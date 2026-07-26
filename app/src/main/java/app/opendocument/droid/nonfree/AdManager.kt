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
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var adContainer: LinearLayout
    private var adView: AdView? = null

    fun initialize(
        activity: Activity,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {
        if (!enabled) {
            return
        }

        this.activity = activity
        this.crashManager = crashManager
        this.analyticsManager = analyticsManager

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
        if (!enabled) {
            return
        }

        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { loadAndShowError
                    ->
                    if (loadAndShowError != null || !consentInformation.canRequestAds()) {
                        // without this the banner just silently stays hidden,
                        // and the ump sdk only logs an unspecific "Error
                        // making request."
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
                // fires for the mundane offline case too - a device that was asleep
                // times out against fundingchoicesmessages.google.com
                crashManager.log("consent info update failed: " + describe(requestConsentError))

                hideGoogleAds()
            },
        )
    }

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
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)
            adView?.destroy()
        } catch (e: Exception) {
            crashManager.log(e)
        }
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
