package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

// TODO: rewrite this class. it's a fucking mess!
public class RecentDocumentsUtil {

    private static final String FILENAME = "recent_documents";

    public static Map<String, String> getRecentDocuments(Context context)
            throws IOException {
        Map<String, String> result = new HashMap<String, String>();

        FileInputStream input = null;
        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;
        try {
            input = context.openFileInput(FILENAME);

            reader = new InputStreamReader(input);
            bufferedReader = new BufferedReader(reader);
            for (String s = bufferedReader.readLine(); s != null; s = bufferedReader
                    .readLine()) {
                String[] temp = s.split(";;;");
                if (temp.length == 2) {
                    if (temp[0].length() == 0 || temp[1].length() == 0) {
                        continue;
                    }

                    result.put(temp[0], temp[1]);
                }
            }
        } finally {
            if (input != null)
                input.close();
            if (reader != null)
                reader.close();
            if (bufferedReader != null)
                bufferedReader.close();
        }

        return result;
    }

    public static void addRecentDocument(Context context, String title, Uri uri)
            throws IOException {
        if (title == null)
            return;

        if (AndroidFileCache.getCacheFileUri().equals(uri)) {
            return;
        }

        FileOutputStream output = null;
        OutputStreamWriter writer = null;
        try {
            output = context.openFileOutput(FILENAME, Context.MODE_APPEND);
            writer = new OutputStreamWriter(output);
            writer.append(System.getProperty("line.separator")).append(title)
                    .append(";;;").append(uri.toString());
            writer.flush();
        } finally {
            if (output != null)
                output.close();
            if (writer != null)
                writer.close();
        }
    }

    public static void removeRecentDocument(Context context, String title)
            throws IOException {
        if (title == null)
            return;

        FileOutputStream output = null;
        OutputStreamWriter writer = null;

        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;

        try {
            reader = new InputStreamReader(context.openFileInput(FILENAME));
            bufferedReader = new BufferedReader(reader);

            output = context.openFileOutput(FILENAME, 0);
            writer = new OutputStreamWriter(output);

            for (String s = bufferedReader.readLine(); s != null; s = bufferedReader
                    .readLine()) {
                if (s.contains(title)) {
                    continue;
                } else {
                    writer.append(System.getProperty("line.separator")).append(s);
                }
            }
        } finally {
            if (reader != null)
                reader.close();
            if (bufferedReader != null)
                bufferedReader.close();

            if (writer != null)
                writer.close();
        }
    }
}
