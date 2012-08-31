package at.tomtasche.reader.ui.activity;

import java.io.File;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import at.tomtasche.reader.Document;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.DocumentLoader.OnErrorCallback;
import at.tomtasche.reader.background.DocumentLoader.OnSuccessCallback;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class OfficeActivity extends FragmentActivity implements OnSuccessCallback, OnErrorCallback {

    private DocumentFragment documentFragment;

    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);

	setContentView(R.layout.fragment_layout);

	documentFragment = (DocumentFragment) getSupportFragmentManager().findFragmentById(
		R.id.document);
    }

    @Override
    protected void onResume() {
	super.onResume();

	loadDocument(getIntent().getData() != null ? getIntent().getData() : DocumentLoader.URI_INTRO);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
	super.onCreateOptionsMenu(menu);

	getMenuInflater().inflate(R.menu.menu_main, menu);

	return true;
    }

//    @Override
//    public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
//	switch (item.getItemId()) {
//	case R.id.menu_page_list: {
//	    if (getDocumentFragment().getPageCount() > 1) {
//		AlertDialog.Builder builder = new AlertDialog.Builder(this);
//		builder.setTitle(getString(R.string.page_dialog_title));
//		builder.setItems(
//			getDocumentFragment().getPageNames().toArray(
//				new CharSequence[getDocumentFragment().getPageCount()]),
//			new DialogInterface.OnClickListener() {
//
//			    public void onClick(DialogInterface dialog, int item) {
//				getDocumentFragment().jumpToPage(item);
//			    }
//			});
//		builder.create().show();
//	    }
//
//	    break;
//	}
//
//	case R.id.menu_search: {
//	    // http://www.androidsnippets.org/snippets/20/
//	    final AlertDialog.Builder alert = new AlertDialog.Builder(this);
//	    alert.setTitle(getString(R.string.menu_search));
//
//	    final EditText input = new EditText(this);
//	    alert.setView(input);
//
//	    alert.setPositiveButton(getString(android.R.string.ok),
//		    new DialogInterface.OnClickListener() {
//
//			@Override
//			public void onClick(final DialogInterface dialog, final int whichButton) {
//			    DocumentView view = getDocumentView();
//
//			    if (view == null)
//				return;
//
//			    view.findAll(input.getText().toString());
//			}
//		    });
//	    alert.setNegativeButton(getString(android.R.string.cancel), null);
//	    alert.show();
//
//	    break;
//	}
//
//	case R.id.menu_open: {
//	    findDocument();
//
//	    break;
//	}
//
//	case R.id.menu_page_next: {
//	    getDocumentFragment().nextPage();
//
//	    break;
//	}
//
//	case R.id.menu_page_previous: {
//	    getDocumentFragment().previousPage();
//
//	    break;
//	}
//
//	case R.id.menu_about: {
//	    showDocument(Uri.parse("reader://intro.odt"));
//
//	    break;
//	}
//	}
//
//	return super.onMenuItemSelected(featureId, item);
//    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
	super.onActivityResult(requestCode, resultCode, data);

	if (requestCode == 42 && resultCode == RESULT_OK) {
	    loadDocument(data.getData());
	}
    }

//    private void findDocument() {
//	final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//	intent.setType("application/vnd.oasis.opendocument.*");
//	intent.addCategory(Intent.CATEGORY_OPENABLE);
//
//	startActivityForResult(intent, 42);
//    }

    private void loadDocument(Uri uri) {
	DocumentLoader documentLoader = new DocumentLoader(this);
	documentLoader.setOnSuccessCallback(this);
	documentLoader.setOnErrorCallback(this);
	documentLoader.execute(uri);
    }

    @Override
    public void onError(Exception exception) {
	// TODO: show toast
    }

    @Override
    public void onSuccess(Document document) {
	if (documentFragment != null) {
	    documentFragment.loadDocument(document);
	} else {
	    Intent intent = new Intent();
	    intent.setClass(OfficeActivity.this, DocumentFragmentActivity.class);
	    intent.putExtra(DocumentFragmentActivity.EXTRA_DOCUMENT, document);
	    startActivity(intent);
	}
    }

    @Override
    protected void onDestroy() {
	super.onDestroy();

	// TODO: ugly threading
	new Thread() {

	    @Override
	    public void run() {
		// clean cache
		for (final String s : getCacheDir().list()) {
		    try {
			new File(getCacheDir() + "/" + s).delete();
		    } catch (final Exception e) {
			e.printStackTrace();
		    }
		}
	    }
	}.start();
    }
}
