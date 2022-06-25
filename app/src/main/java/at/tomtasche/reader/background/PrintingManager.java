package at.tomtasche.reader.background;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;

import com.commonsware.android.print.PdfDocumentAdapter;

import java.io.File;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.activity.MainActivity;

public class PrintingManager {

    private final HandlerThread backgroundThread;
    private final Handler backgroundHandler;

    public PrintingManager() {
        backgroundThread = new HandlerThread(PrintingManager.class.getSimpleName());
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void print(MainActivity activity, WebView webView) {
        PrintDocumentAdapter printAdapter = webView
                .createPrintDocumentAdapter();

        print(activity, printAdapter);
    }

    private void print(MainActivity activity, PrintDocumentAdapter printAdapter) {
        PrintManager printManager = (PrintManager) activity
                .getSystemService(Context.PRINT_SERVICE);

        String jobName = "OpenDocument Reader - Document";
        PrintJob printJob = printManager.print(jobName, printAdapter,
                new PrintAttributes.Builder().build());

        Runnable checkPrintJob = new Runnable() {
            @Override
            public void run() {
                if (!printJob.isCompleted()
                        && (!activity.isFinishing() && !activity.isDestroyed())) {
                    SnackbarHelper.show(activity, R.string.crouton_printing, null, false, false);

                    backgroundHandler.postDelayed(this, 1000);
                }
            }
        };

        checkPrintJob.run();
    }

    public void print(MainActivity activity, File pdfFile) {
        PrintDocumentAdapter printAdapter = new PdfDocumentAdapter(activity, "OpenDocument Reader - Document", pdfFile);

        print(activity, printAdapter);
    }

    public void close() {
        backgroundThread.quit();
    }
}
