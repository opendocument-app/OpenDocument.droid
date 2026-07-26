package at.tomtasche.reader.background

import org.junit.Assert
import org.junit.Test

class MimeTypeResolverTest {

    /** Stands in for MimeTypeMap, which is not available on the JVM. */
    private class FakeLookup : MimeTypeResolver.ExtensionLookup {

        private val mimeToExtension = HashMap<String, String>()
        private val extensionToMime = HashMap<String, String>()

        fun with(mimeType: String, extension: String): FakeLookup {
            mimeToExtension[mimeType] = extension
            extensionToMime[extension] = mimeType
            return this
        }

        override fun extensionFromMimeType(mimeType: String): String? = mimeToExtension[mimeType]

        override fun mimeTypeFromExtension(extension: String?): String? = extension?.let {
            extensionToMime[it]
        }
    }

    private fun lookup(): FakeLookup =
        FakeLookup()
            .with("application/vnd.oasis.opendocument.text", "odt")
            .with("application/pdf", "pdf")

    @Test
    fun parsesExtensionFromFilename() {
        Assert.assertEquals("odt", MimeTypeResolver.parseExtension("report.odt"))
        Assert.assertEquals("odt", MimeTypeResolver.parseExtension("my.backup.report.odt"))
        Assert.assertEquals("ODT", MimeTypeResolver.parseExtension("report.ODT"))
    }

    @Test
    fun parsesNoExtensionWhenThereIsNone() {
        // regression: the old split("\\.") based code returned the whole filename here
        Assert.assertNull(MimeTypeResolver.parseExtension("README"))
        Assert.assertNull(MimeTypeResolver.parseExtension(".bashrc"))
        Assert.assertNull(MimeTypeResolver.parseExtension("report."))
        Assert.assertNull(MimeTypeResolver.parseExtension(""))
        Assert.assertNull(MimeTypeResolver.parseExtension(null))
    }

    @Test
    fun knownMimeTypeWinsOverFilenameExtension() {
        val resolution = MimeTypeResolver.resolve("application/pdf", "bin", lookup())

        Assert.assertEquals("application/pdf", resolution.mimeType)
        Assert.assertEquals("pdf", resolution.extension)
    }

    @Test
    fun filenameExtensionSurvivesUnknownMimeType() {
        // regression: this used to null out the extension, so features building on
        // options.fileExtension (share/open-with) lost it
        val resolution = MimeTypeResolver.resolve("application/x-made-up", "odt", lookup())

        Assert.assertEquals("application/x-made-up", resolution.mimeType)
        Assert.assertEquals("odt", resolution.extension)
    }

    @Test
    fun mimeTypeIsLookedUpFromExtensionWhenUndetected() {
        // regression: this branch was unreachable, because it tested the loader type
        // instead of the mime type
        val resolution = MimeTypeResolver.resolve(null, "odt", lookup())

        Assert.assertEquals("application/vnd.oasis.opendocument.text", resolution.mimeType)
        Assert.assertEquals("odt", resolution.extension)
    }

    @Test
    fun resolvesToNothingWhenNeitherIsKnown() {
        val resolution = MimeTypeResolver.resolve(null, "xyz", lookup())

        Assert.assertNull(resolution.mimeType)
        Assert.assertEquals("xyz", resolution.extension)
    }

    @Test
    fun toleratesMissingExtension() {
        val resolution = MimeTypeResolver.resolve(null, null, lookup())

        Assert.assertNull(resolution.mimeType)
        Assert.assertNull(resolution.extension)
    }
}
