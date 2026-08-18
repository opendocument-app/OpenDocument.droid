package app.opendocument.droid.background

import android.content.Context
import app.opendocument.core.DocumentType
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * Whether a document keeps the side margins of a printed page, or fills the screen.
 *
 * This is odrcore's `textDocumentMargin`, which is only the left and right of a page - the document
 * stays one continuous sheet, it does not break into pages. It used to be the "use_paging" remote
 * config key, which resolved to false for every user once firebase remote config was removed, and
 * is the user's answer now: the margins are what the document was written to look like, the full
 * width is what reads on a phone.
 *
 * Only [CoreLoader] reads it, and only while translating, so a change reaches a document by
 * rendering it again: opening one from the landing screen does that anyway, and the button over an
 * open document asks `DocumentFragment.reloadForMargins` for it.
 */
object PaginationSetting {

    private const val PREF_PAGINATION_ENABLED = "pagination_enabled"

    /** On unless the user says otherwise: the margins are the document as it was written. */
    const val DEFAULT_ENABLED: Boolean = true

    fun isEnabled(context: Context): Boolean =
        AppPreferences.of(context).getBoolean(PREF_PAGINATION_ENABLED, DEFAULT_ENABLED)

    /**
     * Whether this reaches what [mimeType] names at all.
     *
     * odrcore lays a *text* document out with the margins or without them and nothing else, so
     * anywhere else the button would render the document again to show nothing new - and quietly
     * answer for the next text document opened.
     */
    fun affects(mimeType: String?): Boolean {
        // not lowercased, and for the same reason as DocumentDarkening.fileTypeOf
        val fileType = mimeType?.let { Odr.fileTypeByMimetype(it) } ?: return false

        // pdf calls itself text too, but is fixed pages laid out by a frontend of its own that the
        // margin never reaches
        if (fileType == FileType.PORTABLE_DOCUMENT_FORMAT) {
            return false
        }

        return Odr.documentTypeByFileType(fileType) == DocumentType.TEXT
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.of(context).edit().putBoolean(PREF_PAGINATION_ENABLED, enabled).apply()
    }
}
