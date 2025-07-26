package at.tomtasche.reader.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
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

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;

/**
 * Isolated test for password-protected documents to debug CI failures
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PasswordTestIsolated {
    private IdlingResource m_idlingResource;
    private static File s_passwordTestFile;

    @Rule
    public ActivityTestRule<MainActivity> mainActivityActivityTestRule = new ActivityTestRule<>(MainActivity.class);

    @BeforeClass
    public static void extractPasswordTestFile() throws IOException {
        Log.d("PasswordTestIsolated", "=== BeforeClass: Extracting password test file ===");
        
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();

        File appCacheDir = appContext.getCacheDir();
        File testDocumentsDir = new File(appCacheDir, "test-documents-isolated");
        
        testDocumentsDir.mkdirs();
        Assert.assertTrue("Failed to create test directory", testDocumentsDir.exists());

        AssetManager testAssetManager = instrumentation.getContext().getAssets();
        
        s_passwordTestFile = new File(testDocumentsDir, "password-test.odt");
        
        try (InputStream inputStream = testAssetManager.open("password-test.odt");
             OutputStream out = new FileOutputStream(s_passwordTestFile)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        
        Log.d("PasswordTestIsolated", "Password test file created at: " + s_passwordTestFile.getAbsolutePath());
        Log.d("PasswordTestIsolated", "Password test file size: " + s_passwordTestFile.length());
        Assert.assertEquals("File size mismatch", 12671L, s_passwordTestFile.length());
    }

    @Before
    public void setUp() {
        Log.d("PasswordTestIsolated", "=== setUp: Initializing test ===");
        
        MainActivity mainActivity = mainActivityActivityTestRule.getActivity();
        Assert.assertNotNull("MainActivity is null in setUp", mainActivity);

        m_idlingResource = mainActivity.getOpenFileIdlingResource();
        IdlingRegistry.getInstance().register(m_idlingResource);

        // Close system dialogs which may cover our Activity
        mainActivity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        Intents.init();
        
        // Give the activity time to fully initialize
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Log.e("PasswordTestIsolated", "Sleep interrupted", e);
        }
    }

    @After
    public void tearDown() {
        Log.d("PasswordTestIsolated", "=== tearDown: Cleaning up ===");
        
        Intents.release();

        if (null != m_idlingResource) {
            IdlingRegistry.getInstance().unregister(m_idlingResource);
        }
        
        mainActivityActivityTestRule.getActivity().finish();
    }

    @Test
    public void testPasswordProtectedDocument() {
        Log.d("PasswordTestIsolated", "=== Starting password test ===");
        
        Assert.assertNotNull("Password test file is null", s_passwordTestFile);
        Assert.assertTrue("Password test file doesn't exist", s_passwordTestFile.exists());
        
        Context appCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri testFileUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".provider", s_passwordTestFile);
        
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK,
                        new Intent()
                                .setData(testFileUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
        );

        // Verify activity state before interaction
        MainActivity activity = mainActivityActivityTestRule.getActivity();
        Assert.assertNotNull("Activity is null before test", activity);
        Assert.assertFalse("Activity is finishing before test", activity.isFinishing());
        Assert.assertFalse("Activity is destroyed before test", activity.isDestroyed());
        
        Log.d("PasswordTestIsolated", "Opening document menu");
        onView(allOf(withId(R.id.menu_open), withText("Open")))
            .check(matches(isDisplayed()))
            .perform(click());

        Log.d("PasswordTestIsolated", "Waiting for password dialog");
        
        // Wait with timeout for password dialog
        try {
            onView(withText("This document is password-protected"))
                    .check(matches(isDisplayed()));
            Log.d("PasswordTestIsolated", "Password dialog appeared");
        } catch (Exception e) {
            Log.e("PasswordTestIsolated", "Failed to find password dialog", e);
            // Check if activity crashed
            Assert.assertFalse("Activity was destroyed while waiting for dialog", 
                    mainActivityActivityTestRule.getActivity().isDestroyed());
            throw e;
        }

        // Enter correct password
        Log.d("PasswordTestIsolated", "Entering password");
        onView(withClassName(equalTo("android.widget.EditText")))
                .perform(clearText(), typeText("passwort"));

        onView(withId(android.R.id.button1))
                .perform(click());
        
        Log.d("PasswordTestIsolated", "Test completed successfully");
    }
}