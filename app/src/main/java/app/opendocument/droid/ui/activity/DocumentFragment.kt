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
import app.opendocument.droid.background.AndroidFileCache
import app.opendocument.droid.background.FileLoader
import app.opendocument.droid.background.LoaderService
import app.opendocument.droid.background.LoaderServiceQueue
import app.opendocument.droid.background.StreamUtil
import app.opendocument.droid.background.SupportedDocumentTypes
import app.opendocument.droid.background.UsageCounters
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.nonfree.InAppReview
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.widget.DocumentActions
import app.opendocument.droid.ui.widget.PageView
import app.opendocument.droid.ui.widget.ProgressDialogFragment
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class DocumentFragment : Fragment(), LoaderService.LoaderListener {

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

    private lateinit var tabLayout: TabLayout

    private lateinit var serviceQueue: LoaderServiceQueue

    /** Survives a configuration change, so the document and any unsaved edits outlive it. */
    class DocumentViewModel : ViewModel() {
        var lastResult: FileLoader.Result? = null
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

            // a property of the page, not of the buttons over it - this used to be the last line
            // of prepareActions(), which reached every new PageView only because the two are set
            // up together. every one of them passes through here
            pageView.disableDarkening()
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

        serviceQueue = mainActivity.loaderServiceQueue
        serviceQueue.addToQueue { service -> service.setListener(this) }

        crashManager.log("onViewCreated")

        if (savedInstanceState == null) {
            return
        }

        crashManager.log("onViewCreated has savedInstanceState")

        initializePageView()

        // the view model survives a rotation, the bundle also survives process death
        if (state.lastResult == null) {
            @Suppress("DEPRECATION") // the typed getParcelable overload needs API 33
            state.lastResult = savedInstanceState.getParcelable(SAVED_KEY_LAST_RESULT)
        }
        if (state.currentHtmlDiff == null) {
            state.currentHtmlDiff = savedInstanceState.getString(SAVED_KEY_CURRENT_HTML_DIFF)
        }

        state.lastResult?.let { lastResult ->
            crashManager.log("restoring lastResult")

            restoreTabs(lastResult)
            prepareActions(lastResult)
        }

        pageView?.restoreState(savedInstanceState)
    }

    /** Rebuilds the tab strip: the tabs live in the view, which the view model does not hold. */
    private fun restoreTabs(result: FileLoader.Result) {
        if (result.partTitles.size <= 1) {
            return
        }

        addTabs(result.partTitles)

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

        outState.putParcelable(SAVED_KEY_LAST_RESULT, state.lastResult)
        outState.putString(SAVED_KEY_CURRENT_HTML_DIFF, state.currentHtmlDiff)

        pageView?.saveState(outState)
    }

    override fun onStart() {
        super.onStart()

        val replay = replayOnStart ?: return
        replayOnStart = null

        replay()
    }

    private fun loadWithType(loaderType: FileLoader.LoaderType, options: FileLoader.Options) {
        // whatever the last load had to say was about the last document. the offers to reopen or
        // upload are indefinite, so without this they sit over the document that came after them
        SnackbarHelper.dismiss(requireActivity())

        showProgress(loaderType == FileLoader.LoaderType.ONLINE)

        state.beginLoadIdling()

        serviceQueue.addToQueue { service -> service.loadWithType(loaderType, options) }
    }

    /**
     * [freshOpen] is false for a load the user did not ask for, which then never asks for a review.
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

        val options = FileLoader.Options()
        options.originalUri = uri
        options.persistentUri = persistentUri
        options.translatable = editable

        loadWithType(FileLoader.LoaderType.METADATA, options)
    }

    fun reloadUri(translatable: Boolean) {
        // closeDocument() removes this fragment and only then finishes the edit mode, whose
        // onDestroyActionMode reloads - a load queued here would have nothing left to land in
        if (!isAdded) {
            return
        }

        val lastResult = checkNotNull(state.lastResult) { "nothing was loaded yet" }
        lastResult.options.translatable = translatable

        // entering or leaving edit mode is not a new document, and the user is working
        freshOpenPending = false

        loadWithType(lastResult.loaderType, lastResult.options)
    }

    fun prepareSave(callback: Runnable, fullSave: Boolean) {
        if (fullSave) {
            state.currentHtmlDiff = null

            callback.run()
        }

        // a full save runs the callback twice, so a second requestSave() would open the
        // create-document picker twice - masked only by odr.generateDiff() existing in edit mode
        pageView?.requestHtml { htmlDiff ->
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

        // read inside the entry: the queue replays this once the service is bound
        serviceQueue.addToQueue { service ->
            service.saveAsync(requireLastResult(), outFile, state.currentHtmlDiff)
        }
    }

    private fun unload() {
        // guarded like resetTabs below: a load can fail before there is a view to put right
        if (::actions.isInitialized) {
            actions.setActions(null, emptyList())
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
    private fun prepareActions(result: FileLoader.Result) {
        // whether editing is on offer is the core's answer, not a list of formats kept here: it
        // knows which of the documents it renders it can also write back, which is why neither the
        // legacy binary formats nor the spreadsheets of issue #442 need naming
        val edit =
            if (!result.isEditable) null
            else
                DocumentActions.Action(
                    DocumentActions.ACTION_EDIT,
                    R.string.menu_edit,
                    R.drawable.ic_edit,
                )

        // read aloud needs the javascript bridge, which PageView keeps off the third party viewers
        // an ONLINE result loads - offering it there leaves it stuck at "reading" with no paragraph
        val tts =
            if (result.loaderType == FileLoader.LoaderType.ONLINE) null
            else
                DocumentActions.Action(
                    DocumentActions.ACTION_TTS,
                    R.string.menu_tts,
                    R.drawable.ic_volume_up,
                )

        // the order they unfold in, most wanted first
        val unfolding =
            listOfNotNull(
                edit,
                tts,
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
                DocumentActions.Action(
                    DocumentActions.ACTION_FULLSCREEN,
                    R.string.menu_fullscreen,
                    R.drawable.ic_fullscreen,
                ),
            )

        actions.setActions(
            DocumentActions.Action(
                DocumentActions.ACTION_SEARCH,
                R.string.menu_search,
                R.drawable.ic_search,
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
     * Whether [result] can be acted on now. If not, [replay] is what [onStart] runs instead - it
     * has to be the caller's own callback, or a failure comes back as a success.
     */
    private fun isActivityReadyForResult(
        result: FileLoader.Result,
        replay: () -> Unit,
    ): Boolean {
        val lastRequestedUri = state.lastRequestedUri
        if (lastRequestedUri != null && lastRequestedUri != result.options.originalUri) {
            crashManager.log("dropping result of abandoned load: " + result.options.originalUri)

            return false
        }

        if (activity == null || isStateSaved) {
            replayOnStart = replay

            return false
        }

        // needs to be saved for errors too for features like "Open With" to work
        state.lastResult = result

        replayOnStart = null

        return true
    }

    override fun onLoadSuccess(result: FileLoader.Result) {
        if (!isActivityReadyForResult(result) { onLoadSuccess(result) }) {
            return
        }

        val activity = requireActivity()
        val options = result.options

        analyticsManager.setCurrentScreen(
            activity,
            result.loaderType.toString() + "_" + options.fileType,
        )

        resetTabs()

        val titles = result.partTitles
        val pages = titles.size
        if (pages > 1) {
            addTabs(titles)

            tabLayout.getTabAt(0)?.select()
        } else if (pages == 1) {
            loadData(result.partUris[0].toString())
        }

        prepareActions(result)

        // the escape hatch for a file we show rather than read: an image, an archive listing
        if (
            !SupportedDocumentTypes.isDocument(options.fileType) ||
                result.loaderType == FileLoader.LoaderType.ONLINE
        ) {
            offerReopen(activity, options, R.string.toast_hint_unsupported_file, false)
        }

        dismissProgress()

        state.endLoadIdling()

        // only a fresh open earns the ask - reloadUri and the webview reach here mid-task.
        // a save reloads through loadUri and so still counts, which is wanted
        if (freshOpenPending) {
            freshOpenPending = false

            InAppReview.requestIfEarned(
                activity,
                analyticsManager,
                UsageCounters.recordDocumentOpen(activity),
            )
        }
    }

    override fun onError(result: FileLoader.Result, error: Throwable) {
        if (!isActivityReadyForResult(result) { onError(result, error) }) {
            return
        }

        val activity = requireActivity()
        val options = result.options

        unload()
        dismissProgress()

        when {
            error is FileNotFoundException ->
                offerReopen(activity, options, R.string.toast_error_find_file, true)
            error is OutOfMemoryError ->
                offerReopen(activity, options, R.string.toast_error_out_of_memory, true)
            // the network or the server failed, which says nothing about the file
            result.loaderType == FileLoader.LoaderType.ONLINE ->
                offerReopen(activity, options, R.string.toast_error_upload_failed, true)
            // unreadable, or named and still not openable - neither is worth an upload
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

    override fun onEncrypted(result: FileLoader.Result) {
        if (!isActivityReadyForResult(result) { onEncrypted(result) }) {
            return
        }

        val activity = requireActivity()
        val options = result.options

        unload()
        dismissProgress()

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.toast_error_password_protected)

        val input = EditText(activity)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton(getString(android.R.string.ok)) { dialog, _ ->
            options.password = input.text.toString()

            // close dialog before progress is shown again
            dialog.dismiss()

            if (result.loaderType != FileLoader.LoaderType.CORE) {
                throw RuntimeException("encryption not supported for type: " + result.loaderType)
            }

            loadWithType(FileLoader.LoaderType.CORE, options)
        }
        builder.setNegativeButton(getString(android.R.string.cancel), null)
        builder.show()

        // after show(), so espresso does not go idle until the dialog is on screen
        state.endLoadIdling()
    }

    override fun onUnsupported(result: FileLoader.Result) {
        if (!isActivityReadyForResult(result) { onUnsupported(result) }) {
            return
        }

        val activity = requireActivity()
        val options = result.options

        unload()
        dismissProgress()

        if (result.loaderType == FileLoader.LoaderType.CORE) {
            if (serviceQueue.service?.isOnlineSupported(options) == true) {
                // the upload offer is the app still having something to try, so the document stays
                // where it is until the user answers it
                offerUpload(activity, options)
            } else {
                offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true)
                giveUp(activity)
            }
        } else if (result.loaderType == FileLoader.LoaderType.ONLINE) {
            offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true)
            giveUp(activity)
        }

        state.endLoadIdling()
    }

    override fun onSaveSuccess(outFile: Uri) {
        state.currentHtmlDiff = null

        SnackbarHelper.show(
            requireActivity(),
            R.string.toast_edit_status_saved,
            null,
            isIndefinite = false,
            isError = false,
        )

        loadUri(outFile, true, true)
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
     * Not every failure ends here. The password prompt and the upload offer are both the app still
     * having something to do with the file, and both need the document on screen to do it.
     */
    private fun giveUp(activity: Activity) {
        (activity as MainActivity).closeFailedDocument()
    }

    private fun offerUpload(activity: Activity, options: FileLoader.Options) {
        val fileType = options.fileType

        analyticsManager.report(
            "upload_offer_invasive",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            fileType,
            AnalyticsConstants.PARAM_CONTENT,
            options.originalUri,
        )

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.toast_error_illegal_file)
        builder.setMessage(R.string.dialog_upload_file)

        builder.setPositiveButton(getString(R.string.action_upload)) { dialog, _ ->
            analyticsManager.report("load_upload", AnalyticsConstants.PARAM_CONTENT_TYPE, fileType)

            loadWithType(FileLoader.LoaderType.ONLINE, options)

            dialog.dismiss()
        }
        builder.setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
            analyticsManager.report(
                "load_upload_cancel",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                fileType,
            )

            offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true)
            giveUp(activity)

            dialog.dismiss()
        }

        builder.show()
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

    private fun offerReopen(
        activity: Activity,
        options: FileLoader.Options,
        description: Int,
        isIndefinite: Boolean,
    ) {
        analyticsManager.report(
            "reopen_offer",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            options.fileType,
            AnalyticsConstants.PARAM_CONTENT,
            options.originalUri,
        )

        SnackbarHelper.show(
            activity,
            description,
            { doReopen(options, activity, true, false) },
            isIndefinite = isIndefinite,
            isError = false,
        )
    }

    fun openWith(activity: Activity) {
        doReopen(requireLastResult().options, activity, true, false)
    }

    fun share(activity: Activity) {
        doReopen(requireLastResult().options, activity, true, true)
    }

    private fun doReopen(
        options: FileLoader.Options,
        activity: Activity,
        grantPermission: Boolean,
        share: Boolean,
    ) {
        val fileType = options.fileType

        var reopenUri = options.originalUri
        if (options.fileExists) {
            val cacheFile = AndroidFileCache.getCacheFile(activity, checkNotNull(options.cacheUri))
            val cacheDirectory = AndroidFileCache.getCacheDirectory(checkNotNull(cacheFile))

            val reopenFile = File(cacheDirectory, "yourdocument." + options.fileExtension)
            try {
                StreamUtil.copy(cacheFile, reopenFile)

                reopenUri = AndroidFileCache.getCacheFileUri(activity, reopenFile)
            } catch (e: IOException) {
                crashManager.log(e)
            }
        }

        val intent = Intent()

        intent.action = if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW

        if ("N/A" != fileType) {
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
                doReopen(options, activity, false, share)
            }
        }
    }

    private fun showProgress(isUpload: Boolean) {
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
            val progressDialog = ProgressDialogFragment(isUpload)
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
                val lastResult = requireLastResult()

                // an edited document must not switch away from the page being edited,
                // so the previous tab is re-selected instead
                if (lastResult.options.translatable && state.lastSelectedTab >= 0) {
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

                loadData(lastResult.partUris[tab.position].toString())
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        }

    private fun loadData(url: String) {
        pageView?.loadUrl(url)
    }

    private fun requireLastResult(): FileLoader.Result =
        checkNotNull(state.lastResult) { "nothing was loaded yet" }

    @get:VisibleForTesting
    val lastResult: FileLoader.Result?
        get() = state.lastResult

    fun hasLastResult(): Boolean {
        // the activity can hold a freshly constructed fragment that has not been created yet
        return ::state.isInitialized && state.lastResult != null
    }

    val lastFileType: String?
        get() = requireLastResult().options.fileType

    override fun onDestroyView() {
        super.onDestroyView()

        serviceQueue.service?.setListener(null)

        // no callback is coming, except on a configuration change - there the recreated
        // fragment inherits both the listener and the view model
        if (!requireActivity().isChangingConfigurations) {
            state.endLoadIdling()
        }

        pageView?.destroy()
    }

    private companion object {
        const val SAVED_KEY_LAST_RESULT = "LAST_RESULT"
        const val SAVED_KEY_CURRENT_HTML_DIFF = "CURRENT_HTML_DIFF"
    }
}
