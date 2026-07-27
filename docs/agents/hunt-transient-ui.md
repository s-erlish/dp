# Transient-state & micro-moment sweep — both clients

Scope: the surfaces a user touches every session on both apps — PC `v2rayN.Desktop`
(`Views/MainWindow`, `ConnectHeroView`, `SubscriptionMetaView`, `BuyView`, `DevicesView`,
`ViewModels/{Home,Account,Buy,Devices,PaymentHistory}ViewModel`, `Account/*`) and Android
(`ui/MainActivity`, `ui/AccountFragment`, `ui/BuyTariffActivity`, `ui/LoginActivity`,
`ui/SubscriptionPagerAdapter`, `viewmodel/AuthViewModel`).

Method: for every control and every state flag, ask — can it be entered twice? can it be left? does
the visible surface still match the data after the operation resolves, fails, or is cancelled? does
the busy indicator start when the work starts and stop when the work stops? does an error the user
did not cause get shown, and does an error the user *did* cause get swallowed?

This sweep deliberately does **not** repeat `hunt-pc-cold-start.md` (F1–F9) or
`hunt-android-cold-start.md` (F1–F11), which cover first-frame / cold-start state. Everything below
is a *live-session* defect. Every claim is anchored to a `file:line` that was read.

Ordered by severity. **No code was written in this phase.**

---

## Cross-platform pattern: the connect control is a toggle with three states and only two branches

Both apps model connect as `if (running) stop else start`. Neither models "an attempt is already in
flight", and on neither platform is the control ever disabled. The result is the same three defects
on both: a double-tap fires twice, an in-flight connect cannot be cancelled, and a tap that cannot
possibly connect still produces a full-length fake spinner ending in a real-looking error.

---

## P1 — HIGH (PC). The connect disc stays live during «Подключение…»: a second tap restarts the attempt and pushes the 12 s deadline out, and a connect can never be cancelled

`ConnectHeroView.axaml:512-517` — the disc is a plain `Border` with `Cursor="Hand"`; it is never
given `IsEnabled=false` and never has hit-testing removed.

`ConnectHeroView.axaml.cs:251-253` wires `PointerPressed`/`PointerReleased` unconditionally, and
`:708-716` raises `ConnectToggleRequested` on every release regardless of the current visual state.

`HomeHeroPresenter.cs:60` — `void OnToggle(object? s, EventArgs e) => vm.ConnectToggle();` — no gate.

`HomeViewModel.cs:184-194`:

```csharp
public void ConnectToggle()
{
    if (IsConnected) { _ = Disconnect(); }
    else            { _ = Connect(); }
}
```

`IsConnecting` is not consulted. During a connect `IsConnected` is false, so the tap takes the
`Connect()` branch, which calls `BeginConnecting()` (`HomeViewModel.cs:307-317`) — and that resets
`_connectingUntil = DateTime.Now.AddSeconds(12)` and issues another `_main.Reload()`.

**Reproduction**
1. Select a server that is slow or unreachable.
2. Tap the shield. It enters «Подключение…» (arc spins, shield pulses).
3. At t≈5 s tap the shield again — the user's intent is "stop trying".
4. Instead: a second `Reload()` is queued and the 12 s timeout is re-armed to t≈17 s.
5. Every further impatient tap extends it again. The only way out is a successful connect or
   walking away. In every other state the disc is a toggle; here it is the opposite of one.

---

## P2 — HIGH (PC). With no server the disc *looks* disabled but still fires — 12 s of fake «Подключение…» ending in a red «Не удалось подключиться»

`ConnectHeroView.axaml.cs:319-321` stores `_hasServer` but only uses it cosmetically:
`:407-410` sets `ShieldOutline.Opacity = hasServer ? 1 : 0.38` and the label to
`L.T("Home_ChooseServer")`. Nothing gates input.

A logged-in user with zero servers reaches this frame: `MainWindow.axaml.cs:867-869` routes to
`bodyRoot` (not onboarding) whenever `_isLoggedIn` is true, so the Home hero is on screen with
`HasServers == false`.

**Reproduction**
1. Sign in on an account whose subscription has not imported any servers yet.
2. Home shows the shield at 0.38 opacity with «Выберите сервер» — the app's own disabled-affordance.
3. Tap it. `BeginConnecting()` runs: arc spins, «Подключение…», 12 s deadline armed.
4. At t=12 s `SyncState` (`HomeViewModel.cs:370-380`) sets `ConnectFailed = true`.
5. The shield turns **red** with «Не удалось подключиться» and the retry hint
   (`ConnectHeroView.axaml.cs:379-401`, `:430-449`) — reporting a connection failure for a user who
   has nothing to connect to. The one control that was styled as unavailable was the one that fired.

---

## P3 — HIGH (Android). Denying the system VPN permission leaves «Подключение…» up for 20 s and then blames the app

`MainActivity.kt:229-233`:

```kotlin
private val requestVpnPermission = registerForActivityResult(StartActivityForResult()) {
    if (it.resultCode == RESULT_OK) {
        startV2Ray()
    }
}
```

There is no `else`. By the time this dialog is shown, `handleFabAction` (`MainActivity.kt:1530-1536`)
has already set `connectInProgress = true`, fired the «Подключение…» toast, run
`applyRunningState(isLoading = true, ...)` (rotating arc + accent shield, `:1621-1633`) and armed
`scheduleConnectWatchdog()` — `CONNECT_TIMEOUT_MS = 20000L` (`:214`).

The watchdog (`MainActivity.kt:191-199`) then shows `toast_status_failed`.

**Reproduction**
1. Fresh install, a server selected. Tap the connect shield.
2. Android shows its "Connection request" dialog. Tap **Отмена**.
3. The hero keeps spinning «Подключение…» for a full 20 s with the app doing nothing at all.
4. At t=20 s: «Не удалось подключиться».

The user cancelled deliberately; nothing failed. This is an error toast for a user-cancelled action,
preceded by 20 seconds of the UI asserting work it is not doing.

---

## P4 — HIGH (Android). The "no server selected" guard toasts and returns, but leaves the connecting state and the watchdog running

`MainActivity.kt:1556-1562`:

```kotlin
private fun startV2Ray() {
    if (MmkvManager.getSelectServer().isNullOrEmpty()) {
        toast(R.string.title_file_chooser)
        return
    }
    CoreServiceManager.startVService(this)
}
```

The early return clears neither `connectInProgress` nor the watchdog armed at
`MainActivity.kt:1535`.

**Reproduction**
1. Fresh install, no servers imported. Tap the connect shield.
2. A toast about choosing a config file appears — *and simultaneously* the shield enters
   «Подключение…» and spins.
3. 20 s later: «Не удалось подключиться» from the watchdog.

Two contradictory messages for one tap, 20 s apart, neither of which is "you have no server".

---

## P5 — HIGH (Android). The connect card fires on every tap, including during «Подключение…»

`MainActivity.kt:287-290`:

```kotlin
binding.cardConnect.setOnClickListener {
    animateConnectPress()
    handleFabAction()
}
```

No `connectInProgress` guard, no `isEnabled = false`. `handleFabAction`
(`MainActivity.kt:1527-1536`) branches only on `mainViewModel.isRunning.value == true`, so during an
in-flight connect it re-enters the *start* branch: another «Подключение…» toast, another
`scheduleConnectWatchdog()` (which does `removeCallbacks` then `postDelayed`, i.e. pushes the
deadline out), and another `CoreServiceManager.startVService(this)`.

**Reproduction** — identical to P1: tap connect, tap again at t=5 s, the 20 s watchdog restarts and a
duplicate start is issued to the daemon. As on PC, there is no gesture that aborts a connect.

---

## P6 — HIGH (Android). After a failed payment the Buy screen erases the selection on screen but keeps a live «Итого» and «Оплатить» for it

`BuyTariffActivity.kt:136-138` re-renders on **any** change of the error flow:

```kotlin
viewModel.tariffs.combine(viewModel.error) { tariffs, error -> tariffs to error }
    .collect { renderState() }
```

`BuyTariffActivity.kt:141-149` — the payment-error observer shows the dialog and then calls
`viewModel.clearError()`, which emits `null` and fires the combine again.

`renderTariffs` (`BuyTariffActivity.kt:226-235`) rebuilds everything from scratch:

```kotlin
tariffsContainer.removeAllViews()
checkMarks.clear()
optionRows.clear()
```

Every card comes back neutral, no options expanded, no check mark. But the selection *state* —
`selectedTariff` / `selectedOption` / `extraDevices` (`:52-54`) — is untouched, and `checkoutCard`
visibility is only ever changed by `selectTariff` (`:371`) and `selectOption` (`:380`), neither of
which runs here.

Contrast the PC port, which resets all of it explicitly on reload (`BuyViewModel.cs:201-207`:
`_selectedTariff = null; _selectedOption = null; _extraDevices = 0; ... ShowCheckout = false;`).

**Reproduction**
1. «Купить подписку» → select **Base** → select **90 дн.** → step extra devices to **+3**.
2. «Оплатить» → «С баланса» → the balance is insufficient.
3. The «Ошибка оплаты» dialog appears. Tap OK.
4. The tariff list is now fully neutral: no card highlighted, no option rows shown, no check mark —
   yet the checkout card at the bottom still reads «Итого 505 ₽» with a live «Оплатить».
5. Pressing it charges for a selection that is nowhere on screen.

The same rebuild also fires whenever any background load sets or clears `error`, so the selection can
be visually erased without the user having done anything.

---

## P7 — HIGH (Android). «Оплатить» can be pressed twice, and the screen's own progress bar is dead code

`BuyTariffActivity.kt:113` — `btnPay.setOnClickListener { onPayClicked() }`. `onPayClicked`
(`:457-480`) has no in-flight flag and never disables the button. `onMethodPicked` (`:482-517`) fires
`payWithBalance` / `buy` with no guard either.

`progress_buy` is bound at `:94` and set `View.GONE` at `:176`, `:187`, `:197` and `:218` — and is
**never set to `View.VISIBLE` anywhere in `app/src/main`** (verified by grep across `java/` and
`res/layout/activity_buy_tariff.xml`). The declared payment progress indicator does not exist at
runtime.

Contrast PC, which gets this right: `BuyViewModel.IsPaying` (`BuyViewModel.cs:81`, set at `:448`,
cleared at `:463`/`:477`) drives `IsEnabled="{Binding !IsPaying}"` on the CTA plus an inline spinner
(`BuyView.axaml:559-560`, `:576`, `:583-584`).

**Reproduction**
1. Pick a tariff and a duration. Tap «Оплатить».
2. Tap «С баланса». On a slow connection **nothing on screen changes at all** — no spinner, no
   disabled button, the sheet just closes.
3. Assume the tap missed. Tap «Оплатить» again → the method sheet reopens → «С баланса» again.
4. Two `payWithBalance` calls for the same purchase.

---

## P8 — HIGH (PC). Pinning a subscription tints the pin but never reorders the list

`SubscriptionMetaView.axaml.cs:583-609` — the whole handler:

```csharp
var sub = await AppManager.Instance.GetSubItem(subId);
sub.Pinned = !sub.Pinned;
await SQLiteHelper.Instance.UpdateAsync(sub);
if (_currentSubId == subId) { PinIcon.Foreground = sub.Pinned ? _accent : _muted; }
```

`AppManager.cs:211-214` — `GetSubItem` returns a **fresh row read from SQLite**, a different object
from the ones cached in `ProfilesViewModel.SubItems` (`ProfilesViewModel.cs:44`, populated only
inside the refresh path at `:427-431`).

`HomeViewModel.cs:688-694` orders the Home groups from that stale in-memory cache:

```csharp
Pinned = Profiles?.SubItems.FirstOrDefault(s => s.Id == g.Key.Key)?.Pinned ?? false,
```

Nothing in the pin handler re-reads `SubItems`, calls `RefreshServers()`, or triggers
`ReconcileGroups()` — unlike the delete handler two methods below, which explicitly does
(`SubscriptionMetaView.axaml.cs:646-654`).

Secondary: `OnRefreshClick` guards itself with `if (_refreshing) return;` (`:528-531`); `OnPinClick`
has no such guard, so a double-click runs two overlapping read-modify-write cycles and the second
read can observe the pre-write value — landing the flag back on its original value after tinting the
icon twice.

**Reproduction**
1. Home with two subscription groups, **B** below **A**.
2. Click the pin on **B**. Its icon turns accent immediately.
3. **B does not move.** Collapse/expand groups, switch tabs, run a ping — the order never changes.
4. Restart the app → **B** is now at the top.

The icon asserts a state the list contradicts, for the rest of the session.

---

## P9 — MEDIUM (both). The post-checkout poll never checks whether the payment was confirmed; on Android there is no success state at all

**Android** `BuyTariffActivity.kt:582-594`:

```kotlin
pollJob = lifecycleScope.launch {
    repeat(5) {
        viewModel.refreshProfile()
        viewModel.loadSubscriptions()
        delay(8000L)
    }
    pendingPayment = false
    tvPending.visibility = View.GONE
}
```

Nothing inspects payment status. The hint runs its fixed 40 s and disappears with no verdict.
`AccountFragment.kt:649-661` is the same shape with `repeat(6)` / 48 s.

**PC** does check — `BuyViewModel.IsPendingPaymentConfirmed` (`:562-573`) matches the pending
`OrderId`/`PaymentId` against `PaidStatuses` and flips to a real «Подписка оплачена» state
(`SetSuccess`, `:575-589`). But when it does *not* confirm within the window, `PollAsync` ends at
`:554` with a bare `RunOnUi(() => ShowPending = false)` — the hint just evaporates.

Also on PC the pending text flips between two values on its own: it is set to
«Завершите оплату в браузере» at `:505` and overwritten with «Платёж обрабатывается…» on the *first*
poll tick at `:540`, i.e. 8 s later, whether or not anything has happened.

**Reproduction (Android)**
1. Buy by СБП. The Custom Tab opens; complete the payment.
2. Return to the app. «Платёж обрабатывается» appears.
3. Even if the webhook confirmed at second 3, the banner sits for the full 40 s.
4. It then vanishes silently. The buy screen still offers the tariff you just bought, and nothing
   ever told the user the purchase landed.

**Reproduction (PC)** — same flow; at ~40 s the hint disappears with the tariff list unchanged. A
slow webhook and a failed payment are indistinguishable.

---

## P10 — MEDIUM (Android). Leaving and re-entering the Account tab during the payment poll restarts the 48-second window, indefinitely

`AccountFragment.kt:640-643`:

```kotlin
override fun onResume() {
    super.onResume()
    if (pendingPayment) startPaymentPolling()
}
```

`pendingPayment` is cleared only at the very end of an uninterrupted poll (`:658`) or when the
checkout could not be launched (`:634`). The job runs on `viewLifecycleOwner.lifecycleScope`, so
switching tabs destroys the fragment view and cancels the coroutine **before** line 658 runs.

**Reproduction**
1. Top up the balance and return from the browser. «Платёж обрабатывается» appears.
2. At t≈10 s switch to Home, then back to Аккаунт.
3. The banner restarts at 0:00 with a fresh 48 s window.
4. Repeat and it never terminates — a state that can be entered but not left.

---

## P11 — MEDIUM (PC). A sub-page swallows clicks for 300 ms before it is visible, and for 200 ms after it is gone

`MainWindow.axaml.cs:1099-1105` — `PushSubPage` sets `subPageHost.IsVisible = true` and then
`AnimateSubPageIn` (`:1126-1147`) sets `subPageHost.Opacity = 0` at `:1138` and fades it up over
300 ms. Avalonia hit-testing keys off `IsVisible`/`IsHitTestVisible`, **not** `Opacity`, so for those
300 ms a fully-interactive, fully-transparent full-screen page sits over the shell.

The reverse is worse: `AnimateSubPageOut` (`:1149-1171`) fades to opacity 0 over 200 ms and only then
runs `ApplySubPageResult` (`:1173-1186`), which sets `Content = null; IsVisible = false`. For 200 ms
after the user presses back, the invisible dying page still owns every click.

**Reproduction**
1. Account tab → click «Купить подписку».
2. Within ~300 ms, click where the «Устройства» row was. The click lands on the still-invisible
   BuyView (on whatever control occupies that point) rather than on the account row you can see.
3. Now press back on Buy. Within ~200 ms click a tariff-card position — the click still hits the
   Buy page that has already faded out.

---

## P12 — MEDIUM (PC). `Disconnect()` has no busy state: the shield keeps saying «Подключено» through the whole teardown, and a second tap runs a second teardown

`HomeViewModel.cs:227-243`:

```csharp
private async Task Disconnect()
{
    IsConnecting = false;
    ...
    await CoreManager.Instance.CoreStop(byUser: true);
    await SysProxyHandler.UpdateSysProxy(_config, true);
    _connectedSince = null;
    SyncState();
}
```

`IsConnected` is not touched until `SyncState()` at the end, so throughout both awaits the hero
renders the full Connected state — solid blue shield, glow, live uptime clock — while the tunnel is
being torn down and the system proxy rewritten. And because `ConnectToggle` still reads
`IsConnected == true` (`:184-194`) and the disc is never disabled (P1), a second tap during that
window starts a **second** `CoreStop` + `UpdateSysProxy`.

**Reproduction**
1. Connect. Then double-click the shield.
2. Two disconnect sequences run against the core and the system proxy.
3. For the duration, the shield shows «Подключено» with the uptime still counting up.

---

## P13 — MEDIUM (Android). Rotating the Buy screen discards the whole purchase in progress, silently

No activity in the app declares `configChanges` (verified: zero matches for `configChanges` in
`app/src/main/AndroidManifest.xml`), and `BuyTariffActivity` has no `onSaveInstanceState`.
`selectedTariff`, `selectedOption`, `extraDevices`, `pendingPayment`, `loaded`
(`BuyTariffActivity.kt:52-70`) are plain fields; `onCreate` unconditionally calls `reload()` (`:117`).

**Reproduction**
1. «Купить подписку» → select **Plus** → **90 дн.** → step extra devices to **+5**.
2. Rotate the device.
3. The screen returns to the loading skeleton, then to a fully unselected tariff list. The stepper
   is back at 0, the checkout card is gone. No message.
4. Worse path: rotate while the Custom Tab checkout is open. On return `pendingPayment` is `false`,
   so `onResume` (`:572-575`) starts no poll — the payment hint and the profile re-fetch never
   happen at all.

---

## P14 — MEDIUM (Android). An invalid top-up amount closes the dialog and destroys the typed input

`AccountFragment.kt:518-533`:

```kotlin
.setPositiveButton(android.R.string.ok) { _, _ ->
    val amount = dialogBinding.etTopUp.text?.toString()?.trim()?.toDoubleOrNull()
    if (amount != null && amount > 0.0) { showPaymentMethodSheet(amount) }
    else { toastError(R.string.account_top_up_invalid) }
}
```

The Material positive button dismisses unconditionally, then validation runs. The failure is a toast
over a dialog that no longer exists, and the typed value is gone.

Contrast PC, which keeps the flyout open on a validation failure and shows an inline error, hiding it
only on success — `AccountViewModel.cs:1343-1352` (inline `TopUpError` + early return) and
`:1385-1386` (`TopUpCheckoutOpened` raised only after the checkout actually opened; the intent is
documented at `:544-546`).

**Reproduction**
1. Аккаунт → «Пополнить».
2. Type `1 500` (the space is what the user's keyboard produces for thousands) — or a comma decimal,
   or leave the field blank.
3. Tap OK. The dialog closes, a toast appears, the amount is lost. Reopen and retype from scratch.

---

## P15 — MEDIUM (Android). The login screen shows the Telegram «ожидаем подтверждения» block and the site spinner at once, for a Telegram flow that was already cancelled

`LoginActivity.kt:296-315` — `setSiteBusy(busy)` touches `btnTelegram`, `btnSite`, `btnConfirm2fa`,
`pbSite`, `pbConfirm2fa` and the button labels. It never touches `binding.layoutAwaiting`. Only
`showIntro` (`:279`), `showAwaiting` (`:286`) and `showError` (`:318`) change that block's visibility.

`AuthViewModel.kt:47-50` — `loginSite` does `loginJob?.cancel()` (killing the Telegram poll) and sets
`LoginState.SiteLoading`. `LoginActivity.kt:202` renders that state with `setSiteBusy(true)` alone.

**Reproduction**
1. Open the login screen in its default mode (both cards visible).
2. Tap «Войти через Telegram». The awaiting block appears, `btnTelegram` is disabled (`:288`).
3. Return to the app without confirming in Telegram. Type email + password. Tap «Войти».
4. The screen now shows **«Ожидаем подтверждения…» with its spinner** — for a poll that has been
   cancelled — **plus** the site button spinner. Two live busy surfaces for one screen.
5. `btnRestart` inside the stale block is still enabled; tapping it calls `startTelegramLogin()`
   (`LoginActivity.kt:102-107`), which cancels the *real* in-flight site login (`AuthViewModel.kt:36`).

---

## P16 — MEDIUM (Android). Every carousel page shows the ROOT subscription's connected-device count

`AccountFragment.kt:317` fetches devices for the first sub only:

```kotlin
list.firstOrNull()?.remnawaveUuid?.takeIf { it.isNotBlank() }?.let { viewModel.loadDevices(it) }
```

`AccountFragment.kt:147` hands the adapter a resolver that ignores its argument entirely:

```kotlin
resolveUsedDevices = { viewModel.deviceCount.value ?: 0 },
```

`SubscriptionPagerAdapter.kt:88-90` applies that value to whichever sub is being bound, and the
adapter's own doc comment at `:19-20` claims it is "the live 'used' device count" for that card.

This is exactly the failure mode the repo's `CLAUDE.md` warns about — a subscription-scoped value
resolved from a client-level default instead of `selectedSub.uuid`.

**Reproduction**
1. An account with a root subscription (3 devices connected, limit 3) and one secondary
   subscription (0 connected, limit 5).
2. Аккаунт → swipe the carousel to page 2.
3. It reads «3 / 5». The numerator belongs to a different subscription. `renderDevicesRowValue`
   (`AccountFragment.kt:323-334`) has the same defect on the «Устройства» row.

---

## P17 — MEDIUM (PC). The subscription refresh spinner stops without reporting anything

`SubscriptionMetaView.axaml.cs:526-580` — `OnRefreshClick` swaps the glyph for a spinner, awaits
`mainVm.UpdateSubscriptionProcess(subId, false)` inside a `try/catch` that only logs (`:555-558`),
restores the glyph in `finally` (`:559-565`), then re-reads the row and re-binds (`:568-579`).

There is no success path and no failure path — only "the spinner stopped".

**Reproduction**
1. Go offline (or point the sub at an unreachable host).
2. Click the refresh glyph in a subscription group header.
3. The glyph becomes a spinner, spins, becomes a glyph again. The «last updated» subtitle
   (`FormatSubtitle`, bound at `:343-344`) still shows the old timestamp; the traffic pill and expiry
   are unchanged. Nothing says the refresh failed.
4. This is byte-for-byte the same visual outcome as a successful refresh that found no changes.

---

## P18 — LOW/MEDIUM (PC). A failed device reload over a populated list is completely silent

`DevicesViewModel.cs:410-430` — `Recompute()` ranks a non-empty list above everything else:

```csharp
if (Devices.Count > 0)      { list  = true; }
else if (_noSubscription)   { noSub = true; }
else if (IsLoading || _pendingFirstLoad) { loading = true; }
else if (Error != null)     { error = true; }
```

So `ShowError` can never be true while any row is rendered, even though `Error` is set at `:259-263`
and `ErrorText` is computed at `:440`.

The reload is not only user-triggered: `OnAccountStateChanged` (`:304-319`) fires `_ = Load()`
whenever the session's profile gains or changes a `remnawaveUuid`.

**Reproduction**
1. Open «Устройства» with a warm cache — the list renders from `AccountCache` (`:171-180`).
2. The account session refreshes and the profile's uuid changes → `OnAccountStateChanged` →
   `Load()`.
3. `GetDevices` fails (offline / 502). `Error` is set; `ShowError` stays false.
4. The screen keeps showing the stale list as if it were current. The failure is stored and never
   shown.

---

## P19 — LOW (Android). Re-tapping the already-selected server row does nothing at all — no feedback of any kind

`MainActivity.kt:1443-1445`:

```kotlin
private fun setSelectServer(guid: String) {
    val selected = MmkvManager.getSelectServer()
    if (guid == selected) return
    ...
}
```

The early return precedes every piece of feedback: no pill movement, no carousel paging
(`:1451-1457`), no snackbar (`:1458-1460`), no toast.

This exact case was identified and fixed on PC — `HomeViewModel.cs:296-300`:

```csharp
// Re-tapping the ALREADY-active server while disconnected does not reload (nothing changed),
// so connect explicitly — this is the A5 fix (that tap used to be dead).
if (!changed && !wasConnected) { await Connect(); }
```

**Reproduction**
1. Disconnected, one server selected. Servers tab.
2. Tap that server's row.
3. Nothing happens — no visual change anywhere on screen. Tapping any *other* row visibly moves the
   selection pill and pages the subscription carousel, so the dead tap reads as a missed touch and
   the user taps again.

---

## P20 — LOW (Android). During a server switch the hero says «Подключение…» while the uptime clock below it keeps counting the old session

`MainActivity.kt:1621-1633` — the `isLoading` branch of `applyRunningState` sets the connecting
visuals and `return`s. It never calls `stopConnectionTimer()`, and `timerRunnable`
(`MainActivity.kt:2015-2025`) keeps posting itself every second. `stopConnectionTimer` is reached
only from `applyIdleState` (`:1724`).

`promptApplySelectedServer`'s snackbar action (`MainActivity.kt:1477-1481`) calls exactly that:

```kotlin
connectInProgress = true
applyRunningState(isLoading = true, isRunning = true)
scheduleConnectWatchdog()
restartV2Ray()
```

**Reproduction**
1. Connect and let the uptime reach e.g. 00:04:12.
2. Tap a different server → the snackbar «Переключиться на …?» → tap the action.
3. The status line changes to «Подключение…» while the counter directly beneath it continues
   04:13, 04:14, 04:15 — until the daemon's stop broadcast lands and `applyIdleState` resets it.

Secondary observation on the same handler: the snackbar's message is built from the `guid` argument
(`:1469-1475`) but the action ignores it and calls `restartV2Ray()` (`:1575`), which starts
`MmkvManager.getSelectServer()`. If the selection moves again while the snackbar is still on screen,
the label names one server and the action connects to another.

---

## Notes for the fix phase

- P1/P2 and P3/P4/P5 are the same defect on two platforms and should be fixed as one contract:
  the connect control needs a third state (attempting), must be disabled or must abort while in it,
  and must refuse to enter it when there is nothing to connect to — with the reason stated, not a
  dimmed control that fires anyway.
- P6/P7/P9/P13/P14 are all places where the PC port already solved the problem and Android did not
  (`BuyViewModel.Reload` selection reset, `IsPaying`, `IsPendingPaymentConfirmed`/`SetSuccess`,
  inline top-up validation). The PC implementations are the reference.
- P8, P16 and P18 are the same shape as the subscription-scoping rule in the repo `CLAUDE.md`:
  a value written or read through a client-level/default path instead of the specific entity's
  identity. Worth grepping for more of these before touching anything.
- P11 needs one line in each direction (`subPageHost.IsHitTestVisible = false` while the fade runs).
