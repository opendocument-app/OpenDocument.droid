package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import java.util.LinkedList;
import java.util.List;

public abstract class FileLoader {

    enum LoaderType {
        ODF,
        PDF,
        FIREBASE,
        SAVE,
        RAW,
        METADATA
    }

    Context context;

    Handler backgroundHandler;
    Handler mainHandler;

    FileLoaderListener listener;

    boolean initialized;
    boolean loading;

    public FileLoader(Context context) {
        this.context = context;
    }

    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler) {
        this.listener = listener;
        this.mainHandler = mainHandler;
        this.backgroundHandler = backgroundHandler;

        initialized = true;
    }

    public void loadAsync(Options options) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                loadSync(options);
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

    public class Options {
        Uri originalUri;
        Uri cacheUri;

        String filename;
        String fileType;

        String password;

        boolean limit;
        boolean translatable;
    }

    public class Result {
        LoaderType loaderType;
        Options options;

        List<String> partTitles = new LinkedList<>();
        List<Uri> partUris = new LinkedList<>();
    }

    public interface FileLoaderListener {

        public void onSuccess(Result result);

        public void onError(Result result, Throwable throwable);
    }
}
