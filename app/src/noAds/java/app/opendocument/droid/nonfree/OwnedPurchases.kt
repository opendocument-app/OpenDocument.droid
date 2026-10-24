package app.opendocument.droid.nonfree

import android.content.Context

/** Purchases, in a build that sells none and has no ads to remove. */
object OwnedPurchases {

    fun query(context: Context, crashManager: CrashManager, onOwned: () -> Unit) {}
}
