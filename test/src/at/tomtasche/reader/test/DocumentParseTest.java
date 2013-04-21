package at.tomtasche.reader.test;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;
import at.tomtasche.reader.background.DocumentLoader.EncryptedDocumentException;
import at.tomtasche.reader.background.LoadingListener;
import at.tomtasche.reader.ui.activity.MainActivity;

public class DocumentParseTest extends
		ActivityInstrumentationTestCase2<MainActivity> implements
		LoadingListener {

	private Map<Uri, Boolean> passwordTested;
	private MainActivity activity;
	private File textDirectory;
	private File spreadsheetDirectory;
	private File presentationDirectory;
	private List<Uri> queue;

	private Object waitObject = new Object();

	public DocumentParseTest() {
		super("at.tomtasche.reader", MainActivity.class);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		queue = new LinkedList<Uri>();
		passwordTested = new HashMap<Uri, Boolean>();

		activity = getActivity();
		activity.setLoadingListener(this);

		File directory = Environment.getExternalStorageDirectory();

		textDirectory = new File(directory, "text");
		spreadsheetDirectory = new File(directory, "spreadsheet");
		presentationDirectory = new File(directory, "presentation");
	}

	public void testPreconditions() {
		Assert.assertEquals(Environment.getExternalStorageState(),
				Environment.MEDIA_MOUNTED);

		Assert.assertTrue(textDirectory.exists());
		Assert.assertTrue(spreadsheetDirectory.exists());
		Assert.assertTrue(presentationDirectory.exists());
	}

	public void testText() {
		parseDocuments(textDirectory);
	}

	public void testSpreadsheet() {
		parseDocuments(spreadsheetDirectory);
	}

	public void testPresentation() {
		parseDocuments(presentationDirectory);
	}

	private void parseDocuments(File directory) {
		for (File file : directory.listFiles()) {
			queue.add(Uri.fromFile(file));
		}

		activity.loadUri(queue.get(0));

		synchronized (waitObject) {
			try {
				waitObject.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onError(Throwable error, Uri uri) {
		if (error instanceof EncryptedDocumentException) {
			if (!passwordTested.get(uri)) {
				activity.loadUri(uri, getPassword(uri.getLastPathSegment()));

				passwordTested.put(uri, true);
			} else {
				throw new RuntimeException(
						"correct password has already been tested", error);
			}
		} else {
			throw new RuntimeException("error parsing: "
					+ uri.getLastPathSegment(), error);
		}
	}

	@Override
	public void onSuccess(Uri uri) {
		queue.remove(uri);

		if (queue.isEmpty()) {
			synchronized (waitObject) {
				waitObject.notifyAll();
			}
		} else {
			// dirty hack because committing isn't allowed right after
			// onLoadFinished:
			// "java.lang.IllegalStateException: Can not perform this action inside of onLoadFinished"
			new Handler().post(new Runnable() {

				@Override
				public void run() {
					activity.loadUri(queue.get(0));
				}
			});
		}
	}

	private String getPassword(String name) {
		String[] split = name.split("$");
		return (split.length == 3) ? split[1] : null;
	}
}
