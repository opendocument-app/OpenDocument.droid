package app.opendocument.droid.background

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class RawLoaderTest {

    private lateinit var rawLoader: RawLoader

    @Before
    fun setUp() {
        rawLoader = RawLoader(null, CoreLoader(null))
    }

    private fun isSupported(fileType: String): Boolean {
        val options = FileLoader.Options()
        options.fileType = fileType
        return rawLoader.isSupported(options)
    }

    @Test
    fun supportsWhitelistedMimeTypes() {
        Assert.assertTrue(isSupported("text/plain"))
        Assert.assertTrue(isSupported("image/png"))
        Assert.assertTrue(isSupported("video/mp4"))
        Assert.assertTrue(isSupported("audio/mpeg"))
        Assert.assertTrue(isSupported("application/json"))
        Assert.assertTrue(isSupported("application/xml"))
        Assert.assertTrue(isSupported("application/zip"))
    }

    @Test
    fun rejectsBlacklistedMimeTypes() {
        Assert.assertFalse(isSupported("image/vnd.dwg"))
        Assert.assertFalse(isSupported("image/tiff"))
        Assert.assertFalse(isSupported("audio/amr"))
        Assert.assertFalse(isSupported("video/quicktime"))
        Assert.assertFalse(isSupported("text/rtf"))
        Assert.assertFalse(isSupported("text/calendar"))
    }

    @Test
    fun rejectsUnknownMimeTypes() {
        Assert.assertFalse(isSupported("application/pdf"))
        Assert.assertFalse(isSupported("application/vnd.oasis.opendocument.text"))
        Assert.assertFalse(isSupported("application/octet-stream"))
    }
}
