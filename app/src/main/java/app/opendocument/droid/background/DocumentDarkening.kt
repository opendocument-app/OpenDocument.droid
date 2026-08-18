package app.opendocument.droid.background

import android.content.Context
import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * Whether a document follows the app into night mode, which is not one answer for everything the
 * app opens: a text document reads dark, a scanned page inverted is something nobody wrote.
 *
 * The core answers it now - see [darkensByDefault] - and the button over the document is what
 * overrides that. There is no switch for this on the landing screen. What is remembered is the
 * [Kind] rather than the file: it is never *this* pdf that inverts badly, it is pdfs.
 */
object DocumentDarkening {

    /**
     * What an override is remembered for, each named the way the button over the document says it.
     */
    enum class Kind {

        /** Text, spreadsheets, plain text, and everything else the core reflows into html. */
        DOCUMENT,

        /**
         * Fixed pages, scans included, which is where the app's rendering is at its most literal.
         */
        PDF,

        /** A photograph, which the core darkens by putting a dark ground under rather than into. */
        IMAGE,
    }

    fun kindOf(mimeType: String?): Kind = kindOf(fileTypeOf(mimeType))

    private fun kindOf(fileType: FileType?): Kind =
        when {
            fileType == null -> Kind.DOCUMENT
            fileType == FileType.PORTABLE_DOCUMENT_FORMAT -> Kind.PDF
            Odr.fileCategoryByFileType(fileType) == FileCategory.IMAGE -> Kind.IMAGE
            else -> Kind.DOCUMENT
        }

    private fun fileTypeOf(mimeType: String?): FileType? =
        // not lowercased: the core's table is matched exactly and spells some entries with
        // capitals ("macroEnabled"). canonicalMimeType has already been applied upstream
        mimeType?.let { Odr.fileTypeByMimetype(it) }

    /**
     * Whether the core renders this type dark itself, which is what darkening defaults to.
     *
     * Where it does, the dark is the one the format was translated into - the colours the file
     * authored giving way, a photograph keeping its own and taking a dark ground instead. Where it
     * does not, a pdf and the media views, all that is left is the webview inverting what it was
     * handed, so that is offered but not taken for granted.
     */
    private fun darkensByDefault(fileType: FileType?): Boolean =
        fileType != null && Odr.capabilitiesByFileType(fileType).colorScheme

    /** Whether what [mimeType] names darkens, the button's answer first and the core's after. */
    fun isAllowed(context: Context, mimeType: String?): Boolean {
        val fileType = fileTypeOf(mimeType)

        return AppPreferences.of(context)
            .getBoolean(prefKey(kindOf(fileType)), darkensByDefault(fileType))
    }

    fun setAllowed(context: Context, kind: Kind, allowed: Boolean) {
        AppPreferences.of(context).edit().putBoolean(prefKey(kind), allowed).apply()
    }

    /** Forgets the override, which leaves the core answering for the kind again. */
    fun clear(context: Context, kind: Kind) {
        AppPreferences.of(context).edit().remove(prefKey(kind)).apply()
    }

    private fun prefKey(kind: Kind) = PREF_PREFIX + kind.name.lowercase()

    private const val PREF_PREFIX = "darken_"
}
