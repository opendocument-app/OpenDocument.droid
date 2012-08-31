package at.tomtasche.reader.ui.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import at.tomtasche.reader.Document;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class DocumentFragmentActivity extends FragmentActivity {

    public static final String EXTRA_DOCUMENT = "EXTRA_DOCUMENT";

    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);

	setContentView(R.layout.fragment_layout);

	DocumentFragment documentFragment = new DocumentFragment();
	getSupportFragmentManager().beginTransaction().add(R.id.document, documentFragment)
		.commit();

	Document document = getIntent().getParcelableExtra(EXTRA_DOCUMENT);
//	((DocumentFragment) getSupportFragmentManager().findFragmentById(R.id.document))
	documentFragment.loadDocument(document);
    }
}
