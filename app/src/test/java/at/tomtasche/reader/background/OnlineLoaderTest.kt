package at.tomtasche.reader.background

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OnlineLoaderTest {

    private lateinit var onlineLoader: OnlineLoader

    @Before
    fun setUp() {
        onlineLoader = OnlineLoader(null, CoreLoader(null, true))
    }

    private fun options(fileType: String): FileLoader.Options {
        val options = FileLoader.Options()
        options.fileType = fileType
        return options
    }

    private fun isSupported(fileType: String): Boolean = onlineLoader.isSupported(options(fileType))

    private fun isConvertible(fileType: String): Boolean =
        onlineLoader.isConvertible(options(fileType))

    @Test
    fun supportsOfficeDocuments() {
        Assert.assertTrue(isSupported("application/vnd.oasis.opendocument.text"))
        Assert.assertTrue(
            isSupported("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        Assert.assertTrue(isSupported("application/msword"))
        Assert.assertTrue(isSupported("application/vnd.ms-excel"))
        Assert.assertTrue(isSupported("application/vnd.ms-powerpoint"))
        Assert.assertTrue(isSupported("application/pdf"))
        Assert.assertTrue(isSupported("application/rtf"))
        Assert.assertTrue(isSupported("application/vnd.wordperfect"))
    }

    @Test
    fun supportsGenericPreviewableTypes() {
        Assert.assertTrue(isSupported("text/plain"))
        Assert.assertTrue(isSupported("image/png"))
        Assert.assertTrue(isSupported("application/zip"))
    }

    @Test
    fun rejectsBlacklistedMimeTypes() {
        Assert.assertFalse(isSupported("image/x-tga"))
        Assert.assertFalse(isSupported("image/vnd.djvu"))
        Assert.assertFalse(isSupported("audio/amr"))
        Assert.assertFalse(isSupported("video/3gpp"))
        Assert.assertFalse(isSupported("text/calendar"))
        // contacts must not be offered for upload - see issue #477
        Assert.assertFalse(isSupported("text/vcard"))
    }

    @Test
    fun rejectsUnknownMimeTypes() {
        Assert.assertFalse(isSupported("application/octet-stream"))
        Assert.assertFalse(isSupported("application/x-made-up"))
    }

    @Test
    fun convertsOfficeDocumentsItself() {
        Assert.assertTrue(isConvertible("application/pdf"))
        Assert.assertTrue(isConvertible("text/rtf"))
        Assert.assertTrue(isConvertible("application/vnd.wordperfect"))
        Assert.assertTrue(isConvertible("application/vnd.ms-excel"))
        Assert.assertTrue(isConvertible("application/msword"))
        Assert.assertTrue(isConvertible("application/vnd.ms-powerpoint"))
        Assert.assertTrue(
            isConvertible(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
        )
        // delegated to CoreLoader
        Assert.assertTrue(isConvertible("application/vnd.oasis.opendocument.text"))
    }

    @Test
    fun handsEverythingElseToAThirdPartyViewer() {
        Assert.assertFalse(isConvertible("text/plain"))
        Assert.assertFalse(isConvertible("image/png"))
        Assert.assertFalse(isConvertible("application/zip"))
        Assert.assertFalse(isConvertible("application/vnd.ms-excel.sheet.macroEnabled.12"))
    }
}
