package at.tomtasche.reader.background;

import android.annotation.TargetApi;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;
import at.tomtasche.reader.ui.activity.MainActivity;
import de.keyboardsurfer.android.widget.crouton.Style;

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

		activity.showCrouton("Printing...", null, Style.INFO);
	}
}
