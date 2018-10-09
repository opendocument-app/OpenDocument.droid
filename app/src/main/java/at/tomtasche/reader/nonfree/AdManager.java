package at.tomtasche.reader.nonfree;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;
import de.keyboardsurfer.android.widget.crouton.Style;

public class AdManager {

    private boolean enabled;

    private Context applicationContext;
    private AnalyticsManager analyticsManager;
    private Runnable onAdFailed;

    private boolean showAds;

    private LinearLayout adContainer;
    private AdView madView;
    private InterstitialAd interstitial;

    public void initialize(Context applicationContext, AnalyticsManager analyticsManager, Runnable onAdFailed) {
        if (!enabled) {
            return;
        }

        this.applicationContext = applicationContext;
        this.analyticsManager = analyticsManager;
        this.onAdFailed = onAdFailed;

        MobileAds.initialize(applicationContext, "ca-app-pub-8161473686436957~9025061963");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAdContainer(LinearLayout adContainer) {
        this.adContainer = adContainer;
    }

    public void showAds(AdView adView) {
        if (!enabled) {
            return;
        }

        this.madView = adView;

        adContainer.removeAllViews();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        adContainer.addView(adView, params);
    }

    public void showGoogleAds() {
        if (!enabled) {
            return;
        }

        showAds = true;

        AdView adView = new AdView(applicationContext);
        adView.setAdSize(AdSize.SMART_BANNER);
        adView.setAdUnitId("ca-app-pub-8161473686436957/5931994762");
        adView.setAdListener(new MyAdListener());

        AdRequest adRequest = new AdRequest.Builder().build();

        adView.loadAd(adRequest);

        showAds(adView);
    }

    public void loadInterstitial() {
        if (!enabled) {
            return;
        }

        if (!showAds) {
            return;
        }

        interstitial = new InterstitialAd(applicationContext);
        interstitial.setAdUnitId("ca-app-pub-8161473686436957/2477707165");

        AdRequest adRequest = new AdRequest.Builder().build();

        interstitial.loadAd(adRequest);
        interstitial.setAdListener(new MyAdListener());
    }

    public void showInterstitial() {
        if (!enabled) {
            return;
        }

        if (interstitial != null) {
            interstitial.show();
        }

        interstitial = null;

        loadInterstitial();
    }

    public void removeAds() {
        showAds = false;

        if (madView != null) {
            madView.setVisibility(View.GONE);
        }

        analyticsManager.report("remove_ads");
    }

    public void destroyAds() {
        try {
            // keeps throwing exceptions for some users:
            // Caused by: java.lang.NullPointerException
            // android.webkit.WebViewClassic.requestFocus(WebViewClassic.java:9898)
            // android.webkit.WebView.requestFocus(WebView.java:2133)
            // android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:2384)
            if (madView != null) {
                ((AdView) madView).destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class MyAdListener extends AdListener {

        @Override
        public void onAdFailedToLoad(int arg0) {
            onAdFailed.run();
        }

        @Override
        public void onAdClicked() {}

        @Override
        public void onAdLoaded() {
            if (interstitial != null) {
                analyticsManager.report("ads_interstitial_shown");
            }
        }
    }
}
