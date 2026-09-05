package app.opendocument.droid.background

import android.os.Parcel
import android.os.Parcelable

/**
 * How much of a sheet the markup carries against how much was written - odrcore's own
 * `HtmlView.sheetCut()`, and only present where [SpreadsheetBudget] cut the sheet.
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
