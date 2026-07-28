// ActivityTestRule instead of ActivityScenario, matching MainActivityTests - see the note there.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import app.opendocument.droid.R
import app.opendocument.droid.background.FolderTreesUtil
import app.opendocument.droid.background.RecentDocumentsUtil
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.hamcrest.Matchers.allOf
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
    fun emptyStateOpensTheSystemPicker() {
        stubOpenDocumentCancelled()

        launch()

        onView(withId(R.id.landing_empty_open)).perform(closeSoftKeyboard(), click())

        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
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

        onView(withText(TEST_DOCUMENT)).perform(closeSoftKeyboard(), click())

        Assert.assertTrue(
            "the document did not load after tapping its entry in the recent list",
            waitForDocumentFragment(activity),
        )
    }

    @Test
    fun theFabOpensTheSystemPicker() {
        // the fab is hidden behind the empty state, which offers the same thing with a label
        seedRecentDocument()
        stubOpenDocumentCancelled()

        launch()

        onView(withId(R.id.landing_open_fab)).perform(closeSoftKeyboard(), click())

        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
    }

    @Test
    fun addingAFolderAsksTheSystemForATree() {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        launch()

        onView(withId(R.id.landing_empty_add_folder)).perform(closeSoftKeyboard(), click())

        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    @Test
    fun aGrantedFolderIsListed() {
        seedFolderTree()

        launch()

        onView(withText(TEST_FOLDER)).check(matches(isDisplayed()))
    }

    /**
     * Removing a granted folder, which a long press and a swipe now reach the same way - the swipe
     * gesture itself is ItemTouchHelper's, so this covers what both of them call.
     */
    @Test
    fun aGrantedFolderCanBeRemoved() {
        seedFolderTree()

        launch()

        onView(withText(TEST_FOLDER)).perform(closeSoftKeyboard(), longClick())

        onView(withText(R.string.landing_folder_removed)).check(matches(isDisplayed()))
        onView(withText(TEST_FOLDER)).check(doesNotExist())
    }

    /** The undo next to it puts the folder back, cached name and all. */
    @Test
    fun removingAGrantedFolderCanBeUndone() {
        seedFolderTree()

        launch()

        onView(withText(TEST_FOLDER)).perform(closeSoftKeyboard(), longClick())
        onView(withText(R.string.landing_undo)).perform(click())

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(500)

        onView(withText(TEST_FOLDER)).check(matches(isDisplayed()))
    }

    @Test
    fun theFoldersSectionOffersToAddOne() {
        seedRecentDocument()

        launch()

        onView(withText(R.string.landing_section_folders)).check(matches(isDisplayed()))

        // the empty state carries a button with the same label, so match the one on screen -
        // the empty state is gone once there is a recent document to show
        onView(allOf(withText(R.string.landing_add_folder), isDisplayed()))
            .check(matches(isDisplayed()))
    }

    @Test
    fun theCatchAllSettingIsOffered() {
        seedRecentDocument()

        launch()

        scrollToCatchAllSetting()

        onView(withText(R.string.landing_catch_all_title)).check(matches(isDisplayed()))
    }

    @Test
    fun theCatchAllSettingIsOfferedBeforeAnythingWasOpened() {
        // a fresh install is where the switch matters most, and the empty state used to be shown
        // instead of the list it sits in
        launch()

        scrollToCatchAllSetting()

        onView(withText(R.string.landing_catch_all_title)).check(matches(isDisplayed()))
    }

    /**
     * The settings sit under whatever the list is showing, and the empty state alone is taller than
     * a small screen - so on the emulators CI runs, the switch is not merely off screen but never
     * bound at all, and no matcher can find it.
     *
     * Scrolling is the whole point of the row being in the list rather than a screen of its own, so
     * this scrolls the way a user would and then asserts. It has to say nothing about how far.
     */
    private fun scrollToCatchAllSetting() {
        onView(withId(R.id.landing_list))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText(R.string.landing_catch_all_title))
                )
            )
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

    /**
     * The picker is stubbed as cancelled: these tests are about what the landing screen asks for,
     * not about loading a document.
     */
    private fun stubOpenDocumentCancelled() {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    private fun seedRecentDocument() {
        RecentDocumentsUtil.addRecentDocument(context(), TEST_DOCUMENT, uriOf(requireTestFile()))
    }

    /**
     * A folder tree written straight into the store, cached display name and all.
     *
     * No real grant behind it, which is enough for the row: the name is the one written down when
     * the folder was added, so listing it asks no provider anything. Entering it would come up
     * empty, and none of these tests do.
     */
    private fun seedFolderTree() {
        FolderTreesUtil.addFolderTree(
            context(),
            Uri.parse("content://app.opendocument.test/tree/seeded"),
            TEST_FOLDER,
        )
    }

    /**
     * Both stores, not just the recent documents: a folder granted on this device - by a previous
     * run, or by hand - would keep the empty state from ever being shown.
     */
    private fun clearLandingState() {
        context().deleteFile("recent_documents.json")
        context().deleteFile("folder_trees.json")
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
        private const val TEST_FOLDER = "Seeded folder"
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
