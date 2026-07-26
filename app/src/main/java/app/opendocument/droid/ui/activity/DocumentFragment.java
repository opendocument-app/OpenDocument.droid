package app.opendocument.droid.ui.activity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import app.opendocument.droid.R;
import app.opendocument.droid.background.AndroidFileCache;
import app.opendocument.droid.background.FileLoader;
import app.opendocument.droid.background.LoaderService;
import app.opendocument.droid.background.LoaderServiceQueue;
import app.opendocument.droid.background.StreamUtil;
import app.opendocument.droid.nonfree.AnalyticsConstants;
import app.opendocument.droid.nonfree.AnalyticsManager;
import app.opendocument.droid.nonfree.CrashManager;
import app.opendocument.droid.ui.SnackbarHelper;
import app.opendocument.droid.ui.widget.PageView;
import app.opendocument.droid.ui.widget.ProgressDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class DocumentFragment extends Fragment
        implements LoaderService.LoaderListener, MenuProvider {

    private static final String SAVED_KEY_LAST_RESULT = "LAST_RESULT";
    private static final String SAVED_KEY_CURRENT_HTML_DIFF = "CURRENT_HTML_DIFF";

    private Handler mainHandler;

    private AnalyticsManager analyticsManager;
    private CrashManager crashManager;

    private ProgressDialogFragment progressDialog;

    private ViewGroup pageContainer;
    private PageView pageView;

    private Menu menu;

    private FileLoader.Result resultOnStart;
    private Throwable errorOnStart;

    private TabLayout tabLayout;

    private DocumentViewModel state;

    private LoaderServiceQueue serviceQueue;

    /**
     * Survives configuration changes, so a rotation neither reloads the document nor loses unsaved
     * edits. This used to be setRetainInstance(true), which kept the whole fragment - views
     * included - alive instead.
     */
    public static class DocumentViewModel extends ViewModel {
        FileLoader.Result lastResult;
        String currentHtmlDiff;

        // loads cannot be canceled once running, so results of abandoned loads
        // (e.g. user navigated back while the document was still loading) are
        // identified by their uri and dropped
        Uri lastRequestedUri;

        int lastSelectedTab = -1;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // not in onViewCreated: when the activity is recreated it asks a restored fragment
        // for hasLastResult() from its own onCreate, which is before any view exists
        state = new ViewModelProvider(this).get(DocumentViewModel.class);
    }

    @Nullable @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        getActivity().addMenuProvider(this, getActivity());

        return inflater.inflate(R.layout.fragment_document, container, false);
    }

    private void initializePageView() {
        if (pageView != null) {
            pageContainer.removeAllViews();
            pageView.destroy();
            pageView = null;
        }

        try {
            getLayoutInflater().inflate(R.layout.page_view, pageContainer, true);
            pageView = pageContainer.findViewById(R.id.page_view);

            pageView.setDocumentFragment(this);
        } catch (Throwable t) {
            // can't call crashlytics yet at this point (onViewCreated not called)

            String errorString =
                    "Please install \"Android System WebView\" and restart the app afterwards.";

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                    "https://play.google.com/store/apps/details?id=com.google.android.webview"));
            startActivity(intent);

            Toast.makeText(getContext(), errorString, Toast.LENGTH_LONG).show();
            getActivity().finishAffinity();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pageContainer = view.findViewById(R.id.page_container);
        tabLayout = view.findViewById(R.id.document_tabs);

        mainHandler = new Handler(Looper.getMainLooper());

        MainActivity mainActivity = (MainActivity) getActivity();
        analyticsManager = mainActivity.getAnalyticsManager();
        crashManager = mainActivity.getCrashManager();

        serviceQueue = mainActivity.getLoaderServiceQueue();
        serviceQueue.addToQueue(
                new LoaderServiceQueue.QueueEntry() {
                    @Override
                    public void onService(LoaderService service) {
                        service.setListener(DocumentFragment.this);
                    }
                });

        crashManager.log("onViewCreated");

        if (savedInstanceState != null) {
            crashManager.log("onViewCreated has savedInstanceState");

            initializePageView();

            // the view model survives a rotation, the bundle also survives process death
            if (state.lastResult == null) {
                state.lastResult = savedInstanceState.getParcelable(SAVED_KEY_LAST_RESULT);
            }
            if (state.currentHtmlDiff == null) {
                state.currentHtmlDiff = savedInstanceState.getString(SAVED_KEY_CURRENT_HTML_DIFF);
            }

            if (state.lastResult != null) {
                crashManager.log("restoring lastResult");

                prepareLoad(state.lastResult.loaderType, false);
                restoreTabs(state.lastResult);
            }

            pageView.restoreState(savedInstanceState);
        }
    }

    /**
     * Rebuilds the tab strip after the view was recreated. The tabs live in the fragment view, so
     * unlike the document itself they do not survive a rotation on their own.
     */
    private void restoreTabs(FileLoader.Result result) {
        if (result.partTitles.size() <= 1) {
            return;
        }

        addTabs(result.partTitles);

        int selected = Math.max(state.lastSelectedTab, 0);
        TabLayout.Tab tab = tabLayout.getTabAt(selected);
        if (tab != null) {
            tab.select();
        }
    }

    private void addTabs(List<String> titles) {
        for (int i = 0; i < titles.size(); i++) {
            String name = titles.get(i);
            if (name == null) name = "Page " + (i + 1);

            tabLayout.addTab(tabLayout.newTab().setText(name), false);
        }

        tabLayout.setVisibility(View.VISIBLE);
        tabLayout.addOnTabSelectedListener(tabSelectedListener);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        this.menu = menu;

        menu.findItem(R.id.menu_fullscreen).setVisible(true);
        menu.findItem(R.id.menu_open_with).setVisible(true);
        menu.findItem(R.id.menu_share).setVisible(true);
        menu.findItem(R.id.menu_save).setVisible(true);
        menu.findItem(R.id.menu_print).setVisible(true);

        // the other menu items are dynamically enabled based on the loaded document
        if (state.lastResult != null) {
            prepareMenu(state.lastResult.loaderType, state.lastResult.options.fileType);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        // TODO: handle menu item clicks here. currently done in Activity for historical reasons
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        crashManager.log("onSaveInstanceState");

        outState.putParcelable(SAVED_KEY_LAST_RESULT, state.lastResult);
        outState.putString(SAVED_KEY_CURRENT_HTML_DIFF, state.currentHtmlDiff);

        pageView.saveState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (resultOnStart != null) {
            if (errorOnStart == null) {
                onLoadSuccess(resultOnStart);
            } else {
                onError(resultOnStart, errorOnStart);
            }

            resultOnStart = null;
            errorOnStart = null;
        }
    }

    private void prepareLoad(FileLoader.LoaderType loaderType, boolean showProgress) {
        boolean isUpload = false;
        switch (loaderType) {
            case ONLINE:
                isUpload = true;
                break;
            default:
                break;
        }

        if (showProgress) {
            showProgress(isUpload);
        }
    }

    private void loadWithType(FileLoader.LoaderType loaderType, FileLoader.Options options) {
        prepareLoad(loaderType, true);

        serviceQueue.addToQueue(
                new LoaderServiceQueue.QueueEntry() {
                    @Override
                    public void onService(LoaderService service) {
                        service.loadWithType(loaderType, options);
                    }
                });
    }

    public void loadUri(Uri uri, boolean persistentUri) {
        loadUri(uri, persistentUri, false);
    }

    public void loadUri(Uri uri, boolean persistentUri, boolean editable) {
        initializePageView();

        state.lastRequestedUri = uri;

        FileLoader.Options options = new FileLoader.Options();
        options.originalUri = uri;
        options.persistentUri = persistentUri;
        options.translatable = editable;

        loadWithType(FileLoader.LoaderType.METADATA, options);
    }

    public void reloadUri(boolean translatable) {
        state.lastResult.options.translatable = translatable;

        loadWithType(state.lastResult.loaderType, state.lastResult.options);
    }

    public void prepareSave(Runnable callback, boolean fullSave) {
        if (fullSave) {
            state.currentHtmlDiff = null;

            callback.run();
        }

        pageView.requestHtml(
                new PageView.HtmlCallback() {

                    @Override
                    public void onHtml(String htmlDiff) {
                        state.currentHtmlDiff = htmlDiff;

                        callback.run();
                    }
                });
    }

    public void save(Uri outFile) {
        if (outFile == null) {
            SnackbarHelper.show(getActivity(), R.string.toast_error_save_nofile, null, true, true);

            return;
        }

        serviceQueue.addToQueue(
                new LoaderServiceQueue.QueueEntry() {
                    @Override
                    public void onService(LoaderService service) {
                        service.saveAsync(state.lastResult, outFile, state.currentHtmlDiff);
                    }
                });
    }

    private void unload() {
        toggleDocumentMenu(false);

        resetTabs();
    }

    private void resetTabs() {
        if (tabLayout != null) {
            tabLayout.clearOnTabSelectedListeners();
            tabLayout.removeAllTabs();
            tabLayout.setVisibility(View.GONE);
        }

        state.lastSelectedTab = -1;
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
            pageView.post(
                    new Runnable() {
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

    private void prepareMenu(FileLoader.LoaderType loaderType, String fileType) {
        boolean isEditEnabled = false;
        boolean isDarkModeSupported = true;

        if (loaderType == FileLoader.LoaderType.CORE) {
            isEditEnabled = true;

            // Edit is currently broken for ODS spreadsheets
            // See: https://github.com/opendocument-app/OpenDocument.droid/issues/442
            if (fileType != null
                    && fileType.startsWith("application/vnd.oasis.opendocument.spreadsheet")) {
                isEditEnabled = false;
            }

            // Edit is not supported for PDF documents
            if (fileType != null && fileType.startsWith("application/pdf")) {
                isEditEnabled = false;
                isDarkModeSupported = false;
            }
        }

        toggleDocumentMenu(true, isEditEnabled);
        pageView.toggleDarkMode(isDarkModeSupported);
    }

    private void requestInAppRating(Activity activity) {
        analyticsManager.report("in_app_review_eligible");

        ReviewManager manager = ReviewManagerFactory.create(activity);
        com.google.android.gms.tasks.Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(
                reviewInfoTask -> {
                    if (reviewInfoTask.isSuccessful()) {
                        analyticsManager.report("in_app_review_start");

                        ReviewInfo reviewInfo = reviewInfoTask.getResult();
                        com.google.android.gms.tasks.Task<Void> flow =
                                manager.launchReviewFlow(activity, reviewInfo);
                        flow.addOnCompleteListener(
                                reviewTask -> {
                                    analyticsManager.report("in_app_review_done");
                                });
                    } else {
                        analyticsManager.report("in_app_review_error");
                    }
                });
    }

    private boolean isActivityReadyForResult(FileLoader.Result result) {
        if (state.lastRequestedUri != null
                && !state.lastRequestedUri.equals(result.options.originalUri)) {
            crashManager.log("dropping result of abandoned load: " + result.options.originalUri);

            return false;
        }

        Activity activity = getActivity();
        if (activity == null || isStateSaved()) {
            resultOnStart = result;

            return false;
        }

        // needs to be saved for errors too for features like "Open With" to work
        state.lastResult = result;

        resultOnStart = null;
        errorOnStart = null;

        return true;
    }

    @Override
    public void onLoadSuccess(FileLoader.Result result) {
        if (!isActivityReadyForResult(result)) {
            return;
        }

        Activity activity = getActivity();
        FileLoader.Options options = result.options;

        analyticsManager.setCurrentScreen(
                activity, result.loaderType.toString() + "_" + options.fileType);

        resetTabs();

        List<String> titles = result.partTitles;
        int pages = titles.size();
        if (pages > 1) {
            addTabs(titles);

            TabLayout.Tab first = tabLayout.getTabAt(0);
            if (first != null) {
                first.select();
            }
        } else if (pages == 1) {
            loadData(result.partUris.get(0).toString());
        }

        if (result.loaderType == FileLoader.LoaderType.RAW
                || result.loaderType == FileLoader.LoaderType.ONLINE) {
            offerReopen(activity, options, R.string.toast_hint_unsupported_file, false);
        }

        dismissProgress();

        // in-app review is requested in the pro flavor only. The lite branch used to
        // consult a "show_in_app_rating" remote config key, which has resolved to nothing
        // since firebase remote config was removed, so lite never asked either way.
        if (getResources().getBoolean(R.bool.DISABLE_TRACKING)) {
            requestInAppRating(activity);
        }
    }

    @Override
    public void onError(FileLoader.Result result, Throwable error) {
        if (!isActivityReadyForResult(result)) {
            return;
        }

        Activity activity = getActivity();
        FileLoader.Options options = result.options;

        unload();
        dismissProgress();

        int errorDescription;
        if (error instanceof FileNotFoundException) {
            errorDescription = R.string.toast_error_find_file;
        } else if (error instanceof OutOfMemoryError) {
            errorDescription = R.string.toast_error_out_of_memory;
        } else {
            errorDescription = R.string.toast_error_generic;
        }

        // MetadataLoader failed, so there's no point in trying to parse or upload the file
        offerReopen(activity, options, errorDescription, true);
    }

    @Override
    public void onEncrypted(FileLoader.Result result) {
        if (!isActivityReadyForResult(result)) {
            return;
        }

        Activity activity = getActivity();
        FileLoader.Options options = result.options;

        unload();
        dismissProgress();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.toast_error_password_protected);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton(
                getString(android.R.string.ok),
                (dialog, whichButton) -> {
                    options.password = input.getText().toString();

                    // close dialog before progress is shown again
                    dialog.dismiss();

                    if (result.loaderType == FileLoader.LoaderType.CORE) {
                        loadWithType(FileLoader.LoaderType.CORE, options);
                    } else {
                        throw new RuntimeException(
                                "encryption not supported for type: " + result.loaderType);
                    }
                });
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        builder.show();
    }

    @Override
    public void onUnsupported(FileLoader.Result result) {
        if (!isActivityReadyForResult(result)) {
            return;
        }

        Activity activity = getActivity();
        FileLoader.Options options = result.options;

        unload();
        dismissProgress();

        if (result.loaderType == FileLoader.LoaderType.CORE) {
            if (serviceQueue.getService().isOnlineSupported(options)) {
                offerUpload(activity, options);
            } else {
                offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true);
            }
        } else if (result.loaderType == FileLoader.LoaderType.ONLINE) {
            offerReopen(activity, options, R.string.toast_error_illegal_file_reopen, true);
        }
    }

    @Override
    public void onSaveSuccess(Uri outFile) {
        state.currentHtmlDiff = null;

        SnackbarHelper.show(getActivity(), R.string.toast_edit_status_saved, null, false, false);

        loadUri(outFile, true, true);
    }

    @Override
    public void onSaveError() {
        state.currentHtmlDiff = null;

        SnackbarHelper.show(getActivity(), R.string.toast_error_save_failed, null, true, true);
    }

    private void offerUpload(Activity activity, FileLoader.Options options) {
        String fileType = options.fileType;

        analyticsManager.report(
                "upload_offer_invasive",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                fileType,
                AnalyticsConstants.PARAM_CONTENT,
                options.originalUri);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.toast_error_illegal_file);
        builder.setMessage(R.string.dialog_upload_file);

        builder.setPositiveButton(
                getString(R.string.action_upload),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        analyticsManager.report(
                                "load_upload", AnalyticsConstants.PARAM_CONTENT_TYPE, fileType);

                        loadWithType(FileLoader.LoaderType.ONLINE, options);

                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton(
                getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        analyticsManager.report(
                                "load_upload_cancel",
                                AnalyticsConstants.PARAM_CONTENT_TYPE,
                                fileType);

                        offerReopen(
                                activity, options, R.string.toast_error_illegal_file_reopen, true);

                        dialog.dismiss();
                    }
                });

        builder.show();
    }

    private void offerReopen(
            Activity activity, FileLoader.Options options, int description, boolean isIndefinite) {
        String fileType = options.fileType;

        analyticsManager.report(
                "reopen_offer",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                fileType,
                AnalyticsConstants.PARAM_CONTENT,
                options.originalUri);

        SnackbarHelper.show(
                activity,
                description,
                new Runnable() {
                    @Override
                    public void run() {
                        doReopen(options, activity, true, false);
                    }
                },
                isIndefinite,
                false);
    }

    public void openWith(Activity activity) {
        doReopen(state.lastResult.options, activity, true, false);
    }

    public void share(Activity activity) {
        doReopen(state.lastResult.options, activity, true, true);
    }

    private void doReopen(
            FileLoader.Options options, Activity activity, boolean grantPermission, boolean share) {
        Uri reopenUri;
        Uri cacheUri = options.cacheUri;
        String fileType = options.fileType;

        if (options.fileExists) {
            File cacheFile = AndroidFileCache.getCacheFile(activity, cacheUri);
            File cacheDirectory = AndroidFileCache.getCacheDirectory(cacheFile);

            String reopenFilename = "yourdocument." + options.fileExtension;
            File reopenFile = new File(cacheDirectory, reopenFilename);
            try {
                StreamUtil.copy(cacheFile, reopenFile);

                reopenUri = AndroidFileCache.getCacheFileUri(activity, reopenFile);
            } catch (IOException e) {
                crashManager.log(e);

                reopenUri = options.originalUri;
            }
        } else {
            reopenUri = options.originalUri;
        }

        Intent intent = new Intent();

        String action = share ? Intent.ACTION_SEND : Intent.ACTION_VIEW;
        intent.setAction(action);

        if (!"N/A".equals(fileType)) {
            intent.setDataAndType(reopenUri, fileType);
        } else {
            intent.setData(reopenUri);
        }

        if (share) {
            intent.putExtra(Intent.EXTRA_STREAM, reopenUri);
        }

        if (grantPermission) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        String logPrefix = share ? "share" : "reopen";

        Intent chooserIntent =
                Intent.createChooser(intent, activity.getString(R.string.reopen_chooser_title));

        try {
            activity.startActivity(chooserIntent);

            analyticsManager.report(
                    logPrefix + "_success", AnalyticsConstants.PARAM_CONTENT_TYPE, fileType);
        } catch (Throwable e) {
            crashManager.log(e);

            analyticsManager.report(
                    logPrefix + "_failed", AnalyticsConstants.PARAM_CONTENT_TYPE, fileType);

            if (grantPermission) {
                // if we're trying to reopen the originalUri, the provider might decline the request
                doReopen(options, activity, false, share);
            }
        }
    }

    private void showProgress(boolean isUpload) {
        // getParentFragmentManager() throws when the fragment is not attached, where the
        // deprecated getFragmentManager() used to return null
        if (!isAdded()) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();

        if (progressDialog == null) {
            progressDialog =
                    (ProgressDialogFragment)
                            fragmentManager.findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG);
        }

        if (progressDialog != null) {
            return;
        }

        try {
            progressDialog = new ProgressDialogFragment(isUpload);

            progressDialog.show(fragmentManager, ProgressDialogFragment.FRAGMENT_TAG);
        } catch (IllegalStateException e) {
            // sometimes called while activity is in background
            crashManager.log(e);

            progressDialog = null;
        }
    }

    private void dismissProgress() {
        if (progressDialog == null && isAdded()) {
            progressDialog =
                    (ProgressDialogFragment)
                            getParentFragmentManager()
                                    .findFragmentByTag(ProgressDialogFragment.FRAGMENT_TAG);
        }

        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (IllegalStateException e) {
                // sometimes called while activity is in background
                crashManager.log(e);
            }
        }

        progressDialog = null;
    }

    private final TabLayout.OnTabSelectedListener tabSelectedListener =
            new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    // an edited document must not switch away from the page being edited,
                    // so the previous tab is re-selected instead
                    if (state.lastResult.options.translatable && state.lastSelectedTab >= 0) {
                        // reselecting from inside onTabSelected() does not take effect
                        mainHandler.postDelayed(
                                () -> {
                                    TabLayout.Tab previous =
                                            tabLayout.getTabAt(state.lastSelectedTab);
                                    if (previous != null) {
                                        previous.select();
                                    }
                                },
                                1);

                        return;
                    }

                    // in every mode, so restoreTabs() puts the indicator back on the page
                    // the user was looking at after the view is recreated
                    state.lastSelectedTab = tab.getPosition();

                    loadData(state.lastResult.partUris.get(tab.getPosition()).toString());
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            };

    private void loadData(String url) {
        pageView.loadUrl(url);
    }

    public PageView getPageView() {
        return pageView;
    }

    @VisibleForTesting
    public FileLoader.Result getLastResult() {
        return state.lastResult;
    }

    public boolean hasLastResult() {
        // the activity can hold a freshly constructed fragment that has not been created yet
        return state != null && state.lastResult != null;
    }

    public String getLastFileType() {
        return state.lastResult.options.fileType;
    }

    public CrashManager getCrashManager() {
        return crashManager;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        LoaderService service = serviceQueue.getService();
        if (service != null) {
            service.setListener(null);
        }

        if (pageView != null) {
            pageView.destroy();
        }
    }
}
