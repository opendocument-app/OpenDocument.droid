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
     * Progress 100 reported before the page commits leaves it blank
     * (https://stackoverflow.com/q/48082474/198996), so onPageFinished schedules a reload for
     * whatever never committed.
     */
    private val buggyWebViewHandler = Handler(Looper.getMainLooper())

    private var wasCommitCalled = false

    private var isBridgeAttached = false

    init {
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.defaultTextEncodingName = StreamUtil.ENCODING
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = true

        attachBridge(true)

        keepScreenOn = true

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

                // a failed load otherwise leaves chrome's error page on screen and tells nobody
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
                    // everything shown here is served from localhost, so any link leaves the app
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

    /**
     * Keeps the document on the background it was authored against, whatever the app theme is.
     *
     * The app shell follows night mode, the page does not: inverting a document is a rendering
     * decision the reader has not asked for, and it goes badly on the tables, coloured text and
     * images a real odt or docx is full of. Note this only became a live question when the theme
     * moved to Material3.DayNight - at targetSdk 33 and up the webview only darkens when the app
     * theme reports itself as dark, so under the old AppCompat.Light theme none of this ever fired.
     *
     * There is no parameter to this any more. It used to take an `isDarkModeSupported` that nothing
     * in here ever read - both callers reached the same three lines - while [DocumentFragment] went
     * to the trouble of working out that a pdf is not worth inverting. A user facing "dark
     * documents" setting can grow the argument back when there is something on the other end of it.
     */
    @Suppress("DEPRECATION") // setForceDarkAllowed and setForceDark are the pre-webkit-1.6 api
    fun disableDarkening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = false
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
        }
    }

    override fun loadUrl(url: String) {
        wasCommitCalled = false

        // sendFile writes into the cache and opens what it wrote, so the bridge must not reach
        // the third party viewers an ONLINE result loads here. takes effect on the next load
        if (!url.startsWith(JAVASCRIPT_SCHEME)) {
            attachBridge(isOwnContent(url))
        }

        super.loadUrl(url)
    }

    override fun destroy() {
        // the reload is scheduled 2.5s out, and this also runs when a second document
        // replaces the view - so it must not land on a WebView that is gone
        buggyWebViewHandler.removeCallbacksAndMessages(null)

        super.destroy()
    }

    /** Whether [url] is a document we produced: a cached file, or the core's own http server. */
    private fun isOwnContent(url: String): Boolean =
        url.startsWith("file://") || url.startsWith(LOCAL_SERVER_URL_PREFIX)

    private fun attachBridge(attach: Boolean) {
        if (attach == isBridgeAttached) {
            return
        }

        if (attach) {
            addJavascriptInterface(this, BRIDGE_NAME)
        } else {
            removeJavascriptInterface(BRIDGE_NAME)
        }

        isBridgeAttached = attach
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

        loadUrl("${JAVASCRIPT_SCHEME}window.$BRIDGE_NAME.sendHtml(odr.generateDiff());")
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
                // the user is mid-read, not opening something
                documentFragment.loadUri(
                    AndroidFileCache.getCacheFileUri(context, tmpFile),
                    false,
                    freshOpen = false,
                )
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

    private companion object {

        const val BRIDGE_NAME = "paragraphListener"

        const val JAVASCRIPT_SCHEME = "javascript:"

        /** Where CoreLoader publishes a translated document. */
        const val LOCAL_SERVER_URL_PREFIX = "http://localhost:"
    }
}
