package app.opendocument.droid.background

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.droid.nonfree.CrashManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreServerUriTest {
    @Test
    fun onlyDocumentUrlsOnTheCoreServerAreTrusted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = CoreLoader(context)
        loader.initialize(CrashManager())
        val file = java.io.File.createTempFile("origin", ".txt", context.cacheDir)
        val uri =
            try {
                file.writeText("document")
                Uri.parse(loader.host("origin-test", file.path).first().url)
            } finally {
                loader.close()
                file.delete()
            }
        val origin = "http://${uri.authority}"
        assertTrue(CoreLoader.isHostedUri(uri))
        for (url in
            listOf(
                "$origin@evil.example/file/odr0/document.html",
                "$origin/other",
                "http://localhost:1/file/odr0/document.html",
                "file:///data/local/tmp/document.html",
                "https://example.com/document.html",
            )) {
            assertFalse(url, CoreLoader.isHostedUri(Uri.parse(url)))
        }
    }
}
