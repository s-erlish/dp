package com.v2ray.ang.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.databinding.ActivityCheckUpdateBinding
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.ui.component.EmptyStateBinder
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

/**
 * A-36 - «Обновления». H3 header, R1 rhythm.
 *
 * Reachable for the first time, from «О приложении». The result used to arrive as three toasts -
 * one on entry, one on success, and one carrying `e.message` raw to a customer (§9.4: never a code,
 * never an untranslated exception). It is a drawn state now:
 *
 * - **checking** - a 20dp inline arc with «Проверяем обновления…», announced politely to TalkBack;
 * - **up to date** - «Обновлений нет» / «Установлена последняя версия.», no action;
 * - **update available** - «Доступна версия X» with the release notes below and one «Скачать»;
 * - **failed** - the mapped cause and «Повторить», with the raw exception going to the log instead.
 */
class CheckUpdateActivity : BaseActivity() {

    private val binding by lazy { ActivityCheckUpdateBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.upd_title),
            activity = this,
        )
        ToolbarBinder.attachTo(binding.toolbar.root, binding.mainContent)

        RowBinder.bind(
            root = binding.rowVersion.root,
            title = getString(R.string.about_version),
            value = "${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})",
            valueIsNumeric = true,
            trailing = RowBinder.Trailing.None,
        )

        bindPreReleaseRow()
        checkForUpdates()
    }

    private fun bindPreReleaseRow() {
        RowBinder.bind(
            root = binding.rowPreRelease.root,
            title = getString(R.string.upd_prerelease),
            subtitle = getString(R.string.upd_prerelease_hint),
            trailing = RowBinder.Trailing.Toggle(
                checked = MmkvManager.decodeSettingsBool(
                    AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE,
                    false,
                ),
            ) { checked ->
                MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, checked)
                // The answer depends on this switch, so the answer is re-fetched rather than left
                // on screen contradicting the control above it.
                checkForUpdates()
            },
        )
    }

    private fun includePreRelease(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

    private fun checkForUpdates() {
        showChecking()
        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease())
                if (result.hasUpdate) showUpdateAvailable(result) else showUpToDate()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates", e)
                showFailure()
            }
        }
    }

    // -------------------------------------------------------------- states

    private fun showChecking() {
        binding.checking.isVisible = true
        binding.tvReleaseNotes.isVisible = false
        EmptyStateBinder.hide(binding.emptyState.root)
    }

    private fun showUpToDate() {
        binding.checking.isVisible = false
        binding.tvReleaseNotes.isVisible = false
        EmptyStateBinder.bind(
            root = binding.emptyState.root,
            glyph = R.drawable.ic_check_update_24dp,
            title = getString(R.string.upd_none_title),
            line = getString(R.string.upd_none_line),
        )
    }

    private fun showUpdateAvailable(result: CheckUpdateResult) {
        binding.checking.isVisible = false
        val notes = result.releaseNotes?.trim().orEmpty()
        binding.tvReleaseNotes.text = notes
        binding.tvReleaseNotes.isVisible = notes.isNotEmpty()

        val downloadUrl = result.downloadUrl
        EmptyStateBinder.bind(
            root = binding.emptyState.root,
            glyph = R.drawable.ic_cloud_download_24dp,
            title = getString(R.string.upd_found_title, result.latestVersion.orEmpty()),
            line = getString(R.string.upd_found_line),
            actionLabel = downloadUrl?.let { getString(R.string.upd_action_download) },
            emphasis = EmptyStateBinder.Emphasis.PRIMARY,
            onAction = downloadUrl?.let { url -> { Utils.openUri(this, url) } },
        )
    }

    private fun showFailure() {
        binding.checking.isVisible = false
        binding.tvReleaseNotes.isVisible = false
        EmptyStateBinder.bind(
            root = binding.emptyState.root,
            glyph = R.drawable.ic_dl_info,
            title = getString(R.string.upd_error_title),
            line = getString(R.string.upd_error_line),
            actionLabel = getString(R.string.editor_retry),
            emphasis = EmptyStateBinder.Emphasis.TERTIARY,
            onAction = { checkForUpdates() },
        )
    }
}
