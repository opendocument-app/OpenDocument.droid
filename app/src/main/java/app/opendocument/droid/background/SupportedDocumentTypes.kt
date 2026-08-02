package app.opendocument.droid.background

import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * What the app claims to open and which loader gets it, read out of odrcore instead of kept here.
 *
 * This used to be three hand written lists - mime prefixes for the core, mime prefixes for
 * [RawLoader] and an extension fallback - because odrcore only knew one canonical mime type and one
 * extension per format, and none of the spellings a content provider actually hands out. odrcore
 * 6.1 publishes its whole format table instead ([Odr.allFileTypes], [Odr.mimetypesByFileType],
 * [Odr.fileExtensionsByFileType] and [Odr.capabilitiesByFileType]), so the lists are derived now
 * and the app cannot claim a format the core does not have, or miss one it does.
 *
 * Two questions live here, and they are not the same one: what the app offers itself for
 * ([CLAIMED_FILE_TYPES], mirrored by the STRICT_CATCH `activity-alias`) and what [CoreLoader]
 * renders once it has a file ([CORE_FILE_TYPES]). The second is wider on purpose - the app does not
 * offer for an mp3, but it plays one handed to it.
 *
 * The other declaration left is that manifest alias, which is XML and cannot read any of this.
 * `SupportedFormatsTest` walks the core's table and asks the package manager whether the manifest
 * still agrees, so the two cannot drift silently.
 *
 * These are still a guess, not the real answer: a folder listing only has a name and whatever mime
 * type the provider volunteered, and it has to go on appearances. What the core *decides* is
 * everything after the file is in the cache - [MetadataLoader] runs its libmagic over the copy, and
 * [CoreLoader.isDocumentEditable] asks the opened document itself.
 */
object SupportedDocumentTypes {

    /**
     * What [CoreLoader] renders: everything odrcore can turn into html, minus [RAW_FILE_TYPES].
     * Since 6.2 that is far more than documents - images, archives, fonts and media included.
     */
    private val CORE_FILE_TYPES: List<FileType> by lazy {
        Odr.allFileTypes().filter {
            Odr.capabilitiesByFileType(it).translateHtml && it !in RAW_FILE_TYPES
        }
    }

    /**
     * What [RawLoader] keeps although the core would take it too: only csv, which odrcore renders
     * line by line where `text-prefix.html` builds a table. An app decision, so not derived.
     */
    private val RAW_FILE_TYPES = listOf(FileType.COMMA_SEPARATED_VALUES)

    /** No core file type for this one, so [RawLoader] lets the WebView draw it. */
    private const val SVG_MIME_TYPE = "image/svg+xml"

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
     * Images, as a prefix rather than a set of file types: odrcore names all of them since 6.2
     * except svg, which the wildcard is what claims. The manifest declares `image` likewise.
     */
    private val RAW_MIME_PREFIXES = listOf("image/")

    /** Every spelling of [RAW_FILE_TYPES], for [isCsv]. */
    private val RAW_MIME_TYPES: Set<String> by lazy { mimeTypesOf(RAW_FILE_TYPES) }

    /** Also unknown to the core. Never claimed - only a catch-all or a share gets one here. */
    private val XML_MIME_TYPES = setOf("application/xml", "text/xml")

    /** Every mime type spelling odrcore accepts for a format [CoreLoader] renders. */
    val CORE_MIME_TYPES: Set<String> by lazy { mimeTypesOf(CORE_FILE_TYPES) }

    /** The same for everything the app offers itself for. */
    val MIME_TYPES: Set<String> by lazy { mimeTypesOf(CLAIMED_FILE_TYPES) }

    /**
     * The extensions to fall back on, because providers regularly volunteer nothing better than
     * `application/octet-stream` - and because a file manager that sends `ACTION_VIEW` with no mime
     * type at all leaves the name as the only thing anyone can go on. That second case is what the
     * `pathPattern` filters in AndroidManifest.xml cover, and they have to list exactly this set.
     */
    val EXTENSIONS: Set<String> by lazy {
        CLAIMED_FILE_TYPES.flatMap { Odr.fileExtensionsByFileType(it) }
            .map { it.lowercase() }
            .toSet()
    }

    /**
     * The spelling of [mimeType] the rest of the app goes by: odrcore's canonical one whenever the
     * core recognizes what a provider handed us, and the original otherwise.
     *
     * Reading the core's table means the app claims every spelling in it - `application/csv`,
     * `application/x-zip-compressed`, `multipart/x-zip` - and not just the one it names first.
     * Collapsing them here keeps one spelling per format flowing downstream.
     *
     * Anything the core does not name - svg, xml - passes through untouched.
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
     * Whether [RawLoader] takes this instead. Asked *before* [isRenderedByCore], because the core
     * would render a csv itself and RawLoader would never get its turn.
     *
     * [extension] is needed because [MetadataLoader] identifies by content first, and an svg or an
     * xml *is* text - `Odr.mimetype` says `text/plain` and the provider's `image/svg+xml` never
     * arrives. The name only gets a say where the detection has none - see [nameSays].
     */
    fun isRenderedByRaw(mimeType: String?, extension: String? = null): Boolean =
        isCsv(mimeType, extension) || isSvg(mimeType, extension) || isXml(mimeType, extension)

    /** Csv in any spelling odrcore accepts for it - [RawLoader] builds a table out of one. */
    fun isCsv(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() in RAW_MIME_TYPES || nameSays(mimeType, extension, "csv")

    /** Svg, which odrcore has no file type for and the WebView draws by itself. */
    fun isSvg(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() == SVG_MIME_TYPE || nameSays(mimeType, extension, "svg")

    /** Xml, likewise unknown to the core and likewise shown by the WebView. */
    fun isXml(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() in XML_MIME_TYPES || nameSays(mimeType, extension, "xml")

    /**
     * Whether the file is called `.expected` *and* the detection left room for the name to know
     * better - plain text, or nothing recognized. The bytes win otherwise: a `report.csv` holding
     * an odt would go to the table viewer, succeed, and leave no fallback.
     */
    private fun nameSays(mimeType: String?, extension: String?, expected: String): Boolean {
        if (extension?.lowercase() != expected) {
            return false
        }

        val fileType = mimeType?.let { Odr.fileTypeByMimetype(it) } ?: return true

        return fileType == FileType.UNKNOWN || fileType == FileType.TEXT_FILE
    }

    /**
     * Whether the core files this as a document rather than text, an image, an archive, a font or
     * media. What [OnlineLoader] and [DocumentFragment] used to get from [CoreLoader.isSupported],
     * back when the core loader only claimed documents.
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

        return normalized in MIME_TYPES || RAW_MIME_PREFIXES.any { normalized.startsWith(it) }
    }

    private fun mimeTypesOf(fileTypes: List<FileType>): Set<String> =
        fileTypes.flatMap { Odr.mimetypesByFileType(it) }.map { it.lowercase() }.toSet()
}
