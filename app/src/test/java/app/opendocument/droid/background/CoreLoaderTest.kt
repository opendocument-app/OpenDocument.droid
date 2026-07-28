package app.opendocument.droid.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the core is expected to render. odrcore v6 keeps reference html output for every format
 * asserted here, so these are the types a load is expected to succeed for.
 */
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
        // spelled outside the openxmlformats family, which is why the legacy types below are
        // matched by their prefix and vnd.ms-word is listed at all
        assertTrue(isSupported("application/vnd.ms-word.document.macroEnabled.12"))
        assertTrue(isSupported("application/vnd.ms-excel.sheet.macroEnabled.12"))
        assertTrue(isSupported("application/vnd.ms-powerpoint.presentation.macroEnabled.12"))
    }

    @Test
    fun theLegacyBinaryFormatsAreSupported() {
        assertTrue(isSupported("application/msword"))
        assertTrue(isSupported("application/vnd.ms-excel"))
        assertTrue(isSupported("application/vnd.ms-powerpoint"))
    }

    @Test
    fun pdfIsSupported() {
        assertTrue(isSupported("application/pdf"))
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
}
