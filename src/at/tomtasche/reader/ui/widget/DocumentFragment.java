package at.tomtasche.reader.ui.widget;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class DocumentFragment extends Fragment {

    public static DocumentFragment newInstance(int page, String data) {
	DocumentFragment document = new DocumentFragment();

	Bundle args = new Bundle();
	args.putInt("page", page);
	args.putString("data", data);

	document.setArguments(args);

	return document;
    }


    DocumentView view;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	if (container == null) {
	    return null;
	}

	view = new DocumentView(getActivity());
	view.loadData(getArguments().getString("data"));
	return view;
    }


    public int getShownIndex() {
	return getArguments().getInt("page", 0);
    }
    
    public DocumentView getDocumentView() {
	return view;
    }
}
