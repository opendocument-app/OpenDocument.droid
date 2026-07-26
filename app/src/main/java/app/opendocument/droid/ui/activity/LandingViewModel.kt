package app.opendocument.droid.ui.activity

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.DocumentTreeBrowser
import app.opendocument.droid.background.FolderTreesUtil
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.background.RecentDocumentsUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * What the landing screen shows.
 *
 * Reading the recently opened documents touches a file, listing a folder talks to its provider, and
 * checking whether a document still resolves does too - so all of it happens on [executor] and is
 * published through [LiveData], which, unlike posting to a handler, drops the result when the
 * fragment is gone.
 *
 * There are no coroutines anywhere in this project, so this follows the executor plus handler shape
 * the loaders already use.
 */
class LandingViewModel(application: Application) : AndroidViewModel(application) {

    /** Where in a folder tree the browser currently is. */
    class FolderLocation(val treeUri: Uri, val documentId: String, val displayName: String)

    class State(
        val documents: List<RecentDocumentList.Entry>,
        val trees: List<FolderTreesUtil.FolderTree>,
        val catchAllEnabled: Boolean,
        /** null while the roots are shown, otherwise the folder that is open. */
        val location: FolderLocation?,
        val children: List<DocumentTreeBrowser.Child>,
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val mutableState = MutableLiveData<State>()
    val state: LiveData<State> = mutableState

    // not persisted across process death on purpose: coming back into a folder whose grant was
    // revoked while the app was away is a pile of edge cases for very little
    private val backStack = ArrayList<FolderLocation>()
    private var location: FolderLocation? = null

    val isInsideFolder: Boolean
        get() = location != null

    /**
     * Publishes what is on disk right away, then re-publishes once the documents that no longer
     * resolve have been dropped. Rendering does not wait on the provider round trip that way.
     */
    fun refresh() {
        executor.execute {
            val context = getApplication<Application>()

            val stored = RecentDocumentsUtil.getRecentDocuments(context)
            val trees = FolderTreesUtil.getFolderTrees(context)
            val catchAll = CatchAllSetting.isEnabled(context)
            val here = location

            val children =
                if (here == null) emptyList()
                else DocumentTreeBrowser.listChildren(context, here.treeUri, here.documentId)

            mutableState.postValue(State(stored, trees, catchAll, here, children))

            val alive = stored.filter { isReadable(context, Uri.parse(it.uri)) }
            if (alive.size == stored.size) {
                return@execute
            }

            for (entry in stored - alive.toSet()) {
                RecentDocumentsUtil.removeRecentDocument(context, Uri.parse(entry.uri))
            }
            PersistedUriPermissions.prune(context)

            mutableState.postValue(State(alive, trees, catchAll, here, children))
        }
    }

    fun addFolderTree(treeUri: Uri) {
        executor.execute {
            val context = getApplication<Application>()

            val displayName = DocumentTreeBrowser.displayNameOf(context, treeUri)
            if (FolderTreesUtil.addFolderTree(context, treeUri, displayName).isNotEmpty()) {
                PersistedUriPermissions.prune(context)
            }

            refresh()
        }
    }

    fun removeFolderTree(treeUri: Uri) {
        executor.execute {
            val context = getApplication<Application>()

            FolderTreesUtil.removeFolderTree(context, treeUri)
            PersistedUriPermissions.prune(context)

            // the folder that was just dropped may be the one we are standing in
            if (location?.treeUri == treeUri) {
                backStack.clear()
                location = null
            }

            refresh()
        }
    }

    fun enterFolder(treeUri: Uri, documentId: String, displayName: String) {
        location?.let { backStack.add(it) }
        location = FolderLocation(treeUri, documentId, displayName)

        refresh()
    }

    /** @return whether there was somewhere to go back to. */
    fun leaveFolder(): Boolean {
        if (location == null) {
            return false
        }

        location = backStack.removeLastOrNull()

        refresh()

        return true
    }

    fun setCatchAllEnabled(enabled: Boolean) {
        CatchAllSetting.setEnabled(getApplication(), enabled)

        refresh()
    }

    fun removeRecentDocument(uri: Uri) {
        executor.execute {
            RecentDocumentsUtil.removeRecentDocument(getApplication(), uri)
            PersistedUriPermissions.prune(getApplication())

            refresh()
        }
    }

    /**
     * Puts a swiped away document back where it was.
     *
     * The grant it needs is still held: [prune] only runs after a removal that the user has had the
     * chance to undo, so nothing has been handed back yet.
     */
    fun restoreRecentDocument(entry: RecentDocumentList.Entry, index: Int) {
        executor.execute {
            RecentDocumentsUtil.restoreRecentDocument(getApplication(), entry, index)

            refresh()
        }
    }

    /** Forgets a document without releasing its grant yet, so an undo can still put it back. */
    fun removeRecentDocumentUndoably(uri: Uri) {
        executor.execute {
            RecentDocumentsUtil.removeRecentDocument(getApplication(), uri)

            refresh()
        }
    }

    /** Hands back the grants of everything that is no longer referenced. */
    fun releaseUnusedGrants() {
        executor.execute { PersistedUriPermissions.prune(getApplication()) }
    }

    override fun onCleared() {
        super.onCleared()

        executor.shutdown()
    }

    /**
     * Whether the document behind [uri] can still be reached. A grant revoked while the app was
     * away, a removed sd card and a deleted file all end up here.
     */
    private fun isReadable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                .use { cursor -> cursor != null && cursor.moveToFirst() }
        } catch (e: Exception) {
            false
        }
    }
}
