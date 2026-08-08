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
class OnlineLoader(context: Context?) : FileLoader(context, LoaderType.ONLINE) {

    override fun isSupported(options: Options): Boolean {
        val fileType = options.fileType ?: return false

        return MIME_WHITELIST.any { fileType.startsWith(it) } &&
            MIME_BLACKLIST.none { fileType.startsWith(it) }
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
     * Whether use.opendocument.app converts this itself; everything else is uploaded and handed to
     * a third party viewer.
     *
     * The last term asks whether the core *files* this as a document, not whether it renders one:
     * the converter runs libreoffice, so an `.xlsb` is worth sending it.
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
            SupportedDocumentTypes.isDocument(fileType)
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

        try {
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

            // a converted document is answered with a redirect to it, so a response without a
            // Location is the server refusing the file - concatenating the missing header would
            // build a "...appnull" url that only fails later, inside the webview
            val responseCode = connection.responseCode
            val redirectUrl = connection.getHeaderField("Location")
            if (redirectUrl.isNullOrEmpty()) {
                val error = readError(connection)
                throw IOException("server couldn't handle request: $responseCode $error")
            }

            return Uri.parse(basePath + redirectUrl)
        } finally {
            // the success path never reads the body, so the socket would sit in the keep-alive
            // pool with an unconsumed response until it timed out
            connection.disconnect()
        }
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
        // google's viewer will not take odf, microsoft's will not take pdf
        val isPdf = options.fileType?.startsWith("application/pdf") == true

        // the office viewer wants an office document; an image or an mp3 is google's problem
        if (SupportedDocumentTypes.isDocument(options.fileType) && !isPdf) {
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
        //
        // matched as prefixes, so the four families at the top already cover every "text/..." and
        // "image/..." spelling of the formats below them
        private val MIME_WHITELIST =
            arrayOf(
                "text/",
                "image/",
                "video/",
                "audio/",
                // markup
                "application/json",
                "application/xml",
                "application/css-stylesheet",
                "application/xhtml",
                "application/x-httpd-php",
                "application/php",
                "application/x-php",
                "application/x-javascript",
                "application/ms-java",
                // rtf
                "application/rtf",
                // psd: https://filext.com/file-extension/PSD
                "application/photoshop",
                "application/psd",
                "zz-application/zz-winassoc-psd",
                // pdf: https://filext.com/file-extension/PDF
                "application/pdf",
                "application/x-pdf",
                "application/acrobat",
                "applications/vnd.pdf",
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
                // autocad: https://filext.com/file-extension/DXF
                "application/dxf",
                "application/x-autocad",
                "application/x-dxf",
                "drawing/x-dxf",
                "zz-application/zz-winassoc-dxf",
                // zip: https://filext.com/file-extension/ZIP
                "application/zip",
                "application/x-zip",
                "application/x-compress",
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
