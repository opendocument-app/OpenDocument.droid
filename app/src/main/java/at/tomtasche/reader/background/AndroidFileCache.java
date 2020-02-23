package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import java.io.File;

public class AndroidFileCache {

    public static final File getCacheDirectory(Context context) {
        File directory = context.getCacheDir();
        directory = new File(directory, "cache");
        directory.mkdirs();

        return directory;
    }

    public static Uri getCacheFileUri() {
        // hex hex!
        return Uri.parse("content://at.tomtasche.reader.provider/cache/document.odt");
    }

    public static File getCacheFile(Context context) {
        return new File(getCacheDirectory(context), "document.odt");
    }

    public static void cleanup(Context context) {
        File cache = getCacheDirectory(context);
        String[] files = cache.list();
        if (files == null)
            return;

        for (String s : files) {
            try {
                if (!s.equals("document.odt")) {
                    new File(cache, s).delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
