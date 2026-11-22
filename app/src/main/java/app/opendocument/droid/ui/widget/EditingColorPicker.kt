package app.opendocument.droid.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import app.opendocument.droid.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** A shared, scrollable palette for text, highlighting and cell fill. */
internal class EditingColorPicker(private val context: Context) {
    private val preferences = context.getSharedPreferences("editing_colors", Context.MODE_PRIVATE)
    private var dialog: AlertDialog? = null

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun show(
        title: CharSequence,
        @StringRes clearLabel: Int?,
        documentColors: ((List<Int>) -> Unit) -> Unit,
        picked: (Int) -> Unit,
    ) {
        dismiss()
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(16), dp(8))
            }
        val scroll = ScrollView(context).apply { addView(content) }
        val builder =
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
        if (clearLabel != null) {
            builder.setNeutralButton(clearLabel) { _, _ -> picked(Color.TRANSPARENT) }
        }
        val popup = builder.create()
        dialog = popup
        val choose: (Int) -> Unit = { color ->
            val recent = recentColors(color)
            preferences.edit().putString("recent", recent.joinToString(",")).apply()
            popup.dismiss()
            picked(color)
        }
        val recent = recentColors()
        if (recent.isNotEmpty()) {
            addSection(content, R.string.color_recent, recent.map { it to hex(it) }, choose)
        }
        val documentSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(documentSection)
        addSection(content, R.string.color_palette, palette(), choose)
        popup.show()
        documentColors { colors ->
            if (popup.isShowing && colors.isNotEmpty()) {
                documentSection.removeAllViews()
                addSection(
                    documentSection,
                    R.string.color_document,
                    colors.distinct().take(32).map { it to hex(it) },
                    choose,
                )
            }
        }
    }

    private fun recentColors(newColor: Int? = null): List<Int> =
        (listOfNotNull(newColor) +
                preferences.getString("recent", "").orEmpty().split(',').mapNotNull {
                    it.toIntOrNull()
                })
            .filter { Color.alpha(it) == 255 }
            .distinct()
            .take(8)

    private fun addSection(
        parent: LinearLayout,
        @StringRes title: Int,
        colors: List<Pair<Int, String>>,
        choose: (Int) -> Unit,
    ) {
        parent.addView(
            TextView(context).apply {
                setText(title)
                setPadding(dp(4), dp(12), dp(4), dp(4))
                ViewCompat.setAccessibilityHeading(this, true)
            }
        )
        val grid = GridLayout(context)
        // Leave room for the dialog margins and section padding, including in split screen.
        grid.columnCount =
            ((context.resources.configuration.screenWidthDp - 80) / 48).coerceIn(1, 5)
        grid.layoutParams =
            LinearLayout.LayoutParams(
                    dp(48 * grid.columnCount),
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        for ((color, name) in colors) {
            val swatch =
                View(context).apply {
                    layoutParams =
                        GridLayout.LayoutParams().apply {
                            width = dp(48)
                            height = dp(48)
                        }
                    background =
                        android.graphics.drawable.InsetDrawable(
                            context.getDrawable(R.drawable.bg_color_swatch)!!.mutate().also {
                                (it as GradientDrawable).setColor(color)
                            },
                            dp(4),
                        )
                    contentDescription = name
                    TooltipCompat.setTooltipText(this, name)
                    isFocusable = true
                    setOnClickListener { choose(color) }
                }
            grid.addView(swatch)
        }
        parent.addView(grid)
    }

    private fun palette(): List<Pair<Int, String>> {
        val hues =
            listOf(
                R.string.color_gray to listOf(0xffffff, 0xbdbdbd, 0x757575, 0x424242, 0x191c1e),
                R.string.color_red to listOf(0xffcdd2, 0xef9a9a, 0xe53935, 0xc62828, 0x7f0000),
                R.string.color_orange to listOf(0xffe0b2, 0xffb74d, 0xfb8c00, 0xef6c00, 0x8a3800),
                R.string.color_yellow to listOf(0xfff59d, 0xffee58, 0xffeb3b, 0xc0a600, 0x756500),
                R.string.color_green to listOf(0xc5e1a5, 0xa5d6a7, 0x43a047, 0x2e7d32, 0x145218),
                R.string.color_blue to listOf(0xb3e5fc, 0x90caf9, 0x1e88e5, 0x1565c0, 0x0d3773),
                R.string.color_purple to listOf(0xe1bee7, 0xce93d8, 0x8e24aa, 0x6a1b9a, 0x38006b),
                R.string.color_pink to listOf(0xf8bbd0, 0xf48fb1, 0xd81b60, 0xad1457, 0x6a0032),
            )
        val shades =
            listOf(
                R.string.color_lightest,
                R.string.color_light,
                R.string.color_standard,
                R.string.color_dark,
                R.string.color_darkest,
            )
        return hues.flatMap { (name, values) ->
            shades.indices.map { shade ->
                val color = values[shade] or 0xff000000.toInt()
                val label =
                    when (color) {
                        Color.WHITE -> context.getString(R.string.color_white)
                        0xff191c1e.toInt() -> context.getString(R.string.color_black)
                        else -> context.getString(shades[shade], context.getString(name))
                    }
                color to "$label, ${hex(color)}"
            }
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun hex(color: Int) = String.format("#%06X", color and 0xffffff)
}
