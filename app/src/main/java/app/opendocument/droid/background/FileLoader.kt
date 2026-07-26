package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.nonfree.AnalyticsManager
import app.opendocument.droid.nonfree.CrashManager
import java.io.File
import java.util.LinkedList

/**
 * Base of the four loaders: each one turns a document into something the WebView can display and
 * reports back on the main thread through a [FileLoaderListener].
 *
 * The lifecycle is construct - [initialize] - [loadAsync]* - [close]. Constructing is deliberately
 * side effect free, so the unit tests can build a loader without a context and ask [isSupported]
 * about a mime type.
 */
abstract class FileLoader(context: Context?, protected val type: LoaderType) {

    /**
     * Null in the unit tests, which only exercise [isSupported], and dropped again by [close].
     * Everything on the loading path goes through [context] instead, which fails loudly rather than
     * NPEing somewhere further down.
     */
    private var contextOrNull: Context? = context

    protected val context: Context
        get() = checkNotNull(contextOrNull) { "loader has no context" }

    private lateinit var backgroundHandler: Handler
    private lateinit var mainHandler: Handler

    private var listener: FileLoaderListener? = null

    private lateinit var analyticsManager: AnalyticsManager
    protected lateinit var crashManager: CrashManager

    private var initialized = false

    /** Whether a [loadAsync] is still running. */
    var isLoading: Boolean = false
        private set

    open fun initialize(
        listener: FileLoaderListener,
        mainHandler: Handler,
        backgroundHandler: Handler,
        analyticsManager: AnalyticsManager,
        crashManager: CrashManager,
    ) {
        this.listener = listener
        this.mainHandler = mainHandler
        this.backgroundHandler = backgroundHandler
        this.analyticsManager = analyticsManager
        this.crashManager = crashManager

        initialized = true
    }

    abstract fun isSupported(options: Options): Boolean

    fun loadAsync(options: Options) {
        if (!initialized) {
            throw RuntimeException("not initialized")
        }

        isLoading = true

        backgroundHandler.post {
            loadSync(options)

            isLoading = false
        }
    }

    protected abstract fun loadSync(options: Options)

    open fun retranslate(options: Options, htmlDiff: String): File? {
        throw RuntimeException("not implemented")
    }

    protected fun callOnSuccess(result: Result) {
        mainHandler.post {
            analyticsManager.report(
                "loader_success_$type",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                result.options.fileType,
                AnalyticsConstants.PARAM_CONTENT,
                result.options.fileExtension,
            )

            listener?.onSuccess(result)
        }
    }

    protected fun callOnError(result: Result, t: Throwable) {
        crashManager.log(result.loaderType.name + " failed")
        crashManager.log(t)

        mainHandler.post {
            analyticsManager.report(
                "loader_error_$type",
                AnalyticsConstants.PARAM_CONTENT_TYPE,
                result.options.fileType,
                AnalyticsConstants.PARAM_CONTENT,
                result.options.fileExtension,
            )

            listener?.onError(result, t)
        }
    }

    open fun close() {
        backgroundHandler.post {
            initialized = false
            listener = null
            contextOrNull = null
        }
    }

    enum class LoaderType {
        CORE,
        ONLINE,
        RAW,
        METADATA,
    }

    /**
     * What to load and everything the loaders found out about it along the way - [MetadataLoader]
     * fills most of these in before handing the same instance on to the loader that does the work.
     */
    class Options() : Parcelable {

        var originalUri: Uri? = null
        var cacheUri: Uri? = null
        var persistentUri: Boolean = false

        var fileExists: Boolean = false
        var filename: String? = null
        var fileType: String? = null
        var fileExtension: String? = null

        var password: String? = null

        var limit: Boolean = false
        var translatable: Boolean = false

        @Suppress("DEPRECATION") // the typed readParcelable overload only exists since API 33
        private constructor(parcel: Parcel) : this() {
            val classLoader = Options::class.java.classLoader
            originalUri = parcel.readParcelable(classLoader)
            cacheUri = parcel.readParcelable(classLoader)
            persistentUri = ParcelUtil.readBoolean(parcel)
            fileExists = ParcelUtil.readBoolean(parcel)
            filename = parcel.readString()
            fileType = parcel.readString()
            fileExtension = parcel.readString()
            password = parcel.readString()
            limit = ParcelUtil.readBoolean(parcel)
            translatable = ParcelUtil.readBoolean(parcel)
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(originalUri, 0)
            parcel.writeParcelable(cacheUri, 0)
            ParcelUtil.writeBoolean(parcel, persistentUri)
            ParcelUtil.writeBoolean(parcel, fileExists)
            parcel.writeString(filename)
            parcel.writeString(fileType)
            parcel.writeString(fileExtension)
            parcel.writeString(password)
            ParcelUtil.writeBoolean(parcel, limit)
            ParcelUtil.writeBoolean(parcel, translatable)
        }

        companion object {
            // @JvmField because the framework looks CREATOR up as a static field
            @JvmField
            val CREATOR: Parcelable.Creator<Options> =
                object : Parcelable.Creator<Options> {
                    override fun createFromParcel(parcel: Parcel): Options = Options(parcel)

                    override fun newArray(size: Int): Array<Options?> = arrayOfNulls(size)
                }
        }
    }

    /**
     * The outcome of one load: the [Options] it ran with plus one uri per part of the document
     * (spreadsheets have one per sheet, everything else a single one with a null title).
     */
    class Result(val loaderType: LoaderType, val options: Options) : Parcelable {

        val partTitles: MutableList<String?> = LinkedList()
        val partUris: MutableList<Uri> = LinkedList()

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(loaderType.name)
            parcel.writeParcelable(options, 0)
            parcel.writeList(partTitles)
            parcel.writeList(partUris)
        }

        companion object {
            // @JvmField because the framework looks CREATOR up as a static field
            @JvmField
            val CREATOR: Parcelable.Creator<Result> =
                object : Parcelable.Creator<Result> {

                    @Suppress("DEPRECATION") // typed readParcelable needs API 33
                    override fun createFromParcel(parcel: Parcel): Result {
                        // in the order writeToParcel wrote them
                        val classLoader = Result::class.java.classLoader
                        val loaderType = LoaderType.valueOf(parcel.readString()!!)
                        val options = parcel.readParcelable<Options>(classLoader)!!

                        return Result(loaderType, options).also {
                            parcel.readList(it.partTitles, classLoader)
                            parcel.readList(it.partUris, classLoader)
                        }
                    }

                    override fun newArray(size: Int): Array<Result?> = arrayOfNulls(size)
                }
        }
    }

    interface FileLoaderListener {

        fun onSuccess(result: Result)

        fun onError(result: Result, error: Throwable)
    }

    class EncryptedDocumentException : Exception()
}
