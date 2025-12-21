package at.tomtasche.reader.test;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.SystemClock;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.test.espresso.intent.Intents;

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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.util.ArrayMap;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.DocumentFragment;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.PageView;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class MainActivityUiTests {
    private static final long UI_TIMEOUT_MS = 10000;
    private static final Map<String, File> s_testFiles = new ArrayMap<>();
    private static final String EXPECTED_FIRST_WORD_ODT = "This";
    private static final String EXPECTED_FIRST_WORD_PDF = "Dummy";
    private static final String EXPECTED_FIRST_WORD_DOCX = "Table";
    private static final String EXPECTED_FIRST_WORD_PASSWORD_ODT = "Hallo";

    @Rule
    public ActivityTestRule<MainActivity> mainActivityActivityTestRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    private UiDevice device;

    @BeforeClass
    public static void extractTestFiles() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File appCacheDir = appContext.getCacheDir();
        File testDocumentsDir = new File(appCacheDir, "test-documents");

        testDocumentsDir.mkdirs();
        Assert.assertTrue(testDocumentsDir.exists());

        AssetManager testAssetManager = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        for (String filename : new String[] {"test.odt", "dummy.pdf", "password-test.odt", "style-various-1.docx"}) {
            File targetFile = new File(testDocumentsDir, filename);
            try (InputStream inputStream = testAssetManager.open(filename)) {
                copy(inputStream, targetFile);
            }
            s_testFiles.put(filename, targetFile);
        }
    }

    @AfterClass
    public static void cleanupTestFiles() {
        for (File file : s_testFiles.values()) {
            file.delete();
        }
    }

    @Before
    public void setUp() {
        MainActivity activity = mainActivityActivityTestRule.launchActivity(null);
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wait(Until.hasObject(By.pkg(activity.getPackageName()).depth(0)), UI_TIMEOUT_MS);
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();

        MainActivity activity = mainActivityActivityTestRule.getActivity();
        if (activity != null) {
            mainActivityActivityTestRule.finishActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test
    public void testODTUi() throws Exception {
        File testFile = s_testFiles.get("test.odt");
        Assert.assertNotNull(testFile);
        stubOpenDocumentIntent(testFile);

        openDocumentViaUi();

        DocumentFragment fragment = waitForDocumentFragment();
        PageView pageView = fragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("ODT should load", waitForPageLoaded(pageView, UI_TIMEOUT_MS));
        assertFirstWord(pageView, EXPECTED_FIRST_WORD_ODT, "ODT");
    }

    @Test
    public void testPDFUi() throws Exception {
        File testFile = s_testFiles.get("dummy.pdf");
        Assert.assertNotNull(testFile);
        stubOpenDocumentIntent(testFile);

        openDocumentViaUi();

        DocumentFragment fragment = waitForDocumentFragment();
        PageView pageView = fragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("PDF should load", waitForPageLoaded(pageView, UI_TIMEOUT_MS));
        assertFirstWord(pageView, EXPECTED_FIRST_WORD_PDF, "PDF");
    }

    @Test
    public void testPasswordProtectedODTUi() throws Exception {
        File testFile = s_testFiles.get("password-test.odt");
        Assert.assertNotNull(testFile);
        stubOpenDocumentIntent(testFile);

        openDocumentViaUi();

        waitForText("This document is password-protected");
        setPassword("wrongpassword");
        clickDialogOk();

        waitForText("This document is password-protected");
        setPassword("passwort");
        clickDialogOk();

        DocumentFragment fragment = waitForDocumentFragment();
        PageView pageView = fragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("Password-protected ODT should load", waitForPageLoaded(pageView, UI_TIMEOUT_MS));
        assertFirstWord(pageView, EXPECTED_FIRST_WORD_PASSWORD_ODT, "Password-protected ODT");
    }

    @Test
    public void testODTEditModeUi() throws Exception {
        File testFile = s_testFiles.get("test.odt");
        Assert.assertNotNull(testFile);
        stubOpenDocumentIntent(testFile);

        openDocumentViaUi();

        DocumentFragment fragment = waitForDocumentFragment();
        PageView pageView = fragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("ODT should load", waitForPageLoaded(pageView, UI_TIMEOUT_MS));
        assertFirstWord(pageView, EXPECTED_FIRST_WORD_ODT, "ODT");

        clickEditMenu();
        String bannerText = mainActivityActivityTestRule.getActivity().getString(R.string.action_edit_banner);
        waitForText(bannerText);

        Assert.assertTrue(
                "ODT should become editable after entering edit mode",
                waitForEditableState(pageView, true, UI_TIMEOUT_MS)
        );
    }

    @Test
    public void testDOCXEditModeUi() throws Exception {
        File testFile = s_testFiles.get("style-various-1.docx");
        Assert.assertNotNull(testFile);
        stubOpenDocumentIntent(testFile);

        openDocumentViaUi();

        DocumentFragment fragment = waitForDocumentFragment();
        PageView pageView = fragment.getPageView();
        Assert.assertNotNull(pageView);
        Assert.assertTrue("DOCX should load", waitForPageLoaded(pageView, UI_TIMEOUT_MS));
        assertFirstWord(pageView, EXPECTED_FIRST_WORD_DOCX, "DOCX");

        clickEditMenu();
        String bannerText = mainActivityActivityTestRule.getActivity().getString(R.string.action_edit_banner);
        waitForText(bannerText);

        Assert.assertTrue(
                "DOCX should become editable after entering edit mode",
                waitForEditableState(pageView, true, UI_TIMEOUT_MS)
        );
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

    private void stubOpenDocumentIntent(File testFile) {
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", testFile);
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                        new Intent()
                                .setData(testFileUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );
    }

    private void openDocumentViaUi() {
        clickOpenMenu();
        clickFileManagerOption();
    }

    private void clickOpenMenu() {
        UiObject2 open = device.wait(Until.findObject(By.desc("Open document")), UI_TIMEOUT_MS);
        if (open == null) {
            open = device.wait(Until.findObject(By.text("Open document")), UI_TIMEOUT_MS);
        }
        if (open == null) {
            clickOverflowMenu();
            open = device.wait(Until.findObject(By.text("Open document")), UI_TIMEOUT_MS);
        }
        Assert.assertNotNull("Open document menu not found", open);
        open.click();
        device.waitForIdle();
    }

    private void clickEditMenu() {
        UiObject2 edit = device.wait(Until.findObject(By.desc("Edit document")), UI_TIMEOUT_MS);
        if (edit == null) {
            edit = device.wait(Until.findObject(By.text("Edit document")), UI_TIMEOUT_MS);
        }
        if (edit == null) {
            clickOverflowMenu();
            edit = device.wait(Until.findObject(By.text("Edit document")), UI_TIMEOUT_MS);
        }
        Assert.assertNotNull("Edit document menu not found", edit);
        edit.click();
        device.waitForIdle();
    }

    private void clickOverflowMenu() {
        UiObject2 more = device.wait(Until.findObject(By.desc("More options")), UI_TIMEOUT_MS);
        Assert.assertNotNull("Overflow menu not found", more);
        more.click();
        device.waitForIdle();
    }

    private void clickFileManagerOption() {
        UiObject2 documents = device.wait(Until.findObject(By.text("Documents")), UI_TIMEOUT_MS);
        if (documents != null) {
            documents.click();
            device.waitForIdle();
            return;
        }
        UiObject2 files = device.wait(Until.findObject(By.text("Files")), UI_TIMEOUT_MS);
        if (files != null) {
            files.click();
            device.waitForIdle();
            return;
        }
        Assert.fail("No file manager option found");
    }

    private void waitForText(String text) {
        UiObject2 obj = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS);
        Assert.assertNotNull("Expected text not found: " + text, obj);
    }

    private void setPassword(String password) {
        UiObject2 input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS);
        Assert.assertNotNull("Password input not found", input);
        input.setText(password);
        device.waitForIdle();
    }

    private void clickDialogOk() {
        UiObject2 ok = device.wait(Until.findObject(By.res("android", "button1")), UI_TIMEOUT_MS);
        if (ok == null) {
            ok = device.wait(Until.findObject(By.text("OK")), UI_TIMEOUT_MS);
        }
        Assert.assertNotNull("Dialog OK button not found", ok);
        ok.click();
        device.waitForIdle();
    }

    private DocumentFragment waitForDocumentFragment() throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - startMs < UI_TIMEOUT_MS) {
            MainActivity activity = mainActivityActivityTestRule.getActivity();
            DocumentFragment fragment = (DocumentFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("document_fragment");
            if (fragment != null && fragment.hasLastResult()) {
                return fragment;
            }
            SystemClock.sleep(100);
        }
        Assert.fail("Timed out waiting for document fragment");
        return null;
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

    private void assertFirstWord(PageView pageView, String expected, String label) throws InterruptedException {
        String firstWord = waitForFirstWord(pageView, UI_TIMEOUT_MS);
        Assert.assertEquals(label + " first word mismatch", expected, firstWord);
    }

    private String waitForFirstWord(PageView pageView, long timeoutMs) throws InterruptedException {
        long startMs = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            String firstWord = getFirstWord(pageView);
            if (!firstWord.isEmpty()) {
                return firstWord;
            }
            SystemClock.sleep(250);
        }
        return "";
    }

    private String getFirstWord(PageView pageView) throws InterruptedException {
        String result = evaluateJavascript(pageView,
                "(function(){"
                        + "var text = document.body ? (document.body.innerText || '') : '';"
                        + "text = text.replace(/\\s+/g,' ').trim();"
                        + "if (!text) return '';"
                        + "return text.split(' ')[0];"
                        + "})()");
        if (result == null) {
            return "";
        }
        return result.replace("\"", "").trim();
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
