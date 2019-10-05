package at.tomtasche.reader.background;

import android.content.Context;

public class PdfLoader extends FileLoader {

    private static final String[] MIME_WHITELIST = {
            // pdf: https://filext.com/file-extension/PDF
            "application/pdf", "application/x-pdf", "application/acrobat", "applications/vnd.pdf", "text/pdf", "text/x-pdf",
    };

    public PdfLoader(Context context) {
        super(context, LoaderType.PDF);
    }

    @Override
    public boolean isSupported(Options options) {
        String fileType = options.fileType;

        for (String mime : MIME_WHITELIST) {
            if (!fileType.startsWith(mime)) {
                continue;
            }

            return true;
        }

        return false;
    }

    @Override
    public void loadSync(Options options) {
        throw new RuntimeException("noop");
    }
}
