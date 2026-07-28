// ActivityTestRule instead of ActivityScenario, matching MainActivityTests - see the note there.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import app.opendocument.droid.R
import app.opendocument.droid.background.RecentDocumentsUtil
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The landing screen: the recently opened documents and the actions offered next to them. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LandingTests {

    // launched by hand in each test, so that the recently opened list can be seeded first -
    // the landing screen reads it while it is coming up
    @get:Rule val activityRule = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun setUp() {
        clearLandingState()

        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()

        clearLandingState()

        if (activityRule.activity != null) {
            activityRule.finishActivity()

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun emptyStateIsShownWhenNothingWasOpenedYet() {
        launch()

        onView(withId(R.id.landing_empty)).check(matches(isDisplayed()))
    }

    @Test
    fun aRecentDocumentIsListed() {
        seedRecentDocument()

        launch()

        onView(withText(TEST_DOCUMENT)).check(matches(isDisplayed()))
    }

    @Test
    fun aRecentDocumentOpensWhenTapped() {
        seedRecentDocument()

        val activity = launch()

        onView(withText(TEST_DOCUMENT)).perform(click())

        Assert.assertTrue(
            "the document did not load after tapping its entry in the recent list",
            waitForDocumentFragment(activity),
        )
    }

    @Test
    fun theCatchAllSettingIsOffered() {
        seedRecentDocument()

        launch()

        onView(withText(R.string.landing_catch_all_title)).check(matches(isDisplayed()))
    }

    private fun launch(): MainActivity {
        val activity = activityRule.launchActivity(null)

        activity.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        // the list is filled from a background executor, so give it a moment to arrive
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(1000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        return activity
    }

    private fun seedRecentDocument() {
        RecentDocumentsUtil.addRecentDocument(context(), TEST_DOCUMENT, uriOf(requireTestFile()))
    }

    private fun clearLandingState() {
        context().deleteFile("recent_documents.json")
    }

    private fun waitForDocumentFragment(activity: MainActivity): Boolean {
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS

        while (SystemClock.uptimeMillis() < deadline) {
            val fragment =
                activity.supportFragmentManager.findFragmentByTag("document_fragment")
                    as DocumentFragment?
            if (fragment != null) {
                return true
            }

            SystemClock.sleep(200)
        }

        return false
    }

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun uriOf(file: File): Uri =
        FileProvider.getUriForFile(context(), context().packageName + ".provider", file)

    private fun requireTestFile(): File = checkNotNull(testFile) { "test file was not extracted" }

    companion object {
        private const val TEST_DOCUMENT = "test.odt"
        private const val LOAD_TIMEOUT_MS = 20000L

        private var testFile: File? = null

        // @JvmStatic because junit requires @BeforeClass to be static
        @JvmStatic
        @BeforeClass
        fun extractTestFile() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()

            val directory = File(instrumentation.targetContext.cacheDir, "test-documents")
            directory.mkdirs()

            val target = File(directory, TEST_DOCUMENT)
            copy(instrumentation.context.assets.open(TEST_DOCUMENT), target)

            testFile = target
        }

        private fun copy(source: InputStream, target: File) {
            source.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        }
    }
}
