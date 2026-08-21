package app.opendocument.droid.background

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import app.opendocument.core.OdrException
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import java.io.IOException

/**
 * Opens a document: caches it, names it, renders it, and reports back on the main thread. Owns the
 * background thread all of that runs on, and the components that do it.
 *
 * Scoped to `MainActivity`, so it outlives a configuration change and is there before anything asks
 * it for a document.
 *
 * There is nothing after [CoreLoader]. A file it cannot open is reported as unsupported and that is
 * the end of it: a format the app should open is a format odrcore should learn, and no document
 * leaves the device.
 */
class DocumentLoader(application: Application) : AndroidViewModel(application) {

    private val context: Context = application

    private val mainHandler = Handler(Looper.getMainLooper())

    private val backgroundThread =
        HandlerThread(DocumentLoader::class.java.simpleName).apply { start() }

    private val backgroundHandler = Handler(backgroundThread.looper)

    private val crashManager = CrashManager().apply { initialize() }

    private val analyticsManager = AnalyticsManager().apply { initialize(context) }

    private val fileIdentifier = FileIdentifier(crashManager)

    private val coreLoader = CoreLoader(context)

    private val documentSaver = DocumentSaver(context, coreLoader, crashManager)

    /**
     * Where the outcomes go. `DocumentFragment` attaches itself before it asks for anything, so an
     * outcome arriving with nothing to hand it to means the fragment went away mid-load - see
     * [deliver].
     */
    var listener: Listener? = null

    init {
        // posted, not called: loading the native library is not work for the thread putting the
        // first frame up, and everything that reaches the core is posted to this one anyway
        backgroundHandler.post { coreLoader.initialize(crashManager) }
    }

    fun load(request: DocumentRequest) {
        backgroundHandler.post { loadSync(request) }
    }

    /**
     * Renders a file that has already been read again, after [DocumentRequest.editable] or
     * [DocumentRequest.password] changed.
     *
     * Not a [load]: the copy in the cache is the document, so caching and naming it a second time
     * would only cost another pass over the bytes and move it up the recent list again.
     */
    fun reload(request: DocumentRequest, file: IdentifiedFile) {
        backgroundHandler.post { renderSync(request, file) }
    }

    fun save(document: LoadedDocument, target: Uri, htmlDiff: String?) {
        backgroundHandler.post { saveSync(document, target, htmlDiff) }
    }

    private fun loadSync(request: DocumentRequest) {
        val uri = readableUri(request.uri)

        val file =
            try {
                fileIdentifier.identify(context, uri, FileCache.store(context, uri))
            } catch (e: Throwable) {
                failed("load_error", null, request, e)

                // a document that cannot be read is not one the recent list should offer again
                try {
                    RecentDocumentsUtil.removeRecentDocument(context, request.uri)
                } catch (e1: Exception) {
                    crashManager.log(e1)
                }

                deliver { it.onError(request, null, e) }

                return
            }

        remember(request, uri, file)

        renderSync(request, file)
    }

    private fun renderSync(request: DocumentRequest, file: IdentifiedFile) {
        try {
            val document = coreLoader.render(request, file)

            report("load_success", file)

            deliver { it.onLoadSuccess(document) }
        } catch (e: CoreLoader.UndecryptableFile) {
            encrypted(request, file, e, canDecrypt = false)
        } catch (e: OdrException.FileEncrypted) {
            encrypted(request, file, e)
        } catch (e: OdrException.WrongPassword) {
            encrypted(request, file, e)
        } catch (e: OdrException.DecryptionFailed) {
            encrypted(request, file, e)
        } catch (e: Throwable) {
            failed("load_odf_error", file, request, e)

            if (SupportedDocumentTypes.isRenderedByCore(file.mimeType)) {
                // the core names the format and still said no, so the file is what is wrong,
                // not the format
                deliver { it.onError(request, file, e) }
            } else {
                deliver { it.onUnsupported(request, file) }
            }
        }
    }

    private fun encrypted(
        request: DocumentRequest,
        file: IdentifiedFile,
        error: Throwable,
        canDecrypt: Boolean = true,
    ) {
        failed("load_error_encrypted", file, request, error)

        deliver { it.onEncrypted(request, file, canDecrypt) }
    }

    /**
     * Records the document in the recent list, under the uri that was actually opened rather than
     * the request's own spelling of it - see [readableUri].
     */
    private fun remember(request: DocumentRequest, uri: Uri, file: IdentifiedFile) {
        if (!request.persistentUri) {
            return
        }

        try {
            val evicted = RecentDocumentsUtil.addRecentDocument(context, file.filename, uri)
            if (evicted.isNotEmpty()) {
                // stay well below the per package grant limit
                PersistedUriPermissions.prune(context)
            }
        } catch (e: IOException) {
            crashManager.log(e)
        }
    }

    private fun saveSync(document: LoadedDocument, target: Uri, htmlDiff: String?) {
        try {
            documentSaver.save(document, target, htmlDiff)

            deliver { it.onSaveSuccess(target) }
        } catch (e: Throwable) {
            failed("save_error", document.file, document.request, e)

            deliver { it.onSaveError() }
        }
    }

    private fun report(event: String, file: IdentifiedFile?) {
        analyticsManager.report(event, AnalyticsConstants.PARAM_CONTENT_TYPE, file?.mimeType)
    }

    private fun failed(
        event: String,
        file: IdentifiedFile?,
        request: DocumentRequest,
        error: Throwable,
    ) {
        report(event, file)
        crashManager.log(error, request.uri)
    }

    /**
     * Hands [outcome] to the listener on the main thread.
     *
     * Dropped when there is none: the load it belongs to was abandoned, and replaying it later
     * would land it in whatever fragment is showing by then. Worth a crash report all the same,
     * since the document the user asked for is gone with it.
     */
    private fun deliver(outcome: (Listener) -> Unit) {
        mainHandler.post {
            val listener = this.listener
            if (listener == null) {
                crashManager.log(RuntimeException("missing listener"))

                return@post
            }

            outcome(listener)
        }
    }

    override fun onCleared() {
        listener = null

        // posted, not called: the teardown must not race a load still on the thread
        backgroundHandler.post { coreLoader.close() }

        // quitSafely, not quit: quit() would drop the teardown just posted
        backgroundThread.quitSafely()

        super.onCleared()
    }

    /**
     * Uris reach us spelled `/./content://...`, which resolves to nothing. Only what is opened is
     * corrected; the request keeps the spelling it was handed, which is what the reopen offer and
     * the abandoned-load check compare against.
     */
    private fun readableUri(uri: Uri): Uri {
        val value = uri.toString()

        return if (value.startsWith("/./")) Uri.parse(value.substring(2)) else uri
    }

    interface Listener {

        fun onLoadSuccess(document: LoadedDocument)

        /**
         * The document needs a password, or the one it was given was wrong.
         *
         * [canDecrypt] is false where odrcore cannot decrypt the format at all - asking for a
         * password would only ask again, so there is nothing to prompt for.
         */
        fun onEncrypted(request: DocumentRequest, file: IdentifiedFile, canDecrypt: Boolean)

        /** Not a format the core renders, so nothing is ever going to show it. */
        fun onUnsupported(request: DocumentRequest, file: IdentifiedFile)

        /** [file] is null when the document could not be read at all. */
        fun onError(request: DocumentRequest, file: IdentifiedFile?, error: Throwable)

        fun onSaveSuccess(target: Uri)

        fun onSaveError()
    }
}
