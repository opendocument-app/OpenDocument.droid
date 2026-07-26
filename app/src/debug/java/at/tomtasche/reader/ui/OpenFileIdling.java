package at.tomtasche.reader.ui;

import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;

/**
 * Debug variant: a real espresso idling resource, so instrumented tests can wait for the document
 * picker round trip. The release variant of this class does nothing, which keeps espresso out of
 * the shipped apk.
 */
public final class OpenFileIdling {

    private static final CountingIdlingResource RESOURCE =
            new CountingIdlingResource("MainActivity.openFileIdlingResource");

    private OpenFileIdling() {}

    public static IdlingResource getIdlingResource() {
        return RESOURCE;
    }

    public static void increment() {
        RESOURCE.increment();
    }

    public static void decrement() {
        if (!RESOURCE.isIdleNow()) {
            RESOURCE.decrement();
        }
    }
}
