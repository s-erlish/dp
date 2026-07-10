package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.AuthResult
import com.v2ray.ang.auth.dto.LoginResult
import com.v2ray.ang.auth.dto.TelegramCheckResult
import com.v2ray.ang.auth.dto.UserProfileDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates the auth flows only (Telegram deep-link login, site email/password, TOTP 2FA).
 * There is NO refresh/logout here — the JWT is 7-day and non-refreshable; session persistence is
 * delegated to [AccountSession]/[AuthTokenStore], subscription import to [AccountRepository].
 */
class AuthManager(
    private val api: DepartamentApiClient = DepartamentApiClientImpl(),
) {

    /** UI-facing state of a login attempt (Telegram deep-link, site email/password, or 2FA). */
    sealed interface LoginState {
        object Idle : LoginState

        /** Deep link is ready; the UI should open Telegram via ACTION_VIEW with it. */
        data class AwaitingTelegram(val deepLink: String) : LoginState

        /** Polling the backend for confirmation. Carries the deep link so the UI can reopen it. */
        data class Polling(val deepLink: String) : LoginState

        /**
         * A site email/password or 2FA request is in flight. The UI shows an inline busy indicator
         * on the site/2FA button; unlike [Polling] it never opens Telegram or the awaiting card.
         */
        object SiteLoading : LoginState

        /** Confirmed — session persisted; carries the profile. */
        data class Success(val profile: UserProfileDto) : LoginState

        data class Error(val error: ApiError) : LoginState
    }

    fun isLoggedIn(): Boolean = AuthTokenStore.isLoggedIn()

    /**
     * Telegram login as a cold [Flow]: create a login token, emit [LoginState.AwaitingTelegram]
     * (so the UI opens the deep link), then poll every ~2s (capped at ~3 min) until the user
     * confirms in Telegram. On confirmation the session is persisted before [LoginState.Success].
     */
    fun beginTelegramLogin(): Flow<LoginState> = flow {
        if (!BackendConfig.isConfigured()) {
            emit(LoginState.Error(ApiError.NotConfigured))
            return@flow
        }

        val tokenDto = try {
            api.createTelegramLoginToken()
        } catch (e: ApiError) {
            emit(LoginState.Error(e))
            return@flow
        }
        if (tokenDto.token.isBlank()) {
            emit(LoginState.Error(ApiError.Parse()))
            return@flow
        }

        val deepLink = "https://t.me/${BackendConfig.botUsername}?start=auth_${tokenDto.token}"
        emit(LoginState.AwaitingTelegram(deepLink))
        emit(LoginState.Polling(deepLink))

        val pollIntervalMs = 2_000L
        val timeoutMs = 3 * 60 * 1_000L
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val result = try {
                api.checkTelegramLogin(tokenDto.token)
            } catch (e: ApiError) {
                emit(LoginState.Error(e))
                return@flow
            }
            when (result) {
                is TelegramCheckResult.Confirmed -> {
                    AccountSession.onAuthenticated(result.token, result.client)
                    emit(LoginState.Success(result.client))
                    return@flow
                }
                TelegramCheckResult.Expired -> {
                    emit(LoginState.Error(ApiError.Gone))
                    return@flow
                }
                TelegramCheckResult.NotYet -> Unit // keep polling
            }
        }
        emit(LoginState.Error(ApiError.Timeout))
    }

    /**
     * Site login with email/password. On [LoginResult.Success] the session is persisted; on
     * [LoginResult.Requires2FA] the caller must follow up with [submit2fa] using the tempToken.
     * Throws [ApiError] on failure.
     */
    suspend fun loginSite(email: String, password: String): LoginResult {
        if (!BackendConfig.isConfigured()) throw ApiError.NotConfigured
        val result = api.login(email, password)
        if (result is LoginResult.Success) {
            AccountSession.onAuthenticated(result.token, result.client)
        }
        return result
    }

    /** Completes a 2FA login; persists the session and returns the profile. Throws [ApiError]. */
    suspend fun submit2fa(tempToken: String, code: String): UserProfileDto {
        if (!BackendConfig.isConfigured()) throw ApiError.NotConfigured
        val auth: AuthResult = api.login2fa(tempToken, code)
        AccountSession.onAuthenticated(auth.token, auth.client)
        return auth.client
    }
}
