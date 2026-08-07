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

    // the historical at.tomtasche.reader names: the OS persists component names for "always open
    // .odt with this app", so renaming them drops that for every existing install
    private const val CATCH_ALL_COMPONENT = "at.tomtasche.reader.ui.activity.MainActivity.CATCH_ALL"
    private const val STRICT_CATCH_COMPONENT =
        "at.tomtasche.reader.ui.activity.MainActivity.STRICT_CATCH"

    /**
     * Puts the aliases back in the state the stored setting asks for. On every launch, not just the
     * landing screen: an upgrade from different alias defaults is only corrected here, and the app
     * is regularly started straight into a document by an external intent.
     */
    fun applyOnLaunch(context: Context): Boolean {
        val enabled = isEnabled(context)

        apply(context, enabled)

        return enabled
    }

    fun isEnabled(context: Context): Boolean =
        // opt-in: the default is STRICT_CATCH, so the app does not volunteer for contacts (#477)
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
     * The file android.preference.PreferenceManager used to hand out, opened by name so upgrading
     * users keep their setting - its androidx replacement needs a preference-ui library we do not
     * otherwise want.
     */
    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
