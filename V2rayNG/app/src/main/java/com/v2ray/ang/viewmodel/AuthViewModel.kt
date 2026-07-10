package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.AuthManager
import com.v2ray.ang.auth.AuthManager.LoginState
import com.v2ray.ang.auth.dto.LoginResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives [AuthManager] for the login UI, exposing the Telegram [state] plus a [twoFactor] signal
 * that carries the tempToken when a site login needs a TOTP code.
 */
class AuthViewModel : ViewModel() {

    private val authManager = AuthManager()

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** Non-null tempToken when the last site login requires a 2FA code; null otherwise. */
    private val _twoFactor = MutableStateFlow<String?>(null)
    val twoFactor: StateFlow<String?> = _twoFactor.asStateFlow()

    private var loginJob: Job? = null

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    /** Starts (or restarts) the Telegram deep-link + poll flow. */
    fun startTelegramLogin() {
        loginJob?.cancel()
        _twoFactor.value = null
        _state.value = LoginState.Idle
        loginJob = viewModelScope.launch {
            authManager.beginTelegramLogin().collect { s ->
                _state.value = s
            }
        }
    }

    /** Site email/password login. Sets [twoFactor] when a code is required. */
    fun loginSite(email: String, password: String) {
        loginJob?.cancel()
        _twoFactor.value = null
        _state.value = LoginState.Polling("")
        loginJob = viewModelScope.launch {
            try {
                when (val result = authManager.loginSite(email, password)) {
                    is LoginResult.Success -> _state.value = LoginState.Success(result.client)
                    is LoginResult.Requires2FA -> {
                        _twoFactor.value = result.tempToken
                        _state.value = LoginState.Idle
                    }
                }
            } catch (e: ApiError) {
                _state.value = LoginState.Error(e)
            }
        }
    }

    /** Completes a 2FA login started by [loginSite]. */
    fun submit2fa(tempToken: String, code: String) {
        loginJob?.cancel()
        _state.value = LoginState.Polling("")
        loginJob = viewModelScope.launch {
            try {
                val profile = authManager.submit2fa(tempToken, code)
                _twoFactor.value = null
                _state.value = LoginState.Success(profile)
            } catch (e: ApiError) {
                _state.value = LoginState.Error(e)
            }
        }
    }
}
