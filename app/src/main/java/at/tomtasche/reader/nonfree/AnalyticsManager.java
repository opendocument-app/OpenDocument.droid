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

    public void report(String event, String key1, String value1, String key2, String value2) {
        if (!enabled) {
            return;
        }

        Bundle bundle = new Bundle();
        if (key1 != null) {
            bundle.putString(key1, value1);
        }
        if (key2 != null) {
            bundle.putString(key2, value2);
        }

        analytics.logEvent(event, bundle);
    }

    public void report(String event, String key, String value) {
        report(event, key, value, null, null);
    }
}
