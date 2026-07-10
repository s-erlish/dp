package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AuthTokenStore
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.template.TemplateManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.SubscriptionUserInfo
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.SubscriptionGuard
import com.v2ray.ang.util.Utils
import java.net.URI

object AngConfigManager {

    /**
     * Rich outcome of [importBatchConfig].
     *
     * Kept as a data class whose first two components are (count, countSub) on purpose: existing
     * callers that destructure `val (count, countSub) = importBatchConfig(...)` keep compiling and
     * behaving exactly as before, while the add-server/subscription UIs can read the extra fields to
     * tell "added nothing because it was a duplicate" apart from "added nothing because it was
     * invalid", and to surface subscription fetch failures.
     */
    data class ImportResult(
        val count: Int = 0,                  // component1: single-server configs imported
        val countSub: Int = 0,               // component2: NEW subscriptions added
        val subDuplicate: Boolean = false,   // a subscription URL was recognised but already present
        val subRejected: Boolean = false,    // a subscription URL was blocked (not a departament link)
        val subFetch: SubscriptionUpdateResult? = null  // fetch outcome for the just-added subscription(s)
    )

    /** Outcome of trying to add a single subscription URL. */
    private sealed interface SubAddOutcome {
        data class Added(val guid: String) : SubAddOutcome
        object Duplicate : SubAddOutcome
        object Rejected : SubAddOutcome
    }

    /** Aggregated outcome of parsing every subscription URL found in a pasted/scanned blob. */
    private data class SubParseResult(
        val addedGuids: List<String> = emptyList(),
        val subDuplicate: Boolean = false,
        val subRejected: Boolean = false
    ) {
        val countSub: Int get() = addedGuids.size
    }

    // Parser mapping for different config types (lazy initialized)
    private val configFmtParsers: Map<String, (String) -> ProfileItem?> by lazy {
        mapOf(
            EConfigType.VMESS.protocolScheme to VmessFmt::parse,
            EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
            EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
            AppConfig.SOCKS4 to SocksFmt::parse,
            AppConfig.SOCKS5 to SocksFmt::parse,
            EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
            EConfigType.VLESS.protocolScheme to VlessFmt::parse,
            EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
            EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
            AppConfig.HY2 to Hysteria2Fmt::parse
        )
    }

    /**
     * Shares the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares non-custom configurations to the clipboard.
     *
     * @param context The context.
     * @param serverList The list of server GUIDs.
     * @return The number of configurations shared.
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    /**
     * Shares the configuration as a QR code.
     *
     * @param guid The GUID of the configuration.
     * @return The QR code bitmap.
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    /**
     * Shares the full content of the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            // Block full-config export for managed/hidden profiles.
            if (TemplateManager.isLocked(guid)) return -1
            val result = CoreConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares the configuration.
     *
     * @param guid The GUID of the configuration.
     * @return The configuration string.
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""
            // Managed/hidden profiles must never be shareable/exportable.
            if (TemplateManager.isLocked(config)) return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                else -> {}
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    /**
     * Imports a batch of configurations.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return An [ImportResult]; its first two components stay (count, countSub) for back-compat.
     */
    fun importBatchConfig(server: String?, subid: String, append: Boolean): ImportResult {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }

        // Single-server pastes/QR/manual use append mode, which would otherwise let a re-scanned or
        // re-pasted link pile up identical rows. Drop fingerprint duplicates within this bucket.
        if (count > 0) {
            dedupServersViaSubid(subid)
        }

        var subResult = parseBatchSubscription(server)
        if (subResult.countSub <= 0 && !subResult.subDuplicate && !subResult.subRejected) {
            subResult = parseBatchSubscription(Utils.decode(server))
        }

        // Fetch ONLY the just-added subscription(s) - not every existing one - and schedule their
        // auto-update now, instead of waiting for the next cold-start WorkManager sync.
        var subFetch: SubscriptionUpdateResult? = null
        if (subResult.addedGuids.isNotEmpty()) {
            var acc = SubscriptionUpdateResult()
            subResult.addedGuids.forEach { guid ->
                MmkvManager.decodeSubscription(guid)?.let { item ->
                    acc += updateConfigViaSub(SubscriptionCache(guid, item))
                    SubscriptionUpdater.syncOne(subId = guid)
                }
            }
            subFetch = acc
        }

        return ImportResult(
            count = count,
            countSub = subResult.countSub,
            subDuplicate = subResult.subDuplicate,
            subRejected = subResult.subRejected,
            subFetch = subFetch
        )
    }

    /**
     * Removes fingerprint-duplicate servers within a single subscription bucket, so re-scanning or
     * re-pasting the same link never leaves duplicate rows. Keeps one entry per fingerprint and
     * always preserves the currently-selected server (if a later duplicate is the selected one, the
     * earlier copy is dropped instead). Complex profiles (Custom/PolicyGroup/ProxyChain) are left
     * untouched.
     *
     * Fingerprinting reuses [ProfileItem.equals], which compares connection identity
     * (server/port/credentials/transport) and ignores remarks. A linear scan is used on purpose:
     * [ProfileItem] overrides equals but not hashCode, so it must never be used as a hash key.
     *
     * @param subid The subscription ID whose servers should be de-duplicated.
     * @return The number of duplicate servers removed.
     */
    private fun dedupServersViaSubid(subid: String): Int {
        try {
            val serverList = MmkvManager.decodeServerList(subid)
            if (serverList.size < 2) return 0

            val selected = MmkvManager.getSelectServer()
            val kept = mutableListOf<Pair<ProfileItem, String>>() // (profile, kept guid)
            val toRemove = mutableListOf<String>()
            for (guid in serverList) {
                val profile = MmkvManager.decodeServerConfig(guid) ?: continue
                if (profile.configType.isComplexType()) continue
                val dupIndex = kept.indexOfFirst { it.first == profile }
                if (dupIndex < 0) {
                    kept.add(profile to guid)
                } else if (guid == selected && kept[dupIndex].second != selected) {
                    // Preserve the selected duplicate; drop the earlier copy instead.
                    toRemove.add(kept[dupIndex].second)
                    kept[dupIndex] = profile to guid
                } else {
                    toRemove.add(guid)
                }
            }
            toRemove.forEach { MmkvManager.removeServer(it) }
            return toRemove.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to dedup servers for subid: $subid", e)
            return 0
        }
    }

    /**
     * Parses a batch of subscriptions.
     *
     * @param servers The servers string.
     * @return Aggregated outcome: guids added, plus whether any URL was a duplicate or was rejected.
     */
    private fun parseBatchSubscription(servers: String?): SubParseResult {
        try {
            if (servers == null) {
                return SubParseResult()
            }

            val added = mutableListOf<String>()
            var duplicate = false
            var rejected = false
            servers.lines()
                .distinct()
                .forEach { str ->
                    if (Utils.isValidSubUrl(str)) {
                        when (val outcome = importUrlAsSubscription(str)) {
                            is SubAddOutcome.Added -> added.add(outcome.guid)
                            SubAddOutcome.Duplicate -> duplicate = true
                            SubAddOutcome.Rejected -> rejected = true
                        }
                    }
                }
            return SubParseResult(added, duplicate, rejected)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return SubParseResult()
    }

    /**
     * Parses a batch of configurations.
     *
     * @param servers The servers string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }
            // Find the currently selected server that belongs to the same subscription before replacement.
            val removedSelected = getRemovedSelectedProfile(subid, append)

            val subItem = MmkvManager.decodeSubscription(subid)

            // Parse all configs first (no I/O during parsing)
            val configs = mutableListOf<ProfileItem>()
            servers.lines()
                .distinct()
                .reversed()
                .forEach {
                    val config = parseConfig(it, subid, subItem)
                    if (config != null) {
                        configs.add(config)
                    }
                }

            // Batch save all parsed configs (only one serverList read/write)
            if (configs.isNotEmpty()) {
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val keyToProfile = batchSaveConfigs(configs, subid)
                val matchKey = resolveSelectedKey(keyToProfile, removedSelected, subid, append)
                matchKey?.let { MmkvManager.setSelectServer(it) }
            }

            return configs.size
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    /**
     * Batch save configurations to reduce serverList read/write operations.
     * Reads serverList once, saves all configs, then writes serverList once.
     *
     * @param configs The list of ProfileItem to save.
     * @param subid The subscription ID.
     * @return Map of generated keys to their corresponding ProfileItem.
     */
    private fun batchSaveConfigs(configs: List<ProfileItem>, subid: String): Map<String, ProfileItem> {
        val keyToProfile = mutableMapOf<String, ProfileItem>()

        // Read serverList once
        val serverList = MmkvManager.decodeServerList(subid)

        configs.forEach { config ->
            val key = Utils.getUuid()
            // Save profile directly without updating serverList
            MmkvManager.encodeProfileDirect(key, JsonUtil.toJson(config))

            if (!serverList.contains(key)) {
                serverList.add(0, key)
            }
            keyToProfile[key] = config
        }

        // Write serverList once
        MmkvManager.encodeServerList(serverList, subid)
        return keyToProfile
    }

    /**
     * Finds a matched profile key from the given key-profile map using multi-level matching.
     * Matching priority (from highest to lowest):
     * 1. Exact match: server + port + password
     * 2. Match by remarks (exact match)
     * 3. Match by server + port
     * 4. Match by server only
     *
     * @param keyToProfile Map of server keys to their ProfileItem
     * @param target Target profile to match
     * @return Matched key or null
     */
    private fun findMatchedProfileKey(keyToProfile: Map<String, ProfileItem>, target: ProfileItem?): String? {
        if (keyToProfile.isEmpty()) return null
        if (target == null) return null

        // Level 0: Full match (remarks + server + port + password)
        if (target.remarks.isNotBlank()) {
            keyToProfile.entries.firstOrNull { (_, saved) ->
                isSameText(saved.remarks, target.remarks) &&
                        isSameText(saved.server, target.server) &&
                        isSameText(saved.serverPort, target.serverPort) &&
                        isSameText(saved.password, target.password)
            }?.key?.let { return it }
        }

        // Level 1: Match by remarks
        if (target.remarks.isNotBlank()) {
            keyToProfile.entries.firstOrNull { (_, saved) ->
                isSameText(saved.remarks, target.remarks)
            }?.key?.let { return it }
        }

        // Level 2: Exact match (server + port + password)
        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server) &&
                    isSameText(saved.serverPort, target.serverPort) &&
                    isSameText(saved.password, target.password)
        }?.key?.let { return it }

        // Level 3: Match by server + port
        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server) &&
                    isSameText(saved.serverPort, target.serverPort)
        }?.key?.let { return it }

        // Level 4: Match by server only
        keyToProfile.entries.firstOrNull { (_, saved) ->
            isSameText(saved.server, target.server)
        }?.key?.let { return it }

        // If the old selected node cannot be matched, fall back to the first subscription server.
        // keyToProfile is built in REVERSE subscription order (parseBatchConfig reverses the lines
        // and both batchSaveConfigs and the custom-JSON loop insert each key at the head of the
        // stored list), so the first subscription server - the one shown at the TOP of the list -
        // is the LAST inserted key here.
        return keyToProfile.keys.lastOrNull()
    }

    /**
     * Decides which server should be selected (highlighted/active) after an import.
     *
     * Behaviour:
     * - Re-import: if the previously selected server (removedSelected) can be matched in the newly
     *   imported set, keep selecting it.
     * - Fresh add: select the FIRST server of the subscription (the top of the displayed list).
     *   Because keyToProfile is in reverse subscription order (see findMatchedProfileKey), the first
     *   subscription server is the LAST inserted key.
     * - Guardrails: never change the selection in append mode, and never hijack a still-valid
     *   selection that belongs to a DIFFERENT subscription (this protects the user's active server
     *   during a background update-all refresh, where every subscription is re-imported with
     *   append = false).
     *
     * @param keyToProfile Map of newly saved server keys to their ProfileItem (reverse sub order).
     * @param removedSelected The previously selected profile of this subscription, if any.
     * @param subid The subscription ID being imported.
     * @param append Whether this import is an append (vs. replace).
     * @return The key to select, or null to leave the current selection unchanged.
     */
    private fun resolveSelectedKey(
        keyToProfile: Map<String, ProfileItem>,
        removedSelected: ProfileItem?,
        subid: String,
        append: Boolean
    ): String? {
        if (keyToProfile.isEmpty()) return null

        // Re-import: preserve the matched previous selection (falls back to the first server).
        if (removedSelected != null) {
            return findMatchedProfileKey(keyToProfile, removedSelected)
        }

        // Never touch the selection when appending.
        if (append) return null

        // Do not steal a still-valid selection that lives in another subscription.
        val current = MmkvManager.getSelectServer()
        if (!current.isNullOrBlank()) {
            val currentSubId = MmkvManager.decodeServerConfig(current)?.subscriptionId
            if (currentSubId != null && currentSubId != subid) return null
        }

        // Fresh add: select the first subscription server (top of the list).
        return keyToProfile.keys.lastOrNull()
    }

    /**
     * Returns the currently selected profile if it belongs to the target subscription and will be replaced.
     */
    private fun getRemovedSelectedProfile(subid: String, append: Boolean): ProfileItem? {
        if (subid.isBlank() || append) return null

        return MmkvManager.getSelectServer()
            .takeIf { it?.isNotBlank() == true }
            ?.let { MmkvManager.decodeServerConfig(it) }
            ?.takeIf { it.subscriptionId == subid }
    }

    /**
     * Case-insensitive trimmed string comparison.
     *
     * @param left First string
     * @param right Second string
     * @return True if both are non-empty and equal (case-insensitive, trimmed)
     */
    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }

    /**
     * Parses a custom configuration server.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseCustomConfigServer(server: String?, subid: String, append: Boolean): Int {
        if (server == null) {
            return 0
        }
        // A locked (operator-managed, hidden) subscription stamps every imported profile and
        // stores its raw template obfuscated/encrypted. Non-locked subs keep the plaintext path.
        val locked = MmkvManager.decodeSubscription(subid)?.locked == true
        // Robustly detect a raw Xray JSON body (single object or array of configs).
        // Remnawave XRAY_JSON templates may omit "inbounds"/"routing", so key off the
        // structure (starts with { or [) plus the mandatory "outbounds" key, rather than
        // requiring all three substrings. Base64 / vless:// link lists are handled upstream
        // in parseConfigViaSub (parseBatchConfig) before this custom-JSON path is reached.
        val trimmedServer = server.trim()
        val looksLikeJson = (trimmedServer.startsWith("{") || trimmedServer.startsWith("["))
            && server.contains("outbounds")
        if (looksLikeJson) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()

                if (serverList.isNotEmpty()) {
                    val removedSelected = getRemovedSelectedProfile(subid, append)
                    if (!append) {
                        MmkvManager.removeServerViaSubid(subid)
                    }
                    var count = 0
                    val keyToProfile = mutableMapOf<String, ProfileItem>()
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv)) ?: continue
                        config.subscriptionId = subid
                        config.locked = locked
                        config.description = generateDescription(config)
                        val key = MmkvManager.encodeServerConfig("", config)
                        MmkvManager.encodeServerRaw(key, TemplateManager.wrapRawForStorage(JsonUtil.toJsonPretty(srv) ?: "", locked))
                        keyToProfile[key] = config
                        count += 1
                    }
                    if (count > 0) {
                        val matchKey = resolveSelectedKey(keyToProfile, removedSelected, subid, append)
                        matchKey?.let { MmkvManager.setSelectServer(it) }
                    }
                    return count
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server) ?: return 0
                config.subscriptionId = subid
                config.locked = locked
                config.description = generateDescription(config)
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, TemplateManager.wrapRawForStorage(server, locked))
                return 1
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server) ?: return R.string.toast_incorrect_protocol
                config.description = generateDescription(config)
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    /**
     * Parses the configuration from a QR code or string.
     * Only parses and returns ProfileItem, does not save.
     *
     * @param str The configuration string.
     * @param subid The subscription ID.
     * @param subItem The subscription item.
     * @return The parsed ProfileItem or null if parsing fails or filtered out.
     */
    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?
    ): ProfileItem? {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return null
            }

            val config = configFmtParsers.firstNotNullOfOrNull { (scheme, parser) ->
                if (str.startsWith(scheme)) parser(str) else null
            }

            if (config == null) {
                return null
            }

            // Apply filter
            if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
                val matched = Regex(pattern = subItem?.filter.orEmpty())
                    .containsMatchIn(input = config.remarks)
                if (!matched) return null
            }

            config.subscriptionId = subid
            // Inherit the hidden/locked state from the owning subscription so share-link
            // members of a managed subscription are also gated in the UI.
            config.locked = subItem?.locked == true
            config.description = generateDescription(config)

            return config
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse config", e)
            return null
        }
    }

    /**
     * Updates the configuration via all subscriptions.
     *
     * @return Detailed result of the subscription update operation.
     */
    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        return try {
            val subscriptions = MmkvManager.decodeSubscriptions()
            subscriptions.fold(SubscriptionUpdateResult()) { acc, subscription ->
                acc + updateConfigViaSub(subscription)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            SubscriptionUpdateResult()
        }
    }

    /**
     * Decodes a Happ/Incy subscription directive header value.
     * @return null when the header is absent (leave the stored value unchanged);
     *         "" when the value is "0" (clear); otherwise the plaintext ("base64:" decoded).
     */
    private fun decodeSubDirective(raw: String?): String? {
        if (raw == null) return null
        val v = raw.trim()
        if (v == "0") return ""
        return if (v.startsWith("base64:")) {
            try {
                String(android.util.Base64.decode(v.removePrefix("base64:"), android.util.Base64.DEFAULT)).trim()
            } catch (e: Exception) {
                v
            }
        } else {
            v
        }
    }

    /**
     * Updates the configuration via a subscription.
     *
     * @param it The subscription item.
     * @return Subscription update result.
     */
    fun updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult {
        try {
            // Check if disabled
            if (!it.subscription.enabled) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            // Validate subscription info
            if (TextUtils.isEmpty(it.guid)
                || TextUtils.isEmpty(it.subscription.remarks)
                || TextUtils.isEmpty(it.subscription.url)
            ) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            val url = HttpUtil.toIdnUrl(it.subscription.url)
            if (!Utils.isValidUrl(url)) {
                return SubscriptionUpdateResult(failureCount = 1)
            }
            if (!it.subscription.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return SubscriptionUpdateResult(failureCount = 1)
                }
            }
            LogUtil.i(AppConfig.TAG, url)
            val userAgent = it.subscription.userAgent
            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()
            // Stable per-install device id (HWID) header, opt-out via settings.
            val hwid = if (SettingsManager.isSendHwid()) AuthTokenStore.deviceId() else null

            var result = try {
                val httpPort = SettingsManager.getHttpPort()
                HttpUtil.getUrlContentWithUserAgentEx(
                    UrlContentRequest(
                        url = url,
                        userAgent = userAgent,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword,
                        hwid = hwid
                    )
                )
            } catch (e: Exception) {
                LogUtil.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error", e)
                null
            }
            if (result == null || result.body.isEmpty()) {
                result = try {
                    HttpUtil.getUrlContentWithUserAgentEx(
                        UrlContentRequest(
                            url = url,
                            userAgent = userAgent,
                            hwid = hwid
                        )
                    )
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Update subscription: Failed to get URL content with user agent", e)
                    null
                }
            }
            val configText = result?.body ?: ""
            if (configText.isEmpty()) {
                return SubscriptionUpdateResult(failureCount = 1)
            }

            // Resolve the hidden/locked state (header or in-body directive) and persist it
            // BEFORE parsing, so imported profiles inherit sub.locked and store their raw
            // template obfuscated/encrypted. Single entry point into the template module.
            if (TemplateManager.applyLockState(it.subscription, result?.hidden, configText)) {
                MmkvManager.encodeSubscription(it.guid, it.subscription)
            }

            val count = parseConfigViaSub(configText, it.guid, false)
            if (count > 0) {
                // Persist traffic/expiry metadata from the subscription-userinfo header, if present.
                SubscriptionUserInfo.parse(result?.subscriptionUserInfo)?.let { info ->
                    it.subscription.uploadUsed = info.upload
                    it.subscription.downloadUsed = info.download
                    it.subscription.totalTraffic = info.total
                    it.subscription.expire = info.expire
                    it.subscription.userInfoUpdated = System.currentTimeMillis()
                }
                // Persist Happ/Incy-style directives (announce banner, support/website buttons).
                // null header = leave unchanged; "0" = clear; "base64:.." = decoded.
                decodeSubDirective(result?.announce)?.let { v -> it.subscription.announce = v }
                decodeSubDirective(result?.supportUrl)?.let { v -> it.subscription.supportUrl = v }
                decodeSubDirective(result?.webPageUrl)?.let { v -> it.subscription.webPageUrl = v }
                // Real subscription title sent by the provider (used as the meta-bar heading).
                decodeSubDirective(result?.profileTitle)?.let { v -> it.subscription.profileTitle = v }
                it.subscription.lastUpdated = System.currentTimeMillis()
                MmkvManager.encodeSubscription(it.guid, it.subscription)
                LogUtil.i(AppConfig.TAG, "Subscription updated: ${it.subscription.remarks}, $count configs")
                return SubscriptionUpdateResult(
                    configCount = count,
                    successCount = 1
                )
            } else {
                // Got response but no valid configs parsed
                return SubscriptionUpdateResult(failureCount = 1)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return SubscriptionUpdateResult(failureCount = 1)
        }
    }

    /**
     * Parses the configuration via a subscription.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }
        return count
    }

    /**
     * Imports a URL as a subscription.
     *
     * A departament-only guard is applied here (and not to single-server pastes): subscription
     * URLs are an account/payment surface, so pasting/scanning/deep-linking a foreign subscription
     * link is rejected the same way [com.v2ray.ang.ui.SubEditActivity] rejects it. Individual
     * `vless://`/`vmess://`/... server links are not subscription URLs and stay unguarded so users
     * can still add one-off servers by hand.
     *
     * @param url The URL.
     * @return The outcome (added with its new guid, duplicate, or rejected).
     */
    private fun importUrlAsSubscription(url: String): SubAddOutcome {
        // Only departament subscription links may be added.
        if (!SubscriptionGuard.isAllowed(url)) {
            return SubAddOutcome.Rejected
        }

        // Normalise before the equality check so trivially-different spellings of the same link
        // (trailing slash, host/scheme case) are recognised as duplicates rather than re-added.
        val normalized = normalizeSubUrl(url)
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (normalizeSubUrl(it.subscription.url) == normalized) {
                return SubAddOutcome.Duplicate
            }
        }

        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        val guid = Utils.getUuid()
        MmkvManager.encodeSubscription(guid, subItem)
        return SubAddOutcome.Added(guid)
    }

    /**
     * Normalises a subscription URL for equality comparison: trims, lowercases scheme + host,
     * drops the fragment, and strips a trailing slash from the path. Falls back to a trimmed,
     * trailing-slash-stripped string if the URL cannot be parsed.
     */
    private fun normalizeSubUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        return try {
            val uri = URI(Utils.fixIllegalUrl(trimmed))
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val path = (uri.path ?: "").trimEnd('/')
            val query = uri.query?.let { "?$it" } ?: ""
            "$scheme://$host$port$path$query"
        } catch (e: Exception) {
            trimmed.trimEnd('/')
        }
    }

    /** Generates a description for the profile.
     *
     * @param profile The profile item.
     * @return The generated description.
     */
    fun generateDescription(profile: ProfileItem): String {
        // Hide xxx:xxx:***/xxx.xxx.xxx.***
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addrPart = server?.let {
            if (it.contains(":"))
                it.split(":").take(2).joinToString(":", postfix = ":***")
            else
                it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addrPart : ${port ?: ""}"
    }
}
