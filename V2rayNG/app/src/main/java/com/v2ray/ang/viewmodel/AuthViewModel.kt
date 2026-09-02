package com.v2ray.ang.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.AuthManager
import com.v2ray.ang.auth.AuthManager.LoginState
import com.v2ray.ang.auth.dto.LoginResult
import com.v2ray.ang.auth.dto.UserProfileDto
import com.v2ray.ang.auth.serverMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The auth state machine (14-auth.md §4), owned here so every UI decision on the sign-in surfaces
 * is a pure function of [state] + [error] + the form buffers.
 *
 * The screen this replaces had no machine at all: the Telegram awaiting block and the 2FA block
 * were independent `visibility` toggles on one scroll and could be on screen at the same time,
 * saying two contradictory things. Here exactly one [AuthUiState] is live, starting any method
 * cancels the one before it ([loginJob]), and there is no state a surface cannot draw.
 *
 * Three invariants from 4.3 are enforced in this file rather than asked of the caller:
 *
 * 1. **At most one method is in flight.** Every entry point cancels [loginJob] first, including the
 *    Telegram poll.
 * 2. **The deep link opens once per token.** [openedDeepLink] lives in [SavedStateHandle], so a
 *    rotation re-renders the awaiting stack without re-launching Telegram.
 * 3. **A failure is rendered, never re-raised.** [error] is a separate, idempotent value rather than
 *    a terminal state: re-collecting it after a rotation redraws the same line (which is correct —
 *    the message must survive turning the phone) while nothing re-fires. It is cleared by starting
 *    a new attempt, by fixing the field it belongs to, or by [consumeError].
 *
 * The `ApiError -> string` mapping (13.1) lives here too, so no Activity has to know what a 410 is,
 * and so both surfaces phrase the same failure identically.
 */
class AuthViewModel(private val saved: SavedStateHandle) : ViewModel() {

    private val authManager = AuthManager()

    /** Every state the sign-in surfaces can be in. Exactly one is live at a time. */
    sealed interface AuthUiState {
        /** Nothing in flight. The gate shows its idle stack; the form is editable. */
        data object Idle : AuthUiState

        /** Creating the Telegram login token. The CTA is loading; there is no deep link yet. */
        data object TelegramStarting : AuthUiState

        /** The deep link exists and the backend is polled every 2s for ~3 min. */
        data class TelegramAwaiting(val deepLink: String) : AuthUiState

        /** An email/password or 2FA request is in flight. The submit CTA is loading. */
        data object Submitting : AuthUiState

        /** The backend accepted the password and wants the TOTP code. */
        data class TwoFactor(val tempToken: String) : AuthUiState

        /**
         * Registered, and the panel wants the address proved: a letter carrying a link is out and
         * the login is being polled until it is opened. [resending] is true only while a second
         * letter is being requested, so «Отправить снова» can report itself without the screen
         * leaving the waiting state it is reporting from.
         */
        data class EmailVerification(
            val email: String,
            val resending: Boolean = false,
        ) : AuthUiState

        /** Session persisted. The UI plays the beat and hands back to the caller. */
        data class Success(val profile: UserProfileDto) : AuthUiState
    }

    /** Which surface a failure belongs to, so the right line (or the right field) carries it. */
    enum class Surface { GATE, MAIL, TWO_FACTOR }

    /**
     * A failure, already turned into copy. [credentialFlash] marks the one case where the two
     * credential fields also flash their border (12.11) — colour only, never a shake.
     */
    data class AuthError(
        @param:StringRes @get:StringRes val message: Int,
        val surface: Surface,
        val credentialFlash: Boolean = false,
        /**
         * The panel's OWN sentence, when it sent one and the surface is allowed to quote it — the
         * registration path, where a bare 400 covers both «Этот email уже зарегистрирован» and
         * «Некорректные данные» and only the panel knows which. Null everywhere else, and [message]
         * is then the whole answer.
         */
        val text: String? = null,
    )

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _error = MutableStateFlow<AuthError?>(null)
    val error: StateFlow<AuthError?> = _error.asStateFlow()

    /**
     * True while a 429 cool-down is running. The CTA is disabled for the whole 60s and no countdown
     * is printed: a ticking number would be a second live number on a screen that has none (5.6).
     */
    private val _rateLimited = MutableStateFlow(false)
    val rateLimited: StateFlow<Boolean> = _rateLimited.asStateFlow()

    private var loginJob: Job? = null
    private var cooldownJob: Job? = null

    /**
     * The credentials a registration was started with, held HERE and not inside
     * [AuthUiState.EmailVerification].
     *
     * The address is what «Отправить снова» re-sends to and what the poll signs in with, and both
     * outlive the state that named it: a failed resend puts the machine back into the waiting
     * state, and reading the address out of the state it is being rebuilt from is circular — one
     * error and the screen would be left with a resend button that has nothing to send and a poll
     * with nobody to log in as. A ViewModel field survives every state change and every rotation,
     * which is exactly the lifetime this pair needs.
     *
     * The password is deliberately NOT in [SavedStateHandle]: that Bundle can be written to disk by
     * the platform, and a password does not belong there. Process death therefore ends the wait,
     * which is correct — nothing else on this screen survives it either.
     */
    private var pendingEmail: String? = null
    private var pendingPassword: String? = null

    /**
     * The deep link this process has already handed to Telegram. Survives configuration change in
     * [SavedStateHandle] precisely so a rotation does not re-open Telegram behind the user.
     */
    var openedDeepLink: String?
        get() = saved[KEY_DEEP_LINK]
        set(value) {
            saved[KEY_DEEP_LINK] = value
        }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    /**
     * Starts (or restarts) the Telegram deep-link flow: mint a token, hand the link to the UI, then
     * poll until the user confirms. Restarting mints a fresh token and abandons the old one, which
     * is why there is no separate «Начать заново» control anywhere on the gate.
     */
    fun startTelegramLogin() {
        loginJob?.cancel()
        _error.value = null
        _state.value = AuthUiState.TelegramStarting
        loginJob = viewModelScope.launch {
            authManager.beginTelegramLogin().collect { managerState ->
                when (managerState) {
                    is LoginState.AwaitingTelegram ->
                        _state.value = AuthUiState.TelegramAwaiting(managerState.deepLink)

                    // AwaitingTelegram can be conflated away before the UI sees it, so Polling
                    // carries the link too and lands on the same UI state.
                    is LoginState.Polling ->
                        _state.value = AuthUiState.TelegramAwaiting(managerState.deepLink)

                    is LoginState.Success -> _state.value = AuthUiState.Success(managerState.profile)

                    is LoginState.Error -> fail(managerState.error, Surface.GATE)

                    // The manager never emits these on this flow: Idle would be a no-op,
                    // SiteLoading belongs to the password path and the verification wait belongs
                    // to registration.
                    is LoginState.Idle, is LoginState.SiteLoading,
                    is LoginState.AwaitingEmailVerification -> Unit
                }
            }
        }
    }

    /**
     * Cancels the poll and returns to the gate's idle stack. This is what system Back does while
     * awaiting: it never leaves the screen from that state (D-14.E).
     */
    fun cancelTelegramLogin() {
        loginJob?.cancel()
        loginJob = null
        openedDeepLink = null
        _error.value = null
        _state.value = AuthUiState.Idle
    }

    /**
     * Registration, in the app and not in a browser. Two answers are both successes: a panel with
     * e-mail verification off signs the user straight in, one with it on sends a letter and the
     * machine moves to [AuthUiState.EmailVerification] while [AuthManager] polls the login.
     *
     * Also the «Отправить снова» action — see [resendVerification]. When it is called from the
     * waiting screen the screen STAYS on it (`resending = true` spins the ring in place) instead of
     * dropping back to the form: the first letter may well arrive while the second is being asked
     * for, and yanking the user off the screen that explains what to do with it would be a step
     * backwards for a tap that meant "keep going".
     */
    fun register(email: String, password: String) {
        loginJob?.cancel()
        _error.value = null
        pendingEmail = email
        pendingPassword = password
        val resending = _state.value is AuthUiState.EmailVerification
        _state.value = if (resending) {
            AuthUiState.EmailVerification(email, resending = true)
        } else {
            AuthUiState.Submitting
        }
        loginJob = viewModelScope.launch {
            authManager.beginRegister(email, password).collect { managerState ->
                when (managerState) {
                    is LoginState.AwaitingEmailVerification ->
                        _state.value = AuthUiState.EmailVerification(managerState.email)

                    is LoginState.Success -> _state.value = AuthUiState.Success(managerState.profile)

                    is LoginState.Error -> fail(managerState.error, Surface.MAIL, quoteBackend = true)

                    // Not emitted on this flow: the Telegram and password states belong to the
                    // other two entry points.
                    is LoginState.Idle, is LoginState.SiteLoading,
                    is LoginState.AwaitingTelegram, is LoginState.Polling -> Unit
                }
            }
        }
    }

    /** «Отправить снова» on the waiting screen: the same registration, to the same address. */
    fun resendVerification() {
        val email = pendingEmail ?: return
        val password = pendingPassword ?: return
        register(email, password)
    }

    /**
     * «Вернуться ко входу»: abandons the wait and the poll with it, and forgets the credentials it
     * was holding. The account still exists and the letter is still valid — nothing here undoes the
     * registration, it only stops watching for it.
     */
    fun leaveVerification() {
        loginJob?.cancel()
        loginJob = null
        pendingEmail = null
        pendingPassword = null
        _error.value = null
        _state.value = AuthUiState.Idle
    }

    /** Email + password. A `Requires2FA` answer moves to [AuthUiState.TwoFactor], not to an error. */
    fun loginSite(email: String, password: String) {
        loginJob?.cancel()
        _error.value = null
        _state.value = AuthUiState.Submitting
        loginJob = viewModelScope.launch {
            try {
                when (val result = authManager.loginSite(email, password)) {
                    is LoginResult.Success -> _state.value = AuthUiState.Success(result.client)
                    is LoginResult.Requires2FA -> {
                        saved[KEY_TEMP_TOKEN] = result.tempToken
                        _state.value = AuthUiState.TwoFactor(result.tempToken)
                    }
                }
            } catch (e: ApiError) {
                fail(e, Surface.MAIL)
            }
        }
    }

    /**
     * Completes a 2FA login with the token held from [loginSite]. The token lives in
     * [SavedStateHandle] so rotating the phone mid-step does not throw the user back to the form.
     */
    fun submit2fa(code: String) {
        val tempToken: String = saved[KEY_TEMP_TOKEN] ?: return
        loginJob?.cancel()
        _error.value = null
        _state.value = AuthUiState.Submitting
        loginJob = viewModelScope.launch {
            try {
                val profile = authManager.submit2fa(tempToken, code)
                saved.remove<String>(KEY_TEMP_TOKEN)
                _state.value = AuthUiState.Success(profile)
            } catch (e: ApiError) {
                fail(e, Surface.TWO_FACTOR)
            }
        }
    }

    /** «Отмена» and system Back on the 2FA step: drop the token and go back to the form. */
    fun cancelTwoFactor() {
        loginJob?.cancel()
        loginJob = null
        saved.remove<String>(KEY_TEMP_TOKEN)
        _error.value = null
        _state.value = AuthUiState.Idle
    }

    /**
     * Abandons whatever is in flight and returns the machine to [AuthUiState.Idle] **without**
     * raising a failure: the user navigated away, and a navigation is not an error. Used when the
     * form is left while a submit is running — the answer to a request nobody is waiting for must
     * not arrive on a surface that has moved on.
     */
    fun cancelPending() {
        loginJob?.cancel()
        loginJob = null
        openedDeepLink = null
        saved.remove<String>(KEY_TEMP_TOKEN)
        pendingEmail = null
        pendingPassword = null
        _error.value = null
        _state.value = AuthUiState.Idle
    }

    /** Raises a failure the UI produced itself, e.g. no Telegram and no browser to fall back to. */
    fun failLocally(@StringRes message: Int, surface: Surface) {
        loginJob?.cancel()
        loginJob = null
        openedDeepLink = null
        _state.value = AuthUiState.Idle
        _error.value = AuthError(message, surface)
    }

    /** Clears the error line: the user has started a new attempt or fixed the field it named. */
    fun consumeError() {
        _error.value = null
    }

    /**
     * Turns a failure into copy and puts the machine back where the surface can act again. The 2FA
     * step keeps its own state, so the user lands back on the six cells rather than on the password
     * field they already got right.
     */
    private fun fail(cause: ApiError, surface: Surface, quoteBackend: Boolean = false) {
        val awaitingTelegram = _state.value is AuthUiState.TelegramAwaiting
        val verifying = _state.value is AuthUiState.EmailVerification
        _state.value = when {
            surface == Surface.TWO_FACTOR -> {
                val token: String? = saved[KEY_TEMP_TOKEN]
                if (token != null) AuthUiState.TwoFactor(token) else AuthUiState.Idle
            }

            // A failed «Отправить снова» is not a failed WAIT: the letter that is already out is
            // still valid and the poll still has something to find, so the screen goes back to
            // waiting with the reason printed on it. The address comes from the field that outlives
            // the state, never from the state being replaced.
            verifying -> pendingEmail?.let { AuthUiState.EmailVerification(it) } ?: AuthUiState.Idle

            else -> AuthUiState.Idle
        }
        if (cause is ApiError.RateLimited) startCooldown()
        _error.value = AuthError(
            message = messageFor(cause, surface, awaitingTelegram),
            surface = surface,
            // 12.11: an Unauthorized on the password step flashes both credential borders. On the
            // 2FA step the cells carry that signal instead, so the flash is not doubled.
            credentialFlash = surface == Surface.MAIL && cause is ApiError.Unauthorized && !quoteBackend,
            text = if (quoteBackend) cause.serverMessage() else null,
        )
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        _rateLimited.value = true
        cooldownJob = viewModelScope.launch {
            delay(RATE_LIMIT_COOLDOWN_MS)
            _rateLimited.value = false
        }
    }

    companion object {
        /**
         * 13.1, in one place. Every message names the cause AND the fix; none carries an HTTP code,
         * a URL or a response body — the raw reason goes to the log, not to the customer.
         *
         * Public and on the companion because the Telegram flow that runs from the START SCREEN
         * (README §3, `ui/component/GateView`) reports the same failures on a surface this
         * ViewModel does not own: its overlay lifts and the reason lands in the gate's caption. Two
         * copies of this `when` would let the same 410 read two different ways in one product.
         */
        @StringRes
        fun messageFor(
            cause: ApiError,
            surface: Surface = Surface.GATE,
            awaitingTelegram: Boolean = false,
        ): Int = when {
            // A wrong TOTP code is not "wrong password": it names the authenticator app, because
            // that is where the user has to look.
            surface == Surface.TWO_FACTOR && cause is ApiError.Unauthorized -> R.string.auth_2fa_wrong
            cause is ApiError.Unauthorized -> R.string.auth_err_credentials
            cause is ApiError.Gone -> R.string.auth_err_gone
            cause is ApiError.RateLimited -> R.string.auth_err_rate_limited
            cause is ApiError.ServiceUnavailable -> R.string.auth_err_unavailable
            cause is ApiError.Network -> R.string.auth_err_network
            // The manager reports the 180s poll deadline as Timeout as well, so the phrasing is
            // chosen by WHERE we were: waiting on a human reads differently from waiting on a
            // server.
            cause is ApiError.Timeout && awaitingTelegram -> R.string.auth_err_tg_timeout
            cause is ApiError.Timeout -> R.string.auth_err_timeout
            cause is ApiError.NotConfigured -> R.string.auth_err_not_configured
            else -> R.string.auth_err_generic
        }

        /** 5.6: 429 disables the submit for a minute; the countdown is deliberately not printed. */
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        private const val KEY_DEEP_LINK = "auth_opened_deep_link"
        private const val KEY_TEMP_TOKEN = "auth_temp_token"
    }
}
