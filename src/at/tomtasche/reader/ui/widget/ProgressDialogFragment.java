package at.tomtasche.reader.ui.widget;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import at.tomtasche.reader.R;

public class ProgressDialogFragment extends DialogFragment {

	public static final String FRAGMENT_TAG = "progress_dialog";

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ProgressDialog progressDialog = new ProgressDialog(getActivity());
		progressDialog.setTitle(getString(R.string.dialog_loading_title));
		progressDialog.setMessage(getString(R.string.dialog_loading_message));
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);

		setCancelable(false);

		return progressDialog;
	}
}
