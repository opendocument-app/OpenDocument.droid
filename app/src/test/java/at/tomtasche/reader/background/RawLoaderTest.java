package at.tomtasche.reader.background;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RawLoaderTest {

    private RawLoader rawLoader;

    @Before
    public void setUp() {
        rawLoader = new RawLoader(null);
    }

    private boolean isSupported(String fileType) {
        FileLoader.Options options = new FileLoader.Options();
        options.fileType = fileType;
        return rawLoader.isSupported(options);
    }

    @Test
    public void supportsWhitelistedMimeTypes() {
        Assert.assertTrue(isSupported("text/plain"));
        Assert.assertTrue(isSupported("image/png"));
        Assert.assertTrue(isSupported("video/mp4"));
        Assert.assertTrue(isSupported("audio/mpeg"));
        Assert.assertTrue(isSupported("application/json"));
        Assert.assertTrue(isSupported("application/xml"));
        Assert.assertTrue(isSupported("application/zip"));
    }

    @Test
    public void rejectsBlacklistedMimeTypes() {
        Assert.assertFalse(isSupported("image/vnd.dwg"));
        Assert.assertFalse(isSupported("image/tiff"));
        Assert.assertFalse(isSupported("audio/amr"));
        Assert.assertFalse(isSupported("video/quicktime"));
        Assert.assertFalse(isSupported("text/rtf"));
        Assert.assertFalse(isSupported("text/calendar"));
    }

    @Test
    public void rejectsUnknownMimeTypes() {
        Assert.assertFalse(isSupported("application/pdf"));
        Assert.assertFalse(isSupported("application/vnd.oasis.opendocument.text"));
        Assert.assertFalse(isSupported("application/octet-stream"));
    }
}
