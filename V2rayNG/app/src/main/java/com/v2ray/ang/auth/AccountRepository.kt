package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.DevicesDto
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PaymentResultDto
import com.v2ray.ang.auth.dto.PaymentsDto
import com.v2ray.ang.auth.dto.PromoDto
import com.v2ray.ang.auth.dto.PublicConfigDto
import com.v2ray.ang.auth.dto.ReferralStatsDto
import com.v2ray.ang.auth.dto.ServerStatusDto
import com.v2ray.ang.auth.dto.SubscriptionAllDto
import com.v2ray.ang.auth.dto.TariffCatalogDto
import com.v2ray.ang.auth.dto.UpgradeQuoteDto
import com.v2ray.ang.auth.dto.UserProfileDto

/**
 * Coroutine wrapper over [DepartamentApiClient] that returns [Result] instead of throwing, maps
 * every failure to an [ApiError], and performs the higher-level account operations the UI needs.
 *
 * On [ApiError.Unauthorized] the local session is wiped via [AccountSession.wipe] so a stale JWT
 * self-heals into a logged-out state.
 */
class AccountRepository(
    private val api: DepartamentApiClient = DepartamentApiClientImpl(),
    private val subs: SubscriptionSyncManager = SubscriptionSyncManager(),
) {

    private suspend fun <T> guard(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: ApiError) {
            if (e is ApiError.Unauthorized) AccountSession.wipe()
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ApiError.Network(e))
        }
    }

    // Public catalog / status
    suspend fun loadPublicConfig(): Result<PublicConfigDto> = guard { api.getPublicConfig() }
    suspend fun loadCatalog(): Result<TariffCatalogDto> = guard { api.getPublicTariffs() }
    suspend fun loadServerStatus(): Result<List<ServerStatusDto>> = guard { api.getServerStatus() }

    // Profile
    suspend fun refreshProfile(): Result<UserProfileDto> = guard {
        val profile = api.getMe()
        AccountSession.updateProfile(profile)
        profile
    }

    // Subscriptions
    suspend fun loadSubscriptions(): Result<SubscriptionAllDto> = guard { api.getSubscriptionAll() }

    /**
     * Fetches all subscriptions and imports them into the local plumbing. Returns the local guids
     * so the caller (UI) can reload its server list.
     */
    suspend fun autoImportSubscriptions(): Result<List<String>> = guard {
        val all = api.getSubscriptionAll()
        subs.importAll(all.items)
    }

    suspend fun renameSubscription(scope: String, id: String, name: String): Result<Unit> =
        guard { api.renameSubscription(scope, id, name) }

    suspend fun getQr(remnawaveUuid: String): Result<ByteArray> = guard { api.getSubscriptionQr(remnawaveUuid) }

    // Purchase / renew / upgrade / devices
    suspend fun buy(req: PaymentRequestDto): Result<PaymentInitDto> = guard { api.payPlatega(req) }
    suspend fun payWithBalance(req: PaymentRequestDto): Result<PaymentResultDto> = guard { api.payBalance(req) }

    /** Renew is a Platega purchase of the given tariff/price-option for an existing subscription. */
    suspend fun renew(req: PaymentRequestDto): Result<PaymentInitDto> = guard { api.payPlatega(req) }

    suspend fun upgradeQuote(targetTariffId: String): Result<UpgradeQuoteDto> =
        guard { api.getUpgradeQuote(targetTariffId) }

    suspend fun upgrade(
        targetTariffId: String,
        method: String,
        paymentMethod: String? = null,
        subscriptionUuid: String,
    ): Result<PaymentInitDto> = guard { api.upgrade(targetTariffId, method, paymentMethod, subscriptionUuid) }

    suspend fun addDevices(
        scope: String,
        id: String,
        extraDevices: Int,
        method: String,
        paymentMethod: String? = null,
    ): Result<PaymentInitDto> = guard { api.addDevices(scope, id, extraDevices, method, paymentMethod) }

    suspend fun getDevices(remnawaveUuid: String): Result<DevicesDto> = guard { api.getDevices(remnawaveUuid) }
    suspend fun deleteDevice(hwid: String, remnawaveUuid: String): Result<Unit> =
        guard { api.deleteDevice(hwid, remnawaveUuid) }

    // Payments history
    suspend fun getPayments(): Result<PaymentsDto> = guard { api.getPayments() }

    // Promo / trial / auto-renew / referral
    suspend fun checkPromo(code: String): Result<PromoDto> = guard { api.checkPromo(code) }
    suspend fun activatePromo(code: String): Result<Unit> = guard { api.activatePromo(code) }
    suspend fun activateTrial(): Result<Unit> = guard { api.activateTrial() }
    suspend fun toggleAutoRenew(id: String, autoRenew: Boolean): Result<Unit> =
        guard { api.setSecondaryAutoRenew(id, autoRenew) }
    suspend fun getReferralStats(): Result<ReferralStatsDto> = guard { api.getReferralStats() }
}
