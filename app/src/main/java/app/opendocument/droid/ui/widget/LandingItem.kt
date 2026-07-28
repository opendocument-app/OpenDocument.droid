package app.opendocument.droid.ui.widget

import android.net.Uri
import androidx.annotation.StringRes

/** One row of the landing screen. */
sealed class LandingItem {

    /** The identity a row keeps across refreshes, so DiffUtil can tell a move from a change. */
    abstract val id: String

    class Header(@param:StringRes val title: Int) : LandingItem() {
        override val id: String = "header:$title"
    }

    class Document(val filename: String, val uri: Uri) : LandingItem() {
        override val id: String = "document:$uri"
    }

    class Folder(val name: String, val treeUri: Uri, val documentId: String) : LandingItem() {
        override val id: String = "folder:$treeUri:$documentId"
    }

    /** A tappable row that is not a document, such as "add a folder". */
    class Action(val action: Int, @param:StringRes val label: Int, val icon: Int) : LandingItem() {
        override val id: String = "action:$action"
    }

    class CatchAll(val checked: Boolean) : LandingItem() {
        override val id: String = "catch_all"
    }

    /** An explanatory line, used for the empty states of a section. */
    class Message(@param:StringRes val text: Int) : LandingItem() {
        override val id: String = "message:$text"
    }

    /**
     * What the screen says while it has nothing to list, offering both ways to fill it.
     *
     * A row like any other, so that the settings underneath stay on screen - shown instead of the
     * list, it would take the catch-all switch with it on a fresh install.
     */
    class Empty : LandingItem() {
        override val id: String = "empty"
    }

    companion object {
        const val ACTION_ADD_FOLDER: Int = 1
        const val ACTION_UP: Int = 2
    }
}
