package at.tomtasche.reader.test;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import at.tomtasche.reader.background.CoreWrapper;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class CoreTest {
    private File m_testFile;
    private File m_passwordTestFile;
    private File m_coreLibTestFile;

    @Before
    public void initializeCore() {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CoreWrapper.initialize(appCtx);
    }

    @Before
    public void extractTestFile() throws IOException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        m_testFile = new File(appCtx.getCacheDir(), "test.odt");
        m_passwordTestFile = new File(appCtx.getCacheDir(), "password-test.odt");
        m_coreLibTestFile = new File(appCtx.getCacheDir(), "style-various-1.odt");

        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        AssetManager assetManager = testCtx.getAssets();
        try (InputStream inputStream = assetManager.open("test.odt")) {
            copy(inputStream, m_testFile);
        }
        try (InputStream inputStream = assetManager.open("password-test.odt")) {
            copy(inputStream, m_passwordTestFile);
        }
        try (InputStream inputStream = assetManager.open("style-various-1.odt")) {
            copy(inputStream, m_coreLibTestFile);
        }
    }

    @After
    public void cleanupTestFile() {
        if (null != m_testFile) {
            m_testFile.delete();
        }
        if (null != m_passwordTestFile) {
            m_passwordTestFile.delete();
        }
        if (null != m_coreLibTestFile) {
            m_coreLibTestFile.delete();
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
        coreOptions.editable = true;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);

        File resultFile = new File(cacheDir, "result");
        coreOptions.outputPath = resultFile.getPath();

        String htmlDiff = "{\"modifiedText\":{\"/column:2/column:0\":\"This is a simple testooo document to demonstrate the DocumentLoader example!\"}}";

        CoreWrapper.CoreResult result = CoreWrapper.backtranslate(coreOptions, htmlDiff);
        Assert.assertEquals(0, result.errorCode);
    }

    @Test
    public void testPasswordProtectedDocumentWithoutPassword() {
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputDir = new File(cacheDir, "output_password_test");
        File cachePath = new File(cacheDir, "core_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_passwordTestFile.getAbsolutePath();
        coreOptions.outputPath = outputDir.getPath();
        coreOptions.editable = false;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals(-2, coreResult.errorCode);
    }

    @Test
    public void testPasswordProtectedDocumentWithWrongPassword() {
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputDir = new File(cacheDir, "output_password_test");
        File cachePath = new File(cacheDir, "core_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_passwordTestFile.getAbsolutePath();
        coreOptions.outputPath = outputDir.getPath();
        coreOptions.password = "wrongpassword";
        coreOptions.editable = false;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals(-2, coreResult.errorCode);
    }

    @Test
    public void testPasswordProtectedDocumentWithCorrectPassword() {
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputDir = new File(cacheDir, "output_password_test");
        File cachePath = new File(cacheDir, "core_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_passwordTestFile.getAbsolutePath();
        coreOptions.outputPath = outputDir.getPath();
        coreOptions.password = "passwort";
        coreOptions.editable = false;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult coreResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);
    }

    @Test
    public void testCoreLibraryEditFormat() {
        // This test exactly mirrors the core library's edit_odt_diff test
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputPath = new File(cacheDir, "core_output_style");
        File cachePath = new File(cacheDir, "core_cache_style");

        // Parse the document with editable=true
        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_coreLibTestFile.getAbsolutePath();
        coreOptions.outputPath = outputPath.getPath();
        coreOptions.editable = true;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult parseResult = CoreWrapper.parse(coreOptions);
        Assert.assertEquals("Parse should succeed", 0, parseResult.errorCode);

        // Use the exact same diff format as the core library test
        String htmlDiff = "{\"modifiedText\":{\"/child:16/child:0\":\"Outasdfsdafdline\",\"/child:24/child:0\":\"Colorasdfasdfasdfed Line\",\"/child:6/child:0\":\"Text hello world!\"}}";

        // Set output path for the edited file
        File editedFile = new File(cacheDir, "style-various-1_edit_diff");
        coreOptions.outputPath = editedFile.getPath();

        // Perform the edit
        CoreWrapper.CoreResult editResult = CoreWrapper.backtranslate(coreOptions, htmlDiff);
        Assert.assertEquals("Edit should succeed", 0, editResult.errorCode);

        // Verify the file was created
        File outputFile = new File(editResult.outputPath);
        Log.e("CoreTest", "Edited file saved to: " + outputFile.getAbsolutePath());
        Log.e("CoreTest", "File size: " + outputFile.length() + " bytes");
        Assert.assertTrue("Edited file should exist", outputFile.exists());
        Assert.assertTrue("Edited file should have content", outputFile.length() > 0);

        // Let's verify the edit actually worked by re-parsing the edited file
        CoreWrapper.CoreOptions verifyOptions = new CoreWrapper.CoreOptions();
        verifyOptions.inputPath = outputFile.getAbsolutePath();
        File verifyOutput = new File(cacheDir, "verify_output");
        verifyOptions.outputPath = verifyOutput.getPath();
        verifyOptions.editable = false;
        verifyOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult verifyResult = CoreWrapper.parse(verifyOptions);
        Assert.assertEquals("Edited file should be parseable", 0, verifyResult.errorCode);
        Log.e("CoreTest", "Successfully verified edited file can be reopened");

        // Try to copy to app's external files directory which should be accessible
        try {
            Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            File externalFilesDir = appContext.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                File externalCopy = new File(externalFilesDir, "edited_test_output.odt");
                copy(new java.io.FileInputStream(outputFile), externalCopy);
                Log.e("CoreTest", "Copied to external: " + externalCopy.getAbsolutePath());

                // Also try to make it world-readable
                externalCopy.setReadable(true, false);

                // Keep file available for 10 seconds so we can pull it
                Log.e("CoreTest", "Waiting 10 seconds - pull the file now!");
                Thread.sleep(10000);
            }
        } catch (Exception e) {
            Log.e("CoreTest", "Failed to copy to external: " + e.getMessage());
        }
    }
}
