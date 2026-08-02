package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.system.Os
import android.util.Log
import app.opendocument.core.DecodePreference
import app.opendocument.core.DecodedFile
import app.opendocument.core.Document
import app.opendocument.core.DocumentType
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
import java.io.File
import java.io.IOException

/**
 * Loads documents through odrcore and publishes them on a local http server.
 *
 * This talks to odrcore's own JNI bindings (`app.opendocument.core`, the java classes and the
 * matching `libodr_jni.so`, both out of the odr-core-android AAR). It used to go through
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
class CoreLoader(context: Context?) : FileLoader(context, LoaderType.CORE) {

    private var document: Document? = null
    private var lastInputPath: String? = null
    private var lastCachePath: String? = null

    /**
     * Whether the document [host] last opened is one [edit] can do something with.
     *
     * This is the core's own answer, not a list of formats kept here: [host] only holds a document
     * open when it reports itself editable and savable, so having one is the answer. The core says
     * no to the legacy binary formats, to ooxml spreadsheets and presentations and to opendocument
     * spreadsheets - the last of those being the same gap as issue #442, which the app used to
     * check for by mime type on its own.
     */
    val isDocumentEditable: Boolean
        get() = document != null

    // the shared server and the port it actually got, which is not always the preferred one.
    // see bind() and startSharedServer()
    private val server: HttpServer?
        get() = sharedServer

    private val serverPort: Int
        get() = sharedServerPort

    override fun initialize(
        listener: FileLoaderListener,
        mainHandler: Handler,
        backgroundHandler: Handler,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {
        // loads the native library and points the core at a usable temporary directory. Kept out
        // of the constructor so that constructing a CoreLoader stays side effect free -
        // LoaderService calls initialize() right after new CoreLoader() anyway.
        initializeCore(context)

        startSharedServer(crashManager)

        super.initialize(listener, mainHandler, backgroundHandler, analyticsManager, crashManager)
    }

    /**
     * What the core renders itself, out of [SupportedDocumentTypes] - which asks odrcore's own
     * format table rather than keeping a list, so this and the manifest cannot disagree about it.
     *
     * Text, csv and images are left out although the core takes those too - [RawLoader] is what
     * gives them their player or their viewer, and this answer is what lets it have its turn. The
     * rest of it decides whether a failed load is worth reporting and which viewer [OnlineLoader]
     * falls back to.
     */
    override fun isSupported(options: Options): Boolean =
        SupportedDocumentTypes.isRenderedByCore(options.fileType)

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

        result.isEditable = isDocumentEditable

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

            // what the format could ever allow, which odrcore 6.1 answers without decoding
            // anything. A document is only opened here when it might be kept, and opening one
            // costs a second parse of the whole file (the TODO below) - so the formats that
            // declare no editing, which is pdf and every spreadsheet and presentation the app
            // renders, no longer pay for an answer that was always going to be no.
            val capabilities = file.capabilities()

            if (file.isDocumentFile && capabilities.edit && capabilities.save) {
                // TODO this will cause a second load
                val document = file.asDocumentFile().document()

                // keep only what [edit] can do something with, and let the core be the one to say
                // so - see [isDocumentEditable]. The declaration above is an upper bound; the
                // document itself is the precise answer, and a read only one held open buys a
                // second parse and nothing else.
                if (document.isEditable && document.isSavable) {
                    this.document = document
                } else {
                    document.close()
                }
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

        // the file type's own extension, not [Odr.fileTypeToString], which is its *name*: the two
        // read alike for most formats but are not the same table, and the name is not always
        // something a file can be called (odrcore 6.1 spells the encrypted ooxml one
        // "ooxml_encrypted")
        val extension = Odr.fileExtensionByFileType(document.fileType())
        val outputFile = File("$outputPathPrefix.$extension")

        Log.d(TAG, "HTML diff: $htmlDiff")

        Html.edit(document, htmlDiff)
        document.save(outputFile.path)

        return outputFile
    }

    /**
     * Drops what this loader published and leaves the server itself running - see
     * [startSharedServer] for why it is never stopped.
     */
    override fun close() {
        super.close()

        server?.clear()

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

        /**
         * The one http server of the process, started on the first [initialize] and never stopped.
         *
         * Never stopped, though no longer because stopping it was unsafe. Both of the ways odrcore
         * let a server go used to drop the native server while the thread that called
         * [HttpServer.listen] was still inside it - `close()` as a use after free that surfaced as
         * a SIGSEGV in `httplib::Server::listen_internal`, and `stop()` by closing the listening
         * socket twice, the second time after its fd number had been handed to whatever opened a
         * file next (which android's fdsan catches by aborting the process, "attempted to close
         * file descriptor N, actually owned by ZipArchive"). That was
         * opendocument-app/OpenDocument.core#631, and odrcore 6.1 fixed it: `stop()` now closes the
         * socket and waits for the accept loop to leave before releasing anything, so nothing is
         * serving by the time it returns.
         *
         * What is left is that there is nobody to stop it *for*. The server is process wide and
         * shared - [RawLoader] publishes text files on it too - so no single loader's teardown is
         * the end of it, and [close] drops what it published with [HttpServer.clear] and leaves the
         * socket listening. The process exit reclaims it, which is what was really doing the work
         * all along; nothing here ever outlived its process.
         *
         * A second consequence is that the port is bound once rather than once per service
         * lifetime, so [bind]'s fallback stops being reached by our own teardown.
         */
        private var sharedServer: HttpServer? = null

        private var sharedServerPort = PREFERRED_SERVER_PORT

        /**
         * Binds [server], preferring [PREFERRED_SERVER_PORT], and returns the port it got.
         *
         * The preferred port is occupied more often than it looks. Both flavors hardcode it, so
         * lite and pro collide whenever they run on one device - the instrumented tests do exactly
         * that, one flavor after the other - and a port does not come free the moment its owner
         * goes away: the sockets the webview opened linger in TIME_WAIT for a minute. That second
         * half is what [HttpServer.Options.reuseAddress] covers, on by default since the core
         * started setting SO_REUSEADDR rather than cpp-httplib's SO_REUSEPORT; a live server of the
         * other flavor still holds the port for real, hence the fallback to any free one.
         *
         * A failed bind used to be invisible: the old `listen(host, port)` returned void either
         * way, so an occupied port left the app with no server and nothing to show for it - every
         * document then failed with ERR_CONNECTION_REFUSED behind chrome's own error page.
         * [HttpServer.bind] throws instead, and reports back the port that a request has to be
         * addressed to.
         */
        private fun bind(server: HttpServer, crashManager: CrashManager): Int {
            // a preference of ANY_PORT is a request for an arbitrary free port, which is what the
            // fallback below asks for anyway - there is nothing to try first
            if (PREFERRED_SERVER_PORT != ANY_PORT) {
                try {
                    return server.bind(SERVER_BIND_ADDRESS, PREFERRED_SERVER_PORT)
                } catch (e: Throwable) {
                    crashManager.log(
                        IOException(
                            "core server cannot bind $SERVER_BIND_ADDRESS:$PREFERRED_SERVER_PORT",
                            e,
                        )
                    )
                }
            }

            try {
                return server.bind(SERVER_BIND_ADDRESS, ANY_PORT)
            } catch (e: Throwable) {
                crashManager.log(IOException("core server cannot bind any port", e))
            }

            // nothing is bound at this point, so [listen] will fail as well and this port only
            // decides which url the documents that follow are refused at. It must not be
            // ANY_PORT, which would address every one of them to :0.
            return if (PREFERRED_SERVER_PORT != ANY_PORT) PREFERRED_SERVER_PORT
            else FALLBACK_SERVER_PORT
        }

        /** Starts [sharedServer] if this is the first loader to ask. */
        @Synchronized
        private fun startSharedServer(crashManager: CrashManager) {
            if (sharedServer != null) {
                return
            }

            val server = HttpServer()

            sharedServer = server
            sharedServerPort = bind(server, crashManager)

            Thread {
                try {
                    server.listen()
                } catch (e: Throwable) {
                    crashManager.log(e)
                }
            }
                .also {
                    // daemon so it cannot keep the process alive: it is never asked to stop
                    it.isDaemon = true
                    it.name = "odr-http-server"
                    it.start()
                }
        }

        /** The loopback address the server binds to. */
        private const val SERVER_BIND_ADDRESS = "127.0.0.1"

        /**
         * The host the served pages are addressed by, deliberately not [SERVER_BIND_ADDRESS]:
         * res/xml/network_security_config.xml permits cleartext http for the domain "localhost"
         * only, so a page loaded from "http://127.0.0.1:..." never reaches the WebView.
         */
        private const val SERVER_URL_HOST = "localhost"

        /** What a bind asks for when any free port will do. */
        private const val ANY_PORT = 0

        /**
         * The port the server prefers. Only a preference: it is hardcoded and therefore shared with
         * the other flavor, so it is not always free. Set it to [ANY_PORT] to always take whatever
         * is going. See [bind].
         */
        const val PREFERRED_SERVER_PORT: Int = 29665

        /**
         * The port the urls name when nothing could be bound at all and the preference is
         * [ANY_PORT], which cannot be passed on - the resulting url would address :0. Only reached
         * when the device has nothing free, where any choice is as good as the next.
         */
        private const val FALLBACK_SERVER_PORT: Int = 29665

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
         * One-time process wide setup of the core: the temporary directory, and nothing else.
         *
         * There used to be data to unpack here - the renderer's css and js, and libmagic's
         * database - out of `assets/core` and into [Context.getFilesDir], with
         * `GlobalParams.setOdrCoreDataPath` and `setLibmagicDatabasePath` pointing the core at the
         * result. odrcore 6.2 ended both: the css and js are written into the html it produces, and
         * `Odr.mimetype` is core's own detection rather than libmagic. Both setters are inert now,
         * so the extraction only cost startup time and apk size.
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

            coreInitialized = true
        }

        /**
         * Opens [path].
         *
         * This used to pass a [DecodePreference] naming core's own decoder engine, since the
         * pdf2htmlEX and wvWare ones were compiled out of the android build and asking for them
         * would only produce "unsupported decoder engine" failures. odrcore 6 dropped those
         * backends and with them the whole engine dimension, so there is nothing left to choose.
         */
        private fun open(path: String): DecodedFile = Odr.open(path)

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
