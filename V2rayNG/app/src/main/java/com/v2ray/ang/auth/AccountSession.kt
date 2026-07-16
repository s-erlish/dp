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
     * Clear session + managed subscriptions and flip to LoggedOut.
     *
     * Called ONLY on an explicit user logout, or when the identity endpoint (getMe) confirms the
     * JWT is dead with a 401. It must never be triggered by a 403 or by a 401 on any other
     * endpoint (e.g. a background subscription-URL fetch or a per-action permission failure).
     */
    fun wipe() {
        subs.removeAllManaged()
        AuthTokenStore.clear()
        _state.value = AccountState.LoggedOut
    }
}
