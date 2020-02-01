package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.perf.metrics.Trace;

import java.util.LinkedList;
import java.util.List;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;

public abstract class FileLoader {

    public enum LoaderType {
        ODF,
        DOC,
        PDF,
        ONLINE,
        RAW,
        METADATA
    }

    Context context;
    LoaderType type;

    Handler backgroundHandler;
    Handler mainHandler;

    FileLoaderListener listener;

    AnalyticsManager analyticsManager;
    CrashManager crashManager;

    private boolean initialized;
    private boolean loading;

    public FileLoader(Context context, LoaderType type) {
        this.context = context;
        this.type = type;
    }

    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler, AnalyticsManager analyticsManager, CrashManager crashManager) {
        this.listener = listener;
        this.mainHandler = mainHandler;
        this.backgroundHandler = backgroundHandler;
        this.analyticsManager = analyticsManager;
        this.crashManager = crashManager;

        initialized = true;
    }

    public abstract boolean isSupported(Options options);

    public void loadAsync(Options options) {
        if (!initialized) {
            throw new RuntimeException("not initialized");
        }

        loading = true;

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Trace trace = analyticsManager.startTrace("sync_" + type.toString());

                loadSync(options);

                analyticsManager.stopTrace(trace);

                loading = false;
            }
        });
    }

    abstract void loadSync(Options options);

    public boolean isLoading() {
        return loading;
    }

    void callOnSuccess(Result result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                analyticsManager.report("loader_success_" + type, FirebaseAnalytics.Param.CONTENT_TYPE, result.options.fileType, FirebaseAnalytics.Param.CONTENT, result.options.fileExtension);

                FileLoaderListener strongReferenceListener = listener;
                if (strongReferenceListener != null) {
                    listener.onSuccess(result);
                }
            }
        });
    }

    void callOnError(Result result, Throwable t) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                analyticsManager.report("loader_error_" + type, FirebaseAnalytics.Param.CONTENT_TYPE, result.options.fileType, FirebaseAnalytics.Param.CONTENT, result.options.fileExtension);

                FileLoaderListener strongReferenceListener = listener;
                if (strongReferenceListener != null) {
                    listener.onError(result, t);
                }
            }
        });
    }

    public void close() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initialized = false;
                listener = null;
                context = null;
            }
        });
    }

    public static class Options {
        public Uri originalUri;
        public Uri cacheUri;
        public boolean persistentUri;

        public boolean fileExists;
        public String filename;
        public String fileType;
        public String fileExtension;

        public String password;

        public boolean limit;
        public boolean translatable;
    }

    public class Result {
        public LoaderType loaderType;
        public Options options;

        public List<String> partTitles = new LinkedList<>();
        public List<Uri> partUris = new LinkedList<>();
    }

    public interface FileLoaderListener {

        public void onSuccess(Result result);

        public void onError(Result result, Throwable throwable);
    }

    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {
    }
}
