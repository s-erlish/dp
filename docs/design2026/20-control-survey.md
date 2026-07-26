# 20 - Control survey

**Departament VPN - every interactive control that exists today, on both clients, counted.**

Status: **audit only.** This file changes nothing. It is the factual baseline that the Account
rework (and every screen rework after it) is measured against. No opinion is stated here that is not
backed by a count and a file path.

Authority: `docs/design2026/00-rules.md` is the law being measured against. Where this file says
"defect", it means "violates a numbered rule in 00-rules.md", and the rule is cited.

| | Android | Desktop |
|---|---|---|
| Root | `/home/user/dp` | `/home/user/v2rayN` |
| Surveyed | `V2rayNG/app/src/main/res/layout/*.xml` (73 files) + `res/values/{styles,themes,colors,dimens,motion}.xml` | `v2rayN/v2rayN.Desktop/Views/*.axaml` (50 files) + `Assets/{GlobalResources,GlobalStyles}.axaml` |
| Interactive instances found | **324** across **60** layout files | **483** across **47** view files |
| Survey date | 2026-07-26 | 2026-07-26 |

Everything below is reproducible. Appendix A lists the exact commands.

---

## 0. Executive finding, in one paragraph

There is no shared control vocabulary on either platform, and the two platforms do not share one
with each other. Android has **two reusable row layouts that are included by zero screens** while
**23 settings rows are hand-inlined**; it has **5 declared button heights producing 6 different
drawn heights**, **5 button corner radii**, **6 icon-button box sizes of which 34 of 35 instances
fail the 48dp touch minimum**, **3 chevron sizes**, and **58 completely unstyled `EditText`s and 15
unstyled `Spinner`s** that render as stock AOSP widgets in the middle of a themed app. Its buttons
are not in the brand face at all - **zero of 33 carry a `textAppearance`**, so every button label
is Roboto. Desktop has a genuinely good global style layer that **54 of its 186 buttons, 118 of its
126 text fields, 66 of its 66 combo boxes and 43 of its 57 switches never opt into** - they render
in the Semi default theme, which §12.1 calls a defect by name; **12 more buttons carry a class
(`Success`) that does not exist in this repository and pull a third-party green straight out of
Semi.** Across both token layers there are **27 distinct blue hex values**. The desktop's accent is
**not theme-dependent at all**, so in light mode accent text measures **2.98:1** on the page
background - below the 3:1 floor, let alone 4.5:1. And the two platforms disagree on the most basic
shape in the system: Android buttons are stadium-round, desktop buttons are `Radius.Button 16`, and
`00-rules.md` §3.2 says pill. Every one of those is a countable, fixable fact. None of them is a
matter of taste.

---

# PART A - ANDROID

## A.1 What the theme actually supplies

`res/values/themes.xml` sets **colour attributes and nothing else**. It defines **zero** component
default styles: there is no `materialButtonStyle`, no `materialCardViewStyle`, no
`materialSwitchStyle`, no `textInputStyle`, no `chipStyle`, no `shapeAppearanceSmallComponent` /
`MediumComponent` / `LargeComponent` override.

Consequence, and this is the root cause of half of Part A: **every Material component in the app
falls through to the stock `Widget.Material3.*` defaults** (material 1.13.0, `gradle/libs.versions.toml:11`),
and any brand shape/type/height has to be re-declared inline on every single instance. Which is
exactly what has happened.

`res/values/styles.xml` supplies the type ramp and four component-adjacent styles:

| Style | Purpose | Referenced by | Verdict |
|---|---|---|---|
| `TextAppearance.App.Display/Headline/Title/Title.Medium/Body/Subtitle/Caption/Chip/Numeric` | The 3.4 type ramp | widely | correct, matches §3.4 |
| `SettingsSectionLabel` | Section header 16sp/700, padding 16/18/16/8 | 8 refs | correct, but its `paddingTop` is **18dp** - off-scale (§1.4.5) |
| `ToolbarBrandTitle` | 20sp/700 wordmark | 2 refs (`activity_base.xml`, `activity_main.xml`) | **misapplied** - see A.7 |
| `BottomNavLabel` | 11sp/500 nav label | 1 file (4 uses) | fine |
| `BottomNavIndicator` | 64×34 `colorPrimaryContainer` pill | **0 refs** | **dead code** |
| `ThemeOverlay.Departament.Dialog` + `.Title` + `.Button` | app-wide AlertDialog skin | wired via 3 theme attrs | correct |

Also dead: `res/color/bottom_nav_item_color.xml` (**0 refs**), and drawables `bg_acc_option`,
`bg_chip_gold`, `bg_nav_header`, `bg_speed_chip` (**0 refs each**).

## A.2 Button inventory - all 34 instances

`MaterialButton` ×31, plain `Button` ×2, `FloatingActionButton` ×1. There is no `Chip` and no
`ExtendedFloatingActionButton`; there is one `MaterialButtonToggleGroup` (`toggle_memory`,
`activity_local_proxy.xml`, holding rows 8-12 below).

**Read the "drawn h" column carefully.** `Widget.Material3.Button` carries `android:insetTop` /
`insetBottom` = 6dp and `minHeight` 48dp. A button that does not zero its insets draws its
background **12dp shorter than its declared height**. Nine instances zero their insets; twenty-four
do not.

| # | File | id | style | declared h | insets | **drawn h** | corner | text style | states defined |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `activity_account.xml` | `btn_top_up` | *(none → filled)* | wrap | 0/0 | ~36 (padV 8) | `radius_pill` | `textStyle="bold"` + `textAllCaps=false` | pressed(ripple) |
| 2 | `activity_account.xml` | `btn_buy_first` | *(none → filled)* | wrap | default | **36** in 48 box | `radius_pill` → clamps to 18 | `textStyle="bold"` | pressed(ripple) |
| 3 | `activity_account.xml` | `btn_retry_load` | `Widget.Material3.Button.TonalButton` | wrap | default | **36** in 48 box | `radius_pill` → clamps to 18 | `textStyle="bold"` | pressed(ripple) |
| 4 | `activity_buy_tariff.xml` | `btn_retry` | `…TonalButton` | wrap | default | **36** | **22dp** | `textAllCaps=false` | pressed(ripple) |
| 5 | `activity_buy_tariff.xml` | `btn_dev_minus` | `…IconButton` | `tile_size` 40 | 0/0 | **40** | **20dp** | icon 20dp | pressed(ripple) |
| 6 | `activity_buy_tariff.xml` | `btn_dev_plus` | `…IconButton` | `tile_size` 40 | 0/0 | **40** | **20dp** | icon 20dp | pressed(ripple) |
| 7 | `activity_buy_tariff.xml` | `btn_pay` | *(none → filled)* | **52dp** | default | **40** | **26dp** → clamps to 20 | `textStyle="bold"` | pressed(ripple) |
| 8-12 | `activity_local_proxy.xml` | `btn_mem_40/60/80/100/150` | `?attr/materialButtonOutlinedStyle` | **44dp** | 0/0 | **44** | *(inherited)* | `textSize="13sp"` inline | pressed(ripple), **checked via ToggleGroup** |
| 13 | `activity_local_proxy.xml` | `btn_reset_creds` | `?attr/materialButtonOutlinedStyle` | wrap | default | **36** | *(inherited)* | - | pressed(ripple) |
| 14 | `activity_login.xml` | `btn_telegram` | *(none)* | **52dp** | default | **40** | **26dp** | `textStyle="bold"`, `textColor=?colorOnPrimary` | pressed(ripple), **disabled set in Kotlin** (`LoginActivity.kt:288`) |
| 15 | `activity_login.xml` | `btn_restart` | `?attr/materialButtonOutlinedStyle` | wrap | default | **36** | **26dp** | `textColor=?colorPrimary` | pressed(ripple) |
| 16 | `activity_login.xml` | `btn_site` | *(none)* | **52dp** | default | **40** | **26dp** | `textStyle="bold"` | pressed, disabled (`LoginActivity.kt:299`) |
| 17 | `activity_login.xml` | `btn_confirm_2fa` | *(none)* | **52dp** | default | **40** | **26dp** | `textStyle="bold"` | pressed, disabled (`LoginActivity.kt:300`) |
| 18 | `activity_login.xml` | `btn_register_site` | `?attr/materialButtonOutlinedStyle` | wrap | default | **36** | **26dp** | `textColor=?colorPrimary` | pressed(ripple) |
| 19 | `activity_payment_history.xml` | `btn_history_buy` | `…TonalButton` | wrap | default | **36** | **22dp** | - | pressed(ripple) |
| 20 | `activity_server_proxy_chain.xml` | `fab_add_proxy_chain_member` | FAB | wrap | - | Material default | Material default | - | Material default |
| 21 | `activity_tv_receive.xml` | `btn_regenerate` | plain `<Button>` | wrap | - | **AppCompat default, not Material** | AppCompat default | - | pressed(ripple) |
| 22 | `activity_tv_send.xml` | `btn_scan` | *(none)* | wrap | default | **36** | *(inherited)* | - | pressed(ripple) |
| 23 | `activity_tv_send.xml` | `btn_send` | *(none)* | wrap | default | **36** | *(inherited)* | - | pressed(ripple) |
| 24 | `item_device.xml` | `btn_device_delete` | `…IconButton` | **44dp** | 0/0 | **44** | *(inherited)* | icon 22dp, `iconTint=?iconTintRed` | pressed(ripple) |
| 25 | `layout_home_empty.xml` | `btn_home_add_qr` | *(none)* | wrap | default | **36** | *(inherited)* | - | pressed(ripple) + **`@anim/press_scale`** |
| 26 | `layout_home_empty.xml` | `btn_home_add_clipboard` | `…OutlinedButton.Icon` | wrap | default | **36** | *(inherited)* | - | pressed + press_scale |
| 27 | `layout_home_empty.xml` | `btn_home_buy` | *(none)* | wrap | default | **36** | *(inherited)* | `textStyle="bold"` | pressed + press_scale |
| 28 | `layout_home_empty.xml` | `btn_home_link_tg` | `…OutlinedButton.Icon` | wrap | default | **36** | *(inherited)* | - | pressed + press_scale |
| 29 | `layout_home_empty.xml` | `btn_home_login_tg` | `…TonalButton.Icon` | **52dp** | default | **40** | **26dp** | `textColor=?colorOnPrimaryContainer` | pressed + press_scale |
| 30 | `layout_home_empty.xml` | `btn_home_login_site` | `…OutlinedButton.Icon` | wrap | default | **36** | *(inherited)* | - | pressed + press_scale |
| 31 | `layout_servers_empty.xml` | `btn_import_clipboard` | *(none)* | wrap | default | **36** | *(inherited)* | - | pressed(ripple) |
| 32 | `layout_servers_empty.xml` | `btn_scan_qr` | `…OutlinedButton.Icon` | wrap | default | **36** | *(inherited)* | - | pressed(ripple) |
| 33 | `layout_subscription_meta_bar.xml` | `btn_support` | `…TonalButton.Icon` | wrap, `minHeight="0dp"` | default | **≈28-30** | *(inherited)* | `textSize="12sp"`, icon 16dp | pressed(ripple) |
| 34 | `preference_with_help_link.xml` | *(no id)* | `Widget.AppCompat.Button.Borderless` | match_parent | - | AppCompat | AppCompat | `textColor=?colorPrimary` | pressed(ripple) |

Row 33 is **under the 48dp touch minimum** (§7.2, §14.2) - it sets `minHeight="0dp"` with 12/16
horizontal padding and no vertical padding beyond the 12sp line box.

Row 21 and row 34 are **AppCompat, not Material** - a different component family entirely.

**Button facts:**
- **5** declared height values: `wrap_content` (19), `44dp` (6), `52dp` (5), `@dimen/tile_size` 40dp (2), `match_parent` (1).
- **6** effective drawn heights: **≈29, 36, 40, 44**, plus the two AppCompat defaults.
- **5** corner-radius values: inherited-stadium (19), `26dp` (7), `@dimen/radius_pill` (3), `22dp` (2), `20dp` (2).
- **0** buttons carry a `textAppearance`. **0** carry `textFontWeight`. **8** carry `textStyle="bold"` - synthetic bold, banned by §3.4 ("Never a synthetic bold") and §5.4.
- **6** buttons set `textSize` inline (`13sp` ×5, `12sp` ×1) - banned by §5.2, and `13sp` is a body size being used as a button label.
- **0** buttons define a **focus** state. **3** define a **disabled** state, all in `LoginActivity.kt`. **0** define a **loading** state in XML; `MainActivity.kt:264` is the only `isEnabled = false` outside Login.
- Only **6** buttons (all in `layout_home_empty.xml`) carry `@anim/press_scale`. The other **28** rely on the Material ripple alone, which §7.1 explicitly pairs *with* the scale.

## A.3 Icon affordances - 6 different box sizes

There is no icon-button component. Icon affordances are `ImageButton`, `MaterialButton.IconButton`,
or a bare `ImageView` with `clickable="true"`.

| Box | Count | Where | Glyph | Touch target |
|---|---|---|---|---|
| **36dp** | 8 | `layout_servers_header.xml` (`btn_collapse_all`, `btn_refresh_all`, `btn_speedtest_all`, `btn_add`), `layout_subscription_meta_bar.xml` (`btn_ping`, `btn_refresh`, `btn_pin`, `btn_telegram`) | 20dp (8dp pad) | **FAILS 48dp (§7.2)** |
| **40dp** | 3 | `layout_home_account.xml` `btn_cta_dismiss`; `activity_buy_tariff.xml` `btn_dev_minus`/`btn_dev_plus` | 20dp | **FAILS 48dp** |
| **42dp** | 10 | `activity_main.xml` `btn_home_add`; `activity_url_scheme_list.xml` ×9 | 24-26dp (8dp pad) | **FAILS 48dp** |
| **44dp** | 8 | `activity_local_proxy.xml` ×7 copy/reveal `ImageButton`s; `item_device.xml` `btn_device_delete` | 22-24dp | **FAILS 48dp** |
| **48dp** | 1 | `layout_subscription_meta_bar.xml` `btn_collapse` (13dp pad → 22dp glyph) | 22dp | **passes** |
| **wrap_content** | 5 | `activity_sub_edit.xml` ×2, `activity_routing_edit.xml` ×2 dropdown arrows, `item_recycler_proxy_chain_member.xml` ×1 | 24dp | unmeasurable, content-sized, fails |

**34 of 35 icon affordances are below the 48dp Android touch minimum.** That is a P1 accessibility
defect by §7.2 / §14.2, repeated 34 times. There is no `TouchDelegate` anywhere in `ui/`.

`btn_collapse` uses **13dp** padding - a value that exists nowhere else in the product.

`btn_cta_dismiss` (`layout_home_account.xml:78`) is a **`TextView` whose text is the character
`✕`**, sized `16sp`, coloured inline. A dingbat glyph used as UI chrome instead of a vector icon
(§10.4 "Vector only"), with an inline `textSize` (§5.2).

Accessible names: **34 icon-only controls checked, 0 missing a `contentDescription`.** That one is
clean, and it is the only §14 item Android currently passes outright.

## A.4 Rows - 99 hand-rolled clickable containers, 2 dead components

There are exactly two reusable row layouts:

| Layout | Height | Padding | Title type | Trailing | Press | Included by |
|---|---|---|---|---|---|---|
| `layout_setting_row.xml` | `minHeight=row_min_height` 56 | 16/16, `padV=space_12` | `TextAppearance.App.Body` **14sp/400** | value text + **18dp** chevron | **`@anim/press_scale`** | **0 files** |
| `layout_setting_toggle_row.xml` | 56 | 16/16, `padV=space_12` | `App.Body` **14sp/400** | `MaterialSwitch` (`clickable=false`) | **`@anim/press_scale`** | **0 files** |

Both are **dead**. Nothing includes them. Meanwhile:

| Screen | Rows | Height | Padding | Title type | Chevron | Press-scale | Divider |
|---|---|---|---|---|---|---|---|
| `layout_settings_content.xml` | **23** hand-inlined (`row_mode`, `row_per_app`, `row_bypass_lan`, `row_ipv6`, `row_dns`, `row_ping_method`, `row_local_proxy`, `row_always_on`, `row_mux`, `row_mux_concurrency`, `row_fragment`, `row_appearance`, `row_language`, `row_boot`, `row_sub_auto_update`, `row_routing`, `row_assets`, `row_provider`, `row_tv_send`, `row_tv_receive`, `row_about`, `row_backup`, `row_url_scheme`) | 56 | 16/16, padV 12 | `App.Body` 14sp/400 ×23 | 18dp | **none** | 1dp inset |
| `activity_account.xml` | 3 (`row_buy`, `row_devices`, `row_history`) | 56 | 16/16, `padV=space_8` | **`App.Title` 16sp/700** | 18dp | **none** | 1dp @ `marginStart=72dp` |
| `sheet_server_actions.xml` | 6 | 56 | 16/16, padV 12 | *(inline)* | none | **none** | - |
| `item_payment_method.xml` | 1 | 56 | 16/16, padV 12 | `App.Body` 14sp | **20dp** | **`press_scale`** | 1dp @ `marginH=16` |
| `activity_provider_settings.xml` | 9 | mixed | mixed | mixed | 18dp | none | mixed |
| `activity_backup.xml` | 4 | mixed | mixed | mixed | 18dp | none | mixed |
| `activity_about.xml` | 5 | mixed | mixed | mixed | - | none | mixed |
| `item_buy_option.xml` | 1 | **48dp** | `padH/V = space_12` | `App.Body` | - | none | - |
| `item_recycler_main.xml` | 1 `info_container` | 56 | 12/12, padV 8 | *(inline)* | - | **`press_scale`** | - |
| `item_recycler_sub_setting.xml` | 4 | **no minHeight** | - | - | - | none | - |

**Counted defects in the row layer:**
1. **Two shared row components with zero call sites**, and **23 hand-inlined copies** of the exact same structure in one file. §1.3 product ban: "Inconsistent component vocabulary across screens. If the 'save' button looks different in two places, one is wrong."
2. **Two different row-title type treatments**: Settings uses `App.Body` (14sp/400) ×23; Account uses `App.Title` (16sp/700) ×3. §4.5 specifies "Title (16sp/700)". Settings is wrong 23 times.
3. **Two different row vertical paddings**: `space_12` (settings, sheet) vs `space_8` (account).
4. **Three text origins**: `16 + 40 + 16 = 72` (settings, account), `16 + 40 + 12 = 68` (payment method, device), `12 + 40 + 12 = 64` (server row). §4.1 mandates one: **68**.
5. **Dividers start at 72dp** in `activity_account.xml` and `layout_settings_content.xml` - i.e. under the wrong text origin, and 72dp is off-scale.
6. **9 of 121 clickable surfaces have press-scale.** 112 do not.

## A.5 Text fields and pickers - the unstyled majority

| Control | Count | Style | Renders as |
|---|---|---|---|
| bare `<EditText>`, no style, no background, no `textAppearance` | **58** | none | **stock AOSP/AppCompat underlined field** |
| `<EditText>` with `@drawable/bg_lp_input` + `textSize="15sp"` | **7** | bespoke | `activity_local_proxy.xml` only |
| `<EditText>` `et_search` with `@drawable/bg_search_pill`, h=44dp, `textSize="14sp"`, `paddingStart/End=14dp` | 1 | bespoke | `layout_servers_header.xml` |
| `<EditText>` `background="@null"` | 1 | none | `item_recycler_proxy_chain_member.xml` |
| `<EditText>` `minHeight="48dp"` | 1 | none | `activity_routing_edit.xml` `et_process` |
| `TextInputLayout` + `TextInputEditText` | **4 pairs** | `Widget.Material3.TextInputLayout.OutlinedBox` | correct Material |
| `AutoCompleteTextView` | 4 | none (1 uses `TextAppearance.AppCompat.Small`) | stock |
| `<Spinner>` | **15** | none | **stock platform spinner** |
| `MaterialCheckBox` | 1 | none | `item_recycler_bypass_list.xml` |
| `RadioGroup` | 1 | none | `activity_tv_send.xml` |

**Where the 58 bare `EditText`s live**: `layout_tls.xml` (7), `activity_server_hysteria2.xml` (6),
`activity_server_wireguard.xml` (6), `layout_transport.xml` (6), `activity_routing_edit.xml` (7),
`activity_sub_edit.xml` (5), `dialog_webdav.xml` (4), `layout_address_port.xml` (3),
`layout_tls_hysteria2.xml` (2), `activity_server_socks.xml` (2), `activity_server_vless.xml` (2),
`activity_server_group.xml` (2), `activity_user_asset_url.xml` (2), and one each in
`activity_server_vmess/trojan/shadowsocks/custom_config`, `dialog_config_filter.xml`,
`activity_server_proxy_chain.xml`.

**Where the 15 `Spinner`s live**: `layout_tls.xml` (4), `layout_transport.xml` (3),
`layout_tls_hysteria2.xml` (2), `activity_server_group.xml` (2), and one each in
`activity_server_vless/vmess/shadowsocks`, `dialog_config_filter.xml`.

Defects: §11.2 says the Spinner is not an allowed component ("Choice among many → Bottom sheet list
with radio; **Not** `Spinner`"). §7.4 requires label-above, helper-text slot, blur validation,
error-below, password toggle, autofill hints, correct `inputType` - **none of that exists on the 58
bare fields**. `15sp` (×18 occurrences) is not on the type ramp; §3.4 says "15sp does not exist."

The only fields that satisfy §7.4 are the 4 `TextInputLayout` pairs in `activity_login.xml` (3) and
`dialog_top_up.xml` (1). Those 4 carry `endIconMode="password_toggle"`, `autofillHints`,
`imeOptions` and correct `inputType`. Every other field in the app has none of it.

## A.6 Switches, cards, chips

**`MaterialSwitch` ×23** - all `wrap_content`, none styled, none disabled, none with an explicit
size. They render as the stock M3 switch (52×32 track, 24dp thumb growing to 28dp with a check icon
when on). Distribution: `activity_local_proxy.xml` 7, `activity_provider_settings.xml` 6,
`layout_settings_content.xml` 5, `activity_bypass_list.xml` 2, and 1 each in
`layout_setting_toggle_row.xml` (dead), `item_recycler_routing_setting.xml`,
`item_recycler_sub_setting.xml`. In `layout_setting_toggle_row.xml` the switch is correctly
`clickable="false"` so the row owns the toggle - **that pattern is not replicated in any of the 5
live settings switches**, so those rows have two independent hit targets (§4.5).

**`MaterialCardView` ×50** - four distinct corner radii:

| Radius | Count | Note |
|---|---|---|
| `20dp` raw literal | 25 | should be `@dimen/radius_card` |
| `@dimen/radius_card` (=20) | 21 | correct |
| `16dp` | 2 | **off-token** |
| `18dp` | 1 | **off-token** |
| `88dp` | 1 | `activity_main.xml` `card_connect` - a 176dp card used as the connect button |

All 50 are `cardElevation="0dp"` (correct, §4.7). 47 carry `strokeWidth="1dp"` +
`strokeColor="?attr/colorOutlineVariant"` (correct); 3 use `cardBackgroundColor="?attr/colorSurfaceVariant"`
with no stroke (`activity_buy_tariff.xml` skeletons, `layout_height="76dp"` - off-scale).

`card_connect` is the product's primary action, and it is **a `MaterialCardView`, not a button**:
`rippleColor="@android:color/transparent"`, `strokeWidth="0dp"`, `clickable="true"`,
`contentDescription="@string/tasker_start_service"` (which is a *Tasker* string on the main connect
control). It has no disabled state and no focus state.

**Chips: there is no `Chip` component in the app.** Five `TextView`s wearing shape drawables do the
job: `tv_referral` and `tv_pending` (`bg_acc_chip`) and `tv_payment_status` (`bg_acc_chip`,
retinted in `PaymentsAdapter`), `tv_type` (`bg_type_chip`), `tv_tariff_badge` (`bg_acc_badge`).
§11.2: "Chip → `Chip` styled to `radius_chip`; **Not** a `TextView` with a rounded drawable." Five
violations. Their vertical padding is `2dp` in `item_payment.xml` - off-scale.

## A.7 Navigation, toolbar, and the hero

**Bottom navigation is not `BottomNavigationView`.** `activity_main.xml:526` is a
`LinearLayout id=bottom_nav` (`minHeight="56dp"`) holding four `LinearLayout` items
(`nav_home`, `nav_servers`, `nav_settings`, `nav_account` - the last `visibility="gone"` by
default). Each item: 24dp `ImageView` + `BottomNavLabel` 11sp `TextView` (`marginTop="3dp"` -
off-scale) + a 34×3dp `View` indicator (`marginTop="3dp"`). Selected weight is stepped to 700 at
runtime (`MainActivity.kt:333`, `updateNavSelection`).

That satisfies the owner's "no ripple glow" request (0.4.8) but it means `BottomNavIndicator`
(64×34, `colorPrimaryContainer`) and `res/color/bottom_nav_item_color.xml` are **dead**, §7.7's
`sw600dp` → `NavigationRailView` transition **does not exist**, and the nav items have their own
press recipe (see A.8).

**Toolbars.** Only two files declare one: `activity_base.xml` (every sub-page) and
`activity_main.xml`. Both set `titleTextAppearance="@style/ToolbarBrandTitle"` - the **20sp/700
brand wordmark**. §0.4.6 + §4.8 say a sub-page toolbar carries its title at
`TextAppearance.App.Title` (16sp/700) and the wordmark style is for the wordmark only. So **every
sub-page in the app** (Devices, Payment history, Buy, Backup, Local proxy, Provider settings,
Routing, Assets, About, URL schemes, TV send/receive, every server editor) renders its title in the
brand wordmark face. Height is `?attr/actionBarSize`, not the 56dp token.

## A.8 Press, focus and disabled - the state gap

Three mutually inconsistent press recipes exist:

| Recipe | Scale | In | Out | Interpolator | Applied to |
|---|---|---|---|---|---|
| `@anim/press_scale` | **0.96** | `motion_press_in` 90 | `motion_press_out` 160 | `ease_out_quart` **both ways** | **14** elements in 8 files |
| `@anim/nav_press` | **0.92** | **100** hard-coded | **120** hard-coded | **none → linear** | 4 bottom-nav items |
| *(nothing)* | - | - | - | Material ripple only | **112** clickable surfaces |

§7.1 specifies scale **0.97**, 90ms in `ease_out_quart`, 160ms out **`ease_out_quint`**. So:
`press_scale` has the wrong scale and the wrong out-curve; `nav_press` has the wrong scale, two
off-token durations, and **linear easing, which §8.3 bans by name**.

**Focus.** Exactly **one** drawable in the entire app defines `state_focused`:
`res/drawable/bg_server_row.xml` (2dp `colorPrimary` outline). Every other control - including the
TV activities `activity_tv_send.xml` / `activity_tv_receive.xml`, which are D-pad-only surfaces -
has no visible focus state. §7.1 requires it for hardware keyboard and TV.

**Disabled.** Seven `isEnabled = false` calls exist in the whole `ui/` package
(`MainActivity.kt:264`, `SettingsActivity.kt:282,298`, `LoginActivity.kt:288,299,300`,
`SubEditActivity.kt:59`). No layout declares `android:enabled="false"`, no style declares an alpha
0.38 disabled treatment, and there is no disabled colour state list anywhere.

**Loading.** No control implements the §7.1 inline loading state (label swaps for a 20dp
indicator). There is one screen-level skeleton (`activity_account.xml` `group_sub_skeleton`, alpha
pulsed to 0.7 in `AccountFragment.kt:418`) and two indeterminate indicators
(`activity_main.xml` `progress_connect`, `layout_subscription_meta_bar.xml` `progress_action`).

**Toast vs Snackbar.** 2 files use `Toast`, 1 uses `Snackbar`. §1.4.8 permits Toast only for
fire-and-forget confirmations already present.

## A.9 Colour and type discipline in Android layouts

| Check | Result |
|---|---|
| Raw hex in layouts (`§1.5`) | **0** - clean |
| `textAllCaps="true"` | **0** - clean |
| `android:textStyle="bold"` (synthetic bold, §3.4/§5.4) | **16** across 11 files |
| Inline `android:textSize` (§5.2) | **109** across 9 values: `16sp`×31, `12sp`×26, `13sp`×24, **`15sp`×18**, `14sp`×5, **`18sp`×2**, `22sp`×1, `11sp`×1, `20sp`×1 |
| Off-scale `dp` (rules' own §1.5 grep) | **325** across 46 files - **unchanged from the 2026-07-26 baseline** |
| Off-scale `dp` against the strict §3.1 scale | **627** across 34 distinct values, 37 files |
| Icon-tile colour fills in use | **7**: `bg_icon_blue`×26, `bg_icon_purple`×11, `bg_icon_green`×11, `bg_icon_orange`×8, `bg_icon_neutral`×4, `bg_icon_red`×3, `bg_icon_yellow`×2 |

On the tiles: `themes.xml:88-99` remaps `iconTintGreen/Orange/Purple/Yellow` → `@color/icon_blue`
and `iconTileBgGreen/Orange/Purple/Yellow` → `@color/icon_tile_blue`, so all seven **render** as
two colours (blue, red) plus neutral. That is a good save. But it means **56 of 65 tiles are blue**,
which is the exact opposite of §3.6: "Settings rows use the **neutral** icon tile … unless the row
is genuinely a coloured category. A screen where every row has a different coloured tile has no
category system, only noise." Only **4** tiles in the whole app are neutral.

Glyph sizes inside those tiles are also not uniform: `@dimen/tile_glyph` (22) ×40, but `18dp` ×34,
`22dp` literal ×26, `@dimen/image_size_dp24` ×16, `36dp` ×9, `24dp` ×6, `48dp` ×4, plus 32/20/56/80.
§10.3 allows exactly 22 / 24 / 20 / 16.

---

# PART B - DESKTOP

## B.1 What the global layer supplies

Unlike Android, `Assets/GlobalStyles.axaml` (1449 lines) is a real component library, and
`Assets/GlobalResources.axaml` (569 lines) is a real token file. The problem on desktop is not
absence, it is **adoption**.

Global control classes that exist:

| Class | Height | Radius | Type | default | hover | pressed | focus | disabled |
|---|---|---|---|---|---|---|---|---|
| `Button.Primary` | 48 | `Radius.Button` **16** | Grotesk 15 Bold | Accent / OnAccent | `#3D7EF0`, 150ms Standard | `#3877E0` + scale 0.97 @120 OutQuart | inner ring OnAccent @40%, r16 | Opacity 0.38 |
| `Button.Primary.Tall` | **52** | 16 | inherits | inherits | inherits | inherits | inherits | inherits |
| `Button.Tonal` | 48 | 16 | Grotesk 15 Medium | SurfaceHighest / OnSurface | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px Accent r18 | 0.38 |
| `Button.Tonal.Tall` | **52** (local override, `LoginView`/`OnboardingView`) | 16 | inherits | | | | | |
| `Button.OutlinedAccent` | 48 | 16 | Grotesk 15 Medium | transparent + 1px Accent | Hover + Accent border | Hover + 0.97 | outer 2px r18 | 0.38 |
| `Button.Destructive` | 48 | 16 | Grotesk 15 Bold | `Brush.Red` / White | `Brush.RedPressed` | RedPressed + 0.97 | outer 2px r18 | 0.38 |
| `Button.LinkAction` | **40** | `Radius.Chip` 12 | Grotesk **14** Medium | transparent / Accent | *(none defined)* | scale 0.97 | outer 2px r14 | **none** |
| `Button.Stepper` | 40×40 | **20** | - | `Tile.Blue`, glyph 20 Accent | *(none defined)* | scale **0.94** | **none** | 0.38 |
| `Button.IconButton40` | 40×40 | Pill | - | transparent, glyph 22 | `Brush.Hover` | Hover + scale **0.92** | **none** | 0.38 |
| `Button.IconButton40.Row` | 40×40 | Pill | - | glyph **20** | | | | |
| `Button.IconButton` *(legacy)* | **32×32** | **8** | - | transparent | `Brush.Hover` | `Brush.Hover`, **no scale** | **none** | **none** |
| `Button.BackNav` | 40×40 | Pill | - | glyph 22 OnSurface | Hover | Hover + 0.92 | **none** | **none** |
| `Button.NavRailItem` | **76×64** | 14 (presenter) | glyph 24 + label 11 | OnSurfaceVariant/Medium | glyph → `OnSurfaceVariantHover` | scale **0.92** @160 OutQuint | **none** | **none** |
| `ToggleButton.Segment` | `Size.SegmentChip` **44** | 12 | Grotesk 14 Medium | transparent + 1.5px OutlineVariant | Hover (unchecked only) | scale **0.96** | outer 2px r14 | **none** |
| `Border.Card` | - | `Radius.Card` 20 | - | Surface + 1px OutlineVariant, pad 16 | - | - | - | - |
| `Border.Row` | min 56 | `Radius.Tile` 12 | - | transparent | `Brush.Hover` | - | **none** | **none** |
| `Border.SettingRow` | min 56 | *(none)* | - | transparent, pad 16,12 | `Brush.Hover` | **deliberately none** | inner 2px r12 | **none** |
| `Border.ServerRow` | min 56 | `Radius.Card` 20 | - | transparent + 1.5px transparent | Hover | `.pressed` scale **0.96** | **none** | **none** |
| `Border.PriceOption` | min **48** | `Radius.Search` **14** | - | transparent + 1.5px OutlineVariant | Hover (unselected only) | - | **none** | **none** |
| `Border.ChipBadge` | - | 12 | - | AccentContainer, pad **10,4** | - | - | - | - |
| `Border.StatusChip` (+ .paid/.pending/.failed/.canceled) | - | 12 | - | status @18%, text = full colour | - | - | - | - |
| `Border.ProtocolChip` | - | 12 | - | AccentContainer, pad 8,2 | - | - | - | - |
| `Border.Tile` (+ Blue/Green/Orange/Purple/Red/Yellow) | 40×40 | 12 | - | `Tile.Neutral` | - | - | - | - |
| `Border.SearchPill` | - | 14 | - | Surface + 1px | - | - | - | - |
| `Border.TrafficPill` | 16 | `Radius.Traffic` **8** | - | SurfaceVariant | - | - | - | - |
| `Border.EmptyIcon` | 64 | 20 | - | Tile.Blue, glyph 32 | - | - | - | - |
| `Border.Toast` | - | Pill | Grotesk 14 Bold | Toast.Bg, pad **22,12**, BoxShadow | - | - | - | - |
| `Border.SheetTop` / `.SheetHandle` / `.Scrim` | - | 24,24,0,0 / Pill | - | Surface / SurfaceHighest 36×4 / 60% black | - | - | - | - |
| `Border.SkelBar` / `.SkelCard` | 16 / `Size.SkeletonCard` 76 | 12 / 20 | - | SurfaceHighest / SurfaceVariant | - | - | - | - |
| `Border.ConnectDisc` | `Size.ConnectDisc` 176 | **88** | - | SurfaceHigh | - | `.pressed` scale **0.94** @160 | - | - |
| `Border.Avatar` | consumer-set | 100 | - | Tile.Blue | - | - | - | - |
| `Ellipse.Dot` / `.active` | 6 / 8 | - | - | OutlineVariant / Accent | - | - | - | - |
| `ToggleSwitch.iOS` (ControlTheme) | 52×32 track, 26 knob | 16 | - | SurfaceHighest / Accent | *(none)* | knob squash 0.9 @90 | **none** | 0.38 |
| `TextBox.Incy` (ControlTheme) | min **52** | **14** | 15 | SurfaceVariant + 1px | border → `Brush.Outline` | - | border → Accent + outer 2px r16 | 0.38 |
| `TextBox.IncyField` (ControlTheme) | min **44** | 14 | 15 | SurfaceHigh + 1px | border → Outline | - | border → Accent | 0.38 |
| `IncyFlyoutTheme` (FlyoutPresenter) | - | 20 | - | SurfaceHigh + 1px, pad 16, BoxShadow | - | - | - | - |
| `Incy.ScrollBarThumb` | 6→8 | Pill | - | OnSurfaceVariant @0 → 0.45 | 0.8 | Accent @1.0 | - | opacity 0 |

That is **34 global classes/themes**. It is a good library. Here is who uses it.

## B.2 Adoption - the Semi-default leak, counted

| Control | Instances | Uses an Incy style | **Falls through to Semi default** |
|---|---|---|---|
| `Button` | **186** | 132 | **54 (29%)** |
| `TextBox` | **126** | 8 (`TextBox.Incy` ×5, `TextBox.IncyField` ×3) | **118 (94%)** |
| `ComboBox` | **66** | 0 | **66 (100%)** |
| `ToggleSwitch` | **57** | 14 (`ToggleSwitch.iOS` ×11, `SimpleToggleSwitch` ×3) | **43 (75%)** |
| `CheckBox` | 2 | 0 | **2** |
| `RadioButton` | 2 | 0 | **2** |
| `HyperlinkButton` | 4 | 0 | **4** |
| `ToggleButton` | 4 | 4 (`Segment`) | 0 |

§12.1, verbatim: "**No default Fluent/Semi look may leak.** Any control that has not been restyled
to the token set is a defect: it will look like a different application."

**289 controls are that defect.**

They are not scattered - they are concentrated in the un-migrated v2rayN "geek" windows:

| View | Semi `TextBox` | Semi `ComboBox` | Semi `ToggleSwitch` | Unstyled `Button` |
|---|---|---|---|---|
| `AddServerWindow.axaml` | **54** | **16** | **8** | 6 |
| `OptionSettingWindow.axaml` | 15 | **30** | **24** | 4 |
| `SubEditWindow.axaml` | 11 | 1 | 1 | 4 |
| `RoutingRuleDetailsWindow.axaml` | 5 | 3 | 1 | 3 |
| `GlobalHotkeySettingWindow.axaml` | 5 | - | - | 3 |
| `RoutingRuleSettingWindow.axaml` | 4 | 2 | - | 3 |
| `BackupAndRestoreView.axaml` | 4 | - | - | 5 |
| `AddServer2Window.axaml` | 3 | 1 | 1 | 4 |
| `FullConfigTemplateWindow.axaml` | 2 | - | 4 | 2 |
| `PingSettingsPage.axaml` | 2 | - | - | - |
| `AccountView.axaml` | **2** | - | - | **1** |
| `CheckUpdateView.axaml` | - | - | 2 | 2 |
| `ThemeSettingView.axaml` | - | 3 | - | - |
| `ClashProxiesView` / `ClashConnectionsView` / `MsgView` / `ProfilesView` / `ProfilesSelectWindow` / `QrcodeView` / `SudoPasswordInputView` / `PerAppProxyPage` / `ProviderSettingsPage` / `DnsSubView` / `CompactServersView` / `AddGroupServerWindow` / `RoutingSubView` / `AboutPage` / `BackupPage` / `UrlSchemesPage` / `GeoFilesPage` / `StatusBarView` | 1-1 each | 1-4 each | 1-3 each | 1-3 each |

Note row 11: **the Account tab itself** contains 2 Semi-default `TextBox`es (the top-up amount
field at `AccountView.axaml:372` and the link-email field at `:1223`) and 1 unstyled `Button`. The
`TextBox.Incy` theme exists and is applied in `LoginView` - the Account flyouts simply never opted
in. Those two fields are the money-entry and identity-linking fields on the tab we are rebuilding.

## B.3 Button class fragmentation - 28 combinations for 186 buttons

| Class combination | Count | Defined where |
|---|---|---|
| *(no class at all)* | **54** | - |
| `IconButton` (legacy 32×32) | 18 | GlobalStyles + **10 local re-declarations across views** |
| `LinkAction` | 17 | GlobalStyles |
| `Primary` | 15 | GlobalStyles |
| `Tonal` | 13 | GlobalStyles |
| `IconButton Success` | 12 | `IconButton` from GlobalStyles; **`Success` is defined nowhere in this repo - it resolves against Semi.Avalonia's own semantic-green class** |
| `IconButton40` | 9 | GlobalStyles |
| `Primary Tall` | 7 | GlobalStyles |
| `Tonal Tall` | 4 | **local, `LoginView`/`OnboardingView`** |
| `IconButton40 Row` | 3 | GlobalStyles |
| `BottomNavItem` | 3 | **local, `BottomNavBar.axaml`** |
| `Flat` | 3 | **local, `BuyView.axaml`** |
| `OutlinedAccent` | 3 | GlobalStyles |
| `NavRailItem` | 3 | GlobalStyles |
| `IconButton40 Row Accent MetaIcon` | 3 | **local, `SubscriptionMetaView.axaml`** |
| `IconButton BackNav` | 2 | mixed |
| `BackNav` | 2 | GlobalStyles |
| `Stepper` | 2 | GlobalStyles |
| `SegItem` | 2 | **local, `LoginView.axaml`** |
| `WinBtn` | 2 | **local, `MainWindow.axaml`** |
| `WinBtn close` | 1 | **local, `MainWindow.axaml`** |
| `MethodChip` | 1 | **local, `AccountView.axaml`** |
| `MeterRow` | 1 | **local, `AccountView.axaml`** |
| `IconButton40 Accent` | 1 | GlobalStyles |
| `Destructive` | 1 | GlobalStyles |
| `RailToggle` | 1 | **local, `MainWindow.axaml`** |
| `IconButton40 MetaIcon` | 1 | **local, `SubscriptionMetaView.axaml`** |
| `IconButton40 Row MetaIcon` | 1 | **local** |
| `IconButton40 Row MetaIcon MetaDanger` | 1 | **local** |

**11 bespoke, view-local button classes** exist, each defined in exactly the view that uses it:

| Class | Defined in | Instances |
|---|---|---|
| `BottomNavItem` | `BottomNavBar.axaml:32` | 3 |
| `Flat` | `BuyView.axaml` | 3 |
| `SegItem` | `LoginView.axaml` | 2 |
| `WinBtn` / `WinBtn.close` | `MainWindow.axaml` | 3 |
| `RailToggle` | `MainWindow.axaml` | 1 |
| `MethodChip` | `AccountView.axaml:169` | 1 |
| `MeterRow` | `AccountView.axaml:212` | 1 |
| `MetaIcon` / `MetaDanger` | `SubscriptionMetaView.axaml` | 6 |
| `Tonal.Tall` | **`LoginView.axaml` and `OnboardingView.axaml` - declared twice, identically** | 4 |

Plus a **twelfth class that does not exist in this repository at all**: `Success`, applied 12 times
(`ClashConnectionsView.axaml:33,43`, `ClashProxiesView.axaml:56,…`, `MsgView`, `ProfilesView` ×5,
`ProfilesSelectWindow`). There is no `Button.Success` in `GlobalStyles.axaml` or in any view, so it
resolves against **Semi.Avalonia's own semantic-green class** (`v2rayN.Desktop.csproj:29`,
`App.axaml:20`). Twelve buttons in the product are painted a third-party green that is not in the
token set. §1.4.1: "No second accent hue."

**`Button.IconButton` is re-declared 10 times across views**, each time with the same two rules
(base + `:pressed`), duplicating `GlobalStyles.axaml:226-245`.

**190 view-local `<Style Selector=…>` rules** exist across 24 view files, on top of the 1449-line
global sheet. `MainWindow.axaml` alone declares **32**; `AccountView.axaml` **26**; `BuyView.axaml`
**20**; `LoginView.axaml` **18**; `BottomNavBar.axaml` **14**; `ConnectHeroView.axaml` **12**;
`SettingsView.axaml` **10**.

Explicit per-instance size overrides on buttons that already carry a sized class: **Height=40**
×14, **Height=32** ×3, **Height=34** ×1. The three `Height="32"` cases are all in
`AccountView.axaml` (the flyout back-buttons at lines 619, 704, 759) and they shrink
`IconButton40` below its own token to exactly the §7.2 desktop minimum.

## B.4 Two navigations, one product

The desktop ships **two complete, independently implemented navigation components**:

| | `Button.NavRailItem` | `Button.BottomNavItem` |
|---|---|---|
| Defined in | `GlobalStyles.axaml:749-828` | `BottomNavBar.axaml:32-124` (local) |
| Used by | `MainWindow.axaml` (3 items) | `BottomNavBar.axaml` (3 items) |
| Box | 76×64 | full-width, `MinHeight 56`, padding `0,8,0,6` |
| Glyph | 24 | 24 |
| Label | 11 Medium → Bold when active | `NavLabel` class, local |
| Selected indicator | one shared sliding `Border` (`railIndicator`, moved in `MainWindow.axaml.cs`) | per-item **34×3 pill** |
| Active class | `.active` | `.sel` |
| Press | scale 0.92 @160 OutQuint | scale 0.92 @160 OutQuint |
| Hover | glyph → `OnSurfaceVariantHover` | glyph → `OnSurfaceVariantHover` |
| Focus | **none** | **none** |

Two names for the same state (`.active` vs `.sel`), two indicator mechanisms, two style blocks.
This is the single clearest "should be one shared component" on the desktop.

Both carry **3 destinations**. Android carries **4** (`nav_home`, `nav_servers`, `nav_settings`,
`nav_account`). §13 requires "the destination set and its order" to be identical. It is not.

## B.5 Glyph and chevron sizes

Explicit `PathIcon` `Width` values across `Views/*.axaml`:

| Width | Count | Files |
|---|---|---|
| *(inherits a class)* | 94 | 22 |
| **22** | 29 | 14 |
| **20** | 19 | 6 |
| **18** | 8 | 4 |
| **30** | 6 | 3 |
| **16** | 6 | 3 |
| **26** | 2 | 1 |
| **32** | 2 | 1 |
| **28** | 1 | 1 |
| **15** | 1 | 1 |
| **24** | 1 | 1 |

**10 distinct explicit glyph sizes.** §10.3 allows four: 22 (in a tile), 24 (toolbar/nav), 20
(inline chevron/status), 16 (inside a chip). `30`, `26`, `28`, `15`, `18`, `32` are all off-token
(`32` is legitimate only inside `Border.EmptyIcon`).

Chevrons specifically: `PathIcon.Chevron` in `SettingsView.axaml:159` is **18**; `AccountView.axaml`
uses **18** for the Buy/History row chevrons (lines 1310, 1359) and **16** for the `MeterRow`
chevron (line 873). Three chevron sizes on desktop, matching Android's three (18/20/22) but not
matching *which* three.

`AccountView.axaml` also re-declares glyph geometry the global file already has:
`Geo.Acc.Chevron` (line 42) is byte-identical to `Geo.ChevronRight` in `GlobalResources.axaml:320`
(`M8.6,4.6L7.2,6l6,6l-6,6l1.4,1.4L16,12z`). **16 `StreamGeometry` keys are declared locally in
`AccountView.axaml`**, of which at least one duplicates the global set outright.

## B.6 Desktop mechanical checks

| Check (§1.5, §9.7) | Result |
|---|---|
| Inline hex in `Views/` | **3** - `DevicesView.axaml:451` `#80000000`, `MainWindow.axaml:308` `#B3000000`, `ConnectHeroView.axaml:526` `#000000`. Matches the documented baseline; all three are scrims that should be `Brush.Scrim`. |
| `StaticResource Brush.*` (breaks live theme switching) | **0** - clean |
| Off-scale `Margin`/`Padding`/`Spacing` | **97** occurrences, **14 distinct off-scale values**: `6`×25 (12 files), `10`×23 (12 files), `2`×11, `14`×9, `1`×7, `20`×4, `3`×4, `72`×4, `68`×3, `40`×2, `18`×2, `28`, `7`, `1.5` |
| em/en dash in `Common/L.*.cs` | **44** - matches the documented baseline, still unresolved |
| Icon-only buttons without `ToolTip.Tip` or `AutomationProperties.Name` | **18 of 65** - `MainWindow` 6, `AddServerWindow` 3, `BottomNavBar` 3, **`AccountView` 2**, `BackupAndRestoreView` 1, `OptionSettingWindow` 1, `SubEditWindow` 1, `ThemeSettingView` 1. §10.7/§14.3: P1. |

---

# PART C - THE INCONSISTENCY LEDGER

Counted, not characterised.

## C.1 Buttons

1. **6 distinct drawn button heights on Android** (≈29, 36, 40, 44, plus 2 AppCompat defaults) from **5 declared values**. Desktop has **5** (32, 40, 44, 48, 52). Product total: **11 distinct button heights.** §3.3 defines two (`Size.IconButton` 40, `Size.CtaTall` 52) plus the 48 touch floor.
2. **5 button corner radii on Android** (stadium-inherited, `26dp`, `radius_pill` 100, `22dp`, `20dp`) and **5 on desktop** (`Radius.Button` 16, `Radius.Pill` for `IconButton40`/`BackNav`, `Radius.Chip` 12 for `LinkAction`, `20` for `Stepper`, `8` for legacy `IconButton`). Product total: **10 distinct button radii.** §3.2 defines one for buttons: pill.
3. **Android's five 52dp CTAs draw at 40dp** because none of them zeroes `insetTop`/`insetBottom` (`btn_pay`, `btn_telegram`, `btn_site`, `btn_confirm_2fa`, `btn_home_login_tg`). Their `cornerRadius="26dp"` then clamps to 20 (half of 40) and produces a stadium. That is why the desktop port note in `GlobalStyles.axaml:3-14` describes them as capsules the owner rejected: **the shipped Android button is not the button the layout claims it is.**
4. **0 Android buttons carry a `textAppearance`.** 8 carry `textStyle="bold"`. 6 set `textSize` inline. Desktop buttons are typed by class (Grotesk 15 Bold / 15 Medium / 14 Medium = **3 button type sizes**, none of which is on the §3.4 ramp, which has no 15sp step).
5. **54 desktop buttons have no class at all.**
6. **11 bespoke desktop button classes** that should be variants of two shared ones, one of them (`Tonal.Tall`) declared twice in two different files.
7. **`Button.IconButton` is re-declared 10 times** in view-local styles with identical rules.
8. **12 desktop buttons carry `Classes="IconButton Success"`, and `Success` is not defined in this repository** - it resolves against Semi.Avalonia's semantic green. An off-brand accent hue, shipped, 12 times.
9. **190 view-local `<Style Selector>` rules** on top of a 1449-line global sheet.

## C.2 Icon buttons and glyphs

10. **6 icon-button box sizes on Android** (36, 40, 42, 44, 48, wrap) across **35** instances; **34 of 35 fail the 48dp touch minimum.**
11. **2 icon-button sizes on desktop** (legacy 32, Incy 40) plus 3 instance-level shrinks to 32, plus 14 shrinks to 40 on classes that are already 40.
12. **3 chevron sizes on Android** (18 ×32, 20 ×1, 22 ×1) and **3 on desktop** (16, 18, 22). The two platforms' sets do not match.
13. **10 distinct explicit glyph sizes on desktop**, **11 on Android**. §10.3 allows 4.
14. **`AccountView.axaml` locally re-declares 16 `StreamGeometry` glyphs**, at least one identical to a global one.

## C.3 Rows

15. **2 reusable Android row layouts with 0 call sites** while **23 rows are hand-inlined** in `layout_settings_content.xml`.
16. **3 row-title type treatments product-wide**: Android settings `App.Body` 14/400 (×23), Android account `App.Title` 16/700 (×3), desktop settings `Classes="Body"` 14 (×22 rows) vs desktop account `Classes="Title"` overridden to Medium (`AccountView.axaml:84-89`).
17. **3 text origins on Android** (64, 68, 72). §4.1 defines one: 68.
18. **Dividers inset to 72dp** in `activity_account.xml` and `layout_settings_content.xml`, and to **72** on desktop (`AccountView.axaml:1124,1172,1240,1319`) - consistent with each other, inconsistent with the 68 the rule defines.
19. **5 row press behaviours**: Android `press_scale` 0.96 (9 rows), Android none (112 surfaces), desktop `Border.Row` scale 0.99 (`AccountView.axaml:101`), desktop `Border.SettingRow` deliberately none (`GlobalStyles.axaml:650-655`, with a written rationale), desktop `Border.ServerRow` scale 0.96.

## C.4 Colour

20. **27 distinct blue-family hex values** across the four token files. Excluding the 3 that are actually desaturated greys and the 6 alpha steps of `#4C8DFF`, that is **18 distinct solid blues**: `#4C8DFF`, `#1E5FC7`, `#17469A`, `#3B6FD0`, `#3B82F6`, `#7FA8FF`, `#5F9AFF`, `#3D7EF0`, `#3877E0`, `#AEC7FF`, `#D8E4FF`, `#DCE6FF`, `#CFE0FF`, `#17325C`, `#001A43`, `#0A1F45`, `#14468F`, `#1B2D50`. §3.5 defines **two** (`#4C8DFF` dark, `#1E5FC7` light) plus a container pair.
21. **The desktop accent is not theme-aware.** `Color.Accent` / `Brush.Accent` / `Brush.OnAccent` / all `Brush.Tile.*` / `Brush.SelectedFill` / `Brush.StatusChip.*` are declared **outside** `ResourceDictionary.ThemeDictionaries` (`GlobalResources.axaml:39-51, 226-258`). The `Light` dictionary never overrides them. Measured consequence in light theme:

    | Pair | Ratio | Verdict |
    |---|---|---|
    | accent `#4C8DFF` on light bg `#F4F7FC` | **2.98:1** | **fails even the 3:1 UI floor** |
    | accent `#4C8DFF` on light surface `#FFFFFF` | **3.20:1** | **fails the 4.5:1 body floor** |
    | (rule's light accent `#1E5FC7` on `#FFFFFF`) | 5.97:1 | what it should be |

    Every `Button.LinkAction` (17 instances), the `BuyRow` title (`AccountView.axaml:1305`), every
    `Segment` checked label, and every focus ring on desktop is drawn at 2.98-3.20:1 in light
    theme. §6.8 + §14.1: **P1, and it is systemic, not per-screen.**
22. **56 of 65 Android icon tiles are blue.** §3.6 says settings rows are neutral by default. Only **4** are.
23. **7 icon-tile fill drawables on Android** and **7 `Border.Tile` variants on desktop**, both collapsing to 2-3 real colours at runtime. Dead colour surface either way.
24. **No `Chip` component on Android** - 5 `TextView`s with shape drawables instead.

## C.5 Fields

25. **58 completely unstyled `EditText`s and 15 unstyled `Spinner`s on Android.** Only 4 fields in the app use `TextInputLayout`.
26. **118 Semi-default `TextBox`es and 66 Semi-default `ComboBox`es on desktop.**
27. **3 field heights on desktop** (`TextBox.Incy` 52, `TextBox.IncyField` 44, Semi default ≈32) and **3 on Android** (bare wrap, `bg_lp_input` wrap with 12dp padding, `bg_search_pill` 44dp).
28. **`15sp` used 18 times on Android** and as the desktop button/field size, on a ramp that has no 15 step (§3.4: "15sp does not exist").
29. **§7.4 (label above, helper slot, blur validation, error below, autofill, password toggle) is satisfied by 4 fields on Android and 8 on desktop, out of 199 fields product-wide.**

## C.6 Switches

30. **3 switch appearances on desktop**: `ToggleSwitch.iOS` (11), `SimpleToggleSwitch` (3), Semi default (43).
31. **Android switches are stock M3** (52×32 track, thumb 24→28 **with a check icon when on**); desktop `ToggleSwitch.iOS` is 52×32 with a fixed 26 knob and **no icon**. Same size, visibly different thumb. §13: same defaults, same look.
32. **Only the dead `layout_setting_toggle_row.xml` sets `clickable="false"` on its switch.** The 5 live switch rows in `layout_settings_content.xml` do not, so each has two independent hit targets.

## C.7 States - the biggest gap

33. **Disabled**: Android has **0** declarative disabled treatments and **7** imperative `isEnabled = false` calls in the entire `ui/` package (`MainActivity.kt:264`, `SettingsActivity.kt:282,298`, `LoginActivity.kt:288,299,300`, `SubEditActivity.kt:59`). Desktop defines `:disabled` on **9 of its 19** interactive classes (`Primary`, `Tonal`, `OutlinedAccent`, `Destructive`, `Stepper`, `IconButton40`, `ToggleSwitch.iOS`, `TextBox.Incy`, `TextBox.IncyField`) and **omits it on 10**: `LinkAction`, `IconButton` (legacy), `BackNav`, `NavRailItem`, `BottomNavItem`, `ToggleButton.Segment`, `Border.Row`, `Border.SettingRow`, `Border.ServerRow`, `Border.PriceOption`.
34. **Focus**: Android has **exactly one** `state_focused` drawable in the whole app (`res/drawable/bg_server_row.xml`) - including on the two D-pad-only TV activities `activity_tv_send.xml` / `activity_tv_receive.xml`. Desktop defines `FocusAdorner` on **8 classes across 6 rules** (`Primary`, `Tonal`, `OutlinedAccent`, `Destructive`, `LinkAction`, `TextBox`, `Border.AccountChip`, `ToggleButton.Segment`, plus `Border.SettingRow` inline) and **omits it on 16**: `IconButton40`, `IconButton`, `BackNav`, `Stepper`, `NavRailItem`, `BottomNavItem`, `Border.Row`, `Border.ServerRow`, `Border.PriceOption`, `ToggleSwitch.iOS`, and the 6 bespoke classes (`MethodChip`, `MeterRow`, `Flat`, `SegItem`, `WinBtn`, `RailToggle`). That is every icon button, both navigations, and every clickable row that is not a settings row. §12.2 calls keyboard focus **mandatory**.
35. **Hover** (desktop only): `Button.LinkAction` and `Button.Stepper` define **no** `:pointerover` at all, so 17 link buttons and 2 steppers give zero pointer feedback.
36. **Pressed**: 112 of 121 Android clickable surfaces have ripple only, no scale. `Button.IconButton` (legacy, 18 desktop instances) has hover but **no press scale**.
37. **Loading**: **0** controls on either platform implement the §7.1 inline loading contract (fixed size, label→20dp indicator, disabled). Desktop `AccountView.axaml:900` comes closest (an `Ellipse.Spinner` swapped in for the Renew label) - that is 1 of 483.
38. **3 press-motion recipes on Android** with **2 off-token durations and 1 linear interpolator** (`nav_press`), and **6 press scales product-wide**: 0.99, 0.97, 0.96, 0.94, 0.92, and none. §7.1 defines one: 0.97.

---

# PART D - CROSS-PLATFORM CONTRADICTIONS

These are not per-platform bugs. Somebody has to decide, and §18 says only the owner can.

| # | Subject | Android today | Desktop today | `00-rules.md` says | Severity |
|---|---|---|---|---|---|
| D1 | **Button shape** | stadium (26dp on a 40dp-drawn button) | `Radius.Button` **16**, with a 12-line comment stating the owner **rejected** capsules | §3.2: "buttons are pill" | **P1 - the rule and the shipped desktop contradict each other; the owner's recorded rejection outranks §3.2 per §0.1.1, so §3.2 is the thing that must change** |
| D2 | **Accent in light theme** | `#1E5FC7` (5.97:1) | `#4C8DFF` (2.98:1) - not theme-aware at all | §3.5 light accent = `#1E5FC7` | **P1 accessibility** |
| D3 | **Destination set** | 4 (Главная, Серверы, Настройки, Аккаунт) | 3 (Главная, Настройки, Аккаунт) - no Servers tab, by design in `BottomNavBar.axaml` comments | §13: identical set and order | P1 parity |
| D4 | **Row title type** | Settings 14/400, Account 16/700 | Settings 14, Account 16 Medium | §4.5: Title 16/700 | P2, but it is the most-repeated element in the product |
| D5 | **Primary CTA height** | declared 52, drawn 40 | `Primary` 48, `Primary.Tall` 52 | §3.3 `Size.CtaTall` 52; §11.2 "52dp tall" | P1 |
| D6 | **Button label type** | inherits `textAppearanceLabelLarge` (Roboto 14 Medium) - **not Space Grotesk** | Grotesk 15 Bold / Medium | §3.4 has no 15 step; §5.1 puts Grotesk on Title/Chip/Numeric, system face on body | P1 - Android buttons are not in the brand face at all |
| D7 | **Switch thumb** | M3, 24→28dp, check icon when on | iOS-style, fixed 26dp, no icon | §13: same look | P2 |
| D8 | **Field height** | 3 informal heights | `Incy` 52 / `IncyField` 44 | not specified - **the rules have no input-field size token** | P2, and a **gap in `00-rules.md`** |
| D9 | **Icon button** | 6 box sizes, 33/34 under 48dp | 40 (+ legacy 32) | §7.2 Android 48, desktop 40 in rows/toolbars | P1 Android |
| D10 | **Press scale** | 0.96 / 0.92 / none | 0.99 / 0.97 / 0.96 / 0.94 / 0.92 | §7.1: 0.97 | P2 |
| D11 | **Toolbar title** | `ToolbarBrandTitle` 20sp wordmark on **every** sub-page | `Border.SubToolbar` 56 + Headline | §4.8: `TextAppearance.App.Title` 16sp/700 | P1 Android |
| D12 | **Chip component** | none - `TextView` + drawable ×5 | `Border.ChipBadge` / `.StatusChip` / `.ProtocolChip` | §11.2 Android must use `Chip` | P2 |
| D13 | **Chip padding** | `space_8`/`space_4`, and `2dp` in `item_payment.xml` | `10,4` (`ChipBadge`) vs `8,2` (`StatusChip`, `ProtocolChip`) | §3.1 - `10` and `2` are off-scale | P2 |

---

# PART E - THE CONSOLIDATION TARGET

What the 807 surveyed instances collapse into if one component set is built. This is the shopping
list for the rework, not a spec - the specs are separate files.

| Shared component | Replaces on Android | Replaces on Desktop | Instances consolidated |
|---|---|---|---|
| **Button, 3 variants** (filled / tonal / text) × 2 heights (48, 52) | 33 buttons, 5 heights, 5 radii, 8 `textStyle="bold"`, 6 inline `textSize` | 186 buttons, 28 class combinations, 12 bespoke classes, 54 classless | **219** |
| **Icon button, one 48dp Android / 40px desktop box** | 35 icon affordances across 6 box sizes | 65 icon buttons across 2 sizes + 3 shrinks + 14 redundant overrides | **100** |
| **Row** (title / subtitle / trailing: chevron \| switch \| value \| icon button) | 99 hand-rolled clickable containers + 2 dead layouts | 36 clickable `Border`s + `SettingRow` + `Row` + `ServerRow` + `MeterRow` | **137** |
| **Field** (label above, helper slot, error below, one height) | 58 bare `EditText` + 7 `bg_lp_input` + 1 search + 4 `TextInputLayout` | 126 `TextBox` across 3 themes | **196** |
| **Select** (bottom sheet on Android, flyout on desktop) | 15 `Spinner` + 4 `AutoCompleteTextView` | 66 `ComboBox` | **85** |
| **Switch row** (row owns the toggle) | 23 `MaterialSwitch`, 5 of them double-target | 57 `ToggleSwitch` across 3 appearances | **80** |
| **Chip** (badge / status / protocol) | 5 `TextView`+drawable | 3 `Border` classes | **8** |
| **Card** | 50 `MaterialCardView`, 4 radii, 25 raw `20dp` | `Border.Card` (already single) | **50** |
| **Nav item** (bottom bar Android, rail + compact bar desktop) | 4 hand-rolled `LinearLayout` + 2 dead styles + 1 dead colour list | `NavRailItem` + `BottomNavItem` (2 implementations) | **10** |
| **Segmented control** | 1 `MaterialButtonToggleGroup` (5 outlined buttons) | 4 `ToggleButton.Segment` + 2 `Button.SegItem` | **12** |

Plus the token work that has to land first:
- Android `themes.xml` must set `materialButtonStyle`, `materialCardViewStyle`, `materialSwitchStyle`, `textInputStyle`, `chipStyle` and the three `shapeAppearance*Component` attrs, so instance-level shape/height/type declarations stop being necessary.
- Desktop `GlobalResources.axaml` must move `Color.Accent`, `Brush.Accent`, `Brush.OnAccent`, `Brush.Tile.*`, `Brush.SelectedFill`, `Brush.StatusChip.*` **inside** `ResourceDictionary.ThemeDictionaries`.
- `00-rules.md` needs a field-height token (D8) and an owner ruling on D1.

---

# PART F - PER-SCREEN INDEX

Where every instance lives, for whoever picks up a screen. Android first.

## F.1 Android, by file

| File | Controls |
|---|---|
| `activity_account.xml` | 4 `MaterialCardView`, 3 `MaterialButton`, 4 clickable `LinearLayout`, 1 clickable `FrameLayout` (avatar 52dp) |
| `activity_about.xml` | 5 clickable `LinearLayout` |
| `activity_backup.xml` | 4 clickable `LinearLayout`, 2 `MaterialCardView` |
| `activity_buy_tariff.xml` | 4 `MaterialButton`, 4 `MaterialCardView` |
| `activity_bypass_list.xml` | 2 `MaterialSwitch`, 1 clickable `LinearLayout`, 1 `MaterialCardView` |
| `activity_check_update.xml` | 2 clickable `LinearLayout` |
| `activity_local_proxy.xml` | 7 `MaterialSwitch`, 7 `EditText`, 7 `ImageButton`, 6 `MaterialButton`, 1 `ToggleGroup`, 7 clickable `LinearLayout`, 3 `MaterialCardView` |
| `activity_login.xml` | 5 `MaterialButton`, 3 `TextInputLayout`+`TextInputEditText`, 2 `MaterialCardView` |
| `activity_main.xml` | 4 nav `LinearLayout`, 1 `ImageButton` (42dp), 2 `MaterialCardView` (`card_connect` 176/88, `card_memory`) |
| `activity_payment_history.xml` | 1 `MaterialButton` |
| `activity_provider_settings.xml` | 9 clickable `LinearLayout`, 6 `MaterialSwitch`, 4 `MaterialCardView` |
| `activity_routing_edit.xml` | 7 `EditText`, 2 `ImageButton`, 1 `AutoCompleteTextView` |
| `activity_routing_setting.xml` | 1 clickable `LinearLayout`, 1 `MaterialCardView` |
| `activity_server_*` (9 files) | 21 `EditText`, 5 `Spinner`, 1 FAB, 4 `MaterialCardView` |
| `activity_sub_edit.xml` | 5 `EditText`, 2 `AutoCompleteTextView`, 2 `ImageButton` |
| `activity_tv_receive.xml` | 1 plain `Button` |
| `activity_tv_send.xml` | 2 `MaterialButton`, 1 `RadioGroup`, 2 `MaterialCardView` |
| `activity_url_scheme_list.xml` | 9 `ImageButton` (42dp), 6 `MaterialCardView` |
| `activity_user_asset*.xml` | 2 `EditText`, 1 clickable `LinearLayout`, 1 `MaterialCardView` |
| `dialog_config_filter.xml` / `dialog_top_up.xml` / `dialog_webdav.xml` | 5 `EditText`, 1 `Spinner`, 1 `TextInputLayout` |
| `item_buy_option.xml` | 1 clickable `LinearLayout` (48dp, `bg_buy_option` / `bg_buy_option_selected`) |
| `item_buy_tariff.xml` | 1 `MaterialCardView` (press_scale) + 1 clickable header |
| `item_device.xml` | 1 `MaterialCardView` (press_scale), 1 `MaterialButton.IconButton` 44dp |
| `item_payment.xml` | 1 `MaterialCardView`, 1 chip-`TextView` |
| `item_payment_method.xml` | 1 clickable `LinearLayout` (press_scale, 20dp chevron) |
| `item_recycler_bypass_list.xml` | 1 `MaterialCheckBox`, 1 clickable `LinearLayout`, 1 `MaterialCardView` |
| `item_recycler_main.xml` | 1 clickable `LinearLayout` (`bg_server_row`, press_scale) |
| `item_recycler_proxy_chain_member.xml` | 1 `AutoCompleteTextView`, 1 clickable `ImageView`, 2 clickable `LinearLayout` |
| `item_recycler_routing_setting.xml` | 2 clickable `LinearLayout`, 1 `MaterialSwitch`, 1 `MaterialCardView` |
| `item_recycler_sub_setting.xml` | 4 clickable `LinearLayout` (no `minHeight`), 1 `MaterialSwitch`, 1 `MaterialCardView` |
| `item_recycler_user_asset.xml` | 2 clickable `LinearLayout`, 1 `MaterialCardView` |
| `item_section_header.xml` | 1 clickable `LinearLayout`, 22dp disclosure chevron |
| `item_subscription_card.xml` | 1 `MaterialCardView` + tariff badge `TextView` |
| `layout_home_account.xml` | 2 clickable `LinearLayout` (press_scale), 1 clickable `TextView` (`✕`, 40dp) |
| `layout_home_empty.xml` | 6 `MaterialButton` (all press_scale), 1 `MaterialCardView` |
| `layout_servers_empty.xml` | 2 `MaterialButton`, 1 `MaterialCardView` |
| `layout_servers_header.xml` | 4 clickable `ImageView` (36dp), 1 `EditText` (`bg_search_pill`, 44dp) |
| `layout_setting_row.xml` | **dead** - 1 clickable `LinearLayout` |
| `layout_setting_toggle_row.xml` | **dead** - 1 `MaterialSwitch` |
| `layout_settings_content.xml` | **23** clickable `LinearLayout`, 5 `MaterialSwitch`, 6 `MaterialCardView`, 6 `SettingsSectionLabel` |
| `layout_subscription_meta_bar.xml` | 5 clickable `ImageView` (4×36dp, 1×48dp), 1 `MaterialButton` (≈29dp) |
| `layout_tls*.xml` / `layout_transport.xml` / `layout_address_port.xml` | 18 `EditText`, 9 `Spinner` |
| `preference_with_help_link.xml` | 1 `Widget.AppCompat.Button.Borderless` |
| `sheet_server_actions.xml` | 6 clickable `LinearLayout` (56dp, no press-scale) |

## F.2 Desktop, by file

Instance counts per view (interactive controls only):

**Incy-migrated** - uses the global class set; this is the redesign surface:

| View | Instances | View | Instances |
|---|---|---|---|
| `AccountView.axaml` | **40** | `ConnectHeroView.axaml` | 4 |
| `SettingsView.axaml` | **36** | `OnboardingView.axaml` | 4 |
| `LoginView.axaml` | **24** | `CompactServersView.axaml` | 4 |
| `BuyView.axaml` | 11 | `StatusBarView.axaml` | 4 |
| `MainWindow.axaml` | 7 | `BottomNavBar.axaml` | 3 |
| `SubscriptionMetaView.axaml` | 7 | `PaymentHistoryView.axaml` | 3 |
| `DevicesView.axaml` | 7 | `AccountSyncView.axaml` | 2 |
| `MessageBoxDialog.axaml` | 2 | `HomeView` / `CompactHomeView` / `HomeAccountChip` / `ServerListView` | 1 each |

**Un-migrated legacy** - Semi default look, 289 of the 483 controls:

| View | Instances | View | Instances |
|---|---|---|---|
| `AddServerWindow.axaml` | **87** | `ClashProxiesView.axaml` | 5 |
| `OptionSettingWindow.axaml` | **74** | `MsgView.axaml` | 5 |
| `SubEditWindow.axaml` | 18 | `PingSettingsPage.axaml` | 5 |
| `RoutingRuleDetailsWindow.axaml` | 14 | `ProviderSettingsPage.axaml` | 5 |
| `BackupAndRestoreView.axaml` | 10 | `AboutPage` / `CheckUpdateView` / `ClashConnectionsView` / `ProfilesSelectWindow` / `RoutingSubView` / `ThemeSettingView` / `UrlSchemesPage` | 4 each |
| `FullConfigTemplateWindow.axaml` | 10 | `BackupPage` / `DnsSubView` / `SudoPasswordInputView` | 3 each |
| `RoutingRuleSettingWindow.axaml` | 10 | `GeoFilesPage.axaml` | 2 |
| `AddServer2Window.axaml` | 9 | `QrcodeView.axaml` | 1 |
| `GlobalHotkeySettingWindow.axaml` | 8 | | |
| `PerAppProxyPage.axaml` | 8 | | |
| `AddGroupServerWindow.axaml` | 7 | | |
| `ProfilesView.axaml` | 6 | | |

`AddServerWindow.axaml` and `OptionSettingWindow.axaml` alone hold **161 controls - a third of the
desktop client** - and not one of them uses an Incy style.

---

# APPENDIX A - reproduction

Every number in this file comes from one of these. Run from the stated directory.

```bash
# ---------- Android ----------
cd /home/user/dp/V2rayNG/app/src/main/res

# control census (the 324)
grep -rc 'MaterialButton\|ImageButton\|MaterialSwitch\|TextInputLayout\|EditText\|Spinner\|MaterialCardView' layout/

# off-scale dp, the rules' own §1.5 allow-list  -> 325
grep -rnoE '"(-?[0-9]+)dp"' layout/ menu/ \
  | grep -vE '"(0|1|2|4|8|12|16|20|22|24|28|32|36|40|44|48|52|56|64|72|80|100|120|152|160|176|212|230)dp"' | wc -l

# synthetic bold  -> 16
grep -rn 'android:textStyle="bold"' layout/ | wc -l

# inline text sizes  -> 109 over 9 values
grep -rhoE 'android:textSize="[0-9]+sp"' layout/ | sort | uniq -c | sort -rn

# chevron sizes  -> 18dp x32, 20dp x1
grep -rn -B3 'ic_chevron_right' layout/ | grep layout_width | sort | uniq -c

# icon tile fills  -> 7 drawables, 65 uses
grep -rho 'android:background="@drawable/bg_icon_\w*"' layout/ | sort | uniq -c | sort -rn

# dead shared rows  -> no output = zero call sites
grep -rn 'layout_setting_row\|layout_setting_toggle_row' --include=*.xml --include=*.kt \
  /home/user/dp/V2rayNG/app/src/main

# press feedback coverage  -> 14 press_scale, 4 nav_press
grep -rc 'press_scale' layout/*.xml | grep -v ':0'
grep -rc 'nav_press'   layout/*.xml | grep -v ':0'

# focus states  -> 1 file
grep -rln 'state_focused' drawable/ color/

# disabled states  -> 7
grep -rn 'isEnabled = false' /home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/

# ---------- Desktop ----------
cd /home/user/v2rayN/v2rayN/v2rayN.Desktop

# Semi-default leak
grep -rc '<TextBox' Views/*.axaml | grep -v ':0'          # 126 total
grep -rn '<TextBox' Views/*.axaml | grep -c 'Theme='      # 8 themed
grep -rc '<ComboBox' Views/*.axaml | grep -v ':0'         # 66, none themed
grep -rn '<ToggleSwitch' Views/*.axaml | grep -c 'Theme=' # 14 of 57

# button class fragmentation  -> 28 combinations
grep -rhoE '<Button[^>]*Classes="[^"]*"' Views/*.axaml | grep -oE 'Classes="[^"]*"' | sort | uniq -c | sort -rn

# view-local style rules  -> 190 across 24 files
grep -rc '<Style Selector=' Views/*.axaml | grep -v ':0'

# inline hex  -> 3
grep -rnE '(Background|Foreground|BorderBrush|Fill|Stroke)="#' Views/ | grep -v GlobalResources

# StaticResource on theme brushes  -> 0
grep -rn 'StaticResource Brush\.' Views/

# off-scale spacing  -> 97 occurrences, 14 values
grep -rhoE '(Margin|Padding|Spacing)="[0-9, \.]+"' Views/ | sort | uniq -c | sort -rn

# dashes in shipped copy  -> 44
grep -rn -e '—' -e '–' Common/L.*.cs | wc -l

# the accent is not theme-aware: these must appear inside ThemeDictionaries and do not
grep -n 'Color.Accent\|Brush.Accent\|Brush.OnAccent' Assets/GlobalResources.axaml | head
```

Contrast ratios in C.4 / D2 were computed with the WCAG 2.1 relative-luminance formula; the script
is inline in the survey session and reproduces `#4C8DFF` on `#F4F7FC` = 2.98:1, on `#FFFFFF` =
3.20:1, on `#141619` = 5.66:1, and `#1E5FC7` on `#FFFFFF` = 5.97:1.

---

## Change log

| Date | Change |
|---|---|
| 2026-07-26 | Initial survey. 324 Android + 483 desktop control instances catalogued; **38** counted inconsistencies; **13** cross-platform contradictions logged for owner decision (D1 and D2 are blocking). |
