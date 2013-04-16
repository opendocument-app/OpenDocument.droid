package at.tomtasche.reader.ui.widget;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@SuppressLint("SetJavaScriptEnabled")
public class PageView extends WebView {

    protected static final String ENCODING = "UTF-8";

    private boolean scrolled;

    public PageView(Context context) {
	this(context, 0);
    }

    public PageView(Context context, final int scroll) {
	super(context);

	WebSettings settings = getSettings();
	settings.setBuiltInZoomControls(true);
	settings.setLightTouchEnabled(true);
	settings.setSupportZoom(true);
	settings.setDefaultTextEncodingName(ENCODING);
	settings.setJavaScriptEnabled(true);
	settings.setUseWideViewPort(true);

	setInitialScale(1);

	setKeepScreenOn(true);
	if (Build.VERSION.SDK_INT >= 14)
	    try {
		Method method = context.getClass().getMethod(
			"setSystemUiVisibility", Integer.class);
		method.invoke(context, 1);
	    } catch (Exception e) {
	    }

	setWebViewClient(new WebViewClient() {

	    @Override
	    public void onPageFinished(WebView view, String url) {
		super.onPageFinished(view, url);

		if (!scrolled) {
		    postDelayed(new Runnable() {

			@Override
			public void run() {
			    scrollBy(0, scroll);
			}
		    }, 250);

		    scrolled = true;
		}
	    }

	    @Override
	    public boolean shouldOverrideUrlLoading(WebView view, String url) {
		if (url.startsWith("https://docs.google.com/viewer?embedded=true")) {
		    return false;
		} else {
		    getContext().startActivity(
			    new Intent(Intent.ACTION_VIEW, Uri.parse(url)));

		    return true;
		}
	    }
	});
    }
}
