package app.opendocument.droid.test;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import app.opendocument.core.OdrException;
import app.opendocument.droid.background.CoreLoader;
import app.opendocument.droid.background.FileLoader;
import app.opendocument.droid.nonfree.AnalyticsManager;
import app.opendocument.droid.nonfree.CrashManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class CoreTest {
    private static CoreLoader s_coreLoader;

    private File m_testFile;
    private File m_passwordTestFile;
    private File m_spreadsheetTestFile;
    private File m_docxTestFile;

    @BeforeClass
    public static void startServer() throws InterruptedException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // the loader owns the core lifecycle now, so the test drives a real one rather than a
        // set of statics. nothing here goes through loadAsync, so both handlers can be the main
        // looper and the listener is never called back
        Handler handler = new Handler(Looper.getMainLooper());
        s_coreLoader = new CoreLoader(appCtx, true);
        s_coreLoader.initialize(
                new FileLoader.FileLoaderListener() {
                    @Override
                    public void onSuccess(FileLoader.Result result) {}

                    @Override
                    public void onError(FileLoader.Result result, Throwable throwable) {}
                },
                handler,
                handler,
                new AnalyticsManager(),
                new CrashManager());

        // Give server time to start
        Thread.sleep(1000);
    }

    @AfterClass
    public static void stopServer() {
        if (s_coreLoader != null) {
            s_coreLoader.close();
            s_coreLoader = null;
        }
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
        m_docxTestFile = new File(appCtx.getCacheDir(), "style-various-1.docx");
        try (InputStream inputStream = assetManager.open("style-various-1.docx")) {
            copy(inputStream, m_docxTestFile);
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
        if (null != m_docxTestFile) {
            m_docxTestFile.delete();
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

    private static File cacheDir() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
    }

    @Test
    public void test() {
        File cachePath = new File(cacheDir(), "core_cache");

        List<CoreLoader.HostedView> views =
                s_coreLoader.host(
                        "test",
                        m_testFile.getAbsolutePath(),
                        cachePath.getPath(),
                        null,
                        true,
                        false,
                        true);
        Assert.assertFalse("hosting the ODT file should produce a view", views.isEmpty());

        String htmlDiff =
                "{\"modifiedText\":{\"/child:1/child:0\":\"This is a simple testoooo document to"
                    + " demonstrate the DocumentLoader example!\",\"/child:3/child:0\":\"This is a"
                    + " simple testaaaa document to demonstrate the DocumentLoader example!\"}}";

        File result = s_coreLoader.edit(htmlDiff, new File(cacheDir(), "result").getPath());
        Assert.assertTrue("the edited document should have been saved", result.isFile());
    }

    @Test
    public void testDocxEdit() {
        File cachePath = new File(cacheDir(), "core_cache");

        List<CoreLoader.HostedView> views =
                s_coreLoader.host(
                        "docx-edit",
                        m_docxTestFile.getAbsolutePath(),
                        cachePath.getPath(),
                        null,
                        true,
                        false,
                        true);
        Assert.assertFalse("hosting the DOCX file should produce a view", views.isEmpty());

        String htmlDiff =
                "{\"modifiedText\":{\"/child:16/child:0/child:0\":\"Outasdfsdafdline\",\"/child:24/child:0/child:0\":\"Colorasdfasdfasdfed"
                    + " Line\",\"/child:6/child:0/child:0\":\"Text hello world!\"}}";

        File result = s_coreLoader.edit(htmlDiff, new File(cacheDir(), "result_docx").getPath());
        Assert.assertTrue("the edited document should have been saved", result.isFile());
    }

    @Test
    public void testPasswordProtectedDocumentWithoutPassword() {
        File cachePath = new File(cacheDir(), "core_cache");

        Assert.assertThrows(
                OdrException.FileEncrypted.class,
                () ->
                        s_coreLoader.host(
                                "password-test-no-pw",
                                m_passwordTestFile.getAbsolutePath(),
                                cachePath.getPath(),
                                null,
                                false,
                                false,
                                false));
    }

    @Test
    public void testPasswordProtectedDocumentWithWrongPassword() {
        File cachePath = new File(cacheDir(), "core_cache");

        Assert.assertThrows(
                OdrException.class,
                () ->
                        s_coreLoader.host(
                                "password-test-wrong-pw",
                                m_passwordTestFile.getAbsolutePath(),
                                cachePath.getPath(),
                                "wrongpassword",
                                false,
                                false,
                                false));
    }

    @Test
    public void testPasswordProtectedDocumentWithCorrectPassword() {
        File cachePath = new File(cacheDir(), "core_cache");

        List<CoreLoader.HostedView> views =
                s_coreLoader.host(
                        "password-test-correct-pw",
                        m_passwordTestFile.getAbsolutePath(),
                        cachePath.getPath(),
                        "passwort",
                        false,
                        false,
                        false);
        Assert.assertFalse("the decrypted document should produce a view", views.isEmpty());
    }

    @Test
    public void testSpreadsheetSheetNames() {
        File cachePath = new File(cacheDir(), "spreadsheet_cache");

        List<CoreLoader.HostedView> views =
                s_coreLoader.host(
                        "spreadsheet-test",
                        m_spreadsheetTestFile.getAbsolutePath(),
                        cachePath.getPath(),
                        null,
                        false,
                        false,
                        false);

        // Verify we have exactly 3 sheets
        Assert.assertEquals("ODS file should contain 3 sheets", 3, views.size());

        // Verify sheet names match the actual sheet names from the ODS file
        Assert.assertEquals("First sheet should be named 'hey'", "hey", views.get(0).getName());
        Assert.assertEquals("Second sheet should be named 'ho'", "ho", views.get(1).getName());
        Assert.assertEquals(
                "Third sheet should be named 'Sheet3'", "Sheet3", views.get(2).getName());
    }
}
