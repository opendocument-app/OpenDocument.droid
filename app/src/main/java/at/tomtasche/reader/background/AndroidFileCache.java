package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import at.stefl.opendocument.java.translator.File2URITranslator;
import at.stefl.opendocument.java.util.DefaultFileCache;

public class AndroidFileCache extends DefaultFileCache {

    private static File cache;

    public static final File getCacheDirectory(Context context) {
        File directory = context.getCacheDir();
        directory = new File(directory, "cache");
        directory.mkdirs();

        return cache = directory;
    }

    private static final boolean testDirectory(File directory) {
        return directory != null && directory.canWrite() && directory.canRead();
    }

    private static final File2URITranslator URI_TRANSLATOR = new File2URITranslator() {
        @Override
        public URI translate(File file) {
            URI uri = file.toURI();

            File imageFile = new File(uri);
            String imageFileName = imageFile.getName();

            URI result = null;
            try {
                result = new URI(
                        // use relative paths (important for chromecast-support)
                        // "content://at.tomtasche.reader.provider/" +
                        Uri.encode(imageFileName));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            return result;
        }
    };

    public AndroidFileCache(Context context) {
        super(getCacheDirectory(context), URI_TRANSLATOR);
    }

    public static Uri getCacheFileUri() {
        // hex hex!
        return Uri.parse("content://at.tomtasche.reader.provider/cache/document.odt");
    }

    public static Uri getHtmlCacheFileUri() {
        // hex hex!
        return Uri.parse("content://at.tomtasche.reader.provider/cache/content.html");
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
