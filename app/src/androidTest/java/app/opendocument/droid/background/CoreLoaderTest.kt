package app.opendocument.droid.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the core is expected to render. odrcore v6 keeps reference html output for every format
 * asserted here, so these are the types a load is expected to succeed for.
 *
 * Instrumented rather than a JVM test since odrcore 6.1: [SupportedDocumentTypes] reads the core's
 * own format table now instead of keeping a list of mime prefixes, and that table lives in
 * `libodr_jni`. Nothing here opens a file - the loader only asks the table - but a device is what
 * has the library.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class CoreLoaderTest {

    private lateinit var coreLoader: CoreLoader

    @Before
    fun setUp() {
        // no context: isSupported() is pure, and constructing a loader has no side effects
        coreLoader = CoreLoader(null)
    }

    private fun isSupported(fileType: String?): Boolean {
        val options = FileLoader.Options()
        options.fileType = fileType

        return coreLoader.isSupported(options)
    }

    @Test
    fun opendocumentIsSupported() {
        assertTrue(isSupported("application/vnd.oasis.opendocument.text"))
        assertTrue(isSupported("application/vnd.oasis.opendocument.spreadsheet"))
        assertTrue(isSupported("application/vnd.oasis.opendocument.presentation"))
        assertTrue(isSupported("application/vnd.oasis.opendocument.graphics"))
        assertTrue(isSupported("application/vnd.oasis.opendocument.text-master"))
        // the spelling some providers use
        assertTrue(isSupported("application/x-vnd.oasis.opendocument.text"))
        // and the single file xml flavor, which the core learned to name in 6.1
        assertTrue(isSupported("application/vnd.oasis.opendocument.text-flat-xml"))
    }

    @Test
    fun ooxmlIsSupported() {
        assertTrue(
            isSupported("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        assertTrue(isSupported("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(
            isSupported("application/vnd.openxmlformats-officedocument.presentationml.presentation")
        )
        assertTrue(
            isSupported("application/vnd.openxmlformats-officedocument.wordprocessingml.template")
        )
    }

    @Test
    fun theMacroEnabledOoxmlVariantsAreSupported() {
        // spelled outside the openxmlformats family, and each one named in the core's table -
        // this used to be a prefix match on "application/vnd.ms-word" and friends
        assertTrue(isSupported("application/vnd.ms-word.document.macroEnabled.12"))
        assertTrue(isSupported("application/vnd.ms-excel.sheet.macroEnabled.12"))
        assertTrue(isSupported("application/vnd.ms-powerpoint.presentation.macroEnabled.12"))
    }

    @Test
    fun theLegacyBinaryFormatsAreSupported() {
        assertTrue(isSupported("application/msword"))
        assertTrue(isSupported("application/vnd.ms-excel"))
        assertTrue(isSupported("application/vnd.ms-powerpoint"))
        // the aliases the core names for them, which the old prefix list did not reach
        assertTrue(isSupported("application/msexcel"))
        assertTrue(isSupported("application/mspowerpoint"))
    }

    @Test
    fun pdfIsSupported() {
        assertTrue(isSupported("application/pdf"))
        assertTrue(isSupported("application/x-pdf"))
    }

    @Test
    fun whatRawLoaderShowsIsNotClaimed() {
        // the core does render these, but RawLoader is what gives them their player or viewer
        assertFalse(isSupported("text/plain"))
        assertFalse(isSupported("text/csv"))
        assertFalse(isSupported("image/png"))
        assertFalse(isSupported("application/zip"))
    }

    @Test
    fun whatTheCoreCannotOpenIsNotClaimed() {
        assertFalse(isSupported("application/vnd.wordperfect"))
        assertFalse(isSupported("text/rtf"))
        assertFalse(isSupported("application/vnd.apple.pages"))
        assertFalse(isSupported("application/octet-stream"))
        assertFalse(isSupported(null))
    }

    /**
     * The binary excel package: an ooxml container whose parts are not spreadsheetml, so there is
     * no decoder for it. Claimed until odrcore 6.1 gave it a file type of its own, because the mime
     * type starts like every other excel one and the app matched excel by prefix.
     */
    @Test
    fun theBinaryExcelWorkbookIsNotClaimed() {
        assertFalse(isSupported("application/vnd.ms-excel.sheet.binary.macroEnabled.12"))
    }
}
