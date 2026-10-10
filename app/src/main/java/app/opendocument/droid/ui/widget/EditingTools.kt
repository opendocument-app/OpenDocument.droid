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
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import app.opendocument.droid.R
import org.json.JSONObject

/**
 * Formatting and annotation tools below the edit bar. Long-press opens colors; page callbacks
 * update selection. Locked editions offer only [FREE_TOOLS].
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
    }

    var listener: Listener? = null

    private val row: LinearLayout

    /** Whether the tools only offer pro, rather than doing anything - see [showFormatting]. */
    private var locked = false

    private val toggles = mutableMapOf<String, View>()
    private val markTools = mutableMapOf<String, View>()

    /** What each tool applies, which is what its bar shows. Kept as the document is edited. */
    private val toolColors = STARTING_COLORS.toMutableMap()

    /** The highlighter of a document, or the fill of a sheet, named by [groundKey]. */
    private var groundTool: View? = null
    private var groundKey = HIGHLIGHT_COLOR
    private var sizeTool: View? = null
    private var alignTool: View? = null

    /** The alignments [alignTool] offers: a cell has no `justify`. */
    private var alignments = ALIGNMENTS

    /** The size the selection is in, in points, or null where the runs disagree. */
    private var selectionSize: String? = null

    /** How the selection is aligned, or null where the paragraphs or cells disagree. */
    private var selectionAlign: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_editing_tools, this, true)

        row = findViewById(R.id.editing_tools_row)

        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
    }

    fun hide() {
        visibility = View.GONE
    }

    /**
     * The formatting tools. A [sheet] takes a fill where a document takes a highlight, and no
     * `justify`. [locked] adds pro's badge, and makes every tool but the free one offer pro.
     */
    fun showFormatting(sheet: Boolean, locked: Boolean) {
        reset(locked)

        addBadge()

        val firstTool = row.childCount

        addToggle("bold", R.drawable.ic_format_bold, R.string.tool_bold)
        addToggle("italic", R.drawable.ic_format_italic, R.string.tool_italic)
        addToggle("underline", R.drawable.ic_format_underlined, R.string.tool_underline)
        addToggle("strikethrough", R.drawable.ic_format_strikethrough, R.string.tool_strikethrough)

        // the tool has nothing to turn off, so a tap is the colours themselves; the long press
        // opens them too, because that is where every other tool keeps its colours
        val textColor = newTool(R.drawable.ic_text_color, R.string.tool_text_color, TEXT_COLOR)
        paintBar(textColor, TEXT_COLOR)
        textColor.setOnClickListener { ifOffered(TEXT_COLOR) { pickTextColor(it) } }
        addColorPress(textColor, TEXT_COLOR, R.string.tool_text_color) { pickTextColor(it) }
        row.addView(textColor)

        // a tap flips the highlight or the fill on the selection, in the colour the long press
        // picked
        groundKey = if (sheet) FILL_COLOR else HIGHLIGHT_COLOR
        val groundLabel = if (sheet) R.string.tool_fill else R.string.tool_highlight
        val ground =
            newTool(
                if (sheet) R.drawable.ic_format_color_fill else R.drawable.ic_highlight,
                groundLabel,
                groundKey,
            )
        paintBar(ground, groundKey)
        ground.setOnClickListener {
            ifOffered(groundKey) {
                listener?.onFormat(
                    JSONObject()
                        .put(
                            groundKey,
                            if (ground.isSelected) JSONObject.NULL else hex(colorOf(groundKey)),
                        )
                )
            }
        }
        addColorPress(ground, groundKey, groundLabel) { anchor ->
            showPalette(anchor, if (sheet) FILL_COLORS else HIGHLIGHT_COLORS) { color ->
                if (color == Color.TRANSPARENT) {
                    listener?.onFormat(JSONObject().put(groundKey, JSONObject.NULL))

                    return@showPalette
                }

                setToolColor(groundKey, ground, color)

                listener?.onFormat(JSONObject().put(groundKey, hex(color)))
            }
        }
        groundTool = ground
        // a locked strip leads with its free tool, so a narrow screen shows it without scrolling
        row.addView(ground, if (locked) firstTool else row.childCount)

        val size = newTool(R.drawable.ic_format_size, R.string.tool_font_size, SIZE_TOOL)
        size.setOnClickListener { ifOffered(SIZE_TOOL) { showSizes(it) } }
        sizeTool = size
        row.addView(size)

        alignments = if (sheet) ALIGNMENTS.filter { it.value != "justify" } else ALIGNMENTS
        val align = newTool(R.drawable.ic_format_align_left, R.string.tool_align, ALIGN_TOOL)
        align.setOnClickListener { ifOffered(ALIGN_TOOL) { showAlignments(it) } }
        alignTool = align
        row.addView(align)

        setSelectionStyle(JSONObject())

        visibility = View.VISIBLE
    }

    /** The five marking tools of a pdf, each with a colour of its own. */
    fun showMarking(locked: Boolean) {
        reset(locked)

        addBadge()

        for (mark in MARKS) {
            val tool = newTool(mark.icon, mark.label, mark.tool)
            paintBar(tool, mark.tool)
            tool.setOnClickListener {
                ifOffered(mark.tool) {
                    listener?.onMarkTool(mark.tool, colorOf(mark.tool), false)
                }
            }
            addColorPress(tool, mark.tool, mark.label) { anchor ->
                showPalette(anchor, MARK_COLORS) { picked ->
                    setToolColor(mark.tool, tool, picked)

                    listener?.onMarkTool(mark.tool, picked, true)
                }
            }

            markTools[mark.tool] = tool
            row.addView(tool)
        }

        visibility = View.VISIBLE
    }

    /** Pro's badge, in front of a locked row, saying whose the dimmed tools are. */
    private fun addBadge() {
        if (!locked) {
            return
        }

        val badge =
            LayoutInflater.from(context).inflate(R.layout.item_editing_tool_badge, row, false)
                as TextView
        badge.setText(R.string.tool_pro_badge)
        badge.isSelected = true
        badge.setOnClickListener { listener?.onLocked() }
        row.addView(badge)
    }

    /** Shows which of the toggles the selection has on, and the size it is set in. */
    fun setSelectionStyle(style: JSONObject) {
        for ((property, view) in toggles) {
            view.isSelected = !locked && style.optBoolean(property, false)
        }

        // isNull is also true of a key the page left out, where the runs disagree
        // the free tool works when locked, so it shows what it would take off
        groundTool?.isSelected = !isPro(groundKey) && !style.isNull(groundKey)

        // the caption is the size the text is in; the icon alone means the runs disagree
        selectionSize =
            style.optString("size", "").removeSuffix("pt").takeIf {
                it.isNotEmpty() && !style.isNull("size")
            }

        sizeTool?.let { tool ->
            captionOf(
                tool,
                selectionSize?.let { context.getString(R.string.tool_font_size_points, it) },
            )
        }

        // the icon is the alignment the selection is in; left where they disagree
        selectionAlign = style.optString("align", "").takeIf { !style.isNull("align") }
        alignTool
            ?.findViewById<ImageView>(R.id.editing_tool_icon)
            ?.setImageResource(
                (alignments.find { it.value == selectionAlign } ?: alignments.first()).icon
            )
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
        groundTool = null
        sizeTool = null
        alignTool = null

        scrollTo(0, 0)
    }

    /** Whether [tool] only offers pro in this build, rather than doing its work. */
    private fun isPro(tool: String) = locked && tool !in FREE_TOOLS

    private fun ifOffered(tool: String, action: () -> Unit) {
        if (isPro(tool)) {
            listener?.onLocked()
        } else {
            action()
        }
    }

    private fun pickTextColor(anchor: View) {
        showPalette(anchor, TEXT_COLORS) { color ->
            setToolColor(TEXT_COLOR, anchor, color)

            listener?.onFormat(JSONObject().put("color", hex(color)))
        }
    }

    private fun addToggle(property: String, @DrawableRes icon: Int, @StringRes label: Int) {
        val tool = newTool(icon, label, property)
        tool.setOnClickListener { ifOffered(property) { listener?.onToggleStyle(property) } }

        toggles[property] = tool
        row.addView(tool)
    }

    /**
     * Puts the colours of [tool] on its long press. That is where the name of a tool is otherwise
     * read out, so the screen reader is told what the press does instead.
     */
    private fun addColorPress(
        tool: View,
        name: String,
        @StringRes label: Int,
        open: (View) -> Unit,
    ) {
        val description = context.getString(R.string.tool_color_of, context.getString(label))

        tool.setOnLongClickListener {
            ifOffered(name) { open(tool) }

            true
        }

        ViewCompat.setAccessibilityDelegate(
            tool,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)

                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                            description,
                        )
                    )
                }
            },
        )
    }

    private fun newTool(@DrawableRes icon: Int, @StringRes label: Int, name: String): View {
        val tool = LayoutInflater.from(context).inflate(R.layout.item_editing_tool, row, false)

        tool.findViewById<ImageView>(R.id.editing_tool_icon).setImageResource(icon)
        tool.contentDescription = context.getString(label)

        // a tool that only offers pro is dimmed, so the free one is the one that stands out
        if (isPro(name)) {
            tool.alpha = PRO_ALPHA
        }

        // no label beside it, so the name is what a long press turns up - on the tools whose long
        // press is not the colours, see addColorPress
        TooltipCompat.setTooltipText(tool, context.getString(label))

        return tool
    }

    /** Shows [text] under the tool's icon, or nothing where it is null. */
    private fun captionOf(tool: View, text: String?) {
        val caption = tool.findViewById<TextView>(R.id.editing_tool_caption)

        caption.text = text.orEmpty()
        caption.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** What [name] applies, which starts at the first colour its palette offers. */
    @ColorInt private fun colorOf(name: String): Int = toolColors.getValue(name)

    private fun setToolColor(name: String, tool: View, @ColorInt color: Int) {
        toolColors[name] = color

        paintBar(tool, name)
    }

    /** Shows under [tool]'s icon what it applies, so that the bar is what a tap uses. */
    private fun paintBar(tool: View, name: String) {
        val bar = tool.findViewById<View>(R.id.editing_tool_bar)
        bar.visibility = View.VISIBLE

        (bar.background.mutate() as GradientDrawable).setColor(colorOf(name))
    }

    /**
     * The sizes, in one row under the strip rather than a menu down the screen: fourteen of them
     * would otherwise cover the document they are about.
     */
    private fun showSizes(anchor: View) {
        showRow(anchor, fill = true) { row, popup ->
            var current: View? = null

            for (size in FONT_SIZES) {
                val chip =
                    LayoutInflater.from(context)
                        .inflate(R.layout.item_editing_tool_size, row, false) as TextView
                chip.text = context.getString(R.string.tool_font_size_points, "$size")
                chip.isSelected = "$size" == selectionSize
                chip.setOnClickListener {
                    popup.dismiss()

                    listener?.onFormat(JSONObject().put("size", "${size}pt"))
                }

                if (chip.isSelected) {
                    current = chip
                }

                row.addView(chip)
            }

            // the size the text is in is what the reader is looking for, so start there - one
            // chip short of it, so that the smaller sizes before it are not hidden
            current?.let { chip ->
                row.post {
                    (row.parent as HorizontalScrollView).scrollTo(
                        (chip.left - chip.width).coerceAtLeast(0),
                        0,
                    )
                }
            }
        }
    }

    /** The alignments, as a row of tools under the one that opened them. */
    private fun showAlignments(anchor: View) {
        showRow(anchor, fill = false) { row, popup ->
            for (alignment in alignments) {
                val choice = newTool(alignment.icon, alignment.label, ALIGN_TOOL)
                // a choice carries no colour and no caption, so its icon is centred without the
                // slot
                (choice.findViewById<View>(R.id.editing_tool_caption).parent as View).visibility =
                    View.GONE
                choice.isSelected = alignment.value == selectionAlign
                choice.setOnClickListener {
                    popup.dismiss()

                    listener?.onFormat(JSONObject().put("align", alignment.value))
                }

                row.addView(choice)
            }
        }
    }

    private fun showPalette(anchor: View, colors: List<NamedColor>, picked: (Int) -> Unit) {
        showRow(anchor, fill = false) { row, popup ->
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (2 * resources.displayMetrics.density).toInt()

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

                row.addView(swatch)
            }
        }
    }

    /**
     * What a tool opens: a row of choices under it. [fill] takes the width of the screen, for a row
     * that is too long to stand under one tool.
     */
    private fun showRow(anchor: View, fill: Boolean, build: (LinearLayout, PopupWindow) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.view_editing_tool_popup, null)
        val row: LinearLayout = content.findViewById(R.id.editing_tool_popup_row)

        val popup =
            PopupWindow(
                content,
                if (fill) LinearLayout.LayoutParams.MATCH_PARENT
                else LinearLayout.LayoutParams.WRAP_CONTENT,
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

        build(row, popup)

        // a row that fills the screen hangs under the strip, not under the tool that opened it
        popup.showAsDropDown(if (fill) this else anchor)
    }

    @ColorInt
    private fun themeColor(attribute: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attribute, value, true)

        return value.data
    }

    private class NamedColor(@param:ColorInt val color: Int, @param:StringRes val name: Int)

    private class Alignment(
        val value: String,
        @param:DrawableRes val icon: Int,
        @param:StringRes val label: Int,
    )

    private class Mark(
        val tool: String,
        @param:DrawableRes val icon: Int,
        @param:StringRes val label: Int,
    )

    companion object {

        /** The width of a line the Draw tool makes, in pdf points. */
        const val INK_WIDTH = 2f

        /** The two formatting tools that carry a colour, named as [toolColors] keys. */
        private const val TEXT_COLOR = "color"
        private const val HIGHLIGHT_COLOR = "highlight"

        /** The colour behind a cell, which a sheet takes in place of a highlight. */
        private const val FILL_COLOR = "fill"

        /** The size and the alignment tools, which carry no colour and so are only names here. */
        private const val SIZE_TOOL = "size"
        private const val ALIGN_TOOL = "align"

        /**
         * The tools a locked strip still does the work of. The highlighter, under both names it
         * has: `highlight` is the formatting style and the pdf's marking tool alike, so a reader of
         * either kind of document has the same free tool. A sheet's fill stands in for it.
         */
        private val FREE_TOOLS = setOf(HIGHLIGHT_COLOR, FILL_COLOR)

        /** What a tool that only offers pro is drawn at, against the free one beside it. */
        private const val PRO_ALPHA = 0.45f

        /** `#rrggbb`, the one spelling `odr.editing.format` takes. */
        private fun hex(@ColorInt color: Int) = String.format("#%06x", color and 0xffffff)

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

        private val FILL_COLORS =
            HIGHLIGHT_COLORS.dropLast(1) + NamedColor(Color.TRANSPARENT, R.string.color_no_fill)

        /** In the values `odr.editing.format` takes for `align`. */
        private val ALIGNMENTS =
            listOf(
                Alignment("left", R.drawable.ic_format_align_left, R.string.tool_align_left),
                Alignment("center", R.drawable.ic_format_align_center, R.string.tool_align_center),
                Alignment("right", R.drawable.ic_format_align_right, R.string.tool_align_right),
                Alignment(
                    "justify",
                    R.drawable.ic_format_align_justify,
                    R.string.tool_align_justify,
                ),
            )

        private val MARK_COLORS =
            listOf(
                NamedColor(0xffffe633.toInt(), R.string.color_yellow),
                NamedColor(0xffe53935.toInt(), R.string.color_red),
                NamedColor(0xff1e88e5.toInt(), R.string.color_blue),
                NamedColor(0xff43a047.toInt(), R.string.color_green),
            )

        /** The marks a pdf takes, in the annotator's names. */
        private val MARKS =
            listOf(
                Mark("highlight", R.drawable.ic_highlight, R.string.tool_mark_highlight),
                Mark("underline", R.drawable.ic_format_underlined, R.string.tool_mark_underline),
                Mark(
                    "strikeOut",
                    R.drawable.ic_format_strikethrough,
                    R.string.tool_mark_strike_out,
                ),
                Mark("squiggly", R.drawable.ic_format_squiggly, R.string.tool_mark_squiggly),
                Mark("ink", R.drawable.ic_draw, R.string.tool_mark_draw),
            )

        /** The colour each tool starts with. */
        private val STARTING_COLORS =
            mapOf(
                TEXT_COLOR to TEXT_COLORS.first().color,
                HIGHLIGHT_COLOR to HIGHLIGHT_COLORS.first().color,
                FILL_COLOR to FILL_COLORS.first().color,
                "highlight" to 0xffffe633.toInt(),
                "underline" to 0xffe53935.toInt(),
                "strikeOut" to 0xffe53935.toInt(),
                "squiggly" to 0xffe53935.toInt(),
                "ink" to 0xff1e88e5.toInt(),
            )
    }
}
