package app.opendocument.droid.nonfree

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * The play in-app review sheet. Whether it appears at all is play's own per-user quota, and the
 * completion listener fires either way - the analytics events are the only visibility there is.
 *
 * Nothing here decides whether to ask: `ReviewInvitation` does, in a build that has this or the one
 * that does not.
 */
object InAppReview {

    /** [onAsked] runs when the sheet is handed to play - see `ReviewInvitation.recordAsk`. */
    fun request(activity: Activity, analyticsManager: AnalyticsManager, onAsked: () -> Unit) {
        analyticsManager.report("in_app_review_eligible")

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { reviewInfoTask ->
            if (!reviewInfoTask.isSuccessful) {
                // usually an install that did not come from play, so there is no store to ask
                analyticsManager.report("in_app_review_error")

                return@addOnCompleteListener
            }

            analyticsManager.report("in_app_review_start")

            onAsked()

            manager.launchReviewFlow(activity, reviewInfoTask.result).addOnCompleteListener {
                analyticsManager.report("in_app_review_done")
            }
        }
    }
}
