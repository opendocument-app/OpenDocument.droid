package at.tomtasche.reader.background;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import openoffice.IllegalMimeTypeException;
import openoffice.MimeTypeNotFoundException;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.openoffice.JOpenDocumentWrapper;
import at.tomtasche.reader.ui.OfficeInterface;

public class DocumentLoader extends Handler implements DocumentInterface, OfficeInterface {

    public static DocumentLoader getThreadedLoader(OfficeInterface office) {
        HandlerThread thread = new HandlerThread("DocumentLoader");
        thread.start();
        
        return new DocumentLoader(thread.getLooper(), office);
    }


    private JOpenDocumentWrapper tschopen;

    private OfficeInterface office;


    private DocumentLoader(Looper looper, OfficeInterface office) {
        super(looper);

        this.office = office;
    }

    
    public void loadDocument(InputStream stream, File cache) throws Exception {
        try {
            tschopen = new JOpenDocumentWrapper(this, stream, cache);
        } catch (final MimeTypeNotFoundException e) {
            e.printStackTrace();

            showToast(R.string.toast_error_open_file);
        } catch (final IllegalMimeTypeException e) {
            e.printStackTrace();

            showToast(R.string.toast_error_open_file);
        } catch (final FileNotFoundException e) {
            e.printStackTrace();

            showToast(R.string.toast_error_find_file);
        } catch (final IllegalArgumentException e) {
            e.printStackTrace();

            showToast(R.string.toast_error_illegal_file);
        } catch (final OutOfMemoryError e) {
            e.printStackTrace();

            showToast(R.string.toast_error_out_of_memory);
        }
    }

    @Override
    public boolean getNext() {
        if (tschopen == null) return false;
        return tschopen.getNext();
    }

    @Override
    public boolean getPrevious() {
        if (tschopen == null) return false;
        return tschopen.getPrevious();
    }

    @Override
    public int getPageCount() {
        if (tschopen == null) return 0;
        return tschopen.getPageCount();
    }

    @Override
    public int getPageIndex() {
        if (tschopen == null) return 0;
        return tschopen.getPageIndex();
    }

    @Override
    public void showProgress() {
        office.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                office.showProgress();
            }
        });
    }

    @Override
    public void hideProgress() {
        office.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                office.hideProgress();
            }
        });
    }

    @Override
    public void showDocument(final String html) {
        Log.e("smn", "run");
        office.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Log.e("smn", "show");
                office.showDocument(html);
            }
        });
    }

    @Override
    public void showToast(final int resId) {
        office.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                office.showToast(resId);
            }
        });
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        office.runOnUiThread(runnable);
    }    
}
