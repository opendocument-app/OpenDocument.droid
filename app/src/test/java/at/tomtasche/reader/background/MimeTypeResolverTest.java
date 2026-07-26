package at.tomtasche.reader.background;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MimeTypeResolverTest {

    /**
     * Stands in for MimeTypeMap, which is not available on the JVM.
     */
    private static class FakeLookup implements MimeTypeResolver.ExtensionLookup {

        private final Map<String, String> mimeToExtension = new HashMap<>();
        private final Map<String, String> extensionToMime = new HashMap<>();

        FakeLookup with(String mimeType, String extension) {
            mimeToExtension.put(mimeType, extension);
            extensionToMime.put(extension, mimeType);
            return this;
        }

        @Override
        public String extensionFromMimeType(String mimeType) {
            return mimeToExtension.get(mimeType);
        }

        @Override
        public String mimeTypeFromExtension(String extension) {
            return extensionToMime.get(extension);
        }
    }

    private static FakeLookup lookup() {
        return new FakeLookup()
                .with("application/vnd.oasis.opendocument.text", "odt")
                .with("application/pdf", "pdf");
    }

    @Test
    public void parsesExtensionFromFilename() {
        Assert.assertEquals("odt", MimeTypeResolver.parseExtension("report.odt"));
        Assert.assertEquals("odt", MimeTypeResolver.parseExtension("my.backup.report.odt"));
        Assert.assertEquals("ODT", MimeTypeResolver.parseExtension("report.ODT"));
    }

    @Test
    public void parsesNoExtensionWhenThereIsNone() {
        // regression: the old split("\\.") based code returned the whole filename here
        Assert.assertNull(MimeTypeResolver.parseExtension("README"));
        Assert.assertNull(MimeTypeResolver.parseExtension(".bashrc"));
        Assert.assertNull(MimeTypeResolver.parseExtension("report."));
        Assert.assertNull(MimeTypeResolver.parseExtension(""));
        Assert.assertNull(MimeTypeResolver.parseExtension(null));
    }

    @Test
    public void knownMimeTypeWinsOverFilenameExtension() {
        MimeTypeResolver.Resolution resolution =
                MimeTypeResolver.resolve("application/pdf", "bin", lookup());

        Assert.assertEquals("application/pdf", resolution.mimeType);
        Assert.assertEquals("pdf", resolution.extension);
    }

    @Test
    public void filenameExtensionSurvivesUnknownMimeType() {
        // regression: this used to null out the extension, so features building on
        // options.fileExtension (share/open-with) lost it
        MimeTypeResolver.Resolution resolution =
                MimeTypeResolver.resolve("application/x-made-up", "odt", lookup());

        Assert.assertEquals("application/x-made-up", resolution.mimeType);
        Assert.assertEquals("odt", resolution.extension);
    }

    @Test
    public void mimeTypeIsLookedUpFromExtensionWhenUndetected() {
        // regression: this branch was unreachable, because it tested the loader type
        // instead of the mime type
        MimeTypeResolver.Resolution resolution =
                MimeTypeResolver.resolve(null, "odt", lookup());

        Assert.assertEquals("application/vnd.oasis.opendocument.text", resolution.mimeType);
        Assert.assertEquals("odt", resolution.extension);
    }

    @Test
    public void resolvesToNothingWhenNeitherIsKnown() {
        MimeTypeResolver.Resolution resolution =
                MimeTypeResolver.resolve(null, "xyz", lookup());

        Assert.assertNull(resolution.mimeType);
        Assert.assertEquals("xyz", resolution.extension);
    }

    @Test
    public void toleratesMissingExtension() {
        MimeTypeResolver.Resolution resolution =
                MimeTypeResolver.resolve(null, null, lookup());

        Assert.assertNull(resolution.mimeType);
        Assert.assertNull(resolution.extension);
    }
}
