package at.tomtasche.reader.background;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import at.andiwand.commons.lwxml.writer.LWXMLStreamWriter;
import at.andiwand.commons.lwxml.writer.LWXMLWriter;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.andiwand.odf2html.odf.OpenDocument;
import at.andiwand.odf2html.odf.OpenDocumentSpreadsheet;
import at.andiwand.odf2html.odf.OpenDocumentText;
import at.andiwand.odf2html.odf.TemporaryOpenDocumentFile;
import at.andiwand.odf2html.translator.document.SpreadsheetTranslator;
import at.andiwand.odf2html.translator.document.TextTranslator;
import at.tomtasche.reader.background.Document.Part;

public class DocumentLoader extends AsyncTask<Uri, Void, Document> {

	public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

	private OnSuccessCallback successCallback;
	private OnErrorCallback errorCallback;
	private Throwable lastError;

	private Context context;
	private Uri uri;

	private String password;

	public DocumentLoader(Context context) {
		this.context = context.getApplicationContext();
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

	public Throwable getLastError() {
		return lastError;
	}

	@Override
	protected Document doInBackground(Uri... params) {
		try {
			AndroidFileCache.cleanup();

			// cleanup uri
			if ("/./".equals(uri.toString().substring(0, 2))) {
				uri = Uri.parse(uri.toString().substring(2,
						uri.toString().length()));
			}

			InputStream stream;
			if (URI_INTRO.equals(uri)) {
				stream = context.getAssets().open("intro.odt");
			} else {
				stream = context.getContentResolver().openInputStream(uri);
			}

			AndroidFileCache cache = new AndroidFileCache(context);
			TemporaryOpenDocumentFile documentFile = new TemporaryOpenDocumentFile(
					stream, cache);

			uri = Uri.parse(cache.getFileURI(documentFile.getFile().getName())
					.toString());

			String mimeType = documentFile.getMimetype();
			if (!OpenDocument.checkMimetype(mimeType)) {
				throw new IllegalMimeTypeException();
			}

			if (documentFile.isEncrypted() && password == null) {
				throw new EncryptedDocumentException();
			} else if (password != null) {
				documentFile.setPassword(password);
			}

			Document result = new Document();
			OpenDocument document = documentFile.getAsOpenDocument();
			if (document instanceof OpenDocumentText) {
				CharArrayWriter writer = new CharArrayWriter();
				LWXMLWriter out = new LWXMLStreamWriter(writer);
				try {
					TextTranslator translator = new TextTranslator(cache);
					translator.translate(document, out);
				} finally {
					out.close();
					writer.close();
				}

				File htmlFile = cache.getFile("temp.html");
				FileWriter fileWriter = new FileWriter(htmlFile);
				writer.writeTo(fileWriter);
				fileWriter.close();

				result.addPage(new Part("Document", htmlFile.toURI(), 0));
			} else if (document instanceof OpenDocumentSpreadsheet) {
				List<String> tableNames = new ArrayList<String>(
						((OpenDocumentSpreadsheet) document).getTableMap()
								.keySet());
				SpreadsheetTranslator translator = new SpreadsheetTranslator(
						cache);
				for (int i = 0; i < ((OpenDocumentSpreadsheet) document)
						.getTableCount(); i++) {
					CharArrayWriter writer = new CharArrayWriter();
					LWXMLWriter out = new LWXMLStreamWriter(writer);
					try {
						translator.translate(document, out, i);

						File htmlFile = cache.getFile("temp" + i + ".html");
						FileWriter fileWriter = new FileWriter(htmlFile);
						try {
							writer.writeTo(fileWriter);
						} finally {
							fileWriter.close();
						}

						result.addPage(new Part(tableNames.get(i), htmlFile
								.toURI(), i));
					} finally {
						out.close();
						writer.close();
					}
				}
			} else {
				throw new IllegalMimeTypeException(
						"I don't know what it is, but I can't stop parsing it");
			}

			return result;
		} catch (Throwable e) {
			e.printStackTrace();

			lastError = e;
		}

		return null;
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();

		errorCallback.onError(null, uri);
	}

	@Override
	protected void onPostExecute(Document document) {
		super.onPostExecute(document);

		if (lastError != null && errorCallback != null) {
			errorCallback.onError(lastError, uri);
		} else if (document != null) {
			if (successCallback != null)
				successCallback.onSuccess(document);
		} else {
			if (errorCallback != null)
				errorCallback.onError(new IllegalStateException(
						"document and lastError null"), uri);
		}
	}

	public static interface OnSuccessCallback {

		public void onSuccess(Document document);
	}

	public static interface OnErrorCallback {

		public void onError(Throwable error, Uri uri);
	}

	@SuppressWarnings("serial")
	public static class EncryptedDocumentException extends Exception {
	}
}
