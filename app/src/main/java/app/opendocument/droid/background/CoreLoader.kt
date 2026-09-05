package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.system.Os
import android.util.Log
import app.opendocument.core.DecodePreference
import app.opendocument.core.DecodedFile
import app.opendocument.core.Document
import app.opendocument.core.DocumentType
import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Html
import app.opendocument.core.HtmlColorScheme
import app.opendocument.core.HtmlConfig
import app.opendocument.core.HtmlView
import app.opendocument.core.HttpServer
import app.opendocument.core.Odr
import app.opendocument.core.OdrException
import app.opendocument.core.TableDimensions
import app.opendocument.droid.nonfree.CrashManager
import java.io.File
import java.io.IOException

/**
 * Loads documents through odrcore and publishes them on a local http server.
 *
 * Owns the process wide core state: the one-time initialization, the single http server and the
 * currently open [Document] that [retranslate] edits.
 */
class CoreLoader(private val context: Context) {

    private lateinit var crashManager: CrashManager

    private var document: Document? = null
    private var lastInputPath: String? = null
    private var lastDocumentType: DocumentType = DocumentType.UNKNOWN

    /** Counts the renders, so each one publishes under a prefix of its own - see [render]. */
    private var renderCount = 0

    /**
     * Whether the document [host] last opened is one [edit] can do something with - the core's own
     * answer, since [host] only keeps a document that reports itself editable and savable.
     */
    val isDocumentEditable: Boolean
        get() = document != null

    /**
     * Whether the core reads what [host] last opened as a document rather than only showing it -
     * true of csv and markdown as well as the document formats, false of an image or an archive.
     */
    val readsAsDocument: Boolean
        get() = lastDocumentType != DocumentType.UNKNOWN

    fun initialize(crashManager: CrashManager) {
        this.crashManager = crashManager

        // loads the native library and points the core at a usable temporary directory
        initializeCore(context)

        startSharedServer(crashManager)
    }

    /**
     * Renders [file] and publishes it, replacing whatever was published before.
     *
     * Under a prefix of its own, so a request the page it replaces still had in flight cannot be
     * mistaken for one for the new page - `PageView.failPage` tells them apart by url.
     *
     * Throws what odrcore threw: which failure it is decides which bar the user gets, so
     * [DocumentLoader] needs the exception itself.
     */
    fun render(request: DocumentRequest, file: IdentifiedFile): LoadedDocument {
        val cachedFile =
            checkNotNull(FileCache.getCacheFile(context, file.cacheUri)) {
                "not a cached file: " + file.cacheUri
            }
        val cacheDirectory = FileCache.getCacheDirectory(cachedFile)

        val coreCacheDirectory = File(cacheDirectory, "core_cache")

        lastInputPath = cachedFile.path

        val views =
            host(
                prefix = "odr" + renderCount++,
                inputPath = cachedFile.path,
                cachePath = coreCacheDirectory.path,
                password = request.password,
                editable = request.editable,
                paging = PaginationSetting.isEnabled(context),
                keepDocument = true,
                declaredType = declaredType(file),
            )

        return LoadedDocument(
            request,
            file,
            views.map { it.name },
            views.map { Uri.parse(it.url) },
            views.map { it.sheetCut },
            isDocumentEditable,
            readsAsDocument,
        )
    }

    /**
     * Opens [inputPath], translates it to html and publishes it on the shared http server under
     * [prefix], replacing whatever was published before.
     *
     * [keepDocument] retains the decoded document for [retranslate]; [declaredType] is what the
     * document is called - see [openFile].
     */
    fun host(
        prefix: String,
        inputPath: String,
        cachePath: String,
        password: String? = null,
        editable: Boolean = false,
        paging: Boolean = false,
        keepDocument: Boolean = false,
        declaredType: FileType? = null,
    ): List<HostedView> {
        val server = checkNotNull(sharedServer) { "core server is not running" }

        Log.i(TAG, "host file")

        server.clear()

        var file = openFile(inputPath, declaredType)

        if (file.passwordEncrypted()) {
            // the core's answer, not a list of ours: a legacy .doc, .ppt or .xls has no way in
            // whatever the password, so the prompt would be a dialog that can never close
            if (!file.capabilities().decrypt) {
                throw UndecryptableFile(inputPath)
            }

            if (password == null) {
                throw OdrException.FileEncrypted(inputPath)
            }
            file = file.decrypt(password)
        }

        Log.i(TAG, "type=" + Odr.fileTypeToString(file.fileType()))

        // the type it opened as, which a markdown file read by its name is not the mime type of
        lastDocumentType = Odr.documentTypeByFileType(file.fileType()) ?: DocumentType.UNKNOWN

        // the core opens text it cannot name a charset for and only fails once a page is
        // rendered - on the server thread, long after this reported success. so ask now
        if (file.isTextFile && file.asTextFile().charset() == null) {
            throw OdrException.UnsupportedFileType("no charset could be detected: $inputPath")
        }

        if (keepDocument) {
            closeDocument()

            // an upper bound the core answers without decoding, so a format that declares no
            // editing is not opened just to be told no
            val capabilities = file.capabilities()

            if (file.isDocumentFile && capabilities.edit && capabilities.save) {
                // TODO this will cause a second load
                val document = file.asDocumentFile().document()

                // the document itself is the precise answer, and a read only one held open buys
                // that second parse and nothing else
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

        // both schemes, each behind prefers-color-scheme, rather than the one it is being read in
        // now: this is decided while translating, and darkening is turned on and off over the open
        // document. PageView.setDarkeningAllowed picks between them
        htmlConfig.colorScheme = HtmlColorScheme.SYSTEM

        // stated rather than inherited, and this device's answer rather than one number for
        // every phone - see SpreadsheetBudget. What is cut is reported rather than dropped
        // silently: HostedView carries the core's own account of it
        htmlConfig.spreadsheetLimit =
            TableDimensions(SpreadsheetBudget.ROWS, SpreadsheetBudget.COLUMNS)
        htmlConfig.spreadsheetCellLimit = SpreadsheetBudget.cells(context)
        htmlConfig.spreadsheetLimitByContent = true

        val cacheDirectory = File(cachePath)
        cacheDirectory.deleteRecursively()
        cacheDirectory.mkdirs()

        val service = Html.translate(file, cachePath, htmlConfig)
        server.connectService(service, prefix)

        return selectViews(file, service.listViews()).map { view ->
            Log.i(TAG, "view name=" + view.name() + " path=" + view.path())
            HostedView(
                view.name(),
                "http://$SERVER_URL_HOST:$sharedServerPort/file/$prefix/" + view.path(),
                view.sheetCut()?.let {
                    SheetCut(
                        it.content.rows,
                        it.content.columns,
                        it.rendered.rows,
                        it.rendered.columns,
                    )
                },
            )
        }
    }

    /**
     * What the document is *called*. Not [IdentifiedFile.mimeType]: `FileIdentifier` takes that
     * from `Odr.mimetype` wherever it answered, so it would be the same reading again.
     *
     * Only a name the core has a decoder for: html is named by the table and not opened by it, so
     * calling a file `.html` answers nothing [openAs] could act on.
     */
    private fun declaredType(file: IdentifiedFile): FileType? {
        val extension = MimeTypeResolver.parseExtension(file.filename)?.lowercase() ?: return null
        val type = Odr.fileTypeByFileExtension(extension) ?: return null

        return type.takeIf { it != FileType.UNKNOWN && Odr.capabilitiesByFileType(it).open }
    }

    /**
     * [inputPath] as odrcore reads its bytes, or as [declaredType] where it answers *text* - its
     * bucket for bytes nothing else claims, and where a pdf carrying its http response lands.
     *
     * Detection stays first, and only some names outrank text - see [nameOutranksText].
     */
    private fun openFile(inputPath: String, declaredType: FileType?): DecodedFile {
        val detected =
            try {
                Odr.open(inputPath)
            } catch (e: Throwable) {
                // nothing was recognised at all, which the name may still answer for
                if (declaredType == null) {
                    throw e
                }

                return openAs(inputPath, declaredType) ?: throw e
            }

        if (
            declaredType == null ||
                declaredType == detected.fileType() ||
                !detected.isTextFile ||
                !nameOutranksText(declaredType)
        ) {
            return detected
        }

        return openAs(inputPath, declaredType) ?: detected
    }

    /**
     * Whether what the file is called beats a text reading: a document, or a format the core says
     * it cannot recognise from its bytes.
     *
     * Csv is neither - the core decides it from plain text itself - and markdown is the case it
     * says it cannot, so the name is all there is.
     */
    private fun nameOutranksText(declaredType: FileType): Boolean =
        Odr.fileCategoryByFileType(declaredType) == FileCategory.DOCUMENT ||
            !Odr.capabilitiesByFileType(declaredType).detectByContent

    /** [inputPath] opened as [type], or null where it is not one after all. */
    private fun openAs(inputPath: String, type: FileType): DecodedFile? =
        try {
            Odr.open(inputPath, DecodePreference().apply { asFileType = type })
        } catch (e: Throwable) {
            Log.i(TAG, "not a " + Odr.fileTypeToString(type))

            null
        }

    /** The document with [htmlDiff] applied, written to a file of ours. Null if that failed. */
    fun retranslate(request: DocumentRequest, file: IdentifiedFile, htmlDiff: String): File? {
        try {
            if (document == null) {
                // nothing is held open after a rebuild, so open it again before editing it
                render(request, file)
            }

            val inputFile = File(checkNotNull(lastInputPath))
            val inputCacheDirectory = FileCache.getCacheDirectory(inputFile)

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

        // the file type's extension, not [Odr.fileTypeToString], which is its name - and a name
        // like "ooxml_encrypted" is not something a file can be called
        val extension = Odr.fileExtensionByFileType(document.fileType())
        val outputFile = File("$outputPathPrefix.$extension")

        Log.d(TAG, "HTML diff: $htmlDiff")

        Html.edit(document, htmlDiff)
        document.save(outputFile.path)

        return outputFile
    }

    /**
     * Drops what this loader published and leaves the server itself running - see [sharedServer]
     * for why it is never stopped.
     *
     * Belongs on the thread the loads run on, so it cannot race one in flight.
     */
    fun close() {
        sharedServer?.clear()

        closeDocument()
    }

    private fun closeDocument() {
        document?.close()
        document = null
    }

    /**
     * A translated view of a document, ready to be opened in the WebView. [sheetCut] is set only
     * where the budget cut the sheet this view renders.
     */
    data class HostedView(val name: String, val url: String, val sheetCut: SheetCut?)

    /** An encrypted file whose format odrcore cannot decrypt, whatever the password. */
    class UndecryptableFile(path: String) : IOException("cannot be decrypted: $path")

    companion object {
        private const val TAG = "CoreLoader"

        /**
         * The one http server of the process, started on the first [initialize] and never stopped.
         *
         * There is nobody to stop it for: the server is process wide, so no single loader's
         * teardown is the end of it. [close] drops what it published with [HttpServer.clear] and
         * leaves the socket listening; the process exit reclaims it.
         */
        private var sharedServer: HttpServer? = null

        private var sharedServerPort = PREFERRED_SERVER_PORT

        /**
         * Binds [server], preferring [PREFERRED_SERVER_PORT], and returns the port it got.
         *
         * The preferred port is hardcoded in both flavors, so lite and pro collide whenever they
         * run on one device - the instrumented tests do exactly that. A failed bind used to be
         * silent, leaving every document at ERR_CONNECTION_REFUSED.
         */
        private fun bind(server: HttpServer, crashManager: CrashManager): Int {
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

            try {
                return server.bind(SERVER_BIND_ADDRESS, ANY_PORT)
            } catch (e: Throwable) {
                crashManager.log(IOException("core server cannot bind any port", e))
            }

            // nothing is bound, so listen() fails too - this port only decides which url the
            // documents that follow are refused at
            return PREFERRED_SERVER_PORT
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
         * the other flavor, so it is not always free. See [bind].
         */
        const val PREFERRED_SERVER_PORT: Int = 29665

        private var coreInitialized = false

        /** One-time process wide setup of the core: the temporary directory, and nothing else. */
        @Synchronized
        fun initializeCore(context: Context) {
            if (coreInitialized) {
                return
            }

            // core resolves its temporary directory through std::filesystem::temp_directory_path(),
            // which falls back to /tmp - a path that does not exist on android. has to happen
            // before the first native call, because core caches the directory on first use
            Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)

            Log.i(TAG, "odrcore " + Odr.identify())

            coreInitialized = true
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
