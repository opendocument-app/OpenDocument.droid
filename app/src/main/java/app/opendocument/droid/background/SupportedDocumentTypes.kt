package app.opendocument.droid.background

/**
 * Whether a document is worth offering in the folder browser.
 *
 * Deliberately free of Android dependencies so it can be covered by plain JVM unit tests.
 *
 * This is a guess, not the real answer. [MetadataLoader] decides what is supported by running
 * libmagic over the file once it has been copied into the cache, and neither [CoreLoader] nor
 * [RawLoader] can answer before that - so a folder listing, which only has a name and whatever mime
 * type the provider volunteered, has to go on appearances.
 *
 * Mirrors the STRICT_CATCH intent filter in AndroidManifest.xml. **Keep the two in step**: that
 * filter is what the system offers us for, this is what we offer ourselves for.
 */
object SupportedDocumentTypes {

    /** Mime types and prefixes taken from the STRICT_CATCH filter. */
    private val MIME_PREFIXES =
        listOf(
            // everything the core opens as a document - the same set as CoreLoader.isSupported
            "application/vnd.oasis.opendocument",
            "application/x-vnd.oasis.opendocument",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-word",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            // and what the core or RawLoader shows without being a document
            "application/pdf",
            "application/zip",
            "text/plain",
            "text/csv",
            "image/",
        )

    /** The extensions the STRICT_CATCH pathPatterns spell out. */
    private val EXTENSIONS =
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

    fun isSupported(mimeType: String?, filename: String?): Boolean {
        // providers regularly hand out application/octet-stream for anything they do not know,
        // so a recognised extension has to be able to win on its own
        val extension = MimeTypeResolver.parseExtension(filename)?.lowercase()
        if (extension != null && extension in EXTENSIONS) {
            return true
        }

        if (mimeType == null) {
            return false
        }

        val normalized = mimeType.lowercase()

        return MIME_PREFIXES.any { normalized.startsWith(it) }
    }
}
