package at.tomtasche.reader.background;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public class MetadataLoader extends FileLoader {

    private static final MimeTypeResolver.ExtensionLookup MIME_TYPE_LOOKUP =
            new MimeTypeResolver.ExtensionLookup() {

                @Override
                public String extensionFromMimeType(String mimeType) {
                    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                }

                @Override
                public String mimeTypeFromExtension(String extension) {
                    if (extension == null) {
                        return null;
                    }

                    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            };

    public MetadataLoader(Context context) {
        super(context, LoaderType.METADATA);
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
            String uriString = uri.toString();
            if (uriString.startsWith("/./")) {
                uri = Uri.parse(uriString.substring(2));
            }

            AndroidFileCache.cleanup(context);

            // detecting the filename early so we can use it in the catch-block if something goes
            // wrong
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
                // "Couldn't read row 0, col -1 from CursorWindow. Make sure the Cursor is
                // initialized correctly before accessing data from it."

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

            String extension = MimeTypeResolver.parseExtension(options.filename);

            String mimetype = null;
            try {
                mimetype = CoreWrapper.mimetype(cachedFile.getAbsolutePath());
            } catch (Throwable e) {
                crashManager.log(e);
            }

            if (mimetype == null) {
                mimetype = context.getContentResolver().getType(uri);
            }

            if (mimetype == null) {
                try {
                    mimetype = URLConnection.guessContentTypeFromName(filename);
                } catch (Exception e) {
                    // Samsung S7 Edge crashes with java.lang.StringIndexOutOfBoundsException
                    crashManager.log(e);
                }
            }

            if (mimetype == null) {
                try {
                    try (InputStream tempStream = new FileInputStream(cachedFile)) {
                        mimetype = URLConnection.guessContentTypeFromStream(tempStream);
                    }
                } catch (Exception e) {
                    crashManager.log(e);
                }
            }

            MimeTypeResolver.Resolution resolution =
                    MimeTypeResolver.resolve(mimetype, extension, MIME_TYPE_LOOKUP);
            mimetype = resolution.mimeType;

            if (resolution.extension != null) {
                options.fileExtension = resolution.extension;
            }
            if (mimetype != null) {
                options.fileType = mimetype;
            }

            if ("inode/x-empty".equals(mimetype)) {
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
                RecentDocumentsUtil.removeRecentDocument(
                        context, options.filename, options.originalUri);
            } catch (Exception e1) {
                crashManager.log(e1);
            }

            callOnError(result, e);
        }
    }
}
