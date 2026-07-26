package app.opendocument.droid.background

import android.content.Context
import androidx.core.content.edit

class BillingPreferences(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(
            "modifyMeIfYouWantToRemoveAdsIllegally",
            Context.MODE_PRIVATE,
        )

    fun hasPurchased(): Boolean = sharedPreferences.getBoolean(PURCHASE_KEY, false)

    fun setPurchased(purchased: Boolean) {
        sharedPreferences.edit { putBoolean(PURCHASE_KEY, purchased) }
    }

    private companion object {
        const val PURCHASE_KEY = "purchaseAcknowledged"
    }
}
