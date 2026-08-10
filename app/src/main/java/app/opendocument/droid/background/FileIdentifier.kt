package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.opendocument.core.Odr
import app.opendocument.droid.nonfree.CrashManager
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLConnection

/**
 * Names and types the cached copy of a document, in that order: the filename comes from the
 * provider, the mime type from the bytes.
 *
 * It never refuses a file - what nothing can name simply gets a null mime type, and the ui shows
 * "N/A" for it. The one exception is an empty file, which is a document that is not there.
 */
class FileIdentifier(private val crashManager: CrashManager) {

    /**
     * @param uri the document as the user picked it, for the provider's own answers
     * @param cachedFile our copy of it, for the core's
     * @throws FileNotFoundException when the file turns out to be empty
     */
    fun identify(context: Context, uri: Uri, cachedFile: File): IdentifiedFile {
        val filename = displayName(context, uri)

        var mimetype = detectMimeType(context, uri, cachedFile, filename)

        // one spelling per format from here on: a provider may volunteer application/csv
        mimetype = SupportedDocumentTypes.canonicalMimeType(mimetype)

        if (EMPTY_MIME_TYPE == mimetype) {
            throw FileNotFoundException()
        }

        val resolution =
            MimeTypeResolver.resolve(
                mimetype,
                MimeTypeResolver.parseExtension(filename),
                MIME_TYPE_LOOKUP,
            )

        return IdentifiedFile(
            FileCache.getCacheFileUri(context, cachedFile),
            filename,
            resolution.mimeType,
            resolution.extension,
        )
    }

    private fun displayName(context: Context, uri: Uri): String {
        var filename: String? = null

        try {
            // https://stackoverflow.com/a/38304115/198996
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    filename = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // providers throw here for a missing access token or a missing DISPLAY_NAME column
            crashManager.log(e)
        }

        return filename ?: uri.lastPathSegment ?: "N/A"
    }

    /** The core first, then everything android can guess. Null if none of them can name it. */
    private fun detectMimeType(
        context: Context,
        uri: Uri,
        cachedFile: File,
        filename: String,
    ): String? {
        try {
            val mimetype = Odr.mimetype(cachedFile.absolutePath)

            // text/plain without a charset is the core's fallback for bytes nothing else
            // claims, not an identification - it would hand random binary to the text renderer
            if (mimetype != null && (mimetype != TEXT_MIME_TYPE || hasKnownCharset(cachedFile))) {
                return mimetype
            }
        } catch (e: Throwable) {
            crashManager.log(e)
        }

        context.contentResolver.getType(uri)?.let {
            return it
        }

        try {
            URLConnection.guessContentTypeFromName(filename)?.let {
                return it
            }
        } catch (e: Exception) {
            // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
            crashManager.log(e)
        }

        try {
            FileInputStream(cachedFile).use { tempStream: InputStream ->
                return URLConnection.guessContentTypeFromStream(tempStream)
            }
        } catch (e: Exception) {
            crashManager.log(e)
        }

        return null
    }

    /** Whether the core can name the encoding of a file it decided is text. */
    private fun hasKnownCharset(file: File): Boolean =
        try {
            val opened = Odr.open(file.absolutePath)

            !opened.isTextFile || opened.asTextFile().charset() != null
        } catch (e: Throwable) {
            crashManager.log(e)

            false
        }

    private companion object {
        const val TEXT_MIME_TYPE = "text/plain"

        /** What the core answers for a file with nothing in it. */
        const val EMPTY_MIME_TYPE = "inode/x-empty"

        val MIME_TYPE_LOOKUP =
            object : MimeTypeResolver.ExtensionLookup {

                override fun extensionFromMimeType(mimeType: String): String? =
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

                override fun mimeTypeFromExtension(extension: String?): String? {
                    if (extension == null) {
                        return null
                    }

                    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                }
            }
    }
}
