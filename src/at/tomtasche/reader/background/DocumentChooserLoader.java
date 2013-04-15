package at.tomtasche.reader.background;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;

public class DocumentChooserLoader extends AsyncTaskLoader<Map<String, String>> {

    private final FilenameFilter filter = new FilenameFilter() {

	@Override
	public boolean accept(File dir, String filename) {
	    File file = new File(dir, filename);
	    if (!file.canRead())
		return false;

	    if (file.isDirectory())
		return true;

	    // String type = getContext().getContentResolver().getType(
	    // Uri.fromFile(file));
	    // if (type == null)
	    // return false;

	    // return type.startsWith("application/vnd.oasis.opendocument.");

	    return filename.endsWith(".odt") || filename.endsWith(".ods");
	}
    };

    private Map<String, String> result;

    public DocumentChooserLoader(Context context) {
	super(context);
    }

    @Override
    public Map<String, String> loadInBackground() {
	result = new HashMap<String, String>();

	try {
	    result.putAll(RecentDocumentsUtil.getRecentDocuments(getContext()));
	} catch (IOException e) {
	    e.printStackTrace();
	}

	// TODO: use aFileChooser?
	// String state = Environment.getExternalStorageState();
	// if (Environment.MEDIA_MOUNTED.equals(state)
	// || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
	// File sdcard = Environment.getExternalStorageDirectory();
	// if (sdcard.canRead())
	// findOdfs(result, sdcard);
	// }

	return result;
    }

    @Override
    protected void onStartLoading() {
	super.onStartLoading();

	if (result != null) {
	    deliverResult(result);
	} else {
	    forceLoad();
	}
    }

    @Override
    protected void onReset() {
	super.onReset();

	onStopLoading();

	result = null;
    }

    @Override
    protected void onStopLoading() {
	super.onStopLoading();

	cancelLoad();
    }

    private void findOdfs(Map<String, String> result, File directory) {
	File[] files = directory.listFiles(filter);
	if (files == null)
	    return;

	for (File file : files) {
	    if (file.isDirectory()) {
		findOdfs(result, file);
	    } else {
		Uri uri = Uri.fromFile(file);
		String title = uri.getLastPathSegment();
		if (title == null)
		    continue;

		result.put(title, uri.toString());
	    }
	}
    }
}