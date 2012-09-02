package at.tomtasche.reader.background;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import android.content.Context;
import android.net.Uri;
import at.andiwand.odf2html.translator.File2URITranslator;
import at.andiwand.odf2html.translator.FileCache;

public class AndroidFileCache extends FileCache {

    private static File2URITranslator DEFAULT_INSTANCE = new File2URITranslator() {
	@Override
	public URI translate(File file) {
	    URI uri = file.toURI();

	    File imageFile = new File(uri);
	    String imageFileName = imageFile.getName();

	    URI result = null;

	    try {
		result = new URI("content://at.tomtasche.reader/" + Uri.encode(imageFileName));
	    } catch (URISyntaxException e) {
		e.printStackTrace();
	    }

	    return result;
	}
    };

    public AndroidFileCache(Context context) {
	super(context.getCacheDir(), DEFAULT_INSTANCE, false);
    }
}
