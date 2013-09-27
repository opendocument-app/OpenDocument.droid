package at.tomtasche.reader.ui;

import java.io.File;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ValueCallback;
import android.widget.TextView;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.widget.PageView;

public class EditActionModeCallback implements ActionMode.Callback {

	private Context context;
	private PageView pageView;
	private TextView statusView;

	public EditActionModeCallback(Context context, PageView pageView) {
		this.context = context;
		this.pageView = pageView;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		statusView = new TextView(context);
		statusView.setText("Getting your document ready for some changes...");
		mode.setCustomView(statusView);

		mode.getMenuInflater().inflate(R.menu.edit, menu);

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.edit_help: {
			context.startActivity(new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://plus.google.com/communities/113494011673882132018")));

			break;
		}

		case R.id.edit_save: {
			pageView.saveWebArchive("webarchive", false,
					new ValueCallback<String>() {

						@Override
						public void onReceiveValue(String value) {
							File archive = new File(context.getFilesDir(),
									value);

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
