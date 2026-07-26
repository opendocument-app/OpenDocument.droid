package app.opendocument.droid.ui

/**
 * Release variant: does nothing. The debug variant backs these calls with an espresso
 * CountingIdlingResource; keeping the espresso dependency out of release builds is the whole point
 * of the split.
 */
object OpenFileIdling {

    @JvmStatic fun increment() {}

    @JvmStatic fun decrement() {}
}
