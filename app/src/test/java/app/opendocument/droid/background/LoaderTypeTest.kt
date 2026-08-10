package app.opendocument.droid.background

import app.opendocument.droid.background.FileLoader.LoaderType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [LoaderType.ofParcelled] is what stands between a saved [FileLoader.Result] written by an older
 * version and the `IllegalArgumentException` `valueOf` would raise on it. Saved instance state
 * outlives an app update, so the names below really do arrive.
 */
class LoaderTypeTest {

    @Test
    fun aNameThisVersionStillHasIsItself() {
        assertEquals(LoaderType.CORE, LoaderType.ofParcelled("CORE"))
        assertEquals(LoaderType.METADATA, LoaderType.ofParcelled("METADATA"))
    }

    /** The two that were dropped when odrcore learned to render svg and xml itself. */
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
