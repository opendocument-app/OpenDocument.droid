package at.tomtasche.reader.ui.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.background.MetadataLoader;
import at.tomtasche.reader.background.OdfLoader;
import at.tomtasche.reader.background.OdfLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.OnlineLoader;
import at.tomtasche.reader.background.PdfLoader;
import at.tomtasche.reader.background.RawLoader;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.widget.FailsafePDFPagerAdapter;
import at.tomtasche.reader.ui.widget.PageView;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;
import at.tomtasche.reader.ui.widget.VerticalViewPager;

public class DocumentFragment extends Fragment implements FileLoader.FileLoaderListener, ActionBar.TabListener {

    private Handler mainHandler;

    private MetadataLoader metadataLoader;
    private OdfLoader odfLoader;
    private PdfLoader pdfLoader;
    private RawLoader rawLoader;
    private OnlineLoader onlineLoader;

    private AnalyticsManager analyticsManager;
    private CrashManager crashManager;

    private ProgressDialogFragment progressDialog;

    private PageView pageView;

    private VerticalViewPager pdfView;
    private FailsafePDFPagerAdapter pdfAdapter;

    private Menu menu;

    private FileLoader.Result lastResult;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler();

        backgroundThread = new HandlerThread(DocumentFragment.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        Context context = getContext();
        MainActivity mainActivity = (MainActivity) getActivity();
        analyticsManager = mainActivity.getAnalyticsManager();
        crashManager = mainActivity.getCrashManager();

        metadataLoader = new MetadataLoader(context);
        metadataLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        odfLoader = new OdfLoader(context);
        odfLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        pdfLoader = new PdfLoader(context);
        pdfLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        rawLoader = new RawLoader(context);
        rawLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        onlineLoader = new OnlineLoader(context);
        onlineLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            LinearLayout inflatedView = (LinearLayout) inflater.inflate(R.layout.fragment_document, container, false);

            pageView = inflatedView.findViewById(R.id.page_view);
            pageView.setDocumentFragment(this);

            pdfView = inflatedView.findViewById(R.id.pdf_view);

            return inflatedView;
        } catch (Throwable t) {
            crashManager.log("no webview installed: " + t.getMessage());

            String errorString = "Please install \"Android System WebView\" and restart the app afterwards.";

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"));
            startActivity(intent);

            Toast.makeText(getContext(), errorString, Toast.LENGTH_LONG).show();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                getActivity().finishAffinity();
            } else {
                getActivity().finish();
                System.exit(0);
            }

            TextView textView = new TextView(getContext());
            textView.setText(errorString);
            return textView;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        super.onCreateOptionsMenu(menu, inflater);
    }

    public void loadUri(Uri uri, boolean persistentUri) {
        loadUri(uri, persistentUri, null, false, false);
    }

    public void loadUri(Uri uri, boolean persistentUri, String password) {
        loadUri(uri, persistentUri, password, false, false);
    }

    public void loadUri(Uri uri, boolean persistentUri, String password, boolean limit, boolean translatable) {
        FileLoader.Options options = new FileLoader.Options();
        options.originalUri = uri;
        options.persistentUri = persistentUri;
        options.password = password;
        options.limit = limit;
        options.translatable = translatable;

        showProgress();

        metadataLoader.loadAsync(options);
    }

    private void loadOdf(FileLoader.Options options) {
        showProgress();

        toggleDocumentMenu(true);
        togglePageView(true);

        odfLoader.loadAsync(options);
    }

    private void loadRaw(FileLoader.Options options) {
        showProgress();

        toggleDocumentMenu(true, false);
        togglePageView(true);

        rawLoader.loadAsync(options);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean loadPdf(Uri uri) {
        toggleDocumentMenu(false);
        togglePageView(false);

        try {
            pdfAdapter = new FailsafePDFPagerAdapter(getContext(), new File(AndroidFileCache.getCacheDirectory(getContext()),
                    uri.getLastPathSegment()).getPath());
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (pdfAdapter == null || !pdfAdapter.isInitialized()) {
            if (pdfAdapter != null) {
                pdfAdapter.close();
                pdfAdapter = null;
            }

            togglePageView(false);

            return false;
        }

        pdfView.setAdapter(pdfAdapter);

        return true;
    }

    private void togglePageView(boolean enabled) {
        if (pageView == null) {
            // happens on devices with no WebView installed, ignore
            return;
        }

        pageView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        pdfView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        pdfView.setAdapter(null);

        if (!enabled && pdfAdapter != null) {
            pdfAdapter.close();
            pdfAdapter = null;
        }
    }

    public void reloadUri(boolean translatable) {
        lastResult.options.translatable = translatable;

        loadOdf(lastResult.options);
    }

    public void saveAsync(String htmlDiff, TextView statusView) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                saveSync(htmlDiff, statusView);
            }
        });
    }

    private void saveSync(String htmlDiff, TextView statusView) {
        try {
            String password = lastResult.options.password;

            File modifiedFile = odfLoader.retranslate(htmlDiff);
            Uri fileUri = Uri.parse("file://"
                    + modifiedFile.getAbsolutePath());

            loadUri(fileUri, false, password, false, true);

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusView.setText(R.string.edit_status_saved);
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();

            analyticsManager.report("save_error", FirebaseAnalytics.Param.CONTENT_TYPE, lastResult.options.fileType);
            crashManager.log(e, lastResult.options.originalUri);

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusView.setText(R.string.toast_error_generic);
                }
            });
        }
    }

    private void loadOnline(FileLoader.Options options) {
        showProgress(onlineLoader, true);

        togglePageView(true);
        toggleDocumentMenu(true, false);

        onlineLoader.loadAsync(options);
    }

    private void unload() {
        lastResult = null;

        toggleDocumentMenu(false);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        bar.removeAllTabs();
    }

    private void toggleDocumentMenu(boolean enabled) {
        toggleDocumentMenu(enabled, enabled);
    }

    private void toggleDocumentMenu(boolean enabled, boolean editEnabled) {
        if (menu == null) {
            if (getActivity() == null || getActivity().isFinishing() || pageView == null) {
                return;
            }

            // menu is not set when loadUri is called via onStart, retry later
            pageView.post(new Runnable() {
                @Override
                public void run() {
                    toggleDocumentMenu(enabled, editEnabled);
                }
            });

            return;
        }

        menu.findItem(R.id.menu_edit).setVisible(editEnabled);

        menu.findItem(R.id.menu_search).setVisible(enabled);
        menu.findItem(R.id.menu_tts).setVisible(enabled);
    }

    @Override
    public void onSuccess(FileLoader.Result result) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        dismissProgress();

        FileLoader.Options options = result.options;
        if (result.loaderType == FileLoader.LoaderType.METADATA) {
            if (!odfLoader.isSupported(options)) {
                crashManager.log("we do not expect this file to be an ODF: " + options.originalUri.toString());
                analyticsManager.report("load_odf_error_expected", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType);
            }

            loadOdf(options);
        } else {
            analyticsManager.setCurrentScreen(activity, "screen_" + result.loaderType.toString() + "_" + result.options.fileType);

            analyticsManager.report("load_success", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType, FirebaseAnalytics.Param.CONTENT, result.loaderType.toString());

            lastResult = result;

            ActionBar bar = ((AppCompatActivity) activity).getSupportActionBar();
            bar.removeAllTabs();

            List<String> titles = result.partTitles;
            int pages = titles.size();
            if (pages > 1) {
                bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                for (int i = 0; i < pages; i++) {
                    ActionBar.Tab tab = bar.newTab();
                    String name = titles.get(i);
                    if (name == null)
                        name = "Page " + (i + 1);
                    tab.setText(name);
                    tab.setTabListener(this);

                    bar.addTab(tab);
                }

                bar.setSelectedNavigationItem(0);
            } else {
                bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

                if (pages == 1) {
                    loadData(result.partUris.get(0).toString());
                }
            }

            if (result.loaderType == FileLoader.LoaderType.RAW || result.loaderType == FileLoader.LoaderType.ONLINE) {
                offerReopen(activity, options, R.string.toast_hint_unsupported_file, false);
            }
        }
    }

    @Override
    public void onError(FileLoader.Result result, Throwable error) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        unload();

        dismissProgress();

        FileLoader.Options options = result.options;
        Uri cacheUri = options.cacheUri;
        if (result.loaderType == FileLoader.LoaderType.ODF) {
            analyticsManager.report("load_odf_error", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType);
            crashManager.log(error, options.originalUri);

            if (error instanceof EncryptedDocumentException) {
                analyticsManager.report("load_error_encrypted");

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(R.string.toast_error_password_protected);

                final EditText input = new EditText(activity);
                input.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                builder.setView(input);

                builder.setPositiveButton(getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                loadUri(cacheUri, false, input.getText().toString());

                                dialog.dismiss();
                            }
                        });
                builder.setNegativeButton(getString(android.R.string.cancel), null);
                builder.show();

                return;
            } else {
                if (pdfLoader.isSupported(options)) {
                    boolean didLoad = loadPdf(result, (AppCompatActivity) activity);
                    if (!didLoad) {
                        // we can assume at this point that the file is supported by the online viewer
                        // because we already checked if it is supported by the PdfLoader
                        offerUpload(activity, options);
                    }
                } else if (rawLoader.isSupported(options)) {
                    loadRaw(options);
                } else if (onlineLoader.isSupported(options)) {
                    offerUpload(activity, options);
                } else {
                    offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true);
                }

                return;
            }
        } else if (result.loaderType == FileLoader.LoaderType.ONLINE) {
            crashManager.log(error, options.originalUri);

            offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true);

            return;
        }

        int errorDescription;
        if (error instanceof FileNotFoundException) {
            errorDescription = R.string.toast_error_find_file;
        } else if (error instanceof OutOfMemoryError) {
            errorDescription = R.string.toast_error_out_of_memory;
        } else {
            errorDescription = R.string.toast_error_generic;
        }

        offerReopen(activity, options, errorDescription, true);

        analyticsManager.report("load_error", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType, FirebaseAnalytics.Param.CONTENT, result.loaderType.toString());
        crashManager.log(error, options.originalUri);
    }

    private boolean loadPdf(FileLoader.Result result, AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean pdfSuccess = loadPdf(result.options.cacheUri);

            if (pdfSuccess) {
                analyticsManager.report("load_success", FirebaseAnalytics.Param.CONTENT_TYPE, result.options.fileType, FirebaseAnalytics.Param.CONTENT, result.loaderType.toString());
                analyticsManager.report("loader_success_" + FileLoader.LoaderType.PDF, FirebaseAnalytics.Param.CONTENT_TYPE, result.options.fileType, FirebaseAnalytics.Param.CONTENT, result.options.fileExtension);

                result.loaderType = FileLoader.LoaderType.PDF;
                lastResult = result;

                ActionBar bar = activity.getSupportActionBar();
                bar.removeAllTabs();

                bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

                return true;
            }
        }

        return false;
    }

    private void offerUpload(Activity activity, FileLoader.Options options) {
        String fileType = options.fileType;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.toast_error_illegal_file);

        if (MainActivity.IS_GOOGLE_ECOSYSTEM) {
            builder.setMessage(R.string.dialog_upload_file);

            builder.setPositiveButton(getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog,
                                            int whichButton) {
                            analyticsManager.report("load_upload", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

                            loadOnline(options);

                            dialog.dismiss();
                        }
                    });
        }

        builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                analyticsManager.report("load_upload_cancel", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

                offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true);

                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void offerReopen(Activity activity, FileLoader.Options options, int description, boolean isIndefinite) {
        String fileType = options.fileType;
        Uri cacheUri = options.cacheUri;

        analyticsManager.report("reopen_offer", FirebaseAnalytics.Param.CONTENT_TYPE, fileType, FirebaseAnalytics.Param.CONTENT, options.originalUri);

        SnackbarHelper.show(activity, description, new Runnable() {
            @Override
            public void run() {
                loadReopen(fileType, cacheUri, options, activity, true);
            }
        }, isIndefinite, false);
    }

    private void loadReopen(String fileType, Uri cacheUri, FileLoader.Options options, Activity activity, boolean grantPermission) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        if (!"N/A".equals(fileType)) {
            intent.setDataAndType(cacheUri, fileType);
        } else if (cacheUri != null) {
            intent.setData(cacheUri);
        } else {
            intent.setData(options.originalUri);
        }

        // taken from: https://stackoverflow.com/a/23268821/198996
        PackageManager packageManager = activity.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        String packageNameToHide = activity.getPackageName();
        ArrayList<Intent> targetIntents = new ArrayList<Intent>();
        for (ResolveInfo currentInfo : activities) {
            String packageName = currentInfo.activityInfo.packageName;
            if (!packageNameToHide.equals(packageName)) {
                Intent targetIntent = new Intent();
                targetIntent.setAction(intent.getAction());
                targetIntent.setDataAndType(intent.getData(), intent.getAction());
                targetIntent.setPackage(packageName);
                targetIntent.setComponent(new ComponentName(packageName, currentInfo.activityInfo.name));

                if (grantPermission) {
                    targetIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                targetIntents.add(targetIntent);
            }
        }

        Intent initialIntent;
        if (targetIntents.size() > 0) {
            initialIntent = targetIntents.remove(0);
            analyticsManager.report("reopen_success", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);
        } else {
            initialIntent = intent;
            analyticsManager.report("reopen_failed_noapp", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);
        }

        Intent chooserIntent = Intent.createChooser(initialIntent, activity.getString(R.string.reopen_chooser_title));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[]{}));

        try {
            activity.startActivity(chooserIntent);
        } catch (Throwable e) {
            e.printStackTrace();

            if (grantPermission) {
                loadReopen(fileType, cacheUri, options, activity, false);
            }
        }
    }

    private void showProgress() {
        showProgress(null, false);
    }

    private void showProgress(final FileLoader fileLoader,
                              boolean hasProgress) {
        boolean reallyHasProgress = false;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    return;
                }

                // TODO: fix progress

                try {
                    progressDialog = new ProgressDialogFragment(reallyHasProgress);

                    FragmentManager fragmentManager = getFragmentManager();
                    if (fragmentManager == null) {
                        // TODO: use crashmanager
                        progressDialog = null;

                        return;
                    }

                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    progressDialog.show(transaction,
                            ProgressDialogFragment.FRAGMENT_TAG);

                    if (reallyHasProgress) {
                        mainHandler.postDelayed(new Runnable() {

                            @Override
                            public void run() {
                                Activity activity = getActivity();
                                if (activity == null || activity.isFinishing()) {
                                    return;
                                }

                                if (progressDialog == null) {
                                    return;
                                }

                                // TODO: progressDialog.setProgress(fileLoader.getProgress());

                                if (fileLoader.isLoading()) {
                                    mainHandler.postDelayed(this, 1000);
                                }
                            }
                        }, 1000);
                    }
                } catch (IllegalStateException e) {
                    e.printStackTrace();

                    progressDialog = null;
                }
            }
        });
    }

    private void dismissProgress() {
        // dirty hack because committing isn't allowed right after
        // onLoadFinished:
        // "java.lang.IllegalStateException: Can not perform this action inside of onLoadFinished"

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getFragmentManager() == null) {
                    return;
                }

                if (progressDialog == null) {
                    progressDialog = (ProgressDialogFragment) getFragmentManager()
                            .findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG);
                }

                if (progressDialog != null && progressDialog.getShowsDialog()
                        && progressDialog.isNotNull()) {
                    try {
                        progressDialog.dismissAllowingStateLoss();
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }

                    progressDialog = null;
                }
            }
        });
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
        Uri uri = lastResult.partUris.get(tab.getPosition());
        loadData(uri.toString());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
    }

    private void loadData(String url) {
        pageView.loadUrl(url);
    }

    public PageView getPageView() {
        return pageView;
    }

    public VerticalViewPager getPdfView() {
        return pdfView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (metadataLoader != null) {
            metadataLoader.close();
        }

        if (odfLoader != null) {
            odfLoader.close();
        }

        if (pdfLoader != null) {
            pdfLoader.close();
        }

        if (rawLoader != null) {
            rawLoader.close();
        }

        if (onlineLoader != null) {
            onlineLoader.close();
        }

        if (pdfAdapter != null) {
            pdfAdapter.close();
        }

        if (pageView != null) {
            pageView.destroy();
        }

        backgroundThread.quit();
        backgroundThread = null;
        backgroundHandler = null;
    }
}
