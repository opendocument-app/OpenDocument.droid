package app.opendocument.droid.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.FileType
import app.opendocument.core.OdrException
import app.opendocument.droid.background.CoreLoader
import app.opendocument.droid.background.EditingKind
import app.opendocument.droid.background.SpreadsheetBudget
import app.opendocument.droid.nonfree.CrashManager
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CoreTest {

    private val coreLoader: CoreLoader
        get() = checkNotNull(sharedLoader) { "the core loader was not started" }

    @Test
    fun testOdtEdit() {
        assertEditRoundTrips("odt-edit", testFile)
    }

    @Test
    fun testDocxEdit() {
        assertEditRoundTrips("docx-edit", docxTestFile)
    }

    @Test
    fun testPptxEdit() {
        assertEditRoundTrips("pptx-edit", pptxTestFile)
    }

    /** Writes one run of [file] as the page's editor does, and reads the saved file back. */
    private fun assertEditRoundTrips(prefix: String, file: File) {
        val html =
            URL(coreLoader.host(prefix, file.absolutePath, askEditing = true)[0].url).readText()

        val id =
            checkNotNull(RUN_ID.find(html)) { "the editable render of ${file.name} names no run" }
                .groupValues[1]

        val payload = """{"version":2,"ops":[{"op":"setText","id":$id,"text":"$EDITED"}]}"""

        val result =
            coreLoader.writeEdits(
                file.absolutePath,
                null,
                null,
                coreLoader.editing,
                payload,
                File(cacheDir(), "$prefix-result").path,
            )
        Assert.assertTrue("the edited document should have been saved", result.isFile)

        val saved = URL(coreLoader.host("$prefix-saved", result.absolutePath)[0].url).readText()
        Assert.assertTrue("the saved ${file.name} should carry the edit", saved.contains(EDITED))

        result.delete()
    }

    /** A pdf takes marks, which the core appends to a copy of it as annotations. */
    @Test
    fun testPdfAnnotation() {
        val payload =
            """{"version":1,"annotations":[{"page":0,"type":"highlight",""" +
                """"quads":[[72,720,200,720,72,700,200,700]],"color":[1,0.9,0.2]}]}"""

        val result =
            coreLoader.writeEdits(
                pdfTestFile.absolutePath,
                null,
                null,
                EditingKind.ANNOTATION,
                payload,
                File(cacheDir(), "pdf-annotate-result").path,
            )

        Assert.assertTrue(
            "the annotated pdf should hold more than the original",
            result.length() > pdfTestFile.length(),
        )
        Assert.assertTrue(
            "the annotation should have been appended",
            String(result.readBytes(), Charsets.ISO_8859_1).contains("/Highlight"),
        )

        result.delete()
    }

    /** A plain text file is edited whole, and saved as utf-8. */
    @Test
    fun testTextEdit() {
        val text = File(cacheDir(), "plain.txt")
        text.writeText("before\n")
        extracted += text

        coreLoader.host("text-edit", text.absolutePath, askEditing = true)
        Assert.assertEquals(EditingKind.TEXT, coreLoader.editing)

        val result =
            coreLoader.writeEdits(
                text.absolutePath,
                null,
                null,
                EditingKind.TEXT,
                """{"version":2,"ops":[{"op":"setContent","text":"$EDITED"}]}""",
                File(cacheDir(), "text-edit-result").path,
            )

        Assert.assertEquals(EDITED, result.readText())

        result.delete()
    }

    /**
     * The formats that used to be sent off for conversion instead: presentations, and the legacy
     * binary microsoft ones. The core renders all four, so the app hands them to it.
     */
    @Test
    fun testPptx() {
        val views =
            coreLoader.host(
                prefix = "pptx-test",
                inputPath = pptxTestFile.absolutePath,
            )
        Assert.assertFalse("hosting the PPTX file should produce a view", views.isEmpty())
    }

    @Test
    fun testDoc() {
        val views =
            coreLoader.host(
                prefix = "doc-test",
                inputPath = docTestFile.absolutePath,
            )
        Assert.assertFalse("hosting the DOC file should produce a view", views.isEmpty())
    }

    @Test
    fun testPpt() {
        val views =
            coreLoader.host(
                prefix = "ppt-test",
                inputPath = pptTestFile.absolutePath,
            )
        Assert.assertFalse("hosting the PPT file should produce a view", views.isEmpty())
    }

    @Test
    fun testXls() {
        val views =
            coreLoader.host(
                prefix = "xls-test",
                inputPath = xlsTestFile.absolutePath,
            )
        Assert.assertFalse("hosting the XLS file should produce a view", views.isEmpty())
    }

    /**
     * What the core lets the user change in each of the formats it renders - the answer
     * `DocumentFragment` puts the Edit button up by, and picks the tools with.
     */
    @Test
    fun testEditableFormats() {
        assertEditing("odt-editable", testFile, EditingKind.DOCUMENT)
        assertEditing("docx-editable", docxTestFile, EditingKind.DOCUMENT)
        assertEditing("pptx-editable", pptxTestFile, EditingKind.DOCUMENT)
        assertEditing("ods-editable", spreadsheetTestFile, EditingKind.SHEET)
        assertEditing("pdf-editable", pdfTestFile, EditingKind.ANNOTATION)

        // the core declares the three legacy binary formats read only
        assertEditing("doc-editable", docTestFile, EditingKind.NONE)
        assertEditing("ppt-editable", pptTestFile, EditingKind.NONE)
        assertEditing("xls-editable", xlsTestFile, EditingKind.NONE)
    }

    private fun assertEditing(prefix: String, file: File, expected: EditingKind) {
        coreLoader.host(prefix = prefix, inputPath = file.absolutePath, askEditing = true)

        Assert.assertEquals(
            "what the core lets the user change in ${file.name}",
            expected,
            coreLoader.editing,
        )
    }

    @Test
    fun testPasswordProtectedDocumentWithoutPassword() {
        Assert.assertThrows(OdrException.FileEncrypted::class.java) {
            coreLoader.host(
                prefix = "password-test-no-pw",
                inputPath = passwordTestFile.absolutePath,
            )
        }
    }

    @Test
    fun testPasswordProtectedDocumentWithWrongPassword() {
        Assert.assertThrows(OdrException::class.java) {
            coreLoader.host(
                prefix = "password-test-wrong-pw",
                inputPath = passwordTestFile.absolutePath,
                password = "wrongpassword",
            )
        }
    }

    @Test
    fun testPasswordProtectedDocumentWithCorrectPassword() {
        val views =
            coreLoader.host(
                prefix = "password-test-correct-pw",
                inputPath = passwordTestFile.absolutePath,
                password = "passwort",
            )
        Assert.assertFalse("the decrypted document should produce a view", views.isEmpty())
    }

    /**
     * A document only held decrypted is read only, so no Edit button appears over it. The same odt
     * without a password is editable - see [testEditableFormats] - and saving this one would have
     * written the content back out without the protection its author asked for.
     */
    @Test
    fun testDecryptedDocumentIsNotEditable() {
        coreLoader.host(
            prefix = "password-test-editable",
            inputPath = passwordTestFile.absolutePath,
            password = "passwort",
            askEditing = true,
        )

        Assert.assertEquals(
            "a decrypted document should not be editable",
            EditingKind.NONE,
            coreLoader.editing,
        )
    }

    /**
     * An encrypted `.doc` says so rather than failing as a parse error, and odrcore has no way into
     * it, so [CoreLoader.host] refuses it instead of raising a prompt no password can close.
     */
    @Test
    fun testEncryptedLegacyDocument() {
        Assert.assertThrows(CoreLoader.UndecryptableFile::class.java) {
            coreLoader.host(
                prefix = "encrypted-doc",
                inputPath = encryptedDocTestFile.absolutePath,
            )
        }

        // and a password does not get it any further
        Assert.assertThrows(CoreLoader.UndecryptableFile::class.java) {
            coreLoader.host(
                prefix = "encrypted-doc-pw",
                inputPath = encryptedDocTestFile.absolutePath,
                password = "passwort",
            )
        }
    }

    /** The refusal is per format, so an odf document odrcore can decrypt still prompts. */
    @Test
    fun testEncryptedOdfDocumentStillPrompts() {
        Assert.assertThrows(OdrException.FileEncrypted::class.java) {
            coreLoader.host(
                prefix = "encrypted-odt-prompts",
                inputPath = passwordTestFile.absolutePath,
            )
        }
    }

    /** A pdf behind the http response that delivered it opens only once the name is asked. */
    @Test
    fun testWhatTheFileIsCalledOpensWhatDetectionCannotRead() {
        val prefixed = File(cacheDir(), "http-prefixed.pdf")
        prefixed.writeBytes(HTTP_PREAMBLE.toByteArray() + pdfTestFile.readBytes())

        // the preamble puts the signature off the front, and the nul bytes behind it stop the
        // core reading the whole thing as text
        Assert.assertThrows(OdrException.UnknownFileType::class.java) {
            firstView("preamble-detected", prefixed, declaredType = null)
        }

        val asNamed = firstView("preamble-named", prefixed, FileType.PORTABLE_DOCUMENT_FORMAT)
        Assert.assertFalse(
            "a file called .pdf should open as one rather than as its own source",
            asNamed.contains("HTTP/1.0 200 OK"),
        )
    }

    /** Only a text reading is outranked, so a real format still opens as itself. */
    @Test
    fun testWhatTheFileIsCalledDoesNotOverrideARealFormat() {
        val views =
            coreLoader.host(
                prefix = "odt-called-pdf",
                inputPath = testFile.absolutePath,
                declaredType = FileType.PORTABLE_DOCUMENT_FORMAT,
            )

        Assert.assertFalse("the odt should still open as an odt", views.isEmpty())
    }

    /** The html odrcore serves for [file]'s first view. */
    private fun firstView(prefix: String, file: File, declaredType: FileType?): String {
        val views =
            coreLoader.host(
                prefix = prefix,
                inputPath = file.absolutePath,
                declaredType = declaredType,
            )

        return URL(views.first().url).readText()
    }

    /**
     * A cut sheet says how much of it was written. Generated rather than shipped, since
     * `SpreadsheetBudget` answers per device.
     */
    @Test
    fun aSheetPastTheBudgetSaysWhatItLeftOut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val budget = SpreadsheetBudget.cells(context)

        val columns = 10
        val expectedRows = (budget / columns).toInt()
        val rows = expectedRows + 500

        val views =
            coreLoader.host(
                prefix = "big-sheet",
                inputPath = generateCsv(rows, columns).absolutePath,
            )

        val cut =
            checkNotNull(views.first().sheetCut) { "a sheet past the budget should report a cut" }

        Assert.assertEquals("every row written should be counted", rows, cut.contentRows)
        Assert.assertEquals(columns, cut.contentColumns)
        Assert.assertEquals("the budget decides the rows", expectedRows, cut.renderedRows)
        Assert.assertEquals("a narrow sheet loses no columns", columns, cut.renderedColumns)
    }

    /** The other side of it: a sheet written whole reports nothing to say. */
    @Test
    fun aSheetInsideTheBudgetReportsNoCut() {
        val views =
            coreLoader.host(
                prefix = "whole-sheet",
                inputPath = spreadsheetTestFile.absolutePath,
            )

        views.forEach { Assert.assertNull("nothing was cut from " + it.name, it.sheetCut) }
    }

    @Test
    fun testSpreadsheetSheetNames() {
        val views =
            coreLoader.host(
                prefix = "spreadsheet-test",
                inputPath = spreadsheetTestFile.absolutePath,
            )

        Assert.assertEquals("ODS file should contain 3 sheets", 3, views.size)

        Assert.assertEquals("First sheet should be named 'hey'", "hey", views[0].name)
        Assert.assertEquals("Second sheet should be named 'ho'", "ho", views[1].name)
        Assert.assertEquals("Third sheet should be named 'Sheet3'", "Sheet3", views[2].name)
    }

    companion object {

        private var sharedLoader: CoreLoader? = null

        // extracted once for the whole class: nothing here writes to a fixture
        private val extracted = mutableListOf<File>()

        private lateinit var testFile: File
        private lateinit var passwordTestFile: File
        private lateinit var spreadsheetTestFile: File
        private lateinit var docxTestFile: File
        private lateinit var pptxTestFile: File
        private lateinit var docTestFile: File
        private lateinit var pptTestFile: File
        private lateinit var xlsTestFile: File
        private lateinit var encryptedDocTestFile: File
        private lateinit var pdfTestFile: File

        /** The address the editable render puts on a run of text. */
        private val RUN_ID = Regex("""<x-s[^>]*data-odr-id="(\d+)"""")

        private const val EDITED = "Edited by CoreTest"

        /** What a document saved straight out of a browser carries in front of itself. */
        private const val HTTP_PREAMBLE =
            "HTTP/1.0 200 OK\r\n" +
                "Cache-Control:       no-cache, private\r\n" +
                "Content-Disposition: inline\r\n" +
                "Content-Type:        application/pdf\r\n\r\n"

        // @JvmStatic because junit requires @BeforeClass / @AfterClass to be static
        @JvmStatic
        @BeforeClass
        fun extractTestFiles() {
            testFile = extract("test.odt")
            passwordTestFile = extract("password-test.odt")
            spreadsheetTestFile = extract("spreadsheet-test.ods")
            docxTestFile = extract("style-various-1.docx")
            pptxTestFile = extract("style-various-1.pptx")
            docTestFile = extract("11KB.doc")
            pptTestFile = extract("style-various-1.ppt")
            xlsTestFile = extract("file_example_XLS_10.xls")
            encryptedDocTestFile = extract("encrypted.doc")
            pdfTestFile = extract("dummy.pdf")
        }

        @JvmStatic
        @AfterClass
        fun cleanupTestFiles() {
            extracted.forEach { it.delete() }
            extracted.clear()
        }

        @JvmStatic
        @BeforeClass
        fun startServer() {
            val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

            // every test here calls host() straight, so all initialize has to do is start the
            // core and its server
            val loader = CoreLoader(appCtx)
            sharedLoader = loader
            loader.initialize(CrashManager())
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            sharedLoader?.close()
            sharedLoader = null
        }

        private fun cacheDir(): File =
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

        /** A csv of [rows] x [columns] cells, each one a short string. */
        private fun generateCsv(rows: Int, columns: Int): File {
            val target = File(cacheDir(), "generated-sheet.csv")

            target.bufferedWriter().use { writer ->
                for (row in 1..rows) {
                    for (column in 1..columns) {
                        if (column > 1) {
                            writer.write(",")
                        }

                        writer.write("r")
                        writer.write(row.toString())
                        writer.write("c")
                        writer.write(column.toString())
                    }

                    writer.write("\n")
                }
            }
            extracted += target

            return target
        }

        private fun extract(name: String): File {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val target = File(instrumentation.targetContext.cacheDir, name)

            instrumentation.context.assets.open(name).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            extracted += target

            return target
        }
    }
}
