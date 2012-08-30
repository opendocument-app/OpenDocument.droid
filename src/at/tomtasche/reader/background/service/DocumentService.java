package at.tomtasche.reader.background.service;

import java.io.IOException;
import java.util.List;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentService implements OfficeInterface {

    public static final String DOCUMENT_CHANGED_INTENT = "at.tomtasche.reader.DOCUMENT_CHANGED";

    ProgressDialog dialog;

    DocumentLoader loader;

    private final Context context;

    public DocumentService(Context context) {
	this.context = context;
	loader = DocumentLoader.getThreadedLoader(context, this);

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

    @Override
    public void onFinished() {
	System.out.println("fin!");
	context.sendBroadcast(new Intent(DOCUMENT_CHANGED_INTENT));
    }

    @Override
    public void showToast(int resId) {
	Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show();
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
	    showToast(R.string.toast_error_no_next);
	}
    }

    public void previousPage() {
	if (loader.hasPrevious()) {
	    loader.getPrevious();
	} else {
	    showToast(R.string.toast_error_no_previous);
	}
    }

    public void jumpToPage(int page) {
	if (loader.getPageCount() <= page) {
	    showToast(R.string.toast_error_no_next);
	} else {
	    loader.getPage(page);
	}
    }
}
