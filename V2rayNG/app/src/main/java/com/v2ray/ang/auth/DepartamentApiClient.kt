package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.AuthResult
import com.v2ray.ang.auth.dto.DevicesResult
import com.v2ray.ang.auth.dto.LoginResult
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
import com.v2ray.ang.auth.dto.TelegramCheckResult
import com.v2ray.ang.auth.dto.TelegramTokenDto
import com.v2ray.ang.auth.dto.UpgradeQuoteDto
import com.v2ray.ang.auth.dto.UserProfileDto

/**
 * Pluggable client for the Departament backend. Every method runs on Dispatchers.IO in the
 * implementation and throws [ApiError] on failure (including [ApiError.NotConfigured] when the
 * backend base URL is blank). The JWT is injected by the OkHttp interceptor, so no token param.
 */
interface DepartamentApiClient {

    // Public
    suspend fun getPublicConfig(): PublicConfigDto
    suspend fun getPublicTariffs(): TariffCatalogDto
    suspend fun getServerStatus(): List<ServerStatusDto>

    // Auth
    suspend fun createTelegramLoginToken(): TelegramTokenDto
    suspend fun checkTelegramLogin(token: String): TelegramCheckResult
    suspend fun login(email: String, password: String): LoginResult
    suspend fun login2fa(tempToken: String, code: String): AuthResult
    suspend fun loginGoogle(idToken: String, referralCode: String? = null): AuthResult
    suspend fun getMe(): UserProfileDto

    // Subscription
    suspend fun getSubscriptionAll(): SubscriptionAllDto
    suspend fun renameSubscription(scope: String, id: String, name: String)
    suspend fun getSubscriptionQr(remnawaveUuid: String): ByteArray
    suspend fun addDevices(scope: String, id: String, extraDevices: Int, method: String, paymentMethod: String? = null): PaymentInitDto
    suspend fun getUpgradeQuote(targetTariffId: String): UpgradeQuoteDto
    suspend fun upgrade(targetTariffId: String, method: String, paymentMethod: String? = null, subscriptionUuid: String): PaymentInitDto

    // Devices
    suspend fun getDevices(remnawaveUuid: String): DevicesResult
    suspend fun deleteDevice(hwid: String, remnawaveUuid: String)

    // Payments
    suspend fun payPlatega(req: PaymentRequestDto): PaymentInitDto
    suspend fun payBalance(req: PaymentRequestDto): PaymentResultDto
    suspend fun getPayments(): PaymentsDto

    // Promo / trial / referral
    suspend fun checkPromo(code: String): PromoDto
    suspend fun activatePromo(code: String)
    suspend fun activateTrial()
    suspend fun setSecondaryAutoRenew(id: String, autoRenew: Boolean)
    suspend fun getReferralStats(): ReferralStatsDto
}
