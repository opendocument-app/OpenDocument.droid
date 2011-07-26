package at.tomtasche.reader.background.service;

import java.io.IOException;
import java.util.List;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentService extends Service implements OfficeInterface {

    private final DocumentBinder binder = new DocumentBinder();

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
	try {
	    loader.loadDocument(getAssets().open("intro.odt"));
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    @Override
    public void onStart(Intent intent, int startId) {
	super.onStart(intent, startId);

	loader.loadDocument(intent.getData());
    }

    @Override
    public void showProgress() {
	dialog = ProgressDialog.show(this, "", getString(R.string.progress_dialog_message), true);
    }

    @Override
    public void hideProgress() {
	if (dialog != null) {
	    dialog.dismiss();
	}
    }

    @Override
    public void showDocument(String html) {}

    @Override
    public void runOnUiThread(Runnable runnable) {}

    @Override
    public void showToast(int resId) {
	Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show();
    }

    
    public String getData(int page) {
	return loader.getPage(page);
    }

    public int getPageCount() {
	return loader.getPageCount();
    }

    public List<String> getPageNames() {
	return loader.getPageNames();
    }


    public class DocumentBinder extends Binder {

	public DocumentService getService() {
	    return DocumentService.this;
	}
    }
}
