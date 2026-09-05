package app.opendocument.droid.test

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.opendocument.droid.R
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.background.RecentDocumentsUtil
import app.opendocument.droid.ui.EditActionModeCallback
import app.opendocument.droid.ui.OpenFileIdling
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import app.opendocument.droid.ui.widget.DocumentActions
import app.opendocument.droid.ui.widget.PageView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pictures the play store shows.
 *
 * Six screens, in every locale the listing is written in, on whichever device the runner is
 * driving. `scripts/frame-screenshots.py` then frames what this writes and
 * `scripts/store_screenshots.py` checks the set and stages it for supply - the names below are the
 * names those expect, and their order is the order the store shows them in.
 *
 * Which locales those are, and which language's documents each of them reads, comes out of
 * `screenshot-names.json` beside the samples: `scripts/store_screenshots.py` holds that table and
 * `scripts/make-screenshot-documents.py` writes it in, so there is no second copy here to drift.
 *
 * **This is the whole back door.** An instrumented test runs in the app's own process, so laying
 * the sample documents out, filling the recently opened list and switching the app's language are
 * all things a test can do directly - and none of them needs a line of code in a build that ships.
 *
 * Everything after that goes through the app the way a user does: the recently opened list is
 * tapped-through documents, edit mode is the button, and the find bar is typed into. Photographing
 * a screen the app can only reach from a test would be photographing something nobody can get to.
 *
 * **Skipped unless a run names a device**, since `connectedCheck` runs everything there is on five
 * api levels and this is an hour of emulator each. `fastlane android screenshots` is the way in; by
 * hand it is the runner's own arguments, which is also how to look at one language without waiting
 * out the other fourteen:
 * ```
 * ./gradlew connectedProDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=app.opendocument.droid.test.ScreenshotTests \
 *     -Pandroid.testInstrumentationRunnerArguments.device=phone \
 *     -Pandroid.testInstrumentationRunnerArguments.locales=en-US+de-DE
 * ```
 *
 * Wants android 15 or newer, and says so rather than photographing what an older one draws.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
// api 33 for `LocaleManager`, which is what puts the app into a language. The pictures want
// android 15, which is a floor of its own and asserted below rather than skipped over.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class ScreenshotTests {

    // The activity is launched and finished per screen rather than once for the run: every screen
    // starts from the landing screen, and an edit mode or a find bar left standing is the sort of
    // thing that turns up in the next picture rather than in a failure. Ninety launches in a row
    // is also more than ActivityTestRule is built for, which is why there is none here.
    private var activity: MainActivity? = null

    private var idlingResource: IdlingResource? = null

    private var dressed = false

    @Before
    fun setUp() {
        val idlingResource = OpenFileIdling.idlingResource
        this.idlingResource = idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
    }

    @After
    fun tearDown() {
        finish()

        if (dressed) {
            undressTheDevice()
        }

        idlingResource?.let { IdlingRegistry.getInstance().unregister(it) }
    }

    @Test
    fun takesTheStoreScreenshots() {
        // Skipped unless a run asked for it by naming a device. `connectedCheck` runs every test
        // there is, on five api levels, and an hour of emulator per level photographing a store
        // listing nobody asked for is not what that job is for.
        Assume.assumeTrue(
            "no device given, so this is not a screenshot run - see the class comment",
            argument("device") != null,
        )

        // The app sets the system bar icons to suit the theme from api 35 on, and only there
        // (values-v35/themes.xml). Below it, a light-themed screen gets white icons on a white
        // bar: the clock and the battery are in every picture and none of them can be seen.
        // Which is a thing to be told rather than to notice in the store.
        Assert.assertTrue(
            "the store screenshots want android 15 or newer, not api ${Build.VERSION.SDK_INT}: " +
                "the status bar icons do not follow the light theme below it",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM,
        )

        // marked before rather than after: dressing that fails halfway has still changed
        // the device, and tearDown is the only thing that puts it back
        dressed = true
        dressTheDevice()

        val details = JSONObject(asset("screenshots/screenshot-names.json").decodeToString())
        val spoken = details.getJSONObject("locales")
        val languages = details.getJSONObject("languages")

        for (locale in locales(spoken)) {
            val language = spoken.getString(locale)
            val words = languages.optJSONObject(language) ?: languages.getJSONObject("en")

            speak(locale)
            val folder = layOut(language, words.getJSONObject("files"))
            fill(folder)

            landing(locale)
            searching(locale, folder.getValue("text"), words.getString("search"))
            editing(locale, folder.getValue("text"))
            document(locale, Shot.SHEET, folder.getValue("sheet"))
            document(locale, Shot.PDF, folder.getValue("paper"))
            document(locale, Shot.OFFICE, folder.getValue("word"))
        }
    }

    // --- the screens --------------------------------------------------------

    /** The landing screen, which is the recently opened documents and the way in. */
    private fun landing(locale: String) {
        val activity = launch()

        Assert.assertTrue(
            "the landing screen never listed the documents",
            waitFor(LIST_TIMEOUT_MS) { hasRows(activity) },
        )

        // the list fades its rows in, and the row heights settle a beat after the first of them
        settle()

        shoot(locale, Shot.RECENTS)
        finish()
    }

    /** One document, open and drawn. */
    private fun document(locale: String, shot: Shot, uri: Uri) {
        launchWith(uri)

        shoot(locale, shot)
        finish()
    }

    /**
     * The same text document, being edited, with the keyboard up.
     *
     * The keyboard needs a real tap: WebKit raises it for a gesture it saw, so an edit staged
     * entirely in code sets a caret and nothing else. Where the text is depends on the page, so
     * this works down the page rather than betting the run on one offset - the sample is a page of
     * A4 and a tap into its margin reaches nothing.
     */
    private fun editing(locale: String, uri: Uri) {
        val activity = launchWith(uri)
        val fragment = documentFragment(activity)

        val started = AtomicReference(false)
        instrumentation.runOnMainSync {
            started.set(
                activity.startSupportActionMode(EditActionModeCallback(activity, fragment)) != null
            )
        }
        Assert.assertTrue("edit mode did not start", started.get())

        val pageView = requireNotNull(fragment.pageView) { "the edit has no page to tap into" }
        Assert.assertTrue(
            "the page never became editable",
            waitFor(EDIT_TIMEOUT_MS) { isEditable(pageView) },
        )

        Assert.assertTrue(
            "no tap down the page set a caret, so the keyboard never came up",
            KEYBOARD_OFFSETS.any { offset ->
                onView(withId(R.id.page_view)).perform(tapAt(0.5f, offset))

                waitFor(KEYBOARD_TIMEOUT_MS) { keyboardIsUp(activity.window.decorView) }
            },
        )

        sendAwayWhatTheKeyboardIsAsking(activity)

        // the keyboard slides in, and a picture taken while it is halfway up is a picture of a
        // keyboard halfway up
        settle()

        shoot(locale, Shot.EDIT)
        finish()
    }

    /**
     * The text document with a word of its own searched for, so the hits are highlighted.
     *
     * The text document and not the pdf: the word is counted out of the report, and a find bar over
     * a document that does not say it is a picture of a search with no hits in it.
     */
    private fun searching(locale: String, uri: Uri, query: String) {
        val activity = launchWith(uri)

        instrumentation.runOnMainSync { activity.onDocumentAction(DocumentActions.ACTION_SEARCH) }

        Assert.assertTrue(
            "the find bar never came up",
            waitFor(FIND_TIMEOUT_MS) { activity.findViewById<EditText>(R.id.edit) != null },
        )

        // replaceText rather than typeText: the find bar searches on every keystroke, and typing
        // a word letter by letter is a dozen searches of the whole document for nothing
        onView(withId(R.id.edit)).perform(replaceText(query), closeSoftKeyboard())

        // and then the next-match button, as somebody searching would: the web view tints every
        // match on findAllAsync but only picks one of them out, and a picture of a search with no
        // match picked out is a picture of a search that has not been used yet
        onView(withId(R.id.find_next)).perform(click())

        // the highlights are drawn by the web view a beat after the field says the word
        settle()

        shoot(locale, Shot.TEXT)
        finish()
    }

    // --- the app's state ----------------------------------------------------

    /**
     * Puts the app into one language, and waits for the change to have reached it.
     *
     * The framework's own `LocaleManager`, not `AppCompatDelegate`: from api 33 on that only
     * forwards to this, and only once an AppCompat activity has attached itself to it. Called with
     * nothing on screen - which is where this has to be called - it returns having done nothing at
     * all, and the whole set comes out in the language the last one was in.
     *
     * The system server is what applies it, so it is waited on rather than assumed: an activity
     * launched before the new configuration reaches the process comes up in the language it was.
     */
    private fun speak(locale: String) {
        val wanted = Locale.forLanguageTag(locale)
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(locale)

        Assert.assertTrue(
            "the app never came up in $locale",
            waitFor(LOCALE_TIMEOUT_MS) {
                context.resources.configuration.locales[0].language == wanted.language
            },
        )
    }

    /**
     * Copies this language's samples out of the test apk and onto the device, under the names the
     * folder should read as.
     *
     * Into the app's own cache directory, so they are reachable through the app's own
     * `FileProvider` and need no storage access grant - which is also why the recently opened list
     * below is one the app can really open. Seeding it with uris nobody holds a grant for would
     * photograph a list that does nothing when tapped.
     */
    private fun layOut(language: String, titles: JSONObject): Map<String, Uri> {
        val folder = File(context.cacheDir, "screenshots")
        folder.deleteRecursively()
        folder.mkdirs()

        val laid = LinkedHashMap<String, Uri>()
        for ((name, source) in SAMPLES) {
            val extension = EXTENSIONS.getValue(source)
            val title = titles.optString(name).ifEmpty { name }

            val file = File(folder, "$title.$extension")
            FileOutputStream(file).use { out ->
                out.write(asset("screenshots/sample-$source-$language.$extension"))
            }

            laid[name] =
                FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        }

        return laid
    }

    /**
     * Writes the recently opened list, newest first and spread over the last few days.
     *
     * `restoreRecentDocument` rather than `addRecentDocument`, because only the former takes the
     * time the entry was opened at: added, all of them would carry this second and the column
     * beside them would read "0 minutes ago" twenty-seven times over.
     */
    private fun fill(folder: Map<String, Uri>) {
        File(context.filesDir, "recent_documents.json").delete()

        val now = System.currentTimeMillis()
        folder.values.forEachIndexed { index, uri ->
            // from one step back rather than from now: the newest entry would otherwise be
            // "0 minutes ago", which is the app being opened for the picture rather than a list
            // somebody has been using
            val opened = now - OPENED_APART_MS * (index + 1)
            val name = File(uri.path.orEmpty()).name

            RecentDocumentsUtil.restoreRecentDocument(
                context,
                RecentDocumentList.Entry(name, uri.toString(), opened),
                index,
            )
        }
    }

    // --- driving the app ----------------------------------------------------

    private fun launch(): MainActivity {
        finish()

        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val launched = instrumentation.startActivitySync(intent) as MainActivity
        activity = launched

        // espresso picks its root by window focus and fails where it finds none
        Assert.assertTrue(
            "the activity never took window focus",
            waitFor(FOCUS_TIMEOUT_MS) { launched.hasWindowFocus() },
        )

        return launched
    }

    /** Launches and opens [uri], waiting for the page to have drawn rather than for the load. */
    private fun launchWith(uri: Uri): MainActivity {
        val activity = launch()

        instrumentation.runOnMainSync { activity.loadUri(uri) }

        val fragment = documentFragment(activity)
        Assert.assertTrue(
            "$uri never finished loading",
            waitFor(LOAD_TIMEOUT_MS) { fragment.hasLastResult() },
        )

        val pageView = requireNotNull(fragment.pageView) { "$uri produced no page" }
        Assert.assertTrue(
            "$uri loaded but the page never drew anything",
            waitFor(DRAW_TIMEOUT_MS) { hasDrawn(pageView) },
        )

        // the page is there; what is still moving is the buttons fading in over it
        settle()

        return activity
    }

    private fun finish() {
        val running = activity ?: return
        activity = null

        instrumentation.runOnMainSync { running.finish() }
        instrumentation.waitForIdleSync()

        waitFor(FOCUS_TIMEOUT_MS) { running.isDestroyed }
    }

    private fun documentFragment(activity: MainActivity): DocumentFragment {
        var fragment: DocumentFragment? = null
        Assert.assertTrue(
            "no document fragment came up",
            waitFor(LOAD_TIMEOUT_MS) {
                fragment =
                    activity.supportFragmentManager.findFragmentByTag("document_fragment")
                        as DocumentFragment?
                fragment != null
            },
        )

        return checkNotNull(fragment)
    }

    /**
     * The way in, the section heading and a document under it: a list with fewer rows drawn than
     * that is one the recently opened documents have not reached yet.
     *
     * Asked as a plain `ViewGroup` rather than as the `RecyclerView` it is, so this test needs
     * nothing on its compile path that the app happens to bring along.
     */
    private fun hasRows(activity: MainActivity): Boolean {
        val list = activity.findViewById<ViewGroup>(R.id.landing_list)

        return (list?.childCount ?: 0) > 2
    }

    /** Whether the page has laid something out, which is a step past the load reporting success. */
    private fun hasDrawn(pageView: PageView): Boolean {
        val answer =
            javascript(
                pageView,
                "(function(){return document.readyState === 'complete' && !!document.body &&" +
                    " document.body.innerText.trim().length > 0;})()",
            )

        return "true".equals(answer?.replace("\"", ""), ignoreCase = true)
    }

    private fun isEditable(pageView: PageView): Boolean {
        val answer =
            javascript(
                pageView,
                "(function(){return !!(document.body && document.body.isContentEditable) ||" +
                    " !!document.querySelector('[contenteditable=\"true\"],'+" +
                    " '[contenteditable=\"plaintext-only\"]');})()",
            )

        return "true".equals(answer?.replace("\"", ""), ignoreCase = true)
    }

    /**
     * Null when the page did not answer in time, which the caller's poll owns rather than fails.
     */
    private fun javascript(pageView: PageView, script: String): String? {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            pageView.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }

        return if (latch.await(JS_ANSWER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) result.get() else null
    }

    /**
     * Sends away whatever the keyboard has put over itself before it is photographed.
     *
     * Asked for a language it has no layout for, gboard covers its own keys with a picker - two
     * layouts to choose between, `Skip` and `Next` - and the picture comes out of a keyboard being
     * set up rather than of a document being edited. Japanese is the one that asks; a language
     * whose layout is not in question never does.
     *
     * What is looked for is the *class*: a key is a `FrameLayout` carrying a description, while
     * what these put up is a real `Button` with a word on it. So there is no list of buttons per
     * language here, and the next thing gboard decides to ask is dismissed by the same code.
     */
    private fun sendAwayWhatTheKeyboardIsAsking(activity: MainActivity) {
        repeat(SETUP_ASKS) {
            val asking = buttonOverTheKeyboard() ?: return

            // the first of them, which is the way out: `Skip` sits left of `Next`, and a picker
            // that answers `Next` instead only comes back with the next thing it wants to know
            instrumentation.runOnMainSync {
                asking.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            settle()
        }

        Assert.assertNull(
            "the keyboard kept asking something, so it cannot be photographed",
            buttonOverTheKeyboard(),
        )

        Assert.assertTrue(
            "the keyboard went away with what it was asking",
            waitFor(KEYBOARD_TIMEOUT_MS) { keyboardIsUp(activity.window.decorView) },
        )
    }

    /** The first button in the keyboard's own window, or null while it is only keys. */
    private fun buttonOverTheKeyboard(): AccessibilityNodeInfo? {
        fun below(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.className == "android.widget.Button") {
                return node
            }

            for (index in 0 until node.childCount) {
                below(node.getChild(index))?.let {
                    return it
                }
            }

            return null
        }

        return instrumentation.uiAutomation.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .firstNotNullOfOrNull { below(it.root) }
    }

    private fun keyboardIsUp(decorView: View): Boolean =
        ViewCompat.getRootWindowInsets(decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /** A tap at a place on the view rather than at its middle, which is where the margin is. */
    private fun tapAt(across: Float, down: Float): ViewAction =
        GeneralClickAction(
            Tap.SINGLE,
            CoordinatesProvider { view ->
                val at = IntArray(2)
                view.getLocationOnScreen(at)

                floatArrayOf(at[0] + view.width * across, at[1] + view.height * down)
            },
            Press.FINGER,
            0,
            0,
        )

    // --- the picture --------------------------------------------------------

    private fun shoot(locale: String, shot: Shot) {
        val bitmap =
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "the screen could not be photographed for $locale ${shot.fileName}"
            }

        val folder = File(writable(), locale)
        folder.mkdirs()

        val file = File(folder, "$device-${shot.fileName}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()

        Assert.assertTrue("nothing was written to $file", file.length() > 0)
    }

    /**
     * Where the pictures go: the directory gradle hands the run for output of its own.
     *
     * Not `getExternalFilesDir`: `connectedAndroidTest` uninstalls both apks when the run ends, and
     * an app's external directory goes with it - so the pictures were written, the test passed, and
     * there was nothing left to fetch.
     *
     * `additionalTestOutputDir` is gradle's own answer to that: it copies what is in there back to
     * `app/build/outputs/connected_android_test_additional_output/` *before* it uninstalls
     * anything, so nothing has to be pulled by hand and nothing is racing the cleanup.
     */
    private fun writable(): File {
        val given =
            requireNotNull(argument("additionalTestOutputDir")) {
                "gradle passed no additionalTestOutputDir, so there is nowhere to write a " +
                    "screenshot that would outlive the run. Start this from " +
                    "`fastlane android screenshots` or from connectedProDebugAndroidTest, not " +
                    "from `adb shell am instrument`."
            }

        return File(given, "screenshots")
    }

    // --- what the run was asked for -----------------------------------------

    /**
     * The locales to photograph: every one the listing is written in, unless fewer were named.
     *
     * A plus separates them as well as a comma, which AGP 9.4.0 cuts such a property at.
     */
    private fun locales(spoken: JSONObject): List<String> {
        val known = spoken.keys().asSequence().sorted().toList()

        val given = argument("locales")
        if (given.isNullOrBlank()) {
            return known
        }

        val wanted = given.split(',', '+').map { it.trim() }.filter { it.isNotEmpty() }
        val unknown = wanted.filterNot { it in known }
        Assert.assertTrue(
            "no such locale: ${unknown.joinToString()}. One of ${known.joinToString()}",
            unknown.isEmpty(),
        )

        return wanted
    }

    /** Which device the runner is driving, which only the runner knows. */
    private val device: String
        get() {
            val given = argument("device") ?: "phone"
            Assert.assertTrue(
                "no such device: $given. One of ${DEVICES.joinToString()}",
                given in DEVICES,
            )

            return given
        }

    private fun asset(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private fun settle() {
        SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private enum class Shot(val fileName: String) {
        RECENTS("01-recents"),
        TEXT("02-text"),
        SHEET("03-sheet"),
        EDIT("04-edit"),
        PDF("05-pdf"),
        OFFICE("06-office"),
    }

    companion object {
        private val DEVICES = listOf("phone", "tablet")

        /**
         * The folder, in the order the landing screen should list it: the nine documents the
         * screenshots open, and then the rest, so it reads as a folder somebody keeps things in
         * rather than as a set of samples. Each of the rest is a copy of one of the nine under a
         * name of its own, and none of them is ever opened.
         */
        private val SAMPLES =
            linkedMapOf(
                "text" to "text",
                "sheet" to "sheet",
                "slides" to "slides",
                "word" to "word",
                "cells" to "cells",
                "deck" to "deck",
                "paper" to "paper",
                "rows" to "rows",
                "notes" to "notes",
                "meeting" to "text",
                "letter" to "text",
                "travel" to "text",
                "reading" to "text",
                "household" to "sheet",
                "hours" to "sheet",
                "stocktake" to "sheet",
                "kickoff" to "slides",
                "course" to "slides",
                "lease" to "word",
                "resume" to "word",
                "application" to "word",
                "expenses" to "cells",
                "inventory" to "cells",
                "review" to "deck",
                "ticket" to "paper",
                "warranty" to "paper",
                "manual" to "paper",
            )

        private val EXTENSIONS =
            mapOf(
                "text" to "odt",
                "sheet" to "ods",
                "slides" to "odp",
                "word" to "docx",
                "cells" to "xlsx",
                "deck" to "pptx",
                "paper" to "pdf",
                "rows" to "csv",
                "notes" to "txt",
            )

        /** Near the top: the sample is a page of A4 with a few lines on it. */
        private val KEYBOARD_OFFSETS = listOf(0.20f, 0.26f, 0.14f, 0.32f, 0.40f)

        // spread over the last few days, newest first, so the times beside the rows read like a
        // list somebody has been using
        private const val OPENED_APART_MS = 2 * 60 * 60 * 1000L

        // a cold emulator opening a document it has to translate first
        private const val LOAD_TIMEOUT_MS = 60000L
        private const val DRAW_TIMEOUT_MS = 30000L
        private const val EDIT_TIMEOUT_MS = 30000L
        private const val LIST_TIMEOUT_MS = 20000L
        private const val FOCUS_TIMEOUT_MS = 10000L
        private const val FIND_TIMEOUT_MS = 10000L
        private const val KEYBOARD_TIMEOUT_MS = 5000L

        // one to choose a layout, and room for whatever it wants to know after that
        private const val SETUP_ASKS = 3
        private const val LOCALE_TIMEOUT_MS = 10000L
        private const val JS_ANSWER_TIMEOUT_MS = 10000L

        private const val POLL_MS = 200L
        private const val SETTLE_MS = 1500L

        // long enough for system ui to come back after the theme and the navigation bar change
        private const val RESTART_MS = 6000L

        // for the request to have reached the display, and then for it to have turned: a
        // runner painting through swiftshader has taken longer over that than any beat worth
        // waiting on every rotation
        private const val ROTATE_MS = 2500L
        private const val ROTATE_TIMEOUT_MS = 30000L

        private fun waitFor(timeoutMs: Long, until: () -> Boolean): Boolean {
            val startMs = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startMs < timeoutMs) {
                if (until()) {
                    return true
                }
                SystemClock.sleep(POLL_MS)
            }

            return until()
        }

        private fun argument(name: String): String? =
            InstrumentationRegistry.getArguments().getString(name)

        /**
         * Runs a shell command and waits for it to have finished.
         *
         * Waits by reading: the pipe reaches its end when the command exits, and closing it unread
         * instead returns in a handful of milliseconds whatever the command was doing - five,
         * measured, for a `sleep 2`. Which is how the store set came back with a status bar reading
         * the real time: `demo enter` landed and the six commands after it, the clock and the
         * battery among them, were fired into a device already busy with the next one. `am
         * broadcast` answers `Broadcast completed` once its receiver has run, so waiting for it is
         * also what puts them in order.
         */
        private fun shell(command: String) {
            val pipe =
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand(command)

            ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }
        }

        /**
         * The device the store should see: upright, in the light, on gestures, and with the status
         * bar every store screenshot has had since the first one.
         *
         * 9:41 and a full battery is what `override_status_bar` is on the App Store side; android
         * has a demo mode for it, which has to be allowed before it can be entered.
         *
         * Light rather than whatever the emulator image happens to default to - which is dark on
         * the ones CI creates. Both are the real app, but a set of pictures has to pick one, and
         * the light one is what the app opens as on a phone out of the box.
         *
         * Gesture navigation for the same reason: the three button bar is a setting almost nobody
         * changes back to, and it takes a strip off the bottom of every picture.
         */
        fun dressTheDevice() {
            // the keyboard is not the active window, and without this it is not among the ones
            // `uiAutomation` will answer with at all
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val service = automation.serviceInfo
            service.flags =
                service.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = service

            standUpright()

            // Before demo mode and not after: both of these restart system ui, and a system ui
            // that restarts forgets it was in demo mode - which is a status bar showing the real
            // time in the corner of every picture, and nothing failing to say so.
            shell("cmd uimode night no")
            shell("cmd overlay enable com.android.internal.systemui.navbar.gestural")
            SystemClock.sleep(RESTART_MS)

            shell("settings put global sysui_demo_allowed 1")
            demo("enter")
            demo("clock -e hhmm 0941")

            // plugged false, or the battery is drawn with a charging bolt in it - which says the
            // picture was taken on a desk rather than that the app was being used
            demo("battery -e level 100 -e plugged false -e powersave false")

            // Wifi at full, and no mobile signal at all: a bar of wifi and a full battery is what
            // a store screenshot has looked like for fifteen years, and the alternative here is a
            // 3G badge from the emulator's fake network in the corner of all 180 of them.
            // mobile first: the extras are one flat bundle, so the `level` that follows belongs to
            // whichever radio was named last, and wifi has to be the one that gets it
            demo("network -e mobile hide -e wifi show -e level 4")

            // and everything the system puts up there of its own: the emulator arrives with a
            // notification or two, and they are in every picture until they are told not to be
            demo("notifications -e visible false")
            demo(
                "status -e volume hide -e bluetooth hide -e location hide -e alarm hide " +
                    "-e sync hide -e tty hide -e eri hide -e mute hide -e speakerphone hide"
            )

            SystemClock.sleep(SETTLE_MS)
        }

        private fun demo(command: String) {
            shell("am broadcast -a com.android.systemui.demo -e command $command")
        }

        /**
         * Turns the device upright and holds it there.
         *
         * Tried rather than told: rotation 0 is a device's *natural* orientation, and a tablet's
         * natural orientation is landscape - so the same setting that stands a phone up lays a
         * tablet down.
         *
         * What it settles on is checked by photographing the screen, which is the only thing that
         * answers the question actually being asked: `wm size` reports the panel, not the way up
         * the picture will come out.
         */
        private fun standUpright() {
            shell("settings put system accelerometer_rotation 0")

            for (rotation in 0..3) {
                shell("settings put system user_rotation $rotation")

                // the beat first and the poll after: asked too early the screen still answers
                // with the way up it is leaving, and a rotation that has really been refused
                // answers the same way for as long as it is waited on
                SystemClock.sleep(ROTATE_MS)
                if (waitFor(ROTATE_TIMEOUT_MS) { isUpright() }) {
                    return
                }
            }

            throw AssertionError(
                "no rotation stood this device upright, so it cannot be photographed"
            )
        }

        private fun isUpright(): Boolean {
            val shot =
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?: return false
            val upright = shot.height > shot.width
            shot.recycle()

            return upright
        }

        fun undressTheDevice() {
            demo("exit")
            shell("settings put system accelerometer_rotation 1")
        }
    }
}
