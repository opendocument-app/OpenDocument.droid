package app.opendocument.droid.ui

import android.app.Activity
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    /**
     * Current snackbar, cleared on dismissal to release its activity. Dismiss when switching
     * documents.
     */
    private var current: Snackbar? = null

    /** Takes down whatever is up, if anything. Safe to call when nothing is. */
    fun dismiss(activity: Activity) {
        activity.runOnUiThread {
            current?.dismiss()
            current = null
        }
    }

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

    /** Same, where the message carries numbers and is built rather than looked up. */
    fun show(
        activity: Activity,
        message: String,
        callback: Runnable?,
        isIndefinite: Boolean,
        isError: Boolean,
    ) {
        show(
            activity,
            activity.getString(android.R.string.ok),
            message,
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

            snackbar.addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(dismissed: Snackbar?, event: Int) {
                        // only if it is still the one on show: a bar that replaced this one has
                        // already put itself there, and clearing would drop that instead
                        if (current === dismissed) {
                            current = null
                        }
                    }
                }
            )

            current = snackbar

            snackbar.show()
        }
    }
}
