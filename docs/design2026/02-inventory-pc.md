# 02 — Current-state inventory, PC (Windows / Linux desktop)

**Repo:** `/home/user/v2rayN` (fork of v2rayN) · **UI project:** `/home/user/v2rayN/v2rayN/v2rayN.Desktop`
**Stack:** C# .NET + Avalonia 11 + ReactiveUI + Semi.Avalonia theme + DialogHost.Avalonia
**Shared core:** `/home/user/v2rayN/v2rayN/ServiceLib` (view-models, engine, `ResUI` resx strings)
**Date of inventory:** 2026-07-26
**Method:** read every `.axaml` in `v2rayN.Desktop/Views/` (56 files) plus their code-behind,
`App.axaml` / `App.axaml.cs` (707 ln), `Assets/GlobalStyles.axaml` (1 448 ln),
`Assets/GlobalResources.axaml` (569 ln), `Common/L*.cs` (1 097 ln), `Common/SimpleViewLocator.cs`,
`Manager/WindowDialog.cs`, `ViewModels/*`, and cross-checked every view's reachability against
`ServiceLib/ViewModels/*.cs`.

> There is no `Views/Styles/` or `Views/Themes/` directory in this project. The theming that the
> brief calls "Styles/ and Themes/" physically lives in **two files**: `Assets/GlobalResources.axaml`
> (tokens + `ControlTheme`s + `ResourceDictionary.ThemeDictionaries`) and `Assets/GlobalStyles.axaml`
> (component styles). A third theme layer — the monochrome/AMOLED overlay — is built **in C#** at
> `App.axaml.cs:580 BuildMonoOverlay(bool light)`. Anyone looking for `Themes/` will not find it;
> this is worth fixing as a file-layout matter alone.

---

## 0. Executive verdict (read this first)

The PC app is **two applications sharing one window**, and the seam is visible the moment a user
touches anything that edits a server.

| Stratum | What it is | Where | Share of surfaces |
|---|---|---|---|
| **A — "Incy" 2026 layer** | The whole shell + Home + Settings tab + 8 settings sub-pages + Account + Buy + Devices + Payment history + Login + Onboarding + Sync. Token-driven, Russian sentence-case, Space Grotesk, motion tokens, every state designed. | `MainWindow`, `HomeView`, `CompactHomeView`, `ConnectHeroView`, `ServerListView`, `SubscriptionMetaView`, `HomeAccountChip`, `BottomNavBar`, `SettingsView` + `PerAppProxyPage`/`DnsSubView`/`PingSettingsPage`/`RoutingSubView`/`GeoFilesPage`/`AboutPage`/`BackupPage`/`UrlSchemesPage`, `AccountView`, `BuyView`, `DevicesView`, `PaymentHistoryView`, `LoginView`, `OnboardingView`, `AccountSyncView`, `MessageBoxDialog` | 26 surfaces |
| **B — raw upstream v2rayN** | Every server/subscription editor, the whole "geek" configuration surface, and the QR / sudo / log dialogs. Chinese-origin `ResUI` resx strings (no Russian), Semi default look, `TabControl` + `DataGrid`, 900×600 OS-decorated windows, `Width="100"` buttons. | `AddServerWindow` (1 388 ln), `AddServer2Window`, `AddGroupServerWindow`, `SubEditWindow`, `SubSettingWindow`, `OptionSettingWindow` (1 206 ln), `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow`, `ProfilesSelectWindow`, `FullConfigTemplateWindow`, `GlobalHotkeySettingWindow`, `JsonEditor`, `QrcodeView`, `SudoPasswordInputView`, `MsgView` | 15 surfaces |
| **C — dead code** | Built, styled, compiled, unreachable. | `ServersView`, `CompactServersView`, `ProfilesView`, `ClashProxiesView`, `ClashConnectionsView`, `ThemeSettingView`, `CheckUpdateView`, `BackupAndRestoreView`, `ProviderSettingsPage`, `StatusBarView` (mounted at 0×0), `MsgView` | 11 surfaces |

**The headline facts:**

1. **Right-click any server → "Изменить" opens a 900×600 Chinese-string window.** That is
   `AddServerWindow.axaml` (1 388 lines, 94 `resx:` references, 0 Incy classes,
   `ServerListView.axaml:154` → `ServerListView.axaml.cs:780` → `ProfilesViewModel.EditServerAsync()`
   → `ServiceLib/ViewModels/ProfilesViewModel.cs:527`). The 2026 redesign stops dead at that click.
2. **There is no server search anywhere in the shipping app.** The only search field
   (`CompactServersView.axaml:90`, bound to `Profiles.ServerFilter`) lives in a view that nothing
   instantiates. With 80–150 servers per subscription this is a functional hole, not a polish item.
3. **There is no user-visible feedback surface at all.** The toast (`MainWindow.axaml:623 snackHost`)
   is a deliberate permanent no-op; `DelegateSnackMsg` (`MainWindow.axaml.cs:1765`) reroutes every
   message to `NoticeManager.SendMessage` → `MsgViewModel` — and **`MsgView` is never mounted
   anywhere in the shell**. Clipboard-import failures, subscription-update results and engine errors
   are written to a log surface that has no window. The code comment claims an "inline message panel"
   exists. It does not.
4. **Escape does not go back.** `_subStack` (`MainWindow.axaml.cs:74`) is popped only by the toolbar
   `←` button. `Key.Escape` is handled in exactly four places, all local modals
   (`DevicesView.axaml.cs:47`, `BuyView.axaml.cs:167`, `SubSettingWindow.axaml.cs:100`,
   `ProfilesView.axaml.cs:309`). Nothing pops the sub-page stack on Escape or on mouse-back.
5. **The design law's absolute ban on decorative gradients and glows is violated on every screen.**
   `Brush.HomeGradient` (a radial gradient, `GlobalResources.axaml:88`) is the background of the
   entire shell (`MainWindow.axaml:434`, `:551`), Home, Onboarding, Login and AccountSync.
   `Brush.ConnectGlow` (`GlobalResources.axaml:269`) is a radial halo behind the connect shield,
   with an ambient breathing loop on top (`ConnectHeroView.axaml:342–470`).
6. **Settings has no measure.** `SettingsView.axaml:216` is a bare `ScrollViewer` with **no
   `MaxWidth`**, while Account/Buy/History/Devices all clamp to 560 and the settings sub-pages clamp
   to 620. At the app's own 1120×760 "wide" preset the settings rows run ~1030 px edge to edge with
   a 40 px tile hard left and a value hard right — an unreadable scan line, and inconsistent with
   every other tab.

The token system itself is **good** — genuinely one scale, one accent, real motion tokens, real
theme dictionaries, near-zero hardcoded hex. The problem is not the tokens. It is (a) 15 unconverted
upstream windows sitting one click away, (b) a dead-code layer that hides real features, (c) a
shell whose brand layer is built out of exactly the effects the design law forbids, and (d) an
auth screen that has grown 20 buttons and 5 fields on one page.

---

## 1. Complete screen / page / dialog inventory

### 1.0 The application object

**File:** `App.axaml` (49 ln) + `App.axaml.cs` (707 ln)

- `RequestedThemeVariant="Dark"` — dark is the default and the fallback.
- Style stack, in order: `semi:SemiTheme` → `semi:AvaloniaEditSemiTheme` → `Assets/GlobalStyles.axaml`
  → `Semi.Avalonia.DataGrid` → `dialogHost:DialogHostStyles`. GlobalStyles loads **after** SemiTheme,
  which is why so many rules in it exist purely to un-do Semi's hover tinting (see §3.4).
- **Tray icon + native menu** (`App.axaml:26–48`): 4 items, Russian sentence-case —
  «Перезапустить» · «Подключить»/«Отключить» (label follows live core state) · «Показать» · «Выход».
  OS-drawn; cannot be Incy-styled. Correctly scoped.
- **Theme application**: `ApplyTheme(string? theme, bool black)` at `App.axaml.cs:505`.
  `Dark`/`Light` map to `ThemeVariant`; `black` is a **separate monochrome overlay**
  `ResourceDictionary` appended to `Application.Resources.MergedDictionaries`
  (`ApplyMonoOverlay`, `:544`; `BuildMonoOverlay`, `:580`) that redefines ~30 `Brush.*` keys to
  greyscale, including collapsing `Brush.Accent` to grey. So the product really has
  **three themes**: Тёмная, Светлая, Чёрная (AMOLED, mono).
- `ThemeTransitionHook` (`:494`) lets `MainWindow` run the circular flood reveal (see §1.1.7).

---

### 1.1 The shell — `MainWindow`

**Files:** `Views/MainWindow.axaml` (737 ln) + `Views/MainWindow.axaml.cs` (2 029 ln — the largest
file in the project).
**Window:** `Width=372 Height=630 MinWidth=340 MinHeight=560`,
`WindowDecorations="None"`, `Background="{DynamicResource Brush.Bg}"`,
`FontFamily="{DynamicResource Font.Grotesk}"`, `WindowStartupLocation="CenterScreen"`.

Visual tree, outermost first:

```
Panel #windowRoot
├─ LayoutTransformControl #uiScaleHost            ← in-app zoom (ScaleTransform), Ctrl +/−/0
│   └─ DialogHost  (Background #B3000000, CloseOnClickAway)
│       └─ Panel
│           ├─ Grid #chromeRoot  RowDefinitions="28,*"
│           │   ├─ [Row 0] Grid #titleBar  (28 px custom caption)
│           │   │     ├─ wordmark: 18×18 accent tile "d" (radius 6) + "departament" 14 Bold
│           │   │     └─ #btnMin / #btnMax / #btnClose  — 44×22 each, 2 px gap, 6 px right margin
│           │   └─ [Row 1] Panel
│           │         ├─ Grid #bodyRoot                 ← the shell (see 1.1.1)
│           │         ├─ OnboardingView #onboardingView ← full-bleed, first-run
│           │         ├─ AccountSyncView #accountSyncView
│           │         ├─ ContentControl #subPageHost    ← the sub-page stack
│           │         └─ Border #snackHost (Toast)      ← PERMANENTLY IsVisible=False
│           ├─ ContentControl #contentStatusBarView     ← StatusBarView at 0×0, Opacity 0
│           └─ Border #themeTransitionOverlay + Image   ← theme flood-reveal snapshot
└─ Grid #resizeGripHost  (4/*/4 × 4/*/4)               ← 8 transparent 4 px resize zones
```

#### 1.1.1 `#bodyRoot` — the adaptive shell

One `Grid` re-laid by `ApplyLayoutMode(bool compact)` (`MainWindow.axaml.cs:696`):

| Mode | Grid | Chrome |
|---|---|---|
| **Compact** (`width < 760`) | `ColumnDefinitions="*"`, `RowDefinitions="*,Auto"` | `contentArea` row 0, `BottomNavBar` row 1, `navScrim` visible |
| **Wide** (`width ≥ 760 − 24 hysteresis`) | `ColumnDefinitions="Auto,*"`, `RowDefinitions="*"` | `railHost` col 0, `contentArea` col 1, no bottom nav, no scrim |

Constants: `CompactBreakpointWidth = 760.0`, `LayoutHysteresis = 24.0`
(`MainWindow.axaml.cs:31–32`). Startup is compact (372 < 760).
There is also an explicit **layout toggle** (`ToggleLayoutSize`, `:1267`) that animates the window
between `1120×760` (wide) and `372×630` (compact) over 200 ms.

`#bodyRoot` also carries a full-bleed `Border` with `Brush.HomeGradient` spanning both rows and
columns (`MainWindow.axaml:429–435`) so the rail shares the content's gradient instead of painting
a flat block.

#### 1.1.2 Left nav rail (wide)

`Border #railHost` → `DockPanel`:
- Top: `StackPanel #navItems` (`Classes="RailItems"`, `Width=76`, `ClipToBounds`) with **three**
  `Button.NavRailItem` (76×64, glyph 24 + label 11): `#navHome` «Главная», `#navSettings`
  «Настройки», `#navAccount` «Аккаунт». Glyphs `Geo.Nav.Home` / `Geo.Nav.Settings` /
  `Geo.Nav.Account` (`GlobalResources.axaml:312–315`).
- Overlaid: **one travelling indicator** `Border #railIndicator` — 3×28, `Brush.Accent`,
  `CornerRadius=2`, slid on Y by `MoveRailIndicator` (`:531`) at
  `Y = index·64 + 18` (`RailSlotY`, `:529`), 220 ms `Ease.OutQuint`.
- Bottom (always visible): `Ellipse #railStatusDot` 8×8 (`Classes="ConnDot"`, grey → accent when
  connected/connecting) + `Button #btnRailToggle` 30×30 chevron that collapses the rail
  (`Width 76→0`, `Opacity 1→0`, 200 ms).

**There is no "Серверы" rail item.** Servers live inside Home's left column.

#### 1.1.3 Bottom nav (compact) — `Views/BottomNavBar.axaml` (180 ln) + `.axaml.cs` (206 ln)

Three items, glyph 24 + label 11, `MinHeight=56`, press `scale(0.92)` @160 ms OutQuint, a 34×3
accent pill under the selected item, plus a soft `Nav.Scrim` linear gradient behind the bar.
«Аккаунт» is column-collapsed to 0 width when signed out and expands to an equal share on sign-in.

#### 1.1.4 `#contentArea` — the keep-alive tab host

`Panel #contentArea` = `Border(Brush.HomeGradient)` + `Panel #contentHost` + `Border #navScrim`
(24 px, gradient `OpacityMask`, compact only).

`#contentHost` holds **four permanently-realised children** (`MainWindow.axaml.cs:19–27`):
`_homeView` (`HomeView`), `_compactHome` (`CompactHomeView`), `_settingsView` (`SettingsView`),
`_accountView` (`AccountView`). Tab switching is `SwapContent` (`:388`) — never re-parenting, only
`Opacity` + `TranslateX ±16` + `ZIndex` + `IsHitTestVisible`. `ViewFor(tab)` (`:463`) picks the
compact or wide Home by `_compactMode`; `BindActiveHome()` (`:479`) gives the live `HomeViewModel`
to exactly one of them and nulls the other's `DataContext` to free its rows.

#### 1.1.5 `#subPageHost` — the sub-page stack

`ContentControl`, `Background="{DynamicResource Brush.Bg}"`, hidden while `_subStack` is empty.
`PushSubPage` / `PopSubPage` (`:1075` / `:1086`) with directional slide + fade.
Public entry points on `MainWindow`: `OpenBuy()` (`:1165`), `OpenDevices()` (`:1174`),
`OpenHistory()` (`:1182`), `OpenLogin()` (`:1192`), `OpenLoginTelegram()` (`:1213`),
`OpenLoginSite()` (`:1222`), `OpenSubPage(Control)` (`:1232` — the generic one Settings uses).
Any pushed view implementing `ISubPage` gets its `BackRequested` wired to `PopSubPage`.

#### 1.1.6 Overlays

- `OnboardingView #onboardingView` — full-bleed under the caption, no rail, no nav.
- `AccountSyncView #accountSyncView` — post-login import overlay, z above onboarding, below sub-pages.
- Visibility is a **3-way gate**, `ApplyShellVisibility()` (`:823`): `syncing > empty > content`.

#### 1.1.7 Theme transition

`Border #themeTransitionOverlay` + `Image` — `RenderTargetBitmap` snapshot of the old theme,
revealed away with an expanding circular clip from the click point (~520 ms `OutQuint`); skipped
under lite / reduced motion. Driven by `App.ThemeTransitionHook`.

#### 1.1.8 Keyboard

`MainWindow_KeyDown` (`:1875`) handles only: `Ctrl +/=` and `Ctrl −` (UI zoom step), `Ctrl 0`
(reset zoom), `Ctrl V` (add server from clipboard), `Ctrl S` (scan screen for QR), `F5` (reload).
**No Escape, no Alt+←, no Ctrl+, for settings, no tab-cycling shortcut.**

---

### 1.2 Tab 1 — «Главная» (Home)

Two separate trees, chosen by layout mode.

#### 1.2.1 Wide — `Views/HomeView.axaml` (74 ln)

`Border(Brush.HomeGradient)` → `Grid ColumnDefinitions="440,1,*"`:

| Column | Content |
|---|---|
| 0 (440, `MinWidth=380`) | `Grid RowDefinitions="Auto,*"` → `HomeAccountChip` (margin 16,16,16,4) + `ServerListView` |
| 1 (1 px) | vertical hairline, `Brush.OutlineVariant` |
| 2 (`*`) | `DockPanel Margin=16` → TUN-unavailable banner (docked top) + `ConnectHeroView` |

#### 1.2.2 Compact — `Views/CompactHomeView.axaml` (94 ln)

One `ScrollViewer #PageScroll` (`Padding="0,8,0,8"`) → `StackPanel`:
`HomeAccountChip` (16,8,16,0) → TUN banner → `ConnectHeroView` (`MinHeight=440`) → `ServerListView`.

#### 1.2.3 `ConnectHeroView` — `Views/ConnectHeroView.axaml` (839 ln) + `.axaml.cs` (1 156 ln)

Two mutually exclusive layers.

**`#LayerNormal`** — centred `StackPanel`:
- `Panel #HeroFrame` 230×230 (`Size.HeroFrame`), `ClipToBounds=False`, layered back-to-front:
  `#AmbientSonar` (200) → `#AmbientRing` → `#GlowHalo` (`Brush.ConnectGlow` radial) →
  `#RingOuter` / `#RingHoverGlow` / `#RingInner` → `#SonarPulse` / `#SonarPulseEcho` →
  `#ConnectingArc` (212, `Size.ConnectArc`) → `Border #ConnectDisc` (176, `Size.ConnectDisc`,
  radius 88, `Brush.SurfaceHigh`) containing `#PressScrim` + `#ShieldViewbox`
  (`#ShieldOutline` / `#ShieldFilled`, 80 px `Size.ShieldGlyph`).
- `#StatusText` — «Не подключено» / «Подключение» / «Подключено» / error.
- `#RetryHint` — «Нажмите, чтобы повторить».
- `#StatsRow` — `↑ 0 KB/s` · `00:00:00` · `↓ 0 KB/s`. **The arrows are literal `Text="↑"` / `Text="↓"`
  TextBlocks** (`ConnectHeroView.axaml`), not glyphs.
- `#ServerInfo` — 28 px circular flag (`Size.FlagTile`, `Assets/Flags/*.png`, 16 countries + `xx.png`
  globe fallback) + name + protocol chip + transport line.
- `#CornerAddButton` — the **only** "add subscription" affordance in the wide layout, parked in the
  hero's corner. Deliberately absent in compact.

**`#LayerEmpty`** — «Приветствуем!» / «Пока нет подписок» / «Добавьте подписку, чтобы начать
пользоваться» + `#AddQrButton` (Primary) + `#AddClipboardButton` (Tonal).

Motion: connecting arc 1.2 s linear spin with a one-shot 200 ms `OutQuint` wind-up; glow/shield
breathe 850 ms; sonar `Dur.Emphasis` 600 ms; disc press `scale(0.94)`. All gated off under lite.

#### 1.2.4 `ServerListView` — `Views/ServerListView.axaml` (313 ln) + `.axaml.cs` (939 ln)

`ScrollViewer` (visible only when `HasServers`) → `ItemsControl` over `ServerGroups`:
each group renders `SubscriptionMetaView` as its header, then an inner `ItemsControl` with
`VirtualizingStackPanel` over `Servers`. Row = `Border.ServerRow` (radius 20, `MinHeight=56`,
padding 12,8, 1.5 px permanent border) with 40 px flag tile → name (leading flag emoji stripped by
`StripLeadingFlagConverter`) → protocol chip → transport → ping value/spinner. A 1 px
`Border.rowDivider` inset `80,0,16,0` separates rows; hidden on the selected row.
Trailing spacer `Border` 24 px so the last row clears the compact nav scrim exactly.

Selection is **manual press/release** (`OnRowPointerPressed` `:141` / `OnRowPointerReleased` `:170`)
rather than `Tapped`, because `Tapped` was being cancelled by the `ScrollViewer`.

Context menu (`ServerListView.axaml:149–165`): «Сделать основным» · «Проверить задержку» ·
«Изменить» · «Дублировать» · «Поделиться QR» · «Поделиться ссылкой» · «Удалить».
Empty state: `Border.EmptyIcon` 64 with `Geo.CloudDownload`, «Серверов пока нет» + hint.

**No search field.** **No sort.** **No multi-select.** **No bulk ping/delete.**

#### 1.2.5 `SubscriptionMetaView` — `Views/SubscriptionMetaView.axaml` (335 ln) + `.axaml.cs` (687 ln)

Group header card (`Classes="Card"`, line 95). Row 1: collapse chevron + title/subtitle + four
icon actions (ping · refresh · pin · delete) — locally re-sized to **34×34 with 20 px glyphs** via a
local `Button.MetaIcon` style that overrides the global `IconButton40`. Body (only for a real
subscription with userinfo): traffic pill + expiry + announce + support + Telegram.

#### 1.2.6 `HomeAccountChip` — `Views/HomeAccountChip.axaml` (131 ln) + `.axaml.cs` (233 ln)

One shared row used by both Home layouts: 40 px avatar initial + `@handle` +
«Управление аккаунтом» + chevron. Self-hides when signed out. Skeleton state while the profile
resolves. Focusable with `Enter`/`Space`, `FocusAdorner` ring (radius 22). Raises
`AccountRequested` → host switches to the Account tab.

---

### 1.3 Tab 2 — «Настройки» — `Views/SettingsView.axaml` (1 075 ln) + `.axaml.cs` (359 ln)

Root: bare `ScrollViewer` → `StackPanel Margin="0,0,0,16"`. **No `MaxWidth`.**
Six sections, each a `TextBlock.SectionHeader.SettingsSection` followed by one
`Border.Card ClipToBounds=True Padding=0 Margin="16,0,16,8"` containing `Border.SettingRow`s
(`MinHeight=56`, padding 16,12, 1 px inset divider at 72).

| Section (`loc` key) | Rows (`x:Name`) | Right affordance |
|---|---|---|
| Подключение (`Settings_SecConnection`) | `RowMode` | inline segment TUN / Прокси |
| | `RowPerApp` | chevron → `PerAppProxyPage` |
| | `RowBypassLan` | iOS toggle |
| | `RowIpv6` | iOS toggle |
| | `RowDns` | chevron → `DnsSubView` |
| | `RowPingMethod` | chevron → `PingSettingsPage` |
| | `RowLocalProxy` | expand chevron 0→90 → inline panel (port / user / pass) |
| Обход блокировок (`Settings_SecBypass`) | `RowMux` | iOS toggle |
| | `RowMuxConcurrency` | `unfold_more`, cycles value; visible only when Mux on |
| | `RowFragment` | iOS toggle |
| Производительность (`Settings_SecPerformance`) | `RowLiteMode` | iOS toggle (reduced-motion) |
| Интерфейс (`Settings_SecInterface`) | `RowAppearance` | inline segment Тёмная / Светлая |
| | `RowBlackTheme` | iOS toggle (AMOLED/mono overlay) |
| | `RowUiScale` | `unfold_more`, cycles zoom presets |
| | `RowLanguage` | `unfold_more`, cycles Русский/English |
| | `RowBoot` | iOS toggle (autostart) |
| Подписка (`Settings_SecSubscription`) | `RowSubAutoUpdate` | `unfold_more`, cycles interval |
| | `RowRouting` | chevron → `RoutingSubView` |
| | `RowAssets` | chevron → `GeoFilesPage` |
| О приложении (`Settings_About`) | `RowAbout` | chevron → `AboutPage` |
| | `RowBackup` | chevron → `BackupPage` |
| | `RowUrlScheme` | chevron → `UrlSchemesPage` |

**Affordance honesty is genuinely well done** (`SettingsView.axaml.cs:14–22` documents the contract):
chevron = navigation, rotating chevron = inline expand, `unfold_more` = value cycles in place,
segment = 2-state change in place, toggle = boolean. Every row is `Focusable`/`IsTabStop` with
`Enter`/`Space` and a `FocusAdorner` ring; toggles are removed from the tab order so the row owns
the stop.

**What is missing from Settings entirely** (these exist in the engine and in the legacy
`OptionSettingWindow`, and are unreachable in the shipping UI): core selection (Xray/sing-box),
log level and log viewer, socks/http inbound secondary ports, custom DNS per-core JSON,
`FullConfigTemplate`, global hotkeys, sub-settings (`SubSettingWindow`), speed-test URL,
"check for updates", clipboard/URL-scheme import toggles beyond the one row, Clash/Mihomo proxy
group control.

---

### 1.4 Settings sub-pages (all Incy, all pushed onto `#subPageHost`)

All eight share the same skeleton: `Background="{DynamicResource Brush.Bg}"`,
`DockPanel MaxWidth="620"`, a docked 56 px seamless toolbar (`← ` + `Headline`), then a
`ScrollViewer` of `Border.Card` sections. All eight redeclare a **local** `Button.IconButton`
press style instead of using the global `BackNav` (see §3.5).

| Page | File (ln) | Reached from | Content |
|---|---|---|---|
| Прокси по приложениям | `PerAppProxyPage.axaml` (163) + `.cs` (238) | `RowPerApp` | split-tunnel mode + search + app list + "add .exe" |
| DNS | `DnsSubView.axaml` (162) + `.cs` (130) | `RowDns` | preset chips (Cloudflare / Google / AdGuard / FakeIP / По умолчанию / Свой) + custom address + advanced |
| Пинг | `PingSettingsPage.axaml` (160) | `RowPingMethod` | method rows (TCP / real delay) with check glyph + test address + timeout |
| Маршрутизация | `RoutingSubView.axaml` (184) + `.cs` (140) | `RowRouting` | routing profile rows (`{0} правил`), domain strategy, rule sets, reset |
| Файлы ресурсов | `GeoFilesPage.axaml` (100) + `.cs` (99) | `RowAssets` | geoip.dat / geosite.dat rows + «Обновить сейчас» |
| О приложении | `AboutPage.axaml` (105) | `RowAbout` | wordmark + version + details + copy + site + telegram bot |
| Резервное копирование | `BackupPage.axaml` (96) + `.cs` (91) | `RowBackup` | export / import .zip |
| Схемы URL-адресов | `UrlSchemesPage.axaml` (115) + `.cs` (157) | `RowUrlScheme` | register/remove `depv://` + registration list + copy |
| **Провайдер** | `ProviderSettingsPage.axaml` (138) + `.cs` (86) | **nothing** | User-Agent, HWID, auto-update interval — **dead, see §5** |

---

### 1.5 Tab 3 — «Аккаунт» — `Views/AccountView.axaml` (1 474 ln) + `.axaml.cs` (524 ln)

The largest single view. Root `Panel` with two mutually exclusive surfaces gated on `IsLoggedIn`.

**Signed in** — `ScrollViewer` → `StackPanel MaxWidth=560 Margin="16,12,16,24"`:

1. **Zone 1 · Hero** (`Border.Card Padding=16`, line 253) — one card, three stacked zones separated
   by 1 px hairlines: (A) 48 px avatar + name (`Headline`) + tariff caption; (B) balance —
   `Display` numeral + muted ₽ + «Пополнить» button with a top-up flyout (amount field + inline
   error + payment-method chips); (C) referral code row with copy.
2. **Zone 2 · Subscriptions** (line 484 onward) — four exclusive states: skeleton / carousel /
   empty / error. The carousel is a horizontal `ScrollViewer` of subscription cards, each with:
   header (name + health chip + kebab), a 4-panel inline flyout stack (menu → add-devices stepper →
   upgrade picker → upgrade confirm), a measures block (expiry / traffic pill / device gauge),
   one full-width accent «Продлить» CTA with inline balance-vs-card choice, and an auto-renew
   iOS toggle with next-charge line. Dot pager + arrows appear at 2+ subscriptions.
3. **Zone 3 · Способы входа** (line 1053 onward) — Telegram link state (chip + `@id` / pending code
   + «Открыть бота» / «Привязать»), e-mail link, Google.
4. **Zone 4 · Управление** — rows to `PaymentHistoryView`, `DevicesView`, web cabinet, and a quiet
   `Brush.RedText` «Выйти» (text only, no red fill).

**Signed out** — the sign-in gate: title, hint, «Войти через Telegram», «Войти через сайт».

Entrance choreography: group 1 at delay 0, group 2 staggered +40 ms.

---

### 1.6 Sub-pages of Account

| Page | File (ln) | Reached from | States implemented |
|---|---|---|---|
| **Покупка** `BuyView` | `.axaml` (709) + `.cs` (173) | `MainWindow.OpenBuy()` — Account empty state, Devices no-sub CTA, History empty CTA | skeleton · error · empty · success · content; tariff cards with expandable price options; checkout card with device stepper + total + `Primary.Tall` «Оплатить»; bottom sheet for payment method (scrim + slide-up); Escape closes the sheet |
| **Устройства** `DevicesView` | `.axaml` (491) | `MainWindow.OpenDevices()` — Account «Устройства» row | list · skeleton · empty · no-subscription · error; one card, inset dividers, per-row platform tile + «Это устройство» chip + red unlink; centred unlink-confirm card on scrim; Escape closes the confirm |
| **История платежей** `PaymentHistoryView` | `.axaml` (351) | `MainWindow.OpenHistory()` — Account «История платежей» row | list · loading (3 pulsing silhouettes) · empty · error; per-payment card with description/date left, amount + `Border.StatusChip` (paid/pending/failed/canceled) right |
| **Вход** `LoginView` | `.axaml` (954) + `.cs` (1 377) | `OpenLogin()` / `OpenLoginTelegram()` / `OpenLoginSite()` — Onboarding, Account gate, Sync error | see §1.7 |

---

### 1.7 `LoginView` — the account sign-in screen (owner: "выглядит плохо")

**Files:** `Views/LoginView.axaml` (954 ln) + `Views/LoginView.axaml.cs` (1 377 ln).
Background `Brush.HomeGradient`; `Grid RowDefinitions="Auto,*"`; row 0 = 56 px seamless toolbar
`← Вход`; row 1 = `ScrollViewer` → `Panel Margin="16,8,16,28"` → `Panel MaxWidth=440` holding **two
z-stacked blocks** that cross-fade.

**`#MethodBlock`** — top to bottom:
64 px shield tile (radius 20, `Brush.Tile.Blue`, 30 px accent glyph) → wordmark «departament»
(`Title`) → `#TitleText` (`Headline`) → `#SubtitleText` (`Body`, muted) →
segment «Вход | Регистрация» (44 px track, 36 px items) → `#EmailBox` → `#PasswordBox` (with eye
toggle in `InnerRightContent`) → `#ConfirmPasswordBox` → `#RegisterPasswordHint` →
`#ConfirmButton`/`#RegisterSubmitButton` (`Primary.Tall` 52) → `#ForgotPasswordButton` +
`#MagicLinkButton` + `#LoginByCodeButton` (`LinkAction`) → divider «или» →
`#SiteButton` (`Tonal.Tall`) → `#GoogleButton` («Скоро», disabled) → `#SiteBrowserButton` →
`#CodeEntryHost` with `#CodeCells` (6 segment cells) + `#CodeSubmitButton` + `#CodeError` →
`#EmailPendingBlock` (`#PendingTitle` / `#PendingHint` / `#PendingSpinner` / `#ResendButton` /
`#BackToSignInButton`) → `#ErrorLine`.

**`#AwaitingBlock`** — Telegram confirmation: `#AwaitingRingFull` + `#AwaitingSpinner` arc +
breathing `#AwaitingPlane` + `#AwaitingCheck` (arc completes into a check on success) →
«Ждём подтверждения» → `#OpenTelegramButton` (`Primary.Tall`) → `#RestartButton` →
`#ChooseAnotherButton`.

**Measured density: 20 `Button`s and 5 `TextBox`es in one scrolling column, 34 distinct
localisation keys.** Six sign-in methods (Telegram, site handoff, e-mail+password, magic link,
one-time code, Google-soon) are all present simultaneously. The primary action —
"войти через Telegram" — is **not** the first thing on the screen; it sits below the e-mail form,
under an «или» divider, as a *tonal* button, while the accent `Primary` is spent on the e-mail
submit. This is the concrete reason the screen reads badly: **the hierarchy is inverted and the
page carries every method at once instead of one path with the rest behind a disclosure.**

---

### 1.8 `OnboardingView` — the first frame — `Views/OnboardingView.axaml` (238 ln) + `.axaml.cs` (213 ln)

Full-bleed under the caption bar; visible while `HomeViewModel.IsEmpty`. `Border(Brush.HomeGradient)`
→ `ScrollViewer` → `Panel Margin="16,0" MinHeight={Scroll.Bounds.Height}` → `StackPanel #Column`
`MaxWidth=440`, vertically centred:

64 px shield tile → «departament» wordmark → «Добавьте подписку» (`Display`) →
«Отсканируйте QR-код или вставьте ссылку из буфера — доступ появится сразу.» →
`#AddQrButton` (`Primary.Tall` 52) → `#AddClipboardButton` (`Tonal.Tall` 52) →
two-hairline rule «или войдите в аккаунт» → `#LoginTelegramButton` (`Tonal.Tall`) →
`#LoginSiteButton` (`LinkAction`, demoted to a text link so there is only one filled accent).

Copy lives in `Common/L.Home.cs:36–38` (`Onboarding_Title`, `Onboarding_Subtitle`,
`Onboarding_OrSignInShort`).

---

### 1.9 `AccountSyncView` — `Views/AccountSyncView.axaml` (176 ln) + `.axaml.cs` (324 ln)

Post-login import overlay so the empty onboarding never flashes between "Вход" closing and Home
filling. `Brush.HomeGradient` → centred column `MaxWidth=400`: 64 px ring (static
`Brush.OutlineVariant` track + spinning `Brush.Accent` arc, `StrokeDashArray="16.75,50.25"`) with
the brand shield inside → «Добавляем аккаунт» title → live stage line. On failure the column
cross-fades in place to a red alert shield + «Не удалось синхронизировать» + hint +
«Повторить» / «Войти заново». On success the arc stops, the shield does a 1.0→1.04→1.0 settle pop,
then the shell cross-fades to Home.

---

### 1.10 Dialogs

| Dialog | File | Host | Style verdict |
|---|---|---|---|
| Yes/no confirm | `MessageBoxDialog.axaml` (67 ln) | `Window`, `WindowDecorations=None`, `SizeToContent`, shown by `Common/UI.cs:14` | **Incy.** `Brush.Bg` window + `Brush.Surface` card, radius 20, 1 px `OutlineVariant`, `BoxShadow 0 16 40 0 #73000000`, `Primary` + `Tonal`. The question *is* the title. |
| QR share | `QrcodeView.axaml` (25 ln) | `DialogHost.Show(...)` from `ServerListView.axaml.cs:116` | **Raw upstream.** 400×400 `Image` + a read-only `TextBox`, `Margin8`/`MarginTb8` legacy resources, no card, no toolbar, no copy button, no theme tokens. |
| sudo password (Linux) | `SudoPasswordInputView.axaml` (57 ln) | `DialogHost.Show` from `StatusBarView.axaml.cs:203` | **Raw upstream.** `Theme="{DynamicResource CardBorder}"`, `resx:ResUI.TbConfirm` / `TbCancel`, `Width="100"` buttons. |
| Log / messages | `MsgView.axaml` (104 ln) | registered in `SimpleViewLocator` — **never built** | Dead. See §5. |

---

### 1.11 Legacy upstream windows (stratum B) — the unconverted half

All are `Window`s with OS decorations, `WindowStartupLocation="CenterScreen"`, `Title` bound to
`ServiceLib.Resx.ResUI` (Chinese-origin resource strings, **no Russian localisation**), Semi default
chrome, `TabControl` + `DataGrid` + `Width="100"` buttons. They inherit only `Font.Grotesk` from the
global `TopLevel` style, so they render as Space Grotesk on an otherwise stock Semi surface — which
looks *worse* than leaving them alone, because the font says "our app" and everything else says
"someone else's".

| Window | File (ln) | `resx:` refs | Incy classes | Reached from | Size |
|---|---|---|---|---|---|
| `AddServerWindow` | 1 388 | 94 | 0 | server row → «Изменить» (`ProfilesViewModel.cs:527`) | 900×600 |
| `AddServer2Window` | 160 | 14 | 0 | custom-config server edit (`ProfilesViewModel.cs:517`) | 700×500 |
| `AddGroupServerWindow` | 258 | 32 | 0 | group/policy server edit (`ProfilesViewModel.cs:522`) | 900×700 |
| `SubEditWindow` | 272 | 29 | 0 | `ProfilesViewModel.cs:947`, `SubSettingViewModel.cs:78` | 700×600 |
| `SubSettingWindow` | — | 16 | 0 | `MainWindowViewModel.cs:708` — **no UI binding in this shell** | 900×600 |
| `OptionSettingWindow` | 1 206 | 91 | 1 | `MainWindowViewModel.cs:726` — **no UI binding** | 1000×600 |
| `RoutingRuleSettingWindow` | 259 | 27 | 0 | `RoutingSettingViewModel.cs:136` (reachable via `RoutingSubView`) | 900×600 |
| `RoutingRuleDetailsWindow` | 263 | 16 | 2 | `RoutingRuleSettingViewModel.cs:141` | 900×600 |
| `ProfilesSelectWindow` | 129 | 12 | 0 | `SubEditViewModel.cs:85`, `AddGroupServerViewModel.cs:116`, `RoutingRuleDetailsViewModel.cs:105` | 800×450 |
| `FullConfigTemplateWindow` | 197 | 15 | 0 | `MainWindowViewModel.cs:766` — **no UI binding** | 900×600 |
| `GlobalHotkeySettingWindow` | 133 | 11 | 0 | `MainWindowViewModel.cs:212` — **no UI binding** | 700×500 |
| `JsonEditor` | 26 | 4 | 0 | embedded in `AddServerWindow:271` and `FullConfigTemplateWindow` ×4 | control |

Note the split: **four of these windows are reachable and ugly** (AddServer×3, SubEdit, the two
Routing-rule windows, ProfilesSelect), and **four more are unreachable but still the only place a
whole feature exists** (OptionSetting, SubSetting, FullConfigTemplate, GlobalHotkey).

---

## 2. The navigation model

### 2.1 Model

```
                    ┌──────────────────────────────────────┐
   launch  ────────▶│  3-way shell gate (ApplyShellVisibility) │
                    │   syncing  >  empty  >  content       │
                    └──────────────────────────────────────┘
                        │            │              │
             AccountSyncView   OnboardingView    bodyRoot
                                    │               │
                              (Login sub-page)      │
                                                    ▼
              ┌──────────────────────────────────────────────────┐
              │  ONE tab state (_currentTab) · ONE host (contentHost) │
              │  Home(0) ── Settings(1) ── Account(2)            │
              │  wide: left rail 76      compact: bottom bar     │
              └──────────────────────────────────────────────────┘
                                    │
                        ┌───────────┴────────────┐
                        ▼                        ▼
              sub-page stack (_subStack)   legacy modal Window
              Buy · Devices · History ·    AddServer* · SubEdit ·
              Login · 8 settings pages     RoutingRule* · ProfilesSelect
              (slide+fade, back = ← only)  (ShowDialog, OS-decorated)
```

### 2.2 Rules as implemented

- **Three tabs, one state.** `_currentTab` (`MainWindow.axaml.cs:59`) is the single source of truth
  across both layouts. `ShowTab(tab, animate)` (`:354`) computes slide direction from the index delta
  (`Home 0 ▸ Settings 1 ▸ Account 2`) — forward enters from +16 px, back from −16 px.
- **Layout is a chrome swap, not a navigation event.** `ApplyLayoutMode` re-lays `#bodyRoot`, hides
  the rail or the bar, and re-runs `ShowTab(_currentTab, animate:false)`. Controls are never
  re-parented (this was a documented crash source: compact → Settings → widen).
- **Sub-pages are a stack, but a shallow one.** `_subStack` is a `List<Control>`; `PushSubPage`
  appends, `PopSubPage` removes the last and shows the previous or nothing. Depth 2 is reachable
  (Account → Buy is depth 1; Account → Devices → Buy is depth 2).
- **Back is one button.** `Button.BackNav` in each sub-page's 56 px toolbar raises `BackRequested`.
  No Escape, no mouse-button-4, no `Alt+←`, no breadcrumb, no title-bar back.
- **Tabs do not remember scroll or sub-state.** Because all four tab views are keep-alive children
  of `contentHost`, scroll position *is* preserved — but sub-pages are not tied to a tab, so
  Account → Buy → rail-click "Настройки" leaves Buy on top of Settings.
- **Legacy dialogs are modal `Window`s**, not sub-pages, and they steal the taskbar.
  `WindowDialog.TryGetOwnerWindow()` (`Manager/WindowDialog.cs:29`) picks the topmost visible window
  as owner.
- **Onboarding is not part of the stack.** It is a gate. Adding a subscription flips
  `HomeViewModel.IsEmpty` and the gate cross-fades to `bodyRoot`.

### 2.3 What the model is missing

| Gap | Consequence |
|---|---|
| No Escape / no mouse-back on `_subStack` | Only exit from Buy/Devices/History/Login/settings pages is a 40 px target in the top-left. |
| Sub-pages are global, not per-tab | Switching tabs while a sub-page is open leaves the sub-page over the *new* tab. |
| No deep-link / no route identity | Nothing can restore "Account → Devices" after restart, and `depv://` URL-scheme handling has no destination vocabulary. |
| No "Серверы" destination | Servers are a column inside Home. In compact they are the tail of one long scroll below a 440 px hero — the list is below the fold on a 630 px window. |
| Legacy windows are outside the model | 12 surfaces with their own title bar, own back semantics, own language. |
| No visible feedback channel | See §0 item 3. Navigation succeeds silently; navigation failures are silent too. |

---

## 3. The token set actually in use, and every violation

### 3.1 Colour — `Assets/GlobalResources.axaml`

**Accent (theme-independent, lines 39–51):**

| Token | Value |
|---|---|
| `Color.Accent` / `Brush.Accent` | `#4C8DFF` |
| `Color.OnAccent` / `Brush.OnAccent` | `#00183A` |
| `SemiColorPrimary` / `Hover` / `Active` | `#4C8DFF` / `#5F9AFF` / `#3D7EF0` |
| `Brush.Tile.Blue` / `Brush.Tile.Green` | `#4C8DFF` @0.20 / `#22C55E` @0.20 |

**Theme dictionaries (`ThemeDictionaries`, lines 59–135) — identical key sets:**

| Key | Dark | Light |
|---|---|---|
| `Brush.Bg` | `#0A0B0D` | `#F4F7FC` |
| `Brush.Surface` | `#141619` | `#FFFFFF` |
| `Brush.SurfaceHigh` | `#1A1D21` | `#EAEFF7` |
| `Brush.SurfaceVariant` | `#1E2126` | `#E9EEF7` |
| `Brush.SurfaceHighest` | `#20242B` | `#E3EAF4` |
| `Brush.OnSurface` | `#F2F4F8` | `#111826` |
| `Brush.OnSurfaceVariant` | `#9BA1AD` | `#54607A` |
| `Brush.OnSurfaceVariantHover` | `#6E7480` | `#3C475E` |
| `Brush.Outline` | `#2A2E36` | `#C3CCDC` |
| `Brush.OutlineVariant` | `#20242B` | `#DCE3EF` |
| `Brush.AccentContainer` | `#17325C` | `#D8E4FF` |
| `Brush.OnAccentContainer` | `#CFE0FF` | `#14468F` |
| `Brush.Green` | `#22C55E` | `#0B7D4A` |
| `Brush.Red` | `#F04452` | `#C42B32` |
| `Brush.RedText` | `#FF6069` | `#C42B32` |
| `Brush.Tile.Neutral` | `#20242B` | `#E3EAF4` |
| `Brush.Hover` | `#000000` @0.32 | `#000000` @0.06 |
| `Brush.Toast.Bg` | `#20242B` | `#E3EAF4` |
| `Brush.HomeGradient` | radial `#1B2D50` → `#0E141F` → `#0A0B0D` | radial `#FFFFFF` → `#EEF3FB` → `#DFE6F1` |

Plus theme-independent: `Brush.Tile.Orange/Purple/Red/Yellow` (@0.20), `Brush.Icon.Orange`
(`#FB923C`), `Brush.Icon.Yellow` (`#EAB308`), `Brush.SelectedFill` (`#4C8DFF` @0.12),
`Brush.RedPressed` (`#D93844`), `Brush.StatusChip.{Green,Orange,Red,Yellow}` (@0.18),
`Brush.Scrim` (`#000000` @0.6), `Brush.ConnectGlow` (radial `#594C8DFF`→`#264C8DFF`→`#004C8DFF`),
`Brush.Ring.Outer` (`#334C8DFF`), `Brush.Ring.Inner` (`#804C8DFF`).

Third theme: `App.axaml.cs:580 BuildMonoOverlay(light)` overrides ~30 of the above to greyscale
(`Brush.Bg` = `#000000` dark / `#FFFFFF` light, `Brush.Accent` collapsed to grey).

**Verdict:** this layer is correct and disciplined. Only **3 files** contain a hardcoded
`Background`/`Foreground`/`Fill`/`Stroke`/`BorderBrush` hex (`MainWindow.axaml`,
`DevicesView.axaml`, `ConnectHeroView.axaml` — one each). That is a good number for a 30 000-line
UI project.

### 3.2 Spacing, radii, sizes

`Space.4/8/12/16/24/32` as `x:Double`; `Pad.12`, `Pad.16`, `Pad.Card` (16), `Gutter` (`16,0`).
`Radius.Chip 12` · `Radius.Tile 12` · `Radius.Search 14` · `Radius.Button 16`
(declared in `GlobalStyles.axaml:16`, not GlobalResources) · `Radius.Card 20` ·
`Radius.Sheet 24,24,0,0` · `Radius.Pill 100` · `Radius.Traffic 8`.
Sizes: `Size.Tile 40` · `Size.Glyph 22` · `Size.Row 56` · `Size.Gutter 16` · `Size.IconButton 40` ·
`Size.SubToolbar 56` · `Size.CtaTall 52` · `Size.SegmentChip 44` · `Size.EmptyIcon 64` ·
`Size.EmptyGlyph 32` · `Size.FlagTile 28` · `Size.AvatarChip 36` · `Size.AvatarAcc 48` ·
`Size.AvatarBadge 18` · `Size.SheetHandleW/H 36/4` · `Size.SkeletonCard 76` ·
`Size.TrafficPill 160` · `Size.HeroFrame 230` · `Size.ConnectDisc 176` · `Size.ShieldGlyph 80` ·
`Size.ConnectArc 212` · `Dot 6` / `Dot.Active 8` / `Dot.Gap 8`.

### 3.3 Type

Font: `Font.Grotesk` = `avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk`.
`Font.Numeric` is **the same file** under a different semantic key (documented at
`GlobalResources.axaml:168–180`); numeric surfaces pair it with
`FontFeatures="tnum,lnum,zero"`.

| Class | Size | Weight | Tracking | Colour |
|---|---|---|---|---|
| `TextBlock.Display` | 34 | Bold | −0.7 | `OnSurface` |
| `TextBlock.Headline` | 24 | Bold | −0.24 | `OnSurface` |
| `TextBlock.Title` | 16 | Bold | — | `OnSurface` |
| `TextBlock.TitleMedium` | 16 | Medium | — | `OnSurface` |
| `TextBlock.Body` | 14 | — | — | `OnSurface` |
| `TextBlock.Subtitle` | 13 | — | — | `OnSurfaceVariant` |
| `TextBlock.Caption` | 12 | — | — | `OnSurfaceVariant` |
| `TextBlock.Chip` | 11 | Medium | — | `OnSurface` |
| `TextBlock.SectionHeader` | 16 | Bold | — | `OnSurface` |

`SectionHeader` is bold sentence-case, **not** a tiny tracked ALL-CAPS eyebrow — the law is honoured.
Font is forced onto `TopLevel`, `TextBlock` and `TemplatedControl` (`GlobalStyles.axaml:257–265`)
so every popup and legacy window inherits it.

### 3.4 Motion

| Curve | Spline | Use |
|---|---|---|
| `Ease.OutQuart` | 0.25,1,0.5,1 | press feedback |
| `Ease.OutQuint` | 0.22,1,0.36,1 | reveal / settle / rail indicator |
| `Ease.Standard` | 0.2,0,0,1 | two-way state / crossfade |
| `Ease.OutExpo` | 0.16,1,0.3,1 | reserved for the ONE auth→home hand-off |

Durations (literal `TimeSpan`s in XAML; C# mirror in `Common/Motion.cs`):
`Instant 0` · `PressIn 0.09` · `PressOut 0.16` · `State 0.22` · `Reveal 0.30` · `Exit 0.15` ·
`Shell 0.20` · `Slow 0.45` · `Stagger 0.04` · `Emphasis 0.60`. Disconnect reverse = 75 % tempo.
A global reduced-motion mode (`.lite` class on the window, driven by `UiItem.LiteMode`) zeroes
`Transitions` on every interactive class and de-selects cyclic keyframe animations at the selector
level (`GlobalStyles.axaml:1285`, `:1331`, `:1383–1446`).

### 3.5 Component classes actually shipped

`Border.Card` · `Border.Row` · `Border.SettingRow` (+ `.segmentRow`) · `Border.Tile` (+
`.Blue/.Green/.Orange/.Purple/.Red/.Yellow`) · `Border.ChipBadge` · `Border.ProtocolChip` ·
`Border.ServerRow` (+ `.pressed/.selected`) · `Border.SearchPill` · `Border.TrafficPill` (+ `.Fill`)
· `Border.Avatar` · `Border.AccountChip` · `Border.ConnectDisc` · `Border.SubToolbar` ·
`Border.StatusChip` (+ `.paid/.pending/.failed/.canceled`) · `Border.PriceOption` ·
`Border.SheetTop` · `Border.SheetHandle` · `Border.Scrim` · `Border.Toast` · `Border.EmptyIcon` ·
`Border.SkelBar` · `Border.SkelCard` · `Border.NavRail` · `Border.RailIndicator` ·
`Button.Primary` (+ `.Tall`) · `Button.Tonal` · `Button.OutlinedAccent` · `Button.Destructive` ·
`Button.Stepper` · `Button.IconButton` (legacy 32) · `Button.IconButton40` (+ `.Row/.Accent`) ·
`Button.BackNav` · `Button.LinkAction` · `Button.NavRailItem` (+ `.active`) ·
`ToggleButton.Segment` · `Ellipse.Dot` (+ `.active`) · `Ellipse.Spinner` (+ `.spinning`) ·
`:is(Control).SkeletonPulse` · `ControlTheme ToggleSwitch.iOS` · `ControlTheme TextBox.Incy` ·
`ControlTheme TextBox.IncyField` · `ControlTheme IncyFlyoutTheme` · custom `ScrollBar` theme.

---

### 3.6 VIOLATIONS

Ranked by how much they cost.

**V1 — Decorative gradients and glows, everywhere. (Absolute ban.)**
`Brush.HomeGradient` is a `RadialGradientBrush` (`GlobalResources.axaml:88` dark / `:124` light)
painted as the background of: `MainWindow.axaml:434` (full-bleed under rail + content),
`MainWindow.axaml:551` (again, under `contentHost`), `HomeView.axaml:16`, `OnboardingView.axaml:43`,
`LoginView.axaml:237`, `AccountSyncView.axaml:47`. `Brush.ConnectGlow`
(`GlobalResources.axaml:269`) is a radial halo rendered at `ConnectHeroView.axaml:380`, wrapped in
two alpha rings (`Brush.Ring.Outer/Inner`, `:280–281`) plus an ambient breathing loop
(`#AmbientSonar`, `#AmbientRing`, `ConnectHeroView.axaml:342`, `:357`) and a pulsing sonar
(`#SonarPulse`, `#SonarPulseEcho`, `:438`, `:453`). Also: `Nav.Scrim` linear gradient
(`BottomNavBar.axaml:24–27`) and the `navScrim` `OpacityMask` gradient (`MainWindow.axaml:582–586`).
Two shadows exist as well: `IncyFlyoutTheme` `BoxShadow 0 12 32 0 #66000000`
(`GlobalStyles.axaml:42`), `Border.Toast` `0 8 24 0 #40000000` (`:1198`),
`MessageBoxDialog` `0 16 40 0 #73000000`.
*This is not an accident — it is the deliberate brand layer ported from Android. The 2026 spec must
either (a) formally amend the law to permit exactly these two named brand surfaces, or (b) replace
them. It cannot leave the contradiction unresolved.*

**V2 — 15 upstream windows, unlocalised and unstyled, one click away.**
`AddServerWindow` (94 `resx:`), `OptionSettingWindow` (91), `AddGroupServerWindow` (32),
`SubEditWindow` (29), `RoutingRuleSettingWindow` (27), `AddServer2Window` (14),
`FullConfigTemplateWindow` (15), `SubSettingWindow` (16), `RoutingRuleDetailsWindow` (16),
`ProfilesSelectWindow` (12), `GlobalHotkeySettingWindow` (11), `JsonEditor` (4),
`QrcodeView`, `SudoPasswordInputView`, `MsgView`. Zero Incy classes in 12 of them
(`OptionSettingWindow` has 1, `RoutingRuleDetailsWindow` has 2). No Russian copy — `ResUI` is the
upstream Chinese/English resource set. All are OS-decorated `Window`s with `Width="100"` fixed
buttons and `DataGrid`/`TabControl`.

**V3 — `SettingsView` has no measure.** `SettingsView.axaml:216` — bare `ScrollViewer`, no
`MaxWidth`. Account/Buy/History/Devices use 560; the eight settings sub-pages use 620. At the app's
own 1120×760 preset the settings cards run ~1030 px wide. Inconsistent and unreadable.

**V4 — Off-scale spacing.** The law is one scale (4/8/12/16/24/32). Actual `Margin`/`Padding`/
`Spacing` literals found per file:

| File | Off-scale values used |
|---|---|
| `LoginView.axaml` | 14, 20, 28, 40 |
| `AccountView.axaml` | 6, 10, 20, 72 |
| `DevicesView.axaml` | 3, 10, 68 |
| `SubscriptionMetaView.axaml` | 2, 6, 10, 14 |
| `RoutingSubView.axaml` | 3, 6, 10 |
| `CompactHomeView.axaml` | 10, 14 |
| `HomeView.axaml` | 10, 14 |
| `ConnectHeroView.axaml` | 6, 20 |
| `ServerListView.axaml` | 2, 6, 14 |
| `HomeAccountChip.axaml` | 10 |
| `PerAppProxyPage.axaml` | 10 |
| `SettingsView.axaml` | 6 |
| `DnsSubView.axaml`, `PingSettingsPage.axaml` | 6, 2 |
| `BottomNavBar.axaml` | 6 |

(1 / 1.5 / 3 px values on hairlines and borders are legitimate and excluded.)

**V5 — Off-scale type.** `FontSize="20"` at `AccountView.axaml:268` (avatar initial) and
`LoginView.axaml`; `FontSize="18"` at `HomeAccountChip.axaml`. Neither 18 nor 20 exists in the
type scale (34/24/16/14/13/12/11). Three occurrences — small, but they are the only ones, so
closing them costs nothing.

**V6 — Touch targets under 48.** Caption buttons are **44×22** (`MainWindow.axaml:52–61`;
`btnMin`/`btnMax`/`btnClose` at `:359`, `:373`, `:387`). Rail collapse toggle is **30×30**
(`MainWindow.axaml:131–143`). `SubscriptionMetaView` locally shrinks its four action buttons to
**34×34** with 20 px glyphs, deliberately overriding the global `IconButton40`. The global
`Button.IconButton` legacy class is **32×32** (`GlobalResources.axaml:20–21`) and is still applied
in `LoginView.axaml:242` (`Classes="IconButton BackNav"` — both classes at once) and in all eight
settings sub-pages.

**V7 — Two icon-button systems still coexist.** `Button.IconButton` (32, legacy, `GlobalStyles.axaml:226`)
and `Button.IconButton40` (40, canonical, `:842`). The comment at `:838` says the Incy screens
"migrate to IconButton40 in the dedup pass" — that pass never finished. The eight settings sub-pages
and `LoginView` still declare their own local `Button.IconButton:pressed { scale(0.92) }` style,
duplicated verbatim nine times.

**V8 — Two radii for the same role.** `Radius.Chip` = 12 and `Radius.Tile` = 12 are the same number
under two names; `Radius.Search` = 14 and `Radius.Button` = 16 both apply to "a rectangle you click".
`Radius.Button` is declared in `GlobalStyles.axaml:16` (a `Styles.Resources` block) instead of with
the other radii in `GlobalResources.axaml:150–154`, so the scale is split across two files.

**V9 — GlobalStyles is 40 % Semi-suppression.** Roughly 25 selectors exist purely to stop
`SemiTheme` re-tinting `PART_ContentPresenter` on `:pointerover` / `:pressed`
(`GlobalStyles.axaml:236–245`, `:412–438`, `:474–493`, `:524–537`, `:769–782`, `:862–872`,
`:927–939`, `:1000–1011`, `:1238–1254`). The design system is fighting its own base theme on every
interactive control. Every new component must remember to add the same three suppressions.

**V10 — Emoji / text-glyph chrome.** `ConnectHeroView` renders the speed arrows as literal
`Text="↑"` and `Text="↓"` TextBlocks. `ServerListView` needs a `StripLeadingFlagConverter` because
server remarks arrive with flag emoji, and country identity is carried by 16 raster PNGs
(`Assets/Flags/*.png`) with a `xx.png` globe fallback — raster art in an otherwise all-vector UI.

**V11 — Feedback surface missing.** `snackHost` (`MainWindow.axaml:623`) is permanently
`IsVisible=False` by design; `DelegateSnackMsg` (`MainWindow.axaml.cs:1765`) forwards to
`NoticeManager.SendMessage` → `MsgViewModel`, and `MsgView` is registered in `SimpleViewLocator.cs:26`
but **never instantiated**. Net result: zero user-visible feedback for clipboard import, subscription
refresh, engine errors. The in-code comment describing an "inline message panel" is wrong.

**V12 — Escape/back gap.** See §2.3.

**V13 — Contrast risk on the light gradient.** `Brush.HomeGradient` light stops at `#FFFFFF` in the
centre; `Brush.OnSurfaceVariant` light is `#54607A` (≈5.3:1 on white — passes). But the **dark**
gradient's lightest stop is `#1B2D50`, and `LoginView.axaml:229–236` documents a self-assessed
subtitle contrast of "≈4.6:1" on that stop. That is a 0.1 margin above the 4.5 floor, measured by
hand, with no automated check. It should be re-measured, not trusted.

**V14 — No `Views/Styles/` or `Views/Themes/` directory.** Theming is split across
`Assets/GlobalResources.axaml`, `Assets/GlobalStyles.axaml`, `App.axaml.cs` (mono overlay built in
C#), plus per-window `Window.Resources`/`Window.Styles` blocks in `MainWindow.axaml:32–292` (260
lines of window-chrome styling that belongs in the system). Discoverability is poor and the mono
theme cannot be reviewed as data.

---

## 4. Keep / restyle / rebuild — the blunt verdict

**KEEP** = ships as-is, only token/spacing cleanups.
**RESTYLE** = structure is right, surface needs the 2026 pass.
**REBUILD** = start from the spec, not from this file.

### 4.1 Shell & navigation

| Surface | File | Verdict | Why |
|---|---|---|---|
| `MainWindow` chrome (custom 28 px caption, resize grips, UI zoom, theme flood) | `MainWindow.axaml` / `.cs` | **RESTYLE** | Mechanics are excellent and hard-won (LayoutTransformControl zoom, 8-zone native resize, keep-alive tab host). But: caption 44×22 targets, 260 lines of chrome styles living in the window instead of the system, and the whole thing is 2 029 lines of code-behind. Extract chrome styles to the design system; raise caption targets; keep the architecture. |
| Nav rail (wide) | `MainWindow.axaml:439–545` | **KEEP** | 76×64 items, travelling 3×28 indicator, no ripple, honest hover (glyph darkens, no background box). This is the best-executed piece in the app. Add the 4th destination when Servers becomes one. |
| Bottom nav (compact) | `BottomNavBar.axaml` | **KEEP** | Same grammar as the rail, 56 px targets, correct collapse of «Аккаунт» when signed out. |
| Sub-page stack | `MainWindow.axaml.cs:1075–1240` | **RESTYLE** | Add Escape + mouse-back, scope the stack per tab, give routes identities so `depv://` and restore can target them. |
| Feedback / notice channel | `snackHost` + `MsgView` | **REBUILD** | There is no surface. Design one: an inline, dismissible status strip in the shell (not a floating toast — the owner rejected toasts), plus a real log page reachable from Settings › О приложении. |

### 4.2 Home

| Surface | File | Verdict | Why |
|---|---|---|---|
| `HomeView` (wide 440 \| 1 \| *) | `HomeView.axaml` | **RESTYLE** | Two-panel split is correct. The 1 px divider plus the full-bleed gradient plus the per-column gradient is three background decisions where one is needed. Left column has no header, no count, no search, no sort — it is a bare list under a card. |
| `CompactHomeView` | `CompactHomeView.axaml` | **RESTYLE** | One scroll containing a 440 px-min hero then the entire server list means at 630 px window height the server list starts below the fold and the hero cannot be skipped. Needs a collapsing hero or a sticky segmented header. |
| `ConnectHeroView` | `.axaml` (839) + `.cs` (1 156) | **RESTYLE** *(and settle V1)* | The state machine, the wind-up arc, the press physics and the reduced-motion gating are genuinely good. What must change: the ambient breathing/sonar loops are two competing idle animations on the same object; the `↑`/`↓` text arrows; the `#CornerAddButton` hiding the app's primary "add subscription" action in a hero corner in one layout only; and the glow stack must be resolved against the design law. |
| `ServerListView` | `.axaml` (313) + `.cs` (939) | **RESTYLE** | Row grammar, virtualization, divider inset and empty state are right. Missing: **search**, sort, ping-all, multi-select, and a visible "which server am I on" affordance beyond the selected fill. The context menu is the only route to 7 actions and is undiscoverable. |
| `SubscriptionMetaView` | `.axaml` (335) + `.cs` (687) | **RESTYLE** | Content is right. It breaks the icon-button system locally (34/20 instead of 40/22) to fit a 372 px window — that is a symptom of the compact width being too tight for four trailing actions; solve it structurally (overflow menu), not by shrinking targets. |
| `HomeAccountChip` | `.axaml` (131) | **KEEP** | Single definition shared by both layouts, self-gating, skeleton state, keyboard-activatable, focus ring. Fix `FontSize="18"` → 16. |

### 4.3 Settings

| Surface | Verdict | Why |
|---|---|---|
| `SettingsView` (the tab) | **RESTYLE** | Affordance grammar (chevron / rotating chevron / unfold / segment / toggle) is the single best design decision in the codebase — keep it verbatim. Fix: add `MaxWidth`; add search over settings; surface the ~10 engine features that currently exist only in the dead `OptionSettingWindow`; give the 6 sections an order that reflects use, not the Android port order. |
| `PerAppProxyPage` | **RESTYLE** | Correct skeleton; needs the shared `BackNav`/`SubToolbar` instead of its local `IconButton` override. |
| `DnsSubView` | **RESTYLE** | Same. Preset chips are good; "Свой" custom path needs validation states. |
| `PingSettingsPage` | **RESTYLE** | Same. |
| `RoutingSubView` | **RESTYLE** | Same — but its "edit rule" path drops into `RoutingRuleSettingWindow` (upstream, 900×600, `resx`). That escape hatch must be closed. |
| `GeoFilesPage` | **RESTYLE** | Same. Needs progress/error states for the download. |
| `AboutPage` | **RESTYLE** | Same. Should host the log/notice surface (see 4.1). |
| `BackupPage` | **RESTYLE** | Same. |
| `UrlSchemesPage` | **RESTYLE** | Same. |
| `ProviderSettingsPage` | **REBUILD or DELETE** | Fully built, fully styled, **zero references**. Either wire it into Settings › Подписка or delete the file. |

### 4.4 Account & commerce

| Surface | Verdict | Why |
|---|---|---|
| `AccountView` | **RESTYLE** | 1 474 lines in one file with a 4-panel flyout state machine inside a horizontal carousel inside a scroll. The visual grammar is right (one hero card, three zones, one hairline each, one accent CTA per card, quiet red text for sign-out). The *structure* is over-nested: the upgrade/add-device flows belong on their own sub-pages, not in a flyout inside a carousel card. |
| `BuyView` | **KEEP** | Five states, real skeletons, a proper bottom sheet with scrim + Escape, one accent CTA. Closest thing to a finished 2026 screen in the app. Fix off-scale margins only. |
| `DevicesView` | **KEEP** | Five states, one card, inset dividers, correct destructive treatment (red glyph + confirm card, not a red row). Fix `Margin` 3/10/68. |
| `PaymentHistoryView` | **KEEP** | Four states, status chips wired to the token set, amount and chip aligned on one vertical. |
| `LoginView` | **REBUILD** | 954 + 1 377 lines, 20 buttons, 5 fields, 6 sign-in methods, 34 copy keys, on one scrolling column — with the *primary* method demoted to a tonal button under an «или» divider. The owner is right. Rebuild as: one screen, one accent action (Telegram), one secondary (сайт), everything else behind «Другой способ входа». |
| `OnboardingView` | **RESTYLE** | Already close: one accent, one tonal, one demoted link, correct rhythm. But it is the app's first frame and it currently reads as a form, not as a product. It also has no third path for "I already have this app configured" other than the same login. |
| `AccountSyncView` | **KEEP** | Correct: no empty flash, live stage line, real error state with two exits, success settle. Exactly how a loading gate should behave. |

### 4.5 Dialogs

| Surface | Verdict |
|---|---|
| `MessageBoxDialog` | **KEEP** — already Incy. |
| `QrcodeView` | **REBUILD** — 25 lines of raw upstream inside our DialogHost. Needs card, title, copy button, tokens. |
| `SudoPasswordInputView` | **REBUILD** — `resx` strings, `CardBorder` legacy theme, `Width="100"` buttons. Linux users see this on first TUN start. |
| `MsgView` | **REBUILD** as the log surface (see 4.1) or delete. |

### 4.6 The upstream stratum

| Surface | Verdict | Note |
|---|---|---|
| `AddServerWindow` (1 388 ln) | **REBUILD** as an Incy sub-page | Reached from every server row. Highest-value single conversion in the project. |
| `AddServer2Window`, `AddGroupServerWindow` | **REBUILD** as sub-pages | Same entry point family. |
| `SubEditWindow` | **REBUILD** as a sub-page | Reachable via `ProfilesViewModel:947`. |
| `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow` | **REBUILD** as sub-pages under `RoutingSubView` | Currently the only way to edit a rule. |
| `ProfilesSelectWindow` | **REBUILD** as a picker sheet | Called from 3 places. |
| `OptionSettingWindow` (1 206 ln) | **DELETE**, migrate its ~10 unique controls into `SettingsView` | Unreachable today; its features are the gap listed in §1.3. |
| `SubSettingWindow`, `FullConfigTemplateWindow`, `GlobalHotkeySettingWindow` | **DELETE**, migrate the feature | Same. Global hotkeys in particular is a desktop-native expectation with no UI at all right now. |
| `JsonEditor` | **KEEP** as a control, **RESTYLE** its chrome | Needed by any advanced-config surface. |

### 4.7 Dead code — delete or wire, decide explicitly

| File | Lines | Status |
|---|---|---|
| `ServersView.axaml` | 12 | Orphan. Wraps `ServerListView`; no reference anywhere. **Delete.** |
| `CompactServersView.axaml` | 116 | Orphan referenced only in a comment. **Contains the app's only server search.** Harvest the search, then delete. |
| `ProfilesView.axaml` | 322 | Registered in `SimpleViewLocator:29`; `ProfilesViewModel` is never shown as a dialog. **Delete** (its interaction handlers were already re-implemented in `ServerListView.axaml.cs:71–133`). |
| `ClashProxiesView.axaml` | 158 | Registered, never built. Mihomo/Clash proxy-group control is simply absent from the product. **Decide: feature or delete.** |
| `ClashConnectionsView.axaml` | 104 | Same. Live connection list — a real desktop expectation. **Decide.** |
| `ThemeSettingView.axaml` | 67 | Registered, never built; superseded by Settings › Интерфейс. **Delete.** |
| `CheckUpdateView.axaml` | 95 | Registered, never built. **There is no "check for updates" in the shipping UI at all.** Wire it into Settings › О приложении. |
| `BackupAndRestoreView.axaml` | 213 | Registered, never built; superseded by `BackupPage`. **Delete.** |
| `ProviderSettingsPage.axaml` | 138 | Built and Incy-styled, zero references. **Wire or delete.** |
| `StatusBarView.axaml` | 125 | Mounted at `Width=0 Height=0 Opacity=0` (`MainWindow.axaml:643–651`) purely to keep its interaction handlers and `StatusBarViewModel` alive. **Refactor**: move the handlers to the shell, delete the phantom view. |
| `MsgView.axaml` | 104 | See 4.5. |

---

## 5. Appendix — file map

```
v2rayN.Desktop/
├── App.axaml (49)  App.axaml.cs (707)            ← theme engine + tray + mono overlay
├── Assets/
│   ├── GlobalResources.axaml (569)               ← ALL tokens + 4 ControlThemes
│   ├── GlobalStyles.axaml (1448)                 ← ALL component styles + lite mode
│   ├── Fonts/SpaceGrotesk.ttf, NotoSansSC-Regular.ttf
│   └── Flags/*.png (16 + xx.png)
├── Common/  L.cs (341) L.Account (226) L.Settings (185) L.Common (76)
│            L.Buy (68) L.Home (62) L.Shell (39) L.Servers (35)
│            LocExtension (65) Motion.cs MotionState.cs UiScaleState.cs UI.cs …
├── Manager/ WindowDialog.cs HotkeyManager.cs
├── ViewModels/ AccountViewModel BuyViewModel DevicesViewModel HomeViewModel
│               PaymentHistoryViewModel SettingsViewModel ThemeSettingViewModel
└── Views/  (56 .axaml — see §1)
```

Localisation: `Common/L*.cs` register `(key, ru, en)` triples consumed by the `{loc:T Key}` markup
extension. **Russian and English only.** Stratum-B windows bypass this entirely and use
`ServiceLib.Resx.ResUI`.

---

## 6. What the 2026 spec has to answer

1. **Gradients and glow** — amend the law for two named brand surfaces, or replace them. Pick one,
   write it down, apply it identically on Android and PC.
2. **Where do servers live** — a 4th destination, or a permanent column in Home? The answer changes
   the rail, the bottom bar, the compact scroll, and whether search has a home.
3. **The auth screen** — one method promoted, five disclosed. Specify the disclosure component.
4. **The upstream stratum** — which 6 editors get rebuilt as sub-pages, which 4 get their features
   migrated into Settings and then deleted. Nothing may remain reachable and unstyled.
5. **The feedback channel** — the owner rejected toasts. Specify what replaces them, because right
   now nothing does.
6. **Back semantics** — Escape, mouse-back, per-tab stacks, and what a "route" is.
7. **File layout** — real `Views/Themes/` and `Views/Styles/` directories, mono theme as data not C#.
