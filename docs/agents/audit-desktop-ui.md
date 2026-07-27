# Desktop UI correctness audit — `v2rayN.Desktop`

Scope: `/home/user/v2rayN/v2rayN/v2rayN.Desktop/**` (Views + ViewModels + Common + Manager), with
supporting reads into `/home/user/v2rayN/v2rayN/ServiceLib/**` where the desktop UI calls into it.

Hunted for: event handlers never unsubscribed, timers/animations never stopped, UI mutation off the UI
thread, re-entrancy on button handlers, unreachable/stuck states, dispatcher deadlocks, unhandled
exceptions in `async void`, binding/lifetime leaks.

Every claim below is anchored to a line I read. Where I could not fully prove a runtime behaviour from
source alone (e.g. Avalonia container recycling on `ObservableCollection.Move`), I say so explicitly.

**Headline:** the motion/lifecycle discipline in this codebase is genuinely good — CTS tokens per animated
node, `MotionSuppressed` gates, safety timers that restore rest state, `Dispose`-on-detach for
subscriptions. The defects that remain cluster in four places: (1) no re-entrancy guards on navigation /
submit handlers, (2) `ReactiveCommand.Execute().Subscribe()` without an `onError`, (3) one-step-only
cleanup in cancelled crossfades, and (4) latch fields (`_detached`) that are set on detach but never
cleared on re-attach.

---

## Severity index

| # | Severity | Title | File |
|---|----------|-------|------|
| 1 | **critical** | Double-tap on the onboarding login CTA permanently strands a `LoginView` on screen after a successful login | `MainWindow.axaml.cs` / `LoginView.axaml.cs` |
| 2 | **high** | `Execute().Subscribe()` with no `onError` → unhandled exception on the UI thread | `MainWindow.axaml.cs`, `AccountView.axaml.cs`, `BuyViewModel.cs` |
| 3 | **high** | Cancelled tab crossfade strands the two-steps-back tab fully opaque under the current (transparent) tab | `MainWindow.axaml.cs` |
| 4 | **high** | Right-click / middle-click on the connect disc connects or disconnects the VPN | `ConnectHeroView.axaml.cs` |
| 5 | **medium** | `MainWindow_KeyDown` is `async void` with no `try`/`catch` around two awaited engine calls | `MainWindow.axaml.cs` |
| 6 | **medium** | `OnDeleteSubClick` / `OnPinClick`: `async void`, no re-entrancy guard, `await` outside the `try` | `SubscriptionMetaView.axaml.cs` |
| 7 | **medium** | `SubscriptionMetaView` unhooks its group on detach and never re-hooks on re-attach → dead chevron | `SubscriptionMetaView.axaml.cs` |
| 8 | **medium** | Every sub-page opener can be double-pushed (two identical pages on the back stack) | `MainWindow.axaml.cs`, `SettingsView.axaml.cs` |
| 9 | **medium** | `OnClosed` cancels only 2 of 7 animation tokens; `AnimateWindowSize` keeps writing `Position`/`Width`/`Height` at 60 Hz on a closing window | `MainWindow.axaml.cs` |
| 10 | **medium** | Static-event and static-delegate subscriptions never released → the closed `MainWindow` stays rooted and re-entrant | `MainWindow.axaml.cs` |
| 11 | **medium** | `LoginView.CrossfadeBlocks`: a superseded crossfade leaves the outgoing block visible | `LoginView.axaml.cs` |
| 12 | **medium** | `SettingsView.ToggleLocalProxy` — `async void`, no guard, overlapping animations, commit fired from the losing branch | `SettingsView.axaml.cs` |
| 13 | **medium** | `HomeViewModel.Disconnect()` fire-and-forget: a throw leaves the shield reading "Connected" with the core down | `HomeViewModel.cs` |
| 14 | **low** | `SchedulePostTopUpBalanceRefresh` catches only `OperationCanceledException` → unobserved task exceptions | `AccountViewModel.cs` |
| 15 | **low** | `ServerListView.RegisterInteractions` never re-registers after a VM swap; both layout copies keep handlers on the same shared interactions | `ServerListView.axaml.cs` |
| 16 | **low** | `AccountSyncView` subscriptions are disposed on detach with no re-subscribe path | `AccountSyncView.axaml.cs` |
| 17 | **low** | `MainWindow.OnClosing` is `async void` — `base.OnClosing(e)` runs after the shutdown await | `MainWindow.axaml.cs` |
| 18 | **low** | Command failures silently swallowed with no user-visible feedback | `LoginView.axaml.cs`, `SubscriptionMetaView.axaml.cs` |
| 19 | **low** | Dead pattern-match + never-unregistered wait handle | `MainWindow.axaml.cs` |

---

## 1. CRITICAL — double-tapping the onboarding login CTA strands a `LoginView` forever

**Files:** `Views/MainWindow.axaml.cs:1076-1081`, `:1192-1226`; `Views/LoginView.axaml.cs:110`, `:175-179`,
`:276-281`, `:919-927`; `Views/OnboardingView.axaml.cs:108-117`; `Views/MainWindow.axaml:611-615`.

`OnboardingView` wires the two login CTAs straight through, with no guard:

```csharp
// OnboardingView.axaml.cs:108-111
private void OnLoginTelegram(object? sender, RoutedEventArgs e)
{
    (TopLevel.GetTopLevel(this) as MainWindow)?.OpenLoginTelegram();
}
```

`MainWindow.OpenLoginTelegram()` (`:1213-1217`) calls `OpenLogin()` unconditionally, and `OpenLogin()`
(`:1192-1208`) unconditionally constructs a new `LoginView` and calls `PushSubPage(view)`
(`:1076-1081`), which does `subPageHost.Content = view`. `subPageHost` is a `ContentControl`
(`MainWindow.axaml:611`), so assigning `Content` **removes the previous `LoginView` from the visual tree**.

`LoginView` reacts to that detach by setting a latch that is never cleared:

```csharp
// LoginView.axaml.cs:175-179
DetachedFromVisualTree += (_, _) =>
{
    _detached = true;
    Unbind();
};
```

and the success hand-off refuses to fire on a view that has ever been detached:

```csharp
// LoginView.axaml.cs:919-927
private void TryHandoff()
{
    if (_handoffFired || _detached || !_loggedIn || !_beatDone)
    {
        return;   // ... never fire on a dead view
    }
    _handoffFired = true;
    BackRequested?.Invoke(this, EventArgs.Empty);
}
```

Note the asymmetry: `AttachedToVisualTree += (_, _) => Rebind();` (`:174`) **does** rebuild the VM
subscriptions on re-attach, but nothing resets `_detached`.

**Failure scenario (fully traced):**

1. User double-clicks «Войти через Telegram» on the onboarding screen. `Button.Click` fires twice — Avalonia
   has no debounce.
2. Click 1: `LoginView#1` pushed. `_subStack = [LV1]`.
3. Click 2: `LoginView#2` pushed. `subPageHost.Content = LV2` detaches LV1 → `LV1._detached = true`,
   `LV1.Unbind()`. `_subStack = [LV1, LV2]`.
4. User confirms in Telegram. `IsLoggedIn` flips true. LV2's live subscription (`LoginView.axaml.cs:276-281`)
   → `OnLoggedIn()` → success beat → `TryHandoff()` → `BackRequested` → `MainWindow.OpenLogin`'s handler
   (`:1195-1206`) → `PopSubPage()`.
5. `PopSubPage` (`:1086-1094`) pops LV2, `next = LV1`, `ApplySubPageResult` sets `subPageHost.Content = LV1`
   → LV1 **re-attaches** → `Rebind()` re-subscribes → `WhenAnyValue(x => x.IsLoggedIn)` replays the current
   value `true` → `OnLoggedIn()` → beat plays → `finally { _beatDone = true; TryHandoff(); }` (`:953-957`).
6. `TryHandoff` returns immediately because `_detached` is still `true` from step 3.

**Result:** the user is left staring at a login page showing a success check-mark, already signed in, with
no automatic dismissal. The only escape is the toolbar back arrow (`BackButton`, `:124-128`), which is a
different affordance than the one that normally closes the page.

The same double-push exists for `OpenLoginSite()` (`:1222-1226`), which additionally launches the browser
handoff twice.

**Fix direction:** (a) reset `_detached = false` in the `AttachedToVisualTree` handler next to the existing
`Rebind()`; (b) make `OpenLogin()` idempotent — if `_subStack.LastOrDefault() is LoginView`, reuse it
instead of pushing another (the pattern `HandleAuthCallback` at `:1065-1068` already uses).

---

## 2. HIGH — `Execute().Subscribe()` with no `onError` is an unhandled UI-thread exception

**Files:** `Views/MainWindow.axaml.cs:1216`, `:1225`; `Views/AccountView.axaml.cs:66`;
`ViewModels/BuyViewModel.cs:642`.

```csharp
// MainWindow.axaml.cs:1216
_accountVm.LoginTelegramCmd.Execute().Subscribe();
// MainWindow.axaml.cs:1225
_accountVm.LoginBrowserCmd.Execute().Subscribe();
// AccountView.axaml.cs:66
LogoutRow.Tapped += (_, _) => (DataContext as AccountViewModel)?.LogoutCmd.Execute().Subscribe();
// BuyViewModel.cs:642
accountVm?.RetryCmd.Execute().Subscribe();
```

`ReactiveCommand.Execute()` pushes a faulting execution to **both** `ThrownExceptions` *and* the returned
observable's `OnError`. `AccountViewModel` merges every command's `ThrownExceptions` (`:359-395`), which
handles the first channel — but `Subscribe()` with no `onError` delegate installs Rx's default
error stub, which rethrows. Because the command's output scheduler is the main-thread scheduler, the
rethrow lands inside a dispatcher work item → process-level unhandled exception (only *logged* by
`App.CurrentDomain_UnhandledException`, `App.axaml.cs:297-303`).

This is not theoretical: `AuthManager.BeginTelegramLogin` (`Account/AuthManager.cs:71-140`) only catches
`ApiError`. `DepartamentApiClient` maps `TaskCanceledException`/`HttpRequestException`/JSON faults to
`ApiError` (`Account/DepartamentApiClient.cs:404-467`), but anything outside that mapped set — e.g. a
`UriFormatException` or `ObjectDisposedException` from the handler, or a throw inside
`ProcUtils.ProcessStart(awaiting.DeepLink)` called from `ApplyLoginState` (`AccountViewModel.cs:1039`) —
escapes uncaught.

The rest of the codebase already does this correctly, which makes these four sites clearly accidental:

```csharp
// LoginView.axaml.cs:1357-1360  — correct
private static void Execute(ReactiveCommand<Unit, Unit>? command)
    => command?.Execute().Subscribe(_ => { }, _ => { });

// ServerListView.axaml.cs:793 — correct
profiles.CopyServerCmd.Execute().Subscribe(static _ => { }, static _ => { });
```

**Failure scenario:** offline machine, user taps «Войти через Telegram» on the onboarding screen; a
non-`ApiError` fault in the token round-trip terminates the app instead of showing the error line.

---

## 3. HIGH — a cancelled tab crossfade leaves the previous-previous tab fully opaque

**File:** `Views/MainWindow.axaml.cs:388-460`. Corroborating: `Views/MainWindow.axaml:557`
(`contentHost` is `Background="Transparent"`), `Views/HomeView.axaml:16` (Home root is an **opaque**
`Brush.HomeGradient` border), `Views/SettingsView.axaml:216-218` and `Views/AccountView.axaml:242-243`
(Settings/Account roots are **transparent**).

All four tabs are permanent keep-alive children of `contentHost` (`:214-219`), and visibility is driven by
`Opacity`. `SwapContent` cancels the in-flight animation and hands `(target, previous)` to
`AnimateContentSwap`, which bails on cancellation *before* the cleanup block:

```csharp
// MainWindow.axaml.cs:428-431
_contentAnim?.Cancel();
var cts = new CancellationTokenSource();
_contentAnim = cts;
AnimateContentSwap(target, previous, direction, cts.Token);

// MainWindow.axaml.cs:446-459
try { await Task.WhenAll(enter, exit); }
catch { }
if (ct.IsCancellationRequested)
{
    return;                       // <-- previous.Opacity is NEVER zeroed
}
target.Opacity = 1d;
target.RenderTransform = null;
if (previous != _currentContentView)
{
    previous.Opacity = 0d;
    previous.RenderTransform = null;
}
```

Avalonia reverts an animated property to its *base* value when the animation is cancelled. The outgoing
tab's base `Opacity` is still `1` (nothing ever wrote `0` locally — only the animation was driving it),
so cancellation restores it to fully visible. The `foreach` at `:407-410` that runs on the next swap only
resets `IsHitTestVisible`, never `Opacity`:

```csharp
// MainWindow.axaml.cs:407-411
foreach (var v in new Control[] { _homeView, _compactHome, _settingsView, _accountView })
{
    v.IsHitTestVisible = ReferenceEquals(v, target);
}
```

**Failure scenario:** click «Настройки» then «Аккаунт» within ~220 ms (the enter animation's
`Motion.Dur.State`).
- Swap 1 starts: Home fades 1→0 (150 ms), Settings fades 0→1 (220 ms), `ZIndex` Home < Settings.
- Swap 2 cancels swap 1. Home's fade reverts → **Home stays at `Opacity = 1`**. Settings is correctly
  zeroed by swap 2's completion (`previous != _currentContentView`). Account fades in on top.
- Final frame: `AccountView` (transparent root) rendered over a fully visible `HomeView` (opaque gradient
  + server list + connect shield). The account content is legible but the whole page has the Home screen
  painted behind it, not the shell's own gradient.

The same one-step-only cleanup exists on the instant path (`:415-426`), so an instant swap during an
in-flight animation strands the same way.

**Fix direction:** in the keep-alive `foreach`, also set `v.Opacity = 0d; v.RenderTransform = null;` for
every view that is neither `target` nor `previous`.

---

## 4. HIGH — right-click / middle-click on the connect disc toggles the VPN

**File:** `Views/ConnectHeroView.axaml.cs:688-716`.

```csharp
private void OnDiscPointerPressed(object? sender, PointerPressedEventArgs e)
{
    _pressing = true;                    // <-- no button check
    if (ReducedMotion) { return; }
    ...
}

private void OnDiscPointerReleased(object? sender, PointerReleasedEventArgs e)
{
    ReleaseDiscScale();
    if (_pressing)
    {
        _pressing = false;
        ConnectToggleRequested?.Invoke(this, EventArgs.Empty);   // <-- fires for any button
    }
}
```

`ConnectToggleRequested` is wired to `vm.ConnectToggle()` (`Views/HomeHeroPresenter.cs:60`, `:65`), which
starts or stops the tunnel (`ViewModels/HomeViewModel.cs:166-176`).

The equivalent code for server rows gets it right, which shows the intent:

```csharp
// ServerListView.axaml.cs:162-166
if (e.GetCurrentPoint(b).Properties.IsLeftButtonPressed && b.DataContext is ProfileItemModel item)
{
    _rowPressing = true;
    _rowPressTarget = item;
}
```

**Failure scenario:** a user right-clicks the shield (a reflex on desktop, e.g. expecting a context menu)
and the VPN disconnects. Middle-click and any pen/touch barrel button do the same. Note that
`ReducedMotion` short-circuits the visual press feedback (`:691-694`) but *not* `_pressing`, so under lite
mode the toggle fires with no press feedback at all.

**Fix direction:** gate `_pressing = true` on `e.GetCurrentPoint(ConnectDisc).Properties.IsLeftButtonPressed`,
and on release additionally require `e.InitialPressMouseButton == MouseButton.Left` (mirroring
`ServerListView.OnRowPointerReleased`, `:184-191`).

---

## 5. MEDIUM — `MainWindow_KeyDown` is `async void` with two unguarded awaits

**File:** `Views/MainWindow.axaml.cs:1875-1951`.

```csharp
private async void MainWindow_KeyDown(object? sender, KeyEventArgs e)
{
    ...
    case Key.V:
        await AddServerViaClipboardAsync();   // :1912
        break;
    case Key.S:
        await ScanScreenTaskAsync();          // :1916
        break;
```

`AddServerViaClipboardAsync` (`:1929-1936`) awaits `ViewModel.AddServerViaClipboardAsync(clipboardData)`;
`ScanScreenTaskAsync` (`:1938-1951`) awaits `ViewModel.ScanScreenResult(bytes)`. Neither the handler nor
the helpers have a `try`/`catch`, so any fault in the engine's import path becomes an unhandled exception
on the UI thread rather than an inline message. (`AvaUtils.GetClipboardData` and
`QRCodeAvaloniaUtils.CaptureScreen` are themselves exception-safe — `Common/AvaUtils.cs:7-23`,
`Common/QRCodeAvaloniaUtils.cs:8-24` — so the engine calls are the exposed surface.)

`ScanScreenTaskAsync` has a second problem: it calls `ShowHideWindow(false)` at `:1940` and restores at
`:1950`. If the awaited work throws, **the window stays hidden** and the user has to find the tray icon.

---

## 6. MEDIUM — subscription pin/delete: `async void`, no re-entrancy guard, `await` outside `try`

**File:** `Views/SubscriptionMetaView.axaml.cs:583-609`, `:631-659`.

`OnRefreshClick` gets this right — it has a `_refreshing` latch (`:526-539`). `OnPinClick` and
`OnDeleteSubClick` do not.

```csharp
// :583-609 — pin
private async void OnPinClick(object? sender, RoutedEventArgs e)
{
    var subId = _currentSubId;
    ...
    var sub = await AppManager.Instance.GetSubItem(subId);   // read
    sub.Pinned = !sub.Pinned;                                // modify
    await SQLiteHelper.Instance.UpdateAsync(sub);            // write
```

Two fast clicks interleave as read/read → modify/modify → write/write. Both reads observe
`Pinned == false`, both write `true`: the second click is silently lost, and the optimistic tint at
`:600-603` is applied twice for the same value. (The group's `Pinned` ordering in
`HomeViewModel.BuildGroupPlan:659-666` then disagrees with the number of clicks the user made.)

```csharp
// :631-643 — delete
private async void OnDeleteSubClick(object? sender, RoutedEventArgs e)
{
    var subId = _currentSubId;
    if (subId.IsNullOrEmpty()) { return; }

    if (await UI.ShowYesNo(L.T("Sub_DeleteConfirm")) != ButtonResult.Yes)   // <-- OUTSIDE the try
    {
        return;
    }
    try { ... }
```

`UI.ShowYesNo` (`Common/UI.cs:11-18`) resolves an owner via `WindowDialog.TryGetOwnerWindow()`
(`Manager/WindowDialog.cs:31-62`), which **throws `InvalidOperationException`** when there are no visible
windows and no `MainWindow` (`:41`) — reachable while the app is hidden to tray. It then calls
`box.ShowDialog<ButtonResult>(owner)`. Any throw here escapes an `async void` handler with no catch.

**Fix direction:** add a `_pinning` / `_deleting` latch matching `_refreshing`, and move the confirm
`await` inside the `try`.

---

## 7. MEDIUM — `SubscriptionMetaView` unhooks its group on detach and never re-hooks

**File:** `Views/SubscriptionMetaView.axaml.cs:91-97`, `:102-124`, `:239-296`.

The group's `PropertyChanged` hook is established **only** from `Rebind()`, which runs from the constructor
and from `DataContextChanged`:

```csharp
// :91-96
DataContextChanged += (_, _) => Rebind();
AttachedToVisualTree += OnMetaAttached;
DetachedFromVisualTree += OnMetaDetached;
Rebind();
```

but it is torn down from the *detach* handler:

```csharp
// :116-124
private void OnMetaDetached(object? sender, VisualTreeAttachmentEventArgs e)
{
    MotionState.Changed -= OnMotionStateChanged;
    L.Instance.LanguageChanged -= OnLanguageChanged;
    ActualThemeVariantChanged -= OnThemeVariantChanged;
    _boundsSub?.Dispose();
    _boundsSub = null;
    Unhook();                        // <-- removes _group.PropertyChanged
}
```

`OnMetaAttached` (`:102-114`) restores everything *except* the group hook, and `_group` is left non-null by
`Unhook()` (`:239-245`), so a re-attach with an unchanged `DataContext` raises no `DataContextChanged` and
`Rebind()` never runs.

Consequence: after such a cycle, `OnGroupPropertyChanged` (`:282-288`) no longer fires, so `SyncCollapsed()`
(`:292-296`) never runs — the collapse chevron freezes at whatever angle it had, while the rows below still
expand/collapse (their hook lives in `ServerListView` and *is* re-established on `Loaded`,
`ServerListView.axaml.cs:515-545`).

The sibling code in `ServerListView` explicitly documents that these containers do get re-loaded
("Already hooked to the SAME group (Loaded can re-fire)", `:522`), which is what makes this asymmetry a
live risk rather than a theoretical one. The concrete trigger would be `ServerGroups.Move(...)`
(`HomeViewModel.cs:711`) on a pin toggle or re-ordering — **I could not prove from source whether Avalonia's
non-virtualizing `ItemsControl` moves the existing container or rebuilds it**, so treat this as a latent
defect with a known trigger rather than a confirmed repro.

**Fix direction:** call `Rebind()` (or at minimum re-hook `_group.PropertyChanged`) from `OnMetaAttached`,
and null `_group` in `Unhook()` so the state is unambiguous.

---

## 8. MEDIUM — every sub-page opener can be double-pushed

**Files:** `Views/MainWindow.axaml.cs:1165-1239`; `Views/SettingsView.axaml.cs:43-50`, `:107-120`,
`:314-326`; `Views/AccountView.axaml.cs:60-61`.

None of `OpenBuy` / `OpenDevices` / `OpenHistory` / `OpenLogin` / `OpenSubPage` checks whether an identical
page is already on top of `_subStack`:

```csharp
// MainWindow.axaml.cs:1174-1179
public void OpenDevices()
{
    var view = new DevicesView();
    view.BackRequested += (_, _) => PopSubPage();
    PushSubPage(view);
}
```

and `SettingsView` rows fire on every `Tapped`:

```csharp
// SettingsView.axaml.cs:107-120
private void WireRow(Border row, Action activate)
{
    row.Focusable = true;
    row.IsTabStop = true;
    row.Tapped += (_, _) => activate();
    ...
}
// :43-50 — e.g.
WireRow(RowDns, () => OpenPage(new DnsSubView(), refresh: true));
```

**Failure scenario:** a double-click on «DNS» (or «Устройства», «История платежей», …) pushes two
`DnsSubView` instances; the user must press back twice to leave, and each instance ran its own
constructor-time data load. For `DevicesView` / `PaymentHistoryView` / `BuyView` that means duplicate
network fetches on every double-click. `HandleAuthCallback` (`:1065-1068`) already shows the guard shape
this needs.

---

## 9. MEDIUM — `OnClosed` cancels 2 of 7 animation tokens

**File:** `Views/MainWindow.axaml.cs:1862-1873`, with the token fields at `:91-96` and `:1599`.

```csharp
protected override void OnClosed(EventArgs e)
{
    _windowInteractions.Dispose();
    CancelThemeTransition();
    _indicatorAnim?.Cancel();
    base.OnClosed(e);
}
```

`_subPageAnim`, `_shellAnim`, `_layoutAnim`, `_resizeAnim` and `_contentAnim` are left running. Most of
those are harmless `Opacity` tweens on detached visuals, but `_resizeAnim` drives a hand-rolled 60 Hz loop
that writes **window geometry**:

```csharp
// MainWindow.axaml.cs:1344-1356
while (true)
{
    var t = Math.Min(1d, (Environment.TickCount64 - startTicks) / durationMs);
    ...
    ApplySizeCentered(w, h, centerX, centerY, screen, scaling);   // sets Position, Width, Height
    if (t >= 1d) { break; }
    await Task.Delay(16, cts.Token);
}
```

`ApplySizeCentered` (`:1438-1458`) assigns `Position`, `Width` and `Height` on the window. If the user
double-clicks the nav chrome to toggle the layout and then quits within the 200 ms window, this loop keeps
poking a closing/closed `Window`. The `catch { }` at `:1359` swallows whatever it produces, so the symptom
would be a silent geometry write or a swallowed platform exception rather than a crash — but the token
discipline is inconsistent with the file's own stated rule ("та же CTS-дисциплина, что у остальных узлов",
`:1869-1871`).

---

## 10. MEDIUM — static-event / static-delegate subscriptions are never released

**File:** `Views/MainWindow.axaml.cs:125`, `:142`, `:147`; `Common/MotionState.cs:25`,
`Common/UiScaleState.cs:41`; `App.axaml.cs:494`.

```csharp
// MainWindow ctor
UiScaleState.Changed += OnUiScaleChanged;      // :125   — static event
MotionState.Changed += OnMotionStateChanged;   // :142   — static event
App.ThemeTransitionHook = RunThemeTransition;  // :147   — static delegate to an instance method
```

None is removed in `OnClosed` (`:1862-1873`). Because `MotionState`/`UiScaleState`/`App` are static, the
closed `MainWindow` remains reachable for the process lifetime, and a later
`App.ApplyTheme(...)` → `hook(Swap)` (`App.axaml.cs:530-534`) re-enters `RunThemeTransition` on a dead
window. The escape hatch at `MainWindow.axaml.cs:1623` (`!IsVisible` → `applySwap()` immediately) means
this degrades safely today, but it is defence-by-accident: `RunThemeTransition` still touches
`chromeRoot.Bounds` before that check (`:1621-1622`).

Same shape, one level down: `ThreadPool.RegisterWaitForSingleObject(...)` at `:334` returns a
`RegisteredWaitHandle` that is discarded and never unregistered.

This is a *latent* leak — the desktop app has exactly one window and normal close routes to tray via
`OnClosing` (`:1846-1849`) — but it is the kind that turns into a real bug the moment a second window or a
restart-in-process path appears.

---

## 11. MEDIUM — `LoginView.CrossfadeBlocks` leaves the outgoing block visible when superseded

**File:** `Views/LoginView.axaml.cs:492-529`.

```csharp
_blockCts?.Cancel();
var cts = new CancellationTokenSource();
_blockCts = cts;

incoming.Opacity = 0;
incoming.RenderTransform = _scale098;
incoming.IsVisible = true;
outgoing.IsVisible = true;
...
if (cts.IsCancellationRequested)
{
    return; // новее переход владеет финальным состоянием
}
outgoing.IsVisible = false;
```

The comment's premise — "a newer transition owns the final state" — only holds when the newer transition's
`outgoing` is the same control. It is not, whenever three distinct blocks are traversed in under 220 ms
(`Motion.Dur.State`). `Method → Awaiting → EmailPending` leaves `MethodBlock` at `IsVisible = true` and, on
cancellation, `Opacity` reverted to its base `1` — two overlapping blocks in the content column.

Same class of bug as finding #3, same fix shape: on entry, hide *all* blocks except `incoming` and
`outgoing` (the pattern `MainWindow.CrossfadeShellTo` already uses at `:876-883` and which this code is
missing).

---

## 12. MEDIUM — `SettingsView.ToggleLocalProxy` re-entrancy

**File:** `Views/SettingsView.axaml.cs:217-233`, `:263-305`.

```csharp
private async void ToggleLocalProxy()
{
    var open = !LocalProxyPanel.IsVisible;
    SetProxyChevron(open);
    if (open)
    {
        LocalProxyPanel.IsVisible = true;
        await RevealPanel(LocalProxyPanel, show: true);     // 300 ms
    }
    else
    {
        await RevealPanel(LocalProxyPanel, show: false);    // 150 ms
        LocalProxyPanel.IsVisible = false;
        _ = Vm?.CommitLocalProxyAsync();
    }
}
```

The row is wired with `WireRow(RowLocalProxy, ToggleLocalProxy)` (`:61`) — plain `Tapped`, no guard, and
`RevealPanel` (`:263-305`) has **no cancellation token**. A double-tap runs the open and close animations
concurrently on the same `Opacity`/`TranslateTransform.Y`; the open branch's trailing
`panel.Opacity = 1d; panel.RenderTransform = null;` (`:300-304`) can land *after* the close branch already
hid the panel. `SetProxyChevron` (`:238-259`) is likewise uncancelled, so the chevron can settle at the
wrong angle relative to `LocalProxyPanel.IsVisible` — a state/visual desync in a settings row.

Secondary: `CommitLocalProxyAsync()` is dispatched from whichever branch happens to be the "close" one,
so a rapid open/close/open sequence may write the proxy port/user/password mid-edit or not at all.

---

## 13. MEDIUM — `Disconnect()` fire-and-forget leaves a false "Connected" shield

**File:** `ViewModels/HomeViewModel.cs:166-176`, `:209-225`.

```csharp
public void ConnectToggle()
{
    if (IsConnected) { _ = Disconnect(); }
    else             { _ = Connect(); }
}

private async Task Disconnect()
{
    IsConnecting = false;
    ...
    await CoreManager.Instance.CoreStop(byUser: true);
    await SysProxyHandler.UpdateSysProxy(_config, true);   // <-- can throw (registry / netsh / perms)
    _connectedSince = null;
    SyncState();                                           // <-- skipped on throw
}
```

No `try`/`catch`, discarded task. If `UpdateSysProxy` throws (Windows registry write, `netsh` failure,
insufficient rights), `SyncState()` never runs, so `IsConnected` stays `true` while the core is already
stopped. `HomeHeroPresenter.ApplyConnectState` (`Views/HomeHeroPresenter.cs:126-151`) reads `IsConnected`
first, so the shield keeps showing "Подключено" over a dead tunnel. The uptime tick would eventually
re-`SyncState` — but `UpdateStateTick` (`HomeViewModel.cs:408-423`) stops the timer once
`!IsCoreRunning() && !IsConnecting`, which is exactly the state reached here, so **there is no self-heal**
until the next user action or a core event.

`Connect()` (`:178-207`) is better protected — `executed && !IsCoreRunning()` and the 12 s deadline in
`BeginConnecting` (`:289-299`) both surface failure — but it too has no `try`/`catch` around
`await _main.Reload()`.

---

## 14. LOW — unobserved task exceptions in the post-top-up poll

**File:** `ViewModels/AccountViewModel.cs:1353-1379`.

```csharp
_ = Task.Run(async () =>
{
    try
    {
        for (var attempt = 0; attempt < 12 && !cts.IsCancellationRequested; attempt++)
        {
            await Task.Delay(TimeSpan.FromSeconds(5), cts.Token);
            await RefreshProfile();
        }
    }
    catch (OperationCanceledException) { ... }
});
```

Only `OperationCanceledException` is caught. `RefreshProfile()` (`:517-535`) awaits `_repo.RefreshProfile()`;
anything it throws that is not mapped to a returned `ApiResult` faults the discarded task
(`TaskScheduler.UnobservedTaskException` logs it, `App.axaml.cs:305-308`, and the poll silently stops —
the balance never refreshes and the user gets no signal). The `CancellationTokenSource` at `:1356` is also
never disposed (same for `_telegramCts`, `_registerCts`, `_renewPollCts`, `_linkPollCts`,
`_cardActionPollCts` — `:43-66`), each `Cancel()`-then-replace leaking the previous instance.

---

## 15. LOW — `ServerListView` interaction handlers: no re-registration, duplicate registration across layouts

**File:** `Views/ServerListView.axaml.cs:38`, `:44-68`, `:73-132`.

```csharp
private void RegisterInteractions()
{
    if (_interactionHandlers.Count > 0)
    {
        return;                                   // <-- early-out ignores a VM identity change
    }
    if (DataContext is not HomeViewModel { Profiles: { } profiles }) { return; }
    ...
}
```

Two consequences:

1. **Stale handlers on a VM swap.** `DataContextChanged` calls `RegisterInteractions()` (`:51`) but the
   early-out means handlers registered against an older `ProfilesViewModel` are never replaced. Today the
   engine has a single `ProfilesViewModel` instance (`HomeViewModel.cs:111`), so this is dormant.
2. **Both layout copies stay registered.** `_homeView` and `_compactHome` are both permanent children of
   `contentHost` (`MainWindow.axaml.cs:214-219`), so neither `ServerListView` ever detaches and
   `OnDetachedFromVisualTree` (`:79-87`) never runs. After the first 760 px layout swap, both copies hold
   registered handlers on the same shared `ShowYesNoInteraction` / `ShareServerInteraction` /
   `SetClipboardDataInteraction`. ReactiveUI invokes handlers newest-first and stops once output is set, so
   behaviour is correct — but the registration count grows to two and the older handler closes over the
   now-inactive view (`AvaUtils.SetClipboardData(this, …)`, `:129`).

Also in this file: `_revealHooks` and `_revealGen` (`:491`, `:494`) are cleaned per-container on `Unloaded`
(`:547-561`) but never bulk-cleared in `OnDetachedFromVisualTree` (`:79-87`), which only disposes
`_interactionHandlers`.

---

## 16. LOW — `AccountSyncView` subscriptions are one-shot

**File:** `Views/AccountSyncView.axaml.cs:31`, `:60-76`.

```csharp
private readonly CompositeDisposable _subs = new();
...
DetachedFromVisualTree += (_, _) => _subs.Dispose();
```

A `CompositeDisposable` that has been disposed rejects further additions, and there is no re-subscribe on
attach. Since `accountSyncView` is a permanent child of the MainWindow shell (`MainWindow.axaml:604`) this
never fires in practice — but it is the same latch-shaped hazard as finding #1 and #7, and it is worth
normalising across the three views that use the pattern (`AccountSyncView`, `LoginView`,
`SubscriptionMetaView`) since `HomeAccountChip` (`:44-61`) and `ConnectHeroView` (`:495-541`) already do
attach/detach symmetrically and correctly.

---

## 17. LOW — `MainWindow.OnClosing` is `async void`

**File:** `Views/MainWindow.axaml.cs:1835-1857`.

```csharp
protected override async void OnClosing(WindowClosingEventArgs e)
{
    ...
    case WindowCloseReason.ApplicationShutdown or WindowCloseReason.OSShutdown:
        await AppManager.Instance.AppExitAsync(false);
        break;
    }
    base.OnClosing(e);
}
```

`e.Cancel = true` in the tray branch (`:1847`) is set synchronously before any await, so that path is
correct. The shutdown branch, however, returns to the framework at the `await` — `base.OnClosing(e)` (which
raises the public `Closing` event) then runs *after* the window has already been allowed to close, and
`AppExitAsync` is not actually awaited by the shutdown sequence. An exception from `AppExitAsync` is
unhandled `async void`.

---

## 18. LOW — command failures swallowed with no user-visible feedback

**Files:** `Views/LoginView.axaml.cs:1357-1360`; `Views/SubscriptionMetaView.axaml.cs:523`.

```csharp
// LoginView
command?.Execute().Subscribe(_ => { }, _ => { });
// SubscriptionMetaView — «пинг»
Profiles?.FastRealPingCmd.Execute().Subscribe(_ => { }, _ => { });
```

These are the *safe* form (finding #2 is about the unsafe form), but they discard the error entirely. For
the login commands the VM's own `ThrownExceptions` merge (`AccountViewModel.cs:359-395`) reports into
`ErrorText`, so the user does see something. For `FastRealPingCmd` — a `ProfilesViewModel` command with no
such merge visible from this call site — a failed speed test produces no message and no state change: the
row spinners simply never resolve. This is precisely the failure mode `MainWindow.DelegateSnackMsg`
(`:1765-1769`) was written to eliminate for the add-subscription path.

---

## 19. LOW — dead pattern-match and an unregistered wait handle

**File:** `Views/MainWindow.axaml.cs:1953-1965`, `:334`.

```csharp
private void Shutdown(bool obj)
{
    if (obj is bool b && _blCloseByUser == false)   // `obj` is already bool — `is bool` is always true
    {
        _blCloseByUser = b;
    }
```

and

```csharp
ThreadPool.RegisterWaitForSingleObject(Program.ProgramStarted, OnProgramStarted, null, -1, false);
```

whose returned `RegisteredWaitHandle` is discarded, so the wait is never unregistered.

---

## Things I checked and found correct (so they don't get "fixed" into bugs)

- **Off-UI-thread mutation.** I found no UI mutation off the dispatcher. Every cross-thread path funnels
  through `AccountViewModel.RunOnUi` (`:2572-2582`), `MainWindow.OnMotionStateChanged`/`OnUiScaleChanged`
  (`:792-802`, `:1499-1509`), `LoginView.RunOnUiLang` (`:296-306`), `AccountView.OnBalanceChanged`
  (`:216-220`), `HomeAccountChip._handler` (`:48`), `BottomNavBar._handler` (`:69`), or
  `.ObserveOn(RxSchedulers.MainThreadScheduler)`. `ProfilesViewModel.SetSpeedTestResult` is explicitly
  scheduled onto the main thread at its call site (`ServiceLib/ViewModels/ProfilesViewModel.cs:795-801`),
  which is what makes `HomeViewModel.OnSourceItemChanged` (`:563-591`) safe.
- **No dispatcher deadlocks.** Every cross-thread hop uses `Post`/`InvokeAsync` fire-and-forget; I found no
  `Dispatcher.UIThread.Invoke(...)` blocking call and no `.Result`/`.Wait()` on the UI thread in this project.
- **Timers.** `HomeViewModel._uptimeTimer` is a transient tick that stops itself (`:408-441`) and is torn
  down in `Dispose` (`:750-766`). `AccountView.SnapOffsetTo` (`:474-508`) stops its timer on completion and
  on token cancellation, and `OnPropertyChanged` cancels the token when the tab goes inactive (`:170-176`).
  `DispatcherTimer.RunOnce` safety timers in `ServerListView.PlayRowReveal` (`:365-387`),
  `LoginView.PlayReveal` (`:1245-1267`) and `OnboardingView.PlayReveal` (`:173-195`) are disposed in
  `finally` and always restore the rest state.
- **Off-screen animation loops.** `ConnectHeroView` gates every infinite loop behind
  `MotionSuppressed = _animationsPaused || _deactivated` (`:99-112`) and strips them on window hide
  (`:547-575`) and on layout deactivation (`:585-606`); `MainWindow.IsWindowLive()` (`:381`) guards tab and
  rail-indicator motion; `BottomNavBar.IsWindowLive()` (`:185-186`) does the same. This is done thoroughly.
- **`DelayInkConverter`.** I suspected a dead branch (`value is int ms && ms <= 0` against a string binding)
  but the XAML binds `Delay` (`ServerListView.axaml:256`), which is `[Reactive] public int Delay`
  (`ServiceLib/Models/Dto/ProfileItemModel.cs:26-27`) and *is* updated by
  `SetSpeedTestResult` (`ServiceLib/ViewModels/ProfilesViewModel.cs:297-301`). Not a bug.
- **Rail indicator geometry.** `RailSlotY` assumes 64 px slots (`MainWindow.axaml.cs:529`) and
  `Button.NavRailItem` is `Height = 64` (`Assets/GlobalStyles.axaml:749-753`). Consistent.
- **`_railIndicatorSeeded` / `_indicatorSeeded`.** Both are folded into the `instant` predicate
  (`MainWindow.axaml.cs:546`, `BottomNavBar.axaml.cs:145`) so the first call always snaps and sets the flag —
  no unreachable "never seeded" state.
- **Connect re-entrancy.** `MainWindowViewModel.Reload()` is guarded by a
  `SemaphoreSlim(1,1)` with a `_hasNextReloadJob` follow-up (`ServiceLib/ViewModels/MainWindowViewModel.cs:802-878`)
  and returns `false` when it deferred, which `HomeViewModel.Connect()` correctly uses to avoid painting a
  false failure (`:188-205`). Double-tapping the shield to *connect* is handled; see finding #13 for the
  disconnect side.

---

## Recommended fix order

1. **#1** and **#8** together — one `OpenLogin`/`PushSubPage` idempotency guard plus resetting `_detached`
   on attach closes the stuck-login bug and the duplicate-page bug at once.
2. **#2** — mechanical: give the four `Subscribe()` calls an `onError` (copy `LoginView.Execute`).
3. **#3** and **#11** — one-line-ish: zero every non-participating surface at crossfade entry.
4. **#4** — left-button gate on the connect disc.
5. **#6**, **#12**, **#13** — re-entrancy latches + `try`/`catch` around the awaits.
6. **#5**, **#9**, **#10**, **#17** — lifecycle hygiene; low user-visible impact, high "stops the next bug"
   value.
