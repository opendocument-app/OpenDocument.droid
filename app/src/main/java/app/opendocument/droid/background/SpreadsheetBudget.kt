package app.opendocument.droid.background

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * How much of a sheet is translated to html.
 *
 * The budget is the *WebView's*, not the core's. Every cell translated becomes a `<td>` the browser
 * engine then holds: measured on two devices, a rendered cell costs 10-20 KB in the WebView's
 * renderer process against some 226 bytes of html, so a sheet the core writes in a second can be
 * one no phone can lay out. A budget too high does not show more of the document - the page fails
 * to load and the user is told the file could not be opened.
 *
 * Hence the ladder below, and hence its steps being small next to what the core would allow.
 */
object SpreadsheetBudget {

    /**
     * The furthest a sheet is followed, before [cells] narrows it further. Bounds the two
     * directions on their own, so one very long or very wide sheet cannot spend the whole budget in
     * a direction nothing can scroll to.
     */
    const val ROWS = 100000
    const val COLUMNS = 500

    /** What the device this is running on can afford to show. */
    fun cells(context: Context): Long {
        val activityManager = context.getSystemService<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        return cellsFor(memoryInfo.totalMem, activityManager?.isLowRamDevice == true)
    }

    /**
     * The device's memory decides, not its api level or its screen: what is being budgeted is the
     * renderer process, which is killed by the same low memory killer as everything else.
     *
     * [totalMemoryBytes] of zero is what a device that would not answer looks like, and takes the
     * smallest step.
     */
    fun cellsFor(totalMemoryBytes: Long, isLowRamDevice: Boolean): Long {
        if (isLowRamDevice || totalMemoryBytes < 3L * GIGABYTE) {
            return 50000
        }

        if (totalMemoryBytes < 6L * GIGABYTE) {
            return 100000
        }

        return 150000
    }

    private const val GIGABYTE = 1024L * 1024L * 1024L
}
