package at.tomtasche.reader.nonfree;

import android.content.Context;

import at.tomtasche.reader.BuildConfig;
import at.tomtasche.reader.background.BillingPreferences;

public class BillingManager {

    private boolean enabled;

    private AdManager adManager;

    private BillingPreferences billingPreferences;

    public void initialize(Context context, AnalyticsManager analyticsManager, AdManager adManager) {
        this.adManager = adManager;

        if (!enabled) {
            adManager.showGoogleAds();

            return;
        }

        billingPreferences = new BillingPreferences(context);

        if (BuildConfig.FLAVOR.equals("pro")) {
            billingPreferences.setPurchased(true);
        }

        enforceAds();
    }

    private void enforceAds() {
        if (hasPurchased()) {
            adManager.removeAds();
        } else {
            adManager.showGoogleAds();
        }
    }

    public boolean hasPurchased() {
        if (!enabled) {
            return true;
        }

        if (billingPreferences == null) {
            return false;
        }

        return billingPreferences.hasPurchased();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
