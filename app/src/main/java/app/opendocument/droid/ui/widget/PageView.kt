package app.opendocument.droid.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.opendocument.droid.background.FileCache
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

    /** What [loadUrl] was last given: the only page whose failure is this document's. */
    private var loadedUrl: String? = null

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

        // the webview refuses to draw text below 8px - 6pt - which is a size a document is
        // entitled to ask for: a footnote, a table's fine print, or a pdf page whose text is
        // positioned absolutely and overlaps once it is enlarged. 1 is as close to none as
        // the setting goes, 0 being pinned back up to it
        settings.minimumFontSize = 1
        // the same floor again, for the sizes the page leaves to the browser - keywords,
        // percentages, anything inherited - which the first of the two does not cover
        settings.minimumLogicalFontSize = 1

        attachBridge(true)

        keepScreenOn = true

        webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    restorePendingScroll(0)

                    buggyWebViewHandler.postDelayed(
                        {
                            // [url] and not whatever is loaded now: this callback can arrive after
                            // another page was asked for, which cancels the retries queued until
                            // then but not the one queued here. wasCommitCalled is about the page
                            // being waited on, so on its own it would answer for that other page
                            // and put this one back over it
                            if (!wasCommitCalled && url == loadedUrl) {
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

                    failPage(
                        request.url,
                        "loading ${request.url} failed: ${error.errorCode} ${error.description}",
                    )
                }

                /**
                 * An error status is the only shape a rendering failure has here: the core
                 * translates a page on the server thread, long after `CoreLoader` reported success.
                 * [onReceivedError] never sees it - the server did answer.
                 */
                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)

                    if (!request.isForMainFrame) {
                        return
                    }

                    failPage(
                        request.url,
                        "serving ${request.url} failed: " +
                            "${errorResponse.statusCode} ${errorResponse.reasonPhrase}",
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
     * Where the page sits, as a fraction of what there is to scroll.
     *
     * A fraction and not the offset: the one thing that reloads a document in place is a change to
     * how it is laid out, which changes the height an offset would mean anything against.
     */
    val verticalScrollFraction: Float
        get() {
            val scrollable = verticalScrollableHeight

            return if (scrollable <= 0) 0f
            else (computeVerticalScrollOffset().toFloat() / scrollable).coerceIn(0f, 1f)
        }

    /** How far the page can be scrolled: its height less the screenful already showing. */
    val verticalScrollableHeight: Int
        get() = computeVerticalScrollRange() - computeVerticalScrollExtent()

    private var scrollFractionToRestore: Float? = null

    /** The height the last attempt at restoring measured, to see whether it is still growing. */
    private var lastScrollableHeight = -1

    private val scrollRestoreHandler = Handler(Looper.getMainLooper())

    /**
     * Puts the next page loaded back to [fraction] of its height.
     *
     * Not applied here: the page is still being laid out when the load reports itself finished.
     */
    fun restoreScrollFraction(fraction: Float) {
        scrollFractionToRestore = fraction.takeIf { it > 0f }
        lastScrollableHeight = -1
    }

    /**
     * Waits for a height that has stopped growing and scrolls to it - a long document goes on being
     * laid out, and the first height it reports lands near the top of where the reader was. Gives
     * up after [SCROLL_RESTORE_ATTEMPTS], leaving the page where it is.
     */
    private fun restorePendingScroll(attempt: Int) {
        val fraction = scrollFractionToRestore ?: return

        val scrollable = verticalScrollableHeight

        if (
            (scrollable <= 0 || scrollable != lastScrollableHeight) &&
                attempt < SCROLL_RESTORE_ATTEMPTS
        ) {
            lastScrollableHeight = scrollable

            scrollRestoreHandler.postDelayed(
                { restorePendingScroll(attempt + 1) },
                SCROLL_RESTORE_INTERVAL_MS,
            )

            return
        }

        scrollFractionToRestore = null

        if (scrollable > 0) {
            scrollTo(scrollX, (fraction * scrollable).toInt())
        }
    }

    /** What [setDarkeningAllowed] was last set to, whether or not printing has it suspended. */
    var isDarkeningAllowed = false
        private set

    /**
     * How many print jobs are still reading the page. Counted rather than a flag: the framework
     * keeps reading well after `print()` returns, so a second job can start while the first is
     * still spooling, and the page may only darken again once the last of them is done.
     */
    private var darkeningSuspensions = 0

    /**
     * Whether the document follows the app into night mode, which is only ever a question while the
     * app is in it: the webview darkens a page algorithmically, and at targetSdk 33 and up only
     * once the app theme reports itself as dark.
     *
     * [DocumentFragment] decides which documents get it, from `DocumentDarkening`.
     */
    fun setDarkeningAllowed(allowed: Boolean) {
        isDarkeningAllowed = allowed

        applyDarkening()
    }

    /**
     * Holds the page light while the print framework reads it - printing a darkened document wastes
     * ink. Every call has to be matched by a [resumeDarkening], or the rest of the document is read
     * in light mode.
     */
    fun suspendDarkening() {
        darkeningSuspensions++

        applyDarkening()
    }

    fun resumeDarkening() {
        if (darkeningSuspensions > 0) {
            darkeningSuspensions--
        }

        applyDarkening()
    }

    @Suppress("DEPRECATION") // setForceDarkAllowed and setForceDark are the pre-webkit-1.6 api
    private fun applyDarkening() {
        val darken = isDarkeningAllowed && darkeningSuspensions == 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = darken
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darken)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            // invert rather than stand aside for the page's own dark theme, which is the default.
            // Every page carries one now, but a webview old enough for this branch answers
            // prefers-color-scheme by the system alone, so standing aside leaves the page light
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY,
                )
            }

            // ON rather than AUTO on the pre-webkit-1.6 api: AUTO is the platform's smart dark,
            // which an app declaring a dark theme is deliberately left out of, so it never fires
            // here. Asking the app whether it is in night mode is what AUTO cannot do for us
            WebSettingsCompat.setForceDark(
                settings,
                if (darken && isInNightMode()) WebSettingsCompat.FORCE_DARK_ON
                else WebSettingsCompat.FORCE_DARK_OFF,
            )
        }
    }

    private fun isInNightMode() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    override fun loadUrl(url: String) {
        wasCommitCalled = false

        // sendFile writes into the cache and opens what it wrote, so the bridge must not reach
        // the third party viewers an ONLINE result loads here. takes effect on the next load
        if (!url.startsWith(JAVASCRIPT_SCHEME)) {
            attachBridge(isOwnContent(url))

            // a page that never committed left a retry waiting in onPageFinished. Now that another
            // page has been asked for, that retry would load the old one back over it - and the
            // document it belonged to has taken its server with it, so what it would find there is
            // a 404 this page is then given up on for
            buggyWebViewHandler.removeCallbacksAndMessages(null)

            loadedUrl = url
        }

        super.loadUrl(url)
    }

    override fun destroy() {
        // the reload is scheduled 2.5s out, and this also runs when a second document
        // replaces the view - so it must not land on a WebView that is gone
        buggyWebViewHandler.removeCallbacksAndMessages(null)

        super.destroy()
    }

    /** Reports a page that will never appear. [description] is all there is of the cause. */
    private fun failPage(url: Uri, description: String) {
        crashManager.log(RuntimeException(description))

        // only the document is the app's to give up on. a link shouldOverrideUrlLoading could not
        // hand to another app is left to the webview, and fails here as a main frame load too
        if (!isOwnContent(url.toString())) {
            return
        }

        // and only the page being shown. A request made for a document already closed can still be
        // answered here, long after the page moved on, and the document on screen is not the one
        // that failed
        if (loadedUrl != null && url.toString() != loadedUrl) {
            return
        }

        documentFragment.onPageFailed()
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
            val tmpFile = FileCache.createCacheFile(context)

            ByteArrayInputStream(base64.toByteArray(charset(StreamUtil.ENCODING))).use { inputStream
                ->
                StreamUtil.copy(Base64InputStream(inputStream, Base64.NO_WRAP), tmpFile)
            }

            post {
                // the user is mid-read, not opening something
                documentFragment.loadUri(
                    FileCache.getCacheFileUri(context, tmpFile),
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

        /** Two seconds of them, which a megabyte of text lays out well inside of. */
        const val SCROLL_RESTORE_ATTEMPTS = 20

        const val SCROLL_RESTORE_INTERVAL_MS = 100L
    }
}
