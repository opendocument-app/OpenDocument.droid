package app.opendocument.droid.background

import android.content.Context
import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * Whether a document follows the app into night mode, which is not one answer for everything the
 * app opens: a text document inverts into something that still reads, a scanned page or a photo
 * into something nobody wrote.
 *
 * So the answer is per [Kind], and the button over the document is what edits it - there is no
 * switch for this on the landing screen. The kind is what is remembered rather than the file: it is
 * never *this* pdf that inverts badly, it is pdfs.
 */
object DocumentDarkening {

    /**
     * What the answer differs for, each named the way the button over the document says it.
     *
     * Presentations and drawings are [DOCUMENT] until someone has looked at enough of them to say
     * otherwise - a designed page may well invert as badly as a pdf does, but that is a guess, and
     * the two below are not.
     */
    enum class Kind(val darkensByDefault: Boolean) {

        /** Text, spreadsheets, plain text, and everything else the core reflows into html. */
        DOCUMENT(true),

        /**
         * Fixed pages, scans included, which is where the app's rendering is at its most literal.
         */
        PDF(false),

        /** A photograph inverted is a photograph of nothing. */
        IMAGE(false),
    }

    fun kindOf(mimeType: String?): Kind {
        // not lowercased: the core's table is matched exactly and spells some entries with
        // capitals ("macroEnabled"). canonicalMimeType has already been applied upstream
        val fileType = mimeType?.let { Odr.fileTypeByMimetype(it) } ?: return Kind.DOCUMENT

        return when {
            fileType == FileType.PORTABLE_DOCUMENT_FORMAT -> Kind.PDF
            Odr.fileCategoryByFileType(fileType) == FileCategory.IMAGE -> Kind.IMAGE
            else -> Kind.DOCUMENT
        }
    }

    /** Whether what [mimeType] names darkens, which is the default until the button says else. */
    fun isAllowed(context: Context, mimeType: String?): Boolean =
        isAllowed(context, kindOf(mimeType))

    fun isAllowed(context: Context, kind: Kind): Boolean =
        AppPreferences.of(context).getBoolean(prefKey(kind), kind.darkensByDefault)

    fun setAllowed(context: Context, kind: Kind, allowed: Boolean) {
        AppPreferences.of(context).edit().putBoolean(prefKey(kind), allowed).apply()
    }

    private fun prefKey(kind: Kind) = PREF_PREFIX + kind.name.lowercase()

    private const val PREF_PREFIX = "darken_"
}
