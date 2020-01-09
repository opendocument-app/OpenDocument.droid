package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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

public class OdfLoader extends FileLoader {

    private static final boolean USE_CPP = true;

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public OdfLoader(Context context) {
        super(context, LoaderType.ODF);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument") || options.fileType.startsWith("application/x-vnd.oasis.opendocument");
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        DocumentTranslator lastTranslator;

        LocatedOpenDocumentFile documentFile = null;
        try {
            File cachedFile = AndroidFileCache.getCacheFile(context);

            if (USE_CPP) {
                if (lastCore != null) {
                    lastCore.close();
                }

                CoreWrapper core = new CoreWrapper();
                try {
                    core.initialize();

                    lastCore = core;
                } catch (Throwable e) {
                    e.printStackTrace();

                    crashManager.log(e);
                }
            }

            if (lastCore != null && lastCore.initialized) {
                File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

                File fakeHtmlFile = new File(cacheDirectory, "odf");

                CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
                coreOptions.inputPath = cachedFile.getPath();
                coreOptions.outputPath = fakeHtmlFile.getPath();
                coreOptions.password = options.password;
                coreOptions.editable = options.translatable;

                lastCoreOptions = coreOptions;

                CoreWrapper.CoreResult coreResult = lastCore.parse(coreOptions);
                if (coreResult.errorCode == 0) {
                    for (int i = 0; i < coreResult.pageNames.size(); i++) {
                        File entryFile = new File(fakeHtmlFile.getPath() + i + ".html");

                        result.partTitles.add(coreResult.pageNames.get(i));
                        result.partUris.add(Uri.fromFile(entryFile));
                    }

                    callOnSuccess(result);
                } else {
                    if (coreResult.errorCode == -2) {
                        throw new EncryptedDocumentException();
                    } else {
                        throw new RuntimeException("failed with code " + coreResult.errorCode);
                    }
                }

                return;
            }

            // hardcode translatable for java because we are using the new editing approach already
            options.translatable = false;

            documentFile = new LocatedOpenDocumentFile(cachedFile);

            if (documentFile.isEncrypted()) {
                String password = options.password;
                if (password == null)
                    throw new EncryptedDocumentException();

                documentFile.setPassword(password);
                if (!documentFile.isPasswordValid())
                    throw new EncryptedDocumentException();
            }

            OpenDocument openDocument = documentFile.getAsDocument();

            AndroidFileCache cache = new AndroidFileCache(context);

            TranslationSettings settings = new TranslationSettings();
            settings.setCache(cache);
            settings.setBackTranslateable(options.translatable);
            settings.setImageStoreMode(ImageStoreMode.CACHE);
            settings.setSplitPages(true);
            if (options.limit) {
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

                result.partTitles.add(output.getTitles().get(i));
                result.partUris.add(Uri.fromFile(htmlFile));
            }

            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
        } finally {
            try {
                if (documentFile != null)
                    documentFile.close();
            } catch (IOException e) {
            }
        }
    }

    public File retranslate(String htmlDiff) {
        DateFormat dateFormat = new SimpleDateFormat("MMddyyyy-HHmmss", Locale.US);
        Date nowDate = Calendar.getInstance().getTime();
        String nowString = dateFormat.format(nowDate);

        File modifiedFilePrefix = new File(Environment.getExternalStorageDirectory(),
                "modified-by-opendocument-reader-on-" + nowString);

        lastCoreOptions.outputPath = modifiedFilePrefix.getPath();

        CoreWrapper.CoreResult result = lastCore.backtranslate(lastCoreOptions, htmlDiff);
        if (result.errorCode != 0) {
            throw new RuntimeException("could not retranslate file with error " + result.errorCode);
        }

        return new File(result.outputPath);
    }

    @Override
    public void close() {
        super.close();

        if (lastCore != null) {
            lastCore.close();
            lastCore = null;
        }
    }

    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {
    }
}
