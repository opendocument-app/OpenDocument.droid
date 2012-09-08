package at.tomtasche.reader.background;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.EditText;
import at.andiwand.commons.lwxml.writer.LWXMLStreamWriter;
import at.andiwand.commons.lwxml.writer.LWXMLWriter;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.andiwand.odf2html.odf.OpenDocument;
import at.andiwand.odf2html.odf.OpenDocumentFile;
import at.andiwand.odf2html.odf.OpenDocumentSpreadsheet;
import at.andiwand.odf2html.odf.OpenDocumentText;
import at.andiwand.odf2html.odf.TemporaryOpenDocumentFile;
import at.andiwand.odf2html.translator.FileCache;
import at.andiwand.odf2html.translator.document.SpreadsheetTranslator;
import at.andiwand.odf2html.translator.document.TextTranslator;
import at.tomtasche.reader.background.Document.Part;

public class DocumentLoader extends AsyncTask<Uri, Void, Document> {

    public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

    private final Context context;
    private final ProgressDialog progressDialog;

    private OnSuccessCallback successCallback;
    private OnErrorCallback errorCallback;
    private Throwable lastError;

    private Uri uri;

    private String password;

    public DocumentLoader(Context context) {
	this.context = context;

	progressDialog = new ProgressDialog(context);
	progressDialog.setTitle("Loading...");
	progressDialog.setIndeterminate(true);
	progressDialog.setCancelable(false);
    }

    public void setPassword(String password) {
	this.password = password;
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
	uri = params[0];

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

	    OpenDocumentFile documentFile = new TemporaryOpenDocumentFile(stream);
	    String mimeType = documentFile.getMimetype();
	    if (!OpenDocument.checkMimetype(mimeType))
		throw new IllegalMimeTypeException();

	    if (documentFile.isEncrypted() && password == null) {
		throw new EncryptedDocumentException();
	    } else if (password != null) {
		documentFile.setPassword(password);
	    }

	    FileCache fileCache = new AndroidFileCache(context);
	    OpenDocument document = documentFile.getAsOpenDocument();
	    CharArrayWriter writer = new CharArrayWriter();
	    LWXMLWriter out = new LWXMLStreamWriter(writer);
	    if (document instanceof OpenDocumentText) {
		try {
		    TextTranslator translator = new TextTranslator(fileCache);
		    translator.translate(document, out);
		} finally {
		    out.close();
		}

		File htmlFile = new File(context.getCacheDir(), "temp.html");
		FileWriter fileWriter = new FileWriter(htmlFile);
		writer.writeTo(fileWriter);
		fileWriter.close();
	    } else if (document instanceof OpenDocumentSpreadsheet) {
		try {
		    SpreadsheetTranslator translator = new SpreadsheetTranslator(fileCache);
		    translator.translate(document, out);
		} finally {
		    out.close();
		}

		File htmlFile = new File(context.getCacheDir(), "temp.html");
		FileWriter fileWriter = new FileWriter(htmlFile);
		writer.writeTo(fileWriter);
		fileWriter.close();
	    } else {
		throw new IllegalMimeTypeException(
			"I don't know what it is, but I can't stop parsing it");
	    }

	    Document result = new Document();
	    result.addPage(new Part("Document", new File(context.getCacheDir(), "temp.html")
		    .toURI(), 0));

	    return result;
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
	    if (lastError instanceof EncryptedDocumentException) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Document is password-protected");

		final EditText input = new EditText(context);
		builder.setView(input);

		builder.setPositiveButton(context.getString(android.R.string.ok),
			new DialogInterface.OnClickListener() {

			    @Override
			    public void onClick(DialogInterface dialog, int whichButton) {
				DocumentLoader documentLoader = new DocumentLoader(context);
				documentLoader.setOnSuccessCallback(successCallback);
				documentLoader.setOnErrorCallback(errorCallback);
				documentLoader.setPassword(input.getText().toString());
				documentLoader.execute(uri);

				dialog.dismiss();
			    }
			});
		builder.setNegativeButton(context.getString(android.R.string.cancel), null);
		builder.show();
	    }

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

    @SuppressWarnings("serial")
    private class EncryptedDocumentException extends Exception {
    }
}
