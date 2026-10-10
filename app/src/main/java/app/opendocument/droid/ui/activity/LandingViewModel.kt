package app.opendocument.droid.ui.activity

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.opendocument.droid.background.CatchAllSetting
import app.opendocument.droid.background.PaginationSetting
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.background.RecentDocumentsUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Loads recent documents and checks provider access on [executor], publishing lifecycle-aware
 * [LiveData].
 */
class LandingViewModel(application: Application) : AndroidViewModel(application) {

    class State(
        val documents: List<RecentDocumentList.Entry>,
        val paginationEnabled: Boolean,
        val catchAllEnabled: Boolean,
        val introExpanded: Boolean,
        val settingsExpanded: Boolean,
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val mutableState = MutableLiveData<State>()
    val state: LiveData<State> = mutableState

    /**
     * Session-only intro expansion override. Null follows list emptiness; crossing between empty
     * and nonempty resets it.
     */
    @Volatile private var introExpanded: Boolean? = null

    @Volatile private var settingsExpanded = false

    /** What the list held last time, to notice it becoming empty or stopping being empty. */
    @Volatile private var listWasEmpty: Boolean? = null

    /** Publishes what is on disk. What the screen's own doing - an edit - needs. */
    fun refresh() {
        publish(dropUnreachable = false)
    }

    /**
     * Publishes stored entries immediately, then checks provider access. Call when returning to the
     * landing screen.
     */
    fun reload() {
        publish(dropUnreachable = true)
    }

    private fun publish(dropUnreachable: Boolean) {
        executor.execute {
            val context = getApplication<Application>()

            val stored = RecentDocumentsUtil.getRecentDocuments(context)
            val pagination = PaginationSetting.isEnabled(context)
            val catchAll = CatchAllSetting.isEnabled(context)

            mutableState.postValue(stateOf(stored, pagination, catchAll))

            if (!dropUnreachable) {
                return@execute
            }

            val alive = stored.filter { isReadable(context, Uri.parse(it.uri)) }
            if (alive.size == stored.size) {
                return@execute
            }

            for (entry in stored - alive.toSet()) {
                RecentDocumentsUtil.removeRecentDocument(context, Uri.parse(entry.uri))
            }
            PersistedUriPermissions.prune(context)

            mutableState.postValue(stateOf(alive, pagination, catchAll))
        }
    }

    private fun stateOf(
        documents: List<RecentDocumentList.Entry>,
        paginationEnabled: Boolean,
        catchAllEnabled: Boolean,
    ): State {
        val isEmpty = documents.isEmpty()

        rememberEmptiness(isEmpty)

        return State(
            documents,
            paginationEnabled,
            catchAllEnabled,
            introExpanded = introExpanded ?: isEmpty,
            settingsExpanded = settingsExpanded,
        )
    }

    /** Resets the intro override when the recent list changes between empty and nonempty. */
    private fun rememberEmptiness(isEmpty: Boolean) {
        if (listWasEmpty == isEmpty) {
            return
        }

        listWasEmpty = isEmpty
        introExpanded = null
    }

    /** @return whether the section is unfolded afterwards. */
    fun toggleIntro(): Boolean {
        val expanded = !(state.value?.introExpanded ?: true)
        introExpanded = expanded

        refresh()

        return expanded
    }

    /** @return whether the section is unfolded afterwards. */
    fun toggleSettings(): Boolean {
        settingsExpanded = !settingsExpanded

        refresh()

        return settingsExpanded
    }

    /**
     * A document already on the screen keeps the layout it was translated with - this reaches the
     * next one that is opened. The switch is only on the landing screen, which is only reached by
     * closing whatever was open, so there is nothing on screen to re-lay out.
     */
    fun setPaginationEnabled(enabled: Boolean) {
        PaginationSetting.setEnabled(getApplication(), enabled)

        refresh()
    }

    fun setCatchAllEnabled(enabled: Boolean) {
        CatchAllSetting.setEnabled(getApplication(), enabled)

        refresh()
    }

    /**
     * Puts a swiped away document back where it was.
     *
     * The grant it needs is still held: [prune] only runs after a removal that the user has had the
     * chance to undo, so nothing has been handed back yet.
     */
    fun restoreRecentDocument(entry: RecentDocumentList.Entry, index: Int) {
        executor.execute {
            val context = getApplication<Application>()

            // putting one back can push another off the end, if a document was opened while the
            // undo was still on offer - that one is holding a grant nothing points at any more
            if (RecentDocumentsUtil.restoreRecentDocument(context, entry, index).isNotEmpty()) {
                PersistedUriPermissions.prune(context)
            }

            refresh()
        }
    }

    /**
     * Removes a recent entry while retaining its grant for undo. Unused grants are pruned on the
     * next launch.
     */
    fun removeRecentDocument(uri: Uri) {
        executor.execute {
            RecentDocumentsUtil.removeRecentDocument(getApplication(), uri)

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
