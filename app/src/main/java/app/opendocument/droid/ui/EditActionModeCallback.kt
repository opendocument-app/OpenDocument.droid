package app.opendocument.droid.ui

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R
import app.opendocument.droid.background.EditingKind
import app.opendocument.droid.ui.activity.DocumentFragment
import app.opendocument.droid.ui.activity.MainActivity

/**
 * The edit mode: the bar with undo, redo and save. The tools that change the text itself are
 * `EditingTools`, in the strip under the bar.
 */
class EditActionModeCallback(
    private val activity: MainActivity,
    private val documentFragment: DocumentFragment,
) : ActionMode.Callback {

    private var annotating = false

    private var undoItem: MenuItem? = null
    private var redoItem: MenuItem? = null

    private var canUndo = false
    private var canRedo = false

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        annotating = documentFragment.editingKind == EditingKind.ANNOTATION

        // the name of the mode, not a sentence: three buttons leave a phone's bar no room for one
        mode.setTitle(if (annotating) R.string.menu_annotate else R.string.menu_edit)

        mode.menuInflater.inflate(R.menu.edit, menu)

        undoItem = menu.findItem(R.id.edit_undo)
        redoItem = menu.findItem(R.id.edit_redo)
        redoItem?.isVisible = !annotating

        setUndoState(canUndo = false, canRedo = false)

        documentFragment.setEditing(true)

        if (annotating) {
            // what the strip cannot say: a mark goes on the text that is selected
            SnackbarHelper.show(
                activity,
                R.string.action_annotate_banner,
                null,
                isIndefinite = false,
                isError = false,
            )
        }

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    /** What the page says can be taken back and put back. */
    fun setUndoState(canUndo: Boolean, canRedo: Boolean) {
        this.canUndo = canUndo
        this.canRedo = canRedo

        undoItem?.let { setUsable(it, canUndo) }
        redoItem?.let { setUsable(it, canRedo) }
    }

    /** A bar item is not dimmed by being disabled, so its icon is dimmed here. */
    private fun setUsable(item: MenuItem, usable: Boolean) {
        item.isEnabled = usable
        item.icon?.alpha = if (usable) OPAQUE else DISABLED_ALPHA
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
        undoItem = null
        redoItem = null

        documentFragment.setEditing(false)

        // the page keeps its edits with the mode off. not when the document is being closed: that
        // asked already
        if (documentFragment.isAdded && documentFragment.hasUnsavedEdits()) {
            activity.confirmLeavingEdits { documentFragment.discardEdits() }
        }
    }

    companion object {

        private const val OPAQUE = 255

        /** Material's opacity for a disabled icon, 38%. */
        private const val DISABLED_ALPHA = 97
    }
}
