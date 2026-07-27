package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.system.Os
import android.util.Log
import app.opendocument.core.DecodePreference
import app.opendocument.core.DecodedFile
import app.opendocument.core.DecoderEngine
import app.opendocument.core.Document
import app.opendocument.core.DocumentType
import app.opendocument.core.FileType
import app.opendocument.core.GlobalParams
import app.opendocument.core.Html
import app.opendocument.core.HtmlConfig
import app.opendocument.core.HtmlView
import app.opendocument.core.HttpServer
import app.opendocument.core.Odr
import app.opendocument.core.OdrException
import app.opendocument.droid.background.FileLoader.EncryptedDocumentException
import app.opendocument.droid.background.FileLoader.FileLoaderListener
import app.opendocument.droid.background.FileLoader.LoaderType
import app.opendocument.droid.background.FileLoader.Options
import app.opendocument.droid.background.FileLoader.Result
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import com.viliussutkus89.android.assetextractor.AssetExtractor
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Loads documents through odrcore and publishes them on a local http server.
 *
 * This talks to odrcore's own JNI bindings (`app.opendocument.core`, the `odr-core-java.jar` and
 * the matching `libodr_jni.so`, both out of the odrcore conan package). It used to go through
 * `CoreWrapper` and a hand-written JNI layer in `src/main/cpp/core_wrapper.cpp` that flattened
 * every failure into an integer error code; the core reports typed [OdrException]s instead, so the
 * codes and their mirror exception types are gone.
 *
 * The loader owns the process-wide core state: the one-time initialization, the single http server
 * (which [RawLoader] also publishes text files on) and the currently open [Document] that
 * [retranslate] edits.
 */
// the context is nullable like FileLoader's own field, which close() clears and the unit
// tests never set - isSupported() is pure and does not need one
class CoreLoader(context: Context?, private val doOoxml: Boolean) :
    FileLoader(context, LoaderType.CORE) {

    private var server: HttpServer? = null
    private var httpThread: Thread? = null

    private var document: Document? = null
    private var lastInputPath: String? = null
    private var lastCachePath: String? = null

    // the port the server actually got, which is not always SERVER_PORT. see choosePort()
    private var serverPort = SERVER_PORT

    override fun initialize(
        listener: FileLoaderListener,
        mainHandler: Handler,
        backgroundHandler: Handler,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {
        // loads the native library and extracts the core assets. Kept out of the constructor so
        // that constructing a CoreLoader stays side effect free - LoaderService calls initialize()
        // right after new CoreLoader() anyway.
        initializeCore(context)

        val serverCacheDir = File(context.cacheDir, "core/server")
        if (!serverCacheDir.isDirectory && !serverCacheDir.mkdirs()) {
            Log.e(TAG, "Failed to create cache directory for the core server: $serverCacheDir")
        }

        val config = HttpServer.Config()
        config.cachePath = serverCacheDir.absolutePath
        val server = HttpServer(config)
        this.server = server

        serverPort = choosePort(crashManager)

        httpThread =
            Thread {
                try {
                    server.listen(SERVER_BIND_ADDRESS, serverPort)
                } catch (e: Throwable) {
                    crashManager.log(e)
                }
            }
                .also { it.start() }

        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager)
    }

    /**
     * Picks the port the server binds to, and says so when [SERVER_PORT] cannot be had.
     *
     * [HttpServer.listen] returns void and does not report a failed bind, so an occupied port left
     * the app with no server at all and nothing to show for it: every document then failed with
     * ERR_CONNECTION_REFUSED behind chrome's own error page. That is what testODTEditMode kept
     * failing on, and it took a webview error log to see at all.
     *
     * The port is occupied more often than it looks. Both flavors hardcode it, so lite and pro
     * collide whenever they run on one device - the instrumented tests do exactly that, one flavor
     * after the other - and a port does not come free the moment its owner goes away: the sockets
     * the webview opened linger in TIME_WAIT for a minute, and the native bind does not set
     * SO_REUSEADDR. Clean shutdown does not help either, since the previous app is usually killed
     * outright rather than destroyed.
     */
    private fun choosePort(crashManager: CrashManager): Int {
        try {
            return bindable(SERVER_PORT)
        } catch (e: IOException) {
            crashManager.log(
                IOException("core server cannot bind $SERVER_BIND_ADDRESS:$SERVER_PORT", e)
            )
        }

        return try {
            bindable(0)
        } catch (e: IOException) {
            // nothing is bindable; let listen() fail on the usual port rather than invent one
            crashManager.log(IOException("core server cannot bind any port", e))

            SERVER_PORT
        }
    }

    /** The port [port] resolves to, if a socket that binds like the native one can have it. */
    private fun bindable(port: Int): Int =
        ServerSocket().use { probe ->
            // deliberately not reuseAddress: the probe has to fail wherever the native bind
            // would, and java turns SO_REUSEADDR on by default - which is exactly the flag
            // whose absence makes a TIME_WAIT port unusable
            probe.reuseAddress = false
            probe.bind(InetSocketAddress(SERVER_BIND_ADDRESS, port))

            probe.localPort
        }

    override fun isSupported(options: Options): Boolean {
        val fileType = options.fileType ?: return false
        return fileType.startsWith("application/vnd.oasis.opendocument") ||
            fileType.startsWith("application/x-vnd.oasis.opendocument") ||
            fileType.startsWith("application/vnd.oasis.opendocument.text-master") ||
            fileType.startsWith("application/msword") ||
            (doOoxml &&
                (fileType.startsWith(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ) ||
                    fileType.startsWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                // TODO: enable pptx too
                // fileType.startsWith("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                ))
    }

    override fun loadSync(options: Options) {
        val result = Result(type, options)

        try {
            translate(options, result)

            callOnSuccess(result)
        } catch (e: OdrException.FileEncrypted) {
            callOnError(result, EncryptedDocumentException())
        } catch (e: OdrException.WrongPassword) {
            callOnError(result, EncryptedDocumentException())
        } catch (e: OdrException.DecryptionFailed) {
            callOnError(result, EncryptedDocumentException())
        } catch (e: Throwable) {
            callOnError(result, e)
        }
    }

    private fun translate(options: Options, result: Result) {
        val cacheUri = checkNotNull(options.cacheUri) { "nothing was cached to load" }
        val cachedFile =
            checkNotNull(AndroidFileCache.getCacheFile(context, cacheUri)) {
                "not a cached file: $cacheUri"
            }
        val cacheDirectory = AndroidFileCache.getCacheDirectory(cachedFile)

        val coreCacheDirectory = File(cacheDirectory, "core_cache")

        lastInputPath = cachedFile.path
        lastCachePath = coreCacheDirectory.path

        val views =
            host(
                prefix = "odr",
                inputPath = cachedFile.path,
                cachePath = coreCacheDirectory.path,
                password = options.password,
                editable = options.translatable,
                paging = USE_PAGING,
                keepDocument = true,
            )

        for (view in views) {
            result.partTitles.add(view.name)
            result.partUris.add(Uri.parse(view.url))
        }
    }

    /**
     * Opens [inputPath], translates it to html and publishes it on the shared http server under
     * [prefix], replacing whatever was published before.
     *
     * [keepDocument] retains the decoded document for [retranslate]; [RawLoader] has nothing to
     * edit and passes false.
     */
    fun host(
        prefix: String,
        inputPath: String,
        cachePath: String,
        password: String? = null,
        editable: Boolean = false,
        paging: Boolean = false,
        keepDocument: Boolean = false,
    ): List<HostedView> {
        val server = checkNotNull(server) { "core server is not running" }

        Log.i(TAG, "host file")

        server.clear()

        var file = open(inputPath)

        if (file.passwordEncrypted()) {
            if (password == null) {
                throw OdrException.FileEncrypted(inputPath)
            }
            file = file.decrypt(password)
        }

        Log.i(TAG, "type=" + Odr.fileTypeToString(file.fileType()))

        if (keepDocument) {
            closeDocument()
            // .doc-files are not real documents in core
            if (file.isDocumentFile && file.fileType() != FileType.LEGACY_WORD_DOCUMENT) {
                // TODO this will cause a second load
                document = file.asDocumentFile().document()
            }
        }

        val htmlConfig = HtmlConfig()
        htmlConfig.embedImages = false
        htmlConfig.embedShippedResources = true
        htmlConfig.relativeResourcePaths = false
        htmlConfig.textDocumentMargin = paging
        htmlConfig.editable = editable

        val cacheDirectory = File(cachePath)
        cacheDirectory.deleteRecursively()
        cacheDirectory.mkdirs()

        val service = Html.translate(file, cachePath, htmlConfig)
        server.connectService(service, prefix)

        return selectViews(file, service.listViews()).map { view ->
            Log.i(TAG, "view name=" + view.name() + " path=" + view.path())
            HostedView(
                view.name(),
                "http://$SERVER_URL_HOST:$serverPort/file/$prefix/" + view.path(),
            )
        }
    }

    override fun retranslate(options: Options, htmlDiff: String): File? {
        try {
            if (document == null) {
                // necessary if fragment was destroyed in the meanwhile - meaning the Loader is
                // reinstantiated
                translate(options, Result(type, options))
            }

            val inputFile = File(checkNotNull(lastInputPath))
            val inputCacheDirectory = AndroidFileCache.getCacheDirectory(inputFile)

            return edit(htmlDiff, File(inputCacheDirectory, "retranslate").path)
        } catch (e: Throwable) {
            crashManager.log(e)

            return null
        }
    }

    /**
     * Applies [htmlDiff] to the document currently held open by [host] and saves it next to
     * [outputPathPrefix], with the extension that matches the document's own file type.
     */
    fun edit(htmlDiff: String, outputPathPrefix: String): File {
        val document = checkNotNull(document) { "no editable document is open" }

        val extension = Odr.fileTypeToString(document.fileType())
        val outputFile = File("$outputPathPrefix.$extension")

        Log.d(TAG, "HTML diff: $htmlDiff")

        Html.edit(document, htmlDiff)
        document.save(outputFile.path)

        return outputFile
    }

    override fun close() {
        super.close()

        val server = this.server
        if (server != null) {
            server.stop()
            httpThread?.let {
                try {
                    it.join(1000)
                } catch (e: InterruptedException) {
                    crashManager.log(e)
                }
            }
            httpThread = null

            server.close()
            this.server = null
        }

        closeDocument()
    }

    private fun closeDocument() {
        document?.close()
        document = null
    }

    /** A translated view of a document, ready to be opened in the WebView. */
    data class HostedView(val name: String, val url: String)

    companion object {
        private const val TAG = "CoreLoader"

        /** The loopback address the server binds to. */
        private const val SERVER_BIND_ADDRESS = "127.0.0.1"

        /**
         * The host the served pages are addressed by, deliberately not [SERVER_BIND_ADDRESS]:
         * res/xml/network_security_config.xml permits cleartext http for the domain "localhost"
         * only, so a page loaded from "http://127.0.0.1:..." never reaches the WebView.
         */
        private const val SERVER_URL_HOST = "localhost"

        /**
         * The port the server prefers. Only a preference: it is hardcoded and therefore shared with
         * the other flavor, so it is not always free. See [choosePort].
         */
        const val SERVER_PORT: Int = 29665

        /**
         * Whether odrcore renders text documents with page margins. This used to be read from the
         * "use_paging" remote config key, but that resolved to false for every user since firebase
         * remote config was removed - the ConfigManager left behind is a stub without a backing
         * store. Kept as an explicit constant so the shipped behavior is visible instead of hidden
         * behind a lookup that cannot return a value.
         */
        private const val USE_PAGING = false

        private var coreInitialized = false

        /**
         * One-time process wide setup of the core: data paths and the temporary directory.
         *
         * [MetadataLoader] calls [Odr.mimetype], which needs the libmagic database set up here, so
         * this has to have run before any document is loaded. LoaderService constructs and
         * initializes the CoreLoader while starting up, well before the first load request.
         */
        @Synchronized
        fun initializeCore(context: Context) {
            if (coreInitialized) {
                return
            }

            // core resolves its temporary directory through std::filesystem::temp_directory_path(),
            // which falls back to /tmp - a path that does not exist on android. this replaces the
            // tmpfile() interposition that used to live in src/main/cpp/tmpfile_hack.cpp: that only
            // worked while the app linked the core into a library it compiled itself, and
            // libodr_jni.so is a prebuilt now. has to happen before the first native call, because
            // core caches the directory on first use.
            Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)

            Log.i(TAG, "odrcore " + Odr.identify())

            val assetsDirectory = File(context.filesDir, "assets")
            val odrCoreDataDirectory = File(assetsDirectory, "odrcore")
            val libmagicDataDirectory = File(assetsDirectory, "libmagic")

            val assetExtractor = AssetExtractor(context.assets)
            assetExtractor.setOverwrite()
            assetExtractor.extract(assetsDirectory, "core/odrcore")
            assetExtractor.extract(assetsDirectory, "core/libmagic")

            GlobalParams.setOdrCoreDataPath(odrCoreDataDirectory.absolutePath)
            GlobalParams.setLibmagicDatabasePath(
                File(libmagicDataDirectory, "magic.mgc").absolutePath
            )

            coreInitialized = true
        }

        /**
         * Opens [path] restricted to core's own decoder engine. pdf2htmlEX and wvWare are compiled
         * out of the android build (`with_pdf2htmlEX=False`, `with_wvWare=False`), so offering them
         * would only produce "unsupported decoder engine" failures.
         */
        private fun open(path: String): DecodedFile {
            val preference = DecodePreference()
            preference.enginePriority.add(DecoderEngine.ODR)
            return Odr.open(path, preference)
        }

        /**
         * Spreadsheets show one tab per sheet; every other format only shows the full "document"
         * view without tabs (if the service provides one - e.g. plain text and image files only
         * have a single differently named view).
         */
        private fun selectViews(file: DecodedFile, views: List<HtmlView>): List<HtmlView> {
            val isSpreadsheet =
                file.isDocumentFile &&
                    file.asDocumentFile().documentType() == DocumentType.SPREADSHEET
            if (isSpreadsheet) {
                return views.filter { it.name() != "document" }
            }

            val hasDocumentView = views.any { it.name() == "document" }
            if (hasDocumentView) {
                return views.filter { it.name() == "document" }
            }

            return views
        }
    }
}
