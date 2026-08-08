package app.opendocument.droid.nonfree

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * The play in-app review sheet. Whether it appears at all is Play's own per-user quota, and the
 * completion listener fires either way - the analytics events are the only visibility there is.
 */
object InAppReview {

    /** Opens before the first ask: a fresh install has nothing to say, and asking costs stars. */
    private const val MINIMUM_OPENS: Int = 3

    fun requestIfEarned(activity: Activity, analyticsManager: AnalyticsManager, opens: Int) {
        if (opens < MINIMUM_OPENS) {
            return
        }

        request(activity, analyticsManager)
    }

    private fun request(activity: Activity, analyticsManager: AnalyticsManager) {
        analyticsManager.report("in_app_review_eligible")

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { reviewInfoTask ->
            if (!reviewInfoTask.isSuccessful) {
                // usually an install that did not come from play, so there is no store to ask
                analyticsManager.report("in_app_review_error")

                return@addOnCompleteListener
            }

            analyticsManager.report("in_app_review_start")

            manager.launchReviewFlow(activity, reviewInfoTask.result).addOnCompleteListener {
                analyticsManager.report("in_app_review_done")
            }
        }
    }
}
