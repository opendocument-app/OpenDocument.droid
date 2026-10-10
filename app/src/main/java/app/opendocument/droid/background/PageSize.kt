package app.opendocument.droid.background

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A page in thousandths of an inch, what [android.print.PrintAttributes.MediaSize] measures in. */
class PageSize(val widthMils: Int, val heightMils: Int) {

    val isLandscape: Boolean
        get() = widthMils > heightMils

    /** The standard paper this page is cut for, or null for a size of its own. */
    fun paper(): Paper? {
        val short = min(widthMils, heightMils)
        val long = max(widthMils, heightMils)
        return Paper.entries.firstOrNull {
            abs(short - it.shortMils) <= it.shortMils * TOLERANCE &&
                abs(long - it.longMils) <= it.longMils * TOLERANCE
        }
    }

    companion object {
        /** Close enough for a rounded A4, too far apart for A4 and Letter. */
        private const val TOLERANCE = 0.02

        /** Null where either side is missing or in a unit that is not a length. */
        fun of(
            width: Double?,
            widthUnit: String?,
            height: Double?,
            heightUnit: String?,
        ): PageSize? {
            val widthMils = mils(width, widthUnit) ?: return null
            val heightMils = mils(height, heightUnit) ?: return null
            return PageSize(widthMils, heightMils)
        }

        private fun mils(magnitude: Double?, unit: String?): Int? {
            val perUnit =
                when (unit) {
                    "in" -> 1000.0
                    "cm" -> 1000.0 / 2.54
                    "mm" -> 1000.0 / 25.4
                    "pt" -> 1000.0 / 72
                    "pc" -> 1000.0 / 6
                    "px" -> 1000.0 / 96
                    else -> return null
                }
            return magnitude?.let { (it * perUnit).roundToInt() }?.takeIf { it > 0 }
        }
    }
}

/** The papers a print service offers by name, portrait. */
enum class Paper(val shortMils: Int, val longMils: Int) {
    ISO_A3(11693, 16535),
    ISO_A4(8268, 11693),
    ISO_A5(5827, 8268),
    NA_LETTER(8500, 11000),
    NA_LEGAL(8500, 14000),
    NA_TABLOID(11000, 17000),
}
