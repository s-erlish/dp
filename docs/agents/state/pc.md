# PC (desktop) — verified state

**Scope:** `v2rayN/v2rayN.Desktop/**` — the token layer (`Assets/GlobalResources.axaml`), the
class-style layer (`Assets/GlobalStyles.axaml`), `App.axaml(.cs)`, all 50 `Views/*.axaml` and their
code-behind, and `ViewModels/SettingsViewModel.cs`. Checked against
`docs/design2026/{10-design-system,22-components,33-master-plan-pc,12-settings}.md` and the three
`docs/agents/audit2026/pc-*.md` audits.

**Method:** every claim was read in source. Where a document claims a thing exists, I found the
declaration. Where a document claims a thing is *used*, I found the consumer — or recorded that
there is none. Class-style usage was computed twice by two different methods (regex over
`Classes="…"` + `Classes.Add/Remove/Contains` in every `.axaml` and `.cs`, then a per-name grep) and
both agree.

**Repos:** `/home/user/v2rayN` HEAD `ccbec27`, `/home/user/dp` HEAD `bf3a738`, branch
`claude/app-audit-agents-hyyftk` on both.

**Build:** verified, not assumed. `dotnet build v2rayN.Desktop -c Release --no-incremental` →
**Build succeeded, 0 errors, 28 warnings**, which is exactly the recorded baseline in
`docs/agents/.baseline-warnings-desktop.txt`. Nothing here is broken markup. That is the point of
the next section.

---

## The headline

**The desktop token layer is complete and genuinely applied. The desktop class-style layer that was
written on top of it is complete, compiles, and is applied by nothing.**

The 2026-07-26 wave grew `Assets/GlobalStyles.axaml` from 1448 to 2645 lines. That growth added
**45 new class names**. Of those 45, **45 are referenced by zero views and zero code-behind.**
Not "mostly unused" — none of them. The views the user actually sees were last edited on
2026-07-16/17 and still speak the old vocabulary, plus 26 view-local `<Style Selector>` rules in
`AccountView` alone that duplicate what the new global classes now express.

Same shape in `Common/Motion.cs`: the file was extended to the `22-components.md` §20.2 spec
(`Dur.Pulse`, `Dur.Spin`, `Dur.Debounce`, `Dur.RevealExit`, `Dur.StateExit`, `Dur.Hover`,
`PressScale`, `Play()`, `StaggerFor()`). **Every one of those new members has zero call sites.**
The pre-existing `Ease.*` / `Dur.State|Reveal|Exit|Shell|Stagger` are used; nothing added in this
wave is.

So the honest state is: step 1 of `22-components.md` §21's migration order (tokens) is **done**;
steps 3–7 (buttons, rows, fields/select/segment/switch, chrome, states) are **written as a
vocabulary and not migrated into a single screen**.

---

## 1. The token layer — `Assets/GlobalResources.axaml` (711 lines)

### Done and wired

| Thing | Evidence |
|---|---|
| R11 — accent moved **inside** `ResourceDictionary.ThemeDictionaries`, light accent `#1E5FC7` not `#4C8DFF` | `GlobalResources.axaml:63-241`; `Brush.Accent` at `:69` (Dark) and `:163` (Light). This was the spec's only P1 accessibility defect in the whole token system and it is closed. |
| Two full theme dictionaries, identical key sets, Dark + Light | `:66-156` and `:159-239` |
| `Brush.AccentHover` / `Brush.AccentPressed` | `:74-75`, `:166-167` — spec §20.2 verbatim |
| `Radius.Button` 16, moved out of GlobalStyles into the token file | `:273` |
| `Size.Btn` 48 / `Size.BtnTall` 52 / `Size.BtnMinWidth` 96 / `Size.Field` 56 / `Size.Meter` 6 | `:294-298` — the five §20.2 additions, all present |
| `Brush.OutlineControl` (D-9, the 3:1 control boundary) | `:96` `#646C7C`, `:182` `#7D8BA3` |
| `Brush.RedText` / `Brush.Amber` / `Brush.AmberText` / `Brush.Ping.Good` with separate light text tones (D-10) | `:104-112`, `:190-200` |
| Motion curves as `SplineEasing`, 1:1 with Android | `:326-333` |
| Third theme (black/AMOLED) as a code overlay, not a variant | `App.axaml.cs:544-662` `ApplyMonoOverlay` / `BuildMonoOverlay` |
| Tokens actually consumed by views | `grep 'StaticResource Brush\.' Views/` = **0** across all 50 views — every brush is `DynamicResource`, so live theme switching is real, not decorative |

### Declared and not honoured

| Thing | State |
|---|---|
| `Radius.Search` 14 and `Radius.Traffic` 8 — **retired** by D-7, kept only until the last reference migrates | Still drawing live UI: `ServerListView.axaml:156` (the canonical server row), `AccountView.axaml:511,517`, and — worse — the two *promoted* control themes themselves, `TextBox.Incy` (`:552`) and `TextBox.IncyField` (`:639`), plus `Border.SearchPill` (`GlobalStyles:1003`) and `Border.TrafficPill` (`:1011`). The retired radius is the default for every input in the app. |
| `Size.CtaTall` 52, `Size.SegmentChip` 44 — retired | Still defined (`:440-441`); no migration performed |
| `Brush.OutlineStrong` (required by `33-master-plan-pc.md` 2.12.2 for the connect ring) | **Does not exist.** `pc-home.md` C-5 flagged this; still absent. |
| `Stroke.Control` (`10-design-system.md` §538 — "this is the 3:1 stroke") | **Does not exist** as a desktop token; the 1px is written inline on each control theme |

### A defect the token wave introduced — mono theme now leaks brand blue

`BuildMonoOverlay` (`App.axaml.cs:578-661`) overrides 36 brush keys. The token wave added seven
theme-dependent keys to Dark/Light and **did not mirror any of them into the mono overlay**:

`Brush.AccentHover`, `Brush.AccentPressed`, `Brush.OutlineControl`, `Brush.OnSurfaceVariantHover`,
`Brush.Amber`, `Brush.AmberText`, `Brush.Ping.Good`.

Two of these are load-bearing and visible:

- `GlobalStyles.axaml:653` / `:656` — `Button.Primary:pointerover` / `:pressed` set
  `Brush.AccentHover` / `Brush.AccentPressed`. `Button.Primary` is used **44 times**. In the
  black/mono theme the button sits grey at rest (mono overrides `Brush.Accent`) and **flashes
  `#3D7EF0` brand blue on hover and `#3877E0` on press**, because those two keys fall through to the
  base variant. Mono's whole contract is "no accent hue"; it breaks on every primary button the
  moment a pointer touches it.
- `GlobalStyles.axaml:2154` / `:2160` — status-chip success/warning text still resolves to green
  `#22C55E` and amber `#EAB308` under mono. (Currently latent: those two selectors belong to
  `Border.Chip.Status.*`, which no view uses yet — see §2.)

This is a real regression of the kind the brief is about: new keys added to two of three themes.

---

## 2. The class-style layer — `Assets/GlobalStyles.axaml` (2645 lines)

**111 class names declared. 56 used. 55 unused.** Of the 55 unused, **45 are exactly the 45 the
2026-07-26 wave added.** The 10 remaining unused names pre-date the wave (`ChipBadge`, `NavRail`,
`Green`, `Orange`, `Purple`, `Yellow`, `paid`, `pending`, `failed`, `canceled`).

**The 45 declared-and-never-used names, verbatim:**

`Action`, `Check`, `Danger`, `DestructiveText`, `Divider`, `EmptyState`, `Error`, `Field`,
`FieldError`, `FieldLabel`, `Filled`, `Flush`, `Group`, `Icon`, `Inline`, `Meter`, `Modal`,
`Money`, `NavIndicator`, `NavItem`, `Neutral`, `OfflineBar`, `Ok`, `Pressable`, `PrimaryCompact`,
`Secondary`, `SegmentTrack`, `Selectable`, `Skeleton`, `Status`, `Tertiary`, `TextAction`,
`Toggle`, `Value`, `Warn`, `Wordmark`, `bar`, `disabled`, `error`, `focused`, `loading`, `over`,
`rail`, `readonly`, `scrolled`.

That set is the `22-components.md` vocabulary: the five button variants (`Secondary`, `Tertiary`,
`TextAction`, `DestructiveText`, `PrimaryCompact`), the five row archetypes
(`Row.Value/.Action/.Toggle/.Destructive` modifiers), the text field (`Field`, `FieldLabel`,
`FieldError`, `focused`, `error`, `readonly`), the selection indicator (`Selectable`, `Check`,
`selected` states), the segmented control (`SegmentTrack`), the icon button (`Icon`, `.Filled`,
`.Accent`, `.Danger`, `.Row`), navigation (`NavItem`, `NavIndicator`), and the state components
(`EmptyState`, `Skeleton`, `Meter`, `OfflineBar`, `loading`, `disabled`).

**What the views use instead.** They keep the older `IconButton` (64 uses) and `IconButton40` (38),
`SettingRow` (44), `ServerRow`, `Card` (86), `Tile`, `Tonal`, `LinkAction`, `Segment` — and they
hand-roll the rest locally:

| View-local rule | The global class it shadows |
|---|---|
| `LoginView.axaml:72` `Border.SegTrack` + `:78` `Button.SegItem` | `Border.SegmentTrack` + `ToggleButton.Segment` |
| `SettingsView.axaml:143` `Border.SettingDivider`, `PingSettingsPage:49` `Border.MethodDivider`, `ServerListView:51` `Border.rowDivider` | `Border.Divider` |
| `SettingsView.axaml:68` a **full local `ControlTheme x:Key="TextBox.IncyField"`** | the promoted global one at `GlobalResources.axaml:635` |

That last one matters twice. `{StaticResource}` resolves nearest-first, so the local copy at
`SettingsView.axaml:68` wins and the promoted global `TextBox.IncyField` (75 lines,
`GlobalResources.axaml:635-709`) has **no live consumer at all**. The two bodies also differ:
local uses `Radius.Tile` 12, global uses `Radius.Search` 14, and the local one drops the
`:disabled` opacity rule. `docs/agents/verify-settingsview-incyfield-shadowing.md` confirmed this
defect in an earlier round; it is unchanged.

Local style-rule counts, for the size of the migration debt:
`AccountView` 26, `BuyView` 20, `LoginView` 18, `ConnectHeroView` 12, `SettingsView` 10,
`DnsSubView` 8, `DevicesView` 6, `PingSettingsPage` 6, `ServerListView` 3, `AboutPage` 2.

### The one class rule the wave did *not* enforce anywhere

`22-components.md` R4 / D-11: one press recipe, **scale 0.97**. Current distribution across
`Views/` + `Assets/`:

`0.97` ×16 · `0.92` ×13 · `0.99` ×4 · `0.96` ×1 · `0.94` ×1 · `0.9` ×1 (toggle knob squash, legitimate).

`scale(0.92)` is redeclared verbatim in 12 view files — `AboutPage`, `BackupPage`, `BottomNavBar`,
`DevicesView`, `DnsSubView`, `GeoFilesPage`, `MainWindow`, `PerAppProxyPage`, `PingSettingsPage`,
`ProviderSettingsPage`, `RoutingSubView`, `UrlSchemesPage`.

---

## 3. Which views were reworked, and which were not

Last commit that touched each file (`git log -1 --`):

**Touched in the 2026-07-26 wave (5 files, all small defect fixes — none is a redesign):**

| File | Commit | Size of change |
|---|---|---|
| `Assets/GlobalResources.axaml` | `655adb3` 15:53 | the token work above |
| `Assets/GlobalStyles.axaml` | `c13042e`/`1e9f969`/`5c43abc` | +1197 lines of unused vocabulary |
| `Views/AccountView.axaml` | `5c43abc` 16:49 | +12 lines |
| `Views/ServerListView.axaml` | `5c43abc` 16:49 | +15 lines (`Focusable`/`IsTabStop` on the row) |
| `Views/BuyView.axaml` | `8778233` 16:54 | +11 lines |

**Untouched since 2026-07-17 or earlier — i.e. every screen the design wave was supposed to
rework:**

`SettingsView.axaml` (07-17 17:04) · `LoginView.axaml` (07-17 20:29) · `MainWindow.axaml` (07-17
14:23) · `ConnectHeroView.axaml` (07-17 13:34) · `BottomNavBar.axaml` (07-17 14:23) ·
`OnboardingView.axaml` (07-17 20:29) · `AccountSyncView.axaml` · `HomeAccountChip.axaml` (07-17
10:37) · `App.axaml` (07-15) · `ServersView.axaml` (07-15).

And the whole 07-16 block, untouched by anything since: `HomeView.axaml`, `CompactHomeView.axaml`,
`CompactServersView.axaml`, `DevicesView.axaml`, `PaymentHistoryView.axaml`, `DnsSubView.axaml`,
`RoutingSubView.axaml`, `PingSettingsPage.axaml`, `GeoFilesPage.axaml`, `BackupPage.axaml`,
`AboutPage.axaml`, `UrlSchemesPage.axaml`, `PerAppProxyPage.axaml`, `ProviderSettingsPage.axaml`,
`ProfilesView.axaml`, plus all seven legacy `resx`-string editor windows.

So: **the token/class foundation moved; not one screen was migrated onto it.**

### Reachability census (50 view files)

**Live and reachable by a user:** `MainWindow`, `OnboardingView`, `AccountSyncView`,
`BottomNavBar`, `HomeView`, `CompactHomeView`, `ConnectHeroView`, `HomeAccountChip`,
`ServerListView`, `SubscriptionMetaView`, `SettingsView`, `AccountView`, `StatusBarView` (hosted at
0×0, but it owns three live interaction handlers); sub-pages `PerAppProxyPage`, `DnsSubView`,
`PingSettingsPage`, `RoutingSubView`, `GeoFilesPage`, `AboutPage`, `BackupPage`, `UrlSchemesPage`,
`LoginView`, `BuyView`, `DevicesView`, `PaymentHistoryView`; modals reached from the server row's
context menu via ServiceLib (`AddServerWindow`, `AddServer2Window`, `AddGroupServerWindow`,
`SubEditWindow`, `ProfilesSelectWindow`, `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow`),
plus `QrcodeView`, `MessageBoxDialog`, `SudoPasswordInputView`.

**Built but unreachable (zero constructors, zero XAML refs, and no shell command binds their
ViewModel's dialog interaction):** `ServersView`, `CompactServersView`, `ProfilesView`,
`ProviderSettingsPage`, `ThemeSettingView`, `BackupAndRestoreView`, `CheckUpdateView`, `MsgView`,
`ClashProxiesView`, `ClashConnectionsView`, `OptionSettingWindow` (1206 lines),
`GlobalHotkeySettingWindow`, `FullConfigTemplateWindow`, `SubSettingWindow`.

Verified for the windows: they are registered in `Common/SimpleViewLocator.cs:15-36` and would open
if `MainWindowViewModel`'s `WindowDialog.ShowDialogAsync` were invoked, but
`grep 'OptionSettingCmd|GlobalHotkeySettingCmd|SubSettingCmd|CheckUpdateCmd' Views/` returns
nothing outside those views' own files. No departament surface raises them. **14 of 50 views are
dead weight**, and with them go core selection, log level, global hotkeys, the config template,
check-for-updates and the log viewer.

---

## 4. The open question: which server view is live

**`ServerListView` — and only `ServerListView`.** Confirmed independently of the audit:

- `ServersView.axaml` is 12 lines whose entire body is `<local:ServerListView />`
  (`Views/ServersView.axaml:11`). Nothing constructs `ServersView`: `grep -rn "ServersView"` over
  `.cs` + `.axaml` returns only its own `x:Class` / `partial class` and prose comments.
- `CompactServersView` — same, zero constructors. It holds the **only server search field in the
  product** (`CompactServersView.axaml:88-113`) and it is unreachable.
- `ProfilesView` is registered at `SimpleViewLocator.cs:29` but no `ProfilesViewModel` is ever
  routed through the locator in this shell — dead.
- `ServerListView` is constructed twice, in both live Home layouts: `HomeView.axaml:35` and
  `CompactHomeView.axaml:91`.

**Why they are dead by construction, not by accident:** `BottomNavBar.axaml.cs:9-14` declares
`enum AppTab { Home, Settings, Account }` — three values. `MainWindow.axaml.cs:175` wires exactly
three rail buttons (`navHome`, `navSettings`, `navAccount`) and `MainWindow.axaml:456-495` renders
exactly three. **There is no «Серверы» destination and no route that could reach one.** Servers
live in the left 440px column of `HomeView`.

Consequence for the next wave, unchanged from `pc-servers-account.md` §1: editing
`ServersView.axaml` or `CompactServersView.axaml` ships **zero pixels**. The fourth destination the
master plan wants (`33-master-plan-pc.md:108`) has to be built, not restyled.

---

## 5. The two must-not-regress fixes — both intact

### 5.1 Onboarding gate decides from a synchronous storage snapshot — **INTACT**

`Views/MainWindow.axaml.cs:216-217`, in the constructor, before the first `ApplyShellVisibility()`:

```csharp
_storedServersAtLaunch = Design.IsDesignMode ? null : AppManager.Instance.HasStoredProfiles();
_isEmpty = _storedServersAtLaunch == false;
```

All four required properties verified:

1. **Synchronous.** `AppManager.HasStoredProfiles()` (`ServiceLib/Manager/AppManager.cs:223-234`)
   runs `SQLiteHelper.Instance.ExecuteScalar<int>("select count(*) from ProfileItem")` on the
   **synchronous** connection. `SqliteHelper.ExecuteScalar` (`Helper/SqliteHelper.cs:83`) exists
   for exactly this one launch-path question and documents itself as such.
2. **Tri-state.** It returns `bool?`; `null` means *unknown*, and the assignment is deliberately
   `== false`, not `!= true`. Unknown therefore yields `_isEmpty = false` → the shell, not the gate.
3. **The 3-way precedence still gates it.** `ApplyShellVisibility()` (`:880-882`):
   `(_isSyncing || _isStartupLoading) ? accountSyncView : (_isEmpty && !_isLoggedIn) ? onboardingView : bodyRoot`
   — syncing > empty > content, with the `!_isLoggedIn` clause so a signed-in user with an empty
   account lands on Главная, not on the sign-in gate.
4. **One question, one answer on both sides.** The same snapshot is handed to the view model:
   `_homeViewModel = new HomeViewModel(vm, _storedServersAtLaunch)` (`:1013`), and
   `HomeViewModel.cs:605-606` carries the same rule: `IsEmpty = loaded ? count == 0 : _storedServersAtLaunch == false`.

No unloaded default anywhere in the path.

### 5.2 Windows autostart reads real registry state — **INTACT**

`Common/AutostartHelper.cs`, called from `ViewModels/SettingsViewModel.cs:177-179` inside
`LoadFromConfig()`:

```csharp
AutoStart = _designMode ? _config.GuiItem.AutoRun
                        : v2rayN.Desktop.Common.AutostartHelper.Reconcile(_config.GuiItem.AutoRun);
```

All three required behaviours verified:

- **Reads real registry state.** `IsEnabled()` (`:85-104`) returns true only when
  `HKCU\…\Run` value `departament` is non-empty **and** `IsDisabledInTaskManager()` is false.
- **Reconciles at startup.** `Reconcile(intended)` (`:168-180`) compares intent to `IsEnabled()`,
  calls `Apply()` on divergence, and returns the *actual* state afterwards — which is what the
  toggle then displays. `LoadFromConfig` runs in the ctor (`:113`) before `WirePersistence()`
  (`:114`), so the reconciled value is what the subscriptions see.
- **Clears the StartupApproved disable flag.** `Set()` (`:33-59`) calls
  `ClearStartupApprovedFlag()` (`:130-151`), which rewrites
  `HKCU\…\Explorer\StartupApproved\Run` value `departament` with an even first byte when the
  stored one is odd.
- The handler is also still deliberately **not** short-circuited on an unchanged flag
  (`OnAutoStartChanged`, `:263-288`, with the comment explaining why) — so a user whose registry
  drifted can fix it from the UI.

Neither fix has regressed. Both are load-bearing and invisible; keep them verbatim through any
rebuild of `MainWindow.axaml.cs` or `SettingsViewModel.cs`.

---

## 6. What the `pc-*` audits raised as P0/P1, and what is still open

The three audits were committed to `/home/user/dp` at 16:33 and 16:47 on 2026-07-26. Five desktop
commits landed after them (16:49 → 17:24). Those commits were **functional and copy fixes**, not
the design work the audits ordered. Below is what actually changed.

### P0 — 3 raised, 0 closed

| # | Audit | Item | State |
|---|---|---|---|
| P0-1 | `pc-servers-account` §2.1 | «Устройства» opens the **root** subscription's devices, not the selected card's | **OPEN, unchanged.** Chain re-traced end to end: `AccountViewModel.cs:2800` `DevicesCmd = ReactiveCommand.Create(owner.RequestDevices)` → `:552` `RequestDevices() => DevicesIntentRequested?.Invoke(this, EventArgs.Empty)` → `AccountView.axaml.cs:135` forwards `EventArgs.Empty` → `MainWindow.axaml.cs:251` `OpenDevices()` → `DevicesView.axaml.cs:25` `new DevicesViewModel()` **with no argument** → `DevicesViewModel.cs:98` falls back to `LoggedInProfileUuid()`. The ctor parameter `remnawaveUuid` exists and is documented as the Android `EXTRA_REMNAWAVE_UUID` mirror; the call chain still drops it. |
| P0-2 | `pc-home` W-01 | The connect control is not operable without a mouse | **OPEN, unchanged.** `ConnectHeroView.axaml:512` `#ConnectDisc` is a `Border` with pointer handlers only — no `Focusable`, no `IsTabStop`, no `KeyDown`, no `AutomationProperties.Name`. `grep 'Ctrl.*Enter\|Key.Enter' MainWindow.axaml.cs` = 0. The single action the app exists to perform has no keyboard path. |
| P0-3 | `pc-settings` §4.2 | «Автообновление подписки» configures **geo-file** updates, in the wrong unit | **OPEN, unchanged.** `SettingsViewModel.cs:461` still writes `_config.GuiItem.AutoUpdateInterval`; `ServiceLib/Manager/TaskManager.cs:113` is still its only consumer and still gates `UpdateGeoFileAll()` on **hours of process uptime**. `AutoUpdateOptions = [60,360,720,1440]` (`:36`) stored as-is means the "1 ч." option needs 60 hours of continuous uptime. Real subscription refresh is `SubItem.AutoUpdateInterval` (`TaskManager.cs:84-85`), which this row never touches. The copy pass renamed the label from «Автообновление подписки» to «Автообновление провайдеров» (`L.Settings.cs:65`) — the label moved, the wiring did not. |

### P1 — closed, partly closed, still open

**Closed:**

| Item | Evidence |
|---|---|
| Em/en dashes in shipped copy (`pc-servers-account` §2.11) | `grep -o '—\|–' Common/L.*.cs` = **0** across all seven L files (was 26) |
| Destination noun «Сервера» → «Серверы» (`§2.23`) | `L.Servers.cs:17` `Add("Servers_Title", "Серверы", "Servers")` |
| Servers empty-state **copy** (`§2.4`) | `L.Servers.cs:23-24` now ships the contract strings «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» |
| Local-proxy port silently reverting (`pc-settings` §4.4) | `SettingsViewModel.CommitLocalProxyAsync` now returns `bool` and sets `PortInvalid` — **but see §7, the flag has no reader** |
| TUN intent vs. capability | New `TunModeItem.TunUnavailable` / `EnableTunEffective` (`ConfigItems.cs:199-203`) with **real** readers: `CoreManager`, `ConfigHandler`, `CoreConfigContextBuilder`, `SingboxInboundService`, and a user-visible banner at `HomeView.axaml:56` / `CompactHomeView.axaml:61` |

**Partly closed:**

| Item | What landed | What did not |
|---|---|---|
| Server rows not focusable (`pc-servers-account` §2.2) | `ServerListView.axaml:157-158` now sets `Focusable="True" IsTabStop="True"`, so the row takes a tab stop, gets the `Border.ServerRow` focus adorner, and can raise its `ContextMenu` via Menu/Shift+F10 | **Enter/Space does not select a server.** The markup comment at `:147` says activation "остаётся за code-behind"; `grep 'Key.Enter\|Key.Space\|KeyDown' ServerListView.axaml.cs` = **0**. Selection is still mouse-only. No visible kebab either. |
| Servers empty state (`§2.4`) | copy fixed | **No action button.** `ServerListView.axaml:305-326` is icon + title + line. Title+line+action is the formula; two of three is not a state. |

**Still open (spot-verified, not taken on the audits' word):**

| Item | Evidence today |
|---|---|
| Russian strings in the Cyrillic-free brand face (`§2.3`) | `AccountView.axaml:85`, `:180`, `:267` still set `FontFamily="{DynamicResource Font.Grotesk}"` |
| No offline state anywhere (`§2.5`) | `grep -i 'offline\|Нет сети' Views/*.axaml Common/L.*.cs` = **0** |
| `PaymentHistoryView` / `DevicesView` unvirtualised (`§2.6`, `§2.21`) | `grep VirtualizingStackPanel Views/` returns exactly one hit, `ServerListView.axaml:121` |
| Icon-only controls unnamed (`§2.7`) | `AutomationProperties.Name` appears in **1** of 50 views (`PaymentHistoryView`) |
| Sub-page toolbar titles at `Headline` 24 instead of `Title` 16/700 (`§2.9`) | `DevicesView.axaml:125`, `PaymentHistoryView.axaml:55`, `BuyView.axaml:254`, `LoginView.axaml:255` |
| Content never capped at 720 (`§2.10`) | `grep 'MaxWidth="7[0-9][0-9]"' Views/*.axaml` = **0** |
| Press scale ≠ 0.97 (`§2.13`) | six distinct values, see §2 above |
| Raw hex in views (`§2.16`) | 6 sites: `AccountView.axaml:65,68` (`Value="#…"`), `SettingsView.axaml:78`, `ConnectHeroView.axaml:526`, `DevicesView.axaml:451`, `MainWindow.axaml:308` |
| Off-scale spacing (`§2.17`) | **91** off-scale `Margin`/`Padding`/`Spacing` values across `Views/` (allowed 0/4/8/12/16/24/32/68). Worst: `SubscriptionMetaView` 10, `AccountView` 9, `CompactServersView` 8, `RoutingSubView` 7 |
| Retired radius on the canonical row (`§2.15`) | `ServerListView.axaml:156` `CornerRadius="{StaticResource Radius.Search}"` |
| Inverted light-accent fallback comment (`§2.24`) | `ServerListView.axaml.cs:902` `_blueFallback = "#4C8DFF" // Brush.Accent (Light)` — `#4C8DFF` is the **dark** accent |
| `DelayDisplayConverter` renders `"—"` as a data value (`§2.11`) | `ServerListView.axaml.cs:884` unchanged |
| **Whole `pc-home` P1 block W-02…W-08** | `ConnectHeroView.axaml` untouched since 07-17. `#AmbientSonar` `:342`, `#AmbientRing` `:357`, `#GlowHalo` `:374`, `#RingOuter` `:403`, `#RingHoverGlow` `:416`, `#RingInner` `:426`, `#SonarPulseEcho` `:453`, `#HeroFrame` `:328` all present. `Brush.HomeGradient` still paints `HomeView.axaml:16`, `MainWindow.axaml:434,551`, `LoginView.axaml:237`, `OnboardingView`, `AccountSyncView`. |
| `pc-settings` §3 — hub structure | Unchanged: **6 named groups** (`SettingsView.axaml:223,522,641,686,875,972`) against the spec's 4 + footer; **22 named rows**; **8 reachable sub-pages** out of 17 spec routes (`SettingsView.axaml.cs:43-50`); **25 `Classes="Card"` across the settings tree** (6+3+3+3+1+1+2+1+2+3) against the spec's zero |
| `pc-settings` §4.1 — «Обход локальной сети» writes the opposite direction | `SettingsViewModel.cs:229` still `inbound.AllowLANConn = v` |
| `pc-settings` §4.3 — «Язык» cannot reach `Системный` | `:475` still `CurrentLanguage == "en" ? "ru" : "en"` |
| `pc-settings` §7 — the sign-in form | `LoginView.axaml` untouched. Segment is still **mode** («Вход»/«Регистрация», `:328-341`), not method. `EmailBox:351`, `PasswordBox:370`, `ConfirmPasswordBox:417` still have **no label element** — the `Watermark` is the label. Error slots still `IsVisible="False"` (`:352`, `:419`), so the form jumps. |
| `pc-settings` §8 — keyboard | The shell handler (`MainWindow.axaml.cs:1965-2025`) binds **only** Ctrl +/−/0, Ctrl+V, Ctrl+S, F5. **No `Key.Escape`, no `XButton1`, no Ctrl+F, no Ctrl+`,`** anywhere in `MainWindow.axaml.cs`. `Key.Escape` exists only inside `BuyView`, `DevicesView`, `ProfilesView`, `SubSettingWindow` — for their own inner modals, not for popping a sub-page. |
| `pc-settings` §3.2 — hardcoded Russian literal | `SettingsView.axaml:792` `Text="Масштаб интерфейса"` is still a literal, not a `loc:T` key — it stays Russian in English. Same shape at `StatusBarView.axaml:114,120`. |
| D-S5 label convergence | The copy pass moved strings without landing on the spec: `Settings_Monochrome` = «Чёрно-белый режим» (spec: «Чёрная тема»), `Settings_SubAutoUpdate` = «Автообновление провайдеров» (spec: «Автообновление подписок»), and `Settings_Mode` «Режим», `Settings_Ping` «Пинг», `Settings_Autostart` «Запуск при загрузке», `Settings_SecInterface` «Интерфейс», `Settings_SecSubscription` «Подписка», `Settings_SecPerformance` «Производительность», `Settings_Backup` «Резервное копирование», `Provider_Title` «Настройки провайдеров» are all unchanged from what the audit flagged. |

---

## 7. New defects found in this pass (not in any pc-* audit)

1. **`PortInvalid` is a write with no reader** — a fresh instance of the exact chronic defect.
   `SettingsViewModel.cs:76` declares it, `:410` sets it, and
   `grep -rn PortInvalid` over the whole solution returns **only those two lines plus a doc
   comment**. The doc comment is candid about it: "the inline caption that renders it is a markup
   change and is not part of this pass." Net effect: an out-of-range local-proxy port is still
   rejected in silence, exactly as `pc-settings` §4.4 described — the state moved from "not
   modelled" to "modelled and unrendered", which is not a fix.
2. **Mono/black theme leaks brand blue on every primary button** — the seven theme keys the token
   wave added were not mirrored into `BuildMonoOverlay`. Detail and evidence in §1.
3. **The promoted global `TextBox.IncyField` has zero live consumers** — shadowed by
   `SettingsView.axaml:68`. 75 lines of `GlobalResources.axaml` that no control resolves.
4. **`Common/Motion.cs`'s new members have zero call sites** — `Dur.Pulse`, `Dur.Spin`,
   `Dur.Debounce`, `Dur.RevealExit`, `Dur.StateExit`, `Dur.Hover`, `PressScale`, `Play()`,
   `StaggerFor()`. Verified by grep across all `.cs` and `.axaml` excluding `Motion.cs` itself.
   In particular `Motion.Play()` — the method written specifically so that "the right call is
   shorter than the wrong one" for reduced motion — is called nowhere.

---

## 8. What is right and must not be lost

Not everything here is debt. These are verified-correct and easy to destroy in a rebuild:

- **`grep 'StaticResource Brush\.' Views/` = 0.** Every brush in every view is `DynamicResource`,
  which is why live theme switching (including the mono overlay and the circular flood transition
  at `MainWindow.axaml:661-669`) actually works.
- **The two load-bearing fixes in §5**, verbatim.
- **Reduced motion is read at play time, not cached in a constructor.** `MotionState`
  (`Common/MotionState.cs`), the `.lite` window class, and `ServerListView.axaml.cs:400-428`
  reading it live. The toggle takes effect on the next frame, no restart.
- **Virtualisation where it matters most**, with the reasoning written down:
  `ServerListView.axaml:106-122` explains why a `VirtualizingStackPanel` nested inside a reveal
  `Border` still realises off the effective viewport.
- **Settings-row keyboard model**: `SettingsView.axaml.cs:107-139` `WireRow`/`WireToggleRow` give
  the **row** the tab stop and take the switch out of the tab order, with Enter/Space activation
  and a 2px accent `FocusAdorner` that survives lite mode. This is the correct desktop translation
  and it is the model `ServerListView` still needs.
- **The trial flag is trusted, never inferred.** `AccountViewModel.cs:689`
  `IsTrial = rootFromAll?.IsTrial ?? false` straight from `/subscription/all`;
  `grep -ric 'squad'` = 0 across the account/buy/devices view models.
- **Sub-page push is idempotent by type** (`MainWindow.axaml.cs:1118-1121`), so a double-click on a
  settings row no longer stacks two copies of the page and fires two network requests.
- **The keep-alive tab host** (`MainWindow.axaml.cs:231-236`): all four tabs stay measured and
  arranged; switching is a composited `Opacity`+`TranslateY` on an already-laid-out view.
- **Build hygiene**: 0 errors, 28 warnings = baseline exactly. The 28 are 14 `AVLN5001`
  `TextBox.Watermark` obsolescence hits, 9 `CA1416` on `UrlSchemesPage`'s registry calls, one
  `CS0067` unused event, and 4 in the vendored `GlobalHotKeys` submodule.

---

## 9. Summary judgement

| Layer | State |
|---|---|
| Design tokens (`GlobalResources.axaml`) | **done and wired**, minus 2 retired-radius migrations, 2 missing tokens, and the mono-overlay gap |
| Motion tokens (XAML table) | **done and wired** |
| Motion tokens (`Motion.cs` new members) | **built, unreachable** — 0 call sites |
| Class-style vocabulary (`GlobalStyles.axaml`, +1197 lines) | **built, unreachable** — 45 of 45 new names unused |
| `SettingsView` + its 8 sub-pages | **untouched since 07-17**; 25 cards, 6 groups, 8 of 17 routes, 3 rows that lie |
| `LoginView` | **untouched since 07-17**; every `14-auth.md` structural finding stands |
| `ConnectHeroView` / Home | **untouched since 07-17**; connect control still mouse-only |
| `AccountView` / `ServerListView` / `BuyView` | **defect-patched**, not reworked (12/15/11 lines) |
| «Серверы» destination | **spec only** — three tabs in code, two dead server views on disk |
| The two regression checks | **both intact** |
