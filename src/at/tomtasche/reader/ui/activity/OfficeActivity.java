
package at.tomtasche.reader.ui.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.background.error.ErrorReport;
import at.tomtasche.reader.ui.OfficeInterface;
import at.tomtasche.reader.ui.widget.DocumentView;

public class OfficeActivity extends Activity implements OfficeInterface {

    // TODO: switch to ViewFlipper for pages
    // private ViewFlipper flipper;

    private ProgressDialog dialog;

    private DocumentLoader loader;

    private DocumentView view;

    // private ArrayList<DocumentView> views;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_document);

        // flipper = (ViewFlipper)findViewById(R.id.flipper);

        // views = new ArrayList<DocumentView>();
        // views.add(new DocumentView(this));
        // flipper.addView(views.get(0));

        view = new DocumentView(this);
        setContentView(view);

        loader = DocumentLoader.getThreadedLoader(this);

        if (getIntent().getData() == null) {
            final AlertDialog.Builder builder = new Builder(this);
            builder.setTitle(getString(R.string.start_dialog_title));
            builder.setMessage(getString(R.string.start_dialog_message));
            builder.setNeutralButton(getString(R.string.start_dialog_button),
                    new OnClickListener() {

                        @Override
                        public void onClick(final android.content.DialogInterface dialog,
                                final int which) {
                            findDocument();
                        }
                    });
            builder.create().show();
        } else {
            loadDocument(getIntent().getData());
        }
    }

    @Override
    public void showProgress() {
        dialog = ProgressDialog.show(OfficeActivity.this, "",
                getString(R.string.progress_dialog_message), true);
    }

    @Override
    public void hideProgress() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    @Override
    public void showDocument(final String html) {
        // views.get(loader.getPageIndex()).loadData(html);

        // if (loader.getPageCount() > 1) {
        // Toast.makeText(this, "SWIPE LEFT / RIGHT FOR NEXT PAGE",
        // Toast.LENGTH_LONG).show();
        // }

        view = new DocumentView(this);
        setContentView(view);
        view.loadData(html);

        hideProgress();
    }

    @Override
    public void showToast(final int resId) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show();
    }

    private void findDocument() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.oasis.opendocument.*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, 42);
    }

    private void loadDocument(final InputStream stream) {
        if (stream == null) {
            showToast(R.string.toast_error_access_file);

            return;
        }

        showProgress();

        // views = new ArrayList<DocumentView>();
        // views.add(new DocumentView(this));

        cleanCache();

        loader.post(new Runnable() {

            @Override
            public void run() {
                try {
                    loader.loadDocument(stream, getCacheDir());
                } catch (final Exception e) {
                    e.printStackTrace();

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            hideProgress();

                            showToast(R.string.toast_error_open_file);
                        }
                    });

                    try {
                        ErrorReport.report(OfficeActivity.this, e);
                    } catch (final Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    private void loadDocument(Uri uri) {
        if ("/./".equals(uri.toString().substring(0, 2))) {
            uri = Uri.parse(uri.toString().substring(2, uri.toString().length()));
        }

        try {
            loadDocument(getContentResolver().openInputStream(uri));
        } catch (final FileNotFoundException e) {
            e.printStackTrace();

            showToast(R.string.toast_error_find_file);
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
            case R.id.menu_copy: {
                if (Integer.parseInt(Build.VERSION.SDK) > 7) {
                    // views.get(loader.getPageIndex()).emulateShiftHeld();
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
                // views.get(loader.getPageIndex()).zoomIn();
                view.zoomIn();

                break;
            }

            case R.id.menu_zoom_out: {
                // views.get(loader.getPageIndex()).zoomOut();
                view.zoomOut();

                break;
            }

            case R.id.menu_page_next: {
                // flipper.showNext();
                if (loader.hasNext()) {
                    loader.getNext();
                } else {
                    showToast(R.string.toast_error_no_next);
                }

                break;
            }

            case R.id.menu_page_previous: {
                // flipper.showNext();
                if (loader.hasPrevious()) {
                    loader.getPrevious();
                } else {
                    showToast(R.string.toast_error_no_previous);
                }

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
                try {
                    loadDocument(getAssets().open("intro.odt"));
                } catch (final IOException e) {
                    e.printStackTrace();

                    showToast(R.string.toast_error_open_file);

                    new Thread() {

                        @Override
                        public void run() {
                            try {
                                ErrorReport.report(OfficeActivity.this, e);
                            } catch (final Exception e1) {
                                e1.printStackTrace();
                            }
                        };
                    }.start();
                }

                break;
            }
        }

        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 42 && resultCode == RESULT_OK) {
            loadDocument(data.getData());
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
    protected void onDestroy() {
        cleanCache();

        super.onDestroy();
    }
}
