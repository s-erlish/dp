# 10 - Design system

**Departament VPN - the definitive cross-platform token set and component library.**

`00-rules.md` is the law and outranks this file on any conflict. `03-direction.md` decides what the
product looks like and why. **This file is the single machine-readable answer to "what value do I
type".** Every number, every hex, every resource name, every state, for both clients, for all three
themes.

If a value is not in this file, it does not exist. If you need one that is not here, you add it here
first (with its purpose and its measured contrast ratio), then add it to both platforms' token files
in the same change, then use it. That order is not negotiable.

| | Android | Desktop (Windows / Linux / macOS) |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Token files | `V2rayNG/app/src/main/res/values/{colors,dimens,styles,themes,motion,attrs}.xml`, `res/values-night/{colors,themes}.xml` | `v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml`, `Assets/GlobalStyles.axaml`, `Common/Motion.cs` |
| Consumption | `?attr/...` theme attributes, `@dimen/...`, `@style/...` | `{DynamicResource ...}`, class selectors |

Read before using this file: `00-rules.md` sections 1-3 and 16, `03-direction.md` sections 2-9,
`.claude/skills/impeccable/SKILL.md`, `reference/product.md`, `reference/colorize.md`,
`reference/typeset.md`, `reference/interaction-design.md`, `reference/android.md`,
`.claude/skills/ui-ux-design-system/references/states-and-variants.md`.

---

## 0. How to use this document

### 0.1 The rule of three layers

```
Layer 1  PRIMITIVE   raw values, no meaning        blue_60  = #4C8DFF
             |                                     space_16 = 16
             v
Layer 2  SEMANTIC    a role in the product         color_accent = blue_60
             |                                     space_gutter = space_16
             v
Layer 3  COMPONENT   one component's contract      btn_primary_bg = color_accent
                                                   btn_primary_height = 52
```

**References only ever point downward.** A component token may reference a semantic token. A
semantic token may reference a primitive. A primitive references nothing. A layout file references
**only layer 2 or layer 3** - never a primitive, never a hex.

Why three layers and not one flat list: the product ships three themes plus a mono overlay plus a
light/dark split of that overlay. Only layer 2 changes between themes. Layer 1 is the fixed palette
of the brand. Layer 3 never changes at all: a button is a button in every theme, and it is exactly
the same button on both platforms.

### 0.2 Naming convention, identical in spirit across platforms

| Layer | Android (snake_case, `res/values`) | Desktop (Dot.Case, AXAML key) | Example pair |
|---|---|---|---|
| Primitive colour | `<color name="blue_60">` | `<Color x:Key="Color.Blue60">` | `blue_60` / `Color.Blue60` |
| Semantic colour | `?attr/colorPrimary` backed by `<color name="md_theme_primary">` | `<SolidColorBrush x:Key="Brush.Accent">` | accent |
| Semantic dimension | `@dimen/space_16` | `<x:Double x:Key="Space.16">` | 16 |
| Semantic radius | `@dimen/radius_card` | `<CornerRadius x:Key="Radius.Card">` | 20 |
| Type role | `@style/TextAppearance.App.Title` | `TextBlock.Title` class | Title |
| Component style | `@style/Widget.App.Button.Primary` | `Button.Primary` class | primary button |
| Motion duration | `@integer/motion_state` | `Motion.Dur.State` / literal `0:0:0.22` | 220 ms |
| Motion curve | `@interpolator/ease_standard` | `{StaticResource Ease.Standard}` / `Motion.Ease.Standard` | (0.2,0,0,1) |

**The parity contract:** if a token exists on one platform it exists on the other, with the same
name in the platform's own casing and the same value. A token that exists on only one platform is a
defect logged in section 10, not a platform difference. The only tolerated asymmetries are listed in
section 6.5 and each has a written reason.

### 0.3 What is a defect

- A hex literal in `res/layout/**`, `res/menu/**`, or `Views/**`.
- A `dp` / `px` / `Margin` / `Padding` value not in section 2.3.
- `android:textSize` or `FontSize` set on an element instead of applying a role.
- `StaticResource Brush.*` on desktop (freezes the value, breaks live theme switching).
- `@color/md_theme_*` referenced directly from a layout instead of `?attr/...`.
- A radius not in {12, 16, 20, 24-top, pill}.
- A duration or curve not in section 2.5.
- A component built by hand when section 7 defines one.

Greps that catch each of these are in section 10.3.

---

## 1. Layer 1 - Primitives

Primitives carry **no meaning**. `blue_60` is not "the accent"; it is a blue. Nothing in a layout
ever names a primitive. They exist so that the semantic layer has a closed, auditable palette to
alias, and so that a theme change is a re-alias rather than a re-invention.

Numeric suffixes are approximate **lightness on a 0-100 scale**, not Material tonal values. Higher
number = lighter.

### 1.1 Colour primitives

#### 1.1.1 Ink ramp (the near-black neutrals, dark theme spine)

| Primitive | Hex | Sourced from | Currently used for |
|---|---|---|---|
| `ink_00` | `#000000` | mono background (night) | AMOLED ground, scrim base |
| `ink_04` | `#08090B` | `md_theme_surfaceContainerLowest` night | below-ground (rare) |
| `ink_06` | `#0A0B0D` | `md_theme_background` night | P0 ground |
| `ink_09` | `#111316` | `md_theme_surfaceContainerLow` night | sunken block inside a card |
| `ink_11` | `#141619` | `md_theme_surface` night | P1 object |
| `ink_13` | `#1A1D21` | `md_theme_surfaceContainerHigh` night | P2 raised (transient) |
| `ink_15` | `#1E2126` | `md_theme_surfaceVariant` night | field fill (desktop legacy) |
| `ink_17` | `#20242B` | `md_theme_surfaceContainerHighest` night | P3 inset, hairline |
| `ink_22` | `#2A2E36` | `md_theme_outline` night | separator on a dark plane |
| `ink_45` | `#646C7C` | **new** | control boundary at 3:1 (see 3.7) |
| `ink_50` | `#6E7480` | `Brush.OnSurfaceVariantHover` dark | dimmed glyph |
| `ink_68` | `#9BA1AD` | `md_theme_onSurfaceVariant` night | secondary text |
| `ink_96` | `#F2F4F8` | `md_theme_onSurface` night | primary text |

#### 1.1.2 Paper ramp (light theme spine)

| Primitive | Hex | Sourced from |
|---|---|---|
| `paper_100` | `#FFFFFF` | `md_theme_surface` |
| `paper_98` | `#F7F9FD` | `md_theme_surfaceContainerLow` |
| `paper_96` | `#F4F7FC` | `md_theme_background` |
| `paper_95` | `#F1F4FA` | `md_theme_surfaceContainer` |
| `paper_92` | `#EAEFF7` | `md_theme_surfaceContainerHigh` |
| `paper_91` | `#E9EEF7` | `md_theme_surfaceVariant` |
| `paper_89` | `#E3EAF4` | `md_theme_surfaceContainerHighest` |
| `paper_86` | `#DCE3EF` | `md_theme_outlineVariant` |
| `paper_78` | `#C3CCDC` | `md_theme_outline` |
| `paper_50` | `#7D8BA3` | **new** control boundary at 3:1 (see 3.7) |
| `paper_38` | `#54607A` | `md_theme_onSurfaceVariant` |
| `paper_28` | `#3C475E` | `Brush.OnSurfaceVariantHover` light |
| `paper_10` | `#111826` | `md_theme_onSurface` |

#### 1.1.3 Blue (the one accent hue)

| Primitive | Hex | Role it will be aliased to |
|---|---|---|
| `blue_03` | `#00183A` | on-accent, dark theme |
| `blue_20` | `#17325C` | accent container, dark |
| `blue_30` | `#14468F` | on-accent-container, light |
| `blue_45` | `#17469A` | reserved (brand dark, wordmark alt) |
| `blue_50` | `#1E5FC7` | accent, light theme |
| `blue_54` | `#3877E0` | accent pressed |
| `blue_56` | `#3D7EF0` | accent hover |
| `blue_60` | `#4C8DFF` | **accent, dark theme. The brand blue.** |
| `blue_66` | `#5F9AFF` | accent hover, light theme |
| `blue_85` | `#CFE0FF` | on-accent-container, dark |
| `blue_90` | `#D8E4FF` | accent container, light |

#### 1.1.4 Status hues (never actions)

| Primitive | Hex | Meaning |
|---|---|---|
| `green_20` | `#0C3F22` | green container, dark |
| `green_32` | `#065132` | green text on a light green chip |
| `green_45` | `#0B7D4A` | green, light theme |
| `green_60` | `#22C55E` | green, dark theme (подключено, оплачено) |
| `green_88` | `#A6F2C4` | on-green-container, dark |
| `red_15` | `#5C1420` | red container, dark |
| `red_38` | `#9B1B23` | red text on a light red chip |
| `red_45` | `#C42B32` | red, light theme (fill and text) |
| `red_50` | `#D93844` | red pressed |
| `red_55` | `#F04452` | red fill, dark theme |
| `red_65` | `#FF6069` | **red text**, dark theme (see 3.3) |
| `amber_28` | `#6B5000` | amber text on a light amber chip |
| `amber_40` | `#8A6300` | amber, light theme |
| `amber_58` | `#EAB308` | amber, dark theme (истекает, ждёт оплаты) |
| `orange_62` | `#FB923C` | legacy tile fill only. **Not a semantic colour.** Scheduled for deletion with D-5 |

#### 1.1.5 Mono primitives (the third theme)

Mono is not a colour strip. It re-aliases the accent to ink and flattens the hue ramps, keeping
every structural token identical. It exists in both a light and a dark form because
`ThemeOverlay.Mono` composes over either base.

| Primitive | Mono dark | Mono light |
|---|---|---|
| `mono_bg` | `#000000` | `#FFFFFF` |
| `mono_surface` | `#121214` | `#FFFFFF` |
| `mono_surface_high` | `#1B1B1E` | `#EEEEEF` |
| `mono_surface_variant` | `#1E1E20` | `#F1F1F2` |
| `mono_surface_highest` | `#232326` | `#E7E7E9` |
| `mono_on_surface` | `#F4F4F5` | `#111214` |
| `mono_on_surface_variant` | `#B0B0B4` | `#5A5A5E` |
| `mono_outline` | `#38383C` | `#D2D2D6` |
| `mono_outline_variant` | `#28282C` | `#E6E6E8` |
| `mono_outline_control` | `#6A6A6E` | `#767679` |
| `mono_ink` | `#FFFFFF` | `#111214` |
| `mono_on_ink` | `#111214` | `#FFFFFF` |
| `mono_ink_container` | `#2A2A2E` | `#E6E6E8` |
| `mono_on_ink_container` | `#FFFFFF` | `#111214` |

`mono_outline_control` is **new** on both platforms and both variants: it is the only value that had
no mono equivalent and it is required to keep input fields at the 3:1 non-text floor when the accent
is greyscale.

### 1.2 Opacity primitives

The state layer is expressed as alpha over a plane, never as a separately picked grey. One list,
used by both platforms.

| Primitive | Value | Android hex prefix | Use |
|---|---|---|---|
| `alpha_disabled` | 0.38 | `61` | Disabled content. WCAG 1.4.3 exempts inactive components; do not "fix" the ratio |
| `alpha_state_hover` | 0.06 | `0F` | Hover overlay (desktop only) |
| `alpha_state_press` | 0.10 | `1A` | Android ripple / pressed overlay |
| `alpha_selected` | 0.12 | `1F` | Selected-row fill |
| `alpha_tile` | 0.20 | `33` | Coloured icon-tile fill |
| `alpha_chip` | 0.18 | `2E` | Status-chip fill |
| `alpha_scrim` | 0.60 | `99` | Dialog / sheet scrim |
| `alpha_skeleton_lo` | 0.35 | `59` | Skeleton pulse floor |
| `alpha_skeleton_hi` | 0.65 | `A6` | Skeleton pulse ceiling |

### 1.3 Dimension primitives

**These are the only numbers.** 6, 10, 14, 18, 22 (as a spacing value), 26, 28, 34, 42, 60, 68, 76,
88 and every other value currently in the layouts are defects.

| Group | Primitives |
|---|---|
| Space | `0, 4, 8, 12, 16, 24, 32, 40, 48, 56, 64` |
| Radius | `12, 16, 20, 24, 100` |
| Stroke | `1, 2, 3` |
| Component size | `18, 20, 22, 24, 28, 36, 40, 44, 48, 52, 56, 64, 72, 80, 120, 152, 176, 720` |

The "component size" group is a closed list of measured object sizes (a glyph, a tile, a row, a CTA,
a disc, the content cap). It is not a scale you may interpolate inside. 22 appears there as the tile
glyph size and nowhere as a margin.

### 1.4 Type primitives

| Primitive | Value | Note |
|---|---|---|
| `font_brand_file` | `res/font/spacegrotesk.ttf` / `Assets/Fonts/SpaceGrotesk.ttf` | Byte-identical binaries. Variable, `wght` 300-700, **default instance 300**, internal name `Space Grotesk Light`, **zero Cyrillic codepoints** |
| `font_ui_file` | pending decision D-1 | Must cover U+0400-U+04FF. Recommendation: Golos Text (OFL, variable). See 3.8.1 |
| `size_11 … size_34` | `11, 12, 13, 14, 16, 24, 34` | Seven sizes. 15 does not exist. 18 does not exist. 20 does not exist as a text size |
| `weight_400 / 500 / 700` | 400, 500, 700 | Three weights. **600 does not exist. 300 does not exist in the UI. Italic does not exist** (the file carries no italic master) |
| `line_14 … line_40` | `14, 16, 18, 20, 28, 40` | Line heights, all on or near the 4-grid |
| `track_tight2 … track_open4` | `-0.02, -0.01, 0, +0.01, +0.02, +0.04` em | Six tracking values |
| `feat_tabular` | `"tnum" on, "lnum" on` / `tnum,lnum` | Tabular lining figures |
| `feat_slashed_zero` | `"zero" on` / `zero` | Technical figures only, never currency (D-3) |

### 1.5 Motion primitives

Already 1:1 across platforms. Keep them so.

| Primitive | ms | Android | Desktop |
|---|---|---|---|
| `dur_0` | 0 | (reduced-motion collapse) | `Motion.Dur.Instant` |
| `dur_90` | 90 | `@integer/motion_press_in` | `Dur.PressIn` / `0:0:0.09` |
| `dur_150` | 150 | (reverse of reveal, computed) | `Dur.Exit` / `0:0:0.15` |
| `dur_160` | 160 | `@integer/motion_press_out` | `Dur.PressOut` / `0:0:0.16` |
| `dur_165` | 165 | (reverse of state, computed) | `0:0:0.165` |
| `dur_200` | 200 | n/a | `Dur.Shell` / `0:0:0.20` |
| `dur_220` | 220 | `@integer/motion_state` | `Dur.State` / `0:0:0.22` |
| `dur_225` | 225 | (reverse of reveal, computed) | `0:0:0.225` |
| `dur_300` | 300 | `@integer/motion_reveal` | `Dur.Reveal` / `0:0:0.30` |
| `dur_450` | 450 | (auth hand-off) | `Dur.Slow` / `0:0:0.45` |
| `dur_600` | 600 | `@integer/motion_emphasis` | `Dur.Emphasis` / `0:0:0.60` |
| `dur_40` | 40 | `@integer/motion_stagger` | `Dur.Stagger` / `0:0:0.04` |

| Curve primitive | Control points | Android | Desktop |
|---|---|---|---|
| `ease_out_quart` | 0.25, 1, 0.5, 1 | `@interpolator/ease_out_quart` | `Ease.OutQuart` |
| `ease_out_quint` | 0.22, 1, 0.36, 1 | `@interpolator/ease_out_quint` | `Ease.OutQuint` |
| `ease_standard` | 0.2, 0, 0, 1 | `@interpolator/ease_standard` | `Ease.Standard` |
| `ease_out_expo` | 0.16, 1, 0.3, 1 | **missing, must be added** as `res/interpolator/ease_out_expo.xml` | `Ease.OutExpo` |

There is no `ease_in`, no `linear` (outside a genuine indeterminate loop), no bounce, no elastic, no
spring. If you need a fifth curve, you do not.

---

## 2. Layer 2 - Semantic tokens

### 2.1 The plane model

Four planes with fixed meanings, from `03-direction.md` 4.1. Depth is **tone, never shadow**.

| Plane | Semantic token | What it is allowed to be | Elevation dp | Shadow |
|---|---|---|---|---|
| **P0 Ground** | `color_background` | The screen, the toolbar, the bottom nav, the nav rail, the scroll container | 0 | none |
| **P1 Object** | `color_surface` | A card, a sheet body, a dialog body, a flyout body | 0 | none |
| **P2 Raised** | `color_surface_raised` | **Transient only**: desktop hover, pressed row, drag. Nothing is P2 at rest | 0 | none |
| **P3 Inset** | `color_surface_inset` | Input field, chip fill, neutral icon tile, selected row, skeleton bar, meter track | 0 | none |

**Plane budget:** at most two planes stacked above ground, three counting ground.
Legal: `P0 -> P1 card -> P3 chip`. Illegal: `P0 -> P1 -> P1` (nested cards), `P0 -> P1 -> P2 -> P3`.

**Measured plane deltas** (dark): P0 to P1 = 1.09:1, P1 to P2 = 1.07:1, P0 to P2 = 1.16:1. These are
deliberately below the 3:1 non-text floor because a plane is **not** a UI-component boundary: it is
a background. Anything that must be *identified* as a control (a field, an outlined button, a
segmented track) carries `color_outline_control` at 3:1 and does not rely on the plane delta. This
distinction is the single most-missed accessibility point in the current build.

### 2.2 Semantic colour, all themes

Dark is the default and is the column you design against. Light and both mono variants ship and are
checked on every component before it is done.

| Semantic token | Android attr | Desktop key | **Dark** | **Light** | **Mono dark** | **Mono light** |
|---|---|---|---|---|---|---|
| `color_background` | `?android:attr/colorBackground` | `Brush.Bg` | `#0A0B0D` | `#F4F7FC` | `#000000` | `#FFFFFF` |
| `color_on_background` | `?attr/colorOnBackground` | `Brush.OnBg` | `#F2F4F8` | `#111826` | `#F4F4F5` | `#111214` |
| `color_surface` | `?attr/colorSurface` | `Brush.Surface` | `#141619` | `#FFFFFF` | `#121214` | `#FFFFFF` |
| `color_surface_sunken` | `?attr/colorSurfaceContainerLow` | `Brush.SurfaceLow` | `#111316` | `#F7F9FD` | `#0E0E10` | `#FAFAFB` |
| `color_surface_raised` | `?attr/colorSurfaceContainerHigh` | `Brush.SurfaceHigh` | `#1A1D21` | `#EAEFF7` | `#1B1B1E` | `#EEEEEF` |
| `color_surface_inset` | `?attr/colorSurfaceContainerHighest` | `Brush.SurfaceHighest` | `#20242B` | `#E3EAF4` | `#232326` | `#E7E7E9` |
| `color_surface_variant` | `?attr/colorSurfaceVariant` | `Brush.SurfaceVariant` | `#1E2126` | `#E9EEF7` | `#1E1E20` | `#F1F1F2` |
| `color_on_surface` | `?attr/colorOnSurface` | `Brush.OnSurface` | `#F2F4F8` | `#111826` | `#F4F4F5` | `#111214` |
| `color_on_surface_variant` | `?attr/colorOnSurfaceVariant` | `Brush.OnSurfaceVariant` | `#9BA1AD` | `#54607A` | `#B0B0B4` | `#5A5A5E` |
| `color_on_surface_dim` | `?attr/colorOnSurfaceDim` (new) | `Brush.OnSurfaceDim` (renames `Brush.OnSurfaceVariantHover`) | `#6E7480` | `#3C475E` | `#8A8A90` | `#3C3C40` |
| `color_accent` | `?attr/colorPrimary` | `Brush.Accent` | `#4C8DFF` | `#1E5FC7` | `#FFFFFF` | `#111214` |
| `color_on_accent` | `?attr/colorOnPrimary` | `Brush.OnAccent` | `#00183A` | `#FFFFFF` | `#111214` | `#FFFFFF` |
| `color_accent_hover` | (desktop only) | `Brush.AccentHover` | `#3D7EF0` | `#3D7EF0` | `#E6E6E8` | `#2A2A2E` |
| `color_accent_press` | (desktop only) | `Brush.AccentPress` | `#3877E0` | `#3877E0` | `#D2D2D6` | `#3A3A3D` |
| `color_accent_container` | `?attr/colorPrimaryContainer` | `Brush.AccentContainer` | `#17325C` | `#D8E4FF` | `#2A2A2E` | `#E6E6E8` |
| `color_on_accent_container` | `?attr/colorOnPrimaryContainer` | `Brush.OnAccentContainer` | `#CFE0FF` | `#14468F` | `#FFFFFF` | `#111214` |
| `color_success` | `?attr/colorTertiary` | `Brush.Green` | `#22C55E` | `#0B7D4A` | `#F4F4F5` | `#111214` |
| `color_success_text` | `?attr/pingGood` | `Brush.Ping.Good` | `#22C55E` | `#065132` | `#F4F4F5` | `#111214` |
| `color_success_container` | `?attr/colorTertiaryContainer` | `Brush.GreenContainer` | `#0C3F22` | `#D3E8DE` | `#232326` | `#E7E7E9` |
| `color_destructive` | `?attr/colorError` | `Brush.Red` | `#F04452` | `#C42B32` | `#F4F4F5` | `#111214` |
| `color_destructive_press` | (desktop only) | `Brush.RedPressed` | `#D93844` | `#9B1B23` | `#B0B0B4` | `#3A3A3D` |
| `color_destructive_text` | `?attr/pingBad` | `Brush.RedText` | `#FF6069` | `#C42B32` | `#F4F4F5` | `#111214` |
| `color_destructive_container` | `?attr/colorErrorContainer` | `Brush.RedContainer` | `#5C1420` | `#F4D9DA` | `#232326` | `#E7E7E9` |
| `color_warning` | `?attr/warning` (new; replaces `iconTintYellow`) | `Brush.Amber` | `#EAB308` | `#8A6300` | `#B0B0B4` | `#5A5A5E` |
| `color_warning_text` | (new attr `warningText`) | `Brush.AmberText` | `#EAB308` | `#6B5000` | `#F4F4F5` | `#111214` |
| `color_outline` | `?attr/colorOutline` | `Brush.Outline` | `#2A2E36` | `#C3CCDC` | `#38383C` | `#D2D2D6` |
| `color_outline_variant` | `?attr/colorOutlineVariant` | `Brush.OutlineVariant` | `#20242B` | `#DCE3EF` | `#28282C` | `#E6E6E8` |
| `color_outline_control` | (new attr `colorOutlineControl`) | `Brush.OutlineControl` | `#646C7C` | `#7D8BA3` | `#6A6A6E` | `#767679` |
| `color_scrim` | `?attr/colorScrim` @ 60% | `Brush.Scrim` | `#000000` 0.6 | `#000000` 0.6 | `#000000` 0.6 | `#000000` 0.6 |
| `color_tile_neutral` | `@color/icon_tile_neutral` | `Brush.Tile.Neutral` | `#20242B` | `#E3EAF4` | `#232326` | `#E7E7E9` |
| `color_tile_glyph_neutral` | `@color/icon_glyph_neutral` | `Brush.Tile.Glyph` | `#9BA1AD` | `#54607A` | `#B0B0B4` | `#5A5A5E` |
| `color_tile_accent` | `?attr/iconTileBgBlue` | `Brush.Tile.Blue` | `#4C8DFF` @20% | `#1E5FC7` @20% | `#232326` | `#E7E7E9` |
| `color_tile_destructive` | `?attr/iconTileBgRed` | `Brush.Tile.Red` | `#F04452` @20% | `#C42B32` @20% | `#232326` | `#E7E7E9` |
| `color_selected_fill` | `@color/state_selected` | `Brush.SelectedFill` | `#4C8DFF` @12% | `#1E5FC7` @12% | `#FFFFFF` @12% | `#111214` @12% |
| `color_state_hover` | (n/a on Android) | `Brush.Hover` | `#FFFFFF` @6% | `#000000` @6% | `#FFFFFF` @6% | `#000000` @6% |
| `color_state_press` | `?attr/colorPrimary` @10% ripple | (scale only, no overlay) | `#4C8DFF` @10% | `#1E5FC7` @10% | `#FFFFFF` @10% | `#111214` @10% |
| `color_skeleton` | `@color/skeleton` | `Brush.Skeleton` | `#20242B` | `#E3EAF4` | `#232326` | `#E7E7E9` |

**Deleted colour tokens** (they encode a category system that does not exist, per D-5 and F1):
`icon_purple`, `icon_tile_purple`, `Brush.Tile.Purple`, `icon_orange`, `icon_tile_orange`,
`Brush.Tile.Orange`, `icon_green`, `icon_tile_green`, `Brush.Tile.Green`, `icon_yellow`,
`icon_tile_yellow`, `Brush.Tile.Yellow`, `Brush.Icon.Orange`, `Brush.Icon.Yellow`, `chip_json_bg`,
`chip_json_text`, `brand_cream`, `divider_color_light`, `colorWhite`, `color_upload`,
`color_download`, `Brush.HomeGradient`, `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`.
The icon-tile category system that survives is exactly two entries plus neutral:
**accent** (the one lit row on a screen, if any), **destructive** (delete / unlink), **neutral**
(everything else). That is the whole system.

### 2.3 Measured contrast (WCAG 2.1, computed, not estimated)

Floors: body text 4.5:1, large text (>=24sp, or >=16sp at weight 700) 3:1, icons and control
boundaries 3:1, placeholder text 4.5:1.

**Dark theme**

| Pair | Ratio | Verdict |
|---|---|---|
| `on_surface` `#F2F4F8` on `background` `#0A0B0D` | **17.88** | AAA |
| `on_surface` on `surface` `#141619` | **16.46** | AAA |
| `on_surface` on `surface_raised` `#1A1D21` | **15.36** | AAA |
| `on_surface` on `surface_inset` `#20242B` | **14.14** | AAA |
| `on_surface_variant` `#9BA1AD` on `background` | **7.59** | AAA |
| `on_surface_variant` on `surface` | **6.99** | AA body |
| `on_surface_variant` on `surface_inset` | **6.00** | AA body. This is the placeholder floor and it clears it |
| `accent` `#4C8DFF` on `background` | **6.15** | AA body |
| `accent` on `surface` | **5.66** | AA body |
| `accent` on `surface_inset` | **4.87** | AA body, tight. Do not put accent text on an inset chip |
| `on_accent` `#00183A` on `accent` | **5.51** | AA body. The primary button label |
| `success` `#22C55E` on `background` | **8.64** | AAA |
| `success` on `surface` | **7.95** | AAA |
| `destructive` `#F04452` on `surface` | **4.88** | AA, only just. **Fill and glyph only** |
| `destructive_text` `#FF6069` on `background` | **6.68** | AA body. Use this for error text |
| `destructive_text` on `surface` | **6.15** | AA body |
| `destructive_text` on `surface_inset` | **5.29** | AA body |
| `warning` `#EAB308` on `background` | **10.27** | AAA |
| `on_accent_container` `#CFE0FF` on `accent_container` `#17325C` | **9.57** | AAA |
| `outline_control` `#646C7C` on `background` | **3.73** | Passes the 3:1 control floor |
| `outline_control` on `surface` | **3.43** | Passes |
| `tile_glyph_neutral` `#9BA1AD` on `tile_neutral` `#20242B` | **6.00** | Passes the 3:1 icon floor with headroom |
| accent glyph on `tile_accent` (20% over ground, `#17253D`) | **4.79** | Passes the 3:1 icon floor |
| `on_surface` on `selected_fill` over ground (`#121B2A`) | **15.68** | AAA |

**Light theme**

| Pair | Ratio | Verdict |
|---|---|---|
| `on_surface` `#111826` on `background` `#F4F7FC` | **16.54** | AAA |
| `on_surface` on `surface` `#FFFFFF` | **17.76** | AAA |
| `on_surface_variant` `#54607A` on `surface` | **6.30** | AA body |
| `on_surface_variant` on `background` | **5.87** | AA body |
| `on_surface_variant` on `surface_inset` `#E3EAF4` | **5.21** | AA body |
| `accent` `#1E5FC7` on `surface` | **5.97** | AA body |
| `accent` on `background` | **5.56** | AA body |
| `on_accent` `#FFFFFF` on `accent` | **5.97** | AA body |
| `success` `#0B7D4A` on `surface` | **5.19** | AA body |
| `success` on `background` | **4.83** | AA body |
| `success_text` `#065132` on green chip fill `#D3E8DE` | **7.34** | AAA. **This is why light chips need their own text token** |
| `destructive` `#C42B32` on `surface` | **5.62** | AA body |
| `destructive_text` `#9B1B23` on red chip fill `#F4D9DA` | **6.13** | AAA |
| `warning` `#8A6300` on `surface` | **5.43** | AA body |
| `warning_text` `#6B5000` on amber chip fill `#FBF1D3` | **6.72** | AAA |
| `on_accent_container` `#14468F` on `accent_container` `#D8E4FF` | **7.15** | AAA |
| `outline_control` `#7D8BA3` on `surface` | **3.45** | Passes the 3:1 control floor |
| `outline_control` on `background` | **3.21** | Passes |
| `tile_glyph_neutral` `#54607A` on `tile_neutral` `#E3EAF4` | **5.21** | Passes |

**Mono, both variants**

| Pair | Ratio |
|---|---|
| mono dark `on_surface` `#F4F4F5` on `background` `#000000` | **19.11** |
| mono dark `on_surface` on `surface` `#121214` | **17.02** |
| mono dark `on_surface_variant` `#B0B0B4` on `surface` | **8.66** |
| mono dark `on_accent` `#111214` on `accent` `#FFFFFF` | **18.74** |
| mono dark `outline_control` `#6A6A6E` on `background` | **3.90** |
| mono light `on_surface` `#111214` on `surface` `#FFFFFF` | **18.74** |
| mono light `on_surface_variant` `#5A5A5E` on `surface` | **6.87** |
| mono light `outline_control` `#767679` on `surface` | **4.53** |

**The two rules that follow from the numbers**

1. `#F04452` is a fill and a glyph. Error **text** on dark is `#FF6069`. Never introduce a third red.
2. On light, a status chip's text is **not** the status colour. Green text on a green chip is
   4.05:1 and fails. Use `color_success_text` / `color_destructive_text` / `color_warning_text`,
   which are the darkened variants defined for exactly this. On dark the chip fill is dark enough
   that the status colour itself clears AA (green 6.60, red-text 5.65, amber 7.51) and no second
   token is needed, which is why the dark and light columns of those three tokens differ.

### 2.4 State-layer semantics

The eight states from `00-rules.md` 7.1, expressed as tokens.

| State | Android | Desktop | Duration / curve |
|---|---|---|---|
| **Default** | ramp style + `?attr/...` | class + `{DynamicResource ...}` | n/a |
| **Hover** | does not exist. Do not design for it | `color_state_hover` overlay: white 6% (dark), black 6% (light). **Whole row/control, not the label** | `dur_150` `ease_standard` |
| **Focus** | keyboard and TV only: 2dp `color_accent` ring, 2dp offset | **mandatory, always**: 2px `color_accent` ring, 2px offset, radius = control radius + 2 | `dur_150` `ease_standard` |
| **Pressed** | ripple `color_state_press` + `stateListAnimator` scale **0.97** | scale **0.97**, no ripple | in `dur_90` `ease_out_quart`, out `dur_160` `ease_out_quint` |
| **Selected** | `color_selected_fill` + weight 500->700 + a state glyph. **Two channels minimum** | same | `dur_220` `ease_standard` |
| **Disabled** | content `alpha_disabled` 0.38, no ripple, `isEnabled=false` | `Opacity` 0.38, no hand cursor, `IsEnabled=False` | instant |
| **Loading** | control keeps its exact size, label swaps for a 20dp indeterminate indicator in `color_on_accent`; control disabled | same, `Ellipse.Spinner` | appears after 300 ms |
| **Error** | inline below, `color_destructive_text`, 12sp, plus 1dp `color_destructive` field border | same | `dur_220` `ease_standard` |
| **Success** | 220 ms tint to `color_success` plus the word. No confetti, no checkmark flourish | same | `dur_220` `ease_standard` |

**State priority**, highest first: `disabled` > `loading` > `pressed` > `focus` > `selected` >
`hover` > `default`. A disabled control shows no hover and no focus ring.

**Two notes that are corrections to the current build:**

- Press scale is **0.97 everywhere**. Today `res/anim/press_scale.xml` uses 0.96,
  `res/anim/nav_press.xml` uses 0.92 with hard-coded 100/120 ms, and desktop uses 0.97 at 120 ms.
  Three values for one gesture. Fix all four to 0.97 / `dur_90` in / `dur_160` out.
- Desktop hover was a **32% black scrim**. Measured on the near-black ground that produces a 1.16:1
  delta, which is invisible: `#0A0B0D` darkened 32% is `#070709`. The token is therefore redefined
  as a **6% white overlay on dark and a 6% black overlay on light**, which is a visible, plane-
  independent step in both themes and still reads as "a slight change", not a glow. See the
  change-control row in section 9.

### 2.5 Spacing semantics

One scale. One gutter. A rhythm, not a drone.

| Semantic token | Android | Desktop | Value | Used between |
|---|---|---|---|---|
| `space_hair` | `@dimen/space_4` | `Space.4` | 4 | Glyph and its label; title and subtitle; chip inner vertical |
| `space_tight` | `@dimen/space_8` | `Space.8` | 8 | Two tightly related siblings; chip inner horizontal; minimum gap between two touch targets |
| `space_snug` | `@dimen/space_12` | `Space.12` | 12 | Row inner vertical; icon tile to text; stacked text buttons |
| `space_base` | `@dimen/space_16` | `Space.16` | 16 | **Screen gutter**; card padding; default block separation |
| `space_section` | `@dimen/space_24` | `Space.24` | 24 | Between distinct sections; the space that replaces a divider under a section header |
| `space_major` | `@dimen/space_32` | `Space.32` | 32 | Max twice per screen: after a hero, before a bottom CTA |
| `space_gutter` | `@dimen/screen_gutter` | `Size.Gutter` / `Gutter` | 16 | The horizontal screen inset |
| `space_gutter_wide` | `@dimen/screen_gutter` in `values-sw600dp` | `Size.GutterWide` | 24 | `sw600dp` and desktop window >= 1000 px |
| `space_text_origin` | derived: 16 + 40 + 12 | derived | **68** | Where every row title and every hairline starts |
| `size_content_max` | `@dimen/content_max_width` | `Size.ContentMax` | 720 | Content column cap on tablet and desktop |

**Rhythm rule**: parts of one object 4-12; objects 16; sections 24; the two major breaks 32. A screen
where every vertical gap is 16 has no hierarchy and fails the squint test. This is the single most
common defect in the current build.

### 2.6 Radius semantics - the shape lock

**The lock has changed.** `00-rules.md` 3.2 says "buttons are pill". The owner rejected the capsule
button on desktop in writing (`Assets/GlobalStyles.axaml:9` - "Владелец отклонил эти капсулы"), and
under the precedence order in `00-rules.md` 0.1 the owner's word outranks the rule file. The lock is
therefore re-derived below, and the change-control rows are in section 9.

| Semantic token | Android | Desktop | Value | Applies to. Nothing else. |
|---|---|---|---|---|
| `radius_fitting` | `@dimen/radius_chip` = `@dimen/radius_tile` | `Radius.Chip` = `Radius.Tile` | **12** | Chips, badges, icon tiles, flag tiles, avatar squares, segmented thumb, skeleton bars |
| `radius_control` | `@dimen/radius_control` **(new)** | `Radius.Control` (renames `Radius.Button`) | **16** | Buttons of every variant, input fields, search field, price option, segmented track, list-row hover shape |
| `radius_object` | `@dimen/radius_card` | `Radius.Card` | **20** | Cards, dialogs, flyout bodies, sheet body, empty-state container |
| `radius_sheet` | `@dimen/radius_sheet` | `Radius.Sheet` | **24 top only** | Bottom sheet / drawer lip. `24,24,0,0` |
| `radius_round` | `@dimen/radius_pill` | `Radius.Pill` | **100** | Genuinely circular or stadium objects only: connect disc, avatar circle, status dot, page dot, progress-meter ends, sheet drag handle |

**Retired:** `Radius.Search` (14), `Radius.Traffic` (8), and every literal `26dp` / `22dp` / `20dp`
/ `18dp` / `14dp` `app:cornerRadius` in the Android layouts. A meter bar uses `radius_round`, which
clamps to half its own height and gives true round ends without a bespoke token.

**Concentricity:** an inner radius equals the outer radius minus the padding between them. A
segmented track at 16 with 4 padding holds a thumb at 12. A card at 20 with 16 padding holds a
button at 16 and that reads correctly because the button does not touch the card edge. Never place a
20 radius inside a 16 radius.

### 2.7 Size semantics

| Semantic token | Android | Desktop | Value |
|---|---|---|---|
| `size_glyph_chip` | `@dimen/glyph_16` | `Size.GlyphChip` | 16 |
| `size_glyph_inline` | `@dimen/glyph_20` | `Size.GlyphInline` | 20 |
| `size_glyph_tile` | `@dimen/tile_glyph` | `Size.Glyph` | 22 |
| `size_glyph_nav` | `@dimen/image_size_dp24` | `Size.GlyphNav` | 24 |
| `size_flag` | `@dimen/flag_size` | `Size.FlagTile` | 28 |
| `size_avatar_chip` | `@dimen/avatar_chip` | `Size.AvatarChip` | 36 |
| `size_tile` | `@dimen/tile_size` | `Size.Tile` | 40 |
| `size_icon_button` | `@dimen/icon_button` | `Size.IconButton` | 40 |
| `size_touch_min` | `@dimen/view_height_dp48` | `Size.TouchMin` | 48 |
| `size_field` | `@dimen/field_height` | `Size.Field` | 52 |
| `size_cta` | `@dimen/cta_height` | `Size.CtaTall` | 52 |
| `size_row` | `@dimen/row_min_height` | `Size.Row` | 56 |
| `size_toolbar` | `@dimen/toolbar_height` | `Size.SubToolbar` | 56 |
| `size_nav_bar` | `@dimen/nav_bar_height` | `Size.NavRailItem` | 64 |
| `size_empty_icon` | `@dimen/empty_icon` | `Size.EmptyIcon` | 64 |
| `size_shield` | `@dimen/shield_glyph` | `Size.ShieldGlyph` | 80 |
| `size_sub_card` | `@dimen/sub_card_height` | `Size.SubCard` | 152 |
| `size_connect_disc` | `@dimen/connect_disc` | `Size.ConnectDisc` | 176 |
| `dot` / `dot_active` / `dot_gap` | `@dimen/dot_size` / `_active` / `dot_gap` | `Dot` / `Dot.Active` / `Dot.Gap` | 6 / 8 / 8 |
| `size_handle_w` / `size_handle_h` | `@dimen/sheet_handle_w` / `_h` | `Size.SheetHandleW` / `H` | 36 / 4 |
| `size_meter` | `@dimen/meter_height` | `Size.Meter` | 6 |

**Deleted sizes:** `Size.HeroFrame` 230, `Size.ConnectArc` 212, `Size.TrafficPill` 160,
`view_height_dp36`, `view_height_dp120`, `view_height_dp160`, `padding_spacing_dp4/8/16`
(duplicates of `space_*`), `image_size_dp24` is kept but renamed in spirit to `size_glyph_nav`.

### 2.8 Stroke semantics

| Semantic token | Android | Desktop | Value | Colour | Where |
|---|---|---|---|---|---|
| `stroke_hairline` | `@dimen/stroke_hairline` | `Stroke.Hairline` | 1 | `color_outline_variant` | Row separators, card border |
| `stroke_control` | `@dimen/stroke_control` | `Stroke.Control` | 1 | `color_outline_control` | Input field, outlined button, segmented track. **This is the 3:1 stroke** |
| `stroke_focus` | `@dimen/stroke_focus` | `Stroke.Focus` | 2 | `color_accent` | The focus ring, 2 px offset |
| `stroke_emphasis` | `@dimen/stroke_emphasis` | `Stroke.Emphasis` | 2 | `color_accent` | Selected indicator, connect ring when connected |
| `stroke_ring` | `@dimen/stroke_ring` | `Stroke.Ring` | 3 | `color_outline` idle / `color_accent` active | Connect disc ring |

A card's border is `stroke_hairline` in `color_outline_variant`, which measures 1.16:1 against the
surface. That is correct and deliberate: a card boundary is decorative structure, not a control
boundary, and WCAG 1.4.11 does not apply to it. An input's border is `stroke_control` in
`color_outline_control` at 3.43:1 because an input **is** a control and 1.4.11 does apply.

### 2.9 Type roles - the ramp

Ten roles. Nothing outside them. A layout that sets a size directly is a defect.

| Role | Android style | Desktop class | Face | Size | Weight | Line height (ratio) | Tracking (em / px) | Default colour |
|---|---|---|---|---|---|---|---|---|
| **Display** | `TextAppearance.App.Display` | `TextBlock.Display` | brand | 34sp/34px | 700 | 40 (1.18) | -0.02 / -0.68 | `color_on_surface` |
| **Headline** | `TextAppearance.App.Headline` | `TextBlock.Headline` | ui | 24sp/24px | 700 | 28 (1.17) | -0.01 / -0.24 | `color_on_surface` |
| **Title** | `TextAppearance.App.Title` | `TextBlock.Title` | ui | 16sp/16px | 700 | 20 (1.25) | 0 / 0 | `color_on_surface` |
| **Title medium** | `TextAppearance.App.Title.Medium` | `TextBlock.TitleMedium` | ui | 16sp/16px | 500 | 20 (1.25) | 0 / 0 | `color_on_surface` |
| **Body** | `TextAppearance.App.Body` | `TextBlock.Body` | ui | 14sp/14px | 400 | 20 (1.43) | +0.01 / +0.14 | `color_on_surface` |
| **Subtitle** | `TextAppearance.App.Subtitle` | `TextBlock.Subtitle` | ui | 13sp/13px | 400 | 18 (1.38) | +0.01 / +0.13 | `color_on_surface_variant` |
| **Caption** | `TextAppearance.App.Caption` | `TextBlock.Caption` | ui | 12sp/12px | 400 | 16 (1.33) | +0.02 / +0.24 | `color_on_surface_variant` |
| **Chip** | `TextAppearance.App.Chip` | `TextBlock.Chip` | brand | 11sp/11px | 500 | 14 (1.27) | +0.04 / +0.44 | contextual |
| **Numeric** | `TextAppearance.App.Numeric` | `TextBlock.Numeric` | brand | inherits | **500** | inherits | 0 / 0 | `color_on_surface` |
| **Section header** | `SettingsSectionLabel` | `TextBlock.SectionHeader` | ui | 16sp/16px | 700 | 20 (1.25) | 0 / 0 | `color_on_surface` |
| **Wordmark** | `ToolbarBrandTitle` | `TextBlock.Wordmark` | brand | 20sp/20px | 700 | 24 (1.20) | -0.01 / -0.20 | `color_on_background` |

Adjacent-step ratios: 34/24 = 1.42, 24/16 = 1.50, 16/14 = 1.14, 14/13 = 1.08, 13/12 = 1.08,
12/11 = 1.09. Wide at the top (real hierarchy where it matters), tight at the bottom (a product ramp,
per `typeset.md`: 1.125-1.2 typical). **Do not add a step.** 15sp does not exist. 18sp does not exist.

**The wordmark is a new named role.** It was previously `ToolbarBrandTitle` on Android and nothing at
all on desktop, where the wordmark used ad-hoc sizes. It exists so that the one place the brand face
sets Latin letters is a role and not a one-off.

#### 2.9.1 Two faces, split by script

| | Token | Carries | Weights |
|---|---|---|---|
| **Brand / figure face** | `font_brand` (`@font/space_grotesk`, `Font.Brand`) | Digits, units, currency, Latin technical tokens (`VLESS`, `Reality`, `WS`, `TCP`, hosts, ports), the wordmark, chip labels | 500, 700 |
| **UI face** | `font_ui` (`@font/ui_sans`, `Font.Ui`) | Every Russian string: headlines, titles, labels, buttons, subtitles, captions, errors, empty states | 400, 500, 700 |

**This is a change to the ramp above.** Headline, Title, Title medium and Section header were all
`@font/space_grotesk`. The vendored binary contains **zero Cyrillic codepoints**, so those setters
have been silently no-ops for every Russian string in the product, handing the choice to Roboto on
Android and to whatever the OS picks on desktop. The ramp above assigns them `font_ui`, which is what
they already render as; the difference is that it becomes declared and identical on all four
operating systems.

Until decision **D-1** lands, `font_ui` resolves to the pinned platform face:

- Android: `res/font/ui_sans.xml` containing three `<font>` entries pointing at `sans-serif` at
  weights 400 / 500 / 700.
- Desktop: `<FontFamily x:Key="Font.Ui">Segoe UI, Inter, Noto Sans, DejaVu Sans, sans-serif</FontFamily>`
  as an explicit stack, so Windows, Linux and macOS stop rendering three different products.

Recommendation for D-1, in order: **Golos Text** (OFL, variable, Cyrillic-first, humanist-leaning -
genuine contrast against the geometric figures), then Onest, then the pinned platform stack above.
Do not pick Inter by default; it is the reflex.

**`Font.Grotesk` is renamed `Font.Brand`** and must be stripped from the three blanket setters at
`GlobalStyles.axaml:257-265` (`TopLevel`, `TextBlock`, `TemplatedControl`), which currently apply the
Cyrillic-free brand face to every string in the desktop app. Those three setters become `Font.Ui`.

#### 2.9.2 The variable-font verification gate (P1, do first)

`res/font/space_grotesk.xml` declares 400 / 500 / 700 against one variable file whose **default
instance is `wght` 300**, with no `android:fontVariationSettings` anywhere in the repo. Every brand
run on Android may be rendering at Light 300 while every style file looks correct.

1. Build a debug screen with `0123456789` at 34sp in `TextAppearance.App.Display`, beside the same
   string with `android:fontVariationSettings="'wght' 700"`.
2. Screenshot on API 28+ and on API 26.
3. Compare stem widths.

If they differ: add `android:fontVariationSettings="'wght' 400" / "'wght' 500" / "'wght' 700"` to the
three entries in `res/font/space_grotesk.xml` (API 28+), and ship baked static instances as the
fallback family for API 26-27.

Independently and unconditionally: `TextAppearance.App.Numeric` declares **no weight at all** today
(`res/values/styles.xml:122-127`) while the ramp specifies 500. Add
`<item name="android:textFontWeight">500</item>`.

#### 2.9.3 Rules that come with the ramp

1. `sp` on Android for every text size, never `dp`. Layouts survive font scale 200% or they are P1
   accessibility defects.
2. Real weights only: 400 / 500 / 700. No `android:textStyle="bold"` on a brand-face style (that is
   synthetic bold on a file that carries genuine masters). No 600. No italic.
3. Tracking is what the ramp says. No per-screen tuning. Tracking above +0.02em at a heading size is
   the eyebrow tell and is banned.
4. Measure: any paragraph past one line caps at roughly 60 characters on phone, 65-70 on desktop.
   On desktop that is a `MaxWidth` on the `TextBlock`, not on the panel.
5. Truncation is a last resort. Prefer `maxLines="2"` and wrap. A primary label that truncates means
   the Russian copy is too long: rewrite it. User content (server remarks, Telegram names) may
   ellipsise at the **end**, never in the middle.
6. Section headers are sentence-case bold at 16sp/700. Never `textAllCaps`.

### 2.10 Numerals

Every quantity in the product is a Numeric role. This is signature one of the direction and it is
mechanical.

| Rule | Value |
|---|---|
| Features, always | `tnum` on, `lnum` on |
| Slashed zero | `zero` **on** for technical figures (latency, ports, traffic, identifiers, uptime); **off** for money (D-3) |
| Tabular advance | 620/1000 em. A five-digit figure at 34sp reserves `5 x 0.620 x 34 = 105.4sp` |
| Alignment | Every numeric column right-aligned, width reserved for the maximum expected digit count |
| Thousands separator | U+2009 THIN SPACE |
| Column padding when digit count changes | U+2007 FIGURE SPACE (exactly one digit wide in this font) |
| Currency | U+00A0 then U+20BD. `1 290 ₽`. Never `RUB`, never `руб.` |
| Decimal separator | comma. `12,4`, never `12.4` |
| Canonical formats | `12,4 ГБ` · `24,8 Мбит/с` · `48 мс` · `02:14:07` · `3 / 5` · `1 290 ₽` |
| Placeholders and mocks | realistic values only. `47,2 ГБ`, `1 290 ₽`, `183 мс`. Never `99,9%`, never `1234567` |

**The one exception:** a figure inside a running Russian sentence («Осталось 3 дня подписки») is set
in the UI face like the rest of the sentence. A sentence never ripples between two faces. If the
figure matters enough to be branded, it is not a sentence, it is a value, and it gets its own slot.

### 2.11 Motion roles

| Role | Token | ms | Curve | Use |
|---|---|---|---|---|
| Press in | `motion_press_in` / `Dur.PressIn` | 90 | `ease_out_quart` | Finger or pointer down. Scale to 0.97 |
| Press out | `motion_press_out` / `Dur.PressOut` | 160 | `ease_out_quint` | Release, settle to rest |
| State | `motion_state` / `Dur.State` | 220 | `ease_standard` | Selection, enable/disable, tint crossfade, hover on Android-equivalent surfaces |
| Hover | (desktop only) | 150 | `ease_standard` | `:pointerover` overlay fade |
| Reveal | `motion_reveal` / `Dur.Reveal` | 300 | `ease_out_quint` | Show/hide, expand, sheet and sub-page entrance |
| Exit | (225 reverse) / `Dur.Exit` | 150 | `ease_standard` | Screen or sub-page exit |
| Shell | (n/a) / `Dur.Shell` | 200 | `ease_standard` | Desktop shell overlay crossfade |
| Hand-off | (n/a) / `Dur.Slow` | 450 | `ease_out_expo` | The single auth -> home hand-off |
| Stagger | `motion_stagger` / `Dur.Stagger` | 40 | n/a | Per-item list delay, total capped at 400 ms (so 10 items, then the rest together) |
| Emphasis | `motion_emphasis` / `Dur.Emphasis` | 600 | `ease_out_quint` | **The one hero moment: connect confirmation.** Nothing else |
| Instant | (system collapse) / `Dur.Instant` | 0 | n/a | Reduced-motion fallback: snap to end state |

**Exit is 75% of enter.** State reverse 165, reveal reverse 225.

**Reduced motion is a contract.** Android: `MotionUtils.animationsEnabled(context)` /
`View.reducedMotion()` in `util/MotionUtils.kt`; declarative `stateListAnimator` collapses
automatically at animator scale 0. Desktop: `MotionState.IsLite` in `Common/MotionState.cs`,
broadcast live; a view that reads the setting once in its constructor is the exact bug that file was
written to fix. An animation that honours neither is a P1 accessibility defect.

**Haptics** (Android only): `View.pressHaptic()` on primary confirmations (connect, purchase, delete
confirm), `View.tickHaptic()` for stepping and incremental selection. Nothing else vibrates.

### 2.12 Iconography

| Rule | Specification |
|---|---|
| **Grid** | Material 24 x 24 dp keyline grid. Live area 20 x 20, padding 2 on every side. Square keyline 18 x 18, circle keyline 20, vertical/horizontal rectangle keyline 20 x 16 |
| **Style** | Material **Outlined** for rows, toolbars, chips and empty states. Material **Filled** only for the selected navigation destination and for status glyphs (connected shield, paid check) |
| **Stroke** | 2 dp at the 24 dp grid, rendered as a filled `<path>` in the vector drawable (this is how Material outlined assets are drawn). **One weight across a hierarchy level.** Terminals are butt-cut, corners are 2 dp radius |
| **Colour** | Exactly one fill per glyph. `color_on_surface_variant` in rows and toolbars, `color_accent` when the row is the one lit element, `color_destructive` for destroy, `color_tile_glyph_neutral` inside a neutral tile |
| **Sizes** | 16 in a chip · 20 inline (chevron, status) · 22 inside a 40 tile · 24 in a toolbar or nav bar. **Nothing else** |
| **Format** | Vector only. No PNG. The single exception is `Assets/Flags/*.png` at `size_flag` 28, circular-masked, `xx.png` as the fallback |
| **The unified server icon** | One treatment everywhere a server appears (list row, connect hero, sheet header, notification): the flag tile at 28 inside the standard 40 tile slot, falling back to the globe glyph. No screen invents its own server visual |
| **Optical alignment** | Glyphs are optically centred in their tile and optically aligned to their label's baseline. A play glyph is nudged right by 1; a chevron is not |
| **Naming** | `ic_<domain>_<name>.xml` on Android, `Geo.<Domain>.<Name>` on desktop, same `<name>` on both. A glyph needed on the other platform is **ported**, never redrawn |
| **Accessible name** | `android:contentDescription` / `AutomationProperties.Name` on every icon-only control. Missing = P1 |
| **Banned** | Emoji as a glyph (including the emoji flags currently in `item_recycler_main.xml`), a second icon family, padlocks and globes as security decoration, any glyph carrying a gradient |

---

## 3. Layer 3 - Component tokens

Component tokens are the contract between the system and a component. They **never** change per
theme; they resolve through layer 2, which does. They are what a builder types.

| Component token | Resolves to | Value |
|---|---|---|
| `btn_height` | `size_cta` | 52 |
| `btn_height_compact` | `size_touch_min` | 48 |
| `btn_radius` | `radius_control` | 16 |
| `btn_pad_h` | `space_section` | 24 |
| `btn_gap_icon` | `space_tight` | 8 |
| `btn_label` | Title (16/700) on brand-free copy | 16sp / 700 |
| `btn_primary_bg` / `_fg` | `color_accent` / `color_on_accent` | |
| `btn_tonal_bg` / `_fg` | `color_accent_container` / `color_on_accent_container` | |
| `btn_outline_stroke` / `_fg` | `color_outline_control` @ `stroke_control` / `color_accent` | 1 |
| `btn_text_fg` | `color_accent` | |
| `btn_destructive_bg` / `_fg` | `color_destructive` / `#FFFFFF` | |
| `field_height` | `size_field` | 52 |
| `field_radius` | `radius_control` | 16 |
| `field_bg` | `color_surface_inset` | |
| `field_stroke` | `color_outline_control` @ `stroke_control` | 1 |
| `field_stroke_focus` | `color_accent` @ `stroke_focus` | 2 |
| `field_stroke_error` | `color_destructive` @ `stroke_control` | 1 |
| `field_pad_h` | `space_base` | 16 |
| `field_label_gap` | `space_tight` | 8 |
| `field_helper_gap` | `space_hair` | 4 |
| `row_min_height` | `size_row` | 56 |
| `row_pad_h` | `space_gutter` | 16 |
| `row_pad_v` | `space_snug` | 12 |
| `row_tile` / `row_glyph` | `size_tile` / `size_glyph_tile` | 40 / 22 |
| `row_tile_gap` | `space_snug` | 12 |
| `row_text_origin` | derived | 68 |
| `row_trailing_gap` | `space_snug` | 12 |
| `row_divider_inset_start` | `row_text_origin` | 68 |
| `card_radius` | `radius_object` | 20 |
| `card_pad` | `space_base` | 16 |
| `card_bg` / `card_stroke` | `color_surface` / `color_outline_variant` @ 1 | |
| `card_elevation` | 0 | **always** |
| `chip_height` | 24 | |
| `chip_radius` | `radius_fitting` | 12 |
| `chip_pad_h` / `chip_pad_v` | `space_tight` / `space_hair` | 8 / 4 |
| `chip_label` | Chip role | 11sp / 500 |
| `seg_track_height` | `size_touch_min` | 48 |
| `seg_track_radius` | `radius_control` | 16 |
| `seg_track_bg` | `color_surface_inset` | |
| `seg_thumb_radius` | `radius_fitting` | 12 |
| `seg_thumb_inset` | `space_hair` | 4 |
| `seg_thumb_bg` | `color_accent` | |
| `sheet_radius_top` | `radius_sheet` | 24 |
| `sheet_handle` | 36 x 4, `color_outline` | |
| `sheet_pad` | `space_base` | 16 |
| `sheet_scrim` | `color_scrim` | black 60% |
| `dialog_radius` | `radius_object` | 20 |
| `dialog_pad` | `space_section` | 24 |
| `dialog_max_width` | 360 phone / 420 desktop | |
| `toast_radius` | `radius_control` | 16 |
| `toast_bg` / `toast_fg` | `color_surface_inset` / `color_on_surface` | |
| `toast_pad` | `space_base` x `space_snug` | 16 x 12 |
| `toast_duration` | 5000 ms with an action, 3000 without | |
| `empty_icon` / `empty_glyph` | `size_empty_icon` / 32 | 64 / 32 |
| `empty_gap_title` / `_line` / `_action` | `space_base` / `space_tight` / `space_section` | 16 / 8 / 24 |
| `skeleton_radius` | `radius_fitting` | 12 |
| `skeleton_bar_h` | 12 / 16 / 20 (matching the role it stands in for) | |
| `progress_track_h` | `size_meter` | 6 |
| `progress_radius` | `radius_round` | 100 |
| `progress_track` / `_fill` | `color_surface_inset` / `color_accent` | |
| `spinner_size_inline` / `_button` / `_screen` | 20 / 20 / 32 | |
| `nav_height` | `size_nav_bar` | 64 |
| `nav_indicator` | 64 x 34, `color_accent_container`, `radius_control` | |
| `nav_glyph` / `nav_label` | 24 / Chip role at 11sp | |
| `toolbar_height` | `size_toolbar` | 56 |
| `toolbar_bg` | `color_background` | **the page, never a bar colour** |
| `toolbar_back` | 24 glyph in a 48 hit box at the gutter | |
| `toolbar_title_gap` | `space_base` | 16 |

---

## 4. Platform map - Android

Paths are relative to `/home/user/dp/V2rayNG/app/src/main/`.

### 4.0 The API floor, and what it costs

`app/build.gradle.kts`: **`minSdk = 24`**, `compileSdk = 37`, Material Components **1.13.0**. Three
of the attributes this system depends on are not available at 24, and the workaround for each is
mandatory, not optional.

| Attribute | Available from | On API 24-27 | Required workaround |
|---|---|---|---|
| `android:textFontWeight` | **API 28** | ignored | The weight must also be resolvable through `res/font/*.xml` family matching. That is why the family declares three `<font>` entries. See the gate in 2.9.2: with a variable file and no `fontVariationSettings`, all three entries resolve to the same default instance (`wght` 300) and **every brand string renders Light on a third of the install base**. Ship baked static instances (`spacegrotesk_400.ttf`, `_500.ttf`, `_700.ttf`) as the family's real entries if the gate confirms it |
| `android:lineHeight` | **API 28** (framework) | works anyway **inside a `TextAppearance` applied to an AppCompat/Material text view**: AppCompat 1.2+ reads it and applies it through `TextViewCompat.setLineHeight` on every API level | Every text view in the product must be `MaterialTextView` or `AppCompatTextView` (which is what layout inflation produces automatically for `<TextView>` under an AppCompat theme). A raw `TextView` inflated by hand in Kotlin will silently lose the line height on API 24-27 |
| `android:fontVariationSettings` | **API 26** | ignored | Static instances are the only fallback for 24-25 |

Two more floors worth stating because they are load-bearing here:

- **Edge-to-edge insets**: `WindowCompat.setDecorFitsSystemWindows(window, false)` plus explicit
  `WindowInsetsCompat` handling for status bar, navigation bar, display cutout and IME. Content never
  sits under a system bar or the keyboard. Required from API 24 up; there is no version gate on this.
- **Predictive Back** is API 33+ behaviour, but `OnBackPressedCallback` correctness is required on
  every API level. Back never traps the user and always restores scroll, filter and input state.

### 4.1 `res/values/colors.xml` (light theme + shared primitives)

Structure the file in three blocks and keep the block comments. The `md_theme_*` names stay: they are
referenced by `themes.xml` and renaming them is churn with no benefit.

```xml
<!-- ============ LAYER 1: PRIMITIVES. Never referenced from a layout. ============ -->
<color name="ink_00">#000000</color>
<color name="ink_04">#08090B</color>
<color name="ink_06">#0A0B0D</color>
<color name="ink_09">#111316</color>
<color name="ink_11">#141619</color>
<color name="ink_13">#1A1D21</color>
<color name="ink_15">#1E2126</color>
<color name="ink_17">#20242B</color>
<color name="ink_22">#2A2E36</color>
<color name="ink_45">#646C7C</color>   <!-- control boundary, 3.73:1 on ink_06 -->
<color name="ink_50">#6E7480</color>
<color name="ink_68">#9BA1AD</color>
<color name="ink_96">#F2F4F8</color>

<color name="paper_100">#FFFFFF</color>
<color name="paper_98">#F7F9FD</color>
<color name="paper_96">#F4F7FC</color>
<color name="paper_95">#F1F4FA</color>
<color name="paper_92">#EAEFF7</color>
<color name="paper_91">#E9EEF7</color>
<color name="paper_89">#E3EAF4</color>
<color name="paper_86">#DCE3EF</color>
<color name="paper_78">#C3CCDC</color>
<color name="paper_50">#7D8BA3</color> <!-- control boundary, 3.45:1 on paper_100 -->
<color name="paper_38">#54607A</color>
<color name="paper_28">#3C475E</color>
<color name="paper_10">#111826</color>

<color name="blue_03">#00183A</color>
<color name="blue_20">#17325C</color>
<color name="blue_30">#14468F</color>
<color name="blue_50">#1E5FC7</color>
<color name="blue_54">#3877E0</color>
<color name="blue_56">#3D7EF0</color>
<color name="blue_60">#4C8DFF</color>
<color name="blue_85">#CFE0FF</color>
<color name="blue_90">#D8E4FF</color>

<color name="green_20">#0C3F22</color>
<color name="green_32">#065132</color>
<color name="green_45">#0B7D4A</color>
<color name="green_60">#22C55E</color>
<color name="green_88">#A6F2C4</color>
<color name="red_15">#5C1420</color>
<color name="red_38">#9B1B23</color>
<color name="red_45">#C42B32</color>
<color name="red_50">#D93844</color>
<color name="red_55">#F04452</color>
<color name="red_65">#FF6069</color>
<color name="amber_28">#6B5000</color>
<color name="amber_40">#8A6300</color>
<color name="amber_58">#EAB308</color>

<!-- ============ LAYER 2: SEMANTIC (light). values-night/ overrides. ============ -->
<color name="md_theme_background">@color/paper_96</color>
<color name="md_theme_onBackground">@color/paper_10</color>
<color name="md_theme_surface">@color/paper_100</color>
<color name="md_theme_onSurface">@color/paper_10</color>
<color name="md_theme_surfaceContainerLowest">@color/paper_100</color>
<color name="md_theme_surfaceContainerLow">@color/paper_98</color>
<color name="md_theme_surfaceContainer">@color/paper_95</color>
<color name="md_theme_surfaceContainerHigh">@color/paper_92</color>
<color name="md_theme_surfaceContainerHighest">@color/paper_89</color>
<color name="md_theme_surfaceVariant">@color/paper_91</color>
<color name="md_theme_onSurfaceVariant">@color/paper_38</color>
<color name="md_theme_onSurfaceDim">@color/paper_28</color>                <!-- NEW -->
<color name="md_theme_outline">@color/paper_78</color>
<color name="md_theme_outlineVariant">@color/paper_86</color>
<color name="md_theme_outlineControl">@color/paper_50</color>          <!-- NEW -->
<color name="md_theme_primary">@color/blue_50</color>
<color name="md_theme_onPrimary">@color/paper_100</color>
<color name="md_theme_primaryContainer">@color/blue_90</color>
<color name="md_theme_onPrimaryContainer">@color/blue_30</color>
<color name="md_theme_tertiary">@color/green_45</color>
<color name="md_theme_onTertiary">@color/paper_100</color>
<color name="md_theme_tertiaryContainer">#D3E8DE</color>               <!-- green_45 @18% over white -->
<color name="md_theme_onTertiaryContainer">@color/green_32</color>
<color name="md_theme_error">@color/red_45</color>
<color name="md_theme_onError">@color/paper_100</color>
<color name="md_theme_errorContainer">#F4D9DA</color>                  <!-- red_45 @18% over white -->
<color name="md_theme_onErrorContainer">@color/red_38</color>
<color name="md_theme_scrim">@color/ink_00</color>
<color name="md_theme_inverseSurface">#2A3142</color>
<color name="md_theme_inverseOnSurface">@color/paper_95</color>
<color name="md_theme_inversePrimary">#AEC7FF</color>
<color name="md_theme_surfaceTint">@color/blue_50</color>

<color name="ping_good">@color/green_32</color>
<color name="ping_bad">@color/red_45</color>
<color name="warning">@color/amber_40</color>                          <!-- NEW -->
<color name="warning_text">@color/amber_28</color>                     <!-- NEW -->
<color name="warning_container">#FBF1D3</color>                        <!-- NEW, amber @18% over white -->
<color name="chip_type_text">@color/blue_30</color>
<color name="icon_tile_neutral">@color/paper_89</color>
<color name="icon_glyph_neutral">@color/paper_38</color>
<color name="icon_tile_blue">#331E5FC7</color>
<color name="icon_tile_red">#33C42B32</color>
<color name="icon_blue">@color/blue_50</color>
<color name="icon_red">@color/red_45</color>
<color name="state_selected">#1F1E5FC7</color>                         <!-- NEW, accent @12% -->
<color name="state_press">#1A1E5FC7</color>                            <!-- NEW, accent @10% ripple -->
<color name="skeleton">@color/paper_89</color>                         <!-- NEW -->
<color name="color_connected">@color/green_45</color>
<color name="color_fab_active">@color/blue_50</color>
<color name="color_fab_inactive">@color/paper_50</color>

<!-- MONO (light base) - unchanged names, values per section 1.1.5 -->
```

**Deleted from this file:** `brand_cream`, `colorWhite`, `color_upload`, `color_download`,
`colorPing`, `colorPingRed`, `colorConfigType`, `divider_color_light`, `colorIndicator`,
`icon_green`, `icon_purple`, `icon_yellow`, `icon_orange`, `icon_tile_green`, `icon_tile_purple`,
`icon_tile_yellow`, `icon_tile_orange`, `chip_json_text`, `chip_json_bg`. Every one is either a
duplicate of a primitive or a category that does not exist (D-5).

### 4.2 `res/values-night/colors.xml`

Only the semantic block is overridden. The primitive block is inherited from `values/` and **must
not be duplicated here** (a duplicated primitive is how a value drifts).

```xml
<color name="md_theme_background">@color/ink_06</color>
<color name="md_theme_onBackground">@color/ink_96</color>
<color name="md_theme_surface">@color/ink_11</color>
<color name="md_theme_onSurface">@color/ink_96</color>
<color name="md_theme_surfaceContainerLowest">@color/ink_04</color>
<color name="md_theme_surfaceContainerLow">@color/ink_09</color>
<color name="md_theme_surfaceContainer">@color/ink_11</color>
<color name="md_theme_surfaceContainerHigh">@color/ink_13</color>
<color name="md_theme_surfaceContainerHighest">@color/ink_17</color>
<color name="md_theme_surfaceVariant">@color/ink_15</color>
<color name="md_theme_onSurfaceVariant">@color/ink_68</color>
<color name="md_theme_onSurfaceDim">@color/ink_50</color>
<color name="md_theme_outline">@color/ink_22</color>
<color name="md_theme_outlineVariant">@color/ink_17</color>
<color name="md_theme_outlineControl">@color/ink_45</color>
<color name="md_theme_primary">@color/blue_60</color>
<color name="md_theme_onPrimary">@color/blue_03</color>
<color name="md_theme_primaryContainer">@color/blue_20</color>
<color name="md_theme_onPrimaryContainer">@color/blue_85</color>
<color name="md_theme_tertiary">@color/green_60</color>
<color name="md_theme_onTertiary">#00210F</color>
<color name="md_theme_tertiaryContainer">@color/green_20</color>
<color name="md_theme_onTertiaryContainer">@color/green_88</color>
<color name="md_theme_error">@color/red_55</color>
<color name="md_theme_onError">@color/paper_100</color>
<color name="md_theme_errorContainer">@color/red_15</color>
<color name="md_theme_onErrorContainer">#FFD9DD</color>
<color name="md_theme_inverseSurface">@color/ink_96</color>
<color name="md_theme_inverseOnSurface">@color/ink_11</color>
<color name="md_theme_inversePrimary">@color/blue_50</color>
<color name="md_theme_surfaceTint">@color/blue_60</color>

<color name="ping_good">@color/green_60</color>
<color name="ping_bad">@color/red_65</color>
<color name="warning">@color/amber_58</color>
<color name="warning_text">@color/amber_58</color>
<color name="warning_container">#32290C</color>
<color name="chip_type_text">@color/blue_60</color>
<color name="icon_tile_neutral">@color/ink_17</color>
<color name="icon_glyph_neutral">@color/ink_68</color>
<color name="icon_tile_blue">#334C8DFF</color>
<color name="icon_tile_red">#33F04452</color>
<color name="icon_blue">@color/blue_60</color>
<color name="icon_red">@color/red_55</color>
<color name="state_selected">#1F4C8DFF</color>
<color name="state_press">#1A4C8DFF</color>
<color name="skeleton">@color/ink_17</color>
<color name="color_connected">@color/green_60</color>
<color name="color_fab_active">@color/blue_60</color>
<color name="color_fab_inactive">@color/ink_45</color>

<!-- MONO (dark base) - unchanged names, values per section 1.1.5 -->
```

### 4.3 `res/values/dimens.xml`

Replace the file with this. Every value is in section 1.3.

```xml
<!-- Spacing scale. THE ONLY spacing values in the product. -->
<dimen name="space_4">4dp</dimen>
<dimen name="space_8">8dp</dimen>
<dimen name="space_12">12dp</dimen>
<dimen name="space_16">16dp</dimen>
<dimen name="space_24">24dp</dimen>
<dimen name="space_32">32dp</dimen>
<dimen name="screen_gutter">16dp</dimen>          <!-- values-sw600dp overrides to 24dp -->
<dimen name="content_max_width">720dp</dimen>

<!-- Radius. Shape lock: 12 fittings / 16 controls / 20 objects / 24 sheet lip / 100 round. -->
<dimen name="radius_chip">12dp</dimen>
<dimen name="radius_tile">12dp</dimen>
<dimen name="radius_control">16dp</dimen>         <!-- NEW: buttons, fields, segmented track -->
<dimen name="radius_card">20dp</dimen>
<dimen name="radius_sheet">24dp</dimen>
<dimen name="radius_pill">100dp</dimen>

<!-- Stroke. -->
<dimen name="stroke_hairline">1dp</dimen>
<dimen name="stroke_control">1dp</dimen>
<dimen name="stroke_focus">2dp</dimen>
<dimen name="stroke_emphasis">2dp</dimen>
<dimen name="stroke_ring">3dp</dimen>
<dimen name="focus_offset">2dp</dimen>

<!-- Sizes. -->
<dimen name="glyph_16">16dp</dimen>
<dimen name="glyph_20">20dp</dimen>
<dimen name="tile_glyph">22dp</dimen>
<dimen name="image_size_dp24">24dp</dimen>
<dimen name="flag_size">28dp</dimen>
<dimen name="avatar_chip">36dp</dimen>
<dimen name="tile_size">40dp</dimen>
<dimen name="icon_button">40dp</dimen>
<dimen name="view_height_dp48">48dp</dimen>
<dimen name="field_height">52dp</dimen>
<dimen name="cta_height">52dp</dimen>
<dimen name="row_min_height">56dp</dimen>
<dimen name="toolbar_height">56dp</dimen>
<dimen name="nav_bar_height">64dp</dimen>
<dimen name="empty_icon">64dp</dimen>
<dimen name="shield_glyph">80dp</dimen>
<dimen name="sub_card_height">152dp</dimen>
<dimen name="connect_disc">176dp</dimen>
<dimen name="meter_height">6dp</dimen>
<dimen name="dot_size">6dp</dimen>
<dimen name="dot_size_active">8dp</dimen>
<dimen name="dot_gap">8dp</dimen>
<dimen name="sheet_handle_w">36dp</dimen>
<dimen name="sheet_handle_h">4dp</dimen>
<dimen name="divider_inset_start">68dp</dimen>
```

New file `res/values-sw600dp/dimens.xml`:

```xml
<dimen name="screen_gutter">24dp</dimen>
```

**Deleted:** `padding_spacing_dp4`, `padding_spacing_dp8`, `padding_spacing_dp16` (duplicates of
`space_*`, currently used by the raw-upstream stratum C layouts), `view_height_dp36`,
`view_height_dp64`, `view_height_dp120`, `view_height_dp160`.

### 4.4 `res/values/motion.xml`, `res/interpolator/`, `res/anim/`

`motion.xml` gains one token and keeps the rest unchanged:

```xml
<integer name="motion_press_in">90</integer>
<integer name="motion_press_out">160</integer>
<integer name="motion_hover">150</integer>        <!-- parity token; unused on Android -->
<integer name="motion_state">220</integer>
<integer name="motion_state_exit">165</integer>   <!-- NEW: 75% of state -->
<integer name="motion_reveal">300</integer>
<integer name="motion_reveal_exit">225</integer>  <!-- NEW: 75% of reveal -->
<integer name="motion_slow">450</integer>         <!-- NEW: auth hand-off only -->
<integer name="motion_stagger">40</integer>
<integer name="motion_emphasis">600</integer>
<item name="press_scale" type="fraction">97%</item>  <!-- NEW: one press value -->
```

New `res/interpolator/ease_out_expo.xml`:

```xml
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.16" android:controlY1="1"
    android:controlX2="0.3"  android:controlY2="1" />
```

`res/anim/press_scale.xml`: change `valueTo` from **0.96 to 0.97** on both axes.
`res/anim/nav_press.xml`: change `valueTo` from **0.92 to 0.97**, and replace the hard-coded
`duration="100"` / `"120"` with `@integer/motion_press_in` / `@integer/motion_press_out`. One gesture,
one value, everywhere.

### 4.5 `res/values/styles.xml` - type ramp

```xml
<style name="TextAppearance.App.Display" parent="TextAppearance.Material3.HeadlineMedium">
    <item name="android:fontFamily">@font/space_grotesk</item>
    <item name="android:textFontWeight">700</item>
    <item name="android:textSize">34sp</item>
    <item name="android:lineHeight">40sp</item>
    <item name="android:letterSpacing">-0.02</item>
    <item name="android:fontFeatureSettings">"tnum" on, "lnum" on</item>
    <item name="android:textColor">?attr/colorOnSurface</item>
</style>

<style name="TextAppearance.App.Headline" parent="TextAppearance.Material3.HeadlineSmall">
    <item name="android:fontFamily">@font/ui_sans</item>
    <item name="android:textFontWeight">700</item>
    <item name="android:textSize">24sp</item>
    <item name="android:lineHeight">28sp</item>
    <item name="android:letterSpacing">-0.01</item>
    <item name="android:textColor">?attr/colorOnSurface</item>
</style>

<style name="TextAppearance.App.Title" parent="TextAppearance.Material3.TitleMedium">
    <item name="android:fontFamily">@font/ui_sans</item>
    <item name="android:textFontWeight">700</item>
    <item name="android:textSize">16sp</item>
    <item name="android:lineHeight">20sp</item>
    <item name="android:letterSpacing">0.0</item>
    <item name="android:textColor">?attr/colorOnSurface</item>
</style>

<style name="TextAppearance.App.Title.Medium" parent="TextAppearance.App.Title">
    <item name="android:textFontWeight">500</item>
</style>

<style name="TextAppearance.App.Body" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">@font/ui_sans</item>
    <item name="android:textFontWeight">400</item>
    <item name="android:textSize">14sp</item>
    <item name="android:lineHeight">20sp</item>
    <item name="android:letterSpacing">0.01</item>
    <item name="android:textColor">?attr/colorOnSurface</item>
</style>

<style name="TextAppearance.App.Subtitle" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">@font/ui_sans</item>
    <item name="android:textFontWeight">400</item>
    <item name="android:textSize">13sp</item>
    <item name="android:lineHeight">18sp</item>
    <item name="android:letterSpacing">0.01</item>
    <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
</style>

<style name="TextAppearance.App.Caption" parent="TextAppearance.Material3.BodySmall">
    <item name="android:fontFamily">@font/ui_sans</item>
    <item name="android:textFontWeight">400</item>
    <item name="android:textSize">12sp</item>
    <item name="android:lineHeight">16sp</item>
    <item name="android:letterSpacing">0.02</item>
    <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
</style>

<style name="TextAppearance.App.Chip" parent="TextAppearance.Material3.LabelSmall">
    <item name="android:fontFamily">@font/space_grotesk</item>
    <item name="android:textFontWeight">500</item>
    <item name="android:textSize">11sp</item>
    <item name="android:lineHeight">14sp</item>
    <item name="android:letterSpacing">0.04</item>
    <item name="android:textAllCaps">false</item>
</style>

<!-- Numeric: apply ON TOP of a size-bearing role, never alone. -->
<style name="TextAppearance.App.Numeric" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">@font/space_grotesk</item>
    <item name="android:textFontWeight">500</item>
    <item name="android:fontFeatureSettings">"tnum" on, "lnum" on, "zero" on</item>
    <item name="android:letterSpacing">0.0</item>
    <item name="android:textColor">?attr/colorOnSurface</item>
</style>

<!-- Money: same, WITHOUT the slashed zero (D-3). -->
<style name="TextAppearance.App.Numeric.Money" parent="TextAppearance.App.Numeric">
    <item name="android:fontFeatureSettings">"tnum" on, "lnum" on</item>
</style>

<style name="SettingsSectionLabel" parent="TextAppearance.App.Title">
    <item name="android:paddingStart">@dimen/screen_gutter</item>
    <item name="android:paddingEnd">@dimen/screen_gutter</item>
    <item name="android:paddingTop">@dimen/space_24</item>
    <item name="android:paddingBottom">@dimen/space_8</item>
    <item name="android:textAllCaps">false</item>
</style>

<style name="ToolbarBrandTitle" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:fontFamily">@font/space_grotesk</item>
    <item name="android:textFontWeight">700</item>
    <item name="android:textSize">20sp</item>
    <item name="android:lineHeight">24sp</item>
    <item name="android:letterSpacing">-0.01</item>
    <item name="android:textColor">?attr/colorOnBackground</item>
</style>
```

Note `SettingsSectionLabel` padding changes from `18dp` top (off-scale, a defect) to
`@dimen/space_24`.

### 4.6 `res/values/styles.xml` - component widget styles

Today the app has **no button styles at all**: every `MaterialButton` sets its own height, radius and
padding inline, which is how four card radii and three button shapes happened. These styles are the
fix. A layout that sets `app:cornerRadius`, `android:minHeight` or `android:textAppearance` on a
button after this exists is a defect.

```xml
<!-- ONE button system. Every screen uses these. No screen sets its own geometry. -->
<style name="Widget.App.Button" parent="Widget.Material3.Button">
    <item name="android:minHeight">@dimen/cta_height</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:paddingStart">@dimen/space_24</item>
    <item name="android:paddingEnd">@dimen/space_24</item>
    <item name="cornerRadius">@dimen/radius_control</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title</item>
    <item name="iconGravity">textStart</item>
    <item name="iconPadding">@dimen/space_8</item>
    <item name="iconSize">@dimen/glyph_20</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
    <item name="elevation">0dp</item>
</style>

<style name="Widget.App.Button.Primary" parent="Widget.App.Button">
    <item name="backgroundTint">?attr/colorPrimary</item>
    <item name="android:textColor">?attr/colorOnPrimary</item>
    <item name="iconTint">?attr/colorOnPrimary</item>
    <item name="rippleColor">@color/state_press_on_accent</item>
</style>

<style name="Widget.App.Button.Tonal" parent="Widget.Material3.Button.TonalButton">
    <item name="android:minHeight">@dimen/cta_height</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="cornerRadius">@dimen/radius_control</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title</item>
    <item name="backgroundTint">?attr/colorPrimaryContainer</item>
    <item name="android:textColor">?attr/colorOnPrimaryContainer</item>
    <item name="iconTint">?attr/colorOnPrimaryContainer</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
</style>

<style name="Widget.App.Button.Outlined" parent="Widget.Material3.Button.OutlinedButton">
    <item name="android:minHeight">@dimen/cta_height</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="cornerRadius">@dimen/radius_control</item>
    <item name="strokeColor">?attr/colorOutlineControl</item>
    <item name="strokeWidth">@dimen/stroke_control</item>
    <item name="android:textColor">?attr/colorPrimary</item>
    <item name="iconTint">?attr/colorPrimary</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
</style>

<style name="Widget.App.Button.Text" parent="Widget.Material3.Button.TextButton">
    <item name="android:minHeight">@dimen/view_height_dp48</item>
    <item name="cornerRadius">@dimen/radius_control</item>
    <item name="android:textColor">?attr/colorPrimary</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title</item>
    <item name="android:paddingStart">@dimen/space_16</item>
    <item name="android:paddingEnd">@dimen/space_16</item>
</style>

<style name="Widget.App.Button.Destructive" parent="Widget.App.Button">
    <item name="backgroundTint">?attr/colorError</item>
    <item name="android:textColor">@color/paper_100</item>
    <item name="iconTint">@color/paper_100</item>
</style>

<style name="Widget.App.Button.Destructive.Text" parent="Widget.App.Button.Text">
    <item name="android:textColor">?attr/pingBad</item>
</style>

<style name="Widget.App.Button.Icon" parent="Widget.Material3.Button.IconButton">
    <item name="android:layout_width">@dimen/view_height_dp48</item>
    <item name="android:layout_height">@dimen/view_height_dp48</item>
    <item name="iconSize">@dimen/image_size_dp24</item>
    <item name="iconTint">?attr/colorOnSurfaceVariant</item>
    <item name="cornerRadius">@dimen/radius_control</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
</style>

<!-- Container -->
<style name="Widget.App.Card" parent="Widget.Material3.CardView.Outlined">
    <item name="cardBackgroundColor">?attr/colorSurface</item>
    <item name="cardCornerRadius">@dimen/radius_card</item>
    <item name="strokeColor">?attr/colorOutlineVariant</item>
    <item name="strokeWidth">@dimen/stroke_hairline</item>
    <item name="cardElevation">0dp</item>
    <item name="contentPadding">@dimen/space_16</item>
    <item name="rippleColor">@null</item>
</style>

<!-- Input.
     Base is OutlinedBox, NOT FilledBox: FilledBox draws a bottom indicator only,
     and this design needs a P3 fill PLUS a continuous 1 px border. OutlinedBox with
     boxBackgroundColor set gives exactly that, and hintEnabled="false" removes the
     label notch so the outline stays unbroken (the label lives above the field). -->
<style name="Widget.App.TextField" parent="Widget.Material3.TextInputLayout.OutlinedBox">
    <item name="boxBackgroundColor">?attr/colorSurfaceContainerHighest</item>
    <item name="boxCornerRadiusTopStart">@dimen/radius_control</item>
    <item name="boxCornerRadiusTopEnd">@dimen/radius_control</item>
    <item name="boxCornerRadiusBottomStart">@dimen/radius_control</item>
    <item name="boxCornerRadiusBottomEnd">@dimen/radius_control</item>
    <item name="boxStrokeColor">@color/field_stroke_selector</item>
    <item name="boxStrokeWidth">@dimen/stroke_control</item>
    <item name="boxStrokeWidthFocused">@dimen/stroke_focus</item>
    <item name="boxStrokeErrorColor">?attr/colorError</item>
    <item name="hintEnabled">false</item>            <!-- label lives ABOVE the field -->
    <item name="errorTextAppearance">@style/TextAppearance.App.Caption.Error</item>
    <item name="android:textColorHint">?attr/colorOnSurfaceVariant</item>
    <item name="materialThemeOverlay">@style/ThemeOverlay.App.TextField</item>
</style>

<style name="TextAppearance.App.Caption.Error" parent="TextAppearance.App.Caption">
    <item name="android:textColor">?attr/pingBad</item>
</style>

<!-- Chip -->
<style name="Widget.App.Chip.Status" parent="Widget.Material3.Chip.Assist">
    <item name="chipMinHeight">24dp</item>
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.App.Fitting</item>
    <item name="chipStartPadding">@dimen/space_8</item>
    <item name="chipEndPadding">@dimen/space_8</item>
    <item name="chipStrokeWidth">0dp</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Chip</item>
    <item name="ensureMinTouchTargetSize">false</item>
    <item name="chipIconSize">@dimen/glyph_16</item>
</style>

<!-- Segmented control -->
<style name="Widget.App.SegmentedGroup" parent="Widget.Material3.MaterialButtonToggleGroup">
    <item name="android:background">@drawable/bg_segment_track</item>
    <item name="android:padding">@dimen/space_4</item>
    <item name="singleSelection">true</item>
    <item name="selectionRequired">true</item>
</style>

<style name="Widget.App.SegmentedButton" parent="Widget.Material3.Button.TextButton">
    <item name="android:minHeight">40dp</item>
    <item name="cornerRadius">@dimen/radius_chip</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title.Medium</item>
    <item name="android:textColor">@color/segment_text_selector</item>
    <item name="backgroundTint">@color/segment_bg_selector</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
</style>

<!-- Toggle -->
<style name="Widget.App.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
    <item name="thumbTint">@color/switch_thumb_selector</item>
    <item name="trackTint">@color/switch_track_selector</item>
    <item name="trackDecorationTint">@android:color/transparent</item>
</style>

<!-- Structure -->
<style name="Widget.App.Divider" parent="Widget.Material3.MaterialDivider">
    <item name="dividerThickness">@dimen/stroke_hairline</item>
    <item name="dividerColor">?attr/colorOutlineVariant</item>
    <item name="dividerInsetStart">@dimen/divider_inset_start</item>
    <item name="dividerInsetEnd">0dp</item>
</style>

<!-- Seamless sub-page toolbar: page background, no elevation, no line. -->
<style name="Widget.App.Toolbar.Seamless" parent="Widget.Material3.Toolbar">
    <item name="android:background">?android:attr/colorBackground</item>
    <item name="android:elevation">0dp</item>
    <item name="titleTextAppearance">@style/TextAppearance.App.Title</item>
    <item name="titleCentered">false</item>
    <item name="contentInsetStartWithNavigation">@dimen/screen_gutter</item>
    <item name="navigationIconTint">?attr/colorOnSurface</item>
    <item name="android:minHeight">@dimen/toolbar_height</item>
</style>

<style name="Widget.App.BottomNav" parent="Widget.Material3.BottomNavigationView">
    <item name="android:background">?android:attr/colorBackground</item>
    <item name="android:elevation">0dp</item>
    <item name="itemActiveIndicatorStyle">@style/BottomNavIndicator</item>
    <item name="itemRippleColor">@null</item>      <!-- owner request 0.4.8: no ripple glow -->
    <item name="itemTextAppearanceInactive">@style/BottomNavLabel</item>
    <item name="itemTextAppearanceActive">@style/BottomNavLabel.Active</item>
    <item name="labelVisibilityMode">labeled</item>
    <item name="itemIconSize">@dimen/image_size_dp24</item>
</style>

<style name="BottomNavIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:width">64dp</item>
    <item name="android:height">32dp</item>
    <item name="android:color">?attr/colorPrimaryContainer</item>
    <item name="marginHorizontal">@dimen/space_8</item>
    <item name="shapeAppearance">@style/ShapeAppearance.App.Control</item>
</style>

<style name="BottomNavLabel" parent="TextAppearance.App.Chip">
    <item name="android:textFontWeight">500</item>
</style>
<style name="BottomNavLabel.Active" parent="BottomNavLabel">
    <item name="android:textFontWeight">700</item>
</style>

<!-- Progress -->
<style name="Widget.App.Progress.Linear" parent="Widget.Material3.LinearProgressIndicator">
    <item name="trackThickness">@dimen/meter_height</item>
    <item name="trackCornerRadius">3dp</item>       <!-- half of 6: true round ends -->
    <item name="trackColor">?attr/colorSurfaceContainerHighest</item>
    <item name="indicatorColor">?attr/colorPrimary</item>
</style>

<style name="Widget.App.Progress.Circular.Inline" parent="Widget.Material3.CircularProgressIndicator.ExtraSmall">
    <item name="indicatorSize">@dimen/glyph_20</item>
    <item name="trackThickness">2dp</item>
    <item name="indicatorColor">?attr/colorPrimary</item>
    <item name="trackColor">@android:color/transparent</item>
</style>

<!-- Sheets and dialogs -->
<style name="ThemeOverlay.App.BottomSheet" parent="ThemeOverlay.Material3.BottomSheetDialog">
    <item name="bottomSheetStyle">@style/Widget.App.BottomSheet</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>

<style name="Widget.App.BottomSheet" parent="Widget.Material3.BottomSheet.Modal">
    <item name="backgroundTint">?attr/colorSurface</item>
    <item name="shapeAppearance">@style/ShapeAppearance.App.Sheet</item>
    <item name="android:elevation">0dp</item>
</style>

<style name="ShapeAppearance.App.Sheet" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSizeTopLeft">@dimen/radius_sheet</item>
    <item name="cornerSizeTopRight">@dimen/radius_sheet</item>
    <item name="cornerSizeBottomLeft">0dp</item>
    <item name="cornerSizeBottomRight">0dp</item>
</style>

<style name="ShapeAppearance.App.Control" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">@dimen/radius_control</item>
</style>

<style name="ShapeAppearance.App.Fitting" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">@dimen/radius_chip</item>
</style>

<style name="ShapeAppearance.App.Object" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">@dimen/radius_card</item>
</style>

<style name="Widget.App.Snackbar" parent="Widget.Material3.Snackbar">
    <item name="backgroundTint">?attr/colorSurfaceContainerHighest</item>
    <item name="android:layout_margin">@dimen/space_16</item>
    <item name="shapeAppearance">@style/ShapeAppearance.App.Control</item>
    <item name="elevation">0dp</item>
</style>
```

### 4.7 `res/values/attrs.xml` and `themes.xml`

**New theme attributes** (`attrs.xml`):

```xml
<attr name="colorOutlineControl" format="color" />   <!-- the 3:1 control boundary -->
<attr name="colorOnSurfaceDim" format="color" />     <!-- dimmed glyph tone -->
<attr name="warning" format="color" />               <!-- replaces iconTintYellow -->
<attr name="warningText" format="color" />
<attr name="warningContainer" format="color" />
<attr name="colorSkeleton" format="color" />
```

**Removed attributes**: `iconTintGreen`, `iconTintOrange`, `iconTintPurple`, `iconTintYellow`,
`iconTileBgGreen`, `iconTileBgOrange`, `iconTileBgPurple`, `iconTileBgYellow`, `chipJsonText`,
`chipJsonBg`. Today six of these are aliased to blue in `themes.xml:88-99`, meaning **the colour
names in the layouts are lies**: a row that says `bg_icon_purple` renders blue. Removing the attrs
forces each of the ~40 call sites to state its real category (neutral, accent, or destructive).

`themes.xml` `AppThemeBase` gains:

```xml
<item name="colorOutlineControl">@color/md_theme_outlineControl</item>
<item name="colorOnSurfaceDim">@color/md_theme_onSurfaceDim</item>
<item name="warning">@color/warning</item>
<item name="warningText">@color/warning_text</item>
<item name="warningContainer">@color/warning_container</item>
<item name="colorSkeleton">@color/skeleton</item>

<!-- One button / card / field / toolbar system, applied theme-wide so a layout
     that forgets the style still gets the right shape. -->
<item name="materialButtonStyle">@style/Widget.App.Button.Primary</item>
<item name="materialButtonOutlinedStyle">@style/Widget.App.Button.Outlined</item>
<item name="borderlessButtonStyle">@style/Widget.App.Button.Text</item>
<item name="materialCardViewStyle">@style/Widget.App.Card</item>
<item name="textInputStyle">@style/Widget.App.TextField</item>
<item name="materialSwitchStyle">@style/Widget.App.Switch</item>
<item name="chipStyle">@style/Widget.App.Chip.Status</item>
<item name="materialDividerStyle">@style/Widget.App.Divider</item>
<item name="bottomSheetDialogTheme">@style/ThemeOverlay.App.BottomSheet</item>
<item name="snackbarStyle">@style/Widget.App.Snackbar</item>
<item name="linearProgressIndicatorStyle">@style/Widget.App.Progress.Linear</item>
<item name="bottomNavigationStyle">@style/Widget.App.BottomNav</item>
<item name="toolbarStyle">@style/Widget.App.Toolbar.Seamless</item>

<!-- Dynamic Color (Material You) stays OFF. The brand's single blue wins over
     wallpaper theming. Deliberate deviation from android.md, per 00-rules 6.10. -->
```

`ThemeOverlay.Mono` gains the same four new attrs, pointing at the mono values in 1.1.5, and drops
the twelve removed ones.

### 4.8 Drawables the system needs

Create (all in `res/drawable/`, all vector or shape XML, all referencing theme attrs):

| File | What it is |
|---|---|
| `bg_field.xml` | `<shape>` rect, `radius_control`, solid `?attr/colorSurfaceContainerHighest`, stroke `stroke_control` `?attr/colorOutlineControl` |
| `bg_segment_track.xml` | `<shape>` rect, `radius_control`, solid `?attr/colorSurfaceContainerHighest` |
| `bg_segment_thumb.xml` | `<shape>` rect, `radius_chip`, solid `?attr/colorPrimary` |
| `bg_tile_neutral.xml` | `<shape>` rect, `radius_tile`, solid `@color/icon_tile_neutral` (replaces `bg_icon_neutral`) |
| `bg_tile_accent.xml` | same, solid `?attr/iconTileBgBlue` (replaces `bg_icon_blue`) |
| `bg_tile_destructive.xml` | same, solid `?attr/iconTileBgRed` (replaces `bg_icon_red`) |
| `bg_chip_neutral.xml` | `<shape>` rect, `radius_chip`, solid `?attr/colorSurfaceContainerHighest` |
| `bg_row_selected.xml` | `<shape>` rect, `radius_control`, solid `@color/state_selected` |
| `focus_ring.xml` | `<layer-list>`: inner transparent inset `focus_offset`, outer `<shape>` stroke `stroke_focus` `?attr/colorPrimary`, `radius_control` |
| `divider_hairline.xml` | `<shape>` line, `stroke_hairline`, `?attr/colorOutlineVariant` |
| ~~`bg_meter_track.xml` / `bg_meter_fill.xml`~~ | **удалены при финальной сверке.** Ни одной ссылки за всё время: единственный измеритель в продукте — `ProgressBar` с `@drawable/bg_traffic_gradient` в `layout_subscription_meta_bar.xml`, и лишний набор имён для той же полосы только приглашал завести второй |
| `bg_skeleton.xml` | `<shape>` rect, `radius_chip`, solid `?attr/colorSkeleton` |
| `bg_sheet_handle.xml` | `<shape>` rect, radius 2, solid `?attr/colorOutline` |
| Colour selectors in `res/color/` | `field_stroke_selector`, `segment_text_selector`, `segment_bg_selector`, `switch_thumb_selector`, `switch_track_selector`, `state_press_on_accent` |

**Delete** (per `03-direction.md` D-H and F1):
`bg_home_gradient.xml`, `bg_home_gradient_mono.xml`, `bg_connect_glow.xml`,
`bg_connect_glow_mono.xml`, `bg_nav_header.xml`, `bg_bottom_nav_scrim.xml`, `nav_header_bg.png`,
`bg_traffic_gradient.xml`, `bg_settings_glass.xml`, `bg_chip_gold.xml`, `bg_type_chip.xml`
(superseded by `bg_chip_neutral`), `bg_icon_green.xml`, `bg_icon_orange.xml`, `bg_icon_purple.xml`,
`bg_icon_yellow.xml`, and `res/font/montserrat_thin.ttf` (an orphan with zero Cyrillic and zero
references).

---

## 5. Platform map - Desktop (Avalonia)

Paths relative to `/home/user/v2rayN/v2rayN/v2rayN.Desktop/`.

### 5.1 `Assets/GlobalResources.axaml` - tokens

**Layer 1 goes at the top of the file, outside `ThemeDictionaries`**, as `<Color>` entries (not
brushes: a primitive has no paint role):

```xml
<!-- LAYER 1: PRIMITIVES. Never referenced from a View. -->
<Color x:Key="Color.Ink06">#0A0B0D</Color>
<Color x:Key="Color.Ink11">#141619</Color>
<Color x:Key="Color.Ink13">#1A1D21</Color>
<Color x:Key="Color.Ink17">#20242B</Color>
<Color x:Key="Color.Ink22">#2A2E36</Color>
<Color x:Key="Color.Ink45">#646C7C</Color>
<Color x:Key="Color.Ink68">#9BA1AD</Color>
<Color x:Key="Color.Ink96">#F2F4F8</Color>
<Color x:Key="Color.Paper100">#FFFFFF</Color>
<Color x:Key="Color.Paper96">#F4F7FC</Color>
<Color x:Key="Color.Paper89">#E3EAF4</Color>
<Color x:Key="Color.Paper86">#DCE3EF</Color>
<Color x:Key="Color.Paper78">#C3CCDC</Color>
<Color x:Key="Color.Paper50">#7D8BA3</Color>
<Color x:Key="Color.Paper38">#54607A</Color>
<Color x:Key="Color.Paper10">#111826</Color>
<Color x:Key="Color.Blue60">#4C8DFF</Color>
<Color x:Key="Color.Blue50">#1E5FC7</Color>
<!-- ... the full set from section 1.1 ... -->
```

**Layer 2 lives entirely inside `ResourceDictionary.ThemeDictionaries`.** Both the `Dark` and the
`Light` dictionaries must carry the **identical key list**; a key present in one and absent in the
other is the classic Avalonia theme-switch crash. New keys to add to **both**:

```xml
<SolidColorBrush x:Key="Brush.OnBg"            Color="..." />   <!-- was implicit -->
<SolidColorBrush x:Key="Brush.SurfaceLow"      Color="..." />
<SolidColorBrush x:Key="Brush.OutlineControl"  Color="..." />   <!-- 3:1 control boundary -->
<SolidColorBrush x:Key="Brush.AccentHover"     Color="#3D7EF0" />
<SolidColorBrush x:Key="Brush.AccentPress"     Color="#3877E0" />
<SolidColorBrush x:Key="Brush.GreenContainer"  Color="..." />
<SolidColorBrush x:Key="Brush.RedContainer"    Color="..." />
<SolidColorBrush x:Key="Brush.Ping.Good"       Color="..." />
<SolidColorBrush x:Key="Brush.Ping.Bad"        Color="..." />
<SolidColorBrush x:Key="Brush.Amber"           Color="..." />
<SolidColorBrush x:Key="Brush.AmberText"       Color="..." />
<SolidColorBrush x:Key="Brush.AmberContainer"  Color="..." />
<SolidColorBrush x:Key="Brush.Skeleton"        Color="..." />
<SolidColorBrush x:Key="Brush.Tile.Glyph"      Color="..." />
<!-- Hover redefined: white 6% on dark, black 6% on light. See 2.4. -->
<SolidColorBrush x:Key="Brush.Hover"           Color="#FFFFFF" Opacity="0.06" />  <!-- Dark -->
<SolidColorBrush x:Key="Brush.Hover"           Color="#000000" Opacity="0.06" />  <!-- Light -->
```

**Removed from `GlobalResources.axaml`:** `Brush.HomeGradient` (both themes),
`Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`, `Brush.Tile.Orange`,
`Brush.Tile.Purple`, `Brush.Tile.Yellow`, `Brush.Tile.Green`, `Brush.Icon.Orange`,
`Brush.Icon.Yellow`, `Brush.StatusChip.Orange`, `Brush.StatusChip.Yellow`, `Radius.Search`,
`Radius.Traffic`, `Size.TrafficPill`, `Size.HeroFrame`, `Size.ConnectArc`, `Margin2`, `MarginLr4`,
`Margin4`, `MarginLr8`, `MarginTb8`, `Margin8`, `IconButtonWidth`, `IconButtonHeight` (off-scale
duplicates of `Space.*` and `Size.IconButton`), and the five `building_*` `StreamGeometry` entries
(upstream Chinese icon set, replaced by the ported Material glyphs).

**Renamed:**

| Old key | New key | Why |
|---|---|---|
| `Font.Grotesk` | `Font.Brand` | It is the brand face, not a generic family |
| `Radius.Button` | `Radius.Control` | It is now also the field and segmented-track radius |
| `Brush.SurfaceHighest` | kept | already correct |
| `Brush.OnSurfaceVariantHover` | `Brush.OnSurfaceDim` | it is a dim tone, used beyond hover |

**Added:**

```xml
<FontFamily x:Key="Font.Ui">Segoe UI, Inter, Noto Sans, DejaVu Sans, sans-serif</FontFamily>
<FontFamily x:Key="Font.Brand">avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk</FontFamily>
<FontFamily x:Key="Font.Numeric">avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk</FontFamily>

<CornerRadius x:Key="Radius.Control">16</CornerRadius>
<x:Double x:Key="Stroke.Hairline">1</x:Double>
<x:Double x:Key="Stroke.Control">1</x:Double>
<x:Double x:Key="Stroke.Focus">2</x:Double>
<x:Double x:Key="Stroke.Emphasis">2</x:Double>
<x:Double x:Key="Stroke.Ring">3</x:Double>
<x:Double x:Key="Size.GutterWide">24</x:Double>
<x:Double x:Key="Size.ContentMax">720</x:Double>
<x:Double x:Key="Size.Field">52</x:Double>
<x:Double x:Key="Size.TouchMin">48</x:Double>
<x:Double x:Key="Size.Meter">6</x:Double>
<x:Double x:Key="Size.GlyphChip">16</x:Double>
<x:Double x:Key="Size.GlyphInline">20</x:Double>
<x:Double x:Key="Size.GlyphNav">24</x:Double>
<x:Double x:Key="Size.NavRailItem">64</x:Double>
<x:Double x:Key="Size.ConnectDisc">176</x:Double>
<Thickness x:Key="Gutter.Wide">24,0</Thickness>
<Thickness x:Key="Pad.Row">16,12</Thickness>
<Thickness x:Key="Pad.Chip">8,4</Thickness>
<Thickness x:Key="Pad.Toast">16,12</Thickness>
```

### 5.2 `Assets/GlobalStyles.axaml` - the class vocabulary

The three blanket font setters at lines 257-265 change from `Font.Grotesk` to `Font.Ui`. That single
edit is the largest visual change in the desktop app and it is the one that makes the Russian text
identical on Windows, Linux and macOS.

Existing classes that stay (their names are correct):
`TextBlock.{Display,Headline,Title,TitleMedium,Body,Subtitle,Caption,Chip,Numeric,SectionHeader}`,
`Border.{Card,Row,Tile,ChipBadge,ProtocolChip,ServerRow,SettingRow,SearchPill,Avatar,ConnectDisc,SubToolbar,StatusChip,PriceOption,SheetTop,SheetHandle,Scrim,Toast,EmptyIcon,SkelBar,SkelCard,NavRail}`,
`Button.{Primary,Tonal,OutlinedAccent,Destructive,LinkAction,IconButton40,BackNav,NavRailItem,Stepper}`,
`ToggleButton.Segment`, `Ellipse.{Dot,Spinner}`, `ControlTheme` `ToggleSwitch.iOS`, `TextBox.Incy`.

Changes required:

| Class | Change |
|---|---|
| `TextBlock.Headline/Title/TitleMedium/SectionHeader` | `FontFamily` from `Font.Grotesk` to `Font.Ui` |
| `TextBlock.Body/Subtitle/Caption` | add explicit `FontFamily = Font.Ui` (currently inherit the blanket setter) |
| `TextBlock.Display/Chip/Numeric` | `FontFamily` to `Font.Brand` |
| all `TextBlock.*` | add `LineHeight` per section 2.9 |
| `TextBlock.Numeric` | keep `FontFeatures="tnum,lnum,zero"`; add sibling class `TextBlock.Numeric.Money` with `FontFeatures="tnum,lnum"` (D-3) |
| `TextBlock.Wordmark` | **new**, brand face 20px/700, `Brush.OnBg`, tracking -0.2 |
| `Button.Primary` | `Height` 48 to `Size.CtaTall` 52; press transition duration 0.12 to `0:0:0.09` in / `0:0:0.16` out; `FontSize` 15 to 16 with `TextBlock.Title` metrics |
| `Button.Primary` hover | `#3D7EF0` literal to `{DynamicResource Brush.AccentHover}` |
| `Button.Primary` pressed | `#3877E0` literal to `{DynamicResource Brush.AccentPress}` |
| `Button.OutlinedAccent` | stroke to `Brush.OutlineControl` at `Stroke.Control` |
| `Border.SearchPill`, `Border.PriceOption`, `TextBox.Incy`, `TextBox.IncyField` | `Radius.Search` 14 to `Radius.Control` 16 |
| `TextBox.Incy` | `Background` to `Brush.SurfaceHighest` (P3 inset), border to `Brush.OutlineControl`, `FontSize` 15 to 14 with `Body` metrics |
| `Border.Row` | `Padding` `12,0` to `{StaticResource Pad.Row}` 16,12; `CornerRadius` `Radius.Tile` to `Radius.Control` |
| `Border.Row:pointerover` | unchanged selector, new `Brush.Hover` value |
| `Border.TrafficPill` / `.Fill` | replace with `Border.Meter` / `Border.Meter.Fill`, height `Size.Meter` 6, radius `Radius.Pill` |
| `Border.StatusChip.*` | light-theme text must use `Brush.Ping.Good` / `Brush.RedText` / `Brush.AmberText`, not the fill colour (see 2.3) |
| `Button.NavRailItem` | active label weight step 500 to 700 (currently colour-only in places) |
| every focusable control | add a `:focus-visible` style with a 2 px `Brush.Accent` ring at 2 px offset. **Currently absent from most classes and it is the single largest desktop accessibility gap** |

New classes required:

```
Button.PrimaryCompact     48 h, otherwise Button.Primary
Button.TextAction         text-only, Brush.Accent, 48 h, Radius.Control
Button.DestructiveText    text-only, Brush.RedText
Border.FieldLabel         label-above-field slot
TextBlock.FieldError      Caption in Brush.RedText
Border.Divider            1 px Brush.OutlineVariant, Margin="68,0,0,0"
Border.EmptyState         container: 64 icon, Headline, Body, one Button.Primary
Border.Skeleton           Radius.Chip, Brush.Skeleton, the SkeletonPulse animation
Border.Meter / .Fill      6 h, Radius.Pill
Border.OfflineBar         P3 fill, Caption, one Button.TextAction «Повторить»
TextBlock.Wordmark        brand face 20/700
```

### 5.3 `Common/Motion.cs`

Add the two computed reverses so C# and XAML cannot drift:

```csharp
/// <summary>165 мс - реверс State (75% от 220).</summary>
public static readonly TimeSpan StateExit = TimeSpan.FromMilliseconds(165);
/// <summary>225 мс - реверс Reveal (75% от 300).</summary>
public static readonly TimeSpan RevealExit = TimeSpan.FromMilliseconds(225);
/// <summary>150 мс - ховер (:pointerover), кривая Standard.</summary>
public static readonly TimeSpan Hover = TimeSpan.FromMilliseconds(150);
/// <summary>0.97 - единственный масштаб нажатия во всём продукте.</summary>
public const double PressScale = 0.97;
```

### 5.4 Cross-platform name parity table

The audit table. Every row must exist on both sides after the work.

| Concept | Android | Desktop |
|---|---|---|
| ground | `?android:attr/colorBackground` | `Brush.Bg` |
| object surface | `?attr/colorSurface` | `Brush.Surface` |
| raised (transient) | `?attr/colorSurfaceContainerHigh` | `Brush.SurfaceHigh` |
| inset | `?attr/colorSurfaceContainerHighest` | `Brush.SurfaceHighest` |
| primary text | `?attr/colorOnSurface` | `Brush.OnSurface` |
| secondary text | `?attr/colorOnSurfaceVariant` | `Brush.OnSurfaceVariant` |
| accent | `?attr/colorPrimary` | `Brush.Accent` |
| on accent | `?attr/colorOnPrimary` | `Brush.OnAccent` |
| hairline | `?attr/colorOutlineVariant` | `Brush.OutlineVariant` |
| control boundary | `?attr/colorOutlineControl` | `Brush.OutlineControl` |
| success | `?attr/colorTertiary` | `Brush.Green` |
| success text | `?attr/pingGood` | `Brush.Ping.Good` |
| destructive | `?attr/colorError` | `Brush.Red` |
| destructive text | `?attr/pingBad` | `Brush.RedText` |
| warning | `?attr/warning` | `Brush.Amber` |
| warning text | `?attr/warningText` | `Brush.AmberText` |
| dimmed glyph | `?attr/colorOnSurfaceDim` | `Brush.OnSurfaceDim` |
| selected fill | `@color/state_selected` | `Brush.SelectedFill` |
| skeleton | `?attr/colorSkeleton` | `Brush.Skeleton` |
| gutter | `@dimen/screen_gutter` | `Size.Gutter` |
| row height | `@dimen/row_min_height` | `Size.Row` |
| tile / glyph | `@dimen/tile_size` / `tile_glyph` | `Size.Tile` / `Size.Glyph` |
| CTA height | `@dimen/cta_height` | `Size.CtaTall` |
| control radius | `@dimen/radius_control` | `Radius.Control` |
| card radius | `@dimen/radius_card` | `Radius.Card` |
| brand face | `@font/space_grotesk` | `Font.Brand` |
| UI face | `@font/ui_sans` | `Font.Ui` |
| press scale | `@fraction/press_scale` 97% | `Motion.PressScale` 0.97 |
| state duration | `@integer/motion_state` | `Motion.Dur.State` |

### 5.5 Allowed asymmetries, with reasons

| Asymmetry | Reason |
|---|---|
| `color_state_hover` / `Brush.Hover` exists only on desktop | Android has no pointer. Do not add hover states to Android |
| Focus ring always rendered on desktop, keyboard/TV only on Android | Platform convention. Android touch focus rings are noise |
| `Brush.AccentHover` / `AccentPress` are desktop-only literals | Android expresses press with a ripple plus scale, not a background swap |
| Android has `stateListAnimator`, desktop has `Transitions` | Different animation systems, identical result |
| Android bottom navigation, desktop left rail | `00-rules.md` 13. Same destinations, same order, same labels |
| Android bottom sheet, desktop flyout | `00-rules.md` 13 |
| Haptics are Android-only | No desktop equivalent |
| `Radius.Sheet` is `24,24,0,0` on desktop, a `ShapeAppearance` on Android | Framework shape APIs differ |

---

## 6. Component library

Every component below is specified by **anatomy, sizes, and every applicable state**. A component
that ships without one of its states is not done. The state columns follow the priority order in
2.4.

### 6.1 Button

Six variants, one geometry. Per `00-rules.md` 4.3: **one primary action per screen.** Two filled
accent buttons on one screen is a defect.

**Anatomy**

```
[ pad 24 ][ icon 20 ][ 8 ][ label, Title 16/700 ][ pad 24 ]
height 52 (compact 48) · radius 16 · elevation 0 · no shadow
```

Full-width buttons sit at the gutter and stretch; inline buttons hug their content with a 24 pad.
A button never wraps its label: if the Russian string does not fit at 320 dp width, the copy is
rewritten. Icon is optional and leading only, 20 dp, tinted with the label colour.

**Variants and their tokens**

| Variant | Background | Label / icon | Border | Use |
|---|---|---|---|---|
| **Primary** | `color_accent` | `color_on_accent` | none | The one thing the screen wants you to press. «Войти», «Подключить», «Купить» |
| **Tonal** | `color_accent_container` | `color_on_accent_container` | none | The secondary action beside a primary. «Пополнить», «Поддержка» |
| **Outlined** | transparent | `color_accent` | 1 `color_outline_control` | A neutral alternative path. «Из буфера» |
| **Text** | transparent | `color_accent` | none | Tertiary. «Забыли пароль?», «Создать аккаунт» |
| **Destructive** | `color_destructive` | `#FFFFFF` | none | Confirms a destroy inside a dialog. «Удалить подписку» |
| **Destructive text** | transparent | `color_destructive_text` | none | The destroy row's action, and the dialog's confirm |

**States**

| State | Primary | Tonal | Outlined | Text | Destructive |
|---|---|---|---|---|---|
| Default | accent fill | container fill | 1 px control outline | label only | red fill |
| Hover (desktop) | bg `color_accent_hover` `#3D7EF0`, 150 ms | +6% overlay | +6% overlay | +6% overlay | bg `color_destructive_press` |
| Focus | 2 px accent ring, 2 px offset, radius 18 | same | same | same | 2 px accent ring (**not** red: the ring means focus, not danger) |
| Pressed | bg `color_accent_press` `#3877E0` + scale 0.97 | overlay 10% + scale 0.97 | overlay 10% + scale 0.97 | overlay 10% | `#D93844` + scale 0.97 |
| Disabled | opacity 0.38, no ripple, no cursor change | 0.38 | 0.38 | 0.38 | 0.38 |
| Loading | label hidden, 20 dp indeterminate in `color_on_accent` centred, **width unchanged**, `IsEnabled=false` | same in `on_accent_container` | same in `color_accent` | same | same in white |
| Selected | n/a (a button is not a toggle) | | | | |
| Error | n/a. Errors live on the field or in a snackbar | | | | |

**Copy rules**: buttons are verbs in sentence case. «Подключить», «Купить», «Привязать Telegram»,
«Сохранить», «Удалить». Never «OK», never «Да»/«Нет» as the primary pair, never a final period.

### 6.2 Icon button

40 dp visual, **48 dp hit box** (Android expands with padding or `TouchDelegate`), 24 dp glyph,
radius 16, no background at rest.

| State | Treatment |
|---|---|
| Default | glyph `color_on_surface_variant`, transparent background |
| Hover (desktop) | glyph steps to `color_on_surface_dim`; **no background tile appears** |
| Focus | 2 px accent ring at 2 px offset |
| Pressed | scale 0.97 + (Android) ripple `color_state_press` bounded to the 40 dp shape |
| Disabled | 0.38 |
| Active / toggled | glyph `color_accent`, plus the tooltip or label states the mode. Never tint alone |

Every icon button carries `contentDescription` / `AutomationProperties.Name`. Toolbars hold **at most
one** trailing icon button; more go into an overflow.

### 6.3 Text field

**Anatomy** (this order, always):

```
Label            Title.Medium 16/500, color_on_surface
  8
[ Field ]        height 52, radius 16, P3 fill, 1 px control outline, pad 16 h
  4
Helper / error   Caption 12, color_on_surface_variant / color_destructive_text
```

The helper slot is **present in the markup even when empty**, so the layout does not jump when an
error appears. The label is never a placeholder. The placeholder, when present, is example content
(`example@mail.ru`), not a repeat of the label, and it renders at `color_on_surface_variant` which
clears 4.5:1 on the inset plane (6.00:1 dark, 5.21:1 light).

| State | Treatment |
|---|---|
| Default | P3 fill, 1 px `color_outline_control` |
| Hover (desktop) | border to `color_outline`, 150 ms |
| Focus | border to 2 px `color_accent`; **no ring on top of the border**, the border is the ring |
| Filled | identical to default. A field with content does not change colour |
| Disabled | 0.38 on the whole control, no caret |
| Read-only | P3 fill, no border, `color_on_surface_variant` text, no caret |
| Error | 1 px `color_destructive` border, error text below in `color_destructive_text` 12sp, and the field keeps its fill |
| Loading | trailing 20 dp indeterminate inside the field, field disabled |
| Success | no green field. Success is the screen moving on |

**Behaviour** (`00-rules.md` 7.4): validate on **blur**, not per keystroke (exception: password
strength). After a failed submit, focus moves to the first invalid field. Password fields carry a
show/hide toggle in `InnerRightContent` / `endIconMode="password_toggle"`. Correct
`android:inputType` and `android:autofillHints` on every field.

**Error copy** follows the formula *what happened + why + what to do*: «Неверная почта или пароль.»,
«Проверьте ссылку провайдера и повторите.» Never a bare «Ошибка», never an error code.

### 6.4 Row archetypes

**The universal row.** This is the repeating structural unit of the entire product, on both
platforms. Every archetype below is this row with a different trailing element.

```
[16][ tile 40, r12, glyph 22 ][12][ text column, weight 1 ][12][ trailing ][16]
                                   Title     Title 16/700, max 2 lines
                                   Subtitle  Subtitle 13/400, max 2 lines, optional
min height 56 · text origin 68 · hairline starts at 68 and never runs under the tile
```

- The **whole row** is the target, not the trailing control.
- **Exactly one** trailing element. Never two.
- With a two-line subtitle the row grows; it never clips.
- The tile is optional on dense sub-pages; when it is absent the text origin is still 68 (16 gutter
  + 52 reserved), so the column does not move between screens.

| Archetype | Trailing | Tap does | Notes |
|---|---|---|---|
| **Navigation** | chevron 20 dp, `color_on_surface_variant` | pushes a sub-page | The only archetype that navigates |
| **Value** | value text, Subtitle 13/400, `color_on_surface_variant`, right-aligned, max 40% of the row width | opens a segmented inline control, a sheet, or a sub-page | The value is the current setting, in the user's words: «TUN», «Системный», «1 час» |
| **Toggle** | `MaterialSwitch` / `ToggleSwitch.iOS`, 52 x 32 | toggles | Row and switch both toggle. Label states what is on when it is on |
| **Destructive** | none, or a 20 dp glyph in `color_destructive` | removes, with undo | Title in `color_destructive_text`, tile is `color_tile_destructive` |
| **Selection** | check glyph 20 dp in `color_accent` when selected | selects | Selected row also carries `color_selected_fill`. Two channels |
| **Server** | ping value (Numeric 13/500) or a 20 dp spinner | selects the server | Leading tile is the flag at 28 inside the 40 slot, globe fallback. Protocol chip sits in the text column, not the trailing slot |

**Row states**

| State | Android | Desktop |
|---|---|---|
| Default | transparent on P0 | transparent on P0 |
| Hover | n/a | `color_state_hover` overlay across the full row, radius 16, 150 ms |
| Focus | 2 px accent ring inset 2 (keyboard/TV) | 2 px accent ring inset 2, always |
| Pressed | ripple `color_state_press` bounded to the row + scale 0.97 | scale 0.97 |
| Selected | `color_selected_fill` + check glyph + title weight 700 | same |
| Disabled | 0.38, no ripple, and a subtitle saying **why** it is disabled | same |
| Loading | the trailing slot holds a 20 dp indeterminate; the row stays interactive-looking but is disabled | same |
| Error | subtitle swaps to `color_destructive_text` with the cause, plus a «Повторить» text button in the trailing slot | same |

**Group rules** (`03-direction.md` 7.3): max **7 rows per group**, max **4 groups per screen**,
groups separated by 24 and a sentence-case bold section header, **no divider under a section header**
and none above a group's first row. Space separates groups; hairlines separate siblings.

### 6.5 Card

**Allowed only when all three hold** (`00-rules.md` 4.4): the content is a distinct object the user
acts on as a unit, it needs a boundary that spacing cannot give, and it is not inside another card.
**Maximum one card per screen** (`03-direction.md` D-D). A settings screen is rows, not cards. An
account screen is one card (the subscription) plus rows.

| Property | Value |
|---|---|
| Background | `color_surface` (P1) |
| Radius | 20 |
| Border | 1 px `color_outline_variant` |
| Padding | 16 |
| Elevation | **0**. Always. No shadow, in any theme |
| Inside it | spacing and at most one hairline. Never a second card. A chip (P3) inside is legal |

| State | Treatment |
|---|---|
| Default | as above |
| Hover (desktop, only if the card is clickable) | `color_state_hover` overlay |
| Focus | 2 px accent ring at 2 px offset, radius 22 |
| Pressed (clickable only) | scale 0.97 |
| Loading | the card renders as a skeleton of the same height with 3 bars at 12/16/20 |
| Empty | the card is not rendered. The empty state replaces it |

### 6.6 Chip and badge

Height 24, radius 12, padding 8 x 4, label Chip role (11/500 brand face), optional leading 16 dp
glyph with a 4 gap. **A chip never carries both a fill and a border**: it is a hole in the plane, not
an object on it.

| Kind | Fill | Label colour | Example |
|---|---|---|---|
| **Neutral** | `color_surface_inset` | `color_on_surface_variant` | `WS`, `TCP` |
| **Protocol** | `color_accent_container` | `color_on_accent_container` | `VLESS`, `Reality` |
| **Tariff badge** | `color_accent_container` | `color_on_accent_container` | «Базовый» |
| **Status: paid / connected** | `color_success` @18% | `color_success_text` | «Оплачен», «Активна» |
| **Status: pending** | `color_warning` @18% | `color_warning_text` | «Ждёт оплаты» |
| **Status: failed / expired** | `color_destructive` @18% | `color_destructive_text` | «Не оплачен», «Истекла» |
| **Status: canceled** | `color_surface_inset` | `color_on_surface_variant` | «Отменён» |

Status chips are **not interactive**: no hover, no press, no focus. If a chip needs to be pressed it
is a button or a filter chip, and a filter chip gets the selection states of 6.4.

**Never** a chip that repeats what the title already says. That is the decoration tell.

### 6.7 Segmented control

For a choice among **2 to 4** options, inline, replacing a single-choice dialog
(`03-direction.md` F13 lists six dialogs to convert).

```
[ track: height 48, radius 16, P3 fill, padding 4 ]
  [ segment ][ segment ][ segment ]     each: height 40, radius 12, weight 1
```

| State | Segment treatment |
|---|---|
| Default (unselected) | transparent, label `color_on_surface_variant` Title.Medium 16/500 |
| Hover (desktop) | `color_state_hover` overlay on the segment |
| Focus | 2 px accent ring around the **segment**, inset 2 |
| Pressed | scale 0.97 on the segment only |
| **Selected** | fill `color_accent`, label `color_on_accent`, weight **700**. Thumb slides to position over `dur_220` `ease_standard` |
| Disabled (whole control) | 0.38 |
| Disabled (one segment) | label 0.38, not selectable, and a caption below states why |

Two channels on selection: fill **and** weight. Never fill alone. More than 4 options is a sheet
(Android) or a flyout (desktop) with a radio list, not a wider segmented control.

### 6.8 Section header and divider

**Section header**: Title role, 16sp/700, sentence case, `color_on_surface`, at the gutter, 24 above
and 8 below. Never `textAllCaps`, never tracked, never a tiny grey eyebrow. It may carry a trailing
text button (for example «Показать все») at Title 16/700 in `color_accent`, right-aligned.

**Divider**: 1 px `color_outline_variant`, starting at the **68 text origin**, ending at the screen
edge (no end inset). Between siblings inside one group only. Never full-bleed, never under the tile,
never both above and below one row, never under a section header.

### 6.9 Sheet (Android) and flyout (desktop)

Order of preference for any decision: **inline > expandable row > sheet/flyout > dialog.** A dialog
is the last resort.

**Android bottom sheet**

| Property | Value |
|---|---|
| Body | `color_surface` (P1), radius `24,24,0,0` |
| Drag handle | 36 x 4, radius 2, `color_outline`, centred, 12 from the top |
| Padding | 16 h, 8 top under the handle, 16 + navigation-bar inset at the bottom |
| Title | Title 16/700 at the gutter, 8 under the handle |
| Content | rows (6.4), full 56 height, no tiles unless they carry a category |
| Scrim | `color_scrim` black 60% |
| Enter / exit | `dur_300` `ease_out_quint` in, `dur_225` `ease_standard` out |
| Dismiss | drag, scrim tap, system Back. Focus returns to the trigger |

**Desktop flyout**: `Brush.SurfaceHigh` (P2, so it separates from a card beneath), radius 20, 1 px
`color_outline_variant`, padding 16, `MaxHeight` 480 with an internal scroller, anchored to the row
that opened it. Esc closes. Focus moves in on open and back to the trigger on close.

Neither ever nests: a sheet does not open a sheet.

### 6.10 Dialog

Only for a decision that is genuinely interrupting and irreversible. Everything else is inline or a
sheet. `MaterialAlertDialogBuilder` with `ThemeOverlay.Departament.Dialog` on Android; a modal window
with the same layout on desktop.

```
radius 20 · P1 body · padding 24 · max width 360 (phone) / 420 (desktop) · scrim 60%
Title      Title 16/700, color_on_surface
  8
Body       Body 14/400, color_on_surface, max 60 characters per line
  24
[ Cancel (text) ]                    [ Confirm (text or destructive text) ]
```

- The confirm button **says what it does**: «Удалить подписку», «Выйти из аккаунта». Never «OK».
- Cancel is on the left, confirm on the right, both text buttons at 48 height.
- A destructive confirm uses `Widget.App.Button.Destructive.Text` (`color_destructive_text`).
- Esc and system Back both cancel. Focus enters the dialog and returns to the trigger on close.
- **Prefer undo.** `00-rules.md` 7.5: remove immediately plus a 5-second undo snackbar. A dialog is
  correct only for deleting an account, wiping all subscriptions, or resetting the whole config.

### 6.11 Toast and snackbar

Android uses `Snackbar` (never `Toast` for anything the user can act on). Desktop uses
`Border.Toast`.

| Property | Value |
|---|---|
| Background | `color_surface_inset` (P3) |
| Text | Body 14/400, `color_on_surface`, max 2 lines |
| Action | text button, Title 16/700, `color_accent`, right-aligned, one action maximum |
| Radius | 16 |
| Padding | 16 x 12 |
| Margins | 16 from the screen edges, anchored **above** the bottom navigation (never at a magic offset like the current `toast_status.xml` at 110 dp) |
| Duration | 5000 ms with an action, 3000 ms without |
| Enter / exit | slide + fade, `dur_300` `ease_out_quint` / `dur_225` `ease_standard` |
| Stacking | one at a time. A new message replaces the current one |

Every error snackbar carries a recovery action («Повторить»). A snackbar never carries the only copy
of information the user needs.

### 6.12 Empty state

Formula (`00-rules.md` 9.5): **title (what is not here) + one line (why, or what it gives you) + one
action.** Never «Нет данных» alone, never an illustration, never a mascot.

```
        [ 64 tile, radius 20, color_surface_inset, 32 glyph in color_on_surface_variant ]
                                  16
        Title           Headline 24/700, centred, color_on_surface
                                   8
        One line        Body 14/400, centred, color_on_surface_variant, max 60 chars
                                  24
        [ one Primary button ]
```

Centred is the one place centred text is allowed (plus the connect control's own label).

| Screen | Title | Line | Action |
|---|---|---|---|
| No servers | `Нет серверов` | `Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.` | `Добавить провайдера` |
| Search found nothing | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| No subscription | `Подписки пока нет` | `Купите тариф, чтобы подключаться к серверам Departament.` | `Купить` |
| No payments | `Платежей пока нет` | `Здесь появится история покупок и продлений.` | none |
| No devices | `Устройств пока нет` | `Устройства появятся после первого подключения.` | none |
| Telegram not linked | `Telegram не привязан` | `Привяжите Telegram, чтобы управлять подпиской из бота.` | `Привязать Telegram` |
| No apps (per-app proxy) | `Приложений нет` | `Установленные приложения появятся здесь.` | none |

### 6.13 Skeleton

Never a centred spinner over a blank screen. A skeleton is **shaped like the content it replaces**
and appears only after **300 ms** of waiting.

| Property | Value |
|---|---|
| Fill | `color_skeleton` (= `color_surface_inset`) |
| Radius | 12 |
| Bar heights | 12 (caption), 16 (subtitle), 20 (title). Match the role being replaced |
| Bar widths | vary: 40%, 70%, 55%. Never three identical bars |
| Card skeleton | the card's exact height (76 for a row block, 152 for the subscription card) |
| Pulse | alpha 0.35 to 0.65, 1200 ms, `ease_standard`, alternating. **Not a shimmer sweep** |
| Reduced motion | no pulse, static at alpha 0.5 |
| Exit | crossfade to content over `dur_220` `ease_standard`. No layout jump: the skeleton reserves the final height |

### 6.14 Progress

| Kind | Spec |
|---|---|
| **Linear determinate (meter)** | Track height 6, radius = half height, track `color_surface_inset`, fill `color_accent`, animates to a new value over `dur_300` `ease_out_quint`. **The label is never printed on the fill** (F11: the current subscription meter measures 2.9:1). Label above at Subtitle, value to its right at Numeric |
| **Linear indeterminate** | Same geometry, `color_accent`, only while real work is happening |
| **Circular inline** | 20 dp, 2 dp track, `color_accent` (or `color_on_accent` inside a primary button). Used in buttons, fields and row trailing slots |
| **Circular screen** | 32 dp, centred. **Only** for a blocking operation with no content shape to skeleton |
| **Connect sweep** | The connect disc's own ring, `stroke_ring` 3, `color_accent`, running **only while the core is negotiating**. An indeterminate indicator that runs while nothing is happening is a lie about the system |

A determinate meter carries its numbers: «12,4 из 50 ГБ» with the figures in the Numeric role.

### 6.15 Tab bar (Android) and nav rail (desktop)

Same destinations, same order, same Russian labels on both platforms:
**Главная · Серверы · Настройки · Аккаунт** (Аккаунт appears only when signed in; when signed out the
fourth slot is **Войти**).

**Android bottom navigation**

| Property | Value |
|---|---|
| Height | 64 + navigation-bar inset |
| Background | `color_background` (P0). **No scrim, no elevation, no divider** |
| Item | 24 dp glyph + 11sp label, always visible |
| Inactive | glyph and label `color_on_surface_variant`, weight 500, **no tint** |
| Active | glyph `color_accent` filled variant, label `color_accent` weight **700**, plus the 64 x 32 `color_accent_container` indicator pill at radius 16 |
| Ripple | **none** (owner request 0.4.8). Press feedback is scale 0.97 only |
| Indicator motion | `dur_220` `ease_standard` |
| sw600dp | becomes a `NavigationRailView` with the same items in the same order |

**Desktop nav rail**: fixed width, P0 ground, separated from content by a single 1 px
`color_outline_variant` (not a tone change). Same two-channel active state. Indicator moves over
`dur_220` `ease_out_quint`. Hover on an inactive item darkens its glyph to `color_on_surface_dim`; it
does **not** grow a background tile.

Sub-pages never appear inside the tab bar or the rail. Nothing nests inside navigation.

### 6.16 Toolbar

**The seamless sub-page toolbar** (owner request 0.4.6) is the only toolbar in the product.

```
[16][ back 24 in a 48 hit box ][16][ Title 16/700 ][ ......... ][ one action 40 ][16]
height 56 · background color_background (P0) · elevation 0 · no divider · no shadow
```

- On scroll it does **not** change colour and does not gain a line. The single permitted variant: a
  1 px `color_outline_variant` hairline fades in at `scrollY > 0` over `dur_220` and fades out at 0.
- At most **one** trailing action. More go into an overflow.
- The main shell's top area carries the wordmark at `TextBlock.Wordmark` / `ToolbarBrandTitle` and no
  bar at all. **The wordmark is not blue** (`color_on_background`); the brand does not spend the
  screen's one accent on advertising itself.
- Back: system Back and predictive Back on Android; the back button plus Esc plus mouse button 4 on
  desktop. Back restores scroll position, filter state and input.

### 6.17 Switch

Track 52 x 32, radius 16 (half height), thumb 26 circle.

| State | Track | Thumb |
|---|---|---|
| Off | `color_surface_inset` | `#FFFFFF`, 0.5 px `#22000000` edge |
| On | `color_accent` | `#FFFFFF`, translated +20 px |
| Pressed | unchanged | thumb fill scales to 0.9 from its centre, `dur_90` `ease_out_quart` |
| Focus | 2 px accent ring around the track at 2 px offset | |
| Disabled | 0.38 on the whole control | |
| Transition | track colour `dur_220` `ease_standard`; thumb travel `dur_220` `ease_out_quint` | |

The row and the switch are both the target. The label says what is on when it is on: «Автообновление
подписки», never «Не отключать автообновление».

### 6.18 Search field

Height 48, radius 16, P3 fill, no border at rest, leading 20 dp search glyph at 16 from the left,
placeholder `Поиск` at `color_on_surface_variant`, trailing 20 dp clear glyph when there is text.

| State | Treatment |
|---|---|
| Default | P3 fill, no border |
| Hover (desktop) | `color_state_hover` overlay |
| Focus | 2 px `color_accent` border |
| Filled | trailing clear glyph appears |
| No results | the list below shows the "Ничего не найдено" empty state, **not** a blank list |

Search **filters in place**; it never navigates. `Ctrl+F` focuses it on desktop.

---

## 7. The component state matrix

One page. Cross-check every component against it before calling it done.

| Component | Default | Hover (desktop) | Focus | Pressed | Selected | Disabled | Loading | Error | Empty |
|---|---|---|---|---|---|---|---|---|---|
| Button primary | required | required | required | required | n/a | required | required | n/a | n/a |
| Button tonal / outlined / text | required | required | required | required | n/a | required | required | n/a | n/a |
| Button destructive | required | required | required | required | n/a | required | required | n/a | n/a |
| Icon button | required | required | required | required | optional | required | optional | n/a | n/a |
| Text field | required | required | required | n/a | n/a | required | required | **required** | n/a |
| Row: navigation | required | required | required | required | n/a | required | optional | optional | n/a |
| Row: value | required | required | required | required | n/a | required | optional | optional | n/a |
| Row: toggle | required | required | required | required | **required** | required | optional | optional | n/a |
| Row: destructive | required | required | required | required | n/a | required | required | required | n/a |
| Row: selection | required | required | required | required | **required** | required | optional | n/a | n/a |
| Row: server | required | required | required | required | **required** | required | **required** (ping) | **required** | n/a |
| Card | required | if clickable | if clickable | if clickable | n/a | required | **required** | optional | n/a |
| Chip: status | required | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| Chip: filter | required | required | required | required | **required** | required | n/a | n/a | n/a |
| Segmented control | required | required | required | required | **required** | required | n/a | n/a | n/a |
| Switch | required | required | required | required | **required** | required | n/a | n/a | n/a |
| Search field | required | required | required | n/a | n/a | required | optional | n/a | **required** |
| Sheet / flyout | required | n/a | required | n/a | n/a | n/a | required | required | required |
| Dialog | required | n/a | required | n/a | n/a | n/a | required | required | n/a |
| Toast / snackbar | required | n/a | required (action) | required (action) | n/a | n/a | n/a | n/a | n/a |
| List | required | n/a | n/a | n/a | n/a | n/a | **required** (skeleton) | **required** | **required** |
| Tab bar / rail | required | required | required | required | **required** | required | n/a | n/a | n/a |
| Toolbar | required | n/a | required (actions) | required (actions) | n/a | required (actions) | n/a | n/a | n/a |
| Progress | required | n/a | n/a | n/a | n/a | n/a | required | n/a | n/a |
| Empty state | n/a | n/a | required (action) | required (action) | n/a | n/a | n/a | n/a | required |

Plus the screen-level states that every screen owns (`00-rules.md` 15): default, first run, loading,
empty, error, offline, partial, long content, short content, gated, success. And the product-specific
gates: `нет подписки`, `подписка истекает`, `подписка истекла`, `триал`, `Telegram не привязан`,
`нет серверов`, `подключение`, `подключено`, `отключение`, `ошибка туннеля`, `лимит устройств`.

---

## 8. Offline, as a designed state

Offline is not an error toast. Every screen that reads the network implements it.

```
[ 16 gutter ][ P3 bar, radius 16, padding 16 x 12 ]
   Нет сети. Показаны последние данные.        Body 14/400, color_on_surface
                                  [ Повторить ]  text button, color_accent
```

- Last known data stays on screen and is marked stale: a caption «Данные могли устареть» under the
  affected block.
- Every action that needs the network is disabled (0.38) with a subtitle saying why.
- The bar is quiet, persistent, and appears once per screen. It is not a snackbar and it does not
  auto-dismiss.

---

## 9. Change-control rows for `00-rules.md` section 18

Nothing here is implemented until the row is pasted into `00-rules.md` section 18 and the rule body
there is updated. Rows D-1 to D-5 are carried forward from `03-direction.md` 11.2 unchanged.

| Date | Decision | Rule affected |
|---|---|---|
| pending | **D-1.** The Russian UI face is `<Golos Text / Onest / pinned platform stack>`, vendored identically to both platforms, because the Space Grotesk binary contains zero Cyrillic codepoints | 3.4, 5.1 |
| pending | **D-2.** Space Grotesk is scoped to digits, units, currency, Latin technical tokens, chip labels and the wordmark, and is never applied to a Russian string | 3.4, 5.1 |
| pending | **D-3.** `zero` (slashed zero) is on for technical figures and off for currency, identically on both platforms | 5.5 |
| pending | **D-4.** `TextAppearance.App.Numeric` gains `android:textFontWeight="500"`; `res/font/space_grotesk.xml` gains explicit `android:fontVariationSettings` if the 2.9.2 verification confirms the default-instance risk | 3.4, 5.4 |
| pending | **D-5.** Coloured icon tiles are a closed system of exactly three: accent, destructive, neutral. `icon_*_purple/orange/yellow/green` and their theme attrs are deleted | 3.6 |
| **pending** | **D-6. Buttons are a 16 dp rounded rectangle, not a pill.** The owner rejected the capsule button (`Assets/GlobalStyles.axaml:9`), and under 0.1 the owner's word outranks the rule file. `00-rules.md` 3.2's "buttons are pill" and 16's "pill buttons" are replaced by `radius_control` 16. `radius_pill` survives for genuinely round objects only: connect disc, avatar, dots, meter ends, sheet handle | 3.2, 4.5 of 03-direction, 16 |
| **pending** | **D-7. Input fields, the search field, the price option and the segmented track share the button radius, 16.** This retires the undocumented desktop `Radius.Search` 14 and `Radius.Traffic` 8 and reduces the product from nine radii in use to four. `00-rules.md` 3.2's "inputs are 12" is replaced | 3.2 |
| **pending** | **D-8. Desktop hover is a 6% white overlay on dark and a 6% black overlay on light**, replacing the 32% black scrim. Measured: 32% black over the `#0A0B0D` ground produces `#070709`, a 1.16:1 delta that is invisible. The new value is plane-independent and visible in both themes while still reading as a slight change, not a lift | 7.1 |
| **pending** | **D-9. A new semantic colour `color_outline_control` is added** (`#646C7C` dark, `#7D8BA3` light, `#6A6A6E` / `#767679` mono) for input, outlined-button and segmented-track boundaries, because `color_outline` measures 1.45:1 on the dark ground and fails the WCAG 1.4.11 3:1 floor for UI-component boundaries. `color_outline_variant` keeps its 1 px hairline role, which is structural decoration and exempt | 3.5, 14.1 |
| **pending** | **D-10. On the light theme, status-chip text uses dedicated darkened tokens** (`color_success_text` `#065132`, `color_destructive_text` `#C42B32`, `color_warning_text` `#6B5000`) because the status hue on its own 18% fill measures 4.05:1 (green), 4.22:1 (red) and 4.82:1 (amber) and fails AA | 3.5, 6.8 |
| **pending** | **D-11. Press scale is 0.97 everywhere**, at `dur_90` in and `dur_160` out. Today `press_scale.xml` is 0.96, `nav_press.xml` is 0.92 at hard-coded 100/120 ms, and desktop is 0.97 at 120 ms | 3.7, 7.1 |
| **pending** | **D-12. The line-height column is added to the type ramp** (40 / 28 / 20 / 20 / 20 / 18 / 16 / 14 / 24). Neither platform declares line heights today, so the ramp's stated 1.2 / 1.45 / 1.35 ratios are unenforced and the two platforms render different leading | 3.4, 5.7 |

---

## 10. Conformance

### 10.1 The token pre-flight

- [ ] Every value in the change comes from section 1, 2 or 3
- [ ] Zero raw hex in `res/layout/**`, `res/menu/**`, `Views/**`
- [ ] Zero off-scale spacing
- [ ] Every text element applies a role; no inline `textSize` / `FontSize`
- [ ] Radii are 12 / 16 / 20 / 24-top / 100 and nothing else
- [ ] `?attr/...` on Android, `{DynamicResource ...}` on desktop, never `StaticResource Brush.*`
- [ ] Any new token has a purpose comment **and** its measured contrast ratio
- [ ] The token exists on **both** platforms, with the mirrored name
- [ ] Dark, light, mono-dark and mono-light all checked
- [ ] Every state in the section 7 matrix implemented and looked at
- [ ] Longest real Russian string fits at 320 dp and at font scale 200%
- [ ] Focus ring visible on every desktop control
- [ ] Reduced motion toggled on and the screen re-checked

### 10.2 The direction check (`03-direction.md` 12.2, abbreviated)

Count the blue (one filled accent surface max, zero on a settings screen). Count the planes (three
including ground, nothing raised at rest). Measure the text origin (68, everywhere). Count the gaps
(at least three distinct values). Count the cards (one, or zero). Find a number (figure face,
right-aligned, tabular, and it does not move when you change `1` to `8`). Find a Russian string set
in Space Grotesk (there must not be one). Squint.

### 10.3 Mechanical greps

Android, from `/home/user/dp/V2rayNG/app/src/main/`:

```bash
# raw colour literals in layouts
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:(tint|strokeColor|cardBackgroundColor))="#' res/layout/ res/menu/
# off-scale spacing (allowed: 0 1 2 3 4 8 12 16 20 22 24 28 32 36 40 44 48 52 56 64 68 72 80 100 152 176 720)
grep -rnoE '"(-?[0-9]+)dp"' res/layout/ | grep -vE '"(0|1|2|3|4|8|12|16|20|22|24|28|32|36|40|44|48|52|56|64|68|72|80|100|152|176|720)dp"'
# roles, not sizes
grep -rn 'android:textSize' res/layout/
# no synthetic bold, no all-caps
grep -rn 'android:textStyle="bold"\|textAllCaps="true"' res/layout/ res/values/
# no shadows
grep -rnE 'android:elevation="[1-9]|app:cardElevation="[1-9]' res/layout/
# no gradients, no glows
grep -rn '<gradient' res/drawable*/
# brand face must not set a Russian string: every space_grotesk consumer must be a
# Display/Chip/Numeric/Wordmark role
grep -rn '@font/space_grotesk' res/layout/ res/values/styles.xml
# orphan face gone
grep -rn 'montserrat' res/ java/
# tabular figures applied wherever Numeric is used
grep -rLn 'fontFeatureSettings' $(grep -rl 'App.Numeric' res/layout/)
# retired radii
grep -rnE 'cornerRadius="(26|22|18|14)dp"' res/layout/
# dash and ellipsis debt
grep -rn -e '—' -e '–' res/values*/strings*.xml
grep -rn '\.\.\.' res/values*/strings*.xml
```

Desktop, from `/home/user/v2rayN/v2rayN/v2rayN.Desktop/`:

```bash
# inline hex outside the token dictionary
grep -rnE '(Background|Foreground|BorderBrush|Fill|Stroke)="#' Views/
# frozen theme values
grep -rn 'StaticResource Brush\.' Views/ Assets/GlobalStyles.axaml
# roles, not sizes
grep -rn 'FontSize="' Views/
# brand face on prose
grep -rn 'Font.Grotesk\|Font.Brand' Views/ Assets/GlobalStyles.axaml
# banned materials
grep -rn 'LinearGradientBrush\|RadialGradientBrush\|BoxShadow\|ExperimentalAcrylic' Views/ Assets/
# off-scale spacing
grep -rnoE '(Margin|Padding|Spacing)="[0-9, ]+"' Views/
# retired tokens
grep -rn 'Radius.Search\|Radius.Traffic\|Radius.Button\|Brush.HomeGradient\|Brush.ConnectGlow\|Brush.Ring\.' Views/ Assets/
# focus rings present
grep -rn 'focus-visible\|:focus' Assets/GlobalStyles.axaml
# dash debt
grep -rn -e '—' -e '–' Common/L.*.cs
```

**Baseline to clear** (measured 2026-07-26): 325 off-scale `dp` values across 25 Android layout
files; 3 inline hex values in desktop `Views/`; 22 dash hits in Android strings and 44 in
`Common/L.*.cs`; 16 `Font.Grotesk` setters on desktop prose classes; 14 literal `cornerRadius`
values in Android layouts. Every screen touched during the rebuild leaves **its own files at zero**.

### 10.4 Migration order

The order matters: doing 3 before 1 rewrites every layout twice.

1. **Tokens.** `colors.xml`, `values-night/colors.xml`, `dimens.xml`, `motion.xml`, `attrs.xml`,
   `values-sw600dp/dimens.xml`, `GlobalResources.axaml`, `Motion.cs`. Nothing visual changes yet
   except the retired radii and the new hover.
2. **Type.** The ramp in `styles.xml` and `GlobalStyles.axaml`, the `font_ui` family, the
   `Font.Grotesk` to `Font.Ui` swap on the three blanket desktop setters, the 2.9.2 variable-font
   verification. This is where the Russian text stops being an accident.
3. **Component styles.** `Widget.App.*`, the theme-wide `materialButtonStyle` / `textInputStyle` /
   `materialCardViewStyle` wiring, the new desktop classes, the focus rings. At this point a layout
   that sets nothing renders correctly.
4. **Drawables.** Create the new shape and selector set, delete the gradient and glow set.
5. **Screens**, in the owner's stated priority: sign-in, then the first tab at launch, then Account,
   then Servers, then every settings tab, then the remaining sub-pages, then the raw-upstream
   editors.
6. **Delete** the dead surfaces (14 on Android, 11 on desktop) in the same change that would
   otherwise have restyled them.

Each step ends with section 10.1 ticked for the files it touched. A step that leaves a grep dirty is
not finished.
