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

    class CatchAll(val checked: Boolean) : LandingItem() {
        override val id: String = "catch_all"
    }

    /** An explanatory line, used for the empty states of a section. */
    class Message(@param:StringRes val text: Int) : LandingItem() {
        override val id: String = "message:$text"
    }
}
