# pc-settings - desktop settings tree and sign-in surface, audit

Branch `claude/app-audit-agents-hyyftk`. Build root `/home/user/v2rayN/v2rayN`. Read-only audit; no
source file was edited. Law: `docs/design2026/00-rules.md` sections 1-10, 12, 13, 14, 15, 16, 18.
Specs: `12-settings.md` (row by row), `14-auth.md` (sign-in state machine), `20-control-survey.md`,
`33-master-plan-pc.md`.

Files audited (all under `/home/user/v2rayN/v2rayN/v2rayN.Desktop/`):
`Views/SettingsView.axaml{,.cs}`, `OptionSettingWindow.axaml{,.cs}`, `PingSettingsPage.axaml{,.cs}`,
`ProviderSettingsPage.axaml{,.cs}`, `DnsSubView.axaml{,.cs}`, `RoutingSubView.axaml{,.cs}`,
`RoutingRuleSettingWindow.axaml{,.cs}`, `RoutingRuleDetailsWindow.axaml{,.cs}`,
`GeoFilesPage.axaml{,.cs}`, `UrlSchemesPage.axaml{,.cs}`, `PerAppProxyPage.axaml{,.cs}`,
`BackupPage.axaml{,.cs}`, `BackupAndRestoreView.axaml{,.cs}`, `ThemeSettingView.axaml{,.cs}`,
`GlobalHotkeySettingWindow.axaml{,.cs}`, `ClashProxiesView.axaml`, `ClashConnectionsView.axaml`,
`LoginView.axaml{,.cs}`, `OnboardingView.axaml{,.cs}`, `CheckUpdateView.axaml`,
`AboutPage.axaml{,.cs}`, `Views/ISubPage.cs`, `Common/SimpleViewLocator.cs`, `Common/L.Settings.cs`,
`Common/L.Shell.cs`, `Common/UiScaleState.cs`, `ViewModels/SettingsViewModel.cs`. Cross-read for
evidence only: `Assets/GlobalStyles.axaml`, `Assets/GlobalResources.axaml`, `Views/MainWindow.axaml.cs`,
`ServiceLib/Manager/TaskManager.cs`, `ServiceLib/Models/Configs/ConfigItems.cs`.

---

## 1. Verdict - five dimensions (`00-rules.md` 17.1, ship bar >= 18/20, no dimension below 3)

| # | Dimension | Score | Why |
|---|---|---|---|
| 1 | Accessibility | **1** / 4 | No Esc, no Ctrl+F, no Ctrl+`,`, no mouse-4 back anywhere in the shell - a sub-page is dismissable only by clicking one 40px arrow. `TextBox.Incy` draws its boundary with `Brush.OutlineVariant` (1.16:1), failing the 3:1 control-boundary floor (D-9) on every field in the tree. `Button.Tonal.Tall` and 10 other controls set fixed `Height`, which clips at 200% DPI (R2). Settings rows have no pressed state at all. Login fields have no label - the watermark is the label. |
| 2 | Performance | **3** / 4 | No virtualisation problem in the settings tree (no long lists). `SettingsViewModel` subscribes to `UiScaleState.Changed` and `Vm.PropertyChanged` with no unsubscribe, on the assumption of a single keep-alive instance; `SettingsView`'s constructor allocates a fresh `SettingsViewModel` every time the view is constructed, so the assumption is a comment, not a guarantee. |
| 3 | Appearance and theming | **2** / 4 | 25 `Border.Card` group wrappers in a tree the spec says has zero cards. `LoginView`/`OnboardingView` paint a `RadialGradientBrush` page background. One accent (blue) tile on the hub's mode row. 22 unstyled `Button`/`ComboBox`/`CheckBox`/`RadioButton`/`ListBox`/`DataGrid` instances leak the default Semi look. Three parallel row grammars. |
| 4 | Platform conformance | **2** / 4 | The sub-page skeleton is copy-pasted nine times instead of shared; `Button.IconButton:pressed scale(0.92)` is redeclared verbatim in 9 files of this set (V7) against a D-11 value of 0.97. Cycle-in-place (`unfold_more`) survives on 4 rows after D-S2 retired it. The inline-expand local-proxy panel survives after D-S3 retired it. Two trailing elements on 8 hub rows. |
| 5 | Adaptivity | **2** / 4 | The hub has no `MaxWidth`; rows run edge to edge. Four sub-pages (`PingSettingsPage`, `GeoFilesPage`, `BackupPage`, `AboutPage`) have **no `ScrollViewer` at all** - at 900x600 or 150% UI scale their content is simply cut off with no way to reach it. Sub-pages cap at 620, not 720. |

**Total 10 / 20.** Below the bar on every dimension. Sections 4 to 9 below carry the evidence.

---

## 2. Canonical-view determination

Several views overlap. A rebuild aimed at a dead one wastes the whole effort. Determined by tracing
what constructs each (grep for `new <View>(`, plus `Common/SimpleViewLocator.cs` registrations, plus
`ServiceLib/ViewModels/MainWindowViewModel.cs` dialog-interaction sites).

| View | Constructed by | Verdict |
|---|---|---|
| `SettingsView.axaml` | `MainWindow.axaml.cs` tab host (keep-alive) | **LIVE - canonical settings hub.** All settings work targets this file. |
| `SettingsViewModel.cs` | `SettingsView` ctor, `new SettingsViewModel()` | **LIVE - canonical settings VM.** |
| `PerAppProxyPage`, `DnsSubView`, `PingSettingsPage`, `RoutingSubView`, `GeoFilesPage`, `AboutPage`, `BackupPage`, `UrlSchemesPage` | `SettingsView.axaml.cs:43-50` `OpenPage(new …)` | **LIVE - the 8 reachable sub-pages.** |
| `LoginView` | `MainWindow.axaml.cs:1218` `new LoginView { DataContext = _accountVm }` | **LIVE - canonical sign-in.** |
| `OnboardingView` | `MainWindow.axaml.cs` first-run host | **LIVE.** |
| `ProviderSettingsPage.axaml{,.cs}` (138 + 86 lines, fully built, fully styled) | **nothing** - 0 instantiations, 0 locator entries | **DEAD.** Same finding as `12-settings.md` 0.3 ("built, styled, **zero references**"). Still zero. Do not restyle it in place; it is `settings/providers` waiting to be wired, and its auto-update section must be deleted first (5.7 / 4.4: auto-update has exactly one home). |
| `OptionSettingWindow` (1 206 lines, 91 `resx:` refs) | `ServiceLib/ViewModels/MainWindowViewModel.cs:725` via a dialog interaction | **REACHABLE ONLY FROM SERVICELIB.** The desktop shell never raises that interaction, so from the departament UI it is dead. Slated for deletion (9.7). |
| `GlobalHotkeySettingWindow` | `MainWindowViewModel.cs:211` (same shape) | Same - shell never raises it. Feature must migrate to `settings/window` (5.14) before deletion. |
| `FullConfigTemplateWindow`, `SubSettingWindow` | `MainWindowViewModel.cs:765`, `:707` | Same. |
| `RoutingRuleSettingWindow` / `RoutingRuleDetailsWindow` | `ServiceLib/ViewModels/RoutingSettingViewModel.cs:135`, `RoutingRuleSettingViewModel.cs:140` | **REACHABLE from `RoutingSubView`'s edit path.** 9.7 is explicit: delete only once `settings/routing/rule/{id}` exists. Today they are the sole rule editor. |
| `ThemeSettingView` | `ThemeSettingView.axaml.cs:13` sets its own VM; nothing constructs the view | **DEAD** - registered in the locator, never built (0.3 says delete). |
| `BackupAndRestoreView` | locator only | **DEAD** (0.3 says delete). |
| `CheckUpdateView` | locator only | **DEAD** - but 0.3 says wire it into `settings/about` (PG-S7), not delete. |
| `MsgView` | locator only | **DEAD** - 5.17 says rebuild as `settings/about/log`. |
| `ClashProxiesView` / `ClashConnectionsView` | locator only | **DEAD** - 6.1 cuts them with the whole Mihomo proxy-group surface. |

Consequence for the work order: everything in section 10 targets `SettingsView` + the 8 live
sub-pages + `LoginView`. `ProviderSettingsPage` is a wiring task, not a restyle task. Nothing may be
deleted before its replacement is reachable.

---

## 3. The hub, row by row, against `12-settings.md` section 4

`Views/SettingsView.axaml` ships **6 named groups and 16 rows**. The spec is **4 named groups + an
unnamed footer pair, 25 rows (23 at rest)**.

### 3.1 Groups

| Position | Shipped | Spec (4.1) | Verdict |
|---|---|---|---|
| 1 | `Подключение` (`Settings_SecConnection`) | `Подключение` | name matches, contents do not (3.2) |
| 2 | `Обход блокировок` (`Settings_SecBypass`) | `Обход блокировок` | matches |
| 3 | `Производительность` (`Settings_SecPerformance`) | **does not exist** | **P1** - a 5th named group; 2.5 caps named groups at 4. Its one row belongs in `Приложение` as `Меньше движения` (4.5 row 4.4) |
| 4 | `Интерфейс` (`Settings_SecInterface`) | `Приложение` (`Settings_SecApp`) | **wrong name**, wrong contents |
| 5 | `Подписка` (`Settings_SecSubscription`) | `Подписки` (`Settings_SecSubs`) | wrong number; contents wrong (holds `Маршрутизация`, which is a Подключение row) |
| 6 | `О приложении` (`Settings_About`) as a **named group** | the footer pair carries **no section header** (4.6) | **P2** - the header is what makes it structurally a group; 4.6 says it must not have one |
| - | missing | search field `Поиск по настройкам` (4.1, section 7) | **P1** - no search field exists on the hub |
| - | missing | 56 header row with `Настройки` + scroll hairline (4.1, 9.1) | **P1** - the hub has no header at all; it starts at the first section label |

### 3.2 Rows present, mapped to spec rows

`A1` navigation, `A2` value, `A3` toggle, `A4` segment, `A5` action, `A6` destructive (3).

| Shipped row (`x:Name`, line) | Trailing elements | Spec row | Verdict |
|---|---|---|---|
| `RowMode` :234 - `Режим`, segment `TUN` / `Прокси` | segment | 1.1 `Режим подключения`, A4, **3** segments `VPN` / `Прокси` / `Вместе` | label short; `TUN` is a Latin engine token where the spec ships `VPN`; third segment missing; `Вместе` behaviour (`AllowLANConn`) unreachable. Icon tile is `Classes="Tile Blue"` with an accent glyph - **the only coloured tile in the tree, banned by 12-settings law 2.2** |
| `RowPerApp` :274 - `Прокси по приложениям` | **value text + chevron (2)** | 1.2 A2 | **P1** two trailing elements (`00-rules.md` 4.5) |
| `RowBypassLan` :306 - `Обход локальной сети` + hint | switch | 1.5 A3 | correct archetype, **wrong binding** - see 4.1 |
| `RowIpv6` :340 - `IPv6` + hint | switch | 1.6 A3 | matches |
| `RowDns` :374 - `DNS` | **value + chevron (2)** | 1.4 A2 | **P1** two trailing elements |
| `RowPingMethod` :406 - `Пинг` | **value + chevron (2)** | belongs to group 3 as 3.3 `Проверка задержки` A1 | **P1** two trailing; wrong group; wrong noun (D-S5: `Пинг` -> `Проверка задержки`) |
| `RowLocalProxy` :438 - `Локальный прокси` + inline expand panel :475 | rotating chevron | moved to `settings/advanced/localproxy` (5.10); the inline-expand archetype is **retired by D-S3** | **P1** archetype retired; the panel is a 7th archetype |
| `RowMux` :532 - `Мультиплексирование (Mux)` | switch | 2.1 A3 | matches |
| `RowMuxConcurrency` :566 - `Число соединений Mux` (conditional) | **value + `unfold_more` (2)** | 2.2 A2 + picker | **P1** two trailing; **cycle-in-place retired by D-S2** |
| `RowFragment` :604 - `Фрагментация пакетов` | switch | 2.3 A3 | matches |
| - | - | 2.4 `Параметры фрагментации` A1 -> `settings/fragment` | **missing** - the three fragment parameters are unreachable |
| `RowLiteMode` :651 - `Облегчённый режим` | switch | 4.4 `Меньше движения` A3 | wrong group, wrong label, hint restates the mechanism |
| `RowAppearance` :698 - `Оформление`, segment `Тёмная` / `Светлая` | segment | 4.1 A4, **3** segments `Тёмная` / `Светлая` / `Системная` | third segment missing - a user cannot follow the system theme |
| `RowBlackTheme` :737 - `Монохром` | switch | 4.2 `Чёрная тема` A3 | **D-S5 label violation**; hint restates the title |
| `RowUiScale` :773 - hardcoded literal `Масштаб интерфейса` | **value + `unfold_more` (2)** | 4.6 A2 + picker (`100%` / `110%` / `125%` / `150%`) | **P1** two trailing; cycle retired; **the label is a hardcoded Russian string literal, not a `loc:T` key** - it stays Russian in English. `ToolTip.Tip` on the same row is also a hardcoded literal and contains two em dashes and a U+2212 minus |
| `RowLanguage` :808 - `Язык` | **value + `unfold_more` (2)** | 4.3 A2 + picker (`Системный` / `Русский` / `English`) | **P1** two trailing; cycle retired; `Системный` unreachable |
| `RowBoot` :840 - `Запуск при загрузке` | switch | 4.5 `Запуск при старте` A3 | **D-S5 label violation** |
| `RowSubAutoUpdate` :885 - `Автообновление подписки` | **value + `unfold_more` (2)** | 3.1 `Автообновление подписок` A2 | **P0 - the row does not do what it says.** See 4.2 |
| `RowRouting` :917 - `Маршрутизация` | chevron | 1.3 A1, group **Подключение** | wrong group |
| `RowAssets` :944 - `Файлы ресурсов` | chevron | 3.5 A1 | matches |
| `RowAbout` :982 - `О приложении` | **value + chevron (2)** | footer pair, A1, version as **helper** not as value | **P1** two trailing; version rendered as an A2 value, not the row helper |
| `RowBackup` :1014 - `Резервное копирование` | chevron | footer `Данные и резервные копии` A1 | wrong noun |
| `RowUrlScheme` :1041 - `Схемы URL-адресов` + hint | chevron | 5.16, reached **from About**, not from the hub | wrong home - a `depv://` cheat sheet in the consumer hub is the category error 5.16 names |

### 3.3 Spec rows with no shipped row at all

`Дополнительно` (1.7 -> `settings/advanced`, and everything under it: `Ядро`, `Уровень журнала`,
`Определение домена в трафике`, `Только для маршрутизации`, `Разрешать небезопасные соединения`,
`Переключать сервер при сбое`, `MTU`, `Адрес интерфейса`, `Локальный прокси`, `Шаблон конфигурации`),
`Параметры фрагментации` (2.4), `Обновлять при запуске` (3.2), `Провайдеры` (3.4),
`Окно и горячие клавиши` (4.7 -> `settings/window`: tray-on-close, start minimised, show in Dock, four
hotkey capture rows, `Сбросить сочетания`), `Данные и резервные копии` as a route (`settings/data`:
`Создать копию`, `Восстановить из копии`, `Облачная копия`, `Сбросить настройки`).

**Count: 8 of 17 spec routes exist. 9 do not.** Every setting behind them is unreachable from the UI,
which is the count `12-settings.md` 6.5 requires to be 0.

### 3.4 Two trailing elements - the single most repeated structural defect

`00-rules.md` 4.5 and `12-settings.md` 2.4: "One trailing element per row. Never two."

8 hub rows ship two: `RowPerApp`, `RowDns`, `RowPingMethod`, `RowAbout` (value + chevron);
`RowMuxConcurrency`, `RowUiScale`, `RowLanguage`, `RowSubAutoUpdate` (value + `unfold_more`).

The affordance contract in `SettingsView.axaml.cs:14-22` is honest about *which* glyph means what -
that part is the good decision `12-settings.md` 2.3 praises - but it pairs each glyph with a value,
which is the thing the rule forbids. The A2 archetype's trailing element **is** the value text; the
picker is what the tap opens. No glyph is needed.

---

## 4. Which settings rows lie

A row lies when the control is bound to a field nothing reads, or to a field that does something
other than what the label promises. Three found, one of them severe.

### 4.1 `Обход локальной сети` writes the opposite direction of traffic - **P1**

`ViewModels/SettingsViewModel.cs:160` reads and `:222` writes `Inbound[0].AllowLANConn`:

```csharp
BypassLan = inbound?.AllowLANConn ?? false;      // :160
inbound.AllowLANConn = v;                        // :222
```

`AllowLANConn` makes the local SOCKS5/HTTP proxy listen on the LAN interface so **other machines can
use it**. The label and helper promise the opposite: `Прямой доступ к устройствам в локальной сети`,
i.e. route traffic **to** LAN addresses outside the tunnel. This is `12-settings.md` 6.4 **B1**,
documented and still live. Android's `PREF_VPN_BYPASS_LAN` does the documented thing, so the two
platforms ship one label over two unrelated behaviours - exactly what the parity contract exists to
prevent. Correct binding: `TunModeItem.RouteExcludeAddress` seeded with the six private ranges;
`AllowLANConn` becomes `Доступ из локальной сети` on `settings/advanced/localproxy` plus the hub's
`Вместе` mode.

### 4.2 `Автообновление подписки` configures geo-file updates, in the wrong unit - **P0**

Two independent faults on one row.

**(a) Wrong feature.** `SettingsViewModel.CycleAutoUpdateAsync` (:424-438) writes
`_config.GuiItem.AutoUpdateInterval`. That field has exactly one consumer in the whole engine:

```csharp
// ServiceLib/Manager/TaskManager.cs:65
await UpdateTaskRunGeo(numOfExecuted / 60);
// ServiceLib/Manager/TaskManager.cs:111-121
private async Task UpdateTaskRunGeo(int hours)
{
    if (_config.GuiItem.AutoUpdateInterval > 0 && hours > 0 && hours % _config.GuiItem.AutoUpdateInterval == 0)
        … UpdateGeoFileAll();
}
```

It is the **geo-database** update interval. Subscription auto-update is driven per feed by
`SubItem.AutoUpdateInterval` (`TaskManager.cs:84-85`), which this row never touches. Changing the row
has no effect whatsoever on subscription refresh. `12-settings.md` 4.4 row 3.1 requires the row to
write every `SubscriptionItem.autoUpdate` + `.updateInterval`.

**(b) Wrong unit, by a factor of 60.** `SettingsViewModel.cs:35-36` declares the option set as
minutes and `ResolveAutoUpdateText` (:604-609) divides by 60 to render hours:

```csharp
private static readonly int[] AutoUpdateOptions = [60, 360, 720, 1440];   // "== 1/6/12/24 ч."
return n > 0 ? Common.L.F("Common_HoursShort", n / 60) : Common.L.T("Common_Off");
```

`UpdateTaskRunGeo`'s parameter is **hours of process uptime**. So the row that displays `24 ч.`
stores 1440 and the geo update fires when uptime hits a multiple of 1440 hours - 60 days of
continuous running. Even the row's own lowest option, displayed `1 ч.`, stores 60 and needs 60 hours
of uptime. Every option on this row is unreachable in practice.

**(c)** The option array has no `0`, so `Выключено` (spec 4.4: the first picker option) cannot be
selected, and the XML doc comment on the method claims a fifth option set ("Выкл / 6 / 12 / 24 / 48 ч")
that matches neither the array nor the rendered label.

### 4.3 `Язык` cannot reach `Системный`, and cycles through two of eight values - **P2**

`CycleLanguageAsync` (:442-458) is `CurrentLanguage == "en" ? "ru" : "en"`, while
`ResolveLanguageText` (:590-602) can render eight languages. A config carrying `zh-Hans` displays
`简体中文` and the first tap silently rewrites it to `en`. The spec's option set is
`Системный` / `Русский` / `English`; `Системный` has no representation in the config at all.

### 4.4 Rows that are honest but under-bound

- `Пинг` value is resolved from `_config.SpeedTestItem.PingMethod` (:560-566) and renders `HTTP` and
  `ICMP`, two methods `12-settings.md` 6.2 removes from the product (pending D-S13). The sub-page
  offers only `Реальная` and `TCP`, so a config carrying `Httping` shows a value the sub-page cannot
  reproduce or clear.
- `Масштаб интерфейса` (`CycleUiScale`, :491-510) writes `UiItem.UiScale` and pushes `UiScaleState` -
  honest and live. Its option set is 8 presets (0.8 to 2.0) where the spec names 4 (`100%` to `150%`).
- Local-proxy port commit (`CommitLocalProxyAsync`, :369-403) rejects an out-of-range port by
  **silently** reverting the field. `12-settings.md` 10 requires the error state to name the cause
  (`Введите порт от 1 до 65535`, 11.5). Silent revert is the "UI never shows a value the store does
  not hold" half of the rule with the "say why" half missing.

---

## 5. Sub-page conformance, against `12-settings.md` section 5

One shared skeleton is required (9.3): `Border.SubToolbar` 56, `Button.BackNav` 48, title at
Title 16/700, one `ScrollViewer`, content column 720, gutter 16.

| Page | Toolbar | Title ramp | Scroll | MaxWidth | Cards | Spec route | Rows shipped / spec |
|---|---|---|---|---|---|---|---|
| `PerAppProxyPage` | hand-rolled `Grid` | `Headline` 24 | 1 | 620 | 2 | 5.1 | partial |
| `RoutingSubView` | hand-rolled `Grid` | `Headline` 24 | 1 | 620 | 3 | 5.2 | partial; edit path escapes into `RoutingRuleSettingWindow` (900x600, `resx` strings, 27 refs) |
| `DnsSubView` | hand-rolled `Grid` | `Headline` 24 | 1 | 620 | 3 | 5.4 | 1 of 4 groups: chips + FakeIP only. Missing `DNS для прямых соединений`, `Записи hosts` |
| `PingSettingsPage` | hand-rolled `Grid` | `Headline` 24 | **0** | 620 | 3 | 5.6 | 2 method rows + 2 fields. Missing `Одновременных проверок` and the whole `Автоматически` group (4 toggles) |
| `ProviderSettingsPage` | hand-rolled `Grid` | `Headline` 24 | 1 | 620 | 3 | 5.7 | **unreachable**; contains a second home for auto-update, which 4.4 forbids |
| `GeoFilesPage` | hand-rolled `Grid` | `Headline` 24 | **0** | 620 | 1 | 5.8 | 2 rows + 1 button. Missing `Источник обновлений`, `Свои файлы`, empty state |
| `BackupPage` | hand-rolled `Grid` | `Headline` 24 | **0** | 620 | 1 | 5.11 | 2 rows. Missing `Облачная копия`, `Сбросить настройки` + its confirm dialog |
| `AboutPage` | hand-rolled `Grid` | `Headline` 24 | **0** | 620 | 2 | 5.15 | 2 links + details. Missing `Проверить обновления`, `Для разработчика` group, `Правовое` group |
| `UrlSchemesPage` | hand-rolled `Grid` | `Headline` 24 | 1 | 620 | 1 | 5.16 | reached from the **hub**, spec says from About |
| `LoginView` | `Border.SubToolbar` (correct) | `Headline` 24 | 1 | 440 | 0 | `14-auth` B | section 7 |
| `OnboardingView` | - | - | 1 | 440 | - | - | shares the banned gradient background |

Systemic, on all nine settings sub-pages:

1. **The toolbar is hand-rolled nine times.** Each is
   `<Grid DockPanel.Dock="Top" MinHeight="56" Margin="16,8,16,0" ColumnDefinitions="Auto,*">` with a
   `Button Classes="IconButton" Width="40" Height="40"`. The shared `Border.SubToolbar` class exists in
   `Assets/GlobalStyles.axaml:1210` and is used only by `LoginView`. 9.3 requires one base.
2. **The legacy 32px `Button.IconButton` class is still the back button** on all nine, with a local
   `Width/Height 40` override, instead of `Button.BackNav` (which exists,
   `Assets/GlobalStyles.axaml:1237`, and already carries the correct 0.97 press).
3. **`Button.IconButton:pressed { RenderTransform: scale(0.92) }` is redeclared verbatim in 9 of these
   files** (V7). D-11 fixes press scale at 0.97 everywhere. Across `Views/` the one gesture is drawn
   six ways: 0.92 x13, 0.94 x1, 0.96 x2, 0.97 x16, 0.98 x1, 0.99 x4.
4. **Title uses `Classes="Headline"` (24/700).** The sub-page toolbar title is Title 16/700
   (`00-rules.md` 4.8, `12-settings.md` 5 skeleton). At 24px in a 56 bar with `TextTrimming` on, a long
   Russian title (`Прокси по приложениям`, `Схемы URL-адресов`) truncates rather than fits.
5. **Content caps at 620.** The measure is 720 (`00-rules.md` 4.1, 12-settings 5). 620 is the third of
   the three measures `12-settings.md` 1 counts as a defect.
6. **Four pages have no `ScrollViewer`.** `PingSettingsPage`, `GeoFilesPage`, `BackupPage`,
   `AboutPage` are `DockPanel > Grid + StackPanel`. Content taller than the viewport is clipped with
   no scroll path. See section 9.
7. **Every group is a `Border.Card`.** 25 in the audited set. `12-settings.md` law 2.1 and D-S4: zero
   cards in the settings tree.

---

## 6. The state matrix (`00-rules.md` 15, `12-settings.md` 10)

Y = implemented, `-` = not applicable, blank cell reason given.

| Surface | Default | First run | Loading | Empty | Error | Offline | Partial | Long | Short | Disabled/gated | Success |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Hub `SettingsView` | Y | - (correct, 10) | **missing** | - | **missing** - a failed `SaveConfig` is swallowed; the control keeps the new value the store rejected | **missing** | - | partial - `TextBlock.RowValue` has `MaxWidth="150"`, a fixed cap where the spec says 40% of the row | Y | **missing** - no row is ever disabled with a reason | partial - value crossfade only |
| `PerAppProxyPage` | Y | - | **missing** (spec: 8 skeleton rows) | **missing** (search no-match) | **missing** | - | - | Y | Y | **missing** - list must render 0.38 with `Включите раздельное туннелирование` | Y |
| `RoutingSubView` | Y | - | **missing** | **missing** (`Наборов правил пока нет`) | **missing** | - | - | Y | Y | - | Y |
| `DnsSubView` | Y | - | - | - | **missing** - invalid DoH silently accepted, no `Проверьте адрес DNS-сервера` | - | - | Y | - | - | Y |
| `PingSettingsPage` | Y | - | - | - | **missing** - timeout field has no range validation | - | - | Y | - | **missing** - `Одновременных проверок` should disable under TCP | Y |
| `GeoFilesPage` | partial - both rows ship the literal `—` as their subtitle | - | partial - `Geo_Downloading` text only, no per-row `Обновление…`, no button loading state | - | text-only via `txtStatus`, no inline placement, no `Повторить` | **missing** | **missing** - one base updated, one failed | Y | - | **missing** | text only |
| `BackupPage` | Y | - | text only | - | text only, no recovery action | - | - | Y | - | - | text only |
| `AboutPage` | partial - runtime block ships `—` | - | - | - | - | - | - | Y | - | - | - |
| `UrlSchemesPage` | partial - status ships `—` | - | - | - | text only | - | - | Y | - | Y (non-Windows) | Y |
| `LoginView` | Y | Y | Y (spinner over pinned CTA - correct) | - | Y (error line + field flash) | **missing** - no `Нет подключения к интернету` path distinguishable | - | partial | - | Y (Google `Скоро`) | Y |

Product-specific gate states (`00-rules.md` 15): `нет подписки`, `подписка истекает/истекла`, `триал`,
`Telegram не привязан`, `лимит устройств` are all account-tab concerns and correctly absent here.
`нет серверов` is a Серверы concern. The settings-relevant gate - **hub 3.1 with zero subscriptions
must be disabled, read `Нет подписок` and say `Добавьте провайдера, чтобы включить`** - is not
implemented; the row cycles a value that changes nothing regardless.

**Missing states, counted: 31.** The biggest cluster is error: no row in the tree reverts and explains
on a failed write, which is `12-settings.md` 3.1 and 10 verbatim.

---

## 7. The sign-in surface against `14-auth.md`

`LoginView.axaml` (954) + `.axaml.cs` (1 377). Owner request 0.4.10 puts this screen in the
redesigned-from-scratch category.

### 7.1 The forms law (`00-rules.md` 7.4), in full

| Clause | Shipped | Verdict |
|---|---|---|
| Field 56 min height, radius 16 (D-7), 1px `color_outline_control` border, `color_surface_inset` fill | `TextBox.Incy` (`Assets/GlobalResources.axaml:548-561`): `MinHeight 52`, `CornerRadius = Radius.Search` **14** (retired by D-7), `BorderBrush = Brush.OutlineVariant` **#20242B, 1.16:1**, `Background = Brush.SurfaceVariant` | **P1** - the border fails the 3:1 control-boundary floor (D-9). Radius is the retired 14. |
| **Label above the input, always visible. Placeholder is never the label.** | `EmailBox` :346 `Watermark="{loc:T Login_Email}"`, `PasswordBox` :363 `Watermark="{loc:T Login_Password}"`, `ConfirmPasswordBox` :409 `Watermark="{loc:T Login_ConfirmPassword}"` - **no label element exists for any of the three** | **P1 x3.** `14-auth.md` 6.2 specifies `lbl_email` «Электронная почта» + placeholder `name@example.com`, and `lbl_password` «Пароль». Shipped: the label IS the placeholder and vanishes on first keystroke. |
| Helper below, present in the markup even when empty so the layout does not jump | `EmailError` :353, `ConfirmPasswordError` :419, `RegisterPasswordHint` :399 all use `IsVisible="False"` | **P1** - `IsVisible=False` collapses the slot; `14-auth.md` 6.2 says INVISIBLE never GONE, `12-settings.md` 9.5 says `Opacity=0` not `IsVisible=False`. The form jumps by ~18px when an error appears. |
| Validate on blur, not per keystroke | `.axaml.cs` validates live (the comment on :352 says «Живая подсказка невалидного email») | **P2** - the one permitted live exception is register password strength |
| Error text below the field, `Brush.RedText`, field border red | Yes - `TextBox.fieldError` :122 sets `PART_BorderElement` to `Brush.Red`, error text `Brush.RedText` | correct |
| After a failed submit, focus moves to the first invalid field | not implemented | **P2** |
| Correct input type + autofill hints | no `autofillHints` equivalent; no `InputScope` | **P2** |
| Password field has a show/hide toggle | Yes, `TogglePasswordButton` :372 | correct |
| Submit disabled while in flight, shows loading | Yes, spinner over a pinned-width CTA (:447, :478) - the R8 pattern done correctly | correct |

### 7.2 Structure against `14-auth.md` 3 and 6.2

| Spec | Shipped | Verdict |
|---|---|---|
| Surface **A - the gate**, inside the Аккаунт tab: one filled `Войти через Telegram` + tertiary `Войти по почте`. No push. | No gate surface. `MainWindow.axaml.cs:1218` pushes `LoginView` directly. | structural gap |
| Surface **B** segment is the **method** segment `Пароль` / `Ссылка на почту` (6.4) | The segment is the **mode** segment `Вход` / `Регистрация` (:328-341); mode is what the spec puts on a tertiary button (`btn_switch_mode`), and the magic link is a text link in `PasswordlessLinks` (:500) | inverted vs spec |
| Surface **C - `Другой способ входа`** as a sheet, entered from one navigation row (6.2 `row_other_methods`) | All alternates inlined under an `или` divider (:521-644): Telegram, сайт, «у меня есть код», Google-`Скоро` | inlined, not a sheet |
| Toolbar title Title 16/700 | `Classes="Headline"` 24/700 (:255) | ramp defect |
| Column max 440 | 440 (:270) | correct |
| Card count zero | zero | correct |
| Exactly one filled accent surface at any instant | `SiteButton`/`RegisterSubmitButton` `Classes="Primary Tall"` is the only fill; the 64px shield tile is `Brush.Tile.Blue` (20% accent) | correct |

### 7.3 Ban and token hits inside `LoginView`

| Line | Hit | Rule |
|---|---|---|
| :237 | `Background="{DynamicResource Brush.HomeGradient}"` - a `RadialGradientBrush` (`GlobalResources.axaml:147-155`, stops `#1B2D50` / `#0E141F` / `#0A0B0D`) painted as the page background | **6.5 / 1.4.3 - no gradients on backgrounds.** Same hit in `OnboardingView`. **P1** |
| :88-89 | `Button.SegItem` sets `FontFamily="{DynamicResource Font.Grotesk}"` + `FontSize 14`; its content is «Вход» / «Регистрация» | **5.1 - a Russian string in the Cyrillic-free brand face is a P1 defect**, plus 12.1 inline FontSize |
| :103 | `FontWeight="SemiBold"` (600) | 5.4 - real weights only, 400/500/700. No 600 |
| :63-65 | `Button.Tonal.Tall { Height 52 }` - fixed `Height` | **3.3 R2 - MinHeight never Height. Clips a two-line label at 200% DPI. P1** |
| :73 | `Border.SegTrack { Height 44 }` fixed, and off the size scale | 3.3; the segmented track is 48 (`12-settings.md` 9.2) |
| :76 | `SegTrack CornerRadius = Radius.Chip` 12 | D-7 - the segmented **track** is 16, the thumb is 12 |
| :79, :144 | `Button.SegItem { Height 36 }`, `Border.CodeCell { Height 52 }` fixed | 3.3 R2 |
| :112 | `Border.SoonPill CornerRadius 8` | 3.2 - a chip is 12; 8 is not in the shape lock |
| :145 | `Border.CodeCell Margin="3,0"` | 1.4.5 - 3 is off the spacing scale |
| :167, :682 | `FontSize 20` on the OTP digit | 3.4 - 20 is not a ramp step |
| :203-226 | `PathIcon.PlaneBreathe.breathing`: infinite opacity 1 -> 0.55 + scale 1 -> 0.94, **1.6 s** | **8.1 decorative motion + 8.2 off-token duration.** `motion_pulse` 1000 is the only looping opacity animation the product permits (3.7). **P1** |
| :180-194 | spinner `Duration="0:0:1.1"` linear | correct value, but a raw literal instead of `Dur.Spin`; lite handled by selector - good |

Reduced motion is handled correctly throughout `LoginView` by the `:is(Window):not(.lite)` selector
prefix, which is the live-broadcast pattern `12.5` requires. This is the one motion area of the file
that is right.

---

## 8. Keyboard completeness

`00-rules.md` 12.2 and 14.8: "Standard shortcuts work: Esc closes a flyout or modal, Enter submits a
form, Ctrl+F focuses search, Ctrl+, opens settings", "Nothing is reachable only by mouse", "every task
is completable without a mouse". `12-settings.md` 9.3 adds mouse button 4 for back.

`Views/MainWindow.axaml.cs:1899-1951` is the entire shell keyboard surface:

| Shortcut | Required by | Shipped | Verdict |
|---|---|---|---|
| `Esc` pops the current sub-page | 12.2, 7.6, `12-settings.md` 5 and 9.3 (gap V12) | **absent** - no `Key.Escape` case anywhere in `MainWindow.axaml.cs`; grep `Key.Escape` in `Views/` returns nothing | **P1.** Every sub-page and the sign-in screen is dismissable only by clicking one 40px arrow. |
| Mouse button 4 pops the sub-page | 13 translation table, `12-settings.md` 9.3 | **absent** - `XButton1` appears nowhere | **P1** |
| `Ctrl+F` focuses settings search | 12.2, `12-settings.md` 7.1 | **absent** (and there is no search field to focus) | **P1** |
| `Ctrl+,` opens settings | 12.2 | **absent** | **P1** |
| `Enter` submits the form | 7.4, 12.2 | present in `LoginView` (email -> password -> submit chain in `.axaml.cs`); **absent** in every settings field - `ProxyPortBox` commits only on `LostFocus` or panel collapse | **P2** |
| `Ctrl` `+` / `-` / `0` UI scale | project shortcut | present (:1904-1928) | correct |
| `Ctrl+V` add server from clipboard | project shortcut | present (:1935) | ok - `TextBox` marks paste handled, so it does not fire while typing |
| `Ctrl+S` screen QR scan | project shortcut | present (:1939) | **P2** - `TextBox` does **not** handle `Ctrl+S`, so it bubbles: pressing Ctrl+S while typing a DNS address or a password hides the window and screenshots the desktop |
| `F5` reload | project shortcut | present (:1946) | ok |

Tab reachability inside the hub is good: `WireRow`/`WireToggleRow` (`SettingsView.axaml.cs:107-139`)
set `Focusable`/`IsTabStop` on the row and remove the switch from the tab order, with Enter/Space
activation - this is the correct desktop translation of `12-settings.md` 3.1 ("the **row** owns the tab
stop"). `Border.SettingRow` carries a 2px accent `FocusAdorner` that survives lite mode
(`GlobalStyles.axaml:963-970`). Both are right and must be preserved through any rebuild.

What is not reachable by keyboard: the `Border.DnsChip` preset chips in `DnsSubView` (plain `Border`s
with a `Cursor=Hand`, no `Focusable`, no `IsTabStop`, no focus adorner) and the `Border.MethodRow`
rows in `PingSettingsPage` (same). Both are **P1** under 14.4 and 12.2.

---

## 9. What clips at 900x600, and nested scrollers

- **Nested scrollers: none found in the live settings tree.** `SettingsView.axaml` greps as 2
  `<ScrollViewer` but the second is `PART_ScrollViewer` inside the `TextBox.IncyField` control
  template, which is a template part, not a page scroller. `OptionSettingWindow` has 3 real ones and is
  dead code.
- **Four live sub-pages have zero scrollers and will clip:** `PingSettingsPage` (intro + 2 method rows
  + 2 field cards ~ 430px of content), `GeoFilesPage`, `BackupPage`, `AboutPage`. At the 900x600
  minimum the shell's chrome and rail leave roughly 520px of page height; at `UiScale 150%` (a shipped
  preset, `SettingsViewModel.cs:42`) the same content measures ~645px and the bottom control - the
  `Обновить сейчас` button on `GeoFilesPage`, the `Копировать сведения` button on `AboutPage` - is off
  screen with **no scroll path to reach it**. That is 14.5 (a lost action at scale) and 12.3
  (usable at 900x600 with no clipping). **P1.**
- **The hub has no `MaxWidth`.** `SettingsView.axaml:216` is a bare `ScrollViewer`. At the app's own
  1120x760 preset the rows run about 1030px with a 40 tile hard left and a value hard right - the
  defect `12-settings.md` 9.1 change 1 names. **P1.**
- **`TextBlock.RowValue MaxWidth="150"`** (`SettingsView.axaml:200`) is a fixed cap where the spec says
  40% of the row. At 900px wide it under-uses the row; at 150% scale it truncates a value that would
  fit. **P2.**
- **Sub-page titles at `Headline` 24 in a 56 bar** with `TextTrimming="CharacterEllipsis"`: at 150%
  scale `Прокси по приложениям` and `Схемы URL-адресов` truncate. `00-rules.md` 1.1 bans a truncated
  primary label. **P2.**
- **`Border.SubToolbar` is correctly `MinHeight`** (`GlobalStyles.axaml:1212`), so the bar itself grows.
  The nine hand-rolled toolbars use `MinHeight="56"` too - correct - but their 40x40 back buttons are
  fixed `Width`/`Height`.

---

## 10. Grep numbers (file set of this audit, 2026-07-26)

Run from `/home/user/v2rayN/v2rayN/v2rayN.Desktop`.

| Check (`00-rules.md` 1.5) | Result in this file set | Bar |
|---|---|---|
| `grep -rn 'StaticResource Brush\.' Views/` | **0** (whole `Views/`) | 0 - clean, keep it |
| `grep -rnE '(Background\|Foreground\|BorderBrush\|Fill\|Stroke)="#" Views/` | **0** in this set (3 elsewhere: `DevicesView:451`, `MainWindow:308`, `ConnectHeroView:526`) | 0 |
| raw hex on **any** attribute in this set | **1** - `SettingsView.axaml:78 SelectionBrush="#334C8DFF"` (the 1.5 grep misses `SelectionBrush`) | 0 |
| `FontFamily=` / `FontSize=` attribute form | **3** - `UrlSchemesPage:95 FontFamily="Consolas,monospace"`, `LoginView:680 Font.Numeric`, `LoginView:682 FontSize="20"` | 0 |
| `Property="FontSize"` / `Property="FontFamily"` setter form (the 1.5 grep misses this shape) | **7** - `SettingsView:75` (15), `DnsSubView:53-54` (Grotesk + 14), `LoginView:88-89` (Grotesk + 14), `LoginView:165,167` (Numeric + 20) | 0 |
| off-scale `Margin`/`Padding`/`Spacing` (allowed 0/4/8/12/16/24/32) | **32 occurrences, 9 distinct bad values**: 1 x3, 2 x4, 3 x2, 6 x14, 10 x4, 14 x1, 18 x1, 20 x2, 28 x1 | 0 |
| `Classes="Card"` in the settings tree | **25** | **0** (`12-settings.md` law 2.1, D-S4, acceptance checklist "Zero cards in the settings tree") |
| em dash / en dash in user-visible strings | **14 sites** - `SettingsView:776` (ToolTip, 2 em dashes + a U+2212 minus), `ProviderSettingsPage:109`, `GeoFilesPage:73,80`, `UrlSchemesPage:74`, `AboutPage:95` (all `Text="—"`), `L.Settings.cs:86,104,105,112,128,132,147,168` (8 keys x 2 languages = 16 shipped strings) | 0 |
| emoji in this file set | **0** | 0 - clean |
| three-dot `...` where `…` belongs | **0** | 0 - clean |
| duplicated local `Button.IconButton:pressed` blocks | **10 files** (9 in this set + `DevicesView`) | 1 shared block (V7) |
| distinct press-scale values across `Views/` | **6**: 0.92 x13, 0.94 x1, 0.96 x2, 0.97 x16, 0.98 x1, 0.99 x4 | 1 (0.97, D-11) |
| fixed `Height` setters in this set | **11** | 0 (R2: `MinHeight` only) |
| unstyled `Button` (no `Classes`) in this set | **~22** across 15 files | 0 (12.1: no default Fluent/Semi leakage) |
| unstyled `ComboBox` / `CheckBox` / `RadioButton` / `ListBox` / `DataGrid` | **28** (`ProviderSettingsPage:90`, `RoutingSubView:154`, `PerAppProxyPage:93,99,136`, `CheckUpdateView:57`, `ThemeSettingView:38,48,58`, `ClashProxiesView` x9, `ClashConnectionsView` x9) | 0 |
| `TextBox.Watermark=` (AVLN5001, recorded baseline) | **14** across 8 files | see section 11 |
| settings routes reachable from the hub | **8** of 17 | 17 |
| unreachable settings (built and bound, no UI path) | `ProviderSettingsPage` in full; `OptionSettingWindow`'s ~10; every row behind the 9 missing routes | 0 (`12-settings.md` 6.5) |

---

## 11. `TextBox.Watermark` / AVLN5001 register

All 14 sites, with the 7.4 judgement ("a placeholder is never the label") for each. Fixing the
deprecation is welcome but must not add warnings; the 7.4 column is the design half of the same work.

| File:line | Watermark | Is it acting as the label? |
|---|---|---|
| `LoginView:351` | `{loc:T Login_Email}` («Электронная почта») | **YES - P1.** No label element exists. `14-auth.md` 6.2 wants `lbl_email` above and the placeholder `name@example.com` |
| `LoginView:370` | `{loc:T Login_Password}` («Пароль») | **YES - P1.** No label element |
| `LoginView:417` | `{loc:T Login_ConfirmPassword}` | **YES - P1.** No label element |
| `LoginView:600` | `{loc:T Login_CodePaste}` | borderline - it is the paste-a-code field's only affordance; `14-auth.md` 9.4 gives it a label |
| `SettingsView:500` | `{loc:T Settings_NotSet}` on `ProxyUserBox` | no - there is a `Settings_Username` label above (:495). Correct use |
| `SettingsView:509` | `{loc:T Settings_NotSet}` on `ProxyPassBox` | no - label above (:503). Correct use |
| `DnsSubView:131` | `https://example.com/dns-query` | no - a genuine example value; but its label sits in a `SectionHeader` above the card, not as a field label |
| `PingSettingsPage:144` | `https://www.gstatic.com/generate_204` | no - example value, label `Ping_TestAddress` above (:140). Correct use |
| `PingSettingsPage:155` | `5` | no - label above (:150). Correct use |
| `ProviderSettingsPage:127` | `INCY/1.0` | **YES - P2.** The only text near the field is a `SectionHeader` reading `User-Agent` and a helper below; the field itself has no label block, and `12-settings.md` 5.7 requires the **effective** value shown as content, not as a placeholder |
| `PerAppProxyPage:~` | `{loc:T Common_SearchPlaceholder}` | no - a search field's placeholder is its label by convention and the spec agrees (4.1, 7.2) |
| `CompactServersView:~` | `{loc:T Servers_SearchPlaceholder}` | no - same |
| `AccountView:~` | `{loc:T Account_AmountRub}` | outside this audit's ownership; flagging for the account wave |
| `AccountView:~` | `{loc:T Login_Email}` | **YES - P1**, outside this audit's ownership; flagging for the account wave |

**Four label-as-placeholder defects in this audit's scope, three of them on the sign-in form.**

---

## 12. Ban hits (`00-rules.md` section 1), consolidated

| # | Ban | Sites | Severity |
|---|---|---|---|
| B1 | Cards where a divided list belongs (1.1 "identical card grids"; `12-settings.md` 2.1 / D-S4 "zero cards in the settings tree") | 25 `Classes="Card"` across `SettingsView` (6 group wrappers) and 9 sub-pages | P1 |
| B2 | Decorative gradients (1.4.3, 6.5) | `LoginView:237` and `OnboardingView` background = `Brush.HomeGradient` (`RadialGradientBrush`) | P1 |
| B3 | Russian string in the Cyrillic-free brand face (5.1, D-1/D-2 - "a P1 defect, not a polish item") | `LoginView:88` (`Button.SegItem` = «Вход»/«Регистрация»), `DnsSubView:53` (`Border.DnsChip` = «По умолчанию»/«Свой») | P1 |
| B4 | A third font family, hard-coded (1.3 note, 5.1) | `UrlSchemesPage:95 FontFamily="Consolas,monospace"` | P1 |
| B5 | Em dash / en dash in shipped copy (1.4.11, 9.2, 9.7) | 14 sites - 6 in views (5 of them the literal `Text="—"`), 8 keys in `L.Settings.cs` | P1 |
| B6 | Raw colour literal in a view (1.4.6) | `SettingsView:78 SelectionBrush="#334C8DFF"` | P2 |
| B7 | Off-scale spacing (1.4.5) | 32 occurrences, 9 distinct values, incl. the `Margin="16,18,16,8"` on `TextBlock.SettingsSection` that `12-settings.md` 8.2 already names | P2 |
| B8 | Inline font size / family instead of a ramp class (5.2, 12.1) | 10 sites (3 attribute-form, 7 setter-form); sizes 14, 15, 20 - none is a ramp step | P1 |
| B9 | Coloured icon tile in settings (`12-settings.md` law 2.2, D-5) | `SettingsView:238 Classes="Tile Blue"` on `RowMode` with an accent glyph | P1 |
| B10 | Default Fluent/Semi look leaking (12.1) | ~50 unstyled controls (22 `Button`, 28 selection controls) | P1 |
| B11 | Fixed control height (3.3 R2 - "a P1 accessibility defect by 14.5") | 11 setters, incl. `Button.Tonal.Tall Height 52`, `Border.SegTrack Height 44`, `Button.SegItem Height 36`, `Border.CodeCell Height 52`, `Button.BackNav Width/Height 40` | P1 |
| B12 | Decorative motion, off-token duration (1.3, 8.1, 8.2) | `LoginView:203-226` the 1.6 s infinite "breathing" plane | P1 |
| B13 | Synthetic/absent weight (5.4 - 400/500/700 only) | `LoginView:103 FontWeight="SemiBold"` (600), `DnsSubView:55` same | P2 |
| B14 | Retired radii still in use (D-7) | `Radius.Search` 14 on `TextBox.Incy` and `Border.SearchPill`; `Radius.Traffic` 8 on `Border.TrafficPill` | P2 |
| B15 | Control boundary drawn with `colorOutlineVariant` instead of `color_outline_control` (D-9, 6.8, 14.1) | `TextBox.Incy` and `TextBox.IncyField` `BorderBrush = Brush.OutlineVariant`; every field in the tree and on the sign-in form | P1 |

Clean and to be kept clean: `StaticResource Brush.*` **0**, emoji **0**, `...` **0**, inline hex on the
five colour attributes **0** in this set.

---

## 13. Parity gaps against the Android counterpart

`00-rules.md` 13 makes destination set, order, strings, defaults and the state matrix identical by
contract. `12-settings.md` 12.2 logs the eight allowed asymmetries (PG-S1 to PG-S8). Anything else is a
defect.

**Allowed and correctly desktop-only:** `Масштаб интерфейса` (PG-S1, shipped as `RowUiScale`).

**Allowed but not shipped:** `Окно и горячие клавиши` (PG-S2 - the feature exists in the dead
`GlobalHotkeySettingWindow` and is unreachable), `Шаблон конфигурации` + `Ядро` (PG-S3 - in the dead
`OptionSettingWindow`), the copyable device identifier (PG-S4 - in the dead `ProviderSettingsPage`),
`Проверить обновления` (PG-S7 - in the dead `CheckUpdateView`). Four of the five desktop-only
capabilities the spec grants are built and unreachable.

**Not allowed - defects or gaps to log:**

| ID | Gap | Which is wrong |
|---|---|---|
| PG-D1 | `Обход локальной сети` writes `Inbound[0].AllowLANConn` on desktop and `PREF_VPN_BYPASS_LAN` on Android - one label, two unrelated behaviours | **defect**, desktop. `12-settings.md` 6.4 B1 |
| PG-D2 | `Автообновление подписки` writes the geo-file interval in the wrong unit on desktop; Android writes the per-subscription interval | **defect**, desktop. See 4.2 |
| PG-D3 | Group set: desktop ships 6 named groups (`…, Производительность, Интерфейс, Подписка, О приложении`), Android/spec ship 4 + a footer pair | **defect**, desktop |
| PG-D4 | `Режим подключения` has 3 segments in the spec, 2 on desktop (`Вместе` missing) | **defect**, desktop |
| PG-D5 | `Оформление` has 3 segments in the spec, 2 on desktop (`Системная` missing) | **defect**, desktop |
| PG-D6 | `Язык` picker: spec `Системный`/`Русский`/`English`; desktop cycles ru<->en only | **defect**, desktop |
| PG-D7 | Labels diverge from the D-S5 single-label decision: `Монохром` vs `Чёрная тема`, `Пинг` vs `Проверка задержки`, `Запуск при загрузке` vs `Запуск при старте`, `Настройки провайдеров` vs `Провайдеры`, `Автообновление подписки` vs `Автообновление подписок`, `Резервное копирование` vs `Данные и резервные копии`, `Облегчённый режим` vs `Меньше движения` | **defect**, both platforms must land on the spec string |
| PG-D8 | Settings search ships on both platforms (D-S8). Neither has it; desktop has no field and no `Ctrl+F` | **gap**, both |
| PG-D9 | `Схемы URL-адресов` sits in the hub on desktop; the spec puts it under About on both | **defect**, desktop |
| PG-D10 | Sign-in: Android gets surfaces A (gate) / B (form) / C (sheet) / D (hand-off); desktop collapses A+B+C into one `LoginView` and inverts the segment's job (mode instead of method) | **defect**, desktop - `14-auth.md` 3 and 6.4 |
| PG-D11 | `Локальный прокси` is a hub row with an inline expand panel on desktop; the spec puts it at `settings/advanced/localproxy` on both (6.3, D-S3) | **defect**, desktop |
| PG-D12 | 21 settings the cut list removes and 8 it merges are still present in the dead `OptionSettingWindow`; nothing has been cut on desktop | **gap**, desktop |

---

## 14. Copy sheet conformance (`12-settings.md` 11)

`Common/L.Settings.cs` carries **68 keys**. Section 11.1 alone specifies 57 hub keys and 11.2 another
~170 for the sub-pages. Coverage is roughly **30%**, and eight of the shipped keys carry an em dash.

Wrong strings (D-S5 and 11.1, all one-line fixes but all parity-visible):

| Key | Ships | Spec |
|---|---|---|
| `Settings_Mode` | `Режим` | `Режим подключения` |
| `Settings_Ping` | `Пинг` | `Проверка задержки` (`Settings_Latency`) |
| `Settings_Monochrome` | `Монохром` | `Чёрная тема` (`Settings_BlackTheme`) |
| `Settings_MonochromeHint` | `Монохромный режим поверх тёмной или светлой темы` (restates the title) | `Чистый чёрный фон без цветного акцента` |
| `Settings_Autostart` | `Запуск при загрузке` | `Запуск при старте` |
| `Settings_SubAutoUpdate` | `Автообновление подписки` | `Автообновление подписок` |
| `Settings_SecInterface` | `Интерфейс` | `Приложение` (`Settings_SecApp`) |
| `Settings_SecSubscription` | `Подписка` | `Подписки` (`Settings_SecSubs`) |
| `Settings_SecPerformance` | `Производительность` | group does not exist |
| `Settings_LiteMode` / `Hint` | `Облегчённый режим` / `Отключает анимации, снижает нагрузку` | `Меньше движения` / `Отключает анимации` |
| `Settings_Backup` | `Резервное копирование` | `Данные и резервные копии` |
| `Settings_Ipv6Hint` | `Включить IPv6-адресацию в туннеле` | `Включить IPv6 в туннеле` |
| `Settings_MuxHint` | `Объединяет запросы в один канал соединения` | `Объединяет запросы в один канал` |
| `Provider_Title` | `Настройки провайдеров` | `Провайдеры` |
| `Ping_TestAddress` | `Адрес проверки задержки` | `Адрес проверки` |
| `Ping_Timeout` | `Тайм-аут проверки, сек` | `Тайм-аут`, value `5 с` |
| `Ping_TcpHint` | `TCP-подключение к серверу` (restates the title) | `Быстрее, но менее точно` |
| `Dns_CustomAddress` | `Свой DNS-адрес` | `Адрес DNS-сервера` |
| `Routing_Reset` | `Сбросить` | `Сбросить правила` + helper `Удалит все наборы, включая свои` |
| `Backup_Export` / `Backup_Import` | `Экспорт` / `Импорт` | `Создать копию` / `Восстановить из копии` |
| `About_Version` | `Версия —` | a placeholder shipped as a string; use `About_VersionValue` |
| `Settings_PerAppExcept` / `Only` | lowercase `кроме` / `только`, composed as `$"{mode} {n}"` | `Кроме %1$d` / `Только %1$d`, sentence case at the start of a value |
| `Backup_Save` / `Backup_Restore` | `Сохранить…` / `Восстановить…` | 11.6.4 - `…` belongs only in `Обновление…` and `Нажмите сочетание…` |

Missing entirely: the whole `Adv_*`, `Fragment_*`, `Lp_*`, `Webdav_*`, `Window_*`, `Log_*` namespaces,
plus `Settings_SearchHint`, `Settings_ModeVpn/Both/BothHint`, `Settings_PerAppOff/None`,
`Settings_Advanced`, `Settings_FragmentParams`, `Settings_SubUpdateLaunch`, `Settings_Latency`,
`Settings_Providers`, `Settings_ThemeSystem`, `Settings_LanguageSystem`, `Settings_ReducedMotion`,
`Settings_UiScale` (the label is a hardcoded literal at `SettingsView.axaml:792`), `Settings_Window`,
`Settings_Data`.

---

## 15. Work order

Ordered by severity, then by what unblocks what. Every item names the spec clause it satisfies. No
item deletes a screen before its replacement is reachable (`12-settings.md` 9.7).

### P0

**W1. Fix `Автообновление подписки`.**
Files: `ViewModels/SettingsViewModel.cs`, `Common/L.Settings.cs`.
Change: point the row at the real subscription interval (`SubItem.AutoUpdateInterval`, minutes, per
feed, then reschedule) instead of `GuiItem.AutoUpdateInterval`; option set `0 / 60 / 360 / 720 / 1440`
minutes rendered `Выключено` / `Каждый час` / `Каждые 6 часов` / `Каждые 12 часов` / `Раз в сутки`;
disable the row with `Нет подписок` + `Добавьте провайдера, чтобы включить` when there are none.
Spec: `12-settings.md` 4.4 row 3.1, 11.1. Evidence: `SettingsViewModel.cs:35-36, 424-438, 604-609`
vs `ServiceLib/Manager/TaskManager.cs:65, 84-85, 111-121`.
Risk: `GuiItem.AutoUpdateInterval` is also the geo-update knob and is read by `OptionSettingViewModel`;
leave that field alone and give the geo interval its own home on `settings/assets` (5.8
`Источник обновлений` group) rather than silently repurposing it.

### P1

**W2. Correct the `Обход локальной сети` binding.**
Files: `ViewModels/SettingsViewModel.cs:160, 215-224`.
Change: bind to the private-range direct route (`TunModeItem.RouteExcludeAddress` seeded with
`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `fc00::/7`, `fe80::/10`); move
`AllowLANConn` to a `Доступ из локальной сети` row on the local-proxy page and to the `Вместе` mode.
Spec: `12-settings.md` 6.4 B1, 4.2 row 1.5.
Risk: migration - an install with `AllowLANConn = true` must land on both `Доступ из локальной сети` on
and `Обход локальной сети` on, per 6.4.

**W3. Delete the 25 `Border.Card` group wrappers; groups become a section header plus 24 of space plus
hairlines at the 68 origin.**
Files: `Views/SettingsView.axaml`, `PingSettingsPage`, `ProviderSettingsPage`, `DnsSubView`,
`RoutingSubView`, `GeoFilesPage`, `UrlSchemesPage`, `PerAppProxyPage`, `BackupPage`, `AboutPage`.
Spec: `12-settings.md` law 2.1, D-S4, acceptance checklist ("both must return nothing").
Risk: `ClipToBounds="True"` on the card is what currently clips the row hover fill to the group's
rounded corners; without the card the hover must be bounded by the row's own `Radius.Button` 16
(`12-settings.md` 9.2 `Border.SettingRow`), or hover will draw square against a rounded neighbour.

**W4. One trailing element per row.**
Files: `Views/SettingsView.axaml` (8 rows listed in 3.4).
Change: drop the chevron from `RowPerApp`/`RowDns`/`RowPingMethod`/`RowAbout`; drop `unfold_more` from
`RowMuxConcurrency`/`RowUiScale`/`RowLanguage`/`RowSubAutoUpdate` and replace cycle-in-place with the
A2 picker flyout (D-S2).
Spec: `00-rules.md` 4.5, `12-settings.md` 2.4, 3, D-S2, 9.4.
Risk: `SettingsView.axaml.cs:53-58` wires `Cycle*Async` to the row tap; the picker replaces those four
call sites, and `Geo.Set.UnfoldMore` becomes dead.

**W5. Give the hub a header, a search field and a 720 measure.**
Files: `Views/SettingsView.axaml`, `Views/SettingsView.axaml.cs`, `Common/L.Settings.cs`,
`Views/MainWindow.axaml.cs` (Ctrl+F / Ctrl+`,`).
Spec: `12-settings.md` 4.1, 7, 9.1 changes 1 and 3.

**W6. Restore the keyboard contract in the shell.**
Files: `Views/MainWindow.axaml.cs:1899-1951` and the `_subStack`/`PopSubPage` region (:1089-1120,
:1256).
Change: `Key.Escape` pops the top sub-page (and closes an open flyout first); `PointerPressed` with
`XButton1` pops; `Ctrl+F` focuses the settings search; `Ctrl+,` selects the settings tab and focuses
the search; gate `Ctrl+S`/`Ctrl+V` on the focused element not being a `TextBox`.
Spec: `00-rules.md` 12.2, 14.8, 13 translation table; `12-settings.md` 9.3 (gap V12).
Risk: `LoginView` is on the same stack (`MainWindow.axaml.cs:1089` special-cases it) - Esc must respect
whatever that special case protects (the awaiting-Telegram poll) rather than dropping it silently.

**W7. Give the four scroll-less sub-pages a `ScrollViewer` and raise every measure to 720.**
Files: `PingSettingsPage.axaml`, `GeoFilesPage.axaml`, `BackupPage.axaml`, `AboutPage.axaml` (add);
all nine sub-pages (620 -> 720).
Spec: `00-rules.md` 12.3, 4.1; `12-settings.md` 5, 9.6.

**W8. Extract the sub-page skeleton to one shared base.**
Files: all nine settings sub-pages, `Views/ISubPage.cs`, `Assets/GlobalStyles.axaml` (owned by another
wave - coordinate).
Change: `Border.SubToolbar` + `Button.BackNav` + title at `Classes="Title"`; delete the nine local
`Button.IconButton` / `:pressed scale(0.92)` blocks; retire `Button.IconButton` in favour of
`IconButton40`.
Spec: `12-settings.md` 9.3, 9.7; `00-rules.md` D-11, 4.8, 7.2.

**W9. Sign-in: labels above the three fields, reserved helper slots, and the toolbar on the ramp.**
Files: `Views/LoginView.axaml:346-426`, `.axaml.cs`.
Change: add `TextBlock Classes="Subtitle"` labels «Электронная почта» / «Пароль» / «Повторите пароль»
above `EmailBox` / `PasswordBox` / `ConfirmPasswordBox`; move the watermark to a real example
(`name@example.com`); swap `IsVisible="False"` for `Opacity="0"` on `EmailError`,
`ConfirmPasswordError`, `RegisterPasswordHint`; toolbar title `Headline` -> `Title`; move focus to the
first invalid field after a failed submit.
Spec: `00-rules.md` 7.4, 4.8; `14-auth.md` 6.2, 6.5; `12-settings.md` 9.5.

**W10. Remove the gradient page background from `LoginView` and `OnboardingView`.**
Files: `Views/LoginView.axaml:237`, `Views/OnboardingView.axaml`.
Change: `Brush.HomeGradient` -> `Brush.Bg`.
Spec: `00-rules.md` 6.5, 1.4.3.
Risk: the file comment argues the radial is the visual continuity between onboarding and sign-in; the
shared 64px shield tile already carries that, so the continuity survives the change.

**W11. Take the Russian strings out of the brand face and off inline sizes.**
Files: `Views/LoginView.axaml:88-89, 103, 165-167, 680-682`, `Views/DnsSubView.axaml:53-55`,
`Views/UrlSchemesPage.axaml:95`, `Views/SettingsView.axaml:75`.
Change: delete every `FontFamily` setter on a control whose content is Russian; delete every
`FontSize` setter and apply a ramp class; replace `Consolas,monospace` with the `Font.Numeric` token
(`depv://…` is a technical token, so the brand face is correct there - the family must still come from
a resource, not a literal).
Spec: `00-rules.md` 5.1, 5.2, 5.4, 12.1.

**W12. `MinHeight` everywhere a `Height` is set on a control.**
Files: `Views/LoginView.axaml:64, 73, 79, 144`, the nine back buttons, `Assets/GlobalStyles.axaml`
`Button.BackNav` (other wave).
Spec: `00-rules.md` 3.3 R2, 14.5.

**W13. Delete the 1.6 s breathing animation.**
Files: `Views/LoginView.axaml:203-226` and the `.breathing` class writer in `.axaml.cs`.
Spec: `00-rules.md` 8.1, 8.2, 3.7.

**W14. Neutralise the one coloured tile in settings and give rows a pressed state.**
Files: `Views/SettingsView.axaml:238`, `Assets/GlobalStyles.axaml` `Border.SettingRow` (other wave).
Change: `Classes="Tile Blue"` -> `Classes="Tile"`, glyph to `Brush.Tile.Glyph`; add
`Border.SettingRow:pressed` stepping the background to `Brush.SurfaceHigh` (R5 - rows step, they do
not scale).
Spec: `12-settings.md` law 2.2, D-5; `00-rules.md` 7.1, 16.

**W15. Style the leaking controls.**
Files: 15 files, ~50 controls (grep table, section 10).
Spec: `00-rules.md` 12.1 ("Any control that has not been restyled to the token set is a defect").

**W16. Make `DnsSubView` chips and `PingSettingsPage` method rows keyboard-reachable.**
Files: `Views/DnsSubView.axaml:104-120` + `.axaml.cs`, `Views/PingSettingsPage.axaml:100-134` +
`.axaml.cs`.
Change: `Focusable`/`IsTabStop` + Enter/Space + the shared `FocusAdorner`, the same pattern
`SettingsView.axaml.cs:107-119` already uses correctly.
Spec: `00-rules.md` 12.2, 14.4, 14.8.

### P2

**W17. Clear the em dashes and land the D-S5 strings.**
Files: `Common/L.Settings.cs` (8 keys x 2 languages), `Views/SettingsView.axaml:776, 792`,
`ProviderSettingsPage:109`, `GeoFilesPage:73, 80`, `UrlSchemesPage:74`, `AboutPage:95`.
Change: hyphen, comma or full stop for every dash; the five `Text="—"` placeholders become real empty
states (`Не загружен`, etc.); `SettingsView:792` gets a `Settings_UiScale` key; the tooltip becomes a
keyed string with no dashes and no U+2212.
Spec: `00-rules.md` 1.4.11, 9.2, 9.7; `12-settings.md` 11.1, 11.6, D-S5.

**W18. Off-scale spacing, retired radii, and the raw hex.**
Files: 32 spacing sites; `TextBox.Incy`/`Border.SearchPill` (`Radius.Search` 14 -> `Radius.Button` 16),
`Border.TrafficPill` (`Radius.Traffic` 8 -> `Radius.Pill`); `SettingsView.axaml:78`.
Spec: `00-rules.md` 1.4.5, 1.4.6, 3.2 D-7.

**W19. Field boundary contrast.**
Files: `Assets/GlobalResources.axaml` `TextBox.Incy` (other wave), `SettingsView.axaml`
`TextBox.IncyField:70`.
Change: `BorderBrush` `Brush.OutlineVariant` (1.16:1) -> `Brush.OutlineControl` (3.43:1).
Spec: `00-rules.md` D-9, 6.8, 14.1.
Risk: this is the token wave's file; the local `TextBox.IncyField` copy in `SettingsView` must move
with it or the two fields will differ.

**W20. `RowValue MaxWidth` and the row text origin.**
Files: `Views/SettingsView.axaml:145, 200, 247, 287, …`.
Change: `MaxWidth="150"` -> 40% of the row measured in code; the tile-to-text gap is 16 in every row
(giving a 72 origin and a 72 divider inset) where the spec's universal row is 12, giving the 68 origin
and 68 inset both platforms share.
Spec: `00-rules.md` 4.1, 4.5; `12-settings.md` 9.2, acceptance checklist.

**W21. Validation and error states on the fields that have none.**
Files: `DnsSubView` (DoH address), `PingSettingsPage` (timeout range),
`SettingsViewModel.CommitLocalProxyAsync` (port range - it already reverts, it must also say why).
Spec: `12-settings.md` 10, 11.5; `00-rules.md` 7.4.

**W22. `Ctrl+S` guard.**
Files: `Views/MainWindow.axaml.cs:1931-1943`.
Change: skip the global handler when the focused element is a `TextBox`.

### P3

**W23. Stale comments that will mislead the rebuild.**
`PingSettingsPage.axaml:94` says "4 выбираемыми строками" over 2 rows; `SettingsView.axaml:14` says
"1:1 порт Android layout_settings_content.xml" for a layout that now diverges in six groups;
`SettingsViewModel.CycleAutoUpdateAsync`'s doc comment claims "Выкл / 6 / 12 / 24 / 48 ч" for an array
of four values; `SettingsView.axaml:437` says "КРАСНАЯ плитка" over a neutral tile;
`L.Settings.cs:9` lists `ProviderSettingsPage` as a consumer of a page nothing constructs.

**W24. Lifetime of `SettingsViewModel`'s subscriptions.**
`SettingsViewModel.cs:123` (`UiScaleState.Changed +=`) and `SettingsView.axaml.cs:87`
(`Vm.PropertyChanged +=`) are never unsubscribed, justified in comments by a single keep-alive
instance. That instance is real - `MainWindow.axaml.cs:20`
`private readonly Control _settingsView = new SettingsView();` - so **there is no leak today**. The
fragility is that the invariant lives in a field initialiser and nothing enforces it: a second
`new SettingsView()` anywhere (a preview host, a second window, a future split view) silently doubles
every persistence subscription in `WirePersistence`, and each config write would fire twice. Either
assert it (private ctor + static factory) or unsubscribe on detach.

---

## 15b. Per-page detail for the four pages whose defects are structural, not cosmetic

### `PerAppProxyPage` (`settings/perapp`, 5.1)

| Line | Finding | Rule |
|---|---|---|
| :93-104 | The mode control is **two bare `RadioButton`s**, and their `Content` is the row's **helper** string (`PerApp_BypassHint` = «Кроме выбранных - идут напрямую, минуя VPN»), not its label. There is no `Правило` label and no segment | 5.1 wants an **A4 segment** labelled `Правило` with the segment labels `Кроме выбранных` / `Только выбранные` and the helper as a dynamic second line. Also a 7th archetype and a Semi leak. **P1** |
| :133-151 | The app list is a non-virtualised `ItemsControl` inside a `ScrollViewer`; on Windows the enumeration is hundreds of executables and every one is realised | `00-rules.md` 4.6: "Any list that can exceed ~20 items is virtualised. Non-virtualised long lists are a P1 performance defect." **P1** |
| :136-148 | The row **is** a `CheckBox` at `MinHeight="44"` with the text inside its content slot | 5.1 wants the universal row: 40 app-icon tile, Title, Subtitle in the Numeric role, checkbox in the **trailing** slot, 56 minimum. 44 is off-token and below `Size.Row`. **P1** |
| :121-129 | Two unstyled `Button`s (`Обновить`, `Добавить .exe`) beside the search field | 5.1 wants one 40 icon button; both leak Semi. **P1** |
| - | No empty state, no search-no-match state, no 8-row loading skeleton, no bulk-action overflow, no 0.38 gating of the list when the master toggle is off | 5.1, 10. **P1** |

### `RoutingSubView` (`settings/routing`, 5.2)

| Line | Finding | Rule |
|---|---|---|
| :143 | The group header is `Routing_DomainStrategy` («Стратегия доменов») while the single row inside it is `Routing_DomainResolution` («Разрешение доменов») | 5.2 group `Домены` holds **two** A2 rows, `Стратегия доменов` and `Разрешение доменов`. One row is missing and the group is named after it. **P1** |
| :154-158 | `ComboBox cmbStrategy`, unstyled | Law 9 "No modal for a choice", `00-rules.md` 11.2 (never a spinner), 9.4 (picker flyout). Semi leak. **P1** |
| :123-135 | The active set carries **both** a 20 check glyph and an `Активен` chip repeating it | 2.4 decoration tell - "a chip repeating what the title says: delete it". The second channel the spec asks for is Title weight 700 + `color_selected_fill`, not a chip. **P2** |
| :173-177 | `Восстановить стандартные наборы` is a title+helper block with a trailing `Button` | A5 has **no** trailing element; the row itself performs. **P2** |
| - | Missing: `Добавить набор`, `Импортировать набор` + its picker, per-item flyout (`Изменить`/`Дублировать`/`Экспортировать`/`Удалить`), reorder handle, `Сбросить правила` A6 + confirm dialog, empty state | 5.2, 11.3, 11.4. **P1** |
| `.axaml.cs` | Editing a set drops into `RoutingRuleSettingWindow` - a 900x600 OS-decorated window with 27 `resx:` refs | 9.4 "Never a modal `Window` for a settings choice"; 5.2 closes this escape hatch. **P1**, and the window may not be deleted until `settings/routing/rule/{id}` exists (9.7) |
| :100, :108, :119, :126, :129, :93 | `Padding="12,10"`, `Padding="10,4"`, `Margin="0,3,0,0"`, `Margin="0,6,0,0"` x4 | 1.4.5 off-scale. **P2** |

### `UrlSchemesPage` (`settings/about/urlschemes`, 5.16)

| Line | Finding | Rule |
|---|---|---|
| :65-68 | A hand-rolled card (`Border` + `Brush.SurfaceHigh` + `Radius.Card` + `Padding 16,12`) instead of `Border.Card` | 12.1 "A view that hand-rolls a card is a defect; extend the class" - and in this tree the answer is no card at all (D-S4). **P1** |
| :74 | `Text="—"` as the registration status at rest | 1.4.11 dash ban; and the two designed strings already exist (`UrlSchemes_Registered` / `UrlSchemes_NotRegistered`). **P1** |
| :95 | `FontFamily="Consolas,monospace"` - a third font family, as a string literal | 5.1 (no view sets a family), 1.3 (no third family). The `depv://…` token is Latin so `Font.Numeric` (Space Grotesk) is the correct face. **P1** |
| :92 | Rows at `MinHeight="48"` | `Size.Row` is 56. **P2** |
| :101-107 | Only the trailing `Button` copies; the row is not clickable | 5.16: "the whole row also copies, so the two do the same thing and there is no ambiguity". **P2** |
| :77 | `btnRegister Classes="Primary"` - a filled accent button | The whole settings tree is allowed exactly **two** filled accent buttons (`settings/assets`, `settings/data/webdav`). Shipped today: `GeoFilesPage btnUpdate` and this one, plus `LoginView` (outside the tree). Registration is an A5 action, not a primary CTA. **P2** |
| :78 | `btnUnregister` unstyled | Semi leak. **P1** |
| - | Reached from the **hub**, not from About | 5.16, PG-D9. **P2** |

### `GeoFilesPage` (`settings/assets`, 5.8)

Covered in sections 5, 6 and 9. The one item worth restating: it and `BackupPage` both end in an
`x:Name="txtStatus"` `TextBlock` that starts empty and is filled from code with success, progress and
failure text alike. That single line is doing the work of the loading state, the success message, the
per-row error and the page error at once, in one colour, with no recovery action. `12-settings.md` 10
and 10.1 split those into a per-row trailing indicator, a per-row subtitle, an inline error under the
failing element, and one transient message carrying `Повторить`.

---

## 16. What is already right and must survive the rebuild

Listed because a rebuild that loses these is a regression, and two of them are the best decisions in
the file.

1. **The affordance-honesty contract** (`SettingsView.axaml.cs:14-22`): each trailing glyph states what
   the tap will do before the tap. `12-settings.md` 2.3 calls it "the single best design decision in
   either codebase" and inherits it verbatim. Keep the contract; drop only the `unfold_more` and
   inline-expand vocabulary it currently documents (D-S2, D-S3).
2. **The row owns the tab stop.** `WireRow`/`WireToggleRow` + `ToggleSwitch.RowSwitch`
   `Focusable=False`/`IsTabStop=False` is exactly `12-settings.md` 3.1, and the `OriginatedInToggle`
   guard is what stops the double write the same spec warns about.
3. **The OFF model.** `SetTunMode` (`SettingsViewModel.cs:339-360`) deliberately bypasses
   `StatusBarViewModel.EnableTun`'s `DoEnableTun` so a settings tap never reloads the core or raises a
   UAC prompt. `12-settings.md` 4.2 orders this preserved by name.
4. **`AutostartHelper.Reconcile`** (`SettingsViewModel.cs:170-172`): the autostart toggle shows the
   registry fact, not the stored intent, and re-asserts even when the stored flag already matches. That
   is the anti-lying discipline the rest of the tree needs.
5. **Reduced motion is live, not read-once.** `MotionState.IsLite` is checked at play time in
   `SettingsView.axaml.cs` and by the `:is(Window):not(.lite)` selector prefix in `LoginView` -
   `00-rules.md` 12.5 exactly.
6. **The focus adorner survives lite mode** (`GlobalStyles.axaml:963-970`) - accessibility outranks
   motion reduction, per 7.1.
7. **The sign-in loading state holds the control's width and hides the label** (`LoginView:433-460`),
   which is R8 done right and is rare in this codebase.
