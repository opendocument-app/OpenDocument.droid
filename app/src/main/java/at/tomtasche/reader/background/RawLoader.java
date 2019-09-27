package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RawLoader extends FileLoader {

    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/", "application/json", "application/xml"};
    private static final String[] MIME_BLACKLIST = {"image/vnd.dwg", "image/g3fax", "image/tiff", "image/vnd.djvu", "image/x-eps", "image/x-tga", "image/x-tga", "audio/amr", "video/3gpp", "video/quicktime"};

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
            File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

            Uri finalUri;
            if (fileType.startsWith("image/")) {
                File htmlFile = new File(cacheDirectory, "image.html");
                InputStream htmlStream = context.getAssets().open("image.html");
                copy(htmlStream, htmlFile);

                // use jpg as a workaround for most images
                extension = "jpg";
                if (fileType.contains("svg")) {
                    // browser does not recognize SVG if it's not called ".svg"
                    extension = "svg";
                }

                File imageFile = new File(cacheDirectory, "image." + extension);
                copy(cacheFile, imageFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();
            } else if (fileType.startsWith("audio/")) {
                File htmlFile = new File(cacheDirectory, "audio.html");
                InputStream htmlStream = context.getAssets().open("audio.html");
                copy(htmlStream, htmlFile);

                // use mp3 as a workaround for most images
                extension = "mp3";

                File audioFile = new File(cacheDirectory, "audio." + extension);
                copy(cacheFile, audioFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();
            } else if (fileType.startsWith("video/")) {
                File htmlFile = new File(cacheDirectory, "video.html");
                InputStream htmlStream = context.getAssets().open("video.html");
                copy(htmlStream, htmlFile);

                // use mp4 as a workaround for most images
                extension = "mp4";

                File videoFile = new File(cacheDirectory, "video." + extension);
                copy(cacheFile, videoFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();

            } else {
                File renamedFile = new File(cacheDirectory, "temp." + extension);
                copy(cacheFile, renamedFile);

                finalUri = Uri.fromFile(renamedFile);
            }

            result.partTitles.add(null);
            result.partUris.add(finalUri);
            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            callOnError(result, e);
        }
    }

    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        copy(in, dst);
    }

    // taken from: https://stackoverflow.com/a/9293885/198996
    private void copy(InputStream in, File dst) throws IOException {
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
