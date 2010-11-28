
package at.tomtasche.reader.odt;

import java.io.File;
import java.io.InputStream;

import openOffice.OpenDocumentText;
import openOffice.html.ClassAttributeTranslator;
import openOffice.html.HtmlPageOdt;
import openOffice.html.ImageCache;
import openOffice.html.ImageTranslator;
import openOffice.html.NodeSubstitution;
import openOffice.html.StaticStyleSubstitution;
import openOffice.html.StyleNodeTranslator;
import openOffice.html.StyleSubstitution;
import openOffice.html.TableStyleNodeTranslator;
import openOffice.html.TranslatorOdt;

public class JOpenDocument {
    private final HtmlPageOdt pageOdt;

    public JOpenDocument(final InputStream stream, final File cache) throws Exception {
        final ImageCache imageCache = new ImageCache(cache, true);

        final OpenDocumentText documentText = new OpenDocumentText(stream);
        final TranslatorOdt translatorOdt = new TranslatorOdt(documentText);

        translatorOdt.addStyleNodeTranslator("text-properties", new StyleNodeTranslator(
                new StyleSubstitution("font-size", "font-size"), new StyleSubstitution(
                        "font-weight", "font-weight"), new StyleSubstitution("font-style",
                        "font-style"), new StaticStyleSubstitution("text-underline-style",
                        "text-decoration", "underline")));
        translatorOdt.addStyleNodeTranslator("table-properties", new TableStyleNodeTranslator(
                new StyleSubstitution("width", "width")));
        translatorOdt.addStyleNodeTranslator("table-column-properties", new StyleNodeTranslator(
                new StyleSubstitution("column-width", "width")));
        translatorOdt.addStyleNodeTranslator("table-cell-properties", new StyleNodeTranslator(
                new StyleSubstitution("padding", "padding"), new StyleSubstitution("border",
                        "border"), new StyleSubstitution("border-top", "border-top"),
                new StyleSubstitution("border-right", "border-right"), new StyleSubstitution(
                        "border-bottom", "border-bottom"), new StyleSubstitution("border-left",
                        "border-left")));

        translatorOdt.addNodeSubstitution(new NodeSubstitution("p", "p"));
        translatorOdt.addNodeSubstitution(new NodeSubstitution("h", "p"));
        translatorOdt.addNodeSubstitution(new NodeSubstitution("table", "table"));
        translatorOdt.addNodeSubstitution(new NodeSubstitution("table-row", "tr"));
        translatorOdt.addNodeSubstitution(new NodeSubstitution("table-cell", "td"));
        translatorOdt.addNodeSubstitution(new NodeSubstitution("frame", "span"));

        final ImageTranslator imageTranslator = new ImageTranslator(documentText, imageCache);
        imageTranslator.setUriTranslator(new AndroidImageUriTranslator());
        translatorOdt.addNodeTranslator("image", imageTranslator);

        translatorOdt.addAttributeTranslators("style-name", new ClassAttributeTranslator());

        pageOdt = translatorOdt.translate(0);
    }

    public String getDocument() {
        return pageOdt.getHtmlDocument().toString();
    }
}
