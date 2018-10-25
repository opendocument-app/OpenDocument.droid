package at.tomtasche.reader.background;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
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

public class UpLoader extends AsyncTaskLoader<Document> implements FileLoader {

    private Uri uri;
    private Document document;
    private Throwable lastError;

    private final StorageReference storage;
    private final FirebaseAuth auth;

    public UpLoader(Context context, Uri uri) {
        super(context);

        this.uri = uri;

        storage = FirebaseStorage.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public Throwable getLastError() {
        return lastError;
    }

    @Override
    public Uri getLastUri() {
        return uri;
    }

    @Override
    public double getProgress() {
        return 0;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if (document != null) {
            deliverResult(document);
        } else {
            forceLoad();
        }
    }

    @Override
    protected void onReset() {
        super.onReset();

        onStopLoading();

        document = null;
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();

        cancelLoad();
    }

    @Override
    public Document loadInBackground() {
        if (uri == DocumentLoader.URI_INTRO) {
            cancelLoad();

            return null;
        }

        Task<AuthResult> authenticationTask = null;
        String currentUserId = null;
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        } else {
            authenticationTask = auth.signInAnonymously();
        }

        String filename = null;
        // https://stackoverflow.com/a/38304115/198996
        Cursor fileCursor = getContext().getContentResolver().query(uri, null, null, null, null);
        if (fileCursor != null) {
            int nameIndex = fileCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            fileCursor.moveToFirst();
            filename = fileCursor.getString(nameIndex);
            fileCursor.close();
        }

        String type = getContext().getContentResolver().getType(uri);
        if (type == null && filename != null) {
            type = URLConnection.guessContentTypeFromName(filename);
        }

        if (type == null) {
            try {
                InputStream stream = getContext().getContentResolver()
                        .openInputStream(uri);
                try {
                    type = URLConnection.guessContentTypeFromStream(stream);
                } finally {
                    stream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (type != null
                && (type.equals("text/html") || type.equals("text/plain")
                || type.equals("image/png") || type.equals("image/jpeg"))) {
            try {
                document = new Document(null);
                document.addPage(new Page("Document", new URI(uri.toString()),
                        0));

                return document;
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        InputStream stream = null;
        try {
            if (authenticationTask != null) {
                Tasks.await(authenticationTask);

                currentUserId = authenticationTask.getResult().getUser().getUid();
            }

            stream = getContext().getContentResolver().openInputStream(uri);

            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            StorageReference reference = storage.child("uploads/" + currentUserId + "/" + UUID.randomUUID() + "." + fileExtension);

            UploadTask uploadTask = reference.putStream(stream);
            Tasks.await(uploadTask);

            if (uploadTask.isSuccessful()) {
                Task<Uri> urlTask = reference.getDownloadUrl();
                Tasks.await(urlTask);

                String downloadUrl = urlTask.getResult().toString();

                URI viewerUri = URI
                        .create("https://docs.google.com/viewer?embedded=true&url="
                                + URLEncoder.encode(downloadUrl, "UTF-8"));

                document = new Document(null);
                document.addPage(new Page("Document", viewerUri, 0));
            } else {
                throw new RuntimeException("server couldn't handle request");
            }
        } catch (Throwable e) {
            e.printStackTrace();

            lastError = e;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
            }
        }

        return document;
    }
}
