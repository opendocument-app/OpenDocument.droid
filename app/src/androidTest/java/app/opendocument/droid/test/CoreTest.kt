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
    }

    @After
    fun cleanupTestFile() {
        testFile?.delete()
        passwordTestFile?.delete()
        spreadsheetTestFile?.delete()
        docxTestFile?.delete()
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
            val loader = CoreLoader(appCtx, true)
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
