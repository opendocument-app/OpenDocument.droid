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
import at.andiwand.commons.lwxml.writer.LWXMLMultiWriter;
import at.andiwand.commons.lwxml.writer.LWXMLStreamWriter;
import at.andiwand.commons.lwxml.writer.LWXMLWriter;
import at.andiwand.commons.math.vector.Vector2i;
import at.andiwand.odf2html.odf.IllegalMimeTypeException;
import at.andiwand.odf2html.odf.OpenDocument;
import at.andiwand.odf2html.odf.OpenDocumentPresentation;
import at.andiwand.odf2html.odf.OpenDocumentSpreadsheet;
import at.andiwand.odf2html.odf.OpenDocumentText;
import at.andiwand.odf2html.odf.TemporaryOpenDocumentFile;
import at.andiwand.odf2html.translator.document.BulkDocumentTranslator;
import at.andiwand.odf2html.translator.document.BulkPresentationTranslator;
import at.andiwand.odf2html.translator.document.BulkSpreadsheetTranslator;
import at.andiwand.odf2html.translator.document.DocumentTranslator;
import at.andiwand.odf2html.translator.document.PresentationTranslator;
import at.andiwand.odf2html.translator.document.SpreadsheetTranslator;
import at.andiwand.odf2html.translator.document.TextTranslator;
import at.tomtasche.reader.background.Document.Page;

public class DocumentLoader extends AsyncTaskLoader<Document> implements
	FileLoader {

    public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

    private Throwable lastError;
    private Uri uri;
    private String password;
    private Document document;
    private DocumentTranslator<?> translator;

    public DocumentLoader(Context context, Uri uri) {
	super(context);

	this.uri = uri;
    }

    public void setPassword(String password) {
	this.password = password;
    }

    @Override
    public Throwable getLastError() {
	return lastError;
    }

    @Override
    public Uri getLastUri() {
	return uri;
    }

    @Override
    public double getProgress() {
	if (translator != null)
	    return translator.getProgress();

	return 0;
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
	uri = null;
	translator = null;
	password = null;
    }

    @Override
    protected void onStopLoading() {
	super.onStopLoading();

	cancelLoad();
    }

    @Override
    public Document loadInBackground() {
	InputStream stream = null;
	TemporaryOpenDocumentFile documentFile = null;
	try {
	    // cleanup uri
	    if ("/./".equals(uri.toString().substring(0, 2))) {
		uri = Uri.parse(uri.toString().substring(2,
			uri.toString().length()));
	    }

	    // TODO: don't delete file being displayed at the moment, but keep
	    // it until the new document has finished loading
	    AndroidFileCache.cleanup(getContext());

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
	    documentFile = new TemporaryOpenDocumentFile(stream, cache);

	    String mimeType = documentFile.getMimetype();
	    if (!OpenDocument.checkMimetype(mimeType)) {
		throw new IllegalMimeTypeException();
	    }

	    if (documentFile.isEncrypted() && password == null) {
		throw new EncryptedDocumentException();
	    } else if (password != null) {
		if (!documentFile.isPasswordValid(password))
		    throw new EncryptedDocumentException();

		documentFile.setPassword(password);
	    }

	    document = new Document();
	    OpenDocument openDocument = documentFile.getAsDocument();
	    if (openDocument instanceof OpenDocumentText) {
		File htmlFile = cache.getFile("temp.html");
		FileWriter fileWriter = new FileWriter(htmlFile);
		BufferedWriter writer = new BufferedWriter(fileWriter);
		LWXMLWriter out = new LWXMLStreamWriter(writer);
		try {
		    TextTranslator textTranslator = new TextTranslator(cache);
		    this.translator = textTranslator;
		    textTranslator.translate(openDocument, out);
		} finally {
		    out.close();
		    writer.close();
		    fileWriter.close();
		}

		document.addPage(new Page("Document", htmlFile.toURI(), 0));
	    } else {
		List<String> pageNames = null;
		int count = 0;
		if (openDocument instanceof OpenDocumentSpreadsheet) {
		    OpenDocumentSpreadsheet spreadsheet = openDocument
			    .getAsSpreadsheet();

		    SpreadsheetTranslator spreadsheetTranslator = new SpreadsheetTranslator(
			    cache);
		    spreadsheetTranslator.setMaxTableDimension(new Vector2i(
			    300, 50));

		    translator = new BulkSpreadsheetTranslator(
			    spreadsheetTranslator);

		    count = spreadsheet.getTableCount();
		    pageNames = new ArrayList<String>(
			    spreadsheet.getTableNames());
		} else if (openDocument instanceof OpenDocumentPresentation) {
		    OpenDocumentPresentation presentation = documentFile
			    .getAsPresentation();

		    PresentationTranslator presentationTranslator = new PresentationTranslator(
			    cache);

		    translator = new BulkPresentationTranslator(
			    presentationTranslator);

		    count = presentation.getPageCount();
		    pageNames = new ArrayList<String>(
			    presentation.getPageNames());
		}

		LWXMLMultiWriter writer = null;
		try {
		    writer = ((BulkDocumentTranslator<?>) translator)
			    .provideOutput(openDocument, "temp", ".html");
		    translator.translate(openDocument, writer);
		} finally {
		    if (writer != null)
			writer.close();
		}

		for (int i = 0; i < count; i++) {
		    File htmlFile = cache.getFile("temp" + i + ".html");
		    document.addPage(new Page(pageNames.get(i), htmlFile
			    .toURI(), i));
		}
	    }

	    return document;
	} catch (Throwable e) {
	    e.printStackTrace();

	    lastError = e;
	} finally {
	    try {
		if (stream != null)
		    stream.close();
	    } catch (IOException e) {
	    }

	    try {
		if (documentFile != null)
		    documentFile.close();
	    } catch (IOException e) {
	    }
	}

	return null;
    }

    @SuppressWarnings("serial")
    public static class EncryptedDocumentException extends Exception {
    }
}
