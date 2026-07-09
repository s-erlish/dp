package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.AppConfig
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

class MainActivity : HelperBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var serversAdapter: MainRecyclerAdapter
    private lateinit var homeAdapter: MainRecyclerAdapter
    private var lastProtocolChipSet: List<EConfigType> = emptyList()

    private val shareMethod: Array<out String> by lazy { resources.getStringArray(R.array.share_method) }
    private val shareMethodMore: Array<out String> by lazy { resources.getStringArray(R.array.share_method_more) }

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var connectionStartTime = 0L

    // Infinite pulse animation on the connect glow while the tunnel is establishing.
    private var glowPulse: Animator? = null

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

        // All servers are shown in one flat, provider-grouped list (no subscription tabs).
        mainViewModel.subscriptionId = ""
        setupServerLists()

        // "More" bottom tab and edge-swipe open the drawer; secondary navigation lives there
        binding.navView.setNavigationItemSelectedListener(this)
        setupBottomNav()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)

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
        binding.layoutServerInfo.setOnClickListener { handleLayoutTestClick() }

        setupServersHeader()
        setupHomeMetaBar()
        setupEmptyState()
        setupAccountHeader()
        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    /**
     * Wires the bottom navigation: Home shows the connect hero, Servers shows the
     * subscription/server list, and More opens the side drawer with secondary screens.
     */
    private fun setupBottomNav() {
        showHomeTab(true)
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeTab(true)
                    true
                }

                R.id.nav_servers -> {
                    showHomeTab(false)
                    true
                }

                R.id.nav_more -> {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    false
                }

                else -> false
            }
        }
    }

    private fun showHomeTab(home: Boolean) {
        binding.groupHome.isVisible = home
        binding.groupServers.isVisible = !home
    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index -> refreshServerLists(index ?: -1) }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.updateSpeedAction.observe(this) { (down, up) ->
            binding.tvDownloadSpeed.text = down.toSpeedString()
            binding.tvUploadSpeed.text = up.toSpeedString()
        }
        mainViewModel.fastConnectAction.observe(this) { guid ->
            // One-shot event: ignore the retained value replayed on recreate/rotation.
            if (!mainViewModel.consumeFastConnectEvent()) return@observe
            if (guid == null) {
                setTestState(getString(R.string.connection_test_fail))
                toastError(R.string.toast_services_failure)
                return@observe
            }
            updateSelectedServer()
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            } else {
                applyRunningState(isLoading = true, isRunning = false)
                startVpnWithPermission()
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
            if (isRunning) scheduleHealthCheckIfEnabled() else cancelHealthCheck()
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

    /** Wires the Servers tab header: title actions, search, protocol chips. */
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
        header.chipGroupProtocol.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(id) ?: return@setOnCheckedStateChangeListener
            val type = chip.tag as? EConfigType
            mainViewModel.applyProtocolFilter(type)
        }
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
    }

    private fun updateServersChrome(providerCount: Int) {
        val serverCount = mainViewModel.serversCache.size
        val distinctProviders = mainViewModel.serversCache.map { it.profile.subscriptionId }.distinct().size
        binding.layoutServersHeader.tvServersSubtitle.text =
            getString(R.string.servers_count, serverCount) + " · " +
                getString(R.string.providers_count, maxOf(distinctProviders, 0))

        buildProtocolChips()

        val filtersActive = mainViewModel.keywordFilter.isNotEmpty() || mainViewModel.protocolFilter != null
        val showEmpty = serverCount == 0 && !filtersActive
        binding.layoutEmpty.root.isVisible = showEmpty
        binding.rvServers.isVisible = !showEmpty
    }

    /** Rebuilds protocol chips only when the available protocol set changes. */
    private fun buildProtocolChips() {
        val protocols = mainViewModel.availableProtocols()
        if (protocols == lastProtocolChipSet) return
        lastProtocolChipSet = protocols

        val group = binding.layoutServersHeader.chipGroupProtocol
        // Keep the permanent "Все" chip (index 0), drop the rest.
        while (group.childCount > 1) group.removeViewAt(group.childCount - 1)
        binding.layoutServersHeader.chipAll.tag = null

        val current = mainViewModel.protocolFilter
        val chipContext = android.view.ContextThemeWrapper(
            this, com.google.android.material.R.style.Widget_Material3_Chip_Filter
        )
        protocols.forEach { type ->
            val chip = Chip(chipContext).apply {
                text = type.name
                tag = type
                isCheckable = true
                isChecked = (type == current)
            }
            group.addView(chip)
        }
        if (current == null) binding.layoutServersHeader.chipAll.isChecked = true
    }

    /**
     * Binds the Home provider meta bar to the selected server's subscription (or the first
     * provider). Reuses the collapsible meta-bar layout shared with the old fragment.
     */
    private fun setupHomeMetaBar() {
        val meta = binding.layoutHomeMetaBar
        meta.btnCollapse.setOnClickListener { toggleMetaBody() }
        meta.btnRefresh.setOnClickListener { refreshHomeSub() }
        meta.btnPing.setOnClickListener {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllServers()
        }
        meta.btnPin.setOnClickListener { toggleHomePin() }
        meta.btnSupport.setOnClickListener { openSubUrl(MmkvManager.decodeSubscription(currentMetaSubId())?.supportUrl) }
        meta.btnTelegram.setOnClickListener { openSubUrl(MmkvManager.decodeSubscription(currentMetaSubId())?.supportUrl) }
        meta.btnWebsite.setOnClickListener { openSubUrl(MmkvManager.decodeSubscription(currentMetaSubId())?.webPageUrl) }
        bindHomeMetaBar()
    }

    private fun toggleMetaBody() {
        val body = binding.layoutHomeMetaBar.layoutMetaBody
        val collapse = body.isVisible
        body.isVisible = !collapse
        binding.layoutHomeMetaBar.btnCollapse.rotation = if (collapse) -90f else 0f
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
        meta.progressAction.visibility = android.view.View.VISIBLE
        meta.btnRefresh.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            launch(Dispatchers.Main) {
                if (result.configCount > 0) mainViewModel.reloadServerList()
                bindHomeMetaBar()
                meta.progressAction.visibility = android.view.View.GONE
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
     * Repaints the meta bar from persisted subscription metadata (moved from GroupServerFragment).
     */
    private fun bindMetaBar(sub: SubscriptionItem?) {
        val meta = binding.layoutHomeMetaBar
        if (sub == null) {
            meta.root.visibility = android.view.View.GONE
            return
        }
        meta.root.visibility = android.view.View.VISIBLE
        meta.tvSubTitle.text = metaTitle(sub)

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
        meta.btnWebsite.visibility = if (sub.webPageUrl.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        // Compact Telegram shortcut in the collapsed header, shown only when a support URL exists.
        meta.btnTelegram.visibility = if (sub.supportUrl.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE

        if (!sub.hasUserInfo) {
            meta.layoutTraffic.visibility = android.view.View.GONE
            return
        }
        meta.layoutTraffic.visibility = android.view.View.VISIBLE

        val variantColor = MaterialColors.getColor(meta.tvTraffic, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val redColor = ContextCompat.getColor(this, R.color.colorPingRed)
        val greenColor = ContextCompat.getColor(this, R.color.colorPing)

        if (sub.isUnlimited) {
            meta.tvTraffic.text = getString(R.string.sub_traffic_unlimited, sub.usedTraffic.toTrafficString())
            meta.tvTraffic.setTextColor(variantColor)
            meta.progressTraffic.visibility = android.view.View.GONE
        } else {
            val near = sub.trafficFraction >= 0.9f
            meta.tvTraffic.text = getString(
                R.string.sub_traffic_used,
                sub.usedTraffic.toTrafficString(),
                sub.totalTraffic.toTrafficString()
            )
            meta.tvTraffic.setTextColor(if (near) redColor else variantColor)
            meta.progressTraffic.visibility = android.view.View.VISIBLE
            meta.progressTraffic.setProgressCompat((sub.trafficFraction * 1000).toInt(), false)
            meta.progressTraffic.setIndicatorColor(if (near) redColor else greenColor)
        }

        if (sub.hasExpiry) {
            meta.tvExpiry.visibility = android.view.View.VISIBLE
            if (sub.isExpired) {
                meta.tvExpiry.text = getString(R.string.sub_expired)
                meta.tvExpiry.setTextColor(redColor)
            } else {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sub.expire * 1000))
                val daysLeft = ((sub.expire - System.currentTimeMillis() / 1000) / 86400).toInt()
                meta.tvExpiry.text = if (daysLeft in 0..999) {
                    getString(R.string.sub_days_left, getString(R.string.sub_expires, date), daysLeft)
                } else {
                    getString(R.string.sub_expires, date)
                }
                meta.tvExpiry.setTextColor(variantColor)
            }
        } else {
            meta.tvExpiry.visibility = android.view.View.GONE
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
                    else -> toast("else")
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
            // Stop: no "connecting" visual, the isRunning observer will settle the idle state.
            CoreServiceManager.stopVService(this)
        } else {
            // Start: show the subtle blue "connecting" state (pulsing ring), never a bright fill.
            applyRunningState(isLoading = true, isRunning = false)
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

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // service not running: keep existing no-op (could show a message if desired)
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

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
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
        // Connecting: blue pulsing ring/glow + blue shield, "Подключение…" (no bright fill).
        if (isLoading) {
            val active = themeColor(R.attr.connectActiveColor)
            startGlowPulse()
            binding.imgConnect.setColorFilter(active)
            binding.tvConnectionStatus.setTextColor(active)
            binding.tvConnectionStatus.text = getString(R.string.connection_connecting)
            return
        }

        if (isRunning) {
            // Connected: green shield/glow/label, steady (no pulse).
            val connected = themeColor(R.attr.connectedColor)
            stopGlowPulse()
            binding.viewConnectGlow.visibility = android.view.View.VISIBLE
            binding.imgConnect.setColorFilter(connected)
            binding.tvConnectionStatus.setTextColor(connected)
            binding.cardConnect.contentDescription = getString(R.string.action_stop_service)
            binding.tvConnectionStatus.text = getString(R.string.connection_connected)
            binding.layoutServerInfo.isFocusable = true
            startConnectionTimer()
        } else {
            // Idle: neutral shield, no glow.
            stopGlowPulse()
            binding.viewConnectGlow.visibility = android.view.View.INVISIBLE
            binding.imgConnect.setColorFilter(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            binding.tvConnectionStatus.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            binding.cardConnect.contentDescription = getString(R.string.tasker_start_service)
            binding.tvConnectionStatus.text = getString(R.string.connection_not_connected)
            binding.layoutServerInfo.isFocusable = false
            setTestState(getString(R.string.connection_test_pending))
            stopConnectionTimer()
            binding.tvDownloadSpeed.text = getString(R.string.speed_zero)
            binding.tvUploadSpeed.text = getString(R.string.speed_zero)
        }
        updateSelectedServer()
    }

    /**
     * Resolves a themed color attribute (respects the active blue/mono overlay).
     */
    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.cardConnect, attr)

    /**
     * Incy-style press feedback on the connect button: a quick scale-down followed by a
     * gentle overshoot back to full size. Purely cosmetic, no state change.
     */
    private fun animateConnectPress() {
        binding.cardConnect.animate().cancel()
        binding.cardConnect.animate()
            .scaleX(0.92f).scaleY(0.92f)
            .setDuration(90)
            .withEndAction {
                binding.cardConnect.animate()
                    .scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator())
                    .setDuration(220)
                    .start()
            }
            .start()
    }

    /**
     * Starts an infinite breathing pulse on the glow ring, used for the "connecting" state.
     */
    private fun startGlowPulse() {
        stopGlowPulse()
        val glow = binding.viewConnectGlow
        glow.visibility = View.VISIBLE
        val animators = listOf(
            ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.88f, 1.10f),
            ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.88f, 1.10f),
            ObjectAnimator.ofFloat(glow, View.ALPHA, 0.4f, 1f),
        ).onEach {
            it.duration = 750
            it.repeatCount = ValueAnimator.INFINITE
            it.repeatMode = ValueAnimator.REVERSE
        }
        glowPulse = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    /** Cancels the connecting pulse and resets the glow transform. */
    private fun stopGlowPulse() {
        glowPulse?.cancel()
        glowPulse = null
        binding.viewConnectGlow.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
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

    private fun updateSelectedServer() {
        val guid = MmkvManager.getSelectServer()
        val remarks = guid?.let { MmkvManager.decodeServerConfig(it)?.remarks }
        binding.tvSelectedServer.text = remarks?.takeIf { it.isNotBlank() } ?: getString(R.string.title_file_chooser)
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

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_proxy_chain -> {
            importManually(EConfigType.PROXYCHAIN.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllServers()
            true
        }

        R.id.real_ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }

        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.locate_selected_config -> {
            locateSelectedServer()
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


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.telegram_login -> {
                // Login is OPTIONAL: only offer it when a backend is configured.
                if (BackendConfig.isConfigured()) {
                    requestActivityLauncher.launch(Intent(this, LoginActivity::class.java))
                } else {
                    toast(R.string.auth_not_configured)
                }
            }
            R.id.sub_setting -> requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
            R.id.per_app_proxy_settings -> requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
            R.id.routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.user_asset_setting -> requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
            R.id.settings -> requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.backup_restore -> requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.removeCallbacks(healthCheckRunnable)
        timerHandler.removeCallbacks(memoryRunnable)
        stopGlowPulse()
        super.onDestroy()
    }
}