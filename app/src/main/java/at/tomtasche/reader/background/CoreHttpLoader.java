package at.tomtasche.reader.background;

import android.content.Context;
import android.net.Uri;

import com.viliussutkus89.android.assetextractor.AssetExtractor;

import java.io.File;

import at.tomtasche.reader.nonfree.ConfigManager;

public class CoreHttpLoader extends FileLoader {

    private final ConfigManager configManager;

    private CoreWrapper lastCore;

    public CoreHttpLoader(Context context, ConfigManager configManager) {
        super(context, LoaderType.CORE);

        this.configManager = configManager;

        AssetExtractor ae = new AssetExtractor(context.getAssets());
        ae.setNoOverwrite();
        ae.extract(new File(context.getFilesDir(), "assets"), ".");
    }

    @Override
    public boolean isSupported(Options options) {
        return options.fileType.startsWith("application/vnd.oasis.opendocument") || options.fileType.startsWith("application/x-vnd.oasis.opendocument") || options.fileType.startsWith("application/vnd.oasis.opendocument.text-master");
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
            CoreWrapper.close();
            lastCore = null;
        }

        CoreWrapper core = new CoreWrapper();
        try {
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

        String id = CoreWrapper.hostFile(coreOptions);

        result.partTitles.add("document");
        result.partUris.add(Uri.parse("http://localhost:29665/" + id + "/document.html"));
    }

    @Override
    public File retranslate(Options options, String htmlDiff) {
        return null;
    }

    @Override
    public void close() {
        super.close();

        if (lastCore != null) {
            CoreWrapper.close();
            lastCore = null;
        }
    }
}
