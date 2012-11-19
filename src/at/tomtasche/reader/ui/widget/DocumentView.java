package at.tomtasche.reader.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class DocumentView extends WebView {

	protected static final String ENCODING = "UTF-8";

	@SuppressLint("NewApi")
	public DocumentView(Context context) {
		super(context);

		final WebSettings settings = getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setLightTouchEnabled(true);
		settings.setSupportZoom(true);
		settings.setDefaultTextEncodingName(ENCODING);

		if (Build.VERSION.SDK_INT >= 14)
			// setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
			setSystemUiVisibility(1);
		setKeepScreenOn(true);
	}
}
