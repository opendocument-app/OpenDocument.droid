package app.opendocument.droid.ui.activity

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.opendocument.droid.R
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.ui.widget.LandingAdapter
import app.opendocument.droid.ui.widget.LandingItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * The screen the app opens on: the documents the user was last working on.
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
    private lateinit var empty: View
    private lateinit var fab: FloatingActionButton

    private var landingVisible = true

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

        empty = view.findViewById(R.id.landing_empty)

        fab = view.findViewById(R.id.landing_open_fab)
        fab.setOnClickListener {
            mainActivity.analyticsManager.report("fab_open")

            mainActivity.findDocument()
        }
        view.findViewById<View>(R.id.landing_empty_open).setOnClickListener {
            mainActivity.analyticsManager.report("empty_open")

            mainActivity.findDocument()
        }

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
     * opened, missing the document that was just added to it.
     */
    fun setLandingVisible(visible: Boolean) {
        landingVisible = visible

        // MainActivity can swap the containers before the view exists, on a launch that goes
        // straight into a document; onViewCreated refreshes anyway once it gets there
        if (!::viewModel.isInitialized) {
            return
        }

        if (visible) {
            viewModel.refresh()
        }
    }

    private fun render(state: LandingViewModel.State) {
        val items = ArrayList<LandingItem>()

        if (state.documents.isNotEmpty()) {
            items.add(LandingItem.Header(R.string.landing_section_recent))
            for (document in state.documents) {
                items.add(LandingItem.Document(document.filename, Uri.parse(document.uri)))
            }
        }

        items.add(LandingItem.Header(R.string.landing_section_settings))
        items.add(LandingItem.CatchAll(state.catchAllEnabled))

        // with nothing to show but the setting, the list is just noise around one button
        val isEmpty = state.documents.isEmpty()

        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE

        // the empty state offers the same thing with a label on it, so the bare fab would just
        // be a second unexplained button next to it
        fab.visibility = if (isEmpty) View.GONE else View.VISIBLE

        adapter.submitList(items)
    }

    override fun onDocumentClicked(document: LandingItem.Document) {
        mainActivity.analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            "recent",
        )

        mainActivity.loadUri(document.uri)
    }

    override fun onDocumentRemoveRequested(document: LandingItem.Document) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(document.filename)
            .setMessage(R.string.landing_remove_recent_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.landing_remove_recent) { _, _ ->
                viewModel.removeRecentDocument(document.uri)
            }
            .show()
    }

    override fun onCatchAllChanged(enabled: Boolean) {
        viewModel.setCatchAllEnabled(enabled)

        mainActivity.analyticsManager.report(
            if (enabled) "catch_all_enabled" else "catch_all_disabled"
        )
    }

    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    companion object {
        const val FRAGMENT_TAG: String = "landing_fragment"
    }
}
