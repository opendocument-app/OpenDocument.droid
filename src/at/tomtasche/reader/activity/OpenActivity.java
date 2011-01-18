
package at.tomtasche.reader.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import at.tomtasche.reader.R;
import at.tomtasche.reader.error.ErrorReport;
import at.tomtasche.reader.odt.JOpenDocument;

public class OpenActivity extends Activity {

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

        documentView.loadData("Get started using your phone's menu button!", "text/plain", "utf-8");
        setContentView(documentView);

        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle("Welcome to an Open World!");
        builder.setMessage("I suspect you came here to open your beloved OpenOffice / LibreOffice documents, so let's just skip the boring part and start reading!\n\nIf you want to read more about this project visit http://reader.tomtasche.at/ or click \"About\" in the menu.\n\nSincerely,\nYour developers,\nAndi, David and Tom");
        builder.setNeutralButton("Cool!", new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                openDocument();
            }
        });
        builder.create().show();
    }

    private void openDocument() {
        try {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.oasis.opendocument.*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 42);
        } catch (final Exception e) {
            e.printStackTrace();
            
            Toast.makeText(this, "No supported app installed. Try EStrongs File Explorer",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadDocument(final Uri data) {
        dialog = ProgressDialog.show(this, "", "Gathering all the sheets of paper together...",
                true);

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
                try {
                    documentView.emulateShiftHeld();
                } catch (final Exception e) {
                    Toast.makeText(this, "Not possible on Android versions older than 2.0",
                            Toast.LENGTH_LONG).show();
                }

                break;
            }

            case R.id.menu_open: {
                openDocument();

                break;
            }

            case R.id.menu_donate: {
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://www.appbrain.com/app/saymyname-donate/org.mailboxer.saymyname.donate?install")));

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
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://www.appbrain.com/app/openoffice-document-reader/at.tomtasche.reader?install")));

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

        public DocumentLoader(Uri data) {
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
                                Toast.makeText(OpenActivity.this, "Couldn't access file", Toast.LENGTH_LONG).show();
                            }
                        });
                        
                        return;
                    }
                }

                final JOpenDocument document = new JOpenDocument(stream, getCacheDir());

                documentView.loadData(document.getDocument().toString(), "text/html", ENCODING);
            } catch (FileNotFoundException e) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OpenActivity.this, "Couldn't find file", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IllegalArgumentException e) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(OpenActivity.this, "This doesn't seem to be a supported OpenOffice Document", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (final Exception e) {
                e.printStackTrace();

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            ErrorReport.report(OpenActivity.this, e);
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
