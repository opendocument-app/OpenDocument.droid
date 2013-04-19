package at.tomtasche.reader.background;

import android.net.Uri;

public interface FileLoader {

	public Throwable getLastError();

	public Uri getLastUri();

	public double getProgress();
}
