package com.v2ray.ang.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountSession
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.template.TemplateManager
import com.v2ray.ang.tv.TvSendActivity
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.animationsEnabled
import com.v2ray.ang.util.tickHaptic
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The four bottom-navigation destinations, in bar order.
 *
 * This is the shared vocabulary between the shell and its tab fragments. [navId] is the bar item's
 * view id — the same value [MainActivity] persists across a theme/language recreate — and [tag] is
 * the `FragmentManager` tag the tab's fragment is added under, which is how a restored instance is
 * found again on recreate instead of being rebuilt from scratch.
 *
 * Every tab is a fragment in the one `tab_host` container now; `activity_main.xml` holds no tab
 * content of its own.
 */
enum class MainTab(@get:IdRes val navId: Int) {
    HOME(R.id.nav_home),
    SERVERS(R.id.nav_servers),
    ACCOUNT(R.id.nav_account),
    SETTINGS(R.id.nav_settings),
    ;

    /** FragmentManager tag for this tab's fragment. Stable across process death. */
    val tag: String get() = "tab:$name"

    companion object {
        /** The tab a bottom-nav item id belongs to, or null for an id that is not a tab. */
        fun fromNavId(@IdRes navId: Int): MainTab? =
            values().firstOrNull { it.navId == navId }
    }
}

/**
 * What a tab fragment is allowed to ask of the shell.
 *
 * [MainActivity] keeps the window, the insets, the bottom bar, the tab switch, the selected server
 * and the import/whole-list actions; a tab owns its own content and reaches the shell only through
 * this interface — never by casting to `MainActivity` and calling into its internals. Shared *state*
 * does not come through here: every tab reads the one `MainViewModel` scoped to the activity
 * (`BaseFragment.mainViewModel`).
 *
 * The three connection calls below are the exception that proves the rule: the connect state
 * machine lives in [HomeFragment], and the shell forwards to it. That is why Главная is attached
 * from launch rather than on first visit (see [MainActivity.syncTabFragments]) — a forward that
 * could land on a fragment that does not exist yet would silently drop a tunnel restart.
 *
 * When a later stage needs something the shell owns and this interface does not expose, add it
 * here rather than widening the cast.
 */
interface MainHost {

    /** The tab currently on screen. */
    val selectedTab: MainTab

    /** Switches tabs, exactly as tapping the bar item does (repaint, haptic). */
    fun selectTab(tab: MainTab)

    /**
     * Connect or disconnect, whichever the current tunnel state calls for — the hero disc's own
     * action, including the VPN-permission prompt, the connect watchdog and the status pill.
     */
    fun toggleConnection()

    /**
     * Stops the running tunnel and starts it again on the currently selected server. Waits for a
     * real stopped state rather than a fixed delay; see `HomeFragment.restartV2Ray`.
     */
    fun restartConnection()

    /** The transient status pill («Подключение…», «Отключено», …). */
    fun showStatus(text: CharSequence)

    /**
     * Recomputes the bottom bar's own gates: whether the Аккаунт item exists (signed in only) and
     * whether the whole bar exists (hidden in the pure onboarding state — signed out AND no
     * servers). Both read shell state only, so the bar stays the shell's alone; a tab calls this
     * after doing something that can change either input.
     */
    fun refreshNavGates()

    /**
     * Bottom padding a tab's scrolling list needs so its last row clears the overlaid bottom nav:
     * the system inset plus the bar itself plus breathing room. The shell computes it once, from
     * the window insets, and every list that needs it asks for it here rather than re-deriving the
     * same figure from its own inset listener.
     */
    val listBottomInset: Int

    /**
     * The per-server row actions — select, edit, share, remove. ONE instance for every server list
     * in the app, because a row must behave the same in the Серверы tab and in the Главная preview,
     * which are two adapters over the same servers.
     */
    val serverActions: MainAdapterListener

    /** Opens the server-actions sheet for one server (the row long-press entry point). */
    fun showServerActions(guid: String)

    /**
     * Opens the add-source popup anchored to [anchor]. [withListActions] adds the whole-list group
     * (sort / export / the three bulk deletes) — only the Серверы header passes it.
     */
    fun showAddMenu(anchor: View, withListActions: Boolean)

    /** «Обновить подписки»: re-fetches every subscription and reloads the list. */
    fun refreshSubscriptions()

    /** «Проверить задержку» across the whole list, with the recovery actions for an empty one. */
    fun startLatencyCheckAll()

    /** Imports whatever the clipboard holds: a share link, a subscription URL or a raw config. */
    fun importFromClipboard()

    /** Opens the QR scanner and imports what it reads. */
    fun importFromQrCode()

    /**
     * Opens a settings sub-screen and applies whatever it changed on the way back: a theme or
     * language change recreates the activity, a core-config change restarts a running tunnel, a
     * group change reloads the server list.
     *
     * The tab could register a launcher of its own, but the three consume-flags
     * (`SettingsChangeManager`) are the shell's contract — recreating the activity is not a
     * fragment's to do, and two copies of that body would drift. So the one launcher stays here
     * and the tab hands it an intent.
     */
    fun launchSettingsScreen(intent: Intent)

    /**
     * Opens the sign-in / link-Telegram screen through that SAME launcher, so a login that changes
     * the theme, the core config or the server groups is applied on the way back exactly as a
     * settings sub-screen is.
     *
     * Named apart from [launchSettingsScreen] because it is not a settings sub-screen — the two
     * share one launcher deliberately, and a later stage that can touch both callers may collapse
     * them into one honestly-named call.
     */
    fun launchAuthScreen(intent: Intent)
}

class MainActivity : HelperBaseActivity(), MainHost {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()

    /**
     * The Главная tab's fragment. Attached from launch (see [syncTabFragments]), so this is null
     * only before `onCreate` has wired the bar and after the activity is gone. Looked up by tag on
     * every access rather than cached in a field, so the instance the FragmentManager restores
     * after a theme/language recreate is found too.
     */
    private val homeFragment: HomeFragment?
        get() = supportFragmentManager.findFragmentByTag(MainTab.HOME.tag) as? HomeFragment

    /**
     * The Серверы tab's fragment, or null until that tab has been opened for the first time — every
     * tab but Главная is added lazily (see [syncTabFragments]).
     */
    private val serversFragment: ServersFragment?
        get() = supportFragmentManager.findFragmentByTag(MainTab.SERVERS.tag) as? ServersFragment

    /**
     * The Серверы tab's adapter, or null while that tab has no view.
     *
     * The shell owns the selected server, because Главная renders the same servers from a second
     * adapter and one selection has to reach both lists. This is the Серверы half of that mirror;
     * the Главная half is reached through [HomeFragment.onSelectedServerChanged].
     */
    private val serversAdapter: MainRecyclerAdapter?
        get() = serversFragment?.listAdapter

    /**
     * The one row-action listener shared by every server list (see [MainHost.serverActions]).
     */
    private val adapterListener: ActivityAdapterListener by lazy { ActivityAdapterListener() }

    /** Last computed bottom-nav padding for a tab's scrolling list; see [MainHost.listBottomInset]. */
    private var navListPadding = 0

    private val shareMethod: Array<out String> by lazy { resources.getStringArray(R.array.share_method) }
    private val shareMethodMore: Array<out String> by lazy { resources.getStringArray(R.array.share_method_more) }

    // Cached easing curve (loaded once) so the imperative nav motion rides the same ease-out tempo
    // as the declarative res/interpolator + res/anim resources. No bounce.
    private val easeStandard by lazy { AnimationUtils.loadInterpolator(this, R.interpolator.ease_standard) }

    private companion object {
        // Remembers which bottom-nav tab was selected so it survives an activity
        // recreate (theme/language change) instead of snapping back to Home.
        const val KEY_SELECTED_NAV = "selected_bottom_nav"
    }

    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRecreateUi()) {
            recreate()
            return@registerForActivityResult
        }
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartConnection()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            mainViewModel.reloadServerList()
        }
    }

    /**
     * Launcher for the manual server editors (ServerActivity / ServerGroupActivity /
     * ServerProxyChainActivity).
     *
     * Separate from [requestActivityLauncher] because saving a server sets no
     * [SettingsChangeManager] flag other than restart-service, so a shared launcher would return
     * without reloading and the just-created server would stay invisible until some other reload
     * happened to fire. Here the list is reloaded unconditionally.
     */
    private val createServerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartConnection()
        }
        mainViewModel.reloadServerList()
        homeFragment?.refreshSelectedServer()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        applyThemeDecorations()
        setupEdgeToEdge()

        // Главная always shows the inline up/down speed row, so the traffic-stats pipeline must be
        // on: without this the core config omits the stats outbound and the speed-notification loop
        // never runs, so the row is stuck on «0 KB/s». Enabled here, in the shell, because it has to
        // be true before ANY connect — including one started from the quick-settings tile, which
        // never opens a tab.
        MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, true)

        // All servers are shown in one flat, provider-grouped list (no subscription tabs).
        mainViewModel.subscriptionId = ""

        // Keep the user on the tab they were on when the activity is recreated (e.g. after a theme
        // or language change) instead of jumping back to Home. Handed to setupBottomNav so the
        // restored tab is the FIRST one painted: selecting Home and then correcting it would run
        // two fragment transactions and a tab swap the user never asked for.
        setupBottomNav(savedInstanceState?.getInt(KEY_SELECTED_NAV, R.id.nav_home) ?: R.id.nav_home)
        // The one BACK handler in the shell: any other tab goes to Главная first, and Главная
        // minimises. See onKeyDown for why nothing else may handle the key.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selectedNavId != R.id.nav_home ->
                        selectNav(R.id.nav_home)

                    // Keep the upstream semantic on Главная: minimise the task, leaving the
                    // tunnel running and the app in Recents, rather than finishing the activity.
                    else -> moveTaskToBack(false)
                }
            }
        })

        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    /** Persist the selected tab so a theme/language recreate does not reset it to Home. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV, selectedNavId)
    }

    /**
     * Wires the bottom navigation and paints [initialNav] as the first tab on screen. Every tab is
     * a fragment in the shared container; Главная is attached here whichever tab is showing.
     */
    private fun setupBottomNav(initialNav: Int) {
        // The custom bar is a plain LinearLayout with no fitsSystemWindows behaviour, so it never
        // auto-pads itself; setupEdgeToEdge's parent listener is the single source of its bottom
        // inset padding. (A no-op listener returning the insets unchanged used to sit here.)
        binding.navHome.setOnClickListener { selectNav(R.id.nav_home) }
        binding.navServers.setOnClickListener { selectNav(R.id.nav_servers) }
        binding.navSettings.setOnClickListener { selectNav(R.id.nav_settings) }
        // The Account item is a real in-place content tab (AccountFragment), selected like the
        // others; its fragment is attached lazily the first time it is opened (see syncTabFragments).
        binding.navAccount.setOnClickListener { selectNav(R.id.nav_account) }
        // Аккаунт exists only while signed in, so a restored selection of it is honoured only if
        // that is still true — otherwise the tab would be attached (and would start loading) for a
        // user refreshNavGates is about to move off it anyway.
        val start = if (initialNav == R.id.nav_account && !accountAccessAllowed()) {
            R.id.nav_home
        } else {
            initialNav
        }
        // Not selectNav: the first paint is not a tab CHANGE, so it takes showTab's previous == tab
        // path (no haptic) and lands on the restored tab in one transaction.
        selectedNavId = start
        updateNavSelection(start)
        showTab(start, start)
    }

    /** Currently selected bottom-nav tab (replaces BottomNavigationView.selectedItemId). */
    private var selectedNavId = R.id.nav_home

    /** Selects a bottom-nav tab: repaints the custom bar and swaps the visible tab content. */
    private fun selectNav(navId: Int) {
        val previous = selectedNavId
        selectedNavId = navId
        updateNavSelection(previous)
        showTab(navId, previous)
    }

    // ==================== MainHost ====================

    override val selectedTab: MainTab
        get() = MainTab.fromNavId(selectedNavId) ?: MainTab.HOME

    override fun selectTab(tab: MainTab) = selectNav(tab.navId)

    // The connect state machine lives in Главная (HomeFragment), which is attached from launch, so
    // these three forwards always land — see MainHost's own note.
    override fun toggleConnection() {
        homeFragment?.toggleConnection()
    }

    override fun restartConnection() {
        homeFragment?.restartConnection()
    }

    override fun showStatus(text: CharSequence) {
        homeFragment?.showStatus(text)
    }

    /**
     * The Аккаунт item exists only while signed in, and the whole bar only once there is something
     * to navigate to. Both inputs are shell state, so both are computed here; see [updateAccountNav]
     * and [updateBottomNavVisibility].
     */
    override fun refreshNavGates() {
        updateAccountNav()
        updateBottomNavVisibility()
    }

    override val listBottomInset: Int
        get() = navListPadding

    override val serverActions: MainAdapterListener
        get() = adapterListener

    override fun showAddMenu(anchor: View, withListActions: Boolean) =
        showImportMenu(anchor, withListActions)

    override fun refreshSubscriptions() {
        importConfigViaSub()
    }

    override fun importFromClipboard() {
        importClipboard()
    }

    override fun importFromQrCode() {
        importQRcode()
    }

    override fun launchSettingsScreen(intent: Intent) {
        requestActivityLauncher.launch(intent)
    }

    override fun launchAuthScreen(intent: Intent) {
        requestActivityLauncher.launch(intent)
    }

    /**
     * Routes subscription add/refresh progress onto Главная's connect circle (the shared rotating
     * arc) instead of a top progress bar — the shell's own imports and bulk actions report through
     * the same indicator the tab uses. Overrides the BaseActivity top-bar spinner.
     */
    override fun showLoading() {
        runOnUiThread { homeFragment?.showConnectArc() }
    }

    override fun hideLoading() {
        runOnUiThread { homeFragment?.hideConnectArc() }
    }

    /**
     * Tints the custom bar items: blue (colorPrimary) for the selected one, grey otherwise. On a
     * real tab change (animations enabled) the two involved items TWEEN their icon+label colour
     * grey↔blue over motion_state; everything else (initial paint, reduced motion, unchanged items)
     * sets the colour instantly.
     */
    private fun updateNavSelection(previousNavId: Int = selectedNavId) {
        val active = themeColor(androidx.appcompat.R.attr.colorPrimary)
        val inactive = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val items = listOf(
            Triple(R.id.nav_home, binding.navHomeIcon, binding.navHomeLabel),
            Triple(R.id.nav_servers, binding.navServersIcon, binding.navServersLabel),
            Triple(R.id.nav_settings, binding.navSettingsIcon, binding.navSettingsLabel),
            // The Account tab tints blue when selected, exactly like the other tabs.
            Triple(R.id.nav_account, binding.navAccountIcon, binding.navAccountLabel),
        )
        val animate = animationsEnabled() && previousNavId != selectedNavId
        items.forEach { (id, icon, label) ->
            val selected = id == selectedNavId
            val target = if (selected) active else inactive
            val involved = selected || id == previousNavId
            if (animate && involved) {
                val from = if (selected) inactive else active
                tweenNavItemColor(icon, label, from, target)
            } else {
                icon.setColorFilter(target)
                label.setTextColor(target)
            }
            // Second active-state axis (beyond the colour tween): a heavier label and a
            // short blue pill under the selected item, so the active tab reads on weight
            // + accent, not tint alone.
            applyNavLabelWeight(label, selected)
            // INVISIBLE (not GONE) for inactive items so every column keeps the 3dp pill
            // slot and nothing shifts vertically as the selection moves.
            navDot(id)?.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
    }

    /** The active-tab indicator pill under a nav item (null for an unknown id). */
    private fun navDot(navId: Int): View? = when (navId) {
        R.id.nav_home -> binding.navHomeDot
        R.id.nav_servers -> binding.navServersDot
        R.id.nav_settings -> binding.navSettingsDot
        R.id.nav_account -> binding.navAccountDot
        else -> null
    }

    /** Steps a nav label to 700 when selected and 500 otherwise (real numeric weight on
     *  API 28+, bold/normal fallback below that). */
    private fun applyNavLabelWeight(label: android.widget.TextView, selected: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val family = android.graphics.Typeface.create(label.typeface, android.graphics.Typeface.NORMAL)
            label.typeface = android.graphics.Typeface.create(family, if (selected) 700 else 500, false)
        } else {
            label.setTypeface(
                label.typeface,
                if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
        }
    }

    /** Tweens one nav item's icon tint + label colour from -> to over motion_state (ease-out). */
    private fun tweenNavItemColor(
        icon: android.widget.ImageView,
        label: android.widget.TextView,
        from: Int,
        to: Int,
    ) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 200
            interpolator = easeStandard
            addUpdateListener {
                val c = it.animatedValue as Int
                icon.setColorFilter(c)
                label.setTextColor(c)
            }
            start()
        }
    }

    /**
     * The fragment that owns [tab].
     *
     * Called ONLY when the FragmentManager has no instance under [MainTab.tag] — a tab is built
     * once per process and then kept, so this is not a place to pass per-open arguments.
     *
     * Every tab is a fragment now. Anything a tab needs from the shell — the connection actions,
     * the status pill, the nav gates, the list inset, the row actions, the result launchers — is on
     * [MainHost], never reached for by casting.
     */
    private fun createTabFragment(tab: MainTab): Fragment = when (tab) {
        MainTab.HOME -> HomeFragment()
        MainTab.ACCOUNT -> AccountFragment()
        MainTab.SERVERS -> ServersFragment()
        MainTab.SETTINGS -> SettingsTabFragment()
    }

    /**
     * Brings [navId]'s fragment on screen and takes the others off it, **keeping every instance
     * added**: a tab is `add`ed once, then only ever hidden and shown again.
     *
     * That is the whole reason this is not `replace()`. A replaced fragment is destroyed and
     * rebuilt on the way back, which throws away scroll position, a half-typed field and any
     * request still in flight; hide/show leaves the view hierarchy and the fragment's lifecycle
     * state untouched, so returning to a tab returns to it exactly as it was left. Hidden
     * fragments stay RESUMED, so nothing that was running keeps running any differently.
     *
     * **Главная is the exception to lazy attachment**: it is added on the very first call whatever
     * tab is selected, hidden if that tab is not it. It carries the connect state machine — the
     * tunnel observer, the status pill, the health check and the restart the Настройки tab asks for
     * after a core-config change — and none of that may wait for the user to visit the tab. A
     * theme/language recreate that restores a non-Главная tab would otherwise leave the app with no
     * state machine at all.
     *
     * On a theme/language recreate the FragmentManager restores each tab's fragment (and its
     * hidden flag) under the same tag before this runs, so the lookup finds the restored instance
     * and [createTabFragment] is never called for it.
     */
    private fun syncTabFragments(navId: Int) {
        val fm = supportFragmentManager
        // After onSaveInstanceState a commit is illegal; the restored activity will re-run this
        // from its own onCreate, so there is nothing to lose by skipping it.
        if (fm.isStateSaved) return
        val tx = fm.beginTransaction()
        var changed = false
        for (candidate in MainTab.values()) {
            val existing = fm.findFragmentByTag(candidate.tag)
            if (candidate.navId == navId) {
                if (existing == null) {
                    tx.add(R.id.tab_host, createTabFragment(candidate), candidate.tag)
                    changed = true
                } else if (existing.isHidden) {
                    tx.show(existing)
                    changed = true
                }
            } else if (existing == null) {
                // Only Главная is attached without being selected; every other tab waits for its
                // first visit. Added and hidden in this same transaction, so it is never on screen.
                if (candidate != MainTab.HOME) continue
                val fragment = createTabFragment(candidate)
                tx.add(R.id.tab_host, fragment, candidate.tag)
                tx.hide(fragment)
                changed = true
            } else if (!existing.isHidden) {
                tx.hide(existing)
                changed = true
            }
        }
        // commitNow, not commit: the tab swap below reads the container in this same frame, and a
        // posted transaction would show it empty for one frame first.
        if (changed) tx.commitNow()
    }

    /**
     * The tab-content view for a nav id (null for an unknown id). All four tabs are fragments in
     * the one container, so this answers the same view for each — it stays a lookup because
     * [showTab] must still tell a real tab id from an unknown one.
     */
    private fun tabGroup(navId: Int): View? = when (navId) {
        R.id.nav_home, R.id.nav_servers, R.id.nav_settings, R.id.nav_account -> binding.tabHost
        else -> null
    }

    /** Every tab-content view the shell can show, so exactly one is left visible. */
    private fun tabGroups(): List<View> = listOf(binding.tabHost)

    /**
     * The single authority on which tab group is on screen: [visible] is shown and every other
     * group is hidden.
     */
    private fun settleTabs(visible: View?) {
        tabGroups().forEach { it.isVisible = it === visible }
    }

    /**
     * Swaps the tab content: the incoming tab's fragment is added or shown and every other one is
     * hidden, in a single transaction.
     *
     * There is no crossfade left to run. With all four tabs inside `tab_host` the outgoing and
     * incoming views are the same container, so the old group-level fade had nothing to fade
     * between; the pair of fragments inside it can be crossfaded instead, but that is a motion
     * change and belongs to the stage that owns motion (32-master-plan-android.md 9.3 asks for a
     * simultaneous 220 ms crossfade for every tab switch). The tick haptic that marked a switch is
     * kept, so the change is still felt.
     */
    private fun showTab(tab: Int, previous: Int = tab) {
        val incoming = tabGroup(tab)
        syncTabFragments(tab)
        settleTabs(incoming)
        if (previous != tab) binding.bottomNav.tickHaptic()
        maybeRevealServersTab(tab)
    }

    /**
     * First time the Servers tab is shown with rows, plays the reveal stagger (once only). The
     * once-only flag and the stagger itself live with the list, in [ServersFragment]; this is only
     * the moment the tab comes on screen, which the shell is the one to know.
     */
    private fun maybeRevealServersTab(tab: Int) {
        if (tab == R.id.nav_servers) serversFragment?.maybeRevealList()
    }

    /**
     * True edge-to-edge: the home gradient (home_root) draws behind the status and nav
     * bars; each tab's content receives the top inset (so it clears the clock) and the
     * bottom nav a bottom inset pad (so items clear the gesture bar). The bars themselves
     * stay transparent (handled by the theme, not touched here).
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // There is no app bar: every tab draws its own header, so each tab's content clears the
            // status bar itself — the Home scroll header, and the Settings first section, both start
            // just below the clock with no empty band above them. The fragment container is padded
            // once, here, on behalf of every tab it hosts — a tab fragment does not repeat the top
            // inset for itself.
            binding.tabHost.updatePadding(top = bars.top)
            // Pad the custom bar by the FULL bottom inset so its icons/labels sit ABOVE whatever the
            // system draws: the ~48dp of Android 3-button navigation, or the ~24dp gesture pill. The
            // bar has wrap_content height (min 56dp) and hugs the bottom, so this padding grows it
            // UPWARD, lifting the content clear of the buttons. The bottom_nav_scrim and home gradient
            // keep flowing behind it, so there is no black bar in either nav mode.
            val density = resources.displayMetrics.density
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            // The nav overlays the content, so pad the scrollable lists so the last row clears the
            // full nav footprint: the system inset + the 56dp bar content + 16dp breathing room.
            val navHeightPx = (56 * density).toInt()
            val breathingPx = (16 * density).toInt()
            val navPad = bars.bottom + navHeightPx + breathingPx
            // Published for the tabs that own their own lists (see MainHost.listBottomInset) and
            // pushed straight into the ones already attached — insets are not re-dispatched just
            // because a fragment was added, and a fragment added later reads the field itself.
            navListPadding = navPad
            homeFragment?.applyListInsets()
            serversFragment?.applyListInsets()
            insets
        }
    }

    /**
     * The shell's own ViewModel wiring: the service broadcast listener, the bundled assets, and the
     * one list signal that has to reach TWO tabs. Everything a single tab cares about — the speed
     * feed, the tunnel state, the latency results — is observed by that tab.
     */
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            val position = index ?: -1
            // Both server lists are driven by the one cache; each tab rebinds its own, and a tab
            // that has no view yet paints itself from the same cache when it gets one.
            serversFragment?.bindList(position)
            homeFragment?.bindList(position)
            // Adding or removing a subscription can change whether there is anything to navigate
            // to, and (with a departament subscription) whether the Аккаунт item belongs there.
            refreshNavGates()
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    /**
     * Opens the Incy server-actions bottom sheet for [guid] (long-press entry point).
     * Each action delegates to an existing per-server flow; duplicate reuses
     * [MmkvManager.encodeServerConfig] with a blank guid to mint a fresh copy.
     */
    override fun showServerActions(guid: String) {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return
        ServerActionsSheet(
            context = this,
            profile = profile,
            onShareQr = { showQRCode(guid) },
            onShareClipboard = { share2Clipboard(guid) },
            onEdit = { editServer(guid, profile) },
            onDuplicate = {
                val copy = profile.copy(
                    remarks = profile.remarks + getString(R.string.server_action_duplicate_suffix),
                    addedTime = System.currentTimeMillis()
                )
                MmkvManager.encodeServerConfig("", copy)
                mainViewModel.reloadServerList()
            },
            onSetDefault = { setSelectServer(guid) },
            onDelete = { removeServer(guid, serversAdapter?.positionOfGuid(guid) ?: -1) },
        ).show()
    }

    /**
     * Popup for the header controls, anchored to the tapped button.
     *
     * [withListActions] adds the whole-list group (sort / export / the three bulk deletes). Only
     * the Servers tab passes it: Главная owns adding a source, not managing the list.
     */
    private fun showImportMenu(anchor: android.view.View, withListActions: Boolean = false) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        // Icons in a PopupMenu are hidden unless forced, and the drawables ship in mixed
        // black/white fills, so each visible item is tinted from the theme below.
        popup.setForceShowIcon(true)
        MenuCompat.setGroupDividerEnabled(popup.menu, true)
        prepareMenu(popup.menu, withListActions)
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    /**
     * Decides what the popup may offer, so no item in it can be a dead end:
     *
     * - the whole-list group is hidden entirely when there is nothing to act on (no servers) or
     *   when the surface does not own list actions;
     * - «Экспортировать в буфер» is disabled when every server is operator-locked, because
     *   `AngConfigManager.shareConfig()` refuses to emit a link for those by design;
     * - «Найти выбранный сервер» is hidden unless a selected server actually exists in the store,
     *   because with none the item could only ever announce its own uselessness;
     * - the three deletions are painted in `colorError` (1.4.1: red is destructive only) and sit
     *   behind the group divider, so the destructive half of the menu is legible before a tap.
     */
    private fun prepareMenu(menu: Menu, withListActions: Boolean) {
        val neutral = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val destructive = themeColor(androidx.appcompat.R.attr.colorError)

        val hasServers = mainViewModel.serversCache.isNotEmpty()
        menu.setGroupVisible(R.id.group_server_list, withListActions && hasServers)

        val exportable = mainViewModel.serversCache.any { !TemplateManager.isLocked(it.profile) }
        menu.findItem(R.id.servers_export)?.isEnabled = exportable

        // Read the store, not `serversCache`: the cache is narrowed by the search field, and a
        // selection the search happens to hide is exactly the case «Найти выбранный сервер» is
        // for. Runs after setGroupVisible, which writes every item in the group.
        val selectedGuid = MmkvManager.getSelectServer()
        val hasSelection = !selectedGuid.isNullOrEmpty() && MmkvManager.decodeServerConfig(selectedGuid) != null
        menu.findItem(R.id.servers_locate)?.isVisible = withListActions && hasServers && hasSelection

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!item.isVisible) continue
            val isDestructive = item.itemId == R.id.servers_del_duplicate ||
                item.itemId == R.id.servers_del_invalid ||
                item.itemId == R.id.servers_del_all
            paintMenuItem(item, if (isDestructive) destructive else neutral, tintTitle = isDestructive)
        }
    }

    /** Tints one menu item's glyph (and its label, for destructive items) from the theme. */
    private fun paintMenuItem(item: MenuItem, color: Int, tintTitle: Boolean) {
        item.icon?.let { icon ->
            val glyph = icon.mutate()
            DrawableCompat.setTint(glyph, color)
            // Disabled = 0.38 on the whole control (00-rules.md 7.1); the label is greyed by the
            // menu itself, the glyph is not.
            glyph.alpha = if (item.isEnabled) 255 else 97
            item.icon = glyph
        }
        if (tintTitle) {
            val label = SpannableString(item.title ?: "")
            label.setSpan(ForegroundColorSpan(color), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = label
        }
    }

    /**
     * Hides the whole bottom nav (bar + scrim) in the pure onboarding state — signed out AND no
     * servers — so the sign-in screen reads as a clean solid background with no tab buttons. It
     * returns as soon as EITHER is true (logged in OR at least one server). The nav is an overlay
     * in a FrameLayout (it never reserved layout space), so hiding it leaves no phantom bottom gap.
     * When it reappears we guarantee a valid selected tab (the Account tab is only valid while
     * signed in; otherwise fall back to Home).
     */
    private fun updateBottomNavVisibility() {
        val show = AccountSession.isLoggedIn() || mainViewModel.serversCache.isNotEmpty()
        val becomingVisible = show && !binding.bottomNav.isVisible
        binding.bottomNav.isVisible = show
        binding.bottomNavScrim.isVisible = show
        if (becomingVisible) {
            val valid = selectedNavId == R.id.nav_home ||
                selectedNavId == R.id.nav_servers ||
                selectedNavId == R.id.nav_settings ||
                (selectedNavId == R.id.nav_account && accountAccessAllowed())
            if (!valid) selectTabWhenIdle(R.id.nav_home)
        }
    }

    /**
     * The account gate: the account tab and every account-only entry point exist ONLY after the user
     * has authorized (logged in). A pasted/imported subscription — even a genuine "departament" one —
     * must NOT unlock the account, since there is no account to load without a login.
     */
    private fun accountAccessAllowed(): Boolean =
        AccountSession.isLoggedIn()

    /**
     * Recomputes the visibility of the Аккаунт nav item from that gate. Called whenever the account
     * state changes (login/logout) and whenever the subscription / server list changes — a pasted
     * subscription never unlocks the tab. Главная applies the same gate to its account chip
     * (`HomeFragment.applyAccountHeaderGate`); the bar itself is the shell's.
     */
    private fun updateAccountNav() {
        if (!BackendConfig.isConfigured()) {
            binding.navAccount.isVisible = false
            return
        }
        val loggedIn = AccountSession.isLoggedIn()
        binding.navAccount.isVisible = loggedIn
        // Signed out while on the Account tab: the tab is hidden, so fall back to Home.
        if (!loggedIn && selectedNavId == R.id.nav_account) selectTabWhenIdle(R.id.nav_home)
    }

    /**
     * Selects [navId] on the next loop, not inline.
     *
     * The two gates above run from a tab fragment's own lifecycle callbacks (`onViewCreated`,
     * `onResume`), and those run INSIDE the FragmentManager's dispatch, where committing another
     * transaction throws «FragmentManager is already executing transactions». The correction is a
     * frame later, which no one can see, and it is a no-op if the tab is already right by then.
     */
    private fun selectTabWhenIdle(@IdRes navId: Int) {
        binding.bottomNav.post {
            if (isFinishing || isDestroyed) return@post
            if (selectedNavId != navId) selectNav(navId)
        }
    }


    // ---- Per-server actions (moved from GroupServerFragment) ----

    private fun shareServer(guid: String, profile: ProfileItem, position: Int, shareOptions: List<String>, skip: Int) {
        AlertDialog.Builder(this).setItems(shareOptions.toTypedArray()) { _, i ->
            try {
                when (i + skip) {
                    0 -> showQRCode(guid)
                    1 -> share2Clipboard(guid)
                    2 -> shareFullContent(guid)
                    3 -> editServer(guid, profile)
                    4 -> removeServer(guid, position)
                    else -> {}
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error when sharing server", e)
            }
        }.show()
    }

    private fun showQRCode(guid: String) {
        val ivBinding = ItemQrcodeBinding.inflate(layoutInflater)
        ivBinding.ivQcode.setImageBitmap(AngConfigManager.share2QRCode(guid))
        ivBinding.ivQcode.contentDescription = shareMethod.firstOrNull() ?: "QR Code"
        AlertDialog.Builder(this).setView(ivBinding.root).show()
    }

    private fun share2Clipboard(guid: String) {
        if (AngConfigManager.share2Clipboard(this, guid) == 0) toastSuccess(R.string.toast_success)
        else toastError(R.string.toast_failure)
    }

    private fun shareFullContent(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        // Managed/hidden profiles cannot be opened in an editor (would reveal the config).
        if (TemplateManager.isLocked(profile)) {
            toast(R.string.template_locked_toast)
            return
        }
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            else -> ServerActivity::class.java
        }
        val intent = Intent(this, activityClass)
            .putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", profile.subscriptionId)
        requestActivityLauncher.launch(intent)
    }

    private fun removeServer(guid: String, position: Int) {
        if (guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ -> removeServerSub(guid, position) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
        } else {
            removeServerSub(guid, position)
        }
    }

    private fun removeServerSub(guid: String, position: Int) {
        mainViewModel.removeServer(guid)
        serversAdapter?.removeServerSub(guid, position)
        homeFragment?.removeServerRow(guid, position)
        serversFragment?.refreshChrome()
    }

    /**
     * Tapping a server row SELECTS it — it never connects and never reconnects.
     *
     * Connecting is the connect button's job alone. When a tunnel is already up and the user picks a
     * different server, the running tunnel is left untouched and an explicit "apply it" action is
     * offered instead, so a tap in the list can never silently tear down a working connection.
     */
    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid == selected) return

        MmkvManager.setSelectServer(guid)
        serversAdapter?.setSelectServer(selected, guid)
        // The Главная half of the same mirror: its list row, its under-shield label and the
        // subscription card its carousel is showing.
        homeFragment?.onSelectedServerChanged(selected, guid)
        if (mainViewModel.isRunning.value == true) {
            promptApplySelectedServer(guid)
        }
    }

    /**
     * Offers to move an already-running tunnel onto the newly selected server. Declining leaves the
     * connection exactly as it was — the selection is remembered for the next connect.
     */
    private fun promptApplySelectedServer(guid: String) {
        val name = MmkvManager.decodeServerConfig(guid)?.remarks.orEmpty()
        val message = if (name.isBlank()) {
            getString(R.string.server_selected_reconnect_prompt_generic)
        } else {
            getString(R.string.server_selected_reconnect_prompt, FlagUtil.stripLeadingFlag(name))
        }
        Snackbar.make(binding.mainContent, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.bottomNav)
            // The restart runs through the connect state machine, so a stalled one is reported like
            // any other failed start rather than leaving the hero on the old server.
            .setAction(R.string.server_selected_reconnect_action) {
                homeFragment?.applySelectionToRunningTunnel()
            }
            .show()
    }

    private inner class ActivityAdapterListener : MainAdapterListener {
        override fun onEdit(guid: String, position: Int) {}
        override fun onShare(url: String) {}
        override fun onRefreshData() {}
        override fun onRemove(guid: String, position: Int) { removeServer(guid, position) }
        override fun onEdit(guid: String, position: Int, profile: ProfileItem) { editServer(guid, profile) }
        override fun onSelectServer(guid: String) { setSelectServer(guid) }
        override fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean) {
            // Managed/hidden profile: expose only removal; block QR/share/full-config/edit.
            if (TemplateManager.isLocked(profile)) {
                shareServer(
                    guid, profile, position,
                    listOf(getString(R.string.template_locked_action_remove)), skip = 4
                )
                return
            }
            val isCustom = profile.configType.isComplexType()
            val (shareOptions, skip) = if (more) {
                val options = if (isCustom) shareMethodMore.asList().takeLast(3) else shareMethodMore.asList()
                options to if (isCustom) 2 else 0
            } else {
                val options = if (isCustom) shareMethod.asList().takeLast(1) else shareMethod.asList()
                options to if (isCustom) 2 else 0
            }
            shareServer(guid, profile, position, shareOptions, skip)
        }
    }

    /**
     * The blue/light and blue/dark backgrounds are theme-qualified drawables, but the mono
     * overlay is a runtime style overlay (not a resource qualifier), so the decorative home
     * gradient must be swapped to its neutral grey variant here when mono is active. (The hero's
     * glow and ring live in Главная; `HomeFragment.applyThemeDecorations` swaps those.)
     */
    private fun applyThemeDecorations() {
        val mono = MmkvManager.decodeSettingsString(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE) == BaseActivity.THEME_MONO
        if (!mono) return
        binding.homeRoot.setBackgroundResource(R.drawable.bg_home_gradient_mono)
    }

    /**
     * Resolves a themed color attribute (respects the active blue/mono overlay).
     */
    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.mainContent, attr)

    override fun onResume() {
        super.onResume()
        // Other entry points change the selected server without owning this list — the URL-scheme
        // and shortcut activities, and the quick tile. Re-reading it here keeps the rows honest
        // instead of leaving a stale one painted as selected. (Главная re-reads its own list, and
        // its account chip, in HomeFragment.onResume — a hidden tab is still RESUMED, so every tab
        // refreshes itself without the shell reaching into it.)
        serversAdapter?.syncSelection()
        // A login or a subscription change from another screen can add or remove the Аккаунт item
        // and, in the onboarding state, the bar itself.
        refreshNavGates()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // No toolbar action menu: the shell has no app bar at all, so the tab header controls own
        // the menu. Both open menu_main as a PopupMenu via showImportMenu (Главная: the add group
        // only; Серверы: add + whole-list actions), and the Settings tab has no control at all.
        // onOptionsItemSelected is the shared dispatch for those PopupMenus, so it stays.
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.tv_send -> {
            startActivity(Intent(this, TvSendActivity::class.java))
            true
        }

        R.id.import_manually_vless -> {
            // «Ввести ссылку» - a simple text-input dialog where the user pastes/types a config
            // or subscription link by hand; the entered text is imported via the same path as
            // pasted clipboard text (importBatchConfig).
            showManualEntryDialog()
            true
        }

        R.id.import_create -> {
            // «Создать вручную» - pick the protocol, then fill in the server by hand.
            pickManualServerType()
            true
        }

        R.id.import_file -> {
            importConfigLocal()
            true
        }

        R.id.servers_locate -> {
            locateSelectedServer()
            true
        }

        R.id.servers_sort -> {
            sortByTestResults()
            true
        }

        R.id.servers_export -> {
            exportAll()
            true
        }

        R.id.servers_del_duplicate -> {
            delDuplicateConfig()
            true
        }

        R.id.servers_del_invalid -> {
            delInvalidConfig()
            true
        }

        R.id.servers_del_all -> {
            delAllConfig()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    /**
     * «Создать вручную»: choose what is being created, then open its editor.
     *
     * The editors pick their layout from `createConfigType` and give the user no way to change it
     * afterwards ([ServerActivity] onCreate maps the type to a layout and finishes silently for a
     * type it has none for), so the type has to be settled here. Only types with a real editor are
     * offered: CUSTOM is deliberately absent because [ServerActivity] would open blank for it, and
     * a raw xray-json body already imports through «Ввести ссылку». A proxy chain is offered only
     * when there are at least two plain servers to chain, which is what
     * [ServerProxyChainActivity.saveServer] requires.
     */
    private fun pickManualServerType() {
        val types = mutableListOf(
            "VLESS" to EConfigType.VLESS,
            "VMess" to EConfigType.VMESS,
            "Trojan" to EConfigType.TROJAN,
            "Shadowsocks" to EConfigType.SHADOWSOCKS,
            "WireGuard" to EConfigType.WIREGUARD,
            "Hysteria2" to EConfigType.HYSTERIA2,
            "SOCKS5" to EConfigType.SOCKS,
            "HTTP" to EConfigType.HTTP,
            getString(R.string.menu_actions_type_group) to EConfigType.POLICYGROUP,
        )
        val chainable = mainViewModel.serversCache.count { !it.profile.configType.isComplexType() }
        if (chainable >= 2) {
            types.add(getString(R.string.menu_actions_type_chain) to EConfigType.PROXYCHAIN)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_actions_type_title)
            .setItems(types.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                importManually(types[which].second.value)
            }
            .setNegativeButton(R.string.menu_actions_cancel, null)
            .show()
    }

    private fun importManually(createConfigType: Int) {
        // Launched through createServerLauncher (not startActivity) so the new server appears in
        // the list the moment the editor returns.
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            createServerLauncher.launch(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            createServerLauncher.launch(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            createServerLauncher.launch(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    /**
     * "Ввести вручную": a plain text-input dialog for pasting/typing a config or subscription
     * link by hand. The entered string is fed into the same import path as pasted clipboard text.
     */
    private fun showManualEntryDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.manual_entry_hint)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.menu_add_manual)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // Validate before dismissing so a bad paste shows an inline reason instead of a generic
        // failure toast after the dialog has already closed.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                when {
                    text.isEmpty() ->
                        input.error = "Вставьте ссылку подписки или конфигурацию сервера"
                    !looksImportable(text) ->
                        input.error = "Не похоже на ссылку или конфигурацию. " +
                                "Пример: https://departament.example/sub или vless://…"
                    else -> {
                        dialog.dismiss()
                        importBatchConfig(text)
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Cheap pre-check for the manual-entry dialog: is the text plausibly a subscription link, a
     * proxy share-link (possibly base64-wrapped), or a raw config body? Keeps obviously-broken
     * input from reaching the importer while accepting every format the importer understands.
     */
    private fun looksImportable(text: String): Boolean {
        if (Utils.isValidSubUrl(text)) return true
        if (text.contains("://")) return true
        if (Utils.decode(text).contains("://")) return true
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    showImportResult(result)
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * Turns the rich [AngConfigManager.ImportResult] into precise, sentence-case feedback so adding
     * a server or subscription is never silently successful nor falsely reported as a failure.
     */
    private fun showImportResult(result: AngConfigManager.ImportResult) {
        when {
            // A subscription was just added: report how many of its servers actually loaded.
            result.countSub > 0 -> {
                val loaded = result.subFetch?.configCount ?: 0
                if (loaded > 0) {
                    toastSuccess("Серверы добавлены: $loaded")
                } else {
                    toastError("Не удалось загрузить серверы подписки")
                }
                mainViewModel.reloadServerList()
            }
            // One or more individual servers were imported.
            result.count > 0 -> {
                toastSuccess(getString(R.string.title_import_config_count, result.count))
                mainViewModel.reloadServerList()
            }
            // The subscription link is valid but was already added.
            result.subDuplicate -> toast("Подписка уже добавлена")
            // The subscription link is not from departament.
            result.subRejected -> toast("Эта ссылка не от departament. Используйте подписку из нашего бота.")
            else -> toastError(R.string.toast_failure)
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }


    /**
     * import config from sub
     */
    fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                }
                hideLoading()
            }
        }
        return true
    }

    /**
     * «Экспортировать в буфер»: every shareable server as one newline-separated list of links.
     *
     * `AngConfigManager.shareConfig()` returns nothing for an operator-locked profile and for the
     * complex types (custom json, group, chain), so a partial result is normal and the count is
     * what the user is told. The menu item is already disabled when nothing at all is shareable.
     */
    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            withContext(Dispatchers.Main) {
                hideLoading()
                if (ret > 0) {
                    showActionSnackbar(getString(R.string.menu_actions_export_done, ret))
                } else {
                    showActionSnackbar(getString(R.string.menu_actions_export_failed))
                }
            }
        }
    }

    /**
     * «Удалить все серверы»: the one bulk deletion that keeps a dialog.
     *
     * 00-rules.md 7.5 allows a confirmation only for the genuinely irreversible, and this is it:
     * `MmkvManager.removeAllServer()` clears the whole profile store, the per-provider indexes and
     * the selected-server key in one `clearAll()`, so there is no per-item removal left to reverse.
     * Provider servers come back with «Обновить подписки»; hand-added ones do not come back at all,
     * which is what the dialog body says.
     */
    private fun delAllConfig() {
        if (!bulkDeleteAllowed()) return
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.menu_actions_del_all)
            .setMessage(R.string.menu_actions_del_all_body)
            .setPositiveButton(R.string.menu_actions_del_all_confirm) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    // MmkvManager.removeAllServer() wipes the whole MAIN store, which also holds
                    // the WebDAV backup config - collateral the label does not promise. Carry it
                    // across. (The subscription id list rebuilds itself from the SUB store on the
                    // next read, so providers survive.)
                    val webDav = MmkvManager.decodeWebDavConfig()
                    // What the user is told is what the user could see. The store-level count can
                    // be larger (it counts every profile key, including any orphan not listed
                    // under a provider), and a receipt that overstates the deletion is a lie.
                    val listed = mainViewModel.serversCache.size
                    val wiped = mainViewModel.removeAllServer()
                    webDav?.let { MmkvManager.encodeWebDavConfig(it) }
                    LogUtil.i(AppConfig.TAG, "delAllConfig: $listed listed servers, $wiped profile entries wiped")
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        homeFragment?.refreshSelectedServer()
                        hideLoading()
                        showActionSnackbar(getString(R.string.menu_actions_all_deleted, listed))
                    }
                }
            }
            .setNegativeButton(R.string.menu_actions_cancel, null)
            .create()
        // 7.5: the confirm is the red button on the right, cancel stays neutral on the left.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(themeColor(androidx.appcompat.R.attr.colorError))
        }
        dialog.show()
    }

    /**
     * «Удалить дубликаты»: removes immediately and offers undo, per 00-rules.md 7.5 - a duplicate
     * is a copy of a server that is still in the list, so nothing unique is at stake.
     */
    private fun delDuplicateConfig() {
        if (!bulkDeleteAllowed()) return
        runBulkDelete(
            remove = { mainViewModel.removeDuplicateServer() },
            successText = R.string.menu_actions_duplicates_deleted,
            emptyResult = { showActionSnackbar(getString(R.string.menu_actions_duplicates_none)) },
        )
    }

    /**
     * «Удалить недоступные»: removes the servers whose last latency check failed, with undo.
     *
     * "Invalid" means a stored delay below zero, so with no check ever run there is nothing to
     * remove and the old dialog's «Выполните проверку перед удалением» was the only hint. Instead
     * of a warning before the fact, the empty outcome now says why and offers the check itself.
     */
    private fun delInvalidConfig() {
        if (!bulkDeleteAllowed()) return
        runBulkDelete(
            remove = { mainViewModel.removeInvalidServer() },
            successText = R.string.menu_actions_invalid_deleted,
            emptyResult = {
                showActionSnackbar(
                    getString(R.string.menu_actions_invalid_none),
                    getString(R.string.menu_actions_check),
                ) { startLatencyCheckAll() }
            },
        )
    }

    /**
     * Shared body of the two undoable bulk deletions: snapshot, delete, report the count with an
     * «Отменить» action that puts the removed servers back exactly where they were.
     */
    private fun runBulkDelete(
        remove: () -> Int,
        @StringRes successText: Int,
        emptyResult: () -> Unit,
    ) {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = snapshotServers()
            val ret = remove()
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                homeFragment?.refreshSelectedServer()
                hideLoading()
                if (ret > 0) {
                    showActionSnackbar(
                        getString(successText, ret),
                        getString(R.string.menu_actions_undo),
                    ) { undoBulkDelete(snapshot) }
                } else {
                    emptyResult()
                }
            }
        }
    }

    /** Everything needed to put a bulk-deleted set of servers back, taken before the deletion. */
    private class ServersSnapshot(
        val profiles: Map<String, ProfileItem>,
        val delays: Map<String, Long>,
        val order: Map<String, List<String>>,
        val selected: String?,
    )

    /**
     * Reads the current servers into memory: the profile behind each guid, its measured latency,
     * the per-provider order and the selected guid. Raw xray-json bodies are not captured because
     * `MmkvManager.removeServer()` leaves the raw store untouched.
     *
     * Call from a background dispatcher: this is one json parse per server.
     */
    private fun snapshotServers(): ServersSnapshot {
        val profiles = LinkedHashMap<String, ProfileItem>()
        val delays = HashMap<String, Long>()
        // Copied first: the cache itself is rebuilt on the main thread, and this runs on IO.
        mainViewModel.serversCache.toList().forEach { cached ->
            MmkvManager.decodeServerConfig(cached.guid)?.let { profiles[cached.guid] = it }
            MmkvManager.decodeServerAffiliationInfo(cached.guid)?.let {
                delays[cached.guid] = it.testDelayMillis
            }
        }
        val subIds = (listOf(AppConfig.DEFAULT_SUBSCRIPTION_ID) + MmkvManager.decodeSubsList()).distinct()
        val order = subIds.associateWith { MmkvManager.decodeServerList(it).toList() }
        return ServersSnapshot(profiles, delays, order, MmkvManager.getSelectServer())
    }

    /**
     * Restores a [ServersSnapshot]: re-encodes the profiles that are gone, puts their latency back,
     * then rewrites each provider's order from the snapshot with anything added since appended, so
     * a subscription refresh that landed inside the undo window is not thrown away.
     */
    private fun undoBulkDelete(snapshot: ServersSnapshot) {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            snapshot.profiles.forEach { (guid, profile) ->
                if (MmkvManager.decodeServerConfig(guid) == null) {
                    MmkvManager.encodeServerConfig(guid, profile)
                    snapshot.delays[guid]?.let { MmkvManager.encodeServerTestDelayMillis(guid, it) }
                }
            }
            snapshot.order.forEach { (subId, guids) ->
                val restored = guids.filter { MmkvManager.decodeServerConfig(it) != null }.toMutableList()
                MmkvManager.decodeServerList(subId).forEach { guid ->
                    if (!restored.contains(guid)) restored.add(guid)
                }
                MmkvManager.encodeServerList(restored, subId)
            }
            // encodeServerConfig() claims the selection when none is set, so put the user's own
            // choice back if it survived.
            snapshot.selected
                ?.takeIf { MmkvManager.decodeServerConfig(it) != null }
                ?.let { MmkvManager.setSelectServer(it) }
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                homeFragment?.refreshSelectedServer()
                hideLoading()
                showActionSnackbar(getString(R.string.menu_actions_restored))
            }
        }
    }

    /**
     * Bulk deletions are refused while the tunnel is up.
     *
     * Deleting the running server would leave the hero labelled with a server that no longer
     * exists, and emptying the list hides the connect control altogether (Главная's empty state),
     * which would strand the user with a live tunnel and no way to stop it. The per-server delete
     * already refuses to touch the selected server, so this is the same rule at list scale.
     */
    private fun bulkDeleteAllowed(): Boolean {
        if (mainViewModel.isRunning.value != true) return true
        showActionSnackbar(
            getString(R.string.menu_actions_busy),
            getString(R.string.menu_actions_busy_action),
        ) { toggleConnection() }
        return false
    }

    /**
     * «Сортировать по задержке»: reorders each provider's group by its measured latency.
     *
     * With no measurement there is nothing to sort by and the old code just spun the loader and
     * changed nothing, so the untested case now says so and offers the check.
     */
    private fun sortByTestResults() {
        val measured = mainViewModel.serversCache.toList().any { cached ->
            (MmkvManager.decodeServerAffiliationInfo(cached.guid)?.testDelayMillis ?: 0L) > 0L
        }
        if (!measured) {
            showActionSnackbar(
                getString(R.string.menu_actions_no_delay),
                getString(R.string.menu_actions_check),
            ) { startLatencyCheckAll() }
            return
        }
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
                showActionSnackbar(getString(R.string.menu_actions_sorted))
            }
        }
    }

    /**
     * Runs the latency check over the whole list.
     *
     * Every «Проверить задержку» route lands here: the Серверы header control, the Home meta bar
     * and the recovery actions on the sort / «Удалить недоступные» snackbars.
     *
     * Which rows go into the measuring state is decided by [MainViewModel], not here: it is the one
     * that knows which rows this method can actually address, and a row nothing will measure must
     * not be shown as being measured.
     *
     * With nothing to measure, none of the four methods behind [MainViewModel.testAllServers]
     * leaves a trace - three iterate an empty list and the real-ping one returns early inside its
     * coroutine ([MainViewModel.testAllRealPing]) - so the control would read as broken. An empty
     * list and a search that matched nothing are different problems, so they get different
     * recoveries. The measurement itself needs no tunnel: real ping runs in `CoreTestService`,
     * which builds a throwaway core per server at `SettingsManager.getRealPingConcurrency()` at a
     * time, reports progress in its own notification and is cancellable, so a long list is slow
     * but never unbounded.
     */
    override fun startLatencyCheckAll() {
        if (mainViewModel.serversCache.isEmpty()) {
            if (mainViewModel.keywordFilter.isNotEmpty()) {
                showActionSnackbar(
                    getString(R.string.menu_actions_ping_filtered),
                    getString(R.string.menu_actions_reset_search),
                ) { serversFragment?.clearSearch() }
            } else {
                val onServers = selectedNavId == R.id.nav_servers
                // Each tab offers its own control to anchor to: the Серверы header's, or Главная's
                // scrolling "+". With neither on screen the menu is anchored to the bar itself, so
                // the action is never a dead end.
                val anchor = (if (onServers) serversFragment?.addMenuAnchor() else homeFragment?.addMenuAnchor())
                    ?: binding.bottomNav
                showActionSnackbar(
                    getString(R.string.menu_actions_ping_empty),
                    getString(R.string.menu_actions_add),
                ) { showImportMenu(anchor, withListActions = onServers) }
            }
            return
        }
        mainViewModel.testAllServers()
    }

    /**
     * The one feedback surface for these actions: a themed Snackbar, never a Toast (00-rules.md
     * 1.4.8 - anything the user can act on needs an action). 5s with an action, 3s without
     * (22-components.md 14). Anchored above the bottom navigation only while it is actually
     * visible - in the signed-out empty state it is gone, and anchoring to a hidden view would
     * park the bar in the wrong place.
     */
    private fun showActionSnackbar(
        text: CharSequence,
        actionLabel: CharSequence? = null,
        action: (() -> Unit)? = null,
    ) {
        val bar = Snackbar.make(binding.mainContent, text, Snackbar.LENGTH_LONG)
        bar.duration = if (action != null) 5000 else 3000
        if (binding.bottomNav.isVisible) bar.setAnchorView(binding.bottomNav)
        if (action != null && actionLabel != null) bar.setAction(actionLabel) { action() }
        bar.show()
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     *
     * A file the system hands back can still be unreadable (revoked permission, a directory, a
     * provider that dies mid-read). That used to fail into the log only, leaving the tap with no
     * visible outcome at all, so the failure is reported.
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                val text = input?.bufferedReader()?.readText()
                if (text.isNullOrBlank()) {
                    showActionSnackbar(getString(R.string.menu_actions_file_failed))
                    return
                }
                importBatchConfig(text)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            showActionSnackbar(getString(R.string.menu_actions_file_failed))
        }
    }

    /**
     * «Найти выбранный сервер»: scrolls the Серверы list to the server the next connect would use.
     *
     * The scrolling itself — and the two states that hide the row without it being gone, an active
     * search and a collapsed provider group — belong to the list, so they live in
     * [ServersFragment.locateServer]; this opens the tab it needs and reports the one outcome that
     * is not a scroll.
     *
     * The two old `toast()` calls also pointed at the wrong strings: `title_file_chooser`
     * («Выберите профиль») and `toast_server_not_found_in_group` both say «профиль», which
     * 00-rules.md 9.3 locks to «сервер», and both were Toasts for something the user can act on
     * (1.4.8).
     */
    private fun locateSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            // prepareMenu() hides the item in this state; this still guards the window between
            // opening the menu and tapping it.
            showActionSnackbar(getString(R.string.menu_actions_locate_none))
            return
        }
        // Only the Серверы menu offers this today, so this is a no-op there; it keeps the action
        // correct if a second surface ever calls it. Selecting the tab also attaches its fragment,
        // so the list exists by the line below.
        if (selectedNavId != R.id.nav_servers) selectNav(R.id.nav_servers)

        if (serversFragment?.locateServer(selectedGuid) != true) {
            showActionSnackbar(getString(R.string.menu_actions_locate_missing))
        }
    }

    /**
     * BACK is owned by the [OnBackPressedCallback] registered in [onCreate] (tab -> Главная ->
     * minimise) and must not be handled here as well.
     *
     * This used to consume `KEYCODE_BACK` and call `moveTaskToBack(false)` on key-DOWN, which is
     * the upstream v2rayNG behaviour and pre-empts the dispatcher. With `targetSdk` 37 and no
     * `enableOnBackInvokedCallback` the two paths coexist and the SAME build navigates
     * differently per device: on Android 15+ the platform routes BACK to the dispatcher and Back
     * returns to Главная, while below it the key path won here and Back minimised the app from
     * every tab. One path only, so every supported version behaves the same.
     *
     * Gamepad B still needs routing by hand: it is never delivered through the platform's
     * back-invoked dispatcher, and the leanback build is a declared target.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}