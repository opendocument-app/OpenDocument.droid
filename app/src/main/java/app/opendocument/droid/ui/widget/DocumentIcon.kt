package app.opendocument.droid.ui.widget

import androidx.annotation.DrawableRes
import app.opendocument.droid.R
import app.opendocument.droid.background.MimeTypeResolver

/**
 * Visual hints from the saved filename; choosing an icon never opens a document or its provider.
 */
object DocumentIcon {
    @DrawableRes
    fun forFilename(filename: String): Int =
        when (MimeTypeResolver.parseExtension(filename)?.lowercase()) {
            "odt",
            "ott",
            "fodt",
            "odm",
            "otm",
            "doc",
            "docx",
            "docm",
            "dot",
            "dotx",
            "dotm",
            "pages",
            "rtf",
            "txt",
            "md",
            "markdown" -> R.drawable.ic_document_text
            "ods",
            "ots",
            "fods",
            "xls",
            "xlsx",
            "xlsm",
            "xlsb",
            "xlt",
            "xltx",
            "xltm",
            "xlm",
            "csv",
            "numbers" -> R.drawable.ic_document_spreadsheet
            "odp",
            "otp",
            "fodp",
            "ppt",
            "pptx",
            "pptm",
            "pot",
            "potx",
            "potm",
            "pps",
            "ppsx",
            "ppsm",
            "key" -> R.drawable.ic_document_presentation
            "pdf" -> R.drawable.ic_document_pdf
            else -> R.drawable.ic_description
        }
}
