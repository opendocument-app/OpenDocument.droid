package app.opendocument.droid.background

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

/**
 * Whether the app is in night mode when the system says otherwise.
 *
 * The document follows the app rather than the system - a webview darkens a page algorithmically
 * and only while the app theme reports itself dark - so this is also the switch for reading at
 * night on a phone that stays light all day, and for keeping light a document that inverts badly.
 *
 * What it answers is handed to [AppCompatDelegate.setLocalNightMode], not to the default mode:
 * `MainActivity` is the only screen there is, and a local mode leaves whatever asks
 * `AppCompatDelegate` itself saying what it said before.
 */
object NightModeSetting {

    private const val PREF_NIGHT_MODE = "night_mode"

    /**
     * The mode the activity's delegate is put in.
     *
     * [AppCompatDelegate.MODE_NIGHT_UNSPECIFIED] is no override at all, which is not the same as
     * MODE_NIGHT_FOLLOW_SYSTEM: that one is an override too, and would talk over a default mode set
     * anywhere else.
     */
    fun mode(context: Context): Int =
        AppPreferences.of(context).getInt(PREF_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)

    /**
     * Remembers whether the app should be dark, and answers the mode that puts it there.
     *
     * Stored as no override whenever the answer wanted is the one that would be given anyway: an
     * override agreeing with the system is one the user can never be rid of again, and the app
     * would sit in night mode through a morning the phone had long left it for.
     */
    fun setNight(context: Context, night: Boolean): Int {
        val mode =
            when {
                night == isNightWithoutOverride() -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                night -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }

        AppPreferences.of(context).edit().putInt(PREF_NIGHT_MODE, mode).apply()

        return mode
    }

    /** What [context] is showing right now, the override included - so ask an activity. */
    fun isNight(context: Context): Boolean = isNight(context.resources)

    /**
     * What the app would show with the override taken away, which an activity carrying one can no
     * longer say. `Resources.getSystem()` is the device configuration and nothing else, so a
     * default mode set on top of it - the instrumented tests set one - is asked for separately.
     */
    private fun isNightWithoutOverride(): Boolean =
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> isNight(Resources.getSystem())
        }

    private fun isNight(resources: Resources): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
