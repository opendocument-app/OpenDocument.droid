package at.tomtasche.reader.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.intent.Intents;
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
import java.util.Map;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;

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

        Intents.init();
        
        // Log test setup for debugging
        Log.d("MainActivityTests", "setUp() called for test: " + getClass().getName());
    }

    @After
    public void tearDown() {
        Log.d("MainActivityTests", "tearDown() called");
        
        Intents.release();

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

        for (String filename: new String[] {"test.odt", "dummy.pdf", "password-test.odt"}) {
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
    public void testODT() {
        File testFile = s_testFiles.get("test.odt");
        Assert.assertNotNull(testFile);
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", testFile);
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(testFileUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        onView(allOf(withId(R.id.menu_open), withContentDescription("Open document"), isDisplayed()))
                .perform(click());

        // The menu item could be either Documents or Files.
        onView(allOf(withId(android.R.id.text1), anyOf(withText("Documents"), withText("Files")), isDisplayed()))
                .perform(click());

        // next onView will be blocked until m_idlingResource is idle.
        onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isEnabled()))
                .withFailureHandler((error, viewMatcher) -> {
                    // fails on small screens, try again with overflow menu
                    onView(allOf(withContentDescription("More options"), isDisplayed())).perform(click());

                    onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isDisplayed()))
                            .perform(click());
                });
    }

    @Test
    public void testPDF() {
        File testFile = s_testFiles.get("dummy.pdf");
        Assert.assertNotNull(testFile);
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", testFile);
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(testFileUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        onView(allOf(withId(R.id.menu_open), withContentDescription("Open document"), isDisplayed()))
            .perform(click());

        // The menu item could be either Documents or Files.
        onView(allOf(withId(android.R.id.text1), anyOf(withText("Documents"), withText("Files")), isDisplayed()))
                .perform(click());

        // next onView will be blocked until m_idlingResource is idle.

        onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isEnabled()))
            .withFailureHandler((error, viewMatcher) -> {
                // fails on small screens, try again with overflow menu
                onView(allOf(withContentDescription("More options"), isDisplayed())).perform(click());

                onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isDisplayed()))
                        .perform(click());
            });

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPasswordProtectedODT() {
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

        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", testFile);
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(testFileUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        onView(allOf(withId(R.id.menu_open), withContentDescription("Open document"), isDisplayed()))
                .perform(click());

        onView(allOf(withId(android.R.id.text1), anyOf(withText("Documents"), withText("Files")), isDisplayed()))
                .perform(click());

        // Wait for the password dialog to appear
        onView(withText("This document is password-protected"))
                .check(matches(isDisplayed()));

        // Enter wrong password first
        onView(withClassName(equalTo("android.widget.EditText")))
                .perform(typeText("wrongpassword"));

        onView(withId(android.R.id.button1))
                .perform(click());

        // Should show password dialog again for wrong password
        onView(withText("This document is password-protected"))
                .check(matches(isDisplayed()));

        // Clear the text field and enter correct password
        onView(withClassName(equalTo("android.widget.EditText")))
                .perform(clearText(), typeText("passwort"));

        onView(withId(android.R.id.button1))
                .perform(click());

        // Check if edit button becomes available (indicating successful load)
        onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isEnabled()))
                .withFailureHandler((error, viewMatcher) -> {
                    onView(allOf(withContentDescription("More options"), isDisplayed())).perform(click());

                    onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isDisplayed()))
                            .perform(click());
                });
    }
}
