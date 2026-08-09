package app.opendocument.droid.nonfree

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeoutException

/**
 * Reporting a crash has been local logging only since the crashlytics integration was removed; the
 * call sites are kept so a reporting backend can be wired back in here.
 */
class CrashManager {

    fun initialize() {
        // mitigate TimeoutException on finalize
        // https://stackoverflow.com/a/55999687/198996
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread.name == "FinalizerWatchdogDaemon" && error is TimeoutException) {
                log(error)
            } else {
                defaultUncaughtExceptionHandler?.uncaughtException(thread, error)
            }
        }
    }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun log(error: Throwable, uri: Uri?) {
        Log.d(TAG, "could not load document at: " + (uri?.toString() ?: "null"))
        log(error)
    }

    fun log(error: Throwable) {
        Log.e(TAG, "Error reported", error)
    }

    private companion object {
        const val TAG = "ODR"
    }
}
