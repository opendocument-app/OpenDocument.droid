package at.tomtasche.reader.ui.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.ui.widget.DocumentFragment;
import at.tomtasche.reader.ui.widget.DocumentView;

public abstract class OfficeActivity extends FragmentActivity {

    Intent serviceIntent;


    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);

	serviceIntent = new Intent(this, DocumentService.class);
    }


    private void findDocument() {
	final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	intent.setType("application/vnd.oasis.opendocument.*");
	intent.addCategory(Intent.CATEGORY_OPENABLE);

	startActivityForResult(intent, 42);
    }

    private DocumentView getDocumentView() {
	DocumentFragment fragment = (DocumentFragment) getSupportFragmentManager().findFragmentById(R.id.document);

	if (fragment == null || !fragment.isVisible()) return null;

	return fragment.getDocumentView();
    }

    private DocumentFragment getDocumentFragment() {
	return (DocumentFragment) getSupportFragmentManager().findFragmentById(R.id.document);
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
		builder.setItems(getDocumentFragment().getPageNames().toArray(new CharSequence[getDocumentFragment().getPageCount()]), new DialogInterface.OnClickListener() {

		    public void onClick(DialogInterface dialog, int item) {
			getDocumentFragment().jumpToPage(item);
		    }
		});
		builder.create().show();
	    }

	    break;
	}

	case R.id.menu_copy: {
	    if (Integer.parseInt(Build.VERSION.SDK) > 7) {
		DocumentView view = getDocumentView();

		if (view == null) break;

		view.emulateShiftHeld();
	    } else {
		Toast.makeText(this, getString(R.string.toast_error_copy), Toast.LENGTH_LONG)
		.show();
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

		    if (view == null) return;

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

	case R.id.menu_donate: {
	    final CharSequence[] items = {
		    getString(R.string.donate_paypal), getString(R.string.donate_market),
		    getString(R.string.donate_flattr)
	    };

	    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle(getString(R.string.donate_choose));
	    builder.setItems(items, new OnClickListener() {

		@Override
		public void onClick(final android.content.DialogInterface dialog, final int item) {
		    if (items[0].equals(items[item])) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri
				.parse("http://goo.gl/1e8K9")));
		    } else if (items[1].equals(items[item])) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri
				.parse("http://goo.gl/DTGgP")));
		    } else {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri
				.parse("http://goo.gl/fhecu")));
		    }
		}
	    });
	    builder.create().show();

	    break;
	}

	case R.id.menu_zoom_in: {
	    DocumentView view = getDocumentView();

	    if (view == null) break;

	    view.zoomIn();

	    break;
	}

	case R.id.menu_zoom_out: {
	    DocumentView view = getDocumentView();

	    if (view == null) break;

	    view.zoomOut();

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

	case R.id.menu_share: {
	    final Intent shareIntent = new Intent(Intent.ACTION_SEND);
	    shareIntent.putExtra(Intent.EXTRA_TEXT, "http://goo.gl/aPP9e");
	    shareIntent.setType("text/plain");
	    shareIntent.addCategory(Intent.CATEGORY_DEFAULT);

	    startActivity(shareIntent);

	    break;
	}

	case R.id.menu_rate: {
	    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://goo.gl/X8L5n")));

	    break;
	}

	case R.id.menu_about: {
	    Intent documentIntent = new Intent(serviceIntent);
	    documentIntent.setData(Uri.parse("reader://intro.odt"));

	    startService(documentIntent);

	    break;
	}
	}

	return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
	super.onActivityResult(requestCode, resultCode, data);

	if (requestCode == 42 && resultCode == RESULT_OK) {
	    Intent documentIntent = new Intent(serviceIntent);
	    documentIntent.setData(data.getData());

	    startService(documentIntent);
	}
    }
}
