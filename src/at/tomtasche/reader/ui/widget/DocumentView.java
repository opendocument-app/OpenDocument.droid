package at.tomtasche.reader.ui.widget;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import at.tomtasche.reader.R;

public class DocumentView extends WebView {

    private static final String ENCODING = "UTF-8";

    public DocumentView(final Context context) {
	super(context);

	final WebSettings settings = getSettings();
	settings.setBuiltInZoomControls(true);
	settings.setLightTouchEnabled(true);
	settings.setSupportZoom(true);
	settings.setPluginsEnabled(false);
	settings.setDefaultTextEncodingName(ENCODING);

	loadData(context.getString(R.string.message_get_started), "text/plain", ENCODING);
    }

    public void loadData(final String html) {
	loadData(html, "text/html", ENCODING);
    }
}
