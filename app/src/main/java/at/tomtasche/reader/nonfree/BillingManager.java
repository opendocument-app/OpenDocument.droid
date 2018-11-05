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

    public void initialize(Context context, AnalyticsManager analyticsManager, AdManager adManager) {
        this.adManager = adManager;

        if (!enabled) {
            return;
        }

        this.analyticsManager = analyticsManager;

        billingPreferences = new BillingPreferences(context);
        billingHelper = new IabHelper(context, getPublicKey());

        billingHelper.startSetup(new OnIabSetupFinishedListener() {

            @Override
            public void onIabSetupFinished(IabResult result) {
                if (billingPreferences.hasPurchased()) {
                    adManager.removeAds();

                    return;
                }

                if (result.isFailure()) {
                    adManager.showGoogleAds();
                } else if (result.isSuccess()) {
                    // query every 7 days
                    if ((billingPreferences.getLastQueryTime() + 1000 * 60 * 60 * 24 * 7) < System
                            .currentTimeMillis()) {
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
                                }

                                billingPreferences.setLastQueryTime(System.currentTimeMillis());
                            }
                        });
                    }
                }
            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void startPurchase(AppCompatActivity activity, String productSku) {
        billingHelper.launchPurchaseFlow(activity, productSku, ItemType.INAPP, PURCHASE_CODE,
                new OnIabPurchaseFinishedListener() {
                    public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                        if (result.isSuccess()) {
                            billingPreferences.setPurchased(true);
                            billingPreferences.setLastQueryTime(System.currentTimeMillis());

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
