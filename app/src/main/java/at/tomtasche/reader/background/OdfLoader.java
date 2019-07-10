package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.OpenableColumns;

import com.hzy.libmagic.MagicApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import at.stefl.commons.math.vector.Vector2i;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentGraphics;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.document.BulkPresentationTranslator;
import at.stefl.opendocument.java.translator.document.BulkSpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslatorUtil;
import at.stefl.opendocument.java.translator.document.GraphicsTranslator;
import at.stefl.opendocument.java.translator.document.PresentationTranslator;
import at.stefl.opendocument.java.translator.document.SpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;
import at.tomtasche.reader.background.Document.Page;

public class OdfLoader implements FileLoader {

    private Context context;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Handler mainHandler;

    private FileLoaderListener listener;

    private boolean initialized;
    private boolean loading;

    private DocumentTranslator lastTranslator;

    public OdfLoader(Context context) {
        this.context = context;
    }

    @Override
    public void initialize(FileLoaderListener listener) {
        this.listener = listener;

        mainHandler = new Handler();

        backgroundThread = new HandlerThread(OdfLoader.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        initialized = true;
    }

    private boolean initMagicFromAssets() {
        try {
            InputStream inputStream = context.getAssets().open("magic.mgc");
            int length = inputStream.available();
            byte[] buffer = new byte[length];
            if (inputStream.read(buffer) > 0) {
                return MagicApi.loadFromBytes(buffer, MagicApi.MAGIC_MIME_TYPE) == 0;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
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
    public void loadAsync(Uri uri, String ignoreType, String password, boolean limit, boolean translatable) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                loadSync(uri, ignoreType, password, limit, translatable);
            }
        });
    }

    @Override
    public void loadSync(Uri uri, String ignoreType, String password, boolean limit, boolean translatable) {
        if (!initialized) {
            throw new RuntimeException("not initialized");
        }

        loading = true;
        lastTranslator = null;

        InputStream stream = null;
        String type = null;

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

            File cachedFile;
            if (uri.equals(AndroidFileCache.getCacheFileUri())) {
                cachedFile = AndroidFileCache.getCacheFile(context);
            } else {
                stream = context.getContentResolver().openInputStream(
                        uri);

                cachedFile = cache.create("document.odt", stream);
            }

            String filename = null;
            try {
                // https://stackoverflow.com/a/38304115/198996
                Cursor fileCursor = context.getContentResolver().query(uri, null, null, null, null);
                if (fileCursor != null && fileCursor.moveToFirst()) {
                    int nameIndex = fileCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    filename = fileCursor.getString(nameIndex);
                    fileCursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();

                // "URI does not contain a valid access token." or
                // "Couldn't read row 0, col -1 from CursorWindow. Make sure the Cursor is initialized correctly before accessing data from it."
            }

            if (filename == null) {
                filename = uri.getLastPathSegment();
            }

            if (initMagicFromAssets()) {
                try {
                    type = MagicApi.magicFile(cachedFile.getAbsolutePath());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            if (type == null) {
                type = context.getContentResolver().getType(uri);
            }

            if (type == null && filename != null) {
                try {
                    type = URLConnection.guessContentTypeFromName(filename);
                } catch (Exception e) {
                    // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
                    e.printStackTrace();
                }
            }

            if (type == null) {
                try {
                    InputStream tempStream = new FileInputStream(cachedFile);
                    try {
                        type = URLConnection.guessContentTypeFromStream(tempStream);
                    } finally {
                        tempStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                RecentDocumentsUtil.addRecentDocument(context, filename, uri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            documentFile = new LocatedOpenDocumentFile(cachedFile);

            if (documentFile.isEncrypted()) {
                if (password == null)
                    throw new EncryptedDocumentException();

                documentFile.setPassword(password);
                if (!documentFile.isPasswordValid())
                    throw new EncryptedDocumentException();
            }

            OpenDocument openDocument = documentFile.getAsDocument();
            Document document = new Document();

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
            if (!settings.isSplitPages() || (openDocument instanceof OpenDocumentText || openDocument instanceof OpenDocumentGraphics)) {
                if (openDocument instanceof OpenDocumentText) {
                    lastTranslator = new TextTranslator();
                } else if (openDocument instanceof OpenDocumentSpreadsheet) {
                    lastTranslator = new SpreadsheetTranslator();
                } else if (openDocument instanceof OpenDocumentPresentation) {
                    lastTranslator = new PresentationTranslator();
                } else if (openDocument instanceof OpenDocumentGraphics) {
                    lastTranslator = new GraphicsTranslator();
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

            final String fileType = type;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    FileLoaderListener strongReferenceListener = listener;
                    if (strongReferenceListener != null) {
                        listener.onSuccess(LoaderType.ODF, document, fileType);
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();

            final String fileType = type;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    FileLoaderListener strongReferenceListener = listener;
                    if (strongReferenceListener != null) {
                        listener.onError(LoaderType.ODF, e, fileType);
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
