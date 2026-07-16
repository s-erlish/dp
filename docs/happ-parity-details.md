# Happ Parity — the "small but important" details, catalogued & prioritized

**Status:** Design / analysis only. **No app code is changed by this document.**
**App:** v2rayNG / Xray-core fork, package `com.v2ray.ang`, Kotlin, XML Material3 views, app label
**departament** (a Remnawave-backed "departament" Telegram bot delivers the subscription).
**Goal:** make the app *feel* like [Happ](https://www.happ.su/main) for a product owner who uses Happ
daily. This doc **catalogues** the many easy-to-miss Happ UX details, maps each to a **real file** in
this tree, sizes the effort **S / M / L**, and **prioritizes P0–P3** so engineering does the
high-value items first.

**Read-first / coordinate (do NOT duplicate these):**
- `docs/server-flags-design.md` — country flags on server rows **and** the collapse/"Hide all" toggle
  (feature B). This doc references it for detail #10 and does not re-plan it.
- `docs/circumvention-settings-design.md` — the Bypass/anti-censorship settings screen + presets. The
  grouped-settings restructure (detail #12) coordinates with it and does not re-plan it.
- `docs/subscription-meta-bar-design.md` — the meta bar + `subscription-userinfo` capture (already
  **implemented**: `layout_subscription_meta_bar.xml`, `SubscriptionItem` traffic fields,
  `HttpUtil.getUrlContentWithUserAgentEx`, `AngConfigManager` persist block).
- `docs/ping-methods-design.md`, `docs/review-04-auto-fallback.md`, `docs/telegram-auth-design.md`,
  `docs/hidden-templates-design.md` — ping, auto-fallback, delivery, templates live there.

---

## 0. Ground truth — Happ's subscription protocol (why these details exist)

Happ drives almost the entire subscription UX from **HTTP response headers on the subscription URL**
*or* the same directives placed **in the subscription body prefixed with `#`** (verified against Happ
dev-docs).[^happ-appmgmt] The full directive set the product owner's screenshots exercise:

| Directive (header / `#body`) | Format | Drives in Happ UI | This fork today |
|---|---|---|---|
| `profile-title` | plain or `base64:` UTF-8, ≤25 chars | Subscription name in the meta bar | Uses `remarks` from URL fragment; header **not** read |
| `subscription-userinfo` | `upload=..; download=..; total=..; expire=..` | Traffic bar `(i) [1.7TB/∞]`, expiry | **Done** (meta-bar design) |
| `announce` | plain or `base64:`, ≤200 chars; `0` = clear | The multi-line **announce banner** | **Missing** |
| `support-url` | URL; `t.me/*` → Telegram icon, else link icon | The **Telegram / support button** | **Missing** |
| `profile-web-page-url` | URL | Left-side **website button** (gray if unset) | **Missing** |
| `profile-update-interval` | integer hours | "Auto-update Nh" line + schedule | Have `updateInterval` (min) + `autoUpdate`; header **not** read |
| `routing` | `routing.happ.su` link / deeplink | Imports a routing profile | Out of scope (see routing doc) |
| `sub-info-text` / `-color` / `-button-text` / `-button-link` | plain/≤200/≤25; color `red\|blue\|green`; `0` = hide | Rich **info block** with colored button | **Missing** (superset of `announce`) |
| `sub-expire` / `sub-expire-button-link` | `true\|1`; URL | "Renew" prompt when ≤3 days left | **Missing** |

Key consequence: **this fork parses the URL `#fragment` for names but never reads Happ's response
headers except `subscription-userinfo`.** Capturing the header block is a one-place change
(`HttpUtil.getUrlContentWithUserAgentEx` already reads one header at
`util/HttpUtil.kt:247`; `AngConfigManager` already persists it at
`handler/AngConfigManager.kt:592`). Every "banner/button/interval" detail below is unlocked by
extending those two existing seams — **not** by new network plumbing.

Body-directive support matters as much as headers: the departament Remnawave panel may emit `#announce:`
/ `#support-url:` lines at the top of the base64-decoded body, so the parser must scan leading `#`
lines too, not only OkHttp headers.[^happ-appmgmt]

---

## 1. PRIORITIZED CATALOG (P0 → P3)

Effort key: **S** ≤ ~½ day · **M** ~1–2 days · **L** ~3 days+ / cross-cutting.
"New?" = net-new vs. what's already in the tree.

### P0 — highest value, do first

| # | Detail | What it is / why it matters | New? | Effort | Where it maps in THIS codebase |
|---|---|---|---|---|---|
| **1** | **Subscription PIN / UNPIN** | Pin a sub so it sorts to the top of the tab strip **and becomes the default open tab**. The single most-used Happ multi-sub affordance; the owner has several subs and wants the "departament" one always first. Menu item **Закрепить/Открепить** + 📌 icon state. | **Yes** | **M** | Data: add `var pinned: Boolean = false` to `dto/entities/SubscriptionItem.kt`. Sort: `viewmodel/MainViewModel.kt:381 getSubscriptions()` — stable-sort `pinned` first before building `GroupMapItem`s. Default tab: `ui/MainActivity.kt:223` — pick first pinned instead of `groups.size-1`. Toggle+persist: `SubscriptionsViewModel.update()` + `MmkvManager.encodeSubscription`. Icon: new `iv_pin` in `layout_subscription_meta_bar.xml` + `GroupServerFragment.bindMetaBar()`. Menu: detail #6. See §2. |
| **2** | **Capture Happ header/body directive block** | One parse point that reads `announce`, `support-url`, `profile-web-page-url`, `profile-update-interval`, `profile-title` (+ the `sub-info-*` superset). Unlocks details 3–5, 8, 9 at once. | **Yes** | **M** | Extend `HttpUtil.UrlContentResult` (`util/HttpUtil.kt:201`) from one header to a `Map<String,String>` (read the fixed Happ key set via `response.headers`). Add a `#`-prefixed body-line scan in a new `util/HappDirectives.kt` (mirrors `util/SubscriptionUserInfo.kt`). Persist into `SubscriptionItem` in the existing block at `handler/AngConfigManager.kt:592`. |
| **3** | **Announce banner from the subscription** | Multi-line text shown above the server list; `announce` header/`#announce:` body, plain or `base64:`, ≤200 chars, `0` clears. The owner's screenshot shows a live announce. | **Yes** | **S** (given #2) | Store `var announce: String = ""` on `SubscriptionItem`; render a `TextView` banner in `layout_subscription_meta_bar.xml` (row above traffic), bound in `GroupServerFragment.bindMetaBar()`. Decode `base64:` prefix in `util/HappDirectives.kt`. |
| **4** | **Telegram / support button** | `support-url` → a button in the meta bar; **`t.me/*` shows the Telegram glyph**, any other URL shows a generic link glyph. Direct line to the departament bot/support. | **Yes** | **S** (given #2) | `var supportUrl: String = ""` on `SubscriptionItem`; `iv_support` `ImageView` in the meta bar row-1 action cluster (next to `btn_ping`/`btn_refresh`), icon chosen by `url.contains("t.me")`; `setOnClickListener { Utils.openUri(...) }`. |
| **5** | **"..." subscription overflow menu** | Popup with **Refresh subscription · Ping · Edit · PIN/UNPIN · Delete** — the home for actions that don't fit as icons, and the *only* place PIN lives on small screens. | **Yes** | **S** | Add `iv_more` to `layout_subscription_meta_bar.xml`; in `GroupServerFragment` build a `PopupMenu`/`AlertDialog` reusing existing `refreshSub()`, `pingSub()`, `SubEditActivity` launch, new `togglePin()`, and `SubscriptionsViewModel.remove()`. |

### P1 — strong parity wins

| # | Detail | What it is / why it matters | New? | Effort | Where it maps |
|---|---|---|---|---|---|
| **6** | **Meta-bar identity cluster: clover/emoji + name + 📌** | Happ row-1 = leading emoji (from `profile-title`, often a 🍀/flag) + name + pin badge, visually grouping the sub. Small but it's the first thing the eye lands on. | partial | **S** | `tv_sub_title` exists (`layout_subscription_meta_bar.xml:26`); keep the leading emoji from `remarks`/`profile-title` verbatim (don't strip), add the `iv_pin` badge inline (detail #1). Reuse `FlagUtil.extractFlagEmoji` from `server-flags-design.md` if a flag is wanted. |
| **7** | **"last-updated \| Auto-update Nh" line** | A sub-caption under the title: relative last-updated time + the auto-update cadence in **hours** (Happ speaks hours; we store minutes). Sets the user's expectation of freshness. | partial | **S** | Data present: `SubscriptionItem.lastUpdated`, `autoUpdate`, `updateInterval` (minutes). Add a `tv_sub_updated` line to the meta bar; format `updateInterval/60` as "Nh"; honor `profile-update-interval` (hours→minutes) captured in #2. Use `DateUtils.getRelativeTimeSpanString`. |
| **8** | **Website button (`profile-web-page-url`)** | Left-side button in Happ (gray when unset). Distinct from support; opens the panel's user page. | **Yes** (given #2) | **S** | `var webPageUrl: String = ""`; `iv_web` `ImageView` at the **start** of the meta bar row; tint gray/disabled when blank, colored + clickable when set. |
| **9** | **Server row protocol chips + "Auto-select" group** | Each server row shows country flag + name + protocol chips **`VLESS / TCP / REALITY \| JSON`** + chevron; a subscription can carry an **Auto-select (Hybrid)** group that balancer-picks a server. Communicates *what* a node is at a glance. | partial | **M** | Chips: derive from `ProfileItem` (`configType`, `network`, `security`/`REALITY`) in `MainRecyclerAdapter.onBindViewHolder` → new chip views in `item_recycler_main.xml`. Auto-select already exists as **`EConfigType.POLICYGROUP` (101)** with a real balancer (`core/CoreConfigManager.kt:151` `balancerStrategies`, `TAG_BALANCER`, `applyObservability`); surface it as a first "Hybrid (Auto-select)" row + label. See §3. |
| **10** | **Collapse / "Hide all" (Скрыть все)** | Toggle above the sub that hides/shows that tab's server list; persisted per-sub. | **Yes** | **M** | **Do NOT re-plan — see `docs/server-flags-design.md` §4 (feature B, Option 2):** chevron on the meta bar, `binding.recyclerView` visibility toggle in `GroupServerFragment`, persisted `PREF_GROUP_COLLAPSED+subId` via `MmkvManager`. This doc only notes it belongs in the meta bar next to the pin. |
| **11** | **Big central power button with colored ring** | The connect FAB is a large ringed power button whose ring color encodes state (idle / connecting / connected / error). The emotional centerpiece of the Happ main screen. | partial | **M** | This fork has a connect FAB + test state in `ui/MainActivity.kt` (`handleFabAction`, `applyRunningState`, `setTestState`). Upgrade to a ringed button: a `ProgressBar`/custom ring drawable behind the FAB, colored by `isRunning`/loading (`colorPing`/`colorPingRed`/`colorPrimary`). Coordinate visual tokens with `docs/design-system-2026.md`. |

### P2 — grouped settings & the separate screens

| # | Detail | What it is / why it matters | New? | Effort | Where it maps |
|---|---|---|---|---|---|
| **12** | **Grouped Settings with Happ's section headers** | Happ groups prefs under **Interface / Tunnel / Advanced / Other / Information**; a red **Reset**; separate screens for **Ping / Statistics / Logs**; **FAQ / URL-schemes / About**. Today `pref_settings.xml` uses fork-native categories (UI / VPN / Core / Mux / Fragment / Advanced). | reorg | **M** | Relabel/reorder `PreferenceCategory`s in `res/xml/pref_settings.xml` to Happ's five sections; move mux/fragment/uTLS into the **Bypass** screen from `docs/circumvention-settings-design.md` (don't duplicate). Add an **Information** category (FAQ / URL schemes / About) + a red-tinted **Reset** `Preference`. Ping/Stats/Logs already have or warrant standalone activities. |
| **13** | **The specific Happ toggles** | Fragmentation, **Mux**, **Xray TUN**, **kill switch ("block outside tunnel")**, **Allow LAN**, **App autostart**, **Preferred IP type**, **Inbounds**, **Per-app proxy**, **Routing** — the exact switch set Happ shows. | mixed | **M** | Present today: `pref_fragment_enabled`, `pref_mux_enabled`, `pref_use_hev_tunnel_v2` (≈Xray TUN), `pref_vpn_bypass_lan` (≈Allow LAN), `pref_is_booted` (≈autostart), `pref_prefer_ipv6`/`pref_ipv6_enabled` (≈preferred IP), per-app proxy (commented out at `pref_settings.xml:82`), routing via `RoutingSettingActivity`. **Gap:** a true **kill switch / "block outside tunnel"** pref. Re-label to Happ wording; wire per-app proxy back on; add the kill-switch toggle. |
| **14** | **Multi-subscription ordering & drag-reorder respects pin** | With pin (detail #1), manual drag order must keep pinned subs on top; the tab strip and the sub-manager list share one order. | **Yes** | **S** (with #1) | `SubscriptionsViewModel.swap()` already reorders via `SettingsManager.swapSubscriptions`; add a rule that swaps can't move a non-pinned above a pinned (or resort after). Single source of order = `MmkvManager.decodeSubsList()`. |

### P3 — nice-to-have polish

| # | Detail | What it is / why it matters | New? | Effort | Where it maps |
|---|---|---|---|---|---|
| **15** | **Rich info block (`sub-info-*`) + expiry "Renew"** | Superset of `announce`: colored (`red/blue/green`) block with a labeled button + a `≤3 days` **Renew** prompt (`sub-expire*`). Monetization / renewal nudge the owner will want. | **Yes** | **M** | Extend detail #3's banner into a colored card with an action button; fields on `SubscriptionItem`; drive color from `sub-info-color`; show the renew CTA when `expire - now ≤ 3d` (reuse `isExpired`/expiry code in `GroupServerFragment.bindMetaBar()`). |
| **16** | **Per-subscription User-Agent already honored** | Happ lets a provider dodge UA-based blocks; confirm ours is wired end-to-end. | done | — | `SubscriptionItem.userAgent` → used at `AngConfigManager.kt:553`. No work; note for parity completeness. |
| **17** | **`fallback-url` / `new-url` / `new-domain` resilience** | Happ swaps to a backup URL on 3xx–5xx/timeout and can rotate the sub's domain server-side — keeps subs alive when a domain is blocked (very relevant for RU). | **Yes** | **L** | New capture in #2's directive map; store alt URLs on `SubscriptionItem`; retry logic in `AngConfigManager.updateConfigViaSub` (already has a two-try proxy/direct fallback at `:557`/`:575` to extend). Defer unless the departament panel emits these. |
| **18** | **Auto-update honoring `profile-update-interval` (hours)** | Happ schedules by the provider-sent hour cadence and runs a missed update on next launch. | partial | **S** | `updateInterval` + `SubscriptionUpdater` exist; on capture (#2) convert hours→minutes and set `autoUpdate=true`; verify "run on next launch if missed" in `SubscriptionUpdater`. |
| **19** | **Ping/refresh spinner + result affordances in meta bar** | Happ shows an inline spinner and per-row ping badges; already largely built. | done | — | `progress_action` + `btn_ping`/`btn_refresh` exist and are wired (`GroupServerFragment.refreshSub/pingSub`). Keep; just move under the new "..." menu too (#5). |

---

## 2. Detail #1 in depth — Subscription PIN / UNPIN (the flagship)

### 2.1 Data model
`dto/entities/SubscriptionItem.kt` — append one defaulted field so existing persisted JSON
deserializes unchanged (`JsonUtil.fromJsonSafe` tolerates missing keys, same guarantee the
traffic fields already rely on):

```kotlin
data class SubscriptionItem(
    // ... existing fields ...
    var pinned: Boolean = false,   // Happ "Закрепить": sort to top + default tab
)
```

Optional refinement if the owner pins **several** subs and wants a defined order among them: add
`var pinnedAt: Long = 0` and sort pinned subs by `pinnedAt` ascending (first-pinned leftmost). Start
with just `pinned`.

### 2.2 Sorting — one choke point
Ordering flows through exactly one function used by the tab strip:
`viewmodel/MainViewModel.kt:381 getSubscriptions()`. Sort **stably** before mapping to `GroupMapItem`,
so manual drag order (detail #14) is preserved *within* the pinned and unpinned partitions:

```kotlin
val subscriptions = MmkvManager.decodeSubscriptions()
    .sortedByDescending { it.subscription.pinned }   // stable: keeps decodeSubsList() order otherwise
```

The "All servers" pseudo-tab (`id == ""`, gated by `PREF_GROUP_ALL_DISPLAY`) stays first/unaffected.
`decodeSubscriptions()` (`handler/MmkvManager.kt:370`) itself keeps insertion order — do **not** sort
there (other callers, e.g. `SubscriptionActivity`'s manager list, may want raw order; but for Happ
feel they should share the same pinned-first sort — apply the same comparator in
`SubscriptionsViewModel`).

### 2.3 Default open tab
`ui/MainActivity.kt:223` currently defaults to the last group when nothing matches:
```kotlin
val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
    .takeIf { it >= 0 } ?: (groups.size - 1)
```
Change the fallback to **first pinned**, else preserve today's behavior:
```kotlin
?: groups.indexOfFirst { MmkvManager.decodeSubscription(it.id)?.pinned == true }
    .takeIf { it >= 0 } ?: (groups.size - 1)
```
Because pinned subs are sorted to the front (§2.2), "first pinned" is simply the first non-"All" tab —
cheap to compute.

### 2.4 UI — icon state + menu
- **Meta bar**: add `iv_pin` (`ic_push_pin` filled / `ic_push_pin_outlined`) to
  `layout_subscription_meta_bar.xml` row-1, left of the title. In `GroupServerFragment.bindMetaBar()`
  set the drawable by `sub.pinned` and `setOnClickListener { togglePin() }`.
- **Menu** (detail #5): the label flips **Закрепить ↔ Открепить** by `sub.pinned`.
- **`togglePin()`**: flip the field, `SubscriptionsViewModel.update(subId, sub)` (persists via
  `encodeSubscription`), then `SettingsChangeManager.makeSetupGroupTab()` so the tab strip re-sorts and
  re-selects. The existing `makeSetupGroupTab` signal is already consumed to rebuild tabs
  (`MainActivity` observes it → `setupGroupTab()`), so pin changes animate into place with no new
  plumbing.

### 2.5 Effort & risk
**M.** One new field, one comparator, one default-index tweak, one icon + one menu entry, reusing the
existing `makeSetupGroupTab` rebuild. No migration, no core-config impact.

---

## 3. Detail #9 in depth — Auto-select group + protocol chips

**Auto-select already exists in the core.** `EConfigType.POLICYGROUP (101)` is a group profile that the
config builder turns into an Xray **balancer** (`core/CoreConfigManager.kt:151` builds
`balancerStrategies`, rewrites `TAG_PROXY → TAG_BALANCER` when the main profile is `POLICYGROUP`, and
calls `applyObservability` so the balancer health-checks members). So Happ's **"Hybrid (Auto-select)"**
maps to: a `POLICYGROUP` profile whose members are the subscription's servers, selected by
least-ping/observatory. Parity work is **presentation, not engine**:
1. Ensure a subscription can auto-create/point at a `POLICYGROUP` covering its servers (the owner's
   Remnawave sub can ship a balancer template; `ServerGroupActivity` already edits policy groups).
2. Render it as the **first row** labeled "Hybrid (Auto-select)" with a globe/flag, so tapping it =
   "let the app pick." Everything downstream (balancer, observatory) is already wired.

**Protocol chips** are pure derivation in `MainRecyclerAdapter.onBindViewHolder` from `ProfileItem`:
`configType.name` (VLESS) · `network` (TCP/ws/grpc) · `security` (REALITY/TLS) · `| JSON` when
`configType == CUSTOM`. Add small chip `TextView`s (or a single formatted line) to
`item_recycler_main.xml`. No new data. Effort **M** mostly for the layout.

---

## 4. Top 8 to do first

1. **#1 Subscription PIN/UNPIN** — field + sort + default-tab + icon + menu (P0, M). *Highest owner value.*
2. **#2 Capture Happ header/body directives** — extend `UrlContentResult` to a map + `#`-body scan (P0, M). *Unlocks 3,4,7,8,15,18.*
3. **#5 "..." overflow menu** — Refresh · Ping · Edit · PIN/UNPIN · Delete (P0, S). *Home for PIN.*
4. **#3 Announce banner** — render `announce`/`base64:` above the list (P0, S given #2).
5. **#4 Telegram/support button** — `support-url`, t.me→Telegram glyph (P0, S given #2).
6. **#7 "last-updated | Auto-update Nh" line** — relative time + hours cadence (P1, S).
7. **#10 Collapse "Hide all"** — implement per `server-flags-design.md` §4, placed by the pin (P1, M).
8. **#9 Protocol chips + Auto-select row** — chips from `ProfileItem`; surface existing `POLICYGROUP` balancer (P1, M).

Sequencing note: 1, 2, 5 are independent and can land in parallel; 3/4/7 fall out of 2 cheaply;
10 reuses the flag/collapse doc; the settings restructure (12/13) and power-ring (11) are separable
tracks that don't block the subscription work.

---

## 5-line summary
- Catalogued **19 Happ details** mapped to real files, prioritized P0–P3 with S/M/L effort.
- **P0:** Subscription **PIN/UNPIN** (`SubscriptionItem.pinned` + sort in `MainViewModel.getSubscriptions()` + default-tab in `MainActivity:223`), **capture Happ header/body directives** (extend `HttpUtil.UrlContentResult` + `#`-body scan, persist in `AngConfigManager:592`), the **"..." menu**, **announce banner**, and **Telegram/support button**.
- **P1:** identity cluster (emoji+name+📌), "last-updated | Auto-update Nh", website button, **protocol chips + existing `POLICYGROUP` Auto-select balancer**, collapse "Hide all" (defer to `server-flags-design.md`), ringed power button.
- **P2/P3:** regroup `pref_settings.xml` into Happ's Interface/Tunnel/Advanced/Other/Information + red Reset (coordinate with `circumvention-settings-design.md`), add the kill-switch toggle, and the `sub-info-*`/renew/`fallback-url` resilience directives.
- **Top-8** front-loads PIN + the one-place directive capture, which together light up most of the Happ meta-bar feel.
```

[^happ-appmgmt]: Happ — App management (subscription headers & `#`-body directives: `profile-title`, `announce` plain/`base64:` ≤200 chars with `0`=clear, `support-url` with t.me→Telegram icon, `profile-web-page-url`, `profile-update-interval` hours, `subscription-userinfo`, `sub-info-*`, `sub-expire*`, `fallback-url`/`new-url`/`new-domain`). https://www.happ.su/main/dev-docs/app-management ; Routing: https://www.happ.su/main/dev-docs/routing ; FAQ (adding subscription): https://www.happ.su/main/faq/adding-configuration-subscription
