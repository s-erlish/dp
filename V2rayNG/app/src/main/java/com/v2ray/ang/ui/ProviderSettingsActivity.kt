package com.v2ray.ang.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.BackendConfig
import com.v2ray.ang.databinding.ActivityProviderSettingsBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.component.SelectPopup
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.ui.component.onSingleClick
import com.v2ray.ang.ui.component.restoreChecked
import com.v2ray.ang.util.HttpUtil

/**
 * «Настройки подписок» — the settings that belong to the subscription feeds, not to one подписка.
 *
 * The word is the owner's: what this app used to call a «провайдер» is a **подписка** on every
 * surface, so the screen, its title and its copy say подписка.
 *
 * Groups four cards:
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
        /**
         * Fallback shown when nothing has an interval yet - the same default a fresh
         * [com.v2ray.ang.dto.entities.SubscriptionItem] carries.
         *
         * There is no screen-local key any more. This screen used to keep the chosen interval in a
         * private `pref_provider_update_interval`, which nothing else in the app read: the value
         * that actually schedules work is `SubscriptionItem.updateInterval`, and a second copy of it
         * could disagree with the scheduler the moment the interval was changed anywhere else. The
         * row reads the real one now.
         */
        private const val DEFAULT_INTERVAL_MINUTES = 60L

        /** 0.38 - the disabled alpha of the row grammar. */
        private const val ALPHA_DISABLED = 0.38f
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
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        // Handoff README §7: the sub-page lekalo draws the title at 24sp/700 UNDER a 44dp
        // back control, which activity_base's 16sp MaterialToolbar cannot do, so the header
        // is @layout/view_sub_header inside this screen's own layout. Nothing else moves —
        // the screen never used the base layout's progress bar.
        setContentView(binding.root)
        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.ps_title),
            activity = this,
        )
        ToolbarBinder.attachTo(binding.toolbar.root, binding.mainContent)

        // ОБНОВЛЕНИЕ
        binding.rowAutoUpdate.setOnClickListener { toggleAutoUpdate() }
        binding.rowInterval.onSingleClick { pickInterval() }
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
        binding.rowUserAgent.onSingleClick { editUserAgent() }

        // СПИСОК СЕРВЕРОВ
        // The three rows that OPEN something are guarded; a doubled tap on any of them stacked two
        // окошка выбора / two dialogs. The toggle rows above keep a raw listener on purpose: they
        // flip a stored boolean and repaint their own switch, so a doubled tap lands back exactly
        // where it started and there is nothing to duplicate.
        binding.rowSortOrder.onSingleClick { pickSortOrder() }

        bindState()
    }

    /**
     * A подписка can be added or deleted while this screen sits in the back stack (the list is two
     * taps away), and that changes whether the update rows can do anything at all. Re-read on the
     * way back rather than showing what was true when the screen opened.
     */
    override fun onResume() {
        super.onResume()
        bindState()
    }

    /** Reflect all persisted values/toggle states into the rows. */
    private fun bindState() {
        // With no подписка stored there is nothing for a schedule to apply to: toggling the switch
        // wrote to an empty list and looked like it had worked. The two rows say so instead.
        val hasSubscriptions = MmkvManager.decodeSubscriptions().isNotEmpty()
        setRowEnabled(binding.rowAutoUpdate, hasSubscriptions)
        setRowEnabled(binding.rowInterval, hasSubscriptions)
        binding.switchAutoUpdate.restoreChecked(hasSubscriptions && isAutoUpdateOn())
        binding.valueInterval.text = if (hasSubscriptions) {
            intervalLabel(storedIntervalMinutes())
        } else {
            getString(R.string.ps_no_subs_value)
        }
        binding.switchNotify.restoreChecked(SettingsManager.isNotifyOnSubscriptionUpdate())

        binding.switchUpdateOnLaunch.restoreChecked(SettingsManager.isUpdateSubscriptionOnLaunch())
        binding.switchPingOnLaunch.restoreChecked(SettingsManager.isPingOnLaunch())
        binding.switchPingOnUpdate.restoreChecked(SettingsManager.isPingOnSubscriptionUpdate())

        binding.switchSendHwid.restoreChecked(SettingsManager.isSendHwid())
        binding.valueUserAgent.text = currentUserAgent()

        binding.valueSortOrder.text = getString(sortLabelRes(currentSortOrder()))
    }

    // ---------------- ОБНОВЛЕНИЕ ----------------

    /** True if any stored subscription currently auto-updates. */
    private fun isAutoUpdateOn(): Boolean =
        MmkvManager.decodeSubscriptions().any { it.subscription.autoUpdate }

    /**
     * The interval that is actually scheduled: the one on the first auto-updating подписка, or the
     * one the first подписка carries, or the shipped default when there is nothing to read.
     */
    private fun storedIntervalMinutes(): Long {
        val subs = MmkvManager.decodeSubscriptions()
        val item = subs.firstOrNull { it.subscription.autoUpdate }?.subscription
            ?: subs.firstOrNull()?.subscription
            ?: return DEFAULT_INTERVAL_MINUTES
        return item.updateInterval.takeIf { it > 0L } ?: DEFAULT_INTERVAL_MINUTES
    }

    /**
     * A подписка can carry an interval that is not one of the five offered here - the form in
     * `SubEditActivity` takes any number of minutes. Such a value is shown as it is; the old `else`
     * branch fell back to «1 ч» and so reported an interval the scheduler was not using.
     */
    private fun intervalLabel(minutes: Long): String = when (minutes) {
        60L -> getString(R.string.ps_interval_1h)
        120L -> getString(R.string.ps_interval_2h)
        360L -> getString(R.string.ps_interval_6h)
        720L -> getString(R.string.ps_interval_12h)
        1440L -> getString(R.string.ps_interval_24h)
        else -> getString(R.string.settings_sub_auto_update_minutes, minutes)
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
        val entries = intervalValues.map { intervalLabel(it) }
        val idx = intervalValues.indexOf(storedIntervalMinutes()).coerceAtLeast(0)
        SelectPopup.show(
            anchor = binding.rowInterval,
            options = entries,
            selectedIndex = idx,
            widthRes = R.dimen.select_popup_w_interval,
            valueView = binding.valueInterval,
            caret = binding.caretInterval,
        ) { which ->
            val minutes = intervalValues[which]
            MmkvManager.decodeSubscriptions().forEach { cache ->
                val item = cache.subscription
                item.autoUpdate = true
                item.updateInterval = minutes
                MmkvManager.encodeSubscription(cache.guid, item)
            }
            SubscriptionUpdater.sync(forceReschedule = true)
            bindState()
        }
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
     * otherwise the operator default the build ships with — put through the same resolution the
     * fetch uses, so a value the fetch would replace (one an older build stored before this screen
     * validated its input) is not advertised here as if it were sent.
     */
    private fun currentUserAgent(): String = HttpUtil.resolveSubscriptionUserAgent(
        SettingsManager.getSubscriptionUserAgent() ?: BackendConfig.subscriptionUserAgent
    )

    /**
     * Edits the global User-Agent override.
     *
     * The value is validated HERE, on entry, and a rejected one is never stored: a User-Agent that
     * cannot travel in an HTTP header is silently replaced at fetch time, so storing it would put
     * a string in this row that the app will never send — and the row exists to say what is sent.
     */
    private fun editUserAgent() {
        val field = TextInputLayout(this).apply {
            hint = getString(R.string.ps_user_agent_hint)
            isErrorEnabled = true
        }
        val input = TextInputEditText(field.context).apply {
            // Only the override is prefilled: confirming an untouched dialog must not freeze the
            // operator default into a permanent override that a later build could no longer change.
            // The row itself already shows the User-Agent that is actually sent.
            setText(SettingsManager.getSubscriptionUserAgent().orEmpty())
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        field.addView(input)
        val pad = resources.getDimensionPixelSize(R.dimen.space_16)
        val container = FrameLayout(this).apply {
            setPadding(pad, pad, pad, 0)
            addView(field)
        }

        // Validate on blur, and again on confirm; clear the error as soon as the user edits.
        input.doAfterTextChanged { field.error = null }
        input.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) showUserAgentError(field, input) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.ps_user_agent)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            // Own the click so an invalid value keeps the dialog open with the error visible,
            // instead of dismissing and quietly discarding what was typed.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (showUserAgentError(field, input)) {
                    input.requestFocus()
                    return@setOnClickListener
                }
                // An empty field clears the override rather than storing a blank User-Agent.
                MmkvManager.encodeSettings(AppConfig.PREF_SUB_USER_AGENT, input.text.toString().trim())
                bindState()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Shows/clears the inline error. @return true when the value is rejected. */
    private fun showUserAgentError(field: TextInputLayout, input: TextInputEditText): Boolean {
        val value = input.text.toString().trim()
        val invalid = value.isNotEmpty() && !HttpUtil.isHeaderSafe(value)
        field.error = if (invalid) getString(R.string.ps_user_agent_error) else null
        return invalid
    }

    // ---------------- СПИСОК СЕРВЕРОВ ----------------

    private fun currentSortOrder(): String = SettingsManager.getServerSortOrder()

    private fun sortLabelRes(value: String): Int = when (value) {
        AppConfig.SERVER_SORT_PING -> R.string.ps_sort_ping
        AppConfig.SERVER_SORT_NAME -> R.string.ps_sort_name
        else -> R.string.ps_sort_default
    }

    private fun pickSortOrder() {
        val entries = sortValues.map { getString(sortLabelRes(it)) }
        val idx = sortValues.indexOf(currentSortOrder()).coerceAtLeast(0)
        SelectPopup.show(
            anchor = binding.rowSortOrder,
            options = entries,
            selectedIndex = idx,
            valueView = binding.valueSortOrder,
            caret = binding.caretSortOrder,
        ) { which ->
            MmkvManager.encodeSettings(AppConfig.PREF_SERVER_SORT_ORDER, sortValues[which])
            // Order lives in storage, so reorder now — the servers list renders what is stored
            // and never re-sorts on its own. It also holds its rows from before this screen was
            // opened, so ask it to rebuild; otherwise the new order only shows after a restart.
            SettingsManager.applyServerSortOrder()
            SettingsChangeManager.makeSetupGroupTab()
            bindState()
        }
    }

    // ---------------- helpers ----------------

    /**
     * A row that cannot do anything is not presented as if it could: it stops taking taps and
     * drops to the disabled alpha of the row grammar, and its value states the reason.
     */
    private fun setRowEnabled(row: View, enabled: Boolean) {
        row.isEnabled = enabled
        row.isClickable = enabled
        row.isFocusable = enabled
        row.alpha = if (enabled) 1f else ALPHA_DISABLED
    }

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
