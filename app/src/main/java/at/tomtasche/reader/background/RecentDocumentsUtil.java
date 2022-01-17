package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

public class RecentDocumentsUtil {

    private static final String FILENAME = "recent_documents.json";

    public static Map<String, String> getRecentDocuments(Context context)
            throws IOException, JSONException {
        Map<String, String> result = new HashMap<String, String>();

        JSONArray jsonArray = getRecentDocumentsJson(context);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject document = jsonArray.getJSONObject(i);
            String filename = document.getString("filename");
            String uri = document.getString("uri");

            result.put(filename, uri);
        }

        return result;
    }

    private static JSONArray getRecentDocumentsJson(Context context)
            throws IOException, JSONException {
        FileInputStream input = null;
        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;
        try {
            input = context.openFileInput(FILENAME);

            reader = new InputStreamReader(input);
            bufferedReader = new BufferedReader(reader);
            StringBuilder builder = new StringBuilder();
            for (String s = bufferedReader.readLine(); s != null; s = bufferedReader
                    .readLine()) {
                builder.append(s);
            }

            return new JSONArray(builder.toString());
        } finally {
            if (bufferedReader != null)
                bufferedReader.close();
            if (reader != null)
                reader.close();
            if (input != null)
                input.close();
        }
    }

    public static void addRecentDocument(Context context, String title, Uri uri)
            throws IOException, JSONException {
        if (title == null)
            return;

        if (AndroidFileCache.isCached(context, uri)) {
            return;
        }

        JSONObject document = new JSONObject();
        document.put("uri", uri.toString());
        document.put("filename", title);

        JSONArray jsonArray;
        try {
            jsonArray = getRecentDocumentsJson(context);

            // avoid duplicates
            removeRecentDocument(context, title, uri);
        } catch (Exception e) {
            jsonArray = new JSONArray();
        }

        jsonArray.put(document);

        saveJson(context, jsonArray);
    }

    private static void saveJson(Context context, JSONArray jsonArray) throws IOException {
        FileOutputStream output = null;
        OutputStreamWriter writer = null;
        try {
            output = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            writer = new OutputStreamWriter(output);
            writer.write(jsonArray.toString());
            writer.flush();
        } finally {
            if (writer != null)
                writer.close();
            if (output != null)
                output.close();
        }
    }

    public static void removeRecentDocument(Context context, String title, Uri uri)
            throws IOException, JSONException {
        if (title == null)
            return;

        String uriString = uri.toString();

        JSONArray jsonArray = getRecentDocumentsJson(context);
        int deleteIndex = findUriIndex(uriString, jsonArray);

        if (deleteIndex >= 0) {
            jsonArray.remove(deleteIndex);
        }

        saveJson(context, jsonArray);
    }

    private static int findUriIndex(String uriString, JSONArray jsonArray) throws JSONException {
        int index = -1;
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject document = jsonArray.getJSONObject(i);
            if (uriString.equals(document.getString("uri"))) {
                index = i;
            }
        }
        return index;
    }
}
