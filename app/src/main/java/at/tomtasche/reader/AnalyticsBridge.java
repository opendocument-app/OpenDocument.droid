package at.tomtasche.reader;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import android.content.Context;

public class AnalyticsBridge {

	private GoogleAnalytics analytics;
	private Tracker tracker;

	public void initialize(Context context) {
		analytics = GoogleAnalytics.getInstance(context);
		tracker = analytics.newTracker("UA-41583346-1");
		tracker.setAnonymizeIp(true);
		tracker.setUseSecure(true);
		tracker.enableAutoActivityTracking(true);
		tracker.enableExceptionReporting(true);
	}

	public void sendEvent(String category, String action, String label) {
		tracker.send(new HitBuilders.EventBuilder().setCategory(category).setAction(action).setLabel(label).build());
	}

	public void sendException(String message, Throwable error) {
		// TODO: implement
	}
}
