package app.opendocument.droid.nonfree

import android.app.Activity
import android.widget.LinearLayout

/**
 * The banner, in a build that links no ad sdk. [isPrivacyOptionsRequired] answering no is what
 * keeps the consent row off the landing screen.
 */
class AdManager {

    fun initialize(
        activity: Activity,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {}

    fun setEnabled(enabled: Boolean) {}

    fun setAdContainer(adContainer: LinearLayout) {}

    fun setConsentListener(listener: () -> Unit) {}

    fun setPurchaseListener(listener: () -> Unit) {}

    fun showGoogleAds() {}

    fun updateConsentInfo() {}

    fun refreshAds() {}

    fun isPrivacyOptionsRequired(): Boolean = false

    fun showPrivacyOptions() {}

    fun removeAds() {}

    fun destroyAds() {}
}
