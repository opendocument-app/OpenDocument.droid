package at.tomtasche.reader.nonfree;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import at.tomtasche.reader.ui.activity.IntroActivity;

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
        Intent intent = new Intent(context, IntroActivity.class);
        context.startActivity(intent);
    }
}
