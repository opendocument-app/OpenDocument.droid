package at.tomtasche.reader.background;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import android.content.Context;
import android.net.Uri;
import at.andiwand.odf2html.translator.File2URITranslator;
import at.andiwand.odf2html.util.DefaultFileCache;

public class AndroidFileCache extends DefaultFileCache {

	private static final File2URITranslator URI_TRANSLATOR = new File2URITranslator() {
		@Override
		public URI translate(File file) {
			URI uri = file.toURI();

			File imageFile = new File(uri);
			String imageFileName = imageFile.getName();

			URI result = null;
			try {
				result = new URI("content://at.tomtasche.reader/"
						+ Uri.encode(imageFileName));
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

			return result;
		}
	};

	public AndroidFileCache(Context context) {
		super(context.getCacheDir(), URI_TRANSLATOR);
	}

	public static void cleanup(Context context) {
		File cache = context.getCacheDir();
		if (cache.list() == null)
			return;

		for (String s : cache.list()) {
			try {
				new File(cache, s).delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
