package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.util.Utils

/**
 * A-33 `settings/about` - «О приложении». H3 header, R1 rhythm.
 *
 * Beyond the restyle, this screen is where three built-but-unreachable activities get an entry
 * point. `LogcatActivity`, `CheckUpdateActivity` and `UrlSchemeListActivity` are all declared in the
 * manifest and launched by nothing in the codebase, which is why the product shipped with no way to
 * read a log and no way to check for an update. They are rows here.
 *
 * The version and the application id stop being two centred grey labels at the bottom of a scroll
 * and become `Row.Value` rows with a copy action - they exist to be sent to support, so they have to
 * be copyable.
 */
class AboutActivity : BaseActivity() {

    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ToolbarBinder.bind(
            root = binding.toolbar.root,
            title = getString(R.string.about_title),
            activity = this,
        )
        ToolbarBinder.attachTo(binding.toolbar.root, binding.mainContent)

        bindFacts()
        bindLinks()
    }

    private fun bindFacts() {
        val version = "${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"
        RowBinder.bind(
            root = binding.rowVersion.root,
            title = getString(R.string.about_version),
            value = version,
            valueIsNumeric = true,
            trailing = RowBinder.Trailing.IconAction(
                icon = R.drawable.ic_copy,
                contentDescription = getString(R.string.about_version_cd),
                onClick = { copy(version) },
            ),
        )
        RowBinder.bind(
            root = binding.rowPackage.root,
            title = getString(R.string.about_package),
            value = BuildConfig.APPLICATION_ID,
            trailing = RowBinder.Trailing.IconAction(
                icon = R.drawable.ic_copy,
                contentDescription = getString(R.string.about_package_cd),
                onClick = { copy(BuildConfig.APPLICATION_ID) },
            ),
        )
    }

    private fun bindLinks() {
        RowBinder.bind(
            root = binding.rowUrlSchemes.root,
            title = getString(R.string.about_url_schemes),
            glyph = R.drawable.ic_hub_url_scheme,
            onClick = { open(UrlSchemeListActivity::class.java) },
        )
        RowBinder.bind(
            root = binding.rowLog.root,
            title = getString(R.string.about_log),
            glyph = R.drawable.ic_logcat_24dp,
            onClick = { open(LogcatActivity::class.java) },
        )
        RowBinder.bind(
            root = binding.rowCheckUpdate.root,
            title = getString(R.string.about_check_update),
            glyph = R.drawable.ic_check_update_24dp,
            onClick = { open(CheckUpdateActivity::class.java) },
        )
        RowBinder.bind(
            root = binding.rowSourceCode.root,
            title = getString(R.string.about_source_code),
            glyph = R.drawable.ic_source_code_24dp,
            onClick = { Utils.openUri(this, AppConfig.APP_URL) },
        )
        RowBinder.bind(
            root = binding.rowFeedback.root,
            title = getString(R.string.about_feedback),
            glyph = R.drawable.ic_feedback_24dp,
            onClick = { Utils.openUri(this, AppConfig.APP_ISSUES_URL) },
        )
        RowBinder.bind(
            root = binding.rowTgChannel.root,
            title = getString(R.string.about_telegram),
            glyph = R.drawable.ic_telegram_24dp,
            onClick = { Utils.openUri(this, AppConfig.TG_CHANNEL_URL) },
        )
        RowBinder.bind(
            root = binding.rowLicenses.root,
            title = getString(R.string.about_licenses),
            glyph = R.drawable.license_24px,
            onClick = { showLicenses() },
        )
        RowBinder.bind(
            root = binding.rowPrivacyPolicy.root,
            title = getString(R.string.about_privacy),
            glyph = R.drawable.ic_privacy_24dp,
            onClick = { Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY) },
        )
    }

    private fun open(target: Class<*>) = SubPage.open(this, Intent(this, target))

    private fun copy(value: String) {
        Utils.setClipboard(this, value)
        toast(R.string.notice_copied)
    }

    /**
     * The licence text is a bundled HTML asset, so it stays in a WebView. What changed is the frame
     * around it: a Russian title and a verb on the button instead of the English «Open source
     * licenses» / «OK» pair the upstream dialog shipped (§9.2 - no «OK» anywhere).
     */
    private fun showLicenses() {
        val webView = android.webkit.WebView(this)
        webView.loadUrl("file:///android_asset/open_source_licenses.html")
        AlertDialog.Builder(this)
            .setTitle(R.string.about_licenses)
            .setView(webView)
            .setPositiveButton(R.string.editor_close) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
