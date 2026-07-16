# Home Screen Polish — Incy Background, De‑carded Hero, Connect Glow, Frameless Nav

Design-only spec. **Do not modify code from this doc** — it defines the exact visual treatment to
apply later. All targets are in `V2rayNG/app/src/main/res/…` and `…/ui/MainActivity.kt`.

## Why the current Home "isn't Incy"

Incy Home has **no cards on the hero area**. The screen is a single near‑black canvas (`#0A0B0D`)
with a soft upper‑center glow; the connect control is a dark circle wearing a *glowing ring* that
sits directly on that canvas, with a faint radial bloom behind the shield. Our current Home wraps
everything in `card_hero` (a `MaterialCardView` with `colorSurface` fill + 1dp stroke) and the
connect button is itself a bordered `MaterialCardView`. Two nested bordered surfaces on a flat
background read as "a card app," not Incy. The bottom nav also carries a pill active‑indicator
(`itemActiveIndicatorStyle`) which Incy does not use.

Fix in four moves: (1) paint a radial gradient/glow canvas, (2) delete the hero card and connect
card borders/fills, (3) add a real bloom behind the connect circle, (4) strip the nav frame +
indicator and drive active/inactive purely by tint.

### Palette anchors (from `values-night/colors.xml`, the dark default)

| Token | Hex | Use |
|---|---|---|
| `md_theme_background` | `#0A0B0D` | canvas edge / true background |
| `md_theme_surface` | `#141619` | (was) hero + connect fill — being removed |
| `md_theme_surfaceContainerHigh` | `#1A1D21` | connect circle fill (kept, dark disc) |
| `md_theme_primary` / `brand_blue` | `#4C8DFF` | ring, glow, active nav, shield-on |
| `md_theme_onSurfaceVariant` | `#8A909C` | grey shield-off, inactive nav |
| `md_theme_outlineVariant` | `#20242B` | (was) borders — being removed |

---

## 1. Background — radial gradient canvas (primary approach)

Create a new drawable that lifts a navy/blue glow in the **upper center** (behind the connect
control) and fades to true near‑black `#0A0B0D` at the edges. This is the single biggest "now it
looks like Incy" change and it is free (one GPU gradient fill, universal from API 21).

**New file: `res/drawable/bg_home_gradient.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Incy Home canvas: navy glow lifted in the upper-center, fading to near-black.
     centerY 0.30 puts the bloom behind the connect circle, not dead-center. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:type="radial"
        android:centerX="0.5"
        android:centerY="0.30"
        android:gradientRadius="560dp"
        android:startColor="#1B2D50"
        android:centerColor="#0E141F"
        android:endColor="#0A0B0D" />
    <solid android:color="#0A0B0D" />
</shape>
```

- `startColor #1B2D50` is `md_theme_primary #4C8DFF` knocked down into the background — a desaturated
  navy lift, NOT bright blue. Keep it subtle; if it reads too strong on device, step it to `#16233F`.
- `centerColor #0E141F` is the mid falloff so the transition to `#0A0B0D` is smooth (no hard ring).
- `gradientRadius 560dp` covers a tall phone; the glow reaches roughly the vertical middle then dies.
- The `<solid>` is a safety fill for any area outside the radius (very tall screens).

**Where to set it.** Two options — recommend **option A** (whole Home scrolls over the glow, matches
Incy where the glow is anchored to the connect zone at the top of the scroll):

- **A (recommended):** set on the Home scroll container `group_home`:
  ```xml
  <androidx.core.widget.NestedScrollView
      android:id="@+id/group_home"
      android:background="@drawable/bg_home_gradient"
      … />
  ```
  Because the inner `LinearLayout` and rows below are transparent, the glow shows through behind the
  stats row + connect circle and fades before the server list. The root `LinearLayout` keeps
  `?android:attr/colorBackground` (`#0A0B0D`) so the toolbar/nav strips stay true black — the glow
  is confined to the Home tab, exactly like Incy.

- **B (glow-layer only):** keep `group_home` on `colorBackground` and instead drop a dedicated glow
  `View` as the first child of the connect `FrameLayout` (see §3, `bg_connect_glow`). Use this if the
  product owner wants the rest of Home dead‑black and only the connect disc to bloom. A and B can be
  combined (gradient canvas + tighter bloom) for the richest result.

### RenderEffect blur — the honest tradeoff (secondary, not recommended as primary)

`android.graphics.RenderEffect.createBlurEffect(...)` (API 31 / Android 12+) can blur a backing
bitmap or a child view to fake Incy's frosted depth. Reality check:

- **Cost:** a live blur re‑samples every frame it's dirty; on a scrolling `NestedScrollView` that is
  real GPU/bandwidth cost and can drop frames on low/mid devices. A *static* blurred bitmap (blur
  once, cache) is cheap but then it's just an image — a gradient does the same for less.
- **Coverage:** hard‑gated to API 31+. `departament` supports older devices, so you'd ship the
  gradient fallback anyway (`RenderEffect` is a no‑op below 31, and `Paint`/`RenderScript` blur is
  deprecated). You'd maintain two paths for a look the gradient already delivers.
- **Verdict:** ship **§1 gradient + §3 bloom** as the primary, universal treatment. Treat blur as an
  optional API‑31+ enhancement only if a future "glass" pass is requested — and even then prefer a
  pre‑blurred cached asset over a live `RenderEffect` on the scroll view.

---

## 2. Hero de‑carding — remove `card_hero` (drop the border + surface)

Goal: connect circle, status text, and the server‑info line sit **directly on the gradient canvas**,
with no surface fill and no stroke. Keep every id intact (`card_connect`, `img_connect`,
`tv_connection_status`, `layout_server_info`, `tv_selected_server`, `tv_test_state`).

**Change in `res/layout/activity_main.xml`:** replace the `MaterialCardView` wrapper
(`@+id/card_hero`, lines ~135–148 and its closing tag ~255) with a plain transparent `LinearLayout`.
Keep the inner content `LinearLayout` and its children exactly as they are.

Replace the opening tag:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/card_hero"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:layout_marginStart="12dp"
    android:layout_marginTop="12dp"
    android:layout_marginEnd="12dp"
    android:layout_marginBottom="6dp"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="28dp"
    app:cardElevation="0dp"
    app:strokeColor="?attr/colorOutlineVariant"
    app:strokeWidth="1dp">
```

with:

```xml
<LinearLayout
    android:id="@+id/card_hero"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:orientation="vertical"
    android:layout_marginTop="8dp"
    android:layout_marginBottom="4dp">
```

And change the matching closing tag `</com.google.android.material.card.MaterialCardView>` (the one
that closes `card_hero`, ~line 255) to `</LinearLayout>`.

Attributes to **drop** (these are what made it a card): `app:cardBackgroundColor`,
`app:cardCornerRadius`, `app:cardElevation`, `app:strokeColor`, `app:strokeWidth`, and the
side margins (`12dp`) so the content spans full width like Incy. Notes:

- The id `card_hero` is retained on the `LinearLayout` so the generated binding still has
  `binding.cardHero` if referenced (grep shows it is not touched in code, so a rename is also safe —
  but keeping the id is the zero‑risk move).
- `LinearLayout` has no `orientation` default, so we add `orientation="vertical"` (the removed
  `MaterialCardView` implicitly stacked its single child; the inner content `LinearLayout` already
  centers everything, so this outer one just needs to stack).
- The inner content `LinearLayout` (with `paddingTop="28dp"` etc., `gravity="center_horizontal"`) is
  unchanged — it keeps the vertical rhythm.

### Connect circle — remove the border, keep the dark disc

The connect control (`@+id/card_connect`) is a `MaterialCardView` with a 1dp
`colorOutlineVariant` stroke. Incy's disc has **no stroke** — the only edge is the glowing ring.
Keep it a `MaterialCardView` (so `cardCornerRadius` gives the perfect circle and ripple clips to the
circle) but strip the stroke and darken the fill so it reads as a hole in the glow:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/card_connect"
    android:layout_width="176dp"
    android:layout_height="176dp"
    android:layout_gravity="center"
    android:clickable="true"
    android:contentDescription="@string/tasker_start_service"
    android:focusable="true"
    app:cardBackgroundColor="?attr/colorSurfaceContainer"
    app:cardCornerRadius="88dp"
    app:cardElevation="0dp"
    app:rippleColor="?attr/colorPrimary"
    app:strokeWidth="0dp">
```

Changes: `strokeWidth` `1dp → 0dp`, drop `strokeColor`, and set fill to `colorSurfaceContainer`
(`#141619`, slightly darker than the previous `colorSurfaceContainerHigh #1A1D21`) so the disc looks
recessed inside the bloom. The `img_connect` shield stays as-is (tinted by MainActivity per state).

---

## 3. Connect glow — brighter ring + a real radial bloom behind the disc

Two drawables: refine the existing ring, and add a bloom layer that fills the 230dp
`FrameLayout` behind `card_connect`.

**Refine `res/drawable/bg_connect_ring.xml`** — current rings are fine in shape; nudge them brighter
and add a third faint outer halo so the ring reads as "glowing," not "outlined":

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Faint outer halo -->
    <item>
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="6dp" android:color="#1A4C8DFF" />
        </shape>
    </item>
    <!-- Mid ring -->
    <item android:top="10dp" android:bottom="10dp" android:left="10dp" android:right="10dp">
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="1.5dp" android:color="#4D4C8DFF" />
        </shape>
    </item>
    <!-- Inner bright ring -->
    <item android:top="24dp" android:bottom="24dp" android:left="24dp" android:right="24dp">
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="2dp" android:color="#B34C8DFF" />
        </shape>
    </item>
</layer-list>
```

Hex are ARGB on `#4C8DFF`: `#1A…`≈10%, `#4D…`≈30%, `#B3…`≈70% alpha. A flat‑drawable stroke can't
truly feather, so the *illusion* of glow comes from the layered halo + the bloom below.

**New file: `res/drawable/bg_connect_glow.xml`** — the radial bloom that makes the disc "glow":

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Soft blue bloom behind the connect disc: bright-ish center → fully transparent edge. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <gradient
        android:type="radial"
        android:centerX="0.5"
        android:centerY="0.5"
        android:gradientRadius="115dp"
        android:startColor="#594C8DFF"
        android:centerColor="#264C8DFF"
        android:endColor="#004C8DFF" />
</shape>
```

`#59…`≈35% blue center, `#26…`≈15% mid, `#00…` = transparent edge (note: keep the `4C8DFF` RGB on the
transparent stop so there's no grey fringe as it fades).

**Wire it in `activity_main.xml`** as the first child of the 230dp connect `FrameLayout`, *behind*
the ring `View` and `card_connect`:

```xml
<FrameLayout
    android:layout_width="230dp"
    android:layout_height="230dp"
    android:layout_gravity="center">

    <!-- NEW: soft bloom, drawn first so it sits behind ring + disc -->
    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/bg_connect_glow" />

    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/bg_connect_ring" />

    <com.google.android.material.card.MaterialCardView android:id="@+id/card_connect" … />
</FrameLayout>
```

Optional (nicer, more Incy on connect): make the bloom brighten when connected by swapping the
`View`'s background alpha in `applyRunningState` — see §5. Not required for the static look.

---

## 4. Frameless bottom nav — no indicator, no frame, tint‑only active state

Incy's bar is a frameless floating strip: active tab = blue icon + blue label, inactive = grey,
**no pill behind the active item, no elevation, no border**. Current nav has a
`md_theme_primaryContainer` pill via `itemActiveIndicatorStyle="@style/BottomNavIndicator"`.

**In `res/layout/activity_main.xml`** — replace the `BottomNavigationView` block:

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/bottom_nav"
    android:layout_width="match_parent"
    android:layout_height="64dp"
    android:background="@android:color/transparent"
    app:elevation="0dp"
    app:itemIconSize="22dp"
    app:itemPaddingTop="8dp"
    app:itemIconTint="@color/bottom_nav_item_color"
    app:itemTextColor="@color/bottom_nav_item_color"
    app:itemRippleColor="@android:color/transparent"
    app:itemActiveIndicatorStyle="@null"
    app:itemTextAppearanceActive="@style/BottomNavLabel"
    app:itemTextAppearanceInactive="@style/BottomNavLabel"
    app:labelVisibilityMode="labeled"
    app:menu="@menu/menu_bottom_nav" />
```

Key changes vs. current:
- `app:itemActiveIndicatorStyle="@null"` — removes the pill entirely (this is the "no frame" fix).
  If a build rejects `@null` on this attr, use the transparent‑indicator style variant in §4b below.
- `android:background` `?android:attr/colorBackground` → `@android:color/transparent` so the bar
  floats over the gradient canvas with no visible frame. (Use `?android:attr/colorBackground` =
  `#0A0B0D` instead if you want the bar to stay opaque black over scrolling content — both are
  frameless; transparent is the more literal Incy match.)
- `app:elevation="0dp"` kept (no shadow/line).
- `app:itemRippleColor="@android:color/transparent"` removes the touch pill flash so nothing framed
  ever appears behind an item.
- `itemIconTint` + `itemTextColor` now point at a shared color‑state‑list (below) so blue/grey is
  driven purely by `state_checked`.

**New file: `res/color/bottom_nav_item_color.xml`** (icon + text share one selector):

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- active tab: bright blue -->
    <item android:color="#4C8DFF" android:state_checked="true" />
    <!-- inactive tab: mid grey (legible on near-black) -->
    <item android:color="#8A909C" />
</selector>
```

- Active `#4C8DFF` = `md_theme_primary` / `icon_blue`. Inactive `#8A909C` = `md_theme_onSurfaceVariant`
  (Incy's secondary‑text grey — readable). Prefer this over the existing `color_fab_inactive #3A3F49`,
  which is too dim for a nav label. If the owner wants dimmer inactive icons, drop to `#5A616C`.
- Reference the theme tokens instead of raw hex if you'd rather it follow the mono overlay:
  `@color/md_theme_primary` and `@color/md_theme_onSurfaceVariant`. Raw hex is shown to match Incy
  exactly regardless of overlay.

**4b. `res/values/styles.xml`** — the `BottomNavIndicator` style becomes unused once
`itemActiveIndicatorStyle="@null"`. Either delete it, or, if you keep the attribute pointing at a
style for compatibility, neutralize it so no pill ever renders:

```xml
<!-- Frameless: active indicator made invisible (no pill behind the active tab) -->
<style name="BottomNavIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:color">@android:color/transparent</item>
    <item name="android:width">0dp</item>
    <item name="android:height">0dp</item>
</style>
```

`BottomNavLabel` (11sp bold) is unchanged — the bold label is correct for Incy.

---

## 5. MainActivity — what must (and must not) change

Good news: **MainActivity never sets a per‑state background on `card_hero` or `card_connect`.**
`applyRunningState()` only calls `binding.imgConnect.setColorFilter(...)` (shield tint: primary when
loading/connected, `colorOnSurfaceVariant` grey when idle) and sets `tv_connection_status` text
(MainActivity.kt ~343–361). So the de‑carding and glow are **pure layout/drawable work** — no card
background code to remove.

Two compatibility notes:

1. **`card_connect` stays a `MaterialCardView`**, so `binding.cardConnect` keeps its type and every
   call site is safe: `setOnClickListener` / `setOnLongClickListener` (MainActivity.kt ~153–154),
   `contentDescription` (~352, ~358), and `themeColor(attr)` which does
   `MaterialColors.getColor(binding.cardConnect, attr)` (~372). No change required.

2. **`card_hero` changes type** `MaterialCardView → LinearLayout`. Grep shows no code references
   `binding.cardHero`, so `binding` regenerates cleanly. Keeping the same id avoids any rename churn.

**Optional enhancement (only if you want the bloom to react to state):** give the new bloom `View` an
id (e.g. `@+id/view_connect_glow`) and in `applyRunningState` set
`binding.viewConnectGlow.alpha = if (isRunning) 1f else 0.55f` (and maybe `0.8f` while loading) so the
glow brightens on connect — mirroring Incy's "brighter ring when connected." This is additive; the
static drawables already deliver the base look, so it is not required for this pass.

Nothing else in MainActivity, the nav selection logic, or the mono/blue theme overlays needs to
change — the selectors and drawables inherit the active overlay's `colorPrimary` where theme tokens
are used, and use literal Incy hex where an exact match is wanted.

---

## Summary (hex + file targets)

1. **Canvas:** new `res/drawable/bg_home_gradient.xml` — `radial` `#1B2D50 → #0E141F → #0A0B0D`,
   `centerY 0.30`, `radius 560dp`; set as `group_home` background. Gradient is the primary approach;
   RenderEffect blur is API‑31‑only and costlier — skip as primary.
2. **De‑card hero:** in `activity_main.xml` turn `card_hero` `MaterialCardView → LinearLayout`; drop
   `cardBackgroundColor / cardCornerRadius / cardElevation / strokeColor / strokeWidth` + side margins.
3. **Connect disc:** `card_connect` keeps `MaterialCardView` but `strokeWidth 1dp → 0dp` (drop
   `strokeColor`), fill → `colorSurfaceContainer` (`#141619`).
4. **Glow:** brighten `bg_connect_ring.xml` (10/30/70% `#4C8DFF` alphas + outer halo); add
   `res/drawable/bg_connect_glow.xml` radial `#594C8DFF → #264C8DFF → #004C8DFF`, placed behind the
   ring in the 230dp `FrameLayout`.
5. **Frameless nav:** `activity_main.xml` — `itemActiveIndicatorStyle=@null`, background transparent,
   `elevation 0`, transparent `itemRippleColor`; new `res/color/bottom_nav_item_color.xml`
   (`#4C8DFF` checked / `#8A909C` unchecked) on both `itemIconTint` + `itemTextColor`; neutralize or
   delete `BottomNavIndicator` in `styles.xml`.
6. **Code:** none required — MainActivity sets no per‑state card bg; `card_connect` stays a
   `MaterialCardView` so all call sites hold; optional `view_connect_glow` alpha toggle in
   `applyRunningState` for a connect‑reactive bloom.
7. **Kept ids:** `card_connect, img_connect, tv_connection_status, layout_server_info,
   tv_selected_server, tv_test_state` all preserved; `card_hero` id kept on the new `LinearLayout`.
8. **Anchors:** bg `#0A0B0D`, navy lift `#1B2D50`, primary/glow/active `#4C8DFF`, disc `#141619`,
   inactive nav `#8A909C`.
