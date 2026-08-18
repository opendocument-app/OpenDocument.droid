// ActivityTestRule instead of ActivityScenario, for the reason MainActivityTests gives.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.opendocument.droid.background.DocumentDarkening
import app.opendocument.droid.background.NightModeSetting
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import app.opendocument.droid.ui.widget.DocumentActions
import app.opendocument.droid.ui.widget.PageView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The document follows the app into night mode, if it is a kind of document that reads better for
 * it, and the switches over it are what say otherwise.
 *
 * A webview darkens a page algorithmically and only while the app theme reports itself dark, so
 * every test here puts the app in night mode first - in day mode nothing below would fail.
 * [theSwitchDarkensADayModeApp] is the exception, and undoes it.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class DarkModeTests {

    @get:Rule
    val mainActivityActivityTestRule = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun enterNightMode() {
        setNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @After
    fun leaveNightMode() {
        setNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // both switches keep their answer on disk, where the next test would find it. asking for
        // what the system says is what stores no override at all - see NightModeSetting.setNight
        NightModeSetting.setNight(targetContext(), systemIsNight())

        for (kind in DocumentDarkening.Kind.entries) {
            DocumentDarkening.clear(targetContext(), kind)
        }

        // switching night mode recreates the activity, leaving one behind that the rule does not
        // know about - and it would still be up when the next test launches its own
        val resumed = resumedMainActivity()
        if (resumed != null && resumed !== mainActivityActivityTestRule.activity) {
            onMainThread { resumed.finish() }
        }
    }

    @Test
    fun aDocumentIsDarkened() {
        assertDarkened(openPageView("test.odt"))
    }

    /**
     * A pdf does not, which is the one kind the app is sure about: a scanned page inverts into
     * something nobody wrote. See [DocumentDarkening].
     */
    @Test
    fun aPdfIsNotDarkened() {
        val pageView = openPageView("dummy.pdf")

        Assert.assertFalse("the pdf was allowed to darken", pageView.isDarkeningAllowed)
        assertNotDarkened(pageView)
    }

    /** What the button says is kept for every document of that kind, not for the file it was on. */
    @Test
    fun theDocumentSwitchIsRememberedForTheKind() {
        // after openPageView, which is what launches it
        val pageView = openPageView("dummy.pdf")
        val activity = mainActivityActivityTestRule.activity

        Assert.assertFalse("the pdf started out darkened", pageView.isDarkeningAllowed)

        onMainThread { activity.onDocumentAction(DocumentActions.ACTION_DOCUMENT_DARKENING) }

        // no reload: darkening is a webview setting, not something the page was translated with
        assertDarkened(pageView)

        assertDarkened(reopenPageView(activity, "dummy.pdf"))
    }

    /** What is drawn, not only the flag: a webview ignoring the setting passes the flag check. */
    @Test
    fun theDrawnPageIsDark() {
        Assume.assumeTrue(
            "this webview has no darkening api at all - nothing the app sets could reach it",
            canDarken(),
        )

        openPageView("test.odt")

        // the darkening lands while compositing, which the load callback does not wait for, and
        // an emulator painting through swiftshader has taken longer than the 30s the loads get.
        // the last reading is kept for the message: an argument is evaluated before the call it
        // is an argument to, so measuring it there would report the screen before the wait
        var luminance = WHITE
        val darkened = waitFor(60000) { meanLuminance().also { luminance = it } < DARK_LUMINANCE }

        Assert.assertTrue(
            "the page stayed light - mean luminance $luminance; ${darkeningDiagnosis()}",
            darkened,
        )
    }

    /**
     * The switch over the document, which is the answer for a phone that stays in day mode all
     * night - the only test here that starts in day mode, since that is what it switches out of.
     */
    @Test
    fun theSwitchDarkensADayModeApp() {
        Assume.assumeTrue(
            "this webview has no darkening api at all - nothing the app sets could reach it",
            canDarken(),
        )
        Assume.assumeFalse(
            "the device itself is in night mode - there is no day mode to switch out of",
            systemIsNight(),
        )

        // undoing what every other test here starts from: the switch is the only thing that
        // should be putting this app in night mode
        setNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        openPageView("test.odt")

        onMainThread {
            mainActivityActivityTestRule.activity.onDocumentAction(
                DocumentActions.ACTION_NIGHT_MODE
            )
        }

        // 60s and the last reading kept, for the reason theDrawnPageIsDark gives: this waits on
        // the same screenshot, and an activity recreation happens before it
        var luminance = WHITE
        val darkened = waitFor(60000) { meanLuminance().also { luminance = it } < DARK_LUMINANCE }

        Assert.assertTrue(
            "the page stayed light - mean luminance $luminance; ${darkeningDiagnosis()}",
            darkened,
        )
        Assert.assertEquals(
            "the switch was not remembered",
            AppCompatDelegate.MODE_NIGHT_YES,
            NightModeSetting.mode(targetContext()),
        )
    }

    /** Printing holds the page light, and only the last job still reading it gives it back. */
    @Test
    fun printingGivesTheDarkeningBack() {
        val pageView = openPageView("test.odt")

        onMainThread {
            pageView.suspendDarkening()
            pageView.suspendDarkening()
        }
        assertNotDarkened(pageView)

        onMainThread { pageView.resumeDarkening() }
        assertNotDarkened(pageView)

        onMainThread { pageView.resumeDarkening() }
        assertDarkened(pageView)
    }

    private fun assertDarkened(pageView: PageView) {
        Assert.assertTrue("the page was not allowed to darken", pageView.isDarkeningAllowed)

        val darkening = darkeningSetting(pageView) ?: return

        Assert.assertTrue("the webview was not told to darken the page", darkening)
    }

    private fun assertNotDarkened(pageView: PageView) {
        val darkening = darkeningSetting(pageView) ?: return

        Assert.assertFalse("the webview was still told to darken the page", darkening)
    }

    /**
     * What the webview was actually given, or null where it has neither api to ask - an old webview
     * cannot darken a page whatever the app asks of it.
     */
    private fun darkeningSetting(pageView: PageView): Boolean? {
        val darkening = AtomicReference<Boolean?>(null)

        onMainThread {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                darkening.set(WebSettingsCompat.isAlgorithmicDarkeningAllowed(pageView.settings))
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                darkening.set(
                    WebSettingsCompat.getForceDark(pageView.settings) !=
                        WebSettingsCompat.FORCE_DARK_OFF
                )
            }
        }

        return darkening.get()
    }

    /**
     * What the webview is and what it was given, for a failure message: these tests fail on one api
     * level at a time, on emulators whose webview is far older than any developer machine's, and
     * "the page stayed light" alone does not say which of the two darkening apis was even in play.
     */
    private fun darkeningDiagnosis(): String {
        val algorithmic = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
        val force = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)

        val webView =
            try {
                WebViewCompat.getCurrentWebViewPackage(targetContext())?.versionName ?: "none"
            } catch (t: Throwable) {
                "unknown (${t.javaClass.simpleName})"
            }

        val pageView = resumedMainActivity()?.let { waitForFragment(it)?.pageView }

        return "webview $webView, algorithmicDarkening=$algorithmic, forceDark=$force, " +
            "night=${NightModeSetting.isNight(targetContext())}, " +
            "allowed=${pageView?.isDarkeningAllowed}, setting=${pageView?.let(::darkeningSetting)}"
    }

    private fun canDarken() =
        WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ||
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)

    /**
     * What the middle of the screen draws, averaged - 0 is black and 255 white.
     *
     * A screenshot rather than `pageView.draw()`: the WebView renders on the compositor, so drawing
     * it into a bitmap of our own hands back an empty layer whatever the page looks like. The
     * middle, so the bars and the buttons over the page do not count towards the answer.
     */
    private fun meanLuminance(): Int {
        val screen =
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: return WHITE

        val left = screen.width / 4
        val right = screen.width * 3 / 4
        val top = screen.height / 3
        val bottom = screen.height * 2 / 3

        var total = 0L
        var counted = 0
        // a sample, not every pixel: this is polled, and the answer is an average anyway
        for (x in left until right step SAMPLE_STEP) {
            for (y in top until bottom step SAMPLE_STEP) {
                val pixel = screen.getPixel(x, y)
                total += ((pixel shr 16 and 0xff) + (pixel shr 8 and 0xff) + (pixel and 0xff)) / 3
                counted++
            }
        }
        screen.recycle()

        return if (counted == 0) WHITE else (total / counted).toInt()
    }

    private fun openPageView(name: String): PageView {
        // the activity first: a freshly installed app has never run, so its cache directory -
        // where the document has to be for the app's own FileProvider to hand it back - is only
        // there once the app process has started
        val activity = mainActivityActivityTestRule.launchActivity(null)
        val uri = uriOf(extract(name))

        onMainThread { activity.loadUri(uri) }

        val fragment = checkNotNull(waitForFragment(activity)) { "no document fragment" }
        Assert.assertTrue(
            "$name never loaded",
            waitFor(30000) { fragment.lastDocument?.request?.uri == uri },
        )

        return checkNotNull(fragment.pageView) { "no page view" }
    }

    /** The same file again in the activity already up: a page view that was told nothing yet. */
    private fun reopenPageView(activity: MainActivity, name: String): PageView {
        val fragment = checkNotNull(waitForFragment(activity)) { "no document fragment" }
        val before = fragment.lastDocument

        val uri = uriOf(extract(name))
        onMainThread { activity.loadUri(uri) }

        // not the uri, which is the one it already had: what says this load landed is a
        // document that is not the one from the load before it
        Assert.assertTrue(
            "$name never loaded again",
            waitFor(30000) { fragment.lastDocument != null && fragment.lastDocument !== before },
        )

        return checkNotNull(fragment.pageView) { "no page view" }
    }

    private fun waitForFragment(activity: MainActivity): DocumentFragment? {
        var fragment: DocumentFragment? = null
        waitFor(30000) {
            fragment =
                activity.supportFragmentManager.findFragmentByTag("document_fragment")
                    as DocumentFragment?
            fragment != null
        }
        return fragment
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        do {
            if (condition()) {
                return true
            }
            SystemClock.sleep(250)
        } while (SystemClock.elapsedRealtime() - startMs < timeoutMs)
        return false
    }

    /** Everything touching a WebView, its settings included, has to run where it was made. */
    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun setNightMode(mode: Int) {
        onMainThread { AppCompatDelegate.setDefaultNightMode(mode) }
    }

    /** The device's own answer, which an activity carrying a local mode no longer gives. */
    private fun systemIsNight(): Boolean =
        Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    /** Whatever is on screen, which after a night mode switch is not what the rule launched. */
    private fun resumedMainActivity(): MainActivity? {
        val current = AtomicReference<MainActivity>()
        onMainThread {
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

    private fun uriOf(file: File): Uri {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

        return FileProvider.getUriForFile(appCtx, appCtx.packageName + ".provider", file)
    }

    /** The asset [name], copied where the app can read it back through its FileProvider. */
    private fun extract(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.cacheDir, "dark-mode-$name")

        instrumentation.context.assets.open(name).use { input: InputStream ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }

        return target
    }

    private companion object {
        /** Below this the page is dark rather than the white a document is authored on. */
        private const val DARK_LUMINANCE = 128

        private const val WHITE = 255

        private const val SAMPLE_STEP = 8
    }
}
