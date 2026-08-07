package app.opendocument.droid.background

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import app.opendocument.droid.R
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.activity.MainActivity
import com.commonsware.android.print.PdfDocumentAdapter
import java.io.File

class PrintingManager {

    private val backgroundThread =
        HandlerThread(PrintingManager::class.java.simpleName).apply { start() }

    private val backgroundHandler = Handler(backgroundThread.looper)

    /**
     * [onFinished] runs on the main thread once the job is over, whether it printed or not. The
     * adapter is laid out and written by the print framework long after this returns, so anything
     * the WebView had to be put into for printing can only be undone from there.
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

        val printJob = printManager.print(JOB_NAME, printAdapter, PrintAttributes.Builder().build())

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
                        activity.runOnUiThread(onFinished)

                        return
                    }

                    SnackbarHelper.show(activity, R.string.crouton_printing, null, false, false)

                    backgroundHandler.postDelayed(this, 1000)
                }
            }

        checkPrintJob.run()
    }

    fun close() {
        backgroundThread.quit()
    }

    private companion object {
        const val JOB_NAME = "OpenDocument Reader - Document"
    }
}
