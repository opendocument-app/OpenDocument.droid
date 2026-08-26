package app.opendocument.droid.background

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether the user has earned being asked for a review, and whether we have already asked.
 *
 * Only documents count. Opening the app and putting it down again says nothing about the app, and
 * counting it would only bring the ask forward for the people who do read something.
 *
 * A counter rather than the length of the recently opened list: that list is capped, pruned and
 * deletable, so it undercounts exactly the returning users this is meant to find.
 */
object ReviewInvitation {

    /**
     * Documents to read before each ask, counted from the one before it. Escalating, because
     * someone who did not answer the first ask has to have come a good deal further before it is
     * worth spending another interruption on them - and after the fifth they have answered.
     */
    private val DOCUMENTS_BEFORE_ASK = intArrayOf(5, 10, 20, 50, 100)

    /**
     * The rail under [DOCUMENTS_BEFORE_ASK]: a folder walked through in one afternoon crosses two
     * of those, and one sitting must never carry two asks.
     */
    private const val DAYS_BETWEEN_ASKS = 30

    private const val KEY_DOCUMENT_OPENS = "usage_document_opens"
    private const val KEY_ASKS = "usage_review_asks"
    private const val KEY_ASKED_AT = "usage_review_asked_at"
    private const val KEY_ASKED_AFTER = "usage_review_asked_after"

    fun recordDocumentOpen(context: Context) {
        val preferences = preferences(context)

        // apply, not commit: nothing reads this back synchronously
        preferences
            .edit()
            .putInt(KEY_DOCUMENT_OPENS, preferences.getInt(KEY_DOCUMENT_OPENS, 0) + 1)
            .apply()
    }

    /** Only ever asked at a moment where the user is waiting for nothing - see `MainActivity`. */
    fun isEarned(context: Context): Boolean {
        val preferences = preferences(context)

        val asks = preferences.getInt(KEY_ASKS, 0)
        if (asks >= DOCUMENTS_BEFORE_ASK.size) {
            return false
        }

        val documents =
            preferences.getInt(KEY_DOCUMENT_OPENS, 0) - preferences.getInt(KEY_ASKED_AFTER, 0)
        if (documents < DOCUMENTS_BEFORE_ASK[asks]) {
            return false
        }

        val askedAt = preferences.getLong(KEY_ASKED_AT, 0)
        if (askedAt == 0L) {
            return true
        }

        // a clock moved backwards reads as no time passed, which only delays the ask
        return System.currentTimeMillis() - askedAt >= DAYS_BETWEEN_ASKS * 24L * 60 * 60 * 1000
    }

    /**
     * Written when the sheet is handed to play, not when it comes back: play may swallow it under a
     * quota it does not report, and spending an ask on a sheet nobody saw is the cheaper mistake.
     * It also survives the process dying while the sheet is up.
     */
    fun recordAsk(context: Context) {
        val preferences = preferences(context)

        preferences
            .edit()
            .putInt(KEY_ASKS, preferences.getInt(KEY_ASKS, 0) + 1)
            .putLong(KEY_ASKED_AT, System.currentTimeMillis())
            .putInt(KEY_ASKED_AFTER, preferences.getInt(KEY_DOCUMENT_OPENS, 0))
            .apply()
    }

    /** The same default preference file [CatchAllSetting] uses. */
    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
