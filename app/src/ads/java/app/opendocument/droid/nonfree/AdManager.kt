package app.opendocument.droid.nonfree

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.opendocument.droid.R
import app.opendocument.droid.background.AppPreferences
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

    /** The house ad sitting in the container beside [adView]; only ever one of the two is shown. */
    private var houseAd: View? = null

    /** Which text the house ad in the container carries. The stored index only moves on a show. */
    private var houseAdVariant = 0

    /** The slot width the house ad was laid out for, for the breadcrumb it leaves when shown. */
    private var houseAdWidth = 0

    /** The width the banner was last asked at: a change back to it needs no new request. */
    private var requestedWidth = 0

    /** Whether a banner has ever filled. A refresh that does not keeps the ad already on screen. */
    private var hasAd = false

    private var paused = false

    private val retries = Handler(Looper.getMainLooper())

    /**
     * How long the next retry waits: doubling from [FIRST_RETRY_DELAY_MS] up to the unit's rate.
     */
    private var retryDelay = FIRST_RETRY_DELAY_MS

    /**
     * Reschedules itself before it asks, so a request that never comes back cannot end the chain -
     * which would leave the slot exactly as silent as the bug this replaces.
     */
    private val retry = Runnable {
        val adView = this.adView

        if (enabled && !paused && adView != null && !isActivityGone()) {
            retryDelay = (retryDelay * 2).coerceAtMost(RETRY_DELAY_MS)

            scheduleRetry()

            adView.loadAd(AdRequest.Builder().build())
        }
    }

    /** Set by the consent flow, which is also the only thing that makes it readable. */
    private var consentInformation: ConsentInformation? = null

    private var onConsentSettled: (() -> Unit)? = null
    private var onPurchaseRequested: (() -> Unit)? = null

    /**
     * Which rotation of the house ad comes next. On disk, because this object is rebuilt on every
     * cold start - as OpenDocument.ios keeps it in its own defaults.
     */
    private var houseAdIndex: Int
        get() = AppPreferences.of(activity).getInt(PREF_HOUSE_AD_INDEX, 0)
        set(value) {
            AppPreferences.of(activity).edit().putInt(PREF_HOUSE_AD_INDEX, value).apply()
        }

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

    /** Swaps the slot between the two views already in it, or hides both while neither has run. */
    private fun show(view: View?) {
        if (!enabled) {
            return
        }

        adView?.visibility = if (view === adView) View.VISIBLE else View.GONE
        houseAd?.visibility = if (view === houseAd) View.VISIBLE else View.GONE

        adContainer.visibility = if (view == null) View.GONE else View.VISIBLE
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
        if (!enabled || isActivityGone()) {
            return
        }

        // WindowManager.getDefaultDisplay() is deprecated and its replacement needs API
        // 30; the resources' metrics carry the same width and density.
        val metrics = activity.resources.displayMetrics
        val adWidth = (metrics.widthPixels / metrics.density).toInt()
        if (adWidth <= 0) {
            return
        }

        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)

        // both live in the container for as long as the activity does, and take turns being shown.
        // the banner staying in the hierarchy is the point: the ad unit refreshes itself, but only
        // for a banner that is on screen, so one that is torn out never gets asked for again
        layOutHouseAd(houseAd ?: addHouseAd().also { houseAd = it }, adSize, adWidth)

        if (!hasConsentDecision()) {
            showHouseAd()

            return
        }

        // an anchored banner keeps the size it was asked at, so a real width change needs a new
        // one - but a keyboard opening, or a rotation back, does not
        val previous = this.adView
        if (previous != null && adWidth == requestedWidth) {
            return
        }
        requestedWidth = adWidth

        // AdView.setAdSize throws once the view has a size, so a width change replaces the banner
        // rather than resizing it, and the ad that was up goes with it
        if (previous != null) {
            destroyAdView()
            adContainer.removeView(previous)
            hasAd = false
        }

        val adView = addAdView().also { this.adView = it }

        adView.setAdSize(adSize)

        retries.removeCallbacksAndMessages(null)
        retryDelay = FIRST_RETRY_DELAY_MS

        adView.loadAd(AdRequest.Builder().build())
    }

    private fun addAdView(): AdView {
        val adView = AdView(activity)

        adView.adUnitId = AD_UNIT_ID
        adView.visibility = View.GONE
        adView.adListener =
            object : AdListener() {
                override fun onAdLoaded() {
                    retries.removeCallbacksAndMessages(null)
                    retryDelay = FIRST_RETRY_DELAY_MS
                    hasAd = true

                    show(adView)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // limited ads fill far less often - the common case for a refusal
                    crashManager.log("ad failed to load: " + error.code + "/" + error.message)

                    // a refresh that did not fill leaves the ad it already has up, and the sdk
                    // keeps refreshing it; only an empty slot needs the house ad and our own asking
                    if (hasAd) {
                        return
                    }

                    showHouseAd()
                    scheduleRetry()
                }
            }

        adContainer.addView(
            adView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        return adView
    }

    /**
     * Asks again for a slot that did not fill. The ad unit refreshes itself every 60 seconds, but
     * only while its banner is on screen - and a banner the house ad is standing in for is not, so
     * without this one refusal ends the session's advertising.
     *
     * A failure served nothing, so no impression's refresh rate is being protected and the first
     * ask can come long before a minute is up - which is what a reader who opens one document and
     * leaves needs. Each further one doubles up to the rate the unit is set to, so a session that
     * is never going to fill settles on what the sdk itself would have done.
     */
    private fun scheduleRetry() {
        retries.removeCallbacksAndMessages(null)
        retries.postDelayed(retry, retryDelay)
    }

    /** Puts the house ad in the container, hidden, with the text the stored rotation is on. */
    private fun addHouseAd(): View {
        val houseAd = activity.layoutInflater.inflate(R.layout.house_ad, adContainer, false)

        houseAdVariant = houseAdIndex % HOUSE_ADS.size

        houseAd.setOnClickListener {
            analyticsManager.report("house_ad_tapped")

            onPurchaseRequested?.invoke()
        }

        houseAd.visibility = View.GONE
        adContainer.addView(houseAd)

        return houseAd
    }

    /**
     * Fits the house ad to the slot the banner comes in: parts drop out as it narrows, the subline
     * first and then the icon. Neither line wraps, so a translation cannot break the height. Runs
     * again on every width change, on the one view - the text it carries does not change under a
     * reader who is already looking at it.
     */
    private fun layOutHouseAd(houseAd: View, adSize: AdSize, adWidth: Int) {
        val variant = HOUSE_ADS[houseAdVariant]

        houseAdWidth = adWidth

        houseAd.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                adSize.getHeightInPixels(activity),
            )

        // the 90dp slot, which only tablets get
        val wide = adWidth >= WIDE_WIDTH

        val icon = houseAd.findViewById<ImageView>(R.id.house_ad_icon)
        if (adWidth < ICON_WIDTH) {
            icon.visibility = View.GONE
        } else {
            icon.visibility = View.VISIBLE

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
            subline.visibility = View.VISIBLE

            subline.setText(if (wide) variant.wideSubline else variant.subline)
            subline.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (wide) 13f else 11f)
        }

        val cta = houseAd.findViewById<TextView>(R.id.house_ad_cta)
        cta.setText(variant.cta)
        if (wide) {
            cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            cta.setPadding(dp(16), dp(8), dp(16), dp(8))
        }
    }

    /**
     * Hands the slot to the house ad. The stored rotation only moves when one is actually shown, so
     * a session that never fills does not walk through all three texts.
     */
    private fun showHouseAd() {
        if (!enabled) {
            return
        }

        val houseAd = this.houseAd ?: return

        if (houseAd.visibility == View.VISIBLE) {
            return
        }

        crashManager.log("house ad " + houseAdVariant + " at " + houseAdWidth + "dp")

        houseAdIndex = (houseAdVariant + 1) % HOUSE_ADS.size

        analyticsManager.report("house_ad_shown")

        show(houseAd)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private class HouseAd(
        val headline: Int,
        val shortHeadline: Int,
        val subline: Int,
        val wideSubline: Int,
        val cta: Int,
    )

    /** Stops the banner with the activity: the sdk's own refresh does not know it went away. */
    fun pauseAds() {
        paused = true

        retries.removeCallbacksAndMessages(null)

        adView?.pause()
    }

    fun resumeAds() {
        paused = false

        adView?.resume()

        // a slot that came back still empty gets asked for again; a filled one is the sdk's
        if (enabled && !hasAd && adView != null && !isActivityGone()) {
            scheduleRetry()
        }
    }

    /** Not gated on [enabled]: billing calls it once the user has paid. */
    fun removeAds() {
        enabled = false

        destroyAds()

        hideGoogleAds()
    }

    fun destroyAds() {
        retries.removeCallbacksAndMessages(null)

        destroyAdView()

        adView = null
        houseAd = null
        hasAd = false
        requestedWidth = 0

        if (::adContainer.isInitialized) {
            adContainer.removeAllViews()
        }
    }

    private fun destroyAdView() {
        try {
            // has thrown out of the ad sdk's own focus handling for some users
            adView?.destroy()
        } catch (e: Exception) {
            crashManager.log(e)
        }
    }

    private companion object {
        const val AD_UNIT_ID = "ca-app-pub-8161473686436957/5931994762"

        const val TEST_DEVICE_ID = "46C05048B04145D0724C1ADA7FC17619"

        const val PREF_HOUSE_AD_INDEX = "house_ad_index"

        // 10s, 20s, 40s, then the 60s the unit refreshes at. Not shorter: the sdk throttles a
        // burst of failed requests itself.
        const val FIRST_RETRY_DELAY_MS = 10_000L
        const val RETRY_DELAY_MS = 60_000L

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
