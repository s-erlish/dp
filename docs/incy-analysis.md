# INCY VPN — source hunt + feature analysis for "departament VPN"

**Status:** Design / analysis only. **No app code is changed by this document.**
**App:** v2rayNG / Xray-core fork, package `com.v2ray.ang`, Kotlin, XML Material3 views, label **departament** (Remnawave-backed subscription).
**Task:** Find the Incy source, read how it implements the screenshot features, and turn that into a prioritized plan for *this* app. The #1 deliverable is the **announce / status banner**.

---

## 0. Did we find the Incy repo? (read this first)

**Partly — and the important half is public.** Incy's **Android app is NOT open source.** The shipping app is `llc.itdev.incy` on Google Play, closed-source.[^play] What *is* public is the **INCY-DEV GitHub org** and its **provider-integration documentation**, which specifies the exact subscription/announce protocol the screenshots exercise — that is what we actually need.

INCY-DEV org: **https://github.com/INCY-DEV** — 5 public repos:[^org]

| Repo | What it is | Use to us |
|---|---|---|
| **incy-platforms** (677★) | Release binaries + download/version info. No app source. | Confirms closed-source app; version tracking. |
| **incy-docs** (4★) | *"INCY developer documentation — integration guide for VPN providers"* (MkDocs, RU + `.en.md`). | **The prize** — documents `announce`, `support-url`, headers, base64 rules, formats. |
| **incy-link-encoder** (2★, TypeScript) | Encodes a subscription URL into an `incy://crypt1/<payload>` deep link (AES-256-GCM). | Deep-link import format; **not** secrecy (key is shared/hardcoded).[^encoder] |
| **incy-icons** (5★) | App icon set. | Cosmetic only. |
| **.github** | Org profile. | — |

So: **no app code to copy line-for-line**, but the `incy-docs` **subscription protocol is fully specified** and is a near-superset of Happ's. I fell back to the INCY-DEV docs + Happ dev-docs + the screenshots as instructed; **nothing below is fabricated from an app repo that does not exist.** Where a claim comes from the closed app's behavior, it is sourced to the INCY-DEV docs' "Rendering" notes, not invented.

**Core engine:** Incy is **Xray-based** (not sing-box): `incy-docs` ships a `full-xray-config.md` page and states it accepts *"full xray configurations"* JSON with `inbounds/outbounds/routing/dns`.[^subfmt] Protocols parsed: **VLESS, VMess, Trojan, Shadowsocks (SIP002), Hysteria2, SOCKS5, WireGuard, AmneziaWG**; `ssr:// tuic:// hysteria://`(v1) are *recognized but skipped*.[^subfmt] That maps 1:1 onto our Xray fork — **the protocol/core surface is already ours; parity work is UI + subscription-header parsing, not an engine swap.**

---

## 1. THE ANNOUNCE / STATUS BANNER (highest priority)

> **Reconciliation note:** `announce`, `support-url`, and `profile-web-page-url` are **already designed** in
> `docs/happ-parity-details.md` (details #3/#4/#8, §0 directive table) and touched in
> `docs/hidden-templates-design.md`. This section does **not** re-plan them — it (a) adds the **Incy-specific
> exactness** those docs lack, and (b) confirms the storage/render seam. Treat `happ-parity-details.md` §0 +
> details #2/#3/#4/#8 as the base plan; treat this as the Incy-sourced amendment.

### 1.1 How Incy delivers the banner (verbatim from `incy-docs/app-management`)

Incy drives the banner from the **subscription HTTP response**, exactly like Happ, via **two channels with a defined priority**:[^appmgmt]

1. **HTTP response headers** — *highest priority*.
2. **Body comments** prefixed with `#` — fallback used only when the header is absent.

The banner directive:

```
announce: <plaintext>            ← header form
announce: base64:SGVsbG8...      ← base64 form (needed for emoji + newlines)
#announce: <plaintext>           ← body form (first lines of the decoded body)
```

Incy-documented rules (these are the details our existing docs don't state):[^appmgmt][^subfmt]

- **Max 200 chars**, **plaintext or `base64:` prefix** (UTF-8). Base64 also accepts **URL-safe** alphabet (`-`/`_`).
- **Multi-line: "up to 5 lines."** The screenshot banner is 2 lines (`Без рекламы на YouTube…\nЕсли не работает…`). Newlines survive because the value is base64 (a raw header can't carry `\n`).
- **`0` clears** the banner (same convention as Happ / `sub-info-text`).
- **Headers are case-insensitive** (`Announce` == `announce`).
- **`announce-url`** *(Incy extra, not in Happ)*: a URL notice — when present it renders as a **clickable link that replaces the inline text**. Worth a field even if we render text-only first.
- Rendering per Incy docs: a **"dismissible alert in the subscription card."** (Happ shows it non-dismissible above the list. Pick one; dismissible-per-update is the nicer behavior.)

**Support / website buttons** (the "Поддержка" button in the screenshot):[^appmgmt][^happ]

- **`support-url`** → button; **`t.me/*` auto-shows the Telegram glyph**, any other URL a generic link glyph. This is the screenshot's **"Поддержка"** button (`@departamentvpn`).
- **`profile-web-page-url`** → a second button (Incy: *"blue icon on left of the status bar; gray if unset"*; fallback header `homepage`). Distinct from support; opens the panel's user page.

**Adjacent Incy header extras worth capturing in the same pass:**
- **`profile-description`** — a subtitle line under the title (base64 ok; header-only, no body form).[^appmgmt]
- **`sort-order`** = `ping | name | none` — server-list ordering hint.[^subfmt]
- **`subscription-userinfo`** — already implemented here; **Incy note:** `expire` **auto-converts ms→s when `>32e9`.** Our `SubscriptionUserInfo.parse` should adopt that guard (a Remnawave panel emitting ms would otherwise show a year-5000 expiry). Field already stored: `SubscriptionItem.expire` (epoch **seconds**).[^appmgmt]

### 1.2 There is ALSO a second, separate "notification" system (don't confuse them)

`incy-docs/provider-notifications` documents a **push** system distinct from the subscription banner: **FCM (Android) / APNs (iOS)**, title ≤100, body ≤500, optional image URL, action URL, button label, enterprise modal timer 1–10 s, with `deliveredCount`/`failedCount` telemetry.[^notif] **The screenshot banner is the *subscription* `announce`, not this push channel.** Push is a much larger, out-of-scope build (needs Firebase + a provider backend); note it as a P3 "future" and do **not** conflate it with the banner. The subscription `announce` gives ~90% of the perceived value at S effort.

### 1.3 How it maps into THIS codebase (real seams)

Storage — extend `dto/entities/SubscriptionItem.kt` (currently ends at the `userInfoUpdated` field, line 22; append defaulted fields so old JSON still deserializes, exactly as the traffic fields did):[^subitem]

```kotlin
// --- Incy/Happ subscription directives ---
var announce: String = "",       // decoded, ≤200 chars, may contain \n; "" or "0" == hidden
var announceUrl: String = "",    // Incy `announce-url`: link notice
var supportUrl: String = "",     // t.me/* → Telegram glyph, else link glyph
var webPageUrl: String = "",     // profile-web-page-url (fallback: homepage)
var description: String = "",    // profile-description subtitle
```

Capture — the fetch already surfaces exactly one header today: `util/HttpUtil.kt:201` `UrlContentResult(body, subscriptionUserInfo)` and `:247` reads only `subscription-userinfo`.[^http] Widen `UrlContentResult` to carry a `Map<String,String>` of the fixed Incy/Happ key-set (read case-insensitively from `response.headers`), and add a `#`-body-line scan (a small `util/HappDirectives.kt`/`IncyDirectives.kt`, mirroring `util/SubscriptionUserInfo.kt`). Decode the `base64:` prefix there. HTTP header wins over `#body` per Incy priority.

Persist — one existing block: `handler/AngConfigManager.kt:591–602`, right beside where `SubscriptionUserInfo` is already written back onto `it.subscription`.[^angcfg] Add the five setters there.

Render — `layout_subscription_meta_bar.xml` today has **Row 1** (`tv_sub_title:27`, `progress_action:39`, `btn_ping:48`, `btn_refresh:60`) and **Row 2** traffic (`tv_traffic:89`, `tv_expiry:99`, `progress_traffic:109`) — **no announce / support / web / description views exist yet.**[^meta] Add, bound in `GroupServerFragment.bindMetaBar()`:
- a **`tv_announce`** `TextView` **below `progress_traffic`** (Row 3), `maxLines=5`, `android:autoLink` off (we control links), tinted `?attr/colorOnSurfaceVariant` on a subtle `bg_server_card` inset; `visibility=GONE` when `announce.isBlank() || announce=="0"`;
- **`iv_support`** + **`iv_web`** `ImageView`s in the Row-1 action cluster next to `btn_refresh` (glyph chosen by `supportUrl.contains("t.me")`; `iv_web` tinted gray + disabled when `webPageUrl` blank), `setOnClickListener { Utils.openUri(...) }`;
- optional `tv_sub_desc` under `tv_sub_title` for `description`.

That is the whole banner: **5 fields + 1 widened result + 1 body-scan util + 1 persist block + 3 views.** Effort **S–M**, and it reuses the seam `subscription-userinfo` already proved out.

---

## 2. Prioritized feature catalog (everything notable in the Incy screenshots)

Effort: **S** ≤½day · **M** 1–2d · **L** 3d+/cross-cutting. "Existing doc" = already planned elsewhere; do not duplicate.

### P0 — the banner cluster (this doc, §1)
| # | Feature (screenshot) | What / why the owner likes it | New? | Effort | Maps to |
|---|---|---|---|---|---|
| 1 | **Announce banner** under the traffic bar | Live operator message ("Без рекламы на YouTube… @departamentvpn"); the owner runs a channel and wants to talk to users in-app | see note | **S** | §1.3; base plan `happ-parity-details.md` #3 |
| 2 | **"Поддержка" / support button** (`support-url`, t.me glyph) | One-tap line to `@departamentvpn`; retention + support deflection | see note | **S** | §1.3; `happ-parity-details.md` #4 |
| 3 | **Website button** (`profile-web-page-url`) | Opens panel user page; gray when unset | see note | **S** | §1.3; `happ-parity-details.md` #8 |
| 4 | **Directive-capture seam** (headers + `#body`, base64, priority, ms→s) | Unlocks 1–3 + description + sort-order at once | Yes | **M** | §1.3; `happ-parity-details.md` #2 |

("New? = see note" → already designed in `happ-parity-details.md`; Incy exactness added here.)

### P1 — the main-screen chrome
| # | Feature | What / why | New? | Effort | Maps to |
|---|---|---|---|---|---|
| 5 | **Stats row** `↑ 26 B/s · 🕐 3:11:08 uptime · ↓ 40 B/s` | At-a-glance liveness; uptime timer reads as "solid connection" | partial | **S** | Up/down already exist: `MainActivity.kt:174–175` (`tvUploadSpeed`/`tvDownloadSpeed`). **Gap = uptime timer**: start a ticker on `applyRunningState(isRunning=true)` (`:307`), format `H:MM:SS`, show near the speeds. |
| 6 | **Shield connect button + glowing ring**, ring color = state | Emotional centerpiece; ring color = instant status read | partial | **M** | `binding.cardConnect` + `handleFabAction():231`, `applyRunningState():307` (idle/connecting/connected), `setTestState():303` already model the states. Add a ring drawable / `CircularProgressIndicator` behind the FAB colored by state (`colorPing`/`colorPingRed`/`colorPrimary`). Tokens: `design-system-2026.md`. **See `happ-parity-details.md` #11.** |
| 7 | **Selected-server line** (EU flag + "Hybrid (Автовыбор)") | Shows *what you'll connect to*; "Hybrid" = auto-select balancer | partial | **M** | `tvSelectedServer` at `MainActivity.kt:344`. "Hybrid/Autoselect" = existing **`EConfigType.POLICYGROUP` balancer** — surface as a first row. Flag emoji via `FlagUtil`. **See `server-flags-design.md` + `happ-parity-details.md` #9.** |
| 8 | **Memory card** "Память приложения / 25 MB · Норма" + green dot | Owner cares about a *light* app; honest MB + status reads as quality | Yes | **M** | **`activity_main.xml` has no memory card today** (confirmed). **Do not re-plan — `docs/memory-panel-design.md` is the full design.** Reference only. |
| 9 | **"Проверить" (Check) connectivity button** | Manual "is my tunnel actually working?" probe | partial | **S** | Reuses connection-test infra (`setTestState():303`, `updateTestResultAction` observer `:172`). Add a labeled button that runs the real-delay/URL test. Method choice: **`docs/ping-methods-design.md`.** |
| 10 | **"ТЕКУЩИЙ ПРОВАЙДЕР" section header** | Frames the sub card as "your provider"; branding | Yes | **S** | Add a section `TextView` above the meta bar in `GroupServerFragment`/`activity_main`. Pure layout. |

### P2 — the subscription card + server list
| # | Feature | What / why | New? | Effort | Maps to |
|---|---|---|---|---|---|
| 11 | **Collapse chevron** on the sub card | Hide a long list; owner has 15 servers | Yes | **M** | **`docs/server-flags-design.md` §4 (feature B).** Reference only; place chevron in the meta-bar Row 1. |
| 12 | **Auto-update interval "1ч" + refresh + ping + "…" overflow** | Freshness cue + action home; `profile-update-interval` in **hours** | partial | **S** | `btn_ping:48`/`btn_refresh:60` exist. Add hours label (`updateInterval/60`, honor `profile-update-interval`) + **`iv_more`** overflow (Refresh·Ping·Edit·Pin·Delete). **See `happ-parity-details.md` #5/#7.** |
| 13 | **Traffic bar `∞ · [====] 1,72 TB / ∞`** | Unlimited handled gracefully | done | — | Implemented: `progress_traffic:109`, `tv_traffic:89`, `isUnlimited`/`trafficFraction` in `SubscriptionItem.kt:27,33`. Note: Incy `sort-order` header could reorder rows (new, S). |
| 14 | **`09.07.26, 07:17 · 15` (last-updated · server count)** | Freshness + "how many nodes" confidence | partial | **S** | `SubscriptionItem.lastUpdated` exists; add caption via `DateUtils.getRelativeTimeSpanString` + group size. **`happ-parity-details.md` #7.** |
| 15 | **Server-row protocol chips** — `Auto`/`VLESS`(blue) · `JSON`(gold) · `TCP`·`REALITY`(muted) | Communicates node type at a glance; gold **JSON** = full-Xray-config node | partial | **M** | **A config-type chip already exists**: `item_recycler_main.xml:86` `bg_type_chip`, `:95` `tv…"VLESS"`, color `colorConfigType`. Extend to multi-chip (network + security), gold when `configType==CUSTOM` (JSON). **`hidden-templates-design.md`** = the JSON-config side. |
| 16 | **Ping dot color + `454ms` / red `n/a`** | Instant quality read; red = dead | partial | **S** | Thresholds/colors: **`docs/ping-methods-design.md`.** Reference. |
| 17 | **Country-flag tile** per row (EU/DE/DK/SE/FI/LV/RU) | Fast geographic scan | Yes | **M** | **`docs/server-flags-design.md` §A.** Reference. |
| 18 | **Selected-row blue outline** | Clear "this is active" | partial | **S** | Selection state exists in `MainRecyclerAdapter`; ensure a blue stroke on the selected `bg_server_card`. |

### P3 — top bar / nav / future
| # | Feature | What / why | New? | Effort | Maps to |
|---|---|---|---|---|---|
| 19 | **Language globe** in top bar | Owner ships RU+EN; quick locale swap | Yes | **M** | App already localized; add a locale-picker action on `binding.toolbar` (`MainActivity.kt:94`) writing a per-app locale (`AppCompatDelegate.setApplicationLocales`). |
| 20 | **Bottom nav** Главная / Сервера / Настройки | Familiar 3-tab shell | Yes | **L** | Structural (fragmentize main/servers/settings). Largest UI change; note as its own track. |
| 21 | **`announce-url` / `sort-order` / `profile-description`** | Incy header extras (§1.1) | Yes | **S** | Fields + render; ride along with #4. |
| 22 | **Provider push (FCM/APNs)** | Incy's richer out-of-band notices (§1.2) | Yes | **L** | Firebase + backend; future. Do **not** conflate with the banner. |
| 23 | **`incy://crypt1/` deep-link import** | Incy's obfuscated import link | Yes | **M** | AES-256-GCM, shared key.[^encoder] Only if we want Incy-link compatibility; our own scheme is simpler. |

---

## 3. Incy implementation details worth copying (from `incy-docs`, cited)

Since the app is closed, these are **protocol/behavior** details (not source lines), each verified in INCY-DEV docs:

1. **Header-over-body priority** for every directive; `#body` is fallback only.[^appmgmt] — adopt in the new directive parser.
2. **`announce` up to 5 lines, ≤200 chars, plaintext or `base64:` (std *or* URL-safe alphabet), `0` clears.**[^appmgmt][^subfmt] — our banner should render newlines and cap at 5 lines.
3. **`support-url` t.me auto-glyph; `profile-web-page-url` gray-when-unset with `homepage` fallback header.**[^appmgmt] — exact button behavior.
4. **`subscription-userinfo` `expire` ms→s auto-convert when `>32e9`.**[^appmgmt] — add the guard to `SubscriptionUserInfo.parse`.
5. **`announce-url` (link-only notice) and `profile-description` (subtitle)** — Incy extras beyond Happ.[^appmgmt]
6. **`sort-order: ping|name|none`** server ordering hint.[^subfmt]
7. **Full-Xray-JSON subscriptions are first-class** (`inbounds/outbounds/routing/dns`) → the **gold "JSON" chip** = `configType==CUSTOM`; ties into `hidden-templates-design.md`.[^subfmt]
8. **Deep-link import** `incy://crypt1/<AES-256-GCM payload>` wraps `{url, name}`; **explicitly not secrecy** (shared/hardcoded key) — obfuscation vs scanners only.[^encoder]

**No clever *engine* trick to copy** — Incy is a conventional Xray client; its edge is the **provider-integration protocol** (headers + push + deep-link), which is precisely what §1–§2 port.

---

## 4. Top 8 Incy touches to implement first

1. **Directive-capture seam** — widen `HttpUtil.UrlContentResult` to a header map + add `#body` scan + base64/URL-safe decode + header-priority + `expire` ms→s guard. *(P0, M — unlocks 2–5, 21.)* [`HttpUtil.kt:201/247`, `AngConfigManager.kt:591`]
2. **Announce banner** (`tv_announce`, ≤5 lines, `0`-clears, dismissible) under the traffic bar. *(P0, S.)* [`layout_subscription_meta_bar.xml`, `GroupServerFragment`]
3. **Поддержка / support button** (`support-url`, t.me glyph) + **website button** (`profile-web-page-url`). *(P0, S.)*
4. **Uptime timer** in the stats row (start on `applyRunningState` connected). *(P1, S.)* [`MainActivity.kt:174,307`]
5. **Ringed shield connect button** colored by state. *(P1, M — coordinate `design-system-2026.md`, `happ-parity-details.md` #11.)*
6. **"Проверить" Check button** reusing the connection-test path. *(P1, S — `ping-methods-design.md`.)*
7. **"…" overflow + hours interval + last-updated·count** on the sub card. *(P2, S — `happ-parity-details.md` #5/#7.)*
8. **Multi-chip protocol tags + gold JSON chip** on server rows. *(P2, M — extends `item_recycler_main.xml:86`; `hidden-templates-design.md`.)*

Reference-only (already fully designed elsewhere, high owner value, sequence alongside): **memory card** (`memory-panel-design.md`), **flags + collapse** (`server-flags-design.md`), **ping dot thresholds** (`ping-methods-design.md`).

---

## 10-line summary

1. **Incy's Android app is CLOSED source** (`llc.itdev.incy`, Google Play) — there is no app repo to read.
2. **Found the useful half:** the **INCY-DEV GitHub org — https://github.com/INCY-DEV** — publishes the **provider-integration docs** (`incy-docs`) + a deep-link encoder, which specify the exact subscription/announce protocol.
3. Incy is **Xray-based** (VLESS/VMess/Trojan/SS/Hy2/WG/AmneziaWG; full-Xray-JSON configs) — same core as our fork, so parity is **UI + header parsing, not an engine swap**.
4. **Announce banner** = subscription HTTP `announce` header (or `#announce:` body), **plaintext or `base64:`, ≤200 chars, up to 5 lines, `0` clears, headers win over body** — matches the screenshot's `@departamentvpn` message.
5. **"Поддержка"** = `support-url` (t.me → Telegram glyph); a second **website** button = `profile-web-page-url`.
6. Incy **extras** beyond Happ: `announce-url`, `profile-description`, `sort-order`, and `expire` **ms→s** auto-convert.
7. A **separate** FCM/APNs **push** system exists (`provider-notifications`) — larger, P3 future; **not** the banner.
8. **Storage/render seam is small**: 5 new `SubscriptionItem` fields, widen `HttpUtil.UrlContentResult` (`:201/247`), persist at `AngConfigManager.kt:591`, add 3 views to `layout_subscription_meta_bar.xml`.
9. **`announce`/`support-url`/`profile-web-page-url` were already designed** in `docs/happ-parity-details.md` (#2/#3/#4/#8) — this doc adds the **Incy-specific exactness** and does not duplicate the memory-card/flags/collapse/ping docs, which it references.
10. **Top 8** front-loads the directive seam → banner → support/web buttons → uptime timer → ringed connect → Check → overflow/interval → protocol chips.

---

[^play]: Google Play `llc.itdev.incy`; APKPure/AppBrain/Filehippo mirrors; official site https://incy.cc/ ; 4PDA thread https://4pda.to/forum/index.php?showtopic=1120957 . Version 3.3.0 (updated 2026-07-02/03).
[^org]: INCY-DEV org listing (GitHub API + https://github.com/INCY-DEV): incy-platforms, incy-icons, incy-docs, incy-link-encoder, .github. No Android-app source repo present.
[^encoder]: https://github.com/INCY-DEV/incy-link-encoder — `incy://crypt1/<payload>`, AES-256-GCM, key derived from package constants and hardcoded into all clients; README states it is obfuscation, not secrecy.
[^appmgmt]: INCY-DEV/incy-docs — `ru/dev-docs/app-management.md` (raw). Directives: header priority over `#body`; `profile-title` ≤25 (base64), `profile-description` (subtitle, header-only), `profile-update-interval` (whole hours), `subscription-userinfo` (`upload/download/total/expire`, expire ms→s when >32e9), `support-url` (t.me glyph), `profile-web-page-url` (gray-if-unset, `homepage` fallback), `announce` (≤200, plaintext/`base64:`, `#announce:` body, dismissible alert), `announce-url` (link notice). Headers case-insensitive.
[^subfmt]: INCY-DEV/incy-docs — `ru/dev-docs/subscription-format.md` (raw). Protocols VLESS/VMess/Trojan/SS/Hysteria2/SOCKS5/WireGuard/AmneziaWG; ssr/tuic/hysteria1 recognized-not-parsed; body formats base64 (std + URL-safe), plaintext, JSON full-xray configs, mixed, WG `.conf`; headers include `announce` ("up to 5 lines"), `sort-order` (`ping|name|none`), `routing`.
[^notif]: INCY-DEV/incy-docs — `ru/dev-docs/provider-notifications.md` (raw). FCM (Android)/APNs (iOS) push; desktop polls on next sync; title ≤100, body ≤500, optional image/action URL/button/enterprise modal timer 1–10s; `deliveredCount`/`failedCount` telemetry. **Separate from the subscription `announce`.**
[^happ]: Happ dev-docs — App management: https://www.happ.su/main/dev-docs/app-management (`announce` base64/plain ≤200 `0`=clear; `support-url` t.me detection; `profile-web-page-url` gray-if-unset; `sub-info-text/-color/-button-*` superset). Cross-checked against `docs/happ-parity-details.md`.
[^subitem]: `V2rayNG/app/src/main/java/com/v2ray/ang/dto/entities/SubscriptionItem.kt:1–37` (fields end at `userInfoUpdated`, line 22; derived helpers `isUnlimited:27`, `trafficFraction:33`).
[^http]: `V2rayNG/app/src/main/java/com/v2ray/ang/util/HttpUtil.kt:201` `UrlContentResult(body, subscriptionUserInfo)`, `:207` `getUrlContentWithUserAgentEx`, `:247` reads only the `subscription-userinfo` header.
[^angcfg]: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt:591–602` — persist block writing `SubscriptionUserInfo` back onto `it.subscription`.
[^meta]: `V2rayNG/app/src/main/res/layout/layout_subscription_meta_bar.xml` — Row1: `tv_sub_title:27`, `progress_action:39`, `btn_ping:48`, `btn_refresh:60`; Row2: `tv_traffic:89`, `tv_expiry:99`, `progress_traffic:109`. No announce/support/web/description/collapse/more views today. Connect button + states: `MainActivity.kt:122,231,303,307,344`; speeds `:174–175`. Config-type chip: `item_recycler_main.xml:86,95`. `activity_main.xml` has no memory card / Check button.
