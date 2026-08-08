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

    @Suppress("DEPRECATION")
    fun print(activity: MainActivity, webView: WebView) {
        print(activity, webView.createPrintDocumentAdapter())
    }

    fun print(activity: MainActivity, pdfFile: File) {
        print(activity, PdfDocumentAdapter(activity, JOB_NAME, pdfFile))
    }

    private fun print(activity: MainActivity, printAdapter: PrintDocumentAdapter) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager

        val printJob = printManager.print(JOB_NAME, printAdapter, PrintAttributes.Builder().build())

        val checkPrintJob =
            object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) {
                        return
                    }

                    // cancelled and failed are ends too - waiting only for isCompleted polls
                    // forever on the job the user dismissed
                    if (printJob.isCompleted || printJob.isCancelled || printJob.isFailed) {
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

    private companion object {
        const val JOB_NAME = "OpenDocument Reader - Document"
    }
}
