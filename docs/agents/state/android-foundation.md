# Android design foundation — verified state

**Scope:** tokens (`res/values/**`, `values-night/**`, `res/color/**`, `res/anim/**`,
`res/interpolator/**`, `res/font/**`) and the component layer
(`java/com/v2ray/ang/ui/component/**`), checked line by line against
`docs/design2026/10-design-system.md` and `docs/design2026/22-components.md`.

**Method:** every claim below was read in source. Where a document claims a thing exists, I
found the declaration; where a document claims a thing is *used*, I found the call site, or
recorded that there is none.

**Repo:** `/home/user/dp`, branch `claude/app-audit-agents-hyyftk`, HEAD `c9d7bf0`.

---

## The headline

**The token layer is real and largely correct. The component layer is real, well written,
and completely unreachable — it has zero call sites.**

An exhaustive grep across every source set (`main`, `dev`, `fdroid`, `pre_release`, `test`)
for the component package and every public symbol it exports:

```
grep -rn "ui\.component|RowBinder|ChipBinder|EmptyStateBinder|SkeletonBinder|ToolbarBinder|
          SelectionBinder|SubPage|onSingleClick|acceptClick|clearClick|RowSlots|ToolbarSlots|
          EmptyStateSlots" --include=*.kt --include=*.java V2rayNG/app/src/
  | grep -v "/ui/component/"
→ 0 results
```

48 activity/fragment files live in `java/com/v2ray/ang/ui/`. None of them import
`com.v2ray.ang.ui.component`. None of them inflate `view_row.xml`, `view_chip.xml`,
`view_toolbar.xml`, `view_empty_state.xml` or `view_skeleton_row.xml`. The 11 component
layouts are `<include>`d by zero screens (the only `<include>` anywhere is
`view_empty_state.xml` pulling in its own `view_action_secondary.xml` ViewStub).

So the foundation splits cleanly in two:

| Layer | State |
|---|---|
| Colour / dimension / type / motion / shape tokens | **done and reaching screens** (via theme defaults + `TextAppearance.App.*`) |
| `res/color/**` state selectors, `res/anim`, `res/interpolator`, `res/font` | **done**, wired into styles |
| `res/values/styles.xml` component styles (95 styles) | **built**; reach screens only implicitly, through `themes.xml` component defaults |
| `res/layout/view_*.xml` component layouts (11 files) | **built, unreachable** — 0 includes |
| `ui/component/**` binders (9 files, ~1 900 lines) | **built, unreachable** — 0 call sites |

---

## 1. Tokens — what is actually there

### 1.1 Colour primitives (spec 1.1) — DONE

`res/values/colors.xml:31-90` carries every primitive the spec names, at the specified hex:

- Ink ramp, 13 entries `ink_00 … ink_96` — all 13 present, all match.
- Paper ramp, 13 entries `paper_100 … paper_10` — all present, all match.
- Blue, 11 entries `blue_03 … blue_90` — all present, all match, including `blue_60 #4C8DFF`.
- Status hues: `green_20/32/45/60/88`, `red_15/38/45/50/55/65`, `amber_28/40/58` — present, match.
- Mono, both variants (`values/colors.xml:296-339`, `values-night/colors.xml:182-229`),
  including `mono_outlineControl` which spec 1.1.5 flags as new.

One deliberate deletion not carried out: `orange_62 #FB923C` is gone from the primitive block
(good), but `@color/icon_orange #FB923C` survives at `colors.xml:121`.

### 1.2 The "deleted tokens" list (spec 2.2) — NOT DONE

Spec 2.2 lists 15 Android colour tokens as deleted. All 15 are still declared:
`icon_purple`, `icon_tile_purple`, `icon_orange`, `icon_tile_orange`, `icon_green`,
`icon_tile_green`, `icon_yellow`, `icon_tile_yellow`, `chip_json_bg`, `chip_json_text`,
`brand_cream`, `divider_color_light`, `colorWhite`, `color_upload`, `color_download`
(`colors.xml:100-141`).

This is *documented* as intentional: the file comment says they "stay only until their last
reference migrates", and `themes.xml:160-180` neutralises the tint attrs by aliasing
`iconTintGreen/Orange/Purple/Yellow` → `md_theme_primary` and every `iconTileBg*` → the blue
tile. So the *palette* is one accent even though the *names* survive. Call it **partial**:
the behaviour is fixed, the retirement is not.

### 1.3 Semantic colour (spec 2.2) — MOSTLY DONE, with four light-theme deltas

Dark column (`values-night/colors.xml`) matches the spec exactly on every row I checked:
background `#0A0B0D`, surface `#141619`, surfaceContainerHigh `#1A1D21`,
surfaceContainerHighest `#20242B`, onSurface `#F2F4F8`, onSurfaceVariant `#9BA1AD`,
primary `#4C8DFF`, onPrimary `#00183A`, error `#F04452`, outline `#2A2E36`,
outlineVariant `#20242B`.

Light column does **not** match the spec on four rows — these are legacy M3 values the token
wave did not overwrite:

| Token | Spec 2.2 light | `values/colors.xml` | Line |
|---|---|---|---|
| `color_destructive` (`md_theme_error`) | `#C42B32` | `#BA1A1A` | 170 |
| `color_destructive_container` (`md_theme_errorContainer`) | `#F4D9DA` | `#FFDAD6` | 171 |
| `color_success` (`md_theme_tertiary`) | `#0B7D4A` | `#12B76A` | 164 |
| `color_success_container` (`md_theme_tertiaryContainer`) | `#D3E8DE` | `#A8F0CE` | 166 |
| `color_on_accent_container` (`md_theme_onPrimaryContainer`) | `#14468F` | `#001A43` | 155 |

`#12B76A` on white measures ~2.6:1 — it fails as light-theme success **text**, which is why
`pingGood` is bound to `color_success_text` instead (`themes.xml:188`). The fill token itself
is still off-spec.

`accent_hover` / `accent_pressed` also deviate: spec 2.2 gives `#3D7EF0` / `#3877E0` for both
themes; `colors.xml:273-274` uses `#1A54B4` / `#17499E` for light, with a written reason
("one step DARKER than the accent"). Dark matches (`values-night/colors.xml:163-164`).

### 1.4 The six new theme attributes — DECLARED AND BOUND, BUT NO READERS

This is the project's chronic defect, and it recurs here. `res/values/attrs.xml:73-102`
declares six attributes; `themes.xml:106-114` binds all six in `AppThemeBase`;
`themes.xml:432-437` re-binds all six under `ThemeOverlay.Mono`. Write side: complete.

Read side, greping `?attr/<name>` across `res/layout`, `res/drawable`, `res/color`,
`res/values/styles.xml` and `java/`, excluding the declaration and binding files themselves:

| Attribute | Real readers | Note |
|---|---|---|
| `?attr/colorOutlineControl` | **0** | consumers name `@color/md_theme_outlineControl` directly instead — 16 references in `bg_field.xml`, `field_stroke_selector.xml`, `btn_outlined_stroke.xml`, `switch_track_decoration.xml` |
| `?attr/colorOnSurfaceDim` | **0** | |
| `?attr/warning` | **0** | one hit in `view_status_strip.xml:31`, inside the XML comment block, not on an element |
| `?attr/warningText` | **0** | `ChipBinder.kt:135` reads `R.color.color_warning_text` directly |
| `?attr/warningContainer` | **0** | `ChipBinder.kt:125` reads `R.color.warning_container` directly |
| `?attr/colorSkeleton` | **0** | the three skeleton drawables use `?attr/colorSurfaceContainerHighest` instead |

The consequence is specific and matters: `attrs.xml`'s own header states the rule — *"must
also change under mono → `?attr/…`"* — because `ThemeOverlay.Mono` is applied at runtime
(`BaseActivity.kt:51`) and a runtime overlay can only redirect attributes, never `@color/`
references. Every consumer of the control boundary, the warning hue, the chip fills and the
skeleton fill reaches for `@color/` directly, so **the mono values for all six
(`mono_outlineControl`, `mono_onSurfaceDim`, `mono_warning`, `mono_warningText`,
`mono_warningContainer`, `mono_skeleton`) are dead weight** — declared, bound, never resolved.

Also dead: `@color/state_selected` (0 readers; consumers use its target `accent_fill_12`
directly, 5 readers), `@color/state_press` (0), `@color/accent_hover` (0),
`@color/accent_pressed` (0), `@color/skeleton` (1 reader — the `themes.xml` binding, nothing
downstream).

### 1.5 Dimensions (spec 1.3 / 2.5-2.8) — DONE

`res/values/dimens.xml` (170 lines) is complete against spec 2.5 (spacing), 2.6 (radius),
2.7 (sizes) and 2.8 (strokes). Every named token exists: `space_4/8/12/16/24/32`,
`screen_gutter 16`, `content_max_width 720`, `divider_inset_start 68`, `radius_chip/tile 12`,
`radius_button 16` with `radius_control` aliased to it, `radius_card 20`, `radius_sheet 24`,
`radius_pill 100`, `stroke_hairline/control 1`, `stroke_focus/emphasis 2`, `stroke_ring 3`,
`focus_offset 2`, and the full size list (`glyph_16/20`, `tile_glyph 22`, `flag_size 28`,
`avatar_chip 36`, `tile_size 40`, `icon_button 40`, `view_height_dp48`, `field_min_height 56`,
`row_min_height 56`, `toolbar_height 56`, `nav_bar_height 64`, `empty_icon 64`,
`shield_glyph 80`, `sub_card_height 152`, `connect_disc 176`, `meter_height 6`,
`dot_size 6 / _active 8 / dot_gap 8`, `sheet_handle_w 36 / _h 4`).

Two ratified deviations from spec 2.7, both stated in the file with their reason:

- `field_height` = **56**, not the spec's 52 (`dimens.xml:142-146`; `22-components.md` R10
  ratifies 56 to avoid fighting `OutlinedBox`'s 56 minimum).
- `btn_height` = **48**, `btn_height_tall` = 52 with `cta_height` aliased to it
  (`dimens.xml:130-135`); the spec's layer-3 table gives `btn_height` = 52. `22-components.md`
  R2 is the more specific ruling and the file follows it.

Both spellings resolve, so neither document can break a build. That is the right call.

Gaps:

- **`values-sw600dp/` does not exist.** Spec 2.5 requires `screen_gutter` → 24dp there
  (`space_gutter_wide`). Only `values-sw360dp-v13/` exists. Tablets get the phone gutter.
- **`content_max_width` has zero readers.** Declared at `dimens.xml:50`, referenced by no
  layout and no Kotlin. The 720dp content cap is not implemented.

### 1.6 Motion (spec 1.5 / 2.11) — DONE, one token unused

`res/values/motion.xml` declares all 13 tokens. Reader counts (excluding the declaration):

```
motion_press_in   9    motion_reveal       14    motion_pulse    9
motion_press_out  9    motion_reveal_exit   5    motion_spin     1
motion_state     19    motion_slow          1    input_debounce  3
motion_state_exit 2    motion_stagger       2    press_scale    22
motion_hover      0  ← parity-only token, documented as such
motion_emphasis   3
```

All four interpolators exist in `res/interpolator/`. `ease_out_expo.xml` — which spec 1.5
marks "**missing, must be added**" — now exists, though it has **0 readers** (it is reserved
for the auth→home hand-off, which is not built).

`res/anim/press_scale.xml` is the single press recipe and it is correct: 0.97 in over
`@integer/motion_press_in` on `ease_out_quart`, out over `motion_press_out` on
`ease_out_quint`. `nav_press.xml` was reconciled to the same durations and curves — spec 2.4
called out the old 0.96 / 0.92 / hard-coded-100ms drift and it is gone. `press_scale` is
applied at 22 sites (4 styles + 18 layouts).

`subpage_enter.xml` (300ms, `ease_out_quint`, 16dp + fade) and `subpage_exit.xml` (225ms,
`ease_standard`) exist and are correct, but they are only referenced from `SubPage.kt`, which
nothing calls — so **no screen has the sub-page transition**.

### 1.7 Type (spec 2.9) — DONE, and the D-1/D-2 font problem is genuinely solved

`res/values/styles.xml:65-290` implements all 11 ramp roles at the specified size, weight,
line height, tracking and colour. Verified individually: Display 34/700/-0.02/lh40,
Headline 24/700/-0.01/lh28, Title 16/700/0/lh20, Title.Medium 16/500, Body 14/400/+0.01/lh20,
Subtitle 13/400/+0.01/lh18, Caption 12/400/+0.02/lh16, Chip 11/500/+0.04/lh14,
Numeric (weight **500 declared**, closing spec 2.9.2's explicit gap), SectionHeader
(sentence-case, `textAllCaps` false), Wordmark 20/700/-0.01/lh24.

The two-face split is real, not aspirational:

- `res/font/golos_text.xml` — **three genuine static masters vendored**
  (`golos_text_regular/medium/bold.ttf`, 64 KB each), both `android:*` and `app:*` namespaces
  on every entry so weight matching works on API 24-25. Cyrillic-bearing roles point at the
  concrete weight file, not the family, so no synthetic bold can occur.
- `res/font/space_grotesk.xml` — the variable-font gate of spec 2.9.2 was **resolved by
  inspecting the binary** rather than screenshotting it, and all three entries now carry
  `android:fontVariationSettings` + `app:fontVariationSettings` pinning `wght` 400/500/700.
  The file states honestly that API 24-25 has no variation path and falls back to 300; that is
  logged as vendoring debt, not hidden.
- Spec 2.9.1's interim `res/font/ui_sans.xml` was **not** created — correctly, because D-1
  landed on Golos and `@font/golos_text` supersedes it.

Usage in layouts: `TextAppearance.App.*` is applied 145 times across `res/layout/`
(Body 45, Subtitle 43, Title 29, Caption 15, Numeric 5, Headline 3, Chip 3,
Title.Destructive 1, SectionHeader 1, Display 1). **This is the one part of the foundation
that has genuinely reached the screens.**

---

## 2. Components — every variant, every state

### 2.1 Styles (`res/values/styles.xml`, 95 styles) — BUILT

All 15 members of the `22-components.md` R15 vocabulary have a style:

| Component | Style(s) | Variants present |
|---|---|---|
| Button | `Widget.Departament.Button.*` | Primary, Primary.Tall, Secondary, Secondary.Tall, Tertiary, Tertiary.Destructive, Destructive, Destructive.Tall — the 5 variants + both modifiers |
| Icon button | `…Button.Icon{,.Filled,.Accent,.Danger,.Toolbar}` | 5 |
| Text field | `…TextField{,.Search,.EditText,.ReadOnly}` + `ThemeOverlay.Departament.TextField` | 4 |
| Segmented control | `…SegmentGroup`, `…Segment`, `TextAppearance.App.Title.Segment.Active` | 3 |
| Switch | `…Switch` | 1 |
| Row | `…Row{,.Navigation,.Value,.Action,.Toggle,.Destructive}` + `…Tile{,.Accent,.Destructive}` | all 5 archetypes + 3 tiles |
| Card | `…Card{,.Group,.Pressable,.Selectable}` | 4 |
| Chip | `…Chip{,.Technical,.Accent,.Status.Ok,.Status.Warn,.Status.Error}` | 6 |
| Tab bar | `…NavigationBar`, `…NavigationRail`, `BottomNavIndicator`, `BottomNavLabel{,.Active}` | 5 |
| Toolbar | `…Toolbar{,.Brand}` | 2 |
| Sheet / dialog | `ThemeOverlay.Departament.BottomSheet`, `…Sheet`, `ThemeOverlay.Departament.Dialog`, `Departament.Dialog.{Title,Body,Button,Button.Destructive}` | 7 |
| Snackbar | `…Snackbar{,.TextView,.Button}` | 3 |
| Empty state | `…EmptyState.{Tile,Title,Line}` | 3 |
| Skeleton | `…Skeleton.{Bar,Block}` | 2 |
| Progress | `…Progress.Linear`, `…Progress.Circular.Inline{,.OnAccent}` | 3 |
| Selection indicator | `card_bg_selectable`, `row_stroke_selectable` (colour selectors) | 2 |

**Missing from the vocabulary: `Select`.** R15 lists it as one of the 15; there is no
`Widget.Departament.Select` style, no select layout, no binder. It is the one component that
exists in the spec only.

A 43-style `Widget.App.*` / `ShapeAppearance.App.*` alias block (`styles.xml:1253-1295`) maps
the `10-design-system.md` spellings onto the `22-components.md` ones so neither document can
break the build. Those aliases have **0 usages in layouts**.

### 2.2 States — mostly complete; loading is the hole

`res/color/` holds 34 selectors. Tabulated by state:

- **Disabled (0.38)** — 19 selectors carry `state_enabled="false"` with `android:alpha="0.38"`.
  The approach is right and R6-compliant: the 0.38 lives in every tinted part's
  `ColorStateList` (`backgroundTint`, `textColor`, `iconTint`, `strokeColor`) rather than as an
  imperative `View.alpha`, because `MaterialButton` cannot take a state-dependent alpha.
- **Pressed** — three ripple selectors (`ripple_accent`, `ripple_neutral`, `ripple_on_accent`)
  plus `press_scale`/`nav_press` `stateListAnimator`s plus `bg_row.xml`'s
  `state_pressed → colorSurfaceContainerHigh` background step (R5: rows step, objects scale).
- **Focused** — five selectors carry `state_focused="true"`
  (`btn_focus_outer_accent`, `btn_focus_inner_on_accent`, `_on_error`, `_on_neutral`,
  `btn_outlined_stroke`, `field_stroke_selector`) plus `bg_row.xml`'s inset 2dp accent ring.
  The trick R7 requires — permanent `strokeWidth 2dp` with a transparent-until-focused
  `strokeColor`, since `MaterialButton` has no state-dependent stroke width — is implemented
  in every button style (`styles.xml:370, 433, 486`).
- **Selected** — `card_bg_selectable` + `row_stroke_selectable` + `bg_row.xml`'s
  `state_activated` 12% fill, all with **no geometry change** (spec 18.1's requirement that
  selecting an item must not reflow the list). `SelectionBinder` and `RowBinder` add the
  weight axis (700/500) and the always-reserved marker whose *alpha* moves, plus the
  TalkBack `isSelected` announcement.
- **Loading — NOT BUILT.** `Widget.Departament.Progress.Circular.Inline.OnAccent` exists as a
  style, and `motion_spin 1100` exists as a token, but **no code puts a control into the
  loading state**. There is no `ButtonBinder`, no `setLoading()`, nothing in the component
  package matches `loading` outside three doc comments. R8's contract (hold the width, hide
  the label, spin a 20dp arc, disable, appear after 300ms) is unimplemented. Spec 20.1's
  required `<string name="state_loading">загрузка</string>` **does not exist** in any strings
  file.

Four selectors are entirely unused: `segment_bg_selector`, `segment_text_selector`,
`state_press_on_accent`, `color_highlight_material`. The first two are documented aliases of
`segment_container`/`segment_content`.

### 2.3 Component layouts — BUILT, UNREACHABLE

`res/layout/view_*.xml`, 11 files. Include counts:

| Layout | Slots declared | `<include>`d by |
|---|---|---|
| `view_row.xml` | 11 (`row`, `row_tile`, `row_text`, `row_title`, `row_subtitle`, `row_value`, `row_marker`, `row_chevron`, `row_trailing_glyph`, `row_switch`, `row_icon_action`, `row_action`) | **0** |
| `view_toolbar.xml` | 6 (`toolbar`, `toolbar_bar`, `toolbar_back`, `toolbar_leading_gap`, `toolbar_title`, `toolbar_action`, `toolbar_hairline`) | **0** |
| `view_empty_state.xml` | 4 + ViewStub (`empty_state`, `empty_glyph`, `empty_title`, `empty_line`, `empty_action_stub`) | **0** |
| `view_chip.xml` | 1 (`chip`) | **0** |
| `view_skeleton_row.xml` | 4 (`skeleton_row`, `skeleton_tile`, `skeleton_title`, `skeleton_subtitle`) | **0** |
| `view_status_strip.xml` | 5 | **0** |
| `view_search_field.xml` | — | **0** |
| `view_meter.xml` | — | **0** |
| `view_action_primary.xml` | — | **0** |
| `view_action_secondary.xml` | — | 1 (`view_empty_state.xml`'s stub) |
| `view_action_tertiary.xml` | — | **0** |

Quality of what is there is high — `view_row.xml` is a genuine universal row: `minHeight 56`
with `wrap_content` height so a two-line subtitle grows it, text column at `layout_weight=1`,
switch explicitly `clickable="false" focusable="false"` so the whole row is one target, tile
marked `importantForAccessibility="no"`, and every optional slot ships `gone`.

Two declared dependencies are missing from `res/drawable/`: `ic_unfold_more` (the cycle
affordance `view_row.xml:166` names) and `ic_warning` / `ic_error`
(`view_status_strip.xml:34-35`). Both gaps are noted in the layouts' own comments.

### 2.4 Binders — BUILT, UNREACHABLE

9 files, `java/com/v2ray/ang/ui/component/`. Call-site count for each, excluding the package
itself:

| File | Public API | External call sites |
|---|---|---|
| `RowBinder.kt` (22 831 B) | `bind()`, `animateExpand()`, `TileRole`, `RowTone`, 7 `Trailing` variants, `RowSlots` | **0** |
| `ToolbarBinder.kt` (7 453 B) | `bind()`, `attachTo(RecyclerView)`, `attachTo(NestedScrollView)`, `ToolbarSlots` | **0** |
| `EmptyStateBinder.kt` (7 345 B) | `bind()`, `hide()`, `Emphasis`, `EmptyStateSlots` | **0** |
| `SkeletonBinder.kt` (6 139 B) | `pulse()`, `hold()`, `showAfterDelay()`, `cancel()`, `swap()` | **0** |
| `ChipBinder.kt` (6 224 B) | `bind()`, `Tone` (6 tones) | **0** |
| `SelectionBinder.kt` (5 055 B) | `apply()` | **0** |
| `SubPage.kt` (5 477 B) | `open()` ×2, `close()`, `installTransitions()`, `subPageAnimations()` | **0** |
| `SingleClick.kt` (4 989 B) | `onSingleClick()`, `acceptClick()`, `clearClick()`, `Haptic` | **0** |
| `ComponentSupport.kt` (4 273 B) | `themeColor()`, `curve()`, `durationOf()`, `motion()`, `slot()`, `RunningAnimators` | **0** (internal, used only inside the package) |

The 17 in-package `onSingleClick` occurrences are the binders calling each other. The only
`ui/component` reference outside a binder body is inside a KDoc `[link]`.

The code itself is not a stub. Spot-verified behaviours that are genuinely implemented:

- `RowBinder.bind` **rejects at runtime** a row that has both an owning trailing control and
  a row-level `onClick` (`RowBinder.kt:198`), and a toggle row that also carries a value
  (`:202`) — the "two targets" defect enforced by `require`, not by comment.
- `resetTrailing()` (`:308`) clears every affordance and detaches every listener before
  binding, which is what makes recycling safe.
- Disabled is applied to the row **and** to the three interactive children, because
  `isEnabled` does not cascade to a `ViewGroup` (`:391-398`) — a real Android trap, handled.
- `applySemantics()` (`:432`) installs a fresh `AccessibilityDelegateCompat` on every bind
  so a recycled row cannot keep the previous item's `isChecked`/`isSelected` announcement.
- `SkeletonBinder.showAfterDelay` honours the 300ms threshold so a fast response never
  flashes a placeholder; `pulse`/`swap`/`hold` all check `reducedMotion()`.
- `SubPage` gates its transitions on `animationsEnabled()` and uses the API 34
  `overrideActivityTransition` path with an `overridePendingTransition` fallback.
- `ChipBinder.bind` **rejects** a status chip with a blank label (`:66`) — colour alone is
  never a signal.

This is finished work that no user can reach.

### 2.5 How the styles *do* reach screens

Worth stating precisely, because it is the one bright spot and it is easy to miss: layouts
almost never name a `Widget.Departament.*` style — 22 usages total, and 21 of them are inside
the unreachable `view_*.xml` files (the 22nd is
`activity_account.xml` → `Widget.Departament.Progress.Circular.Inline`).

But `themes.xml:242-326` sets **15 component defaults** on `AppThemeBase`:
`materialButtonStyle`, `materialButtonOutlinedStyle`, `borderlessButtonStyle`,
`materialCardViewStyle`, `materialDividerStyle`, `materialSwitchStyle`, `textInputStyle`,
`chipStyle`, `toolbarStyle`, `bottomNavigationStyle`, `navigationRailStyle`,
`bottomSheetDialogTheme`, `snackbarStyle` + `snackbarTextViewStyle` + `snackbarButtonStyle`,
`linearProgressIndicatorStyle`, plus the shape lock on both M3 shape families
(`shapeAppearance{Small,Medium,Large}Component` **and** `shapeAppearanceCorner{Small,Medium,Large}`).

So every unstyled `MaterialButton`, `MaterialCardView`, `MaterialSwitch`, `MaterialToolbar`
and `Snackbar` in the shipped app **does** pick up the Departament geometry, insets, face,
press animation and state selectors without a layout edit. The theme comments even count what
each default touches (11 buttons, 50 cards, 23 switches, 21 snackbar call sites, 4 text
inputs). That is real reach, and it is why the app is not visually unchanged despite the
binder layer being dead.

`ThemeOverlay.Mono` is applied for real at `BaseActivity.kt:51`
(`theme.applyStyle(R.style.ThemeOverlay_Mono, true)`), and the overlay restates 40+ roles
including the destructive four that would otherwise leak the blue theme's red into a greyscale
screen. Its six 2026-07-wave attribute lines, however, are the dead ones from §1.4.

---

## 3. Residual token hygiene in the layout layer

The token *layer* is clean; the *layouts* are not migrated. Sampled across
`res/layout/` (84 files):

- Hard-coded hex: **1 file** (`activity_settings.xml`). Effectively solved.
- Off-scale `dp` in margins/padding, against spec 1.3's closed set
  {0,4,8,12,16,24,32,40,48,56,64}: `14dp` ×80, `10dp` ×54, `2dp` ×34, `72dp` ×19,
  `68dp` ×13, `6dp` ×10, `3dp` ×8, `27dp` ×2, `22dp` ×2. `14` and `10` alone are 134
  violations of the one number set the spec calls "the only numbers".

`@dimen/screen_gutter` appears in 17 of 84 layouts. The gutter is not yet the one inset.

---

## 4. Classification

**(a) Fully done and wired**
- Colour primitives (dark + light + both mono variants).
- Dark-theme semantic colour.
- Dimension tokens, radius lock, stroke tokens.
- The 11-role type ramp, and both font families with real weight masters — the D-1/D-2
  Cyrillic problem is genuinely closed, not papered over.
- Motion tokens + 4 interpolators; the single 0.97 press recipe, applied at 22 sites.
- 95 component styles + 34 state selectors covering default/pressed/focused/disabled/selected.
- Theme component defaults — the mechanism by which the styles actually reach 48 screens.
- `ThemeOverlay.Mono`, applied at runtime.

**(b) Built but not reachable**
- All 9 binders in `ui/component/**` — **0 call sites**.
- All 11 `view_*.xml` component layouts — **0 includes**.
- `res/anim/subpage_enter.xml` / `subpage_exit.xml` — only `SubPage.kt` names them.
- `res/interpolator/ease_out_expo.xml` — 0 readers.
- The six 2026-07 theme attributes and their four mono colour sets — bound, never read.
- `@color/state_selected`, `state_press`, `accent_hover`, `accent_pressed`, `skeleton` — 0 readers.
- 43 `Widget.App.*` / `ShapeAppearance.App.*` aliases — 0 layout usages.
- `@dimen/content_max_width` — 0 readers.

**(c) Partially built**
- Light-theme semantic colour: 5 rows still on legacy M3 values (§1.3).
- Token retirement: 15 "deleted" colours still declared, though neutralised at the attr level.
- `res/color` selectors: 4 unused.
- Layout-level token hygiene: hex almost eliminated, off-scale dp widespread.

**(d) In the spec only**
- `Select` — one of R15's 15 components, with no style, no layout, no binder.
- **Loading state** for buttons (R8) — no code, and the required `state_loading` string was
  never added.
- `values-sw600dp/` wide gutter (spec 2.5 `space_gutter_wide`).
- The 720dp content column cap.
- `ic_unfold_more`, `ic_warning`, `ic_error` drawables that shipped layouts already reference.

**(e) Not started**
- Any screen migration onto the component layer. This is the whole gap: the foundation was
  poured, and nothing was built on it.

---

## 5. What the owner should take from this

The design-system work is not vapour — the token files are unusually careful (every colour
carries a computed contrast ratio and a stated purpose; every documented deviation names the
ruling it follows). The type ramp and the font vendoring in particular fixed a real,
previously-invisible defect where every Russian string silently fell back to Roboto.

But the component layer — nine binders, eleven layouts, roughly 1 900 lines of Kotlin plus
the XML — is **finished, tested-by-`require`, documented, and connected to nothing**. Not one
of the 48 screens calls it. A screen agent was supposed to follow, and none did.

The single highest-value next step is not more foundation. It is picking one screen and
wiring it through `RowBinder` / `ToolbarBinder` / `EmptyStateBinder` end to end — which will
also flush out the three missing drawables, the absent `Select`, and the unbuilt loading
state, none of which will surface until something tries to use them.
