package at.tomtasche.reader.ui;

/**
 * Release variant: does nothing. The debug variant backs these calls with an espresso
 * CountingIdlingResource; keeping the espresso dependency out of release builds is the whole point
 * of the split.
 */
public final class OpenFileIdling {

    private OpenFileIdling() {}

    public static void increment() {}

    public static void decrement() {}
}
