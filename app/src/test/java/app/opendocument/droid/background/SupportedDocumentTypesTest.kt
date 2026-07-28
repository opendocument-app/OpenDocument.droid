package app.opendocument.droid.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedDocumentTypesTest {

    @Test
    fun opendocumentTypesAreSupported() {
        assertTrue(supported("application/vnd.oasis.opendocument.text", "report.odt"))
        assertTrue(supported("application/vnd.oasis.opendocument.spreadsheet", "budget.ods"))
        assertTrue(supported("application/vnd.oasis.opendocument.presentation", "deck.odp"))
        assertTrue(supported("application/vnd.oasis.opendocument.graphics", "drawing.odg"))
        assertTrue(supported("application/vnd.oasis.opendocument.text-template", "letter.ott"))
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
    }

    private fun supported(mimeType: String?, filename: String?) =
        SupportedDocumentTypes.isSupported(mimeType, filename)
}
