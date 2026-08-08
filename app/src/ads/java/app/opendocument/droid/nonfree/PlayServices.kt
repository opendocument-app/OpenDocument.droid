package app.opendocument.droid.nonfree

import android.app.Activity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Whether the device has the play services the ad and consent sdks talk to. */
object PlayServices {

    /**
     * Whether they are there, and on a no shows google's own dialog. It reports back through
     * [Activity.onActivityResult] under [requestCode], calling `startActivityForResult` itself.
     */
    fun isAvailableOrOffersFix(activity: Activity, requestCode: Int): Boolean {
        val googleApi = GoogleApiAvailability.getInstance()

        val availability = googleApi.isGooglePlayServicesAvailable(activity)
        if (availability == ConnectionResult.SUCCESS) {
            return true
        }

        googleApi.getErrorDialog(activity, availability, requestCode)?.show()

        return false
    }
}
