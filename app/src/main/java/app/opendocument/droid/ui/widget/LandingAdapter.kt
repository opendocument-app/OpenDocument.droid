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

/** The rows of the landing screen: the recently opened documents and the catch-all setting. */
class LandingAdapter(private val listener: Listener) :
    ListAdapter<LandingItem, LandingAdapter.ViewHolder>(DIFF) {

    interface Listener {

        fun onDocumentClicked(document: LandingItem.Document)

        fun onDocumentRemoveRequested(document: LandingItem.Document)

        fun onCatchAllChanged(enabled: Boolean)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is LandingItem.Header -> TYPE_HEADER
            is LandingItem.Document -> TYPE_DOCUMENT
            is LandingItem.CatchAll -> TYPE_CATCH_ALL
            is LandingItem.Message -> TYPE_MESSAGE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            when (viewType) {
                TYPE_HEADER -> R.layout.item_landing_header
                TYPE_CATCH_ALL -> R.layout.item_landing_switch
                TYPE_MESSAGE -> R.layout.item_landing_message
                else -> R.layout.item_landing_row
            }

        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LandingItem.Header -> holder.title.setText(item.title)

            is LandingItem.Message -> holder.title.setText(item.text)

            is LandingItem.Document -> {
                holder.icon.setImageResource(R.drawable.ic_description)
                holder.title.text = item.filename
                holder.itemView.setOnClickListener { listener.onDocumentClicked(item) }
                holder.itemView.setOnLongClickListener {
                    listener.onDocumentRemoveRequested(item)

                    true
                }
            }

            is LandingItem.CatchAll -> {
                // set the state before the listener, so restoring it does not report a change
                holder.switch.setOnCheckedChangeListener(null)
                holder.switch.isChecked = item.checked
                holder.switch.setOnCheckedChangeListener { _, isChecked ->
                    listener.onCatchAllChanged(isChecked)
                }

                holder.itemView.setOnClickListener { holder.switch.toggle() }
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView by lazy { view.findViewById(R.id.landing_row_icon) }
        val title: TextView by lazy { view.findViewById(R.id.landing_row_title) }
        val switch: MaterialSwitch by lazy { view.findViewById(R.id.landing_row_switch) }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DOCUMENT = 1
        const val TYPE_CATCH_ALL = 2
        const val TYPE_MESSAGE = 3

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
                            oldItem.filename == newItem.filename

                        oldItem is LandingItem.CatchAll && newItem is LandingItem.CatchAll ->
                            oldItem.checked == newItem.checked

                        // headers and messages are fully described by their id
                        else -> true
                    }
            }
    }
}
