package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ActivityRoutingSettingBinding
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.ui.component.EmptyStateBinder
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A-20 `settings/routing` - «Маршрутизация». H3 header, R1 rhythm.
 *
 * The functional change is that **five actions came out of hiding**. «Добавить правило», «Готовые
 * наборы», «Импорт из буфера», «Импорт из QR-кода» and «Экспорт» lived in an `onCreateOptionsMenu`
 * overflow - an affordance no other screen in this app has, on an ActionBar that A-38 removes. Four
 * of them are now rows in a named group and the fifth is the header's one trailing action. Not one
 * of them was dropped.
 *
 * «Стратегия доменов» stops being an `android.app.AlertDialog.setItems` picker (§7.4 bans the
 * single-choice dialog outright) and becomes the cycle-in-place affordance: three values, an unfold
 * glyph, and the current one printed in the row.
 */
class RoutingSettingActivity : HelperBaseActivity() {

    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private lateinit var adapter: RoutingSettingRecyclerAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    private val domainStrategies: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_domain_strategy)
    }
    private val presetRulesets: Array<out String> by lazy {
        resources.getStringArray(R.array.preset_rulesets)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.routing_title),
            activity = this,
            actionIcon = R.drawable.ic_add_24dp,
            actionDescription = getString(R.string.routing_action_add),
            onAction = { addRule() },
        )
        ToolbarBinder.attachTo(binding.toolbar.root, binding.mainContent)

        adapter = RoutingSettingRecyclerAdapter(viewModel, ActivityAdapterListener())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter)).also {
            it.attachToRecyclerView(binding.recyclerView)
        }

        bindDomainStrategyRow()
        bindActionRows()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    // ------------------------------------------------------------ strategy

    private fun currentDomainStrategy(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
            ?: domainStrategies.first()

    private fun bindDomainStrategyRow() {
        val current = currentDomainStrategy()
        RowBinder.bind(
            root = binding.rowDomainStrategy.root,
            title = getString(R.string.routing_domain_strategy),
            glyph = R.drawable.ic_globe_24dp,
            value = current,
            // Three values: the affordance grammar's cycle-in-place case. The glyph promises the
            // value changes right here, and it does - no dialog, no screen.
            trailing = RowBinder.Trailing.Glyph(
                icon = R.drawable.ic_arrow_drop_down,
                contentDescription = getString(R.string.routing_strategy_cd),
            ),
            onClick = {
                val next = domainStrategies[
                    (domainStrategies.indexOf(current).coerceAtLeast(0) + 1) % domainStrategies.size
                ]
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, next)
                bindDomainStrategyRow()
            },
        )
    }

    // ------------------------------------------------------------- actions

    private fun bindActionRows() {
        RowBinder.bind(
            root = binding.rowPresets.root,
            title = getString(R.string.routing_action_presets),
            glyph = R.drawable.ic_routing_24dp,
            trailing = RowBinder.Trailing.None,
            onClick = { importPredefined() },
        )
        RowBinder.bind(
            root = binding.rowImportClipboard.root,
            title = getString(R.string.routing_action_import_clipboard),
            glyph = R.drawable.ic_dl_copy,
            trailing = RowBinder.Trailing.None,
            onClick = { importFromClipboard() },
        )
        RowBinder.bind(
            root = binding.rowImportQrcode.root,
            title = getString(R.string.routing_action_import_qrcode),
            glyph = R.drawable.ic_scan_24dp,
            trailing = RowBinder.Trailing.None,
            onClick = { importQRcode() },
        )
        RowBinder.bind(
            root = binding.rowExport.root,
            title = getString(R.string.routing_action_export),
            glyph = R.drawable.ic_share_24dp,
            trailing = RowBinder.Trailing.None,
            onClick = { export2Clipboard() },
        )
    }

    private fun addRule() {
        SubPage.open(this, Intent(this, RoutingEditActivity::class.java))
    }

    /**
     * Every import REPLACES the rule list, so every import asks first. This is one of the few
     * confirmations §7.5 keeps: the change is not undoable and it silently rewrites work the user
     * did by hand. The buttons carry verbs, not «OK».
     */
    private fun confirmReplace(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(R.string.routing_import_confirm)
            .setPositiveButton(R.string.editor_confirm_replace) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.editor_cancel, null)
            .show()
    }

    private fun importPredefined() {
        EditorActionsSheet(this, getString(R.string.routing_action_presets)).apply {
            presetRulesets.forEachIndexed { index, name ->
                action(label = name, glyph = R.drawable.ic_routing_24dp) {
                    confirmReplace {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.resetRoutingRulesetsFromPresets(
                                this@RoutingSettingActivity,
                                index,
                            )
                            withContext(Dispatchers.Main) {
                                refreshData()
                                toastSuccess(R.string.routing_import_done)
                            }
                        }
                    }
                }
            }
        }.show()
    }

    private fun importFromClipboard() = confirmReplace {
        val clipboard = runCatching { Utils.getClipboard(this) }.getOrElse {
            LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", it)
            toastError(R.string.routing_import_failed)
            return@confirmReplace
        }
        applyRulesets(clipboard)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) confirmReplace { applyRulesets(scanResult) }
        }
    }

    private fun applyRulesets(payload: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = SettingsManager.resetRoutingRulesets(payload)
            withContext(Dispatchers.Main) {
                refreshData()
                if (ok) toastSuccess(R.string.routing_import_done) else toastError(R.string.routing_import_failed)
            }
        }
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.routing_export_empty)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.editor_copied)
        }
    }

    // -------------------------------------------------------------- states

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()

        val isEmpty = viewModel.getAll().isEmpty()
        binding.recyclerView.isVisible = !isEmpty
        binding.labelRules.isVisible = !isEmpty
        if (isEmpty) {
            EmptyStateBinder.bind(
                root = binding.emptyState.root,
                glyph = R.drawable.ic_routing_24dp,
                title = getString(R.string.routing_empty_title),
                line = getString(R.string.routing_empty_line),
                actionLabel = getString(R.string.routing_action_add),
                emphasis = EmptyStateBinder.Emphasis.PRIMARY,
                onAction = { addRule() },
            )
        } else {
            EmptyStateBinder.hide(binding.emptyState.root)
        }
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            SubPage.open(
                this@RoutingSettingActivity,
                Intent(this@RoutingSettingActivity, RoutingEditActivity::class.java)
                    .putExtra("position", position),
            )
        }

        override fun onRemove(guid: String, position: Int) {}

        override fun onShare(url: String) {}

        override fun onRefreshData() {
            refreshData()
        }
    }
}
