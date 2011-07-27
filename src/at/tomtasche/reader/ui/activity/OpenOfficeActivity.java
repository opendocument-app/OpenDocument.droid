package at.tomtasche.reader.ui.activity;

import java.io.File;

import android.os.Bundle;
import android.view.Window;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.PagesFragment;

public class OpenOfficeActivity extends OfficeActivity {

    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);
	
	startService(serviceIntent);

	setContentView(R.layout.fragment_layout);
    }


    private void cleanCache() {
	// TODO: sort pictures in folders and delete old pictures asynchronous

	for (final String s : getCacheDir().list()) {
	    try {
		new File(getCacheDir() + "/" + s).delete();
	    } catch (final Exception e) {
		e.printStackTrace();
	    }
	}
    }


    @Override
    protected void onStop() {
	super.onStop();

	stopService(serviceIntent);
    }

    @Override
    protected void onDestroy() {
	cleanCache();

	super.onDestroy();
    }
}
