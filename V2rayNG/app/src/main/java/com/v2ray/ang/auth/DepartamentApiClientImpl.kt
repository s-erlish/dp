package com.v2ray.ang.auth

import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.auth.dto.AddDevicesRequestDto
import com.v2ray.ang.auth.dto.AuthResult
import com.v2ray.ang.auth.dto.AutoRenewRequestDto
import com.v2ray.ang.auth.dto.DeleteDeviceRequestDto
import com.v2ray.ang.auth.dto.DevicesDto
import com.v2ray.ang.auth.dto.DevicesResult
import com.v2ray.ang.auth.dto.GoogleLoginRequestDto
import com.v2ray.ang.auth.dto.LoginRequestDto
import com.v2ray.ang.auth.dto.LoginResponseDto
import com.v2ray.ang.auth.dto.LoginResult
import com.v2ray.ang.auth.dto.PaymentInitDto
import com.v2ray.ang.auth.dto.PaymentRequestDto
import com.v2ray.ang.auth.dto.PaymentResultDto
import com.v2ray.ang.auth.dto.PaymentsDto
import com.v2ray.ang.auth.dto.PromoDto
import com.v2ray.ang.auth.dto.PromoRequestDto
import com.v2ray.ang.auth.dto.PublicConfigDto
import com.v2ray.ang.auth.dto.ReferralStatsDto
import com.v2ray.ang.auth.dto.RenameRequestDto
import com.v2ray.ang.auth.dto.ServerStatusDto
import com.v2ray.ang.auth.dto.SubscriptionAllDto
import com.v2ray.ang.auth.dto.TariffCatalogDto
import com.v2ray.ang.auth.dto.TelegramCheckResponseDto
import com.v2ray.ang.auth.dto.TelegramCheckResult
import com.v2ray.ang.auth.dto.TelegramTokenDto
import com.v2ray.ang.auth.dto.TwoFaLoginRequestDto
import com.v2ray.ang.auth.dto.UpgradeQuoteDto
import com.v2ray.ang.auth.dto.UpgradeRequestDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * OkHttp + Gson implementation of [DepartamentApiClient].
 *
 * A single request interceptor attaches Accept, User-Agent, the Bearer JWT (from
 * [AuthTokenStore]) and an optional X-HWID header. Every failure is mapped to an [ApiError];
 * tokens and subscription URLs are never logged. All calls throw [ApiError.NotConfigured]
 * when the backend base URL is blank, so the whole layer is inert until configured.
 */
class DepartamentApiClientImpl(
    // Null-tolerant Gson: the backend may send JSON null for non-null Kotlin String fields
    // (e.g. a Telegram-only user has no email). ApiGson maps those to "" to avoid runtime NPEs.
    private val gson: Gson = ApiGson.instance,
    private val http: OkHttpClient = defaultClient(),
) : DepartamentApiClient {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", BackendConfig.subscriptionUserAgent)
            AuthTokenStore.getToken()?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            if (SettingsManager.isSendHwid()) {
                // Stable per-install HWID + real, stable device model so the panel keeps ONE
                // device entry per physical device and labels it with the actual model.
                builder.header(AppConfig.HEADER_HWID, AuthTokenStore.deviceId())
                builder.header(AppConfig.HEADER_DEVICE_OS, "android")
                builder.header(AppConfig.HEADER_VER_OS, Build.VERSION.RELEASE ?: "")
                builder.header(AppConfig.HEADER_DEVICE_MODEL, Utils.getDeviceName())
            }
            chain.proceed(builder.build())
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()
    }

    // region public

    override suspend fun getPublicConfig(): PublicConfigDto =
        getJson(BackendConfig.Endpoints.publicConfig, PublicConfigDto::class.java)

    override suspend fun getPublicTariffs(): TariffCatalogDto =
        getJson(BackendConfig.Endpoints.publicTariffs, TariffCatalogDto::class.java)

    override suspend fun getServerStatus(): List<ServerStatusDto> {
        val type = object : com.google.gson.reflect.TypeToken<List<ServerStatusDto>>() {}.type
        return getJsonType(BackendConfig.Endpoints.serverStatus, type)
    }

    // endregion

    // region auth

    override suspend fun createTelegramLoginToken(): TelegramTokenDto =
        postJson(BackendConfig.Endpoints.telegramLoginToken, "{}", TelegramTokenDto::class.java)

    override suspend fun checkTelegramLogin(token: String): TelegramCheckResult {
        ensureConfigured()
        val url = urlOf(BackendConfig.Endpoints.telegramLoginCheck)
            .addQueryParameter("token", token)
            .build()
        val resp = execute(Request.Builder().url(url).get().build())
        resp.use {
            return when (it.code) {
                404 -> TelegramCheckResult.NotYet
                410 -> TelegramCheckResult.Expired
                in 200..299 -> {
                    val raw = parse(it.body?.string().orEmpty(), TelegramCheckResponseDto::class.java)
                    val jwt = raw.token
                    val client = raw.client
                    if (raw.confirmed && !jwt.isNullOrBlank() && client != null) {
                        TelegramCheckResult.Confirmed(jwt, client, raw.justCreated)
                    } else {
                        TelegramCheckResult.NotYet
                    }
                }
                else -> throw mapError(it.code)
            }
        }
    }

    override suspend fun login(email: String, password: String): LoginResult {
        val raw = postJson(BackendConfig.Endpoints.login, gson.toJson(LoginRequestDto(email, password)), LoginResponseDto::class.java)
        val tempToken = raw.tempToken
        val token = raw.token
        val client = raw.client
        return when {
            raw.requires2FA && !tempToken.isNullOrBlank() -> LoginResult.Requires2FA(tempToken)
            !token.isNullOrBlank() && client != null -> LoginResult.Success(token, client)
            else -> throw ApiError.Parse()
        }
    }

    override suspend fun login2fa(tempToken: String, code: String): AuthResult =
        postJson(BackendConfig.Endpoints.twoFaLogin, gson.toJson(TwoFaLoginRequestDto(tempToken, code)), AuthResult::class.java)

    override suspend fun loginGoogle(idToken: String, referralCode: String?): AuthResult =
        postJson(BackendConfig.Endpoints.googleLogin, gson.toJson(GoogleLoginRequestDto(idToken, referralCode)), AuthResult::class.java)

    override suspend fun getMe(): UserProfileDto =
        getJson(BackendConfig.Endpoints.me, UserProfileDto::class.java)

    // endregion

    // region subscription

    override suspend fun getSubscriptionAll(): SubscriptionAllDto =
        getJson(BackendConfig.Endpoints.subscriptionAll, SubscriptionAllDto::class.java)

    override suspend fun renameSubscription(scope: String, id: String, name: String) {
        ensureConfigured()
        val body = gson.toJson(RenameRequestDto(name)).toRequestBody(JSON)
        val req = Request.Builder().url(urlOf(BackendConfig.Endpoints.renameSubscription(scope, id)).build()).patch(body).build()
        executeVoid(req)
    }

    override suspend fun getSubscriptionQr(remnawaveUuid: String): ByteArray {
        ensureConfigured()
        val url = urlOf(BackendConfig.Endpoints.subscriptionQr).addQueryParameter("uuid", remnawaveUuid).build()
        val resp = execute(Request.Builder().url(url).get().build())
        resp.use {
            if (!it.isSuccessful) throw mapError(it.code)
            return it.body?.bytes() ?: throw ApiError.Parse()
        }
    }

    override suspend fun addDevices(scope: String, id: String, extraDevices: Int, method: String, paymentMethod: String?): PaymentInitDto {
        val json = gson.toJson(AddDevicesRequestDto(extraDevices, method, paymentMethod))
        return postJson(BackendConfig.Endpoints.addDevices(scope, id), json, PaymentInitDto::class.java)
    }

    override suspend fun getUpgradeQuote(targetTariffId: String): UpgradeQuoteDto {
        ensureConfigured()
        val url = urlOf(BackendConfig.Endpoints.upgradeQuote).addQueryParameter("targetTariffId", targetTariffId).build()
        return call(Request.Builder().url(url).get().build(), UpgradeQuoteDto::class.java)
    }

    override suspend fun upgrade(targetTariffId: String, method: String, paymentMethod: String?, subscriptionUuid: String): PaymentInitDto {
        val json = gson.toJson(UpgradeRequestDto(targetTariffId, method, paymentMethod, subscriptionUuid))
        return postJson(BackendConfig.Endpoints.upgrade, json, PaymentInitDto::class.java)
    }

    // endregion

    // region devices

    override suspend fun getDevices(remnawaveUuid: String): DevicesResult {
        ensureConfigured()
        val url = urlOf(BackendConfig.Endpoints.devices).addQueryParameter("uuid", remnawaveUuid).build()
        val resp = execute(Request.Builder().url(url).get().build())
        resp.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapError(it.code, sanitizeBody(body))
            // Keep the raw (sanitized) body so the UI can surface a diagnostic when the parsed
            // list is empty but the subscription reports connected devices (shape mismatch).
            val devices = parse(body, DevicesDto::class.java).devices()
            return DevicesResult(devices, it.code, sanitizeBody(body).orEmpty())
        }
    }

    override suspend fun deleteDevice(hwid: String, remnawaveUuid: String) {
        ensureConfigured()
        val body = gson.toJson(DeleteDeviceRequestDto(hwid, remnawaveUuid)).toRequestBody(JSON)
        executeVoid(Request.Builder().url(urlOf(BackendConfig.Endpoints.deleteDevice).build()).post(body).build())
    }

    // endregion

    // region payments

    override suspend fun payPlatega(req: PaymentRequestDto): PaymentInitDto =
        postJson(BackendConfig.Endpoints.payPlatega, gson.toJson(req), PaymentInitDto::class.java)

    override suspend fun payBalance(req: PaymentRequestDto): PaymentResultDto =
        postJson(BackendConfig.Endpoints.payBalance, gson.toJson(req), PaymentResultDto::class.java)

    override suspend fun getPayments(): PaymentsDto =
        getJson(BackendConfig.Endpoints.payments, PaymentsDto::class.java)

    // endregion

    // region promo / trial / referral

    override suspend fun checkPromo(code: String): PromoDto =
        postJson(BackendConfig.Endpoints.promoCheck, gson.toJson(PromoRequestDto(code)), PromoDto::class.java)

    override suspend fun activatePromo(code: String) {
        ensureConfigured()
        val body = gson.toJson(PromoRequestDto(code)).toRequestBody(JSON)
        executeVoid(Request.Builder().url(urlOf(BackendConfig.Endpoints.promoActivate).build()).post(body).build())
    }

    override suspend fun activateTrial() {
        ensureConfigured()
        val body = "{}".toRequestBody(JSON)
        executeVoid(Request.Builder().url(urlOf(BackendConfig.Endpoints.trial).build()).post(body).build())
    }

    override suspend fun setSecondaryAutoRenew(id: String, autoRenew: Boolean) {
        ensureConfigured()
        val body = gson.toJson(AutoRenewRequestDto(autoRenew)).toRequestBody(JSON)
        executeVoid(Request.Builder().url(urlOf(BackendConfig.Endpoints.secondaryAutoRenew(id)).build()).patch(body).build())
    }

    override suspend fun getReferralStats(): ReferralStatsDto =
        getJson(BackendConfig.Endpoints.referralStats, ReferralStatsDto::class.java)

    // endregion

    // region internals

    private fun ensureConfigured() {
        if (!BackendConfig.isConfigured()) throw ApiError.NotConfigured
    }

    private fun urlOf(path: String) = (BackendConfig.baseUrl + path).toHttpUrl().newBuilder()

    private suspend fun <T> getJson(path: String, cls: Class<T>): T {
        ensureConfigured()
        return call(Request.Builder().url(urlOf(path).build()).get().build(), cls)
    }

    private suspend fun <T> getJsonType(path: String, type: Type): T {
        ensureConfigured()
        return callType(Request.Builder().url(urlOf(path).build()).get().build(), type)
    }

    private suspend fun <T> postJson(path: String, json: String, cls: Class<T>): T {
        ensureConfigured()
        val req = Request.Builder().url(urlOf(path).build()).post(json.toRequestBody(JSON)).build()
        return call(req, cls)
    }

    private suspend fun <T> call(request: Request, cls: Class<T>): T {
        val resp = execute(request)
        resp.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapError(it.code, sanitizeBody(body))
            return parse(body, cls)
        }
    }

    private suspend fun <T> callType(request: Request, type: Type): T {
        val resp = execute(request)
        resp.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapError(it.code, sanitizeBody(body))
            return parseType(body, type)
        }
    }

    private suspend fun executeVoid(request: Request) {
        val resp = execute(request)
        resp.use {
            if (!it.isSuccessful) throw mapError(it.code)
        }
    }

    /** Executes the call on IO, mapping transport failures to [ApiError]. Caller must close. */
    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw ApiError.Timeout
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }
    }

    private fun mapError(code: Int, detail: String? = null): ApiError = when (code) {
        401, 403 -> ApiError.Unauthorized(detail)
        404 -> ApiError.NotFound
        410 -> ApiError.Gone
        429 -> ApiError.RateLimited
        502, 503 -> ApiError.ServiceUnavailable
        else -> ApiError.Server(code, detail)
    }

    /**
     * Reduces an error response body to a short, screenshot-safe diagnostic snippet: drops any
     * line mentioning a token/authorization header or an http(s) URL, then caps at 300 chars.
     * Returns null when nothing usable remains.
     */
    private fun sanitizeBody(body: String): String? {
        if (body.isBlank()) return null
        val cleaned = body.lineSequence()
            .filterNot { line ->
                val l = line.lowercase()
                l.contains("token") || l.contains("authorization") ||
                    l.contains("http://") || l.contains("https://")
            }
            .joinToString("\n")
            .trim()
        if (cleaned.isBlank()) return null
        return if (cleaned.length > 300) cleaned.substring(0, 300) else cleaned
    }

    private fun <T> parse(body: String, cls: Class<T>): T {
        return try {
            gson.fromJson(body, cls) ?: throw ApiError.Parse()
        } catch (e: JsonSyntaxException) {
            throw ApiError.Parse(e)
        }
    }

    private fun <T> parseType(body: String, type: Type): T {
        return try {
            gson.fromJson<T>(body, type) ?: throw ApiError.Parse()
        } catch (e: JsonSyntaxException) {
            throw ApiError.Parse(e)
        }
    }

    // endregion
}
