package app.opendocument.droid.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSizeTest {

    @Test
    fun anOdfA4PageIsA4() {
        val page = checkNotNull(PageSize.of(21.001, "cm", 29.7, "cm"))
        assertEquals(Paper.ISO_A4, page.paper())
        assertFalse(page.isLandscape)
    }

    @Test
    fun aLetterPageIsLetterNotA4() {
        assertEquals(Paper.NA_LETTER, PageSize.of(8.5, "in", 11.0, "in")?.paper())
        assertEquals(Paper.NA_LETTER, PageSize.of(612.0, "pt", 792.0, "pt")?.paper())
    }

    @Test
    fun aLandscapePageKeepsItsPaper() {
        val page = checkNotNull(PageSize.of(297.0, "mm", 210.0, "mm"))
        assertEquals(Paper.ISO_A4, page.paper())
        assertTrue(page.isLandscape)
    }

    @Test
    fun aSlideHasNoPaperButAnOrientation() {
        val page = checkNotNull(PageSize.of(27.991, "cm", 21.006, "cm"))
        assertNull(page.paper())
        assertTrue(page.isLandscape)
    }

    @Test
    fun aSizeWithoutALengthHasNone() {
        assertNull(PageSize.of(21.0, "cm", null, null))
        assertNull(PageSize.of(21.0, "cm", 29.7, "%"))
        assertNull(PageSize.of(0.0, "cm", 29.7, "cm"))
    }
}
