package at.tomtasche.reader.background;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class FileProvider extends ContentProvider {

    @Override
    public ParcelFileDescriptor openFile(final Uri uri, final String mode)
            throws FileNotFoundException {
        final File file = new File(
                AndroidFileCache.getCacheDirectory(getContext()),
                uri.getLastPathSegment());

        final ParcelFileDescriptor parcel = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
        return parcel;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection,
                        final String selection, final String[] selectionArgs,
                        final String sortOrder) {
        return null;
    }

    @Override
    public String getType(final Uri uri) {
        return null;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        return null;
    }

    @Override
    public int delete(final Uri uri, final String selection,
                      final String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(final Uri uri, final ContentValues values,
                      final String selection, final String[] selectionArgs) {
        return 0;
    }
}
