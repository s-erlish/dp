# departament VPN — Additional Module Proposals (2026)

**Status:** Research + product design only. No app code is changed by this document.
**App:** v2rayNG / Xray-core fork, package `com.v2ray.ang`, Kotlin + XML views, bottom-nav
Home / Servers / More, audience incl. Russia / Iran censorship users.

## Read-first / non-duplication

These proposals are deliberately **additional** to — and do not restate — the existing docs:

- `design-system-2026.md` — visual system, bottom-nav, glass, connect ring, tablet responsiveness.
- `telegram-auth-design.md` — Telegram sign-in + subscription delivery + payments scaffold.
- `hidden-templates-design.md` — locked operator JSON templates, header parsing, share-gating.
- `ping-methods-design.md` — selectable TCP / HTTP-204 / ICMP / proxied real-delay probes.
- `subscription-meta-bar-design.md` — `subscription-userinfo` quota/expiry bar.
- `ux-recommendations.md` — onboarding, staged connect, error/recovery model, **kill-switch guidance,
  trusted-Wi-Fi auto-connect, quick tile + widget, split-tunnel picker, rich notification,
  subscription health chip, diagnostics/share-debug, on-demand/schedule, accessibility, i18n depth,
  empty states, stealth icon alias, backup/restore polish, haptics, Wear OS**.
- `strategy-russia-2026.md` — **auto protocol fallback, Russia mode (Reality/Vision/Fragment/uTLS),
  resilient subscription fetch, health-check auto-rotate, AmneziaWG, kill-switch/no-leak failover,
  RU-whitelist split-tunnel preset, always-on auto-reconnect, real-reachability test, SS-2022,
  multi-subscription-group failover, DNS strategy (DoH foreign resolvers, FakeDNS), self-update
  through tunnel**.

Anything in the two lists above is considered **covered**. Below are concrete modules those docs do
**not** plan. Where a base capability already exists in the tree it is called out so we extend rather
than reinvent.

**What already exists in the codebase** (verified, so not proposed as new): Quick-Settings tile
(`service/QSTileService.kt`), home-screen widget (`receiver/WidgetProvider.kt`), WebDAV backup/sync
(`handler/WebDavManager.kt`, `ui/BackupActivity.kt`), per-app split tunnel (`ui/PerAppProxyActivity.kt`),
routing rules + preset rulesets (`ui/RoutingSettingActivity.kt`, `handler/SettingsManager.kt`),
geoip/geosite asset management (`ui/UserAssetActivity.kt`), proxy chaining
(`ui/ServerProxyChainActivity.kt`), raw MTU/Mux/DNS preferences (`AppConfig.kt`), boot auto-start
(`receiver/BootReceiver.kt`), Tasker (`ui/TaskerActivity.kt`), QR/clipboard/deep-link import
(`ui/ScannerActivity.kt`, `ui/UrlSchemeActivity.kt`).

**Effort key:** S ≈ ≤2 days · M ≈ 3–8 days · L ≈ >8 days / cross-cutting.

---

## Priority summary

| P | Module | One line |
|---|---|---|
| **P0** | **M1 · Ad / tracker / malware blocking** | One-tap content filtering via routing + DNS blocklists; universal table-stakes. |
| **P0** | **M2 · App-lock + duress / panic wipe** | Biometric/PIN gate + emergency config wipe — a safety feature for the RU/Iran audience. |
| **P1** | **M3 · DNS control center** | Friendly DoH/DoT presets (Cloudflare/Quad9/NextDNS/ControlD/AdGuard) + per-profile DNS. |
| **P1** | **M4 · Real throughput speed test** | Actual ↓/↑ Mbps per node, not just latency; feeds "Fastest" and diagnostics. |
| **P1** | **M5 · Data-usage & per-app traffic dashboard** | Per-app + per-server usage, session/day/month totals, quota-aware. |
| **P1** | **M6 · Android TV / Leanback + landscape** | D-pad-navigable big-screen UI; a whole reachable device class today unsupported. |
| **P2** | **M7 · Connection profiles ("scenes")** | Save/switch named bundles of protocol + routing + DNS + split-tunnel in one tap. |
| **P2** | **M8 · Guided advanced tuning** | MTU auto-probe + Mux/TLS presets replacing raw number fields. |
| **P2** | **M9 · Routing preset library + geo-asset auto-update** | Curated one-tap routing packs; scheduled, mirrored geoip/geosite refresh. |
| **P2** | **M10 · Config interop importer** | Import Clash/Mihomo YAML + sing-box JSON so migrants from NekoBox/Karing/Clash land cleanly. |
| **P3** | **M11 · Leak protection + in-app leak test** | IPv6/DNS/WebRTC leak guards + a one-tap "check for leaks" tool. |
| **P3** | **M12 · Background-reliability guardian** | OEM battery-whitelist / autostart deep-links so auto-reconnect actually survives. |
| **P3** | **M13 · Multi-hop / proxy-chain UX polish** | Turn the existing chain engine into a legible 2-hop picker. |

---

## P0

### M1 · Ad / tracker / malware blocking

- **What:** A single "Block ads & trackers" toggle (with an optional "block malware" tier) that injects
  a routing/DNS rule set: route `geosite:category-ads-all` (and community tracker/malware lists) to a
  `blackhole`/reject outbound, and/or point the tunnel resolver at a filtering DoH endpoint. Ship a
  small on/off control on **More → Privacy**, plus a counter ("~1,240 requests blocked today").
- **Why valuable here:** This is now table-stakes across the market and one of the most-cited reasons
  users pick a VPN. For the RU/Iran audience it also **cuts tunnel payload** (fewer ad/tracker bytes
  through a scarce, throttled link — directly relevant to the 2026 RU 15 GB/mo mobile-billing pressure)
  and reduces third-party tracking on high-stakes connections. Reuses the routing engine already in the
  tree, so it is mostly UX + a curated ruleset.
- **Real apps:** Proton VPN **NetShield** (DNS filtering, now blocks subdomains), Windscribe
  **R.O.B.E.R.T.**, Surfshark **CleanWeb**, IVPN/Mullvad **AntiTracker / DNS content blockers**,
  NordVPN **Threat Protection**; sing-box/Clash do this natively via rule providers.
- **Effort:** **M** (S if DNS-only, M if routing-rule + counter + list updates).
- **Files/areas:** `handler/SettingsManager.kt` (already has preset rulesets — add ad/tracker packs),
  `dto/entities/RulesetItem.kt`, routing build path in `handler/*ConfigManager`, `AppConfig.kt`
  (new `PREF_BLOCK_ADS` flag), a toggle row in the settings UI, and `ui/UserAssetActivity.kt` /
  `UserAssetViewModel.kt` to keep the blocklist geo-asset fresh.
- **Risks:** False positives breaking sites (need a per-site allow exception); blocklist freshness
  (tie to M9 auto-update); DNS-based blocking is bypassed if a hidden operator template overrides DNS —
  respect `hidden-templates` precedence and document the interaction.

### M2 · App-lock (biometric / PIN) + duress / panic wipe

- **What:** An optional lock screen (`BiometricPrompt` + PIN fallback) shown on app foreground, with an
  auto-lock timeout. Layered on top: a **duress PIN** that opens a decoy/empty state, and a **panic
  action** (long-press or a specific PIN) that immediately wipes stored subscriptions/configs/token.
  Pairs naturally with the already-planned stealth icon alias (`ux-recommendations` P2-7).
- **Why valuable here:** For users in Iran/Russia, phone inspection at checkpoints or by authorities is
  a real threat; a VPN app that visibly lists servers is incriminating. App-lock + duress/panic is a
  genuine safety feature, not just polish — and it is the natural completion of the stealth-icon story.
  Nothing in the existing docs covers locking or emergency wipe.
- **Real apps:** Proton **App Lock** (PIN + biometric + auto-lock on Android), many banking/messaging
  apps; duress/panic patterns from security-focused tools (e.g. Briar/Amnezia-style self-host privacy
  posture). App-lock itself is a common VPN-client request.
- **Effort:** **M** (S for basic biometric gate; M with PIN + duress + wipe).
- **Files/areas:** New `ui/AppLockActivity.kt` + a lifecycle hook in `ui/BaseActivity.kt`
  (`onStart`/`onStop`) and `ui/MainActivity.kt`; secrets in MMKV via `handler/MmkvManager.kt`;
  androidx.biometric dependency; wipe path reuses `MmkvManager` clear + subscription store.
- **Risks:** Lock-out/recovery UX (must not permanently brick a paying user); panic wipe must be
  unambiguous and confirmable in normal mode; biometric availability varies on low-end target hardware
  (always keep PIN fallback). Must **not** weaken the locked-template threat model in `hidden-templates`.

---

## P1

### M3 · DNS control center

- **What:** Promote today's raw IP-string DNS prefs (`PREF_REMOTE_DNS`, `PREF_DOMESTIC_DNS`,
  `PREF_VPN_DNS`, `PREF_DNS_HOSTS`) into a friendly panel: a **provider picker** (Cloudflare, Quad9,
  Google, AdGuard-DNS, NextDNS, Control D — with DoH/DoT template + custom-ID field), separate remote
  vs. domestic resolver rows, a "test resolver" button, and **per-profile DNS override**. Includes the
  `strategy-russia §3.5` split-DNS defaults (foreign DoH for proxied, domestic for whitelist) as a
  one-tap preset.
- **Why valuable here:** The plumbing exists but is expert-only (paste an IP). A guided picker makes
  the censorship-resistant DNS posture accessible, enables **filtering DNS** (ties into M1), and lets
  advanced users bring NextDNS/Control D profiles. RU/Iran users specifically need "foreign DoH for
  proxied, local for banking" without hand-editing.
- **Real apps:** Clash/Mihomo & sing-box (rich DNS blocks, DoH/DoT), Karing, Hiddify; consumer VPNs
  expose "custom DNS / DNS providers".
- **Effort:** **M.**
- **Files/areas:** `handler/SettingsManager.kt` (`getRemoteDnsObject`/`getDomesticDnsObject` ~L360–392),
  `AppConfig.kt` DNS consts, `res/xml/pref_settings.xml` + new preference screen, `dto/entities/ProfileItem.kt`
  (optional per-profile DNS field), `util/Utils.isCoreDNSAddress`.
- **Risks:** DoH bootstrap chicken-and-egg (resolving the DoH host through a censored resolver) —
  IP-pin bootstrap; some DoH endpoints are themselves blocked in RU (validate/test); avoid clobbering
  operator hidden-template DNS.

### M4 · Real throughput speed test

- **What:** A per-node **download/upload throughput test** (Mbps) run through the core, distinct from
  the latency probes in `ping-methods`. Surfaced as a ⚡ action on a server row and in the connection
  insight sheet; results cached and feed the "Fastest/Auto" ranking and diagnostics.
- **Why valuable here:** `ping-methods` covers *latency*; it explicitly does not measure bandwidth. A
  node can ping fast yet throttle to a crawl (common in RU throttling and the 16 KB "TLS freeze"
  regime). A real throughput number is what users actually judge servers by, and it complements
  `strategy-russia §2 #11`'s past-freeze reachability test with an actual speed figure.
- **Real apps:** Hiddify's home-screen speed-test (⚡) that finds the fastest node; NekoBox/Karing
  URL-test + speed; most consumer VPNs ship an in-app speed test.
- **Effort:** **M.**
- **Files/areas:** `handler/SpeedtestManager.kt` (add a bounded download/upload probe alongside
  `tcping`/`socketConnectTime`), `service/CoreTestService.kt` / `RealPingWorkerService.kt`,
  `viewmodel/MainViewModel.kt`, server row adapter, and the insight sheet from `ux-recommendations` P2-1.
- **Risks:** Consumes real bandwidth/quota (cap payload, warn on metered, respect the RU mobile-billing
  concern — make it opt-in and small); concurrency/battery (reuse the ping-methods throttling model).

### M5 · Data-usage & per-app traffic dashboard

- **What:** A usage screen: total ↓/↑ per **session / today / this month**, a per-**server** breakdown
  (the core already exposes `queryAllOutboundTrafficStats`), and a per-**app** breakdown via
  `NetworkStatsManager`. Optional quota ring that reconciles with the subscription meta-bar quota.
- **Why valuable here:** Managed users on capped subscriptions (and RU users facing >15 GB/mo
  international-traffic billing from 2026) need to see where bytes go. The base already collects
  per-outbound stats and shows totals in the notification — this turns raw counters into an actionable
  dashboard. Not covered by the meta-bar doc (that shows the *operator's* quota header, not local
  device/app accounting).
- **Real apps:** Windscribe/NetGuard-style per-app data views; most premium VPNs show data usage;
  Clash dashboards show per-connection traffic.
- **Effort:** **M.**
- **Files/areas:** `dto/OutboundTrafficStat.kt`, `core/CoreServiceManager.kt`
  (`queryAllOutboundTrafficStats` ~L318), `handler/NotificationManager.kt` (already iterates stats),
  new `ui/DataUsageActivity.kt` + viewmodel, `NetworkStatsManager` (needs `PACKAGE_USAGE_STATS`
  permission with rationale), MMKV persistence for historical rollups.
- **Risks:** `PACKAGE_USAGE_STATS` is a sensitive special-access permission (clear rationale, off by
  default); bounded storage for history (ring buffers per `ux-recommendations §6`); month reset logic
  vs. subscription cycle mismatch.

### M6 · Android TV / Leanback + landscape / large-screen

- **What:** A TV entry point (`LEANBACK_LAUNCHER` category + banner) and a D-pad-navigable layout:
  focusable connect ring, server list, and settings usable with a remote; plus proper landscape /
  free-form window support for tablets and foldables. The manifest already declares
  `uses-feature android.software.leanback` (non-required) but there is no TV launcher or focus model.
- **Why valuable here:** Smart TVs and TV-boxes are a huge unblocking use case (streaming, YouTube) and
  a common device in the target regions; a dedicated fork **`Android-TV-v2rayNG`** exists purely
  because mainline TV support is weak — clear demonstrated demand. `design-system-2026` covers phone/
  tablet responsiveness but explicitly defers TV/D-pad. Reaches a device class currently unserved.
- **Real apps:** The community `Android-TV-v2rayNG` fork; consumer VPNs (Proton, Windscribe, Mullvad)
  ship Android-TV apps.
- **Effort:** **L** (cross-cutting focus/navigation + layouts).
- **Files/areas:** `AndroidManifest.xml` (TV launcher activity + banner, ~L20 leanback feature),
  `ui/MainActivity.kt` + `GroupServerFragment.kt` focus handling, landscape resources
  (`res/layout-land`, `res/layout-television`), remove any portrait-lock, TV banner drawable.
- **Risks:** VPN consent + always-on flows differ on TV; input focus is a broad refactor; keep it a
  variant of existing screens (don't fork the app). Store/distribution for TV may differ (ties to
  `strategy-russia §4.2`).

---

## P2

### M7 · Connection profiles ("scenes")

- **What:** Named, one-tap presets that bundle a **protocol/fallback preference + routing rule set +
  DNS choice + split-tunnel app set + Fragment/Mux state** — e.g. "Everyday", "Streaming (some apps
  direct)", "Max stealth (RU)", "Work". Switching a scene reconfigures everything at once. Generalizes
  the single "Russia mode" toggle from `strategy-russia` into user-savable, shareable bundles.
- **Why valuable here:** Power users juggle routing/DNS/split settings for different contexts; today
  that means digging through several screens. Scenes make context-switching a single tap and pair well
  with trusted-Wi-Fi automation (`ux-recommendations` P1-3 could *select a scene* per network). "Russia
  mode" becomes one built-in scene among several.
- **Real apps:** Clash/Mihomo "profiles" + policy groups, Karing profile switching, Tasker-style
  automations; consumer VPNs' "modes" (streaming/gaming/privacy).
- **Effort:** **M.**
- **Files/areas:** New scene model persisted in `handler/MmkvManager.kt`; apply/save in
  `handler/SettingsManager.kt`; a picker on Home/More; interplays with `AppConfig` prefs it snapshots;
  optional Tasker action (`ui/TaskerActivity.kt`) to switch scene.
- **Risks:** Interaction with **locked hidden templates** (a scene must not override operator-forced
  routing/DNS — gate accordingly); combinatorial testing; keep the default path dead-simple so casual
  users never see scenes unless they opt in.

### M8 · Guided advanced tuning (MTU auto-probe + Mux/TLS presets)

- **What:** Replace the raw `PREF_VPN_MTU` / `PREF_MUX_*` number fields with a guided "Advanced tuning"
  panel: an **MTU auto-probe** (binary-search the largest non-fragmenting MTU), **Mux presets**
  (Off / Balanced / Aggressive with an explainer of when Mux *hurts* — e.g. Reality+Vision per
  `strategy-russia §3.2`), and a plain-language note per option.
- **Why valuable here:** These knobs already exist but are expert-hostile and easy to misconfigure in
  ways that *reduce* survivability (e.g. Mux on with Vision). Guardrails + auto-detect turn a footgun
  into a safe optimization, and MTU auto-probe measurably helps on the mid/low-end mobile links common
  in-region.
- **Real apps:** WireGuard clients' MTU tuning, Amnezia's per-install obfuscation params, sing-box/Clash
  mux presets.
- **Effort:** **M.**
- **Files/areas:** `AppConfig.kt` (`PREF_VPN_MTU` L35, `PREF_MUX_*` L38–41), `res/xml/pref_settings.xml`,
  the settings UI, `service/CoreVpnService.kt` (MTU on the `VpnService.Builder`), an MTU-probe helper
  (reuse `handler/SpeedtestManager.kt` socket paths).
- **Risks:** MTU probe needs care over cellular; wrong Mux advice could hurt — anchor recommendations to
  `strategy-russia §3.2`; keep an "expert: raw values" escape hatch.

### M9 · Routing preset library + geo-asset auto-update

- **What:** Two linked pieces: (a) a **curated routing preset library** beyond the current built-in
  rulesets — one-tap packs like "RU banking & gov direct" (ties to `strategy-russia §2 #9`), "China-list",
  "streaming unblock", "ads/trackers reject" (shares M1's list), each previewable before apply; and
  (b) **scheduled, mirrored geoip/geosite auto-update** so `geo*.dat` and blocklists refresh in the
  background from fallback mirrors (fetch-through-tunnel when connected).
- **Why valuable here:** The routing engine and preset rulesets exist but the library is thin and geo
  assets are manually managed; stale geosite data silently breaks routing/adblock. Auto-refresh with
  mirrors matches the `strategy-russia §3.4` delivery-resilience posture applied to routing data.
- **Real apps:** Clash/Mihomo rule-providers with auto-update, sing-box rule-set updates, Happ routing
  profiles.
- **Effort:** **M.**
- **Files/areas:** `handler/SettingsManager.kt` (preset rulesets, ~L64–80), `ui/RoutingSettingActivity.kt`
  + `RoutingEditActivity.kt`, `ui/UserAssetActivity.kt` / `UserAssetViewModel.kt` + `dto/entities/AssetUrlItem.kt`
  (add schedule + mirror list), a WorkManager job, `util/HttpUtil.kt`.
- **Risks:** Asset size/download cost on metered links (respect metered + do it inside tunnel); mirror
  trust (verify checksums); preset conflicts with locked templates.

### M10 · Config interop importer (Clash / Mihomo / sing-box)

- **What:** Extend import to parse **Clash/Mihomo YAML** and **sing-box JSON** subscriptions/configs,
  mapping their proxies to the fork's `ProfileItem`s (best-effort, with a preview + "unsupported field"
  report). Complements the existing per-URL (`vless://` etc.) and base64 import.
- **Why valuable here:** Users migrating from **NekoBox, Karing, FlClash, Clash Meta, Stash** hold their
  configs in Clash/sing-box format; today they can't bring them in. Frictionless migration lowers the
  switch cost to departament VPN and is a concrete competitive wedge. `fmt/` currently has only the
  native per-protocol formatters (no Clash/sing-box parser), so this is genuinely new.
- **Real apps:** Karing (full Clash + partial sing-box), Hiddify (auto-converts subscription formats),
  NekoBox (sing-box native) — interop is a headline selling point for all three.
- **Effort:** **L** (format mapping is broad; can ship incrementally — Clash first).
- **Files/areas:** New `fmt/ClashFmt.kt` / `fmt/SingBoxFmt.kt` alongside existing `fmt/*Fmt.kt`,
  wired into `handler/AngConfigManager.kt` import/parse path, `ui/ScannerActivity.kt` /
  `UrlSchemeActivity.kt`, a YAML dependency (SnakeYAML) — weigh APK-size cost.
- **Risks:** Format sprawl and partial-mapping surprises (be explicit about what didn't import); YAML
  parser adds size; keep it a power-user path, not core.

---

## P3

### M11 · Leak protection + in-app leak test

- **What:** Guardrails against clear-text leaks around the tunnel: explicit **IPv6 handling**
  (route or block IPv6 to prevent v6 leaks), DNS-leak prevention verification, and a one-tap **"Check
  for leaks"** tool that reports the visible exit IP/DNS and flags mismatches. Complements the planned
  kill-switch (`ux-recommendations` P0-5, `strategy-russia §2 #8`) with *verification* and IPv6, which
  those items don't spell out.
- **Why valuable here:** RU providers now actively correlate the user's *real* IP leaking during drops
  (2026-04-15 mandate, `strategy-russia §1.5`), so leak prevention + a way to *prove* no leak is a
  safety feature. A visible "you're not leaking" check is reassuring for the audience.
- **Real apps:** Mullvad/IVPN leak protection + IPv6 controls; ipleak-style web tools built in.
- **Effort:** **M.**
- **Files/areas:** `service/CoreVpnService.kt` (`VpnService.Builder` routes/`allowFamily`/IPv6),
  `AppConfig.kt` (new IPv6 pref), a check tool reusing `getRemoteIPInfo()` in
  `handler/SpeedtestManager.kt` + `dto/IPAPIInfo.kt`, diagnostics screen (`ui/LogcatActivity.kt` area).
- **Risks:** IPv6 behavior varies by device/carrier; must not break dual-stack where it works; keep the
  leak-test redacted per `hidden-templates §3`.

### M12 · Background-reliability guardian

- **What:** A small reliability layer for auto-reconnect/always-on: detect aggressive OEM battery
  killers, offer **deep-links to battery-optimization exemption and autostart** settings (Xiaomi/MIUI,
  Huawei, Oppo/ColorOS, Samsung), and show a "connection may drop in background" warning with a fix CTA.
- **Why valuable here:** The `strategy-russia` always-on / mobile-shutdown auto-reconnect (§2 #10) is
  only as good as the OS letting the service live. On the Xiaomi/Realme/Oppo/Huawei hardware common
  in-region, background VPN services are silently killed — the single biggest cause of "it disconnected
  by itself". `ux-recommendations §6` discusses battery *budget* but not OEM autostart/whitelist
  guidance. Cheap, high-impact reliability.
- **Real apps:** Widely done by reliability-sensitive apps (messengers, health, dontkillmyapp.com
  patterns); several VPNs prompt for battery-optimization exemption.
- **Effort:** **S–M.**
- **Files/areas:** `service/CoreVpnService.kt`, `receiver/BootReceiver.kt`, new guidance card in
  settings, intents to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + per-OEM autostart intents,
  `util/Utils.kt` for OEM detection.
- **Risks:** Per-OEM intents are undocumented/fragile (wrap in try/catch, fall back to generic battery
  settings); don't nag — show once with dismiss.

### M13 · Multi-hop / proxy-chain UX polish

- **What:** The engine exists (`ui/ServerProxyChainActivity.kt`, `ServerProxyChainMemberAdapter.kt`) but
  is buried and expert-only. Wrap it in a legible **"Double-hop (entry → exit)"** picker on the server
  screen: choose an entry and an exit node, show the path visually, one-tap enable.
- **Why valuable here:** Multi-hop is a premium trust signal (obscures the exit from the entry operator)
  and is already technically supported — this is pure UX leverage on existing capability. Lower priority
  because it serves a narrower power-user slice.
- **Real apps:** Mullvad/Proton/Windscribe "double VPN / multi-hop"; NekoBox/sing-box chains.
- **Effort:** **S–M** (UX over existing engine).
- **Files/areas:** `ui/ServerProxyChainActivity.kt`, server list entry point, `viewmodel/MainViewModel.kt`.
- **Risks:** Doubles latency/halves throughput (label clearly); interaction with fallback/failover chain
  (`strategy-russia §3.1`) needs defined semantics.

---

## Cross-cutting notes

- **Audience-first ordering.** M1 (adblock) and M2 (app-lock/panic) lead because they combine broad
  market table-stakes with specific value for the censored, at-risk RU/Iran user (payload reduction,
  device-inspection safety). M6 (TV) unlocks an entire device class with proven demand.
- **Reuse over rebuild.** Most proposals extend engines already in the tree (routing, DNS prefs, traffic
  stats, proxy chain, geo assets) — the work is UX + guardrails, not new cores.
- **Respect the locked-template threat model.** M1, M3, M7, M9 all touch routing/DNS; each must defer to
  operator hidden templates (`hidden-templates-design.md §3–4`) and never expose or override locked
  configs.
- **Keep the connect path simple.** Everything advanced (M3, M7, M8, M10, M13) lives behind More /
  opt-in, so a casual managed user still sees the one-tap sign-in-and-connect flow from
  `ux-recommendations`.

## Sources

- Ad/tracker blocking landscape — Proton NetShield: https://protonvpn.com/support/netshield · IVPN
  AntiTracker: https://www.ivpn.net/en/antitracker/ · Best-VPN-ad-blocker roundups (Windscribe R.O.B.E.R.T.,
  Surfshark CleanWeb, NordVPN Threat Protection): https://restoreprivacy.com/vpn/ad-blocking-adblock/ ·
  https://www.cloudwards.net/best-vpn-with-ad-blocker/
- App-lock / biometric / PIN — Proton App Lock on Android: https://proton.me/support/pin-lock-and-auto-lock-on-android ·
  Proton biometric setup: https://proton.me/support/biometric-setup-mobile
- Speed test in clients — Hiddify server speed test: https://hiddify.com/manager/basic-concepts-and-troubleshooting/How-to-do-speed-test-on-server/ ·
  Hiddify vs NekoBox throughput: https://vpn07.com/en/blog/2026-hiddify-vs-nekobox-cross-platform-vpn-clients-comparison.html
- Clash / Karing DNS + rule providers + interop — Clash Meta for Android: https://github.com/MetaCubeX/ClashMetaForAndroid ·
  Karing: https://karing.app/en/ · Karing Clash compatibility: https://karing.app/en/clash
- Android TV demand — Android-TV-v2rayNG fork: https://github.com/savyjs/Android-TV-v2rayNG ·
  NekoBox: https://github.com/MatsuriDayo/NekoBoxForAndroid
- RU 2026 context (mobile billing, real-IP-leak detection, throttling) — see `strategy-russia-2026.md`
  sources (zona.media, OSW, net4people #490).

*Advisory document. Sources cited inline. No application code modified.*
