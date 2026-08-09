package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * The two files [CoreLoader] does not get: svg and xml, which the WebView draws by itself.
 *
 * [SupportedDocumentTypes.isRenderedByRaw] decides, and [LoaderService] asks it before the core.
 */
class RawLoader(context: Context?) : FileLoader(context, LoaderType.RAW) {

    override fun isSupported(options: Options): Boolean =
        SupportedDocumentTypes.isRenderedByRaw(options.fileType, nameExtension(options))

    override fun loadSync(options: Options) {
        val result = Result(type, options)

        try {
            val fileType = checkNotNull(options.fileType) { "no file type detected" }
            val fileExtension = nameExtension(options)

            val cacheFile =
                checkNotNull(AndroidFileCache.getCacheFile(context, checkNotNull(options.cacheUri)))
            val cacheDirectory = AndroidFileCache.getCacheDirectory(cacheFile)

            val finalUri: Uri
            if (SupportedDocumentTypes.isSvg(fileType, fileExtension)) {
                // the browser does not recognize an svg not called ".svg", and the cached copy
                // has no name of its own
                val extension = "svg"

                val htmlFile = File(cacheDirectory, "image.html")
                StreamUtil.copy(context.assets.open("image.html"), htmlFile)

                val imageFile = File(cacheDirectory, "image.$extension")
                StreamUtil.copy(cacheFile, imageFile)

                finalUri =
                    Uri.fromFile(htmlFile)
                        .buildUpon()
                        .appendQueryParameter("ext", extension)
                        .build()
            } else {
                // xml, the only case left: the WebView goes by the name, and a shared file
                // typed application/xml need not have an extension of its own
                val renamedFile = File(cacheDirectory, "temp.${fileExtension ?: "xml"}")
                StreamUtil.copy(cacheFile, renamedFile)

                finalUri = Uri.fromFile(renamedFile)
            }

            result.partTitles.add(null)
            result.partUris.add(finalUri)
            callOnSuccess(result)
        } catch (e: Throwable) {
            callOnError(result, e)
        }
    }

    /**
     * Not [Options.fileExtension]: [MimeTypeResolver.resolve] lets the detected mime type's
     * canonical extension win, so an xml the core called `text/plain` arrives there as "txt".
     */
    private fun nameExtension(options: Options): String? =
        MimeTypeResolver.parseExtension(options.filename)
}
