package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RawLoader extends FileLoader {

    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/", "application/json", "application/xml"};
    private static final String[] MIME_BLACKLIST = {};

    public RawLoader(Context context) {
        super(context);
    }

    @Override
    public boolean isSupported(Options options) {
        String fileType = options.fileType;

        for (String mime : MIME_WHITELIST) {
            if (!fileType.startsWith(mime)) {
                continue;
            }

            for (String blackMime : MIME_BLACKLIST) {
                if (fileType.startsWith(blackMime)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = LoaderType.RAW;

        try {
            String fileType = options.fileType;

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);
            if (extension == null || extension.equals("csv")) {
                // WebView doesn't display CSV if it has that extension
                extension = "txt";
            }

            // TODO: use options.cacheUri instead
            File cacheFile = AndroidFileCache.getCacheFile(context);
            File renamedFile = new File(cacheFile.getParentFile(), "temp." + extension);

            try {
                copy(cacheFile, renamedFile);
            } catch (IOException e) {
                e.printStackTrace();

                // there's no point in trying to open a file called "document.odt" in the browser
                callOnError(result, e);
                return;
            }

            result.partTitles.add(null);
            result.partUris.add(Uri.fromFile(renamedFile));
            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
        }
    }

    // taken from: https://stackoverflow.com/a/9293885/198996
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
