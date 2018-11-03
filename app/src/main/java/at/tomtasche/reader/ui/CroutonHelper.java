package at.tomtasche.reader.ui;

import android.app.Activity;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import de.keyboardsurfer.android.widget.crouton.Configuration;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class CroutonHelper {

    public static void showCrouton(Activity activity, int resId, final Runnable callback,
                                   Style style) {
        showCrouton(activity, activity.getString(resId), callback, style);
    }

    public static void showCrouton(Activity activity, final String message, final Runnable callback,
                                   final Style style) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Crouton crouton = Crouton.makeText(activity, message, style);

                Configuration configuration = new Configuration.Builder().setDuration(10000).build();
                crouton.setConfiguration(configuration);

                crouton.setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {
                                if (callback != null)
                                    callback.run();
                            }
                        });
                crouton.show();
            }
        });
    }
}
