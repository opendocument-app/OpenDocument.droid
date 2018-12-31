package at.tomtasche.reader.background;

import android.net.Uri;

public interface FileLoader {

    public void initialize(FileLoaderListener listener);

    public void loadAsync(Uri uri, String password, boolean limit, boolean translatable);

    public void loadSync(Uri uri, String password, boolean limit, boolean translatable);

    public boolean isLoading();

    public double getProgress();

    public void close();


    public interface FileLoaderListener {

        public void onSuccess(Document document);

        public void onError(Throwable throwable);
    }
}
