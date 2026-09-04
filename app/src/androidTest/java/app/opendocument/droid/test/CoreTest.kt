package app.opendocument.droid.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.FileType
import app.opendocument.core.OdrException
import app.opendocument.droid.background.CoreLoader
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
    fun test() {
        val views =
            coreLoader.host(
                prefix = "test",
                inputPath = testFile.absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
                editable = true,
                keepDocument = true,
            )
        Assert.assertFalse("hosting the ODT file should produce a view", views.isEmpty())

        val htmlDiff =
            "{\"modifiedText\":{\"/child:1/child:0\":\"This is a simple testoooo document to" +
                " demonstrate the DocumentLoader example!\",\"/child:3/child:0\":\"This is a" +
                " simple testaaaa document to demonstrate the DocumentLoader example!\"}}"

        val result = coreLoader.edit(htmlDiff, File(cacheDir(), "result").path)
        Assert.assertTrue("the edited document should have been saved", result.isFile)
    }

    @Test
    fun testDocxEdit() {
        val views =
            coreLoader.host(
                prefix = "docx-edit",
                inputPath = docxTestFile.absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
                editable = true,
                keepDocument = true,
            )
        Assert.assertFalse("hosting the DOCX file should produce a view", views.isEmpty())

        val htmlDiff =
            "{\"modifiedText\":{\"/child:16/child:0/child:0\":\"Outasdfsdafdline\",\"/child:24/child:0/child:0\":\"Colorasdfasdfasdfed" +
                " Line\",\"/child:6/child:0/child:0\":\"Text hello world!\"}}"

        val result = coreLoader.edit(htmlDiff, File(cacheDir(), "result_docx").path)
        Assert.assertTrue("the edited document should have been saved", result.isFile)
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
                cachePath = File(cacheDir(), "pptx_cache").path,
            )
        Assert.assertFalse("hosting the PPTX file should produce a view", views.isEmpty())
    }

    @Test
    fun testDoc() {
        val views =
            coreLoader.host(
                prefix = "doc-test",
                inputPath = docTestFile.absolutePath,
                cachePath = File(cacheDir(), "doc_cache").path,
            )
        Assert.assertFalse("hosting the DOC file should produce a view", views.isEmpty())
    }

    @Test
    fun testPpt() {
        val views =
            coreLoader.host(
                prefix = "ppt-test",
                inputPath = pptTestFile.absolutePath,
                cachePath = File(cacheDir(), "ppt_cache").path,
            )
        Assert.assertFalse("hosting the PPT file should produce a view", views.isEmpty())
    }

    @Test
    fun testXls() {
        val views =
            coreLoader.host(
                prefix = "xls-test",
                inputPath = xlsTestFile.absolutePath,
                cachePath = File(cacheDir(), "xls_cache").path,
            )
        Assert.assertFalse("hosting the XLS file should produce a view", views.isEmpty())
    }

    /**
     * Which of the formats the core renders it can also write back again - the answer
     * `DocumentFragment` puts the Edit button up by.
     */
    @Test
    fun testEditableFormats() {
        assertEditable("odt-editable", testFile, true)
        assertEditable("docx-editable", docxTestFile, true)

        // the core declares these read only: the three legacy binary formats, ooxml presentations
        // and every spreadsheet - the last being issue #442, which the core has its own TODO for
        assertEditable("doc-editable", docTestFile, false)
        assertEditable("ppt-editable", pptTestFile, false)
        assertEditable("xls-editable", xlsTestFile, false)
        assertEditable("pptx-editable", pptxTestFile, false)
        assertEditable("ods-editable", spreadsheetTestFile, false)
    }

    private fun assertEditable(prefix: String, file: File, expected: Boolean) {
        coreLoader.host(
            prefix = prefix,
            inputPath = file.absolutePath,
            cachePath = File(cacheDir(), prefix).path,
            editable = true,
            keepDocument = true,
        )

        Assert.assertEquals(
            "the core should report ${file.name} as ${if (expected) "editable" else "read only"}",
            expected,
            coreLoader.isDocumentEditable,
        )
    }

    @Test
    fun testPasswordProtectedDocumentWithoutPassword() {
        Assert.assertThrows(OdrException.FileEncrypted::class.java) {
            coreLoader.host(
                prefix = "password-test-no-pw",
                inputPath = passwordTestFile.absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
            )
        }
    }

    @Test
    fun testPasswordProtectedDocumentWithWrongPassword() {
        Assert.assertThrows(OdrException::class.java) {
            coreLoader.host(
                prefix = "password-test-wrong-pw",
                inputPath = passwordTestFile.absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
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
                cachePath = File(cacheDir(), "core_cache").path,
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
            cachePath = File(cacheDir(), "password_editable").path,
            password = "passwort",
            editable = true,
            keepDocument = true,
        )

        Assert.assertFalse(
            "a decrypted document should not be editable",
            coreLoader.isDocumentEditable,
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
                cachePath = File(cacheDir(), "encrypted_doc_cache").path,
            )
        }

        // and a password does not get it any further
        Assert.assertThrows(CoreLoader.UndecryptableFile::class.java) {
            coreLoader.host(
                prefix = "encrypted-doc-pw",
                inputPath = encryptedDocTestFile.absolutePath,
                cachePath = File(cacheDir(), "encrypted_doc_cache").path,
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
                cachePath = File(cacheDir(), "core_cache").path,
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
                cachePath = File(cacheDir(), "odt_called_pdf").path,
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
                cachePath = File(cacheDir(), prefix).path,
                declaredType = declaredType,
            )

        return URL(views.first().url).readText()
    }

    @Test
    fun testSpreadsheetSheetNames() {
        val views =
            coreLoader.host(
                prefix = "spreadsheet-test",
                inputPath = spreadsheetTestFile.absolutePath,
                cachePath = File(cacheDir(), "spreadsheet_cache").path,
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
