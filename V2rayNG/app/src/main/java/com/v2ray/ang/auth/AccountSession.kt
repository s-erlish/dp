package com.v2ray.ang.auth

import com.v2ray.ang.auth.dto.UserProfileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the logged-in/out account state, observed by the UI.
 *
 * Seeded from [AuthTokenStore] on first access so a returning user is already "logged in".
 * Mutations here keep the persisted session and the in-memory [state] consistent.
 */
object AccountSession {

    sealed interface AccountState {
        object LoggedOut : AccountState
        data class LoggedIn(val profile: UserProfileDto) : AccountState
    }

    private val _state = MutableStateFlow(seed())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val subs = SubscriptionSyncManager()

    private fun seed(): AccountState {
        return if (AuthTokenStore.isLoggedIn()) {
            AccountState.LoggedIn(AuthTokenStore.getUser() ?: UserProfileDto())
        } else {
            AccountState.LoggedOut
        }
    }

    fun isLoggedIn(): Boolean = AuthTokenStore.isLoggedIn()

    /** Persist a freshly issued session and flip to LoggedIn. */
    fun onAuthenticated(jwt: String, profile: UserProfileDto) {
        AuthTokenStore.saveSession(jwt, user = profile)
        _state.value = AccountState.LoggedIn(profile)
    }

    /** Refresh the cached profile (e.g. after GET /client/auth/me). */
    fun updateProfile(profile: UserProfileDto) {
        AuthTokenStore.saveUser(profile)
        if (isLoggedIn()) _state.value = AccountState.LoggedIn(profile)
    }

    /**
     * The token is dead: end the session and leave everything else alone.
     *
     * Called when the identity endpoint (getMe) answers 401 — and only there; a 403, or a 401 from
     * any other endpoint, is a per-action failure and must not touch the session at all.
     *
     * This deliberately does NOT remove the imported subscriptions. A 7-day JWT expiring is the
     * most ordinary event in this app's life, and it says nothing about whether the user still has
     * a подписка: the subscription URLs are Remnawave URLs with their own credentials, they keep
     * refreshing, and the серверы keep working while the user is signed out. Wiping them here is
     * how one visit to the Аккаунт tab used to delete every сервер on the device — including the
     * selected one — for a token that simply timed out.
     *
     * Signing back in re-imports through the kept uuid->guid map, so the same провайдеры are
     * updated in place rather than duplicated. Detaching the account's subscriptions is what
     * [wipe] is for, and only an explicit sign-out asks for that.
     */
    fun endSession() {
        AuthTokenStore.clearSession()
        _state.value = AccountState.LoggedOut
    }

    /**
     * Clear session + managed subscriptions and flip to LoggedOut. **Explicit user logout only.**
     *
     * Removing the subscriptions is the point here: the user asked to detach this device from the
     * account, so the account's провайдеры go with it. Everything the user added by hand stays.
     * An expired token takes [endSession] instead.
     *
     * Throws when the token could not actually be erased — the one outcome a sign-out must never
     * paper over. The store is unopenable in that case, so nothing was removed either (the
     * managed-guid map read back empty) and the session is untouched and the action retryable,
     * which is exactly what the caller's failure branch promises the user.
     */
    fun wipe() {
        subs.removeAllManaged()
        check(AuthTokenStore.clear()) { "auth store unavailable: the session was not cleared" }
        _state.value = AccountState.LoggedOut
    }
}
