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
import com.v2ray.ang.auth.dto.RegisterResult
import com.v2ray.ang.auth.dto.ServerStatusDto
import com.v2ray.ang.auth.dto.PrimarySubscriptionDto
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
    suspend fun register(email: String, password: String, referralCode: String? = null): RegisterResult
    suspend fun login(email: String, password: String): LoginResult
    suspend fun login2fa(tempToken: String, code: String): AuthResult
    suspend fun loginGoogle(idToken: String, referralCode: String? = null): AuthResult
    suspend fun getMe(): UserProfileDto

    /**
     * Sends the «привяжите эту почту» letter to [email] for the session already signed in. Returns
     * on 200 (the letter is out) and throws [ApiError] otherwise, carrying the panel's own sentence
     * — «Почта уже привязана», «Эта почта уже используется другим аккаунтом» — which is the only
     * description of the refusal that exists. There is no confirmation call: the link lands on the
     * site, and [getMe] is what tells the app it was opened.
     */
    suspend fun linkEmailRequest(email: String)

    // Subscription
    suspend fun getPrimarySubscription(): PrimarySubscriptionDto
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
    suspend fun setPrimaryAutoRenew(autoRenew: Boolean)
    suspend fun getReferralStats(): ReferralStatsDto
}
