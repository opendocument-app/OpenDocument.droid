package app.opendocument.droid.nonfree

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeoutException

class CrashManager {

    private var enabled = false

    fun initialize() {
        if (!enabled) {
            return
        }

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

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun log(message: String) {
        if (!enabled) {
            return
        }

        Log.d(TAG, message)
    }

    fun log(error: Throwable, uri: Uri?) {
        if (!enabled) {
            return
        }

        Log.d(TAG, "could not load document at: " + (uri?.toString() ?: "null"))
        log(error)
    }

    fun log(error: Throwable) {
        error.printStackTrace()

        if (!enabled) {
            return
        }

        Log.e(TAG, "Error reported", error)
    }

    private companion object {
        const val TAG = "ODR"
    }
}
