package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentServersBinding
import com.v2ray.ang.util.reducedMotion

/**
 * The Серверы tab: every server the app knows about, grouped by provider, with the header actions
 * (collapse all, refresh all, check latency, add) and the search field above it and the empty state
 * behind it.
 *
 * The shell still owns everything a server list is not: the connect state machine, the add/import
 * routes, the whole-list actions behind the header "+", and — because Главная renders the same
 * servers from a second adapter — SELECTION. That is why this class exposes [listAdapter] and a
 * handful of narrow entry points instead of keeping its list to itself: `MainActivity` writes the
 * selected guid once and mirrors it into both lists, and the mirroring must reach this one. When
 * Главная moves to its own fragment those entry points collapse into the shared state that replaces
 * them; until then this is the seam.
 *
 * The tab's fragment is added the first time the tab is opened and then only hidden and shown, so
 * the view can outlive a bind and every public method here checks [isBindingInitialized] first.
 */
class ServersFragment : BaseFragment<FragmentServersBinding>() {

    /**
     * The list's adapter, alive only while the view is. Held so the shell can mirror a selection
     * change, drop a removed row and locate a server without reaching through the view tree.
     */
    private var serversAdapter: MainRecyclerAdapter? = null

    /** @see serversAdapter */
    val listAdapter: MainRecyclerAdapter?
        get() = serversAdapter

    // The reveal stagger plays once per list, the first time the tab is shown with rows in it —
    // never again on scroll or a later notify (see revealListStagger).
    private var serversListRevealed = false

    // Cached easing curve (loaded once) so this list's reveal rides the same ease-out tempo as the
    // rest of the app's motion. No bounce.
    private val easeOutQuint by lazy {
        AnimationUtils.loadInterpolator(requireContext(), R.interpolator.ease_out_quint)
    }

    // Shared motion durations (ms), read from the res/values/motion.xml tempo tokens.
    private val durReveal get() = resources.getInteger(R.integer.motion_reveal).toLong()
    private val durStagger get() = resources.getInteger(R.integer.motion_stagger).toLong()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentServersBinding =
        FragmentServersBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
        setupHeader()
        setupEmptyState()
        applyListInsets()
        // This tab is attached lazily, long after the shell's first list load, so paint from the
        // cache that already exists rather than waiting for the next update to arrive.
        bindList(-1)
        // The view can appear while this tab is ALREADY the selected one — it is attached inside
        // the tab switch itself, and a theme/language recreate restores it before the shell repaints
        // the bar — and in both cases the shell's own reveal call has nothing to reveal yet.
        if (mainHost.selectedTab == MainTab.SERVERS) maybeRevealList()
    }

    override fun onDestroyView() {
        // The adapter belongs to the view: releasing it here keeps a detached tab from holding on
        // to a RecyclerView full of rows it can no longer show.
        serversAdapter = null
        super.onDestroyView()
    }

    /**
     * Creates the grouped all-servers list. The row actions come from the shell
     * ([MainHost.serverActions]) rather than from a listener of our own, so a row behaves
     * identically here and in the Главная preview, which is driven by a second adapter.
     */
    private fun setupList() {
        val adapter = MainRecyclerAdapter(mainViewModel, mainHost.serverActions)
        serversAdapter = adapter
        binding.rvServers.setHasFixedSize(true)
        binding.rvServers.layoutManager = LinearLayoutManager(requireContext())
        addCustomDividerToRecyclerView(binding.rvServers, R.drawable.custom_divider)
        binding.rvServers.adapter = adapter

        // Long-press a server row -> Incy server-actions bottom sheet (S3 moved inline actions here).
        adapter.onItemLongClick = { guid -> mainHost.showServerActions(guid) }
    }

    /** Wires the Servers tab header: title actions and search. */
    private fun setupHeader() {
        val header = binding.layoutServersHeader
        header.btnCollapseAll.setOnClickListener { serversAdapter?.toggleCollapseAll() }
        header.btnRefreshAll.setOnClickListener { mainHost.refreshSubscriptions() }
        // 10.7: the layout ships «Проверить соединение» here, which is the name of the live-tunnel
        // check, not of a pass over the whole list. Named for what it does.
        header.btnSpeedtestAll.contentDescription = getString(R.string.menu_actions_ping_cd)
        header.btnSpeedtestAll.setOnClickListener { mainHost.startLatencyCheckAll() }
        // The Servers tab's single trailing control carries BOTH the add actions and the
        // whole-list actions (32-master-plan-android.md 12.3 gives this header one trailing
        // action holding exactly that set), so its accessible name is the menu it opens, not
        // just "add". The glyph is still "+" until the header is rebuilt; see the report.
        header.btnAdd.contentDescription = getString(R.string.menu_actions_more_cd)
        header.btnAdd.setOnClickListener { mainHost.showAddMenu(it, withListActions = true) }
        header.etSearch.doAfterTextChanged { mainViewModel.filterConfig(it?.toString().orEmpty()) }
    }

    private fun setupEmptyState() {
        binding.layoutEmpty.btnImportClipboard.setOnClickListener { mainHost.importFromClipboard() }
        binding.layoutEmpty.btnScanQr.setOnClickListener { mainHost.importFromQrCode() }
    }

    /**
     * Rebuilds the list from the current cache and refreshes the tab chrome.
     *
     * @param index server index in the cache; when >= 0 only that row is refreshed.
     */
    fun bindList(index: Int) {
        val adapter = serversAdapter ?: return
        adapter.setSections(
            mainViewModel.serversCache,
            mainViewModel.getProviderGroups(),
            showHeaders = true,
            index = index,
        )
        updateServersChrome()
    }

    /** Refreshes the subtitle counts and the empty state without rebuilding the rows. */
    fun refreshChrome() {
        if (!isBindingInitialized) return
        updateServersChrome()
    }

    private fun updateServersChrome() {
        val serverCount = mainViewModel.serversCache.size
        val distinctProviders = mainViewModel.serversCache.map { it.profile.subscriptionId }.distinct().size
        binding.layoutServersHeader.tvServersSubtitle.text =
            getString(R.string.servers_count, serverCount) + " · " +
                getString(R.string.providers_count, maxOf(distinctProviders, 0))

        val filtersActive = mainViewModel.keywordFilter.isNotEmpty()
        val showEmpty = serverCount == 0 && !filtersActive
        binding.layoutEmpty.root.isVisible = showEmpty
        binding.rvServers.isVisible = !showEmpty
    }

    /**
     * The bottom nav overlays tab content, so the list is padded so its last row clears the full
     * nav footprint. The figure itself is the shell's — one inset strategy, one formula.
     */
    fun applyListInsets() {
        if (!isBindingInitialized) return
        binding.rvServers.updatePadding(bottom = mainHost.listBottomInset)
    }

    /** First time the tab is shown with rows in it, plays the reveal stagger (once only). */
    fun maybeRevealList() {
        if (!isBindingInitialized) return
        if (serversListRevealed || mainViewModel.serversCache.isEmpty()) return
        serversListRevealed = true
        revealListStagger(binding.rvServers)
    }

    /** Empties the search field, which reloads the unfiltered cache through the text watcher. */
    fun clearSearch() {
        if (!isBindingInitialized) return
        binding.layoutServersHeader.etSearch.setText("")
    }

    /** The header's trailing control, for anchoring the add menu to it from the shell. */
    fun addMenuAnchor(): View? = if (isBindingInitialized) binding.layoutServersHeader.btnAdd else null

    /**
     * Scrolls the list to [guid], and reports whether it could be reached at all.
     *
     * Two states hide that row without it being gone, and the old code reported both as «не
     * найден»: an active search narrows `serversCache` to the matches, and a collapsed provider
     * group drops its rows out of the adapter's flat list entirely
     * ([MainRecyclerAdapter.positionOfGuid] answers -1 in both cases). Telling the user a server
     * is missing while it sits one tap away reads as a bug rather than as a state, so both are
     * undone before the action is allowed to fail.
     */
    fun locateServer(guid: String): Boolean {
        if (!isBindingInitialized) return false
        val adapter = serversAdapter ?: return false

        // Clearing the field runs the existing text watcher, so MainViewModel.filterConfig()
        // reloads the cache and posts updateListAction synchronously and the adapter is already
        // rebuilt when this returns.
        if (mainViewModel.keywordFilter.isNotEmpty() && adapter.positionOfGuid(guid) < 0) {
            binding.layoutServersHeader.etSearch.setText("")
        }
        // toggleCollapseAll() expands everything only when everything is already collapsed;
        // otherwise it collapses first, so reaching the fully expanded state can take two calls.
        // Both land before the next frame, so the list does not flicker through the intermediate
        // state. It returns early when the list has no group headers at all, and then the two
        // attempts simply cost nothing.
        var position = adapter.positionOfGuid(guid)
        var attempt = 0
        while (position < 0 && attempt < 2) {
            adapter.toggleCollapseAll()
            position = adapter.positionOfGuid(guid)
            attempt++
        }
        if (position < 0) return false
        val target = position
        binding.rvServers.post {
            if (!isBindingInitialized) return@post
            val manager = binding.rvServers.layoutManager as? LinearLayoutManager
            if (manager != null) {
                // A third of the way down rather than flush to the top: the row lands where the
                // eye already is, with its neighbours for context.
                manager.scrollToPositionWithOffset(target, binding.rvServers.height / 3)
            } else {
                binding.rvServers.smoothScrollToPosition(target)
            }
        }
        return true
    }

    /**
     * Reveal stagger for a freshly bound list: each of the first rows rises 12dp and fades in,
     * offset by index * motion_stagger. CAPPED at 8 rows (beyond that rows appear instantly, at
     * rest) so the whole reveal never runs long. Runs once per list (the caller guards with a
     * flag), never on scroll or a later notify. Reduced motion / animations-off: no-op — rows are
     * already at their rest state, so the list simply appears.
     */
    private fun revealListStagger(rv: RecyclerView) {
        if (rv.reducedMotion()) return
        val dy = 12f * resources.displayMetrics.density
        rv.doOnPreDraw {
            val count = minOf(rv.childCount, 8)
            for (i in 0 until count) {
                val child = rv.getChildAt(i)
                child.translationY = dy
                child.alpha = 0f
                child.animate()
                    .translationY(0f).alpha(1f)
                    .setStartDelay(i * durStagger)
                    .setDuration(durReveal)
                    .setInterpolator(easeOutQuint)
                    .start()
            }
        }
    }
}
