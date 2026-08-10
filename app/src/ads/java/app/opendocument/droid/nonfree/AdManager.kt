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

    /** Whether [initialize] ran far enough for the fields below to exist. */
    private var initialized = false

    private lateinit var activity: Activity
    private lateinit var crashManager: CrashManager
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var adContainer: LinearLayout
    private var adView: AdView? = null

    /** Set by the consent flow, which is also the only thing that makes it readable. */
    private var consentInformation: ConsentInformation? = null

    private var onConsentSettled: (() -> Unit)? = null
    private var onPurchaseRequested: (() -> Unit)? = null

    private var houseAdIndex = 0

    fun initialize(
        activity: Activity,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {
        // before the guard: removeAds() and destroyAds() reach these even with ads disabled,
        // which billing does whenever the user has already paid
        this.activity = activity
        this.crashManager = crashManager
        this.analyticsManager = analyticsManager

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

        initialized = true
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setAdContainer(adContainer: LinearLayout) {
        this.adContainer = adContainer
    }

    /** Fires once the consent flow has settled, so the caller can re-read it. */
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
     * [ConsentInformation.canRequestAds] is deliberately not a gate, unlike in google's sample: a
     * refusal still emits a tc string google picks limited ads from, and not asking is the only way
     * to lose those. Nor are the two errors, neither of which is a refusal. [hasConsentDecision] is
     * the gate.
     */
    fun showGoogleAds() {
        if (!enabled) {
            return
        }

        requestConsentInfo(gatherConsent = true)
    }

    /**
     * The update on its own, no form and no banner, which is all [isPrivacyOptionsRequired] needs.
     * Ad removal leads here, since [showGoogleAds] never runs once ads are gone.
     */
    fun updateConsentInfo() {
        requestConsentInfo(gatherConsent = false)
    }

    private fun requestConsentInfo(gatherConsent: Boolean) {
        if (!initialized || isActivityGone()) {
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

                if (gatherConsent) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        loadAndShowError ->
                        if (isActivityGone()) {
                            return@loadAndShowConsentFormIfRequired
                        }

                        if (loadAndShowError != null) {
                            // the ump sdk only logs an unspecific "Error making request."
                            crashManager.log("consent form failed: " + describe(loadAndShowError))
                        }

                        consentSettled(consentInformation)
                    }
                } else {
                    consentSettled(consentInformation)
                }
            },
            { requestConsentError ->
                if (isActivityGone()) {
                    return@requestConsentInfoUpdate
                }

                // fires for the mundane offline case too, not just a real failure
                crashManager.log("consent info update failed: " + describe(requestConsentError))

                consentSettled(consentInformation)
            },
        )
    }

    private fun isActivityGone(): Boolean =
        !::activity.isInitialized || activity.isFinishing || activity.isDestroyed

    private fun consentSettled(consentInformation: ConsentInformation) {
        this.consentInformation = consentInformation

        // the served mode is not readable from here; this is what decides it
        crashManager.log(
            "consent settled, status=" +
                consentInformation.consentStatus +
                ", canRequestAds=" +
                consentInformation.canRequestAds()
        )

        activity.runOnUiThread {
            onConsentSettled?.invoke()

            if (enabled) {
                showAdaptiveBanner()
            }
        }
    }

    /**
     * Whether the sdk holds an answer to send. A refusal counts; an update that never came back on
     * a device that has never had one does not, and would send no tc string to pick from.
     */
    private fun hasConsentDecision(): Boolean =
        when (consentInformation?.consentStatus) {
            ConsentInformation.ConsentStatus.OBTAINED,
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> true
            else -> false
        }

    /** Rebuilds the banner for a new size. A rotation cannot change what consent decided. */
    fun refreshAds() {
        if (!enabled || consentInformation == null) {
            return
        }

        activity.runOnUiThread { showAdaptiveBanner() }
    }

    /** Not gated on [enabled]: buying ad removal clears that, withdrawal outlives it. */
    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Only from user input - the sdk preloads the form for exactly this. */
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
        // WindowManager.getDefaultDisplay() is deprecated and its replacement needs API
        // 30; the resources' metrics carry the same width and density.
        val metrics = activity.resources.displayMetrics
        val adWidth = (metrics.widthPixels / metrics.density).toInt()

        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)

        // a webview underneath, and every rotation builds another
        adView?.destroy()
        adView = null

        if (!hasConsentDecision()) {
            showHouseAd(adSize, adWidth)

            return
        }

        val adView = AdView(activity)
        this.adView = adView

        adView.setAdSize(adSize)
        adView.adUnitId = AD_UNIT_ID
        adView.adListener =
            object : AdListener() {
                // the sdk retries behind our back and reports every attempt, which would
                // otherwise walk the house ad through all three texts at once
                private var houseAdShown = false

                /** A rotation replaced this banner, so it is too late to say anything. */
                private fun stale() = adView !== this@AdManager.adView

                override fun onAdLoaded() {
                    if (stale()) {
                        return
                    }

                    // a retry that eventually fills still gets to replace the house ad
                    houseAdShown = false

                    showAds(adView)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (stale() || houseAdShown) {
                        return
                    }
                    houseAdShown = true

                    // limited ads fill far less often - the common case for a refusal
                    crashManager.log("ad failed to load: " + error.code + "/" + error.message)

                    showHouseAd(adSize, adWidth)
                }
            }

        // the container stays as it is until the listener fires, so a request that does not
        // fill never shows as a gap
        adView.loadAd(AdRequest.Builder().build())
    }

    /**
     * One layout for every slot the banner comes in: parts drop out as it narrows, the subline
     * first and then the icon. Neither line wraps, so a translation cannot break the height.
     */
    private fun showHouseAd(adSize: AdSize, adWidth: Int) {
        if (!enabled) {
            return
        }

        val houseAd = activity.layoutInflater.inflate(R.layout.house_ad, adContainer, false)

        val variant = HOUSE_ADS[houseAdIndex % HOUSE_ADS.size]
        houseAdIndex++

        crashManager.log("house ad " + houseAdIndex + " at " + adWidth + "dp")

        analyticsManager.report("house_ad_shown")

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
            analyticsManager.report("house_ad_tapped")

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
