package app.opendocument.droid.test

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.core.OdrException
import app.opendocument.droid.background.CoreLoader
import app.opendocument.droid.background.FileLoader
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CoreTest {

    // nullable like the java fields were: a failure while extracting must not turn into an
    // "uninitialized" error from the cleanup that follows it
    private var testFile: File? = null
    private var passwordTestFile: File? = null
    private var spreadsheetTestFile: File? = null
    private var docxTestFile: File? = null
    private var pptxTestFile: File? = null
    private var docTestFile: File? = null
    private var pptTestFile: File? = null
    private var xlsTestFile: File? = null

    private val coreLoader: CoreLoader
        get() = checkNotNull(sharedLoader) { "the core loader was not started" }

    private fun requireFile(file: File?): File = checkNotNull(file) { "the test file is missing" }

    @Before
    fun extractTestFile() {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets

        testFile = extract(assetManager.open("test.odt"), File(appCtx.cacheDir, "test.odt"))
        passwordTestFile =
            extract(
                assetManager.open("password-test.odt"),
                File(appCtx.cacheDir, "password-test.odt"),
            )
        spreadsheetTestFile =
            extract(
                assetManager.open("spreadsheet-test.ods"),
                File(appCtx.cacheDir, "spreadsheet-test.ods"),
            )
        docxTestFile =
            extract(
                assetManager.open("style-various-1.docx"),
                File(appCtx.cacheDir, "style-various-1.docx"),
            )
        pptxTestFile =
            extract(
                assetManager.open("style-various-1.pptx"),
                File(appCtx.cacheDir, "style-various-1.pptx"),
            )
        docTestFile = extract(assetManager.open("11KB.doc"), File(appCtx.cacheDir, "11KB.doc"))
        pptTestFile =
            extract(
                assetManager.open("style-various-1.ppt"),
                File(appCtx.cacheDir, "style-various-1.ppt"),
            )
        xlsTestFile =
            extract(
                assetManager.open("file_example_XLS_10.xls"),
                File(appCtx.cacheDir, "file_example_XLS_10.xls"),
            )
    }

    @After
    fun cleanupTestFile() {
        testFile?.delete()
        passwordTestFile?.delete()
        spreadsheetTestFile?.delete()
        docxTestFile?.delete()
        pptxTestFile?.delete()
        docTestFile?.delete()
        pptTestFile?.delete()
        xlsTestFile?.delete()
    }

    @Test
    fun test() {
        val views =
            coreLoader.host(
                prefix = "test",
                inputPath = requireFile(testFile).absolutePath,
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
                inputPath = requireFile(docxTestFile).absolutePath,
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
                inputPath = requireFile(pptxTestFile).absolutePath,
                cachePath = File(cacheDir(), "pptx_cache").path,
            )
        Assert.assertFalse("hosting the PPTX file should produce a view", views.isEmpty())
    }

    @Test
    fun testDoc() {
        val views =
            coreLoader.host(
                prefix = "doc-test",
                inputPath = requireFile(docTestFile).absolutePath,
                cachePath = File(cacheDir(), "doc_cache").path,
            )
        Assert.assertFalse("hosting the DOC file should produce a view", views.isEmpty())
    }

    @Test
    fun testPpt() {
        val views =
            coreLoader.host(
                prefix = "ppt-test",
                inputPath = requireFile(pptTestFile).absolutePath,
                cachePath = File(cacheDir(), "ppt_cache").path,
            )
        Assert.assertFalse("hosting the PPT file should produce a view", views.isEmpty())
    }

    @Test
    fun testXls() {
        val views =
            coreLoader.host(
                prefix = "xls-test",
                inputPath = requireFile(xlsTestFile).absolutePath,
                cachePath = File(cacheDir(), "xls_cache").path,
            )
        Assert.assertFalse("hosting the XLS file should produce a view", views.isEmpty())
    }

    /**
     * Which of the formats the core renders it can also write back again - the answer
     * `DocumentFragment` puts the Edit button up by. Offering it for a document the core will not
     * save only gets as far as a failed save, which is what happened to the legacy binary formats
     * when they started going through the core.
     */
    @Test
    fun testEditableFormats() {
        assertEditable("odt-editable", requireFile(testFile), true)
        assertEditable("docx-editable", requireFile(docxTestFile), true)

        // the core declares these read only: the three legacy binary formats, ooxml presentations
        // and every spreadsheet - the last being issue #442, which the core has its own TODO for
        assertEditable("doc-editable", requireFile(docTestFile), false)
        assertEditable("ppt-editable", requireFile(pptTestFile), false)
        assertEditable("xls-editable", requireFile(xlsTestFile), false)
        assertEditable("pptx-editable", requireFile(pptxTestFile), false)
        assertEditable("ods-editable", requireFile(spreadsheetTestFile), false)
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
                inputPath = requireFile(passwordTestFile).absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
            )
        }
    }

    @Test
    fun testPasswordProtectedDocumentWithWrongPassword() {
        Assert.assertThrows(OdrException::class.java) {
            coreLoader.host(
                prefix = "password-test-wrong-pw",
                inputPath = requireFile(passwordTestFile).absolutePath,
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
                inputPath = requireFile(passwordTestFile).absolutePath,
                cachePath = File(cacheDir(), "core_cache").path,
                password = "passwort",
            )
        Assert.assertFalse("the decrypted document should produce a view", views.isEmpty())
    }

    @Test
    fun testSpreadsheetSheetNames() {
        val views =
            coreLoader.host(
                prefix = "spreadsheet-test",
                inputPath = requireFile(spreadsheetTestFile).absolutePath,
                cachePath = File(cacheDir(), "spreadsheet_cache").path,
            )

        // Verify we have exactly 3 sheets
        Assert.assertEquals("ODS file should contain 3 sheets", 3, views.size)

        // Verify sheet names match the actual sheet names from the ODS file
        Assert.assertEquals("First sheet should be named 'hey'", "hey", views[0].name)
        Assert.assertEquals("Second sheet should be named 'ho'", "ho", views[1].name)
        Assert.assertEquals("Third sheet should be named 'Sheet3'", "Sheet3", views[2].name)
    }

    companion object {

        private var sharedLoader: CoreLoader? = null

        // @JvmStatic because junit requires @BeforeClass / @AfterClass to be static
        @JvmStatic
        @BeforeClass
        fun startServer() {
            val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

            // the loader owns the core lifecycle now, so the test drives a real one rather than a
            // set of statics. nothing here goes through loadAsync, so both handlers can be the main
            // looper and the listener is never called back
            val handler = Handler(Looper.getMainLooper())
            val loader = CoreLoader(appCtx)
            sharedLoader = loader
            loader.initialize(
                object : FileLoader.FileLoaderListener {
                    override fun onSuccess(result: FileLoader.Result) {}

                    override fun onError(result: FileLoader.Result, error: Throwable) {}
                },
                handler,
                handler,
                AnalyticsManager(),
                CrashManager(),
            )

            // no waiting for the server here: these tests call host() and edit() straight on
            // the loader and never fetch a url, so whether the socket is listening yet does
            // not come into it. it used to sleep a second for it anyway
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            sharedLoader?.close()
            sharedLoader = null
        }

        private fun cacheDir(): File =
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

        private fun extract(source: InputStream, target: File): File {
            source.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }

            return target
        }
    }
}
