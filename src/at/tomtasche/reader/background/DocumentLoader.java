package at.tomtasche.reader.background;

import java.io.File;
import java.io.InputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import at.tomtasche.reader.background.openoffice.JOpenDocumentWrapper;

public class DocumentLoader extends AsyncTask<Uri, Void, Document> {

    public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

    private final Context context;
    private final File cacheDirectory;
    private final ProgressDialog progressDialog;

    private OnSuccessCallback successCallback;
    private OnErrorCallback errorCallback;
    private Throwable lastError;

    public DocumentLoader(Context context) {
	this.context = context;

	progressDialog = new ProgressDialog(context);
	progressDialog.setTitle("Loading...");
	progressDialog.setIndeterminate(true);
	progressDialog.setCancelable(false);

	cacheDirectory = context.getCacheDir();
    }

    public void setOnSuccessCallback(OnSuccessCallback successCallback) {
	this.successCallback = successCallback;
    }

    public void setOnErrorCallback(OnErrorCallback errorCallback) {
	this.errorCallback = errorCallback;
    }

    @Override
    protected void onPreExecute() {
	super.onPreExecute();

	progressDialog.show();
    }

    @Override
    protected Document doInBackground(Uri... params) {
	Uri uri = params[0];

	// cleanup uri
	if ("/./".equals(uri.toString().substring(0, 2))) {
	    uri = Uri.parse(uri.toString().substring(2, uri.toString().length()));
	}

	try {
	    InputStream stream;
	    if (URI_INTRO.equals(uri)) {
		stream = context.getAssets().open("intro.odt");
	    } else {
		stream = context.getContentResolver().openInputStream(uri);
	    }

	    return JOpenDocumentWrapper.parseStream(stream, cacheDirectory);
	} catch (Throwable e) {
	    e.printStackTrace();

	    lastError = e;

	    return null;
	}
    }

    @Override
    protected void onCancelled() {
	super.onCancelled();

	progressDialog.dismiss();
    }

    @Override
    protected void onPostExecute(Document document) {
	super.onPostExecute(document);

	if (document == null) {
	    if (errorCallback != null)
		errorCallback.onError(lastError);
	} else {
	    if (successCallback != null)
		successCallback.onSuccess(document);
	}

	progressDialog.dismiss();
    }

    public static interface OnSuccessCallback {

	public void onSuccess(Document document);
    }

    public static interface OnErrorCallback {

	public void onError(Throwable error);
    }
}
