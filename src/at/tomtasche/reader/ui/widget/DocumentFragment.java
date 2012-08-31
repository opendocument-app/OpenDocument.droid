package at.tomtasche.reader.ui.widget;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import at.tomtasche.reader.Document;
import at.tomtasche.reader.Document.Page;

public class DocumentFragment extends Fragment {

    private DocumentView documentView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	documentView = new DocumentView(getActivity());

	return documentView;
    }

    private void loadData(String html) {
	documentView.loadData(html);
    }

    public void loadDocument(Document document) {
	Page firstPage = document.getPageAt(0);
	loadData(firstPage.getHtml());
    }
}
