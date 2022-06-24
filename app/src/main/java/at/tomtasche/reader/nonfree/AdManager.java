package at.tomtasche.reader.nonfree;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public class AdManager {

    private boolean enabled;

    private Activity activity;
    private CrashManager crashManager;
    private AnalyticsManager analyticsManager;
    private ConfigManager configManager;

    private LinearLayout adContainer;
    private AdView adView;

    public void initialize(Activity activity, AnalyticsManager analyticsManager, CrashManager crashManager, ConfigManager configManager) {
        if (!enabled) {
            return;
        }

        this.activity = activity;
        this.crashManager = crashManager;
        this.analyticsManager = analyticsManager;
        this.configManager = configManager;

        try {
            MobileAds.initialize(activity);
        } catch (Throwable e) {
            // java.lang.VerifyError: com/google/android/gms/ads/internal/ClientApi
            crashManager.log(e);

            enabled = false;
        }

        List<String> testDeviceIds = Arrays.asList("46C05048B04145D0724C1ADA7FC17619");
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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        adContainer.addView(adView, params);

        adContainer.setVisibility(View.VISIBLE);
    }

    public void showGoogleAds() {
        if (!enabled) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AdView adView = new AdView(activity);

                // https://developers.google.com/admob/android/banner/adaptive
                Display display = activity.getWindowManager().getDefaultDisplay();
                DisplayMetrics outMetrics = new DisplayMetrics();
                display.getMetrics(outMetrics);

                float widthPixels = outMetrics.widthPixels;
                float density = outMetrics.density;

                int adWidth = (int) (widthPixels / density);
                AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
                adView.setAdSize(adSize);

                adView.setAdUnitId("ca-app-pub-8161473686436957/5931994762");
                adView.setAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull @NotNull LoadAdError loadAdError) {
                        analyticsManager.report("ads_banner_failed", "code", loadAdError.getCode());
                    }

                    @Override
                    public void onAdLoaded() {
                        analyticsManager.report("ads_banner_loaded");
                    }

                    @Override
                    public void onAdClicked() {
                        analyticsManager.report("ads_banner_clicked");
                    }

                    @Override
                    public void onAdImpression() {
                        analyticsManager.report("ads_banner_impression");
                    }

                    @Override
                    public void onAdClosed() {
                        analyticsManager.report("ads_banner_closed");
                    }

                    @Override
                    public void onAdOpened() {
                        analyticsManager.report("ads_banner_opened");
                    }
                });

                AdRequest adRequest = new AdRequest.Builder().build();
                adView.loadAd(adRequest);

                showAds(adView);
            }
        });
    }

    public void removeAds() {
        enabled = false;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adContainer.setVisibility(View.GONE);
            }
        });
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
