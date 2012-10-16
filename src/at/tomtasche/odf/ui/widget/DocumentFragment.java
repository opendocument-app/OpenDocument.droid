package at.tomtasche.odf.ui.widget;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import at.tomtasche.odf.R;
import at.tomtasche.odf.background.Document;
import at.tomtasche.odf.background.Document.Part;

public class DocumentFragment extends Fragment {

    private DocumentView documentView;
    private Document document;
    private int index;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	documentView = new DocumentView(getActivity());
	documentView.loadData(getActivity().getString(R.string.message_get_started), "text/plain", DocumentView.ENCODING);
	
	document = new Document();

	return documentView;
    }

    private void loadData(String url) {
	documentView.loadUrl(url);
    }

    public void loadDocument(Document document) {
	this.document = document;

	index = 0;

	Part firstPage = document.getPageAt(index);
	loadData(firstPage.getUrl());
    }

    public void searchDocument(String query) {
	documentView.findAll(query);
    }

    public boolean nextPage() {
	return goToPage(index + 1);
    }

    public boolean previousPage() {
	return goToPage(index - 1);
    }

    public boolean goToPage(int page) {
	if (page < 0 || page >= document.getPages().size())
	    return false;

	index = page;

	loadData(document.getPageAt(index).getUrl());

	return true;
    }

    public List<Part> getPages() {
	return document.getPages();
    }
}
