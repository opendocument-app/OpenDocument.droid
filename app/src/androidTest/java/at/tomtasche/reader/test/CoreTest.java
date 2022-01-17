package at.tomtasche.reader.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import at.tomtasche.reader.background.CoreWrapper;

@LargeTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class CoreTest {

    private String testFilePath;

    @Before
    public void setup() {
        // TODO: fix for Android 29+

        try {
            File file = new File(ApplicationProvider.getApplicationContext().getCacheDir(), "test.odt");
            testFilePath = file.getAbsolutePath();

            if (file.exists()) {
                return;
            }

            InputStream inputStream = new URL("https://api.libreoffice.org/examples/cpp/DocumentLoader/test.odt").openStream();
            copy(inputStream, file);
        } catch (IOException e) {
            e.printStackTrace();

            throw new RuntimeException(e);
        }
    }

    private static void copy(InputStream src, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = src.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            src.close();
        }
    }

    @Test
    public void test() {
        CoreWrapper core = new CoreWrapper();
        core.initialize();

        File htmlFile = new File(ApplicationProvider.getApplicationContext().getCacheDir(),"html");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = testFilePath;
        coreOptions.outputPath = htmlFile.getPath();
        coreOptions.editable = true;

        CoreWrapper.CoreResult coreResult = core.parse(coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);

        File resultFile = new File(ApplicationProvider.getApplicationContext().getCacheDir(),"result");
        coreOptions.outputPath = resultFile.getPath();

        String htmlDiff = "{\"modifiedText\":{\"3\":\"This is a simple test document to demonstrate the DocumentLoadewwwwr example!\"}}";

        CoreWrapper.CoreResult result = core.backtranslate(coreOptions, htmlDiff);
        Assert.assertEquals(0, coreResult.errorCode);
    }
}
