package app.opendocument.droid.background

import android.content.Context
import android.content.SharedPreferences

/**
 * The one preferences file the app keeps its settings in.
 *
 * The file android.preference.PreferenceManager used to hand out. That class is deprecated and its
 * androidx replacement lives in a whole preference-ui library we do not otherwise need, so the
 * default file is opened by name instead - keeping the settings of users who upgrade.
 *
 * Its name follows getPackageName(), which is the applicationId rather than the namespace, and has
 * to stay that way: a renamed file is an empty file for every existing install.
 */
object AppPreferences {

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
