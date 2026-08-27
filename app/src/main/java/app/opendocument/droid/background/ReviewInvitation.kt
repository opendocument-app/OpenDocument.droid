package app.opendocument.droid.background

import android.content.Context

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
    private const val DAYS_BETWEEN_ASKS = 14

    private const val KEY_DOCUMENT_OPENS = "usage_document_opens"
    private const val KEY_ASKS = "usage_review_asks"
    private const val KEY_ASKED_AT = "usage_review_asked_at"
    private const val KEY_ASKED_AFTER = "usage_review_asked_after"

    fun recordDocumentOpen(context: Context) {
        val preferences = AppPreferences.of(context)

        // apply, not commit: nothing reads this back synchronously
        preferences
            .edit()
            .putInt(KEY_DOCUMENT_OPENS, preferences.getInt(KEY_DOCUMENT_OPENS, 0) + 1)
            .apply()
    }

    fun isEarned(context: Context): Boolean {
        val preferences = AppPreferences.of(context)

        return isEarned(
            documentOpens = preferences.getInt(KEY_DOCUMENT_OPENS, 0),
            asks = preferences.getInt(KEY_ASKS, 0),
            askedAfterOpens = preferences.getInt(KEY_ASKED_AFTER, 0),
            askedAtMillis = preferences.getLong(KEY_ASKED_AT, 0),
            nowMillis = System.currentTimeMillis(),
        )
    }

    /** The decision alone, so the jvm test can reach every branch. */
    internal fun isEarned(
        documentOpens: Int,
        asks: Int,
        askedAfterOpens: Int,
        askedAtMillis: Long,
        nowMillis: Long,
    ): Boolean {
        if (asks >= DOCUMENTS_BEFORE_ASK.size) {
            return false
        }

        if (documentOpens - askedAfterOpens < DOCUMENTS_BEFORE_ASK[asks]) {
            return false
        }

        if (askedAtMillis == 0L) {
            return true
        }

        // a clock moved backwards reads as no time passed, which only delays the ask
        return nowMillis - askedAtMillis >= DAYS_BETWEEN_ASKS * 24L * 60 * 60 * 1000
    }

    /**
     * Written when the sheet is handed to play, not when it comes back: play may swallow it under a
     * quota it does not report, and spending an ask on a sheet nobody saw is the cheaper mistake.
     * It also survives the process dying while the sheet is up.
     */
    fun recordAsk(context: Context) {
        val preferences = AppPreferences.of(context)

        preferences
            .edit()
            .putInt(KEY_ASKS, preferences.getInt(KEY_ASKS, 0) + 1)
            .putLong(KEY_ASKED_AT, System.currentTimeMillis())
            .putInt(KEY_ASKED_AFTER, preferences.getInt(KEY_DOCUMENT_OPENS, 0))
            .apply()
    }

    /** Read back only by the instrumented test. */
    internal fun documentOpens(context: Context): Int =
        AppPreferences.of(context).getInt(KEY_DOCUMENT_OPENS, 0)
}
