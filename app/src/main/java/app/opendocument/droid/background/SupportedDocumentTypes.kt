package app.opendocument.droid.background

/**
 * The one list of what the app claims to open, for both of the places that have to guess.
 *
 * There used to be two copies of this - one here for the folder browser, one in [CoreLoader] for
 * the loader - which is one copy too many for a set that only ever changes as a whole.
 * [isRenderedByCore] is what [CoreLoader.isSupported] answers with, [isSupported] adds what
 * [RawLoader] shows and the extension fallback on top, and the STRICT_CATCH `activity-alias` in
 * AndroidManifest.xml is the third and last declaration, which XML keeps out of reach.
 * `SupportedFormatsTest` walks this table and asks the package manager whether the manifest still
 * agrees, so the two cannot drift silently any more.
 *
 * Deliberately free of Android dependencies, and of the core's own tables - odrcore does know
 * [app.opendocument.core.Odr.fileTypeByMimetype] and `fileTypeByFileExtension`, but both are native
 * calls into libodr_jni, and both only know one canonical mime type per format: no templates, no
 * macro enabled variants, no `application/x-vnd.oasis...`, which are exactly the spellings a
 * content provider hands out. What the core does decide is everything *after* the file is in the
 * cache - [MetadataLoader] runs its libmagic over it, and [CoreLoader.isDocumentEditable] asks the
 * opened document itself.
 *
 * So this is a guess, not the real answer: a folder listing only has a name and whatever mime type
 * the provider volunteered, and it has to go on appearances.
 */
object SupportedDocumentTypes {

    /**
     * What the core renders itself: opendocument, ooxml, the three legacy binary microsoft formats
     * and pdf. odrcore keeps reference html output for every one of them, doc, ppt and xls
     * included.
     *
     * Matching by prefix carries the variants along: the ooxml prefix covers the templates, and the
     * macro enabled ones are spelled `vnd.ms-word.document.macroEnabled.12` and so on, which is why
     * the legacy powerpoint and excel types appear here without their subtypes and why
     * `vnd.ms-word` is listed next to `msword`.
     */
    private val CORE_MIME_PREFIXES =
        listOf(
            // odf, including the master documents and the x- spelling some providers use
            "application/vnd.oasis.opendocument",
            "application/x-vnd.oasis.opendocument",
            // ooxml: word, excel and powerpoint, their templates and their macro variants
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-word",
            // legacy binary microsoft: doc, xls and ppt
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            // not a document, but the core renders it just the same
            "application/pdf",
        )

    /**
     * What [RawLoader] shows instead - text, csv, images and zip - which the core would take too
     * but which get their own player or viewer here.
     *
     * Narrower than [RawLoader.isSupported], which also takes audio and video: those are worth
     * playing once someone hands us one, but not worth offering the app for.
     */
    private val RAW_MIME_PREFIXES = listOf("text/plain", "text/csv", "image/", "application/zip")

    /**
     * The extensions to fall back on, spelling out the same set once more because providers
     * regularly volunteer nothing better than `application/octet-stream`.
     *
     * Public so `SupportedFormatsTest` can walk it: it holds every one of these against the core's
     * own extension table and against the manifest.
     */
    val EXTENSIONS: Set<String> =
        setOf(
            "odt",
            "ods",
            "odp",
            "odg",
            "ott",
            "ots",
            "otp",
            "otg",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "pdf",
            "txt",
            "csv",
            "zip",
        )

    /** Whether [CoreLoader] is expected to render this - see [CORE_MIME_PREFIXES]. */
    fun isRenderedByCore(mimeType: String?): Boolean = matches(mimeType, CORE_MIME_PREFIXES)

    /** Whether the folder browser should offer this at all. */
    fun isSupported(mimeType: String?, filename: String?): Boolean {
        // a recognised extension has to be able to win on its own, or every octet-stream is lost
        val extension = MimeTypeResolver.parseExtension(filename)?.lowercase()
        if (extension != null && extension in EXTENSIONS) {
            return true
        }

        return isRenderedByCore(mimeType) || matches(mimeType, RAW_MIME_PREFIXES)
    }

    private fun matches(mimeType: String?, prefixes: List<String>): Boolean {
        if (mimeType == null) {
            return false
        }

        val normalized = mimeType.lowercase()

        return prefixes.any { normalized.startsWith(it) }
    }
}
