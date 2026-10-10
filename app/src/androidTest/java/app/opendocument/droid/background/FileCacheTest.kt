package app.opendocument.droid.background

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FileCacheTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    private fun cachedFile() = FileCache.createCacheFile(context).also { files += it }

    @After
    fun cleanUp() {
        files.forEach { FileCache.deleteCacheFile(it) }
    }

    @Test
    fun allocationsNeverShareADirectory() {
        val allocated = List(100) { cachedFile() }
        assertEquals(allocated.size, allocated.map { it.parentFile }.toSet().size)
    }

    @Test
    fun reopeningAnOlderCachedFilePreservesItsContent() {
        val first = cachedFile().apply { writeText("original") }
        cachedFile().writeText("newer")

        val reopened = FileCache.store(context, FileCache.getCacheFileUri(context, first))

        assertEquals(first.canonicalFile, reopened.canonicalFile)
        assertEquals("original", reopened.readText())
    }

    @Test
    fun encodedNamesRoundTripThroughTheProvider() {
        val parent = cachedFile()
        val file = File(parent.parentFile, "a space # and %.txt").also { files += it }
        file.writeText("document")

        assertEquals(
            file.canonicalFile,
            FileCache.getCacheFile(context, FileCache.getCacheFileUri(context, file)),
        )
    }

    @Test
    fun cacheUrisCannotEscapeTheirDirectory() {
        val uri = FileCache.getCacheFileUri(context, cachedFile())
        val directory = uri.toString().substringBeforeLast('/')

        assertNull(FileCache.getCacheFile(context, Uri.parse("$directory/../outside")))
        assertNull(FileCache.getCacheFile(context, Uri.parse("$directory/%2e%2e%2foutside")))
        assertNull(FileCache.getCacheFile(context, uri.buildUpon().scheme("https").build()))
    }

    @Test
    fun privateFilesCannotBeSharedByTheProvider() {
        assertThrows(IllegalArgumentException::class.java) {
            FileCache.getCacheFileUri(context, File(context.filesDir, "private.json"))
        }
    }
}
