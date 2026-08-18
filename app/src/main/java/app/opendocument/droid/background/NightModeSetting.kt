package app.opendocument.droid.background

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

/**
 * Whether the app is in night mode when the system says otherwise, which is also the switch for
 * reading at night on a phone that stays light all day: a webview darkens a page only while the app
 * theme reports itself dark.
 *
 * Handed to [AppCompatDelegate.setLocalNightMode] rather than the default mode - `MainActivity` is
 * the only screen there is, and a local mode leaves the default where anything else set it.
 */
object NightModeSetting {

    private const val PREF_NIGHT_MODE = "night_mode"

    /**
     * The mode the activity's delegate is put in.
     *
     * [AppCompatDelegate.MODE_NIGHT_UNSPECIFIED] is no override at all, unlike
     * MODE_NIGHT_FOLLOW_SYSTEM, which is one and would talk over a default mode set elsewhere.
     */
    fun mode(context: Context): Int =
        AppPreferences.of(context).getInt(PREF_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)

    /**
     * Remembers whether the app should be dark, and answers the mode that puts it there.
     *
     * Stored as no override whenever it agrees with the system: one that does can never be got rid
     * of again, and the app would sit in night mode through a morning the phone had long left.
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
     * What the app would show without the override, which an activity carrying one cannot say.
     * `Resources.getSystem()` is the device configuration alone, so a default mode set on top of it
     * - the instrumented tests set one - is asked for separately.
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
