package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import app.opendocument.droid.background.ReviewInvitation
import app.opendocument.droid.background.SupportedDocumentTypes
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
                    // fall through to the default behavior (close the activity)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

    // kept because onPause has to stop it; the edit mode needs no such handle
    private var ttsActionMode: TtsActionModeCallback? = null

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
    // the user opening the app. saved: a rotation under the picker recreates the activity, and
    // onStart must not read the return trip as a quiet landing screen - a document is on its way
    private var leftForOwnActivity = false

    // requestReviewFlow answers asynchronously, so recordAsk lands only later - and a second
    // qualifying moment inside that window would pass isEarned again and spend two of the five
    // asks in one sitting. never reset: one hand-off per activity is plenty
    private var reviewRequested = false

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
            val outFile = result.data?.data ?: return@registerForActivityResult

            lastSaveUri = outFile

            documentFragment?.save(outFile)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // before super: appcompat applies a mode the moment it is told, so setting it afterwards
        // recreates the activity that has just been created
        delegate.localNightMode = NightModeSetting.mode(this)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.main)

        // nothing lives in the toolbar any more - the landing screen has its own header and the
        // document its buttons - so the bar would just be an empty strip of colour. Hiding it
        // rather than moving to a .NoActionBar theme keeps it as the host the action modes are
        // raised in: appcompat shows the container again for as long as one is up, and takes it
        // back down afterwards, so find, tts and edit still get their bar without the app
        // having to put one on screen itself.
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, backCallback)

        // before the fragments below: a restored DocumentFragment reaches for it in onViewCreated
        documentLoader = ViewModelProvider(this)[DocumentLoader::class.java]

        handler = Handler(Looper.getMainLooper())

        adContainer = findViewById(R.id.ad_container)
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
     * Edge-to-edge is enforced from targetSdk 35 on, so the window extends under the system bars
     * and something has to keep the content clear of them. On older devices it does not, and every
     * inset below is zero.
     *
     * The bars are not all held off the same way. Left, right and top are padding on the root, so
     * nothing at all is drawn behind a cutout or the status bar. The bottom is not: the document is
     * meant to run under the gesture bar - a page that stops short of it, with a strip of window
     * background below, looks like a rendering fault rather than a decision - so only the things
     * that would be *hidden* under it are lifted. The landing screen, whose list ends in a button,
     * and [DocumentActions], whose buttons sit in that very corner.
     *
     * The keyboard is the exception, and gets the document container itself: it covers half the
     * screen, and while a document is being edited the caret has to stay above it.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { view, windowInsets
            ->
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

        // the landing screen with nothing on its way to it. the only moment left for someone who
        // arrives with a document from another app: back takes them out of the app, not to the
        // list.
        // not in onCreate: a launcher tap onto a live task resumes rather than creates
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

            leftForOwnActivity = true
            createDocumentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // a device with nothing handling ACTION_CREATE_DOCUMENT - there is nowhere to save to
            crashManager.log(e)
        }
    }

    private fun initializeManagers() {
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

        // the grant has to outlive this call: the load is only queued, so the stream is opened
        // long after we return. releasing it here also dropped the persisted grant, leaving the
        // recent documents unreadable - prune() reclaims instead. isRetained covers a uri that
        // did not arrive on an intent of ours, such as one tapped in the recently opened list
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

                documentFragment?.pageView?.let { pageView ->
                    // printing a dark page wastes ink, so the page is held light for as long as
                    // the framework is reading it - which is long after print() returns. what it
                    // is given back to is looked up again: a document closed meanwhile took its
                    // WebView with it, and the one that replaced it was never suspended
                    pageView.suspendDarkening()

                    printingManager.print(this, pageView) {
                        documentFragment?.pageView?.resumeDarkening()
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

                documentFragment?.let { fragment ->
                    currentActionMode =
                        startSupportActionMode(EditActionModeCallback(this, fragment))
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
     * The buttons of the document are only up while nothing else is using the screen: an action
     * mode has taken the toolbar over and brought its own controls, and fullscreen is for reading.
     *
     * Counted rather than a flag, because the two kinds of action mode overlap - selecting text in
     * the page starts a framework one on top of the appcompat one that edit mode is.
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
     * Whether the ad removal is still worth offering: never in pro, where the purchase is implied,
     * and not once it has been bought.
     *
     * The landing screen asks rather than being told, because billing is set up by
     * [initializeManagers] - which can run a second time, after the play services dialog - and it
     * is not something the ViewModel could read off disk itself.
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

    /**
     * A load failed and the app has nothing left to try with the file - go back to the list rather
     * than leave an empty page on the screen with a bar over it.
     *
     * The bar is raised before this and deliberately survives it: with the document gone, it is the
     * only thing left saying why. Its action - handing the file to another app - needs no document,
     * which is exactly the case this is called in.
     */
    fun closeFailedDocument() {
        if (documentFragment == null) {
            return
        }

        analyticsManager.report("close_failed_document")

        closeDocument(keepMessage = true)
    }

    /**
     * Asks before walking away from a document being edited, then runs [leave]. Saving does not
     * also leave: it opens the create-document picker, which still needs the page the diff comes
     * from.
     */
    private fun confirmLeavingEdits(leave: () -> Unit) {
        val documentFragment = this.documentFragment
        if (documentFragment == null || !documentFragment.isEditing()) {
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

        // the fragment goes first: finishing an edit mode reloads the document it acts on, and
        // that load would put a progress dialog up over a fragment that is about to be removed.
        // reloadUri() is a no-op once it is detached
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
     * What the picker is told to offer: everything the app can open, plus the type that means
     * nothing at all.
     *
     * This is what makes it a document picker rather than a file picker - with it the image, audio
     * and video filters come off the screen, and a phone full of photos does not have to be walked
     * past to reach a document. Setting a starting folder was the other way of doing this and is
     * the wrong one: documents are wherever the user put them, mostly Downloads, and sending
     * everyone to Documents is only right for the people who use that folder.
     *
     * [GENERIC_MIME_TYPE] is in the list because a filter is worse than no filter when it hides a
     * file the app could open. Providers regularly volunteer nothing better than
     * application/octet-stream for a real document - the same reason [SupportedDocumentTypes] keeps
     * an extension fallback - and a filtered picker does not grey those out, it leaves them out
     * altogether. The price is that other unnamed binaries stay listed too, which is the harmless
     * half of the trade.
     *
     * Images are not here, though [CoreLoader] shows them: this is a document reader's file picker,
     * and an image reaches it by being shared or opened from a gallery.
     */
    private fun pickableMimeTypes(): Array<String> =
        (SupportedDocumentTypes.MIME_TYPES + GENERIC_MIME_TYPE).toTypedArray()

    fun findDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"

        intent.putExtra(Intent.EXTRA_MIME_TYPES, pickableMimeTypes())

        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // straight to the system picker. this used to put an "Open document via:" dialog of our
        // own in front of it, listing every app that answers ACTION_OPEN_DOCUMENT - an extra tap
        // that duplicated what the picker itself already offers, since it can browse Drive,
        // Downloads, a usb stick and every installed file manager on its own.
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

        super.onPause()
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

        // what a provider volunteers when it has nothing better to say. included in the picker's
        // filter on purpose - see pickableMimeTypes
        const val GENERIC_MIME_TYPE = "application/octet-stream"

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
