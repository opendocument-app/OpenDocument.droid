package at.tomtasche.reader.ui.activity;

import java.io.File;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.DocumentFragment;
import at.tomtasche.reader.ui.widget.DocumentView;

public class OfficeActivity extends FragmentActivity {

    boolean fragmented;

    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);

	setContentView(R.layout.fragment_layout);

	View documentFrame = findViewById(R.id.document);
	fragmented = (documentFrame != null && documentFrame.getVisibility() == View.VISIBLE);
    }

    @Override
    protected void onResume() {
	super.onResume();

	showDocument(getIntent().getData() != null ? getIntent().getData() : Uri
		.parse("reader://intro.odt"));
    }

    private void findDocument() {
	final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	intent.setType("application/vnd.oasis.opendocument.*");
	intent.addCategory(Intent.CATEGORY_OPENABLE);

	startActivityForResult(intent, 42);
    }

    private DocumentView getDocumentView() {
	DocumentFragment fragment = (DocumentFragment) getSupportFragmentManager()
		.findFragmentById(R.id.document);

	if (fragment == null || !fragment.isVisible())
	    return null;

	return fragment.getDocumentView();
    }

    private DocumentFragment getDocumentFragment() {
	return (DocumentFragment) getSupportFragmentManager().findFragmentById(R.id.document);
    }

    private void showDocument(Uri uri) {
	if (fragmented) {
	    DocumentFragment document = (DocumentFragment) getSupportFragmentManager()
		    .findFragmentById(R.id.document);
	    if (document == null) {
		document = new DocumentFragment(uri);

		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.document, document);
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.commit();
	    }
	} else {
	    Intent intent = new Intent();
	    intent.setClass(this, DocumentActivity.class);
	    intent.setData(uri);
	    startActivity(intent);
	}
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
	super.onCreateOptionsMenu(menu);

	getMenuInflater().inflate(R.menu.menu_main, menu);

	return true;
    }

    @Override
    public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
	switch (item.getItemId()) {
	case R.id.menu_page_list: {
	    if (getDocumentFragment().getPageCount() > 1) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.page_dialog_title));
		builder.setItems(
			getDocumentFragment().getPageNames().toArray(
				new CharSequence[getDocumentFragment().getPageCount()]),
			new DialogInterface.OnClickListener() {

			    public void onClick(DialogInterface dialog, int item) {
				getDocumentFragment().jumpToPage(item);
			    }
			});
		builder.create().show();
	    }

	    break;
	}

	case R.id.menu_search: {
	    // http://www.androidsnippets.org/snippets/20/
	    final AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    alert.setTitle(getString(R.string.menu_search));

	    final EditText input = new EditText(this);
	    alert.setView(input);

	    alert.setPositiveButton(getString(android.R.string.ok),
		    new DialogInterface.OnClickListener() {

			@Override
			public void onClick(final DialogInterface dialog, final int whichButton) {
			    DocumentView view = getDocumentView();

			    if (view == null)
				return;

			    view.findAll(input.getText().toString());
			}
		    });
	    alert.setNegativeButton(getString(android.R.string.cancel), null);
	    alert.show();

	    break;
	}

	case R.id.menu_open: {
	    findDocument();

	    break;
	}

	case R.id.menu_page_next: {
	    getDocumentFragment().nextPage();

	    break;
	}

	case R.id.menu_page_previous: {
	    getDocumentFragment().previousPage();

	    break;
	}

	case R.id.menu_about: {
	    showDocument(Uri.parse("reader://intro.odt"));

	    break;
	}
	}

	return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
	super.onActivityResult(requestCode, resultCode, data);

	if (requestCode == 42 && resultCode == RESULT_OK) {
	    showDocument(data.getData());
	}
    }

    @Override
    protected void onDestroy() {
	super.onDestroy();

	cleanCache();
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
}
