package app.opendocument.droid.background

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import app.opendocument.droid.ui.activity.DocumentFragment
import java.io.File

/**
 * Owns the two loaders and the background thread they run on: metadata first, then the core.
 * Nothing follows the core - what it cannot open is reported as unsupported.
 */
class LoaderService : Service(), FileLoader.FileLoaderListener {

    private lateinit var mainHandler: Handler

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private lateinit var crashManager: CrashManager
    private lateinit var analyticsManager: AnalyticsManager

    private lateinit var metadataLoader: MetadataLoader
    private lateinit var coreLoader: CoreLoader

    private var currentListener: LoaderListener? = null

    @Synchronized
    override fun onCreate() {
        super.onCreate()

        mainHandler = Handler(Looper.getMainLooper())

        backgroundThread = HandlerThread(DocumentFragment::class.java.simpleName)
        backgroundThread.start()

        backgroundHandler = Handler(backgroundThread.looper)

        crashManager = CrashManager()
        crashManager.initialize()

        analyticsManager = AnalyticsManager()
        analyticsManager.initialize(this)

        metadataLoader = MetadataLoader(this)
        metadataLoader.initialize(
            this,
            mainHandler,
            backgroundHandler,
            analyticsManager,
            crashManager,
        )

        coreLoader = CoreLoader(this)
        coreLoader.initialize(this, mainHandler, backgroundHandler, analyticsManager, crashManager)
    }

    override fun onBind(intent: Intent?): IBinder = LoaderBinder()

    @Synchronized
    fun setListener(listener: LoaderListener?) {
        this.currentListener = listener
    }

    private fun logMissingListener() {
        crashManager.log(RuntimeException("missing listener"))
    }

    @Synchronized
    fun loadWithType(loaderType: FileLoader.LoaderType, options: FileLoader.Options) {
        val loader =
            when (loaderType) {
                FileLoader.LoaderType.CORE -> coreLoader
                FileLoader.LoaderType.METADATA -> metadataLoader
            }

        loader.loadAsync(options)
    }

    override fun onSuccess(result: FileLoader.Result) {
        val options = result.options
        if (result.loaderType == FileLoader.LoaderType.METADATA) {
            if (!coreLoader.isSupported(options)) {
                crashManager.log("we do not expect this file to be an ODF: " + options.originalUri)
                analyticsManager.report(
                    "load_odf_error_expected",
                    AnalyticsConstants.PARAM_CONTENT_TYPE,
                    options.fileType,
                )
            }

            loadWithType(FileLoader.LoaderType.CORE, options)

            return
        }

        analyticsManager.report(
            "load_success",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            options.fileType,
            AnalyticsConstants.PARAM_CONTENT,
            result.loaderType.toString(),
        )

        withListener { it.onLoadSuccess(result) }
    }

    override fun onError(result: FileLoader.Result, error: Throwable) {
        val options = result.options
        crashManager.log(error, options.originalUri)

        if (error is FileLoader.EncryptedDocumentException) {
            analyticsManager.report("load_error_encrypted")

            withListener { it.onEncrypted(result) }

            return
        }

        if (result.loaderType == FileLoader.LoaderType.CORE) {
            analyticsManager.report(
                "load_odf_error",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                options.fileType,
            )

            if (coreLoader.isSupported(options)) {
                // the core names the format and still said no, so the file is what is wrong,
                // not the format
                withListener { it.onError(result, error) }
            } else {
                withListener { it.onUnsupported(result) }
            }

            return
        }

        // MetadataLoader failed, so there's no point in trying to parse the file

        analyticsManager.report(
            "load_error",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            options.fileType,
            AnalyticsConstants.PARAM_CONTENT,
            result.loaderType.toString(),
        )

        withListener { it.onError(result, error) }
    }

    fun saveAsync(lastResult: FileLoader.Result, outFile: Uri, htmlDiff: String?) {
        backgroundHandler.post { saveSync(lastResult, outFile, htmlDiff) }
    }

    private fun saveSync(lastResult: FileLoader.Result, outFile: Uri, htmlDiff: String?) {
        val options = lastResult.options

        // only the retranslated file is ours to remove afterwards - the other branch hands back
        // the cache file of the document that is still open
        var retranslated: File? = null
        var backup: File? = null
        var backupIsTheLastCopy = false

        try {
            val fileToSave =
                if (htmlDiff != null) {
                    val edited =
                        coreLoader.retranslate(options, htmlDiff)
                            ?: throw RuntimeException("retranslate failed")
                    retranslated = edited

                    edited
                } else {
                    // "full save" from the main UI
                    checkNotNull(
                        AndroidFileCache.getCacheFile(this, checkNotNull(options.cacheUri))
                    ) {
                        "not a cached file: " + options.cacheUri
                    }
                }

            // the write goes straight into the user's own document, so keep what is there
            // until the new content has landed whole
            backup = backUp(outFile)

            try {
                if (!writeTo(outFile, fileToSave)) {
                    crashManager.log("saved without truncating: $outFile")
                }
            } catch (e: Throwable) {
                backupIsTheLastCopy = !restore(outFile, backup)

                throw e
            }

            mainHandler.post { withListener { it.onSaveSuccess(outFile) } }
        } catch (e: Throwable) {
            analyticsManager.report(
                "save_error",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                options.fileType,
            )
            crashManager.log(e, options.originalUri)

            mainHandler.post { withListener { it.onSaveError() } }
        } finally {
            retranslated?.delete()

            // if the rollback did not get the old content back in, this copy is all that is
            // left of it - leave it in the cache rather than finishing the job
            if (!backupIsTheLastCopy) {
                backup?.let { AndroidFileCache.deleteCacheFile(it) }
            }
        }
    }

    /** Copies [source] over [uri]. False if the provider would not truncate first. */
    private fun writeTo(uri: Uri, source: File): Boolean {
        // "wt", not the default "w": not every provider truncates for "w", and the tail of a
        // longer previous document left behind is what stops an odt or docx opening
        var truncated = true

        val outputStream =
            try {
                contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                // a provider that rejects the mode outright - saving at all beats truncating
                crashManager.log(e, uri)

                truncated = false

                contentResolver.openOutputStream(uri)
            }

        checkNotNull(outputStream) { "cannot write $uri" }.use { StreamUtil.copy(source, it) }

        return truncated
    }

    /** What [uri] holds right now, so a half finished write can be rolled back. */
    private fun backUp(uri: Uri): File? {
        val backup = AndroidFileCache.createCacheFile(this)

        return try {
            val input = checkNotNull(contentResolver.openInputStream(uri)) { "cannot read $uri" }
            StreamUtil.copy(input, backup)

            if (backup.length() > 0) {
                backup
            } else {
                // a document that was just created has nothing worth keeping
                AndroidFileCache.deleteCacheFile(backup)

                null
            }
        } catch (e: Throwable) {
            // unreadable target: no worse than before, the save just cannot be rolled back
            crashManager.log(e, uri)
            AndroidFileCache.deleteCacheFile(backup)

            null
        }
    }

    /**
     * Puts [backup] back into [uri]. False if the old content could not be restored, including a
     * write that did not truncate and so left the tail of the failed save behind it.
     */
    private fun restore(uri: Uri, backup: File?): Boolean {
        if (backup == null) {
            return false
        }

        return try {
            writeTo(uri, backup)
        } catch (e: Throwable) {
            crashManager.log(e, uri)

            false
        }
    }

    /** Every callback is dropped once the fragment is gone, which is worth a crash report. */
    private inline fun withListener(block: (LoaderListener) -> Unit) {
        val listener = currentListener
        if (listener != null) {
            block(listener)
        } else {
            logMissingListener()
        }
    }

    override fun onDestroy() {
        metadataLoader.close()
        coreLoader.close()

        // quitSafely, not quit: close() posts each loader's teardown, which quit() would drop
        backgroundThread.quitSafely()

        super.onDestroy()
    }

    inner class LoaderBinder : Binder() {
        fun getService(): LoaderService = this@LoaderService
    }

    interface LoaderListener {
        fun onLoadSuccess(result: FileLoader.Result)

        fun onSaveSuccess(outFile: Uri)

        fun onError(result: FileLoader.Result, error: Throwable)

        fun onEncrypted(result: FileLoader.Result)

        fun onUnsupported(result: FileLoader.Result)

        fun onSaveError()
    }
}
