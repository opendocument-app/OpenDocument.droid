package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;

public class OdfLoader extends FileLoader {

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public OdfLoader(Context context) {
        super(context, LoaderType.ODF);
    }

    @Override
    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler, AnalyticsManager analyticsManager, CrashManager crashManager) {
        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager);

        // mitigate TimeoutException on finalize
        // https://stackoverflow.com/a/55999687/198996
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (t.getName().equals("FinalizerWatchdogDaemon") && e instanceof TimeoutException) {
                    if (crashManager != null) {
                        crashManager.log(e);
                    }
                } else {
                    defaultUncaughtExceptionHandler.uncaughtException(t, e);
                }
            }
        });
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

        try {
            File cachedFile = AndroidFileCache.getCacheFile(context);

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
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
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
}
