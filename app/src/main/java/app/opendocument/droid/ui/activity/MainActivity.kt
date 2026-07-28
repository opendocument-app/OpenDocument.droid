package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ActionMode
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.opendocument.droid.R
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.LoaderService
import app.opendocument.droid.background.LoaderServiceQueue
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.PrintingManager
import app.opendocument.droid.nonfree.AdManager
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.BillingManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.ui.EditActionModeCallback
import app.opendocument.droid.ui.FindActionModeCallback
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.TtsActionModeCallback
import app.opendocument.droid.ui.widget.DocumentActions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class MainActivity : AppCompatActivity() {

    private lateinit var handler: Handler

    private lateinit var landingContainer: View
    private lateinit var documentContainer: View
    private lateinit var adContainer: LinearLayout
    private var documentFragment: DocumentFragment? = null

    private val landingFragment: LandingFragment?
        get() =
            supportFragmentManager.findFragmentByTag(LandingFragment.FRAGMENT_TAG)
                as LandingFragment?

    private var fullscreen = false

    // With targetSdk 36 predictive back is enabled by default and neither KEYCODE_BACK
    // nor onBackPressed() are delivered anymore, so back is intercepted via the
    // OnBackPressedDispatcher instead.
    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) {
                    leaveFullscreen()

                    return
                }

                if (documentFragment != null && !documentOpenedExternally) {
                    analyticsManager.report("back_to_landing")

                    closeDocument()

                    return
                }

                // fall through to the default behavior (close the activity)
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

    // kept because onPause has to stop it; the edit mode needs no such handle
    private var ttsActionMode: TtsActionModeCallback? = null

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

    // documents opened from another app keep the default back behavior (returning
    // to that app), while documents opened from within the app go back to the
    // landing screen instead of closing the app
    private var documentOpenedExternally = false

    lateinit var loaderServiceQueue: LoaderServiceQueue
        private set

    private var service: LoaderService? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                service?.setListener(null)

                service = null
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val service = (binder as LoaderService.LoaderBinder).getService()
                this@MainActivity.service = service

                loaderServiceQueue.service = service
            }
        }

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
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main)

        // Edge-to-edge is enforced from targetSdk 35 on: pad the root view so content
        // stays clear of the system bars, display cutouts and the keyboard. On older
        // devices the window does not extend under the bars and the insets are zero.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { view, windowInsets
            ->
            val insets =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // nothing lives in the toolbar any more - the landing screen has its own header and the
        // document its buttons - so the bar would just be an empty strip of colour. Hiding it
        // rather than moving to a .NoActionBar theme keeps it as the host the action modes are
        // raised in: appcompat shows the container again for as long as one is up, and takes it
        // back down afterwards, so find, tts and edit still get their bar without the app
        // having to put one on screen itself.
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, backCallback)

        loaderServiceQueue = LoaderServiceQueue()
        bindService(Intent(this, LoaderService::class.java), connection, BIND_AUTO_CREATE)

        handler = Handler(Looper.getMainLooper())

        adContainer = findViewById(R.id.ad_container)
        landingContainer = findViewById(R.id.landing_container)
        documentContainer = findViewById(R.id.document_container)

        if (supportFragmentManager.findFragmentByTag(LandingFragment.FRAGMENT_TAG) == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.landing_container, LandingFragment(), LandingFragment.FRAGMENT_TAG)
                .commitNow()
        }

        printingManager = PrintingManager()
        initializeProprietaryLibraries()

        // has to happen here rather than in LandingFragment: users upgrading from a version
        // with different alias defaults have to be corrected even when the app is launched
        // straight into a document and the landing screen is never shown
        val catchAllEnabled = CatchAllSetting.applyOnLaunch(this)
        analyticsManager.report(if (catchAllEnabled) "catch_all_enabled" else "catch_all_disabled")

        // reclaims the uri permissions of documents that have since dropped off the recently
        // opened list. touches the filesystem and the permission binder, so not on the main thread
        Thread { PersistedUriPermissions.prune(applicationContext) }.start()

        crashManager.log("onCreate")

        documentFragment =
            supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

        if (savedInstanceState != null) {
            documentOpenedExternally =
                savedInstanceState.getBoolean(SAVED_KEY_OPENED_EXTERNALLY, false)
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

    override fun onStart() {
        super.onStart()

        documentFragment =
            supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

        if (documentFragment != null) {
            landingContainer.visibility = View.GONE
            documentContainer.visibility = View.VISIBLE

            // a recreated LandingFragment starts out believing it is on screen, and its view model
            // survives with it - so one that was inside a folder would go on eating back presses
            // behind the document it was recreated underneath
            landingFragment?.setLandingVisible(false)
        }

        crashManager.log("onStart")

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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        adManager.showGoogleAds()
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

            createDocumentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // happens on a variety devices, e.g. Samsung Galaxy Tab4 7.0 with Android 4.4.2
            crashManager.log(e)
        }
    }

    private fun initializeProprietaryLibraries() {
        var useProprietaryLibraries = !resources.getBoolean(R.bool.DISABLE_TRACKING)

        if (useProprietaryLibraries) {
            val googleApi = GoogleApiAvailability.getInstance()
            val googleAvailability = googleApi.isGooglePlayServicesAvailable(this)
            if (googleAvailability != ConnectionResult.SUCCESS) {
                useProprietaryLibraries = false
                googleApi.getErrorDialog(this, googleAvailability, GOOGLE_REQUEST_CODE)?.show()
            }
        }

        crashManager = CrashManager()
        crashManager.setEnabled(useProprietaryLibraries)
        crashManager.initialize()

        analyticsManager = AnalyticsManager()
        analyticsManager.setEnabled(useProprietaryLibraries)
        analyticsManager.initialize(this)

        adManager = AdManager()
        adManager.setEnabled(!IS_TESTING && useProprietaryLibraries)
        adManager.setAdContainer(adContainer)
        adManager.initialize(this, analyticsManager, crashManager)

        billingManager = BillingManager()
        billingManager.setEnabled(useProprietaryLibraries)
        billingManager.initialize(this, analyticsManager, adManager)
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

    // The play services availability dialog calls startActivityForResult() itself with a
    // request code we hand it, so its result cannot be routed through an
    // ActivityResultLauncher. Everything the app launches on its own goes through the
    // launchers declared above.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == GOOGLE_REQUEST_CODE) {
            initializeProprietaryLibraries()
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

            this.documentFragment = documentFragment
        }

        crashManager.log("loading document at: $uri")
        analyticsManager.report(
            AnalyticsConstants.EVENT_VIEW_ITEM,
            AnalyticsConstants.PARAM_ITEM_NAME,
            uri.toString(),
        )

        // the grant has to outlive this call: loadUri only queues the load onto LoaderService,
        // so MetadataLoader opens the stream long after we return. releasing it here - as this
        // used to - also dropped the persisted grant, which left every uri in the recently
        // opened list unreadable on the next launch. PersistedUriPermissions.prune() reclaims
        // them instead, once nothing refers to them any more.
        //
        // takeRead fails for a document below a directory tree we were granted, because only a
        // uri that arrived on one of our own intents can be persisted - isRetained covers those,
        // so browsing to a document still records it as recent
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
                startSupportActionMode(findActionModeCallback)

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

            DocumentActions.ACTION_PRINT -> {
                analyticsManager.report("menu_print")

                documentFragment?.pageView?.let { pageView ->
                    pageView.disableDarkening()

                    printingManager.print(this, pageView)
                }
            }

            DocumentActions.ACTION_TTS -> {
                analyticsManager.report("menu_tts")

                documentFragment?.pageView?.let { pageView ->
                    val ttsActionMode = TtsActionModeCallback(this, pageView)
                    this.ttsActionMode = ttsActionMode

                    startSupportActionMode(ttsActionMode)
                }
            }

            DocumentActions.ACTION_EDIT -> {
                analyticsManager.report("menu_edit")

                documentFragment?.let { fragment ->
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
     * [initializeProprietaryLibraries] - which can run a second time, after the play services
     * dialog - and it is not something the ViewModel could read off disk itself.
     */
    fun offersAdRemoval(): Boolean =
        ::billingManager.isInitialized && !billingManager.hasPurchased()

    fun buyAdRemoval() {
        analyticsManager.report(AnalyticsConstants.EVENT_ADD_TO_CART)

        // the play listing id is the applicationId, which stays at.tomtasche.reader.pro
        // - it is not the java package and does not follow the namespace rename
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=at.tomtasche.reader.pro"),
            )
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

    private fun closeDocument() {
        documentFragment?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()

            documentFragment = null
        }

        lastUri = null

        documentContainer.visibility = View.GONE
        landingContainer.visibility = View.VISIBLE

        // the fragment is only hidden, not stopped, so it has to be told to pick the document
        // that was just closed up into the recently opened list
        landingFragment?.setLandingVisible(true)

        analyticsManager.setCurrentScreen(this, "screen_main")
    }

    fun findDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // straight to the system picker. this used to put an "Open document via:" dialog of our
        // own in front of it, listing every app that answers ACTION_OPEN_DOCUMENT - an extra tap
        // that duplicated what the picker itself already offers, since it can browse Drive,
        // Downloads, a usb stick and every installed file manager on its own.
        try {
            OpenFileIdling.increment()

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
        if (service != null) {
            unbindService(connection)
        }

        printingManager.close()

        adManager.destroyAds()

        try {
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)

            super.onDestroy()
        } catch (e: Exception) {
            crashManager.log(e)
        }
    }

    private companion object {
        const val SAVED_KEY_LAST_CACHE_URI = "LAST_CACHE_URI"
        const val SAVED_KEY_OPENED_EXTERNALLY = "OPENED_EXTERNALLY"
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
