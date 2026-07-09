# departament VPN — "Incy‑style" Settings Screen Design

**Status:** Design only. **No app code is changed by this document.**
**App:** v2rayNG / Xray‑core fork, package `com.v2ray.ang`, Kotlin + AndroidX `Preference`, label `departament`.
**Goal:** rebuild the current flat `PreferenceFragmentCompat` (`res/xml/pref_settings.xml` + `ui/SettingsActivity.kt`)
into the **grouped‑card, colored‑rounded‑icon‑tile** layout the owner likes from Incy — *without* heavy
libraries and without throwing away the existing MMKV‑backed preference plumbing.

**Read‑first / coordinate (do not duplicate these):**
- `docs/design-system-2026.md` — tokens (spacing `space_*`, radii `radius_*`, colors, §5.5 "Settings row / card",
  glass §4). This doc consumes those tokens; it does not redefine them.
- `docs/circumvention-settings-design.md` — the **Bypass / Anti‑censorship** screen (`res/xml/pref_bypass.xml`).
  Incy's **"Соединение · Configure"** and **"Туннель · Configure"** rows map onto that screen (see §3, ТУННЕЛЬ/СОЕДИНЕНИЕ).
- `docs/memory-panel-design.md` — the RAM panel + `MemoryStatsManager`. Incy's **"Memory monitoring"** toggle drives
  that panel's *home‑card* mount (see §3, ПРОИЗВОДИТЕЛЬНОСТЬ).
- `docs/ping-methods-design.md` / `docs/review-03-ping-methods.md` — the `PREF_PING_METHOD` we just added = Incy's
  "Ping settings · HTTP GET".

---

## 0. Did we find the Incy source? — No (stated honestly, with citations)

The **Incy Android client is closed‑source.** The `INCY-DEV` GitHub org publishes only four public repos, none of
which contain the Android app code:

| Repo | What it is | Use to us |
|---|---|---|
| [`INCY-DEV/incy-platforms`](https://github.com/INCY-DEV/incy-platforms) | Downloads + release notes (677★) | Feature list / version facts only |
| [`INCY-DEV/incy-icons`](https://github.com/INCY-DEV/incy-icons) | "All icons of INCY App" | **Confirms the app‑icon chooser** — Incy ships 16 selectable icons |
| [`INCY-DEV/incy-docs`](https://github.com/INCY-DEV/incy-docs) | Provider integration guide | `incy://` URL‑scheme + routing deep‑link semantics |
| [`INCY-DEV/incy-link-encoder`](https://github.com/INCY-DEV/incy-link-encoder) | TypeScript, `incy://crypt1/<payload>` | URL‑scheme shape only |

Play listing: `llc.itdev.incy` ([Google Play](https://play.google.com/store/apps/details?id=llc.itdev.incy)),
customization = "16 app icons and multiple themes" ([incy-platforms README](https://github.com/INCY-DEV/incy-platforms/blob/main/README.md)).
Routing/URL‑scheme deep links (`incy://routing/onadd/…`) documented at
[incy.gitbook.io/docs](https://incy.gitbook.io/docs/docs-en/routing.en).

**Therefore this design is reconstructed from the owner's screenshots + our own codebase**, not copied from Incy
source. Where a behavior is asserted about Incy it comes from the public docs/README above; the Android *implementation*
is entirely our own.

---

## 1. The look, decomposed

From the screenshots, an Incy settings screen is:

1. A vertically scrolling list of **grouped cards**. Each group has a small ALL‑CAPS gray header
   ("ОФОРМЛЕНИЕ", "СОЕДИНЕНИЕ" …) sitting *outside/above* a single rounded‑16dp surface card.
2. Inside a card, rows are stacked with **hairline inset dividers** between them (divider starts at the text, not
   under the icon tile) and the card has **one continuous rounded background** (top row rounds the top corners, bottom
   row rounds the bottom).
3. Every row = **[colored rounded‑square icon tile] · title (+ optional subtitle) · trailing value/chevron or switch**.
   - Icon tile: ~30–32dp rounded square (`radius ≈ 8dp`), a **flat pastel/solid fill unique per row**, a **24dp white
     line icon** centered on it. (Theme=blue palette, Language=blue globe, App‑icon=gold, Connection=yellow bolt,
     Tunnel=purple layers, Ping=green speedometer, Disconnect‑on‑lock=indigo moon, Memory=teal chip, etc.)
   - Trailing = gray value text + a `chevron_right` (navigation rows) **or** a `MaterialSwitch` (toggle rows).
4. A red **"Reset settings"** text/button sits alone at the bottom.
5. Bottom nav Home / Servers / Settings — already planned in `design-system-2026.md` §2 (bottom‑nav migration); out of
   scope here except that this screen is the "Settings" destination.

This is exactly `design-system-2026.md` §5.5 ("Settings row / card") made concrete, plus the **colored icon tile**,
which that doc did not specify. So the icon‑tile is the one genuinely new visual primitive.

---

## 2. Two ways to build it — comparison & recommendation

### Option A — Keep `PreferenceFragmentCompat`, restyle via custom `layout`/`widgetLayout` + card‑shaped `PreferenceCategory`  ✅ RECOMMENDED

Stay on AndroidX `Preference`. We already have a **fully wired** `SettingsFragment` with an MMKV `PreferenceDataStore`
(`helper/MmkvPreferenceDataStore`) and a lot of interdependent enable/disable logic
(`updateMux`, `updateFragment`, `updateEnableLocalProxy`, `updateHevTunSettings`, mode gating…). We keep all of it and
only change *appearance*:

- **Row appearance** — set `android:layout="@layout/pref_row_incy"` on each preference (or globally via a custom
  `PreferenceFragmentCompat` theme's `preferenceStyle`). The layout supplies the icon tile + title + summary + a
  `widget_frame` where `widgetLayout` injects the chevron or switch. The tile color + icon are per‑row via a tiny
  helper (see §5).
- **Card grouping** — give `PreferenceCategory` a custom `android:layout` whose *title* is the gray caps header, and
  give the **group's child rows** a card background using position‑aware backgrounds (first/middle/last), so a category
  reads as one rounded card. AndroidX exposes each child's position through `PreferenceViewHolder` via
  `isDividerAllowedAbove/Below`; the clean way is a custom `Preference.onBindViewHolder` mix‑in (a base class or an
  extension applied in `onCreatePreferences`) that sets the correct `bg_pref_card_top/middle/bottom/single` drawable.

**Pros:** lowest risk — zero behavioral rewrite; all existing listeners, summaries, gating, and the MMKV datastore keep
working. No new dependency. Search/highlight, dialogs (ListPreference/EditText) still free. ~1–2 new layouts + 4
drawables + a small binding helper.
**Cons:** position‑aware card corners in a `Preference` list take a little care (recompute on expand/collapse of the
`initialExpandedChildrenCount="0"` groups); `PreferenceCategory` styling is slightly fiddly. Live‑ticking values (memory)
still don't belong here (they live on Home — matches memory doc).

### Option B — Replace the whole screen with a `RecyclerView` + sealed `SettingItem` model

Drop `Preference` entirely; render rows with our own adapter over a `List<SettingRow>` (Header / Navigation / Toggle /
Value types), read/write MMKV directly, and hand‑roll dialogs.

**Pros:** total visual control (corners, tiles, animations trivial); the model maps 1:1 to the screenshots; no
`Preference` quirks.
**Cons:** **high risk / high effort** — we must re‑implement every ListPreference/EditTextPreference dialog, all the
enable/disable interdependencies currently in `SettingsActivity.kt` (mux↔concurrency, fragment↔length/interval,
localProxy↔socks/httpProxy, hevTun↔localProxy, mode gating), summary formatting, and password masking. That's ~300 lines
of working logic to port and re‑test. Also duplicates effort with `pref_bypass.xml` (which the circumvention doc keeps
as a `Preference` screen).

### Recommendation

**Take Option A.** It is materially lighter and lower‑risk: it is a *reskin*, not a rewrite, and it composes with the
two sibling docs that also assume `Preference` screens (`pref_bypass.xml`, the About/home mounts). Reserve Option B only
if, later, we want the settings **hub** (the top‑level list of cards that navigate out) to be its own screen separate
from the raw preference editor — in which case a *small* RecyclerView hub is fine (see §6, P1) while the detailed editors
stay `Preference`‑based. Best of both: **RecyclerView for the shallow "hub" of nav cards, `Preference` (restyled) for
the deep editors.**

---

## 3. Every Incy setting → our codebase (map)

Legend: **E** = exists today, **E***= exists but needs a value/label/wiring tweak, **N** = new.

### ПРОКСИ ПО ПРИЛОЖЕНИЯМ (Per‑app proxy) — **E**
- Incy: orange grid tile, value "Off", → picker.
- Ours: `ui/PerAppProxyActivity` + `PerAppProxyAdapter` already exist; `PREF_PER_APP_PROXY` / `PREF_PER_APP_PROXY_SET` /
  `PREF_BYPASS_APPS`. Currently launched from the **MainActivity menu** (`R.id.per_app_proxy_settings`, `MainActivity.kt:858`)
  and the pref row is **commented out** in `pref_settings.xml:82`. **Action:** add it back as a top nav row on this
  screen; value = "On/Off" from `PREF_PER_APP_PROXY`. No new logic.

### ОФОРМЛЕНИЕ (Appearance) card
| Incy row | Tile | Ours | Status |
|---|---|---|---|
| Theme ("Dark") | blue palette | `PREF_COLOR_THEME` (blue/mono) + `PREF_UI_MODE_NIGHT` (light/dark/system) — both `ListPreference`, recreate wired (`SettingsActivity.kt:118`, `SettingsChangeManager.makeRecreateUi`) | **E** — Incy's single "Theme=Dark" collapses light/dark + palette; keep as two rows or one combined dialog |
| Language ("System") | blue globe | `PREF_LANGUAGE` `ListPreference` (`arrays language_select`) | **E** |
| App icon | gold palette | — no chooser today; single `@mipmap/ic_launcher`, no `activity-alias` | **N** (see §4.1) |

### СОЕДИНЕНИЕ (Connection · Configure) — **E*** (navigation)
Single row → sub‑screen. **Maps to the circumvention doc's Bypass screen** (`res/xml/pref_bypass.xml`, Tier‑1 toggles:
Fragment / Browser‑fingerprint / Mux, presets, DNS). Do **not** design a second one here — this screen just contributes
the **nav row** (yellow bolt tile, "Configure") that opens `BypassSettingsFragment`. Underlying knobs already exist
(`PREF_FRAGMENT_*`, `PREF_MUX_*`, DNS, `PREF_REMOTE/DOMESTIC_DNS`, sniffing).

### ТУННЕЛЬ (Tunnel · Configure) — **E** (navigation)
Single row → sub‑screen for the **VPN/tun** knobs that already live in `pref_settings.xml`'s "VPN settings" category:
`PREF_VPN_MTU`, `PREF_VPN_BYPASS_LAN`, `PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX`, `PREF_USE_HEV_TUNNEL` +
`PREF_HEV_TUNNEL_LOGLEVEL`/`_RW_TIMEOUT`, `PREF_LOCAL_DNS_ENABLED`, `PREF_FAKE_DNS_ENABLED`, `PREF_IPV6_ENABLED`,
`PREF_PREFER_IPV6`, `PREF_APPEND_HTTP_PROXY`. **Action:** move that existing `PreferenceCategory` into its own
`res/xml/pref_tunnel.xml` (purple layers tile, "Configure"). Pure regrouping — the gating logic in `SettingsActivity.kt`
moves with it. (Split of Connection=circumvention vs Tunnel=tun mirrors Incy exactly.)

### ПРОВАЙДЕРЫ (Providers) card
| Incy row | Tile | Ours | Status |
|---|---|---|---|
| Provider settings ("Auto") | gray gear | `ui/SubSettingActivity` (subscriptions list, auto‑update) + `PREF_AUTO_FALLBACK` (`review-04-auto-fallback.md`). "Auto" ≈ auto‑update/auto‑fallback state | **E** — nav row to SubSetting |
| Ping settings ("HTTP GET") | green speedometer | `PREF_PING_METHOD` `ListPreference` (`arrays ping_method_*`) we just added, + `PREF_DELAY_TEST_URL` | **E** |

### ПРИЛОЖЕНИЕ (Application) card
| Incy row | Tile | Ours | Status |
|---|---|---|---|
| About ("INCY") | info | `ui/AboutActivity` | **E** — nav row |
| URL schemes ("incy://") | link | `ui/UrlSchemeActivity`; manifest scheme is `v2rayng` (`AndroidManifest.xml:162`) | **E*** — a read‑only "scheme" info row; keep our brand scheme (`departament://`/`v2rayng://`), not `incy://` |
| Backup (cloud) | cloud | `ui/BackupActivity` + WebDAV (`dialog_webdav`, `WEBDAV_BACKUP_*`) | **E** — nav row |
| Rate app (star) | gold star | — Play‑store intent | **N (trivial)** — `Utils.openUri("market://details?id=…")` (S) |

### ПРОИЗВОДИТЕЛЬНОСТЬ (Performance) card
| Incy row | Tile | Ours | Status |
|---|---|---|---|
| Advanced settings ("Xray Core") | slider | our "Advanced" + core log/mode knobs; we ship **only Xray core** | **E*/N** — a nav row labeled "Xray Core" opening the existing Advanced/Core categories; a *real* core selector (sing‑box) is out of scope, **L** — see §4.4 |
| Disconnect on screen lock (moon, toggle, "Break on screen lock") | indigo moon | — none | **N** (see §4.2) |
| Memory monitoring (chip, toggle ON, "Show on home screen") | teal chip | — none; ties to `memory-panel-design.md` | **N** (see §4.3) |

### ОТЛАДКА (Debug) — **E**
- Tunnel logs ("None · 1h"): `ui/LogcatActivity` (live log). "None · 1h" ≈ log level + retention; our log levels =
  `PREF_LOGLEVEL` (core) + `PREF_HEV_TUNNEL_LOGLEVEL`. **Action:** nav row → Logcat, value = current core log level.

### Reset settings (red) — **N (small)**
Clear the settings MMKV namespace and `recreate()`. `MmkvManager` already centralizes settings I/O; add a
`clearAllSettings()` that removes the `PREF_*` keys (or clears the settings MMKV instance) behind a confirm dialog. **S.**

### New‑settings feasibility summary
| New item | Feasibility | Effort | Files | Key risks |
|---|---|---|---|---|
| **App‑icon chooser** (§4.1) | Yes, standard `activity-alias` pattern | **M** | `AndroidManifest.xml` (aliases), `res/mipmap-*` (icon sets), new `AppIconActivity` + grid layout, a helper using `PackageManager.setComponentEnabledSetting` | Launcher icon briefly disappears / app may be evicted when the alias flips; **exactly one** alias must stay enabled; some launchers cache the old icon; TV/`LEANBACK_LAUNCHER` alias must be handled |
| **Disconnect on screen lock** (§4.2) | Yes | **S** | `AppConfig` (`PREF_DISCONNECT_ON_SCREEN_LOCK`), `service/CoreVpnService.kt` (register `ACTION_SCREEN_OFF` receiver) | `SCREEN_OFF` **cannot** be manifest‑registered → must be a dynamically‑registered receiver bound to the service lifecycle; avoid disconnecting on brief screen‑offs (debounce / respect Always‑On expectations) |
| **Memory monitoring → home card** (§4.3) | Yes | **M** | `AppConfig` (`PREF_MEMORY_MONITOR`), `res/layout/activity_main.xml` (a `layout_memory` card), `MainActivity`, reuse `util/MemoryStatsManager` from memory doc | Home is perf‑sensitive — sample at ≤0.5 Hz only while visible & toggle on; don't allocate per tick (memory doc §1.6/§2.7) |
| **Advanced = core selection ("Xray Core")** (§4.4) | As a **label/nav row**, yes; as a real multi‑core switch, no | **S** (label) / **L** (real) | label: strings + nav row; real: build system, `libv2ray`/sing‑box, config generation | We only bundle Xray; a real selector is a large core‑integration project — ship the honest "Xray Core" label now |
| **Rate app** | Yes | **S** | strings, `Utils.openUri` | none |
| **Reset settings** | Yes | **S** | `MmkvManager.clearAllSettings()`, confirm dialog | must not wipe servers/subscriptions — clear only the settings namespace/keys |

---

## 4. New features — concrete design

### 4.1 App‑icon chooser (activity‑alias)
- Declare N `<activity-alias>` entries in `AndroidManifest.xml`, each `targetActivity=".ui.MainActivity"`, each with its
  own `android:icon`/`roundIcon` and the `MAIN`/`LAUNCHER` (+`LEANBACK_LAUNCHER`) intent‑filter. The **default** alias is
  `enabled=true`; the rest `enabled=false`.
- A `AppIconActivity` shows a grid of icon options (reuse `incy-icons` as *inspiration only*, ship our own art). On pick:
  disable the currently‑enabled alias and enable the chosen one via
  `packageManager.setComponentEnabledSetting(component, ENABLED/DISABLED, DONT_KILL_APP)`.
- **Persist** the choice in `PREF_APP_ICON` so the grid shows the current selection.
- **Risks/mitigations:** only ever have one enabled alias (flip old→new atomically); warn that the launcher shortcut may
  refresh; keep the `LEANBACK_LAUNCHER` category on each alias for Android‑TV builds (this fork ships a TV banner
  `@mipmap/ic_banner`). Effort **M** mostly because of producing the icon art × densities.

### 4.2 Disconnect on screen lock
- New `PREF_DISCONNECT_ON_SCREEN_LOCK` (default off). When on and the tunnel is up, register a receiver for
  `Intent.ACTION_SCREEN_OFF` **at runtime** inside `CoreVpnService` (screen‑off can't be declared in the manifest). On
  fire → stop the tunnel (same path as the notification "Disconnect"). Unregister on service stop.
- **Debounce**: optionally require the screen to stay off for a few seconds, and skip if a "keep‑alive/Always‑On"
  expectation exists, so a glance at the lock screen doesn't kill an active session. Subtitle mirrors Incy:
  *"Break on screen lock."* Effort **S**.

### 4.3 Memory monitoring toggle → home card
- New `PREF_MEMORY_MONITOR` (Incy shows it **ON**; we default **off** to stay light). This is the **switch that drives
  the memory‑panel doc's home mount**: when on, `MainActivity` shows a small `layout_memory` card (headline
  `Debug.MemoryInfo.getTotalPss()/1024` MB) driven by `util/MemoryStatsManager` (memory doc §1.3/§4), sampled with a
  lifecycle‑scoped 2 s loop **only while Home is visible**. Subtitle mirrors Incy: *"Show on home screen."*
- **Coordinate:** `memory-panel-design.md` proposed the card primarily on the **About** screen; this Incy‑parity toggle
  makes **Home** the gated mount. Land `MemoryStatsManager` once; both surfaces consume it. Effort **M**.

### 4.4 "Advanced settings — Xray Core"
- Ship now as a **navigation row** (title "Advanced settings", value "Xray Core") opening the existing Advanced/Core
  `PreferenceCategory`s (`PREF_LOGLEVEL`, `PREF_MODE`, socks/local‑proxy, resolve method, delay‑test URL, real‑ping
  concurrency, etc.). The "Xray Core" value is honest — that is the engine we bundle.
- A **real** core switcher (e.g. sing‑box) is a separate large effort (**L**): second native core, config generation
  fork, packaging. Out of scope; note it so the label isn't mistaken for a live selector.

---

## 5. The row & card XML — concrete sketches (Option A)

### 5.1 Icon‑tile drawable — rounded square, tint + 24dp icon
`res/drawable/bg_pref_tile.xml` (solid tile; the per‑row **color** is applied at bind via `backgroundTintList`, the
**icon** is a separate `ImageView` `src`):
```xml
<!-- bg_pref_tile.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="@dimen/radius_xs"/>   <!-- 8dp, design-system §3.2 -->
    <solid android:color="@android:color/white"/>  <!-- tinted per-row -->
</shape>
```
Per‑row tile colors live in `res/values/colors.xml` as a small pastel set (`tile_blue`, `tile_gold`, `tile_yellow`,
`tile_purple`, `tile_green`, `tile_indigo`, `tile_teal`, `tile_orange`, `tile_gray`) — one flat color each, chosen to
pass on both light and dark surfaces (design‑system §3.6 already carries the palette to derive from).

### 5.2 Row layout — `res/layout/pref_row_incy.xml`
Used as each preference's `android:layout`. The AndroidX preference framework fills `@android:id/title`,
`@android:id/summary`, and injects the trailing control into `@android:id/widget_frame`.
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:minHeight="56dp" android:gravity="center_vertical"
    android:paddingStart="@dimen/space_12" android:paddingEnd="@dimen/space_12"
    android:paddingTop="@dimen/space_8" android:paddingBottom="@dimen/space_8"
    android:background="?attr/selectableItemBackground">

    <!-- colored rounded-square icon tile -->
    <FrameLayout android:layout_width="32dp" android:layout_height="32dp"
        android:background="@drawable/bg_pref_tile"
        android:backgroundTint="@color/tile_blue">           <!-- set per-row at bind -->
        <ImageView android:id="@+id/pref_icon"
            android:layout_width="20dp" android:layout_height="20dp"
            android:layout_gravity="center"
            android:src="@drawable/ic_settings_24dp"          <!-- set per-row -->
            android:tint="@android:color/white"/>
    </FrameLayout>

    <LinearLayout android:layout_width="0dp" android:layout_weight="1"
        android:layout_height="wrap_content" android:orientation="vertical"
        android:layout_marginStart="@dimen/space_12">
        <TextView android:id="@android:id/title"
            android:textSize="15sp" android:textColor="?attr/colorOnSurface"
            android:maxLines="1" android:ellipsize="end"/>
        <TextView android:id="@android:id/summary"
            android:textSize="13sp" android:textColor="?attr/colorOnSurfaceVariant"
            android:maxLines="2" android:ellipsize="end"/>
    </LinearLayout>

    <!-- value text (nav rows) sits before the widget frame -->
    <TextView android:id="@+id/pref_value"
        android:textSize="13sp" android:textColor="?attr/colorOnSurfaceVariant"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/space_4"/>

    <!-- framework injects switch (widgetLayout) OR we show a chevron -->
    <FrameLayout android:id="@android:id/widget_frame"
        android:layout_width="wrap_content" android:layout_height="wrap_content"/>
</LinearLayout>
```
- **Nav rows:** set `widgetLayout` to a 24dp `ic_chevron_right` ImageView; put the current value in `pref_value` (bind
  from summary).
- **Toggle rows:** for `SwitchPreferenceCompat`, set `android:widgetLayout="@layout/pref_widget_switch"` wrapping a
  `com.google.android.material.materialswitch.MaterialSwitch` styled `BrandedSwitch` (already defined, design‑system §5.5).

### 5.3 Making a `PreferenceCategory` look like one rounded card
Two parts:

**(a) The gray caps header** — custom category layout `res/layout/pref_category_incy.xml` that shows only
`@android:id/title` styled 13sp/600 `?attr/colorPrimary`‑or‑`onSurfaceVariant`, ALL‑CAPS, with `space_16` top / `space_8`
bottom padding and *no* background (the header floats above the card, as in Incy).

**(b) The card body** — the child rows share one rounded surface. Provide four backgrounds and pick by position:
```
bg_pref_card_single.xml   corners: 16dp all
bg_pref_card_top.xml      corners: 16dp top only
bg_pref_card_middle.xml   corners: 0 (+ 1dp inset top divider)
bg_pref_card_bottom.xml   corners: 16dp bottom only
```
each `<shape>` filled `?attr/colorSurface` (or `colorSurfaceContainer` per design‑system §3.6 "fix the surface‑container
tokens"), with the design‑system 1dp `outlineVariant` hairline. Apply them from a small **bind mix‑in**:

```kotlin
// applied in onCreatePreferences via a base Preference or a onBindViewHolder hook
fun bindCardBackground(holder: PreferenceViewHolder, indexInGroup: Int, groupSize: Int) {
    val bg = when {
        groupSize == 1        -> R.drawable.bg_pref_card_single
        indexInGroup == 0     -> R.drawable.bg_pref_card_top
        indexInGroup == groupSize - 1 -> R.drawable.bg_pref_card_bottom
        else                  -> R.drawable.bg_pref_card_middle
    }
    holder.itemView.setBackgroundResource(bg)
    // horizontal card inset:
    (holder.itemView.layoutParams as? MarginLayoutParams)?.apply { … space_16 sides … }
}
```
Recompute `indexInGroup/groupSize` when a collapsed group (`initialExpandedChildrenCount="0"`) expands. This is the only
delicate bit of Option A; it's ~40 lines and localized.

> **Lighter alternative if the position math proves annoying:** wrap each logical group in its own nested
> `PreferenceScreen`/`PreferenceCategory` rendered inside a `MaterialCardView` via a category layout that *is* a card,
> and let rows be plain (transparent) with inset dividers. Slightly less pixel‑perfect at the corners but no per‑row
> position logic. Recommend trying the position‑aware backgrounds first; fall back to this if needed.

### 5.4 Wiring the per‑row tile color + icon
Preferences don't carry a "tile color" attribute, so attach it out‑of‑band. Cheapest: a `Map<prefKey, Pair<iconRes,
tileColorRes>>` in the fragment, applied in a shared `onBindViewHolder` hook (subclass `Preference` once as
`IncyPreference`, or iterate in `onCreatePreferences` and wrap each pref's binder). Keeps XML declarative and the color
table in one Kotlin map — trivially themeable.

---

## 6. Prioritized implementation plan

**P0 — foundation & the reskin (highest value: the look itself)**
1. Add tile drawables (`bg_pref_tile`), card backgrounds (`bg_pref_card_{single,top,middle,bottom}`), tile color set,
   and any missing 24dp icons (`ic_chevron_right`, moon, chip/memory, layers, bolt, grid, globe, palette, speedometer —
   we already have `ic_settings/ic_speed/ic_lock/ic_backup/ic_about/ic_cloud_download`).
2. `pref_row_incy.xml` + `pref_category_incy.xml` + `pref_widget_switch.xml`; the `bindCardBackground` + tile mix‑in.
3. Point `SettingsFragment` rows at these layouts (globally via theme `preferenceStyle`, per‑type via `android:layout`).
   Keep all existing listeners/gating untouched. Verify Blue/Mono × Light/Dark render (design‑system P0 tokens).

**P1 — restructure into the Incy card groups (regroup existing prefs, add nav rows)**
4. Rebuild `pref_settings.xml` group order to match §3: Per‑app proxy → Appearance → Connection(nav) → Tunnel(nav) →
   Providers → Application → Performance → Debug → Reset. Un‑comment/re‑add per‑app‑proxy as a nav row.
5. Split out `pref_tunnel.xml` (move the VPN category + its gating), and add the **Connection** nav row pointing at
   `pref_bypass.xml` (circumvention doc). Convert `CheckBoxPreference`→`SwitchPreferenceCompat` for the toggle look.
6. (Optional) a thin RecyclerView **hub** for the top‑level nav cards if we want the shallow list separate from editors
   (see §2 recommendation); otherwise all‑Preference is fine.

**P2 — the high‑value new toggles**
7. **Disconnect on screen lock** (§4.2) — `PREF_DISCONNECT_ON_SCREEN_LOCK` + service receiver. Small, self‑contained,
   visibly "Incy".
8. **Memory monitoring** (§4.3) — `PREF_MEMORY_MONITOR` + `MemoryStatsManager` + Home `layout_memory` card (coordinate
   memory doc; land the manager once).
9. **Reset settings** + **Rate app** rows (both S).

**P3 — the heavier / cosmetic‑optional new features**
10. **App‑icon chooser** (§4.1) — aliases + `AppIconActivity` + icon art × densities (M; gated by art).
11. **Advanced "Xray Core"** as a label/nav row (§4.4); real multi‑core switching explicitly deferred (L).
12. Glass finish on the cards (design‑system §4 Tier A/B/C) behind the "Glass surfaces" toggle — the settings background
    `bg_settings_glass` already exists.

---

## 7. Anti‑bloat / non‑duplication guardrails
- **No new libraries.** Option A reuses AndroidX `Preference` + Material we already ship.
- **Don't re‑implement dialogs or gating** (that's the whole reason to reject Option B).
- **Don't design Bypass or the RAM panel here** — link to `circumvention-settings-design.md` and `memory-panel-design.md`.
- **Don't ship `incy://`** — keep our brand URL scheme; the "URL schemes" row is informational.
- Keep new prefs **off by default** (memory monitor, disconnect‑on‑lock) to honor the "stay light" principle.

---

## 8. Ten‑line summary
1. **Incy Android is closed‑source** — verified: `INCY-DEV` only publishes `incy-platforms`, `incy-icons`, `incy-docs`, `incy-link-encoder` (no app code). This design is reconstructed from the screenshots + our codebase, citing those repos.
2. The look = scrolling **grouped rounded‑cards**, each row a **colored rounded‑square icon tile + title/subtitle + value/chevron or switch**, gray caps headers, red "Reset" at the bottom.
3. Two build paths compared; **recommend Option A** — keep the existing MMKV‑backed `PreferenceFragmentCompat` and *reskin* it (custom row `layout`, `widgetLayout` switch, position‑aware card backgrounds), because it's a reskin not a rewrite and composes with the Bypass/Memory sibling docs.
4. Option B (full RecyclerView) rejected as high‑risk: it would force re‑porting ~300 lines of dialog + enable/disable interdependency logic already working in `SettingsActivity.kt`.
5. Gave concrete XML: `bg_pref_tile` (8dp rounded square, per‑row tint + 24dp white icon), `pref_row_incy.xml`, and four `bg_pref_card_{single/top/middle/bottom}` drawables applied by a ~40‑line `bindCardBackground` mix‑in to fake one continuous card per `PreferenceCategory`.
6. **Most Incy settings already exist**: per‑app proxy, theme+night+palette, language, ping method (just added), delay‑test URL, subscriptions/auto, backup/WebDAV, About, URL scheme, logcat, and all Connection(=Bypass)/Tunnel(=tun: MTU/hev/bypass‑LAN/DNS) knobs — only regrouping + nav rows needed.
7. Incy's **"Соединение · Configure"** → `pref_bypass.xml` (circumvention doc); **"Туннель · Configure"** → a new `pref_tunnel.xml` holding today's VPN category; **"Ping · HTTP GET"** → `PREF_PING_METHOD`.
8. **Genuinely new**: app‑icon chooser (activity‑alias, **M**), disconnect‑on‑screen‑lock (service `SCREEN_OFF` receiver, **S**), memory‑monitoring toggle→Home card (reuse `MemoryStatsManager`, **M**), "Advanced=Xray Core" as an honest label (**S**; real multi‑core = **L**), plus Reset + Rate (**S**).
9. Plan: **P0** land tiles/cards/row layouts and reskin the existing screen; **P1** regroup into the Incy card order + Connection/Tunnel nav rows; **P2** ship the two flagship toggles (screen‑lock, memory) + Reset/Rate; **P3** app‑icon chooser, "Xray Core" label, optional glass.
10. Guardrails: no new libs, don't duplicate the Bypass/RAM docs, keep new toggles off by default, keep our own URL scheme (not `incy://`).
```
