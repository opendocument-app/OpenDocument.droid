package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.UUID;

import at.tomtasche.reader.background.Document.Page;

public class UpLoader implements FileLoader, OnProgressListener<UploadTask.TaskSnapshot> {

    private Context context;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Handler mainHandler;

    private FileLoaderListener listener;

    private boolean initialized;
    private boolean loading;

    private int progress;

    private StorageReference storage;
    private FirebaseAuth auth;

    public UpLoader(Context context) {
        this.context = context;
    }

    @Override
    public void initialize(FileLoaderListener listener) {
        this.listener = listener;

        storage = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();

        mainHandler = new Handler();

        backgroundThread = new HandlerThread(DocumentLoader.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        initialized = true;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public boolean isLoading() {
        return loading;
    }

    @Override
    public void loadAsync(Uri uri, String fileType, String password, boolean limit, boolean translatable) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                loadSync(uri, fileType, password, limit, translatable);
            }
        });
    }

    @Override
    public void loadSync(Uri uri, String fileType, String password, boolean limit, boolean translatable) {
        if (!initialized) {
            throw new RuntimeException("not initialized");
        }

        loading = true;
        progress = 0;

        Task<AuthResult> authenticationTask = null;
        String currentUserId = null;
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        } else {
            authenticationTask = auth.signInAnonymously();
        }

        InputStream stream = null;
        try {
            if (authenticationTask != null) {
                Tasks.await(authenticationTask);

                currentUserId = authenticationTask.getResult().getUser().getUid();
            }

            stream = context.getContentResolver().openInputStream(uri);

            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);
            StorageReference reference = storage.child("uploads/" + currentUserId + "/" + UUID.randomUUID() + "." + fileExtension);

            UploadTask uploadTask = reference.putStream(stream);
            uploadTask.addOnProgressListener(this);
            Tasks.await(uploadTask);

            if (uploadTask.isSuccessful()) {
                Task<Uri> urlTask = reference.getDownloadUrl();
                Tasks.await(urlTask);

                String downloadUrl = urlTask.getResult().toString();

                URI viewerUri = URI
                        .create("https://docs.google.com/viewer?embedded=true&url="
                                + URLEncoder.encode(downloadUrl, "UTF-8"));

                Document document = new Document(null);
                document.addPage(new Page("Document", viewerUri, 0));

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        FileLoaderListener strongReferenceListener = listener;
                        if (strongReferenceListener != null) {
                            strongReferenceListener.onSuccess(document, null);
                        }
                    }
                });
            } else {
                throw new RuntimeException("server couldn't handle request");
            }
        } catch (Throwable e) {
            e.printStackTrace();
w
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    FileLoaderListener strongReferenceListener = listener;
                    if (strongReferenceListener != null) {
                        strongReferenceListener.onError(e, null);
                    }
                }
            });
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
            }
        }

        loading = false;
    }

    @Override
    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
        try {
            progress = (int) (taskSnapshot.getTotalByteCount() / taskSnapshot.getTotalByteCount());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initialized = false;
                listener = null;
                context = null;
                auth = null;
                storage = null;

                backgroundThread.quit();
                backgroundThread = null;

                backgroundHandler = null;
            }
        });
    }
}
