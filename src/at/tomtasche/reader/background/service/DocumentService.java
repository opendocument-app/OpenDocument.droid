package at.tomtasche.reader.background.service;

import java.io.IOException;
import java.util.List;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentService extends Service implements OfficeInterface {

    public static final String DOCUMENT_CHANGED_INTENT = "at.tomtasche.reader.DOCUMENT_CHANGED";


    DocumentBinder binder = new DocumentBinder();

    ProgressDialog dialog;

    DocumentLoader loader;


    @Override
    public IBinder onBind(Intent arg0) {
	return binder;
    }

    @Override
    public void onCreate() {
	super.onCreate();

	loader = DocumentLoader.getThreadedLoader(this, this);

	loadAsset("intro.odt");
    }


    private void loadAsset(String name) {
	try {
	    loader.loadDocument(getAssets().open(name));
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }


    @Override
    public void onStart(Intent intent, int startId) {
	super.onStart(intent, startId);

	if (intent == null) {
	    stopSelf();

	    return;
	}

	Uri uri = intent.getData();

	if (uri == null || uri.toString().equals("")) return;

	if (uri.toString().equals("reader://intro.odt")) {
	    loadAsset("intro.odt");

	    return;
	}

	loader.loadDocument(uri);
    }

    @Override
    public void onFinished() {
	sendBroadcast(new Intent(DOCUMENT_CHANGED_INTENT));
    }

    @Override
    public void showToast(int resId) {
	Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show();
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


    public class DocumentBinder extends Binder {

	public DocumentService getService() {
	    return DocumentService.this;
	}
    }
}
