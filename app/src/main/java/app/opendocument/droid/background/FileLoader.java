package app.opendocument.droid.background;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import app.opendocument.droid.nonfree.AnalyticsConstants;
import app.opendocument.droid.nonfree.AnalyticsManager;
import app.opendocument.droid.nonfree.CrashManager;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

public abstract class FileLoader {

    public enum LoaderType {
        CORE,
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

    public void initialize(
            FileLoaderListener listener,
            Handler mainHandler,
            Handler backgroundHandler,
            AnalyticsManager analyticsManager,
            CrashManager crashManager) {
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

        backgroundHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        loadSync(options);

                        loading = false;
                    }
                });
    }

    abstract void loadSync(Options options);

    public File retranslate(Options options, String htmlDiff) {
        throw new RuntimeException("not implemented");
    }

    public boolean isLoading() {
        return loading;
    }

    void callOnSuccess(Result result) {
        mainHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        analyticsManager.report(
                                "loader_success_" + type,
                                AnalyticsConstants.PARAM_CONTENT_TYPE,
                                result.options.fileType,
                                AnalyticsConstants.PARAM_CONTENT,
                                result.options.fileExtension);

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

        mainHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        analyticsManager.report(
                                "loader_error_" + type,
                                AnalyticsConstants.PARAM_CONTENT_TYPE,
                                result.options.fileType,
                                AnalyticsConstants.PARAM_CONTENT,
                                result.options.fileExtension);

                        FileLoaderListener strongReferenceListener = listener;
                        if (strongReferenceListener != null) {
                            listener.onError(result, t);
                        }
                    }
                });
    }

    public void close() {
        backgroundHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        initialized = false;
                        listener = null;
                        context = null;
                    }
                });
    }

    public static class Options implements Parcelable {

        public static final Parcelable.Creator<Options> CREATOR =
                new Parcelable.Creator<Options>() {

                    @Override
                    public Options createFromParcel(Parcel in) {
                        return new Options(in);
                    }

                    @Override
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

        public Options() {}

        public Options(Parcel parcel) {
            ClassLoader classLoader = Options.class.getClassLoader();
            originalUri = parcel.readParcelable(classLoader);
            cacheUri = parcel.readParcelable(classLoader);
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

        public static final Parcelable.Creator<Result> CREATOR =
                new Parcelable.Creator<Result>() {

                    @Override
                    public Result createFromParcel(Parcel in) {
                        return new Result(in);
                    }

                    @Override
                    public Result[] newArray(int size) {
                        return new Result[size];
                    }
                };

        public LoaderType loaderType;
        public Options options;

        public List<String> partTitles = new LinkedList<>();
        public List<Uri> partUris = new LinkedList<>();

        public Result() {}

        public Result(Parcel parcel) {
            loaderType = LoaderType.valueOf(parcel.readString());
            ClassLoader classLoader = Result.class.getClassLoader();
            options = parcel.readParcelable(classLoader);
            parcel.readList(partTitles, classLoader);
            parcel.readList(partUris, classLoader);
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

        void onSuccess(Result result);

        void onError(Result result, Throwable throwable);
    }

    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {}
}
