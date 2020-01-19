package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

import com.viliussutkus89.android.wvware.wvWare;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import at.stefl.commons.math.vector.Vector2i;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentGraphics;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.document.BulkPresentationTranslator;
import at.stefl.opendocument.java.translator.document.BulkSpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslatorUtil;
import at.stefl.opendocument.java.translator.document.GraphicsTranslator;
import at.stefl.opendocument.java.translator.document.PresentationTranslator;
import at.stefl.opendocument.java.translator.document.SpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;
import at.tomtasche.reader.nonfree.AnalyticsManager;
import at.tomtasche.reader.nonfree.CrashManager;

public class DocLoader extends FileLoader {

    public DocLoader(Context context) {
        super(context, LoaderType.DOC);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/msword");
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            File cacheFile = AndroidFileCache.getCacheFile(context);
            File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

            wvWare docConverter = new wvWare(context).setInputDOC(cacheFile);
            if (options.password != null) {
                docConverter.setPassword(options.password);
            }

            File output = docConverter.convertToHTML();

            File htmlFile = new File(cacheDirectory, "doc.html");
            StreamUtil.copy(output, htmlFile);

            // library does not delete output files automatically
            output.delete();

            Uri finalUri = Uri.fromFile(htmlFile);

            result.partTitles.add(null);
            result.partUris.add(finalUri);

            callOnSuccess(result);
        } catch (Throwable e) {
            e.printStackTrace();

            if (e instanceof wvWare.PasswordRequiredException || e instanceof wvWare.WrongPasswordException) {
                e = new EncryptedDocumentException();
            }

            callOnError(result, e);
        }
    }
}
