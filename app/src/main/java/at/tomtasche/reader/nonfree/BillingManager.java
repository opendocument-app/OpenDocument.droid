package at.tomtasche.reader.nonfree;

import android.content.Context;
import android.content.Intent;

import com.github.jberkel.pay.me.IabHelper;
import com.github.jberkel.pay.me.IabResult;
import com.github.jberkel.pay.me.listener.OnIabPurchaseFinishedListener;
import com.github.jberkel.pay.me.listener.OnIabSetupFinishedListener;
import com.github.jberkel.pay.me.listener.QueryInventoryFinishedListener;
import com.github.jberkel.pay.me.model.Inventory;
import com.github.jberkel.pay.me.model.ItemType;
import com.github.jberkel.pay.me.model.Purchase;

import androidx.appcompat.app.AppCompatActivity;
import at.tomtasche.reader.background.BillingPreferences;

public class BillingManager {

    public static final int PURCHASE_CODE = 1337;

    public static final String BILLING_PRODUCT_YEAR = "remove_ads_for_1y";
    public static final String BILLING_PRODUCT_FOREVER = "remove_ads_for_eva";
    public static final String BILLING_PRODUCT_LOVE = "love_and_everything";

    private boolean enabled;

    private AnalyticsManager analyticsManager;
    private AdManager adManager;

    private IabHelper billingHelper;
    private BillingPreferences billingPreferences;

    public void initialize(Context context, AnalyticsManager analyticsManager, AdManager adManager, CrashManager crashManager) {
        this.adManager = adManager;

        if (!enabled) {
            adManager.showGoogleAds();

            return;
        }

        this.analyticsManager = analyticsManager;

        billingPreferences = new BillingPreferences(context);
        billingHelper = new IabHelper(context, getPublicKey());

        if (billingPreferences.hasPurchased()) {
            adManager.removeAds();

            return;
        }

        try {
            billingHelper.startSetup(new OnIabSetupFinishedListener() {

                @Override
                public void onIabSetupFinished(IabResult result) {
                    if (result.isFailure()) {
                        enabled = false;

                        adManager.showGoogleAds();
                    } else if (result.isSuccess()) {
                        billingHelper.queryInventoryAsync(new QueryInventoryFinishedListener() {

                            @Override
                            public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                                if (result.isSuccess()) {
                                    boolean purchased = inv.getPurchase(BILLING_PRODUCT_FOREVER) != null;
                                    purchased |= inv.getPurchase(BILLING_PRODUCT_YEAR) != null;
                                    purchased |= inv.getPurchase(BILLING_PRODUCT_LOVE) != null;

                                    if (purchased) {
                                        adManager.removeAds();
                                    } else {
                                        adManager.showGoogleAds();
                                    }

                                    billingPreferences.setPurchased(purchased);
                                } else {
                                    adManager.showGoogleAds();
                                }
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            crashManager.log(e);

            enabled = false;

            adManager.showGoogleAds();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void startPurchase(AppCompatActivity activity, String productSku) {
        if (!enabled) {
            return;
        }

        billingHelper.launchPurchaseFlow(activity, productSku, ItemType.INAPP, PURCHASE_CODE,
                new OnIabPurchaseFinishedListener() {
                    public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                        if (result.isSuccess()) {
                            billingPreferences.setPurchased(true);

                            adManager.removeAds();
                        } else {
                            analyticsManager.report("purchase_abort");
                        }
                    }
                }, null);

        analyticsManager.report("purchase_attempt");
    }

    public void endPurchase(int requestCode, int resultCode, Intent intent) {
        billingHelper.handleActivityResult(requestCode, resultCode, intent);
    }

    public void close() {
        if (!enabled) {
            return;
        }

        if (billingHelper == null) {
            return;
        }

        billingHelper.dispose();
    }

    private String getPublicKey() {
        return "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDsdGybFkj9/26Fpu2mNASpAC8xQDRYocvVkxbpN6mF8k4a9L5ocnyUAY7sfKb0wjEc5e+vxL21kFKvvW0zEZX8a5wSXUfD5oiaXaiMPrp7cC1YbPPAelZvFEAzriA6pyk7PPKuqtAN2tcTiJED+kpiVAyEVU42lDUqE70xlRE6dQIDAQAB";
    }
}
