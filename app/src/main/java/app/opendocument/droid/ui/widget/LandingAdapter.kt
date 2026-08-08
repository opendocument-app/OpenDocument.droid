package app.opendocument.droid.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.opendocument.droid.R
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * The rows of the landing screen: the recently opened documents, what the app is for, and the
 * settings underneath them.
 */
class LandingAdapter(private val listener: Listener) :
    ListAdapter<LandingItem, LandingAdapter.ViewHolder>(DIFF) {

    interface Listener {

        fun onDocumentClicked(document: LandingItem.Document)

        fun onActionClicked(action: Int)

        fun onOpenClicked()

        fun onSectionToggled(section: Int)

        fun onSettingChanged(setting: Int, enabled: Boolean)
    }

    /**
     * Whether the row at [position] can be swiped away, which only a recently opened document can:
     * the list is the app's own memory of it, and nothing else on the screen is the app's to
     * forget.
     */
    fun isRemovable(position: Int): Boolean = itemAt(position) is LandingItem.Document

    fun itemAt(position: Int): LandingItem? =
        if (position in 0 until itemCount) getItem(position) else null

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is LandingItem.Header -> TYPE_HEADER
            is LandingItem.Open -> TYPE_OPEN
            is LandingItem.Document -> TYPE_DOCUMENT
            is LandingItem.Action -> TYPE_ACTION
            is LandingItem.Setting -> TYPE_SETTING
            is LandingItem.Intro -> TYPE_INTRO
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            when (viewType) {
                TYPE_HEADER -> R.layout.item_landing_header
                TYPE_OPEN -> R.layout.item_landing_open
                TYPE_SETTING -> R.layout.item_landing_switch
                TYPE_INTRO -> R.layout.item_landing_intro
                // documents and actions are both an icon plus a label
                else -> R.layout.item_landing_row
            }

        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LandingItem.Header -> {
                holder.title.setText(item.title)

                val section = item.section
                if (section == null) {
                    holder.chevron.visibility = View.GONE
                    holder.itemView.setOnClickListener(null)
                    holder.itemView.isClickable = false
                } else {
                    holder.chevron.visibility = View.VISIBLE
                    holder.chevron.setImageResource(
                        if (item.expanded) R.drawable.ic_keyboard_arrow_up
                        else R.drawable.ic_keyboard_arrow_down
                    )
                    holder.itemView.setOnClickListener { listener.onSectionToggled(section) }
                }
            }

            is LandingItem.Open -> holder.open.setOnClickListener { listener.onOpenClicked() }

            // a whole layout of its own, with nothing to fill in
            is LandingItem.Intro -> Unit

            is LandingItem.Document -> {
                holder.icon.setImageResource(R.drawable.ic_description)
                holder.title.text = item.filename
                holder.bindSubtitle(item.subtitle)
                holder.itemView.setOnClickListener { listener.onDocumentClicked(item) }
            }

            is LandingItem.Action -> {
                holder.icon.setImageResource(item.icon)
                holder.title.setText(item.label)
                holder.bindSubtitle(null)
                holder.itemView.setOnClickListener { listener.onActionClicked(item.action) }
            }

            is LandingItem.Setting -> {
                holder.title.setText(item.title)
                holder.subtitle.setText(item.body)

                // set the state before the listener, so restoring it does not report a change
                holder.switch.setOnCheckedChangeListener(null)
                holder.switch.isChecked = item.checked
                holder.switch.setOnCheckedChangeListener { _, isChecked ->
                    listener.onSettingChanged(item.setting, isChecked)
                }

                holder.itemView.setOnClickListener { holder.switch.toggle() }
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView by lazy { view.findViewById(R.id.landing_row_icon) }
        val title: TextView by lazy { view.findViewById(R.id.landing_row_title) }
        val subtitle: TextView by lazy { view.findViewById(R.id.landing_row_subtitle) }
        val switch: MaterialSwitch by lazy { view.findViewById(R.id.landing_row_switch) }
        val chevron: ImageView by lazy { view.findViewById(R.id.landing_header_chevron) }
        val open: View by lazy { view.findViewById(R.id.landing_open) }

        fun bindSubtitle(text: String?) {
            subtitle.text = text
            subtitle.visibility = if (text == null) View.GONE else View.VISIBLE
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DOCUMENT = 1
        const val TYPE_ACTION = 2
        const val TYPE_SETTING = 3
        const val TYPE_INTRO = 4
        const val TYPE_OPEN = 5

        val DIFF =
            object : DiffUtil.ItemCallback<LandingItem>() {

                override fun areItemsTheSame(oldItem: LandingItem, newItem: LandingItem): Boolean =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: LandingItem,
                    newItem: LandingItem,
                ): Boolean =
                    when {
                        oldItem is LandingItem.Document && newItem is LandingItem.Document ->
                            oldItem.filename == newItem.filename &&
                                oldItem.subtitle == newItem.subtitle

                        oldItem is LandingItem.Setting && newItem is LandingItem.Setting ->
                            oldItem.checked == newItem.checked

                        // the chevron has to turn over when the section folds
                        oldItem is LandingItem.Header && newItem is LandingItem.Header ->
                            oldItem.expanded == newItem.expanded

                        // actions, the open button and the intro are fully described by their id
                        else -> true
                    }
            }
    }
}
