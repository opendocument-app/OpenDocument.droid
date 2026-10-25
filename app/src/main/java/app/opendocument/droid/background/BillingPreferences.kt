package app.opendocument.droid.background

import android.content.Context
import androidx.core.content.edit

/** The last answer from play, so that a launch starts without waiting for the next one. */
class BillingPreferences(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(
            "modifyMeIfYouWantToRemoveAdsIllegally",
            Context.MODE_PRIVATE,
        )

    /** No ads, for good: an old purchase, or a build without ads. */
    fun hasPurchased(): Boolean = sharedPreferences.getBoolean(PURCHASE_KEY, false)

    fun setPurchased(purchased: Boolean) {
        sharedPreferences.edit { putBoolean(PURCHASE_KEY, purchased) }
    }

    /** The pro features, for good: an old purchase that was for life. */
    fun hasLifetimePro(): Boolean = sharedPreferences.getBoolean(LIFETIME_PRO_KEY, false)

    fun setLifetimePro(lifetimePro: Boolean) {
        sharedPreferences.edit { putBoolean(LIFETIME_PRO_KEY, lifetimePro) }
    }

    /** The yearly pass, as play last reported it. */
    fun hasPass(): Boolean = sharedPreferences.getBoolean(PASS_KEY, false)

    fun setPass(pass: Boolean) {
        sharedPreferences.edit { putBoolean(PASS_KEY, pass) }
    }

    private companion object {
        const val PURCHASE_KEY = "purchaseAcknowledged"
        const val LIFETIME_PRO_KEY = "lifetimePro"
        const val PASS_KEY = "pass"
    }
}
