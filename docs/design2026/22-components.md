# 22 - Components

**Departament VPN - the shared component system. One library, two implementations.**

Status: **specification.** This file is normative. It is what both codebases are rebuilt on. Where
it gives a number, that number is the number; where it gives a name, that name is the name on both
platforms.

Authority chain: `00-rules.md` §0.1. This file sits at level 2 alongside `00-rules.md`; where it
resolves a contradiction that `00-rules.md` left open, the ruling is recorded in section 1 with the
precedence argument and the exact amendment `00-rules.md` needs. Facts come from
`20-control-survey.md` (control census) and `21-account-survey.md` (Account surfaces); every "today"
claim below is one of theirs.

Scope of the owner's demand this file answers:

> «там также все кнопки и в них стиль переделать, все надо переработать полностью под общий концепт
> приложений, это касается и пк версии и андроид версии»

So: **buttons first and hardest**, then the rest of the library to the same standard, both platforms
side by side, every state.

| | Android | Desktop |
|---|---|---|
| Token files | `res/values/{dimens,colors,styles,themes,motion}.xml`, `res/values-night/colors.xml` | `Assets/GlobalResources.axaml`, `Common/Motion.cs` |
| Component files | `res/values/styles.xml`, `res/drawable/*.xml`, `res/color/*.xml` | `Assets/GlobalStyles.axaml` |
| Stack | Kotlin + Material 3 (1.13.0) + XML views, minSdk 24 | C# .NET + Avalonia + Semi.Avalonia base |

---

## 0. How to read this file

### 0.1 The naming law

**One concept, one name, both platforms.** A reviewer must be able to read an Android style name and
a desktop class name and see the same component. The mapping is mechanical:

| Concept | Android style name | Desktop class selector |
|---|---|---|
| `X` variant `Y` | `@style/Widget.Departament.X.Y` | `X.Y` (e.g. `Button.Primary`) |
| Text role `R` | `@style/TextAppearance.App.R` | `TextBlock.R` |
| Token `T` | `@dimen/t` / `@color/t` | `T` resource key |

Any name that exists on only one platform is a defect unless the component itself is
platform-exclusive (haptics, hover, keyboard shortcuts). Names that must die are listed with their
replacement in section 20.

### 0.2 What "complete" means for a component here

Every component below states, without exception:

1. **Anatomy** - the full element tree, in order, with the slot rules.
2. **Geometry** - height, padding, radius, gaps, min touch target, in real dp/px.
3. **Type** - the ramp role, not a size.
4. **States** - default, hover (desktop), pressed, focused, disabled, loading, selected, error,
   whichever apply, each with the exact colour token and the exact transform.
5. **Motion** - the token, the duration, the curve, the reduced-motion fallback.
6. **Both platform mappings** - Android style + parent + drawables/selectors it needs; Avalonia
   class + target control + pseudo-classes it defines.
7. **Copy** - the real Russian strings, when the component carries any.

### 0.3 Reference convention

A bare `§n` cites **`00-rules.md`**. A citation of a section of **this** file always carries that
section's title, so the two can never be confused: `§6 Segmented control`, `§8.4 Row.Action`,
`§2.7 Loading`.

### 0.4 Anything not in this file

If a screen needs a control that is not here, the control is added **here first**, with all of the
above, and only then used. A screen-local `<Style Selector>` or a one-off `MaterialButton` with
inline attributes is a defect by §1.3 (product ban: inconsistent component vocabulary). The desktop
currently carries **190 view-local style rules across 24 files** and **11 bespoke button classes**;
the target after migration is **zero** view-local control styles.

---

## 1. Rulings

These resolve the open contradictions in `20-control-survey.md` Part D and the token gaps it found.
Each states its precedence argument. Each requires the `00-rules.md` §18 change-control entry quoted
with it.

### R1 - Buttons are radius **16**, not pill. (resolves D1)

`00-rules.md` §3.2 says "buttons are pill". The shipped desktop uses `Radius.Button` 16 and carries,
at `Assets/GlobalStyles.axaml:3-14`, a recorded owner decision:

> «Владелец отклонил эти капсулы. Порт: настоящий скруглённый ПРЯМОУГОЛЬНИК radius 16 … 16
> достраивает шкалу Incy chip 12 / button 16 / card 20 / sheet 24»

§0.1.1 puts "the owner's explicit request, in his own words, past or present" **above** this rule
set. The rejection is explicit, recorded, and refers to the exact artefacts (`android_buy.jpg`,
`android_login.jpg`) produced by the current stadium CTAs. Therefore §3.2's button line is the thing
that changes.

**The shape law, restated in full:**

| Radius | Token | Applies to |
|---|---|---|
| **12** | `radius_chip` / `radius_tile` / `Radius.Chip` / `Radius.Tile` | Fittings: chips, badges, icon tiles, flag tiles, **inputs**, segment items, price options |
| **16** | **`radius_button`** / **`Radius.Button`** | Controls with a label that you press: all five button variants, the segmented-control track, the snackbar action's hit shape |
| **20** | `radius_card` / `Radius.Card` | Objects: cards, dialogs, flyouts, modal cards, sheet bodies, snackbar/toast surface, empty-state tile |
| **24 top only** | `radius_sheet` / `Radius.Sheet` | The bottom-sheet lip |
| **Full round** | `radius_pill` / `Radius.Pill` | Only where width == height, or where the shape is intrinsically a track: icon-only buttons, avatars, page dots, the sheet handle, the connect disc, the progress bar, the switch track, the M3 navigation active indicator |

The distinction that makes this teachable: **pill is for circles and tracks, never for a wide
capsule with a label inside it.** A 52x342 stadium is what the owner rejected; a 48x48 round icon
button is not that shape and is not affected.

Dead radii after this: `26dp` (7 Android instances), `22dp` (2), `20dp` on buttons (2), inherited
stadium (19), `Radius.Search` 14 (fold into 12), `Radius.Traffic` 8 (fold into full-round),
`18dp`/`16dp` cards (3), `88dp` on `card_connect` (becomes `radius_pill`).

> **`00-rules.md` §18 entry required:** *2026-07-26 | Button radius is 16 (`radius_button` /
> `Radius.Button`), not pill; pill is reserved for circles and tracks. Supersedes §3.2's "buttons are
> pill" on the strength of the owner's recorded rejection of capsule CTAs
> (`GlobalStyles.axaml:3-14`), per §0.1.1. | §3.2, §11.2* - and the same correction to
> `03-direction.md` §4.5.

### R2 - Two button heights: **48** and **52**, declared as `minHeight`, never as a fixed height

New tokens `btn_height` 48 and `btn_height_tall` 52 (`Size.Btn` / `Size.BtnTall`). 48 is the default
for everything in flow; 52 (`.Tall`) is the screen's one full-width primary CTA.

They are **minimums**, not fixed sizes: `android:layout_height="wrap_content"` +
`android:minHeight`, and Avalonia `MinHeight`, never `Height`. A fixed height clips a two-line label
at font scale 200% / 200% DPI, which is a P1 accessibility defect by §14.5. The desktop's current
`Height="48"` setters are that bug waiting to happen.

This kills, on Android, the six drawn heights (≈29, 36, 40, 44, and two AppCompat defaults) and, on
desktop, the five (32, 40, 44, 48, 52).

**The insets fix.** `Widget.Material3.Button` carries `insetTop`/`insetBottom` = 6dp, which is why
Android's five declared-52dp CTAs draw at 40dp. Every Departament button style sets
`android:insetTop="0dp"` and `android:insetBottom="0dp"`. This is the single change that makes the
shipped button match the layout that declares it.

### R3 - Button labels are on the type ramp: **Title 16/700** or **Title.Medium 16/500** (resolves D6)

Zero of the 33 Android buttons carries a `textAppearance` today, so every Android button label is
Roboto - the brand face is absent from the most-pressed element in the product. Desktop uses Grotesk
at 15, and §3.4 has no 15 step ("15sp does not exist").

- Primary and Destructive: `TextAppearance.App.Title` / `TextBlock.Title` - Space Grotesk 16, weight **700**, tracking 0, sentence case.
- Secondary and Tertiary: `TextAppearance.App.Title.Medium` / `TextBlock.TitleMedium` - Space Grotesk 16, weight **500**.

This deletes all 8 `android:textStyle="bold"` on buttons (synthetic bold on a variable font, §5.4)
and all 6 inline `android:textSize` on buttons.

### R4 - One press recipe: **scale 0.97**, in 90ms `ease_out_quart`, out 160ms `ease_out_quint`

`res/anim/press_scale.xml` today uses 0.96 and `ease_out_quart` in **both** directions;
`res/anim/nav_press.xml` uses 0.92, two off-token durations (100/120) and **linear** easing, which
§8.3 bans by name. Desktop carries six different press scales.

Fix: rewrite `press_scale.xml` to 0.97 / 90 `ease_out_quart` in / 160 `ease_out_quint` out, and
**delete `nav_press.xml`** (its consumer, the hand-rolled bottom nav, is replaced in section 15).

Avalonia expresses the asymmetry by putting a **different `Transitions` collection on the `:pressed`
selector than on the base selector**: the transition active when the property changes is the one on
the currently-applied style, so entering `:pressed` uses 90ms `Ease.OutQuart` and leaving it uses
160ms `Ease.OutQuint`. The shipped single `0:0:0.12` on both directions is replaced everywhere.

### R5 - Rows do not scale. Objects do. (resolves D10 for rows)

A row is a slice of a surface; scaling it tears the hairlines above and below it and reads as a
rendering bug. The desktop already reached this conclusion in writing at
`GlobalStyles.axaml:650-655` for `Border.SettingRow`.

- **Rows inside a card or group** (navigation / value / toggle / action / destructive rows, payment
  method rows, sheet rows): press feedback is a **background step** to `colorSurfaceContainerHigh`
  `#1A1D21` over `motion_press_in` 90ms, released over `motion_press_out` 160ms. Android also gets
  the standard `?attr/selectableItemBackground` ripple. **No scale.**
- **Free-standing pressable objects** (server row, subscription card, account chip, tariff card,
  device card, empty-state CTA card): scale 0.97 per R4, plus the background step.

### R6 - Disabled is **0.38 on the whole control**, both platforms

Android expresses it with `ColorStateList` selectors carrying `android:alpha="0.38"` on the
`state_enabled="false"` item for `backgroundTint`, `textColor`, `iconTint` and `strokeColor` -
because a MaterialButton style cannot set a state-dependent `android:alpha`. Desktop uses
`:disabled { Opacity 0.38 }`, which it already does on 9 of 19 classes; the other 10 gain it.

Android has **zero** declarative disabled treatments today and 7 imperative `isEnabled = false`
calls in the whole `ui/` package. After this spec, disabled is free: set `isEnabled = false` and the
style does the rest.

### R7 - Focus is drawn on **every** focusable control, on both platforms

Android today has exactly one `state_focused` drawable in the entire app
(`res/drawable/bg_server_row.xml`) - including on the two D-pad-only TV activities. Desktop omits
the focus adorner on 16 classes, among them every icon button and both navigations.

Mechanism, unified:

- **Filled controls** (Primary, Destructive, filled icon buttons): an **inner** 2dp ring in the
  control's own on-colour at 40% alpha, at the control's own radius. An outer accent ring on an
  accent fill is invisible.
- **Everything else**: an **outer** 2dp `colorPrimary` / `Brush.Accent` ring at 2dp offset, radius =
  control radius + 2.
- Android draws it with the MaterialButton stroke: `app:strokeWidth="2dp"` **always present** (it
  draws inside the bounds, so no layout shift) plus a `strokeColor` ColorStateList whose default is
  `@android:color/transparent`.
- Desktop draws it with `FocusAdorner`, which already exists for 8 classes and is extended to all.
  The adorner is **not** suppressed under `.lite`: accessibility outranks motion reduction.

### R8 - Loading holds the width, hides the label, spins a 20dp arc, and is not the disabled look

Full contract in §2.7 Loading. Zero of the 807 surveyed controls implements it today.

### R9 - Double-press is impossible by construction

Every action button either (a) is bound to a command that reports its own in-flight state and
disables itself, or (b) is wrapped in a **500ms re-entry guard**. New token `input_debounce` = 500
(`Input.Debounce`). Full contract in §2.8 Double-press.

### R10 - The input-field size token, which `00-rules.md` did not have (resolves D8)

`field_min_height` = **56dp/px** - deliberately the same number as `row_min_height` / `Size.Row`, so
a form and a list share one horizontal rhythm, and so a Material `TextInputLayout.OutlinedBox`
(whose own minimum is 56dp) is not fought. Radius 12 (fittings). This replaces the desktop's 52/44
pair and Android's three informal heights.

> **`00-rules.md` §18 entry required:** *2026-07-26 | Added `field_min_height` 56 (`Size.Field`), the
> input-field size token §3.3 was missing (D8). Inputs are radius 12. | §3.2, §3.3*

### R11 - One accent, and the accent is not theme-aware today. Fix that first. (resolves D2)

`Color.Accent`, `Brush.Accent`, `Brush.OnAccent`, every `Brush.Tile.*`, `Brush.SelectedFill` and
every `Brush.StatusChip.*` are declared **outside** `ResourceDictionary.ThemeDictionaries` in
`GlobalResources.axaml` (lines 39-51, 226-258). In light theme the accent therefore stays `#4C8DFF`
and measures **2.98:1** on the light background - below even the 3:1 UI floor. 17 `LinkAction`
buttons, every checked segment label and **every focus ring in the product** are drawn at that
ratio.

This is a P1 that no component spec can work around: **move those keys inside the `Dark` and `Light`
theme dictionaries before implementing anything below.** Light accent is `#1E5FC7` (5.56:1 on
`#F4F7FC`, 5.97:1 on `#FFFFFF`).

### R12 - The four-hue payment status becomes three, and status classes stop being reused for health

Green = оплачено/активно. Amber = в обработке/истекает. Red = ошибка/истекла. **«Отменён» is
neutral** - a cancelled payment is not a warning. And the subscription-health chip gets its own
class names (`Chip.Status.Active/.Expiring/.Expired`) instead of borrowing
`.paid`/`.pending`/`.failed`, which lie about the semantics (`21-account-survey.md` §2.3.7).

### R13 - No component invents a colour. `Success` and the third-party green are deleted

12 desktop buttons carry `Classes="IconButton Success"`; **`Success` is defined nowhere in this
repository** and resolves against Semi.Avalonia's own semantic green (`App.axaml:20`). That is a
second accent hue, shipped 12 times, banned by §1.4.1. All 12 become `Button.Icon`.

### R14 - Where the accent is allowed, exhaustively

**Allowed** (this is the whole list):

1. Exactly **one** filled accent surface per screen: the primary button, **or** the connect control, never both.
2. The current navigation destination (indicator fill + label colour + weight 700).
3. The selected state of a selectable item (12% accent fill + accent check glyph + weight 700).
4. The focus ring.
5. Determinate progress fill (traffic meter, download, connect arc).
6. The tertiary button's label - **at most two tertiary buttons visible at once**.
7. The tariff badge chip (`Chip.Accent`) - one per subscription card.
8. A genuinely categorical icon tile - at most **three** coloured tiles on one screen.

**Forbidden** (each of these exists today and each is a defect):

- An accent-coloured **row title** (`row_buy` on Android `activity_account.xml`, `BuyRow` on desktop
  `AccountView.axaml:1305`). A row title is `colorOnSurface`. The row's job is carried by its tile
  and its position, not by tinting the noun.
- A second filled accent button on the same screen (Account EMPTY state has two today).
- An accent icon tile on a non-categorical row. **56 of 65 Android tiles are blue; only 4 are
  neutral.** The default tile is `@color/icon_tile_neutral` `#20242B` with
  `@color/icon_glyph_neutral` `#9BA1AD`.
- An accent wash across a list row that is not selected (the desktop's current-device row).
- Accent on section headers, body copy, dividers, card borders, hover states, secondary buttons,
  skeletons, or any chip other than the tariff badge.
- Any accent **gradient**. `TrafficFillBrush` is a `LinearGradientBrush` today; a solid fill of the
  same width encodes the same number (§6.5).

### R15 - The 15 components in this file are the entire vocabulary

Button (5 variants, 2 modifiers) · Icon button · Text field · Select · Segmented control · Switch ·
Row (5 archetypes) · Card · Chip · Tab bar · Toolbar · Sheet/Dialog · Snackbar · Empty state ·
Skeleton · Progress · Selection indicator. Nothing else is a component; everything else is a layout
of these.

---

## 2. Buttons

The owner's demand is loudest here, so this section is the longest.

### 2.1 The five variants, and no more

| Variant | What it is for | How many per screen |
|---|---|---|
| **Primary** | The one thing the screen wants you to do. Accent fill. | **1**, at most |
| **Secondary** | The realistic alternative to the primary, or an action with no competitor. Neutral tonal fill. | Up to 2 |
| **Tertiary** | A quiet action that must not compete: inline row actions, «Отмена», «Повторить» next to a primary, dialog buttons. Transparent, accent label. | Up to 2 |
| **Destructive** | Removes something the user cannot get back cheaply. Red fill. | 1 |
| **Icon** | A single glyph with no label, where the glyph is unambiguous and named for assistive tech. | No hard cap; each one still needs a `contentDescription` |

Two modifiers, applied on top of a variant, never alone:

- **`.Tall`** - height 48 → 52. Only on Primary, only for the screen's full-width bottom CTA.
- **`.Filled`** - Icon only: transparent → `colorSurfaceContainerHighest` fill (steppers, the
  toolbar action that needs to be found instantly).

There is no outlined variant. `Button.OutlinedAccent` (desktop, 3 instances) and
`?attr/materialButtonOutlinedStyle` (Android, 8 instances) collapse into **Secondary**: an outlined
accent button is a second accent surface competing with the primary, which §4.3 forbids outright.

There is no text-with-underline variant, no ghost variant, no "success" variant (R13), no elevated
variant.

### 2.2 Collapse table - every existing button, and what it becomes

**Android** (all 34 instances from `20-control-survey.md` §A.2):

| Today | File | Becomes |
|---|---|---|
| `btn_top_up` filled, pill, bold | `activity_account.xml` | **Secondary** - the Account screen's one Primary is the subscription action, not top-up |
| `btn_buy_first` filled | `activity_account.xml` | **Primary** |
| `btn_retry_load` tonal | `activity_account.xml` | **Tertiary** |
| `btn_retry` tonal r22 | `activity_buy_tariff.xml` | **Tertiary** |
| `btn_dev_minus` / `btn_dev_plus` IconButton 40, r20 | `activity_buy_tariff.xml` | **Icon.Filled**, 48dp box |
| `btn_pay` filled 52 r26 | `activity_buy_tariff.xml` | **Primary.Tall** |
| `btn_mem_40…150` outlined 44 ×5 | `activity_local_proxy.xml` | **Segmented control** (§6 Segmented control), not buttons |
| `btn_reset_creds` outlined | `activity_local_proxy.xml` | **Tertiary** |
| `btn_telegram` 52 r26 | `activity_login.xml` | **Primary.Tall** |
| `btn_restart` outlined | `activity_login.xml` | **Tertiary** |
| `btn_site` 52 r26 | `activity_login.xml` | **Secondary** |
| `btn_confirm_2fa` 52 r26 | `activity_login.xml` | **Primary.Tall** |
| `btn_register_site` outlined | `activity_login.xml` | **Tertiary** |
| `btn_history_buy` tonal r22 | `activity_payment_history.xml` | **Secondary** |
| `fab_add_proxy_chain_member` FAB | `activity_server_proxy_chain.xml` | **Icon.Filled** in the toolbar; the app has one FAB and it does not earn a floating layer |
| `btn_regenerate` plain AppCompat `Button` | `activity_tv_receive.xml` | **Secondary** |
| `btn_scan` / `btn_send` | `activity_tv_send.xml` | **Secondary** / **Primary** |
| `btn_device_delete` IconButton 44, red | `item_device.xml` | **Icon**, 48dp box, red glyph |
| `btn_home_add_qr` | `layout_home_empty.xml` | **Secondary** |
| `btn_home_add_clipboard` outlined+icon | `layout_home_empty.xml` | **Tertiary** |
| `btn_home_buy` filled | `layout_home_empty.xml` | **Primary** |
| `btn_home_link_tg` outlined+icon | `layout_home_empty.xml` | **Tertiary** |
| `btn_home_login_tg` tonal 52 | `layout_home_empty.xml` | **Primary.Tall** |
| `btn_home_login_site` outlined+icon | `layout_home_empty.xml` | **Secondary** |
| `btn_import_clipboard` | `layout_servers_empty.xml` | **Secondary** |
| `btn_scan_qr` outlined+icon | `layout_servers_empty.xml` | **Tertiary** |
| `btn_support` tonal, `minHeight=0`, ≈29dp drawn | `layout_subscription_meta_bar.xml` | **Icon** (48dp) - a 29dp control is a P1 touch defect and the label is redundant beside its tile |
| unnamed `Widget.AppCompat.Button.Borderless` | `preference_with_help_link.xml` | **Tertiary** |

**Desktop** (all 28 class combinations from §B.3):

| Today | Instances | Becomes |
|---|---|---|
| *(no class at all)* | 54 | The variant its role demands. Untriaged legacy windows (`AddServerWindow` 6, `OptionSettingWindow` 4, …) default to **Secondary**, the screen's confirm to **Primary** |
| `Primary` | 15 | `Button.Primary` |
| `Primary Tall` | 7 | `Button.Primary.Tall` |
| `Tonal` | 13 | **`Button.Secondary`** (rename) |
| `Tonal Tall` (declared twice, locally) | 4 | `Button.Secondary` - `.Tall` does not exist on Secondary |
| `OutlinedAccent` | 3 | **`Button.Secondary`** |
| `Destructive` | 1 | `Button.Destructive` |
| `LinkAction` | 17 | **`Button.Tertiary`** (rename) |
| `IconButton` (legacy 32×32, re-declared in 10 views) | 18 | **`Button.Icon`** |
| `IconButton40`, `IconButton40 Row`, `IconButton40 Accent` | 13 | **`Button.Icon`** |
| `IconButton Success` | 12 | **`Button.Icon`** - `Success` deleted (R13) |
| `BackNav`, `IconButton BackNav` | 4 | **`Button.Icon`**, placed by the toolbar (§12 Toolbar) |
| `Stepper` | 2 | **`Button.Icon.Filled`** |
| `NavRailItem` | 3 | **`Button.NavItem.Rail`** (§11 Tab bar) |
| `BottomNavItem` (local) | 3 | **`Button.NavItem.Bar`** (§11 Tab bar) |
| `SegItem` (local, LoginView) | 2 | **`ToggleButton.Segment`** (§6 Segmented control) |
| `Flat` (local, BuyView) | 3 | **`Button.Tertiary`** |
| `WinBtn`, `WinBtn close` (local, MainWindow) | 3 | **`Button.Icon`**; `.close` keeps only its hover colour override, declared globally |
| `RailToggle` (local) | 1 | **`Button.Icon`** |
| `MethodChip` (local, AccountView) | 1 | **`ToggleButton.Segment`** |
| `MeterRow` (local, AccountView) | 1 | **Row, value archetype** (§8.3 Row.Value) |
| `MetaIcon`, `MetaDanger` (local, SubscriptionMetaView) | 6 | **`Button.Icon`**; `MetaDanger` → `Button.Icon.Danger` glyph tint only |

Net: **219 button instances, 11 bespoke classes, 28 class combinations, 11 heights and 10 radii
collapse into 5 variants, 2 modifiers, 2 heights, 1 radius.**

### 2.3 Geometry

| Property | Primary | Secondary | Tertiary | Destructive | Icon |
|---|---|---|---|---|---|
| Min height | `btn_height` 48 (`.Tall` → 52) | 48 | 48 | 48 | Android 48, desktop 40 (`Size.IconButton`) |
| Horizontal padding | `space_24` 24 | 24 | `space_12` 12 | 24 | 0 (square box) |
| Min width | `btn_min_width` 96 | 96 | none | 96 | = height |
| Corner radius | `radius_button` 16 | 16 | 16 | 16 | `radius_pill` (circle) |
| Insets (Android) | 0 top / 0 bottom | 0/0 | 0/0 | 0/0 | 0/0 |
| Icon size | 20 | 20 | 20 | 20 | **22** |
| Icon-to-label gap | `space_8` 8 | 8 | 8 | 8 | n/a |
| Min touch target | 48×48 | 48×48 | 48×48 | 48×48 | Android 48×48, desktop 40×40 |
| Gap to the next button | `space_8` 8 minimum, `space_12` 12 when stacked vertically | | | | |
| Full-width behaviour | `match_parent` at the gutter, label centred | same | never full-width | same | never full-width |

Notes that are not optional:

- **Height is `minHeight`.** At font scale 200% a 16sp label needs ~34dp of line box; the button
  grows to ~58dp and the layout must let it. Any `android:layout_height="48dp"` on a button is a
  defect.
- **96dp minimum width** stops «Да» / «Нет» / «ОК»-sized buttons from appearing (they are banned
  copy anyway, §9.2, but the geometry backs the rule up).
- **Icon-only buttons on Android are 48dp even though the glyph is 22dp.** Today 34 of 35 icon
  affordances are below 48dp; this single rule fixes all of them.
- The icon in a labelled button is **20dp**, which is the §10.3 "inline" size. 22dp is the tile
  glyph and 24dp is the navigation glyph; a button icon is neither.

### 2.4 Type

| Variant | Ramp role | Family | Size | Weight | Tracking | Case |
|---|---|---|---|---|---|---|
| Primary | `TextAppearance.App.Title` / `TextBlock.Title` | Space Grotesk | 16 | 700 | 0 | sentence |
| Secondary | `TextAppearance.App.Title.Medium` / `TextBlock.TitleMedium` | Space Grotesk | 16 | 500 | 0 | sentence |
| Tertiary | `TextAppearance.App.Title.Medium` / `TextBlock.TitleMedium` | Space Grotesk | 16 | 500 | 0 | sentence |
| Destructive | `TextAppearance.App.Title` / `TextBlock.Title` | Space Grotesk | 16 | 700 | 0 | sentence |
| Icon | n/a | | | | | |

Labels are verbs, sentence case, no final period, no ALL-CAPS, no ellipsis unless the action opens a
further choice («Способ оплаты…» is correct; «Оплатить…» is not). One line; a button label that
needs two lines is the wrong copy. `android:textAllCaps` is `false` in the style, so no layout ever
needs to set it again (0 instances today - keep it 0).

### 2.5 State tables

Colour tokens are Android attr / desktop key. Dark values are given for the reader; the token is
what ships.

#### Primary

| State | Container | Label + icon | Border | Transform | Motion |
|---|---|---|---|---|---|
| Default | `?attr/colorPrimary` / `Brush.Accent` `#4C8DFF` | `?attr/colorOnPrimary` / `Brush.OnAccent` `#00183A` (5.51:1) | none | none | - |
| Hover (desktop) | `#3D7EF0` - one step darker, **never lighter, never a hue flip** | unchanged | none | none | `Dur.State` 220 `Ease.Standard` on Background |
| Pressed | `#3877E0` | unchanged | none | `scale(0.97)` | in 90 `ease_out_quart`, out 160 `ease_out_quint` |
| Focused | unchanged | unchanged | **inner** 2dp `colorOnPrimary` @ 40%, radius 16 | none | none |
| Disabled | `colorPrimary` @ 38% | `colorOnPrimary` @ 38% | none | none | none |
| Loading | unchanged | label hidden, 20dp arc in `colorOnPrimary` | none | none | §2.7 |

The hover values `#3D7EF0` / `#3877E0` are today raw hex in `AccountView.axaml:65,68` and in
`GlobalStyles.axaml`. They become tokens: `Color.AccentHover` / `Color.AccentPressed` in the desktop
dictionary and `@color/accent_hover` / `@color/accent_pressed` on Android (Android has no hover, but
the pressed value is used by the ripple's underlying colour so both platforms stay identical).

#### Secondary

| State | Container | Label + icon | Border | Transform |
|---|---|---|---|---|
| Default | `?attr/colorSurfaceContainerHighest` / `Brush.SurfaceHighest` `#20242B` | `?attr/colorOnSurface` / `Brush.OnSurface` `#F2F4F8` (14.14:1) | none | none |
| Hover (desktop) | `Brush.Hover` overlay (black 0.32 dark / 0.06 light) | unchanged | none | none |
| Pressed | `Brush.Hover` overlay + Android ripple `colorPrimary` @ 12% | unchanged | none | `scale(0.97)` |
| Focused | unchanged | unchanged | **outer** 2dp `colorPrimary`, offset 2, radius 18 | none |
| Disabled | container @ 38% | label @ 38% | none | none |
| Loading | unchanged | label hidden, 20dp arc in `colorOnSurface` | none | none |

#### Tertiary

| State | Container | Label + icon | Border | Transform |
|---|---|---|---|---|
| Default | transparent | `?attr/colorPrimary` / `Brush.Accent` (6.64:1 on background, 5.66:1 on surface) | none | none |
| Hover (desktop) | `Brush.Hover` | unchanged | none | none |
| Pressed | `Brush.Hover` + Android ripple | unchanged | none | `scale(0.97)` |
| Focused | transparent | unchanged | outer 2dp `colorPrimary`, radius 18 | none |
| Disabled | transparent | `colorPrimary` @ 38% | none | none |
| Loading | transparent | label hidden, 20dp arc in `colorPrimary` | none | none |

Tertiary is the **only** button that may sit inside a row (§8.4 Row.Action). When it does, it is right-aligned
in the trailing slot and the row itself is not clickable.

#### Destructive

| State | Container | Label + icon | Border | Transform |
|---|---|---|---|---|
| Default | `?attr/colorError` / `Brush.Red` `#F04452` | `?attr/colorOnError` / white `#FFFFFF` - **3.71:1, which clears the ≥3:1 large-text floor only because the label is 16sp/700. Destructive labels never drop below that.** | none | none |
| Hover (desktop) | `Brush.RedPressed` | unchanged | none | none |
| Pressed | `Brush.RedPressed` | unchanged | none | `scale(0.97)` |
| Focused | unchanged | unchanged | inner 2dp white @ 40%, radius 16 | none |
| Disabled | container @ 38% | label @ 38% | none | none |
| Loading | unchanged | label hidden, 20dp arc in white | none | none |

Destructive **text** on a surface (a destructive row's title, an error line) is never `#F04452`; it
is `@color/ping_bad` / `Brush.RedText` `#FF6069` (6.15:1). The fill and the text colour are different
tokens for a measured reason (§3.5 of the rules); `BuyView.axaml`'s `PaymentNoticeTitle` in
`Brush.Red` is the current violation.

#### Icon

| State | Container | Glyph | Transform |
|---|---|---|---|
| Default | transparent (`.Filled`: `colorSurfaceContainerHighest`) | `?attr/colorOnSurfaceVariant` `#9BA1AD`; `.Accent` → `colorPrimary`; `.Danger` → `?attr/iconTintRed` `#F04452` | none |
| Hover (desktop) | `Brush.Hover` | `Brush.OnSurface` | none |
| Pressed | `Brush.Hover` + Android ripple, borderless, radius = pill | unchanged | `scale(0.97)` |
| Focused | unchanged | unchanged | outer 2dp `colorPrimary`, radius = pill |
| Disabled | transparent | glyph @ 38% | none |
| Loading | transparent | glyph replaced by a 22dp arc in the glyph's own colour | none |

Every Icon button carries `android:contentDescription` / `AutomationProperties.Name` **and** a
desktop `ToolTip.Tip` with the same Russian string. 18 of 65 desktop icon buttons lack one today
(§B.6); that is a P1 each.

### 2.6 What the states look like in the other two themes

- **Light**: accent `#1E5FC7` (5.56:1 on `#F4F7FC`), `Brush.OnAccent` white, Secondary container
  `#E3EAF4` with `#111826` label, hover overlay black @ 0.06, error `#C42B32`, error text `#C42B32`.
- **Mono**: `ThemeOverlay.Mono` remaps `colorPrimary` to ink. Primary becomes an ink fill with a
  paper label; Tertiary becomes ink text. **The hierarchy must survive**: verify by squinting that
  the primary is still the loudest element with the colour removed. If Primary and Secondary are
  indistinguishable in mono, Secondary's container is one step lighter, not the primary one step
  darker.

### 2.7 Loading, precisely

The contract, identical on both platforms:

1. **The label is hidden, not removed.** The button keeps its exact width.
2. **The width is pinned** before the swap, so a 3-character label and a 20dp spinner do not produce
   two different widths.
3. **A 20dp arc spins in the button's own foreground colour** - `colorOnPrimary` inside a Primary,
   `colorOnSurface` inside a Secondary, `colorPrimary` inside a Tertiary, white inside a Destructive.
4. **The arc is a 90° sweep, 2dp stroke, round caps, rotating 360° in 1100ms, linear.** Linear is
   correct here and is not the `00-rules.md` §8.3 ban: that ban is about transitions between states, not about a
   continuous rotation, where any easing would read as a stutter.
5. **The control is not the disabled look.** Opacity stays 1.0. It stops accepting input
   (`isClickable = false` / `IsHitTestVisible = False`) but it does not fade, because a faded
   spinner reads as "broken", not "working".
6. **Focus is retained.** The button stays focusable so the keyboard user is not thrown to the top
   of the page.
7. **Assistive tech is told.** `contentDescription` becomes `"<label>, загрузка"` /
   `AutomationProperties.Name` likewise, and the desktop sets
   `AutomationProperties.LiveSetting="Polite"` on the button.
8. **It appears only after 300ms** (`00-rules.md` §7.3). An action that completes in 80ms must not flash a
   spinner. The call site starts a 300ms timer and cancels it on completion.
9. **On completion**: success → the label returns and the screen moves on within `motion_state`
   220ms; failure → the label returns and the error surfaces as an inline message or a snackbar with
   «Повторить» (§9.4). The button never stays spinning.

**Android implementation** - a single shared extension, `util/ControlState.kt`:

```kotlin
// util/ControlState.kt
private val loadingLabel = HashMap<Int, CharSequence>()

fun MaterialButton.setLoading(loading: Boolean) {
    if (loading) {
        if (tag_loading == true) return
        minimumWidth = width                       // pin the width
        loadingLabel[id] = text
        text = ""
        icon = IndeterminateDrawable.createCircularDrawable(
            context,
            CircularProgressIndicatorSpec(context, null, 0,
                R.style.Widget_Departament_Spinner_Button)   // 20dp / 2dp track
        ).apply { setTint(currentTextColor) }
        iconPadding = 0
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        isClickable = false
        contentDescription = "${loadingLabel[id]}, ${context.getString(R.string.state_loading)}"
    } else {
        icon = null
        text = loadingLabel.remove(id)
        minimumWidth = resources.getDimensionPixelSize(R.dimen.btn_min_width)
        isClickable = true
        contentDescription = null
    }
}
```

`Widget.Departament.Spinner.Button` is `Widget.Material3.CircularProgressIndicator.ExtraSmall` with
`indicatorSize` 20dp, `trackThickness` 2dp, `indicatorInset` 0dp.

New string: `<string name="state_loading">загрузка</string>`.

**Desktop implementation** - the button's content is always a two-child `Panel`, so the width is
held by the layout with no measurement code:

```xml
<Button Classes="Primary" Command="{Binding PayCmd}">
  <Panel>
    <TextBlock Text="{x:Static l:L.Buy_Pay}"
               Opacity="{Binding IsPaying, Converter={StaticResource BoolToZeroOne}}" />
    <Ellipse Classes="Spinner spinning" Width="20" Height="20"
             StrokeThickness="2" StrokeDashArray="1.6,6.4" StrokeLineCap="Round"
             Stroke="{DynamicResource Brush.OnAccent}"
             IsVisible="{Binding IsPaying}" />
  </Panel>
</Button>
```

plus, in `GlobalStyles.axaml`, `Button:disabled` is **not** applied while loading: bind
`IsHitTestVisible`, not `IsEnabled`. `Ellipse.Spinner.spinning` already rotates at 1.1s linear
(`GlobalStyles.axaml:1331-1344`); that value becomes `Motion.Dur.Spin` = 1100ms in `Common/Motion.cs`
so the two platforms are provably the same speed.

### 2.8 Double-press

Three layers; a button needs at least one and gets the strongest that applies.

1. **Commanded buttons** (desktop, and Android buttons bound through a ViewModel action): the
   command exposes `IsExecuting`, `CanExecute` goes false for the duration, the button both disables
   and shows the loading state. This is the preferred layer and covers every network action.
2. **Navigation buttons** (open a screen, open a sheet, open a browser tab): the shell holds a
   `navigationInFlight` flag from the moment the transition starts until it completes; taps during
   that window are dropped. This is what stops two `BuyTariffActivity` instances stacking when the
   user double-taps «Купить».
3. **Everything else**: a **500ms re-entry guard** (`@integer/input_debounce` / `Input.Debounce`),
   implemented once:

```kotlin
// util/ControlState.kt
fun View.onSingleClick(action: (View) -> Unit) {
    setOnClickListener { v ->
        val now = SystemClock.elapsedRealtime()
        val last = v.getTag(R.id.tag_last_click) as? Long ?: 0L
        if (now - last < v.resources.getInteger(R.integer.input_debounce)) return@setOnClickListener
        v.setTag(R.id.tag_last_click, now)
        action(v)
    }
}
```

Desktop: `ControlState.SingleClick(button, action)` in `Common/ControlState.cs`, same window,
same semantics.

**Rule: `setOnClickListener` is not used directly on any button in `ui/**` after this migration.**
The grep `grep -rn 'setOnClickListener' ui/` is the enforcement check; every hit must be
`onSingleClick`.

**Destructive double-press**: a destructive button additionally requires the pointer/finger to be
released **inside** the control (which both platforms do by default) and never auto-focuses. In a
confirm dialog, the neutral action is the auto-focused one (`DevicesView` already does this
correctly with «Отмена»).

### 2.9 Platform mapping

#### Android - `res/values/styles.xml`

```xml
<!-- Base: every Departament button. Zeroes the Material insets (this is why the
     shipped 52dp CTAs currently draw at 40dp), pins the shape to radius_button 16,
     puts the brand face on the label, and carries the always-present 2dp stroke
     that the focus ColorStateList paints. -->
<style name="Widget.Departament.Button" parent="Widget.Material3.Button">
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:minHeight">@dimen/btn_height</item>
    <item name="android:minWidth">@dimen/btn_min_width</item>
    <item name="android:paddingStart">@dimen/space_24</item>
    <item name="android:paddingEnd">@dimen/space_24</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title</item>
    <item name="android:textAllCaps">false</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.Departament.Button</item>
    <item name="iconSize">20dp</item>
    <item name="iconPadding">@dimen/space_8</item>
    <item name="iconGravity">textStart</item>
    <item name="strokeWidth">2dp</item>
    <item name="elevation">0dp</item>
</style>

<style name="ShapeAppearance.Departament.Button" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">@dimen/radius_button</item>
</style>

<style name="Widget.Departament.Button.Primary">
    <item name="backgroundTint">@color/btn_primary_container</item>
    <item name="android:textColor">@color/btn_primary_content</item>
    <item name="iconTint">@color/btn_primary_content</item>
    <item name="strokeColor">@color/btn_focus_inner_on_accent</item>
    <item name="rippleColor">@color/ripple_on_accent</item>
</style>

<style name="Widget.Departament.Button.Primary.Tall">
    <item name="android:minHeight">@dimen/btn_height_tall</item>
</style>

<style name="Widget.Departament.Button.Secondary">
    <item name="backgroundTint">@color/btn_secondary_container</item>
    <item name="android:textColor">@color/btn_secondary_content</item>
    <item name="iconTint">@color/btn_secondary_content</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title.Medium</item>
    <item name="strokeColor">@color/btn_focus_outer_accent</item>
    <item name="rippleColor">@color/ripple_accent</item>
</style>

<style name="Widget.Departament.Button.Tertiary" parent="Widget.Material3.Button.TextButton">
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:minHeight">@dimen/btn_height</item>
    <item name="android:paddingStart">@dimen/space_12</item>
    <item name="android:paddingEnd">@dimen/space_12</item>
    <item name="android:textAppearance">@style/TextAppearance.App.Title.Medium</item>
    <item name="android:textAllCaps">false</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.Departament.Button</item>
    <item name="android:textColor">@color/btn_tertiary_content</item>
    <item name="iconTint">@color/btn_tertiary_content</item>
    <item name="iconSize">20dp</item>
    <item name="iconPadding">@dimen/space_8</item>
    <item name="strokeWidth">2dp</item>
    <item name="strokeColor">@color/btn_focus_outer_accent</item>
    <item name="rippleColor">@color/ripple_accent</item>
</style>

<style name="Widget.Departament.Button.Destructive">
    <item name="backgroundTint">@color/btn_destructive_container</item>
    <item name="android:textColor">@color/btn_destructive_content</item>
    <item name="iconTint">@color/btn_destructive_content</item>
    <item name="strokeColor">@color/btn_focus_inner_on_error</item>
    <item name="rippleColor">@color/ripple_on_accent</item>
</style>

<style name="Widget.Departament.Button.Icon" parent="Widget.Material3.Button.IconButton">
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:minWidth">@dimen/view_height_dp48</item>
    <item name="android:minHeight">@dimen/view_height_dp48</item>
    <item name="android:layout_width">@dimen/view_height_dp48</item>
    <item name="android:layout_height">@dimen/view_height_dp48</item>
    <item name="iconSize">@dimen/tile_glyph</item>
    <item name="iconTint">@color/btn_icon_glyph</item>
    <item name="android:stateListAnimator">@anim/press_scale</item>
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.Departament.Pill</item>
    <item name="strokeWidth">2dp</item>
    <item name="strokeColor">@color/btn_focus_outer_accent</item>
    <item name="rippleColor">@color/ripple_neutral</item>
</style>

<style name="Widget.Departament.Button.Icon.Filled">
    <item name="backgroundTint">@color/btn_secondary_container</item>
</style>
<style name="Widget.Departament.Button.Icon.Accent">
    <item name="iconTint">@color/btn_icon_glyph_accent</item>
</style>
<style name="Widget.Departament.Button.Icon.Danger">
    <item name="iconTint">@color/btn_icon_glyph_danger</item>
</style>
```

And in `themes.xml`, so no instance ever has to opt in again - this is the root cause of half of
Part A of the survey:

```xml
<item name="materialButtonStyle">@style/Widget.Departament.Button.Secondary</item>
<item name="materialCardViewStyle">@style/Widget.Departament.Card</item>
<item name="materialSwitchStyle">@style/Widget.Departament.Switch</item>
<item name="textInputStyle">@style/Widget.Departament.TextField</item>
<item name="chipStyle">@style/Widget.Departament.Chip</item>
<item name="snackbarStyle">@style/Widget.Departament.Snackbar</item>
<item name="bottomNavigationStyle">@style/Widget.Departament.NavigationBar</item>
<item name="navigationRailStyle">@style/Widget.Departament.NavigationRail</item>
<item name="toolbarStyle">@style/Widget.Departament.Toolbar</item>
<item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Departament.Small</item>
<item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Departament.Medium</item>
<item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Departament.Large</item>
```

(`Small` = 12, `Medium` = 16, `Large` = 20.)

**`res/color/` selectors** the styles above require - eight files, each three lines, each replacing
dozens of instance-level declarations. `?attr` in a ColorStateList needs API 23; minSdk is 24.

| File | Contents |
|---|---|
| `btn_primary_container.xml` | disabled → `?attr/colorPrimary` @ 0.38; default → `?attr/colorPrimary` |
| `btn_primary_content.xml` | disabled → `?attr/colorOnPrimary` @ 0.38; default → `?attr/colorOnPrimary` |
| `btn_secondary_container.xml` | disabled → `?attr/colorSurfaceContainerHighest` @ 0.38; default → `?attr/colorSurfaceContainerHighest` |
| `btn_secondary_content.xml` | disabled → `?attr/colorOnSurface` @ 0.38; default → `?attr/colorOnSurface` |
| `btn_tertiary_content.xml` | disabled → `?attr/colorPrimary` @ 0.38; default → `?attr/colorPrimary` |
| `btn_destructive_container.xml` | disabled → `?attr/colorError` @ 0.38; default → `?attr/colorError` |
| `btn_destructive_content.xml` | disabled → `?attr/colorOnError` @ 0.38; default → `?attr/colorOnError` |
| `btn_icon_glyph.xml` | disabled → `?attr/colorOnSurfaceVariant` @ 0.38; default → `?attr/colorOnSurfaceVariant` |
| `btn_focus_outer_accent.xml` | `state_focused` → `?attr/colorPrimary`; default → `@android:color/transparent` |
| `btn_focus_inner_on_accent.xml` | `state_focused` → `?attr/colorOnPrimary` @ 0.40; default → transparent |
| `btn_focus_inner_on_error.xml` | `state_focused` → `?attr/colorOnError` @ 0.40; default → transparent |
| `ripple_accent.xml` / `ripple_on_accent.xml` / `ripple_neutral.xml` | pressed → the base colour @ 0.12, focused → @ 0.10, hovered → @ 0.08 |

Example, in full, so the pattern is unambiguous:

```xml
<!-- res/color/btn_primary_container.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:alpha="0.38" android:color="?attr/colorPrimary" />
    <item android:color="?attr/colorPrimary" />
</selector>
```

**`res/anim/press_scale.xml`** is rewritten to R4 (0.97, 90 `ease_out_quart` in, 160
`ease_out_quint` out). **`res/anim/nav_press.xml` is deleted.**

#### Desktop - `Assets/GlobalStyles.axaml`

Class selectors, replacing the current set:

| Class | Targets | Pseudo-classes it defines |
|---|---|---|
| `Button.Primary` | `Button` | `:pointerover`, `:pressed`, `:disabled`, `:focus-visible` (FocusAdorner), `.Tall` |
| `Button.Secondary` | `Button` | `:pointerover`, `:pressed`, `:disabled`, `:focus-visible` |
| `Button.Tertiary` | `Button` | `:pointerover`, `:pressed`, `:disabled`, `:focus-visible` |
| `Button.Destructive` | `Button` | `:pointerover`, `:pressed`, `:disabled`, `:focus-visible` |
| `Button.Icon` | `Button` | `:pointerover`, `:pressed`, `:disabled`, `:focus-visible`, `.Filled`, `.Accent`, `.Danger` |

Structure of each, with the asymmetric press transition from R4:

```xml
<Style Selector="Button.Primary">
  <Setter Property="RenderTransformOrigin" Value="50%,50%" />   <!-- must be relative; see the note at GlobalStyles.axaml:374 -->
  <Setter Property="Background"   Value="{DynamicResource Brush.Accent}" />
  <Setter Property="Foreground"   Value="{DynamicResource Brush.OnAccent}" />
  <Setter Property="CornerRadius" Value="{DynamicResource Radius.Button}" />
  <Setter Property="MinHeight"    Value="{DynamicResource Size.Btn}" />
  <Setter Property="MinWidth"     Value="{DynamicResource Size.BtnMinWidth}" />
  <Setter Property="Padding"      Value="24,0" />
  <Setter Property="FontFamily"   Value="{DynamicResource Font.Grotesk}" />
  <Setter Property="FontWeight"   Value="Bold" />
  <Setter Property="FontSize"     Value="16" />
  <Setter Property="Cursor"       Value="Hand" />
  <Setter Property="Transitions">                               <!-- release: 160 OutQuint -->
    <Transitions>
      <TransformOperationsTransition Property="RenderTransform" Duration="0:0:0.16" Easing="{StaticResource Ease.OutQuint}" />
    </Transitions>
  </Setter>
</Style>
<Style Selector="Button.Primary:pressed">                        <!-- press: 90 OutQuart -->
  <Setter Property="RenderTransform" Value="scale(0.97)" />
  <Setter Property="Transitions">
    <Transitions>
      <TransformOperationsTransition Property="RenderTransform" Duration="0:0:0.09" Easing="{StaticResource Ease.OutQuart}" />
    </Transitions>
  </Setter>
</Style>
```

The `:pointerover` and `:pressed` background overrides stay on
`/template/ ContentPresenter#PART_ContentPresenter`, because without them Semi.Avalonia's own
`ButtonDefaultPointeroverBackground` **lightens** the CTA - the bug the current comment block
documents. That defence is kept, with the raw hex replaced by
`{DynamicResource Brush.AccentHover}` / `{DynamicResource Brush.AccentPressed}`.

`FontSize="16"` replaces 15 everywhere (R3). `MinHeight` replaces `Height` everywhere (R2).

---

## 3. Icon button

Covered as a button variant in §2 Buttons, because it is one. What it additionally owns:

**Anatomy**: one 22dp `PathIcon` / vector drawable, optically centred in a square box. No label, no
badge, no chevron. If it needs a label it is a Tertiary button.

**Geometry**: Android **48×48** hit box, desktop **40×40** (`Size.IconButton`), glyph 22 in both.
The visual weight difference is intentional and is the `00-rules.md` §13 translation table's rule: touch needs
48, pointer needs 40. Two adjacent icon buttons sit `space_8` 8 apart.

**Where it may live**: a toolbar's single trailing action, a row's trailing slot, a card header's
overflow, a stepper pair, a sheet's close affordance. **Never** as the only affordance on a
destructive row (§8.6 Row.Destructive).

**Accessible name is mandatory** and is the action, not the object: «Обновить список», «Отвязать
устройство», «Скопировать код», «Ещё», not «Иконка» and not the glyph name.

**The `✕` problem.** `layout_home_account.xml:78` uses the *text character* `✕` in a `TextView` at
`textSize="16sp"` as a close button. That is a dingbat used as chrome (§1.4.4, §10.4). It becomes
`Widget.Departament.Button.Icon` with `@drawable/ic_close_24`.

---

## 4. Text field

Today: **58 completely unstyled `EditText`s** and **118 Semi-default `TextBox`es**. §7.4's contract
(label above, helper slot, blur validation, error below, autofill, password toggle) is satisfied by
**4 of 199 fields product-wide**.

### 4.1 Anatomy, top to bottom

```
Label            Subtitle 13/400 onSurfaceVariant, always visible, sentence case, no colon
  space_8
Field            56 min-height, radius 12, colorSurfaceContainerHighest fill, 1dp colorOutline border
                 [ 16 pad ][ leading glyph 20 (optional) ][ 12 ][ text 16sp onSurface ][ 12 ][ trailing icon button 40 (optional) ][ 8 ]
  space_4
Helper / error   Caption 12/400, one line reserved even when empty so the layout never jumps
```

The **label is never the placeholder**. A placeholder, when present, is an example of the value
(«например, 500»), is `colorOnSurfaceVariant` (6.99:1, which clears the §14.1 placeholder floor of
4.5:1), and disappears on the first keystroke. `dialog_top_up.xml`'s hint-as-label is the current
violation.

### 4.2 Geometry and type

| Property | Value |
|---|---|
| Min height | `field_min_height` 56 (R10) |
| Radius | `radius_chip` 12 |
| Horizontal padding | `space_16` 16 |
| Fill | `?attr/colorSurfaceContainerHighest` / `Brush.SurfaceHighest` |
| Border | 1dp `?attr/colorOutline` / `Brush.Outline` (2dp when focused or errored) |
| Input text | 16sp, system face, `colorOnSurface`; **Numeric role** for any field that takes digits (amount, port, HWID) |
| Label | `TextAppearance.App.Subtitle` / `TextBlock.Subtitle` |
| Helper / error | `TextAppearance.App.Caption` / `TextBlock.Caption` |
| Multi-line | grows to a maximum of 5 lines then scrolls internally |

### 4.3 States

| State | Border | Fill | Label | Helper |
|---|---|---|---|---|
| Default | 1dp `colorOutline` | `colorSurfaceContainerHighest` | `colorOnSurfaceVariant` | `colorOnSurfaceVariant` |
| Hover (desktop) | 1dp `colorOnSurfaceVariant` | + `Brush.Hover` | unchanged | unchanged |
| Focused | **2dp `colorPrimary`** + outer 2px accent ring on desktop | unchanged | `colorPrimary` | unchanged |
| Filled (has a value) | 1dp `colorOutline` | unchanged | unchanged | unchanged |
| Error | **2dp `colorError`** | unchanged | `Brush.RedText` `#FF6069` | error text in `Brush.RedText` |
| Disabled | 1dp `colorOutline` @ 38% | @ 38% | @ 38% | @ 38% |
| Read-only | no border, no fill | transparent | unchanged | unchanged |

Validation is on **blur**, never per keystroke (exception: password strength). After a failed submit,
focus moves to the first invalid field and the screen scrolls it into view. Error copy follows §9.4:
cause + fix, no codes.

### 4.4 Sub-parts

- **Password**: trailing `Button.Icon` toggling `ic_eye` / `ic_eye_off`, `contentDescription`
  «Показать пароль» / «Скрыть пароль». `android:inputType="textPassword"` +
  `android:autofillHints="password"`.
- **Search**: the same field with a leading 20dp `ic_search` glyph and a trailing clear
  `Button.Icon` that appears only when non-empty. Radius 12 (this replaces `bg_search_pill`'s 14
  and `Radius.Search`). Filters in place; never navigates. Ctrl+F focuses it on desktop.
- **Amount**: leading nothing, trailing a static `₽` in `TextBlock.Numeric` `colorOnSurfaceVariant`,
  `inputType="numberDecimal"`, `imeOptions="actionDone"`.
- **Stepper-backed numeric** (extra devices): field replaced by `Button.Icon.Filled` `−`, a
  `TextAppearance.App.Numeric` count with `minWidth` 32dp, `Button.Icon.Filled` `+`. Disabled ends
  use R6's 0.38, not the current imperative `alpha = 0.4f`.

### 4.5 Platform mapping

| | Android | Desktop |
|---|---|---|
| Style / theme | `@style/Widget.Departament.TextField` (parent `Widget.Material3.TextInputLayout.FilledBox`), wired as `textInputStyle` in `themes.xml` | `ControlTheme` `TextBox.Incy`, applied via `Theme="{StaticResource TextBox.Incy}"` **and** as the implicit `TextBox` theme so nothing can fall through |
| Inner control | `TextInputEditText` with `@style/Widget.Departament.TextField.EditText` | n/a |
| Label | `app:hintEnabled="false"` + a real `TextView` above, so the label never floats into the border | `TextBlock.Subtitle` above |
| Error | `app:errorEnabled="true"`, `app:errorTextAppearance` → Caption in `@color/ping_bad` | `TextBlock.Caption` bound to the VM's error, `Brush.RedText` |
| Shape | `shapeAppearanceOverlay` → 12dp all corners | `CornerRadius` `Radius.Chip` |
| Focus | `app:boxStrokeWidthFocused="2dp"`, `app:boxStrokeColor` state list | `FocusAdorner` 2px accent, radius 14 |

`TextBox.IncyField` (44px) is **deleted**; its 3 call sites take `TextBox.Incy`. The 58 bare Android
`EditText`s each become a `TextInputLayout` + `TextInputEditText` pair with a real label, the correct
`inputType`, and `autofillHints` where one exists.

---

## 5. Select

Not a text field, and **never a `Spinner`** (§11.2 names it as forbidden). 15 `Spinner`s and 4
`AutoCompleteTextView`s on Android; 66 Semi-default `ComboBox`es on desktop.

**Anatomy**: a **value row** (§8.3 Row.Value) whose trailing value is the current selection and whose trailing
affordance is a 20dp chevron. Tapping it opens the choice surface.

| Choice count | Android | Desktop |
|---|---|---|
| 2-4, and the options are short | **Segmented control inline** (§6 Segmented control) - no surface opens at all | same |
| 5+ | `BottomSheetDialogFragment` list, one `Row.Value` per option with a trailing 20dp check on the current one | `Flyout` (`IncyFlyoutTheme`) anchored to the row, same list |
| 12+ | the same sheet with a search field pinned at the top | the same flyout with a search field |

The choice surface uses the **same row component** as everything else. Selection is confirmed
immediately and the surface closes; there is no OK button. Esc / Back closes without changing the
value. Focus returns to the row.

Android style: `@style/Widget.Departament.Sheet` (§13 Bottom sheet). Desktop: `MenuFlyout` is not used - a
`Flyout` hosting an `ItemsControl` of `Border.Row` is, so the rows are ours and not Fluent's.

---

## 6. Segmented control

Replaces: Android's 5 outlined `btn_mem_*` buttons in a `MaterialButtonToggleGroup`
(`activity_local_proxy.xml`), desktop's `ToggleButton.Segment` (4), `Button.SegItem` (2, local to
`LoginView`) and `Button.MethodChip` (1, local to `AccountView`). **12 instances → 1 component.**

**Use it for 2-4 mutually exclusive options with short labels.** More than 4, or labels longer than
~10 characters, means a Select (§5 Select).

### 6.1 Anatomy and geometry

```
[ track: radius 16, colorSurfaceContainerHighest, padding 4 ]
  [ segment 1 ][ 4 ][ segment 2 ][ 4 ][ segment 3 ]
```

| Property | Value |
|---|---|
| Track height | 48 (`btn_height`) - so a segmented control and a button on the same row align |
| Track radius | `radius_button` 16 |
| Track fill | `?attr/colorSurfaceContainerHighest` / `Brush.SurfaceHighest` |
| Track padding | `space_4` 4 |
| Segment height | 40 |
| Segment radius | `radius_chip` 12 |
| Segment gap | `space_4` 4 |
| Segment min width | 64 |
| Segment padding | `space_12` 12 horizontal |
| Label | `TextAppearance.App.Title.Medium` 16/500 unselected → **700 selected** |

### 6.2 States

| State | Segment fill | Label |
|---|---|---|
| Unselected | transparent | `colorOnSurfaceVariant` `#9BA1AD`, weight 500 |
| Unselected + hover (desktop) | `Brush.Hover` | `colorOnSurface` |
| Unselected + pressed | `Brush.Hover` | unchanged, `scale(0.97)` |
| **Selected** | `?attr/colorPrimaryContainer` `#17325C` | `?attr/colorOnPrimaryContainer` `#CFE0FF` (**9.57:1**), weight **700** |
| Selected + hover | unchanged | unchanged - a selected segment does not react to hover |
| Focused | unchanged | unchanged + outer 2dp accent ring, radius 14 |
| Disabled (whole control) | track and segments @ 38% | @ 38% |

Selection reads on **three** axes (fill, colour, weight), so it survives the mono theme and
colour-blindness. The fill crossfades over `motion_state` 220ms `ease_standard`; the label weight
snaps (a weight tween is not available and not wanted). **The selected fill does not slide** - a
sliding thumb needs a shared layer and is a per-item animation that stagger rules do not cover; the
crossfade is the whole motion.

### 6.3 Platform mapping

| | Android | Desktop |
|---|---|---|
| Container | `MaterialButtonToggleGroup` with `android:background="@drawable/bg_segment_track"`, `android:padding="@dimen/space_4"`, `app:singleSelection="true"`, `app:selectionRequired="true"`, `app:spacing="@dimen/space_4"`, `app:innerCornerSize="@dimen/radius_chip"` | `Border.SegmentTrack` wrapping a horizontal `StackPanel` with `Spacing="{StaticResource Space.4}"` |
| Item | `@style/Widget.Departament.Segment` (parent `Widget.Material3.Button.TextButton`), `checkable` | `ToggleButton.Segment` |
| Selected state | `android:checked` → `@color/segment_container` / `@color/segment_content` ColorStateLists + `app:textAppearanceActive` | `:checked` |
| Drawable | `res/drawable/bg_segment_track.xml` - solid `?attr/colorSurfaceContainerHighest`, corners 16 | inline setters |

`Size.SegmentChip` 44 is retired in favour of 48/40.

---

## 7. Switch

Android keeps `MaterialSwitch` - the M3 component - because the alternative is the iOS tell that
`android.md` names verbatim and that `30-reference-analysis.md` already ruled **REFUSE**. The desktop
mirrors M3's geometry so the two look like one product; the current `ToggleSwitch.iOS` (52×32, fixed
26 knob, no icon) is renamed and reshaped.

### 7.1 Geometry

| Part | Value |
|---|---|
| Track | 52 × 32, fully round (radius 16) |
| Track border (off) | 2dp `?attr/colorOutline` / `Brush.Outline` |
| Thumb, off | 16 diameter, centred, `?attr/colorOnSurfaceVariant` `#9BA1AD` (6.00:1 on the off track - visible, which the M3 default `colorOutline` is not on our ramp) |
| Thumb, on | 24 diameter, `?attr/colorOnPrimary` `#00183A` |
| Thumb, pressed | 28 diameter, either state |
| Check glyph, on | 16dp `ic_check`, `?attr/colorPrimary` inside the thumb |
| Hit box | 48 × 48 minimum on Android; the **row** is the target, the switch is decoration (§8.5 Row.Toggle) |

### 7.2 States

| State | Track | Thumb |
|---|---|---|
| Off | `?attr/colorSurfaceContainerHighest` `#20242B` + 2dp `colorOutline` | 16dp `colorOnSurfaceVariant` |
| Off + hover (desktop) | + `Brush.Hover` | unchanged |
| Off + pressed | unchanged | 28dp |
| On | `?attr/colorPrimary` `#4C8DFF`, no border | 24dp `colorOnPrimary` + accent check |
| On + pressed | unchanged | 28dp |
| Focused | unchanged | + outer 2dp accent ring around the track, radius 18 |
| Disabled | track and thumb @ 38% | @ 38% |
| Indeterminate / pending | **does not exist.** A switch that is waiting on the network shows the row's inline 20dp spinner in place of the switch and is not interactive |

Motion: thumb translation and size over `motion_state` 220ms `ease_standard`; track colour
crossfades over the same. Reduced motion snaps.

### 7.3 Platform mapping

| | Android | Desktop |
|---|---|---|
| Control | `MaterialSwitch`, `@style/Widget.Departament.Switch`, wired as `materialSwitchStyle` | `ToggleSwitch`, `ControlTheme` **`ToggleSwitch.Incy`** (renamed from `ToggleSwitch.iOS`, geometry changed to match M3) |
| Colours | `app:trackTint`/`app:trackDecorationTint`/`app:thumbTint` ColorStateLists in `res/color/switch_*.xml` | `/template/ Border#track`, `Ellipse#knobFill` setters |
| Row ownership | `android:clickable="false"` + `android:focusable="false"` on the switch; the row toggles it | `IsHitTestVisible="False"`; the row's `Tapped` toggles it |

**The `clickable="false"` rule is not optional.** Today only the dead `layout_setting_toggle_row.xml`
sets it; the 5 live switch rows in `layout_settings_content.xml` do not, so each has two independent
hit targets that can disagree.

`SimpleToggleSwitch` (3 desktop instances) and the 43 Semi-default `ToggleSwitch`es are migrated.

---

## 8. Row - five archetypes

The single most-repeated element in the product: **137 instances** collapse here. Android has two
reusable row layouts with **zero call sites** and 23 hand-inlined copies of the same structure in one
file.

### 8.1 The universal geometry (all five archetypes)

```
[ 16 gutter ][ 40 tile, r12, 22 glyph ][ 12 ][ text column, weight 1 ][ 12 ][ trailing ][ 16 gutter ]
                                              Title    16/700 onSurface, max 2 lines
                                              Subtitle 13/400 onSurfaceVariant, max 2 lines
```

| Property | Value |
|---|---|
| Min height | `row_min_height` 56 - grows with a 2-line subtitle, never clips |
| Vertical padding | `space_12` 12 |
| Horizontal padding | `screen_gutter` 16 |
| **Text origin** | **68** from the row's leading edge (16 + 40 + 12). Three origins exist today (64, 68, 72); this is the one |
| **Divider inset** | **68**, matching the text origin. `72dp` in `activity_account.xml`, `layout_settings_content.xml` and `AccountView.axaml:1124,1172,1240,1319` is the current debt |
| Divider | 1dp `?attr/colorOutlineVariant` `#20242B`, **between** rows only - never above the first or below the last |
| Tile | 40 `tile_size`, radius 12, **neutral by default** (`@color/icon_tile_neutral` fill, `@color/icon_glyph_neutral` glyph) |
| Trailing | **exactly one** of: chevron 20dp, switch, value text, `Button.Icon`, chip. Never two |
| Title | `TextAppearance.App.Title` / `TextBlock.Title` 16/700 - this is the D4 fix; settings rows are 14/400 today, 23 times |
| Subtitle | `TextAppearance.App.Subtitle` / `TextBlock.Subtitle` 13/400 |
| Value | `TextAppearance.App.Subtitle` 13/400 `colorOnSurfaceVariant`; the **Numeric** role when it is a number |

The whole row is the target. Press feedback is the **background step** per R5, not a scale.

### 8.2 Row.Navigation

Goes somewhere. Trailing: 20dp `ic_chevron_right` in `colorOnSurfaceVariant`. Optional value text
before the chevron.

Chevron size today: **18dp ×32, 20dp ×1, 22dp ×1 on Android; 16, 18, 22 on desktop.** One size: **20**.

### 8.3 Row.Value

Shows a value and opens a Select (§5 Select) or a field. Trailing: value text, then a 20dp chevron.
Every settings row shows its **current value** - this is a `30-reference-analysis.md` **TAKE** and it
is what makes a settings list readable without opening anything.

### 8.4 Row.Action

Performs an action in place. Trailing: a **Tertiary button** (the only place a Tertiary lives inside
a row) or a `Button.Icon`. When the trailing control is the action, **the row itself is not
clickable** and does not show press feedback - two targets that do different things is the
`layout_subscription_meta_bar` mistake.

Examples with their real copy: «Привязать Telegram» → Tertiary «Привязать»; «Реферальный код» →
value `ABC123` in the Numeric role + `Button.Icon` «Скопировать код».

### 8.5 Row.Toggle

Trailing: `MaterialSwitch` / `ToggleSwitch.Incy`, non-interactive (§7.3). The row owns the toggle.
Subtitle states what is on when it is on («Автопродление подписки»), never a negation.

### 8.6 Row.Destructive

Title in `Brush.RedText` / `@color/ping_bad` `#FF6069` (6.15:1 - **not** `#F04452`, 4.88:1). Tile
stays **neutral**; a red tile plus red text is the same signal twice and turns the row into an alarm.
Trailing: nothing, or a 20dp chevron if it opens a confirm.

Per §7.5, the default is **act + undo snackbar**, not a confirm dialog. A dialog is used only when
the action is irreversible and costly (delete the account, wipe every subscription). Unlinking a
device is **reversible** - the device re-registers on the next connect - so it takes the undo path,
not the current in-view modal.

Rows: «Выйти» (sign out - undo not applicable, so a confirm; the confirm's primary is «Выйти», not
«OK»), «Удалить сервер», «Сбросить настройки».

### 8.7 Platform mapping

| | Android | Desktop |
|---|---|---|
| Layout | `res/layout/row_navigation.xml`, `row_value.xml`, `row_action.xml`, `row_toggle.xml`, `row_destructive.xml` - **five files, included with `<include>` or inflated by an adapter.** `layout_setting_row.xml` and `layout_setting_toggle_row.xml` are replaced by these and deleted | `Border.Row`, `Border.Row.Value`, `Border.Row.Action`, `Border.Row.Toggle`, `Border.Row.Destructive` |
| Background | `@drawable/bg_row` - `<ripple>` over a `<selector>` whose `state_pressed` is `?attr/colorSurfaceContainerHigh` | `Background` setter + `:pointerover` → `Brush.Hover`, `.pressed` → `Brush.SurfaceHigh` |
| Divider | `@drawable/divider_row` 1dp inset 68, applied by the group container, not by the row | `Border.RowDivider`, height 1, `Margin="68,0,0,0"` |
| Cursor | n/a | `Hand` |
| Focus | `android:focusable="true"` + `@drawable/bg_row` `state_focused` → 2dp accent inset ring | `FocusAdorner` outer 2px accent, radius 14 |
| Group | `Widget.Departament.Card` with `padding 0` and `clipToOutline`, rows inside | `Border.Card` `Padding="0"` `ClipToBounds="True"` |

`Border.SettingRow`, `Border.ServerRow` (which becomes §18 Selection indicator), `Button.MeterRow` and the 99 hand-rolled
clickable `LinearLayout`s all map into these five.

---

## 9. Card / surface

The container rules are already law in §4.4 of `00-rules.md`; this section fixes them into one
component.

**A card is allowed only when all three hold**: it is a distinct object the user acts on as a unit;
it needs a boundary spacing cannot give; and it is not inside another card. A settings screen is
rows, not cards. A payment history is a divided list, not a card grid - the current 
"N identical rounded rectangles that do nothing" is the §2.4.3 uniform-card tell on both platforms.

| Property | Value |
|---|---|
| Fill | `?attr/colorSurface` / `Brush.Surface` `#141619` |
| Radius | `radius_card` 20 |
| Border | 1dp `?attr/colorOutlineVariant` `#20242B` |
| Padding | `space_16` 16, or **0** when the card is a row group |
| Elevation / shadow | **0 / none.** Depth is the surface ramp |
| Gap between cards | `space_12` 12; between a card and a section header above it, `space_24` 24 |

**Pressable card** (server row, subscription card, account chip, tariff card): adds `press_scale`
0.97 (R5), `:pointerover` → `Brush.SurfaceHigh`, a focus ring at radius 22, and a `Cursor=Hand`.

**Nested cards are a defect**, with two named current offenders: the Buy screen's price-option
`Border`/drawable inside the tariff card, on **both** platforms. Fix: the price options become plain
rows separated by hairlines inside the tariff card, with the selected one carrying the §18 selection
treatment - no second border, no radius change, no stroke-width change.

Android: `@style/Widget.Departament.Card` (parent `Widget.Material3.CardView.Outlined`) wired as
`materialCardViewStyle`, `cardElevation` 0, `strokeWidth` 1dp, `cardCornerRadius`
`@dimen/radius_card`. This retires the 25 raw `20dp` literals, the 2 `16dp`, the 1 `18dp` and the 1
`88dp`.
Desktop: `Border.Card`, already correct, plus a new `Border.Card.Pressable`.

---

## 10. Chip

Android has **no `Chip` component at all** - five `TextView`s wearing shape drawables do the job,
one of them with `2dp` vertical padding. Desktop has three `Border` classes.

**One component, four semantic classes.** Chips are **labels, not controls** - a chip is never
clickable. A clickable pill is a Tertiary button or a Segment.

| Property | Value |
|---|---|
| Min height | 24 |
| Padding | `space_8` 8 horizontal, `space_4` 4 vertical |
| Radius | `radius_chip` 12 |
| Label | `TextAppearance.App.Chip` / `TextBlock.Chip` - Space Grotesk 11/500, tracking 0.04, sentence case |
| Optional leading glyph | 16dp, `space_4` 4 gap |
| Max width | 160; longer content ellipsises at the end |

| Class | Fill | Text | Used for |
|---|---|---|---|
| `Chip.Neutral` | `?attr/colorSurfaceContainerHighest` `#20242B` | `colorOnSurfaceVariant` (6.00:1) | Protocol, transport, «Отменён», «Это устройство» |
| `Chip.Accent` | `?attr/colorPrimaryContainer` `#17325C` | `colorOnPrimaryContainer` `#CFE0FF` (9.57:1) | **The tariff badge, and nothing else** |
| `Chip.Status.Ok` | `colorTertiary` @ 18% | `?attr/colorTertiary` `#22C55E` (7.95:1) | «Оплачено», «Активна», «Подключено» |
| `Chip.Status.Warn` | `#EAB308` @ 18% | `#EAB308` | «В обработке», «Истекает» |
| `Chip.Status.Error` | `colorError` @ 18% | `@color/ping_bad` `#FF6069` (6.15:1) | «Ошибка», «Истекла» |

R12 in force: there is no fourth status hue. «Отменён» is `Chip.Neutral`. Subscription health uses
`Chip.Status.*`, not the payment classes.

**Colour is never the only signal** (§6.3): every status chip carries the word. A bare coloured dot
next to a label that already says the state is decoration and gets deleted.

| | Android | Desktop |
|---|---|---|
| Control | `com.google.android.material.chip.Chip`, `@style/Widget.Departament.Chip` (parent `Widget.Material3.Chip.Assist`), `android:clickable="false"`, `app:chipMinHeight="24dp"`, `app:chipStartPadding`/`EndPadding` 8dp, `app:chipCornerRadius` 12dp, `app:chipStrokeWidth` 0dp | `Border.Chip` + `.Accent` / `.Status.Ok` / `.Status.Warn` / `.Status.Error` |
| Retires | `bg_acc_chip`, `bg_acc_badge`, `bg_type_chip`, `bg_chip_gold` (dead), `bg_speed_chip` (dead) | `Border.ChipBadge` (padding 10,4 - off-scale), `Border.StatusChip` + 4 classes, `Border.ProtocolChip` (padding 8,2) |

---

## 11. Tab bar (top-level navigation)

Android ships a hand-rolled `LinearLayout` bottom bar; desktop ships **two complete, independent
implementations** (`Button.NavRailItem` with `.active`, `Button.BottomNavItem` with `.sel`). One
component, one active-class name, both platforms.

**Destinations: 4, in this order, with these labels** - the D3 fix (desktop is missing Servers):

| # | Label | Glyph |
|---|---|---|
| 1 | Главная | `ic_nav_home` / `Geo.Nav.Home` |
| 2 | Серверы | `ic_nav_servers` / `Geo.Nav.Servers` |
| 3 | Настройки | `ic_nav_settings` / `Geo.Nav.Settings` |
| 4 | Аккаунт | `ic_nav_account` / `Geo.Nav.Account` |

### 11.1 Geometry and states

| Property | Value |
|---|---|
| Bar height | 56 + the bottom system inset (Android) / 56 (desktop compact) |
| Rail item | 76 × 64 (desktop wide, and Android at `sw600dp`) |
| Glyph | **24** |
| Label | `@style/BottomNavLabel` / 11px, weight **500 inactive → 700 active**, always visible |
| Active indicator | 64 × 34, `?attr/colorPrimaryContainer`, fully round. This is the M3 navigation indicator and keeps its native stadium shape under R1's "tracks" carve-out |
| Gap glyph → label | `space_4` 4 |

| State | Glyph | Label | Indicator |
|---|---|---|---|
| Inactive | `colorOnSurfaceVariant`, outline glyph | `colorOnSurfaceVariant`, 500 | none |
| Inactive + hover (desktop) | `Brush.OnSurfaceVariantHover` | same | none |
| Pressed | unchanged | unchanged | none - **no ripple, no glow, no scale** (owner request §0.4.8) |
| **Active** | `colorPrimary`, **filled** glyph | `colorPrimary`, **700** | visible, crossfade `motion_state` 220 `ease_standard`; on desktop the rail's single shared indicator slides 220 `Ease.OutQuint` |
| Focused | unchanged | unchanged | outer 2dp accent ring around the item, radius 14 |
| Disabled | **does not exist.** A destination that is unavailable is not shown | | |

Three axes on the active state (colour, weight, fill/indicator), so it survives mono.

### 11.2 Platform mapping

| | Android | Desktop |
|---|---|---|
| Control | `BottomNavigationView` with `@style/Widget.Departament.NavigationBar`; at `sw600dp` `NavigationRailView` with `@style/Widget.Departament.NavigationRail` | `Button.NavItem` with `.rail` / `.bar` size modifiers and **`.active`** |
| No-ripple | `app:itemRippleColor="@android:color/transparent"` | no ripple exists |
| Indicator | `app:itemActiveIndicatorStyle="@style/BottomNavIndicator"` - which is **dead code today** and comes alive here | one shared `Border#railIndicator` moved in `MainWindow.axaml.cs`; the per-item 34×3 pill in `BottomNavBar.axaml` is deleted |
| Labels | `app:labelVisibilityMode="labeled"`, `app:itemTextAppearanceActive/Inactive` | `TextBlock` inside the button |
| Colours | `res/color/nav_item_color.xml` (the existing `bottom_nav_item_color.xml`, revived and renamed) | setters |
| Deleted | the `LinearLayout id=bottom_nav` in `activity_main.xml:526` and its 4 hand-rolled items; `@anim/nav_press` | `Button.BottomNavItem` and its 14 local style rules |

The `.sel` class name disappears; `.active` is the one name.

---

## 12. Toolbar / header

Owner request §0.4.6, and the D11 fix: **every sub-page in the app currently renders its title in
the 20sp brand wordmark face** because both toolbars set `titleTextAppearance="@style/ToolbarBrandTitle"`.

### 12.1 Two headers only

**A. Sub-page toolbar (seamless).**

```
[ 16 ][ Button.Icon back, 24 glyph ][ 16 ][ Title 16/700 ][ flex ][ Button.Icon (0-1) ][ 16 ]
```

| Property | Value |
|---|---|
| Height | 56 (`toolbar_height` / `Size.SubToolbar`) |
| Background | `?android:attr/colorBackground` / `Brush.Bg` - **the page background**, not a bar colour |
| Elevation / divider / shadow | **none** |
| Title | `TextAppearance.App.Title` / `TextBlock.Title` 16/700, one line, ellipsise at the end |
| Back glyph | 24dp `ic_arrow_back`, `contentDescription` «Назад» |
| Trailing | at most **one** `Button.Icon`; a second goes into an overflow (`Button.Icon` + `ic_more`) |
| On scroll | nothing changes. If a boundary is genuinely needed, a 1dp `colorOutlineVariant` hairline fades in over `motion_state` 220ms - and that is the only permitted variant |

**B. Home wordmark header.** The only place `@style/ToolbarBrandTitle` (20sp/700) survives: the
brand on the first tab. Not on sub-pages, not on `activity_base.xml`.

### 12.2 Platform mapping

| | Android | Desktop |
|---|---|---|
| Control | `MaterialToolbar`, `@style/Widget.Departament.Toolbar` wired as `toolbarStyle`; `android:background="?android:attr/colorBackground"`, `app:elevation="0dp"`, `android:layout_height="@dimen/toolbar_height"` (56, replacing `?attr/actionBarSize`), `app:contentInsetStartWithNavigation="0dp"`, `app:titleTextAppearance="@style/TextAppearance.App.Title"` | `Border.SubToolbar` height `Size.SubToolbar` 56 + `Button.Icon` + `TextBlock.Title` |
| Back | system Back / predictive Back **and** the button | the button + **Esc** + mouse button 4 |
| Fixes | `activity_base.xml` and every sub-page stop using the wordmark | `BuyView`, `DevicesView`, `PaymentHistoryView` stop using `Classes="Headline"` (24px) for the title |

---

## 13. Bottom sheet / dialog

Order of preference is law (§7.6): **inline > expandable row > sheet (Android) / flyout (desktop) >
dialog.** A dialog is the last resort.

### 13.1 Bottom sheet (Android) / Flyout (desktop)

The per-item action surface.

| Property | Android | Desktop |
|---|---|---|
| Shape | `radius_sheet` 24 top only | `Radius.Card` 20, all corners |
| Fill | `?attr/colorSurface` `#141619` | `Brush.SurfaceHigh` `#1A1D21` - one step above the card it sits over |
| Border | none | 1px `Brush.OutlineVariant` |
| Handle | 36 × 4, `colorSurfaceContainerHighest`, radius pill, `space_12` 12 above and below | none (a flyout has no drag) |
| Scrim | `?attr/colorScrim` @ 60% | `Brush.Scrim` (60%) only for the modal card, not for a flyout |
| Title | `TextAppearance.App.Title` 16/700 at the gutter, `space_16` 16 below | same |
| Content | Rows (§8 Row) at the full width, dividers inset 68 | same |
| Enter | slide + fade, `motion_reveal` 300 `ease_out_quint` | fade + 8px rise, `Dur.Reveal` 300 |
| Exit | 225ms (75% of enter) `ease_standard` | `Dur.Exit` 150 |
| Dismiss | scrim tap, drag down, system Back | Esc, click-away |
| Focus | moves into the sheet on open, returns to the trigger on close | same |

**The desktop's bottom sheet is deleted.** `BuyView.axaml`'s window-bottom payment sheet is a phone
idiom in a 900×600 window (§13 translation table); it becomes a flyout anchored to the pay button.
This also fixes the "one decision, two grammars" defect: the payment-method choice is **one
component everywhere** - a flyout/sheet list of rows - and the inline «С баланса» / «Картой» button
pairs on the Account card are removed.

Android: `@style/Widget.Departament.Sheet` + `ThemeOverlay.Departament.BottomSheet`
(`bottomSheetStyle`, `android:windowIsFloating=false`, `behavior_fitToContents=true`), body
`@drawable/bg_sheet_top`. Desktop: `IncyFlyoutTheme` (already correct at radius 20, padding 16) plus
`Border.SheetTop` retained **only** for the Android-parity mobile-width case.

### 13.2 Dialog

For an interrupting, irreversible decision only.

| Property | Value |
|---|---|
| Width | `min(360, screen − 2×24)` |
| Radius | `radius_card` 20 |
| Fill | `?attr/colorSurface` over a 60% scrim |
| Padding | 24 |
| Title | `TextAppearance.App.Title` 16/700 |
| Body | `TextAppearance.App.Body` 14/400, max ~60 characters per line |
| Actions | right-aligned, `space_8` 8 apart, **Tertiary (cancel) then the confirm**; the confirm is Primary, or Destructive when the action destroys |
| Copy | the confirm says what it does: «Удалить подписку», «Выйти». **Never «OK», never «Да»/«Нет»** |
| Auto-focus | the **neutral** action |
| Dismiss | Esc / Back / scrim tap = cancel |

Android: `MaterialAlertDialogBuilder` inheriting `ThemeOverlay.Departament.Dialog` (already wired via
three theme attrs); its `Departament.Dialog.Button` style is retargeted to
`Widget.Departament.Button.Tertiary` so dialog buttons are the same component as everywhere else.
Desktop: `Border.Card.Modal` centred on `Border.Scrim`, enter scale 0.96→1 + fade over
`Dur.State` 220.

**Two dialogs must not survive** (`21-account-survey.md` §1.6, §1.4.13): the end-user
«Ответ сервера (диагностика)» raw-response dialog, and the «Ошибка оплаты» dialog that prints
`HTTP %1$s` to a customer. Both violate §9.4's "no error codes visible to the user".

---

## 14. Snackbar / toast

Android: `Snackbar` only. **`Toast` is not used by new code** (§1.4.8); the current toasts for top-up
success, referral copy and avatar errors are actionable feedback and become snackbars.

| Property | Android | Desktop |
|---|---|---|
| Surface | `?attr/colorSurfaceContainerHigh` `#1A1D21` | `Brush.Toast.Bg` `#20242B` |
| Radius | `radius_card` 20 | 20 (currently `Radius.Pill` - a wide capsule, banned by R1) |
| Padding | 16 horizontal, 12 vertical | **16,12** (currently 22,12 - off-scale) |
| Position | bottom, 16 from the edges, **above** the bottom navigation and the system inset | bottom-centre, 24 from the bottom edge |
| Text | `TextAppearance.App.Body` 14/400 `colorOnSurface`, max 2 lines | `TextBlock.Body` |
| Action | `Widget.Departament.Button.Tertiary`, label in `colorPrimary`, one word: «Повторить», «Отменить» | `Button.Tertiary` |
| Duration | 5000ms with an action, 3000ms without | same |
| Enter | fade + 8dp rise, `motion_reveal` 300 `ease_out_quint` | `Dur.Reveal` 300 |
| Exit | 225ms `ease_standard` | `Dur.Exit` 150 |
| Stacking | one at a time; a new one replaces the current one | same |
| Reduced motion | appears and disappears with no translation | `Dur.Instant` |

Android: `@style/Widget.Departament.Snackbar` + `@style/Widget.Departament.Snackbar.TextView` +
`@style/Widget.Departament.Snackbar.Button`, wired via `snackbarStyle` / `snackbarTextViewStyle` /
`snackbarButtonStyle`. Desktop: `Border.Toast`.

**Undo is the destructive default** (§7.5): «Устройство отвязано» + «Отменить», 5 seconds, and the
row animates back if undone.

---

## 15. Empty state

Three different empty-state grammars exist in the Android app today (a card, a 64dp tile block, a
`drawableTop` on a `TextView`) and the desktop hard-codes a 64×64 tile in `PaymentHistoryView` where
a class already exists. One grammar.

```
        [ 64 tile, radius 20, colorSurfaceContainerHighest, 32dp glyph in colorOnSurfaceVariant ]
                                     space_16
                    Title      Headline 24/700 onSurface, centred, max 2 lines
                                     space_8
                    Line       Subtitle 13/400 onSurfaceVariant, centred, max ~60 chars, max 2 lines
                                     space_24
                    Action     one button
```

- The tile is **neutral**, not accent. An empty state is not the screen's primary action surface and
  does not spend the accent budget on decoration.
- The action is **Primary only when it is the screen's primary action**; otherwise Secondary.
- Copy is §9.5's formula: what is not here / why or what it gives you / one action.

| Screen | Title | Line | Action |
|---|---|---|---|
| No servers | `Нет серверов` | `Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.` | `Добавить провайдера` |
| Search empty | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| No subscription | `Подписки пока нет` | `Купите тариф, чтобы подключаться к серверам Departament.` | `Купить` |
| No payments | `Платежей пока нет` | `Здесь появится история покупок и продлений.` | none |
| No devices | `Устройств пока нет` | `Устройства появятся после первого подключения.` | none |
| Telegram not linked | `Telegram не привязан` | `Привяжите Telegram, чтобы управлять подпиской из бота.` | `Привязать Telegram` |

These strings replace «Оформите первую подписку» / «Оформи первую подписку» (§9.3 locks buying to
«Купить»), «Нет подключённых устройств», and the desktop's informal «ты» voice.

**Error state** is the same silhouette with: the alert glyph, the mapped cause (never a hard-wired
«Что-то пошло не так» in XML - `activity_account.xml` does this today while `messageFor()` maps five
real causes), and a **Tertiary «Повторить»**.

**Offline state** is not an error: the screen keeps its data, marks it stale, disables
network-dependent actions, and shows one quiet full-width bar at the top of the content:
`Нет сети. Показаны последние данные.` + Tertiary `Повторить`. Bar: `colorSurfaceContainerHigh`
fill, radius 12, 12 padding, Body text, no icon.

Android: `res/layout/layout_state_empty.xml` - **one** file, parameterised by the binding.
Desktop: `Border.EmptyIcon` (already exists, `Size.EmptyIcon` 64 / `Size.EmptyGlyph` 32) inside a
`StackPanel.EmptyState`.

---

## 16. Skeleton

Skeletons replace **every centred spinner over a blank screen** (§15). `activity_payment_history.xml`
is the current offender on Android; the desktop already has skeletons and copy-pastes them three
times in `PaymentHistoryView`.

**The rule that makes a skeleton work: it is the silhouette of the real content.** Same number of
blocks, same heights, same positions. The Buy screen's three flat 76dp blocks do not match the tariff
card and therefore still read as a pop when they swap.

| Part | Value |
|---|---|
| Bar | height 16, radius 12, `?attr/colorSurfaceContainerHighest` `#20242B` |
| Block / card | the real component's height and radius, filled `colorSurfaceContainerHighest`, no border |
| Widths | derived from the real content, not hand-picked to "fake variety" |
| Gaps | the real component's gaps |
| Pulse | opacity **0.45 ↔ 1.0**, `motion_pulse` **1000ms** each way, `ease_standard`, infinite reverse |
| Reduced motion | static at opacity **0.7** |
| Appears after | 300ms (§7.3) |
| Swap to content | `motion_state` 220ms crossfade, no layout change |

`motion_pulse` is a **new token** (see §20 Token additions) because the shipped 900ms
`AccelerateDecelerateInterpolator` on Android exists in no scale and the desktop's `SkeletonPulse`
has its own. It is documented as a loading indicator, not the §8.4 hero moment.

Android: `@style/Widget.Departament.Skeleton.Bar` / `.Block` + a shared
`View.startSkeletonPulse()` in `util/ControlState.kt` that honours `MotionUtils.animationsEnabled`.
Desktop: `Border.SkelBar` / `Border.SkelCard` + the existing
`:is(Window):not(.lite) :is(Control).SkeletonPulse` selector, retimed to 1000ms.

---

## 17. Progress

Three forms, no more.

### 17.1 Inline spinner (in a control)

20dp arc, 2dp stroke, 90° sweep, round caps, rotating 360° in **1100ms linear**, in the host's
foreground colour. Defined once per platform (§2.7 Loading). It appears inside buttons, inside a row where a
switch was, and inside a toolbar icon button.

Android needs three files, all specified:

- `res/drawable/ic_spinner_arc.xml` - 20dp vector, `<group android:name="rotor" android:pivotX="10" android:pivotY="10">` around a 90° arc path, `strokeWidth="2"`, `strokeLineCap="round"`, `strokeColor="#FFFFFF"` (tinted at use).
- `res/animator/spinner_rotate.xml` - `objectAnimator` on `rotation` 0→360, `duration="@integer/motion_spin"` 1100, `repeatCount="infinite"`, `interpolator="@android:interpolator/linear"`.
- `res/drawable/spinner_arc.xml` - `<animated-vector>` binding the two.

Desktop uses the existing `Ellipse.Spinner.spinning` (1.1s linear), with the literal moved to
`Motion.Dur.Spin`.

### 17.2 Determinate bar (traffic meter, download)

| Property | Value |
|---|---|
| Height | 6 |
| Radius | fully round (3) |
| Track | `?attr/colorSurfaceContainerHighest` `#20242B` |
| Fill | **solid** `?attr/colorPrimary` - **no gradient.** `TrafficFillBrush`'s `LinearGradientBrush` encodes nothing a solid fill does not (§6.5) |
| Width | fills its column; `Size.TrafficPill` 160 is retired as a fixed width |
| Value change | `motion_state` 220ms `ease_standard` on the fill width |
| Label | **beside** the bar, never on top of it - the current 11px label over a moving fill measures 2.9:1 |
| Over-quota | fill turns `?attr/colorError` at ≥100%, and the label says «Лимит исчерпан» |

Android: `LinearProgressIndicator`, `@style/Widget.Departament.Progress.Linear`
(`app:trackThickness="6dp"`, `app:trackCornerRadius="3dp"`, `app:indicatorColor="?attr/colorPrimary"`).
Desktop: `Border.Meter` (track) + `Border.Meter.Fill`, replacing `Border.TrafficPill` /
`.Fill` and `Radius.Traffic` 8.

### 17.3 The connect arc

Out of scope here: it is the product's single hero moment (§8.4) and belongs to the Home spec. It
uses `motion_emphasis` 600 and nothing else in the product may.

---

## 18. Selection indicator (server lists, and every selectable item)

The most-touched selection surface in the product, and the one §7.1 is strictest about: **two axes
minimum, never tint alone, and no geometry shift.**

Current failures: the Buy price option changes **radius 14 → 20 and stroke 1 → 1.5** on selection, so
the row visibly jumps; the tariff card changes stroke 1dp → 2dp; the desktop's current-device row
gets an accent wash while not being selected at all.

### 18.1 The treatment

Applied to: server rows, tariff cards, price options, payment-method rows, Select options, subscription cards.

| Axis | Unselected | Selected |
|---|---|---|
| **Fill** | transparent / `colorSurface` | `@color/accent_fill_12` / `Brush.SelectedFill` - `#4C8DFF` @ 12% |
| **Border** | 1dp `?attr/colorOutlineVariant` | 1dp `?attr/colorPrimary` - **same width**, colour only |
| **Title weight** | 500 | **700** |
| **Trailing check** | slot reserved, glyph at alpha 0 | 20dp `ic_check` in `colorPrimary`, alpha 1 |

Four axes, zero layout change. The check slot is **always reserved**, so nothing reflows. Border
width never changes. Radius never changes.

Motion: fill and border crossfade + check alpha over `motion_state` 220ms `ease_standard`; the weight
snaps. Reduced motion snaps everything.

**Not permitted**: a coloured left edge (§1.1 side-stripe ban), a scale change on selection, a
shadow, a second badge that repeats what the check already says.

**The unified server icon** (§0.4.7, §10.5) is part of this row and does not change on selection:
the 28dp flag tile inside the standard 40dp tile slot, falling back to the globe glyph. One
treatment in the list row, the connect hero, the sheet header and the notification.

| | Android | Desktop |
|---|---|---|
| Drawable / class | `@drawable/bg_selectable_item` - a `<selector>` with `state_activated` → the fill, plus a `strokeColor` ColorStateList | `Border.ServerRow` / `Border.PriceOption` with `.selected`, unified into `Border.Selectable` + `.selected` |
| Set by | `view.isActivated = true` on the adapter's bind | `Classes.Add("selected")` |
| Announce | `AccessibilityNodeInfo.isSelected = true` | `AutomationProperties.IsSelected` |

---

## 19. Cross-platform name registry

The one table a reviewer needs. Left column is the concept; the two right columns must always read
as the same thing.

| Concept | Android | Desktop |
|---|---|---|
| Primary button | `@style/Widget.Departament.Button.Primary` | `Button.Primary` |
| Primary, tall CTA | `…Button.Primary.Tall` | `Button.Primary.Tall` |
| Secondary button | `…Button.Secondary` | `Button.Secondary` |
| Tertiary button | `…Button.Tertiary` | `Button.Tertiary` |
| Destructive button | `…Button.Destructive` | `Button.Destructive` |
| Icon button | `…Button.Icon` (+`.Filled`/`.Accent`/`.Danger`) | `Button.Icon` (+`.Filled`/`.Accent`/`.Danger`) |
| Segment | `…Segment` in a `MaterialButtonToggleGroup` | `ToggleButton.Segment` in `Border.SegmentTrack` |
| Switch | `…Switch` | `ToggleSwitch.Incy` |
| Text field | `…TextField` | `TextBox.Incy` |
| Row, navigation | `@layout/row_navigation` | `Border.Row` |
| Row, value | `@layout/row_value` | `Border.Row.Value` |
| Row, action | `@layout/row_action` | `Border.Row.Action` |
| Row, toggle | `@layout/row_toggle` | `Border.Row.Toggle` |
| Row, destructive | `@layout/row_destructive` | `Border.Row.Destructive` |
| Card | `…Card` | `Border.Card` |
| Pressable card | `…Card.Pressable` | `Border.Card.Pressable` |
| Chip | `…Chip` (+`.Accent`/`.Status.Ok`/`.Status.Warn`/`.Status.Error`) | `Border.Chip` (+ the same four) |
| Nav item | `…NavigationBar` / `…NavigationRail` | `Button.NavItem` + `.rail`/`.bar`/`.active` |
| Sub-page toolbar | `…Toolbar` | `Border.SubToolbar` |
| Sheet | `…Sheet` | `Border.SheetTop` (mobile width) / `IncyFlyoutTheme` (pointer) |
| Dialog | `ThemeOverlay.Departament.Dialog` | `Border.Card.Modal` + `Border.Scrim` |
| Snackbar | `…Snackbar` | `Border.Toast` |
| Empty state | `@layout/layout_state_empty` | `StackPanel.EmptyState` + `Border.EmptyIcon` |
| Skeleton | `…Skeleton.Bar` / `.Block` | `Border.SkelBar` / `Border.SkelCard` |
| Spinner | `@drawable/spinner_arc` | `Ellipse.Spinner.spinning` |
| Progress bar | `…Progress.Linear` | `Border.Meter` / `Border.Meter.Fill` |
| Selection | `@drawable/bg_selectable_item` + `isActivated` | `Border.Selectable` + `.selected` |

**Names deleted by this spec.** Android: `layout_setting_row.xml`, `layout_setting_toggle_row.xml`
(both already dead), `bottom_nav_item_color.xml`, `bg_acc_option`, `bg_chip_gold`, `bg_nav_header`,
`bg_speed_chip` (all dead), `@anim/nav_press`, `bg_acc_chip`, `bg_acc_badge`, `bg_type_chip`,
`bg_search_pill`, `bg_lp_input`, `bg_buy_option`, `bg_buy_option_selected`.
Desktop: `Button.Tonal`, `Button.OutlinedAccent`, `Button.LinkAction`, `Button.IconButton`,
`Button.IconButton40`, `Button.BackNav`, `Button.Stepper`, `Button.NavRailItem`,
`Button.BottomNavItem`, `Button.SegItem`, `Button.Flat`, `Button.WinBtn`, `Button.RailToggle`,
`Button.MethodChip`, `Button.MeterRow`, `Button.MetaIcon`, `Button.MetaDanger`, `Button.Success`
(never existed here - it was Semi's), `Border.SettingRow`, `Border.ChipBadge`, `Border.StatusChip`,
`Border.ProtocolChip`, `Border.TrafficPill`, `Border.SearchPill`, `TextBox.IncyField`,
`ToggleSwitch.iOS`, `Radius.Search`, `Radius.Traffic`, `Size.SegmentChip`, `Size.TrafficPill`.

---

## 20. Token additions

Everything this spec needs that does not exist yet. Add these **before** implementing any component;
a component that hard-codes one of these values instead is a defect (`00-rules.md` §3).

### 20.1 Android

`res/values/dimens.xml`:

```xml
<!-- Button system (22-components R1/R2). radius_button 16 completes the shape
     scale chip 12 / button 16 / card 20 / sheet 24; the owner rejected capsule
     CTAs (see GlobalStyles.axaml:3-14), so radius_pill is for circles only. -->
<dimen name="radius_button">16dp</dimen>
<dimen name="btn_height">48dp</dimen>
<dimen name="btn_height_tall">52dp</dimen>
<dimen name="btn_min_width">96dp</dimen>

<!-- Input field height. Deliberately == row_min_height so forms and lists share
     one horizontal rhythm, and so Material's OutlinedBox 56dp minimum is not
     fought. Fills the gap 00-rules.md 3.3 left (survey D8). -->
<dimen name="field_min_height">56dp</dimen>

<!-- Seamless sub-page toolbar (00-rules 4.8). Replaces ?attr/actionBarSize. -->
<dimen name="toolbar_height">56dp</dimen>

<!-- Determinate progress / traffic meter. -->
<dimen name="meter_height">6dp</dimen>
```

`res/values/motion.xml`:

```xml
<!-- Skeleton pulse. Loading feedback, NOT the 600ms hero moment (8.4): a
     skeleton conveys state, which 8.1 permits. Replaces the off-token 900ms
     AccelerateDecelerate pulse in AccountFragment.kt:413-430. -->
<integer name="motion_pulse">1000</integer>
<!-- Inline spinner: one full revolution. Linear is correct for a continuous
     rotation and is not the 8.3 ban, which is about state transitions. -->
<integer name="motion_spin">1100</integer>
<!-- Re-entry guard for taps that are not command-gated (22-components R9). -->
<integer name="input_debounce">500</integer>
```

`res/values/colors.xml` + `res/values-night/colors.xml`:

```xml
<!-- Accent interaction steps. One step darker than the accent, never lighter,
     never a hue flip. Night: #3D7EF0 / #3877E0. Light: #1A54B4 / #17499E. -->
<color name="accent_hover">#3D7EF0</color>
<color name="accent_pressed">#3877E0</color>
<!-- 12% accent fill for the selected state (22-components 18). onSurface over
     this on colorSurface measures >14:1. -->
<color name="accent_fill_12">#1F4C8DFF</color>
```

`res/values/ids.xml`: `<item name="tag_last_click" type="id" />`.

`res/values/strings.xml`: `<string name="state_loading">загрузка</string>`.

### 20.2 Desktop - `Assets/GlobalResources.axaml`

```xml
<!-- inside ResourceDictionary.ThemeDictionaries -> Dark, and mirrored in Light -->
<SolidColorBrush x:Key="Brush.AccentHover"   Color="#3D7EF0" />
<SolidColorBrush x:Key="Brush.AccentPressed" Color="#3877E0" />

<!-- OUTSIDE ThemeDictionaries only for genuinely theme-independent values.
     Brush.Accent / Brush.OnAccent / Brush.Tile.* / Brush.SelectedFill /
     Brush.StatusChip.* MUST MOVE INSIDE (ruling R11) - light accent is
     #1E5FC7, not #4C8DFF, or every focus ring in the app is 2.98:1. -->
<CornerRadius x:Key="Radius.Button">16</CornerRadius>   <!-- move out of GlobalStyles into the token file -->
<x:Double x:Key="Size.Btn">48</x:Double>
<x:Double x:Key="Size.BtnTall">52</x:Double>
<x:Double x:Key="Size.BtnMinWidth">96</x:Double>
<x:Double x:Key="Size.Field">56</x:Double>
<x:Double x:Key="Size.Meter">6</x:Double>
```

`Common/Motion.cs`:

```csharp
/// <summary>1000 мс - пульс скелетона (loading), НЕ hero-момент.</summary>
public static readonly TimeSpan Pulse = TimeSpan.FromMilliseconds(1000);
/// <summary>1100 мс - один оборот инлайн-спиннера, LinearEasing.</summary>
public static readonly TimeSpan Spin = TimeSpan.FromMilliseconds(1100);
/// <summary>500 мс - защита от двойного нажатия (22-components R9).</summary>
public static readonly TimeSpan Debounce = TimeSpan.FromMilliseconds(500);
```

### 20.3 Tokens retired

`Radius.Search` 14 → `Radius.Chip` 12. `Radius.Traffic` 8 → full-round.
`Size.SegmentChip` 44 → `Size.Btn` 48 / segment 40. `Size.TrafficPill` 160 → fluid width.
`@dimen/view_height_dp36` (used as a button height) → `btn_height`.

---

## 21. Migration order

Dependencies run downward; each step is shippable and leaves its files at zero mechanical defects.

1. **Tokens** (§20 Token additions) on both platforms, in the same change. Includes R11's move of the accent keys
   into the theme dictionaries - **this is the only P1 accessibility defect in the whole system and
   it goes first.**
2. **Android `themes.xml` component defaults** (`materialButtonStyle`, `materialCardViewStyle`,
   `materialSwitchStyle`, `textInputStyle`, `chipStyle`, `snackbarStyle`, `toolbarStyle`, the three
   `shapeAppearance*Component`). This alone removes the need for most instance-level declarations
   and is the root-cause fix for half of the survey's Part A.
3. **Buttons** (§2 Buttons) - styles, ColorStateLists, `press_scale` rewrite, `nav_press` deletion,
   `util/ControlState.kt`, `Common/ControlState.cs`. Then the collapse table (§2.2), screen by
   screen.
4. **Rows** (§8 Row) - the five layouts, then the 23 hand-inlined settings rows, then Account, then the
   rest.
5. **Fields, Select, Segment, Switch** (§4 Text field, §5 Select, §6 Segmented control, §7 Switch) - the biggest instance counts, the least visible
   change per instance.
6. **Chrome**: toolbar (§12), tab bar (§11), sheet/dialog (§13), snackbar (§14).
7. **States**: empty (§15), skeleton (§16), progress (§17), selection (§18).

At each step, the `00-rules.md` §1.5 mechanical greps must be clean **for the files touched**. The standing debt
(325 off-scale Android `dp`, 97 desktop off-scale spacing, 44 + 22 dashes, 3 desktop inline hex) is
paid down by whoever touches the file, not deferred.

---

## 22. Acceptance

A component is done when every box is ticked for it, in all three themes, at default and 200% scale,
on both platforms.

**Geometry**
- [ ] Height declared as a minimum, never fixed; survives a 2-line label at 200%
- [ ] Radius is one of 12 / 16 / 20 / 24-top / full-round, and matches R1's table
- [ ] Every spacing value is on the `space_*` scale
- [ ] Touch target ≥48dp Android, ≥40px desktop in rows and toolbars, ≥32px elsewhere; ≥8 apart
- [ ] Text origin 68, divider inset 68 (rows)

**Type**
- [ ] A ramp role, never an inline `textSize` / `FontSize`
- [ ] No `textStyle="bold"`, no `ToUpper`, no `textAllCaps`
- [ ] Numbers use the Numeric role with `tnum`

**Colour**
- [ ] Theme attrs / `DynamicResource` only; zero raw hex
- [ ] Contrast measured and recorded: body ≥4.5:1, large ≥3:1, icons and boundaries ≥3:1
- [ ] Accent used only where §R14 allows; the screen has exactly one filled accent surface
- [ ] Colour is never the only signal

**States**
- [ ] Default, hover (desktop), pressed, focused, disabled, loading, selected, error - each one drawn, not assumed
- [ ] Focus ring visible on every focusable control, and it survives reduced motion
- [ ] Disabled is 0.38, declaratively, with no imperative alpha
- [ ] Loading holds the width, hides the label, spins at 1100ms, does not fade, appears after 300ms
- [ ] Double-press cannot fire the action twice

**Motion**
- [ ] Every duration and curve from the token scale; exit is 75% of enter
- [ ] Press is 0.97 / 90 `ease_out_quart` in / 160 `ease_out_quint` out - and rows do not scale
- [ ] Reduced motion honoured through `MotionUtils` / `MotionState`, verified by toggling it live

**System**
- [ ] The Android style name and the desktop class name are the same concept with the same name
- [ ] No view-local style rule was added
- [ ] The component appears in §19's cross-platform name registry
- [ ] Nothing falls through to `Widget.Material3.*` or Semi defaults

---

## Change log

| Date | Change |
|---|---|
| 2026-07-26 | Initial component system. 15 rulings; 5 button variants + 2 modifiers replacing 219 instances across 28 desktop class combinations, 11 heights and 10 radii; 17 components specified with both platform mappings; 13 new tokens; 30 names retired. R1 (button radius 16) and R11 (theme-aware accent) require `00-rules.md` §18 entries. |
