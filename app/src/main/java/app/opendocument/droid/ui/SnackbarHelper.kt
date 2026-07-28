package app.opendocument.droid.ui

import android.app.Activity
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
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

    /** Same, with a button that says something other than "OK" - "undo", typically. */
    fun show(
        activity: Activity,
        resId: Int,
        buttonResId: Int,
        callback: Runnable?,
        isIndefinite: Boolean,
        isError: Boolean,
    ) {
        show(
            activity,
            activity.getString(buttonResId),
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
            if (callback != null) {
                snackbar.setAction(buttonText) {
                    callback.run()

                    snackbar.dismiss()
                }
            }

            if (isError) {
                val context = snackbar.view.context
                snackbar.view.setBackgroundColor(
                    MaterialColors.getColor(context, R.attr.colorErrorContainer, 0)
                )
                snackbar.setTextColor(
                    MaterialColors.getColor(context, R.attr.colorOnErrorContainer, 0)
                )
                snackbar.setActionTextColor(
                    MaterialColors.getColor(context, R.attr.colorOnErrorContainer, 0)
                )
            }

            snackbar.view.setOnClickListener { snackbar.dismiss() }

            snackbar.show()
        }
    }
}
