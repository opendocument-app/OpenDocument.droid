package at.tomtasche.reader.background.service;

import java.io.IOException;
import java.util.List;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentService {

    ProgressDialog dialog;

    DocumentLoader loader;

    private final Context context;

    private final OfficeInterface office;

    public DocumentService(Context context, OfficeInterface office) {
	this.context = context;
	this.office = office;
	loader = DocumentLoader.getThreadedLoader(context, office);

	loadAsset("intro.odt");
    }

    public void loadUri(Uri uri) {
	if (uri.toString().equals("reader://intro.odt")) {
	    loadAsset("intro.odt");
	} else {
	    loader.loadDocument(uri);
	}
    }

    private void loadAsset(String name) {
	try {
	    loader.loadDocument(context.getAssets().open(name));
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public String getData(int page) {
	return loader.getPage(page);
    }

    public String getData() {
	return loader.getPage(loader.getPageIndex());
    }

    public int getPageCount() {
	return loader.getPageCount();
    }

    public List<String> getPageNames() {
	return loader.getPageNames();
    }

    public void nextPage() {
	if (loader.hasNext()) {
	    loader.getNext();
	} else {
	    office.showToast(R.string.toast_error_no_next);
	}
    }

    public void previousPage() {
	if (loader.hasPrevious()) {
	    loader.getPrevious();
	} else {
	    office.showToast(R.string.toast_error_no_previous);
	}
    }

    public void jumpToPage(int page) {
	if (loader.getPageCount() <= page) {
	    office.showToast(R.string.toast_error_no_next);
	} else {
	    loader.getPage(page);
	}
    }
}
