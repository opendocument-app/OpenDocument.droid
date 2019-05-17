package at.tomtasche.readerpdf.background;

import android.net.Uri;

public interface FileLoader {

    public void initialize(FileLoaderListener listener);

    public void loadAsync(Uri uri, String fileType, String password, boolean limit, boolean translatable);

    public void loadSync(Uri uri, String fileType, String password, boolean limit, boolean translatable);

    public boolean isLoading();

    public double getProgress();

    public void close();


    public interface FileLoaderListener {

        public void onSuccess(Document document, String fileType);

        public void onError(Throwable throwable, String fileType);
    }
}
