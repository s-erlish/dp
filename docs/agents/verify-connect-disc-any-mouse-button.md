# Verify: "Right-click / middle-click on the connect disc toggles the VPN"

**Verdict: CONFIRMED (real defect).** One citation in the report is wrong (`HomeViewModel.cs:166-176`
→ actual `184-194`), and the "no feedback under lite mode" note is technically true but not itself a
defect. The core mechanism — *any* mouse button press+release over the connect disc starts or stops
the tunnel — is exactly as described, and I could not refute it.

Severity: agree with **high**. This is the app's single most destructive-when-accidental control
(it tears down / brings up the VPN tunnel), reachable by a button the user does not associate with
activation.

---

## 1. The handler pair — no button filtering

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ConnectHeroView.axaml.cs:688-716`

```csharp
688:    private void OnDiscPointerPressed(object? sender, PointerPressedEventArgs e)
689:    {
690:        _pressing = true;
691:        if (ReducedMotion)
692:        {
693:            return;
694:        }
...
708:    private void OnDiscPointerReleased(object? sender, PointerReleasedEventArgs e)
709:    {
710:        ReleaseDiscScale();
711:        if (_pressing)
712:        {
713:            _pressing = false;
714:            ConnectToggleRequested?.Invoke(this, EventArgs.Empty);
715:        }
716:    }
```

`e` is never inspected in either handler. There is no
`e.GetCurrentPoint(...).Properties.IsLeftButtonPressed` on press and no
`e.InitialPressMouseButton == MouseButton.Left` on release. The *only* gate on firing
`ConnectToggleRequested` is the `_pressing` bool set unconditionally at `:690`.

## 2. The handlers are attached unconditionally, to a bare `Border`

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ConnectHeroView.axaml.cs:250-256`

```csharp
251:        ConnectDisc.PointerPressed += OnDiscPointerPressed;
252:        ConnectDisc.PointerReleased += OnDiscPointerReleased;
253:        ConnectDisc.PointerCaptureLost += OnDiscPressCancel;
254:        ConnectDisc.PointerExited += OnDiscPressCancel;
```

`ConnectDisc` is a plain `Border`, not a `Button`
(`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ConnectHeroView.axaml:511-517`):

```xml
511:                    <Border
512:                        x:Name="ConnectDisc"
...
515:                        Classes="ConnectDisc"
516:                        Cursor="Hand"
```

So there is no `Button`/`ClickMode` machinery doing left-button filtering for us — raw
`PointerPressed`/`PointerReleased`, which Avalonia raises for **every** pointer button, are the whole
activation path. There is no `ContextMenu`/`ContextFlyout` anywhere in `ConnectHeroView.axaml`
(grep for `ContextMenu|ContextFlyout` returns only the `Border.ConnectDisc` style hits), so nothing
consumes the right button before the handlers run.

**Refutation attempts that failed:**

- *Could an ancestor swallow right-click first?* The only tunneling pointer handler on the window is
  `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs:156`
  (`AddHandler(PointerPressedEvent, OnAnyPointerPressed, RoutingStrategies.Tunnel, handledEventsToo: true)`),
  and its body at `:1632-1633` is `=> _lastPointerInWindow = e.GetPosition(chromeRoot);` — it records
  a coordinate for the theme-ripple origin and never sets `e.Handled`. Nothing blocks the disc.
- *Is the disc disabled/hidden in some states so the window is narrow?* No `IsEnabled` or
  `IsHitTestVisible` gating exists on `ConnectDisc` in code-behind (grep over the file returns only
  the transform/handler/hover lines). The one state that removes it is onboarding-empty
  (`ShowEmptyState`, `:293-297`, toggles `LayerNormal.IsVisible`) — i.e. exactly the state where the
  disc is not on screen anyway. In every normal state (idle / connecting / connected / error) the
  disc is live to all buttons.
- *Does drag-off save us?* Partly and irrelevantly: `PointerExited` and `PointerCaptureLost` both
  clear `_pressing` (`:718-722`), so a right-press that leaves the disc before release does nothing.
  A right-press **released over the disc** — the ordinary right-click — fires the toggle.

## 3. The event really does start/stop the tunnel

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/HomeHeroPresenter.cs:60,65`

```csharp
60:        void OnToggle(object? s, EventArgs e) => vm.ConnectToggle();
...
65:        hero.ConnectToggleRequested += OnToggle;
```

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/HomeViewModel.cs:184-194`

```csharp
184:    public void ConnectToggle()
185:    {
186:        if (IsConnected)
187:        {
188:            _ = Disconnect();
189:        }
190:        else
191:        {
192:            _ = Connect();
193:        }
194:    }
```

(`Connect()` at `:196` calls `_main.Reload()` — a real core start.) **Citation correction:** the
report cited `HomeViewModel.cs:166-176`; those lines are the `CoreSwitchSettled` subscription. The
toggle is at `184-194`. The claim's substance is unaffected.

## 4. The in-repo counter-example is correctly guarded — this is an inconsistency, not a house style

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ServerListView.axaml.cs:150-194`, whose comment at
`:140` explicitly says it *mirrors* the disc's `_pressing` flag, does the button check on both legs:

```csharp
162:        if (e.GetCurrentPoint(b).Properties.IsLeftButtonPressed && b.DataContext is ProfileItemModel item)
...
184:        if (_rowPressing
185:            && e.InitialPressMouseButton == MouseButton.Left
186:            && _rowPressTarget is { } item
187:            && b.IsPointerOver
```

Every other custom pointer-activated control in the desktop app also filters:
`AccountView.axaml.cs:385`, `MainWindow.axaml.cs:1269`, `MainWindow.axaml.cs:1510`,
`ProfilesView.axaml.cs:457`. The connect disc is the sole outlier, and it guards the highest-stakes
action in the app. (`AccountView.axaml.cs:142-155` `WirePress` also has no button check, but it only
adds/removes a `pressed` style class — cosmetic, no action fired.)

## 5. On the report's lite-mode note

Accurate as mechanism: `_pressing = true` at `:690` precedes the `ReducedMotion` early-return at
`:691-694`, and `ReleaseDiscScale()` likewise early-returns at `:726-729`, so in lite mode the toggle
fires with no press animation. But that is true of left-click in lite mode too and is intentional
(`ReducedMotion` is set from `LiteModeEnabled() || !SystemAnimationsEnabled()`, `:188`, `:623`) — the
state change itself is the feedback. It is not an additional defect; it only means the accidental
right-click toggle is *even less* noticeable in lite mode.

## Corrected description

> The connect disc (`Border x:Name="ConnectDisc"`) activates on **any** pointer button. `OnDiscPointerPressed`
> (`ConnectHeroView.axaml.cs:690`) sets `_pressing = true` without inspecting
> `e.GetCurrentPoint(ConnectDisc).Properties`, and `OnDiscPointerReleased` (`:708-716`) raises
> `ConnectToggleRequested` on any release while `_pressing`, without checking
> `e.InitialPressMouseButton`. The event runs `HomeViewModel.ConnectToggle()`
> (`HomeHeroPresenter.cs:60,65` → `HomeViewModel.cs:184-194`), which starts or tears down the tunnel.
> Result: a right-click or middle-click on the disc — including a right-click a user makes expecting a
> context menu, of which the disc has none — connects or disconnects the VPN. Every comparable control
> in the app filters for the left button (`ServerListView.axaml.cs:162,185`; `AccountView.axaml.cs:385`;
> `ProfilesView.axaml.cs:457`; `MainWindow.axaml.cs:1269,1510`); the disc is the only unguarded one.

## Fix

Two lines, mirroring `ServerListView`:

- `ConnectHeroView.axaml.cs:690` → `_pressing = e.GetCurrentPoint(ConnectDisc).Properties.IsLeftButtonPressed;`
  then `if (!_pressing || ReducedMotion) return;` before the press visuals (so a right-press neither
  arms the toggle nor plays the dip).
- `ConnectHeroView.axaml.cs:711` → `if (_pressing && e.InitialPressMouseButton == MouseButton.Left)`.

Requires `using Avalonia.Input;` for `MouseButton` (already implied by the `PointerPressedEventArgs`
usage in the file).

### Adjacent gap noticed while verifying (not part of this claim)

The disc is a `Border` with no `Focusable`, no `KeyDown` handler and no automation peer, so the
primary connect control has **no keyboard activation path at all** — Space/Enter do nothing. Design
law in `/home/user/dp/CLAUDE.md` demands every state designed and ≥48dp/accessible targets; keyboard
reachability of the hero action belongs in the same fix.
