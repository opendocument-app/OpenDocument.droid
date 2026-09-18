package app.opendocument.droid.ui

import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R
import app.opendocument.droid.background.EditingKind
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity

/**
 * The edit mode: the bar on top with undo, redo and save, and under it the strip of tools the
 * document has - see `EditingTools`. A pdf is marked up rather than edited, and has no redo.
 */
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

        documentFragment.editStateListener = { mode.invalidate() }
        documentFragment.setEditing(true)

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.findItem(R.id.edit_redo).isVisible =
            documentFragment.editingKind != EditingKind.ANNOTATION

        setEnabled(menu.findItem(R.id.edit_undo), documentFragment.canUndo)
        setEnabled(menu.findItem(R.id.edit_redo), documentFragment.canRedo)

        return true
    }

    /** A disabled action item keeps its icon as it was, so it is dimmed here. */
    private fun setEnabled(item: MenuItem, enabled: Boolean) {
        item.isEnabled = enabled
        item.icon = item.icon?.mutate()?.also { it.alpha = if (enabled) 255 else DISABLED_ALPHA }
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.edit_undo -> {
                activity.analyticsManager.report("menu_edit_undo")

                documentFragment.undo()
            }

            R.id.edit_redo -> {
                activity.analyticsManager.report("menu_edit_redo")

                documentFragment.redo()
            }

            R.id.edit_save -> {
                // OpenDocument.ios' name for this; menu_save is the button on the document itself
                activity.analyticsManager.report("menu_edit_save")

                documentFragment.prepareSave({ activity.requestSave() }, false)
            }

            else -> return false
        }

        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        documentFragment.editStateListener = null
        documentFragment.setEditing(false)

        // the page keeps its edits with the mode off, so they are asked about here rather than
        // thrown away. not when the document is being closed: that asked already, and the fragment
        // is gone by the time the mode is finished
        if (documentFragment.isAdded && documentFragment.hasUnsavedEdits()) {
            activity.confirmLeavingEdits { documentFragment.discardEdits() }
        }
    }

    private companion object {
        /** Material's opacity for a disabled icon, 38%. */
        const val DISABLED_ALPHA = 97
    }
}
