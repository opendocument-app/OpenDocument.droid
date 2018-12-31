package at.tomtasche.reader.ui.activity;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.background.UpLoader;
import at.tomtasche.reader.ui.CroutonHelper;
import at.tomtasche.reader.ui.widget.PageView;
import at.tomtasche.reader.ui.widget.ProgressDialogFragment;
import de.keyboardsurfer.android.widget.crouton.Style;

public class DocumentFragment extends Fragment implements FileLoader.FileLoaderListener, ActionBar.TabListener {

    private Handler mainHandler;

    private DocumentLoader documentLoader;
    private UpLoader upLoader;

    private ProgressDialogFragment progressDialog;
    private PageView pageView;

    private Uri lastUri;
    private String lastPassword;
    private Document lastDocument;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler();

        documentLoader = new DocumentLoader(getContext());
        documentLoader.initialize(this);

        upLoader = new UpLoader(getContext());
        upLoader.initialize(this);

        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        pageView = new PageView(getActivity());
        pageView.loadData("", "text/plain", PageView.ENCODING);

        return pageView;
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

        documentLoader.loadAsync(uri, password, limit, translatable);

        showProgress(documentLoader, false);
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

            // TODO: ugly and risky cast
            documentFile = new LocatedOpenDocumentFile(((LocatedOpenDocumentFile) lastDocument.getOrigin().getDocumentFile()).getFile());
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

            File modifiedFile = new File(Environment
                    .getExternalStorageDirectory(),
                    "modified-by-opendocument-reader." + extension);
            modifiedStream = new FileOutputStream(modifiedFile);

            Retranslator.retranslate(openDocument, htmlStream,
                    modifiedStream);

            modifiedStream.close();

            Uri fileUri = Uri.parse("file://"
                    + modifiedFile.getAbsolutePath());

            loadUri(fileUri, lastPassword, false, true);
        } catch (final Throwable e) {
            e.printStackTrace();

            onError(e);
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

    public void uploadUri(Uri uri) {
        lastUri = uri;
        lastPassword = null;

        upLoader.loadAsync(uri, null, false, false);

        showProgress(upLoader, true);
    }

    @Override
    public void onSuccess(Document document) {
        this.lastDocument = document;

        dismissProgress();

        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        // TODO: we should load the first page here already
        // DocumentFragment should - basically - work out-of-the-box
        // (without any further logic)!

        if (document.isLimited()) {
            CroutonHelper.showCrouton(getActivity(), R.string.toast_info_limited, new Runnable() {

                @Override
                public void run() {
                    loadUri(lastUri, lastPassword,
                            false, false);
                }
            }, Style.INFO);
        }

        ActionBar bar = ((MainActivity) getActivity()).getSupportActionBar();
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

    @Override
    public void onError(Throwable error) {
        lastDocument = null;

        dismissProgress();

        final Uri cacheUri = AndroidFileCache.getCacheFileUri();

        int errorDescription;
        if (error == null) {
            throw new RuntimeException("no error given");
        } else if (error instanceof EncryptedDocumentException) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.toast_error_password_protected);

            final EditText input = new EditText(getActivity());
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

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    builder.show();
                }
            });

            return;
        } else if (error instanceof IllegalMimeTypeException
                || error instanceof ZipException
                || error instanceof ZipEntryNotFoundException
                || error instanceof UnsupportedMimeTypeException) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.toast_error_illegal_file);
            builder.setMessage(R.string.dialog_upload_file);
            builder.setPositiveButton(getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog,
                                            int whichButton) {
                            uploadUri(lastUri);

                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(getString(android.R.string.cancel), null);

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    builder.show();
                }
            });

            return;
        } else if (error instanceof FileNotFoundException) {
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED_READ_ONLY)
                    || Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED)) {
                errorDescription = R.string.toast_error_find_file;
            } else {
                errorDescription = R.string.toast_error_storage;
            }
        } else if (error instanceof IllegalArgumentException) {
            errorDescription = R.string.toast_error_illegal_file;
        } else if (error instanceof OutOfMemoryError) {
            errorDescription = R.string.toast_error_out_of_memory;
        } else {
            errorDescription = R.string.toast_error_generic;
        }

        CroutonHelper.showCrouton(getActivity(), errorDescription, null, Style.ALERT);

        Log.e("OpenDocument Reader", "Error opening file at " + lastUri.toString(),
                error);

        ((MainActivity) getActivity()).getCrashManager().log(error, lastUri);
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

    @SuppressWarnings("deprecation")
    public void searchDocument(String query) {
        pageView.findAll(query);
    }

    public PageView getPageView() {
        return pageView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (documentLoader != null) {
            documentLoader.close();
        }

        if (upLoader != null) {
            upLoader.close();
        }
    }
}
