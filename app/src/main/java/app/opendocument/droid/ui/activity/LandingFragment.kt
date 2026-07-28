package app.opendocument.droid.ui.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.opendocument.droid.R
import app.opendocument.droid.background.DocumentTreeBrowser
import app.opendocument.droid.background.PersistedUriPermissions
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.widget.LandingAdapter
import app.opendocument.droid.ui.widget.LandingItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * The screen the app opens on: the documents the user was last working on, and the folders they
 * granted access to.
 *
 * A fragment rather than more views inside MainActivity, following DocumentFragment - the list
 * state belongs in a ViewModel so it survives the recreation a theme or rotation change causes.
 */
class LandingFragment : Fragment(), LandingAdapter.Listener {

    // ViewModelProvider rather than the by viewModels() delegate, which lives in fragment-ktx -
    // this project sticks to the non-ktx androidx artifacts. same as DocumentFragment.
    private lateinit var viewModel: LandingViewModel

    private lateinit var adapter: LandingAdapter
    private lateinit var list: RecyclerView
    private lateinit var fab: FloatingActionButton

    private var landingVisible = true

    // the entries behind the recent rows, kept so a swipe can restore the exact entry - the row
    // itself only carries what it needs to draw
    private var lastDocuments: List<RecentDocumentList.Entry> = emptyList()

    /**
     * Back goes up a folder before it goes anywhere else.
     *
     * Registered on viewLifecycleOwner so it stacks above MainActivity's own callback - the
     * dispatcher runs the most recently added enabled callback first.
     */
    private val folderBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!viewModel.leaveFolder()) {
                    isEnabled = false
                }
            }
        }

    /**
     * ACTION_OPEN_DOCUMENT_TREE. Read only - the app asks for no write access anywhere, which is
     * what keeps it free of storage permissions entirely.
     */
    private val openTreeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }

            val treeUri = result.data?.data ?: return@registerForActivityResult

            // has to happen before the transient grant on the result intent expires, and through
            // PersistedUriPermissions rather than the resolver directly: addFolderTree writes the
            // tree down on the view model's executor, so a prune in between would find a grant
            // nothing points at yet and hand it straight back
            if (!PersistedUriPermissions.takeRead(requireContext(), treeUri)) {
                // some providers refuse to hand out a lasting grant
                mainActivity.crashManager.log("could not persist the grant for $treeUri")

                SnackbarHelper.show(
                    requireActivity(),
                    R.string.landing_add_folder_failed,
                    null,
                    false,
                    true,
                )

                return@registerForActivityResult
            }

            mainActivity.analyticsManager.report("folder_added")

            viewModel.addFolderTree(treeUri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_landing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[LandingViewModel::class.java]

        adapter = LandingAdapter(this)

        list = view.findViewById(R.id.landing_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        fab = view.findViewById(R.id.landing_open_fab)
        fab.setOnClickListener {
            mainActivity.analyticsManager.report("fab_open")

            mainActivity.findDocument()
        }

        attachSwipeToRemove()

        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, folderBackCallback)

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
    }

    override fun onResume() {
        super.onResume()

        // a document may have been opened from another app while we were in the background
        viewModel.refresh()
    }

    /**
     * Called by MainActivity when it swaps between the landing screen and a document.
     *
     * The fragment is only hidden, never stopped - so nothing in the lifecycle fires when a
     * document is closed, and the list would keep showing what it held before the document was
     * opened, missing the document that was just added to it. The back callback has to be turned
     * off too, or a hidden landing screen would keep swallowing back inside a document.
     */
    fun setLandingVisible(visible: Boolean) {
        landingVisible = visible

        // MainActivity can swap the containers before the view exists, on a launch that goes
        // straight into a document; onViewCreated refreshes anyway once it gets there
        if (!::viewModel.isInitialized) {
            return
        }

        folderBackCallback.isEnabled = visible && viewModel.isInsideFolder

        if (visible) {
            viewModel.refresh()
        }
    }

    private fun render(state: LandingViewModel.State) {
        folderBackCallback.isEnabled = landingVisible && state.location != null

        lastDocuments = state.documents

        val items = ArrayList<LandingItem>()

        val location = state.location
        val isEmpty = location == null && state.documents.isEmpty() && state.trees.isEmpty()

        if (location != null) {
            renderFolder(items, state, location)
        } else {
            renderRoot(items, state, isEmpty)
        }

        // the empty state offers the same thing with a label on it, so the bare fab would just
        // be a second unexplained button next to it
        fab.visibility = if (isEmpty) View.GONE else View.VISIBLE

        // a refresh can land while a document is open - the fragment is only hidden, so it keeps
        // getting lifecycle callbacks - and the folder name must not take over the title then
        if (landingVisible) {
            mainActivity.title = location?.displayName ?: ""
        }

        adapter.submitList(items)
    }

    /**
     * Swiping a recent document away removes it, with an undo that puts it back at the same place.
     *
     * Only the recently opened documents can go: a document inside a folder is simply there, and
     * the app has no business deleting anything.
     */
    private fun attachSwipeToRemove() {
        val callback =
            object :
                ItemTouchHelper.SimpleCallback(
                    0,
                    ItemTouchHelper.START or ItemTouchHelper.END,
                ) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = false

                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int =
                    if (adapter.isRemovableDocument(viewHolder.bindingAdapterPosition))
                        super.getSwipeDirs(recyclerView, viewHolder)
                    else 0

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    val document = adapter.documentAt(position) ?: return

                    removeWithUndo(document, position)
                }
            }

        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun removeWithUndo(document: LandingItem.Document, position: Int) {
        val entry = entryFor(document) ?: return

        // where it sits among the documents, which is what restoring it needs - the adapter
        // position counts the section header too
        val index = lastDocuments.indexOfFirst { it.uri == entry.uri }

        mainActivity.analyticsManager.report("recent_swiped_away")

        viewModel.removeRecentDocumentUndoably(document.uri)

        // the grant it was holding is deliberately not released here: an undo would need it back,
        // and prune() reclaims it on the next launch anyway, which is the whole point of
        // reconciling against the stored lists rather than bookkeeping every removal
        SnackbarHelper.show(
            requireActivity(),
            R.string.landing_recent_removed,
            R.string.landing_undo,
            { viewModel.restoreRecentDocument(entry, index) },
            false,
            false,
        )
    }

    private fun entryFor(document: LandingItem.Document): RecentDocumentList.Entry? {
        val uri = document.uri.toString()

        return lastDocuments.firstOrNull { it.uri == uri }
    }

    private fun renderRoot(
        items: ArrayList<LandingItem>,
        state: LandingViewModel.State,
        isEmpty: Boolean,
    ) {
        if (isEmpty) {
            // a row of the list rather than a screen of its own: the settings below it are the
            // only place the catch-all switch is offered, and a fresh install starts here. it
            // carries both actions itself, so the empty folders section stays off the screen.
            items.add(LandingItem.Empty())
        } else {
            if (state.documents.isNotEmpty()) {
                items.add(LandingItem.Header(R.string.landing_section_recent))
                for (document in state.documents) {
                    items.add(
                        LandingItem.Document(
                            document.filename,
                            Uri.parse(document.uri),
                            lastOpenedLabel(document.lastOpenedAt),
                        )
                    )
                }
            }

            items.add(LandingItem.Header(R.string.landing_section_folders))
            for (tree in state.trees) {
                items.add(
                    LandingItem.Folder(
                        tree.displayName,
                        tree.uri,
                        DocumentTreeBrowser.rootDocumentId(tree.uri),
                    )
                )
            }
            items.add(
                LandingItem.Action(
                    LandingItem.ACTION_ADD_FOLDER,
                    R.string.landing_add_folder,
                    R.drawable.ic_add,
                )
            )
        }

        items.add(LandingItem.Header(R.string.landing_section_settings))
        items.add(
            LandingItem.Setting(
                LandingItem.SETTING_CATCH_ALL,
                R.string.landing_catch_all_title,
                R.string.landing_intro_open_all,
                state.catchAllEnabled,
            )
        )
    }

    /** "2 hours ago", or nothing at all for entries written before this was recorded. */
    private fun lastOpenedLabel(lastOpenedAt: Long): String? {
        if (lastOpenedAt <= 0) {
            return null
        }

        return DateUtils.getRelativeTimeSpanString(
                lastOpenedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            .toString()
    }

    private fun renderFolder(
        items: ArrayList<LandingItem>,
        state: LandingViewModel.State,
        location: LandingViewModel.FolderLocation,
    ) {
        items.add(
            LandingItem.Action(
                LandingItem.ACTION_UP,
                R.string.landing_up_one_level,
                R.drawable.ic_arrow_upward,
            )
        )

        if (state.children.isEmpty()) {
            items.add(LandingItem.Message(R.string.landing_folder_empty))

            return
        }

        for (child in state.children) {
            if (child.isDirectory) {
                items.add(LandingItem.Folder(child.displayName, location.treeUri, child.documentId))
            } else {
                items.add(
                    LandingItem.Document(
                        child.displayName,
                        DocumentTreeBrowser.documentUri(location.treeUri, child.documentId),
                    )
                )
            }
        }
    }

    override fun onDocumentClicked(document: LandingItem.Document) {
        mainActivity.analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            if (viewModel.isInsideFolder) "folder" else "recent",
        )

        mainActivity.loadUri(document.uri)
    }

    override fun onDocumentRemoveRequested(document: LandingItem.Document) {
        // a document inside a folder is not ours to forget - it is simply there
        if (viewModel.isInsideFolder) {
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(document.filename)
            .setMessage(R.string.landing_remove_recent_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.landing_remove_recent) { _, _ ->
                viewModel.removeRecentDocument(document.uri)
            }
            .show()
    }

    override fun onFolderClicked(folder: LandingItem.Folder) {
        viewModel.enterFolder(folder.treeUri, folder.documentId, folder.name)
    }

    override fun onFolderRemoveRequested(folder: LandingItem.Folder) {
        // only the folders the user added themselves can be given back; a sub directory is just
        // part of the tree above it
        if (viewModel.isInsideFolder) {
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(folder.name)
            .setMessage(R.string.landing_remove_folder_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.landing_remove_folder) { _, _ ->
                mainActivity.analyticsManager.report("folder_removed")

                viewModel.removeFolderTree(folder.treeUri)
            }
            .show()
    }

    override fun onActionClicked(action: Int) {
        when (action) {
            LandingItem.ACTION_ADD_FOLDER -> addFolder()
            LandingItem.ACTION_UP -> viewModel.leaveFolder()
        }
    }

    private fun addFolder() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )

        try {
            openTreeLauncher.launch(intent)
        } catch (e: Exception) {
            mainActivity.crashManager.log(e)

            SnackbarHelper.show(
                requireActivity(),
                R.string.crouton_error_open_app,
                null,
                false,
                true,
            )
        }
    }

    override fun onOpenClicked() {
        mainActivity.analyticsManager.report("empty_open")

        mainActivity.findDocument()
    }

    override fun onSettingChanged(setting: Int, enabled: Boolean) {
        when (setting) {
            LandingItem.SETTING_CATCH_ALL -> {
                viewModel.setCatchAllEnabled(enabled)

                mainActivity.analyticsManager.report(
                    if (enabled) "catch_all_enabled" else "catch_all_disabled"
                )
            }
        }
    }

    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    companion object {
        const val FRAGMENT_TAG: String = "landing_fragment"
    }
}
