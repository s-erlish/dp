package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.auth.AccountCache
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.AccountSession
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PrimarySubscriptionDto
import com.v2ray.ang.auth.dto.PromoDto
import com.v2ray.ang.auth.dto.PublicConfigDto
import com.v2ray.ang.auth.dto.ServerStatusDto
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.TariffGroupDto
import com.v2ray.ang.auth.dto.UserProfileDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the account/subscriptions/store/payments screens. Holds the observable data as
 * [StateFlow]s and delegates every action to [AccountRepository]; errors surface via [error].
 */
class AccountViewModel : ViewModel() {

    private val repo = AccountRepository()

    /** Logged-in/out state (seeded from the persisted session). */
    val account: StateFlow<AccountSession.AccountState> = AccountSession.state

    private val _profile = MutableStateFlow<UserProfileDto?>(null)
    val profile: StateFlow<UserProfileDto?> = _profile.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubInfoDto>>(emptyList())
    val subscriptions: StateFlow<List<SubInfoDto>> = _subscriptions.asStateFlow()

    // Cache of the last subscription fetch so we can re-merge the synthesized root when the profile
    // (which supplies the root's auto-renew flag + remnawave uuid) arrives after the sub list.
    private var lastPrimary: PrimarySubscriptionDto? = null
    private var lastAll: List<SubInfoDto> = emptyList()
    private var hasSubData = false

    // Single-flight ownership for subscription loads: [loadSubscriptions] is polled every ~8s and
    // must not race an older in-flight load and publish stale state. Cancel the previous before
    // launching the next (latest-wins).
    private var subsJob: Job? = null

    private val _tariffs = MutableStateFlow<List<TariffGroupDto>>(emptyList())
    val tariffs: StateFlow<List<TariffGroupDto>> = _tariffs.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentDto>>(emptyList())
    val payments: StateFlow<List<PaymentDto>> = _payments.asStateFlow()

    // Live connected-device count for the ACTIVE subscription, from GET /client/devices (its
    // total). /subscription/all reports 0 here, so the account card reads this instead. Null until
    // first resolved. Latest-wins via [devicesJob].
    private val _deviceCount = MutableStateFlow<Int?>(null)
    val deviceCount: StateFlow<Int?> = _deviceCount.asStateFlow()

    private var devicesJob: Job? = null

    private val _publicConfig = MutableStateFlow<PublicConfigDto?>(null)
    val publicConfig: StateFlow<PublicConfigDto?> = _publicConfig.asStateFlow()

    private val _serverStatus = MutableStateFlow<List<ServerStatusDto>>(emptyList())
    val serverStatus: StateFlow<List<ServerStatusDto>> = _serverStatus.asStateFlow()

    /** Local guids of the imported subscriptions after [autoImportSubscriptions]. */
    private val _importedGuids = MutableStateFlow<List<String>>(emptyList())
    val importedGuids: StateFlow<List<String>> = _importedGuids.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    private fun report(t: Throwable?) {
        _error.value = t as? ApiError ?: t?.let { ApiError.Network(it) }
    }

    // region loads

    fun refreshProfile() = viewModelScope.launch {
        repo.refreshProfile()
            .onSuccess {
                _profile.value = it
                // Re-merge so the active/root sub reflects the profile's auto-renew flag + uuid,
                // even if the profile finished loading after the subscription list.
                if (hasSubData) _subscriptions.value = mergeSubscriptions(lastPrimary, lastAll, it)
            }
            .onFailure { report(it) }
    }

    /**
     * Loads the account's subscriptions for the Account screen. The `/subscription/all` list does
     * NOT carry the user's primary/active subscription in every case (an account with only a
     * primary sub and no secondary subs gets `items: []`), which is why the tab used to show
     * "нет активной подписки" for a genuinely-active account. We therefore ALSO fetch the
     * authoritative primary subscription (`/client/subscription`) and merge it in, so the active
     * sub always renders with its real name/expiry/devices/auto-renew state and connect payload.
     */
    fun loadSubscriptions() {
        // Latest-wins: cancel any in-flight load before starting a new one so a slow older request
        // can't finish late and publish stale state over a newer one.
        subsJob?.cancel()
        subsJob = viewModelScope.launch { fetchAndApplySubscriptions() }
    }

    /**
     * Fetches `/subscription/all` plus the authoritative primary subscription and publishes the
     * merged list via [applyMerged]. The single code path shared by the polled [loadSubscriptions]
     * and [autoImportSubscriptions], so both always render the active/primary sub (never the raw
     * un-merged `/all` list, and never a "нет активной подписки" flash).
     */
    private suspend fun fetchAndApplySubscriptions() {
        val allResult = repo.loadSubscriptions()
        val primaryResult = repo.loadPrimarySubscription()

        val all = allResult.getOrNull()?.items ?: emptyList()
        val primary = primaryResult.getOrNull()
        val merged = mergeSubscriptions(primary, all, _profile.value)

        if (merged.isNotEmpty() || allResult.isSuccess) {
            applyMerged(primary, all, _profile.value)
        } else {
            // Both calls failed and we have nothing to show: surface the primary error.
            allResult.exceptionOrNull()?.let { report(it) }
                ?: primaryResult.exceptionOrNull()?.let { report(it) }
        }
    }

    /**
     * The single place that publishes the displayed subscription list: caches the inputs
     * ([lastPrimary]/[lastAll]/[hasSubData]) so [refreshProfile] can re-merge later, and pushes the
     * merged list to [subscriptions].
     */
    private fun applyMerged(
        primary: PrimarySubscriptionDto?,
        all: List<SubInfoDto>,
        profile: UserProfileDto?,
    ) {
        lastPrimary = primary
        lastAll = all
        hasSubData = true
        _subscriptions.value = mergeSubscriptions(primary, all, profile)
    }

    /**
     * Builds the list the Account screen consumes: the active/root subscription first (enriched
     * from the primary payload when present), then the secondary subscriptions from `/all`.
     */
    private fun mergeSubscriptions(
        primary: PrimarySubscriptionDto?,
        all: List<SubInfoDto>,
        profile: UserProfileDto?,
    ): List<SubInfoDto> {
        val rootFromAll = all.firstOrNull { it.type.equals("root", ignoreCase = true) }
        val secondaries = all.filter { !it.type.equals("root", ignoreCase = true) }

        val activeRoot: SubInfoDto? = when {
            primary?.hasActiveSubscription() == true -> buildRootSub(primary, rootFromAll, profile)
            rootFromAll != null -> rootFromAll
            else -> null
        }
        // Dedup by non-blank id (a synthesized root can have a blank id and must be kept).
        val ordered = listOfNotNull(activeRoot) + secondaries
        val seen = HashSet<String>()
        return ordered.filter { it.id.isBlank() || seen.add(it.id) }
    }

    /**
     * Synthesizes/enriches the root [SubInfoDto] from the primary payload. Display + connect data
     * (tariff name, expiry, device limit, subscription URL) come from the primary's raw remnawave
     * record; the action ids (subscription id for auto-renew, tariff/price-option for renew) live
     * only on the `/all` root entry; the root's auto-renew flag and remnawave uuid live on the
     * profile — mirroring how the web cabinet composes the same card.
     */
    private fun buildRootSub(
        primary: PrimarySubscriptionDto,
        rootFromAll: SubInfoDto?,
        profile: UserProfileDto?,
    ): SubInfoDto {
        val raw = primary.raw()
        return SubInfoDto(
            type = "root",
            // Auto-renew / renew target the id from /all; blank when /all has no root entry.
            id = rootFromAll?.id.orEmpty(),
            remnawaveUuid = profile?.remnawaveUuid?.takeIf { it.isNotBlank() }
                ?: rootFromAll?.remnawaveUuid.orEmpty(),
            // Carry the raw record so import (subscriptionUrl) and the unlimited-devices check work.
            subscription = primary.subscription,
            tariffDisplayName = primary.tariffDisplayName?.takeIf { it.isNotBlank() }
                ?: rootFromAll?.tariffDisplayName,
            displayName = rootFromAll?.displayName,
            defaultLabel = rootFromAll?.defaultLabel,
            subscriptionIndex = rootFromAll?.subscriptionIndex,
            // Prefer the /all root's tariff id (it also drives renew/upgrade), but fall back to the
            // PRIMARY payload's own tariff id when /all has no root (a primary-only account). Without
            // this the badge lost the tariff identity and mis-resolved to a stale product label.
            tariffId = rootFromAll?.tariffId?.takeIf { it.isNotBlank() }
                ?: primary.activeTariffId(),
            tariffPriceOptionId = rootFromAll?.tariffPriceOptionId,
            deviceCount = rootFromAll?.deviceCount ?: 0,
            totalDevices = rootFromAll?.totalDevices
                ?: raw?.hwidDeviceLimit?.takeIf { it > 0 } ?: 0,
            connectedDevices = rootFromAll?.connectedDevices ?: 0,
            // The root sub's auto-renew is exposed on the profile (as in the web cabinet).
            autoRenewEnabled = profile?.autoRenewEnabled ?: rootFromAll?.autoRenewEnabled ?: false,
            expireAtIso = raw?.expireAt?.takeIf { it.isNotBlank() } ?: rootFromAll?.expireAtIso,
            isTrial = rootFromAll?.isTrial ?: false,
            tariffPrice = rootFromAll?.tariffPrice,
            tariffCurrency = primary.autoRenewCurrency?.takeIf { it.isNotBlank() }
                ?: rootFromAll?.tariffCurrency,
            renewalPrice = primary.autoRenewNextChargeAmount ?: rootFromAll?.renewalPrice,
        )
    }

    fun loadTariffs() = viewModelScope.launch {
        repo.loadCatalog()
            .onSuccess { _tariffs.value = it.items }
            .onFailure { report(it) }
    }

    /**
     * Resolves a tariff's display name ("Base" / "Plus") from its [tariffId] against the loaded
     * catalog ([tariffs]). Returns null when the catalog isn't loaded yet or the id doesn't match,
     * so callers can fall back to the sub's own product name.
     */
    fun tariffNameFor(tariffId: String?): String? {
        if (tariffId.isNullOrBlank()) return null
        return _tariffs.value.asSequence()
            .flatMap { it.tariffs.asSequence() }
            .firstOrNull { it.id == tariffId }
            ?.name
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves a tariff's display name ("Base" / "Plus") from the PRICE-OPTION id the subscription
     * renews on ([SubInfoDto.tariffPriceOptionId]) against the loaded catalog. Used as the second
     * badge source when [tariffNameFor] can't resolve because the sub carries no [tariffId]: the
     * price-option id reflects the option the sub CURRENTLY renews on, so it points at the real
     * current tariff even after a Base→Plus upgrade (unlike the stale provisioning product name).
     * Returns null when the id is blank, the catalog isn't loaded yet, or nothing matches.
     */
    fun tariffNameForPriceOptionId(priceOptionId: String?): String? {
        if (priceOptionId.isNullOrBlank()) return null
        return _tariffs.value.asSequence()
            .flatMap { it.tariffs.asSequence() }
            .firstOrNull { tariff -> tariff.priceOptions.any { it.id == priceOptionId } }
            ?.name
            ?.takeIf { it.isNotBlank() }
    }

    fun loadPayments() = viewModelScope.launch {
        repo.getPayments()
            .onSuccess {
                _payments.value = it.items
                // Warm the process-wide cache so PaymentHistoryActivity (a separate ViewModel
                // instance) renders instantly instead of spinning through a fresh network load.
                AccountCache.putPayments(it.items)
            }
            .onFailure { report(it) }
    }

    /**
     * Resolves the ACTIVE subscription's connected-device count from GET /client/devices (its
     * total = list size) and publishes it via [deviceCount]. Cache-first: a fresh (< 1h)
     * [AccountCache] entry is used immediately with no network call; a miss fetches and repopulates
     * the cache, so the Devices sub-screen also opens instantly (pre-warm). A device-fetch failure
     * is swallowed on purpose — the count is secondary and must not pop an error toast on the
     * Account tab; the sub-screen surfaces its own diagnostics.
     */
    fun loadDevices(uuid: String) {
        if (uuid.isBlank()) return
        val cached = AccountCache.getDevices(uuid)
        if (cached != null) {
            _deviceCount.value = cached.size
            return
        }
        devicesJob?.cancel()
        devicesJob = viewModelScope.launch {
            repo.getDevices(uuid)
                .onSuccess {
                    AccountCache.putDevices(uuid, it.devices)
                    _deviceCount.value = it.devices.size
                }
                .onFailure { /* secondary data: keep last known count, no error toast */ }
        }
    }

    fun loadPublicConfig() = viewModelScope.launch {
        repo.loadPublicConfig()
            .onSuccess { _publicConfig.value = it }
            .onFailure { report(it) }
    }

    fun loadServerStatus() = viewModelScope.launch {
        repo.loadServerStatus()
            .onSuccess { _serverStatus.value = it }
            .onFailure { report(it) }
    }

    /** Fetch + import all subscriptions; publishes the local guids and refreshes the sub list. */
    fun autoImportSubscriptions(onImported: (List<String>) -> Unit = {}) = viewModelScope.launch {
        _loading.value = true
        repo.autoImportSubscriptions()
            .onSuccess {
                _importedGuids.value = it
                onImported(it)
            }
            .onFailure { report(it) }
        // Publish through the same merge path as loadSubscriptions so the active/primary sub
        // renders (never the raw un-merged /all list) and lastPrimary/lastAll/hasSubData stay set.
        fetchAndApplySubscriptions()
        _loading.value = false
    }

    // endregion

    // region actions

    fun buy(req: PaymentRequestDto, onInit: (PaymentInitDto) -> Unit) = viewModelScope.launch {
        repo.buy(req).onSuccess { onInit(it) }.onFailure { report(it) }
    }

    fun payWithBalance(req: PaymentRequestDto, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.payWithBalance(req).onSuccess { onDone() }.onFailure { report(it) }
    }

    fun upgrade(
        targetTariffId: String,
        method: String,
        subscriptionUuid: String,
        paymentMethod: String? = null,
        onInit: (PaymentInitDto) -> Unit,
    ) = viewModelScope.launch {
        repo.upgrade(targetTariffId, method, paymentMethod, subscriptionUuid)
            .onSuccess { onInit(it) }
            .onFailure { report(it) }
    }

    fun addDevices(
        scope: String,
        id: String,
        extraDevices: Int,
        method: String,
        paymentMethod: String? = null,
        onInit: (PaymentInitDto) -> Unit,
    ) = viewModelScope.launch {
        repo.addDevices(scope, id, extraDevices, method, paymentMethod)
            .onSuccess { onInit(it) }
            .onFailure { report(it) }
    }

    fun checkPromo(code: String, onResult: (PromoDto) -> Unit) = viewModelScope.launch {
        repo.checkPromo(code).onSuccess { onResult(it) }.onFailure { report(it) }
    }

    fun toggleAutoRenew(
        id: String,
        autoRenew: Boolean,
        onError: (ApiError) -> Unit = { report(it) },
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        repo.toggleAutoRenew(id, autoRenew)
            .onSuccess { onDone() }
            .onFailure { t -> onError(t as? ApiError ?: ApiError.Network(t)) }
    }

    /** Auto-renew for the active (root/primary) subscription — hits the id-less primary endpoint. */
    fun togglePrimaryAutoRenew(
        autoRenew: Boolean,
        onError: (ApiError) -> Unit = { report(it) },
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        repo.togglePrimaryAutoRenew(autoRenew)
            .onSuccess { onDone() }
            .onFailure { t -> onError(t as? ApiError ?: ApiError.Network(t)) }
    }

    fun activateTrial(onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.activateTrial().onSuccess { onDone() }.onFailure { report(it) }
    }

    fun renameSubscription(scope: String, id: String, name: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.renameSubscription(scope, id, name).onSuccess { onDone() }.onFailure { report(it) }
    }

    fun logout() {
        AccountSession.wipe()
        // Hard reset: clear the in-memory cache eagerly rather than relying only on the lazy
        // logged-out clear on next read, and stop any in-flight subscription load.
        AccountCache.invalidateAll()
        subsJob?.cancel()
        subsJob = null
        devicesJob?.cancel()
        devicesJob = null
        _profile.value = null
        _subscriptions.value = emptyList()
        lastPrimary = null
        lastAll = emptyList()
        hasSubData = false
        _payments.value = emptyList()
        _deviceCount.value = null
        _importedGuids.value = emptyList()
    }

    // endregion
}
