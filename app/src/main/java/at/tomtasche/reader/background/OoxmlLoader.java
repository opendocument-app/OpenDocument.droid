package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;

public class OoxmlLoader extends FileLoader {

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public OoxmlLoader(Context context) {
        super(context, LoaderType.OOXML);
    }

    @Override
    public boolean isSupported(Options options) {
        // TODO: enable xlsx and pptx too
        return options.fileType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document") /*|| options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") || options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.presentation")*/;
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            translate(options, result);

            callOnSuccess(result);
        } catch (Throwable e) {
            if (e instanceof CoreWrapper.CoreEncryptedException) {
                e = new EncryptedDocumentException();
            }

            callOnError(result, e);
        }
    }

    private void translate(Options options, Result result) throws Exception {
        File cacheFile = AndroidFileCache.getCacheFile(context, options.cacheUri);

        if (lastCore != null) {
            lastCore.close();
        }

        CoreWrapper core = new CoreWrapper();
        try {
            core.initialize();

            lastCore = core;
        } catch (Throwable e) {
            crashManager.log(e);
        }

        File cacheDirectory = AndroidFileCache.getCacheDirectory(cacheFile);

        File fakeHtmlFile = new File(cacheDirectory, "ooxml");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = cacheFile.getPath();
        coreOptions.outputPath = fakeHtmlFile.getPath();
        coreOptions.password = options.password;
        coreOptions.editable = options.translatable;
        coreOptions.ooxml = true;

        lastCoreOptions = coreOptions;

        CoreWrapper.CoreResult coreResult = lastCore.parse(coreOptions);
        if (coreResult.exception != null) {
            throw coreResult.exception;
        }

        // fileType could potentially change after decrypting DOCX successfully for the first time
        //  (not reported as DOCX prior)
        options.fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(coreResult.extension);

        for (int i = 0; i < coreResult.pageNames.size(); i++) {
            File entryFile = new File(fakeHtmlFile.getPath() + i + ".html");

            result.partTitles.add(coreResult.pageNames.get(i));
            result.partUris.add(Uri.fromFile(entryFile));
        }
    }

    @Override
    public File retranslate(Options options, String htmlDiff) {
        if (lastCore == null) {
            // necessary if fragment was destroyed in the meanwhile - meaning the Loader is reinstantiated

            Result result = new Result();
            result.options = options;

            try {
                translate(options, result);
            } catch (Exception e) {
                crashManager.log(e);

                return null;
            }
        }

        File inputFile = new File(lastCoreOptions.inputPath);
        File inputCacheDirectory = AndroidFileCache.getCacheDirectory(inputFile);
        File tempFilePrefix = new File(inputCacheDirectory, "retranslate");

        lastCoreOptions.outputPath = tempFilePrefix.getPath();

        try {
            CoreWrapper.CoreResult result = lastCore.backtranslate(lastCoreOptions, htmlDiff);

            return new File(result.outputPath);
        } catch (Throwable e) {
            crashManager.log(e);

            return null;
        }
    }

    @Override
    public void close() {
        super.close();

        if (lastCore != null) {
            lastCore.close();
            lastCore = null;
        }
    }
}
