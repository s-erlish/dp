package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.AuthCodeRequest
import com.v2ray.ang.auth.dto.AuthPollRequest
import com.v2ray.ang.auth.dto.AuthPollResponse
import com.v2ray.ang.auth.dto.AuthStartRequest
import com.v2ray.ang.auth.dto.RefreshRequest
import com.v2ray.ang.auth.dto.UserProfileDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates the Telegram login lifecycle: start -> open deep link -> poll -> import
 * subscription, plus code fallback, refresh and logout. Backed by [AuthTokenStore] and
 * bridges to the existing subscription plumbing via [SubscriptionSyncManager].
 */
class AuthManager(
    private val api: DepartamentApiClient = DepartamentApiClientImpl(),
    private val subs: SubscriptionSyncManager = SubscriptionSyncManager(),
) {

    /** UI-facing state of a login attempt. */
    sealed interface LoginState {
        object Idle : LoginState

        /** Deep link is ready; the UI should open Telegram with it. */
        data class AwaitingTelegram(val deepLink: String) : LoginState

        /** Polling the backend for confirmation. Carries the deep link so the UI can
         *  (re)open Telegram even if the momentary AwaitingTelegram state was conflated away. */
        data class Polling(val deepLink: String) : LoginState

        data class Success(val user: UserProfileDto?) : LoginState

        data class Error(val error: ApiError) : LoginState
    }

    fun isLoggedIn(): Boolean = AuthTokenStore.isLoggedIn()

    /**
     * Full Telegram login as a cold [Flow]: start the nonce, emit [LoginState.AwaitingTelegram]
     * (so the UI opens the deep link), then poll with backoff until ready/expired/timeout. On
     * success the subscription is imported before [LoginState.Success] is emitted.
     */
    fun beginTelegramLogin(): Flow<LoginState> = flow {
        if (!BackendConfig.isConfigured()) {
            emit(LoginState.Error(ApiError.NotConfigured))
            return@flow
        }

        val deviceId = AuthTokenStore.deviceId()
        val nonce = generateNonce()

        val start = try {
            api.startTelegramAuth(AuthStartRequest(nonce = nonce, deviceId = deviceId))
        } catch (e: ApiError) {
            emit(LoginState.Error(e))
            return@flow
        }

        val deepLink = resolveDeepLink(start.deepLink, start.botUsername, nonce)
        // Carry the deep link on the Polling state; a conflating StateFlow in the ViewModel
        // may otherwise drop the momentary AwaitingTelegram before the UI observes it.
        emit(LoginState.Polling(deepLink))

        val pollIntervalMs = 2_000L
        val timeoutMs = (start.expiresInSec.coerceAtLeast(30)) * 1000L
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val resp = try {
                api.pollTelegramAuth(AuthPollRequest(nonce = nonce, deviceId = deviceId))
            } catch (e: ApiError) {
                emit(LoginState.Error(e))
                return@flow
            }
            when (resp.status) {
                "ready" -> {
                    emit(finishReady(resp))
                    return@flow
                }
                "expired" -> {
                    emit(LoginState.Error(ApiError.Timeout))
                    return@flow
                }
                else -> Unit // "pending" -> keep polling
            }
        }
        emit(LoginState.Error(ApiError.Timeout))
    }

    /** Fallback C: user pastes a code printed by the bot. */
    suspend fun submitCode(code: String): LoginState {
        if (!BackendConfig.isConfigured()) return LoginState.Error(ApiError.NotConfigured)
        val deviceId = AuthTokenStore.deviceId()
        val resp = try {
            api.submitAuthCode(AuthCodeRequest(code = code.trim(), deviceId = deviceId))
        } catch (e: ApiError) {
            return LoginState.Error(e)
        }
        return if (resp.status == "ready") finishReady(resp)
        else LoginState.Error(ApiError.Unauthorized)
    }

    /** Attempts a token refresh if a refresh token is present. Returns true on success. */
    suspend fun refreshIfNeeded(): Boolean {
        if (!BackendConfig.isConfigured()) return false
        val refresh = AuthTokenStore.getRefreshToken() ?: return false
        return try {
            val resp = api.refresh(RefreshRequest(refreshToken = refresh, deviceId = AuthTokenStore.deviceId()))
            if (resp.status == "ready" && !resp.token.isNullOrBlank()) {
                AuthTokenStore.saveSession(resp.token, resp.refreshToken, resp.expiresAt, resp.user)
                true
            } else {
                false
            }
        } catch (e: ApiError) {
            false
        }
    }

    /** Logs out: best-effort backend call, clears local session and the managed subscription. */
    suspend fun logout() {
        val token = AuthTokenStore.getToken()
        if (BackendConfig.isConfigured() && !token.isNullOrBlank()) {
            try {
                api.logout(token)
            } catch (e: ApiError) {
                // ignore — local cleanup below is authoritative
            }
        }
        subs.removeManagedSubscription()
        AuthTokenStore.clear()
    }

    // region internals

    private suspend fun finishReady(resp: AuthPollResponse): LoginState {
        val token = resp.token
        if (token.isNullOrBlank()) return LoginState.Error(ApiError.Parse())
        AuthTokenStore.saveSession(token, resp.refreshToken, resp.expiresAt, resp.user)
        resp.subscription?.let { subs.importOrUpdate(it) }
        return LoginState.Success(resp.user)
    }

    private fun resolveDeepLink(deepLink: String?, botUsername: String?, nonce: String): String {
        if (!deepLink.isNullOrBlank()) return deepLink
        val bot = botUsername?.ifBlank { null } ?: BackendConfig.botUsername
        return "https://t.me/$bot?start=$nonce"
    }

    private fun generateNonce(): String {
        // 128-bit CSPRNG, base64url without padding.
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    // endregion
}
