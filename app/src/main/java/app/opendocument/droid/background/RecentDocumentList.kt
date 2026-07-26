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

    class Entry(val filename: String, val uri: String, val lastOpenedAt: Long) {

        override fun equals(other: Any?): Boolean =
            other is Entry &&
                other.filename == filename &&
                other.uri == uri &&
                other.lastOpenedAt == lastOpenedAt

        override fun hashCode(): Int =
            filename.hashCode() * 31 * 31 + uri.hashCode() * 31 + lastOpenedAt.hashCode()

        override fun toString(): String = "Entry($filename, $uri, $lastOpenedAt)"
    }

    /**
     * The list after an [add], plus whatever fell off the end of it. The evicted entries are handed
     * back so their persisted uri permissions can be released again.
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

        if (entries.size <= max) {
            return Update(entries, emptyList())
        }

        return Update(entries.subList(0, max).toList(), entries.subList(max, entries.size).toList())
    }

    /** Drops every entry for [uri]. Returns [current] unchanged if there is none. */
    fun remove(current: List<Entry>, uri: String): List<Entry> = current.filter { it.uri != uri }
}
