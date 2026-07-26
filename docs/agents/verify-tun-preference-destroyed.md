# Verify: "User's TUN preference is silently and permanently destroyed after one unelevated run"

Target: `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/StatusBarViewModel.cs:156`
Verdict: **REAL — but the reporter's trigger condition is wrong on Windows and understated on Linux/macOS.**

---

## 1. The persistence mechanism — fully confirmed, every link read

| Link | Evidence |
| --- | --- |
| The VM holds the **shared, global** config object, not a copy | `StatusBarViewModel.cs:138` `_config = AppManager.Instance.Config;`; `AppManager.cs:13` `public Config Config => _config;` |
| The ctor writes to it | `StatusBarViewModel.cs:156` `_config.TunModeItem.EnableTun = EnableTun = false;` |
| `SaveConfig` serializes the **whole** object, no field filtering | `Handler/ConfigHandler.cs:199-224`, in particular `:207` `JsonUtils.Serialize(config, true, true)` |
| `nullValue: true` selects the options with `DefaultIgnoreCondition = JsonIgnoreCondition.Never` | `Common/JsonUtils.cs:121-127` → `_nullValueSerializeOptions` at `Common/JsonUtils.cs:27-32` |
| A background task saves the same object every 20 minutes | `Manager/TaskManager.cs:39-52` (`await ConfigHandler.SaveConfig(_config)` at `:45`), registered with the same instance: `ViewModels/MainWindowViewModel.cs:93` `_config = AppManager.Instance.Config;` → `:333` `TaskManager.Instance.RegUpdateTask(_config, UpdateTaskHandler);` |
| Exit saves it too | `Manager/AppManager.cs:151-179`, `:161` `await ConfigHandler.SaveConfig(_config);` — called from `v2rayN.Desktop/App.axaml.cs:328` and `:462`, `v2rayN.Desktop/Views/MainWindow.axaml.cs:1876` |
| The "real intent" is kept **only** in RAM and never written anywhere | `_tunRequested` occurs at exactly `StatusBarViewModel.cs:134, 147, 209, 519, 556` — declaration, ctor read, elevation command, `DoEnableTun`, status recompute. No serialization, no config field. |
| The default that gets destroyed is TUN-**on** | `Models/Configs/ConfigItems.cs:184` `public bool EnableTun { get; set; } = true;`, `Handler/ConfigHandler.cs:93` `EnableTun = true` in the freshly-created config |

So: **once the ctor's else-branch runs, the user's persisted TUN preference is overwritten with `false` on disk within 20 minutes or at exit.** That half of the claim is exactly right.

## 2. Where the reporter is wrong: the Windows trigger does not exist in the shipped app

`AllowEnableTun()` (`StatusBarViewModel.cs:559-574`):

```csharp
if (Utils.IsWindows())   return Utils.IsAdministrator();
if (Utils.IsLinux())     return AppManager.Instance.LinuxSudoPwd.IsNotEmpty();
if (Utils.IsMacOS())     return AppManager.Instance.LinuxSudoPwd.IsNotEmpty();
return false;
```

The departament desktop app **always launches elevated on Windows**:

- `v2rayN.Desktop/app.manifest` — `<requestedExecutionLevel level="requireAdministrator" uiAccess="false" />`, with the comment "departament is a full-tunnel VPN (all PC traffic), so it launches elevated."
- `v2rayN.Desktop/v2rayN.Desktop.csproj` wires it: `<ApplicationManifest>app.manifest</ApplicationManifest>`.
- The release build publishes exactly this project: `.github/workflows/departament-branch-build.yml:18` `runs-on: windows-latest`, `:32` `dotnet publish ./v2rayN.Desktop/v2rayN.Desktop.csproj ... -r win-x64 -p:SelfContained=true`.
- `Common/Utils.cs:1249-1255` `IsAdministrator()` → `new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator)`, which is true in an elevated token.

Therefore on the shipped Windows build `AllowEnableTun()` is always `true`, the ctor takes the `EnableTun = true` branch (`:148-151`), and `:156` only ever re-writes `false` over an already-`false` value (a no-op). **"After one unelevated run" is not a reachable Windows scenario** for the shipped exe; it is reachable only in a dev launch that bypasses the apphost manifest (`dotnet departament.dll`).

## 3. Where the defect is worse than reported: Linux/macOS, every single launch

`AppManager.LinuxSudoPwd` (`Manager/AppManager.cs:50`) is a plain in-memory auto-property. It is written in exactly one place — the sudo-password dialog handler, `v2rayN.Desktop/Views/StatusBarView.axaml.cs:200-213` (`:212` `AppManager.Instance.LinuxSudoPwd = password;`). Nothing restores it from disk; it starts empty in every process.

`StatusBarViewModel.Instance` is constructed during app startup, long before any dialog can run: `v2rayN.Desktop/App.axaml.cs:28` `DataContext = StatusBarViewModel.Instance;` (and `ViewModels/MainWindowViewModel.cs:20`).

So on Linux/macOS `AllowEnableTun()` is **guaranteed false at construction time**, the ctor's else-branch at `StatusBarViewModel.cs:152-157` fires on **every** launch, and the downgrade is persisted by TaskManager/exit. Consequences, all traceable in code:

- First launch on a fresh profile: default `EnableTun = true` (`ConfigItems.cs:184`) is destroyed before the user ever sees the UI.
- The user *can* set TUN in settings — `v2rayN.Desktop/ViewModels/SettingsViewModel.cs:324-345` writes `_config.TunModeItem.EnableTun = enable` (`:331`) and saves (`:332`) without any elevation — but the next launch resets it to `false` again. **The preference can never survive a restart on Linux/macOS.**
- Same project is what the non-Windows workflows publish: `.github/workflows/build.yml:12` default `Project` = `./v2rayN.Desktop/v2rayN.Desktop.csproj`, consumed by `build-linux.yml` / `build-osx.yml`.

## 4. Extra defect the reporter missed: the honest-notice defeats itself after one restart

The A6 banner is wired for real (`v2rayN.Desktop/Views/HomeView.axaml:56-71`, `CompactHomeView.axaml:61-76`, `StatusBarView.axaml:106-119` — `TunRequestedButUnavailable` / `RoutingModeDisplay` / `RequestTunElevationCmd`), and `TunRequestedButUnavailable = _tunRequested && !TunAvailable` (`StatusBarViewModel.cs:556`).

But `_tunRequested` is seeded from the config that the previous session already destroyed (`:147` `_tunRequested = _config.TunModeItem.EnableTun;`). So on Linux/macOS:

- Launch 1: config `true` → banner shows "TUN requested but unavailable" + elevation button → config saved as `false`.
- Launch 2 and forever after: config `false` → `_tunRequested = false` → **no banner, no elevation affordance**, `RoutingModeDisplay` reads "Через системный прокси" and Settings shows "Прокси".

The mechanism added specifically to prevent a silent downgrade is neutralised by the persisted downgrade one restart later.

## 5. The "compounding" RebootAsAdmin paragraph: mechanically correct, but dead code in shipped builds

The ordering the reporter describes is real:

- `StatusBarViewModel.cs:520` `_config.TunModeItem.EnableTun = EnableTun;` then `:527` `_config.TunModeItem.EnableTun = false;` → `:529` `await AppManager.Instance.RebootAsAdmin();` → `:530` `return;` — so `DoEnableTun`'s own `await ConfigHandler.SaveConfig(_config);` at `:544` is skipped.
- `Manager/AppManager.cs:186-190` `RebootAsAdmin()` = `ProcUtils.RebootAsAdmin();` (`Common/ProcUtils.cs:49-68`, `Verb = "runas"`) then `await AppManager.Instance.AppExitAsync(true);` → `AppManager.cs:161` `SaveConfig` writes `EnableTun: false`.

So *if* that branch ran, the elevated relaunch would indeed come back with TUN off. But the branch is gated by `if (EnableTun && AllowEnableTun() == false)` (`:522`) **and** `if (Utils.IsWindows())` (`:525`). On the shipped Windows build `AllowEnableTun()` is always true (section 2); on Linux/macOS `IsWindows()` is false. **The compounding scenario is unreachable in the shipped departament apps** — only in an unelevated dev run. It is a latent trap worth fixing, not a live bug.

## 6. Provenance

`git blame` on the branch: line `:156` and the `RebootAsAdmin` block (`:520-531`) are upstream 2dust code (`0d225cd`, 2026-06-19). The departament commit `96d0d67` (s-erlish, 2026-07-16) added `_tunRequested` (`:131-134, 145-147`), `UpdateRoutingModeStatus()` (`:158, 528, 538, 545, 549-557`) and the elevation command around it — i.e. departament noticed the downgrade and papered over it in the UI instead of stopping it from being persisted.

## 7. Corrected statement of the defect

> The `StatusBarViewModel` constructor downgrades the **shared global** config — `_config.TunModeItem.EnableTun = EnableTun = false;` (`StatusBarViewModel.cs:156`, `_config` is `AppManager.Instance.Config`, `:138`) — whenever TUN is unavailable *in that process*, and that mutation is serialized wholesale to `guiNConfig.json` by the 20-minute autosave (`TaskManager.cs:39-52`) and by exit (`AppManager.cs:161`). The user's intent survives only in the never-persisted `_tunRequested` field (`:134`).
>
> On **Linux/macOS** this fires on **every** launch, not just after an unelevated one, because `AllowEnableTun()` there reduces to `AppManager.Instance.LinuxSudoPwd.IsNotEmpty()` (`:565-572`) and `LinuxSudoPwd` (`AppManager.cs:50`) is in-memory-only, set solely by the sudo dialog (`StatusBarView.axaml.cs:212`) which cannot have run before the VM is constructed (`App.axaml.cs:28`). Result: TUN can never survive a restart there, the shipped default `EnableTun = true` (`ConfigItems.cs:184`) is destroyed on first run, and from the second launch onward even the "TUN requested but unavailable" banner disappears because `_tunRequested` is re-seeded from the already-destroyed value (`:147`).
>
> On the **shipped Windows build the defect is not reachable**: `v2rayN.Desktop/app.manifest` declares `requireAdministrator` and the csproj embeds it, so `Utils.IsAdministrator()` (`Utils.cs:1249-1255`) is always true. The Windows `RebootAsAdmin` ordering problem (`:527-530` before `:544`) is likewise unreachable for the same reason — a latent trap, not a live bug.

**Fix shape (not applied):** never write the unavailability downgrade back into the persisted config. Keep the persisted `TunModeItem.EnableTun` as the user's intent, and pass the *effective* value (intent AND `AllowEnableTun()`) into core-config generation — the consumers are `Handler/Builder/CoreConfigContextBuilder.cs:45` (`IsTunEnabled = config.TunModeItem.EnableTun`) and `:204`, plus `Handler/ConfigHandler.cs:1508, 1522` and `Services/CoreConfig/CoreConfigClashService.cs:105, 174`. Equivalently: introduce an `EffectiveEnableTun` computed from `_tunRequested && AllowEnableTun()` and stop mutating `_config` at `:156`, `:527`, `:537`.
