package app.opendocument.droid.ui

import android.app.Activity
import android.view.View
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    fun show(
        activity: Activity,
        resId: Int,
        callback: Runnable?,
        isIndefinite: Boolean,
        isError: Boolean,
    ) {
        show(
            activity,
            activity.getString(android.R.string.ok),
            activity.getString(resId),
            callback,
            isIndefinite,
            isError,
        )
    }

    private fun show(
        activity: Activity,
        buttonText: String,
        message: String,
        callback: Runnable?,
        isIndefinite: Boolean,
        isError: Boolean,
    ) {
        activity.runOnUiThread {
            val duration = if (isIndefinite) Snackbar.LENGTH_INDEFINITE else 20000

            val snackbar =
                Snackbar.make(
                    activity.findViewById<View>(android.R.id.content),
                    message,
                    duration,
                )

            // material stops at two lines, which toast_error_save_failed already fills in
            // english and overflows once translated
            snackbar.setTextMaxLines(3)

            if (callback != null) {
                snackbar.setAction(buttonText) {
                    callback.run()

                    snackbar.dismiss()
                }
            }

            if (isError) {
                snackbar.view.setBackgroundColor(0xffff4444.toInt())
            }

            snackbar.view.setOnClickListener { snackbar.dismiss() }

            snackbar.show()
        }
    }
}
