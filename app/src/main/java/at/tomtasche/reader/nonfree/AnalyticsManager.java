package at.tomtasche.reader.nonfree;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

public class AnalyticsManager {

    private boolean enabled;

    private FirebaseAnalytics analytics;

    public void initialize(Context context) {
        if (!enabled) {
            return;
        }

        analytics = FirebaseAnalytics.getInstance(context);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void report(String event) {
        report(event, null, null);
    }

    public void report(String event, String key, String value) {
        if (!enabled) {
            return;
        }

        Bundle bundle = new Bundle();
        if (key != null) {
            bundle.putString(key, value);
        }

        analytics.logEvent(event, bundle);
    }
}
