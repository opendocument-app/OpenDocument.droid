
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
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebSettings.ZoomDensity;
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

        dialog = ProgressDialog.show(this, "", "Gathering all the sheets of paper together...",
                true);

        documentView = new WebView(this);
        final WebSettings settings = documentView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setLightTouchEnabled(true);
        settings.setSupportZoom(true);
        settings.setPluginsEnabled(false);
        settings.setDefaultTextEncodingName(ENCODING);
        settings.setDefaultZoom(ZoomDensity.FAR);

        thread = new Thread() {
            @Override
            public void run() {
                try {
                    InputStream stream;
                    if (getIntent().getData() == null) {
                        stream = getResources().openRawResource(R.raw.promo);
                    } else {
                        stream = getContentResolver().openInputStream(getIntent().getData());
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
                                        "http://analydroid.appspot.com/analydroid/exception");
                                request.setEntity(entity);

                                final HttpClient client = new DefaultHttpClient();
                                System.out.println(client.execute(request,
                                        new BasicResponseHandler()));
                            } catch (final Exception e1) {
                            }
                        };
                    }.start();
                    return;
                } finally {
                    dialog.dismiss();
                }
            };
        };
        thread.start();

        setContentView(documentView);
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
                documentView.emulateShiftHeld();
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
                shareIntent
                        .putExtra(Intent.EXTRA_TEXT,
                                "http://www.appbrain.com/app/openoffice-document-reader/at.tomtasche.reader");
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
        }

        return super.onMenuItemSelected(featureId, item);
    }

    private void fail() {
        Toast.makeText(this, "Couldn't load .odt-file. Sorry!", Toast.LENGTH_LONG).show();
    }
}
