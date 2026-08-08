package app.opendocument.droid.background

/**
 * The ordering, de-duplication and capping rules of the recently opened documents list.
 *
 * Deliberately free of Android dependencies so it can be covered by plain JVM unit tests; in
 * production [RecentDocumentsUtil] adds the file and JSON handling around it.
 */
object RecentDocumentList {

    /**
     * How many documents to remember. Every entry holds a persisted uri permission, and those are
     * capped per package (128 before android 11, 512 from it), so this cannot grow without bound.
     */
    const val MAX_ENTRIES: Int = 32

    data class Entry(val filename: String, val uri: String, val lastOpenedAt: Long)

    /**
     * The list after an [add] or an [insert], plus whatever fell off the end of it. The evicted
     * entries are handed back so their persisted uri permissions can be released again.
     */
    class Update internal constructor(val entries: List<Entry>, val evicted: List<Entry>)

    /**
     * Puts [entry] at the front of [current], newest first. An entry for the same uri that is
     * already in the list is replaced rather than duplicated, and anything past [max] is evicted.
     */
    fun add(current: List<Entry>, entry: Entry, max: Int = MAX_ENTRIES): Update {
        val entries = ArrayList<Entry>(current.size + 1)
        entries.add(entry)
        current.filterTo(entries) { it.uri != entry.uri }

        return cap(entries, max)
    }

    /** Drops every entry for [uri]. Returns [current] unchanged if there is none. */
    fun remove(current: List<Entry>, uri: String): List<Entry> = current.filter { it.uri != uri }

    /**
     * Puts [entry] back at [index], for undoing a removal.
     *
     * Unlike [add] this does not move it to the front - the point of an undo is that the list ends
     * up looking like it did before. An index past the end lands at the end.
     *
     * [max] still applies: the list can have filled up again while the undo was on offer - another
     * document opened over the top of the snackbar - and an undo is no reason to grow past the cap.
     */
    fun insert(current: List<Entry>, entry: Entry, index: Int, max: Int = MAX_ENTRIES): Update {
        val entries = ArrayList<Entry>(current.size + 1)
        current.filterTo(entries) { it.uri != entry.uri }

        entries.add(index.coerceIn(0, entries.size), entry)

        return cap(entries, max)
    }

    /** Keeps the first [max] entries, handing the tail back to be released. */
    private fun cap(entries: List<Entry>, max: Int): Update {
        if (entries.size <= max) {
            return Update(entries, emptyList())
        }

        return Update(entries.subList(0, max).toList(), entries.subList(max, entries.size).toList())
    }
}
