package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode as SupportActionMode
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import app.opendocument.droid.R
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.LoaderService
import app.opendocument.droid.background.LoaderServiceQueue
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.PrintingManager
import app.opendocument.droid.background.UsageCounters
import app.opendocument.droid.nonfree.AdManager
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.BillingManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.nonfree.InAppReview
import app.opendocument.droid.ui.EditActionModeCallback
import app.opendocument.droid.ui.FindActionModeCallback
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.TtsActionModeCallback
import app.opendocument.droid.ui.widget.RecentDocumentDialogFragment
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class MainActivity : AppCompatActivity(), MenuProvider {

    private lateinit var handler: Handler

    private lateinit var landingContainer: View
    private lateinit var documentContainer: View
    private lateinit var adContainer: LinearLayout
    private var documentFragment: DocumentFragment? = null

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

                    closeDocument()

                    return
                }

                // fall through to the default behavior (close the activity)
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

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
    // the user opening the app
    private var leftForOwnActivity = false

    lateinit var loaderServiceQueue: LoaderServiceQueue
        private set

    private var service: LoaderService? = null

    /**
     * Whether [bindService] was called. Not the same as holding a [service]: the binder arrives
     * asynchronously, and an activity destroyed before it does still has to unbind.
     */
    private var isBound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                service?.setListener(null)

                service = null

                // the queue holds its own reference, and handing work to a service whose
                // background thread has quit drops it without telling anyone
                loaderServiceQueue.service = null
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

        // edge-to-edge is enforced from targetSdk 35 on: pad the root so content stays clear
        // of the system bars, cutouts and the keyboard
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

        title = ""

        onBackPressedDispatcher.addCallback(this, backCallback)

        loaderServiceQueue = LoaderServiceQueue()
        bindService(Intent(this, LoaderService::class.java), connection, BIND_AUTO_CREATE)
        isBound = true

        handler = Handler(Looper.getMainLooper())

        adContainer = findViewById(R.id.ad_container)
        landingContainer = findViewById(R.id.landing_container)
        documentContainer = findViewById(R.id.document_container)

        findViewById<View>(R.id.landing_intro_open).setOnClickListener {
            analyticsManager.report("intro_open")
            findDocument()
        }
        findViewById<View>(R.id.landing_open_fab).setOnClickListener {
            analyticsManager.report("fab_open")
            findDocument()
        }

        printingManager = PrintingManager()
        initializeProprietaryLibraries()

        initializeCatchAllSwitch()

        // reclaims the grants of documents that dropped off the recent list; hits the
        // filesystem and the permission binder, so off the main thread
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

        addMenuProvider(this, this)
    }

    override fun onStart() {
        super.onStart()

        documentFragment =
            supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

        if (documentFragment != null) {
            landingContainer.visibility = View.GONE
            documentContainer.visibility = View.VISIBLE
        }

        crashManager.log("onStart")

        // not in onCreate: a launcher tap onto a live task resumes rather than creates,
        // and those opens count too
        if (documentFragment == null && loadOnStart == null) {
            if (leftForOwnActivity) {
                leftForOwnActivity = false
            } else {
                InAppReview.requestIfEarned(
                    this,
                    analyticsManager,
                    UsageCounters.recordAppOpen(this),
                )
            }
        }

        val loadOnStart = this.loadOnStart ?: return

        // loadOnStart either came from an external intent or from a restored
        // instance state, in which case documentOpenedExternally was restored too
        loadUri(loadOnStart, documentOpenedExternally)

        this.loadOnStart = null
    }

    private fun initializeCatchAllSwitch() {
        val isCatchAllEnabled = CatchAllSetting.applyOnLaunch(this)

        val catchAllSwitch = findViewById<SwitchCompat>(R.id.landing_catch_all)

        catchAllSwitch.setOnCheckedChangeListener { _, isChecked ->
            CatchAllSetting.setEnabled(this, isChecked)
        }

        catchAllSwitch.isChecked = isCatchAllEnabled

        analyticsManager.report(
            if (isCatchAllEnabled) "catch_all_enabled" else "catch_all_disabled"
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(SAVED_KEY_LAST_CACHE_URI, lastUri)
        outState.putBoolean(SAVED_KEY_OPENED_EXTERNALLY, documentOpenedExternally)
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
        // the menu is built long before the consent update comes back
        adManager.setConsentListener { invalidateMenu() }
        adManager.setPurchaseListener { buyAdRemoval() }
        adManager.initialize(this, analyticsManager, crashManager)

        billingManager = BillingManager()
        billingManager.setEnabled(useProprietaryLibraries)
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

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)

        if (billingManager.hasPurchased()) {
            menu.findItem(R.id.menu_remove_ads).isVisible = false
        }

        menu.findItem(R.id.menu_privacy_options).isVisible = adManager.isPrivacyOptionsRequired()
    }

    // the play services availability dialog calls startActivityForResult() itself with a request
    // code we hand it, so its result cannot come back through an ActivityResultLauncher
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
            documentFragment =
                supportFragmentManager.findFragmentByTag(DOCUMENT_FRAGMENT_TAG) as DocumentFragment?

            landingContainer.visibility = View.GONE
            documentContainer.visibility = View.VISIBLE

            if (documentFragment == null) {
                documentFragment = DocumentFragment()
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.document_container, documentFragment, DOCUMENT_FRAGMENT_TAG)
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

        // the grant has to outlive this call: the load is only queued, so the stream is opened
        // long after we return. releasing it here also dropped the persisted grant, leaving the
        // recent documents unreadable - prune() reclaims instead. isRetained covers a document
        // below a granted tree, which cannot be persisted on its own
        val isPersistentUri =
            PersistedUriPermissions.takeRead(this, uri) ||
                PersistedUriPermissions.isRetained(this, uri)

        documentFragment.loadUri(uri, isPersistentUri)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val documentFragment = this.documentFragment

        when (item.itemId) {
            R.id.menu_search -> {
                val findActionModeCallback = FindActionModeCallback(this)
                documentFragment?.pageView?.let { findActionModeCallback.setWebView(it) }
                currentActionMode = startSupportActionMode(findActionModeCallback)

                analyticsManager.report("menu_search")
                analyticsManager.report(AnalyticsConstants.EVENT_SEARCH)
            }

            R.id.menu_open -> {
                findDocument()

                analyticsManager.report("menu_open")
                analyticsManager.report(
                    AnalyticsConstants.EVENT_SELECT_CONTENT,
                    AnalyticsConstants.PARAM_CONTENT_TYPE,
                    "choose",
                )
            }

            R.id.menu_open_with -> {
                documentFragment?.openWith(this)

                analyticsManager.report("menu_open_with")
            }

            R.id.menu_save -> {
                documentFragment?.prepareSave({ requestSave() }, true)

                analyticsManager.report("menu_save")
            }

            R.id.menu_share -> {
                documentFragment?.share(this)

                analyticsManager.report("menu_share")
            }

            R.id.menu_remove_ads -> {
                analyticsManager.report("menu_remove_ads")

                buyAdRemoval()
            }

            R.id.menu_privacy_options -> {
                analyticsManager.report("menu_privacy_options")

                adManager.showPrivacyOptions()
            }

            R.id.menu_fullscreen -> {
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

                    supportActionBar?.hide()

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
            }

            R.id.menu_print -> {
                analyticsManager.report("menu_print")

                documentFragment?.pageView?.let { pageView ->
                    // printing a dark page wastes ink, but the setting has to come back
                    // afterwards or the rest of the document is read in light mode. only once
                    // the job is over: the print framework reads the page well after print()
                    val wasDark = pageView.isDarkEnabled

                    pageView.toggleDarkMode(false)

                    printingManager.print(this, pageView) { pageView.toggleDarkMode(wasDark) }
                }
            }

            R.id.menu_tts -> {
                analyticsManager.report("menu_tts")

                documentFragment?.pageView?.let { pageView ->
                    val ttsActionMode = TtsActionModeCallback(this, pageView)
                    this.ttsActionMode = ttsActionMode

                    currentActionMode = startSupportActionMode(ttsActionMode)
                }
            }

            R.id.menu_edit -> {
                analyticsManager.report("menu_edit")

                documentFragment?.let { fragment ->
                    currentActionMode =
                        startSupportActionMode(EditActionModeCallback(this, fragment))
                }
            }

            else -> return super.onOptionsItemSelected(item)
        }

        return true
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
            true,
            false,
        )
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        super.onActionModeFinished(mode)

        currentActionMode = null
        ttsActionMode = null
    }

    private fun showRecent() {
        val transaction = supportFragmentManager.beginTransaction()

        val chooserDialog: DialogFragment = RecentDocumentDialogFragment()
        chooserDialog.show(transaction, RecentDocumentDialogFragment.FRAGMENT_TAG)

        analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            "recent",
        )
    }

    private fun buyAdRemoval() {
        analyticsManager.report(AnalyticsConstants.EVENT_ADD_TO_CART)

        // the play listing id is the applicationId, not the renamed java package
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

        supportActionBar?.show()

        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())

        fullscreen = false

        analyticsManager.report("fullscreen_end")
    }

    // also called by DocumentFragment, so an error dialog comes up over the landing screen
    fun closeDocument() {
        // the fragment goes first: finishing an edit mode reloads the document it acts on, and
        // that load would put a progress dialog up over a fragment that is about to be removed.
        // reloadUri() is a no-op once it is detached
        documentFragment?.let { fragment ->
            removeMenuProvider(fragment)

            supportFragmentManager.beginTransaction().remove(fragment).commitNow()

            documentFragment = null
        }

        // an edit or tts mode would otherwise outlive the document it acts on
        currentActionMode?.finish()

        lastUri = null

        documentContainer.visibility = View.GONE
        landingContainer.visibility = View.VISIBLE

        analyticsManager.setCurrentScreen(this, "screen_main")
    }

    fun findDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val packageManager = packageManager
        val targetList: List<ResolveInfo> =
            packageManager.queryIntentActivities(intent, 0).filter { target ->
                target.activityInfo.packageName == packageName || target.activityInfo.exported
            }

        val targetNames =
            (targetList.map { it.loadLabel(packageManager).toString() } +
                    getString(R.string.menu_recent))
                .toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_choose_filemanager)
        builder.setItems(targetNames) { dialog, which ->
            if (which == targetNames.size - 1) {
                showRecent()

                return@setItems
            }

            val target = targetList[which]

            intent.component =
                ComponentName(target.activityInfo.packageName, target.activityInfo.name)

            try {
                OpenFileIdling.increment()

                leftForOwnActivity = true
                openDocumentLauncher.launch(intent)
            } catch (e: Exception) {
                OpenFileIdling.decrement()

                crashManager.log(e)

                SnackbarHelper.show(
                    this,
                    R.string.crouton_error_open_app,
                    { findDocument() },
                    true,
                    true,
                )
            }

            analyticsManager.report(
                AnalyticsConstants.EVENT_SELECT_CONTENT,
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                target.activityInfo.packageName,
            )

            dialog.dismiss()
        }
        builder.show()
    }

    override fun onPause() {
        ttsActionMode?.stop()

        super.onPause()
    }

    override fun onDestroy() {
        // appcompat does not finish it for us, and TtsActionModeCallback only shuts its
        // engine down when the mode is destroyed
        currentActionMode?.finish()

        if (isBound) {
            unbindService(connection)
            isBound = false
        }

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
