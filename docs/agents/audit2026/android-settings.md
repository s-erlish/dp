# Audit 2026 - Android settings and the peripheral screens

**Wave**: android-settings. **Branch**: `claude/app-audit-agents-hyyftk`. **Date**: 2026-07-26.
**Build root**: `/home/user/dp/V2rayNG`, package `com.v2ray.ang`, Kotlin + Material 3 + XML views.

**Law**: `docs/design2026/00-rules.md`, section 18 ratified decisions D-1 to D-12 binding.
**Specs audited against**: `12-settings.md` (row by row), `14-auth.md` (the sign-in state machine),
`11-app-structure.md` (merge / delete), `20-control-survey.md`, `22-components.md` sections 4, 5, 6,
7, 8 and R15.

**Ownership note.** Several waves are editing this tree right now. The token layer
(`res/values/dimens.xml`, `colors.xml`, `motion.xml`, `ids.xml`) and the component-style layer
(`res/values/styles.xml`, 100+ `Widget.Departament.*` / `TextAppearance.App.*` styles) landed today
and are **theirs**. Every fix below is expressed as *apply an existing style*, never *invent a
style*. Where a file is being rewired by another wave (`MainActivity.kt` settings-tab handlers,
`AuthViewModel`, the provider plumbing), **the plumbing is theirs and the visual system is this
document's**: this audit specifies geometry, ramp role, tile colour, trailing element, state and
copy, and does not touch the MMKV keys, the intents or the coroutine flow behind them.

**Verdict: 6 / 20. Does not ship.** Ship bar is >= 18/20 with nothing below 3.

---

## 1. Score - the five dimensions of `audit.native.md` (00-rules.md 17.1)

| # | Dimension | Score | The single sentence that fixes the number |
|---|---|---|---|
| 1 | Accessibility | **1** / 4 | Zero focus rings in 21 layouts (`focus_ring.xml` exists, referenced 0 times here) against 55 `focusable="true"` declarations; 9 settings rows carry two trailing elements; 3 fixed-height buttons clip at font scale 200%; `colorError` `#F04452` (4.88:1) draws error text where `@color/ping_bad` (6.15:1) is required; a 3-target row 4dp apart in `activity_bypass_list.xml:64-108`. |
| 2 | Performance | **2** / 4 | The 1 536-line `layout_settings_content.xml` inflates 23 rows + 6 cards + 6 headers in one pass inside `activity_main.xml`, on the main thread, at cold start, for a tab most launches never open; `activity_url_scheme_list.xml` inflates 9 identical hand-copied blocks (634 lines) where a `RecyclerView` belongs; two `RecyclerView`s are pinned `match_parent` + `nestedScrollingEnabled="false"` inside a `NestedScrollView` so they never recycle; `activity_bypass_list.xml:124` uses `GridLayoutManager` for a vertical app list. |
| 3 | Appearance and theming | **1** / 4 | Three parallel row grammars, 6 tile hues where D-5 allows 3, 259 off-scale dp, 93 inline `textSize`/`fontFamily`, `15sp` used 18 times when the ramp says 15sp does not exist, and a settings page painted `colorSurface` carrying `colorSurface` cards so the card boundary is invisible. |
| 4 | Platform conformance | **2** / 4 | 8 `AlertDialog`s where 00-rules 7.6 orders sheets, 57 `Toast` call sites where 1.4.8 orders snackbars, 8 dialogs whose primary button says "OK", `SwitchCompat` beside `MaterialSwitch`, and a `?attr/actionBarSize` toolbar that draws every Russian sub-page title in a face with zero Cyrillic. |
| 5 | Adaptivity | **0** / 4 | No `values-sw600dp` override for these screens, no `content_max_width` cap anywhere in the tree, no landscape consideration, `?attr/actionBarSize` silently drops the sub-page toolbar to 48dp in landscape, three fixed 52dp button heights, five fixed 44dp segment buttons, and 18 `15sp` + 21 `12sp` labels that collide with their trailing value at font scale 200%. |

Nothing here is a polish pass. Dimensions 3 and 5 are structural.

---

## 2. Mechanical checks - 00-rules.md 1.5 and 9.7, scoped to this wave's files

Files in scope for every number below (all under `V2rayNG/app/src/main/res/`):
`layout/layout_settings_content.xml`, `layout_setting_row.xml`, `layout_setting_toggle_row.xml`,
`activity_settings.xml`, `preference_with_help_link.xml`, `widget_switch.xml`,
`activity_local_proxy.xml`, `activity_provider_settings.xml`, `activity_about.xml`,
`activity_backup.xml`, `activity_check_update.xml`, `activity_logcat.xml`,
`activity_bypass_list.xml`, `item_recycler_bypass_list.xml`, `activity_app_picker.xml`,
`activity_routing_setting.xml`, `activity_routing_edit.xml`, `activity_url_scheme_list.xml`,
`activity_user_asset.xml`, `activity_login.xml`, plus `activity_base.xml` (the shared toolbar every
one of these screens inherits) and `xml/pref_settings.xml`.

### 2.1 Results

| Check (00-rules.md 1.5) | Scoped result | Verdict |
|---|---|---|
| Raw colour literals in layouts | **0** | clean, keep it |
| `textAllCaps="true"` | **0** | clean |
| Inline `android:fontFamily` / `android:textSize` (D-2 enforcement) | **93** across 8 files | fail |
| Off-scale `dp` | **259** across 11 files | fail |
| Total raw `dp` literals of any value | **738** across 15 files | fail |
| Em / en dash in the layouts | **0** | clean |
| `...` (three dots) in the layouts | **0** | clean |
| Emoji in scoped strings | **0** | clean |
| `MaterialCardView` opening tags | **27** across 10 files | fail (a settings screen is rows, 12-settings 2.1) |
| Nested cards | **0** | clean |

### 2.2 Inline type, by file (93 hits)

| File | Hits |
|---|---|
| `activity_local_proxy.xml` | 37 |
| `activity_url_scheme_list.xml` | 29 (20 `textSize` + 9 `fontFamily="monospace"`) |
| `activity_provider_settings.xml` | 15 |
| `activity_backup.xml` | 4 |
| `activity_bypass_list.xml` | 2 |
| `item_recycler_bypass_list.xml` | 2 |
| `activity_routing_setting.xml` | 2 |
| `activity_user_asset.xml` | 2 |

Distinct sizes: `16sp` ×25, `12sp` ×21, `13sp` ×20, `15sp` ×18.
**`15sp` does not exist in the ramp** (00-rules.md 3.4, closing line). All 18 are defects by
definition, not approximations. `fontFamily="monospace"` ×9 introduces a **third font family** into
a two-face product (`activity_url_scheme_list.xml:121,173,244,296,367,438,490,561,613`) - product
ban 1.3 ("display fonts in UI labels, buttons, data") and 00-rules 3.4, which has exactly two faces.

### 2.3 Off-scale dp, by file and by value (259 hits)

| File | Hits | | Value | Count |
|---|---|---|---|---|
| `activity_local_proxy.xml` | 75 | | `14dp` | 72 |
| `activity_provider_settings.xml` | 62 | | `18dp` | 57 |
| `activity_url_scheme_list.xml` | 37 | | `10dp` | 53 |
| `layout_settings_content.xml` | 36 | | `60dp` | 32 |
| `activity_backup.xml` | 26 | | `42dp` | 18 |
| `activity_user_asset.xml` | 6 | | `68dp` (raw, not the token) | 13 |
| `activity_routing_setting.xml` | 6 | | `6dp` | 7 |
| `activity_login.xml` | 5 | | `26dp` | 5 |
| `widget_switch.xml` | 2 | | `45dp` | 2 |
| `layout_setting_row.xml` | 2 | | | |
| `item_recycler_bypass_list.xml` | 2 | | | |

Two of these deserve naming:

- **`18dp` ×57 is the chevron.** Every navigation row in the tree draws its chevron at 18x18. The
  icon-size token set is 16 / 20 / 22 / 24 (00-rules.md 10.3); 18 is in none of them, and 20 is the
  size the spec names for an inline chevron (`12-settings.md` 3, A1). There is no
  `ic_chevron_right_20dp` drawable in `res/drawable/` - only `ic_chevron_right.xml` - so the fix is
  to size the existing vector at `@dimen/glyph_20`, not to add a drawable.
- **`68dp` ×13 raw** is the correct *number* written the wrong way: `@dimen/divider_inset_start` is
  68dp and exists. Meanwhile `layout_settings_content.xml` writes **`72dp` ×17** for the same
  divider - which the 1.5 grep does **not** flag, because 72 is in the allow-list, and which is
  therefore the more dangerous of the two: the settings tab's dividers are inset 4dp further than
  every other list in the app and nothing mechanical catches it.

### 2.4 The 9.7 copy grep, scoped to the string files this wave owns

| File | `—` / `–` | `...` |
|---|---|---|
| `values/strings_local_proxy.xml` | **3** (`:13`, `:41`, `:44`) | 0 |
| `values/strings_perapp.xml` | **1** line, 2 occurrences (`:7`) | 0 |
| `values/strings_deeplink.xml` | **1** line, 2 occurrences (`:6`) | 0 |
| `values/strings_auth.xml` | **1** (`:4`, inside an XML comment) | 0 |
| `values/strings_settings_hub.xml` | 0 | 0 |
| `values/strings_provider.xml` | 0 | 0 |

**5 hits in shipped copy, 1 in a comment. Zero `...` hits.** Each rewrite is given in section 9.1.

---

## 3. Ban hits - 00-rules.md 1.1, 1.3, 1.4

Every row is at least P1 by 17.2. Evidence is file:line.

| # | Ban | Where | Count |
|---|---|---|---|
| B1 | 1.4.5 no off-scale spacing | 11 layouts | 259 |
| B2 | 1.4.1 / D-5 the coloured tile system is exactly three | `layout_settings_content.xml` uses **6 hues on one screen**: blue ×7, green ×6, purple ×5, orange ×3, red ×1, yellow ×1 | 23 tiles, 6 hues |
| B3 | 1.4.1 / D-5, continued | `activity_provider_settings.xml` 9 tiles / 5 hues; `activity_local_proxy.xml` 8 / 5; `activity_backup.xml` 4 / 4; `activity_user_asset.xml:56` orange; `activity_routing_setting.xml:57` purple | 46 tiles, 6 hues, tree-wide |
| B4 | 4.4 + 12-settings 2.1 "a settings screen is rows, not cards" | 27 `MaterialCardView` opening tags across 10 of the 21 files: settings tab 6, url schemes 6, provider settings 4, local proxy 3, backup 2, login 2, bypass list 1, bypass item 1, routing 1, user assets 1 | 27 |
| B5 | 1.1 identical card grids | `activity_url_scheme_list.xml` - 9 visually identical hand-copied blocks, 634 lines, 6 card wrappers; `item_recycler_bypass_list.xml` - one card per app row | 9 + n |
| B6 | 4.5 "trailing is exactly one of… never two" | `layout_settings_content.xml` rows `row_mode`, `row_per_app`, `row_dns`, `row_ping_method`, `row_mux_concurrency`, `row_appearance`, `row_language`, `row_sub_auto_update`, `row_about` all carry **value text + chevron**; `activity_bypass_list.xml:82-107` carries **info glyph + switch** | 10 rows |
| B7 | 3.4 "never `android:textStyle="bold"`" (synthetic bold) | `activity_login.xml:62,200,271` | 3 |
| B8 | 3.2 / D-6 buttons are radius 16, pill only where w == h | `activity_login.xml:64,105,202,273,296` `cornerRadius="26dp"` | 5 |
| B9 | 3.3 / R2 heights are `minHeight`, never fixed | `activity_login.xml:57,196,267` `layout_height="52dp"`; `activity_local_proxy.xml:104,117,130,143,156` `layout_height="44dp"` | 8 |
| B10 | 4.3 one primary action per screen | `activity_login.xml` draws **three** filled `?attr/colorPrimary` buttons (`btn_telegram:63`, `btn_site:201`, `btn_confirm_2fa:272`) | 3 |
| B11 | 1.4.8 no `Toast` for anything actionable | **57** `toast*(` call sites across 10 activities in scope: `BackupActivity` 18, `RoutingSettingActivity` 8, `UserAssetActivity` 7, `LocalProxyActivity` 6, `LoginActivity` 5, `PerAppProxyActivity` 4, `CheckUpdateActivity` 3, `LogcatActivity` 3, `RoutingEditActivity` 2, `UrlSchemeListActivity` 1 | 57 |
| B12 | 1.4.9 / 7.6 no dialog for a decision that can be inline | 6 `setSingleChoiceItems` dialogs at `MainActivity.kt:3003,3051,3111,3218,3253,3309` + 2 free-value dialogs (`editDnsCustom:3130`, `editMuxConcurrency:3160`) | 8 |
| B13 | 9.2 "Never OK" | `AboutActivity.kt:33` literal `"OK"`; `android.R.string.ok` at `ProviderSettingsActivity.kt:199`, `RoutingEditActivity.kt:149`, `RoutingSettingActivity.kt:102,124,172`, `UserAssetActivity.kt:222`, `LoginActivity.kt:339` | 8 |
| B14 | 1.4.10 no Latin UI text | `preference_with_help_link.xml` and the whole of `pref_settings.xml` render upstream English titles | 55 prefs |
| B15 | 1.3 product ban, a third font family | `fontFamily="monospace"` ×9, `activity_url_scheme_list.xml` | 9 |
| B16 | 1.4.11 no em/en dash | 5 shipped strings (2.4) | 5 |
| B17 | 1.4.13 no screen without its states | 19 of the 21 screens ship the happy path only; see section 8 | 19 |
| B18 | 7.1 / R7 focus is drawn on **every** focusable control | `res/drawable/focus_ring.xml` exists; **0** references anywhere in these 21 layouts, against 55 `focusable="true"` | 55 |

**Not a hit** (checked, clean): side-stripe borders 0, gradient text 0, glassmorphism 0 -
`@drawable/bg_settings_glass` is misnamed but is a plain `<solid android:color="?attr/colorSurface"/>`,
no blur - hero-metric template 0, numbered section markers 0, emoji 0, nested cards 0,
`textAllCaps` 0, raw hex 0.

---

## 4. The structural findings

### 4.1 There are two settings screens, and one of them is unreachable

| | The settings tab | `SettingsActivity` |
|---|---|---|
| Entry | `activity_main.xml:492-497` includes `layout_settings_content.xml` as `group_settings` | `AndroidManifest.xml:88-90`, `exported="false"`, **no `startActivity` anywhere in `java/`** |
| Content | 1 536 lines, 23 hand-inlined rows, 6 card wrappers, 6 section headers | `activity_settings.xml` (16 lines) hosting `SettingsFragment` over `res/xml/pref_settings.xml` |
| Size | 23 rows | **55 preference keys in 6 `PreferenceCategory` groups** |
| Language | Russian | upstream English |
| Reachable | yes | **no** - `MainActivity.kt:2873` calls it "the legacy SettingsActivity" in a comment and reads the same MMKV keys directly |

`12-settings.md` 0.3 rules: `SettingsActivity.kt` + `pref_settings.xml` **deleted**, its live
preferences triaged into the new IA. This audit confirms the count is **55 keys / 6 categories**
(2 further keys sit commented out at `pref_settings.xml:88-91` and `:108-113`). Until the delete
lands, the app carries a second, English, unreachable settings surface that no reviewer sees and no
grep for "the settings screen" finds. `preference_with_help_link.xml` and `widget_switch.xml` are
satellites of the same dead branch:

- `preference_with_help_link.xml` (12 lines) - a `Widget.AppCompat.Button.Borderless` with
  `textStyle="italic"`, `textColor="?attr/colorPrimary"` and `android:onClick="onModeHelpClicked"`
  wired **by name to a method that does not exist** in `SettingsActivity.kt`. It is attached at
  `SettingsActivity.kt:101` as `mode?.dialogLayoutResource`. Italic is not in the ramp; a
  name-bound `onClick` cannot be verified by the compiler. Delete with the branch.
- `widget_switch.xml` (32 lines) - the home-screen app widget, not a settings row: a 45dp
  `ImageView` (off-scale) with `TextAppearance.AppCompat.Small` in `@android:color/white`
  (a raw platform colour, theme-blind). Out of `12-settings.md`'s scope, in this wave's file list,
  and one of the last three homes of the `AppCompat` type ramp.

### 4.2 The 23 hand-inlined rows - counted

`12-settings.md` 0.3 and 1 say "20 hand-copied rows". **The real count today is 23.** Verified by id
in `layout_settings_content.xml`:

| # | id | line | Archetype it should be (12-settings 4.2-4.6) | Trailing today |
|---|---|---|---|---|
| 1 | `row_mode` | 43 | A4 segment | value + chevron |
| 2 | `row_per_app` | 104 | A2 value | value + chevron |
| 3 | `row_bypass_lan` | 165 | A3 toggle | switch |
| 4 | `row_ipv6` | 230 | A3 toggle | switch |
| 5 | `row_dns` | 295 | A2 value | value + chevron |
| 6 | `row_ping_method` | 356 | A2 value (moves to `settings/latency`) | value + chevron |
| 7 | `row_local_proxy` | 417 | A1 navigation (moves under `settings/advanced`) | chevron |
| 8 | `row_always_on` | 483 | A5 action, external | chevron |
| 9 | `row_mux` | 568 | A3 toggle | switch |
| 10 | `row_mux_concurrency` | 634 | A2 value, conditional | value + chevron |
| 11 | `row_fragment` | 696 | A3 toggle | switch |
| 12 | `row_appearance` | 780 | A2 value | value + chevron |
| 13 | `row_language` | 841 | A2 value | value + chevron |
| 14 | `row_boot` | 902 | A3 toggle | switch |
| 15 | `row_sub_auto_update` | 986 | A2 value | value + chevron |
| 16 | `row_routing` | 1047 | A1 navigation | chevron |
| 17 | `row_assets` | 1099 | A1 navigation | chevron |
| 18 | `row_provider` | 1151 | A1 navigation | chevron |
| 19 | `row_tv_send` | 1236 | A1 navigation | chevron |
| 20 | `row_tv_receive` | 1290 | A1 navigation, conditional (`FEATURE_LEANBACK`) | chevron |
| 21 | `row_about` | 1362 | A1 navigation | value + chevron |
| 22 | `row_backup` | 1423 | A1 navigation | chevron |
| 23 | `row_url_scheme` | 1475 | A1 navigation | chevron |

Corroborating counts in the same file: `row_min_height` 23, `focusable="true"` 23,
`SettingsSectionLabel` 6, the `72dp` divider 17. The two shared row components that exist for
exactly this purpose - `layout_setting_row.xml` and `layout_setting_toggle_row.xml` - are included
**zero** times: the file contains no `<include>` at all. They are orphans, and `12-settings.md` 8.2
says so.

**Nine of the 23 carry two trailing elements** (B6). `00-rules.md` 4.5 permits exactly one, and
`12-settings.md` 2.3 explains why it matters here: the trailing element is the honest promise of
what the tap does. Value + chevron promises both "opens a picker here" and "leaves this screen",
which is why `row_dns` (opens a dialog, stays) and `row_about` (pushes `AboutActivity`) are
visually identical.

### 4.3 Three parallel row grammars in one product

| | Grammar A - settings tab | Grammar B - hub-layer sub-pages | Grammar C - upstream |
|---|---|---|---|
| Files | `layout_settings_content.xml` | `activity_user_asset.xml`, `activity_routing_setting.xml`, `activity_local_proxy.xml`, `activity_provider_settings.xml`, `activity_backup.xml` | `activity_about.xml`, `activity_check_update.xml` |
| Min height | `@dimen/row_min_height` 56 | `60dp` literal | none (padding 16 all round) |
| Horizontal padding | `@dimen/space_16` | `14dp` literal | `@dimen/padding_spacing_dp16` |
| Leading | 40 tile, coloured | 40 tile, coloured | bare 24dp icon, **no tile**, untinted |
| Tile-to-text gap | `@dimen/space_16` (spec: 12) | `14dp` literal | `paddingStart` 16 on the label |
| Title | `TextAppearance.App.Body` 14/400 | inline `textSize="16sp"` + `textColor` | `TextAppearance.AppCompat.Subhead` |
| Subtitle | `TextAppearance.App.Subtitle` | inline `textSize="13sp"`, `marginTop="2dp"` | none |
| Trailing | chevron `18dp`, often after a value | chevron `18dp` | **nothing** |
| Divider | `72dp` inset | none (each row is its own card) | none |
| Example | `:43-95` | `activity_user_asset.xml:40-99`, `activity_routing_setting.xml:41-100` | `activity_about.xml:18-41` |

`12-settings.md` 1 measured exactly this and called it "3 parallel row grammars". Confirmed, with
the addition that **grammar A's title is on the wrong ramp step**: `App.Body` is 14sp/400, and both
`00-rules.md` 4.5 and `12-settings.md` 8.2 specify `TextAppearance.App.Title` 16sp/700 for a row
title. Grammar B is nominally 16sp but reaches it with a raw literal, so it is 16sp/400 with no
declared line height (D-12) - a third thing again. **Three grammars, three different title weights,
on the same product surface.**

The fix is not a fourth grammar. `res/values/styles.xml` already ships the vocabulary:
`Widget.Departament.Row` (`:707`), `.Navigation` (`:723`), `.Value` (`:727`), `.Action` (`:734`),
`.Toggle` (`:741`), `.Destructive` (`:749`), `Widget.Departament.Tile` (`:753`), `.Tile.Accent`
(`:762`), `.Tile.Destructive` (`:767`), `Widget.Departament.Divider` (`:909`). Every row in this
tree resolves to one of those six with no new style.

### 4.4 The settings page is painted on the wrong plane

`activity_settings.xml:6` sets the root background to `@drawable/bg_settings_glass`, which is a
solid `?attr/colorSurface` (`#141619` dark). The card wrappers inside `layout_settings_content.xml`
are also `app:cardBackgroundColor="?attr/colorSurface"` (`:30` and five more). **A `#141619` card on
a `#141619` page** - the only thing separating them is the 1dp `colorOutlineVariant` hairline, which
measures 1.16:1 against that ground *by design* (see the `stroke_hairline` comment in `dimens.xml`).
The card boundary is invisible, so the wrappers are pure inflation cost with zero visual payload.
`00-rules.md` 4.7 puts the screen background at `?attr/colorBackground` `#0A0B0D` and the card at
`colorSurface`; six sub-page layouts already get this right (`activity_bypass_list.xml:7`,
`activity_user_asset.xml:7`, `activity_routing_setting.xml:8` all use
`?android:attr/colorBackground`), so the tree is internally inconsistent as well as wrong.

### 4.5 The seamless sub-screen toolbar - owner request 0.4.6, rule 4.8

This is the most repeated defect across peripheral screens, and it has exactly **one** root, not
nineteen. Every sub-page in this wave reaches its toolbar through
`BaseActivity.setContentViewWithToolbar` (`BaseActivity.kt:151` and `:171`), which inflates
`R.layout.activity_base` and calls `setSupportActionBar` on the `MaterialToolbar` inside it. No
peripheral layout declares a toolbar of its own - verified: only `activity_base.xml` and
`activity_main.xml` contain `MaterialToolbar` / `AppBarLayout` in the whole `layout/` directory.

Screens that inherit it, with the call site:

| Screen | Call site | Title passed |
|---|---|---|
| `SettingsActivity` (dead branch) | `SettingsActivity.kt:20` | `title_settings` |
| `AboutActivity` | `AboutActivity.kt:17` | `title_about` |
| `AppPickerActivity` | `AppPickerActivity.kt:51` | `resolveScreenTitle()` |
| `BackupActivity` | `BackupActivity.kt:42` | `title_configuration_backup_restore` |
| `CheckUpdateActivity` | `CheckUpdateActivity.kt:28` | `update_check_for_update` |
| `LocalProxyActivity` | `LocalProxyActivity.kt:54` | - |
| `LogcatActivity` | `LogcatActivity.kt:36` | `title_logcat` |
| `PerAppProxyActivity` | `PerAppProxyActivity.kt:45` | `pa_title` |
| `ProviderSettingsActivity` | `ProviderSettingsActivity.kt:61` | `ps_title` |
| `RoutingEditActivity` | `RoutingEditActivity.kt:35` | `routing_settings_rule_title` |
| `RoutingSettingActivity` | `RoutingSettingActivity.kt:47` | `routing_settings_title` |
| `UrlSchemeListActivity` | `UrlSchemeListActivity.kt:17` | - |
| `UserAssetActivity` | `UserAssetActivity.kt:43` | `title_user_asset_setting` |
| `LoginActivity` | `LoginActivity.kt:65` | `auth_title` / `home_link_telegram` |

**What `activity_base.xml` already gets right**, and must not regress: `android:elevation="0dp"`
(`:13`), `app:elevation="0dp"` (`:15`), `android:stateListAnimator="@null"` (`:14`) - no
lift-on-scroll, no shadow - and no divider is declared anywhere. That is three quarters of rule 4.8.
The defects are the other quarter, and they are four attributes:

| Line | Today | Rule 4.8 / D-2 requires |
|---|---|---|
| `activity_base.xml:11` | `android:layout_height="?attr/actionBarSize"` | `layout_height="wrap_content"` + `minHeight="@dimen/toolbar_height"` 56, as a **minimum** (R2). `?attr/actionBarSize` is 56dp portrait but **48dp landscape** on phones, so the sub-page toolbar silently loses 8dp in landscape and the back arrow's hit box goes under the 48dp floor once the title inset is applied. |
| `activity_base.xml:12` | `android:background="@android:color/transparent"` | `?android:attr/colorBackground`. Transparent works only while the window background happens to be the page colour; the moment a screen sets its own root background - and `activity_settings.xml:6` does, to `colorSurface` - the toolbar sits on the wrong plane. This is why the settings sub-page toolbar reads one surface step lighter than the About toolbar. |
| `activity_base.xml:19` | `app:titleTextAppearance="@style/ToolbarBrandTitle"` | `@style/TextAppearance.App.Title` via `style="@style/Widget.Departament.Toolbar"`. `ToolbarBrandTitle` is Space Grotesk 20sp/700 (`styles.xml:280-285`), and **Space Grotesk maps zero Cyrillic codepoints** (D-1). Every Russian sub-page title in this wave - «О приложении», «Локальный прокси», «Маршрутизация», «Настройки провайдеров», «Резервные копии», «Журнал», «Вход» - is therefore drawn by an undeclared per-OS fallback face, at the wrong size, in the wrong role. `styles.xml:274-278` already documents this as a defect and names `Widget.Departament.Toolbar` (`styles.xml:1008`) as the fix. |
| `activity_base.xml:28` | `app:indicatorColor="@color/color_fab_active"` | `?attr/colorPrimary`. `color_fab_active` is a raw duplicate of the accent (`#4C8DFF` night, `#1E5FC7` day) under a name that says nothing about its role, and it bypasses `ThemeOverlay.Mono`, so the mono theme draws a blue progress bar. |

There is also **no toolbar where the spec wants one**: `12-settings.md` 8.1 gives the settings *tab*
a 56 header with a title and a hairline that fades in at `scrollY > 0` over `motion_state` 220.
Today the tab has neither - `MainActivity.kt:485-486` hides the whole `appbarLayout` on every tab
and clears the title, and `layout_settings_content.xml:19-23` starts straight into a section header.
The tab has no title at all; the user's only confirmation of where they are is the bottom-nav label.

`activity_settings.xml` additionally sets `android:fitsSystemWindows="true"` on a child inflated
**into** `activity_base.xml`'s `content_container`, whose parent already declares it
(`activity_base.xml:6`). Nested inset consumers. Same pattern at `activity_about.xml:5`,
`activity_check_update.xml:5`, `activity_logcat.xml:7`, `activity_user_asset.xml:8`,
`activity_routing_setting.xml:9`.

### 4.6 Two trailing elements, one of them a third touch target

`activity_bypass_list.xml:64-108` is the worst row in the tree. Inside one 56dp row:

- `:74-80` the label «Режим обхода», inline `16sp`;
- `:82-100` a `LinearLayout` with `selectableItemBackgroundBorderless`, `clickable="true"`,
  `focusable="true"`, `contentDescription`, holding a 24dp info glyph - a **second** target;
- `:102-106` a `MaterialSwitch` with `layout_marginStart="4dp"` - a **third** target, 4dp from the
  second, against the 8dp minimum separation of 7.2 and 14.2.

The row itself is not clickable, so the switch is the only way to flip it, contradicting 4.5's "the
whole row is the touch target". The info affordance is a helper line wearing an icon: rule 6 of
`12-settings.md` 2 says the helper says what the row does. `@string/pa_bypass_tips` is already
written; put it under the title and delete the glyph and its hit box.

### 4.7 The neutral tile is the default, and nothing ships it

`Widget.Departament.Tile` (`styles.xml:753-761`) defaults to `@drawable/bg_tile_neutral` +
`@color/icon_glyph_neutral`, exactly as D-5 and `12-settings.md` 2.2 require. The two orphaned row
components already point at the neutral drawable (`layout_setting_row.xml:26,35`,
`layout_setting_toggle_row.xml:23,32`) - and they are the two files nothing includes. Every row that
actually ships uses a coloured tile: **46 coloured tiles across the tree, in 6 hues**, on a surface
whose spec says **zero** coloured tiles and **zero** accent (`12-settings.md` 2.2: "a settings
screen with no blue on it anywhere is correct, not unfinished").

The colour choices are also not a category system, which is D-5's test: `row_mode` and
`row_always_on` share blue; `row_bypass_lan` and `row_ipv6` share green with `ic_globe_24dp`;
`activity_routing_setting.xml:57-65` uses purple with the same globe glyph;
`activity_user_asset.xml:56-64` uses orange with it. **Same glyph, three hues, no encoded meaning.**
That is noise by 3.6's definition.

### 4.8 Section headers are correct - and are the only correct thing about the grouping

`SettingsSectionLabel` (`styles.xml:262-268`) inherits `TextAppearance.App.Title` (16sp/700), sets
`textAllCaps=false` explicitly, and pads 24 above / 8 below at the gutter - precisely 4.2's "section
header at 24 above and 8 below", and precisely the anti-eyebrow rule. Used 24 times across 7 files: `layout_settings_content.xml` 6 (`:20,545,757,963,1213,1339`),
`activity_url_scheme_list.xml` 5, `activity_provider_settings.xml` 4,
`activity_local_proxy.xml` 3 (`:19,238,503`), `activity_backup.xml` 2,
`activity_routing_setting.xml` 2 (`:26,105`), `activity_user_asset.xml` 2 (`:25,104`). **No ALL-CAPS tracked eyebrow exists anywhere in
this wave.** The strings behind them are sentence-case Russian nouns (`strings.xml:568-573`). Keep
every bit of it.

What is wrong is the grouping around them: 6 named groups where `03-direction.md` 7.3 and
`12-settings.md` 2.5 cap it at **4**, and the group contents do not match the spec's IA. Today:
«Подключение», «Обход блокировок», «Интерфейс», «Подписка», «Устройства», «О приложении». Spec:
«Подключение», «Обход блокировок», «Подписки», «Приложение», plus an unnamed footer pair - and the
footer pair is unnamed *because* that is what makes it structurally not a fifth group.

### 4.9 Empty states: there are none

No layout in this wave contains an empty-state block. `res/layout/` has exactly two
(`layout_home_empty.xml`, `layout_servers_empty.xml`), neither in this tree.

| Screen | Today, when the list is empty | 9.5 requires |
|---|---|---|
| `activity_routing_setting.xml:110-115` | blank space under a section header | title + one line + one action |
| `activity_user_asset.xml:109-114` | same | same |
| `activity_bypass_list.xml:116-124` | same | same |
| `activity_app_picker.xml` | a 10-line bare `RecyclerView` with no chrome whatsoever | same |
| `activity_logcat.xml` | a 26-line bare `RecyclerView` in a `SwipeRefreshLayout` | same |

`Widget.Departament.EmptyState.Tile` / `.Title` / `.Line` already exist (`styles.xml:1151`, `:1160`,
`:1169`) and `22-components.md` 15 specifies the component. Copy is proposed in 9.4. "Нет данных"
appears nowhere, which is the one thing 9.5 forbids by name - so the gap is absence, not bad copy.

---

## 5. The sign-in screen - `activity_login.xml` + `LoginActivity.kt`

Owner request 0.4.10 puts this screen in the **redesigned-from-scratch** category, and `14-auth.md`
is a full state machine across four surfaces. `14-auth.md` 1.2 already tabulates ten measured
defects with line numbers; this audit confirms all ten, corrects two counts, and adds the
forms-law findings, which that section does not enumerate.

### 5.1 Confirmed against `14-auth.md` 1.2

| Defect | Confirmed at | Status |
|---|---|---|
| Two filled accent surfaces | `:63`, `:201`, `:272` | confirmed; the count is **3**, not 2 |
| `cornerRadius="26dp"` | `:64`, `:105`, `:202`, `:273`, **`:296`** | confirmed; the count is **5**, not 4 |
| `android:textStyle="bold"` | `:62`, `:200`, `:271` | confirmed |
| Fixed `layout_height="52dp"` | `:57`, `:196`, `:267` | confirmed |
| Everything is a card | `:21` `card_telegram`, `:112` `card_site` | confirmed; 2 cards, spec says zero |
| No state machine | `LoginActivity.kt:224-228` (2FA is a `visibility` toggle), `:285-290` (awaiting is a `visibility` toggle) | confirmed; `LoginState` exists but drives visibility, not surfaces |
| Error at the bottom of a scroll | `:301-311` | confirmed |
| `Toast` for actionable failure | `LoginActivity.kt:371` | confirmed, plus 4 more toast sites in the same file |
| Debug `AlertDialog` dumping the HTTP body | `LoginActivity.kt:335-341` | confirmed; `:346` renders `"HTTP ${error.code}\n$detail"` - an error code visible to a user, 9.4 |
| Zero `textAppearance` on any button | all 5 buttons | confirmed |

### 5.2 The forms law - 00-rules.md 7.4, point by point

| Requirement | Today | Evidence |
|---|---|---|
| Field 56 min height, radius 16, `color_outline_control` border, `color_surface_inset` fill | stock `Widget.Material3.TextInputLayout.OutlinedBox`, no height, Material default radius, `?attr/colorPrimary` box stroke | `:148`, `:168`, `:241`. `Widget.Departament.TextField` (`styles.xml:592`) exists and is unused |
| **Label above the input, always visible. Placeholder is never the label** | all three fields use `android:hint` only - the Material floating label, which *is* the placeholder until focus | `:152`, `:172`, `:245` |
| **Helper text below, present in the markup even when empty** | absent on all three; `app:helperTextEnabled` never set, no helper `TextView` | the layout jumps ~20dp the first time an error appears |
| **Validate on blur, not per keystroke** | validates on **every keystroke** | `LoginActivity.kt:115-120` email, `:121-124` password, `:125-130` code - all `doAfterTextChanged` |
| Error text below the field, `Brush.RedText`, red field border | email and code errors do reach the `TextInputLayout` error slot (`:237`, `:252`), but every **server-side** error goes to one centred `tv_error` at the very bottom of the page in `?attr/colorError` `#F04452` (4.88:1) | layout `:301-311`; `LoginActivity.kt:319-320`. 00-rules 3.5: error *text* on dark uses `@color/ping_bad` `#FF6069` (6.15:1) |
| **After a failed submit, focus moves to the first invalid field** | never happens - no `requestFocus()` in the file | `LoginActivity.kt:232-244`, `:247-259` |
| Correct keyboard type + autofill hints | correct | `:160-162`, `:182-184`, `:253-255` |
| Password show/hide toggle | present | `:174` `endIconMode="password_toggle"` |
| **Submit disabled in flight, and shows the loading state** | disabled: yes (`LoginActivity.kt:299-300`). Loading: **wrong shape** - the label is set to `""` (`:305-306`) instead of going to alpha 0, and the arc is a 24dp `?android:attr/progressBarStyleSmall` (`:207`, `:278`), not the 20dp `Widget.Departament.Progress.Circular.Inline.OnAccent` (`styles.xml:1243`). R8 |
| Empty-field guard is inline | uses `Toast` | `LoginActivity.kt:236` `toast(auth_fields_required)`, `:251` `toast(auth_code_required)` - field errors wearing a system toast |

### 5.3 Additional findings not in `14-auth.md` 1.2

1. **The awaiting spinner is a centred platform spinner over a blank block.** `:79-84` uses
   `?android:attr/progressBarStyle` (the large one) centred above a caption. 00-rules 15 Loading:
   "skeletons shaped like the final content, never a centred spinner over a blank screen".
   `14-auth.md` 1.3.3 replaces it with the product's universal 56dp row.
2. **No timeout state and no cooldown.** `14-auth.md` 4.1 defines `Tg.Timeout` at 180s and 5.6
   requires a re-send cooldown. `LoginActivity` has no timer; `showAwaiting()` (`:285-290`) is
   terminal until the poll resolves, and `btn_restart` (`:96-107`) has no `input_debounce` guard
   (R9), so it can be hammered.
3. **The gate is an activity with a back arrow.** `LoginActivity.kt:65` passes
   `showHomeAsUp = true`. `14-auth.md` 3 makes the gate **a state of the Аккаунт tab**, not a pushed
   screen.
4. **Three layouts in one activity.** `MODE_SITE` / `MODE_TELEGRAM` hide one card and show the other
   (`LoginActivity.kt:79-94`), so the same screen has three visually different compositions with no
   shared frame. That is the surface-map problem `14-auth.md` 3 solves with A / B / C.
5. **`btn_register_site` opens a Custom Tab with no external affordance** (`:287-297`,
   `LoginActivity.kt:380-391`); the user cannot tell it leaves the app. `12-settings.md` 3 A5
   requires the 20dp external glyph for exactly this.
6. **Success is a toast and an immediate `finish()`** in the same frame
   (`LoginActivity.kt:204-209`). 7.1 Success is a 220ms state change plus a word; `14-auth.md` 10
   replaces it with surface D.
7. **No declared ground plane**: root `android:background="@android:color/transparent"` (`:7`) over
   `activity_base`'s untinted `ConstraintLayout`.

---

## 6. Screen-by-screen findings

Severity per 17.2. Every row cites the spec section that decides it.

### 6.1 `layout_settings_content.xml` - the settings tab (1 536 lines)

| Sev | Finding | Evidence | Spec |
|---|---|---|---|
| P1 | 23 rows hand-inlined; the two shared row components are included 0 times | whole file; no `<include>` | 12-settings 8.2, 8.3 |
| P1 | 6 tile hues on one screen | 23 tiles: blue 7, green 6, purple 5, orange 3, red 1, yellow 1 | D-5, 12-settings 2.2 |
| P1 | Card wrappers on a rows surface | `:25`, `:552`, `:764`, `:970`, `:1220`, `:1346` | 00-rules 4.4, 12-settings 2.1 |
| P1 | 9 rows carry value + chevron | `row_mode`, `row_per_app`, `row_dns`, `row_ping_method`, `row_mux_concurrency`, `row_appearance`, `row_language`, `row_sub_auto_update`, `row_about` | 00-rules 4.5 |
| P1 | Row title on `App.Body` 14/400, not `App.Title` 16/700 | `:70-75` and 22 more | 00-rules 4.5, 12-settings 8.2 |
| P1 | 6 named groups; the cap is 4 | `:20,545,757,963,1213,1339` | 03-direction 7.3 |
| P1 | No screen title and no header; the tab is anonymous | `MainActivity.kt:485-486` hides the app bar; the file starts at a section header | 12-settings 8.1 |
| P2 | Divider inset `72dp` ×17; the token is `@dimen/divider_inset_start` 68 | `:96-100` and 16 more | 00-rules 4.1 |
| P2 | Chevron 18dp ×9; 18 is in no icon-size token | `:87-93` and 8 more | 00-rules 10.3 |
| P2 | Tile-to-text gap `space_16`; the spec says `space_12` | `:71` and 22 more | 00-rules 4.5 |
| P2 | 36 off-scale dp | 2.3 | 00-rules 1.4.5 |
| P3 | ALL-CAPS XML section comments (`<!-- ==== ПОДКЛЮЧЕНИЕ ==== -->`) | `:18` and 5 more | markup habit, not a ban hit |
| P3 | `android:visibility="gone"` on an included root that `MainActivity` controls - dead markup | `:10` | - |

### 6.2 `layout_setting_row.xml` / `layout_setting_toggle_row.xml` - the orphans

| Sev | Finding | Evidence |
|---|---|---|
| P1 | Never included; 0 references in `java/` or `layout/` | grep clean |
| P1 | Title on `App.Body`, not `App.Title` | `:49` / `:47` |
| P2 | Two trailing elements built in (`setting_value` + `setting_chevron`) | `layout_setting_row.xml:63-79` |
| P2 | Chevron 18dp | `:74-75` |
| P2 | Tile-to-text gap 16; the spec says 12 | `:41` / `:38` |
| P3 | `stateListAnimator="@anim/press_scale"` on a **row**: R5 says rows step their background, they do not scale - scaling tears the hairlines above and below | `:17` / `:14`; `@drawable/bg_row` exists and is what `Widget.Departament.Row` uses |
| - | Correct today and to keep: the neutral tile by default, with the comment explaining the accent budget | `:19-21` |

### 6.3 `activity_settings.xml` + `pref_settings.xml` + `preference_with_help_link.xml` - the dead branch

| Sev | Finding | Evidence |
|---|---|---|
| P1 | An unreachable second settings surface: 55 keys, 6 categories, English | `AndroidManifest.xml:88-90`; no launcher anywhere |
| P1 | The page is painted `colorSurface`, so its cards vanish | `activity_settings.xml:6` -> `bg_settings_glass.xml` |
| P2 | `preference_with_help_link.xml:7` binds `onClick="onModeHelpClicked"` by name to a method that does not exist | `SettingsActivity.kt` has no such method |
| P2 | `textStyle="italic"` is not in the ramp | `preference_with_help_link.xml:10` |
| P2 | Nested `fitsSystemWindows` | `activity_settings.xml:7` inside `activity_base.xml:6` |

### 6.4 `activity_local_proxy.xml` (1 035 lines) + `LocalProxyActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | 75 off-scale dp - the worst file in the tree | 2.3 |
| P1 | 37 inline `textSize` | 2.2 |
| P1 | 5 fixed-height 44dp buttons in a toggle group | `:104,117,130,143,156` |
| P1 | Raw `EditText` with a hand-rolled `@drawable/bg_lp_input` background instead of `Widget.Departament.TextField` | `:361-372`, `:407`, `:451`, `:475` |
| P1 | Field label above the input at `12sp` `colorOnSurfaceVariant` - the Caption role used as a label; the field block spec says `Title.Medium` 16/500 | `:352-357` and 5 more |
| P1 | No helper slot under any field | whole file |
| P2 | 3 card wrappers; 8 coloured tiles in 5 hues | `:24`, `:243`, `:508` |
| P2 | 44dp copy `ImageButton`s at `marginStart="6dp"` - under the 48dp target floor and under the 8dp separation floor | `:375-383`, `:431`, `:848`, `:896`, `:954` |
| P2 | The 40/60/80/100/150 memory chips are a `MaterialButtonToggleGroup` styled `?attr/materialButtonOutlinedStyle` with `textSize="13sp"` - not `Widget.Departament.SegmentGroup` / `.Segment` (`styles.xml:523`, `:545`) - and 5 options exceed the 2-3 segment cap | `:91-165` |
| P2 | Dividers written `68dp` raw where the token exists | `:167-171` and 12 more |
| P3 | Section headers correct | `:19`, `:238`, `:503` - keep |

### 6.5 `activity_provider_settings.xml` (648 lines) + `ProviderSettingsActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | 62 off-scale dp, 15 inline `textSize` | 2.2, 2.3 |
| P1 | 4 card wrappers; 9 coloured tiles in 5 hues | grep |
| P1 | «Автообновление подписки» lives here **and** in the hub (`row_sub_auto_update`) - two taps apart, two grammars, one MMKV field | `12-settings.md` 1, confirmed |
| P2 | `setPositiveButton(android.R.string.ok)` | `ProviderSettingsActivity.kt:199` |
| P2 | Grammar B rows: 60dp minHeight, 14dp padding, 18dp chevron | throughout |

### 6.6 `activity_about.xml` + `AboutActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | Grammar C: 5 rows, no tile, bare 24dp untinted icon, `TextAppearance.AppCompat.Subhead`, no trailing element, no `minHeight` | `:18-41`, `:43-66`, `:68-91`, `:94-117`, `:119-142` |
| P1 | Two version `TextView`s both defaulted to `@string/title_about`, both `AppCompat.Small`, centred | `:151-163` |
| P1 | The five rows that leave the app (source code, licences, feedback, Telegram channel, privacy policy) carry no external affordance | all five |
| P2 | Literal `"OK"` dialog button | `AboutActivity.kt:33` |
| P2 | Legacy dimens `padding_spacing_dp16`, `image_size_dp24` throughout | `:27`, `:30-31` and 12 more |
| P2 | Nested `fitsSystemWindows` | `:5` |

### 6.7 `activity_check_update.xml` + `CheckUpdateActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | `androidx.appcompat.widget.SwitchCompat` with the **label inside the switch widget** and `app:theme="@style/BrandedSwitch"` - a different switch component and a different row anatomy from every other toggle in the app | `:28-36` |
| P1 | The row is `clickable="true"` **and** the switch is independently clickable: two targets, one boolean | `:12-37` |
| P1 | Grammar C again: 24dp icon, no tile, `AppCompat.Subhead` / `AppCompat.Small` | `:50-61` |
| P2 | The update check has no loading, no error and no "already up to date" state in the layout | whole file |
| P2 | `tv_version` defaults to `@string/title_about` | `:72-77` |

### 6.8 `activity_bypass_list.xml` + `item_recycler_bypass_list.xml` + `activity_app_picker.xml` + `PerAppProxyActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | Three targets in one row, 4dp apart | `activity_bypass_list.xml:64-108`, see 4.6 |
| P1 | `cardCornerRadius="18dp"` - 18 is not in the shape lock (12 / 16 / 20 / 24 / pill) | `item_recycler_bypass_list.xml:14` |
| P1 | One card per app row (`marginHorizontal="12dp"`, `marginVertical="4dp"`) - the identical-card-grid tell where a divided list belongs | `item_recycler_bypass_list.xml:6-10` |
| P1 | `textSize="15sp"` (does not exist) + `12sp`, `marginTop="2dp"` | `item_recycler_bypass_list.xml:47,55,59` |
| P1 | `GridLayoutManager` for a vertical list of apps | `activity_bypass_list.xml:124` |
| P1 | `activity_app_picker.xml` is a 10-line bare `RecyclerView`: no header, no search, no empty state, no chrome | whole file |
| P2 | `cardCornerRadius="20dp"` written raw where `@dimen/radius_card` exists | `activity_bypass_list.xml:23` |
| P2 | The app icon is 40dp with `padding="4dp"` and no tile - the only leading treatment in the product that is not a 40 tile with a 22 glyph | `item_recycler_bypass_list.xml:31-35` |
| P2 | `12-settings.md` 0.3 merges `PerAppProxyActivity` and `AppPickerActivity` into one route; today they are two activities over one data set | `AppPickerActivity.kt:51` |

### 6.9 `activity_routing_setting.xml`, `activity_routing_edit.xml`, `activity_user_asset.xml`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | Grammar B rows: `minHeight="60dp"`, `paddingHorizontal="14dp"`, `marginStart="14dp"`, inline `16sp`/`13sp`, `marginTop="2dp"`, chevron `18dp` | `activity_routing_setting.xml:41-100`, `activity_user_asset.xml:40-99` |
| P1 | A single row wrapped in its own card | `activity_routing_setting.xml:31-102`, `activity_user_asset.xml:30-101` |
| P1 | No empty state on either `RecyclerView` | `activity_routing_setting.xml:110-115`, `activity_user_asset.xml:109-114` |
| P1 | `RecyclerView` with `layout_height="match_parent"` + `nestedScrollingEnabled="false"` inside a `NestedScrollView` - recycling is defeated, every rule / asset row inflates at once | both files, `:112-113` |
| P2 | Purple / orange tiles both carrying `ic_globe_24dp` | `activity_routing_setting.xml:57-65`, `activity_user_asset.xml:56-64` |
| P2 | 5 `android.R.string.ok` dialogs across the three activities | `RoutingSettingActivity.kt:102,124,172`, `RoutingEditActivity.kt:149`, `UserAssetActivity.kt:222` |
| P2 | 17 toast sites for save / delete / import results where a snackbar with undo belongs (7.5) | `RoutingSettingActivity.kt` 8, `UserAssetActivity.kt` 7, `RoutingEditActivity.kt` 2 |
| P3 | Section headers correct on both | `activity_routing_setting.xml:26,105`; `activity_user_asset.xml:25,104` |

### 6.10 `activity_url_scheme_list.xml` (634 lines) + `UrlSchemeListActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | 9 hand-copied identical blocks, 6 card wrappers - the identical-card-grid ban | 9 repeats from `:100` |
| P1 | 9 `fontFamily="monospace"` - a third font family | `:121,173,244,296,367,438,490,561,613` |
| P1 | 20 inline `textSize`, 37 off-scale dp | 2.2, 2.3 |
| P1 | 9 hard-coded `android:text="depv://…"` scheme strings in the layout instead of a data source | `:122,174,245,297,368,439,491,562,614` |
| P2 | Em-dash ×2 in `@string/url_scheme_note_body` | `strings_deeplink.xml:6` |
| P2 | The whole screen becomes one list at `settings/about/urlschemes` | 12-settings 0.3, 5.16 |

### 6.11 `activity_backup.xml` + `BackupActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | **18 toast sites** - the highest in the app, on a screen whose every operation is destructive or long-running | `BackupActivity.kt` |
| P1 | 26 off-scale dp, 4 inline `textSize`, 2 card wrappers, 4 tile hues | 2.2, 2.3 |
| P1 | Backup / restore has no progress, no partial state and no error state - only toasts after the fact | whole file |
| P2 | 4 dialogs, restore confirm included; 7.5 wants a confirm only for the irreversible one and a snackbar-with-undo elsewhere | `BackupActivity.kt` |

### 6.12 `activity_logcat.xml` + `LogcatActivity.kt`

| Sev | Finding | Evidence |
|---|---|---|
| P1 | 26-line bare `RecyclerView`: no header, no filter, no empty state, no visible copy action | whole file |
| P2 | Reachable only through the dead `pref_settings.xml` branch today - `12-settings.md` 0.3 says "restyled as `settings/about/log`, **reachable for the first time**"; confirmed, no entry point exists in `layout_settings_content.xml` | - |
| P2 | 3 toast sites | `LogcatActivity.kt` |

### 6.13 `widget_switch.xml`

| Sev | Finding | Evidence |
|---|---|---|
| P2 | `45dp` image (off-scale), `padding_spacing_dp16` (retired), `TextAppearance.AppCompat.Small`, `@android:color/white` - a raw platform colour that ignores all three themes | `:19-31` |

---

## 7. Component conformance - `22-components.md` 4, 5, 6, 7, 8 and R15

R15: the 15 components in that file are the entire vocabulary. Nothing in this wave **invents** a
component; several fail to **use** one that exists and compiles today.

| Spec | Component | Status in this tree |
|---|---|---|
| §4 Text field | `Widget.Departament.TextField` (`styles.xml:592`), `.EditText` (`:633`), `.Search` (`:621`), `.ReadOnly` (`:650`), `ThemeOverlay.Departament.TextField` (`:656`) | **0 uses.** `activity_login.xml` uses stock `Widget.Material3.TextInputLayout.OutlinedBox` ×3; `activity_local_proxy.xml` uses raw `EditText` + `@drawable/bg_lp_input` ×6. No label-above and no helper slot anywhere in the tree. |
| §5 Select | picker sheet (`BottomSheetDialogFragment`); `PaymentMethodSheet.kt` is the reference | **0 uses.** 6 `setSingleChoiceItems` `AlertDialog`s + 2 free-value dialogs instead (`MainActivity.kt:3003,3051,3111,3218,3253,3309,3130,3160`). |
| §6 Segmented control | `Widget.Departament.SegmentGroup` (`:523`), `Widget.Departament.Segment` (`:545`), `TextAppearance.App.Title.Segment.Active` (`:566`) | **0 uses.** The one toggle group in the tree (`activity_local_proxy.xml:91-165`) is stock outlined buttons at a fixed 44dp with `13sp` labels, and **5** options where the cap is 3. |
| §7 Switch | `Widget.Departament.Switch` (`:681`) | **0 uses.** `MaterialSwitch` unstyled ×8 in the hub, ×3 in local proxy, ×2 in bypass list; `SwitchCompat` + `BrandedSwitch` ×1 in check-update. Two switch components in one product. |
| §8 Row, five archetypes | `Widget.Departament.Row` + `.Navigation` / `.Value` / `.Action` / `.Toggle` / `.Destructive` (`:707-751`), `Widget.Departament.Tile` + `.Accent` / `.Destructive` (`:753-769`), `Widget.Departament.Divider` (`:909`) | **0 uses** across ~46 rows in this wave. Three ad-hoc grammars instead (4.3). |
| §12 Toolbar | `Widget.Departament.Toolbar` (`:1008`), `.Brand` (`:1024`) | **0 uses.** `activity_base.xml:19` still points at `ToolbarBrandTitle`. |
| §15 Empty state | `Widget.Departament.EmptyState.Tile` / `.Title` / `.Line` (`:1151-1175`) | **0 uses**, and 5 screens need one. |
| §17 Progress | `Widget.Departament.Progress.Circular.Inline` (`:1234`), `.OnAccent` (`:1243`), `.Linear` (`:1214`) | **0 uses.** `activity_login.xml` uses `?android:attr/progressBarStyle` and `progressBarStyleSmall`; `activity_base.xml:21-28` uses a stock `LinearProgressIndicator` with a raw colour name. |
| §2 Buttons | `Widget.Departament.Button.Primary` / `.Tall` / `.Secondary` / `.Tertiary` / `.Destructive` / `.Icon` (`:379-511`) | **0 uses.** `activity_login.xml` sets `backgroundTint` + `cornerRadius` + `textStyle` + a fixed height by hand ×5; `activity_local_proxy.xml` uses `?attr/materialButtonOutlinedStyle` ×5 and raw `ImageButton` ×6. |
| §14 Snackbar | `Widget.Departament.Snackbar` + `.TextView` + `.Button` (`:1114-1135`) | **0 uses**, against 57 toast call sites. |

**The component layer is complete and unused.** That is the single highest-leverage fact in this
audit: nearly every P1 in section 6 is fixed by applying a style that already compiles, not by
designing anything new.

---

## 8. The state matrix

### 8.1 `00-rules.md` 15 - the eleven generic states

Legend: **Y** implemented, **P** partial, **N** absent, **-** not applicable.

| Screen | Default | First run | Loading | Empty | Error | Offline | Partial | Long | Short | Gated | Success |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Settings tab | Y | - | N | - | N | N | N | N | - | N | N |
| Per-app proxy (bypass list) | Y | N | N | N | N | - | N | P | N | - | N |
| App picker | Y | N | N | N | N | - | N | N | N | - | - |
| Local proxy | Y | - | N | - | N | N | N | N | - | N | N |
| Provider settings | Y | N | P | N | N | N | N | N | N | N | N |
| Routing settings | Y | N | N | N | N | - | N | P | N | - | N |
| Routing edit | Y | - | N | - | N | - | - | N | - | - | N |
| User assets | Y | N | P | N | N | N | N | N | N | - | N |
| Backup | Y | - | N | - | N | N | N | - | - | N | N |
| Check update | Y | - | P | - | N | N | - | - | - | - | N |
| Logcat | Y | - | P | N | N | - | - | N | N | - | - |
| URL schemes | Y | - | - | - | - | - | - | - | - | - | N |
| About | Y | - | - | - | - | - | - | - | - | - | - |
| Sign-in | Y | N | P | - | P | N | - | N | - | - | P |

Totals across the applicable cells: **Default 14 / 14. Everything else 0 Y, 8 P, 61 N.**

Notes on the `P`s:
- Loading `P` on provider settings / user assets / check update / logcat / sign-in = the shared
  `LinearProgressIndicator` in `activity_base.xml:21-28`, shown via `BaseActivity.showLoading()`.
  It is a 4dp bar under a toolbar, not a skeleton shaped like the content, and it appears
  immediately rather than after 300ms. 15 Loading is not satisfied by it.
- Error `P` on sign-in = the centred `tv_error` (5.2).
- Success `P` on sign-in = `toastSuccess` + `finish()` in the same frame (5.3.6).
- Long `P` on the bypass list and routing = `maxLines` is set
  (`item_recycler_bypass_list.xml:47,58`) but to `1` and `3`, and 4.5 requires a row title to wrap
  to 2 rather than ellipsise.

**Offline is absent from the entire tree.** 9.6 makes it a designed state with a persistent quiet
bar and disabled network actions. Provider settings, user assets (geo file download), check update
and backup-to-WebDAV are all network operations that today fail into a `Toast`.

### 8.2 The product gate states

`00-rules.md` 15, second table. Which screens in this wave must render each, and what happens today.

| Gate state | Screens that must render it here | Today |
|---|---|---|
| `нет подписки` | settings tab «Автообновление подписки»; provider settings; sign-in | **N.** The row is enabled and opens a picker over an empty list. `@string/settings_sub_auto_update_empty` («Сначала добавьте подписку») exists at `strings_settings_hub.xml:31` and is **not wired to a disabled-row helper** - 12-settings 3.1 requires a disabled row to state its reason in the helper. |
| `подписка истекает` | settings tab; provider settings | **N.** No warning chip, no amber, nowhere in the tree. |
| `подписка истекла` | settings tab; provider settings; sign-in | **N.** |
| `триал` | settings tab (auto-update row) | **N.** The trial flag is never read in this tree. |
| `Telegram не привязан` | sign-in, surface E (`14-auth.md` 8.4) | **P.** `LoginActivity` has `EXTRA_LINK` (`:63`, `:404`) and re-uses the whole sign-in activity for linking; `14-auth.md` 3 makes E a bottom sheet re-using A's awaiting row. Today the site card stays visible unless `EXTRA_MODE` is also passed. |
| `нет серверов` | provider settings; settings tab «Настройки провайдеров» | **N.** No empty state. |
| `подключение` | settings tab: mode, DNS, IPv6, bypass-LAN, Mux, fragmentation are all `при переподключении` in the spec | **N.** No row anywhere shows when a change applies. `12-settings.md` 0.2 makes Effect a required column of every row. |
| `подключено` | the same six rows: changing them while the core runs must re-apply live and say so | **N.** `MainActivity.bindSettingsState` writes MMKV; no transient message, no re-apply signal. 12-settings 4.2 specifies `Переподключаемся, чтобы применить настройку`. |
| `отключение` | the same six rows | **N.** |
| `ошибка туннеля` | settings tab; local proxy | **N.** |
| `лимит устройств` | owned by the account tab, not this wave | **-** |

**8 of the 10 applicable product gate states are absent and 1 more is partial.** That is B17, P1 by
17.2.

### 8.3 The interaction states - `00-rules.md` 7.1

| State | Required | Present in this wave |
|---|---|---|
| Default | all | Y |
| Hover | n/a on Android | - |
| **Focus** | every focusable control, 2dp ring (R7) | **N.** `@drawable/focus_ring.xml` exists; **0** references in these 21 layouts against 55 `focusable="true"`. |
| Pressed | 0.97 / 90 / 160 for objects; a background step for rows (R5) | **P.** `?attr/selectableItemBackground` on ~46 rows is the right channel, but `@anim/press_scale` is 0.96 not 0.97 (D-11, owned by the token wave) and is applied to two *rows* that should step instead (6.2). |
| Selected | two axes, never tint alone | **N.** The only selectable set is the memory toggle group (`activity_local_proxy.xml:91-165`), which relies on the Material checked tint alone. |
| Disabled | 0.38 on the whole control | **N.** No row in the tree renders disabled; the one row with a documented disabled reason does not use it. |
| Loading | width held, label hidden, 20dp arc | **N.** See 5.2 and 8.1. |
| Error | inline under the field | **N**, except the two `TextInputLayout` slots on sign-in. |
| Success | 220ms tint plus the word | **N.** 57 toast call sites instead. |

---

## 9. Copy - `00-rules.md` 9

### 9.1 The dash rewrites (9.7, ban 1.4.11)

| File:line | Today | Proposed |
|---|---|---|
| `strings_local_proxy.xml:13` | `Меньше — экономнее, но возможны замедления` | `Меньше памяти, экономнее, но возможны замедления` |
| `strings_local_proxy.xml:41` | `…в настройках другого устройства — тогда его трафик пойдёт через VPN.` | `…в настройках другого устройства. Тогда его трафик пойдёт через VPN.` |
| `strings_local_proxy.xml:44` | `Wi-Fi недоступен — включите точку доступа или подключитесь к Wi-Fi` | `Wi-Fi недоступен. Включите точку доступа или подключитесь к сети` |
| `strings_perapp.xml:7` | `…идут через прокси, остальные — напрямую.` ×2 | `…идут через прокси, остальные напрямую.` ×2 |
| `strings_deeplink.xml:6` | `{base64} — конфигурация…, {url} — ссылка…` | `{base64}: конфигурация или правила в Base64. {url}: ссылка на подписку или конфигурацию.` |
| `strings_auth.xml:4` (comment) | `…in strings.xml — do not redefine here.` | `…in strings.xml, do not redefine here.` |

### 9.2 Terminology lock 9.3 - checked against every string this wave renders

Clean: «подписка», «сервер», «провайдер», «устройство», «тариф», «Войти», «Привязать Telegram» are
used correctly across `strings_settings_hub.xml`, `strings_provider.xml`, `strings_auth.xml`,
`strings_local_proxy.xml`. No «нода», no «конфиг» in user-visible copy, no «Личный кабинет», no
«руб.», no «RUB», no final periods on labels, no exclamation marks.

Two to fix:
- `@string/settings_provider` = «Настройки провайдеров». A settings row title is a **noun** (9.2)
  and «Настройки» is redundant inside Настройки. -> «Провайдеры», keeping the existing helper
  «Автообновление, HWID, User-Agent» (`strings_settings_hub.xml:9`).
- `@string/pa_bypass_mode` = «Режим обхода» names a mechanism, not a consequence.
  `12-settings.md` 5.1 makes this a 2-segment control «Через прокси» / «Напрямую»; the explanation
  moves from the info glyph (4.6) into the helper line.

### 9.3 Section headers, sentence case

All 6 hub headers are correct sentence-case Russian nouns and none is ALL-CAPS
(`strings.xml:568-573`). The XML **comments** around them are written in caps
(`layout_settings_content.xml:18` and 5 more) - markup, not copy, so not a ban hit, but it is the
visual habit that produces eyebrows and the rebuilt file should not carry it forward.

### 9.4 Proposed empty states (9.5 formula: title + one line + one action)

| Screen | Title | Line | Action |
|---|---|---|---|
| Routing settings | `Правил пока нет` | `Добавьте правило, чтобы часть трафика шла в обход туннеля.` | `Добавить правило` |
| User assets | `Файлов ресурсов нет` | `Скачайте geoip и geosite, чтобы правила маршрутизации заработали.` | `Обновить сейчас` |
| Per-app proxy | `Приложений не выбрано` | `Отметьте приложения, чтобы правило заработало.` | `Выбрать приложения` |
| App picker, search empty | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| Logcat | `Журнал пуст` | `Записи появятся после запуска подключения.` | `Обновить` |
| URL schemes | - (never empty, 9 fixed entries) | - | - |

### 9.5 Proposed snackbar strings to replace the toasts (9.4 formula, each with a recovery affordance)

| Operation | Today | Proposed text | Action |
|---|---|---|---|
| Backup export failed | toast | `Не удалось создать копию. Проверьте место на устройстве и повторите.` | `Повторить` |
| Backup restore failed | toast | `Не удалось восстановить копию. Файл повреждён или создан другой версией.` | `Выбрать файл` |
| Geo file download failed | toast | `Не удалось скачать файлы ресурсов. Проверьте сеть и повторите.` | `Повторить` |
| Routing rule save failed | toast | `Правило не сохранено. Проверьте домены и повторите.` | `Повторить` |
| Update check failed | toast | `Не удалось проверить обновления. Проверьте сеть и повторите.` | `Повторить` |
| Rule / asset deleted | toast | `Правило удалено` | `Отменить` (7.5, undo beats confirm) |
| Sign-in, empty fields | `toast(auth_fields_required)` | move under the field: `Введите почту` / `Введите пароль` | - |
| Telegram not installed | `toastError` | inline on surface A: `Telegram не установлен` / `Установите Telegram или войдите по почте.` | `Войти по почте` |

### 9.6 The effect labels the rows are missing (12-settings 0.2)

Six rows change core config and say nothing about when the change lands. Proposed: the helper
suffix `Применится при переподключении` while the core is running, plus the transient
`Переподключаемся, чтобы применить настройку` on write while connected - the exact strings
`12-settings.md` 4.2 specifies. Rows: `row_mode`, `row_dns`, `row_bypass_lan`, `row_ipv6`,
`row_mux`, `row_fragment`.

---

## 10. The work order

Ordered by severity, then by leverage. Every item names the file, the exact change, the spec that
decides it, and the risk. **No item asks for a new style, a new token, a new drawable or a new
component.** Where an item touches a file another wave owns, the boundary is stated.

### P0 - none

No defect in this tree prevents task completion. The screens work; they are wrong, not broken.

### P1 - blocking release

**W1. The seamless sub-page toolbar: four attributes in one file.**
`res/layout/activity_base.xml`. `:11` `layout_height="?attr/actionBarSize"` ->
`layout_height="wrap_content"` + `android:minHeight="@dimen/toolbar_height"`; `:12`
`@android:color/transparent` -> `?android:attr/colorBackground`; `:19` apply
`style="@style/Widget.Departament.Toolbar"` on the `MaterialToolbar` and delete the local
`titleTextAppearance`, `background` and `elevation` attributes it supersedes; `:28`
`app:indicatorColor="@color/color_fab_active"` -> `?attr/colorPrimary`.
Spec: 00-rules 4.8, D-1, D-2, R2, `22-components.md` 12.
Risk: **the highest-leverage change in this audit** - it repairs 14 screens at once, and a
regression would hit 14 screens at once. `Widget.Departament.Toolbar` already sets
`contentInsetStart` 16, `navigationIconTint` and `titleTextColor`, so the visual delta is title
face, title size and toolbar plane. Verify the back arrow still clears the 16 gutter at 360dp, and
verify landscape now holds 56 rather than 48.

**W2. Collapse the 23 hand-inlined rows onto the shared archetypes.**
`res/layout/layout_settings_content.xml` + `layout_setting_row.xml` + `layout_setting_toggle_row.xml`.
Rewrite the two orphans as the A1/A2/A5/A6 and A3 components of `12-settings.md` 8.2 applying
`@style/Widget.Departament.Row.*` and `@style/Widget.Departament.Tile`; then either `<include>`
them 23 times, or - the spec's answer, and the right one - replace the file with the ~40-line
`RecyclerView` shell of `12-settings.md` 8.1 over the sealed model of 8.3.
Spec: 12-settings 8.1-8.3, 00-rules 4.5, `22-components.md` 8.
Risk: the 23 click handlers live in `MainActivity.setupSettings()` (`:2871-2914`) and the state
reflection in `bindSettingsState()` (`:2916+`). **That plumbing is another wave's.** Do the visual
collapse against the existing ids first - every id in the 4.2 table must survive - so the handler
file needs no edit; the model rewrite is a second, coordinated step.

**W3. One trailing element per row.**
Same file: drop the chevron from the 8 A2 value rows (`row_mode`, `row_per_app`, `row_dns`,
`row_ping_method`, `row_mux_concurrency`, `row_appearance`, `row_language`, `row_sub_auto_update`);
drop the value from `row_about` and show the version on the About screen where it belongs. In
`activity_bypass_list.xml:82-100` delete the info glyph and its hit box and move
`@string/pa_bypass_tips` into the row helper.
Spec: 00-rules 4.5, 12-settings 2.3, 2.4.
Risk: `s.valueAbout.text = BuildConfig.VERSION_NAME` (`MainActivity.kt:2913`) must move with the
value it sets.

**W4. Neutral tiles everywhere in settings; retire the six-hue system.**
`layout_settings_content.xml` (23), `activity_provider_settings.xml` (9),
`activity_local_proxy.xml` (8), `activity_backup.xml` (4), `activity_user_asset.xml` (1),
`activity_routing_setting.xml` (1). Replace every
`@drawable/bg_icon_{blue,green,orange,purple,red,yellow}` + `?attr/iconTint*` pair with
`@style/Widget.Departament.Tile`. The only exception in this wave would be a genuinely destructive
row taking `.Tile.Destructive`; there is currently none.
Spec: D-5, 00-rules 3.6, 12-settings 2.2.
Risk: none - the old colour resources stay in `colors.xml` until their last reference dies, per D-5.

**W5. Rows, not cards.**
Delete all 27 `MaterialCardView` wrappers in the settings tree; replace the boundary with a section
header plus 24 of space, and a 1dp `?attr/colorOutlineVariant` hairline at
`@dimen/divider_inset_start` 68 between siblings (`@style/Widget.Departament.Divider`). In
`item_recycler_bypass_list.xml` replace the per-item card with a flat row plus hairline.
Spec: 00-rules 4.4, 12-settings 2.1.
Risk: `activity_settings.xml:6` paints the page `colorSurface`; land W6 in the same change or the
un-carded rows sit on a lighter plane than every other screen.

**W6. Put the settings page on the ground plane.**
`activity_settings.xml:6`: `@drawable/bg_settings_glass` -> `?android:attr/colorBackground`; delete
`res/drawable/bg_settings_glass.xml` when its last reference dies. Remove the redundant
`fitsSystemWindows="true"` at `activity_settings.xml:7`, `activity_about.xml:5`,
`activity_check_update.xml:5`, `activity_logcat.xml:7`, `activity_user_asset.xml:8`,
`activity_routing_setting.xml:9` - `activity_base.xml:6` already declares it.
Spec: 00-rules 4.7. Risk: low; check the status-bar treatment on a gesture-nav device.

**W7. The sign-in screen: rebuild to `14-auth.md`, do not patch.**
`res/layout/activity_login.xml` + `ui/LoginActivity.kt`. Owner request 0.4.10. Four surfaces
(`14-auth.md` 3), the state machine of 4.1, zero cards, exactly one filled accent surface at any
instant. Interim, if the rebuild lands behind this wave, the seven changes that stop the screen
being a ban magnet: apply `@style/Widget.Departament.Button.Primary.Tall` to `btn_telegram` and
delete its `cornerRadius` / `textStyle` / `backgroundTint` / fixed height; demote `btn_site` and
`btn_confirm_2fa` to `.Secondary`; apply `@style/Widget.Departament.TextField` +
`ThemeOverlay.Departament.TextField` to the three fields; add a persistent label above and an
always-present helper slot below each; move the error from `tv_error` to the field it belongs to,
in `@color/ping_bad`; replace the two `ProgressBar`s with
`@style/Widget.Departament.Progress.Circular.Inline.OnAccent` and hide the label with alpha rather
than with `""`.
Spec: 14-auth 1.2, 3, 4; 00-rules 7.4, 4.3; `22-components.md` R2, R3, R8, §4.
Risk: `AuthViewModel` and the polling belong to another wave; every change above is view-layer only.

**W8. The forms law on every field in the tree.**
`activity_local_proxy.xml` (6 raw `EditText`s), `activity_routing_edit.xml`,
`activity_provider_settings.xml`. Label above at `TextAppearance.App.Title.Medium`, field at
`@style/Widget.Departament.TextField`, helper slot present-but-empty below, validate on blur, error
below the field, focus to the first invalid field on a failed submit.
Spec: 00-rules 7.4, 12-settings 3 "the field block", `22-components.md` 4.
Risk: `LocalProxyActivity` reads these by id; keep the ids.

**W9. Focus rings.**
`@drawable/focus_ring.xml` on all 55 focusable controls in this wave, per the two-case rule of 7.1
(inside for filled controls, outside at 2dp offset for everything else). Cheapest route: the row
styles already use `@drawable/bg_row`, so add the `state_focused` layer there once and every row
inherits it.
Spec: 00-rules 7.1 R7, 14.4. Risk: `bg_row.xml` is the token wave's file - coordinate.

**W10. Delete the dead settings branch.**
`ui/SettingsActivity.kt`, `res/xml/pref_settings.xml` (55 keys), `res/layout/activity_settings.xml`,
`res/layout/preference_with_help_link.xml`, and the `<activity android:name=".ui.SettingsActivity">`
block at `AndroidManifest.xml:88-90`. Triage all 55 keys against `12-settings.md` 4-6 first: that
cut list removes 21 of them and rehomes the rest; nothing may be deleted without landing in the
triage.
Spec: 12-settings 0.3, 6. Risk: **the highest-risk item here.** `MmkvPreferenceDataStore` is the
only writer for several keys today; verify each key's writer before removing its UI.

**W11. Empty states on the five list screens.**
Routing settings, user assets, per-app proxy, app picker, logcat, using
`@style/Widget.Departament.EmptyState.Tile/.Title/.Line` and the copy in 9.4.
Spec: 00-rules 15, 9.5, `22-components.md` 15. Risk: none.

**W12. The product gate states.**
Wire `нет подписки` to the disabled row plus helper on `row_sub_auto_update` (the string already
exists); add `подписка истекает` / `истекла` / `триал` to the same row and to provider settings; add
the `при переподключении` effect helper and the `Переподключаемся, чтобы применить настройку`
transient to the six core-config rows (9.6).
Spec: 00-rules 15, 12-settings 0.2, 3.1, 4.2.
Risk: the subscription state belongs to the account wave; the view contract is specified here so the
two can land independently.

**W13. Kill the 57 toasts.**
Snackbar with an action, or inline, per 1.4.8 and 9.4. Priority by count: `BackupActivity` 18,
`RoutingSettingActivity` 8, `UserAssetActivity` 7, `LocalProxyActivity` 6, `LoginActivity` 5,
`PerAppProxyActivity` 4, `CheckUpdateActivity` 3, `LogcatActivity` 3, `RoutingEditActivity` 2,
`UrlSchemeListActivity` 1. Strings in 9.5. `@style/Widget.Departament.Snackbar` + `.TextView` +
`.Button` exist (`styles.xml:1114-1135`).
Spec: 00-rules 1.4.8, 7.5, 9.4.
Risk: destructive operations should become act-plus-undo rather than confirm-plus-toast; that is a
behaviour change and needs the owning wave.

**W14. Replace the 8 choice dialogs with picker sheets.**
`MainActivity.kt:3003,3051,3111,3218,3253,3309` (single choice) and `:3130`, `:3160` (free value).
`PaymentMethodSheet.kt` is the reference chrome; `ThemeOverlay.Departament.BottomSheet` and
`Widget.Departament.Sheet` exist (`styles.xml:1038`, `:1043`).
Spec: 00-rules 7.6, 12-settings 2.9, 8.4.
Risk: `MainActivity` is another wave's file; deliver the sheet class and the row contract and let
them swap the call sites.

**W15. The three inline type families.**
Delete all 93 inline `textSize` / `fontFamily` and bind each `TextView` to its ramp role. The 18
`15sp` hits have no ramp equivalent and must be **decided, not rounded**: a row title is
`App.Title` 16/700, a row subtitle is `App.Subtitle` 13/400. The 9 `monospace` hits in
`activity_url_scheme_list.xml` become `TextAppearance.App.Chip` - brand face, Latin technical token,
which is exactly what D-2 scopes Space Grotesk to.
Spec: 00-rules 3.4, D-2, D-12, 1.3. Risk: none.

### P2 - fix before the next pass

**W16.** 259 off-scale dp -> tokens. Per file: local proxy 75, provider settings 62, url schemes 37,
settings tab 36, backup 26, user assets 6, routing 6, login 5, widget 2, setting row 2, bypass item
2. Special cases: `72dp` ×17 **and** `68dp` ×13 both become `@dimen/divider_inset_start`; `18dp` ×57
becomes `@dimen/glyph_20`; `44dp`/`45dp` become `@dimen/view_height_dp48` or `@dimen/icon_button`;
`60dp` ×32 becomes `@dimen/row_min_height`; `14dp` ×72 becomes `space_12` or `space_16` by role;
`26dp` ×5 becomes `@dimen/radius_button`; the `18dp` at `item_recycler_bypass_list.xml:14` becomes
`@dimen/radius_card`.

**W17.** Unify the switch: `@style/Widget.Departament.Switch` on all 14 `MaterialSwitch`es; replace
the `SwitchCompat` at `activity_check_update.xml:28-36` with a proper A3 toggle row and retire
`@style/BrandedSwitch`.

**W18.** `activity_url_scheme_list.xml`: 9 hand-copied blocks + 6 cards -> one `RecyclerView` over a
9-item list; move the 9 `depv://` literals out of the layout.

**W19.** `activity_bypass_list.xml:124` `GridLayoutManager` -> `LinearLayoutManager`; remove
`nestedScrollingEnabled="false"` + `match_parent` from the `RecyclerView`s at
`activity_routing_setting.xml:110-115` and `activity_user_asset.xml:109-114` so they recycle.

**W20.** Grammar C: rebuild `activity_about.xml` (5 rows) and `activity_check_update.xml` (2 rows)
on `Widget.Departament.Row.Navigation` / `.Action` with neutral tiles, the 20dp external glyph on
the five rows that leave the app, and one version line at `TextAppearance.App.Caption`. This retires
`TextAppearance.AppCompat.*` from the tree - `activity_about.xml`, `activity_check_update.xml` and
`widget_switch.xml` are its last three homes.

**W21.** The 8 `"OK"` dialog buttons become verbs: `AboutActivity.kt:33`,
`ProviderSettingsActivity.kt:199`, `RoutingEditActivity.kt:149`,
`RoutingSettingActivity.kt:102,124,172`, `UserAssetActivity.kt:222`, `LoginActivity.kt:339`. 9.2.

**W22.** The dash rewrites of 9.1 and the two terminology fixes of 9.2.

**W23.** `activity_local_proxy.xml:91-165`: the 5-option memory toggle exceeds the 3-segment cap ->
A2 value row plus a picker sheet, per 12-settings 2.9.

**W24.** `activity_app_picker.xml` and `activity_logcat.xml`: give both a screen frame - header,
search or filter, empty state - instead of a bare `RecyclerView`.

### P3 - polish

**W25.** `widget_switch.xml`: `45dp` -> `@dimen/tile_size`, `padding_spacing_dp16` ->
`@dimen/space_16`, `@android:color/white` -> a theme attribute, `AppCompat.Small` -> `App.Caption`.

**W26.** `layout_setting_row.xml:17` / `layout_setting_toggle_row.xml:14`: remove
`stateListAnimator="@anim/press_scale"` - R5, rows step their background, they do not scale, and
`@drawable/bg_row` already carries the step.

**W27.** Delete the ALL-CAPS XML section comments in `layout_settings_content.xml` (`:18` and 5
more) when the file is rebuilt.

**W28.** `layout_settings_content.xml:10`: `android:visibility="gone"` on an included root that
`MainActivity` controls - dead markup.

---

## 11. What is already right, and must not be lost

1. **`SettingsSectionLabel`** (`styles.xml:262-268`): sentence case, 16sp/700, `textAllCaps=false`,
   24 above / 8 below at the gutter. Twenty-four uses across seven files: settings tab 6, url schemes 5, provider settings 4, local proxy 3, backup 2, routing 2, user assets 2. This is the anti-eyebrow rule
   implemented correctly, and it is the one part of the settings visual system that already
   conforms.
2. **`activity_base.xml`'s elevation discipline**: `android:elevation="0dp"`, `app:elevation="0dp"`,
   `stateListAnimator="@null"`, no divider. Three quarters of rule 4.8 is already there; W1 finishes
   it rather than starting it.
3. **Zero raw hex, zero `textAllCaps`, zero emoji, zero nested cards, zero side-stripes, zero
   gradients, zero `...`** across all 21 files. The baseline scan of 1.5 says keep these clean; this
   wave's files are clean and every proposed change keeps them so.
4. **The switch-is-not-focusable pattern** (`clickable="false" focusable="false"` on all 14
   switches): the row owns the tap and the tab stop, which is exactly 12-settings 3.1's A3 rule.
5. **Russian, sentence case, correct terminology** across `strings_settings_hub.xml`,
   `strings_provider.xml`, `strings_local_proxy.xml`, `strings_auth.xml`, apart from the five
   dashes and the two nouns in 9.2.
6. **The two orphaned row components already default to the neutral tile** and carry the comment
   explaining the accent budget (`layout_setting_row.xml:19-21`). They were written to the right
   spec and never wired up; W2 wires them up rather than replacing them.

---

## 12. The Departament slop test, run on this surface (00-rules.md 2.4)

| # | Question | Answer |
|---|---|---|
| 1 | Category reflex - could someone guess this palette from "VPN app"? | Partly no: the surface ramp is a genuine near-black and the accent is rationed. But the settings tree spends its colour budget on six decorative tile hues, which is where the screen starts to read as consumer-app-with-icons rather than product UI. **Fails on the tile system, passes on the ground.** |
| 2 | Second-order reflex - terminal green, or Linear-grey with a violet CTA? | No. Passes. |
| 3 | The uniform-card tell - N identical rounded rectangles with icon, title, subtitle? | **Yes, badly.** `activity_url_scheme_list.xml` is 9 identical blocks; `item_recycler_bypass_list.xml` is one card per app; the settings tab is 6 cards that are invisible against their own page. At least three of these should have been a divided list. **Fails.** |
| 4 | The decoration tell - point at every non-text pixel and say what it communicates | The six tile hues communicate nothing (same glyph, three hues, 4.7). The card boundaries communicate nothing (4.4). The info glyph at `activity_bypass_list.xml:93-98` duplicates a helper string that already exists. **Fails.** |
| 5 | The copy tell | Passes, apart from the five dashes and «Настройки провайдеров» inside Настройки. |
| 6 | The state tell - open it empty, failed, offline, with a 40-character name, at 200% | **Fails on all five.** 61 of 75 applicable state cells are absent (8.1); 8 of 10 applicable product gate states are absent (8.2); three fixed 52dp buttons, five fixed 44dp buttons and 18 `15sp` labels clip or collide at 200%. |
| 7 | The trust test - would a Raycast / Linear / Telegram user trust this? | **No.** Three row grammars on one surface is precisely the "pause at every subtly-off component" failure of 2.2. A user who learns the settings tab does not recognise About, and does not recognise the app picker at all. |

Four failures out of seven. Rework, not polish - which is what `12-settings.md` 0.3 already
prescribes, and what this work order sequences.
