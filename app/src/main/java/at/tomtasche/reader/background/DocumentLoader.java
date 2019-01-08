package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import at.stefl.commons.math.vector.Vector2i;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.document.BulkPresentationTranslator;
import at.stefl.opendocument.java.translator.document.BulkSpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslatorUtil;
import at.stefl.opendocument.java.translator.document.PresentationTranslator;
import at.stefl.opendocument.java.translator.document.SpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;
import at.tomtasche.reader.background.Document.Page;

public class DocumentLoader implements FileLoader {

    private Context context;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Handler mainHandler;

    private FileLoaderListener listener;

    private boolean initialized;
    private boolean loading;

    private DocumentTranslator lastTranslator;

    public DocumentLoader(Context context) {
        this.context = context;
    }

    @Override
    public void initialize(FileLoaderListener listener) {
        this.listener = listener;

        mainHandler = new Handler();

        backgroundThread = new HandlerThread(DocumentLoader.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        initialized = true;
    }

    @Override
    public double getProgress() {
        if (initialized && lastTranslator != null) {
            return lastTranslator.getCurrentProgress();
        }

        return 0;
    }

    @Override
    public boolean isLoading() {
        return loading;
    }

    @Override
    public void loadAsync(Uri uri, String password, boolean limit, boolean translatable) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                loadSync(uri, password, limit, translatable);
            }
        });
    }

    @Override
    public void loadSync(Uri uri, String password, boolean limit, boolean translatable) {
        if (!initialized) {
            throw new RuntimeException("not initialized");
        }

        loading = true;
        lastTranslator = null;

        InputStream stream = null;

        LocatedOpenDocumentFile documentFile = null;
        try {
            // cleanup uri
            if ("/./".equals(uri.toString().substring(0, 2))) {
                uri = Uri.parse(uri.toString().substring(2,
                        uri.toString().length()));
            }

            AndroidFileCache cache = new AndroidFileCache(context);
            // TODO: don't delete file being displayed at the moment, but
            // keep it until the new document has finished loading.
            // this must not delete document.odt!
            AndroidFileCache.cleanup(context);

            if (uri.equals(AndroidFileCache.getCacheFileUri())) {
                documentFile = new LocatedOpenDocumentFile(new File(
                        AndroidFileCache.getCacheDirectory(context),
                        "document.odt"));
            } else {
                stream = context.getContentResolver().openInputStream(
                        uri);

                File cachedFile = cache.create("document.odt", stream);
                documentFile = new LocatedOpenDocumentFile(cachedFile);
            }

            try {
                String filename = null;
                // https://stackoverflow.com/a/38304115/198996
                Cursor fileCursor = context.getContentResolver().query(uri, null, null, null, null);
                if (fileCursor != null) {
                    int nameIndex = fileCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    fileCursor.moveToFirst();
                    filename = fileCursor.getString(nameIndex);
                    fileCursor.close();
                } else {
                    filename = uri.getLastPathSegment();
                }

                RecentDocumentsUtil.addRecentDocument(context,
                        filename, uri);
            } catch (IOException e) {
                // not a showstopper, so just continue
                e.printStackTrace();
            }

            if (documentFile.isEncrypted()) {
                if (password == null)
                    throw new EncryptedDocumentException();

                documentFile.setPassword(password);
                if (!documentFile.isPasswordValid())
                    throw new EncryptedDocumentException();
            }

            OpenDocument openDocument = documentFile.getAsDocument();
            Document document = new Document(openDocument);

            TranslationSettings settings = new TranslationSettings();
            settings.setCache(cache);
            settings.setBackTranslateable(translatable);
            settings.setImageStoreMode(ImageStoreMode.CACHE);
            settings.setSplitPages(true);
            if (limit) {
                settings.setMaxTableDimension(new Vector2i(5000, 1000));
                settings.setMaxRowRepetition(100);
            }

            // https://github.com/andiwand/OpenDocument.java/blob/7f13222f77fabd62ee6a9d52cd6ed3e512532a9b/src/at/stefl/opendocument/java/translator/document/DocumentTranslatorUtil.java#L131
            if (!settings.isSplitPages() || (openDocument instanceof OpenDocumentText)) {
                if (openDocument instanceof OpenDocumentText) {
                    lastTranslator = new TextTranslator();
                } else if (openDocument instanceof OpenDocumentSpreadsheet) {
                    lastTranslator = new SpreadsheetTranslator();
                } else if (openDocument instanceof OpenDocumentPresentation) {
                    lastTranslator = new PresentationTranslator();
                } else {
                    throw new IllegalStateException("unsupported document");
                }
            } else {
                if (openDocument instanceof OpenDocumentSpreadsheet) {
                    lastTranslator = new BulkSpreadsheetTranslator();
                } else if (openDocument instanceof OpenDocumentPresentation) {
                    lastTranslator = new BulkPresentationTranslator();
                } else {
                    throw new IllegalStateException("unsupported document");
                }
            }

            DocumentTranslatorUtil.Output output = DocumentTranslatorUtil.provideOutput(
                    openDocument, settings, "temp", ".html");
            try {
                lastTranslator.translate(openDocument, output.getWriter(), settings);
            } finally {
                output.getWriter().close();
            }

            for (int i = 0; i < output.getNames().size(); i++) {
                File htmlFile = cache.getFile(output.getNames().get(i));

                document.addPage(new Page(output.getTitles().get(i),
                        htmlFile.toURI(), i));
            }

            document.setLimited(lastTranslator.isCurrentOutputTruncated());

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onSuccess(document);
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            });
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
            }

            try {
                if (documentFile != null)
                    documentFile.close();
            } catch (IOException e) {
            }
        }

        loading = false;
    }

    @Override
    public void close() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initialized = false;
                listener = null;
                context = null;
                lastTranslator = null;

                backgroundThread.quit();
                backgroundThread = null;
                backgroundHandler = null;
            }
        });
    }


    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {
    }
}
