package app.opendocument.droid.ui.widget

import android.net.Uri
import androidx.annotation.StringRes

/** One row of the landing screen. */
sealed class LandingItem {

    /** The identity a row keeps across refreshes, so DiffUtil can tell a move from a change. */
    abstract val id: String

    /**
     * A section title. Takes the text rather than a string resource, because one of them is the
     * name of the folder being browsed - which is the only thing on screen that says where the list
     * is.
     */
    class Header(val title: String) : LandingItem() {
        override val id: String = "header:$title"
    }

    /**
     * [subtitle] is the second line, when there is something worth saying - a last opened time.
     *
     * [recent] says the row came from the recently opened list, which is the only thing the app may
     * forget; a document inside a granted folder is simply there. It is carried explicitly rather
     * than read off [subtitle], which an entry stored before the last opened time was recorded does
     * not have.
     */
    class Document(
        val filename: String,
        val uri: Uri,
        val subtitle: String? = null,
        val recent: Boolean = false,
    ) : LandingItem() {
        override val id: String = "document:$uri"
    }

    /**
     * [granted] says this is one of the trees the user handed us, which is the only kind the app
     * may give back. A sub directory of one is just part of the tree above it. Same distinction
     * [Document.recent] draws, and carried the same way.
     */
    class Folder(
        val name: String,
        val treeUri: Uri,
        val documentId: String,
        val granted: Boolean = false,
    ) : LandingItem() {
        override val id: String = "folder:$treeUri:$documentId"
    }

    /** A tappable row that is not a document, such as "add a folder". */
    class Action(val action: Int, @param:StringRes val label: Int, val icon: Int) : LandingItem() {
        override val id: String = "action:$action"
    }

    /** A switch under the settings header. */
    class Setting(
        val setting: Int,
        @param:StringRes val title: Int,
        @param:StringRes val body: Int,
        val checked: Boolean,
    ) : LandingItem() {
        override val id: String = "setting:$setting"
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
        const val ACTION_REMOVE_ADS: Int = 3

        const val SETTING_CATCH_ALL: Int = 1
    }
}
