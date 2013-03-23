package at.tomtasche.reader.ui.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import at.tomtasche.reader.R;

@SuppressLint("ValidFragment")
public class ProgressDialogFragment extends DialogFragment {

	public static final String FRAGMENT_TAG = "progress_dialog";

	private boolean upload;

	public ProgressDialogFragment() {
		this(false);
	}

	public ProgressDialogFragment(boolean upload) {
		this.upload = upload;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog progressDialog = new ProgressDialog(getActivity());

		int title;
		if (upload) {
			title = R.string.dialog_uploading_title;
		} else {
			title = R.string.dialog_loading_title;
		}

		progressDialog.setTitle(getString(title));
		progressDialog.setMessage(getString(R.string.dialog_loading_message));
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);

		setCancelable(false);

		return progressDialog;
	}
}
