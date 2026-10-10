package app.opendocument.droid.background

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintAttributes.MediaSize
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import app.opendocument.droid.R
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.activity.MainActivity
import com.commonsware.android.print.PdfDocumentAdapter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class PrintingManager {

    private val backgroundThread =
        HandlerThread(PrintingManager::class.java.simpleName).apply { start() }

    private val backgroundHandler = Handler(backgroundThread.looper)

    /**
     * [onFinished] runs on the main thread once, when the print framework lets go of the adapter.
     * The adapter is laid out and written long after this returns, so anything the WebView had to
     * be put into for printing can only be undone from there.
     */
    @Suppress("DEPRECATION")
    fun print(
        activity: MainActivity,
        webView: WebView,
        pageSize: PageSize?,
        onFinished: () -> Unit,
    ) {
        print(activity, webView.createPrintDocumentAdapter(), pageSize, onFinished)
    }

    fun print(activity: MainActivity, pdfFile: File) {
        print(activity, PdfDocumentAdapter(activity, JOB_NAME, pdfFile), firstPageSize(pdfFile)) {}
    }

    private fun print(
        activity: MainActivity,
        printAdapter: PrintDocumentAdapter,
        pageSize: PageSize?,
        onFinished: () -> Unit,
    ) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager

        // Restore the page once the adapter finishes or the job terminates.
        val finishedOnce = AtomicBoolean(false)
        val finish = {
            if (finishedOnce.compareAndSet(false, true)) {
                // the destroyed check belongs on the main thread, which is where it happens:
                // there is nothing left to restore then, and the webview may be gone
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        onFinished()
                    }
                }
            }
        }

        val printJob =
            printManager.print(
                JOB_NAME,
                FinishReportingAdapter(printAdapter, finish),
                attributesFor(pageSize),
            )

        val checkPrintJob =
            object : Runnable {
                override fun run() {
                    // nothing left to restore, and the webview it would touch may be gone
                    if (activity.isFinishing || activity.isDestroyed) {
                        return
                    }

                    // cancelled and failed are ends too - waiting only for isCompleted polls
                    // forever on the job the user dismissed
                    if (printJob.isCompleted || printJob.isCancelled || printJob.isFailed) {
                        finish()

                        return
                    }

                    SnackbarHelper.show(
                        activity,
                        R.string.crouton_printing,
                        null,
                        isIndefinite = false,
                        isError = false,
                    )

                    backgroundHandler.postDelayed(this, 1000)
                }
            }

        checkPrintJob.run()
    }

    fun close() {
        backgroundThread.quit()
    }

    /** The paper the dialog starts on: the document's own, where the printer has it. */
    private fun attributesFor(pageSize: PageSize?): PrintAttributes {
        val builder = PrintAttributes.Builder()
        if (pageSize != null) {
            builder.setMediaSize(mediaSizeFor(pageSize))
        }
        return builder.build()
    }

    private fun mediaSizeFor(pageSize: PageSize): MediaSize {
        val media =
            when (pageSize.paper()) {
                Paper.ISO_A3 -> MediaSize.ISO_A3
                Paper.ISO_A4 -> MediaSize.ISO_A4
                Paper.ISO_A5 -> MediaSize.ISO_A5
                Paper.NA_LETTER -> MediaSize.NA_LETTER
                Paper.NA_LEGAL -> MediaSize.NA_LEGAL
                Paper.NA_TABLOID -> MediaSize.NA_TABLOID
                // only the orientation, for a size no printer names
                null ->
                    return if (pageSize.isLandscape) MediaSize.UNKNOWN_LANDSCAPE
                    else MediaSize.UNKNOWN_PORTRAIT
            }
        return if (pageSize.isLandscape) media.asLandscape() else media.asPortrait()
    }

    /** Null where the framework cannot read the file. */
    private fun firstPageSize(pdfFile: File): PageSize? =
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) {
                        return null
                    }
                    renderer.openPage(0).use { page ->
                        // points, 1/72 in
                        PageSize(page.width * 1000 / 72, page.height * 1000 / 72)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }

    /** [delegate] with a note taken of [PrintDocumentAdapter.onFinish], the framework's goodbye. */
    private class FinishReportingAdapter(
        private val delegate: PrintDocumentAdapter,
        private val onFinished: () -> Unit,
    ) : PrintDocumentAdapter() {

        override fun onStart() = delegate.onStart()

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) = delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) = delegate.onWrite(pages, destination, cancellationSignal, callback)

        override fun onFinish() {
            delegate.onFinish()

            onFinished()
        }
    }

    companion object {
        private const val JOB_NAME = "OpenDocument Reader - Document"

        /**
         * Whether the file itself prints rather than the page: a pdf whose page shows no more than
         * the file, and which the print framework can open without a password.
         */
        fun printsOriginal(mimeType: String?, encrypted: Boolean, unsavedEdits: Boolean): Boolean =
            mimeType == "application/pdf" && !encrypted && !unsavedEdits
    }
}
