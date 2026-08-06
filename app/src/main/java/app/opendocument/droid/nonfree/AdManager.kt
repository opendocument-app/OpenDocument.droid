package app.opendocument.droid.nonfree

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.opendocument.droid.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
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

    /** Set once the consent flow has settled, which is also what makes its state readable. */
    private var consentInformation: ConsentInformation? = null

    private var onConsentSettled: (() -> Unit)? = null
    private var onPurchaseRequested: (() -> Unit)? = null

    private var houseAdIndex = 0

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

    /** Called once the consent flow has settled, so the caller can re-read what it decided. */
    fun setConsentListener(listener: () -> Unit) {
        onConsentSettled = listener
    }

    /** Where the house ad sends whoever taps it. */
    fun setPurchaseListener(listener: () -> Unit) {
        onPurchaseRequested = listener
    }

    private fun showAds(adView: AdView) {
        if (!enabled) {
            return
        }

        this.adView = adView

        showInAdContainer(
            adView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun showInAdContainer(view: View, params: LinearLayout.LayoutParams) {
        adContainer.removeAllViews()
        adContainer.addView(view, params)

        adContainer.visibility = View.VISIBLE
    }

    private fun hideGoogleAds() {
        activity.runOnUiThread { adContainer.visibility = View.GONE }
    }

    /**
     * Runs the consent flow, once per launch, and loads a banner whatever it decides.
     *
     * [ConsentInformation.canRequestAds] is deliberately not a gate, which is where this parts ways
     * with google's own sample. Refusing everything still emits a tc string carrying the special
     * purposes, from which google picks limited ads server-side: no cookies, no identifiers, no
     * local storage. There is no client-side flag for that mode - not sending the request is the
     * only way to lose it, and showing nothing is a choice rather than an obligation.
     *
     * The two errors are breadcrumbs for the same reason: neither is a refusal. The sdk caches the
     * user's decision, so a form that fails to show, or an update that times out because the device
     * is offline, still leaves an earlier consent standing, and outside the regions where a form is
     * required at all there is no decision to fail in the first place.
     */
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
                    if (loadAndShowError != null) {
                        // the ump sdk only logs an unspecific "Error making request."
                        crashManager.log("consent form failed: " + describe(loadAndShowError))
                    }

                    consentSettled(consentInformation)
                }
            },
            { requestConsentError ->
                // fires for the mundane offline case too - a device that was asleep
                // times out against fundingchoicesmessages.google.com
                crashManager.log("consent info update failed: " + describe(requestConsentError))

                consentSettled(consentInformation)
            },
        )
    }

    private fun consentSettled(consentInformation: ConsentInformation) {
        this.consentInformation = consentInformation

        // the served mode is not readable from here, but this is what decides it
        crashManager.log("consent settled, canRequestAds=" + consentInformation.canRequestAds())

        activity.runOnUiThread {
            onConsentSettled?.invoke()

            showAdaptiveBanner()
        }
    }

    /**
     * Rebuilds the banner, whose size is orientation-dependent. The consent flow stays at once per
     * launch - it is a network call, and nothing about a rotation can change its answer.
     */
    fun refreshAds() {
        if (!enabled || consentInformation == null) {
            return
        }

        activity.runOnUiThread { showAdaptiveBanner() }
    }

    /**
     * Whether the sdk wants a re-entry point into the consent form, which is how a user withdraws.
     * Not gated on [enabled]: buying ad removal clears that, and someone who consented before
     * buying must still be able to take it back.
     */
    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Only ever from user input - the sdk preloads the form for exactly this. */
    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                crashManager.log("privacy options form failed: " + describe(formError))
            }
        }
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

        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        adView.setAdSize(adSize)
        adView.adUnitId = AD_UNIT_ID
        adView.adListener =
            object : AdListener() {
                // the sdk retries behind our back and reports every attempt, so one request
                // arrives here as several failures - which would otherwise walk the house ad
                // through all three of its texts in half a second
                private var houseAdShown = false

                override fun onAdLoaded() {
                    // a retry that eventually fills still gets to replace the house ad
                    houseAdShown = false

                    showAds(adView)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (houseAdShown) {
                        return
                    }
                    houseAdShown = true

                    // limited ads fill far less often than personalised ones, so an empty
                    // strip is the common case for whoever refused consent
                    crashManager.log("ad failed to load: " + error.code + "/" + error.message)

                    showHouseAd(adSize, adWidth)
                }
            }

        // nothing goes into the container until the listener fires, so a request that does
        // not fill never shows as a gap
        adView.loadAd(AdRequest.Builder().build())
    }

    /**
     * One layout for every slot the banner comes in. Parts drop out as it narrows - the subline
     * first, then the icon - and the headline has a short form for when it is all that is left.
     * Neither line ever wraps, so a long translation shortens rather than breaking the height.
     */
    private fun showHouseAd(adSize: AdSize, adWidth: Int) {
        if (!enabled) {
            return
        }

        val houseAd = activity.layoutInflater.inflate(R.layout.house_ad, adContainer, false)

        val variant = HOUSE_ADS[houseAdIndex % HOUSE_ADS.size]
        houseAdIndex++

        crashManager.log("house ad " + houseAdIndex + " at " + adWidth + "dp")

        // the 90dp slot, which only tablets get
        val wide = adWidth >= WIDE_WIDTH

        val icon = houseAd.findViewById<ImageView>(R.id.house_ad_icon)
        if (adWidth < ICON_WIDTH) {
            icon.visibility = View.GONE
        } else {
            val size = dp(if (wide) 58 else 34)
            icon.layoutParams.width = size
            icon.layoutParams.height = size
        }

        val headline = houseAd.findViewById<TextView>(R.id.house_ad_headline)
        headline.setText(if (adWidth < SUBLINE_WIDTH) variant.shortHeadline else variant.headline)
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (wide) 17f else 13f)

        val subline = houseAd.findViewById<TextView>(R.id.house_ad_subline)
        if (adWidth < SUBLINE_WIDTH) {
            subline.visibility = View.GONE
        } else {
            subline.setText(if (wide) variant.wideSubline else variant.subline)
            subline.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (wide) 13f else 11f)
        }

        val cta = houseAd.findViewById<TextView>(R.id.house_ad_cta)
        cta.setText(variant.cta)
        if (wide) {
            cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            cta.setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        houseAd.setOnClickListener {
            analyticsManager.report("house_ad")

            onPurchaseRequested?.invoke()
        }

        showInAdContainer(
            houseAd,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                adSize.getHeightInPixels(activity),
            ),
        )
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private class HouseAd(
        val headline: Int,
        val shortHeadline: Int,
        val subline: Int,
        val wideSubline: Int,
        val cta: Int,
    )

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

        // the slot widths, in dp, at which the house ad loses a part
        const val WIDE_WIDTH = 700
        const val SUBLINE_WIDTH = 360
        const val ICON_WIDTH = 300

        // three angles at the same offer, one per banner that did not fill
        val HOUSE_ADS =
            listOf(
                HouseAd(
                    R.string.house_ad_support_headline,
                    R.string.house_ad_support_headline_short,
                    R.string.house_ad_support_subline,
                    R.string.house_ad_support_subline_wide,
                    R.string.house_ad_cta_go_pro,
                ),
                HouseAd(
                    R.string.house_ad_ad_free_headline,
                    R.string.house_ad_ad_free_headline,
                    R.string.house_ad_ad_free_subline,
                    R.string.house_ad_ad_free_subline_wide,
                    R.string.house_ad_cta_get_pro,
                ),
                HouseAd(
                    R.string.house_ad_open_source_headline,
                    R.string.house_ad_open_source_headline_short,
                    R.string.house_ad_open_source_subline,
                    R.string.house_ad_open_source_subline_wide,
                    R.string.house_ad_cta_go_pro,
                ),
            )

        fun describe(error: FormError?): String {
            if (error == null) {
                return "no error"
            }

            return error.errorCode.toString() + "/" + error.message
        }
    }
}
