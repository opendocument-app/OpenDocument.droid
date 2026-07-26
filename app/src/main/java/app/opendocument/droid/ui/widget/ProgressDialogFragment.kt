@file:Suppress("DEPRECATION")

package app.opendocument.droid.ui.widget

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import app.opendocument.droid.R

/**
 * @JvmOverloads keeps the no-arg constructor the framework needs to re-create the fragment; a
 *   restored dialog reports itself as a load, same as before.
 */
@SuppressLint("ValidFragment")
class ProgressDialogFragment @JvmOverloads constructor(private val isUpload: Boolean = false) :
    DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progressDialog = ProgressDialog(activity)

        val title = if (isUpload) R.string.dialog_uploading_title else R.string.dialog_loading_title

        progressDialog.setTitle(getString(title))

        var message = getString(R.string.dialog_generic_loading_message)
        if (isUpload) {
            message += " " + getString(R.string.dialog_uploading_message_appendix)
        }

        progressDialog.setMessage(message)
        progressDialog.isIndeterminate = true

        // known issue that causes infinite progressdialog
        progressDialog.setCancelable(true)
        setCancelable(true)

        return progressDialog
    }

    companion object {
        const val FRAGMENT_TAG: String = "progress_dialog"
    }
}
