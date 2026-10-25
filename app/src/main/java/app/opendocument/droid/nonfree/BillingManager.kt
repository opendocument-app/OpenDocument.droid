package app.opendocument.droid.nonfree

import android.app.Activity
import android.content.Context
import app.opendocument.droid.background.BillingPreferences

class BillingManager {

    private var enabled = false

    private var adManager: AdManager? = null
    private var billingPreferences: BillingPreferences? = null
    private var playBilling: PlayBilling? = null

    private var onEntitlementChanged: ((Boolean) -> Unit)? = null

    /**
     * Fires when play reports what the preferences did not know: a purchase, a restore, or an ended
     * pass. The argument says whether the pro features have just been unlocked.
     */
    fun setEntitlementListener(listener: (unlocked: Boolean) -> Unit) {
        onEntitlementChanged = listener
    }

    fun initialize(context: Context, adManager: AdManager, crashManager: CrashManager) {
        this.adManager = adManager

        val preferences = BillingPreferences(context)
        billingPreferences = preferences

        // pro and foss both ship without ads, so both count as bought
        if (!Features.withAds) {
            preferences.setPurchased(true)
        }

        // also without play: the last answer stands until play gives a new one
        Features.unlocked = isPro()

        if (!enabled) {
            adManager.showGoogleAds()
            return
        }

        if (isAdFree()) {
            removeAds(adManager)
        } else {
            adManager.showGoogleAds()
        }

        // asked at every launch, not only until something turns up: the flags live only on this
        // device, and a pass ends. the ads show until play answers
        playBilling =
            PlayBilling(context, crashManager) { entitlement ->
                crashManager.log("billing answered")

                apply(entitlement)
            }
        playBilling?.queryOwned()
    }

    private fun apply(entitlement: Entitlement) {
        val preferences = billingPreferences ?: return
        val adManager = adManager ?: return

        val wasAdFree = isAdFree()
        val wasPro = isPro()

        // an old purchase is never taken back, as before; a pass is play's to report
        if (entitlement.adFree) {
            preferences.setPurchased(true)
        }
        if (entitlement.lifetimePro) {
            preferences.setLifetimePro(true)
        }
        preferences.setPass(entitlement.pass)

        if (!wasAdFree && isAdFree()) {
            removeAds(adManager)
        }

        // an ended pass takes effect at the next launch: the ads do not come back once removed,
        // and the open document keeps the editing scope it was rendered with
        val unlocked = !wasPro && isPro()
        if (unlocked) {
            Features.unlocked = true
        }

        if (wasAdFree != isAdFree() || wasPro != isPro()) {
            onEntitlementChanged?.invoke(unlocked)
        }
    }

    private fun removeAds(adManager: AdManager) {
        adManager.removeAds()

        // no banner to gate, but withdrawal has to survive the purchase, and only an
        // update tells us whether the sdk wants a way back out
        adManager.updateConsentInfo()
    }

    /** No ads: an old purchase, the pass, or a build that has none, or billing is not available. */
    fun isAdFree(): Boolean {
        if (!enabled) {
            return true
        }

        val preferences = billingPreferences ?: return false

        return preferences.hasPurchased() || isPro()
    }

    /** The pro features bought in lite: the pass, or an old purchase for life. */
    fun isPro(): Boolean {
        val preferences = billingPreferences ?: return false

        return preferences.hasLifetimePro() || preferences.hasPass()
    }

    /** Whether lite can sell the pass here: play is available and the account does not hold it. */
    fun offersPass(): Boolean = enabled && Features.withAds && !isPro()

    /** See [PlayBilling.queryPassPrice]. Null without play. */
    fun queryPassPrice(callback: (String?) -> Unit) {
        playBilling?.queryPassPrice(callback) ?: callback(null)
    }

    fun buyPass(activity: Activity) {
        playBilling?.buyPass(activity)
    }

    fun destroy() {
        playBilling?.end()
        playBilling = null

        onEntitlementChanged = null
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
