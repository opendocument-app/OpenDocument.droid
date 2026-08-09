package app.opendocument.droid.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two things [RawLoader] still has a viewer for, and the many it handed back to the core.
 *
 * Instrumented because the mime type spellings come from odrcore's table in `libodr_jni`.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class RawLoaderTest {

    private lateinit var rawLoader: RawLoader

    @Before
    fun setUp() {
        // no context: isSupported() is pure, and constructing a loader has no side effects
        rawLoader = RawLoader(null)
    }

    private fun isSupported(fileType: String?, filename: String? = null): Boolean {
        val options = FileLoader.Options()
        options.fileType = fileType
        options.filename = filename

        return rawLoader.isSupported(options)
    }

    /**
     * How svg and xml actually arrive: the core identifies by content and an svg *is* text, so
     * `Odr.mimetype` says `text/plain` and only the name knows better.
     */
    @Test
    fun whatTheCoreMisreadsAsTextIsRoutedByItsName() {
        assertTrue(isSupported("text/plain", "drawing.svg"))
        assertTrue(isSupported("text/plain", "feed.xml"))
        // and a text file is still a text file
        assertFalse(isSupported("text/plain", "readme.txt"))
        assertFalse(isSupported("text/plain", "notes"))
    }

    /**
     * The other half: the bytes win whenever the core recognized them. An odt called `drawing.svg`
     * would otherwise reach the image viewer, succeed, and leave no fallback.
     */
    @Test
    fun aMisnamedDocumentIsStillADocument() {
        assertFalse(isSupported("application/vnd.oasis.opendocument.text", "report.xml"))
        assertFalse(isSupported("application/pdf", "report.xml"))
        assertFalse(isSupported("application/zip", "archive.svg"))
        assertFalse(isSupported("image/png", "scan.xml"))
        // but nothing detected at all still leaves the name as the only thing to go on
        assertTrue(isSupported("application/octet-stream", "feed.xml"))
        assertTrue(isSupported(null, "drawing.svg"))
    }

    /** The WebView draws both, so nothing is gained by translating them first. */
    @Test
    fun whatTheWebViewDrawsItselfIsSupported() {
        assertTrue(isSupported("image/svg+xml"))
        assertTrue(isSupported("application/xml"))
        assertTrue(isSupported("text/xml"))
    }

    /** Each had a viewer here until odrcore learned to render it. Keeps them from coming back. */
    @Test
    fun whatTheCoreRendersIsLeftToIt() {
        assertFalse(isSupported("text/plain"))
        assertFalse(isSupported("image/png"))
        assertFalse(isSupported("image/webp"))
        assertFalse(isSupported("application/zip"))
        assertFalse(isSupported("audio/mpeg"))
        assertFalse(isSupported("video/mp4"))
        assertFalse(isSupported("application/json"))
        assertFalse(isSupported("text/csv"))
        assertFalse(isSupported("application/csv"))
        assertFalse(isSupported("text/comma-separated-values"))
        // and by name, not only by mime type
        assertFalse(isSupported("text/plain", "rows.csv"))
        assertFalse(isSupported("application/octet-stream", "rows.csv"))
    }

    /** No rename-and-hope for these - the upload offer beats a blank page. */
    @Test
    fun whatNobodyCanShowIsNotSupported() {
        assertFalse(isSupported("application/pdf"))
        assertFalse(isSupported("application/vnd.oasis.opendocument.text"))
        assertFalse(isSupported("application/octet-stream"))
        assertFalse(isSupported("text/vcard"))
        assertFalse(isSupported("text/rtf"))
        assertFalse(isSupported(null))
    }
}
