package app.opendocument.droid.nonfree

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
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

    /**
     * Fetches the sheet ahead of the moment to show it, so that moment does not wait on the
     * network.
     */
    fun prepare(activity: Activity, analyticsManager: AnalyticsManager): Prepared {
        val prepared = Prepared(activity, analyticsManager)
        analyticsManager.report("in_app_review_eligible")

        prepared.manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                prepared.reviewInfo = task.result
            } else {
                analyticsManager.report("in_app_review_error")
            }
        }

        return prepared
    }

    class Prepared
    internal constructor(
        private val activity: Activity,
        private val analyticsManager: AnalyticsManager,
    ) {
        internal val manager = ReviewManagerFactory.create(activity)
        internal var reviewInfo: ReviewInfo? = null

        /** False when the sheet has not arrived yet - then [onDone] does not run. */
        fun showIfReady(onAsked: () -> Unit, onDone: () -> Unit): Boolean {
            val reviewInfo = reviewInfo ?: return false

            analyticsManager.report("in_app_review_start")
            onAsked()

            manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                analyticsManager.report("in_app_review_done")
                onDone()
            }

            return true
        }
    }
}
