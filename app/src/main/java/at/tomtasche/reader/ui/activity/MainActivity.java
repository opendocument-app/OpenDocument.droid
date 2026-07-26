package at.tomtasche.reader.ui.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.LoaderService;
import at.tomtasche.reader.background.LoaderServiceQueue;
import at.tomtasche.reader.background.PrintingManager;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.nonfree.AnalyticsConstants;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.BillingManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.OpenFileIdling;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.RecentDocumentDialogFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MenuProvider {

    // taken from: https://stackoverflow.com/a/36829889/198996
    private static boolean isTesting() {
        try {
            Class.forName("at.tomtasche.reader.test.MainActivityTests");
            return true;
        } catch (ClassNotFoundException e) {
        }
        return false;
    }

    private static final String SAVED_KEY_LAST_CACHE_URI = "LAST_CACHE_URI";
    private static final String SAVED_KEY_OPENED_EXTERNALLY = "OPENED_EXTERNALLY";
    private static final boolean IS_TESTING = isTesting();
    private static final int GOOGLE_REQUEST_CODE = 1993;
    private static final String DOCUMENT_FRAGMENT_TAG = "document_fragment";
    private static final int CREATE_CODE = 4213;

    private Handler handler;

    private View landingContainer;
    private View documentContainer;
    private LinearLayout adContainer;
    private DocumentFragment documentFragment;

    private boolean fullscreen;
    // With targetSdk 36 predictive back is enabled by default and neither KEYCODE_BACK
    // nor onBackPressed() are delivered anymore, so back is intercepted via the
    // OnBackPressedDispatcher instead.
    private final OnBackPressedCallback backCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (fullscreen) {
                        leaveFullscreen();

                        return;
                    }

                    if (documentFragment != null && !documentOpenedExternally) {
                        analyticsManager.report("back_to_landing");

                        closeDocument();

                        return;
                    }

                    // fall through to the default behavior (close the activity)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            };
    private TtsActionModeCallback ttsActionMode;
    private EditActionModeCallback editActionMode;

    private CrashManager crashManager;
    private AnalyticsManager analyticsManager;
    private AdManager adManager;
    private BillingManager billingManager;
    private PrintingManager printingManager;

    private Uri lastUri;
    private Uri loadOnStart;
    private Uri lastSaveUri;

    // documents opened from another app keep the default back behavior (returning
    // to that app), while documents opened from within the app go back to the
    // landing screen instead of closing the app
    private boolean documentOpenedExternally;

    private LoaderServiceQueue serviceQueue;
    private LoaderService service;
    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (service != null) {
                        service.setListener(null);
                    }

                    service = null;
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    service = ((LoaderService.LoaderBinder) binder).getService();

                    serviceQueue.setService(service);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        // Edge-to-edge is enforced from targetSdk 35 on: pad the root view so content
        // stays clear of the system bars, display cutouts and the keyboard. On older
        // devices the window does not extend under the bars and the insets are zero.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main_root),
                (view, windowInsets) -> {
                    Insets insets =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                                            | WindowInsetsCompat.Type.ime());
                    view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });

        setTitle("");

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        serviceQueue = new LoaderServiceQueue();
        Intent intent = new Intent(this, LoaderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        handler = new Handler(Looper.getMainLooper());

        adContainer = findViewById(R.id.ad_container);
        landingContainer = findViewById(R.id.landing_container);
        documentContainer = findViewById(R.id.document_container);

        findViewById(R.id.landing_intro_open)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                analyticsManager.report("intro_open");
                                findDocument();
                            }
                        });
        findViewById(R.id.landing_open_fab)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                analyticsManager.report("fab_open");
                                findDocument();
                            }
                        });

        printingManager = new PrintingManager();
        initializeProprietaryLibraries();

        initializeCatchAllSwitch();

        crashManager.log("onCreate");

        documentFragment =
                (DocumentFragment)
                        getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

        if (savedInstanceState != null) {
            documentOpenedExternally =
                    savedInstanceState.getBoolean(SAVED_KEY_OPENED_EXTERNALLY, false);
        }

        if (documentFragment != null && documentFragment.hasLastResult()) {
            // nothing else to do

            crashManager.log("onCreate nothing");
        } else if (savedInstanceState != null
                && savedInstanceState.containsKey(SAVED_KEY_LAST_CACHE_URI)) {
            loadOnStart = savedInstanceState.getParcelable(SAVED_KEY_LAST_CACHE_URI);

            crashManager.log("onCreate loadOnStart");
        } else if (documentFragment == null) {
            crashManager.log("onCreate from background");

            // app was started from another app, but make sure not to load it twice
            // (i.e. after bringing app back from background)
            if (getIntent().getData() != null) {
                loadOnStart = getIntent().getData();
                documentOpenedExternally = true;

                analyticsManager.report(
                        AnalyticsConstants.EVENT_SELECT_CONTENT,
                        AnalyticsConstants.PARAM_CONTENT_TYPE,
                        "other");
            } else {
                analyticsManager.setCurrentScreen(this, "screen_main");
            }
        } else {
            crashManager.log("onCreate empty");

            analyticsManager.setCurrentScreen(this, "screen_main");
        }

        addMenuProvider(this, this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        documentFragment =
                (DocumentFragment)
                        getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

        if (documentFragment != null) {
            landingContainer.setVisibility(View.GONE);
            documentContainer.setVisibility(View.VISIBLE);
        }

        crashManager.log("onStart");

        if (loadOnStart != null) {
            // loadOnStart either came from an external intent or from a restored
            // instance state, in which case documentOpenedExternally was restored too
            loadUri(loadOnStart, documentOpenedExternally);

            loadOnStart = null;
        }
    }

    private static final String PREF_CATCH_ALL_ENABLED = "catch_all_enabled";

    private void initializeCatchAllSwitch() {
        ComponentName catchAllComponent =
                new ComponentName(this, "at.tomtasche.reader.ui.activity.MainActivity.CATCH_ALL");
        ComponentName strictCatchComponent =
                new ComponentName(
                        this, "at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH");

        SharedPreferences preferences = getDefaultSharedPreferences();

        // catch-all is only active if the user explicitly opted in. new installs and
        // existing users who never touched the switch default to STRICT_CATCH, so we
        // no longer volunteer to open unrelated file types like contacts (issue #477).
        boolean isCatchAllEnabled = preferences.getBoolean(PREF_CATCH_ALL_ENABLED, false);

        // retoggle components for users upgrading to latest version of app
        toggleComponent(catchAllComponent, isCatchAllEnabled);
        toggleComponent(strictCatchComponent, !isCatchAllEnabled);

        SwitchCompat catchAllSwitch = findViewById(R.id.landing_catch_all);

        catchAllSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        preferences.edit().putBoolean(PREF_CATCH_ALL_ENABLED, isChecked).apply();

                        toggleComponent(catchAllComponent, isChecked);
                        toggleComponent(strictCatchComponent, !isChecked);
                    }
                });

        catchAllSwitch.setChecked(isCatchAllEnabled);

        analyticsManager.report(isCatchAllEnabled ? "catch_all_enabled" : "catch_all_disabled");
    }

    /**
     * The preferences android.preference.PreferenceManager used to hand out. That class is
     * deprecated and its androidx replacement lives in a whole preference-ui library we do not
     * otherwise need, so the default file is opened by name instead - keeping the existing
     * catch-all setting of users who upgrade.
     */
    private SharedPreferences getDefaultSharedPreferences() {
        return getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    private void toggleComponent(ComponentName component, boolean enabled) {
        int newState =
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager()
                .setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(SAVED_KEY_LAST_CACHE_URI, lastUri);
        outState.putBoolean(SAVED_KEY_OPENED_EXTERNALLY, documentOpenedExternally);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        adManager.showGoogleAds();
    }

    public void requestSave() {
        if (lastSaveUri != null) {
            documentFragment.save(lastSaveUri);

            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.setType(documentFragment.getLastFileType());

            startActivityForResult(intent, CREATE_CODE);
        } catch (ActivityNotFoundException e) {
            // happens on a variety devices, e.g. Samsung Galaxy Tab4 7.0 with Android 4.4.2
            crashManager.log(e);
        }
    }

    private void initializeProprietaryLibraries() {
        boolean useProprietaryLibraries = !getResources().getBoolean(R.bool.DISABLE_TRACKING);

        if (useProprietaryLibraries) {
            GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
            int googleAvailability = googleApi.isGooglePlayServicesAvailable(this);
            if (googleAvailability != ConnectionResult.SUCCESS) {
                useProprietaryLibraries = false;
                googleApi.getErrorDialog(this, googleAvailability, GOOGLE_REQUEST_CODE).show();
            }
        }

        crashManager = new CrashManager();
        crashManager.setEnabled(useProprietaryLibraries);
        crashManager.initialize();

        analyticsManager = new AnalyticsManager();
        analyticsManager.setEnabled(useProprietaryLibraries);
        analyticsManager.initialize(this);

        adManager = new AdManager();
        adManager.setEnabled(!IS_TESTING && useProprietaryLibraries);
        adManager.setAdContainer(adContainer);
        adManager.initialize(this, analyticsManager, crashManager);

        billingManager = new BillingManager();
        billingManager.setEnabled(useProprietaryLibraries);
        billingManager.initialize(this, analyticsManager, adManager);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getData() != null) {
            crashManager.log("onNewIntent loadUri");

            loadUri(intent.getData(), true);

            analyticsManager.report(
                    AnalyticsConstants.EVENT_SELECT_CONTENT,
                    AnalyticsConstants.PARAM_CONTENT_TYPE,
                    "other");
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu);

        if (billingManager.hasPurchased()) {
            menu.findItem(R.id.menu_remove_ads).setVisible(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == GOOGLE_REQUEST_CODE) {
            initializeProprietaryLibraries();
        } else if (requestCode == CREATE_CODE && intent != null) {
            lastSaveUri = intent.getData();

            documentFragment.save(lastSaveUri);
        } else if (intent != null) {
            crashManager.log("onActivityResult loadUri");

            Uri uri = intent.getData();
            if (requestCode == 42 && resultCode == Activity.RESULT_OK && uri != null) {
                OpenFileIdling.decrement();

                loadUri(uri);
            }
        }
    }

    public void loadUri(Uri uri) {
        loadUri(uri, false);
    }

    private void loadUri(Uri uri, boolean openedExternally) {
        documentOpenedExternally = openedExternally;

        lastSaveUri = null;
        lastUri = uri;

        if (documentFragment == null) {
            documentFragment =
                    (DocumentFragment)
                            getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

            landingContainer.setVisibility(View.GONE);
            documentContainer.setVisibility(View.VISIBLE);

            if (documentFragment == null) {
                documentFragment = new DocumentFragment();
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.document_container, documentFragment, DOCUMENT_FRAGMENT_TAG)
                        .commitNow();
            }
        }

        crashManager.log("loading document at: " + uri.toString());
        analyticsManager.report(
                AnalyticsConstants.EVENT_VIEW_ITEM,
                AnalyticsConstants.PARAM_ITEM_NAME,
                uri.toString());

        boolean isPersistentUri = false;
        try {
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContentResolver()
                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            isPersistentUri = true;
        } catch (Exception e) {
            // some providers don't support persisted permissions
            crashManager.log(e);
        }

        documentFragment.loadUri(uri, isPersistentUri);

        try {
            getContentResolver()
                    .releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            crashManager.log(e);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_search) {
            FindActionModeCallback findActionModeCallback = new FindActionModeCallback(this);
            findActionModeCallback.setWebView(documentFragment.getPageView());
            startSupportActionMode(findActionModeCallback);

            analyticsManager.report("menu_search");
            analyticsManager.report(AnalyticsConstants.EVENT_SEARCH);
        } else if (itemId == R.id.menu_open) {
            findDocument();

            analyticsManager.report("menu_open");
            analyticsManager.report(
                    AnalyticsConstants.EVENT_SELECT_CONTENT,
                    AnalyticsConstants.PARAM_CONTENT_TYPE,
                    "choose");
        } else if (itemId == R.id.menu_open_with) {
            documentFragment.openWith(this);

            analyticsManager.report("menu_open_with");
        } else if (itemId == R.id.menu_save) {
            documentFragment.prepareSave(
                    new Runnable() {
                        @Override
                        public void run() {
                            requestSave();
                        }
                    },
                    true);

            analyticsManager.report("menu_save");
        } else if (itemId == R.id.menu_share) {
            documentFragment.share(this);

            analyticsManager.report("menu_share");
        } else if (itemId == R.id.menu_remove_ads) {
            analyticsManager.report("menu_remove_ads");

            buyAdRemoval();
        } else if (itemId == R.id.menu_fullscreen) {
            if (fullscreen) {
                analyticsManager.report("menu_fullscreen_leave");

                leaveFullscreen();
            } else {
                analyticsManager.report("menu_fullscreen_enter");

                WindowInsetsControllerCompat insetsController =
                        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
                insetsController.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                insetsController.hide(WindowInsetsCompat.Type.statusBars());

                getSupportActionBar().hide();

                // delay offer to wait for fullscreen animation to finish
                handler.postDelayed(
                        new Runnable() {

                            @Override
                            public void run() {
                                if (isFinishing()) {
                                    return;
                                }

                                offerPurchase();
                            }
                        },
                        1000);
            }

            fullscreen = !fullscreen;
        } else if (itemId == R.id.menu_print) {
            analyticsManager.report("menu_print");

            documentFragment.getPageView().toggleDarkMode(false);

            printingManager.print(this, documentFragment.getPageView());
        } else if (itemId == R.id.menu_tts) {
            analyticsManager.report("menu_tts");

            ttsActionMode = new TtsActionModeCallback(this, documentFragment.getPageView());
            startSupportActionMode(ttsActionMode);
        } else if (itemId == R.id.menu_edit) {
            analyticsManager.report("menu_edit");

            editActionMode = new EditActionModeCallback(this, documentFragment);
            startSupportActionMode(editActionMode);
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void offerPurchase() {
        if (billingManager.hasPurchased()) {
            return;
        }

        analyticsManager.report("present_offer");
        SnackbarHelper.show(
                this,
                R.string.crouton_remove_ads,
                new Runnable() {

                    @Override
                    public void run() {
                        analyticsManager.report("present_offer_clicked");

                        buyAdRemoval();
                    }
                },
                true,
                false);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);

        editActionMode = null;
        ttsActionMode = null;
    }

    private void showRecent() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        DialogFragment chooserDialog = new RecentDocumentDialogFragment();
        chooserDialog.show(transaction, RecentDocumentDialogFragment.FRAGMENT_TAG);

        analyticsManager.report(
                AnalyticsConstants.EVENT_SELECT_CONTENT,
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                "recent");
    }

    private void buyAdRemoval() {
        analyticsManager.report(AnalyticsConstants.EVENT_ADD_TO_CART);

        startActivity(
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                                "https://play.google.com/store/apps/details?id=at.tomtasche.reader.pro")));
    }

    private void leaveFullscreen() {
        if (!fullscreen) {
            return;
        }

        getSupportActionBar().show();

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.statusBars());

        fullscreen = false;

        analyticsManager.report("fullscreen_end");
    }

    private void closeDocument() {
        if (documentFragment != null) {
            removeMenuProvider(documentFragment);

            getSupportFragmentManager().beginTransaction().remove(documentFragment).commitNow();

            documentFragment = null;
        }

        ActionBar bar = getSupportActionBar();
        bar.removeAllTabs();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

        lastUri = null;

        documentContainer.setVisibility(View.GONE);
        landingContainer.setVisibility(View.VISIBLE);

        analyticsManager.setCurrentScreen(this, "screen_main");
    }

    public void findDocument() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PackageManager pm = getPackageManager();
        List<ResolveInfo> allTargets = pm.queryIntentActivities(intent, 0);
        List<ResolveInfo> targetList = new LinkedList<>();
        for (int i = 0; i < allTargets.size(); i++) {
            ResolveInfo target = allTargets.get(i);
            if (!target.activityInfo.packageName.equals(getPackageName())
                    && !target.activityInfo.exported) {
                continue;
            }

            targetList.add(target);
        }

        String[] targetNames = new String[targetList.size() + 1];
        for (int i = 0; i < targetList.size(); i++) {
            targetNames[i] = targetList.get(i).loadLabel(pm).toString();
        }
        targetNames[targetNames.length - 1] = getString(R.string.menu_recent);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_choose_filemanager);
        builder.setItems(
                targetNames,
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == targetNames.length - 1) {
                            showRecent();

                            return;
                        }

                        ResolveInfo target = targetList.get(which);
                        if (target == null) {
                            return;
                        }

                        intent.setComponent(
                                new ComponentName(
                                        target.activityInfo.packageName, target.activityInfo.name));

                        try {
                            OpenFileIdling.increment();

                            startActivityForResult(intent, 42);
                        } catch (Exception e) {
                            OpenFileIdling.decrement();

                            crashManager.log(e);

                            SnackbarHelper.show(
                                    MainActivity.this,
                                    R.string.crouton_error_open_app,
                                    new Runnable() {

                                        @Override
                                        public void run() {
                                            findDocument();
                                        }
                                    },
                                    true,
                                    true);
                        }

                        analyticsManager.report(
                                AnalyticsConstants.EVENT_SELECT_CONTENT,
                                AnalyticsConstants.PARAM_CONTENT_TYPE,
                                target.activityInfo.packageName);

                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    @Override
    public void onPause() {
        if (ttsActionMode != null) {
            ttsActionMode.stop();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            unbindService(connection);
        }

        printingManager.close();

        adManager.destroyAds();

        try {
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)

            super.onDestroy();
        } catch (Exception e) {
            crashManager.log(e);
        }
    }

    public CrashManager getCrashManager() {
        return crashManager;
    }

    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    public LoaderServiceQueue getLoaderServiceQueue() {
        return serviceQueue;
    }
}
