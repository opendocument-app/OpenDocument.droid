package at.tomtasche.reader.ui.widget;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Part;

public class DocumentFragment extends Fragment {

    private DocumentView documentView;
    private Document document;
    private int index;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	documentView = new DocumentView(getActivity());

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
	if (document.getPages().size() >= index + 1)
	    return false;

	loadData(document.getPageAt(++index).getUrl());

	return true;
    }

    public boolean previousPage() {
	if (index - 1 < 0)
	    return false;

	loadData(document.getPageAt(++index).getUrl());

	return true;
    }

    public boolean goToPage(int page) {
	if (page < 0 && page >= document.getPages().size())
	    return false;

	index = page;

	loadData(document.getPageAt(index).getUrl());

	return true;
    }

    public List<Part> getPages() {
	return document.getPages();
    }
}
