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
 * Fallback for everything the core cannot open as a document: media files get one of the players in
 * `assets/`, text is rendered by the core anyway and the rest is simply handed to the WebView under
 * a name it recognizes.
 */
class RawLoader(
    context: Context?,
    // text files are rendered by the core and published on the http server that CoreLoader
    // owns, so this loader needs the CoreLoader rather than a core of its own
    private val coreLoader: CoreLoader,
) : FileLoader(context, LoaderType.RAW) {

    override fun isSupported(options: Options): Boolean {
        val fileType = options.fileType ?: return false

        for (mime in MIME_WHITELIST) {
            if (!fileType.startsWith(mime)) {
                continue
            }

            for (blackMime in MIME_BLACKLIST) {
                if (fileType.startsWith(blackMime)) {
                    return false
                }
            }

            return true
        }

        return false
    }

    override fun loadSync(options: Options) {
        val result = Result(type, options)

        try {
            val fileType = checkNotNull(options.fileType) { "no file type detected" }
            var extension = options.fileExtension

            val cacheFile =
                checkNotNull(AndroidFileCache.getCacheFile(context, checkNotNull(options.cacheUri)))
            val cacheDirectory = AndroidFileCache.getCacheDirectory(cacheFile)

            val finalUri: Uri
            if (fileType.startsWith("image/")) {
                val htmlFile = File(cacheDirectory, "image.html")
                StreamUtil.copy(context.assets.open("image.html"), htmlFile)

                // use jpg as a workaround for most images
                extension = "jpg"
                if (fileType.contains("svg")) {
                    // browser does not recognize SVG if it's not called ".svg"
                    extension = "svg"
                }

                val imageFile = File(cacheDirectory, "image.$extension")
                StreamUtil.copy(cacheFile, imageFile)

                finalUri =
                    Uri.fromFile(htmlFile)
                        .buildUpon()
                        .appendQueryParameter("ext", extension)
                        .build()
            } else if (fileType.startsWith("audio/")) {
                val htmlFile = File(cacheDirectory, "audio.html")
                StreamUtil.copy(context.assets.open("audio.html"), htmlFile)

                // use mp3 as a workaround for most images
                extension = "mp3"

                val audioFile = File(cacheDirectory, "audio.$extension")
                StreamUtil.copy(cacheFile, audioFile)

                finalUri =
                    Uri.fromFile(htmlFile)
                        .buildUpon()
                        .appendQueryParameter("ext", extension)
                        .build()
            } else if (fileType.startsWith("video/")) {
                val htmlFile = File(cacheDirectory, "video.html")
                StreamUtil.copy(context.assets.open("video.html"), htmlFile)

                // use mp4 as a workaround for most images
                extension = "mp4"

                val videoFile = File(cacheDirectory, "video.$extension")
                StreamUtil.copy(cacheFile, videoFile)

                finalUri =
                    Uri.fromFile(htmlFile)
                        .buildUpon()
                        .appendQueryParameter("ext", extension)
                        .build()
            } else if (extension == "csv") {
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
            } else if (fileType.startsWith("text/")) {
                // the previous cache path used to be left unset, which reached the core as a
                // null path; give the translation a directory of its own next to the file
                val coreCacheDirectory = File(cacheDirectory, "core_cache")

                val views =
                    coreLoader.host(
                        prefix = "raw-text",
                        inputPath = cacheFile.path,
                        cachePath = coreCacheDirectory.path,
                    )

                finalUri = Uri.parse(views[0].url)
            } else if (fileType.startsWith("application/zip")) {
                val htmlFile = File(cacheDirectory, "zip.html")

                FileOutputStream(htmlFile).use { outputStream ->
                    StreamUtil.copy(context.assets.open("zip-prefix.html"), outputStream)

                    writeBase64(cacheFile, cacheDirectory, outputStream)

                    StreamUtil.copy(context.assets.open("zip-suffix.html"), outputStream)
                }

                finalUri = Uri.fromFile(htmlFile)
            } else {
                val renamedFile = File(cacheDirectory, "temp.$extension")
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

    private fun writeBase64(cacheFile: File, cacheDirectory: File, outputStream: OutputStream) {
        // need to store it in a separate file first because BaseStream writes characters on close
        val baseFile = File(cacheDirectory, "tmp")
        Base64OutputStream(FileOutputStream(baseFile), Base64.NO_WRAP).use { baseOutputStream ->
            StreamUtil.copy(FileInputStream(cacheFile), baseOutputStream)
        }

        StreamUtil.copy(FileInputStream(baseFile), outputStream)
    }

    private companion object {
        // one line per format rather than per spelling, because what reaches this loader has been
        // through SupportedDocumentTypes.canonicalMimeType: a zip is application/zip here even when
        // the provider called it application/x-zip-compressed. These are wider than what the app
        // offers itself for - audio and video are worth playing once someone hands us one.
        val MIME_WHITELIST =
            arrayOf(
                "text/",
                "image/",
                "video/",
                "audio/",
                "application/json",
                "application/xml",
                "application/zip",
            )

        val MIME_BLACKLIST =
            arrayOf(
                "image/vnd.dwg",
                "image/g3fax",
                "image/tiff",
                "image/vnd.djvu",
                "image/x-eps",
                "image/x-tga",
                "image/x-tga",
                "audio/amr",
                "video/3gpp",
                "video/quicktime",
                "text/calendar",
                "text/vcard",
                "text/rtf",
            )
    }
}
