package at.tomtasche.reader.ui.widget;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class DocumentView extends WebView {

	protected static final String ENCODING = "UTF-8";

	public DocumentView(Context context) {
		super(context);

		final WebSettings settings = getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setLightTouchEnabled(true);
		settings.setSupportZoom(true);
		settings.setDefaultTextEncodingName(ENCODING);

		setKeepScreenOn(true);
	}
}
