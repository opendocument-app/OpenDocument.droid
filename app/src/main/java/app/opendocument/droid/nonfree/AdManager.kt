package app.opendocument.droid.nonfree

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/** The banner in the lite flavor, behind the consent form the ump sdk puts in front of it. */
class AdManager {

    private var enabled = false

    private lateinit var activity: Activity
    private lateinit var crashManager: CrashManager
    private lateinit var adContainer: LinearLayout
    private var adView: AdView? = null

    fun initialize(activity: Activity, crashManager: CrashManager) {
        // before the guard: removeAds() and destroyAds() reach these even with ads disabled,
        // which billing does whenever the user has already paid
        this.activity = activity
        this.crashManager = crashManager

        if (!enabled) {
            return
        }

        try {
            MobileAds.initialize(activity)
        } catch (e: Throwable) {
            // java.lang.VerifyError: com/google/android/gms/ads/internal/ClientApi
            crashManager.log(e)

            enabled = false
        }

        val configuration =
            RequestConfiguration.Builder().setTestDeviceIds(listOf(TEST_DEVICE_ID)).build()
        MobileAds.setRequestConfiguration(configuration)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setAdContainer(adContainer: LinearLayout) {
        this.adContainer = adContainer
    }

    private fun showAds(adView: AdView) {
        if (!enabled) {
            return
        }

        // a configuration change builds a fresh banner, and only the field's current occupant
        // is ever destroyed - so the one being replaced has to go now
        this.adView?.destroy()
        this.adView = adView

        adContainer.removeAllViews()

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        adContainer.addView(adView, params)

        adContainer.visibility = View.VISIBLE
    }

    private fun hideGoogleAds() {
        activity.runOnUiThread { adContainer.visibility = View.GONE }
    }

    fun showGoogleAds() {
        if (!enabled || isActivityGone()) {
            return
        }

        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // both callbacks come back off the network, seconds later and with the
                // activity captured, so the one they were meant for may be gone by then
                if (isActivityGone()) {
                    return@requestConsentInfoUpdate
                }

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { loadAndShowError
                    ->
                    if (isActivityGone()) {
                        return@loadAndShowConsentFormIfRequired
                    }

                    if (loadAndShowError != null || !consentInformation.canRequestAds()) {
                        // the ump sdk only logs an unspecific "Error making request."
                        crashManager.log(
                            "consent form failed: " +
                                describe(loadAndShowError) +
                                ", canRequestAds=" +
                                consentInformation.canRequestAds()
                        )

                        hideGoogleAds()

                        return@loadAndShowConsentFormIfRequired
                    }

                    activity.runOnUiThread { showAdaptiveBanner() }
                }
            },
            { requestConsentError ->
                if (isActivityGone()) {
                    return@requestConsentInfoUpdate
                }

                // fires for the mundane offline case too, not just a real failure
                crashManager.log("consent info update failed: " + describe(requestConsentError))

                hideGoogleAds()
            },
        )
    }

    private fun isActivityGone(): Boolean =
        !::activity.isInitialized || activity.isFinishing || activity.isDestroyed

    // https://developers.google.com/admob/android/banner/adaptive
    // the anchored adaptive size is deprecated in favour of the inline one, which sizes the
    // banner differently - a change to make on its own rather than in passing
    @Suppress("DEPRECATION")
    private fun showAdaptiveBanner() {
        val adView = AdView(activity)

        // WindowManager.getDefaultDisplay() is deprecated and its replacement needs API
        // 30; the resources' metrics carry the same width and density.
        val metrics = activity.resources.displayMetrics
        val adWidth = (metrics.widthPixels / metrics.density).toInt()

        adView.setAdSize(
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        )
        adView.adUnitId = AD_UNIT_ID
        adView.loadAd(AdRequest.Builder().build())

        showAds(adView)
    }

    fun removeAds() {
        enabled = false

        hideGoogleAds()
    }

    fun destroyAds() {
        try {
            // has thrown out of the ad sdk's own focus handling for some users
            adView?.destroy()
        } catch (e: Exception) {
            crashManager.log(e)
        }

        adView = null
    }

    private companion object {
        const val AD_UNIT_ID = "ca-app-pub-8161473686436957/5931994762"

        const val TEST_DEVICE_ID = "46C05048B04145D0724C1ADA7FC17619"

        fun describe(error: FormError?): String {
            if (error == null) {
                return "no error"
            }

            return error.errorCode.toString() + "/" + error.message
        }
    }
}
