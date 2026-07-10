package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.BuildConfig
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

        // Восстанавливаем то, что нельзя пересоздать из состояния ViewModel: уже открытый deep link
        // (чтобы поворот экрана НЕ переоткрыл Telegram) и признак попытки входа через сайт.
        if (savedInstanceState != null) {
            currentDeepLink = savedInstanceState.getString(KEY_DEEP_LINK)
            lastAttemptWasSite = savedInstanceState.getBoolean(KEY_LAST_ATTEMPT_SITE)
        }

        // Режим ПРИВЯЗКИ Telegram к уже вошедшему аккаунту (вход через сайт → привязать Telegram).
        // Запрос создания токена уходит с текущим JWT (interceptor), поэтому бэкенд привязывает
        // Telegram к текущему аккаунту, а не логинит отдельный.
        val linkMode = intent.getBooleanExtra(EXTRA_LINK, false)
        val title = if (linkMode) getString(R.string.home_link_telegram) else getString(R.string.auth_title)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = title)

        // Уже вошли — обычный экран входа не нужен и закрывается. Но в режиме привязки НЕ закрываем:
        // именно залогиненный пользователь привязывает Telegram.
        if (viewModel.isLoggedIn() && !linkMode) {
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

        binding.btnSite.setOnClickListener { submitSiteLogin() }
        binding.btnConfirm2fa.setOnClickListener { submit2faCode() }
        binding.btnRegisterSite.setOnClickListener { openRegister() }

        // Живая проверка полей: подсказки об ошибке под каждым полем и активация кнопки только
        // при валидном вводе. Ошибка гаснет, как только пользователь исправляет значение.
        binding.etEmail.doAfterTextChanged {
            val email = it?.toString()?.trim().orEmpty()
            binding.tilEmail.error =
                if (email.isNotEmpty() && !isEmail(email)) getString(R.string.auth_email_invalid) else null
            updateSiteSubmitEnabled()
        }
        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
            updateSiteSubmitEnabled()
        }
        binding.etCode.doAfterTextChanged {
            val code = it?.toString()?.trim().orEmpty()
            binding.tilCode.error =
                if (code.isNotEmpty() && !isSixDigits(code)) getString(R.string.auth_code_invalid) else null
            update2faSubmitEnabled()
        }

        // IME «Готово» отправляет форму прямо из поля, не тянясь к кнопке.
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitSiteLogin()
                true
            } else {
                false
            }
        }
        binding.etCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit2faCode()
                true
            } else {
                false
            }
        }

        // Кнопки отправки неактивны, пока ввод не валиден.
        updateSiteSubmitEnabled()
        update2faSubmitEnabled()

        observe()
        showIntro()

        // В режиме привязки пользователь уже нажал «Привязать Telegram» — сразу запускаем флоу и
        // открываем Telegram, без лишнего повторного тапа. Только при первом создании экрана.
        if (linkMode && savedInstanceState == null) {
            viewModel.startTelegramLogin()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем то, что нельзя восстановить из ViewModel: уже открытый deep link (иначе поворот
        // экрана переоткроет Telegram) и признак попытки входа через сайт.
        outState.putString(KEY_DEEP_LINK, currentDeepLink)
        outState.putBoolean(KEY_LAST_ATTEMPT_SITE, lastAttemptWasSite)
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
            is LoginState.Idle -> {
                setSiteBusy(false)
                showIntro()
            }

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

            // Вход через сайт / 2FA в процессе: спиннер на нужной кнопке, submit заблокированы.
            // НЕ трогаем карточку Telegram и не открываем deep link.
            is LoginState.SiteLoading -> setSiteBusy(true)

            is LoginState.Success -> {
                showLoading()
                toastSuccess(R.string.auth_success)
                setResult(RESULT_OK)
                finish()
            }

            is LoginState.Error -> {
                setSiteBusy(false)
                showError(state.error)
                // Сбрасываем терминальную ошибку, чтобы она не всплывала повторно
                // при каждой повторной подписке (например, после поворота экрана).
                viewModel.consumeError()
            }
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

    /** Validates the site fields and launches email/password login. Shared by the button and IME. */
    private fun submitSiteLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        when {
            email.isEmpty() || password.isEmpty() -> toast(R.string.auth_fields_required)
            !isEmail(email) -> binding.tilEmail.error = getString(R.string.auth_email_invalid)
            else -> {
                lastAttemptWasSite = true
                hideError()
                viewModel.loginSite(email, password)
            }
        }
    }

    /** Validates the 6-digit code and completes the 2FA login. Shared by the button and IME. */
    private fun submit2faCode() {
        val token = pendingTempToken ?: return
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
        when {
            code.isEmpty() -> toast(R.string.auth_code_required)
            !isSixDigits(code) -> binding.tilCode.error = getString(R.string.auth_code_invalid)
            else -> {
                lastAttemptWasSite = true
                hideError()
                viewModel.submit2fa(token, code)
            }
        }
    }

    private fun updateSiteSubmitEnabled() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        binding.btnSite.isEnabled = isEmail(email) && password.isNotEmpty()
    }

    private fun update2faSubmitEnabled() {
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
        binding.btnConfirm2fa.isEnabled = isSixDigits(code)
    }

    private fun isEmail(value: String): Boolean =
        value.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun isSixDigits(value: String): Boolean =
        value.length == 6 && value.all { it.isDigit() }

    private fun showIntro() {
        binding.layoutAwaiting.visibility = View.GONE
        binding.btnTelegram.isEnabled = true
        // Намеренно НЕ вызываем hideError(): render(Error) сбрасывает состояние в Idle сразу после
        // показа ошибки, и showIntro не должен затирать только что показанный текст.
    }

    private fun showAwaiting() {
        binding.layoutAwaiting.visibility = View.VISIBLE
        // Блокируем повторный запуск входа через Telegram, пока идёт ожидание подтверждения.
        binding.btnTelegram.isEnabled = false
        hideError()
    }

    /**
     * Встроенный индикатор занятости для входа через сайт / 2FA: спиннер на нужной кнопке,
     * все кнопки отправки заблокированы. По завершении восстанавливает подписи и активность.
     */
    private fun setSiteBusy(busy: Boolean) {
        binding.btnTelegram.isEnabled = !busy
        if (busy) {
            binding.btnSite.isEnabled = false
            binding.btnConfirm2fa.isEnabled = false
            // Спиннер показываем на той кнопке, которая инициировала запрос.
            val on2fa = binding.layout2fa.isVisible
            binding.pbSite.isVisible = !on2fa
            binding.pbConfirm2fa.isVisible = on2fa
            binding.btnSite.text = if (on2fa) getString(R.string.auth_btn_site) else ""
            binding.btnConfirm2fa.text = if (on2fa) "" else getString(R.string.auth_btn_2fa)
        } else {
            binding.pbSite.isVisible = false
            binding.pbConfirm2fa.isVisible = false
            binding.btnSite.setText(R.string.auth_btn_site)
            binding.btnConfirm2fa.setText(R.string.auth_btn_2fa)
            updateSiteSubmitEnabled()
            update2faSubmitEnabled()
        }
    }

    private fun showError(error: ApiError) {
        binding.layoutAwaiting.visibility = View.GONE
        binding.tvError.setText(messageFor(error))
        binding.tvError.visibility = View.VISIBLE
        // Диагностический диалог с «сырой» причиной отказа — только в отладочной сборке.
        // В релизе пользователь видит понятную строку auth_err_*, а не тело HTTP-ответа.
        if (lastAttemptWasSite && BuildConfig.DEBUG) showSiteErrorDialog(error)
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
        // 401/403 на входе через сайт — почти всегда неверные учётные данные.
        is ApiError.Unauthorized -> R.string.auth_err_credentials
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

        /** true → режим ПРИВЯЗКИ Telegram к уже вошедшему аккаунту (экран не закрывается). */
        const val EXTRA_LINK = "link_telegram"

        /** Основной сайт для регистрации (не API-хост). */
        private const val REGISTER_URL = "https://departament.site"

        /** savedInstanceState: уже открытый deep link Telegram (защита от переоткрытия). */
        private const val KEY_DEEP_LINK = "deep_link"

        /** savedInstanceState: последняя попытка входа была через сайт. */
        private const val KEY_LAST_ATTEMPT_SITE = "last_attempt_site"
    }
}
