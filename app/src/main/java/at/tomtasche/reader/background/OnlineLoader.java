package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;

public class OnlineLoader extends FileLoader {

    private static final String TRANSFER_BASE_URL = "https://transfer.opendocument.app/";

    // https://help.joomlatools.com/article/169-google-viewer
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/",
            // markup
            "application/json", "application/xml", "text/css", "application/css-stylesheet", "application/xhtml",
            "application/x-httpd-php", "text/php", "application/php", "application/x-php",
            "application/x-javascript", "text/javascript",
            "text/x-java-source", "text/java", "text/x-java", "application/ms-java",
            // rtf
            "application/rtf", "text/rtf",
            // psd: https://filext.com/file-extension/PSD
            "image/photoshop", "image/x-photoshop", "image/psd", "application/photoshop", "application/psd", "zz-application/zz-winassoc-psd",
            // pdf: https://filext.com/file-extension/PDF
            "application/pdf", "application/x-pdf", "application/acrobat", "applications/vnd.pdf", "text/pdf", "text/x-pdf",
            // odf: https://filext.com/file-extension/ODT
            "application/vnd.oasis.opendocument", "application/x-vnd.oasis.opendocument",
            // ms
            "application/vnd.openxmlformats-officedocument",
            // doc: https://filext.com/file-extension/DOC
            "application/msword", "application/doc", "appl/text", "application/vnd.msword", "application/vnd.ms-word", "application/winword", "application/word", "application/x-msw6", "application/x-msword",
            // xls: https://filext.com/file-extension/XLS
            "application/vnd.ms-excel", "application/msexcel", "application/x-msexcel", "application/x-ms-excel", "application/vnd.ms-excel", "application/x-excel", "application/x-dos_ms_excel", "application/xls",
            // ppt: https://filext.com/file-extension/PPT
            "application/vnd.ms-powerpoint", "application/mspowerpoint", "application/ms-powerpoint", "application/mspowerpnt", "application/vnd-mspowerpoint", "application/powerpoint", "application/x-powerpoint",
            // apple
            "application/x-iwork", "application/vnd.apple",
            // postscript: https://filext.com/file-extension/EPS
            "application/postscript", "application/eps", "application/x-eps", "image/eps", "image/x-eps",
            // autocad: https://filext.com/file-extension/DXF
            "application/dxf", "application/x-autocad", "application/x-dxf", "drawing/x-dxf", "image/vnd.dxf", "image/x-autocad", "image/x-dxf", "zz-application/zz-winassoc-dxf",
            // zip: https://filext.com/file-extension/ZIP
            "application/zip", "application/x-zip", "application/x-zip-compressed", "application/x-compress", "application/x-compressed", "multipart/x-zip",
            // WPD
            "application/vnd.wordperfect"
    };
    private static final String[] MIME_BLACKLIST = {"image/x-tga", "image/vnd.djvu", "image/g3fax", "audio/amr", "text/calendar", "text/vcard", "video/3gpp"};

    public static final String GOOGLE_VIEWER_URL = "https://docs.google.com/viewer?embedded=true&url=";
    public static final String MICROSOFT_VIEWER_URL = "https://view.officeapps.live.com/op/view.aspx?src=";

    private final CoreLoader coreLoader;

    public OnlineLoader(Context context, CoreLoader coreLoader) {
        super(context, LoaderType.ONLINE);
        this.coreLoader = coreLoader;
    }

    @Override
    public boolean isSupported(Options options) {
        String fileType = options.fileType;

        for (String mime : MIME_WHITELIST) {
            if (!fileType.startsWith(mime)) {
                continue;
            }

            for (String blackMime : MIME_BLACKLIST) {
                if (fileType.startsWith(blackMime)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            Uri viewerUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ("text/rtf".equals(options.fileType) || "application/vnd.wordperfect".equals(options.fileType) || coreLoader.isSupported(options) || "application/vnd.ms-excel".equals(options.fileType) || "application/msword".equals(options.fileType) || "application/vnd.ms-powerpoint".equals(options.fileType) || options.fileType.startsWith("application/vnd.openxmlformats-officedocument.") || options.fileType.equals("application/pdf"))) {
                viewerUri = doOnlineConvert(options);
            } else {
                viewerUri = doTransferUpload(options);
            }

            result.partTitles.add(null);
            result.partUris.add(viewerUri);

            callOnSuccess(result);
        } catch (Throwable e) {
            callOnError(result, e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Uri doOnlineConvert(Options options) throws IOException {
        // https://stackoverflow.com/a/2469587/198996
        String basePath = "https://use.opendocument.app";
        String url = basePath + "/upload"; // TODO: /v1
        String charset = "UTF-8";
        File binaryFile = AndroidFileCache.getCacheFile(context, options.cacheUri);
        String boundary = Long.toHexString(System.currentTimeMillis());
        String CRLF = "\r\n";

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setInstanceFollowRedirects(false);

        try (
                OutputStream output = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true)
        ) {
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"document\"").append(CRLF);
            writer.append(CRLF).flush();
            Files.copy(binaryFile.toPath(), output);
            output.flush();
            writer.append(CRLF).flush();

            writer.append("--" + boundary + "--").append(CRLF).flush();
        }

        String redirectUrl = connection.getHeaderField("Location");
        return Uri.parse(basePath + redirectUrl);
    }

    private Uri doTransferUpload(Options options) throws IOException {
        File binaryFile = AndroidFileCache.getCacheFile(context, options.cacheUri);
        String filename = options.filename;
        String encodedFilename = URLEncoder.encode(filename, StreamUtil.ENCODING);

        HttpURLConnection connection = (HttpURLConnection) new URL(TRANSFER_BASE_URL + encodedFilename).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);

        try (OutputStream outputStream = connection.getOutputStream()) {
            Files.copy(binaryFile.toPath(), outputStream);
            outputStream.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            String downloadUrl = readBody(connection);
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new IOException("server couldn't handle request");
            }

            return buildViewerUri(options, downloadUrl.trim());
        } else {
            String error = readError(connection);
            throw new IOException("server couldn't handle request: " + responseCode + " " + error);
        }
    }

    private Uri buildViewerUri(Options options, String downloadUrl) throws UnsupportedEncodingException {
        if (coreLoader.isSupported(options)) {
            // ODF does not seem to be supported by google docs viewer
            return Uri.parse(MICROSOFT_VIEWER_URL + downloadUrl);
        } else {
            return Uri.parse(GOOGLE_VIEWER_URL + URLEncoder.encode(downloadUrl, StreamUtil.ENCODING));
        }
    }

    private String readBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getInputStream();
        if (inputStream == null) {
            return null;
        }

        return StreamUtil.readFully(inputStream);
    }

    private String readError(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream == null) {
                return null;
            }

            return StreamUtil.readFully(errorStream);
        } catch (Throwable t) {
            return null;
        }
    }
}
