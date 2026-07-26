package app.opendocument.droid.nonfree;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.util.List;

public class AdManager {

    private static final String AD_UNIT_ID = "ca-app-pub-8161473686436957/5931994762";

    private boolean enabled;

    private Activity activity;
    private CrashManager crashManager;
    private AnalyticsManager analyticsManager;
    private LinearLayout adContainer;
    private AdView adView;

    public void initialize(
            Activity activity, AnalyticsManager analyticsManager, CrashManager crashManager) {
        if (!enabled) {
            return;
        }

        this.activity = activity;
        this.crashManager = crashManager;
        this.analyticsManager = analyticsManager;

        try {
            MobileAds.initialize(activity);
        } catch (Throwable e) {
            // java.lang.VerifyError: com/google/android/gms/ads/internal/ClientApi
            crashManager.log(e);

            enabled = false;
        }

        List<String> testDeviceIds = List.of("46C05048B04145D0724C1ADA7FC17619");
        RequestConfiguration configuration =
                new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
        MobileAds.setRequestConfiguration(configuration);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAdContainer(LinearLayout adContainer) {
        this.adContainer = adContainer;
    }

    private void showAds(AdView adView) {
        if (!enabled) {
            return;
        }

        this.adView = adView;

        adContainer.removeAllViews();

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
        adContainer.addView(adView, params);

        adContainer.setVisibility(View.VISIBLE);
    }

    private void hideGoogleAds() {
        activity.runOnUiThread(() -> adContainer.setVisibility(View.GONE));
    }

    public void showGoogleAds() {
        if (!enabled) {
            return;
        }

        ConsentRequestParameters params =
                new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build();

        ConsentInformation consentInformation =
                UserMessagingPlatform.getConsentInformation(activity);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            activity,
                            loadAndShowError -> {
                                if (loadAndShowError != null
                                        || !consentInformation.canRequestAds()) {
                                    hideGoogleAds();

                                    return;
                                }

                                activity.runOnUiThread(this::showAdaptiveBanner);
                            });
                },
                requestConsentError -> hideGoogleAds());
    }

    // https://developers.google.com/admob/android/banner/adaptive
    private void showAdaptiveBanner() {
        AdView adView = new AdView(activity);

        // WindowManager.getDefaultDisplay() is deprecated and its replacement needs API
        // 30; the resources' metrics carry the same width and density.
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int adWidth = (int) (metrics.widthPixels / metrics.density);

        adView.setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth));
        adView.setAdUnitId(AD_UNIT_ID);
        adView.loadAd(new AdRequest.Builder().build());

        showAds(adView);
    }

    public void removeAds() {
        enabled = false;

        hideGoogleAds();
    }

    public void destroyAds() {
        try {
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)
            if (adView != null) {
                adView.destroy();
            }
        } catch (Exception e) {
            crashManager.log(e);
        }
    }
}
