package at.tomtasche.reader.background;

import android.net.Uri;

public interface LoadingListener {

	public void onError(Throwable error, Uri uri);

	public void onSuccess(Document document, Uri uri);
}
