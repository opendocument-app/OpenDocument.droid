package app.opendocument.droid.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import app.opendocument.droid.R
import org.json.JSONObject

/**
 * The tools under the edit mode's bar, with undo and redo at the end. It only reports taps; which
 * tool is on comes back from the page through [setSelectionStyle] and [setArmedTool].
 */
class EditingTools(context: Context, attributeSet: AttributeSet?) :
    HorizontalScrollView(context, attributeSet) {

    interface Listener {

        /** Flip `bold`, `italic`, `underline` or `strikethrough` on the selection. */
        fun onToggleStyle(property: String)

        /** State [style] on the selection, in the keys `odr.editing.format` takes. */
        fun onFormat(style: JSONObject)

        /** A marking tool was pressed, or given a new [color] where [recolor]. */
        fun onMarkTool(tool: String, @ColorInt color: Int, recolor: Boolean)

        /** A tool of pro's was tapped in a build without it. */
        fun onLocked()

        fun onUndo()

        fun onRedo()
    }

    var listener: Listener? = null

    private val row: LinearLayout

    /** Whether the tools only offer pro, rather than doing anything - see [showFormatting]. */
    private var locked = false

    private val toggles = mutableMapOf<String, View>()
    private val markTools = mutableMapOf<String, View>()
    private val markColors = mutableMapOf<String, Int>()

    private var textColor = TEXT_COLORS.first().color
    private var highlightColor = HIGHLIGHT_COLORS.first().color

    /** What the selection shows, as the page last reported it. */
    private var selectionStyle = JSONObject()

    private var textColorBar: View? = null
    private var highlightTool: View? = null
    private var highlightBar: View? = null
    private var sizeTool: TextView? = null
    private var undoTool: View? = null
    private var redoTool: View? = null

    private var canUndo = false
    private var canRedo = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_editing_tools, this, true)

        row = findViewById(R.id.editing_tools_row)

        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
    }

    fun hide() {
        visibility = View.GONE
    }

    /** The formatting tools. [locked] adds pro's badge, and makes each tool offer pro. */
    fun showFormatting(locked: Boolean) {
        reset(locked)

        if (locked) {
            val badge = newText(R.string.tool_pro_badge)
            badge.isSelected = true
            row.addView(badge)
        }

        addToggle("bold", R.drawable.ic_format_bold, R.string.tool_bold)
        addToggle("italic", R.drawable.ic_format_italic, R.string.tool_italic)
        addToggle("underline", R.drawable.ic_format_underlined, R.string.tool_underline)
        addToggle(
            "strikethrough",
            R.drawable.ic_format_strikethrough,
            R.string.tool_strikethrough,
        )

        // one control: the colors open under it, and the bar shows the selection's own
        val textColorTool = newTool(R.drawable.ic_text_color, R.string.tool_text_color)
        textColorBar = barOf(textColorTool).also { paintBar(it, textColor) }
        textColorTool.setOnClickListener { anchor ->
            ifUnlocked {
                showPalette(anchor, TEXT_COLORS) { color ->
                    listener?.onFormat(JSONObject().put("color", hex(color)))
                }
            }
        }
        row.addView(textColorTool)

        // a split button: the tool turns the highlight on and off, the arrow picks its colour
        val highlight = newTool(R.drawable.ic_marker, R.string.tool_highlight)
        highlightBar = barOf(highlight).also { paintBar(it, highlightColor) }
        highlight.setOnClickListener {
            ifUnlocked {
                // isNull is also true of a key the page left out, where the runs disagree
                val on = !selectionStyle.isNull("highlight")

                listener?.onFormat(
                    JSONObject().put("highlight", if (on) JSONObject.NULL else hex(highlightColor))
                )
            }
        }
        highlightTool = highlight
        row.addView(highlight)
        addChevron(R.string.tool_highlight) { anchor ->
            showPalette(anchor, HIGHLIGHT_COLORS) { color ->
                if (color == Color.TRANSPARENT) {
                    listener?.onFormat(JSONObject().put("highlight", JSONObject.NULL))

                    return@showPalette
                }

                highlightColor = color
                highlightBar?.let { paintBar(it, color) }

                listener?.onFormat(JSONObject().put("highlight", hex(color)))
            }
        }

        val size = newText(R.string.tool_font_size)
        size.contentDescription = context.getString(R.string.tool_font_size)
        TooltipCompat.setTooltipText(size, context.getString(R.string.tool_font_size))
        size.setOnClickListener { ifUnlocked { showSizes(size) } }
        sizeTool = size
        row.addView(size)

        addUndoRedo(redo = true)

        setSelectionStyle(selectionStyle)

        visibility = View.VISIBLE
    }

    /** A sheet or a plain text file: nothing to format, so only the way back. */
    fun showPlain() {
        reset(false)

        addUndoRedo(redo = true)

        visibility = View.VISIBLE
    }

    /** The five marking tools of a pdf, each with a colour of its own. */
    fun showMarking() {
        reset(false)

        for (mark in MARKS) {
            val color = markColors.getOrPut(mark.tool) { mark.color }

            val tool = newTool(mark.icon, mark.label)
            paintBar(barOf(tool), color)
            tool.setOnClickListener {
                listener?.onMarkTool(mark.tool, markColors.getValue(mark.tool), false)
            }
            markTools[mark.tool] = tool
            row.addView(tool)

            addChevron(mark.label) { anchor ->
                showPalette(anchor, MARK_COLORS) { picked ->
                    markColors[mark.tool] = picked
                    paintBar(barOf(tool), picked)

                    listener?.onMarkTool(mark.tool, picked, true)
                }
            }
        }

        // a mark is taken back one at a time and never put back
        addUndoRedo(redo = false)

        visibility = View.VISIBLE
    }

    /** What the page says can be taken back and put back. */
    fun setUndoState(canUndo: Boolean, canRedo: Boolean) {
        this.canUndo = canUndo
        this.canRedo = canRedo

        undoTool?.let { setUsable(it, canUndo) }
        redoTool?.let { setUsable(it, canRedo) }
    }

    /** Undo and redo are the page's in every edition, so they are never locked. */
    private fun addUndoRedo(redo: Boolean) {
        val undo = newTool(R.drawable.ic_undo, R.string.action_undo)
        undo.setOnClickListener { listener?.onUndo() }
        undoTool = undo
        row.addView(undo)

        if (redo) {
            val tool = newTool(R.drawable.ic_redo, R.string.action_redo)
            tool.setOnClickListener { listener?.onRedo() }
            redoTool = tool
            row.addView(tool)
        }

        setUndoState(canUndo, canRedo)
    }

    private fun setUsable(tool: View, usable: Boolean) {
        tool.isEnabled = usable
        tool.alpha = if (usable) 1f else DISABLED_ALPHA
    }

    /** Shows which of the toggles the selection has on, and the size it is set in. */
    fun setSelectionStyle(style: JSONObject) {
        selectionStyle = style

        for ((property, view) in toggles) {
            view.isSelected = !locked && style.optBoolean(property, false)
        }

        highlightTool?.isSelected = !locked && !style.isNull("highlight")

        // where the runs disagree, the bars keep what they showed
        style
            .optString("color")
            .takeIf { !style.isNull("color") }
            ?.let { parseColor(it) }
            ?.let { color ->
                textColorBar?.let { paintBar(it, color) }
            }
        style
            .optString("highlight")
            .takeIf { !style.isNull("highlight") }
            ?.let { parseColor(it) }
            ?.let { color ->
                highlightColor = color
                highlightBar?.let { paintBar(it, color) }
            }

        sizeTool?.text =
            style
                .optString("size", "")
                .removeSuffix("pt")
                .takeIf { it.isNotEmpty() && !style.isNull("size") }
                ?.let { context.getString(R.string.tool_font_size_points, it) }
                ?: context.getString(R.string.tool_font_size)
    }

    /** Shows which marking tool is armed, or none. */
    fun setArmedTool(tool: String?) {
        for ((name, view) in markTools) {
            view.isSelected = name == tool
        }
    }

    private fun reset(locked: Boolean) {
        this.locked = locked

        row.removeAllViews()
        toggles.clear()
        markTools.clear()
        textColorBar = null
        highlightTool = null
        highlightBar = null
        sizeTool = null
        undoTool = null
        redoTool = null
        selectionStyle = JSONObject()

        scrollTo(0, 0)
    }

    private fun ifUnlocked(action: () -> Unit) {
        if (locked) {
            listener?.onLocked()
        } else {
            action()
        }
    }

    private fun addToggle(property: String, @DrawableRes icon: Int, @StringRes label: Int) {
        val tool = newTool(icon, label)
        tool.setOnClickListener { ifUnlocked { listener?.onToggleStyle(property) } }

        toggles[property] = tool
        row.addView(tool)
    }

    private fun addChevron(@StringRes label: Int, open: (View) -> Unit) {
        val chevron =
            LayoutInflater.from(context).inflate(R.layout.item_editing_tool_chevron, row, false)

        val description = context.getString(R.string.tool_color_of, context.getString(label))
        chevron.contentDescription = description
        TooltipCompat.setTooltipText(chevron, description)

        chevron.setOnClickListener { ifUnlocked { open(it) } }

        row.addView(chevron)
    }

    private fun newTool(@DrawableRes icon: Int, @StringRes label: Int): View {
        val tool = LayoutInflater.from(context).inflate(R.layout.item_editing_tool, row, false)

        tool.findViewById<ImageView>(R.id.editing_tool_icon).setImageResource(icon)
        tool.contentDescription = context.getString(label)

        // no label beside it, so the name is what a long press turns up
        TooltipCompat.setTooltipText(tool, context.getString(label))

        return tool
    }

    private fun newText(@StringRes text: Int): TextView {
        val view =
            LayoutInflater.from(context).inflate(R.layout.item_editing_tool_text, row, false)
                as TextView
        view.setText(text)

        if (text == R.string.tool_pro_badge) {
            view.setOnClickListener { listener?.onLocked() }
        }

        return view
    }

    private fun barOf(tool: View): View =
        tool.findViewById<View>(R.id.editing_tool_bar).also { it.visibility = View.VISIBLE }

    private fun paintBar(bar: View, @ColorInt color: Int) {
        (bar.background.mutate() as GradientDrawable).setColor(color)
    }

    private fun showSizes(anchor: View) {
        val popup = PopupMenu(context, anchor)
        for ((index, size) in FONT_SIZES.withIndex()) {
            popup.menu.add(
                0,
                index,
                index,
                context.getString(R.string.tool_font_size_points, "$size"),
            )
        }
        popup.setOnMenuItemClickListener { item ->
            listener?.onFormat(JSONObject().put("size", "${FONT_SIZES[item.itemId]}pt"))

            true
        }
        popup.show()
    }

    private fun showPalette(anchor: View, colors: List<NamedColor>, picked: (Int) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.view_color_palette, null)
        val paletteRow: LinearLayout = content.findViewById(R.id.color_palette_row)

        val popup =
            PopupWindow(
                content,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true,
            )
        popup.elevation = 8 * resources.displayMetrics.density
        popup.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
                cornerRadius = 12 * resources.displayMetrics.density
            }
        )

        val size = (36 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        for (named in colors) {
            val swatch = View(context)
            swatch.layoutParams =
                LinearLayout.LayoutParams(size, size).apply { setMargins(margin, 0, margin, 0) }
            swatch.background =
                context.getDrawable(R.drawable.bg_color_swatch)!!.mutate().also {
                    // no fill at all is the swatch for no highlight
                    (it as GradientDrawable).setColor(named.color)
                }
            swatch.contentDescription = context.getString(named.name)
            TooltipCompat.setTooltipText(swatch, context.getString(named.name))
            swatch.isClickable = true
            swatch.isFocusable = true
            swatch.setOnClickListener {
                popup.dismiss()

                picked(named.color)
            }

            paletteRow.addView(swatch)
        }

        popup.showAsDropDown(anchor)
    }

    @ColorInt
    private fun themeColor(attribute: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attribute, value, true)

        return value.data
    }

    private class NamedColor(@param:ColorInt val color: Int, @param:StringRes val name: Int)

    private class Mark(
        val tool: String,
        @param:DrawableRes val icon: Int,
        @param:StringRes val label: Int,
        @param:ColorInt val color: Int,
    )

    companion object {

        /** The width of a line the Draw tool makes, in pdf points. */
        const val INK_WIDTH = 2f

        /** `#rrggbb`, the one spelling `odr.editing.format` takes. */
        private fun hex(@ColorInt color: Int) = String.format("#%06x", color and 0xffffff)

        private fun parseColor(hex: String): Int? =
            try {
                Color.parseColor(hex)
            } catch (e: IllegalArgumentException) {
                null
            }

        /** Material's opacity for a disabled icon, 38%. */
        private const val DISABLED_ALPHA = 0.38f

        /** Point sizes a document commonly uses. */
        private val FONT_SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48)

        // the same colors as OpenDocument.ios' EditToolBar; the first of each is the default
        private val TEXT_COLORS =
            listOf(
                NamedColor(0xff191c1e.toInt(), R.string.color_black),
                NamedColor(0xffe53935.toInt(), R.string.color_red),
                NamedColor(0xff1e88e5.toInt(), R.string.color_blue),
                NamedColor(0xff43a047.toInt(), R.string.color_green),
            )

        private val HIGHLIGHT_COLORS =
            listOf(
                NamedColor(0xfffff59d.toInt(), R.string.color_yellow),
                NamedColor(0xffc5e1a5.toInt(), R.string.color_green),
                NamedColor(0xfff8bbd0.toInt(), R.string.color_pink),
                NamedColor(0xffb3e5fc.toInt(), R.string.color_blue),
                NamedColor(Color.TRANSPARENT, R.string.color_none),
            )

        private val MARK_COLORS =
            listOf(
                NamedColor(0xffffe633.toInt(), R.string.color_yellow),
                NamedColor(0xffe53935.toInt(), R.string.color_red),
                NamedColor(0xff1e88e5.toInt(), R.string.color_blue),
                NamedColor(0xff43a047.toInt(), R.string.color_green),
            )

        /** The marks a pdf takes, in the annotator's names, each with its starting colour. */
        private val MARKS =
            listOf(
                Mark(
                    "highlight",
                    R.drawable.ic_marker,
                    R.string.tool_mark_highlight,
                    0xffffe633.toInt(),
                ),
                Mark(
                    "underline",
                    R.drawable.ic_format_underlined,
                    R.string.tool_mark_underline,
                    0xffe53935.toInt(),
                ),
                Mark(
                    "strikeOut",
                    R.drawable.ic_format_strikethrough,
                    R.string.tool_mark_strike_out,
                    0xffe53935.toInt(),
                ),
                Mark(
                    "squiggly",
                    R.drawable.ic_format_squiggly,
                    R.string.tool_mark_squiggly,
                    0xffe53935.toInt(),
                ),
                Mark("ink", R.drawable.ic_draw, R.string.tool_mark_draw, 0xff1e88e5.toInt()),
            )
    }
}
