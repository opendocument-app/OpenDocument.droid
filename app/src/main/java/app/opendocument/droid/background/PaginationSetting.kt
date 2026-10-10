package app.opendocument.droid.background

import android.content.Context
import app.opendocument.core.DocumentType
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * Controls odrcore textDocumentMargin. Changes require re-rendering; the document remains
 * continuous.
 */
object PaginationSetting {

    private const val PREF_PAGINATION_ENABLED = "pagination_enabled"

    /** On unless the user says otherwise: the margins are the document as it was written. */
    const val DEFAULT_ENABLED: Boolean = true

    fun isEnabled(context: Context): Boolean =
        AppPreferences.of(context).getBoolean(PREF_PAGINATION_ENABLED, DEFAULT_ENABLED)

    /** Whether [mimeType] is a text document whose layout supports side margins. */
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
