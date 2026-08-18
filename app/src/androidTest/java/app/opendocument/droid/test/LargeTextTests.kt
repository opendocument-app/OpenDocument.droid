// ActivityTestRule instead of ActivityScenario, for the reason MainActivityTests gives.
@file:Suppress("DEPRECATION")

package app.opendocument.droid.test

import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity
import app.opendocument.droid.ui.widget.PageView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A plain text file of the size a real one is: it has to open, and it has to be searchable once it
 * has.
 *
 * Both of these are end to end through odrcore, which is where a failure will be - the app hands it
 * the bytes and shows what comes back.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LargeTextTests {

    @get:Rule
    val mainActivityActivityTestRule = ActivityTestRule(MainActivity::class.java, false, false)

    /** Opening and searching a megabyte of text, which is the whole of what a reader does. */
    @Test
    fun aMegabyteOfTextOpensAndIsSearchable() {
        val activity = launchApp()
        val (file, lines) = write("large-text.txt", 1_000_000)

        val start = SystemClock.elapsedRealtime()

        val fragment = loadDocument(activity, file)
        val pageView = checkNotNull(fragment.pageView) { "no page view" }

        // the loader callback only means the html was published; the page still has to lay out,
        // and a js round trip is what has to queue behind that
        val elements = awaitStableDom(pageView)
        Assert.assertTrue("the page never laid out", elements > 1)

        // and then the text itself. A megabyte is parsed in bursts, so the element count can stop
        // changing inside a pause rather than at the end - two equal readings both landing in one
        // is a page still filling, which the search then ran against and found nothing in
        var needles = -1
        Assert.assertTrue(
            "the text never finished arriving - $NEEDLE is in the page $needles times, not $lines",
            waitFor(TIMEOUT_MS) { needlesInDom(pageView).also { needles = it } == lines },
        )

        val matches = findAll(pageView, NEEDLE)
        val elapsed = SystemClock.elapsedRealtime() - start

        Assert.assertEquals("the search did not find every line", lines, matches)
        Assert.assertTrue(
            "opening and searching $lines lines took ${elapsed}ms, over the ${BUDGET_MS}ms budget",
            elapsed < BUDGET_MS,
        )
    }

    /**
     * Prose is not a table. A comma between clauses is a consistent two field split, so text like
     * this used to be typed as csv and rendered as a spreadsheet.
     */
    @Test
    fun proseWithCommasStaysText() {
        val activity = launchApp()
        val (file, _) = write("prose.txt", 50_000, separator = ",")

        val fragment = loadDocument(activity, file)

        Assert.assertEquals("prose was read as a table", "text/plain", fragment.lastFileType)
    }

    private fun awaitStableDom(pageView: PageView): Int {
        var previous = -1

        waitFor(TIMEOUT_MS) {
            val current = elementCount(pageView)
            val stable = current > 1 && current == previous
            previous = current
            stable
        }

        return previous
    }

    /** Every line carries the needle once, so the page is all there when they all are. */
    private fun needlesInDom(pageView: PageView): Int =
        evaluateJavascript(
                pageView,
                "(document.body.textContent.match(/$NEEDLE/g) || []).length",
            )
            ?.toIntOrNull() ?: -1

    private fun elementCount(pageView: PageView): Int =
        evaluateJavascript(pageView, "document.getElementsByTagName('*').length")?.toIntOrNull()
            ?: -1

    private fun findAll(pageView: PageView, needle: String): Int {
        val latch = CountDownLatch(1)
        val found = AtomicInteger(-1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pageView.setFindListener { _, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    found.set(numberOfMatches)
                    latch.countDown()
                }
            }
            pageView.findAllAsync(needle)
        }

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)

        return found.get()
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
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return null
        }
        return result.get()
    }

    /**
     * A freshly installed app has never run, so its cache directory - where a document has to be
     * for the app's own FileProvider to hand it back - is only there once the process has started.
     */
    private fun launchApp(): MainActivity = mainActivityActivityTestRule.launchActivity(null)

    private fun loadDocument(activity: MainActivity, file: File): DocumentFragment {
        val uri = uriOf(file)

        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.loadUri(uri) }

        val fragment = checkNotNull(waitForFragment(activity)) { "no document fragment" }
        Assert.assertTrue(
            "${file.name} never loaded",
            waitFor(TIMEOUT_MS) { fragment.lastDocument?.request?.uri == uri },
        )

        return fragment
    }

    private fun waitForFragment(activity: MainActivity): DocumentFragment? {
        var fragment: DocumentFragment? = null
        waitFor(TIMEOUT_MS) {
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
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() - startMs < timeoutMs)
        return false
    }

    private fun uriOf(file: File): Uri {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

        return FileProvider.getUriForFile(appCtx, appCtx.packageName + ".provider", file)
    }

    private companion object {
        /** A word once per line, so a search has to reach the end of the file. */
        const val NEEDLE = "Ceterum"

        /**
         * Opening and searching a megabyte, generously: a cold emulator is slower than a phone and
         * this is not a benchmark, only a ceiling a broken render cannot fit under.
         */
        const val BUDGET_MS = 15000L

        const val TIMEOUT_MS = 120000L

        /**
         * Generated rather than checked in: a megabyte of filler is not worth a git object, and
         * nothing here depends on which words it is.
         */
        fun write(name: String, bytes: Int, separator: String = ""): Pair<File, Int> {
            val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(appCtx.cacheDir, name)

            val line =
                "$NEEDLE censeo Carthaginem esse delendam$separator lorem ipsum dolor sit amet " +
                    "consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore.\n"

            var lines = 0
            file.bufferedWriter().use { writer ->
                var written = 0
                while (written < bytes) {
                    val text = "[$lines] $line"
                    writer.write(text)
                    written += text.length
                    lines++
                }
            }

            return file to lines
        }
    }
}
