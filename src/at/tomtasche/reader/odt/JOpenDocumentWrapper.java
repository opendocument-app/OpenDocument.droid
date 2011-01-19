
package at.tomtasche.reader.odt;

import java.io.File;
import java.io.InputStream;

import openoffice.CachedOpenDocumentFile;
import openoffice.OpenDocumentSpreadsheet;
import openoffice.OpenDocumentSpreadsheetTemplate;
import openoffice.OpenDocumentText;
import openoffice.OpenDocumentTextTemplate;
import openoffice.html.ImageCache;
import openoffice.html.ImageTranslator;
import openoffice.html.ods.TranslatorOds;
import openoffice.html.odt.TranslatorOdt;

public class JOpenDocumentWrapper {

    private final String html;

    public JOpenDocumentWrapper(final InputStream stream, final File cache) throws Exception {
        final ImageCache imageCache = new ImageCache(cache, false);

        final CachedOpenDocumentFile documentFile = new CachedOpenDocumentFile(stream);

        if (OpenDocumentText.MIMETYPE.equals(documentFile.getMimeType())
                || OpenDocumentTextTemplate.MIMETYPE.equals(documentFile.getMimeType())) {
            final OpenDocumentText text = new OpenDocumentText(documentFile);
            final TranslatorOdt translatorOdt = new TranslatorOdt(text);

            final ImageTranslator imageTranslator = new ImageTranslator(text, imageCache);
            imageTranslator.setUriTranslator(new AndroidImageUriTranslator());
            translatorOdt.addNodeTranslator("image", imageTranslator);

            html = translatorOdt.translate(0).getHtmlDocument().toString();
        } else if (OpenDocumentSpreadsheet.MIMETYPE.equals(documentFile.getMimeType())
                || OpenDocumentSpreadsheetTemplate.MIMETYPE.equals(documentFile.getMimeType())) {
            final OpenDocumentSpreadsheet spreadsheet = new OpenDocumentSpreadsheet(documentFile);
            final TranslatorOds translatorOds = new TranslatorOds(spreadsheet);

            html = translatorOds.translate().getHtmlDocument().toString();
        } else {
            html = "Error.";
        }
    }

    public String getHtml() {
        return html;
    }
}
