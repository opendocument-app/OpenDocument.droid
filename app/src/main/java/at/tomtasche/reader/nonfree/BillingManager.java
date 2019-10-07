package at.tomtasche.reader.nonfree;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import at.tomtasche.reader.background.BillingPreferences;

public class BillingManager implements PurchasesUpdatedListener {

    public static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";

    private boolean enabled;

    private AnalyticsManager analyticsManager;
    private CrashManager crashManager;
    private AdManager adManager;

    private BillingPreferences billingPreferences;
    private BillingClient billingClient;

    private SkuDetails resolvedSku;

    public void initialize(Context context, AnalyticsManager analyticsManager, AdManager adManager, CrashManager crashManager) {
        this.adManager = adManager;
        this.crashManager = crashManager;
        this.analyticsManager = analyticsManager;

        if (!enabled) {
            adManager.showGoogleAds();

            return;
        }

        billingPreferences = new BillingPreferences(context);

        if (billingPreferences.hasPurchased()) {
            adManager.removeAds();

            enabled = false;

            return;
        }

        billingClient = BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build();
        connectAndRun(new Runnable() {
            @Override
            public void run() {
                List<String> skuList = Collections.singletonList(BILLING_PRODUCT_FOREVER);
                SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
                billingClient.querySkuDetailsAsync(params.build(),
                        new SkuDetailsResponseListener() {
                            @Override
                            public void onSkuDetailsResponse(BillingResult billingResult,
                                                             List<SkuDetails> skuDetailsList) {
                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !skuDetailsList.isEmpty()) {
                                    resolvedSku = skuDetailsList.get(0);

                                    refreshPurchased();
                                    if (hasPurchased()) {
                                        adManager.removeAds();
                                        enabled = false;
                                    } else {
                                        adManager.showGoogleAds();
                                    }
                                } else {
                                    analyticsManager.report("purchase_init_query_failed", "code", billingResult.getResponseCode());

                                    enabled = false;
                                    adManager.showGoogleAds();
                                }
                            }
                        });
            }
        });
    }

    private void connectAndRun(Runnable runnable) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    analyticsManager.report("purchase_init_success");

                    runnable.run();
                } else {
                    analyticsManager.report("purchase_init_failed", "code", billingResult.getResponseCode());

                    enabled = false;
                    adManager.showGoogleAds();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // TODO: retry?
            }
        });
    }

    private void refreshPurchased() {
        if (!enabled) {
            return;
        }

        Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
        boolean hasPurchased = purchasesResult != null && purchasesResult.getPurchasesList() != null && !purchasesResult.getPurchasesList().isEmpty();
        billingPreferences.setPurchased(hasPurchased);
    }

    public boolean hasPurchased() {
        return billingPreferences.hasPurchased();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void startPurchase(AppCompatActivity activity) {
        if (!enabled) {
            return;
        }

        if (resolvedSku == null) {
            crashManager.log("SKU not resolved");
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(resolvedSku).build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

        analyticsManager.report("purchase_attempt", "code", result.getResponseCode());

        if (result.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
            connectAndRun(new Runnable() {
                @Override
                public void run() {
                    if (activity == null || activity.isFinishing()) {
                        return;
                    }

                    startPurchase(activity);
                }
            });
        }
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
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingPreferences.setPurchased(true);

            adManager.removeAds();
            enabled = false;

            analyticsManager.report("purchase_success");
            analyticsManager.report(FirebaseAnalytics.Event.ECOMMERCE_PURCHASE);
        } else {
            analyticsManager.report(FirebaseAnalytics.Event.REMOVE_FROM_CART);
            analyticsManager.report("purchase_abort", "code", billingResult.getResponseCode());
        }
    }
}
