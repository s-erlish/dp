# 14 - Sign-in and first run (Android)

**Departament VPN - the whole authentication surface on Android, as one state machine.**

This document owns everything between "the user has no session" and "the user is standing inside the
app with a session". It is a build specification: every dp value, every hex-or-token, every Russian
string, every duration, every state. Nothing here is left to the implementer's judgement, because
the implementer cannot ask questions.

| | |
|---|---|
| Platform | Android (Kotlin + Material 3 + XML views) |
| Paths below are relative to | `/home/user/dp/V2rayNG/app/src/main/` |
| Desktop counterpart | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml` (+ `.axaml.cs`), `Views/OnboardingView.axaml` |
| Owner requests satisfied | `00-rules.md` 0.4.10 (sign-in redesigned from scratch, minimalist), 0.4.6 (seamless sub-page toolbar), 0.4.9 («Привязать Telegram» CTA) |

---

## 0. How to use this document

### 0.1 Precedence

1. `00-rules.md` - law. Tokens, bans, floors, accessibility, copy law.
2. `03-direction.md` - the visual argument. Three signatures, four planes, accent budget.
3. `11-app-structure.md` - where auth lives in the information architecture (4.3.1, 5.1-5.6, 7.2).
4. `22-components.md` - the component vocabulary. Every control on these screens is one of its 15.
5. **This file** - the auth surface itself.

Where this file needed a value that the four above do not carry, it is written here **and** recorded
as a change-control row in section 20 for pasting into `00-rules.md` section 18. Nothing is silently
invented.

### 0.2 What this file replaces

| Artefact | Verdict |
|---|---|
| `res/layout/activity_login.xml` (314 lines, 2 stacked `MaterialCardView`s) | **DELETE** |
| `java/com/v2ray/ang/ui/LoginActivity.kt` (415 lines) | **DELETE** |
| `<activity android:name=".ui.LoginActivity">` in `AndroidManifest.xml` | **DELETE** |
| `res/values/strings_auth.xml` (24 strings) | **REWRITE** - full table in section 11 |
| The four auth buttons in `res/layout/layout_home_empty.xml` (`btn_home_login_tg`, `btn_home_login_site`, `btn_home_link_tg`, and the «или войдите» divider) | **DELETE** - `11-app-structure.md` 5.1 deletes the whole file; auth has one home and it is not Главная |
| The 4-line gate sketch in `23-account-rework.md` 6.7 | **SUPERSEDED** by section 5 here, which is its full form |

### 0.3 Conflicts between the existing documents, resolved here

| # | Conflict | Resolution | Authority |
|---|---|---|---|
| C1 | `23-account-rework.md` 4.2 gives the gate's primary `radius_pill`; `22-components.md` R1 says every labelled button is `radius_button` 16 | **16.** The owner's recorded rejection of capsule CTAs (`GlobalStyles.axaml:3-14`, naming `android_login.jpg` by file) is precedence level 1 | `00-rules.md` 0.1.1 |
| C2 | `11-app-structure.md` 4.3.1 puts three buttons on the gate («Войти через Telegram», «Войти по почте», «Создать аккаунт»); `23-account-rework.md` 6.7 puts two | **Two.** «Создать аккаунт» needs an email field, so it belongs on the form page, not on a gate whose job is one tap. Three stacked actions re-creates the button-stack the owner rejected | `03-direction.md` 7.3 ("No screen has two primary actions"), `distill.md` |
| C3 | `11-app-structure.md` 4.3.1 heading is «Войти в аккаунт»; `23-account-rework.md` 6.7 is «Вход в departament» | **«Вход в departament».** Headings are nouns, buttons are verbs (`00-rules.md` 9.2); and it is the only place the product names itself on this screen, which `13-start-screen.md` 10 already assumes ("the wordmark exists in three places: the desktop title bar, **the sign-in gate**, and О приложении") | `00-rules.md` 9.2 |
| C4 | `23-account-rework.md` 6.7 caps the gate column at 320dp and centres it vertically | **`@dimen/auth_column_max` 440dp, top-anchored intro + bottom-anchored actions.** 320dp never binds on a 320dp device (content is 288dp there); 440 is the cap the desktop already ships (`LoginView.axaml:270`), so one number serves both platforms. A vertically-centred CTA is out of thumb reach on a 6.7-inch phone, which the scene sentence forbids | `03-direction.md` 2.1, `00-rules.md` 7.2 |
| C5 | `11-app-structure.md` 4.3.1 method segment reads «Пароль \| Код из письма»; the backend endpoint (`/client/auth/magic-link/request`) emails a **link**, not a code | **«Пароль \| Ссылка на почту».** A label must not lie about what arrives | `00-rules.md` 9.1 |
| C6 | `11-app-structure.md` 5.6 hands off to **Главная** after sign-in | **Hands off to Аккаунт.** The overlay exists to cover an account import; revealing an unchanged Главная makes the cover pointless, and the user signed in from Аккаунт. Change-control row D-14.4 | this file, section 10.4 |
| C7 | The desktop ships a permanently disabled «Продолжить с Google · Скоро» | **Not on Android.** A permanently disabled control is an advertisement, not an affordance. The Google row exists only when Google is actually configured | `distill.md`, `03-direction.md` F15 |

---

## 1. The job

### 1.1 What the user is doing

> He installed the app twenty seconds ago because a friend sent him a Telegram link. He has an
> account on departament.site that he made in a browser three weeks ago, or he has nothing at all.
> He does not remember which. He wants to be connected. He is not here to "sign in"; signing in is a
> tax he is willing to pay once if it takes one tap, and is willing to abandon if it takes six.

Consequences, and every one of them is testable:

1. **One tap must be enough** for the majority path. On a Russian Android phone Telegram is
   installed, and the deep-link flow requires zero typing, zero recall and zero password manager.
   That is the primary path and it gets the screen's one filled accent.
2. **The app must work without any of this.** QR and clipboard import are first-class and live in
   Серверы. There is no wall (`11-app-structure.md` 5.1). Auth is a destination, never a gate on
   launch.
3. **The rest of the methods are a long tail**, not a menu. Email+password, magic link, browser
   hand-off, Google, registration, password reset and 2FA together account for a minority of
   sessions and must occupy a minority of the pixels.

### 1.2 Why the current screen fails

`res/layout/activity_login.xml` shows **every method at once**: two `MaterialCardView`s, two filled
accent buttons of identical weight, one outlined button, two `ProgressBar`s, a hidden 2FA block
inside the second card, and a centred error `TextView` at the bottom. Measured defects:

| Defect | Evidence | Rule broken |
|---|---|---|
| Two filled accent surfaces on one screen | `btn_telegram` and `btn_site`, both `backgroundTint="?attr/colorPrimary"` (`activity_login.xml:63,201`) | `00-rules.md` 4.3, `03-direction.md` 5.2 |
| Off-scale radius appearing nowhere else in the product | `cornerRadius="26dp"` ×4 (`:64,:105,:202,:273`) | `00-rules.md` 3.2, `03-direction.md` 4.5 |
| Synthetic bold on a variable font | `android:textStyle="bold"` ×3 (`:62,:200,:271`) | `00-rules.md` 5.4, `03-direction.md` F4 |
| Fixed button height, clips at font scale 200% | `android:layout_height="52dp"` ×3 | `22-components.md` R2 |
| No hierarchy: every method is a card with a headline, a description and a full-width button | the whole file | `03-direction.md` 3.3 (cards are for objects), F2 |
| No state machine: 2FA is a `visibility` toggle inside the email card, awaiting is a `visibility` toggle inside the Telegram card, and the two can be visible simultaneously | `LoginActivity.kt:224-228, 285-290` | `00-rules.md` 15 |
| Error is a centred grey-red line at the very bottom of a scroll, often below the fold | `activity_login.xml:301-311` | `00-rules.md` 9.4, 7.4 |
| `Toast` used for actionable failures | `toastError(R.string.auth_telegram_not_installed)` (`LoginActivity.kt:371`) | `00-rules.md` 1.4.8 |
| A debug-only `AlertDialog` dumping the raw HTTP body | `LoginActivity.kt:335-341` | `00-rules.md` 9.4 (no codes visible), 7.6 |
| Brand face absent from the most-pressed control in the product | zero `textAppearance` on any button | `22-components.md` R3 |

### 1.3 The three sentences this design is built on

1. **The gate is not a screen, it is a state of the Аккаунт tab**, and it contains exactly one lit
   element (`11-app-structure.md` 4.3.1, `03-direction.md` 3.2).
2. **Every method except the primary lives one level down**, behind a single quiet text button, and
   the tail of that tail lives behind one row that opens one sheet
   (`00-rules.md` 7.6: inline > row > sheet > dialog).
3. **The wait is a ledger row.** The Telegram confirmation wait is drawn as the product's universal
   56dp row - 40dp leading slot, 68dp text origin - so that the most unusual moment in the app is
   built from its most ordinary part (`03-direction.md` 3.3).

---

## 2. First run is not a screen

`11-app-structure.md` 5.1 is absolute: cold start lands on Главная, there is no onboarding gate, and
`OnboardingView.axaml` / `layout_home_empty.xml` are deleted. This section states what carries
first-run instead, so that no one re-introduces a welcome carousel.

### 2.1 The three surfaces that teach

| Surface | Owned by | What it says at first run | Where its action goes |
|---|---|---|---|
| Главная, header row, signed out | `13-start-screen.md` 10 | neutral 40dp tile + «Аккаунт» / «Вход, подписка, устройства» | the Аккаунт tab (a navigation row, **not** a CTA - it must not say «Войти») |
| Главная, gate block, variant A | `13-start-screen.md` 8.2 | «Войдите, чтобы получить серверы Departament.» + Primary «Войти» + Tertiary «Добавить провайдера» | «Войти» **switches to the Аккаунт tab**. It never pushes a screen |
| Серверы, empty state | `22-components.md` 15 | «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» / «Добавить провайдера» | the add-provider sheet |

That is the entire first-run curriculum: **two things exist - войти and подключиться** - and each is
taught by the surface that owns it, at the moment it is relevant. No tutorial, no carousel, no
"Приветствуем!", no progress dots, no skip button (there is nothing to skip).

### 2.2 What must never be added back

- A welcome screen, a value-proposition carousel, or a "1 of 3" stepper.
- A modal on launch of any kind, including the VPN permission prompt (it is raised by the first
  connect attempt).
- A blocking sign-in wall, including a "soft" one that can be dismissed.
- A second place that says «Войти» on the same screen as the gate block.

---

## 3. The surface map

Four surfaces. That is the whole auth product.

```
Аккаунт tab (destination, always present)
│
├─ A · THE GATE                      state of AccountFragment, no push
│     ├ Primary   «Войти через Telegram»          ← the one lit element
│     └ Tertiary  «Войти по почте»                → pushes B
│
├─ B · ВХОД ПО ПОЧТЕ                 sub-page, level 1, seamless toolbar
│     ├ mode: вход | регистрация                  (toggled by one tertiary)
│     ├ method segment: Пароль | Ссылка на почту   (sign-in mode only)
│     ├ states: form · 2FA · отправлено · загрузка · ошибка
│     └ Row      «Другой способ входа»            → opens C
│
├─ C · ДРУГОЙ СПОСОБ ВХОДА           bottom sheet
│     ├ «Через сайт»          → Custom Tab → depv://auth/{code}
│     ├ «У меня есть код»     → inline field back on B
│     └ «Через Google»        → Credential Manager   (only when configured)
│
└─ D · ДОБАВЛЯЕМ АККАУНТ             full-bleed overlay on the shell, after success
```

Plus one satellite, because it is the same machine with a different terminal state:

```
Аккаунт (signed in) › group «Вход» › row «Telegram» › action «Привязать»
└─ E · ПРИВЯЗКА TELEGRAM             bottom sheet, reuses A's awaiting row verbatim
```

**Depth check** (`03-direction.md` 7.3, max 2 levels below a tab): Аккаунт → B is level 1. C, D and
E are sheets and overlays, which are not levels. Nothing in auth reaches level 2.

**Card count**: zero. Neither A nor B is a card. `03-direction.md` 10.1 is explicit: *"the sign-in
form is not an object floating on a surface; it is the screen."*

**Accent count**: exactly one filled accent surface is visible at any instant, on any of the five
surfaces. Section 19 checks this state by state.

---

## 4. The state machine

### 4.1 The states

One sealed hierarchy, `AuthUiState`, owned by `AuthViewModel` and survived across configuration
change. Every UI decision in sections 5-10 is a pure function of this state plus the two form
buffers (`email`, `password`).

```
IDLE ─────────────┐
                  │
 ┌────────────────┴─────────────────────────────────────────────┐
 │ TELEGRAM                                                     │
 │  Tg.Starting      creating the login token (no deep link yet) │
 │  Tg.Awaiting(link, since)   deep link opened, polling 2s      │
 │  Tg.Timeout       3 min elapsed with no confirmation          │
 └──────────────────────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────────────────────┐
 │ EMAIL                                                        │
 │  Mail.Form(mode, method)   mode∈{SignIn,Register}            │
 │                            method∈{Password,Link}            │
 │  Mail.Submitting           POST /login or /register or       │
 │                            /magic-link/request or            │
 │                            /password-reset/request           │
 │  Mail.TwoFactor(tempToken) 6-digit TOTP step                 │
 │  Mail.TwoFactorSubmitting                                    │
 │  Mail.Sent(kind, address)  kind∈{Magic,Verify,Reset}         │
 └──────────────────────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────────────────────┐
 │ HANDOFF                                                      │
 │  Handoff.Browser           Custom Tab is open, app backgrounded│
 │  Handoff.Redeeming(code)   POST /app-handoff/consume          │
 │  Handoff.ManualEntry       the paste-a-code field is showing   │
 └──────────────────────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────────────────────┐
 │ GOOGLE                                                       │
 │  Google.Picking            Credential Manager sheet is up     │
 │  Google.Submitting         POST /client/auth/google           │
 └──────────────────────────────────────────────────────────────┘

 SUCCESS(profile)   → the beat (12.6) → SYNCING → the hand-off (10)
 FAILED(cause, at)  cause: ApiError; at: which surface raised it
```

`FAILED` is **not** a screen. It is a decoration on whichever surface was active: the error line on A
or B, the field error on B, or the overlay's error column on D.

### 4.2 The transition table

Every arrow in the product. `→A` means "renders on surface A".

| From | Event | To | Renders |
|---|---|---|---|
| `Idle` | tap «Войти через Telegram» | `Tg.Starting` | A, CTA loading (12.4) |
| `Tg.Starting` | token received | `Tg.Awaiting` | A, action stack crossfades (12.1) + `ACTION_VIEW` fires |
| `Tg.Starting` | `ApiError` | `FAILED(cause, Gate)` | A, error line (12.5) |
| `Tg.Awaiting` | poll returns `Confirmed` | `Success` | A, ring beat (12.6a) |
| `Tg.Awaiting` | poll returns `Expired` | `FAILED(Gone, Gate)` | A, back to idle stack + error line |
| `Tg.Awaiting` | 180 s elapsed | `Tg.Timeout` | A, back to idle stack + error line |
| `Tg.Awaiting` | tap «Открыть Telegram» | `Tg.Awaiting` | re-fires `ACTION_VIEW` on the same link, no state change |
| `Tg.Awaiting` | tap «Войти по почте» | `Mail.Form(SignIn, Password)` | poll cancelled, B pushed |
| `Tg.Awaiting` | system Back | `Idle` | poll cancelled, A idle stack. **Back never leaves the tab from here** |
| `Idle` | tap «Войти по почте» | `Mail.Form(SignIn, Password)` | B pushed (12.7) |
| `Mail.Form` | segment → «Ссылка на почту» | `Mail.Form(SignIn, Link)` | B, method slot swaps (12.9) |
| `Mail.Form` | tap «Создать аккаунт» | `Mail.Form(Register, Password)` | B, mode swaps, toolbar title changes |
| `Mail.Form(Register)` | tap «У меня уже есть аккаунт» | `Mail.Form(SignIn, Password)` | B |
| `Mail.Form` | submit | `Mail.Submitting` | B, CTA loading |
| `Mail.Submitting` | `LoginResult.Success` | `Success` | B, CTA beat (12.6b) |
| `Mail.Submitting` | `LoginResult.Requires2FA` | `Mail.TwoFactor` | B, 2FA reveal (12.10) |
| `Mail.Submitting` | register accepted | `Mail.Sent(Verify, email)` | B, pending block |
| `Mail.Submitting` | magic-link accepted | `Mail.Sent(Magic, email)` | B, pending block |
| `Mail.Submitting` | reset accepted | `Mail.Sent(Reset, email)` | B, pending block |
| `Mail.Submitting` | `ApiError` | `FAILED(cause, Email)` | B, error line + field flash (12.11) |
| `Mail.TwoFactor` | submit 6 digits | `Mail.TwoFactorSubmitting` | B, CTA loading |
| `Mail.TwoFactorSubmitting` | ok | `Success` | B, CTA beat |
| `Mail.TwoFactorSubmitting` | `Unauthorized` | `FAILED(Unauthorized, TwoFactor)` | B, OTP cells error border, code cleared, focus kept |
| `Mail.TwoFactor` | tap «Отмена» / system Back | `Mail.Form(SignIn, Password)` | B, password field returns, `tempToken` dropped |
| `Mail.Sent` | tap «Отправить снова» | `Mail.Submitting` | B, tertiary loading; a 30 s re-send cooldown applies (5.6) |
| `Mail.Sent` | tap «Вернуться ко входу» | `Mail.Form(SignIn, Password)` | B |
| any B state | tap row «Другой способ входа» | unchanged | C opens |
| C | tap «Через сайт» | `Handoff.Browser` | C dismisses, Custom Tab launches |
| `Handoff.Browser` | `depv://auth/{code}` received | `Handoff.Redeeming` | D directly (may arrive cold, 9.3) |
| `Handoff.Browser` | app resumed with no callback | `Mail.Form` | B, no error - the user simply came back |
| C | tap «У меня есть код» | `Handoff.ManualEntry` | C dismisses, inline field appears on B and takes focus |
| `Handoff.ManualEntry` | submit | `Handoff.Redeeming` | B, CTA loading |
| `Handoff.Redeeming` | ok | `Success` | current surface, beat |
| C | tap «Через Google» | `Google.Picking` | Credential Manager sheet |
| `Google.Picking` | idToken | `Google.Submitting` | B, CTA loading |
| `Google.Picking` | user dismissed | `Mail.Form` | B, **no error** |
| `Success` | beat complete (≤ 400 ms) | `Syncing` | D fades in (12.12) |
| `Syncing` | import complete | - | D fades out to Аккаунт at 450 ms (12.13) |
| `Syncing` | import failed | `FAILED(cause, Sync)` | D, error column |
| any | `BackendConfig.isConfigured() == false` | - | the Аккаунт destination is removed **at start-up** and never re-appears (`23-account-rework.md` 5.1). There is no runtime state for this |

### 4.3 Invariants the machine must hold

1. **At most one method is in flight.** Starting any method cancels the previous job
   (`AuthViewModel.loginJob?.cancel()`), including the Telegram poll.
2. **The Telegram deep link opens once per token.** The already-opened link is held in
   `SavedStateHandle` so a rotation does not re-launch Telegram. This behaviour exists today
   (`LoginActivity.kt:365-373`, `KEY_DEEP_LINK`) and is preserved verbatim.
3. **`FAILED` is consumed exactly once.** After rendering, the ViewModel resets to the state the
   surface was in, so a rotation does not re-raise the error. Today's `consumeError()`
   (`LoginActivity.kt:216`) is the correct pattern and moves into the ViewModel.
4. **Every submit is guarded** by `input_debounce` 500 ms (`22-components.md` R9), and the CTA is
   disabled for the whole in-flight window.
5. **Nothing in this machine writes to disk except `AccountSession.onAuthenticated`.** No
   half-authenticated state is ever persisted.

---

## 5. Surface A - the gate

### 5.1 Frame

The gate is `res/layout/layout_account_gate.xml`, inflated by `AccountFragment` into the tab's
content area whenever `AccountSession` has no token. The tab's own 56dp header («Аккаунт», Title
16/700 at the gutter, P0, no elevation, no divider) stays exactly as it is on the signed-in tab -
the gate does not get its own toolbar.

```
NestedScrollView  id=gate_scroll
  android:fillViewport="true"
  android:scrollbars="none"
  android:paddingStart="@dimen/screen_gutter"     16
  android:paddingEnd="@dimen/screen_gutter"       16
  (bottom padding = IME inset, applied by ViewCompat.setOnApplyWindowInsetsListener)
│
└ LinearLayout  orientation=vertical  layout_height=wrap_content
  │
  ├ LinearLayout  id=gate_intro   orientation=vertical
  │   layout_marginTop="@dimen/space_32"          32
  │   layout_gravity="start"   maxWidth="@dimen/auth_column_max"   440
  │   │
  │   ├ TextView  id=gate_title
  │   │    textAppearance=@style/TextAppearance.App.Headline     24sp / 700 / -0.01em
  │   │    textColor="?attr/colorOnSurface"                      #F2F4F8  17.9:1 on P0
  │   │    text=@string/auth_gate_title    «Вход в departament»
  │   │    maxLines=2  accessibilityHeading=true
  │   │
  │   └ TextView  id=gate_body
  │        layout_marginTop="@dimen/space_8"      8
  │        textAppearance=@style/TextAppearance.App.Body         14sp / 400
  │        textColor="?attr/colorOnSurfaceVariant"               #9BA1AD  7.0:1 on P0
  │        text=@string/auth_gate_body   «Здесь будут подписка, устройства и платежи.»
  │        maxLines=3
  │
  ├ Space  id=gate_spacer
  │    layout_height=0dp  layout_weight=1  minHeight="@dimen/space_32"    32
  │
  └ LinearLayout  id=gate_actions  orientation=vertical
       layout_marginBottom="@dimen/space_32"      32
       │
       ├ TextView  id=gate_error                  (section 5.5)
       │    layout_marginBottom="@dimen/space_12"  12
       │    textAppearance=@style/TextAppearance.App.Body        14sp / 400
       │    textColor="@color/ping_bad"                          #FF6069  6.15:1
       │    visibility=gone   accessibilityLiveRegion=polite
       │
       └ FrameLayout  id=gate_action_slot          (the crossfade host, 12.1)
            ├ LinearLayout id=gate_stack_idle       (5.2)
            └ LinearLayout id=gate_stack_awaiting   (5.3, visibility=gone)
```

**Why two anchors.** The intro is top-anchored because it is the screen's heading and must sit where
every other screen's first line sits. The actions are bottom-anchored because the scene sentence
(`03-direction.md` 2.1) puts this screen in one hand on a moving train, and a 52dp CTA floating at
250dp from the top of a 6.7-inch phone is out of thumb reach. The space between them is not left
over; it is the composition. `fillViewport="true"` plus `layout_weight=1` on the spacer means that
on a short viewport (landscape, split screen, font scale 200%) the spacer collapses to its 32dp
minimum and the whole column scrolls - nothing is ever clipped and nothing overlaps.

**The brand moment.** `gate_title` is a `SpannableString`. The substring `departament` is wrapped in
a `CustomTypefaceSpan` carrying `@font/space_grotesk` at `wght` 700; the Russian «Вход в » stays in
the UI face. This is signature one (`03-direction.md` 3.1) applied at the only place on the screen
where a Latin token exists, and it is why the gate needs no logo, no shield tile and no wordmark
lockup. `03-direction.md` F17 forbids a shield outside the connect object; `11-app-structure.md`
4.3.1 forbids a wordmark competing with the heading. This satisfies both, at zero pixels of chrome.

> If the owner rejects the mixed-face heading, the fallback is one line of code (drop the span). The
> layout is byte-identical. Decision D-14.1.

### 5.2 The idle action stack

```
LinearLayout  id=gate_stack_idle  orientation=vertical  layout_width=match_parent
│
├ MaterialButton  id=btn_gate_telegram
│    style=@style/Widget.Departament.Button.Primary.Tall
│    layout_width=match_parent   layout_height=wrap_content
│    android:minHeight="@dimen/btn_height_tall"     52
│    app:cornerRadius="@dimen/radius_button"        16
│    android:insetTop="0dp"  android:insetBottom="0dp"
│    app:backgroundTint="?attr/colorPrimary"        #4C8DFF
│    android:textColor="?attr/colorOnPrimary"       #00183A   5.51:1
│    android:textAppearance="@style/TextAppearance.App.Title"   16sp / 700
│    app:icon="@drawable/ic_telegram_24dp"  app:iconSize="20dp"
│    app:iconGravity="textStart"  app:iconPadding="@dimen/space_8"   8
│    app:iconTint="?attr/colorOnPrimary"
│    android:text="@string/auth_btn_telegram"   «Войти через Telegram»
│    android:stateListAnimator="@anim/press_scale"
│
└ MaterialButton  id=btn_gate_email
     style=@style/Widget.Departament.Button.Tertiary
     layout_width=match_parent   layout_height=wrap_content
     layout_marginTop="@dimen/space_12"              12
     android:minHeight="@dimen/btn_height"           48
     app:cornerRadius="@dimen/radius_button"         16
     android:textColor="?attr/colorPrimary"          #4C8DFF   6.64:1 on P0
     android:textAppearance="@style/TextAppearance.App.Title.Medium"   16sp / 500
     android:text="@string/auth_btn_email"      «Войти по почте»
     android:stateListAnimator="@anim/press_scale"
```

The Telegram glyph is tinted `colorOnPrimary`, **never** Telegram's own blue: a second brand hue on
the product's one accent surface is banned (`00-rules.md` 1.4.1).

Stack height: 52 + 12 + 48 = **112dp**.

### 5.3 The awaiting action stack

The wait is drawn as the product's universal row (`03-direction.md` 3.3): 16 gutter, 40 leading
slot, 12, text column. Text origin **68dp**, identical to every list row in the app.

```
LinearLayout  id=gate_stack_awaiting  orientation=vertical  visibility=gone
│
├ LinearLayout  id=gate_awaiting_row  orientation=horizontal
│    android:minHeight="@dimen/row_min_height"      56
│    android:gravity="center_vertical"
│    android:accessibilityLiveRegion="polite"
│    │
│    ├ FrameLayout  id=gate_ring   layout_width=40dp  layout_height=40dp
│    │    ├ ProgressBar  id=gate_ring_arc
│    │    │    style=@style/Widget.Departament.Progress.Ring
│    │    │    layout_width=40dp  layout_height=40dp
│    │    │    indeterminate=true
│    │    │    app:trackThickness="2dp"
│    │    │    app:trackColor="?attr/colorOutlineVariant"      #20242B
│    │    │    app:indicatorColor="?attr/colorPrimary"         #4C8DFF
│    │    │    app:indicatorSize="40dp"
│    │    ├ ImageView  id=gate_ring_full        40dp, @drawable/ring_full_2dp,
│    │    │    tint=?attr/colorPrimary, alpha=0      (success beat only)
│    │    └ ImageView  id=gate_ring_check       20dp, @drawable/ic_check,
│    │         layout_gravity=center, tint=?attr/colorPrimary, alpha=0
│    │
│    └ LinearLayout  orientation=vertical  layout_weight=1
│         layout_marginStart="@dimen/space_12"      12
│         ├ TextView  id=gate_awaiting_title
│         │    textAppearance=@style/TextAppearance.App.Title        16sp / 700
│         │    textColor="?attr/colorOnSurface"
│         │    text=@string/auth_awaiting_title   «Ждём подтверждения в Telegram»
│         │    maxLines=2
│         └ TextView  id=gate_awaiting_body
│              layout_marginTop="@dimen/space_4"    4
│              textAppearance=@style/TextAppearance.App.Subtitle     13sp / 400
│              textColor="?attr/colorOnSurfaceVariant"
│              text=@string/auth_awaiting_body
│                   «Подтвердите вход в открывшемся приложении»
│              maxLines=2
│
├ MaterialButton  id=btn_gate_open_telegram
│    style=@style/Widget.Departament.Button.Tertiary
│    layout_width=match_parent  layout_marginTop="@dimen/space_12"   12
│    minHeight=48   text=@string/auth_open_telegram    «Открыть Telegram»
│
└ MaterialButton  id=btn_gate_email_alt
     style=@style/Widget.Departament.Button.Tertiary
     layout_width=match_parent  layout_marginTop="@dimen/space_12"   12
     minHeight=48   text=@string/auth_btn_email        «Войти по почте»
```

Stack height: 56 + 12 + 48 + 12 + 48 = **176dp**. The stack is bottom-anchored, so the 64dp
difference from the idle stack is absorbed by `gate_spacer` above; the intro block does not move.

**There is no «Начать заново» button.** Restarting is what the primary CTA does, and the timeout
state returns the user to the idle stack where that CTA is already sitting. Two tertiaries is the
cap (`22-components.md` R14.6) and this stack is exactly at it.

**Cancel is system Back.** Back while awaiting cancels the poll and returns to the idle stack; it
does **not** leave the Аккаунт tab. This is registered as an `OnBackPressedCallback` that is enabled
only in `Tg.Awaiting` (`00-rules.md` 7.7: Back always works and never traps).

### 5.4 Gate states, exhaustively

| State | `gate_error` | Action slot | `btn_gate_telegram` | Notes |
|---|---|---|---|---|
| **Default (idle)** | gone | idle stack | enabled, default | |
| **Pressed** | gone | idle stack | `scale(0.97)`, ripple `colorPrimary` @ 12% | 12.3 |
| **Focused** (keyboard / TV) | gone | idle stack | inner 2dp `colorOnPrimary` @ 40%, radius 16 | `22-components.md` R7 |
| **Loading** (`Tg.Starting`) | gone | idle stack | label hidden, width pinned, 20dp arc in `colorOnPrimary`, `isEnabled=false`, `stateDescription=@string/state_loading` | 12.4 |
| **Awaiting** | gone | awaiting stack | n/a | ring spins only while the poll is actually running |
| **Error** | visible, mapped string (section 13) | idle stack | enabled | the CTA is the retry; no separate «Повторить» |
| **Timeout** | «Мы не дождались подтверждения. Начните заново.» | idle stack | enabled | |
| **Offline** | «Нет подключения к интернету. Проверьте сеть и повторите.» | idle stack | **enabled** | see below |
| **Rate limited** | «Слишком много попыток. Повторите через минуту.» | idle stack | disabled for 60 s, then enabled; the countdown is not printed | |
| **Telegram not installed** | «Telegram не установлен. Войдите по почте.» | idle stack | enabled (a browser fallback was already tried, 8.2) | |
| **Success** | gone | awaiting stack, in the beat | n/a | 12.6a |
| **Long content** (title wraps to 2 lines at 200% scale) | - | - | - | every height is `minHeight`; the spacer absorbs it |
| **Backend not configured** | - | - | - | the Аккаунт destination does not exist in this build |

**Offline is the one place this surface deviates from `11-app-structure.md` 5.3 variant E**, which
disables network-dependent actions. On the gate every action is network-dependent, so disabling them
leaves a screen the user cannot act on at all. Instead the CTA stays live, the attempt fails inside
the OkHttp connect timeout, and the error line explains it with a fix. Recorded as decision D-14.2.

### 5.5 The error line, precisely

- Position: **above** the action slot, inside `gate_actions`, so a growing error eats the spacer and
  the buttons do not move.
- Type: `TextAppearance.App.Body` 14sp/400 in `@color/ping_bad` `#FF6069` (6.15:1 on P0). This is a
  screen-level error, not a field error; field errors are Caption 12 (`22-components.md` 4.1). The
  distinction is fixed product-wide by decision D-14.3.
- Alignment: **start**, at the gutter, like every other line on the screen. Not centred. Today's
  `android:gravity="center"` (`activity_login.xml:306`) is deleted.
- Copy: cause + fix, one or two sentences, ending in a full stop (`00-rules.md` 9.2: full stops
  exist in error messages), no HTTP codes, no raw response bodies. The debug `AlertDialog` at
  `LoginActivity.kt:335-341` is deleted, not made release-safe.
- Announcement: `accessibilityLiveRegion="polite"`.

### 5.6 Re-send and retry cooldowns

| Action | Cooldown | While cooling |
|---|---|---|
| «Войти через Telegram» after a failure | none | - |
| Telegram poll restart | none | a fresh token is minted; the old one is abandoned |
| «Отправить снова» on B (magic / verify / reset) | **30 s** | the tertiary is disabled at 0.38 and its label stays «Отправить снова». No countdown text: a ticking number is a second live number on a screen that has none |
| Any submit | `input_debounce` 500 ms | the control is disabled |

---

## 6. Surface B - «Вход по почте»

### 6.1 Frame

`ui/AuthEmailActivity.kt` + `res/layout/activity_auth_email.xml`. An Activity, not a fragment,
because it is also a deep-link target (`depv://account/signin`) and because `BaseActivity` already
provides the seamless toolbar contract.

```
LinearLayout  orientation=vertical  background="?attr/colorBackground"   #0A0B0D
│
├ MaterialToolbar  id=toolbar                                  (00-rules 4.8)
│    layout_height="@dimen/toolbar_height"    56
│    background="?attr/colorBackground"       ← same as the page. No bar colour.
│    app:elevation="0dp"  app:contentInsetStartWithNavigation="0dp"
│    app:navigationIcon="@drawable/ic_arrow_back"   24dp, ?attr/colorOnSurface   (NEW, 17.6)
│    app:navigationContentDescription="@string/common_back"      «Назад»   (NEW string)
│    app:titleTextAppearance="@style/TextAppearance.App.Title"   16sp / 700
│    (title set in code: «Вход по почте» | «Регистрация», section 6.3)
│
└ NestedScrollView  id=auth_scroll
     fillViewport=true  scrollbars=none
     paddingStart/End="@dimen/screen_gutter"   16
     paddingBottom = navigationBars + ime insets, applied in code
     │
     └ LinearLayout  id=auth_column  orientation=vertical  maxWidth="@dimen/auth_column_max"   440
          layout_gravity=center_horizontal
          (the tree of 6.2)
```

**No toolbar hairline at rest.** If the column scrolls, `#toolbar_hairline` (1dp
`?attr/colorOutlineVariant`, full bleed) fades in over `motion_state` 220 ms once `scrollY > 0` and
back out at 0. That is the only permitted scroll-linked change (`00-rules.md` 4.8).

**Top-anchored, not bottom-anchored.** Unlike the gate, this surface is a form: it is read top to
bottom, the keyboard governs the lower half, and the CTA belongs a fixed 24dp under the last field
so that the eye never has to travel across a void to find it. The two surfaces have different
anchors because they have different jobs, and they share the gutter, the components and the type
ramp, which is what makes them the same product.

### 6.2 Component tree - sign-in mode, password method (the default)

Vertical rhythm is called out between every pair. Values in dp.

```
[toolbar 56]
  24                                                     ← space_24, above the first control
┌ MaterialButtonToggleGroup  id=seg_method               ← 22-components §6
│    android:background="@drawable/bg_segment_track"     radius_button 16, colorSurfaceContainerHighest #20242B
│    android:padding="@dimen/space_4"                    4
│    android:minHeight="@dimen/btn_height"               48   (track)
│    app:singleSelection="true"  app:selectionRequired="true"
│    app:innerCornerSize="@dimen/radius_chip"            12
│    ├ MaterialButton id=seg_password  style=@style/Widget.Departament.Segment
│    │    layout_width=0dp  layout_weight=1  minHeight=40
│    │    text=@string/auth_method_password   «Пароль»
│    └ MaterialButton id=seg_link      style=@style/Widget.Departament.Segment
│         layout_width=0dp  layout_weight=1  minHeight=40
│         text=@string/auth_method_link       «Ссылка на почту»
└
  24
┌ TextView  id=lbl_email      Subtitle 13/400  colorOnSurfaceVariant
│    text=@string/auth_email_label            «Электронная почта»
└
  8
┌ TextInputLayout  id=til_email   style=@style/Widget.Departament.TextField
│    app:hintEnabled="false"   ← the label is the TextView above, never a floating hint
│    app:boxCornerRadiusTopStart/…="@dimen/radius_chip"   12
│    app:boxBackgroundColor="?attr/colorSurfaceContainerHighest"   #20242B
│    app:boxStrokeColor="@color/field_stroke"       1dp colorOutline / 2dp colorPrimary focused
│    app:boxStrokeWidth="1dp"  app:boxStrokeWidthFocused="2dp"
│    app:errorEnabled="false"  ← the error line below is a real TextView (fixed height, no jump)
│    └ TextInputEditText  id=et_email
│         android:minHeight="@dimen/field_min_height"    56
│         android:textAppearance="@style/TextAppearance.App.Body"  14sp… see 6.6
│         android:inputType="textEmailAddress"
│         android:imeOptions="actionNext"
│         android:autofillHints="emailAddress"
│         android:maxLines="1"  android:ellipsize="end"
│         android:hint="@string/auth_email_placeholder"   «name@example.com»
│         android:textColorHint="?attr/colorOnSurfaceVariant"    7.0:1
└
  4
┌ TextView  id=err_email   Caption 12/400  @color/ping_bad
│    android:minHeight="16dp"   ← the line is ALWAYS in the layout; only its text changes.
│    visibility=INVISIBLE when empty (never GONE - GONE makes the form jump)
└
  16
┌ TextView  id=lbl_password   Subtitle 13/400   «Пароль»                    ⟵ method slot starts
└
  8
┌ TextInputLayout  id=til_password  (same style as til_email)
│    app:endIconMode="custom"  app:endIconDrawable="@drawable/ic_eye"     ← ic_lp_eye renamed, 17.6
│    app:endIconContentDescription="@string/auth_show_password"
│    app:endIconTint="?attr/colorOnSurfaceVariant"
│    (the end icon's touch box is 48×48; the glyph is 22dp)
│    └ TextInputEditText  id=et_password
│         minHeight=56  inputType="textPassword"  imeOptions="actionDone"
│         autofillHints="password"  maxLines=1
└
  4
┌ TextView  id=err_password   Caption 12/400   (reserved line, as err_email)
└                                                                            ⟵ method slot ends
  24
┌ TextView  id=auth_error   Body 14/400  @color/ping_bad
│    visibility=gone   accessibilityLiveRegion=polite
│    (when visible it adds its own height + 16 below itself)
└
  0 | 16
┌ MaterialButton  id=btn_submit
│    style=@style/Widget.Departament.Button.Primary.Tall
│    layout_width=match_parent  minHeight=52  cornerRadius=16  insets 0
│    text=@string/auth_btn_signin             «Войти»
└
  12
┌ MaterialButton  id=btn_forgot
│    style=@style/Widget.Departament.Button.Tertiary
│    layout_width=match_parent  minHeight=48
│    text=@string/auth_forgot                 «Забыли пароль?»
└
  12
┌ MaterialButton  id=btn_switch_mode
│    style=@style/Widget.Departament.Button.Tertiary
│    layout_width=match_parent  minHeight=48
│    text=@string/auth_create_account         «Создать аккаунт»
└
  24
┌ View  id=auth_hairline   height=1dp   background="?attr/colorOutlineVariant"   #20242B
└
  8
┌ LinearLayout  id=row_other_methods       ← Row.Navigation, 22-components §8.2
│    minHeight="@dimen/row_min_height"  56   gravity=center_vertical
│    background="?attr/selectableItemBackground"   clickable  focusable
│    ├ FrameLayout 40×40  background="@drawable/bg_icon_neutral"  radius_tile 12
│    │     fill @color/icon_tile_neutral #20242B
│    │     └ ImageView 22dp  @drawable/ic_more_horiz  tint @color/icon_glyph_neutral #9BA1AD
│    │        (NEW glyph, 17.6. NOT ic_more_vert_24dp: a vertical kebab means
│    │         "overflow menu", and this row opens a named list of methods)
│    ├ TextView  layout_marginStart=12  layout_weight=1   Title 16/700
│    │     text=@string/auth_other_method    «Другой способ входа»
│    └ ImageView 22dp  @drawable/ic_chevron_right  tint ?attr/colorOnSurfaceVariant
└
  32   (bottom padding, before the navigation-bar inset)
```

Total intrinsic height at default font scale: 56 + 24 + 48 + 24 + 18 + 8 + 56 + 4 + 16 + 16 + 18 + 8
+ 56 + 4 + 16 + 24 + 52 + 12 + 48 + 12 + 48 + 24 + 1 + 8 + 56 + 32 = **689dp**. It scrolls on a
640dp viewport and does not on a 720dp one. Both are correct; neither clips.

**Why three stacked buttons is not the defect this document is fixing.** The rejected pattern was
three buttons of *competing intent* - three ways to sign in, all full-width, two of them filled. Here
there is one action («Войти», the only filled surface on the screen) and two adjuncts that are
transparent, unweighted and semantically subordinate («забыли пароль» and «у меня нет аккаунта» are
both statements about the form above them). `22-components.md` R14.6 caps tertiary buttons at two;
this is exactly two.

### 6.3 The two modes

Mode is **not** a back-stack entry. System Back always pops the whole sub-page, from either mode, and
predictive Back shows the Аккаунт tab underneath before the gesture commits - so the destination is
visible before it happens and there is no surprise. The mode toggle is always on screen and always
reversible.

| | Sign-in mode | Register mode |
|---|---|---|
| Toolbar title | «Вход по почте» | «Регистрация» |
| `seg_method` | visible | **gone** (registration has one method) |
| `lbl_password` | «Пароль» | «Пароль» |
| Password helper (`err_password` slot, neutral colour) | empty | «Не менее 8 символов», `colorOnSurfaceVariant` |
| `et_password` autofill | `password` | `newPassword` |
| `et_password` IME | `actionDone` | `actionNext` |
| Repeat field (`lbl_password2` + `til_password2` + `err_password2`) | gone | visible, 16 above, `autofillHints="newPassword"`, `imeOptions="actionDone"` |
| `btn_submit` | «Войти» | «Создать аккаунт» |
| `btn_forgot` | visible | gone |
| `btn_switch_mode` | «Создать аккаунт» | «У меня уже есть аккаунт» |
| `auth_hairline` + `row_other_methods` | visible | **gone** - the alternates are all sign-in concepts |
| Submit gate | valid email **and** password non-empty | valid email **and** password ≥ 8 **and** repeat == password |

Switching mode clears `auth_error`, keeps the email buffer, clears both password buffers, and moves
focus to `et_password`.

### 6.4 The method slot (sign-in mode only)

The two methods occupy the same slot, between `err_email` and `auth_error`.

| | «Пароль» | «Ссылка на почту» |
|---|---|---|
| Slot content | `lbl_password` + `til_password` + `err_password` | one `TextView` id=`lbl_link_hint`, Body 14/400 `colorOnSurfaceVariant`, text «Отправим ссылку для входа на этот адрес. Откройте её на этом телефоне.», maxLines 3 |
| `et_email` IME | `actionNext` | `actionDone` |
| `btn_submit` | «Войти» | «Отправить ссылку» |
| `btn_forgot` | visible | **gone** (a password you never type cannot be forgotten here) |
| `btn_switch_mode` | «Создать аккаунт» | «Создать аккаунт» |
| Submit gate | email valid + password non-empty | email valid |

**The method swap is instant, not animated.** The two slots have different heights, and animating a
height is banned outright (`00-rules.md` 8.7). The acknowledgement is carried by the segment's own
220 ms fill crossfade, which is already inside the 80 ms perceived-instant budget for the tap
itself. This is a deliberate absence of motion, not an omission.

### 6.5 Surface B states, exhaustively

| State | What changes |
|---|---|
| **Default** | 6.2 as drawn, `btn_submit` disabled at 0.38 until the gate passes |
| **Focused field** | box stroke 1dp `colorOutline` → 2dp `colorPrimary` over `motion_state` 220 ms; `lbl_*` colour → `colorPrimary`; no ring, no glow, no shadow |
| **Filled** | identical to default; a filled field is not a state that needs a look |
| **Field error** | box stroke 2dp `?attr/colorError` `#F04452`; `err_*` carries the message in `@color/ping_bad`; label → `@color/ping_bad`. Validation runs on **blur**, never per keystroke (`00-rules.md` 7.4). The single exception is the register password-length helper, which updates live |
| **Submitting** | `btn_submit` loading (12.4): label hidden, width pinned, 20dp arc in `colorOnPrimary`; both tertiaries disabled at 0.38; all fields `isEnabled=false`; the segment disabled |
| **Screen error** | `auth_error` reveals (12.5); on `Unauthorized` both credential fields flash their border to `colorError` for 220 ms and return - **colour only, no shake** |
| **2FA** | 6.7 |
| **Sent** (magic / verify / reset) | 6.8 |
| **Manual code entry** | 9.4 |
| **Offline** | `auth_error` shows «Нет подключения к интернету. Проверьте сеть и повторите.»; controls stay enabled (same argument as 5.4) |
| **Rate limited** | `auth_error` shows «Слишком много попыток. Повторите через минуту.»; `btn_submit` disabled 60 s |
| **Google row present / absent** | 9.5 |
| **Long content** | a 60-character email ellipsises at the end while unfocused and scrolls horizontally while focused; `auth_error` wraps to 3 lines |
| **Short content** | n/a - the form has a fixed field set |
| **Success** | 12.6b, then the surface finishes and D takes over |
| **Font scale 200 %** | every control is `minHeight` + `wrap_content`; the column scrolls; nothing is clipped, nothing overlaps, the CTA is still reachable |

### 6.6 One typographic exception, stated so it is not treated as a bug

Input text inside a field is **16sp**, not the Body ramp's 14sp. `22-components.md` 4.2 sets it, and
the reason is mechanical: below 16sp Android's browsers and some OEM keyboards zoom the field, and a
14sp password field with a 22dp reveal glyph reads as cramped. The **label** (13sp Subtitle), the
**helper** (12sp Caption) and everything else on the screen are on the ramp. No new ramp step is
introduced: 16sp is the Title size, used here at weight 400.

### 6.7 The 2FA step

Entered from `Mail.Submitting` when the backend returns `Requires2FA(tempToken)`. It **replaces the
method slot** - the password field, its label and its error line go; the segment and both tertiaries
go. What remains: the email field (read-only, so the user can see whose code they are entering), the
OTP group, one CTA and one «Отмена».

```
[toolbar 56]  title unchanged «Вход по почте»
  24
┌ TextView  id=lbl_email  …  «Электронная почта»                       (unchanged)
  8
┌ TextInputLayout id=til_email
│    et_email: android:enabled="false"  → 0.38 on text and stroke      (read-only)
  4
┌ err_email  (reserved, empty)
  24
┌ TextView  id=lbl_otp   Subtitle 13/400 colorOnSurfaceVariant
│    text=@string/auth_2fa_label        «Код из приложения-аутентификатора»
  8
┌ com.v2ray.ang.ui.widget.OtpCodeView  id=otp                          (section 7)
  4
┌ TextView  id=err_otp   Caption 12/400 @color/ping_bad, reserved
  24
┌ auth_error   (screen-level, as 6.2)
  0 | 16
┌ MaterialButton id=btn_submit   Primary.Tall 52
│    text=@string/auth_btn_2fa         «Подтвердить»
  12
┌ MaterialButton id=btn_cancel_2fa   Tertiary 48
│    text=@string/common_cancel         «Отмена»
  32
```

- `btn_submit` is disabled until six digits are present.
- On `Unauthorized`: the six cells' borders go to `?attr/colorError` for 220 ms, the code buffer is
  **cleared**, focus stays in the field, the IME stays up, and `err_otp` reads «Неверный код.
  Проверьте приложение-аутентификатор.»
- «Отмена» and system Back both return to `Mail.Form(SignIn, Password)` and drop the `tempToken`.
- The `tempToken` is held in `SavedStateHandle` so a rotation does not lose the step.

### 6.8 The «отправлено» block

One block, three kinds, entered from `Mail.Sent(kind, address)`. It replaces the **entire** column
below the toolbar (segment, fields, CTA, tertiaries, hairline and row all go). Toolbar title becomes
the block title so the two never disagree.

```
[toolbar 56]  title = the block title
  32
┌ LinearLayout  orientation=horizontal  minHeight=56  gravity=center_vertical
│    ├ FrameLayout 40×40  @drawable/bg_icon_neutral  radius_tile 12  #20242B
│    │     └ ImageView 22dp  @drawable/ic_mail  tint @color/icon_glyph_neutral #9BA1AD  (NEW, 17.6)
│    └ LinearLayout vertical weight=1  marginStart=12
│         ├ TextView  Title 16/700  = title (table below)
│         └ TextView  Subtitle 13/400 colorOnSurfaceVariant  marginTop=4
│              = body, with the address substituted, maxLines=4
└
  24
┌ MaterialButton  id=btn_resend    Tertiary 48 full width  «Отправить снова»
  12
┌ MaterialButton  id=btn_back_signin  Tertiary 48 full width  «Вернуться ко входу»
  32
```

| kind | Toolbar + title | Body |
|---|---|---|
| `Magic` | «Ссылка отправлена» | «Мы отправили ссылку на %1$s. Откройте её на этом телефоне.» |
| `Verify` | «Подтвердите почту» | «Мы отправили ссылку на %1$s. Откройте её, чтобы завершить регистрацию.» |
| `Reset` | «Письмо отправлено» | «Если аккаунт с %1$s существует, мы отправили ссылку для сброса пароля. Задайте новый пароль и вернитесь ко входу.» |

**The tile is static and neutral, and it does not spin.** Nothing is happening: the app is not
polling, it is waiting for the user to open a link that will come back through `depv://auth/{code}`
(section 9). An indeterminate indicator running while no work is in flight is a lie about the system
(`03-direction.md` 8.4). This is the one visible divergence from the desktop, which polls
verify-email and therefore correctly spins; it is logged as PG-A5.

---

## 7. The OTP component

`com.v2ray.ang.ui.widget.OtpCodeView`, `res/layout/layout_auth_otp.xml`. Ported from the desktop's
six-cell treatment (`LoginView.axaml:661-689`), which is the right idea and the right mechanics: the
cells are **decoration**, a single real input is the source of truth.

```
FrameLayout  layout_width=match_parent  minHeight=56
│
├ LinearLayout  id=otp_cells  orientation=horizontal  importantForAccessibility=no
│    duplicateParentState=false
│    × 6:
│      FrameLayout  layout_width=0dp  layout_weight=1  minHeight=56
│                   layout_marginStart=4  layout_marginEnd=4      (outer margins 0)
│                   background=@drawable/bg_otp_cell            ← state list, below
│        └ TextView  layout_gravity=center
│             textAppearance=@style/TextAppearance.App.Numeric   Space Grotesk, tnum+lnum+zero
│             textSize inherited (20sp declared in the Numeric style override for this widget)
│             textColor="?attr/colorOnSurface"
│
└ EditText  id=otp_input      ← the real field, invisible, on top, full bleed
     layout_width=match_parent  layout_height=match_parent
     background=@null  textColor=@android:color/transparent
     textCursorDrawable=@null  textSelectHandle… = transparent
     inputType="number"   maxLength=6   imeOptions="actionDone"
     autofillHints="smsOTPCode"
     importantForAutofill="yes"
     contentDescription=@string/auth_2fa_a11y   «Код из приложения, 6 цифр»
```

`res/drawable/bg_otp_cell.xml` - a `selector` over three states, radius `@dimen/radius_chip` 12:

| Cell state | Fill | Stroke |
|---|---|---|
| Empty | `?attr/colorSurfaceContainerHighest` `#20242B` | 1dp `?attr/colorOutlineVariant` `#20242B` |
| Filled | `?attr/colorSurfaceContainerHighest` | 1dp `?attr/colorOutline` `#2A2E36` |
| Next-to-fill (only while the field has focus) | `?attr/colorSurfaceContainerHighest` | 2dp `?attr/colorPrimary` `#4C8DFF` |
| Error (whole group, 220 ms) | `?attr/colorSurfaceContainerHighest` | 2dp `?attr/colorError` `#F04452` |

Stroke colour transitions over `motion_state` 220 ms `ease_standard`. Nothing moves, nothing scales.

**Sizing.** Cell width is `0dp` + `layout_weight=1`, so on a 320dp screen each cell is
(320 − 32 gutter − 40 gaps) / 6 = **41.3dp** wide and 56 tall. The cells are **not touch targets** -
`otp_input` spans the full width and is the single 56dp-tall target, which clears the 48dp floor. At
font scale 200 % the 20sp digit becomes 40sp (~26dp advance at the 620/1000 tabular width) and still
fits 41dp; the cell grows in height, not width.

**Behaviour.**
- Paste of a 6-digit string fills all six cells and auto-submits after 120 ms.
- Non-digits are dropped on input, silently.
- The sixth digit auto-submits (no need to press «Подтвердить»), guarded by `input_debounce`.
- `autofillHints="smsOTPCode"` is used because Android has no TOTP hint; the major password managers
  map it to their TOTP fill. Documented so nobody "fixes" it to a non-existent constant.
- TalkBack sees exactly one node: the `EditText`, named «Код из приложения, 6 цифр». The cells are
  `importantForAccessibility="no"`.

---

## 8. The Telegram path, in full

### 8.1 The happy path

1. Tap `btn_gate_telegram` → `pressHaptic()` → `Tg.Starting` → CTA loading.
2. `POST /client/auth/telegram-login-token` → `token`.
3. Deep link = `https://t.me/{BackendConfig.botUsername}?start=auth_{token}` (unchanged from
   `AuthManager.kt:66`).
4. `Tg.Awaiting` → the action stack crossfades (12.1) **and** `startActivity(ACTION_VIEW, link)`
   fires in the same frame.
5. Poll `POST /client/auth/telegram-login-check` every **2000 ms**, budget **180 000 ms**
   (unchanged).
6. `Confirmed` → `AccountSession.onAuthenticated` → `Success` → ring beat (12.6a) → D.

### 8.2 Telegram is not installed

Today: `ActivityNotFoundException` → `toastError` (`LoginActivity.kt:371`), which
`00-rules.md` 1.4.8 bans for anything the user can act on. Replacement, in order:

1. Try `Intent(ACTION_VIEW, t.me/...)` with the Telegram package preferred.
2. On `ActivityNotFoundException`, open the **same `t.me` URL in a Custom Tab**. Telegram Web
   completes the same confirmation, so this is a real fallback and not a consolation prize. The poll
   keeps running throughout.
3. If no browser exists either, cancel the poll, return to the idle stack, and show the error line:
   «Telegram не установлен. Войдите по почте.» The fix named in the copy is the button directly
   below it.

### 8.3 Leaving and returning

- The app is backgrounded while the user is in Telegram. The poll runs in `viewModelScope`, which
  outlives the Activity but not the ViewModel; it survives the trip.
- On `onResume` the UI re-renders from `AuthUiState`; it does **not** re-fire the deep link
  (invariant 4.3.2).
- If the process is killed while awaiting, the state is lost and the gate returns to idle. That is
  correct: the login token may have expired and a stale poll would be a lie. No attempt is made to
  resurrect the poll from disk.

### 8.4 Linking Telegram to an existing account (surface E)

This is the same flow with a different terminal state and it replaces `LoginActivity`'s
`EXTRA_LINK` mode.

- Entry: Аккаунт (signed in) → group «Вход» → row «Telegram» → trailing action «Привязать».
- Presentation: `ui/LinkTelegramSheet.kt`, a `BottomSheetDialogFragment`, `radius_sheet` 24 top,
  36×4 handle, `?attr/colorSurface`, 60 % scrim. Not a full screen: this is a per-item action and
  `00-rules.md` 7.6 puts it in a sheet.
- Content: the title «Привязать Telegram» (Title 16/700), then **the awaiting row of 5.3, verbatim**,
  then «Открыть Telegram» (Tertiary) and «Отмена» (Tertiary).
- The deep link is created with the current JWT attached, so the backend links rather than logs in.
  This is existing behaviour and the comment at `LoginActivity.kt:60-62` explains it; preserve it.
- Terminal state: **no overlay, no hand-off.** The ring beat plays inside the sheet, the sheet
  dismisses over 225 ms, and a `Snackbar` says «Telegram привязан». The Аккаунт row updates to
  «Привязан @handle».

---

## 9. Surface C, the website hand-off, and Google

### 9.0 The sheet

`res/layout/sheet_auth_methods.xml`, hosted by `ui/AuthMethodsSheet.kt`
(`BottomSheetDialogFragment`, `@style/Widget.Departament.Sheet`). Opened only from
`row_other_methods` on B. It is a **list of methods**, so it is built from the universal row and
nothing else - no cards, no icons-in-circles, no descriptions longer than one line.

```
BottomSheetDialog   background=@drawable/bg_sheet_top
                    radius_sheet 24 top corners, ?attr/colorSurface #141619
                    scrim ?attr/colorScrim @ 60%
│
├ View  36×4   @drawable/bg_sheet_handle   ?attr/colorSurfaceContainerHighest, radius_pill
│    layout_gravity=center_horizontal   layout_marginTop="@dimen/space_12"    12
│
├ TextView  Title 16/700  ?attr/colorOnSurface   accessibilityHeading=true
│    text=@string/auth_methods_title        «Другой способ входа»
│    paddingHorizontal="@dimen/screen_gutter" 16
│    layout_marginTop="@dimen/space_12"      12
│
├ [ space_16 ]  16
│
├ row  id=row_site      tile ic_globe_24dp    «Через сайт»       «Откроем departament.site в браузере»
├ hairline 1dp ?attr/colorOutlineVariant, marginStart=68dp, marginEnd=16dp
├ row  id=row_code      tile ic_key           «У меня есть код»  «Вставьте код, который показал сайт»
├ hairline (same inset)                                          ← omitted when the Google row is absent
└ row  id=row_google    tile ic_google        «Через Google»     «Войдите аккаунтом Google»
                                                                  visibility gone unless 9.5's test passes
[ padding bottom = space_16 + navigationBars inset ]
```

Each row, identically (`22-components.md` 8.2, Row.Navigation):

```
[16 gutter][ 40dp tile, radius_tile 12, @drawable/bg_icon_neutral,
             22dp glyph tinted @color/icon_glyph_neutral ][12][ text column, weight 1 ][12][ 22dp chevron ][16]
                                                                 Title    16/700 onSurface, maxLines 1
                                                                 Subtitle 13/400 onSurfaceVariant, maxLines 2
minHeight=@dimen/row_min_height 56   background=?attr/selectableItemBackground   the whole row is the target
press = background step to ?attr/colorSurfaceContainerHigh over 90ms, released over 160ms. No scale (R5)
```

Text origin **68dp**, hairlines start at 68dp and never run under a tile - the same ledger geometry
as every list in the product.

**Every tile is neutral.** Three coloured tiles here would turn a three-item list into a paint chart
and would spend an accent budget that this sheet does not have (`03-direction.md` 3.2, 5.7). The
sheet contains **zero** accent pixels; it is a list of navigations, not a set of calls to action.

Esc / system Back / swipe-down dismiss it, focus returns to `row_other_methods`, and the row keeps
its scroll position (`00-rules.md` 7.6).

### 9.1 Why a browser hand-off exists at all

The site (`departament.site`) can already sign a user in with methods the app does not implement, and
it holds the session that the user made three weeks ago. Rather than re-implementing every method in
the client, the app borrows the site's session once, through a one-time code. The desktop already
does this (`Account/BackendConfig.cs:50-51`, `AppHandoff` / `AppHandoffConsume`); Android gains it.

### 9.2 The flow

1. C → «Через сайт» → `Handoff.Browser`.
2. `CustomTabsIntent` opens `https://departament.site/app-login?platform=android`, with
   `setColorScheme(COLOR_SCHEME_DARK)` and the toolbar colour set to `?attr/colorBackground` so the
   browser chrome does not flash white.
3. The site authenticates (or reuses its cookie), calls `POST /client/auth/app-handoff`, and
   redirects to **`depv://auth/{code}`**.
4. `UrlSchemeActivity` receives it, hands the code to `AuthViewModel`, `Handoff.Redeeming`,
   `POST /client/auth/app-handoff/consume` → session → `Success`.

`depv://auth/{code}` is a **new route** and is added to the table in `11-app-structure.md` 7.2
(change-control row D-14.5). It is the only `depv://` route that carries a secret, so:

- The code is single-use and short-lived (backend contract).
- It is **never logged**, never put in a `Toast`, and never shown in the UI.
- `UrlSchemeActivity` consumes it and immediately clears the Intent data so a
  configuration change cannot replay it.

### 9.3 Arriving cold

If `depv://auth/{code}` arrives when the app is not running or is not on an auth surface,
`UrlSchemeActivity` launches `MainActivity` with the Аккаунт tab selected and the code in the Intent,
and **surface D is shown immediately** with the stage line «Проверяем аккаунт». The user never sees
the gate flash first. This is the same behaviour the overlay has after any other successful method.

### 9.4 «У меня есть код» - the manual fallback

Some Android setups do not route custom schemes back from a Custom Tab (aggressive OEM link
handlers, an external browser without scheme support). The site shows the code as text; the user
pastes it.

Selecting the row in C dismisses the sheet and reveals, on B, immediately **below `err_email`**:

```
  16
┌ TextView  id=lbl_handoff   Subtitle 13/400   «Код из браузера»
  8
┌ TextInputLayout id=til_handoff  (same field style)
│    └ TextInputEditText id=et_handoff
│         minHeight=56  inputType="textVisiblePassword"   ← no autocorrect, no suggestions
│         imeOptions="actionDone"   maxLines=1
│         hint="@string/auth_code_paste"    «Вставьте код, который показал сайт»
  4
┌ err_handoff   (reserved Caption line)
```

and `btn_submit` becomes «Войти по коду». The method segment and the password slot are hidden while
this field is up. Back or a second tap on the row dismisses it. The field is **not** a permanent part
of the form - it is revealed by an explicit request, which is what keeps the default form at two
fields.

### 9.5 Google

**The row exists only when Google is actually available**, evaluated once at sheet-construction time:

```
BackendConfig.googleClientId.isNotBlank()
  && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == SUCCESS
```

When false, the row is **absent** - not disabled, not labelled «Скоро». A permanently disabled
control advertises a feature instead of offering one, and `03-direction.md` F15 refuses dead
surfaces. (The desktop ships the disabled «Скоро» variant today; PG-A4.)

When true:

1. `Google.Picking` → `CredentialManager.getCredential` with `GetGoogleIdOption`
   (`setServerClientId(BackendConfig.googleClientId)`, `setFilterByAuthorizedAccounts(false)`).
2. `Google.Submitting` → `api.loginGoogle(idToken, referralCode)` - the endpoint already exists
   (`DepartamentApiClient.kt:39`).
3. User-cancelled → back to `Mail.Form` with **no error line**. A dismissed picker is not a failure.
4. `NoCredentialException` → error line «Не удалось войти через Google. Войдите по почте.»

The glyph is a **single-colour** Google mark tinted `@color/icon_glyph_neutral`. The multicoloured
mark is a second, third and fourth hue on a screen whose accent budget is one
(`00-rules.md` 1.4.1), and it is illegible in the mono theme.

---

## 10. Surface D - the hand-off into the app

### 10.1 Why a gate is correct here and nowhere else

`11-app-structure.md` 5.6: a real import is running (profile → subscriptions → servers), and an empty
Главная flashing for two seconds is a worse lie than a covered screen. This is the **only** gate in
the product and the **only** use of `Dur.Slow` 450.

### 10.2 The layout

`res/layout/layout_sync_overlay.xml`, added as the last child of the root of
`res/layout/activity_main.xml`, `visibility="gone"`.

```
FrameLayout  id=sync_overlay
  layout_width/height=match_parent
  background="?attr/colorBackground"      #0A0B0D, fully opaque - not a scrim
  clickable=true  focusable=true          ← swallows every touch beneath it
  importantForAccessibility=yes
  android:accessibilityLiveRegion="polite"
│
└ LinearLayout  orientation=vertical  layout_gravity=center  gravity=center
     paddingStart/End="@dimen/screen_gutter"  16   maxWidth="@dimen/auth_column_max"   440
     │
     ├ FrameLayout  id=sync_ring   64×64        (@dimen/sync_ring)
     │    ├ ProgressBar  id=sync_arc   64dp  style=@style/Widget.Departament.Progress.Ring
     │    │     trackThickness=2dp  trackColor="?attr/colorOutlineVariant"
     │    │     indicatorColor="?attr/colorPrimary"  indeterminate=true
     │    └ ImageView  id=sync_check  28dp  layout_gravity=center
     │          @drawable/ic_check  tint="?attr/colorPrimary"  alpha=0
     │          (ic_check = the existing ic_fab_check, renamed. It is already the
     │           product's only check glyph; do not draw a second one)
     │
     ├ TextView  id=sync_title
     │    layout_marginTop="@dimen/space_24"    24
     │    textAppearance=@style/TextAppearance.App.Headline   24sp / 700
     │    textColor="?attr/colorOnSurface"   gravity=center
     │    text=@string/auth_sync_title        «Добавляем аккаунт»
     │
     ├ TextView  id=sync_stage
     │    layout_marginTop="@dimen/space_8"     8
     │    textAppearance=@style/TextAppearance.App.Body       14sp / 400
     │    textColor="?attr/colorOnSurfaceVariant"  gravity=center  minLines=1
     │    (text set live, table below)
     │
     └ LinearLayout  id=sync_error   orientation=vertical  visibility=gone
          layout_marginTop="@dimen/space_24"   24
          ├ TextView  id=sync_error_text   Body 14/400  @color/ping_bad  gravity=center
          ├ MaterialButton id=btn_sync_retry   Primary.Tall 52  marginTop=24  «Повторить»
          └ MaterialButton id=btn_sync_relogin Tertiary 48      marginTop=12  «Войти заново»
```

`minLines="1"` on `sync_stage` reserves the line so the column does not shift as the stage text
changes length.

### 10.3 Stages and timing

| Phase | `sync_stage` |
|---|---|
| profile fetch | «Проверяем аккаунт» |
| subscriptions | «Загружаем подписку» |
| servers | «Загружаем серверы» |

- **Minimum visible: 600 ms.** A 200 ms flash of a full-screen overlay reads as a rendering glitch.
- **Failure deadline: 20 s.** After that the error column appears with the mapped cause.
- `btn_sync_retry` re-runs the import without touching the session. `btn_sync_relogin` clears the
  session and returns to the gate.
- On error the arc **stops**. An indicator that keeps spinning next to an error message is the
  same lie as 6.8.

### 10.4 The exit

The overlay dismisses onto **Аккаунт**, populated - not onto Главная.

`11-app-structure.md` 5.6 step 3 says Главная. This document overrides it (conflict C6) for one
reason: the overlay's entire purpose is to cover an *account* import, so revealing anything other
than the account makes the cover pointless, and the user pressed the button on the Аккаунт tab.
Главная is one tap away and its state has already been recomputed behind the overlay. Change-control
row D-14.4.

Motion: 12.13.

---

## 11. Copy

`res/values/strings_auth.xml`, replacing the current file in full. Russian, sentence case, no
ALL-CAPS, no em-dash or en-dash, `…` as a single character, «ёлочки», no final period on labels and
buttons, full stops only in error sentences and body copy (`00-rules.md` 9.2).

### 11.1 Surface A - the gate

| Resource | String |
|---|---|
| `auth_gate_title` | `Вход в departament` |
| `auth_gate_body` | `Здесь будут подписка, устройства и платежи.` |
| `auth_btn_telegram` | `Войти через Telegram` |
| `auth_btn_email` | `Войти по почте` |
| `auth_awaiting_title` | `Ждём подтверждения в Telegram` |
| `auth_awaiting_body` | `Подтвердите вход в открывшемся приложении` |
| `auth_open_telegram` | `Открыть Telegram` |

### 11.2 Surface B - the form

| Resource | String |
|---|---|
| `auth_email_title` | `Вход по почте` |
| `auth_register_title` | `Регистрация` |
| `auth_method_password` | `Пароль` |
| `auth_method_link` | `Ссылка на почту` |
| `auth_email_label` | `Электронная почта` |
| `auth_email_placeholder` | `name@example.com` |
| `auth_password_label` | `Пароль` |
| `auth_password_hint_register` | `Не менее 8 символов` |
| `auth_password_repeat_label` | `Повторите пароль` |
| `auth_show_password` | `Показать пароль` |
| `auth_hide_password` | `Скрыть пароль` |
| `auth_link_hint` | `Отправим ссылку для входа на этот адрес. Откройте её на этом телефоне.` |
| `auth_btn_signin` | `Войти` |
| `auth_btn_send_link` | `Отправить ссылку` |
| `auth_btn_register` | `Создать аккаунт` |
| `auth_btn_by_code` | `Войти по коду` |
| `auth_forgot` | `Забыли пароль?` |
| `auth_create_account` | `Создать аккаунт` |
| `auth_have_account` | `У меня уже есть аккаунт` |
| `auth_other_method` | `Другой способ входа` |

### 11.3 2FA

| Resource | String |
|---|---|
| `auth_2fa_label` | `Код из приложения-аутентификатора` |
| `auth_2fa_a11y` | `Код из приложения, 6 цифр` |
| `auth_btn_2fa` | `Подтвердить` |
| `auth_2fa_invalid` | `Код состоит из 6 цифр` |
| `auth_2fa_wrong` | `Неверный код. Проверьте приложение-аутентификатор.` |

### 11.4 The «отправлено» block

| Resource | String |
|---|---|
| `auth_sent_magic_title` | `Ссылка отправлена` |
| `auth_sent_magic_body` | `Мы отправили ссылку на %1$s. Откройте её на этом телефоне.` |
| `auth_sent_verify_title` | `Подтвердите почту` |
| `auth_sent_verify_body` | `Мы отправили ссылку на %1$s. Откройте её, чтобы завершить регистрацию.` |
| `auth_sent_reset_title` | `Письмо отправлено` |
| `auth_sent_reset_body` | `Если аккаунт с %1$s существует, мы отправили ссылку для сброса пароля. Задайте новый пароль и вернитесь ко входу.` |
| `auth_resend` | `Отправить снова` |
| `auth_back_to_signin` | `Вернуться ко входу` |

### 11.5 Surface C - the sheet

| Resource | String |
|---|---|
| `auth_methods_title` | `Другой способ входа` |
| `auth_method_site` | `Через сайт` |
| `auth_method_site_sub` | `Откроем departament.site в браузере` |
| `auth_method_code` | `У меня есть код` |
| `auth_method_code_sub` | `Вставьте код, который показал сайт` |
| `auth_method_google` | `Через Google` |
| `auth_method_google_sub` | `Войдите аккаунтом Google` |
| `auth_handoff_label` | `Код из браузера` |
| `auth_code_paste` | `Вставьте код, который показал сайт` |

### 11.6 Surface D and E

| Resource | String |
|---|---|
| `auth_sync_title` | `Добавляем аккаунт` |
| `auth_sync_stage_account` | `Проверяем аккаунт` |
| `auth_sync_stage_subscription` | `Загружаем подписку` |
| `auth_sync_stage_servers` | `Загружаем серверы` |
| `auth_sync_retry` | `Повторить` |
| `auth_sync_relogin` | `Войти заново` |
| `auth_link_tg_title` | `Привязать Telegram` |
| `auth_link_tg_done` | `Telegram привязан` |

### 11.7 Deleted strings, and why

| Deleted | Reason |
|---|---|
| `auth_tg_headline` «Вход через Telegram» | the card it headed is gone; the button says it |
| `auth_tg_desc` «Подключите аккаунт Telegram, чтобы войти в один тап.» | explains a button that explains itself (`distill.md`) |
| `auth_site_headline` «Вход через сайт» | ambiguous: it meant email+password, while «через сайт» now means the browser hand-off. `00-rules.md` 9.3 forbids one noun for two concepts |
| `auth_site_desc` | as above |
| `auth_btn_site` «Войти через сайт» | mislabel; the button posts email+password, so it says «Войти» |
| `auth_register_site` «Регистрация на сайте» | registration happens in the app now |
| `auth_awaiting` «Ожидаем подтверждения в Telegram…» | replaced by the shorter active form; the trailing `…` was also a three-dot sequence in `values-ru` |
| `auth_fields_required` «Заполните все поля» | the CTA is disabled until the fields are valid, so this can never fire (`22-components.md` R9) |
| `auth_err_dialog_title` «Ошибка входа» | the debug diagnostic dialog is deleted |
| `auth_title` «Вход» | the gate has no toolbar of its own |

### 11.8 Strings the desktop must adopt or change

Parity is a contract (`00-rules.md` 13): the same concept carries the same Russian string on both
platforms. Section 18 lists the desktop-side edits.

---

## 12. Motion

Every animation in this specification, with its token, its curve and its reduced-motion fallback.
There are no others. Reduced motion is checked with `MotionUtils.animationsEnabled(context)` at
**play time**, never cached in a constructor (`00-rules.md` 8.8).

| # | Transition | Property | In | Out | Curve | Reduced motion |
|---|---|---|---|---|---|---|
| 12.1 | Gate idle ⇄ awaiting | crossfade both stacks, incoming `translationY` 8→0 | `motion_state` 220 | 165 | `ease_standard` | snap to end state |
| 12.2 | Sub-page enter / exit | `translationY` 24→0 + alpha 0→1 | `motion_reveal` 300 | 225 | `ease_out_quint` in, `ease_standard` out | no transition (`overridePendingTransition(0,0)`) |
| 12.3 | Any button press | `scale` 1→0.97 | `motion_press_in` 90 | `motion_press_out` 160 | `ease_out_quart` in, `ease_out_quint` out | collapses automatically at animator scale 0 |
| 12.4 | Button → loading | label alpha 1→0, arc alpha 0→1, width pinned | 90 | 90 | `ease_standard` | instant swap; the arc still rotates (it is a genuine progress indicator, not decoration) |
| 12.5 | Error line show / hide | alpha 0→1 + `translationY` −4→0 | 220 | 165 | `ease_standard` | instant |
| 12.6a | Success beat, Telegram ring | arc alpha 1→0 and `gate_ring_full` alpha 0→1 (220), then at +160 ms the check alpha 0→1 + `scale` 0.9→1 (160), then hold 120 | ≈400 total | - | `ease_standard` then `ease_out_quint` | check visible instantly, hold 120 |
| 12.6b | Success beat, CTA | button arc alpha 1→0 (90), 20dp check alpha 0→1 + `scale` 0.9→1 (160), hold 120 | ≈370 total | - | `ease_out_quint` | check visible instantly, hold 120 |
| 12.7 | Segment selection | fill crossfade `transparent` → `colorPrimaryContainer` | 220 | 220 | `ease_standard` | instant; the label weight always snaps |
| 12.8 | Field focus | box stroke colour + width | 220 | 220 | `ease_standard` | instant |
| 12.9 | Method swap in the slot | **none** | - | - | - | - |
| 12.10 | 2FA reveal | incoming group alpha 0→1 + `translationY` 8→0 | `motion_reveal` 300 | 225 | `ease_out_quint` | instant |
| 12.11 | Credential flash on `Unauthorized` | both field strokes → `colorError`, then back | 220 | 220 | `ease_standard` | instant to error colour, instant back after 220 ms |
| 12.12 | Overlay D in | alpha 0→1 | 220 | - | `ease_standard` | instant |
| 12.13 | Overlay D out → Аккаунт | overlay alpha 1→0; Аккаунт content alpha 0→1 + `scale` 0.98→1 | **`motion_slow` 450** | - | **`ease_out_expo`** (0.16, 1, 0.3, 1) | instant swap |
| 12.14 | Sheet C / E in / out | Material bottom-sheet default | 300 | 225 | platform | platform handles it |
| 12.15 | Toolbar hairline on scroll | alpha 0→1 | 220 | 220 | `ease_standard` | instant |

**Haptics** (`00-rules.md` 8.10): exactly two in this whole surface. `View.pressHaptic()` fires once
on the primary CTA of a *confirmation* - `btn_gate_telegram` and `btn_submit` - and once at the first
frame of the success beat. Nothing else vibrates. No haptic on segment change, field focus, sheet
open or error.

**What never happens here**: no shake on a wrong password, no bounce, no elastic, no spring, no
looping ambience, no page-load choreography, no staggered entrance of the form's fields, no
confetti, no checkmark flourish beyond the single 160 ms scale in the beat, no animated height, no
animated margin, no cross-fade on the tab switch beyond the shell's own 220 ms.

**Why the beat is not a second hero moment**: it is built entirely from the 220 / 160 / 120 state
tempo. `motion_emphasis` 600 belongs to connect confirmation and appears nowhere in this document
(`00-rules.md` 8.4). The 450 ms hand-off is separately reserved by the token scale for exactly this
transition (`00-rules.md` 3.7, `Dur.Slow`).

---

## 13. Errors

### 13.1 The mapping

`ApiError` (`auth/ApiError.kt`) → string, replacing `LoginActivity.messageFor()`. Every message is
cause + fix; none contains a code, a URL, or a response body.

| `ApiError` | Where it is shown | String |
|---|---|---|
| `Unauthorized` (site login) | screen error line + both credential fields flash | `Неверная почта или пароль. Проверьте и повторите.` |
| `Unauthorized` (2FA step) | `err_otp`, code cleared, focus kept | `Неверный код. Проверьте приложение-аутентификатор.` |
| `Gone` | screen error line | `Ссылка устарела. Начните вход заново.` |
| `NotFound` (poll only) | not an error - it is the "keep polling" signal | - |
| `RateLimited` | screen error line, CTA disabled 60 s | `Слишком много попыток. Повторите через минуту.` |
| `ServiceUnavailable` | screen error line | `Сервис временно недоступен. Повторите через пару минут.` |
| `Network` | screen error line | `Нет подключения к интернету. Проверьте сеть и повторите.` |
| `Timeout` (request) | screen error line | `Сервер не отвечает. Повторите позже.` |
| `Timeout` (Telegram poll, 180 s) | screen error line | `Мы не дождались подтверждения. Начните заново.` |
| `Server(409)` (register) | `err_email`, focus moves to the email field | `Аккаунт с этой почтой уже существует. Войдите или восстановите пароль.` |
| `Server(*)` | screen error line | `Что-то пошло не так. Повторите попытку.` |
| `Parse` | screen error line | `Что-то пошло не так. Повторите попытку.` |
| `NotConfigured` | never reaches the UI | the destination is removed at start-up |
| `ActivityNotFoundException` (no Telegram, no browser) | screen error line | `Telegram не установлен. Войдите по почте.` |
| Google `NoCredentialException` | screen error line | `Не удалось войти через Google. Войдите по почте.` |
| Google user-cancelled | **nothing** | a dismissed picker is not a failure |

### 13.2 Client-side validation

| Field | Rule | When | Message |
|---|---|---|---|
| email | `Patterns.EMAIL_ADDRESS` | on blur, and live once it has been invalid once | `Введите корректный email, например name@example.com` |
| password (sign-in) | non-empty | on submit | the CTA is simply disabled; no message |
| password (register) | ≥ 8 characters | live (the documented exception in `00-rules.md` 7.4) | helper, neutral: `Не менее 8 символов` |
| repeat password | equal to password | on blur | `Пароли не совпадают` |
| OTP | exactly 6 digits | live | `Код состоит из 6 цифр` |
| handoff code | non-empty | on submit | the CTA is simply disabled |

After a failed submit, focus moves to the first invalid field and `auth_scroll` scrolls it into view
(`00-rules.md` 7.4).

### 13.3 What is never done

- No `Toast` anywhere in this surface.
- No `AlertDialog` anywhere in this surface. The debug diagnostic dialog is deleted; the real cause
  goes to `Log.w` with the token and URL already stripped by the data layer.
- No error text on top of, or inside, a filled accent surface.
- No red used for anything except an error or a destructive action; there is no destructive action
  in auth.

---

## 14. Keyboard, IME, autofill, focus

### 14.1 Per-field contract

| Field | `inputType` | `imeOptions` | `autofillHints` | Extra |
|---|---|---|---|---|
| `et_email` | `textEmailAddress` | `actionNext` (password method), `actionDone` (link method) | `emailAddress` | `maxLines=1`, `ellipsize=end` |
| `et_password` (sign-in) | `textPassword` | `actionDone` | `password` | reveal toggle 48×48 |
| `et_password` (register) | `textPassword` | `actionNext` | `newPassword` | reveal toggle |
| `et_password2` | `textPassword` | `actionDone` | `newPassword` | reveal toggle |
| `otp_input` | `number` | `actionDone` | `smsOTPCode` | `maxLength=6`, auto-submit on the sixth digit |
| `et_handoff` | `textVisiblePassword` | `actionDone` | none | no autocorrect, no suggestions |

Every `actionDone` runs **the same submit path as the button**, guarded by the same 500 ms debounce,
and only when the submit gate passes. Today the two paths are separate
(`LoginActivity.kt:133-148` vs `:109`); they become one function.

### 14.2 Window and insets

- `AuthEmailActivity`: `android:windowSoftInputMode="adjustResize"`. Edge-to-edge via
  `WindowCompat.setDecorFitsSystemWindows(window, false)`.
- `auth_scroll` bottom padding = `max(navigationBars.bottom, ime.bottom)`, applied through
  `ViewCompat.setOnApplyWindowInsetsListener`. The CTA therefore sits directly above the keyboard
  when it is up, and above the navigation bar when it is not.
- The toolbar consumes the status-bar inset as top padding.
- On the gate, `gate_scroll` takes the same treatment; the bottom-anchored action stack rides the
  keyboard if one ever appears (it does not today, but the contract must not depend on that).

### 14.3 Autofill

- The whole form is `importantForAutofill="yes"`; each field declares its hint (14.1). This is what
  makes a password manager offer a fill on the email field.
- After a **successful** email/password sign-in, call `AutofillManager.commit()` so the OS offers to
  save the credential. Missing this is why users of the current screen are never prompted to save.
- After a successful registration, `commit()` too - the `newPassword` hints make it a save
  candidate.

### 14.4 Focus

- Focus order follows visual order everywhere; nothing is reachable only by touch.
- The gate opens with **no** field focused and **no** keyboard (there is no field).
- Surface B opens with `et_email` focused and the keyboard shown, unless the email buffer is already
  filled (returning from 2FA cancel), in which case `et_password` is focused.
- The 2FA step focuses `otp_input` and shows the keyboard.
- Focus indicators: `22-components.md` R7. Filled controls get an **inner** 2dp `colorOnPrimary` @
  40 % ring; everything else an **outer** 2dp `colorPrimary` ring at 2dp offset. Rendered for
  hardware keyboard and TV D-pad only (`00-rules.md` 7.1).

### 14.5 Accessibility

| Requirement | How |
|---|---|
| Every icon-only control named | the reveal toggle («Показать пароль» / «Скрыть пароль»), the toolbar back («Назад»), the sheet rows |
| Headings exposed | `android:accessibilityHeading="true"` on `gate_title`, `sync_title`, and the «отправлено» title |
| Errors announced | `accessibilityLiveRegion="polite"` on `auth_error`, `gate_error`, `err_*` |
| The wait announced | `accessibilityLiveRegion="polite"` on `gate_awaiting_row`; the title and body are read as one node |
| Loading announced | `stateDescription = @string/state_loading` («загрузка») on a loading button; the label is `visibility=INVISIBLE`, so its text is not read while hidden |
| OTP is one node | cells `importantForAccessibility="no"`; `otp_input` carries the description |
| Targets | 48×48 minimum everywhere; the OTP cells are decoration and the 56dp-tall full-width input is the target |
| Contrast | every pair used here is quoted from `00-rules.md` 3.5 and clears 4.5:1 for body, 3:1 for large text and control boundaries |
| Colour never alone | the error state carries a colour **and** a sentence; the selected segment carries fill **and** colour **and** weight 700 |
| Reduced motion | section 12, right-hand column |
| Font scale 200 % | no fixed heights anywhere in this document; verified in section 16 |

---

## 15. The state matrix

`00-rules.md` 15 requires each applicable state to be designed, implemented and looked at. This is
the checklist; a screenshot exists for every filled cell before the surface is called done.

| State | A gate | B form | B 2FA | B sent | C sheet | D overlay |
|---|---|---|---|---|---|---|
| Default | 5.2 | 6.2 | 6.7 | 6.8 | 9 | 10.2 |
| First run | identical to default - there is no separate first-run auth | ✓ | - | - | - | - |
| Loading | 5.4 CTA loading | 6.5 submitting | 6.5 | resend cooldown 5.6 | - | the overlay **is** the loading state |
| Empty | n/a | n/a | n/a | n/a | n/a | n/a |
| Error | 5.4 | 6.5 | 6.7 | resend failed → error line above the actions | n/a | 10.2 error column |
| Offline | 5.4 (actions stay live) | 6.5 | 6.5 | 6.5 | n/a | error column, cause = network |
| Partial | n/a | n/a | n/a | n/a | n/a | some data imported → the overlay continues; a failed *server* import still lands the user (the account exists) and Главная shows «Загрузить серверы» |
| Long content | 2-line title, 3-line body | 60-char email, 3-line error | - | 4-line body | 2-line subtitles | 3-line stage line |
| Short content | n/a | n/a | n/a | n/a | 1 row (Google absent) | n/a |
| Disabled / gated | rate-limited | submit gate | submit gate | resend cooldown | - | - |
| Success | 12.6a | 12.6b | 12.6b | n/a | n/a | 12.13 |
| Font scale 200 % | 16 | 16 | 16 | 16 | 16 | 16 |
| Dark / light / mono | 16.5 | 16.5 | 16.5 | 16.5 | 16.5 | 16.5 |

Product-specific gate states from `00-rules.md` 15 that touch this surface: `Telegram не привязан`
(surface E), `нет подписки` (handled by Аккаунт after the hand-off, not here).

---

## 16. Adaptivity

### 16.1 320dp width

- Gate: content width 288dp. The 440dp cap never binds. «Войти через Telegram» with a 20dp icon and
  8dp gap needs ~215dp at 16sp/700; it fits with 73dp to spare.
- Form: the segment's two items are 136dp each; «Ссылка на почту» at 16sp/500 needs ~128dp. Fits.
- OTP: cells 41.3dp wide (7.1). Fits.

### 16.2 Font scale 200 %

Every height in this document is `minHeight` on a `wrap_content` view (`22-components.md` R2), so
every control grows instead of clipping. Specific checks:

- Gate: intro grows from ~86dp to ~172dp; the spacer absorbs it down to its 32dp floor; then the
  column scrolls. The CTA is never off-screen.
- Segment: track grows 48 → ~64; the two labels wrap to two lines each and the track grows again.
  This is why the segment's height is `minHeight` and its labels are `maxLines=2`.
- Form: the column reaches ~1180dp and scrolls.
- OTP: cells grow to ~76dp tall; the digit at 40sp fits the 41dp cell width at the tabular 620/1000
  advance.

### 16.3 Landscape and split screen

The gate's spacer collapses to 32dp and the column scrolls; the action stack is then in flow at the
bottom of the content rather than pinned. Nothing is repositioned by an orientation check - the
weighted spacer does it all. The form is unchanged; it scrolls, as it already does.

### 16.4 `sw600dp`

Gutter steps 16 → 24 and the content column caps at 720dp centred (`00-rules.md` 4.1). Nothing else
changes: no two-column split, no side-by-side methods. The gate's action stack keeps the column
width, it does not stretch to 720dp: every auth surface is a single-column form and is capped at
`auth_column_max` 440, centred. The 720dp cap in `00-rules.md` 4.1 governs ledger and list screens,
where a wide row is still readable; a 720dp-wide text field is not.

### 16.5 Themes

All three ship and all three are checked for every state.

| | Dark (default) | Light | Mono |
|---|---|---|---|
| Page | `#0A0B0D` | `#F4F7FC` | `#000000` |
| Primary CTA | `#4C8DFF` fill, `#00183A` label (5.51:1) | `#1E5FC7` fill, `#FFFFFF` label | ink fill, paper label |
| Tertiary label | `#4C8DFF` (6.64:1 on page) | `#1E5FC7` (5.97:1) | ink |
| Field fill | `#20242B` | `#E3EAF4` | one step off black |
| Field stroke | `#2A2E36` → `#4C8DFF` focused | `#C3CCDC` → `#1E5FC7` | ink |
| Error text | `#FF6069` (6.15:1) | `#C42B32` (5.62:1) | ink; the message is the signal, plus the 2dp stroke |
| Neutral tile | `#20242B` fill, `#9BA1AD` glyph | `#E3EAF4` / `#54607A` | - |

**Mono check**: squint at the gate. The primary must still be the loudest element with the hue
removed. If Primary and Tertiary become indistinguishable in mono, the Tertiary loses the ink fill
and keeps only the label - never the reverse.

### 16.6 RTL

`values-ar/` exists in this app. Every margin and padding in this document is
`Start`/`End`, never `Left`/`Right`; the toolbar back arrow, the row chevron and the OTP cell order
mirror automatically. The `depv://` and `t.me` strings are not user-visible and are unaffected.

---

## 17. Implementation map

### 17.1 New files

| File | What |
|---|---|
| `res/layout/layout_account_gate.xml` | surface A (5.1) |
| `res/layout/activity_auth_email.xml` | surface B (6.1) |
| `res/layout/layout_auth_otp.xml` | the six-cell group (7) |
| `res/layout/sheet_auth_methods.xml` | surface C (9) |
| `res/layout/sheet_link_telegram.xml` | surface E (8.4) |
| `res/layout/layout_sync_overlay.xml` | surface D (10.2) |
| `java/com/v2ray/ang/ui/AuthEmailActivity.kt` | surface B |
| `java/com/v2ray/ang/ui/AuthMethodsSheet.kt` | surface C |
| `java/com/v2ray/ang/ui/LinkTelegramSheet.kt` | surface E |
| `java/com/v2ray/ang/ui/widget/OtpCodeView.kt` | the OTP widget |
| `res/drawable/bg_otp_cell.xml` | OTP cell state list (7) |
| `res/drawable/ring_full_2dp.xml` | the completed ring for the success beat |
| `res/drawable/bg_segment_track.xml` | `22-components.md` 6.3 |
| `res/drawable/ic_spinner_arc.xml`, `res/animator/spinner_rotate.xml`, `res/drawable/spinner_arc.xml` | `22-components.md` 17.1 |
| `res/interpolator/ease_out_expo.xml` | `pathInterpolator` 0.16, 1, 0.3, 1 - **new** |

### 17.2 Changed files

| File | Change |
|---|---|
| `java/com/v2ray/ang/ui/AccountFragment.kt` | render surface A when signed out; own the gate's state rendering |
| `java/com/v2ray/ang/viewmodel/AuthViewModel.kt` | replace `LoginState` + `twoFactor` with `AuthUiState` (4.1); own error consumption; own the 500 ms debounce; hold `tempToken`, the opened deep link and the form buffers in `SavedStateHandle` |
| `java/com/v2ray/ang/auth/AuthManager.kt` | add `beginRegister`, `beginMagicLink`, `beginPasswordReset`, `consumeAppHandoff`, `loginGoogle` orchestration (mirroring `Account/AuthManager.cs:167-320`) |
| `java/com/v2ray/ang/auth/DepartamentApiClient.kt` + `Impl` | add `register`, `verifyEmail`, `requestMagicLink`, `consumeMagicLink`, `requestPasswordReset`, `createAppHandoff`, `consumeAppHandoff` (endpoints exist server-side; the desktop already calls them, `Account/BackendConfig.cs:42-51`) |
| `java/com/v2ray/ang/auth/BackendConfig.kt` | add the six endpoint constants, `siteUrl` and `googleClientId` |
| `java/com/v2ray/ang/ui/UrlSchemeActivity.kt` | handle `depv://auth/{code}` (9.2, 9.3); clear the Intent data after consumption |
| `java/com/v2ray/ang/ui/MainActivity.kt` | host surface D; remove `updateAccountGate()`'s runtime hiding of `nav_account` (`23-account-rework.md` 5.1) |
| `res/layout/activity_main.xml` | add `layout_sync_overlay` as the last child |
| `AndroidManifest.xml` | remove `LoginActivity`; add `AuthEmailActivity`; add the `depv://auth` path to the existing `depv` intent filter |
| `res/values/strings_auth.xml` | rewritten (section 11) |
| `res/values-*/strings_auth.xml` (ru, vi, zh-rCN, zh-rTW, bn, ar) | regenerated from the new key set; the dash and three-dot debt listed in `00-rules.md` 9.7 for this file is cleared in the same change |
| `res/values/dimens.xml`, `motion.xml`, `colors.xml`, `styles.xml`, `themes.xml` | the tokens in 17.4 |

### 17.3 Deleted files

`res/layout/activity_login.xml`, `java/com/v2ray/ang/ui/LoginActivity.kt`, and the auth block inside
`res/layout/layout_home_empty.xml` (the whole file is deleted by `11-app-structure.md` 11.1).

### 17.4 Tokens this document needs

All of `22-components.md` 20.1, plus:

```xml
<!-- res/values/dimens.xml -->
<!-- The post-sign-in hand-off ring (14-auth 10.2). 64 is the empty-state /
     hero glyph size already used by Size.EmptyIcon on desktop. -->
<dimen name="sync_ring">64dp</dimen>

<!-- Single-column cap for the two auth surfaces. Mirrors the desktop's
     MaxWidth 440 on LoginView so one number serves both platforms. The 720dp
     cap of 00-rules 4.1 governs ledger screens; a 720dp text field does not
     read. -->
<dimen name="auth_column_max">440dp</dimen>

<!-- res/values/motion.xml -->
<!-- The single auth -> account hand-off. 00-rules 3.7 lists Dur.Slow 450 as
     desktop-only; this is its Android mirror. Nothing else may use it. -->
<integer name="motion_slow">450</integer>
```

```xml
<!-- res/interpolator/ease_out_expo.xml -->
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.16" android:controlY1="1"
    android:controlX2="0.3"  android:controlY2="1" />
```

New styles in `res/values/styles.xml`, all defined by `22-components.md` and merely consumed here:
`Widget.Departament.Button.Primary`, `.Primary.Tall`, `.Tertiary`, `Widget.Departament.Segment`,
`Widget.Departament.TextField`, `Widget.Departament.TextField.EditText`,
`Widget.Departament.Progress.Ring`, `Widget.Departament.Sheet`.

`res/color/field_stroke.xml` - a `ColorStateList`: `state_focused` → `?attr/colorPrimary`,
`state_activated="false"` error handled by `app:boxStrokeErrorColor`, default →
`?attr/colorOutline`.

**One token in the shipped tree is wrong and must be fixed before any of this is implemented.**
`res/values-night/colors.xml:23` declares `ping_bad` as `#F04452`, which measures **4.88:1** on
`?attr/colorSurface` and is the *fill* red, not the *text* red. `00-rules.md` 3.5 and its section 18
row are explicit: error text on dark is `#FF6069` (6.15:1), delivered through `@color/ping_bad`.
Every error string in this document is drawn in that token, so the value is corrected in the same
change:

```xml
<!-- res/values-night/colors.xml -->
<!-- Error TEXT on a dark surface. #F04452 (the fill red) measures 4.88:1 and is
     below the body floor; #FF6069 measures 6.15:1. See 00-rules.md 3.5 / 18. -->
<color name="ping_bad">#FF6069</color>
```

`res/values/colors.xml:36` (`#C42B32`, 5.62:1 on light) is already correct and does not change.

### 17.5 Data contract for the UI

`AuthViewModel` exposes exactly this to the views. No view reads a repository.

```
val state: StateFlow<AuthUiState>
val email: MutableStateFlow<String>            // shared by every email-bearing method
val password: MutableStateFlow<String>
val passwordRepeat: MutableStateFlow<String>
val otp: MutableStateFlow<String>              // digits only, max 6, sanitised in the setter
val handoffCode: MutableStateFlow<String>
val submitEnabled: StateFlow<Boolean>          // the gate of 13.2 for the current mode+method
val resendCooldownActive: StateFlow<Boolean>

fun startTelegramLogin()
fun cancelTelegramLogin()
fun submit()                                   // one entry point: button AND ime action
fun setMode(register: Boolean)
fun setMethod(link: Boolean)
fun cancelTwoFactor()
fun resend()
fun consumeHandoff(code: String)
fun startGoogle(activity: Activity)
fun consumeError()
```

---

### 17.6 Icons and strings that do not exist yet

Verified against `res/drawable/` and `res/values/` on 2026-07-26. Everything else this document
references already exists.

| Referenced | Status | Action |
|---|---|---|
| `ic_telegram_24dp` | exists | use |
| `ic_chevron_right` | exists | use |
| `ic_globe_24dp` | exists | use for «Через сайт» |
| `ic_lp_key` | exists | **rename to `ic_key`** and use for «У меня есть код». The `lp_` prefix meant "local proxy"; it is now used on two surfaces and a per-screen prefix breaks the one-family rule (`00-rules.md` 10.1) |
| `ic_lp_eye` / `ic_lp_eye_off` | exist | **rename to `ic_eye` / `ic_eye_off`**, same reason |
| `ic_fab_check` | exists | **rename to `ic_check`**; it is the product's only check glyph |
| `bg_icon_neutral` | exists | use for every 40dp tile here |
| `ic_arrow_back` | **missing** | NEW 24dp vector, Material `arrow_back`. Port the desktop's `Geo.Login.Back` path (`LoginView.axaml:32`) so the two platforms draw the identical arrow |
| `ic_mail` | **missing** | NEW 22dp vector. Port `Geo.Login.Mail` (`LoginView.axaml:43`) |
| `ic_more_horiz` | **missing** | NEW 22dp vector |
| `ic_google` | **missing** | NEW 22dp **single-colour** vector. Port `Geo.Login.Google` (`LoginView.axaml:41`) |
| `@string/common_back` «Назад» | **missing** | NEW, in `values/strings.xml` (not `strings_auth.xml` - it is shared chrome) |
| `@string/common_cancel` «Отмена» | **missing** as a shared string; `devices_delete_cancel` duplicates it | NEW shared `common_cancel`; `devices_delete_cancel` folds into it |
| `@string/state_loading` «загрузка» | **missing** | NEW, defined by `22-components.md` 20.1 |

The four new vectors are ported from the desktop's `StreamGeometry` set rather than redrawn, so the
two clients draw the same path data at the same optical weight. That is `00-rules.md` 10.1's "ported,
not redrawn" applied literally.

---

## 18. Parity with the desktop

### 18.1 Identical by contract

The Russian string for every shared concept (section 11 ⇄ `Common/L.Account.cs:124-204`), the state
set (4.1), the error mapping (13.1), the component vocabulary, the token values, and the motion
tempo.

### 18.2 Deliberate, recorded divergences

| # | Android | Desktop | Why |
|---|---|---|---|
| DV-1 | **Telegram is the primary**, email is one level down | **Email is primary**, Telegram is a demoted tonal button (`LoginView.axaml:516-564`) | On an Android phone in this market Telegram is installed and the deep link is a genuine one-tap with no typing. On a desktop it often is not installed and typing is cheap. The *component vocabulary* and *strings* stay identical; only which one is filled changes |
| DV-2 | Method choice is a **sheet** | Method choice is a stack of demoted actions | `00-rules.md` 13 explicitly allows per-item action surfaces to differ (sheet vs flyout). The sheet exists on Android because a bottom sheet is the platform's answer |
| DV-3 | The «отправлено» tile is **static** | The verify-email ring spins | Android has no poll on that path (the site returns through `depv://auth`); the desktop keeps its poll. An indicator must match reality on each platform |

### 18.3 What the desktop must change (parity gaps to log)

| # | `LoginView.axaml` / `OnboardingView.axaml` | Change | Rule |
|---|---|---|---|
| PG-A1 | `Grid Background="{DynamicResource Brush.HomeGradient}"` (`LoginView.axaml:237`, `OnboardingView.axaml:43`) | flat `Brush.Bg` | `00-rules.md` 1.4.3, `03-direction.md` F1 - decorative gradients do not exist in this product |
| PG-A2 | `ShieldTile` 64 with `Geo.Login.Shield` (`LoginView.axaml:282-296`, `OnboardingView.axaml:61-74`) | delete both | `03-direction.md` F17 - no shield beyond the connect object. Android's gate has none |
| PG-A3 | `OnboardingView.axaml` in full | delete the view | `11-app-structure.md` 5.1 - onboarding is not a surface. Its two provisioning actions move to the Серверы empty state |
| PG-A4 | `GoogleButton` `IsEnabled="False"` + «Скоро» (`LoginView.axaml:617-643`) | show only when Google is configured, else omit | `03-direction.md` F15, section 9.5 here |
| PG-A5 | `Login_WaitingConfirm` «Ожидаем подтверждения в Telegram» | «Ждём подтверждения в Telegram» | one string per concept (`00-rules.md` 13) |
| PG-A6 | `Login_TelegramConfirmHint` contains « - остальное сделаем сами» | «Подтвердите вход в открывшемся приложении» | `00-rules.md` 1.4.11 / 9.2 - no dashes |
| PG-A7 | `Login_ResetSentHint`, `Login_VerifyHint`, `Login_MagicSentHint` contain « - » | rewritten to the strings in 11.4 | same |
| PG-A8 | `Button.SegItem` local class, radius 8, `FontSize 14` (`LoginView.axaml:78-107`) | `ToggleButton.Segment` from `22-components.md` 6 | one component, one radius, one ramp |
| PG-A9 | `Button.Tonal.Tall` declared locally in two views | `Button.Secondary` has no `.Tall` | `22-components.md` 2.1 |
| PG-A10 | `PlaneBreathe` infinite opacity+scale loop (`LoginView.axaml:203-226`) | delete | `00-rules.md` 8.1 / `03-direction.md` 8.5 - no looping ambience; the spinning arc already says "waiting" |
| PG-A11 | The awaiting block is a full-column replacement | the 56-row treatment of 5.3 | `03-direction.md` 3.3 - the ledger row is the universal unit on both platforms |
| PG-A12 | `LoginView` is a pushed sub-page with its own «← Вход» toolbar | a state of the Account view, as on Android | `11-app-structure.md` 4.3.1 - "There is no `LoginActivity` and no `LoginView` any more" |

PG-A12 is the largest and is the desktop half of this document; it is out of scope here and belongs
in the desktop auth spec, which must be written against **this** state machine.

---

## 19. Acceptance

### 19.1 Mechanical (must return nothing)

From `/home/user/dp/V2rayNG/app/src/main/res`:

```bash
# the deleted screen is really gone
grep -rn "activity_login\|LoginActivity" layout/ ../java/ ../AndroidManifest.xml
# raw colour literals in the new layouts
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint|app:strokeColor)="#' \
  layout/layout_account_gate.xml layout/activity_auth_email.xml layout/layout_auth_otp.xml \
  layout/sheet_auth_methods.xml layout/sheet_link_telegram.xml layout/layout_sync_overlay.xml
# off-scale spacing in the new layouts
grep -rnoE '"(-?[0-9]+)dp"' layout/layout_account_gate.xml layout/activity_auth_email.xml \
  layout/layout_auth_otp.xml layout/sheet_auth_methods.xml layout/layout_sync_overlay.xml \
  | grep -vE '"(0|1|2|4|8|12|16|20|22|24|28|32|36|40|44|48|52|56|64)dp"'
# fixed button heights (must be minHeight only)
grep -rn 'layout_height="4[0-9]dp"\|layout_height="5[0-9]dp"' layout/activity_auth_email.xml layout/layout_account_gate.xml
# synthetic bold, all-caps, inline sizes
grep -rn 'textStyle="bold"\|textAllCaps="true"\|android:textSize' layout/layout_account_gate.xml layout/activity_auth_email.xml
# dashes and three-dot ellipsis in the new copy
grep -rn -e '—' -e '–' -e '\.\.\.' values*/strings_auth.xml
# Toast in the auth path
grep -rn 'toast(' ../java/com/v2ray/ang/ui/AuthEmailActivity.kt ../java/com/v2ray/ang/ui/AccountFragment.kt
```

### 19.2 By eye, with a screenshot in front of you

1. **Count the blue** on the gate: exactly one filled surface, at most three tinted elements. On the
   form: exactly one filled surface («Войти»), plus the focused field's stroke and the two tertiary
   labels - three tinted. At the cap, not over it.
2. **Count the cards**: zero, on every auth surface.
3. **Measure the text origin** on the awaiting row and on the sheet rows: 68dp, with a ruler.
4. **Count the gaps** on the form: 24, 16, 12, 8, 4 - five distinct values, not a 16dp drone.
5. **Squint**: on the gate, the CTA must be the only thing that survives the blur. On the form, the
   heading of hierarchy is CTA → fields → tertiaries.
6. **Find a Russian string set in Space Grotesk.** There must be exactly one exception and it is the
   Latin token `departament` inside `gate_title` (5.1), which is not Russian.
7. **Change the OTP code from `111111` to `888888`** in the preview and confirm nothing moves
   (tabular figures, 7.1).
8. **Twelve screenshots**: gate idle, gate pressed, gate loading, gate awaiting, gate error, form
   default, form focused, form error, 2FA, «отправлено», sheet, overlay. Then the same twelve at font
   scale 200 %, then in light, then in mono.
9. **Turn animations off** in Developer options and repeat the flow end to end. Every state must
   still be reachable and legible; nothing may be stuck mid-fade.
10. **The category question**: crop the wordmark out. Does this still read as an instrument rather
    than a gamer VPN sign-up? If a shield, a glow or a globe has appeared, it failed.
11. **The trust test**: would someone who lives in Raycast, Linear and Telegram trust this form? Every
    control standard-shaped, every state legible, no control that makes them wonder what it does.
12. **320dp, 200 % font scale, landscape, split screen** - no clipping, no truncated primary label,
    no horizontal scroll, the CTA always reachable.

### 19.3 Definition of done

`00-rules.md` 17.1 scoring, ≥ 18/20 with no dimension below 3, plus:

- every row of section 15's matrix has a screenshot,
- every string in section 11 exists in `values/` and in all six translated `values-*/` variants,
- the greps in 19.1 are clean,
- `LoginActivity` and `activity_login.xml` no longer exist in the tree,
- the desktop parity gaps in 18.3 are **logged** (not necessarily fixed) with issue numbers.

---

## 20. Decisions

### 20.1 Taken here, inside existing law

- **D-14.A** The gate is a state of Аккаунт, never a pushed Activity. `LoginActivity` is deleted.
- **D-14.B** Exactly two actions on the gate: one Primary, one Tertiary. Registration lives on the
  form page, where the fields it needs already are.
- **D-14.C** The Telegram wait is drawn as the universal 56dp row at the 68dp text origin, not as a
  bespoke centred hero.
- **D-14.D** Method switching and mode switching are **instant**; only colour crossfades. Animating a
  height is banned, and a jump under a fade is worse than a clean swap.
- **D-14.E** Cancel is system Back everywhere in this surface: Back cancels the poll, cancels 2FA and
  pops the sub-page, in that priority order, and never traps.
- **D-14.F** No `Toast` and no `AlertDialog` anywhere in auth. The debug diagnostic dialog is deleted
  rather than release-gated.
- **D-14.G** Google appears only when configured; there is no «Скоро» teaser.
- **D-14.H** The «отправлено» tile is static, because nothing is polling.

### 20.2 Rows for `00-rules.md` section 18

| Date | Decision | Rule affected |
|---|---|---|
| 2026-07-26 | **D-14.1** The gate heading «Вход в departament» sets the Latin token `departament` in Space Grotesk inside a Russian sentence. This is the product's wordmark appearing in its one legitimate slot on this screen, and it replaces a logo lockup. Fallback if rejected: one span removed, layout unchanged | `03-direction.md` 3.1, 6.1 |
| 2026-07-26 | **D-14.2** On the sign-in gate, network-dependent actions are **not** disabled when offline, because every action on that screen is network-dependent and disabling them leaves a dead screen. The failure is surfaced by the error line with a fix | `00-rules.md` 9.6, `11-app-structure.md` 5.3 variant E |
| 2026-07-26 | **D-14.3** Screen-level errors are Body 14/400 in `@color/ping_bad`; field-level errors are Caption 12/400 in the same colour. Two sizes, one meaning, product-wide | `00-rules.md` 7.1, `22-components.md` 4.1 |
| 2026-07-26 | **D-14.4** The post-sign-in hand-off reveals **Аккаунт**, not Главная: the overlay covers an account import and must reveal its result | `11-app-structure.md` 5.6 |
| 2026-07-26 | **D-14.5** New route `depv://auth/{code}` consumes a one-time browser hand-off code. It is the only `depv://` route carrying a secret: never logged, never displayed, cleared from the Intent after consumption | `11-app-structure.md` 7.2 |
| 2026-07-26 | **D-14.6** Android gains in-app registration, magic link, password reset and browser hand-off, calling endpoints that already exist server-side and that the desktop already calls | `00-rules.md` 13 (parity) |
| 2026-07-26 | **D-14.7** New Android tokens `motion_slow` 450, `ease_out_expo`, `sync_ring` 64 and `auth_column_max` 440, mirroring the desktop's `Dur.Slow` / `Ease.OutExpo` / `Size.EmptyIcon` / `LoginView`'s `MaxWidth` | `00-rules.md` 3.3, 3.7, 4.1 |
| 2026-07-26 | **D-14.8** `@color/ping_bad` in `values-night` is corrected from `#F04452` (4.88:1) to `#FF6069` (6.15:1). The shipped value contradicts the section 18 row of 2026-07-26 that created the token, and every error string in auth is drawn in it | `00-rules.md` 3.5 |
| 2026-07-26 | **D-14.9** Icon names lose per-screen prefixes: `ic_lp_eye` / `ic_lp_eye_off` / `ic_lp_key` become `ic_eye` / `ic_eye_off` / `ic_key`, and `ic_fab_check` becomes `ic_check`. One family, one name per glyph, no matter which screen first needed it | `00-rules.md` 10.1 |

### 20.3 Open questions for the owner

1. **The mixed-face heading (D-14.1).** Accept «Вход в departament» with `departament` in the brand
   face, or set the whole heading in the UI face?
2. **Referral attribution.** The web client captures `?ref=` and forwards it on email and Google
   sign-up, and has a known gap on the Telegram path. The Android client currently forwards a
   referral code on **no** path. Should the app accept a referral code (from a `depv://` link, or
   from the clipboard at first launch) and forward it to `register` and `loginGoogle`? This is a
   product decision with revenue attached, not a design one.
3. **Password reset completion.** The reset link opens the site, where the new password is set. Should
   the app also handle `depv://auth/reset/{token}` and set the new password in-app? The desktop does
   not. Recommendation: no - one place to change a password is safer and cheaper.
4. **2FA enrolment.** The app can *satisfy* a TOTP challenge but cannot enrol a device. Enrolment
   stays on the site. Confirm.
