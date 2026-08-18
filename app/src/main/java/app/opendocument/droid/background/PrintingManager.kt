package app.opendocument.droid.background

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
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
    fun print(activity: MainActivity, webView: WebView, onFinished: () -> Unit) {
        print(activity, webView.createPrintDocumentAdapter(), onFinished)
    }

    fun print(activity: MainActivity, pdfFile: File) {
        print(activity, PdfDocumentAdapter(activity, JOB_NAME, pdfFile)) {}
    }

    private fun print(
        activity: MainActivity,
        printAdapter: PrintDocumentAdapter,
        onFinished: () -> Unit,
    ) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager

        // the adapter is done well before the job is: a printer that is off leaves the job queued
        // or blocked for as long as it takes the user to notice, and the page is free either way.
        // the job is still watched as the backstop, for the adapter that is dropped without a
        // last call - whichever comes first wins, which is why this only runs once
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
                PrintAttributes.Builder().build(),
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

    private companion object {
        const val JOB_NAME = "OpenDocument Reader - Document"
    }
}
