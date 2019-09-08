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
    public void loadSync(Options options) {
        if (!initialized) {
            throw new RuntimeException("not initialized");
        }

        loading = true;

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

                result.partUris.add(viewerUri);

                callOnSuccess(result);
            } else {
                throw new RuntimeException("server couldn't handle request");
            }
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
        }

        loading = false;
    }

    @Override
    public void close() {
        super.close();

        auth = null;
        storage = null;
    }
}
