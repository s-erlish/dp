# 12 - Settings, both platforms

**Departament VPN - the complete settings specification. One information architecture, one row
vocabulary, two platforms, screen for screen.**

This document exists because of one owner sentence, quoted verbatim:

> «проработать все настройки что на андроид версии что на пк версии, чтобы все вкладки в
> настройках были проработаны под общий стиль и дизайн, а не абы как»

"Абы как" is the review failure. The acceptance bar is that a user who learns Настройки on the
phone recognises every group, every row, every label and every default on the desktop, and that no
row anywhere is a leftover from the upstream project.

---

## 0. What this is, and how to read it

### 0.1 Precedence

1. `00-rules.md` - the law. Any numeric value or ban here that contradicts it is a bug in this file.
2. `03-direction.md` - why the product looks like this. Section 7.3 (7 rows, 4 groups, 2 levels,
   1 display figure) is the density law this IA is built to satisfy.
3. `10-design-system.md` - the components and the semantic tokens. Every token name below
   (`color_on_surface`, `space_16`, `radius_control`, `dur_220`, `size_row`) is defined there in
   section 2, with its Android attribute and its desktop key.
4. `11-app-structure.md` section 9 - the settings IA skeleton. **This document is its completion,
   not its replacement.** Where it deviates, section 14 records the deviation and its reason.

### 0.2 The three columns every row carries

Every row in every table below is specified with all of:

| Column | Meaning |
|---|---|
| **Archetype** | One of the six in section 3. Determines the trailing element, the interaction and the states. Nothing else is allowed in a settings list. |
| **Label / helper** | The exact Russian string, sentence case, no final period on labels, no dash characters. Helper is <= 6 words, or empty. |
| **Default** | The value a fresh install has. Identical on both platforms unless the row is platform-only. |
| **Binding** | The real field it writes. Android: the `AppConfig.PREF_*` key. Desktop: the `Config` path. `NEW` marks a key that does not exist yet and must be added. |
| **Effect** | When the change takes effect: `сразу` (UI only), `при переподключении` (core config, applied live only if the core is already running), or `после перезапуска` (process-level). |

### 0.3 Files this document replaces

**Android** (`/home/user/dp/V2rayNG/app/src/main/`)

| File | Fate |
|---|---|
| `res/layout/layout_settings_content.xml` (1 536 lines, 20 hand-copied rows) | deleted, replaced by a `RecyclerView` over the model in 8.3 |
| `res/layout/layout_setting_row.xml`, `layout_setting_toggle_row.xml` (orphans, never included) | rewritten as the row components in 8.2 and actually used |
| `ui/SettingsActivity.kt` + `res/xml/pref_settings.xml` (354 lines, unreachable) | deleted; its ~48 preferences are triaged in sections 4 to 6 |
| `ui/LocalProxyActivity.kt` + `res/layout/activity_local_proxy.xml` (1 035 lines, 112 hardcoded dp) | deleted, replaced by `settings/advanced/localproxy` (5.10) |
| `ui/ProviderSettingsActivity.kt` + `res/layout/activity_provider_settings.xml` (648 lines) | deleted, split between the hub, `settings/latency` and `settings/providers` |
| `ui/UrlSchemeListActivity.kt` + `res/layout/activity_url_scheme_list.xml` (634 lines, 5 section cards) | deleted, replaced by one list at `settings/about/urlschemes` (5.16) |
| `ui/BackupActivity.kt`, `res/layout/dialog_webdav.xml` | rebuilt as `settings/data` and `settings/data/webdav` |
| `ui/RoutingSettingActivity.kt`, `ui/RoutingEditActivity.kt` | rebuilt as `settings/routing` and `settings/routing/rule/{id}` |
| `ui/UserAssetActivity.kt`, `ui/UserAssetUrlActivity.kt` | rebuilt as `settings/assets`, the URL screen becomes a sheet |
| `ui/PerAppProxyActivity.kt`, `ui/AppPickerActivity.kt` | merged into `settings/perapp` |
| `ui/AboutActivity.kt` (pure 2018 upstream) | rebuilt as `settings/about` |
| `ui/LogcatActivity.kt` (unreachable) | restyled as `settings/about/log`, reachable for the first time |
| the six single-choice `AlertDialog`s in `MainActivity.kt` (Режим, Пинг, DNS, Оформление, Язык, Автообновление) and `editMuxConcurrency` / `editDnsCustom` / `editUserAgent` | deleted; replaced by segments (2 to 3 options) and picker sheets (4+), per `03-direction.md` F13 |

**Desktop** (`/home/user/v2rayN/v2rayN/v2rayN.Desktop/`)

| File | Fate |
|---|---|
| `Views/SettingsView.axaml` (1 075 lines, 6 sections, no `MaxWidth`) | rebuilt to the IA in section 4, `MaxWidth = 720` |
| `Views/SettingsView.axaml.cs` (359 lines) | the affordance contract in its header comment is kept and extended; the `unfold_more` cycle affordance and the inline local-proxy panel are removed (14, D-S2 and D-S4) |
| `Views/PingSettingsPage.axaml` | becomes `settings/latency` (5.6), gains 6 rows |
| `Views/ProviderSettingsPage.axaml` (built, styled, **zero references**) | wired as `settings/providers` (5.7) |
| `Views/UrlSchemesPage.axaml` | becomes `settings/about/urlschemes` (5.16), reached from About, not from the hub |
| `Views/PerAppProxyPage.axaml` | stays as `settings/perapp` (5.1), switches to the shared `Button.BackNav` |
| `Views/RoutingSubView.axaml` | stays as `settings/routing` (5.2); its escape hatch into `RoutingRuleSettingWindow` (900x600, `resx` strings) is closed |
| `Views/DnsSubView.axaml`, `GeoFilesPage.axaml`, `AboutPage.axaml`, `BackupPage.axaml` | restyled to the specs in section 5 |
| `Views/OptionSettingWindow.axaml` (1 206 lines, 91 `resx:` refs, unreachable) | deleted; its unique controls migrate to `settings/advanced` (5.9) and `settings/window` (5.14) |
| `Views/GlobalHotkeySettingWindow.axaml`, `FullConfigTemplateWindow.axaml`, `SubSettingWindow.axaml` | deleted; features migrate to `settings/window`, `settings/advanced/template`, and the Серверы provider headers |
| `Views/ThemeSettingView.axaml`, `BackupAndRestoreView.axaml` (registered, never built) | deleted |
| `Views/CheckUpdateView.axaml` (registered, never built) | wired into `settings/about` (desktop only) |
| `Views/MsgView.axaml` (registered, never built) | rebuilt as `settings/about/log` (5.17) |

---

## 1. The baseline this replaces, measured

Not opinion. These are counts from `01-inventory-android.md` and `02-inventory-pc.md`.

| Symptom | Android today | Desktop today |
|---|---|---|
| Settings rows visible in the hub | 20, in a 1 536-line hand-copied layout | 20, in a 1 075-line layout |
| Settings that exist in the engine and are unreachable | ~30 (`res/xml/pref_settings.xml`, loaded only by an activity nothing launches) | ~10 (`OptionSettingWindow`, no UI binding) |
| Settings with two homes | «Автообновление подписки» in the hub AND in `ProviderSettingsActivity`, two taps apart, in two visual languages, writing the same field | none, but `Settings_BypassLan` is bound to `Inbound[0].AllowLANConn`, which is a **different setting** than Android's `PREF_VPN_BYPASS_LAN` (see 6.4) |
| Parallel row grammars | 3 (settings-tab row 40/16/Body/18-chevron/72-divider; hub-layer row 40/14/`16sp`/18-chevron/68-divider/60-minHeight; upstream row 24-icon/16/`AppCompat.Subhead`/no chevron) | 2 (Incy `Border.SettingRow` and raw Semi in the legacy windows) |
| Hardcoded dp in the settings tree | 112 + 66 + 57 = 235 in three files alone | 6, 10, 14, 20, 28, 40, 68, 72 across the settings views |
| Raw `android:textSize` in the settings tree | 37 + 20 + 15 = 72 in three files | 3 (`FontSize="18"` / `"20"`) |
| Single-choice `AlertDialog`s where an inline control belongs | 6 | 0 (but the replacement, cycle-in-place, hides the option set) |
| Content measure | none | none in the hub, 620 in the sub-pages, 560 in the account tabs: three values |
| Settings search | none | none |
| Touch targets under 48 in the settings tree | 8 distinct components | caption 44x22, rail toggle 30x30, meta 34x34, legacy `IconButton` 32 |

**The result of this document, counted the same way:** one row grammar, one measure (720), one
spacing scale, 23 rows in the Android hub and 25 in the desktop hub (21 and 23 at rest), 18 routes,
0 unreachable settings, 0 settings with two homes, 0 single-choice dialogs, and a search field.

---

## 2. Ten laws for this surface

Derived from the foundation documents, restated here because an implementer of a settings screen
should not have to reconstruct them.

1. **A settings screen is rows, not cards.** `00-rules.md` 4.4 and `10-design-system.md` 6.5. There
   is **no card anywhere in the settings tree** - not in the hub, not on a sub-page. Groups are made
   by a section header plus 24 of space; siblings are separated by a 1 dp hairline at the 68 origin.
   The desktop's current `Border.Card ClipToBounds Padding=0` wrapper around each group is deleted.
2. **Zero accent on the hub.** `03-direction.md` 3.2: "a settings screen with no blue on it anywhere
   is correct, not unfinished". The only blue permitted in the whole settings tree is: the selected
   segment fill, the check glyph on a selected picker row, the switch track when on, the focus ring,
   and one primary button on `settings/assets` («Обновить сейчас») and `settings/data`. Every icon
   tile is `color_tile_neutral`. There is no coloured tile category system in settings.
3. **Every affordance is honest.** The trailing element states what the tap will do, before the tap.
   Chevron = leaves this screen. Value text = opens a picker here. Switch = flips here. Segment =
   changes here. Nothing = performs an action here. This contract is inherited verbatim from
   `Views/SettingsView.axaml.cs:14-22`, which is the single best design decision in either codebase.
4. **One trailing element per row. Never two.** `00-rules.md` 4.5.
5. **Max 7 rows per group, max 4 named groups per screen, max 2 levels below the tab.**
   `03-direction.md` 7.3. A group that wants an eighth row is two groups or a sub-page.
6. **A helper line says what the row does, never what its title already says.** `03-direction.md`
   F16. Most rows have no helper. A helper exists when the consequence is not obvious from the
   noun («Обход локальной сети» -> «Прямой доступ к устройствам в локальной сети») or when the row
   is dangerous («Разрешать небезопасные соединения» -> «Отключает проверку сертификата»).
7. **A toggle is named for what is true when it is on.** `00-rules.md` 9.2. The current Android
   «Блокировать UDP», which writes `!PREF_SOCKS_ENABLE_UDP`, is a double negative and becomes
   «UDP через прокси», default on.
8. **A setting that the app can decide correctly is not a setting.** Section 6 removes 21 of them.
   Every removal names its replacement behaviour and its migration rule.
9. **No modal for a choice.** 2 or 3 options: inline segment. 4 or more: a picker sheet (Android) or
   a picker flyout (desktop) with a radio list. A free value: the same picker surface containing one
   labelled field. A dialog appears exactly twice in the whole settings tree, both times for a
   genuinely irreversible action (5.11 «Сбросить настройки», 5.2 «Сбросить правила»).
10. **Every state ships.** `00-rules.md` 15. For this surface that specifically means: a disabled row
    always says **why** it is disabled in its helper; a value row that is loading holds a 20 dp
    indeterminate in the trailing slot; a failed write shows the transient message and reverts the
    control to the persisted value rather than lying about it.

---

## 3. The archetype vocabulary

Six archetypes. Nothing else appears in a settings list on either platform. All six are the
universal row from `10-design-system.md` 6.4 with a different trailing element, so the 68 text
origin, the 56 minimum height, the hairline inset and the hit target are identical across all of
them.

| # | Archetype | Trailing element | Tap does | Used when |
|---|---|---|---|---|
| **A1** | **Navigation** | chevron, `size_glyph_inline` 20, `color_on_surface_variant` | pushes a sub-page | The setting is a group of settings, or a list |
| **A2** | **Value** | the current value as text, Subtitle 13/400, `color_on_surface_variant`, right-aligned, `maxWidth` 40% of the row | opens the picker (sheet on Android, flyout on desktop) anchored to this row | 4 or more options, or one free value |
| **A3** | **Toggle** | switch 52x32 (`10-design-system.md` 6.17) | flips the boolean | A boolean |
| **A4** | **Segment** | none in the trailing slot; a full-width segmented track occupies a second line inside the same row | selects one of 2 or 3 options in place | Exactly 2 or 3 mutually exclusive options that the user changes often |
| **A5** | **Action** | none, or a 20 dp `ic_open_external` glyph when the tap leaves the app | performs an operation on this screen | Обновить сейчас, Скопировать, Создать копию, Открыть сайт |
| **A6** | **Destructive** | none | removes or resets, with undo, or with a confirm dialog when irreversible | Сбросить настройки, Сбросить правила, Удалить файл |

Two supporting components that are **not** row archetypes and never appear in the hub:

- **The field block** (`10-design-system.md` 6.3): label above (Title.Medium 16/500), field 52 tall
  at `radius_control` 16 on `color_surface_inset`, helper or error below at Caption 12. Used only
  inside sub-pages, for free text: DNS address, ping URL, User-Agent, SOCKS login and password,
  hosts entries. The helper slot is present in the markup even when empty so nothing jumps.
- **The picker** (Android: `BottomSheetDialogFragment`; desktop: `Flyout`). Two contents, one
  chrome: a radio list of up to 8 rows, or one field block plus a 52 primary «Сохранить». Full spec
  in 8.4 and 9.4.

### 3.1 Archetype states

All six inherit the row state table of `10-design-system.md` 6.4. The settings-specific additions:

| State | Every archetype | A2 specifically | A3 specifically |
|---|---|---|---|
| Default | title `color_on_surface`, helper `color_on_surface_variant`, tile `color_tile_neutral` with a `color_tile_glyph_neutral` glyph | value `color_on_surface_variant` | switch off = track `color_surface_inset` |
| Hover (desktop) | `color_state_hover` across the full row, `radius_control` 16, 150 ms `ease_standard` | same | same |
| Focus | 2 px `color_accent` ring inset 2. Android: keyboard and TV only. Desktop: always | same | the **row** owns the tab stop; the switch is removed from the tab order |
| Pressed | Android ripple `color_state_press` bounded to the row; both platforms scale 0.97, 90 ms in / 160 ms out | same | same |
| Selected | n/a | n/a | on = track `color_accent`, thumb translated +20 |
| Disabled | 0.38 on the whole row, no ripple, **and the helper states the reason** | value renders `Недоступно` | switch 0.38, not togglable |
| Loading | trailing slot holds a 20 dp indeterminate in `color_accent`; row is not tappable | value replaced by the indicator | switch replaced by the indicator |
| Error (write failed) | helper swaps to `color_destructive_text` with the cause; the transient message carries «Повторить» | value reverts to the persisted one | switch springs back over `dur_220` |

### 3.2 Conditional rows

Two rows in the hub and several on sub-pages are visible only while their parent is on
(«Число соединений Mux», «Параметры фрагментации», the SOCKS credentials, «Только для
маршрутизации»). The rule, identical on both platforms:

- The dependent row is inserted or removed, not merely faded. It animates with the group: height
  and alpha over `dur_reveal` 300 `ease_out_quint` in, `dur_225` `ease_standard` out. Under reduced
  motion (`MotionUtils.animationsEnabled` / `MotionState.IsLite`) it snaps.
- The hairline above it appears and disappears with it, so a group never ends on a hanging divider.
- A dependent row is **never** shown disabled instead of hidden. A disabled row is for a setting the
  user could have but cannot right now (no subscription, no network), not for one that does not
  apply.

---

## 4. The hub: Настройки

### 4.1 The screen, top to bottom

```
[ header 56 ]  «Настройки», Title 16/700 at the 16 gutter, no leading, no trailing
               ground plane, elevation 0, no divider at rest
               a 1 dp color_outline_variant hairline fades in at scrollY > 0 over dur_220
[ 8 ]
[ search 48 ] «Поиск по настройкам», radius_control 16, color_surface_inset,
               leading 20 glyph at 16, trailing 20 clear glyph when filled
[ 24 ]
Подключение                          section header, Title 16/700, sentence case, at the gutter
[ 8 ]
  7 rows                              hairline between siblings, inset to 68
[ 24 ]
Обход блокировок
[ 8 ]
  2 to 4 rows
[ 24 ]
Подписки
[ 8 ]
  5 rows
[ 24 ]
Приложение
[ 8 ]
  5 rows (Android) / 7 rows (desktop)
[ 24 ]
  Данные и резервные копии  >         footer pair, NO section header
  О приложении              >
[ 32 ]                                before the bottom navigation inset (Android)
```

The footer pair has no section header, which is exactly what makes it structurally not a group. It
is also why the 4-groups-per-screen cap is satisfied with 4 named groups.

### 4.2 Group 1: Подключение

7 rows. The group a user opens the tab for.

| # | Archetype | Label | Helper | Default | Binding (Android / Desktop) | Effect |
|---|---|---|---|---|---|---|
| 1.1 | **A4** | `Режим подключения` | none | `VPN` | `PREF_MODE` + `PREF_PROXY_SHARING` / `TunModeItem.EnableTun` + `Inbound[0].AllowLANConn` | при переподключении |
| 1.2 | **A2** | `Прокси по приложениям` | none | `Выкл` | `PREF_PER_APP_PROXY` (read-only display) / `UiItem.PerAppProxyEnabled` | - |
| 1.3 | **A1** | `Маршрутизация` | none | - | `settings/routing` | - |
| 1.4 | **A2** | `DNS` | none | `1.1.1.1` shown as `Cloudflare` | `PREF_VPN_DNS` / `SimpleDNSItem.RemoteDNS` | при переподключении |
| 1.5 | **A3** | `Обход локальной сети` | `Прямой доступ к устройствам в локальной сети` | вкл | `PREF_VPN_BYPASS_LAN` (`"1"` on / `"2"` off) / `TunModeItem.RouteExcludeAddress` private ranges, **NEW binding** | при переподключении |
| 1.6 | **A3** | `IPv6` | `Включить IPv6 в туннеле` | выкл | `PREF_IPV6_ENABLED` / `TunModeItem.EnableIPv6Address` | при переподключении |
| 1.7 | **A1** | `Дополнительно` | none | - | `settings/advanced` | - |

**1.1 Режим подключения.** Three segments, in this order and with these exact labels:

| Segment | Means | Android writes | Desktop writes |
|---|---|---|---|
| `VPN` | The tunnel captures all device traffic. The default and the correct answer for a consumer. | `PREF_MODE = "VPN"`, `PREF_PROXY_SHARING = false` | `TunModeItem.EnableTun = true`, `Inbound[0].AllowLANConn = false` |
| `Прокси` | No tunnel. Only the local SOCKS5 and HTTP proxy on 127.0.0.1 run. | `PREF_MODE = "Proxy only"` | `TunModeItem.EnableTun = false` |
| `Вместе` | Tunnel plus the local proxy reachable from the local network, for a TV or a console behind the same router. | `PREF_MODE = "VPN"`, `PREF_PROXY_SHARING = true` | `TunModeItem.EnableTun = true`, `Inbound[0].AllowLANConn = true` |

- `Вместе` carries a helper only while it is selected: `Прокси доступен другим устройствам в сети`.
  A segment row's helper is allowed to change with the selection; that is the one place a helper is
  dynamic, and it is what stops the third option from being a mystery.
- Changing the mode while the core is running re-applies live and shows the transient message
  `Переподключаемся, чтобы применить настройку`. Changing it while disconnected shows nothing and
  **never starts the core**. This is the OFF model already documented in
  `SettingsViewModel.SetTunMode` and it must survive the rebuild: a settings tap has never started
  the tunnel and must not begin to.
- Desktop must not route this through `StatusBarViewModel.EnableTun`'s `DoEnableTun`, which reloads
  unconditionally and calls `RebootAsAdmin()` with a UAC prompt on non-admin Windows. Write the
  config first, persist, mirror the shared VM, reload only when running. The existing code comment
  at `ViewModels/SettingsViewModel.cs:314-323` explains this; keep it.
- **Narrow-width degradation:** at Android width < 360 dp, or at font scale >= 1.3, or at desktop
  window width < 420, the segment row degrades to an **A2 Value** row opening a 3-item picker. Same
  strings, same defaults, same bindings. This is a designed responsive rule, not a fallback: a
  3-segment track cannot hold «Вместе» at 200% font scale without truncating, and
  `00-rules.md` 1.1 bans a truncated primary label.

**1.2 Прокси по приложениям** is an A2 Value row and not an A1 Navigation row on purpose: its value
is the state a user needs to see from the hub. The value string is one of:

| State | Value text |
|---|---|
| off | `Выкл` |
| on, bypass mode, n apps | `Кроме 12` (Numeric role for the figure) |
| on, include mode, n apps | `Только 3` |
| on, mode set, 0 apps | `Не выбрано` |

Tapping it opens `settings/perapp` (5.1). An A2 row that navigates is legal per
`10-design-system.md` 6.4 ("opens a segmented inline control, a sheet, **or a sub-page**"); what is
illegal is a chevron that does not navigate.

**1.4 DNS** value text: the preset name when the stored value matches a preset
(`Cloudflare`, `Google`, `AdGuard`, `По умолчанию`), otherwise the raw address, ellipsised at the
end. Tapping opens `settings/dns` (5.4).

**1.5 Обход локальной сети on desktop is a bug fix, not a port.** Today
`ViewModels/SettingsViewModel.cs:160` binds this row to `Inbound[0].AllowLANConn`, which means
"let other machines on the LAN use my local proxy" - the opposite direction of traffic from what the
label promises, and the same field the `Вместе` mode now owns. The correct desktop binding is the
private-range direct route (`TunModeItem.RouteExcludeAddress` seeded with
`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `fc00::/7`, `fe80::/10`). See
6.4 for the migration.

### 4.3 Group 2: Обход блокировок

4 rows, 2 of them conditional. At rest the group shows 2.

| # | Archetype | Label | Helper | Default | Binding | Effect |
|---|---|---|---|---|---|---|
| 2.1 | **A3** | `Мультиплексирование (Mux)` | `Объединяет запросы в один канал` | выкл | `PREF_MUX_ENABLED` / `Mux4SboxItem.Protocol` (`"h2mux"` on, `""` off) | при переподключении |
| 2.2 | **A2** | `Число соединений Mux` | none | `8` | `PREF_MUX_CONCURRENCY` / `Mux4SboxItem.MaxConnections` | при переподключении |
| 2.3 | **A3** | `Фрагментация пакетов` | `Разбивает TLS-рукопожатие против DPI` | выкл | `PREF_FRAGMENT_ENABLED` / `CoreBasicItem.EnableFragment` | при переподключении |
| 2.4 | **A1** | `Параметры фрагментации` | none | - | `settings/fragment` | - |

- 2.2 is visible only while 2.1 is on. Picker options: `4`, `8`, `16`, `32`, `64`, `128`. The
  figures are set in the Numeric role with `tnum`.
- 2.4 is visible only while 2.3 is on. It replaces the current situation where the three fragment
  parameters exist only in the unreachable `pref_settings.xml` on Android and only in the
  unreachable `OptionSettingWindow` on desktop.
- The group is named for the user's goal, not for the mechanism. Neither Mux nor fragmentation is a
  thing a consumer knows; "обход блокировок" is why he is here.

### 4.4 Group 3: Подписки

5 rows.

| # | Archetype | Label | Helper | Default | Binding | Effect |
|---|---|---|---|---|---|---|
| 3.1 | **A2** | `Автообновление подписок` | none | `Каждый час` | every `SubscriptionItem.autoUpdate` + `.updateInterval`, then `SubscriptionUpdater.sync(forceReschedule = true)` / `GuiItem.AutoUpdateInterval` | сразу |
| 3.2 | **A3** | `Обновлять при запуске` | none | выкл | `PREF_SUB_UPDATE_ON_LAUNCH` / `GuiItem.UpdateSubscriptionOnLaunch` **NEW** | сразу |
| 3.3 | **A1** | `Проверка задержки` | none | - | `settings/latency` | - |
| 3.4 | **A1** | `Провайдеры` | none | - | `settings/providers` | - |
| 3.5 | **A1** | `Файлы ресурсов` | none | - | `settings/assets` | - |

- 3.1 picker options and their exact labels: `Выключено`, `Каждый час`, `Каждые 6 часов`,
  `Каждые 12 часов`, `Раз в сутки`. Stored in minutes on Android (0 / 60 / 360 / 720 / 1440) and in
  hours on desktop (`GuiItem.AutoUpdateInterval`, 0 / 1 / 6 / 12 / 24). The unit difference is an
  implementation detail; the user-facing option set is identical.
- **3.1 has exactly one home.** Today Android writes the same fields from two screens two taps
  apart. `ProviderSettingsActivity`'s auto-update toggle, its interval picker and its private
  `pref_provider_update_interval` key are deleted, and `settings/providers` no longer contains an
  update section.
- 3.1 with zero subscriptions: the row is **disabled**, its value reads `Нет подписок` and its
  helper reads `Добавьте провайдера, чтобы включить`. This replaces today's silent toast
  (`MainActivity.pickSubAutoUpdate` early-returns with `settings_sub_auto_update_empty`), which is
  an actionable message delivered by a `Toast` and therefore banned by `00-rules.md` 1.4.8.
- The group is «Подписки», plural. `00-rules.md` 9.3 locks «подписка» for the user's active service;
  this group is about the provider feeds that populate the server list, and calling it «Подписки»
  is the one place the two senses touch. The rows inside it disambiguate: «Провайдеры» is the noun
  for a feed, per the terminology lock.

### 4.5 Group 4: Приложение

5 rows on Android, 7 on desktop.

| # | Archetype | Label | Helper | Default | Binding | Effect | Platform |
|---|---|---|---|---|---|---|---|
| 4.1 | **A4** | `Оформление` | none | `Тёмная` | `PREF_UI_MODE_NIGHT` (`0` системная / `1` светлая / `2` тёмная) / `UiItem.CurrentTheme` | сразу | both |
| 4.2 | **A3** | `Чёрная тема` | `Чистый чёрный фон без цветного акцента` | выкл | `PREF_COLOR_THEME` (`blue` / `mono`) / `UiItem.BlackTheme` | сразу | both |
| 4.3 | **A2** | `Язык` | none | `Системный` | `PREF_LANGUAGE` / `UiItem.CurrentLanguage` | сразу | both |
| 4.4 | **A3** | `Меньше движения` | `Отключает анимации` | выкл | `PREF_REDUCED_MOTION` **NEW** / `UiItem.LiteMode` | сразу | both |
| 4.5 | **A3** | `Запуск при старте` | `Открывать departament при входе в систему` | выкл | `PREF_IS_BOOTED` / `GuiItem.AutoRun` | сразу | both |
| 4.6 | **A2** | `Масштаб интерфейса` | none | `100%` | - / `UiItem.UiScale` | сразу | desktop |
| 4.7 | **A1** | `Окно и горячие клавиши` | none | - | `settings/window` | - | desktop |

**4.1 Оформление** segments in this order: `Тёмная`, `Светлая`, `Системная`. Dark is first and is
the default because the product is dark by design (`03-direction.md` 2.1: a bright surface at 30%
brightness in a dark carriage is a physical insult). This replaces the current Android picker whose
three options are `Светлая` / `Тёмная` / `Чёрно-белая` - a set that conflates the base variant with
the mono overlay, so a user cannot have a light monochrome theme even though the token set defines
one (`10-design-system.md` 2.2 ships four theme columns: dark, light, mono dark, mono light).

**4.2 Чёрная тема** is the mono overlay on **both** platforms: on Android it is the runtime
`ThemeOverlay.Mono`, on desktop it is `App.axaml.cs:580 BuildMonoOverlay(light)`. It composes with
4.1 instead of replacing it. The label is `Чёрная тема` on both; the desktop's current
`Settings_Monochrome` («Монохром») and Android's `settings_theme_mono` («Чёрно-белый режим») are
retired to one string (14, D-S5).

**4.3 Язык** picker options: `Системный`, `Русский`, `English`. Language endonyms are not
translated. Android applies it through `BaseActivity.attachBaseContext` and needs `recreate()`;
desktop applies it live through `L.Instance.SetLanguage` with no restart. Neither shows a restart
prompt: `00-rules.md` 9.1 forbids apologising, and the desktop already dropped its reboot notice.

**4.4 Меньше движения** on Android currently has no key at all: reduced motion is read only from the
system animator scale via `MotionUtils.animationsEnabled(context)`. The new `PREF_REDUCED_MOTION`
is ORed with the system value, so the app can be calmer than the system but never louder. This
closes a real parity gap: desktop has had `UiItem.LiteMode` with a live broadcast
(`Common/MotionState.cs`) since the redesign began.

**4.5 Запуск при старте** on Android is the boot receiver, which does **not** connect - it only
starts the app's service so the tile and the shortcut work. The helper must not promise a
connection. Today `settings_boot_sub` reads «Подключаться после перезагрузки устройства», which is
a claim the code does not make. Corrected copy in section 11.

**4.6 Масштаб интерфейса** picker options: `100%`, `110%`, `125%`, `150%`. Android has no
equivalent and never gets one: the OS font scale is the Android answer and the layouts are
required to survive 200% of it (`00-rules.md` 11.4). Logged as parity gap PG-S1.

### 4.6 The footer pair

No section header. 24 above the first of the two, 32 below the second.

| Archetype | Label | Helper | Route |
|---|---|---|---|
| **A1** | `Данные и резервные копии` | none | `settings/data` |
| **A1** | `О приложении` | the version as the row's helper, `Версия 2.4.1`, figures in the Numeric role | `settings/about` |

The version in the helper is the one place a figure appears in the hub, and it is set in the
Numeric role with `tnum` so it does not reflow between builds.

### 4.7 Count and depth audit

| | Android | Desktop |
|---|---|---|
| Named groups | 4 | 4 |
| Rows, total | 23 | 25 |
| Rows visible at rest (both conditionals off) | 21 | 23 |
| Largest group | Подключение, 7 | Подключение, 7 and Приложение, 7 |
| Routes below the hub | 16 | 17 |
| Deepest route | `settings/routing/rule/{id}`, level 2 | same |
| Level-3 routes | 0 | 0 |
| Rows with two homes | 0 | 0 |
| Settings reachable only from a dead screen | 0 | 0 |

---

## 5. The sub-pages

Every sub-page shares one skeleton, identical on both platforms:

```
[ toolbar 56 ]  [16][ back 24 in a 48 box ][16][ title, Title 16/700 ][ * ][ 0 or 1 action 40 ][16]
                background = page background (ground plane), elevation 0, no divider at rest
[ 16 ]
[ intro paragraph ]   optional, Body 14/400, color_on_surface_variant, max 60 characters per line
[ 24 ]
[ section header ][ 8 ][ rows ]
[ 24 ]  repeated per group
[ 32 ]
```

- Content column: `size_content_max` 720, centred, gutter 16 (24 at `sw600dp` and at desktop width
  >= 1000). This replaces the desktop's current three measures (none / 620 / 560).
- Back: system Back and predictive Back on Android; the toolbar button **plus Esc plus mouse button
  4** on desktop. The missing Escape handler is `02-inventory-pc.md` gap V12 and it is closed here,
  for every sub-page, by the shell (`_subStack`), not per page.
- A sub-page never opens another sub-page except where the route table says so, and never past
  level 2.
- The intro paragraph exists only where the page's subject is not self-evident (DNS, маршрутизация,
  файлы ресурсов, схемы URL). It is one sentence. Pages whose rows explain themselves do not carry
  one.

### 5.0 Route map

| Route | Title | Level | Entry | Platform |
|---|---|---|---|---|
| `settings/perapp` | Прокси по приложениям | 1 | hub 1.2 | both |
| `settings/routing` | Маршрутизация | 1 | hub 1.3 | both |
| `settings/routing/rule/{id}` | Правило | 2 | routing rule row | both |
| `settings/dns` | DNS | 1 | hub 1.4 | both |
| `settings/advanced` | Дополнительно | 1 | hub 1.7 | both |
| `settings/advanced/localproxy` | Локальный прокси | 2 | advanced | both |
| `settings/advanced/template` | Шаблон конфигурации | 2 | advanced | desktop |
| `settings/fragment` | Параметры фрагментации | 1 | hub 2.4 | both |
| `settings/latency` | Проверка задержки | 1 | hub 3.3 | both |
| `settings/providers` | Провайдеры | 1 | hub 3.4 | both |
| `settings/assets` | Файлы ресурсов | 1 | hub 3.5 | both |
| `settings/window` | Окно и горячие клавиши | 1 | hub 4.7 | desktop |
| `settings/data` | Данные и резервные копии | 1 | hub footer | both |
| `settings/data/webdav` | Облачная копия | 2 | data | both |
| `settings/tv` | Перенести на ТВ | 2 | data | Android |
| `settings/about` | О приложении | 1 | hub footer | both |
| `settings/about/urlschemes` | Схемы URL-адресов | 2 | about | both |
| `settings/about/log` | Журнал | 2 | about | both |

### 5.1 `settings/perapp` - Прокси по приложениям

Absorbs Android `PerAppProxyActivity` + `AppPickerActivity` (a 10-line bare `RecyclerView` with no
header and no empty state) and desktop `PerAppProxyPage`.

Toolbar action: 40 dp overflow with `Выбрать все`, `Снять выделение`, `Инвертировать`,
`Импорт списка`, `Экспорт списка`. Five actions in an overflow is correct here and only here: they
are bulk operations on a list, not settings.

**Группа «Режим»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `Раздельное туннелирование` | `Выберите, какие приложения идут через VPN` | выкл | `PREF_PER_APP_PROXY` / `UiItem.PerAppProxyEnabled` |
| **A4** | `Правило` | dynamic, see below | `Кроме выбранных` | `PREF_BYPASS_APPS` / `UiItem.PerAppProxyBypass` |

Segment labels: `Кроме выбранных` / `Только выбранные`. Dynamic helper:
`Выбранные идут напрямую, мимо VPN` and `Через VPN идут только выбранные`. Both rows disable the
list below when the master toggle is off; the list renders at 0.38 with the helper
`Включите раздельное туннелирование`.

**Группа «Приложения»**

- A 48 search field, `Поиск по приложениям`, filtering in place.
- Desktop only: a 40 icon button `Добавить программу` opening a native file picker for a `.exe`.
- The list: virtualised, one row per app. Row anatomy is the universal row with the app icon in the
  40 tile slot (decoded at 40, never full size), the app name as Title, the package or path as
  Subtitle in the Numeric role (it is an identifier), and a **checkbox** in the trailing slot. This
  is the one place a checkbox exists in the product, because the row expresses membership of a set,
  not a setting. Selected rows sort to the top on entry only, never while the user is typing.
- Empty result: `Ничего не найдено` / `Попробуйте другой запрос.` / `Сбросить поиск`.
- Loading (the app list takes 300 ms or more to enumerate): 8 skeleton rows of the same height, not
  a spinner. Today Android shows a bare progress bar and desktop shows nothing.
- Android footer helper: `Изменения применятся при следующем подключении`. Desktop:
  `Работает в режиме TUN. Правила применятся при следующем подключении.`

### 5.2 `settings/routing` - Маршрутизация

Absorbs Android `RoutingSettingActivity` (whose five actions hide in a toolbar overflow the rest of
the app does not have) and desktop `RoutingSubView` (whose edit path drops into the 900x600
`resx`-stringed `RoutingRuleSettingWindow`).

Intro: `Наборы правил решают, какой трафик идёт через VPN, а какой напрямую.`

**Группа «Наборы правил»** - a reorderable list of selection rows.

| Element | Spec |
|---|---|
| Row | universal row, tile = `ic_route_24dp` neutral, Title = the set name, Subtitle = `24 правила` with the figure in the Numeric role |
| Selected | `color_selected_fill` + a 20 dp check glyph in `color_accent` + Title weight 700. Two channels |
| Reorder | long press and drag (Android `ItemTouchHelper`), pointer drag on the 20 dp handle (desktop). The handle is the row's only trailing element while reorder mode is on |
| Per-item actions | Android bottom sheet, desktop flyout: `Изменить`, `Дублировать`, `Экспортировать`, `Удалить` (destructive) |
| Empty | `Наборов правил пока нет` / `Добавьте набор или восстановите стандартные.` / `Добавить набор` |

Below the list, two A5 rows: `Добавить набор` and `Импортировать набор`. `Импортировать набор`
opens a picker with `Из буфера обмена`, `Из QR-кода`, `Стандартные наборы`. The current five
overflow items become two visible rows plus one picker, which is `distill.md`'s consolidation rule
applied literally.

**Группа «Домены»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A2** | `Стратегия доменов` | none | `Как есть` | `PREF_ROUTING_DOMAIN_STRATEGY` / `RoutingBasicItem.DomainStrategy` |
| **A2** | `Разрешение доменов` | `Как ядро сопоставляет домены с правилами` | `IP при несовпадении` | `PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD` / `RoutingBasicItem.DomainStrategy4Singbox` |

Strategy picker options: `Как есть`, `IP при несовпадении`, `IP по запросу`.

**Группа «Обслуживание»**

| Archetype | Label | Helper | Behaviour |
|---|---|---|---|
| **A5** | `Восстановить стандартные наборы` | none | rebuilds the built-in sets, shows loading in the trailing slot, then the transient `Стандартные наборы восстановлены` |
| **A6** | `Сбросить правила` | `Удалит все наборы, включая свои` | confirm dialog: title `Сбросить правила маршрутизации?`, body `Все наборы, включая созданные вами, будут удалены. Действие нельзя отменить.`, buttons `Отмена` / `Сбросить` (destructive) |

### 5.3 `settings/routing/rule/{id}` - Правило

Absorbs Android `RoutingEditActivity` (raw upstream: bare `TextView` labels, `EditText`s and a
`Spinner`) and desktop `RoutingRuleDetailsWindow`.

Not rows. This is a form of field blocks (3, second bullet), in this order, each with its label
above and its helper slot below:

| Field | Type | Helper | Validation |
|---|---|---|---|
| `Название` | text, single line | none | required, non-empty, trimmed |
| `Действие` | A2 value row | none | `Через VPN` / `Напрямую` / `Заблокировать`. Default `Через VPN` |
| `Домены` | text, multiline, 4 visible lines | `Один домен в строке. Поддерживаются geosite:` | each line must be a domain, a `geosite:` token or a `regexp:` token |
| `IP-адреса` | text, multiline, 4 visible lines | `Один адрес или диапазон в строке. Поддерживаются geoip:` | each line must be an IP, a CIDR or a `geoip:` token |
| `Порты` | text, single line | `Например: 80, 443, 1000-2000` | digits, commas and ranges only |
| `Протоколы` | chip multi-select | none | `http`, `tls`, `bittorrent`, `quic` |
| `Приложения` | text, multiline | `Имена процессов, по одному в строке` | desktop only |

Validation is on **blur**, never per keystroke. The toolbar carries one trailing action,
`Сохранить`, disabled until the form is valid and dirty; it shows the inline loading state while
writing. A rule that fails to save shows the error under the offending field and moves focus to it.
Deleting a rule happens from the list, not from the editor.

### 5.4 `settings/dns` - DNS

Absorbs the Android DNS `AlertDialog` pair (`editDns` / `editDnsCustom` in `MainActivity.kt`), the
hidden `pref_settings.xml` DNS keys, and desktop `DnsSubView`.

Intro: `DNS-сервер, через который приложение разрешает домены. По умолчанию используется
встроенный резолвер.`

**Группа «Провайдер»** - a wrap of filter chips, not rows. Chip spec from
`10-design-system.md` 6.6: height 24, `radius_fitting` 12, Chip role 11/500. Selected chip:
`color_accent_container` fill + `color_on_accent_container` label + weight 700 (two channels).

`По умолчанию` · `Cloudflare` · `Google` · `AdGuard` · `Свой`

| Chip | Writes | Value |
|---|---|---|
| `По умолчанию` | `PREF_VPN_DNS` + `PREF_REMOTE_DNS` / `SimpleDNSItem.RemoteDNS` | the shipped default |
| `Cloudflare` | same | `1.1.1.1` |
| `Google` | same | `8.8.8.8` |
| `AdGuard` | same | `94.140.14.14` |
| `Свой` | same | whatever the field below holds |

Selecting `Свой` reveals a field block below with the label `Адрес DNS-сервера`, the helper
`DoH-адрес (https://…/dns-query), DoT или обычный IP`, and the placeholder `1.1.1.1`. Validation on
blur; an invalid address shows `Проверьте адрес DNS-сервера` and does not persist.

**Группа «Дополнительно»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `FakeIP` | `Ускоряет соединение, отвечая на запросы локально` | выкл | `PREF_FAKE_DNS_ENABLED` / `SimpleDNSItem.FakeIP` |
| **A3** | `Локальный резолвер` | `Разрешать домены внутри приложения` | выкл | `PREF_LOCAL_DNS_ENABLED` / n/a on desktop (implicit in sing-box) |
| **A2** | `DNS для прямых соединений` | none | `Системный` | `PREF_DOMESTIC_DNS` / `SimpleDNSItem.DirectDNS` |

**Группа «Свои записи»** - one multiline field block, label `Записи hosts`, helper
`Одна запись в строке: домен и адрес`, monospaced input in the Numeric role.
Binding `PREF_DNS_HOSTS` / `SimpleDNSItem.Hosts`.

Every change on this page is `при переподключении`.

### 5.5 `settings/fragment` - Параметры фрагментации

New page. Today these three values exist only in `res/xml/pref_settings.xml` (unreachable) and
`OptionSettingWindow` (unreachable), while the toggle that needs them is in the hub on both
platforms. Three field blocks:

| Field | Label | Helper | Default | Binding |
|---|---|---|---|---|
| 1 | `Длина` | `Диапазон в байтах, например 50-100` | `50-100` | `PREF_FRAGMENT_LENGTH` / `Fragment4RayItem.Length` |
| 2 | `Интервал` | `Пауза между частями, мс` | `10-20` | `PREF_FRAGMENT_INTERVAL` / `Fragment4RayItem.Interval` |
| 3 | `Пакеты` | none | `tlshello` | `PREF_FRAGMENT_PACKETS` / `Fragment4RayItem.Packets` |

Field 3 is an A2 value row, not a text field: options `tlshello`, `1-3`, `1-2`. All three are
`при переподключении`. A row of copy at the bottom: `Значения по умолчанию подходят большинству
сетей. Меняйте их, только если соединение не устанавливается.`

### 5.6 `settings/latency` - Проверка задержки

Absorbs the Android ping `AlertDialog` (`pickPingMethod`), three toggles currently stranded in
`ProviderSettingsActivity`, four keys from `pref_settings.xml`, and desktop `PingSettingsPage`.

Intro: `Как измерять задержку серверов.`

**Группа «Метод»** - selection rows, one check glyph, two channels.

| Row | Helper | Binding value |
|---|---|---|
| `Реальная задержка` | `Через ядро, как при подключении` | `PingMethod.PROXIED_REAL_DELAY` / `ESpeedActionType.Realping` |
| `TCP-подключение` | `Быстрее, но менее точно` | `PingMethod.TCP_CONNECT` / `ESpeedActionType.Tcping` |

Default: `Реальная задержка`. The two other Android methods (`HTTP_URL`, `ICMP`) are removed; see
6.2 for the migration.

**Группа «Проверка»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| field | `Адрес проверки` | none | `https://www.gstatic.com/generate_204` | `PREF_DELAY_TEST_URL` / `SpeedTestItem.SpeedTestUrl` |
| **A2** | `Тайм-аут` | none | `5 с` | `PREF_PING_TIMEOUT` **NEW** / `SpeedTestItem.SpeedTestTimeout` |
| **A2** | `Одновременных проверок` | none | `16` | `PREF_REAL_PING_CONCURRENCY` / `SpeedTestItem.MixedConcurrencyCount` |

Timeout picker: `3 с`, `5 с`, `10 с`, `15 с`. Concurrency picker: `4`, `8`, `16`, `32`, `64`. Both
figures in the Numeric role.

**Группа «Автоматически»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `Проверять при запуске` | none | выкл | `PREF_PING_ON_LAUNCH` / `SpeedTestItem.PingOnLaunch` **NEW** |
| **A3** | `Проверять после обновления подписки` | none | вкл | `PREF_PING_ON_UPDATE` / `SpeedTestItem.PingOnUpdate` **NEW** |
| **A3** | `Сортировать по задержке после проверки` | none | выкл | `PREF_AUTO_SORT_AFTER_TEST` / **NEW** |
| **A3** | `Удалять нерабочие после проверки` | `Серверы без ответа будут удалены` | выкл | `PREF_AUTO_REMOVE_INVALID_AFTER_TEST` / **NEW** |

### 5.7 `settings/providers` - Провайдеры

Absorbs what is left of Android `ProviderSettingsActivity` after 4.4 and 5.6 take their rows, and
wires desktop `ProviderSettingsPage`, which is fully built, fully styled and referenced by nothing.

**Группа «Обновление»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `Уведомлять об обновлении` | none | вкл | `PREF_SUB_NOTIFY_ON_UPDATE` / `GuiItem.NotifyOnSubUpdate` **NEW** |

**Группа «Сеть»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `Отправлять идентификатор устройства` | `Нужен, чтобы считать устройства в тарифе` | вкл | `PREF_SEND_HWID` / **NEW** |
| field | `User-Agent` | `Отправляется при обновлении подписки` | the operator default from `BackendConfig.subscriptionUserAgent` | `PREF_SUB_USER_AGENT` / `CoreBasicItem.DefUserAgent` |

The User-Agent field shows the **effective** value, not an empty box: when the user has set no
override, the operator default is displayed as the field's content in `color_on_surface_variant`
with the helper `Значение по умолчанию`. Clearing the field restores the default rather than
sending an empty header. This behaviour already exists in `ProviderSettingsActivity.currentUserAgent`
and is worth preserving verbatim: a row that advertises a string the app never sends is a lie.

Desktop additionally shows the device identifier itself as a read-only field with a copy action,
because desktop support asks for it; Android does not, because there is nowhere to paste it. Logged
as PG-S4, an allowed asymmetry.

**Группа «Список серверов»**

| Archetype | Label | Default | Binding |
|---|---|---|---|
| **A2** | `Порядок серверов` | `Как у провайдера` | `PREF_SERVER_SORT_ORDER` / `UiItem.ServerSortOrder` **NEW** |

Picker: `Как у провайдера`, `По задержке`, `По имени`. Order is a property of the stored list on
Android (`SettingsManager.applyServerSortOrder` rewrites it), so the change is `сразу` and the
Серверы tab reflects it on return.

### 5.8 `settings/assets` - Файлы ресурсов

Absorbs Android `UserAssetActivity` + `UserAssetUrlActivity` and desktop `GeoFilesPage`.

Intro: `Базы geoip и geosite нужны для маршрутизации по странам и доменам.`

**Группа «Базы»** - two A5 rows, each with a live subtitle:

| Row | Subtitle by state |
|---|---|
| `geoip.dat` | loaded: `4,2 МБ · обновлён 12 июля` · missing: `Не загружен` · updating: `Обновление…` · failed: `Не удалось обновить` in `color_destructive_text` |
| `geosite.dat` | same |

Sizes and dates in the Numeric role. Below the two rows, one **primary button** `Обновить сейчас`,
full width at the gutter, `size_cta` 52 - the only filled accent surface on this page, and one of
only two in the whole settings tree. While updating it shows the inline loading state (label swaps
for a 20 dp indeterminate in `color_on_accent`, size unchanged) and both rows show `Обновление…`.

**Группа «Источник»**

| Archetype | Label | Default | Binding |
|---|---|---|---|
| **A2** | `Источник обновлений` | the first entry of `AppConfig.GEO_FILES_SOURCES` | `PREF_GEO_FILES_SOURCES` / `ConstItem.GeoSourceUrl` |

**Группа «Свои файлы»** - a list of user assets, each an A6-capable row: Title = file name,
Subtitle = size and date, per-item sheet with `Обновить`, `Поделиться`, `Удалить`. Below it one A5
row `Добавить файл`, opening a picker with `Из файла`, `По ссылке`, `Из QR-кода`. The `По ссылке`
option opens a picker containing two field blocks (`Название`, `Ссылка`) and a `Сохранить` button -
this is what deletes `UserAssetUrlActivity`, an entire raw-upstream activity, without losing the
feature.

Empty: `Своих файлов нет` / `Добавьте файл, если провайдер прислал свою базу.` / `Добавить файл`.

### 5.9 `settings/advanced` - Дополнительно

The home for everything that is real, that a competent user may need, and that must not be in the
hub. It absorbs most of `res/xml/pref_settings.xml` and most of `OptionSettingWindow`.

**Группа «Ядро»**

| # | Archetype | Label | Helper | Default | Binding | Platform |
|---|---|---|---|---|---|---|
| a | **A2** | `Ядро` | none | `sing-box` | - / `CoreTypeItem` (applied to every config type) | desktop |
| b | **A2** | `Уровень журнала` | none | `Предупреждения` | `PREF_LOGLEVEL` / `CoreBasicItem.Loglevel` | both |
| c | **A3** | `Определение домена в трафике` | `Помогает правилам маршрутизации` | вкл | `PREF_SNIFFING_ENABLED` / `Inbound[0].SniffingEnabled` | both |
| d | **A3** | `Только для маршрутизации` | `Не подменять адрес назначения` | выкл | `PREF_ROUTE_ONLY_ENABLED` / `Inbound[0].RouteOnly` | both |
| e | **A3** | `Разрешать небезопасные соединения` | `Отключает проверку сертификата сервера` | выкл | `PREF_ALLOW_INSECURE` / per-profile | both |
| f | **A3** | `Переключать сервер при сбое` | `Если сервер не отвечает после подключения` | вкл | `PREF_AUTO_FALLBACK` / **NEW** | both |

- Row d is visible only while row c is on.
- Row b picker: `Никакой`, `Ошибки`, `Предупреждения`, `Информация`, `Отладка`, mapping to
  `none` / `error` / `warning` / `info` / `debug`. Selecting `Отладка` shows the transient
  `Отладочный журнал заметно нагружает устройство`.
- Row e is the only row in the settings tree whose helper is a warning without being destructive
  red. It stays neutral: red is for destroy and error (`00-rules.md` 6.2), not for risk.
- Row a: desktop only, options `sing-box` and `Xray`. Today the core is chosen per config type in
  eight separate dropdowns inside `OptionSettingWindow`; one choice applied to all of them is the
  consumer-correct simplification, and the per-type set is dropped (14, D-S7).

**Группа «Туннель»**

| # | Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|---|
| g | **A2** | `MTU` | none | `1500` | `PREF_VPN_MTU` / `TunModeItem.Mtu` |
| h | **A2** | `Адрес интерфейса` | none | `10.10.14.x` | `PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX` / n/a |
| i | **A1** | `Локальный прокси` | none | - | `settings/advanced/localproxy` |

Row g opens the field variant of the picker: one numeric field, label `MTU`, helper
`От 576 до 9000. По умолчанию 1500`, and `Сохранить`. Out-of-range input shows
`Введите значение от 576 до 9000` and does not persist. Row h picker lists the seven address ranges
already in `res/values/arrays.xml`.

**Группа «Конфигурация»** (desktop only)

| Archetype | Label | Helper | Route |
|---|---|---|---|
| **A1** | `Шаблон конфигурации` | `Свой JSON поверх сгенерированного` | `settings/advanced/template` |

The template page is the `JsonEditor` control (kept, chrome restyled) inside the standard sub-page
skeleton, with `Сохранить` in the toolbar and a `Сбросить к стандартному` A6 row below the editor.
This is what deletes `FullConfigTemplateWindow`.

**Android only, at the bottom, no section header:** one A5 row `Постоянный VPN` with the helper
`Настраивается в системных настройках Android` and the external-link glyph. It opens
`Settings.ACTION_VPN_SETTINGS`. Today this row lives in the hub's Подключение group and shows a
`Toast` before launching the intent; the toast is deleted (`00-rules.md` 1.4.8) and the helper
carries the same information permanently.

### 5.10 `settings/advanced/localproxy` - Локальный прокси

Replaces `activity_local_proxy.xml`: 1 035 lines, 112 hardcoded dp, 37 raw `textSize`, 0 tokens,
five sections of developer plumbing including a memory-limit chip row. It becomes 9 rows and 3
fields. It also absorbs the desktop's inline expand panel from `SettingsView`.

**Группа «Локальный прокси»**

| # | Archetype | Label | Helper | Default | Binding | Platform |
|---|---|---|---|---|---|---|
| a | **A3** | `Локальный прокси` | `SOCKS5 и HTTP на 127.0.0.1` | вкл | `PREF_ENABLE_LOCAL_PROXY` / always on | Android |
| b | field | `Порт` | none | `10808` | `PREF_SOCKS_PORT` / `Inbound[0].LocalPort` | both |
| c | **A3** | `UDP через прокси` | none | вкл | `PREF_SOCKS_ENABLE_UDP` / `Inbound[0].UdpEnabled` | both |
| d | **A3** | `HTTP-прокси на соседнем порту` | none | выкл | `PREF_APPEND_HTTP_PROXY` / `Inbound[0].SecondLocalPortEnabled` | both |
| e | **A3** | `Доступ из локальной сети` | `Другие устройства смогут пользоваться прокси` | выкл | `PREF_PROXY_SHARING` / `Inbound[0].AllowLANConn` | both |

Row c replaces Android's `Блокировать UDP`, which stores the inverse and reads as a double negative
(law 7). Row e is the same field that the hub's `Вместе` mode sets; the two stay in sync because
they write one field, and switching the hub mode away from `Вместе` turns this row off.

**Группа «SOCKS5-авторизация»**

| # | Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|---|
| f | **A3** | `SOCKS5-авторизация` | none | выкл | derived: on when both credentials are non-empty |
| g | field | `Логин` | none | generated `dep_xxxxxx` | `PREF_SOCKS_USERNAME` / `Inbound[0].User` |
| h | field | `Пароль` | none | generated 12 hex | `PREF_SOCKS_PASSWORD` / `Inbound[0].Pass` |
| i | **A5** | `Создать новые логин и пароль` | none | - | regenerates both, shows the transient `Логин и пароль обновлены` |

Rows g, h and i are visible only while f is on. Turning f off clears both stored values so the core
falls back to no-auth, which is the current Android behaviour and is correct. The password field
carries the show/hide toggle in its end slot; both fields carry a copy action as a 40 icon button
**outside** the field, not inside it, so the row keeps one trailing element.

**Группа «Адрес подключения»** - visible only while `Доступ из локальной сети` is on. One read-only
field showing `SOCKS5 192.168.1.42:10810` with a copy action, and the helper
`Адрес действует, пока устройство в этой сети`. When no LAN address can be resolved it reads
`Устройство не подключено к локальной сети` and the copy action is disabled.

**Deleted with this page:** the memory-limit section (five outlined chips 40/60/80/100/150 plus a
«Снять ограничение» toggle) and the hotspot duplicate of the credential fields. See 6.1.

### 5.11 `settings/data` - Данные и резервные копии

Absorbs Android `BackupActivity` and desktop `BackupPage`.

Intro: `Все настройки, подписки и серверы сохраняются в один файл.`

**Группа «Резервная копия»**

| Archetype | Label | Helper | Behaviour |
|---|---|---|---|
| **A5** | `Создать копию` | none | writes the `.zip`, shows loading in the trailing slot, then the transient `Копия сохранена` with the action `Открыть папку` (desktop) or `Поделиться` (Android) |
| **A5** | `Восстановить из копии` | `Приложение перезапустится` | file picker, then a confirm dialog, then restore |
| **A5** | `Поделиться копией` | none | Android only, the system share sheet |
| **A1** | `Облачная копия` | `WebDAV` | `settings/data/webdav` |

Today Android puts `Создать копию` and `Восстановить` behind an `AlertDialog` list that asks
«локально или WebDAV» first. That dialog is deleted: local is the direct action, WebDAV is a
sub-page with its own two actions.

**Группа «Устройства»** (Android only)

| Archetype | Label | Route |
|---|---|---|
| **A1** | `Перенести подписку на ТВ` | `settings/tv` |

**Группа «Сброс»**

| Archetype | Label | Helper | Behaviour |
|---|---|---|---|
| **A6** | `Сбросить настройки` | `Серверы и подписки останутся` | confirm dialog. Title `Сбросить настройки?` Body `Все настройки вернутся к значениям по умолчанию. Серверы, подписки и аккаунт не пострадают.` Buttons `Отмена` / `Сбросить` |

This is one of the two dialogs in the settings tree. It qualifies under `00-rules.md` 7.5 because
it is irreversible and there is nothing to undo it with.

### 5.12 `settings/data/webdav` - Облачная копия

Replaces `dialog_webdav.xml`, which is four unlabelled `EditText`s in a `ScrollView`. Four field
blocks with real labels, then two actions:

| Field | Label | Placeholder | Helper |
|---|---|---|---|
| 1 | `Адрес сервера` | `https://webdav.example.com` | none |
| 2 | `Логин` | none | none |
| 3 | `Пароль` | none | none, show/hide toggle in the end slot |
| 4 | `Папка` | `departament` | `Создаётся автоматически, если её нет` |

Then: A5 `Проверить подключение` (shows loading, then `Подключение работает` or the error under the
first field), A5 `Выгрузить копию`, A5 `Загрузить копию`. Validation on blur; an address without a
scheme shows `Адрес должен начинаться с https://`.

### 5.13 `settings/tv` - Перенести на ТВ (Android)

`TvSendActivity`, restyled to the sub-page skeleton with tokens. Structure: one instruction
paragraph, a subscription picker (A2 value row), a primary `Показать QR-код` button, and the QR
surface. `TvReceiveActivity` is unchanged and LEANBACK-only; it is not reachable from this tree.

### 5.14 `settings/window` - Окно и горячие клавиши (desktop)

Absorbs the window behaviour section of `OptionSettingWindow` and the whole of
`GlobalHotkeySettingWindow`.

**Группа «Окно»**

| Archetype | Label | Helper | Default | Binding |
|---|---|---|---|---|
| **A3** | `Сворачивать в трей при закрытии` | none | вкл | `UiItem.Hide2TrayWhenClose` |
| **A3** | `Запускать свёрнутым` | none | выкл | `UiItem.AutoHideStartup` |
| **A3** | `Показывать в Dock` | none | вкл | `UiItem.MacOSShowInDock`, macOS only |

**Группа «Горячие клавиши»** - four capture rows, one per `EGlobalHotkey`:

| Label | Default |
|---|---|
| `Показать окно` | not set |
| `Подключить или отключить` | not set |
| `Сменить сервер` | not set |
| `Обновить подписки` | not set |

A capture row is an A2 Value row whose value is the current combination in the Numeric role
(`Ctrl + Alt + D`) or `Не назначено`. Tapping puts the row into capture state: the value slot reads
`Нажмите сочетание…`, the row keeps focus, the next key combination is captured, Esc cancels. A
conflict with another row shows the helper `Уже назначено: Показать окно` in
`color_destructive_text` and does not persist. One A6 row below: `Сбросить сочетания`.

### 5.15 `settings/about` - О приложении

Replaces Android `AboutActivity`, which is pure 2018 upstream (`TextAppearance.AppCompat.Subhead`,
untinted 24 dp icons, no tiles, no brand font) and is, in the inventory's words, the last screen a
user sees before deciding the app is amateur.

Header block, centred, above the first group: the wordmark `departament` at Title 20/700 in the
brand face, then `Версия 2.4.1` at Subtitle 13/400 with the figures in the Numeric role. No logo
lockup, no tagline, no illustration, and no accent: `03-direction.md` 3.2's corollary forbids the
brand spending the one accent on advertising itself.

**Группа «Поддержка»**

| Archetype | Label | Trailing | Opens |
|---|---|---|---|
| **A5** | `Сайт departament.site` | external glyph | the site in a Custom Tab / the default browser |
| **A5** | `Telegram-бот` | external glyph | the bot |
| **A5** | `Проверить обновления` | none | desktop only; shows loading, then the result inline in the helper |

**Группа «Для разработчика»**

| Archetype | Label | Route |
|---|---|---|
| **A1** | `Схемы URL-адресов` | `settings/about/urlschemes` |
| **A1** | `Журнал` | `settings/about/log` |

**Группа «Правовое»**

| Archetype | Label | Trailing |
|---|---|---|
| **A5** | `Политика конфиденциальности` | external glyph |
| **A5** | `Лицензии открытого кода` | external glyph |

Below the last group, one centred A5 text action `Скопировать сведения об устройстве`, which copies
the OS, architecture, runtime and app version as plain text for support. It replaces the two
"screenshot the raw server response" diagnostic dialogs currently shipped to end users from
`DeviceManagementActivity` and the payment flow; those are deleted and their raw bodies go to the
log (`11-app-structure.md` 8.3).

### 5.16 `settings/about/urlschemes` - Схемы URL-адресов

Replaces 634 lines and five section cards with one list. Reached from About, **not** from the hub:
a `depv://` cheat sheet is developer reference, and `01-inventory-android.md` is right that shipping
it in the consumer settings tree is a category error.

Desktop keeps its registration block at the top: a read-only status line
(`Схема зарегистрирована` / `Схема не зарегистрирована`) and two buttons,
`Зарегистрировать` / `Убрать`. On Linux and macOS the block reads
`Регистрация схемы доступна только на Windows.` and the buttons are disabled. Android has no
registration block: the scheme is declared in the manifest.

Then one list, no section cards, one row per scheme:

| Title | Subtitle |
|---|---|
| `depv://connect` (Numeric role) | `Запустить туннель` |
| `depv://disconnect` | `Остановить соединение` |
| `depv://toggle` | `Переключить соединение` |
| `depv://open` | `Открыть приложение` |
| `depv://close` | `Закрыть приложение` |
| `depv://import/{base64}` | `Импорт (тип определяется сам)` |
| `depv://add/{url}` | `Добавить по ссылке` |
| `depv://routing/add/{base64}` | `Добавить набор правил` |
| `depv://routing/onadd/{base64}` | `Добавить и включить набор правил` |

The scheme string is the Title and it is set in the brand face (it is a technical token, not
Russian prose - `03-direction.md` 3.1). Tapping the row copies it and shows the transient
`Скопировано`. The row's trailing element is a 40 copy icon button; the whole row also copies, so
the two do the same thing and there is no ambiguity.

Helper at the top: `Нажмите на схему, чтобы скопировать.`

### 5.17 `settings/about/log` - Журнал

`11-app-structure.md` 8.3. A virtualised, timestamped list in the Numeric role, a two-option filter
segment (`Все` / `Ошибки`), and two toolbar actions collapsed into one 40 overflow
(`Скопировать всё`, `Очистить`). Row: timestamp (Numeric 12), level glyph 16, message (Body 14,
wrapping, never truncated mid-word). Error lines carry `color_destructive_text`.

Empty: `Журнал пуст` / `Здесь появятся события приложения.` / no action.

This gives Android `LogcatActivity` an entry point for the first time and gives desktop `MsgView` a
home, closing `02-inventory-pc.md` V11 (an app with no user-visible feedback surface at all).

---

## 6. The cut list

`03-direction.md` F15: nothing ships that cannot be reached, and nothing is kept "for now". Every
row below stops being a setting in the same change that replaces it. Each one names its replacement
behaviour and its migration rule, so a user who had set it does not silently get a different app.

### 6.1 Removed: the app decides correctly

| Setting today | Where | Replacement behaviour | Migration |
|---|---|---|---|
| `pref_speed_enabled` «показывать скорость» | `pref_settings.xml` | Always on. The connect screen's numeric strip is part of the design, not an option | value ignored |
| `pref_confirm_remove` «подтверждать удаление» | `pref_settings.xml`, read by `MainActivity.kt:1381` | Always off. Deletion is immediate plus an undo action for 5 s (`00-rules.md` 7.5). A confirmation dialog for a reversible action is the pattern undo exists to kill | value ignored |
| `pref_start_scan_immediate` | `pref_settings.xml` | Always on. The scanner starts scanning when it opens | value ignored |
| `pref_double_column_display` | `pref_settings.xml` | Always off. The server list is one column at every width; a two-column server list breaks the 68 origin and the row grammar | value ignored |
| `pref_group_all_display` | `pref_settings.xml` | Always grouped by provider. That is the Серверы design | value ignored |
| `pref_show_memory` | `pref_settings.xml`, gates `card_memory` | Deleted with the memory card | value ignored |
| `pref_prefer_ipv6` | `pref_settings.xml` | Folded into the single `IPv6` toggle. Two switches for one intent is the defect | on -> `PREF_IPV6_ENABLED = true` |
| `pref_use_hev_tunnel_v2`, `pref_hev_tunnel_loglevel`, `pref_hev_tunnel_rw_timeout_v2` | `pref_settings.xml` | The app picks the tunnel implementation. The tunnel's own log level folds into `Уровень журнала` | values ignored |
| `pref_dynamic_socks_port` | `pref_settings.xml` | Always off. The port is a fixed, copyable number or it is useless to paste into another app | on -> port stays whatever was last allocated |
| `pref_mux_xudp_concurrency`, `pref_mux_xudp_quic` | `pref_settings.xml` | Derived: xudp concurrency follows `Число соединений Mux`, quic stays `reject` | values ignored |
| `pref_ip_api_url` | `pref_settings.xml` | Operator-set, not user-set | value ignored |
| `pref_memory_limit`, `pref_memory_limit_enabled` + the 5-chip memory section | `LocalProxyActivity` | Removed. A soft memory cap for the core is not a consumer decision, and the section presented it as five outlined chips plus an inverted "Снять ограничение" toggle | values ignored, cap stays at the shipped default |
| `UiItem.EnableStatistics`, `DisplayRealTimeSpeed`, `EnableAutoAdjustMainLvColWidth`, `EnableDragDropSort`, `DoubleClick2Activate`, `TrayMenuServersLimit`, `CurrentFontFamily`, `CurrentFontSize`, `MainGirdOrientation`, `EnableHWA`, `KeepOlderDedupl` | desktop `OptionSettingWindow` | All fixed by the design. Font family and size in particular are owned by the type ramp (`00-rules.md` 5.2) and cannot be user-set without breaking every layout | values ignored |
| `SystemProxyItem.*` (PAC path, script path, exceptions, advanced protocol) | desktop `OptionSettingWindow` | Not exposed. System-proxy mode is not one of the three modes this product ships | values ignored |
| `ClashUIItem.*` and the Mihomo proxy-group surface | desktop | Cut with `ClashProxiesView` / `ClashConnectionsView` (`11-app-structure.md` D-11) | n/a |
| `KcpItem`, `GrpcItem`, `HysteriaItem` tuning | desktop `OptionSettingWindow` | Per-protocol transport tuning belongs to the server form, not to app settings | values preserved, edited per server |

### 6.2 Removed: the option set is wrong

| Setting today | Replacement | Migration |
|---|---|---|
| Ping method `HTTP_URL` and `ICMP` (Android `PingMethod` has four values; desktop has always had two) | Two methods, `Реальная задержка` and `TCP-подключение` (5.6). HTTP duplicates real delay with a worse signal; ICMP needs raw sockets and is silently blocked on most mobile networks, so it reports a failure that is not the server's | stored `http` or `icmp` -> `PROXIED_REAL_DELAY` on first read |
| Android «Оформление» as a 3-way radio (`Светлая` / `Тёмная` / `Чёрно-белая`) | Base variant segment (4.1) plus a `Чёрная тема` toggle (4.2), composable, matching desktop | `Чёрно-белая` -> variant `Тёмная` + `Чёрная тема` on |
| Desktop core selection as eight per-config-type dropdowns | One `Ядро` value row applied to every type (5.9 row a) | the most common stored value wins; `Xray` only if every type was `Xray` |
| Android `Автообновление подписки` living in two screens | One row, hub 3.1 | `pref_provider_update_interval` (a private key inside `ProviderSettingsActivity`) is read once into the subscription items, then deleted |

### 6.3 Merged, not removed

| Was | Now |
|---|---|
| `Локальный прокси` as a top-level hub row (Android) and as an inline expand panel (desktop) | `settings/advanced/localproxy` (5.10), one page, both platforms |
| `LocalProxyActivity`'s «Маршрутизация по домену» (`PREF_ROUTE_ONLY_ENABLED`) | `settings/advanced` row d, next to the sniffing toggle it actually depends on |
| `LocalProxyActivity`'s «Доступ через хотспот» section (endpoint, warnings, login, password) | `settings/advanced/localproxy` group 3, and the credentials are the same two fields as SOCKS auth instead of a second copy |
| `ProviderSettingsActivity`'s ping toggles | `settings/latency` group «Автоматически» |
| `UserAssetUrlActivity` (a whole activity for two fields) | the `По ссылке` picker on `settings/assets` |
| `AppPickerActivity` (a 10-line bare `RecyclerView`) | the app list on `settings/perapp` |
| `dialog_webdav.xml` (four unlabelled `EditText`s) | `settings/data/webdav` (5.12) |
| Desktop `SubSettingWindow` | the provider group headers on Серверы (`11-app-structure.md` 4.2) |

### 6.4 Two bindings that are wrong today and are corrected here

**B1. Desktop «Обход локальной сети» writes the wrong field.**
`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:160` and `:214` bind the row to
`Inbound[0].AllowLANConn`. That flag makes the local proxy listen on the LAN interface so **other
machines can use it**. Android's `PREF_VPN_BYPASS_LAN` does the opposite thing in the opposite
direction: it routes traffic **to** LAN addresses outside the tunnel. Two platforms, one label, two
unrelated behaviours - the exact failure the parity contract exists to prevent.

Correction: desktop 1.5 binds to the private-range direct route (`TunModeItem.RouteExcludeAddress`
seeded with `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `fc00::/7`,
`fe80::/10`), and `AllowLANConn` becomes the `Доступ из локальной сети` row in
`settings/advanced/localproxy` plus the hub's `Вместе` mode. On upgrade, a desktop install with
`AllowLANConn = true` gets `Доступ из локальной сети` on and `Обход локальной сети` on (the new
default), which is what the user believed he had.

**B2. Android «Блокировать UDP» stores the inverse of its label.**
`LocalProxyActivity.kt:198-204` renders `!PREF_SOCKS_ENABLE_UDP`. Correction: the row becomes
`UDP через прокси`, default on, storing the value directly. No migration needed - the stored field
is unchanged, only the label and the sense of the switch.

### 6.5 What the cut list costs, counted

| | Before | After |
|---|---|---|
| User-settable values that exist in code | ~68 Android `PREF_*` keys, ~90 desktop reactive properties | ~62 Android, ~68 desktop |
| Of those, reachable from the UI | 20 Android, 20 desktop | **all of them** |
| Of those, unreachable | ~30 Android, ~10 desktop | **0** |
| Settings removed outright | - | 21 |
| Settings merged into an existing one | - | 8 |
| Bindings corrected | - | 2 (6.4) |
| Screens deleted | - | 11 Android activities and layouts, 9 desktop views |

The count barely moves and that is the point: this is not a cull, it is a triage. About 30 real
settings stop being invisible, 21 fake ones stop being settings, and everything that survives is
reachable, has one home, has a stated default, and writes a field that changes behaviour. That last
clause is the whole exercise: a settings screen full of switches that do nothing is worse than a
short one.

---

## 7. Settings search

New on both platforms. With about 40 settings across 18 routes, search is what stops the hierarchy
from becoming a maze, and it is the reason a deep sub-page is acceptable at all.

### 7.1 Behaviour

- The field sits under the header on the hub only. Sub-pages do not have one.
- It filters **in place**: the hub's groups are replaced by a flat result list. It never navigates.
- It matches, in this priority order: row label, row helper, the label of any row on a sub-page,
  the sub-page title, and a hidden keyword list per row (see 7.3).
- Matching is case-insensitive, accent-insensitive, and matches on any word boundary, not only the
  prefix: typing `dns` finds `DNS`, `DNS для прямых соединений` and `Записи hosts`.
- A result row renders as the universal row plus a **breadcrumb caption** at Caption 12 under the
  subtitle: `Подключение › Дополнительно`. The breadcrumb is the only place `›` appears as a
  character in the product.
- Tapping a result performs the row's own archetype behaviour. If the row lives on a sub-page, the
  sub-page opens **and the row flashes**: its background animates `color_selected_fill` to
  transparent over `dur_reveal` 300 `ease_out_quint`, once, so the user's eye lands on it. Under
  reduced motion the fill simply appears for 600 ms and then clears.
- Back from a result returns to the search results with the query intact, not to the unfiltered hub.
  `00-rules.md` 7.7: Back restores scroll position, filter state and input.
- Desktop: `Ctrl+F` focuses the field from anywhere in the Настройки tab, `Ctrl+,` from anywhere in
  the app opens the tab and focuses the field. Esc clears the query; Esc on an empty query leaves
  the field.

### 7.2 States

| State | Treatment |
|---|---|
| Rest | placeholder `Поиск по настройкам` at `color_on_surface_variant` |
| Focused | 2 px `color_accent` border, no ring on top of it |
| Filled | trailing 20 dp clear glyph appears; the groups below are replaced by results |
| No results | `Ничего не найдено` / `Попробуйте другой запрос.` / a `Сбросить поиск` text button. Never a blank list |
| Cleared | the hub returns with its scroll position and its expanded conditionals unchanged |

### 7.3 Keywords

Every row carries a hidden keyword list so a user who thinks in the other vocabulary still lands.
The list is part of the row model (8.3) and is localised with the labels. Examples, not the full
set:

| Row | Keywords |
|---|---|
| `Режим подключения` | tun, прокси, proxy, socks, туннель |
| `Обход локальной сети` | lan, локальная сеть, роутер, принтер |
| `Мультиплексирование (Mux)` | mux, мультиплекс, соединения |
| `Фрагментация пакетов` | dpi, фрагмент, tls, блокировка |
| `Чёрная тема` | amoled, монохром, чёрный, oled |
| `Меньше движения` | анимация, motion, lite, движение |
| `Локальный прокси` | socks5, порт, 10808, http |
| `Журнал` | лог, log, ошибки, диагностика |

---

## 8. Android layout specification

Package `com.v2ray.ang`, Gradle root `/home/user/dp/V2rayNG`. Kotlin, XML views, Material 3,
ViewBinding. Every value below is a token from `10-design-system.md` section 2; the raw numbers are
given so an implementer can check a screenshot with a ruler.

### 8.1 The hub screen

The Настройки tab is a destination, not an activity: it lives inside `MainActivity`'s shell as the
`nav_settings` group. Its content is **one `RecyclerView`**, not 1 536 lines of inflated blocks.

```xml
<!-- res/layout/layout_settings_content.xml, rebuilt: ~40 lines total -->
<LinearLayout
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground">

    <!-- header 56, ground plane, no elevation, no divider at rest -->
    <FrameLayout
        android:id="@+id/settings_header"
        android:layout_width="match_parent"
        android:layout_height="@dimen/toolbar_height">           <!-- 56dp -->
        <TextView
            android:id="@+id/settings_title"
            android:layout_gravity="center_vertical"
            android:layout_marginStart="@dimen/screen_gutter"     <!-- 16dp -->
            android:textAppearance="@style/TextAppearance.App.Title"
            android:text="@string/nav_settings" />
        <View
            android:id="@+id/settings_header_hairline"
            android:layout_width="match_parent"
            android:layout_height="@dimen/stroke_hairline"        <!-- 1dp -->
            android:layout_gravity="bottom"
            android:alpha="0"
            android:background="?attr/colorOutlineVariant" />
    </FrameLayout>

    <!-- search 48 -->
    <include layout="@layout/component_search_field"
        android:id="@+id/settings_search"
        android:layout_marginHorizontal="@dimen/screen_gutter"
        android:layout_marginTop="@dimen/space_8" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_settings"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingTop="@dimen/space_24"
        android:overScrollMode="ifContentScrolls" />
</LinearLayout>
```

- `rv_settings` bottom padding = navigation-bar inset + `nav_bar_height` 64 + `space_32` 32.
- The header hairline animates `alpha` 0 to 1 over `motion_state` 220 `ease_standard` when
  `recyclerView.canScrollVertically(-1)`, and back at 0. It is the only chrome change on scroll,
  ever (`00-rules.md` 4.8).
- No `AppBarLayout`, no `MaterialToolbar`, no `CollapsingToolbarLayout`. The current shell inflates
  a toolbar, applies an inset to it and then sets it `GONE` on every tab switch
  (`MainActivity.kt:441`); that dead chrome is removed.

### 8.2 Row component anatomy

Six layouts, one per archetype, each under 40 lines, each used by the adapter in 8.3. The two
orphaned components (`res/layout/layout_setting_row.xml`, `layout_setting_toggle_row.xml`) are
rewritten as A1/A2 and A3 and finally used.

**A1 / A2 / A5 / A6 - `res/layout/item_setting_row.xml`**

```
LinearLayout  (horizontal, gravity=center_vertical)
  android:minHeight            = @dimen/row_min_height        56dp
  android:paddingStart/End     = @dimen/screen_gutter         16dp
  android:paddingTop/Bottom    = @dimen/space_8                8dp
  android:background           = ?attr/selectableItemBackground
  android:stateListAnimator    = @anim/press_scale             0.97, 90 in / 160 out
  android:focusable            = true
  android:clickable            = true

  |- FrameLayout  @id/tile                       40 x 40   (@dimen/tile_size)
  |    android:background = @drawable/bg_tile_neutral        radius 12, color_tile_neutral
  |    `- ImageView @id/glyph                    22 x 22   (@dimen/tile_glyph)
  |         android:layout_gravity = center
  |         app:tint               = @color/icon_glyph_neutral
  |
  |- Space                                       12dp      (@dimen/space_12)
  |
  |- LinearLayout (vertical, weight=1)
  |    |- TextView @id/title
  |    |    android:textAppearance = @style/TextAppearance.App.Title      16sp / 700
  |    |    android:maxLines       = 2
  |    |    android:ellipsize      = end
  |    `- TextView @id/helper
  |         android:textAppearance = @style/TextAppearance.App.Subtitle   13sp / 400
  |         android:layout_marginTop = @dimen/space_4                      4dp
  |         android:maxLines       = 2
  |         android:visibility     = gone   when empty
  |
  |- Space                                       12dp
  |
  `- FrameLayout @id/trailing                    wrap, gravity=center_vertical
       A1: ImageView 20x20 @drawable/ic_chevron_right_20dp, tint ?attr/colorOnSurfaceVariant
       A2: TextView @style/TextAppearance.App.Subtitle, gravity=end,
           maxWidth = 40% of the row (measured in onMeasure, not a fixed dp)
       A5: nothing, or ImageView 20x20 @drawable/ic_open_external_20dp
       A6: nothing
```

Measured heights: one-line row = 8 + 40 + 8 = **56 dp** exactly (the 40 tile is the tallest child).
Two-line row = 8 + (20 title + 4 + 19 helper) + 8 = **59 dp**. Both satisfy `size_row` 56 as a
minimum, and neither is a token: they are computed.

A6 overrides three attributes and nothing else: `tile` background `?attr/iconTileBgRed`, `glyph`
tint `?attr/colorError`, `title` colour `?attr/pingBad` (`color_destructive_text` `#FF6069`, the
6.15:1 pair, not the 4.88:1 fill red).

**A3 - `res/layout/item_setting_toggle.xml`**

Identical to the above, with the trailing slot holding:

```
com.google.android.material.materialswitch.MaterialSwitch  @id/switch
  style              = @style/Widget.App.Switch      track 52x32, radius 16, thumb 26
  android:clickable  = false
  android:focusable  = false                          the ROW owns the tab stop
  android:duplicateParentState = true                 so the row's disabled state reaches it
```

The row's `OnClickListener` calls `switch.toggle()`. `switch.setOnCheckedChangeListener` performs
the write. Never both, or the write fires twice.

**A4 - `res/layout/item_setting_segment.xml`**

```
LinearLayout (vertical)
  android:paddingStart/End  = @dimen/screen_gutter    16dp
  android:paddingTop/Bottom = @dimen/space_12         12dp

  |- LinearLayout (horizontal, gravity=center_vertical, minHeight=40dp)
  |    |- FrameLayout @id/tile  40x40  (as above)
  |    |- Space 12dp
  |    `- LinearLayout (vertical, weight=1): @id/title, @id/helper
  |
  |- Space                                            8dp   (@dimen/space_8)
  |
  `- com.google.android.material.button.MaterialButtonToggleGroup @id/segment
       android:layout_width  = match_parent
       android:layout_height = @dimen/view_height_dp48        48dp
       android:background    = @drawable/bg_segment_track     radius 16, color_surface_inset
       android:padding       = @dimen/space_4                  4dp
       app:singleSelection   = true
       app:selectionRequired = true
       `- 2 or 3 x MaterialButton
            style                = @style/Widget.App.SegmentButton
            android:layout_weight= 1
            android:layout_height= 40dp
            app:cornerRadius     = @dimen/radius_chip          12dp
            android:insetTop/Bottom = 0dp
            unselected: transparent, ?attr/colorOnSurfaceVariant, textFontWeight 500
            selected:   ?attr/colorPrimary fill, ?attr/colorOnPrimary label, textFontWeight 700
```

Measured height = 12 + 40 + 8 + 48 + 12 = **120 dp**. Two rows in the whole product use it.
Concentricity holds: a 16 track with 4 padding holds a 12 thumb (`10-design-system.md` 2.6).

Degradation, implemented in `onMeasure` and re-evaluated on configuration change: if
`availableWidth / segmentCount < 96dp` **or** `resources.configuration.fontScale >= 1.3`, the
adapter binds A2 instead of A4 for that row. Same model entry, same strings, same binding.

**Divider.** Not a `View` inside the row. A `RecyclerView.ItemDecoration` draws a
`stroke_hairline` 1 dp line in `?attr/colorOutlineVariant` from x = `space_text_origin` **68 dp** to
the parent's right edge, between two rows of the same group only. Never above a group's first row,
never below its last, never under a section header. This is what finally kills the three divider
insets (44 / 68 / 72) the app ships today.

**Section header - `res/layout/item_setting_header.xml`**

```
TextView
  style                  = @style/SettingsSectionLabel     Space Grotesk 16sp / 700, sentence case
  android:paddingStart/End = @dimen/screen_gutter          16dp
  android:layout_marginTop = @dimen/space_24               24dp
  android:paddingBottom    = @dimen/space_8                 8dp
```

`SettingsSectionLabel` currently carries 16/16/18/8 padding; the 18 is off-scale and becomes the
24 top margin above.

### 8.3 The data-driven list

The hub is a list of one sealed model, not a hand-written layout. This is what takes
`layout_settings_content.xml` from 1 536 lines to 40 and makes search (section 7) possible at all.

```kotlin
sealed interface SettingsItem {
    val id: String                       // stable, e.g. "connection.mode" - the RecyclerView id
    data class Header(override val id: String, @StringRes val title: Int) : SettingsItem
    data class Navigation(
        override val id: String,
        @StringRes val title: Int,
        @StringRes val helper: Int? = null,
        @DrawableRes val glyph: Int,
        val route: SettingsRoute,
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
    data class Value(
        override val id: String,
        @StringRes val title: Int,
        @StringRes val helper: Int? = null,
        @DrawableRes val glyph: Int,
        val value: () -> CharSequence,   // read from MmkvManager at bind time
        val onTap: (View) -> Unit,       // opens a picker or a route
        val enabled: () -> Boolean = { true },
        @StringRes val disabledReason: Int? = null,
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
    data class Toggle(
        override val id: String,
        @StringRes val title: Int,
        @StringRes val helper: Int? = null,
        @DrawableRes val glyph: Int,
        val checked: () -> Boolean,
        val onChange: (Boolean) -> Unit,
        val enabled: () -> Boolean = { true },
        @StringRes val disabledReason: Int? = null,
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
    data class Segment(
        override val id: String,
        @StringRes val title: Int,
        val options: List<Int>,          // 2 or 3 @StringRes
        val selected: () -> Int,
        val onSelect: (Int) -> Unit,
        val helperFor: (Int) -> Int?,    // the dynamic helper of 4.2
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
    data class Action(
        override val id: String,
        @StringRes val title: Int,
        @StringRes val helper: Int? = null,
        @DrawableRes val glyph: Int,
        val external: Boolean = false,
        val perform: suspend () -> Result<Unit>,   // loading state is driven by this
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
    data class Destructive(
        override val id: String,
        @StringRes val title: Int,
        @StringRes val helper: Int? = null,
        @DrawableRes val glyph: Int,
        val confirm: ConfirmSpec?,       // null = undo instead of confirm
        val perform: suspend () -> Result<Unit>,
        val keywords: List<Int> = emptyList(),
    ) : SettingsItem
}
```

Requirements on the adapter:

- `ListAdapter` + `DiffUtil` + `setHasStableIds(true)` with `id.hashCode()`. No
  `notifyDataSetChanged()` on a visible list (`00-rules.md` 11.5).
- Conditional rows are expressed by rebuilding the list and submitting it; `DiffUtil` produces the
  insert or remove animation described in 3.2 for free.
- The same model builds every sub-page in section 5, so there is exactly one row implementation in
  the whole app.
- `keywords` are string resources, so search works in whatever locale is active.

### 8.4 The picker sheet

`BottomSheetDialogFragment`, one class, two contents. `PaymentMethodSheet.kt` is the reference
implementation for the chrome and it is already correct.

```
Body            ?attr/colorSurface, @drawable/bg_sheet_top (radius 24,24,0,0)
Handle          36 x 4, radius 2, ?attr/colorOutline, centred, 12 from the top
Title           TextAppearance.App.Title at the 16 gutter, 8 under the handle
Content         radio list, or one field block
Bottom padding  16 + navigation-bar inset
Scrim           ?attr/colorScrim at 60%
Enter / exit    motion_reveal 300 ease_out_quint / 225 ease_standard
Dismiss         drag, scrim tap, system Back; focus returns to the row that opened it
```

**Radio list content.** Up to 8 rows at `size_row` 56; beyond 8 it scrolls with the sheet at
`peekHeight = 60%` of the screen. Each row: no tile, title at the 68 origin (16 + 52 reserved, so
the text column does not move between a tiled row and an untiled one), and a 20 dp check glyph in
`color_accent` on the selected row plus `color_selected_fill` behind it. Two channels, per
`00-rules.md` 5.4. Selecting dismisses immediately and writes; there is no `OK` button.

**Field content.** One field block (label, 52 field at `radius_control` 16, helper), then a 52
`Сохранить` primary button at the gutter. Validation on blur and on submit. `Отмена` is the Back
gesture, not a second button.

### 8.5 Field blocks on sub-pages

```
TextView label        TextAppearance.App.Title.Medium     16sp / 500, colorOnSurface
Space                 @dimen/space_8                       8dp
TextInputLayout       style Widget.App.TextField
  boxCornerRadius     @dimen/radius_control               16dp
  boxBackgroundColor  ?attr/colorSurfaceContainerHighest  color_surface_inset
  boxStrokeWidth      @dimen/stroke_control                1dp,  ?attr/colorOutlineControl
  boxStrokeWidthFocused 2dp                                     ?attr/colorPrimary
  minHeight           @dimen/field_height                 52dp
  paddingHorizontal   @dimen/space_16                     16dp
Space                 @dimen/space_4                       4dp
TextView helper       TextAppearance.App.Caption          12sp, colorOnSurfaceVariant
                      or colorError text -> @color/ping_bad on error
```

The helper `TextView` is always present with `visibility="invisible"` when empty, never `gone`, so
an error does not shift the layout. `android:inputType` and `android:autofillHints` are set per
field: `textUri` for the DNS and test addresses, `number` for ports and MTU, `textPassword` with
`endIconMode="password_toggle"` for the SOCKS password.

### 8.6 Insets, Back, and configuration

- Edge to edge: the hub applies the system-bars **top** inset to the header and the **bottom**
  inset plus 64 plus 32 to the RecyclerView padding. Sub-pages apply the top inset to the toolbar
  and the bottom inset to their scroll container. The IME inset is applied to any page with a field
  (`settings/dns`, `settings/latency`, `settings/routing/rule`, `settings/data/webdav`,
  `settings/advanced/localproxy`, `settings/fragment`) - today **no** `ime()` inset exists anywhere
  in the app, so every settings field is currently coverable by the keyboard.
- Predictive Back: `android:enableOnBackInvokedCallback="true"` in the manifest (absent today), and
  every sub-page registers an `OnBackPressedCallback` that pops itself. The `onKeyDown` handler in
  `MainActivity.kt:2298` that unconditionally returns `true` for `KEYCODE_BACK` is removed; it makes
  the app never finish and breaks the predictive-Back animation.
- Rotation, font scale, theme change and split screen preserve: scroll offset, search query, the
  open picker and its selection, and every field's uncommitted text.
- `sw600dp`: gutter 24, content capped at `size_content_max` 720 and centred, bottom navigation
  becomes a `NavigationRailView`. Nothing else changes.

### 8.7 Resources this creates and deletes

**New dimens** (`res/values/dimens.xml`):
`space_text_origin` 68, `radius_control` 16, `stroke_hairline` 1, `stroke_control` 1,
`stroke_focus` 2, `field_height` 52, `cta_height` 52, `toolbar_height` 56, `nav_bar_height` 64,
`icon_button` 40, `glyph_16` 16, `glyph_20` 20, `content_max_width` 720, `meter_height` 6.

**New drawables:** `bg_tile_neutral` (12, `color_tile_neutral`), `bg_segment_track`
(16, `color_surface_inset`), `ic_chevron_right_20dp`, `ic_open_external_20dp`,
`ic_check_20dp`, plus one 22 dp outlined glyph per settings row, named `ic_set_<row>_24dp` and drawn
on the Material 24 keyline grid at 2 dp stroke.

**New styles:** `Widget.App.Switch`, `Widget.App.SegmentButton`, `Widget.App.TextField`,
`Widget.App.SearchField`.

**Deleted:** `activity_local_proxy.xml`, `activity_provider_settings.xml`,
`activity_url_scheme_list.xml`, `activity_backup.xml`, `dialog_webdav.xml`, `activity_settings.xml`,
`res/xml/pref_settings.xml`, `activity_about.xml`, `activity_user_asset_url.xml`,
`activity_app_picker.xml`, `activity_routing_edit.xml`, plus the orphans
`bg_icon_green/orange/purple/yellow` and the `iconTint*` / `iconTileBg*` attributes for green,
orange, purple and yellow (`03-direction.md` D-5: they all resolve to blue today, so the colour
names in the layouts are lies).

---

## 9. Desktop layout specification

`/home/user/v2rayN/v2rayN/v2rayN.Desktop`, C# + Avalonia 11 + ReactiveUI. Same design, different
mechanics. Every class name below either exists in `Assets/GlobalStyles.axaml` or is added there;
**no view hand-rolls a row**.

### 9.1 The hub view

`Views/SettingsView.axaml`, rebuilt:

```xml
<Grid RowDefinitions="Auto,Auto,*" Background="{DynamicResource Brush.Bg}">

  <!-- row 0: header 56, ground plane -->
  <Grid Grid.Row="0" Height="{StaticResource Size.SubToolbar}">      <!-- 56 -->
    <TextBlock Classes="Title" Margin="16,0,0,0" VerticalAlignment="Center"
               Text="{loc:T Nav_Settings}" />
    <Border x:Name="HeaderHairline" Height="1" VerticalAlignment="Bottom" Opacity="0"
            Background="{DynamicResource Brush.OutlineVariant}" />
  </Grid>

  <!-- row 1: search 48 -->
  <Border Grid.Row="1" Classes="SearchField" Margin="16,8,16,0" />

  <!-- row 2: the list -->
  <ScrollViewer Grid.Row="2" x:Name="SettingsScroll">
    <ItemsRepeater x:Name="SettingsList" MaxWidth="{StaticResource Size.ContentMax}"   <!-- 720 -->
                   Margin="0,24,0,32" HorizontalAlignment="Center" />
  </ScrollViewer>
</Grid>
```

Three changes from today that matter:

1. **`MaxWidth = 720`.** `SettingsView.axaml:216` is a bare `ScrollViewer` with none, so at the
   app's own 1120x760 preset the rows run about 1030 px edge to edge with a 40 tile hard left and a
   value hard right. That is unreadable and it is inconsistent with every other tab.
2. **No `Border.Card` per group.** The six `Border.Card ClipToBounds=True Padding=0 Margin="16,0,16,8"`
   wrappers are removed. Groups are a section header plus 24 of space plus hairlines. A settings
   screen is rows, not cards.
3. **`ItemsRepeater` over the shared model,** not 25 hand-written `Border.SettingRow` blocks. The
   model mirrors 8.3 one to one; the C# record set carries the same six cases with the same field
   names so the two platforms' settings trees can be diffed by eye.

`HeaderHairline` fades in over `Dur.State` 220 `Ease.Standard` when `SettingsScroll.Offset.Y > 0`.

### 9.2 Row classes

| Class | Base | Spec |
|---|---|---|
| `Border.SettingRow` | existing, corrected | `MinHeight` 56, `Padding="16,8"`, `CornerRadius` `Radius.Control` 16, `Background` transparent |
| `Border.SettingRow:pointerover` | new | `Brush.Hover` overlay across the **whole row**, 150 ms `Ease.Standard`. Never only the label |
| `Border.SettingRow:focus-visible` | corrected | 2 px `Brush.Accent` ring, 2 px offset, radius 18. Mandatory, never removed |
| `Border.SettingRow:pressed` | new | `RenderTransform` scale 0.97, `Dur.PressIn` 90 in / `Dur.PressOut` 160 out. No ripple |
| `Border.SettingRow.disabled` | new | `Opacity` 0.38, `IsHitTestVisible=False`, cursor default |
| `Border.SettingTile` | new | 40x40, `CornerRadius` `Radius.Tile` 12, `Brush.Tile.Neutral`, 22 glyph in `Brush.Tile.Glyph` |
| `Border.SettingTile.destructive` | new | `Brush.Tile.Red`, glyph `Brush.Red` |
| `Border.SettingDivider` | corrected | 1 px `Brush.OutlineVariant`, `Margin="68,0,0,0"` |
| `Border.SegmentTrack` | new | `Height` 48, `CornerRadius` 16, `Brush.SurfaceHighest`, `Padding` 4 |
| `ToggleButton.Segment` | existing, corrected | `Height` 40, `CornerRadius` 12, weight 500 unselected / 700 selected, `Brush.Accent` fill when selected |
| `ToggleSwitch.iOS` | existing | track 52x32, thumb 26. Removed from the tab order; the row owns the stop |
| `Border.SearchField` | renames `Border.SearchPill` | `Height` 48, `CornerRadius` `Radius.Control` 16 (not the retired `Radius.Search` 14), `Brush.SurfaceHighest` |

Every one of these must carry the three Semi suppressions that `02-inventory-pc.md` V9 documents
(about 25 selectors exist purely to stop `SemiTheme` re-tinting `PART_ContentPresenter` on
`:pointerover` and `:pressed`). The suppressions belong in one shared style block, applied by class,
not copied into each new component - that is the maintenance bug V9 is really describing.

Row anatomy, identical geometry to Android:

```
Grid ColumnDefinitions="40,12,*,12,Auto"  MinHeight=56  Padding="16,8"
  [0] Border.SettingTile          40x40, 22 glyph
  [2] StackPanel
        TextBlock Classes="Title"     16/700, MaxLines 2, TextTrimming CharacterEllipsis
        TextBlock Classes="Subtitle"  13/400, Margin="0,4,0,0", collapsed when empty
  [4] the one trailing element
```

### 9.3 The sub-page skeleton

All 17 sub-pages use one `UserControl` base, not nine copies of a local style. Today the eight
settings sub-pages and `LoginView` each redeclare a local `Button.IconButton:pressed` style
verbatim (V7) and use the legacy 32 px `Button.IconButton` class, which is below the 40 px minimum
for a toolbar control.

```xml
<DockPanel Background="{DynamicResource Brush.Bg}" MaxWidth="{StaticResource Size.ContentMax}">
  <Border DockPanel.Dock="Top" Classes="SubToolbar" Height="{StaticResource Size.SubToolbar}">
    <Grid ColumnDefinitions="48,16,*,Auto,16">
      <Button Grid.Column="0" Classes="BackNav" Width="48" Height="48"
              AutomationProperties.Name="{loc:T Common_Back}" />
      <TextBlock Grid.Column="2" Classes="Title" VerticalAlignment="Center" />
      <Button Grid.Column="3" Classes="IconButton40" IsVisible="False" />   <!-- 0 or 1 action -->
    </Grid>
  </Border>
  <ScrollViewer><StackPanel Margin="16,16,16,32" /></ScrollViewer>
</DockPanel>
```

- `Button.IconButton` (32 px, legacy) is **deleted** from `Assets/GlobalStyles.axaml:226`.
  `IconButton40` is the only icon button.
- Back is raised as `BackRequested` and handled by the shell. The shell additionally pops on
  **Esc** and on **mouse button 4** (`PointerPressed` with `XButton1`), which do not work today at
  all (V12).
- One trailing action maximum; extras go into a `MenuFlyout` on a single 40 overflow.

### 9.4 The picker flyout

`Flyout` with `IncyFlyoutTheme`, anchored to the row that opened it, `Placement="BottomEdgeAlignedRight"`.

```
Background     Brush.SurfaceHigh          P2, so it separates from the ground plane beneath
CornerRadius   Radius.Card 20
BorderBrush    Brush.OutlineVariant, 1
Padding        16
MinWidth       240,  MaxWidth 360,  MaxHeight 480 with an internal ScrollViewer
Shadow         the existing BoxShadow 0 12 32 0 #66000000 (a flyout is the one place a shadow is
               legal, because it floats over content and needs the separation)
```

Radio list rows are `Border.SettingRow` at 48 with no tile, the label at the 16 padding origin and a
20 check glyph in `Brush.Accent` plus `Brush.SelectedFill` on the selected one. Esc closes; focus
moves into the flyout on open and back to the row on close. Field content is one field block plus a
`Button.Primary` `Сохранить`.

**Never a modal `Window` for a settings choice.** The 15 upstream windows are the reason this rule
is written down: a 900x600 OS-decorated window with `resx` Chinese-origin strings is what the user
currently gets one click past several settings rows.

### 9.5 Field blocks

`ControlTheme TextBox.IncyField` already exists and is correct except for its radius. Corrected:

```
Label        TextBlock Classes="TitleMedium"   16/500, Brush.OnSurface
Spacing      8
TextBox      Theme=TextBox.IncyField
             Height 52, CornerRadius Radius.Control 16, Background Brush.SurfaceHighest
             BorderBrush Brush.OutlineControl 1 px,  :focus -> Brush.Accent 2 px
             Padding 16,0
Spacing      4
Helper       TextBlock Classes="Caption"       12, Brush.OnSurfaceVariant
             error -> Brush.RedText, and the TextBox border -> Brush.Red 1 px
```

The helper `TextBlock` keeps its slot when empty (`Opacity=0`, not `IsVisible=False`) so the layout
does not jump. Validation on `LostFocus`. Tab order follows visual order; every field is reachable
without a mouse, and `Enter` in the last field of a page commits the page's primary action.

### 9.6 Window sizes and scaling

- Usable at the 900x600 minimum: at that width the content column is 720 minus nothing, gutter 16,
  and at least six rows are visible below the header and the search field with no horizontal scroll.
- At width >= 1000 the gutter steps to 24. The content column never exceeds 720 and is centred
  inside the content area, not stretched to the window.
- `UiScaleState` at 100 / 110 / 125 / 150 percent, and OS DPI at 100 / 125 / 150 / 200 percent: no
  clipping, no truncated label, no overlapping control. The longest Russian strings to test with are
  `Разрешать небезопасные соединения`, `Сортировать по задержке после проверки` and
  `Проверять после обновления подписки`.
- One scroll region per view. No nested scrollers. The themed thin overlay scrollbar only.

### 9.7 What the desktop settings tree deletes

`OptionSettingWindow.axaml` (1 206 lines, 91 `resx:` refs, 1 Incy class),
`GlobalHotkeySettingWindow.axaml`, `FullConfigTemplateWindow.axaml`, `SubSettingWindow.axaml`,
`ThemeSettingView.axaml`, `BackupAndRestoreView.axaml`, the legacy `Button.IconButton` class, the
nine duplicated local `Button.IconButton:pressed` style blocks, `Radius.Search` 14,
`Radius.Traffic` 8, and the `Border.Card` wrappers around the settings groups.

`RoutingRuleSettingWindow.axaml` and `RoutingRuleDetailsWindow.axaml` are deleted **only** once
`settings/routing` and `settings/routing/rule/{id}` exist, because they are today the sole way to
edit a rule. Nothing is deleted before its replacement is reachable.

---

## 10. The states of the settings surface

`00-rules.md` 15: a screen is not done until every applicable state is designed, implemented and
looked at. For settings, "applicable" is narrower than for Главная, and that is exactly why the
states that do apply have to be right.

| State | Where it happens | Treatment |
|---|---|---|
| **Default** | everywhere | The tables in sections 4 and 5, with real values read at bind time, never with placeholder text |
| **First run** | the hub, before anything is configured | **No first-run state.** Settings are already correct on install because every row has a stated default. There is no empty settings screen, no tour, no "getting started" card |
| **Loading** | `settings/perapp` (app enumeration), `settings/assets` (file stat), `settings/about/log` | Skeleton rows of the final height, appearing only after 300 ms. Never a centred spinner. `settings/perapp`: 8 skeleton rows. `settings/assets`: 2 |
| **Empty** | `settings/routing` (no rule sets), `settings/assets` (no custom files), `settings/about/log` (no entries), `settings/perapp` search | Title + one line + one action, per `00-rules.md` 9.5. The exact strings are in 11.4 |
| **Error, per row** | any A2 or A3 whose write fails | The control reverts to the persisted value, the helper swaps to `color_destructive_text` with the cause, and the transient message carries «Повторить». The UI never shows a value the store does not hold |
| **Error, per page** | `settings/assets` update failure, `settings/data/webdav` connection failure | Inline, under the failing element, with the cause and the fix. `Не удалось обновить базы. Проверьте подключение и повторите.` |
| **Offline** | `settings/assets`, `settings/data/webdav`, `settings/providers` | The network-dependent actions are **disabled with a reason in their helper** (`Нет подключения к интернету`), the rest of the page works normally, and the shell's status strip carries `Нет сети. Показаны последние данные.` with «Повторить» (`11-app-structure.md` 8.2). No modal, no blocking |
| **Partial** | `settings/assets` when one of the two bases updated and the other failed | Show what you have: the succeeded row updates its subtitle, the failed row shows its error inline. Never a page-level failure for a per-row problem |
| **Long content** | every row | A 40-character setting value, a 60-character app name, a 70-character DoH URL. Titles wrap to 2 lines and never truncate; **values** ellipsise at the end at 40% of the row width; identifiers (package names, URLs) ellipsise at the end, never in the middle |
| **Short content** | `settings/routing` with one rule set, `settings/assets` with one custom file | The single row sits alone with its group header and no divider. It must not look broken |
| **Disabled / gated** | hub 3.1 with no subscriptions; `settings/perapp` list with the master toggle off; `settings/latency` `Одновременных проверок` when the method is TCP | 0.38 on the row **and a helper that states the reason**. A disabled control with no explanation is the defect this rule exists to prevent |
| **Success** | any write | The control changes over `dur_220` and that is all. No confirmation, no checkmark flourish. A transient message appears **only** when something happened that the row cannot show by itself: a reconnect, a regeneration, a file update |

### 10.1 The one message settings is allowed to send

`11-app-structure.md` 8.1 gives the transient channel (Android `Snackbar` above the bottom
navigation, desktop `Border.Toast` bottom centre). Settings uses it in exactly six situations:

| Trigger | Message | Action |
|---|---|---|
| A core-affecting setting changed **while connected** | `Переподключаемся, чтобы применить настройку` | none |
| A copy action | `Скопировано` | none |
| SOCKS credentials regenerated | `Логин и пароль обновлены` | none |
| Geo bases updated | `Базы обновлены` | none |
| Backup written | `Копия сохранена` | `Поделиться` (Android) / `Открыть папку` (desktop) |
| Any write failed | the cause, from `00-rules.md` 9.4 | `Повторить` |

A setting changed **while disconnected** sends nothing. The control moved; the user saw it.

---

## 11. The copy sheet

Every string, both platforms, one table per area. `00-rules.md` 9: Russian, sentence case, active
verbs, no final period on labels, no exclamation marks, hyphen only (no em dash, no en dash), `…` as
one character, «ёлочки» for quotes, `₽` never `RUB`.

**Android resource file:** `res/values/strings_settings.xml`, **in Russian**, with an English
override in `res/values-en/strings_settings.xml`. This inverts today's arrangement, where the
departament strings were written into the default (English) file and a non-Russian device shows
English chrome with Russian product copy mixed in (`01-inventory-android.md` 5.4). See 14, D-S9.

**Desktop file:** `Common/L.Settings.cs`, extending the existing `Settings_*` / `Dns_*` /
`Routing_*` / `PerApp_*` / `Ping_*` / `Geo_*` / `About_*` / `Backup_*` / `UrlSchemes_*` /
`Provider_*` namespace. Locale-neutral tokens are never keyed: TUN, DNS, IPv6, FakeIP, Mux, SOCKS5,
HTTP, TCP, HWID, User-Agent, MTU, `depv://`, `geoip.dat`, `geosite.dat`, Cloudflare, Google,
AdGuard, sing-box, Xray, and the language endonyms.

### 11.1 The hub

| Android key | Desktop key | Russian |
|---|---|---|
| `set_search_hint` | `Settings_SearchHint` | `Поиск по настройкам` |
| `set_sec_connection` | `Settings_SecConnection` | `Подключение` |
| `set_mode` | `Settings_Mode` | `Режим подключения` |
| `set_mode_vpn` | `Settings_ModeVpn` | `VPN` |
| `set_mode_proxy` | `Settings_ModeProxy` | `Прокси` |
| `set_mode_both` | `Settings_ModeBoth` | `Вместе` |
| `set_mode_both_hint` | `Settings_ModeBothHint` | `Прокси доступен другим устройствам в сети` |
| `set_perapp` | `Settings_PerApp` | `Прокси по приложениям` |
| `set_perapp_off` | `Settings_PerAppOff` | `Выкл` |
| `set_perapp_except` | `Settings_PerAppExcept` | `Кроме %1$d` |
| `set_perapp_only` | `Settings_PerAppOnly` | `Только %1$d` |
| `set_perapp_none` | `Settings_PerAppNone` | `Не выбрано` |
| `set_routing` | `Settings_Routing` | `Маршрутизация` |
| `set_dns` | `Settings_Dns` | `DNS` |
| `set_bypass_lan` | `Settings_BypassLan` | `Обход локальной сети` |
| `set_bypass_lan_hint` | `Settings_BypassLanHint` | `Прямой доступ к устройствам в локальной сети` |
| `set_ipv6` | `Settings_Ipv6` | `IPv6` |
| `set_ipv6_hint` | `Settings_Ipv6Hint` | `Включить IPv6 в туннеле` |
| `set_advanced` | `Settings_Advanced` | `Дополнительно` |
| `set_sec_bypass` | `Settings_SecBypass` | `Обход блокировок` |
| `set_mux` | `Settings_Mux` | `Мультиплексирование (Mux)` |
| `set_mux_hint` | `Settings_MuxHint` | `Объединяет запросы в один канал` |
| `set_mux_count` | `Settings_MuxCount` | `Число соединений Mux` |
| `set_fragment` | `Settings_Fragment` | `Фрагментация пакетов` |
| `set_fragment_hint` | `Settings_FragmentHint` | `Разбивает TLS-рукопожатие против DPI` |
| `set_fragment_params` | `Settings_FragmentParams` | `Параметры фрагментации` |
| `set_sec_subs` | `Settings_SecSubs` | `Подписки` |
| `set_sub_auto_update` | `Settings_SubAutoUpdate` | `Автообновление подписок` |
| `set_sub_auto_off` | `Settings_SubAutoOff` | `Выключено` |
| `set_sub_auto_1h` | `Settings_SubAuto1h` | `Каждый час` |
| `set_sub_auto_6h` | `Settings_SubAuto6h` | `Каждые 6 часов` |
| `set_sub_auto_12h` | `Settings_SubAuto12h` | `Каждые 12 часов` |
| `set_sub_auto_24h` | `Settings_SubAuto24h` | `Раз в сутки` |
| `set_sub_auto_empty` | `Settings_SubAutoEmpty` | `Нет подписок` |
| `set_sub_auto_empty_hint` | `Settings_SubAutoEmptyHint` | `Добавьте провайдера, чтобы включить` |
| `set_sub_update_launch` | `Settings_SubUpdateLaunch` | `Обновлять при запуске` |
| `set_latency` | `Settings_Latency` | `Проверка задержки` |
| `set_providers` | `Settings_Providers` | `Провайдеры` |
| `set_assets` | `Settings_GeoFiles` | `Файлы ресурсов` |
| `set_sec_app` | `Settings_SecApp` | `Приложение` |
| `set_appearance` | `Settings_Appearance` | `Оформление` |
| `set_theme_dark` | `Settings_ThemeDark` | `Тёмная` |
| `set_theme_light` | `Settings_ThemeLight` | `Светлая` |
| `set_theme_system` | `Settings_ThemeSystem` | `Системная` |
| `set_black_theme` | `Settings_BlackTheme` | `Чёрная тема` |
| `set_black_theme_hint` | `Settings_BlackThemeHint` | `Чистый чёрный фон без цветного акцента` |
| `set_language` | `Settings_Language` | `Язык` |
| `set_language_system` | `Settings_LanguageSystem` | `Системный` |
| `set_reduced_motion` | `Settings_ReducedMotion` | `Меньше движения` |
| `set_reduced_motion_hint` | `Settings_ReducedMotionHint` | `Отключает анимации` |
| `set_boot` | `Settings_Autostart` | `Запуск при старте` |
| `set_boot_hint` | `Settings_AutostartHint` | `Открывать departament при входе в систему` |
| `set_ui_scale` | `Settings_UiScale` | `Масштаб интерфейса` |
| `set_window` | `Settings_Window` | `Окно и горячие клавиши` |
| `set_data` | `Settings_Data` | `Данные и резервные копии` |
| `set_about` | `Settings_About` | `О приложении` |
| `set_about_version` | `About_VersionValue` | `Версия %1$s` |

`set_boot_hint` replaces today's `settings_boot_sub` («Подключаться после перезагрузки устройства»),
which promises a connection the boot receiver does not make.

### 11.2 Sub-page titles and section headers

| Android key | Desktop key | Russian |
|---|---|---|
| `set_perapp_title` | `PerApp_Title` | `Прокси по приложениям` |
| `set_perapp_sec_mode` | `PerApp_SecMode` | `Режим` |
| `set_perapp_split` | `PerApp_SplitTunnel` | `Раздельное туннелирование` |
| `set_perapp_split_hint` | `PerApp_SplitTunnelHint` | `Выберите, какие приложения идут через VPN` |
| `set_perapp_rule` | `PerApp_Rule` | `Правило` |
| `set_perapp_rule_except` | `PerApp_RuleExcept` | `Кроме выбранных` |
| `set_perapp_rule_only` | `PerApp_RuleOnly` | `Только выбранные` |
| `set_perapp_hint_except` | `PerApp_BypassHint` | `Выбранные идут напрямую, мимо VPN` |
| `set_perapp_hint_only` | `PerApp_OnlyHint` | `Через VPN идут только выбранные` |
| `set_perapp_sec_apps` | `PerApp_Apps` | `Приложения` |
| `set_perapp_search` | `PerApp_Search` | `Поиск по приложениям` |
| `set_perapp_apply_hint` | `PerApp_TunHint` | `Изменения применятся при следующем подключении` |
| `set_routing_intro` | `Routing_Intro` | `Наборы правил решают, какой трафик идёт через VPN, а какой напрямую.` |
| `set_routing_sets` | `Routing_RuleSets` | `Наборы правил` |
| `set_routing_rules_n` | `Routing_RulesCount` | `%1$d правил` |
| `set_routing_add` | `Routing_Add` | `Добавить набор` |
| `set_routing_import` | `Routing_Import` | `Импортировать набор` |
| `set_routing_import_clip` | `Routing_ImportClipboard` | `Из буфера обмена` |
| `set_routing_import_qr` | `Routing_ImportQr` | `Из QR-кода` |
| `set_routing_import_preset` | `Routing_ImportPreset` | `Стандартные наборы` |
| `set_routing_sec_domains` | `Routing_SecDomains` | `Домены` |
| `set_routing_strategy` | `Routing_DomainStrategy` | `Стратегия доменов` |
| `set_routing_resolve` | `Routing_DomainResolution` | `Разрешение доменов` |
| `set_routing_resolve_hint` | `Routing_DomainHint` | `Как ядро сопоставляет домены с правилами` |
| `set_routing_sec_maint` | `Routing_Maintenance` | `Обслуживание` |
| `set_routing_restore` | `Routing_RestoreDefaults` | `Восстановить стандартные наборы` |
| `set_routing_reset` | `Routing_Reset` | `Сбросить правила` |
| `set_routing_reset_hint` | `Routing_ResetHint` | `Удалит все наборы, включая свои` |
| `set_dns_intro` | `Dns_Intro` | `DNS-сервер, через который приложение разрешает домены. По умолчанию используется встроенный резолвер.` |
| `set_dns_provider` | `Dns_Provider` | `Провайдер` |
| `set_dns_default` | `Dns_Default` | `По умолчанию` |
| `set_dns_custom` | `Dns_Custom` | `Свой` |
| `set_dns_custom_label` | `Dns_CustomAddress` | `Адрес DNS-сервера` |
| `set_dns_custom_hint` | `Dns_CustomHint` | `DoH-адрес (https://…/dns-query), DoT или обычный IP` |
| `set_dns_sec_advanced` | `Dns_Advanced` | `Дополнительно` |
| `set_dns_fakeip_hint` | `Dns_FakeIpHint` | `Ускоряет соединение, отвечая на запросы локально` |
| `set_dns_local` | `Dns_LocalResolver` | `Локальный резолвер` |
| `set_dns_local_hint` | `Dns_LocalResolverHint` | `Разрешать домены внутри приложения` |
| `set_dns_direct` | `Dns_Direct` | `DNS для прямых соединений` |
| `set_dns_sec_hosts` | `Dns_SecHosts` | `Свои записи` |
| `set_dns_hosts` | `Dns_Hosts` | `Записи hosts` |
| `set_dns_hosts_hint` | `Dns_HostsHint` | `Одна запись в строке: домен и адрес` |
| `set_fragment_title` | `Fragment_Title` | `Параметры фрагментации` |
| `set_fragment_length` | `Fragment_Length` | `Длина` |
| `set_fragment_length_hint` | `Fragment_LengthHint` | `Диапазон в байтах, например 50-100` |
| `set_fragment_interval` | `Fragment_Interval` | `Интервал` |
| `set_fragment_interval_hint` | `Fragment_IntervalHint` | `Пауза между частями, мс` |
| `set_fragment_packets` | `Fragment_Packets` | `Пакеты` |
| `set_fragment_note` | `Fragment_Note` | `Значения по умолчанию подходят большинству сетей. Меняйте их, только если соединение не устанавливается.` |
| `set_latency_title` | `Ping_Title` | `Проверка задержки` |
| `set_latency_intro` | `Ping_Intro` | `Как измерять задержку серверов.` |
| `set_latency_sec_method` | `Ping_SecMethod` | `Метод` |
| `set_latency_real` | `Ping_RealTitle` | `Реальная задержка` |
| `set_latency_real_hint` | `Ping_RealHint` | `Через ядро, как при подключении` |
| `set_latency_tcp` | `Ping_TcpTitle` | `TCP-подключение` |
| `set_latency_tcp_hint` | `Ping_TcpHint` | `Быстрее, но менее точно` |
| `set_latency_sec_test` | `Ping_SecTest` | `Проверка` |
| `set_latency_url` | `Ping_TestAddress` | `Адрес проверки` |
| `set_latency_timeout` | `Ping_Timeout` | `Тайм-аут` |
| `set_latency_concurrency` | `Ping_Concurrency` | `Одновременных проверок` |
| `set_latency_sec_auto` | `Ping_SecAuto` | `Автоматически` |
| `set_latency_on_launch` | `Ping_OnLaunch` | `Проверять при запуске` |
| `set_latency_on_update` | `Ping_OnUpdate` | `Проверять после обновления подписки` |
| `set_latency_sort` | `Ping_SortAfter` | `Сортировать по задержке после проверки` |
| `set_latency_remove` | `Ping_RemoveAfter` | `Удалять нерабочие после проверки` |
| `set_latency_remove_hint` | `Ping_RemoveAfterHint` | `Серверы без ответа будут удалены` |
| `set_providers_title` | `Provider_Title` | `Провайдеры` |
| `set_providers_notify` | `Provider_Notify` | `Уведомлять об обновлении` |
| `set_providers_sec_net` | `Provider_SecNetwork` | `Сеть` |
| `set_providers_hwid` | `Provider_Hwid` | `Отправлять идентификатор устройства` |
| `set_providers_hwid_hint` | `Provider_HwidHint` | `Нужен, чтобы считать устройства в тарифе` |
| `set_providers_ua_hint` | `Provider_UserAgentHint` | `Отправляется при обновлении подписки` |
| `set_providers_ua_default` | `Provider_UserAgentDefault` | `Значение по умолчанию` |
| `set_providers_sec_list` | `Provider_SecList` | `Список серверов` |
| `set_providers_sort` | `Provider_Sort` | `Порядок серверов` |
| `set_providers_sort_default` | `Provider_SortDefault` | `Как у провайдера` |
| `set_providers_sort_ping` | `Provider_SortPing` | `По задержке` |
| `set_providers_sort_name` | `Provider_SortName` | `По имени` |
| `set_assets_intro` | `Geo_Intro` | `Базы geoip и geosite нужны для маршрутизации по странам и доменам.` |
| `set_assets_sec_bases` | `Geo_SecBases` | `Базы` |
| `set_assets_update` | `Geo_UpdateNow` | `Обновить сейчас` |
| `set_assets_updating` | `Geo_Updating` | `Обновление…` |
| `set_assets_not_loaded` | `Geo_NotDownloaded` | `Не загружен` |
| `set_assets_meta` | `Geo_SizeUpdated` | `%1$s МБ · обновлён %2$s` |
| `set_assets_source` | `Geo_Source` | `Источник обновлений` |
| `set_assets_sec_custom` | `Geo_SecCustom` | `Свои файлы` |
| `set_assets_add` | `Geo_Add` | `Добавить файл` |
| `set_assets_add_file` | `Geo_AddFile` | `Из файла` |
| `set_assets_add_url` | `Geo_AddUrl` | `По ссылке` |
| `set_assets_add_qr` | `Geo_AddQr` | `Из QR-кода` |
| `set_adv_title` | `Adv_Title` | `Дополнительно` |
| `set_adv_sec_core` | `Adv_SecCore` | `Ядро` |
| `set_adv_core` | `Adv_Core` | `Ядро` |
| `set_adv_loglevel` | `Adv_LogLevel` | `Уровень журнала` |
| `set_adv_loglevel_none` | `Adv_LogNone` | `Никакой` |
| `set_adv_loglevel_error` | `Adv_LogError` | `Ошибки` |
| `set_adv_loglevel_warn` | `Adv_LogWarn` | `Предупреждения` |
| `set_adv_loglevel_info` | `Adv_LogInfo` | `Информация` |
| `set_adv_loglevel_debug` | `Adv_LogDebug` | `Отладка` |
| `set_adv_loglevel_debug_note` | `Adv_LogDebugNote` | `Отладочный журнал заметно нагружает устройство` |
| `set_adv_sniffing` | `Adv_Sniffing` | `Определение домена в трафике` |
| `set_adv_sniffing_hint` | `Adv_SniffingHint` | `Помогает правилам маршрутизации` |
| `set_adv_route_only` | `Adv_RouteOnly` | `Только для маршрутизации` |
| `set_adv_route_only_hint` | `Adv_RouteOnlyHint` | `Не подменять адрес назначения` |
| `set_adv_insecure` | `Adv_AllowInsecure` | `Разрешать небезопасные соединения` |
| `set_adv_insecure_hint` | `Adv_AllowInsecureHint` | `Отключает проверку сертификата сервера` |
| `set_adv_fallback` | `Adv_AutoFallback` | `Переключать сервер при сбое` |
| `set_adv_fallback_hint` | `Adv_AutoFallbackHint` | `Если сервер не отвечает после подключения` |
| `set_adv_sec_tunnel` | `Adv_SecTunnel` | `Туннель` |
| `set_adv_mtu_hint` | `Adv_MtuHint` | `От 576 до 9000. По умолчанию 1500` |
| `set_adv_iface` | `Adv_InterfaceAddress` | `Адрес интерфейса` |
| `set_adv_always_on` | - | `Постоянный VPN` |
| `set_adv_always_on_hint` | - | `Настраивается в системных настройках Android` |
| - | `Adv_SecConfig` | `Конфигурация` |
| - | `Adv_Template` | `Шаблон конфигурации` |
| - | `Adv_TemplateHint` | `Свой JSON поверх сгенерированного` |
| `set_lp_title` | `Lp_Title` | `Локальный прокси` |
| `set_lp_enabled` | `Lp_Enabled` | `Локальный прокси` |
| `set_lp_enabled_hint` | `Lp_EnabledHint` | `SOCKS5 и HTTP на 127.0.0.1` |
| `set_lp_port` | `Settings_Port` | `Порт` |
| `set_lp_udp` | `Lp_Udp` | `UDP через прокси` |
| `set_lp_http` | `Lp_Http` | `HTTP-прокси на соседнем порту` |
| `set_lp_lan` | `Lp_Lan` | `Доступ из локальной сети` |
| `set_lp_lan_hint` | `Lp_LanHint` | `Другие устройства смогут пользоваться прокси` |
| `set_lp_sec_auth` | `Settings_Socks5Auth` | `SOCKS5-авторизация` |
| `set_lp_user` | `Settings_Username` | `Логин` |
| `set_lp_pass` | `Lp_Password` | `Пароль` |
| `set_lp_regen` | `Lp_Regenerate` | `Создать новые логин и пароль` |
| `set_lp_sec_endpoint` | `Lp_SecEndpoint` | `Адрес подключения` |
| `set_lp_endpoint_hint` | `Lp_EndpointHint` | `Адрес действует, пока устройство в этой сети` |
| `set_lp_no_lan` | `Lp_NoLan` | `Устройство не подключено к локальной сети` |
| `set_data_intro` | `Backup_Intro` | `Все настройки, подписки и серверы сохраняются в один файл.` |
| `set_data_sec_backup` | `Backup_SecBackup` | `Резервная копия` |
| `set_data_create` | `Backup_Create` | `Создать копию` |
| `set_data_restore` | `Backup_Restore` | `Восстановить из копии` |
| `set_data_restore_hint` | `Backup_RestoreHint` | `Приложение перезапустится` |
| `set_data_share` | `Backup_Share` | `Поделиться копией` |
| `set_data_cloud` | `Backup_Cloud` | `Облачная копия` |
| `set_data_sec_devices` | - | `Устройства` |
| `set_data_tv` | - | `Перенести подписку на ТВ` |
| `set_data_sec_reset` | `Backup_SecReset` | `Сброс` |
| `set_data_reset` | `Backup_Reset` | `Сбросить настройки` |
| `set_data_reset_hint` | `Backup_ResetHint` | `Серверы и подписки останутся` |
| `set_webdav_title` | `Webdav_Title` | `Облачная копия` |
| `set_webdav_url` | `Webdav_Url` | `Адрес сервера` |
| `set_webdav_folder` | `Webdav_Folder` | `Папка` |
| `set_webdav_folder_hint` | `Webdav_FolderHint` | `Создаётся автоматически, если её нет` |
| `set_webdav_test` | `Webdav_Test` | `Проверить подключение` |
| `set_webdav_upload` | `Webdav_Upload` | `Выгрузить копию` |
| `set_webdav_download` | `Webdav_Download` | `Загрузить копию` |
| `set_window_title` | `Window_Title` | `Окно и горячие клавиши` |
| - | `Window_SecWindow` | `Окно` |
| - | `Window_HideToTray` | `Сворачивать в трей при закрытии` |
| - | `Window_StartMinimized` | `Запускать свёрнутым` |
| - | `Window_ShowInDock` | `Показывать в Dock` |
| - | `Window_SecHotkeys` | `Горячие клавиши` |
| - | `Window_HkShow` | `Показать окно` |
| - | `Window_HkToggle` | `Подключить или отключить` |
| - | `Window_HkSwitch` | `Сменить сервер` |
| - | `Window_HkUpdate` | `Обновить подписки` |
| - | `Window_HkUnset` | `Не назначено` |
| - | `Window_HkCapture` | `Нажмите сочетание…` |
| - | `Window_HkConflict` | `Уже назначено: %1$s` |
| - | `Window_HkReset` | `Сбросить сочетания` |
| `set_about_sec_support` | `About_SecSupport` | `Поддержка` |
| `set_about_site` | `About_Site` | `Сайт departament.site` |
| `set_about_bot` | `About_TelegramBot` | `Telegram-бот` |
| - | `About_CheckUpdate` | `Проверить обновления` |
| `set_about_sec_dev` | `About_SecDev` | `Для разработчика` |
| `set_about_schemes` | `Settings_UrlSchemes` | `Схемы URL-адресов` |
| `set_about_log` | `About_Log` | `Журнал` |
| `set_about_sec_legal` | `About_SecLegal` | `Правовое` |
| `set_about_privacy` | `About_Privacy` | `Политика конфиденциальности` |
| `set_about_licenses` | `About_Licenses` | `Лицензии открытого кода` |
| `set_about_copy_info` | `About_CopyDetails` | `Скопировать сведения об устройстве` |
| `set_schemes_hint` | `UrlSchemes_Hint` | `Нажмите на схему, чтобы скопировать.` |
| `set_log_title` | `Log_Title` | `Журнал` |
| `set_log_all` | `Log_All` | `Все` |
| `set_log_errors` | `Log_Errors` | `Ошибки` |
| `set_log_copy` | `Log_CopyAll` | `Скопировать всё` |
| `set_log_clear` | `Log_Clear` | `Очистить` |

### 11.3 Confirms

Both dialogs, in full. `00-rules.md` 7.5: destructive confirms use a red text button on the right,
a neutral cancel on the left, and the button says what it does.

| | Сбросить правила | Сбросить настройки |
|---|---|---|
| Title | `Сбросить правила маршрутизации?` | `Сбросить настройки?` |
| Body | `Все наборы, включая созданные вами, будут удалены. Действие нельзя отменить.` | `Все настройки вернутся к значениям по умолчанию. Серверы, подписки и аккаунт не пострадают.` |
| Left | `Отмена` | `Отмена` |
| Right | `Сбросить` in `color_destructive_text` | `Сбросить` in `color_destructive_text` |

### 11.4 Empty states

Formula from `00-rules.md` 9.5: title, one line, one action.

| Screen | Title | Line | Action |
|---|---|---|---|
| Settings search, no match | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| `settings/perapp` search, no match | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| `settings/routing`, no sets | `Наборов правил пока нет` | `Добавьте набор или восстановите стандартные.` | `Добавить набор` |
| `settings/assets`, no custom files | `Своих файлов нет` | `Добавьте файл, если провайдер прислал свою базу.` | `Добавить файл` |
| `settings/about/log`, empty | `Журнал пуст` | `Здесь появятся события приложения.` | none |

### 11.5 Errors

Formula from `00-rules.md` 9.4: what happened, why, what to do. Every one ships with a recovery
affordance.

| Situation | String |
|---|---|
| Geo bases failed to update | `Не удалось обновить базы. Проверьте подключение и повторите.` |
| WebDAV connection failed | `Не удалось подключиться. Проверьте адрес, логин и пароль.` |
| WebDAV address has no scheme | `Адрес должен начинаться с https://` |
| DNS address invalid | `Проверьте адрес DNS-сервера` |
| Port out of range | `Введите порт от 1 до 65535` |
| MTU out of range | `Введите значение от 576 до 9000` |
| Backup could not be written | `Не удалось сохранить копию. Проверьте свободное место и повторите.` |
| Backup could not be read | `Не удалось прочитать копию. Файл повреждён или создан другой версией.` |
| Hotkey already used | `Уже назначено: %1$s` |
| A setting failed to persist | `Не удалось сохранить настройку. Повторите.` |
| Anything else | `Что-то пошло не так. Повторите попытку.` (last resort only; the real cause goes to the log) |

### 11.6 Copy rules this surface breaks most often

1. **Do not restate the title in the helper.** `Мультиплексирование (Mux)` /
   `Включает мультиплексирование` is noise. Say the consequence or say nothing.
2. **Do not name the mechanism when the user thinks in outcomes.** The group is
   `Обход блокировок`, not `Транспорт`.
3. **No dash characters.** `50-100` uses a hyphen, and it is a range, not a pause. Where a dash was
   carrying a pause in the old copy, use a comma or a full stop.
4. **`…` is one character**, used in `Обновление…` and `Нажмите сочетание…` only.
5. **Figures are Russian.** `4,2 МБ`, `48 мс`, `1 290 ₽`, thin space as the thousands separator,
   comma as the decimal separator, non-breaking space before `₽`.
6. **No exclamation marks anywhere in this tree, and no reassurance copy.** The user is configuring
   an instrument.

---

## 12. Parity contract and the logged gaps

### 12.1 Identical by contract

Both platforms ship the same group order, the same group names, the same row order inside each
group, the same Russian label for the same concept, the same default for every shared setting, the
same archetype for every shared row, the same picker option sets, the same empty and error strings,
the same route names, and the same 68 text origin at the same 56 row height.

A change to any of those is a change to both platforms in the same commit. A row that exists on one
platform and not the other is either in 12.2 or it is a bug.

### 12.2 Logged parity gaps

| ID | Gap | Platform | Reason it is allowed |
|---|---|---|---|
| **PG-S1** | `Масштаб интерфейса` (hub 4.6) | desktop only | The OS font scale is Android's answer and the layouts must survive 200% of it. An in-app zoom on top of it would compound |
| **PG-S2** | `Окно и горячие клавиши` (`settings/window`) | desktop only | There is no window and no global hotkey on Android |
| **PG-S3** | `Шаблон конфигурации` (`settings/advanced/template`) and `Ядро` (5.9 row a) | desktop only | Android ships one core; the desktop ships two and a JSON editor |
| **PG-S4** | The device identifier shown as a read-only copyable field on `settings/providers` | desktop only | Support asks desktop users to paste it; on a phone there is nowhere to paste it. The `Отправлять идентификатор устройства` toggle itself exists on both |
| **PG-S5** | `Перенести подписку на ТВ` (`settings/tv`) and `Постоянный VPN` (5.9) and `Локальный прокси` master toggle (5.10 row a) | Android only | Android TV pairing, an Android system feature, and an inbound that desktop always has |
| **PG-S6** | `Поделиться копией` (5.11) | Android only | The system share sheet has no desktop equivalent; the desktop offers `Открыть папку` on the success message instead |
| **PG-S7** | `Проверить обновления` (5.15) | desktop only | Android is not distributed through GitHub releases (`11-app-structure.md` PG-1) |
| **PG-S8** | `Локальный резолвер` (5.4) | Android only | On desktop the resolver is implicit in sing-box and has no separate flag |

Eight gaps, all of them a genuine platform capability difference, none of them a design difference.

---

## 13. Acceptance checklist

Mechanical where it can be. A box that cannot be honestly ticked means the settings work is not
done. This is `00-rules.md` 16 narrowed to this surface.

**Structure**
- [ ] 4 named groups in the hub, plus the unnamed footer pair. No fifth group
- [ ] No group exceeds 7 rows on either platform
- [ ] No route below level 2. `settings/routing/rule/{id}`, `settings/advanced/localproxy`,
      `settings/about/log`, `settings/data/webdav`, `settings/tv` are the level-2 routes and there
      are no others
- [ ] Every setting has exactly one home. Grep for a preference key written from two files
- [ ] Every route in 5.0 is reachable from the hub in at most 2 taps
- [ ] Nothing in `res/xml/pref_settings.xml` or `OptionSettingWindow.axaml` remains reachable, and
      both files are deleted

**Components**
- [ ] Every row is one of the six archetypes. No seventh
- [ ] Exactly one trailing element per row
- [ ] Every row is 56 minimum, with its title at the 68 origin, on both platforms
- [ ] Every hairline starts at 68 and none runs under a tile, above a group's first row, below its
      last, or under a section header
- [ ] **Zero cards in the settings tree.** Grep `MaterialCardView` in the settings layouts and
      `Border.Card` in the settings views: both must return nothing
- [ ] Section headers are `SettingsSectionLabel` / `TextBlock.SectionHeader`, sentence case, 16/700,
      24 above and 8 below
- [ ] Every icon tile is neutral. No coloured tile anywhere in settings except the destructive one

**Colour and type**
- [ ] The hub screenshot contains accent pixels only in: a selected segment, a switch that is on,
      and the focus ring. Nothing else
- [ ] Exactly two filled accent buttons exist in the whole tree (`settings/assets` «Обновить
      сейчас», `settings/data/webdav` primary), and never two on one screen
- [ ] Every text element uses a ramp style. Grep `android:textSize` in the settings layouts and
      `FontSize=` in the settings views: both must return nothing
- [ ] Every figure (version, sizes, ports, MTU, timeouts, counts, hotkeys) is in the Numeric role
      with `tnum`
- [ ] Dark, light, mono dark and mono light all checked on the hub and on three sub-pages

**Interaction**
- [ ] Every row has default, pressed, disabled; plus hover and focus on desktop; plus focus on
      Android for keyboard and TV
- [ ] Every disabled row states why in its helper
- [ ] Row and switch both toggle an A3 row, and the write fires once, not twice
- [ ] A picker selection dismisses immediately and writes; there is no `OK` button on a radio list
- [ ] Zero single-choice `AlertDialog`s remain. Grep `setSingleChoiceItems` in
      `app/src/main/java/`: it must return nothing
- [ ] Exactly two confirm dialogs exist in the tree, both irreversible
- [ ] Esc and mouse button 4 pop a desktop sub-page; predictive Back pops an Android one
- [ ] Back restores the search query, the scroll offset and the open group state
- [ ] Every field validates on blur, shows its error below, and keeps its helper slot when empty
- [ ] The IME does not cover any field on Android

**Motion**
- [ ] Conditional rows insert and remove over `dur_reveal` 300 in, 225 out, and snap under reduced
      motion
- [ ] The header hairline is the only thing that changes on scroll
- [ ] No settings row animates a layout property for effect
- [ ] `Меньше движения` toggled on visibly stops the above, live, without a restart, on both
      platforms

**Copy**
- [ ] Every string Russian, sentence case, no final period on labels
- [ ] Zero em dashes and en dashes: `grep -rn -e '—' -e '–' res/values*/strings_settings.xml` and
      `Common/L.Settings.cs` return nothing
- [ ] No helper restates its title
- [ ] Every toggle is named for the state that is true when it is on
- [ ] Every error names a cause and a fix and carries a recovery action

**Parity**
- [ ] Put the two hubs side by side at the same scale. Group order, group names, row order, row
      labels, values and defaults match, row for row, except for the eight gaps in 12.2
- [ ] The default of every shared setting is identical. Check by installing both fresh
- [ ] Every picker offers the same options in the same order with the same labels

**Adaptivity**
- [ ] Android at 320 dp width and font scale 200%: no clipping, no truncated label, and the segment
      rows have degraded to value rows
- [ ] Android `sw600dp`: gutter 24, content capped at 720, rail instead of the bottom bar
- [ ] Desktop at 900x600: at least six rows visible, no horizontal scroll
- [ ] Desktop at 1920 wide: the content column is 720 and centred, not stretched

**Verification**
- [ ] Every row was tapped, on both platforms, and the value it claims to write was read back from
      storage
- [ ] The seven questions of the Departament slop test (`00-rules.md` 2.4) answered for the hub and
      for three sub-pages

---

## 14. Decisions this document takes

### 14.1 Taken here, inside existing law

- **D-S1. The six archetypes of section 3 are the whole settings vocabulary.** A settings row that
  is not one of them is a defect on both platforms.
- **D-S2. Cycle-in-place is retired.** The desktop's `unfold_more` affordance
  (`SettingsView.axaml.cs`, four rows) advances a value blindly: the user cannot see the option set,
  cannot jump to an option, and must tap n-1 times to reach the last one. It is replaced by the A2
  Value archetype plus a picker. The rest of the affordance-honesty contract in that file's header
  comment is kept verbatim and extended to Android.
- **D-S3. The inline-expand archetype (rotating chevron) is retired.** After `Локальный прокси`
  moves to `settings/advanced/localproxy`, no row in either product uses it. An affordance with no
  users is vocabulary the user has to learn for nothing.
- **D-S4. A settings screen contains no cards.** The desktop's six `Border.Card` group wrappers are
  removed. This is `00-rules.md` 4.4 applied rather than reinterpreted.
- **D-S5. One label per concept across both platforms**, replacing: `Монохром` / `Чёрно-белый
  режим` -> `Чёрная тема`; `Обход локальной сети` keeps its name but changes its desktop binding
  (6.4 B1); `Блокировать UDP` -> `UDP через прокси` (6.4 B2); `Пинг` -> `Проверка задержки`;
  `Настройки провайдеров` -> `Провайдеры`; `Запуск при загрузке` -> `Запуск при старте`.
- **D-S6. The base theme variant and the mono overlay are two independent controls on both
  platforms.** This gives the product all four theme columns the token set already defines
  (`10-design-system.md` 2.2), including mono light, which Android cannot reach today.
- **D-S7. One core choice, applied to every config type** (desktop), replacing eight per-type
  dropdowns.
- **D-S8. Settings search ships on both platforms** with the keyword lists of 7.3. Without it, 18
  routes is a maze; with it, depth is affordable.
- **D-S9. Android settings strings ship Russian in the default resource folder**, with English in
  `values-en/`. Today the departament strings live in the default (English) file, so a non-Russian
  device shows English chrome mixed with Russian product copy and half of this document's copy would
  never be seen.
- **D-S10. `settings/hotkeys` becomes `settings/window`** and absorbs the desktop window-behaviour
  settings, keeping the Приложение group at exactly 7 rows.
- **D-S11. `settings/fragment` is added** so a toggle and its three parameters are not on two
  different screens. This takes `11-app-structure.md` 9.4 from 15 routes to 16, and to 18 counting
  the two that document already implied (`settings/data/webdav`, `settings/advanced/template`).
- **D-S12. 21 settings are removed and 8 are merged** (section 6), each with a stated replacement
  behaviour and a stated migration. Nothing is silently dropped.

### 14.2 Needs an owner decision, in `00-rules.md` section 18 row format

| Date | Decision | Rule affected |
|---|---|---|
| pending | **D-S13.** The ping methods `HTTP_URL` and `ICMP` are removed from the product; stored values migrate to `Реальная задержка`. ICMP is silently blocked on most mobile networks and reports a failure that is not the server's; HTTP duplicates real delay with a worse signal | 6.2, `settings/latency` |
| pending | **D-S14.** The core memory limit (`PREF_MEMORY_LIMIT`, `PREF_MEMORY_LIMIT_ENABLED`) and its five-chip section stop being user-settable and stay at the shipped default | 6.1 |
| pending | **D-S15.** System-proxy mode, Clash/Mihomo proxy-group control, and per-protocol transport tuning (KCP, gRPC, Hysteria) are not app settings in this product. The first two are cut entirely; the third moves to the server form | 6.1 |
| pending | **D-S16.** `Разрешать небезопасные соединения` remains user-settable and remains neutral, not red. It is a risk, not a destructive action, and `00-rules.md` 6.2 reserves red for destroy and error | 5.9 row e |

Nothing in 14.2 is implemented until the row is pasted into `00-rules.md` section 18 and the rule
body there is updated.

---

## 15. Summary for an implementer with one afternoon

One hub with four groups and a footer pair. 23 rows on Android, 25 on desktop, 21 and 23 at rest.
Six row archetypes and nothing else: navigation, value, toggle, segment, action, destructive. Every
row is 56 tall with its title at 68 and one trailing element. No cards. No accent except a selected
segment, a switch that is on, and the focus ring. Sixteen sub-pages under Android and seventeen
under desktop, every one of them reachable, every one of them built from the same row and the same
56 seamless toolbar. Twenty-one settings deleted because the app can decide them, eight merged
because they had two homes, two bindings corrected because they wrote the wrong field. One search
field, because eighteen routes without one is a maze. Two confirm dialogs in the whole tree, both
irreversible. Every string Russian, sentence case, hyphen not dash, and every figure tabular.

If a reviewer can put the two hubs side by side and read down them in lockstep, this document did
its job.


