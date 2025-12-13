package at.tomtasche.reader.nonfree;

import android.net.Uri;
import android.util.Log;

import java.util.concurrent.TimeoutException;

public class CrashManager {

    private boolean enabled;

    public void initialize() {
        if (!enabled) {
            return;
        }

        // mitigate TimeoutException on finalize
        // https://stackoverflow.com/a/55999687/198996
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (t.getName().equals("FinalizerWatchdogDaemon") && e instanceof TimeoutException) {
                    log(e);
                } else if (defaultUncaughtExceptionHandler != null) {
                    defaultUncaughtExceptionHandler.uncaughtException(t, e);
                }
            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String message) {
        if (!enabled) {
            return;
        }

        Log.d("ODR", message);
    }

    public void log(Throwable error, Uri uri) {
        if (!enabled) {
            return;
        }

        String uriString = uri != null ? uri.toString() : "null";

        Log.d("ODR", "could not load document at: " + uriString);
        log(error);
    }

    public void log(Throwable error) {
        error.printStackTrace();

        if (!enabled) {
            return;
        }

        Log.e("ODR", "Error reported", error);
    }
}
