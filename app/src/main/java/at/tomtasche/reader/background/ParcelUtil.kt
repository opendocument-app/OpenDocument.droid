package at.tomtasche.reader.background

import android.os.Parcel

/** Parcel.writeBoolean/readBoolean exist since API 29, which is above minSdk. */
object ParcelUtil {

    @JvmStatic
    fun writeBoolean(parcel: Parcel, value: Boolean) {
        parcel.writeInt(if (value) 1 else 0)
    }

    @JvmStatic fun readBoolean(parcel: Parcel): Boolean = parcel.readInt() != 0
}
