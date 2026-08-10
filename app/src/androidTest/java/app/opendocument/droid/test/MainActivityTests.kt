// ActivityTestRule instead of ActivityScenario, because ActivityScenario does not actually work.
// Issue ID may or may not be added later.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
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
import app.opendocument.droid.ui.widget.DocumentActions
import app.opendocument.droid.ui.widget.PageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
        val mainActivity = mainActivityActivityTestRule.launchActivity(null)

        val idlingResource = OpenFileIdling.idlingResource
        this.idlingResource = idlingResource
        IdlingRegistry.getInstance().register(idlingResource)

        // Close system dialogs which may cover our Activity.
        // Happens frequently on slow emulators.
        mainActivity.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()

        idlingResource?.let { IdlingRegistry.getInstance().unregister(it) }

        // a test that recreated the activity left one behind that the rule does not know
        // about, and it would still be around when the next test launches its own
        val resumed = resumedMainActivity()
        if (resumed != null && resumed !== mainActivityActivityTestRule.activity) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { resumed.finish() }
        }

        if (mainActivityActivityTestRule.activity != null) {
            mainActivityActivityTestRule.finishActivity()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun testODT() {
        respondToOpenDocumentWith(requireTestFile("test.odt"))

        openDocumentThroughPicker()

        // next onView will be blocked until the idling resource is idle, which now covers
        // the load itself and not just the picker round trip.
        waitForDocumentActions()

        unfoldDocumentActions()

        onView(withText(R.string.menu_edit)).check(matches(isDisplayed()))
    }

    @Test
    fun testPDF() {
        respondToOpenDocumentWith(requireTestFile("dummy.pdf"))

        openDocumentThroughPicker()

        // next onView will be blocked until the idling resource is idle, which now covers
        // the load itself and not just the picker round trip. the buttons being up is what
        // says the pdf opened - Edit is not, because the core does not write pdf back
        waitForDocumentActions()

        unfoldDocumentActions()

        onView(withText(R.string.menu_edit)).check(doesNotExist())
    }

    @Test
    fun testPasswordProtectedODT() {
        val testFile = requireTestFile("password-test.odt")

        respondToOpenDocumentWith(testFile)

        openDocumentThroughPicker()

        // The dialog is put up by DocumentFragment.onEncrypted, and the idling resource
        // stays busy until it is on screen - so this does not have to poll for it.
        onView(withText("This document is password-protected")).check(matches(isDisplayed()))

        onView(withClassName(equalTo("android.widget.EditText"))).perform(typeText("wrongpassword"))

        // typing leaves the keyboard up, and on a short screen it covers the dialog's buttons
        onView(withId(android.R.id.button1)).perform(closeSoftKeyboard(), click())

        // Should show password dialog again for wrong password
        onView(withText("This document is password-protected")).check(matches(isDisplayed()))

        onView(withClassName(equalTo("android.widget.EditText")))
            .perform(clearText(), typeText("passwort"))

        onView(withId(android.R.id.button1)).perform(closeSoftKeyboard(), click())

        // Check if the document buttons become available (indicating successful load)
        waitForDocumentActions()
    }

    @Test
    fun testCorruptODTOffersContact() {
        val activity = mainActivityActivityTestRule.activity

        // the core claims the format and fails on the file, which is final. not loadDocument(),
        // which waits for a fragment this path takes back down
        val testFileUri = uriOf(requireTestFile("corrupt.odt"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.loadUri(testFileUri) }

        onView(withText(R.string.dialog_broken_file)).check(matches(isDisplayed()))
        onView(withText(R.string.action_contact)).check(matches(isDisplayed()))
    }

    @Test
    fun testODTEditMode() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("test.odt"))
        enterEditMode(activity, documentFragment)

        val pageView = documentFragment.pageView
        Assert.assertNotNull(pageView)
        assertBecomesEditable("ODT", pageView!!, documentFragment)
    }

    @Test
    fun testDOCXEditMode() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("style-various-1.docx"))
        enterEditMode(activity, documentFragment)

        val pageView = documentFragment.pageView
        Assert.assertNotNull(pageView)

        assertBecomesEditable("DOCX", pageView!!, documentFragment)
    }

    /** A full save needs no diff from the page, and must not ask for one and then save twice. */
    @Test
    fun testSaveOpensOneCreateDocumentPicker() {
        val activity = mainActivityActivityTestRule.activity
        loadDocument(activity, requireTestFile("test.odt"))

        // cancelled: how many pickers were opened is the whole question, not what came back
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.onDocumentAction(DocumentActions.ACTION_SAVE)
        }

        // a plain wait: this asserts something does not happen, after a web view round trip
        // that nothing idles on
        SystemClock.sleep(3000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        Intents.intended(hasAction(Intent.ACTION_CREATE_DOCUMENT), times(1))
    }

    @Test
    fun testDocumentSurvivesRecreation() {
        val activity = mainActivityActivityTestRule.activity
        val documentFragment = loadDocument(activity, requireTestFile("test.odt"))
        val before = documentFragment.lastResult
        Assert.assertNotNull(before)

        // not rotation, which MainActivity handles itself: recreate() is what a locale or font
        // scale change does, and that is the path the ViewModel and saved state must survive
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

    // the landing screen is the only thing offering to open a document now that the toolbar
    // action is gone. the fab is the entry point that is there whatever the list holds - the
    // empty state has one of its own, but only while it is up, so matching either would be
    // ambiguous rather than tolerant.
    //
    // closeSoftKeyboard first, and it is not decoration: the fab sits in the lower half of the
    // screen, where a keyboard left over from an earlier test covers it. the ime window is
    // above ours, so the tap lands on it, espresso reports the click as performed, and nothing
    // happens - see the note on waitForDocumentActions.
    private fun openDocumentThroughPicker() {
        onView(withId(R.id.landing_open_fab)).perform(closeSoftKeyboard(), click())
    }

    // the buttons of the open document are up once it has loaded, so this blocks on the idling
    // resource and then says whether anything came of the load. only the button that unfolds the
    // rest is checked: what is inside it differs per format, a pdf cannot be edited.
    //
    // a click that never reached the app fails here rather than where it happened: nothing was
    // ever queued, so the idling resource stays idle, this does not wait, and the landing screen
    // is still what is on screen.
    private fun waitForDocumentActions() {
        onView(withId(R.id.document_actions_more)).check(matches(isDisplayed()))
    }

    private fun unfoldDocumentActions() {
        onView(withId(R.id.document_actions_more)).perform(click())
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

    /**
     * Fails with what the page and the file on disk actually contained, since the interesting half
     * of an edit mode failure is which end of the round trip went missing.
     */
    private fun assertBecomesEditable(
        label: String,
        pageView: PageView,
        fragment: DocumentFragment,
    ) {
        if (waitForEditableState(pageView, true, EDIT_MODE_TIMEOUT_MS)) {
            return
        }

        Assert.fail(
            "$label should become editable after entering edit mode." +
                " dom=${describeDom(pageView)} document=${describeLoadedDocument(fragment)}"
        )
    }

    private fun describeDom(pageView: PageView): String =
        evaluateJavascript(
            pageView,
            "(function(){var b = document.body;var html = b ? b.innerHTML : '';" +
                "return 'url=' + document.location.href + ' ready=' + document.readyState +" +
                " ' bodyLength=' + html.length + ' bodyEditable=' + !!(b && b.isContentEditable) +" +
                " ' editableNodes=' + document.querySelectorAll('[contenteditable]').length +" +
                " ' odr=' + (typeof odr);})()",
        ) ?: "no result"

    /**
     * What the loader published, fetched over http the way the webview fetches it: a document the
     * test process cannot fetch either was never served, and one it can fetch was the webview's to
     * lose.
     */
    private fun describeLoadedDocument(fragment: DocumentFragment): String {
        val result = fragment.lastResult ?: return "no result"
        val url = result.partUris.firstOrNull() ?: return "no part uri"

        return try {
            val connection = URL(url.toString()).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            try {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                "$url http=${connection.responseCode} length=${html.length}" +
                    " contenteditable=${html.contains("contenteditable")}" +
                    " translatable=${result.options.translatable}"
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            "$url unreachable: $e"
        }
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
        // a cold CI emulator needs more than the 10s this used to wait. more than 30s does not
        // help: what still failed at 60s was the core server failing to bind its port
        private const val EDIT_MODE_TIMEOUT_MS = 30000L

        private val testFiles = mutableMapOf<String, File>()

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
                arrayOf(
                    "test.odt",
                    "dummy.pdf",
                    "password-test.odt",
                    "style-various-1.docx",
                    "corrupt.odt",
                )) {
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
