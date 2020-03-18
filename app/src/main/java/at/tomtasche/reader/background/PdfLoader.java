package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.viliussutkus89.android.pdf2htmlex.pdf2htmlEX;
import com.viliussutkus89.android.wvware.wvWare;

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
            File cacheFile = AndroidFileCache.getCacheFile(context);
            File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

            pdf2htmlEX pdfConverter = new pdf2htmlEX(context).setInputPDF(cacheFile);
            pdfConverter.setOutline(false).setBackgroundFormat("jpg").setDRM(false);
            if (options.password != null) {
                pdfConverter.setOwnerPassword(options.password).setUserPassword(options.password);
            }

            File output = pdfConverter.convert();

            File htmlFile = new File(cacheDirectory, "pdf.html");
            StreamUtil.copy(output, htmlFile);

            // pdf2htmlEX does not delete output files automatically
            output.delete();

            Uri finalUri = Uri.fromFile(htmlFile);

            options.fileType = "application/pdf";

            result.partTitles.add(null);
            result.partUris.add(finalUri);

            callOnSuccess(result);
        } catch (Throwable e) {
            if (e instanceof pdf2htmlEX.PasswordRequiredException || e instanceof pdf2htmlEX.WrongPasswordException) {
                e = new EncryptedDocumentException();
            }

            callOnError(result, e);
        }
    }
}
