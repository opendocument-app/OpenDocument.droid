package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.hzy.libmagic.MagicApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public class MetadataLoader extends FileLoader {

    public MetadataLoader(Context context) {
        super(context, LoaderType.METADATA);
    }

    private boolean initMagicFromAssets() {
        try {
            InputStream inputStream = context.getAssets().open("magic.mgc");
            int length = inputStream.available();
            byte[] buffer = new byte[length];
            if (inputStream.read(buffer) > 0) {
                return MagicApi.loadFromBytes(buffer, MagicApi.MAGIC_MIME_TYPE | MagicApi.MAGIC_COMPRESS_TRANSP) == 0;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean isSupported(Options options) {
        return true;
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        options.fileType = "N/A";

        Uri uri = options.originalUri;

        try {
            // cleanup uri
            if ("/./".equals(uri.toString().substring(0, 2))) {
                uri = Uri.parse(uri.toString().substring(2,
                        uri.toString().length()));
            }

            AndroidFileCache cache = new AndroidFileCache(context);
            // TODO: don't delete file being displayed at the moment, but
            // keep it until the new document has finished loading.
            // this must not delete document.odt!
            AndroidFileCache.cleanup(context);

            File cachedFile;
            if (uri.equals(AndroidFileCache.getCacheFileUri())) {
                cachedFile = AndroidFileCache.getCacheFile(context);
            } else {
                InputStream stream = context.getContentResolver().openInputStream(uri);
                cachedFile = cache.create("document.odt", stream);

                options.cacheUri = AndroidFileCache.getCacheFileUri();
            }

            String filename = null;
            try {
                // https://stackoverflow.com/a/38304115/198996
                Cursor fileCursor = context.getContentResolver().query(uri, null, null, null, null);
                if (fileCursor != null && fileCursor.moveToFirst()) {
                    int nameIndex = fileCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    filename = fileCursor.getString(nameIndex);
                    fileCursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();

                // "URI does not contain a valid access token." or
                // "Couldn't read row 0, col -1 from CursorWindow. Make sure the Cursor is initialized correctly before accessing data from it."
            }

            if (filename == null) {
                filename = uri.getLastPathSegment();
            }

            if (filename == null) {
                filename = "N/A";
            }

            options.filename = filename;

            String[] fileSplit = options.filename.split("\\.");
            options.fileExtension = fileSplit.length > 0 ? fileSplit[fileSplit.length - 1] : "N/A";

            String type = null;
            try {
                if (initMagicFromAssets()) {
                    type = MagicApi.magicFile(cachedFile.getAbsolutePath());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }

            if (type == null) {
                type = context.getContentResolver().getType(uri);
            }

            if (type == null && filename != null) {
                try {
                    type = URLConnection.guessContentTypeFromName(filename);
                } catch (Exception e) {
                    // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
                    e.printStackTrace();
                }
            }

            if (type == null) {
                try {
                    InputStream tempStream = new FileInputStream(cachedFile);
                    try {
                        type = URLConnection.guessContentTypeFromStream(tempStream);
                    } finally {
                        tempStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (type == null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(options.fileExtension);
            }

            if (type != null) {
                options.fileType = type;
            }

            if ("inode/x-empty".equals(type)) {
                throw new FileNotFoundException();
            }

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(options.fileType);
            if (extension != null) {
                // override extension parsed from filename
                options.fileExtension = extension;
            }

            try {
                RecentDocumentsUtil.addRecentDocument(context, filename, uri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            options.fileType = "N/A";

            callOnError(result, e);
        }
    }

    @Override
    public void close() {
        super.close();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                MagicApi.close();
            }
        });
    }
}
