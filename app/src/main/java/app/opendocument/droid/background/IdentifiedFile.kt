package app.opendocument.droid.background

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * The working copy of a document and what it turned out to be - see [FileIdentifier].
 *
 * Only ever made from a file that was cached whole, so holding one *is* the answer to whether the
 * document could be read. [mimeType] is null when nothing could name the bytes.
 */
class IdentifiedFile(
    val cacheUri: Uri,
    val filename: String,
    val mimeType: String?,
    val extension: String?,
) : Parcelable {

    @Suppress("DEPRECATION") // the typed readParcelable overload only exists since API 33
    private constructor(
        parcel: Parcel
    ) : this(
        checkNotNull(parcel.readParcelable(Uri::class.java.classLoader)),
        checkNotNull(parcel.readString()),
        parcel.readString(),
        parcel.readString(),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(cacheUri, 0)
        parcel.writeString(filename)
        parcel.writeString(mimeType)
        parcel.writeString(extension)
    }

    companion object {
        // @JvmField because the framework looks CREATOR up as a static field
        @JvmField
        val CREATOR: Parcelable.Creator<IdentifiedFile> =
            object : Parcelable.Creator<IdentifiedFile> {
                override fun createFromParcel(parcel: Parcel): IdentifiedFile =
                    IdentifiedFile(parcel)

                override fun newArray(size: Int): Array<IdentifiedFile?> = arrayOfNulls(size)
            }
    }
}
