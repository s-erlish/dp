package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.AuthResult
import com.v2ray.ang.auth.dto.LoginResult
import com.v2ray.ang.auth.dto.RegisterResult
import com.v2ray.ang.auth.dto.TelegramCheckResult
import com.v2ray.ang.auth.dto.UserProfileDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates the auth flows only (Telegram deep-link login, e-mail registration, e-mail/password
 * sign-in, TOTP 2FA, and attaching an e-mail to a session that already exists).
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

        /**
         * A letter carrying a LINK (not a code — there is nothing for the user to type back) has
         * been sent to [email], and the flow that emitted this keeps watching for it to be opened.
         *
         * TWO ERRANDS END UP HERE, and the wait is the same wait either way. [beginRegister] gets
         * it when the panel wants a new account's address proved, and polls the LOGIN until it
         * answers 200; [beginLinkEmail] gets it when a signed-in user attaches an address, and
         * polls the PROFILE until it carries one. The question differs because the fact each is
         * waiting for differs; the moment does not, so neither does the screen.
         */
        data class AwaitingEmailVerification(val email: String) : LoginState

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
                    // Persisting can now REFUSE the reply (a blank jwt is not a session), and this
                    // flow is collected straight into `viewModelScope.launch` / `lifecycleScope`
                    // with no catch around it — an exception thrown here would not be a failed
                    // login, it would be a crash on the sign-in screen. It becomes the same
                    // rendered error every other failure on this flow becomes.
                    try {
                        AccountSession.onAuthenticated(result.token, result.client)
                    } catch (e: ApiError) {
                        emit(LoginState.Error(e))
                        return@flow
                    }
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
     * Registration as a cold [Flow], and it has to be a flow rather than a suspend call for the same
     * reason the Telegram login does: the errand does not end with the request. A panel with e-mail
     * verification switched OFF answers with a session and this is over in one emission; a panel
     * with it switched ON answers with a letter, and the flow then sits on
     * [LoginState.AwaitingEmailVerification] until the user opens the link in it.
     *
     * The letter carries a LINK and not a code, which is why nothing on the waiting screen asks the
     * user to type anything: the app cannot receive the browser's confirmation, so it watches for
     * the SIDE EFFECT of it instead — see [pollUntilVerified].
     */
    fun beginRegister(email: String, password: String): Flow<LoginState> = flow {
        if (!BackendConfig.isConfigured()) {
            emit(LoginState.Error(ApiError.NotConfigured))
            return@flow
        }

        val result = try {
            api.register(email, password)
        } catch (e: ApiError) {
            emit(LoginState.Error(e))
            return@flow
        }

        when (result) {
            is RegisterResult.Success -> {
                // Persisting can refuse the reply (a blank jwt is not a session); that is a failed
                // registration, not a crash on the sign-in screen. Same guard as the Telegram flow.
                try {
                    AccountSession.onAuthenticated(result.token, result.client)
                } catch (e: ApiError) {
                    emit(LoginState.Error(e))
                    return@flow
                }
                emit(LoginState.Success(result.client))
            }

            is RegisterResult.RequiresVerification -> {
                emit(LoginState.AwaitingEmailVerification(email))
                pollUntilVerified(email, password)
            }
        }
    }

    /**
     * Watches for the emailed link being opened, by asking the ONE question whose answer changes
     * when it is: log in with the credentials just registered. While the address is unproved the
     * panel refuses with 401; the moment the link is opened the same request answers 200 with a
     * token, and the app signs itself in without the user coming back to type anything.
     *
     * **Every failure here is swallowed on purpose.** A 401 is the expected answer for as long as
     * the letter is unread, and a network blip is not the user's mistake — reporting either would
     * put an error on a screen whose only correct instruction is «откройте ссылку». The deadline is
     * reached in the same silence: the waiting screen stays up with «Отправить снова» on it, which
     * is the real next move, rather than being replaced by an invented failure.
     */
    private suspend fun FlowCollector<LoginState>.pollUntilVerified(
        email: String,
        password: String,
    ) {
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(VERIFY_POLL_INTERVAL_MS)
            val result = try {
                api.login(email, password)
            } catch (e: ApiError) {
                continue // Still unverified, or a blip. Neither is news.
            }
            if (result is LoginResult.Success) {
                try {
                    AccountSession.onAuthenticated(result.token, result.client)
                } catch (e: ApiError) {
                    emit(LoginState.Error(e))
                    return
                }
                emit(LoginState.Success(result.client))
                return
            }
            // An account registered a minute ago cannot have TOTP on it, so Requires2FA is not a
            // real answer here; keep waiting rather than stranding the user on a code prompt they
            // have no way to satisfy.
        }
    }

    /**
     * **Attaching an e-mail to the account that is already signed in.**
     *
     * The errand a Telegram-only account arrives with: it has no address, so «Способы входа» has
     * nothing to report on its «Почта» row and the panel has no second way to let that person back
     * in. `POST /client/link-email-request` carries the address alone — the Bearer token already
     * says which account is asking, and no password is set here.
     *
     * A flow rather than a suspend call for the same reason registration is one: the errand does
     * not end with the request. The 200 only means a letter is out, and the wait that follows is
     * the user's, not the network's — so the state moves to [LoginState.AwaitingEmailVerification]
     * and this flow sits on [pollUntilLinked] until the link is opened.
     */
    fun beginLinkEmail(email: String): Flow<LoginState> = flow {
        if (!BackendConfig.isConfigured()) {
            emit(LoginState.Error(ApiError.NotConfigured))
            return@flow
        }

        try {
            api.linkEmailRequest(email)
        } catch (e: ApiError) {
            emit(LoginState.Error(e))
            return@flow
        }

        emit(LoginState.AwaitingEmailVerification(email))
        pollUntilLinked()
    }

    /**
     * Watches for the emailed link being opened, and — as in [pollUntilVerified] — it does so by
     * asking the one question whose answer changes when it is. Here that question is not the login
     * (this user is already signed in) but the PROFILE: `/client/auth/me` carries a blank `email`
     * for a Telegram-only account and a real one the moment the site has run the confirmation
     * behind the link.
     *
     * The address the letter went to is deliberately NOT compared against the one that comes back.
     * The panel owns that value and may hand it back normalised — a different case, a trimmed
     * alias — and an equality test would then wait forever for a link that was opened correctly.
     * A profile that has an address where it had none is the whole signal.
     *
     * **Every failure here is swallowed, exactly as in [pollUntilVerified]**: an unread letter is
     * not news, and neither is a network blip. Note this is also why the poll asks [api] directly
     * rather than going through `AccountRepository` — a 401 on the identity endpoint ENDS THE
     * SESSION there, and a token that died while a letter was in flight must not sign the user out
     * from inside a background poll they cannot see.
     *
     * The profile is written back to [AccountSession] before the success is emitted, so the row
     * that sent the user here reports the new address from cache rather than from a lucky reload.
     */
    private suspend fun FlowCollector<LoginState>.pollUntilLinked() {
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(VERIFY_POLL_INTERVAL_MS)
            val profile = try {
                api.getMe()
            } catch (e: ApiError) {
                continue // Still unlinked, or a blip. Neither is news.
            }
            if (profile.email.isNotBlank()) {
                AccountSession.updateProfile(profile)
                emit(LoginState.Success(profile))
                return
            }
        }
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

    private companion object {
        /** 4s between rounds: the user is reading a letter, not watching a spinner tick. */
        const val VERIFY_POLL_INTERVAL_MS = 4_000L

        /** 10 minutes. Long enough to find the letter in a spam folder; not an unbounded poll. */
        const val VERIFY_TIMEOUT_MS = 10 * 60 * 1_000L
    }
}
