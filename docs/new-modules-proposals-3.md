# departament VPN — Additional Module Proposals, Round 3 (2026)

**Status:** Research + product design only. **No app code is changed by this document.**
**App:** v2rayNG / Xray-core fork, package `com.v2ray.ang`, Kotlin + XML views, bottom-nav
Home / Servers / More. Audience: censorship-region users (Russia / Iran first).
**Source root (note):** Kotlin lives under `app/src/main/java/com/v2ray/ang/` (not `.../kotlin/...`).

## Purpose & guardrails

This round proposes **~11 more lightweight, genuinely useful modules**, chosen against three
priorities: **(1) settings that let users self-tune censorship bypass, (2) clarity / onboarding,
(3) reliability** — while **favoring low memory and avoiding bloat**. Because the project will later
target **Windows / Linux**, each module notes whether its logic is **platform-agnostic** (pure
Kotlin over `ProfileItem` / `V2rayConfig` / prefs, no Android UI or Android-only APIs), so the core
can be lifted into a shared module for the desktop port. Anything with bloat risk is **flagged
explicitly** so it can be trimmed or deferred.

### Non-duplication (read-first)

These are **additional** to, and deliberately do not restate, work already built or planned:

- **Built/planned features** (per project status): blue/mono theme, Happ-style home, bottom nav,
  subscription meta bar, 4 ping methods, auto-fallback, Telegram auth, hidden templates, glass
  settings, rich notification, server flags + collapsible list, Smart TV + QR transfer, RAM panel,
  circumvention settings.
- **Existing design docs:** `design-system-2026`, `strategy-russia-2026`, `ux-recommendations`,
  `telegram-auth-design`, `hidden-templates-design`, `ping-methods-design`,
  `subscription-meta-bar-design`, plus the round-1 module set `new-modules-proposals.md`
  (**M1–M13**: adblock, app-lock/panic, DNS control center, throughput speed test, data-usage
  dashboard, Android TV, connection scenes, guided tuning, routing preset library, config interop
  importer, leak test, background-reliability guardian, multi-hop UX).
- Docs the project says are in progress and are therefore treated as **covered**:
  `notification-design`, `server-flags-design`, `smart-tv-transfer-design`, `memory-panel-design`,
  `circumvention-settings-design`.

Where a new module sits **next to** an existing one, the adjacency and the boundary are called out
so we extend rather than collide. The most important boundary to respect throughout: **locked
hidden templates** (`hidden-templates-design §3–4`) — any module that reads or edits config must
**defer to operator-forced values and never override or reveal locked ones**.

**Effort key:** S ≈ ≤2 days · M ≈ 3–8 days · L ≈ >8 days / cross-cutting.
**Mem/perf key:** ▁ negligible · ▃ small bounded · ▅ moderate (watch).

---

## Priority summary (top ~11)

| P | Module | One line |
|---|---|---|
| **P0** | **N1 · Pre-connect "Bypass Readiness" linter** | Static check of a profile before connecting flags RU-risky settings (empty SNI, no uTLS fingerprint, Mux+Vision, DC IP, ECH) with one-tap fixes. |
| **P0** | **N2 · Device clock-skew guard** | Warns when the phone clock is off enough to break Reality/TLS handshakes — a silent, common "nothing connects" cause. |
| **P1** | **N3 · Post-connect reachability + captive-portal check** | After connect, confirm the internet actually works and name "connected but no internet" / captive portal vs a real block. |
| **P1** | **N4 · Inline settings glossary ("?" plain-language help)** | Strings-only help popovers on bypass knobs so users self-tune with understanding, not guesswork. |
| **P1** | **N5 · Smart clipboard / paste-anything import** | Auto-detect a `vless://`/sub/base64 link on foreground and offer one-tap import; add gallery-QR decode. |
| **P1** | **N6 · Connection event timeline (in-memory ring)** | Human-readable history of connect / stall / failover / network-change events — "why did it drop?" transparency. |
| **P2** | **N7 · Subscription update diff summary** | After a refresh show "+3 −2 ~1", review before/after, and never silently swap a working list for a bad push. |
| **P2** | **N8 · Lite / low-resource mode** | One toggle trims animations, lengthens health-check cadence, and caps log/history retention on low-RAM devices. |
| **P2** | **N9 · Manual "rotate / next server" + temporary avoid** | One-tap jump to the next-best node and "avoid this one for 30 min" — the manual sibling of auto-fallback. |
| **P3** | **N10 · Setup pre-flight checklist** | One-time card: VPN consent, notification permission (A13+), always-on hint, links out to the battery guardian. |
| **P3** | **N11 · Config sanity export / redacted share-debug bundle** | Portable, secret-stripped snapshot of settings+profiles for support, safe under the locked-template threat model. |

---

## P0

### N1 · Pre-connect "Bypass Readiness" linter

- **What:** A small, pure-logic validator that inspects the **selected profile** (and, for TLS
  nodes, the effective obfuscation prefs) *before* connecting and returns a short list of
  plain-language findings with **one-tap fixes**. Rules it checks, all high-signal for the TSPU
  regime: empty/obviously-fake `sni` on a Reality/TLS node; missing or non-browser `fingerPrint`
  (uTLS); `echConfigList` populated (ECH is a **flag** in RU, `strategy-russia §1.7`); Mux enabled
  on a Vision/Reality node (`flow=xtls-rprx-vision` + Mux **hurts**, `strategy-russia §3.2`);
  Fragment off while in a censored region; datacenter-range server IP; UDP/QUIC transport where the
  network just failed a UDP probe. Surface as a subtle chip on the connect hero ("2 tips") that
  opens a sheet; **never blocks** connecting.
- **Why valuable here:** This is the single cheapest way to help users **self-tune bypass**. Most
  RU/Iran connection failures are a *misconfigured* good protocol (empty SNI, Mux-on-Vision, ECH
  on), not a bad server. `strategy-russia` repeatedly says "validate templates and warn" but no
  module owns it. It converts expert tribal knowledge into inline, actionable guidance.
- **Effort:** **S–M** (rules are simple; the fix-actions and copy are the work).
- **Mem/perf:** ▁ pure synchronous checks on one object; no background work.
- **Cross-platform:** **Fully platform-agnostic** — a `BypassLinter(profile, prefs) -> List<Finding>`
  over `ProfileItem`/prefs is portable verbatim to the Windows/Linux core. Keep it UI-free.
- **Files/areas:** new `handler/BypassLinter.kt` (pure); reads
  `dto/entities/ProfileItem.kt` (has `flow`, `security`, `sni`, `alpn`, `fingerPrint`,
  `echConfigList`, `publicKey`) and `AppConfig.kt` (`PREF_FRAGMENT_*`, `PREF_MUX_*`); a findings
  sheet wired into the connect path in `ui/MainActivity.kt` / `viewmodel/MainViewModel.kt`; fixes
  write via `handler/SettingsManager.kt`.
- **Risks:** Must **defer to locked templates** — show findings as read-only (no fix button) when
  the field is operator-forced, and never reveal a locked value. Avoid false alarms (some SNIs are
  legitimately allow-listed) → keep the hint set updatable via subscription (ties `strategy-russia
  R2.4`), don't hardcode. Keep it advisory, never a hard gate.

### N2 · Device clock-skew guard

- **What:** On app start and before a Reality/TLS connect, compare the device clock to a trusted
  time source (the `Date:` header already returned by the `generate_204` probe in
  `SpeedtestManager`, or an SNTP query) and, if skew exceeds ~±90 s, show a one-line banner: "Your
  phone clock is off by ~4 min — secure connections may fail. Fix date & time." with a deep-link to
  system date settings.
- **Why valuable here:** TLS/Reality handshakes **fail hard on large clock skew**, and this presents
  to the user as an inexplicable "no server works" — a classic, hard-to-diagnose support case,
  disproportionately common on cheap in-region hardware with dead RTC batteries. Detecting it is a
  handful of lines and removes a whole class of false "the app is broken" churn.
- **Effort:** **S.**
- **Mem/perf:** ▁ one header read or one SNTP round-trip, cached per session.
- **Cross-platform:** Logic (skew comparison, threshold) is platform-agnostic; only the "open date
  settings" intent is Android-specific.
- **Files/areas:** `handler/SpeedtestManager.kt` (reuse the 204 probe; expose server `Date`), a tiny
  `util/TimeCheck.kt`, banner in `ui/MainActivity.kt`.
- **Risks:** Don't nag when offline (no reference time → skip silently); tolerate small skew;
  respect user dismissal for the session.

---

## P1

### N3 · Post-connect reachability + captive-portal check

- **What:** Immediately after the tunnel comes up, run one **generate_204** request *through* the
  tunnel. Three outcomes drive clear status: **204 →** "Online via <server>"; **non-204 / redirect →**
  "Connected, but this Wi-Fi needs sign-in" (captive portal); **timeout →** "Connected but no
  internet — try another server" (offer N9 rotate). Optionally confirm the visible exit IP via
  `getRemoteIPInfo()` for a "you're browsing as <country>" line.
- **Why valuable here:** "VPN says connected but nothing loads" is the top confusion for
  non-technical users, and under TSPU it has *several distinct causes* (captive portal, the 16 KB
  freeze, DNS block, total mobile shutdown). A single post-connect probe turns an opaque green
  "connected" into an **honest** state. Distinct from `ping-methods` (which *ranks nodes* pre-connect)
  and from the planned RU diagnostics classifier (which is a deeper "why can't I connect" panel) —
  this is the lightweight always-on confidence signal on the home screen.
- **Effort:** **S.**
- **Mem/perf:** ▁ one request on connect; no polling (health-check loop is a separate planned item).
- **Cross-platform:** Probe logic portable; the result maps to any UI.
- **Files/areas:** `handler/SpeedtestManager.kt` (204 probe + `getRemoteIPInfo()` exist),
  `service/CoreVpnService.kt` connect callback, `viewmodel/MainViewModel.kt`, home status chip.
- **Risks:** Choose a neutral, RU-reachable 204 endpoint (a blocked one gives false "no internet");
  make the exit-IP lookup opt-in (it's an outbound call). Don't confuse with the health-check loop —
  fire once, not on a timer.

### N4 · Inline settings glossary ("?" plain-language help)

- **What:** A **strings-only** microcopy layer: a small "?" affordance next to each bypass-relevant
  setting (Fragment, Mux, uTLS fingerprint, SNI, Reality, MTU, FakeDNS, kill-switch) that opens a
  2–3 sentence plain-language explainer — *what it does, when to turn it on, when it hurts* — with a
  link to the relevant scenario ("recommended ON in Russia mode"). No new engine; it annotates
  existing controls.
- **Why valuable here:** The app already exposes powerful bypass knobs, but they're expert-hostile;
  users flip them blindly. Good microcopy is the highest-leverage, lowest-risk way to make
  **self-tuning** safe and to reduce misconfiguration (feeds the same goal as N1). Directly serves
  the "UX clarity" priority. Near-zero code, near-zero memory.
- **Effort:** **S** (mostly writing + i18n; a reusable help-popover component).
- **Mem/perf:** ▁ static strings.
- **Cross-platform:** The help **content** is portable (ship as a shared key→text map); only the
  popover widget is per-platform.
- **Files/areas:** `res/values*/strings.xml` (+ RU/FA locales), a reusable help-icon binding used in
  `res/xml/pref_settings.xml` screens and `ui/SettingsActivity.kt`.
- **Risks:** Keep copy **factual, non-promotional** (RU legal surface, `strategy-russia §4.1`);
  translation upkeep; avoid tooltip clutter (only annotate the ~8 knobs that matter).

### N5 · Smart clipboard / paste-anything import

- **What:** Two small conveniences over the existing importer: (a) when the app returns to
  foreground and the clipboard holds a recognizable config (`vless://`/`vmess://`/`ss://`/`trojan://`,
  a subscription URL, or a base64 blob), show a one-tap "Import copied config?" snackbar; (b) a
  "**Import QR from photo**" action that decodes a QR from a gallery image (many users receive
  configs as screenshots from a Telegram channel).
- **Why valuable here:** In-region, configs spread as **pasted links and screenshots** via Telegram,
  not app-store deep links. Removing the "open scanner → aim at another screen" friction is a real
  onboarding win for non-technical users. Extends the existing `ScannerActivity` /
  `UrlSchemeActivity` / `QRCodeDecoder` rather than adding an engine.
- **Effort:** **S.**
- **Mem/perf:** ▃ clipboard read on resume + on-demand image decode; no background service.
- **Cross-platform:** Link/format detection is portable; clipboard + gallery access are per-platform.
- **Files/areas:** `ui/MainActivity.kt` (foreground clipboard peek), `util/QRCodeDecoder.kt` /
  `helper/QRCodeScannerHelper.kt` (add bitmap-from-gallery path), reuse
  `handler/AngConfigManager.kt` import.
- **Risks:** **Privacy** — never auto-import; only *offer*, and don't log clipboard contents. Guard
  against clipboard spam/loops; respect Android 12+ clipboard-access notifications.

### N6 · Connection event timeline (in-memory ring)

- **What:** A bounded, in-memory ring buffer (e.g. last ~50 events, human-readable) recording the
  connection lifecycle: connected to X, stall detected, failed over X→Y, network changed
  (Wi-Fi↔cellular), reconnect after shutdown window, kill-switch engaged. Shown as a simple
  "Activity" list on the connection-insight sheet, with a one-line summary chip ("2 failovers in the
  last hour").
- **Why valuable here:** With auto-fallback and frequent RU mobile-shutdown reconnects, the tunnel
  will visibly flap; users currently have **no explanation**, which reads as unreliability. A plain
  event log builds trust and turns "it keeps dropping" support threads into self-serve understanding.
  Distinct from the RAM panel (resource monitor) and from the RU diagnostics classifier (prescriptive
  "why can't I connect") — this is a passive **historical** record of what the app already did.
- **Effort:** **S–M.**
- **Mem/perf:** ▃ **bounded ring buffer only** — a few KB, capped count, no disk persistence by
  default (flag below). This is the anti-bloat design.
- **Cross-platform:** The event model + ring is pure Kotlin, portable to desktop; only the emit
  points are wired to the Android service.
- **Files/areas:** new `handler/ConnectionEventLog.kt` (ring), emit hooks in `service/CoreVpnService.kt`
  and the auto-fallback path (`core/CoreServiceManager.kt` / `viewmodel/MainViewModel.kt`), a list on
  the insight sheet.
- **Risks:** **Bloat flag** — do **not** let this grow into a second full logcat or a persisted
  analytics store; keep it in-memory, capped, and redact server identifiers per `hidden-templates §3`.
  If persistence is ever added, cap bytes and gate it off by default.

---

## P2

### N7 · Subscription update diff summary

- **What:** When a subscription refresh completes, compute a **diff** against the prior server set
  and show "**+3 added · 2 removed · 1 changed**" with an expandable review, instead of silently
  swapping the list. Crucially, **never replace a working list with an empty/failed fetch** (guards
  the `strategy-russia §3.4 #4` "never wipe on fail" rule) and let the user keep the old set if a
  push looks wrong.
- **Why valuable here:** Operators rotate servers constantly under TSPU; today a refresh is opaque and
  a bad/empty push can wipe good servers mid-block. A visible diff builds trust, surfaces operator
  rotations, and is a safety net against a broken subscription response. Complements the
  subscription-meta-bar (which shows quota/expiry, not *what changed*).
- **Effort:** **M.**
- **Mem/perf:** ▃ one comparison over two lists at refresh time.
- **Cross-platform:** The diff algorithm over `ProfileItem` lists is **fully portable**.
- **Files/areas:** `handler/AngConfigManager.kt` (`updateConfigViaSub`), `handler/SubscriptionUpdater.kt`,
  `dto/SubscriptionUpdateResult.kt` (extend with added/removed/changed), a review dialog in
  `ui/SubSettingActivity.kt` / `viewmodel/SubscriptionsViewModel.kt`.
- **Risks:** Identity/keying of nodes across refreshes (define a stable key — remark+address+port);
  respect locked templates (don't diff-expose hidden fields); keep the default flow one-tap for
  casual users (auto-apply + a passive "what changed" chip, not a mandatory prompt).

### N8 · Lite / low-resource mode

- **What:** A single "Lite mode" toggle (auto-suggested on low-RAM devices) that: disables/*reduces*
  UI animations and the connect-ring effects, **lengthens** background health-check / auto-probe
  intervals, caps the N6 event ring and log retention, and skips optional outbound lookups (exit-IP,
  flags imagery). A remediation counterpart to the (planned) RAM monitor panel.
- **Why valuable here:** The target audience runs a lot of cheap, low-memory Android hardware; the
  redesign's glass/animation and the new background probes cost battery and RAM. One honest "make it
  light" switch respects the **anti-bloat / low-memory** priority and keeps the app usable on weak
  devices — and it's a natural CTA from the RAM panel.
- **Effort:** **S–M.**
- **Mem/perf:** ▁ the toggle itself; **net negative** memory/CPU when on (that's the point).
- **Cross-platform:** The "performance budget" flags are portable; some (animations) are UI-only.
- **Files/areas:** `AppConfig.kt` (new `PREF_LITE_MODE`), gate points across `ui/MainActivity.kt`
  (animations), the health-check/probe scheduler, `handler/ConnectionEventLog.kt` (N6) and
  `LogcatActivity` retention.
- **Risks:** Longer health-check intervals slightly slow failover — document the trade-off; don't let
  Lite mode disable safety features (kill-switch, leak guards). Keep it one switch, not a settings
  maze (avoid re-introducing bloat as configurability).

### N9 · Manual "rotate / next server" + temporary avoid

- **What:** A one-tap "**Next server**" control on the home hero and in the notification that jumps to
  the next-best-ranked node, plus a "**Avoid this server for 30 min**" action that pushes a flapping
  node down the ranking temporarily. The manual, user-driven sibling of the automatic fallback engine.
- **Why valuable here:** Auto-fallback handles silent stalls, but users also want *agency* — "this
  one feels slow, give me another" without hunting the server list. Under RU throttling, quick manual
  rotation is a common self-serve behavior; a temporary avoid-list stops the auto-picker from
  re-selecting a node the user just rejected.
- **Effort:** **S–M** (reuses the ranking + reconnect path already built for auto-fallback).
- **Mem/perf:** ▃ a small in-memory avoid set with timestamps.
- **Cross-platform:** Ranking + avoid-set logic portable; the notification action is Android-specific.
- **Files/areas:** `viewmodel/MainViewModel.kt` (ranking/select), `core/CoreServiceManager.kt` /
  `service/CoreVpnService.kt` (reconnect), `handler/NotificationManager.kt` (action button), home UI.
- **Risks:** Define interaction with auto-fallback (manual choice should stick / be honored, per the
  "sticky success" rule in `strategy-russia §3.1`); avoid-set must expire and never strand the user
  with zero candidates.

---

## P3

### N10 · Setup pre-flight checklist

- **What:** A one-time, dismissible "Finish setup" card that verifies and links out to the handful of
  OS-level prerequisites reliability depends on: **VPN consent** granted, **notification permission**
  (Android 13+ `POST_NOTIFICATIONS`, needed for the rich notification), **always-on/kill-switch** hint,
  and a pointer to the **battery-exemption / autostart guardian** (round-1 M12, not duplicated here).
  Shows only items that are actually unmet.
- **Why valuable here:** Auto-reconnect, the rich notification, and always-on are only as good as the
  permissions behind them; missing `POST_NOTIFICATIONS` silently kills the status notification on new
  Android. A short, self-clearing checklist prevents "it stopped working in the background" confusion
  and consolidates onboarding. Scoped to **not** duplicate M12 (it links to it, owns the non-battery
  items).
- **Effort:** **S.**
- **Mem/perf:** ▁ a few permission/state reads, once.
- **Cross-platform:** The "unmet prerequisites" concept ports, but the specific checks are per-OS
  (keep them behind an interface).
- **Files/areas:** `helper/PermissionHelper.kt`, `enums/PermissionType.kt`, a card in
  `ui/MainActivity.kt`; battery item defers to M12.
- **Risks:** Don't nag — show once, honor dismissal, re-surface only if a prerequisite actually
  regresses. Avoid overlap creep with M12 (battery) and the always-on work in `strategy-russia`.

### N11 · Config sanity export / redacted share-debug bundle

- **What:** A "**Export for support**" action that produces a **secret-stripped** text/JSON snapshot:
  app version, device/OS, active transport family and obfuscation flags (Fragment/Mux/uTLS/DNS state),
  the N6 event summary, and the N1 linter findings — **with all credentials, UUIDs, keys, real SNIs,
  subscription URLs, and locked-template contents redacted**. Shareable to the operator's Telegram
  support without leaking the user's config.
- **Why valuable here:** Support for a censorship tool needs enough context to diagnose *without* the
  user pasting a live, incriminating config into a chat. A structured, pre-redacted bundle is safer
  and faster than "send me a screenshot." Complements the planned diagnostics/logcat surfaces by
  focusing on **safe shareability**. Adjacent to `ux-recommendations`' share-debug idea; this round
  owns the **redaction contract**.
- **Effort:** **S–M** (redaction correctness is the real work).
- **Mem/perf:** ▁ on-demand string build.
- **Cross-platform:** The redaction + serialization logic is **fully portable** and worth sharing with
  the desktop port verbatim.
- **Files/areas:** new `util/SupportBundle.kt`, reads `dto/entities/ProfileItem.kt` /
  `AppConfig.kt` / `handler/ConnectionEventLog.kt` (N6); shares via existing intent paths;
  redaction must honor `hidden-templates-design §3` (locked configs never emitted).
- **Risks:** **Redaction is safety-critical** — a leak here de-anonymizes a user (RU real-IP/config
  correlation, `strategy-russia §1.5`). Default to allow-list serialization (emit only known-safe
  fields), never blocklist. Unit-test that no key/UUID/URL/SNI escapes.

---

## Cross-cutting notes

- **Where these fit the priorities.** *Self-tune bypass:* N1 (linter), N4 (glossary), N9 (rotate),
  and N2 (clock) as the invisible prerequisite. *Clarity/onboarding:* N3 (honest online state),
  N4, N5 (import friction), N7 (what changed), N10 (setup). *Reliability:* N2, N3, N6 (timeline),
  N8 (lite mode), N9.
- **Reuse over rebuild.** Every module extends something already in the tree —
  `SpeedtestManager`'s 204 probe (N2/N3), `ProfileItem`'s obfuscation fields (N1/N11), the auto-
  fallback ranking (N9), the importer (N5), the subscription updater (N7). None add a new core.
- **Cross-platform lift.** N1, N7, and N11 are **pure config logic** with no Android dependency —
  the natural first candidates for a shared `core-config` Kotlin module when Windows/Linux lands.
  N2/N3 logic ports too; only their OS intents don't.
- **Bloat flags (explicit, so we can avoid them).**
  - **N6** must stay an in-memory capped ring — do **not** grow it into a second logcat or a
    persisted analytics store.
  - **N5** must **offer**, never auto-import, and never log clipboard contents.
  - **N8** must stay *one* switch — resist turning "make it light" into a configurability maze.
  - **N4** must annotate only the ~8 knobs that matter — no tooltip on every row.
  - **N7/N9** must keep the default one-tap path for casual users (passive chips, not mandatory
    prompts).
- **Locked-template respect.** N1, N7, N9, N11 all read config; each must defer to operator hidden
  templates — findings become read-only, diffs hide locked fields, exports never emit locked values.

## Sources

- Clock skew breaks TLS/Reality handshakes (correct system time is a documented prerequisite for
  TLS/Reality): general TLS validity-window behavior; Xray Reality relies on a valid TLS handshake —
  see XTLS/Xray-core issues on handshake sensitivity, e.g. https://github.com/XTLS/Xray-core/issues/5332
- Captive-portal / connectivity check pattern (generate_204): Android/Chromium connectivity-check
  design — https://developer.android.com/reference/android/net/ConnectivityManager and the widely used
  `generate_204` endpoint convention (already used by this fork's `SpeedtestManager`).
- ECH as a block trigger in RU, Mux-hurts-Vision, "never wipe on failed sub fetch", real-IP leak
  correlation, allow-listed SNI: see `docs/strategy-russia-2026.md` (§1.5, §1.7, §3.1–3.4, §4.1) and
  its cited sources (net4people/bbs #490 https://github.com/net4people/bbs/issues/490 ; zona.media
  https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026 ).
- Config distribution via Telegram screenshots/links (motivates N5): Durov on Telegram as the
  censorship-resistant channel — https://www.benzinga.com/markets/tech/26/04/51591434/telegram-ceo-pavel-durov-slams-apple-for-removing-vpn-apps-russia
- Redacted diagnostics / safe share-debug and locked-template threat model: `docs/hidden-templates-design.md`
  §3, `docs/ux-recommendations.md` (diagnostics/share-debug).

*Advisory document. Sources cited inline. No application code modified.*
