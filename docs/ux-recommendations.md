# departament VPN — UX & Product Recommendations (2026)

Status: **Advisory / product-UX, design-only.** No app code is changed by this document.
Scope: what will make **departament VPN** maximally intuitive, modern and delightful in 2026,
**beyond** what the three existing specs already cover.

**Read-first / do-not-duplicate.** This doc deliberately does **not** restate:
- `docs/design-system-2026.md` — colour/type/spacing tokens, bottom-nav, glass Settings, connect-ring
  *visual* states, responsiveness. (I reuse its tokens by name; I don't re-spec them.)
- `docs/telegram-auth-design.md` — the Telegram deep-link + poll login *mechanism*.
- `docs/hidden-templates-design.md` — locked/managed templates, header parsing, share-gating.

Where a recommendation touches those areas I extend them at the **product/interaction** level only.

**Who this app is for (the lens for every decision below).** Unlike stock v2rayNG, this is a
**managed, branded deployment**: a user signs in with Telegram and receives **one operator-managed
subscription** (often a *locked* profile), with **RU / EN / FA** as first-class locales. That means:
1. Onboarding is **"sign in"**, not "paste a subscription link" — the power-user import path exists but
   is secondary.
2. Many users are in **censored / high-stakes networks** (Iran's May 2026 whitelist regime and DPI
   blocking of known VPN IPs; Russia). Trust, discretion, DPI-resilience and *graceful failure* matter
   more than raw feature count. ([RFE/RL](https://www.rferl.org/a/iran-internet-censorship-x-twitter-vpn/33602370.html),
   [VPNOverview: censorship in Iran](https://vpnoverview.com/unblocking/censorship/internet-censorship-iran/))
3. The competitive bar for "modern native feel" is **Incy / NekoBox / Proton / Mullvad**, and the trap
   to avoid (per Incy reviews) is a **crowded, tiny-font** UI.

---

## 0. How the reference clients set the bar (delta research)

Grounding for the recommendations. Sources cited inline; these confirm what "table stakes" vs
"differentiator" is in 2026.

- **Mullvad** — automatic kill switch (blocks internet the instant the tunnel drops); split tunnelling
  by app; but reviewers note the UI "feels empty" and **lacks a quick-connect** — a gap we can beat.
  ([Mullvad Android help](https://mullvad.net/en/help/using-mullvad-vpn-on-android),
  [CyberInsider comparison](https://cyberinsider.com/vpn/comparison/mullvad-vs-proton-vpn/))
- **Proton VPN** — 2025-26 work went into **startup reliability** and **excluding locations from
  "Fastest"**; notably kill-switch and split-tunnel are **mutually exclusive on most platforms** — a
  real Android constraint we must design around, not hide.
  ([Proton fall/winter recap](https://protonvpn.com/blog/fall-winter-recap-2025-2026),
  [Proton kill switch](https://protonvpn.com/support/what-is-kill-switch))
- **IVPN** — the gold standard for **Trusted Wi-Fi networks**: per-network trust status that
  auto-connects/auto-firewalls on *untrusted* and auto-disconnects on *trusted*; needs location
  permission to read SSIDs; ships a **Quick Settings tile**.
  ([IVPN trusted Wi-Fi](https://www.ivpn.net/blog/new-trusted-wi-fi-networks-feature-for-ivpn-apps/),
  [IVPN Android](https://www.ivpn.net/en/apps-android/))
- **Android platform** — "**Block connections without VPN**" (system kill switch) + **Always-On** are
  OS features (Android 7+); an app can also register a **`TileService`** Quick Settings tile and app
  widgets. Design must lean on these rather than reinvent them.
  ([Android VPN docs](https://developer.android.com/develop/connectivity/vpn),
  [Always-On + kill switch 2026](https://nimbusvpn.tech/en/guides/always-on-vpn-kill-switch-android-2026/))
- **Happ** — auto-start on boot (Android-only), rule-based routing; subscription meta bar; encrypted
  "hidden" subscriptions. ([Happ app-management](https://www.happ.su/main/dev-docs/app-management))
- **NekoBox** — praised for a **clean, uncrowded interface** as the "modern alternative to v2rayNG".
  ([Grokipedia: NekoBox](https://grokipedia.com/page/NekoBox_for_Android))
- **UX craft** — every connect action should fire **immediate visual + haptic feedback**; connection
  animations (glowing ring / sweep) read as "actively securing"; motion **100–300 ms** feels right.
  ([kolpolok VPN UX](https://kolpolok.com/vpn-interface-ui-ux-tips/),
  [Justinmind micro-interactions 2025](https://www.justinmind.com/web-design/micro-interactions))

---

## 1. Prioritized recommendations (P0 → P3)

Effort key: **S** ≈ ≤2 days · **M** ≈ 3–8 days · **L** ≈ >8 days / cross-cutting. "Where" points at the
screen or the likely file(s). Nothing here contradicts the three existing specs.

### P0 — the spine of a delightful, trustworthy first session

| # | What | Why it helps | Effort | Where it fits |
|---|---|---|---|---|
| P0-1 | **3-screen first-run onboarding, then Telegram sign-in.** Screen 1: what the app is + brand promise; Screen 2: "we don't run the servers / your operator does" trust note; Screen 3: **one CTA — "Continue with Telegram"** (the mechanism is already designed). Skippable, shown once, never blocks a returning user. | New users currently hit a config-first UI with an empty server list — the #1 cause of first-session abandonment. Onboarding that "delivers value fast" and builds trust reduces churn. ([kolpolok](https://kolpolok.com/vpn-interface-ui-ux-tips/)) | M | New `OnboardingActivity` before `LoginActivity` (telegram-auth doc §3.6); gate in `MainActivity.onCreate`/`SubscriptionGuard`. |
| P0-2 | **"Connecting → Connected" as one honest, staged status,** not a spinner that lies. Show sub-states: *Preparing → Handshaking → Testing route → Connected* (map to core start + first successful probe). If a probe fails, jump straight to a **recovery** state (P0-4), never a frozen "Connecting…". | Censored networks fail slowly; a truthful staged status is the single biggest perceived-reliability win and matches Proton's 2025 investment in startup reliability. | M | `MainActivity` hero status label; drive from `V2RayServiceManager`/core callbacks. Extends design-system §5.1/§5.2 states. |
| P0-3 | **One-tap reconnect + last-known-good server memory.** On launch, if previously connected, the connect ring shows the last server pre-selected and connect is a single tap. After any drop, offer **"Reconnect"** as the primary action. | Beats Mullvad's missing quick-connect. Returning-user flow becomes literally one tap. | S | `MainActivity`; persist `lastConnectedGuid` in MMKV. |
| P0-4 | **Structured error & recovery layer** (see §5). Every failure maps to a plain-language cause + one primary fix button (*Retry*, *Try another server*, *Update subscription*, *Switch to fragment/mux*). Never surface raw core/log strings on the home screen. | In DPI environments failures are the *norm*, not the exception; recovery UX is where trust is won or lost. | M | New `ConnectError` sealed model; consumed by `MainActivity` + a bottom sheet. Logcat stays for power users only. |
| P0-5 | **Kill-switch that tells the truth about Android's limits.** Surface **"Block connections without VPN"** and **Always-On** as *in-app* cards that deep-link to the correct OS Settings page (with a screenshot-style hint), plus detect and **show current state**. Clearly state the **kill-switch ⊕ split-tunnel exclusivity** so users aren't surprised. | Kill switch is table stakes (Mullvad/Proton), but it's an OS feature; the value we add is *guidance + honest constraints*, which competitors bury. ([Android VPN](https://developer.android.com/develop/connectivity/vpn), [Proton](https://protonvpn.com/support/what-is-kill-switch)) | S–M | Settings → Connection group; intent to `Settings.ACTION_VPN_SETTINGS`. |
| P0-6 | **Import that "just works" from anywhere:** clipboard auto-detect on Servers-screen focus, QR scan, `vless://`/`vmess://`/`departament://`/`happ://` deep-links, and **paste-and-recognize** (detect single link vs subscription vs base64 blob and route correctly, with a preview sheet before committing). | The manual escape hatch for power users and for onboarding fallback. Reduces "why didn't my link work" support load. Reuses existing `AngConfigManager` parsing. | M | FAB/top-bar action on Servers (design-system §2); `UrlSchemeActivity`, `AngConfigManager.parseConfigViaSub`. |

### P1 — the everyday-use polish that makes it feel premium

| # | What | Why it helps | Effort | Where it fits |
|---|---|---|---|---|
| P1-1 | **Quick server switching without leaving Home:** a **"switch server" bottom sheet** invoked from the selected-server row — shows current sub's servers with live ping, favourites pinned, and **hot-swap while connected** (reconnect in place with a subtle ring pulse, no full teardown UI). | The most frequent action after connecting. Happ/Incy make you go to a list tab; doing it in-place on Home is a signature convenience. | M | `MainActivity` selected-server row → `ModalBottomSheet`; reuse server list adapter. |
| P1-2 | **Quick Settings tile (`TileService`) + a home-screen widget.** Tile toggles connect/disconnect and shows state; widget shows status + last server + up/down and a tap-to-toggle. | IVPN ships a tile; it's the fastest possible connect and a strong "installed & trusted" signal. Widgets keep the app one tap from the launcher. ([IVPN tile](https://www.ivpn.net/blog/new-trusted-wi-fi-networks-feature-for-ivpn-apps/)) | M | New `QuickTileService` + `AppWidgetProvider`; bind to `V2RayServiceManager`. |
| P1-3 | **Trusted / untrusted Wi-Fi auto-connect (IVPN model).** Per-SSID trust status; auto-connect (and optionally auto-enable Always-On) on untrusted, auto-disconnect on trusted. Ship **off by default**, behind an explicit location-permission rationale sheet. | The premium automation users cite most; turns the app from "a thing I tap" into "protection that's just on". ([IVPN trusted Wi-Fi](https://www.ivpn.net/blog/new-trusted-wi-fi-networks-feature-for-ivpn-apps/)) | L | New `NetworkMonitor` (WifiManager/ConnectivityManager) + Settings → Connection; respects Android 10+ location rules. |
| P1-4 | **Per-app split tunnelling with a humane picker.** Search, "system apps" toggle, **recently-used apps on top**, select-all/none, live count, and a one-line explainer of include vs exclude mode. Warn inline when kill-switch is on (mutual exclusivity). | v2rayNG's app picker exists but is dense; the differentiator is a *fast, searchable, explained* picker (Incy's "crowded" trap avoided). | M | `activity_app_picker.xml`; add search + sort + mode explainer. |
| P1-5 | **Rich, actionable ongoing notification.** Persistent connected notification with **elapsed time, live ↓/↑, server name**, and inline actions **Disconnect · Switch server · Pause 5 min**. Distinct disconnected/error notifications with a single tap-to-fix. | The notification is the app's second home screen for a VPN. Inline actions remove trips into the app. | S–M | `V2RayServiceManager` notification builder. |
| P1-6 | **Subscription health at a glance on Home.** A slim banner/chip under the hero: expiry countdown ("**expires in 6 days**"), traffic used/total ring, and — when the operator sends an `announce` header — a dismissible message. Turns red near expiry with a "Renew" affordance (routes to the Telegram bot / future payments). | Managed users care about *"am I about to be cut off"* more than protocol trivia; surfaces the meta bar's most urgent data one level up. Uses header parsing already designed in hidden-templates §2.4. | S | Home hero; reads `SubscriptionItem` quota/expiry + `announce`. |
| P1-7 | **Favourites & "Fastest" pseudo-server.** Star servers; add a synthetic **"Fastest / Auto"** entry that picks the lowest-ping reachable node (and, for balancer templates, just uses the operator's balancer). | "Fastest" is the single most-used entry in commercial apps; removes decision paralysis for non-technical users. | M | Server list + Home; ping cache; for CUSTOM/balancer templates defer to template. |

### P2 — depth, delight, and reach

| # | What | Why it helps | Effort | Where it fits |
|---|---|---|---|---|
| P2-1 | **Connection insight / stats sheet.** Pull up from Home: session duration, total ↓/↑, current protocol & route, ping sparkline, and a **"connection quality" pill** (good/unstable) from rolling probe latency. | Incy markets "real-time connection-quality monitoring"; a tasteful stats sheet satisfies power users without cluttering Home. | M | New bottom sheet from Home; sample from traffic stats + probes. |
| P2-2 | **First-class diagnostics & "Share debug report".** A guided "Having trouble?" flow: run reachability + DNS + a test fetch, then offer a **redacted** shareable report (never leaks locked configs/URLs per hidden-templates §3). | Cuts support-ticket back-and-forth dramatically; respects the locked-config threat model. | M | Extends `LogcatActivity`; redaction filter. |
| P2-3 | **On-demand / schedule & app-triggers (Happ auto-start parity+).** Auto-connect on boot, on specific app launch, or on a schedule; expose via **Tasker** (already present) and Quick Tile. | Happ has auto-start; going further (per-app, schedule) is a clear "more modern than Happ" beat. ([Happ](https://www.happ.su/main/dev-docs/app-management)) | M | Boot receiver + Settings → Automation; reuse `activity_tasker.xml`. |
| P2-4 | **Accessibility pass (WCAG-minded).** Real `contentDescription` on the connect ring and every icon-only control; state changes announced via `announceForAccessibility` ("Connected to Amsterdam"); 48dp targets (design-system already mandates); honour `fontScale` with `autoSize` bounds; **reduced-motion** respected for ring/sweep; TalkBack focus order Home→status→server→speed. | Accessibility is both an ethical and a market requirement in 2026, and censorship users skew broad demographics. Cheap to do early, expensive to retrofit. | M | Cross-cutting: `MainActivity`, all icon buttons, motion code. |
| P2-5 | **i18n depth for RU/EN/FA (+ RTL correctness).** Beyond translation: **RTL mirroring** of the meta bar and speed chips (design-system §6 notes `values-ar/fa` exist); locale-aware **number/byte/date formatting** (traffic "۱٫۲ گیگ", dates); Persian digit shaping option; **no truncation** of longer RU strings (they run ~30% longer — reserve space, avoid fixed-width chips). Add an in-app language switcher independent of system locale (many Iranian users run English-locale devices but want Farsi UI). | FA/RU are first-class here; getting RTL + number shaping right is what separates "translated" from "localized" and signals respect to the core audience. | M | `values-fa/`, `values-ru/`, `values-ar/`; `LocaleHelper`; formatting utils. |
| P2-6 | **Delightful empty & loading states (see §3).** Purpose-built empty states for: no subscription yet, subscription updating, all servers unreachable, search-no-results, offline. Each with an illustration-lite glyph, one sentence, and one action. | Empty states are first impressions and failure moments; generic blank screens read as "broken" in censored networks where blankness is ambiguous. | S–M | Server list, Home, search. |
| P2-7 | **Theme & personalization niceties.** Respect the design-system Blue/Mono + glass, and add: **per-connection-state accent** already covered; a **discreet / "stealth" app icon + name** alias (calculator-style) for at-risk users; Dynamic-Color opt-in on Android 12+ that still preserves brand blue as the connect accent. | The stealth icon is a genuine safety feature for the FA/RU audience; Material You opt-in feels current without diluting brand. | S–M | `activity-alias` icons; Settings → Appearance. |

### P3 — nice-to-have / long-tail

| # | What | Why it helps | Effort | Where it fits |
|---|---|---|---|---|
| P3-1 | **Wear OS / Android Auto tile** (connect/disconnect + status). | Reach + "premium ecosystem" signal. | L | Separate module. |
| P3-2 | **Per-server notes & auto-generated flags/labels** (region from address, protocol chip). | Faster scanning of long lists. | S | Server row; geoip lookup already available. |
| P3-3 | **"What changed" release sheet** on update (concise, dismissible, localized). | Keeps managed users informed of operator/app changes. | S | On version bump. |
| P3-4 | **Haptic & sound theme toggle** (off for stealth). | Personalization + accessibility + discretion. | S | Settings → Appearance. |
| P3-5 | **Backup/restore UX polish** (one-tap encrypted export, QR handoff to a new device) — excluding locked profiles per hidden-templates §3.5. | Device-migration is a known pain point. | M | `activity_backup.xml`. |

---

## 2. Signature moments — the 4 things that make it feel *special*

These are the deliberately-crafted, "screenshot-worthy" moments. Everything else can be tasteful and
restrained; **spend the delight budget here.**

1. **The Connect ring as a living object.** One continuous, physical motion language on the single most
   important control (extends design-system §5.1 with *interaction* detail):
   - *Tap to connect* → ring compresses ~4% then springs back (a "button that pushes back"), a **single
     crisp haptic tick**, and a **gradient sweep** begins travelling the ring (`#1E5FC7→#3B82F6`).
   - *Connecting* → sweep speed maps to real progress (slows if a stage stalls — honesty, not a fake
     spinner). Status text crossfades through the staged sub-states (P0-2).
   - *Connected* → sweep completes into a **full solid ring**, one **soft "success" double-tick haptic**,
     the glyph settles, and the ring adopts a slow **breathing glow** (2–3s cycle, tinted shadow). This is
     the payoff frame.
   - *Disconnect* → ring "exhales": glow fades, gradient drains out the way it came, one light tick.
   - All motion **100–300 ms** for transitions, breathing excepted; fully **disabled under reduced-motion**
     and power-save. ([Justinmind](https://www.justinmind.com/web-design/micro-interactions),
     [kolpolok](https://kolpolok.com/vpn-interface-ui-ux-tips/))

2. **"You're protected" — the reassurance beat.** The instant Connected lands, the hero briefly reveals
   the **exit context** ("Connected · Amsterdam · 42 ms") and a one-line, non-alarming reassurance that
   fades to the persistent status. For the censored-network audience this is the emotional core of the
   product: a calm, unambiguous *"it worked."* (Ambiguous blank screens are the enemy in DPI networks.)

3. **In-place server hot-swap.** Change server from Home without a jarring disconnect screen: the ring
   does a quick half-pulse, the server name cross-slides, ping re-resolves — the tunnel is rebuilt under a
   2-second "switching…" micro-state. Feels like changing a radio station, not rebooting a modem. (Beats
   Happ/Incy's tab round-trip and Mullvad's missing quick-connect.)

4. **The glass Settings reveal** (make the existing design-system §4 glass *earn its keep*). Opening
   Settings from the bottom bar slides a **frosted, brand-tinted glass sheet** up over a softly-blurred
   Home; group cards settle in with a subtle staggered fade (30–40 ms apart). It's the one place the app
   shows off — and it's off the connect path, so it never costs a connect frame. Auto-degrades on
   low-RAM/power-save (design-system §4 Tier C).

*(Optional 5th, audience-specific): the **stealth unlock** — long-press the disguised launcher icon or a
PIN reveals the real app. A safety feature that doubles as a memorable, "this app gets me" moment for
at-risk users.)*

---

## 3. Empty, loading & offline states — concrete specs

One glyph, one sentence, one action. Copy in §4. Never a raw blank screen.

| State | Trigger | Visual | Primary action |
|---|---|---|---|
| **No subscription yet** | Signed in, zero servers | Friendly glyph + "Setting up your access…" (auto-fetch) → if still empty, "No servers yet" | *Refresh* / *Contact support* (Telegram) |
| **Subscription updating** | Fetch in progress | Skeleton server rows (shimmer), meta bar shows spinner | (none; cancel available) |
| **All servers unreachable** | Every ping timed out | Muted glyph + honest line | *Retry all* + *Update subscription* + *Try fragment mode* |
| **Search no-results** | Filter yields nothing | Small glyph | *Clear filters* |
| **Offline (no network)** | No connectivity | Distinct from "servers down" | *Open Wi-Fi settings* |
| **First launch, not signed in** | Fresh install | Onboarding (P0-1) | *Continue with Telegram* |

Loading craft: **skeleton screens, not spinners**, for lists (perceived-performance win, §6). The connect
path uses the staged ring (§2), never a generic modal spinner.

---

## 4. Micro-interactions & copy (concrete)

### Micro-interactions
- **Haptics (use sparingly, respect the mute/stealth toggle):** connect tap = `CLOCK_TICK`/light;
  connected = a light "confirm" double-tick; error = `REJECT`/double buzz; toggle switches = tiny tick;
  pull-to-refresh release = tick. Never haptic-spam list scrolling.
- **Ping badges** animate value → colour, not a hard swap: number counts up, pill tints green/amber/red
  over ~200 ms (design-system §5.3 colours). A timed-out ping does a single subtle shake.
- **Copy/import success:** a brief inline check-mark morph on the button, not a toast, where possible.
- **Traffic counters** ease (don't jump) between samples so the numbers feel live but calm.
- **Pull-to-refresh** on Servers updates the subscription with the meta-bar spinner inline.
- **Long-press connect ring** = quick menu (Reconnect · Switch server · Disconnect) — power-user speed.
- **Reduced motion / power-save / low-RAM:** all of the above collapse to instant state changes.

### Copy — voice: calm, plain, trustworthy; never alarmist, never jargon on Home. (All strings localized RU/EN/FA.)
- Connect states: **"Tap to connect"** → **"Connecting…"** → (sub) **"Securing your connection…"** →
  **"Connected"**. Disconnected idle: **"Not connected"** (not "Disconnected", which reads as failure).
- Connected detail: **"Connected · {city} · {ms} ms"**. Reassurance beat: **"You're protected."**
- Kill switch card: title **"Block traffic if the VPN drops"**, summary **"Turn on Android's kill switch
  so nothing leaks when the tunnel is down. Note: can't be combined with per-app split tunnelling."**
- Untrusted Wi-Fi: **"Auto-connect on unknown Wi-Fi"** / summary **"We'll turn the VPN on when you join a
  network you haven't marked as trusted."**
- Expiry banner: **"Access expires in {n} days"** → at ≤3 days red: **"Your access ends {date} — renew to
  stay connected."** Action: **"Renew"**.
- Errors (see §5) lead with cause + fix, e.g. **"Couldn't reach this server. Your network may be blocking
  it."** → **"Try another server"**.
- Empty "all unreachable": **"We couldn't reach any server right now. This can happen on restricted
  networks."** → **"Retry"** / **"Try stealth (fragment) mode"**.
- Onboarding: S1 **"Private access, made simple."** S2 **"Your access is managed by your provider — we
  just keep the connection fast and secure."** S3 **"Sign in with Telegram to get started."**
- Never show raw protocol/core errors on Home. Keep `VMess`/`Reality`/exit codes in the diagnostics
  screen only.

---

## 5. Error handling & recovery model

A single sealed `ConnectError` taxonomy, each with **{plain cause, primary fix, secondary fix, is-retryable}**.
Home shows the friendly layer; Logcat/diagnostics keeps the raw truth for power users.

| Class | Likely cause | Primary fix | Secondary |
|---|---|---|---|
| `NoSubscription` | Signed in, no servers | Update subscription | Contact support (Telegram) |
| `AllUnreachable` | DPI/blocked IPs, dead nodes | Try another server / Fastest | Enable fragment/mux ("stealth mode") |
| `HandshakeTimeout` | Node up, tunnel blocked | Retry | Switch server; try fragment |
| `AuthExpired` | Token/subscription expired | Renew | Sign in again |
| `PermissionDenied` | VPN consent revoked | Grant VPN permission | — |
| `NoNetwork` | Phone offline | Open Wi-Fi settings | — |
| `CoreCrash` | Core failure | Restart connection | Share debug report (redacted) |

Principles: **auto-retry once silently** on transient drops (with the ring in a "reconnecting" micro-state)
before ever showing an error; **degrade, don't dead-end** — always offer at least one forward action;
**"stealth mode" as a recovery affordance** (toggle fragment/mux from the error sheet) directly serves the
DPI audience; and **never** leak locked-config internals in any error or report (hidden-templates §3).

---

## 6. Perceived performance & low-memory behaviour

Fast-feeling and light on RAM — critical on the mid/low-end Android hardware common in the target regions.

**Perceived performance**
- **Optimistic connect:** the ring animates to "Connecting" on the *same frame* as the tap, before the
  core actually starts — the UI never waits on IPC. Reconcile if start fails.
- **Skeleton screens** for server lists and the subscription meta bar instead of spinners; content
  swaps in without layout jump (reserve row heights).
- **Cache last-known state** (last server, last pings, traffic totals) and render it instantly on cold
  start, then refresh in the background — Home is never blank on launch.
- **Debounce ping tests**; batch and stagger them; cache results with a short TTL so re-opening the list
  is instant. Cancel in-flight pings when the user leaves the screen.
- **Preload nothing heavy on the connect path.** Glass/blur (design-system §4) is scoped to Settings and
  sheets, off Home — keep it that way; the connect screen stays solid for guaranteed 60fps.
- Motion budget **100–300 ms**; the only long-running animation (breathing ring) is a cheap alpha/scale
  loop, paused when the screen is off or app backgrounded.

**Low-memory & battery**
- Detect `ActivityManager.isLowRamDevice` **and** `PowerManager.isPowerSaveMode` → auto-drop glass to
  Tier B/C, disable breathing/sweep, reduce probe frequency (design-system §4 already specifies the glass
  tiers; extend the same detection to motion + polling).
- **Bounded RAM:** cap in-memory ping/stat history (ring buffers, not growing lists); avoid retaining
  bitmaps for flags — render vector/tinted glyphs.
- **Battery-aware automation:** the untrusted-Wi-Fi monitor (P1-3) uses `ConnectivityManager` callbacks,
  not polling; back off when screen-off.
- **Notification updates throttled** to ~1 Hz for live speed (no per-packet churn).
- **Cold-start budget:** keep `Application.onCreate` lean (defer non-critical init — auth refresh, sub
  sync — off the main thread) so first frame is fast even on low-end devices; Proton's 2025 headline win
  was literally *startup reliability*. ([Proton recap](https://protonvpn.com/blog/fall-winter-recap-2025-2026))

---

## 7. Suggested sequencing (so this lands without churn)

1. **Foundation:** P0-1 onboarding, P0-2 staged connect, P0-3 one-tap reconnect, P0-4 error model — these
   define the emotional spine and are prerequisites for the signature moments.
2. **Everyday polish:** P1-1 hot-swap, P1-2 tile/widget, P1-5 rich notification, P1-6 subscription health.
3. **Automation & reach:** P1-3 trusted Wi-Fi, P1-4 split-tunnel picker, P2-3 on-demand.
4. **Craft & inclusion:** signature-moment motion pass (§2), P2-4 accessibility, P2-5 i18n depth,
   P2-6 empty states, P2-7 stealth icon.
5. **Long-tail:** P3 items opportunistically.

---

### Sources
- Mullvad Android (kill switch, split tunnel, "empty" UI / no quick-connect) — https://mullvad.net/en/help/using-mullvad-vpn-on-android , https://cyberinsider.com/vpn/comparison/mullvad-vs-proton-vpn/
- Proton VPN 2025-26 recap (startup reliability) + kill switch (split-tunnel exclusivity) — https://protonvpn.com/blog/fall-winter-recap-2025-2026 , https://protonvpn.com/support/what-is-kill-switch
- IVPN trusted Wi-Fi networks + Android app (tile) — https://www.ivpn.net/blog/new-trusted-wi-fi-networks-feature-for-ivpn-apps/ , https://www.ivpn.net/en/apps-android/
- Android VPN service / always-on / kill switch — https://developer.android.com/develop/connectivity/vpn , https://nimbusvpn.tech/en/guides/always-on-vpn-kill-switch-android-2026/
- Happ app management (auto-start, routing) — https://www.happ.su/main/dev-docs/app-management
- NekoBox (clean UI, modern v2rayNG alternative) — https://grokipedia.com/page/NekoBox_for_Android
- Amnezia split tunnelling (by app / by IP) — https://docs.amnezia.org/documentation/instructions/vpn-split-tunneling/
- VPN UX & micro-interaction craft — https://kolpolok.com/vpn-interface-ui-ux-tips/ , https://www.justinmind.com/web-design/micro-interactions
- Iran censorship / DPI context (audience) — https://www.rferl.org/a/iran-internet-censorship-x-twitter-vpn/33602370.html , https://vpnoverview.com/unblocking/censorship/internet-censorship-iran/

*Advisory document. Sources cited inline. No application code modified.*
