package at.tomtasche.reader.ui.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class DocumentActivity extends OpenOfficeActivity {

    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);
	
	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    finish();
	    return;
	}

	if (arg0 == null) {
	    DocumentFragment document = new DocumentFragment();
	    document.setArguments(getIntent().getExtras());

	    getSupportFragmentManager().beginTransaction().add(android.R.id.content, document).commit();
	}
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    finish();
	    return;
	}
    }
}
