package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.auth.AccountRepository
import com.v2ray.ang.auth.AccountSession
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.dto.PaymentDto
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PromoDto
import com.v2ray.ang.auth.dto.PublicConfigDto
import com.v2ray.ang.auth.dto.ServerStatusDto
import com.v2ray.ang.auth.dto.SubInfoDto
import com.v2ray.ang.auth.dto.TariffGroupDto
import com.v2ray.ang.auth.dto.UserProfileDto
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

    private val _tariffs = MutableStateFlow<List<TariffGroupDto>>(emptyList())
    val tariffs: StateFlow<List<TariffGroupDto>> = _tariffs.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentDto>>(emptyList())
    val payments: StateFlow<List<PaymentDto>> = _payments.asStateFlow()

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
            .onSuccess { _profile.value = it }
            .onFailure { report(it) }
    }

    fun loadSubscriptions() = viewModelScope.launch {
        repo.loadSubscriptions()
            .onSuccess { _subscriptions.value = it.items }
            .onFailure { report(it) }
    }

    fun loadTariffs() = viewModelScope.launch {
        repo.loadCatalog()
            .onSuccess { _tariffs.value = it.items }
            .onFailure { report(it) }
    }

    fun loadPayments() = viewModelScope.launch {
        repo.getPayments()
            .onSuccess { _payments.value = it.items }
            .onFailure { report(it) }
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
        repo.loadSubscriptions().onSuccess { _subscriptions.value = it.items }
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

    fun toggleAutoRenew(id: String, autoRenew: Boolean, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.toggleAutoRenew(id, autoRenew).onSuccess { onDone() }.onFailure { report(it) }
    }

    fun activateTrial(onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.activateTrial().onSuccess { onDone() }.onFailure { report(it) }
    }

    fun renameSubscription(scope: String, id: String, name: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.renameSubscription(scope, id, name).onSuccess { onDone() }.onFailure { report(it) }
    }

    fun logout() {
        AccountSession.wipe()
        _profile.value = null
        _subscriptions.value = emptyList()
        _payments.value = emptyList()
        _importedGuids.value = emptyList()
    }

    // endregion
}
