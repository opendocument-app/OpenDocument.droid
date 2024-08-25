package at.tomtasche.reader.background;

import java.util.LinkedList;
import java.util.List;

public class CoreWrapper {

    public void initialize() {
        System.loadLibrary("odr-core");
    }

    public CoreResult parse(CoreOptions options) {
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

    private native CoreResult parseNative(CoreOptions options);

    public CoreResult backtranslate(CoreOptions options, String htmlDiff) {
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

    private native CoreResult backtranslateNative(CoreOptions options, String htmlDiff);

    public void close() {
        CoreOptions options = new CoreOptions();

        closeNative(options);
    }

    private native void closeNative(CoreOptions options);

    public static class CoreOptions {

        public boolean ooxml;
        public boolean txt;
        public boolean pdf2htmlEX;
        public boolean wvWare;

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

    public class CoreCouldNotOpenException extends RuntimeException {}

    public class CoreEncryptedException extends RuntimeException {}

    public class CoreCouldNotTranslateException extends RuntimeException {}

    public class CoreUnexpectedFormatException extends RuntimeException {}

    public class CoreUnexpectedErrorCodeException extends RuntimeException {}

    public class CoreUnknownErrorException extends RuntimeException {}

    public class CoreCouldNotEditException extends RuntimeException {}

    public class CoreCouldNotSaveException extends RuntimeException {}

}
