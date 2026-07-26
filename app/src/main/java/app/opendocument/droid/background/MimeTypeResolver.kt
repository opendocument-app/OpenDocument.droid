package app.opendocument.droid.background

/**
 * Derives the final mime type / file extension pair of a document from whatever the detection steps
 * in [MetadataLoader] came up with.
 *
 * Deliberately free of Android dependencies so it can be covered by plain JVM unit tests; in
 * production [ExtensionLookup] is backed by `MimeTypeMap`.
 */
object MimeTypeResolver {

    /** The lookups `MimeTypeMap` provides, as an interface so tests can fake them. */
    interface ExtensionLookup {

        fun extensionFromMimeType(mimeType: String): String?

        fun mimeTypeFromExtension(extension: String?): String?
    }

    class Resolution internal constructor(val mimeType: String?, val extension: String?)

    /**
     * Returns the extension of the given filename, or null if it does not have one. Dotfiles
     * (`.bashrc`) and trailing dots (`report.`) count as "no extension".
     */
    fun parseExtension(filename: String?): String? {
        if (filename == null) {
            return null
        }

        val lastDot = filename.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == filename.length - 1) {
            return null
        }

        return filename.substring(lastDot + 1)
    }

    /**
     * Combines a detected mime type with the extension taken from the filename. If the mime type is
     * known its canonical extension wins, but the filename extension is kept as a fallback; if no
     * mime type could be detected it is looked up from the extension instead. Either field of the
     * result may be null.
     */
    fun resolve(
        mimeType: String?,
        filenameExtension: String?,
        lookup: ExtensionLookup,
    ): Resolution {
        if (mimeType != null) {
            val mimeExtension = lookup.extensionFromMimeType(mimeType)

            return Resolution(mimeType, mimeExtension ?: filenameExtension)
        }

        return Resolution(lookup.mimeTypeFromExtension(filenameExtension), filenameExtension)
    }
}
