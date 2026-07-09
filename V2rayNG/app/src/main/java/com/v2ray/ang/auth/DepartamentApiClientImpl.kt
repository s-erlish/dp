package com.v2ray.ang.auth

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.auth.dto.AuthCodeRequest
import com.v2ray.ang.auth.dto.AuthPollRequest
import com.v2ray.ang.auth.dto.AuthPollResponse
import com.v2ray.ang.auth.dto.AuthStartRequest
import com.v2ray.ang.auth.dto.AuthStartResponse
import com.v2ray.ang.auth.dto.RefreshRequest
import com.v2ray.ang.auth.dto.SubscriptionInfoDto
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp + Gson implementation of [DepartamentApiClient].
 *
 * Builds requests against [BackendConfig.baseUrl] + endpoint paths, attaches Bearer auth
 * where required and the negotiated User-Agent for subscription calls. Maps every failure
 * to an [ApiError]; never logs tokens.
 *
 * There is no real backend yet — calls simply fail gracefully (ApiError.NotConfigured when
 * the base URL is blank, ApiError.Network otherwise) until BuildConfig is filled in.
 */
class DepartamentApiClientImpl(
    private val gson: Gson = Gson(),
    private val http: OkHttpClient = defaultClient(),
) : DepartamentApiClient {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun startTelegramAuth(req: AuthStartRequest): AuthStartResponse =
        post(BackendConfig.endpoints.authStart, gson.toJson(req), token = null, cls = AuthStartResponse::class.java)

    override suspend fun pollTelegramAuth(req: AuthPollRequest): AuthPollResponse =
        post(BackendConfig.endpoints.authPoll, gson.toJson(req), token = null, cls = AuthPollResponse::class.java)

    override suspend fun submitAuthCode(req: AuthCodeRequest): AuthPollResponse =
        post(BackendConfig.endpoints.authCode, gson.toJson(req), token = null, cls = AuthPollResponse::class.java)

    override suspend fun getProfile(token: String): UserProfileDto =
        get(BackendConfig.endpoints.profile, token = token, cls = UserProfileDto::class.java)

    override suspend fun getSubscription(token: String): SubscriptionInfoDto =
        get(BackendConfig.endpoints.subscription, token = token, cls = SubscriptionInfoDto::class.java)

    override suspend fun refresh(req: RefreshRequest): AuthPollResponse =
        post(BackendConfig.endpoints.refresh, gson.toJson(req), token = null, cls = AuthPollResponse::class.java)

    override suspend fun logout(token: String) {
        // Best-effort: ignore body, only care that the call was attempted.
        execute(buildRequest(BackendConfig.endpoints.logout, method = "POST", body = "{}", token = token))
    }

    // region internals

    private suspend fun <T> post(path: String, json: String, token: String?, cls: Class<T>): T {
        val body = execute(buildRequest(path, method = "POST", body = json, token = token))
        return parse(body, cls)
    }

    private suspend fun <T> get(path: String, token: String?, cls: Class<T>): T {
        val body = execute(buildRequest(path, method = "GET", body = null, token = token))
        return parse(body, cls)
    }

    private fun buildRequest(path: String, method: String, body: String?, token: String?): Request {
        if (!BackendConfig.isConfigured()) throw ApiError.NotConfigured
        val builder = Request.Builder()
            .url(BackendConfig.baseUrl + path)
            .header("Accept", "application/json")
            .header("User-Agent", BackendConfig.subscriptionUserAgent)
        if (SettingsManager.isSendHwid()) {
            builder.header(AppConfig.HEADER_HWID, AuthTokenStore.deviceId())
        }
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        when (method) {
            "GET" -> builder.get()
            else -> builder.post((body ?: "").toRequestBody(JSON))
        }
        return builder.build()
    }

    /** Executes the call on IO and returns the response body string, mapping failures to ApiError. */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> bodyStr
                    response.code == 401 || response.code == 403 -> throw ApiError.Unauthorized
                    response.code == 429 -> throw ApiError.RateLimited
                    else -> throw ApiError.Server(response.code)
                }
            }
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }
    }

    private fun <T> parse(body: String, cls: Class<T>): T {
        return try {
            gson.fromJson(body, cls) ?: throw ApiError.Parse()
        } catch (e: JsonSyntaxException) {
            throw ApiError.Parse(e)
        }
    }

    // endregion
}
