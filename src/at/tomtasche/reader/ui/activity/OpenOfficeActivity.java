package at.tomtasche.reader.ui.activity;

import java.io.File;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class OpenOfficeActivity extends OfficeActivity {

    boolean fragmented;
    
    
    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);
	
	startService(serviceIntent);

	setContentView(R.layout.fragment_layout);
	
	View documentFrame = findViewById(R.id.document);
	fragmented = (documentFrame != null && documentFrame.getVisibility() == View.VISIBLE);
	
	showDocument();
    }

    
    private void showDocument() {
	if (fragmented) {
	    DocumentFragment document = (DocumentFragment) getSupportFragmentManager().findFragmentById(R.id.document);
	    if (document == null) {
		document = new DocumentFragment();

		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.document, document);
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.commit();
	    }
	} else {
	    Intent intent = new Intent();
	    intent.setClass(this, DocumentActivity.class);
	    startActivity(intent);
	}
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
