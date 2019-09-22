package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.hzy.libmagic.MagicApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.UUID;

public class OnlineLoader extends FileLoader {

    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/",
            // markup
            "application/json", "application/xml", "text/css", "application/css-stylesheet", "application/xhtml",
            "application/x-httpd-php", "text/php", "application/php", "application/x-php",
            "application/x-javascript", "text/javascript",
            "text/x-java-source", "text/java", "text/x-java", "application/ms-java",
            "application/rtf",
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
            "application/zip", "application/x-zip", "application/x-zip-compressed", "application/x-compress", "application/x-compressed", "multipart/x-zip"
    };
    private static final String[] MIME_BLACKLIST = {};

    private StorageReference storage;
    private FirebaseAuth auth;

    public OnlineLoader(Context context) {
        super(context);
    }

    @Override
    public void initialize(FileLoaderListener listener, Handler mainHandler, Handler backgroundHandler) {
        super.initialize(listener, mainHandler, backgroundHandler);

        storage = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
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
        result.loaderType = LoaderType.FIREBASE;

        Task<AuthResult> authenticationTask = null;
        String currentUserId = null;
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        } else {
            authenticationTask = auth.signInAnonymously();
        }

        try {
            if (authenticationTask != null) {
                Tasks.await(authenticationTask);

                currentUserId = authenticationTask.getResult().getUser().getUid();
            }

            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(options.fileType);
            StorageReference reference = storage.child("uploads/" + currentUserId + "/" + UUID.randomUUID() + "." + fileExtension);

            UploadTask uploadTask = reference.putFile(options.cacheUri);
            //uploadTask.addOnProgressListener(this);
            Tasks.await(uploadTask);

            if (uploadTask.isSuccessful()) {
                Task<Uri> urlTask = reference.getDownloadUrl();
                Tasks.await(urlTask);

                String downloadUrl = urlTask.getResult().toString();
                Uri viewerUri = Uri.parse("https://docs.google.com/viewer?embedded=true&url="
                        + URLEncoder.encode(downloadUrl, "UTF-8"));

                result.partTitles.add(null);
                result.partUris.add(viewerUri);

                callOnSuccess(result);
            } else {
                throw new RuntimeException("server couldn't handle request");
            }
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
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
