# Recon — Android design-system token inventory & inconsistency audit

**Scope:** every design token declared in `V2rayNG/app/src/main/res/**`, plus a grep-level
audit of all 73 layouts and the Kotlin call sites that bypass the token layer.
**Repo root for all paths below:** `/home/user/dp/V2rayNG/app/src/main/`
**Law being audited against:** `/home/user/dp/CLAUDE.md` § "Design standard" (Incy = pure dark
+ ONE bright blue accent, Space Grotesk, Russian sentence-case, ONE spacing scale, 16dp
gutter, 40dp tiles / 22dp glyphs, 56dp rows / ≥48dp targets, no gradients/glows, no nested
cards, no ALL-CAPS headers, no emoji as chrome, body contrast ≥4.5:1).

Everything below was read from the files cited. Nothing is inferred from memory.

---

## 0. Headline numbers

| Metric | Value | Source |
|---|---|---|
| Layout files | 73 | `res/layout/` |
| Raw `…dp` values in layout size/margin/padding attrs | **944** | grep over `res/layout/*.xml` |
| `@dimen/…` token references in the same attrs | **893** | same |
| **True off-scale** dp occurrences (not on 4/8/12/16/24/32, not an existing component token) | **426** | script tally, §5.1 |
| Existing token values written as raw dp (40/22/56 instead of `@dimen/tile_size` etc.) | **118** | §5.1 |
| Hardcoded `android:textSize` in layouts | **109** across 9 distinct sizes | §5.2 |
| Hardcoded hex colours in layouts | **0** (good) | grep `"#` in `res/layout/` |
| Hardcoded hex colours in drawables | **7** | §3.5 |
| Interactive targets < 48dp | **37** | §5.4 |
| `drawable-night/` vectors that are pure black↔white copies of `drawable/` | **31** | §3.6 |
| Distinct accent hue families in `colors.xml` | **5** (blue, green, red, amber, orange) | §1 |
| Declared dimens tokens that are **never referenced** | **10** | §2.4 |

**One-line verdict:** the token layer (`dimens.xml`, `styles.xml`, `themes.xml`, `attrs.xml`,
`motion.xml`) is genuinely well-designed and internally coherent. The *consumption* layer is
split roughly 50/50: the newer screens (`layout_setting_row`, `layout_settings_content`,
`activity_account`, `item_recycler_main`, `activity_main`) largely use it; a cluster of
settings sub-screens (`activity_local_proxy`, `activity_provider_settings`,
`activity_url_scheme_list`, `activity_backup`) runs an entirely **parallel, undeclared
12/14/10/60/68/44dp scale** and a parallel 15/16/12/13sp type ramp that never touches
`TextAppearance.App.*`.

---

## 1. Colour tokens

### 1.1 Brand / legacy layer — `res/values/colors.xml` (136 lines) vs `res/values-night/colors.xml` (112 lines)

| Token | Light (`values/colors.xml`) | Dark (`values-night/colors.xml`) | Line (light) |
|---|---|---|---|
| `brand_blue` | `#1E5FC7` | `#4C8DFF` | 4 |
| `brand_blue_dark` | `#17469A` | `#3B82F6` | 5 |
| `brand_cream` | `#F4F1EA` | `#141619` | 6 |
| `colorPing` | `#12B76A` | `#22C55E` | 8 |
| `colorPingRed` | `#E5484D` | `#F04452` | 9 |
| `colorConfigType` | `#1E5FC7` | `#4C8DFF` | 10 |
| `colorWhite` | `#FFFFFF` | — (inherits) | 11 |
| `color_fab_active` | `#1E5FC7` | `#4C8DFF` | 12 |
| `color_fab_inactive` | `#9AA6B8` | `#3A3F49` | 13 |
| `color_connected` | `#12B76A` | `#22C55E` | 14 |
| `color_upload` | `#1E5FC7` | `#4C8DFF` | 15 |
| `color_download` | `#1E5FC7` | `#4C8DFF` | 16 |
| `divider_color_light` | `#E4E9F2` | `#20242B` | 25 |
| `colorIndicator` | `@color/md_theme_primary` | — | 26 |

### 1.2 Icon accents (light values, night inherits except where noted)

| Token | Value | Line |
|---|---|---|
| `icon_blue` | `#4C8DFF` | 19 |
| `icon_green` | `#22C55E` | 20 |
| `icon_purple` | `#4C8DFF` ← **byte-identical to `icon_blue`** | 21 |
| `icon_yellow` | `#EAB308` | 22 |
| `icon_red` | `#F04452` | 23 |
| `icon_orange` | `#FB923C` | 24 |
| `icon_tile_blue` | `#334C8DFF` (20 % α) | 38 |
| `icon_tile_green` | `#3322C55E` | 39 |
| `icon_tile_orange` | `#33FB923C` | 40 |
| `icon_tile_purple` | `#334C8DFF` ← **identical to `icon_tile_blue`** | 41 |
| `icon_tile_red` | `#33F04452` | 42 |
| `icon_tile_yellow` | `#33EAB308` | 43 |
| `icon_tile_neutral` | `#E3EAF4` / night `#20242B` | 49 / night 27 |
| `icon_glyph_neutral` | `#54607A` / night `#9BA1AD` | 50 / night 28 |

### 1.3 Chip / ping accents

| Token | Light | Dark | Line (light) |
|---|---|---|---|
| `chip_type_text` | `#14468F` | `#4C8DFF` | 30 / night 19 |
| `chip_json_text` | `#7A5C00` | `#EAB308` | 32 / night 20 |
| `chip_json_bg` | `#F5E6B0` | `#3A2E00` | 33 / night 21 |
| `ping_good` | `#0B7D4A` | `#22C55E` | 35 / night 22 |
| `ping_bad` | `#C42B32` | `#F04452` | 36 / night 23 |

### 1.4 Material 3 role palette — `md_theme_*`

**Light** (`values/colors.xml:53-96`, `124-128`):
primary `#1E5FC7` · onPrimary `#FFFFFF` · primaryContainer `#D8E4FF` · onPrimaryContainer `#001A43`
secondary `#3B6FD0` · onSecondary `#FFFFFF` · secondaryContainer `#DCE6FF` · onSecondaryContainer `#0A1F45`
tertiary `#12B76A` · onTertiary `#FFFFFF` · tertiaryContainer `#A8F0CE` · onTertiaryContainer `#00201A`
error `#BA1A1A` · errorContainer `#FFDAD6` · onError `#FFFFFF` · onErrorContainer `#410002`
background `#F4F7FC` · onBackground `#111826`
surface `#FFFFFF` · onSurface `#111826` · surfaceVariant `#E9EEF7` · onSurfaceVariant `#54607A`
inverseSurface `#2A3142` · inverseOnSurface `#F1F4FA`
outline `#C3CCDC` · outlineVariant `#DCE3EF`
inversePrimary `#AEC7FF` · shadow `#000000` · surfaceTint `#1E5FC7` · scrim `#000000`
Surface ramp: Lowest `#FFFFFF` → Low `#F7F9FD` → `#F1F4FA` → High `#EAEFF7` → Highest `#E3EAF4`

**Dark** (`values-night/colors.xml:31-84`):
primary `#4C8DFF` · onPrimary `#00183A` · primaryContainer `#17325C` · onPrimaryContainer `#CFE0FF`
secondary `#7FA8FF` · secondaryContainer `#17325C` · onSecondaryContainer `#CFE0FF`
tertiary `#22C55E` · onTertiary `#00210F` · tertiaryContainer `#0C3F22` · onTertiaryContainer `#A6F2C4`
error `#F04452` · errorContainer `#5C1420` · onError `#FFFFFF` · onErrorContainer `#FFD9DD`
background `#0A0B0D` · onBackground `#F2F4F8`
surface `#141619` · onSurface `#F2F4F8` · surfaceVariant `#1E2126` · onSurfaceVariant `#9BA1AD`
inverseSurface `#F2F4F8` · inverseOnSurface `#141619`
outline `#2A2E36` · outlineVariant `#20242B`
inversePrimary `#1E5FC7` · surfaceTint `#4C8DFF`
Surface ramp: Lowest `#08090B` → Low `#111316` → `#141619` → High `#1A1D21` → Highest `#20242B`

### 1.5 Mono palette

**Light** (`values/colors.xml:101-135`): primary `#111214` · primaryContainer `#E6E6E8` ·
secondary `#3A3A3D` · tertiary `#111214` · background `#FFFFFF` · surface `#FFFFFF` ·
surfaceVariant `#F1F1F2` · onSurfaceVariant `#5A5A5E` · outline `#D2D2D6` ·
outlineVariant `#E6E6E8` · fab_active `#111214` · fab_inactive `#C7C7CC` · connected `#111214`
Ramp: `#FFFFFF` / `#FAFAFB` / `#F4F4F5` / `#EEEEEF` / `#E7E7E9`

**Dark** (`values-night/colors.xml:87-111`): primary `#FFFFFF` · onPrimary `#111214` ·
primaryContainer `#2A2A2E` · secondary `#C8C8CC` · background `#000000` · surface `#121214` ·
surfaceVariant `#1E1E20` · onSurfaceVariant `#B0B0B4` · outline `#38383C` · outlineVariant `#28282C`
Ramp: `#000000` / `#0E0E10` / `#141416` / `#1B1B1E` / `#232326`
**Note:** `mono_fab_inactive` is declared only in `values/` (`#C7C7CC`, line 120) — there is
**no night override**, so in Mono-dark the idle connect colour is a light grey `#C7C7CC` on
`#000000`. Probably intentional (idle = visible), but it is an asymmetry in the table.

### 1.6 Launcher colours
`values/ic_launcher_background.xml` → `#1E5FC7` (light-theme brand blue, not the dark `#4C8DFF`).
`values/ic_banner_background.xml` → `#FFFFFF` (TV banner).
`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` both declare
`<monochrome android:drawable="@drawable/ic_launcher_foreground" />` — themed-icon support is present.

---

## 2. Dimension tokens — `res/values/dimens.xml` (41 lines)

### 2.1 Legacy upstream scale (lines 3-11)
`padding_spacing_dp4` 4dp · `padding_spacing_dp8` 8dp · `padding_spacing_dp16` 16dp ·
`image_size_dp24` 24dp · `view_height_dp36` 36 · `view_height_dp48` 48 · `view_height_dp64` 64 ·
`view_height_dp120` 120 · `view_height_dp160` 160

### 2.2 Incy scale (lines 13-19)
`space_4` 4 · `space_8` 8 · `space_12` 12 · `space_16` 16 · `space_24` 24 · `space_32` 32

### 2.3 Radii / components (lines 21-40)
`radius_chip` 12 · `radius_card` 20 · `radius_tile` 12 · `radius_pill` 100 · `radius_sheet` 24
`tile_size` 40 · `tile_glyph` 22 · `row_min_height` 56 · `screen_gutter` 16
`sub_card_height` 152 · `dot_size` 6 · `dot_size_active` 8 · `dot_gap` 8

### 2.4 Actual usage (grep over `res/layout/` + `res/drawable/` + `*.kt`)

| Token | refs in layouts | Note |
|---|---|---|
| `space_16` | 146 | |
| `padding_spacing_dp16` | **142** | **duplicate of `space_16`** — same 16dp, two names |
| `space_8` | 95 | |
| `space_12` | 89 | |
| `tile_size` | 80 | |
| `tile_glyph` | 80 | |
| `space_4` | 67 | |
| `padding_spacing_dp8` | **53** | **duplicate of `space_8`** |
| `screen_gutter` | 49 | **also 16dp — a third name for the same value** |
| `row_min_height` | 39 | |
| `image_size_dp24` | 32 | |
| `radius_card` | 21 | |
| `space_24` | 17 | |
| `radius_pill` | 3 | |
| `sub_card_height` | 2 | |
| `space_32` | 2 | |
| `padding_spacing_dp4` | 1 | |
| `radius_chip`, `radius_tile`, `radius_sheet`, `dot_size`, `dot_size_active` | 0 in layouts | used only inside drawables |
| **`dot_gap`** | **0 anywhere** | dead |
| **`view_height_dp36 / 48 / 64 / 120 / 160`** | **0 anywhere** | dead (5 tokens) |

---

## 3. Everything else in `res/`

### 3.1 Type scale — `res/values/styles.xml`

| Style | Font | Weight | Size | Tracking | Colour | Line |
|---|---|---|---|---|---|---|
| `TextAppearance.App.Display` | Space Grotesk | 700 | 34sp | −0.02 | `?colorOnSurface` | 56-62 |
| `TextAppearance.App.Headline` | Space Grotesk | 700 | 24sp | −0.01 | `?colorOnSurface` | 65-71 |
| `TextAppearance.App.Title` | Space Grotesk | 700 | 16sp | 0.0 | `?colorOnSurface` | 74-80 |
| `TextAppearance.App.Title.Medium` | Space Grotesk | 500 | 16sp | — | inherited | 83-85 |
| `TextAppearance.App.Body` | system | — | 14sp | 0.01 | `?colorOnSurface` | 88-92 |
| `TextAppearance.App.Subtitle` | system | — | 13sp | 0.01 | `?colorOnSurfaceVariant` | 95-99 |
| `TextAppearance.App.Caption` | system | — | 12sp | 0.02 | `?colorOnSurfaceVariant` | 102-106 |
| `TextAppearance.App.Chip` | Space Grotesk | 500 | 11sp | 0.04 | inherited | 109-114 |
| `TextAppearance.App.Numeric` | Space Grotesk | — | **none declared** | 0.0 | `?colorOnSurface` | 122-127 |
| `SettingsSectionLabel` | Space Grotesk | 700 | 16sp | 0, `allCaps=false` | `?colorOnSurface` | 6-17 |
| `BottomNavLabel` | inherited | 500 | 11sp | — | — | 22-25 |
| `BottomNavIndicator` | — | — | 64×34dp, marginH 6dp | — | `md_theme_primaryContainer` | 27-32 |
| `ToolbarBrandTitle` | Space Grotesk | 700 | 20sp | −0.01 | `?colorOnBackground` | 35-41 |
| `ThemeOverlay.Departament.Dialog` | — | — | corner 20dp | — | theme attrs | 139-160 |
| `Departament.Dialog.Title` | — | — | — | — | `?colorOnSurface` | 163-165 |
| `Departament.Dialog.Button` | — | — | — | — | `?colorPrimary` | 168-171 |

Adoption across layouts: `App.Body` 44 · `App.Subtitle` 39 · `App.Title` 26 ·
`App.Caption` 15 · `App.Numeric` 5 · `BottomNavLabel` 4 · `App.Headline` 3 · `App.Chip` 3 ·
`App.Display` 1 · **`App.Title.Medium` 0 (dead)**.
Competing legacy appearances still in use: `TextAppearance.AppCompat.Subhead` **15** ·
`AppCompat.Small` **8** · `AppCompat.Medium` 1 · `AppCompat.Tooltip` 1.

### 3.2 Themes — `res/values/themes.xml` (196 lines) + `values-night/themes.xml` (10 lines)
- `AppThemeBase` (4-107) → maps all `md_theme_*` to M3 roles, transparent system bars
  (64-68), connect-state attrs (73-75), themed accent attrs (78-99), app-wide dialog theme
  (104-106).
- `AppThemeDayNight` (110-114, light) / `values-night/themes.xml:5-9` (dark) — only differ in
  `windowLightStatusBar` / `windowLightNavigationBar`.
- `AppThemeDayNight.NoActionBar` (117-120), `.Translucent` (122-126).
- `BrandedSwitch` (128-130) — sets `colorPrimary` to `@color/color_fab_active`.
- `ThemeOverlay.Mono` (135-194) — applied at runtime, `ui/BaseActivity.kt:51`
  (`theme.applyStyle(R.style.ThemeOverlay_Mono, true)`).
- Manifest uses only `AppThemeDayNight.NoActionBar` (×1) and `.NoActionBar.Translucent` (×3).
- **The whole multicolour icon system is collapsed to blue in the theme** (themes.xml:88-99):
  `iconTintGreen/Orange/Purple/Yellow` → `@color/icon_blue`,
  `iconTileBgGreen/Orange/Purple/Yellow` → `@color/icon_tile_blue`. Only `iconTintRed` /
  `iconTileBgRed` stay red. Good — but the *resource names in the layouts still say
  green/orange/purple/yellow*, which is a maintenance trap (§4.2).

### 3.3 Custom attrs — `res/values/attrs.xml` (40 lines)
`connectIdleColor`, `connectActiveColor`, `connectedColor` (9-11);
`chipTypeText`, `chipJsonText`, `chipJsonBg` (17-19);
`pingGood`, `pingBad`, `indicatorColor` (22-24);
`iconTintBlue/Green/Orange/Purple/Red/Yellow` (27-32);
`iconTileBgBlue/Green/Orange/Purple/Red/Yellow` (35-40).
Also contains a stray **style** (`TabLayoutTextStyle`, lines 4-6) in an attrs file — and it is
**never referenced**.

### 3.4 Motion — `res/values/motion.xml` (26 lines) + `res/interpolator/` + `res/anim/`
`motion_press_in` 90 · `motion_press_out` 160 · `motion_state` 220 · `motion_reveal` 300 ·
`motion_stagger` 40 · `motion_emphasis` 600.
Interpolators: `ease_out_quart` cubic-bezier(0.25,1,0.5,1) · `ease_out_quint` (0.22,1,0.36,1)
· `ease_standard` (0.2,0,0,1).
Anim: `press_scale.xml` (0.96 press, uses tokens + `ease_out_quart`, 9 usages),
`nav_press.xml` (0.92 press, **hardcoded 100ms/120ms — bypasses the motion tokens**, lines
9/14/23/28), `connect_confirm.xml` (scale 1.0→1.6 + fade, `motion_emphasis`),
`shield_assemble.xml` (**hardcoded `android:duration="400"`, line 7 — bypasses the tokens**).

### 3.5 Drawables — `res/drawable/` (138 files)

Classification:
- **Gradients / glows (7):** `bg_home_gradient.xml`, `bg_home_gradient_mono.xml`,
  `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`, `bg_bottom_nav_scrim.xml`,
  `bg_nav_header.xml`, plus night twins `drawable-night/bg_home_gradient*.xml`,
  `drawable-night/bg_connect_glow.xml`.
- **Rings (4):** `bg_connect_ring.xml`, `bg_connect_ring_mono.xml` + night twin.
- **Surfaces / cards (10):** `bg_card_incy`, `bg_server_card`, `bg_server_row`, `bg_sheet_top`,
  `bg_dialog`, `bg_settings_glass`, `bg_acc_option`, `bg_buy_option`,
  `bg_buy_option_selected`, `bg_search_pill`.
- **Chips / tiles (12):** `bg_acc_badge`, `bg_acc_chip`, `bg_chip_gold`, `bg_type_chip`,
  `bg_speed_chip`, `bg_flag_tile`, `bg_icon_blue/green/orange/purple/red/yellow`,
  `bg_icon_neutral`.
- **Dots / handles / misc (9):** `bg_nav_dot`, `dot_active`, `dot_inactive`, `bg_status_dot`,
  `bg_sheet_handle`, `bg_skeleton`, `bg_toast_status`, `custom_divider`, `ripple_card`.
- **Inputs (2):** `bg_lp_input`, `bg_avatar_circle`/`bg_avatar_edit`.
- **Progress (1):** `bg_traffic_gradient.xml` — despite the name it is a **flat** two-layer
  clip drawable (`?colorSurfaceVariant` track + `?colorPrimary` fill), no gradient. Good.
- **Vector icons (92 `ic_*.xml`)** + 1 PNG (`nav_header_bg.png`, 12 KB).

Hardcoded hex in drawables (7 occurrences, all "12 %-blue selection fill" / ring strokes):
`bg_buy_option_selected.xml:7` `#1F4C8DFF` · `bg_server_row.xml:8` and `:18` `#1F4C8DFF` ·
`bg_connect_ring.xml:8` `#2E1E5FC7`, `:21` `#701E5FC7` · `bg_connect_ring_mono.xml:8`
`#33808080`, `:20` `#66808080`.

Radii hardcoded inside drawables rather than referencing `@dimen/radius_*`:
`bg_acc_option:3` 12dp · `bg_buy_option:5` 14dp · `bg_dialog:10` 20dp ·
`bg_icon_{blue,green,orange,purple,red,yellow}:3` 12dp · `bg_lp_input:3` 12dp ·
`bg_nav_dot:7` 2dp · `bg_search_pill:5` **14dp** · `bg_sheet_handle:5` 2dp ·
`bg_toast_status:8` **24dp** · `bg_traffic_gradient:10,18` 8dp ·
`ic_rounded_corner_{active,inactive}:3` 20dp.

### 3.6 `res/drawable-night/` (37 files)
- 4 are legitimate night surface variants: `bg_home_gradient`, `bg_home_gradient_mono`,
  `bg_connect_glow`, `bg_connect_ring`.
- 1 PNG: `nav_header_bg.png`.
- **31 are byte-identical vector copies that differ only in `android:fillColor`
  (`#FF000000` → `#FFFFFFFF`)**: `ic_about_24dp`, `ic_action_done`, `ic_add_24dp`,
  `ic_backup_24dp`, `ic_cloud_download_24dp`, `ic_copy`, `ic_delete_24dp`,
  `ic_description_24dp`, `ic_edit_24dp`, `ic_fab_check`, `ic_feedback_24dp`, `ic_file_24dp`,
  `ic_image_24dp`, `ic_lock_24dp`, `ic_logcat_24dp`, `ic_more_vert_24dp`, `ic_per_apps_24dp`,
  `ic_play_24dp`, `ic_privacy_24dp`, `ic_promotion_24dp`, `ic_restore_24dp`,
  `ic_routing_24dp`, `ic_save_24dp`, `ic_scan_24dp`, `ic_select_all_24dp`,
  `ic_settings_24dp`, `ic_share_24dp`, `ic_source_code_24dp`, `ic_stop_24dp`,
  `ic_subscriptions_24dp`, `ic_telegram_24dp`.
  (`ic_check_update_24dp` and `ic_outline_filter_alt_24` differ in more than fill.)

Icon fill-colour census across `drawable/ic_*.xml`: `#FF000000` ×58, `@android:color/white`
×25, `#FFFFFFFF` ×9, `#00000000` ×5, `#FFFFFF` ×4, `#000000` ×3, `#000` ×1,
`?attr/colorControlNormal` ×1 — **7 different ways of writing "black or white", and exactly
one icon is theme-aware.**

Icon grid: `viewportWidth` is **24** (66 files) or **1024** (22 files) — two different icon
families (`ic_about`, `ic_backup`, `ic_feedback`, `ic_file`, `ic_image`, `ic_lock`,
`ic_logcat`, `ic_play`, `ic_privacy`, `ic_promotion`, `ic_qu_*`, `ic_restore`, `ic_routing`,
`ic_scan`, `ic_settings`, `ic_source_code`, `ic_stop`, `ic_subscriptions`, `ic_telegram` are
on the 1024 grid). All declare `android:width="24dp"` except `ic_launcher_foreground` (108dp)
and one 48dp asset.

### 3.7 `res/color/` (2 files)
- `bottom_nav_item_color.xml` — `?colorPrimary` when checked, `?colorOnSurfaceVariant`
  otherwise. **Zero references** (the custom nav in `activity_main.xml` tints in Kotlin).
- `color_highlight_material.xml` — references `@dimen/highlight_alpha_material_colored`, an
  AppCompat **private** dimen not declared in this project. **Zero references.**

### 3.8 `res/font/`
- `spacegrotesk.ttf` — variable font, weight axis 300-700.
- `space_grotesk.xml` — family pinning 400/500/700 on the same variable file.
- **`montserrat_thin.ttf` — present but referenced nowhere** (grep of `res/` + `java/`).

### 3.9 `res/menu/` (10 files)
`action_server`, `action_sub_setting`, `menu_app_picker`, `menu_asset`, **`menu_bottom_nav`**,
`menu_bypass_list`, `menu_logcat`, `menu_main`, `menu_routing_setting`, `menu_scanner`.
- **`menu_bottom_nav.xml` is dead** — no `.kt` or `.xml` reference; it also declares only 3
  items (home/servers/settings) while the shipping nav has 4 (`activity_main.xml:660`
  `nav_account`).
- Icon semantics: `ic_description_24dp` (a document glyph) is reused as the icon for *search*,
  *select proxy app*, *import proxy app* and *export proxy app*
  (`menu_bypass_list.xml:6,29,35,41`; `menu_app_picker.xml:8`).

### 3.10 `res/values-*` locale coverage
| Dir | files | `<string>` count |
|---|---|---|
| `values` | `strings.xml` + 20 `strings_*.xml` | 484 in `strings.xml`, ~810 total |
| `values-ru` | `strings.xml`, `strings_tv.xml` | 450 |
| `values-ar` / `-bn` / `-bqi-rIR` / `-fa` / `-vi` | `strings.xml` | 352-353 |
| `values-zh-rCN` / `-zh-rTW` | `strings.xml` | 353-355 |
| `values-sw360dp-v13` | 2 AppCompat private overrides | — |
| `values-night` | `colors.xml`, `themes.xml` | — |

The 20 `strings_*.xml` module files in `values/` (account, auth, buy, deeplink, devices,
history, home_shell, local_proxy, manual_add, nav, pay, perapp, provider, server_actions,
settings_hub, templates, tv, ui_polish) are **Russian text placed in the default locale** and
have **no `values-*` counterparts** — the departament modules are effectively RU-only.

---

## 4. Findings (ranked)

### 4.1 CRITICAL — a whole family of settings screens runs a parallel, undeclared scale

`activity_local_proxy.xml` (93 off-scale), `activity_provider_settings.xml` (62),
`activity_url_scheme_list.xml` (37), `activity_backup.xml` (26) — plus
`activity_routing_setting.xml` and `activity_user_asset.xml` — implement a rhythm that exists
nowhere in `dimens.xml`:

| Concern | Law (`dimens.xml`) | What these files use |
|---|---|---|
| Screen gutter | `screen_gutter` 16dp | **12dp** (`activity_local_proxy.xml:27-28`, `activity_provider_settings.xml`, `activity_url_scheme_list.xml`; 16 `marginStart="12dp"` + 14 `marginEnd="12dp"` + 8 `marginHorizontal="12dp"` across layouts) |
| Row inner padding | `space_16` | **14dp** (82 occurrences repo-wide; 32 in `activity_local_proxy.xml` alone, e.g. `:47-48`) |
| Row vertical padding | `space_12` | **10dp** (54 occurrences, e.g. `activity_local_proxy.xml:49`) |
| Row min height | `row_min_height` 56dp | **60dp** (`activity_local_proxy.xml:45`, ×8 in that file, ×9 in provider settings) |
| Divider inset | — (undeclared) | **68dp** here, **72dp** in `layout_settings_content.xml`, **16dp** in `activity_url_scheme_list.xml`, **44dp** in `drawable/custom_divider.xml:5-6`, and **none** in 6 other files |
| Card radius | `radius_card` 20dp | raw `cardCornerRadius="20dp"` (25 occurrences) — the token exists and is used 21× elsewhere |
| Icon tile / glyph | `tile_size` 40 / `tile_glyph` 22 | raw `40dp`/`22dp` (118 occurrences) |

Full off-scale value census across all layouts (excluding 0/1/2dp and the 4/8/12/16/24/32
scale, and excluding 40/22/56 which are real tokens written raw):
`14dp` ×74 · `18dp` ×70 · `10dp` ×54 · `60dp` ×34 · `42dp` ×21 · `72dp` ×19 · `44dp` ×22 ·
`36dp` ×22 · `68dp` ×13 · `3dp` ×12 · `6dp` ×10 · `20dp` ×8 · `34dp` ×4 · `52dp` ×7 ·
`80dp` ×4 · `76dp` ×3 · `28dp` ×3 · `27dp` ×2 · `13dp` ×1 · `45dp` ×1 — **426 total.**

### 4.2 HIGH — three spacing token families for the same values

`space_16` (146 refs), `padding_spacing_dp16` (142 refs) and `screen_gutter` (49 refs) are
**all 16dp**. `space_8` (95) and `padding_spacing_dp8` (53) are **both 8dp**.
`radius_chip` and `radius_tile` are **both 12dp** with different names.
Consequence: the gutter cannot be retuned independently of intra-component spacing, and a
"replace all 16dp" refactor has three different search targets.

`padding_spacing_dp16` / `padding_spacing_dp8` live entirely in the un-redesigned upstream
screens: `layout_tls.xml` (17 refs), `layout_tls_hysteria2.xml` (8), `activity_sub_edit.xml`
(11), `activity_server_proxy_chain.xml` (4), and the `activity_server_*.xml` family.

### 4.3 HIGH — 109 hardcoded `textSize` values, including a size that is not in the scale

| size | count | in scale? | worst files |
|---|---|---|---|
| 16sp | 31 | = `App.Title` | `activity_local_proxy` ×9, `activity_provider_settings` ×9, `activity_backup` ×4 |
| 12sp | 26 | = `App.Caption` | `activity_local_proxy` ×16 |
| 13sp | 24 | = `App.Subtitle` | `activity_url_scheme_list` ×10, `activity_local_proxy` ×5 |
| **15sp** | **18** | **NOT IN THE SCALE** | `activity_url_scheme_list` ×10, `activity_local_proxy` ×6, `item_recycler_bypass_list` ×1 |
| 14sp | 5 | = `App.Body` | |
| 18sp | 2 | **not in scale** | `activity_tv_receive.xml:27`, `item_recycler_main.xml:49` |
| 22sp | 1 | **not in scale** | `item_buy_tariff.xml:48` |
| 20sp | 1 | **not in scale** | `activity_account.xml:76` |
| 11sp | 1 | = `App.Chip` | `layout_subscription_meta_bar.xml:175` |

23 layout files contain `<TextView>` and **zero** `textAppearance` — i.e. they never touch the
type system at all: `activity_backup`, `activity_bypass_list`, `activity_local_proxy`,
`activity_provider_settings`, `activity_routing_setting`, `activity_server_custom_config`,
`activity_server_{hysteria2,shadowsocks,socks,trojan,vless,vmess,wireguard}`,
`activity_tv_receive`, `activity_tv_send`, `activity_url_scheme_list`, `activity_user_asset`,
`dialog_config_filter`, `item_recycler_routing_setting`, `item_recycler_user_asset`,
`layout_tls`, `layout_tls_hysteria2`, `toast_status`.

`TextAppearance.App.Numeric` (`styles.xml:122-127`) declares **no `textSize`**, so every one of
its 5 usages must add a raw size: `activity_main.xml:113` (13sp), `:128` (14sp), `:155` (13sp),
`item_recycler_main.xml:121` (12sp), `layout_subscription_meta_bar.xml:175` (11sp) — four
different sizes for the same "numeric" role.

### 4.4 HIGH — competing accents leak past the theme layer in Kotlin

The theme correctly collapses all icon hues to blue (`themes.xml:88-99`), but four Kotlin
call sites use **raw colour resources**, so they keep their hue even under `ThemeOverlay.Mono`:

| File:line | Colour | Effect |
|---|---|---|
| `ui/adapter/PaymentsAdapter.kt:73` | `R.color.icon_green` | "Оплачено" chip stays green in Mono |
| `ui/adapter/PaymentsAdapter.kt:76` | `R.color.icon_orange` | "В обработке" stays orange |
| `ui/adapter/PaymentsAdapter.kt:79` | `R.color.icon_red` | "Ошибка" |
| `ui/adapter/PaymentsAdapter.kt:82` | `R.color.icon_yellow` | "Отменён" stays yellow |
| `ui/PaymentMethodSheet.kt:77,79` | `R.drawable.bg_icon_green` + `R.color.icon_green` | balance row tile stays green (the code comment at `:78` says this is deliberate) |
| `ui/MainActivity.kt:1916` | `R.color.color_connected` (`#12B76A`/`#22C55E`) | memory dot green |
| `ui/MainActivity.kt:1917` | `R.color.colorConfigType` | memory dot blue |
| `ui/MainActivity.kt:1918` | `R.color.colorPingRed` | memory dot red |
| `ui/MainActivity.kt:1313` | `R.color.colorPingRed` | traffic-warning red |
| `res/layout/activity_base.xml:28` | `app:indicatorColor="@color/color_fab_active"` | the shared toolbar progress bar stays blue in Mono — should be `?attr/colorPrimary` |

That is **4 non-blue, non-destructive hues** (green, orange, yellow, plus green again) still
reaching the screen, against the "ONE accent; red only for destructive" rule.

### 4.5 HIGH — 37 interactive targets below the 48dp minimum

| Size | Where |
|---|---|
| **36dp** | `layout_servers_header.xml:29,41,53,65` (collapse/refresh/speedtest/add — 4 in a row); `layout_subscription_meta_bar.xml:75,88,111,239` (ping/refresh/pin/telegram); `layout_home_account.xml:95` (avatar, tappable parent) |
| **40dp** | `layout_home_account.xml:62-65` (`btn_cta_dismiss`); `activity_buy_tariff.xml:213-217` and `:242-246` (device −/+ steppers, `tile_size` with `minHeight="0dp"`) |
| **42dp** | `activity_main.xml:161-164` (`btn_home_add`); `activity_url_scheme_list.xml:128,180,251,303,374,445,497,568,620` (9 `ImageButton`s) |
| **44dp** | `activity_local_proxy.xml:101-153` (5 memory chips), `:374,420,430,847,895,943,953` (7 copy/eye buttons); `item_device.xml:77` (delete) |
| **~36dp effective** | `activity_account.xml:111-127` (`btn_top_up`: `minHeight="0dp"` + 8dp vertical padding); `layout_subscription_meta_bar.xml:216-230` (`btn_support`: `minHeight="0dp"`, 12sp text) |

None of these declare a `TouchDelegate`.

### 4.6 MEDIUM — glyphs and emoji used as UI chrome

| File:line | Content | Issue |
|---|---|---|
| `layout/activity_main.xml:98` | `android:text="↑"` | upload arrow drawn as a text glyph, not a vector |
| `layout/activity_main.xml:140` | `android:text="↓"` | download arrow, same |
| `layout/layout_home_account.xml:71` | `android:text="✕"` (U+2715) | close button drawn as a text glyph in a 40dp `TextView` |
| `layout/item_recycler_main.xml:40-50` | `tv_flag` — a 28×28dp `TextView` at 18sp rendering a **country-flag emoji** | emoji as chrome; the tile is also off both `tile_size` (40) and the spacing scale |
| `layout/sheet_server_actions.xml:40` | `tools:text="🇳🇱 Amsterdam · Fast"` | same emoji flag surface |
| `util/FlagUtil.kt:19` | `private const val GLOBE = "🌐"` | emoji globe as the fallback "no country" icon |
| `layout/activity_account.xml:74` | `android:text="?"` | avatar fallback initial is a literal `?` |
| `layout/layout_home_account.xml:105` | `android:text="?"` | same |
| `layout/activity_main.xml:124` | `android:text="00:00:00"` | hardcoded literal, not a string resource |

### 4.7 MEDIUM — decorative gradients and glows are shipping

`CLAUDE.md` bans decorative gradients/glows. Present and wired:

| Drawable | Content | Used at |
|---|---|---|
| `drawable/bg_home_gradient.xml` | radial `#FFFFFF → #EEF3FB → #DFE6F1`, r=560dp | `activity_main.xml:8` (whole screen background) |
| `drawable-night/bg_home_gradient.xml` | radial **blue-tinted** `#1B2D50 → #0E141F → #0A0B0D` | same |
| `drawable/bg_connect_glow.xml` | radial blue halo `#4D4C8DFF → #1F3B82F6 → #001E5FC7` | `activity_main.xml:211` |
| `drawable-night/bg_connect_glow.xml` | radial `#594C8DFF → #264C8DFF → #004C8DFF` | same |
| `drawable/bg_connect_ring.xml` | two concentric strokes, hardcoded `#2E1E5FC7` / `#701E5FC7` | `activity_main.xml:218` and `:228` |
| `drawable/bg_bottom_nav_scrim.xml` | linear `?colorSurface → transparent`, on a **160dp-tall** View | `activity_main.xml:515-517` |
| `drawable/bg_nav_header.xml` | linear 135° `brand_blue → brand_blue_dark` | **unused** |

The night home gradient is the strongest offender: `#1B2D50` is a visibly blue wash across the
top 30 % of the "pure dark" home screen.

### 4.8 MEDIUM — contrast failures

Computed with the WCAG 2.x relative-luminance formula on the exact hex values above; alpha
tiles composited over the theme's `colorSurface`.

| Pair | Ratio | Verdict |
|---|---|---|
| `chip_type_text` `#4C8DFF` on dark `primaryContainer` `#17325C` (`item_recycler_main.xml:86`, 11sp `App.Chip`) | **3.98** | **FAILS AA** for small text (needs 4.5) |
| `icon_blue` `#4C8DFF` glyph on light `icon_tile_blue` (`#334C8DFF` over `#FFFFFF` = `#DBE8FF`) | **2.59** | **FAILS** WCAG 1.4.11 non-text (needs 3.0) — every settings tile in light mode |
| `icon_red` `#F04452` glyph on light `icon_tile_red` (= `#FCDADC`) | **2.87** | **FAILS** 1.4.11 |
| light `colorTertiary` `#12B76A` on `#FFFFFF` | 2.62 | fails if ever used for text/icons |
| light `color_fab_inactive` `#9AA6B8` on background `#F4F7FC` | 2.30 | idle-state affordance below 3:1 |
| dark `color_fab_inactive` `#3A3F49` on background `#0A0B0D` | 1.86 | idle-state affordance below 3:1 |

Passing (for the record): dark `onSurfaceVariant #9BA1AD` on surface 6.99 / on
`surfaceContainerHighest` 6.00 / on background 7.59; light `onSurfaceVariant #54607A` 6.30 /
5.21; both `ping_good`/`ping_bad` ≥ 4.88; light `chip_type_text #14468F` on `#D8E4FF` 7.15;
dark tile glyph on tile 4.26.

Hairlines: `outlineVariant` vs `surface` is **1.16:1** (dark) and **1.29:1** (light). Card
edges are therefore essentially invisible on dark — acceptable for a decorative border, but
worth knowing that the "1dp `colorOutlineVariant` stroke" that 46 cards rely on carries almost
no visual weight in the dark theme.

### 4.9 MEDIUM — two competing settings visual systems

- `res/layout/layout_settings_content.xml` (1536 lines, 12 cards) — the redesigned hub:
  `SettingsSectionLabel` headers, `@dimen/tile_size`/`tile_glyph` tiles, `App.Body`/
  `App.Subtitle`, `row_min_height`.
- `res/xml/pref_settings.xml` (354 lines) — a stock AndroidX `PreferenceScreen` hosted by
  `res/layout/activity_settings.xml:11-15`, with plain `<PreferenceCategory android:title=…>`
  headers, no tiles, no `TextAppearance.App.*`, no `space_*`.
  `res/values-sw360dp-v13/` even patches AppCompat's **private** `preference_category_padding_start`
  to 0dp to make it look closer.

Same product, two settings languages, reachable in the same session.

### 4.10 LOW-MEDIUM — dead / vestigial design resources

| Resource | Status |
|---|---|
| `dimen/dot_gap`, `view_height_dp36/48/64/120/160` | declared, **0 references** |
| `style/BottomNavIndicator` (`styles.xml:27-32`) | 0 references — the Material `BottomNavigationView` it styles was replaced by the hand-rolled nav in `activity_main.xml:525-701` |
| `style/TabLayoutTextStyle` (in **attrs.xml**:4-6) | 0 references |
| `style/TextAppearance.App.Title.Medium` | 0 references |
| `menu/menu_bottom_nav.xml` | 0 references, and stale (3 items vs 4 tabs) |
| `color/bottom_nav_item_color.xml` | 0 references |
| `color/color_highlight_material.xml` | 0 references; points at an AppCompat private dimen |
| `drawable/bg_chip_gold.xml` + `chipJsonBg`/`chipJsonText` attrs + `chip_json_*` colours | the drawable has 0 references; a complete dead amber accent subsystem (4 colours × 2 themes + 2 attrs) |
| `drawable/bg_nav_header.xml`, `drawable/nav_header_bg.png`, `drawable-night/nav_header_bg.png` | 0 references (leftover navigation-drawer header) |
| `color/brand_cream`, `colorWhite` | `brand_cream` reachable only through the dead `bg_nav_header`; `colorWhite` used once, in `drawable/ic_power_settings.xml:6` |
| `color/icon_tile_green/orange/purple/yellow` | 0 references — the theme remaps every one of them to `icon_tile_blue` |
| `font/montserrat_thin.ttf` | 0 references |
| `string/account_trial_badge` = **"ПРОБНЫЙ"** (`values/strings_account.xml:41`) | 0 references — the only ALL-CAPS user-facing string in the project, and it is dead |
| `drawable/bg_settings_glass.xml` | named "glass", is a flat `?colorSurface` solid; used once at `activity_settings.xml:6` |
| `layout/item_recycler_main.xml:34-38` | `layout_indicator` — a 0×0 `gone` View kept alive only because `MainRecyclerAdapter` still binds it (documented at `:30-33`) |

### 4.11 LOW — colour aliasing

The same hex is reachable under many names, which makes a palette change a multi-file edit:

- `#1E5FC7` (light) = `brand_blue`, `colorConfigType`, `color_fab_active`, `color_upload`,
  `color_download`, `md_theme_primary`, `md_theme_surfaceTint`, `ic_launcher_background` — **8 names**.
- `#4C8DFF` (dark) = the same eight + `icon_blue` + `icon_purple` + `chip_type_text`.
- `#12B76A` (light) = `colorPing`, `color_connected`, `md_theme_tertiary` — 3 names.
- `#22C55E` = `icon_green` (light) and `colorPing`/`color_connected`/`md_theme_tertiary` (dark).
- `icon_purple` **is** `icon_blue` (`#4C8DFF`); `icon_tile_purple` **is** `icon_tile_blue`.
- **Four near-duplicate reds in the light palette alone:** `#E5484D` (`colorPingRed`),
  `#F04452` (`icon_red`), `#C42B32` (`ping_bad`), `#BA1A1A` (`md_theme_error`).

### 4.12 LOW — radius drift

Declared: `radius_chip` 12 · `radius_tile` 12 · `radius_card` 20 · `radius_sheet` 24 ·
`radius_pill` 100.
Actually shipped in layouts/drawables: **2, 8, 12, 14, 16, 18, 20, 22, 24, 26, 88, 100** dp.

Off-token cases: `cardCornerRadius="16dp"` (`activity_server_custom_config.xml:38,67`),
`"18dp"` (`item_recycler_bypass_list.xml:14`), `"88dp"` (`activity_main.xml:262` — the connect
disc, arguably a pill), `cornerRadius="26dp"` ×7 (`activity_login.xml:64,105,202,273,296`,
`activity_buy_tariff.xml:303`, `layout_home_empty.xml:118` — 52dp-tall CTAs, i.e. hand-computed
pills where `@dimen/radius_pill` exists and is used 3× in `activity_account.xml`),
`cornerRadius="22dp"` ×2 (`activity_buy_tariff.xml:75`, `activity_payment_history.xml:70`),
`cornerRadius="20dp"` ×2 (`activity_buy_tariff.xml:225,254` — on 40dp buttons),
`bg_search_pill.xml:5` 14dp, `bg_buy_option.xml:5` 14dp **vs** `bg_buy_option_selected.xml:5`
`@dimen/radius_card` (20dp) — **the selected and unselected states of the same control have
different corner radii**.

### 4.13 LOW — "nested cards"

No `MaterialCardView` is nested inside another (verified by an XML-stack walk over all 73
layouts). The only card-inside-card *look* is `bg_lp_input` (an outlined input field, itself a
1dp-stroked rounded box) placed inside a 1dp-stroked `MaterialCardView` at
`activity_local_proxy.xml:360, 406, 450, 474` (parent card at `:243`) and `:832, 879, 927`
(parent at `:508`) — 7 occurrences of an outlined box inside an outlined box.

### 4.14 LOW — motion tokens partially bypassed

`anim/nav_press.xml` hardcodes `100`/`120` ms (lines 9, 14, 23, 28) instead of
`@integer/motion_press_in` / `motion_press_out`; `anim/shield_assemble.xml:7` hardcodes `400`.
`ui/MainActivity.kt:531-532` hardcodes `(56 * density)` and `(16 * density)` instead of
reading `@dimen/row_min_height` / `@dimen/space_16`.

### 4.15 LOW — i18n gaps that surface as English text in a Russian UI

**33 keys exist only in `values/strings.xml` with no `values-ru` override.** Most are already
Russian in the default file, but these are genuinely English and will render as English on
every device:

`memory_app_usage` "App memory" · `memory_normal` "Normal" · `memory_elevated` "Elevated" ·
`memory_high` "High" · `memory_value` "%1$d MB · %2$s" ·
`menu_item_fast_connect` "Fast connect (fastest server)" ·
`ping_method_http` / `ping_method_icmp` / `ping_method_real` / `ping_method_tcp` (all English) ·
`title_pref_color_theme` "Color theme" · `title_pref_ping_method` "Ping method" ·
`title_pref_show_memory` "Show memory usage on home" ·
`summary_pref_show_memory` "Display this app's live memory usage (MB) on the home screen" ·
`color_theme_blue` "Blue (departament)" · `color_theme_mono` "Black & white" ·
`speed_zero` "0 KB/s".

Several of these are the labels of the Settings rows the user sees most (`title_pref_color_theme`,
`title_pref_ping_method`, `title_pref_show_memory`) — they sit directly under Russian
`SettingsSectionLabel` headers.

Also: **one key has two different Russian strings** —
`home_empty_title` is `"У вас пока не добавлены подписки."` in `values/strings.xml` but
`"Пока нет подписок"` in `values-ru/strings.xml:10`. The `values-ru` copy wins on RU devices;
the default-locale copy (sentence with a trailing period, wordier) is what everyone else sees.

`values/strings.xml:338` still defines `bottom_nav_more` = "More" / `values-ru:517` = "Настройки"
alongside `bottom_nav_settings` — a leftover from the "More"→"Настройки" rename.

### 4.16 INFO — the spec document disagrees with the implemented tokens

`/home/user/dp/docs/design-system-2026.md` §3.1-3.5 (lines 144-215) specifies tokens that were
**not** adopted and that contradict `CLAUDE.md`:

| Doc says | Implemented / CLAUDE.md says |
|---|---|
| `space_2 / 20 / 48` tokens | not declared; scale is 4/8/12/16/24/32 |
| `radius_xs 8 / sm 12 / md 16 / lg 24 / xl 28`, "standardise on **16dp** cards" | `radius_chip 12 / radius_tile 12 / radius_card **20** / radius_sheet 24 / radius_pill 100` |
| "Keep the platform font (Roboto / system)" | Space Grotesk is the brand font (`styles.xml`, `CLAUDE.md`) |
| Body **15sp**, Headline 22sp, Title 19sp, Caption 13sp monospace | Body 14 / Headline 24 / Title 16 / Caption 12 |
| "Raised … true elevation **6dp**" with tinted shadows | every card in the app is `cardElevation="0dp"` (46 occurrences), zero `elevation=` above 0 anywhere in `res/layout/` |
| "gradient ring", "glass / translucent Settings" (§4, lines 316-376) | `CLAUDE.md` bans decorative gradients; `bg_settings_glass` is a flat solid |

Anyone opening `design-system-2026.md` to do design work will produce output that violates
`CLAUDE.md`. The doc needs a "superseded by CLAUDE.md" banner or a rewrite.

---

## 5. Appendix — raw offender tables

### 5.1 Off-scale dp per layout (top 16)

| # off-scale | File | Values |
|---:|---|---|
| 93 | `activity_local_proxy.xml` | 14×32, 10×22, 44×19, 60×8, 68×6, 6×6 |
| 62 | `activity_provider_settings.xml` | 14×27, 10×15, 60×9, 18×6, 68×5 |
| 53 | `layout_settings_content.xml` | 18×36 (chevrons), 72×17 (divider inset) |
| 37 | `activity_url_scheme_list.xml` | 42×18, 60×9, 10×9, 14×1 |
| 28 | `activity_main.xml` | 3×12, 80×4, 34×4, 42×3, 230×2, 176×2, 160×1 |
| 26 | `activity_account.xml` | 18×11, 48×6, 52×2, 14×2, 72×2, 140, 200, 120 |
| 26 | `activity_backup.xml` | 14×8, 18×8, 60×4, 10×4, 68×2 |
| 14 | `layout_subscription_meta_bar.xml` | 36×8, 48×2, 20×2, 13×1, 160×1 |
| 11 | `layout_servers_header.xml` | 36×8, 14×2, 44×1 |
| 7 | `activity_buy_tariff.xml` | 76×3, 48×2, 28×1, 52×1 |
| 7 | `layout_servers_empty.xml` | 20×3, 64, 28, 14, 10 |
| 6 | `activity_routing_setting.xml` | 14×2, 18×2, 60, 10 |
| 6 | `activity_tv_receive.xml` | 48×2, 27×2, 320×2 |
| 6 | `activity_tv_send.xml` | 48×2, 20, 14, 18, 6 |
| 6 | `activity_user_asset.xml` | 14×2, 18×2, 60, 10 |
| 6 | `layout_home_account.xml` | 36×4, 18×2 |

Existing token values written as raw dp (40/22/56): `activity_provider_settings.xml` 36 ·
`activity_local_proxy.xml` 32 · `activity_backup.xml` 16 · `activity_routing_setting.xml` 4 ·
`activity_user_asset.xml` 4 · `item_recycler_routing_setting.xml` 4 ·
`item_recycler_sub_setting.xml` 4 · `item_recycler_user_asset.xml` 4 · 8 more files ×1-2.

### 5.2 The one clean reference implementation

`res/layout/layout_setting_row.xml` (81 lines) is the file every settings row should look
like: `row_min_height` (`:12`), `space_16` gutters (`:14-15`), `space_12` vertical (`:16`),
`press_scale` (`:17`), `tile_size`/`tile_glyph` (`:24-25, :30-31`), `icon_glyph_neutral`
(`:35`), `App.Body`/`App.Subtitle` (`:49, :57`), `space_4`/`space_8` (`:56, :67, :76`).
Its **only** deviation is the `18dp` chevron (`:74-75`) — which is exactly the value repeated
36× in `layout_settings_content.xml`, i.e. a missing `@dimen/chevron_size` token.

### 5.3 Divider conventions in use (five)

| Inset | Colour | Count | Where |
|---|---|---|---|
| 72dp | `?colorOutlineVariant` | 19 | `layout_settings_content.xml` ×17, `activity_account.xml` ×2 |
| 68dp | `?colorOutlineVariant` | 13 | `activity_local_proxy` ×6, `activity_provider_settings` ×5, `activity_backup` ×2 |
| 16dp | `?colorOutlineVariant` | 4 | `activity_url_scheme_list.xml` |
| none | `?colorOutlineVariant` | 6 | `activity_buy_tariff`, `activity_bypass_list`, `activity_login`, `activity_main`, `item_payment_method`, `layout_subscription_meta_bar` |
| 44dp | **`@color/divider_color_light`** (raw, not a theme attr) | drawable | `drawable/custom_divider.xml:5-6,10`, applied to 5 RecyclerViews: `MainActivity.kt:637,644`, `AppPickerActivity.kt:103`, `LogcatActivity.kt:42`, `PerAppProxyActivity.kt:47` |
