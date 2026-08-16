package com.v2ray.ang.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.DrawableCompat
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
import com.v2ray.ang.ui.component.onSingleClick
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.SubscriptionOrigin
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.animationsEnabled
import com.v2ray.ang.util.tickHaptic
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The three bottom-navigation destinations, in bar order: Главная · Аккаунт · Настройки.
 *
 * There is deliberately no Серверы destination. The owner decided on 2026-07-26 that both clients
 * carry these three and no more (`docs/design2026/11-app-structure.md` 2.0), which overrules the
 * four-destination set that document's 2.1 still describes, and the tab's own capabilities were NOT
 * relocated anywhere — «функции из вкладки сервера не надо никуда пихать».
 *
 * This is the shared vocabulary between the shell and its tab fragments. [navId] is the bar item's
 * view id — the same value [MainActivity] persists across a theme/language recreate — and [tag] is
 * the `FragmentManager` tag the tab's fragment is added under, which is how a restored instance is
 * found again on recreate instead of being rebuilt from scratch.
 *
 * Every tab is a fragment in the one `tab_host` container; `activity_main.xml` holds no tab content
 * of its own.
 */
enum class MainTab(@get:IdRes val navId: Int) {
    HOME(R.id.nav_home),
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
 * and the import actions; a tab owns its own content and reaches the shell only through
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
     * Recomputes the bottom bar's own gates: whether the Аккаунт item exists (signed in, OR holding
     * a departament подписка — see `MainActivity.accountTabAvailable`) and whether the whole bar
     * exists (hidden in the pure onboarding state — signed out AND no servers). Both read shell
     * state only, so the bar stays the shell's alone; a tab calls this after doing something that
     * can change either input.
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
     * in the app, so a row behaves identically wherever it is drawn.
     *
     * Главная's inline list is that list today: `HomeFragment.setupServerList` builds its
     * `MainRecyclerAdapter` with exactly this listener. It stays an interface rather than a direct
     * call into the activity so a second list can be plugged in without either screen learning the
     * other's internals.
     */
    val serverActions: MainAdapterListener

    /**
     * Opens the server-actions sheet for one server — edit, share, QR, duplicate, make default,
     * delete.
     *
     * Reached by a long press on a row of Главная's inline list
     * (`HomeFragment.setupServerList` -> `onItemLongClick`). It is the ONLY route to edit, share,
     * QR and delete for a single server, so it is not something a later change may quietly drop:
     * removing the long press removes the whole per-server action set with it.
     */
    fun showServerActions(guid: String)

    /**
     * «Добавить подписку»: the add popup, anchored to [anchor]. **Two items — scan a QR code, or
     * take the link from the clipboard — and nothing else.**
     *
     * The owner cut it to those two on 2026-07-27. A departament customer adds a подписка the way
     * the bot hands it over, and the four other ways of getting a config into the app were burying
     * the two that matter. See `menu_main.xml` for where each of the four went.
     */
    fun showAddMenu(anchor: View)

    /**
     * «Добавить по QR-коду» itself — the scanner, opened directly, with no menu in front of it.
     *
     * [showAddMenu] is the entry point for a user who has NOT yet chosen how to add; this one is
     * for a user who has. The start screen's «Другие способы» list names the method in the row
     * label, so sending that row through the add popup asked the same question twice and put a
     * two-item menu between the tap and the camera (owner report, 2026-08-05).
     *
     * The scanner itself is unchanged: this forwards to the same private `importQRcode()` the add
     * popup's QR item fires, so both routes share one result handler and one importer.
     */
    fun importByQr()

    /**
     * The three add methods «Добавить подписку» no longer carries: a typed link, a hand-built
     * server, a config file. Unchanged in behaviour — only their entry point left the add menu.
     *
     * **Nothing calls this yet, and that is the one unfinished piece of the owner's cut**: he
     * removed the three from the add menu and said where they belong next is his to say. They are
     * one row away from a home — a «Другие способы добавления» row in the Настройки tab's ПОДПИСКА
     * section calling this — and until that row exists the capability is preserved rather than
     * deleted. Do not inline these back into the add menu, and do not delete them for being
     * unreachable: the entry point is what moved, not the feature.
     */
    fun showAdvancedAddMethods()

    /** «Обновить подписки»: re-fetches every subscription and reloads the list. */
    fun refreshSubscriptions()

    /**
     * Whether Главная's entrance (handoff §3, «Сборка главной») is being held for a full-screen
     * flow overlay, so the tab parks in its pre-entrance state at [HomeFragment.onViewCreated]
     * instead of assembling itself behind the overlay.
     *
     * Set by [MainActivity.holdHomeEntrance] and cleared by [MainActivity.revealHome]. It lives on
     * the shell rather than in Главная because it has to be answerable BEFORE Главная has a view.
     */
    val homeEntranceHeld: Boolean

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
     * The Настройки tab's fragment, or null until the tab has been opened once (it is attached
     * lazily — see [syncTabFragments]). Looked up by tag for the same reason [homeFragment] is.
     *
     * The shell needs it for ONE thing: pushing a changed bottom inset into the tab's scrolling
     * list, exactly as it pushes one into Главная's ([setupEdgeToEdge]). A tab attached after the
     * window's insets were dispatched reads the published figure itself.
     */
    private val settingsFragment: SettingsTabFragment?
        get() = supportFragmentManager.findFragmentByTag(MainTab.SETTINGS.tag) as? SettingsTabFragment

    /**
     * The one row-action listener shared by every server list (see [MainHost.serverActions]).
     */
    private val adapterListener: ActivityAdapterListener by lazy { ActivityAdapterListener() }

    /** Last computed bottom-nav padding for a tab's scrolling list; see [MainHost.listBottomInset]. */
    private var navListPadding = 0

    /**
     * The bottom-nav scrim's height BEFORE the window's bottom inset is added to it — the figure
     * `activity_main.xml` states, read once from the inflated view so the layout stays the single
     * place that number is written. [setupEdgeToEdge] adds the inset to it on every dispatch.
     */
    private var navScrimBaseHeight = 0

    private val shareMethod: Array<out String> by lazy { resources.getStringArray(R.array.share_method) }
    private val shareMethodMore: Array<out String> by lazy { resources.getStringArray(R.array.share_method_more) }

    // Cached easing curves (loaded once) so the imperative nav motion rides the same ease-out tempo
    // as the declarative res/interpolator + res/anim resources. No bounce.
    /**
     * §8 «Полоска навигации» and «Смена цвета» both ride ease-out-quart — the curve every 220–340ms
     * state change in the product uses. This replaced ease_standard here: the bar had a curve of
     * its own for no reason, and the shell has no other imperative animation to keep it alive.
     */
    private val easeOutQuart by lazy { AnimationUtils.loadInterpolator(this, R.interpolator.ease_out_quart) }

    /**
     * Where the travelling nav bar is headed, in px. Held because a tab switch lays the shell out
     * again mid-flight (the fragment container shows one child and hides another), and the layout
     * listener that keeps the bar glued would otherwise cancel the travel and snap it home on the
     * very frame it started. Same destination == nothing to do. NaN until the first placement.
     */
    private var navIndicatorTarget = Float.NaN

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
        setupBottomNav(restoreSelectedNav(savedInstanceState))
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
     * The tab to paint first: the one that was on screen before the recreate, or Главная.
     *
     * The stored value is validated against the destination set rather than trusted, because a
     * saved selection can name a tab that no longer exists — the removed Серверы destination is
     * exactly that case, and so is any id an older build wrote. An id with no tab behind it would
     * otherwise reach [showTab], find no fragment to add and no group to show, and leave the shell
     * on an empty container with nothing selected in the bar. Anything unrecognised falls back to
     * Главная, which is also the only tab guaranteed to exist.
     */
    private fun restoreSelectedNav(savedInstanceState: Bundle?): Int {
        val stored = savedInstanceState?.getInt(KEY_SELECTED_NAV, R.id.nav_home) ?: R.id.nav_home
        return MainTab.fromNavId(stored)?.navId ?: R.id.nav_home
    }

    /**
     * Wires the bottom navigation and paints [initialNav] as the first tab on screen. Every tab is
     * a fragment in the shared container; Главная is attached here whichever tab is showing.
     */
    private fun setupBottomNav(initialNav: Int) {
        // The custom bar is a plain LinearLayout with no fitsSystemWindows behaviour, so it never
        // auto-pads itself; setupEdgeToEdge's parent listener is the single source of its bottom
        // inset padding. (A no-op listener returning the insets unchanged used to sit here.)

        // onSingleClick, not setOnClickListener: the 500ms guard is what stops a hammered bar
        // queueing three tab swaps in one frame (D03). Haptic.NONE here because showTab already
        // ticks on a real change — a tab switch buzzes once, whether the bar or a fragment asked
        // for it.
        binding.navHome.onSingleClick { selectNav(R.id.nav_home) }
        binding.navSettings.onSingleClick { selectNav(R.id.nav_settings) }
        // The Account item is a real in-place content tab (AccountFragment), selected like the
        // others — including while signed out, where the tab renders its own "not signed in" state
        // and is the place a clipboard-подписка user signs in from. Its fragment is attached lazily
        // the first time it is opened (see syncTabFragments).
        binding.navAccount.onSingleClick { selectNav(R.id.nav_account) }
        // Аккаунт can vanish with the подписка it came in on, so a restored selection of it is
        // honoured only if the destination still exists — otherwise the tab would be attached (and
        // would start loading) for a user refreshNavGates is about to move off it anyway.
        val start = if (initialNav == R.id.nav_account && !accountTabAvailable()) {
            R.id.nav_home
        } else {
            initialNav
        }
        // Not selectNav: the first paint is not a tab CHANGE, so it takes showTab's previous == tab
        // path (no haptic) and lands on the restored tab in one transaction.
        selectedNavId = start
        updateNavSelection(start)
        // The single travelling indicator has to be placed once the columns have been measured,
        // and re-placed whenever they are measured again — Аккаунт appearing halves them.
        trackNavIndicator()
        showTab(start, start)
    }

    /** Currently selected bottom-nav tab (replaces BottomNavigationView.selectedItemId). */
    private var selectedNavId = R.id.nav_home

    /**
     * Selects a bottom-nav tab: swaps the visible tab content, then repaints the custom bar.
     *
     * **That order is the fix for D03, and it is the whole rule of this method: the content moves
     * first and the highlight follows it, never the other way round.** The bar used to be
     * repainted up front and the transaction attempted afterwards — and [syncTabFragments]
     * legitimately refuses to commit once `onSaveInstanceState` has run, so a tap that arrived in
     * that window moved the blue pill under one tab and left another tab's content on screen, with
     * nothing to correct it. On Аккаунт that is terminal: the item can still leave the bar (the
     * подписка it came in on is removed), and the app would sit on a tab with no way back to it.
     *
     * A re-tap of the current tab is a no-op rather than a rebuild — there is nothing to swap, and
     * a haptic with no consequence teaches the user the bar is unreliable.
     */
    private fun selectNav(navId: Int) {
        val previous = selectedNavId
        if (navId == previous) return
        selectedNavId = navId
        if (!showTab(navId, previous)) {
            // The content could not move, so the bar must not either. The activity is on its way
            // to a recreate; it repaints the persisted tab from onCreate.
            selectedNavId = previous
            return
        }
        updateNavSelection(previous)
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

    override val homeEntranceHeld: Boolean
        get() = entranceHeld

    // ==================== The overlay hand-off (handoff §3, §11 grabl 6) ====================

    /**
     * Whether a full-screen flow overlay has asked Главная to wait before assembling itself.
     * @see holdHomeEntrance
     */
    private var entranceHeld = false

    /**
     * ARM the hand-off: call this BEFORE raising a full-screen loading overlay over the shell.
     *
     * Главная is stamped into its pre-entrance state — the account row, the «+», the connect
     * object, the speeds, the server line, the подписка card and the visible server rows all at
     * alpha 0 — and stays there. Without this the tab assembles itself the moment it is created,
     * finishes in about 1.3 seconds and the overlay comes down over a screen that is already whole,
     * which is README §11 grabl 6.
     *
     * Safe before Главная exists: the flag is the shell's, and the tab reads it in onViewCreated.
     */
    fun holdHomeEntrance() {
        entranceHeld = true
        homeFragment?.holdEntrance()
    }

    /**
     * RELEASE the hand-off — the single call the overlay's owner makes, and the whole reason it
     * takes the teardown as a lambda.
     *
     * [dismissOverlay] runs and Главная starts assembling INSIDE THE SAME synchronous block, so
     * both land in one traversal and the compositor never gets a frame with the overlay gone and
     * the screen already built. Anything that puts a post, a delay or a second animation callback
     * between the two re-opens grabl 6.
     *
     *     mainActivity.revealHome { (overlay.parent as? ViewGroup)?.removeView(overlay) }
     *
     * §3 puts this at 6450ms — the overlay fades over its last 520ms and is REMOVED here, not
     * hidden. Calling it without a prior [holdHomeEntrance] is harmless: Главная will already have
     * assembled, and an entrance plays once per view.
     */
    fun revealHome(dismissOverlay: () -> Unit) {
        entranceHeld = false
        dismissOverlay()
        homeFragment?.playEntrance()
    }

    override fun showAddMenu(anchor: View) = showImportMenu(anchor)

    /** The add popup's QR item without the popup; see [MainHost.importByQr]. */
    override fun importByQr() {
        importQRcode()
    }

    override fun showAdvancedAddMethods() {
        val labels = arrayOf(
            getString(R.string.menu_actions_add_link),
            getString(R.string.menu_actions_add_create),
            getString(R.string.menu_actions_add_file),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.add_other_methods_title)
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> showManualEntryDialog()
                    1 -> pickManualServerType()
                    else -> importConfigLocal()
                }
            }
            .setNegativeButton(R.string.menu_actions_cancel, null)
            .show()
    }

    override fun refreshSubscriptions() {
        importConfigViaSub()
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
     *
     * The third axis — the 28x3 accent bar — is no longer per item. It is ONE view that slides to
     * the selected column; see [positionNavIndicator].
     */
    private fun updateNavSelection(previousNavId: Int = selectedNavId) {
        val active = themeColor(androidx.appcompat.R.attr.colorPrimary)
        val inactive = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val items = listOf(
            Triple(R.id.nav_home, binding.navHomeIcon, binding.navHomeLabel),
            // The Account tab tints blue when selected, exactly like the other tabs.
            Triple(R.id.nav_account, binding.navAccountIcon, binding.navAccountLabel),
            Triple(R.id.nav_settings, binding.navSettingsIcon, binding.navSettingsLabel),
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
            // Second active-state axis (beyond the colour tween): a heavier label, so the
            // active tab reads on weight + accent, not tint alone.
            applyNavLabelWeight(label, selected)
        }
        positionNavIndicator(animate)
    }

    /** The nav column a tab id belongs to (null for an unknown id). */
    private fun navItem(navId: Int): View? = when (navId) {
        R.id.nav_home -> binding.navHome
        R.id.nav_account -> binding.navAccount
        R.id.nav_settings -> binding.navSettings
        else -> null
    }

    /**
     * Slides the ONE active-tab bar to the selected column (handoff README §4).
     *
     * The prototype states the three resting positions as 46 / 166 / 286dp, which are that
     * mock-up's own 360dp-wide, always-three-tabs arithmetic. This bar is neither: Аккаунт is a
     * weighted item that collapses to nothing while signed out, so the columns are 1/2 the bar
     * wide as often as they are 1/3. The centre is therefore READ off the column that is actually
     * on screen — the same number the prototype's constants encode, computed instead of copied,
     * so a two-tab bar and a 412dp phone are both right.
     *
     * Travel is @integer/motion_nav_indicator (280ms) on ease_out_quart, and only on a real tab
     * change: the first paint, a recreate and reduced motion all place it without animating,
     * because a bar that flies in from x=0 on launch is a transition to nothing.
     */
    private fun positionNavIndicator(animate: Boolean) {
        val indicator = binding.navIndicator
        val target = navItem(selectedNavId)
        if (target == null || !target.isVisible) {
            indicator.visibility = View.INVISIBLE
            return
        }
        // Pre-layout (first paint, or the frame in which Аккаунт appears): nothing has a width
        // yet, so there is no centre to compute. Come back once the row has been measured.
        if (target.width == 0 || indicator.width == 0) {
            binding.navItems.post { positionNavIndicator(false) }
            return
        }
        val x = target.left + (target.width - indicator.width) / 2f
        indicator.visibility = View.VISIBLE
        // Already going there. See [navIndicatorTarget]: this is the guard that lets the layout
        // listener run on every pass without ever interrupting a travel in progress.
        if (x == navIndicatorTarget) return
        navIndicatorTarget = x
        indicator.animate().cancel()
        if (!animate || !animationsEnabled()) {
            indicator.translationX = x
            return
        }
        indicator.animate()
            .translationX(x)
            .setDuration(resources.getInteger(R.integer.motion_nav_indicator).toLong())
            .setInterpolator(easeOutQuart)
            .start()
    }

    /**
     * Keeps the bar glued to its column across every layout the bar can go through without a tab
     * change: the Аккаунт item arriving or leaving (two columns become three and every centre
     * moves), a rotation, a font-scale change, the window being resized. None of those is a
     * transition, so none of them animates — and a pass that changes nothing is a no-op, because
     * [positionNavIndicator] compares against [navIndicatorTarget] before it touches the view.
     */
    private fun trackNavIndicator() {
        binding.navItems.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionNavIndicator(animate = false)
        }
        binding.navItems.post { positionNavIndicator(animate = false) }
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

    /**
     * Tweens one nav item's icon tint + label colour from -> to.
     *
     * @integer/motion_state and ease_out_quart, which is §8's «Смена цвета 220 мс» — the same
     * clock and curve the indicator's travel and every switch in the product run on. It was a
     * hard-coded 200 on ease_standard, i.e. the one place in the bar that had its own tempo.
     */
    private fun tweenNavItemColor(
        icon: android.widget.ImageView,
        label: android.widget.TextView,
        from: Int,
        to: Int,
    ) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = resources.getInteger(R.integer.motion_state).toLong()
            interpolator = easeOutQuart
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
     *
     * @return whether the container now shows [navId]. False means the transaction was refused and
     *   the caller must not paint the bar as if it had happened — see [selectNav].
     */
    private fun syncTabFragments(navId: Int): Boolean {
        val fm = supportFragmentManager
        // After onSaveInstanceState a commit is illegal; the restored activity will re-run this
        // from its own onCreate, so there is nothing to lose by skipping it.
        if (fm.isStateSaved) return false
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
        return true
    }

    /**
     * The tab-content view for a nav id (null for an unknown id). All three tabs are fragments in
     * the one container, so this answers the same view for each — it stays a lookup because
     * [showTab] must still tell a real tab id from an unknown one.
     */
    private fun tabGroup(navId: Int): View? =
        if (MainTab.fromNavId(navId) != null) binding.tabHost else null

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
     * There is no crossfade left to run. With all three tabs inside `tab_host` the outgoing and
     * incoming views are the same container, so the old group-level fade had nothing to fade
     * between; the pair of fragments inside it can be crossfaded instead, but that is a motion
     * change and belongs to the stage that owns motion (32-master-plan-android.md 9.3 asks for a
     * simultaneous 220 ms crossfade for every tab switch). The tick haptic that marked a switch is
     * kept, so the change is still felt.
     *
     * @return false when the transaction could not be committed, in which case nothing on screen
     *   moved and neither did the haptic — [selectNav] rolls the selection back.
     */
    private fun showTab(tab: Int, previous: Int = tab): Boolean {
        if (!syncTabFragments(tab)) return false
        settleTabs(tabGroup(tab))
        if (previous != tab) binding.bottomNav.tickHaptic()
        return true
    }

    /**
     * True edge-to-edge: the home gradient (home_root) draws behind the status and nav
     * bars; each tab's content receives the top inset (so it clears the clock) and the
     * bottom nav a bottom inset pad (so items clear the gesture bar). The bars themselves
     * stay transparent (handled by the theme, not touched here).
     *
     * **EVERY FIGURE BELOW THAT MEETS THE BOTTOM OF THE WINDOW IS THE INSET PLUS SOMETHING, NEVER A
     * CONSTANT.** The bar, its scrim and the tabs' scrolling lists all have to clear the same
     * hardware, and the phone that has a gesture bar and the phone that has none are the same build
     * — the owner ran both, and every symptom he reported («значки выпали выше из-за слайдера»,
     * «кнопка настройки залазит за навигацию») was a number here that had one of the two baked in.
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The scrim's XML height is its ZERO-INSET baseline (bar + headroom); read before the first
        // dispatch can grow it, so a second dispatch adds the inset to the baseline and not to
        // itself.
        navScrimBaseHeight = binding.bottomNavScrim.layoutParams.height
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
            // AND THE SCRIM GROWS WITH IT. The bar's padding lifts the icons by the whole inset, so
            // a scrim of fixed height stays behind while they climb: at 24dp of gesture bar its ramp
            // reached the icons already a fifth opaque, and at 48dp of 3-button navigation the icons
            // stood above it entirely. Height = baseline + inset keeps the backdrop in exactly the
            // relationship to the buttons it has on a phone with no gesture bar — the one the owner
            // reports as correct — whatever the phone puts under them. Written only when it actually
            // changes: this listener runs on every dispatch and a layout request per dispatch would
            // be a needless pass.
            // (Guarded on a real baseline: the arithmetic is only meaningful while the scrim is
            // declared with an exact height. A layout that ever gave it wrap/match keeps whatever
            // that means instead of being handed a nonsense number.)
            if (navScrimBaseHeight > 0) {
                val scrimHeight = navScrimBaseHeight + bars.bottom
                val scrimLp = binding.bottomNavScrim.layoutParams
                if (scrimLp.height != scrimHeight) {
                    scrimLp.height = scrimHeight
                    binding.bottomNavScrim.layoutParams = scrimLp
                }
            }
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
            settingsFragment?.applyListInsets()
            insets
        }
    }

    /**
     * The shell's own ViewModel wiring: the service broadcast listener, the bundled assets, and the
     * list signal that reaches Главная. Everything a single tab cares about — the speed feed, the
     * tunnel state, the latency results — is observed by that tab.
     */
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            val position = index ?: -1
            // Главная reads the one cache; a tab that has no view yet paints itself from the same
            // cache when it gets one.
            homeFragment?.bindList(position)
            // Adding or removing a subscription can change whether there is anything to navigate
            // to, and (with a departament subscription) whether the Аккаунт item belongs there.
            refreshNavGates()
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    /**
     * Opens the Incy server-actions bottom sheet for [guid].
     * Each action delegates to an existing per-server flow; duplicate reuses
     * [MmkvManager.encodeServerConfig] with a blank guid to mint a fresh copy.
     *
     * Opened by a long press on a row of Главная's list — see [MainHost.showServerActions].
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
            // -1 = "no row index": the sheet knows the guid, not the adapter position. Главная
            // repaints its whole list from the cache on a removal (HomeFragment.removeServerRow),
            // so an index would buy nothing here.
            onDelete = { removeServer(guid, -1) },
        ).show()
    }

    /**
     * The «Добавить подписку» popup, anchored to the tapped control: scan a QR code, or take the
     * link from the clipboard. Two items — see `menu_main.xml` for the owner's cut and for where
     * the four that used to sit under them went.
     *
     * No group divider is set any more: there is one group, and a divider above the first item of
     * the only group is a rule drawn under nothing.
     */
    private fun showImportMenu(anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        // Icons in a PopupMenu are hidden unless forced, and the drawables ship in mixed
        // black/white fills, so each item is tinted from the theme below.
        popup.setForceShowIcon(true)
        prepareMenu(popup.menu)
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    /**
     * Tints the popup's glyphs from the theme. Nothing is conditionally hidden: every item in the
     * menu resource has a live handler in [onOptionsItemSelected], which is the property that
     * replaced the old "hide the group whose handlers are gone" pass.
     */
    private fun prepareMenu(menu: Menu) {
        val neutral = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        for (i in 0 until menu.size()) {
            paintMenuItem(menu.getItem(i), neutral)
        }
    }

    /** Tints one menu item's glyph from the theme. */
    private fun paintMenuItem(item: MenuItem, color: Int) {
        item.icon?.let { icon ->
            val glyph = icon.mutate()
            DrawableCompat.setTint(glyph, color)
            // Disabled = 0.38 on the whole control (00-rules.md 7.1); the label is greyed by the
            // menu itself, the glyph is not.
            glyph.alpha = if (item.isEnabled) 255 else 97
            item.icon = glyph
        }
    }

    /**
     * Hides the whole bottom nav (bar + scrim) in the pure onboarding state — signed out AND no
     * servers — so the sign-in screen reads as a clean solid background with no tab buttons. It
     * returns as soon as EITHER is true (logged in OR at least one server). The nav is an overlay
     * in a FrameLayout (it never reserved layout space), so hiding it leaves no phantom bottom gap.
     * The selected tab is corrected in BOTH directions: when the bar reappears the selection has to
     * be one that still exists (Аккаунт only while signed in), and when it disappears the tab on
     * screen has to be Главная, since a hidden bar leaves no way back to it.
     *
     * **The count is the STORED one, never `serversCache`** (D15). `serversCache` is a *view*: it
     * is rebuilt by `MainViewModel.updateCache()` through `subscriptionId` and `keywordFilter`, so
     * a filter that matches nothing empties it while the user's servers are all still there. Gated
     * on that, typing in a server search took the whole bottom navigation off screen and threw the
     * app back to onboarding — a filtered-to-zero list and an empty store are not the same fact,
     * and only one of them is a reason to hide the app's navigation.
     * [MmkvManager.decodeAllServerList] is the unfiltered truth; the cache stays a list's own
     * business. There is no search on screen today, which is precisely why this must be written
     * down now: the next one to add a filter must not be able to reintroduce the defect.
     */
    private fun updateBottomNavVisibility() {
        val show = AccountSession.isLoggedIn() || MmkvManager.decodeAllServerList().isNotEmpty()
        val becomingVisible = show && !binding.bottomNav.isVisible
        binding.bottomNav.isVisible = show
        binding.bottomNavScrim.isVisible = show
        if (becomingVisible) {
            val valid = when (MainTab.fromNavId(selectedNavId)) {
                MainTab.HOME, MainTab.SETTINGS -> true
                MainTab.ACCOUNT -> accountTabAvailable()
                null -> false
            }
            if (!valid) selectTabWhenIdle(R.id.nav_home)
        } else if (!show && selectedNavId != R.id.nav_home) {
            // The other half of the same invariant: with no bar there is no way back to Главная
            // except the BACK key, so the onboarding state must never be entered while another
            // tab's content is on screen. (Reaching it needs the last server to go while Настройки
            // is open; the correction is here rather than at each such site so the invariant is
            // total — bar hidden implies Главная — instead of true only on the way back.)
            selectTabWhenIdle(R.id.nav_home)
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
     * **The Аккаунт destination exists.** One gate, read by [updateAccountNav], [dropAccountTab],
     * [setupBottomNav] and [updateBottomNavVisibility] — ONE expression, because the item hiding
     * while the fragment stays added and collecting is D12 itself, and two copies of the condition
     * is how that comes back.
     *
     * It used to read `BackendConfig.isConfigured() && accountAccessAllowed()`, i.e. **signed in**,
     * and that is the bar the owner photographed: two items stretched to half the width each. He
     * had pasted a departament подписка from the clipboard and never signed in, so the middle
     * destination deleted itself and took the only way to sign in with it. The owner's correction:
     * «убрать эту плашку надо полностью и оставить вкладку аккаунт, где можно зарегистрироваться
     * там и тд … именно для подписки с буфера обмена, тоесть там можно вход сделать».
     *
     * So a departament подписка now opens the destination on its own. What it does NOT do is grant
     * account ACCESS — that is [accountAccessAllowed], it still means a session, and widening it
     * would send the tab after account data with no token, collect a 401 and sign the user out of
     * a session they never had. The tab exists; whether it renders an account or a "not signed in"
     * state is the tab's own business.
     *
     * A FOREIGN pasted subscription still unlocks nothing: [SubscriptionOrigin] answers only for
     * the owner's own links, because payment and account cannot mean anything for someone else's
     * servers. And a build with no backend has no destination at all, which is decision A-3's one
     * sanctioned removal — it happens at start-up, not mid-session.
     */
    private fun accountTabAvailable(): Boolean =
        BackendConfig.isConfigured() &&
            (accountAccessAllowed() || SubscriptionOrigin.hasDepartamentSubscription())

    /**
     * Recomputes the visibility of the Аккаунт nav item from that gate. Called whenever the account
     * state changes (login/logout) and whenever the subscription / server list changes — pasting a
     * departament подписка is now one of the things that can bring the item in, so the existing
     * `updateListAction` call site matters as much as the account one.
     */
    private fun updateAccountNav() {
        val available = accountTabAvailable()
        binding.navAccount.isVisible = available
        // Hiding the item is not enough: the tab BEHIND it has to go too — see dropAccountTab.
        if (!available) dropAccountTab()
    }

    /**
     * Takes the Аккаунт tab off screen and out of the FragmentManager when the DESTINATION goes
     * (D12).
     *
     * Hiding the bar item used to be the whole of it, and a hidden fragment is not a stopped one:
     * tabs are `hide`/`show`n, never replaced, so [AccountFragment] stayed added and RESUMED after
     * a sign-out, with every `repeatOnLifecycle(STARTED)` collector in it still running against a
     * session that no longer exists. Removing it is what makes «выйти» mean it — the next sign-in
     * gets a fresh instance from [syncTabFragments], loading from a clean state rather than from
     * the previous user's rendered screen.
     *
     * **Signing out no longer fires this on its own**, and that is the intended consequence of
     * [accountTabAvailable] widening: with a departament подписка still on the device the
     * destination survives the sign-out, so the fragment survives with it and shows its
     * "not signed in" state. What stops the dead collectors in that case is
     * `AccountFragment.onSessionCleared`, which cancels the poll and clears the ViewModel on the
     * same transition — the half of D12's fix that lives in the tab. This method still runs, and
     * still removes, when the destination itself goes: the подписка is deleted, or the build has no
     * backend.
     *
     * Posted, not inline, for the same reason [selectTabWhenIdle] is: the account state arrives on
     * a fragment's own collector, inside the FragmentManager's dispatch, where a second commit
     * throws. Every precondition is re-read inside the runnable — through the SAME
     * [accountTabAvailable] gate the caller used — so a sign-in that lands in that one frame
     * cancels the removal instead of racing it.
     *
     * Being posted also keeps the removal behind the sign-out repaint: `AccountFragment` reloads
     * the server list from its own collector when the session ends (the store has just lost that
     * account's подписки), and that runs while this runnable is still queued.
     */
    private fun dropAccountTab() {
        val attached = supportFragmentManager.findFragmentByTag(MainTab.ACCOUNT.tag) != null
        if (!attached && selectedNavId != R.id.nav_account) return
        binding.bottomNav.post {
            if (isFinishing || isDestroyed) return@post
            // Signed back in within the frame: the tab is legitimate again, leave it alone.
            if (accountTabAvailable()) return@post
            val fm = supportFragmentManager
            if (fm.isStateSaved) return@post
            // Off the tab first — removing the fragment the user is looking at would empty the
            // container under them.
            if (selectedNavId == R.id.nav_account) selectNav(R.id.nav_home)
            val fragment = fm.findFragmentByTag(MainTab.ACCOUNT.tag) ?: return@post
            fm.beginTransaction().remove(fragment).commitNow()
        }
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

    // Copying to the clipboard is one of the two actions in the product that change NOTHING on
    // screen, so it is one of the two that still confirms itself — and it does it in a sentence
    // that names what happened, rather than in upstream's «Успешно» / «Ошибка», which were the
    // same two words every outcome in the app used to get.
    private fun share2Clipboard(guid: String) {
        if (AngConfigManager.share2Clipboard(this, guid) == 0) toast(R.string.notice_copied)
        else toastError(R.string.notice_copy_failed)
    }

    private fun shareFullContent(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) toast(R.string.notice_copied) else toastError(R.string.notice_copy_failed)
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
        homeFragment?.removeServerRow(guid, position)
    }

    /**
     * Tapping a server row SELECTS it — it never connects and never reconnects.
     *
     * Connecting is the connect button's job alone. When a tunnel is already up and the user picks a
     * different server, the running tunnel is left untouched and an explicit "apply it" action is
     * offered instead, so a tap in the list can never silently tear down a working connection.
     *
     * The shell owns the write, and fans it out, because more than one surface reads the selected
     * server: Главная's under-shield label, its subscription card, and its inline list. The one
     * call below carries both halves — `HomeFragment.onSelectedServerChanged` repaints the labels
     * AND mirrors the change into the adapter via [MainRecyclerAdapter.setSelectServer], which is
     * what guarantees exactly one row is ever painted selected. Never write the store here without
     * that call: a row painted from MMKV instead of the mirrored guid is how two rows end up
     * looking selected at once.
     */
    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            // The Главная half of the same mirror: its under-shield label and the subscription card.
            homeFragment?.onSelectedServerChanged(selected, guid)
        }

        // THE OFFER IS DECIDED AGAINST THE RUNNING SERVER, NEVER AGAINST THE SELECTED ONE, and that
        // is the whole correction here. This used to `return` the moment the tap landed on the row
        // that was already selected — which, after the very first decline, is the row the user has
        // to press. Selecting does not switch the tunnel, so declining leaves the selection sitting
        // one server ahead of the connection; from that point the picked row IS the selection, the
        // guard swallowed every further tap on it, and «Переподключиться» could not be reached
        // again for the rest of the session. The screen made that worse rather than revealing it:
        // Главная's under-shield line is drawn from the SELECTED server (`HomeFragment.resolveState`),
        // so it already named the server the user was pressing, and the press answered with nothing.
        //
        // Now the write is what the change gates, and the offer is gated by the only question it
        // was ever about: is the tunnel already on this server? While it is, there is nothing to
        // apply and silence is honest. While it is not, the way back is offered every time.
        if (mainViewModel.isRunning.value == true && mainViewModel.runningGuid != guid) {
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
        val bar = Snackbar.make(binding.mainContent, message, Snackbar.LENGTH_LONG)
        // Above the navigation, and ONLY while the navigation is actually on screen — the same
        // guard `Notice.show` and [showActionSnackbar] already make. Anchoring to a hidden view
        // parks the bar off the bottom of the window, silently: nothing throws and nothing paints.
        if (binding.bottomNav.isVisible) bar.setAnchorView(binding.bottomNav)
        // The restart runs through the connect state machine, so a stalled one is reported like
        // any other failed start rather than leaving the hero on the old server. It carries the
        // guid the SENTENCE NAMED: the action used to start whatever `getSelectServer()` held when
        // it was pressed, so a selection that moved while the bar was up connected to one server
        // while the words on the bar promised another.
        bar.setAction(R.string.server_selected_reconnect_action) {
            homeFragment?.applySelectionToRunningTunnel(guid)
        }
        bar.show()
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
        // Главная re-reads the selected server, and its account chip, in HomeFragment.onResume — a
        // hidden tab is still RESUMED, so every tab refreshes itself without the shell reaching
        // into it. (Other entry points change the selection without owning a list: the URL-scheme
        // and shortcut activities, and the quick tile.)
        //
        // A login or a subscription change from another screen can add or remove the Аккаунт item
        // and, in the onboarding state, the bar itself.
        refreshNavGates()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // No toolbar action menu: the shell has no app bar at all, so the tab's own control owns the
        // menu. Главная opens menu_main as a PopupMenu via showImportMenu; the Settings tab has no
        // control at all. onOptionsItemSelected is the dispatch for that PopupMenu, so it stays.
        return false
    }

    /**
     * Dispatch for the «Добавить подписку» popup, which is the only menu the shell inflates.
     *
     * Two branches, because the menu has two items. The «Ввести ссылку» / «Создать вручную» /
     * «Импортировать из файла» branches that used to sit here moved to
     * [showAdvancedAddMethods] — the actions are unchanged, they are simply no longer reached
     * through a menu id — and «Отправить на телевизор» kept only its live entry point, the
     * Настройки · Устройства row.
     */
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
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
     * «Ввести ссылку»: a plain text-input dialog for pasting/typing a config or subscription link
     * by hand. The entered string is fed into the same import path as pasted clipboard text.
     *
     * Reached from [showAdvancedAddMethods], no longer from the add menu.
     */
    private fun showManualEntryDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.manual_entry_hint)
        }
        val dialog = AlertDialog.Builder(this)
            // The same wording the picker row carries. It used to read «Ввести вручную» while the
            // row that opened it read «Ввести ссылку» — one concept, two names.
            .setTitle(R.string.menu_actions_add_link)
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
                        input.error = getString(R.string.import_link_required)
                    !looksImportable(text) ->
                        input.error = getString(R.string.import_link_invalid)
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
                    toastError(R.string.notice_add_failed)
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * The outcome of an add, in the product's own voice and only when the screen cannot say it
     * itself.
     *
     * WHAT CAME OUT OF HERE, and why. This used to answer every branch with a `Toasty` capsule —
     * green «Серверы добавлены: 4», green «Импортировано серверов: 4», red «Ошибка» — and that
     * red one on the QR path is the message the owner named: «по qr коду когда добавляешь там
     * внизу уведомление красное и тд, их убрать надо совсем».
     *
     * A successful add is SILENT now, because it is the one case where the screen answers
     * completely on its own: the gate block is replaced by the подписка card, the card carries
     * the name and the traffic, and the server list fills in under it, all in the same frame as
     * the message would have been. A count of records on top of that is upstream telling the user
     * about its own bookkeeping.
     *
     * The three cases where the tap has NO visible answer keep one sentence each — a подписка
     * that fetched nothing, a link already added, a link that is not ours — and the blunt
     * `toast_failure` («Ошибка») is replaced by a sentence that says what failed.
     */
    private fun showImportResult(result: AngConfigManager.ImportResult) {
        when {
            // A подписка was added. If it produced servers the screen says so by rebuilding
            // itself; if it produced none, nothing on screen changes and that needs a word.
            result.countSub > 0 -> {
                if ((result.subFetch?.configCount ?: 0) <= 0) toastError(R.string.import_sub_empty)
                mainViewModel.reloadServerList()
            }
            // Individual servers: they appear in the list, which is the confirmation.
            result.count > 0 -> mainViewModel.reloadServerList()
            // The subscription link is valid but was already added — nothing changes on screen.
            result.subDuplicate -> toast(R.string.import_sub_duplicate)
            // The subscription link is not from departament.
            result.subRejected -> toast(R.string.import_sub_foreign)
            else -> toastError(R.string.notice_add_failed)
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
                // THE COUNTER IS GONE, and it is the second message the owner named: «Обновлено
                // серверов: 13 (успешно: 1, ошибки: 0, пропущено: 1)». Four figures about the
                // refresh's own bookkeeping, in upstream's voice, on top of a card that has just
                // repainted with the new timestamp and a list that has just repainted with the new
                // rows. The repaint IS the confirmation, so a refresh that worked says nothing.
                //
                // A refresh that produced NOTHING is the case with no visible answer, and it gets
                // one sentence with a next step in it. `NoticePolicy` blocks the counter shape at
                // the surface too, so the string cannot come back through another call site.
                if (result.successCount == 0) toastError(R.string.notice_refresh_failed)
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                }
                hideLoading()
            }
        }
        return true
    }

    /**
     * The one feedback surface for the shell's own outcomes: a themed Snackbar, never a Toast
     * (00-rules.md 1.4.8). 3s, which is the no-action duration of 22-components.md 14. Anchored
     * above the bottom navigation only while it is actually visible - in the signed-out empty state
     * it is gone, and anchoring to a hidden view would park the bar in the wrong place.
     */
    private fun showActionSnackbar(text: CharSequence) {
        val bar = Snackbar.make(binding.mainContent, text, Snackbar.LENGTH_LONG)
        bar.duration = 3000
        if (binding.bottomNav.isVisible) bar.setAnchorView(binding.bottomNav)
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