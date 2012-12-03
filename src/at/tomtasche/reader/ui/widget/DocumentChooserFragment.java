package at.tomtasche.reader.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentChooserLoader;
import at.tomtasche.reader.ui.activity.MainActivity;

public class DocumentChooserFragment extends ListFragment implements
		LoaderCallbacks<Map<String, String>> {

	private Map<String, String> items;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ListAdapter adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.simple_list_item_1, new String[0]);
		setListAdapter(adapter);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onStart() {
		super.onStart();

		setEmptyText(getActivity().getString(R.string.list_searching_documents));
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (!(activity instanceof MainActivity))
			throw new IllegalArgumentException(
					"Activity must be of type MainActivity");

		getLoaderManager().restartLoader(0, null, this);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		if (items == null)
			return;

		String key = (String) getListAdapter().getItem(position);
		if (key == null)
			return;

		String uri = items.get(key);
		if (uri == null)
			return;

		MainActivity activity = ((MainActivity) getActivity());
		activity.loadUri(Uri.parse(uri));
		if (activity.getSlidingMenu().isBehindShowing())
			activity.getSlidingMenu().toggle();
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

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.simple_list_item_1, new ArrayList<String>(
						items.keySet()));

		setListAdapter(adapter);
	}

	@Override
	public void onLoaderReset(Loader<Map<String, String>> arg0) {
		items = null;
	}
}
