package at.tomtasche.reader.background;

import android.annotation.TargetApi;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.SnackbarHelper;
import at.tomtasche.reader.ui.activity.MainActivity;

@TargetApi(19)
public class KitKatPrinter {

    public static void print(MainActivity activity, WebView webView) {
        PrintManager printManager = (PrintManager) activity
                .getSystemService(Context.PRINT_SERVICE);

        PrintDocumentAdapter printAdapter = webView
                .createPrintDocumentAdapter();

        String jobName = "OpenDocument Reader - Document";
        PrintJob printJob = printManager.print(jobName, printAdapter,
                new PrintAttributes.Builder().build());

        SnackbarHelper.show(activity, R.string.crouton_printing, null, false, false);
    }
}
