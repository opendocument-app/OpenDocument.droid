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

        return result;
    }

    private native CoreResult parseNative(CoreOptions options);

    public CoreResult backtranslate(CoreOptions options, String htmlDiff) {
        options.nativePointer = lastNativePointer;

        return backtranslateNative(options, htmlDiff);
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

        boolean editable;

        String password;

        String inputPath;
        String outputPath;
    }

    public static class CoreResult {

        long nativePointer;

        int errorCode;

        List<String> pageNames = new LinkedList<>();

        String outputPath;
    }
}
