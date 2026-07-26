# Recon — Desktop settings & secondary pages (extraction spec for the Android port)

**Scope.** Everything in this document was read from files on disk in this session. Desktop repo root
is `/home/user/v2rayN/v2rayN/` (branch `claude/app-audit-agents-hyyftk`); the Avalonia UI lives in
`/home/user/v2rayN/v2rayN/v2rayN.Desktop/`. Android repo root is `/home/user/dp/V2rayNG/`.

> **Path correction for the orchestrator:** the task brief said the desktop views are at
> `/home/user/v2rayN/v2rayN.Desktop/Views/`. That path does not exist. The real path is
> `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/` (the solution folder `v2rayN/` is nested inside the
> repo root `v2rayN/`). All citations below use the real paths.

---

## 0. Files read (and their sizes)

| File | Lines |
|---|---|
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml` | 1075 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml.cs` | 359 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/SettingsViewModel.cs` | 597 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/PingSettingsPage.axaml` / `.axaml.cs` | 160 / 83 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ProviderSettingsPage.axaml` / `.axaml.cs` | 138 / 86 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/UrlSchemesPage.axaml` / `.axaml.cs` | 115 / 157 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/PerAppProxyPage.axaml` / `.axaml.cs` | 163 / 238 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/RoutingSubView.axaml` / `.axaml.cs` | 184 / 140 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/DnsSubView.axaml` / `.axaml.cs` | 163 / 131 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/GeoFilesPage.axaml` / `.axaml.cs` | 100 / 99 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/AboutPage.axaml` / `.axaml.cs` | 105 / 58 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/BackupPage.axaml` / `.axaml.cs` | 96 / 91 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MessageBoxDialog.axaml` / `.axaml.cs` | 74 / 40 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ServerListView.axaml` | 313 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/ISubPage.cs` | 13 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs` (sub-page host + hotkeys) | — |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml` | 1270+ |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml` | 400+ |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.Settings.cs` | 185 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/UiScaleState.cs` | 59 |
| `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/Motion.cs` | — |
| Android: `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_settings_content.xml` | 1536 |
| Android: `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt` §2425–2885 | — |
| Android: `SettingsActivity.kt`, `ProviderSettingsActivity.kt`, `RoutingSettingActivity.kt`, `PerAppProxyActivity.kt`, `LocalProxyActivity.kt`, `UrlSchemeListActivity.kt`, `AboutActivity.kt` | — |

---

## 1. The desktop settings information architecture (source of truth)

`SettingsView.axaml` is a single vertical scroll of **section header → card**, with every card
`Padding="0"`, `ClipToBounds="True"`, `Margin="16,0,16,8"` (`SettingsView.axaml:225-229`, and repeated
identically for each section). Rows inside a card are separated by a 1px divider inset 72dp from the
left (`Border.SettingDivider`, `SettingsView.axaml:143-147`) — 16 padding + 40 tile + 16 gap.

### 1.1 Section + row inventory, in shipped order

| # | Section (`{loc:T}` key → RU / EN) | Row | `x:Name` | Archetype | Value / affordance | Backing config |
|---|---|---|---|---|---|---|
| **1** | `Settings_SecConnection` → **Подключение** / Connection (`:223`) | Режим | `RowMode` (`:234`) | **inline segment (2-state)** | `TUN` \| `Прокси` ToggleButtons (`:259-267`) | `TunModeItem.EnableTun` |
| | | Прокси по приложениям | `RowPerApp` (`:274`) | **navigation** | value + chevron (`:291-299`) | `UiItem.PerAppProxy*` |
| | | Обход локальной сети | `RowBypassLan` (`:306`) | **toggle** | iOS switch (`:328-333`) | `Inbound[0].AllowLANConn` |
| | | IPv6 | `RowIpv6` (`:340`) | **toggle** | iOS switch (`:362-367`) | `TunModeItem.EnableIPv6Address` |
| | | DNS | `RowDns` (`:374`) | **navigation** | value + chevron (`:391-399`) | `SimpleDNSItem.RemoteDNS` |
| | | Пинг | `RowPingMethod` (`:406`) | **navigation** | value + chevron (`:423-431`) | `SpeedTestItem.PingMethod` |
| | | Локальный прокси | `RowLocalProxy` (`:438`) | **inline disclosure** | chevron rotates 0↔90 (`:463-468`) | `Inbound[0].LocalPort/User/Pass` |
| | | *(inline panel)* | `LocalProxyPanel` (`:475`) | expanded editor | Порт / Логин / Пароль + hint (`:480-515`) | same |
| **2** | `Settings_SecBypass` → **Обход блокировок** / Bypass censorship (`:522`) | Мультиплексирование (Mux) | `RowMux` (`:532`) | **toggle** | iOS switch (`:554-559`) | `Mux4SboxItem.Protocol` (`"h2mux"`/`""`) |
| | | Число соединений Mux | `RowMuxConcurrency` (`:566`) | **value cycle** | value + `unfold_more` (`:586-594`); `IsVisible=False` until Mux on | `Mux4SboxItem.MaxConnections` |
| | | Фрагментация пакетов | `RowFragment` (`:604`) | **toggle** | iOS switch (`:626-631`) | `CoreBasicItem.EnableFragment` |
| **3** | `Settings_SecPerformance` → **Производительность** / Performance (`:641`) | Облегчённый режим | `RowLiteMode` (`:651`) | **toggle** | iOS switch (`:673-678`) | `UiItem.LiteMode` |
| **4** | `Settings_SecInterface` → **Интерфейс** / Interface (`:686`) | Оформление | `RowAppearance` (`:698`) | **inline segment (2-state)** | `Тёмная` \| `Светлая` (`:720-727`) | `UiItem.CurrentTheme` |
| | | Монохром | `RowBlackTheme` (`:737`) | **toggle** | iOS switch (`:759-764`) | `UiItem.BlackTheme` |
| | | Масштаб интерфейса | `RowUiScale` (`:773`) | **value cycle** | value + `unfold_more` (`:793-801`) | `UiItem.UiScale` |
| | | Язык | `RowLanguage` (`:808`) | **value cycle** | value + `unfold_more` (`:825-833`) | `UiItem.CurrentLanguage` |
| | | Запуск при загрузке | `RowBoot` (`:840`) | **toggle** | iOS switch (`:862-867`) | `GuiItem.AutoRun` |
| **5** | `Settings_SecSubscription` → **Подписка** / Subscription (`:875`) | Автообновление подписки | `RowSubAutoUpdate` (`:885`) | **value cycle** | value + `unfold_more` (`:902-910`) | `GuiItem.AutoUpdateInterval` ⚠ see §5.1 |
| | | Маршрутизация | `RowRouting` (`:917`) | **navigation** | chevron only (`:934-937`) | `RoutingItem` set |
| | | Файлы ресурсов | `RowAssets` (`:944`) | **navigation** | chevron only (`:961-964`) | on-disk geoip/geosite |
| **6** | `Settings_About` → **О приложении** / About (`:972`) | О приложении | `RowAbout` (`:982`) | **navigation** | version value + chevron (`:999-1007`) | `Utils.GetVersionInfo()` |
| | | Резервное копирование | `RowBackup` (`:1014`) | **navigation** | chevron only (`:1031-1034`) | config dir → .zip |
| | | Схемы URL-адресов | `RowUrlScheme` (`:1041`) | **navigation** | subtitle + chevron (`:1052-1066`) | HKCU protocol keys |

**There is no destructive row archetype in the desktop settings screen.** The only destructive
affordance in the settings family is `Common_Delete` in the `ServerListView` row context menu, tinted
`Brush.Red` (`ServerListView.axaml:160-163`), and the shared confirm dialog (`MessageBoxDialog`, §4).

### 1.2 Exact copy (RU / EN), from `Common/L.Settings.cs`

Section headers (`L.Settings.cs:25, 41, 48, 52, 60, 65`):
```
Settings_SecConnection   Подключение              / Connection
Settings_SecBypass       Обход блокировок          / Bypass censorship
Settings_SecPerformance  Производительность        / Performance
Settings_SecInterface    Интерфейс                 / Interface
Settings_SecSubscription Подписка                  / Subscription
Settings_About           О приложении              / About
```

Rows + hints (`L.Settings.cs:26-68`) — subtitles are single-sentence, no trailing period, sentence-case:
```
Settings_Mode            Режим / Mode                       Settings_ModeProxy  Прокси / Proxy
Settings_PerApp          Прокси по приложениям / Per-app proxy
Settings_BypassLan       Обход локальной сети / Bypass local network
Settings_BypassLanHint   Прямой доступ к устройствам в локальной сети
Settings_Ipv6Hint        Включить IPv6-адресацию в туннеле
Settings_Ping            Пинг / Ping
Settings_LocalProxy      Локальный прокси / Local proxy
Settings_LocalProxyHint  Порт, логин и пароль SOCKS5-подключения
Settings_Port            Порт    Settings_Username Логин    Settings_NotSet Не задан
Settings_Socks5Auth      SOCKS5-авторизация
Settings_Socks5Hint      Адрес: 127.0.0.1. Пустые логин и пароль отключают SOCKS5-авторизацию.
Settings_Mux             Мультиплексирование (Mux)
Settings_MuxHint         Объединяет запросы в один канал соединения
Settings_MuxCount        Число соединений Mux
Settings_Fragment        Фрагментация пакетов
Settings_FragmentHint    Разбивает TLS-рукопожатие против DPI
Settings_LiteMode        Облегчённый режим
Settings_LiteModeHint    Отключает анимации, снижает нагрузку
Settings_Appearance      Оформление      Settings_ThemeDark Тёмная   Settings_ThemeLight Светлая
Settings_Monochrome      Монохром
Settings_MonochromeHint  Монохромный режим поверх тёмной или светлой темы
Settings_Language        Язык            Settings_LangRussian Русский
Settings_Autostart       Запуск при загрузке
Settings_AutostartHint   Открывать departament при входе в систему
Settings_SubAutoUpdate   Автообновление подписки
Settings_Routing         Маршрутизация
Settings_GeoFiles        Файлы ресурсов
Settings_Backup          Резервное копирование
Settings_UrlSchemes      Схемы URL-адресов
Settings_UrlSchemesHint  Быстрые команды depv://
```

Shared value tokens (`Common/L.Common.cs:40-44, 62`):
`Common_Default` "По умолчанию", `Common_Custom` "Свой", `Common_On` "Вкл", `Common_Off` "Выкл",
`Common_HoursShort` "{0} ч.".

**Locale-neutral tokens deliberately NOT keyed** (documented at `L.Settings.cs:15-17`): TUN, DNS,
IPv6, FakeIP, Mux, SOCKS5, TCP/HTTP/ICMP, HWID, User-Agent, `depv://`, `geoip.dat`, `geosite.dat`,
Cloudflare/Google/AdGuard, protocol names, language endonyms.

### 1.3 Value resolvers (what each value slot actually prints)

From `SettingsViewModel.cs:520-596`:

- **DNS** (`ResolveDnsText`, `:526-540`): empty → `Common_Default` ("По умолчанию");
  `https://cloudflare-dns.com/dns-query` → "Cloudflare"; `https://dns.google/dns-query` → "Google";
  `https://dns.adguard-dns.com/dns-query` → "AdGuard"; anything else → `Common_Custom` ("Свой").
  **Never prints the raw DoH URL.**
- **Пинг** (`ResolvePingMethodText`, `:545-551`): `Tcping` → "TCP", `Httping` → "HTTP",
  `Icmping` → "ICMP", else `Ping_Real` ("Реальная"). The comment at `:542-544` states the long
  "…через ядро" phrasing was rejected because it overflowed the value slot.
- **Число Mux** (`:553-554`): the integer, defaulting to `"8"` when ≤ 0.
- **Прокси по приложениям** (`ResolvePerAppText`, `:556-565`): off → "Выкл"; on with N apps →
  `"кроме N"` / `"только N"` (`Settings_PerAppExcept` / `Settings_PerAppOnly`); on with 0 apps → "Вкл".
- **Оформление** (`ResolveThemeText`, `:567-573`), **Язык** (`ResolveLanguageText`, `:575-587`, with
  endonyms `English / 简体中文 / 繁體中文 / فارسی / Français / Magyar / Bahasa Indonesia`).
- **Автообновление** (`ResolveAutoUpdateText`, `:589-594`): `n > 0` → `L.F("Common_HoursShort", n/60)`
  else "Выкл". ⚠ integer division — see §5.1.
- **Масштаб** (`FormatUiScale`, `:497`): `$"{Math.Round(scale*100)}%"`.

### 1.4 Cycle option sets (hard-coded, `SettingsViewModel.cs:36-42`)

```csharp
AutoUpdateOptions     = [60, 360, 720, 1440];                     // shown as 1/6/12/24 ч.
MuxConcurrencyOptions = [4, 8, 16, 32, 64, 128];                  // unknown/0 starts at index 1 (=8)
UiScaleOptions        = [0.8, 0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0];
```
UI-scale cycling picks the **first preset strictly greater than current**, wrapping to the minimum
(`CycleUiScale`, `:476-495`) — correct even after arbitrary Ctrl +/− intermediate values.
Language cycles Русский ↔ English only (`CycleLanguageAsync`, `:427-443`).

---

## 2. The interaction rules the desktop settled on

These are the rules to port, stated as the desktop states them.

### 2.1 "Honest row archetypes" (the central rule)

Documented verbatim in the `SettingsView` class doc, `SettingsView.axaml.cs:15-22`:

> КАЖДАЯ строка — реальная рабочая функция, и КАЖДЫЙ правый affordance ЧЕСТЕН:
> • **шеврон = НАВИГАЦИЯ** (тап открывает суб-страницу): Прокси по приложениям, DNS, Пинг,
>   Маршрутизация, Файлы ресурсов, О приложении, Резервное копирование, Схемы URL;
> • **шеврон-раскрытие (0↔90) = инлайн-панель**: Локальный прокси;
> • **`unfold_more` = значение ЦИКЛИТСЯ на месте** (≥3 значений): Язык, Автообновление, Число Mux,
>   Масштаб интерфейса;
> • **инлайн-сегмент (2 состояния) = смена на месте**: Режим (TUN/Прокси), Оформление (Тёмная/Светлая);
> • **тумблер = булево**: Обход сети, IPv6, Mux, Фрагментация, Облегчённый режим, Монохром, Запуск.

The `unfold_more` glyph is explicitly *not* a chevron — `SettingsView.axaml:167-169`:
> честный аффорданс «значение меняется ЗДЕСЬ» … НЕ шеврон: шеврон = «уходит вперёд на суб-страницу»,
> а эти строки продвигают значение на месте.

The 2-state segment is justified at `SettingsView.axaml:251-253`: a 2-state setting shows **both**
options and the current one at once, changes in place, and therefore carries **no chevron**.

### 2.2 No press-scale on rows (explicit owner requirement, with the bug it fixed)

`GlobalStyles.axaml:650-655`:
> **БЕЗ press-scale: владелец не хочет «продавливания» строки при тапе.** Прежний класс `.pressed`
> (scale 0.98 по PointerPressed) не только визуально «сжимал» строку, но и съезжал под курсором на
> нажатии — жест `Tapped` отменялся, и тап срабатывал «через раз». Отклик на нажатие несёт сам ховер
> (`Brush.Hover`) под курсором; кольцо фокуса — a11y.

Also: the row hover is **instant** — no `BrushTransition` on `Background` — so during wheel-scroll
exactly one row is lit (`GlobalStyles.axaml:654-655`).

Press-scale *is* retained on **buttons and chips**, not rows: `Button.Primary:pressed` → `scale(0.97)`
@120ms `Ease.OutQuart` (`GlobalStyles.axaml:398-408`), `ToggleButton.Segment:pressed` → `scale(0.96)`
(`GlobalStyles.axaml:1245-1247`), sub-page back `Button.IconButton:pressed` → `scale(0.92)` @90ms
(e.g. `PingSettingsPage.axaml:27-29`, repeated in every sub-page).

### 2.3 Keyboard focus model

`SettingsView.axaml.cs:102-106` (`WireRow`) and `:122-125` (`WireToggleRow`):

- Every action row becomes `Focusable = true; IsTabStop = true` at runtime; **Tab order = markup order**.
- **Enter/Space activate the row** (`KeyDown` handler, `:112-119` and `:131-138`).
- The **single pointer path is `Tapped`** — one call per tap, no `PointerPressed` interception
  (which previously swallowed the gesture).
- Switches are removed from the tab ring (`ToggleSwitch.RowSwitch` sets `Focusable=False`,
  `IsTabStop=False`, `SettingsView.axaml:207-212`) — **the row owns the tab stop**, otherwise each
  toggle row would produce two stops. Mouse clicks directly on the switch still work; the
  `OriginatedInToggle` guard (`SettingsView.axaml.cs:339-351`) suppresses the double-flip.
- Segment rows are the exception: their `ToggleButton`s are natively focusable, so the row itself is
  **not** made a tab stop (`SettingsView.axaml.cs:75-77`).
- Focus ring: `FocusAdorner` on `Border.SettingRow` — an **inner** 2px `Brush.Accent` border,
  `Margin=3`, `CornerRadius=12` (`GlobalStyles.axaml:661-668`). Inner, because a full-width row inside
  a rounded card would push an outer ring past the card's clipped corner. It is **instant and survives
  lite mode** ("a11y > движение").

### 2.4 "De-rainbow" — exactly one coloured tile

`SettingsView.axaml:232-233`:
> Режим → инлайн-сегмент TUN / Прокси. **ЕДИНСТВЕННАЯ синяя плитка-идентичность в списке**
> (щит = ядро VPN-режима): один акцент, остальные плитки нейтральны.

Every other row's tile is `Classes="Tile"` (neutral `Brush.Tile.Neutral` `#20242B` dark / `#E3EAF4`
light, `GlobalResources.axaml:85, 121`) with the glyph tinted `Brush.OnSurface`. Only `RowMode` uses
`Classes="Tile Blue"` + `Foreground="{DynamicResource Brush.Accent}"` (`SettingsView.axaml:236-244`).

Confirmed by grep of the whole file: `Classes="Tile Blue"` appears once. Green/Orange/Purple/Red/Yellow
tile variants **exist** in `GlobalStyles.axaml:581-595` but are **not used** by settings.

### 2.5 Motion rules (all composer-only, all gated on lite)

- **Value crossfade**: opacity 0.3 → 1, `Motion.Dur.PressOut` = **160ms**, `Ease.Standard`, run on the
  `TextBlock` itself, only when the VM property actually changed
  (`CrossfadeValue`, `SettingsView.axaml.cs:196-215`; dispatch table `:172-190`). Under lite → opacity
  snapped to 1.
- **Local-proxy chevron**: `RotateTransform.Angle` 0↔90, `Motion.Dur.State` = **220ms** `Ease.Standard`
  (`SetProxyChevron`, `:238-259`). `RenderTransformOrigin="50%,50%"` is set in markup and the comment
  at `SettingsView.axaml:460-462` warns that `"0.5,0.5"` would be parsed as **0.5 pixels**, making the
  chevron orbit instead of spin.
- **Inline panel reveal**: open = fade + translateY −6→0 over **300ms** `Ease.OutQuint`; close = fade +
  0→−6 over **150ms** `Ease.Standard` (`RevealPanel`, `:263-305`). Exit is deliberately faster than entry.
- **Sub-page push/pop**: push = translateX 16→0 + fade 0→1, **300ms** `Ease.OutQuint`; pop =
  translateX 0→16 + fade 1→0, **200ms** `Ease.Standard`. **Only translate + opacity — no scale/rotate**
  ("страница не «улетает» из угла"), `MainWindow.axaml.cs:1120-1171`.
- Duration tokens (`Common/Motion.cs`): `PressIn` 90ms `OutQuart`, `PressOut` 160ms `OutQuint`,
  `State` 220ms `Standard`, `Reveal` 300ms `OutQuint`, `Exit` 150ms `Standard`, `Overlay` 200ms,
  hero 600ms. Easings: `OutQuart` = `cubic-bezier(.25,1,.5,1)`, `OutQuint` = `(.22,1,.36,1)`,
  `Standard` = `(.2,0,0,1)`. **No bounce/elastic anywhere.**
- Everything above short-circuits on `MotionState.IsLite`, which is the persisted
  `UiItem.LiteMode` broadcast live (`SettingsViewModel.cs:270-282`).

### 2.6 The "OFF model" (consumer-VPN discipline)

Stated at `SettingsViewModel.cs:22-25` and enforced everywhere:
**no settings row ever starts the core.** Each write persists via `ConfigHandler.SaveConfig`, then
re-applies live **only if the core is already running**:

```csharp
private async Task PersistAndMaybeReload()      // SettingsViewModel.cs:297-305
{
    await ConfigHandler.SaveConfig(_config);
    if (IsCoreRunning()) StatusBarViewModel.Instance.ReloadRequested.Publish();
}
private static bool IsCoreRunning() =>          // :307-308
    AppManager.Instance.IsRunningCore(ECoreType.Xray) || AppManager.Instance.IsRunningCore(ECoreType.sing_box);
```

The TUN-mode segment goes to unusual lengths for this (`SetTunMode`, `:314-345`): it deliberately does
**not** route through `StatusBarViewModel.EnableTun`'s `DoEnableTun`, because that unconditionally
reloads and, on non-admin Windows, calls `RebootAsAdmin()` with a UAC prompt. It writes the config
first, persists, then mirrors the shared VM (so `DoEnableTun` early-returns), and reloads only if
running. **"TUN admin escalation belongs to the connect action, not this row."**

The same `IsCoreRunning()` gate is repeated in `DnsSubView.axaml.cs:119-123`,
`PerAppProxyPage.axaml.cs:166-169`, `RoutingSubView.axaml.cs:111-119`.

### 2.7 Text-truncation contract for rows

- Row title: `TextWrapping=NoWrap`, `TextTrimming=CharacterEllipsis` — long titles degrade to "…" and
  **never overlap the value or chevron** (`SettingsView.axaml:183-189`).
- Row value: right-aligned, single line, ellipsized, `Margin="8,0,0,0"`, **`MaxWidth="150"`** — so a
  long DNS URL can't push the chevron off the card at ~360px width (`SettingsView.axaml:191-201`).

### 2.8 Dependent-row visibility

Pure view logic: `Число соединений Mux` and its divider are visible only when Mux is on
(`UpdateMuxDependentRows`, `SettingsView.axaml.cs:353-358`; markup default `IsVisible="False"` at
`:569` and `:601`). Mirrors Android's `rowMuxConcurrency.isVisible = muxOn`.

### 2.9 Segment re-assertion

A `ToggleButton` inverts itself on click, so a second tap on the already-active segment would
*deselect* it. `SelectMode` / `SelectTheme` re-assert the correct pair before writing
(`SettingsView.axaml.cs:143-167`), and `OnVmPropertyChanged` (`:172-190`) reflects **external** changes
to TUN or theme back into the segments.

---

## 3. The secondary-page contract

### 3.1 `ISubPage` + the shared back stack

`Views/ISubPage.cs` — one member, `event EventHandler? BackRequested`. Rule stated in its doc:
> Раньше эти экраны были отдельными OS-окнами (`*Window`) — теперь это `UserControl`, которые
> кладутся на стек «назад» через `MainWindow.OpenSubPage`. **Никаких отдельных окон.**

`MainWindow.OpenSubPage` (`MainWindow.axaml.cs:1256-1263`) subscribes `BackRequested → PopSubPage()`
and pushes onto the same `_subStack` used by Buy / Login / Devices / History.

`SettingsView.OpenPage(page, refresh)` (`SettingsView.axaml.cs:314-326`) additionally subscribes
`BackRequested → Vm.RefreshDisplayValues()` **before** `OpenSubPage`, so row values are refreshed
before the page leaves the stack. `refresh: true` is passed for `PerAppProxyPage`, `DnsSubView`,
`PingSettingsPage` (`:43-45`) and omitted for `RoutingSubView`, `GeoFilesPage`, `AboutPage`,
`BackupPage`, `UrlSchemesPage` (`:46-50`).

### 3.2 Seamless toolbar (identical in all 8 sub-pages)

Every sub-page uses the same header block — verified byte-identical in `PingSettingsPage.axaml:57-86`,
`ProviderSettingsPage.axaml:31-60`, `UrlSchemesPage.axaml:31-60`, `PerAppProxyPage.axaml:32-61`,
`RoutingSubView.axaml:54-83`, `DnsSubView.axaml:63-92`, `GeoFilesPage.axaml:31-60`,
`AboutPage.axaml:31-60`, `BackupPage.axaml:31-60`:

```
DockPanel MaxWidth="620"
  Grid Dock=Top MinHeight=56 Margin="16,8,16,0" Cols="Auto,*"
    Button x:Name=btnBack  40×40  Padding=0  Classes="IconButton"  ToolTip={loc:T Common_Back}
      PathIcon 22×22  Geo.Sub.Back  Foreground=Brush.OnSurface
    TextBlock Margin="16,0,0,0" Classes="Headline" Text=<page title> TextTrimming=CharacterEllipsis
  <content>  Margin="16,12,16,24"  Spacing=8
```
Back arrow geometry (shared literal): `M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z`.
**No OS chrome, no window title, no "Save"/"Cancel" buttons.**

### 3.3 Save-on-back with an idempotence guard

Every mutating sub-page follows exactly this shape (`PingSettingsPage.axaml.cs:61-82`,
`ProviderSettingsPage.axaml.cs:63-79`, `PerAppProxyPage.axaml.cs:144-171`,
`DnsSubView.axaml.cs:105-126`, `RoutingSubView.axaml.cs:101-122`):

```csharp
private bool _saved;
private async Task SaveAndBackAsync()
{
    if (_saved) return;   // guard: back can be raised more than once
    _saved = true;
    ... write config ...
    await ConfigHandler.SaveConfig(_config);
    if (IsCoreRunning()) StatusBarViewModel.Instance.ReloadRequested.Publish();
    BackRequested?.Invoke(this, EventArgs.Empty);
}
```
Read-only pages (`AboutPage:22`, `BackupPage:22`, `GeoFilesPage:25`, `UrlSchemesPage:41`) raise
`BackRequested` directly with no save step.

### 3.4 Per-page spec

#### `PingSettingsPage` — «Пинг»
- Intro paragraph `Ping_Intro`: *"Как измерять задержку серверов. Ниже — адрес и тайм-аут проверки."*
- **One card, two selectable method rows** (`PingSettingsPage.axaml:95-136`), each
  `Border.MethodRow` = `MinHeight={Size.Row}` 56, `Padding="16,12"`, transparent, `:pointerover` →
  `Brush.Hover` (`:32-39`). Selection is a **right-aligned 22px accent checkmark**
  (`PathIcon.MethodCheck`, `:41-47`), divider inset 16 (`:49-53`).
  - `Ping_RealTitle` **Реальная задержка** / `Ping_RealHint` **Через ядро, как при подключении**
  - `TCP` (literal) / `Ping_TcpHint` **TCP-подключение к серверу**
- Two more cards, each a labelled `TextBox`:
  - `Ping_TestAddress` **Адрес проверки задержки** → `SpeedTestItem.SpeedPingTestUrl`,
    watermark `https://www.gstatic.com/generate_204`
  - `Ping_Timeout` **Тайм-аут проверки, сек** → `SpeedTestItem.SpeedTestTimeout`, `MaxLength=3`,
    watermark `5`, accepted range `> 0 && < 600` (`PingSettingsPage.axaml.cs:76-79`)
- **Only Realping/Tcping are offered.** Rationale, `PingSettingsPage.axaml.cs:7-8` + `:30-34`: the
  engine has no Httping/Icmping, so a previously-stored value is coerced to `Realping`.

#### `DnsSubView` — «DNS» (replaces the legacy English `DNSSettingWindow`)
- Intro `Dns_Intro`. Section `Dns_Provider` **Провайдер** → a `WrapPanel` of 5 **chips**
  (`Border.DnsChip`, `DnsSubView.axaml:30-59`): rest = `Brush.SurfaceHigh` + `Radius.Chip` 12 +
  `Padding="16,10"`; hover = `Brush.SurfaceHighest`; `.selected` = solid `Brush.Accent` with
  `Brush.OnAccent` text; **press scale 0.96, no ripple** (comment `:29`).
  Chips: `Common_Default` / Cloudflare / Google / AdGuard / `Common_Custom`.
- Choosing «Свой» reveals `customPanel` with a DoH `TextBox` and hint `Dns_CustomHint`
  *"DoH-адрес (https://…/dns-query), DoT или обычный IP: 1.1.1.1"*, and focuses it
  (`DnsSubView.axaml.cs:61-69`).
- Section `Dns_Advanced` **Дополнительно** → one toggle row **FakeIP** with hint
  *"Ускоряет соединение, отвечая на DNS-запросы локально (sing-box)"* → `SimpleDNSItem.FakeIP`.
- Preset URLs: `https://cloudflare-dns.com/dns-query`, `https://dns.google/dns-query`,
  `https://dns.adguard-dns.com/dns-query` (`DnsSubView.axaml.cs:17-19`). Default = **empty string**.

#### `RoutingSubView` — «Маршрутизация» (replaces the legacy Semi `RoutingSettingWindow`)
- Reuses the engine `RoutingSettingViewModel` verbatim (`RoutingSubView.axaml.cs:44-45`) — **no logic
  duplication.** Per-rule editing is deliberately excluded: *"на суб-странице живут только основные,
  самодостаточные элементы; никаких отдельных окон"* (`:14-15`).
- `Routing_RuleSets` **Наборы правил** → list of `Border.RouteRow` (`:34-50`, hover `Brush.Hover`,
  press `scale(0.99)`); the active one shows a 20px accent check **and** a
  «Активен» chip (`Radius.Chip`, `Brush.SurfaceHigh`). Subtitle = `Routing_RulesCount` "{0} правил"
  through `RuleCountConverter` (`:128-140`) — needed because XAML `StringFormat` is static and
  wouldn't follow a language switch.
- `Routing_DomainStrategy` → `ComboBox` `MinWidth=170` over `AsIs` / `IPIfNonMatch` / `IPOnDemand`
  labelled **Как есть / IP при несовпадении / IP по запросу** (`:25-30`).
- `Routing_Maintenance` **Обслуживание** → `Routing_DefaultRules` **Стандартные правила** /
  hint *"Пересоздать встроенные наборы правил"* / button `Routing_Reset` **Сбросить**, which disables
  itself while running (`:87-99`).
- On back, if `IsModified`: `ConfigHandler.InitBuiltinRouting` + `RefreshRoutingsMenu`, then reload
  only if the core is running (`:107-119`).

#### `PerAppProxyPage` — «Прокси по приложениям»
- Card 1: toggle `PerApp_SplitTunnel` **Раздельное туннелирование** / hint *"Выберите, какие программы
  идут через VPN"*, then **two 56dp radio buttons** (`GroupName="perAppMode"`):
  - `PerApp_BypassHint` **Кроме выбранных — идут напрямую, минуя VPN**
  - `PerApp_OnlyHint` **Только выбранные — через VPN идут лишь они**
- Section `PerApp_Apps` **Приложения**; a row of search `TextBox` (`Common_SearchPlaceholder` "Поиск…"),
  `Common_Refresh` **Обновить**, `PerApp_AddExe` **Добавить .exe**.
- List = `CheckBox` per app, `MinHeight=44`, two-line (display name + identifier, ellipsized).
- Footer hint `PerApp_TunHint`: *"Работает в режиме TUN (sing-box). Правила применяются при следующем
  подключении."*
- **Real behaviour** (`PerAppProxyPage.axaml.cs:173-224`): writes `UiItem.PerAppProxy*` **and** injects
  managed rules into the active `RoutingItem.RuleSet`, marked with sentinel `Remarks`
  (`__departament_perapp_bypass` / `_include` / `_catchall`, `:21-23`) so user rules are never touched.
  bypass → one rule `OutboundTag = DirectTag` with `Process = apps` at index 0. include → rule
  `OutboundTag = ProxyTag` at index 0 **plus a trailing catch-all** `DirectTag`, `Network = "tcp,udp"`.
- Process list is built from `Process.GetProcesses()` plus previously-selected identifiers (so
  hand-added paths survive when not running), sorted checked-first then alphabetically (`:49-101`).

#### `GeoFilesPage` — «Файлы ресурсов»
- Intro `Geo_Intro`. One card with two info rows: `geoip.dat` / `geosite.dat`, each with a subtitle
  that is either `Geo_NotDownloaded` **Не загружен** or `Geo_SizeUpdated` **"{0} МБ · обновлён {1}"**
  (size to 1 decimal, date `dd.MM.yyyy HH:mm`, `GeoFilesPage.axaml.cs:37-54`).
- Full-width `Button.Primary` `Geo_UpdateNow` **Обновить сейчас**; while running its label becomes
  `Geo_Updating` **Обновление…** and it disables itself; a status line streams the engine's
  `UpdateService` messages, ending in `Geo_Done` **Готово — базы обновлены.** or
  `Geo_Failed` **Не удалось обновить: <msg>** (`:56-98`).

#### `AboutPage` — «О приложении»
- Centred `Classes="Display"` wordmark **departament** + `About_VersionValue` **Версия {0}**.
- Card of two rows: `About_OpenSite` **Открыть сайт** → button labelled `departament.site`;
  `About_TelegramBot` **Telegram-бот** → button `Common_Open` **Открыть**.
  Site URL is derived by stripping the trailing `/api` from `BackendConfig.BaseUrl`
  (`AboutPage.axaml.cs:31-37`); Telegram is `https://t.me/{BackendConfig.BotUsername}`.
- Section `About_Details` **Сведения** → a wrapping subtitle built from
  `About_SystemInfo` **"ОС: {0}\nАрхитектура: {1}\n.NET: {2}"** + button `About_CopyDetails`
  **Копировать сведения** which copies `About_TitleVersion` + the runtime block.

#### `BackupPage` — «Резервное копирование»
- Intro `Backup_Intro`. One card, two 64dp rows:
  - `Backup_Export` **Экспорт** / `Backup_ExportHint` **Сохранить копию в файл** → `Backup_Save` **Сохранить…**
  - `Backup_Import` **Импорт** / `Backup_ImportHint` **Восстановить из файла — приложение перезапустится**
    → `Backup_Restore` **Восстановить…**
- Status line reports `Backup_Saving` / `Backup_Saved` "{0}" / `Backup_SaveFailed` /
  `Backup_Restoring` **"Восстановление… Приложение перезапустится."**
- Reuses the engine `BackupAndRestoreViewModel.LocalBackup/LocalRestore`; `.zip` extension is appended
  if the user omits it (`BackupPage.axaml.cs:38-41`); a `_busy` flag blocks re-entry.

#### `UrlSchemesPage` — «Схемы URL-адресов»
- Top status card (`Brush.SurfaceHigh`, `Radius.Card`): `UrlSchemes_Registration`
  **Регистрация схемы depv://**, a live status line, and two buttons —
  `UrlSchemes_Register` **Зарегистрировать** (`Classes="Primary"`) / `UrlSchemes_Remove` **Убрать**.
  Status strings: `UrlSchemes_Registered` **"Схема зарегистрирована — ссылки depv:// открывают
  departament."**, `UrlSchemes_NotRegistered`, `UrlSchemes_WindowsOnly` **"Регистрация схемы доступна
  только на Windows."** (non-Windows also disables both buttons, `UrlSchemesPage.axaml.cs:63-69`).
- Hint `UrlSchemes_Hint`: *"Нажмите на схему, чтобы скопировать. Используйте их в ярлыках, скриптах или
  других приложениях."*
- Monospace list with a per-row `Common_Copy` button (`UrlSchemesPage.axaml:88-110`), 8 entries
  (`UrlSchemesPage.axaml.cs:29-39`):
  `depv://connect` (Запустить туннель), `depv://open` (Открыть приложение), `depv://disconnect` and
  `depv://close` (Остановить соединение), `depv://toggle` (Переключить соединение),
  `depv://import/{base64}` (Импорт (автоопределение типа)), `depv://add/{url}` (Добавить по URL),
  `departamentvpn://auth` (Войти через сайт).
- **Two schemes are registered**, both under `HKCU\Software\Classes` (per-user, no admin):
  `depv` and `departamentvpn`. Rationale at `UrlSchemesPage.axaml.cs:18-21`: the site's safe-return
  allowlist only accepts `^departament[a-z0-9]*$`, which `depv` doesn't match, so browser→app SSO needs
  the second scheme.

#### `ProviderSettingsPage` — «Настройки провайдеров» ⚠ **ORPHANED ON DESKTOP**
- Sections: `Provider_SecUpdates` **Обновление** (toggle `Provider_AutoUpdate` **Автообновление** /
  hint *"Автоматически обновлять подписки"*, + `Provider_Interval` **Интервал обновления** `ComboBox`
  `MinWidth=140`); `Provider_SecNetwork` **Сеть** (`Provider_Hwid` **Идентификатор устройства (HWID)**
  with the real `AuthTokenStore.DeviceId()` and a `Common_Copy` button); `User-Agent` (free `TextBox`,
  watermark `INCY/1.0`, hint *"Отправляется ядром на исходящих соединениях."* → `CoreBasicItem.DefUserAgent`).
- Toggle↔interval coupling: turning the switch on when the interval reads "Выкл" jumps it to 24 ч;
  turning it off zeroes the interval; changing the interval re-derives the switch
  (`ProviderSettingsPage.axaml.cs:38-53`).
- **A repo-wide grep for `ProviderSettingsPage` returns only its own two files and two comment lines in
  `L.Settings.cs`.** Nothing constructs it — `SettingsView.axaml.cs:43-50` wires eight sub-pages and
  this is not one of them. So the whole page is dead code on desktop, while Android *does* expose it
  (`MainActivity.kt:2461`).

### 3.5 `MessageBoxDialog` — the one shared confirm

`MessageBoxDialog.axaml` is the **only** modal dialog in this family. Contract (`:17-26`):

- `Window` with `WindowDecorations="None"`, `SizeToContent="WidthAndHeight"`, `CanResize="False"`,
  `ShowInTaskbar="False"`, `WindowStartupLocation="CenterOwner"`, `CanMinimize=false`
  (`MessageBoxDialog.axaml.cs:28`). Removing the OS title bar is deliberate: it strips the "v2rayN"
  caption — **the question itself is the title**.
- Window `Background = Brush.Bg`; inside, a floating card: `Brush.Surface`, `Padding=24`, `Margin=16`,
  1px `Brush.OutlineVariant`, `BoxShadow="0 16 40 0 #73000000"`, `CornerRadius = Radius.Card` (20).
- Body `Width=300`, `Spacing=24`. Question: 16px SemiBold `Brush.OnSurface`, `LineHeight=24`, wrapping,
  inside a `MaxHeight=280` scroller.
- Actions right-aligned, `Spacing=12`: **«Отмена» tonal, `MinWidth=96`, `IsCancel=True`** on the left;
  **«Подтвердить» `Classes="Primary"`, `MinWidth=112`, `IsDefault=True`** on the right. Labels come
  from `ResUI.TbCancel` / `ResUI.TbConfirm`.
- Returns `ButtonResult.Yes` / `ButtonResult.No`.
- Note the deviation from the usual destructive-red convention: the affirmative button is **accent
  blue, not red**, justified in the comment as "годится и для не-деструктивных подтверждений" — this
  dialog is shared by delete-server, delete-subscription, delete-rule *and* benign confirms.

### 3.6 Settings-adjacent bits of `ServerListView`

`ServerListView.axaml` is not a settings screen, but three of its conventions are settings-adjacent and
worth carrying:

- **Row context menu** (`:149-165`): Сделать основным · Проверить задержку — Изменить · Дублировать —
  Поделиться (QR) · Поделиться ссылкой — **Удалить** (`Foreground="{DynamicResource Brush.Red}"`),
  with `Separator`s grouping the four blocks. This is the destructive archetype: **red text in a menu,
  then the shared `MessageBoxDialog`**.
- **Bottom clearance is a real trailing child, not `ScrollViewer.Padding`** (`:76-82`, `:276-284`):
  a 24dp `IsHitTestVisible="False"` spacer, because in this Avalonia build bottom padding is not folded
  into the scroll extent and the last row could not be scrolled fully into view.
- Divider inset 80 (16 gutter + 12 row padding + 40 tile + 12 gap) and hidden on the selected row
  (`:51-55`, `:265`) — the settings screen uses inset 72 for its own (16 + 40 + 16) geometry.

---

## 4. Design tokens the settings screens depend on

From `Assets/GlobalResources.axaml`:

| Token | Value | Line |
|---|---|---|
| `Color.Accent` / `Brush.Accent` | `#4C8DFF` | 39, 41 |
| `Brush.SelectedFill` | `#4C8DFF` @ 0.12 | 242 |
| `Brush.Tile.Blue` | `#4C8DFF` @ 0.20 | 45 |
| `Brush.Tile.Neutral` | `#20242B` (dark) / `#E3EAF4` (light) | 85 / 121 |
| `Brush.SurfaceHigh` | `#1A1D21` / `#EAEFF7` | 65 / 103 |
| `Brush.OutlineVariant` | `#20242B` / `#DCE3EF` | 76 / 112 |
| `Brush.Hover` | black @ 0.32 (dark) / 0.06 (light) | 86 / 122 |
| `Radius.Chip` / `Radius.Tile` | 12 | 150 / 151 |
| `Radius.Card` | 20 | 152 |
| `Radius.Pill` | 100 | 154 |
| `Radius.Search` / `Radius.Traffic` | 14 / 8 | 284 / 285 |
| `Size.Tile` / `Size.Glyph` / `Size.Row` | 40 / 22 / 56 | 157–159 |
| `Size.SegmentChip` | 44 | 300 |
| `Size.EmptyIcon` / `Size.EmptyGlyph` | 64 / 32 | 301 / 302 |

From `Assets/GlobalStyles.axaml`:

- `TextBlock.SectionHeader` (`:322-327`): Grotesk **Bold 16**, `Brush.OnSurface` — explicitly commented
  *"жирный sentence-case, НЕ мелкий ALL-CAPS eyebrow"*. In settings it additionally gets
  `TextBlock.SettingsSection` `Margin="16,18,16,8"` (`SettingsView.axaml:179-181`).
- `TextBlock.Body` 15 / `Subtitle` 13 `OnSurfaceVariant` / `Caption` 12 / `Chip` 11 Medium (`:296-315`).
- `Border.Card` (`:330-336`): `Brush.Surface`, radius 20, 1px `OutlineVariant`, **no shadow**.
- `Border.Tile` (`:350-358`): 40×40, radius 12, neutral; `.Blue` variant.
- `Border.SettingRow` (`:657-672`) — see §2.2/§2.3.
- `ToggleButton.Segment` (`:1214-1254`): height 44, `MinWidth=44`, `Padding="16,0"`, radius 12;
  rest = transparent + **1.5px** `OutlineVariant` + `OnSurfaceVariant` text; **checked** =
  `SelectedFill` + `Accent` border + `Accent` text; press `scale(0.96)`; **no ripple**.
- `ToggleSwitch.iOS` (`GlobalResources.axaml:329-396`): track pill **52×32** radius 16,
  off `Brush.SurfaceHighest` → on `Brush.Accent` (220ms `Ease.Standard`); knob 26px white with a
  0.5px `#22000000` hairline, travel `translateX(20px)` @220ms `Ease.OutQuint`; **press squashes only
  the knob fill to `scale(0.9)`** @90ms `OutQuart` so the squash can't fight the travel transform;
  disabled = 0.38 opacity.
- `Border.EmptyIcon` (`:1260-1269`): 64 blue tile, radius 20, 32px accent glyph — the **empty-state**
  archetype, distinct from the 40 row tile.

Glyph set used by the settings rows (inline `StreamGeometry` at `SettingsView.axaml:23-63`, each a port
of an Android `res/drawable`): `ic_shield_outline`, `ic_per_apps_24dp`, `ic_globe_24dp`,
`ic_privacy_24dp`, `ic_ping_24dp`, `ic_hub_local_proxy`, filled circle (Mux), `ic_speed_24dp`,
`ic_source_code_24dp`, `ic_chevron_right`, `unfold_more`, `format_size`, `ic_palette_24dp`,
`ic_dark_mode_24dp`, `ic_power_settings`, `ic_refresh_24dp`, `ic_routing_24dp`, `ic_file_24dp`,
`ic_about_24dp`, `ic_backup_24dp`, `ic_hub_url_scheme`.

---

## 5. Defects found in the desktop settings while extracting

These must be resolved before/while porting, otherwise the bugs get copied.

### 5.1 «Автообновление подписки» controls the wrong field, in the wrong unit — and two screens disagree

Three separate problems around one field, `GuiItem.AutoUpdateInterval`:

1. **It does not drive subscription auto-update at all.** The only consumer of
   `GuiItem.AutoUpdateInterval` in the engine is the **geo-file** task:
   `ServiceLib/Manager/TaskManager.cs:113` — `if (_config.GuiItem.AutoUpdateInterval > 0 && hours > 0 && hours % _config.GuiItem.AutoUpdateInterval == 0)`.
   Subscription refresh is driven by the **per-subscription** `SubItem.AutoUpdateInterval`
   (`TaskManager.cs:84-85`, `updateTime - t.UpdateTime >= t.AutoUpdateInterval * 60`). So the desktop
   row labelled **«Автообновление подписки»** actually sets the geo-file cadence and changes nothing
   about subscription refresh. (Android gets this right: `MainActivity.pickSubAutoUpdate()`
   `:2853-2885` writes `item.autoUpdate` / `item.updateInterval` on **every** stored subscription and
   calls `SubscriptionUpdater.sync(forceReschedule = true)`.)
2. **Unit mismatch.** `ConfigItems.cs:77-81` documents the field as *minutes* and `SettingsViewModel`
   cycles `[60, 360, 720, 1440]` and prints `n / 60` as hours (`:36`, `:589-594`). `TaskManager.cs:113`
   consumes it as **hours** (`hours % interval`). Cycling to "24 ч." therefore stores `1440`, which the
   geo task reads as "every 1440 hours" ≈ 60 days.
3. **The two screens write incompatible values to the same field.**
   `ProviderSettingsPage.axaml.cs:16` uses `IntervalOptions = { 0, 6, 12, 24, 48 }` (hours) and saves
   the raw hour count (`:71-72`). After using that page and picking "24 ч.", the settings row computes
   `24 / 60 = 0` → renders **"0 ч."** (`SettingsViewModel.cs:593`). And `CycleAutoUpdateAsync` finds
   `IndexOf(…, 24) == -1` and jumps to `60` ("1 ч."), silently discarding the choice.

**Port decision needed:** pick one unit, split the two concerns (subscription cadence vs geo cadence)
into two honest rows, or point the subscription row at the real per-sub field.

### 5.2 «Настройки провайдеров» is unreachable on desktop

`ProviderSettingsPage` has no construction site anywhere in `v2rayN.Desktop` (grep verified: only
`ProviderSettingsPage.axaml`, `ProviderSettingsPage.axaml.cs`, and two comments in `L.Settings.cs`).
The eight wired sub-pages are listed at `SettingsView.axaml.cs:43-50`. Consequences: the **HWID
display/copy**, the **User-Agent override** (`CoreBasicItem.DefUserAgent`), and the interval combo are
all dead on desktop, though Android exposes an equivalent screen from its `row_provider`
(`MainActivity.kt:2461`).

### 5.3 «Масштаб интерфейса» is not localized

`SettingsView.axaml:792` — `Text="Масштаб интерфейса"` is a hard-coded Russian literal, unlike every
other row which uses `{loc:T …}`. Its tooltip is likewise hard-coded Russian
(`SettingsView.axaml:776`, `ToolTip.Tip="Ctrl + / Ctrl − — масштаб, Ctrl 0 — сброс"`). There is no
`Settings_UiScale*` key in `L.Settings.cs`. In English the row reads Russian.

### 5.4 Two dead ViewModel properties

`SettingsViewModel.ModeText` (`:75`, maintained at `:115`, `:172`, `:337`, `:507`) and
`AppearanceText` (`:89`, maintained at `:179`, `:468`, `:513`) are `[Reactive]` and kept in sync, but
**nothing binds them** — a grep of `SettingsView.axaml` for `Binding` returns 15 bindings, none of
which is `ModeText` or `AppearanceText` (both rows use the segments instead). Harmless, but it means
the crossfade dispatch table (`SettingsView.axaml.cs:178-189`) has no case for them and never could.

### 5.5 No `Escape` → back on sub-pages

`MainWindow_KeyDown` (`MainWindow.axaml.cs:1899-1951`) handles Ctrl +/−/0 (UI scale), Ctrl V, Ctrl S
and F5 — **there is no `Key.Escape` branch and no call to `PopSubPage()` from the keyboard**. A
keyboard-only user who tabs into a sub-page must Tab back to `btnBack`. Given that §2.3 went to real
trouble on row focus, this is an inconsistency to fix (and to design in on Android as the system Back
button, which Android already gets for free).

### 5.6 `Settings_Ping` value can disagree with what the page offers

`ResolvePingMethodText` (`SettingsViewModel.cs:545-551`) still maps `Httping` → "HTTP" and
`Icmping` → "ICMP", but `PingSettingsPage` coerces any non-`Tcping` value to `Realping` on entry
(`PingSettingsPage.axaml.cs:32-34`) and only offers two rows. A config carrying `Httping` shows
**"HTTP"** on the settings row until the user opens and closes the ping page. Either drop the two dead
branches from the resolver or coerce at load.

---

## 6. Desktop → Android delta

Android's Incy settings tab is `res/layout/layout_settings_content.xml` (1536 lines), included at
`res/layout/activity_main.xml:494`, wired in `MainActivity.setupSettings()` /
`bindSettingsState()` (`MainActivity.kt:2434-2526`) with handlers through `:2885`.
Android's token values match desktop 1:1 (`res/values/dimens.xml:14-34`: space 4/8/12/16/24,
`radius_chip` 12, `radius_card` 20, `radius_tile` 12, `tile_size` 40, `tile_glyph` 22,
`row_min_height` 56, `screen_gutter` 16) and `SettingsSectionLabel` (`res/values/styles.xml:6-17`)
already matches desktop's `SectionHeader` (Space Grotesk 700, 16sp, `textAllCaps=false`,
`letterSpacing=0`, padding 16/18/16/8).

### 6.1 Section-by-section comparison

| Desktop section | Android section | Verdict |
|---|---|---|
| Подключение | `settings_section_connection` **Подключение** (`layout:19-23`) | present, **+1 Android-only row** (Always-on) |
| Обход блокировок | `settings_section_bypass` (`layout:543-544`) | present |
| **Производительность** | — | **MISSING on Android** |
| Интерфейс | `settings_section_interface` (`layout:755`) | present, **−2 rows** (Монохром toggle, Масштаб) |
| Подписка | `settings_section_subscription` (`layout:961`) | present, **+1 row** (Настройки провайдеров) |
| — | `settings_section_devices` **Устройства** (`layout:1211`) | **Android-only** (ТВ transfer) |
| О приложении | `settings_section_about` (`layout:1337`) | present |

### 6.2 Row-level delta

**Present on desktop, MISSING on Android**

| Desktop row | Backing | Android status |
|---|---|---|
| **Облегчённый режим** + whole «Производительность» section | `UiItem.LiteMode`, broadcast via `MotionState.SetLite` (`SettingsViewModel.cs:270-282`) | No equivalent anywhere. Grep for `lite_mode` / `LITE_MODE` / `reduced_motion` in `app/src/main` returns nothing. Android has no reduced-motion switch at all. |
| **Масштаб интерфейса** (80–200%, 8 presets, Ctrl +/−/0) | `UiItem.UiScale` + `UiScaleState` (`Common/UiScaleState.cs`), applied by `MainWindow` via `LayoutTransformControl` | No equivalent. Grep for `ui_scale` / `uiScale` returns nothing. |
| **Монохром as an independent toggle** over the dark/light base | `UiItem.BlackTheme`, applied live by `App.ApplyTheme(theme, black)` (`SettingsViewModel.cs:284-295`) | Android folds mono into the *same* single-choice picker as light/dark (`MainActivity.currentAppearanceIndex()` `:2751-2755`, `pickAppearance()` `:2766-2801`) → **you cannot have "Светлая + монохром" on Android**, and mono silently overrides whatever night mode is set. |
| **Оформление as an inline 2-state segment** | `SettingsView.axaml:698-730` | Android uses a chevron row + `AlertDialog` single-choice with 3 entries (`row_appearance`, `layout:780`; handler `:2766`). |
| **Режим as an inline 2-state segment (TUN / Прокси)** | `SettingsView.axaml:234-269` | Android uses a chevron row + `AlertDialog` with **3** entries: TUN / Только прокси / VPN + Proxy (`pickMode`, `MainActivity.kt:2545-2580`). Desktop has no "VPN + Proxy" third state. |
| **Ping test address + timeout** (`SpeedPingTestUrl`, `SpeedTestTimeout`) | `PingSettingsPage.axaml:138-157` | On Android the Incy tab offers only a **method** dialog (`pickPingMethod`, `:2595-2615`). `PREF_DELAY_TEST_URL` exists (`AppConfig.kt:73`, consumed at `CoreConfigManager.kt:1173`) but is only editable in the orphaned legacy prefs screen (`res/xml/pref_settings.xml:330`). No timeout UI. |
| **DNS as DoH presets + FakeIP toggle** | `DnsSubView` | Android's DNS row is a plain-**IP** preset dialog: `dns_preset_values` = `1.1.1.1 / 8.8.8.8 / 1.1.1.1,8.8.8.8 / 94.140.14.14 / 9.9.9.9 / (custom)` (`strings_settings_hub.xml:44-51`), no DoH URLs, **no FakeIP toggle** in the Incy tab (`PREF_FAKE_DNS_ENABLED` is only in the orphaned `pref_settings.xml:99`). |
| **Routing: domain-strategy row with friendly RU labels + "Сбросить"** | `RoutingSubView.axaml:143-179` | `RoutingSettingActivity` shows the raw `R.array.routing_domain_strategy` values in a bare `AlertDialog.setItems` (`RoutingSettingActivity.kt:83-90`) and has no "reset to defaults" row — the equivalent hides in the overflow menu (`menu_routing_setting`, `:74-80`). |
| **URL-scheme registration status + Register/Remove** | `UrlSchemesPage.axaml:65-81` | Android's `UrlSchemeListActivity` (40 lines) is copy-only — no registration concept (Android registers via manifest intent filters), which is correct; but it also **lacks `departamentvpn://auth`** in the list, while carrying two schemes desktop lacks: `depv://routing/add/{base64}` and `depv://routing/onadd/{base64}` (`UrlSchemeListActivity.kt:23-31`). |
| **Local proxy as an inline expanding panel** | `SettingsView.axaml:475-516` | Android navigates to a full `LocalProxyActivity` (340 lines) — which is *richer* (memory limit presets 40/60/80/100/150 + unlimited, hotspot sharing with its own endpoint/user/pass) but breaks the "value changes here" rhythm for a 3-field edit. |
| **Sub-page save-on-back with `_saved` guard** | §3.3 | Android screens write on every interaction (`MmkvManager.encodeSettings` per toggle) — a different, also-defensible model, but the two platforms should be deliberate about which one they use. |

**Present on Android, MISSING on desktop**

| Android row | Location | Desktop status |
|---|---|---|
| **Always-on VPN и блокировка** (`settings_always_on` / `_sub` "Постоянное подключение и блокировка без VPN") | `layout:481-539`, `openAlwaysOnSettings()` `MainActivity.kt:2636-2644` | Platform-specific (deep-links `Settings.ACTION_VPN_SETTINGS`). No desktop analogue is needed. |
| Whole **«Устройства»** section: `settings_tv_send` "Перенести подписку на ТВ", `settings_tv_receive` "Принять подписку" (leanback-only, `MainActivity.kt:2466-2469`) | `layout:1211-1335` | Desktop has no ТВ transfer. |
| **Настройки провайдеров** row | `layout:1149-1209`, `MainActivity.kt:2461` | Desktop page exists but is unreachable (§5.2). |
| **Mode: "VPN + Proxy"** third option | `MainActivity.kt:2545-2580` | Desktop segment is binary. |
| Provider screen extras: notify-on-update, update-on-launch, ping-on-launch, ping-on-update, **send-HWID toggle**, **server sort order** (default/ping/name) | `ProviderSettingsActivity.kt:63-104, 206-229` | Desktop `ProviderSettingsPage` has only auto-update+interval, HWID **display**, User-Agent. |
| **Ping methods HTTP and ICMP** | `enums/PingMethod.kt:11-19`, `pickPingMethod` `:2595` | Desktop deliberately dropped them (engine lacks them) — see §5.6. Decide which platform is right. |

### 6.3 Differently organised (same feature, different shape)

1. **Tile colour.** Android is a **rainbow**: `bg_icon_blue/orange/green/purple/yellow/red` across 24
   rows (`layout_settings_content.xml:59, 120, 181, 246, 311, 372, 433, 499, 584, 650, 712, 796, 857,
   918, 1002, 1063, 1115, 1167, 1252, 1307, 1378, 1439, 1491`). Desktop settled on **one blue tile
   (Режим/щит) and neutral everywhere else** (§2.4). Porting the desktop decision means retinting 23
   of 24 Android tiles to `Brush.Tile.Neutral` + `colorOnSurface` glyph.
2. **Press feedback.** Android rows use `android:background="?attr/selectableItemBackground"` — i.e. a
   **Material ripple** — on every row (`layout:46, 107, 168, …`). Desktop explicitly rejected press
   feedback on rows (hover only) and rejects ripple everywhere (`GlobalStyles.axaml:371-372`
   *"Никакого ripple/glow"*). The two are currently contradictory.
3. **Value-change affordance.** Android puts a **chevron** on rows that merely open an `AlertDialog`
   (Режим, DNS, Пинг, Оформление, Язык, Автообновление, Число Mux — chevrons at `layout:92, 344, 405,
   829, 890, 1035, 683`). Under the desktop rule those seven are *value-cycle* or *segment* rows and
   must not carry a chevron. This is the single biggest IA change to make on Android.
4. **Sub-page chrome.** Android sub-screens are separate `Activity`s with a Material toolbar
   (`setContentViewWithToolbar(..., showHomeAsUp = true, title = …)`); desktop uses one in-app
   `subPageHost` stack with the seamless 56dp header (§3.2). The visual result is close; the
   difference is that Android's toolbar carries an overflow menu on some screens
   (`RoutingSettingActivity.onCreateOptionsMenu` `:69-80`, `PerAppProxyActivity` `:111`) which the
   desktop pages have no equivalent for.
5. **Mux count editing.** Desktop cycles a fixed set `[4,8,16,32,64,128]` in place; Android opens a
   free numeric `EditText` clamped to 1..1024 (`editMuxConcurrency`, `MainActivity.kt:2717-2734`).
6. **Auto-update value set.** Desktop `[60,360,720,1440]` (no "off"); Android
   `[0,60,360,720,1440]` including "Выкл" (`subAutoUpdateValues`, `MainActivity.kt:2827`), with the
   friendly labels **"1 час / 6 часов / 12 часов / 24 часа"** (`strings.xml:600-603`) vs desktop's
   terse **"1 ч. / 6 ч. …"** (`Common_HoursShort`). Android also guards the empty case with
   `settings_sub_auto_update_empty` **"Сначала добавьте подписку"** (`MainActivity.kt:2856-2859`) —
   desktop has no such guard.
7. **Ping method labels.** Android's are long — `settings_ping_method_real` **"Реальная задержка
   (через ядро)"**, `_tcp` **"TCP-соединение"**, `_http` **"HTTP-запрос"**, `_icmp` **"ICMP (системный
   ping)"** (`strings.xml:583-586`) — and are used **both** as the row value and as the dialog entry.
   Desktop split them: short value tokens ("Реальная"/"TCP") for the row, long title+hint pairs for the
   page (`Ping_RealTitle` + `Ping_RealHint`). The desktop split is the better pattern (§1.3).
8. **Boot-toggle copy diverges.** Desktop: `Settings_AutostartHint` **"Открывать departament при входе
   в систему"**. Android: `settings_boot_sub` **"Подключаться после перезагрузки устройства"**
   (`strings.xml:597`). These describe *different behaviours* (open the app vs auto-connect) — worth
   confirming which is true on each platform before unifying the copy.

### 6.4 Dead code on the Android side to clean up during the port

- **`SettingsActivity` (the whole legacy `PreferenceFragmentCompat` screen) has no launcher.** It is
  declared in `AndroidManifest.xml:89` and referenced only by its own layout
  (`res/layout/activity_settings.xml:9,13`) and a comment in `MainActivity.kt:2430`. A repo grep finds
  no `startActivity(... SettingsActivity ...)`. That leaves ~40 preferences unreachable, including:
  MTU, VPN interface address, sniffing, route-only, allow-insecure, core log level, outbound domain
  resolve method, hev-tunnel settings, xudp concurrency/quic, fragment length/interval/packets,
  domestic DNS, DNS hosts, real-ping concurrency, IP-API URL, auto-remove-invalid-after-test,
  auto-sort-after-test, delay-test URL (`res/xml/pref_settings.xml:79-349`).
- **`res/layout/layout_setting_row.xml` and `res/layout/layout_setting_toggle_row.xml` are never
  included.** Grep finds only their own `@+id/setting_row_root` and a passing mention in a comment at
  `res/layout/sheet_server_actions.xml:4`. `layout_settings_content.xml` inlines all 24 rows by hand
  (which is why it is 1536 lines). Extracting the two templates and reusing them would cut that file
  by roughly two thirds and make the archetype rules enforceable in one place.

---

## 7. Port checklist (condensed)

1. Adopt the **five archetypes** verbatim (§2.1) and retire chevrons from the seven Android
   dialog-backed value rows; convert Режим and Оформление to inline segments; convert Язык,
   Автообновление, Число Mux to in-place cycles with an `unfold_more` glyph.
2. **De-rainbow**: one blue tile (Режим), neutral for the other 23 (§2.4).
3. Replace the row **ripple** with hover/pressed states that do **not** scale the row (§2.2); keep
   press-scale on buttons (0.97) and chips (0.96) only.
4. Add the **«Производительность» section** with «Облегчённый режим» wired to a real reduced-motion
   flag that the animation layer reads live.
5. Decide on **Масштаб интерфейса** for Android (font-scale / display-density analogue) or drop it
   knowingly.
6. Split **Монохром** out of the appearance picker into an independent toggle over light/dark.
7. Build a real **«Пинг» sub-screen** (method rows with accent checkmarks + test address + timeout) and
   reconcile the 4-vs-2 method set with the engine's real capabilities.
8. Give the Android **DNS** row DoH presets and a FakeIP toggle, or explicitly document why Android
   stays on plain-IP.
9. Give **Routing** a first-class domain-strategy row with friendly RU labels and a visible
   "Стандартные правила / Сбросить" row instead of an overflow item.
10. Fix §5.1 (auto-update field/unit/screen conflict) **before** copying the row to Android.
11. Extract `layout_setting_row.xml` / `layout_setting_toggle_row.xml` and rebuild
    `layout_settings_content.xml` on top of them, so the archetype rules live in one place.
12. Either wire **`ProviderSettingsPage`** into the desktop settings list, or delete it — right now the
    two platforms disagree about whether provider settings exist.
