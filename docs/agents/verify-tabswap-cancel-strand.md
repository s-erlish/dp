# Verify: "Cancelled tab crossfade strands the two-steps-back tab fully opaque"

**Target:** `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs` (2053 lines)
**Verdict:** **REAL defect — but the reporter's mechanism is wrong, and the severity is overstated.**
The stranded tab is frozen at its *mid-flight interpolated* opacity, not restored to `1`.

---

## 1. What the code actually does (verified)

### The swap pipeline

`ShowTab` → `SwapContent` → `AnimateContentSwap`.

- All four tab surfaces are permanent children of `contentHost`, seeded to `Opacity = 0`:
  `MainWindow.axaml.cs:230-235` — `foreach (var v in new Control[] { _homeView, _compactHome, _settingsView, _accountView }) { v.Opacity = 0d; v.IsHitTestVisible = false; contentHost.Children.Add(v); }`
  (Reporter cited `:214-219`; the actual lines are `230-235`. All of the reporter's `.cs` citations are off by ~16 lines; all XAML citations are exact.)
- `SwapContent` cancels the in-flight swap and starts a new one: `MainWindow.axaml.cs:444-447`.
- The keep-alive `foreach` on each swap touches **only** `IsHitTestVisible`, never `Opacity`:
  `MainWindow.axaml.cs:423-426`. **Confirmed** — this part of the report is accurate.
- `AnimateContentSwap` bails before the cleanup block when cancelled:
  `MainWindow.axaml.cs:464-467` (`if (ct.IsCancellationRequested) { return; }`), skipping
  `MainWindow.axaml.cs:468-475` which is the only place `previous.Opacity = 0d` is written on the
  animated path. **Confirmed.**
- Instant path: `MainWindow.axaml.cs:431-442` cancels `_contentAnim` and zeroes only the *current*
  `previous`, never a tab stranded by the cancellation it just performed. **Confirmed.**

### Durations / easing

`Common/Motion.cs:34` `State = 220ms`, `Common/Motion.cs:40` `Exit = 150ms`,
`Common/Motion.cs:71` `Standard = new SplineEasing(0.2, 0, 0, 1)`.
The outgoing tab's fade is `Motion.Dur.Exit` = **150 ms** (`MainWindow.axaml.cs:459`), not 220 ms —
so the vulnerable window is the first ~150 ms of a swap, not ~220 ms as the report says.

### Z-order and opacity of the surfaces

- `target.ZIndex = ++_contentZ` on every swap (`MainWindow.axaml.cs:416`) — each newer tab paints
  over every older one, so a stranded older tab sits *under* the current tab. **Confirmed.**
- `contentHost` is `Background="Transparent"` — `MainWindow.axaml:557`. **Confirmed.**
- `HomeView` root is an opaque `Border Background="{DynamicResource Brush.HomeGradient}"` —
  `HomeView.axaml:16`. **Confirmed.**
- `SettingsView` root is `ScrollViewer Background="Transparent"` — `SettingsView.axaml:216-218`.
  **Confirmed.**
- `AccountView` root is a bare `Panel` (`AccountView.axaml:239`) whose content `ScrollViewer` is
  `Background="Transparent"` (`AccountView.axaml:242-243`). **Confirmed.**

So a non-zero-opacity Home *does* show through Settings/Account.

---

## 2. Where the report is WRONG: Avalonia does not revert on cancel

The claim's load-bearing premise —

> "Avalonia reverts a cancelled animation's property to its base value, and the outgoing tab's base
> Opacity is still 1"

— is **false** for the Avalonia version this project pins (`Directory.Packages.props:10`,
`Avalonia.Desktop 12.1.0`; local package cache `/root/.nuget/packages/avalonia/12.1.0/`).

Verified against `release/12.1` of the Avalonia source:

1. `Animation.RunAsync(..., CancellationToken)` registers a callback that does
   `run.TrySetResult(null); subscriptions?.Dispose();` — it disposes the animator subscriptions and
   completes the Task *successfully* (so the `await Task.WhenAll(...)` at
   `MainWindow.axaml.cs:462` returns normally, not via the `catch`). — `src/Avalonia.Base/Animation/Animation.cs`
2. The disposed subscription is `control.Bind((AvaloniaProperty<T>)Property, instance, BindingPriority.Animation)`
   from `Animator<T>.Run`. — `src/Avalonia.Base/Animation/Animators/Animator`1.cs`
3. Disposing that unsubscribes the single subscriber, and
   `SingleSubscriberObservableBase.Dispose()` invokes the `Unsubscribed()` hook.
   — `src/Avalonia.Base/Reactive/SingleSubscriberObservableBase.cs`
4. `AnimationInstance<T>.Unsubscribed()` calls `ApplyFinalFill()` with the comment *"Animation may
   have been stopped before it has finished"*, and `ApplyFinalFill()` does
   `_targetControl.SetValue(_animator.Property, _lastInterpValue)` (default priority = `LocalValue`)
   whenever `FillMode` is `Forward` or `Both`. Our animations are `FillMode.Forward`
   (`MainWindow.axaml.cs:1805`, `:1821`, `:1832`).
   `_lastInterpValue` is written every tick from the easing function, i.e. it is the **current
   interpolated value**, not the final keyframe.
   — `src/Avalonia.Base/Animation/AnimationInstance`1.cs`

**Net effect:** cancelling the swap does not restore `previous.Opacity` to `1`. It *freezes* it as a
`LocalValue` at whatever the fade had reached.

---

## 3. The real defect (corrected)

**Corrected statement:** `AnimateContentSwap` returns early on cancellation
(`MainWindow.axaml.cs:464-467`) before the only code that zeroes `previous.Opacity`
(`:468-475`). Because Avalonia's `FillMode.Forward` animator writes its last interpolated value as a
`LocalValue` when its subscription is disposed, the outgoing tab is left permanently pinned at a
*partial* opacity, and nothing on any later swap ever clears it — the keep-alive `foreach`
(`:423-426`) resets only `IsHitTestVisible`, and both cleanup sites (`:438` animated, `:473`
instant) only ever touch the *current* `previous`.

**Repro:** click «Настройки», then «Аккаунт» **within 150 ms** (`Motion.Dur.Exit`, not 220 ms).
Swap 2 cancels swap 1 → `_homeView.Opacity` freezes mid-fade. Swap 2's `previous` is `_settingsView`,
so Home is never touched again.

**Magnitude**, computed from `Ease.Standard = cubic-bezier(0.2, 0, 0, 1)` over the 150 ms exit fade:

| second click at | stranded `_homeView.Opacity` |
|---|---|
| 16 ms (1 frame) | 0.82 |
| 30 ms | 0.50 |
| 50 ms | 0.27 |
| 75 ms | 0.12 |
| 100 ms | 0.05 |
| ≥150 ms | 0.00 (no defect — fade already completed and wrote 0) |

This easing is very front-loaded, so realistic two-button clicks (~80–150 ms apart with a mouse) leave
a faint 1–10 % ghost; fast touch taps on `bottomNav` in compact layout (30–60 ms apart) leave a clearly
visible 25–50 % ghost of the Home screen.

**What is visible in the ghost:** *not* the gradient. `contentArea` already paints
`Brush.HomeGradient` under `contentHost` unconditionally (`MainWindow.axaml:551`), and `bodyRoot`
paints a second full-bleed gradient (`MainWindow.axaml:429-435`), so `HomeView`'s own gradient
(`HomeView.axaml:16`) is visually a no-op. What ghosts through is Home's **content** — the account
chip, the subscription/server list (`HomeView.axaml:29-35`) and the connect shield — bleeding through
the transparent Settings/Account surfaces.

**Persistence:** the strand survives arbitrarily long Settings↔Account use. It clears only when the
user navigates back to Home (`:452` resets `target.Opacity = 0d`, animation drives to 1, `:468` sets 1)
and then leaves Home with a swap that completes.

**No input hazard:** `IsHitTestVisible` is correctly forced false on every non-target tab
(`:423-426`), so the ghost is purely visual — it cannot steal clicks.

**Instant path:** the report's claim that `:431-442` (cited as `:415-426`) "strands identically" is
**correct** — it calls `_contentAnim?.Cancel()` at `:433` and then only zeroes the current `previous`
at `:438`. In practice this path is hard to reach for tab-to-tab strands, because the layout-swap
caller (`ApplyLayoutMode` → `ShowTab(_currentTab, animate:false)`, `MainWindow.axaml.cs:766`) usually
hits the `previous == target` early return at `:407-413`, which does *not* cancel `_contentAnim`.

---

## 4. Corroborating evidence that this is an oversight, not intent

The same author guarded exactly this failure mode two other times in the same file:

- `CrossfadeShellTo` explicitly force-hides the third, non-participating overlay with the comment
  *"страхует от прерванного кроссфейда, чтобы никогда не остались видны сразу три поверхности"*
  ("insures against an interrupted crossfade so three surfaces are never visible at once") —
  `MainWindow.axaml.cs:896-905`.
- `RunRegionReveal` repairs opacity unconditionally after the await with
  *"Контент виден, даже если стаггер no-op/прерван — гейтить видимость нельзя"* —
  `MainWindow.axaml.cs:685-689`.

`SwapContent`/`AnimateContentSwap` has no equivalent guard.

---

## 5. Suggested fix

In `SwapContent`, fold the opacity reset into the existing keep-alive loop so every non-target
surface is normalized on every swap — this repairs any strand left by a prior cancellation and
removes the need for the early-return cleanup to be reached:

```csharp
foreach (var v in new Control[] { _homeView, _compactHome, _settingsView, _accountView })
{
    v.IsHitTestVisible = ReferenceEquals(v, target);
    if (!ReferenceEquals(v, target) && !ReferenceEquals(v, previous))
    {
        v.Opacity = 0d;              // чинит strand от прерванного свопа
        v.RenderTransform = null;
    }
}
```

`previous` must be excluded because it is about to be faded out from its current opacity at `:459`.
Alternatively (or additionally), drop the early return at `:464-467` and instead skip only the
`target` writes, still zeroing `previous` when it is no longer `_currentContentView`.

---

## 6. Corrections to the original report, itemized

| Report claim | Status |
|---|---|
| Early return at `:448-451` skips the `previous.Opacity` cleanup | **True** (actual lines `464-467` / `468-475`) |
| "Avalonia reverts a cancelled animation's property to its base value" | **False** — `ApplyFinalFill()` on `Unsubscribed()` freezes it at `_lastInterpValue` (`LocalValue`) |
| "HomeView reverts to Opacity = 1 … fully visible Home screen" | **False** — pinned at the mid-fade value (0.05–0.82 depending on timing) |
| Keep-alive `foreach` resets only `IsHitTestVisible` | **True** (`:423-426`) |
| All four tabs are permanent children of `contentHost` | **True** (`:230-235`) |
| Repro window ≈ 220 ms (`Motion.Dur.State`) | **Wrong duration** — the outgoing fade is `Motion.Dur.Exit` = 150 ms (`:459`, `Motion.cs:40`) |
| Home root opaque; Account/Settings roots + `contentHost` transparent | **True** (`HomeView.axaml:16`, `AccountView.axaml:239/242-243`, `SettingsView.axaml:216-218`, `MainWindow.axaml:557`) |
| Ghost includes the gradient | **False** — the gradient is painted anyway by `contentArea` (`MainWindow.axaml:551`) and `bodyRoot` (`:429-435`); only Home's *content* ghosts |
| Instant path `:415-426` strands identically | **True in code** (`:431-442`), but rarely reached in practice (early return at `:407-413` does not cancel) |
| Severity: high | **Overstated** — visual-only (hit-testing is correct at `:423-426`), typically a faint ghost; **medium** |
