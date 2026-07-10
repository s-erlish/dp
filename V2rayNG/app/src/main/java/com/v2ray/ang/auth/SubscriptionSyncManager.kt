package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the backend subscription payloads into the app's EXISTING subscription plumbing.
 *
 * This is the only place that touches the existing config code, and it reuses rather than
 * duplicates it:
 *  - [MmkvManager.encodeSubscription] / [MmkvManager.decodeSubscription] persist each item
 *  - [AngConfigManager.updateConfigViaSub] fetches + parses the servers (no new parsing)
 *  - [SubscriptionUpdater.syncOne] / [SubscriptionUpdater.cancelOne] handle periodic refresh
 *
 * The uuid->guid mapping is owned by [AuthTokenStore]; MmkvManager does not return the guid it
 * generates, so we generate a stable one ourselves on first import and remember it.
 */
class SubscriptionSyncManager {

    /**
     * Imports/updates every subscription in [items], removes locally any managed subscription no
     * longer present remotely, and returns the local guids of the current managed set (so the UI
     * can reload its server list).
     */
    suspend fun importAll(items: List<SubInfoDto>): List<String> = withContext(Dispatchers.IO) {
        val managed = AuthTokenStore.getManagedGuids()
        val newMap = HashMap<String, String>()
        val resultGuids = ArrayList<String>()

        for (info in items) {
            val raw = info.subscription?.response ?: continue
            val url = raw.subscriptionUrl
            if (url.isBlank()) continue

            val uuid = info.remnawaveUuid.ifBlank { info.id }.ifBlank { url }
            val guid = managed[uuid]?.ifBlank { null } ?: Utils.getUuid()

            val item = (MmkvManager.decodeSubscription(guid) ?: SubscriptionItem()).apply {
                remarks = info.displayName?.ifBlank { null }
                    ?: info.tariffDisplayName?.ifBlank { null }
                    ?: "Departament VPN"
                this.url = url
                enabled = true
                autoUpdate = true
                userAgent = BackendConfig.subscriptionUserAgent
            }

            MmkvManager.encodeSubscription(guid, item)
            AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
            SubscriptionUpdater.syncOne(subId = guid)

            newMap[uuid] = guid
            resultGuids.add(guid)
        }

        // Drop any previously managed subscription that is gone remotely.
        for ((uuid, guid) in managed) {
            if (!newMap.containsKey(uuid)) {
                SubscriptionUpdater.cancelOne(subId = guid)
                MmkvManager.removeSubscription(guid)
            }
        }

        AuthTokenStore.setManagedGuids(newMap)
        resultGuids
    }

    /** Removes every managed subscription and cancels their auto-update tasks (logout / 401). */
    fun removeAllManaged() {
        val managed = AuthTokenStore.getManagedGuids()
        for ((_, guid) in managed) {
            if (guid.isBlank()) continue
            SubscriptionUpdater.cancelOne(subId = guid)
            MmkvManager.removeSubscription(guid)
        }
        AuthTokenStore.setManagedGuids(emptyMap())
    }
}
