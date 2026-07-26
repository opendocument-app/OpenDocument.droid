// ActivityTestRule instead of ActivityScenario, because ActivityScenario does not actually work.
// Issue ID may or may not be added later.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.opendocument.droid.R
import app.opendocument.droid.ui.EditActionModeCallback
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import app.opendocument.droid.ui.widget.PageView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTests {

    private var idlingResource: IdlingResource? = null

    // Launch activity manually to ensure complete restart between tests
    @get:Rule
    val mainActivityActivityTestRule = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun setUp() {
        // Launch a fresh activity for each test
        val mainActivity = mainActivityActivityTestRule.launchActivity(null)

        val idlingResource = OpenFileIdling.idlingResource
        this.idlingResource = idlingResource
        IdlingRegistry.getInstance().register(idlingResource)

        // Close system dialogs which may cover our Activity.
        // Happens frequently on slow emulators.
        mainActivity.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        Intents.init()

        // Log test setup for debugging
        Log.d(TAG, "setUp() called for test: " + javaClass.name)
    }

    @After
    fun tearDown() {
        Log.d(TAG, "tearDown() called")

        Intents.release()

        idlingResource?.let { IdlingRegistry.getInstance().unregister(it) }

        // a test that recreated the activity left one behind that the rule does not know
        // about, and it would still be around when the next test launches its own
        val resumed = resumedMainActivity()
        if (resumed != null && resumed !== mainActivityActivityTestRule.activity) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { resumed.finish() }
        }

        // Finish and wait for activity to be destroyed
        if (mainActivityActivityTestRule.activity != null) {
            mainActivityActivityTestRule.finishActivity()

            // Use Instrumentation to wait until activity is destroyed
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun testODT() {
        respondToOpenDocumentWith(requireTestFile("test.odt"))

        openDocumentThroughPicker()

        // next onView will be blocked until the idling resource is idle.
        clickEditWithOverflowFallback()
    }

    @Test
    fun testPDF() {
        respondToOpenDocumentWith(requireTestFile("dummy.pdf"))

        openDocumentThroughPicker()

        // next onView will be blocked until the idling resource is idle.
        clickEditWithOverflowFallback()

        Thread.sleep(10000)
    }

    @Test
    fun testPasswordProtectedODT() {
        val testFile = requireTestFile("password-test.odt")

        // Check if the file exists and is readable
        Assert.assertTrue(
            "Password test file does not exist: " + testFile.absolutePath,
            testFile.exists(),
        )
        Assert.assertTrue(
            "Password test file is not readable: " + testFile.absolutePath,
            testFile.canRead(),
        )

        // Log file info for debugging CI issues
        Log.d(TAG, "Password test file path: " + testFile.absolutePath)
        Log.d(TAG, "Password test file size: " + testFile.length())
        Log.d(TAG, "All test files: " + testFiles.keys)

        // Double-check we're using the right file
        Assert.assertEquals("password-test.odt file size mismatch", 12671L, testFile.length())

        respondToOpenDocumentWith(testFile)

        openDocumentThroughPicker()

        // Wait for the password dialog to appear
        onView(withText("This document is password-protected")).check(matches(isDisplayed()))

        // Enter wrong password first
        onView(withClassName(equalTo("android.widget.EditText"))).perform(typeText("wrongpassword"))

        onView(withId(android.R.id.button1)).perform(click())

        // Should show password dialog again for wrong password
        onView(withText("This document is password-protected")).check(matches(isDisplayed()))

        // Clear the text field and enter correct password
        onView(withClassName(equalTo("android.widget.EditText")))
            .perform(clearText(), typeText("passwort"))

        onView(withId(android.R.id.button1)).perform(click())

        // Check if edit button becomes available (indicating successful load)
        clickEditWithOverflowFallback()
    }

    @Test
    fun testODTEditMode() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("test.odt"))
        enterEditMode(activity, documentFragment)

        val pageView = documentFragment.pageView
        Assert.assertNotNull(pageView)
        Assert.assertTrue(
            "ODT should become editable after entering edit mode",
            waitForEditableState(pageView!!, true, EDIT_MODE_TIMEOUT_MS),
        )
    }

    @Test
    fun testDOCXEditMode() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("style-various-1.docx"))
        enterEditMode(activity, documentFragment)

        val pageView = documentFragment.pageView
        Assert.assertNotNull(pageView)

        Assert.assertTrue(
            "DOCX should become editable after entering edit mode",
            waitForEditableState(pageView!!, true, EDIT_MODE_TIMEOUT_MS),
        )
    }

    @Test
    fun testDocumentSurvivesRecreation() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("test.odt"))
        val before = documentFragment.lastResult
        Assert.assertNotNull(before)

        // the document state used to be kept alive by setRetainInstance(true) and now
        // lives in a ViewModel, so a configuration change must not drop it or reload.
        // rotating is not enough to check that: MainActivity handles orientation and
        // screenSize itself, so it is only reconfigured, never torn down. recreate() is
        // what a locale or font scale change does, and that is the path the ViewModel and
        // the saved instance state have to survive.
        val recreated = recreate(activity)
        Assert.assertNotNull("activity gone after recreation", recreated)
        Assert.assertNotSame("activity was not recreated", activity, recreated)

        val afterRecreation = waitForDocumentFragment(recreated!!, 10000)
        Assert.assertNotNull("fragment gone after recreation", afterRecreation)
        Assert.assertNotSame("fragment was not recreated", documentFragment, afterRecreation)
        Assert.assertTrue(
            "document state lost across recreation",
            waitForLastResult(afterRecreation!!, 10000),
        )
        Assert.assertEquals(
            "document was reloaded instead of restored",
            before!!.options.originalUri,
            afterRecreation.lastResult?.options?.originalUri,
        )
    }

    private fun respondToOpenDocumentWith(testFile: File) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent()
                        .setData(uriOf(testFile))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            )
    }

    private fun openDocumentThroughPicker() {
        onView(
                allOf(
                    withId(R.id.menu_open),
                    withContentDescription("Open document"),
                    isDisplayed(),
                )
            )
            .perform(click())

        // The menu item could be either Documents or Files.
        onView(
                allOf(
                    withId(android.R.id.text1),
                    anyOf(withText("Documents"), withText("Files")),
                    isDisplayed(),
                )
            )
            .perform(click())
    }

    private fun clickEditWithOverflowFallback() {
        onView(allOf(withId(R.id.menu_edit), withContentDescription("Edit document"), isEnabled()))
            .withFailureHandler { _, _ ->
                // fails on small screens, try again with overflow menu
                onView(allOf(withContentDescription("More options"), isDisplayed()))
                    .perform(click())

                onView(
                        allOf(
                            withId(R.id.menu_edit),
                            withContentDescription("Edit document"),
                            isDisplayed(),
                        )
                    )
                    .perform(click())
            }
    }

    private fun recreate(activity: MainActivity): MainActivity? {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }

        val startMs = SystemClock.elapsedRealtime()
        do {
            val current = resumedMainActivity()
            if (current != null && current !== activity) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                return current
            }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() - startMs < 10000)

        return null
    }

    private fun resumedMainActivity(): MainActivity? {
        val current = AtomicReference<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (candidate in
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (candidate is MainActivity) {
                    current.set(candidate)
                }
            }
        }
        return current.get()
    }

    private fun loadDocument(activity: MainActivity, testFile: File): DocumentFragment {
        val testFileUri = uriOf(testFile)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.loadUri(testFileUri) }

        val fragment = waitForDocumentFragment(activity, 10000)
        Assert.assertNotNull(fragment)
        Assert.assertTrue(
            "Timed out waiting for document to load",
            waitForLastResult(fragment!!, 10000),
        )
        return fragment
    }

    private fun waitForDocumentFragment(
        activity: MainActivity,
        timeoutMs: Long,
    ): DocumentFragment? {
        val startMs = SystemClock.elapsedRealtime()
        do {
            val fragment =
                activity.supportFragmentManager.findFragmentByTag("document_fragment")
                    as DocumentFragment?
            if (fragment != null) {
                return fragment
            }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() - startMs < timeoutMs)
        return null
    }

    private fun waitForLastResult(fragment: DocumentFragment, timeoutMs: Long): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            if (fragment.hasLastResult()) {
                return true
            }
            SystemClock.sleep(100)
        }
        return false
    }

    private fun enterEditMode(activity: MainActivity, documentFragment: DocumentFragment) {
        val started = AtomicReference(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            started.set(
                activity.startSupportActionMode(
                    EditActionModeCallback(activity, documentFragment)
                ) != null
            )
        }
        Assert.assertTrue("Failed to enter edit mode", started.get())
    }

    private fun waitForEditableState(
        pageView: PageView,
        expected: Boolean,
        timeoutMs: Long,
    ): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
            if (expected == isEditableDom(pageView)) {
                return true
            }
            SystemClock.sleep(250)
        }
        return false
    }

    private fun isEditableDom(pageView: PageView): Boolean {
        val result =
            evaluateJavascript(
                pageView,
                "(function(){var bodyEditable = document.body &&" +
                    " document.body.isContentEditable;var editableNode =" +
                    " document.querySelector('[contenteditable=\"true\"]," +
                    " [contenteditable=\"plaintext-only\"]');return !!(bodyEditable ||" +
                    " editableNode);})()",
            ) ?: return false

        return "true".equals(result.replace("\"", ""), ignoreCase = true)
    }

    private fun evaluateJavascript(pageView: PageView, script: String): String? {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pageView.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) {
            Assert.fail("Timed out waiting for JS evaluation result")
        }
        return result.get()
    }

    private fun requireTestFile(name: String): File =
        checkNotNull(testFiles[name]) { "test file was not extracted: $name" }

    private fun uriOf(file: File): Uri {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

        return FileProvider.getUriForFile(appCtx, appCtx.packageName + ".provider", file)
    }

    companion object {
        private const val TAG = "MainActivityTests"

        // entering edit mode has to round-trip through the webview before the dom reports
        // contenteditable, and on a cold CI emulator that regularly took longer than the 10s
        // this used to wait for - the edit mode tests were the flakiest thing in the suite.
        private const val EDIT_MODE_TIMEOUT_MS = 30000L

        // insertion ordered, so the "All test files" log below reads in extraction order
        private val testFiles = LinkedHashMap<String, File>()

        // @JvmStatic because junit requires @BeforeClass / @AfterClass to be static
        @JvmStatic
        @BeforeClass
        fun extractTestFiles() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()

            val testDocumentsDir = File(instrumentation.targetContext.cacheDir, "test-documents")

            testDocumentsDir.mkdirs()
            Assert.assertTrue(testDocumentsDir.exists())

            val testAssetManager = instrumentation.context.assets

            for (filename in
                arrayOf("test.odt", "dummy.pdf", "password-test.odt", "style-various-1.docx")) {
                val targetFile = File(testDocumentsDir, filename)
                copy(testAssetManager.open(filename), targetFile)
                testFiles[filename] = targetFile
            }
        }

        @JvmStatic
        @AfterClass
        fun cleanupTestFiles() {
            for (file in testFiles.values) {
                file.delete()
            }
        }

        private fun copy(source: InputStream, target: File) {
            source.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        }
    }
}
