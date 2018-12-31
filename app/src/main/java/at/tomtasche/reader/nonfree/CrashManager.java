package at.tomtasche.reader.nonfree;

import android.net.Uri;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

public class CrashManager {

    private boolean enabled;

    public void initialize() {
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(Throwable error, Uri uri) {
        if (!enabled) {
            return;
        }

        Crashlytics.log(Log.ERROR, "MainActivity", "could not load document at: " + uri.toString());
        log(error);
    }

    public void log(Throwable error) {
        error.printStackTrace();

        if (!enabled) {
            return;
        }

        Crashlytics.logException(error);
    }
}
