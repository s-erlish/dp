package com.v2ray.ang.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.AccountSession
import com.v2ray.ang.auth.AuthTokenStore
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.auth.SubscriptionSyncManager
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.FragmentHomeBinding
import com.v2ray.ang.databinding.LayoutSubscriptionMetaBarBinding
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.hasUserInfo
import com.v2ray.ang.dto.entities.isExpired
import com.v2ray.ang.dto.entities.isUnlimited
import com.v2ray.ang.dto.entities.trafficFraction
import com.v2ray.ang.dto.entities.usedTraffic
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.component.Haptic
import com.v2ray.ang.ui.component.SkeletonBinder
import com.v2ray.ang.ui.component.onSingleClick
import com.v2ray.ang.util.AvatarManager
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.util.SubscriptionOrigin
import com.v2ray.ang.util.reducedMotion
import com.v2ray.ang.util.tickHaptic
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Главная — the screen the app opens on.
 *
 * **THIS SCREEN IS A RESTYLE, NOT A REDESIGN**, and the distinction is the reason this file reads
 * the way it does. A wave rebuilt Главная from `docs/design2026/13-start-screen.md`, dropped the
 * inline server list, the subscription card, the session clock and the connect object's rings and
 * motion, and replaced them with two rows that navigate away. The owner ran that build and rejected
 * it — «как выглядела главная по функционалу такая и должна остаться», «все должно быть вернуто с
 * пилюлей и инфой о подписке под кнопкой». That document now carries an OWNER OVERRULE: its scope is
 * void and only its visual language survives.
 *
 * So everything that screen had is here, drawn on the component layer with tokens and the type ramp:
 *
 *  - the top strip — отдача, время сессии, приём — and the add action, on one line;
 *  - the connect object with its three concentric rings, its negotiating breath and sweep, its
 *    one-shot confirm ring, its press scale and its cold-start assemble;
 *  - the country flag and the server identity under it;
 *  - the subscription card carousel — traffic pill, name, auto-update stamp, the operator's notice,
 *    support and Telegram, refresh, ping, pin, delete;
 *  - the server list, inline, which is the ONLY place in the product a server can be picked.
 *
 * **The fragment renders, it does not decide twice.** [resolveState] reads every input once and
 * returns one [HomeState]; [render] applies it and branches on nothing else.
 *
 * **It owns the CONNECT STATE MACHINE, unchanged in behaviour**: the tap handler, the VPN permission
 * prompt, the connect watchdog, the restart-that-waits-for-a-real-stop, the one-shot auto-fallback
 * with its confirmation re-probe. That state machine is why this fragment is attached at LAUNCH
 * rather than the first time its tab is opened (`MainActivity.syncTabFragments`): a hidden fragment
 * is still RESUMED, so the tunnel is still observed and «Перезапустить» after a core-config change
 * still reaches a running core.
 *
 * The tab's view outlives nothing and outlasts every bind — the shell hides and shows tabs rather
 * than replacing them — so every async callback checks [isBindingInitialized] before touching a view
 * and every posted callback is removed in [onDestroyView].
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    // ==================== The model ====================

    /** The connection as the screen draws it. Exactly one is true at a time. */
    private enum class Conn { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR, NO_SERVER, GATED }

    /**
     * The gate block's shapes. A gate exists ONLY when the subscription card and the server list
     * would have nothing true to show, which on this product means "there is not one server to
     * connect to".
     */
    private enum class Gate { SIGN_IN, ADD_SUBSCRIPTION, BUY, SYNC_SERVERS, SYNC_FAILED }

    /** What the account knows about the subscription. [Unknown] is "still resolving". */
    private sealed interface Sub {
        data object Unknown : Sub
        data object None : Sub
        data class Active(val untilMs: Long?) : Sub
        data class Trial(val untilMs: Long) : Sub
        data class Expiring(val untilMs: Long, val daysLeft: Int) : Sub
        data class Expired(val sinceMs: Long) : Sub
    }

    private enum class Severity { INFO, WARN, ERROR }

    /** The single strip condition, already resolved by priority. Never a list. */
    private data class Condition(
        val text: CharSequence,
        val severity: Severity,
        val actionLabel: CharSequence? = null,
        val action: (() -> Unit)? = null,
    )

    private data class HomeState(
        val conn: Conn,
        val gate: Gate?,
        val accountLoading: Boolean,
        val serverName: String?,
        val serverFlag: String?,
        val serverCount: Int,
        val sub: Sub,
        val condition: Condition?,
        val stale: Boolean,
    )

    // ==================== State this screen keeps ====================

    /**
     * The account's subscriptions, the same `SubInfoDto` list Аккаунт renders — one truth, two
     * surfaces. It is also what gives the subscription card its NAME: when a подписка came from the
     * account, the nickname the account returns is what the card shows.
     */
    private val accountViewModel: AccountViewModel by viewModels()

    private var accountSubs: List<SubInfoDto> = emptyList()
    private var subsResolved = false

    /**
     * The last account fetch failed. The screen then KEEPS its last values and says it could not be
     * refreshed, rather than emptying: an error must not delete data the user had.
     */
    private var subsError = false

    /** The user asked for a server sync. If one completes and there are still none, the gate says so. */
    private var syncRequested = false

    // Tracks the last observed signed-in state so the post-login auto-import fires only on a real
    // logged-out -> logged-in transition, not on every state replay.
    private var accountLoggedIn = AccountSession.isLoggedIn()
    private var accountResolved = !BackendConfig.isConfigured() || !AccountSession.isLoggedIn()

    // The connect machine's own flags. resolveState() reads them; nothing else writes them.
    private var connectLoading = false
    private var tunnelRunning = false
    private var disconnecting = false
    private var tunnelError = false

    /** What [render] last drew, so a transition can be told from a repaint. */
    private var renderedConn: Conn? = null

    /**
     * Whether the connect object is currently drawn in its CONNECTED dress. Kept apart from
     * [renderedConn] because «Отключение…» is a distinct connection state but the same visual exit:
     * the release plays on the tap, not one repaint later when the daemon finally answers.
     */
    private var visualConnected = false

    // SkeletonBinder.showAfterDelay() re-posts its 300ms timer on every call, so a screen that
    // re-renders while it waits would postpone the skeleton forever. This arms it exactly once.
    private var accountSkeletonArmed = false

    /** True while the strip's exit is still running, so an arriving condition re-shows it. */
    private var stripHiding = false

    /** Which of the slot's two occupants is drawn, so the swap can be crossfaded once. */
    private var renderedGateVisible: Boolean? = null

    // The live figures. Null means "no reading yet", which prints a zero rather than an empty box:
    // this strip is the screen's ledger and it is on screen at rest.
    private var downBytesPerSec: Long? = null
    private var upBytesPerSec: Long? = null
    private var pingMs: Int? = null
    private var pingProbeFailures = 0

    // Offline is a live condition, so it is observed rather than polled.
    private var offline = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** How many background loads the shell has open — a subscription refresh, an import, an export. */
    private var backgroundLoads = 0

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Tracks the last delivered running state so a transition can be told from the LiveData value
    // replay after a rotation/theme recreate.
    private var lastRunningState: Boolean? = null

    // True between a connect tap and the definitive running/failed result, so a start that ends in
    // "not running" is reported as a failure rather than a silent revert.
    private var connectInProgress = false

    // ---- The inline server list ----

    /**
     * Главная's own server list. The Серверы destination was removed by the owner on 2026-07-26 and
     * none of its functions moved anywhere, so this adapter is the product's only server list; row
     * actions come from the shell so a row behaves identically wherever it is drawn.
     */
    private var homeAdapter: MainRecyclerAdapter? = null

    /** The subscription card's chevron collapses the list under it. Session state, not a setting. */
    private var homeListCollapsed = false

    // ---- The subscription card carousel ----

    private var homeMetaAdapter: HomeMetaPagerAdapter? = null
    private var homeMetaSubIds: List<String> = emptyList()
    private var homeMetaPage = 0

    // ---- The connect object ----

    // The three rings' strokes live in these drawables; the tint animation retints them in place,
    // which is why there is no runtime-tinted ring drawable file and no raw hex on this screen.
    private var ringOuter: GradientDrawable? = null
    private var ringMid: GradientDrawable? = null
    private var ringInner: GradientDrawable? = null
    private var ringColor = 0
    private var ringAnimator: ValueAnimator? = null

    /** The negotiating breath: the outer rings swelling while the core talks to a server. */
    private var breathAnimator: ValueAnimator? = null

    // Cached easing curves (loaded once) so the imperative hero motion rides the same ease-out tempo
    // as res/interpolator and res/anim. No bounce.
    private val easeOutQuint by lazy { AnimationUtils.loadInterpolator(requireContext(), R.interpolator.ease_out_quint) }
    private val easeStandard by lazy { AnimationUtils.loadInterpolator(requireContext(), R.interpolator.ease_standard) }

    private val durState get() = resources.getInteger(R.integer.motion_state).toLong()
    private val durStateExit get() = resources.getInteger(R.integer.motion_state_exit).toLong()
    private val durReveal get() = resources.getInteger(R.integer.motion_reveal).toLong()
    private val durRevealExit get() = resources.getInteger(R.integer.motion_reveal_exit).toLong()

    // ---- The session clock ----

    /** Epoch millis the current session started, persisted so uptime survives a recreate. */
    private var connectionStartTime = 0L

    private val uptimeRunnable = object : Runnable {
        override fun run() {
            if (connectionStartTime == 0L || !isBindingInitialized) return
            val elapsed = ((System.currentTimeMillis() - connectionStartTime) / 1000L).coerceAtLeast(0L)
            binding.tvUptime.text = getString(
                R.string.home_uptime_format,
                elapsed / 3600L,
                (elapsed % 3600L) / 60L,
                elapsed % 60L,
            )
            timerHandler.postDelayed(this, 1000L)
        }
    }

    // Auto-fallback: one-shot post-connect health check that switches to the fastest working server
    // if the current tunnel doesn't actually pass traffic. The "already fired this session" flag
    // lives in the ViewModel (autoFallbackUsed).
    private var healthCheckPending = false

    // True while the confirmation re-probe is armed or in flight. A single negative probe is not
    // evidence that the tunnel is dead — one dropped packet on a fine connection would otherwise
    // tear the user off a working server — so the fallback needs two consecutive failures.
    private var healthCheckConfirming = false

    private val healthCheckRunnable = Runnable {
        if (mainViewModel.isRunning.value == true) {
            healthCheckPending = true
            mainViewModel.testCurrentServerRealPing()
        }
    }

    // The confirmation probe. Re-checks the same conditions as the first one, so a tunnel the user
    // stopped meanwhile — or a fallback that already fired — cannot be probed back into action.
    private val healthRecheckRunnable = Runnable {
        if (mainViewModel.isRunning.value == true && !mainViewModel.autoFallbackUsed) {
            healthCheckPending = true
            mainViewModel.testCurrentServerRealPing()
        }
    }

    /** The 30s latency probe of the ACTIVE server, and the only producer of the «мс» figure. */
    private val latencyRunnable = object : Runnable {
        override fun run() {
            if (mainViewModel.isRunning.value != true) return
            mainViewModel.testCurrentServerRealPing()
            timerHandler.postDelayed(this, LATENCY_INTERVAL_MS)
        }
    }

    // Connect watchdog: if a start neither succeeds nor reports a failure within the timeout (e.g.
    // the core/daemon process crashed without broadcasting any state), recover the UI to idle
    // instead of hanging forever on «Подключение…».
    private val connectWatchdogRunnable = Runnable {
        if (mainViewModel.isRunning.value != true) {
            connectInProgress = false
            tunnelError = true
            applyRunningState(isLoading = false, isRunning = false)
        }
    }

    private companion object {
        const val KEY_CONNECTION_START = "cache_connection_start_time"

        const val HEALTH_CHECK_DELAY_MS = 7000L

        // Gap before the confirmation re-probe: long enough for a momentary DNS/test-URL hiccup to
        // pass, short enough that a genuinely dead tunnel is not endured.
        const val HEALTH_CHECK_RECHECK_MS = 2000L

        // Upper bound for a connect attempt before the UI gives up and returns to idle.
        const val CONNECT_TIMEOUT_MS = 20000L

        // A restart must not start the new core until the daemon process reports the old one
        // stopped; these bound that wait.
        const val RESTART_STOP_TIMEOUT_MS = 6000L
        const val RESTART_STOP_POLL_MS = 50L

        // The dial to turn if the probe ever costs battery. The design does not change.
        const val LATENCY_INTERVAL_MS = 30_000L

        // Three consecutive failed probes, not one: a single dropped packet is not a dead server.
        const val SILENT_SERVER_FAILURES = 3

        // Under this many days left the subscription is «Истекает».
        const val EXPIRING_DAYS = 3

        // Below 100 the speed carries one decimal, at or above it none.
        const val SPEED_DECIMAL_BELOW = 100.0

        // ~2088-01-01 in epoch seconds. Some panels send a date this far out to mean "never".
        const val UNLIMITED_EXPIRE_SECONDS = 3_723_840_000L

        const val DISABLED_ALPHA = 0.38f
        const val RIPPLE_ALPHA = 26 // 10% of 255

        // The three rings are ONE colour at three opacities: outermost faintest, the disc's own
        // brightest. That is the whole gradient this object is allowed, and it is a state channel
        // rather than decoration.
        const val RING_ALPHA_OUTER = 56 // ~22%
        const val RING_ALPHA_MID = 128 // ~50%
        const val OPAQUE = 255

        // The negotiating breath, on the two outer rings. Same 850ms reverse the old build breathed
        // the (now banned) halo glow with, moved onto the rings where it belongs.
        const val BREATH_PERIOD_MS = 850L
        const val BREATH_ALPHA_MIN = 90

        // The cold-start assemble plays once per PROCESS, not on every theme/language recreate.
        private var heroAssembled = false
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            // The system prompt is modal and the user may take as long as he likes over it, so the
            // watchdog was stood down while it was up (see [startVpnWithPermission]). The attempt's
            // clock starts here, when a start is actually going out.
            scheduleConnectWatchdog()
            startV2Ray()
        } else {
            // A CANCELLED ACTION IS NOT A FAILURE. Declining Android's own VPN prompt used to leave
            // the attempt in flight, so the object sat on «Подключение…» and then reported
            // «Не удалось подключиться» for something the user chose to do.
            connectInProgress = false
            tunnelError = false
            cancelConnectWatchdog()
            applyRunningState(isLoading = false, isRunning = false)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildConnectObject()
        wireHeaderRow()
        wireConnect()
        wireStatusStrip()
        wireScrollHairline()
        setupServerList()
        setupHomeMetaPager()
        observeTunnel()
        observeAccount()
        observeNetwork()
        applyListInsets()
        render()
        playColdStartAssemble()
    }

    /**
     * Re-reads what other entry points may have changed while this tab was up but not in front.
     * Hidden tabs stay RESUMED, so this also runs for a tab that is attached but not on screen —
     * which is exactly what makes it correct the moment it is shown again.
     */
    override fun onResume() {
        super.onResume()
        refreshAccountData()
        refreshServerSurfaces(-1)
        render()
    }

    override fun onDestroyView() {
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(healthRecheckRunnable)
        timerHandler.removeCallbacks(latencyRunnable)
        timerHandler.removeCallbacks(connectWatchdogRunnable)
        timerHandler.removeCallbacks(uptimeRunnable)
        ringAnimator?.cancel()
        ringAnimator = null
        breathAnimator?.cancel()
        breathAnimator = null
        ringOuter = null
        ringMid = null
        ringInner = null
        homeAdapter = null
        homeMetaAdapter = null
        unregisterNetworkCallback()
        super.onDestroyView()
    }

    // ==================== What the shell calls ====================

    /**
     * The server cache changed: rebuild the inline list and the subscription carousel from it.
     *
     * @param index the changed server's index in the cache, or -1 for the whole list.
     */
    fun bindList(index: Int) {
        if (!isBindingInitialized) return
        refreshServerSurfaces(index)
        render()
        // Whether there is a server at all is one of the two inputs to the shell's nav gates.
        mainHost.refreshNavGates()
    }

    /**
     * The server list scrolls with this screen rather than inside itself, so the inset that clears
     * the overlaid bottom bar belongs to the scroll CONTENT. Never smaller than the layout's own
     * bottom breathing room, so a zero inset (before the shell has measured the window) cannot
     * leave the last row flush against the edge.
     */
    fun applyListInsets() {
        if (!isBindingInitialized) return
        val floor = resources.getDimensionPixelSize(R.dimen.space_24)
        binding.homeContent.updatePadding(bottom = maxOf(mainHost.listBottomInset, floor))
    }

    /** Repaints after the shell changed which server is selected. */
    fun refreshSelectedServer() {
        if (!isBindingInitialized) return
        homeAdapter?.syncSelection()
        render()
    }

    /**
     * The shell has just written a new selected server. The shell owns the write because more than
     * one surface reads the selection; this mirrors it into the list so exactly one row is ever
     * painted selected, and drops the previous server's latency reading.
     */
    fun onSelectedServerChanged(previous: String?, guid: String) {
        if (!isBindingInitialized) return
        homeAdapter?.setSelectServer(previous, guid)
        pingMs = null
        pingProbeFailures = 0
        render()
    }

    /** A server was removed elsewhere; the inline list follows it. */
    fun removeServerRow(guid: String, position: Int) {
        if (!isBindingInitialized) return
        refreshServerSurfaces(-1)
        render()
        mainHost.refreshNavGates()
    }

    /**
     * Moves an already-running tunnel onto the newly selected server — the action on the shell's
     * «применить» snackbar. Goes through the connect state machine (in-progress flag, connecting
     * visual, watchdog) so a restart that stalls is reported like any other failed start.
     */
    fun applySelectionToRunningTunnel() {
        connectInProgress = true
        tunnelError = false
        if (isBindingInitialized) applyRunningState(isLoading = true, isRunning = true)
        scheduleConnectWatchdog()
        restartV2Ray()
    }

    /** @see MainHost.toggleConnection */
    fun toggleConnection() = handleConnectAction()

    /** @see MainHost.restartConnection */
    fun restartConnection() = restartV2Ray()

    /**
     * @see MainHost.showStatus
     *
     * A transient EVENT, so it is a `Snackbar`, offset above the overlaid bottom navigation with the
     * shell's own inset figure. Persistent CONDITIONS go on the status strip instead.
     */
    fun showStatus(text: CharSequence) {
        if (!isBindingInitialized) return
        val bar = Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT)
        (bar.view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.bottomMargin += mainHost.listBottomInset
            bar.view.layoutParams = lp
        }
        bar.show()
    }

    /**
     * A background load opened somewhere in the shell (a subscription refresh, an import, an
     * export). It spins the connect object's sweep — the shared indicator this screen has always
     * used for exactly these two things — and reports on the status strip at the LOWEST priority,
     * so a real condition always wins the single strip.
     */
    fun showConnectArc() {
        backgroundLoads++
        if (isBindingInitialized) render()
    }

    /** @see showConnectArc */
    fun hideConnectArc() {
        if (backgroundLoads > 0) backgroundLoads--
        if (isBindingInitialized) render()
    }

    // ==================== Building the connect object ====================

    /**
     * Builds the three rings, the disc's fill and the frame's ripple/focus foreground from THEME
     * ATTRIBUTES.
     *
     * These are drawables and not drawable files on purpose. The rings' colour is the screen's whole
     * state channel and changes at runtime between five values; the shipped `bg_connect_ring.xml`
     * solved that with six raw hex literals across three theme variants, two of which measured
     * 2.33:1 and 1.29:1 against the ground and failed the 3:1 boundary floor. Built here, every
     * colour is resolved through `?attr` and is therefore correct in blue, light and the mono
     * overlay at once, with no file to keep in three copies.
     *
     * THE FRAME IS THE CONTROL: 224dp of touch target, and @anim/press_scale scales the rings, the
     * disc and the shield together, because they are one object.
     */
    private fun buildConnectObject() {
        val frame = binding.connectFrame
        val stroke = resources.getDimensionPixelSize(R.dimen.stroke_ring)
        val step = resources.getDimensionPixelSize(R.dimen.space_12)
        ringColor = idleRingColor()

        // Rings 3 and 2 are two layers of the frame's background — 224dp and, inset
        // by one @dimen/space_12 all round, 200dp. They are layers rather than child views
        // because a child of a FrameLayout is measured inside the PADDED box, so no child of this
        // frame can be wider than the 176dp disc, and these two have to be wider than it.
        ringOuter = ovalStroke(stroke)
        ringMid = ovalStroke(stroke)
        frame.background = LayerDrawable(arrayOf(ringOuter, ringMid)).apply {
            setLayerInset(1, step, step, step, step)
        }
        // Ring 1 of 3, the disc's own, over the disc's fill.
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
        }
        ringInner = ovalStroke(stroke)
        binding.connectDisc.background = LayerDrawable(arrayOf(fill, ringInner))
        applyRingColor(ringColor)

        // Every focusable control draws a 2dp accent ring. The disc had none at all — the product's
        // primary control was unreachable by keyboard, D-pad and switch access in practice.
        val focus = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        resources.getDimensionPixelSize(R.dimen.stroke_focus),
                        themeColor(androidx.appcompat.R.attr.colorPrimary),
                    )
                },
            )
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val ripple = ColorUtils.setAlphaComponent(
            themeColor(androidx.appcompat.R.attr.colorPrimary),
            RIPPLE_ALPHA,
        )
        frame.foreground = RippleDrawable(ColorStateList.valueOf(ripple), focus, mask)

        // The confirm ring is the same geometry in the accent, emitted exactly once.
        binding.connectRingPulse.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(stroke, themeColor(androidx.appcompat.R.attr.colorPrimary))
        }

        // The monogram's circle: the P3 plane, never the accent.
        binding.layoutHomeAccount.tvAvatarInitial.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
        }
    }

    private fun ovalStroke(stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(stroke, Color.TRANSPARENT)
    }

    /** One hue, three opacities. The geometry never changes; only this does. */
    private fun applyRingColor(colour: Int) {
        val stroke = resources.getDimensionPixelSize(R.dimen.stroke_ring)
        ringOuter?.setStroke(stroke, ColorUtils.setAlphaComponent(colour, RING_ALPHA_OUTER))
        ringMid?.setStroke(stroke, ColorUtils.setAlphaComponent(colour, RING_ALPHA_MID))
        ringInner?.setStroke(stroke, colour)
    }

    /**
     * The cold-start assemble: the object settles in once per process, scaling up from 0.9 as it
     * fades in. It is the first thing the app says and it was removed by the redesign; it is back,
     * still once per process and still skipped under reduced motion.
     */
    private fun playColdStartAssemble() {
        if (heroAssembled) return
        heroAssembled = true
        val frame = binding.connectFrame
        if (frame.reducedMotion()) return
        frame.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shield_assemble))
    }

    private fun wireConnect() {
        binding.connectFrame.onSingleClick(Haptic.PRESS) { handleConnectAction() }
    }

    private fun wireHeaderRow() {
        binding.layoutHomeAccount.rowAccount.onSingleClick { openAccount() }
        binding.btnHomeAdd.onSingleClick { mainHost.showAddMenu(it) }
        // The restored «Привязать Telegram» banner: the banner itself is the action, the ✕ is the
        // dismissal, and the dismissal is written to MMKV so the offer is made once.
        binding.ctaLinkTelegram.onSingleClick { openTelegramLink() }
        binding.btnCtaDismiss.onSingleClick {
            MmkvManager.encodeSettings(AppConfig.PREF_LINK_TG_CTA_DISMISSED, true)
            binding.ctaLinkTelegram.isVisible = false
        }
    }

    /**
     * Главная's «Привязать Telegram» banner, restored from 5e8cd54.
     *
     * ONE state, and nothing else on this screen covers it: a departament подписка was PASTED and
     * the user has never signed in. He has servers, so [resolveGate] returns null and there is no
     * gate block; the gate's own «Привязать Telegram» secondary belongs to the
     * signed-in-with-no-подписка shape and never reaches him.
     *
     * The подписка must be a departament one — a pasted foreign link must not surface an account
     * affordance for an account it has nothing to do with, which is the same gate
     * [MainActivity.accountAccessAllowed] applies to the Аккаунт tab.
     */
    private fun paintLinkCta() {
        val show = BackendConfig.isConfigured() &&
            !AccountSession.isLoggedIn() &&
            !MmkvManager.decodeSettingsBool(AppConfig.PREF_LINK_TG_CTA_DISMISSED) &&
            SubscriptionOrigin.hasDepartamentSubscription()
        binding.ctaLinkTelegram.isVisible = show
    }

    /**
     * The inline placement of the shared status strip. The component file is the shell's DOCKED bar
     * — full-bleed, hairline on top — so two properties are adjusted here and nowhere else: the
     * hairline goes, and the bar is clipped to @dimen/radius_control so it reads as a block inside
     * the content rather than as an edge of the window.
     */
    private fun wireStatusStrip() {
        val strip = binding.layoutStatusStrip
        strip.statusStripHairline.isVisible = false
        val radius = resources.getDimension(R.dimen.radius_control)
        strip.statusStripBar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        strip.statusStripBar.clipToOutline = true
        strip.statusStrip.isVisible = false
    }

    /**
     * The ONE scroll-linked change on this screen: a hairline under the header row fades in once the
     * content has moved. No colour step, no elevation, no shadow, no collapsing title.
     */
    private fun wireScrollHairline() {
        // The SAM constructor is explicit because View and NestedScrollView both declare a
        // setOnScrollChangeListener overload and a bare lambda is ambiguous between them.
        binding.homeTabRoot.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                if (!isBindingInitialized) return@OnScrollChangeListener
                val hairline = binding.headerHairline
                val target = if (scrollY > 0) 1f else 0f
                if (hairline.alpha == target) return@OnScrollChangeListener
                hairline.animate().cancel()
                if (hairline.reducedMotion()) {
                    hairline.alpha = target
                    return@OnScrollChangeListener
                }
                hairline.animate().alpha(target)
                    .setDuration(durState).setInterpolator(easeStandard).start()
            }
        )
    }

    // ==================== The inline server list ====================

    /**
     * Builds Главная's server list. Row actions come from [MainHost.serverActions] — one listener
     * for every list in the app — and a long press opens the shell's server-actions sheet, which is
     * the only route to edit, share, QR and delete.
     *
     * Tapping a row SELECTS and never connects; that contract lives in `MainActivity.setSelectServer`
     * and is deliberately not re-implemented here.
     */
    private fun setupServerList() {
        val listAdapter = MainRecyclerAdapter(mainViewModel, mainHost.serverActions)
        listAdapter.onItemLongClick = { guid -> mainHost.showServerActions(guid) }
        homeAdapter = listAdapter
        binding.rvHomeServers.apply {
            setHasFixedSize(false)
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            this@HomeFragment.addCustomDividerToRecyclerView(this, R.drawable.custom_divider)
            adapter = listAdapter
        }
        refreshServerList(-1)
    }

    /**
     * Repaints the list from the one cache.
     *
     * Section headers are OFF: the subscription card above the list is that подписка's header, and a
     * second one inside the list would say the same thing twice.
     *
     * This does NOT touch the carousel, because a bulk ping delivers one result per server and
     * rebuilding a ViewPager2 once per server would thrash it for a change no card shows.
     */
    private fun refreshServerList(index: Int) {
        val groups = mainViewModel.getProviderGroups()
        homeAdapter?.setSections(mainViewModel.serversCache, groups, showHeaders = false, index = index)
    }

    /** The cache itself changed — an import, a delete, a refresh — so both surfaces follow it. */
    private fun refreshServerSurfaces(index: Int) {
        refreshServerList(index)
        rebuildHomeMeta()
    }

    // ==================== The subscription card carousel ====================

    /**
     * One page per подписка. Per-page actions carry the page's own subscription id — pin, delete,
     * support and Telegram act on the card under the thumb — while collapse, ping and refresh are
     * genuinely list-wide.
     */
    private fun setupHomeMetaPager() {
        val pagerAdapter = HomeMetaPagerAdapter(
            bindPage = { meta, subId, sub -> bindMetaBar(meta, subId, sub) },
            onToggleList = { toggleHomeServerList() },
            // "A check is in flight" is the ViewModel's own transient state (`isMeasuring`) and it
            // publishes a repaint itself, so this asks for the test and nothing else. Marking the
            // rows here by writing a negative delay into the STORE is what the shipped build did,
            // and it is exactly what `ServerAffiliationInfo` forbids: a stored negative outlives
            // the run that wrote it and every reader treats it as "unreachable" — including
            // «Удалить недоступные», which then deletes the server.
            onPingAll = { mainViewModel.testAllServers() },
            onRefreshAll = { mainHost.refreshSubscriptions() },
            onTogglePin = { subId -> toggleHomePin(subId) },
            onDeleteSub = { subId -> confirmDeleteSubscription(subId) },
            onOpenSupport = { subId -> openSubUrl(MmkvManager.decodeSubscription(subId)?.supportUrl) },
            onOpenTelegram = { subId -> openSubUrl(MmkvManager.decodeSubscription(subId)?.supportUrl) },
            collapsed = { homeListCollapsed },
        )
        homeMetaAdapter = pagerAdapter
        binding.vpHomeMeta.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1
            clipToPadding = false
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                private var dragged = false

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) dragged = true
                }

                override fun onPageSelected(position: Int) {
                    homeMetaPage = position
                    updateHomeMetaDots(position)
                    if (dragged) {
                        if (isBindingInitialized) binding.vpHomeMeta.tickHaptic()
                        dragged = false
                    }
                }
            })
        }
        rebuildHomeMeta()
    }

    /**
     * Rebuilds the carousel from the current groups, keeping the user on the same подписка across
     * rebuilds (pin reorders, refresh, deletes) by restoring the page by its subscription id. Page
     * dots and the inter-page gap appear only past one card.
     */
    private fun rebuildHomeMeta() {
        val adapter = homeMetaAdapter ?: return
        val ids = mainViewModel.getProviderGroups().map { it.id }.filter { it.isNotEmpty() }
        val keepSubId = homeMetaSubIds.getOrNull(homeMetaPage)
        val count = ids.size
        val many = count > 1
        // A подписка with no card is a slot with no content: the carousel is only on screen when
        // there is at least one to draw. Local-only servers have no подписка and no card.
        binding.groupHomeMeta.isVisible = count > 0

        // Same подписки, changed contents (a refresh moved the traffic figure and the timestamp):
        // repaint the pages in place rather than replacing them, so the card under the thumb does
        // not jump back to the first one every time a subscription updates.
        if (ids == homeMetaSubIds && adapter.itemCount == count) {
            adapter.repaint()
            measureHomeMetaHeight()
            return
        }
        homeMetaSubIds = ids
        adapter.submit(ids)
        // Neighbour cards peek past the 16dp gutter; a 12dp gap keeps them from touching.
        binding.vpHomeMeta.setPageTransformer(
            if (many) {
                CompositePageTransformer().apply {
                    addTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.space_12)))
                }
            } else {
                null
            }
        )
        // Keep the user on the card they were reading; on the FIRST build there is no such card, so
        // open on the подписка the selected server belongs to rather than always on the first one.
        val restore = keepSubId?.let { ids.indexOf(it) }?.takeIf { it >= 0 }
            ?: ids.indexOf(currentMetaSubId()).takeIf { it >= 0 }
            ?: 0
        homeMetaPage = restore.coerceIn(0, (count - 1).coerceAtLeast(0))
        if (count > 0) binding.vpHomeMeta.setCurrentItem(homeMetaPage, false)
        buildHomeMetaDots(count)
        updateHomeMetaDots(homeMetaPage)
        binding.llHomeMetaDots.isVisible = many
        measureHomeMetaHeight()
    }

    /**
     * ViewPager2 cannot wrap_content, so fix its height to the tallest page. Each page's height
     * varies (traffic row, the operator's notice), so measure every подписка's card at the page
     * width and take the max — one stable height, so peeking neighbours stay aligned.
     */
    private fun measureHomeMetaHeight() {
        if (homeMetaSubIds.isEmpty()) return
        binding.vpHomeMeta.doOnPreDraw {
            if (!isBindingInitialized) return@doOnPreDraw
            val pager = binding.vpHomeMeta
            val innerWidth = pager.width - pager.paddingStart - pager.paddingEnd
            if (innerWidth <= 0) return@doOnPreDraw
            val widthSpec = View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val inflater = LayoutInflater.from(requireContext())
            var maxH = 0
            for (id in homeMetaSubIds) {
                val sub = MmkvManager.decodeSubscription(id) ?: continue
                val probe = LayoutSubscriptionMetaBarBinding.inflate(inflater, pager, false)
                (probe.root.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)
                bindMetaBar(probe, id, sub)
                probe.root.measure(widthSpec, heightSpec)
                maxH = maxOf(maxH, probe.root.measuredHeight)
            }
            if (maxH > 0 && pager.layoutParams.height != maxH) {
                pager.layoutParams = pager.layoutParams.apply { height = maxH }
            }
        }
    }

    /** Rebuilds the page dots to match [count] pages (nothing shown for 0/1 page). */
    private fun buildHomeMetaDots(count: Int) {
        val container = binding.llHomeMetaDots
        container.removeAllViews()
        if (count <= 1) return
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val activeSize = resources.getDimensionPixelSize(R.dimen.dot_size_active)
        val gap = resources.getDimensionPixelSize(R.dimen.space_4)
        for (i in 0 until count) {
            val selected = i == homeMetaPage
            val dim = if (selected) activeSize else size
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dim, dim).apply {
                    if (i > 0) marginStart = gap
                }
                setBackgroundResource(if (selected) R.drawable.dot_active else R.drawable.dot_inactive)
            }
            container.addView(dot)
        }
    }

    /** Swaps the dot backgrounds/sizes so only [position]'s dot reads as active. */
    private fun updateHomeMetaDots(position: Int) {
        val container = binding.llHomeMetaDots
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val activeSize = resources.getDimensionPixelSize(R.dimen.dot_size_active)
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val selected = i == position
            dot.setBackgroundResource(if (selected) R.drawable.dot_active else R.drawable.dot_inactive)
            val dim = if (selected) activeSize else size
            dot.layoutParams = dot.layoutParams.apply {
                width = dim
                height = dim
            }
        }
    }

    /**
     * The card's chevron shows and hides the server list under it — never the card's own body, which
     * stays on screen so the traffic figure is always readable.
     */
    private fun toggleHomeServerList() {
        homeListCollapsed = !homeListCollapsed
        // The chevron lives on EVERY page and the collapse is global, so all of them follow it —
        // not just the one under the thumb, which would leave the neighbour pointing the wrong way.
        homeMetaAdapter?.repaint()
        render()
    }

    private fun toggleHomePin(subId: String) {
        val sub = MmkvManager.decodeSubscription(subId) ?: return
        sub.pinned = !sub.pinned
        MmkvManager.encodeSubscription(subId, sub)
        homeMetaAdapter?.notifyItemChanged(homeMetaPage)
        mainViewModel.reloadServerList()
    }

    /**
     * Deleting a подписка, from the card that shows it. The owner reported that подписки could not
     * be deleted on the phone at all — the card that carried this was removed with the rest of the
     * screen, and nothing replaced it.
     */
    private fun confirmDeleteSubscription(subId: String) {
        if (subId.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.home_sub_delete_title)
            .setMessage(R.string.home_sub_delete_message)
            .setPositiveButton(R.string.home_sub_delete_confirm) { _, _ ->
                MmkvManager.removeSubscription(subId)
                mainViewModel.reloadServerList()
                showStatus(getString(R.string.home_sub_deleted))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Opens the подписка's own link, and says so plainly when the device has nothing to open it. */
    private fun openSubUrl(url: String?) {
        if (url.isNullOrBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { showStatus(getString(R.string.home_sub_link_failed)) }
    }

    /**
     * The card's heading.
     *
     * WHEN THE ПОДПИСКА CAME FROM THE ACCOUNT, THE ACCOUNT'S OWN NICKNAME WINS. That is the owner's
     * instruction — «чтобы при подтягивании подписки с акка писался ник подписки» — and it is the one
     * thing the old build got wrong: it preferred the provider-sent `profile-title`, which for this
     * deployment is the same generic service name on every подписка, so two differently-named
     * подписки drew the same label.
     *
     * [accountNameFor] reads the LIVE account list, so renaming a подписка in Аккаунт shows here
     * without waiting for a re-import; the imported remark is the same nickname one refresh behind,
     * and stands in when the account has not answered yet.
     */
    private fun metaTitle(subId: String, sub: SubscriptionItem): String {
        accountNameFor(subId)?.let { return it }
        val remarks = sub.remarks.trim()
        val fromRemarks = remarks.takeIf { it.isNotEmpty() && !it.equals("Default", ignoreCase = true) }
        if (isAccountManaged(subId)) {
            // Account-managed: the remark IS the nickname the import wrote, so it outranks the
            // provider's generic title.
            fromRemarks?.let { return it }
        }
        sub.profileTitle.takeIf { it.isNotBlank() }?.let { return it }
        fromRemarks?.let { return it }
        return getString(R.string.home_sub_untitled)
    }

    /** True when this local подписка is one the account manages, rather than a pasted link. */
    private fun isAccountManaged(subId: String): Boolean =
        runCatching { AuthTokenStore.getManagedGuids().containsValue(subId) }.getOrDefault(false)

    /**
     * The nickname the account returns for this local подписка, or null when it is not an account
     * one (or the account has not answered yet).
     *
     * The import remembers each подписка under an identity key — the constant "root" for the account's
     * primary, the remnawave uuid / id for a secondary — so the mapping back to a `SubInfoDto` is
     * that key, not a name match.
     */
    private fun accountNameFor(subId: String): String? {
        val identity = runCatching {
            AuthTokenStore.getManagedGuids().entries.firstOrNull { it.value == subId }?.key
        }.getOrNull() ?: return null
        val info = accountSubs.firstOrNull { candidate ->
            val key = if (candidate.type.equals(SubscriptionSyncManager.TYPE_ROOT, ignoreCase = true)) {
                SubscriptionSyncManager.TYPE_ROOT
            } else {
                candidate.remnawaveUuid.ifBlank { candidate.id }
            }
            key.isNotBlank() && key == identity
        } ?: return null
        return info.displayName?.takeIf { it.isNotBlank() }
            ?: info.defaultLabel?.takeIf { it.isNotBlank() }
    }

    /**
     * The line under the card's heading: when the подписка last updated, and how often it does. The
     * timestamp is formatted from a resource pattern rather than a locale-dependent default so a
     * Russian sentence never ends in an English month.
     */
    private fun metaSubtitle(sub: SubscriptionItem): String {
        val last = if (sub.lastUpdated > 0L) {
            SimpleDateFormat(getString(R.string.home_date_numeric_time), Locale.getDefault())
                .format(Date(sub.lastUpdated))
        } else {
            getString(R.string.home_sub_updated_never)
        }
        val interval = if (!sub.autoUpdate) {
            getString(R.string.home_sub_auto_update_off)
        } else {
            val minutes = sub.updateInterval
            if (minutes >= 60L && minutes % 60L == 0L) {
                getString(R.string.home_sub_interval_hours, (minutes / 60L).toInt())
            } else {
                getString(R.string.home_sub_interval_minutes, minutes.toInt())
            }
        }
        return getString(R.string.home_sub_meta, last, getString(R.string.home_sub_auto_update, interval))
    }

    /**
     * Paints one card: the name, the meta line, the traffic pill, the expiry marker, the operator's
     * notice and the support / Telegram actions. The pill is a rounded track with the usage figure
     * centred on it; the expiry shows ∞ when there is none (or an effectively unlimited one).
     */
    private fun bindMetaBar(meta: LayoutSubscriptionMetaBarBinding, subId: String, sub: SubscriptionItem?) {
        if (sub == null) {
            meta.root.isVisible = false
            return
        }
        meta.root.isVisible = true
        meta.tvSubTitle.text = metaTitle(subId, sub)
        meta.tvMetaSubtitle.text = metaSubtitle(sub)
        meta.tvMetaSubtitle.isVisible = true

        // Accessible names for the card's actions, in this screen's own voice.
        meta.btnCollapse.contentDescription = getString(
            if (homeListCollapsed) R.string.home_sub_cd_expand else R.string.home_sub_cd_collapse
        )
        meta.btnPing.contentDescription = getString(R.string.home_sub_cd_ping)
        meta.btnRefresh.contentDescription = getString(R.string.home_sub_cd_refresh)
        meta.btnSupport.contentDescription = getString(R.string.home_sub_cd_support)
        meta.btnTelegram.contentDescription = getString(R.string.home_sub_cd_telegram)

        val primaryColor = MaterialColors.getColor(meta.btnPin, androidx.appcompat.R.attr.colorPrimary)
        val onVariant = MaterialColors.getColor(meta.btnPin, com.google.android.material.R.attr.colorOnSurfaceVariant)
        meta.btnPin.setColorFilter(if (sub.pinned) primaryColor else onVariant)
        meta.btnPin.contentDescription =
            getString(if (sub.pinned) R.string.home_sub_cd_unpin else R.string.home_sub_cd_pin)

        // The operator's notice, verbatim, as plain text — never markup.
        if (sub.announce.isNotBlank()) {
            meta.tvAnnounce.isVisible = true
            meta.tvAnnounce.text = sub.announce
        } else {
            meta.tvAnnounce.isVisible = false
        }
        meta.btnSupport.isVisible = sub.supportUrl.isNotBlank()
        meta.btnTelegram.isVisible = sub.supportUrl.isNotBlank()

        if (!sub.hasUserInfo) {
            meta.layoutTraffic.isVisible = false
            return
        }
        meta.layoutTraffic.isVisible = true

        val onSurfaceColor = MaterialColors.getColor(meta.tvTraffic, com.google.android.material.R.attr.colorOnSurface)
        val variantColor = MaterialColors.getColor(meta.tvExpiry, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val dangerColor = ContextCompat.getColor(requireContext(), R.color.color_destructive_text)

        meta.tvTraffic.text = if (sub.isUnlimited) {
            getString(R.string.home_sub_traffic_unlimited, sub.usedTraffic.toTrafficString())
        } else {
            getString(
                R.string.home_sub_traffic_used,
                sub.usedTraffic.toTrafficString(),
                sub.totalTraffic.toTrafficString(),
            )
        }
        meta.tvTraffic.setTextColor(onSurfaceColor)
        // Unlimited traffic keeps an empty rounded track behind the label instead of a filled bar.
        // A horizontal ProgressBar takes an Int against max=1000, so the fraction is unchanged.
        val fillFraction = if (sub.isUnlimited) 0f else sub.trafficFraction
        meta.progressTraffic.progress = (fillFraction * 1000).toInt()

        val expiryUnlimited = sub.expire <= 0L || sub.expire >= UNLIMITED_EXPIRE_SECONDS
        when {
            expiryUnlimited -> {
                meta.tvExpiry.text = getString(R.string.home_sub_infinity)
                meta.tvExpiry.setTextColor(variantColor)
            }

            sub.isExpired -> {
                meta.tvExpiry.text = getString(R.string.home_sub_expired)
                meta.tvExpiry.setTextColor(dangerColor)
            }

            else -> {
                meta.tvExpiry.text = getString(R.string.home_sub_expires, formatDate(sub.expire * 1000L))
                meta.tvExpiry.setTextColor(variantColor)
            }
        }
        meta.tvExpiry.isVisible = true
    }

    /** The подписка the selected server belongs to, else the first one there is. */
    private fun currentMetaSubId(): String {
        mainViewModel.findSubscriptionIdBySelect()?.takeIf { it.isNotEmpty() }?.let { return it }
        return mainViewModel.getProviderGroups().firstOrNull()?.id.orEmpty()
    }

    // ==================== Observers ====================

    private fun observeTunnel() {
        mainViewModel.updateSpeedAction.observe(viewLifecycleOwner) { (down, up) ->
            downBytesPerSec = down
            upBytesPerSec = up
            if (renderedConn == Conn.CONNECTED) paintFigures()
        }

        mainViewModel.fastConnectAction.observe(viewLifecycleOwner) { guid ->
            // One-shot event: ignore the retained value replayed on recreate/rotation.
            if (!mainViewModel.consumeFastConnectEvent()) return@observe
            if (guid == null) {
                // No candidate, so no restart follows — the fallback attempt is over.
                mainViewModel.fallbackInProgress = false
                connectInProgress = false
                tunnelError = true
                applyRunningState(isLoading = false, isRunning = false)
                return@observe
            }
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            } else {
                // The tunnel went down while the fallback was still testing, so there is no internal
                // stop left for the disconnect handler to protect.
                mainViewModel.fallbackInProgress = false
                connectInProgress = true
                applyRunningState(isLoading = true, isRunning = false)
                scheduleConnectWatchdog()
                startVpnWithPermission()
            }
        }

        mainViewModel.isRunning.observe(viewLifecycleOwner) { isRunning ->
            // A definitive running/stopped state arrived (success or failure): the connect attempt
            // is over, so the watchdog is no longer needed.
            cancelConnectWatchdog()

            // Play the signature confirm/reverse ONLY on a genuine live transition — a connect the
            // user just triggered (connectInProgress), or a real running-state flip. Never on the
            // LiveData replay at launch (prev == null, no connect in progress), which jumps to end.
            val prevRunning = lastRunningState
            val liveTransition = connectInProgress || (prevRunning != null && prevRunning != isRunning)

            // A start that ends in "not running" while a connect was in flight is a FAILURE, and it
            // is reported on the screen rather than as a toast that has already gone.
            tunnelError = !isRunning && connectInProgress
            disconnecting = false

            applyRunningState(isLoading = false, isRunning = isRunning, animate = liveTransition)

            if (isRunning) {
                startConnectionTimer()
                scheduleHealthCheckIfEnabled()
                startLatencyProbe()
            } else {
                stopConnectionTimer()
                cancelHealthCheck()
                stopLatencyProbe()
            }

            connectInProgress = false
            lastRunningState = isRunning
        }

        mainViewModel.delayResultAction.observe(viewLifecycleOwner) { time ->
            onDelayResult(time)
        }

        mainViewModel.updateTestResultAction.observe(viewLifecycleOwner) {
            // A bulk ping finished for one row; the list repaints itself from the stored delay.
            if (isBindingInitialized) refreshServerList(-1)
        }
    }

    /**
     * One delay result serves two consumers: the latency shown beside the server identity, and the
     * auto-fallback health check. The latency update runs FIRST and unconditionally — it is the
     * reading, and the health check's `pending` flag says nothing about whether the figure is true.
     */
    private fun onDelayResult(time: Long) {
        if (mainViewModel.isRunning.value == true) {
            if (time >= 0) {
                pingMs = time.toInt()
                pingProbeFailures = 0
            } else {
                pingMs = null
                pingProbeFailures++
            }
            // A FULL render, and it has to be: the reading is drawn beside the server IDENTITY, and
            // that line is written by paintStatusLine. paintFigures() only fills the two speed
            // columns, so repainting those alone left the «мс» figure at whatever the screen last
            // resolved — which on a tunnel nobody navigates away from is "never shown at all".
            // MSG_STATE_DELAY_RESULT arrives once per 30s probe and once per health check, never
            // once per row of a bulk test, so this is not a hot path.
            if (isBindingInitialized) render()
        }

        if (!healthCheckPending) return
        healthCheckPending = false
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)) return
        if (mainViewModel.autoFallbackUsed || mainViewModel.isRunning.value != true) return
        if (time >= 0) {
            // The tunnel answered: whatever the earlier probe hit was transient.
            healthCheckConfirming = false
            return
        }
        if (!healthCheckConfirming) {
            // First failure only asks again — a working server must not be abandoned on one blip.
            healthCheckConfirming = true
            timerHandler.postDelayed(healthRecheckRunnable, HEALTH_CHECK_RECHECK_MS)
            return
        }
        // Second consecutive failure: the tunnel really isn't passing traffic.
        healthCheckConfirming = false
        // Mark used BEFORE restarting so the restart's own START_SUCCESS doesn't re-arm.
        mainViewModel.autoFallbackUsed = true
        // The stop->start that follows is ours, not a user disconnect.
        mainViewModel.fallbackInProgress = true
        showStatus(getString(R.string.auto_fallback_switching))
        // Exclude the server that just failed so we don't switch back to it.
        mainViewModel.fastConnect(excludeGuid = MmkvManager.getSelectServer())
    }

    /**
     * The account and its subscriptions. Both feed the header row and the subscription card, and
     * NEITHER blocks the other: a подписка that resolves while the profile is still in flight paints
     * its card and leaves the header a skeleton.
     */
    private fun observeAccount() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccountSession.state.collect { applyAccountState(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountViewModel.subscriptions.collect { subs ->
                    accountSubs = subs
                    subsResolved = true
                    subsError = false
                    if (!isBindingInitialized) return@collect
                    // The account's nicknames are what the cards are named by, so a fresh list
                    // repaints them.
                    homeMetaAdapter?.repaint()
                    render()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountViewModel.error.collect { error ->
                    if (error == null) return@collect
                    // Consumed here, so the same failure is not re-reported on every recomposition
                    // of this collector. This ViewModel is fragment-scoped, so clearing it cannot
                    // take an error away from Аккаунт.
                    accountViewModel.clearError()
                    accountResolved = true
                    subsResolved = true
                    subsError = true
                    if (!isBindingInitialized) return@collect
                    render()
                    showRetry()
                }
            }
        }
        refreshAccountData()
    }

    /** Every error ships a recovery affordance. */
    private fun showRetry() {
        val bar = Snackbar.make(binding.root, getString(R.string.home_sub_stale), Snackbar.LENGTH_LONG)
            .setAction(R.string.home_action_retry) { refreshAccountData() }
        (bar.view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.bottomMargin += mainHost.listBottomInset
            bar.view.layoutParams = lp
        }
        bar.show()
    }

    private fun applyAccountState(state: AccountSession.AccountState) {
        val loggedIn = BackendConfig.isConfigured() && state is AccountSession.AccountState.LoggedIn
        if (state is AccountSession.AccountState.LoggedIn) {
            accountResolved = true
            if (isBindingInitialized) bindAccountRow(state.profile)
        } else {
            accountResolved = true
            accountSubs = emptyList()
            subsResolved = true
        }
        if (isBindingInitialized) render()
        // Login state feeds the shell's nav gates: signing in reveals the Аккаунт item.
        mainHost.refreshNavGates()
        // Fire the one-shot post-login import only on a genuine logged-out -> logged-in transition,
        // not on the state replay that happens every time the activity restarts while signed in.
        if (loggedIn && !accountLoggedIn) onLoggedIn()
        // AND THE OTHER DIRECTION. An explicit sign-out (`AccountSession.wipe`) removes the
        // account's подписки and their серверы from the store, but nothing rebuilds the cache this
        // screen's list is painted from — so the rows outlived the session and stayed selectable
        // with no account behind them. The reload publishes through the shell, which repaints the
        // list, the carousel and the nav gates together.
        if (!loggedIn && accountLoggedIn) mainViewModel.reloadServerList()
        accountLoggedIn = loggedIn
    }

    private fun refreshAccountData() {
        if (!BackendConfig.isConfigured() || !AccountSession.isLoggedIn()) {
            accountSubs = emptyList()
            subsResolved = true
            accountResolved = true
            return
        }
        subsError = false
        accountViewModel.refreshProfile()
        accountViewModel.loadSubscriptions()
    }

    /**
     * Runs once when the user transitions to signed-in: auto-import their подписки and reload the
     * server list on success.
     */
    private fun onLoggedIn() {
        lifecycleScope.launch {
            AccountRepository().autoImportSubscriptions().onSuccess { mainViewModel.reloadServerList() }
            accountViewModel.loadSubscriptions()
        }
        showStatus(getString(R.string.toast_subscription_linked))
    }

    /**
     * Offline is a CONDITION, so it is observed and not polled: the strip has to appear the moment
     * the network goes and disappear the moment it returns, without the user touching anything.
     */
    private fun observeNetwork() {
        val manager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        offline = !hasInternet(manager)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postOffline(false)
            override fun onLost(network: Network) = postOffline(!hasInternet(manager))
            override fun onUnavailable() = postOffline(true)
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { networkCallback = null }
    }

    private fun postOffline(value: Boolean) {
        timerHandler.post {
            if (!isBindingInitialized) return@post
            if (offline == value) return@post
            offline = value
            render()
        }
    }

    private fun hasInternet(manager: ConnectivityManager): Boolean {
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val manager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    // ==================== Resolve ====================

    /**
     * Reads every input once and returns the screen. This is the only place a decision is made; from
     * here down the code paints what it is told.
     */
    private fun resolveState(): HomeState {
        val serverCount = mainViewModel.serversCache.size
        val selected = MmkvManager.getSelectServer()
        val profile = selected?.let { MmkvManager.decodeServerConfig(it) }
        val serverName = profile?.remarks
            ?.takeIf { it.isNotBlank() }
            // The leading country flag is the tile beside the name, never text inside it.
            ?.let { FlagUtil.stripLeadingFlag(it).trim() }
            ?.takeIf { it.isNotEmpty() }
        val serverFlag = profile?.let { FlagUtil.resolveFlag(it) }

        // A sync that produced servers is a sync that succeeded; the request is spent.
        if (serverCount > 0) syncRequested = false
        val gate = resolveGate(serverCount)
        val sub = resolveSubscription()

        val conn = when {
            gate != null -> Conn.GATED
            connectLoading -> Conn.CONNECTING
            disconnecting -> Conn.DISCONNECTING
            tunnelRunning -> Conn.CONNECTED
            tunnelError -> Conn.ERROR
            serverName == null -> Conn.NO_SERVER
            else -> Conn.DISCONNECTED
        }

        return HomeState(
            conn = conn,
            gate = gate,
            accountLoading = !accountResolved,
            serverName = serverName,
            serverFlag = serverFlag,
            serverCount = serverCount,
            sub = sub,
            condition = resolveCondition(sub, conn),
            stale = offline,
        )
    }

    /**
     * The gate replaces the card and the list only when they would have nothing true to show. On
     * this product that reduces to one test — there is not a single server to connect to — and then
     * WHICH gate is a question about what the user is missing.
     */
    private fun resolveGate(serverCount: Int): Gate? {
        if (serverCount > 0) return null
        if (!BackendConfig.isConfigured()) return Gate.ADD_SUBSCRIPTION
        if (!AccountSession.isLoggedIn()) return Gate.SIGN_IN
        // Still resolving: show nothing rather than guess a gate and swap it a second later.
        if (!subsResolved) return null
        if (accountSubs.isEmpty()) return Gate.BUY
        // A sync that the user asked for, that has finished, and that produced no server, is a
        // FAILURE and says so — offering «Загрузить серверы» a second time as if nothing happened
        // is the button that does nothing.
        return if (syncRequested && backgroundLoads == 0) Gate.SYNC_FAILED else Gate.SYNC_SERVERS
    }

    /**
     * The subscription, from the account when there is one and from the local metadata otherwise, so
     * a pasted подписка still tells the truth.
     *
     * `isTrial` is the backend's flag and is never inferred from a tariff name or a squad — in this
     * deployment the trial squad IS the paid base squad, so squad-based detection misclassifies real
     * paying customers.
     */
    private fun resolveSubscription(): Sub {
        if (BackendConfig.isConfigured() && AccountSession.isLoggedIn()) {
            if (!subsResolved) return Sub.Unknown
            val active = accountSubs.firstOrNull() ?: return Sub.None
            val until = parseIsoMillis(active.expireAtIso) ?: return Sub.Active(null)
            return classifyExpiry(until, active.isTrial)
        }
        // No account: the подписка's own `userinfo` expiry is the only truth available. Panels
        // sometimes send a huge timestamp instead of 0 for "never", which reads as an active
        // subscription with no date — which is exactly what Sub.Active(null) draws.
        val expireSeconds = MmkvManager.decodeSubscriptions()
            .mapNotNull { it.subscription.expire.takeIf { e -> e > 0L } }
            .minOrNull()
            ?: return Sub.None
        if (expireSeconds >= UNLIMITED_EXPIRE_SECONDS) return Sub.Active(null)
        return classifyExpiry(expireSeconds * 1000L, trial = false)
    }

    private fun classifyExpiry(untilMs: Long, trial: Boolean): Sub {
        val today = LocalDate.now(ZoneId.systemDefault())
        val until = Instant.ofEpochMilli(untilMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val daysLeft = ChronoUnit.DAYS.between(today, until).toInt()
        return when {
            daysLeft < 0 -> Sub.Expired(untilMs)
            trial -> Sub.Trial(untilMs)
            daysLeft < EXPIRING_DAYS -> Sub.Expiring(untilMs, daysLeft.coerceAtLeast(0))
            else -> Sub.Active(untilMs)
        }
    }

    /**
     * ONE condition, resolved by priority. The renderer never picks between conditions; if two are
     * true at once, the higher one is the only one shown.
     */
    private fun resolveCondition(sub: Sub, conn: Conn): Condition? {
        if (sub is Sub.Expired) {
            return Condition(
                text = getString(R.string.home_condition_expired),
                severity = Severity.ERROR,
                actionLabel = getString(R.string.home_action_renew),
                action = { openSubscription() },
            )
        }
        if (offline) {
            return Condition(
                text = getString(R.string.home_condition_offline),
                severity = Severity.INFO,
                actionLabel = getString(R.string.home_action_retry),
                action = { mainHost.refreshSubscriptions() },
            )
        }
        if (conn == Conn.CONNECTED && pingProbeFailures >= SILENT_SERVER_FAILURES) {
            // The list is on this screen, so the warning can point at it: the recovery is one tap
            // away and no longer a dead end.
            return Condition(
                text = getString(R.string.home_condition_silent),
                severity = Severity.WARN,
            )
        }
        if (sub is Sub.Expiring) {
            return Condition(
                text = getString(R.string.home_condition_expiring, formatDate(sub.untilMs)),
                severity = Severity.WARN,
                actionLabel = getString(R.string.home_action_renew),
                action = { openSubscription() },
            )
        }
        if (backgroundLoads > 0) {
            return Condition(
                text = getString(R.string.home_condition_loading),
                severity = Severity.INFO,
            )
        }
        return null
    }

    // ==================== Render ====================

    private fun render(animate: Boolean = false) {
        if (!isBindingInitialized) return
        val state = resolveState()
        paintHeader(state)
        paintLinkCta()
        paintCondition(state.condition)
        paintConnect(state, animate)
        paintFigures()
        paintSlot(state)
        renderedConn = state.conn
    }

    private fun paintHeader(state: HomeState) {
        val header = binding.layoutHomeAccount
        // The row is a navigation row in every state, so it is never hidden — an account entry point
        // that disappears is an account entry point the user cannot find.
        header.rowAccount.isVisible = true

        if (state.accountLoading) {
            header.accountText.isVisible = false
            if (!accountSkeletonArmed) {
                accountSkeletonArmed = true
                SkeletonBinder.showAfterDelay(header.accountSkeleton)
            }
            header.rowAccount.isEnabled = false
            return
        }
        header.rowAccount.isEnabled = true
        if (header.accountSkeleton.isVisible) {
            SkeletonBinder.swap(skeleton = header.accountSkeleton, content = header.accountText)
        } else if (accountSkeletonArmed) {
            SkeletonBinder.cancel(header.accountSkeleton)
            header.accountText.isVisible = true
        } else {
            header.accountText.isVisible = true
        }
        accountSkeletonArmed = false

        if (!AccountSession.isLoggedIn() || !BackendConfig.isConfigured()) {
            header.accountTile.isVisible = true
            header.imgAvatar.isVisible = false
            header.tvAvatarInitial.isVisible = false
            header.tvAccountName.text = getString(R.string.home_account_title)
            header.tvAccountSub.text = getString(R.string.home_account_subtitle)
        }
    }

    /**
     * Fills the signed-in header from the profile: the @handle, the photo or its monogram, and the
     * one neutral subtitle. The avatar tile is the signed-out state and goes with the identity.
     */
    private fun bindAccountRow(profile: UserProfileDto) {
        val header = binding.layoutHomeAccount
        val handle = profile.telegramUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }
        val identity = handle
            ?: profile.email.takeIf { it.isNotBlank() }
            ?: profile.telegramName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.home_account_title)
        header.accountTile.isVisible = false
        header.tvAccountName.text = identity
        header.tvAccountSub.text = getString(R.string.home_account_manage)
        AvatarManager.setMonogram(header.tvAvatarInitial, identity)
        AvatarManager.applyAvatar(
            viewLifecycleOwner.lifecycleScope,
            requireContext(),
            header.imgAvatar,
            header.tvAvatarInitial,
            profile,
        )
    }

    private fun paintCondition(condition: Condition?) {
        val strip = binding.layoutStatusStrip
        if (condition == null) {
            if (strip.statusStrip.isVisible) hideStrip()
            return
        }
        val wasVisible = strip.statusStrip.isVisible && !stripHiding
        // A replaced message CROSSFADES; the bar itself does not move. Only the text is animated, so
        // a strip that stays up while its reason changes never jumps.
        if (wasVisible && strip.statusStripText.text?.toString() != condition.text.toString()) {
            crossfadeText(strip.statusStripText, condition.text)
        } else {
            strip.statusStripText.text = condition.text
        }
        val tint = when (condition.severity) {
            Severity.INFO -> themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            Severity.WARN -> themeColor(R.attr.warning)
            Severity.ERROR -> ContextCompat.getColor(requireContext(), R.color.color_destructive_text)
        }
        strip.statusStripIcon.imageTintList = ColorStateList.valueOf(tint)

        val label = condition.actionLabel
        val action = condition.action
        if (label == null || action == null) {
            strip.statusStripAction.isVisible = false
        } else {
            strip.statusStripAction.isVisible = true
            strip.statusStripAction.text = label
            strip.statusStripAction.onSingleClick { action() }
        }
        if (!wasVisible) showStrip()
    }

    /** Half out, swap, half back: one `motion_state` in total, and the box never moves. */
    private fun crossfadeText(view: android.widget.TextView, text: CharSequence) {
        if (view.reducedMotion()) {
            view.text = text
            view.alpha = 1f
            return
        }
        val half = durState / 2
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(half).setInterpolator(easeStandard)
            .withEndAction {
                if (!isBindingInitialized) return@withEndAction
                view.text = text
                view.animate().alpha(1f).setDuration(half).setInterpolator(easeStandard).start()
            }.start()
    }

    private fun showStrip() {
        val strip = binding.layoutStatusStrip.statusStrip
        stripHiding = false
        strip.animate().cancel()
        strip.isVisible = true
        if (strip.reducedMotion()) {
            strip.alpha = 1f
            strip.translationY = 0f
            return
        }
        strip.alpha = 0f
        strip.translationY = resources.getDimensionPixelSize(R.dimen.space_8).toFloat()
        strip.animate().alpha(1f).translationY(0f)
            .setDuration(durReveal).setInterpolator(easeOutQuint).start()
    }

    private fun hideStrip() {
        val strip = binding.layoutStatusStrip.statusStrip
        if (strip.reducedMotion()) {
            strip.isVisible = false
            return
        }
        stripHiding = true
        strip.animate().alpha(0f)
            .translationY(resources.getDimensionPixelSize(R.dimen.space_8).toFloat())
            .setDuration(durRevealExit).setInterpolator(easeStandard)
            .withEndAction {
                if (!isBindingInitialized) return@withEndAction
                stripHiding = false
                strip.isVisible = false
                strip.alpha = 1f
                strip.translationY = 0f
            }.start()
    }

    /**
     * The rings, the disc, the shield and the status line. Colour carries the state on a geometry
     * that never changes: every ring is 3dp in every state and only its tint moves, because a ring
     * that changes width makes the control appear to change size.
     */
    private fun paintConnect(state: HomeState, animate: Boolean) {
        val frame = binding.connectFrame
        val enabled = when (state.conn) {
            Conn.GATED, Conn.NO_SERVER, Conn.DISCONNECTING -> false
            // The app does not know better than the OS whether a tunnel can be raised, so offline
            // keeps the disc live.
            else -> true
        }
        frame.isEnabled = enabled
        frame.isFocusable = enabled
        frame.alpha = if (enabled) 1f else DISABLED_ALPHA
        // The accessible name states STATE and ACTION. A disabled object names the state alone,
        // because it has no action to offer and promising one is a lie to a screen reader.
        frame.contentDescription = getString(
            when (state.conn) {
                Conn.CONNECTED -> R.string.home_cd_disconnect
                Conn.CONNECTING -> R.string.home_cd_cancel
                Conn.GATED -> R.string.home_cd_locked
                Conn.NO_SERVER -> R.string.home_status_no_server
                Conn.DISCONNECTING -> R.string.home_status_disconnecting
                else -> R.string.home_cd_connect
            }
        )

        val negotiating = state.conn == Conn.CONNECTING
        // The sweep spins for the two things this screen has always spun it for: the core
        // negotiating a tunnel, and a подписка being fetched.
        if (negotiating || backgroundLoads > 0) binding.connectSweep.show() else binding.connectSweep.hide()
        if (negotiating) startBreathing() else stopBreathing()

        val targetRing = when (state.conn) {
            Conn.CONNECTED -> themeColor(androidx.appcompat.R.attr.colorPrimary)
            Conn.CONNECTING -> themeColor(R.attr.connectActiveColor)
            Conn.ERROR -> themeColor(androidx.appcompat.R.attr.colorError)
            Conn.GATED, Conn.NO_SERVER -> themeColor(com.google.android.material.R.attr.colorOutline)
            else -> idleRingColor()
        }

        val nowConnected = state.conn == Conn.CONNECTED
        // `transition` is "the user just did this"; `live` is "and the OS wants motion". They are
        // separate because the confirm HAPTIC fires on every genuine transition, reduced motion or
        // not, and on none of the repaints — including the one at launch that finds a tunnel
        // already up, which must not buzz the phone in the user's pocket.
        val transition = animate && renderedConn != null
        val live = transition && !frame.reducedMotion()

        when {
            nowConnected && !visualConnected -> playConfirm(targetRing, live, transition)
            !nowConnected && visualConnected -> playRelease(targetRing, live)
            else -> {
                tintRing(targetRing, animate = live && renderedConn != state.conn)
                binding.shieldFilled.alpha = if (nowConnected) 1f else 0f
                binding.shieldOutline.alpha = if (nowConnected) 0f else 1f
            }
        }
        visualConnected = nowConnected

        paintStatusLine(state)
    }

    private fun paintStatusLine(state: HomeState) {
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val (textRes, colour) = when (state.conn) {
            Conn.DISCONNECTED -> R.string.home_status_disconnected to onSurface
            Conn.CONNECTING -> R.string.home_status_connecting to onSurface
            Conn.CONNECTED -> R.string.home_status_connected to
                themeColor(com.google.android.material.R.attr.colorTertiary)
            Conn.DISCONNECTING -> R.string.home_status_disconnecting to onSurface
            Conn.ERROR -> R.string.home_status_error to
                ContextCompat.getColor(requireContext(), R.color.color_destructive_text)
            Conn.NO_SERVER -> R.string.home_status_no_server to onSurface
            Conn.GATED -> gateStatusWord(state) to onSurface
        }
        // The word is swapped INSTANTLY. Text that crossfades is unreadable for the duration of the
        // crossfade, and this is the one string that has to be legible in four seconds.
        binding.tvStatus.setText(textRes)
        binding.tvStatus.setTextColor(colour)

        // WHAT IT RUNS THROUGH: the flag, then the server, then its live latency once a probe has
        // landed. The whole line is INVISIBLE rather than GONE, so nothing below it moves.
        val name = state.serverName
        // The IDENTITY and a HINT are two different lines that share one slot, and only the
        // identity gets the flag: a flag beside «Нажмите, чтобы повторить» would label the
        // instruction with a country.
        val identity: CharSequence? = when (state.conn) {
            Conn.ERROR, Conn.NO_SERVER, Conn.GATED -> null
            Conn.CONNECTED -> name?.let { server ->
                pingMs?.let { getString(R.string.home_server_latency, server, it) } ?: server
            }

            else -> name
        }
        val hint: CharSequence? = when (state.conn) {
            Conn.ERROR -> getString(R.string.home_detail_retry)
            Conn.NO_SERVER -> getString(R.string.home_detail_pick_server)
            else -> null
        }
        val detail = identity ?: hint
        binding.tvServerFlag.text = state.serverFlag.orEmpty()
        binding.tvServerFlag.isVisible = identity != null && state.serverFlag != null
        binding.tvStatusDetail.text = detail ?: ""
        binding.serverIdentity.visibility = if (detail == null) View.INVISIBLE else View.VISIBLE
    }

    private fun gateStatusWord(state: HomeState): Int = when {
        state.sub is Sub.Expired -> R.string.home_status_expired
        state.gate == Gate.BUY -> R.string.home_status_no_subscription
        else -> R.string.home_status_no_servers
    }

    /**
     * The strip's two speed columns. A figure LANDS — it does not tick, count or animate — and it
     * reads zero at rest rather than blank: this row is the screen's ledger and it is always there.
     * The session clock is written by [uptimeRunnable], which owns it second by second.
     */
    private fun paintFigures() {
        if (!isBindingInitialized) return
        val zero = getString(R.string.home_speed_zero)
        binding.tvUp.text = upBytesPerSec?.let { formatSpeed(it) } ?: zero
        binding.tvDown.text = downBytesPerSec?.let { formatSpeed(it) } ?: zero
    }

    /** The subscription card + list, and the gate block, share one slot and are never both up. */
    private fun paintSlot(state: HomeState) {
        val gate = state.gate
        val gateVisible = gate != null
        binding.subscriptionSlot.isVisible = !gateVisible
        binding.layoutGate.gate.isVisible = gateVisible
        if (gate != null) paintGate(gate) else paintSubscriptionSlot(state)
        // The slot's two occupants swap in place over motion_state. Nothing else on the screen
        // moves, because the slot's y is fixed by the rhythm above it.
        if (renderedGateVisible != null && renderedGateVisible != gateVisible) {
            val incoming = if (gateVisible) binding.layoutGate.gate else binding.subscriptionSlot
            incoming.animate().cancel()
            if (incoming.reducedMotion()) {
                incoming.alpha = 1f
            } else {
                incoming.alpha = 0f
                incoming.animate().alpha(1f)
                    .setDuration(durState).setInterpolator(easeStandard).start()
            }
        }
        renderedGateVisible = gateVisible
    }

    private fun paintSubscriptionSlot(state: HomeState) {
        binding.rvHomeServers.isVisible = state.serverCount > 0 && !homeListCollapsed
        // Offline, or a failed refresh: the card and the rows keep their last values and the screen
        // says so, rather than emptying.
        binding.tvStaleHint.isVisible = state.stale || subsError
    }

    private fun paintGate(gate: Gate) {
        val block = binding.layoutGate
        // The heading, restored from the onboarding card this block replaced (5e8cd54's
        // home_empty_title). It names the SHAPE the user is in; the caption under it keeps the
        // reason, which is why the two are separate strings rather than one longer sentence.
        block.tvGateTitle.setText(
            when (gate) {
                Gate.SIGN_IN -> R.string.home_gate_signin_title
                Gate.ADD_SUBSCRIPTION -> R.string.home_gate_subscription_title
                Gate.BUY -> R.string.home_gate_buy_title
                Gate.SYNC_SERVERS -> R.string.home_gate_sync_title
                Gate.SYNC_FAILED -> R.string.home_gate_sync_failed_title
            }
        )
        val captionRes = when (gate) {
            Gate.SIGN_IN -> R.string.home_gate_signin_caption
            Gate.ADD_SUBSCRIPTION -> R.string.home_gate_subscription_caption
            Gate.BUY -> R.string.home_gate_buy_caption
            Gate.SYNC_SERVERS -> R.string.home_gate_sync_caption
            Gate.SYNC_FAILED -> R.string.home_gate_sync_failed_caption
        }
        block.tvGateCaption.setText(captionRes)
        // The failure reason IS the caption, so it carries the failure's colour.
        block.tvGateCaption.setTextColor(
            if (gate == Gate.SYNC_FAILED) {
                ContextCompat.getColor(requireContext(), R.color.color_destructive_text)
            } else {
                themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        )

        // THE LOADING STATE of this block. «Загрузить серверы» is the action that STARTS the
        // subscription refresh this screen is then waiting on, so while one is in flight it is
        // disabled: an in-flight action shows it and cannot be fired twice (OWNER-FEEDBACK
        // 2026-07-27 G2). The strip above already says «Загружаем…» and the connect object's sweep
        // is spinning, so the button does not need to restate it — it needs to stop accepting taps
        // that would stack a second refresh on the first. Every other gate's action starts nothing
        // this screen waits on, so it stays live.
        block.btnGatePrimary.isEnabled = !(gate == Gate.SYNC_SERVERS && backgroundLoads > 0)

        when (gate) {
            Gate.SIGN_IN -> {
                block.btnGatePrimary.setText(R.string.home_gate_signin)
                block.btnGatePrimary.onSingleClick { openLoginScreen() }
                block.btnGateSecondary.isVisible = true
                block.btnGateSecondary.setText(R.string.home_gate_add_subscription)
                block.btnGateSecondary.onSingleClick { mainHost.showAddMenu(it) }
            }

            Gate.ADD_SUBSCRIPTION -> {
                block.btnGatePrimary.setText(R.string.home_gate_add_subscription)
                block.btnGatePrimary.onSingleClick { mainHost.showAddMenu(it) }
                block.btnGateSecondary.isVisible = false
            }

            Gate.BUY -> {
                block.btnGatePrimary.setText(R.string.home_gate_buy)
                block.btnGatePrimary.onSingleClick {
                    startActivity(Intent(requireContext(), BuyTariffActivity::class.java))
                }
                // «Привязать Telegram» — the only live entry point to linking Telegram on this
                // screen. It lived on the onboarding card this block replaced, and it is offered on
                // exactly the same condition: signed in, no подписка, Telegram not yet attached.
                val linked = (AccountSession.state.value as? AccountSession.AccountState.LoggedIn)
                    ?.profile?.telegramLinked == true
                block.btnGateSecondary.isVisible = !linked
                block.btnGateSecondary.setText(R.string.home_gate_link_telegram)
                block.btnGateSecondary.onSingleClick { openTelegramLink() }
            }

            Gate.SYNC_SERVERS -> {
                block.btnGatePrimary.setText(R.string.home_gate_sync)
                block.btnGatePrimary.onSingleClick { requestServerSync() }
                block.btnGateSecondary.isVisible = false
            }

            Gate.SYNC_FAILED -> {
                block.btnGatePrimary.setText(R.string.home_gate_retry)
                block.btnGatePrimary.onSingleClick { requestServerSync() }
                block.btnGateSecondary.isVisible = false
            }
        }
    }

    private fun requestServerSync() {
        syncRequested = true
        mainHost.refreshSubscriptions()
    }

    // ==================== Motion ====================

    /**
     * The one hero moment in the product: 600ms, once, and nothing else in the app is allowed this
     * budget. Four beats fire together at T=0 — the shield crossfade, the ring tint, the sweep's
     * exit and the single confirm ring.
     *
     * Reduced motion: the shield is filled instantly, the ring tint is set instantly, the confirm
     * ring is NOT emitted at all, and the haptic still fires.
     */
    private fun playConfirm(ringTarget: Int, live: Boolean, haptic: Boolean) {
        if (haptic) binding.connectFrame.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        stopBreathing()
        tintRing(ringTarget, animate = live)

        if (!live) {
            binding.shieldFilled.alpha = 1f
            binding.shieldOutline.alpha = 0f
            binding.connectRingPulse.clearAnimation()
            binding.connectRingPulse.visibility = View.INVISIBLE
            return
        }

        binding.shieldFilled.animate().cancel()
        binding.shieldFilled.animate().alpha(1f).setDuration(durState).setInterpolator(easeStandard).start()
        binding.shieldOutline.animate().cancel()
        binding.shieldOutline.animate().alpha(0f).setDuration(durState).setInterpolator(easeStandard).start()

        // The choreography itself lives in @anim/connect_confirm — one file, so the ring's scale,
        // its fade and its tempo cannot drift away from the tokens they are written in.
        val pulse = binding.connectRingPulse
        pulse.clearAnimation()
        pulse.visibility = View.VISIBLE
        val confirm = AnimationUtils.loadAnimation(requireContext(), R.anim.connect_confirm)
        confirm.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) = Unit
            override fun onAnimationRepeat(animation: Animation?) = Unit
            override fun onAnimationEnd(animation: Animation?) {
                if (!isBindingInitialized) return
                pulse.visibility = View.INVISIBLE
            }
        })
        pulse.startAnimation(confirm)
    }

    /** Exit is 75 percent of enter, and it emits nothing. */
    private fun playRelease(ringTarget: Int, live: Boolean) {
        stopBreathing()
        tintRing(ringTarget, animate = live)
        binding.connectRingPulse.clearAnimation()
        binding.connectRingPulse.visibility = View.INVISIBLE

        if (!live) {
            binding.shieldFilled.alpha = 0f
            binding.shieldOutline.alpha = 1f
            return
        }
        binding.shieldFilled.animate().cancel()
        binding.shieldFilled.animate().alpha(0f).setDuration(durStateExit).setInterpolator(easeStandard).start()
        binding.shieldOutline.animate().cancel()
        binding.shieldOutline.animate().alpha(1f).setDuration(durStateExit).setInterpolator(easeStandard).start()
    }

    /**
     * The negotiating breath. The old build breathed the halo glow behind the shield; that glow is
     * banned and did not come back, so the same 850ms reverse lives on the TWO OUTER RINGS, which
     * are part of the object rather than a wash behind it. They swell in opacity together while the
     * sweep travels the disc — motion the user reads as "it is working on it", on the object itself.
     *
     * Reduced motion: nothing breathes and the sweep is the only signal.
     */
    private fun startBreathing() {
        if (breathAnimator?.isRunning == true) return
        if (binding.connectFrame.reducedMotion()) return
        breathAnimator = ValueAnimator.ofInt(BREATH_ALPHA_MIN, OPAQUE).apply {
            duration = BREATH_PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                ringOuter?.alpha = value
                ringMid?.alpha = value
            }
            start()
        }
    }

    private fun stopBreathing() {
        breathAnimator?.cancel()
        breathAnimator = null
        ringOuter?.alpha = OPAQUE
        ringMid?.alpha = OPAQUE
    }

    /** The rings' only state channel. Width never changes; the colour crosses over motion_state. */
    private fun tintRing(target: Int, animate: Boolean) {
        if (ringInner == null) return
        ringAnimator?.cancel()
        ringAnimator = null
        if (!animate || ringColor == target) {
            ringColor = target
            applyRingColor(target)
            return
        }
        val from = ringColor
        ringAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration = durState
            interpolator = easeStandard
            addUpdateListener {
                val value = it.animatedValue as Int
                ringColor = value
                applyRingColor(value)
            }
            start()
        }
    }

    private fun idleRingColor(): Int = themeColor(R.attr.colorOnSurfaceDim)

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.connectFrame, attr)

    // ==================== The session clock ====================

    /**
     * Starts the per-second uptime. The start instant is persisted, so the clock survives a rotation
     * or a theme recreate instead of restarting from zero on a tunnel that never went down.
     */
    private fun startConnectionTimer() {
        val stored = MmkvManager.decodeSettingsLong(KEY_CONNECTION_START, 0L)
        connectionStartTime = if (stored > 0L) {
            stored
        } else {
            System.currentTimeMillis().also { MmkvManager.encodeSettings(KEY_CONNECTION_START, it) }
        }
        timerHandler.removeCallbacks(uptimeRunnable)
        timerHandler.post(uptimeRunnable)
    }

    private fun stopConnectionTimer() {
        timerHandler.removeCallbacks(uptimeRunnable)
        connectionStartTime = 0L
        MmkvManager.encodeSettings(KEY_CONNECTION_START, 0L)
        // The session is over, so its readings go with it — and the strip returns to zeroes rather
        // than freezing on the last speed the tunnel ever saw.
        downBytesPerSec = null
        upBytesPerSec = null
        pingMs = null
        pingProbeFailures = 0
        if (isBindingInitialized) {
            binding.tvUptime.text = getString(R.string.home_uptime_zero)
            paintFigures()
        }
    }

    // ==================== The connect state machine ====================

    /**
     * The object's action. Three cases, and the middle one is the one the shipped build got wrong: a
     * tap DURING negotiation cancels the attempt, because a control that ignores a tap for twenty
     * seconds is a control the user stops trusting.
     */
    private fun handleConnectAction() {
        // A manual connect/disconnect starts a fresh session: allow auto-fallback again, and end any
        // fallback restart still considered in flight (the user's tap supersedes it).
        mainViewModel.autoFallbackUsed = false
        mainViewModel.fallbackInProgress = false
        healthCheckPending = false
        healthCheckConfirming = false
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(healthRecheckRunnable)

        when {
            connectInProgress -> {
                // Cancel: stop whatever half-started and return to idle.
                connectInProgress = false
                cancelConnectWatchdog()
                tunnelError = false
                CoreServiceManager.stopVService(requireContext())
                applyRunningState(isLoading = false, isRunning = false)
            }

            mainViewModel.isRunning.value == true -> {
                connectInProgress = false
                cancelConnectWatchdog()
                disconnecting = true
                tunnelError = false
                // The exit plays on the TAP, not one repaint later when the daemon finally answers.
                render(animate = true)
                CoreServiceManager.stopVService(requireContext())
            }

            else -> {
                connectInProgress = true
                tunnelError = false
                applyRunningState(isLoading = true, isRunning = false)
                scheduleConnectWatchdog()
                startVpnWithPermission()
            }
        }
    }

    /**
     * Starts the VPN, requesting the system VPN permission first when needed.
     *
     * The watchdog is STOOD DOWN while that system prompt is up, and re-armed by the result
     * callback when the permission comes back granted. It bounds a start that stalls; a decision the
     * user has not made yet is not a stalled start, and counting the prompt against the 20s deadline
     * reported a failure to anyone who read it before answering.
     */
    private fun startVpnWithPermission() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(requireContext())
            if (intent == null) {
                startV2Ray()
            } else {
                cancelConnectWatchdog()
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            // The object is disabled in this state, so this is a backstop rather than the user's
            // first contact with the problem — the status line already says «Сервер не выбран».
            connectInProgress = false
            applyRunningState(isLoading = false, isRunning = false)
            return
        }
        CoreServiceManager.startVService(requireContext())
    }

    /**
     * Stops the running tunnel and starts it again on the currently selected server.
     *
     * The core runs in its own process (`:RunSoLibV2RayDaemon`), so stopping is asynchronous and the
     * only truthful signal in this process is `MainViewModel.isRunning`, driven by the daemon's
     * broadcasts. Waiting a fixed delay here used to lose that race: the new start would arrive
     * while the old core was still up, `startContextService()` would see `coreController.isRunning`
     * and return silently, and the tunnel would keep running the PREVIOUS server while the UI showed
     * the new one. So wait for a real stopped state, and report failure rather than pretending to
     * have switched.
     */
    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value != true) {
            startV2Ray()
            return
        }
        CoreServiceManager.stopVService(requireContext())
        lifecycleScope.launch {
            val deadline = SystemClock.elapsedRealtime() + RESTART_STOP_TIMEOUT_MS
            while (mainViewModel.isRunning.value == true && SystemClock.elapsedRealtime() < deadline) {
                delay(RESTART_STOP_POLL_MS)
            }
            if (mainViewModel.isRunning.value == true) {
                connectInProgress = false
                // Nothing restarted, so an auto-fallback restart is no longer in flight — leaving
                // the flag set would make the next real disconnect look internal.
                mainViewModel.fallbackInProgress = false
                tunnelError = true
                if (isBindingInitialized) applyRunningState(isLoading = false, isRunning = true)
                return@launch
            }
            // The stop landed and the new start is going out now, so an auto-fallback restart is no
            // longer in flight. Released here, after the stop the disconnect handler had to read as
            // internal — releasing it any earlier hands that stop to the user-disconnect branch of
            // cancelHealthCheck() and re-opens the switch/restart loop.
            mainViewModel.fallbackInProgress = false
            startV2Ray()
        }
    }

    /**
     * The one entry point the connect machine uses to publish a state. [animate] is true ONLY on a
     * genuine live transition, so the signature confirmation and its reverse play then — not on the
     * LiveData replay after a rotation/theme recreate, which jumps straight to the end state.
     */
    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean, animate: Boolean = false) {
        connectLoading = isLoading
        tunnelRunning = isRunning && !isLoading
        if (!isLoading) disconnecting = false
        if (isRunning || isLoading) tunnelError = false
        if (isBindingInitialized) render(animate)
    }

    /** Arms the connect watchdog so a stalled/crashed start can't hang the UI on «Подключение…». */
    private fun scheduleConnectWatchdog() {
        timerHandler.removeCallbacks(connectWatchdogRunnable)
        timerHandler.postDelayed(connectWatchdogRunnable, CONNECT_TIMEOUT_MS)
    }

    /** Cancels the connect watchdog once the attempt resolved (success/failure/stop). */
    private fun cancelConnectWatchdog() {
        timerHandler.removeCallbacks(connectWatchdogRunnable)
    }

    /**
     * Schedules the one-shot post-connect health check, if auto-fallback is enabled and it hasn't
     * already run this session.
     */
    private fun scheduleHealthCheckIfEnabled() {
        // Any half-finished probe from the previous tunnel is void. Nothing about the fallback's
        // one-shot state is touched here: this runs on EVERY isRunning==true emission, including the
        // stale one the core answers MSG_REGISTER_CLIENT with (the quick-settings tile registers
        // every time the panel opens), so "a tunnel is up" is not evidence that the fallback's
        // restart has landed — the restart itself clears that flag.
        healthCheckConfirming = false
        timerHandler.removeCallbacks(healthRecheckRunnable)
        if (mainViewModel.autoFallbackUsed) return
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)) return
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_DELAY_MS)
    }

    /**
     * Cancels a pending health check and its confirmation re-probe. On a *genuine* user disconnect
     * it also clears the once-per-session fallback flag; during the fallback's own internal restart
     * (`MainViewModel.fallbackInProgress`) the flag must survive, or the next START_SUCCESS re-arms
     * the check and the switch/restart loop returns.
     */
    private fun cancelHealthCheck() {
        healthCheckPending = false
        healthCheckConfirming = false
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(healthRecheckRunnable)
        if (!mainViewModel.fallbackInProgress) {
            mainViewModel.autoFallbackUsed = false
        }
    }

    private fun startLatencyProbe() {
        timerHandler.removeCallbacks(latencyRunnable)
        timerHandler.post(latencyRunnable)
    }

    private fun stopLatencyProbe() {
        timerHandler.removeCallbacks(latencyRunnable)
    }

    // ==================== Navigation ====================

    private fun openAccount() {
        if (BackendConfig.isConfigured() && AccountSession.isLoggedIn()) {
            mainHost.selectTab(MainTab.ACCOUNT)
        } else {
            openLoginScreen()
        }
    }

    /** The subscription is managed on Аккаунт; the card here acts on the local copy. */
    private fun openSubscription() {
        if (BackendConfig.isConfigured() && AccountSession.isLoggedIn()) {
            mainHost.selectTab(MainTab.ACCOUNT)
        } else {
            openLoginScreen()
        }
    }

    private fun openLoginScreen() {
        if (!BackendConfig.isConfigured()) return
        mainHost.launchAuthScreen(Intent(requireContext(), LoginActivity::class.java))
    }

    /**
     * Opens the Telegram screen in LINK mode: the already-signed-in account gets its Telegram
     * attached, so the bot tracks the подписка. The token request carries the current JWT, so the
     * backend links Telegram to this account instead of starting a separate login.
     */
    private fun openTelegramLink() {
        if (!BackendConfig.isConfigured()) return
        val intent = Intent(requireContext(), LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_TELEGRAM)
            .putExtra(LoginActivity.EXTRA_LINK, true)
        mainHost.launchAuthScreen(intent)
    }

    // ==================== Formatting ====================

    /**
     * One decimal below 100, none at or above it, comma decimal, and the unit is fixed at Мбит/с in
     * the label — a real 40 Кбит/с renders `0,0`, which is a rounding and not a lie.
     */
    private fun formatSpeed(bytesPerSec: Long): String {
        val mbps = bytesPerSec * 8.0 / 1_000_000.0
        return if (mbps >= SPEED_DECIMAL_BELOW) {
            mbps.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.1f", mbps).replace('.', ',')
        }
    }

    /**
     * «14 августа» inside the current year, «14 августа 2027» otherwise. Never a numeric date on
     * this screen, and never `SimpleDateFormat("d MMMM")`, which follows the DEVICE locale and
     * prints «до 14 August» on an English phone.
     */
    private fun formatDate(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val months = resources.getStringArray(R.array.home_months_genitive)
        val month = months.getOrElse(date.monthValue - 1) { "" }
        return if (date.year == LocalDate.now(ZoneId.systemDefault()).year) {
            getString(R.string.home_date_short, date.dayOfMonth, month)
        } else {
            getString(R.string.home_date_full, date.dayOfMonth, month, date.year)
        }
    }

    /** ISO-8601 with an offset, or a bare date; anything else is no date at all. */
    private fun parseIsoMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(iso).toEpochMilli() }
        val parts = iso.substringBefore('T').split('-')
        if (parts.size != 3) return null
        return runCatching {
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
