package app.opendocument.droid.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentDocumentListTest {

    @Test
    fun addPutsTheDocumentFirst() {
        val update = RecentDocumentList.add(listOf(entry("older")), entry("newer"))

        assertEquals(listOf("newer", "older"), update.entries.map { it.uri })
        assertTrue(update.evicted.isEmpty())
    }

    @Test
    fun addToAnEmptyListYieldsTheOneDocument() {
        val update = RecentDocumentList.add(emptyList(), entry("only"))

        assertEquals(listOf("only"), update.entries.map { it.uri })
    }

    @Test
    fun addMovesAKnownDocumentBackToTheFrontInsteadOfDuplicatingIt() {
        val current = listOf(entry("c"), entry("b"), entry("a"))

        val update = RecentDocumentList.add(current, entry("a"))

        assertEquals(listOf("a", "c", "b"), update.entries.map { it.uri })
        assertTrue(update.evicted.isEmpty())
    }

    @Test
    fun addKeepsTheNewestMetadataOfAKnownDocument() {
        val current = listOf(RecentDocumentList.Entry("old name", "a", 1))

        val update = RecentDocumentList.add(current, RecentDocumentList.Entry("new name", "a", 2))

        assertEquals(1, update.entries.size)
        assertEquals("new name", update.entries[0].filename)
        assertEquals(2, update.entries[0].lastOpenedAt)
    }

    @Test
    fun addDropsTheOldestDocumentOnceTheListIsFull() {
        // newest first, so uri3 is the one that has been sitting there longest
        val current = (1..3).map { entry("uri$it") }

        val update = RecentDocumentList.add(current, entry("newest"), max = 3)

        assertEquals(listOf("newest", "uri1", "uri2"), update.entries.map { it.uri })
        assertEquals(listOf("uri3"), update.evicted.map { it.uri })
    }

    @Test
    fun addEvictsEverythingPastTheCap() {
        val current = (1..10).map { entry("uri$it") }

        val update = RecentDocumentList.add(current, entry("newest"), max = 4)

        assertEquals(4, update.entries.size)
        assertEquals(7, update.evicted.size)
        assertEquals(
            listOf("uri4", "uri5", "uri6", "uri7", "uri8", "uri9", "uri10"),
            update.evicted.map { it.uri },
        )
    }

    @Test
    fun reAddingAKnownDocumentToAFullListEvictsNothing() {
        val current = (1..3).map { entry("uri$it") }

        val update = RecentDocumentList.add(current, entry("uri3"), max = 3)

        assertEquals(listOf("uri3", "uri1", "uri2"), update.entries.map { it.uri })
        assertTrue(update.evicted.isEmpty())
    }

    @Test
    fun removeDropsEveryEntryForTheUri() {
        val current = listOf(entry("a"), entry("b"), entry("a"))

        assertEquals(listOf("b"), RecentDocumentList.remove(current, "a").map { it.uri })
    }

    @Test
    fun removeLeavesAnUnknownUriAlone() {
        val current = listOf(entry("a"), entry("b"))

        assertEquals(listOf("a", "b"), RecentDocumentList.remove(current, "c").map { it.uri })
    }

    @Test
    fun removeFromAnEmptyListYieldsAnEmptyList() {
        assertTrue(RecentDocumentList.remove(emptyList(), "a").isEmpty())
    }

    @Test
    fun insertPutsADocumentBackWhereItWas() {
        val current = listOf(entry("a"), entry("c"))

        val restored = RecentDocumentList.insert(current, entry("b"), 1)

        assertEquals(listOf("a", "b", "c"), restored.map { it.uri })
    }

    @Test
    fun insertAtTheFrontAndAtTheEndBothWork() {
        val current = listOf(entry("a"), entry("b"))

        assertEquals(
            listOf("x", "a", "b"),
            RecentDocumentList.insert(current, entry("x"), 0).map { it.uri },
        )
        assertEquals(
            listOf("a", "b", "x"),
            RecentDocumentList.insert(current, entry("x"), 2).map { it.uri },
        )
    }

    @Test
    fun insertClampsAnIndexPastTheEnd() {
        val current = listOf(entry("a"))

        assertEquals(
            listOf("a", "x"),
            RecentDocumentList.insert(current, entry("x"), 99).map { it.uri },
        )
        assertEquals(
            listOf("x", "a"),
            RecentDocumentList.insert(current, entry("x"), -5).map { it.uri },
        )
    }

    @Test
    fun insertDoesNotDuplicateAKnownUri() {
        val current = listOf(entry("a"), entry("b"))

        assertEquals(
            listOf("b", "a"),
            RecentDocumentList.insert(current, entry("a"), 1).map { it.uri },
        )
    }

    private fun entry(uri: String) = RecentDocumentList.Entry("name of $uri", uri, 0)
}
