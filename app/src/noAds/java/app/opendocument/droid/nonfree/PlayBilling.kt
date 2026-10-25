package app.opendocument.droid.nonfree

import android.app.Activity
import android.content.Context

/** Play billing, in a build that sells nothing and has no ads to remove. */
class PlayBilling(
    context: Context,
    crashManager: CrashManager,
    onOwned: (entitlement: Entitlement, purchased: Boolean) -> Unit,
) {

    fun queryOwned(purchased: Boolean = false) {}

    fun queryPassPrice(callback: (String?) -> Unit) {
        callback(null)
    }

    fun buyPass(activity: Activity) {}

    fun end() {}
}
