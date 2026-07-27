package app.opendocument.droid.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.opendocument.droid.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * What can be done with the open document, as buttons over the bottom right corner of it: one for
 * the action worth its own button, and one that unfolds the rest.
 *
 * This is what the toolbar menu used to be. A document is read with the phone in one hand, and the
 * top right corner of a modern screen is the one place a thumb cannot reach - so the actions sit
 * where the thumb already is, and the ones that were hidden behind "More options" now say what they
 * are.
 *
 * Material ships no speed dial component (the one it had was never brought over to Material 3), so
 * the rows are built here from [R.layout.item_document_action].
 */
class DocumentActions(context: Context, attributeSet: AttributeSet?) :
    FrameLayout(context, attributeSet) {

    /** One button, identified by one of the ACTION_ ids the caller gets back when it is tapped. */
    class Action(val id: Int, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

    fun interface Listener {
        fun onDocumentActionClicked(id: Int)
    }

    var listener: Listener? = null

    /** Told whenever the actions unfold or fold back up, so back can close them. */
    var expandedListener: ((Boolean) -> Unit)? = null

    private val scrim: View
    private val rowsScroll: ScrollView
    private val rows: LinearLayout
    private val primaryButton: FloatingActionButton
    private val moreButton: FloatingActionButton

    var isExpanded: Boolean = false
        private set

    init {
        LayoutInflater.from(context).inflate(R.layout.view_document_actions, this, true)

        scrim = findViewById(R.id.document_actions_scrim)
        rowsScroll = findViewById(R.id.document_actions_rows_scroll)
        rows = findViewById(R.id.document_actions_rows)
        primaryButton = findViewById(R.id.document_actions_primary)
        moreButton = findViewById(R.id.document_actions_more)

        scrim.setOnClickListener { collapse() }
        moreButton.setOnClickListener { if (isExpanded) collapse() else expand() }

        setActions(null, emptyList())
    }

    /**
     * What the document can do right now. [primary] gets a button of its own, the rest unfold out
     * of the second one, the first of them closest to it - so the order is most wanted first.
     *
     * Nothing and an empty list take the buttons away entirely, which is what a document that
     * failed to load leaves behind.
     */
    fun setActions(primary: Action?, unfolding: List<Action>) {
        collapse()

        if (primary == null) {
            primaryButton.visibility = View.GONE
        } else {
            primaryButton.visibility = View.VISIBLE
            primaryButton.setImageResource(primary.icon)
            primaryButton.contentDescription = context.getString(primary.label)
            primaryButton.setOnClickListener { listener?.onDocumentActionClicked(primary.id) }
        }

        rows.removeAllViews()

        // added back to front, because the row added last is the one that ends up at the bottom
        // of the column, right above the button they unfold from
        for (action in unfolding.asReversed()) {
            rows.addView(newRow(action))
        }

        moreButton.visibility = if (unfolding.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun newRow(action: Action): View {
        val row = LayoutInflater.from(context).inflate(R.layout.item_document_action, rows, false)

        val label: TextView = row.findViewById(R.id.document_action_label)
        label.setText(action.label)

        val button: FloatingActionButton = row.findViewById(R.id.document_action_button)
        button.setImageResource(action.icon)
        button.contentDescription = context.getString(action.label)

        // the button is only the visible half of the row: the label is as much of a target
        val click = OnClickListener {
            collapse()

            listener?.onDocumentActionClicked(action.id)
        }
        row.setOnClickListener(click)
        button.setOnClickListener(click)

        return row
    }

    private fun expand() {
        if (isExpanded || rows.childCount == 0) {
            return
        }

        isExpanded = true

        // a fold that was still fading out would otherwise hide all of this again when it ends
        scrim.animate().cancel()
        rowsScroll.animate().cancel()

        scrim.alpha = 0f
        scrim.visibility = View.VISIBLE
        scrim.animate().alpha(1f).setDuration(ANIMATION_MILLIS)

        rowsScroll.alpha = 1f
        rowsScroll.visibility = View.VISIBLE

        // the rows hang off the bottom of the scroller, and that is the end the buttons are at
        rowsScroll.post { rowsScroll.fullScroll(View.FOCUS_DOWN) }

        val rise = RISE_DP * resources.displayMetrics.density
        for (index in 0 until rows.childCount) {
            val row = rows.getChildAt(index)

            row.alpha = 0f
            row.translationY = rise
            row.animate().alpha(1f).translationY(0f).setDuration(ANIMATION_MILLIS)
        }

        moreButton.setImageResource(R.drawable.ic_close)
        moreButton.contentDescription = context.getString(R.string.document_actions_close)

        expandedListener?.invoke(true)
    }

    fun collapse() {
        if (!isExpanded) {
            // also the state setActions() starts from, so the views have to be put right anyway
            scrim.visibility = View.GONE
            rowsScroll.visibility = View.GONE

            return
        }

        isExpanded = false

        scrim.animate().alpha(0f).setDuration(ANIMATION_MILLIS).withEndAction {
            scrim.visibility = View.GONE
        }

        rowsScroll.animate().alpha(0f).setDuration(ANIMATION_MILLIS).withEndAction {
            rowsScroll.visibility = View.GONE
            rowsScroll.alpha = 1f
        }

        moreButton.setImageResource(R.drawable.ic_more_vert)
        moreButton.contentDescription = context.getString(R.string.document_actions_more)

        expandedListener?.invoke(false)
    }

    companion object {
        const val ACTION_SEARCH: Int = 1
        const val ACTION_EDIT: Int = 2
        const val ACTION_TTS: Int = 3
        const val ACTION_SHARE: Int = 4
        const val ACTION_PRINT: Int = 5
        const val ACTION_OPEN_WITH: Int = 6
        const val ACTION_SAVE: Int = 7
        const val ACTION_FULLSCREEN: Int = 8

        private const val ANIMATION_MILLIS = 150L

        /** How far below its place a row starts out, so the column rises as it appears. */
        private const val RISE_DP = 16f
    }
}
