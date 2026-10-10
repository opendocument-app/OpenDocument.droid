package app.opendocument.droid.background

import android.content.Context
import android.content.SharedPreferences

/**
 * Opens the legacy default preferences file using the application ID. Keep this name to preserve
 * settings across upgrades.
 */
object AppPreferences {

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
