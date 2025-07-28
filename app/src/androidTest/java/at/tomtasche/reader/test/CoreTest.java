package at.tomtasche.reader.test;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import at.tomtasche.reader.background.CoreWrapper;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class CoreTest {
    private File m_testFile;

    @Before
    public void initializeCore() {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CoreWrapper.initialize(appCtx);
    }

    @Before
    public void extractTestFile() throws IOException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        m_testFile = new File(appCtx.getCacheDir(), "test.odt");

        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        AssetManager assetManager = testCtx.getAssets();
        try (InputStream inputStream = assetManager.open("test.odt")) {
            copy(inputStream, m_testFile);
        }
    }

    @After
    public void cleanupTestFile() {
        if (null != m_testFile) {
            m_testFile.delete();
        }
    }

    private static void copy(InputStream src, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = src.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    @Test
    public void test() {
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputPath = new File(cacheDir, "core_output");
        File cachePath = new File(cacheDir, "core_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_testFile.getAbsolutePath();
        coreOptions.outputPath = outputPath.getPath();
        coreOptions.cachePath = cachePath.getPath();
        coreOptions.editable = true;

        CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);

        File resultFile = new File(cacheDir, "result");
        coreOptions.outputPath = resultFile.getPath();

        String htmlDiff = "{\"modifiedText\":{\"3\":\"This is a simple test document to demonstrate the DocumentLoadewwwwr example!\"}}";

        CoreWrapper.CoreResult result = CoreWrapper.backtranslate(coreOptions, htmlDiff);
        Assert.assertEquals(0, coreResult.errorCode);
    }
}
