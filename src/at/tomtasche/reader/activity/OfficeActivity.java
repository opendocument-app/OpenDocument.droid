
package at.tomtasche.reader.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import openoffice.IllegalMimeTypeException;
import openoffice.MimeTypeNotFoundException;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.error.ErrorReport;
import at.tomtasche.reader.odt.JOpenDocumentWrapper;

public class OfficeActivity extends Activity {

    private static final String ENCODING = "utf-8";

    private WebView documentView;

    private ProgressDialog dialog;

    private Thread thread;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        documentView = new WebView(this);

        final WebSettings settings = documentView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setLightTouchEnabled(true);
        settings.setSupportZoom(true);
        settings.setPluginsEnabled(false);
        settings.setDefaultTextEncodingName(ENCODING);

        documentView.loadData(getString(R.string.message_get_started), "text/plain", ENCODING);
        setContentView(documentView);

        if (getIntent().getData() == null) {
            final AlertDialog.Builder builder = new Builder(this);
            builder.setTitle(getString(R.string.start_dialog_title));
            builder.setMessage(getString(R.string.start_dialog_message));
            builder.setNeutralButton(getString(R.string.start_dialog_button),
                    new OnClickListener() {

                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            openDocument();
                        }
                    });
            builder.create().show();
        } else {
            loadDocument(getIntent().getData());
        }
    }

    private void openDocument() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.oasis.opendocument.*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 42);
    }

    private void loadDocument(final Uri data) {
        dialog = ProgressDialog.show(this, "", getString(R.string.progress_dialog_message), true);

        new DocumentLoader(data);
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
                    documentView.emulateShiftHeld();
                } else {
                    Toast.makeText(this, getString(R.string.toast_error_copy), Toast.LENGTH_LONG)
                            .show();
                }

                break;
            }

            case R.id.menu_open: {
                openDocument();

                break;
            }

            case R.id.menu_donate: {
                final CharSequence[] items = {
                        getString(R.string.donate_paypal), getString(R.string.donate_market),
                        getString(R.string.donate_flattr)
                };

                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.donate_choose));
                builder.setItems(items, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int item) {
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
                documentView.zoomIn();

                break;
            }

            case R.id.menu_zoom_out: {
                documentView.zoomOut();

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
                loadDocument(null);

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

    @Override
    protected void onPause() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (final String s : getCacheDir().list()) {
            try {
                new File(getCacheDir() + "/" + s).delete();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        super.onDestroy();
    }

    class DocumentLoader extends Thread {
        private Uri data;

        public DocumentLoader(final Uri data) {
            this.data = data;

            start();
        }

        @Override
        public void run() {
            try {
                InputStream stream;

                if (data == null) {
                    stream = getAssets().open("intro.odt");
                } else {
                    if ("/./".equals(data.toString().substring(0, 2))) {
                        data = Uri.parse(data.toString().substring(2, data.toString().length()));
                    }

                    stream = getContentResolver().openInputStream(data);

                    if (stream == null) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(OfficeActivity.this,
                                        getString(R.string.toast_error_access_file),
                                        Toast.LENGTH_LONG).show();
                            }
                        });

                        return;
                    }
                }

                final JOpenDocumentWrapper document = new JOpenDocumentWrapper(stream,
                        getCacheDir());

                documentView.loadData(document.getHtml(), "text/html", ENCODING);
            } catch (final MimeTypeNotFoundException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_open_file), Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (final IllegalMimeTypeException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_open_file), Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (final FileNotFoundException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_find_file), Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (final IllegalArgumentException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_illegal_file), Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (final OutOfMemoryError e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_out_of_memory), Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (final Exception e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OfficeActivity.this,
                                getString(R.string.toast_error_open_file), Toast.LENGTH_LONG)
                                .show();
                    }
                });

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            ErrorReport.report(OfficeActivity.this, e);
                        } catch (final Exception e1) {
                            e.printStackTrace();
                        }
                    };
                }.start();
            } finally {
                dialog.dismiss();
            }
        }
    }
}
