package app.opendocument.droid.background

import app.opendocument.droid.background.FileLoader.LoaderType
import org.junit.Assert.assertEquals
import org.junit.Test

/** A saved [FileLoader.Result] can name a loader this version does not have. */
class LoaderTypeTest {

    @Test
    fun aNameThisVersionStillHasIsItself() {
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled("CORE"))
        assertEquals(LoaderType.METADATA, LoaderType.ofParcelled("METADATA"))
    }

    @Test
    fun aRetiredLoaderReadsAsTheCore() {
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled("RAW"))
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled("ONLINE"))
    }

    @Test
    fun nothingAtAllReadsAsTheCore() {
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled(null))
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled(""))
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled("core"))
    }
}
