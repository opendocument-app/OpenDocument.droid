package app.opendocument.droid.background

import android.content.Context
import android.content.SharedPreferences

/**
 * How often the user opened the app from the launcher, and how many documents they opened.
 *
 * A counter rather than the length of the recently opened list: that list is capped, pruned and
 * deletable, so it undercounts exactly the returning users this is meant to find.
 */
object UsageCounters {

    private const val KEY_APP_OPENS = "usage_app_opens"
    private const val KEY_DOCUMENT_OPENS = "usage_document_opens"

    fun recordAppOpen(context: Context): Int = increment(context, KEY_APP_OPENS)

    fun recordDocumentOpen(context: Context): Int = increment(context, KEY_DOCUMENT_OPENS)

    fun appOpens(context: Context): Int = preferences(context).getInt(KEY_APP_OPENS, 0)

    fun documentOpens(context: Context): Int = preferences(context).getInt(KEY_DOCUMENT_OPENS, 0)

    private fun increment(context: Context, key: String): Int {
        val preferences = preferences(context)
        val next = preferences.getInt(key, 0) + 1

        // apply, not commit: nothing reads this back synchronously
        preferences.edit().putInt(key, next).apply()

        return next
    }

    /** The same default preference file [CatchAllSetting] uses. */
    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
