package at.tomtasche.reader.background;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OnlineLoaderTest {

    private OnlineLoader onlineLoader;

    @Before
    public void setUp() {
        onlineLoader = new OnlineLoader(null, null);
    }

    private boolean isSupported(String fileType) {
        FileLoader.Options options = new FileLoader.Options();
        options.fileType = fileType;
        return onlineLoader.isSupported(options);
    }

    @Test
    public void supportsOfficeDocuments() {
        Assert.assertTrue(isSupported("application/vnd.oasis.opendocument.text"));
        Assert.assertTrue(isSupported("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
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
}
