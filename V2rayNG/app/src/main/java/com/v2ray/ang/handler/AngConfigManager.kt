package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.auth.AuthTokenStore
import com.v2ray.ang.auth.BackendConfig
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /** Remnawave's own directive block (`injectHosts`, tag prefixes) — not an Xray root field. */
    private const val VENDOR_ROOT_KEY = "remnawave"

    /**
     * Drops the vendor [VENDOR_ROOT_KEY] object from a raw Xray JSON template.
     *
     * Remnawave resolves and removes it server-side when it generates a subscription, but a
     * template imported from a file or served by a panel that skipped that step still carries it,
     * and the core refuses to start on an unknown root field. What is stored must be exactly what
     * the core will run, so strip it before storage rather than at connect time.
     *
     * @param rawConfig The raw config JSON.
     * @return The config without the vendor object, or [rawConfig] unchanged when there is nothing
     *         to strip or it is not a JSON object.
     */
    private fun stripVendorRootKey(rawConfig: String): String {
        if (!rawConfig.contains(VENDOR_ROOT_KEY)) return rawConfig
        return try {
            val root = JsonUtil.parseString(rawConfig) ?: return rawConfig
            if (root.remove(VENDOR_ROOT_KEY) == null) return rawConfig
            JsonUtil.toJsonPretty(root) ?: rawConfig
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to strip vendor key from custom config", e)
            rawConfig
        }
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
                    // Parse into a staging list FIRST, delete second — the same order the link-list
                    // branch uses (`parseBatchConfig`, "if (configs.isNotEmpty())"). Deleting on
                    // `serverList.isNotEmpty()` meant a провайдер answering with a non-empty but
                    // unparseable XRAY_JSON body wiped every сервер it had, cleared the selected
                    // one, and then discovered it had nothing to put back — the user was left with
                    // an empty провайдер and no way to recover it from the device.
                    val staged = ArrayList<Pair<String, ProfileItem>>(serverList.size)
                    for (srv in serverList.reversed()) {
                        // Pretty-printing also normalises Gson's doubles back to ints, which the core needs.
                        val rawConfig = stripVendorRootKey(JsonUtil.toJsonPretty(srv) ?: "")
                        val config = CustomFmt.parse(rawConfig) ?: continue
                        staged.add(rawConfig to config)
                    }
                    if (staged.isNotEmpty()) {
                        val removedSelected = getRemovedSelectedProfile(subid, append)
                        if (!append) {
                            MmkvManager.removeServerViaSubid(subid)
                        }
                        val keyToProfile = mutableMapOf<String, ProfileItem>()
                        for ((rawConfig, config) in staged) {
                            config.subscriptionId = subid
                            config.locked = locked
                            config.description = generateDescription(config)
                            val key = MmkvManager.encodeServerConfig("", config)
                            MmkvManager.encodeServerRaw(key, TemplateManager.wrapRawForStorage(rawConfig, locked))
                            keyToProfile[key] = config
                        }
                        val matchKey = resolveSelectedKey(keyToProfile, removedSelected, subid, append)
                        matchKey?.let { MmkvManager.setSelectServer(it) }
                        return staged.size
                    }
                    // Nothing in the array parsed. Fall through to the single-config path below
                    // rather than returning 0: that path guards its own delete on a successful
                    // parse, so the worst case is an honest "imported nothing" with the
                    // already-stored серверы untouched.
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val rawConfig = stripVendorRootKey(server)
                val config = CustomFmt.parse(rawConfig) ?: return 0
                config.subscriptionId = subid
                config.locked = locked
                config.description = generateDescription(config)
                if (!append) {
                    MmkvManager.removeServerViaSubid(subid)
                }
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, TemplateManager.wrapRawForStorage(rawConfig, locked))
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
     * Decides which URL a subscription fetch may actually use, or null when it must not run.
     *
     * [Utils.isValidSubUrl] is the transport-safety test: https anywhere, or http to loopback or a
     * private range (that traffic never leaves the device or the LAN, which is why self-hosted
     * panels keep working below). `allowInsecureUrl` is the user's opt-out from it — and for an
     * operator-managed (locked) subscription it is deliberately NOT honoured: that URL carries the
     * account token and the response is the template we then store encrypted precisely because it
     * is sensitive, so cleartext would undo both at once.
     *
     * A locked subscription that IS on cleartext http is not just dropped. `locked` only becomes
     * known after the first successful fetch ([TemplateManager.applyLockState]), so a plain refusal
     * would turn a subscription that worked once into one that fails forever, with only a generic
     * "update failed" to show for it. Instead the same URL is retried over https — a scheme swap,
     * dropping a redundant `:80`, since asking for TLS on the cleartext port only fails the
     * handshake — which is what a real panel behind nginx/Caddy answers on anyway. If https does
     * not answer, the update fails with the reason in the log; it never falls back to http.
     *
     * @param url The subscription URL, already IDN-normalised.
     * @param sub The subscription being fetched.
     * @return The URL to fetch, or null to fail the update.
     */
    private fun resolveSecureSubUrl(url: String, sub: SubscriptionItem): String? {
        if (Utils.isValidSubUrl(url)) return url

        // Every refusal below ends the update with the same generic failure count, so the log line
        // is the only place the reason exists: each one names the subscription, what was refused
        // and what would fix it. (Surfacing it in the UI needs a reason on SubscriptionUpdateResult,
        // which this change does not own.)
        val name = sub.remarks.ifBlank { "(unnamed)" }

        if (!sub.locked) {
            // Ordinary subscription: the user's explicit opt-in still governs.
            if (sub.allowInsecureUrl) return url
            LogUtil.w(
                AppConfig.TAG,
                "Subscription \"$name\" not updated: its address is cleartext http and "
                    + "\"allow insecure HTTP address\" is off for it. Turn that switch on in the "
                    + "subscription editor, or change the address to https."
            )
            return null
        }

        val upgraded = toHttpsUrl(url)
        if (upgraded == null) {
            LogUtil.w(
                AppConfig.TAG,
                "Managed subscription \"$name\" not updated: its address is neither https nor a "
                    + "cleartext http address this app can parse, so there is no https address to "
                    + "try. Re-add the subscription from the account. (\"Allow insecure HTTP "
                    + "address\" does not apply here: an operator address carries the account "
                    + "token and is never fetched in cleartext.)"
            )
            return null
        }
        if (!Utils.isValidSubUrl(upgraded)) {
            LogUtil.w(
                AppConfig.TAG,
                "Managed subscription \"$name\" not updated: its address stays unsafe after the "
                    + "https upgrade. Re-add the subscription from the account."
            )
            return null
        }
        LogUtil.w(
            AppConfig.TAG,
            "Managed subscription \"$name\" is on cleartext http; fetching over https instead. "
                + "\"Allow insecure HTTP address\" does not apply to operator-managed "
                + "subscriptions — that address carries the account token."
        )
        return upgraded
    }

    /**
     * Rewrites an `http://` URL as `https://`, keeping userinfo, path, query and fragment, and any
     * explicit port other than the http default. Returns null when [url] is not cleartext http.
     *
     * Parsed with OkHttp's [okhttp3.HttpUrl] rather than [java.net.URI] on purpose: URI applies
     * RFC 2396 host rules and returns a null host for addresses OkHttp accepts and would happily
     * fetch (an underscore in the hostname is the everyday case), so a URI-based upgrade refused
     * the retry on addresses that were never the problem. The parser here is the one that performs
     * the fetch, so what it accepts and what can be fetched cannot drift apart.
     */
    private fun toHttpsUrl(url: String): String? {
        val parsed = Utils.fixIllegalUrl(url.trim()).toHttpUrlOrNull() ?: return null
        if (!parsed.scheme.equals("http", ignoreCase = true)) return null
        val builder = parsed.newBuilder().scheme("https")
        // Port 80 — implicit or written out — belongs to cleartext: asking for TLS on it only
        // fails the handshake, so move to the https default. Any other explicit port is the
        // operator's choice and is kept.
        if (parsed.port == 80) builder.port(443)
        return builder.build().toString()
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

            // Validate subscription info.
            //
            // A BLANK REMARK IS NOT A REASON TO SKIP, and it used to be. A подписка is identified by
            // its guid and its url; the remark is a display name that this very function is
            // responsible for FILLING IN from the провайдер's `profile-title` a few dozen lines
            // below. Refusing to fetch until it is already named made that adoption unreachable —
            // which is why the import had to invent «import sub» to get past this line at all. With
            // the placeholder gone, this guard would have skipped every freshly added подписка
            // forever.
            if (TextUtils.isEmpty(it.guid) || TextUtils.isEmpty(it.subscription.url)) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            val url = HttpUtil.toIdnUrl(it.subscription.url)
            if (!Utils.isValidUrl(url)) {
                return SubscriptionUpdateResult(failureCount = 1)
            }
            val fetchUrl = resolveSecureSubUrl(url, it.subscription)
                ?: return SubscriptionUpdateResult(failureCount = 1)
            // THE ADDRESS IS NOT LOGGED. A подписка URL carries the account token in its path, so
            // this line put a working credential into «Журнал», into logcat, and into every log the
            // user might export or screenshot for support. The host alone answers the only question
            // the log has to answer here — which операторский endpoint was contacted.
            LogUtil.i(AppConfig.TAG, "Subscription fetch: ${HttpUtil.hostOf(fetchUrl)}")
            // The panel picks the response format (XRAY_JSON template vs base64 link list) from
            // the User-Agent, so a per-subscription override wins over everything, then the
            // global override from the provider screen, then the operator-configured UA. All
            // three tiers are resolved HERE because this is the only fetch point, and both the
            // scheduled worker and a manual refresh come through it — resolving the global tier
            // anywhere else would let the automatic and the manual refresh of one subscription
            // ask for different response formats. The worker deliberately does not pre-stamp the
            // item (see SubscriptionUpdater.UpdateTask) and neither does the account import (see
            // SubscriptionSyncManager): this function persists the item, so a UA set on it there
            // would silently become that subscription's own override and outlive the global one.
            val userAgent = it.subscription.userAgent?.trim()?.ifBlank { null }
                ?: SettingsManager.getSubscriptionUserAgent()
                ?: BackendConfig.subscriptionUserAgent
            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()
            // Stable per-install device id (HWID) header, opt-out via settings.
            val hwid = if (SettingsManager.isSendHwid()) AuthTokenStore.deviceId() else null

            var result = try {
                val httpPort = SettingsManager.getHttpPort()
                HttpUtil.getUrlContentWithUserAgentEx(
                    UrlContentRequest(
                        url = fetchUrl,
                        userAgent = userAgent,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword,
                        hwid = hwid
                    )
                )
            } catch (e: Exception) {
                // THE FIRST ATTEMPT IS EXPECTED TO FAIL WHEN NO TUNNEL IS UP, so it is not an error.
                //
                // This call is deliberately routed through the local HTTP proxy port, which only
                // exists while the core is running. Refreshing a подписка with the VPN off — the
                // ordinary case, and what the background worker does every few hours — therefore
                // threw here EVERY time, and the ERROR line with its stack trace was the first thing
                // in «Журнал» on a session where nothing had gone wrong. The direct retry two lines
                // below is what actually fetches it, and it succeeds.
                //
                // One INFO line, and the reason, so a genuine proxy failure is still traceable.
                LogUtil.i(
                    AppConfig.TAG,
                    "Subscription fetch via the local proxy did not answer, retrying directly: ${e.message}"
                )
                null
            }
            if (result == null || result.body.isEmpty()) {
                result = try {
                    HttpUtil.getUrlContentWithUserAgentEx(
                        UrlContentRequest(
                            url = fetchUrl,
                            userAgent = userAgent,
                            hwid = hwid
                        )
                    )
                } catch (e: Exception) {
                    // THIS one is a real failure — both routes are exhausted and the подписка did
                    // not refresh — so it stays at error level. The address is not in the message:
                    // it carries the account token (see the fetch log above).
                    LogUtil.e(
                        AppConfig.TAG,
                        "Subscription not refreshed: ${HttpUtil.hostOf(fetchUrl)} did not answer",
                        e
                    )
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
                // Adopt that provider title as the подписка's stored name too, but only while the
                // подписка is still unnamed — a name that identifies THIS подписка must never be
                // clobbered.
                //
                // THIS IS ALSO THE HEALING PATH FOR EVERY INSTALL THAT ALREADY STORED A
                // PLACEHOLDER. [SubscriptionNaming.isUnnamed] treats «import sub», «Default» and the
                // generic service label as no name at all, so the first refresh after this build
                // replaces each of them with what the провайдер actually calls the подписка
                // («🍀 erlish»). There is no rename to fall back on (OWNER-DECISION-2026-08-02 §5),
                // so this is the only route by which a bad stored name can ever be corrected.
                val providerTitle = it.subscription.profileTitle.trim()
                if (providerTitle.isNotEmpty() && SubscriptionNaming.isUnnamed(it.subscription)) {
                    it.subscription.remarks = providerTitle
                }
                it.subscription.lastUpdated = System.currentTimeMillis()
                MmkvManager.encodeSubscription(it.guid, it.subscription)
                LogUtil.i(AppConfig.TAG, "Subscription updated: $count configs")
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
        // Name the подписка after the URL's #fragment when present, and LEAVE IT BLANK otherwise —
        // no placeholder is stored, because a placeholder that is stored is a placeholder that gets
        // shown. Upstream's «import sub» reached the card heading, the server-list group name and
        // the shade («Обновляем «import sub»»), and with no rename UI to correct it
        // (OWNER-DECISION-2026-08-02 §5) it was permanent.
        //
        // Blank is not a gap: the real provider title (`profile-title`) arrives on the very first
        // fetch, a few lines into [updateConfigViaSub], which adopts it into a still-unnamed
        // подписка. Until then every display path resolves the name through [SubscriptionNaming],
        // which answers «Подписка» rather than printing an empty string.
        subItem.remarks = uri.fragment?.trim().orEmpty()
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
