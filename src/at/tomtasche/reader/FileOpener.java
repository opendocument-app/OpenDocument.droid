package at.tomtasche.reader;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

public class FileOpener extends Activity {
	public static final String ENCODING = "utf-8";
	public static final String NEW_LINE = "<br>";

	private WebView textView;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (getIntent().getData().toString() == null) {
			fail();
			return;
		}

		textView = (WebView) findViewById(R.id.textView);
		final WebSettings settings = textView.getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setSupportZoom(true);
		settings.setPluginsEnabled(false);
		settings.setDefaultTextEncodingName(ENCODING);

		final ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
		InputStream fileStream = null;

		try {
			fileStream = getContentResolver().openInputStream(getIntent().getData());
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		}

		if (fileStream != null) {
			try {
				Parser.parse(fileStream, resultStream);

				if (resultStream.size() < 1) {
					fail();
					return;
				}

				try {
					textView.loadData("<html>" + resultStream.toString(ENCODING) + "</html>", "text/html", ENCODING);
				} catch (final UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			} catch (final Exception e) {
				e.printStackTrace();

				final Intent sendIntent = new Intent(Intent.ACTION_SEND);
				try {
					final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					final PrintWriter printer = new PrintWriter(byteStream);
					e.printStackTrace(printer);
					printer.close();
					byteStream.close();
					sendIntent.putExtra(Intent.EXTRA_SUBJECT, "OpenOffice Document Reader - Problem");
					sendIntent.putExtra(Intent.EXTRA_TEXT, byteStream.toString());
					sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"tomtasche+reader@gmail.com"});
					sendIntent.setType("message/rfc822");
					startActivity(sendIntent);
				} catch (final IOException e1) {}

				return;
			}
		} else {
			fail();
		}
	}

	private void fail() {
		Toast.makeText(this, "Couldn't load .odt-file. Sorry!", Toast.LENGTH_LONG).show();
	}
}