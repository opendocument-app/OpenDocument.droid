package app.opendocument.android.pdf2htmlex;

final class EnvVar {
    static {
        System.loadLibrary("envvar");
    }
    public static native void set(String key, String value);
}
