package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.opendocument.droid.R
import app.opendocument.droid.background.DocumentDarkening
import app.opendocument.droid.background.DocumentLoader
import app.opendocument.droid.background.DocumentRequest
import app.opendocument.droid.background.FileCache
import app.opendocument.droid.background.IdentifiedFile
import app.opendocument.droid.background.LoadedDocument
import app.opendocument.droid.background.NightModeSetting
import app.opendocument.droid.background.PaginationSetting
import app.opendocument.droid.background.ReviewInvitation
import app.opendocument.droid.background.StreamUtil
import app.opendocument.droid.background.SupportedDocumentTypes
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.widget.DocumentActions
import app.opendocument.droid.ui.widget.PageView
import app.opendocument.droid.ui.widget.ProgressDialogFragment
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class DocumentFragment : Fragment(), DocumentLoader.Listener {

    private lateinit var analyticsManager: AnalyticsManager

    lateinit var crashManager: CrashManager
        private set

    private var progressDialog: ProgressDialogFragment? = null

    private lateinit var pageContainer: ViewGroup

    var pageView: PageView? = null
        private set

    private lateinit var actions: DocumentActions
    private var bottomInset = 0

    /** Folding the actions back up is what back does first, while they are unfolded. */
    private val actionsBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                actions.collapse()
            }
        }

    /** A loader callback that arrived while the activity was stopped, replayed by [onStart]. */
    private var replayOnStart: (() -> Unit)? = null

    private var freshOpenPending = false

    /**
     * Where the reader was, held from [reloadForMargins] until the document comes back. Null at
     * every other load: opening a document belongs at its top.
     */
    private var positionToRestore: ReadingPosition? = null

    /** A tab and how far down it, which survives the document being translated again. */
    private data class ReadingPosition(val tab: Int, val scrollFraction: Float)

    private lateinit var tabLayout: TabLayout

    private lateinit var documentLoader: DocumentLoader

    /** Survives a configuration change, so the document and any unsaved edits outlive it. */
    class DocumentViewModel : ViewModel() {

        /** The last document asked for, whether or not it opened. */
        var lastRequest: DocumentRequest? = null

        /**
         * Null until something was read, and again for a document that could not be read at all.
         */
        var lastFile: IdentifiedFile? = null

        /** Only ever the document currently on screen. */
        var lastDocument: LoadedDocument? = null

        var currentHtmlDiff: String? = null

        // loads cannot be canceled once running, so results of abandoned loads
        // (e.g. user navigated back while the document was still loading) are
        // identified by their uri and dropped
        var lastRequestedUri: Uri? = null

        var lastSelectedTab: Int = -1

        // one espresso token per fragment: a load started while another is in flight replaces it,
        // and it lives in the view model so a configuration change does not lose it
        private var loadIsIdling = false

        /** Keeps espresso busy until a loader callback has run. */
        fun beginLoadIdling() {
            if (!loadIsIdling) {
                loadIsIdling = true

                OpenFileIdling.increment()
            }
        }

        fun endLoadIdling() {
            if (loadIsIdling) {
                loadIsIdling = false

                OpenFileIdling.decrement()
            }
        }

        // no callback is coming, and the idling resource is a singleton the next test inherits
        override fun onCleared() {
            endLoadIdling()
        }
    }

    private lateinit var state: DocumentViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // not in onViewCreated: when the activity is recreated it asks a restored fragment
        // for hasLastResult() from its own onCreate, which is before any view exists
        state = ViewModelProvider(this)[DocumentViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_document, container, false)

    private fun initializePageView() {
        pageView?.let {
            pageContainer.removeAllViews()
            it.destroy()
            pageView = null
        }

        try {
            layoutInflater.inflate(R.layout.page_view, pageContainer, true)
            val pageView: PageView = pageContainer.findViewById(R.id.page_view)
            this.pageView = pageView

            pageView.setDocumentFragment(this)
        } catch (t: Throwable) {
            // crashManager is not set yet: onViewCreated has not run

            val errorString =
                "Please install \"Android System WebView\" and restart the app afterwards."

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://play.google.com/store/apps/details?id=com.google.android.webview"
                    ),
                )
            )

            Toast.makeText(context, errorString, Toast.LENGTH_LONG).show()
            requireActivity().finishAffinity()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pageContainer = view.findViewById(R.id.page_container)
        tabLayout = view.findViewById(R.id.document_tabs)

        val mainActivity = requireActivity() as MainActivity
        analyticsManager = mainActivity.analyticsManager
        crashManager = mainActivity.crashManager

        actions = view.findViewById(R.id.document_actions)
        actions.setBottomInset(bottomInset)
        actions.listener = DocumentActions.Listener { action ->
            mainActivity.onDocumentAction(action)
        }
        actions.expandedListener = { expanded -> actionsBackCallback.isEnabled = expanded }

        // on viewLifecycleOwner, so it stacks above the activity's own callback - the dispatcher
        // runs the most recently added enabled callback first
        mainActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, actionsBackCallback)

        documentLoader = mainActivity.documentLoader
        documentLoader.listener = this

        crashManager.log("onViewCreated")

        if (savedInstanceState == null) {
            return
        }

        crashManager.log("onViewCreated has savedInstanceState")

        initializePageView()

        val pageRestored = restore(savedInstanceState)

        state.lastDocument?.let { lastDocument ->
            crashManager.log("restoring lastDocument")

            // the page view is a new one, and knows nothing of what the old one was told
            applyDarkening(lastDocument.file)

            restoreTabs(lastDocument)
            prepareActions(lastDocument)

            // a webview saves nothing until it has committed a page, and the night mode switch
            // recreating this activity can come sooner than that. the tabs load a part themselves
            if (!pageRestored && lastDocument.partUris.size == 1) {
                loadData(lastDocument.partUris[0].toString())
            }
        }
    }

    /**
     * Reads back what [onSaveInstanceState] wrote. The view model already survives a rotation; the
     * bundle also survives process death.
     *
     * Guarded because it outlives an app update too: a bundle written by an older version can name
     * classes this one no longer has, and [android.os.Bundle] reads its whole map on the first
     * access, so one stale entry takes the rest with it. Losing the reopened document beats
     * throwing on launch.
     *
     * Answers whether the page view got a page back out of it.
     */
    private fun restore(savedInstanceState: Bundle): Boolean {
        try {
            if (state.lastRequest == null) {
                @Suppress("DEPRECATION") // the typed getParcelable overload needs API 33
                state.lastRequest = savedInstanceState.getParcelable(SAVED_KEY_LAST_REQUEST)

                @Suppress("DEPRECATION")
                state.lastFile = savedInstanceState.getParcelable(SAVED_KEY_LAST_FILE)

                @Suppress("DEPRECATION")
                state.lastDocument = savedInstanceState.getParcelable(SAVED_KEY_LAST_DOCUMENT)
            }
            if (state.currentHtmlDiff == null) {
                state.currentHtmlDiff = savedInstanceState.getString(SAVED_KEY_CURRENT_HTML_DIFF)
            }

            return pageView?.restoreState(savedInstanceState) != null
        } catch (e: Throwable) {
            crashManager.log(e)

            return false
        }
    }

    /** Rebuilds the tab strip: the tabs live in the view, which the view model does not hold. */
    private fun restoreTabs(document: LoadedDocument) {
        if (document.partTitles.size <= 1) {
            return
        }

        addTabs(document.partTitles)

        val selected = maxOf(state.lastSelectedTab, 0)
        tabLayout.getTabAt(selected)?.select()
    }

    private fun addTabs(titles: List<String?>) {
        for ((i, title) in titles.withIndex()) {
            val name = title ?: "Page ${i + 1}"

            tabLayout.addTab(tabLayout.newTab().setText(name), false)
        }

        tabLayout.visibility = View.VISIBLE
        tabLayout.addOnTabSelectedListener(tabSelectedListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        crashManager.log("onSaveInstanceState")

        outState.putParcelable(SAVED_KEY_LAST_REQUEST, state.lastRequest)
        outState.putParcelable(SAVED_KEY_LAST_FILE, state.lastFile)
        outState.putParcelable(SAVED_KEY_LAST_DOCUMENT, state.lastDocument)
        outState.putString(SAVED_KEY_CURRENT_HTML_DIFF, state.currentHtmlDiff)

        pageView?.saveState(outState)
    }

    override fun onStart() {
        super.onStart()

        val replay = replayOnStart ?: return
        replayOnStart = null

        replay()
    }

    private fun load(request: DocumentRequest) {
        beforeLoad()

        documentLoader.load(request)
    }

    private fun beforeLoad() {
        // the page on screen is about to be published over - see expectNewPage
        pageView?.expectNewPage()

        // whatever the last load had to say was about the last document. the offer to reopen is
        // indefinite, so without this it sits over the document that came after it
        SnackbarHelper.dismiss(requireActivity())

        showProgress()

        state.beginLoadIdling()
    }

    /**
     * [freshOpen] is false for a load the user did not ask for, which then does not count towards a
     * review being earned.
     */
    fun loadUri(
        uri: Uri,
        persistentUri: Boolean,
        editable: Boolean = false,
        freshOpen: Boolean = true,
    ) {
        initializePageView()

        freshOpenPending = freshOpen

        state.lastRequestedUri = uri

        load(DocumentRequest(uri, persistentUri).apply { this.editable = editable })
    }

    fun reloadUri(editable: Boolean) {
        // closeDocument() removes this fragment and only then finishes the edit mode, whose
        // onDestroyActionMode reloads - a load queued here would have nothing left to land in
        if (!isAdded) {
            return
        }

        val lastRequest = requireLastRequest()
        lastRequest.editable = editable

        // entering or leaving edit mode is not a new document, and the user is working
        freshOpenPending = false

        reload(lastRequest, requireLastFile())
    }

    /** Tells the page whether it may follow the app into night mode - see [DocumentDarkening]. */
    private fun applyDarkening(file: IdentifiedFile) {
        pageView?.setDarkeningAllowed(DocumentDarkening.isAllowed(requireContext(), file.mimeType))
    }

    /**
     * Flips that answer for every document of this kind, and shows it straight away: darkening is a
     * webview setting, so nothing is rendered again.
     */
    fun toggleDarkening() {
        val document = state.lastDocument ?: return

        val kind = DocumentDarkening.kindOf(document.file.mimeType)
        DocumentDarkening.setAllowed(
            requireContext(),
            kind,
            !DocumentDarkening.isAllowed(requireContext(), document.file.mimeType),
        )

        applyDarkening(document.file)

        // the row says what tapping it does, and what it does has just changed
        prepareActions(document)
    }

    /**
     * The document again with the margins [PaginationSetting] now says, which is decided while
     * translating - so it has to be rendered a second time to be seen.
     */
    fun reloadForMargins() {
        if (!isAdded) {
            return
        }

        // the page is thrown away and translated again, so where the reader had got to is carried
        // by hand - it is the same document, and they did not ask to be put back at the top
        positionToRestore =
            ReadingPosition(
                maxOf(state.lastSelectedTab, 0),
                pageView?.verticalScrollFraction ?: 0f,
            )

        // not a new document, and not one the user went and opened either
        freshOpenPending = false

        reload(requireLastRequest(), requireLastFile())
    }

    /** The same document again, rendered differently - see [DocumentLoader.reload]. */
    private fun reload(request: DocumentRequest, file: IdentifiedFile) {
        beforeLoad()

        documentLoader.reload(request, file)
    }

    /**
     * Collects whatever the save needs and runs [callback] - exactly once. A full save writes the
     * file as it is on disk, so it has no diff to ask the page for.
     */
    fun prepareSave(callback: Runnable, fullSave: Boolean) {
        val pageView = this.pageView

        if (fullSave || pageView == null) {
            state.currentHtmlDiff = null

            callback.run()

            return
        }

        pageView.requestHtml { htmlDiff ->
            state.currentHtmlDiff = htmlDiff

            callback.run()
        }
    }

    fun save(outFile: Uri?) {
        if (outFile == null) {
            SnackbarHelper.show(
                requireActivity(),
                R.string.toast_error_save_nofile,
                null,
                isIndefinite = true,
                isError = true,
            )

            return
        }

        documentLoader.save(requireLastDocument(), outFile, state.currentHtmlDiff)
    }

    private fun unload() {
        // nothing left to save or to switch tabs on. the request and the file stay: they are what
        // the reopen offer raised over this works from
        state.lastDocument = null

        // guarded like resetTabs below: a load can fail before there is a view to put right
        if (::actions.isInitialized) {
            actions.setActions(emptyList(), emptyList())
        }

        resetTabs()
    }

    private fun resetTabs() {
        if (::tabLayout.isInitialized) {
            tabLayout.clearOnTabSelectedListeners()
            tabLayout.removeAllTabs()
            tabLayout.visibility = View.GONE
        }

        state.lastSelectedTab = -1
    }

    /**
     * Puts the buttons of the loaded document up, in the order they are worth reaching for.
     *
     * Called for every result rather than from a menu callback, which is what the toolbar version
     * relied on: the menu was only rebuilt when something happened to invalidate it, and a document
     * that finished loading is not one of those things.
     */
    private fun prepareActions(document: LoadedDocument) {
        // whether editing is on offer is the core's answer, not a list of formats kept here: it
        // knows which of the documents it renders it can also write back, which is why neither the
        // legacy binary formats nor the spreadsheets of issue #442 need naming
        val edit =
            if (!document.isEditable) null
            else
                DocumentActions.Action(
                    DocumentActions.ACTION_EDIT,
                    R.string.menu_edit,
                    R.drawable.ic_edit,
                )

        // what the display rows offer is the opposite of what is on screen, so each says what
        // tapping it does rather than what it is called
        val night =
            DocumentActions.Action(
                DocumentActions.ACTION_NIGHT_MODE,
                if (NightModeSetting.isNight(requireContext())) R.string.menu_day_mode
                else R.string.menu_night_mode,
                R.drawable.ic_lightbulb,
            )

        // only while the app is dark: below that the webview darkens nothing whatever it is
        // allowed, so the row would be a switch with nothing on the other end
        val darkening =
            if (!NightModeSetting.isNight(requireContext())) null
            else {
                val kind = DocumentDarkening.kindOf(document.file.mimeType)
                val darkened = DocumentDarkening.isAllowed(requireContext(), document.file.mimeType)

                DocumentActions.Action(
                    DocumentActions.ACTION_DOCUMENT_DARKENING,
                    // it is remembered for the kind, not for the file, so it says which kind
                    when (kind) {
                        DocumentDarkening.Kind.PDF ->
                            if (darkened) R.string.menu_pdfs_light else R.string.menu_pdfs_dark
                        DocumentDarkening.Kind.IMAGE ->
                            if (darkened) R.string.menu_images_light else R.string.menu_images_dark
                        DocumentDarkening.Kind.DOCUMENT ->
                            if (darkened) R.string.menu_documents_light
                            else R.string.menu_documents_dark
                    },
                    R.drawable.ic_invert_colors,
                )
            }

        // odrcore applies them to a text document and nothing else, so anywhere else the row would
        // render the document again to show nothing new - see PaginationSetting.affects
        val margins =
            if (!PaginationSetting.affects(document.file.mimeType)) null
            else
                DocumentActions.Action(
                    DocumentActions.ACTION_PAGE_MARGINS,
                    if (PaginationSetting.isEnabled(requireContext())) R.string.menu_fit_to_screen
                    else R.string.menu_page_borders,
                    R.drawable.ic_menu_book,
                )

        // the order they unfold in, most wanted first - and what a reader reaches for mid-document
        // is how it is displayed, not what else can be done to it
        val unfolding =
            listOfNotNull(
                night,
                darkening,
                margins,
                DocumentActions.Action(
                    DocumentActions.ACTION_FULLSCREEN,
                    R.string.menu_fullscreen,
                    R.drawable.ic_fullscreen,
                ),
                DocumentActions.Action(
                    DocumentActions.ACTION_TTS,
                    R.string.menu_tts,
                    R.drawable.ic_volume_up,
                ),
                DocumentActions.Action(
                    DocumentActions.ACTION_SHARE,
                    R.string.menu_share,
                    R.drawable.ic_share,
                ),
                DocumentActions.Action(
                    DocumentActions.ACTION_PRINT,
                    R.string.menu_cloud_print,
                    R.drawable.ic_print,
                ),
                DocumentActions.Action(
                    DocumentActions.ACTION_OPEN_WITH,
                    R.string.menu_open_with,
                    R.drawable.ic_open_in_new,
                ),
                DocumentActions.Action(
                    DocumentActions.ACTION_SAVE,
                    R.string.action_edit_save,
                    R.drawable.ic_save,
                ),
            )

        // Edit above Search, not below it: Search is offered for every document and Edit is not,
        // so this is the order that keeps the button nearest the thumb the same one throughout
        actions.setActions(
            listOfNotNull(
                edit,
                DocumentActions.Action(
                    DocumentActions.ACTION_SEARCH,
                    R.string.menu_search,
                    R.drawable.ic_search,
                ),
            ),
            unfolding,
        )
    }

    /**
     * How far the gesture bar reaches into the window. The page itself runs under it - see
     * MainActivity.applyWindowInsets - but the buttons in that corner have to stay above it.
     *
     * Remembered rather than applied straight away: MainActivity creates this fragment inside its
     * own onCreate, long before there is a view to put it on.
     */
    fun setBottomInset(inset: Int) {
        bottomInset = inset

        if (::actions.isInitialized) {
            actions.setBottomInset(inset)
        }
    }

    /** Takes the buttons away while the document has the screen to itself. */
    fun setActionsVisible(visible: Boolean) {
        if (!::actions.isInitialized) {
            return
        }

        actions.collapse()
        actions.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Whether an outcome for [request] can be acted on now. If not, [replay] is what [onStart] runs
     * instead - it has to be the caller's own callback, or a failure comes back as a success.
     */
    private fun isActivityReadyForOutcome(
        request: DocumentRequest,
        file: IdentifiedFile?,
        replay: () -> Unit,
    ): Boolean {
        val lastRequestedUri = state.lastRequestedUri
        if (lastRequestedUri != null && lastRequestedUri != request.uri) {
            crashManager.log("dropping result of abandoned load: " + request.uri)

            return false
        }

        if (activity == null || isStateSaved) {
            replayOnStart = replay

            return false
        }

        // needs to be kept for errors too for features like "Open With" to work
        state.lastRequest = request
        state.lastFile = file

        replayOnStart = null

        return true
    }

    override fun onLoadSuccess(document: LoadedDocument) {
        if (
            !isActivityReadyForOutcome(document.request, document.file) { onLoadSuccess(document) }
        ) {
            return
        }

        state.lastDocument = document

        val activity = requireActivity()
        val file = document.file

        // before the page is put in below, so it is drawn the way it is going to stay
        applyDarkening(file)

        analyticsManager.setCurrentScreen(activity, file.mimeType ?: UNKNOWN_FILE_TYPE)

        // clears lastSelectedTab, so what reloadForMargins put aside is read after it
        resetTabs()

        val restored = positionToRestore
        positionToRestore = null

        // always, and not only when there is something to put back: a reload that failed on the
        // way here would otherwise leave its fraction waiting for the next document opened
        pageView?.restoreScrollFraction(restored?.scrollFraction ?: 0f)

        val titles = document.partTitles
        val pages = titles.size
        if (pages > 1) {
            addTabs(titles)

            tabLayout.getTabAt(restored?.tab?.coerceAtMost(pages - 1) ?: 0)?.select()
        } else if (pages == 1) {
            loadData(document.partUris[0].toString())
        }

        prepareActions(document)

        // the escape hatch for a file we show rather than read: an image, an archive listing
        if (!SupportedDocumentTypes.isDocument(file.mimeType)) {
            offerReopen(activity, R.string.toast_hint_unsupported_file, false)
        }

        dismissProgress()

        state.endLoadIdling()

        // only a fresh open counts - reloadUri and the webview reach here mid-task.
        // a save reloads through loadUri and so still counts, which is wanted.
        // the ask itself waits for the document to be closed again, where nothing is pending
        if (freshOpenPending) {
            freshOpenPending = false

            ReviewInvitation.recordDocumentOpen(activity)
        }
    }

    override fun onError(request: DocumentRequest, file: IdentifiedFile?, error: Throwable) {
        if (!isActivityReadyForOutcome(request, file) { onError(request, file, error) }) {
            return
        }

        val activity = requireActivity()

        unload()
        dismissProgress()

        when {
            error is FileNotFoundException ->
                offerReopen(activity, R.string.toast_error_find_file, true)
            error is OutOfMemoryError ->
                offerReopen(activity, R.string.toast_error_out_of_memory, true)
            // unreadable, or named and still not openable
            else -> {
                // nothing is ever going to be shown for this file, so drop back to the
                // landing screen and let the dialog come up over that
                state.endLoadIdling()
                giveUp(activity)

                offerContact(activity)

                return
            }
        }

        giveUp(activity)

        state.endLoadIdling()
    }

    /**
     * The page [PageView] was given cannot be shown after all, so this ends where a document that
     * would not open ends: back on the landing screen, offering to tell us about it.
     *
     * It arrives after [onLoadSuccess] rather than instead of it, which is why it undoes it instead
     * of going through [isActivityReadyForOutcome].
     */
    fun onPageFailed() {
        // already given up on, or a second view of the same failure
        if (state.lastDocument == null) {
            return
        }

        if (activity == null || isStateSaved) {
            replayOnStart = { onPageFailed() }

            return
        }

        val activity = requireActivity()

        analyticsManager.report(
            "page_failed",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            state.lastFile?.mimeType,
        )

        unload()
        dismissProgress()

        giveUp(activity)

        offerContact(activity)
    }

    override fun onEncrypted(request: DocumentRequest, file: IdentifiedFile, canDecrypt: Boolean) {
        if (!isActivityReadyForOutcome(request, file) { onEncrypted(request, file, canDecrypt) }) {
            return
        }

        val activity = requireActivity()

        unload()
        dismissProgress()

        if (!canDecrypt) {
            // no password opens one of these; another app might, which is what the bar offers
            offerReopen(activity, R.string.toast_error_password_protected, true)
            giveUp(activity)

            state.endLoadIdling()

            return
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.toast_error_password_protected)

        val input = EditText(activity)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton(getString(android.R.string.ok)) { dialog, _ ->
            request.password = input.text.toString()

            // close dialog before progress is shown again
            dialog.dismiss()

            // the file is already in the cache - only the key to it was missing
            reload(request, file)
        }
        builder.setNegativeButton(getString(android.R.string.cancel), null)
        builder.show()

        // after show(), so espresso does not go idle until the dialog is on screen
        state.endLoadIdling()
    }

    override fun onUnsupported(request: DocumentRequest, file: IdentifiedFile) {
        if (!isActivityReadyForOutcome(request, file) { onUnsupported(request, file) }) {
            return
        }

        val activity = requireActivity()

        unload()
        dismissProgress()

        offerReopen(activity, R.string.toast_error_illegal_file_reopen, true)
        giveUp(activity)

        state.endLoadIdling()
    }

    override fun onSaveSuccess(target: Uri) {
        state.currentHtmlDiff = null

        SnackbarHelper.show(
            requireActivity(),
            R.string.toast_edit_status_saved,
            null,
            isIndefinite = false,
            isError = false,
        )

        loadUri(target, true, true)
    }

    override fun onSaveError() {
        state.currentHtmlDiff = null

        SnackbarHelper.show(
            requireActivity(),
            R.string.toast_error_save_failed,
            null,
            isIndefinite = true,
            isError = true,
        )
    }

    /**
     * Nothing left to try with this document, so stop showing it: the landing screen is a better
     * answer than a blank page, and the bar raised just before this says what happened.
     *
     * Not every failure ends here. The password prompt is the app still having something to do with
     * the file, and it needs the document on screen to do it.
     */
    private fun giveUp(activity: Activity) {
        (activity as MainActivity).closeFailedDocument()
    }

    private fun offerContact(activity: Activity) {
        analyticsManager.report("contact_offer")

        // its own content view rather than setMessage plus the builder's buttons - see
        // dialog_broken_file.xml. strings off the activity: giveUp() already detached this
        // fragment, and its own getString() would throw
        val view = activity.layoutInflater.inflate(R.layout.dialog_broken_file, null)

        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_broken_file_title)
                .setView(view)
                .show()

        view.findViewById<View>(R.id.dialog_broken_file_contact).setOnClickListener {
            contactSupport(activity)

            dialog.dismiss()
        }
        view.findViewById<View>(R.id.dialog_broken_file_ok).setOnClickListener {
            dialog.dismiss()
        }

        // the address is clickable too, through the same guarded launch as the button rather
        // than autoLink - TextView's own mailto handler throws where there is no mail app.
        // a translation that dropped the address just leaves the message as plain text
        val message = view.findViewById<TextView>(R.id.dialog_broken_file_message)
        val address = activity.getString(R.string.support_email)
        val text = SpannableString(message.text)
        val start = text.indexOf(address)
        if (start >= 0) {
            text.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        contactSupport(activity)

                        dialog.dismiss()
                    }
                },
                start,
                start + address.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

            message.text = text
            message.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun contactSupport(activity: Activity) {
        val intent =
            Intent(
                Intent.ACTION_SENDTO,
                "mailto:${activity.getString(R.string.support_email)}".toUri(),
            )
        intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.app_title))

        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // no mail app - the address is in the dialog either way, so say nothing more
            crashManager.log(e)
        }
    }

    private fun offerReopen(activity: Activity, description: Int, isIndefinite: Boolean) {
        // taken here rather than when the button is tapped: the bar is about the document that
        // raised it, and it outlives the fragment on its way over the landing screen
        val request = requireLastRequest()
        val file = state.lastFile

        analyticsManager.report(
            "reopen_offer",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            file?.mimeType,
            AnalyticsConstants.PARAM_CONTENT,
            request.uri,
        )

        SnackbarHelper.show(
            activity,
            description,
            { doReopen(activity, request, file, true, false) },
            isIndefinite = isIndefinite,
            isError = false,
        )
    }

    fun openWith(activity: Activity) {
        doReopen(activity, requireLastRequest(), state.lastFile, true, false)
    }

    fun share(activity: Activity) {
        doReopen(activity, requireLastRequest(), state.lastFile, true, true)
    }

    private fun doReopen(
        activity: Activity,
        request: DocumentRequest,
        file: IdentifiedFile?,
        grantPermission: Boolean,
        share: Boolean,
    ) {
        val fileType = file?.mimeType

        var reopenUri = request.uri
        // having a file is having read it whole: what is handed on is our copy, under a name the
        // receiving app can make sense of
        if (file != null) {
            val cacheFile = FileCache.getCacheFile(activity, file.cacheUri)
            val cacheDirectory = FileCache.getCacheDirectory(checkNotNull(cacheFile))

            val reopenFile = File(cacheDirectory, "yourdocument." + file.extension)
            try {
                StreamUtil.copy(cacheFile, reopenFile)

                reopenUri = FileCache.getCacheFileUri(activity, reopenFile)
            } catch (e: IOException) {
                crashManager.log(e)
            }
        }

        val intent = Intent()

        intent.action = if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW

        if (fileType != null) {
            intent.setDataAndType(reopenUri, fileType)
        } else {
            intent.data = reopenUri
        }

        if (share) {
            intent.putExtra(Intent.EXTRA_STREAM, reopenUri)
        }

        if (grantPermission) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val logPrefix = if (share) "share" else "reopen"

        val chooserIntent =
            Intent.createChooser(intent, activity.getString(R.string.reopen_chooser_title))

        try {
            activity.startActivity(chooserIntent)

            analyticsManager.report(
                logPrefix + "_success",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                fileType,
            )
        } catch (e: Throwable) {
            crashManager.log(e)

            analyticsManager.report(
                logPrefix + "_failed",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                fileType,
            )

            if (grantPermission) {
                // if we're trying to reopen the originalUri, the provider might decline the request
                doReopen(activity, request, file, false, share)
            }
        }
    }

    private fun showProgress() {
        // getParentFragmentManager() throws when the fragment is not attached, where the
        // deprecated getFragmentManager() used to return null
        if (!isAdded) {
            return
        }

        val fragmentManager = parentFragmentManager

        if (progressDialog == null) {
            progressDialog =
                fragmentManager.findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG)
                    as ProgressDialogFragment?
        }

        if (progressDialog != null) {
            return
        }

        try {
            val progressDialog = ProgressDialogFragment()
            this.progressDialog = progressDialog

            progressDialog.show(fragmentManager, ProgressDialogFragment.FRAGMENT_TAG)
        } catch (e: IllegalStateException) {
            // sometimes called while activity is in background
            crashManager.log(e)

            progressDialog = null
        }
    }

    private fun dismissProgress() {
        if (progressDialog == null && isAdded) {
            progressDialog =
                parentFragmentManager.findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG)
                    as ProgressDialogFragment?
        }

        try {
            progressDialog?.dismiss()
        } catch (e: IllegalStateException) {
            // sometimes called while activity is in background
            crashManager.log(e)
        }

        progressDialog = null
    }

    private val tabSelectedListener =
        object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val lastDocument = requireLastDocument()

                // an edited document must not switch away from the page being edited,
                // so the previous tab is re-selected instead
                if (lastDocument.request.editable && state.lastSelectedTab >= 0) {
                    // reselecting from inside onTabSelected() does not take effect
                    tabLayout.postDelayed(
                        { tabLayout.getTabAt(state.lastSelectedTab)?.select() },
                        1,
                    )

                    return
                }

                // in every mode, so restoreTabs() puts the indicator back on the page
                // the user was looking at after the view is recreated
                state.lastSelectedTab = tab.position

                loadData(lastDocument.partUris[tab.position].toString())
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        }

    private fun loadData(url: String) {
        pageView?.loadUrl(url)
    }

    private fun requireLastRequest(): DocumentRequest =
        checkNotNull(state.lastRequest) { "nothing was loaded yet" }

    private fun requireLastFile(): IdentifiedFile =
        checkNotNull(state.lastFile) { "nothing was read yet" }

    private fun requireLastDocument(): LoadedDocument =
        checkNotNull(state.lastDocument) { "no document is open" }

    @get:VisibleForTesting
    val lastDocument: LoadedDocument?
        get() = state.lastDocument

    @get:VisibleForTesting
    val lastRequest: DocumentRequest?
        get() = state.lastRequest

    fun hasLastResult(): Boolean {
        // the activity can hold a freshly constructed fragment that has not been created yet
        return ::state.isInitialized && state.lastRequest != null
    }

    /** Whether the document is in edit mode, so its changes are still only in the page. */
    fun isEditing(): Boolean = ::state.isInitialized && state.lastRequest?.editable == true

    val lastFileType: String?
        get() = state.lastFile?.mimeType

    override fun onDestroyView() {
        super.onDestroyView()

        if (::documentLoader.isInitialized) {
            documentLoader.listener = null
        }

        // no callback is coming, except on a configuration change - there the recreated
        // fragment inherits both the listener and the view model
        if (!requireActivity().isChangingConfigurations) {
            state.endLoadIdling()
        }

        pageView?.destroy()
    }

    private companion object {
        const val SAVED_KEY_LAST_REQUEST = "LAST_REQUEST"
        const val SAVED_KEY_LAST_FILE = "LAST_FILE"
        const val SAVED_KEY_LAST_DOCUMENT = "LAST_DOCUMENT"
        const val SAVED_KEY_CURRENT_HTML_DIFF = "CURRENT_HTML_DIFF"

        /** What the analytics screen name is when nothing could name the bytes. */
        const val UNKNOWN_FILE_TYPE = "N/A"
    }
}
