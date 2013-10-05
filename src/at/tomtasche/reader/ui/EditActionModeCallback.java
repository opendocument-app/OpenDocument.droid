package at.tomtasche.reader.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import at.stefl.opendocument.java.odf.LocatedOpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.translator.Retranslator;
import at.tomtasche.reader.R;
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
		statusView.setText(R.string.edit_banner);
		mode.setCustomView(statusView);

		mode.getMenuInflater().inflate(R.menu.edit, menu);

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		// reload document with translation enabled
		activity.loadUri(activity.getCacheFileUri(), null, true, true);

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
			// TODO: use getCacheDir() in release-build
			final File htmlFile = new File(activity.getExternalCacheDir(),
					"content.html");
			pageView.requestHtml(htmlFile, new Runnable() {

				@Override
				public void run() {
					FileInputStream htmlStream = null;
					FileOutputStream modifiedStream = null;
					LocatedOpenDocumentFile documentFile = null;
					try {
						htmlStream = new FileInputStream(htmlFile);

						File modifiedFile = new File(activity
								.getExternalCacheDir(), "modified.odt");
						modifiedStream = new FileOutputStream(modifiedFile);

						// TODO: ugly and risky cast
						documentFile = new LocatedOpenDocumentFile(
								((LocatedOpenDocumentFile) document
										.getDocumentFile()).getFile());

						Retranslator.retranslate(documentFile.getAsDocument(),
								htmlStream, modifiedStream);

						modifiedStream.close();

						activity.loadUri(Uri.parse("file://"
								+ modifiedFile.getAbsolutePath()));
					} catch (IOException e) {
						e.printStackTrace();
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

						if (htmlFile != null) {
							htmlFile.delete();
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
