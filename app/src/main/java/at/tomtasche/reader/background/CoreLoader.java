package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.ConfigManager;
import at.tomtasche.reader.nonfree.CrashManager;

public class CoreLoader extends FileLoader {

    private final ConfigManager configManager;

    private CoreWrapper.CoreOptions lastCoreOptions;

    private final boolean doOoxml;
    private final boolean doHttp;

    private Thread httpThread;

    public CoreLoader(Context context, ConfigManager configManager, boolean doOoxml, boolean doHttp) {
        super(context, LoaderType.CORE);

        this.configManager = configManager;
        this.doOoxml = doOoxml;
        this.doHttp = doHttp;

        CoreWrapper.initialize(context);
    }

    @Override
    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler, AnalyticsManager analyticsManager, CrashManager crashManager) {
        if (doHttp) {
            File serverCacheDir = new File(context.getCacheDir(), "core/server");
            if (!serverCacheDir.mkdirs()) {
                Log.e("CoreLoader", "Failed to create cache directory for CoreWrapper server: " + serverCacheDir.getAbsolutePath());
            }
            CoreWrapper.createServer(serverCacheDir.getAbsolutePath());

            httpThread = new Thread(() -> {
                try {
                    CoreWrapper.listenServer(29665);
                } catch (Throwable e) {
                    crashManager.log(e);
                }
            });
            httpThread.start();
        }

        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument") ||
                options.fileType.startsWith("application/x-vnd.oasis.opendocument") ||
                options.fileType.startsWith("application/vnd.oasis.opendocument.text-master") ||
                (this.doOoxml && (
                        options.fileType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        // TODO: enable xlsx and pptx too
                        //options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                        //options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.presentation");
                ));
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
        File cachedFile = AndroidFileCache.getCacheFile(context, options.cacheUri);

        File cacheDirectory = AndroidFileCache.getCacheDirectory(cachedFile);

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = cachedFile.getPath();
        coreOptions.outputPath = cacheDirectory.getPath();
        coreOptions.password = options.password;
        coreOptions.editable = options.translatable;
        coreOptions.ooxml = doOoxml;
        coreOptions.txt = false;
        coreOptions.pdf = false;

        Boolean usePaging = configManager.getBooleanConfig("use_paging");
        if (usePaging == null || usePaging) {
            coreOptions.paging = true;
        }

        lastCoreOptions = coreOptions;

        if (doHttp) {
            CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("odr", coreOptions);

            if (coreResult.exception != null) {
                throw coreResult.exception;
            }

            for (int i = 0; i < coreResult.pagePaths.size(); i++) {
                result.partTitles.add(coreResult.pageNames.get(i));
                result.partUris.add(Uri.parse(coreResult.pagePaths.get(i)));
            }
        } else {
            CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);

            String coreExtension = coreResult.extension;
            if (coreResult.exception == null && "pdf".equals(coreExtension)) {
                // some PDFs do not cause an error in the core
                // https://github.com/opendocument-app/OpenDocument.droid/issues/348#issuecomment-2446888981
                throw new CoreWrapper.CoreCouldNotTranslateException();
            } else if (!"unnamed".equals(coreExtension)) {
                // "unnamed" refers to default of Meta::typeToString
                options.fileExtension = coreExtension;

                String fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(coreExtension);
                if (fileType != null) {
                    options.fileType = fileType;
                }
            }

            if (coreResult.exception != null) {
                throw coreResult.exception;
            }

            for (int i = 0; i < coreResult.pagePaths.size(); i++) {
                File entryFile = new File(coreResult.pagePaths.get(i));

                result.partTitles.add(coreResult.pageNames.get(i));
                result.partUris.add(Uri.fromFile(entryFile));
            }
        }
    }

    @Override
    public File retranslate(Options options, String htmlDiff) {
        if (doHttp) {
            return null; // TODO
        }

        if (lastCoreOptions == null) {
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
            CoreWrapper.CoreResult result = CoreWrapper.backtranslate(lastCoreOptions, htmlDiff);

            return new File(result.outputPath);
        } catch (Throwable e) {
            crashManager.log(e);

            return null;
        }
    }

    @Override
    public void close() {
        super.close();

        if (httpThread != null) {
            CoreWrapper.stopServer();
            try {
                httpThread.join(1000);
            } catch (InterruptedException e) {
                crashManager.log(e);
            }
            httpThread = null;
        }

        CoreWrapper.close();
    }
}
