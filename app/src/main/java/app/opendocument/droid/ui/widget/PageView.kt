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
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.opendocument.droid.background.CoreLoader
import app.opendocument.droid.background.EditingKind
import app.opendocument.droid.background.FileCache
import app.opendocument.droid.background.StreamUtil
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.ui.ParagraphListener
import app.opendocument.droid.ui.activity.DocumentFragment
import java.io.ByteArrayInputStream
import java.io.IOException
import org.json.JSONObject
import org.json.JSONTokener

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

    /** Told what the page's editor reports, on the main thread - see [postMessage]. */
    var editingListener: EditingListener? = null

    /** What [setEditing] was last told, applied again to every page that loads. */
    private var editingKind = EditingKind.NONE
    private var isEditing = false

    /**
     * Progress 100 reported before the page commits leaves it blank
     * (https://stackoverflow.com/q/48082474/198996), so onPageFinished schedules a reload for
     * whatever never committed.
     */
    private val buggyWebViewHandler = Handler(Looper.getMainLooper())

    private var wasCommitCalled = false

    /** What [loadUrl] was last given: the only page whose failure is this document's. */
    private var loadedUrl: String? = null

    /** Whether the page on screen is being replaced - see [expectNewPage]. */
    private var isAwaitingNewPage = false

    private var isBridgeAttached = false
    private var destroyed = false

    init {
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.defaultTextEncodingName = StreamUtil.ENCODING
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        // Allow small document text without WebView enlarging it and overlapping adjacent content.
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

                    if (destroyed || isAwaitingNewPage || (loadedUrl != null && url != loadedUrl)) {
                        return
                    }
                    restorePendingScroll(0)

                    // a sheet loads a page per tab, and each one is a page of its own to wire up
                    if (isOwnContent(url) && isEditing) {
                        applyEditing()
                    }

                    buggyWebViewHandler.postDelayed(
                        {
                            // Retry only this URL; callbacks from a replaced page may arrive late.
                            if (!wasCommitCalled && url == loadedUrl) {
                                crashManager.log(RuntimeException("commit was not called"))

                                loadUrl(url)
                            }
                        },
                        2500,
                    )
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    if (url == loadedUrl) {
                        wasCommitCalled = true
                    }
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

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    crashManager.log("WebView renderer exited; crashed=${detail.didCrash()}")
                    (parent as? ViewGroup)?.removeView(this@PageView)
                    destroy()
                    documentFragment.onPageFailed()
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = !request.isForMainFrame || openExternal(request.url)

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    openExternal(Uri.parse(url))
            }

        setDownloadListener { url, _, _, _, _ -> openExternal(Uri.parse(url)) }
    }

    private fun openExternal(uri: Uri): Boolean {
        if (uri.scheme in setOf("file", "content", "javascript")) {
            return true
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        } catch (e: Exception) {
            crashManager.log(e)
        }
        // Never load external content with the document's JavaScript bridge.
        return true
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
        scrollRestoreHandler.removeCallbacksAndMessages(null)
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
     * Allows document darkening when the app theme is dark. [DocumentFragment] selects the document
     * policy.
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

    /** Ignores failures from the old page while the core replaces its translation. */
    fun expectNewPage() {
        isAwaitingNewPage = true

        stopLoading()

        // the reload waiting in onPageFinished would load the old page back over the new one
        buggyWebViewHandler.removeCallbacksAndMessages(null)
    }

    override fun loadUrl(url: String) {
        // Bridge changes take effect on the next page load.
        if (!url.startsWith(JAVASCRIPT_SCHEME)) {
            wasCommitCalled = false
            attachBridge(isOwnContent(url))

            // Cancel retries for the previous page before loading its replacement.
            buggyWebViewHandler.removeCallbacksAndMessages(null)

            loadedUrl = url
            isAwaitingNewPage = false
        }

        super.loadUrl(url)
    }

    override fun destroy() {
        if (destroyed) return
        // the reload is scheduled 2.5s out, and this also runs when a second document
        // replaces the view - so it must not land on a WebView that is gone
        buggyWebViewHandler.removeCallbacksAndMessages(null)

        scrollRestoreHandler.removeCallbacksAndMessages(null)
        scrollFractionToRestore = null
        paragraphListener = null
        editingListener = null
        destroyed = true
        super.destroy()
    }

    /** Reports a page that will never appear. [description] is all there is of the cause. */
    private fun failPage(url: Uri, description: String) {
        crashManager.log(RuntimeException(description))

        // Ignore failures outside the core's document server.
        if (!isOwnContent(url.toString())) {
            return
        }

        // and only the page being shown. A request made for a document already closed can still be
        // answered here, long after the page moved on, and the document on screen is not the one
        // that failed
        if (loadedUrl != null && url.toString() != loadedUrl) {
            return
        }

        // nor the page that is being replaced right now, whose url is the one replacing it
        if (isAwaitingNewPage) {
            return
        }

        documentFragment.onPageFailed()
    }

    /** Whether [url] belongs to the active core server. */
    private fun isOwnContent(url: String): Boolean = CoreLoader.isHostedUri(Uri.parse(url))

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

    fun setParagraphListener(paragraphListener: ParagraphListener?) {
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

    /** Turns the page's edit mode on or off. A pdf has no mode, so leaving only disarms. */
    fun setEditing(kind: EditingKind, editing: Boolean) {
        editingKind = kind
        isEditing = editing

        applyEditing()
    }

    private fun applyEditing() {
        evaluateJavascript(
            when {
                editingKind == EditingKind.ANNOTATION ->
                    if (isEditing) "void 0"
                    else "window.odr && odr.annotation && odr.annotation.setTool(null)"
                isEditing -> "window.odr && odr.editing && odr.editing.enable()"
                else -> "window.odr && odr.editing && odr.editing.disable()"
            },
            null,
        )
    }

    fun undo() {
        evaluateJavascript(
            if (editingKind == EditingKind.ANNOTATION)
                "window.odr && odr.annotation && odr.annotation.undo()"
            else "window.odr && odr.editing && odr.editing.undo()",
            null,
        )
    }

    fun redo() {
        evaluateJavascript("window.odr && odr.editing && odr.editing.redo()", null)
    }

    /** Flips `bold`, `italic`, `underline` or `strikethrough` on the selection. */
    fun toggleStyle(property: String) {
        evaluateJavascript("odr.editing.toggle(${JSONObject.quote(property)})", null)
    }

    /** States [style] on the selection, in the keys `odr.editing.format` takes. */
    fun formatStyle(style: JSONObject) {
        evaluateJavascript("odr.editing.format($style)", null)
    }

    /**
     * A marking tool pressed, or given a new colour; [callback] gets the tool left armed. The page
     * marks a standing selection, and arms where there is none.
     */
    fun pressMarkTool(
        tool: String,
        color: Int,
        width: Float,
        recolor: Boolean,
        callback: (armed: String?) -> Unit,
    ) {
        val rgb =
            "[${android.graphics.Color.red(color) / 255f}," +
                "${android.graphics.Color.green(color) / 255f}," +
                "${android.graphics.Color.blue(color) / 255f}]"
        val method = if (recolor) "recolor" else "press"

        evaluateJavascript(
            "window.odr && odr.annotation ? odr.annotation.$method(" +
                "${JSONObject.quote(tool)}, {color: $rgb, width: $width}) : null"
        ) {
            callback(decodeString(it))
        }
    }

    /** What a save hands the core: the page's operations, or a pdf's marks. Null if none. */
    fun requestEditPayload(kind: EditingKind, callback: (String?) -> Unit) {
        val expression =
            if (kind == EditingKind.ANNOTATION) {
                "window.odr && odr.annotation ? odr.annotation.getAnnotations() : null"
            } else {
                "window.odr && odr.editing ? odr.editing.getOperations() : null"
            }

        evaluateJavascript("(function(){return $expression;})()") { callback(decodeString(it)) }
    }

    private fun decodeString(result: String?): String? =
        try {
            JSONTokener(result ?: "null").nextValue() as? String
        } catch (e: Exception) {
            crashManager.log(e)

            null
        }

    /**
     * Every `odr.on*` callback of the page, as `{type, detail}` - see
     * `HtmlConfig.hostMessageHandler` in `CoreLoader`. Called on the javabridge thread, so it posts
     * to the main one.
     */
    @JavascriptInterface
    @Keep
    fun postMessage(json: String) {
        val message =
            try {
                JSONObject(json)
            } catch (e: Exception) {
                crashManager.log(e)

                return
            }
        val detail = message.optJSONObject("detail") ?: JSONObject()

        post {
            val listener = editingListener ?: return@post

            when (message.optString("type")) {
                "editChange" ->
                    listener.onEditChanged(
                        detail.optBoolean("dirty"),
                        detail.optBoolean("canUndo"),
                        detail.optBoolean("canRedo"),
                    )
                "editRefused" -> listener.onEditRefused(detail.optString("reason"))
                "selectionChange" -> listener.onSelectionChanged(detail)
                "annotationChange" -> listener.onMarksChanged(detail.optInt("count"))
                "cellsStale" -> listener.onCellsStale(detail.optJSONArray("cells")?.length() ?: 0)
            }
        }
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
                if (destroyed) {
                    FileCache.deleteCacheFile(tmpFile)
                    return@post
                }
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
        post { paragraphListener?.paragraph(text) }
    }

    @Keep
    @JavascriptInterface
    override fun increaseIndex() {
        post { paragraphListener?.increaseIndex() }
    }

    @Keep
    @JavascriptInterface
    override fun end() {
        post { paragraphListener?.end() }
    }

    interface EditingListener {

        fun onEditChanged(dirty: Boolean, canUndo: Boolean, canRedo: Boolean)

        /** [reason] is the page's name for it, such as `outOfScope` or `range`. */
        fun onEditRefused(reason: String)

        /** What the selection shows, a key per property the runs under it agree on. */
        fun onSelectionChanged(style: JSONObject)

        /** How many marks the pdf holds that no save has written. */
        fun onMarksChanged(count: Int)

        /** How many formula cells an edit left showing an old result. */
        fun onCellsStale(count: Int)
    }

    companion object {

        private const val BRIDGE_NAME = "paragraphListener"

        /** [postMessage], as the page finds it from `window`. */
        const val HOST_MESSAGE_HANDLER = "$BRIDGE_NAME.postMessage"

        private const val JAVASCRIPT_SCHEME = "javascript:"

        /** Two seconds of them, which a megabyte of text lays out well inside of. */
        private const val SCROLL_RESTORE_ATTEMPTS = 20

        private const val SCROLL_RESTORE_INTERVAL_MS = 100L
    }
}
