package at.tomtasche.reader.nonfree;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.util.List;

import at.tomtasche.reader.BuildConfig;
import at.tomtasche.reader.background.BillingPreferences;

public class BillingManager implements PurchasesUpdatedListener {

    private boolean enabled;

    private AdManager adManager;

    private BillingPreferences billingPreferences;
    private BillingClient billingClient;


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

        if (hasPurchased()) {
            adManager.removeAds();

            return;
        }

        analyticsManager.report("purchase_init");

        billingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    analyticsManager.report("purchase_init_success");

                    // refreshPurchased is async! since IAPs are not actively used anymore we intentionally make a tradeoff here: paying customers who are installing the app fresh / on a new device will see ads at least once (until refreshPurchased has completed)
                    refreshPurchased();
                } else {
                    analyticsManager.report("purchase_init_failed", "code", billingResult.getResponseCode());
                }

                enforceAds();

                billingClient.endConnection();
            }

            @Override
            public void onBillingServiceDisconnected() {
                analyticsManager.report("purchase_init_disconnected");
            }
        });
    }

    private void enforceAds() {
        if (hasPurchased()) {
            adManager.removeAds();
        } else {
            adManager.showGoogleAds();
        }
    }

    private void refreshPurchased() {
        if (!enabled) {
            return;
        }

        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> purchases) {
                for (Purchase purchase : purchases) {
                    if (purchase.isAcknowledged()) {
                        billingPreferences.setPurchased(true);

                        enforceAds();
                    }
                }
            }
        });
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

    public void close() {
        if (!enabled) {
            return;
        }

        if (billingClient == null || !billingClient.isReady()) {
            return;
        }
        billingClient.endConnection();
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        // NOOP
    }
}
