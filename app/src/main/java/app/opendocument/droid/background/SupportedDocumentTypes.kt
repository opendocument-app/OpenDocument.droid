package app.opendocument.droid.background

import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * What the app claims to open and which loader gets it, derived from odrcore's format table rather
 * than kept here - so the app cannot claim a format the core does not have, or miss one it does.
 *
 * Two separate questions: what the app offers itself for ([CLAIMED_FILE_TYPES], mirrored by the
 * STRICT_CATCH `activity-alias`) and what [CoreLoader] renders once it has a file
 * ([CORE_FILE_TYPES]). The second is wider on purpose - the app does not offer for an mp3, but it
 * plays one handed to it. `SupportedFormatsTest` holds the manifest, which cannot read any of this,
 * against the first.
 *
 * All of it is a guess from a name and whatever mime type a provider volunteered. What decides once
 * the file is in the cache is [MetadataLoader], which asks `Odr.mimetype` about the copy, and
 * [CoreLoader.isDocumentEditable], which asks the opened document.
 */
object SupportedDocumentTypes {

    /**
     * What [CoreLoader] renders: everything odrcore turns into html. Far more than documents - csv,
     * images, archives, fonts and media included.
     */
    private val CORE_FILE_TYPES: List<FileType> by lazy {
        Odr.allFileTypes().filter { Odr.capabilitiesByFileType(it).translateHtml }
    }

    /**
     * What the app offers itself for: the core's document formats plus the three non-document ones
     * worth opening a viewer for. Much narrower than [CORE_FILE_TYPES] - see the class doc.
     */
    private val CLAIMED_FILE_TYPES: List<FileType> by lazy {
        val documents =
            Odr.allFileTypes().filter {
                Odr.capabilitiesByFileType(it).translateHtml &&
                    Odr.fileCategoryByFileType(it) == FileCategory.DOCUMENT
            }

        documents + listOf(FileType.TEXT_FILE, FileType.COMMA_SEPARATED_VALUES, FileType.ZIP)
    }

    /**
     * Images, as a prefix rather than a set of file types: the wildcard claims the ones odrcore
     * does not name as well. The manifest declares `image` likewise.
     */
    private val CLAIMED_MIME_PREFIXES = listOf("image/")

    /** Every mime type spelling odrcore accepts for a format [CoreLoader] renders. */
    private val CORE_MIME_TYPES: Set<String> by lazy { mimeTypesOf(CORE_FILE_TYPES) }

    /** The same for everything the app offers itself for. */
    val MIME_TYPES: Set<String> by lazy { mimeTypesOf(CLAIMED_FILE_TYPES) }

    /**
     * The extension fallback, for the `application/octet-stream` providers regularly volunteer and
     * for an `ACTION_VIEW` with no mime type at all. The `pathPattern` filters in
     * AndroidManifest.xml cover that second case and have to list exactly this set.
     */
    val EXTENSIONS: Set<String> by lazy {
        CLAIMED_FILE_TYPES.flatMap { Odr.fileExtensionsByFileType(it) }
            .map { it.lowercase() }
            .toSet()
    }

    /**
     * odrcore's canonical spelling of [mimeType], so one spelling per format flows downstream - the
     * app claims every spelling in the core's table, `application/csv` and `multipart/x-zip`
     * included. Anything the core does not name passes through untouched.
     */
    fun canonicalMimeType(mimeType: String?): String? {
        if (mimeType == null) {
            return null
        }

        val fileType = Odr.fileTypeByMimetype(mimeType) ?: return mimeType
        if (fileType == FileType.UNKNOWN) {
            return mimeType
        }

        return Odr.mimetypeByFileType(fileType) ?: mimeType
    }

    /** Whether [CoreLoader] is expected to render this - see [CORE_FILE_TYPES]. */
    fun isRenderedByCore(mimeType: String?): Boolean =
        mimeType != null && mimeType.lowercase() in CORE_MIME_TYPES

    /**
     * Whether the core files this as a document rather than text, an image, an archive, a font or
     * media.
     */
    fun isDocument(mimeType: String?): Boolean {
        if (mimeType == null) {
            return false
        }

        // not lowercased: the core's table is matched exactly and spells some entries with
        // capitals ("macroEnabled"). canonicalMimeType has already been applied upstream
        val fileType = Odr.fileTypeByMimetype(mimeType) ?: return false

        return Odr.fileCategoryByFileType(fileType) == FileCategory.DOCUMENT
    }

    /** Whether the app should offer itself for this at all. */
    fun isSupported(mimeType: String?, filename: String?): Boolean {
        // a recognised extension has to be able to win on its own, or every octet-stream is lost
        val extension = MimeTypeResolver.parseExtension(filename)?.lowercase()
        if (extension != null && extension in EXTENSIONS) {
            return true
        }

        if (mimeType == null) {
            return false
        }

        val normalized = mimeType.lowercase()

        return normalized in MIME_TYPES || CLAIMED_MIME_PREFIXES.any { normalized.startsWith(it) }
    }

    private fun mimeTypesOf(fileTypes: List<FileType>): Set<String> =
        fileTypes.flatMap { Odr.mimetypesByFileType(it) }.map { it.lowercase() }.toSet()
}
