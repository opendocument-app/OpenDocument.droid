package at.tomtasche.reader.nonfree

import android.content.Context
import at.tomtasche.reader.BuildConfig
import at.tomtasche.reader.background.BillingPreferences

class BillingManager {

    private var enabled = false

    private var adManager: AdManager? = null
    private var billingPreferences: BillingPreferences? = null

    fun initialize(context: Context, analyticsManager: AnalyticsManager, adManager: AdManager) {
        this.adManager = adManager

        val preferences = BillingPreferences(context)
        billingPreferences = preferences

        if (BuildConfig.FLAVOR == "pro") {
            preferences.setPurchased(true)
        }

        if (!enabled) {
            adManager.showGoogleAds()
            return
        }

        if (hasPurchased()) {
            adManager.removeAds()
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

    fun isEnabled(): Boolean = enabled
}
