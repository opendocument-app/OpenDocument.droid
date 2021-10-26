package at.tomtasche.reader.nonfree;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class HelpManager {

    private boolean enabled;
    private Context context;

    public void initialize(Context context) {
        this.context = context;

        if (!enabled) {
            return;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void show() {
        context.startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://opendocument.app/")));
    }
}
