package at.tomtasche.reader.test;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
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
    private static Thread serverThread;
    private File m_testFile;
    private File m_passwordTestFile;
    private File m_spreadsheetTestFile;

    @BeforeClass
    public static void startServer() throws InterruptedException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CoreWrapper.initialize(appCtx);

        // Create server cache directory
        File serverCacheDir = new File(appCtx.getCacheDir(), "core/server");
        if (!serverCacheDir.isDirectory()) {
            serverCacheDir.mkdirs();
        }
        CoreWrapper.createServer(serverCacheDir.getAbsolutePath());

        // Start server in background thread
        serverThread = new Thread(() -> {
            try {
                CoreWrapper.listenServer(29665);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Give server time to start
        Thread.sleep(1000);
    }

    @AfterClass
    public static void stopServer() {
        CoreWrapper.stopServer();
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Before
    public void initializeCore() {
        // Server is already initialized in @BeforeClass
    }

    @Before
    public void extractTestFile() throws IOException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        m_testFile = new File(appCtx.getCacheDir(), "test.odt");
        m_passwordTestFile = new File(appCtx.getCacheDir(), "password-test.odt");

        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        AssetManager assetManager = testCtx.getAssets();
        try (InputStream inputStream = assetManager.open("test.odt")) {
            copy(inputStream, m_testFile);
        }
        try (InputStream inputStream = assetManager.open("password-test.odt")) {
            copy(inputStream, m_passwordTestFile);
        }
        m_spreadsheetTestFile = new File(appCtx.getCacheDir(), "spreadsheet-test.ods");
        try (InputStream inputStream = assetManager.open("spreadsheet-test.ods")) {
            copy(inputStream, m_spreadsheetTestFile);
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
        if (null != m_spreadsheetTestFile) {
            m_spreadsheetTestFile.delete();
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

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("test", coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);

        File resultFile = new File(cacheDir, "result");
        coreOptions.outputPath = resultFile.getPath();

        String htmlDiff = "{\"modifiedText\":{\"/child:1/child:0\":\"This is a simple testoooo document to demonstrate the DocumentLoader example!\",\"/child:3/child:0\":\"This is a simple testaaaa document to demonstrate the DocumentLoader example!\"}}";

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

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("password-test-no-pw", coreOptions);
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

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("password-test-wrong-pw", coreOptions);
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

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("password-test-correct-pw", coreOptions);
        Assert.assertEquals(0, coreResult.errorCode);
    }

    @Test
    public void testSpreadsheetSheetNames() {
        File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File outputPath = new File(cacheDir, "spreadsheet_output");
        File cachePath = new File(cacheDir, "spreadsheet_cache");

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = m_spreadsheetTestFile.getAbsolutePath();
        coreOptions.outputPath = outputPath.getPath();
        coreOptions.editable = false;
        coreOptions.cachePath = cachePath.getPath();

        CoreWrapper.CoreResult coreResult = CoreWrapper.hostFile("spreadsheet-test", coreOptions);
        Assert.assertEquals("CoreWrapper should successfully parse the ODS file", 0, coreResult.errorCode);

        // Verify we have exactly 3 sheets
        Assert.assertEquals("ODS file should contain 3 sheets", 3, coreResult.pageNames.size());

        // Verify sheet names match the actual sheet names from the ODS file
        Assert.assertEquals("First sheet should be named 'hey'", "hey", coreResult.pageNames.get(0));
        Assert.assertEquals("Second sheet should be named 'ho'", "ho", coreResult.pageNames.get(1));
        Assert.assertEquals("Third sheet should be named 'Sheet3'", "Sheet3", coreResult.pageNames.get(2));
    }
}
