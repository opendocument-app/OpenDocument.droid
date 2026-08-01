package app.opendocument.droid.background

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * A list of objects in an app private json file, read and written whole.
 *
 * Open the file or treat a missing one as an empty list, walk the array, skip what does not parse,
 * write the whole thing back - none of which is about what [RecentDocumentsUtil] stores, so none of
 * it is in there. The list is not long enough to be worth a database, and is only ever replaced
 * entirely.
 */
internal object JsonFileStore {

    /**
     * Everything in [filename] that [parse] makes sense of, in the order the file has it.
     *
     * A missing file is an empty list rather than the [java.io.FileNotFoundException] every caller
     * would otherwise have to catch, and so is a truncated one - there is no history to recover,
     * and nothing the list holds is worth failing a launch over.
     */
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
