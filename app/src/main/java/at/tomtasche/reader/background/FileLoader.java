package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.perf.metrics.Trace;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;

public abstract class FileLoader {

    public enum LoaderType {
        ODF,
        DOC,
        OOXML,
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

    public File retranslate(String htmlDiff) {
        throw new RuntimeException("not implemented");
    }

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
        crashManager.log(result.loaderType.name() + " failed");
        crashManager.log(t);

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

    public static class Options implements Parcelable {

        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {

            public Options createFromParcel(Parcel in) {
                return new Options(in);
            }

            public Options[] newArray(int size) {
                return new Options[size];
            }
        };

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

        public Options() {
        }

        public Options(Parcel parcel) {
            originalUri = parcel.readParcelable(null);
            cacheUri = parcel.readParcelable(null);
            persistentUri = ParcelUtil.readBoolean(parcel);
            fileExists = ParcelUtil.readBoolean(parcel);
            filename = parcel.readString();
            fileType = parcel.readString();
            fileExtension = parcel.readString();
            password = parcel.readString();
            limit = ParcelUtil.readBoolean(parcel);
            translatable = ParcelUtil.readBoolean(parcel);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeParcelable(originalUri, 0);
            parcel.writeParcelable(cacheUri, 0);
            ParcelUtil.writeBoolean(parcel, persistentUri);
            ParcelUtil.writeBoolean(parcel, fileExists);
            parcel.writeString(filename);
            parcel.writeString(fileType);
            parcel.writeString(fileExtension);
            parcel.writeString(password);
            ParcelUtil.writeBoolean(parcel, limit);
            ParcelUtil.writeBoolean(parcel, translatable);
        }
    }

    public static class Result implements Parcelable {

        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {

            public Result createFromParcel(Parcel in) {
                return new Result(in);
            }

            public Result[] newArray(int size) {
                return new Result[size];
            }
        };

        public LoaderType loaderType;
        public Options options;

        public List<String> partTitles = new LinkedList<>();
        public List<Uri> partUris = new LinkedList<>();

        public Result() {
        }

        public Result(Parcel parcel) {
            loaderType = LoaderType.valueOf(parcel.readString());
            options = parcel.readParcelable(getClass().getClassLoader());
            parcel.readList(partTitles, null);
            parcel.readList(partUris, null);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(loaderType.name());
            parcel.writeParcelable(options, 0);
            parcel.writeList(partTitles);
            parcel.writeList(partUris);
        }
    }

    public interface FileLoaderListener {

        public void onSuccess(Result result);

        public void onError(Result result, Throwable throwable);
    }

    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {
    }
}
