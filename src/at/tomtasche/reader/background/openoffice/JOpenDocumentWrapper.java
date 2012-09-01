package at.tomtasche.reader.background.openoffice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import openoffice.CachedOpenDocumentFile;
import openoffice.MimeTypeNotFoundException;
import openoffice.OpenDocumentSpreadsheet;
import openoffice.OpenDocumentSpreadsheetTemplate;
import openoffice.OpenDocumentText;
import openoffice.OpenDocumentTextTemplate;
import openoffice.html.ImageCache;
import openoffice.html.ImageTranslator;
import openoffice.html.ods.TranslatorOds;
import openoffice.html.odt.TranslatorOdt;

import org.xml.sax.SAXException;

import at.tomtasche.reader.background.AndroidImageUriTranslator;
import at.tomtasche.reader.background.Document;
import at.tomtasche.reader.background.Document.Page;

public class JOpenDocumentWrapper {

    public static Document parseStream(InputStream stream, File cache) throws IOException,
	    ParserConfigurationException, SAXException, Exception {
	final ImageCache imageCache = new ImageCache(cache, false);

	final CachedOpenDocumentFile documentFile = new CachedOpenDocumentFile(stream);

	List<Page> pages = new ArrayList<Page>();
	if (isDocument(documentFile)) {
	    final OpenDocumentText text = new OpenDocumentText(documentFile);
	    final TranslatorOdt translatorOdt = new TranslatorOdt(text);

	    final ImageTranslator imageTranslator = new ImageTranslator(text, imageCache);
	    imageTranslator.setUriTranslator(new AndroidImageUriTranslator());
	    translatorOdt.addNodeTranslator("image", imageTranslator);

	    String html = translatorOdt.translate().getHtmlDocument().toString();
	    pages.add(new Page("Document", html, 0));
	} else if (isSpreadsheet(documentFile)) {
	    final OpenDocumentSpreadsheet spreadsheet = new OpenDocumentSpreadsheet(documentFile);
	    final TranslatorOds translatorOds = new TranslatorOds(spreadsheet);

	    final ImageTranslator imageTranslator = new ImageTranslator(spreadsheet, imageCache);
	    imageTranslator.setUriTranslator(new AndroidImageUriTranslator());
	    translatorOds.addNodeTranslator("image", imageTranslator);

	    List<String> tableNames = spreadsheet.getTableNames();
	    for (int i = 0; i < spreadsheet.getTableCount(); i++) {
		String html = translatorOds.translate(i).getHtmlDocument().toString();
		pages.add(new Page(tableNames.get(i), html, i));
	    }
	} else {
	    throw new MimeTypeNotFoundException();
	}

	return new Document(pages);
    }

    private static boolean isSpreadsheet(final CachedOpenDocumentFile file) throws IOException {
	return file.getMimeType().startsWith(OpenDocumentSpreadsheet.MIMETYPE)
		|| file.getMimeType().startsWith(OpenDocumentSpreadsheetTemplate.MIMETYPE);
    }

    private static boolean isDocument(final CachedOpenDocumentFile file) throws IOException {
	return file.getMimeType().startsWith(OpenDocumentText.MIMETYPE)
		|| file.getMimeType().startsWith(OpenDocumentTextTemplate.MIMETYPE);
    }
}
