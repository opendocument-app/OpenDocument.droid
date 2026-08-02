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
     *
     * Since 6.2 that reaches well past documents - text, images, zip and cfb, fonts, audio and
     * video all get a page of their own, and every one of them used to need a viewer here. A format
     * the core can name but not decode stays out: `.xlsb` is the current example.
     */
    private val CORE_FILE_TYPES: List<FileType> by lazy {
        Odr.allFileTypes().filter {
            Odr.capabilitiesByFileType(it).translateHtml && it !in RAW_FILE_TYPES
        }
    }

    /**
     * What [RawLoader] keeps although the core would take it too: only csv, because odrcore renders
     * one line-numbered like any other text and `text-prefix.html` builds a table out of it. The
     * app's own decision, so named rather than derived.
     */
    private val RAW_FILE_TYPES = listOf(FileType.COMMA_SEPARATED_VALUES)

    /** No core file type for this one, so [RawLoader] lets the WebView draw it. */
    private const val SVG_MIME_TYPE = "image/svg+xml"

    /**
     * What the app offers itself for: the core's document formats plus the three non-document ones
     * worth opening a document viewer for. Far narrower than [CORE_FILE_TYPES], which also renders
     * fonts, archives, audio and video that are never claimed - as are json and markdown.
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
     * Images, as a prefix rather than a set of file types. odrcore names most of them since 6.2 -
     * webp, tiff, heic and avif joined png, gif, jpeg, bmp and starview metafiles - but not svg,
     * which the wildcard is what claims. The manifest declares the whole `image` type likewise.
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
     * `application/x-zip-compressed`, `multipart/x-zip` - and not just the one the core happens to
     * name first. Both loaders match whole sets rather than prefixes now, so this matters less than
     * it did, but it still keeps one spelling per format flowing downstream and it is what the
     * `.svg` and `.xml` checks in [isRenderedByRaw] are written against.
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
     * Whether [RawLoader] takes this instead, which is asked *before* [isRenderedByCore] - csv is a
     * format the core would happily render, and only losing the race decides it.
     *
     * Three things reach it: csv, for the table viewer the core has no equivalent for; svg, which
     * the core has no file type for at all; and xml, likewise unknown to the core but something a
     * WebView shows without help. Everything else the core cannot open is better served by the
     * upload offer than by a rename and a shrug.
     *
     * [extension] is not a nicety here. An svg and an xml *are* text, and [MetadataLoader] asks the
     * core to identify the file before anything else - `Odr.mimetype` reads the bytes and says
     * `text/plain`, so the `image/svg+xml` the provider volunteered never reaches this at all and
     * the name is the only thing left that knows better. The same goes for a csv plain enough that
     * the content detection does not spot the delimiter.
     */
    fun isRenderedByRaw(mimeType: String?, extension: String? = null): Boolean =
        isCsv(mimeType, extension) || isSvg(mimeType, extension) || isXml(mimeType, extension)

    /** Csv in any spelling odrcore accepts for it - [RawLoader] builds a table out of one. */
    fun isCsv(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() in RAW_MIME_TYPES || extension?.lowercase() == "csv"

    /** Svg, which odrcore has no file type for and the WebView draws by itself. */
    fun isSvg(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() == SVG_MIME_TYPE || extension?.lowercase() == "svg"

    /** Xml, likewise unknown to the core and likewise shown by the WebView. */
    fun isXml(mimeType: String?, extension: String? = null): Boolean =
        mimeType?.lowercase() in XML_MIME_TYPES || extension?.lowercase() == "xml"

    /**
     * Whether the core files this as a document, as opposed to text, an image, an archive, a font
     * or something with a soundtrack.
     *
     * This is the question [OnlineLoader] wants when it asks what a converter or the microsoft
     * viewer could do with a file, and the one [DocumentFragment] wants when it offers to hand a
     * file to another app. Both used to ask [CoreLoader.isSupported], which meant the same thing
     * back when the core loader only claimed documents.
     */
    fun isDocument(mimeType: String?): Boolean {
        if (mimeType == null) {
            return false
        }

        // not lowercased first, unlike the lookups against our own sets: the core's table is
        // matched exactly and spells some of its own entries with capitals ("macroEnabled"). What
        // gets here has been through [canonicalMimeType] anyway, so it is the core's own spelling
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
