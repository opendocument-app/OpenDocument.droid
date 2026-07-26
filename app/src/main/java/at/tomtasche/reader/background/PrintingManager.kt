package at.tomtasche.reader.background

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import at.tomtasche.reader.R
import at.tomtasche.reader.ui.SnackbarHelper
import at.tomtasche.reader.ui.activity.MainActivity
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
                    if (!printJob.isCompleted && !activity.isFinishing && !activity.isDestroyed) {
                        SnackbarHelper.show(activity, R.string.crouton_printing, null, false, false)

                        backgroundHandler.postDelayed(this, 1000)
                    }
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
