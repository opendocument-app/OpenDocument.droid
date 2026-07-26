package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Lists what is inside a directory tree the user granted access to.
 *
 * Uses [DocumentsContract] rather than androidx's DocumentFile: DocumentFile.listFiles() issues a
 * fresh query per child as soon as the name or type is asked for, which is one binder round trip
 * per file. One cursor over the whole folder is what this needs instead.
 */
object DocumentTreeBrowser {

    class Child(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val lastModified: Long,
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /**
     * The supported documents and the sub directories of [parentDocumentId], directories first and
     * then by name.
     *
     * Does binder work, so it must not run on the main thread.
     */
    fun listChildren(context: Context, treeUri: Uri, parentDocumentId: String): List<Child> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

        val children = ArrayList<Child>()

        try {
            context.contentResolver
                .query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )
                .use { cursor ->
                    if (cursor == null) {
                        return emptyList()
                    }

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(0) ?: continue
                        val displayName = cursor.getString(1) ?: continue
                        val mimeType = cursor.getString(2) ?: ""

                        // dotfiles are not something anyone browses for a document
                        if (displayName.startsWith(".")) {
                            continue
                        }

                        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        if (
                            !isDirectory &&
                                !SupportedDocumentTypes.isSupported(mimeType, displayName)
                        ) {
                            continue
                        }

                        children.add(Child(documentId, displayName, mimeType, cursor.getLong(3)))
                    }
                }
        } catch (e: Exception) {
            // a revoked grant, an unmounted sd card or a provider that died - nothing to list
            return emptyList()
        }

        children.sortWith(
            compareBy<Child> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) {
                    it.displayName
                }
        )

        return children
    }

    /** The document id of the folder the user picked, i.e. the root of the tree. */
    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /** The name the provider gives the folder itself, falling back to its last path segment. */
    fun displayNameOf(context: Context, treeUri: Uri): String {
        val documentUri = documentUri(treeUri, rootDocumentId(treeUri))

        try {
            context.contentResolver
                .query(
                    documentUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        cursor.getString(0)?.let {
                            return it
                        }
                    }
                }
        } catch (e: Exception) {
            // fall through to the uri
        }

        return treeUri.lastPathSegment ?: treeUri.toString()
    }
}
