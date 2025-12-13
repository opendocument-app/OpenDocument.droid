package at.tomtasche.reader.nonfree;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class AnalyticsManager {

    private boolean enabled;

    public void initialize(Context context) {
        return;
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

        Log.i("smn", event);
    }

    public void report(String event, String key, Object value) {
        report(event, key, value, null, null);
    }

    public void setCurrentScreen(Activity activity, String name) {
        if (!enabled) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString("screen_name", name);
        bundle.putString("screen_class", activity.getClass().getSimpleName());

        Log.i("smn", name);
    }
}
