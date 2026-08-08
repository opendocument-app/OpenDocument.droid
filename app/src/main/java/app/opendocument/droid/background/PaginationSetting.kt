package app.opendocument.droid.background

import android.content.Context

/**
 * Whether a document keeps the side margins of a printed page, or fills the screen.
 *
 * This is odrcore's `textDocumentMargin`, which is only the left and right of a page - the document
 * stays one continuous sheet, it does not break into pages. It used to be the "use_paging" remote
 * config key, which resolved to false for every user once firebase remote config was removed, and
 * is the user's answer now: the margins are what the document was written to look like, the full
 * width is what reads on a phone.
 *
 * Only [CoreLoader] reads it, and only while translating, so a change reaches a document the next
 * time it is opened. That is enough: the switch is on the landing screen, which is only reached by
 * closing whatever was open, and reopening it translates again.
 */
object PaginationSetting {

    private const val PREF_PAGINATION_ENABLED = "pagination_enabled"

    /** On unless the user says otherwise: the margins are the document as it was written. */
    const val DEFAULT_ENABLED: Boolean = true

    fun isEnabled(context: Context): Boolean =
        AppPreferences.of(context).getBoolean(PREF_PAGINATION_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.of(context).edit().putBoolean(PREF_PAGINATION_ENABLED, enabled).apply()
    }
}
