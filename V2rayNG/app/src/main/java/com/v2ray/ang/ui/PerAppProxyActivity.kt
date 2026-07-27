package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBypassListBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.component.EmptyStateBinder
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SkeletonBinder
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.PerAppProxyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * A-18 `settings/perapp` - «Прокси по приложениям». H3 header, R1 rhythm.
 *
 * The two audit findings this screen was called out for:
 *
 * - **The boolean was a bare checkbox in a card grid.** Every app is now a `Row.Toggle` in one
 *   grouped list, so the whole row toggles and the switch matches every other boolean in the app.
 * - **There was no grouping and no search on screen.** Search lived in an ActionBar `SearchView`
 *   that A-38 deletes along with the ActionBar; it is now a real 56dp field that filters in place.
 *   The list is grouped «Ваши приложения» / «Системные приложения» instead of prefixing system apps
 *   with `** ` in Kotlin.
 *
 * And the states the screen simply did not have: a skeleton while the package manager resolves the
 * list (slow on a cold start, and the screen used to be blank for all of it), «Ничего не найдено»
 * for a search that matched nothing, and a load failure that offers «Повторить» instead of logging
 * and showing an empty screen.
 *
 * **The mode is one row, not two switches.** `PREF_PER_APP_PROXY` and `PREF_BYPASS_APPS` are two
 * booleans encoding three states, and the old screen showed them as two independent switches - one
 * of which was meaningless while the other was off, with the explanation hidden behind a second
 * tappable target that fired a toast. They are one `Row.Value` here, cycling «Все приложения» ->
 * «Только выбранные» -> «Кроме выбранных» in place, with the subtitle stating what the current mode
 * actually does. Both preferences keep their existing meaning and their existing keys.
 */
class PerAppProxyActivity : BaseActivity() {

    private val binding by lazy { ActivityBypassListBinding.inflate(layoutInflater) }

    private val viewModel: PerAppProxyViewModel by viewModels()
    private val adapter by lazy {
        PerAppProxyAdapter(
            isSelected = viewModel::contains,
            onToggle = { packageName ->
                viewModel.toggle(packageName)
                updateMeta()
            },
        )
    }

    private var appsAll: List<AppInfo> = emptyList()
    private var query: String = ""
    private var loadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.pa_title),
            activity = this,
            actionIcon = R.drawable.ic_more_vert_24dp,
            actionDescription = getString(R.string.editor_more_actions),
            onAction = { showListActions() },
        )
        ToolbarBinder.attachTo(binding.toolbar.root, binding.recyclerView)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.search.searchInput.setHint(R.string.perapp_search_hint)
        binding.search.searchInput.doAfterTextChanged { text ->
            query = text?.toString().orEmpty()
            applyFilter()
        }

        bindModeRow()
        loadApps()
    }

    // ---------------------------------------------------------------- mode

    /**
     * The three modes, in cycle order. The pair of booleans behind them is unchanged - this is a
     * presentation of the existing preferences, not a new setting.
     */
    private enum class Mode(val labelRes: Int, val hintRes: Int) {
        ALL(R.string.perapp_mode_all, R.string.perapp_mode_all_hint),
        SELECTED(R.string.perapp_mode_selected, R.string.perapp_mode_selected_hint),
        EXCEPT(R.string.perapp_mode_except, R.string.perapp_mode_except_hint),
    }

    private fun currentMode(): Mode = when {
        !MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false) -> Mode.ALL
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false) -> Mode.EXCEPT
        else -> Mode.SELECTED
    }

    private fun applyMode(mode: Mode) {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, mode != Mode.ALL)
        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, mode == Mode.EXCEPT)
        SettingsChangeManager.makeRestartService()
        bindModeRow()
    }

    private fun bindModeRow() {
        val mode = currentMode()
        RowBinder.bind(
            root = binding.rowMode.root,
            title = getString(R.string.perapp_mode_row),
            subtitle = getString(mode.hintRes),
            glyph = R.drawable.ic_per_apps_24dp,
            value = getString(mode.labelRes),
            // The unfold glyph is the promise that the value changes HERE - no screen, no dialog
            // (22-components 8.1). Three options is exactly the count that grammar is for.
            trailing = RowBinder.Trailing.Glyph(
                icon = R.drawable.ic_arrow_drop_down,
                contentDescription = null,
            ),
            onClick = {
                val next = Mode.entries[(mode.ordinal + 1) % Mode.entries.size]
                applyMode(next)
            },
        )
    }

    // ---------------------------------------------------------------- list

    private fun loadApps() {
        loadFailed = false
        showLoadingState()

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                runCatching {
                    val collator = Collator.getInstance()
                    AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                        .sortedWith(compareBy(collator) { it.appName })
                }
            }
            apps.onSuccess {
                appsAll = it
                loadFailed = false
            }.onFailure {
                appsAll = emptyList()
                loadFailed = true
                LogUtil.e(ANG_PACKAGE, "Error loading apps", it)
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val key = query.trim().uppercase()
        val visible = if (key.isEmpty()) {
            appsAll
        } else {
            appsAll.filter {
                it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key)
            }
        }
        adapter.submit(
            apps = visible,
            userLabel = getString(R.string.perapp_section_user),
            systemLabel = getString(R.string.perapp_section_system),
        )
        updateMeta()
        showContentState(visible.isEmpty())
    }

    private fun updateMeta() {
        binding.tvMeta.text = getString(
            R.string.perapp_selected_count,
            viewModel.getAll().size,
            appsAll.size,
        )
    }

    // -------------------------------------------------------------- states

    private fun showLoadingState() {
        EmptyStateBinder.hide(binding.emptyState.root)
        binding.recyclerView.isVisible = false
        binding.tvMeta.isVisible = false
        SkeletonBinder.showAfterDelay(binding.skeleton)
    }

    /** Exactly one of list / empty / error is on screen once the load settles (00-rules.md 15). */
    private fun showContentState(isEmpty: Boolean) {
        SkeletonBinder.cancel(binding.skeleton)
        binding.tvMeta.isVisible = !loadFailed
        binding.search.root.isVisible = !loadFailed

        when {
            loadFailed -> {
                binding.recyclerView.isVisible = false
                EmptyStateBinder.bind(
                    root = binding.emptyState.root,
                    glyph = R.drawable.ic_dl_info,
                    title = getString(R.string.perapp_error_title),
                    line = getString(R.string.perapp_error_line),
                    actionLabel = getString(R.string.editor_retry),
                    emphasis = EmptyStateBinder.Emphasis.TERTIARY,
                    onAction = { loadApps() },
                )
            }

            isEmpty && query.isNotBlank() -> {
                binding.recyclerView.isVisible = false
                EmptyStateBinder.bind(
                    root = binding.emptyState.root,
                    glyph = R.drawable.ic_per_apps_24dp,
                    title = getString(R.string.editor_search_empty_title),
                    line = getString(R.string.editor_search_empty_line),
                    actionLabel = getString(R.string.editor_search_reset),
                    onAction = { binding.search.searchInput.setText("") },
                )
            }

            isEmpty -> {
                binding.recyclerView.isVisible = false
                EmptyStateBinder.bind(
                    root = binding.emptyState.root,
                    glyph = R.drawable.ic_per_apps_24dp,
                    title = getString(R.string.perapp_empty_title),
                    line = getString(R.string.perapp_empty_line),
                )
            }

            else -> {
                EmptyStateBinder.hide(binding.emptyState.root)
                binding.recyclerView.isVisible = true
            }
        }
    }

    // ------------------------------------------------------------- actions

    /**
     * The six list actions. They used to live in a toolbar overflow menu; the seamless sub-page
     * header of §4.8 keeps at most one trailing action, so they live in the one sheet the editors
     * share. Nothing was dropped: select-all now states which way it will go, and its inverse
     * («Снять отметки») is a separate row rather than a second meaning of the same one.
     */
    private fun showListActions() {
        val visible = adapter.apps.map { it.packageName }
        val allSelected = visible.isNotEmpty() && visible.all { viewModel.contains(it) }

        EditorActionsSheet(this, getString(R.string.editor_actions_title))
            .action(
                labelRes = if (allSelected) R.string.perapp_action_clear_all else R.string.perapp_action_select_all,
                enabled = visible.isNotEmpty(),
            ) {
                if (allSelected) viewModel.removeAll(visible) else viewModel.addAll(visible)
                afterBulkChange()
            }
            .action(R.string.perapp_action_invert, enabled = visible.isNotEmpty()) {
                visible.forEach { viewModel.toggle(it) }
                afterBulkChange()
            }
            .action(
                labelRes = R.string.perapp_action_auto,
                subtitle = getString(R.string.perapp_action_auto_hint),
            ) { selectProxyAppAuto() }
            .action(R.string.perapp_action_import) { importProxyApp() }
            .action(R.string.perapp_action_export) { exportProxyApp() }
            .show()
    }

    /**
     * A bulk change implies the feature is wanted: the old screen called `allowPerAppProxy()` after
     * every one of these, and that behaviour is kept - but only when the mode is «Все приложения»,
     * where the list would otherwise have no effect at all.
     */
    private fun afterBulkChange() {
        if (currentMode() == Mode.ALL) {
            applyMode(Mode.SELECTED)
        }
        adapter.refreshSelection()
        updateMeta()
    }

    private fun selectProxyAppAuto() {
        showLoadingState()
        binding.recyclerView.isVisible = false

        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 5000))
                    ?.takeIf { it.isNotEmpty() }
                    ?: HttpUtil.getUrlContent(
                        UrlContentRequest(
                            url = url,
                            timeout = 5000,
                            httpPort = SettingsManager.getHttpPort(),
                            proxyUsername = SettingsManager.getSocksUsername(),
                            proxyPassword = SettingsManager.getSocksPassword(),
                        )
                    ).orEmpty()
            }
            selectProxyApp(content, force = true)
            afterBulkChange()
            applyFilter()
            toastSuccess(R.string.editor_done)
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) return
        selectProxyApp(content, force = false)
        afterBulkChange()
        toastSuccess(R.string.editor_done)
    }

    private fun exportProxyApp() {
        val header = (currentMode() == Mode.EXCEPT).toString()
        val payload = viewModel.getAll().fold(header) { acc, pkg -> acc + System.lineSeparator() + pkg }
        Utils.setClipboard(applicationContext, payload)
        toastSuccess(R.string.editor_done)
    }

    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = content.ifEmpty {
                Utils.readTextFromAssets(v2RayApplication, "proxy_package_name")
            }
            if (proxyApps.isEmpty()) return false

            viewModel.clear()
            val bypassing = currentMode() == Mode.EXCEPT
            appsAll.forEach { app ->
                val listed = inProxyApps(proxyApps, app.packageName, force)
                if (listed != bypassing) {
                    viewModel.add(app.packageName)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error selecting proxy app", e)
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == WEBVIEW_PACKAGE) return false
            if (packageName.startsWith(GOOGLE_PACKAGE_PREFIX)) return true
        }
        return proxyApps.contains(packageName)
    }

    override fun onDestroy() {
        SkeletonBinder.cancel(binding.skeleton)
        super.onDestroy()
    }

    private companion object {
        const val WEBVIEW_PACKAGE = "com.google.android.webview"
        const val GOOGLE_PACKAGE_PREFIX = "com.google"
    }
}
