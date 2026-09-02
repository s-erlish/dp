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
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
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
 * A · the gate            «Войти через Telegram»  ← contour pill, Telegram glyph #2AABEE
 *     │                   «Войти по почте»          the same pill, envelope glyph
 *     └ awaiting          the universal 56dp ledger row, not a centred hero spinner
 *
 * B · «Вход по почте»     сегмент «Вход | Регистрация» → email + password → «Войти»
 *     ├ регистрация      the same fields plus «Повторите пароль» → «Создать аккаунт»
 *     ├ подтверждение    the letter carries a LINK: a wait, a poll, and no field
 *     ├ 2FA              replaces the password slot; six cells over one real field
 *     └ one row          «Восстановить пароль» → departament.site
 * ```
 *
 * **The redesign pass (2026-08-17).** The owner opened this screen and found the version that
 * predates the handoff: a back chevron pointing right, a filled white «Войти через Telegram» and a
 * text «Войти по почте». All three are gone. The back glyph was never this screen's own — every
 * icon button in the app wears `@anim/press_icon`, whose resting item animates `scaleX` to 1.0 and
 * so undid the `scaleX="-1"` mirror both headers used; it is a real `ic_chevron_left` now. There
 * are no filled surfaces left here: every action is the start screen's 52dp contour pill, so the
 * two sign-in surfaces a user can meet look like one product. And «Войти по почте» is off the
 * screen — the owner's ruling folds e-mail, OTP, 2FA and Google into one «Войти через сайт», which
 * opens this very form. Nothing was deleted to do it. (That name lasted until registration came
 * home and the door was renamed «Войти по почте» — see below.)
 *
 * **Every method that worked before still works**, through the same entry points: a plain launch
 * lands on A, [EXTRA_MODE] = [MODE_SITE] lands straight on B, [MODE_TELEGRAM] lands on A (where
 * Telegram is already the primary), [MODE_TELEGRAM_START] lands on A with the attempt already
 * running, and [EXTRA_LINK] runs the same Telegram flow with the JWT of the account that is already
 * signed in, so the backend links instead of logging a second one in.
 *
 * **Why the fourth mode exists, and why nothing calls it today (2026-08-17).** The Аккаунт tab's
 * signed-out block was rebuilt to look exactly like this gate — same heading, same two contour
 * pills, same order — and its «Войти через Telegram» opened [MODE_TELEGRAM], which is this gate. So
 * the tap answered the question and the answer was the question again: «нажимаешь вход через
 * телеграм, открывается снова окно где предлагается войти через телеграм». [MODE_TELEGRAM_START]
 * was the answer to that: mint the token on entry and land on the awaiting stack, so the method is
 * carried out rather than offered twice.
 *
 * It removed the duplicated CHOICE and left the duplicated SCREEN, which is what the owner reported
 * next: «меня кидает на новое окно где опять кнопки открыть телеграм и вход через сайт, этого быть
 * не должно, ВСЁ ДОЛЖНО ПРОИСХОДИТЬ НА ВКЛАДКЕ АККАУНТ». A tap on a tab that already shows the
 * offer must not push an Activity at all — so the tab runs the flow in place now
 * (`AccountFragment.startTelegramSignIn` -> `TelegramFlow`), exactly as Главная's start screen
 * always has, and this mode has no caller left.
 *
 * **It is kept, and so is the gate.** Refinement is not removal: the entry point moved, the
 * capability did not. A caller that has NOT named the method still wants [MODE_TELEGRAM] — the gate
 * is the screen where the choice is made, «Привязать Telegram» arrives on it, and a caller that HAS
 * named the method but cannot host an overlay still wants [MODE_TELEGRAM_START]. Both surfaces,
 * both stacks and every path through them are untouched and tested by the site route, which shares
 * all of it.
 *
 * **What is deliberately gone**: the `Toast` on a failure the user can act on, and the debug
 * `AlertDialog` that put the raw HTTP body on screen (14-auth.md 13.3, D-14.F). The cause goes to
 * the log; the customer gets a sentence that names the fix.
 *
 * **REGISTRATION CAME HOME (2026-09-02).** «а регистрации внутри приложения не увидел, там
 * регистрация через сайт идёт, а не через приложение» — and the owner was reading the screen
 * correctly: «Создать аккаунт» was a row that opened `departament.site/register` in a browser,
 * because the client carried no register call. It carries one now
 * ([com.v2ray.ang.auth.AuthManager.beginRegister]), so the row is gone and the errand is a SEGMENT
 * at the top of the form — «Вход | Регистрация» — over the same fields, with «Повторите пароль»
 * appearing for the second of them. The door on the way in was renamed with it: «Войти через
 * сайт» became «Войти по почте», because nothing behind it goes to a site any more.
 *
 * **The letter carries a LINK, not a code** (checked by the owner on the live panel). That single
 * fact decides the whole confirmation step: there is nothing to type, so no field is drawn, and the
 * app watches for the link being opened instead of asking about it — the poll re-tries the LOGIN
 * with the credentials just registered, which answers 401 until the address is proved and 200 the
 * moment it is. The user's part is to open the letter; ours is to notice.
 *
 * **AND THE THIRD ERRAND ON THIS FORM (2026-09-02): ПРИВЯЗКА ПОЧТЫ.** The «Почта» row under
 * «Способы входа» named an address when the account had one and did nothing at all when it did
 * not — it opened [MODE_SITE], and the first thing `onCreate` does with a signed-in visitor is
 * `finish()`. It is tapped in exactly one situation: somebody signed in through Telegram wants an
 * address on the account. [MODE_LINK_EMAIL] is that errand.
 *
 * It is the same form with two thirds of it taken away — no segment, no password, no «Восстановить
 * пароль» — because none of that belongs to it: the account exists, the request is signed by its
 * token, and the panel is being asked for a letter rather than for a credential. What is left is
 * one field, one sentence saying what will happen to the account (nothing: it stays the same one),
 * and «Отправить ссылку».
 *
 * **The wait afterwards is the registration's wait**, deliberately and to the pixel — same ledger
 * row, same ring, same success beat, same «Отправить снова» over a tertiary way out. Only the
 * question being asked underneath differs: registration re-tries the LOGIN until the link is
 * opened, this one re-reads the PROFILE until it carries an address
 * ([com.v2ray.ang.auth.AuthManager.beginLinkEmail]).
 *
 * **What is deliberately absent rather than disabled**: magic link, password reset, the browser
 * hand-off and Google. `DepartamentApiClient` carries no call for any of them, so a control here
 * would advertise a feature instead of offering one. The one errand the site *can* finish is a row
 * that names its destination, and it ends where this screen starts: back here, signing in with an
 * email and a password.
 */
class LoginActivity : BaseActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    private val viewModel: AuthViewModel by viewModels()

    /** Which of the two surfaces is on screen. Not a back-stack entry; see [showPage]. */
    private enum class Page { GATE, MAIL }

    private var page = Page.GATE

    /**
     * Which errand surface B is on. It is not a page and not a machine state: the form is the same
     * form either way, and this only decides whether the repeat field is in it, what the button
     * says, and which of the two names the toolbar carries. The segment is its only author.
     */
    private enum class FormMode { SIGN_IN, REGISTER }

    private var formMode = FormMode.SIGN_IN

    /** True in [EXTRA_LINK] mode: the flow attaches Telegram to the session that already exists. */
    private var linkMode = false

    /**
     * True in [MODE_LINK_EMAIL]: the form attaches an ADDRESS to the session that already exists.
     *
     * Not a [FormMode] and not a page. It is a property of the whole screen, fixed at entry and
     * never changed afterwards — the segment that authors [FormMode] is not even on screen here —
     * which is why it is a field beside [linkMode] rather than a third value on that enum. It
     * decides which fields exist, what the button says, what the bar is called, and which of the
     * two questions the wait afterwards is asking.
     */
    private var emailLinkMode = false

    /**
     * True when the gate sits behind the form, i.e. Back on surface B pops to surface A instead of
     * closing the screen. False for [MODE_SITE], which opens the form with nothing behind it, for
     * link mode, which never reaches the form at all, and at entry for [MODE_TELEGRAM_START], whose
     * caller answered the gate's question before this screen was ever created. It decides Back, NOT
     * which surface opens — see [onCreate].
     *
     * [goToMail] also sets it, and that is the one place the form is ever PUSHED from the gate. A
     * page that arrived by the sub-page transition has to leave by reversing it, whatever the entry
     * mode was — closing the screen under a page the gate itself opened would be a different
     * animation than the one the user just watched.
     */
    private var gateReachable = true

    /**
     * True while the Telegram attempt this screen was OPENED WITH is the only thing on it
     * ([MODE_TELEGRAM_START]). Cancelling that attempt leaves nothing behind it: the gate's idle
     * stack asks the question the caller's own button has already answered, so Back closes the
     * screen from there instead of stranding the user on it. Cleared the moment the user starts an
     * attempt himself or takes the gate's «Войти по почте» — from then on the gate is a surface
     * he has actually used, and Back belongs on it.
     */
    private var telegramEntry = false

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
        // The segment's own state. The fields restore themselves (they have ids), so without this
        // a rotation halfway through a registration would hand back the address and both passwords
        // on a form that had quietly become «Вход» — repeat field gone, and its content with it.
        formMode = savedInstanceState?.getString(STATE_FORM_MODE)
            ?.let { saved -> FormMode.entries.firstOrNull { it.name == saved } }
            ?: FormMode.SIGN_IN
        val mode = intent.getStringExtra(EXTRA_MODE)
        telegramEntry = !linkMode && mode == MODE_TELEGRAM_START
        // A SESSION IS PART OF THIS MODE'S DEFINITION: the errand is «attach an address to the
        // account I am signed into», so without one there is no account to attach it to. A launch
        // that arrives signed out therefore lands on the ordinary sign-in form rather than on a
        // form whose request would go out unauthenticated and come back 401.
        emailLinkMode = !linkMode && mode == MODE_LINK_EMAIL && viewModel.isLoggedIn()
        gateReachable = !linkMode && !telegramEntry && !emailLinkMode && mode != MODE_SITE
        // WHICH surface opens is NOT the same question as whether the gate is behind it. All three
        // of MODE_SITE, link mode and MODE_TELEGRAM_START make Back leave the screen rather than
        // pop to the gate — that is what [gateReachable] says — but only MODE_SITE starts on the
        // form. Link mode starts on the GATE: that is where its CTA, its awaiting row and its
        // success beat live, and its two «Войти по почте» buttons are hidden below precisely
        // because the form has no part in it. Deriving the start page from gateReachable therefore
        // opened «Привязать Telegram» on the email form, keyboard up, while startTelegramLogin()
        // sent Telegram over the top of it and the awaiting row was never seen at all.
        // MODE_TELEGRAM_START starts on the GATE for the very same reason: the awaiting stack it
        // is heading for is the gate's own, and it must be the gate's own, so that «Открыть
        // Telegram», the poll and Back behave exactly as they do for a user-started attempt.
        // MODE_LINK_EMAIL falls through to the form, which is the whole of it.
        val startPage = if (gateReachable || linkMode || telegramEntry) Page.GATE else Page.MAIL

        // Already signed in and this is an ordinary sign-in: there is nothing to do here. The two
        // linking errands are the exception and the whole point — it is the signed-in user who
        // attaches Telegram, and the signed-in user who attaches an address.
        if (viewModel.isLoggedIn() && !linkMode && !emailLinkMode) {
            setResult(RESULT_OK)
            finish()
            return
        }

        setupToolbar()
        setupGate()
        setupForm()
        setupRows()
        setupBack()

        showPage(startPage, animate = false)
        observe()

        // Both of these arrive from a tap that has already named Telegram — «Привязать Telegram»
        // on the Аккаунт row, «Войти через Telegram» on its signed-out block — so they start the
        // flow themselves instead of asking for the same tap twice. It is done here, as part of
        // opening, and not by firing a button after layout: the entry mode IS the attempt, so the
        // machine has to be in it before the first frame is drawn rather than be nudged into it
        // afterwards. First creation only: a rotation must not mint a second token.
        if ((linkMode || telegramEntry) && savedInstanceState == null) viewModel.startTelegramLogin()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FORM_MODE, formMode.name)
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
     * Back never traps and never surprises (D-14.E). Priority: the 2FA step returns to the form, a
     * Telegram attempt — being minted or being waited on — returns to the gate's idle stack, the
     * form returns to the gate, and only then does the screen close.
     *
     * Every busy state therefore has a way out, and none of them leaves the screen while something
     * it started is still running.
     *
     * ONE STEP OF THAT LADDER IS MISSING WHEN THE ATTEMPT IS THE ENTRY ([MODE_TELEGRAM_START]).
     * There is no idle stack under it to return to — the user chose Telegram on the Аккаунт tab,
     * and the gate would put that same choice back in front of him, which is precisely the dead
     * step this mode exists to remove. So the cancel still happens, in full and by the same call,
     * and then the screen goes with it: one Back, one errand abandoned, and the tab he came from
     * underneath. Everything the poll owns is released either way, so leaving is not a shortcut
     * past the cleanup — it is what happens after it.
     */
    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = viewModel.state.value
                when {
                    state is AuthUiState.TwoFactor -> viewModel.cancelTwoFactor()

                    state is AuthUiState.TelegramAwaiting || state is AuthUiState.TelegramStarting -> {
                        viewModel.cancelTelegramLogin()
                        if (telegramEntry) SubPage.close(this@LoginActivity)
                    }

                    // The confirmation wait is a step on surface B, so Back leaves the STEP and
                    // stays on the form — the same rung the 2FA code sits on.
                    state is AuthUiState.EmailVerification -> leaveVerification()

                    page == Page.MAIL && gateReachable -> goToGate()

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

        gate.btnGateTelegram.onSingleClick(Haptic.PRESS) {
            // An attempt started HERE is the user's own, made on a gate he is looking at, so Back
            // out of it belongs on that gate rather than out of the screen. Only ever reachable in
            // [MODE_TELEGRAM_START] after the entry attempt has already failed and put the idle
            // stack back on screen with the reason on it.
            telegramEntry = false
            viewModel.startTelegramLogin()
        }
        gate.btnGateOpenTelegram.onSingleClick { reopenTelegram() }
        // Both «Войти по почте» take the identical path. The idle one used to just swap the page,
        // which meant a tap during the token mint left the poll running: the user reached the form,
        // and a second later Telegram opened over it on its own.
        //
        // The ids still say `email` because they always did and an @+id is wired to logic, not to
        // copy; the LABEL is the owner's «Войти по почте», set in the layout. What is behind them
        // is untouched — and it is a door the interface names again: the form it opens now carries
        // registration as well as sign-in, which is why the label names the method and not a site.
        gate.btnGateEmail.onSingleClick { goToMail() }
        gate.btnGateEmailAlt.onSingleClick { goToMail() }
    }

    /** Leaving the gate for the form abandons whatever the gate had in flight. */
    private fun goToMail() {
        val state = viewModel.state.value
        // The success beat runs for 440ms with the awaiting stack — and its «Войти по почте» —
        // still on screen. The sign-in has already happened and the screen is handing back, so a
        // tap in that window must do nothing rather than cancel a finished login and swap to a
        // form that is about to be destroyed.
        if (state is AuthUiState.Success) return
        if (state !is AuthUiState.Idle) viewModel.cancelTelegramLogin()
        // The form is being pushed FROM the gate, by a button that lives on the gate, with the
        // gate's own page transition. That makes the gate the surface behind it whatever the entry
        // mode said — an entry that opened straight into the Telegram attempt has, by this tap,
        // become an entry the user has navigated away from. Back reverses the push; it does not
        // close the screen out from under a page the gate itself opened.
        gateReachable = true
        telegramEntry = false
        // The door says «Войти по почте», so it opens on «Вход» — every time, including after a
        // registration the user backed out of. A button that lands on the other errand because of
        // something that happened before Back was pressed is a button whose label is a coin toss.
        setFormMode(FormMode.SIGN_IN)
        showPage(Page.MAIL)
    }

    /**
     * Leaving the form for the gate abandons a submit in flight. Without this the user would land
     * on the gate with a request still running, and its answer — a failure line, or a success beat
     * played against a check on a page nobody is looking at — would arrive on the wrong surface.
     */
    private fun goToGate() {
        if (viewModel.state.value is AuthUiState.Submitting) viewModel.cancelPending()
        showPage(Page.GATE)
    }

    /**
     * Sets the Latin token in the brand face (D-14.1), when the title has one. The span was the
     * whole brand moment on this screen, and it is why the gate needs no logo, no shield tile and
     * no wordmark lockup: 03-direction.md F17 forbids a shield outside the connect object and
     * 11-app-structure.md 4.3.1 forbids a wordmark competing with the heading, so the product named
     * itself in the one place where a Latin word already existed. The Russian around it stays in
     * the UI face — Space Grotesk maps zero Cyrillic codepoints, so setting it on the whole line
     * would silently hand every Russian glyph to the platform fallback.
     *
     * **NEITHER TITLE CARRIES THE TOKEN TODAY.** The heading is «Вход в аккаунт» by the owner's
     * ruling of 2026-08-17 and link mode's is «Привязать Telegram», so the `indexOf` below misses
     * and the raw string is returned — the branch this function has always had for exactly this.
     * It is kept rather than removed: a title that regains the word gets the brand face back with
     * no code change, and the layout does not depend on either outcome by a pixel.
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
            resetPasswordLine()
            // The repeat is judged against the password, so editing either one re-judges the pair.
            validateConfirm()
            clearScreenError()
            updateSubmitEnabled()
        }
        mail.etConfirm.doAfterTextChanged {
            validateConfirm()
            clearScreenError()
            updateSubmitEnabled()
        }

        // IME «Далее» / «Готово» run the same submit path as the button, through the same gate and
        // the same debounce. The two used to be separate functions that could disagree.
        //
        // THE ADDRESS IS THE LAST FIELD WHEN IT IS THE ONLY FIELD (14-auth.md 6.4). In the link
        // errand «Далее» would move the caret to a password box that is not on screen, i.e. do
        // nothing and say nothing; the key says «Готово» and submits instead. Set here, before the
        // first input connection is opened, so it is never the wrong key even once.
        if (emailLinkMode) mail.etEmail.imeOptions = EditorInfo.IME_ACTION_DONE
        mail.etEmail.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_NEXT -> {
                    mail.etPassword.requestFocus()
                    true
                }

                EditorInfo.IME_ACTION_DONE -> {
                    submit()
                    true
                }

                else -> false
            }
        }
        // The password's own IME key changes with the errand: «Далее» to the repeat field while
        // registering, «Готово» when there is nothing under it. Both land on the same submit().
        mail.etPassword.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_NEXT -> {
                    mail.etConfirm.requestFocus()
                    true
                }

                EditorInfo.IME_ACTION_DONE -> {
                    submit()
                    true
                }

                else -> false
            }
        }
        mail.etConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        // The segment is the only author of [formMode]; everything else reads it.
        mail.segMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            setFormMode(if (checkedId == R.id.seg_register) FormMode.REGISTER else FormMode.SIGN_IN)
        }

        mail.btnSubmit.onSingleClick(Haptic.PRESS) { submit() }
        // One control, two steps, one meaning: leave the step and stay on the screen.
        mail.btnStepBack.onSingleClick {
            when (viewModel.state.value) {
                is AuthUiState.TwoFactor -> viewModel.cancelTwoFactor()
                is AuthUiState.EmailVerification -> leaveVerification()
                else -> Unit
            }
        }

        setupOtp()
        applyFormMode()
        updateSubmitEnabled()
    }

    /**
     * The segment moved. The fields do not: the address and the password the user has already typed
     * are the same address and password for either errand, so switching tabs never clears them.
     *
     * What DOES go is the other errand's verdict — a «Неверная почта или пароль» left over a form
     * that is now creating an account is an answer to a question nobody asked any more.
     */
    private fun setFormMode(mode: FormMode) {
        if (formMode == mode) return
        formMode = mode
        clearScreenError()
        setFieldError(binding.mail.tilConfirm, binding.mail.errConfirm, null)
        applyFormMode()
        updateSubmitEnabled()
    }

    /**
     * Draws whatever [formMode] currently says, and is the ONLY place that does. Called on entry
     * and on every segment change; deliberately safe to call twice.
     *
     * The repeat field is the only structural difference between the two errands, which is why they
     * are a segment over one form rather than two forms: everything else here is a label.
     */
    private fun applyFormMode() {
        val mail = binding.mail
        val register = formMode == FormMode.REGISTER
        val stepped = viewModel.state.value.let {
            it is AuthUiState.TwoFactor || it is AuthUiState.EmailVerification
        }

        // THE LINK ERRAND IS NOT ONE OF THE SEGMENT'S TWO, so it returns before any of the
        // segment's drawing runs. There is nothing to choose between (the account exists), no
        // password to take (the panel is being asked for a letter) and no password to restore, so
        // the segment, both password slots and the «Восстановить пароль» row are all absent rather
        // than present and inert. What the slot holds instead is the sentence that says what the
        // button will do — @id/slot_link_hint, 14-auth.md 6.4.
        if (emailLinkMode) {
            mail.segMode.isVisible = false
            mail.slotPassword.isVisible = false
            mail.slotConfirm.isVisible = false
            mail.slotLinkHint.isVisible = !stepped
            mail.authHairline.isVisible = false
            mail.altMethods.isVisible = false
            mail.btnSubmit.setText(submitLabelRes())
            applyToolbarTitle()
            return
        }
        mail.slotLinkHint.isVisible = false

        mail.segSignin.setTextAppearance(
            if (register) R.style.TextAppearance_App_Title_Medium else R.style.TextAppearance_App_Title_Segment_Active
        )
        mail.segRegister.setTextAppearance(
            if (register) R.style.TextAppearance_App_Title_Segment_Active else R.style.TextAppearance_App_Title_Medium
        )
        if (mail.segMode.checkedButtonId != if (register) R.id.seg_register else R.id.seg_signin) {
            mail.segMode.check(if (register) R.id.seg_register else R.id.seg_signin)
        }

        mail.slotConfirm.isVisible = register && !stepped
        mail.etPassword.imeOptions =
            if (register) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        // imeOptions is read when the input connection is opened, so a field that is ALREADY under
        // the caret keeps the old key until it is asked again. Switching the segment mid-password
        // would otherwise leave «Готово» sitting over a form with one more field to fill.
        if (mail.etPassword.hasFocus()) {
            getSystemService(InputMethodManager::class.java)?.restartInput(mail.etPassword)
        }
        resetPasswordLine()

        // «Восстановить пароль» answers a question only the sign-in errand asks. An account that
        // does not exist yet has no password to restore.
        mail.authHairline.isVisible = !register && !stepped
        mail.altMethods.isVisible = !register && !stepped

        mail.btnSubmit.setText(submitLabelRes())
        applyToolbarTitle()
    }

    /**
     * The reserved line under the password, in its resting state: the RULE while registering («Не
     * менее 8 символов», muted), nothing at all while signing in. It is the same line the failure
     * uses, so stating the rule costs no height and breaking it moves nothing.
     */
    private fun resetPasswordLine() {
        val line = binding.mail.errPassword
        if (formMode == FormMode.REGISTER) {
            line.setTextAppearance(R.style.TextAppearance_App_Caption)
            line.text = getString(R.string.auth_password_hint)
            line.visibility = View.VISIBLE
        } else {
            line.setTextAppearance(R.style.TextAppearance_App_Caption_Error)
            line.text = ""
            line.visibility = View.INVISIBLE
        }
        fieldStrokeDefault?.let { binding.mail.tilPassword.setBoxStrokeColorStateList(it) }
    }

    /** What the one primary control is called right now — state first, then the segment. */
    @StringRes
    private fun submitLabelRes(): Int = when {
        viewModel.state.value is AuthUiState.TwoFactor -> R.string.auth_btn_2fa
        // «Отправить снова» is the waiting screen's action whichever letter is being waited on.
        viewModel.state.value is AuthUiState.EmailVerification -> R.string.auth_verify_resend
        emailLinkMode -> R.string.auth_link_email_submit
        formMode == FormMode.REGISTER -> R.string.auth_btn_register
        else -> R.string.auth_btn_signin
    }

    /** The bar names the page, and on surface B the segment decides which page that is. */
    private fun applyToolbarTitle() {
        binding.toolbar.toolbarTitle.text = when {
            page == Page.GATE -> ""
            // The link errand has no segment to consult: the bar names it for the whole screen.
            emailLinkMode -> getString(R.string.auth_link_email_title)
            formMode == FormMode.REGISTER -> getString(R.string.auth_register_title)
            else -> getString(R.string.auth_site_title)
        }
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
     * The one errand the app still cannot finish itself. It is a row and not a button because it is
     * a navigation, and its subtitle names the destination: a label promising an in-app form would
     * be lying about what the tap does (00-rules.md 9.1).
     *
     * It used to have a twin — «Создать аккаунт», opening `departament.site/register`. That errand
     * is not a navigation any more; it is the segment at the top of this form.
     */
    private fun setupRows() = bindRows(enabled = true)

    /**
     * @param enabled false while a request is in flight. It goes through [RowBinder] rather than
     * through `root.isEnabled`, which is what this used to do: `isEnabled` on a ViewGroup makes the
     * row inert without changing a pixel of it, so the errand looked perfectly tappable and
     * swallowed every tap. The binder takes the whole control to R6's 0.38 and drops the listener.
     */
    private fun bindRows(enabled: Boolean) {
        RowBinder.bind(
            root = binding.mail.rowReset.root,
            title = getString(R.string.auth_row_reset),
            subtitle = getString(R.string.auth_row_reset_sub),
            trailing = RowBinder.Trailing.Chevron,
            enabled = enabled,
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

            // The waiting screen's primary action is «Отправить снова». There is nothing to
            // validate: the address it re-sends to is the one it already sent to.
            is AuthUiState.EmailVerification -> viewModel.resendVerification()

            is AuthUiState.Idle -> {
                if (!validateEmail()) {
                    binding.mail.etEmail.requestFocus()
                    binding.mail.root.smoothScrollTo(0, 0)
                    return
                }
                // The link errand ends here: the address is the whole request, and the session it
                // attaches to is the one the request already carries.
                if (emailLinkMode) {
                    viewModel.requestEmailLink(
                        binding.mail.etEmail.text?.toString()?.trim().orEmpty()
                    )
                    return
                }
                val password = binding.mail.etPassword.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    // The CTA is already dark for an empty password, so this is only reachable
                    // from the IME's «Готово» — which used to move focus to the field the caret
                    // was already in, i.e. do nothing at all and say nothing. The reserved line
                    // below the box is exactly what it is for.
                    setPasswordError(getString(R.string.auth_password_required))
                    binding.mail.etPassword.requestFocus()
                    return
                }
                val email = binding.mail.etEmail.text?.toString()?.trim().orEmpty()
                if (formMode == FormMode.SIGN_IN) {
                    viewModel.loginSite(email, password)
                    return
                }

                // Registration is gated on the panel's own rule (8 characters) rather than on a
                // 400 coming back to say so, and on the repeat matching — the one thing the panel
                // cannot check for us at all.
                if (password.length < MIN_PASSWORD_LENGTH) {
                    setPasswordError(getString(R.string.auth_password_short))
                    binding.mail.etPassword.requestFocus()
                    return
                }
                if (!validateConfirm(force = true)) {
                    binding.mail.etConfirm.requestFocus()
                    return
                }
                viewModel.register(email, password)
            }

            // Something is already in flight; the CTA is not a target in that state.
            else -> Unit
        }
    }

    /**
     * The repeat field's live verdict. Judged only once the user has typed something into it — a
     * «Пароли не совпадают» under an empty box is an accusation about a field nobody has filled in
     * yet — and only in the errand that has the field at all.
     *
     * @param force also judge an EMPTY repeat, for the submit path: the CTA is dark until the two
     * match, so this is reachable from the IME's «Готово» alone, which is exactly when silence
     * would leave the user pressing a key that does nothing.
     */
    private fun validateConfirm(force: Boolean = false): Boolean {
        if (formMode != FormMode.REGISTER) {
            setFieldError(binding.mail.tilConfirm, binding.mail.errConfirm, null)
            return true
        }
        val password = binding.mail.etPassword.text?.toString().orEmpty()
        val confirm = binding.mail.etConfirm.text?.toString().orEmpty()
        val matches = confirm == password
        val judge = force || confirm.isNotEmpty()
        setFieldError(
            binding.mail.tilConfirm,
            binding.mail.errConfirm,
            if (!judge || matches) null else getString(R.string.auth_password_mismatch),
        )
        return matches
    }

    /** A failure on the password line, which is the same line that carries the rule at rest. */
    private fun setPasswordError(message: String) {
        binding.mail.errPassword.setTextAppearance(R.style.TextAppearance_App_Caption_Error)
        setFieldError(binding.mail.tilPassword, binding.mail.errPassword, message)
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
     * form is actually submittable and no 429 cool-down is running.
     *
     * A control that is *working* is the exception, and it is the same exception the gate's CTA
     * already makes (R8, see [setGateLoading]): while the request is in flight — and through the
     * success beat that follows it — the button keeps its accent and its opacity and stops taking
     * taps instead. Disabling it there would dim the fill under its own spinner, and a faded
     * spinner reads as broken rather than as busy; dimming it under the success check would read
     * as a failure at the exact moment the sign-in worked. [setFormBusy] owns the tap-blocking.
     */
    private fun updateSubmitEnabled() {
        val state = viewModel.state.value
        val resending = state is AuthUiState.EmailVerification && state.resending
        if (state is AuthUiState.Submitting || state is AuthUiState.Success || resending) {
            binding.mail.btnSubmit.isEnabled = true
            return
        }
        val ready = when (state) {
            is AuthUiState.TwoFactor ->
                binding.mail.otp.otpInput.text?.length == OTP_LENGTH

            // «Отправить снова» needs nothing typed; it re-sends to an address already accepted.
            is AuthUiState.EmailVerification -> true

            is AuthUiState.Idle -> {
                val email = binding.mail.etEmail.text?.toString()?.trim().orEmpty()
                val password = binding.mail.etPassword.text?.toString().orEmpty()
                val addressed = Patterns.EMAIL_ADDRESS.matcher(email).matches()
                if (emailLinkMode) {
                    // One field, so one condition. There is no password to be non-empty and no
                    // repeat to match.
                    addressed
                } else if (formMode == FormMode.REGISTER) {
                    // R9 layer 1 again, and the reason the rule is PRINTED under the field rather
                    // than only enforced: a control that stays dark without saying why is a
                    // guessing game.
                    addressed && password.length >= MIN_PASSWORD_LENGTH &&
                        binding.mail.etConfirm.text?.toString() == password
                } else {
                    addressed && password.isNotEmpty()
                }
            }

            else -> false
        }
        binding.mail.btnSubmit.isEnabled = ready && !viewModel.rateLimited.value
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
                showVerification(false)
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

            is AuthUiState.EmailVerification -> {
                // ONE WAIT, TWO ERRANDS, and the words are the only thing that separates them:
                // registration is asking the user to prove an address so they can get in, this is
                // asking them to open a link so an address joins an account they are already in.
                binding.mail.verifyTitle.setText(
                    if (emailLinkMode) {
                        R.string.auth_link_email_sent_title
                    } else {
                        R.string.auth_verify_title
                    }
                )
                binding.mail.verifyBody.text = getString(
                    if (emailLinkMode) {
                        R.string.auth_link_email_sent_body
                    } else {
                        R.string.auth_verify_body
                    },
                    state.email,
                )
                showVerification(true)
                // A second letter being asked for is the SUBMIT working, not the wait restarting:
                // the ring keeps turning and the busy state goes on the button, exactly as it does
                // for every other request this screen makes (R8).
                setFormBusy(state.resending)
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

        // The panel's own sentence when it sent one — «Этот email уже зарегистрирован» is a better
        // answer than anything a status code can be turned into — and this app's copy otherwise.
        val message = error.text ?: getString(error.message)
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

    /**
     * 12.1: the two stacks crossfade, the incoming one rising 8dp. Never a height animation — and
     * now never a height CHANGE either. The outgoing stack is left INVISIBLE rather than GONE, so
     * `gate_action_slot` stays measured against the taller of the two and the heading, the CTA and
     * the row that replaces it keep the same baseline through the whole transition.
     */
    private fun showAwaitingStack(awaiting: Boolean) {
        val idle = binding.gate.gateStackIdle
        val wait = binding.gate.gateStackAwaiting
        if (wait.isVisible == awaiting && idle.isVisible != awaiting) return
        val incoming = if (awaiting) wait else idle
        val outgoing = if (awaiting) idle else wait
        if (!awaiting) resetRingBeat()
        crossfade(outgoing, incoming, hideOutgoingAs = View.INVISIBLE)
    }

    private fun setFormBusy(busy: Boolean) {
        val mail = binding.mail
        val twoFactor = viewModel.state.value is AuthUiState.TwoFactor
        mail.pbSubmit.isVisible = busy
        mail.etEmail.isEnabled = !busy && !twoFactor
        mail.etPassword.isEnabled = !busy
        mail.etConfirm.isEnabled = !busy
        mail.otp.otpInput.isEnabled = !busy
        mail.btnStepBack.isEnabled = !busy
        // The segment is a control like any other: it may not switch the errand out from under a
        // request that is already carrying one out.
        mail.segMode.isEnabled = !busy
        mail.segSignin.isEnabled = !busy
        mail.segRegister.isEnabled = !busy
        bindRows(enabled = !busy)
        if (busy) {
            mail.btnSubmit.contentDescription =
                getString(R.string.auth_loading_cd, mail.btnSubmit.text)
            mail.btnSubmit.text = ""
            mail.btnSubmit.isClickable = false
        } else {
            mail.btnSubmit.setText(submitLabelRes())
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
        mail.segMode.isVisible = !active
        mail.btnStepBack.isVisible = active
        mail.btnStepBack.setText(R.string.auth_cancel)
        mail.etEmail.isEnabled = !active
        mail.btnSubmit.setText(submitLabelRes())
        // Leaving the step hands the form back to whichever errand the segment is on — the repeat
        // field and the «Восстановить пароль» row belong to the mode, not to this transition.
        if (active) {
            hideModeExtras()
        } else {
            applyFormMode()
        }

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
     * The confirmation wait REPLACES the form, for the same reason the 2FA step replaces the
     * password slot (6.7): the letter is out, the address is spoken for, and leaving the fields on
     * screen would invite the user to fill in a form whose answer is already on its way to them.
     *
     * Nothing here is a text field, because the letter carries a LINK. The one thing the user can
     * usefully do is open it, and the two controls under the ring are for the two ways that can go
     * wrong: the letter did not arrive («Отправить снова»), or they would rather sign in to an
     * account they already have («Вернуться ко входу»).
     */
    private fun showVerification(active: Boolean) {
        val mail = binding.mail
        // Same guard as [showTwoFactor]: only a real transition does anything, so the Idle render
        // cannot re-run the "leave" branch on every emission.
        if (mail.slotVerify.isVisible == active) return

        mail.segMode.isVisible = !active
        mail.slotEmail.isVisible = !active
        mail.slotPassword.isVisible = !active
        mail.btnStepBack.isVisible = active

        if (active) {
            hideModeExtras()
            // «Вернуться ко входу» is nonsense for somebody who is already signed in: behind this
            // step there is the address form, not a way in. The label says which one it is.
            mail.btnStepBack.setText(
                if (emailLinkMode) R.string.auth_link_email_back else R.string.auth_verify_back
            )
            showPage(Page.MAIL, animate = false)
            // The user's next move is in another app; a keyboard over the sentence telling them so
            // is the one thing this screen must not do.
            hideKeyboard()
            resetVerifyBeat()
            reveal(mail.slotVerify)
            mail.root.smoothScrollTo(0, 0)
        } else {
            mail.slotVerify.isVisible = false
            applyFormMode()
        }
        mail.btnSubmit.setText(submitLabelRes())
    }

    /** The parts of the form that belong to an errand, hidden while a STEP owns the screen. */
    private fun hideModeExtras() {
        binding.mail.slotConfirm.isVisible = false
        binding.mail.slotLinkHint.isVisible = false
        binding.mail.authHairline.isVisible = false
        binding.mail.altMethods.isVisible = false
    }

    /**
     * «Вернуться ко входу», and it does what it says in that order: the segment goes back to «Вход»
     * first, so the form the user lands on is the one the label promised, and only then is the wait
     * abandoned. The account is untouched — the letter it sent is still valid.
     */
    private fun leaveVerification() {
        // The segment belongs to the two sign-in errands; the link errand has none to hand the
        // form back to, and moving one that is not on screen would only re-label a hidden control.
        if (!emailLinkMode) setFormMode(FormMode.SIGN_IN)
        viewModel.leaveVerification()
    }

    private fun resetVerifyBeat() {
        binding.mail.verifyRingArc.alpha = 1f
        binding.mail.verifyRingCheck.alpha = 0f
    }

    /**
     * Page swap inside this host. Surface B enters on the sub-page motion (12.2) so the transition
     * reads like the Activity push the spec describes, and leaves by reversing it.
     */
    private fun showPage(target: Page, animate: Boolean = true) {
        page = target
        val incoming = if (target == Page.MAIL) binding.mail.root else binding.gate.root
        val outgoing = if (target == Page.MAIL) binding.gate.root else binding.mail.root

        applyToolbarTitle()
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
            // …and the link errand has no password field at all, so the address is where the
            // caret lands whether or not one has already been typed.
            val focusOn = if (emailLinkMode || binding.mail.etEmail.text.isNullOrBlank()) {
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
        // The beat plays wherever the user is looking, and both waits own a ring: the gate's
        // Telegram wait and this form's e-mail confirmation. Same arc, same check, same 440ms.
        val onGateRing = page == Page.GATE && binding.gate.gateStackAwaiting.isVisible
        val onVerifyRing = page == Page.MAIL && binding.mail.slotVerify.isVisible
        val animate = animationsEnabled()
        val step = durationOf(R.integer.motion_press_out)

        if (onGateRing || onVerifyRing) {
            val arc = if (onGateRing) binding.gate.gateRingArc else binding.mail.verifyRingArc
            val check = if (onGateRing) binding.gate.gateRingCheck else binding.mail.verifyRingCheck
            if (onVerifyRing) {
                // The credentials for a confirmed registration were typed HERE, so the OS is told
                // they worked; the Telegram wait has none to offer.
                commitAutofill()
                // A «Отправить снова» can still be in flight when the link is finally opened. Its
                // spinner has nothing left to report, and two live indicators during a 440ms
                // confirmation would be the screen contradicting itself.
                binding.mail.pbSubmit.isVisible = false
            }
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
                // Link mode gets its own sentence: «войдите по почте» is not a fix for an account
                // that is already signed in and is only here to attach Telegram.
                viewModel.failLocally(
                    if (linkMode) {
                        R.string.auth_err_link_telegram_missing
                    } else {
                        R.string.auth_err_telegram_missing
                    },
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

    /**
     * @param hideOutgoingAs [View.GONE] for the page swap, where the surface that left must stop
     * taking space, and [View.INVISIBLE] for the gate's two action stacks, where it must keep it.
     */
    private fun crossfade(outgoing: View, incoming: View, hideOutgoingAs: Int = View.GONE) {
        // A swap that arrives while the previous one is still running finds the two views in
        // swapped roles, and the view now coming IN is still carrying the end action that hides
        // it. Whether a cancelled ViewPropertyAnimator runs that action is a platform detail this
        // screen should not be betting on — if it does, the slot is left with both children
        // hidden and nothing scheduled to show either. Clearing both animators first makes the
        // outcome the same under either behaviour: whatever was pending is settled before the new
        // roles are assigned, and the visibility written below is the last word.
        outgoing.animate().cancel()
        incoming.animate().cancel()
        if (!animationsEnabled()) {
            outgoing.visibility = hideOutgoingAs
            incoming.alpha = 1f
            incoming.translationY = 0f
            incoming.isVisible = true
            return
        }
        outgoing.animate().alpha(0f)
            .setDuration(durationOf(R.integer.motion_state_exit))
            .setInterpolator(curve(R.interpolator.ease_standard))
            .withEndAction {
                outgoing.visibility = hideOutgoingAs
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

    /**
     * The IME is asked for on the view's own queue, not inline. Two of the three callers run
     * before the window has a view root — `MODE_SITE` opens straight onto the form from
     * `onCreate` — and an insets controller asked to show the keyboard then does nothing at all,
     * which is why that entry point used to land on a form with no keyboard. Posting also lets the
     * request check that the field still holds focus, so a page swap in between cannot raise a
     * keyboard for a field nobody is on.
     */
    private fun showKeyboard(view: View) {
        view.post {
            if (!view.isAttachedToWindow || !view.hasFocus()) return@post
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
        }
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

        /**
         * Open straight INTO the Telegram attempt: mint the token, hand the deep link to Telegram
         * and sit on the gate's own awaiting stack while the poll runs.
         *
         * For a caller whose control already named the method, where [MODE_TELEGRAM] would answer
         * a tap by re-asking it. **No caller today**: the one it was written for — the Аккаунт
         * tab's «Войти через Telegram» — now runs the flow on the tab itself and pushes nothing,
         * which is the same objection carried one step further. It stays because the objection is
         * about pushing a screen, not about this mode: a caller that names the method and CANNOT
         * host an overlay still wants exactly this, and every line of it is live code shared with
         * link mode, which does mint on entry and does land on the awaiting stack.
         */
        const val MODE_TELEGRAM_START = "telegram_start"

        /**
         * Open the form as «Привязка почты»: one address field for the account that is ALREADY
         * signed in, `POST /client/link-email-request`, then the confirmation wait.
         *
         * It is an [EXTRA_MODE] value rather than a second boolean beside [EXTRA_LINK] because it
         * chooses a surface, which is exactly what that extra is for; [EXTRA_LINK] chooses what the
         * GATE says, and the gate has no part in this errand. Requires a session — see `onCreate`.
         */
        const val MODE_LINK_EMAIL = "link_email"

        /** true → attach Telegram to the session that is already signed in (surface E). */
        const val EXTRA_LINK = "link_telegram"

        /** The Latin token in the gate heading that carries the brand face (D-14.1). */
        private const val BRAND_TOKEN = "departament"

        /** Which errand the form was on, across a rotation. See [FormMode]. */
        private const val STATE_FORM_MODE = "auth_form_mode"

        /**
         * The public site, not the API host. Only the password reset is left here: registration
         * moved into this screen, and a browser hand-off for an errand the app can finish itself
         * would be a worse version of a feature that already exists.
         */
        private const val RESET_URL = "https://departament.site/forgot-password"

        /** The panel's own floor for a new password. The form holds it so a 400 never has to. */
        private const val MIN_PASSWORD_LENGTH = 8

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
