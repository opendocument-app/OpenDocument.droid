package app.opendocument.droid.nonfree

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeoutException

/**
 * Reporting a crash has been local logging only since the crashlytics integration was removed; the
 * call sites are kept so a reporting backend can be wired back in here.
 */
class CrashManager {

    fun initialize() = installHandler()

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
        private var handlerInstalled = false

        @Synchronized
        fun installHandler() {
            if (handlerInstalled) return

            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                if (thread.name == "FinalizerWatchdogDaemon" && error is TimeoutException) {
                    Log.e(TAG, "Error reported", error)
                } else {
                    previous?.uncaughtException(thread, error)
                }
            }
            handlerInstalled = true
        }
    }
}
