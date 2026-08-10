package app.opendocument.droid.background

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * What the user asked for: a document to open, and how.
 *
 * [password] and [editable] are answered after the fact - by the password dialog and by entering
 * edit mode - and the request is re-issued, which is why they are the only mutable fields.
 */
class DocumentRequest(val uri: Uri, val persistentUri: Boolean) : Parcelable {

    /** Whether the html is rendered for editing, and the document held open to be written back. */
    var editable: Boolean = false

    var password: String? = null

    @Suppress("DEPRECATION") // the typed readParcelable overload only exists since API 33
    private constructor(
        parcel: Parcel
    ) : this(
        checkNotNull(parcel.readParcelable(Uri::class.java.classLoader)),
        ParcelUtil.readBoolean(parcel),
    ) {
        editable = ParcelUtil.readBoolean(parcel)
        password = parcel.readString()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, 0)
        ParcelUtil.writeBoolean(parcel, persistentUri)
        ParcelUtil.writeBoolean(parcel, editable)
        parcel.writeString(password)
    }

    companion object {
        // @JvmField because the framework looks CREATOR up as a static field
        @JvmField
        val CREATOR: Parcelable.Creator<DocumentRequest> =
            object : Parcelable.Creator<DocumentRequest> {
                override fun createFromParcel(parcel: Parcel): DocumentRequest =
                    DocumentRequest(parcel)

                override fun newArray(size: Int): Array<DocumentRequest?> = arrayOfNulls(size)
            }
    }
}
