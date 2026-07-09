package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.auth.AuthManager
import com.v2ray.ang.auth.AuthManager.LoginState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives [AuthManager] for [com.v2ray.ang.ui.LoginActivity], exposing the login state as a
 * [StateFlow] the Activity observes.
 */
class AuthViewModel : ViewModel() {

    private val authManager = AuthManager()

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var loginJob: Job? = null

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    /** Starts (or restarts) the Telegram deep-link + poll flow. */
    fun startTelegramLogin() {
        loginJob?.cancel()
        _state.value = LoginState.Idle
        loginJob = viewModelScope.launch {
            authManager.beginTelegramLogin().collect { state ->
                _state.value = state
            }
        }
    }

    /** Fallback: submit a code copied from the bot. */
    fun submitCode(code: String) {
        loginJob?.cancel()
        _state.value = LoginState.Polling
        loginJob = viewModelScope.launch {
            _state.value = authManager.submitCode(code)
        }
    }
}
