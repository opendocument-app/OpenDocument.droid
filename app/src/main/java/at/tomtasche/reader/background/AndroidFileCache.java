package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.Arrays;

import androidx.core.content.FileProvider;

public class AndroidFileCache {

    private final static String CACHE_DIRECTORY_PREFIX = "cache.";

    private static File getRootCacheDirectory(Context context) {
        File cache = new File(context.getCacheDir(), "cache");
        if (!cache.exists()) {
            cache.mkdirs();
        }

        return cache;
    }

    public static File getCacheDirectory(File cacheFile) {
        File parentDirectory = cacheFile.getParentFile();
        if (!parentDirectory.getName().startsWith(CACHE_DIRECTORY_PREFIX)) {
            return getCacheDirectory(parentDirectory);
        }

        return parentDirectory;
    }

    private static String parseCacheFileName(String path) {
        return path.substring(path.indexOf(CACHE_DIRECTORY_PREFIX));
    }

    public static Uri getCacheFileUri(Context context, File file) {
        return FileProvider.getUriForFile(context, "at.tomtasche.reader.provider", file);
    }

    public static boolean isCached(Uri uri) {
        return uri.getHost().equals("at.tomtasche.reader.provider");
    }

    public static File getCacheFile(Context context, Uri uri) {
        if (!isCached(uri)) {
            return null;
        }

        String cacheFileString = parseCacheFileName(uri.toString());

        return new File(getRootCacheDirectory(context), cacheFileString);
    }

    public static File createCacheFile(Context context) {
        File cacheRoot = getRootCacheDirectory(context);
        File cacheDirectory = new File(cacheRoot, CACHE_DIRECTORY_PREFIX + System.currentTimeMillis());

        cacheDirectory.mkdirs();

        return new File(cacheDirectory, "cached-file.tmp");
    }

    public static void cleanup(Context context) {
        File cache = getRootCacheDirectory(context);
        String[] directories = cache.list((file, s) -> {
            return s.startsWith(CACHE_DIRECTORY_PREFIX);
        });

        if (directories == null) {
            return;
        }

        Arrays.sort(directories);
        // delete all but the last cache directories!
        for (int i = 0; i < directories.length - 1; i++) {
            String directoryName = directories[i];
            cleanup(new File(cache, directoryName));
        }
    }

    private static void cleanup(File directory) {
        String[] files = directory.list();
        if (files == null) {
            return;
        }

        for (String s : files) {
            try {
                new File(directory, s).delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            directory.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
