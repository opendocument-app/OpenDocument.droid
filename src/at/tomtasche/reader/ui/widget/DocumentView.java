
package at.tomtasche.reader.ui.widget;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import at.tomtasche.reader.R;

public class DocumentView extends WebView {

    private static final String ENCODING = "utf-8";

    // private GestureDetector detector;
    //
    // private SimpleOnGestureListener listener = new SimpleOnGestureListener()
    // {
    //
    // public boolean onDown(MotionEvent arg0) {
    // return false;
    // }
    //
    // public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
    // float arg3) {
    // if (arg0.getRawX() > arg1.getRawX()) {
    // Log.e("smn", "left");
    // } else {
    // Log.e("smn", "right");
    // }
    // return true;
    // }
    // };

    public DocumentView(final Context context) {
        super(context);

        final WebSettings settings = getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setLightTouchEnabled(true);
        settings.setSupportZoom(true);
        settings.setPluginsEnabled(false);
        settings.setDefaultTextEncodingName(ENCODING);

        loadData(context.getString(R.string.message_get_started), "text/plain", ENCODING);

        // detector = new GestureDetector(context, listener);
    }

    // @Override
    // protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX,
    // boolean clampedY) {
    // // TODO setOnOverScrolledListener -> flipper.showNext()
    // super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    // }

    public void loadData(final String html) {
        loadData(html, "text/html", ENCODING);
    }

    // @Override
    // public boolean onTouchEvent(MotionEvent ev) {
    // return detector.onTouchEvent(ev) || super.onTouchEvent(ev);
    // }
}
