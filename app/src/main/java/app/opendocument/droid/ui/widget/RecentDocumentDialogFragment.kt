package app.opendocument.droid.ui.widget

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import app.opendocument.droid.R
import app.opendocument.droid.background.RecentDocumentsUtil
import app.opendocument.droid.ui.activity.MainActivity

class RecentDocumentDialogFragment : DialogFragment(), OnItemClickListener {

    // insertion ordered: the adapter is built from the keys, in the order they came back in
    private val items: MutableMap<String, String?> = LinkedHashMap()
    private lateinit var adapter: ListAdapter
    private lateinit var listView: ListView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(R.string.dialog_recent_title)
        builder.setCancelable(true)

        val emptyView = TextView(requireActivity())
        emptyView.setText(R.string.dialog_loading_title)

        listView = ListView(requireActivity())
        listView.emptyView = emptyView
        listView.onItemClickListener = this

        builder.setView(listView)

        setCancelable(true)

        loadRecentDocuments()

        return builder.create()
    }

    private fun loadRecentDocuments() {
        items.clear()
        for (entry in RecentDocumentsUtil.getRecentDocuments(requireActivity())) {
            items[entry.filename] = entry.uri
        }

        if (items.isEmpty()) {
            items[getString(R.string.dialog_list_no_documents_found)] = null
        }

        adapter =
            ArrayAdapter(
                requireActivity(),
                android.R.layout.simple_list_item_1,
                ArrayList(items.keys),
            )

        listView.adapter = adapter
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val key = adapter.getItem(position) as? String ?: return

        val uri = items[key] ?: return

        dismiss()

        (requireActivity() as MainActivity).loadUri(Uri.parse(uri))
    }

    companion object {
        const val FRAGMENT_TAG: String = "document_chooser"
    }
}
