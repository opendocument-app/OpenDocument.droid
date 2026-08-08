package app.opendocument.droid.ui.widget

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import app.opendocument.droid.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Built on MaterialAlertDialogBuilder rather than the framework ProgressDialog, which resolves
 * android:alertDialogTheme and so would have stayed light once the app theme became DayNight.
 *
 * @JvmOverloads keeps the no-arg constructor the fragment framework re-creates this with.
 */
@SuppressLint("ValidFragment")
class ProgressDialogFragment @JvmOverloads constructor(private val isUpload: Boolean = false) :
    DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = if (isUpload) R.string.dialog_uploading_title else R.string.dialog_loading_title

        var message = getString(R.string.dialog_generic_loading_message)
        if (isUpload) {
            message += " " + getString(R.string.dialog_uploading_message_appendix)
        }

        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        view.findViewById<TextView>(R.id.progress_message).text = message

        // known issue that causes infinite progressdialog
        setCancelable(true)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(view)
            .setCancelable(true)
            .create()
    }

    companion object {
        const val FRAGMENT_TAG: String = "progress_dialog"
    }
}
