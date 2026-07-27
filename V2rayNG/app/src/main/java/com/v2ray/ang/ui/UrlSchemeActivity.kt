package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            parseUri(it, null)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    val uri: Uri? = intent.data
                    if (uri?.scheme.equals("depv", ignoreCase = true)) {
                        handleDepvScheme(uri)
                    } else {
                        when (uri?.host) {
                            "install-config" -> {
                                val shareUrl = uri.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri.fragment)
                            }

                            "install-sub" -> {
                                val shareUrl = uri.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri.fragment)
                            }

                            else -> {
                                toastError(R.string.editor_failed)
                            }
                        }
                    }
                }
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }
    }

    /**
     * Dispatch a depv:// deeplink by host / path.
     *
     * Supported:
     *  - connect | open            -> start the VPN service
     *  - disconnect | close        -> stop the VPN service
     *  - toggle                    -> stop if running, otherwise start
     *  - import/{base64}           -> Base64 -> batch import (auto-detect)
     *  - add/{url}                 -> import subscription / config by URL
     *  - routing/add/{base64}      -> Base64 JSON -> import routing rulesets
     *  - routing/onadd/{base64}    -> import routing rulesets and apply (restart if running)
     */
    private fun handleDepvScheme(uri: Uri?) {
        if (uri == null) {
            toastError(R.string.editor_failed)
            return
        }
        when (uri.host) {
            "connect", "open" -> CoreServiceManager.startVService(this)

            "disconnect", "close" -> CoreServiceManager.stopVService(this)

            "toggle" -> {
                if (CoreServiceManager.isRunning()) {
                    CoreServiceManager.stopVService(this)
                } else {
                    CoreServiceManager.startVServiceFromToggle(this)
                }
            }

            "import" -> {
                val decoded = Utils.decode(uri.lastPathSegment)
                if (decoded.isNotEmpty()) {
                    importDecodedConfig(decoded)
                } else {
                    toastError(R.string.editor_failed)
                }
            }

            "add" -> {
                val raw = uri.toString().substringAfter("://add/", "")
                if (raw.isNotEmpty()) {
                    parseUri(raw, null)
                } else {
                    toastError(R.string.editor_failed)
                }
            }

            "routing" -> {
                val segments = uri.pathSegments
                if (segments.size >= 2) {
                    val op = segments.first()
                    val json = Utils.decode(segments.last())
                    if ((op == "add" || op == "onadd") && json.isNotEmpty()) {
                        importRoutingRules(json, apply = op == "onadd")
                    } else {
                        toastError(R.string.editor_failed)
                    }
                } else {
                    toastError(R.string.editor_failed)
                }
            }

            else -> toastError(R.string.editor_failed)
        }
    }

    /**
     * Import already-decoded config content (a config / share-url payload) via the batch importer.
     */
    private fun importDecodedConfig(content: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (count, countSub) = AngConfigManager.importBatchConfig(content, "", false)
            withContext(Dispatchers.Main) {
                if (count + countSub > 0) {
                    toast(R.string.scheme_import_done)
                } else {
                    toastError(R.string.scheme_import_failed)
                }
            }
        }
    }

    /**
     * Import routing rulesets from a JSON payload; optionally apply by restarting a running service.
     */
    private fun importRoutingRules(json: String, apply: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SettingsManager.resetRoutingRulesets(json)
            if (result && apply && CoreServiceManager.isRunning()) {
                MessageUtil.sendMsg2Service(this@UrlSchemeActivity, AppConfig.MSG_STATE_RESTART, "")
            }
            withContext(Dispatchers.Main) {
                if (result) {
                    toastSuccess(R.string.editor_done)
                } else {
                    toastError(R.string.editor_failed)
                }
            }
        }
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            LogUtil.i(AppConfig.TAG, decodedUrl)
            lifecycleScope.launch(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
                withContext(Dispatchers.Main) {
                    if (count + countSub > 0) {
                        toast(R.string.scheme_import_done)
                    } else {
                        toastError(R.string.scheme_import_failed)
                    }
                }
            }
        }
    }
}