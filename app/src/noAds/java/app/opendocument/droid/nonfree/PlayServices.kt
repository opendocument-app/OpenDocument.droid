package app.opendocument.droid.nonfree

import android.app.Activity
import android.content.Context

/** Play services, in a build that links none of them and so would never call them. */
object PlayServices {

    fun isAvailable(context: Context): Boolean = false

    fun isAvailableOrOffersFix(activity: Activity, requestCode: Int): Boolean = false
}
