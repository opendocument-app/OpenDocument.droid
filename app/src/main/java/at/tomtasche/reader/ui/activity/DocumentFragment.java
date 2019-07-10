package at.tomtasche.reader.ui.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import at.stefl.opendocument.java.odf.IllegalMimeTypeException;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.odf.UnsupportedMimeTypeException;
import at.stefl.opendocument.java.odf.ZipEntryNotFoundException;
import at.stefl.opendocument.java.translator.Retranslator;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.OdfLoader;
import at.tomtasche.reader.background.OdfLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.background.UpLoader;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.widget.FailsafePDFPagerAdapter;
import at.tomtasche.reader.ui.widget.PageView;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;
import at.tomtasche.reader.ui.widget.VerticalViewPager;

public class DocumentFragment extends Fragment implements FileLoader.FileLoaderListener, ActionBar.TabListener {

    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/", "application/json", "application/xml"};
    private static final String[] MIME_BLACKLIST = {};

    private Handler mainHandler;

    private OdfLoader odfLoader;
    private UpLoader upLoader;

    private ProgressDialogFragment progressDialog;

    private PageView pageView;

    private VerticalViewPager pdfView;
    private FailsafePDFPagerAdapter pdfAdapter;

    private Menu menu;

    private Uri lastUri;
    private String lastPassword;
    private Document lastDocument;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler();

        odfLoader = new OdfLoader(getContext());
        odfLoader.initialize(this);

        upLoader = new UpLoader(getContext());
        upLoader.initialize(this);

        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            LinearLayout inflatedView = (LinearLayout) inflater.inflate(R.layout.fragment_document, container, false);

            pageView = inflatedView.findViewById(R.id.page_view);
            pdfView = inflatedView.findViewById(R.id.pdf_view);

            return inflatedView;
        } catch (Throwable t) {
            ((MainActivity) getActivity()).getCrashManager().log("no webview installed: " + t.getMessage());

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

    public void loadUri(Uri uri) {
        loadUri(uri, null, false, false);
    }

    public void loadUri(Uri uri, String password) {
        loadUri(uri, password, false, false);
    }

    public void loadUri(Uri uri, String password, boolean limit, boolean translatable) {
        lastUri = uri;
        lastPassword = password;

        showProgress(odfLoader, false);

        toggleDocumentMenu(true);
        togglePageView(true);

        odfLoader.loadAsync(uri, null, password, limit, translatable);
    }

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

    public void reloadUri(boolean limit, boolean translatable) {
        loadUri(AndroidFileCache.getCacheFileUri(), lastPassword, limit, translatable);
    }

    public void save(File htmlFile) {
        FileInputStream htmlStream = null;
        FileOutputStream modifiedStream = null;
        LocatedOpenDocumentFile documentFile = null;
        try {
            htmlStream = new FileInputStream(htmlFile);

            documentFile = new LocatedOpenDocumentFile(AndroidFileCache.getCacheFile(getActivity()));
            documentFile.setPassword(lastPassword);

            String extension = "unknown";
            OpenDocument openDocument = documentFile.getAsDocument();
            if (openDocument instanceof OpenDocumentText) {
                extension = "odt";
            } else if (openDocument instanceof OpenDocumentSpreadsheet) {
                extension = "ods";
            } else if (openDocument instanceof OpenDocumentPresentation) {
                extension = "odp";
            }

            DateFormat dateFormat = new SimpleDateFormat("MMddyyyy-HHmmss", Locale.US);
            Date nowDate = Calendar.getInstance().getTime();
            String nowString = dateFormat.format(nowDate);

            File modifiedFile = new File(Environment.getExternalStorageDirectory(),
                    "modified-by-opendocument-reader-on-" + nowString + "." + extension);
            modifiedStream = new FileOutputStream(modifiedFile);

            Retranslator.retranslate(openDocument, htmlStream,
                    modifiedStream);

            modifiedStream.close();

            Uri fileUri = Uri.parse("file://"
                    + modifiedFile.getAbsolutePath());

            loadUri(fileUri, lastPassword, false, true);
        } catch (final Throwable e) {
            e.printStackTrace();

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onError(FileLoader.LoaderType.SAVE, e, null);
                }
            });
        } finally {
            if (documentFile != null) {
                try {
                    documentFile.close();
                } catch (IOException e) {
                }
            }

            if (htmlStream != null) {
                try {
                    htmlStream.close();
                } catch (IOException e) {
                }
            }

            if (modifiedStream != null) {
                try {
                    modifiedStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void uploadUri(Uri uri, String fileType) {
        lastUri = uri;
        lastPassword = null;

        showProgress(upLoader, true);

        togglePageView(true);
        toggleDocumentMenu(true, false);

        upLoader.loadAsync(uri, fileType, null, false, false);
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
    public void onSuccess(FileLoader.LoaderType loaderType, Document document, String fileType) {
        this.lastDocument = document;

        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        MainActivity mainActivity = (MainActivity) activity;

        mainActivity.getAnalyticsManager().report("load_success", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

        dismissProgress();

        // TODO: we should load the first page here already
        // DocumentFragment should - basically - work out-of-the-box
        // (without any further logic)!

        ActionBar bar = mainActivity.getSupportActionBar();
        bar.removeAllTabs();

        int pages = document.getPages().size();
        if (pages > 1) {
            bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            for (int i = 0; i < pages; i++) {
                ActionBar.Tab tab = bar.newTab();
                String name = document.getPageAt(i).getName();
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
                showPage(document.getPageAt(0));
            }
        }
    }

    // taken from: https://stackoverflow.com/a/9293885/198996
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    @Override
    public void onError(FileLoader.LoaderType loaderType, Throwable error, String fileType) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        final MainActivity mainActivity = (MainActivity) activity;
        final AnalyticsManager analyticsManager = mainActivity.getAnalyticsManager();
        final Uri cacheUri = AndroidFileCache.getCacheFileUri();

        dismissProgress();

        if (loaderType == FileLoader.LoaderType.FIREBASE) {
            analyticsManager.report("upload_error");

            offerReopen(fileType, activity, analyticsManager, cacheUri, R.string.toast_error_illegal_file);

            return;
        }

        analyticsManager.report("load_error", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

        int errorDescription;
        if (error == null) {
            throw new RuntimeException("no error given");
        } else if (error instanceof EncryptedDocumentException) {
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
                            loadUri(cacheUri, input.getText().toString());

                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(getString(android.R.string.cancel), null);
            builder.show();

            return;
        } else if (error instanceof IllegalMimeTypeException
                || error instanceof ZipException
                || error instanceof ZipEntryNotFoundException
                || error instanceof UnsupportedMimeTypeException
                || error instanceof IllegalArgumentException) {
            if (fileType != null) {
                for (String mime : MIME_WHITELIST) {
                    if (!fileType.startsWith(mime)) {
                        continue;
                    }

                    boolean blacklisted = false;
                    for (String blackMime : MIME_BLACKLIST) {
                        if (fileType.startsWith(blackMime)) {
                            blacklisted = true;
                            break;
                        }
                    }

                    if (!blacklisted) {
                        Document document = new Document();

                        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);
                        if (extension == null || extension.equals("csv")) {
                            // WebView doesn't display CSV if it has that extension
                            extension = "txt";
                        }

                        File cacheFile = AndroidFileCache.getCacheFile(activity);
                        File renamedFile = new File(cacheFile.getParentFile(), "temp." + extension);

                        try {
                            copy(cacheFile, renamedFile);
                        } catch (IOException e) {
                            e.printStackTrace();

                            renamedFile = cacheFile;
                        }

                        document.addPage(new Document.Page("Document", renamedFile.toURI(),
                                0));

                        onSuccess(FileLoader.LoaderType.RAW, document, fileType);

                        return;
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && fileType != null && fileType.equals("application/pdf")) {
                boolean pdfSuccess = loadPdf(cacheUri);

                if (pdfSuccess) {
                    analyticsManager.report("load_success", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);
                    analyticsManager.report("load_pdf");

                    ActionBar bar = ((MainActivity) activity).getSupportActionBar();
                    bar.removeAllTabs();

                    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

                    return;
                }
            }

            analyticsManager.report("load_error_unknown_format", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

            offerUpload(fileType, activity, analyticsManager, cacheUri);

            return;
        } else if (error instanceof FileNotFoundException) {
            analyticsManager.report("load_error_file_not_found");

            errorDescription = R.string.toast_error_find_file;
        } else if (error instanceof OutOfMemoryError) {
            analyticsManager.report("load_error_memory");

            errorDescription = R.string.toast_error_out_of_memory;
        } else {
            analyticsManager.report("load_error_generic");

            errorDescription = R.string.toast_error_generic;
        }

        if (loaderType != FileLoader.LoaderType.SAVE) {
            offerReopen(fileType, activity, analyticsManager, cacheUri, errorDescription);
        }

        Log.e("OpenDocument Reader", "Error opening file at " + lastUri.toString(),
                error);

        ((MainActivity) activity).getCrashManager().log(error, lastUri);
    }

    private void offerUpload(String fileType, Activity activity, AnalyticsManager analyticsManager, Uri cacheUri) {
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

                            uploadUri(cacheUri, fileType);

                            dialog.dismiss();
                        }
                    });
        }

        builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                analyticsManager.report("load_upload_cancel", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

                offerReopen(fileType, activity, analyticsManager, cacheUri, R.string.toast_error_illegal_file);

                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void offerReopen(String fileType, Activity activity, AnalyticsManager analyticsManager, Uri cacheUri, int errorDescription) {
        analyticsManager.report("reopen_offer", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

        SnackbarHelper.show(activity, errorDescription, new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(cacheUri, fileType);

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
                        targetIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        targetIntents.add(targetIntent);
                    }
                }

                if (targetIntents.size() > 0) {
                    analyticsManager.report("reopen_success", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);

                    Intent chooserIntent = Intent.createChooser(targetIntents.remove(0), activity.getString(R.string.reopen_chooser_title));
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[]{}));
                    activity.startActivity(chooserIntent);
                } else {
                    analyticsManager.report("reopen_failed_noapp", FirebaseAnalytics.Param.CONTENT_TYPE, fileType);
                }
            }
        }, true, true);
    }

    private void showProgress(final FileLoader fileLoader,
                              final boolean upload) {
        if (progressDialog != null) {
            return;
        }

        try {
            progressDialog = new ProgressDialogFragment(upload);

            FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager == null) {
                // TODO: use crashmanager
                progressDialog = null;

                return;
            }

            FragmentTransaction transaction = fragmentManager.beginTransaction();
            progressDialog.show(transaction,
                    ProgressDialogFragment.FRAGMENT_TAG);

            if (!upload) {
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

                        progressDialog.setProgress(fileLoader.getProgress());

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

    private void dismissProgress() {
        // dirty hack because committing isn't allowed right after
        // onLoadFinished:
        // "java.lang.IllegalStateException: Can not perform this action inside of onLoadFinished"

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

    @Override
    public void onTabSelected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
        Document.Page page = lastDocument.getPageAt(tab.getPosition());
        showPage(page);
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, androidx.fragment.app.FragmentTransaction ft) {
    }

    private void showPage(Document.Page page) {
        loadData(page.getUrl());
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

        if (odfLoader != null) {
            odfLoader.close();
        }

        if (upLoader != null) {
            upLoader.close();
        }

        if (pdfAdapter != null) {
            pdfAdapter.close();
        }
    }
}
