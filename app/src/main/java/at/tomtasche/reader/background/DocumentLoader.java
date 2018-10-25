package at.tomtasche.reader.background;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import at.stefl.commons.lwxml.writer.LWXMLStreamWriter;
import at.stefl.commons.lwxml.writer.LWXMLWriter;
import at.stefl.commons.math.vector.Vector2i;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.document.BulkPresentationTranslator;
import at.stefl.opendocument.java.translator.document.BulkSpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.DocumentTranslatorUtil;
import at.stefl.opendocument.java.translator.document.GenericBulkDocumentTranslator;
import at.stefl.opendocument.java.translator.document.PresentationTranslator;
import at.stefl.opendocument.java.translator.document.SpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;
import at.tomtasche.reader.background.Document.Page;

public class DocumentLoader extends AsyncTaskLoader<Document> implements
		FileLoader {

	public static final Uri URI_INTRO = Uri.parse("reader://intro.odt");

	private Throwable lastError;
	private Uri uri;
	private boolean limit;
	private boolean translatable;
	private String password;
	private Document document;
	private DocumentTranslator translator;

	// support File parameter too (saves us from copying the file
	// unnecessarily)!
	// https://github.com/iPaulPro/aFileChooser/issues/4#issuecomment-16226515
	public DocumentLoader(Context context, Uri uri) {
		super(context);

		this.uri = uri;
		this.limit = true;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setLimit(boolean limit) {
		this.limit = limit;
	}

	public void setTranslatable(boolean translatable) {
		this.translatable = translatable;
	}

	@Override
	public Throwable getLastError() {
		return lastError;
	}

	@Override
	public Uri getLastUri() {
		return uri;
	}

	@Override
	public double getProgress() {
		if (translator != null) {
			return translator.getCurrentProgress();
		}

		return 0;
	}

	@Override
	protected void onStartLoading() {
		super.onStartLoading();

		if (document != null && lastError == null) {
			deliverResult(document);
		} else {
			forceLoad();
		}
	}

	@Override
	protected void onReset() {
		super.onReset();

		onStopLoading();

		document = null;
		lastError = null;
		uri = null;
		translator = null;
		password = null;
		limit = true;
		translatable = false;
	}

	@Override
	protected void onStopLoading() {
		super.onStopLoading();

		cancelLoad();
	}

	@Override
	public Document loadInBackground() {
		InputStream stream = null;

		LocatedOpenDocumentFile documentFile = null;
		try {
			// cleanup uri
			if ("/./".equals(uri.toString().substring(0, 2))) {
				uri = Uri.parse(uri.toString().substring(2,
						uri.toString().length()));
			}

			AndroidFileCache cache = new AndroidFileCache(getContext());
			// TODO: don't delete file being displayed at the moment, but
			// keep it until the new document has finished loading.
			// this must not delete document.odt!
			AndroidFileCache.cleanup(getContext());

			if (uri.equals(AndroidFileCache.getCacheFileUri())) {
				documentFile = new LocatedOpenDocumentFile(new File(
						AndroidFileCache.getCacheDirectory(getContext()),
						"document.odt"));
			} else {
				if (URI_INTRO.equals(uri)) {
					stream = getContext().getAssets().open("intro.odt");
				} else {
					stream = getContext().getContentResolver().openInputStream(
							uri);
				}

				File cachedFile = cache.create("document.odt", stream);
				documentFile = new LocatedOpenDocumentFile(cachedFile);
			}

			try {
				String filename = null;
				// https://stackoverflow.com/a/38304115/198996
				Cursor fileCursor = getContext().getContentResolver().query(uri, null, null, null, null);
				if (fileCursor != null) {
					int nameIndex = fileCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					fileCursor.moveToFirst();
					filename = fileCursor.getString(nameIndex);
					fileCursor.close();
				} else {
					filename = uri.getLastPathSegment();
				}

				RecentDocumentsUtil.addRecentDocument(getContext(),
						filename, uri);
			} catch (IOException e) {
				// not a showstopper, so just continue
				e.printStackTrace();
			}

			if (documentFile.isEncrypted()) {
				if (password == null)
					throw new EncryptedDocumentException();

				documentFile.setPassword(password);
				if (!documentFile.isPasswordValid())
					throw new EncryptedDocumentException();
			}

			OpenDocument openDocument = documentFile.getAsDocument();
			document = new Document(openDocument);

			TranslationSettings settings = new TranslationSettings();
			settings.setCache(cache);
			settings.setBackTranslateable(translatable);
			settings.setImageStoreMode(ImageStoreMode.CACHE);
			if (limit) {
				settings.setMaxTableDimension(new Vector2i(5000, 1000));
				settings.setMaxRowRepetition(100);
			}

			// https://github.com/andiwand/OpenDocument.java/blob/7f13222f77fabd62ee6a9d52cd6ed3e512532a9b/src/at/stefl/opendocument/java/translator/document/DocumentTranslatorUtil.java#L131
			if (!settings.isSplitPages() || (openDocument instanceof OpenDocumentText)) {
				if (openDocument instanceof OpenDocumentText) {
					translator = new TextTranslator();
				} else if (openDocument instanceof OpenDocumentSpreadsheet) {
					translator = new SpreadsheetTranslator();
				} else if (openDocument instanceof OpenDocumentPresentation) {
					translator = new PresentationTranslator();
				} else {
					throw new IllegalStateException("unsupported document");
				}
			} else {
				if (openDocument instanceof OpenDocumentSpreadsheet) {
					translator = new BulkSpreadsheetTranslator();
				} else if (openDocument instanceof OpenDocumentPresentation) {
					translator = new BulkPresentationTranslator();
				} else {
					throw new IllegalStateException("unsupported document");
				}
			}

			DocumentTranslatorUtil.Output output = DocumentTranslatorUtil.provideOutput(
					openDocument, settings, "temp", ".html");
			try {
				translator.translate(openDocument, output.getWriter(), settings);
			} finally {
				output.getWriter().close();
			}

			for (int i = 0; i < output.getNames().size(); i++) {
				File htmlFile = cache.getFile(output.getNames().get(i));

				document.addPage(new Page(output.getTitles().get(i),
						htmlFile.toURI(), i));
			}

			document.setLimited(translator.isCurrentOutputTruncated());

			return document;
		} catch (Throwable e) {
			e.printStackTrace();

			lastError = e;
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException e) {
			}

			try {
				if (documentFile != null)
					documentFile.close();
			} catch (IOException e) {
			}
		}

		return null;
	}

	@SuppressWarnings("serial")
	public static class EncryptedDocumentException extends Exception {
	}
}
