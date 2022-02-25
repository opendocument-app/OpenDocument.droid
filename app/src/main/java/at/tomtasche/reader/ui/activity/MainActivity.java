package at.tomtasche.reader.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nononsenseapps.filepicker.FilePickerActivity;

import java.util.LinkedList;
import java.util.List;

import at.tomtasche.reader.R;
import at.tomtasche.reader.background.PrintingManager;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.BillingManager;
import at.tomtasche.reader.nonfree.ConfigManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.nonfree.HelpManager;
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

    protected static boolean IS_GOOGLE_ECOSYSTEM = true;

    private static final String SAVED_KEY_LAST_CACHE_URI = "LAST_CACHE_URI";
    private static final boolean IS_TESTING = isTesting();
    private static final int GOOGLE_REQUEST_CODE = 1993;
    private static final String DOCUMENT_FRAGMENT_TAG = "document_fragment";
    private static final int PERMISSION_CODE = 1353;
    private static final int CREATE_CODE = 4213;

    private boolean didTriggerPermissionDialogAgain = false;

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
    private HelpManager helpManager;
    private PrintingManager printingManager;

    private Runnable onPermissionRunnable;

    private Uri lastUri;
    private Uri loadOnStart;
    private Uri lastSaveUri;

    @Nullable
    private CountingIdlingResource openFileIdlingResource;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        setTitle("");

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ComponentName filePickerActivity = new ComponentName(this, FilePickerActivity.class.getName());
            toggleComponent(filePickerActivity, false);
        }

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

        final View content = findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (configManager.isLoaded()) {
                            if (IS_TESTING) {
                                return true;
                            }

                            Boolean isShowIntro = configManager.getBooleanConfig("show_intro");
                            if (isShowIntro == null || isShowIntro) {
                                showIntroActivityOnFirstStart();
                            }

                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
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
            loadUri(loadOnStart, false);

            loadOnStart = null;
        }
    }

    private void initializeCatchAllSwitch() {
        ComponentName catchAllComponent = new ComponentName(this, "at.tomtasche.reader.ui.activity.MainActivity.CATCH_ALL");
        ComponentName strictCatchComponent = new ComponentName(this, "at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH");

        boolean isCatchAllEnabled = getPackageManager().getComponentEnabledSetting(catchAllComponent) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED && IS_GOOGLE_ECOSYSTEM;

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

    private void showIntroActivityOnFirstStart() {
        SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean wasIntroShown = getPrefs.getBoolean("introShown", false);
        if (!wasIntroShown) {
            helpManager.show();

            SharedPreferences.Editor editor = getPrefs.edit();
            editor.putBoolean("introShown", true);
            editor.apply();
        }
    }

    public boolean requestPermission(String permission, Runnable onPermissionRunnable) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_CODE);

            this.onPermissionRunnable = onPermissionRunnable;

            return false;
        }

        this.onPermissionRunnable = null;

        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public boolean requestSave() {
        if (lastSaveUri != null) {
            documentFragment.save(lastSaveUri);

            return true;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.setType(documentFragment.getLastFileType());

            startActivityForResult(intent, CREATE_CODE);

            return true;
        } catch (ActivityNotFoundException e) {
            // happens on a variety devices, e.g. Samsung Galaxy Tab4 7.0 with Android 4.4.2
            crashManager.log(e);

            return false;
        }
    }

    private void initializeProprietaryLibraries() {
        boolean useProprietaryLibraries = !getResources().getBoolean(R.bool.DISABLE_TRACKING);

        if (useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM) {
            GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
            int googleAvailability = googleApi.isGooglePlayServicesAvailable(this);
            if (googleAvailability != ConnectionResult.SUCCESS) {
                IS_GOOGLE_ECOSYSTEM = false;
                googleApi.getErrorDialog(this, googleAvailability, GOOGLE_REQUEST_CODE).show();
            }
        }

        crashManager = new CrashManager();
        crashManager.setEnabled(useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM);
        crashManager.initialize();

        analyticsManager = new AnalyticsManager();
        analyticsManager.setEnabled(useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM);
        analyticsManager.initialize(this);

        configManager = new ConfigManager();
        configManager.setEnabled(useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM);
        configManager.initialize();

        adManager = new AdManager();
        adManager.setEnabled(!IS_TESTING && useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM);
        adManager.setAdContainer(adContainer);
        adManager.initialize(this, analyticsManager, crashManager, configManager);

        billingManager = new BillingManager();
        billingManager.setEnabled(useProprietaryLibraries && IS_GOOGLE_ECOSYSTEM);
        billingManager.initialize(this, analyticsManager, adManager, crashManager);

        helpManager = new HelpManager();
        helpManager.setEnabled(true);
        helpManager.initialize(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getData() != null) {
            crashManager.log("onNewIntent loadUri");

            loadUri(intent.getData(), false);

            analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "other");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (!billingManager.isEnabled() || billingManager.hasPurchased()) {
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

                loadUri(uri, true);
            }
        }
    }

    public void loadUri(Uri uri, boolean showAd) {
        lastSaveUri = null;
        lastUri = uri;

        Runnable onPermission = new Runnable() {
            @Override
            public void run() {
                loadUri(uri, showAd);
            }
        };

        boolean hasPermission = requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, onPermission);
        if (!hasPermission) {
            return;
        }

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

        boolean isPersistentUri = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                // some providers dont support persisted permissions
                crashManager.log(e);

                isPersistentUri = false;
            }
        }

        documentFragment.loadUri(uri, isPersistentUri);

        if (showAd) {
            // delay until all UI work has completed for loading the fragment
            handler.post(new Runnable() {
                @Override
                public void run() {
                    adManager.showInterstitial();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search: {
                FindActionModeCallback findActionModeCallback = new FindActionModeCallback(this);
                findActionModeCallback.setWebView(documentFragment.getPageView());
                startSupportActionMode(findActionModeCallback);

                analyticsManager.report("menu_search");
                analyticsManager.report(FirebaseAnalytics.Event.SEARCH);

                break;
            }
            case R.id.menu_open: {
                findDocument();

                analyticsManager.report("menu_open");
                analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "choose");

                break;
            }
            case R.id.menu_open_with: {
                documentFragment.openWith(this);

                analyticsManager.report("menu_open_with");

                break;
            }
            case R.id.menu_share: {
                documentFragment.share(this);

                analyticsManager.report("menu_share");

                break;
            }
            case R.id.menu_remove_ads: {
                analyticsManager.report("menu_remove_ads");

                buyAdRemoval();

                break;
            }
            case R.id.menu_fullscreen: {
                if (fullscreen) {
                    analyticsManager.report("menu_fullscreen_leave");

                    leaveFullscreen();
                } else {
                    analyticsManager.report("menu_fullscreen_enter");

                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

                    getSupportActionBar().hide();

                    if (!billingManager.hasPurchased()) {
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
                }

                fullscreen = !fullscreen;

                break;
            }
            case R.id.menu_print: {
                // TODO: remove as printing is offered on share too!

                analyticsManager.report("menu_print");

                documentFragment.getPageView().toggleDarkMode(false);

                printingManager.print(this, documentFragment.getPageView());

                break;
            }
            case R.id.menu_tts: {
                analyticsManager.report("menu_tts");

                ttsActionMode = new TtsActionModeCallback(this, documentFragment.getPageView());
                startSupportActionMode(ttsActionMode);

                break;
            }
            case R.id.menu_edit: {
                analyticsManager.report("menu_edit");

                editActionMode = new EditActionModeCallback(this, documentFragment, helpManager);
                startSupportActionMode(editActionMode);

                break;
            }
            case R.id.menu_help: {
                helpManager.show();

                break;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }

        return true;
    }

    private void offerPurchase() {
        if (billingManager.hasPurchased() || !billingManager.isEnabled()) {
            return;
        }

        analyticsManager.report(FirebaseAnalytics.Event.PRESENT_OFFER);
        SnackbarHelper.show(this, R.string.crouton_remove_ads, new Runnable() {

            @Override
            public void run() {
                analyticsManager.report(FirebaseAnalytics.Event.PRESENT_OFFER + "_clicked");

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_CODE && onPermissionRunnable != null) {
                onPermissionRunnable.run();
                onPermissionRunnable = null;

                return;
            }
        }

        String permission;
        if (permissions.length > 0) {
            permission = permissions[0];
        } else {
            // https://stackoverflow.com/q/50770955/198996
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        }

        if (!didTriggerPermissionDialogAgain) {
            requestPermission(permission, onPermissionRunnable);

            didTriggerPermissionDialogAgain = true;
        } else {
            SnackbarHelper.show(this, R.string.toast_error_permission_required, new Runnable() {
                @Override
                public void run() {
                    requestPermission(permission, onPermissionRunnable);
                }
            }, true, true);
        }
    }

    private void showRecent() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        DialogFragment chooserDialog = new RecentDocumentDialogFragment();
        chooserDialog.show(transaction, RecentDocumentDialogFragment.FRAGMENT_TAG);

        analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "recent");
    }

    private void buyAdRemoval() {
        adManager.loadVideo();

        analyticsManager.report(FirebaseAnalytics.Event.BEGIN_CHECKOUT);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_remove_ads_title);

        final boolean isBillingEnabled = billingManager.isEnabled();

        boolean isShowSubscription = configManager.getBooleanConfig("show_subscription");
        boolean isNotShowPurchase = configManager.getBooleanConfig("do_not_show_purchase");
        boolean isProPurchase = configManager.getBooleanConfig("use_pro_purchase");

        String[] optionStrings = getResources().getStringArray(R.array.dialog_remove_ads_options);

        List<String> optionStringList = new LinkedList<>();
        List<String> productStringList = new LinkedList<>();

        if (isBillingEnabled) {
            if (isShowSubscription) {
                optionStringList.add(optionStrings[1]);
                productStringList.add(BillingManager.BILLING_PRODUCT_SUBSCRIPTION);
            }
            if (!isNotShowPurchase) {
                optionStringList.add(optionStrings[0]);

                if (isProPurchase) {
                    productStringList.add("https://play.google.com/store/apps/details?id=at.tomtasche.reader.pro");
                }
            }
        }

        optionStringList.add(optionStrings[2]);

        optionStrings = optionStringList.toArray(new String[optionStringList.size()]);

        builder.setItems(optionStrings, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!isBillingEnabled) {
                    which = 99;
                }

                analyticsManager.report(FirebaseAnalytics.Event.ADD_TO_CART);

                String product;
                if (which < productStringList.size()) {
                    product = productStringList.get(which);
                } else {
                    dialog.dismiss();

                    adManager.showVideo();

                    return;
                }

                if (product.startsWith("https://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(product)));
                } else {
                    billingManager.startPurchase(MainActivity.this, product);
                }

                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void leaveFullscreen() {
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
        adManager.loadInterstitial();

        final Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            // remove mime-type because most apps don't support ODF mime-types
            intent.setType("application/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
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
    protected void onStop() {
        billingManager.close();
        printingManager.close();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
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

    @VisibleForTesting
    @NonNull
    public IdlingResource getOpenFileIdlingResource() {
        if (null == openFileIdlingResource) {
            openFileIdlingResource = new CountingIdlingResource("MainActivity.openFileIdlingResource");
        }
        return openFileIdlingResource;
    }
}
