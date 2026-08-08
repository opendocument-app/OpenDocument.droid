package app.opendocument.droid.ui

/**
 * Release variant: does nothing, which is what keeps espresso out of release builds. The debug
 * variant backs these calls with a CountingIdlingResource.
 */
object OpenFileIdling {

    fun increment() {}

    fun decrement() {}
}
