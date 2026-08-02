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
 * The three files odrcore does not render for us, and nothing else.
 *
 * This used to be the fallback for everything the core could not open as a *document*: text,
 * images, audio, video and zip all had a viewer of their own out of `assets/`. odrcore 6.2 renders
 * every one of them - a page with an `<img>`, an `<audio>` or a `<video>` on it, a line-numbered
 * text view, a listing for an archive - so [CoreLoader] took them over and the viewers went with
 * them.
 *
 * What is left has an actual reason to be here:
 * - **csv**, which the core files as text and renders line by line. `text-prefix.html` detects the
 *   delimiter and builds a table instead, which is worth keeping a loader for.
 * - **svg**, which the core has no file type for. The WebView draws it given a name it recognizes.
 * - **xml** and anything else that gets this far, handed over under a name the WebView knows.
 *
 * [SupportedDocumentTypes.isRenderedByRaw] is what decides, and [LoaderService] asks it before it
 * asks the core - csv would otherwise be rendered by [CoreLoader] and never reach this at all.
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
                // the browser does not recognize an svg that is not called ".svg" - and the name
                // is ours to pick rather than the file's, which reached the cache without one.
                // the raster formats that needed the old "call everything jpg" workaround next to
                // this are the core's now
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
                // xml and whatever else got this far: the WebView is given the bytes under the
                // name the file arrived with, and makes of them what it can
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
     * The extension of the file's own name, which is not [Options.fileExtension]:
     * [MimeTypeResolver.resolve] lets the detected mime type's canonical extension win, so an svg
     * the core called `text/plain` arrives here as "txt" and the only `.svg` left is in the name.
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
