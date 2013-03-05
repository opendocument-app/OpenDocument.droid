package at.tomtasche.reader.background;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;
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

public class DocumentLoader extends AsyncTaskLoader<Document> {

	public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

	private Throwable lastError;
	private Uri uri;
	private String password;
	private Document document;

	public DocumentLoader(Context context, Uri uri) {
		super(context);

		this.uri = uri;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Throwable getLastError() {
		return lastError;
	}

	public Uri getLastUri() {
		return uri;
	}

	@Override
	protected void onStartLoading() {
		super.onStartLoading();

		if (document != null && lastError == null) {
			deliverResult(document);
		} else {
			forceLoad();
		}
	}

	@Override
	protected void onReset() {
		super.onReset();

		onStopLoading();

		document = null;
		lastError = null;
	}

	@Override
	protected void onStopLoading() {
		super.onStopLoading();

		cancelLoad();
	}

	@Override
	public Document loadInBackground() {
		try {
			// cleanup uri
			if ("/./".equals(uri.toString().substring(0, 2))) {
				uri = Uri.parse(uri.toString().substring(2,
						uri.toString().length()));
			}

			// TODO: don't delete file being displayed at the moment, but keep
			// it until the new document has finished loading
			AndroidFileCache.cleanup(getContext());

			InputStream stream;
			if (URI_INTRO.equals(uri)) {
				stream = getContext().getAssets().open("intro.odt");
			} else {
				stream = getContext().getContentResolver().openInputStream(uri);
			}

			try {
				RecentDocumentsUtil.addRecentDocument(getContext(),
						uri.getLastPathSegment(), uri);
			} catch (IOException e) {
				// not a showstopper, so just continue
				e.printStackTrace();
			}

			AndroidFileCache cache = new AndroidFileCache(getContext());
			TemporaryOpenDocumentFile documentFile = new TemporaryOpenDocumentFile(
					stream, cache);

			String mimeType = documentFile.getMimetype();
			if (!OpenDocument.checkMimetype(mimeType)) {
				throw new IllegalMimeTypeException();
			}

			if (documentFile.isEncrypted() && password == null) {
				throw new EncryptedDocumentException();
			} else if (password != null) {
				documentFile.setPassword(password);
			}

			document = new Document();
			OpenDocument openDocument = documentFile.getAsOpenDocument();
			if (openDocument instanceof OpenDocumentText) {
				File htmlFile = cache.getFile("temp.html");
				FileWriter fileWriter = new FileWriter(htmlFile);
				BufferedWriter writer = new BufferedWriter(fileWriter);
				LWXMLWriter out = new LWXMLStreamWriter(writer);
				try {
					TextTranslator translator = new TextTranslator(cache);
					translator.translate(openDocument, out);
				} finally {
					out.close();
					writer.close();
					fileWriter.close();
				}

				document.addPage(new Part("Document", htmlFile.toURI(), 0));
			} else if (openDocument instanceof OpenDocumentSpreadsheet) {
				List<String> tableNames = new ArrayList<String>(
						((OpenDocumentSpreadsheet) openDocument).getTableMap()
								.keySet());
				SpreadsheetTranslator translator = new SpreadsheetTranslator(
						cache);
				int count = ((OpenDocumentSpreadsheet) openDocument)
						.getTableCount();
				for (int i = 0; i < count; i++) {
					File htmlFile = cache.getFile("temp" + i + ".html");
					FileWriter fileWriter = new FileWriter(htmlFile);
					BufferedWriter writer = new BufferedWriter(fileWriter);
					LWXMLWriter out = new LWXMLStreamWriter(writer);
					try {
						translator.translate(openDocument, out, i);

						document.addPage(new Part(tableNames.get(i), htmlFile
								.toURI(), i));
					} finally {
						out.close();
						writer.close();
						fileWriter.close();
					}
				}
			} else {
				throw new IllegalMimeTypeException(
						"I don't know what it is, but I can't stop parsing it");
			}

			return document;
		} catch (Throwable e) {
			e.printStackTrace();

			lastError = e;
		}

		return null;
	}

	@SuppressWarnings("serial")
	public static class EncryptedDocumentException extends Exception {
	}
}
