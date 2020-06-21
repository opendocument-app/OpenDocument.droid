package at.tomtasche.reader.background;

import android.os.Parcel;

public class ParcelUtil {

    public static void writeBoolean(Parcel parcel, boolean value) {
        parcel.writeInt(value ? 1 : 0);
    }

    public static boolean readBoolean(Parcel parcel) {
        return parcel.readInt() != 0;
    }
}
