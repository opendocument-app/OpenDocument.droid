package at.tomtasche.reader.ui.widget;

import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.background.service.DocumentServiceConnection;
import at.tomtasche.reader.background.service.DocumentServiceConnection.DocumentServiceConnectionListener;
import at.tomtasche.reader.ui.activity.DocumentActivity;

public class PagesFragment extends ListFragment implements DocumentServiceConnectionListener {

    DocumentServiceConnection connection;

    BroadcastReceiver receiver;

    boolean fragmented;
    
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
	super.onActivityCreated(savedInstanceState);

	connection = new DocumentServiceConnection(getActivity());
	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    connection.setListener(this);
	} else if (savedInstanceState != null) {
	    int page = savedInstanceState.getInt("page", 0);
	    String data = savedInstanceState.getString("data");

	    if (data != null) showDocument(page, data);
	}

	List<String> strings = new LinkedList<String>();
	strings.add(getString(R.string.message_get_started));
	strings.add("http://tomtasche.at/");

	applyAdapter(strings);

	View documentFrame = getActivity().findViewById(R.id.document);
	fragmented = (documentFrame != null && documentFrame.getVisibility() == View.VISIBLE);

	if (fragmented) {
	    getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	    getListView().clearChoices();
	}

	if (savedInstanceState != null) {
	    int page = savedInstanceState.getInt("page", 0);
	    getListView().setSelection(page);

	    if (connection.isConnected()) showDocument();
	}

	receiver = new DocumentChangedReceiver();
	IntentFilter filter = new IntentFilter(DocumentService.DOCUMENT_CHANGED_INTENT);
	getActivity().registerReceiver(receiver, filter);
    }
    

    private void applyAdapter(List<String> strings) {
	ListAdapter adapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_pages, R.id.list_text, strings);

	setListAdapter(adapter);
    }

    private void updateList() {
	getListView().clearChoices();

	List<String> pages = getDocumentService().getPageNames();
	if (pages.isEmpty()) {
	    pages = new LinkedList<String>();
	    pages.add("Your document doesn't have any pages.");
	    pages.add("Have a good reading.");
	    pages.add("Don't forget to rate this app. :)");

	    getListView().setSelection(0);

	    showDocument();
	}

	applyAdapter(pages);
    }

    private void showDocument() {
	int page = getListView().getSelectedItemPosition();
	String data = getDocumentService().getData(page);

	showDocument(page, data);
    }

    private void showDocument(int page, String data) {
	if (fragmented) {
	    getListView().setItemChecked(page, true);

	    DocumentFragment document = (DocumentFragment) getFragmentManager().findFragmentById(R.id.document);
	    if (document == null || document.getShownIndex() != page) {
		document = DocumentFragment.newInstance(page, data);

		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.document, document);
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.commit();
	    }
	} else {
	    Intent intent = new Intent();
	    intent.setClass(getActivity(), DocumentActivity.class);
	    intent.putExtra("page", page);
	    intent.putExtra("data", data);
	    startActivity(intent);
	}
    }


    @Override
    public void onConnected() {
	Log.e("smn", "onConnected");

	showDocument();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
	super.onSaveInstanceState(outState);

	int page = getListView().getSelectedItemPosition();
	outState.putInt("page", page);

	if (connection.isConnected()) {
	    String data = connection.getDocumentService().getData(page);
	    outState.putString("data", data);
	}
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
	super.onListItemClick(l, v, position, id);

	showDocument();
    }

    @Override
    public void onDestroyView() {
	super.onDestroyView();

	if (connection.isConnected()) connection.unbind();
	if (receiver != null) {
	    getActivity().unregisterReceiver(receiver);
	    receiver = null;
	}
    }


    private DocumentService getDocumentService() {
	return connection.getDocumentService();
    }


    class DocumentChangedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
	    if (!connection.isConnected()) return;

	    updateList();
	}
    }
}
