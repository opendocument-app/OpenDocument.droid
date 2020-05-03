package at.tomtasche.reader.background;

import java.util.LinkedList;
import java.util.List;

public class CoreWrapper {

    private long lastNativePointer;

    public void initialize() {
        System.loadLibrary("odr-core");
    }

    public CoreResult parse(CoreOptions options) {
        options.nativePointer = lastNativePointer;

        CoreResult result = parseNative(options);
        lastNativePointer = result.nativePointer;

        switch (result.errorCode) {
            case 0:
                break;

            case -1:
                result.exception = new CoreCouldNotOpenException();

            case -2:
                result.exception = new CoreEncryptedException();

            case -3:
                result.exception = new CoreUnknownErrorException();

            case -4:
                result.exception = new CoreCouldNotTranslateException();

            case -5:
                result.exception = new CoreUnexpectedFormatException();

            default:
                result.exception = new CoreUnexpectedErrorCodeException();
        }

        return result;
    }

    private native CoreResult parseNative(CoreOptions options);

    public CoreResult backtranslate(CoreOptions options, String htmlDiff) {
        options.nativePointer = lastNativePointer;

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
        options.nativePointer = lastNativePointer;

        closeNative(options);

        lastNativePointer = 0;
    }

    private native void closeNative(CoreOptions options);

    public static class CoreOptions {

        long nativePointer;

        boolean ooxml;

        boolean editable;

        String password;

        String inputPath;
        String outputPath;
    }

    public static class CoreResult {

        long nativePointer;

        int errorCode;

        Exception exception;

        List<String> pageNames = new LinkedList<>();

        String outputPath;

        String extension;
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
