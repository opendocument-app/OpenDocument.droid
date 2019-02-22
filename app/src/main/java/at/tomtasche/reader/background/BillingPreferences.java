package at.tomtasche.reader.background;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class BillingPreferences {

    private SharedPreferences sharedPreferences;

    public BillingPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(
                "modifyMeIfYouWantToRemoveAdsIllegally", Context.MODE_PRIVATE);
    }

    public boolean hasPurchased() {
        return sharedPreferences.getBoolean("purchased", false);
    }

    public void setPurchased(boolean purchased) {
        Editor editor = sharedPreferences.edit();
        editor.putBoolean("purchased", purchased);
        editor.commit();
    }
}
