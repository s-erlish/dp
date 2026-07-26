package com.v2ray.ang.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.autofill.AutofillManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLoginBinding
import com.v2ray.ang.ui.component.Haptic
import com.v2ray.ang.ui.component.RowBinder
import com.v2ray.ang.ui.component.SubPage
import com.v2ray.ang.ui.component.ToolbarBinder
import com.v2ray.ang.ui.component.onSingleClick
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.animationsEnabled
import com.v2ray.ang.viewmodel.AuthViewModel
import com.v2ray.ang.viewmodel.AuthViewModel.AuthUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sign-in, rebuilt to 14-auth.md.
 *
 * The screen this replaces showed every method at once — two cards, two filled accent buttons of
 * equal weight, a 2FA block hidden inside the second card, and a centred error line at the bottom of
 * a scroll — so nothing on it was primary and two of its blocks could contradict each other. This
 * one has a hierarchy and a machine:
 *
 * ```
 * A · the gate            «Войти через Telegram»  ← the screen's ONE filled accent
 *     │                   «Войти по почте»          quiet, and the only other thing here
 *     └ awaiting          the universal 56dp ledger row, not a centred hero spinner
 *
 * B · «Вход по почте»     email + password → «Войти»
 *     ├ 2FA               replaces the password slot; six cells over one real field
 *     └ two rows          «Создать аккаунт» / «Восстановить пароль» → departament.site
 * ```
 *
 * **Every method that worked before still works**, through the same entry points: a plain launch
 * lands on A, [EXTRA_MODE] = [MODE_SITE] lands straight on B, [MODE_TELEGRAM] lands on A (where
 * Telegram is already the primary), and [EXTRA_LINK] runs the same Telegram flow with the JWT of the
 * account that is already signed in, so the backend links instead of logging a second one in.
 *
 * **What is deliberately gone**: the `Toast` on a failure the user can act on, and the debug
 * `AlertDialog` that put the raw HTTP body on screen (14-auth.md 13.3, D-14.F). The cause goes to
 * the log; the customer gets a sentence that names the fix.
 *
 * **What is deliberately absent rather than disabled**: registration, magic link, password reset,
 * the browser hand-off and Google. `DepartamentApiClient` carries no call for any of them, so a
 * control here would advertise a feature instead of offering one. The two errands the site *can*
 * finish are rows that name their destination, and they end where this screen starts: back here,
 * signing in with an email and a password.
 */
class LoginActivity : BaseActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val viewModel: AuthViewModel by viewModels()

    /** Which of the two surfaces is on screen. Not a back-stack entry; see [showPage]. */
    private enum class Page { GATE, MAIL }

    private var page = Page.GATE

    /** True in [EXTRA_LINK] mode: the flow attaches Telegram to the session that already exists. */
    private var linkMode = false

    /** False when the caller asked for the form directly ([MODE_SITE]); Back then leaves. */
    private var gateReachable = true

    /** Set once the address has been wrong, after which it is validated live rather than on blur. */
    private var emailWasInvalid = false

    /** The 120ms auto-submit posted when the sixth digit lands; cancelled if the code changes. */
    private var otpAutoSubmit: Job? = null

    /** Guards the OTP watcher while it rewrites its own field to strip non-digits. */
    private var otpSelfEdit = false

    private val otpCells by lazy {
        with(binding.mail.otp) { listOf(otpCell1, otpCell2, otpCell3, otpCell4, otpCell5, otpCell6) }
    }

    /**
     * The field's normal boundary selector, kept so an error stroke can be swapped in and back out
     * without either state being hand-painted from a raw colour.
     */
    private val fieldStrokeDefault by lazy {
        ContextCompat.getColorStateList(this, R.color.field_stroke_selector)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SubPage.installTransitions(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        adjustWindowForKeyboard()

        linkMode = intent.getBooleanExtra(EXTRA_LINK, false)
        val mode = intent.getStringExtra(EXTRA_MODE)
        gateReachable = !linkMode && mode != MODE_SITE

        // Already signed in and this is an ordinary sign-in: there is nothing to do here. Link mode
        // is the exception and the whole point — it is the signed-in user who attaches Telegram.
        if (viewModel.isLoggedIn() && !linkMode) {
            setResult(RESULT_OK)
            finish()
            return
        }

        setupToolbar()
        setupGate()
        setupForm()
        setupRows()
        setupBack()

        showPage(if (gateReachable) Page.GATE else Page.MAIL, animate = false)
        observe()

        // Link mode arrives from a tap that already said «Привязать Telegram», so it starts the
        // flow itself instead of asking for the same tap twice. First creation only: a rotation
        // must not mint a second token.
        if (linkMode && savedInstanceState == null) viewModel.startTelegramLogin()
    }

    // ------------------------------------------------------------------ chrome

    /**
     * The seamless header (00-rules.md 4.8): page background, no elevation, and a hairline that
     * fades in only once content has scrolled under it.
     *
     * The back arrow is re-pointed at the back dispatcher so the arrow and the gesture take the
     * identical path — cancel the poll, cancel 2FA, pop the form, then leave, in that order.
     */
    private fun setupToolbar() {
        ToolbarBinder.bind(root = binding.toolbar.root, title = "", activity = this)
        binding.toolbar.toolbarBack.onSingleClick { onBackPressedDispatcher.onBackPressed() }
    }

    /**
     * Back never traps and never surprises (D-14.E). Priority: the 2FA step returns to the form, an
     * in-flight Telegram wait returns to the gate's idle stack, the form returns to the gate, and
     * only then does the screen close.
     */
    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewModel.state.value is AuthUiState.TwoFactor -> viewModel.cancelTwoFactor()

                    viewModel.state.value is AuthUiState.TelegramAwaiting ->
                        viewModel.cancelTelegramLogin()

                    page == Page.MAIL && gateReachable -> showPage(Page.GATE)

                    else -> SubPage.close(this@LoginActivity)
                }
            }
        })
    }

    /**
     * ADJUST_RESIZE, so the CTA rides above the keyboard instead of hiding behind it. The modern
     * replacement (decorFitsSystemWindows=false plus an inset listener) requires the whole screen to
     * opt out of fitting system windows, which is the shell's decision and not this screen's; the
     * deprecated flag is the form available to an Activity that keeps fitsSystemWindows on its root.
     */
    @Suppress("DEPRECATION")
    private fun adjustWindowForKeyboard() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    // ------------------------------------------------------------------ surface A

    private fun setupGate() {
        val gate = binding.gate
        gate.gateTitle.text = brandedTitle(
            if (linkMode) R.string.auth_link_tg_title else R.string.auth_gate_title
        )
        gate.gateBody.setText(if (linkMode) R.string.auth_link_tg_body else R.string.auth_gate_body)

        if (linkMode) {
            gate.btnGateTelegram.setText(R.string.auth_link_tg_title)
            gate.gateAwaitingBody.setText(R.string.auth_link_awaiting_body)
            // There is no "link by email": the alternative does not exist for this errand, so its
            // two buttons are absent rather than present and dead.
            gate.btnGateEmail.isVisible = false
            gate.btnGateEmailAlt.isVisible = false
        }

        gate.btnGateTelegram.onSingleClick(Haptic.PRESS) { viewModel.startTelegramLogin() }
        gate.btnGateOpenTelegram.onSingleClick { reopenTelegram() }
        gate.btnGateEmail.onSingleClick { showPage(Page.MAIL) }
        gate.btnGateEmailAlt.onSingleClick {
            viewModel.cancelTelegramLogin()
            showPage(Page.MAIL)
        }
    }

    /**
     * «Вход в departament» with the Latin token in the brand face (D-14.1). This span is the whole
     * brand moment on the screen, and it is why the gate needs no logo, no shield tile and no
     * wordmark lockup: 03-direction.md F17 forbids a shield outside the connect object and
     * 11-app-structure.md 4.3.1 forbids a wordmark competing with the heading, so the product names
     * itself in the one place where a Latin word already exists. The Russian around it stays in the
     * UI face — Space Grotesk maps zero Cyrillic codepoints, so setting it on the whole line would
     * silently hand every Russian glyph to the platform fallback.
     *
     * If the owner rejects the mixed face, delete this function and use `setText`; the layout does
     * not change by a pixel.
     */
    private fun brandedTitle(titleRes: Int): CharSequence {
        val raw = getString(titleRes)
        val start = raw.indexOf(BRAND_TOKEN)
        if (start < 0) return raw
        val face = ResourcesCompat.getFont(this, R.font.space_grotesk) ?: return raw
        // The vendored binary is a variable font whose default instance is Light 300; API 28+ can
        // cut the 700 instance the Headline role asks for. Below that it draws at the default,
        // which res/font/space_grotesk.xml already records as known vendoring debt.
        val bold = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(face, 700, false)
        } else {
            face
        }
        return SpannableString(raw).apply {
            setSpan(
                BrandFaceSpan(bold),
                start,
                start + BRAND_TOKEN.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    // ------------------------------------------------------------------ surface B

    private fun setupForm() {
        val mail = binding.mail

        // Validation runs on BLUR, not per keystroke (00-rules.md 7.4) — until the address has been
        // wrong once, after which live feedback is what the user is asking for.
        mail.etEmail.doAfterTextChanged {
            if (emailWasInvalid) validateEmail()
            clearScreenError()
            updateSubmitEnabled()
        }
        mail.etEmail.setOnFocusChangeListener { _, focused -> if (!focused) validateEmail() }
        mail.etPassword.doAfterTextChanged {
            setFieldError(mail.tilPassword, mail.errPassword, null)
            clearScreenError()
            updateSubmitEnabled()
        }

        // IME «Далее» / «Готово» run the same submit path as the button, through the same gate and
        // the same debounce. The two used to be separate functions that could disagree.
        mail.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                mail.etPassword.requestFocus()
                true
            } else {
                false
            }
        }
        mail.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        mail.btnSubmit.onSingleClick(Haptic.PRESS) { submit() }
        mail.btnCancel2fa.onSingleClick { viewModel.cancelTwoFactor() }

        setupOtp()
        updateSubmitEnabled()
    }

    /**
     * The six cells are decoration; `otp_input` is the only real field, the only focusable node and
     * the only thing TalkBack sees. Everything below paints the buffer.
     */
    private fun setupOtp() {
        val input = binding.mail.otp.otpInput
        input.doAfterTextChanged { editable ->
            if (otpSelfEdit) return@doAfterTextChanged
            val raw = editable?.toString().orEmpty()
            val digits = raw.filter { it.isDigit() }.take(OTP_LENGTH)
            if (digits != raw) {
                // Non-digits are dropped silently: a paste that carries a space is a correct paste.
                otpSelfEdit = true
                input.setText(digits)
                input.setSelection(digits.length)
                otpSelfEdit = false
                return@doAfterTextChanged
            }
            paintOtp(error = false)
            setFieldError(null, binding.mail.errOtp, null)
            clearScreenError()
            updateSubmitEnabled()
            scheduleOtpAutoSubmit(digits)
        }
        input.setOnFocusChangeListener { _, _ -> paintOtp(error = false) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    /**
     * The sixth digit submits by itself — nobody types a code and then hunts for a button — after a
     * 120ms settle, so a paste that arrives character by character fires once rather than six times.
     */
    private fun scheduleOtpAutoSubmit(digits: String) {
        otpAutoSubmit?.cancel()
        if (digits.length < OTP_LENGTH) return
        otpAutoSubmit = lifecycleScope.launch {
            delay(OTP_AUTOSUBMIT_DELAY_MS)
            if (viewModel.state.value is AuthUiState.TwoFactor) submit()
        }
    }

    /**
     * Draws the cell states without a bespoke drawable: a resting cell is the field fill plus the
     * 3.43:1 control boundary (`bg_field`), and the next-to-fill cell — or every cell during the
     * 220ms error flash — carries R7's own outer focus ring in its foreground, tinted accent or
     * error. Colour is never the only signal here: the line below the cells says what is wrong.
     */
    private fun paintOtp(error: Boolean) {
        val digits = binding.mail.otp.otpInput.text?.toString().orEmpty()
        val focused = binding.mail.otp.otpInput.hasFocus()
        val ringColor = colorFromAttr(
            if (error) {
                androidx.appcompat.R.attr.colorError
            } else {
                androidx.appcompat.R.attr.colorPrimary
            }
        )
        otpCells.forEachIndexed { index, cell ->
            cell.text = digits.getOrNull(index)?.toString().orEmpty()
            val ringed = error || (focused && index == digits.length)
            cell.foreground =
                if (ringed) ContextCompat.getDrawable(this, R.drawable.focus_ring) else null
            cell.foregroundTintList = if (ringed) ColorStateList.valueOf(ringColor) else null
        }
    }

    /**
     * The two errands the app cannot finish itself. They are rows and not buttons because they are
     * navigations, and their subtitle names the destination: a label promising an in-app form would
     * be lying about what the tap does (00-rules.md 9.1).
     */
    private fun setupRows() {
        RowBinder.bind(
            root = binding.mail.rowRegister.root,
            title = getString(R.string.auth_row_register),
            subtitle = getString(R.string.auth_row_register_sub),
            trailing = RowBinder.Trailing.Chevron,
            onClick = { openInBrowser(REGISTER_URL) },
        )
        RowBinder.bind(
            root = binding.mail.rowReset.root,
            title = getString(R.string.auth_row_reset),
            subtitle = getString(R.string.auth_row_reset_sub),
            trailing = RowBinder.Trailing.Chevron,
            onClick = { openInBrowser(RESET_URL) },
        )
    }

    // ------------------------------------------------------------------ submit + validation

    /** One entry point for the button and for every IME action, so the two can never disagree. */
    private fun submit() {
        when (viewModel.state.value) {
            is AuthUiState.TwoFactor -> {
                val code = binding.mail.otp.otpInput.text?.toString().orEmpty()
                if (code.length == OTP_LENGTH) viewModel.submit2fa(code)
            }

            is AuthUiState.Idle -> {
                if (!validateEmail()) {
                    binding.mail.etEmail.requestFocus()
                    binding.mail.root.smoothScrollTo(0, 0)
                    return
                }
                val password = binding.mail.etPassword.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    binding.mail.etPassword.requestFocus()
                    return
                }
                viewModel.loginSite(
                    binding.mail.etEmail.text?.toString()?.trim().orEmpty(),
                    password,
                )
            }

            // Something is already in flight; the CTA is not a target in that state.
            else -> Unit
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.mail.etEmail.text?.toString()?.trim().orEmpty()
        val valid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
        if (!valid && email.isNotEmpty()) emailWasInvalid = true
        setFieldError(
            binding.mail.tilEmail,
            binding.mail.errEmail,
            if (valid || email.isEmpty()) null else getString(R.string.auth_email_invalid),
        )
        return valid
    }

    /**
     * R9 layer 1: the control is not offered while it cannot work. The submit is live only when the
     * form is actually submittable, nothing is in flight, and no 429 cool-down is running.
     */
    private fun updateSubmitEnabled() {
        val state = viewModel.state.value
        val busy = state is AuthUiState.Submitting
        val ready = when (state) {
            is AuthUiState.TwoFactor ->
                binding.mail.otp.otpInput.text?.length == OTP_LENGTH

            is AuthUiState.Idle -> {
                val email = binding.mail.etEmail.text?.toString()?.trim().orEmpty()
                Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    !binding.mail.etPassword.text.isNullOrEmpty()
            }

            else -> false
        }
        binding.mail.btnSubmit.isEnabled = ready && !busy && !viewModel.rateLimited.value
    }

    // ------------------------------------------------------------------ state rendering

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.error.collect { renderError(it) } }
                launch {
                    viewModel.rateLimited.collect {
                        updateSubmitEnabled()
                        updateGateEnabled()
                    }
                }
            }
        }
    }

    private fun render(state: AuthUiState) {
        when (state) {
            is AuthUiState.Idle -> {
                setGateLoading(false)
                showAwaitingStack(false)
                showTwoFactor(false)
                setFormBusy(false)
            }

            is AuthUiState.TelegramStarting -> {
                setGateLoading(true)
                showAwaitingStack(false)
            }

            is AuthUiState.TelegramAwaiting -> {
                setGateLoading(false)
                showAwaitingStack(true)
                openTelegramOnce(state.deepLink)
            }

            is AuthUiState.Submitting -> setFormBusy(true)

            is AuthUiState.TwoFactor -> {
                setFormBusy(false)
                showTwoFactor(true)
            }

            is AuthUiState.Success -> finishWithBeat()
        }
        updateSubmitEnabled()
        updateGateEnabled()
    }

    /**
     * 5.4, and the one place the gate's CTA is ever taken away: a 429. Offline it stays live on
     * purpose (D-14.2) — every action on this surface needs the network, so disabling them would
     * leave a screen the user cannot act on at all, and the error line names the fix instead.
     */
    private fun updateGateEnabled() {
        binding.gate.btnGateTelegram.isEnabled = !viewModel.rateLimited.value
    }

    private fun renderError(error: AuthViewModel.AuthError?) {
        val gateError = binding.gate.gateError
        val screenError = binding.mail.authError

        if (error == null) {
            gateError.isVisible = false
            screenError.isVisible = false
            return
        }

        val message = getString(error.message)
        when (error.surface) {
            AuthViewModel.Surface.GATE -> {
                gateError.text = message
                revealError(gateError)
                screenError.isVisible = false
            }

            AuthViewModel.Surface.MAIL -> {
                gateError.isVisible = false
                screenError.text = message
                revealError(screenError)
                if (error.credentialFlash) flashCredentials()
            }

            AuthViewModel.Surface.TWO_FACTOR -> {
                gateError.isVisible = false
                screenError.isVisible = false
                binding.mail.errOtp.text = message
                binding.mail.errOtp.visibility = View.VISIBLE
                // The code was wrong, so it is cleared and the keyboard stays up: the next move is
                // to read the authenticator again, not to hunt for the field.
                binding.mail.otp.otpInput.setText("")
                binding.mail.otp.otpInput.requestFocus()
                flashOtp()
            }
        }
    }

    // ------------------------------------------------------------------ visual states

    private fun setGateLoading(loading: Boolean) {
        val button = binding.gate.btnGateTelegram
        binding.gate.pbGateTelegram.isVisible = loading
        if (loading) {
            // R8: the control keeps its width (it is match_parent), drops its label, stops taking
            // input and STAYS at opacity 1 — a faded spinner reads as broken, not as working.
            button.contentDescription = getString(R.string.auth_loading_cd, button.text)
            button.text = ""
            button.icon = null
            button.isClickable = false
        } else {
            button.setText(if (linkMode) R.string.auth_link_tg_title else R.string.auth_btn_telegram)
            button.icon = ContextCompat.getDrawable(this, R.drawable.ic_telegram_24dp)
            button.contentDescription = null
            button.isClickable = true
        }
    }

    /** 12.1: the two stacks crossfade, the incoming one rising 8dp. Never a height animation. */
    private fun showAwaitingStack(awaiting: Boolean) {
        val idle = binding.gate.gateStackIdle
        val wait = binding.gate.gateStackAwaiting
        if (wait.isVisible == awaiting && idle.isVisible != awaiting) return
        val incoming = if (awaiting) wait else idle
        val outgoing = if (awaiting) idle else wait
        if (!awaiting) resetRingBeat()
        crossfade(outgoing, incoming)
    }

    private fun setFormBusy(busy: Boolean) {
        val mail = binding.mail
        val twoFactor = viewModel.state.value is AuthUiState.TwoFactor
        mail.pbSubmit.isVisible = busy
        mail.etEmail.isEnabled = !busy && !twoFactor
        mail.etPassword.isEnabled = !busy
        mail.otp.otpInput.isEnabled = !busy
        mail.btnCancel2fa.isEnabled = !busy
        mail.rowRegister.root.isEnabled = !busy
        mail.rowReset.root.isEnabled = !busy
        if (busy) {
            mail.btnSubmit.contentDescription =
                getString(R.string.auth_loading_cd, mail.btnSubmit.text)
            mail.btnSubmit.text = ""
            mail.btnSubmit.isClickable = false
        } else {
            mail.btnSubmit.setText(if (twoFactor) R.string.auth_btn_2fa else R.string.auth_btn_signin)
            mail.btnSubmit.contentDescription = null
            mail.btnSubmit.isClickable = true
        }
    }

    /**
     * The 2FA step REPLACES the password slot rather than appearing beside it (6.7): the password
     * has already been accepted, so leaving its field on screen would invite the user to retype it.
     * The email stays, read-only, so they can see whose code they are entering.
     */
    private fun showTwoFactor(active: Boolean) {
        val mail = binding.mail
        // Only a real transition does anything. Without this the Idle render would re-run the
        // "leave 2FA" branch on every emission — including the first one, where it would steal
        // focus and raise the keyboard on a gate that has no field at all.
        if (mail.slotOtp.isVisible == active) return

        mail.slotPassword.isVisible = !active
        mail.btnCancel2fa.isVisible = active
        mail.authHairline.isVisible = !active
        mail.altMethods.isVisible = !active
        mail.etEmail.isEnabled = !active
        mail.btnSubmit.setText(if (active) R.string.auth_btn_2fa else R.string.auth_btn_signin)

        if (active) {
            showPage(Page.MAIL, animate = false)
            reveal(mail.slotOtp)
            mail.otp.otpInput.setText("")
            mail.otp.otpInput.requestFocus()
            showKeyboard(mail.otp.otpInput)
        } else {
            mail.slotOtp.isVisible = false
            mail.errOtp.visibility = View.INVISIBLE
            otpAutoSubmit?.cancel()
            paintOtp(error = false)
            // Coming back from the code step, the password field is what the user has to touch
            // next, so it is where the caret lands.
            mail.etPassword.requestFocus()
        }
    }

    /**
     * Page swap inside this host. Surface B enters on the sub-page motion (12.2) so the transition
     * reads like the Activity push the spec describes, and leaves by reversing it.
     */
    private fun showPage(target: Page, animate: Boolean = true) {
        page = target
        val incoming = if (target == Page.MAIL) binding.mail.root else binding.gate.root
        val outgoing = if (target == Page.MAIL) binding.gate.root else binding.mail.root

        binding.toolbar.toolbarTitle.text =
            if (target == Page.MAIL) getString(R.string.auth_email_title) else ""
        // NestedScrollView takes a scroll listener by assignment, so re-attaching per page swaps
        // the hairline's source instead of stacking a second listener, and re-syncs it on the spot.
        ToolbarBinder.attachTo(binding.toolbar.root, incoming)

        if (!animate || !animationsEnabled()) {
            outgoing.isVisible = false
            incoming.alpha = 1f
            incoming.translationY = 0f
            incoming.isVisible = true
        } else {
            crossfade(outgoing, incoming)
        }

        if (target == Page.MAIL && viewModel.state.value is AuthUiState.Idle) {
            // The form opens on the field to fill first — unless the address is already there
            // (they came back from the 2FA step), where the password is next.
            val focusOn = if (binding.mail.etEmail.text.isNullOrBlank()) {
                binding.mail.etEmail
            } else {
                binding.mail.etPassword
            }
            focusOn.requestFocus()
            showKeyboard(focusOn)
        } else if (target == Page.GATE) {
            hideKeyboard()
        }
        updateSubmitEnabled()
    }

    // ------------------------------------------------------------------ success

    /**
     * 12.6: the arc becomes a check, holds 120ms, and the screen hands back. That beat is the only
     * confirmation this flow gets — no success `Toast`, because the surface itself says it.
     */
    private fun finishWithBeat() {
        setResult(RESULT_OK)
        hideKeyboard()
        val onRing = page == Page.GATE && binding.gate.gateStackAwaiting.isVisible
        val animate = animationsEnabled()
        val step = durationOf(R.integer.motion_press_out)

        if (onRing) {
            val arc = binding.gate.gateRingArc
            val check = binding.gate.gateRingCheck
            if (animate) {
                arc.animate().alpha(0f).setDuration(durationOf(R.integer.motion_state)).start()
                check.scaleX = BEAT_CHECK_FROM
                check.scaleY = BEAT_CHECK_FROM
                check.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(step).setDuration(step)
                    .setInterpolator(curve(R.interpolator.ease_out_quint)).start()
            } else {
                arc.alpha = 0f
                check.alpha = 1f
            }
        } else {
            val check = binding.mail.ivSubmitCheck
            binding.mail.pbSubmit.isVisible = false
            binding.mail.btnSubmit.text = ""
            if (animate) {
                check.scaleX = BEAT_CHECK_FROM
                check.scaleY = BEAT_CHECK_FROM
                check.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(step)
                    .setInterpolator(curve(R.interpolator.ease_out_quint)).start()
            } else {
                check.alpha = 1f
            }
            commitAutofill()
        }

        lifecycleScope.launch {
            delay(if (animate) BEAT_TOTAL_MS else BEAT_HOLD_MS)
            SubPage.close(this@LoginActivity)
        }
    }

    /**
     * Tells the OS the credential was accepted so it offers to save it. Missing this call is why
     * nobody was ever prompted to store the password the old screen took.
     */
    private fun commitAutofill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { getSystemService(AutofillManager::class.java)?.commit() }
    }

    // ------------------------------------------------------------------ Telegram

    /**
     * Opens the deep link exactly once per token (invariant 4.3.2): the opened link is held in the
     * ViewModel's SavedStateHandle, so a rotation re-draws the awaiting row without throwing the
     * user back into Telegram.
     *
     * The fallback ladder replaces the old `toastError`, which 00-rules.md 1.4.8 bans for anything
     * the user can act on: the Telegram app, then the same `t.me` URL in a browser (Telegram Web
     * completes the same confirmation, so it is a real fallback and the poll keeps running), then an
     * error line naming the one method still available.
     */
    private fun openTelegramOnce(deepLink: String) {
        if (deepLink.isBlank() || viewModel.openedDeepLink == deepLink) return
        viewModel.openedDeepLink = deepLink
        launchTelegram(deepLink)
    }

    /** «Открыть Telegram» on the awaiting stack: same link, same token, no state change. */
    private fun reopenTelegram() {
        val state = viewModel.state.value
        if (state is AuthUiState.TelegramAwaiting) launchTelegram(state.deepLink)
    }

    private fun launchTelegram(deepLink: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "Telegram is not installed, falling back to the browser", e)
            if (!openInBrowser(deepLink, silent = true)) {
                viewModel.failLocally(
                    R.string.auth_err_telegram_missing,
                    AuthViewModel.Surface.GATE,
                )
            }
        }
    }

    // ------------------------------------------------------------------ browser

    /**
     * A Custom Tab in the app's own colours, falling back to whatever browser exists. Returns false
     * when the device has no browser at all, so the caller can say so instead of failing silently.
     */
    private fun openInBrowser(url: String, silent: Boolean = false): Boolean {
        val uri = Uri.parse(url)
        val dark = Utils.getDarkModeStatus(this)
        val tab = CustomTabsIntent.Builder()
            .setColorScheme(
                if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT
            )
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colorFromAttr(android.R.attr.colorBackground))
                    .build()
            )
            .build()
        try {
            tab.launchUrl(this, uri)
            return true
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "No custom tab host available", e)
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "No browser available", e)
            if (!silent) {
                viewModel.failLocally(
                    R.string.auth_err_browser_missing,
                    if (page == Page.MAIL) AuthViewModel.Surface.MAIL else AuthViewModel.Surface.GATE,
                )
            }
            false
        }
    }

    // ------------------------------------------------------------------ small helpers

    /**
     * A field error is the reserved line below the box plus the box's own boundary going red. The
     * line is INVISIBLE rather than GONE when empty, so showing it never makes the form jump under
     * the thumb, and the stroke swap goes through the field's own colour selector rather than
     * `setError`, which would re-enable Material's helper line and add a second one.
     */
    private fun setFieldError(field: TextInputLayout?, line: TextView, message: String?) {
        line.text = message.orEmpty()
        line.visibility = if (message == null) View.INVISIBLE else View.VISIBLE
        val stroke = if (message == null) {
            fieldStrokeDefault ?: return
        } else {
            ColorStateList.valueOf(colorFromAttr(androidx.appcompat.R.attr.colorError))
        }
        field?.setBoxStrokeColorStateList(stroke)
    }

    private fun clearScreenError() {
        if (viewModel.error.value != null) viewModel.consumeError()
    }

    /** 12.11: both credential borders go to the error colour for 220ms and back. No shake. */
    private fun flashCredentials() {
        val error = ColorStateList.valueOf(colorFromAttr(androidx.appcompat.R.attr.colorError))
        binding.mail.tilEmail.setBoxStrokeColorStateList(error)
        binding.mail.tilPassword.setBoxStrokeColorStateList(error)
        lifecycleScope.launch {
            delay(durationOf(R.integer.motion_state))
            fieldStrokeDefault?.let {
                binding.mail.tilEmail.setBoxStrokeColorStateList(it)
                binding.mail.tilPassword.setBoxStrokeColorStateList(it)
            }
        }
    }

    private fun flashOtp() {
        paintOtp(error = true)
        lifecycleScope.launch {
            delay(durationOf(R.integer.motion_state))
            paintOtp(error = false)
        }
    }

    private fun resetRingBeat() {
        binding.gate.gateRingArc.alpha = 1f
        binding.gate.gateRingCheck.alpha = 0f
    }

    private fun revealError(view: View) {
        if (view.isVisible) return
        view.isVisible = true
        if (!animationsEnabled()) return
        view.alpha = 0f
        view.translationY = -ERROR_RISE_DP * resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f)
            .setDuration(durationOf(R.integer.motion_state))
            .setInterpolator(curve(R.interpolator.ease_standard))
            .start()
    }

    private fun reveal(view: View) {
        view.isVisible = true
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = REVEAL_RISE_DP * resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f)
            .setDuration(durationOf(R.integer.motion_reveal))
            .setInterpolator(curve(R.interpolator.ease_out_quint))
            .start()
    }

    private fun crossfade(outgoing: View, incoming: View) {
        if (!animationsEnabled()) {
            outgoing.isVisible = false
            incoming.alpha = 1f
            incoming.translationY = 0f
            incoming.isVisible = true
            return
        }
        outgoing.animate().alpha(0f)
            .setDuration(durationOf(R.integer.motion_state_exit))
            .setInterpolator(curve(R.interpolator.ease_standard))
            .withEndAction {
                outgoing.isVisible = false
                outgoing.alpha = 1f
            }
            .start()
        incoming.alpha = 0f
        incoming.translationY = REVEAL_RISE_DP * resources.displayMetrics.density
        incoming.isVisible = true
        incoming.animate().alpha(1f).translationY(0f)
            .setDuration(durationOf(R.integer.motion_state))
            .setInterpolator(curve(R.interpolator.ease_standard))
            .start()
    }

    private fun showKeyboard(view: View) {
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun durationOf(token: Int): Long = resources.getInteger(token).toLong()

    private fun curve(interpolator: Int) = AnimationUtils.loadInterpolator(this, interpolator)

    @ColorInt
    private fun colorFromAttr(@AttrRes attr: Int): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return 0
        return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
    }

    /**
     * Swaps the typeface for one run inside a string. `TypefaceSpan(Typeface)` is API 28+ and this
     * app ships to 24, so the span is spelled out rather than imported.
     */
    private class BrandFaceSpan(private val face: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = face
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = face
        }
    }

    companion object {
        /** Intent extra choosing which surface the caller wants. */
        const val EXTRA_MODE = "login_mode"

        /** Open straight on «Вход по почте». */
        const val MODE_SITE = "site"

        /** Open on the gate, where Telegram is already the primary action. */
        const val MODE_TELEGRAM = "telegram"

        /** true → attach Telegram to the session that is already signed in (surface E). */
        const val EXTRA_LINK = "link_telegram"

        /** The Latin token in the gate heading that carries the brand face (D-14.1). */
        private const val BRAND_TOKEN = "departament"

        /** The public site, not the API host: registration and password reset live there. */
        private const val REGISTER_URL = "https://departament.site/register"
        private const val RESET_URL = "https://departament.site/forgot-password"

        private const val OTP_LENGTH = 6
        private const val OTP_AUTOSUBMIT_DELAY_MS = 120L

        /** 12.6: the beat's own length plus the 120ms hold before the screen hands back. */
        private const val BEAT_TOTAL_MS = 440L
        private const val BEAT_HOLD_MS = 120L
        private const val BEAT_CHECK_FROM = 0.9f

        private const val ERROR_RISE_DP = 4f
        private const val REVEAL_RISE_DP = 8f
    }
}
