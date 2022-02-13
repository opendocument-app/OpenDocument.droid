package at.tomtasche.reader.nonfree;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

public class AnalyticsManager {

    private boolean enabled;

    private FirebaseAnalytics analytics;
    private FirebasePerformance performance;

    public void initialize(Context context) {
        if (!enabled) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false);

            FirebasePerformance.getInstance().setPerformanceCollectionEnabled(false);

            return;
        }

        analytics = FirebaseAnalytics.getInstance(context);
        performance = FirebasePerformance.getInstance();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void report(String event) {
        report(event, null, null);
    }

    public void report(String event, String key1, Object value1, String key2, Object value2) {
        if (!enabled) {
            return;
        }

        Bundle bundle = new Bundle();
        if (key1 != null) {
            bundle.putString(key1, String.valueOf(value1));
        }
        if (key2 != null) {
            bundle.putString(key2, String.valueOf(value2));
        }

        analytics.logEvent(event, bundle);
    }

    public void report(String event, String key, Object value) {
        report(event, key, value, null, null);
    }

    public Trace startTrace(String name) {
        if (!enabled) {
            return null;
        }

        Trace trace = performance.newTrace(name);
        trace.start();

        return trace;
    }

    public void stopTrace(Trace trace) {
        if (trace != null) {
            trace.stop();
        }
    }

    public void setCurrentScreen(Activity activity, String name) {
        if (!enabled) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, name);
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, activity.getClass().getSimpleName());
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }
}
