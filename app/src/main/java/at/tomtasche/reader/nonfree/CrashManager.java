package at.tomtasche.reader.nonfree;

import android.net.Uri;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.concurrent.TimeoutException;

public class CrashManager {

    private boolean enabled;

    private FirebaseCrashlytics crashlytics;

    public void initialize() {
        if (!enabled) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);

            return;
        }

        crashlytics = FirebaseCrashlytics.getInstance();

        // mitigate TimeoutException on finalize
        // https://stackoverflow.com/a/55999687/198996
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (t.getName().equals("FinalizerWatchdogDaemon") && e instanceof TimeoutException) {
                    log(e);
                } else {
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

        crashlytics.log(message);
    }

    public void log(Throwable error, Uri uri) {
        if (!enabled) {
            return;
        }

        String uriString = "null";
        if (uri != null) {
            uriString = uri.toString();
        }

        crashlytics.log("could not load document at: " + uriString);
        log(error);
    }

    public void log(Throwable error) {
        error.printStackTrace();

        if (!enabled) {
            return;
        }

        crashlytics.recordException(error);
    }
}
