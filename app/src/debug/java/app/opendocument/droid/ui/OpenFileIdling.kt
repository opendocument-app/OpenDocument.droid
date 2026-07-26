package app.opendocument.droid.ui

import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource

/**
 * Debug variant: a real espresso idling resource, so instrumented tests can wait for the document
 * picker round trip. The release variant of this class does nothing, which keeps espresso out of
 * the shipped apk.
 */
object OpenFileIdling {

    private val RESOURCE = CountingIdlingResource("MainActivity.openFileIdlingResource")

    val idlingResource: IdlingResource
        get() = RESOURCE

    fun increment() {
        RESOURCE.increment()
    }

    fun decrement() {
        if (!RESOURCE.isIdleNow) {
            RESOURCE.decrement()
        }
    }
}
