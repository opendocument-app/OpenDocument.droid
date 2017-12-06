package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.webkit.MimeTypeMap;

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

    private StorageReference storage;

    public UpLoader(Context context, Uri uri) {
        super(context);

        this.uri = uri;

        storage = FirebaseStorage.getInstance().getReference();
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
        if (type == null)
            type = URLConnection.guessContentTypeFromName(filename);

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
            stream = getContext().getContentResolver().openInputStream(uri);

            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            StorageReference reference = storage.child("uploads/" + UUID.randomUUID() + "." + fileExtension);

            UploadTask uploadTask = reference.putStream(stream);
            while (!uploadTask.isComplete()) {
                Thread.sleep(500);
            }

            if (uploadTask.isSuccessful()) {
                UploadTask.TaskSnapshot result = uploadTask.getResult();
                String downloadUrl = result.getDownloadUrl().toString();

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
