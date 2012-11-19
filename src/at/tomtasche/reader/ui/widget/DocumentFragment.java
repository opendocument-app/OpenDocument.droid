package at.tomtasche.reader.ui.widget;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Part;

public class DocumentFragment extends Fragment {

	public static final String FRAGMENT_TAG = "document_fragment";

	private DocumentView documentView;
	private Document document;
	private int currentIndex;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (documentView != null) {
			return documentView;
		} else {
			documentView = new DocumentView(getActivity());

			document = new Document();

			documentView.loadData(
					getActivity().getString(R.string.message_get_started),
					"text/plain", DocumentView.ENCODING);

			return documentView;
		}
	}

	private void loadData(String url) {
		documentView.loadUrl(url);
	}

	public void loadDocument(Document document) {
		this.document = document;

		currentIndex = 0;

		Part firstPage = document.getPageAt(currentIndex);
		loadData(firstPage.getUrl());
	}

	@SuppressWarnings("deprecation")
	public void searchDocument(String query) {
		documentView.findAll(query);
	}

	public boolean nextPage() {
		return goToPage(currentIndex + 1);
	}

	public boolean previousPage() {
		return goToPage(currentIndex - 1);
	}

	public boolean goToPage(int page) {
		if (page < 0 || page >= document.getPages().size())
			return false;

		currentIndex = page;

		loadData(document.getPageAt(currentIndex).getUrl());

		return true;
	}

	public List<Part> getPages() {
		return document.getPages();
	}

	public DocumentView getDocumentView() {
		return documentView;
	}
}
