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

    companion object {
        /** The `{scope}` value of the account's single active subscription. */
        const val TYPE_ROOT = "root"
    }

    /**
     * Imports/updates every subscription in [items], removes locally any managed subscription no
     * longer present remotely, and returns the local guids of the current managed set (so the UI
     * can reload its server list).
     *
     * [items] must be the merged candidate list from [AccountRepository.autoImportSubscriptions],
     * not the raw `/client/subscription/all` payload: `/all` items carry no connect payload, so
     * every one of them would be skipped here.
     */
    suspend fun importAll(items: List<SubInfoDto>): List<String> = withContext(Dispatchers.IO) {
        val managed = AuthTokenStore.getManagedGuids()
        val newMap = HashMap<String, String>()
        val resultGuids = ArrayList<String>()

        for (info in items) {
            // .raw(), not .response: the backend nests the Remnawave record under `response` or
            // under `data.response` depending on the endpoint, and the wrapper is what knows both.
            // Reading one field directly silently skipped every item served in the other shape.
            val raw = info.subscription?.raw() ?: continue
            val url = raw.subscriptionUrl
            if (url.isBlank()) continue

            val uuid = identityOf(info, url)
            val guid = managed[uuid]?.ifBlank { null } ?: Utils.getUuid()

            val item = (MmkvManager.decodeSubscription(guid) ?: SubscriptionItem()).apply {
                remarks = info.displayName?.ifBlank { null }
                    ?: info.tariffDisplayName?.ifBlank { null }
                    ?: "Departament VPN"
                this.url = url
                enabled = true
                autoUpdate = true
                // No per-subscription User-Agent is stamped here on purpose. The fetch resolves
                // per-sub -> global (provider screen) -> operator default itself, and the per-sub
                // tier wins absolutely — so stamping the operator default made the provider
                // screen's User-Agent row dead UI for exactly the subscriptions this deployment
                // creates, and overwrote a value typed in SubEditActivity on every sign-in and
                // account refresh. Leaving it unset resolves to the same operator default, but
                // now an override can actually reach the request.
                //
                // Earlier builds did stamp it, and that stamp is still there on every upgraded
                // install — sitting in the tier that wins absolutely, which is what defeated the
                // fix above for exactly the users who already had the app. It was written by an
                // OLDER build, so it cannot be recognised by comparing against today's resolved
                // default; [BackendConfig.isAppStampedUserAgent] knows every string this app has
                // ever stamped. Anything else was typed by the user and is kept.
                userAgent = userAgent?.takeIf { !BackendConfig.isAppStampedUserAgent(it) }
            }

            MmkvManager.encodeSubscription(guid, item)
            // Fetch first, schedule second — the order is load-bearing. A successful fetch stamps
            // `lastUpdated`, and syncOne derives the worker's initial delay from it, so the timer
            // starts a full interval out. Reversed, that delay would be 0 and the periodic worker
            // would fire at once, pulling the same subscription twice on every sign-in. A failed
            // fetch leaves `lastUpdated` untouched and the worker does run promptly — that retry is
            // the point, not the duplicate above.
            AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
            SubscriptionUpdater.syncOne(subId = guid)

            newMap[uuid] = guid
            resultGuids.add(guid)
        }

        // Drop any previously managed subscription that is gone remotely — but only on the strength
        // of a run that actually saw subscriptions. An empty result is not evidence that the
        // account has none: a payload shape we could not read, a partial outage, or a signed-in
        // account whose connect payload simply did not arrive all produce the same empty map, and
        // pruning on that deletes every подписка and every сервер the user has. Nothing is
        // recoverable from the device afterwards. When in doubt, keep.
        if (newMap.isNotEmpty()) {
            for ((uuid, guid) in managed) {
                if (!newMap.containsKey(uuid)) {
                    SubscriptionUpdater.cancelOne(subId = guid)
                    MmkvManager.removeSubscription(guid)
                }
            }
            AuthTokenStore.setManagedGuids(newMap)
        }
        resultGuids
    }

    /**
     * The key this subscription is remembered under in the uuid->guid map, i.e. what makes a
     * re-import an UPDATE of the same провайдер instead of a second copy of it.
     *
     * The root subscription is keyed by a constant, because an account has exactly one and the two
     * endpoints that describe it do not agree on an identifier: `/subscription/all` carries an id,
     * the `/subscription` summary carries none, and the merge synthesises the root from whichever
     * answered. Keying it by "whatever identifier arrived" therefore made the SAME подписка look
     * like a different one the moment `/all` was the endpoint that failed — the import re-added it
     * under a fresh guid, and the prune below then deleted the original, taking that провайдер's
     * серверы and the selected one with it. Surviving a partial outage is the whole point of asking
     * both endpoints; it cannot be paid for with the user's server list.
     *
     * Secondaries keep their own identity: `/all` is their only source and it always carries the id.
     */
    private fun identityOf(info: SubInfoDto, url: String): String {
        if (info.type.equals(TYPE_ROOT, ignoreCase = true)) return TYPE_ROOT
        return info.remnawaveUuid.ifBlank { info.id }.ifBlank { url }
    }

    /**
     * Removes every managed subscription and cancels their auto-update tasks. Invoked only from
     * [AccountSession.wipe], i.e. an explicit user logout.
     *
     * A dead JWT deliberately does NOT come through here any more: an expired 7-day token is not
     * the user asking to give up their подписки, and treating it as one deleted every сервер on the
     * device the first time the Аккаунт tab noticed. That path is [AccountSession.endSession].
     */
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
