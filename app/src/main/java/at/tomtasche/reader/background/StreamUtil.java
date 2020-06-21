package at.tomtasche.reader.background;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {

    public static final String ENCODING = "UTF-8";

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        copy(in, dst);
    }

    public static void copy(File src, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(src);
        copy(in, out);
    }

    public static void copy(InputStream in, File dst) throws IOException {
        OutputStream out = new FileOutputStream(dst);
        copy(in, out);
        out.close();
    }

    // taken from: https://stackoverflow.com/a/9293885/198996
    public static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            out.flush();
        } finally {
            in.close();
        }
    }
}
