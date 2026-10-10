package app.opendocument.droid.background

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Limits rendered spreadsheet cells to the WebView memory budget; each cell costs roughly 10–20 KB
 * in the renderer.
 */
object SpreadsheetBudget {

    /** Each direction on its own, before [cells] narrows the two together. */
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
     * Memory decides, since what is budgeted is the renderer process. [totalMemoryBytes] of zero is
     * a device that would not answer, and takes the smallest step.
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
