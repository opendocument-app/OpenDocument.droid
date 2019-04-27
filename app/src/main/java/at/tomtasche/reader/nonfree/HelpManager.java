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

        /*HelpCrunchOptions options = new HelpCrunchOptions()
                .setRequestName(false).setNotificationsChannelTitle("Support");

        HelpCrunch.initializeWithOptions(
                context,
                "opendocumentreader",
                1,
                "Fs5HNI5XBRXjgr4dfc7fFd7aVGElLznF3p9hLUAD/2DPpLefIQ5+IZgQlBYgCfQ8bG/xBUx8nQsaAQCqOy3wuA==",
                options
        );*/
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void show() {
        if (!enabled) {
            return;
        }

        context.startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://opendocument.app/")));
        //HelpCrunch.showChatScreen(context);
    }
}
