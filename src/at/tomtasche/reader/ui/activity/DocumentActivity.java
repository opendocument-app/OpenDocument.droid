package at.tomtasche.reader.ui.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class DocumentActivity extends FragmentActivity {

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
}
