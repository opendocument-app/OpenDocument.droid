package at.tomtasche.reader.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import junit.framework.Assert;
import android.net.Uri;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class MainActivityTest extends
		ActivityInstrumentationTestCase2<MainActivity> {

	private MainActivity activity;
	private DocumentFragment documentFragment;

	@SuppressWarnings("deprecation")
	public MainActivityTest() {
		super("at.tomtasche.reader", MainActivity.class);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		setActivityInitialTouchMode(false);

		activity = getActivity();
		documentFragment = (DocumentFragment) activity
				.getSupportFragmentManager().findFragmentByTag(
						DocumentFragment.FRAGMENT_TAG);
	}

	private void loadUri(final Uri uri) throws Throwable {
		runTestOnUiThread(new Runnable() {

			public void run() {
				try {
					DocumentLoader loader = activity.loadUri(uri);
					loader.get();

					if (loader.getLastError() != null) {
						StringWriter writer = new StringWriter();
						PrintWriter printer = new PrintWriter(writer);
						loader.getLastError().printStackTrace(printer);
						printer.close();
						writer.close();

						Assert.fail(writer.toString());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void testStartup() {
		Assert.assertNotNull(documentFragment);
		Assert.assertNotNull(documentFragment.getDocumentView());
		Assert.assertEquals(View.VISIBLE, documentFragment.getDocumentView()
				.getVisibility());
	}

	public void testIntro() throws Throwable {
		loadUri(DocumentLoader.URI_INTRO);
	}

	public void testExternalStorage() throws Throwable {
		assertTrue(Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)
				|| Environment.getExternalStorageState().equals(
						Environment.MEDIA_MOUNTED_READ_ONLY));

		File destinationFile = new File(
				Environment.getExternalStorageDirectory(), "test.odt");

		ReadableByteChannel sourceChannel = null;
		FileChannel destination = null;
		try {
			long size = activity.getAssets().openFd("intro.odt").getLength();

			sourceChannel = Channels.newChannel(activity.getAssets().open(
					"intro.odt"));
			destination = new FileOutputStream(destinationFile).getChannel();
			destination.transferFrom(sourceChannel, 0, size);
		} finally {
			if (sourceChannel != null)
				sourceChannel.close();
			if (destination != null)
				destination.close();
		}

		loadUri(Uri.fromFile(destinationFile));
	}
	
	public void testConfigurationChange() {
		// TODO
	}
}