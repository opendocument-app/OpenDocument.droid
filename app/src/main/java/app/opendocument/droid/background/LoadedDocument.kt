package app.opendocument.droid.background

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * A document that was rendered and published: the request it came from, the file it turned out to
 * be, and one uri per part (spreadsheets have one per sheet, everything else a single one with a
 * null title).
 *
 * [isEditable] and [readsAsDocument] are the core's own answers about this document, never a guess
 * from its mime type - see `CoreLoader.isDocumentEditable` and `CoreLoader.readsAsDocument`.
 */
class LoadedDocument(
    val request: DocumentRequest,
    val file: IdentifiedFile,
    val partTitles: List<String?>,
    val partUris: List<Uri>,
    val isEditable: Boolean,
    val readsAsDocument: Boolean,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(request, 0)
        parcel.writeParcelable(file, 0)
        parcel.writeList(partTitles)
        parcel.writeList(partUris)
        ParcelUtil.writeBoolean(parcel, isEditable)
        ParcelUtil.writeBoolean(parcel, readsAsDocument)
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

                    return LoadedDocument(
                        request,
                        file,
                        partTitles,
                        partUris,
                        ParcelUtil.readBoolean(parcel),
                        ParcelUtil.readBoolean(parcel),
                    )
                }

                override fun newArray(size: Int): Array<LoadedDocument?> = arrayOfNulls(size)
            }
    }
}
