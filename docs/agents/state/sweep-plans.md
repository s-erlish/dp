# Sweep — the original planning documents vs. the code today

**Written:** 2026-07-26 · **Branch:** `claude/app-audit-agents-hyyftk`
**Scope:** the 18 pre-design-wave documents in `docs/` — `next-plan.md`, `roadmap-wave3.md`,
`strategy-russia-2026.md`, `master-requirements-audit.md`, `new-modules-proposals{,-3}.md`,
`remnawave-templates-spec.md`, `circumvention-settings-design.md`, `hidden-templates-design.md`,
`ping-methods-design.md`, `notification-design.md`, `memory-panel-design.md`,
`server-flags-design.md`, `smart-tv-transfer-design.md`, `telegram-auth-design.md`,
`happ-parity-details.md`, `ux-recommendations.md`, `subscription-meta-bar-design.md`.

**Method.** Every actionable item was extracted from the documents and then grepped for in
`/home/user/dp/V2rayNG/app/src/main`. An item is listed OPEN only where I read the code and the
implementing symbol does not exist, or exists with no reader/writer. Line numbers are from the
working tree at the time of the sweep and will drift; symbols will not. Read-only; no source file
changed, no git command run.

**Headline.** These documents aged better than expected. The whole Remnawave/hidden-template
spine, the meta bar, flags, the notification, ping methods, auto-fallback, Telegram auth,
payments, and the TV transfer protocol all landed. What did **not** land clusters into five
themes, and one of them is a shipping-blocker nobody has written down anywhere:

1. **Every locale except Russian is broken.** 690 departament strings were authored in Russian
   directly into `res/values/` and there is no `values-en/`. An English, Farsi, Arabic, Vietnamese
   or Chinese device gets Russian text on every screen built since the departament work started.
2. **The whole subscription-expiry warning story is missing** — no home banner, no notification,
   no reminder worker — across four documents that each independently asked for it.
3. **The anti-censorship self-tuning layer was never started**: no global uTLS fallback, no bypass
   presets, no Russia mode, no periodic liveness re-check. `circumvention-settings-design.md`'s
   *one* new pref (`PREF_UTLS_FINGERPRINT`) is a one-line change that was never made.
4. **Delivery resilience stopped half-way**: never-wipe-on-failed-fetch landed; mirrors,
   fetch-through-tunnel, and out-of-band fallback did not.
5. **Two safety items in the hidden-template threat model were skipped**: backup/export does not
   exclude managed subscriptions, and the TV pairing push sends the subscription URL in cleartext.

---

## 1. Open items, ranked

### 1.1 Localisation — the shipping blocker

**L1 · Every non-Russian locale falls back to Russian. (high · M)**
`roadmap-wave3.md §1` and `next-plan.md §J` both set the convention "add `values/` + `values-ru/`
pairs together" and "run lint `MissingTranslation`". The waves inverted it: 21 string files now
live in `res/values/`, and 18 of them (690 of the 1166 default strings) are written in Russian
with **no English source at all**.

```
res/values/          21 files, 1166 <string>   (strings_account 87, strings_editors 280,
                                                strings_settings_advanced 56, strings_menu_actions 38, …)
res/values-en/       ABSENT
res/values-ru/       3 files  (strings.xml, strings_editors.xml, strings_tv.xml)
res/values-fa|ar|vi|zh-rCN|zh-rTW|bn|bqi-rIR/   ~342 keys each — upstream v2rayNG only
```
Spot check: `res/values/strings_settings_hub.xml:25` = «Постоянный VPN и блокировка»;
`res/values/strings_account.xml:1` = «Аккаунт». There is no locale a non-Russian speaker can pick
that avoids these. `ux-recommendations.md §P2-5` calls RU/EN/**FA** first-class; FA is 690 strings
behind. Additionally 11 keys in `values/strings.xml` still have no `values-ru/` entry
(`auth_subscription_active`, `auth_subscription_expired`, `sub_traffic_used`, `sub_infinity`, …) —
these render English on a Russian device, the original §1 defect, still open in miniature.

Two ways out, both real work: create `values-en/` with the English source and keep `values/` as
the English default (correct, larger), or accept Russian-only and strip the dead locale folders
(honest, smaller). Doing neither is the current state.

### 1.2 Subscription expiry — asked for four times, built zero times

**E1 · No expiry warning anywhere. (high · M)**
Four documents converge on this: `next-plan.md §C.2` (home banner at ≤3 days), `next-plan.md §F`
(WorkManager reminders at 3/1/0 days with MMKV dedup, gated by `PREF_SUB_EXPIRY_REMINDERS`, tap →
plans), `ux-recommendations.md §P1-6` («Access expires in {n} days» turning red, "Renew"), and
`happ-parity-details.md #15` (`sub-expire` / `sub-expire-button-link` directives).

Verified absent:
- `grep -rn "SUB_EXPIRY\|expiry_remind\|ExpiryReminder"` over `app/src/main` → **0 hits**.
- `res/values/strings.xml:347` `sub_days_left` (`%1$s · %2$dd`) has **zero readers** in the tree.
- The meta bar prints a bare date: `MainActivity.kt:1680-1685` sets `sub_expired` or
  `sub_expires` and stops — no threshold, no colour change, no CTA.
- `AccountFragment` / `AccountViewModel` surface no expiry warning either.
- `grep -rn "sub-expire"` → 0. The directive is never parsed.

This is the retention/monetisation feature the plan ranked P1 and the one thing a paying user
notices when it is missing. Payments (`BuyTariffActivity`) shipped; the nudge to use them did not.

### 1.3 Anti-censorship self-tuning — the whole layer

**C1 · `PREF_UTLS_FINGERPRINT` — the single new pref the design introduces — never created. (high · S)**
`circumvention-settings-design.md §1.2, §5.2` and `strategy-russia-2026.md §3.3` / `R0.2`, and
`gap-desktop-to-android.md W4`. `grep -rn "UTLS_FINGERPRINT\|utls_fingerprint"` → **0 hits**.
`core/CoreOutboundBuilder.kt:564` is still:
```kotlin
fingerprint = profileItem.fingerPrint.nullIfBlank(),
```
with no global fallback. A node imported from a share link without `fp=` therefore runs with an
**empty uTLS fingerprint** — the exact JA3/JA4 flag §3.3 names as a direct detection signal. The
value array already exists (`res/values/arrays.xml:72` `streamsecurity_utls`), so this is one
`?:` plus one settings row.

**C2 · `handler/BypassPresets.kt` and the four presets — never built. (high · M)**
`circumvention-settings-design.md §3, §5.3-5.5`: Standard / Russia-strict-DPI / Iran /
Low-latency, each a data-driven `Map<String,String>` applied through `MmkvManager.encodeSettings`
+ `SettingsChangeManager.makeRestartService()`. `grep "BypassPresets\|BYPASS_PRESET\|pref_bypass"`
→ **0 hits**. `res/xml/pref_bypass.xml` does not exist. The individual knobs are all editable
(fragment length/interval/packets in `res/xml/pref_settings.xml:192-234`, mux/fragment toggles at
`MainActivity.kt:3361-3391`), but a non-expert has to know what a ClientHello is to use them —
precisely the problem the document exists to solve.

**C3 · Russia mode and periodic liveness re-check — never built. (high · M)**
`roadmap-wave3.md §6a/§6d`, `strategy-russia-2026.md R0.2/R1.2`, `next-plan.md §I.3`.
`grep "RUSSIA_MODE\|LIVENESS\|liveness"` → **0 hits**. The health check is still the one-shot
post-connect probe (`MainActivity.kt:623-643`, already reported as fixed for the re-probe
confirmation) — nothing runs on a 30-60 s cadence while connected, so the RU "TLS freeze"
(passes TCP, dies at ~16 KB) is still only caught once, at connect time.

**C4 · No per-node liveness memory / temporary avoid-set. (medium · M)**
`next-plan.md §I.3` ("память о плохих нодах" with a TTL) and `new-modules-proposals-3.md N9`
("avoid this server for 30 min"). `grep "avoidUntil\|avoid_until\|nextServer"` → 0. Auto-fallback
excludes exactly one guid for exactly one attempt (`fastConnectExcludeGuid`); a node that flaps
is re-selected on the next connect.

**C5 · `sort-order` subscription directive never honoured. (low · S)**
`next-plan.md §I.2`. The only sort is the user's own `PREF_SERVER_SORT_ORDER`
(`ProviderSettingsActivity.kt:262`). No header or in-body `sort-order` is read anywhere.

**C6 · DNS-leak discipline never verified or enforced. (medium · M)**
`next-plan.md §I.1`, `strategy-russia-2026.md §3.5`, `new-modules-proposals.md M11`. There is no
leak test, no IPv6 route/block control, and `PREF_PREFER_IPV6` has readers
(`CoreOutboundBuilder.kt:682`, `CoreConfigManager.kt:1002`) but no writer — a defect the state
audit already logged as §4.2, which this document independently reaches from the other direction.

### 1.4 Connection UX the plans ranked P1

**U1 · Auto-connect on app launch — never built. (high · S)**
`next-plan.md §E.1`: `PREF_AUTO_CONNECT_ON_LAUNCH`, mirroring the existing `BootReceiver` gate.
`grep -rn "AUTO_CONNECT\|autoConnectOnLaunch"` over `app/src/main` → **0 hits**. Boot autostart
works; opening the app with a server selected still requires a tap.

*(§E.2, the kill-switch surface, DID land — `MainActivity.kt:3286-3292` deep-links to
`Settings.ACTION_VPN_SETTINGS`, `layout_settings_content.xml:483` `row_always_on`,
`AndroidManifest.xml:240` `SUPPORTS_ALWAYS_ON`. Do not re-open it.)*

**U2 · First-run onboarding — never built. (high · M)**
`roadmap-wave3.md §2a`, `ux-recommendations.md §P0-1`. `grep "ONBOARDING_SHOWN\|OnboardingActivity"`
→ **0 hits**. What exists is `MainActivity.updateOnboardingLogin():1484` — the *home empty-state
card*, a different surface that only appears when there are zero servers. A fresh install has no
first-run explanation and no trust note.

**U3 · VPN-permission priming sheet — never built. (medium · S)**
`roadmap-wave3.md §2c`. `MainActivity.startVpnWithPermission():1881` calls `VpnService.prepare()`
and hands the raw system dialog straight to the user. `grep "perm_vpn_priming\|VPN_PERMISSION_PRIMED"`
→ 0.

**U4 · No staged connect status. (medium · M)**
`ux-recommendations.md §P0-2` (Preparing → Handshaking → Testing route → Connected) and
`roadmap-wave3.md §5.1` (three states). Only one intermediate state exists:
`MainActivity.kt:1958` sets `connection_connecting`. The haptics from §5.4 **did** land
(`util/MotionUtils.kt:56,63`, `MainActivity.kt:1988`) — but without `PREF_HAPTICS`
(`grep PREF_HAPTIC` → 0), so they cannot be turned off, which §5.4 required.

**U5 · No structured `ConnectError` taxonomy or recovery sheet. (medium · M)**
`ux-recommendations.md §P0-4, §5` — seven named classes each with a plain cause and a primary fix
button. `grep "ConnectError"` → 0 (only `auth/ApiError.kt`, which is the network-API taxonomy).
Failures still surface as toasts.

**U6 · No last-known-good server / one-tap reconnect. (low-medium · S)**
`ux-recommendations.md §P0-3`. `grep "lastConnected\|last_connected"` → 0.

**U7 · No favourites and no synthetic "Fastest / Auto" list entry. (medium · M)**
`ux-recommendations.md §P1-7` and `happ-parity-details.md #9`. `grep "favourit\|favorite"` → 0.
Fast-connect exists only as a menu action (`menu_item_fast_connect`). `EConfigType.POLICYGROUP`
and its balancer are fully wired (`MainRecyclerAdapter.kt:261` even labels it "Auto"), but no
subscription auto-creates one and it is never surfaced as a first "Hybrid (Auto-select)" row —
the presentation half of #9 that the doc says is all that is left.

**U8 · No in-place server hot-swap sheet from Home. (low-medium · M)**
`ux-recommendations.md §P1-1` and signature moment §2.3. `grep "ServerSwitchSheet"` → 0.

**U9 · Trusted/untrusted Wi-Fi auto-connect — never built. (low · L)**
`ux-recommendations.md §P1-3`. `grep "trustedWifi\|SSID"` → 0. Ranked P1 by the doc but it is the
largest single item in it; recording it so it is a decision rather than an oversight.

### 1.5 The safety items in the hidden-template threat model

**S1 · Backup exports managed subscriptions in plaintext. (high · M)**
`hidden-templates-design.md §3.4 + §5 step 10`: *"Ensure `WebDavConfig` backup/export paths and
any 'export all' flow skip locked profiles/subs."* Not done.
`ui/BackupActivity.kt:162` is `MMKV.backupAllToDirectory(backupDir)` → `ZipUtil.zipFromFolder` →
a **plain zip** the user can then share (`backup_action_share`) or push to WebDAV
(`backupViaWebDav`). `grep -n "locked" ui/BackupActivity.kt handler/WebDavManager.kt util/ZipUtil.kt`
→ **0 hits**.

The locked *template body* is protected (`TemplateManager.wrapRawForStorage` encrypts it), but the
thing the whole feature hides — **the subscription URL, which carries the account token** — lives
in the ordinary config MMKV in plaintext and rides along in the zip. Two consequences:
- the operator's managed sub URL leaves the device in a shareable file, defeating
  `SubEditActivity`'s careful redaction (`SubEditActivity.kt:93-97`);
- restoring that zip on a *different* device yields locked profiles whose raw is `dpt-enc:`-
  prefixed and undecryptable (the Keystore key does not travel) — `TemplateManager.unwrapStoredRaw`
  returns `null` (`TemplateManager.kt:130`) and the profile silently cannot connect until the
  subscription is refreshed. Nothing tells the user that.

**S2 · TV pairing pushes the subscription URL over cleartext HTTP. (medium-high · M)**
`smart-tv-transfer-design.md §3.5` recommends HTTPS with a QR-pinned self-signed fingerprint, or
token-derived AEAD, and calls plain HTTP *"acceptable only as v1 MVP … the sub URL is visible to a
LAN sniffer / rogue AP"*. Shipped as plain HTTP: `tv/TvSendActivity.kt:176`
```kotlin
.url("http://${info.ip}:${info.port}${TvPairingProtocol.PAIR_PATH}")
```
`grep "ssl\|SSLServerSocket\|Cipher\|encrypt"` over `tv/` → 0. Everything *else* §3.6 mandated is
correctly implemented — single-use token, TTL close (`TvHttpReceiver.kt:99`), constant-time
compare and bad-attempt lockout (`:184-189`) — so this is one transport swap, not a rebuild.

**S3 · Template validation stops at the vendor-key strip. (medium · S-M)**
`hidden-templates-design.md §5 step 7` and `remnawave-templates-spec.md §6.3` both ask for a
validator that requires `outbounds`, **rejects or strips inbounds listening on anything other than
loopback/tun**, and caps template size and rule count. What exists is
`AngConfigManager.stripVendorRootKey()` (`:571-581`) and a substring test (`:604-605`
`startsWith("{") … && contains("outbounds")`). There is no `fmt/TemplateValidator.kt`. A locked
operator template can still open a public inbound on the user's device — the exact §4 risk the
document raises.

*(The rest of the remnawave spec landed: `Accept` header `HttpUtil.kt:221,290`; robust JSON
detection `:604-605`; vendor strip `:571`; HTTPS-forced for locked subs with the https-upgrade
retry `:800-815`. Do not re-open those.)*

### 1.6 Happ parity — the last three directives

**H1 · In-body `#` directives other than `profile-hidden` are ignored. (medium · S)**
`happ-parity-details.md §0`: *"the departament Remnawave panel may emit `#announce:` /
`#support-url:` lines at the top of the base64-decoded body, so the parser must scan leading `#`
lines too, not only OkHttp headers."* The **headers** are all read
(`HttpUtil.kt:319-323` — announce, support-url, profile-web-page-url, profile-title,
profile-hidden). The **body scan** recognises only lock state:
`TemplateManager.resolveBodyDirective()` (`:92-105`) matches `profile-hidden|hidden|locked` and
nothing else. A panel that ships directives in the body loses its announce banner and support
button silently.

**H2 · `profile-update-interval` never read. (medium · S)**
`happ-parity-details.md #7, #18`; also `hidden-templates-design.md §2.4`.
`grep -rn "profile-update-interval"` → **0 hits**. `HttpUtil.UrlContentResult` (`:266-269`)
captures five headers and not this one, so the operator cannot push an auto-update cadence.

**H3 · `sub-info-*` rich block and `fallback-url`/`new-url`/`new-domain` — never built. (low-medium · M/L)**
`happ-parity-details.md #15, #17`. `grep "sub-info\|fallback-url\|new-domain"` → 0. #17 is the
same capability as **D1** below, arriving from the Happ side.

### 1.7 Delivery resilience — half-finished

**D1 · No subscription mirror URLs, no fetch-through-tunnel, no out-of-band fallback. (high · M-L)**
`strategy-russia-2026.md §3.4 #1-#3` / `R1.1`, `happ-parity-details.md #17`.
`SubscriptionItem` carries a single `url`. `grep "mirror\|altUrl\|fallbackUrls"` finds only
unrelated prose. `AngConfigManager` does have a proxy retry (`:912-918` uses
`SettingsManager.getHttpPort()`), but there is no ordered mirror list and no deliberate
"pull the next update inside the live tunnel" path.

**Done, do not re-open:** §3.4 #4 *never wipe a working list on a failed fetch* **is** implemented
— `AngConfigManager.kt:612-620` stages the parse and only deletes once `staged.isNotEmpty()`, with
a comment naming the exact failure it prevents.

**D2 · In-app self-update through the tunnel + signature verification — never built. (medium · M)**
`strategy-russia-2026.md §4.2 / R1.4`. `grep "signature\|checksum\|verifySignature"` over
`handler/UpdateCheckerManager.kt` → 0. Compounding: `CheckUpdateActivity` has zero entry points
(already logged in the state audit), so today there is no update check at all — and the strategy
document's premise is that neither app store is a reliable RU channel.

**D3 · No RU-aware diagnostics panel. (medium · M)**
`strategy-russia-2026.md §2 #14 / R3.1`, `ux-recommendations.md §P2-2` (guided "Having trouble?"
+ **redacted** share-debug), `new-modules-proposals-3.md N11` (redaction contract).
`grep -i diagnos` finds only the payment-error dialogs in `BuyTariffActivity`/`AccountFragment`.
`LogcatActivity` exists but has zero entry points (state audit) and no redaction filter.

### 1.8 Platform reach and hygiene

**P1 · TV Phase A (D-pad, overscan, landscape) — never started. (medium · L)**
`smart-tv-transfer-design.md §4 Phase A`, `new-modules-proposals.md M6`,
`master-requirements-audit.md §12`. Phase B (the transfer) is **done and reachable** —
`tv/Tv{Send,Receive}Activity` are launched from `MainActivity.kt:2407, 3118, 3123`. Phase A is not:
```
res/values-television/   ABSENT     res/layout-television/  ABSENT     res/layout-land/  ABSENT
res/values-sw600dp/      ABSENT
nextFocus* in res/layout/ →  only layout_tls.xml (10) and layout_tls_hysteria2.xml (2)
```
The manifest still declares `LEANBACK_LAUNCHER`, so the app ships on TV home screens with a
phone-only focus model.

**P2 · Accessibility pass — not started. (medium · M)**
`ux-recommendations.md §P2-4`, `next-plan.md §J.2`.
`grep -rn "announceForAccessibility"` → **0 hits** in the entire tree — no connection state is
announced. `contentDescription` appears in **13 of 82** `res/layout/*.xml`.

**P3 · `onTrimMemory` / `ComponentCallbacks2` never implemented. (medium · S)**
`memory-panel-design.md §2.6` — the central `trimCaches(level)` entry point that all bounded
caches route through. `AngApplication.kt` overrides only `attachBaseContext` (`:20`) and
`onCreate` (`:32`); `grep "onTrimMemory\|ComponentCallbacks2"` → 0. §2.5's suggestion to revive a
*trimming* (not connection-dropping) response also went nowhere —
`service/CoreVpnService.kt:89-91` is still the commented-out `onLowMemory()` the doc describes.

**P4 · Widget is still stock upstream. (medium · M)**
`next-plan.md §H.2`, `ux-recommendations.md §P1-2`: server name + flag + state + optional live
↑/↓, tap to toggle. `receiver/WidgetProvider.kt` sets exactly two things — an icon
(`ic_play_24dp`/`ic_stop_24dp`) and a background drawable. `grep "setTextViewText\|remarks\|flag"`
over that file → **0 hits**. Tap-to-toggle does work.

**P5 · Quick tile has two states, not three. (low-medium · S)**
`next-plan.md §H.1` asks for отключено / подключается / подключено.
`service/QSTileService.kt:27-32` handles `STATE_INACTIVE` and `STATE_ACTIVE` only;
`grep STATE_UNAVAILABLE` → 0, so a connecting tile reads as already-off.

**P6 · No flag on the home current-server label. (low-medium · S)**
`master-requirements-audit.md §1d`, `next-plan.md §L.1`.
`MainActivity.selectedServerName():2268-2272` returns `remarks` verbatim. `FlagUtil` is applied in
exactly two places — `MainRecyclerAdapter.kt:210-211` and `NotificationManager.kt:143` — so the
row and the notification resolve a flag for a server whose remark has none, and the home hero,
the most looked-at surface in the app, does not.

**P7 · App-icon alias chooser / stealth icon — never built. (medium · S-M)**
`master-requirements-audit.md §5c`, `next-plan.md §L.4`, `ux-recommendations.md §P2-7`,
`new-modules-proposals.md M2`. `grep -c "activity-alias" AndroidManifest.xml` → **0**. For the
RU/FA audience the documents frame this as a safety feature, not personalisation.

**P8 · In-app language switcher — never built. (low-medium · S)**
`next-plan.md §L.5`, `ux-recommendations.md §P2-5`.
`grep "setApplicationLocales\|LocaleListCompat"` → 0. Interacts with **L1**: today there is
nothing to switch *to*.

**P9 · Daily traffic statistics — never built. (medium · M)**
`roadmap-wave3.md §4`. `grep "DailyTraffic\|SHOW_DAILY_TRAFFIC\|TrafficStatsActivity"` → 0. The
integration point the doc identified is still sitting there ready:
`handler/NotificationManager.kt:284` already iterates `queryAllOutboundTrafficStats()` and computes
the per-interval deltas the design wanted to accumulate.

**P10 · Notification: two actions, and not the ones asked for. (low · S)**
`ux-recommendations.md §P1-5` wants Disconnect · Switch server · Pause 5 min;
`NotificationManager.kt:160,165` still has Stop + Restart. Also `setForegroundServiceBehavior(
FOREGROUND_SERVICE_IMMEDIATE)` from `notification-design.md §4` is not set.
**Everything else in that document landed** — chronometer (`:155-156`), `IMPORTANCE_LOW` (`:226`),
`PRIORITY_LOW` (`:152`), the versioned channel id (`AppConfig.kt:203`
`DEPARTAMENT_VPN_CH_ID`), flag in the title (`:143`). Do not re-open those.

**P11 · The portable core-contract document was never written. (low · S)**
`next-plan.md §K` acceptance criterion 3: *"Есть краткий документ «портируемый core-контракт»
(API + формат подписки + конфиг)"*. No such file in `docs/`. Criteria 1 and 2 hold — all endpoints
are centralised in `auth/BackendConfig.kt:33,80`, and `AuthManager`/`SubscriptionSyncManager`
carry no `Activity`/`View` references. Now that a desktop client exists, the missing document is
the one that would have kept the two API surfaces honest with each other.

### 1.9 The module proposals — never scheduled, recorded so they are a choice

`new-modules-proposals.md` (M1-M13) and `new-modules-proposals-3.md` (N1-N11) were advisory and no
wave adopted them. Verified absent by grep, each returning **0 hits**: `BypassLinter` (N1),
`TimeCheck`/clock-skew (N2), post-connect 204 reachability + captive-portal classification (N3),
inline settings glossary (N4), foreground clipboard offer + QR-from-gallery (N5),
`ConnectionEventLog` (N6), subscription update diff (N7), `LITE_MODE` (N8), rotate/avoid (N9),
pre-flight checklist (N10), `SupportBundle` (N11); ad/tracker blocking (M1), app-lock/duress
(M2), DNS control centre (M3), throughput speed test (M4), `NetworkStatsManager` data-usage
dashboard (M5), scenes (M7), guided MTU/Mux tuning (M8), routing preset library + geo auto-update
(M9), Clash/sing-box importer (M10), leak test (M11), OEM battery guardian (M12), multi-hop UX
(M13).

Three of these are cheap and disproportionately useful for the stated audience and are worth
promoting out of the "proposal" bucket: **N2** (a clock-skew banner — TLS/Reality fails hard on
skew and presents as "nothing works", and the `Date:` header from the existing 204 probe is
already in hand), **N3** (one post-connect 204 through the tunnel turns an opaque green
"connected" into an honest state), and **N1** (a pure-Kotlin `BypassLinter(profile, prefs)` that
would catch exactly the empty-SNI / missing-fingerprint / Mux-on-Vision cases **C1** leaves open).

---

## 2. Refused, deferred or superseded — do NOT resurrect these

These are decisions with stated reasons, not gaps.

| Item | Document | Why it is closed |
|---|---|---|
| ECH enabled by default | `strategy-russia-2026.md §1.7, §3.3`; `circumvention §6` | `cloudflare-ech.com` + ECH is itself an RU block trigger. `CoreOutboundBuilder.kt:566` passes only the per-node `echConfigList`; there is no global enable, which is the intended state. |
| Client-side `injectHosts` | `remnawave-templates-spec.md §6.4 branch B`; `hidden-templates §2.3` | Remnawave injects host data **server-side**; the client receives a final JSON. Branch B is only for a Happ-style template+node-list operator, which this deployment is not. |
| Self-rolled in-app kill switch | `next-plan.md §E.2` overrides `strategy §2 #8` | «не изобретать блокировку в приложении» — the system Always-on deep link is the chosen path and it shipped (`MainActivity.kt:3286-3292`). `VpnService` lockdown/`setBlocking` is deliberately not used. |
| GeoIP-based flag resolution | `server-flags-design.md §1 option (c)` | Explicitly a last resort; the shipped emoji→ISO layered resolver is the design's own recommendation. |
| 250 bundled flag vectors / PNGs | `server-flags-design.md §2` | "Overkill for v1"; emoji chosen. |
| Custom `RemoteViews` notification | `notification-design.md §2` | Phase-2 only, "not worth the risk". |
| NSD / mDNS TV discovery | `smart-tv-transfer-design.md §3.4` | IP-in-QR is primary; mDNS is blocked on many consumer APs. Fallback only. |
| Unified single-scroll server list with sticky section headers | `server-flags-design.md §4 Option 1` | Option 2 (collapse within a group) was chosen for blast radius. |
| Google Play Billing | `next-plan.md §D` | "осознанный отдельный выбор", flavour-gated, not the default path. |
| AmneziaWG / TUIC / new UDP transports | `roadmap-wave3.md §6` "what NOT to do"; `strategy R2.1` | Not in this fork; recorded as debt. |
| KMP / aggressive cross-platform refactor | `next-plan.md §K` | "не рефакторить агрессивно" — discipline + a document only. |
| `chrome_pq` uTLS value | `circumvention §4.2` | Breaks Reality; intentionally excluded from the entry list. |
| Desktop «Серверы» tab | owner decision (given) | Overrules `33-master-plan-pc.md`. Two dead views on disk are not progress. |
| PSS as the memory headline | `memory-panel-design.md §1.3` | The code chose Java heap instead and **documents the choice** (`util/MemoryStatsManager.kt:4-9`: shared framework pages "are not really the app"). Contradicted-by-decision, not a gap — but note the panel itself cannot be shown at all (`PREF_SHOW_MEMORY` has one reader and zero writers; already logged as state-audit §4.2). |

---

## 3. Confirmed done — do not re-report

Checked and found implemented, so the next sweep does not re-litigate them:

- **`remnawave-templates-spec.md`, essentially whole**: `Accept: application/json`
  (`HttpUtil.kt:221,290`), robust JSON detection replacing the three-substring heuristic
  (`AngConfigManager.kt:604-605`), vendor `remnawave` root-key strip (`:571-581`), HTTPS forced
  for locked subs with an https-upgrade retry rather than a hard refusal (`:800-815`), staged
  parse so a bad body cannot wipe a provider (`:612-620`).
- **`telegram-auth-design.md` milestones A-D and stage 2**: Keystore-derived MMKV crypt key
  (`auth/AuthTokenStore.kt:86,104-106`, `auth/KeystoreKeyProvider.kt:69`), 401-only-on-`getMe`
  session wipe with 403 correctly excluded (`auth/DepartamentApiClientImpl.kt:357-360`,
  `auth/AccountRepository.kt:65-74`), payments shipped (`ui/BuyTariffActivity.kt`,
  `PaymentMethodSheet`, `PaymentHistoryActivity`, `auth/dto/PaymentDtos.kt`) — `next-plan.md §D`
  is done, not open.
- **`subscription-meta-bar-design.md`**: whole thing, plus the announce banner
  (`MainActivity.kt:1633-1635`, `layout_subscription_meta_bar.xml:194`).
- **`happ-parity-details.md` P0/P1 except H1-H3**: pin/unpin (`SubscriptionItem.kt:25`,
  `MainActivity.kt:1522,1614-1615`), header directive capture (`HttpUtil.kt:319-323`), support and
  web-page buttons (`MainActivity.kt:1189,1624`), collapse (`ServersFragment.kt:190-206`),
  last-updated line (`MainActivity.kt:1574-1585`).
- **`notification-design.md`** except the two items in **P10**.
- **`ping-methods-design.md`**: four methods, `enums/PingMethod.kt`, delay-test URL pref, real-ping
  concurrency pref.
- **`server-flags-design.md`**: `util/FlagUtil.kt` + `FlagUtilTest.kt`, flag tile on rows, collapse.
- **`smart-tv-transfer-design.md` Phase B**: full pairing protocol, reachable from two entry points.
- **`hidden-templates-design.md` §3.4 gating**: `SubEditActivity.kt:90-97,113,151-155` redacts the
  URL and disables allow-insecure for locked subs; share/QR/full-content/editor are gated.
- **`strategy-russia-2026.md`**: the RU-whitelist routing preset exists as a real asset —
  `assets/custom_routing_white_russia`, `enums/RoutingType.kt:8 WHITE_RUSSIA` (§2 #9 / R3.2).
- **`roadmap-wave3.md §5` haptics** (minus the `PREF_HAPTICS` gate, see **U4**) and **§2b** empty
  states (`MainActivity.kt:1097` `home_select_server`, home empty-state card).

---

## 4. If only five things get done

1. **L1** — decide the locale story and execute it. Everything else is polish on an app that
   currently speaks Russian to Farsi and English users.
2. **E1** — the expiry warning. Four documents, one feature, zero code, and it is the one that
   directly protects revenue now that payments ship.
3. **C1** — one `?:` in `CoreOutboundBuilder.kt:564` plus one settings row closes a live
   detection vector for every node imported without `fp=`.
4. **S1** — stop `MMKV.backupAllToDirectory` from putting managed subscription URLs into a
   shareable zip.
5. **U1** — `PREF_AUTO_CONNECT_ON_LAUNCH`. Small, and the most-noticed missing behaviour in the
   whole list.
