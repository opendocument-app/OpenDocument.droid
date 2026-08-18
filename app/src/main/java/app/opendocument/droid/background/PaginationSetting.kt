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
 * rendering it again. The landing screen's switch gets that for free - it is a document closed
 * away, and opening one translates anyway - and the button over the open document asks
 * `DocumentFragment.reloadForMargins` for it.
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
     * odrcore lays a *text* document out with the margins or without them and nothing else: a
     * presentation and a drawing are paged whatever it says, and a spreadsheet, a pdf, an image or
     * a plain text file are never paged. Offering the button on one of those would render the
     * document again to show nothing new, and quietly answer for the next text document opened.
     */
    fun affects(mimeType: String?): Boolean {
        // not lowercased, and for the same reason as DocumentDarkening.kindOf
        val fileType = mimeType?.let { Odr.fileTypeByMimetype(it) } ?: return false

        // four types call themselves text: odt, docx, doc - and pdf, which is fixed pages the core
        // lays out with a frontend of its own that the margin never reaches. So it is asked about
        // the type rather than trusted here
        if (fileType == FileType.PORTABLE_DOCUMENT_FORMAT) {
            return false
        }

        return Odr.documentTypeByFileType(fileType) == DocumentType.TEXT
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPreferences.of(context).edit().putBoolean(PREF_PAGINATION_ENABLED, enabled).apply()
    }
}
