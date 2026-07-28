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
 * The rows of the landing screen: the recently opened documents, the folders the user granted
 * access to, and the settings.
 */
class LandingAdapter(private val listener: Listener) :
    ListAdapter<LandingItem, LandingAdapter.ViewHolder>(DIFF) {

    interface Listener {

        fun onDocumentClicked(document: LandingItem.Document)

        fun onDocumentRemoveRequested(document: LandingItem.Document)

        fun onFolderClicked(folder: LandingItem.Folder)

        fun onFolderRemoveRequested(folder: LandingItem.Folder)

        fun onActionClicked(action: Int)

        fun onOpenClicked()

        fun onCatchAllChanged(enabled: Boolean)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is LandingItem.Header -> TYPE_HEADER
            is LandingItem.Document -> TYPE_DOCUMENT
            is LandingItem.Folder -> TYPE_FOLDER
            is LandingItem.Action -> TYPE_ACTION
            is LandingItem.CatchAll -> TYPE_CATCH_ALL
            is LandingItem.Message -> TYPE_MESSAGE
            is LandingItem.Empty -> TYPE_EMPTY
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            when (viewType) {
                TYPE_HEADER -> R.layout.item_landing_header
                TYPE_CATCH_ALL -> R.layout.item_landing_switch
                TYPE_MESSAGE -> R.layout.item_landing_message
                TYPE_EMPTY -> R.layout.item_landing_empty
                // documents, folders and actions are all an icon plus a label
                else -> R.layout.item_landing_row
            }

        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LandingItem.Header -> holder.title.setText(item.title)

            is LandingItem.Message -> holder.title.setText(item.text)

            is LandingItem.Empty -> {
                holder.open.setOnClickListener { listener.onOpenClicked() }
                holder.addFolder.setOnClickListener {
                    listener.onActionClicked(LandingItem.ACTION_ADD_FOLDER)
                }
            }

            is LandingItem.Document -> {
                holder.icon.setImageResource(R.drawable.ic_description)
                holder.title.text = item.filename
                holder.itemView.setOnClickListener { listener.onDocumentClicked(item) }
                holder.itemView.setOnLongClickListener {
                    listener.onDocumentRemoveRequested(item)

                    true
                }
            }

            is LandingItem.Folder -> {
                holder.icon.setImageResource(R.drawable.ic_folder)
                holder.title.text = item.name
                holder.itemView.setOnClickListener { listener.onFolderClicked(item) }
                holder.itemView.setOnLongClickListener {
                    listener.onFolderRemoveRequested(item)

                    true
                }
            }

            is LandingItem.Action -> {
                holder.icon.setImageResource(item.icon)
                holder.title.setText(item.label)
                holder.itemView.setOnClickListener { listener.onActionClicked(item.action) }
                holder.itemView.setOnLongClickListener(null)
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
        val open: View by lazy { view.findViewById(R.id.landing_empty_open) }
        val addFolder: View by lazy { view.findViewById(R.id.landing_empty_add_folder) }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DOCUMENT = 1
        const val TYPE_FOLDER = 2
        const val TYPE_ACTION = 3
        const val TYPE_CATCH_ALL = 4
        const val TYPE_MESSAGE = 5
        const val TYPE_EMPTY = 6

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

                        oldItem is LandingItem.Folder && newItem is LandingItem.Folder ->
                            oldItem.name == newItem.name

                        oldItem is LandingItem.CatchAll && newItem is LandingItem.CatchAll ->
                            oldItem.checked == newItem.checked

                        // headers, messages, actions and the empty state are fully described
                        // by their id
                        else -> true
                    }
            }
    }
}
