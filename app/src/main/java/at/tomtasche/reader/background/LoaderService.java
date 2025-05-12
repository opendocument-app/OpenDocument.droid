package at.tomtasche.reader.background;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.OutputStream;

import at.tomtasche.reader.R;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.ConfigManager;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.activity.DocumentFragment;

public class LoaderService extends Service implements FileLoader.FileLoaderListener {

    private Handler mainHandler;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private CrashManager crashManager;
    private ConfigManager configManager;
    private AnalyticsManager analyticsManager;

    private MetadataLoader metadataLoader;
    private CoreLoader coreLoader;
    private WvwareDocLoader wvwareDocLoader;
    private RawLoader rawLoader;
    private OnlineLoader onlineLoader;

    private LoaderListener currentListener;

    @Override
    public synchronized void onCreate() {
        super.onCreate();

        mainHandler = new Handler();

        backgroundThread = new HandlerThread(DocumentFragment.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        Context context = this;

        initializeProprietaryLibraries();

        metadataLoader = new MetadataLoader(context);
        metadataLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        coreLoader = new CoreLoader(context, configManager, true, true);
        coreLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        wvwareDocLoader = new WvwareDocLoader(context);
        wvwareDocLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        rawLoader = new RawLoader(context);
        rawLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);

        onlineLoader = new OnlineLoader(context, coreLoader);
        onlineLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager);
    }

    // copied from MainActivity, consider how to deduplicate
    private void initializeProprietaryLibraries() {
        boolean useProprietaryLibraries = !getResources().getBoolean(R.bool.DISABLE_TRACKING);

        if (useProprietaryLibraries) {
            GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
            int googleAvailability = googleApi.isGooglePlayServicesAvailable(this);
            if (googleAvailability != ConnectionResult.SUCCESS) {
                useProprietaryLibraries = false;
            }
        }

        crashManager = new CrashManager();
        crashManager.setEnabled(useProprietaryLibraries);
        crashManager.initialize();

        analyticsManager = new AnalyticsManager();
        analyticsManager.setEnabled(useProprietaryLibraries);
        analyticsManager.initialize(this);

        configManager = new ConfigManager();
        configManager.setEnabled(useProprietaryLibraries);
        configManager.initialize();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LoaderBinder();
    }

    public synchronized void setListener(LoaderListener listener) {
        this.currentListener = listener;
    }

    private void logMissingListener() {
        crashManager.log(new RuntimeException("missing listener"));
    }

    public synchronized void loadWithType(FileLoader.LoaderType loaderType, FileLoader.Options options) {
        FileLoader loader;
        switch (loaderType) {
            case CORE:
                loader = coreLoader;
                break;
            case WVWARE:
                loader = wvwareDocLoader;
                break;
            case ONLINE:
                loader = onlineLoader;
                break;
            case RAW:
                loader = rawLoader;
                break;
            case METADATA:
                loader = metadataLoader;
                break;
            default:
                loader = null;
        }

        loader.loadAsync(options);
    }

    @Override
    public void onSuccess(FileLoader.Result result) {
        FileLoader.Options options = result.options;
        if (result.loaderType == FileLoader.LoaderType.METADATA) {
            if (!coreLoader.isSupported(options)) {
                crashManager.log("we do not expect this file to be an ODF: " + options.originalUri.toString());
                analyticsManager.report("load_odf_error_expected", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType);
            }

            loadWithType(FileLoader.LoaderType.CORE, options);
        } else {
            analyticsManager.report("load_success", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType, FirebaseAnalytics.Param.CONTENT, result.loaderType.toString());

            if (currentListener != null) {
                currentListener.onLoadSuccess(result);
            } else {
                logMissingListener();
            }
        }
    }

    @Override
    public void onError(FileLoader.Result result, Throwable error) {
        FileLoader.Options options = result.options;
        crashManager.log(error, options.originalUri);

        if (error instanceof FileLoader.EncryptedDocumentException) {
            analyticsManager.report("load_error_encrypted");

            if (currentListener != null) {
                currentListener.onEncrypted(result);
            } else {
                logMissingListener();
            }

            return;
        }

        if (result.loaderType == FileLoader.LoaderType.CORE) {
            analyticsManager.report("load_odf_error", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType);

            if (wvwareDocLoader.isSupported(options)) {
                loadWithType(FileLoader.LoaderType.WVWARE, options);
            } else if (rawLoader.isSupported(options)) {
                loadWithType(FileLoader.LoaderType.RAW, options);
            } else {
                if (currentListener != null) {
                    currentListener.onUnsupported(result);
                } else {
                    logMissingListener();
                }
            }

            return;
        } else if (result.loaderType != FileLoader.LoaderType.METADATA) {
            if (currentListener != null) {
                currentListener.onError(result, error);
            } else {
                logMissingListener();
            }

            return;
        }

        // MetadataLoader failed, so there's no point in trying to parse or upload the file

        analyticsManager.report("load_error", FirebaseAnalytics.Param.CONTENT_TYPE, options.fileType, FirebaseAnalytics.Param.CONTENT, result.loaderType.toString());

        if (currentListener != null) {
            currentListener.onError(result, error);
        } else {
            logMissingListener();
        }
    }

    public boolean isOnlineSupported(FileLoader.Options options) {
        return onlineLoader.isSupported(options);
    }

    public void saveAsync(FileLoader.Result lastResult, Uri outFile, String htmlDiff) {
        backgroundHandler.post(() -> saveSync(lastResult, outFile, htmlDiff));
    }

    private void saveSync(FileLoader.Result lastResult, Uri outFile, String htmlDiff) {
        try {
            File fileToSave;
            if (htmlDiff != null) {
                fileToSave = coreLoader.retranslate(lastResult.options, htmlDiff);
                if (fileToSave == null) {
                    throw new RuntimeException("retranslate failed");
                }
            } else {
                // "full save" from the main UI
                fileToSave = AndroidFileCache.getCacheFile(this, lastResult.options.cacheUri);
            }

            OutputStream outputStream = getContentResolver().openOutputStream(outFile);
            StreamUtil.copy(fileToSave, outputStream);
            outputStream.close();

            if (htmlDiff != null) {
                fileToSave.delete();
            }

            mainHandler.post(() -> {
                if (currentListener != null) {
                    currentListener.onSaveSuccess(outFile);
                } else {
                    logMissingListener();
                }
            });
        } catch (Throwable e) {
            analyticsManager.report("save_error", FirebaseAnalytics.Param.CONTENT_TYPE, lastResult.options.fileType);
            crashManager.log(e, lastResult.options.originalUri);

            if (currentListener != null) {
                currentListener.onSaveError();
            } else {
                logMissingListener();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (metadataLoader != null) {
            metadataLoader.close();
        }

        if (coreLoader != null) {
            coreLoader.close();
        }

        if (wvwareDocLoader != null) {
            wvwareDocLoader.close();
        }

        if (rawLoader != null) {
            rawLoader.close();
        }

        if (onlineLoader != null) {
            onlineLoader.close();
        }

        backgroundThread.quit();

        super.onDestroy();
    }

    public class LoaderBinder extends Binder {
        public LoaderService getService() {
            return LoaderService.this;
        }
    }

    public interface LoaderListener {
        void onLoadSuccess(FileLoader.Result result);
        void onSaveSuccess(Uri outFile);

        void onError(FileLoader.Result result, Throwable error);
        void onEncrypted(FileLoader.Result result);
        void onUnsupported(FileLoader.Result result);
        void onSaveError();
    }
}