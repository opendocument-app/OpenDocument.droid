package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.ConfigManager;
import at.tomtasche.reader.nonfree.CrashManager;

public class OnlineLoader extends FileLoader {

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

    private OdfLoader odfLoader;

    private StorageReference storage;
    private FirebaseAuth auth;

    public OnlineLoader(Context context, OdfLoader odfLoader) {
        super(context, LoaderType.ONLINE);
        this.odfLoader = odfLoader;
    }

    @Override
    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler, AnalyticsManager analyticsManager, CrashManager crashManager) {
        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager);

        try {
            storage = FirebaseStorage.getInstance().getReference();
            auth = FirebaseAuth.getInstance();
        } catch (Throwable e) {
            crashManager.log(e);
        }
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
                    ("text/rtf".equals(options.fileType) || "application/vnd.wordperfect".equals(options.fileType) || odfLoader.isSupported(options) || "application/vnd.ms-powerpoint".equals(options.fileType))) {
                viewerUri = doOnlineConvert(options);
            } else {
                viewerUri = doFirebaseConvert(options);
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
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
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

    private Uri doFirebaseConvert(Options options) throws ExecutionException, InterruptedException, UnsupportedEncodingException {
        if (auth == null || storage == null) {
            throw new RuntimeException("firebase not initialized");
        }

        Task<AuthResult> authenticationTask = null;
        String currentUserId = null;
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        } else {
            authenticationTask = auth.signInAnonymously();
        }

        if (authenticationTask != null) {
            Tasks.await(authenticationTask);

            currentUserId = authenticationTask.getResult().getUser().getUid();
        }

        StorageMetadata.Builder metadataBuilder = new StorageMetadata.Builder();
        if (!"N/A".equals(options.fileType)) {
            metadataBuilder.setContentType(options.fileType);
        }

        String filePath = currentUserId + "/" + UUID.randomUUID() + "." + options.fileExtension;
        StorageReference reference = storage.child("uploads/" + filePath);
        UploadTask uploadTask = reference.putFile(options.cacheUri, metadataBuilder.build());
        Tasks.await(uploadTask);

        if (uploadTask.isSuccessful()) {
            Uri viewerUri;
            if (odfLoader.isSupported(options)) {
                // ODF does not seem to be supported by google docs viewer
                String downloadUrl = "https://us-central1-admob-app-id-9025061963.cloudfunctions.net/download?filePath=" + filePath;

                viewerUri = Uri.parse(MICROSOFT_VIEWER_URL + downloadUrl);
            } else {
                Task<Uri> urlTask = reference.getDownloadUrl();
                Tasks.await(urlTask);
                String downloadUrl = urlTask.getResult().toString();

                viewerUri = Uri.parse(GOOGLE_VIEWER_URL + URLEncoder.encode(downloadUrl, StreamUtil.ENCODING));
            }

            return viewerUri;
        } else {
            throw new RuntimeException("server couldn't handle request");
        }
    }

    @Override
    public void close() {
        super.close();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                auth = null;
                storage = null;
            }
        });
    }
}
