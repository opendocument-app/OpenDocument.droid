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
    private boolean hasProgress;

    public ProgressDialogFragment() {
        this(false);
    }

    public ProgressDialogFragment(boolean hasProgress) {
        this.hasProgress = hasProgress;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        progressDialog = new ProgressDialog(getActivity());

        int title;
        if (hasProgress) {
            title = R.string.dialog_uploading_title;
        } else {
            title = R.string.dialog_loading_title;
        }

        progressDialog.setTitle(getString(title));
        progressDialog.setMessage(getString(R.string.dialog_loading_message));
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(hasProgress);
        if (!hasProgress) {
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setProgress(0);
        }

        setCancelable(false);

        return progressDialog;
    }

    public void setProgress(double progress) {
        if (progressDialog != null)
            progressDialog.setProgress(((int) (progress * 100)));
    }

    // another dirty hack for a nullpointerexception thrown sometimes on dismiss()
    public boolean isNotNull() {
        return progressDialog != null;
    }
}
