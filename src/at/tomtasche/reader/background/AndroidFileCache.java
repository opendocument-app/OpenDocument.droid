package at.tomtasche.reader.background;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import at.andiwand.odf2html.translator.File2URITranslator;
import at.andiwand.odf2html.util.DefaultFileCache;

public class AndroidFileCache extends DefaultFileCache {

	private static File cache;

	public static final File getCacheDirectory(Context context) {
		if (cache != null && testDirectory(cache)) {
			return cache;
		} else {
			File directory = context.getCacheDir();
			if (!testDirectory(directory)) {
				directory = context.getFilesDir();
				if (!testDirectory(directory)) {
					directory = new File(
							Environment.getExternalStorageDirectory(),
							".odf-reader");
					if (!testDirectory(directory)) {
						throw new IllegalStateException(
								"No writable cache available");
					}
				}
			}

			return cache = directory;
		}
	}

	private static final boolean testDirectory(File directory) {
		return directory != null && directory.canWrite() && directory.canRead();
	}

	private static final File2URITranslator URI_TRANSLATOR = new File2URITranslator() {
		@Override
		public URI translate(File file) {
			URI uri = file.toURI();

			File imageFile = new File(uri);
			String imageFileName = imageFile.getName();

			URI result = null;
			try {
				result = new URI(
				// use relative paths (important for chromecast-support)
				// "content://at.tomtasche.reader/" +
						Uri.encode(imageFileName));
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

			return result;
		}
	};

	public AndroidFileCache(Context context) {
		super(getCacheDirectory(context), URI_TRANSLATOR);
	}

	public static void cleanup(Context context) {
		File cache = getCacheDirectory(context);
		String[] files = cache.list();
		if (files == null)
			return;

		for (String s : files) {
			try {
				new File(cache, s).delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
