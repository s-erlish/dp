# Audit 2026 - Desktop: servers and account surfaces

Scope: `v2rayN.Desktop` servers + account. Read-only audit, no source edited.
Working tree read 2026-07-26, branch `claude/app-audit-agents-hyyftk`, build root
`/home/user/v2rayN/v2rayN`.

Law applied: `docs/design2026/00-rules.md` sections 1-10, 12 (desktop translation), 13 (parity),
14, 15, 16, and the twelve ratified decisions in section 18.
Specs applied: `16-servers.md` **does not exist** in the tree at audit time, so the servers law is
taken from `24-tab-conformance.md` section 3.2 + D-09/D-10/D-11 and `33-master-plan-pc.md`.
Account law: `23-account-rework.md` (both platforms), `15-account-tab.md`, `21-account-survey.md`.

Files audited (with code-behind and view models):
`Views/ServersView`, `CompactServersView`, `ServerListView`, `ProfilesView`, `ProfilesSelectWindow`,
`AddServerWindow`, `AddServer2Window`, `AddGroupServerWindow`, `SubEditWindow`, `SubSettingWindow`,
`QrcodeView`, `AccountView`, `AccountSyncView`, `BuyView`, `DevicesView`, `PaymentHistoryView`;
`Common/FlagResolver.cs`, `Common/ProfileDisplay.cs`, `Common/L.Account.cs`, `Common/L.Buy.cs`,
`Common/L.Servers.cs`; `ViewModels/AccountViewModel.cs`, `BuyViewModel.cs`, `DevicesViewModel.cs`,
`PaymentHistoryViewModel.cs`, `HomeViewModel.cs`.

---

## 1. The canonical servers view - traced from what constructs each

This is the finding the audit was commissioned to produce. There are four candidate views. Only
**one** is alive, and it is not the one whose name says it is the servers tab.

| View | Lines (axaml / cs) | Constructed by | Verdict |
|---|---|---|---|
| **`ServerListView`** | 313 / 939 | `HomeView.axaml:35` (`<views:ServerListView x:Name="ServerList" Grid.Row="1" />`) and `CompactHomeView.axaml:91` | **CANONICAL. The only live server list in the desktop app.** |
| `ServersView` | 12 / 13 | **nothing** | **DEAD.** Zero constructors. |
| `CompactServersView` | 116 / 38 | **nothing** | **DEAD.** Zero constructors. |
| `ProfilesView` | 322 / 532 | `SimpleViewLocator.cs:29` registration only; no `ProfilesViewModel` is ever routed through `IDataTemplate.Build` in the Desktop shell | **DEAD (registered but unreachable).** Legacy upstream `DataGrid` view. |
| `ProfilesSelectWindow` | 129 / 148 | `SimpleViewLocator.cs:28`, resolved from `SubEditViewModel.cs:83`, `AddGroupServerViewModel.cs:113`, `RoutingRuleDetailsViewModel.cs:103` | **LIVE**, but only as a picker modal inside legacy editors. |

**Evidence.** `grep -rn "ServersView" --include=*.cs --include=*.axaml v2rayN/` returns 8 hits and
**every one of them is either the view's own `x:Class` / `partial class` declaration or a prose
comment**. Same for `CompactServersView`. There is no `new ServersView()`, no `<views:ServersView`,
no `<local:ServersView`, no locator registration, no `ViewFor` branch.

**Why they are dead by construction, not by accident.** `Views/BottomNavBar.axaml.cs:9-14` declares
`enum AppTab { Home, Settings, Account }` - three values. `MainWindow.axaml.cs:370 ShowTab` and
`:388 NavIndex` switch on exactly those three, and the comment at `MainWindow.axaml.cs:369` states
it outright: «Отдельной вкладки "Сервера" нет: серверы - часть "Главной"». The nav rail and the
bottom bar both have three items. There is no route that could reach a servers tab.

**Corroborated by the specs.** `24-tab-conformance.md:305` logs D-09 `ServersView.axaml` as a
«12 ln orphan - DELETE and re-author»; `:307` logs D-11 `CompactServersView` as
«HARVEST + DELETE»; `33-master-plan-pc.md:165` (finding F5) states
«`CompactServersView.axaml:90` is dead; the rail has three items».

**The consequence for any rebuild wave.** The target architecture in `24-tab-conformance.md` 3.2 and
`33-master-plan-pc.md:108` is a **fourth destination «Серверы» at rail index 1**, which does not
exist today. A wave that opens `ServersView.axaml` and restyles its 12 lines, or that restyles
`CompactServersView`, ships **zero pixels to the user**. The only file whose edits reach a screen
today is `ServerListView.axaml(.cs)`. `CompactServersView.axaml:88-113` holds the **only search
field for servers in the entire product** and it is unreachable - harvesting it is a feature
addition, not a restyle.

**One more live-versus-dead trap in the same area.** `SubscriptionMetaView` is live: it is the
per-group header inside `ServerListView.axaml:99`. `24-tab-conformance.md` D-08 splits it and
deletes its subscription-readout half as a duplicate of the Account card. Anyone restyling the
subscription readout must know it renders in two places today.

---

## 2. Work order

Severity per `00-rules.md` 17.2. Any section-1 ban hit and any missing section-15 state is at least
P1 by definition.

| # | Sev | Title | Files | Change | Spec ref | Risk |
|---|---|---|---|---|---|---|
| 1 | P0 | «Устройства» opens the ROOT subscription's devices, not the selected card's | `Views/DevicesView.axaml.cs:25`, `ViewModels/AccountViewModel.cs:552,2800`, `Views/AccountView.axaml.cs:125`, `Views/MainWindow.axaml.cs:250,1198-1203` | The per-card `DevicesCmd` (`AccountViewModel.cs:2800`) calls `owner.RequestDevices()`, which raises `DevicesIntentRequested` with `EventArgs.Empty` (`:552`). `AccountView.axaml.cs:125` forwards `EventArgs.Empty`; `MainWindow.OpenDevices()` does `new DevicesView()`; `DevicesView.axaml.cs:25` does `new DevicesViewModel()` with no argument, so `DevicesViewModel.cs:99` falls back to `LoggedInProfileUuid()` - the **root** profile uuid. Carry `card.RemnawaveUuidValue` through the whole chain (the ctor already accepts it and documents it as the Android `EXTRA_REMNAWAVE_UUID` mirror). | `23-account-rework.md` 9 (data contract), ecosystem rule "every subscription action scoped to the selected subscription's uuid" | Low. The parameter exists and is documented; only the call chain drops it. |
| 2 | P1 | Server rows are not focusable: seven actions are right-click-only, and selection is mouse-only | `Views/ServerListView.axaml:136-152`, `.axaml.cs:150-219,732-819` | The row is a bare `Border` with `PointerPressed`/`PointerReleased` handlers and a `ContextMenu`. No `Focusable`, no `IsTabStop`, no `KeyDown`, no focus ring. Make default / ping / edit / duplicate / share QR / share link / delete are reachable only by right-click. Add `Focusable="True"`, the 2px accent focus adorner, Enter/Space activation, and a visible row kebab. | 7.1 (focus mandatory), 12.2, 14.4, 14.8; `24-tab-conformance.md` 3.2 item 7 «a right-click-only path is undiscoverable»; D-09 «gains … a discoverable actions path» | Low. |
| 3 | P1 | Russian strings set in Space Grotesk, which maps zero Cyrillic codepoints | `Views/AccountView.axaml:85` (`Border.Row TextBlock.Title` - «Купить», «Устройства», «История платежей», «Выйти»), `:180` (`Button.MethodChip` - Russian payment-method labels), `:267` (avatar monogram, a Cyrillic initial) | All three set `FontFamily="{DynamicResource Font.Grotesk}"`. D-1/D-2: the vendored binary maps 735 codepoints and **zero** in U+0400-U+04FF, so these are no-ops handing the face to a per-OS fallback. Delete all three setters; the ramp class carries the face. | 5.1, 3.4, D-1, D-2 (18) | Low. |
| 4 | P1 | The servers empty state has no action, and its copy is not the specified copy | `Views/ServerListView.axaml:290-311`, `Common/L.Servers.cs:20-21` | Ships «Список пуст» + «Добавьте подписку, чтобы увидеть серверы» + **no action**. 9.5 mandates «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» / **«Добавить провайдера»**. Title + line + action is the formula; two of three is not a state. | 9.5, 15, `24-tab-conformance.md` 3.2 item 9, `:127` | Low. |
| 5 | P1 | No offline state on any of the seven audited screens | all seven | `grep -i 'offline\|Нет сети'` returns **0** across `ServerListView`, `AccountView`, `BuyView`, `DevicesView`, `PaymentHistoryView`, `AccountSyncView`, `CompactServersView`. 9.6 requires stale data kept and marked, network actions disabled, one quiet bar «Нет сети. Показаны последние данные.» with «Повторить». `23-account-rework.md` 7.2 already reserves `ContentControl x:Name="StatusBar"` for it. | 9.6, 15 | Medium - needs a shared bar component and a connectivity signal. |
| 6 | P1 | `PaymentHistoryView` renders an unbounded payment list with no virtualisation | `Views/PaymentHistoryView.axaml:69-73` | Plain `ItemsControl` over `Payments`. Payment history is the one collection in the product that grows without bound. 4.6: any list that can exceed ~20 items is virtualised; a non-virtualised long list is a P1 performance defect. Add `VirtualizingStackPanel`, as `ServerListView.axaml:119-122` already does correctly. | 4.6, 17.1 dim. 2 | Low. |
| 7 | P1 | Icon-only controls have no accessible name | `Views/AccountView.axaml` (9 `IconButton40`, **0** `AutomationProperties.Name`), `CompactServersView` (3 / 0), `BuyView` (0 / 0), `DevicesView` (0 / 0), `ServerListView` (0 / 0), `PaymentHistoryView` (1) | `ToolTip.Tip` is not an accessible name. 10.7 + 14.3 make an unnamed icon-only control a P1 defect. One `IconButton40` in `AccountView` has neither tooltip nor name (9 buttons, 8 tooltips). | 10.7, 14.3 | Low. |
| 8 | P1 | Fixed `Height` on controls that carry a label or that must grow at 200% DPI | `AccountView.axaml:172` (`Button.MethodChip Height=40`), `:620,:705,:761` (`Height="32"` shrinking an `IconButton40` below the 40 floor), `:565,:642,:660`, `PaymentHistoryView.axaml:154,196,238,291,327`, `DevicesView.axaml:287`, `CompactServersView.axaml:91` (`SearchPill Height=48`), `ServerListView.axaml:179` | R2 / 3.3: every control height is `MinHeight`, never `Height`. A fixed height clips a two-line Russian label at 200% DPI. The three `Height="32"` also break 7.2's 40px row/toolbar floor. | 3.3 (R2), 7.2, 14.5, `24-tab-conformance.md` D-13 | Low. |
| 9 | P1 | Sub-page toolbar titles are `Headline` 24, not `Title` 16/700 | `DevicesView.axaml:125`, `PaymentHistoryView.axaml:55`, `BuyView.axaml:254` | 4.8 fixes the seamless sub-toolbar title at `TextAppearance.App.Title` 16/700. Three views ship 24. | 4.8, 0.4.6, `23-account-rework.md` 7.6 | Low. |
| 10 | P1 | Content is never capped at 720 and centred; no gutter step at 1000px | all seven views (`grep 'MaxWidth="7[0-9][0-9]"'` = **0** hits) | 4.1 and 12.3: at window width >= 1000 the gutter steps 16 -> 24 and content caps at 720, centred. Nothing in the audited set does either; the account column stretches across a 1920px window. `23-account-rework.md` 7.2 specifies `MaxWidth="720"` explicitly. | 4.1, 12.3, `23-account-rework.md` 7.2 | Low. |
| 11 | P1 | Em-dash in shipped Russian copy (ban 1.4.11) | `Common/L.Account.cs` 20 lines, `L.Buy.cs` 3, `L.Servers.cs` 2 (`Sub_AutoUpdate`), `L.Common.cs` 1; plus `ServerListView.axaml.cs:884` which **returns an em-dash as a rendered value** | 26 grep hits total in the audited L files. `DelayDisplayConverter` returning `"—"` is the worst case: it is not copy, it is a value the user reads in a data column. Use a hyphen, or better, the word «нет». | 1.4.11, 9.2, 9.7; `33-master-plan-pc.md:980` already logs the converter case | Low. |
| 12 | P1 | Legacy upstream server editors ship English/multi-lang `ResUI` strings, `DataGrid`s and Semi themes | `AddServerWindow.axaml` (94 `ResUI`, 1388 ln), `AddGroupServerWindow` (32 `ResUI`, 22 `DataGrid`), `SubEditWindow` (29), `SubSettingWindow` (16, 14 `DataGrid`), `AddServer2Window` (14), `ProfilesSelectWindow` (12, 13 `DataGrid`, 1 `Theme=`), `ProfilesView` (52, 37 `DataGrid`, 2 `Theme=`) | Zero `loc:T` between them. Ban 1.4.10 forbids Latin UI text; 12.1 makes un-restyled default Fluent/Semi controls a defect by name. **Reachability traced, not assumed:** `ServerListView.axaml:157` «Изменить» -> `OnRowEdit` (`.axaml.cs:780`) -> `ProfilesViewModel.EditServerAsync()` (`ServiceLib/ViewModels/ProfilesViewModel.cs:514-551`), which branches on `ConfigType` into `AddServer2ViewModel` / `AddGroupServerViewModel` / `AddServerViewModel` and calls `WindowDialog.ShowDialogAsync`, resolved by `SimpleViewLocator.cs:15-17` to the three legacy windows. The same method surfaces the English `ResUI.PleaseSelectServer` notice at `:523`. So every one of these windows is one right-click away from the canonical list. | 1.4.10, 12.1, `24-tab-conformance.md` D-37, D-38 | High - full rebuild, spec'd as «REBUILD as one sub-page». |
| 13 | P1 | Press scale is 0.96 / 0.99, not 0.97; row press scales instead of stepping its background | `ServerListView.axaml:129` comment + the `Border.ServerRow.pressed` style, `AccountView.axaml:98-102` (`scale(0.99)` at `Duration="0:0:0.12"`) | D-11 fixes press scale at **0.97**, 90ms in `ease_out_quart` / 160ms out `ease_out_quint`. R5 additionally says **rows do not scale, objects do** - a row scaling inside a group tears the hairlines above and below it. Both audited surfaces scale rows. `0:0:0.12` is not on the motion scale at all. | 3.7, 7.1, 8.3, D-11 (18) | Low. |
| 14 | P2 | `zero` (slashed zero) is on for currency figures | `AccountView.axaml:320,375,460,1352`, `BuyView.axaml:370,407,415,465,485,523,687`, `PaymentHistoryView.axaml:105,119` | All 16 `FontFeatures` declarations are the identical string `tnum,lnum,zero`. D-3: `zero` **on for technical figures and off for currency**, because a slashed zero in a price reads as a symbol. The three `DevicesView` hits (`:140,:269,:277`) are technical counts and are correct as-is; the 13 money hits are not. | 5.5, D-3 (18) | Low. |
| 15 | P2 | Retired radius token still drawing the server row | `ServerListView.axaml:143` `CornerRadius="{StaticResource Radius.Search}"` (14px) | D-7 retires `Radius.Search`; a row/control shape is `Radius.Button` 16. The key survives in the token file only until the last reference migrates - this is one of them. (`StaticResource` is correct here: a radius is not a theme brush.) | 3.2, D-7 (18) | Low. |
| 16 | P2 | Two inline hex values the section-1.5 grep does not catch | `AccountView.axaml:65` `#3D7EF0`, `:68` `#3877E0` | Written as `<Setter Property="Background" Value="#3D7EF0"/>`, which the documented grep `'(Background\|Foreground\|...)="#'` misses because the attribute is `Value=`. They are `Brush.AccentHover` / `Brush.AccentPressed` verbatim. Also `DevicesView.axaml:451` `Background="#80000000"` = `Brush.Scrim`. **Add `Value="#` to the 1.5 grep** - the mechanical check currently under-reports desktop hex by 2 of 3. | 1.4.6, 1.5, `23-account-rework.md` 7.5 | Low. |
| 17 | P2 | Off-scale spacing, 28 values | `CompactServersView` 4 (`16,10,10,6`, `Spacing=10`, `Margin 14,0,10,0`, `0,0,10,0`), `ServerListView` 5 (`16,2`, `12,6`, `1.5`, `0,2,0,0`, `Spacing=14`), `AccountView` 5 (`6,0,0,4`, `Spacing=6` x2, `Spacing=20`, `Spacing=10`), `DevicesView` 8 (`10,4`, `16,10`, `8,3`, `0,3,0,0` x2, `68,0,0,0` x3), `BuyView` 1 (`4,0,0,1`), `ProfilesView` 1 (`Margin=2`) | 1.4.5 / 3.1: the scale is 4/8/12/16/24/32 plus the derived size tokens. `68` is the Android text origin and is legitimate as a divider inset per 4.1 - the `1.5` padding, `Margin=2` and `0,0,0,1` are not. | 1.4.5, 3.1 | Low. |
| 18 | P2 | Wrong semantic token for expiry urgency | `AccountView.axaml:127` uses `Brush.Icon.Orange` for `.urgent` | 1.4.1 names the warning token `color_warning` / `Brush.Amber` (with `Brush.AmberText` on light chips). `Brush.Icon.Orange` is part of the retired D-5 tile palette that new work does not use. | 1.4.1, 3.5, D-5 (18) | Low. |
| 19 | P2 | «Выйти» is mouse-only and unconfirmed | `AccountView.axaml:1373 LogoutRow`, `.axaml.cs:66` `LogoutRow.Tapped += ... LogoutCmd` | Sign-out **is reachable on desktop** (see section 6) but the row is a `Border` with a `Tapped` handler: no focus, no Enter/Space, no confirm, no undo. 7.5 wants undo or a confirm for a costly action; 14.8 wants every task completable without a mouse. | 7.5, 14.8, `23-account-rework.md` 7.3 | Low. |
| 20 | P2 | Two Semi-default `TextBox`es on the money and identity fields | `AccountView.axaml:372` (top-up amount), `:1223` (link email) | Neither carries a `Classes=`. 12.1: a control not restyled to the token set is a defect - «it will look like a different application». These two are the payment-entry and account-linking fields. | 12.1, `23-account-rework.md` 7.5 | Low. |
| 21 | P2 | `DevicesView` device list not virtualised; its unlink confirm is a modal | `DevicesView.axaml:169-170`, `:449-470` | Plain `ItemsControl`. A device list is normally < 20 but is not bounded by the client. The unlink path uses an in-view scrim + modal card (`x:Name="DeleteScrim"` `:450`, `Classes="Card ModalCard"` `:461`) where 7.5 wants remove-immediately plus a 5s undo toast (`Border.Toast` already exists). Escape and scrim-click do dismiss it, which 7.6 requires. | 4.6, 7.5, `23-account-rework.md` 7.6 | Low. |
| 22 | P2 | A persisted latency is redisplayed as if it were current | `ServiceLib/Models/Entities/ProfileExItem.cs:9` `public int Delay`, `[PrimaryKey]` on a SQLite entity; rendered by `ServerListView.axaml:245-254` | See section 7. The ping column shows a stored number with no measurement time and no staleness marker, so a host that has been unreachable for days still reads «48». | 15 (partial / stale), 9.6 | Medium - needs a measured-at timestamp or a session-scoped reset. |
| 23 | P2 | Terminology: the destination noun is «Сервера» | `Common/L.Servers.cs:15` `Add("Servers_Title", "Сервера", …)` | 9.3 locks «сервер»; every spec renders the plural destination «Серверы». `33-master-plan-pc.md:989` logs this exact string. Dead today (only `CompactServersView` binds it) but it is the string the new destination will inherit. | 9.3, 13 | Low. |
| 24 | P3 | Wrong light-theme accent in a converter fallback | `ServerListView.axaml.cs:902` `_blueFallback = "#4C8DFF" // Brush.Accent (Light)` | `#4C8DFF` is the **dark** accent; light is `#1E5FC7`. If `TryFindResource` ever misses on light, the ping value draws at 2.98:1 - the exact ratio R11 calls the only P1 defect in the token system. The comment is also factually inverted. | 3.5 (R11), 6.8 | Low. |
| 25 | P3 | Two dead files and one dead registration to remove with the rebuild | `Views/ServersView.axaml(.cs)`, `Views/CompactServersView.axaml(.cs)`, `Common/SimpleViewLocator.cs:29` | Delete after harvesting `CompactServersView.axaml:88-113` (the only server search field in the product) and its live-filter code-behind (`.axaml.cs:20-37`). | `24-tab-conformance.md` D-09, D-11 | Low. |

---

## 3. State matrix

Per `00-rules.md` 15. `y` implemented, `-` absent, `p` partial.

| State | ServerListView | AccountView | BuyView | DevicesView | PaymentHistory | AccountSyncView |
|---|---|---|---|---|---|---|
| Default | y | y | y | y | y | y |
| First run | p (empty doubles as it) | y (signed-out gate) | y | - | - | y |
| Loading (skeleton) | **-** | y | y | y | y | p (stage line) |
| Empty | p (no action) | y | y | y | y | n/a |
| Error | **-** | y | y | y | y | y |
| **Offline** | **-** | **-** | **-** | **-** | **-** | **-** |
| Partial | - | p | - | - | - | - |
| Long content | y (`TextTrimming`) | y | p | p | p | p |
| Short content | y | y | y | y | y | y |
| Disabled / gated | n/a | y (no subscription / expired) | y | p | p (empty CTA) | n/a |
| Success | n/a | y (220ms tint) | y | p | n/a | y |

Product-specific gates from 15: `нет подписки` y, `подписка истекает` y, `подписка истекла` y,
`триал` y, `Telegram не привязан` y, `нет серверов` p (no action), `лимит устройств` p,
`подключение`/`подключено`/`отключение`/`ошибка туннеля` live on `ConnectHeroView` (not this wave).

**The two structural gaps.** Offline is absent from all seven, which is one P1 (work item 5).
`ServerListView` has neither a loading skeleton nor an error state: subscription refresh failures
surface only through `SubscriptionMetaView`, so a list that fails to populate is
indistinguishable from an empty one.

---

## 4. Grep numbers

Run from `/home/user/v2rayN/v2rayN/v2rayN.Desktop` over the 16 audited `.axaml` files.

| Check (00-rules 1.5) | Result |
|---|---|
| `(Background\|Foreground\|BorderBrush\|Fill\|Stroke)="#` | **1** - `DevicesView.axaml:451` `#80000000` |
| `Value="#` (the form the documented grep misses) | **2** - `AccountView.axaml:65,68` |
| `StaticResource Brush.` | **0** - clean, keep it clean |
| `FontFamily=` / `FontSize=` | **18** - 3 `Font.Grotesk` (P1, work item 3), 14 `Font.Numeric` (benign but should ride the `Numeric` ramp class), 1 inline `FontSize="20"` (`AccountView.axaml:268`) |
| `FontWeight=` inline | **6** |
| Off-scale `Margin`/`Padding`/`Spacing` values | **28** across 6 files (work item 17); 10 of the 16 files are clean |
| `Duration="0:0:…"` literals | 7x `0.15` (Exit, valid), 6x `0.22` (State, valid), 2x `0.30` (Reveal, valid), 2x `0.09` (PressIn, valid), **3x `0.12` (off-scale)** |
| `MaxWidth="720"` | **0** |
| `AutomationProperties.Name` on icon-only controls | **1** across all 16 files, against 12 `IconButton40` instances |
| `VirtualizingStackPanel` | **1** - `ServerListView.axaml:121` |
| `<ScrollViewer` per view | ServerList 1, Account 2, Buy 1, Devices 1, History 1, CompactServers 0, AccountSync 0 |
| Nested `Border.Card` inside `Border.Card` | **0** - verified by an XML-nesting pass over all seven, not by grep. Clean. |
| `resx:ResUI` (legacy upstream English strings) | **249** across 7 legacy files; **0** in the seven Incy views |
| `loc:T` (Russian L strings) | **105** across the seven Incy views; **0** in the 7 legacy files |
| `DataGrid` | **86** across `ProfilesView`, `ProfilesSelectWindow`, `AddGroupServerWindow`, `SubSettingWindow` |
| Em/en dash in `Common/L.{Account,Buy,Servers,Common}.cs` | **26** |

---

## 5. Ban hits (00-rules section 1)

| Ban | Hit | Where |
|---|---|---|
| 1.4.11 no em-dash / en-dash | **yes, 27** | `L.Account.cs` 20, `L.Buy.cs` 3, `L.Servers.cs` 2, `L.Common.cs` 1, plus `ServerListView.axaml.cs:884` rendering `"—"` as a data value |
| 1.4.6 no raw colour literals in views | **yes, 3** | `AccountView.axaml:65,68`; `DevicesView.axaml:451` |
| 1.4.5 no off-scale spacing | **yes, 28** | section 4 |
| 1.4.10 no Latin UI text | **yes, 249** | the 7 legacy `ResUI` windows (work item 12) |
| 1.4.13 no screen without its states | **yes** | offline absent x7; `ServerListView` has no loading and no error |
| 5.1 / D-1 / D-2 no Russian in the brand face | **yes, 3** | `AccountView.axaml:85,180,267` |
| 1.4.2 no nested cards | no | verified clean |
| 1.1 side-stripe borders | no | the `.rowDivider` is a 1px horizontal hairline, not a side stripe |
| 1.1 gradient text / gradient fills | no in this set | `TrafficFillBrush` lives in `SubscriptionMetaView`, outside this audit |
| 1.1 glassmorphism | no | |
| 1.1 hero-metric template | no | exactly one `Display` on the whole account tab (`AccountView.axaml:318`, the balance) - correct |
| 1.1 identical card grids | borderline | `PaymentHistoryView` is a list of identical `Border.Card`s (`:76`); 4.4 + 2.4.3 say a divided list belongs here. `23-account-rework.md` 7.6 already replaces it with `LedgerRow`. |
| 1.1 ALL-CAPS tracked eyebrow | no | |
| 1.1 numbered section scaffolding | no | |
| 1.4.4 no emoji as UI chrome | no | `StripLeadingFlagConverter` actively **removes** a user-supplied flag emoji from the remark so it does not double the flag tile - correct handling |
| 1.4.1 no second accent hue | borderline | `Brush.Icon.Orange` on the expiry line (work item 18) |
| 1.4.3 no decorative gradients or glows | no | |
| 1.4.7 no `ToUpper()` | no | |
| 1.4.9 no dialog for an inline decision | **yes, 2** | `DevicesView` modal unlink confirm; `AddServerWindow`/`SubEditWindow` as modal `Window`s where 7.6 wants a sub-page |
| 1.4.12 no new icon family | no | `Geo.Acc.*` are ports of the Android drawables, one stroke weight. But 16 of them are re-declared locally in `AccountView.axaml:34-51` instead of living in `GlobalResources`, and `Geo.Acc.Chevron` (`:42`) duplicates the global `Geo.ChevronRight` byte-for-byte |

---

## 6. The two questions the brief asked to settle

### 6.1 Is the link-Telegram CTA reachable on desktop?

**Yes.** `docs/agents/verify-link-telegram-cta-unreachable.md` confirmed the CTA is dead **on
Android** (`MainActivity.kt:1093` writes `header.groupLogin.isVisible = false` unconditionally, and
the predicate at `:1128` requires `!isLoggedIn()` while the parent is visible only when
`isLoggedIn()` - mutually exclusive). That finding does **not** transfer to desktop.

Desktop's path is different and live: `AccountView.axaml:1062-1120` renders a Telegram row inside
the signed-in «Вход» card with three mutually-exclusive branches - linked (`TelegramLinked`, shows
the chip + `@id`), pending (`TelegramLinkPending`, shows the code pill + «Открыть бота»), and
**«Привязать»** (`:1117-1119`, `Command="{Binding LinkTelegramCmd}"`, `IsVisible="{Binding
TelegramCanLink}"`). `AccountViewModel.cs:210` documents `TelegramCanLink` as «show the «Привязать»
action only when Telegram is neither linked nor pending», and it is set from the `/me` response.
The row sits inside a `Border.Card` that is itself gated on the signed-in state, which is the
correct gate - unlike Android's, this predicate and its ancestor are compatible.

**Parity consequence.** Owner standing request 0.4.9 («Привязать Telegram» CTA where the account
state calls for it) is **satisfied on desktop and not on Android**. Desktop is the reference for
the Android fix. Log it as a parity gap, not as a desktop defect.

### 6.2 Does desktop have a reachable sign-out?

**Yes.** `AccountView.axaml:1370-1400` declares `Border x:Name="LogoutRow"` as a deliberately
separate quiet row below the navigation card, at `MinHeight="{StaticResource Size.Row}"` 56, with
`Text="{loc:T Account_SignOut}"` («Выйти», `L.Account.cs:33`) and a red-**text**-only treatment
(`AccountView.axaml:113` sets `Brush.RedText` on the title, with no red fill and no red tile).
`AccountView.axaml.cs:66` wires `LogoutRow.Tapped` to `AccountViewModel.LogoutCmd`
(`:289`, `:348`, implementation at `:1289`).

The visual treatment is right and matches 7.5's «quiet destructive» reading. Two defects remain
(work item 19): the row is not focusable, so sign-out is unreachable by keyboard, and there is no
confirm and no undo.

**Parity consequence.** Desktop is the parity reference for the Android sign-out wave. The Android
implementation should copy: separate row outside the navigation group, 24 gap above, red text only,
`Size.Row` 56, the string «Выйти», and it should **not** copy the missing keyboard path.

---

## 7. The five topics the brief flagged, resolved

| Topic | Finding |
|---|---|
| **Which servers view is canonical** | `ServerListView`. `ServersView` and `CompactServersView` have zero constructors; `ProfilesView` is registered but unroutable. Section 1. |
| **The unified server icon** | **Violated.** 10.5 specifies «the flag tile at 28 inside the standard 40 tile slot, falling back to the globe glyph». `ServerListView.axaml:174-190` draws a **40x40 fully circular** tile (`CornerRadius="20"`, `Padding="1.5"`) with the flag stretched `UniformToFill` to the full 40 - not 28 inside 40, and the radius is `Pill` where 3.2 puts flag tiles at `Radius.Tile` 12. The globe fallback is data-driven and correct (`FlagResolver` falls back to `xx.png`; `Geo.Globe` is declared at `ServerListView.axaml:31` and **referenced nowhere else in the file** - the fallback is the PNG, not the glyph, so the declared geometry is dead weight). Since `ServerListView` is the only live surface, «unified» is currently unfalsifiable; it becomes a real risk the moment the new destination and the connect hero draw the same server. |
| **Virtualisation** | **Correct where it matters most, missing where it matters next.** `ServerListView.axaml:119-122` puts the per-group rows on a `VirtualizingStackPanel`, and the 14-line comment at `:106-118` correctly explains that Avalonia realises off the *effective viewport*, so nesting inside the reveal `Border` does not defeat it. The outer `ServerGroups` `ItemsControl` is deliberately non-virtualised (providers are few) - acceptable. `PaymentHistoryView` (unbounded) and `DevicesView` are **not** virtualised: work items 6 and 21. |
| **Selection versus connection** | **Conflated, and the copy hides it.** `ServerListView.axaml.cs:190` - a left-click release over a row calls `HomeViewModel.SelectServer(item.IndexId)`, which the class comment at `:28` describes as «a row tap selects + connects the server». There is no select-without-connect. Meanwhile the context menu's first item is «Сделать основным» (`OnRowMakeDefault` -> `SetDefaultServer`), which is what a plain click already did. Two labels, one behaviour, and the destructive one is the undocumented default. 24-tab-conformance 3.2 item 6 additionally requires selection to read on **two** channels (12% accent fill **and** a check glyph replacing the ping value); today it is the fill plus a suppressed divider (`ServerListView.axaml:265`, `IsVisible="{Binding !IsActive}"`), which is tint plus an absence - not two positive channels. |
| **Fake latency for an unreached host** | **Two halves, one clean, one not.** Clean: `DelayResultConverter` (`.axaml.cs:843`) gates visibility on a parseable integer, so the engine's «Testing…» placeholder never renders as text (a spinner takes the slot at `ServerListView.axaml:229-247`), and `DelayDisplayConverter` (`.axaml.cs:877`) maps the core's `-1` failure sentinel to a non-numeric marker rendered in `Brush.OnSurfaceVariant` (`DelayInkConverter`, `.axaml.cs:908`) rather than as a latency. **Not clean**: `ProfileExItem.Delay` (`ServiceLib/Models/Entities/ProfileExItem.cs:9`) is a column on a `[PrimaryKey]`-keyed persisted SQLite entity, so a reading survives app restarts and is redrawn identically to a fresh one at `ServerListView.axaml:248-259`. A server that has been down since last week still shows «48» with no measured-at and no staleness treatment. Work item 22. |
| **Per-item actions: flyout vs modal** | **Half right.** Row actions are an anchored `ContextMenu` (`ServerListView.axaml:148-165`), which is the correct desktop translation per 13 - but it is right-click-only (work item 2), and three of its seven items escalate to a **modal `Window`**: «Изменить» -> `AddServerWindow` (1388 lines), and the provider path -> `SubEditWindow`. 7.6 permits a modal window «only for genuinely separate tasks», and `24-tab-conformance.md` D-37/D-38 re-specify both as sub-pages. |
| **AccountView's real size and the split seams** | 1474 lines of AXAML + 524 of code-behind, against a 2860-line `AccountViewModel`. Counted, not estimated: **26 view-local `<Style Selector>` rules** (first `:57`, last `:232`) and **16 locally re-declared `<StreamGeometry>` keys** (`:34-51`). Structural blocks: hero card `:253-340`, four-panel top-up flyout `:391-480`, drag/snap/tween subscription carousel `x:Name="SubList"` `:532` onward, upgrade wizard `:602-800` (`UpgradeTargets` at `:722`), sign-in-methods «Зона 3» `:1053-1270`, navigation card `:1280`, logout row `:1373`. The spec's seams are explicit and I concur with all four: (a) **«Способы входа» -> a new `LinkingView` sub-page** - `:1053-1270` is self-contained and is the cleanest cut; (b) **the carousel -> a switcher plus one card**, deleting all drag/snap/tween code (`23-account-rework.md` 7.1, 7.4); (c) **the row vocabulary -> one templated `RowItem`** (7.3), which is what removes the 26 local style rules; (d) **the geometries -> `GlobalResources`**, deleting `Geo.Acc.Chevron` (`:42`) as a byte-identical duplicate of the global `Geo.ChevronRight` (`Assets/GlobalResources.axaml:461`) - both are `M8.6,4.6L7.2,6l6,6l-6,6l1.4,1.4L16,12z`, verified. |
| **The card rule and nested cards** | **Clean on nesting** (0 hits, verified structurally). **Not clean on the card count**: 4.4 says «an account screen is one card (the subscription) plus rows», and `AccountView` ships **8** `Border.Card`s (`:253` hero, `:489` skeleton, `:540` per-carousel-card, `:994`, `:1024`, `:1059`, `:1280`, `:1405`). `PaymentHistoryView` ships a card per transaction (`:76`), which is the 2.4.3 uniform-card tell. |
| **The trial rule** | **Correct, and correctly sourced.** `AccountViewModel.cs:689` takes `IsTrial = rootFromAll?.IsTrial ?? false` straight from the `/subscription/all` DTO. There is **no** squad-name or tariff-name inference anywhere in the desktop account code: `grep -ric 'squad'` returns **0** for `AccountViewModel.cs`, `BuyViewModel.cs` and `DevicesViewModel.cs`. The flag is then used as a gate, not a label: `:2322` `canBuyDevices = !sub.IsTrial …`, `:2374` suppresses the upgrade price path for a trial, `:2115-2117` renders the «Account_TrialPeriod» caption. This matches the ecosystem rule that the trial squad *is* the paid base squad and only the backend can tell them apart. **No defect.** |
| **Per-subscription uuid scoping** | **One P0 hole, otherwise correct.** Upgrade is scoped (`:1671` `card.RemnawaveUuidValue`), price resolution is scoped (`:2321` `sub.RemnawaveUuid`). Devices is **not**: the whole `RequestDevices` -> `DevicesIntentRequested` -> `DevicesRequested` -> `OpenDevices` -> `new DevicesViewModel()` chain drops the card, and `DevicesViewModel.cs:99` falls back to the root profile uuid. Work item 1. |
| **₽ and tabular figures** | `₽` is used, «RUB»/«руб.» never reach the UI (`BuyViewModel.cs:712-716` maps ISO -> symbol; `AccountViewModel.cs:2543` documents the RUB-only rule). Tabular and lining figures are on everywhere they should be. The single defect is `zero` being on for currency against D-3 (work item 14). Money is typeset well: `AccountView.axaml:315-336` sets the amount `Classes="Display Numeric"` (`:318`) with the `₽` one ramp step down (`Classes="Headline"`) in `OnSurfaceVariant` - which is exactly what 5.5 and `23-account-rework.md` 4.1 ask for. |

---

## 8. Parity gaps against Android (00-rules section 13)

Section 13 makes destinations, order, strings, defaults and states identical by contract. Each row
below is therefore a defect or a logged gap; the verdict column says which.

| # | Gap | Verdict |
|---|---|---|
| 1 | **Destination set differs.** Android has a Servers destination in its bottom navigation; desktop's rail has three items and `AppTab` has three values (`BottomNavBar.axaml.cs:9-14`). Servers live inside Главная on desktop only. | **Defect.** 13 makes the destination set identical. Both platforms converge on Главная · Серверы · Настройки · Аккаунт (`33-master-plan-pc.md:108`), so this is desktop's to fix. |
| 2 | **Server search exists on neither platform**, and desktop's only implementation is in a dead file (`CompactServersView.axaml:88-113`). | **Logged gap** in `24-tab-conformance.md` 3.2 item 2, which calls it «the **first** server search in the product on either platform». Not a regression; a shared feature debt. |
| 3 | **Per-item action surface.** Android's `ServerActionsSheet` is reachable by long-press - except `MainRecyclerAdapter.kt:232` binds only `setOnClickListener`, so the callback assigned at `MainActivity.kt:651-652` is never invoked. Desktop's context menu **is** wired, but right-click-only. | **Defect on both.** Android's is a P0 rewire (`24-tab-conformance.md` 3.2 item 7); desktop's is work item 2. Neither platform can reach these actions the way its user expects. |
| 4 | **«Привязать Telegram» CTA.** Reachable on desktop (`AccountView.axaml:1117`), doubly unreachable on Android (`verify-link-telegram-cta-unreachable.md`). | **Defect on Android.** Desktop is the reference. |
| 5 | **Sign-out.** Reachable on desktop (`AccountView.axaml:1373` + `.axaml.cs:66`), absent on Android. | **Defect on Android**, being closed by a parallel wave. Desktop is the reference for placement, treatment and string. |
| 6 | **Empty-state copy for servers.** Desktop ships «Список пуст» / «Добавьте подписку, чтобы увидеть серверы» / no action. The contract string (`24-tab-conformance.md:127`, identical for both platforms) is «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» / «Добавить провайдера». | **Defect.** Work item 4. |
| 7 | **Destination noun.** `L.Servers.cs:15` «Сервера» against the spec's «Серверы» everywhere. | **Defect.** Work item 23. |
| 8 | **Press scale.** Android `press_scale.xml` 0.96, `nav_press.xml` 0.92; desktop `ServerRow` 0.96 and `Border.Row` 0.99 at 120ms. D-11 fixes one number, 0.97, on both. | **Defect on both.** Four renderings of one gesture. |
| 9 | **Server row icon.** Android `item_recycler_main.xml` uses an emoji flag; desktop uses a 40px circular PNG tile. Neither is the 28-in-40 tile 10.5 specifies. | **Defect on both**, and the reason the «unified server icon» request (0.4.7) is not yet satisfiable. |
| 10 | **Offline state.** Absent on desktop across all seven audited screens. Android's coverage is outside this audit's scope, but 9.6 binds both. | **Defect.** Work item 5. |
| 11 | **Devices sub-page uuid.** Android passes `EXTRA_REMNAWAVE_UUID` (the desktop ctor doc at `DevicesViewModel.cs:92-95` says so explicitly); desktop drops it. | **Defect on desktop.** Work item 1 - and it is a behavioural divergence on the same backend, not a cosmetic one. |

---

## 9. Five-dimension score (00-rules 17.1)

Ship bar: **>= 18/20, no dimension below 3**.

| Dimension | Score | Reasoning |
|---|---|---|
| 1. Accessibility | **1** / 4 | Server rows and the sign-out row are not focusable, so seven server actions and sign-out are unreachable without a mouse (14.8). One `AutomationProperties.Name` across 16 files against 12 icon-only buttons (14.3). Fixed `Height` on 13 controls, three of them shrinking a 40 hit box to 32, clips at 200% DPI (14.5, 7.2). Russian text handed to a Cyrillic-free face on three surfaces (5.1). Offline never announced. What saves it from 0: contrast comes from the token set throughout, `TextTrimming` is used rather than clipping, and reduced motion is honoured live and correctly (`ServerListView.axaml.cs:402-428` reads the `.lite` class **at play time**, exactly as 12.5 requires). |
| 2. Performance | **2** / 4 | The hardest list in the app is virtualised and the reasoning is documented and right (`ServerListView.axaml:106-122`). The collapse accordion, the one-shot reveal stagger and the `ConditionalWeakTable` replay guard (`.axaml.cs:248`) are careful work. Against that: `PaymentHistoryView` renders an unbounded list unvirtualised (P1), `DevicesView` likewise, and each realised server row instantiates its own 9-item `ContextMenu` from the `DataTemplate`. |
| 3. Appearance and theming | **2** / 4 | `StaticResource Brush.*` is **0** - live theme switching is safe, and that is the single most valuable thing in the file set. Cards are never nested. One `Display` per screen. Money typesetting is genuinely good. Against that: 3 raw hex, 28 off-scale spacing values, a retired radius token still drawing the canonical row, `zero` on for currency, `Brush.Icon.Orange` for a warning, 26 local style rules and 16 duplicated geometries in one view, and Semi defaults leaking through two `TextBox`es and every legacy window. |
| 4. Platform conformance | **1** / 4 | Two of the four servers views are dead files, a third is an unreachable registration - a reviewer cannot tell from the file names which one ships. Seven reachable windows carry English `ResUI` strings, 86 `DataGrid` references and Semi themes, so the server editor looks like a different application from the tab that opens it (12.1 names this a defect). Per-item actions are right-click-only and escalate to modal windows where 7.6 wants sub-pages. Selection and connection are the same gesture with two different labels. |
| 5. Adaptivity | **2** / 4 | Zero `MaxWidth="720"`, so content stretches to any window width (4.1, 12.3); no 1000px gutter step. Fixed heights break the 200% DPI case. `AccountView` has two `ScrollViewer`s against 12.3's one-per-view. Credit where due: the compact/wide layout swap is real and the keep-alive host preserves scroll and tab across it, the `UiScaleState` DPI host exists, and the trailing-spacer clearance in `ServerListView.axaml:276-284` is a correct fix for a real Avalonia extent bug rather than a magic padding constant. |
| **Total** | **8 / 20** | Below the ship bar, and four of five dimensions are below 3. |

Consistent with `24-tab-conformance.md`, which schedules these surfaces as REBUILD (D-13 Аккаунт,
D-37/D-38 the editors) and RESTYLE-rehosted (D-09/D-10 Серверы) rather than as polish passes.

---

## 10. Verification notes

- No source file was edited. This document is the only file written.
- Line numbers were read from the working tree on 2026-07-26 and each cited claim was opened, not
  inferred from a grep hit alone.
- The nested-card result is from an XML nesting pass over all seven Incy views, not a grep - a
  `grep -A40` count of card tags cannot distinguish nesting from adjacency.
- `16-servers.md` was absent at audit time. If it lands and contradicts section 3.2 of
  `24-tab-conformance.md`, the newer file wins under 0.1 and section 1 of this audit should be
  re-checked against it. The canonical-view determination in section 1 is derived from the code and
  is independent of which spec is authoritative.
- The section-1.5 desktop grep for inline hex misses the `<Setter Value="#…"/>` form. It found 1 of
  the 3 real hits in this file set. Recommend amending the rule's grep.
