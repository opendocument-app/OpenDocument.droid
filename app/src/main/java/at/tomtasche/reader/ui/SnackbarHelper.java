package at.tomtasche.reader.ui;

import android.app.Activity;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class SnackbarHelper {

    public static void show(Activity activity, int resId, final Runnable callback,
                            boolean isIndefinite, boolean isError) {
        show(activity, activity.getString(android.R.string.ok), activity.getString(resId), callback, isIndefinite, isError);
    }

    private static void show(Activity activity, String buttonText, final String message, final Runnable callback, boolean isIndefinite, boolean isError) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                int duration = isIndefinite ? Snackbar.LENGTH_INDEFINITE : 20000;

                Snackbar snackbar = Snackbar.make(activity.findViewById(android.R.id.content), message, duration);
                if (callback != null) {
                    snackbar.setAction(buttonText, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            callback.run();

                            snackbar.dismiss();
                        }
                    });
                }

                if (isError) {
                    snackbar.getView().setBackgroundColor(0xffff4444);
                }

                snackbar.getView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbar.dismiss();
                    }
                });

                snackbar.show();
            }
        });
    }
}
