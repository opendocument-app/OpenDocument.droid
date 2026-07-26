package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RecentDocumentsUtil {

    private static final String FILENAME = "recent_documents.json";

    /**
     * The recently opened documents, oldest first. Insertion ordered - a HashMap used to hand them
     * back in an arbitrary order, which made the "recent" list not actually ordered by recency.
     */
    public static Map<String, String> getRecentDocuments(Context context)
            throws IOException, JSONException {
        Map<String, String> result = new LinkedHashMap<>();

        JSONArray jsonArray = getRecentDocumentsJson(context);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject document = jsonArray.getJSONObject(i);
            result.put(document.getString("filename"), document.getString("uri"));
        }

        return result;
    }

    private static JSONArray getRecentDocumentsJson(Context context)
            throws IOException, JSONException {
        try (InputStream input = context.openFileInput(FILENAME);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                builder.append(line);
            }

            return new JSONArray(builder.toString());
        }
    }

    public static void addRecentDocument(Context context, String title, Uri uri)
            throws IOException, JSONException {
        if (title == null) {
            return;
        }

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
        try (OutputStream output = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
                Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(jsonArray.toString());
        }
    }

    public static void removeRecentDocument(Context context, String title, Uri uri)
            throws IOException, JSONException {
        if (title == null) {
            return;
        }

        JSONArray jsonArray = getRecentDocumentsJson(context);
        int deleteIndex = findUriIndex(uri.toString(), jsonArray);

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
