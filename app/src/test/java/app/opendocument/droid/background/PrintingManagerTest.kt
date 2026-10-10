package app.opendocument.droid.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintingManagerTest {

    @Test
    fun aPdfPrintsItself() {
        assertTrue(PrintingManager.printsOriginal("application/pdf", false, false))
    }

    @Test
    fun anEncryptedPdfPrintsThePage() {
        assertFalse(PrintingManager.printsOriginal("application/pdf", true, false))
    }

    @Test
    fun aPdfWithUnsavedMarksPrintsThePage() {
        assertFalse(PrintingManager.printsOriginal("application/pdf", false, true))
    }

    @Test
    fun anyOtherDocumentPrintsThePage() {
        assertFalse(
            PrintingManager.printsOriginal("application/vnd.oasis.opendocument.text", false, false)
        )
        assertFalse(PrintingManager.printsOriginal(null, false, false))
    }
}
