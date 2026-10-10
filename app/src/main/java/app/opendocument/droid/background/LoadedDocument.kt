package app.opendocument.droid.background

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * A rendered document with one URI per part. [partCuts] identifies truncated sheets; [editing] and
 * [readsAsDocument] come from the core.
 */
class LoadedDocument(
    val request: DocumentRequest,
    val file: IdentifiedFile,
    val partTitles: List<String?>,
    val partUris: List<Uri>,
    val partCuts: List<SheetCut?>,
    val editing: EditingKind,
    val readsAsDocument: Boolean,
    val pageSize: PageSize?,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(request, 0)
        parcel.writeParcelable(file, 0)
        parcel.writeList(partTitles)
        parcel.writeList(partUris)
        parcel.writeList(partCuts)
        parcel.writeInt(editing.ordinal)
        ParcelUtil.writeBoolean(parcel, readsAsDocument)
        // 0 for none, which no page measures
        parcel.writeInt(pageSize?.widthMils ?: 0)
        parcel.writeInt(pageSize?.heightMils ?: 0)
    }

    companion object {
        // @JvmField because the framework looks CREATOR up as a static field
        @JvmField
        val CREATOR: Parcelable.Creator<LoadedDocument> =
            object : Parcelable.Creator<LoadedDocument> {

                @Suppress("DEPRECATION") // typed readParcelable needs API 33
                override fun createFromParcel(parcel: Parcel): LoadedDocument {
                    // in the order writeToParcel wrote them
                    val classLoader = LoadedDocument::class.java.classLoader
                    val request = checkNotNull(parcel.readParcelable<DocumentRequest>(classLoader))
                    val file = checkNotNull(parcel.readParcelable<IdentifiedFile>(classLoader))

                    val partTitles = ArrayList<String?>()
                    parcel.readList(partTitles, classLoader)

                    val partUris = ArrayList<Uri>()
                    parcel.readList(partUris, classLoader)

                    val partCuts = ArrayList<SheetCut?>()
                    parcel.readList(partCuts, classLoader)

                    val editing = EditingKind.entries[parcel.readInt()]
                    val readsAsDocument = ParcelUtil.readBoolean(parcel)
                    val widthMils = parcel.readInt()
                    val heightMils = parcel.readInt()

                    return LoadedDocument(
                        request,
                        file,
                        partTitles,
                        partUris,
                        partCuts,
                        editing,
                        readsAsDocument,
                        if (widthMils > 0 && heightMils > 0) PageSize(widthMils, heightMils)
                        else null,
                    )
                }

                override fun newArray(size: Int): Array<LoadedDocument?> = arrayOfNulls(size)
            }
    }
}
