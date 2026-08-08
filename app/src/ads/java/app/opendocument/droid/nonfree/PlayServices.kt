package app.opendocument.droid.nonfree

import android.app.Activity
import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Whether the device has the play services everything else in this package talks to. */
object PlayServices {

    fun isAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS

    /**
     * As [isAvailable], and on a no shows google's own dialog. It reports back through
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
