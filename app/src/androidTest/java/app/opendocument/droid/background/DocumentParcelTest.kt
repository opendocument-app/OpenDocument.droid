package app.opendocument.droid.background

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The saved instance state of an open document, written and read back.
 *
 * `DocumentFragment` parcels these into the bundle that survives process death, where a field
 * written and read in a different order is silently the neighbouring one. Nothing else covers the
 * round trip - the recreation test restores from the view model, which does not parcel.
 *
 * Instrumented because [Parcel] is the framework's.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DocumentParcelTest {

    @Test
    fun aRequestComesBackAsItself() {
        val request =
            DocumentRequest(Uri.parse("content://provider/document/1"), persistentUri = true)
                .apply {
                    editable = true
                    password = "passwort"
                }

        val restored = roundTrip(request, DocumentRequest.CREATOR)

        assertEquals(request.uri, restored.uri)
        assertTrue(restored.persistentUri)
        assertTrue(restored.editable)
        assertEquals("passwort", restored.password)
    }

    @Test
    fun aRequestWithoutAPasswordComesBackWithoutOne() {
        val restored =
            roundTrip(
                DocumentRequest(Uri.parse("content://provider/document/2"), persistentUri = false),
                DocumentRequest.CREATOR,
            )

        assertNull(restored.password)
        assertEquals(false, restored.persistentUri)
        assertEquals(false, restored.editable)
    }

    @Test
    fun anIdentifiedFileComesBackAsItself() {
        val file =
            IdentifiedFile(
                Uri.parse("content://at.tomtasche.reader.pro.provider/cache.1/cached-file.tmp"),
                "report.odt",
                "application/vnd.oasis.opendocument.text",
                "odt",
            )

        val restored = roundTrip(file, IdentifiedFile.CREATOR)

        assertEquals(file.cacheUri, restored.cacheUri)
        assertEquals("report.odt", restored.filename)
        assertEquals("application/vnd.oasis.opendocument.text", restored.mimeType)
        assertEquals("odt", restored.extension)
    }

    /** What nothing could name: the mime type and the extension are both allowed to be missing. */
    @Test
    fun anUnnamedFileComesBackUnnamed() {
        val restored =
            roundTrip(
                IdentifiedFile(Uri.parse("content://provider/cache.1/x.tmp"), "x", null, null),
                IdentifiedFile.CREATOR,
            )

        assertNull(restored.mimeType)
        assertNull(restored.extension)
        assertEquals("x", restored.filename)
    }

    /** A spreadsheet, which is the only shape with more than one part and named tabs. */
    @Test
    fun aDocumentComesBackWithEveryPart() {
        val document =
            LoadedDocument(
                DocumentRequest(Uri.parse("content://provider/document/3"), persistentUri = true),
                IdentifiedFile(
                    Uri.parse("content://provider/cache.1/cached-file.tmp"),
                    "budget.ods",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "ods",
                ),
                listOf("hey", "ho", "Sheet3"),
                listOf(
                    Uri.parse("http://localhost:29665/file/odr/0.html"),
                    Uri.parse("http://localhost:29665/file/odr/1.html"),
                    Uri.parse("http://localhost:29665/file/odr/2.html"),
                ),
                isEditable = true,
                readsAsDocument = true,
            )

        val restored = roundTrip(document, LoadedDocument.CREATOR)

        assertEquals(document.request.uri, restored.request.uri)
        assertEquals("budget.ods", restored.file.filename)
        assertEquals(listOf("hey", "ho", "Sheet3"), restored.partTitles)
        assertEquals(document.partUris, restored.partUris)
        assertTrue(restored.isEditable)
        assertTrue(restored.readsAsDocument)
    }

    /** Everything but a spreadsheet: one part, and the core does not name it. */
    @Test
    fun aSinglePartDocumentKeepsItsNullTitle() {
        val restored =
            roundTrip(
                LoadedDocument(
                    DocumentRequest(Uri.parse("content://provider/document/4"), false),
                    IdentifiedFile(
                        Uri.parse("content://provider/cache.1/cached-file.tmp"),
                        "letter.odt",
                        "application/vnd.oasis.opendocument.text",
                        "odt",
                    ),
                    listOf<String?>(null),
                    listOf(Uri.parse("http://localhost:29665/file/odr/document.html")),
                    isEditable = false,
                    readsAsDocument = true,
                ),
                LoadedDocument.CREATOR,
            )

        assertEquals(1, restored.partTitles.size)
        assertNull(restored.partTitles[0])
        assertEquals(false, restored.isEditable)
        assertTrue(restored.readsAsDocument)
    }

    private fun <T> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()

        return try {
            (value as Parcelable).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)

            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
