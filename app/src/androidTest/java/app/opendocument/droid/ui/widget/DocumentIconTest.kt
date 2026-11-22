package app.opendocument.droid.ui.widget

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.FileType
import app.opendocument.droid.R
import app.opendocument.droid.background.CoreLoader
import app.opendocument.droid.background.RecentDocumentsUtil
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DocumentIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun storedTypeWinsOverMissingOrMisleadingExtension() {
        CoreLoader.initializeCore(context)
        assertEquals(
            R.drawable.ic_document_spreadsheet,
            DocumentIcon.forDocument("report.pdf", FileType.OPENDOCUMENT_SPREADSHEET),
        )
        assertEquals(
            R.drawable.ic_document_pdf,
            DocumentIcon.forDocument("download", FileType.PORTABLE_DOCUMENT_FORMAT),
        )
    }

    @Test
    fun oldEntriesUseCoreExtensionClassification() {
        CoreLoader.initializeCore(context)
        for ((filename, icon) in
            listOf(
                "report.ODT" to R.drawable.ic_document_text,
                "budget.XLSX" to R.drawable.ic_document_spreadsheet,
                "rows.csv" to R.drawable.ic_document_spreadsheet,
                "slides.odp" to R.drawable.ic_document_presentation,
                "manual.pdf" to R.drawable.ic_document_pdf,
                "notes.txt" to R.drawable.ic_document_text,
                "archive.zip" to R.drawable.ic_description,
                "README" to R.drawable.ic_description,
            )) assertEquals(filename, icon, DocumentIcon.forDocument(filename))
    }

    @Test
    fun oldHistoryCanAcquireATypeWithoutChangingOrderOrTimestamps() {
        val history = File(context.filesDir, "recent_documents.json")
        val previous = if (history.exists()) history.readBytes() else null
        try {
            history.writeText(
                """[
                {"filename":"old.ods","uri":"content://test/old","lastOpenedAt":12},
                {"filename":"new.pdf","uri":"content://test/new","lastOpenedAt":34,"fileType":"FUTURE_FORMAT"}
            ]"""
            )
            val before = RecentDocumentsUtil.getRecentDocuments(context)
            assertTrue(before.all { it.fileType == null })
            val uri = Uri.parse("content://test/old")
            RecentDocumentsUtil.setFileType(context, uri, FileType.OPENDOCUMENT_SPREADSHEET)
            val after = RecentDocumentsUtil.getRecentDocuments(context)
            assertEquals(
                before.map { it.uri to it.lastOpenedAt },
                after.map { it.uri to it.lastOpenedAt },
            )
            assertEquals(FileType.OPENDOCUMENT_SPREADSHEET, after.last().fileType)
            RecentDocumentsUtil.removeRecentDocument(context, uri)
            RecentDocumentsUtil.setFileType(context, uri, FileType.OPENDOCUMENT_SPREADSHEET)
            assertEquals(1, RecentDocumentsUtil.getRecentDocuments(context).size)
            RecentDocumentsUtil.restoreRecentDocument(context, after.last(), 1)
            assertEquals(after, RecentDocumentsUtil.getRecentDocuments(context))
        } finally {
            if (previous == null) history.delete() else history.writeBytes(previous)
        }
    }
}
