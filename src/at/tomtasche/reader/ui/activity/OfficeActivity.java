package at.tomtasche.reader.ui.activity;

import java.io.File;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.service.DocumentService;
import at.tomtasche.reader.background.service.DocumentService.DocumentBinder;

public class OfficeActivity extends FragmentActivity implements ServiceConnection {

    Intent serviceIntent;

    DocumentService service;


    @Override
    protected void onCreate(Bundle arg0) {
	super.onCreate(arg0);

	serviceIntent = new Intent(this, DocumentService.class);
	bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

	setContentView(R.layout.fragment_layout);
    }

    @Override
    protected void onStop() {
	super.onStop();

	stopService(serviceIntent);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
	this.service = ((DocumentBinder) service).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
	service = null;

	// TODO? bindService(serviceIntent, this, 0);
    }

    private void findDocument() {
	final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
	intent.setType("application/vnd.oasis.opendocument.*");
	intent.addCategory(Intent.CATEGORY_OPENABLE);

	startActivityForResult(intent, 42);
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
	    // TODO: show pagesfragment

	    break;
	}

	case R.id.menu_copy: {
	    if (Integer.parseInt(Build.VERSION.SDK) > 7) {
		// TODO: view.emulateShiftHeld();
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
		    // TODO: view.findAll(input.getText().toString());
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
				.parse("http://goo.gl/p4jH2")));
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
	    // TODO: view.zoomIn();

	    break;
	}

	case R.id.menu_zoom_out: {
	    // TODO: view.zoomOut();

	    break;
	}

	case R.id.menu_page_next: {
	    // TODO: 
	    //	    if (loader.hasNext()) {
	    //		loader.getNext();
	    //	    } else {
	    //		showToast(R.string.toast_error_no_next);
	    //	    }

	    break;
	}

	case R.id.menu_page_previous: {
	    // TODO: 
	    //	    if (loader.hasPrevious()) {
	    //		loader.getPrevious();
	    //	    } else {
	    //		showToast(R.string.toast_error_no_previous);
	    //	    }

	    break;
	}

	case R.id.menu_share: {
	    final Intent shareIntent = new Intent(Intent.ACTION_SEND);
	    shareIntent.putExtra(Intent.EXTRA_TEXT, "http://goo.gl/ZqiqW");
	    shareIntent.setType("text/plain");
	    shareIntent.addCategory(Intent.CATEGORY_DEFAULT);

	    startActivity(shareIntent);

	    break;
	}

	case R.id.menu_rate: {
	    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://goo.gl/pXKgv")));

	    break;
	}

	case R.id.menu_about: {
	    // TODO: 
	    //	    showDialog(false);
	    //
	    //	    try {
	    //		loadDocument(getAssets().open("intro.odt"));
	    //	    } catch (final IOException e) {
	    //		e.printStackTrace();
	    //
	    //		showToast(R.string.toast_error_open_file);
	    //
	    //		new Thread() {
	    //
	    //		    @Override
	    //		    public void run() {
	    //			try {
	    //			    ErrorReport.report(OfficeActivity.this, e);
	    //			} catch (final Exception e1) {
	    //			    e1.printStackTrace();
	    //			}
	    //		    };
	    //		}.start();
	    //	    }

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
    
    public DocumentService getService() {
	return service;
    }

    @Override
    protected void onDestroy() {
	cleanCache();

	super.onDestroy();
    }
}
