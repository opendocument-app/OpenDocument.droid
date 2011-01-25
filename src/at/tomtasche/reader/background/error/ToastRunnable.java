
package at.tomtasche.reader.background.error;

import android.content.Context;
import android.widget.Toast;

public class ToastRunnable implements Runnable {

    private final Context context;

    private final String text;

    public ToastRunnable(final Context context, final String text) {
        this.context = context;
        this.text = text;
    }

    @Override
    public void run() {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
