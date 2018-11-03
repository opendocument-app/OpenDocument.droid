package at.tomtasche.reader.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.appcompat.view.ActionMode;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.Retranslator;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.nonfree.AdManager;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.PageView;

public class EditActionModeCallback implements ActionMode.Callback {

    private MainActivity activity;
    private DocumentFragment documentFragment;
    private AdManager adManager;
    private PageView pageView;
    private TextView statusView;
    private OpenDocument document;

    private InputMethodManager imm;

    public EditActionModeCallback(MainActivity activity, DocumentFragment documentFragment, AdManager adManager, PageView pageView,
                                  OpenDocument document) {
        this.activity = activity;
        this.documentFragment = documentFragment;
        this.adManager = adManager;
        this.pageView = pageView;
        this.document = document;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        statusView = new TextView(activity);
        statusView.setText(R.string.action_edit_banner);
        mode.setCustomView(statusView);

        mode.getMenuInflater().inflate(R.menu.edit, menu);

        imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // reload document with translation enabled
        documentFragment.loadUri(AndroidFileCache.getCacheFileUri(), documentFragment.getDocumentLoader().getPassword(), false, true);

        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.edit_help: {
                documentFragment.startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://plus.google.com/communities/113494011673882132018")));

                break;
            }

            case R.id.edit_save: {
                adManager.showInterstitial();

                final File htmlFile = new File(
                        AndroidFileCache.getCacheDirectory(activity),
                        "content.html");
                pageView.requestHtml(htmlFile, new Runnable() {

                    @Override
                    public void run() {
                        Uri fileUri = null;
                        FileInputStream htmlStream = null;
                        FileOutputStream modifiedStream = null;
                        LocatedOpenDocumentFile documentFile = null;
                        try {
                            htmlStream = new FileInputStream(htmlFile);

                            // TODO: ugly and risky cast
                            documentFile = new LocatedOpenDocumentFile(
                                    ((LocatedOpenDocumentFile) document
                                            .getDocumentFile()).getFile());

                            String extension = "unknown";
                            OpenDocument openDocument = documentFile
                                    .getAsDocument();
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

                            fileUri = Uri.parse("file://"
                                    + modifiedFile.getAbsolutePath());

                            documentFragment.loadUri(fileUri);

                            activity.showSaveCroutonLater(modifiedFile, fileUri);
                        } catch (final Throwable e) {
                            e.printStackTrace();

                            final Uri cacheUri = AndroidFileCache.getCacheFileUri();
                            final Uri htmlUri = AndroidFileCache
                                    .getHtmlCacheFileUri();

                            documentFragment.handleError(e, cacheUri);
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
                });

                break;
            }

            default:
                return false;
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        imm.toggleSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), 0, 0);
    }
}
