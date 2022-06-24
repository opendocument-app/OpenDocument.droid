package at.tomtasche.reader.ui.widget;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.fragment.app.DialogFragment;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.RecentDocumentsUtil;
import at.tomtasche.reader.ui.activity.MainActivity;

public class RecentDocumentDialogFragment extends DialogFragment implements
        OnItemClickListener, OnItemLongClickListener {

    public static final String FRAGMENT_TAG = "document_chooser";

    private Map<String, String> items;
    private ListAdapter adapter;
    private ListView listView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new Builder(getActivity());
        builder.setTitle(R.string.dialog_recent_title);
        builder.setCancelable(true);

        TextView emptyView = new TextView(getActivity());
        emptyView.setText(R.string.dialog_loading_title);

        listView = new ListView(getActivity());
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);

        adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, new String[0]);
        listView.setAdapter(adapter);

        builder.setView(listView);

        setCancelable(true);

        items = new HashMap<String, String>();

        loadRecentDocuments();

        listView.setEmptyView(emptyView);

        return builder.create();
    }

    private void loadRecentDocuments() {
        items.clear();
        try {
            items.putAll(RecentDocumentsUtil.getRecentDocuments(getActivity()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (items.size() == 0) {
            items = new HashMap<String, String>();
            items.put(
                    getActivity().getString(
                            R.string.dialog_list_no_documents_found), null);
        }

        adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, new ArrayList<String>(
                items.keySet()));

        listView.setAdapter(adapter);
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if (items == null)
            return;

        String key = (String) adapter.getItem(arg2);
        if (key == null)
            return;

        String uri = items.get(key);
        if (uri == null)
            return;

        dismiss();

        MainActivity activity = ((MainActivity) getActivity());
        activity.loadUri(Uri.parse(uri));
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
                                   long arg3) {
        return false;
    }
}
