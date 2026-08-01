package app.opendocument.droid.ui

import android.app.Activity
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    /**
     * The one that is up, so that something else can take it down.
     *
     * Most of what this shows is an error, and an error is about the document that was open when it
     * happened. Several of them are [Snackbar.LENGTH_INDEFINITE] - "this file could not be opened"
     * has to still be there when the user looks up - and an indefinite bar outlives the document it
     * is about unless someone dismisses it, which is how "could not be opened" ended up sitting
     * over a document that had opened perfectly well.
     *
     * Cleared when the bar goes, so this does not keep an activity alive through its view.
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
