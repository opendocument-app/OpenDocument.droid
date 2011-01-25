
package at.tomtasche.reader.background.openoffice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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
import at.tomtasche.reader.background.DocumentInterface;
import at.tomtasche.reader.background.DocumentLoader;

public class JOpenDocumentWrapper implements DocumentInterface {

    private OpenDocumentWrapper wrapper;

    private final DocumentLoader loader;

    private int index;

    public JOpenDocumentWrapper(final DocumentLoader loader, final InputStream stream,
            final File cache) throws Exception {
        this.loader = loader;

        final ImageCache imageCache = new ImageCache(cache, false);

        final CachedOpenDocumentFile documentFile = new CachedOpenDocumentFile(stream);

        if (isDocument(documentFile)) {
            final OpenDocumentText text = new OpenDocumentText(documentFile);
            final TranslatorOdt translatorOdt = new TranslatorOdt(text);

            final ImageTranslator imageTranslator = new ImageTranslator(text, imageCache);
            imageTranslator.setUriTranslator(new AndroidImageUriTranslator());
            translatorOdt.addNodeTranslator("image", imageTranslator);

            wrapper = new OpenDocumentWrapper(text);
            wrapper.setOdt(translatorOdt);
        } else if (isSpreadsheet(documentFile)) {
            final OpenDocumentSpreadsheet spreadsheet = new OpenDocumentSpreadsheet(documentFile);
            final TranslatorOds translatorOds = new TranslatorOds(spreadsheet);

            wrapper = new OpenDocumentWrapper(spreadsheet);
            wrapper.setOds(translatorOds);
        } else {
            assert false : new MimeTypeNotFoundException();
        }

        loader.showDocument(wrapper.translate(getPageIndex()));
    }

    private boolean isSpreadsheet(final CachedOpenDocumentFile file) throws IOException {

        return file.getMimeType().startsWith(OpenDocumentSpreadsheet.MIMETYPE)
                || file.getMimeType().startsWith(OpenDocumentSpreadsheetTemplate.MIMETYPE);

    }

    private boolean isDocument(final CachedOpenDocumentFile file) throws IOException {

        return file.getMimeType().startsWith(OpenDocumentText.MIMETYPE)
                || file.getMimeType().startsWith(OpenDocumentTextTemplate.MIMETYPE);

    }

    @Override
    public boolean hasNext() {
        if (getPageIndex() + 1 < getPageCount() && getPageIndex() >= 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void getNext() {
        loader.showProgress();

        loader.showDocument(wrapper.translate(++index));
    }

    @Override
    public boolean hasPrevious() {
        if (getPageIndex() - 1 >= 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void getPrevious() {
        loader.showProgress();

        loader.showDocument(wrapper.translate(--index));
    }

    @Override
    public int getPageCount() {
        return wrapper.getPageCount();
    }

    @Override
    public int getPageIndex() {
        return index;
    }
}
