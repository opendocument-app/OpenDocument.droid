package app.opendocument.droid.background

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Whether the app offers itself for every file type, or only for the document types it actually
 * supports.
 *
 * Backed by the two activity aliases in the manifest: exactly one of them is enabled at a time, and
 * which one decides how broad the intent filter the system sees is.
 */
object CatchAllSetting {

    private const val PREF_CATCH_ALL_ENABLED = "catch_all_enabled"

    // these keep the historical at.tomtasche.reader names on purpose: the component name is
    // what the OS persists when a user picks "always open .odt with this app", and what
    // setComponentEnabledSetting stores the toggle against. renaming them would drop those
    // defaults for every existing install, so the manifest declares the aliases under the old
    // names too.
    private const val CATCH_ALL_COMPONENT = "at.tomtasche.reader.ui.activity.MainActivity.CATCH_ALL"
    private const val STRICT_CATCH_COMPONENT =
        "at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH"

    /**
     * Puts the aliases back in the state the stored setting asks for.
     *
     * Has to run on every launch, not just when the landing screen is shown: users upgrading from a
     * version that shipped different alias defaults are only corrected here, and the app is
     * regularly started straight into a document by an external intent.
     */
    fun applyOnLaunch(context: Context): Boolean {
        val enabled = isEnabled(context)

        apply(context, enabled)

        return enabled
    }

    fun isEnabled(context: Context): Boolean =
        // catch-all is only active if the user explicitly opted in. new installs and existing
        // users who never touched the switch default to STRICT_CATCH, so we no longer volunteer
        // to open unrelated file types like contacts (issue #477).
        preferences(context).getBoolean(PREF_CATCH_ALL_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(PREF_CATCH_ALL_ENABLED, enabled).apply()

        apply(context, enabled)
    }

    private fun apply(context: Context, enabled: Boolean) {
        toggleComponent(context, CATCH_ALL_COMPONENT, enabled)
        toggleComponent(context, STRICT_CATCH_COMPONENT, !enabled)
    }

    private fun toggleComponent(context: Context, className: String, enabled: Boolean) {
        val newState =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, className),
            newState,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * The preferences android.preference.PreferenceManager used to hand out. That class is
     * deprecated and its androidx replacement lives in a whole preference-ui library we do not
     * otherwise need, so the default file is opened by name instead - keeping the existing
     * catch-all setting of users who upgrade.
     */
    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
