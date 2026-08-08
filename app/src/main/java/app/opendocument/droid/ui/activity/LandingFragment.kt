package app.opendocument.droid.ui.activity

import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.opendocument.droid.R
import app.opendocument.droid.background.RecentDocumentList
import app.opendocument.droid.nonfree.AnalyticsConstants
import app.opendocument.droid.ui.SnackbarHelper
import app.opendocument.droid.ui.widget.LandingAdapter
import app.opendocument.droid.ui.widget.LandingItem
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * The screen the app opens on: the documents the user was last working on, and the settings.
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

    // the entries behind the rows, kept so a swipe can restore the exact one - the rows themselves
    // only carry what they need to draw
    private var lastDocuments: List<RecentDocumentList.Entry> = emptyList()

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

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
    }

    override fun onResume() {
        super.onResume()

        // a document may have been opened from another app while we were in the background, and a
        // grant may have been revoked or an sd card pulled - so this is a reload, not a refresh
        viewModel.reload()
    }

    /**
     * Called by MainActivity when it swaps between the landing screen and a document.
     *
     * The fragment is only hidden, never stopped - so nothing in the lifecycle fires when a
     * document is closed, and the list would keep showing what it held before the document was
     * opened, missing the document that was just added to it.
     */
    fun setLandingVisible(visible: Boolean) {
        // MainActivity can swap the containers before the view exists, on a launch that goes
        // straight into a document; onViewCreated reloads anyway once it gets there
        if (!::viewModel.isInitialized) {
            return
        }

        if (visible) {
            viewModel.reload()
        }
    }

    /**
     * Re-renders the rows the screen does not own.
     *
     * The consent flow settles seconds after launch and decides whether the privacy options row is
     * there at all - nothing the ViewModel could see, so the activity says when to look again.
     */
    fun refresh() {
        if (!::viewModel.isInitialized) {
            return
        }

        viewModel.refresh()
    }

    private fun render(state: LandingViewModel.State) {
        lastDocuments = state.documents

        val items = ArrayList<LandingItem>()

        // the way in, above everything else and there whatever the list holds
        items.add(LandingItem.Open())

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

        // what the app is for, above the settings and folded away once there are documents to
        // read instead - by then the user has seen all three lines and wants the list back
        items.add(
            LandingItem.Header(
                R.string.landing_section_intro,
                LandingItem.SECTION_INTRO,
                state.introExpanded,
            )
        )
        if (state.introExpanded) {
            items.add(LandingItem.Intro())
        }

        // folded to begin with: the switches under here are about how a document is laid out and
        // how it arrives, both of which are right for most people as they stand
        items.add(
            LandingItem.Header(
                R.string.landing_section_settings,
                LandingItem.SECTION_SETTINGS,
                state.settingsExpanded,
            )
        )
        if (state.settingsExpanded) {
            // first, because it is about every document the app shows - the one below it is about
            // the few that will not come across at all
            items.add(
                LandingItem.Setting(
                    LandingItem.SETTING_PAGINATION,
                    R.string.landing_pagination_title,
                    R.string.landing_pagination_body,
                    state.paginationEnabled,
                )
            )

            items.add(
                LandingItem.Setting(
                    LandingItem.SETTING_CATCH_ALL,
                    R.string.landing_catch_all_title,
                    R.string.landing_intro_open_all,
                    state.catchAllEnabled,
                )
            )

            // asked here rather than carried in the state: what billing knows lives on the
            // activity, not in anything the ViewModel reads. it is only ever false in pro or after
            // a purchase, both of which are settled long before the first refresh comes back
            if (mainActivity.offersAdRemoval()) {
                items.add(
                    LandingItem.Action(
                        LandingItem.ACTION_REMOVE_ADS,
                        R.string.menu_remove_ads,
                        R.drawable.ic_block,
                    )
                )
            }

            // the way back into the consent form, which the ump sdk requires an app to offer for
            // as long as it holds a decision that can be withdrawn. asked the same way as the ad
            // removal, and for the same reason: only the activity has been told
            if (mainActivity.offersPrivacyOptions()) {
                items.add(
                    LandingItem.Action(
                        LandingItem.ACTION_PRIVACY_OPTIONS,
                        R.string.menu_privacy_options,
                        R.drawable.ic_privacy_tip,
                    )
                )
            }
        }

        adapter.submitList(items)
    }

    /**
     * Swiping a row away removes it, with an undo that puts it back at the same place.
     *
     * The one gesture, and deliberately: a long press did the same for a while, and a press that
     * has to be held to find out it does anything is not something anyone finds. Swiping a row out
     * of a list is what the rest of the platform does, and the undo says what happened either way.
     *
     * Only a recently opened document can go: that list is the app's own memory of it, and nothing
     * else on the screen is the app's to forget.
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
                    if (adapter.isRemovable(viewHolder.bindingAdapterPosition))
                        super.getSwipeDirs(recyclerView, viewHolder)
                    else 0

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val item = adapter.itemAt(viewHolder.bindingAdapterPosition)
                    if (item is LandingItem.Document) {
                        removeWithUndo(item)
                    }
                }
            }

        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun removeWithUndo(document: LandingItem.Document) {
        val entry = entryFor(document) ?: return

        // where it sits among the documents, which is what restoring it needs - an adapter
        // position would count the section header too
        val index = lastDocuments.indexOfFirst { it.uri == entry.uri }

        mainActivity.analyticsManager.report("recent_removed")

        viewModel.removeRecentDocument(document.uri)

        // the grant it was holding is deliberately not released here: an undo would need it back,
        // and prune() reclaims it on the next launch anyway, which is the whole point of
        // reconciling against the stored list rather than bookkeeping every removal
        SnackbarHelper.show(
            requireActivity(),
            R.string.landing_recent_removed,
            R.string.landing_undo,
            { viewModel.restoreRecentDocument(entry, index) },
            isIndefinite = false,
            isError = false,
        )
    }

    private fun entryFor(document: LandingItem.Document): RecentDocumentList.Entry? {
        val uri = document.uri.toString()

        return lastDocuments.firstOrNull { it.uri == uri }
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

    override fun onDocumentClicked(document: LandingItem.Document) {
        mainActivity.analyticsManager.report(
            AnalyticsConstants.EVENT_SELECT_CONTENT,
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            "recent",
        )

        mainActivity.loadUri(document.uri)
    }

    override fun onActionClicked(action: Int) {
        when (action) {
            LandingItem.ACTION_REMOVE_ADS -> {
                mainActivity.analyticsManager.report("settings_remove_ads")

                mainActivity.buyAdRemoval()
            }

            LandingItem.ACTION_PRIVACY_OPTIONS -> {
                mainActivity.analyticsManager.report("settings_privacy_options")

                mainActivity.showPrivacyOptions()
            }
        }
    }

    override fun onOpenClicked() {
        mainActivity.analyticsManager.report("landing_open")

        mainActivity.findDocument()
    }

    override fun onSectionToggled(section: Int) {
        val expanded =
            when (section) {
                LandingItem.SECTION_INTRO -> viewModel.toggleIntro()
                LandingItem.SECTION_SETTINGS -> viewModel.toggleSettings()
                else -> return
            }

        mainActivity.analyticsManager.report(
            if (section == LandingItem.SECTION_INTRO) "intro_section" else "settings_section",
            AnalyticsConstants.PARAM_CONTENT_TYPE,
            if (expanded) "unfolded" else "folded",
        )
    }

    override fun onSettingChanged(setting: Int, enabled: Boolean) {
        when (setting) {
            LandingItem.SETTING_PAGINATION -> {
                viewModel.setPaginationEnabled(enabled)

                mainActivity.analyticsManager.report(
                    if (enabled) "pagination_enabled" else "pagination_disabled"
                )
            }

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
