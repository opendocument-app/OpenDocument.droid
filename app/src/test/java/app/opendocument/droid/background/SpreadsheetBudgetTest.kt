package app.opendocument.droid.background

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The device's memory is passed into [SpreadsheetBudget.cellsFor], so every step is reachable
 * without the phones it stands for.
 */
class SpreadsheetBudgetTest {

    @Test
    fun aSmallDeviceGetsTheSmallestBudget() {
        assertEquals(50000L, cellsFor(gigabytes = 2))
        assertEquals(50000L, cellsFor(gigabytes = 8, isLowRamDevice = true))
    }

    @Test
    fun anOrdinaryPhoneGetsTheMiddleOne() {
        assertEquals(100000L, cellsFor(gigabytes = 4))
    }

    @Test
    fun aLargeDeviceGetsTheLargest() {
        assertEquals(150000L, cellsFor(gigabytes = 8))
        assertEquals(150000L, cellsFor(gigabytes = 16))
    }

    /** A device that would not say how much memory it has is treated as one that has little. */
    @Test
    fun anUnansweredDeviceGetsTheSmallestBudget() {
        assertEquals(50000L, cellsFor(gigabytes = 0))
    }

    /** The budget only ever grows with the memory: a step in the wrong place would show less. */
    @Test
    fun theLadderNeverFalls() {
        var previous = 0L

        for (gigabytes in 0..24) {
            val cells = cellsFor(gigabytes)

            assert(cells >= previous) { "$gigabytes GB gets $cells, less than the device below it" }

            previous = cells
        }
    }

    private fun cellsFor(gigabytes: Int, isLowRamDevice: Boolean = false): Long =
        SpreadsheetBudget.cellsFor(gigabytes * 1024L * 1024L * 1024L, isLowRamDevice)
}
