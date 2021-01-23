package at.tomtasche.reader.ui.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import at.tomtasche.reader.R;

@SuppressLint("ValidFragment")
public class ProgressDialogFragment extends DialogFragment {

    public static final String FRAGMENT_TAG = "progress_dialog";

    private ProgressDialog progressDialog;
    private boolean isUpload;

    public ProgressDialogFragment() {
        super();
    }

    public ProgressDialogFragment(boolean isUpload) {
        this();

        this.isUpload = isUpload;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        progressDialog = new ProgressDialog(getActivity());

        int title;
        if (isUpload) {
            title = R.string.dialog_uploading_title;
        } else {
            title = R.string.dialog_loading_title;
        }

        progressDialog.setTitle(getString(title));

        String message = getString(R.string.dialog_generic_loading_message);
        if (isUpload) {
            message += " " + getString(R.string.dialog_uploading_message_appendix);
        }

        progressDialog.setMessage(message);
        progressDialog.setIndeterminate(true);

        // known issue that causes infinite progressdialog
        progressDialog.setCancelable(true);
        setCancelable(true);

        return progressDialog;
    }
}
