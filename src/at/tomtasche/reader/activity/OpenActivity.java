
package at.tomtasche.reader.activity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

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
import at.tomtasche.reader.odt.JOpenDocument;

public class OpenActivity extends Activity {

    private static final String PACKAGE = "PACKAGE";

    private static final String ANDROID_VERSION = "ANDROID_VERSION";

    private static final String VERSION_CODE = "VERSION_CODE";

    private static final String VERSION_NAME = "VERSION_NAME";

    private static final String STACKTRACE = "STACKTRACE";

    private static final String MODEL = "MODEL";

    private static final String INFORMATION = "INFORMATION";

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
            Toast.makeText(this, "No supported app installed. Try EStrongs File Explorer",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadDocument(final Uri data) {
        dialog = ProgressDialog.show(this, "", "Gathering all the sheets of paper together...",
                true);

        thread = new Thread() {
            @Override
            public void run() {
                try {
                    InputStream stream;
                    if (data == null) {
                        stream = getAssets().open("intro.odt");
                    } else {
                        stream = getContentResolver().openInputStream(data);
                    }

                    final JOpenDocument document = new JOpenDocument(stream, getCacheDir());

                    documentView.loadData(document.getDocument().toString(), "text/html", ENCODING);
                } catch (final Exception e) {
                    e.printStackTrace();

                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                final List<NameValuePair> formparams = new ArrayList<NameValuePair>();

                                final ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
                                final PrintStream stream = new PrintStream(byteOutput);
                                e.printStackTrace(stream);
                                stream.flush();
                                stream.close();
                                byteOutput.flush();
                                final String stackTrace = byteOutput.toString("UTF-8");
                                byteOutput.close();

                                formparams.add(new BasicNameValuePair(PACKAGE, getPackageName()));
                                formparams.add(new BasicNameValuePair(STACKTRACE, stackTrace));
                                formparams.add(new BasicNameValuePair(MODEL, Build.MODEL));
                                formparams.add(new BasicNameValuePair(VERSION_CODE, Integer
                                        .toString(getPackageManager().getPackageInfo(
                                                getPackageName(), 0).versionCode)));
                                formparams.add(new BasicNameValuePair(ANDROID_VERSION,
                                        Build.VERSION.SDK));
                                formparams.add(new BasicNameValuePair(INFORMATION, "I"));
                                formparams
                                .add(new BasicNameValuePair(VERSION_NAME,
                                        getPackageManager().getPackageInfo(
                                                getPackageName(), 0).versionName));

                                final UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
                                        formparams, "UTF-8");

                                final HttpPost request = new HttpPost(
                                "https://analydroid.appspot.com/analydroid/exception");
                                request.setEntity(entity);

                                final HttpClient client = new DefaultHttpClient();
                                System.out.println(client.execute(request,
                                        new BasicResponseHandler()));
                            } catch (final Exception e1) {
                                e.printStackTrace();

                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        Toast.makeText(OpenActivity.this,
                                                "That's not what I'm looking for, sorry!",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        };
                    }.start();
                } finally {
                    dialog.dismiss();
                }
            };
        };
        thread.start();
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
}
