package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

object RecentDocumentsUtil {

    private const val FILENAME = "recent_documents.json"

    /**
     * The recently opened documents, oldest first. Insertion ordered - a HashMap used to hand them
     * back in an arbitrary order, which made the "recent" list not actually ordered by recency.
     */
    fun getRecentDocuments(context: Context): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        val jsonArray = getRecentDocumentsJson(context)
        for (i in 0 until jsonArray.length()) {
            val document = jsonArray.getJSONObject(i)
            result[document.getString("filename")] = document.getString("uri")
        }

        return result
    }

    private fun getRecentDocumentsJson(context: Context): JSONArray {
        context.openFileInput(FILENAME).use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                return JSONArray(reader.readText())
            }
        }
    }

    fun addRecentDocument(context: Context, title: String?, uri: Uri) {
        if (title == null) {
            return
        }

        if (AndroidFileCache.isCached(context, uri)) {
            return
        }

        val document = JSONObject()
        document.put("uri", uri.toString())
        document.put("filename", title)

        var jsonArray: JSONArray
        try {
            jsonArray = getRecentDocumentsJson(context)

            // avoid duplicates
            removeRecentDocument(context, title, uri)
        } catch (e: Exception) {
            jsonArray = JSONArray()
        }

        jsonArray.put(document)

        saveJson(context, jsonArray)
    }

    private fun saveJson(context: Context, jsonArray: JSONArray) {
        context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonArray.toString())
            }
        }
    }

    fun removeRecentDocument(context: Context, title: String?, uri: Uri) {
        if (title == null) {
            return
        }

        val jsonArray = getRecentDocumentsJson(context)
        val deleteIndex = findUriIndex(uri.toString(), jsonArray)

        if (deleteIndex >= 0) {
            jsonArray.remove(deleteIndex)
        }

        saveJson(context, jsonArray)
    }

    private fun findUriIndex(uriString: String, jsonArray: JSONArray): Int {
        var index = -1
        for (i in 0 until jsonArray.length()) {
            val document = jsonArray.getJSONObject(i)
            if (uriString == document.getString("uri")) {
                index = i
            }
        }
        return index
    }
}
