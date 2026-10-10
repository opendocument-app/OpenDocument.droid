package app.opendocument.droid.background

import android.content.Context

/**
 * Schedules review invitations from cumulative document opens, independent of the capped recent
 * list.
 */
object ReviewInvitation {

    /** Documents to read before each ask, counted from the one before it. */
    private const val DOCUMENTS_BEFORE_ASK = 3

    /**
     * The rail between asks. There is no last ask: play's own quota decides whether the sheet
     * shows, and an ask it swallows still counts here.
     */
    private const val DAYS_BETWEEN_ASKS = 14

    private const val KEY_DOCUMENT_OPENS = "usage_document_opens"
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
            askedAfterOpens = preferences.getInt(KEY_ASKED_AFTER, 0),
            askedAtMillis = preferences.getLong(KEY_ASKED_AT, 0),
            nowMillis = System.currentTimeMillis(),
        )
    }

    /** The decision alone, so the jvm test can reach every branch. */
    internal fun isEarned(
        documentOpens: Int,
        askedAfterOpens: Int,
        askedAtMillis: Long,
        nowMillis: Long,
    ): Boolean {
        if (documentOpens - askedAfterOpens < DOCUMENTS_BEFORE_ASK) {
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
            .putLong(KEY_ASKED_AT, System.currentTimeMillis())
            .putInt(KEY_ASKED_AFTER, preferences.getInt(KEY_DOCUMENT_OPENS, 0))
            .apply()
    }

    /** Read back only by the instrumented test. */
    internal fun documentOpens(context: Context): Int =
        AppPreferences.of(context).getInt(KEY_DOCUMENT_OPENS, 0)
}
