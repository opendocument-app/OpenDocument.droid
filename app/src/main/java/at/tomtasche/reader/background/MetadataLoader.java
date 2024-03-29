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
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open("magic.mgc");
            int length = inputStream.available();
            byte[] buffer = new byte[length];
            if (inputStream.read(buffer) > 0) {
                return MagicApi.loadFromBytes(buffer, MagicApi.MAGIC_MIME_TYPE | MagicApi.MAGIC_COMPRESS_TRANSP) == 0;
            }
        } catch (Throwable e) {
            crashManager.log(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    crashManager.log(e);
                }
            }
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
                uri = Uri.parse(uri.toString().substring(2));
            }

            AndroidFileCache.cleanup(context);

            // detecting the filename early so we can use it in the catch-block if something goes wrong
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
                // "URI does not contain a valid access token." or
                // "Couldn't read row 0, col -1 from CursorWindow. Make sure the Cursor is initialized correctly before accessing data from it."

                crashManager.log(e);
            }

            if (filename == null) {
                filename = uri.getLastPathSegment();
            }

            if (filename == null) {
                filename = "N/A";
            }

            options.filename = filename;

            File cachedFile;
            if (AndroidFileCache.isCached(context, uri)) {
                cachedFile = AndroidFileCache.getCacheFile(context, uri);
            } else {
                cachedFile = AndroidFileCache.createCacheFile(context);

                InputStream stream = context.getContentResolver().openInputStream(uri);
                StreamUtil.copy(stream, cachedFile);
            }

            // if file didn't exist an exception would have been thrown by now
            options.fileExists = true;

            options.cacheUri = AndroidFileCache.getCacheFileUri(context, cachedFile);

            String[] fileSplit = options.filename.split("\\.");
            String extension = fileSplit.length > 0 ? fileSplit[fileSplit.length - 1] : "N/A";

            String type = null;
            try {
                if (initMagicFromAssets()) {
                    type = MagicApi.magicFile(cachedFile.getAbsolutePath());
                }
            } catch (Throwable e) {
                crashManager.log(e);
            }

            if (type == null) {
                type = context.getContentResolver().getType(uri);
            }

            if (type == null) {
                try {
                    type = URLConnection.guessContentTypeFromName(filename);
                } catch (Exception e) {
                    // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
                    crashManager.log(e);
                }
            }

            if (type == null) {
                try {
                    try (InputStream tempStream = new FileInputStream(cachedFile)) {
                        type = URLConnection.guessContentTypeFromStream(tempStream);
                    }
                } catch (Exception e) {
                    crashManager.log(e);
                }
            }

            if (type != null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            } else {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(options.fileExtension);
            }

            if (extension != null) {
                options.fileExtension = extension;
            }
            if (type != null) {
                options.fileType = type;
            }

            if ("inode/x-empty".equals(type)) {
                throw new FileNotFoundException();
            }

            if (options.persistentUri) {
                try {
                    RecentDocumentsUtil.addRecentDocument(context, filename, uri);
                } catch (IOException e) {
                    crashManager.log(e);
                }
            }

            callOnSuccess(result);
        } catch (Throwable e) {
            options.fileType = "N/A";

            try {
                RecentDocumentsUtil.removeRecentDocument(context, options.filename, options.originalUri);
            } catch (Exception e1) {
                crashManager.log(e1);
            }

            callOnError(result, e);
        }
    }

    @Override
    public void close() {
        super.close();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    MagicApi.close();
                } catch (Throwable e) {
                    crashManager.log(e);
                }
            }
        });
    }
}
