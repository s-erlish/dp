package com.v2ray.ang.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentSettingsTabBinding
import com.v2ray.ang.enums.PingMethod
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.tv.TvReceiveActivity
import com.v2ray.ang.tv.TvSendActivity
import com.v2ray.ang.util.LogUtil

/**
 * The Настройки tab: the custom Incy settings screen that replaced the old navigation drawer.
 *
 * Every row lives here — the pickers, the toggles and the launches into the sub-screens (per-app
 * proxy, routing, assets, «Дополнительно», backup, logs, about, …). Toggles and pickers read and
 * write the same MMKV keys the legacy `SettingsActivity` preference tree uses, so a value changed
 * on either surface is the same value. Switches are non-focusable in XML, so the whole row drives
 * them — there are no CheckedChange listeners, which is what keeps [bindSettingsState] from
 * feeding a change back into itself.
 *
 * What the shell still owns: recreating the activity for a theme/language change, restarting a
 * running tunnel after a core-config change, and the result launcher that applies both when a
 * sub-screen returns ([MainHost.launchSettingsScreen]). Nothing here casts to `MainActivity`.
 *
 * The tab's fragment is added the first time the tab is opened and then only hidden and shown, so
 * the view can outlive a bind; [onResume] therefore re-reads the persisted state through
 * [isBindingInitialized], which is what keeps a value changed in a sub-screen (or in another app,
 * as with «Always-on VPN») from being left stale on a tab that is never recreated.
 */
class SettingsTabFragment : BaseFragment<FragmentSettingsTabBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsTabBinding =
        FragmentSettingsTabBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSettings()
    }

    /**
     * Re-reads every persisted value each time the tab comes back to the foreground: a sub-screen,
     * the system VPN settings or another entry point may have changed one while this view was up
     * but not in front. Hidden tabs stay RESUMED, so this also runs for a tab that is attached but
     * not on screen — which is exactly what makes it correct the moment it is shown again.
     */
    override fun onResume() {
        super.onResume()
        bindSettingsState()
    }

    // Context-scoped helpers so the ported (Context.toast) call sites work from a Fragment. They
    // take a STRING ID and never built text: NoticePolicy recognises a message by its id, and a
    // CharSequence is by definition something it cannot vouch for.
    private fun toast(message: Int) = requireContext().toast(message)
    private fun toastError(message: Int) = requireContext().toastError(message)

    /**
     * Wires every row. Click handlers only — nothing here reads state, so the wiring runs once per
     * view and [bindSettingsState] stays the single place that paints values.
     */
    private fun setupSettings() {
        val s = binding

        // ПОДКЛЮЧЕНИЕ
        s.rowMode.setOnClickListener { pickMode() }
        s.rowPerApp.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), PerAppProxyActivity::class.java))
        }
        s.rowBypassLan.setOnClickListener { toggleBypassLan() }
        s.rowIpv6.setOnClickListener { toggleIpv6() }
        s.rowDns.setOnClickListener { editDns() }
        s.rowPingMethod.setOnClickListener { pickPingMethod() }
        // «Локальный прокси» пишет ключи, которые читает конфиг ядра (инбаунды socks/http, их
        // порт, UDP, системный HTTP-прокси, доступ по сети). Открывать его обычным
        // startActivity нельзя: флаг SettingsChangeManager.restartService, который экран
        // выставляет, потребляет только launchSettingsScreen — иначе выключенный локальный
        // прокси остаётся поднятым до следующего ручного переподключения.
        s.rowLocalProxy.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), LocalProxyActivity::class.java))
        }
        s.rowAlwaysOn.setOnClickListener { openAlwaysOnSettings() }

        // ОБХОД БЛОКИРОВОК
        s.rowMux.setOnClickListener { toggleMux() }
        s.rowMuxConcurrency.setOnClickListener { editMuxConcurrency() }
        s.rowFragment.setOnClickListener { toggleFragment() }

        // ИНТЕРФЕЙС
        s.rowAppearance.setOnClickListener { pickAppearance() }
        s.rowLanguage.setOnClickListener { pickLanguage() }
        s.rowBoot.setOnClickListener { toggleStartOnBoot() }

        // ПОДПИСКА
        // «Список подписок» (SubSettingActivity) и «Другие способы добавления»
        // (MainHost.showAdvancedAddMethods) убраны отсюда по прямому указанию владельца
        // (2026-08-02). Что при этом стало недостижимо — записано в комментарии на их месте в
        // fragment_settings_tab.xml; ни один экран и ни одна функция не удалены.
        s.rowSubAutoUpdate.setOnClickListener { pickSubAutoUpdate() }
        s.rowRouting.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), RoutingSettingActivity::class.java))
        }
        s.rowAssets.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), UserAssetActivity::class.java))
        }
        // «Настройки подписок» меняет порядок списка серверов и просит оболочку перестроить его
        // (SettingsChangeManager.setupGroupTab). Этот флаг тоже потребляет только
        // launchSettingsScreen, поэтому через startActivity новый порядок не доезжал до
        // Главной до следующего перезапуска.
        s.rowProvider.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), ProviderSettingsActivity::class.java))
        }

        // УСТРОЙСТВА
        s.rowTvSend.setOnClickListener { startActivity(Intent(requireContext(), TvSendActivity::class.java)) }
        // "Принять подписку" only makes sense on an Android TV device.
        val isTv = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        s.rowTvReceive.isVisible = isTv
        s.dividerTvReceive.isVisible = isTv
        s.rowTvReceive.setOnClickListener { startActivity(Intent(requireContext(), TvReceiveActivity::class.java)) }

        // О ПРИЛОЖЕНИИ
        // CheckUpdateActivity and LogcatActivity were declared in the manifest and referenced from
        // nowhere; a screen with no launch site is not a feature, so each one keeps its row here.
        // «Дополнительно» (SettingsActivity) was removed by the owner on 2026-08-02 — see
        // fragment_settings_tab.xml for the record of what that takes off the map.
        s.rowLogs.setOnClickListener { startActivity(Intent(requireContext(), LogcatActivity::class.java)) }
        s.rowCheckUpdate.setOnClickListener { startActivity(Intent(requireContext(), CheckUpdateActivity::class.java)) }
        s.rowAbout.setOnClickListener { startActivity(Intent(requireContext(), AboutActivity::class.java)) }
        s.rowUrlScheme.setOnClickListener { startActivity(Intent(requireContext(), UrlSchemeListActivity::class.java)) }
        s.rowBackup.setOnClickListener {
            mainHost.launchSettingsScreen(Intent(requireContext(), BackupActivity::class.java))
        }
        s.valueAbout.text = BuildConfig.VERSION_NAME

        bindSettingsState()
    }

    /** Reflects all persisted settings values/toggle states into the settings tab. */
    private fun bindSettingsState() {
        if (!isBindingInitialized) return
        val s = binding

        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN)
        val proxySharing = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false)
        s.valueMode.text = getString(
            when {
                mode != AppConfig.VPN -> R.string.settings_mode_value_proxy      // Proxy
                proxySharing -> R.string.settings_mode_value_tun_proxy           // TUN + Proxy
                else -> R.string.settings_mode_value_tun                         // TUN
            }
        )

        val perApp = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        s.valuePerApp.text = getString(if (perApp) R.string.settings_value_on else R.string.settings_value_off)

        s.valueDns.text = dnsLabel(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN).orEmpty())
        s.valuePingMethod.text = getString(pingMethodLabelRes(SettingsManager.getPingMethod()))
        s.valueMuxConcurrency.text = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8")

        val langEntries = resources.getStringArray(R.array.language_select)
        val langValues = resources.getStringArray(R.array.language_select_value)
        val curLang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, langValues.firstOrNull() ?: "auto").orEmpty()
        val li = langValues.indexOf(curLang).coerceAtLeast(0)
        s.valueLanguage.text = langEntries.getOrElse(li) { langEntries.firstOrNull().orEmpty() }

        s.valueSubAutoUpdate.text = currentSubAutoUpdateLabel()

        s.switchBypassLan.isChecked = isBypassLanOn()
        s.switchIpv6.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, false)

        val muxOn = MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
        s.switchMux.isChecked = muxOn
        s.rowMuxConcurrency.isVisible = muxOn
        s.dividerConcurrency.isVisible = muxOn

        s.switchFragment.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
        s.valueAppearance.text = getString(
            when (currentAppearanceIndex()) {
                0 -> R.string.settings_appearance_light
                2 -> R.string.settings_appearance_mono
                else -> R.string.settings_appearance_dark
            }
        )
        s.switchBoot.isChecked = MmkvManager.decodeStartOnBoot()
    }

    private fun isBypassLanOn(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, "1") != "2"

    private fun isMonoOn(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE) == BaseActivity.THEME_MONO

    /** Restart the tunnel so a changed core-config setting takes effect immediately. */
    private fun restartIfRunning() {
        if (mainViewModel.isRunning.value == true) mainHost.restartConnection()
    }

    /**
     * Three connection modes, all expressed with existing prefs (core config untouched). The
     * names and their order are the owner's: «TUN», «Proxy», «TUN + Proxy», and the row value
     * uses the same three strings as the picker, so a mode never reads one way in the list and
     * another way in the dialog.
     *   0 TUN         = VPN(tun) mode, local-proxy sharing OFF
     *   1 Proxy       = proxy-only mode (isVpnMode() == false)
     *   2 TUN + Proxy = VPN(tun) mode, local-proxy sharing ON (PREF_PROXY_SHARING)
     */
    private fun pickMode() {
        val entries = arrayOf(
            getString(R.string.settings_mode_value_tun),
            getString(R.string.settings_mode_value_proxy),
            getString(R.string.settings_mode_value_tun_proxy),
        )
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN)
        val proxySharing = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false)
        val idx = when {
            mode != AppConfig.VPN -> 1
            proxySharing -> 2
            else -> 0
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_mode)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                when (which) {
                    0 -> { // TUN
                        MmkvManager.encodeSettings(AppConfig.PREF_MODE, AppConfig.VPN)
                        MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, false)
                    }
                    1 -> { // Proxy only
                        MmkvManager.encodeSettings(AppConfig.PREF_MODE, "Proxy only")
                    }
                    else -> { // TUN + Proxy
                        MmkvManager.encodeSettings(AppConfig.PREF_MODE, AppConfig.VPN)
                        MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, true)
                    }
                }
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
        AlertDialog.Builder(requireContext())
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
        binding.switchBypassLan.isChecked = on
        restartIfRunning()
    }

    private fun toggleIpv6() {
        val enabled = !MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_IPV6_ENABLED, enabled)
        binding.switchIpv6.isChecked = enabled
        restartIfRunning()
    }

    /**
     * Deep-links into the system VPN settings screen where the user enables Android's
     * built-in "Always-on VPN" and "Block connections without VPN" (kill-switch). This is a
     * system-level toggle — the app only provides the shortcut and a one-line explainer.
     */
    private fun openAlwaysOnSettings() {
        toast(R.string.settings_always_on_hint)
        try {
            startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to open system VPN settings", e)
            // The device has no VPN settings screen to open — a dead end the row cannot show, so
            // it keeps a sentence. `toast_failure` («Ошибка») was upstream's word for every
            // outcome in the app and says nothing about this one.
            toastError(R.string.settings_always_on_unavailable)
        }
    }

    /** Maps a stored DNS value to its friendly preset name, or returns the raw value. */
    private fun dnsLabel(value: String): String {
        val names = resources.getStringArray(R.array.dns_preset_names)
        val values = resources.getStringArray(R.array.dns_preset_values)
        val i = values.indexOfFirst { it.isNotEmpty() && it == value }
        return if (i >= 0) names.getOrElse(i) { value } else value
    }

    /**
     * Single-choice DNS picker offering ready-made presets plus a "Свой…" option that
     * opens the free-text editor. Writes the selected server(s) into
     * [AppConfig.PREF_VPN_DNS] as a comma-separated list (same key/format as before).
     */
    private fun editDns() {
        val names = resources.getStringArray(R.array.dns_preset_names)
        val values = resources.getStringArray(R.array.dns_preset_values)
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN).orEmpty()
        // The last entry is the custom option (empty value); it's the fallback selection.
        val customIdx = values.size - 1
        val idx = values.indexOfFirst { it.isNotEmpty() && it == current }.let { if (it >= 0) it else customIdx }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_dns)
            .setSingleChoiceItems(names, idx) { dialog, which ->
                dialog.dismiss()
                if (which == customIdx) {
                    editDnsCustom(current)
                } else {
                    // Write both the tun DNS (PREF_VPN_DNS) and the proxied-lookup DNS
                    // (PREF_REMOTE_DNS, read by SettingsManager.getRemoteDnsServers), so
                    // picking a preset like Cloudflare applies to proxied resolution too.
                    MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, values[which])
                    MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, values[which])
                    bindSettingsState()
                    restartIfRunning()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Free-text DNS editor, reached via the "Свой…" preset option. */
    private fun editDnsCustom(current: String) {
        val input = EditText(requireContext()).apply {
            setText(current)
            setSingleLine()
            hint = getString(R.string.settings_dns_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_dns)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim().ifEmpty { AppConfig.DNS_VPN }
                MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, value)
                MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, value)
                bindSettingsState()
                restartIfRunning()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleMux() {
        val enabled = !MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_MUX_ENABLED, enabled)
        val s = binding
        s.switchMux.isChecked = enabled
        s.rowMuxConcurrency.isVisible = enabled
        s.dividerConcurrency.isVisible = enabled
        restartIfRunning()
    }

    private fun editMuxConcurrency() {
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8")
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current)
        }
        AlertDialog.Builder(requireContext())
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
        binding.switchFragment.isChecked = enabled
        restartIfRunning()
    }

    /**
     * Current "Оформление" selection as a picker index:
     *   0 = Светлая (light day theme, blue accent)
     *   1 = Тёмная (dark theme, blue accent)
     *   2 = Чёрно-белая (monochrome overlay over the current night mode)
     * Mono wins regardless of night mode; otherwise the light/dark split follows
     * PREF_UI_MODE_NIGHT ("1" = day, "2" = night; default is Incy dark).
     */
    private fun currentAppearanceIndex(): Int = when {
        isMonoOn() -> 2
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2") == "1" -> 0
        else -> 1
    }

    /**
     * "Оформление" picker. Incy is primarily dark, but light is a first-class choice:
     *   Светлая      -> MODE_NIGHT_NO  ("1") + blue accent  (day resources, dark bar icons)
     *   Тёмная       -> MODE_NIGHT_YES ("2") + blue accent  (night resources)
     *   Чёрно-белая  -> monochrome overlay, keeping the current night mode as-is.
     * Light/dark are applied via AppCompatDelegate (SettingsManager.setNightMode reads
     * PREF_UI_MODE_NIGHT and calls setDefaultNightMode); the mono overlay is applied in
     * BaseActivity.onCreate. Either path is picked up with recreate().
     */
    private fun pickAppearance() {
        val entries = arrayOf(
            getString(R.string.settings_appearance_light),
            getString(R.string.settings_appearance_dark),
            getString(R.string.settings_appearance_mono),
        )
        val idx = currentAppearanceIndex()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_appearance)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                dialog.dismiss()
                if (which == idx) return@setSingleChoiceItems
                when (which) {
                    0 -> {
                        // Светлая: light day theme + blue accent.
                        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, "1")
                        MmkvManager.encodeSettings(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE)
                        SettingsManager.setNightMode()
                    }
                    1 -> {
                        // Тёмная: dark theme + blue accent.
                        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, "2")
                        MmkvManager.encodeSettings(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_BLUE)
                        SettingsManager.setNightMode()
                    }
                    else -> {
                        // Чёрно-белая: mono overlay, keep the current night mode.
                        MmkvManager.encodeSettings(AppConfig.PREF_COLOR_THEME, BaseActivity.THEME_MONO)
                    }
                }
                // The night mode and mono overlay are applied at activity creation, so recreate.
                requireActivity().recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickLanguage() {
        val entries = resources.getStringArray(R.array.language_select)
        val values = resources.getStringArray(R.array.language_select_value)
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, values.firstOrNull() ?: "auto").orEmpty()
        val idx = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, values[which])
                dialog.dismiss()
                // Locale is applied via BaseActivity.attachBaseContext on recreate.
                requireActivity().recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleStartOnBoot() {
        val enabled = !MmkvManager.decodeStartOnBoot()
        MmkvManager.encodeStartOnBoot(enabled)
        binding.switchBoot.isChecked = enabled
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

    /**
     * Row value: the interval of any auto-updating подписка, «Выкл» when none of them updates, or
     * «Нет подписок» when there is nothing to update at all.
     *
     * The last case matters: with no подписка stored the row used to read «Выкл», which invites a
     * tap that can only answer with a toast. It now states the reason, in the same words
     * «Настройки подписок» uses for the same state, so the two screens agree.
     */
    private fun currentSubAutoUpdateLabel(): String {
        val subs = MmkvManager.decodeSubscriptions()
        if (subs.isEmpty()) return getString(R.string.settings_sub_auto_update_none)
        val active = subs.firstOrNull { it.subscription.autoUpdate }
            ?: return getString(R.string.settings_value_off)
        return subAutoUpdateLabel(active.subscription.updateInterval)
    }

    /**
     * Global subscription auto-update picker. There is no dedicated global pref key, so the
     * choice is applied across every stored subscription: `SubscriptionItem.autoUpdate` and
     * `SubscriptionItem.updateInterval` (in minutes) are written for each one, then the
     * WorkManager scheduler is re-synced via [SubscriptionUpdater.sync] so the new interval
     * takes effect immediately. Minutes are stored so the home meta-bar can read the value.
     */
    private fun pickSubAutoUpdate() {
        // With no subscriptions the interval has nothing to apply to, so the picker would
        // silently no-op. Tell the user to add one first instead.
        if (MmkvManager.decodeSubscriptions().isEmpty()) {
            toast(R.string.settings_sub_auto_update_empty)
            return
        }
        val entries = subAutoUpdateValues.map { subAutoUpdateLabel(it) }.toTypedArray()
        val active = MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.autoUpdate }
        val currentMinutes = if (active == null) 0L else active.subscription.updateInterval
        val idx = subAutoUpdateValues.indexOf(currentMinutes).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
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
}
