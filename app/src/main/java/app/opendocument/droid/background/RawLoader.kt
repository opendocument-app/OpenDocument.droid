package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Base64OutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * The three files odrcore does not render for us: csv, whose table viewer beats the core's line by
 * line text; svg and xml, which the core has no file type for.
 *
 * It used to cover text, images, audio, video and zip too, each with a viewer out of `assets/`.
 * odrcore 6.2 renders all of those, so [CoreLoader] took them over.
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
            } else if (SupportedDocumentTypes.isCsv(fileType, fileExtension)) {
                // text-suffix.html reads this back and only cares that it is not "xml"
                val extension = "csv"

                val htmlFile = File(cacheDirectory, "text.html")

                FileOutputStream(htmlFile).use { outputStream ->
                    StreamUtil.copy(context.assets.open("text-prefix.html"), outputStream)

                    writeBase64(cacheFile, cacheDirectory, outputStream)

                    StreamUtil.copy(context.assets.open("text-suffix.html"), outputStream)
                }

                val fontFile = File(cacheDirectory, "text.ttf")
                StreamUtil.copy(context.assets.open("text.ttf"), fontFile)

                finalUri =
                    Uri.fromFile(htmlFile)
                        .buildUpon()
                        .appendQueryParameter("ext", extension)
                        .build()
            } else {
                // xml and whatever else got here: handed to the WebView under its own name
                val renamedFile = File(cacheDirectory, "temp.$fileExtension")
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
     * canonical extension win, so an svg the core called `text/plain` arrives there as "txt".
     */
    private fun nameExtension(options: Options): String? =
        MimeTypeResolver.parseExtension(options.filename)

    private fun writeBase64(cacheFile: File, cacheDirectory: File, outputStream: OutputStream) {
        // need to store it in a separate file first because BaseStream writes characters on close
        val baseFile = File(cacheDirectory, "tmp")
        Base64OutputStream(FileOutputStream(baseFile), Base64.NO_WRAP).use { baseOutputStream ->
            StreamUtil.copy(FileInputStream(cacheFile), baseOutputStream)
        }

        StreamUtil.copy(FileInputStream(baseFile), outputStream)
    }
}
