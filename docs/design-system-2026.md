# departament VPN — Design System & Redesign Spec (2026)

**Product:** departament VPN — an Android VPN/proxy client forked from v2rayNG
(Kotlin, XML Material 3 views, package `com.v2ray.ang`, app label `departament`).
**Brand:** the wordmark "**departament VPN**" set in blue (~`#1E5FC7`) on a
cream/white ground.
**Design goal:** a premium, current-for-2026 **white-blue** default theme (explicitly
*not* the dated flat "Happ blue"), a switchable **black-&-white (mono)** theme, and an
optional **glass / translucent (iOS "liquid glass")** aesthetic for the Settings area —
while keeping the familiar **structure of Happ and Incy** so users feel at home.

This document is design-only. No app code is changed by it. Hex values, dimens and
component sizes below are meant to be copied into `res/` by the engineer.

---

## 1. Reference research — Happ & Incy

### Sources studied
- Happ — Proxy Utility, Google Play: https://play.google.com/store/apps/details?id=com.happproxy
- Happ dev docs, "App management": https://www.happ.su/main/dev-docs/app-management
- Happ site / FAQ: https://www.happ.su/main
- Happ iOS listing (same UI language): https://apps.apple.com/us/app/happ-proxy-utility/id6504287215
- Happ Android repo: https://github.com/Happ-proxy/happ-android
- Incy, Google Play: https://play.google.com/store/apps/details?id=llc.itdev.incy
- Incy on APKPure (8 screenshots): https://apkpure.com/incy/llc.itdev.incy
- Incy dev org / releases: https://github.com/INCY-DEV/incy-platforms

### What the sources tell us
Both apps are **Xray/sing-box front-ends** aimed at users who bring their own servers
(subscription links / QR). Neither sells VPN service, so the UI is about *managing and
connecting to your own configs*, exactly like this fork.

**Happ (structure to emulate).** Clean, modern, single-primary-surface app:
- A **connect surface** is the home. One dominant connect control plus the currently
  selected server and its ping.
- **Servers are grouped by subscription.** A **subscription bar** shows profile name
  (≤25 chars), **used traffic on the left / total on the right**, an **expiration date**,
  and small blue **support / website** icons. This is the single most recognisable Happ
  element and we reproduce it faithfully.
- Each **server row** can show a custom description (≤30 chars, replacing the raw protocol
  name like "VMess"), and a **ping shown as either a time value or an icon**.
- **Sorting** = none / ping / alphabet; **filtering** by connection type (Wi-Fi-only /
  mobile-only).
- Recent Happ work: "**redesign of the subscription-actions bottom sheet**", adjustable
  **font size in Settings**, and an **On-Demand / Always-On** tunnel section. Confirms the
  direction: **bottom sheets for per-item actions**, and settings that are grouped and
  legible.

**Incy (structure to emulate).** Marketed as a **"modern, smooth"** native Android client
(Android 7+, v3.3.x, July 2026). Feature surface: encrypted `VpnService` tunnel, import by
link/QR, **per-app routing**, **kill switch**, **Always-On**, **real-time connection-quality
monitoring**, and **detailed traffic statistics**. The one recurring criticism in reviews —
"**font is very small and it's a bit crowded**" — is the trap to avoid: we spend the premium
budget on **whitespace, type scale and touch targets**, not density.

### Common structure across both (the pattern users expect)
1. **Home = Connect.** Big status/connect control, selected server + latency, live up/down
   speed once connected.
2. **Servers grouped under subscriptions**, with the subscription meta bar (name, traffic
   used/total, expiry).
3. **Per-item actions via bottom sheet** (edit, ping, share/QR, delete, duplicate).
4. **Settings as a grouped hub** that also hosts per-app proxy, routing, DNS, kill-switch/
   Always-On, and appearance.
5. **Import flow** front-and-centre (paste link, scan QR, update subscription).

### What departament adopts vs. improves
| Adopt from Happ/Incy | departament's improvement for 2026 |
|---|---|
| Hero connect screen | Keep the current hero card; make the connect button a gradient ring with animated states (§5). |
| Subscription meta bar (used/total, expiry) | Reproduce exactly; add a thin traffic progress line. |
| Server-grouped-by-subscription tabs | Keep the scrollable `TabLayout` + `ViewPager2`. |
| Per-item bottom sheets | Standardise a single `ModalBottomSheet` spec (§5). |
| Grouped settings hub | Reorganise the drawer's flat list into titled setting *cards* (§5), add glass finish (§4). |
| Modern flat blue | Replace flat mid-blue with a **brighter indigo-blue + soft tints + gradient + generous whitespace** so it never reads like "old Happ blue". |
| "Crowded / tiny font" (Incy) | Larger base type, 48dp min targets, one idea per row. |

---

## 2. Navigation & screen inventory

### Recommendation: **Bottom navigation bar** (3 primary tabs) + a Settings hub

**Pick: `BottomNavigationView` with three destinations — Home · Servers · Settings.**
The current build uses a **left drawer** (`DrawerLayout` + `NavigationView`, see
`activity_main.xml` / `menu_drawer.xml`). Migrate the *primary* destinations to a bottom bar
and demote everything else into the Settings hub.

**Why bottom nav for 2026 (justification):**
- **Thumb-reachability.** On today's 6.1–6.9" phones the top-left drawer hamburger is the
  hardest pixel to reach one-handed; a bottom bar keeps the three most-used destinations on
  the thumb arc.
- **Discoverability.** A drawer hides navigation behind a gesture/tap; peer apps users
  compare us to (Proton, Mullvad, NordVPN, and Incy's "modern" native shell) all surface
  primary destinations persistently.
- **Matches the mental model.** Happ/Incy are effectively *Connect / Servers / Settings*
  apps — three tabs map 1:1, no more.
- **Material 3 guidance.** Bottom nav (or the newer `NavigationBar`) is M3's recommended
  pattern for 3–5 top-level destinations; a `NavigationRail` is the automatic
  `sw600dp`/tablet upgrade (§6), which a drawer does not give you for free.

Keep a drawer *only* if the client insists on parity with Happ's exact chrome; in that case
put the bottom bar on Home and Servers and leave secondary items in the drawer. The
recommended target is bottom-bar-primary.

**Bottom bar tabs**
1. **Home** — connect hero, status, selected server, live speed.
2. **Servers** — subscription tabs + server list (the current `TabLayout`+`ViewPager2`).
3. **Settings** — the grouped settings hub (entry point to all secondary screens below).

> Import (paste link / scan QR / update subs) is a **FAB or top-app-bar action** on the
> Servers tab, not a 4th nav item — it's an action, not a place.

### Full screen inventory
| # | Screen | Existing activity/layout | Purpose | Nav placement |
|---|---|---|---|---|
| 1 | **Home / Connect** | `activity_main.xml` (hero card) | Connect toggle, status, timer, selected server + ping, live up/down speed. | Bottom tab 1 |
| 2 | **Server list** | `fragment_group_server.xml`, tabs in `activity_main` | Servers grouped by subscription; sort/filter; ping; select. | Bottom tab 2 |
| 3 | **Server editors** | `activity_server_*` (vmess/vless/trojan/ss/socks/wireguard/hysteria2/custom/chain) | Add/edit a config per protocol. | Push from Servers |
| 4 | **Subscription settings** | `activity_sub_setting.xml`, `item_recycler_sub_setting.xml` | List/add/edit subscriptions; enable/auto-update; traffic & expiry. | Settings hub |
| 5 | **Subscription edit** | `activity_sub_edit.xml` | Add/edit one subscription (URL, name, filters). | Push from #4 |
| 6 | **Per-app proxy** | `activity_app_picker.xml` (`app-management` parity) | Choose which apps route through the tunnel. | Settings hub |
| 7 | **Routing settings** | `activity_routing_setting.xml`, `activity_routing_edit.xml` | Rule sets, domain/IP rules, bypass. | Settings hub |
| 8 | **Bypass / geo list** | `activity_bypass_list.xml` | Managed bypass entries. | Push from Routing |
| 9 | **Settings (main)** | `activity_settings.xml` (PreferenceScreen) | Appearance/theme, tunnel (kill-switch, Always-On/On-Demand), DNS, mux, fragment, font size. | Bottom tab 3 |
| 10 | **User assets** | `activity_user_asset*.xml` | geoip/geosite files & custom rule assets. | Settings hub |
| 11 | **Backup & restore** | `activity_backup.xml`, `dialog_webdav.xml` | Local + WebDAV backup. | Settings hub |
| 12 | **Check for update** | `activity_check_update.xml` | App update check. | Settings hub |
| 13 | **Logcat** | `activity_logcat.xml` | Live log / diagnostics. | Settings hub (Advanced) |
| 14 | **About** | `activity_about.xml` | Version, licences, links, brand wordmark. | Settings hub |
| 15 | **Tasker plugin** | `activity_tasker.xml` | Automation entry (unchanged). | External |

**Settings hub (screen 9) layout** — grouped titled cards, top to bottom:
*Appearance* (Theme: Blue / Mono, Light-Dark-System, Glass on/off, Font size) →
*Connection* (Per-app proxy, Routing, DNS, Kill-switch, Always-On / On-Demand, Fragment/Mux) →
*Subscriptions & assets* (Subscriptions, User assets) →
*Data* (Backup & restore) →
*Advanced* (Logcat, Check for update) → *About*.

---

## 3. Design system

### 3.1 Spacing scale (4dp base)
Extend `res/values/dimens.xml` (today it only defines 4/8/16 + a few heights). Use a named
4-pt scale so layouts stop hard-coding `12dp`/`20dp`/`24dp` (the hero card currently does):

| Token | dp | Use |
|---|---|---|
| `space_2` | 2 | hairline gaps, icon-to-text micro |
| `space_4` | 4 | dense chip padding |
| `space_8` | 8 | intra-component |
| `space_12` | 12 | card inner padding (compact) |
| `space_16` | 16 | **default screen gutter** |
| `space_20` | 20 | hero inner padding |
| `space_24` | 24 | section spacing |
| `space_32` | 32 | hero vertical rhythm |
| `space_48` | 48 | large empty-state spacing |

Screen horizontal gutter = **16dp** phones, **24dp** `sw600dp`. Vertical gap between setting
cards = **12dp**.

### 3.2 Corner radii
| Token | dp | Use |
|---|---|---|
| `radius_xs` | 8 | chips, small buttons, ping badge |
| `radius_sm` | 12 | list rows, text fields |
| `radius_md` | 16 | **default card** (server card, setting card) |
| `radius_lg` | 24 | hero card, bottom sheet top corners |
| `radius_xl` | 28 | large hero / prominent modals (matches current 28dp hero) |
| `radius_pill` | 999 | connect ring, filter chips, primary CTA |

2026 feel = **soft, generous** radii. Standardise on 16dp cards (the current layout mixes
28dp hero with implicit 4dp progress bar; unify).

### 3.3 Elevation & shadow
Move away from Material's default grey drop shadows toward a **soft, tinted, low-contrast**
approach (premium/"glass" look):
- **Cards:** `cardElevation = 0dp` + **1dp hairline stroke** in `colorOutlineVariant`
  (already used on the hero card). This is the primary separation device in light mode.
- **Raised / floating (FAB, connected connect button, bottom sheet):** true elevation
  **6dp**, but soften with a brand-tinted ambient shadow via `outlineAmbientShadowColor` /
  `outlineSpotShadowColor` set to `#1E5FC7` at low alpha on API 28+.
- **Bottom nav / app bar:** `elevation = 0dp`; separate from content with a 1dp top hairline,
  not a shadow (current app bar already uses `app:elevation="0dp"`).
- **Dark & mono-dark:** shadows are near-invisible; use **surface elevation tint** (lighter
  surface containers) instead — see the surface ramp in §3.6.

### 3.4 Typography scale
Keep the platform font (Roboto / system) to stay light; the **wordmark** "departament VPN"
in the toolbar and About uses the brand blue and can ship a custom brand font as an asset if
provided. Map to M3 type roles (base sizes; respect `fontScale`):

| Role | Size / weight | Use |
|---|---|---|
| Display (brand) | 28sp / 700 | About header wordmark |
| Toolbar title | 20sp / 700, letter-spacing 0 | `ToolbarBrandTitle` (exists) |
| Headline | 22sp / 600 | screen titles, empty states |
| Title / status | 19sp / 700 | connect status ("Connected") — matches current hero |
| Body | 15sp / 400 | rows, descriptions (bump from Incy's tiny text) |
| Label | 13sp / 600 | speed chips, tab labels, buttons |
| Caption / mono | 13sp / 400, `monospace` | timer, ping ms, log |

Rule: **body text never below 14sp**; secondary/caption never below 12sp. Font-size
preference (Happ parity) scales body/label roles only.

### 3.5 Iconography
- **24dp** line icons on 48dp targets; 20dp inline (server row, speed chip — matches
  current). Existing `ic_*_24dp` set is consistent; keep the 24dp line style.
- Stroke ~2dp, rounded caps/joins to match the soft radii.
- **Tinting:** icons use `?attr/colorOnSurfaceVariant` at rest, `?attr/colorPrimary` when
  active/selected. In **mono** theme, `colorPrimary` collapses to near-black/near-white, so
  icons stay tonal automatically — no per-icon overrides needed.
- Connect glyph: `ic_power_settings` (in use) centred in the ring.

### 3.6 Colour roles — concrete hex

The existing `values/colors.xml` and `values-night/colors.xml` are a solid first pass. Below
is the **refined** palette. Where a value differs from what's in the repo it's marked
**(change)**; unmarked values confirm the current value is good.

The design keeps the **logo blue `#1E5FC7` as the brand anchor** but makes the *interactive*
primary a touch brighter and pairs it with cleaner, cooler neutrals and gradients so it never
reads as "old Happ blue".

#### Blue theme — LIGHT (`values/colors.xml`)
| Role | Hex | Note |
|---|---|---|
| `brand_blue` | `#1E5FC7` | logo anchor — keep |
| `md_theme_primary` | `#1E5FC7` | keep (logo fidelity) |
| **primary gradient end** | `#3B82F6` | **(add)** for connect ring / CTA gradient |
| `md_theme_onPrimary` | `#FFFFFF` | |
| `md_theme_primaryContainer` | `#DBE7FF` | (change from `#D8E4FF`) softer, cooler |
| `md_theme_onPrimaryContainer` | `#001A43` | |
| `md_theme_secondary` | `#3B6FD0` | |
| `md_theme_secondaryContainer` | `#DCE6FF` | |
| `md_theme_tertiary` (connected) | `#12B76A` | keep green success |
| `md_theme_tertiaryContainer` | `#A8F0CE` | |
| `md_theme_error` | `#BA1A1A` | |
| `md_theme_background` | `#F5F8FE` | (change from `#F4F7FC`) cleaner cool white |
| `md_theme_onBackground` | `#111826` | |
| `md_theme_surface` | `#FFFFFF` | card white |
| `surfaceContainerLow` | `#F7FAFF` | **(add)** page-tint layer |
| `surfaceContainer` | `#EEF3FC` | **(add)** grouped setting bg |
| `surfaceContainerHigh` | `#E7EEF9` | **(add)** raised sheet |
| `md_theme_onSurface` | `#111826` | |
| `md_theme_surfaceVariant` | `#E9EEF7` | |
| `md_theme_onSurfaceVariant` | `#54607A` | |
| `md_theme_outline` | `#C3CCDC` | |
| `md_theme_outlineVariant` | `#DCE3EF` | hairline strokes |
| `colorPing` (good ms) | `#12B76A` | |
| `colorPingRed` (bad ms) | `#E5484D` | |
| `color_fab_inactive` | `#9AA6B8` | disconnected connect button |

> **Fix the surface-container tokens.** `themes.xml` currently maps **every**
> `colorSurfaceContainer*` to plain `md_theme_surface` (flat white). Point the ramp at the new
> `surfaceContainerLow/High/…` values above so glass layering, bottom sheets and elevated
> settings read as distinct planes.

#### Blue theme — DARK (`values-night/colors.xml`)
| Role | Hex | Note |
|---|---|---|
| `md_theme_primary` | `#7DA8FF` | (change from `#6FA0FF`) brighter on dark navy |
| `md_theme_onPrimary` | `#00246B` | |
| `md_theme_primaryContainer` | `#123A82` | |
| `md_theme_background` | `#0B1220` | deep navy — keep |
| `md_theme_surface` | `#131C2C` | |
| `surfaceContainerLow` | `#111A29` | **(add)** |
| `surfaceContainer` | `#182234` | **(add)** |
| `surfaceContainerHigh` | `#1E2A40` | **(add)** raised/glass |
| `md_theme_onSurface` | `#E6ECF5` | |
| `md_theme_onSurfaceVariant` | `#A9B4C6` | |
| `md_theme_outline` | `#3A4761` | |
| `md_theme_outlineVariant` | `#25303F` | |
| `md_theme_tertiary` (connected) | `#3DDC97` | |
| `colorPingRed` | `#FF6369` | |

#### Mono theme — LIGHT (`mono_*` in `values/colors.xml`)
Applied via `ThemeOverlay.Mono`. Pure black-on-white, one restrained tonal accent.
| Role | Hex | Note |
|---|---|---|
| `mono_primary` | `#111214` | near-black |
| `mono_onPrimary` | `#FFFFFF` | |
| `mono_primaryContainer` | `#EDEDEF` | (change from `#E6E6E8`) lighter chip |
| `mono_background` | `#FFFFFF` | |
| `mono_surface` | `#FFFFFF` | |
| `mono_surfaceContainer` | `#F4F4F5` | **(add)** grouped bg |
| `mono_surfaceVariant` | `#F1F1F2` | |
| `mono_onSurfaceVariant` | `#5A5A5E` | |
| `mono_outline` | `#D2D2D6` | |
| `mono_outlineVariant` | `#E6E6E8` | |
| `mono_connected` | `#111214` | success shown by weight/icon, not colour |

#### Mono theme — DARK (`mono_*` in `values-night/colors.xml`)
| Role | Hex | Note |
|---|---|---|
| `mono_primary` | `#FFFFFF` | |
| `mono_onPrimary` | `#111214` | |
| `mono_background` | `#000000` | true black (OLED) |
| `mono_surface` | `#121214` | |
| `mono_surfaceContainer` | `#1B1B1D` | **(add)** |
| `mono_surfaceVariant` | `#1E1E20` | |
| `mono_onSurfaceVariant` | `#B0B0B4` | |
| `mono_outline` | `#38383C` | |
| `mono_outlineVariant` | `#28282C` | |

**Mono semantic exceptions.** Even in mono, keep **red `#E5484D` / dark `#FF6369`** for
destructive actions and failed pings, and a subtle green tick for "connected" — accessibility
beats purity. Everything else is greyscale.

**Contrast.** All on-/container pairs above meet WCAG AA (≥4.5:1 body, ≥3:1 large/icon).
Notably `onSurfaceVariant` on `surface` ≈ 5.5:1 in both light themes.

---

## 4. Glass / translucent Settings ("liquid glass" on Android)

Goal: give the Settings hub (and its bottom sheets/dialogs) an iOS-liquid-glass feel —
**translucent, blurred, layered** — using only Android-feasible techniques, with graceful
fallbacks. Ship it behind an **"Glass surfaces" toggle** in Appearance (default **on** for
API 31+, **off** below).

### The layer model
A glass card is three stacked planes:
1. **Backdrop** — a soft brand gradient behind the whole Settings screen
   (`#F5F8FE → #DBE7FF` light; `#0B1220 → #101A2E` dark). This is what the blur samples, so
   the glass has something to refract.
2. **Blur layer** — a real-time blur of that backdrop, clipped to the card's rounded rect.
3. **Tint + border** — a translucent surface fill + 1dp light border + faint top highlight.

### Tier A — Android 12+ (API 31), real blur
- **Card / sheet blur:** apply `RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)`
  to a **backdrop snapshot view** positioned behind the card (RenderEffect blurs the view it's
  set on, so blur a *copy of the backdrop*, not the card content). Clip with the card's
  `radius_lg` outline.
- **Dialogs & bottom sheets (blur-behind):** gate on
  `windowManager.isCrossWindowBlurEnabled`, then set
  `LayoutParams.flags |= FLAG_BLUR_BEHIND` and `setBlurBehindRadius(48)` on the window; also
  set `dimAmount ≈ 0.2f`. This blurs everything *behind* the sheet — the closest thing Android
  has to iOS sheet material.
- **Tint over blur:** fill = `colorSurface @ 70–78% alpha`
  (light `#FFFFFF` @ 72%, dark `#131C2C` @ 66%, mono-dark `#121214` @ 60%).
- **Border/highlight:** 1dp stroke `#FFFFFF @ 45%` (light) / `#FFFFFF @ 12%` (dark) on the top
  edge to fake the specular rim.

### Tier B — API 26–30, no live blur
No cheap real-time blur. Simulate:
- Semi-opaque surface (`colorSurface @ 88–92% alpha`) + the 1dp light border + a very soft
  brand-tinted elevation shadow. Reads as "frosted panel", not clear glass.
- Optional: a **pre-blurred static** version of the gradient backdrop (a blurred PNG/drawable)
  behind the cards — no per-frame cost, still gives depth.

### Tier C — very old / low-RAM / battery-saver
- Fully opaque `colorSurfaceContainer` cards + hairline border. No transparency. Detect via
  `ActivityManager.isLowRamDevice` and `PowerManager.isPowerSaveMode` and drop to this tier
  automatically. Never let glass cost frames on the connect path.

### Component specs (glass)
| Property | Value |
|---|---|
| Blur radius (cards) | 24px |
| Blur radius (sheet blur-behind) | 48px |
| Corner radius | 24dp (cards), 24dp top (sheets) |
| Tint alpha | 60–78% depending on theme (above) |
| Border | 1dp, white @ 45% (light) / 12% (dark) |
| Scrim/dim behind sheet | 0.2 |
| Content padding | 20dp |
| Disable when | `isLowRamDevice` ∥ `isPowerSaveMode` ∥ API < 31 (→ Tier B/C) |

> Scope glass to **Settings, bottom sheets and dialogs only**. The **Home/connect** screen and
> the **server list** stay solid for performance and glanceability. Mono theme uses glass too,
> but with neutral tint (no brand gradient — use a grey `#F4F4F5 → #FFFFFF` / dark
> `#000 → #1B1B1D` backdrop).

---

## 5. Component specs

### 5.1 Connect button + ring (Home hero)
Current: 184dp `FrameLayout` → `bg_connect_ring` behind a 140dp circular `MaterialCardView`,
60dp glyph, 6dp elevation. Refine:
| State | Fill | Ring | Glyph | Motion |
|---|---|---|---|---|
| Disconnected | `color_fab_inactive` `#9AA6B8` | thin static `outlineVariant` | power icon, white | idle |
| Connecting | brand gradient `#1E5FC7→#3B82F6` | **animated sweep** on ring | power icon, white | ring rotates; button pulse 0.98–1.0 |
| Connected | brand gradient (or green ring accent) | solid ring, subtle glow (tinted shadow) | power icon, white | soft breathing pulse |
| Error | surface + `error` stroke | red ring | error glyph | single shake |
- Diameter: **140dp** button / **184dp** ring (keep). Min touch 48dp satisfied comfortably.
- Ripple `colorWhite`; content description toggles Connect/Disconnect.
- Connected = the **only** place brand gradient + real elevation appear on Home → draws the eye.

### 5.2 Status area (below button)
- **Status label** 19sp/700 `onSurface` — "Not connected" / "Connecting…" / "Connected".
- **Timer** 13sp monospace `onSurfaceVariant`, visible only when connected (current behaviour).
- **Selected-server row**: 20dp icon + name (15sp/700, ellipsised) + **ping/test state**
  (12sp). Tap = run latency test (keep). This is the Happ "selected server + ping" pattern.
- **Live speed row**: two `bg_speed_chip` pills, ↓ primary / ↑ tertiary, value 13sp/700
  (keep). Hidden until connected.

### 5.3 Server card (list row)
Replace dense rows with a **16dp-radius card**, 1dp `outlineVariant` stroke, `space_12`
padding, min height **64dp**:
```
[ flag/protocol ]  Name / description (15sp/600)          [ ping badge ]
  20dp icon        Sub-label: protocol · address (12sp,   [ 42 ms | ●  ]
                   onSurfaceVariant)                         [ ⋮ ]
```
- **Ping badge**: pill, 8dp radius; green `colorPing` (<150ms) / amber / red `colorPingRed`
  (timeout). Supports Happ's "time OR icon" via a setting.
- **Selected** state: 1.5dp `colorPrimary` stroke + `primaryContainer @ 40%` fill.
- **Overflow (⋮)** → bottom sheet: Test ping, Edit, Share / QR, Duplicate, Move, Delete.
- Sort (none/ping/alphabet) + filter (Wi-Fi/mobile) live in the tab-bar overflow — Happ parity.

### 5.4 Subscription tab (Servers header)
- Scrollable `TabLayout` (keep `activity_main` tabs), label 13sp/600, indicator 3dp
  `colorPrimary`, `tabIndicatorFullWidth=false` (keep).
- **Subscription meta bar** pinned under the tabs (the signature Happ element):
```
Profile name (≤25)                              [🌐] [🛟]
▓▓▓▓▓▓░░░░░░  used 12.4 GB / 50 GB      expires 2026-08-01
```
  - Traffic progress: 4dp track, `colorPrimary` fill, `surfaceVariant` track.
  - Left value = used (up+down), right = total; expiry right-aligned (12sp `onSurfaceVariant`).
  - 🌐 website / 🛟 support = 20dp blue icon buttons; grey when unset.

### 5.5 Settings row / card
- Rows live inside **grouped glass cards** (§4) with a group title (13sp/600 `colorPrimary`
  or `onSurfaceVariant`).
- Row: 24dp leading icon + title (15sp) + optional summary (13sp `onSurfaceVariant`) +
  trailing control (`MaterialSwitch`, value text, or chevron). Min height **56dp**.
- Switch uses `BrandedSwitch` (`colorPrimary = color_fab_active`) — already defined.
- Dividers **inside** a card = 1dp `outlineVariant` inset to text start; **between** cards =
  12dp gap, no divider.

### 5.6 Dialogs & bottom sheets
- **Modal bottom sheet** = the default for per-item actions and pickers. 24dp top radius,
  drag handle, glass material on API 31+ (blur-behind), `space_20` padding, 48dp action rows.
- **Alert dialog** = `MaterialAlertDialog`, 24dp radius, brand `colorPrimary` on the confirm
  text button; destructive confirm uses `colorError`.
- **Input dialogs** (sub URL, rename): 12dp text field, helper/error text, primary filled
  button. On API 31+ enable window blur-behind + 0.2 dim.

---

## 6. Responsiveness — all phones + tablet, iOS parity note

### Density & size buckets
- Keep **all dimensions in `dimens.xml`** (spacing, connect diameter, gutters) and provide
  buckets. Repo already has `values-sw360dp-v13`; add:
  - `values-sw360dp` (small phones): connect ring 156dp, button 120dp, gutter 12dp.
  - default (`values/`, ~sw360–599): ring 184dp / button 140dp / gutter 16dp (current).
  - `values-sw600dp` (large phones landscape, small tablets): gutter 24dp, cards max-width
    **560dp centred**, ring 200dp.
- Type in **sp**, spacing in **dp** — never px. Respect user font scale; cap runaway scaling
  on the connect status with `autoSizeText` bounds if needed.

### Layout strategy
- Prefer **`ConstraintLayout`** with **percent guidelines** for the hero (centre the ring on a
  50% horizontal guideline) so it scales from 5" to 7" without magic numbers. The current
  hero uses nested `LinearLayout` + fixed `184dp`/`140dp`; move those two sizes to dimens and
  keep the rest fluid.
- Server list & settings: `RecyclerView` with cards `match_parent` up to a **560dp max width**
  wrapper on wide screens (avoids full-bleed rows on tablets).
- **`sw600dp` → two-pane / `NavigationRail`.** Auto-swap the bottom bar for a left
  `NavigationRail`, and show **list + detail side-by-side** (server list left, connect/detail
  right; or settings list left, page right). One codebase, resource-qualified layouts.
- Landscape phone: hero switches to a **horizontal** arrangement (ring left, status/speed
  right) via `layout-land`.
- **Insets:** keep `fitsSystemWindows`/edge-to-edge; apply `WindowInsets` padding to the
  bottom bar and sheets so gesture nav and cutouts are respected on every device.
- **RTL:** Arabic/Persian locales already present (`values-ar`, `values-fa`, `bqi`) — use
  `start/end`, never `left/right`, and mirror the subscription meta bar.

### Future iOS parity
When an iOS version follows, the mapping is 1:1 because the structure is intentionally
platform-neutral: **bottom nav → `TabView`**; hero connect → SwiftUI `ZStack` with the same
ring/gradient; setting cards → grouped `List` with **native `.ultraThinMaterial`** (iOS gives
liquid glass for free — Android's §4 tiers are the effortful side). Keep the **token names**
(spacing, radii, colour roles) identical across platforms so both consume one design source of
truth; only the material implementation differs.

---

## 7. Prioritized screen-by-screen redesign checklist

**P0 — brand & foundation (do first, unblocks everything)**
1. Land the refined colour tokens from §3.6 into `values/colors.xml` &
   `values-night/colors.xml` (add `surfaceContainer*`, gradient end, mono container tokens).
2. **Fix `themes.xml`**: map `colorSurfaceContainer*` to the new distinct surface values (not
   flat `md_theme_surface`), so layering/glass work.
3. Expand `dimens.xml` to the §3.1 spacing scale + §3.2 radii + connect sizes; add
   `values-sw360dp` and `values-sw600dp` buckets.
4. Confirm the theme switch (Blue ↔ Mono via `ThemeOverlay.Mono`) and Light/Dark/System all
   render with the new tokens; verify wordmark blue in toolbar + About.

**P1 — primary surfaces users touch every session**
5. **Home / Connect (`activity_main`):** move `184dp`/`140dp` to dimens; add gradient +
   animated ring states (§5.1); tidy status/speed spacing to the 4-pt scale.
6. **Navigation migration:** introduce `BottomNavigationView` (Home · Servers · Settings);
   demote drawer items into the Settings hub; add `NavigationRail` for `sw600dp`.
7. **Server list + subscription meta bar:** rebuild rows as 16dp cards (§5.3); add the Happ
   subscription meta bar with traffic progress + expiry (§5.4); wire sort/filter overflow.
8. **Per-item bottom sheet** standardised (§5.6) for server + subscription actions.

**P2 — settings & the glass moment**
9. **Settings hub (`activity_settings`):** regroup into titled cards (§2); apply glass Tier
   A/B/C (§4) behind the "Glass surfaces" toggle; branded switches.
10. **Bottom sheets & dialogs:** enable API 31+ blur-behind + dim; 24dp radii; branded
    confirm/destructive colours.
11. **Import flow:** promote paste-link / scan-QR / update-sub to a FAB + top-bar action on
    Servers.

**P3 — secondary screens (visual pass to match the system)**
12. Per-app proxy, Routing/bypass, User assets, Backup/WebDAV, Check-update, Logcat, About —
    reskin to grouped cards, 56dp rows, correct tokens; About shows brand wordmark + version.
13. Server editors (`activity_server_*`): consistent 12dp fields, section headers, sticky
    save.

**P4 — polish & scale**
14. Landscape hero (`layout-land`), two-pane `sw600dp` layouts, RTL mirroring audit, inset/
    edge-to-edge pass, font-scale stress test, low-RAM/power-save glass fallback verification.
15. Motion polish: connect state transitions, tab/ping animations, reduced-motion respect.

---

### Appendix — files this spec touches (for the engineer)
- `V2rayNG/app/src/main/res/values/colors.xml` · `values-night/colors.xml` — §3.6 tokens
- `values/themes.xml` · `values-night/themes.xml` — surface-container mapping, glass styles
- `values/styles.xml` — `ToolbarBrandTitle`, `BrandedSwitch`, tab/type styles
- `values/dimens.xml` (+ new `values-sw360dp/`, `values-sw600dp/`) — spacing/radii/sizes
- `layout/activity_main.xml`, `fragment_group_server.xml`, `item_recycler_main.xml` — Home + list
- `layout/activity_settings.xml`, `item_recycler_sub_setting.xml` — settings + subs
- `menu/menu_drawer.xml` → new `menu/menu_bottom_nav.xml` — navigation migration

*Design-only document. Sources cited inline in §1. No application code modified.*
