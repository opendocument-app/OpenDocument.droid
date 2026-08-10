package app.opendocument.droid.ui.widget

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import app.opendocument.droid.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Built on MaterialAlertDialogBuilder rather than the framework ProgressDialog, which resolves
 * android:alertDialogTheme and so would have stayed light once the app theme became DayNight.
 */
class ProgressDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        view.findViewById<TextView>(R.id.progress_message).text =
            getString(R.string.dialog_generic_loading_message)

        // known issue that causes infinite progressdialog
        setCancelable(true)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_title)
            .setView(view)
            .setCancelable(true)
            .create()
    }

    companion object {
        const val FRAGMENT_TAG: String = "progress_dialog"
    }
}
