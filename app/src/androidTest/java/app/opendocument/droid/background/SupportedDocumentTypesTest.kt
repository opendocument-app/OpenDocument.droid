package app.opendocument.droid.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the app offers itself for, by example: the cases odrcore's table cannot express, which
 * `SupportedFormatsTest` walks in full.
 *
 * Instrumented because the lists come from `libodr_jni`.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class SupportedDocumentTypesTest {

    @Test
    fun opendocumentTypesAreSupported() {
        assertTrue(supported("application/vnd.oasis.opendocument.text", "report.odt"))
        assertTrue(supported("application/vnd.oasis.opendocument.spreadsheet", "budget.ods"))
        assertTrue(supported("application/vnd.oasis.opendocument.presentation", "deck.odp"))
        assertTrue(supported("application/vnd.oasis.opendocument.graphics", "drawing.odg"))
        assertTrue(supported("application/vnd.oasis.opendocument.text-template", "letter.ott"))
        // the master document and its template, whose extensions the core names since 6.1
        assertTrue(supported(null, "book.odm"))
        assertTrue(supported(null, "book.otm"))
    }

    @Test
    fun officeAndPdfTypesAreSupported() {
        assertTrue(
            supported(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "notes.docx",
            )
        )
        assertTrue(supported("application/msword", "old.doc"))
        assertTrue(supported("application/pdf", "manual.pdf"))
        assertTrue(supported("text/plain", "readme.txt"))
        assertTrue(supported("text/csv", "rows.csv"))
        assertTrue(supported("image/png", "scan.png"))
    }

    @Test
    fun presentationsAndTheLegacyBinaryFormatsAreSupported() {
        assertTrue(
            supported(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "deck.pptx",
            )
        )
        assertTrue(supported("application/vnd.ms-powerpoint", "deck.ppt"))
        assertTrue(supported("application/vnd.ms-excel", "budget.xls"))
        // the octet-stream a provider volunteers for them, caught by the extension instead
        assertTrue(supported("application/octet-stream", "deck.pptx"))
        assertTrue(supported("application/octet-stream", "deck.ppt"))
        assertTrue(supported("application/octet-stream", "budget.xls"))
        // and the templates of the legacy formats, another 6.1 addition
        assertTrue(supported("application/octet-stream", "letter.dot"))
        assertTrue(supported("application/octet-stream", "slides.pot"))
        assertTrue(supported("application/octet-stream", "sheet.xlt"))
    }

    @Test
    fun aKnownExtensionWinsOverAnUnhelpfulMimeType() {
        // this is the common case: providers fall back to octet-stream for anything they do not
        // have a mapping for, which includes most opendocument files
        assertTrue(supported("application/octet-stream", "report.odt"))
        assertTrue(supported("application/octet-stream", "budget.ods"))
    }

    @Test
    fun aKnownMimeTypeWinsOverAMissingExtension() {
        assertTrue(supported("application/pdf", "scan-without-extension"))
    }

    @Test
    fun unrelatedTypesAreNotSupported() {
        // the reason catch-all defaults to off - see issue #477
        assertFalse(supported("text/vcard", "contact.vcf"))
        assertFalse(supported("text/x-vcard", "contact.vcf"))
        assertFalse(supported("audio/mpeg", "song.mp3"))
        assertFalse(supported("video/mp4", "clip.mp4"))
        assertFalse(supported("application/octet-stream", "firmware.bin"))
    }

    /**
     * Formats odrcore can name but has no decoder for. They used to slip through: rtf and word
     * perfect by never being claimed at all, but a `.xlsb` by its mime type starting like an excel
     * one, which is exactly what a prefix match cannot tell apart.
     */
    @Test
    fun formatsWithoutADecoderAreNotSupported() {
        assertFalse(supported("application/vnd.ms-excel.sheet.binary.macroEnabled.12", "old.xlsb"))
        assertFalse(supported("application/rtf", "letter.rtf"))
        assertFalse(supported("application/vnd.wordperfect", "letter.wpd"))
        assertFalse(supported("text/markdown", "readme.md"))
    }

    @Test
    fun nothingKnownAtAllIsNotSupported() {
        assertFalse(supported(null, null))
        assertFalse(supported(null, "mystery"))
        assertFalse(supported("application/octet-stream", null))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(supported("APPLICATION/PDF", "MANUAL.PDF"))
        assertTrue(supported(null, "Report.ODT"))
        // the core spells this one with capitals of its own
        assertTrue(supported("APPLICATION/VND.MS-WORD.DOCUMENT.MACROENABLED.12", null))
    }

    private fun supported(mimeType: String?, filename: String?) =
        SupportedDocumentTypes.isSupported(mimeType, filename)
}
