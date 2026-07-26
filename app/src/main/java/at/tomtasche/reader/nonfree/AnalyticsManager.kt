package at.tomtasche.reader.nonfree

import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Reporting has been local logging only since the firebase analytics integration was removed; the
 * call sites are kept so an analytics backend can be wired back in here.
 */
class AnalyticsManager {

    private var enabled = false

    fun initialize(context: Context) {
        // nothing to set up while reporting is local only
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @JvmOverloads
    fun report(
        event: String,
        key1: String? = null,
        value1: Any? = null,
        key2: String? = null,
        value2: Any? = null,
    ) {
        if (!enabled) {
            return
        }

        Log.i(
            TAG,
            buildString {
                append(event)
                if (key1 != null) append(" $key1=$value1")
                if (key2 != null) append(" $key2=$value2")
            },
        )
    }

    fun setCurrentScreen(activity: Activity, name: String) {
        if (!enabled) {
            return
        }

        Log.i(TAG, "screen $name (${activity.javaClass.simpleName})")
    }

    private companion object {
        const val TAG = "smn"
    }
}
