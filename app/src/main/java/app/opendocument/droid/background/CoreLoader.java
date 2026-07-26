package app.opendocument.droid.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import app.opendocument.droid.nonfree.AnalyticsManager;
import app.opendocument.droid.nonfree.CrashManager;
import java.io.File;

public class CoreLoader extends FileLoader {

    /**
     * Whether odrcore renders text documents with page margins. This used to be read from the
     * "use_paging" remote config key, but that resolved to false for every user since firebase
     * remote config was removed - the ConfigManager left behind is a stub without a backing store.
     * Kept as an explicit constant so the shipped behavior is visible instead of hidden behind a
     * lookup that cannot return a value.
     */
    private static final boolean USE_PAGING = false;

    private CoreWrapper.CoreOptions lastCoreOptions;

    private final boolean doOoxml;

    private Thread httpThread;

    public CoreLoader(Context context, boolean doOoxml) {
        super(context, LoaderType.CORE);

        this.doOoxml = doOoxml;
    }

    @Override
    public void initialize(
            FileLoaderListener listener,
            Handler mainHandler,
            Handler backgroundHandler,
            AnalyticsManager analyticsManager,
            CrashManager crashManager) {
        // loads the native library and extracts the core assets. Kept out of the
        // constructor so that constructing a CoreLoader stays side effect free -
        // LoaderService calls initialize() right after new CoreLoader() anyway.
        CoreWrapper.initialize(context);

        File serverCacheDir = new File(context.getCacheDir(), "core/server");
        if (!serverCacheDir.isDirectory() && !serverCacheDir.mkdirs()) {
            Log.e(
                    "CoreLoader",
                    "Failed to create cache directory for CoreWrapper server: "
                            + serverCacheDir.getAbsolutePath());
        }
        CoreWrapper.createServer(serverCacheDir.getAbsolutePath());

        httpThread =
                new Thread(
                        () -> {
                            try {
                                CoreWrapper.listenServer(29665);
                            } catch (Throwable e) {
                                crashManager.log(e);
                            }
                        });
        httpThread.start();

        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument")
                || options.fileType.startsWith("application/x-vnd.oasis.opendocument")
                || options.fileType.startsWith("application/vnd.oasis.opendocument.text-master")
                || options.fileType.startsWith("application/msword")
                || (this.doOoxml
                        && (options.fileType.startsWith(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                || options.fileType.startsWith(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        // TODO: enable pptx too
                        // options.fileType.startsWith("application/vnd.openxmlformats-officedocument.presentationml.presentation");
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

        File coreOutputDirectory = new File(cacheDirectory, "core_output");
        File coreCacheDirectory = new File(cacheDirectory, "core_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = cachedFile.getPath();
        coreOptions.outputPath = coreOutputDirectory.getPath();
        coreOptions.cachePath = coreCacheDirectory.getPath();
        coreOptions.password = options.password;
        coreOptions.editable = options.translatable;
        coreOptions.ooxml = doOoxml;
        coreOptions.txt = false;
        coreOptions.pdf = false;

        coreOptions.paging = USE_PAGING;

        lastCoreOptions = coreOptions;

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("odr", coreOptions);

        if (coreResult.exception != null) {
            throw coreResult.exception;
        }

        for (int i = 0; i < coreResult.pagePaths.size(); i++) {
            result.partTitles.add(coreResult.pageNames.get(i));
            result.partUris.add(Uri.parse(coreResult.pagePaths.get(i)));
        }
    }

    @Override
    public File retranslate(Options options, String htmlDiff) {
        if (lastCoreOptions == null) {
            // necessary if fragment was destroyed in the meanwhile - meaning the Loader is
            // reinstantiated

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
