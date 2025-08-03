package at.tomtasche.reader.background;

import android.content.Context;

import com.viliussutkus89.android.assetextractor.AssetExtractor;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class CoreWrapper {
    static {
        System.loadLibrary("odr-core");
    }

    public static class GlobalParams {
        public String coreDataPath;
        public String fontconfigDataPath;
        public String popplerDataPath;
        public String pdf2htmlexDataPath;
    }

    public static native void setGlobalParams(GlobalParams params);

    public static void initialize(Context context) {
        File assetsDirectory = new File(context.getFilesDir(), "assets");
        File odrCoreDataDirectory = new File(assetsDirectory, "odrcore");
        File fontconfigDataDirectory = new File(assetsDirectory, "fontconfig");
        File popplerDataDirectory = new File(assetsDirectory, "poppler");
        File pdf2htmlexDataDirectory = new File(assetsDirectory, "pdf2htmlex");

        AssetExtractor ae = new AssetExtractor(context.getAssets());
        ae.setOverwrite();
        ae.extract(assetsDirectory, "core/odrcore");
        ae.extract(assetsDirectory, "core/fontconfig");
        ae.extract(assetsDirectory, "core/poppler");
        ae.extract(assetsDirectory, "core/pdf2htmlex");

        CoreWrapper.GlobalParams globalParams = new CoreWrapper.GlobalParams();
        globalParams.coreDataPath = odrCoreDataDirectory.getAbsolutePath();
        globalParams.fontconfigDataPath = fontconfigDataDirectory.getAbsolutePath();
        globalParams.popplerDataPath = popplerDataDirectory.getAbsolutePath();
        globalParams.pdf2htmlexDataPath = pdf2htmlexDataDirectory.getAbsolutePath();
        CoreWrapper.setGlobalParams(globalParams);
    }

    public static class CoreOptions {
        public boolean ooxml;
        public boolean txt;
        public boolean pdf;

        public boolean editable;

        public boolean paging;

        public String password;

        public String inputPath;
        public String outputPath;
        public String cachePath;
    }

    public static CoreResult parse(CoreOptions options) {
        CoreResult result = parseNative(options);

        switch (result.errorCode) {
            case 0:
                break;
            case -1:
                result.exception = new CoreCouldNotOpenException();
                break;
            case -2:
                result.exception = new CoreEncryptedException();
                break;
            case -3:
                result.exception = new CoreUnknownErrorException();
                break;
            case -4:
                result.exception = new CoreCouldNotTranslateException();
                break;
            case -5:
                result.exception = new CoreUnexpectedFormatException();
                break;
            default:
                result.exception = new CoreUnexpectedErrorCodeException();
                break;
        }

        return result;
    }

    private static native CoreResult parseNative(CoreOptions options);

    public static CoreResult backtranslate(CoreOptions options, String htmlDiff) {
        CoreResult result = backtranslateNative(options, htmlDiff);

        switch (result.errorCode) {
            case 0:
                break;
            case -3:
                result.exception = new CoreUnknownErrorException();
                break;
            case -6:
                result.exception = new CoreCouldNotEditException();
                break;
            case -7:
                result.exception = new CoreCouldNotSaveException();
                break;
            default:
                result.exception = new CoreUnexpectedErrorCodeException();
                break;
        }

        return result;
    }

    private static native CoreResult backtranslateNative(CoreOptions options, String htmlDiff);

    public static void close() {
        CoreOptions options = new CoreOptions();

        closeNative(options);
    }

    private static native void closeNative(CoreOptions options);

    public static void createServer(String cachePath) {
        createServerNative(cachePath);
    }

    private static native void createServerNative(String cachePath);

    public static CoreResult hostFile(String prefix, CoreOptions options) {
        CoreResult result = hostFileNative(prefix, options);

        switch (result.errorCode) {
            case 0:
                break;
            case -1:
                result.exception = new CoreCouldNotOpenException();
                break;
            case -2:
                result.exception = new CoreEncryptedException();
                break;
            case -3:
                result.exception = new CoreUnknownErrorException();
                break;
            case -4:
                result.exception = new CoreCouldNotTranslateException();
                break;
            case -5:
                result.exception = new CoreUnexpectedFormatException();
                break;
            default:
                result.exception = new CoreUnexpectedErrorCodeException();
                break;
        }

        return result;
    }

    private static native CoreResult hostFileNative(String prefix, CoreOptions options);

    public static void listenServer(int port) {
        listenServerNative(port);
    }

    private static native void listenServerNative(int port);

    public static void stopServer() {
        stopServerNative();
    }

    private static native void stopServerNative();

    public static class CoreResult {
        public int errorCode;

        public Exception exception;

        public List<String> pageNames = new LinkedList<>();
        public List<String> pagePaths = new LinkedList<>();

        public String outputPath;

        public String extension;
    }

    public static class CoreCouldNotOpenException extends RuntimeException {
    }

    public static class CoreEncryptedException extends RuntimeException {
    }

    public static class CoreCouldNotTranslateException extends RuntimeException {
    }

    public static class CoreUnexpectedFormatException extends RuntimeException {
    }

    public static class CoreUnexpectedErrorCodeException extends RuntimeException {
    }

    public static class CoreUnknownErrorException extends RuntimeException {
    }

    public static class CoreCouldNotEditException extends RuntimeException {
    }

    public static class CoreCouldNotSaveException extends RuntimeException {
    }
}
