package app.opendocument.droid.nonfree

import android.app.Activity

/** The play in-app review sheet, in a build that links no play core. */
object InAppReview {

    fun request(activity: Activity, analyticsManager: AnalyticsManager, onAsked: () -> Unit) {}
}
