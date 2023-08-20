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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.LinkedList;
import java.util.List;

import at.tomtasche.reader.R;
import at.tomtasche.reader.background.LoaderService;
import at.tomtasche.reader.background.LoaderServiceQueue;
import at.tomtasche.reader.background.PrintingManager;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.BillingManager;
import at.tomtasche.reader.nonfree.ConfigManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.RecentDocumentDialogFragment;

public class MainActivity extends AppCompatActivity {

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
    private TtsActionModeCallback ttsActionMode;
    private EditActionModeCallback editActionMode;

    private CrashManager crashManager;
    private ConfigManager configManager;
    private AnalyticsManager analyticsManager;
    private AdManager adManager;
    private BillingManager billingManager;
    private PrintingManager printingManager;

    private Uri lastUri;
    private Uri loadOnStart;
    private Uri lastSaveUri;

    @Nullable
    private CountingIdlingResource openFileIdlingResource;

    private LoaderServiceQueue serviceQueue;
    private LoaderService service;
    private final ServiceConnection connection = new ServiceConnection() {
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

        setTitle("");

        serviceQueue = new LoaderServiceQueue();
        Intent intent = new Intent(this, LoaderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        handler = new Handler();

        adContainer = findViewById(R.id.ad_container);
        landingContainer = findViewById(R.id.landing_container);
        documentContainer = findViewById(R.id.document_container);

        findViewById(R.id.landing_intro_open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                analyticsManager.report("intro_open");
                findDocument();
            }
        });
        findViewById(R.id.landing_open_fab).setOnClickListener(new View.OnClickListener() {
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

        documentFragment = (DocumentFragment) getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

        if (documentFragment != null && documentFragment.hasLastResult()) {
            // nothing else to do

            crashManager.log("onCreate nothing");
        } else if (savedInstanceState != null && savedInstanceState.containsKey(SAVED_KEY_LAST_CACHE_URI)) {
            loadOnStart = savedInstanceState.getParcelable(SAVED_KEY_LAST_CACHE_URI);

            crashManager.log("onCreate loadOnStart");
        } else if (documentFragment == null) {
            crashManager.log("onCreate from background");

            // app was started from another app, but make sure not to load it twice
            // (i.e. after bringing app back from background)
            if (getIntent().getData() != null) {
                loadOnStart = getIntent().getData();

                analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "other");
            } else {
                analyticsManager.setCurrentScreen(this, "screen_main");
            }
        } else {
            crashManager.log("onCreate empty");

            analyticsManager.setCurrentScreen(this, "screen_main");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        documentFragment = (DocumentFragment) getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

        if (documentFragment != null) {
            landingContainer.setVisibility(View.GONE);
            documentContainer.setVisibility(View.VISIBLE);
        }

        crashManager.log("onStart");

        if (loadOnStart != null) {
            loadUri(loadOnStart);

            loadOnStart = null;
        }
    }

    private void initializeCatchAllSwitch() {
        ComponentName catchAllComponent = new ComponentName(this, "at.tomtasche.reader.ui.activity.MainActivity.CATCH_ALL");
        ComponentName strictCatchComponent = new ComponentName(this, "at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH");

        boolean isCatchAllEnabled = getPackageManager().getComponentEnabledSetting(catchAllComponent) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        // retoggle components for users upgrading to latest version of app
        toggleComponent(catchAllComponent, isCatchAllEnabled);
        toggleComponent(strictCatchComponent, !isCatchAllEnabled);

        SwitchCompat catchAllSwitch = findViewById(R.id.landing_catch_all);

        catchAllSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                toggleComponent(catchAllComponent, isChecked);
                toggleComponent(strictCatchComponent, !isChecked);
            }
        });

        catchAllSwitch.setChecked(isCatchAllEnabled);

        analyticsManager.report(isCatchAllEnabled ? "catch_all_enabled" : "catch_all_disabled");
    }

    private void toggleComponent(ComponentName component, boolean enabled) {
        int newState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(SAVED_KEY_LAST_CACHE_URI, lastUri);
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

        configManager = new ConfigManager();
        configManager.setEnabled(useProprietaryLibraries);
        configManager.initialize();

        adManager = new AdManager();
        adManager.setEnabled(!IS_TESTING && useProprietaryLibraries);
        adManager.setAdContainer(adContainer);
        adManager.initialize(this, analyticsManager, crashManager, configManager);

        billingManager = new BillingManager();
        billingManager.setEnabled(useProprietaryLibraries);
        billingManager.initialize(this, analyticsManager, adManager);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getData() != null) {
            crashManager.log("onNewIntent loadUri");

            loadUri(intent.getData());

            analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "other");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (billingManager.hasPurchased()) {
            menu.findItem(R.id.menu_remove_ads).setVisible(false);
        }

        return true;
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
                if (null != openFileIdlingResource) {
                    openFileIdlingResource.decrement();
                }

                loadUri(uri);
            }
        }
    }

    public void loadUri(Uri uri) {
        lastSaveUri = null;
        lastUri = uri;

        if (documentFragment == null) {
            documentFragment = (DocumentFragment) getSupportFragmentManager().findFragmentByTag(DOCUMENT_FRAGMENT_TAG);

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
        analyticsManager.report(FirebaseAnalytics.Event.VIEW_ITEM, FirebaseAnalytics.Param.ITEM_NAME, uri.toString());

        boolean isPersistentUri = false;
        try {
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            isPersistentUri = true;
        } catch (Exception e) {
            // some providers don't support persisted permissions
            crashManager.log(e);
        }

        documentFragment.loadUri(uri, isPersistentUri);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_search) {
            FindActionModeCallback findActionModeCallback = new FindActionModeCallback(this);
            findActionModeCallback.setWebView(documentFragment.getPageView());
            startSupportActionMode(findActionModeCallback);

            analyticsManager.report("menu_search");
            analyticsManager.report(FirebaseAnalytics.Event.SEARCH);
        } else if (itemId == R.id.menu_open) {
            findDocument();

            analyticsManager.report("menu_open");
            analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "choose");
        } else if (itemId == R.id.menu_open_with) {
            documentFragment.openWith(this);

            analyticsManager.report("menu_open_with");
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

                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

                getSupportActionBar().hide();

                // delay offer to wait for fullscreen animation to finish
                handler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }

                        offerPurchase();
                    }
                }, 1000);
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
        SnackbarHelper.show(this, R.string.crouton_remove_ads, new Runnable() {

            @Override
            public void run() {
                analyticsManager.report("present_offer_clicked");

                buyAdRemoval();
            }
        }, true, false);
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

        analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "recent");
    }

    private void buyAdRemoval() {
        analyticsManager.report(FirebaseAnalytics.Event.ADD_TO_CART);

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=at.tomtasche.reader.pro")));
    }

    private void leaveFullscreen() {
        if (!fullscreen) {
            return;
        }

        getSupportActionBar().show();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        fullscreen = false;

        analyticsManager.report("fullscreen_end");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (fullscreen && keyCode == KeyEvent.KEYCODE_BACK) {
            leaveFullscreen();

            return true;
        }

        return super.onKeyDown(keyCode, event);
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
            if (!target.activityInfo.packageName.equals(getPackageName()) && !target.activityInfo.exported) {
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
        builder.setItems(targetNames, new OnClickListener() {

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

                intent.setComponent(new ComponentName(target.activityInfo.packageName, target.activityInfo.name));

                try {
                    if (null != openFileIdlingResource) {
                        openFileIdlingResource.increment();
                    }

                    startActivityForResult(intent, 42);
                } catch (Exception e) {
                    if (null != openFileIdlingResource) {
                        openFileIdlingResource.decrement();
                    }

                    crashManager.log(e);

                    SnackbarHelper.show(MainActivity.this, R.string.crouton_error_open_app, new Runnable() {

                        @Override
                        public void run() {
                            findDocument();
                        }
                    }, true, true);
                }

                analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, target.activityInfo.packageName);

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

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LoaderServiceQueue getLoaderServiceQueue() {
        return serviceQueue;
    }

    @VisibleForTesting
    @NonNull
    public IdlingResource getOpenFileIdlingResource() {
        if (null == openFileIdlingResource) {
            openFileIdlingResource = new CountingIdlingResource("MainActivity.openFileIdlingResource");
        }
        return openFileIdlingResource;
    }
}
