package app.opendocument.droid.nonfree

import android.app.Activity

/** Play services, in a build that links none of them and so would never call them. */
object PlayServices {

    fun isAvailableOrOffersFix(activity: Activity, requestCode: Int): Boolean = false
}
