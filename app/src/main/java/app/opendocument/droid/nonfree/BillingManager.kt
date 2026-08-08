package app.opendocument.droid.nonfree

import android.content.Context
import app.opendocument.droid.R
import app.opendocument.droid.background.BillingPreferences

class BillingManager {

    private var enabled = false

    private var adManager: AdManager? = null
    private var billingPreferences: BillingPreferences? = null

    fun initialize(context: Context, adManager: AdManager) {
        this.adManager = adManager

        val preferences = BillingPreferences(context)
        billingPreferences = preferences

        // pro and foss both ship without ads, so both count as bought
        if (context.resources.getBoolean(R.bool.DISABLE_TRACKING)) {
            preferences.setPurchased(true)
        }

        if (!enabled) {
            adManager.showGoogleAds()
            return
        }

        if (hasPurchased()) {
            adManager.removeAds()

            // no banner to gate, but withdrawal has to survive the purchase, and only an
            // update tells us whether the sdk wants a way back out
            adManager.updateConsentInfo()
        } else {
            adManager.showGoogleAds()
        }
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
