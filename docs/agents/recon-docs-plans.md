# Recon — consolidated backlog from `docs/*.md`, cross-checked against code

**Agent:** recon-docs-plans · **Date:** 2026-07-26
**Repos read:** `/home/user/dp` (Android, branch `claude/app-audit-agents-hyyftk`, HEAD `bfd05fd`),
`/home/user/v2rayN` (Desktop, branch `claude/app-audit-agents-hyyftk`).
**Method:** read every plan/roadmap/review/impl doc listed in the task in full, then verified each
"open"/"done" claim by reading the actual source. Every claim below cites a file I opened.
No code was modified.

---

## 0. Documents read (and what each is authoritative for)

| Doc | Size | Role | Staleness vs code |
|---|---|---|---|
| `docs/next-plan.md` | 490 lines | Strategy A–L, dated 2026-07-09 | **Stale in parts** — A/B partly landed, C/D landed differently, E/F/G/H/I/J/K/L mostly open |
| `docs/roadmap-wave3.md` | 484 lines | Wave-3 §1–§6, dated 2026-07-09 | **Stale** — §5 landed, §1 partial, §2/§3/§4/§6 not landed |
| `docs/strategy-russia-2026.md` | 430 lines | RKN/TSPU landscape + tracks R0–R3 | Research still valid; **R0.2/R0.3/R0.4, R1.1–R1.4, R2.*, R3.* all unimplemented** |
| `docs/master-requirements-audit.md` | 159 lines | Requirement matrix, 2026-07-09 | **Heavily stale** — S3/S4 shipped since; several "DONE" rows now regressed (see §2) |
| `docs/new-modules-proposals.md` | 363 lines | M1–M13 module proposals | **None implemented** (verified by absence of files/prefs) |
| `docs/new-modules-proposals-3.md` | 359 lines | N1–N11 module proposals | **None implemented** |
| `docs/remnawave-templates-spec.md` | 340 lines | XRAY_JSON end-to-end spec, gaps A–E | Gap B fixed, gap A fixed *differently* (risk), gaps C/D/E open |
| `docs/review-01-foundation.md` | 66 lines | QA of theme/fast-connect | HIGH + 2 MEDIUM resolved; LOWs partly moot |
| `docs/review-02-subscription-bar.md` | 37 lines | QA of meta bar | 2 MEDIUM + 4 LOW; screen was rewritten in S3, re-verify needed |
| `docs/review-03-ping-methods.md` | 46 lines | QA of ping methods | MEDIUM (OkHttp churn) **fixed**; HIGH (HTTP probe) + LOWs open |
| `docs/review-04-auto-fallback.md` | 36 lines | QA of auto-fallback | BLOCKER + HIGH fixed; **2 MEDIUM still open** |
| `docs/review-05-auth.md` | 59 lines | QA of auth scaffold | HIGH fixed; token-encryption MEDIUM **fixed**; double-fetch MEDIUM **open** |
| `docs/review-06-announce-pin.md` | 48 lines | QA of announce/pin | 3 LOWs, all still open (cosmetic) |
| `docs/review-07-flags-memory-notif.md` | 38 lines | QA of flags/memory/notification | 2 LOWs (flag accuracy) **still open verbatim** |
| `docs/impl-fix-autofallback.md` | 508 lines | Fix plan for review-04 | §0 items landed; **§1, §2, §3 not implemented** |
| `docs/impl-s3-servers.md` + `impl-s3-report.md` | 340 + 88 | Servers redesign spec + report | Shipped |
| `docs/impl-s4-settings.md` | 335 lines | Settings-tab spec | Shipped, **but §4 promise broken** (see §2.1) |
| `docs/impl-s5-connection-detail.md` | 326 lines | Connection/Provider/Theme detail spec | Provider partly shipped, **Connection + Theme/Language pickers not shipped** |
| `docs/impl-module9-tv-report.md` | 124 lines | TV/QR transfer report | Shipped; its "entry points not wired" follow-up **is now done** |
| `docs/compile-review-c942766.md` | 119 lines | Merge compile risk | Its M1 **fixed**; its "SettingsActivity orphan" note **still true** |
| `docs/compile-review-final.md` | 179 lines | HEAD `f179ddf` compile risk | B1 blocker **resolved by deleting the 19 `when` branches** → 19 features amputated (see §2.2) |
| `docs/module4-auth-impl.md` | 51 lines | Auth scaffold report | Superseded — auth is now a full account stack |
| `/home/user/dp/README.md` | 32 lines | — | **Unmodified upstream v2rayNG README.** No departament branding, no build/deploy notes, wrong project name |
| `/home/user/v2rayN/README.md` | 78 lines | — | **Unmodified upstream v2rayN README.** Same problem |

Docs present in `docs/` but outside this task's list (read only for cross-reference of IDs):
`circumvention-settings-design.md`, `design-home-polish.md`, `design-review-c942766.md`,
`design-system-2026.md`, `design-tg-and-settings-trim.md`, `happ-parity-details.md`,
`hidden-templates-design.md`, `incy-*.md`, `memory-panel-design.md`, `notification-design.md`,
`ping-methods-design.md`, `server-flags-design.md`, `smart-tv-transfer-design.md`,
`subscription-meta-bar-design.md`, `telegram-auth-design.md`, `ux-recommendations.md`.
`docs/qa-audit-report.md` referenced by `roadmap-wave3.md:13` **does not exist**.

---

## 1. Consolidated backlog — everything the docs say is still open

De-duplicated across all documents. "Verified" = I checked the current source; the file:line is the
evidence. Priority column is the *reconciled* priority (doc priority, adjusted where code has moved).

### 1.1 P0 — correctness / shipping blockers

| # | Item | From | Verified state in code |
|---|---|---|---|
| **P0-1** | **19 amputated menu actions.** `compile-review-final.md` B1 was resolved by option 2 (delete the `when` branches), not option 1 (restore the ids). The features are now unreachable **and their implementations are dead code**. | `compile-review-final.md:57-66` | `res/menu/menu_main.xml` has only 4 items; `MainActivity.kt:1940-1970` handles only `import_qrcode`/`import_clipboard`/`tv_send`/`import_manually_vless`/`sub_update`. Orphaned private funcs with **zero callers**: `importManually` (`MainActivity.kt:1972`), `exportAll` (`:2165`), `delAllConfig` (`:2179`), `delDuplicateConfig` (`:2198`), `delInvalidConfig` (`:2217`), `sortByTestResults` (`:2236`), `locateSelectedServer` (`:2276`). Lost user-facing features: export all, ping all / real-ping all, restart service, delete all / duplicates / invalid, sort by test result, locate selected, manual import of vmess/ss/socks/http/trojan/wireguard/hysteria2/policy-group/proxy-chain, import from file |
| **P0-2** | **Advanced settings unreachable.** `impl-s4-settings.md §4` promised the raw `SettingsActivity` survives as "Расширенные настройки". It does not. | `impl-s4-settings.md:276-288`, `compile-review-c942766.md:95-98` | `SettingsActivity` is declared (`AndroidManifest.xml:89`) but has **no launch site** anywhere in `java/`. `res/xml/pref_settings.xml` declares 55 keys; **29 of them now have no editing UI at all** — see the full list in §1.7 |
| **P0-3** | **Remnawave XRAY_JSON UA is hardcoded to a v2rayNG-family string, not operator-configurable.** The spec explicitly requires it to be configurable via `BackendConfig.subscriptionUserAgent` because the panel's UA→format mapping is operator-defined, and warns that v2rayNG-like UAs are exactly what Remnawave maps to **Base64**. | `remnawave-templates-spec.md:102-108, 181-206, 313-320`; `next-plan.md:54-89` (item A, P0) | `util/HttpUtil.kt:229-244` forces `"v2rayNG/${BuildConfig.VERSION_NAME}"` whenever the supplied UA does not contain `v2rayng`. `auth/BackendConfig.kt:22-24` `subscriptionUserAgent` exists but is **not consulted on this path**. `Accept: application/json` (spec §6.1) is **not** sent. The non-`Ex` fetch still sends `departament/<ver>` (`HttpUtil.kt:155-158`) |
| **P0-4** | **No user-visible logout.** | `next-plan.md:126-152` (item C) | `AccountViewModel.logout()` (`viewmodel/AccountViewModel.kt:400-410`) has **zero call sites**; `AccountSession.wipe()` is only reached from `AccountRepository.kt:72` on a confirmed-dead JWT. No "Выйти" string exists in `res/values/strings*.xml` |
| **P0-5** | **RAM panel can never be turned on.** Audit calls 13a DONE. | `master-requirements-audit.md:108` | `AppConfig.PREF_SHOW_MEMORY` is **read** at `ui/MainActivity.kt:1813` (default `false`) and **never written** anywhere in `java/`. No `row_memory` in `res/layout/layout_settings_content.xml`. The `card_memory` view (`res/layout/activity_main.xml:313`) is therefore permanently hidden |
| **P0-6** | **Six Provider-settings toggles are write-only (inert UI).** | `impl-s5-connection-detail.md:124-233` (§2.3–2.6, 2.8) | `ui/ProviderSettingsActivity.kt:39-46` declares `pref_provider_notify_on_update`, `_update_on_launch`, `_ping_on_launch`, `_ping_on_update`, `_sub_user_agent`, `_server_sort_order` as **private** constants. Grepping the whole `java/` tree, each string literal appears **only in that file** — nothing reads them. `SubscriptionUpdater` still notifies unconditionally; no update-on-launch, ping-on-launch, ping-on-update, global UA fallback, or sort-order is implemented. (Only `autoUpdate`/`updateInterval` and `PREF_SEND_HWID` are real: `SettingsManager.kt:380`, `AngConfigManager.kt:763,775,788`, `HttpUtil.kt:301-302`) |
| **P0-7** | **Full russification not finished** — `roadmap-wave3.md §1` is P0 and its exact key list is still partly untranslated. | `roadmap-wave3.md:42-112`; `next-plan.md:358-382` (item J) | `values/` = 777 strings, `values-ru/` = 456. 321 keys have no `values-ru` entry; of those, **24 have Latin-only values and are user-facing**, including the exact wave-3 keys: `auto_fallback_switching`, `color_theme_blue`, `color_theme_mono`, `memory_app_usage`, `memory_normal`, `memory_elevated`, `memory_high`, `menu_item_fast_connect`, `ping_method_real/http/tcp/icmp` (shown by the settings picker via `res/values/arrays.xml:175-181` → `MainActivity.pickPingMethod():2477`), `title_pref_ping_method`, `title_pref_auto_fallback`, `summary_pref_auto_fallback`, `title_pref_color_theme`, `title_pref_show_memory`, `summary_pref_show_memory`, plus `ps_user_agent`, `settings_ipv6`, `devices_diag_http`, `account_telegram`, `account_payment_error_body(_nodetail)` |
| **P0-8** | **Both READMEs are unmodified upstream boilerplate.** Neither names departament, neither documents the build/flavor/backend wiring. | task-listed files | `/home/user/dp/README.md` (32 lines, "# v2rayNG"), `/home/user/v2rayN/README.md` (78 lines, "# v2rayN") |

### 1.2 P1 — resilience / correctness hardening

| # | Item | From | Verified state |
|---|---|---|---|
| **P1-1** | **Auto-fallback fires on a single transient probe failure** — no confirmation re-probe. Spec calls this "REQUIRED-ish". | `review-04:17` (MEDIUM), `impl-fix-autofallback.md:288-369` (§3) | `ui/MainActivity.kt:573-583`: the `delayResultAction` observer switches server on the **first** `time < 0`. No `healthCheckConfirming`, no `healthRecheckRunnable`, no `HEALTH_CHECK_RECHECK_MS` anywhere in `java/` |
| **P1-2** | **`MSG_MEASURE_DELAY` channel is untagged** (§2 of the fix plan, marked REQUIRED). | `review-04:18`, `impl-fix-autofallback.md:180-284` (§2) | No `HEALTH_CHECK_TAG` / `testCurrentServerHealthCheck` in `java/`. `CoreServiceManager.measureV2rayDelay()` (`:359`) emits `MSG_STATE_DELAY_RESULT` unconditionally (`:391`). *Mitigating fact:* the manual "test connection" tap was deleted in the redesign — `testCurrentServerRealPing()` now has exactly one caller (`MainActivity.kt:169`, the health-check runnable) — so the cross-consume path is currently dormant, not fixed. Re-adding any manual test button re-opens the bug |
| **P1-3** | **`fallbackInProgress` explicit guard** (hardening; makes the anti-loop invariant legible). | `impl-fix-autofallback.md:72-176` (§1) | Not present. Invariant is still implicit in `MainActivity.kt:1449` (reset on user connect) + `:1879-1880` (gate) + `viewmodel/MainViewModel.kt:72` |
| **P1-4** | **Double subscription fetch on login.** | `review-05:30`, `next-plan.md:112-113` (B.3) | `auth/SubscriptionSyncManager.kt:56-57` still calls `AngConfigManager.updateConfigViaSub(...)` **and** `SubscriptionUpdater.syncOne(subId = guid)`; `SubscriptionUpdater.syncOne` (`:65-73`) enqueues a periodic worker with `REPLACE`, which runs immediately |
| **P1-5** | **HTTP ping method cannot differentiate servers** (review-03 HIGH) + `https://host:port` probe misclassifies non-TLS nodes (review-04 MEDIUM). | `review-03:36`, `review-04:19`, `impl-fix-autofallback.md:433-443`, `master-requirements-audit.md:81` | Not re-verified line-by-line, but no TCP-connect fallback exists in `handler/SpeedtestManager.kt` and the strings still read "HTTP GET /generate_204 (direct)" (`ping_method_http`) |
| **P1-6** | **Kill-switch / always-on surface**: only the deep-link half exists; no `PREF_KILL_SWITCH`, no full-tunnel guard. | `impl-s5-connection-detail.md:66-90` (§1.3), `next-plan.md:202-236` (E), `strategy-russia-2026.md:203-208` (#8) | `MainActivity.kt:2327` `rowAlwaysOn` → `openAlwaysOnSettings()` exists (honest deep-link, good). But `PREF_KILL_SWITCH` is absent from `AppConfig.kt:23-89`, and there is no `routingRulesetsBypassLan()` override |
| **P1-7** | **Auto-connect on app launch.** | `impl-s5-connection-detail.md:43-55` (§1.1), `next-plan.md:218-220` (E.1) | `PREF_AUTO_CONNECT` / `PREF_AUTO_CONNECT_ON_LAUNCH` absent from `AppConfig.kt` |
| **P1-8** | **Subscription-expiry reminder notifications.** | `next-plan.md:239-263` (F, P1) | No expiry logic in `handler/NotificationManager.kt` or `handler/SubscriptionUpdater.kt` (grep for `expire`/`expiry` in both returns nothing). No `PREF_SUB_EXPIRY_REMINDERS` |
| **P1-9** | **Expiry banner on Home ("истекает через N дней").** | `next-plan.md:141-142` (C.2) | Expiry is only rendered inside the meta bar (`MainActivity.kt:1297-1309`), no threshold banner, no ≤3-day CTA |
| **P1-10** | **Onboarding (2–3 slides) + VPN-permission priming.** | `roadmap-wave3.md:116-169` (§2, P1) | No `ui/OnboardingActivity.kt`, no `PREF_ONBOARDING_SHOWN`. `startVpnWithPermission()` (`MainActivity.kt:1470-1472`) goes straight to `VpnService.prepare(this)` with no primer sheet. *Partially superseded:* an empty-state/sign-in home exists (`updateHomeEmptyState()` `MainActivity.kt:679-703`, `updateBottomNavVisibility()` `:713`), but the spec'd onboarding and the permission primer are absent |
| **P1-11** | **"Russia mode" preset + uTLS enforcement + fragment presets.** | `roadmap-wave3.md:354-441` (§6a–6c, P1), `strategy-russia-2026.md:158-180` (#2–#4), R0.2 | `PREF_RUSSIA_MODE` absent. `core/CoreOutboundBuilder.kt:564` passes `profileItem.fingerPrint.nullIfBlank()` straight through — an empty uTLS fingerprint stays empty. `PREF_FRAGMENT_ENABLED` defaults to **false** (`CoreOutboundBuilder.kt:596`). No Mux-off-for-Vision rule. No SNI validation warning |
| **P1-12** | **Periodic liveness re-check / TLS-freeze detection while connected.** | `roadmap-wave3.md:396-408` (§6d, the most valuable item), `strategy-russia-2026.md:191-195` (#6), R1.2 | `PREF_LIVENESS_RECHECK` absent; `scheduleHealthCheckIfEnabled()` (`MainActivity.kt:1879`) is still one-shot per session |
| **P1-13** | **Resilient subscription fetch** — mirror URLs, fetch-through-tunnel, never-wipe-on-failed-refresh, randomized cadence. | `strategy-russia-2026.md:181-188` (#5), §3.4, R1.1 | Not implemented; `SubscriptionItem` has a single `url` (`dto/entities/SubscriptionItem.kt`) |
| **P1-14** | **In-app self-update through the tunnel + signature verification.** | `strategy-russia-2026.md:358-368` (§4.2), R1.4 | `CheckUpdateActivity` exists (`AndroidManifest.xml:192`) but has **zero launch sites** — even plain update-checking is unreachable |

### 1.3 P2 — feature depth

| # | Item | From | Verified state |
|---|---|---|---|
| **P2-1** | **Per-app split tunneling Incy redesign** (switch rows, grouped Пользовательские/Системные, sticky headers, DiffUtil, in-screen search). | `roadmap-wave3.md:173-233` (§3) | `res/layout/item_recycler_bypass_list.xml:63` still uses `@+id/check_box`; no `SwitchMaterial`, no section headers |
| **P2-2** | **Daily traffic statistics (↑/↓ per day, 30-day retention, bar chart).** | `roadmap-wave3.md:237-291` (§4) | No `handler/DailyTrafficManager.kt`, no `PREF_SHOW_DAILY_TRAFFIC`, no `ui/TrafficStatsActivity.kt` |
| **P2-3** | **Haptics behind a preference.** Haptics themselves shipped. | `roadmap-wave3.md:322-325` (§5.4) | Implemented without a gate: `util/MotionUtils.kt:56,63` + `MainActivity.kt:1563` (`HapticFeedbackConstants.CONFIRM`). `PREF_HAPTICS` absent → users can't disable |
| **P2-4** | **Flag accuracy** — require explicit `[XX]`/leading token; drop or map `UK`→`GB`. | `review-07:28-29`, `master-requirements-audit.md:70,135`, `next-plan.md:430-431` | **Still open verbatim.** `util/FlagUtil.kt:97-101` matches any word-boundaried 2-letter token in `ISO2_CODES`, so "No limit"→🇳🇴, "IT support"→🇮🇹, "in-1"→🇮🇳. `FlagUtil.kt:144` still contains `"UK"`, and `codeToFlag()` (`:75-86`) has no UK→GB remap, so it emits the non-existent 🇺🇰 glyph |
| **P2-5** | **Flags from host geoip rather than remark text.** | `master-requirements-audit.md:70` (6b) | `FlagUtil.resolveFlag()` (`:26-31`) only reads `profile.remarks` |
| **P2-6** | **Quick-tile / widget polish** (3 honest states, live speed, server name, single broadcast source). | `next-plan.md:301-324` (H) | `service/QSTileService.kt` and `receiver/WidgetProvider.kt` exist; polish not verified as done — no evidence of a shared broadcast state source |
| **P2-7** | **DNS-leak discipline, `sort-order` header, per-node liveness memory.** | `next-plan.md:328-354` (I) | None present. Note `pref_provider_server_sort_order` (`ProviderSettingsActivity.kt:44`) is a *user* pref that nothing reads — not the subscription-header `sort-order` the doc asks for |
| **P2-8** | **Parallel ranking scan before fast-connect + cached "best".** | `next-plan.md:267-298` (G) | Not present. *Done from that item:* one shared `OkHttpClient` (`handler/SpeedtestManager.kt:35-37`) |
| **P2-9** | **Circumvention self-serve UX** (presets, global uTLS fallback). | `master-requirements-audit.md:98` (11), `circumvention-settings-design.md` | Partially: `row_mux`, `row_mux_concurrency`, `row_fragment` exist in `layout_settings_content.xml:568,634,696`. But fragment packets/length/interval and all uTLS knobs have **no UI** (see §1.7) |
| **P2-10** | **Meta-bar review-02 items:** ping spinner driven by a fixed 3 s timer; refresh scoped to `mainViewModel.subscriptionId` not the visible provider; missing `skipCount` toast; missing `isBindingInitialized` guard; `hasUserInfo` can't distinguish absent-vs-zero header. | `review-02:26-30`, `impl-fix-autofallback.md:409-431` | The hosting screen was rewritten in S3 (logic moved into `MainActivity`), so these need **re-verification against the new code**, not blind carry-over |
| **P2-11** | **Announce/pin LOWs:** `base64:` prefix leaks into the banner on decode failure; URL-safe base64 unsupported; redundant tint+colorFilter on `btn_pin`. | `review-06:22-24` | Cosmetic; not re-verified |
| **P2-12** | **ICMP ping fails for IPv6 literals**; HEAD-vs-GET wording drift. | `review-03:38-39` | Not re-verified |
| **P2-13** | **`getSerializableExtra` deprecated overload** (API 33+). | `review-04:20`, `impl-fix-autofallback.md:456-461` | Warning-level only |

### 1.4 P3 — platform / strategic / proposals never started

| # | Item | From | Verified state |
|---|---|---|---|
| **P3-1** | App-icon `activity-alias` chooser | `master-requirements-audit.md:64` (5c), `next-plan.md:434`, `impl-s4-settings.md:214` | **Zero** `activity-alias` in `AndroidManifest.xml` |
| **P3-2** | Theme picker / Language picker as dedicated Incy screens | `impl-s5-connection-detail.md:236-263` (§3) | No `ThemePickerActivity` / `LanguagePickerActivity`. Replaced by in-place dialogs (`MainActivity.kt:2335-2336` → `pickAppearance()` / `pickLanguage()`) — acceptable substitute, note the doc as superseded |
| **P3-3** | Dedicated `ConnectionSettingsActivity` | `impl-s5-connection-detail.md:30-120` (§1) | Not created; rows live inline in the settings tab |
| **P3-4** | Language globe in the top bar | `next-plan.md:435` (L.5) | Not present |
| **P3-5** | Multi-chip protocol + gold JSON chip on rows | `next-plan.md:432` (L.3) | Shipped in S3 (`impl-s3-report.md:26-28`) — **close this doc item** |
| **P3-6** | AmneziaWG transport | `strategy-russia-2026.md:196-201` (#7), R2.1 | Not present (fork has WireGuard only) |
| **P3-7** | Multi-subscription-group failover; SS-2022+plugin template validation; allow-listed-SNI hint set | `strategy-russia-2026.md` R2.2–R2.4 | Not present |
| **P3-8** | RU diagnostics panel ("why can't I connect"); RU-whitelist split-tunnel preset; randomized timing | `strategy-russia-2026.md` R3.1–R3.3 | Not present |
| **P3-9** | **M1–M13** (adblock; app-lock+panic wipe; DNS control center; throughput speed test; data-usage dashboard; TV/Leanback D-pad UI; connection scenes; guided MTU/Mux tuning; routing preset library + geo auto-update; Clash/sing-box importer; leak test; OEM background guardian; multi-hop UX) | `new-modules-proposals.md` | **None implemented.** No `PREF_BLOCK_ADS`, no `ui/AppLockActivity.kt`, no `ui/DataUsageActivity.kt`, no `fmt/ClashFmt.kt`/`SingBoxFmt.kt`, no biometric dependency. TV *transfer* shipped (`tv/` package) but the D-pad/Leanback UI of M6 did not |
| **P3-10** | **N1–N11** (bypass linter; clock-skew guard; post-connect reachability/captive-portal; inline glossary; smart clipboard/gallery-QR import; connection event timeline; sub-update diff; lite mode; manual rotate/avoid; pre-flight checklist; redacted support bundle) | `new-modules-proposals-3.md` | **None implemented.** No `handler/BypassLinter.kt`, `util/TimeCheck.kt`, `handler/ConnectionEventLog.kt`, `util/SupportBundle.kt`, no `PREF_LITE_MODE` |
| **P3-11** | Cross-platform core contract document + discipline | `next-plan.md:385-416` (K) | The desktop app exists and is large (`/home/user/v2rayN/v2rayN.Desktop/Views/` has `AccountView`, `BuyView`, `CompactHomeView`, `CompactServersView`, `AccountSyncView`, `BottomNavBar`, …), but **no shared core-contract doc exists** and neither repo's README mentions the other |
| **P3-12** | Responsive dimens buckets for tablets/landscape | `master-requirements-audit.md:116,144` (14b) | Not verified; carried forward |

### 1.5 Remnawave XRAY_JSON — per-gap status (`remnawave-templates-spec.md §3`)

| Gap | Description | Status |
|---|---|---|
| **A** | Panel never asked for xray-json (UA negotiation) | **Changed, not per spec** — see P0-3. `HttpUtil.kt:229-244` forces a v2rayNG-family UA; not operator-configurable; no `Accept: application/json` |
| **B** | Brittle 3-substring JSON detection | ✅ **FIXED.** `handler/AngConfigManager.kt:562-577` now keys off `trim().startsWith("{"/"[")` + `contains("outbounds")`, exactly as `remnawave-templates-spec.md:208-216` prescribes |
| **C** | Residual vendor `remnawave` root object not stripped | ❌ **OPEN.** No `fmt/TemplateValidator.kt`; `ls java/com/v2ray/ang/fmt/` shows only the 9 protocol formatters. No code strips a root `remnawave` key |
| **D** | Client-side `injectHosts` | ✅ Correctly **not needed** for the Remnawave path (branch A) |
| **E** | No HTTPS enforcement for locked subscriptions | ❌ **OPEN.** `handler/AngConfigManager.kt:753` still honours `it.subscription.allowInsecureUrl` regardless of `locked` |

### 1.6 Auth / account — per-item status (`next-plan.md` B/C/D, `review-05`)

| Item | Status |
|---|---|
| Token encryption at rest | ✅ **DONE.** `auth/AuthTokenStore.kt:17-46` uses `MMKV.mmkvWithID(ID, SINGLE_PROCESS_MODE, cryptKey)` from `auth/KeystoreKeyProvider.kt` (AES/GCM in AndroidKeyStore, graceful null fallback) |
| 401 → refresh → logout | ⚠️ **Superseded by design.** `auth/AuthManager.kt:13` documents "NO refresh/logout here — the JWT is 7-day and non-refreshable". `refreshIfNeeded` no longer exists anywhere. `auth/AccountRepository.kt:23-27,71-72` deliberately wipes only when the identity endpoint confirms the JWT is dead. **Remaining gap:** nothing proactively handles a 7-day expiry, and there is no user logout (P0-4) |
| De-dupe initial sub fetch | ❌ **OPEN** (P1-4) |
| Account screen | ✅ Shipped — `ui/AccountFragment.kt`, `viewmodel/AccountViewModel.kt`, `ui/DeviceManagementActivity.kt`, `res/layout/activity_account.xml`, `nav_account` tab (`res/layout/activity_main.xml:661`) |
| In-app payments (item D) | ✅ Shipped, far beyond the doc — `ui/BuyTariffActivity.kt`, `ui/PaymentMethodSheet.kt`, `ui/PaymentHistoryActivity.kt`, `auth/dto/PaymentDtos.kt`, endpoints `payPlatega` / `payBalance` / `payments` / `publicTariffs` (`auth/BackendConfig.kt:33,60-63`). **`next-plan.md §D`'s Telegram-invoice/Play-flavor design is obsolete — rewrite or delete it.** Also note the Play-policy risk it raised is unaddressed in docs |

### 1.7 The 29 preferences with no editing UI (evidence for P0-2)

Keys declared in `res/xml/pref_settings.xml` whose `AppConfig` constant is referenced by **no**
Activity/Fragment other than the orphaned `SettingsActivity`:

```
pref_sniffing_enabled              pref_local_dns_enabled          pref_fake_dns_enabled
pref_local_dns_port                pref_vpn_mtu                    pref_vpn_interface_address_config_index
pref_prefer_ipv6                   pref_domestic_dns               pref_dns_hosts
pref_outbound_domain_resolve_method pref_mux_xudp_concurrency      pref_mux_xudp_quic
pref_fragment_packets              pref_fragment_length            pref_fragment_interval
pref_delay_test_url                pref_ip_api_url                 pref_core_loglevel
pref_use_hev_tunnel_v2             pref_hev_tunnel_loglevel        pref_hev_tunnel_rw_timeout_v2
pref_auto_remove_invalid_after_test pref_auto_sort_after_test      pref_real_ping_concurrency
pref_double_column_display         pref_group_all_display          pref_start_scan_immediate
pref_dynamic_socks_port
```
(plus `pref_show_memory`, which is read but never written — P0-5.)

Note the censorship-critical ones in that list: **fragment packets/length/interval**, **FakeDNS**,
**domestic DNS**, **DNS hosts**, **MTU** — i.e. the exact knobs `strategy-russia-2026.md §3.2–3.5`
and `roadmap-wave3.md §6` depend on are now un-tunable from the UI.

### 1.8 Activities with no launch site (dead entry points)

| Activity | Manifest | In-app launch sites |
|---|---|---|
| `ui/SettingsActivity` | `AndroidManifest.xml:89` | **0** |
| `ui/CheckUpdateActivity` | `:192` | **0** |
| `ui/SubSettingActivity` | `:110` | **0** |
| `ui/LogcatActivity` | `:101` | **0** (contradicts `impl-s4-settings.md:250`, which puts "Логи туннеля" under ОТЛАДКА) |
| `ui/AppPickerActivity` | `:95` | **0** |
| `ui/TaskerActivity` | `:307` | 0 in-app — **fine**, it has a `com.twofortyfouram.locale…EDIT_SETTING` intent-filter (`:310-312`) |
| `ui/UrlSchemeActivity` | `:163` | 0 in-app — **fine**, deep-link entry; `UrlSchemeListActivity` is the settings row (`MainActivity.kt:2355`) |

---

## 2. Items the docs claim are DONE that are not (or no longer) true

### 2.1 `impl-s4-settings.md §4` — "the old SettingsActivity survives as Расширенные настройки"
**False.** No settings row launches it (§1.8), so the whole raw-preference surface and 29 keys are
orphaned (§1.7). The spec's own commit plan step 4 (`impl-s4-settings.md:311-316`) says to delete the
drawer *"after all dispatch has moved"* — the "Расширенные настройки" row from its own row inventory
(`impl-s4-settings.md:243`) was never built.

### 2.2 `compile-review-final.md` B1 — "fix: restore the 19 ids (recommended)"
The **non-recommended** option was taken. `res/menu/menu_main.xml` now has 4 items; the `when`
branches are gone (`MainActivity.kt:1940-1970`); and seven implementations are dead code
(`:1972, :2165, :2179, :2198, :2217, :2236, :2276`). The build is green, but the doc's own warning —
*"безвозвратно отрежет функции (экспорт, ping all, restart, удаление дублей/невалидных, ручной импорт
vmess/ss/socks/…)"* (`compile-review-final.md:64-66`) — is exactly what happened. **This is not
recorded anywhere as an accepted trade-off.**

### 2.3 `master-requirements-audit.md:108` — "13a RAM panel **DONE**"
The panel exists but is unreachable: `PREF_SHOW_MEMORY` has no writer and defaults to `false`
(`MainActivity.kt:1813`). See P0-5.

### 2.4 `master-requirements-audit.md:26` — "1d current-server line w/ flag **PARTIAL**"
Now **removed entirely**. `res/layout/activity_main.xml` (ids at `:5-703`) has no `layout_server_info`
/ `tv_selected_server`; the home hero shows only `tv_connection_status` (`:296`). The item is no
longer "add a flag to the row" — the row itself is gone. Same for **1f "Проверить" pill**: no
`tv_test_state` exists (confirmed by `compile-review-final.md:113-114`, still true).

### 2.5 `master-requirements-audit.md:14,127-131` — "S3 TODO / S4 TODO / S5 TODO"
All three shipped since the audit was written. S3: `res/layout/layout_servers_header.xml`,
`rv_servers` (`activity_main.xml:474`), `layout_empty` (`:481`), `GroupServerFragment`/
`GroupPagerAdapter` deleted. S4: `group_settings` (`:493`) + `res/layout/layout_settings_content.xml`
with 23 rows, wired at `MainActivity.kt:2320-2356`, drawer gone. S5: `ui/ProviderSettingsActivity.kt`,
`ui/LocalProxyActivity.kt`, always-on row. **The audit matrix should be regenerated — using it as-is
will send work at already-done items.**

### 2.6 `master-requirements-audit.md:88` — "9 Hidden JSON templates **TODO** (design only, no code)"
**Now implemented:** `template/TemplateManager.kt`, `template/TemplateCrypto.kt`, `ProfileItem.locked`,
`SubscriptionItem.locked`, locked gating in `ui/ServerActionsSheet.kt:46-53`. Stale row.

### 2.7 `master-requirements-audit.md:103` — "12 Smart TV **PARTIAL**, QR transfer TODO"
**Now implemented and wired**: `tv/` package (5 files), plus the entry points that
`impl-module9-tv-report.md:117-123` explicitly deferred — `rowTvSend` (`MainActivity.kt:2346`),
`rowTvReceive` (`:2351`), and `R.id.tv_send` in the add menu (`res/menu/menu_main.xml`,
`MainActivity.kt:1951`). Its documented follow-ups **remain open**: HTTPS/AEAD transport hardening
(`impl-module9-tv-report.md:98-104`) and the D-pad/overscan pass on the phone UI (`:118-122`).

### 2.8 `compile-review-c942766.md` M1 — `ServerActionsSheet.isLocked` stub
**Fixed.** `ui/ServerActionsSheet.kt:69-71` now returns `TemplateManager.isLocked(profile)`. Close it.

### 2.9 `impl-fix-autofallback.md:24-33` — "§0 DONE" table
Accurate: `MainViewModel.autoFallbackUsed` (`viewmodel/MainViewModel.kt:72`), reset only on user
connect (`MainActivity.kt:1449`), gate (`:1879-1880`), exclude-just-failed
(`fastConnect(excludeGuid = …)` at `:581`). But the doc's **§1/§2/§3 are all still unimplemented** —
and the doc's own "recommended minimum" was commits 1 and 2 (§2 + §3). See P1-1/P1-2/P1-3.

### 2.10 `review-07:28-29` — flag LOWs described as "consider"
Both are **still exactly reproducible** in `util/FlagUtil.kt:97-101` and `:144`. A year of work has
passed over this file without them being addressed. See P2-4.

### 2.11 `review-03:37` — OkHttp client churn (MEDIUM)
**Fixed.** `handler/SpeedtestManager.kt:35-37` hoists a single lazy `OkHttpClient` with the comment
"One OkHttpClient shared across direct HTTP probes". Close it.

### 2.12 `impl-s5-connection-detail.md §2.3–2.6, 2.8` — provider rows described as implementable prefs
The **UI shipped, the behaviour did not** — six toggles persist a value nothing reads (P0-6). This is
worse than "not done", because the settings screen tells the user a feature is on.

### 2.13 `roadmap-wave3.md:106-110` — russification acceptance criteria
*"`comm -23` ключей `values/` и `values-ru/` → пусто"* is **not** met: 321 keys, 24 of them
user-visible English. The specific keys the doc listed in `roadmap-wave3.md:52-61` are still
untranslated one year on. See P0-7.

### 2.14 `new-modules-proposals-3.md:20-34` — "Docs the project says are in progress and are therefore
treated as covered: notification-design, server-flags-design, smart-tv-transfer-design,
memory-panel-design, circumvention-settings-design"
Only notification + smart-tv are genuinely done. `memory-panel-design` is unreachable (P0-5),
`server-flags-design` has open accuracy bugs (P2-4/P2-5), `circumvention-settings-design` lost most of
its knobs to the settings-tab migration (P2-9, §1.7). **The round-3 non-duplication assumption is
invalid — modules were excluded from proposal on the basis of work that isn't finished.**

### 2.15 `next-plan.md:20-29` "Опорные факты о текущем коде"
Half of these are now wrong (`ui/MainActivity.kt:442-482 setupHomeAccount/subscriptionStatusLine`,
`:238-271`, `:974` no longer correspond to anything — the file is now ~2700 lines with a completely
different layout). Every line-number reference in `next-plan.md`, `roadmap-wave3.md`,
`impl-fix-autofallback.md` and `master-requirements-audit.md` should be treated as **unreliable**.

---

## 3. Recommended sequencing (my reconciliation, not a doc quote)

1. **P0-1 + P0-2 together** — restore the amputated menu ids *or* surface those 7 dead functions in
   the new Settings/Servers header, and add the "Расширенные настройки" row. One change re-opens
   ~19 features and 29 preferences.
2. **P0-6** — either implement the six provider behaviours or hide the rows. Shipping inert toggles is
   the worst option currently in the tree.
3. **P0-4, P0-5** — logout row + memory toggle row. Both are single rows in
   `layout_settings_content.xml` + a handler in `MainActivity.setupSettings()`.
4. **P0-3** — make the subscription UA operator-configurable (`BackendConfig.subscriptionUserAgent`)
   with the v2rayNG string as the *default*, and add `Accept: application/json`. Also close gap C
   (strip root `remnawave`) and gap E (HTTPS for locked subs).
5. **P0-7** — translate the 24 leaked strings; decide explicitly whether `values/` stays Russian
   (then `values-en/` is needed for fa/zh/en users, who currently see Russian).
6. **P1-1/P1-2** — auto-fallback re-probe + tagged health-check channel (`impl-fix-autofallback.md`
   §2/§3 are already written as line-level patches; they only need porting to the new line numbers).
7. **P1-11/P1-12** — Russia mode + periodic liveness. This is the highest product value left in the
   docs and nothing has been started.
8. Then P2/P3 by the tables above.

**Doc hygiene tasks that should accompany the above:** regenerate
`master-requirements-audit.md`; delete or rewrite `next-plan.md §D` (payments shipped differently);
mark `module4-auth-impl.md` superseded; either write `docs/qa-audit-report.md` or fix the dangling
reference at `roadmap-wave3.md:13`; and replace both READMEs.
