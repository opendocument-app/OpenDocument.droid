package app.opendocument.droid.background

import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr

/**
 * What the app claims to open, read out of odrcore instead of kept here.
 *
 * This used to be three hand written lists - mime prefixes for the core, mime prefixes for
 * [RawLoader] and an extension fallback - because odrcore only knew one canonical mime type and one
 * extension per format, and none of the spellings a content provider actually hands out. odrcore
 * 6.1 publishes its whole format table instead ([Odr.allFileTypes], [Odr.mimetypesByFileType],
 * [Odr.fileExtensionsByFileType] and [Odr.capabilitiesByFileType]), so the lists are derived now
 * and the app cannot claim a format the core does not have, or miss one it does.
 *
 * Two declarations are left. The app's own choice of which of the core's formats to route
 * where - [CORE_FILE_TYPES] against [RAW_FILE_TYPES] - and the STRICT_CATCH `activity-alias` in
 * AndroidManifest.xml, which is XML and cannot read any of this. `SupportedFormatsTest` walks the
 * core's table and asks the package manager whether the manifest still agrees, so the two cannot
 * drift silently.
 *
 * These are still a guess, not the real answer: a folder listing only has a name and whatever mime
 * type the provider volunteered, and it has to go on appearances. What the core *decides* is
 * everything after the file is in the cache - [MetadataLoader] runs its libmagic over the copy, and
 * [CoreLoader.isDocumentEditable] asks the opened document itself.
 */
object SupportedDocumentTypes {

    /**
     * What [CoreLoader] renders: every format odrcore declares it can turn into html and files as a
     * document.
     *
     * That is the same rule the loader follows, so it is the core's answer rather than a list -
     * today it selects opendocument, ooxml, the legacy binary doc/ppt/xls and pdf. The category
     * narrows it to what belongs in a document viewer: the core translates text, csv, images, zip
     * and fonts too, and the first three of those get their own viewer in [RawLoader] below.
     *
     * A format the core can name but not decode stays out on its own - `.xlsb` is the current
     * example, an ooxml package with binary parts that the app used to claim because the mime type
     * starts like an excel one.
     */
    private val CORE_FILE_TYPES: List<FileType> by lazy {
        Odr.allFileTypes().filter {
            Odr.capabilitiesByFileType(it).translateHtml &&
                Odr.fileCategoryByFileType(it) == FileCategory.DOCUMENT
        }
    }

    /**
     * What [RawLoader] shows instead, which the core would take too but which get their own player
     * or viewer here.
     *
     * Named rather than derived, because this is the app's own decision and nothing the core knows
     * about: it is narrower than [RawLoader.isSupported], which also takes audio and video - those
     * are worth playing once someone hands us one, but not worth offering the app for - and it
     * leaves out json and markdown, which the core files as text but which nobody opens a document
     * viewer for.
     */
    private val RAW_FILE_TYPES =
        listOf(FileType.TEXT_FILE, FileType.COMMA_SEPARATED_VALUES, FileType.ZIP)

    /**
     * Images, as a prefix rather than a set of file types.
     *
     * The core names png, gif, jpeg, bmp and starview metafiles; [RawLoader] hands anything at all
     * to the WebView under a name it recognizes, so webp, heic and svg are worth offering for as
     * well. The manifest declares the whole `image` type with a wildcard for the same reason.
     */
    private val RAW_MIME_PREFIXES = listOf("image/")

    /** Every mime type spelling odrcore accepts for a format [CoreLoader] renders. */
    val CORE_MIME_TYPES: Set<String> by lazy { mimeTypesOf(CORE_FILE_TYPES) }

    /** The same for everything the app offers itself for, [RawLoader]'s share included. */
    val MIME_TYPES: Set<String> by lazy { CORE_MIME_TYPES + mimeTypesOf(RAW_FILE_TYPES) }

    /**
     * The extensions to fall back on, because providers regularly volunteer nothing better than
     * `application/octet-stream` - and because a file manager that sends `ACTION_VIEW` with no mime
     * type at all leaves the name as the only thing anyone can go on. That second case is what the
     * `pathPattern` filters in AndroidManifest.xml cover, and they have to list exactly this set.
     */
    val EXTENSIONS: Set<String> by lazy {
        (CORE_FILE_TYPES + RAW_FILE_TYPES)
            .flatMap { Odr.fileExtensionsByFileType(it) }
            .map { it.lowercase() }
            .toSet()
    }

    /** Whether [CoreLoader] is expected to render this - see [CORE_FILE_TYPES]. */
    fun isRenderedByCore(mimeType: String?): Boolean =
        mimeType != null && mimeType.lowercase() in CORE_MIME_TYPES

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
