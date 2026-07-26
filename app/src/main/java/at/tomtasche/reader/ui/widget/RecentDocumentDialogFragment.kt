package at.tomtasche.reader.ui.widget

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import at.tomtasche.reader.R
import at.tomtasche.reader.background.RecentDocumentsUtil
import at.tomtasche.reader.ui.activity.MainActivity

class RecentDocumentDialogFragment :
    DialogFragment(), OnItemClickListener, OnItemLongClickListener {

    // insertion ordered: the adapter is built from keys, so a HashMap here would
    // throw away the order RecentDocumentsUtil hands the documents back in
    private var items: MutableMap<String, String?> = LinkedHashMap()
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
        listView.onItemLongClickListener = this

        adapter =
            ArrayAdapter(
                requireActivity(),
                android.R.layout.simple_list_item_1,
                emptyArray<String>(),
            )
        listView.adapter = adapter

        builder.setView(listView)

        setCancelable(true)

        items = LinkedHashMap()

        loadRecentDocuments()

        listView.emptyView = emptyView

        return builder.create()
    }

    private fun loadRecentDocuments() {
        items.clear()
        try {
            items.putAll(RecentDocumentsUtil.getRecentDocuments(requireActivity()))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.isEmpty()) {
            items = LinkedHashMap()
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

    override fun onItemLongClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long,
    ): Boolean {
        return false
    }

    companion object {
        const val FRAGMENT_TAG: String = "document_chooser"
    }
}
