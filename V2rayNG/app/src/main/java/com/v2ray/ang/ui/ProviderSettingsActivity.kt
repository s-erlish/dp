package com.v2ray.ang.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.databinding.ActivityProviderSettingsBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater

/**
 * "Настройки провайдеров" — provider/subscription settings screen (departament design).
 *
 * Groups four cards that mirror the Incy provider screen:
 *  1. ОБНОВЛЕНИЕ    — auto-update toggle + interval picker + update notification toggle.
 *  2. ПРИ ЗАПУСКЕ    — update-on-launch / ping-on-launch / ping-on-update toggles.
 *  3. СЕТЬ           — HWID toggle + subscription User-Agent editor.
 *  4. СПИСОК СЕРВЕРОВ — server list sort order (single choice).
 *
 * Wiring notes:
 *  - Auto-update + interval are applied to the app's existing per-subscription auto-update
 *    mechanism ([com.v2ray.ang.dto.entities.SubscriptionItem.autoUpdate] /
 *    [com.v2ray.ang.dto.entities.SubscriptionItem.updateInterval]) across every stored
 *    subscription, then rescheduled via [SubscriptionUpdater.sync]. This mirrors the global
 *    picker in MainActivity's settings tab — it changes real update behavior.
 *  - Every other row writes an [AppConfig] preference that the rest of the app reads: HWID and
 *    the User-Agent are consumed by the subscription fetch, the notification and the
 *    launch/update actions by [SubscriptionUpdater], the sort order by
 *    [SettingsManager.applyServerSortOrder]. Defaults live in [SettingsManager], not here, so
 *    the switch state and the behaviour can never drift apart.
 */
class ProviderSettingsActivity : BaseActivity() {

    private val binding by lazy { ActivityProviderSettingsBinding.inflate(layoutInflater) }

    companion object {
        // Screen-local: remembers the chosen interval even when no subscription is present yet.
        // The interval that actually schedules work lives on each SubscriptionItem.
        private const val PREF_UPDATE_INTERVAL = "pref_provider_update_interval"

        private const val DEFAULT_INTERVAL_MINUTES = 60L
    }

    /** Interval options (minutes) offered by the interval picker: 1/2/6/12/24 hours. */
    private val intervalValues = longArrayOf(60L, 120L, 360L, 720L, 1440L)

    /** Sort-order values persisted for the server list. */
    private val sortValues = arrayOf(
        AppConfig.SERVER_SORT_DEFAULT,
        AppConfig.SERVER_SORT_PING,
        AppConfig.SERVER_SORT_NAME
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.ps_title))

        // ОБНОВЛЕНИЕ
        binding.rowAutoUpdate.setOnClickListener { toggleAutoUpdate() }
        binding.rowInterval.setOnClickListener { pickInterval() }
        binding.rowNotify.setOnClickListener {
            toggleBool(AppConfig.PREF_SUB_NOTIFY_ON_UPDATE, binding.switchNotify, SettingsManager.isNotifyOnSubscriptionUpdate())
        }

        // ПРИ ЗАПУСКЕ
        binding.rowUpdateOnLaunch.setOnClickListener {
            toggleBool(AppConfig.PREF_SUB_UPDATE_ON_LAUNCH, binding.switchUpdateOnLaunch, SettingsManager.isUpdateSubscriptionOnLaunch())
        }
        binding.rowPingOnLaunch.setOnClickListener {
            toggleBool(AppConfig.PREF_PING_ON_LAUNCH, binding.switchPingOnLaunch, SettingsManager.isPingOnLaunch())
        }
        binding.rowPingOnUpdate.setOnClickListener {
            toggleBool(AppConfig.PREF_PING_ON_UPDATE, binding.switchPingOnUpdate, SettingsManager.isPingOnSubscriptionUpdate())
        }

        // СЕТЬ
        binding.rowSendHwid.setOnClickListener { toggleSendHwid() }
        binding.rowUserAgent.setOnClickListener { editUserAgent() }

        // СПИСОК СЕРВЕРОВ
        binding.rowSortOrder.setOnClickListener { pickSortOrder() }

        bindState()
    }

    /** Reflect all persisted values/toggle states into the rows. */
    private fun bindState() {
        binding.switchAutoUpdate.isChecked = isAutoUpdateOn()
        binding.valueInterval.text = intervalLabel(storedIntervalMinutes())
        binding.switchNotify.isChecked = SettingsManager.isNotifyOnSubscriptionUpdate()

        binding.switchUpdateOnLaunch.isChecked = SettingsManager.isUpdateSubscriptionOnLaunch()
        binding.switchPingOnLaunch.isChecked = SettingsManager.isPingOnLaunch()
        binding.switchPingOnUpdate.isChecked = SettingsManager.isPingOnSubscriptionUpdate()

        binding.switchSendHwid.isChecked = SettingsManager.isSendHwid()
        binding.valueUserAgent.text = currentUserAgent()

        binding.valueSortOrder.text = getString(sortLabelRes(currentSortOrder()))
    }

    // ---------------- ОБНОВЛЕНИЕ ----------------

    /** True if any stored subscription currently auto-updates. */
    private fun isAutoUpdateOn(): Boolean =
        MmkvManager.decodeSubscriptions().any { it.subscription.autoUpdate }

    private fun storedIntervalMinutes(): Long =
        MmkvManager.decodeSettingsString(PREF_UPDATE_INTERVAL, DEFAULT_INTERVAL_MINUTES.toString())
            ?.toLongOrNull() ?: DEFAULT_INTERVAL_MINUTES

    private fun intervalLabel(minutes: Long): String = when (minutes) {
        60L -> getString(R.string.ps_interval_1h)
        120L -> getString(R.string.ps_interval_2h)
        360L -> getString(R.string.ps_interval_6h)
        720L -> getString(R.string.ps_interval_12h)
        1440L -> getString(R.string.ps_interval_24h)
        else -> getString(R.string.ps_interval_1h)
    }

    /**
     * Enable/disable auto-update across every stored subscription, then reschedule the
     * WorkManager task so the change takes effect immediately.
     */
    private fun toggleAutoUpdate() {
        val enable = !isAutoUpdateOn()
        val interval = storedIntervalMinutes()
        MmkvManager.decodeSubscriptions().forEach { cache ->
            val item = cache.subscription
            item.autoUpdate = enable
            if (enable) item.updateInterval = interval
            MmkvManager.encodeSubscription(cache.guid, item)
        }
        SubscriptionUpdater.sync(forceReschedule = true)
        binding.switchAutoUpdate.isChecked = enable
    }

    /**
     * Single-choice interval picker. Persists the chosen interval locally and applies it to
     * every subscription (turning auto-update on), then reschedules the updater.
     */
    private fun pickInterval() {
        val entries = intervalValues.map { intervalLabel(it) }.toTypedArray()
        val idx = intervalValues.indexOf(storedIntervalMinutes()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.ps_interval)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                val minutes = intervalValues[which]
                MmkvManager.encodeSettings(PREF_UPDATE_INTERVAL, minutes.toString())
                MmkvManager.decodeSubscriptions().forEach { cache ->
                    val item = cache.subscription
                    item.autoUpdate = true
                    item.updateInterval = minutes
                    MmkvManager.encodeSubscription(cache.guid, item)
                }
                SubscriptionUpdater.sync(forceReschedule = true)
                bindState()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- СЕТЬ ----------------

    /** Toggle whether the device HWID is sent when refreshing a subscription. Real behavior. */
    private fun toggleSendHwid() {
        val enabled = !SettingsManager.isSendHwid()
        MmkvManager.encodeSettings(AppConfig.PREF_SEND_HWID, enabled)
        binding.switchSendHwid.isChecked = enabled
    }

    /**
     * The User-Agent subscription fetches will actually send: the global override when set,
     * otherwise the operator default the build ships with. Showing the effective value keeps the
     * row honest instead of advertising a string the app never sends.
     */
    private fun currentUserAgent(): String =
        SettingsManager.getSubscriptionUserAgent() ?: BackendConfig.subscriptionUserAgent

    private fun editUserAgent() {
        val input = EditText(this).apply {
            // Only the override is prefilled: confirming an untouched dialog must not freeze the
            // operator default into a permanent override that a later build could no longer change.
            // The row itself already shows the User-Agent that is actually sent.
            setText(SettingsManager.getSubscriptionUserAgent().orEmpty())
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.ps_user_agent_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ps_user_agent)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // An empty field clears the override rather than storing a blank User-Agent.
                MmkvManager.encodeSettings(AppConfig.PREF_SUB_USER_AGENT, input.text.toString().trim())
                bindState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- СПИСОК СЕРВЕРОВ ----------------

    private fun currentSortOrder(): String = SettingsManager.getServerSortOrder()

    private fun sortLabelRes(value: String): Int = when (value) {
        AppConfig.SERVER_SORT_PING -> R.string.ps_sort_ping
        AppConfig.SERVER_SORT_NAME -> R.string.ps_sort_name
        else -> R.string.ps_sort_default
    }

    private fun pickSortOrder() {
        val entries = sortValues.map { getString(sortLabelRes(it)) }.toTypedArray()
        val idx = sortValues.indexOf(currentSortOrder()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.ps_sort_order)
            .setSingleChoiceItems(entries, idx) { dialog, which ->
                MmkvManager.encodeSettings(AppConfig.PREF_SERVER_SORT_ORDER, sortValues[which])
                // Order lives in storage, so reorder now — the servers list renders what is stored
                // and never re-sorts on its own. It also holds its rows from before this screen was
                // opened, so ask it to rebuild; otherwise the new order only shows after a restart.
                SettingsManager.applyServerSortOrder()
                SettingsChangeManager.makeSetupGroupTab()
                bindState()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- helpers ----------------

    /** Flip a boolean pref and reflect it on its switch. [current] carries the default. */
    private fun toggleBool(
        key: String,
        switch: com.google.android.material.materialswitch.MaterialSwitch,
        current: Boolean
    ) {
        val enabled = !current
        MmkvManager.encodeSettings(key, enabled)
        switch.isChecked = enabled
    }
}
