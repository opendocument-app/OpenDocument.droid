package at.tomtasche.reader.test;

import java.io.PrintWriter;
import java.io.StringWriter;

import junit.framework.Assert;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import at.tomtasche.reader.background.DocumentLoader;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.DocumentFragment;

public class MainActivityTest extends
		ActivityInstrumentationTestCase2<MainActivity> {

	private DocumentFragment documentFragment;
	private MainActivity activity;

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

	public void testStartupCondition() throws Exception {
		Assert.assertNotNull(documentFragment);
		Assert.assertNotNull(documentFragment.getDocumentView());
		Assert.assertEquals(View.VISIBLE, documentFragment.getDocumentView()
				.getVisibility());

		DocumentLoader loader = activity.loadUri(DocumentLoader.URI_INTRO);
		loader.get();

		if (loader.getLastError() != null) {
			StringWriter writer = new StringWriter();
			PrintWriter printer = new PrintWriter(writer);
			loader.getLastError().printStackTrace(printer);
			printer.close();
			writer.close();

			Assert.fail(writer.toString());
		}
	}
}
