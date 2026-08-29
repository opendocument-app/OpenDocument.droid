package app.opendocument.droid.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the core is expected to render: odrcore v6 keeps reference html output for every format
 * asserted here.
 *
 * Instrumented because [SupportedDocumentTypes] reads the core's format table, which lives in
 * `libodr_jni`.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class RenderedByCoreTest {

    private fun isSupported(fileType: String?): Boolean =
        SupportedDocumentTypes.isRenderedByCore(fileType)

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

    /** What odrcore 6.2 renders beyond documents, each of which used to need a viewer in assets. */
    @Test
    fun whatTheCoreRendersBesidesDocumentsIsSupported() {
        assertTrue(isSupported("text/plain"))
        assertTrue(isSupported("image/png"))
        // the image types 6.2 added
        assertTrue(isSupported("image/webp"))
        assertTrue(isSupported("image/heic"))
        assertTrue(isSupported("image/avif"))
        assertTrue(isSupported("application/zip"))
        // rendered when handed one, never claimed in the share sheet
        assertTrue(isSupported("audio/mpeg"))
        assertTrue(isSupported("video/mp4"))
        // the image types 6.3 added
        assertTrue(isSupported("image/svg+xml"))
        assertTrue(isSupported("image/jxl"))
    }

    /** 6.11 opens rtf and the three iwork formats. */
    @Test
    fun whatSixElevenAddedIsSupported() {
        assertTrue(isSupported("application/rtf"))
        assertTrue(isSupported("text/rtf"))
        assertTrue(isSupported("application/vnd.apple.pages"))
        assertTrue(isSupported("application/vnd.apple.numbers"))
        assertTrue(isSupported("application/vnd.apple.keynote"))
    }

    /** 6.11 stopped claiming html for these: no browser paints them. */
    @Test
    fun theImagesNoBrowserPaintsAreNotRendered() {
        assertFalse(isSupported("image/vnd.adobe.photoshop"))
        assertFalse(isSupported("image/jp2"))
        assertFalse(isSupported("image/wmf"))
        assertFalse(isSupported("image/emf"))
    }

    /** 6.4 opens a csv as a spreadsheet, so the table is the core's to draw. */
    @Test
    fun csvIsSupported() {
        assertTrue(isSupported("text/csv"))
        assertTrue(isSupported("application/csv"))
        assertTrue(isSupported("text/comma-separated-values"))
    }

    /** 6.5 puts a decoder behind the name the core gave xml in 6.3. */
    @Test
    fun xmlIsSupported() {
        assertTrue(isSupported("application/xml"))
        assertTrue(isSupported("text/xml"))
    }

    @Test
    fun whatTheCoreCannotOpenIsNotClaimed() {
        assertFalse(isSupported("application/vnd.wordperfect"))
        assertFalse(isSupported("application/octet-stream"))
        assertFalse(isSupported(null))
    }

    /**
     * The binary excel package: an ooxml container whose parts are not spreadsheetml, so there is
     * no decoder for it. Its mime type starts like every other excel one, which is what the old
     * prefix match got wrong.
     */
    @Test
    fun theBinaryExcelWorkbookIsNotClaimed() {
        assertFalse(isSupported("application/vnd.ms-excel.sheet.binary.macroEnabled.12"))
    }
}
