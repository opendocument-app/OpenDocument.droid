package at.tomtasche.reader.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.Retranslator;
import at.tomtasche.reader.R;
import at.tomtasche.reader.background.AndroidFileCache;
import at.tomtasche.reader.background.ReportUtil;
import at.tomtasche.reader.ui.activity.MainActivity;
import at.tomtasche.reader.ui.widget.PageView;

public class EditActionModeCallback implements ActionMode.Callback {

	private MainActivity activity;
	private PageView pageView;
	private TextView statusView;
	private OpenDocument document;

	public EditActionModeCallback(MainActivity activity, PageView pageView,
			OpenDocument document) {
		this.activity = activity;
		this.pageView = pageView;
		this.document = document;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		statusView = new TextView(activity);
		statusView.setText(R.string.action_edit_banner);
		mode.setCustomView(statusView);

		mode.getMenuInflater().inflate(R.menu.edit, menu);

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		// reload document with translation enabled
		activity.loadUri(AndroidFileCache.getCacheFileUri(), null, true, true);

		return true;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.edit_help: {
			activity.startActivity(new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://plus.google.com/communities/113494011673882132018")));

			break;
		}

		case R.id.edit_save: {
			activity.showInterstitial();

			final File htmlFile = new File(
					AndroidFileCache.getCacheDirectory(activity),
					"content.html");
			pageView.requestHtml(htmlFile, new Runnable() {

				@Override
				public void run() {
					Uri fileUri = null;
					FileInputStream htmlStream = null;
					FileOutputStream modifiedStream = null;
					LocatedOpenDocumentFile documentFile = null;
					try {
						htmlStream = new FileInputStream(htmlFile);

						// TODO: ugly and risky cast
						documentFile = new LocatedOpenDocumentFile(
								((LocatedOpenDocumentFile) document
										.getDocumentFile()).getFile());

						String extension = "unknown";
						OpenDocument openDocument = documentFile
								.getAsDocument();
						if (openDocument instanceof OpenDocumentText) {
							extension = "odt";
						} else if (openDocument instanceof OpenDocumentSpreadsheet) {
							extension = "ods";
						} else if (openDocument instanceof OpenDocumentPresentation) {
							extension = "odp";
						}

						File modifiedFile = new File(Environment
								.getExternalStorageDirectory(),
								"modified-by-opendocument-reader." + extension);
						modifiedStream = new FileOutputStream(modifiedFile);

						Retranslator.retranslate(openDocument, htmlStream,
								modifiedStream);

						modifiedStream.close();

						fileUri = Uri.parse("file://"
								+ modifiedFile.getAbsolutePath());

						activity.loadUri(fileUri);

						activity.showSaveCroutonLater(modifiedFile, fileUri);
					} catch (final Throwable e) {
						e.printStackTrace();

						final Uri cacheUri = AndroidFileCache.getCacheFileUri();
						final Uri htmlUri = AndroidFileCache
								.getHtmlCacheFileUri();

						activity.onError(e, cacheUri);

						activity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								ReportUtil.submitFile(activity, e, cacheUri,
										cacheUri, htmlUri, "Editing failed");
							}
						});
					} finally {
						if (documentFile != null) {
							try {
								documentFile.close();
							} catch (IOException e) {
							}
						}

						if (htmlStream != null) {
							try {
								htmlStream.close();
							} catch (IOException e) {
							}
						}

						if (modifiedStream != null) {
							try {
								modifiedStream.close();
							} catch (IOException e) {
							}
						}
					}
				}
			});

			break;
		}

		default:
			return false;
		}

		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
	}
}
