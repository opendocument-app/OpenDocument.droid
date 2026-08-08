package app.opendocument.droid.ui.widget

import android.net.Uri
import androidx.annotation.StringRes

/** One row of the landing screen. */
sealed class LandingItem {

    /** The identity a row keeps across refreshes, so DiffUtil can tell a move from a change. */
    abstract val id: String

    /**
     * A section title.
     *
     * [section] is what folding it reports, and null for a section that does not fold - the
     * recently opened documents are the screen, and hiding them behind a chevron would leave a
     * screen of chevrons. [expanded] only means anything when [section] does not.
     */
    class Header(
        @param:StringRes val title: Int,
        val section: Int? = null,
        val expanded: Boolean = true,
    ) : LandingItem() {
        override val id: String = "header:$title"
    }

    /** The Open button, at the top of the list whatever the list holds. */
    class Open : LandingItem() {
        override val id: String = "open"
    }

    /**
     * A recently opened document. [subtitle] is the time it was last opened, drawn at the end of
     * the same line, or null for an entry stored before that was recorded.
     */
    class Document(val filename: String, val uri: Uri, val subtitle: String? = null) :
        LandingItem() {
        override val id: String = "document:$uri"
    }

    /** A tappable row that is not a document, such as the ad removal. */
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

    /** What the app is for: the body of the intro section, when it is unfolded. */
    class Intro : LandingItem() {
        override val id: String = "intro"
    }

    companion object {
        const val ACTION_REMOVE_ADS: Int = 1
        const val ACTION_PRIVACY_OPTIONS: Int = 2

        const val SETTING_CATCH_ALL: Int = 1
        const val SETTING_PAGINATION: Int = 2

        const val SECTION_INTRO: Int = 1
        const val SECTION_SETTINGS: Int = 2
    }
}
