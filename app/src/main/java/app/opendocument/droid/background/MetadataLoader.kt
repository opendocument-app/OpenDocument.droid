package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.opendocument.core.Odr
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

    private companion object {
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
