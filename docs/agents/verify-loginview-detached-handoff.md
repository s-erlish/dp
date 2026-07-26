# Verification: "Double-tapping the onboarding login CTA strands a LoginView forever"

**Target:** `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml.cs:921`
**Claimed severity:** critical
**Verdict:** **Partly real.** The code mechanism is confirmed exactly as described. The claimed
*trigger* (a pointer double-tap on the onboarding CTA) is **refuted** — it cannot reach a second
`OpenLogin()`. A narrower trigger (keyboard re-activation of the still-focused CTA) does exist, so
the underlying defect is real but low-severity, and it is **recoverable with one tap**, not "forever".

---

## 1. What the claim gets right (verified line by line)

**The sticky flag is real.**

- `LoginView.axaml.cs:110` — `private bool _detached;`
- `LoginView.axaml.cs:174` — `AttachedToVisualTree += (_, _) => Rebind();`
- `LoginView.axaml.cs:175-179` — the detach handler sets `_detached = true;` then `Unbind()`.
  Nothing anywhere in the file assigns `_detached = false` (only occurrences of the identifier are
  lines 110, 177, 921 — verified by grep).
- `LoginView.axaml.cs:919-927` — `TryHandoff()` bails on `_handoffFired || _detached || !_loggedIn ||
  !_beatDone`. So a re-attached instance is permanently barred from raising `BackRequested`.

**Re-attachment really does re-arm everything except the handoff.**
`Rebind()` (`LoginView.axaml.cs:211-293`) re-subscribes `WhenAnyValue(x => x.IsLoggedIn)
.DistinctUntilChanged().Where(loggedIn => loggedIn)` (276-281). `DistinctUntilChanged` is a fresh
operator instance per `Rebind`, and `WhenAnyValue` replays the current property value on subscribe —
the file itself relies on that replay semantics (see the comment at 225-227). So on re-attach after a
successful login, `OnLoggedIn()` (907-917) runs, `_loggedIn = true`, `PlaySuccessBeat()` starts
(`_beatStarted` is false on this instance because its subscriptions were disposed before `Success`
arrived), the beat completes, `finally` calls `TryHandoff()` (954-957) — and it returns immediately
because `_detached` is still `true`.

**The host is a `ContentControl`, so a second push detaches the first view.**
`MainWindow.axaml:611-615` — `<ContentControl x:Name="subPageHost" Background="{DynamicResource
Brush.Bg}" IsVisible="False" .../>`. `PushSubPage` (`MainWindow.axaml.cs:1099-1105`) assigns
`subPageHost.Content = view`, which detaches the previous content. `ApplySubPageResult`
(`MainWindow.axaml.cs:1173-1186`) re-assigns the previous stack entry as `Content` on pop, which
re-attaches it. `OpenLogin()` (`MainWindow.axaml.cs:1216-1232`) unconditionally constructs a new
`LoginView` and pushes it — no dedup guard.

So: **if** two `LoginView`s can ever sit on `_subStack` at once, the trace in the claim plays out
verbatim.

## 2. What the claim gets wrong

### 2a. A pointer double-tap cannot push a second LoginView

`PushSubPage` sets `subPageHost.IsVisible = true` **synchronously, inside the first click's own
handler** (`MainWindow.axaml.cs:1102-1103`), before the async `AnimateSubPageIn()` even starts.

`subPageHost` (`MainWindow.axaml:611`) and `onboardingView` (`MainWindow.axaml:596`) are siblings in
the same `<Panel Grid.Row="1">` (`MainWindow.axaml:410`), with the host declared **after** the
onboarding — so it is z-above it, stretched to the full panel (default `Stretch` alignment), and
painted with an opaque brush: `Brush.Bg` = `#0A0B0D` / `#F4F7FC`
(`Assets/GlobalResources.axaml:63`, `:101`) — a real `SolidColorBrush`, not `null`, so the host
hit-tests across its whole area.

The `Opacity = 0` set by `AnimateSubPageIn` (`MainWindow.axaml.cs:1138`) does **not** exempt it from
hit-testing — the codebase itself documents that opacity-driven entrance animations never gate input:
`OnboardingView.axaml.cs:28` — «Хит-тест НИКОГДА не гейтится анимацией — кнопки кликабельны всё
время (только opacity/transform)» (that is precisely why its own stagger, which pre-hides children at
`Opacity = 0` at lines 55-60, is safe).

Therefore, from the instant the first click's handler returns, the onboarding CTA is covered by an
opaque, hit-testable, full-bleed host. The second tap of a double-tap (≥1 frame later; Avalonia runs
layout on the render loop, far more often than the ~100-300 ms gap between two taps) lands on the
host / the new `LoginView`, not on `LoginTelegramButton`. The only pointer-reachable UI left is the
28 px title bar (`MainWindow.axaml:318-403`) which is outside the host — and it contains **only**
`btnMin` / `btnMax` / `btnClose`, no sub-page triggers.

### 2b. Every other route to a second LoginView is already guarded or unreachable

Complete set of `OpenLogin*` call sites (grep over the whole solution):

| Call site | Reachable while a `LoginView` is up? |
|---|---|
| `OnboardingView.axaml.cs:110` (`OpenLoginTelegram`) | Pointer: no (covered). Keyboard: yes — see 3. |
| `OnboardingView.axaml.cs:116` (`OpenLoginSite`) | Same. |
| `MainWindow.axaml.cs:252` (`AccountView.LoginRequested`) | No — `AccountView` lives inside `bodyRoot`, also under the host. |
| `MainWindow.axaml.cs:1091` (`HandleAuthCallback`) | **Explicitly guarded**: `if (_subStack.LastOrDefault() is not LoginView)` (1089). |
| `MainWindow.axaml.cs:272` (`PREVIEW_VIEW=login` dev hook) | Dev-only, never pushed on the stack. |

Nothing can be pushed *over* a `LoginView` either: `OpenBuy`/`OpenDevices`/`OpenHistory` are raised
only from `AccountView` (249-251) and `PaymentHistoryView` (1210); `OpenSubPage` only from
`SettingsView.axaml.cs:324` — all inside `bodyRoot`. The tray menu (`App.axaml:37-47`) offers only
Перезапустить / toggle / Показать / Выход. So the "push another page over the login, then pop back"
variant of the same bug does not exist today either.

Note that `HandleAuthCallback`'s guard at line 1089 is proof the authors already knew duplicate
`LoginView` pushes are a hazard — `OpenLogin()` itself just never got the same guard.

### 2c. "Never dismissed" / "forever" is overstated

The stranded instance still has a working back affordance: `LoginView.axaml.cs:124-128` raises
`BackRequested` from `BackButton.Click` (it sets `_handoffFired` but is not itself gated on
`_detached`), and MainWindow's per-view closure (`MainWindow.axaml.cs:1219-1230`) pops it —
skipping `CancelLogin` because `IsLoggedIn` is true. So the user sees a spurious login page wearing a
success checkmark and has to tap «назад» once. Annoying and clearly wrong; not an unrecoverable
state, and it does not block the completed login (the session is already established; the sync
overlay sits underneath).

## 3. The trigger that *does* survive scrutiny

Keyboard activation bypasses hit-testing — it is routed to the focused element, which is unaffected
by the host being painted on top. While the login sub-page is open, `onboardingView` stays
`IsVisible = true` (it is only hidden later by `ApplyShellVisibility`/`CrossfadeShellTo`,
`MainWindow.axaml.cs:867-891`, once `_isLoggedIn` flips), and `LoginTelegramButton`
(`OnboardingView.axaml:181-201`) is a plain `Button`: never disabled by the click handler
(`OnboardingView.axaml.cs:108-111` does nothing but call `OpenLoginTelegram`), and focusable —
the only `Focusable="False"` setter in the app's styles is scoped to the scrollbar page button
(`Assets/GlobalStyles.axaml:75`, `ControlTheme x:Key="Incy.ScrollBarPageButton"`).

So a user who activated the CTA from the keyboard (Tab → Enter/Space), or who clicked it and then
pressed Enter/Space again while waiting — including Enter key-repeat from holding the key — fires
`OnLoginTelegram` a second time, pushes LV2, and reproduces the claimed trace exactly.

This is a real but narrow path: keyboard-only, and self-recoverable with one back tap.

## 4. Corrected description

> `MainWindow.OpenLogin()` (`MainWindow.axaml.cs:1216`) pushes a new `LoginView` unconditionally,
> without the "already on top" guard that `HandleAuthCallback` (`:1089`) applies. Any second
> activation of an onboarding login CTA while a `LoginView` is already on `_subStack` therefore
> stacks a second one and detaches the first (`subPageHost` is a `ContentControl`,
> `MainWindow.axaml:611`). Pointer input can no longer reach the CTA at that moment (the opaque
> full-bleed host is made visible synchronously in the same handler), but **keyboard** activation
> of the still-focused, still-enabled CTA can. When the login then succeeds, the top view pops and
> `ApplySubPageResult` (`:1177`) re-attaches the first one; its `AttachedToVisualTree` → `Rebind()`
> (`LoginView.axaml.cs:174`) re-subscribes and `WhenAnyValue` replays `IsLoggedIn = true`, so it
> plays a second success beat — but `TryHandoff()` (`:921`) refuses to raise `BackRequested`
> because `_detached`, set at `:177`, is never cleared on re-attach. Result: after a successful
> login the user is left staring at a redundant login page bearing a success checkmark, which he
> must dismiss manually with «назад».

**Severity: low** (keyboard-only reachability, cosmetic-but-confusing outcome, one-tap recovery) —
not critical.

## 5. Suggested fix (two independent one-liners; do both)

1. Dedup the push, mirroring the guard that already exists for the auth callback —
   in `OpenLogin()` (`MainWindow.axaml.cs:1216`): `if (_subStack.LastOrDefault() is LoginView)
   return;` (`OpenLoginTelegram`/`OpenLoginSite` would then just re-run their command on the live
   view, which is the right behaviour anyway — the VM is shared).
2. Make `_detached` a *state* rather than a tombstone — clear it where the view is re-armed, i.e.
   `AttachedToVisualTree += (_, _) => { _detached = false; Rebind(); };`
   (`LoginView.axaml.cs:174`). This keeps the flag's original purpose intact (the commit that added
   it, `bc3e351`, wanted the deferred `finally → TryHandoff` after the ~0.4 s beat to never fire on a
   popped view) while letting a genuinely re-attached view finish its handoff.
