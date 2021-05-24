package at.tomtasche.reader.nonfree;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import at.tomtasche.reader.BuildConfig;
import at.tomtasche.reader.background.BillingPreferences;

public class BillingManager implements PurchasesUpdatedListener {

    // test SKU: android.test.purchased
    public static final String BILLING_PRODUCT_PURCHASE = "remove_ads_for_eva";
    public static final String BILLING_PRODUCT_SUBSCRIPTION = "remove_ads_subscription";

    private boolean enabled;

    private AnalyticsManager analyticsManager;
    private CrashManager crashManager;
    private AdManager adManager;

    private BillingPreferences billingPreferences;
    private BillingClient billingClient;

    private SkuDetails purchaseSku;
    private SkuDetails subscriptionSku;

    private Runnable onPurchaseInit;
    private ProgressDialog progressDialog;

    public void initialize(Context context, AnalyticsManager analyticsManager, AdManager adManager, CrashManager crashManager) {
        this.adManager = adManager;
        this.crashManager = crashManager;
        this.analyticsManager = analyticsManager;

        if (!enabled) {
            adManager.showGoogleAds();

            return;
        }

        billingPreferences = new BillingPreferences(context);

        if (BuildConfig.FLAVOR.equals("pro")) {
            billingPreferences.setPurchased(true);
        }

        if (billingPreferences.hasPurchased()) {
            adManager.removeAds();

            enabled = false;

            // fall through in order to recheck purchases (e.g. cancelled subscription)
        }

        billingClient = BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build();
        connectAndRun(new Runnable() {
            @Override
            public void run() {
                List<String> skuList = Arrays.asList(BILLING_PRODUCT_PURCHASE, BILLING_PRODUCT_SUBSCRIPTION);
                SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
                billingClient.querySkuDetailsAsync(params.build(),
                        new SkuDetailsResponseListener() {
                            @Override
                            public void onSkuDetailsResponse(BillingResult billingResult,
                                                             List<SkuDetails> skuDetailsList) {
                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    List<SkuDetails> allSkuDetailsList = new LinkedList<>(skuDetailsList);

                                    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                                    params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS);
                                    billingClient.querySkuDetailsAsync(params.build(),
                                            new SkuDetailsResponseListener() {
                                                @Override
                                                public void onSkuDetailsResponse(BillingResult billingResult,
                                                                                 List<SkuDetails> skuDetailsList) {
                                                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                                        analyticsManager.report("purchase_init_query_success", "code", billingResult.getResponseCode());

                                                        allSkuDetailsList.addAll(skuDetailsList);
                                                        finishPurchaseInit(allSkuDetailsList);
                                                    } else {
                                                        failPurchaseInit(billingResult);
                                                    }
                                                }
                                            });
                                } else {
                                    failPurchaseInit(billingResult);
                                }
                            }
                        });
            }
        });
    }

    private void finishPurchaseInit(List<SkuDetails> allSkuDetailsList) {
        for (SkuDetails sku : allSkuDetailsList) {
            if (BILLING_PRODUCT_PURCHASE.equals(sku.getSku())) {
                purchaseSku = sku;
            } else if (BILLING_PRODUCT_SUBSCRIPTION.equals(sku.getSku())) {
                subscriptionSku = sku;
            }
        }

        refreshPurchased();
        if (hasPurchased()) {
            adManager.removeAds();
            enabled = false;
        } else {
            adManager.showGoogleAds();

            if (onPurchaseInit != null) {
                onPurchaseInit.run();
            }
        }
    }

    private void failPurchaseInit(BillingResult billingResult) {
        if (!enabled) {
            // it's possible to get here with !enabled if a user has purchased already, but subsequent license checks failed. ignore in that case

            return;
        }

        analyticsManager.report("purchase_init_query_failed", "code", billingResult.getResponseCode());

        enabled = false;

        adManager.showGoogleAds();
    }

    private void connectAndRun(Runnable runnable) {
        analyticsManager.report("purchase_init");

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

                analyticsManager.report("purchase_init_disconnected");
            }
        });
    }

    private void refreshPurchased() {
        if (!enabled) {
            return;
        }

        Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
        boolean hasPurchased = acknowledgePurchases(purchasesResult);

        purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
        hasPurchased |= acknowledgePurchases(purchasesResult);

        billingPreferences.setPurchased(hasPurchased);
    }

    private boolean acknowledgePurchases(Purchase.PurchasesResult purchasesResult) {
        boolean hasPurchased = false;

        if (purchasesResult.getPurchasesList() != null) {
            for (Purchase purchase : purchasesResult.getPurchasesList()) {
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams.Builder builder = AcknowledgePurchaseParams.newBuilder();
                    builder.setPurchaseToken(purchase.getPurchaseToken());

                    billingClient.acknowledgePurchase(builder.build(), new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                            // TODO: in the best case we would set hasPurchased to true only now. not worth the effort in my opinion
                        }
                    });
                }

                hasPurchased = true;

                break;
            }
        }
        return hasPurchased;
    }

    public boolean hasPurchased() {
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

    public void startPurchase(AppCompatActivity activity, String product) {
        if (!enabled) {
            return;
        }

        SkuDetails productSku = null;
        if (BILLING_PRODUCT_PURCHASE.equals(product)) {
            productSku = purchaseSku;
        } else if (BILLING_PRODUCT_SUBSCRIPTION.equals(product)) {
            productSku = subscriptionSku;
        }

        if (productSku == null) {
            crashManager.log("SKU not resolved");

            onPurchaseInit = new Runnable() {
                @Override
                public void run() {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;

                        onPurchaseInit = null;

                        // only start purchase if progressDialog is still visible.
                        // otherwise we might distract the user while doing something else
                        startPurchase(activity, product);
                    }
                }
            };

            progressDialog = new ProgressDialog(activity);
            progressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    progressDialog = null;
                }
            });
            progressDialog.show();

            return;
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(productSku).build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

        analyticsManager.report("purchase_attempt", "code", result.getResponseCode());

        if (result.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
            connectAndRun(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing()) {
                        return;
                    }

                    startPurchase(activity, product);
                }
            });
        }
    }

    public void close() {
        if (!enabled) {
            return;
        }

        onPurchaseInit = null;

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }

        if (billingClient == null || !billingClient.isReady()) {
            return;
        }

        billingClient.endConnection();
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            refreshPurchased();

            if (hasPurchased()) {
                adManager.removeAds();
                enabled = false;
            }

            analyticsManager.report("purchase_success");
            analyticsManager.report(FirebaseAnalytics.Event.ECOMMERCE_PURCHASE);
        } else {
            analyticsManager.report(FirebaseAnalytics.Event.REMOVE_FROM_CART);
            analyticsManager.report("purchase_abort", "code", billingResult.getResponseCode());
        }
    }
}
