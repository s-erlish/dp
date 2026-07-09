package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.SubscriptionInfoDto
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the backend subscription payload into the app's EXISTING subscription plumbing.
 *
 * This is the only place that touches existing config code, and it reuses rather than
 * duplicates it:
 *  - [MmkvManager.encodeSubscription] / [MmkvManager.decodeSubscription] persist the item
 *  - [AngConfigManager.updateConfigViaSub] fetches + parses the servers (no new parsing)
 *  - [SubscriptionUpdater.syncOne] / [SubscriptionUpdater.cancelOne] handle periodic refresh
 *
 * The guid we own is stored in [AuthTokenStore]. Because MmkvManager.encodeSubscription does
 * not return the generated guid, we generate a stable one ourselves on first import.
 */
class SubscriptionSyncManager {

    /**
     * Imports (first time) or updates the managed subscription from [info], then triggers a
     * synchronous fetch/parse and schedules periodic auto-update.
     */
    suspend fun importOrUpdate(info: SubscriptionInfoDto) = withContext(Dispatchers.IO) {
        val existingGuid = AuthTokenStore.managedSubGuid()
        val guid = existingGuid.ifBlank { Utils.getUuid() }

        val item = (MmkvManager.decodeSubscription(guid) ?: SubscriptionItem()).apply {
            remarks = info.remarks?.ifBlank { null } ?: "Departament VPN"
            url = info.subscriptionUrl
            enabled = true
            autoUpdate = true
            userAgent = info.userAgent ?: BackendConfig.subscriptionUserAgent
        }

        MmkvManager.encodeSubscription(guid, item)
        AuthTokenStore.setManagedSubGuid(guid)

        // Fetch + parse the servers using existing machinery.
        AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
        // Schedule periodic refresh via WorkManager.
        SubscriptionUpdater.syncOne(subId = guid)
    }

    /** Removes the managed subscription and cancels its auto-update task. */
    fun removeManagedSubscription() {
        val guid = AuthTokenStore.managedSubGuid()
        if (guid.isBlank()) return
        SubscriptionUpdater.cancelOne(subId = guid)
        MmkvManager.removeSubscription(guid)
        AuthTokenStore.setManagedSubGuid("")
    }
}
