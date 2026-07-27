package app.opendocument.droid.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Base64
import android.util.Base64InputStream
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.opendocument.droid.background.AndroidFileCache
import app.opendocument.droid.background.OnlineLoader
import app.opendocument.droid.background.StreamUtil
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.ui.ParagraphListener
import app.opendocument.droid.ui.activity.DocumentFragment
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * The WebView the documents are displayed in, plus the javascript bridge the page talks back on.
 */
@SuppressLint("SetJavaScriptEnabled")
class PageView
@SuppressLint("AddJavascriptInterface")
constructor(context: Context, attributeSet: AttributeSet?) :
    WebView(context, attributeSet), ParagraphListener {

    private var paragraphListener: ParagraphListener? = null

    private lateinit var documentFragment: DocumentFragment
    private lateinit var crashManager: CrashManager

    private var htmlCallback: HtmlCallback? = null

    /**
     * sometimes the page stays invisible after reporting progress 100:
     * https://stackoverflow.com/q/48082474/198996
     *
     * this seems to happen if progress 100 is reported before finish is called. therefore we set a
     * timer in finish that checks if commit was ever called and reload if not.
     */
    private val buggyWebViewHandler = Handler(Looper.getMainLooper())

    private var wasCommitCalled = false

    init {
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.defaultTextEncodingName = StreamUtil.ENCODING
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = true

        // setWebContentsDebuggingEnabled(true)

        addJavascriptInterface(this, "paragraphListener")

        keepScreenOn = true
        try {
            val method = context.javaClass.getMethod("setSystemUiVisibility", Integer::class.java)
            method.invoke(context, 1)
        } catch (e: Exception) {
            // the context is not an activity that would hide the system ui
        }

        webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    buggyWebViewHandler.postDelayed(
                        {
                            if (!wasCommitCalled) {
                                crashManager.log(RuntimeException("commit was not called"))

                                loadUrl(url)
                            }
                        },
                        2500,
                    )
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    wasCommitCalled = true
                }

                // a document that fails to load leaves chrome's own error page on screen and
                // said nothing to anyone. that cost a full round of ci archaeology to work out
                // from the outside, so the main frame failing is now reported
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)

                    if (!request.isForMainFrame) {
                        return
                    }

                    crashManager.log(
                        RuntimeException(
                            "loading ${request.url} failed: ${error.errorCode} ${error.description}"
                        )
                    )
                }

                @Suppress("DEPRECATION") // the request based overload needs API 24 semantics
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (
                        url.startsWith(OnlineLoader.GOOGLE_VIEWER_URL) ||
                            url.startsWith(OnlineLoader.MICROSOFT_VIEWER_URL) ||
                            url.contains("officeapps.live.com/")
                    ) {
                        return false
                    }

                    return try {
                        getContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

                        true
                    } catch (e: Exception) {
                        crashManager.log(e)

                        false
                    }
                }
            }

        // taken from: https://stackoverflow.com/a/10069265/198996
        setDownloadListener { url, _, _, _, _ ->
            try {
                getContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                crashManager.log(e)
            }
        }
    }

    @Suppress("DEPRECATION") // setForceDarkAllowed and setForceDark are the pre-webkit-1.6 api
    fun toggleDarkMode(isDarkEnabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = isDarkEnabled
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkEnabled)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                settings,
                if (isDarkEnabled) WebSettingsCompat.FORCE_DARK_AUTO
                else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }
    }

    override fun loadUrl(url: String) {
        wasCommitCalled = false

        super.loadUrl(url)
    }

    fun setDocumentFragment(documentFragment: DocumentFragment) {
        this.documentFragment = documentFragment
        this.crashManager = documentFragment.crashManager
    }

    fun setParagraphListener(paragraphListener: ParagraphListener) {
        this.paragraphListener = paragraphListener
    }

    fun getParagraph(index: Int) {
        post {
            loadUrl(
                "javascript:var children = document.body.childNodes; " +
                    "if (children.length <= $index) { " +
                    "paragraphListener.end();" +
                    "} else {" +
                    "var child = children[$index]; " +
                    "if (child && child.nodeName.toLowerCase() != 'script' && child.innerText) {" +
                    " paragraphListener.paragraph(child.innerText); } else {" +
                    " paragraphListener.increaseIndex(); } }"
            )
        }
    }

    fun requestHtml(callback: HtmlCallback) {
        this.htmlCallback = callback

        loadUrl("javascript:window.paragraphListener.sendHtml(odr.generateDiff());")
    }

    @JavascriptInterface
    @Keep
    fun sendHtml(htmlDiff: String) {
        htmlCallback?.onHtml(htmlDiff)
    }

    @JavascriptInterface
    @Keep
    fun sendFile(base64: String) {
        try {
            val tmpFile = AndroidFileCache.createCacheFile(context)

            ByteArrayInputStream(base64.toByteArray(charset(StreamUtil.ENCODING))).use { inputStream
                ->
                StreamUtil.copy(Base64InputStream(inputStream, Base64.NO_WRAP), tmpFile)
            }

            post {
                documentFragment.loadUri(AndroidFileCache.getCacheFileUri(context, tmpFile), false)
            }
        } catch (e: IOException) {
            crashManager.log(e)
        }
    }

    @Keep
    @JavascriptInterface
    override fun paragraph(text: String?) {
        paragraphListener?.paragraph(text)
    }

    @Keep
    @JavascriptInterface
    override fun increaseIndex() {
        paragraphListener?.increaseIndex()
    }

    @Keep
    @JavascriptInterface
    override fun end() {
        paragraphListener?.end()
    }

    fun interface HtmlCallback {

        fun onHtml(htmlDiff: String)
    }
}
