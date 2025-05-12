package at.tomtasche.reader.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Base64InputStream;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import androidx.annotation.Keep;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.OnlineLoader;
import at.tomtasche.reader.background.StreamUtil;
import at.tomtasche.reader.nonfree.CrashManager;
import at.tomtasche.reader.ui.ParagraphListener;
import at.tomtasche.reader.ui.activity.DocumentFragment;

@SuppressLint("SetJavaScriptEnabled")
public class PageView extends WebView implements ParagraphListener {

    private ParagraphListener paragraphListener;

    private DocumentFragment documentFragment;
    private CrashManager crashManager;

    private HtmlCallback htmlCallback;

    /**
     * sometimes the page stays invisible after reporting progress 100: https://stackoverflow.com/q/48082474/198996
     * <p>
     * this seems to happen if progress 100 is reported before finish is called.
     * therefore we set a timer in finish that checks if commit was ever called and reload if not.
     */
    private final Handler buggyWebViewHandler;
    private boolean wasCommitCalled = false;

    @SuppressLint("AddJavascriptInterface")
    public PageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        buggyWebViewHandler = new Handler();

        WebSettings settings = getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setDefaultTextEncodingName(StreamUtil.ENCODING);
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);

        //WebView.setWebContentsDebuggingEnabled(true);

        addJavascriptInterface(this, "paragraphListener");

        setKeepScreenOn(true);
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    buggyWebViewHandler.postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            if (!wasCommitCalled) {
                                crashManager.log(new RuntimeException("commit was not called"));

                                loadUrl(url);
                            }
                        }
                    }, 2500);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                wasCommitCalled = true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(OnlineLoader.GOOGLE_VIEWER_URL) || url.startsWith(OnlineLoader.MICROSOFT_VIEWER_URL) || url.contains("officeapps.live.com/")) {
                    return false;
                } else {
                    try {
                        getContext().startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)));

                        return true;
                    } catch (Exception e) {
                        crashManager.log(e);

                        return false;
                    }
                }
            }
        });

        // taken from: https://stackoverflow.com/a/10069265/198996
        setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                try {
                    getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    crashManager.log(e);
                }
            }
        });
    }

    public void toggleDarkMode(boolean isDarkEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDarkAllowed(isDarkEnabled);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(getSettings(), isDarkEnabled);
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(getSettings(), isDarkEnabled ? WebSettingsCompat.FORCE_DARK_AUTO : WebSettingsCompat.FORCE_DARK_OFF);
        }
    }

    @Override
    public void loadUrl(String url) {
        wasCommitCalled = false;

        super.loadUrl(url);
    }

    public void setDocumentFragment(DocumentFragment documentFragment) {
        this.documentFragment = documentFragment;
        this.crashManager = documentFragment.getCrashManager();
    }

    public void setParagraphListener(ParagraphListener paragraphListener) {
        this.paragraphListener = paragraphListener;
    }

    public void getParagraph(final int index) {
        post(new Runnable() {

            @Override
            public void run() {
                loadUrl("javascript:var children = document.body.childNodes; "
                        + "if (children.length <= " + index + ") { "
                        + "paragraphListener.end();" + "} else {"
                        + "var child = children[" + index + "]; "
                        + "if (child && child.nodeName.toLowerCase() != 'script' && child.innerText) { "
                        + "paragraphListener.paragraph(child.innerText); "
                        + "} else { " + "paragraphListener.increaseIndex(); "
                        + "} }");
            }
        });
    }

    public void requestHtml(HtmlCallback callback) {
        this.htmlCallback = callback;

        loadUrl("javascript:window.paragraphListener.sendHtml(odr.generateDiff());");
    }

    @JavascriptInterface
    @Keep
    public void sendHtml(String htmlDiff) {
        htmlCallback.onHtml(htmlDiff);
    }

    @JavascriptInterface
    @Keep
    public void sendFile(String base64) {
        try {
            File tmpFile = AndroidFileCache.createCacheFile(getContext());

            ByteArrayInputStream inputStream = new ByteArrayInputStream(base64.getBytes(StreamUtil.ENCODING));
            Base64InputStream baseInputStream = new Base64InputStream(inputStream, Base64.NO_WRAP);
            try {
                StreamUtil.copy(baseInputStream, tmpFile);
            } finally {
                inputStream.close();
            }

            post(new Runnable() {
                @Override
                public void run() {
                    documentFragment.loadUri(AndroidFileCache.getCacheFileUri(getContext(), tmpFile), false);
                }
            });
        } catch (IOException e) {
            crashManager.log(e);
        }
    }

    @Override
    @Keep
    @JavascriptInterface
    public void paragraph(String text) {
        paragraphListener.paragraph(text);
    }

    @Override
    @Keep
    @JavascriptInterface
    public void increaseIndex() {
        paragraphListener.increaseIndex();
    }

    @Override
    @Keep
    @JavascriptInterface
    public void end() {
        paragraphListener.end();
    }

    public interface HtmlCallback {

        void onHtml(String htmlDiff);
    }
}
