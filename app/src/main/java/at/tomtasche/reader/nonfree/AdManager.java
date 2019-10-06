package at.tomtasche.reader.nonfree;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

public class AdManager implements RewardedVideoAdListener {

    private boolean enabled;

    private Activity activity;
    private AnalyticsManager analyticsManager;

    private boolean showAds;
    private boolean adFailed;

    private LinearLayout adContainer;
    private AdView madView;
    private InterstitialAd interstitial;
    private Runnable adFailedRunnable;
    private RewardedVideoAd rewardedVideoAd;
    private ProgressDialog progressDialog;

    public void initialize(Activity activity, AnalyticsManager analyticsManager) {
        if (!enabled) {
            return;
        }

        this.activity = activity;
        this.analyticsManager = analyticsManager;

        try {
            MobileAds.initialize(activity, "ca-app-pub-8161473686436957~9025061963");
        } catch (Throwable e) {
            // java.lang.VerifyError: com/google/android/gms/ads/internal/ClientApi
            e.printStackTrace();

            enabled = false;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setOnAdFailedCallback(Runnable runnable) {
        adFailedRunnable = runnable;
    }

    public void setAdContainer(LinearLayout adContainer) {
        this.adContainer = adContainer;
    }

    private void showAds(AdView adView) {
        if (!enabled) {
            return;
        }

        this.madView = adView;

        adContainer.removeAllViews();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        adContainer.addView(adView, params);

        toggleVisibility(true);
    }

    public void showGoogleAds() {
        if (!enabled) {
            return;
        }

        showAds = true;
        adFailed = false;

        AdView adView = new AdView(activity);
        adView.setAdSize(AdSize.SMART_BANNER);
        adView.setAdUnitId("ca-app-pub-8161473686436957/5931994762");
        adView.setAdListener(new MyAdListener(false));

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        showAds(adView);
    }

    private void toggleVisibility(boolean visible) {
        boolean show = visible && !adFailed && enabled;
        adContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void loadInterstitial() {
        if (!enabled) {
            return;
        }

        if (!showAds) {
            return;
        }

        interstitial = new InterstitialAd(activity);
        interstitial.setAdUnitId("ca-app-pub-8161473686436957/2477707165");
        interstitial.setAdListener(new MyAdListener(true));

        AdRequest adRequest = new AdRequest.Builder().build();

        interstitial.loadAd(adRequest);
    }

    public void showInterstitial() {
        if (!enabled) {
            return;
        }

        if (interstitial != null) {
            if (!interstitial.isLoaded() && interstitial.isLoading()) {
                progressDialog = new ProgressDialog(activity);
                progressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        interstitial = null;
                    }
                });
                progressDialog.show();

                return;
            }

            try {
                interstitial.show();
            } catch (Exception e) {
                e.printStackTrace();

                // very rarely crashes with "The ad unit ID must be set on InterstitialAd before show is called."
            }
        }
    }

    public void loadVideo() {
        if (!enabled) {
            return;
        }

        rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);
        rewardedVideoAd.setRewardedVideoAdListener(this);

        rewardedVideoAd.loadAd("ca-app-pub-8161473686436957/7215347444", new AdRequest.Builder().build());
    }

    public void showVideo() {
        if (!enabled) {
            return;
        }

        if (rewardedVideoAd != null) {
            if (!rewardedVideoAd.isLoaded()) {
                progressDialog = new ProgressDialog(activity);
                progressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        rewardedVideoAd = null;
                    }
                });
                progressDialog.show();

                return;
            }

            try {
                rewardedVideoAd.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        rewardedVideoAd = null;
    }

    public void removeAds() {
        showAds = false;

        adContainer.setVisibility(View.GONE);
    }

    public void destroyAds() {
        interstitial = null;

        try {
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)
            if (madView != null) {
                madView.destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRewardedVideoAdLoaded() {
        analyticsManager.report("ads_video_loaded");

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;

            showVideo();
        }
    }

    @Override
    public void onRewardedVideoAdOpened() {
        analyticsManager.report("ads_video_opened");
    }

    @Override
    public void onRewardedVideoStarted() {
        analyticsManager.report("ads_video_started");
    }

    @Override
    public void onRewardedVideoAdClosed() {
        analyticsManager.report("ads_video_closed");
    }

    @Override
    public void onRewarded(RewardItem rewardItem) {
        analyticsManager.report("ads_video_rewarded");

        removeAds();
    }

    @Override
    public void onRewardedVideoAdLeftApplication() {
        analyticsManager.report("ads_video_left");
    }

    @Override
    public void onRewardedVideoAdFailedToLoad(int i) {
        analyticsManager.report("ads_video_failed_" + i);

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }

        rewardedVideoAd = null;

        removeAds();
    }

    @Override
    public void onRewardedVideoCompleted() {
        analyticsManager.report("ads_video_completed");
    }

    private class MyAdListener extends AdListener {

        private boolean isInterstitial;
        private String prefix;

        MyAdListener(boolean isInterstitial) {
            this.isInterstitial = isInterstitial;
            this.prefix = isInterstitial ? "interstitial" : "banner";
        }

        @Override
        public void onAdFailedToLoad(int arg0) {
            analyticsManager.report("ads_" + prefix + "_failed_" + arg0);

            if (!isInterstitial) {
                adFailed = true;
                toggleVisibility(false);
                adFailedRunnable.run();
            } else if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;

                interstitial = null;
            }
        }

        @Override
        public void onAdLoaded() {
            analyticsManager.report("ads_" + prefix + "_loaded");

            if (isInterstitial && progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;

                showInterstitial();
            }
        }

        @Override
        public void onAdClicked() {
            analyticsManager.report("ads_" + prefix + "clicked");
        }

        @Override
        public void onAdImpression() {
            analyticsManager.report("ads_" + prefix + "impression");
        }

        @Override
        public void onAdClosed() {
            analyticsManager.report("ads_" + prefix + "closed");

            if (isInterstitial) {
                interstitial = null;
            }
        }

        @Override
        public void onAdOpened() {
            analyticsManager.report("ads_" + prefix + "opened");
        }
    }
}
