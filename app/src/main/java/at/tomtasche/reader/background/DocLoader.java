package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import com.viliussutkus89.android.wvware.wvWare;

import java.io.File;

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

            options.fileType = "application/msword";

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
