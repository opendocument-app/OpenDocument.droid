package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Last resort for documents nothing on the device can render: either use.opendocument.app converts
 * them, or they are uploaded to transfer.opendocument.app and shown in a third party viewer.
 */
class OnlineLoader(context: Context?, private val coreLoader: CoreLoader) :
    FileLoader(context, LoaderType.ONLINE) {

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
            val viewerUri =
                if (isConvertible(options)) doOnlineConvert(options) else doTransferUpload(options)

            result.partTitles.add(null)
            result.partUris.add(viewerUri)

            callOnSuccess(result)
        } catch (e: Throwable) {
            callOnError(result, e)
        }
    }

    /**
     * Whether use.opendocument.app can convert this type itself. Everything else is uploaded and
     * handed to a third party viewer instead.
     */
    fun isConvertible(options: Options): Boolean {
        val fileType = options.fileType

        return "text/rtf" == fileType ||
            "application/vnd.wordperfect" == fileType ||
            "application/vnd.ms-excel" == fileType ||
            "application/msword" == fileType ||
            "application/vnd.ms-powerpoint" == fileType ||
            "application/pdf" == fileType ||
            fileType?.startsWith("application/vnd.openxmlformats-officedocument.") == true ||
            coreLoader.isSupported(options)
    }

    private fun doOnlineConvert(options: Options): Uri {
        // https://stackoverflow.com/a/2469587/198996
        val basePath = "https://use.opendocument.app"
        val url = "$basePath/upload" // TODO: /v1
        val binaryFile =
            checkNotNull(AndroidFileCache.getCacheFile(context, checkNotNull(options.cacheUri)))
        val boundary = System.currentTimeMillis().toString(16)
        val crlf = "\r\n"
        val disposition = "Content-Disposition: form-data; name=\"document\"; filename=\"document\""

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.instanceFollowRedirects = false

        connection.outputStream.use { output ->
            PrintWriter(OutputStreamWriter(output, StreamUtil.ENCODING), true).use { writer ->
                writer.append("--$boundary").append(crlf)
                writer.append(disposition).append(crlf)
                writer.append(crlf).flush()
                StreamUtil.copy(binaryFile, output)
                output.flush()
                writer.append(crlf).flush()

                writer.append("--$boundary--").append(crlf).flush()
            }
        }

        // a converted document is answered with a redirect to it, so a response without a Location
        // is the server refusing the file - concatenating the missing header would build a
        // "...appnull" url that only fails later, inside the webview
        val responseCode = connection.responseCode
        val redirectUrl = connection.getHeaderField("Location")
        if (redirectUrl.isNullOrEmpty()) {
            val error = readError(connection)
            throw IOException("server couldn't handle request: $responseCode $error")
        }

        return Uri.parse(basePath + redirectUrl)
    }

    private fun doTransferUpload(options: Options): Uri {
        val binaryFile =
            checkNotNull(AndroidFileCache.getCacheFile(context, checkNotNull(options.cacheUri)))
        val encodedFilename = URLEncoder.encode(options.filename, StreamUtil.ENCODING)

        val connection =
            URL(TRANSFER_BASE_URL + encodedFilename).openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.instanceFollowRedirects = false

        connection.outputStream.use { outputStream ->
            StreamUtil.copy(binaryFile, outputStream)
            outputStream.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val downloadUrl = readBody(connection)
            if (downloadUrl.isNullOrEmpty()) {
                throw IOException("server couldn't handle request")
            }

            return buildViewerUri(options, downloadUrl.trim())
        } else {
            val error = readError(connection)
            throw IOException("server couldn't handle request: $responseCode $error")
        }
    }

    private fun buildViewerUri(options: Options, downloadUrl: String): Uri {
        if (coreLoader.isSupported(options)) {
            // ODF does not seem to be supported by google docs viewer
            return Uri.parse(MICROSOFT_VIEWER_URL + downloadUrl)
        }

        return Uri.parse(GOOGLE_VIEWER_URL + URLEncoder.encode(downloadUrl, StreamUtil.ENCODING))
    }

    private fun readBody(connection: HttpURLConnection): String? {
        val inputStream = connection.inputStream ?: return null

        return StreamUtil.readFully(inputStream)
    }

    private fun readError(connection: HttpURLConnection): String? {
        try {
            val errorStream = connection.errorStream ?: return null

            return StreamUtil.readFully(errorStream)
        } catch (t: Throwable) {
            return null
        }
    }

    companion object {
        private const val TRANSFER_BASE_URL = "https://transfer.opendocument.app/"

        const val GOOGLE_VIEWER_URL: String = "https://docs.google.com/viewer?embedded=true&url="
        const val MICROSOFT_VIEWER_URL: String =
            "https://view.officeapps.live.com/op/view.aspx?src="

        // https://help.joomlatools.com/article/169-google-viewer
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
        private val MIME_WHITELIST =
            arrayOf(
                "text/",
                "image/",
                "video/",
                "audio/",
                // markup
                "application/json",
                "application/xml",
                "text/css",
                "application/css-stylesheet",
                "application/xhtml",
                "application/x-httpd-php",
                "text/php",
                "application/php",
                "application/x-php",
                "application/x-javascript",
                "text/javascript",
                "text/x-java-source",
                "text/java",
                "text/x-java",
                "application/ms-java",
                // rtf
                "application/rtf",
                "text/rtf",
                // psd: https://filext.com/file-extension/PSD
                "image/photoshop",
                "image/x-photoshop",
                "image/psd",
                "application/photoshop",
                "application/psd",
                "zz-application/zz-winassoc-psd",
                // pdf: https://filext.com/file-extension/PDF
                "application/pdf",
                "application/x-pdf",
                "application/acrobat",
                "applications/vnd.pdf",
                "text/pdf",
                "text/x-pdf",
                // odf: https://filext.com/file-extension/ODT
                "application/vnd.oasis.opendocument",
                "application/x-vnd.oasis.opendocument",
                // ms
                "application/vnd.openxmlformats-officedocument",
                // doc: https://filext.com/file-extension/DOC
                "application/msword",
                "application/doc",
                "appl/text",
                "application/vnd.msword",
                "application/vnd.ms-word",
                "application/winword",
                "application/word",
                "application/x-msw6",
                "application/x-msword",
                // xls: https://filext.com/file-extension/XLS
                "application/vnd.ms-excel",
                "application/msexcel",
                "application/x-msexcel",
                "application/x-ms-excel",
                "application/vnd.ms-excel",
                "application/x-excel",
                "application/x-dos_ms_excel",
                "application/xls",
                // ppt: https://filext.com/file-extension/PPT
                "application/vnd.ms-powerpoint",
                "application/mspowerpoint",
                "application/ms-powerpoint",
                "application/mspowerpnt",
                "application/vnd-mspowerpoint",
                "application/powerpoint",
                "application/x-powerpoint",
                // apple
                "application/x-iwork",
                "application/vnd.apple",
                // postscript: https://filext.com/file-extension/EPS
                "application/postscript",
                "application/eps",
                "application/x-eps",
                "image/eps",
                "image/x-eps",
                // autocad: https://filext.com/file-extension/DXF
                "application/dxf",
                "application/x-autocad",
                "application/x-dxf",
                "drawing/x-dxf",
                "image/vnd.dxf",
                "image/x-autocad",
                "image/x-dxf",
                "zz-application/zz-winassoc-dxf",
                // zip: https://filext.com/file-extension/ZIP
                "application/zip",
                "application/x-zip",
                "application/x-zip-compressed",
                "application/x-compress",
                "application/x-compressed",
                "multipart/x-zip",
                // WPD
                "application/vnd.wordperfect",
            )

        private val MIME_BLACKLIST =
            arrayOf(
                "image/x-tga",
                "image/vnd.djvu",
                "image/g3fax",
                "audio/amr",
                "text/calendar",
                "text/vcard",
                "video/3gpp",
            )
    }
}
