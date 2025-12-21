package at.tomtasche.reader.test;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import at.tomtasche.reader.background.FileLoader;
import at.tomtasche.reader.ui.EditActionModeCallback;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.widget.PageView;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class MainActivityTests {
    private IdlingResource m_idlingResource;
    private static final Map<String, File> s_testFiles = new ArrayMap<>();

    // Yes, this is ActivityTestRule instead of ActivityScenario, because ActivityScenario does not actually work.
    // Issue ID may or may not be added later.
    // Launch activity manually to ensure complete restart between tests
    @Rule
    public ActivityTestRule<MainActivity> mainActivityActivityTestRule = new ActivityTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() {
        // Launch a fresh activity for each test
        MainActivity mainActivity = mainActivityActivityTestRule.launchActivity(null);

        m_idlingResource = mainActivity.getOpenFileIdlingResource();
        IdlingRegistry.getInstance().register(m_idlingResource);

        // Close system dialogs which may cover our Activity.
        // Happens frequently on slow emulators.
        mainActivity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        
        // Log test setup for debugging
        Log.d("MainActivityTests", "setUp() called for test: " + getClass().getName());
    }

    @After
    public void tearDown() {
        Log.d("MainActivityTests", "tearDown() called");

        if (null != m_idlingResource) {
            IdlingRegistry.getInstance().unregister(m_idlingResource);
        }
        
        // Finish and wait for activity to be destroyed
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        if (activity != null) {
            mainActivityActivityTestRule.finishActivity();
            
            // Use Instrumentation to wait until activity is destroyed
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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

    @BeforeClass
    public static void extractTestFiles() throws IOException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();

        File appCacheDir = appContext.getCacheDir();
        File testDocumentsDir = new File(appCacheDir, "test-documents");

        testDocumentsDir.mkdirs();
        Assert.assertTrue(testDocumentsDir.exists());

        AssetManager testAssetManager = instrumentation.getContext().getAssets();

        for (String filename: new String[] {"test.odt", "dummy.pdf", "password-test.odt", "style-various-1.docx"}) {
            File targetFile = new File(testDocumentsDir, filename);
            try (InputStream inputStream = testAssetManager.open(filename)) {
                copy(inputStream, targetFile);
            }
            s_testFiles.put(filename, targetFile);
        }
    }

    @AfterClass
    public static void cleanupTestFiles() {
        for (File file: s_testFiles.values()) {
            file.delete();
        }
    }

    @Test
    public void testODT() throws InterruptedException {
        File testFile = s_testFiles.get("test.odt");
        Assert.assertNotNull(testFile);
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        DocumentFragment documentFragment = loadDocument(activity, testFile);

        PageView pageView = documentFragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("ODT should load", waitForPageLoaded(pageView, 10000));

        String fileType = documentFragment.getLastFileType();
        Assert.assertNotNull(fileType);
        Assert.assertTrue("Expected ODT file type", fileType.startsWith("application/vnd.oasis.opendocument"));
    }

    @Test
    public void testPDF() throws InterruptedException {
        File testFile = s_testFiles.get("dummy.pdf");
        Assert.assertNotNull(testFile);
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        DocumentFragment documentFragment = loadDocument(activity, testFile);

        PageView pageView = documentFragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("PDF should load", waitForPageLoaded(pageView, 10000));

        String fileType = documentFragment.getLastFileType();
        Assert.assertNotNull(fileType);
        Assert.assertTrue("Expected PDF file type", fileType.startsWith("application/pdf"));
    }

    @Test
    public void testPasswordProtectedODT() throws InterruptedException {
        File testFile = s_testFiles.get("password-test.odt");
        Assert.assertNotNull(testFile);

        // Check if the file exists and is readable
        Assert.assertTrue("Password test file does not exist: " + testFile.getAbsolutePath(), testFile.exists());
        Assert.assertTrue("Password test file is not readable: " + testFile.getAbsolutePath(), testFile.canRead());

        // Log file info for debugging CI issues
        Log.d("MainActivityTests", "Password test file path: " + testFile.getAbsolutePath());
        Log.d("MainActivityTests", "Password test file size: " + testFile.length());
        Log.d("MainActivityTests", "All test files: " + s_testFiles.keySet());

        // Double-check we're using the right file
        Assert.assertEquals("password-test.odt file size mismatch", 12671L, testFile.length());

        MainActivity activity = mainActivityActivityTestRule.getActivity();
        DocumentFragment documentFragment = loadDocument(activity, testFile);

        setPasswordAndReload(documentFragment, "passwort");

        PageView pageView = documentFragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("Password-protected ODT should load with correct password",
                waitForPageLoaded(pageView, 10000));
    }

    @Test
    public void testODTEditMode() throws InterruptedException {
        File testFile = s_testFiles.get("test.odt");
        Assert.assertNotNull(testFile);
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        DocumentFragment documentFragment = loadDocument(activity, testFile);
        enterEditMode(activity, documentFragment);

        PageView pageView = documentFragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue(
                "ODT should become editable after entering edit mode",
                waitForEditableState(pageView, true, 10000)
        );
    }

    @Test
    public void testDOCXEditMode() throws InterruptedException {
        File testFile = s_testFiles.get("style-various-1.docx");
        Assert.assertNotNull(testFile);
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        DocumentFragment documentFragment = loadDocument(activity, testFile);
        enterEditMode(activity, documentFragment);

        PageView pageView = documentFragment.getPageView();
        Assert.assertNotNull(pageView);

        Assert.assertTrue(
                "DOCX should become editable after entering edit mode",
                waitForEditableState(pageView, true, 10000)
        );
    }

    private DocumentFragment loadDocument(MainActivity activity, File testFile) throws InterruptedException {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", testFile);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.loadUri(testFileUri));

        DocumentFragment fragment = waitForDocumentFragment(activity, 10000);
        Assert.assertNotNull(fragment);
        Assert.assertTrue("Timed out waiting for document to load", waitForLastResult(fragment, 10000));
        return fragment;
    }

    private DocumentFragment waitForDocumentFragment(MainActivity activity, long timeoutMs)
            throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        DocumentFragment fragment;
        do {
            fragment = (DocumentFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("document_fragment");
            if (fragment != null) {
                return fragment;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() - startMs < timeoutMs);
        return null;
    }

    private boolean waitForLastResult(DocumentFragment fragment, long timeoutMs) throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            if (fragment.hasLastResult()) {
                return true;
            }
            SystemClock.sleep(100);
        }
        return false;
    }

    private void setPasswordAndReload(DocumentFragment documentFragment, String password) {
        FileLoader.Result result = getLastResult(documentFragment);
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.options);
        result.options.password = password;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> documentFragment.reloadUri(false));
    }

    private FileLoader.Result getLastResult(DocumentFragment documentFragment) {
        try {
            Field field = DocumentFragment.class.getDeclaredField("lastResult");
            field.setAccessible(true);
            return (FileLoader.Result) field.get(documentFragment);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Assert.fail("Failed to access lastResult: " + e.getMessage());
            return null;
        }
    }

    private void enterEditMode(MainActivity activity, DocumentFragment documentFragment) {
        AtomicReference<Boolean> started = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            started.set(activity.startSupportActionMode(
                    new EditActionModeCallback(activity, documentFragment)) != null);
        });
        Assert.assertTrue("Failed to enter edit mode", started.get());
    }

    private boolean waitForPageLoaded(PageView pageView, long timeoutMs) throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            String url = getPageViewUrl(pageView);
            if (url != null && !url.isEmpty() && !"about:blank".equals(url)) {
                return true;
            }
            SystemClock.sleep(250);
        }
        return false;
    }

    private String getPageViewUrl(PageView pageView) throws InterruptedException {
        AtomicReference<String> url = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            url.set(pageView.getUrl());
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            Assert.fail("Timed out waiting for WebView URL");
        }
        return url.get();
    }

    private boolean waitForEditableState(PageView pageView, boolean expected, long timeoutMs)
            throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            if (expected == isEditableDom(pageView)) {
                return true;
            }
            SystemClock.sleep(250);
        }
        return false;
    }

    private boolean isEditableDom(PageView pageView) throws InterruptedException {
        String result = evaluateJavascript(pageView,
                "(function(){"
                        + "var bodyEditable = document.body && document.body.isContentEditable;"
                        + "var editableNode = document.querySelector('[contenteditable=\"true\"], [contenteditable=\"plaintext-only\"]');"
                        + "return !!(bodyEditable || editableNode);"
                        + "})()");
        if (result == null) {
            return false;
        }
        String normalized = result.replace("\"", "");
        return "true".equalsIgnoreCase(normalized);
    }

    private String evaluateJavascript(PageView pageView, String script) throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            pageView.evaluateJavascript(script, value -> {
                result.set(value);
                latch.countDown();
            });
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            Assert.fail("Timed out waiting for JS evaluation result");
        }
        return result.get();
    }
}
