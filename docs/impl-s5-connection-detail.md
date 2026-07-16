# S5 Implementation Spec — Connection / Providers / Theme detail screens

departament VPN (v2rayNG fork). Doc-only, file-level plan. Derived from the actual code in
`/home/user/dp/V2rayNG` and `docs/incy-redesign-spec.md`.

Incy detail-screen chrome for every screen below: a top bar with **back arrow + centered title +
`Готово`** (right), body = UPPERCASE section labels over rounded grouped cards, rows are
`SettingToggleRow` (iOS switch) / `SettingRow` (value + chevron / dropdown). All reuse the S1/S4
component set called out in the redesign spec (`bg_card_incy`, colored icon boxes, iOS switch).

## 0. Current state of the codebase (verified)

- Settings today are still the legacy `androidx.preference` `SettingsActivity` +
  `res/xml/pref_settings.xml`. The **S4 custom "Настройки" tab does not exist yet**: the bottom nav
  (`MainActivity.setupBottomNav`, tabs `nav_home` / `nav_servers` / `nav_more`) has `nav_more` open
  the **drawer** — it is not a grouped-card settings screen. So S5 detail screens must be built as
  self-contained launch targets that S4 (or the drawer, interim) opens; they do **not** depend on
  the preference framework.
- Recommended pattern for each detail screen: a normal `BaseActivity` subclass with ViewBinding,
  reading/writing prefs directly through `MmkvManager.decodeSettingsBool/String` +
  `MmkvManager.encodeSettings(...)` (same keys the preference screen uses, so both UIs stay in sync),
  then calling `SettingsChangeManager`/service restart where a live-apply is needed. Register each new
  activity in `AndroidManifest.xml` (`android:exported="false"`).
- MMKV is the single source of truth; `PREF_*` constants live in `AppConfig.kt`. New prefs = new
  `const val` in `AppConfig.kt` + read sites; no schema/migration needed (MMKV is schemaless, defaults
  supplied at read).

---

## 1. Screen "Соединение" (ConnectionSettingsActivity)

Section label `СОЕДИНЕНИЕ`, one card of toggle rows (Kill Switch gets its own note row).

| Row | Exists? | Pref key | Verdict |
|---|---|---|---|
| Автоподключение (on app open) | NO | `PREF_AUTO_CONNECT` (new) | New pref + MainActivity hook |
| Автоподключение при загрузке | YES | `PREF_IS_BOOTED` (`pref_is_booted`) | Surface only |
| Kill Switch | PARTIAL | `PREF_KILL_SWITCH` (new) | System deep-link + honest in-app guard |
| Разрешить LAN подключения | YES | `PREF_VPN_BYPASS_LAN` | Surface (tri-state → toggle) |
| Доступ через хотспот | YES | `PREF_PROXY_SHARING` (`pref_proxy_sharing_enabled`) | Surface only |
| LAN через прокси | YES (same pref, inverse) | `PREF_VPN_BYPASS_LAN` | See note — recommend collapse |

### 1.1 Автоподключение (connect on app open) — NEW
- **Does not exist.** No pref; nothing calls `startVService` on launch today.
- Files:
  - `AppConfig.kt`: add `const val PREF_AUTO_CONNECT = "pref_auto_connect"`.
  - `ui/MainActivity.kt`: after init (`onCreate` post-setup, or first `onResume`), if
    `MmkvManager.decodeSettingsBool(PREF_AUTO_CONNECT)` && `!CoreServiceManager.isRunning()` &&
    `!MmkvManager.getSelectServer().isNullOrEmpty()`, call `startV2Ray()` (existing private method,
    line ~297, which prepares VPN then `CoreServiceManager.startVService(this)`). Guard with a
    "did-auto-connect-this-process" flag so it fires once per launch, not on every resume.
  - Detail activity writes the toggle via `MmkvManager.encodeSettings(PREF_AUTO_CONNECT, checked)`.
- Note: VPN permission dialog (`VpnService.prepare`) may appear on first ever start — `startV2Ray()`
  already routes through the existing permission `ActivityResultLauncher`, so no extra work.

### 1.2 Автоподключение при загрузке (connect on boot) — EXISTS, surface only
- Fully wired: `PREF_IS_BOOTED`, `MmkvManager.encodeStartOnBoot(bool)` /
  `MmkvManager.decodeStartOnBoot()` (MmkvManager.kt:688–699). `receiver/BootReceiver.kt` reads
  `decodeStartOnBoot()`, checks a selected server, then `CoreServiceManager.startVService` +
  `SubscriptionUpdater.sync`. Manifest already has `RECEIVE_BOOT_COMPLETED` +
  `<receiver .BootReceiver>` (AndroidManifest.xml ~231). String `title_pref_is_booted` exists
  ("Auto connect at startup" → RU string needed).
- Files: detail row bound to `encodeStartOnBoot/decodeStartOnBoot`. Add RU string
  `title_conn_boot`. **No service/receiver work.**

### 1.3 Kill Switch — PARTIAL / HONEST FLAG
- **Android reality:** a true kill switch = **"Always-on VPN" + "Block connections without VPN"
  (lockdown)**. This is a **system setting** under Settings → Network & internet → VPN → (gear).
  Third-party apps **cannot** enable lockdown programmatically — `VpnService.Builder` exposes no
  lockdown/always-on flag; there is no public API. `setUnderlyingNetworks(...)` (already used in
  `CoreVpnService.defaultNetworkCallback`) is **not** a kill switch — it only tells the system which
  physical network carries the tunnel.
- **What we can honestly do:**
  1. Store the user's intent in `PREF_KILL_SWITCH` (new).
  2. When toggled ON, deep-link the user to the system screen:
     `Intent("android.net.vpn.SETTINGS")` (constant `Settings.ACTION_VPN_SETTINGS`) so they enable
     "Always-on VPN" + "Block connections without VPN" for departament. Show an explanatory sheet.
  3. Optional in-app soft-guard: when ON, keep full-tunnel routing (never split-route) — i.e. force
     `PREF_VPN_BYPASS_LAN` effective bypass OFF while Kill Switch is ON, and do **not** stop the tun
     interface until core has fully stopped (already handled in `stopAllService`). This reduces leak
     surface but is **not** equivalent to system lockdown; label it plainly.
- Files:
  - `AppConfig.kt`: `const val PREF_KILL_SWITCH = "pref_kill_switch"`.
  - Detail activity: toggle writes pref; on enable, launch `ACTION_VPN_SETTINGS` and show a note row
    "Требует «Всегда включён VPN» в настройках системы".
  - (Optional guard) `handler/SettingsManager.routingRulesetsBypassLan()`: `return false` early when
    `decodeSettingsBool(PREF_KILL_SWITCH)` is true — this makes the tunnel full-route. Low risk,
    compile-safe, one line.
- **Do not claim a real kill switch in copy.** The row subtitle should say it opens system settings.

### 1.4 Разрешить LAN подключения (bypass LAN) — EXISTS
- `PREF_VPN_BYPASS_LAN` is a **tri-state** `ListPreference` (values `0` Follow config / `1` Bypass /
  `2` Not Bypass; arrays `vpn_bypass_lan` / `vpn_bypass_lan_value`). Consumed by
  `SettingsManager.routingRulesetsBypassLan()` → `CoreVpnService.configureNetworkSettings`: when
  bypass is true it adds only `AppConfig.ROUTED_IP_LIST` routes (private ranges left off-tunnel = LAN
  reachable); when false it adds `0.0.0.0/0` (everything, incl. LAN, into tunnel).
- Map the Incy **toggle** to the tri-state: ON → write `"1"` (Bypass / LAN allowed), OFF → write
  `"2"` (Not Bypass). Reading: treat `"1"` as ON, `"0"`/`"2"` as OFF. Changing it requires a
  **service restart** to re-establish the tun interface → call `MainActivity.restartV2Ray()` path or
  send `MSG_STATE_RESTART` if running (mirror how the preference screen handles VPN-affecting keys).
- Files: detail row read/write `PREF_VPN_BYPASS_LAN`; no core changes.

### 1.5 Доступ через хотспот (proxy sharing / bind to 0.0.0.0) — EXISTS
- `PREF_PROXY_SHARING` (`pref_proxy_sharing_enabled`). In
  `core/CoreConfigManager.configureInbounds` (line 466): when the pref is **not** true,
  `inbound1.listen = AppConfig.LOOPBACK` (127.0.0.1); when true, `listen` is left unset so the SOCKS
  (and cloned HTTP) inbound binds to all interfaces (0.0.0.0) — reachable over the hotspot/LAN. Also
  surfaces a warning toast in `CoreServiceManager.startContextService` (line 163). String
  `title_pref_proxy_sharing_enabled` = "Allow connections from the LAN".
- Files: detail row read/write `PREF_PROXY_SHARING`; requires **service restart** to re-bind the
  inbound. No core changes.

### 1.6 LAN через прокси — SAME PREF, inverse facet (recommend collapse)
- This is the **inverse facet of `PREF_VPN_BYPASS_LAN`**, not a separate mechanism: "route LAN
  through the proxy" = "Not Bypass" = value `"2"`. There is no independent routing pref for it.
- Recommendation: **do not add a new pref.** Either (a) present the LAN behaviour as one control
  (single dropdown over the tri-state: Авто / Разрешить LAN / LAN через прокси, mapping 0/1/2), or
  (b) keep two mutually-exclusive toggles both bound to `PREF_VPN_BYPASS_LAN` where enabling one
  writes the other's value. Flag the overlap in the UI so the two rows can't contradict.
- Files: same as 1.4; no new pref, no core change.

---

## 2. Screen "Настройки провайдеров" (ProviderSettingsActivity)

**Key architectural fact:** auto-update is **per-subscription**, not global. State lives on
`dto/entities/SubscriptionItem` (`autoUpdate: Boolean`, `updateInterval: Long` minutes default 1440,
`userAgent: String?`). `handler/SubscriptionUpdater` schedules one WorkManager periodic task per sub
from `sub.autoUpdate` / `sub.updateInterval` (min clamp `SUBSCRIPTION_MIN_INTERVAL_MINUTES = 15`).
`SubEditActivity` edits these per-sub. A **global provider screen must choose a target**:
recommend applying to **all subscriptions** (loop `MmkvManager.decodeSubscriptions()`, write each,
then `SubscriptionUpdater.sync(forceReschedule = true)`), or to the pinned/primary provider only.
This spec assumes "apply to all subs + resync".

| Row | Exists? | Storage | Verdict |
|---|---|---|---|
| Автообновление | YES (per-sub) | `SubscriptionItem.autoUpdate` | Surface; write-all + resync |
| Интервал обновления (dropdown) | YES (per-sub) | `SubscriptionItem.updateInterval` | Surface as dropdown |
| Уведомлять об обновлениях | PARTIAL | `PREF_SUB_NOTIFY` (new) | New gate around existing notify |
| Обновлять при запуске | NO | `PREF_SUB_UPDATE_ON_LAUNCH` (new) | New pref + launch hook |
| Пинг при запуске | NO | `PREF_PING_ON_LAUNCH` (new) | New pref + launch hook |
| Пинг при обновлении | NO | `PREF_PING_ON_UPDATE` (new) | New pref + updater hook |
| Отправлять HWID | NO | `PREF_SEND_HWID` (new) | New pref + header wiring |
| USER-AGENT (editable) | YES (per-sub) + none global | `PREF_SUB_USER_AGENT` (new global default) | Surface + new global fallback |

### 2.1 Автообновление — EXISTS (per-sub)
- `SubscriptionItem.autoUpdate`; `SubscriptionUpdater.sync/syncOne` schedule/cancel WorkManager tasks
  from it. Global toggle: iterate `MmkvManager.decodeSubscriptions()`, set each
  `sub.autoUpdate = checked`, `MmkvManager.encodeSubscription(guid, sub)`, then
  `SubscriptionUpdater.sync(context, forceReschedule = true)`.
- Files: detail activity only. No new pref (or add `PREF_SUB_AUTO_UPDATE` purely as the screen's
  display mirror if you want a global toggle independent of per-sub state — optional).

### 2.2 Интервал обновления (dropdown) — EXISTS (per-sub)
- `SubscriptionItem.updateInterval` (minutes; effective min 15 via
  `SUBSCRIPTION_MIN_INTERVAL_MINUTES`). Dropdown entries e.g. 30м / 1ч / 6ч / 12ч / 24ч (=30/60/360/
  720/1440). On select: write to all subs + `SubscriptionUpdater.sync(forceReschedule = true)`.
- Files: detail activity + new `res/values/arrays.xml` entries (`sub_update_interval` /
  `sub_update_interval_value`). No core change.

### 2.3 Уведомлять об обновлениях — PARTIAL (gate existing notify)
- Today `SubscriptionUpdater.UpdateTask.doWork` **always** posts a notification via
  `NotificationHelper.notify(NotificationChannelType.SUBSCRIPTION_UPDATE, ...)` (start) and cancels on
  finish. There is no on/off switch.
- Files:
  - `AppConfig.kt`: `const val PREF_SUB_NOTIFY = "pref_sub_notify"` (default true).
  - `handler/SubscriptionUpdater.kt`: wrap the two `NotificationHelper` calls with
    `if (MmkvManager.decodeSettingsBool(PREF_SUB_NOTIFY, true)) { ... }`.
  - Detail row read/write pref.

### 2.4 Обновлять при запуске — NEW
- Today: `MainActivity.onCreate` and `BootReceiver` call `SubscriptionUpdater.sync` (schedules
  periodic work with KEEP; does **not** force an immediate fetch on launch).
- Files:
  - `AppConfig.kt`: `const val PREF_SUB_UPDATE_ON_LAUNCH = "pref_sub_update_on_launch"`.
  - `ui/MainActivity.kt`: on launch, if pref true, trigger an immediate update — simplest compile-safe
    option: `SubscriptionUpdater.sync(this, forceReschedule = true)` (recomputes next-run; for a true
    immediate fetch enqueue a one-shot `OneTimeWorkRequest` of `UpdateTask` per sub, or call
    `AngConfigManager.updateConfigViaSubAll()` on a background scope). Recommend the one-shot enqueue
    to avoid blocking UI.
  - Detail row read/write pref.

### 2.5 Пинг при запуске — NEW
- No launch-time ping today. The app already has a real-ping path (delay-test used by
  `MSG_MEASURE_DELAY` / server list speedtest; see `SpeedtestManager` / servers-tab "Проверить").
- Files:
  - `AppConfig.kt`: `const val PREF_PING_ON_LAUNCH = "pref_ping_on_launch"`.
  - `ui/MainActivity.kt`: on launch (after list load), if pref true, kick the existing batch
    real-ping routine used by the servers tab / "Проверить" button (reuse that ViewModel/manager
    call — do not invent a new tester).
  - Detail row read/write pref.

### 2.6 Пинг при обновлении — NEW
- Files:
  - `AppConfig.kt`: `const val PREF_PING_ON_UPDATE = "pref_ping_on_update"`.
  - `handler/SubscriptionUpdater.kt` (end of `UpdateTask.doWork`, after `updateConfigViaSub`) **or**
    `AngConfigManager.updateConfigViaSub` completion: if pref true, invoke the same batch real-ping
    routine (background). Prefer hooking the updater so both manual and periodic updates benefit.
  - Detail row read/write pref.

### 2.7 Отправлять HWID — NEW
- Subscription fetch path: `AngConfigManager.updateConfigViaSub` (line ~573) →
  `HttpUtil.getUrlContentWithUserAgentEx(UrlContentRequest(url, userAgent, ...))`.
  `dto/UrlContentRequest` currently carries only url/timeout/httpPort/proxy/userAgent — **no custom
  headers**. A stable device id already exists: `auth/AuthTokenStore.deviceId()` (used across the auth
  DTOs) and `Utils.getDeviceIdForXUDPBaseKey()` (ANDROID_ID-derived). The auth/managed-sub path
  (`DepartamentApiClientImpl`) already sends device id server-side; the **generic sub fetch does not
  send HWID**.
- Files:
  - `AppConfig.kt`: `const val PREF_SEND_HWID = "pref_send_hwid"`.
  - `dto/UrlContentRequest.kt`: add `val extraHeaders: Map<String, String>? = null` (or a dedicated
    `val hwid: String? = null`).
  - `util/HttpUtil.kt` (both `getUrlContentWithUserAgent*` builders, ~line 154 and ~222): after
    setting User-Agent, if `request.hwid`/header present, `requestBuilder.header("X-HWID", value)`
    (confirm the exact header name with the backend; `X-HWID` is a placeholder).
  - `handler/AngConfigManager.kt` (~line 579): when `decodeSettingsBool(PREF_SEND_HWID)` is true,
    pass `hwid = AuthTokenStore.deviceId()` into both `UrlContentRequest` builds.
  - Detail row read/write pref.
- Note: reuse `AuthTokenStore.deviceId()` for a single stable id; do not mint a second one.

### 2.8 USER-AGENT (editable) — EXISTS per-sub; add global default
- Per-sub `SubscriptionItem.userAgent` is edited in `SubEditActivity` (`etUserAgent`, lines 57/121)
  and consumed in `updateConfigViaSub`. When blank, `HttpUtil` falls back to
  `"v2rayNG/${BuildConfig.VERSION_NAME}"`; the auth path uses `BackendConfig.subscriptionUserAgent`
  (`BuildConfig.SUB_USER_AGENT` or `"DepartamentVPN/1.0"`). There is **no user-editable global UA**.
- Recommended: add a **global default** the field edits, feeding subs whose per-sub UA is blank.
  - `AppConfig.kt`: `const val PREF_SUB_USER_AGENT = "pref_sub_user_agent"`.
  - `handler/AngConfigManager.kt` (~line 573): `val userAgent = it.subscription.userAgent`
    `?.takeIf { it.isNotBlank() } ?: MmkvManager.decodeSettingsString(PREF_SUB_USER_AGENT)` (may be
    null → existing HttpUtil default still applies).
  - Detail screen: an `EditText` row (Incy "value + edit" style, or a small edit dialog) read/write
    `PREF_SUB_USER_AGENT`.

---

## 3. Theme picker + Language picker

All three prefs already exist and are already applied — the pickers are pure UI reskins of existing
`ListPreference`s.

### 3.1 Theme picker (blue/mono × light/dark/system) — ThemePickerActivity
- Colour family: `PREF_COLOR_THEME` (`pref_color_theme`, default `"blue"`; arrays `color_theme`
  {blue, mono} / `color_theme_value`). Applied via the app theme/attrs at recreate.
- Light/dark/system: `PREF_UI_MODE_NIGHT` (`pref_ui_mode_night`; values `0` system / `1` light /
  `2` dark; **default now `"2"`** per S1). Applied by `SettingsManager.setNightMode()` →
  `AppCompatDelegate.setDefaultNightMode(...)`.
- UI: two grouped cards — "ЦВЕТ" (two selectable swatch rows blue/mono, radio-style check) and
  "РЕЖИМ" (Системная / Светлая / Тёмная). On change: `MmkvManager.encodeSettings(...)`, call
  `SettingsManager.setNightMode()` and `activity.recreate()` (or the app's existing theme-apply +
  restart used by `SettingsChangeManager`) so the accent/night mode re-applies live.
- Note: the redesign is "dark-only, blue accent" — keep the picker functional but the default and
  primary experience remain dark/blue. No new pref, no logic change.
- Files: `ui/ThemePickerActivity.kt` + layout; manifest entry. No `AppConfig` change.

### 3.2 Language picker — LanguagePickerActivity
- `PREF_LANGUAGE` (`pref_language`, default `"auto"`; arrays `language_select` /
  `language_select_value`). Consumed by `SettingsManager.getLocale()` (maps to the `Language` enum)
  and applied through `MyContextWrapper.wrap(...)` in `attachBaseContext` of activities/services.
- UI: single card, one selectable row per `Language` enum entry (label from `language_select`), radio
  check on the active one. On select: `MmkvManager.encodeSettings(PREF_LANGUAGE, value)` then restart
  the activity/task so `attachBaseContext` re-wraps the locale (mirror how the preference screen
  currently forces a recreate on language change).
- Files: `ui/LanguagePickerActivity.kt` + layout; manifest entry. No `AppConfig`/logic change.

---

## 4. New `AppConfig.PREF_*` constants (single commit)

```
PREF_AUTO_CONNECT            = "pref_auto_connect"          // §1.1
PREF_KILL_SWITCH             = "pref_kill_switch"           // §1.3
PREF_SUB_NOTIFY              = "pref_sub_notify"            // §2.3 (default true)
PREF_SUB_UPDATE_ON_LAUNCH    = "pref_sub_update_on_launch"  // §2.4
PREF_PING_ON_LAUNCH          = "pref_ping_on_launch"        // §2.5
PREF_PING_ON_UPDATE          = "pref_ping_on_update"        // §2.6
PREF_SEND_HWID               = "pref_send_hwid"             // §2.7
PREF_SUB_USER_AGENT          = "pref_sub_user_agent"        // §2.8
```
Reused existing (no new key): `PREF_IS_BOOTED`, `PREF_VPN_BYPASS_LAN`, `PREF_PROXY_SHARING`,
`PREF_COLOR_THEME`, `PREF_UI_MODE_NIGHT`, `PREF_LANGUAGE`; per-sub `SubscriptionItem.autoUpdate` /
`updateInterval` / `userAgent`.

---

## 5. Compile-safe commit plan (each step builds & is independently revertable)

1. **Constants + strings (no behaviour).** Add the 8 `PREF_*` to `AppConfig.kt`; add RU
   strings/arrays (`sub_update_interval*`, connection/provider/theme/lang titles + subtitles,
   Kill-Switch system-settings note). Builds, does nothing yet.
2. **Reusable rows/drawables** (if not already delivered by S4): `SettingToggleRow` /
   `SettingRow` include layouts, iOS switch, `bg_card_incy`, colored icon boxes. Pure resources.
3. **ThemePickerActivity + LanguagePickerActivity** (§3): read/write existing prefs, call
   `setNightMode()` + recreate. Manifest entries. Lowest risk (no core, prefs already applied).
   Wire launch from S4/drawer.
4. **ConnectionSettingsActivity** (§1): rows for boot (`encodeStartOnBoot`), bypass-LAN
   (`PREF_VPN_BYPASS_LAN` 1/2 with restart), proxy-sharing (`PREF_PROXY_SHARING` with restart),
   auto-connect (`PREF_AUTO_CONNECT`), Kill Switch (`PREF_KILL_SWITCH` + `ACTION_VPN_SETTINGS`
   deep-link + note). Manifest entry. No core edits except the optional one-line
   `routingRulesetsBypassLan()` Kill-Switch guard.
5. **MainActivity launch hooks** (§1.1, §2.4, §2.5): once-per-process auto-connect;
   update-on-launch (one-shot `UpdateTask` enqueue); ping-on-launch (reuse servers-tab batch ping).
   Each behind its pref; behaviour off by default → safe.
6. **SubscriptionUpdater / HttpUtil / AngConfigManager wiring** (§2.3, §2.6, §2.7, §2.8):
   gate notify with `PREF_SUB_NOTIFY`; ping-on-update hook; add `UrlContentRequest` header field +
   `HttpUtil` header set + `AngConfigManager` hwid/global-UA fallback. Additive; existing defaults
   preserved.
7. **ProviderSettingsActivity** (§2): toggle/dropdown/edit rows; auto-update + interval write-all +
   `SubscriptionUpdater.sync(forceReschedule = true)`; notify/update-on-launch/ping/HWID/UA rows.
   Manifest entry. Wire launch from S4.
8. **Wire all three detail screens into the S4 Settings tab** (or interim drawer) as
   `Настроить ›` / `Авто ›` / `Тёмная ›` rows.

Restart semantics: bypass-LAN and proxy-sharing changes only take effect on a fresh tun/inbound —
reuse `MainActivity.restartV2Ray()` / `MSG_STATE_RESTART` when the service is running, exactly as the
existing preference screen does for VPN-affecting keys.

---

## 6. Honest Kill Switch statement (for UI copy)

departament cannot toggle a real kill switch itself. A true kill switch on Android is the system
feature **"Always-on VPN" + "Block connections without VPN" (lockdown)**, enabled by the user in
Android Settings → VPN. The in-app toggle (a) remembers the preference, (b) opens that system screen
(`Settings.ACTION_VPN_SETTINGS`), and (c) optionally forces full-tunnel routing while on. It is a
guide + best-effort guard, **not** OS-level lockdown, and the row copy must say so.
