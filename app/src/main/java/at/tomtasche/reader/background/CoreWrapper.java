package at.tomtasche.reader.background;

import java.util.LinkedList;
import java.util.List;

public class CoreWrapper {

    static {
        System.loadLibrary("odr-core");
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

            case -6:
                result.exception = new CoreCouldNotEditException();

            case -7:
                result.exception = new CoreCouldNotSaveException();

            default:
                result.exception = new CoreUnexpectedErrorCodeException();
        }

        return result;
    }

    private static native CoreResult backtranslateNative(CoreOptions options, String htmlDiff);

    public static void close() {
        CoreOptions options = new CoreOptions();

        closeNative(options);
    }

    private static native void closeNative(CoreOptions options);

    public static void createServer(String outputPath) {
        createServerNative(outputPath);
    }

    private static native void createServerNative(String outputPath);

    public static String hostFile(CoreOptions options) {
        return hostFileNative(options);
    }

    private static native String hostFileNative(CoreOptions options);

    public static void listenServer() {
        listenServerNative();
    }

    private static native void listenServerNative();

    public static void stopServer() {
        stopServerNative();
    }

    private static native void stopServerNative();

    public static class CoreOptions {

        public boolean ooxml;
        public boolean txt;

        public boolean editable;

        public boolean paging;

        public String password;

        public String inputPath;
        public String outputPath;
    }

    public static class CoreResult {

        public int errorCode;

        public Exception exception;

        public List<String> pageNames = new LinkedList<>();
        public List<String> pagePaths = new LinkedList<>();

        public String outputPath;

        public String extension;
    }

    public static class CoreCouldNotOpenException extends RuntimeException {}

    public static class CoreEncryptedException extends RuntimeException {}

    public static class CoreCouldNotTranslateException extends RuntimeException {}

    public static class CoreUnexpectedFormatException extends RuntimeException {}

    public static class CoreUnexpectedErrorCodeException extends RuntimeException {}

    public static class CoreUnknownErrorException extends RuntimeException {}

    public static class CoreCouldNotEditException extends RuntimeException {}

    public static class CoreCouldNotSaveException extends RuntimeException {}

}
