# 01 — Current-state inventory, ANDROID

**Repo:** `/home/user/dp` (fork of v2rayNG) · **Gradle root:** `/home/user/dp/V2rayNG`
**Package:** `com.v2ray.ang` · **UI:** Kotlin + XML views + Material 3 + ViewBinding
**Date of inventory:** 2026-07-26 · **Method:** read every file in
`app/src/main/res/layout/` (73 files, 11 949 lines), every file in `res/values*`,
`res/drawable*`, `res/menu`, `res/anim`, `res/interpolator`, `res/color`, `res/xml`,
plus every Activity/Fragment/Adapter/Sheet in `app/src/main/java/com/v2ray/ang/ui/**`
(11 873 lines) and `AndroidManifest.xml`.

This is the baseline the 2026 redesign is measured against. It is deliberately blunt.

---

## 0. Executive verdict (read this first)

The app is **three different apps wearing one APK**:

| Stratum | What it is | Where | Approx. share |
|---|---|---|---|
| **A — "Incy" 2025 layer** | Home / Servers / Settings tab / Account / Buy / Devices / History / Login. Uses `@dimen/space_*`, `TextAppearance.App.*`, 40dp tiles, 20dp cards. | `activity_main`, `layout_settings_content`, `activity_account`, `activity_buy_tariff`, `activity_devices`, `activity_payment_history`, `activity_login`, `sheet_*`, `item_buy_*`, `item_payment*`, `item_device`, `item_subscription_card` | ~35 % of layouts |
| **B — "hub" layer** | Local proxy / Provider settings / URL schemes / Backup / Routing / Assets / Per-app / Bypass list. Card-and-tile *shaped* like layer A but on a **completely different, hardcoded spacing scale** (12dp gutter, 14dp padding, 10dp vertical, 68dp divider inset, 20dp literal radius, `textSize="16sp"` instead of a type style). | `activity_local_proxy` (1035 ln), `activity_provider_settings` (648), `activity_url_scheme_list` (634), `activity_backup`, `activity_routing_setting`, `activity_user_asset`, `activity_bypass_list`, `item_recycler_*` | ~30 % |
| **C — raw upstream v2rayNG** | Server editors, About, Check update, Sub edit, Routing edit, TLS/transport includes, dialogs. `TextAppearance.AppCompat.Subhead`, bare `EditText`, `Spinner`, `padding_spacing_dp16`, no cards, no tiles, no brand font. | `activity_server_*` (8 files), `layout_address_port`, `layout_tls*`, `layout_transport`, `activity_about`, `activity_check_update`, `activity_sub_edit`, `activity_routing_edit`, `dialog_config_filter`, `dialog_webdav` | ~35 % |

**~14 screens are dead code** — registered, built, styled, and unreachable from any
UI path (see §5). Among them: the entire server-editing surface and the
`ServerActionsSheet` that was supposed to open it.

There is **no single design system**. There are three parallel row grammars, two
spacing scales, three type systems, four card radii (12 / 14 / 18 / 20 dp) and two
divider insets (44 / 68 / 72 dp). The tokens in `values/dimens.xml` exist and are
correct — they are simply not used outside layer A.

---

## 1. Complete screen inventory

### 1.1 The shell: `MainActivity`

**File:** `ui/MainActivity.kt` (2 777 lines — the largest file in the app)
**Layout:** `res/layout/activity_main.xml` (705 lines)
**Manifest:** `android:launchMode="singleTask"`, LAUNCHER + LEANBACK_LAUNCHER,
QS-tile preferences intent, app-shortcuts meta-data.
**Theme:** `AppThemeDayNight.NoActionBar`.

`MainActivity` is not a container for fragments. It is **one giant layout holding four
sibling `View` groups** that are `isVisible`-toggled:

| Tab id | Content group | Backing view | Notes |
|---|---|---|---|
| `R.id.nav_home` | `@id/group_home` | inline `NestedScrollView` in `activity_main.xml` | connect hero + subscription carousel + server list |
| `R.id.nav_servers` | `@id/group_servers` | inline `LinearLayout` | header include + `rv_servers` + empty include |
| `R.id.nav_settings` | `@id/group_settings` | `<include layout="@layout/layout_settings_content"/>` (1 536 lines inline) | 20 hand-written setting rows |
| `R.id.nav_account` | `@id/group_account` | `FrameLayout`, lazily `replace()`d with `AccountFragment` | only tab that is a real Fragment |

The fixed `AppBarLayout`/`MaterialToolbar` at the top of `activity_main.xml` is
**hidden on every tab** (`showTab()`, line 441: `binding.appbarLayout.isVisible = false`).
It is inflated, given the status-bar inset, then made GONE, on every tab switch. Dead
chrome that still costs a measure pass.

---

#### SCREEN 1 — Home tab (`nav_home`)

**Reached:** app launch (default), Back from any other tab, `selectNav(R.id.nav_home)`.

Vertical stack inside a `fillViewport` `NestedScrollView`:

1. **Stats row** (`home_stats_row`, lines 58–172) — `↑ 0 KB/s` · `00:00:00` · `↓ 0 KB/s`
   plus a 42×42 dp `+` ImageButton. The arrows are literal `android:text="↑"` / `"↓"`
   TextViews. A 42dp invisible `View` spacer is used to optically centre the block.
   Hidden when there are no servers.
2. **Account header** (`<include layout_home_account/>`) — 155 lines for what renders
   as **one 36dp-avatar row** ("name / Управление аккаунтом / ›"). The file also
   carries a `group_login` container whose only child is a dismissible
   "Привяжите Telegram" CTA banner with an `android:text="✕"` close button — and
   `updateAccountGate()` (line 1052) sets `header.groupLogin.isVisible = false`
   unconditionally. **The entire signed-out half of this file is dead.**
3. **Connect hero** (`card_hero`, lines 187–308) — 230dp `FrameLayout` stacking, back
   to front: `view_connect_glow` (radial gradient halo), `view_connect_ring`
   (two-stroke oval), `view_connect_pulse` (one-shot sonar), `progress_connect`
   (212dp `CircularProgressIndicator`), and a 176dp `MaterialCardView` with 88dp
   radius holding two 80dp shields (`ic_shield_outline` + `ic_shield_filled` crossfade).
   Below it, one 16sp label (`tv_connection_status`).
4. **Memory card** (`card_memory`) — status dot + "25 MB · Normal". `visibility="gone"`
   unless `PREF_SHOW_MEMORY`. **The preference that controls it is not reachable from
   the UI** (it lives only in the orphaned `pref_settings.xml`).
5. **Onboarding block** — `tv_home_welcome` ("Приветствуем!") + `<include
   layout_home_empty/>` bracketed by two weighted spacers that centre it vertically.
6. **Subscription carousel** (`group_home_meta`) — `ViewPager2` of
   `layout_subscription_meta_bar.xml` pages + hand-built dots.
7. **Server list** (`rv_home_servers`) — flat `MainRecyclerAdapter`, headers off.

**States implemented:** empty (no servers) · signed-out empty · signed-in-no-sub
(buy CTA) · populated · connecting · connected · idle-with-no-server (shield dims to
0.38 alpha) · list collapsed. **Not implemented:** offline/no-network, subscription
expired, server list loading.

---

#### SCREEN 2 — Home empty state (`layout_home_empty.xml`, 139 lines)

A `MaterialCardView` holding, depending on auth state:
- signed out: «Добавить QR» (filled) + «Из буфера» (outlined) + a `group_home_login`
  block with a 12sp caption «или войдите» + a 52dp tonal "Войти через Telegram"
  pill (`cornerRadius=26dp`) + an outlined "Войти через сайт".
- signed in, no subscription: «Купить подписку» (filled) + optional
  «Привязать Telegram» (outlined).

This card **is the real sign-in screen** for 100 % of new users. `LoginActivity` is
only ever reached *from* it. It is a card floating in the middle of an empty gradient,
with two competing filled buttons and no product framing.

---

#### SCREEN 3 — Subscription meta bar (`layout_subscription_meta_bar.xml`, 257 lines)

One carousel page. Row 1: 48dp chevron (13dp padding → 22dp glyph) + title + a
"09.07.2026 07:08 · Автообновление — 1 ч." caption + three 36dp icon buttons
(ping / refresh / pin) + a 20dp spinner. Body: a 160dp-wide traffic pill
(`ProgressBar` max=1000 with an 11sp label centred *on top of the fill*), an expiry
marker (`∞` / «до 12.08.2026» / «Истекла» in red), an optional announce banner
(`autoLink="web"`, max 5 lines), a tonal «Поддержка» button and a 36dp Telegram button
in opposite bottom corners.

Long-press on the page = delete the subscription (`HomeMetaPagerAdapter` line 66,
`onLongClickListener` → `confirmDeleteSubscription`). **Undiscoverable and
destructive.** ViewPager2 height is measured by inflating and measuring *every*
subscription's page on every rebuild (`measureHomeMetaHeight`, lines 888–910).

---

#### SCREEN 4 — Servers tab (`nav_servers`)

**Reached:** bottom nav; also forced by `locateSelectedServer()` (line 2276) — which
is itself unreachable, no caller.

- `layout_servers_header.xml` (108 lines): 24sp "Сервера" headline + four **36dp**
  icon buttons (collapse-all / refresh-all / ping-all / add) + a "15 серверов · 1
  провайдер" subtitle + a 44dp search pill (`bg_search_pill`, radius 14dp, filter
  icon as `drawableStart`).
- `rv_servers` with `MainRecyclerAdapter` (`showHeaders = true`).
- `layout_servers_empty.xml`: a card 64dp from the top with a 56dp cloud glyph,
  "Нет серверов", and two buttons. Padding 24/20/28/20/24dp — none of it tokenised.

**Server row** (`item_recycler_main.xml`, 130 lines): 28dp flag tile (emoji from
`FlagUtil`, globe `🌐` fallback) + name + protocol chip + `TRANSPORT · SECURITY`
caption + ping spinner/ms. Selection = `bg_server_row` selected state (1.5dp blue
stroke + `#1F4C8DFF` fill). Tap = select. **Long-press = nothing** (see §5.1).
A zero-size `layout_indicator` View survives purely so the adapter can
`setBackgroundColor` it (`MainRecyclerAdapter.kt:208`).

**Section header** (`item_section_header.xml`): 22dp chevron + provider name + count.

---

#### SCREEN 5 — Settings tab (`nav_settings`)

**Layout:** `layout_settings_content.xml` — **1 536 lines, 20 rows, zero reuse.**
Every row is a hand-copied 55-line `LinearLayout` block. Two reusable includes
(`layout_setting_row.xml`, `layout_setting_toggle_row.xml`) exist and are **never
used by any layout** — they are orphans.

| Section | Rows |
|---|---|
| Подключение | Режим (TUN/Proxy/VPN+Proxy) · Прокси по приложениям · Обход локальной сети ⏻ · IPv6 ⏻ · DNS · Пинг · Локальный прокси › · Always-on VPN › |
| Обход блокировок | Mux ⏻ · Число соединений Mux (conditional) · Фрагментация ⏻ |
| Интерфейс | Оформление · Язык · Запуск при загрузке ⏻ |
| Подписка | Автообновление подписки · Маршрутизация › · Файлы ресурсов › · Настройки провайдеров › |
| Устройства | Перенести подписку на ТВ › · Принять подписку › (TV only) |
| О приложении | О приложении · Резервное копирование › · Схемы URL-адресов › |

Icon tiles are declared `bg_icon_blue / green / orange / purple / yellow / red` but
the theme repoints `iconTintGreen/Orange/Purple/Yellow → @color/icon_blue`
(`values/themes.xml:88-99`), so **every tile renders blue** except the red destructive
one. The colour names in the layouts are lies.

**Missing:** everything in `res/xml/pref_settings.xml` that the tab does not surface
(≈ 30 settings — sniffing, allow-insecure, fake DNS, local DNS, VPN MTU, interface
address, hev-tunnel log level & timeout, core log level, outbound domain resolve,
remote/domestic DNS, DNS hosts, fragment length/interval/packets, mux xudp, delay-test
URL, real-ping concurrency, IP-API URL, double-column, group-all, confirm-remove,
start-scan-immediate, speed-enabled, prefer-IPv6, append-HTTP-proxy, auto-remove-
invalid, auto-sort-after-test, show-memory, auto-fallback). See §5.2.

---

#### SCREEN 6 — Account tab (`nav_account`, `AccountFragment` + `activity_account.xml`)

**Reached:** 4th bottom-nav item (visible only when `AccountSession.isLoggedIn()`),
or the home account chip. Attached lazily once (`accountFragmentAdded`).

Zones:
1. **Profile card** — 52dp avatar container (48dp circle + 18dp camera badge) +
   name + a pill "Пополнить" button; label «Баланс» + 34sp `Display` figure with
   `tnum` and an animated count-up; a referral chip row that copies on tap.
2. **Payment-pending hint** (`tv_pending`) — chip-styled block shown while polling.
3. **Subscription slot** — a `FrameLayout` with **four mutually-exclusive children**:
   skeleton (152dp `bg_skeleton` + 3 grey bars, alpha-pulsed) · empty card
   («Нет активной подписки» + buy CTA) · carousel (`ViewPager2` of
   `item_subscription_card.xml` + dots) · error card («Не удалось загрузить» + retry).
   This is the **only screen in the app with a complete state machine.**
4. **«Управление» list** — Купить подписку (blue tile, blue label) · Устройства
   (neutral tile, trailing "1 / 3") · История платежей (neutral tile, trailing date).

`item_subscription_card.xml` — name + tariff badge chip + «Действует до …» +
«Устройства: 1 / 3».

---

#### SCREEN 7 — Login (`LoginActivity` + `activity_login.xml`, 314 lines)

**Reached:** `openLoginScreen("telegram"|"site")` from the two buttons in
`layout_home_empty`, and `openTelegramLink()` (link mode) from the home CTA / the
"Привязать Telegram" button. Hosted in `activity_base.xml` → **has a toolbar with an
up arrow and the title «Вход»**, unlike every tab.

Two stacked `MaterialCardView`s, both `?colorSurface` + 1dp outline + 20dp radius:
- **Telegram card**: 16sp title, 13sp description, a 52dp filled pill
  (`cornerRadius=26dp`), and a hidden `layout_awaiting` block (circular
  `ProgressBar` + «Ожидаем подтверждения в Telegram…» + an outlined «Начать заново»).
- **Site card**: email `TextInputLayout` + password `TextInputLayout` with
  `password_toggle`, a 52dp filled «Войти через сайт» with a centred `ProgressBar`
  overlay, a hidden 2FA block (divider + description + 6-digit field + second 52dp
  button + second spinner), and an outlined «Регистрация на сайте» that opens
  `https://departament.site` in a Custom Tab.
- A red `tv_error` line under both.

`EXTRA_MODE` can hide either card, so this screen has **three shapes** (both / site /
telegram) plus link-mode. Errors: `auth_err_credentials / gone / unavailable /
network / not_configured / generic`; a raw-detail diagnostic dialog fires only in
`BuildConfig.DEBUG`.

---

#### SCREEN 8 — Buy subscription (`BuyTariffActivity` + `activity_buy_tariff.xml`, 309 ln)

**Reached:** Account › Купить подписку · Account empty-state CTA · Home empty-state
`btn_home_buy`. Toolbar title «Купить подписку».

States, all in one scroll view: `progress_buy` spinner · `iv_state_icon` +
`tv_state` + `btn_retry` (error) · `tv_state` alone (empty) · `ll_skeleton`
(3 × 76dp `surfaceVariant` cards) · `tv_pending` (post-checkout polling) · content.

Content: «Выберите тариф» + a runtime-built column of `item_buy_tariff.xml` cards
(name + «Устройства: 3 · Трафик: ∞» + a check glyph; selected card expands
`ll_price_options` with `item_buy_option.xml` rows). Then a checkout card: a
«Дополнительные устройства» stepper (two 40dp `IconButton`s with
`backgroundTint=?iconTileBgBlue`, 20dp glyphs), a divider, «Итого» + a 24sp blue
price, and a 52dp «Оплатить» pill with a wallet icon.

---

#### SCREEN 9 — Devices (`DeviceManagementActivity` + `activity_devices.xml`, 85 ln)

**Reached:** Account › Устройства. Subtitle line + `rv_devices` of `item_device.xml`
(20dp card, blue tile, name / «Активно: dd.MM.yyyy» / «ID: …», 44dp red delete
`IconButton`). Empty state: 64dp blue tile + «Нет подключённых устройств» + hint.
Delete → confirm dialog. Diagnostic dialog on empty/failed responses
(`devices_diag_*`) that literally asks the user to screenshot the raw server body.

---

#### SCREEN 10 — Payment history (`PaymentHistoryActivity` + `activity_payment_history.xml`)

**Reached:** Account › История платежей. `SwipeRefreshLayout` + `rv_payments` of
`item_payment.xml` (20dp card, blue history tile, description / date, amount +
status chip tinted green/orange/red/yellow at bind time). Empty & error share one
centred block (`tv_empty` with a `drawableTop`); `btn_history_buy` is present but
`visibility="gone"` and **never wired** — a placeholder shipped in the APK.

---

#### SCREEN 11 — Local proxy (`LocalProxyActivity` + `activity_local_proxy.xml`, 1 035 ln)

**Reached:** Settings › Локальный прокси. Sections: ПАМЯТЬ (limit chips 40/60/80/100/
150 as five outlined buttons + «Снять ограничение» toggle) · SOCKS5-АВТОРИЗАЦИЯ
(master switch + login/password/address/port fields with copy & eye buttons +
reset) · ЛОКАЛЬНЫЙ ПРОКСИ (block UDP, HTTP auth, hide icon) · ДОСТУП ЧЕРЕЗ ХОТСПОТ
(endpoint + warnings + login/password) · МАРШРУТИЗАЦИЯ ПО ДОМЕНУ.

**112 hardcoded dp values, 0 token references, 37 raw `textSize`, 0
`TextAppearance.App.*`.** The single worst file in the repo by that measure.

---

#### SCREEN 12 — Provider settings (`ProviderSettingsActivity` + 648 ln layout)

**Reached:** Settings › Настройки провайдеров. Sections: ОБНОВЛЕНИЕ (auto-update ⏻,
interval, notify ⏻) · ПРИ ЗАПУСКЕ (update-on-launch ⏻, ping-on-launch ⏻,
ping-on-update ⏻) · СЕТЬ (send HWID ⏻, User-Agent) · СПИСОК СЕРВЕРОВ (sort order).
66 hardcoded dp, 15 raw `textSize`, 0 tokens.

Note the overlap: «Автообновление подписки» also exists in the Settings tab, writing
the *same* `SubscriptionItem.autoUpdate/updateInterval` fields. **Two UIs for one
setting, in two different visual languages, two taps apart.**

---

#### SCREEN 13 — URL schemes (`UrlSchemeListActivity` + 634 ln layout)

**Reached:** Settings › Схемы URL-адресов. A note card, then five section cards
(ЗАПУСТИТЬ / ОСТАНОВИТЬ / ПЕРЕКЛЮЧИТЬ / ДОБАВИТЬ КОНФИГУРАЦИЮ / МАРШРУТИЗАЦИЯ),
each row = label + `depv://…` mono string + a copy button. 57 hardcoded dp, 20 raw
`textSize`. This is a **developer reference page shipped to consumers.**

---

#### SCREEN 14 — Backup (`BackupActivity` + `activity_backup.xml`, 254 ln)

**Reached:** Settings › Резервное копирование. Two cards: ЛОКАЛЬНО (Backup / Share /
Restore, 60dp rows, 14dp padding, 68dp divider inset, `textSize="16sp"`) and ОБЛАКО
(WebDAV settings → `dialog_webdav.xml`, a bare 4-`EditText` ScrollView with no labels).

---

#### SCREEN 15 — Routing (`RoutingSettingActivity` + `activity_routing_setting.xml`)

**Reached:** Settings › Маршрутизация. A «Стратегия доменов» row card + a list of
`item_recycler_routing_setting.xml` rules (20dp card, green→blue tile, name + lock
glyph + domain/ip counts + enable switch). Toolbar overflow: add rule, import
predefined / from clipboard / from QR, export to clipboard — **five actions hidden
in an overflow menu the redesigned shell otherwise never uses.**

---

#### SCREEN 16 — Routing rule editor (`RoutingEditActivity`, 252 ln)

**Reached:** Routing › a rule / add rule. Pure upstream: bare `TextView` labels +
`EditText`s + a `Spinner`, `padding_spacing_dp16`, no cards, no brand font.

---

#### SCREEN 17 — Assets (`UserAssetActivity`, 119 ln) + Add URL (`UserAssetUrlActivity`, 85 ln)

**Reached:** Settings › Файлы ресурсов. Sources row card + `item_recycler_user_asset`
list. Toolbar menu: add file / add url / scan QR / download. `UserAssetUrlActivity` is
upstream-plain.

---

#### SCREEN 18 — Per-app proxy (`PerAppProxyActivity` + `activity_bypass_list.xml`)

**Reached:** Settings › Прокси по приложениям. Header card (enable ⏻ + bypass-mode ⏻
+ hint) + a search/list of `item_recycler_bypass_list.xml` (18dp radius — the only
18dp in the app — 40dp app icon, 15sp name, 12sp package). Toolbar menu: search,
select all, invert, select proxy app, import/export proxy app (6 items).

---

#### SCREEN 19 — App picker (`AppPickerActivity` + `activity_app_picker.xml`)

**Reached:** from per-app flows. Layout is **a bare `RecyclerView`** — 10 lines, no
empty state, no header, reuses `item_recycler_bypass_list`.

---

#### SCREEN 20 — TV send (`TvSendActivity`, 128 ln) / SCREEN 21 — TV receive (`TvReceiveActivity`, 58 ln)

**Reached:** Settings › Перенести подписку на ТВ; also from the `+` menu
(`R.id.tv_send`). Receive is registered `screenOrientation="landscape"` and only shown
on LEANBACK devices. Send: instruction card (48dp glyph, 20dp padding, 20dp radius,
hardcoded) + scan button + subscription picker. Receive: overscan-padded (48/27dp)
QR + instructions.

---

#### SCREEN 22 — About (`AboutActivity` + `activity_about.xml`, 171 ln)

**Reached:** Settings › О приложении. **Pure upstream v2rayNG**: five 24dp-icon rows
(source code, OSS licenses, feedback, Telegram channel, privacy policy) with
`TextAppearance.AppCompat.Subhead`, `padding_spacing_dp16`, no cards, no tiles, no
brand font, then a centred version + appId block. Looks like a 2018 app.

---

#### SCREEN 23 — Scanner (`ScannerActivity` + `activity_none.xml`)

**Reached:** every "scan QR" path via `QRCodeScannerHelper`. The layout is an **empty
`RelativeLayout`** — the camera preview is supplied by the ZXing library, so the app's
own scan screen has zero branding, zero instruction copy, and zero framing overlay.
Menu: scan / select photo, both with `android:title=""`.

---

#### SCREEN 24 — Tasker (`TaskerActivity` + `activity_tasker.xml`, 48 ln)

**Reached:** externally only (`com.twofortyfouram.locale.intent.action.EDIT_SETTING`).
Toolbar title is `""`.

---

#### SCREEN 25 — Deep-link handler (`UrlSchemeActivity`)

**Reached:** `v2rayng://install-config|install-sub`, `depv://…`, and `ACTION_SEND`
text. `setContentView(binding.root)` on `activity_none` semantics; finishes without
UI. No confirmation surface for `depv://import/{base64}` — a link silently mutates
the user's server list.

---

#### SCREENS 26–29 — Shortcut stubs

`ScSwitchActivity`, `ScStartActivity`, `ScStopActivity`, `ScScannerActivity` — all
`setContentView(R.layout.activity_none)`, translucent theme, `excludeFromRecents`,
run in `:RunSoLibV2RayDaemon`. Launcher long-press shortcuts (`res/xml/shortcuts.xml`)
point at them. They flash a transparent activity and finish.

---

### 1.2 Sheets

| Sheet | File | Reached by | Status |
|---|---|---|---|
| Server actions | `sheet_server_actions.xml` (271 ln) + `ServerActionsSheet.kt` | *nothing* | **DEAD** — see §5.1 |
| Payment method | `sheet_payment_method.xml` + `item_payment_method.xml` + `PaymentMethodSheet.kt` | Buy › Оплатить; Account › Пополнить | live |

Both use `bg_sheet_top` (24dp top corners, `surfaceContainerLow`) + a 36×4dp handle.
`ServerActionsSheet` additionally paints the Material sheet container transparent so
its own background shows through.

### 1.3 Dialogs (all `AlertDialog.Builder`, themed by `ThemeOverlay.Departament.Dialog`)

Single-choice pickers: Режим · Пинг · DNS preset · Оформление · Язык ·
Автообновление подписки. Text inputs: DNS «Свой…» · Число соединений Mux ·
«Ввести вручную» (with inline `input.error` validation and **two hardcoded Russian
error strings in Kotlin**, `MainActivity.kt:2016` and `:2018`). Confirms: delete
server · delete all · delete duplicates · delete invalid · delete subscription ·
delete device. Content dialogs: QR code (`item_qrcode.xml`, a 336dp `fitXY`
ImageView, no title, no share action) · share-method list · top-up amount
(`dialog_top_up.xml`) · avatar options · payment-error diagnostic ·
auth-error diagnostic (debug only) · WebDAV (`dialog_webdav.xml`) ·
config filter (`dialog_config_filter.xml`).

### 1.4 Non-screen surfaces

- **Custom status toast** — `toast_status.xml`, a single bold 14sp pill on
  `bg_toast_status`, gravity BOTTOM + 110dp y-offset. Used for «Подключение…» /
  «Прокси подключён» / «Отключено» / «Не удалось подключиться» / «Обновлено» /
  «Подписка привязана». Uses the **deprecated custom-view Toast API** and is
  therefore invisible on Android 12+ for apps not in the foreground.
- **`Snackbar` count in the app: zero.** Every transient message is a Toast.
- **QS tile** (`QSTileService`), **home-screen widget** (`WidgetProvider` +
  `widget_switch.xml` — a 45dp icon + `TextAppearance.AppCompat.Small` white label),
  **notification** (`ic_notif_stop`, `ic_notif_restart`).

---

## 2. Current navigation model

### 2.1 Topology

```
MainActivity (singleTask, no ActionBar, edge-to-edge)
├── custom bottom bar (LinearLayout, 4 weighted items, NOT BottomNavigationView)
│   ├── nav_home     → group_home      (always)
│   ├── nav_servers  → group_servers   (always)
│   ├── nav_settings → group_settings  (always)
│   └── nav_account  → group_account   (visible only when AccountSession.isLoggedIn())
└── pushes (all via activity_base.xml → toolbar + up arrow + LinearProgressIndicator)
    ├── LoginActivity            (from Home empty card)
    ├── BuyTariffActivity        (Account, Account-empty, Home-empty)
    ├── DeviceManagementActivity (Account)
    ├── PaymentHistoryActivity   (Account)
    ├── PerAppProxyActivity      (Settings) → AppPickerActivity
    ├── LocalProxyActivity       (Settings)
    ├── ProviderSettingsActivity (Settings)
    ├── RoutingSettingActivity   (Settings) → RoutingEditActivity
    ├── UserAssetActivity        (Settings) → UserAssetUrlActivity
    ├── BackupActivity           (Settings)
    ├── UrlSchemeListActivity    (Settings)
    ├── AboutActivity            (Settings)
    ├── TvSendActivity           (Settings + "+" menu) / TvReceiveActivity (Settings, TV only)
    └── ScannerActivity          (any QR action)
```

### 2.2 Bottom bar implementation

`activity_main.xml:519-701`. **Not** a `BottomNavigationView` — a plain
`LinearLayout` with `minHeight="56dp"`, four weighted `LinearLayout` items, each
icon 24dp + label 11sp + a 34×3dp `bg_nav_dot` pill (INVISIBLE when inactive so the
column height never shifts). `android:stateListAnimator="@anim/nav_press"` scales the
item to 0.92 on press; there is deliberately **no ripple**.

Selection is painted in `updateNavSelection()` (`MainActivity.kt:343`): an
`ArgbEvaluator` tween of icon tint + label colour over 200 ms `ease_standard`, plus a
runtime typeface weight step (500 → 700) and the pill toggle. Three redundant
resources survive from the previous implementation and are **unused**:
`res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml`,
`style/BottomNavIndicator`.

The whole bar (bar + `bottom_nav_scrim`) is **hidden** when signed-out AND no servers
(`updateBottomNavVisibility`, line 713), so first launch is a bar-less screen.

### 2.3 Tab transition

`showTab()` (`MainActivity.kt:430`) — outgoing group `alpha → 0` over 150 ms
`ease_standard`, then incoming `alpha 0→1` + `translationY 8dp→0` over 200 ms
`ease_out_quint`, with a `tickHaptic()`. This is a hand-rolled fade-through; it is
*not* `MaterialFadeThrough`, so it does not participate in any container transform,
and there is no shared-element continuity anywhere in the app.

### 2.4 Back behaviour

Three competing handlers:

1. `OnBackPressedCallback` (line 250): if `selectedNavId != nav_home` → go to Home;
   else disable itself and re-dispatch.
2. `onKeyDown` (line 2298): `KEYCODE_BACK` / `KEYCODE_BUTTON_B` → `moveTaskToBack(false)`,
   **returns true unconditionally.**
3. `BaseActivity.onOptionsItemSelected` (line 72): `android.R.id.home` →
   `onBackPressedDispatcher.onBackPressed()`.

Handler 2 makes the app **never finish on Back** — it always backgrounds. Combined
with `launchMode="singleTask"` this is legal but non-standard, and it means the
predictive-Back animation on Android 14+ shows the app "closing" and then not closing.
No `android:enableOnBackInvokedCallback` is declared anywhere, so **predictive Back is
not supported at all.**

Sub-screens rely on the toolbar up arrow and the system Back; there is no
`parentActivityName` on most of them (only the six "departament wave" activities
declare it), so up ≡ back everywhere.

### 2.5 Insets

`setupEdgeToEdge()` (line 488) applies the **system-bars top inset to all four tab
groups individually** and the bottom inset to the bar, and pads both RecyclerViews by
`bottom + 56dp + 16dp`. `activity_base.xml` instead uses plain
`android:fitsSystemWindows="true"`. **Two different inset strategies in one app.**
No `ime()` inset is applied anywhere — the login form, the DNS dialog, the local-proxy
fields and every server editor rely on `windowSoftInputMode` defaults.

---

## 3. The current token set — and every place it is violated

### 3.1 What is declared

**`res/values/dimens.xml`**
```
space_4 / space_8 / space_12 / space_16 / space_24 / space_32
radius_chip 12 · radius_card 20 · radius_tile 12 · radius_pill 100 · radius_sheet 24
tile_size 40 · tile_glyph 22 · row_min_height 56 · screen_gutter 16
sub_card_height 152 · dot_size 6 · dot_size_active 8 · dot_gap 8
(legacy, still referenced) padding_spacing_dp4/8/16 · image_size_dp24 ·
view_height_dp36/48/64/120/160
```

**`res/values/styles.xml` — type ramp**
| Style | Font | Size | Weight | Colour |
|---|---|---|---|---|
| `TextAppearance.App.Display` | Space Grotesk | 34sp | 700 | onSurface |
| `.Headline` | Space Grotesk | 24sp | 700 | onSurface |
| `.Title` | Space Grotesk | 16sp | 700 | onSurface |
| `.Title.Medium` | Space Grotesk | 16sp | 500 | onSurface |
| `.Body` | system | 14sp | — | onSurface |
| `.Subtitle` | system | 13sp | — | onSurfaceVariant |
| `.Caption` | system | 12sp | — | onSurfaceVariant |
| `.Chip` | Space Grotesk | 11sp | 500 | — (inherits) |
| `.Numeric` | Space Grotesk | (inherited) | — | tnum/lnum |
| `SettingsSectionLabel` | Space Grotesk | 16sp | 700 | sentence-case, 16/16/18/8 padding |
| `ToolbarBrandTitle` | Space Grotesk | 20sp | 700 | onBackground |
| `BottomNavLabel` | system | 11sp | 500 | — |

**Colour** — `values/colors.xml` + `values-night/colors.xml`, mapped onto Material 3
roles in `values/themes.xml`. Night: background `#0A0B0D`, surface `#141619`,
container ramp `#08090B → #20242B`, primary `#4C8DFF`, onSurface `#F2F4F8`,
onSurfaceVariant `#9BA1AD`, outline `#2A2E36`, outlineVariant `#20242B`,
error `#F04452`, tertiary (green) `#22C55E`.
Day: background `#F4F7FC`, surface `#FFFFFF`, primary `#1E5FC7`.
A runtime `ThemeOverlay.Mono` neutralises everything to greyscale.

**Motion** — `values/motion.xml`: `press_in 90 · press_out 160 · state 220 ·
reveal 300 · stagger 40 · emphasis 600`. Interpolators: `ease_out_quart`,
`ease_out_quint`, `ease_standard`. Anims: `press_scale` (0.96), `nav_press` (0.92),
`connect_confirm`, `shield_assemble`.

**Custom attrs** — `connectIdleColor/ActiveColor/connectedColor`, `chipTypeText`,
`chipJsonText/Bg`, `pingGood/Bad`, `indicatorColor`, `iconTint{Blue,Green,Orange,
Purple,Red,Yellow}`, `iconTileBg{…}`.

### 3.2 Violations — spacing

| Violation | Where | Evidence |
|---|---|---|
| **12dp screen gutter instead of 16dp** | `activity_local_proxy`, `activity_provider_settings`, `activity_url_scheme_list`, `activity_backup`, `activity_routing_setting`, `activity_user_asset`, `activity_bypass_list`, `item_recycler_routing_setting`, `item_recycler_user_asset`, `item_recycler_bypass_list` | `layout_marginHorizontal="12dp"` / `marginStart="12dp"` |
| **14dp row padding instead of 16dp** | same layer-B files | `paddingHorizontal="14dp"`, `layout_marginStart="14dp"` |
| **10dp vertical row padding instead of 12dp** | same | `paddingVertical="10dp"` |
| **60dp `minHeight` instead of `@dimen/row_min_height` (56)** | layer B rows | `android:minHeight="60dp"` |
| **68dp divider inset vs 72dp in the Settings tab vs 44dp in `custom_divider`** | layer B / layer A / RecyclerView divider | three different left insets for the same visual device |
| **6dp / 4dp / 2dp off-scale margins** | `item_recycler_sub_setting` (`marginVertical="6dp"`), `item_recycler_user_asset` (6dp), `item_recycler_bypass_list` (4dp), `item_payment` `marginTop="2dp"`, `item_buy_tariff` `marginTop="2dp"`, `item_recycler_main` chip padding `2dp` | |
| **20 / 28 / 64 / 24dp ad-hoc** | `layout_servers_empty` (`padding="24dp"`, `marginTop="64dp"`, `paddingTop="28dp"`, `paddingStart="20dp"`) | 9 hardcoded values in a 67-line file |
| **13dp padding to fake a 22dp glyph** | `layout_subscription_meta_bar` `btn_collapse` | `android:padding="13dp"` |
| **3dp / 34dp nav metrics** | `activity_main` nav items (`layout_marginTop="3dp"`, dot `34x3dp`) | |
| **42dp / 36dp / 44dp button sizes** | `btn_home_add` 42dp, servers-header + meta-bar buttons 36dp, `btn_device_delete` 44dp, `et_search` 44dp | none is 40 or 48 |
| **48dp `minHeight` on `item_buy_option`** | literal, not `@dimen/row_min_height` | |
| **`padding_spacing_dp16` legacy scale** | all of layer C (`activity_server_*`, `layout_address_port`, `layout_tls*`, `layout_transport`, `activity_about`, `activity_check_update`, `activity_sub_edit`, `activity_routing_edit`, `dialog_*`, `item_recycler_proxy_chain_member`, `item_recycler_footer`) | a **second, parallel spacing scale** |

Aggregate count of hardcoded `padding*`/`margin*` dp literals per file:
`activity_local_proxy` **112** · `activity_provider_settings` **66** ·
`activity_url_scheme_list` **57** · `layout_settings_content` 17 ·
`activity_backup` 17 · `activity_main` 11 · `activity_tv_send` 10 ·
`activity_bypass_list` 10 · `layout_servers_empty` 9 · `item_recycler_routing_setting` 9.

### 3.3 Violations — radius

Declared: 12 (chip/tile) · 20 (card) · 24 (sheet) · 100 (pill).
Actually shipped: **2** (`bg_nav_dot`, `bg_sheet_handle`), **8** (traffic pill),
**12**, **14** (`bg_search_pill`, `bg_buy_option`), **18** (`item_recycler_bypass_list`),
**20** (literal in ~15 files instead of `@dimen/radius_card`), **22**
(`btn_retry`, `btn_history_buy`), **24** (`bg_toast_status`), **26**
(every 52dp CTA pill: `cornerRadius="26dp"`), **88** (`card_connect`).

### 3.4 Violations — typography

- **34 layout files contain no `TextAppearance.App.*` at all** (listed in §1
  strata B and C). They use `TextAppearance.AppCompat.Subhead/Small`, or nothing.
- **21 files set a raw `android:textSize`**, totalling ~100 occurrences.
  Worst: `activity_local_proxy` 37, `activity_url_scheme_list` 20,
  `activity_provider_settings` 15.
- The `Body` role is 14sp, but layer B renders row titles at `16sp` with
  `textColor="?attr/colorOnSurface"` — a 4th, undeclared "title" size.
- `activity_main` overrides the ramp inline three times
  (`textSize="13sp"`, `"14sp"` on `TextAppearance.App.Numeric`).
- `layout_subscription_meta_bar` overrides `.Numeric` to `11sp` weight 500.
- `layout_home_account` sets `textSize="16sp"` + `textStyle="bold"` on the avatar
  monogram and `textSize="16sp"` on the `✕` glyph.
- `.Title` is used as a *row label* in `activity_account` (16sp/700) and as a
  *list-item title* in `item_recycler_main` — same style, two hierarchy levels.
- **Positive:** no `textAllCaps="true"` anywhere; `SettingsSectionLabel` is
  sentence-case bold. The tiny-tracked-eyebrow ban is genuinely respected.
  (Section *comments* are shouted — `<!-- ==== ПОДКЛЮЧЕНИЕ ==== -->` — but the
  rendered strings are sentence case.)

### 3.5 Violations — colour & the "one accent" rule

- **Decorative gradients (explicitly banned).**
  `bg_home_gradient.xml` and `drawable-night/bg_home_gradient.xml` — a 560dp radial
  gradient behind the entire Home tab. `bg_home_gradient_mono.xml` too.
  `bg_connect_glow.xml` / night / mono — a radial **glow** halo, animated to
  "breathe" (scale 0.96↔1.04, alpha 0.3↔0.6, 850 ms, INFINITE REVERSE) while
  connecting (`MainActivity.kt:1741`). `bg_bottom_nav_scrim.xml` — a linear
  surface→transparent gradient. `bg_nav_header.xml` — a 135° brand-blue diagonal
  gradient (orphan, see §5.3).
- **The `iconTint*` lie.** Six tint attrs and six tile-background attrs exist;
  `values/themes.xml:88-99` collapses green/orange/purple/yellow all to
  `@color/icon_blue` / `@color/icon_tile_blue`. So `bg_icon_green` renders blue.
  Layouts still say "green". Any future maintainer will reintroduce real colours by
  accident.
- **Surviving non-accent hues:** `@color/color_connected #22C55E` and
  `@color/colorPingRed` on the memory dot (`MainActivity.kt:1818-1820`), the payment
  status chip (green/orange/red/yellow, `PaymentsAdapter`), `chip_json_bg` gold,
  `md_theme_tertiary` green.
- **Contrast failures (computed):**
  - Protocol chip, dark theme: `chip_type_text #4C8DFF` on `colorPrimaryContainer
    #17325C` = **4.0 : 1** at 11sp → fails 4.5 : 1 (`item_recycler_main.xml:86`).
  - Traffic pill label: `colorOnSurface #F2F4F8` at 11sp centred **on top of** the
    `colorPrimary #4C8DFF` fill = **2.9 : 1** wherever the fill has advanced past the
    glyph (`layout_subscription_meta_bar.xml:163-176`). Contrast changes mid-word as
    the bar fills.
  - `custom_divider` uses `@color/divider_color_light` (a raw colour, not
    `?colorOutlineVariant`), so it does not follow the mono overlay.
- **Raw hex in drawables that should be role tokens:** `bg_server_row` `#1F4C8DFF`
  (×2), `bg_buy_option_selected` `#1F4C8DFF`, `bg_connect_ring` `#2E1E5FC7` /
  `#701E5FC7`, all the gradient stops.

### 3.6 Violations — touch targets (Material: 48×48 dp minimum)

| Target | Size | File |
|---|---|---|
| Servers header: collapse / refresh / ping / add | **36dp** | `layout_servers_header.xml:30,42,54,66` |
| Meta bar: ping / refresh / pin / telegram | **36dp** | `layout_subscription_meta_bar.xml:76,89,112,240` |
| Home `+` | **42dp** | `activity_main.xml:161` |
| CTA dismiss `✕` | **40dp** | `layout_home_account.xml:63` |
| Device delete | **44dp** | `item_device.xml:83` |
| Buy stepper − / + | **40dp** (`@dimen/tile_size`) | `activity_buy_tariff.xml:214,243` |
| Sub-setting row share / edit / delete | 24dp glyph + 8dp padding = **40dp** | `item_recycler_sub_setting.xml` |
| Search field | 44dp height | `layout_servers_header.xml:91` |

Only `btn_collapse` in the meta bar (48dp) is compliant. There is also **no 8dp
minimum gap** between the four 36dp header buttons — they are flush.

### 3.7 Violations — component grammar

- **Three row grammars.** (a) Settings-tab row: 40dp tile / 16dp gap / `App.Body` /
  value `App.Subtitle` / 18dp chevron / 72dp divider. (b) Layer-B row: 40dp tile /
  14dp gap / `16sp` raw / 18dp chevron / 68dp divider / 60dp minHeight.
  (c) Layer-C row: 24dp bare icon / 16dp gap / `AppCompat.Subhead` / no chevron / no
  divider.
- **Two unused row components** — `layout_setting_row.xml` and
  `layout_setting_toggle_row.xml` exist, are correct, and no layout includes them.
- **Emoji as UI chrome** (banned): country flags + `🌐` globe fallback in every
  server row (`FlagUtil.kt`), `✕` as a close button, `↑`/`↓` as speed labels.
- **Nested containers**: `item_buy_tariff` is a card whose `ll_price_options`
  contains outlined option tiles (a card-in-card read at 14dp radius inside 20dp);
  `activity_bypass_list` puts a card header above a list of cards.
- **Zero `Snackbar`s**, ~40 `Toast`s + one custom deprecated Toast.
- **Menus**: the shell has no toolbar, yet six `res/menu/*.xml` action menus are
  still inflated by layer-B/C activities — action overflow exists only on screens the
  redesign never touched.

---

## 4. Blunt verdict per screen

Legend — **KEEP**: ships as-is (may need token cleanup only). **RESTYLE**: structure
is right, visual language must be replaced. **REBUILD**: structure itself is wrong.

| # | Screen | Verdict | Reason |
|---|---|---|---|
| 1 | Home tab | **REBUILD** | Owner said it looks bad and he is right. The hero is a 230dp stack of a banned glow + banned page gradient + a 176dp round card + a spinner + a sonar ring — five layers to say "off". The stats row uses text arrows and an invisible spacer to fake centring. Account chip, connect hero, memory card, welcome heading, onboarding card, carousel and list are seven unrelated blocks with no rhythm. Empty and populated states are two different screens sharing one file. |
| 2 | Home empty / sign-in card | **REBUILD** | This is the first-run screen for every user and it is a card floating on a gradient with two filled buttons competing for the same tap, a 12sp «или войдите» divider, and 26dp pill radii that appear nowhere else. Owner explicitly called this out. |
| 3 | Subscription meta bar | **REBUILD** | An 11sp label printed on top of a moving progress fill (2.9 : 1 contrast), four 36dp buttons, a hidden destructive long-press, and a height computed by measuring every page on every rebuild. The information (plan, traffic, expiry, support) is right; the container is wrong. |
| 4 | Servers tab | **RESTYLE** | Structure (header + search + grouped list) is correct and worth keeping. Needs: 48dp targets, tokenised header, a real selected state that survives mono, and the **restored** row actions (§5.1). |
| 5 | Server row | **RESTYLE** | Good density. Replace emoji flags with real assets or a typographic country tile, fix the 4.0 : 1 chip, delete the zero-size `layout_indicator`, add long-press back. |
| 6 | Settings tab | **REBUILD** | 1 536 lines of copy-paste for 20 rows, while two correct reusable row components sit unused. It also hides ~30 real settings that exist in code and exposes «Автообновление подписки» twice. Must become a data-driven list. |
| 7 | Account tab | **KEEP (restyle lightly)** | The best screen in the app: one hero, one section header, four designed states, correct tokens, correct empty/error copy. Fix only the 72dp divider inset, the 52dp avatar container vs 48dp circle mismatch, and the `.Title` used as a row label. |
| 8 | Login | **REBUILD** | Two stacked cards, four buttons, two spinners, one error line, three intent-driven shapes. Owner asked for this specifically. Needs to become one focused screen with one primary path. |
| 9 | Buy subscription | **RESTYLE** | State machine is complete and correct; the card-in-card price options, 26dp/22dp radii and 40dp stepper buttons are the problems. |
| 10 | Devices | **KEEP (restyle lightly)** | Clean. Fix the 44dp delete target and stop shipping a "screenshot the raw server response" diagnostic dialog to end users. |
| 11 | Payment history | **RESTYLE** | Fix the dead `btn_history_buy`, merge the empty/error block into two real states, and stop tinting status chips in four hues. |
| 12 | Local proxy | **REBUILD** | 1 035 lines, 112 hardcoded dp, 37 raw text sizes, zero tokens, five sections of developer plumbing (memory limit chips, SOCKS credentials, hotspot endpoint) presented to consumers. |
| 13 | Provider settings | **REBUILD** | 648 lines, 66 hardcoded dp, and it duplicates a Settings-tab row. Should be merged into the settings model, not kept as a parallel screen. |
| 14 | URL schemes | **REBUILD or CUT** | A `depv://` cheat-sheet in the consumer settings tree. If it stays it must become one "for automation" disclosure block, not five section cards. |
| 15 | Backup | **RESTYLE** | Right idea, layer-B metrics, and a WebDAV dialog that is four unlabelled `EditText`s. |
| 16 | Routing list | **RESTYLE** | Structure fine; five actions buried in a toolbar overflow that the rest of the app doesn't have. |
| 17 | Routing rule editor | **REBUILD** | Raw upstream form. No cards, no tokens, no brand font, `Spinner`s. |
| 18 | Assets + Add URL | **RESTYLE** | Layer-B card list; the URL screen is raw upstream. |
| 19 | Per-app proxy | **RESTYLE** | Good bones (header card + searchable list). 18dp radius, 15sp/12sp raw sizes, 6-item overflow menu. |
| 20 | App picker | **REBUILD** | A 10-line bare `RecyclerView`. No header, no empty state, no title logic beyond `resolveScreenTitle()`. |
| 21 | TV send | **RESTYLE** | Hardcoded 16/20dp, but the flow is sound. |
| 22 | TV receive | **KEEP** | Overscan-safe, landscape-locked, TV-only. Low value to redesign; just tokenise. |
| 23 | About | **REBUILD** | Pure 2018 v2rayNG. `TextAppearance.AppCompat.Subhead`, untinted 24dp icons, no cards. It is the last screen a user sees before deciding the app is amateur. |
| 24 | Scanner | **REBUILD** | An empty `RelativeLayout`. No frame, no instruction, no branding, no torch, menu items with empty titles. |
| 25 | Server editors (vmess/vless/trojan/ss/socks/hysteria2/wireguard/custom/group/proxy-chain + TLS/transport/address-port includes) | **REBUILD or CUT** | Currently **unreachable** (§5.1). 13 layouts of raw upstream forms. Decide: either restore access and rebuild them as one tokenised form system, or delete them and own "subscription-only". |
| 26 | Sub setting / Sub edit | **CUT or REBUILD** | **Unreachable.** `activity_sub_edit.xml` is 291 lines of upstream form; `item_recycler_sub_setting.xml` is a decent layer-A card with no entry point. |
| 27 | Legacy `SettingsActivity` (`pref_settings.xml`) | **CUT** | Unreachable, 354 lines of `PreferenceScreen`, ~30 settings the new tab never surfaces. Either migrate the settings into the new model or delete both. Leaving it is the worst option. |
| 28 | Check update | **CUT** | Unreachable, upstream-styled, and this build is not distributed via GitHub releases. |
| 29 | Logcat | **CUT or gate** | Unreachable. If kept, put it behind a developer disclosure in About. |
| 30 | Tasker | **KEEP** | External integration only, `title=""`, nobody sees it. Give it a title, leave it. |
| 31 | Deep-link handler + shortcut stubs | **KEEP (harden)** | No UI by design. But `depv://import/{base64}` mutates the server list with no confirmation — needs a confirm sheet, which is a design task. |
| 32 | Server-actions sheet | **REBUILD + REWIRE** | Well designed, correctly tokenised, **completely dead** (§5.1). |
| 33 | Payment-method sheet | **KEEP** | Correct grammar, correct tokens, correct states. |
| 34 | Status toast | **REBUILD** | Deprecated custom-view Toast, bottom-anchored at a magic 110dp offset, competing with the nav bar. Should be a `Snackbar` anchored above the bar, or an in-hero state line. |
| 35 | Dialogs (18 of them) | **RESTYLE** | `ThemeOverlay.Departament.Dialog` is good work. But single-choice `AlertDialog`s are the interaction pattern for Режим / DNS / Пинг / Оформление / Язык / Автообновление — six pickers that should be inline segmented controls or push screens, and two error strings are hardcoded Russian literals in Kotlin. |
| 36 | Widget / QS tile / notification | **RESTYLE** | `widget_switch.xml` is a 45dp icon + white `AppCompat.Small` label. Not touched by the redesign at all. |

---

## 5. Dead, unreachable and duplicated surfaces

### 5.1 The server-actions regression (P0 for the redesign baseline)

`MainActivity.kt:610-611` assigns:
```kotlin
serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }
homeAdapter.onItemLongClick    = { guid -> showServerActions(guid) }
```
`MainRecyclerAdapter.kt:56` declares `onItemLongClick` and its own comment says
*"Retained for host-activity API compatibility … no longer invoked by the adapter."*
`bindServer()` (line 213) sets **only** `setOnClickListener`.

Consequences, all currently true in the shipping build:
- The `ServerActionsSheet` never opens.
- `editServer()` has no caller → `ServerActivity`, `ServerCustomConfigActivity`,
  `ServerGroupActivity`, `ServerProxyChainActivity` are unreachable.
- `shareServer()` / `showQRCode()` / `share2Clipboard()` / `shareFullContent()` /
  `removeServer()` / duplicate / set-default are unreachable **per server**.
- `MainAdapterListener.onShare/onEdit/onRemove` implementations in
  `ActivityAdapterListener` (lines 1419-1445) are dead.
- `importManually()` (line 1972) is dead — `menu_main.xml` routes
  `import_manually_vless` to `showManualEntryDialog()` instead.
- `locateSelectedServer()` (line 2276) has no caller.

A user therefore **cannot delete, rename, share or edit a single server** from the UI.

### 5.2 Settings that exist in code but not in the UI

`res/xml/pref_settings.xml` (354 lines) is loaded only by `SettingsActivity`, which
nothing launches. Its ~48 preferences overlap the Settings tab in ~12 cases; the
remaining ~30 are unreachable, including several that the *Home tab reads*:
`PREF_SHOW_MEMORY` (gates `card_memory`), `PREF_AUTO_FALLBACK` (gates the post-connect
health check, `MainActivity.kt:1880`), `PREF_CONFIRM_REMOVE` (gates the delete
confirm dialog, line 1381). Features ship with hidden switches.

### 5.3 Orphan resources

Drawables with zero references: `bg_nav_header.xml`, `nav_header_bg.png`,
`bg_acc_option.xml`, `bg_speed_chip.xml`, `ripple_card.xml`, `bg_chip_gold.xml`,
`ic_circle.xml`.
Fonts: `res/font/montserrat_thin.ttf` — **152 KB of a second, unused typeface.**
Layouts: `layout_setting_row.xml`, `layout_setting_toggle_row.xml`,
`item_recycler_footer.xml` (bound but renders an `invisible` empty row).
Styles/resources: `res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml`,
`style/BottomNavIndicator`, `style/TabLayoutTextStyle`, `style/BrandedSwitch`
(only `activity_check_update`, itself unreachable).

### 5.4 Localisation state

`values/strings.xml` holds **469 strings: 392 latin-only, 76 Cyrillic** — the
departament additions were written straight into the *default* (English) resource
file. `values-ru/strings.xml` has 444. Result: on a non-Russian device the app shows
**English chrome with Russian product copy mixed in** — e.g. `bottom_nav_settings` is
«Настройки» in the default file while `bottom_nav_home` is "Home".
The design law says "Russian sentence-case copy"; the *default* locale must be made
Russian (or the app locked to `ru`) or half the redesigned copy will never be seen.
Additional Russian literals are hardcoded in Kotlin: `MainActivity.kt:2016`, `:2018`,
`:2101`, `:2103`, `:2113`, `:2115`.

---

## 6. What is genuinely good (do not regress it)

- **The token files themselves.** `dimens.xml`, `motion.xml`, `styles.xml`,
  `attrs.xml` and the day/night/mono colour system are well-designed. The problem is
  adoption, not definition.
- **The mono theme** — a runtime `ThemeOverlay` that neutralises every accent attr.
  Genuinely clever, and it works because the accents are attrs, not hex.
- **`ThemeOverlay.Departament.Dialog`** — every `AlertDialog` in the app is themed
  with zero Kotlin, via `alertDialogTheme`/`materialAlertDialogTheme`.
- **Section headers are sentence-case bold** (`SettingsSectionLabel`), not tracked
  ALL-CAPS eyebrows. The ban is respected.
- **Reduced-motion is honoured everywhere** — `reducedMotion()` guards the hero
  assemble, the connect confirm, the tab fade-through, the list stagger, the balance
  count-up and the skeleton pulse.
- **`AccountFragment`'s four-state hero** is the reference for how every other
  screen should handle loading / empty / content / error.
- **The connect state machine** (`applyRunningState`, watchdog, one-shot event
  consumption, live-transition gating so LiveData replay does not re-animate) is
  careful, correct work.
- **No `dangerouslySetInnerHTML`-equivalent**: no raw HTML rendering, no
  `WebView` in the product surface.

---

## 7. Numbers to measure the redesign against

| Metric | Now |
|---|---|
| Layout files | 73 (11 949 lines) |
| Largest layout | `layout_settings_content.xml` — 1 536 lines for 20 rows |
| Largest Activity | `MainActivity.kt` — 2 777 lines |
| Layouts with zero `TextAppearance.App.*` | 34 / 73 |
| Layouts with zero spacing tokens but ≥1 hardcoded dp | 24 |
| Hardcoded padding/margin dp literals (top 3 files) | 112 + 66 + 57 = 235 |
| Raw `android:textSize` occurrences | ~100 across 21 files |
| Distinct corner radii shipped | 10 (2, 8, 12, 14, 18, 20, 22, 24, 26, 88) |
| Distinct divider left-insets | 3 (44, 68, 72) |
| Touch targets below 48dp | 8 distinct components |
| Unreachable screens | 14 |
| Orphan drawables / fonts / layouts | 7 / 1 / 3 |
| `Snackbar` usages | 0 |
| Banned decorative gradients/glows in use | 6 drawables |
| Default-locale strings that are Russian | 76 / 469 |

---

*End of inventory. Next document: `02-…` — the target information architecture and
the single 2026 design system both platforms will be rebuilt on.*
