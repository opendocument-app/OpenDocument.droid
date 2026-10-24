package app.opendocument.droid.nonfree

import android.content.Context
import app.opendocument.droid.background.BillingPreferences

class BillingManager {

    private var enabled = false

    private var adManager: AdManager? = null
    private var billingPreferences: BillingPreferences? = null

    private var onPurchaseRestored: (() -> Unit)? = null

    /** Fires once play reports a purchase that the preferences did not know about. */
    fun setRestoreListener(listener: () -> Unit) {
        onPurchaseRestored = listener
    }

    fun initialize(context: Context, adManager: AdManager, crashManager: CrashManager) {
        this.adManager = adManager

        val preferences = BillingPreferences(context)
        billingPreferences = preferences

        // pro and foss both ship without ads, so both count as bought
        if (!Features.withAds) {
            preferences.setPurchased(true)
        }

        if (!enabled) {
            adManager.showGoogleAds()
            return
        }

        if (hasPurchased()) {
            removeAds(adManager)
        } else {
            adManager.showGoogleAds()

            // the flag lives only on this device, so a reinstall or a new device loses it. the
            // ads show until play answers; a purchase is never revoked, as before
            OwnedPurchases.query(context, crashManager) {
                crashManager.log("purchase restored")

                preferences.setPurchased(true)

                removeAds(adManager)

                onPurchaseRestored?.invoke()
            }
        }
    }

    private fun removeAds(adManager: AdManager) {
        adManager.removeAds()

        // no banner to gate, but withdrawal has to survive the purchase, and only an
        // update tells us whether the sdk wants a way back out
        adManager.updateConsentInfo()
    }

    fun hasPurchased(): Boolean {
        if (!enabled) {
            return true
        }

        return billingPreferences?.hasPurchased() ?: false
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
