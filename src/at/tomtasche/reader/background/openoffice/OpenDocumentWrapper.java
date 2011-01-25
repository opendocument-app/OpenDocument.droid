
package at.tomtasche.reader.background.openoffice;

import openoffice.OpenDocument;
import openoffice.OpenDocumentSpreadsheet;
import openoffice.html.ods.TranslatorOds;
import openoffice.html.odt.TranslatorOdt;

public class OpenDocumentWrapper {

    private final OpenDocument document;

    private TranslatorOds ods;

    private TranslatorOdt odt;

    public OpenDocumentWrapper(final OpenDocument document) {
        this.document = document;
    }

    public void setOds(final TranslatorOds ods) {
        this.ods = ods;
    }

    public void setOdt(final TranslatorOdt odt) {
        this.odt = odt;
    }

    public String translate(final int i) {
        if (odt != null) {
            return odt.translate().getHtmlDocument().toString();
        } else if (ods != null) {
            return ods.translate(i).getHtmlDocument().toString();
        } else {
            return "Error.";
        }
    }

    public int getPageCount() {
        if (odt != null) {
            return 1;
        } else if (ods != null) {
            return ((OpenDocumentSpreadsheet)document).getTableCount();
        } else {
            return 0;
        }
    }
}
