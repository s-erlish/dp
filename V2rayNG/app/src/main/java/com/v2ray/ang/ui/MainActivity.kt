package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.hasExpiry
import com.v2ray.ang.dto.entities.hasUserInfo
import com.v2ray.ang.dto.entities.isExpired
import com.v2ray.ang.dto.entities.isUnlimited
import com.v2ray.ang.dto.entities.trafficFraction
import com.v2ray.ang.dto.entities.usedTraffic
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.enums.PingMethod
import android.text.format.DateFormat
import android.view.View
import android.view.animation.OvershootInterpolator
import com.v2ray.ang.auth.AuthTokenStore
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.template.TemplateManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.tv.TvReceiveActivity
import com.v2ray.ang.tv.TvSendActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MemoryStatsManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var serversAdapter: MainRecyclerAdapter
    private lateinit var homeAdapter: MainRecyclerAdapter
    // Home server list collapse state, toggled by the meta-bar chevron.
    private var homeListCollapsed = false

    private val shareMethod: Array<out String> by lazy { resources.getStringArray(R.array.share_method) }
    private val shareMethodMore: Array<out String> by lazy { resources.getStringArray(R.array.share_method_more) }

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var connectionStartTime = 0L

    // Custom gray status toast (VPN state). Kept so a new one can cancel the previous
    // instead of queueing behind it.
    private var statusToast: android.widget.Toast? = null
    // Tracks the last delivered running state so status toasts only fire on a real
    // transition (not on the LiveData value replay after a rotation/theme recreate).
    private var lastRunningState: Boolean? = null
    // True between a connect tap and the definitive running/failed result, so a start that
    // ends in "not running" is reported as a failure rather than a silent revert.
    private var connectInProgress = false
    // Home toolbar auto-hide: whether the app bar is currently slid out of view.
    private var toolbarHidden = false

    // Gentle breathing pulse on the shield while the tunnel is establishing.
    private var connectPulse: Animator? = null
    // The rotating connect arc is shared by the "connecting" state and subscription
    // loading; these track who currently wants it visible so neither hides the other's.
    private var connectArcConnecting = false
    private var connectArcSubLoads = 0

    // Auto-fallback: one-shot post-connect health check that switches to the fastest
    // working server if the current tunnel doesn't actually pass traffic.
    // The "already fired this session" flag lives in the ViewModel (autoFallbackUsed).
    private var healthCheckPending = false
    private val healthCheckRunnable = Runnable {
        if (mainViewModel.isRunning.value == true) {
            healthCheckPending = true
            mainViewModel.testCurrentServerRealPing()
        }
    }

    // Connect watchdog: if a start neither succeeds nor reports a failure within the timeout
    // (e.g. the core/daemon process crashed without broadcasting any state), recover the UI to
    // idle instead of hanging forever on "Подключение…".
    private val connectWatchdogRunnable = Runnable {
        if (mainViewModel.isRunning.value != true) {
            // Render idle through the existing state path and tell the user the start failed.
            connectInProgress = false
            applyRunningState(isLoading = false, isRunning = false)
            showStatusToast(getString(R.string.toast_status_failed))
        }
    }

    // Live app-memory card (home), refreshed every 2s while the activity is visible.
    private val memoryRunnable = object : Runnable {
        override fun run() {
            updateMemoryCard()
            timerHandler.postDelayed(this, 2000L)
        }
    }

    private companion object {
        const val KEY_CONNECTION_START = "cache_connection_start_time"
        const val HEALTH_CHECK_DELAY_MS = 7000L
        // Upper bound for a connect attempt before the UI gives up and returns to idle.
        const val CONNECT_TIMEOUT_MS = 20000L
        // Remembers which bottom-nav tab was selected so it survives an activity
        // recreate (theme/language change) instead of snapping back to Home.
        const val KEY_SELECTED_NAV = "selected_bottom_nav"
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRecreateUi()) {
            recreate()
            return@registerForActivityResult
        }
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            mainViewModel.reloadServerList()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.app_name))
        applyThemeDecorations()
        setupEdgeToEdge()

        // The redesigned Home always shows the inline up/down speed row, so the traffic-stats
        // pipeline must be on: without this the core config omits the stats outbound and the
        // speed-notification loop never runs, so the row is stuck on «0 KB/s». Enabling it here
        // (before any connect) makes real speed flow through updateSpeedAction while connected.
        MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, true)

        // All servers are shown in one flat, provider-grouped list (no subscription tabs).
        mainViewModel.subscriptionId = ""
        setupServerLists()

        setupBottomNav()
        // Keep the user on the tab they were on when the activity is recreated
        // (e.g. after a theme or language change) instead of jumping back to Home.
        val restoredNav = savedInstanceState?.getInt(KEY_SELECTED_NAV, R.id.nav_home) ?: R.id.nav_home
        if (restoredNav != R.id.nav_home && binding.bottomNav.selectedItemId != restoredNav) {
            binding.bottomNav.selectedItemId = restoredNav
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.bottomNav.selectedItemId != R.id.nav_home ->
                        binding.bottomNav.selectedItemId = R.id.nav_home

                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        binding.cardConnect.setOnClickListener {
            animateConnectPress()
            handleFabAction()
        }

        setupServersHeader()
        setupHomeMetaBar()
        setupEmptyState()
        setupAccountHeader()
        setupSettings()
        setupViewModel()
        setupToolbarAutoHide()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    /** Persist the selected tab so a theme/language recreate does not reset it to Home. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV, binding.bottomNav.selectedItemId)
    }

    /**
     * Wires the bottom navigation: Home shows the connect hero, Servers shows the
     * subscription/server list, and Settings shows the custom Incy settings screen.
     */
    private fun setupBottomNav() {
        // Disable Material's built-in bottom-inset auto-padding: setupEdgeToEdge already
        // applies the single gesture-bar inset. Without this the two stack and the items
        // float well above the bottom edge (the "raised nav" bug).
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }
        showTab(R.id.nav_home)
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(R.id.nav_home)
                    true
                }

                R.id.nav_servers -> {
                    showTab(R.id.nav_servers)
                    true
                }

                R.id.nav_settings -> {
                    showTab(R.id.nav_settings)
                    true
                }

                else -> false
            }
        }
    }

    private fun showTab(tab: Int) {
        binding.groupHome.isVisible = tab == R.id.nav_home
        binding.groupServers.isVisible = tab == R.id.nav_servers
        binding.groupSettings.root.isVisible = tab == R.id.nav_settings
        // The auto-hide only tracks Home scrolling; make sure leaving/returning to a tab never
        // strands the app bar (and its shifted content) in the hidden position.
        showToolbar()
    }

    /**
     * True edge-to-edge: the home gradient (home_root) draws behind the status and nav
     * bars; the app bar receives a top inset pad (so the toolbar clears the clock) and the
     * bottom nav a bottom inset pad (so items clear the gesture bar). The bars themselves
     * stay transparent (handled by the theme, not touched here).
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appbarLayout.updatePadding(top = bars.top)
            // Single gesture-bar inset so the nav hugs the very bottom edge. Material's
            // BottomNavigationView also auto-pads by this inset; that auto-pad is disabled
            // in setupBottomNav so this stays the one source of truth (no doubled gap).
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            // The nav now overlays the content, so pad the scrollable lists to keep the
            // last row clear of the nav (nav height + the gesture-bar inset).
            val navPad = bars.bottom + (72 * resources.displayMetrics.density).toInt()
            binding.rvHomeServers.updatePadding(bottom = navPad)
            binding.rvServers.updatePadding(bottom = navPad)
            insets
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index -> refreshServerLists(index ?: -1) }
        mainViewModel.updateSpeedAction.observe(this) { (down, up) ->
            binding.tvDownloadSpeed.text = down.toSpeedString()
            binding.tvUploadSpeed.text = up.toSpeedString()
        }
        mainViewModel.fastConnectAction.observe(this) { guid ->
            // One-shot event: ignore the retained value replayed on recreate/rotation.
            if (!mainViewModel.consumeFastConnectEvent()) return@observe
            if (guid == null) {
                connectInProgress = false
                showStatusToast(getString(R.string.toast_status_failed))
                return@observe
            }
            updateSelectedServer()
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            } else {
                // Mark the attempt so a failed fast-connect is reported as «Не удалось подключиться».
                connectInProgress = true
                applyRunningState(isLoading = true, isRunning = false)
                scheduleConnectWatchdog()
                startVpnWithPermission()
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            // A definitive running/stopped state arrived (success or failure): the connect
            // attempt is over, so the watchdog is no longer needed.
            cancelConnectWatchdog()
            applyRunningState(false, isRunning)
            if (isRunning) scheduleHealthCheckIfEnabled() else cancelHealthCheck()

            // Subtle gray status toast, fired only on a genuine transition. LiveData replays
            // its last value on rotation/theme recreate, and the state present at launch must
            // not toast, so a connected/disconnected toast needs a known prior state (or an
            // in-progress connect for the "connected"/"failed" cases).
            val prev = lastRunningState
            if (isRunning) {
                if (connectInProgress || prev == false) {
                    showStatusToast(getString(R.string.toast_status_connected))
                }
            } else {
                when {
                    connectInProgress -> showStatusToast(getString(R.string.toast_status_failed))
                    prev == true -> showStatusToast(getString(R.string.toast_status_disconnected))
                }
            }
            connectInProgress = false
            lastRunningState = isRunning
        }
        mainViewModel.delayResultAction.observe(this) { time ->
            if (!healthCheckPending) return@observe
            healthCheckPending = false
            val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)
            if (enabled && !mainViewModel.autoFallbackUsed && time < 0 && mainViewModel.isRunning.value == true) {
                // Mark used BEFORE restarting so the restart's own START_SUCCESS doesn't re-arm.
                mainViewModel.autoFallbackUsed = true
                toast(getString(R.string.auto_fallback_switching))
                // Exclude the server that just failed so we don't switch back to it.
                mainViewModel.fastConnect(excludeGuid = MmkvManager.getSelectServer())
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    /**
     * Creates the two RecyclerViews (Servers tab = grouped, Home = flat) sharing one
     * adapter each, both driven by the same all-servers cache.
     */
    private fun setupServerLists() {
        val listener = ActivityAdapterListener()

        serversAdapter = MainRecyclerAdapter(mainViewModel, listener)
        binding.rvServers.setHasFixedSize(true)
        binding.rvServers.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.rvServers, this, R.drawable.custom_divider)
        binding.rvServers.adapter = serversAdapter

        homeAdapter = MainRecyclerAdapter(mainViewModel, listener)
        binding.rvHomeServers.setHasFixedSize(false)
        binding.rvHomeServers.layoutManager = LinearLayoutManager(this)
        binding.rvHomeServers.isNestedScrollingEnabled = false
        addCustomDividerToRecyclerView(binding.rvHomeServers, this, R.drawable.custom_divider)
        binding.rvHomeServers.adapter = homeAdapter

        // Long-press a server row -> Incy server-actions bottom sheet (S3 moved inline actions here).
        serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }
        homeAdapter.onItemLongClick = { guid -> showServerActions(guid) }
    }

    /**
     * Opens the Incy server-actions bottom sheet for [guid] (long-press entry point).
     * Each action delegates to an existing per-server flow; duplicate reuses
     * [MmkvManager.encodeServerConfig] with a blank guid to mint a fresh copy.
     */
    private fun showServerActions(guid: String) {
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
            onDelete = { removeServer(guid, serversAdapter.positionOfGuid(guid)) },
        ).show()
    }

    /** Wires the Servers tab header: title actions and search. */
    private fun setupServersHeader() {
        val header = binding.layoutServersHeader
        header.btnCollapseAll.setOnClickListener { serversAdapter.toggleCollapseAll() }
        header.btnRefreshAll.setOnClickListener { importConfigViaSub() }
        header.btnSpeedtestAll.setOnClickListener {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllServers()
        }
        header.btnAdd.setOnClickListener { showImportMenu(it) }
        header.etSearch.doAfterTextChanged { mainViewModel.filterConfig(it?.toString().orEmpty()) }
    }

    /** Popup with the full import/actions menu, anchored to the header "+" button. */
    private fun showImportMenu(anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    private fun setupEmptyState() {
        binding.layoutEmpty.btnImportClipboard.setOnClickListener { importClipboard() }
        binding.layoutEmpty.btnScanQr.setOnClickListener { importQRcode() }
        // Home empty state (shown when no subscriptions/servers exist yet).
        binding.layoutHomeEmpty.btnHomeAddQr.setOnClickListener { importQRcode() }
        binding.layoutHomeEmpty.btnHomeAddClipboard.setOnClickListener { importClipboard() }
    }

    /**
     * On Home, when there are no servers show ONLY the empty-state card (two add buttons)
     * and hide both the provider meta bar and the server list; otherwise show the list
     * (respecting the chevron collapse state) and let [bindMetaBar] own the meta bar.
     */
    private fun updateHomeEmptyState() {
        val empty = mainViewModel.serversCache.isEmpty()
        binding.layoutHomeEmpty.homeEmptyRoot.isVisible = empty
        if (empty) {
            binding.layoutHomeMetaBar.root.isVisible = false
            binding.rvHomeServers.isVisible = false
            // Nothing selectable: neutral under-shield label, not a stale server name.
            if (mainViewModel.isRunning.value != true) {
                binding.tvConnectionStatus.text = getString(R.string.home_select_server)
            }
        } else {
            applyHomeListVisibility()
        }
    }

    /** Applies the Home server-list visibility and chevron rotation from the collapse flag. */
    private fun applyHomeListVisibility() {
        val show = mainViewModel.serversCache.isNotEmpty() && !homeListCollapsed
        binding.rvHomeServers.isVisible = show
        binding.layoutHomeMetaBar.btnCollapse.rotation = if (homeListCollapsed) -90f else 0f
    }

    /**
     * Rebuilds both lists from the current cache and refreshes the Servers-tab chrome
     * (subtitle counts, protocol chips, empty-state visibility) plus the Home meta bar.
     */
    private fun refreshServerLists(index: Int) {
        val subs = mainViewModel.getProviderGroups()
        serversAdapter.setSections(mainViewModel.serversCache, subs, showHeaders = true, index = index)
        homeAdapter.setSections(mainViewModel.serversCache, subs, showHeaders = false, index = index)
        updateServersChrome(subs.size)
        bindHomeMetaBar()
        updateHomeEmptyState()
    }

    private fun updateServersChrome(providerCount: Int) {
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
     * Binds the Home provider meta bar to the selected server's subscription (or the first
     * provider). Reuses the collapsible meta-bar layout shared with the old fragment.
     */
    private fun setupHomeMetaBar() {
        val meta = binding.layoutHomeMetaBar
        meta.btnCollapse.setOnClickListener { toggleHomeServerList() }
        meta.btnRefresh.setOnClickListener { refreshHomeSub() }
        meta.btnPing.setOnClickListener {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllServers()
        }
        meta.btnPin.setOnClickListener { toggleHomePin() }
        meta.btnSupport.setOnClickListener { openSubUrl(MmkvManager.decodeSubscription(currentMetaSubId())?.supportUrl) }
        meta.btnTelegram.setOnClickListener { openSubUrl(MmkvManager.decodeSubscription(currentMetaSubId())?.supportUrl) }
        meta.root.setOnLongClickListener { confirmDeleteSubscription(); true }
        bindHomeMetaBar()
    }

    /** Long-press the Home subscription card to delete the subscription and its servers. */
    private fun confirmDeleteSubscription() {
        val subId = currentMetaSubId()
        if (subId.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.sub_delete)
            .setMessage(R.string.sub_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MmkvManager.removeSubscription(subId)
                mainViewModel.reloadServerList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * The meta-bar chevron now shows/hides the Home SERVER LIST (not the meta-bar body,
     * which stays permanently visible). Rotates the chevron to reflect the list state.
     */
    private fun toggleHomeServerList() {
        homeListCollapsed = !homeListCollapsed
        applyHomeListVisibility()
    }

    /**
     * Home account header (Telegram login / account chip).
     * Entirely hidden unless a backend is configured, so the no-backend build looks unchanged.
     */
    private fun setupAccountHeader() {
        val open = View.OnClickListener {
            requestActivityLauncher.launch(Intent(this, LoginActivity::class.java))
        }
        binding.layoutHomeAccount.btnTelegramLogin.setOnClickListener(open)
        binding.layoutHomeAccount.chipAccount.setOnClickListener(open)
        updateAccountHeader()
    }

    private fun updateAccountHeader() {
        val header = binding.layoutHomeAccount
        val configured = BackendConfig.isConfigured()
        val loggedIn = configured && AuthTokenStore.isLoggedIn()
        header.root.isVisible = configured
        header.btnTelegramLogin.isVisible = configured && !loggedIn
        header.chipAccount.isVisible = loggedIn
        if (loggedIn) {
            val user = AuthTokenStore.getUser()
            val name = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.username?.takeIf { it.isNotBlank() }
                ?: getString(R.string.auth_account)
            header.tvAccountName.text = name
            header.tvAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "?"
            header.tvAccountSub.text = subscriptionStatusLine()
        }
    }

    /** Subtitle for the signed-in account chip, read from the login-imported subscription. */
    private fun subscriptionStatusLine(): String {
        val sub = AuthTokenStore.managedSubGuid()
            .takeIf { it.isNotEmpty() }
            ?.let { MmkvManager.decodeSubscription(it) }
            ?: return getString(R.string.auth_subscription_none)
        if (!sub.enabled) return getString(R.string.auth_subscription_expired)
        val expire = sub.expire
        if (expire <= 0L) return getString(R.string.auth_subscription_active)
        val date = DateFormat.getDateFormat(this).format(Date(expire * 1000L))
        return getString(R.string.auth_subscription_active_until, date)
    }

    /** Subscription shown in the Home meta bar: the selected server's, else the first provider. */
    private fun currentMetaSubId(): String {
        mainViewModel.findSubscriptionIdBySelect()?.takeIf { it.isNotEmpty() }?.let { return it }
        return mainViewModel.getProviderGroups().firstOrNull()?.id.orEmpty()
    }

    private fun bindHomeMetaBar() {
        val subId = currentMetaSubId()
        val sub = if (subId.isEmpty()) null else MmkvManager.decodeSubscription(subId)
        bindMetaBar(sub)
    }

    private fun toggleHomePin() {
        val subId = currentMetaSubId()
        val sub = MmkvManager.decodeSubscription(subId) ?: return
        sub.pinned = !sub.pinned
        MmkvManager.encodeSubscription(subId, sub)
        bindHomeMetaBar()
        mainViewModel.reloadServerList()
    }

    private fun refreshHomeSub() {
        val meta = binding.layoutHomeMetaBar
        // Progress shows on the connect circle (shared rotating arc), not a top bar.
        showLoading()
        meta.btnRefresh.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            launch(Dispatchers.Main) {
                if (result.configCount > 0) mainViewModel.reloadServerList()
                bindHomeMetaBar()
                hideLoading()
                meta.btnRefresh.isEnabled = true
                if (result.successCount > 0) {
                    toastSuccess(R.string.toast_success)
                } else if (result.failureCount > 0) {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun openSubUrl(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
        }
    }

    /**
     * Display heading for the meta bar: the provider-sent `profile-title` first, then the
     * subscription remarks, and finally the app name - never the raw "Default" placeholder.
     */
    private fun metaTitle(sub: SubscriptionItem): String {
        sub.profileTitle.takeIf { it.isNotBlank() }?.let { return it }
        val remarks = sub.remarks.trim()
        return if (remarks.isNotEmpty() && !remarks.equals("Default", ignoreCase = true)) {
            remarks
        } else {
            getString(R.string.app_name)
        }
    }

    /**
     * Small subtitle shown under the meta-bar title: the last successful update timestamp and the
     * auto-update interval, e.g. "09.07.2026 07:08 · Автообновление — 1 ч." (Выкл when auto-update
     * is off). [SubscriptionItem.lastUpdated] is epoch millis (-1 == never); [updateInterval] is
     * minutes.
     */
    private fun metaSubtitle(sub: SubscriptionItem): String {
        val last = if (sub.lastUpdated > 0L) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(sub.lastUpdated))
        } else {
            getString(R.string.sub_meta_updated_never)
        }
        val interval = if (!sub.autoUpdate) {
            getString(R.string.sub_auto_update_off)
        } else {
            val minutes = sub.updateInterval
            if (minutes >= 60L && minutes % 60L == 0L) {
                getString(R.string.sub_update_interval_hours, (minutes / 60L).toInt())
            } else {
                getString(R.string.sub_update_interval_minutes, minutes.toInt())
            }
        }
        return "$last · " + getString(R.string.sub_auto_update_label, interval)
    }

    /**
     * Repaints the meta bar from persisted subscription metadata (moved from GroupServerFragment).
     * Traffic is drawn Happ-style as a rounded pill (the [progressTraffic] track) with the usage
     * label centered on it; the expiry marker shows the infinity glyph when there is no (or an
     * effectively unlimited) expiry, otherwise the real date.
     */
    private fun bindMetaBar(sub: SubscriptionItem?) {
        val meta = binding.layoutHomeMetaBar
        if (sub == null) {
            meta.root.visibility = android.view.View.GONE
            return
        }
        meta.root.visibility = android.view.View.VISIBLE
        meta.tvSubTitle.text = metaTitle(sub)
        meta.tvMetaSubtitle.text = metaSubtitle(sub)
        meta.tvMetaSubtitle.visibility = android.view.View.VISIBLE

        val primaryColor = MaterialColors.getColor(meta.btnPin, androidx.appcompat.R.attr.colorPrimary)
        val onVariant = MaterialColors.getColor(meta.btnPin, com.google.android.material.R.attr.colorOnSurfaceVariant)
        meta.btnPin.setColorFilter(if (sub.pinned) primaryColor else onVariant)
        meta.btnPin.contentDescription = getString(if (sub.pinned) R.string.sub_unpin else R.string.sub_pin)

        if (sub.announce.isNotBlank()) {
            meta.tvAnnounce.visibility = android.view.View.VISIBLE
            meta.tvAnnounce.text = sub.announce
        } else {
            meta.tvAnnounce.visibility = android.view.View.GONE
        }
        meta.btnSupport.visibility = if (sub.supportUrl.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        // Compact Telegram shortcut in the collapsed header, shown only when a support URL exists.
        meta.btnTelegram.visibility = if (sub.supportUrl.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE

        if (!sub.hasUserInfo) {
            meta.layoutTraffic.visibility = android.view.View.GONE
            return
        }
        meta.layoutTraffic.visibility = android.view.View.VISIBLE

        val onSurfaceColor = MaterialColors.getColor(meta.tvTraffic, com.google.android.material.R.attr.colorOnSurface)
        val variantColor = MaterialColors.getColor(meta.tvExpiry, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val redColor = ContextCompat.getColor(this, R.color.colorPingRed)
        val greenColor = ContextCompat.getColor(this, R.color.colorPing)

        // Traffic pill: "usedTraffic / total-or-∞" centered on the rounded track.
        val near = !sub.isUnlimited && sub.trafficFraction >= 0.9f
        meta.tvTraffic.text = if (sub.isUnlimited) {
            getString(R.string.sub_traffic_unlimited, sub.usedTraffic.toTrafficString())
        } else {
            getString(
                R.string.sub_traffic_used,
                sub.usedTraffic.toTrafficString(),
                sub.totalTraffic.toTrafficString()
            )
        }
        meta.tvTraffic.setTextColor(onSurfaceColor)
        // Unlimited traffic keeps an empty rounded track behind the label instead of a filled bar.
        val fillFraction = if (sub.isUnlimited) 0f else sub.trafficFraction
        meta.progressTraffic.setProgressCompat((fillFraction * 1000).toInt(), false)
        meta.progressTraffic.setIndicatorColor(if (near) redColor else greenColor)

        // Expiry: ∞ when absent or effectively unlimited (panels sometimes send a huge timestamp
        // ~year 2088+ instead of 0), otherwise the real "до <date>".
        val unlimitedExpireThreshold = 3_723_840_000L // ~2088-01-01 in epoch seconds
        val expiryUnlimited = sub.expire <= 0L || sub.expire >= unlimitedExpireThreshold
        when {
            expiryUnlimited -> {
                meta.tvExpiry.text = getString(R.string.sub_infinity)
                meta.tvExpiry.setTextColor(variantColor)
            }
            sub.isExpired -> {
                meta.tvExpiry.text = getString(R.string.sub_expired)
                meta.tvExpiry.setTextColor(redColor)
            }
            else -> {
                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(sub.expire * 1000))
                meta.tvExpiry.text = getString(R.string.sub_expires, date)
                meta.tvExpiry.setTextColor(variantColor)
            }
        }
        meta.tvExpiry.visibility = android.view.View.VISIBLE
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
        serversAdapter.removeServerSub(guid, position)
        homeAdapter.removeServerSub(guid, position)
        updateServersChrome(mainViewModel.getProviderGroups().size)
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            serversAdapter.setSelectServer(selected, guid)
            homeAdapter.setSelectServer(selected, guid)
            updateSelectedServer()
            bindHomeMetaBar()
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            }
        }
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

    private fun handleFabAction() {
        // A manual connect/disconnect starts a fresh session: allow auto-fallback again.
        mainViewModel.autoFallbackUsed = false

        if (mainViewModel.isRunning.value == true) {
            // Stop: no "connecting" visual, the isRunning observer will settle the idle state
            // and show the «Отключено» toast.
            connectInProgress = false
            cancelConnectWatchdog()
            CoreServiceManager.stopVService(this)
        } else {
            // Start: show the subtle blue "connecting" state (pulsing ring), never a bright fill.
            connectInProgress = true
            showStatusToast(getString(R.string.toast_status_connecting))
            applyRunningState(isLoading = true, isRunning = false)
            scheduleConnectWatchdog()
            startVpnWithPermission()
        }
    }

    /**
     * Starts the VPN, requesting the system VPN permission first when needed.
     */
    private fun startVpnWithPermission() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
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
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    /**
     * The blue/light and blue/dark backgrounds are theme-qualified drawables, but the mono
     * overlay is a runtime style overlay (not a resource qualifier), so the decorative home
     * gradient, glow and ring must be swapped to neutral grey variants here when mono is active.
     */
    private fun applyThemeDecorations() {
        val mono = MmkvManager.decodeSettingsString(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE) == BaseActivity.THEME_MONO
        if (!mono) return
        binding.homeRoot.setBackgroundResource(R.drawable.bg_home_gradient_mono)
        binding.viewConnectGlow.setBackgroundResource(R.drawable.bg_connect_glow_mono)
        binding.viewConnectRing.setBackgroundResource(R.drawable.bg_connect_ring_mono)
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        // Connecting: a thin rotating arc sweeps the ring + a gentle shield pulse, blue
        // outline shield. No solid blue fill (the glow stays off while establishing).
        if (isLoading) {
            val active = themeColor(R.attr.connectActiveColor)
            binding.viewConnectGlow.visibility = android.view.View.INVISIBLE
            startConnectingAnim()
            binding.imgConnect.setColorFilter(active)
            binding.tvConnectionStatus.setTextColor(active)
            binding.tvConnectionStatus.text = getString(R.string.connection_connecting)
            return
        }

        if (isRunning) {
            // Connected: subtle blue glow (soft halo, not a solid fill) + blue shield,
            // label shows the connected server name.
            val connected = themeColor(R.attr.connectedColor)
            stopConnectingAnim()
            binding.viewConnectGlow.visibility = android.view.View.VISIBLE
            binding.imgConnect.setColorFilter(connected)
            binding.tvConnectionStatus.setTextColor(connected)
            binding.cardConnect.contentDescription = getString(R.string.action_stop_service)
            binding.tvConnectionStatus.text = selectedServerName()
            startConnectionTimer()
        } else {
            // Idle: neutral shield, no glow; label is a neutral status (never a server name).
            stopConnectingAnim()
            binding.viewConnectGlow.visibility = android.view.View.INVISIBLE
            binding.imgConnect.setColorFilter(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            binding.tvConnectionStatus.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            binding.cardConnect.contentDescription = getString(R.string.tasker_start_service)
            binding.tvConnectionStatus.text = idleStatusText()
            stopConnectionTimer()
            binding.tvDownloadSpeed.text = getString(R.string.speed_zero)
            binding.tvUploadSpeed.text = getString(R.string.speed_zero)
        }
    }

    /**
     * Resolves a themed color attribute (respects the active blue/mono overlay).
     */
    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.cardConnect, attr)

    /**
     * Shows the subtle, neutral gray status toast (custom pill) that reflects the VPN state —
     * «Подключение…» / «Прокси подключён» / «Отключено» / «Не удалось подключиться». Neutral
     * surface colour (no green/system style). Cancels any previous status toast so states never
     * queue up behind each other.
     */
    @Suppress("DEPRECATION") // custom Toast view (Toast(context)/setView) is the intended, subtle status pill
    private fun showStatusToast(text: CharSequence) {
        statusToast?.cancel()
        val view = layoutInflater.inflate(R.layout.toast_status, null)
        view.findViewById<android.widget.TextView>(R.id.tv_toast_status).text = text
        statusToast = android.widget.Toast(this).apply {
            duration = android.widget.Toast.LENGTH_SHORT
            setView(view)
            val yOffset = (110 * resources.displayMetrics.density).toInt()
            setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, yOffset)
            show()
        }
    }

    /**
     * Auto-hides the Home app bar on downward scroll and brings it back on upward scroll (or at
     * the very top). The AppBarLayout is a plain child here (no CoordinatorLayout scroll flags),
     * so this is driven manually: the app bar and the tab content slide up together
     * (translationY) so no empty band is left where the bar was; the bottom nav + scrim overlay
     * the freed strip. State-based (animate to shown/hidden), so it never fights per-frame with
     * an active fling.
     */
    private fun setupToolbarAutoHide() {
        // Explicit SAM: NestedScrollView also inherits View.setOnScrollChangeListener, whose
        // listener is likewise a 5-arg interface, so a bare lambda would be an ambiguous overload.
        binding.groupHome.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                when {
                    scrollY <= 0 -> showToolbar()
                    dy > 6 && scrollY > binding.appbarLayout.height -> hideToolbar()
                    dy < -6 -> showToolbar()
                }
            }
        )
    }

    private fun hideToolbar() {
        if (toolbarHidden) return
        toolbarHidden = true
        animateToolbar(-binding.appbarLayout.height.toFloat())
    }

    private fun showToolbar() {
        if (!toolbarHidden) return
        toolbarHidden = false
        animateToolbar(0f)
    }

    /** Slides the app bar and the tab content to [translationY] in lock-step. */
    private fun animateToolbar(translationY: Float) {
        val interpolator = android.view.animation.DecelerateInterpolator()
        binding.appbarLayout.animate().cancel()
        binding.mainContent.animate().cancel()
        binding.appbarLayout.animate()
            .translationY(translationY).setInterpolator(interpolator).setDuration(220).start()
        binding.mainContent.animate()
            .translationY(translationY).setInterpolator(interpolator).setDuration(220).start()
    }

    /**
     * Incy-style press feedback on the connect button: a distinct "depress" (scale-down, as if
     * pushed in) that springs back with an overshoot. Purely cosmetic — there is NO colour/fill
     * change on press (the ripple and pressed foreground are cleared in the layout), so this
     * scale is the only press feedback.
     */
    private fun animateConnectPress() {
        binding.cardConnect.animate().cancel()
        binding.cardConnect.animate()
            .scaleX(0.88f).scaleY(0.88f)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setDuration(110)
            .withEndAction {
                binding.cardConnect.animate()
                    .scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2.5f))
                    .setDuration(260)
                    .start()
            }
            .start()
    }

    /**
     * "Connecting" visual: shows the thin rotating arc around the ring and a gentle
     * breathing pulse on the shield. No solid blue fill (the glow drawable stays off).
     */
    private fun startConnectingAnim() {
        connectArcConnecting = true
        refreshConnectArc()
        connectPulse?.cancel()
        connectPulse = ObjectAnimator.ofFloat(binding.imgConnect, View.ALPHA, 1f, 0.45f).apply {
            duration = 850
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** Stops the connecting pulse and hides the arc (unless a subscription is still loading). */
    private fun stopConnectingAnim() {
        connectArcConnecting = false
        refreshConnectArc()
        connectPulse?.cancel()
        connectPulse = null
        binding.imgConnect.alpha = 1f
    }

    /**
     * The arc spins whenever we are connecting OR a subscription is loading. Uses the indicator's
     * own show()/hide() (grow-in / shrink-out per the layout's animationBehavior) for a smooth
     * start/stop, and pins its colour to the connecting accent so the arc always matches the
     * shield (correct in both the blue and mono overlays).
     */
    private fun refreshConnectArc() {
        val show = connectArcConnecting || connectArcSubLoads > 0
        if (show) {
            binding.progressConnect.setIndicatorColor(themeColor(R.attr.connectActiveColor))
            binding.progressConnect.show()
        } else {
            binding.progressConnect.hide()
        }
    }

    /**
     * Routes subscription add/refresh progress onto the connect circle (the shared rotating
     * arc) instead of a top progress bar. Ref-counted so overlapping loads don't clash with
     * the connecting state. Overrides the BaseActivity top-bar spinner.
     */
    override fun showLoading() {
        runOnUiThread {
            connectArcSubLoads++
            refreshConnectArc()
        }
    }

    override fun hideLoading() {
        runOnUiThread {
            if (connectArcSubLoads > 0) connectArcSubLoads--
            refreshConnectArc()
        }
    }

    /**
     * Updates the selected server name shown in the hero panel.
     */
    /**
     * Refreshes the home memory card (MB + green/amber/red status), or hides it per preference.
     */
    private fun updateMemoryCard() {
        val show = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_MEMORY, false)
        binding.cardMemory.isVisible = show
        if (!show) return
        val mb = MemoryStatsManager.currentUsedMb()
        val (labelRes, colorRes) = when (MemoryStatsManager.levelFor(mb)) {
            MemoryStatsManager.Level.NORMAL -> R.string.memory_normal to R.color.color_connected
            MemoryStatsManager.Level.ELEVATED -> R.string.memory_elevated to R.color.colorConfigType
            MemoryStatsManager.Level.HIGH -> R.string.memory_high to R.color.colorPingRed
        }
        binding.tvMemory.text = getString(R.string.memory_value, mb, getString(labelRes))
        binding.dotMemory.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(colorRes))
    }

    /** The name shown under the shield ONLY when connected (selected server remarks). */
    private fun selectedServerName(): String {
        val guid = MmkvManager.getSelectServer()
        val remarks = guid?.let { MmkvManager.decodeServerConfig(it)?.remarks }
        return remarks?.takeIf { it.isNotBlank() } ?: getString(R.string.home_select_server)
    }

    /**
     * Neutral under-shield status when NOT connected: never the server name. Shows
     * «Не подключено» when a server is selected, «Выберите сервер» when none is.
     */
    private fun idleStatusText(): String {
        val guid = MmkvManager.getSelectServer()
        val hasServer = guid?.let { MmkvManager.decodeServerConfig(it) } != null
        return getString(if (hasServer) R.string.home_not_connected else R.string.home_select_server)
    }

    private fun updateSelectedServer() {
        // Connecting/connected labels are owned by applyRunningState; when idle keep a
        // neutral status (the server name only appears once actually connected).
        if (mainViewModel.isRunning.value == true) return
        binding.tvConnectionStatus.text = idleStatusText()
    }

    /**
     * Starts a lightweight per-second timer showing the connection uptime.
     * Uses a single reused Runnable to keep memory/CPU footprint minimal.
     */
    private fun startConnectionTimer() {
        // Persist the start time so the uptime survives rotation / theme recreate.
        val stored = MmkvManager.decodeSettingsLong(KEY_CONNECTION_START, 0L)
        connectionStartTime = if (stored > 0L) {
            stored
        } else {
            System.currentTimeMillis().also { MmkvManager.encodeSettings(KEY_CONNECTION_START, it) }
        }
        binding.tvConnectionTime.visibility = android.view.View.VISIBLE
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.post(timerRunnable)
    }

    private fun stopConnectionTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        connectionStartTime = 0L
        MmkvManager.encodeSettings(KEY_CONNECTION_START, 0L)
        binding.tvConnectionTime.text = "00:00:00"
    }

    /**
     * Schedules the one-shot post-connect health check, if auto-fallback is enabled and it
     * hasn't already run this session.
     */
    private fun scheduleHealthCheckIfEnabled() {
        if (mainViewModel.autoFallbackUsed) return
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)) return
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_DELAY_MS)
    }

    /** Cancels a pending health check (on disconnect) without clearing the session flag. */
    private fun cancelHealthCheck() {
        healthCheckPending = false
        timerHandler.removeCallbacks(healthCheckRunnable)
    }

    /** Arms the connect watchdog so a stalled/crashed start can't hang the UI on "connecting". */
    private fun scheduleConnectWatchdog() {
        timerHandler.removeCallbacks(connectWatchdogRunnable)
        timerHandler.postDelayed(connectWatchdogRunnable, CONNECT_TIMEOUT_MS)
    }

    /** Cancels the connect watchdog once the attempt resolved (success/failure/stop). */
    private fun cancelConnectWatchdog() {
        timerHandler.removeCallbacks(connectWatchdogRunnable)
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (connectionStartTime == 0L) return
            val elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000
            val h = elapsed / 3600
            val m = (elapsed % 3600) / 60
            val s = elapsed % 60
            binding.tvConnectionTime.text = String.format("%02d:%02d:%02d", h, m, s)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        updateSelectedServer()
        updateAccountHeader()
        bindSettingsState()
        timerHandler.removeCallbacks(memoryRunnable)
        timerHandler.post(memoryRunnable)
    }

    override fun onPause() {
        super.onPause()
        timerHandler.removeCallbacks(memoryRunnable)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
            })

            searchView.setOnCloseListener {
                mainViewModel.filterConfig("")
                false
            }
        }
        return super.onCreateOptionsMenu(menu)
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
            // "Ввести вручную" — the single manual-entry option in the simplified "+" menu.
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
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
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }

                        countSub > 0 -> mainViewModel.reloadServerList()
                        else -> toastError(R.string.toast_failure)
                    }
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

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
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
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    /**
     * Locates and scrolls to the currently selected server in the flat Servers list.
     */
    private fun locateSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        // Ensure we are on the Servers tab so the list is visible.
        if (binding.bottomNav.selectedItemId != R.id.nav_servers) {
            binding.bottomNav.selectedItemId = R.id.nav_servers
        }
        val position = serversAdapter.positionOfGuid(selectedGuid)
        if (position < 0) {
            toast(R.string.toast_server_not_found_in_group)
            return
        }
        binding.rvServers.post {
            (binding.rvServers.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, binding.rvServers.height / 3)
                ?: binding.rvServers.smoothScrollToPosition(position)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    // ==================== Settings tab (Incy) ====================

    /**
     * Wires the custom "Настройки" tab (replaces the old navigation drawer). All row
     * click handlers live here; toggles/pickers read & write the same MMKV keys the
     * legacy SettingsActivity used, so values stay consistent. Switches are non-focusable
     * in XML, so the whole row drives them — no CheckedChange listeners (avoids feedback
     * loops when reflecting state in [bindSettingsState]).
     */
    private fun setupSettings() {
        val s = binding.groupSettings

        // ПОДКЛЮЧЕНИЕ
        s.rowMode.setOnClickListener { pickMode() }
        s.rowPerApp.setOnClickListener { requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java)) }
        s.rowBypassLan.setOnClickListener { toggleBypassLan() }
        s.rowDns.setOnClickListener { editDns() }
        s.rowPingMethod.setOnClickListener { pickPingMethod() }

        // ОБХОД БЛОКИРОВОК
        s.rowMux.setOnClickListener { toggleMux() }
        s.rowMuxConcurrency.setOnClickListener { editMuxConcurrency() }
        s.rowFragment.setOnClickListener { toggleFragment() }

        // ИНТЕРФЕЙС
        s.rowThemeDark.setOnClickListener { toggleDarkTheme() }
        s.rowThemeMono.setOnClickListener { toggleMono() }
        s.rowLanguage.setOnClickListener { pickLanguage() }
        s.rowBoot.setOnClickListener { toggleStartOnBoot() }

        // ПОДПИСКА
        s.rowSubAutoUpdate.setOnClickListener { pickSubAutoUpdate() }
        s.rowRouting.setOnClickListener { requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java)) }
        s.rowAssets.setOnClickListener { requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java)) }

        // УСТРОЙСТВА
        s.rowTvSend.setOnClickListener { startActivity(Intent(this, TvSendActivity::class.java)) }
        // "Принять подписку" only makes sense on an Android TV device.
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        s.rowTvReceive.isVisible = isTv
        s.dividerTvReceive.isVisible = isTv
        s.rowTvReceive.setOnClickListener { startActivity(Intent(this, TvReceiveActivity::class.java)) }

        // О ПРИЛОЖЕНИИ
        s.rowAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        s.rowBackup.setOnClickListener { requestActivityLauncher.launch(Intent(this, BackupActivity::class.java)) }
        s.valueAbout.text = BuildConfig.VERSION_NAME

        bindSettingsState()
    }

    /** Reflects all persisted settings values/toggle states into the settings tab. */
    private fun bindSettingsState() {
        val s = binding.groupSettings

        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN)
        s.valueMode.text = getString(if (mode == AppConfig.VPN) R.string.settings_mode_vpn else R.string.settings_mode_proxy)

        val perApp = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        s.valuePerApp.text = getString(if (perApp) R.string.settings_value_on else R.string.settings_value_off)

        s.valueDns.text = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        s.valuePingMethod.text = getString(pingMethodLabelRes(SettingsManager.getPingMethod()))
        s.valueMuxConcurrency.text = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8")

        val langEntries = resources.getStringArray(R.array.language_select)
        val langValues = resources.getStringArray(R.array.language_select_value)
        val curLang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, langValues.firstOrNull() ?: "auto").orEmpty()
        val li = langValues.indexOf(curLang).coerceAtLeast(0)
        s.valueLanguage.text = langEntries.getOrElse(li) { langEntries.firstOrNull().orEmpty() }

        s.valueSubAutoUpdate.text = currentSubAutoUpdateLabel()

        s.switchBypassLan.isChecked = isBypassLanOn()

        val muxOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
        s.switchMux.isChecked = muxOn
        s.rowMuxConcurrency.isVisible = muxOn
        s.dividerConcurrency.isVisible = muxOn

        s.switchFragment.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
        s.switchThemeDark.isChecked = isDarkThemeOn()
        s.switchThemeMono.isChecked = isMonoOn()
        s.switchBoot.isChecked = MmkvManager.decodeStartOnBoot()
    }

    private fun isBypassLanOn(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, "1") != "2"

    private fun isDarkThemeOn(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2") != "1"

    private fun isMonoOn(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE) == BaseActivity.THEME_MONO

    /** Restart the tunnel so a changed core-config setting takes effect immediately. */
    private fun restartIfRunning() {
        if (mainViewModel.isRunning.value == true) restartV2Ray()
    }

    private fun pickMode() {
        val entries = arrayOf(getString(R.string.settings_mode_vpn), getString(R.string.settings_mode_proxy))
        val values = arrayOf(AppConfig.VPN, "Proxy only")
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN).orEmpty()
        val idx = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_mode)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_MODE, values[which])
                bindSettingsState()
                restartIfRunning()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Maps a ping method to its short Russian label shown on the settings row. */
    private fun pingMethodLabelRes(method: PingMethod): Int = when (method) {
        PingMethod.PROXIED_REAL_DELAY -> R.string.settings_ping_method_real
        PingMethod.TCP_CONNECT -> R.string.settings_ping_method_tcp
        PingMethod.HTTP_URL -> R.string.settings_ping_method_http
        PingMethod.ICMP -> R.string.settings_ping_method_icmp
    }

    /**
     * Single-choice picker for the connection-test (ping) method. Writes the same
     * [AppConfig.PREF_PING_METHOD] key the "test all" logic reads via
     * [SettingsManager.getPingMethod], so the choice changes ping behavior immediately.
     */
    private fun pickPingMethod() {
        // Order shown to the user; index maps 1:1 to `values`.
        val values = arrayOf(
            PingMethod.PROXIED_REAL_DELAY,
            PingMethod.TCP_CONNECT,
            PingMethod.HTTP_URL,
            PingMethod.ICMP,
        )
        val entries = values.map { getString(pingMethodLabelRes(it)) }.toTypedArray()
        val current = SettingsManager.getPingMethod()
        val idx = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ping_method)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_PING_METHOD, values[which].prefValue)
                bindSettingsState()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleBypassLan() {
        val on = !isBypassLanOn()
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, if (on) "1" else "2")
        binding.groupSettings.switchBypassLan.isChecked = on
        restartIfRunning()
    }

    private fun editDns() {
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        val input = EditText(this).apply {
            setText(current)
            setSingleLine()
            hint = getString(R.string.settings_dns_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_dns)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim().ifEmpty { AppConfig.DNS_VPN }
                MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, value)
                bindSettingsState()
                restartIfRunning()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleMux() {
        val enabled = !MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_MUX_ENABLED, enabled)
        val s = binding.groupSettings
        s.switchMux.isChecked = enabled
        s.rowMuxConcurrency.isVisible = enabled
        s.dividerConcurrency.isVisible = enabled
        restartIfRunning()
    }

    private fun editMuxConcurrency() {
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_mux_concurrency)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = (input.text.toString().toIntOrNull() ?: 8).coerceIn(1, 1024)
                MmkvManager.encodeSettings(AppConfig.PREF_MUX_CONCURRENCY, value.toString())
                bindSettingsState()
                restartIfRunning()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleFragment() {
        val enabled = !MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, enabled)
        binding.groupSettings.switchFragment.isChecked = enabled
        restartIfRunning()
    }

    private fun toggleDarkTheme() {
        val dark = !isDarkThemeOn()
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, if (dark) "2" else "1")
        // AppCompat applies the night mode and recreates the activity to reflect it
        // (same path the legacy settings screen relies on).
        SettingsManager.setNightMode()
    }

    private fun toggleMono() {
        val mono = !isMonoOn()
        MmkvManager.encodeSettings(
            AppConfig.PREF_COLOR_THEME,
            if (mono) BaseActivity.THEME_MONO else BaseActivity.THEME_BLUE
        )
        // The mono overlay is applied in BaseActivity.onCreate, so recreate to pick it up.
        recreate()
    }

    private fun pickLanguage() {
        val entries = resources.getStringArray(R.array.language_select)
        val values = resources.getStringArray(R.array.language_select_value)
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, values.firstOrNull() ?: "auto").orEmpty()
        val idx = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, values[which])
                dialog.dismiss()
                // Locale is applied via BaseActivity.attachBaseContext on recreate.
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleStartOnBoot() {
        val enabled = !MmkvManager.decodeStartOnBoot()
        MmkvManager.encodeStartOnBoot(enabled)
        binding.groupSettings.switchBoot.isChecked = enabled
    }

    /** Interval options (minutes) offered by the subscription auto-update picker; 0 == off. */
    private val subAutoUpdateValues = longArrayOf(0L, 60L, 360L, 720L, 1440L)

    /** Short Russian label for a subscription auto-update interval in minutes (0 == off). */
    private fun subAutoUpdateLabel(minutes: Long): String = when (minutes) {
        0L -> getString(R.string.settings_value_off)
        60L -> getString(R.string.settings_sub_auto_update_1h)
        360L -> getString(R.string.settings_sub_auto_update_6h)
        720L -> getString(R.string.settings_sub_auto_update_12h)
        1440L -> getString(R.string.settings_sub_auto_update_24h)
        else -> getString(R.string.settings_sub_auto_update_minutes, minutes)
    }

    /** Row value: interval of any auto-updating subscription, or "Выкл" when none is enabled. */
    private fun currentSubAutoUpdateLabel(): String {
        val active = MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.autoUpdate }
            ?: return getString(R.string.settings_value_off)
        return subAutoUpdateLabel(active.subscription.updateInterval)
    }

    /**
     * Global subscription auto-update picker. There is no dedicated global pref key, so the
     * choice is applied across every stored subscription: [SubscriptionItem.autoUpdate] and
     * [SubscriptionItem.updateInterval] (in minutes) are written for each one, then the
     * WorkManager scheduler is re-synced via [SubscriptionUpdater.sync] so the new interval
     * takes effect immediately. Minutes are stored so the home meta-bar can read the value.
     */
    private fun pickSubAutoUpdate() {
        val entries = subAutoUpdateValues.map { subAutoUpdateLabel(it) }.toTypedArray()
        val active = MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.autoUpdate }
        val currentMinutes = if (active == null) 0L else active.subscription.updateInterval
        val idx = subAutoUpdateValues.indexOf(currentMinutes).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_sub_auto_update)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                val minutes = subAutoUpdateValues[which]
                MmkvManager.decodeSubscriptions().forEach { cache ->
                    val item = cache.subscription
                    if (minutes <= 0L) {
                        item.autoUpdate = false
                    } else {
                        item.autoUpdate = true
                        item.updateInterval = minutes
                    }
                    MmkvManager.encodeSubscription(cache.guid, item)
                }
                // Recalculate the next run time from the freshly persisted state.
                SubscriptionUpdater.sync(forceReschedule = true)
                bindSettingsState()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(memoryRunnable)
        timerHandler.removeCallbacks(connectWatchdogRunnable)
        stopConnectingAnim()
        statusToast?.cancel()
        super.onDestroy()
    }
}