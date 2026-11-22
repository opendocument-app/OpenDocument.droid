package app.opendocument.droid.ui.widget

import androidx.annotation.DrawableRes
import app.opendocument.core.DocumentType
import app.opendocument.core.FileCategory
import app.opendocument.core.FileType
import app.opendocument.core.Odr
import app.opendocument.droid.R
import app.opendocument.droid.background.MimeTypeResolver

/**
 * Prefer core's recorded file type; older entries use its extension lookup without opening files.
 */
object DocumentIcon {
    @DrawableRes
    fun forDocument(filename: String, detectedType: FileType? = null): Int {
        val type =
            detectedType?.takeUnless { it == FileType.UNKNOWN }
                ?: MimeTypeResolver.parseExtension(filename)?.lowercase()?.let {
                    Odr.fileTypeByFileExtension(it)
                }
                ?: return R.drawable.ic_description
        // PDF has its own visual identity even though core classifies it as a text document.
        if (type == FileType.PORTABLE_DOCUMENT_FORMAT) return R.drawable.ic_document_pdf
        return when (Odr.documentTypeByFileType(type)) {
            DocumentType.TEXT -> R.drawable.ic_document_text
            DocumentType.SPREADSHEET -> R.drawable.ic_document_spreadsheet
            DocumentType.PRESENTATION -> R.drawable.ic_document_presentation
            else ->
                if (Odr.fileCategoryByFileType(type) == FileCategory.TEXT)
                    R.drawable.ic_document_text
                else R.drawable.ic_description
        }
    }
}
