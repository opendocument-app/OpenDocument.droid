package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * The folders the user granted access to, in the order they added them.
 *
 * A json file rather than shared preferences because the order matters and every entry carries a
 * cached display name, which a StringSet could not express. Same shape as [RecentDocumentsUtil].
 */
object FolderTreesUtil {

    /**
     * How many folders to keep. Each holds a persisted uri permission, and those are capped per
     * package - together with [RecentDocumentList.MAX_ENTRIES] this stays far below the limit.
     */
    const val MAX_TREES: Int = 8

    private const val FILENAME = "folder_trees.json"

    private const val KEY_URI = "uri"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_ADDED_AT = "addedAt"

    class FolderTree(val uri: Uri, val displayName: String, val addedAt: Long)

    @Synchronized fun getFolderTrees(context: Context): List<FolderTree> = read(context)

    /**
     * Adds [uri], or refreshes the name of a folder that is already there.
     *
     * @return the folders that fell out, so their grants can be released.
     */
    @Synchronized
    fun addFolderTree(context: Context, uri: Uri, displayName: String): List<FolderTree> {
        val value = uri.toString()

        val remaining = read(context).filter { it.uri.toString() != value }
        val trees = ArrayList<FolderTree>(remaining.size + 1)
        trees.addAll(remaining)
        trees.add(FolderTree(uri, displayName, System.currentTimeMillis()))

        if (trees.size <= MAX_TREES) {
            write(context, trees)

            return emptyList()
        }

        // oldest first, so the ones that drop off the front are the least recently added
        val evicted = trees.subList(0, trees.size - MAX_TREES).toList()
        write(context, trees.subList(trees.size - MAX_TREES, trees.size).toList())

        return evicted
    }

    @Synchronized
    fun removeFolderTree(context: Context, uri: Uri) {
        val value = uri.toString()

        val current = read(context)
        val remaining = current.filter { it.uri.toString() != value }

        if (remaining.size != current.size) {
            write(context, remaining)
        }
    }

    private fun read(context: Context): List<FolderTree> {
        val jsonArray =
            try {
                context.openFileInput(FILENAME).use { input ->
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                        JSONArray(reader.readText())
                    }
                }
            } catch (e: Exception) {
                return emptyList()
            }

        val trees = ArrayList<FolderTree>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val tree = jsonArray.optJSONObject(i) ?: continue

            val uri = tree.optString(KEY_URI, "")
            if (uri.isEmpty()) {
                continue
            }

            trees.add(
                FolderTree(
                    Uri.parse(uri),
                    tree.optString(KEY_DISPLAY_NAME, uri),
                    tree.optLong(KEY_ADDED_AT, 0),
                )
            )
        }

        return trees
    }

    private fun write(context: Context, trees: List<FolderTree>) {
        val jsonArray = JSONArray()
        for (tree in trees) {
            val json = JSONObject()
            json.put(KEY_URI, tree.uri.toString())
            json.put(KEY_DISPLAY_NAME, tree.displayName)
            json.put(KEY_ADDED_AT, tree.addedAt)

            jsonArray.put(json)
        }

        context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonArray.toString())
            }
        }
    }
}
