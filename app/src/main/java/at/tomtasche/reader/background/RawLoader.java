package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;

public class RawLoader extends FileLoader {

    private static final String[] MIME_WHITELIST = {"text/", "image/", "video/", "audio/", "application/json", "application/xml", "application/zip"};
    private static final String[] MIME_BLACKLIST = {"image/vnd.dwg", "image/g3fax", "image/tiff", "image/vnd.djvu", "image/x-eps", "image/x-tga", "image/x-tga", "audio/amr", "video/3gpp", "video/quicktime", "text/calendar", "text/vcard"};

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
                StreamUtil.copy(htmlStream, htmlFile);

                // use jpg as a workaround for most images
                extension = "jpg";
                if (fileType.contains("svg")) {
                    // browser does not recognize SVG if it's not called ".svg"
                    extension = "svg";
                }

                File imageFile = new File(cacheDirectory, "image." + extension);
                StreamUtil.copy(cacheFile, imageFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();
            } else if (fileType.startsWith("audio/")) {
                File htmlFile = new File(cacheDirectory, "audio.html");
                InputStream htmlStream = context.getAssets().open("audio.html");
                StreamUtil.copy(htmlStream, htmlFile);

                // use mp3 as a workaround for most images
                extension = "mp3";

                File audioFile = new File(cacheDirectory, "audio." + extension);
                StreamUtil.copy(cacheFile, audioFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();
            } else if (fileType.startsWith("video/")) {
                File htmlFile = new File(cacheDirectory, "video.html");
                InputStream htmlStream = context.getAssets().open("video.html");
                StreamUtil.copy(htmlStream, htmlFile);

                // use mp4 as a workaround for most images
                extension = "mp4";

                File videoFile = new File(cacheDirectory, "video." + extension);
                StreamUtil.copy(cacheFile, videoFile);

                finalUri = Uri.fromFile(htmlFile).buildUpon().appendQueryParameter("ext", extension).build();
            } else if (fileType.startsWith("text/")) {
                File htmlFile = new File(cacheDirectory, "text.html");
                InputStream htmlPrefixStream = context.getAssets().open("text-prefix.html");
                InputStream htmlSuffixStream = context.getAssets().open("text-suffix.html");

                OutputStream outputStream = new FileOutputStream(htmlFile);
                try {
                    StreamUtil.copy(htmlPrefixStream, outputStream);

                    FileReader fileReader = new FileReader(cacheFile);
                    BufferedReader bufferedReader = new BufferedReader(fileReader);
                    for (String s = bufferedReader.readLine(); s != null; s = bufferedReader.readLine()) {
                        outputStream.write((s + "XODRX").getBytes(StreamUtil.ENCODING));
                    }

                    StreamUtil.copy(htmlSuffixStream, outputStream);
                } finally {
                    outputStream.close();
                }

                File fontFile = new File(cacheDirectory, "text.ttf");
                InputStream fontStream = context.getAssets().open("text.ttf");
                StreamUtil.copy(fontStream, fontFile);

                finalUri = Uri.fromFile(htmlFile);
            } else if (fileType.startsWith("application/zip")) {
                File htmlFile = new File(cacheDirectory, "zip.html");
                InputStream htmlPrefixStream = context.getAssets().open("zip-prefix.html");
                InputStream htmlSuffixStream = context.getAssets().open("zip-suffix.html");

                OutputStream outputStream = new FileOutputStream(htmlFile);
                try {
                    StreamUtil.copy(htmlPrefixStream, outputStream);

                    // need to store it in a separate file first because BaseStream writes characters on close
                    FileInputStream fileInputStream = new FileInputStream(cacheFile);
                    File baseFile = new File(cacheDirectory, "tmp");
                    OutputStream baseFileOutputStream = new FileOutputStream(baseFile);
                    Base64OutputStream baseOutputStream = new Base64OutputStream(baseFileOutputStream, Base64.NO_WRAP);
                    try {
                        StreamUtil.copy(fileInputStream, baseOutputStream);
                    } finally {
                        baseOutputStream.close();
                    }

                    InputStream baseFileInputStream = new FileInputStream(baseFile);
                    StreamUtil.copy(baseFileInputStream, outputStream);

                    StreamUtil.copy(htmlSuffixStream, outputStream);
                } finally {
                    outputStream.close();
                }

                finalUri = Uri.fromFile(htmlFile);
            } else {
                File renamedFile = new File(cacheDirectory, "temp." + extension);
                StreamUtil.copy(cacheFile, renamedFile);

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
}
