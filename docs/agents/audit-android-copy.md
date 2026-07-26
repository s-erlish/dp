# Android copy / localisation / accessibility audit

**Scope:** `/home/user/dp/V2rayNG/app/src/main/**` (res + Kotlin).
**Method:** full read of `values/strings.xml`, `values-ru/strings.xml` and the 18 additional
`values/strings_*.xml` files; key-set diff across all 9 `values-*` locale dirs; regex scan of all
`.kt` sources for user-facing literals; XML parse of all 73 layouts for `contentDescription`,
`labelFor`, declared touch-target sizes; WCAG contrast maths on the palettes in
`values/colors.xml` + `values-night/colors.xml`.
Every claim below is anchored to `file:line`.

---

## Executive summary

The departament copy that was written for this fork is genuinely good — calm, sentence-case,
active. The problems are almost entirely **structural**, and they cluster into five defects:

1. **The default locale (`values/`) is half-Russian.** 66 keys are Russian in `values/` and the app
   still ships an "English" option in its own language picker
   (`res/values/arrays.xml:139-143`, picker at `MainActivity.kt:2804-2809`). Anyone who picks
   English — or whose device is Chinese/Arabic/Persian/Vietnamese — gets a UI that is half Russian.
2. **`values-ru/` is 33 strings behind `values/`**, and 18 of the departament string files
   (`strings_account.xml`, `strings_auth.xml`, `strings_buy.xml`, …) have **no** `values-ru`
   counterpart at all. Only `strings_tv.xml` was mirrored.
3. **~20 user-facing strings are hardcoded in Kotlin**, including four Russian toasts duplicated
   verbatim in two files, and two English dialog strings in `AboutActivity`.
4. **Zero `<plurals>` in the entire resource tree.** The Servers tab literally renders
   `1 серверов · 1 провайдеров`.
5. **Zero `android:labelFor` in 73 layouts.** 57 `EditText`s have no accessible name at all.

Severity legend: **P1** user-visible defect on a main screen · **P2** user-visible but secondary ·
**P3** correctness/consistency debt.

---

## A. User-facing strings hardcoded in Kotlin / XML

### A1 — Four Russian toasts duplicated verbatim in two files **(P1)**

The entire import-result feedback block exists twice, hardcoded, with no resource:

| Text | Locations |
|---|---|
| `"Серверы добавлены: $loaded"` | `ui/MainActivity.kt:2216`, `ui/ScScannerActivity.kt:28` |
| `"Не удалось загрузить серверы подписки"` | `ui/MainActivity.kt:2218`, `ui/ScScannerActivity.kt:30` |
| `"Подписка уже добавлена"` | `ui/MainActivity.kt:2228`, `ui/ScScannerActivity.kt:34` |
| `"Эта ссылка не от departament. Используйте подписку из нашего бота."` | `ui/MainActivity.kt:2230`, `ui/ScScannerActivity.kt:35`, `ui/SubEditActivity.kt:166` |

The last one is in **three** places. Any wording fix has to be made three times or the app
contradicts itself depending on whether you scanned a QR, pasted, or edited a subscription.

### A2 — Manual-entry dialog validation errors **(P1)**

`ui/MainActivity.kt:2131` and `:2133-2134`:

```kotlin
text.isEmpty() ->
    input.error = "Вставьте ссылку подписки или конфигурацию сервера"
!looksImportable(text) ->
    input.error = "Не похоже на ссылку или конфигурацию. " +
            "Пример: https://departament.example/sub или vless://…"
```

Both hardcoded. The second also leaks a fake example domain (`departament.example`) into user copy.

### A3 — English literals in `AboutActivity` **(P1)**

`ui/AboutActivity.kt:31,33`:

```kotlin
.setTitle("Open source licenses")
.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
```

A Russian user opening «О приложении» → «Лицензии открытого исходного кода» gets a dialog titled
**"Open source licenses"**. The correct resource already exists: `title_oss_license` =
«Лицензии открытого исходного кода» (`values-ru/strings.xml:302`).

### A4 — A debug placeholder reachable by the user **(P2)**

`ui/SubSettingActivity.kt:155`:

```kotlin
else -> ownerActivity.toast("else")
```

The `else` branch of the share-method dialog shows a toast reading literally `else`.

### A5 — Byte/speed units hardcoded in Latin, and implemented twice in two languages **(P1)**

`extension/_Ext.kt:82` and `:90`:

```kotlin
fun Long.toSpeedString(): String = this.toTrafficString() + "/s"
...
val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
```

This is what renders the home-screen speed counters and the subscription traffic pill. A Russian
user sees `1.7 GB`, `340.0 KB/s`.

Meanwhile `ui/BuyTariffActivity.kt:649-650` has a **second, independent** formatter:

```kotlin
if (bytes <= 0L) return "0 Б"
val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
```

So the same app shows `ГБ` on the buy screen and `GB` on the home screen. The subscription meta
bar's own design placeholder assumed Cyrillic —
`layout/layout_subscription_meta_bar.xml:176` has `tools:text="1,7 ТБ / ∞"` — which the real
formatter never produces.

### A6 — Ping unit hardcoded **(P2)**

`dto/entities/ServerAffiliationInfo.kt:8`:

```kotlin
return testDelayMillis.toString() + "ms"
```

Every server row shows `84ms`. The Russian resource for the same unit is «мс»
(`values-ru/strings.xml:404`, `connection_test_available`). Two different unit spellings for one
quantity.

### A7 — Protocol chip labels hardcoded in English **(P2)**

`ui/MainRecyclerAdapter.kt:240,241,245`:

```kotlin
EConfigType.POLICYGROUP -> "Auto"
EConfigType.PROXYCHAIN  -> "Chain"
EConfigType.CUSTOM      -> customProtoInfo(guid)?.protocol?.uppercase() ?: "Custom"
```

Russian users see `Auto` / `Chain` / `Custom` chips on the server list.

### A8 — Currency symbol mapping duplicated three times, and they disagree **(P2)**

- `ui/BuyTariffActivity.kt:640-644` — `"USD" -> "$"`
- `ui/AccountFragment.kt:678-684` — `"USD"` falls into `else -> "₽"` (deliberate, documented)
- `ui/adapter/PaymentsAdapter.kt:92-96` — third copy

An account whose backend currency is `USD` therefore renders `500 $` on the buy screen and
`500 ₽` on the account screen.

### A9 — Raw developer diagnostics shown in a user dialog **(P2)**

`ui/AccountFragment.kt:474-481` and `ui/BuyTariffActivity.kt:522-530` build a code string from
`"401/403"`, `"429"`, `"502/503"`, `"timeout"`, `"network"`, `"—"` and feed it into
`account_payment_error_body` = `HTTP %1$s\n%2$s` (`values/strings_account.xml:99`) under the title
«Ошибка оплаты». The `detail` half comes straight from the backend and is frequently English. The
comment at `AccountFragment.kt:464-465` says this is intentional ("so it can be screenshotted"),
but as shipped it is a dead end for the user: no cause, no next step.

### A10 — Smaller hardcoded literals

| Literal | Location | Note |
|---|---|---|
| `"00:00:00"` | `ui/MainActivity.kt:1966`, `layout/activity_main.xml:124` | uptime reset placeholder |
| `"QR Code"` | `ui/MainActivity.kt:1376` | English fallback `contentDescription` |
| `"$usedDevices / $totalDevicesStr"` | `ui/AccountFragment.kt:332` | no resource, no RTL-safe formatting |
| `"••••••••"` | `ui/SubEditActivity.kt:58` | masked URL; TalkBack reads eight bullets |
| `"  •  "` separator | `ui/UserAssetAdapter.kt:45` | every other screen uses `" · "` (`MainActivity.kt:841`, `MainRecyclerAdapter.kt:256,265`, `DeviceAdapter.kt:47`, `BuyTariffActivity.kt:312`) |
| `"✕"` | `layout/layout_home_account.xml:71` | glyph-as-button |
| `"?"` | `layout/layout_home_account.xml:105`, `layout/activity_account.xml:74` | avatar initial placeholder, not hidden from TalkBack |
| `"↑"` / `"↓"` | `layout/activity_main.xml:98,140` | speed direction glyphs, read aloud by TalkBack |
| `"40"…"150"` | `layout/activity_local_proxy.xml:111,124,137,150,163` | memory presets with **no unit** — the string `lp_memory_unit` = «МБ» (`values/strings_local_proxy.xml:16`) is defined and **never used anywhere** |
| `"🌐"` | `util/FlagUtil.kt:19` | emoji used as UI chrome in the server row (`MainRecyclerAdapter.kt:201`) and in the notification title (`NotificationManager.kt:143`) — violates the "no emoji as UI chrome" rule in `CLAUDE.md` |

### A11 — A wrong string used as a layout default **(P2)**

`layout/activity_main.xml:305` — the under-shield status label ships with

```xml
android:text="@string/title_file_chooser"
```

`title_file_chooser` is «Выберите профиль» (`values-ru/strings.xml:131`) — the *file picker* title.
It is what renders for the first frames before `updateSelectedServer()`
(`MainActivity.kt:2144-2149`) overwrites it. The intended default is `home_select_server`
(«Выберите сервер»).

---

## B. Russian copy defects

### B1 — 33 keys missing from `values-ru/strings.xml` **(P1)**

`values/strings.xml` defines 472 strings; `values-ru/strings.xml` defines 439. Missing keys
(excluding `translatable="false"` ones, which are fine):

```
home_welcome_title      home_or_sign_in         home_buy_subscription   home_link_telegram
title_pref_color_theme  title_pref_ping_method  ping_method_real        ping_method_http
ping_method_tcp         ping_method_icmp        menu_item_fast_connect  memory_app_usage
memory_normal           memory_elevated         memory_high             title_pref_show_memory
summary_pref_show_memory color_theme_blue       color_theme_mono        auth_sign_in_telegram
auth_account            auth_subscription_active auth_subscription_active_until
auth_subscription_expired auth_subscription_none
```
plus the string-array `browser_dialer_mode`.

The `home_*` and `auth_*` ones happen to work because `values/` already holds Russian text — that
is luck, not design. The `memory_*`, `ping_method_*` and `color_theme_*` ones are **English in
`values/`** and therefore ship as English to Russian users (section C).

### B2 — 18 departament string files were never mirrored to `values-ru/` **(P1)**

`values/` contains 28 XML files; `values-ru/` contains **two** (`strings.xml`, `strings_tv.xml`).
Not mirrored: `strings_account.xml` (108 lines), `strings_auth.xml`, `strings_buy.xml`,
`strings_deeplink.xml`, `strings_devices.xml`, `strings_history.xml`, `strings_home_shell.xml`,
`strings_local_proxy.xml`, `strings_manual_add.xml`, `strings_nav.xml`, `strings_pay.xml`,
`strings_perapp.xml`, `strings_provider.xml`, `strings_server_actions.xml`,
`strings_settings_hub.xml`, `strings_templates.xml`, `strings_ui_polish.xml`.

Two consequences:
- Every account / auth / buy / devices / local-proxy screen renders Russian **from the default
  locale**, so it stays Russian even when the user picks English.
- There is no place to put an English translation without restructuring.

### B3 — 66 keys are Russian in `values/` **and** duplicated in `values-ru/`; one has already
drifted **(P2)**

Diffing the 66 duplicated keys shows exactly one divergence today:

| key | `values/strings.xml:14` | `values-ru/strings.xml:12` |
|---|---|---|
| `home_empty_title` | `У вас пока не добавлены подписки.` | `Пока нет подписок` |

Two different empty-state headlines for the same screen, plus an inconsistent trailing period
(`home_empty_subtitle` next to it has none). The Russian device shows the second; anyone on English
shows the first. This is the failure mode the other 65 duplicates are queued up for.

### B4 — The only ALL-CAPS Russian string **(P2)**

`values/strings_account.xml:41`:

```xml
<string name="account_trial_badge">ПРОБНЫЙ</string>
```

`CLAUDE.md` bans ALL-CAPS eyebrows. (It is currently **unreferenced** — grep over `java/` and
`res/` finds only the definition — so this is a latent violation, but it should be fixed or deleted
before someone wires it up.)

### B5 — Machine-translated / stilted Russian **(P1–P2)**

| # | Key & line | Current | Problem |
|---|---|---|---|
| 1 | `app_tile_first_use` `values-ru/strings.xml:31` | «Первое использование этой функции, пожалуйста, используйте приложение, чтобы добавить профиль» | Word-for-word MT. No verb, no subject, "пожалуйста" mid-sentence. This is the **Quick Settings tile** text — a first-run touchpoint. |
| 2 | `title_pref_promotion` `values-ru/strings.xml:305` | «Содействие» | Mistranslation of "Promotion". Means "assistance/collaboration" in Russian; conveys nothing. |
| 3 | `toast_fragment_not_available` `values-ru/strings.xml:371` | «Фрагмент недоступен» | Leaks the Android class name *Fragment* to end users. The English source is "Unable to locate current view". |
| 4 | `migration_success` / `migration_fail` `values-ru/strings.xml:34,36` | «Успешный перенос данных!» / «Перенос данных не выполнен!» | Exclamation marks; nominal style ("Успешный перенос"), not active voice. |
| 5 | `pull_down_to_refresh` `values-ru/strings.xml:37` | «Потяните вниз для обновления!» | Exclamation mark on an instruction. |
| 6 | `toast_none_data` `values-ru/strings.xml:128` | «Ничего нет» | Says nothing about *what* is missing or what to do. |
| 7 | `summary_pref_local_dns_enabled` `values-ru/strings.xml:220` | «…рекомендуется выбрать режим «Все, кроме LAN и Китая»» | References a routing preset that **does not exist** in this build. The actual presets are in `values/strings.xml:524-530`: «Базовый набор», «Прокси для заблокированного», «Весь трафик через прокси», «Белый список Ирана», «Белый список России». Stale upstream text. |
| 8 | `title_pref_mux_concurency` / `..._xudp_concurency` `values-ru/strings.xml:202,203` | «(диапазон от **1** до 1024)» | The English source says `range -1 to 1024` (`values/strings.xml:208,209`). The Russian states a factually different valid range. |
| 9 | `sub_setting_filter` `values-ru/strings.xml:336`, `title_policy_group_subscription_filter` `:430` | «Название фильтра» | Reverses the meaning. Source is "Remarks regular filter" — a regex applied *to* names, not the filter's own name. |
| 10 | `sub_setting_pre_profile_tip` `values-ru/strings.xml:342` | «Профиль должен быть уникальным» | Source: "The config remarks exist and are unique". Also used as the `android:hint` for **two different fields** (`layout/activity_sub_edit.xml:223,264`). |
| 11 | `routing_settings_tips` `values-ru/strings.xml:380` | «Через запятую (,)\nЧто-то одно: домен, IP или процесс» | "Что-то одно" is spoken register, not interface copy. Also reused as the hint for three different fields (section D3). |
| 12 | `connection_connected` `values-ru/strings.xml:408` | «Соединено, нажмите для проверки» | «Соединено» is not idiomatic for a connection state; «Подключено». |

### B6 — Voice inconsistency: «профиль» vs «сервер» **(P1)**

The fork's new copy consistently says **сервер**:
`title_servers` «Сервера» (`values-ru:4`), `servers_count` «%d серверов» (`:5`),
`home_select_server` «Выберите сервер» (`:17`), `server_actions_title` «Действия с сервером»
(`values/strings_server_actions.xml:8`).

The inherited copy consistently says **профиль**:
`title_server` «Профиль» (`values-ru:56`), `menu_item_add_config` «Добавить профиль» (`:57`),
`title_del_all_config` «Удалить профили» (`:328`), `title_ping_all_server` «Проверить профили»
(`:357`), `toast_server_not_found_in_group` «Выбранный профиль не найден в текущей группе» (`:370`),
`title_locate_selected_config` «Найти выбранный профиль» (`:372`), `filter_config_all` … and ~20
more.

Both sets are live in the same session: tapping the ping icon in the servers header
(`layout_servers_header.xml:53-63`) speed-tests the things the header just called «серверов» and
reports «Проверка профилей (12)» (`values-ru:403`). Pick one noun.

Related smaller splits:
- `menu_add_tv_send` «Отправить на **TV**» (`values-ru:24`) vs `settings_tv_send` «Перенести
  подписку на **ТВ**» (`values-ru:564`) — Latin and Cyrillic abbreviation in the same app.
- `lp_section_socks` «SOCKS5-авторизация» (`values/strings_local_proxy.xml:8`) vs `lp_socks_auth`
  «SOCKS5 авторизация» (`:19`) — same screen, hyphen present then absent.
- `settings_value_off` «Выкл» (`values/strings.xml:610`) and `ps_value_off` «Выкл`
  (`values/strings_provider.xml:45`) — duplicate resource for identical text.

### B7 — «Сервера» **(P3)**

`title_servers` (`values/strings.xml:5`, `values-ru:4`) and `bottom_nav_servers` (`values-ru:516`)
use «Сервера». That is the colloquial/professional-jargon plural; the literary form is «Серверы».
For a product whose voice is "calm, concrete", «Серверы» is the safer register.

### B8 — No plurals anywhere **(P1)**

`grep -c "<plurals"` over `res/` returns **0**. The Servers tab subtitle is built at
`ui/MainActivity.kt:840-842`:

```kotlin
getString(R.string.servers_count, serverCount) + " · " +
    getString(R.string.providers_count, maxOf(distinctProviders, 0))
```

with `servers_count` = `%d серверов` and `providers_count` = `%d провайдеров`
(`values-ru/strings.xml:5,6`). A user with one subscription and one server reads:

> **1 серверов · 1 провайдеров**

Russian needs three forms (1 / 2-4 / 5+). Same class of bug in
`connection_test_testing_count` (`:403`), `title_del_config_count` (`:364`),
`title_import_config_count` (`:365`), `account_promo_free_days`
(`values/strings_account.xml:65`), `tv_receive_success` (`values/strings_tv.xml:15`).

---

## C. English leftovers a Russian user actually sees

### C1 — The home-screen memory card **(P1)**

`values/strings.xml:355-361` — none of these have a `values-ru` override:

```xml
<string name="memory_app_usage">App memory</string>
<string name="memory_value" translatable="false">%1$d MB · %2$s</string>
<string name="memory_normal">Normal</string>
<string name="memory_elevated">Elevated</string>
<string name="memory_high">High</string>
```

Rendered by `layout/activity_main.xml:352` and `ui/MainActivity.kt:1913-1918`. With the memory card
enabled, a Russian user's home screen reads:

> **App memory**
> 148 MB · Elevated

`memory_value` is additionally marked `translatable="false"`, so "MB" is permanently Latin even if
someone adds the other four.

### C2 — Byte / speed / ping units **(P1)**

`0 KB/s` at rest (`values/strings.xml:334`, `speed_zero`, `translatable="false"`, painted at
`MainActivity.kt:1725-1726`), then `340.0 KB/s` / `1.7 GB` live (`extension/_Ext.kt:82,90`), and
`84ms` per server row (`ServerAffiliationInfo.kt:8`). See A5/A6.

### C3 — Browser-dialer spinner **(P2)**

`ui/ServerActivity.kt:89` reads `R.array.browser_dialer_mode`, which exists only in
`values/strings.xml:512-516` (`Disable` / `OkHttp` / `WebView`). No `values-ru` array → English
spinner in the Russian manual-server editor.

### C4 — About dialog **(P1)** — see A3.

### C5 — Protocol chips `Auto` / `Chain` / `Custom` **(P2)** — see A7.

### C6 — Dead-but-English preference screen **(P3)**

`title_pref_color_theme`, `title_pref_ping_method`, `ping_method_*`, `color_theme_*`,
`title_pref_show_memory`, `summary_pref_show_memory` are English-only and referenced solely from
`res/xml/pref_settings.xml`, loaded by `ui/SettingsActivity.kt:65`. Nothing launches
`SettingsActivity` — grep for `SettingsActivity::class` over `java/` returns no `startActivity`
call, only the comment at `MainActivity.kt:2427`. So these are currently unreachable, but the
activity is still exported in `AndroidManifest.xml:89` and an intent could reach it.

### C7 — The inverse problem: Russian leaking into every other locale **(P1)**

`values/` — the **default** locale, used for every language without its own override — contains 66
Russian strings, plus the whole of `strings_account/auth/buy/devices/deeplink/local_proxy/nav/pay/
perapp/provider/server_actions/settings_hub/templates/ui_polish.xml`.

Key-set check over the locale dirs (13 representative departament keys):

| locale | strings defined | departament keys missing |
|---|---|---|
| `values` | 484 | 0 |
| `values-ru` | 450 | 2 |
| `values-ar` | 352 | 13 / 13 |
| `values-vi` | 352 | 13 / 13 |
| `values-bn` | 353 | 12 / 13 |
| `values-fa` | 352 | 12 / 13 |
| `values-bqi-rIR` | 352 | 12 / 13 |
| `values-zh-rCN` | 355 | 12 / 13 |
| `values-zh-rTW` | 353 | 12 / 13 |

So a Chinese user gets a Chinese UI with «Сервера», «Подключение», «Аккаунт», «Купить подписку»
spliced in. And the app's *own* language picker offers English —
`res/values/arrays.xml:139-143` (`Системный` / `Русский` / `English`), dialog at
`ui/MainActivity.kt:2804-2809`, resolved at `handler/SettingsManager.kt:513-528` — with the same
result. Either drop the non-Russian locales and the picker, or move the 66 Russian strings out of
`values/` into `values-ru/` and put English in `values/`.

---

## D. Accessibility

### D1 — `android:labelFor` is used **zero times** in 73 layouts; 57 `EditText`s have no accessible name **(P1)**

Parsed every layout: 72 `EditText`/`TextInputEditText` nodes, of which **57** have no `hint`, no
`contentDescription`, and no `labelFor` pointing at them.

| layout | unlabelled fields |
|---|---|
| `activity_local_proxy.xml` | 7 |
| `layout_tls.xml` | 7 |
| `activity_server_hysteria2.xml` | 6 |
| `activity_server_wireguard.xml` | 6 |
| `layout_transport.xml` | 6 |
| `activity_sub_edit.xml` | 5 |
| `layout_address_port.xml` | 3 |
| `activity_routing_edit.xml`, `activity_server_group.xml`, `activity_server_socks.xml`, `activity_server_vless.xml`, `activity_user_asset_url.xml`, `layout_tls_hysteria2.xml` | 2 each |
| `activity_server_custom_config.xml`, `activity_server_shadowsocks.xml`, `activity_server_trojan.xml`, `activity_server_vmess.xml`, `dialog_config_filter.xml` | 1 each |

The pattern is always the same — a visible label `TextView` immediately above the field, with no
`labelFor` linking them. Example, `layout/activity_server_vless.xml:24-33`:

```xml
<TextView android:text="@string/server_lab_id" />       <!-- no android:id, no labelFor -->
<EditText android:id="@+id/et_id" android:inputType="text" />   <!-- no hint -->
```

TalkBack announces this as *"Edit box, double-tap to edit"* — no name. Fix is mechanical: give each
label an `@+id` and add `android:labelFor="@id/et_…"`.

`activity_login.xml` is the exception and the model to copy — `TextInputLayout` with `android:hint`
at `:152`, `:172`, `:245`, plus `autofillHints` at `:159`, `:180`.

### D2 — Wrong `contentDescription` on interactive controls **(P1)**

Only 53 `contentDescription` attributes exist across all layouts, and several are wrong:

| Control | Location | `contentDescription` | Actual meaning |
|---|---|---|---|
| Collapse-all chevron | `layout/layout_servers_header.xml:30-39` | `@string/title_servers` = «Сервера» | "Свернуть все группы" |
| Subscription collapse chevron | `layout/layout_subscription_meta_bar.xml:30-42` | `@string/title_servers` = «Сервера» | "Свернуть подписку" |
| 9 × deeplink copy buttons | `layout/activity_url_scheme_list.xml:133,184,255,307,378,449,501,572,624` | `@string/url_scheme_copied` = «Скопировано» | "Скопировать ссылку" — the label is the *result* message, not the action |
| Connect shield | `layout/activity_main.xml:255`, set at `ui/MainActivity.kt:1654` and `:1722` | «Остановить службу» / «Запуск службы» | It is the app's single hero control; it should say «Отключиться» / «Подключиться», and `tasker_start_service` is a *Tasker plugin* string being reused for it |
| QR image | `ui/MainActivity.kt:1376` | `shareMethod.firstOrNull() ?: "QR Code"` | English fallback |

### D3 — One hint reused as the label for several distinct fields **(P2)**

- `layout/activity_routing_edit.xml:81`, `:101`, `:127` — the domain, IP and process fields all use
  `android:hint="@string/routing_settings_tips"`. TalkBack announces the same sentence for three
  different inputs.
- `layout/activity_sub_edit.xml:223`, `:264` — "previous profile" and "next profile" both use
  `android:hint="@string/sub_setting_pre_profile_tip"`.

### D4 — Touch targets below 48 dp **(P1)**

`CLAUDE.md` mandates ≥48 dp; `@dimen/row_min_height` is 56 dp (`values/dimens.xml:33`). No
`TouchDelegate` exists anywhere in `java/` (grep: 0 hits), so these declared sizes are the real hit
areas.

| Size | Controls |
|---|---|
| **36 dp** | `layout_servers_header.xml:29,41,53,65` — collapse-all, refresh-all, speed-test-all, add. These are the Servers tab's only header actions. |
| **36 dp** | `layout_subscription_meta_bar.xml:75,88,111,239` — ping, refresh, pin, Telegram. |
| **40 dp** | `layout_home_account.xml:62-73` — CTA dismiss "✕". |
| **42 dp** | `activity_main.xml:161` — home "+" add button; `activity_url_scheme_list.xml` × 9 copy buttons. |
| **44 dp** | `activity_local_proxy.xml` — 5 memory-preset buttons + 7 copy/reveal icon buttons; `item_device.xml:83` delete. |
| **~38 dp** | `item_section_header.xml` — the collapsible group header row is `clickable="true"` (`:10-11`) with `paddingTop=12dp` + `paddingBottom=4dp` around a 22 dp glyph and **no `minHeight`**. This is the primary way to fold server groups. |

Note `layout_subscription_meta_bar.xml:30-42` gets it right (48 dp box, 13 dp padding, 22 dp glyph)
and even documents the reasoning in a comment — the other icon buttons just weren't updated to
match.

### D5 — Contrast **(P2)**

Body/secondary text passes comfortably. Computed against `values-night/colors.xml`:

| Pair | Ratio | Verdict |
|---|---|---|
| `md_theme_onSurfaceVariant #9BA1AD` on `md_theme_surface #141619` | **7.15 : 1** | pass |
| same on `md_theme_background #0A0B0D` | **7.59 : 1** | pass |
| `md_theme_onPrimary #00183A` on `md_theme_primary #4C8DFF` | **5.51 : 1** | pass |
| light: `#54607A` on `#FFFFFF` | **6.30 : 1** | pass |

**One failure.** The traffic pill label `tv_traffic`
(`layout/layout_subscription_meta_bar.xml:163-176`) is `?attr/colorOnSurface` (`#F2F4F8`) at
**11 sp**, centred over a `ProgressBar` whose fill is `?attr/colorPrimary` (`#4C8DFF`,
`drawable/bg_traffic_gradient.xml:16-22`):

- text over the **blue fill**: **2.91 : 1** — fails 4.5 : 1 for small text
- text over the unfilled track (`colorSurfaceVariant #1E2126`): 14.7 : 1 — fine

So the label is legible on the empty part of the pill and not on the used part — and it drifts from
one to the other as the subscription is consumed. Either put the label outside the bar, or switch it
to `?attr/colorOnPrimary` and add a matching outline, or raise the bar height and use
`colorOnPrimary` throughout.

### D6 — Smaller a11y gaps **(P3)**

- `layout/layout_home_account.xml:100-108` and `layout/activity_account.xml:69-78` — the avatar
  initial `TextView` defaults to `"?"` and is **not** marked
  `importantForAccessibility="no"`, so TalkBack reads "question mark" before the account name.
- `layout/activity_main.xml:93-99,134-140` — the `↑` / `↓` speed-direction `TextView`s are read
  aloud as "up arrow" / "down arrow".
- `item_section_header.xml` — no `stateDescription`, so TalkBack never announces
  expanded/collapsed for the server-group headers.
- `layout/activity_main.xml:536-699` — the four bottom-nav items are plain `LinearLayout`s with
  `clickable="true"`; they never set `isSelected`/`stateDescription` for a11y, so the active tab is
  conveyed only by colour and a 3 dp dot.
- `layout/activity_local_proxy.xml:101-165` — the five memory presets announce as bare numbers
  ("40", "60"…) with no unit and no property name.

The 129 `ImageView`s without `contentDescription` are mostly decorative icons inside labelled rows;
the well-built ones already say so explicitly (`importantForAccessibility="no"` at
`layout_setting_row.xml:32`, `activity_main.xml:552,593,633,677`, `item_recycler_main.xml:47`).
Applying the same attribute to the remainder would silence the lint noise and make the real gaps in
D2 visible.

---

## E. Proposed Russian copy for the worst offenders

Voice: calm, concrete, active verbs, sentence case, no exclamation marks, no trailing period on
single-sentence labels.

### E1 — Hardcoded toasts → resources

Add to a new `values/strings_import.xml` (and mirror in `values-ru/`), then replace all duplicate
call sites (`MainActivity.kt:2216,2218,2228,2230`, `ScScannerActivity.kt:28,30,34,35`,
`SubEditActivity.kt:166`):

```xml
<plurals name="import_servers_added">
    <item quantity="one">Добавлен %d сервер</item>
    <item quantity="few">Добавлено %d сервера</item>
    <item quantity="many">Добавлено %d серверов</item>
    <item quantity="other">Добавлено %d сервера</item>
</plurals>
<string name="import_sub_no_servers">Подписка добавлена, но серверы не загрузились. Обновите её через минуту.</string>
<string name="import_sub_duplicate">Эта подписка уже добавлена</string>
<string name="import_sub_foreign">Ссылка не от departament. Возьмите подписку в нашем боте.</string>
```

Rationale: «Серверы добавлены: 12» is a log line. «Добавлено 12 серверов» is a sentence. The
failure case now says what happened *and* what to do instead of a dead end.

### E2 — Manual-entry validation (`MainActivity.kt:2131,2133-2134`)

```xml
<string name="manual_entry_empty">Вставьте ссылку на подписку или конфигурацию сервера</string>
<string name="manual_entry_invalid">Это не похоже на ссылку. Подписка начинается с https://, сервер — с vless://, vmess:// или ss://</string>
```

Drops the fake `departament.example` domain and names the accepted schemes concretely.

### E3 — Quick-settings tile (`values-ru/strings.xml:31`)

```xml
<!-- was: Первое использование этой функции, пожалуйста, используйте приложение, чтобы добавить профиль -->
<string name="app_tile_first_use">Сначала добавьте сервер в приложении departament</string>
```

### E4 — Home memory card (`values/strings.xml:355-359`, add to `values-ru/`)

```xml
<string name="memory_app_usage">Память приложения</string>
<string name="memory_value">%1$d МБ · %2$s</string>   <!-- drop translatable="false" -->
<string name="memory_normal">В норме</string>
<string name="memory_elevated">Повышенная</string>
<string name="memory_high">Высокая</string>
```

### E5 — Units (`extension/_Ext.kt:82,90`)

Move to resources and delete the duplicate in `BuyTariffActivity.kt:649-650`:

```xml
<string-array name="byte_units">
    <item>Б</item><item>КБ</item><item>МБ</item><item>ГБ</item><item>ТБ</item><item>ПБ</item>
</string-array>
<string name="speed_per_second">%1$s/с</string>
<string name="speed_zero">0 КБ/с</string>   <!-- was: 0 KB/s, translatable="false" -->
<string name="ping_ms">%1$d мс</string>      <!-- replaces ServerAffiliationInfo.kt:8 -->
```

### E6 — Servers header (`values/strings.xml:5-6`, `values-ru/strings.xml:4-5`)

```xml
<string name="title_servers">Серверы</string>
<plurals name="servers_count">
    <item quantity="one">%d сервер</item>
    <item quantity="few">%d сервера</item>
    <item quantity="many">%d серверов</item>
    <item quantity="other">%d сервера</item>
</plurals>
<plurals name="providers_count">
    <item quantity="one">%d провайдер</item>
    <item quantity="few">%d провайдера</item>
    <item quantity="many">%d провайдеров</item>
    <item quantity="other">%d провайдера</item>
</plurals>
```

### E7 — Connect-shield accessibility labels (`MainActivity.kt:1654,1722`)

Stop reusing the Tasker strings; add:

```xml
<string name="connect_cd_start">Подключиться</string>
<string name="connect_cd_stop">Отключиться</string>
<string name="connect_cd_connecting">Подключаемся</string>
```

### E8 — Icon-button `contentDescription`s (section D2)

```xml
<string name="cd_collapse_all">Свернуть все группы</string>
<string name="cd_expand_all">Развернуть все группы</string>
<string name="cd_collapse_subscription">Свернуть подписку</string>
<string name="cd_expand_subscription">Развернуть подписку</string>
<string name="cd_copy_link">Скопировать ссылку</string>   <!-- replaces url_scheme_copied on all 9 buttons -->
```

### E9 — Machine-translated settings copy

| Key | Was | Proposed |
|---|---|---|
| `title_pref_promotion` `values-ru:305` | Содействие | **Поддержать проект** |
| `toast_fragment_not_available` `values-ru:371` | Фрагмент недоступен | **Не удалось открыть экран. Попробуйте ещё раз** |
| `migration_success` `values-ru:34` | Успешный перенос данных! | **Данные перенесены** |
| `migration_fail` `values-ru:36` | Перенос данных не выполнен! | **Не удалось перенести данные** |
| `pull_down_to_refresh` `values-ru:37` | Потяните вниз для обновления! | **Потяните вниз, чтобы обновить** |
| `toast_none_data` `values-ru:128` | Ничего нет | **Здесь пока пусто** |
| `connection_connected` `values-ru:408` | Соединено, нажмите для проверки | **Подключено. Нажмите, чтобы проверить соединение** |
| `sub_setting_filter` `values-ru:336` | Название фильтра | **Фильтр по названию (регулярное выражение)** |
| `sub_setting_pre_profile_tip` `values-ru:342` | Профиль должен быть уникальным | **Укажите точное название существующего сервера** |
| `routing_settings_tips` `values-ru:380` | Через запятую (,)\nЧто-то одно: домен, IP или процесс | **Перечислите через запятую. В одном правиле — либо домены, либо IP, либо процессы** |
| `summary_pref_local_dns_enabled` `values-ru:220` | …режим «Все, кроме LAN и Китая» | **Запросы DNS обрабатывает ядро. Рекомендуем включить вместе с набором правил «Прокси для заблокированного»** |
| `title_pref_mux_concurency` `values-ru:202` | (диапазон от 1 до 1024) | **TCP-соединения (от −1 до 1024)** — matches `values/strings.xml:208` |
| `account_trial_badge` `values/strings_account.xml:41` | ПРОБНЫЙ | **Пробный** |
| `settings_tv_send` `values-ru:564` | Перенести подписку на ТВ | **Отправить подписку на TV** — aligns with `menu_add_tv_send` `values-ru:24` |
| `lp_socks_auth` `values/strings_local_proxy.xml:19` | SOCKS5 авторизация | **SOCKS5-авторизация** — matches `lp_section_socks` on the same screen |

### E10 — Memory presets (`activity_local_proxy.xml:111,124,137,150,163`)

Replace the bare numbers with the already-defined-but-unused unit
(`values/strings_local_proxy.xml:16`):

```xml
<string name="lp_memory_preset">%1$d МБ</string>
```

and set the text from `LocalProxyActivity` so both the visual label and TalkBack say "40 МБ".

### E11 — Payment error dialog (`AccountFragment.kt:474-489`, `BuyTariffActivity.kt:522-538`)

Keep the diagnostic, but lead with a human sentence:

```xml
<string name="account_payment_error_title">Оплата не прошла</string>
<string name="account_payment_error_lead">Деньги не списаны. Попробуйте ещё раз или напишите в поддержку.</string>
<string name="account_payment_error_body">%1$s\n\nКод для поддержки: HTTP %2$s\n%3$s</string>
```

---

## F. Suggested order of work

1. **Move the 66 Russian strings out of `values/` into `values-ru/`** and put English in `values/`;
   mirror the 18 `strings_*.xml` files. Until this is done, every other fix has to be made twice.
   (Or: delete the non-Russian locales and the language picker, and declare Russian the only
   language — a legitimate choice for this product, and far cheaper.)
2. Fix the P1 English leftovers actually on screen: memory card (C1), byte/speed/ping units (C2),
   About dialog (A3).
3. Extract the hardcoded toasts (A1, A2) into resources — one definition each.
4. Add `<plurals>` for the six counted strings (B8).
5. Sweep `labelFor` across the 57 unlabelled fields (D1) and correct the five wrong
   `contentDescription`s (D2).
6. Raise the 36/38/40/42/44 dp targets to 48 dp (D4) and fix the traffic-pill contrast (D5).
7. Settle «сервер» vs «профиль» (B6) and re-word the 12 machine-translated strings (B5/E9).
