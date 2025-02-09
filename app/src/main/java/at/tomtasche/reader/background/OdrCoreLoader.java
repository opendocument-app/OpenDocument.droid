package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;

import at.tomtasche.reader.nonfree.ConfigManager;

public class OdrCoreLoader extends FileLoader {

    private final ConfigManager configManager;

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    private final boolean doOoxml;

    public OdrCoreLoader(Context context, ConfigManager configManager, boolean doOOXML) {
        super(context, LoaderType.CORE);

        this.configManager = configManager;
        this.doOoxml = doOOXML;
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument") ||
                options.fileType.startsWith("application/x-vnd.oasis.opendocument") ||
                options.fileType.startsWith("application/vnd.oasis.opendocument.text-master") ||
                (this.doOoxml && (
                        options.fileType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        // TODO: enable xlsx and pptx too
                        //options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                        //options.fileType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.presentation");
                ));
    }

    @Override
    public void loadSync(Options options) {
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
        coreOptions.ooxml = doOoxml;

        Boolean usePaging = configManager.getBooleanConfig("use_paging");
        if (usePaging == null || usePaging) {
            coreOptions.paging = true;
        }

        lastCoreOptions = coreOptions;

        CoreWrapper.CoreResult coreResult = lastCore.parse(coreOptions);

        String coreExtension = coreResult.extension;
        if (coreResult.exception == null && "pdf".equals(coreExtension)) {
            // some PDFs do not cause an error in the core
            // https://github.com/opendocument-app/OpenDocument.droid/issues/348#issuecomment-2446888981
            throw new CoreWrapper.CoreCouldNotTranslateException();
        } else if (!"unnamed".equals(coreExtension)) {
            // "unnamed" refers to default of Meta::typeToString
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
    public File retranslate(Options options, String htmlDiff) {
        if (lastCore == null) {
            // necessary if fragment was destroyed in the meanwhile - meaning the Loader is reinstantiated

            Result result = new Result();
            result.options = options;

            try {
                translate(options, result);
            } catch (Exception e) {
                crashManager.log(e);

                return null;
            }
        }

        File inputFile = new File(lastCoreOptions.inputPath);
        File inputCacheDirectory = AndroidFileCache.getCacheDirectory(inputFile);
        File tempFilePrefix = new File(inputCacheDirectory, "retranslate");

        lastCoreOptions.outputPath = tempFilePrefix.getPath();

        try {
            CoreWrapper.CoreResult result = lastCore.backtranslate(lastCoreOptions, htmlDiff);

            return new File(result.outputPath);
        } catch (Throwable e) {
            crashManager.log(e);

            return null;
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
