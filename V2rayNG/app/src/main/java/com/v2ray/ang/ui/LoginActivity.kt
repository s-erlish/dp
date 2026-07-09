package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.auth.ApiError
import com.v2ray.ang.auth.AuthManager.LoginState
import com.v2ray.ang.databinding.ActivityLoginBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Optional "Sign in with Telegram" screen. States: intro -> awaiting -> success/error.
 * The app remains fully usable without ever visiting this screen.
 */
class LoginActivity : BaseActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val viewModel: AuthViewModel by viewModels()

    private var currentDeepLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.auth_login_title))

        binding.btnContinueTelegram.setOnClickListener {
            viewModel.startTelegramLogin()
        }
        binding.btnOpenTelegramAgain.setOnClickListener {
            currentDeepLink?.let { openTelegram(it) }
        }
        binding.btnSubmitCode.setOnClickListener {
            val code = binding.etCode.text?.toString()?.trim().orEmpty()
            if (code.isEmpty()) {
                toast(R.string.auth_code_hint)
            } else {
                viewModel.submitCode(code)
            }
        }

        observeState()
        showIntro()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: LoginState) {
        when (state) {
            is LoginState.Idle -> showIntro()

            is LoginState.AwaitingTelegram -> {
                if (currentDeepLink != state.deepLink) {
                    currentDeepLink = state.deepLink
                    openTelegram(state.deepLink)
                }
                showAwaiting()
            }

            is LoginState.Polling -> {
                // Open Telegram here too: the momentary AwaitingTelegram may have been
                // conflated away by the StateFlow before the UI observed it. Blank deep link
                // (manual-code path) means don't open Telegram.
                if (state.deepLink.isNotBlank() && currentDeepLink != state.deepLink) {
                    currentDeepLink = state.deepLink
                    openTelegram(state.deepLink)
                }
                showAwaiting()
            }

            is LoginState.Success -> {
                showLoading()
                toastSuccess(R.string.auth_success)
                setResult(RESULT_OK)
                finish()
            }

            is LoginState.Error -> showError(state.error)
        }
    }

    private fun showIntro() {
        binding.layoutAwaiting.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.btnContinueTelegram.isEnabled = true
    }

    private fun showAwaiting() {
        binding.layoutAwaiting.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.btnContinueTelegram.isEnabled = true
    }

    private fun showError(error: ApiError) {
        binding.layoutAwaiting.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.setText(messageFor(error))
        binding.btnContinueTelegram.isEnabled = true
    }

    private fun messageFor(error: ApiError): Int = when (error) {
        is ApiError.NotConfigured -> R.string.auth_not_configured
        is ApiError.Network -> R.string.auth_error_network
        is ApiError.Unauthorized -> R.string.auth_error_unauthorized
        is ApiError.RateLimited -> R.string.auth_error_rate_limited
        is ApiError.Timeout -> R.string.auth_error_timeout
        is ApiError.Server -> R.string.auth_error_generic
        is ApiError.Parse -> R.string.auth_error_generic
    }

    private fun openTelegram(deepLink: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
        } catch (e: ActivityNotFoundException) {
            toastError(R.string.auth_error_telegram_not_installed)
        }
    }
}
