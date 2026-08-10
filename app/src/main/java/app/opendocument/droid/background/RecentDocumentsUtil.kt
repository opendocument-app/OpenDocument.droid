package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Stores the recently opened documents in an app private json file.
 *
 * Only the fields live here: [JsonFileStore] does the file and json handling, and the ordering and
 * capping rules are in [RecentDocumentList], which is android free and unit tested.
 *
 * Every method is synchronized: [DocumentLoader] writes from its background thread while the
 * landing screen reads from its own executor.
 */
object RecentDocumentsUtil {

    private const val FILENAME = "recent_documents.json"

    private const val KEY_URI = "uri"
    private const val KEY_FILENAME = "filename"
    private const val KEY_LAST_OPENED_AT = "lastOpenedAt"

    /**
     * The recently opened documents, newest first.
     *
     * Returns an empty list when nothing was ever saved, rather than making every caller catch the
     * [java.io.FileNotFoundException] that reading a missing file throws.
     */
    @Synchronized
    fun getRecentDocuments(context: Context): List<RecentDocumentList.Entry> = read(context)

    /**
     * Records [uri] as the most recently opened document.
     *
     * @return the entries that fell out of the list, so [PersistedUriPermissions] can release the
     *   uri permissions they were holding.
     */
    @Synchronized
    fun addRecentDocument(
        context: Context,
        title: String?,
        uri: Uri,
    ): List<RecentDocumentList.Entry> {
        if (title == null) {
            return emptyList()
        }

        // documents copied into our own cache are reachable through the cache, not through the
        // uri we were handed, so remembering them would hand back a uri that no longer resolves
        if (FileCache.isCached(context, uri)) {
            return emptyList()
        }

        val entry = RecentDocumentList.Entry(title, uri.toString(), System.currentTimeMillis())
        val update = RecentDocumentList.add(read(context), entry)

        write(context, update.entries)

        return update.evicted
    }

    /**
     * Puts a removed document back where it was, for undoing a swipe.
     *
     * @return the entries that fell out of the list, so [PersistedUriPermissions] can release the
     *   uri permissions they were holding - the list can have filled up again while the undo was
     *   still on offer.
     */
    @Synchronized
    fun restoreRecentDocument(
        context: Context,
        entry: RecentDocumentList.Entry,
        index: Int,
    ): List<RecentDocumentList.Entry> {
        val update = RecentDocumentList.insert(read(context), entry, index)

        write(context, update.entries)

        return update.evicted
    }

    /** Drops [uri] from the list. Does nothing if it is not in it. */
    @Synchronized
    fun removeRecentDocument(context: Context, uri: Uri) {
        val current = read(context)
        val remaining = RecentDocumentList.remove(current, uri.toString())

        if (remaining.size != current.size) {
            write(context, remaining)
        }
    }

    private fun read(context: Context): List<RecentDocumentList.Entry> {
        val entries =
            JsonFileStore.read(context, FILENAME) { document ->
                val filename = document.optString(KEY_FILENAME, "")
                val uri = document.optString(KEY_URI, "")

                if (filename.isEmpty() || uri.isEmpty()) null
                else
                    RecentDocumentList.Entry(filename, uri, document.optLong(KEY_LAST_OPENED_AT, 0))
            }

        // the file stays in the append order older versions wrote, so upgrading needs no
        // migration - the list is only turned around here, on the way out
        return entries.reversed()
    }

    private fun write(context: Context, entries: List<RecentDocumentList.Entry>) {
        JsonFileStore.write(context, FILENAME, entries.reversed()) { entry ->
            JSONObject().apply {
                put(KEY_URI, entry.uri)
                put(KEY_FILENAME, entry.filename)
                put(KEY_LAST_OPENED_AT, entry.lastOpenedAt)
            }
        }
    }
}
