package app.opendocument.droid.background

import android.os.Parcel
import android.os.Parcelable

/**
 * What a sheet's markup leaves out: the extent its cells span against the extent that was written.
 *
 * odrcore's own answer (`HtmlView.sheetCut()`), and only present for a sheet [SpreadsheetBudget]
 * cut - a sheet written whole has none.
 */
class SheetCut(
    val contentRows: Int,
    val contentColumns: Int,
    val renderedRows: Int,
    val renderedColumns: Int,
) : Parcelable {

    val rowsWereCut: Boolean
        get() = renderedRows < contentRows

    val columnsWereCut: Boolean
        get() = renderedColumns < contentColumns

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(contentRows)
        parcel.writeInt(contentColumns)
        parcel.writeInt(renderedRows)
        parcel.writeInt(renderedColumns)
    }

    companion object {
        // @JvmField because the framework looks CREATOR up as a static field
        @JvmField
        val CREATOR: Parcelable.Creator<SheetCut> =
            object : Parcelable.Creator<SheetCut> {
                override fun createFromParcel(parcel: Parcel): SheetCut =
                    // in the order writeToParcel wrote them
                    SheetCut(
                        parcel.readInt(),
                        parcel.readInt(),
                        parcel.readInt(),
                        parcel.readInt(),
                    )

                override fun newArray(size: Int): Array<SheetCut?> = arrayOfNulls(size)
            }
    }
}
