package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.viliussutkus89.android.pdf2htmlex.pdf2htmlEX;

import java.io.File;
import java.io.InputStream;

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
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            InputStream stream = context.getContentResolver().openInputStream(options.originalUri);
            File cachedFile = new AndroidFileCache(context).create("document.odt", stream);

            File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

            pdf2htmlEX pdfConverter = new pdf2htmlEX(context).setInputPDF(cachedFile);
            if (options.password != null) {
                pdfConverter.setOwnerPassword(options.password).setUserPassword(options.password);
            }

            /* run with this code first: */
            File output = pdfConverter.convert();

            File htmlFile = new File(cacheDirectory, "pdf.html");
            StreamUtil.copy(output, htmlFile);

            // pdf2htmlEX does not delete output files automatically
            output.delete();

            /* should crash (retry one or two times if not).
            afterwards comment out previous code and use this instead: */
            // File htmlFile = new File(cacheDirectory, "pdf.html");

            /* should not crash (uses previously generated result - pdf2htmlEX not ran)! */

            Uri finalUri = Uri.fromFile(htmlFile);

            result.partTitles.add(null);
            result.partUris.add(finalUri);

            Log.e("smn", "success");

            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            Log.e("smn", "fail", e);

            callOnError(result, e);
        }
    }
}
