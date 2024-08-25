package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.viliussutkus89.android.assetextractor.AssetExtractor;

import java.io.File;

import app.opendocument.android.pdf2htmlex.EnvVar;

public class DocLoader extends FileLoader {

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public DocLoader(Context context) {
        super(context, LoaderType.DOC);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/msword");
    }

    public void loadSync(Options options) {
        AssetExtractor ae = new AssetExtractor(context.getAssets()).setNoOverwrite();

        // @TODO: use asset files without extracting
        File wv_data_dir = ae.extract(context.getFilesDir(), "wv/share/wv");
        EnvVar.set("WVDATADIR", wv_data_dir.getAbsolutePath());

        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            translate(options, result);
            callOnSuccess(result);
        } catch (Throwable e) {
            if (e instanceof CoreWrapper.CoreEncryptedException) {
                e = new EncryptedDocumentException();
            }

            callOnError(result, e);
        }
    }

    private void translate(Options options, Result result) throws Exception {
        File cachedFile = AndroidFileCache.getCacheFile(context, options.cacheUri);

        if (lastCore != null) {
            lastCore.close();
            lastCore = null;
        }

        CoreWrapper core = new CoreWrapper();
        try {
            core.initialize();

            lastCore = core;
        } catch (Throwable e) {
            crashManager.log(e);
        }

        File cacheDirectory = AndroidFileCache.getCacheDirectory(cachedFile);

        CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
        coreOptions.inputPath = cachedFile.getPath();
        coreOptions.outputPath = cacheDirectory.getPath();
        coreOptions.password = options.password;
        coreOptions.editable = options.translatable;
        coreOptions.ooxml = false;
        coreOptions.wvWare = true;

        lastCoreOptions = coreOptions;

        CoreWrapper.CoreResult coreResult = lastCore.parse(coreOptions);

        String coreExtension = coreResult.extension;
        // "unnamed" refers to default of Meta::typeToString
        if (coreExtension != null && !coreExtension.equals("unnamed")) {
            options.fileExtension = coreExtension;

            String fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(coreExtension);
            if (fileType != null) {
                options.fileType = fileType;
            }
        }

        if (coreResult.exception != null) {
            throw coreResult.exception;
        }

        for (int i = 0; i < coreResult.pagePaths.size(); i++) {
            File entryFile = new File(coreResult.pagePaths.get(i));

            result.partTitles.add(coreResult.pageNames.get(i));
            result.partUris.add(Uri.fromFile(entryFile));
        }
    }

    @Override
    public void close() {
        super.close();

        if (lastCore != null) {
            lastCore.close();
            lastCore = null;
        }
    }
}
