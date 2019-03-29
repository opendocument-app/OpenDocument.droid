package at.tomtasche.reader.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.kobakei.ratethisapp.RateThisApp;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import at.tomtasche.reader.BuildConfig;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.KitKatPrinter;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.BillingManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.CroutonHelper;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.FindActionModeCallback;
import at.tomtasche.reader.ui.TtsActionModeCallback;
import at.tomtasche.reader.ui.widget.RecentDocumentDialogFragment;
import de.keyboardsurfer.android.widget.crouton.Style;

public class MainActivity extends AppCompatActivity implements DocumentLoadingActivity {

    private static final boolean USE_PROPRIETARY_LIBRARIES = true;
    private static final boolean IS_GOOGLE_ECOSYSTEM = true;
    private static final int GOOGLE_REQUEST_CODE = 1993;
    private static final String DOCUMENT_FRAGMENT_TAG = "document_fragment";
    public static int PERMISSION_CODE = 1353;

    private boolean isDocumentLoaded = false;

    private Menu menu;
    private Handler handler;

    private View landingContainer;
    private View documentContainer;
    private LinearLayout adContainer;
    private DocumentFragment documentFragment;

    private boolean fullscreen;
    private TtsActionModeCallback ttsActionMode;
    private EditActionModeCallback editActionMode;

    private CrashManager crashManager;
    private AnalyticsManager analyticsManager;
    private AdManager adManager;
    private BillingManager billingManager;

    private int permissionDialogCount = 0;
    private boolean isIntroOpen = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        setTitle("");

        handler = new Handler();

        adContainer = findViewById(R.id.ad_container);
        landingContainer = findViewById(R.id.landing_container);
        documentContainer = findViewById(R.id.document_container);

        documentFragment = (DocumentFragment) getSupportFragmentManager()
                .findFragmentByTag(DOCUMENT_FRAGMENT_TAG);
        if (documentFragment == null) {
            documentFragment = new DocumentFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.document_container, documentFragment,
                            DOCUMENT_FRAGMENT_TAG).commit();
        } else {
            loadUri(null);
        }

        initializeProprietaryLibraries();

        showIntroActivity();

        RateThisApp.onCreate(this);
        RateThisApp.showRateDialogIfNeeded(this);

        initializeCatchAllSwitch();
    }

    private void initializeCatchAllSwitch() {
        ComponentName catchAllComponent = new ComponentName(this, BuildConfig.APPLICATION_ID + ".ui.activity.MainActivity.CATCH_ALL");
        ComponentName strictCatchComponent = new ComponentName(this, BuildConfig.APPLICATION_ID + ".ui.activity.MainActivity.STRICT_CATCH");

        boolean isCatchAllEnabled = getPackageManager().getComponentEnabledSetting(catchAllComponent) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        // retoggle components for users upgrading to latest version of app
        toggleComponent(catchAllComponent, isCatchAllEnabled);
        toggleComponent(strictCatchComponent, !isCatchAllEnabled);

        Switch catchAllSwitch = findViewById(R.id.landing_catch_all);
        catchAllSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                toggleComponent(catchAllComponent, isChecked);
                toggleComponent(strictCatchComponent, !isChecked);
            }
        });

        catchAllSwitch.setChecked(isCatchAllEnabled);
    }

    private void toggleComponent(ComponentName component, boolean enabled) {
        int newState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!isIntroOpen) {
            requestPermission();
        }
        isIntroOpen = false;

        // app was started from another app, but make sure not to load it twice
        // (i.e. after bringing app back from background)
        if (!isDocumentLoaded) {
            handleIntent(getIntent());
        }
    }

    private void showIntroActivity() {
        SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean wasIntroShown = getPrefs.getBoolean("introShown", false);
        if (!wasIntroShown) {
            Intent intent = new Intent(MainActivity.this, IntroActivity.class);
            startActivity(intent);

            SharedPreferences.Editor editor = getPrefs.edit();
            editor.putBoolean("introShown", true);
            editor.apply();

            isIntroOpen = true;
        } else {
            requestPermission();
        }
    }

    private void requestPermission() {
        if (permissionDialogCount > 3) {
            // some users keep denying the permission
            return;
        }

        permissionDialogCount++;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_CODE);
        }
    }

    private void initializeProprietaryLibraries() {
        if (USE_PROPRIETARY_LIBRARIES) {
            GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
            int googleAvailability = googleApi.isGooglePlayServicesAvailable(this);
            if (googleAvailability != ConnectionResult.SUCCESS) {
                googleApi.getErrorDialog(this, googleAvailability, GOOGLE_REQUEST_CODE).show();
            }
        }

        crashManager = new CrashManager();
        crashManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
        crashManager.initialize();

        analyticsManager = new AnalyticsManager();
        analyticsManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
        analyticsManager.initialize(this);

        adManager = new AdManager();
        adManager.setEnabled(USE_PROPRIETARY_LIBRARIES);
        adManager.setAdContainer(adContainer);
        adManager.setOnAdFailedCallback(new Runnable() {

            @Override
            public void run() {
                CroutonHelper.showCrouton(MainActivity.this, R.string.crouton_remove_ads, new Runnable() {

                    @Override
                    public void run() {
                        buyAdRemoval();
                    }
                }, Style.CONFIRM);
            }
        });
        adManager.initialize(this, analyticsManager);

        billingManager = new BillingManager();
        billingManager.setEnabled(USE_PROPRIETARY_LIBRARIES && IS_GOOGLE_ECOSYSTEM);
        billingManager.initialize(this, analyticsManager, adManager, crashManager);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.getData() != null) {
            loadUri(intent.getData());

            analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, "other");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;

        if (isDocumentLoaded) {
            showDocumentMenu();
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == BillingManager.PURCHASE_CODE) {
            billingManager.endPurchase(requestCode, resultCode, intent);
        } else if (requestCode == GOOGLE_REQUEST_CODE) {
            initializeProprietaryLibraries();
        } else if (intent != null) {
            adManager.showInterstitial();

            Uri uri = intent.getData();
            if (requestCode == 42 && resultCode == Activity.RESULT_OK && uri != null) {
                loadUri(uri);
            }
        }
    }

    @Override
    public void loadUri(Uri uri) {
        isDocumentLoaded = true;

        if (uri != null) {
            crashManager.log("loading document at: " + uri.toString());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    // some providers dont support persisted permissions
                    e.printStackTrace();
                }
            }

            documentFragment.loadUri(uri);
        } else {
            // null passed in case of orientation change
        }

        landingContainer.setVisibility(View.GONE);
        documentContainer.setVisibility(View.VISIBLE);

        showDocumentMenu();
    }

    private void showDocumentMenu() {
        if (menu != null) {
            menu.setGroupVisible(R.id.menu_document_group, true);
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

                    CroutonHelper.showCrouton(this, R.string.crouton_leave_fullscreen, new Runnable() {

                        @Override
                        public void run() {
                            leaveFullscreen();
                        }
                    }, Style.INFO);

                    handler.postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }

                            CroutonHelper.showCrouton(MainActivity.this, R.string.crouton_remove_ads, new Runnable() {

                                @Override
                                public void run() {
                                    buyAdRemoval();
                                }
                            }, Style.INFO);
                        }
                    }, 10000);
                }

                fullscreen = !fullscreen;

                break;
            }
            case R.id.menu_print: {
                analyticsManager.report("menu_print");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    KitKatPrinter.print(this, documentFragment.getPageView());
                } else {
                    CroutonHelper.showCrouton(this, R.string.crouton_print_unavailable, null, Style.ALERT);
                }

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

                adManager.loadInterstitial();

                editActionMode = new EditActionModeCallback(this, documentFragment, adManager);
                startSupportActionMode(editActionMode);

                break;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }

        return true;
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

        if (requestCode == EditActionModeCallback.PERMISSION_CODE && editActionMode != null) {
            editActionMode.save();
        } else if (requestCode == PERMISSION_CODE) {
            requestPermission();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_remove_ads_title);

        String[] optionStrings = getResources().getStringArray(R.array.dialog_remove_ads_options);
        if (!IS_GOOGLE_ECOSYSTEM) {
            optionStrings = new String[] { optionStrings[3] };
        }

        builder.setItems(optionStrings, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String product;

                if (!IS_GOOGLE_ECOSYSTEM) {
                    which = 3;
                }

                switch (which) {
                    case 0:
                        product = BillingManager.BILLING_PRODUCT_YEAR;

                        break;

                    case 1:
                        product = BillingManager.BILLING_PRODUCT_FOREVER;

                        break;

                    case 2:
                        product = BillingManager.BILLING_PRODUCT_LOVE;

                        break;

                    default:
                        dialog.dismiss();

                        adManager.showVideo();

                        return;
                }

                billingManager.startPurchase(MainActivity.this, product);

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
        final List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
        int size = targets.size() + 1;
        String[] targetNames = new String[size];
        for (int i = 0; i < targets.size(); i++) {
            targetNames[i] = targets.get(i).loadLabel(pm).toString();
        }

        targetNames[size - 1] = getString(R.string.menu_recent);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_choose_filemanager);
        builder.setItems(targetNames, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == size - 1) {
                    showRecent();

                    return;
                }

                ResolveInfo target = targets.get(which);
                if (target == null) {
                    return;
                }

                intent.setComponent(new ComponentName(target.activityInfo.packageName, target.activityInfo.name));

                try {
                    startActivityForResult(intent, 42);
                } catch (Exception e) {
                    e.printStackTrace();

                    CroutonHelper.showCrouton(MainActivity.this, R.string.crouton_error_open_app, new Runnable() {

                        @Override
                        public void run() {
                            findDocument();
                        }
                    }, Style.ALERT);
                }

                analyticsManager.report(FirebaseAnalytics.Event.SELECT_CONTENT, FirebaseAnalytics.Param.CONTENT_TYPE, target.activityInfo.packageName);

                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (ttsActionMode != null) {
            ttsActionMode.stop();
        }
    }

    @Override
    protected void onStop() {
        billingManager.close();

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
            e.printStackTrace();
        }
    }

    public CrashManager getCrashManager() {
        return crashManager;
    }

    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }
}
