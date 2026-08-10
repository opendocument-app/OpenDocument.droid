package app.opendocument.droid.background

import android.content.Context
import android.net.Uri
import app.opendocument.droid.nonfree.CrashManager
import java.io.File

/**
 * Writes a document back to the file the user picked.
 *
 * The write goes straight into their own document, so what is there is kept until the new content
 * has landed whole - and if the rollback does not get it back in, the copy is left in the cache
 * rather than the job being finished.
 *
 * Does file and provider work, so it belongs on the same background thread the loads run on.
 */
class DocumentSaver(
    private val context: Context,
    private val coreLoader: CoreLoader,
    private val crashManager: CrashManager,
) {

    /**
     * Saves [document] to [target].
     *
     * @param htmlDiff the edits still only in the page, or null for a "full save" of the file as it
     *   is on disk.
     */
    fun save(document: LoadedDocument, target: Uri, htmlDiff: String?) {
        // only the retranslated file is ours to remove afterwards - the other branch hands back
        // the cache file of the document that is still open
        var retranslated: File? = null
        var backup: File? = null
        var backupIsTheLastCopy = false

        try {
            val fileToSave =
                if (htmlDiff != null) {
                    val edited =
                        coreLoader.retranslate(document.request, document.file, htmlDiff)
                            ?: throw RuntimeException("retranslate failed")
                    retranslated = edited

                    edited
                } else {
                    // "full save" from the main UI
                    checkNotNull(FileCache.getCacheFile(context, document.file.cacheUri)) {
                        "not a cached file: " + document.file.cacheUri
                    }
                }

            // the write goes straight into the user's own document, so keep what is there
            // until the new content has landed whole
            backup = backUp(target)

            try {
                if (!writeTo(target, fileToSave)) {
                    crashManager.log("saved without truncating: $target")
                }
            } catch (e: Throwable) {
                backupIsTheLastCopy = !restore(target, backup)

                throw e
            }
        } finally {
            retranslated?.delete()

            // if the rollback did not get the old content back in, this copy is all that is
            // left of it - leave it in the cache rather than finishing the job
            if (!backupIsTheLastCopy) {
                backup?.let { FileCache.deleteCacheFile(it) }
            }
        }
    }

    /** Copies [source] over [uri]. False if the provider would not truncate first. */
    private fun writeTo(uri: Uri, source: File): Boolean {
        // "wt", not the default "w": not every provider truncates for "w", and the tail of a
        // longer previous document left behind is what stops an odt or docx opening
        var truncated = true

        val outputStream =
            try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                // a provider that rejects the mode outright - saving at all beats truncating
                crashManager.log(e, uri)

                truncated = false

                context.contentResolver.openOutputStream(uri)
            }

        checkNotNull(outputStream) { "cannot write $uri" }.use { StreamUtil.copy(source, it) }

        return truncated
    }

    /** What [uri] holds right now, so a half finished write can be rolled back. */
    private fun backUp(uri: Uri): File? {
        val backup = FileCache.createCacheFile(context)

        return try {
            val input =
                checkNotNull(context.contentResolver.openInputStream(uri)) { "cannot read $uri" }
            StreamUtil.copy(input, backup)

            if (backup.length() > 0) {
                backup
            } else {
                // a document that was just created has nothing worth keeping
                FileCache.deleteCacheFile(backup)

                null
            }
        } catch (e: Throwable) {
            // unreadable target: no worse than before, the save just cannot be rolled back
            crashManager.log(e, uri)
            FileCache.deleteCacheFile(backup)

            null
        }
    }

    /**
     * Puts [backup] back into [uri]. False if the old content could not be restored, including a
     * write that did not truncate and so left the tail of the failed save behind it.
     */
    private fun restore(uri: Uri, backup: File?): Boolean {
        if (backup == null) {
            return false
        }

        return try {
            writeTo(uri, backup)
        } catch (e: Throwable) {
            crashManager.log(e, uri)

            false
        }
    }
}
