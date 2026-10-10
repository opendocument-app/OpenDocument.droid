package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode as SupportActionMode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import app.opendocument.droid.R
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.DocumentLoader
import app.opendocument.droid.background.NightModeSetting
import app.opendocument.droid.background.PaginationSetting
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.PrintingManager
import app.opendocument.droid.background.RecentDocumentsUtil
import app.opendocument.droid.background.ReviewInvitation
import app.opendocument.droid.nonfree.AdManager
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.BillingManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.nonfree.Features
import app.opendocument.droid.nonfree.InAppReview
import app.opendocument.droid.nonfree.PlayServices
import app.opendocument.droid.ui.EditActionModeCallback
import app.opendocument.droid.ui.FindActionModeCallback
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.TtsActionModeCallback
import app.opendocument.droid.ui.widget.DocumentActions

class MainActivity : AppCompatActivity() {

    private lateinit var handler: Handler

    private lateinit var landingContainer: View
    private lateinit var documentContainer: View
    private lateinit var adContainer: LinearLayout
    private var documentFragment: DocumentFragment? = null

    // how far the gesture bar reaches into the window, kept so a DocumentFragment created after
    // the insets arrived still gets it - see applyWindowInsets
    private var bottomInset = 0

    /**
     * the decor's stack once [liftBanner] has moved the banner into it - see [applyWindowInsets]
     */
    private var windowRoot: ViewGroup? = null

    private val landingFragment: LandingFragment?
        get() =
            supportFragmentManager.findFragmentByTag(LandingFragment.FRAGMENT_TAG)
                as LandingFragment?

    private var fullscreen = false

    // predictive back (default from targetSdk 36) delivers neither KEYCODE_BACK nor
    // onBackPressed(), so back is intercepted through the dispatcher
    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) {
                    leaveFullscreen()

                    return
                }

                if (documentFragment != null && !documentOpenedExternally) {
                    analyticsManager.report("back_to_landing")

                    confirmLeavingEdits {
                        closeDocument()

                        // a document read and put down again: the best moment there is to ask, and
                        // the reason onLoadSuccess does not - that one lands on a document the user
                        // just asked for and is about to read
                        askForReviewIfEarned()
                    }

                    return
                }

                // an externally opened document leaves the app rather than the document, and
                // carries unsaved edits out just the same
                confirmLeavingEdits {
                    // the only moment a document opened from another app is put down again
                    val asked =
                        documentFragment != null &&
                            preparedReview?.showIfReady(
                                onAsked = { ReviewInvitation.recordAsk(this@MainActivity) },
                                onDone = { leaveApp() },
                            ) == true
                    preparedReview = null

                    if (!asked) {
                        leaveApp()
                    }
                }
            }
        }

    /** Falls through to the default back behavior, which closes the activity. */
    private fun leaveApp() {
        if (isFinishing || isDestroyed) {
            return
        }

        backCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        backCallback.isEnabled = true
    }

    // kept because onPause has to stop it
    private var ttsActionMode: TtsActionModeCallback? = null

    /** Kept because the page's undo state is reported to the bar this owns. */
    var editActionMode: EditActionModeCallback? = null
        private set

    /** The action mode on screen, so [closeDocument] and [onDestroy] can take it down with them. */
    private var currentActionMode: SupportActionMode? = null

    lateinit var crashManager: CrashManager
        private set

    lateinit var analyticsManager: AnalyticsManager
        private set

    private lateinit var adManager: AdManager
    private lateinit var billingManager: BillingManager
    private lateinit var printingManager: PrintingManager

    private var lastUri: Uri? = null
    private var loadOnStart: Uri? = null
    private var lastSaveUri: Uri? = null

    // back returns to the calling app for these, rather than to the landing screen
    private var documentOpenedExternally = false

    // set before we start an activity of our own, so coming back from it is not counted as
    // the user opening the app. saved, or a rotation under the picker resets it
    private var leftForOwnActivity = false

    // requestReviewFlow answers asynchronously, so a second qualifying moment before recordAsk
    // lands would pass isEarned again. never reset: one hand-off per activity is plenty
    private var reviewRequested = false

    // fetched while an externally opened document is read: back must not wait on the network
    private var preparedReview: InAppReview.Prepared? = null

    /**
     * Loads and saves the open document. Scoped to the activity, so it survives a configuration
     * change and [DocumentFragment] finds it already there rather than waiting for a binding.
     */
    lateinit var documentLoader: DocumentLoader
        private set

    // ACTION_OPEN_DOCUMENT, dispatched to a file manager the user picked
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            OpenFileIdling.decrement()

            val data = result.data
            if (result.resultCode != Activity.RESULT_OK || data == null) {
                return@registerForActivityResult
            }

            val uri = data.data ?: return@registerForActivityResult

            crashManager.log("open document result")

            loadUri(uri)
        }

    // ACTION_CREATE_DOCUMENT, the target the current document is saved to
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val outFile = result.data?.data ?: return@registerForActivityResult

            PersistedUriPermissions.takeRead(this, outFile)
            lastSaveUri = outFile

            documentFragment?.save(outFile)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // before super: appcompat applies a mode the moment it is told, so setting it afterwards
        // recreates the activity that has just been created
        delegate.localNightMode = NightModeSetting.mode(this)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.main)

        onBackPressedDispatcher.addCallback(this, backCallback)

        // before the fragments below: a restored DocumentFragment reaches for it in onViewCreated
        documentLoader = ViewModelProvider(this)[DocumentLoader::class.java]

        handler = Handler(Looper.getMainLooper())

        adContainer = findViewById(R.id.ad_container)
        liftBanner()
        landingContainer = findViewById(R.id.landing_container)
        documentContainer = findViewById(R.id.document_container)

        applyWindowInsets()

        if (supportFragmentManager.findFragmentByTag(LandingFragment.FRAGMENT_TAG) == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.landing_container, LandingFragment(), LandingFragment.FRAGMENT_TAG)
                .commitNow()
        }

        printingManager = PrintingManager()
        initializeManagers()

        // has to happen here rather than in LandingFragment: users upgrading from a version
        // with different alias defaults have to be corrected even when the app is launched
        // straight into a document and the landing screen is never shown
        val catchAllEnabled = CatchAllSetting.applyOnLaunch(this)
        analyticsManager.report(if (catchAllEnabled) "catch_all_enabled" else "catch_all_disabled")

        // reclaims the grants of documents that dropped off the recent list; hits the
        // filesystem and the permission binder, so off the main thread
        Thread { PersistedUriPermissions.prune(applicationContext) }.start()

        crashManager.log("onCreate")

        documentFragment =
            supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

        if (savedInstanceState != null) {
            documentOpenedExternally =
                savedInstanceState.getBoolean(SAVED_KEY_OPENED_EXTERNALLY, false)
            leftForOwnActivity =
                savedInstanceState.getBoolean(SAVED_KEY_LEFT_FOR_OWN_ACTIVITY, false)
        }

        val documentFragment = this.documentFragment
        if (documentFragment != null && documentFragment.hasLastResult()) {
            // nothing else to do

            crashManager.log("onCreate nothing")
        } else if (
            savedInstanceState != null && savedInstanceState.containsKey(SAVED_KEY_LAST_CACHE_URI)
        ) {
            @Suppress("DEPRECATION") // the typed getParcelable overload needs API 33
            loadOnStart = savedInstanceState.getParcelable(SAVED_KEY_LAST_CACHE_URI)

            crashManager.log("onCreate loadOnStart")
        } else if (documentFragment == null) {
            crashManager.log("onCreate from background")

            // app was started from another app, but make sure not to load it twice
            // (i.e. after bringing app back from background)
            val data = intent.data
            if (data != null) {
                loadOnStart = data
                documentOpenedExternally = true

                analyticsManager.report(
                    AnalyticsConstants.EVENT_SELECT_CONTENT,
                    AnalyticsConstants.PARAM_CONTENT_TYPE,
                    "other",
                )
            } else {
                analyticsManager.setCurrentScreen(this, "screen_main")
            }
        } else {
            crashManager.log("onCreate empty")

            analyticsManager.setCurrentScreen(this, "screen_main")
        }
    }

    /**
     * Puts the banner above everything the window shows, the bar an action mode raises included.
     * That bar is the decor's, not this layout's, so the banner leaves the content view for the
     * decor's own stack, in front of `action_mode_bar_stub`.
     */
    private fun liftBanner() {
        val content: View = findViewById(android.R.id.content)
        val decor = content.parent as? ViewGroup ?: return

        (adContainer.parent as? ViewGroup)?.removeView(adContainer)
        decor.addView(adContainer, 0)
        windowRoot = decor
    }

    /**
     * Insets the decor above system bars and cutouts. The document draws under the gesture bar;
     * controls stay above it. The keyboard insets the document container.
     */
    private fun applyWindowInsets() {
        val root: View = windowRoot ?: findViewById(R.id.main_root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(bars.left, bars.top, bars.right, 0)

            landingContainer.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            documentContainer.setPadding(0, 0, 0, ime.bottom)

            bottomInset = bars.bottom
            documentFragment?.setBottomInset(bars.bottom)

            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onStart() {
        super.onStart()

        documentFragment =
            supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

        if (documentFragment != null) {
            landingContainer.visibility = View.GONE
            documentContainer.visibility = View.VISIBLE

            landingFragment?.setLandingVisible(false)
        }

        crashManager.log("onStart")

        // Ask only on an idle landing screen. onStart also handles returning to an existing task.
        if (documentFragment == null && loadOnStart == null) {
            if (leftForOwnActivity) {
                leftForOwnActivity = false
            } else {
                askForReviewIfEarned()
            }
        }

        val loadOnStart = this.loadOnStart ?: return

        // loadOnStart either came from an external intent or from a restored
        // instance state, in which case documentOpenedExternally was restored too
        loadUri(loadOnStart, documentOpenedExternally)

        this.loadOnStart = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(SAVED_KEY_LAST_CACHE_URI, lastUri)
        outState.putBoolean(SAVED_KEY_OPENED_EXTERNALLY, documentOpenedExternally)
        outState.putBoolean(SAVED_KEY_LEFT_FOR_OWN_ACTIVITY, leftForOwnActivity)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // the banner's size follows the orientation; the consent flow behind it does not
        adManager.refreshAds()
    }

    fun requestSave() {
        val documentFragment = this.documentFragment ?: return

        val lastSaveUri = this.lastSaveUri
        if (lastSaveUri != null) {
            documentFragment.save(lastSaveUri)

            return
        }

        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            intent.type = documentFragment.lastFileType

            // the picker opens with an empty name field otherwise, and what the user wants to
            // call the copy is almost always what the document is already called
            documentFragment.lastFilename?.let { intent.putExtra(Intent.EXTRA_TITLE, it) }

            leftForOwnActivity = true
            createDocumentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // a device with nothing handling ACTION_CREATE_DOCUMENT - there is nowhere to save to
            crashManager.log(e)
        }
    }

    private fun initializeManagers() {
        // the play services dialog can bring us back here; the first manager still owns a slot
        if (::adManager.isInitialized) {
            adManager.destroyAds()
        }

        // the ad and consent sdks are the only thing left that needs play services on the device,
        // and a flavor without them never asks - the dialog would offer a fix for nothing
        val adsAvailable =
            Features.withAds && PlayServices.isAvailableOrOffersFix(this, GOOGLE_REQUEST_CODE)

        crashManager = CrashManager()
        crashManager.initialize()

        analyticsManager = AnalyticsManager()
        analyticsManager.initialize(this)

        adManager = AdManager()
        adManager.setEnabled(!IS_TESTING && adsAvailable)
        adManager.setAdContainer(adContainer)
        // the landing screen is drawn long before the consent update comes back, and the privacy
        // options row only exists once it has
        adManager.setConsentListener { landingFragment?.refresh() }
        adManager.setPurchaseListener { buyAdRemoval() }
        adManager.initialize(this, analyticsManager, crashManager)

        billingManager = BillingManager()
        billingManager.setEnabled(adsAvailable)
        billingManager.initialize(this, adManager)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val data = intent.data ?: return

        crashManager.log("onNewIntent loadUri")

        loadUri(data, true)

        analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            "other",
        )
    }

    // the play services availability dialog calls startActivityForResult() itself with a request
    // code we hand it, so its result cannot come back through an ActivityResultLauncher
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == GOOGLE_REQUEST_CODE) {
            initializeManagers()
        }
    }

    fun loadUri(uri: Uri) {
        loadUri(uri, false)
    }

    private fun loadUri(uri: Uri, openedExternally: Boolean) {
        documentOpenedExternally = openedExternally

        lastSaveUri = null
        lastUri = uri

        var documentFragment = this.documentFragment
        if (documentFragment == null) {
            landingContainer.visibility = View.GONE
            documentContainer.visibility = View.VISIBLE

            landingFragment?.setLandingVisible(false)

            // the manager can still be holding one the field has not been handed yet, e.g. after
            // the process was recreated - taking that one keeps whatever it had already loaded
            documentFragment =
                supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?
                    ?: DocumentFragment().also {
                        supportFragmentManager
                            .beginTransaction()
                            .replace(R.id.document_container, it, DOCUMENT_FRAGMENT_TAG)
                            .commitNow()
                    }

            // the insets arrived long before this fragment existed, and nothing asks for them again
            documentFragment.setBottomInset(bottomInset)

            this.documentFragment = documentFragment
        }

        crashManager.log("loading document at: $uri")
        analyticsManager.report(
            AnalyticsConstants.EVENT_VIEW_ITEM,
            AnalyticsConstants.PARAM_ITEM_NAME,
            uri.toString(),
        )

        // Keep grants through asynchronous loads. isRetained covers documents opened from recents.
        val isPersistentUri =
            PersistedUriPermissions.takeRead(this, uri) ||
                PersistedUriPermissions.isRetained(this, uri)

        documentFragment.loadUri(uri, isPersistentUri)
    }

    /**
     * A button of the open document was tapped. These used to be the toolbar menu, and the ids are
     * now [DocumentActions]' own - the handling stays here, where the action modes and the printing
     * manager already live.
     */
    fun onDocumentAction(action: Int) {
        val documentFragment = this.documentFragment

        when (action) {
            DocumentActions.ACTION_SEARCH -> {
                val findActionModeCallback = FindActionModeCallback(this)
                documentFragment?.pageView?.let { findActionModeCallback.setWebView(it) }
                currentActionMode = startSupportActionMode(findActionModeCallback)

                analyticsManager.report("menu_search")
                analyticsManager.report(AnalyticsConstants.EVENT_SEARCH)
            }

            DocumentActions.ACTION_OPEN_WITH -> {
                documentFragment?.openWith(this)

                analyticsManager.report("menu_open_with")
            }

            DocumentActions.ACTION_SAVE -> {
                documentFragment?.prepareSave({ requestSave() }, true)

                analyticsManager.report("menu_save")
            }

            DocumentActions.ACTION_SHARE -> {
                documentFragment?.share(this)

                analyticsManager.report("menu_share")
            }

            DocumentActions.ACTION_FULLSCREEN -> {
                if (fullscreen) {
                    analyticsManager.report("menu_fullscreen_leave")

                    leaveFullscreen()
                } else {
                    analyticsManager.report("menu_fullscreen_enter")

                    val insetsController =
                        WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())

                    // delay offer to wait for fullscreen animation to finish
                    handler.postDelayed(
                        {
                            if (isFinishing) {
                                return@postDelayed
                            }

                            offerPurchase()
                        },
                        1000,
                    )
                }

                fullscreen = !fullscreen

                updateDocumentActionsVisible()
            }

            DocumentActions.ACTION_NIGHT_MODE -> {
                val night = !NightModeSetting.isNight(this)

                analyticsManager.report(
                    if (night) "menu_night_mode_enter" else "menu_night_mode_leave"
                )

                // recreates the activity, the way a rotation does - and survives it the same way:
                // the loader is a ViewModel, and the fragment saves the document
                delegate.localNightMode = NightModeSetting.setNight(this, night)
            }

            DocumentActions.ACTION_DOCUMENT_DARKENING -> {
                analyticsManager.report("menu_document_darkening")

                documentFragment?.toggleDarkening()
            }

            DocumentActions.ACTION_PAGE_MARGINS -> {
                val margins = !PaginationSetting.isEnabled(this)

                analyticsManager.report(
                    if (margins) "menu_page_margins_on" else "menu_page_margins_off"
                )

                PaginationSetting.setEnabled(this, margins)

                documentFragment?.reloadForMargins()
            }

            DocumentActions.ACTION_PRINT -> {
                analyticsManager.report("menu_print")

                val original = documentFragment?.printableOriginal()
                if (original != null) {
                    printingManager.print(this, original)
                } else {
                    documentFragment?.pageView?.let { pageView ->
                        // Keep the page light until the print adapter finishes; the document may
                        // close meanwhile.
                        pageView.suspendDarkening()

                        val pageSize = documentFragment.pageSize
                        printingManager.print(this, pageView, pageSize) {
                            documentFragment?.pageView?.resumeDarkening()
                        }
                    }
                }
            }

            DocumentActions.ACTION_TTS -> {
                analyticsManager.report("menu_tts")

                documentFragment?.pageView?.let { pageView ->
                    val ttsActionMode = TtsActionModeCallback(this, pageView)
                    this.ttsActionMode = ttsActionMode

                    currentActionMode = startSupportActionMode(ttsActionMode)
                }
            }

            DocumentActions.ACTION_EDIT -> {
                analyticsManager.report("menu_edit")

                // the button stands on the core's answer, and every edition opens what it
                // names: what lite does not sell is the tool, which says so in the strip
                val kind = documentFragment?.editingKind ?: return
                if (!Features.offersEditing(kind)) {
                    return
                }

                documentFragment?.let { fragment ->
                    val editActionMode = EditActionModeCallback(this, fragment)
                    this.editActionMode = editActionMode

                    currentActionMode = startSupportActionMode(editActionMode)
                }
            }
        }
    }

    private fun offerPurchase() {
        if (billingManager.hasPurchased()) {
            return
        }

        analyticsManager.report("present_offer")
        SnackbarHelper.show(
            this,
            R.string.crouton_remove_ads,
            {
                analyticsManager.report("present_offer_clicked")

                buyAdRemoval()
            },
            isIndefinite = true,
            isError = false,
        )
    }

    /**
     * Hides document actions during fullscreen or action modes. Counts overlapping framework and
     * AppCompat modes.
     */
    private var actionModes = 0

    private fun updateDocumentActionsVisible() {
        documentFragment?.setActionsVisible(actionModes == 0 && !fullscreen)
    }

    // the appcompat ones, which is what startSupportActionMode() raises: find, tts and edit
    override fun onSupportActionModeStarted(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeStarted(mode)

        actionModes++

        updateDocumentActionsVisible()
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeFinished(mode)

        actionModes--

        updateDocumentActionsVisible()

        currentActionMode = null
        ttsActionMode = null
        editActionMode = null
    }

    // and the framework ones, which is what selecting text in the page raises
    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)

        actionModes++

        updateDocumentActionsVisible()
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        super.onActionModeFinished(mode)

        actionModes--

        updateDocumentActionsVisible()
    }

    /**
     * Whether billing offers ad removal. Queried after manager initialization and Play Services
     * retries.
     */
    fun offersAdRemoval(): Boolean =
        ::billingManager.isInitialized && !billingManager.hasPurchased()

    /**
     * Whether the consent decision can still be withdrawn, which is when the ump sdk requires an
     * app to offer a way back into the form. Asked the same way as [offersAdRemoval], and it stays
     * true after an ad removal: withdrawal outlives the ads.
     */
    fun offersPrivacyOptions(): Boolean =
        ::adManager.isInitialized && adManager.isPrivacyOptionsRequired()

    /** Only from a tap - the sdk preloads the form for exactly this. */
    fun showPrivacyOptions() {
        adManager.showPrivacyOptions()
    }

    fun buyAdRemoval() {
        analyticsManager.report(AnalyticsConstants.EVENT_ADD_TO_CART)

        // the play listing id is the applicationId, not the renamed java package
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=at.tomtasche.reader.pro"),
            )
        )
    }

    /** Says that what was just tried is pro's, and leads to the pro listing. Lite only. */
    fun offerPro(feature: ProFeature) {
        // the names OpenDocument.ios reports the same gate under
        analyticsManager.report("pro_gate_shown", "feature", feature.name.lowercase())

        AlertDialog.Builder(this)
            .setTitle(R.string.pro_offer_title)
            .setMessage(feature.message)
            .setPositiveButton(R.string.house_ad_cta_get_pro) { _, _ ->
                analyticsManager.report("pro_gate_tapped", "feature", feature.name.lowercase())

                buyAdRemoval()
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    /** What pro adds, as the reader runs into it. */
    enum class ProFeature(@param:StringRes val message: Int) {
        /** Formatting text past the highlighter, and starting or joining a paragraph. */
        FORMATTING(R.string.pro_offer_formatting),

        /** Formatting cells past the fill. */
        SHEET(R.string.pro_offer_sheet),

        /** Marking up a pdf past the highlighter. */
        PDF(R.string.pro_offer_markup),
    }

    /** What [buyAdRemoval] is for a build with no ad removal to sell. */
    fun openSponsorPage() {
        analyticsManager.report(AnalyticsConstants.EVENT_ADD_TO_CART)

        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors/opendocument-app"))
        )
    }

    private fun leaveFullscreen() {
        if (!fullscreen) {
            return
        }

        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())

        fullscreen = false

        updateDocumentActionsVisible()

        analyticsManager.report("fullscreen_end")
    }

    /** Returns to the landing screen while retaining the failure snackbar and its reopen action. */
    fun closeFailedDocument() {
        if (documentFragment == null) {
            return
        }

        analyticsManager.report("close_failed_document")

        closeDocument(keepMessage = true)
    }

    /**
     * Asks before walking away from edits that are only in the page, then runs [leave]. Saving does
     * not also leave: it opens the create-document picker, which still needs the page the edits
     * come from.
     */
    fun confirmLeavingEdits(leave: () -> Unit) {
        val documentFragment = this.documentFragment
        if (documentFragment == null || !documentFragment.hasUnsavedEdits()) {
            leave()

            return
        }

        analyticsManager.report("show_alert_unsaved_changes")

        AlertDialog.Builder(this)
            .setTitle(R.string.alert_unsaved_changes)
            .setMessage(R.string.alert_save_now)
            .setPositiveButton(R.string.action_edit_save) { _, _ ->
                analyticsManager.report("alert_unsaved_changes_yes")

                documentFragment.prepareSave({ requestSave() }, false)
            }
            .setNegativeButton(R.string.alert_discard_changes) { _, _ ->
                analyticsManager.report("alert_unsaved_changes_no")

                leave()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /** Called once a fresh open has rendered. */
    fun onDocumentShown() {
        if (!documentOpenedExternally || reviewRequested || !ReviewInvitation.isEarned(this)) {
            return
        }

        reviewRequested = true
        preparedReview = InAppReview.prepare(this, analyticsManager)
    }

    /** Deliberately not from [closeFailedDocument], the last moment on earth to ask for stars. */
    private fun askForReviewIfEarned() {
        if (reviewRequested || !ReviewInvitation.isEarned(this)) {
            return
        }

        reviewRequested = true
        InAppReview.request(this, analyticsManager) { ReviewInvitation.recordAsk(this) }
    }

    private fun closeDocument(keepMessage: Boolean = false) {
        // whatever the document had to say goes with it - an indefinite "could not be opened" is
        // about a document that is no longer on the screen. unless it is the reason we are leaving
        if (!keepMessage) {
            SnackbarHelper.dismiss(this)
        }

        // the fragment goes first, so finishing the edit mode does not ask about its edits again
        documentFragment?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()

            documentFragment = null
        }

        // an edit or tts mode would otherwise outlive the document it acts on
        currentActionMode?.finish()

        lastUri = null

        documentContainer.visibility = View.GONE
        landingContainer.visibility = View.VISIBLE

        // the fragment is only hidden, not stopped, so it has to be told to pick the document
        // that was just closed up into the recently opened list
        landingFragment?.setLandingVisible(true)

        analyticsManager.setCurrentScreen(this, "screen_main")
    }

    /**
     * The newest recent document the picker can be pointed at, so it opens in that folder rather
     * than in Recent. Only a document uri works; one handed over by ACTION_VIEW does not.
     */
    private fun lastPickedDocument(): Uri? =
        RecentDocumentsUtil.getRecentDocuments(this)
            .map { Uri.parse(it.uri) }
            .firstOrNull { DocumentsContract.isDocumentUri(this, it) }

    fun findDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // no EXTRA_MIME_TYPES: the picker leaves out what fails the filter rather than greying it,
        // and providers report types the core's table does not spell, so documents went missing
        intent.type = "*/*"

        lastPickedDocument()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }

        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Use the system picker for installed document providers.
        try {
            OpenFileIdling.increment()

            leftForOwnActivity = true
            openDocumentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            OpenFileIdling.decrement()

            crashManager.log(e)

            SnackbarHelper.show(
                this,
                R.string.crouton_error_open_app,
                { findDocument() },
                isIndefinite = true,
                isError = true,
            )
        }

        analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            "picker",
        )
    }

    override fun onPause() {
        ttsActionMode?.stop()

        if (::adManager.isInitialized) {
            adManager.pauseAds()
        }

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        if (::adManager.isInitialized) {
            adManager.resumeAds()
        }
    }

    override fun onDestroy() {
        // appcompat does not finish it for us, and TtsActionModeCallback only shuts its
        // engine down when the mode is destroyed
        currentActionMode?.finish()

        printingManager.close()

        adManager.destroyAds()

        try {
            // has thrown out of the WebView's focus handling for some users
            super.onDestroy()
        } catch (e: Exception) {
            crashManager.log(e)
        }
    }

    private companion object {
        const val SAVED_KEY_LAST_CACHE_URI = "LAST_CACHE_URI"
        const val SAVED_KEY_OPENED_EXTERNALLY = "OPENED_EXTERNALLY"
        const val SAVED_KEY_LEFT_FOR_OWN_ACTIVITY = "LEFT_FOR_OWN_ACTIVITY"
        const val GOOGLE_REQUEST_CODE = 1993
        const val DOCUMENT_FRAGMENT_TAG = "document_fragment"

        // taken from: https://stackoverflow.com/a/36829889/198996
        private fun isTesting(): Boolean =
            try {
                Class.forName("app.opendocument.droid.test.MainActivityTests")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

        val IS_TESTING = isTesting()
    }
}
