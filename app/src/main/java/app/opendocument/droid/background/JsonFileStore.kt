package app.opendocument.droid.background

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Reads and replaces lists of JSON objects in app-private storage. */
internal object JsonFileStore {

    /** Parses entries in file order. Missing or malformed files return an empty list. */
    fun <T> read(context: Context, filename: String, parse: (JSONObject) -> T?): List<T> {
        val jsonArray =
            try {
                context.openFileInput(filename).use { input ->
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                        JSONArray(reader.readText())
                    }
                }
            } catch (e: Exception) {
                return emptyList()
            }

        val entries = ArrayList<T>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.optJSONObject(i) ?: continue

            parse(json)?.let { entries.add(it) }
        }

        return entries
    }

    /** Replaces [filename] with [entries]. */
    fun <T> write(
        context: Context,
        filename: String,
        entries: List<T>,
        serialize: (T) -> JSONObject,
    ) {
        val jsonArray = JSONArray()
        for (entry in entries) {
            jsonArray.put(serialize(entry))
        }

        context.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonArray.toString())
            }
        }
    }
}
