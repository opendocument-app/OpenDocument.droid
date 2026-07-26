package at.tomtasche.reader.background;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OnlineLoaderTest {

    private OnlineLoader onlineLoader;

    @Before
    public void setUp() {
        onlineLoader = new OnlineLoader(null, new CoreLoader(null, true));
    }

    private FileLoader.Options options(String fileType) {
        FileLoader.Options options = new FileLoader.Options();
        options.fileType = fileType;
        return options;
    }

    private boolean isSupported(String fileType) {
        return onlineLoader.isSupported(options(fileType));
    }

    private boolean isConvertible(String fileType) {
        return onlineLoader.isConvertible(options(fileType));
    }

    @Test
    public void supportsOfficeDocuments() {
        Assert.assertTrue(isSupported("application/vnd.oasis.opendocument.text"));
        Assert.assertTrue(
                isSupported(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        Assert.assertTrue(isSupported("application/msword"));
        Assert.assertTrue(isSupported("application/vnd.ms-excel"));
        Assert.assertTrue(isSupported("application/vnd.ms-powerpoint"));
        Assert.assertTrue(isSupported("application/pdf"));
        Assert.assertTrue(isSupported("application/rtf"));
        Assert.assertTrue(isSupported("application/vnd.wordperfect"));
    }

    @Test
    public void supportsGenericPreviewableTypes() {
        Assert.assertTrue(isSupported("text/plain"));
        Assert.assertTrue(isSupported("image/png"));
        Assert.assertTrue(isSupported("application/zip"));
    }

    @Test
    public void rejectsBlacklistedMimeTypes() {
        Assert.assertFalse(isSupported("image/x-tga"));
        Assert.assertFalse(isSupported("image/vnd.djvu"));
        Assert.assertFalse(isSupported("audio/amr"));
        Assert.assertFalse(isSupported("video/3gpp"));
        Assert.assertFalse(isSupported("text/calendar"));
        // contacts must not be offered for upload - see issue #477
        Assert.assertFalse(isSupported("text/vcard"));
    }

    @Test
    public void rejectsUnknownMimeTypes() {
        Assert.assertFalse(isSupported("application/octet-stream"));
        Assert.assertFalse(isSupported("application/x-made-up"));
    }

    @Test
    public void convertsOfficeDocumentsItself() {
        Assert.assertTrue(isConvertible("application/pdf"));
        Assert.assertTrue(isConvertible("text/rtf"));
        Assert.assertTrue(isConvertible("application/vnd.wordperfect"));
        Assert.assertTrue(isConvertible("application/vnd.ms-excel"));
        Assert.assertTrue(isConvertible("application/msword"));
        Assert.assertTrue(isConvertible("application/vnd.ms-powerpoint"));
        Assert.assertTrue(
                isConvertible(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        // delegated to CoreLoader
        Assert.assertTrue(isConvertible("application/vnd.oasis.opendocument.text"));
    }

    @Test
    public void handsEverythingElseToAThirdPartyViewer() {
        Assert.assertFalse(isConvertible("text/plain"));
        Assert.assertFalse(isConvertible("image/png"));
        Assert.assertFalse(isConvertible("application/zip"));
        Assert.assertFalse(isConvertible("application/vnd.ms-excel.sheet.macroEnabled.12"));
    }
}
