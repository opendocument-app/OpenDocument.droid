package app.opendocument.droid.ui.activity

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.background.RecentDocumentsUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * What the landing screen shows.
 *
 * Reading the recently opened documents touches a file and checking whether they still resolve
 * talks to their content provider, so both happen on [executor] and are published through
 * [LiveData] - which, unlike posting to a handler, drops the result when the fragment is gone.
 *
 * There are no coroutines anywhere in this project, so this follows the executor plus handler shape
 * the loaders already use.
 */
class LandingViewModel(application: Application) : AndroidViewModel(application) {

    class State(val documents: List<RecentDocumentList.Entry>, val catchAllEnabled: Boolean)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val mutableState = MutableLiveData<State>()
    val state: LiveData<State> = mutableState

    /**
     * Publishes what is on disk right away, then re-publishes once the documents that no longer
     * resolve have been dropped. Rendering does not wait on the provider round trip that way.
     */
    fun refresh() {
        executor.execute {
            val context = getApplication<Application>()

            val stored = RecentDocumentsUtil.getRecentDocuments(context)
            val catchAll = CatchAllSetting.isEnabled(context)

            mutableState.postValue(State(stored, catchAll))

            val alive = stored.filter { isReadable(context, Uri.parse(it.uri)) }
            if (alive.size == stored.size) {
                return@execute
            }

            for (entry in stored - alive.toSet()) {
                RecentDocumentsUtil.removeRecentDocument(context, Uri.parse(entry.uri))
            }
            PersistedUriPermissions.prune(context)

            mutableState.postValue(State(alive, catchAll))
        }
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
