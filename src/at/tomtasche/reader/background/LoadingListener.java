package at.tomtasche.reader.background;

import android.net.Uri;

public interface LoadingListener {

	public void onError(Throwable error, Uri uri);
	
	public void onSuccess(Uri uri);
}
