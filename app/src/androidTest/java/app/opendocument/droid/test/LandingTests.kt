// ActivityTestRule instead of ActivityScenario, matching MainActivityTests - see the note there.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
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
import org.hamcrest.Matcher
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

    /** With nothing to list, what the app is for is unfolded - there is nothing in its way. */
    @Test
    fun theIntroIsUnfoldedWhenNothingWasOpenedYet() {
        launch()

        onView(withId(R.id.landing_intro)).check(matches(isDisplayed()))
    }

    @Test
    fun theOpenButtonOpensTheSystemPicker() {
        stubOpenDocumentCancelled()

        launch()

        onView(withId(R.id.landing_open)).perform(closeSoftKeyboard(), click())

        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
    }

    /** And it stays above the documents once there are some, rather than going away with them. */
    @Test
    fun theOpenButtonStaysAboveTheDocuments() {
        seedRecentDocument()

        launch()

        onView(withId(R.id.landing_open)).check(matches(isDisplayed()))
        onView(withText(TEST_DOCUMENT)).check(matches(isDisplayed()))
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
        // deliberately not seeded: the fab is there whatever the list holds, empty state included
        stubOpenDocumentCancelled()

        launch()

        onView(withId(R.id.landing_open_fab)).perform(closeSoftKeyboard(), click())

        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
    }

    /** Swiping a recent document away is the only way to remove one, and it says so afterwards. */
    @Test
    fun aRecentDocumentCanBeSwipedAway() {
        seedRecentDocument()

        launch()

        onView(rowOf(TEST_DOCUMENT)).perform(closeSoftKeyboard(), swipeRowAway())

        onView(withText(R.string.landing_recent_removed)).check(matches(isDisplayed()))
        onView(withText(TEST_DOCUMENT)).check(doesNotExist())
    }

    /** The undo next to it puts the document back. */
    @Test
    fun removingARecentDocumentCanBeUndone() {
        seedRecentDocument()

        launch()

        onView(rowOf(TEST_DOCUMENT)).perform(closeSoftKeyboard(), swipeRowAway())
        onView(withText(R.string.landing_undo)).perform(click())

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(500)

        onView(withText(TEST_DOCUMENT)).check(matches(isDisplayed()))
    }

    /**
     * The row itself, not the filename inside it: ItemTouchHelper dismisses at half the width of
     * what was swiped, and the title is not wide enough to get there.
     */
    private fun rowOf(filename: String): Matcher<View> =
        allOf(hasDescendant(withText(filename)), withParent(withId(R.id.landing_list)))

    /**
     * Most of the width of the row, slowly, and starting inside it.
     *
     * Espresso's own swipeLeft is a fling across the middle of the view, and ItemTouchHelper reads
     * that as a flick that did not travel far enough to dismiss anything - it wants half the width.
     * The edges are out of bounds for the opposite reason: a drag that begins on the right edge of
     * the screen is the system back gesture, and the first version of this closed the activity
     * instead of the row.
     */
    private fun swipeRowAway(): ViewAction =
        GeneralSwipeAction(Swipe.SLOW, acrossRow(0.9f), acrossRow(0.05f), Press.FINGER)

    private fun acrossRow(fraction: Float): CoordinatesProvider = CoordinatesProvider { view ->
        val onScreen = IntArray(2)
        view.getLocationOnScreen(onScreen)

        floatArrayOf(onScreen[0] + view.width * fraction, onScreen[1] + view.height / 2f)
    }

    /**
     * The header is above the list rather than a row in it, so that it is there whatever the list
     * holds - a fresh install and one with documents in it both say what the app is.
     */
    @Test
    fun theHeaderStaysWhileDocumentsAreListed() {
        seedRecentDocument()

        launch()

        onView(withId(R.id.landing_header_logo)).check(matches(isDisplayed()))
        onView(withText(TEST_DOCUMENT)).check(matches(isDisplayed()))
    }

    /**
     * Once there are documents, what the app is for folds itself away: the user has read it by
     * then, and the list is what they came back for. The section itself stays.
     */
    @Test
    fun theIntroFoldsItselfOnceThereAreDocuments() {
        seedRecentDocument()

        launch()

        scrollTo(R.string.landing_section_intro)

        onView(withText(R.string.landing_section_intro)).check(matches(isDisplayed()))
        onView(withId(R.id.landing_intro)).check(doesNotExist())
    }

    /**
     * Removing the last document leaves a screen with nothing on it but the intro, so it comes back
     * open - even though it had folded itself away when the document was there.
     */
    @Test
    fun theIntroUnfoldsAgainWhenTheLastDocumentGoes() {
        seedRecentDocument()

        launch()

        onView(rowOf(TEST_DOCUMENT)).perform(closeSoftKeyboard(), swipeRowAway())

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(500)

        onView(withId(R.id.landing_intro)).check(matches(isDisplayed()))
    }

    /** And tapping the section brings it back. */
    @Test
    fun theIntroCanBeUnfoldedAgain() {
        seedRecentDocument()

        launch()

        scrollTo(R.string.landing_section_intro)
        onView(withText(R.string.landing_section_intro)).perform(closeSoftKeyboard(), click())

        onView(withId(R.id.landing_intro)).check(matches(isDisplayed()))
    }

    /**
     * The settings start folded whatever the list holds - one switch, for the few users whose file
     * manager will not hand a document over - so reaching them is a tap on the section.
     */
    @Test
    fun theCatchAllSettingIsFoldedAway() {
        seedRecentDocument()

        launch()

        scrollTo(R.string.landing_section_settings)

        onView(withText(R.string.landing_catch_all_title)).check(doesNotExist())
    }

    /**
     * A document that cannot be opened puts the user back on the list rather than on a blank page,
     * with the bar saying why still up. The bar's own offer - hand the file to another app - needs
     * no document, which is why leaving is safe here.
     */
    @Test
    fun aDocumentThatFailsToOpenComesBackToTheList() {
        seedBrokenDocument()

        launch()

        onView(withText(BROKEN_DOCUMENT)).perform(closeSoftKeyboard(), click())

        // espresso waits out the load itself: OpenFileIdling is busy until a loader callback runs
        onView(withText(R.string.toast_error_illegal_file_reopen)).check(matches(isDisplayed()))
        onView(withId(R.id.landing_list)).check(matches(isDisplayed()))
    }

    @Test
    fun theCatchAllSettingIsOffered() {
        seedRecentDocument()

        launch()

        unfoldSettings()

        onView(withText(R.string.landing_catch_all_title)).check(matches(isDisplayed()))
    }

    @Test
    fun theCatchAllSettingIsOfferedBeforeAnythingWasOpened() {
        // a fresh install is where the switch matters most, and it is furthest down the screen
        // there - the intro above it is unfolded
        launch()

        unfoldSettings()

        onView(withText(R.string.landing_catch_all_title)).check(matches(isDisplayed()))
    }

    private fun unfoldSettings() {
        scrollTo(R.string.landing_section_settings)
        onView(withText(R.string.landing_section_settings)).perform(closeSoftKeyboard(), click())

        scrollToCatchAllSetting()
    }

    /** Brings a row of the list on screen by the text somewhere inside it. */
    private fun scrollTo(text: Int) {
        onView(withId(R.id.landing_list))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(text)))
            )
    }

    /**
     * The settings sit under whatever the list is showing, and the intro alone is taller than a
     * small screen - so on the emulators CI runs, the switch is not merely off screen but never
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
     * A recent document that is not a document at all: bytes the core can make nothing of, so the
     * load fails the way a truncated download or a renamed file does.
     *
     * The extension is part of the fixture. `Odr.mimetype` cannot identify these bytes, so
     * `FileIdentifier` falls back to what the provider makes of the filename - and that type is
     * what decides which failure the user gets. `.bin` gives `application/octet-stream`, which the
     * core does not claim, so the file is reported as an unsupported format. Name it `.odt` and the
     * core claims the format and fails on the bytes, which is the broken-file dialog instead.
     */
    private fun seedBrokenDocument() {
        val broken = File(requireTestFile().parentFile, BROKEN_DOCUMENT)
        FileOutputStream(broken).use { output -> output.write(ByteArray(4096) { it.toByte() }) }

        RecentDocumentsUtil.addRecentDocument(context(), BROKEN_DOCUMENT, uriOf(broken))
    }

    /** A document left behind by an earlier run would keep the intro's Open button off screen. */
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
        private const val BROKEN_DOCUMENT = "broken.bin"
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
