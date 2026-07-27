# Verification: "Invalid local-proxy port is silently discarded — no error state exists"

**Target:** `/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/SettingsViewModel.cs:369`
**Verdict: CONFIRMED — real defect.** The mechanism the reporter describes is accurate.
Two corrections and one aggravating factor below; severity is arguably **medium**, not high.

---

## 1. The silent reject is exactly where claimed

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/SettingsViewModel.cs:369-403`
(`CommitLocalProxyAsync`), the rejection block at **:385-391**:

```csharp
var portOk = int.TryParse(LocalPortText?.Trim(), out var port) && port > 0 && port < Global.MaxPort;
if (!portOk)
{
    // Reject silently and restore the real value so the UI never shows an un-persisted port.
    LocalPortText = inbound.LocalPort.ToString();
    port = inbound.LocalPort;
}
```

No message, no flag, no event. The comment says "Reject silently" in as many words. The
method has no other exit that informs anyone. The reporter cited `:369` (the method
declaration); the offending statements are `:385-391`.

**Range is correct, not the bug.** `Global.MaxPort = 65536`
(`/home/user/v2rayN/v2rayN/ServiceLib/Global.cs:108`), so `1..65535` is accepted — `99999`
is genuinely invalid. The *validation* is right; only the *silence* is wrong. The config is
never corrupted, which is why this is a UX defect and not a data defect.

**Invalid input is reachable.** The port field is a plain `TextBox` with `MaxLength="5"` and
no numeric filter (`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml:483-487`).
`99999`, `abc`, and an empty field are all typeable, and all three hit the silent branch.

## 2. Commit trigger — reporter is right, but incomplete (this is the aggravating factor)

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml.cs:62-64` wires
`LostFocus` on all three proxy fields → `OnProxyFieldCommit` (`:307`) → `CommitLocalProxyAsync`.
That is the path the reporter describes.

There is a **second, worse commit path the reporter missed** — collapsing the inline panel,
`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml.cs:226-232`:

```csharp
await RevealPanel(LocalProxyPanel, show: false);
LocalProxyPanel.IsVisible = false;
// Сворачивание = коммит введённых значений (порт/логин/пароль → Inbound[0]).
_ = Vm?.CommitLocalProxyAsync();
```

The panel is faded out and set `IsVisible = false` **before** the commit runs. So on this path
even the corrective snap-back — the only feedback that exists today — is invisible. The user
types a port, taps the row to close the panel, and absolutely nothing happens or is said. The
next time they open the panel the old port is sitting there, indistinguishable from "the app
never saw my typing".

## 3. "No error state exists" — confirmed across the whole screen

- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml` is 1075 lines and
  contains **zero** occurrences of `Red`, `Error`, or Russian `ошиб*`. There is no error label,
  no error border, no error icon anywhere on the settings screen.
- The field's own theme has no error state either:
  `TextBox.IncyField` (`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml:494-568`)
  defines `:pointerover` (`:559`), `:focus` (`:562`) and `:disabled` (`:565`) — and nothing else.
  Its template (`:508-558`) also contains **no `DataValidationErrors` presenter**, so even if the
  VM implemented `INotifyDataErrorInfo`, nothing would render. The theme is structurally
  incapable of showing an error today.

This is a direct violation of the design law in `/home/user/dp/CLAUDE.md`:
"Every state designed (pressed = subtle scale, selected, disabled, empty, loading, **error**)".

## 4. The primitives the reporter names — mostly correct, one caveat

| Claimed primitive | Status |
|---|---|
| `Brush.RedText` at `GlobalResources.axaml:84` | **Confirmed**, and better than claimed — defined for *both* themes: dark `#FF6069` (`:84`) and light `#C42B32` (`:120`), plus a runtime override in `App.axaml.cs:631`. Drop-in and theme-safe. |
| `TextBox.fieldError` at `LoginView.axaml:122-124` | **Confirmed as a pattern, NOT as a reusable resource.** The selector lives inside `LoginView`'s own `<UserControl.Styles>`, so it is scoped to that view. Using it in `SettingsView` means either copying the 3-line selector or promoting it to `GlobalResources.axaml`. Driver: `LoginView.axaml.cs:873-884` (`FlashCredentialFields`, ~220 ms colour flash, no shake). |

The stronger precedent the reporter did not cite is the **inline error label**, which is
exactly the shape this bug needs:
`LoginView.axaml:353-360` (`EmailError`) and `:419-426` (`ConfirmPasswordError`) — `Classes="Caption"`,
`Foreground="{DynamicResource Brush.RedText}"`, `IsVisible="False"`, toggled live from
`LoginView.axaml.cs:723` and `:739-740`.

## 5. Corroboration: this is a REGRESSION against the engine VM it replaced

The shared v2rayN settings VM that this Departament screen supersedes reports this exact
condition to the user — `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/OptionSettingViewModel.cs:304-309`:

```csharp
if (LocalPort.ToString().IsNullOrEmpty() || !Utils.IsNumeric(LocalPort.ToString())
   || LocalPort <= 0 || LocalPort >= Global.MaxPort)
{
    NoticeManager.Instance.Enqueue(ResUI.FillLocalListeningPort);
    return;
}
```

The message even ships localized already: `ResUI.ru.resx:153-155` → "Введите локальный порт для
прослушивания" (`ResUI.resx:153-155` EN). So the rewrite dropped a user-visible error that
upstream had. That removes the "maybe nobody thought it needed one" defence.

## 6. A working app-level feedback channel also already exists (but is the wrong shape here)

`AppEvents.SendSnackMsgRequested.Publish(...)` is the app's standard feedback bus, used
throughout `AccountViewModel.cs` (e.g. `:1350` "Account_AmountGtZero", `:1912` "Login_EmailInvalid").
It is subscribed in `MainWindow.axaml.cs:329-332` → `DelegateSnackMsg` (`:1789-1792`).

Important nuance for anyone fixing this: the owner **deliberately disabled the floating bottom
toast** — see the comment block at `MainWindow.axaml.cs:1780-1788`; snack text is routed to the
inline message-log panel instead. So a snack here would land in a log surface, not next to the
field. **The design-consistent fix is the LoginView inline-label pattern**, not the snack bus.

## 7. Suggested fix (shape only)

1. Add `[Reactive] public bool PortInvalid { get; set; }` to `SettingsViewModel`; set it in the
   `!portOk` branch (`:387`) and clear it on valid commit and on every keystroke.
2. Add a `Caption`/`Brush.RedText` `TextBlock` under `ProxyPortBox` in
   `SettingsView.axaml:483-488`, bound to `PortInvalid`, with a new key alongside
   `Settings_Port` in `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.Settings.cs:35` —
   e.g. `Add("Settings_PortInvalid", "Порт должен быть от 1 до 65535", "Port must be between 1 and 65535");`
   (sentence-case Russian, per design law).
3. Promote the `TextBox.fieldError` selector from `LoginView.axaml:122-124` into
   `GlobalResources.axaml` so both screens share one error primitive, and flash the port field
   on reject.
4. Fix the collapse path (`SettingsView.axaml.cs:226-232`): validate **before** hiding the
   panel, and keep the panel open when the port is invalid — otherwise the new error label is
   hidden at the exact moment it is needed.

## 8. Bonus finding in the same method (low severity, not the reported defect)

`SettingsViewModel.cs:393-397` returns early when `changed == false`, **before** normalizing the
displayed text. Consequences:

- Typing `" 10808 "` or `"010808"` when the persisted port is `10808` parses valid, `changed`
  is false → early return → the field keeps showing the un-normalized string while the config
  holds `10808`.
- `user = ProxyUser?.Trim()` (`:382`) is persisted trimmed, but `ProxyUser` itself is never
  written back trimmed, so `" bob "` stays in the box after `"bob"` is saved.

Cosmetic display drift only; no wrong value is ever persisted.

---

### Files read for this verification

- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/SettingsViewModel.cs`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml.cs`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml.cs`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.Settings.cs`
- `/home/user/v2rayN/v2rayN/ServiceLib/Global.cs`
- `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/OptionSettingViewModel.cs`
- `/home/user/v2rayN/v2rayN/ServiceLib/Resx/ResUI.resx`, `ResUI.ru.resx`
