package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
 * Экран входа departament. Один экран с двумя секциями:
 *   1. Telegram — вход в один тап через deep link + опрос подтверждения.
 *   2. Сайт — email + пароль, при необходимости — 6-значный код (2FA / TOTP).
 * Приложение полностью работает и без входа на этот экран.
 */
class LoginActivity : BaseActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val viewModel: AuthViewModel by viewModels()

    // Deep link уже открытый в текущей сессии — чтобы не открывать Telegram повторно.
    private var currentDeepLink: String? = null

    // tempToken из потока twoFactor — нужен для подтверждения кода.
    private var pendingTempToken: String? = null

    // Последняя попытка входа была через сайт (email/пароль или 2FA), а не Telegram.
    // Только для таких ошибок показываем диагностический диалог с реальной причиной.
    private var lastAttemptWasSite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.auth_title))

        if (viewModel.isLoggedIn()) {
            setResult(RESULT_OK)
            finish()
            return
        }

        // Режим экрана задаётся вызывающей стороной через EXTRA_MODE:
        //   "site"     — только карточка входа через сайт;
        //   "telegram" — только карточка входа через Telegram;
        //   отсутствует — обе карточки.
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_SITE -> {
                binding.cardTelegram.visibility = View.GONE
                binding.cardSite.visibility = View.VISIBLE
            }

            MODE_TELEGRAM -> {
                binding.cardTelegram.visibility = View.VISIBLE
                binding.cardSite.visibility = View.GONE
            }

            else -> {
                binding.cardTelegram.visibility = View.VISIBLE
                binding.cardSite.visibility = View.VISIBLE
            }
        }

        binding.btnTelegram.setOnClickListener {
            lastAttemptWasSite = false
            hideError()
            viewModel.startTelegramLogin()
        }

        binding.btnRestart.setOnClickListener {
            lastAttemptWasSite = false
            hideError()
            currentDeepLink = null
            viewModel.startTelegramLogin()
        }

        binding.btnSite.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            if (email.isEmpty() || password.isEmpty()) {
                toast(R.string.auth_fields_required)
            } else {
                lastAttemptWasSite = true
                hideError()
                viewModel.loginSite(email, password)
            }
        }

        binding.btnConfirm2fa.setOnClickListener {
            val token = pendingTempToken
            val code = binding.etCode.text?.toString()?.trim().orEmpty()
            when {
                token == null -> return@setOnClickListener
                code.isEmpty() -> toast(R.string.auth_code_required)
                else -> {
                    lastAttemptWasSite = true
                    hideError()
                    viewModel.submit2fa(token, code)
                }
            }
        }

        binding.btnRegisterSite.setOnClickListener { openRegister() }

        observe()
        showIntro()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.twoFactor.collect { onTwoFactor(it) } }
            }
        }
    }

    private fun render(state: LoginState) {
        when (state) {
            is LoginState.Idle -> showIntro()

            is LoginState.AwaitingTelegram -> {
                openTelegramOnce(state.deepLink)
                showAwaiting()
            }

            is LoginState.Polling -> {
                // AwaitingTelegram мог быть «поглощён» StateFlow до того, как UI его увидел,
                // поэтому открываем deep link и здесь.
                openTelegramOnce(state.deepLink)
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

    private fun onTwoFactor(tempToken: String?) {
        pendingTempToken = tempToken
        if (tempToken == null) {
            binding.layout2fa.visibility = View.GONE
        } else {
            hideError()
            binding.layout2fa.visibility = View.VISIBLE
        }
    }

    private fun showIntro() {
        binding.layoutAwaiting.visibility = View.GONE
        hideError()
    }

    private fun showAwaiting() {
        binding.layoutAwaiting.visibility = View.VISIBLE
        hideError()
    }

    private fun showError(error: ApiError) {
        binding.layoutAwaiting.visibility = View.GONE
        binding.tvError.setText(messageFor(error))
        binding.tvError.visibility = View.VISIBLE
        // Для входа через сайт показываем диагностический диалог с реальной причиной
        // с бэкенда (её можно заскринить), чтобы понять, почему вход не проходит.
        if (lastAttemptWasSite) showSiteErrorDialog(error)
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    /**
     * Диалог с реальной причиной отказа во входе через сайт. Для [ApiError.Server] и
     * [ApiError.Unauthorized] показываем «сырой» detail из тела ответа бэкенда (уже
     * очищенный от токенов/URL в слое данных); для сети/таймаута — дружелюбный RU-текст.
     */
    private fun showSiteErrorDialog(error: ApiError) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_err_dialog_title)
            .setMessage(detailFor(error))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun detailFor(error: ApiError): CharSequence = when (error) {
        is ApiError.Server -> {
            val detail = error.detail?.takeIf { it.isNotBlank() }
            if (detail != null) "HTTP ${error.code}\n$detail" else "HTTP ${error.code}"
        }

        is ApiError.Unauthorized ->
            error.detail?.takeIf { it.isNotBlank() } ?: getString(messageFor(error))

        else -> getString(messageFor(error))
    }

    private fun messageFor(error: ApiError): Int = when (error) {
        is ApiError.Gone -> R.string.auth_err_gone
        is ApiError.ServiceUnavailable -> R.string.auth_err_unavailable
        is ApiError.Network, is ApiError.Timeout -> R.string.auth_err_network
        is ApiError.NotConfigured -> R.string.auth_err_not_configured
        else -> R.string.auth_err_generic
    }

    private fun openTelegramOnce(deepLink: String) {
        if (deepLink.isBlank() || currentDeepLink == deepLink) return
        currentDeepLink = deepLink
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
        } catch (e: ActivityNotFoundException) {
            toastError(R.string.auth_telegram_not_installed)
        }
    }

    /**
     * Открывает страницу регистрации на основном сайте [REGISTER_URL].
     * Это именно сайт (departament.site), а не API-хост из BACKEND_BASE_URL
     * (web.departament.site), поэтому адрес задан явной константой.
     */
    private fun openRegister() {
        val uri = Uri.parse(REGISTER_URL)
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                toastError(R.string.auth_err_not_configured)
            }
        }
    }

    companion object {
        /** Ключ Intent-экстры, задающей режим экрана входа. */
        const val EXTRA_MODE = "login_mode"

        /** Показать только карточку входа через сайт. */
        const val MODE_SITE = "site"

        /** Показать только карточку входа через Telegram. */
        const val MODE_TELEGRAM = "telegram"

        /** Основной сайт для регистрации (не API-хост). */
        private const val REGISTER_URL = "https://departament.site"
    }
}
