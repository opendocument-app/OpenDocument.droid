package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.viliussutkus89.android.assetextractor.AssetExtractor;

import java.io.File;

import app.opendocument.android.pdf2htmlex.EnvVar;
import app.opendocument.android.pdf2htmlex.FontconfigAndroid;
import at.tomtasche.reader.nonfree.ConfigManager;

public class PdfLoader extends FileLoader {

    private final ConfigManager configManager;

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public PdfLoader(Context context, ConfigManager configManager) {
        super(context, LoaderType.PDF);

        this.configManager = configManager;
    }

    @Override
    public boolean isSupported(Options options) {
        // pdf: https://filext.com/file-extension/PDF
        return options.fileType.startsWith("application/pdf") || options.fileType.startsWith("application/x-pdf") || options.fileType.startsWith("application/acrobat") || options.fileType.startsWith("applications/vnd.pdf") || options.fileType.startsWith("text/pdf") || options.fileType.startsWith("text/x-pdf");
    }

    @Override
    public void loadSync(Options options) {
        AssetExtractor ae = new AssetExtractor(context.getAssets()).setNoOverwrite();

        File cacheDir = new File(context.getCacheDir(), "pdf2htmlEX");
        cacheDir.mkdir();

        File envTMPDIR = new File(cacheDir, "tmp");
        envTMPDIR.mkdir();
        EnvVar.set("TMPDIR", envTMPDIR.getAbsolutePath());

        File fontforgeHome = new File(cacheDir, "FontforgeHome");
        fontforgeHome.mkdir();
        EnvVar.set("HOME", fontforgeHome.getAbsolutePath());

        File shareDir = new File(context.getFilesDir(), "share");
        // @TODO: https://github.com/ViliusSutkus89/pdf2htmlEX-Android/issues/9
        File pdf2htmlEX_dataDir = ae.extract(shareDir, "pdf2htmlEX");
        EnvVar.set("PDF2HTMLEX_DATA_DIR", pdf2htmlEX_dataDir.getAbsolutePath());

        // @TODO: https://github.com/ViliusSutkus89/pdf2htmlEX-Android/issues/10
        File poppler_dataDir = ae.extract(shareDir, "poppler-data");
        EnvVar.set("POPPLER_DATA_DIR", poppler_dataDir.getAbsolutePath());

        FontconfigAndroid.init(context.getAssets(), context.getCacheDir(), context.getFilesDir());

        EnvVar.set("USER", android.os.Build.MODEL);

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

        Boolean usePaging = configManager.getBooleanConfig("use_paging");
        if (usePaging == null || usePaging) {
            coreOptions.paging = true;
        }

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

//    @Override
//    public File retranslate(Options options, String htmlDiff) {
//        if (lastCore == null) {
//            // necessary if fragment was destroyed in the meanwhile - meaning the Loader is reinstantiated
//
//            Result result = new Result();
//            result.options = options;
//
//            try {
//                translate(options, result);
//            } catch (Exception e) {
//                crashManager.log(e);
//
//                return null;
//            }
//        }
//
//        File inputFile = new File(lastCoreOptions.inputPath);
//        File inputCacheDirectory = AndroidFileCache.getCacheDirectory(inputFile);
//        File tempFilePrefix = new File(inputCacheDirectory, "retranslate");
//
//        lastCoreOptions.outputPath = tempFilePrefix.getPath();
//
//        try {
//            CoreWrapper.CoreResult result = lastCore.backtranslate(lastCoreOptions, htmlDiff);
//
//            return new File(result.outputPath);
//        } catch (Throwable e) {
//            crashManager.log(e);
//
//            return null;
//        }
//    }

    @Override
    public void close() {
        super.close();

        if (lastCore != null) {
            lastCore.close();
            lastCore = null;
        }
    }
}
