package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.opendocument.core.Odr
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection

/**
 * Runs first for every document: copies it into the cache and fills in the filename, mime type and
 * extension the other loaders decide on. It never fails to "support" a file - what it cannot
 * identify simply keeps the "N/A" type.
 */
class MetadataLoader(context: Context?) : FileLoader(context, LoaderType.METADATA) {

    override fun isSupported(options: Options): Boolean = true

    override fun loadSync(options: Options) {
        val result = Result(type, options)

        options.fileType = "N/A"

        try {
            var uri = checkNotNull(options.originalUri) { "nothing to load" }

            val uriString = uri.toString()
            if (uriString.startsWith("/./")) {
                uri = Uri.parse(uriString.substring(2))
            }

            AndroidFileCache.cleanup(context)

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

            if (filename == null) {
                filename = uri.lastPathSegment
            }

            if (filename == null) {
                filename = "N/A"
            }

            options.filename = filename

            val cachedFile =
                if (AndroidFileCache.isCached(context, uri)) {
                    checkNotNull(AndroidFileCache.getCacheFile(context, uri))
                } else {
                    AndroidFileCache.createCacheFile(context).also { cacheFile ->
                        val stream = context.contentResolver.openInputStream(uri)
                        StreamUtil.copy(checkNotNull(stream) { "cannot open $uri" }, cacheFile)
                    }
                }

            // if file didn't exist an exception would have been thrown by now
            options.fileExists = true

            options.cacheUri = AndroidFileCache.getCacheFileUri(context, cachedFile)

            val extension = MimeTypeResolver.parseExtension(options.filename)

            var mimetype: String? = null
            try {
                mimetype = Odr.mimetype(cachedFile.absolutePath)

                // Text is the core's fallback for bytes nothing else claims, and since 6.4 it
                // answers "text/plain" even where it cannot name a charset - it used to refuse
                // those outright. That is a guess rather than an identification, and taken at
                // face value it sends random binary to the text renderer and, because the upload
                // offer whitelists "text/", to the conversion service. Drop it and let the
                // fallbacks below have their turn, which is what happened before 6.4.
                if (mimetype == TEXT_MIME_TYPE && !hasKnownCharset(cachedFile)) {
                    mimetype = null
                }
            } catch (e: Throwable) {
                crashManager.log(e)
            }

            if (mimetype == null) {
                mimetype = context.contentResolver.getType(uri)
            }

            if (mimetype == null) {
                try {
                    mimetype = URLConnection.guessContentTypeFromName(filename)
                } catch (e: Exception) {
                    // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
                    crashManager.log(e)
                }
            }

            if (mimetype == null) {
                try {
                    FileInputStream(cachedFile).use { tempStream: InputStream ->
                        mimetype = URLConnection.guessContentTypeFromStream(tempStream)
                    }
                } catch (e: Exception) {
                    crashManager.log(e)
                }
            }

            // one spelling per format from here on: a provider may volunteer application/csv
            mimetype = SupportedDocumentTypes.canonicalMimeType(mimetype)

            val resolution = MimeTypeResolver.resolve(mimetype, extension, MIME_TYPE_LOOKUP)
            mimetype = resolution.mimeType

            if (resolution.extension != null) {
                options.fileExtension = resolution.extension
            }
            if (mimetype != null) {
                options.fileType = mimetype
            }

            if ("inode/x-empty" == mimetype) {
                throw FileNotFoundException()
            }

            if (options.persistentUri) {
                try {
                    val evicted = RecentDocumentsUtil.addRecentDocument(context, filename, uri)
                    if (evicted.isNotEmpty()) {
                        // stay well below the per package grant limit
                        PersistedUriPermissions.prune(context)
                    }
                } catch (e: IOException) {
                    crashManager.log(e)
                }
            }

            callOnSuccess(result)
        } catch (e: Throwable) {
            options.fileType = "N/A"

            val originalUri = options.originalUri
            if (originalUri != null) {
                try {
                    RecentDocumentsUtil.removeRecentDocument(context, originalUri)
                } catch (e1: Exception) {
                    crashManager.log(e1)
                }
            }

            callOnError(result, e)
        }
    }

    /**
     * Whether the core can name the encoding of a file it decided is text. It opens one either
     * way - see the call site - so a `false` here means bytes it can neither name nor decode.
     */
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
