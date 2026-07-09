# departament VPN — "Bypass / Anti-censorship" Settings Design

**Status:** Design only. No app code is changed by this document.
**App:** v2rayNG / Xray-core fork, package `com.v2ray.ang`, Kotlin + AndroidX Preferences, app label `departament`.
**Scope:** turn the scattered advanced circumvention prefs into ONE coherent, non-expert-friendly screen
with presets, inline explanations, and honest guidance — without adding bloat or new transports.

**Read-first / coordinate.** This complements `docs/strategy-russia-2026.md` (the "what survives TSPU"
research + the R0.2 "Russia mode" preset it asks for), `docs/ux-recommendations.md`,
`docs/design-system-2026.md`. This doc is the *settings-surface* slice of strategy §2 (#2–4), §3.2–3.5.
It does NOT re-plan auto-fallback (`docs/review-04-auto-fallback.md`), subscription delivery
(`docs/telegram-auth-design.md`), or hidden templates (`docs/hidden-templates-design.md`).

> **Honesty note (applies throughout, and is surfaced in the UI).** There is no permanently
> unblockable setting. These knobs help on *some* networks and not others; DPI is a moving target.
> The screen must encourage experimentation and never promise a bypass. See §7.

---

## 1. Current state — what already exists (verified in the tree)

All the raw knobs exist today; they are just scattered across three collapsed `PreferenceCategory`
blocks in `app/src/main/res/xml/pref_settings.xml` with terse or missing summaries, and one lives on a
different screen.

### 1.1 Pref keys (constants in `app/src/main/java/com/v2ray/ang/AppConfig.kt`)

| Knob | Pref key constant | Default | Where consumed |
|---|---|---|---|
| Fragment on/off | `PREF_FRAGMENT_ENABLED` (`pref_fragment_enabled`) | `false` | `core/CoreOutboundBuilder.kt:596` |
| Fragment packets | `PREF_FRAGMENT_PACKETS` (`pref_fragment_packets`) | `tlshello` | `CoreOutboundBuilder.kt:609` |
| Fragment length | `PREF_FRAGMENT_LENGTH` (`pref_fragment_length`) | `50-100` (summary) | `CoreOutboundBuilder.kt:624` |
| Fragment interval | `PREF_FRAGMENT_INTERVAL` (`pref_fragment_interval`) | `10-20` (summary) | `CoreOutboundBuilder.kt:626` |
| Mux on/off | `PREF_MUX_ENABLED` (`pref_mux_enabled`) | `false` | `CoreOutboundBuilder.kt:47` |
| Mux concurrency | `PREF_MUX_CONCURRENCY` (`pref_mux_concurrency`) | `8` | `CoreOutboundBuilder.kt:64` |
| xudp concurrency | `PREF_MUX_XUDP_CONCURRENCY` (`pref_mux_xudp_concurrency`) | `8`/`16` | `CoreOutboundBuilder.kt:65` |
| xudp/QUIC (UDP 443) | `PREF_MUX_XUDP_QUIC` (`pref_mux_xudp_quic`) | `reject` | `CoreOutboundBuilder.kt:66` |
| Sniffing | `PREF_SNIFFING_ENABLED` (`pref_sniffing_enabled`) | `true` | core routing/sniffing |
| Sniff route-only | `PREF_ROUTE_ONLY_ENABLED` (`pref_route_only_enabled`) | `false` | core |
| domainStrategy (routing) | `PREF_ROUTING_DOMAIN_STRATEGY` (`pref_routing_domain_strategy`) | `AsIs` | `core/CoreConfigManager.kt:877` |
| Outbound domain resolve | `PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD` | `1` | core (per-outbound `UseIP`) |
| Remote DNS (proxied) | `PREF_REMOTE_DNS` (`pref_remote_dns`) | — | core DNS |
| Domestic DNS (direct) | `PREF_DOMESTIC_DNS` (`pref_domestic_dns`) | — | core DNS |
| VPN DNS | `PREF_VPN_DNS` (`pref_vpn_dns`) | — | tun DNS |
| DNS hosts | `PREF_DNS_HOSTS` (`pref_dns_hosts`) | — | core DNS |
| FakeDNS | `PREF_FAKE_DNS_ENABLED` (`pref_fake_dns_enabled`) | `false` | core DNS |
| Local DNS | `PREF_LOCAL_DNS_ENABLED` (`pref_local_dns_enabled`) | `false` | core DNS |
| MTU | `PREF_VPN_MTU` (`pref_vpn_mtu`) | `1500` | tun setup |
| Allow insecure | `PREF_ALLOW_INSECURE` (`pref_allow_insecure`) | `false` | TLS build |

### 1.2 The uTLS fingerprint gap (important)

**There is no global uTLS-fingerprint pref today.** The fingerprint is stored *per profile*
(`ProfileItem.fingerPrint`), parsed from the share link's `fp=` query param
(`fmt/FmtBase.kt:85`) and written into TLS/Reality at `core/CoreOutboundBuilder.kt:564`
(`populateTlsSettings`). The uTLS value set already exists as an array —
`res/values/arrays.xml` `streamsecurity_utls`: *(empty)*, `chrome`, `firefox`, `safari`, `ios`,
`android`, `edge`, `360`, `qq`, `random`, `randomized`. So a profile whose link carried no `fp=`
runs with an **empty** fingerprint — a direct JA3/JA4 flag under DPI
([Xray Reality+Vision+uTLS](https://j3ffyang.medium.com/xray-with-reality-vision-utls-3abfb63b682e),
[XTLS #4900 JA4](https://github.com/XTLS/Xray-core/issues/4900)). Closing this gap (a global
"enforce browser fingerprint when the node didn't specify one") is the one genuinely new pref this
design introduces; everything else is re-surfacing.

### 1.3 Screen plumbing that exists

- `ui/SettingsActivity.kt` — `SettingsFragment : PreferenceFragmentCompat`, MMKV-backed via
  `MmkvPreferenceDataStore`; already wires `mux`/`fragment`/`vpnMtu`/DNS prefs and has an
  `updateMux(...)` enable/disable pattern.
- `handler/SettingsChangeManager.kt` — `makeRestartService()` / `consumeRestartService()`: the
  existing "settings changed, restart the tunnel" signal we reuse for the reconnect badge (§4.3).
- `handler/MmkvManager.kt` — `encodeSettings(key, …)` / `decodeSettingsString/Bool(key, default)`:
  read/write path a preset applies through.

---

## 2. Design principle — "one approachable screen, presets first, honest copy"

Non-experts should never have to know what a "ClientHello" is to get unblocked. The screen is built
around **three tiers of effort**:

1. **Tier 0 — Pick a preset.** A card at the top. One tap sets the whole bundle (§3). 90% of users
   stop here.
2. **Tier 1 — Toggle the big three.** Fragment, Browser fingerprint, Mux — plain-language switches
   with a one-line "what this does" and a `?` for more. Changing any of these silently flips the
   preset chip to **Custom**.
3. **Tier 2 — Fine-tune (collapsed).** Length/interval, concurrency, DNS/DoH, domainStrategy,
   sniffing, MTU — hidden behind "Advanced tuning" (`app:initialExpandedChildrenCount="0"`, already
   the pattern for the mux/fragment categories).

Anti-bloat rules: no new transports here; no duplicated DNS UI (link out to existing DNS/Routing
screens where they already exist); every item earns its place with a *why*. Target: the whole screen
is ~6 toggles visible by default, everything else collapsed.

---

## 3. Presets (Tier 0) — concrete pref bundles

A preset is just a named map of `(pref key → value)` applied via `MmkvManager.encodeSettings`, then a
restart badge. Presets are **starting points, not guarantees** — copy says so. The active preset is
remembered in a new pref `PREF_BYPASS_PRESET` (`pref_bypass_preset`; values `standard`, `russia`,
`iran`, `lowlatency`, `custom`) purely to drive the selected chip; the *behavior* is entirely the
underlying prefs, so a preset is safe to remove/rename later.

| Pref | **Standard** | **Russia / strict DPI** | **Iran** | **Low-latency** |
|---|---|---|---|---|
| `PREF_FRAGMENT_ENABLED` | `false` | **`true`** | **`true`** | `false` |
| `PREF_FRAGMENT_PACKETS` | `tlshello` | `tlshello` | `tlshello` | `tlshello` |
| `PREF_FRAGMENT_LENGTH` | `50-100` | **`10-20`** (aggressive) | **`5-10`** | `50-100` |
| `PREF_FRAGMENT_INTERVAL` | `10-20` | **`10-20`** | **`10-30`** | `10-20` |
| `PREF_UTLS_FINGERPRINT` *(new, §1.2)* | `chrome` | **`chrome`** | **`firefox`** | `chrome` |
| `PREF_MUX_ENABLED` | `false` | **`false`** (Vision self-muxes) | `false` | **`false`** |
| `PREF_MUX_XUDP_QUIC` | `reject` | `reject` | `reject` | `allow` |
| `PREF_ROUTING_DOMAIN_STRATEGY` | `AsIs` | **`IPIfNonMatch`** | `IPIfNonMatch` | `AsIs` |
| `PREF_SNIFFING_ENABLED` | `true` | `true` | `true` | `true` |
| `PREF_FAKE_DNS_ENABLED` | `false` | **`true`** | `true` | `false` |
| `PREF_REMOTE_DNS` (DoH) | `https://1.1.1.1/dns-query` | `https://1.1.1.1/dns-query` | `https://8.8.8.8/dns-query` | `https://1.1.1.1/dns-query` |
| `PREF_VPN_MTU` | `1500` | `1500` | `1500` | `1500` |

**Why each preset is shaped this way (shown as the preset's subtitle in-app):**

- **Standard** — no fragment, no mux, browser fingerprint enforced, plain DNS. For open/lightly-filtered
  networks; lowest overhead. Good first thing to try.
- **Russia / strict DPI** — mirrors `strategy-russia-2026.md` §3.2–3.3: **Fragment ON** with an
  aggressive ClientHello split to frustrate SNI extraction and the ClientHello-corruption / 25-packet
  "TLS freeze" heuristic ([net4people #490](https://github.com/net4people/bbs/issues/490)); a **Chrome
  uTLS fingerprint** so JA3/JA4 look like a real browser; **Mux OFF** because Reality+XTLS-Vision
  manages its own flow and Vision *cannot* share a mux connection
  ([XTLS #2166](https://github.com/XTLS/Xray-core/discussions/2166)); **FakeDNS + foreign DoH**;
  `IPIfNonMatch` so RU-whitelist domains can route direct. Copy explicitly names the "Reality-first,
  allow-listed SNI" server-side requirement (a *node* property, set by the operator/subscription, not
  this screen) so users understand the preset is only half the story.
- **Iran** — same family; shorter fragments (`5-10`) and Firefox fingerprint reflect community-reported
  tuning for Iranian DPI (fragment is the "community-proven first-line" evasion,
  [MahsaNG](https://github.com/GFW-knocker/MahsaNG); [v2rayNG #2996](https://github.com/2dust/v2rayNG/issues/2996)).
- **Low-latency** — fragment off, mux off, allow UDP 443/QUIC through xudp, plain DNS. For users on a
  free network who want max throughput and don't need evasion.

Preset application is idempotent and only writes the keys in the table; it never touches unrelated
prefs (socks port, theme, per-app proxy).

---

## 4. Screen layout & per-item copy (Tier 1 + Tier 2)

New screen `res/xml/pref_bypass.xml`, opened from a single "Bypass / Anti-censorship" entry on the
main Settings screen. Groups top-to-bottom:

### 4.1 Preset card (Tier 0)
A `ListPreference`-style row or custom card: 4 presets + "Custom (advanced)". Selecting one applies
§3 and shows a one-line honesty subtitle: *"A starting point — some networks need a different mix.
Try another preset if this one stalls."*

### 4.2 The big three (Tier 1, always visible)

**Fragment / "packet noise"** — `CheckBoxPreference` `PREF_FRAGMENT_ENABLED`.
- Title: *"Split the handshake (Fragment)"*.
- Summary: *"Chops the first encrypted packet into small pieces so filters can't read which site you're
  visiting. Helps against SNI/handshake blocking; costs a little speed."*
- `?` help: explains it splits the TLS ClientHello across several TCP segments with delays so DPI that
  only reassembles the first segment can't extract the SNI, and that it only helps where the filter
  doesn't do full stream reassembly ([v2rayNG #2996](https://github.com/2dust/v2rayNG/issues/2996),
  [v2rayN #3761](https://github.com/2dust/v2rayN/issues/3761)).
- Sub-preset chips right under it: **Normal** (`length 50-100`, `interval 10-20`) /
  **Aggressive** (`length 10-20`, `interval 10-20`) — writes the two length/interval prefs so users
  never hand-type ranges. Only shown when Fragment is ON.

**Browser fingerprint (uTLS)** — new `ListPreference` `PREF_UTLS_FINGERPRINT`.
- Title: *"Look like a real browser (TLS fingerprint)"*.
- Entries: *Recommended (Chrome)* → `chrome`, *Firefox*, *Safari*, *Edge*, *Randomized* → `randomized`,
  *Off / use server value* → empty. Default `chrome`.
- Summary: *"Makes your encrypted handshake look like Chrome/Firefox instead of a VPN app, so
  fingerprint-based filters (JA3/JA4) don't single it out."*
- `?` help: DPI classifies TLS ClientHellos; a missing/exotic fingerprint is itself a flag, a
  browser one blends in ([uTLS/JA3](https://j3ffyang.medium.com/xray-with-reality-vision-utls-3abfb63b682e),
  [XTLS #4900](https://github.com/XTLS/Xray-core/issues/4900)). Note: `chrome_pq` is intentionally not
  offered because it breaks Reality ([sing-box #2084](https://github.com/SagerNet/sing-box/issues/2084)).
- Behavior (new code, §5): applied only as a **fallback** when a node's own `fp=` is empty, so operator
  templates keep control (strategy §3.3). "Off" preserves today's per-node behavior.

**Mux (connection multiplexing)** — `CheckBoxPreference` `PREF_MUX_ENABLED`.
- Title: *"Combine connections (Mux)"*.
- Summary: *"Bundles many requests into one tunnel connection. Can help on networks that limit the
  number of connections — but hurts Reality/Vision nodes and can slow browsing. Leave off if unsure."*
- `?` help: Vision doesn't support mux (a single mux connection can't tell sub-streams apart), so mux
  should be OFF for the recommended Reality+Vision setup
  ([XTLS #2166](https://github.com/XTLS/Xray-core/discussions/2166)); it mainly helps when an ISP caps
  concurrent connections. Note the core already force-disables mux for SS/Trojan/WG/Hysteria and XHTTP
  (`CoreOutboundBuilder.kt:49-60`), so the toggle is honest about being a no-op there.

### 4.3 Reconnect badge
Any of the above (and every Tier-2 item) affects the *generated core config*, so changing them while
connected requires a tunnel restart. Reuse `SettingsChangeManager.makeRestartService()` (already wired
in `SettingsActivity`) and show a small inline **"Reconnect to apply"** chip on changed rows plus a
one-tap "Reconnect now" snackbar. Items that need it: fragment*, fingerprint, mux*, xudp/QUIC,
domainStrategy, sniffing, DNS*, MTU. (UI-only prefs like the preset chip do not.)

### 4.4 Advanced tuning (Tier 2, collapsed `initialExpandedChildrenCount="0"`)

- **Fragment length / interval** (`EditTextPreference`) — shown only if the "Aggressive/Normal" chips
  aren't enough. Help states safe ranges: length `1–200` bytes (small = more evasion, more overhead),
  interval `1–100` ms; smaller ClientHello chunks + short delays are the working combination
  ([v2rayN #3761](https://github.com/2dust/v2rayN/issues/3761)). Validate/clamp on input.
- **Fragment packets** (`ListPreference`, existing `fragment_packets` array: `tlshello`, `1-2`, `1-3`,
  `1-5`) — help: *"tlshello splits only the handshake (safest). 1-N also splits the first N data
  packets (more aggressive, slower)."*
- **Mux concurrency / xudp concurrency** (`EditTextPreference`, default `8`/`16`) — help: how many
  streams share one connection; higher can help under connection caps, lower is safer.
- **xudp / QUIC over UDP 443** (`ListPreference`, `mux_xudp_quic`: `reject`/`allow`/`skip`) — help:
  *"reject = don't send QUIC/UDP 443 through mux (best where UDP is blocked/throttled, e.g. Russia);
  allow = tunnel it (better for video where UDP works)."* Ties to strategy §1.5 (UDP 443 broadly
  throttled in RU).
- **DNS (DoH)** — reuse existing `PREF_REMOTE_DNS` / `PREF_DOMESTIC_DNS` / `PREF_FAKE_DNS_ENABLED`
  rows (or link to the DNS screen if one exists) rather than duplicating. Help: *"Use an encrypted
  foreign resolver (DoH, e.g. https://1.1.1.1/dns-query) for proxied sites so lookups aren't read or
  redirected; keep a domestic resolver for local/banking sites. FakeDNS avoids leaking real lookups."*
  (strategy §3.5).
- **domainStrategy** — reuse `PREF_ROUTING_DOMAIN_STRATEGY` (lives on `RoutingSettingActivity`; either
  mirror it here read-only or deep-link). Help: *"AsIs = fastest, sends the name as-is. IPIfNonMatch =
  resolves names to route by IP, needed for whitelist/split-tunnel rules; use it with Russia/Iran
  presets."*
- **Sniffing** (`PREF_SNIFFING_ENABLED`, default on) — help: *"Reads the destination from the
  handshake so routing rules work. Keep ON; turning it off breaks per-site routing."* Pair with
  route-only (`PREF_ROUTE_ONLY_ENABLED`) note.
- **MTU** (`PREF_VPN_MTU`, default `1500`) — help: *"Packet size for the VPN interface. Lower it
  (e.g. 1400/1280) only if pages hang on some mobile networks; 1500 is normal."* Numeric, clamp
  1280–1500.
- **Allow insecure** (`PREF_ALLOW_INSECURE`) — keep but with a red warning: *"Disables certificate
  checks. Only for testing your own server; unsafe on hostile networks."*

---

## 5. Implementation plan (real files)

Ordered, small, reuses existing patterns. Matches strategy R0.2.

1. **New pref constants** in `AppConfig.kt`: `PREF_UTLS_FINGERPRINT = "pref_utls_fingerprint"`,
   `PREF_BYPASS_PRESET = "pref_bypass_preset"`. (Fragment/mux/DNS/domainStrategy/MTU constants already
   exist — reuse.)

2. **Global uTLS fallback** — in `core/CoreOutboundBuilder.kt`, `populateTlsSettings` (line ~561):
   ```
   fingerprint = profileItem.fingerPrint.nullIfBlank()
       ?: MmkvManager.decodeSettingsString(AppConfig.PREF_UTLS_FINGERPRINT).nullIfBlank()
   ```
   i.e. node value wins; global browser fingerprint fills the empty case only. Empty pref = today's
   behavior, so no regression. This is the sole config-generation change.

3. **New screen** `res/xml/pref_bypass.xml` — preset card + the three Tier-1 toggles + collapsed
   "Advanced tuning" `PreferenceCategory`s. Move (don't duplicate) the existing fragment/mux categories'
   items here; leave stubs/links on the main `pref_settings.xml` or replace the fragment+mux categories
   with a single "Bypass / Anti-censorship →" navigation `Preference`.

4. **Screen controller** — either extend `ui/SettingsActivity.kt`'s `SettingsFragment` or add a sibling
   `BypassSettingsFragment : PreferenceFragmentCompat` (same `MmkvPreferenceDataStore` backing). Wire:
   preset selection → `applyPreset()`; Tier-1 changes → set `PREF_BYPASS_PRESET=custom` + reconnect
   badge; enable/disable dependent rows (reuse the existing `updateMux(...)` show/hide pattern for the
   fragment sub-rows).

5. **Preset engine** — a small `object BypassPresets` (new file
   `handler/BypassPresets.kt`, alongside `SettingsManager.kt`) holding the §3 table as
   `Map<String, Map<String,String>>` and an `apply(preset)` that loops `MmkvManager.encodeSettings(...)`
   then calls `SettingsChangeManager.makeRestartService()`. Keeping it data-driven means new presets
   are a map entry, not new UI code.

6. **Reconnect UX** — reuse `SettingsChangeManager.makeRestartService()`/`consumeRestartService()`
   (already observed by the main activity) for the "Reconnect to apply" chip/snackbar.

7. **Strings/arrays** — add titles/summaries/`?`-help strings to `res/values/strings.xml`; reuse
   existing arrays `streamsecurity_utls`, `fragment_packets`, `mux_xudp_quic_*`,
   `routing_domain_strategy`. Add a `utls_fingerprint_entries`/`_values` pair filtered to the
   browser-safe subset (chrome/firefox/safari/edge/randomized/off).

8. **Validation** — clamp fragment length (1–200) / interval (1–100) / MTU (1280–1500) in
   `setOnPreferenceChangeListener` before persisting; reject non-numeric.

No changes to transports, `EConfigType`, subscription, or routing engine. Auto-fallback ranking
(`docs/review-04-auto-fallback.md`) can later read the same preset to bias its candidate order.

---

## 6. What this deliberately does NOT do (anti-bloat / non-duplication)

- No new protocols/transports (AmneziaWG, TUIC live in strategy R2, not here).
- No second DNS/routing editor — link to `RoutingSettingActivity` / existing DNS rows.
- No auto-fallback logic (separate doc).
- No ECH toggle in presets — `cloudflare-ech.com`+ECH is itself an RU block trigger (strategy §1.7);
  leave ECH to per-node `echConfigList`, off by default.

---

## 7. Honesty & UX guardrails

- Every preset subtitle and the screen header carry a **factual, non-promotional** line — Russian law
  restricts *advertising* circumvention, so copy must describe function, not "beat the censor"
  (strategy §4.1).
- Persistent footer: *"These settings help on some networks and not others. If one preset stalls, try
  another, or a different server — there's no single magic setting."*
- Each `?` help ends with a one-line "when this helps / when it doesn't" so users form correct
  expectations instead of blaming the app.
- Encourage experimentation: a subtle "Not working? Try Aggressive fragment, switch fingerprint, or
  pick another server" hint on connection failure (ties into the diagnostics panel, strategy §2 #14).

---

## 8-line summary

1. Consolidate today's scattered Fragment / Mux+xudp / uTLS / DNS / domainStrategy / sniffing / MTU prefs into one "Bypass / Anti-censorship" screen — presets first, three plain toggles next, fine-tuning collapsed.
2. Ship 4 one-tap presets — **Standard, Russia/strict DPI, Iran, Low-latency** — each a data-driven map of concrete pref values (§3), aligned with `strategy-russia-2026.md` §3.2–3.5.
3. Russia preset = Fragment ON (aggressive `10-20` ClientHello split) + Chrome uTLS + Mux OFF (Vision self-muxes) + FakeDNS + foreign DoH + `IPIfNonMatch`.
4. The one new pref is a **global browser-fingerprint fallback** (`PREF_UTLS_FINGERPRINT`) applied only when a node's `fp=` is empty — closing a real JA3/JA4 flag; all other knobs already exist.
5. Each item gets a plain-language summary + a `?` help stating what it does and when it does/doesn't help; length/interval/MTU are clamped to safe ranges.
6. Config-generation change is a single line in `core/CoreOutboundBuilder.kt::populateTlsSettings`; presets loop `MmkvManager.encodeSettings` in a new `handler/BypassPresets.kt`; new `res/xml/pref_bypass.xml` + fragment controller reuse the existing `SettingsActivity` pattern.
7. Reconnect-required rows reuse `SettingsChangeManager.makeRestartService()` to show a "Reconnect to apply" chip; no new transports, DNS editors, or fallback logic (kept in their own docs).
8. Copy stays honest and non-promotional — presets are "starting points," a persistent footer says there's no magic setting, and failure hints nudge users to experiment.
