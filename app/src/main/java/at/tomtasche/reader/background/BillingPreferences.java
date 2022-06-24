package at.tomtasche.reader.background;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import at.tomtasche.reader.BuildConfig;

public class BillingPreferences {

    private final SharedPreferences sharedPreferences;

    public BillingPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(
                "modifyMeIfYouWantToRemoveAdsIllegally", Context.MODE_PRIVATE);
    }

    public boolean hasPurchased() {
        if (sharedPreferences == null) {
            return false;
        }

        return sharedPreferences.getBoolean("purchaseAcknowledged", false);
    }

    public void setPurchased(boolean purchased) {
        if (sharedPreferences == null) {
            return;
        }

        Editor editor = sharedPreferences.edit();
        editor.putBoolean("purchaseAcknowledged", purchased);
        editor.apply();
    }
}
