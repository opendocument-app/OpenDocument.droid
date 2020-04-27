package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;

public class OdfLoader extends FileLoader {

    private CoreWrapper lastCore;
    private CoreWrapper.CoreOptions lastCoreOptions;

    public OdfLoader(Context context) {
        super(context, LoaderType.ODF);
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument") || options.fileType.startsWith("application/x-vnd.oasis.opendocument");
    }

    @Override
    public void loadSync(Options options) {
        final Result result = new Result();
        result.options = options;
        result.loaderType = type;

        try {
            File cachedFile = AndroidFileCache.getCacheFile(context);

            if (lastCore != null) {
                lastCore.close();
            }

            CoreWrapper core = new CoreWrapper();
            try {
                core.initialize();

                lastCore = core;
            } catch (Throwable e) {
                crashManager.log(e);
            }

            File cacheDirectory = AndroidFileCache.getCacheDirectory(context);

            File fakeHtmlFile = new File(cacheDirectory, "odf");

            CoreWrapper.CoreOptions coreOptions = new CoreWrapper.CoreOptions();
            coreOptions.inputPath = cachedFile.getPath();
            coreOptions.outputPath = fakeHtmlFile.getPath();
            coreOptions.password = options.password;
            coreOptions.editable = options.translatable;
            coreOptions.ooxml = false;

            lastCoreOptions = coreOptions;

            CoreWrapper.CoreResult coreResult = lastCore.parse(coreOptions);

            String coreExtension = coreResult.extension;
            if (coreExtension != null && !coreExtension.equals("unnamed")) {
                String fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(coreExtension);
                if (fileType != null) {
                    options.fileType = fileType;
                }
            }

            if (coreResult.errorCode == 0) {
                for (int i = 0; i < coreResult.pageNames.size(); i++) {
                    File entryFile = new File(fakeHtmlFile.getPath() + i + ".html");

                    result.partTitles.add(coreResult.pageNames.get(i));
                    result.partUris.add(Uri.fromFile(entryFile));
                }

                callOnSuccess(result);
            } else {
                if (coreResult.errorCode == -2) {
                    throw new EncryptedDocumentException();
                } else {
                    throw new RuntimeException("failed with code " + coreResult.errorCode);
                }
            }
        } catch (Throwable e) {
            callOnError(result, e);
        }
    }

    @Override
    public File retranslate(String htmlDiff) {
        File cacheDirectory = AndroidFileCache.getCacheDirectory(context);
        File tempFilePrefix = new File(cacheDirectory, "retranslate");

        lastCoreOptions.outputPath = tempFilePrefix.getPath();

        CoreWrapper.CoreResult result = lastCore.backtranslate(lastCoreOptions, htmlDiff);
        if (result.errorCode != 0) {
            crashManager.log(new RuntimeException("could not retranslate file with error " + result.errorCode));
            return null;
        }

        return new File(result.outputPath);
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
