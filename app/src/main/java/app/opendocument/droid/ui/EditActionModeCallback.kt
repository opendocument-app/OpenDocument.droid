package app.opendocument.droid.ui

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity

@Suppress("DEPRECATION")
class EditActionModeCallback(
    private val activity: MainActivity,
    private val documentFragment: DocumentFragment,
) : ActionMode.Callback {

    private lateinit var imm: InputMethodManager

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val statusView = TextView(activity)
        statusView.setText(R.string.action_edit_banner)
        mode.customView = statusView

        mode.menuInflater.inflate(R.menu.edit, menu)

        imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        documentFragment.reloadUri(true)

        imm.toggleSoftInputFromWindow(
            activity.window.decorView.rootView.windowToken,
            InputMethodManager.SHOW_FORCED,
            InputMethodManager.HIDE_IMPLICIT_ONLY,
        )

        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != R.id.edit_save) {
            return false
        }

        // saving from edit mode is its own thing, and the name OpenDocument.ios already
        // reports it under - menu_save is the button on the document itself
        activity.analyticsManager.report("menu_edit_save")

        documentFragment.prepareSave({ activity.requestSave() }, false)

        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        imm.toggleSoftInputFromWindow(activity.window.decorView.rootView.windowToken, 0, 0)

        documentFragment.reloadUri(false)
    }
}
