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
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.viewModels
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.AccountSession
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.FragmentHomeBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.component.Haptic
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SkeletonBinder
import com.v2ray.ang.ui.component.onSingleClick
import com.v2ray.ang.util.AvatarManager
import com.v2ray.ang.util.FlagUtil
import com.v2ray.ang.util.reducedMotion
import com.v2ray.ang.viewmodel.AccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Главная — the screen the app opens on, rebuilt to `docs/design2026/13-start-screen.md`.
 *
 * ONE GLANCE ANSWERS THREE QUESTIONS: am I protected (the disc and the word under it), through what
 * (the server name under the word), and what do I do next (exactly one thing, and only when there is
 * something to do). Everything on this screen serves one of those three; nothing else is here.
 *
 * **The fragment renders, it does not decide twice.** [resolveState] reads every input once and
 * returns one [HomeState]; [render] applies it and branches on nothing else. The two are the whole
 * screen. The spec puts [resolveState] in the ViewModel, and it belongs there — `MainViewModel` is
 * another wave's file, so it lives here for now, as ONE function rather than as branching sprinkled
 * through a dozen `update*()` calls, which is what this file used to be.
 *
 * **It still owns the CONNECT STATE MACHINE, unchanged in behaviour**: the tap handler, the VPN
 * permission prompt, the connect watchdog, the restart-that-waits-for-a-real-stop, the one-shot
 * auto-fallback with its confirmation re-probe. That state machine is why this fragment is attached
 * at LAUNCH rather than the first time its tab is opened (`MainActivity.syncTabFragments`): a hidden
 * fragment is still RESUMED, so the tunnel is still observed, the health check still fires and
 * «Перезапустить» after a core-config change on Настройки still reaches a running core.
 *
 * **What left this screen and why.** The embedded server list, the provider meta-bar carousel, the
 * memory card, the onboarding card, the page gradient, the connect glow and its 850ms breathing
 * loop, the cold-start assemble, the uptime timer and the custom `Toast` are all gone — banned,
 * duplicated elsewhere, or (servers, providers) a destination rather than a widget. Servers are
 * reached through the «Серверы» row; the subscription through «Подписка».
 *
 * The tab's view outlives nothing and outlasts every bind — the shell hides and shows tabs rather
 * than replacing them — so every async callback checks [isBindingInitialized] before touching a view
 * and every posted callback is removed in [onDestroyView].
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    // ==================== The model ====================

    /** The connection as the screen draws it. Exactly one is true at a time (13 s. 11.2). */
    private enum class Conn { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR, NO_SERVER, GATED }

    /**
     * The gate block's four shapes (13 s. 8.3). A gate exists ONLY when the ledger rows would have
     * nothing true to say, which on this product means "there is not one server to connect to".
     */
    private enum class Gate { SIGN_IN, ADD_PROVIDER, BUY, SYNC_SERVERS, SYNC_FAILED }

    /** What the «Подписка» row knows. [UNKNOWN] is "still resolving", and draws the skeleton. */
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
        val serverCount: Int,
        val providerCount: Int,
        val sub: Sub,
        val condition: Condition?,
        val stale: Boolean,
    )

    // ==================== State this screen keeps ====================

    /**
     * The account's subscriptions, the same `SubInfoDto` list Аккаунт renders — one truth, two
     * surfaces (32-master-plan-android.md 1.2, signature moment 3). Fragment-scoped: Главная is
     * attached for the whole process, so this is created once and refreshed on resume.
     */
    private val accountViewModel: AccountViewModel by viewModels()

    private var accountSubs: List<SubInfoDto> = emptyList()
    private var subsResolved = false

    /**
     * The last account fetch failed. The «Подписка» row then KEEPS its last value and says it could
     * not be refreshed, rather than emptying: an error must not delete data the user had
     * (00-rules.md 9.6, 13 s. 11.3 "error, data").
     */
    private var subsError = false

    /** The user asked for a server sync. If one completes and there are still none, the gate says so. */
    private var syncRequested = false

    // Tracks the last observed signed-in state so the post-login auto-import fires only on a real
    // logged-out -> logged-in transition, not on every state replay. Seeded from the persisted
    // session so a returning (already signed-in) user is not treated as a fresh login.
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
     * Whether the connect object is currently drawn in its CONNECTED dress (filled shield, accent
     * ring, figures on screen). Kept apart from [renderedConn] because «Отключение…» is a distinct
     * connection state but the same visual exit: the release plays on the tap, not one repaint later
     * when the daemon finally answers.
     */
    private var visualConnected = false

    // SkeletonBinder.showAfterDelay() re-posts its 300ms timer on every call, so a screen that
    // re-renders while it waits would postpone the skeleton forever. These arm it exactly once.
    private var accountSkeletonArmed = false
    private var subscriptionSkeletonArmed = false

    /** True while the strip's exit is still running, so an arriving condition re-shows it. */
    private var stripHiding = false

    /** Which of the ledger slot's two occupants is drawn, so the swap can be crossfaded once. */
    private var renderedGateVisible: Boolean? = null

    // The three figures. Null means "no reading yet", which draws an EMPTY box of reserved width —
    // never a dash, never a zero (13 s. 7).
    private var downBytesPerSec: Long? = null
    private var upBytesPerSec: Long? = null
    private var pingMs: Int? = null
    private var pingProbeFailures = 0

    // Offline is a live condition, so it is observed rather than polled.
    private var offline = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * How many background loads the shell has open. The connect sweep is no longer allowed to stand
     * in for them (13 s. 5.3: an indeterminate indicator that spins while nothing is negotiating is
     * a lie about the system), so they report on the status strip instead.
     */
    private var backgroundLoads = 0

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Tracks the last delivered running state so a transition can be told from the LiveData value
    // replay after a rotation/theme recreate.
    private var lastRunningState: Boolean? = null

    // True between a connect tap and the definitive running/failed result, so a start that ends in
    // "not running" is reported as a failure rather than a silent revert.
    private var connectInProgress = false

    // The ring's stroke lives in this drawable; the tint animation retints it in place, which is why
    // there is no runtime-tinted ring drawable file and therefore no raw hex anywhere on this screen.
    private var ringDrawable: GradientDrawable? = null
    private var ringColor = 0
    private var ringAnimator: ValueAnimator? = null

    // Cached easing curves (loaded once) so the imperative hero motion rides the same ease-out tempo
    // as res/interpolator and res/anim. No bounce.
    private val easeOutQuint by lazy { AnimationUtils.loadInterpolator(requireContext(), R.interpolator.ease_out_quint) }
    private val easeStandard by lazy { AnimationUtils.loadInterpolator(requireContext(), R.interpolator.ease_standard) }

    private val durState get() = resources.getInteger(R.integer.motion_state).toLong()
    private val durStateExit get() = resources.getInteger(R.integer.motion_state_exit).toLong()
    private val durReveal get() = resources.getInteger(R.integer.motion_reveal).toLong()
    private val durRevealExit get() = resources.getInteger(R.integer.motion_reveal_exit).toLong()
    private val durEmphasis get() = resources.getInteger(R.integer.motion_emphasis).toLong()

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

    /** The 30s latency probe of the ACTIVE server, and the only producer of the third column. */
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

        // 13 s. 19.4 q2: the dial to turn if the probe ever costs battery. The design does not change.
        const val LATENCY_INTERVAL_MS = 30_000L

        // Three consecutive failed probes, not one: a single dropped packet is not a dead server
        // (13 s. 7).
        const val SILENT_SERVER_FAILURES = 3

        // Under this many days left the subscription is «Истекает» (13 s. 8.1).
        const val EXPIRING_DAYS = 3

        // Below 100 the speed carries one decimal, at or above it none (13 s. 7).
        const val SPEED_DECIMAL_BELOW = 100.0

        // ~2088-01-01 in epoch seconds. Some panels send a date this far out to mean "never".
        const val UNLIMITED_EXPIRE_SECONDS = 3_723_840_000L

        const val DISABLED_ALPHA = 0.38f
        const val CONFIRM_RING_SCALE = 1.35f
        const val CONFIRM_RING_ALPHA = 0.6f
        const val RIPPLE_ALPHA = 26 // 10% of 255, 13 s. 5.1
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            startV2Ray()
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
        observeTunnel()
        observeAccount()
        observeNetwork()
        render()
    }

    /**
     * Re-reads what other entry points may have changed while this tab was up but not in front.
     * Hidden tabs stay RESUMED, so this also runs for a tab that is attached but not on screen —
     * which is exactly what makes it correct the moment it is shown again.
     */
    override fun onResume() {
        super.onResume()
        refreshAccountData()
        render()
    }

    override fun onDestroyView() {
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(healthRecheckRunnable)
        timerHandler.removeCallbacks(latencyRunnable)
        timerHandler.removeCallbacks(connectWatchdogRunnable)
        ringAnimator?.cancel()
        ringAnimator = null
        ringDrawable = null
        unregisterNetworkCallback()
        super.onDestroyView()
    }

    // ==================== What the shell calls ====================

    /**
     * The server cache changed. Главная does not list servers any more — the «Серверы» row carries
     * the count — so this repaints the ledger rather than an adapter.
     *
     * @param index unused here; kept because the shell fans one signal out to two tabs and the
     *   Серверы tab does use it.
     */
    fun bindList(index: Int) {
        if (!isBindingInitialized) return
        render()
        // Whether there is a server at all is one of the two inputs to the shell's nav gates.
        mainHost.refreshNavGates()
    }

    /**
     * Главная has no scrolling list of its own any more, so there is nothing to pad. Kept because
     * the shell pushes the inset into every attached tab and one inset strategy is the point.
     */
    fun applyListInsets() = Unit

    /**
     * The control the shell anchors its add menu to. The gate's «Добавить провайдера» when it is on
     * screen, otherwise none — the shell falls back to the bottom bar, so the action is never a dead
     * end.
     */
    fun addMenuAnchor(): View? {
        if (!isBindingInitialized) return null
        val secondary = binding.layoutGate.btnGateSecondary
        return if (binding.layoutGate.gate.isVisible && secondary.isVisible) secondary else null
    }

    /** Repaints the status line after the shell changed which server is selected. */
    fun refreshSelectedServer() {
        if (!isBindingInitialized) return
        render()
    }

    /**
     * The shell has just written a new selected server. The shell owns the write because the Серверы
     * list has to be mirrored too; all this screen has to do is re-read it.
     */
    fun onSelectedServerChanged(previous: String?, guid: String) {
        if (!isBindingInitialized) return
        // A new server invalidates the previous server's latency reading.
        pingMs = null
        pingProbeFailures = 0
        render()
    }

    /** A server was removed elsewhere; the «Серверы» row's count follows it. */
    fun removeServerRow(guid: String, position: Int) {
        if (!isBindingInitialized) return
        render()
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
     * A transient EVENT, so it is a `Snackbar` (00-rules.md 1.4.8 retires the custom `Toast` pill
     * this used to be, and 13 s. 9 keeps the strip for persistent CONDITIONS only). Offset above the
     * overlaid bottom navigation with the shell's own inset figure.
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
     * export). It reports on the status strip at the LOWEST priority, so a real condition always
     * wins the single strip.
     *
     * It deliberately does NOT touch the connect sweep any more: the sweep means "the core is
     * negotiating a tunnel" and nothing else (13 s. 5.3).
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
     * Builds the disc, its ring and its ripple/focus foreground from THEME ATTRIBUTES.
     *
     * These are drawables and not drawable files on purpose. The ring's colour is the screen's whole
     * state channel and changes at runtime between five values; the shipped `bg_connect_ring.xml`
     * solved that with six raw hex literals across three theme variants, two of which measured
     * 2.33:1 and 1.29:1 against the ground and failed the 3:1 boundary floor (00-rules.md 6.8,
     * WCAG 1.4.11). Built here, every colour is resolved through `?attr` and is therefore correct in
     * blue, light and the mono overlay at once, with no file to keep in three copies.
     *
     * The frame IS the control: 176dp of touch target, and press scales the disc, the ring and the
     * shield together because they are one object.
     */
    private fun buildConnectObject() {
        val frame = binding.connectFrame
        val stroke = resources.getDimensionPixelSize(R.dimen.stroke_ring)

        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
        }
        ringColor = idleRingColor()
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(stroke, ringColor)
        }
        ringDrawable = ring
        frame.background = LayerDrawable(arrayOf(fill, ring))

        // R7: every focusable control draws a 2dp accent ring. The disc had none at all — the
        // product's primary control was unreachable by keyboard, D-pad and switch access in practice.
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

        // The monogram's circle: the P3 plane, never the accent (13 s. 10).
        binding.layoutHomeAccount.tvAvatarInitial.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
        }
    }

    private fun wireConnect() {
        binding.connectFrame.onSingleClick(Haptic.PRESS) { handleConnectAction() }
    }

    private fun wireHeaderRow() {
        binding.layoutHomeAccount.rowAccount.onSingleClick { openAccount() }
    }

    /**
     * The inline placement of the shared status strip (S-6). The component file is the shell's
     * DOCKED bar — full-bleed, hairline on top — so two properties are adjusted here and nowhere
     * else: the hairline goes, and the bar is clipped to @dimen/radius_control so it reads as a
     * block inside the content rather than as an edge of the window.
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
     * The ONE scroll-linked change on this screen (13 s. 10): a hairline under the header row fades
     * in once the content has moved. No colour step, no elevation, no shadow, no collapsing title.
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
            // is reported on the screen (13 s. 11.2) rather than as a toast that has already gone.
            tunnelError = !isRunning && connectInProgress
            disconnecting = false

            applyRunningState(isLoading = false, isRunning = isRunning, animate = liveTransition)

            if (isRunning) {
                scheduleHealthCheckIfEnabled()
                startLatencyProbe()
            } else {
                cancelHealthCheck()
                stopLatencyProbe()
            }

            connectInProgress = false
            lastRunningState = isRunning
        }

        mainViewModel.delayResultAction.observe(viewLifecycleOwner) { time ->
            onDelayResult(time)
        }
    }

    /**
     * One delay result serves two consumers: the numeric strip's third column, and the auto-fallback
     * health check. The latency update runs FIRST and unconditionally — it is the reading, and the
     * health check's `pending` flag says nothing about whether the figure is true.
     */
    private fun onDelayResult(time: Long) {
        if (mainViewModel.isRunning.value == true) {
            val wasSilent = pingProbeFailures >= SILENT_SERVER_FAILURES
            if (time >= 0) {
                pingMs = time.toInt()
                pingProbeFailures = 0
            } else {
                pingMs = null
                pingProbeFailures++
            }
            // A reading only repaints the figure. A full render is for the frame the "server is not
            // answering" condition actually appears or clears on.
            if (isBindingInitialized) {
                if (wasSilent != (pingProbeFailures >= SILENT_SERVER_FAILURES)) render() else paintFigures()
            }
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
     * The account and its subscriptions. Both feed the header row and the «Подписка» row, and
     * NEITHER blocks the other: a subscription that resolves while the profile is still in flight
     * paints its row and leaves the header a skeleton (13 s. 11.3, "partial").
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
                    if (isBindingInitialized) render()
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

    /** 00-rules.md 9.4: every error ships a recovery affordance. */
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
     * Runs once when the user transitions to signed-in: auto-import their subscriptions and reload
     * the server list on success.
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
     * here down the code paints what it is told (13 s. 15.4).
     */
    private fun resolveState(): HomeState {
        val serverCount = mainViewModel.serversCache.size
        val providerCount = mainViewModel.serversCache
            .map { it.profile.subscriptionId }
            .filter { it.isNotEmpty() }
            .distinct()
            .size
        val selected = MmkvManager.getSelectServer()
        val serverName = selected
            ?.let { MmkvManager.decodeServerConfig(it)?.remarks }
            ?.takeIf { it.isNotBlank() }
            // The leading country flag renders as a tile on the «Серверы» row, never as text here.
            ?.let { FlagUtil.stripLeadingFlag(it).trim() }

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
            serverCount = serverCount,
            providerCount = providerCount,
            sub = sub,
            condition = resolveCondition(sub, conn),
            stale = offline,
        )
    }

    /**
     * 13 s. 8.3: the gate replaces the rows only when the rows would have nothing true to say. On
     * this product that reduces to one test — there is not a single server to connect to — and then
     * WHICH gate is a question about what the user is missing.
     *
     * With servers present the rows always show, and any actionable condition (expired, expiring,
     * device limit) is carried by the status strip instead.
     */
    private fun resolveGate(serverCount: Int): Gate? {
        if (serverCount > 0) return null
        if (!BackendConfig.isConfigured()) return Gate.ADD_PROVIDER
        if (!AccountSession.isLoggedIn()) return Gate.SIGN_IN
        // Still resolving: show the ledger with its skeleton rather than guess a gate and then
        // swap it for a different one a second later.
        if (!subsResolved) return null
        if (accountSubs.isEmpty()) return Gate.BUY
        // A sync that the user asked for, that has finished, and that produced no server, is a
        // FAILURE and says so — offering «Загрузить серверы» a second time as if nothing happened
        // is the button that does nothing.
        return if (syncRequested && backgroundLoads == 0) Gate.SYNC_FAILED else Gate.SYNC_SERVERS
    }

    /**
     * The subscription, from the account when there is one and from the local provider metadata
     * otherwise, so a pasted subscription still tells the truth on this row.
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
        // No account: the provider's own `userinfo` expiry is the only truth available. Panels
        // sometimes send a huge timestamp instead of 0 for "never", which reads as an active
        // subscription with no date - which is exactly what Sub.Active(null) draws.
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
     * ONE condition, resolved by the priority order of 13 s. 9. The renderer never picks between
     * conditions; if two are true at once, the higher one is the only one shown.
     *
     * Two rows of that table have no producer on Android yet and are therefore absent rather than
     * faked: the device limit (row 2) and TUN-requested-but-unavailable (row 6).
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
            return Condition(
                text = getString(R.string.home_condition_silent),
                severity = Severity.WARN,
                actionLabel = getString(R.string.home_action_change_server),
                action = { mainHost.selectTab(MainTab.SERVERS) },
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
        // A replaced message CROSSFADES; the bar itself does not move (13 s. 12.5). Only the text
        // is animated, so a strip that stays up while its reason changes never jumps.
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
        // res/drawable ships no ic_warning and no ic_error yet (filed with the icon owner), so the
        // warning and error severities carry the info glyph in the correct tone. That keeps two
        // channels — the glyph's colour and the words — rather than one.
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
     * The disc, the ring, the shield and the status line. Colour carries the state on a geometry
     * that never changes: the ring is 3dp in every state and only its tint moves (S-2), because a
     * ring that changes width makes the control appear to change size.
     */
    private fun paintConnect(state: HomeState, animate: Boolean) {
        val frame = binding.connectFrame
        val enabled = when (state.conn) {
            Conn.GATED, Conn.NO_SERVER, Conn.DISCONNECTING -> false
            // 13 s. 11.1 variant E: the app does not know better than the OS whether a tunnel can be
            // raised, so offline keeps the disc live.
            else -> true
        }
        frame.isEnabled = enabled
        frame.isFocusable = enabled
        frame.alpha = if (enabled) 1f else DISABLED_ALPHA
        // 13 s. 14: the name states STATE and ACTION. A disabled disc names the state alone,
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

        val sweeping = state.conn == Conn.CONNECTING
        if (sweeping) binding.connectSweep.show() else binding.connectSweep.hide()

        val targetRing = when (state.conn) {
            Conn.CONNECTED -> themeColor(androidx.appcompat.R.attr.colorPrimary)
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

        val detail: CharSequence? = when (state.conn) {
            Conn.ERROR -> getString(R.string.home_detail_retry)
            Conn.NO_SERVER -> getString(R.string.home_detail_pick_server)
            Conn.GATED -> null
            else -> state.serverName
        }
        binding.tvStatusDetail.text = detail ?: ""
        // INVISIBLE, never GONE: the line stays reserved so the strip below never moves.
        binding.tvStatusDetail.visibility = if (detail == null) View.INVISIBLE else View.VISIBLE
    }

    private fun gateStatusWord(state: HomeState): Int = when {
        state.sub is Sub.Expired -> R.string.home_status_expired
        state.gate == Gate.BUY -> R.string.home_status_no_subscription
        else -> R.string.home_status_no_servers
    }

    /**
     * The three figures. Visible only while CONNECTED: during negotiation the throughput is
     * genuinely zero and there is no latency measurement, and printing `0,0` there would be a
     * placeholder pretending to be a reading.
     */
    private fun paintFigures() {
        if (!isBindingInitialized) return
        // A figure LANDS. It does not tick, count or animate (13 s. 12.5).
        binding.tvDown.text = downBytesPerSec?.let { formatSpeed(it) }.orEmpty()
        binding.tvUp.text = upBytesPerSec?.let { formatSpeed(it) }.orEmpty()

        // The one exception: the latency's FIRST arrival fades in, because until the probe lands
        // its box is deliberately empty and a figure appearing out of nothing reads as a glitch.
        val ping = pingMs?.toString().orEmpty()
        val view = binding.tvPing
        val firstArrival = ping.isNotEmpty() && view.text.isNullOrEmpty()
        if (firstArrival && !view.reducedMotion()) {
            view.animate().cancel()
            view.alpha = 0f
            view.text = ping
            view.animate().alpha(1f).setDuration(durState).setInterpolator(easeStandard).start()
        } else {
            view.alpha = 1f
            view.text = ping
        }
    }

    /** The ledger rows and the gate block share one slot and are never both on screen. */
    private fun paintSlot(state: HomeState) {
        val gate = state.gate
        val gateVisible = gate != null
        binding.ledger.isVisible = !gateVisible
        binding.layoutGate.gate.isVisible = gateVisible
        if (gate != null) paintGate(gate) else paintLedger(state)
        // The slot's two occupants swap in place over motion_state (13 s. 12.5). Nothing else on
        // the screen moves, because the slot's y is fixed by the rhythm above it.
        if (renderedGateVisible != null && renderedGateVisible != gateVisible) {
            val incoming = if (gateVisible) binding.layoutGate.gate else binding.ledger
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

    private fun paintGate(gate: Gate) {
        val block = binding.layoutGate
        val captionRes = when (gate) {
            Gate.SIGN_IN -> R.string.home_gate_signin_caption
            Gate.ADD_PROVIDER -> R.string.home_gate_provider_caption
            Gate.BUY -> R.string.home_gate_buy_caption
            Gate.SYNC_SERVERS -> R.string.home_gate_sync_caption
            Gate.SYNC_FAILED -> R.string.home_gate_sync_failed_caption
        }
        block.tvGateCaption.setText(captionRes)
        // The failure reason IS the caption, so it carries the failure's colour (13 s. 8.3).
        block.tvGateCaption.setTextColor(
            if (gate == Gate.SYNC_FAILED) {
                ContextCompat.getColor(requireContext(), R.color.color_destructive_text)
            } else {
                themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        )

        when (gate) {
            Gate.SIGN_IN -> {
                block.btnGatePrimary.setText(R.string.home_gate_signin)
                block.btnGatePrimary.onSingleClick { openLoginScreen() }
                block.btnGateSecondary.isVisible = true
                block.btnGateSecondary.setText(R.string.home_gate_add_provider)
                block.btnGateSecondary.onSingleClick { mainHost.showAddMenu(it, withListActions = false) }
            }

            Gate.ADD_PROVIDER -> {
                block.btnGatePrimary.setText(R.string.home_gate_add_provider)
                block.btnGatePrimary.onSingleClick { mainHost.showAddMenu(it, withListActions = false) }
                block.btnGateSecondary.isVisible = false
            }

            Gate.BUY -> {
                block.btnGatePrimary.setText(R.string.home_gate_buy)
                block.btnGatePrimary.onSingleClick {
                    startActivity(Intent(requireContext(), BuyTariffActivity::class.java))
                }
                block.btnGateSecondary.isVisible = false
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

    private fun paintLedger(state: HomeState) {
        val servers = resources.getQuantityString(
            R.plurals.home_servers_count,
            state.serverCount,
            state.serverCount,
        )
        val providers = resources.getQuantityString(
            R.plurals.home_providers_count,
            state.providerCount,
            state.providerCount,
        )
        RowBinder.bind(
            root = binding.rowServers.row,
            title = getString(R.string.home_row_servers),
            subtitle = getString(R.string.home_row_servers_value, servers, providers),
            glyph = R.drawable.ic_nav_servers,
            trailing = RowBinder.Trailing.Chevron,
            onClick = { mainHost.selectTab(MainTab.SERVERS) },
        )

        if (state.sub is Sub.Unknown) {
            if (!subscriptionSkeletonArmed) {
                subscriptionSkeletonArmed = true
                SkeletonBinder.showAfterDelay(binding.subscriptionSkeleton)
            }
        } else {
            if (binding.subscriptionSkeleton.isVisible) {
                SkeletonBinder.swap(
                    skeleton = binding.subscriptionSkeleton,
                    content = binding.rowSubscription.row,
                )
            } else if (subscriptionSkeletonArmed) {
                SkeletonBinder.cancel(binding.subscriptionSkeleton)
            }
            subscriptionSkeletonArmed = false
        }

        // The last fetch failed: the row keeps its state and says it could not be refreshed, rather
        // than emptying (13 s. 11.3, "error, data").
        val failedToRefresh = subsError && state.sub !is Sub.Unknown
        val subtitle = if (failedToRefresh) {
            getString(R.string.home_sub_stale)
        } else when (val sub = state.sub) {
            is Sub.Unknown -> ""
            is Sub.None -> getString(R.string.home_sub_none)
            is Sub.Active -> sub.untilMs?.let { getString(R.string.home_sub_active, formatDate(it)) }.orEmpty()
            is Sub.Trial -> getString(R.string.home_sub_trial, formatDate(sub.untilMs))
            is Sub.Expiring -> resources.getQuantityString(
                R.plurals.home_sub_days_left,
                sub.daysLeft,
                sub.daysLeft,
            )
            is Sub.Expired -> getString(R.string.home_sub_expired, formatDate(sub.sinceMs))
        }
        RowBinder.bind(
            root = binding.rowSubscription.row,
            title = getString(R.string.home_row_subscription),
            subtitle = subtitle.takeIf { it.isNotEmpty() },
            glyph = R.drawable.ic_subscriptions_24dp,
            // S-4 puts the state word in the TEXT COLUMN so the chevron stays the row's single
            // trailing element. view_row.xml has no chip slot, so the row's `value` carries the word
            // and its tone is applied below - the one sanctioned value+chevron pairing. A chip slot
            // on the universal row is filed with the ui/component owner.
            value = when (state.sub) {
                is Sub.Expiring -> getString(R.string.home_chip_expiring)
                is Sub.Expired -> getString(R.string.home_chip_expired)
                else -> null
            },
            trailing = RowBinder.Trailing.Chevron,
            onClick = { openSubscription() },
        )
        val danger = ContextCompat.getColor(requireContext(), R.color.color_destructive_text)
        binding.rowSubscription.rowValue.setTextColor(
            if (state.sub is Sub.Expired) danger else themeColor(R.attr.warning)
        )
        binding.rowSubscription.rowSubtitle.setTextColor(
            if (state.sub is Sub.Expired || failedToRefresh) {
                danger
            } else {
                themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        )
        // Offline: the rows keep their last value and the screen says so, rather than emptying
        // (00-rules.md 9.6).
        binding.tvStaleHint.isVisible = state.stale
    }

    // ==================== Motion ====================

    /**
     * The one hero moment in the product (13 s. 12.3): 600ms, once, and nothing else in the app is
     * allowed this budget. Four beats fire together at T=0 — the shield crossfade, the ring tint,
     * the sweep's exit and the single confirm ring — and the numeric strip enters on the tail,
     * because it cannot show a reading before there is a tunnel to read.
     *
     * Reduced motion: the shield is filled instantly, the ring tint is set instantly, the ring is
     * NOT emitted at all, the strip appears instantly, and the haptic still fires.
     */
    private fun playConfirm(ringTarget: Int, live: Boolean, haptic: Boolean) {
        if (haptic) binding.connectFrame.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        tintRing(ringTarget, animate = live)

        if (!live) {
            binding.shieldFilled.alpha = 1f
            binding.shieldOutline.alpha = 0f
            binding.connectRingPulse.visibility = View.INVISIBLE
            showFigures(animated = false)
            return
        }

        binding.shieldFilled.animate().cancel()
        binding.shieldFilled.animate().alpha(1f).setDuration(durState).setInterpolator(easeStandard).start()
        binding.shieldOutline.animate().cancel()
        binding.shieldOutline.animate().alpha(0f).setDuration(durState).setInterpolator(easeStandard).start()

        val pulse = binding.connectRingPulse
        pulse.animate().cancel()
        pulse.scaleX = 1f
        pulse.scaleY = 1f
        pulse.alpha = CONFIRM_RING_ALPHA
        pulse.visibility = View.VISIBLE
        pulse.animate()
            .scaleX(CONFIRM_RING_SCALE).scaleY(CONFIRM_RING_SCALE).alpha(0f)
            .setDuration(durEmphasis).setInterpolator(easeOutQuint)
            .withEndAction {
                if (!isBindingInitialized) return@withEndAction
                pulse.visibility = View.INVISIBLE
                pulse.scaleX = 1f
                pulse.scaleY = 1f
                pulse.alpha = CONFIRM_RING_ALPHA
            }.start()

        showFigures(animated = true)
    }

    /** Exit is 75 percent of enter, and it emits nothing (13 s. 12.4). */
    private fun playRelease(ringTarget: Int, live: Boolean) {
        tintRing(ringTarget, animate = live)
        binding.connectRingPulse.animate().cancel()
        binding.connectRingPulse.visibility = View.INVISIBLE

        if (!live) {
            binding.shieldFilled.alpha = 0f
            binding.shieldOutline.alpha = 1f
            hideFigures(animated = false)
            return
        }
        binding.shieldFilled.animate().cancel()
        binding.shieldFilled.animate().alpha(0f).setDuration(durStateExit).setInterpolator(easeStandard).start()
        binding.shieldOutline.animate().cancel()
        binding.shieldOutline.animate().alpha(1f).setDuration(durStateExit).setInterpolator(easeStandard).start()
        hideFigures(animated = true)
    }

    private fun showFigures(animated: Boolean) {
        val strip = binding.numericStrip
        strip.animate().cancel()
        if (!animated) {
            strip.visibility = View.VISIBLE
            strip.alpha = 1f
            strip.translationY = 0f
            return
        }
        strip.alpha = 0f
        strip.translationY = resources.getDimensionPixelSize(R.dimen.space_8).toFloat()
        strip.visibility = View.VISIBLE
        strip.animate().alpha(1f).translationY(0f)
            .setStartDelay(durState)
            .setDuration(durReveal).setInterpolator(easeOutQuint).start()
    }

    private fun hideFigures(animated: Boolean) {
        val strip = binding.numericStrip
        strip.animate().cancel()
        if (!animated) {
            strip.visibility = View.INVISIBLE
            strip.alpha = 1f
            strip.translationY = 0f
            clearFigures()
            return
        }
        strip.animate().alpha(0f)
            // showFigures() leaves a start delay on the shared animator; the exit has none.
            .setStartDelay(0)
            .translationY(resources.getDimensionPixelSize(R.dimen.space_8).toFloat())
            .setDuration(durRevealExit).setInterpolator(easeStandard)
            .withEndAction {
                if (!isBindingInitialized) return@withEndAction
                strip.visibility = View.INVISIBLE
                strip.alpha = 1f
                strip.translationY = 0f
                clearFigures()
            }.start()
    }

    /** Drops the last session's readings, once nothing is showing them any more. */
    private fun clearFigures() {
        downBytesPerSec = null
        upBytesPerSec = null
        pingMs = null
        pingProbeFailures = 0
        if (isBindingInitialized) paintFigures()
    }

    /** The ring's only state channel. Width never changes; the colour crosses over motion_state. */
    private fun tintRing(target: Int, animate: Boolean) {
        val ring = ringDrawable ?: return
        ringAnimator?.cancel()
        ringAnimator = null
        val stroke = resources.getDimensionPixelSize(R.dimen.stroke_ring)
        if (!animate || ringColor == target) {
            ringColor = target
            ring.setStroke(stroke, target)
            return
        }
        val from = ringColor
        ringAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration = durState
            interpolator = easeStandard
            addUpdateListener {
                val value = it.animatedValue as Int
                ringColor = value
                ring.setStroke(stroke, value)
            }
            start()
        }
    }

    private fun idleRingColor(): Int = themeColor(R.attr.colorOnSurfaceDim)

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.connectFrame, attr)

    // ==================== The connect state machine ====================

    /**
     * The disc's action. Three cases, and the middle one is the one the shipped build got wrong: a
     * tap DURING negotiation cancels the attempt (13 s. 11.2), because a control that ignores a tap
     * for twenty seconds is a control the user stops trusting.
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
                // The previous session's readings are not this session's; the strip is hidden
                // while negotiating, so this lands before anything can show them again.
                clearFigures()
                applyRunningState(isLoading = true, isRunning = false)
                scheduleConnectWatchdog()
                startVpnWithPermission()
            }
        }
    }

    /** Starts the VPN, requesting the system VPN permission first when needed. */
    private fun startVpnWithPermission() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(requireContext())
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            // The disc is disabled in this state, so this is a backstop rather than the user's
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
     * only truthful signal in this process is [MainViewModel.isRunning], driven by the daemon's
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
        // The figures are NOT cleared here: the strip is still fading out at this point, and
        // blanking it first would fade out an empty box. clearFigures() runs when the fade lands.
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
     * ([MainViewModel.fallbackInProgress]) the flag must survive, or the next START_SUCCESS re-arms
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

    /** «Подписка» opens Аккаунт, which is where a subscription is managed. */
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
     * this screen.
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
