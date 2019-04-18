package at.tomtasche.reader.nonfree;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.LinearLayout;

import com.apptutti.ad.ADManager;
import com.apptutti.ad.SuperADPayListener;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

public class AdManager implements SuperADPayListener {

    private boolean enabled;

    private Activity activity;
    private AnalyticsManager analyticsManager;

    private boolean showAds;
    private boolean adFailed;

    private ProgressDialog progressDialog;

    private ADManager chinaAds;

    public void initialize(Activity activity, AnalyticsManager analyticsManager) {
        if (!enabled) {
            return;
        }

        this.activity = activity;
        this.analyticsManager = analyticsManager;

        chinaAds = ADManager.getInstance();
        chinaAds.init(activity, this);
    }

    public void onResume() {
        if (!enabled) {
            return;
        }

        if (chinaAds != null) {
            chinaAds.onResume(activity);
        }
    }

    public void onPause() {
        if (!enabled) {
            return;
        }

        if (chinaAds != null) {
            chinaAds.onPause(activity);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setOnAdFailedCallback(Runnable runnable) {
    }

    public void setAdContainer(LinearLayout adContainer) {
    }

    public void showGoogleAds() {
        if (!enabled) {
            return;
        }

        showAds = true;
        adFailed = false;
    }

    public void loadInterstitial() {
    }

    public void showInterstitial() {
        if (!enabled) {
            return;
        }

        try {
            chinaAds.interstitialAds(activity);
        } catch (Exception e) {
            e.printStackTrace();

            // very rarely crashes with "The ad unit ID must be set on InterstitialAd before show is called."
        }
    }

    public void loadVideo() {
    }

    public void showVideo() {
        if (!enabled) {
            return;
        }

        try {
            chinaAds.videoAds(activity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeAds() {
        showAds = false;
    }

    public void destroyAds() {
    }

    @Override
    public void VideoAdsCallBack() {
        analyticsManager.report("ads_video_rewarded");

        removeAds();
    }

    @Override
    public void VideoAdsLoadSuccess() {
        analyticsManager.report("ads_video_loaded");

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;

            showVideo();
        }
    }
}
