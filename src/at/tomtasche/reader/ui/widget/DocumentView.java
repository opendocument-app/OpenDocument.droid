package at.tomtasche.reader.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class DocumentView extends WebView {

	protected static final String ENCODING = "UTF-8";

	@SuppressLint("NewApi")
	public DocumentView(final Context context) {
		super(context);

		final WebSettings settings = getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setLightTouchEnabled(true);
		settings.setSupportZoom(true);
		settings.setDefaultTextEncodingName(ENCODING);
		try {
			settings.setDisplayZoomControls(true);
		} catch (Exception e) {
		}

		setKeepScreenOn(true);

		setWebViewClient(new WebViewClient() {

			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);

				// TODO: hide progressbar
			}
		});
	}
}
