package at.tomtasche.reader.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentChooserLoader;
import at.tomtasche.reader.ui.activity.DocumentLoadingActivity;

public class DocumentChooserDialogFragment extends DialogFragment implements
		LoaderCallbacks<Map<String, String>>, OnItemClickListener {

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

		adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.simple_list_item_1, new String[0]);
		listView.setAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);

		builder.setView(listView);

		setCancelable(true);

		return builder.create();
	}

	@Override
	public Loader<Map<String, String>> onCreateLoader(int arg0, Bundle arg1) {
		return new DocumentChooserLoader(getActivity());
	}

	@Override
	public void onLoadFinished(Loader<Map<String, String>> arg0,
			Map<String, String> arg1) {
		items = Collections.unmodifiableMap(arg1);
		if (items.size() == 0) {
			items = new HashMap<String, String>();
			items.put(
					getActivity().getString(R.string.list_no_documents_found),
					null);
		}

		adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.simple_list_item_1, new ArrayList<String>(
						items.keySet()));

		listView.setAdapter(adapter);

		TextView emptyView = new TextView(getActivity());
		emptyView.setText(R.string.list_searching_documents);

		listView.setEmptyView(emptyView);
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

		DocumentLoadingActivity activity = ((DocumentLoadingActivity) getActivity());
		activity.loadUri(Uri.parse(uri));
	}

	@Override
	public void onLoaderReset(Loader<Map<String, String>> arg0) {
		items = null;
	}
}
