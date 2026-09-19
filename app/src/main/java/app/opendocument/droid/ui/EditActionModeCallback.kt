package app.opendocument.droid.ui

import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R
import app.opendocument.droid.background.EditingKind
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity

/** The edit mode: the bar with save. The tools under it are `EditingTools`. */
class EditActionModeCallback(
    private val activity: MainActivity,
    private val documentFragment: DocumentFragment,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val annotating = documentFragment.editingKind == EditingKind.ANNOTATION

        val statusView = TextView(activity)
        statusView.setText(
            if (annotating) R.string.action_annotate_banner else R.string.action_edit_banner
        )
        mode.customView = statusView

        mode.menuInflater.inflate(R.menu.edit, menu)

        documentFragment.setEditing(true)

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != R.id.edit_save) {
            return false
        }

        // OpenDocument.ios' name for this; menu_save is the button on the document itself
        activity.analyticsManager.report("menu_edit_save")

        documentFragment.prepareSave({ activity.requestSave() }, false)

        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        documentFragment.setEditing(false)

        // the page keeps its edits with the mode off. not when the document is being closed: that
        // asked already
        if (documentFragment.isAdded && documentFragment.hasUnsavedEdits()) {
            activity.confirmLeavingEdits { documentFragment.discardEdits() }
        }
    }
}
