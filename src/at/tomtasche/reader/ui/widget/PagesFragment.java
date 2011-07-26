package at.tomtasche.reader.ui.widget;

import java.util.LinkedList;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.ui.activity.DocumentActivity;
import at.tomtasche.reader.ui.activity.OfficeActivity;

public class PagesFragment extends ListFragment {

    boolean fragmented;

    int page;


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
	super.onActivityCreated(savedInstanceState);

	List<String> strings = new LinkedList<String>();
	strings.add("Please wait...");
	strings.add("Loading...");
	strings.add("Should take just a few seconds...");
	strings.add("http://tomtasche.at/");
	
	applyAdapter(strings);

	View documentFrame = getActivity().findViewById(R.id.document);
	fragmented = (documentFrame != null && documentFrame.getVisibility() == View.VISIBLE);

	if (savedInstanceState != null) {
	    page = savedInstanceState.getInt("page", 0);
	}

	if (fragmented) {
	    getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}
    }
    
    public void applyAdapter(List<String> strings) {
	ListAdapter adapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_pages, R.id.list_text, strings);

	setListAdapter(adapter);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
	super.onSaveInstanceState(outState);

	outState.putInt("page", page);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
	super.onListItemClick(l, v, position, id);

	page = position;

	showDocument();
    }
    
    public DocumentService getService() {
	return ((OfficeActivity) getActivity()).getService();
    }

    private void showDocument() {
	String data = getService().getData(page);

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
}
