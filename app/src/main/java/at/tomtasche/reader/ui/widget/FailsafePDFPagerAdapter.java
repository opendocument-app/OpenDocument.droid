package at.tomtasche.reader.ui.widget;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import es.voghdev.pdfviewpager.library.adapter.PDFPagerAdapter;

public class FailsafePDFPagerAdapter extends PDFPagerAdapter {

    public FailsafePDFPagerAdapter(Context context, String pdfPath) {
        super(context, pdfPath);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean isInitialized() {
        if (renderer == null) {
            return false;
        }

        try {
            renderer.getPageCount();
        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    @Override
    public int getCount() {
        try {
            return super.getCount();
        } catch (Throwable t) {
            // crashes if file was not loaded successfully before
            t.printStackTrace();
        }

        return 0;
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Throwable t) {
            // crashes if file was not loaded successfully before
            t.printStackTrace();
        }
    }
}
