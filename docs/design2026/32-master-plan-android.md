# 32 - Master design plan: ANDROID

**Departament VPN, Android client. The complete screen-by-screen design specification for the 2026
rebuild.**

| | |
|---|---|
| Repo root | `/home/user/dp` |
| Gradle root | `/home/user/dp/V2rayNG` |
| Package | `com.v2ray.ang` |
| UI code | `V2rayNG/app/src/main/java/com/v2ray/ang/ui/**`, `.../tv/**`, `.../receiver/WidgetProvider.kt`, `.../service/QSTileService.kt` |
| UI resources | `V2rayNG/app/src/main/res/**` |
| Stack | Kotlin, XML views, Material 3, ViewBinding |
| Date | 2026-07-26 |

**Precedence.** `00-rules.md` is law and outranks this file on any numeric value or ban.
`03-direction.md` is the direction and outranks this file on any question of what the product is.
This file outranks nothing; it is the implementation contract. Where it needs a value the law does
not carry, that value is declared in section 8.0 (new tokens) or section 25 (decisions), and it is
not implemented until it exists in `res/values/dimens.xml` / `colors.xml` / `motion.xml`.

**Inputs read before writing this.** `00-rules.md`, `01-inventory-android.md`, `02-inventory-pc.md`,
`03-direction.md`, `21-account-survey.md`, `30-reference-analysis.md`, `31-self-assessment.md`,
`.claude/skills/impeccable/SKILL.md` + `reference/{product,android,layout,typeset,colorize,
interaction-design,animate,distill,onboard,polish}.md`, `/home/user/dp/CLAUDE.md`, and the source:
every file in `res/layout/` (73), `res/values*/`, `res/drawable*/`, `res/menu/`, `res/anim/`,
`res/xml/`, and `ui/**`.

**How to use this document.** Sections 1 to 8 are the system; they are read once and then obeyed.
Sections 9 to 24 are the screens; an implementer opens the one section for the screen he is
building and needs nothing else. Every screen section has the same seven headings, in the same
order, so nothing can be silently skipped:

1. **Purpose** - one sentence.
2. **Files today, and the verdict** - RESTYLE / REBUILD / CUT, with the exact paths.
3. **Component tree** - real dp, real styles, real drawables.
4. **States** - every applicable state from `00-rules.md` section 15.
5. **Copy** - every visible string, with its resource name and its Russian value.
6. **Interaction** - what each control does, what it cannot do, what confirms it.
7. **Transitions** - in, out, and back.

**The bar.** An implementer must never have to make a visual judgement call. If this document leaves
one open, that is a defect in this document; log it in section 25 rather than inventing a value.

---

# PART I - THE SYSTEM

---

## 1. The visual thesis, and the three signature moments

### 1.1 The thesis, in one paragraph

Departament VPN on Android is a black instrument panel with one lit control on it: a flat near-black
ground (`#0A0B0D`) that never draws attention to itself, structure carried entirely by 56dp rows and
1dp hairlines that begin at a 68dp text origin held identically on every screen in the app, exactly
one object per screen permitted to emit the brand blue and on most screens that number is zero, all
Russian prose in a Cyrillic-capable UI face at 14sp and above while every quantity in the product -
traffic, speed, latency, uptime, balance, price, device count - is set in Space Grotesk at fixed
tabular 620/1000 advances so that a live counter is as still as a printed one, a spacing melody of
4/8/12 inside an object, 16 between objects, 24 between sections and 32 exactly twice per screen
rather than a uniform 16dp drone, depth expressed only by four surface planes and never by a shadow,
a gradient or a glow, and motion that acknowledges a finger in 90ms, changes state in 220ms, reveals
in 300ms, and performs exactly once in the entire product, for 600ms, at the instant the tunnel
confirms. It reads as engineered rather than decorated, calm rather than dramatic, and Russian
rather than translated; it is the opposite of the category's navy-plus-neon-plus-halo reflex, and
the thing a user recognises in a screenshot with the wordmark cropped out is not the connect button
but the ledger and the figures in it.

### 1.2 The three signature moments

These are the three instants the product is designed around. Everything else in the app exists to
not get in their way. Each is specified in full in its screen section; this is the register.

**Moment 1 - «Замок». The tunnel confirms.** Screen 11 (Home), state `подключено`.
The only 600ms in the product. The shield outline crossfades to the filled shield in
`?attr/colorPrimary` over 220ms `ease_standard`; on the same frame one 1dp accent ring is emitted
from the disc edge, scaling 1.0 to 1.35 while its alpha falls 0.6 to 0, over `motion_emphasis` 600ms
`ease_out_quint`, exactly once, never looping; a single `pressHaptic()` fires on that same frame;
the status word swaps from «Отключено» to «Подключено» with **no animation at all**, and the green
status dot appears with it. The page does not flash, the background does not tint, the bottom
navigation does not react, and no second ring ever follows the first. Under reduced motion the
shield is filled instantly, the ring is not emitted, and the haptic still fires.
Files: `res/anim/connect_confirm.xml`, `res/anim/shield_assemble.xml`, `MainActivity.applyRunningState()`.

**Moment 2 - «Цифры встают в колонку». The instrument comes alive.** Screen 11, the numeric strip.
The three-column strip (download, uptime, upload) is absent while disconnected and appears when the
tunnel is up, entering once with `motion_reveal` 300ms `ease_out_quint`, alpha 0 to 1 and
translationY 8dp to 0. From that instant it never moves again: every column has a reserved width
computed from the tabular 620/1000 advance, so `9,9 Мбит/с` becoming `10,1 Мбит/с` and `00:09:59`
becoming `00:10:00` shift nothing. This is the moment the product's claim - "an instrument, not a
storefront" - becomes visible, and it is the one thing in the app a competitor using Inter for
everything physically cannot reproduce.

**Moment 3 - «Подписка загорается в трёх местах». One object, three surfaces.** Screens 11, 15, 22.
The subscription is a single six-state object resolved in one place (`SubscriptionState`), and when
it changes state - `активна` becomes `истекает` at T-3 days - the same chip, the same word and the
same action appear simultaneously in the subscription card on Home, in the subscription card on
Account, and on the second line of the ongoing notification, with identical copy and identical
colour. Nothing else in the app changes. The user learns, in one event, that this product has one
truth about his account rather than four renderings of it. This is the moment neither Happ nor Incy
can copy without building an account system first.

### 1.3 What this thesis forbids on Android, restated as a checklist

Every item below is a P1 defect, is currently present in the build, and has a named owner in the
implementation sequence (section 26).

| # | Forbidden | Currently at |
|---|---|---|
| T1 | Any gradient drawable in the product surface | `bg_home_gradient.xml`, `drawable-night/bg_home_gradient.xml`, `bg_home_gradient_mono.xml`, `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`, `bg_bottom_nav_scrim.xml`, `bg_nav_header.xml`, `bg_traffic_gradient.xml` |
| T2 | More than one card on a screen, or a card inside a card | `activity_buy_tariff.xml` (option rows inside a tariff card), `activity_bypass_list.xml` (card header above card list), `activity_devices.xml`, `activity_payment_history.xml` |
| T3 | A coloured icon tile that is not one of the three sanctioned categories | `bg_icon_green/orange/purple/yellow.xml` and their `iconTint*` / `iconTileBg*` attrs |
| T4 | Text set by `android:textSize` instead of a ramp style | ~100 occurrences in 21 files |
| T5 | A dp value outside 0/1/2/4/8/12/16/20/22/24/28/32/36/40/44/48/52/56/64/72/80/100/120/152/160/176 | 325 values in 25 files |
| T6 | A `Toast` for anything the user can act on | ~40 call sites plus `toast_status.xml` |
| T7 | Emoji or a typographic character used as UI chrome | `FlagUtil` flags, `🌐`, `✕`, `↑`, `↓`, `∞` as a value |
| T8 | A screen with no entry point | 14 screens (section 24.9) |
| T9 | A single-choice `AlertDialog` where a segment or a push belongs | Режим, DNS, Пинг, Оформление, Язык, Автообновление |
| T10 | English in a user-visible string | 384 of 463 strings in `values/strings.xml` |

---

## 2. The surface and elevation model

### 2.1 Four planes, no shadows, mapped to Android attributes

`03-direction.md` section 4.1 defines four planes and what each is allowed to mean. This is the
Android binding. **A layout that sets `android:elevation`, `app:cardElevation` above 0, or any
`android:background="#..."` literal is a defect.**

| Plane | Meaning | Android attribute | Dark | Light | Where it is legal |
|---|---|---|---|---|---|
| **P0 Ground** | The screen, its toolbar, its bottom navigation, its scroll container | `?attr/colorBackground` | `#0A0B0D` | `#F4F7FC` | Window background, `CoordinatorLayout` root, toolbar, bottom nav, sheet scrim base |
| **P1 Object** | A discrete thing acted on as a unit | `?attr/colorSurface` | `#141619` | `#FFFFFF` | The one card per screen, bottom-sheet body, dialog body, status strip is **not** P1 |
| **P2 Raised** | Transient only. **Nothing is P2 at rest** | `?attr/colorSurfaceContainerHigh` | `#1A1D21` | `#EAEFF7` | Pressed row background, drag state, the status strip (transient by definition), skeleton fills |
| **P3 Inset** | Recessed into a plane | `?attr/colorSurfaceContainerHighest` | `#20242B` | `#E3EAF4` | Input fields, chips, the neutral 40dp tile, the connect disc, a selected row, a meter track, a segmented container |

**The plane budget: at most two planes above ground in any region, three counting ground.**
Legal: `P0 -> P1 card -> P3 chip`. Illegal and each is a named defect:
`P0 -> P1 -> P1` (nested cards), `P0 -> P1 -> P2 -> P3`, `P0 -> P3 -> P1`.

### 2.2 The consequences for this codebase

1. **The window background is `?attr/colorBackground` everywhere.** `activity_main.xml:8`
   (`android:background="@drawable/bg_home_gradient"`) and `activity_settings.xml`
   (`@drawable/bg_settings_glass`) are removed. `themes.xml` sets
   `android:windowBackground` to `?attr/colorBackground` and no activity overrides it.
2. **The toolbar is P0** (`00-rules.md` 4.8, owner request 0.4.6). `activity_base.xml`'s
   `AppBarLayout` loses its background tint, its `app:elevation` and its
   `app:liftOnScroll`. The only boundary permitted at scroll is a 1dp
   `?attr/colorOutlineVariant` hairline fading in over `motion_state` 220ms once `scrollY > 0`,
   implemented once in `BaseActivity` and reused.
3. **The bottom navigation is P0** with a 1dp `?attr/colorOutlineVariant` top hairline.
   `bottom_nav_scrim` (the 160dp gradient at `activity_main.xml:513`) is deleted along with
   `bg_bottom_nav_scrim.xml`.
4. **Cards are P1, elevation 0, 1dp `?attr/colorOutlineVariant` stroke, radius 20, padding 16.**
   Exactly one per screen. `bg_card_incy.xml` becomes the single card background and every
   `MaterialCardView` uses `app:cardElevation="0dp"`, `app:strokeWidth="1dp"`,
   `app:strokeColor="?attr/colorOutlineVariant"`, `app:cardCornerRadius="@dimen/radius_card"`.
5. **Depth in dark mode comes from the ramp only.** No `android:elevation`, no
   `android:outlineProvider`, no shadow drawable, no `RenderEffect`. The only elevation values
   permitted anywhere are Material's own untouched defaults on `BottomSheetDialogFragment` and
   `MaterialAlertDialog`.
6. **Light theme** may use Material's tonal elevation and must not use a coloured or offset shadow.
   Mono (`ThemeOverlay.Mono`) inherits the same four planes; the plane values are attributes, so
   the overlay works for free. Every raw `@color/...` reference in a layout is a hole in mono and
   is a defect (the current list is in `design-review-c942766.md` section 1.1).

### 2.3 Separators

| Between | Device | Value |
|---|---|---|
| Two rows in the same group | Hairline | `View`, `layout_height="1dp"`, `background="?attr/colorOutlineVariant"`, `layout_marginStart="@dimen/text_origin"` (68dp), `marginEnd="0dp"` |
| Two groups | Space | 24dp plus a section header |
| A section header and its first row | Nothing | No divider above the first row of a group, ever |
| A card and the next block | Space | 16dp (object to object) or 24dp (section to section) |
| The rail-equivalent (sw600dp `NavigationRailView`) and content | Hairline | 1dp `?attr/colorOutlineVariant`, vertical |

A row never carries both a top and a bottom hairline. The last row of a group carries none. The
three divider insets currently shipped (44dp in `custom_divider.xml`, 68dp in layer B, 72dp in the
Settings tab and the Account tab) collapse to one: **68dp**.

---

## 3. Accent strategy: where the blue is spent, and where it is forbidden

### 3.1 The hue and its two jobs

`?attr/colorPrimary` = `#4C8DFF` (dark) / `#1E5FC7` (light). Two jobs, no third:

1. **Action** - the one thing the screen wants the user to press.
2. **State the user controls** - current destination, current selection, focus ring, link,
   determinate progress.

### 3.2 The per-screen accent ledger

This table is binding. "The lit element" is the single filled or fully saturated accent surface.
"Tinted" elements are the at-most-three additional accent-carrying elements permitted by
`03-direction.md` 5.2. Any screen not in this table has **zero** accent.

| Screen | The one lit element | Tinted, max 3 | Accent count target |
|---|---|---|---|
| Home, disconnected | none | subscription-card action text button, nav indicator pill | 0 filled |
| Home, connecting | the 3dp indeterminate arc on the disc | nav indicator pill | 0 filled |
| Home, connected | the filled shield glyph inside the disc | nav indicator pill | 1 |
| Home, no subscription | «Купить» filled CTA | nav indicator pill | 1 |
| Sign-in | «Войти» filled CTA | shield tile (`colorPrimaryContainer`), focused field ring, link text buttons | 1 |
| Servers | none | selected row's 20dp check glyph, nav indicator pill | 0 filled |
| Servers, empty | «Добавить провайдера» filled CTA | nav indicator pill | 1 |
| Server actions sheet | none | none. The destructive row is red, not blue | 0 |
| Account | «Продлить» **or** «Купить», never both | subscription state chip when `истекает`, nav indicator pill | 1 |
| Buy | «Оплатить» filled CTA | selected price option's check + P3 fill, «Итого» figure | 1 |
| Devices | none | none | 0 |
| Payment history | none | none | 0 |
| Settings hub | none | nav indicator pill | 0 |
| Every settings sub-page | none, unless the page's job is one action (Backup: «Создать копию») | switch thumbs when on, segmented thumb | 0 or 1 |
| Add-provider sheet | «Добавить» filled CTA | none | 1 |
| Server editor | «Сохранить» filled CTA in the bottom bar | focused field ring | 1 |
| Scanner | none | the 240dp framing bracket | 0 |
| About | none | none | 0 |
| Deep-link confirm sheet | «Добавить» filled CTA | none | 1 |

### 3.3 Where the accent is forbidden outright

Backgrounds. Section headers. Dividers. The wordmark (`ToolbarBrandTitle` stays
`?attr/colorOnBackground`). Empty-state glyphs and tiles. Any icon tile that is not the screen's
primary-action row. Any chip that is not selected. Any inactive tab, row or control. Any disabled
control. The idle connect disc. Ping values. Protocol chips. Payment amounts. Traffic labels. Any
shadow or glow, which do not exist. Any gradient, which do not exist.

### 3.4 The tile category system, closed at three values

The single largest source of accent leakage in the build: `values/themes.xml:88-99` maps
`iconTintGreen`, `iconTintOrange`, `iconTintPurple`, `iconTintYellow` and their tile fills to
`@color/icon_blue` / `@color/icon_tile_blue`, so 22 of 23 Settings rows, every action-sheet row,
every device row and every payment row render the same blue tile. The layouts still say "green".

**The 40dp tile has exactly three states and no others:**

| Tile | Fill | Glyph | Meaning | Budget |
|---|---|---|---|---|
| Neutral | `@color/icon_tile_neutral` `#20242B` | `@color/icon_glyph_neutral` `#9BA1AD` | Everything | unlimited |
| Accent | `?attr/colorPrimaryContainer` `#17325C` | `?attr/colorOnPrimaryContainer` `#CFE0FF` | This row **is** the screen's primary action | **max 1 per screen** |
| Destructive | `@color/icon_tile_red` `#331F2225`-class red tint | `?attr/colorError` `#F04452` | This row destroys something | max 1 per screen |

Deleted in the same change: `bg_icon_green.xml`, `bg_icon_orange.xml`, `bg_icon_purple.xml`,
`bg_icon_yellow.xml`, `bg_chip_gold.xml`, and the attrs `iconTintGreen`, `iconTintOrange`,
`iconTintPurple`, `iconTintYellow`, `iconTileBgGreen`, `iconTileBgOrange`, `iconTileBgPurple`,
`iconTileBgYellow` from `res/values/attrs.xml` and all three theme files.

### 3.5 The non-accent colours and what they may do

| Colour | Attr / resource | Dark | May be a button? | May be a tile? | Where |
|---|---|---|---|---|---|
| Green (status) | `?attr/colorTertiary` | `#22C55E` | **No** | **No** | The connected dot and word; the «Оплачено» chip; the «Активна» chip |
| Red (destructive) | `?attr/colorError` fill, `@color/ping_bad` text | `#F04452` / `#FF6069` | Yes, and only to destroy | Yes, max 1 | Delete rows, «Удалить» dialog action, error text, «нет ответа» |
| Amber (warning) | `@color/warning` (new) | `#EAB308` | **No** | **No** | The «Истекает» chip, the «В обработке» chip, a meter above 90% |
| Neutral | `?attr/colorOnSurfaceVariant` | `#9BA1AD` | n/a | Yes, default | Everything else |

**Colour is never the only signal.** Every one of the above ships with a word or a glyph beside it:
a green dot always sits next to the word «Подключено»; a red delete glyph always sits in a row whose
title is «Удалить устройство»; an amber chip always carries the word «Истекает».

---

## 4. The type ramp, and its use per screen

### 4.1 The ramp as it will exist after the type pass

Two faces. The brand face has **zero Cyrillic codepoints** (measured, `03-direction.md` 1.1), so the
split is by script and it is load-bearing rather than decorative.

| Role | Style | Face | Size | Weight | Tracking | Colour | Carries |
|---|---|---|---|---|---|---|---|
| Display | `TextAppearance.App.Display` | brand | 34sp | 700 | -0.02em | onSurface | One hero figure per screen. Balance only |
| Headline | `TextAppearance.App.Headline` | brand | 24sp | 700 | -0.01em | onSurface | Full-screen empty/first-run titles, the sign-in headline |
| Title | `TextAppearance.App.Title` | brand | 16sp | 700 | 0 | onSurface | Row titles, card titles, section headers, toolbar titles, button labels |
| Title medium | `TextAppearance.App.Title.Medium` | brand | 16sp | 500 | 0 | onSurface | A softer title inside a dense card. Rare |
| Body | `TextAppearance.App.Body` | **UI face** | 14sp | 400 | +0.01em | onSurface | Prose, dialog bodies, empty-state lines |
| Subtitle | `TextAppearance.App.Subtitle` | **UI face** | 13sp | 400 | +0.01em | onSurfaceVariant | Row subtitles, row values, supporting lines |
| Caption | `TextAppearance.App.Caption` | **UI face** | 12sp | 400 | +0.02em | onSurfaceVariant | Field labels, metadata, timestamps, helper text |
| Chip | `TextAppearance.App.Chip` | brand | 11sp | 500 | +0.04em | contextual | Chip and badge labels only |
| Numeric | `TextAppearance.App.Numeric` | brand | inherits | **500** | 0 | onSurface | Every quantity in the product |
| Section header | `@style/SettingsSectionLabel` | brand | 16sp | 700 | 0 | onSurface | Group headers |
| Wordmark | `@style/ToolbarBrandTitle` | brand | 20sp | 700 | -0.01em | onBackground | `departament`, nowhere else |

**Four required changes to `res/values/styles.xml` before any screen work starts:**

1. `TextAppearance.App.Numeric` gains `android:textFontWeight="500"` (it currently declares no
   weight while `00-rules.md` 3.4 specifies 500).
2. `TextAppearance.App.Body`, `.Subtitle`, `.Caption` gain
   `android:fontFamily="@font/ui_face"` - the Cyrillic-capable family decided by `03-direction.md`
   D-1. Until that decision lands they explicitly declare `android:fontFamily="sans-serif"` so the
   face is chosen by us and not by the OEM.
3. `SettingsSectionLabel` changes `paddingTop` from 18dp (off-scale) to **24dp** and keeps
   `paddingBottom` 8dp.
4. `res/font/space_grotesk.xml` gains explicit `android:fontVariationSettings="'wght' 400|500|700"`
   per entry if the verification in `03-direction.md` 6.3 confirms that the default 300 instance is
   being rendered. **Run that verification before the type pass, not after.**

**Banned, mechanically checkable:** `android:textSize` in any layout; `android:textStyle="bold"`
anywhere; weight 600; 15sp, 18sp or any step not in the ramp; italic; letter-spacing set per screen.

### 4.2 The ramp in use, per screen

Every screen uses a **subset**. A screen that uses more than five roles is doing too much. Display
appears exactly twice in the entire app.

| Screen | Display | Headline | Title | Body | Subtitle | Caption | Chip | Numeric |
|---|---|---|---|---|---|---|---|---|
| Home, populated | - | - | status word, section headers, server row title, sub-card title | - | server row subtitle, sub-card expiry | - | state chip, protocol | speeds, uptime, traffic, days |
| Home, first run | - | empty title | CTA label | empty line | - | - | - | - |
| Sign-in | - | «Вход» | CTA labels | - | subtitle line | field labels, helper, error | - | 2FA code field |
| Servers | - | empty title | toolbar title, row titles, section headers | empty line | transport line, count line | - | protocol | ping |
| Server actions sheet | - | - | sheet title, row titles | - | server subtitle | - | protocol | - |
| Account | **balance** | empty title | name, section headers, row titles, card title | empty line | row values, expiry | «Баланс» label | tariff badge, state chip | balance, devices, traffic, price |
| Buy | - | - | tariff names, «Итого» value, CTA | error line | tariff info, option duration | «Примерная сумма» note | - | prices, device count |
| Devices | - | empty title | row titles | empty line | platform + last-seen | hwid tail | «Это устройство» | date |
| Payment history | - | empty title | row titles | empty line | date | - | status chip | amount, date |
| Settings hub | - | - | row titles, section headers | - | row values | - | - | - |
| Settings sub-page | - | - | row titles, section headers | explanatory paragraphs | row values, subtitles | helper text | segment labels | numeric fields |
| Server editor | - | - | section headers, save CTA | - | - | field labels, helper, error | - | port, mtu |
| About | - | - | row titles, app name | - | row subtitles | version, build | - | version numbers |
| Logs | - | - | toolbar title | - | - | - | level chip | timestamp, log body (mono) |

### 4.3 Number formatting, mandatory and global

One formatter, `util/NumberFormat.kt`, replacing every ad-hoc `String.format` in the UI layer.

| Quantity | Format | Example | Feature flags |
|---|---|---|---|
| Traffic | `X,Y ЕД` comma decimal, one decimal below 100, none above | `12,4 ГБ`, `247 ГБ` | `tnum lnum zero` |
| Speed | `X,Y Мбит/с` | `24,8 Мбит/с` | `tnum lnum zero` |
| Latency | `N мс`; unreachable renders the word | `48 мс`, `нет ответа` | `tnum lnum zero` |
| Uptime | `HH:MM:SS`, hours unpadded above 99 | `02:14:07` | `tnum lnum zero` |
| Money | `1 290 ₽`, U+2009 thin space thousands, U+00A0 before `₽` | `1 290 ₽` | `tnum lnum`, **`zero` off** |
| Device count | `3 / 5`, spaces around the slash; unlimited renders the word | `3 / 5`, `без ограничений` | `tnum lnum` |
| Days remaining | bare integer plus a pluralised noun | `27 дней`, `3 дня`, `1 день` | `tnum lnum` |
| Date | `dd.MM.yyyy`; with time when two records can share a day | `12.08.2026`, `12.08.2026 19:41` | `tnum lnum zero` |
| Percent | `N %` with U+00A0 | `92 %` | `tnum lnum` |

`∞` is **deleted from the product**. `@string/account_unlimited` becomes «без ограничений».
A mathematical symbol standing in for a Russian phrase is the same defect class as `↑` standing in
for an icon.

---

## 5. Spacing rhythm and grid

### 5.1 The scale, and nothing outside it

`4 · 8 · 12 · 16 · 24 · 32`, as `@dimen/space_4` … `@dimen/space_32`. Derived and already
tokenised: `screen_gutter` 16, `row_min_height` 56, `tile_size` 40, `tile_glyph` 22.
**6, 10, 13, 14, 18, 20, 26, 27, 34, 42, 45, 60, 68 (as a margin), 76, 88, 140, 200 do not exist.**

### 5.2 The melody

A screen uses four gap values and they are not interchangeable. A screen where every vertical gap is
16 has no hierarchy and fails the squint test; that is the single most common defect in the build.

| Gap | Between | Frequency per screen |
|---|---|---|
| **4 / 8 / 12** | Parts of one object: glyph to label, title to subtitle, chip padding, label to field | many |
| **16** | Objects: card to card, group to group, the screen gutter, card inner padding | several |
| **24** | Sections; the space that replaces a divider under a section header | 1 to 3 |
| **32** | After a hero; before a bottom CTA bar; the top of a first-run column | **at most twice** |

### 5.3 The grid

```
|<-16->|<-------- 40 -------->|<-12->|<------------ content column ------------>|<-12->|<-tr->|<-16->|
 gutter        icon tile        gap              title / subtitle                 gap  trailing gutter
       |<---------------- 68dp text origin ---------------->|
       |<---------------- hairline starts here ------------>|--------------------------------------|
```

- **Text origin: 68dp** on every screen that uses tiled rows, without exception. Every title on
  every screen starts at the same x. Every hairline starts at the same x. Add
  `<dimen name="text_origin">68dp</dimen>`.
- **Rows without a leading tile** (form fields, dense value rows inside a sub-page) start at 16dp
  and their hairlines start at 16dp. A screen picks one origin and holds it; it never mixes 16 and
  68 in one list.
- **Trailing column** is right-aligned at the gutter. Its content is one of: a 20dp chevron, a
  `MaterialSwitch`, a value in `Subtitle`, a 48dp icon button, or a 20dp state marker. See 8.2 for
  the one sanctioned pairing (value + state marker).
- **Vertical row padding is 8dp**, which with a 40dp tile produces exactly `row_min_height` 56dp.
  A two-line subtitle grows the row; it never clips.

### 5.4 The screen frame

| Region | Value |
|---|---|
| Horizontal gutter | 16dp (`@dimen/screen_gutter`); **24dp** at `sw600dp` |
| Content max width | none on phone; **720dp**, centred, at `sw600dp` |
| Top | system-bar inset, then the 56dp toolbar, then the first content block |
| Bottom, tab screens | content bottom padding = navigation-bar inset + 56 (bar) + 16 |
| Bottom, sub-pages with a CTA bar | content bottom padding = inset + 52 (CTA) + 16 + 16 |
| Between the last section and a bottom CTA | 32dp |

### 5.5 The 3dp / 42dp / 44dp / 36dp cleanup list

| Current | File | Becomes |
|---|---|---|
| 36dp icon buttons ×4 | `layout_servers_header.xml:30,42,54,66` | one 48dp overflow |
| 36dp icon buttons ×4 | `layout_subscription_meta_bar.xml:76,89,112,240` | file deleted; actions move to the sub-card and its sheet |
| 42dp `btn_home_add` | `activity_main.xml:161` | 48dp toolbar action |
| 40dp `✕` glyph | `layout_home_account.xml:63` | markup deleted |
| 44dp delete | `item_device.xml:83` | 48dp |
| 40dp steppers | `activity_buy_tariff.xml:214,243` | 48dp |
| 44dp search field | `layout_servers_header.xml:91` | 48dp |
| 3dp nav margins, 34x3dp dot | `activity_main.xml:560,570-572` | 8dp margins, 64x32dp indicator pill |
| 13dp padding to fake a 22dp glyph | `layout_subscription_meta_bar.xml` | file deleted |
| 12dp gutter, 14dp padding, 10dp vertical, 60dp rows | `activity_local_proxy.xml`, `activity_provider_settings.xml`, `activity_url_scheme_list.xml`, `activity_backup.xml`, `activity_bypass_list.xml`, `activity_routing_setting.xml`, `activity_user_asset.xml` | 16 / 16 / 8 / 56 via the shared row include |

---

## 6. Iconography

### 6.1 The rules

1. **One family.** The existing `res/drawable/ic_*.xml` vector set. 24dp viewport, 2dp stroke,
   round caps, round joins, no fills except where a glyph is deliberately filled (see 3).
   A glyph that exists only on desktop is **ported**, not redrawn.
2. **Outline in rows and toolbars; filled only for two things**: the selected bottom-navigation
   destination, and status (the connected shield, the payment-status glyph, the selected check).
3. **Four sizes, and nothing else:**

| Size | Token | Where |
|---|---|---|
| 22dp | `@dimen/tile_glyph` | inside a 40dp tile |
| 24dp | `@dimen/glyph_toolbar` (new) | toolbar actions, bottom-navigation icons |
| 20dp | `@dimen/glyph_inline` (new) | chevrons, trailing state markers, inline status, button leading icons |
| 16dp | `@dimen/glyph_chip` (new) | inside a chip, inside the numeric strip |

4. **Vector only.** No PNG icons. `nav_header_bg.png` is deleted. Flags are the one exception and
   are handled by 6.3.
5. **Every icon-only control carries `android:contentDescription`.** An icon-only control without
   one is a P1 defect. The current count of icon-only controls missing a description is 11.
6. **No emoji, no typographic stand-ins.** `↑` `↓` become `ic_speed_down` / `ic_speed_up`;
   `✕` becomes `ic_close`; `🌐` becomes `ic_globe_24dp`; `∞` becomes the words «без ограничений».
7. **Optical alignment.** A glyph is optically centred in its tile, not mathematically centred: the
   play triangle is nudged 1dp right, the chevron is not nudged, the shield sits 1dp low.

### 6.2 The icons this rebuild needs and the repo does not have

Draw these to the family rules before screen work begins. All 24dp viewport, 2dp stroke.

| New drawable | Used by |
|---|---|
| `ic_search.xml` | Servers search field (currently `ic_outline_filter_alt_24`, the wrong glyph) |
| `ic_close.xml` | Search clear, sheet dismiss, chip dismiss |
| `ic_speed_down.xml`, `ic_speed_up.xml` | Home numeric strip |
| `ic_clock.xml` | Home numeric strip (uptime) |
| `ic_unfold_more.xml` | The cycle-in-place affordance (8.2) |
| `ic_sort.xml` | Servers sort control |
| `ic_warning.xml`, `ic_info.xml`, `ic_error.xml` | Status strip, error states, the subscription state chip |
| `ic_logout.xml` | Account, «Выйти» |
| `ic_link.xml` | Account, «Привязать Telegram» |
| `ic_qr.xml` | Subscription QR, TV transfer |
| `ic_device_android.xml`, `ic_device_apple.xml`, `ic_device_windows.xml`, `ic_device_router.xml`, `ic_device_unknown.xml` | Devices list, one glyph per platform (desktop already resolves these) |
| `ic_eye.xml`, `ic_eye_off.xml` | Password fields (rename the existing `ic_lp_eye*` and use everywhere) |
| `ic_chevron_down.xml` | Expandable rows (rotates 0 to 90 for the inline-expand affordance) |

### 6.3 The unified server icon (owner request 0.4.7)

**One treatment for a server, on every surface it appears: the list row, the connect hero's server
row, the action sheet header, the notification, the TV screen, the widget.**

```
40dp tile, @dimen/radius_tile 12, fill @color/icon_tile_neutral #20242B
└── 28dp flag raster, centred, corner-clipped to radius 8 (a 12dp radius on a 28dp square
    over-rounds; the clip is 8 so it reads as the same family)
    fallback when no country resolves: 22dp ic_globe_24dp in @color/icon_glyph_neutral
```

- Flags are the **only** raster assets in the app. Port the desktop set:
  `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/Flags/*.png` to
  `res/drawable-nodpi/flag_xx.png`, plus `flag_xx.png` as the unknown fallback.
- `util/FlagUtil.kt` stops returning emoji and starts returning a drawable resource id.
- A `StripLeadingFlag` transform runs on every server remark before it reaches a `TextView`, so
  «🇳🇱 Netherlands, Amsterdam» renders as the tile plus «Netherlands, Amsterdam» and never both.
  Desktop already ships `StripLeadingFlagConverter`; port its rules verbatim.
- The selected server on Home, in the notification and on the widget uses the **same 40dp tile**.
  No screen invents its own server visual.

### 6.4 Glyph-to-row assignment for the settings tree

Every settings row's glyph is fixed here so no implementer picks one. All neutral tiles.

| Row | Glyph |
|---|---|
| Режим подключения | `ic_power_settings` |
| Прокси по приложениям | `ic_per_apps_24dp` |
| Маршрутизация | `ic_routing_24dp` |
| DNS | `ic_globe_24dp` |
| Локальный прокси | `ic_hub_local_proxy` |
| Обход блокировок | `ic_lock_24dp` |
| Проверка серверов | `ic_ping_24dp` |
| Провайдеры | `ic_subscriptions_24dp` |
| Что настроил провайдер | `ic_ps_fingerprint` |
| Перенести подписку | `ic_tv_24dp` |
| Оформление | `ic_palette_24dp` |
| Язык | `ic_description_24dp` |
| Запуск при загрузке | `ic_qu_start_24dp` |
| Резервное копирование | `ic_backup_24dp` |
| О приложении | `ic_about_24dp` |
| Купить подписку | `ic_acc_upgrade` (accent tile: this is Account's primary action row) |
| Устройства | `ic_acc_devices` |
| История платежей | `ic_acc_history` |
| Привязать Telegram | `ic_link` |
| Выйти | `ic_logout` |

---

## 7. Motion choreography

### 7.1 The vocabulary. Nothing outside this table exists

`res/values/motion.xml` plus `res/interpolator/*.xml`. One new interpolator is required.

| Token | ms | Interpolator | Applies to |
|---|---|---|---|
| `motion_press_in` | 90 | `@interpolator/ease_out_quart` | Finger down: scale to 0.97 |
| `motion_press_out` | 160 | `@interpolator/ease_out_quint` | Release: scale back to 1.0 |
| `motion_state` | 220 | `@interpolator/ease_standard` | Tint crossfade, selection, enable/disable, tab crossfade, toolbar hairline |
| `motion_reveal` | 300 | `@interpolator/ease_out_quint` | Sheets, sub-page entrance, expand, first appearance of a block |
| `motion_exit` (new, 225) | 225 | `@interpolator/ease_standard` | Sub-page exit, sheet dismiss. 75% of reveal |
| `motion_stagger` | 40 | n/a | Per-item list delay, total capped at 400ms |
| `motion_emphasis` | 600 | `@interpolator/ease_out_quint` | Signature moment 1 only |
| `motion_handoff` (new, 450) | 450 | `@interpolator/ease_out_expo` (new file) | Sign-in to Home, once per session |

**Exit is 75% of enter.** State reverse = 165ms. Reveal reverse = 225ms.
**Ease-out only.** No bounce, no elastic, no spring, no linear, no `AccelerateDecelerate`.
The existing 900ms `AccelerateDecelerateInterpolator` skeleton pulse in `AccountFragment.kt:413-430`
is off-token and is deleted (see 8.9).

### 7.2 Press physics, unified

**One press language: scale 0.97, 90ms in `ease_out_quart`, 160ms out `ease_out_quint`.**

- `res/anim/press_scale.xml` changes from 0.96 to **0.97** and is applied via
  `android:stateListAnimator` to every clickable row, card, chip, tile and button in the app.
- `res/anim/nav_press.xml` (0.92 at 100/120ms) is **deleted**; the bottom navigation uses
  `press_scale` like everything else, with `android:background="@null"` so there is no ripple
  (owner request 0.4.8).
- Ripple: `?attr/selectableItemBackground` on rows and cards **in addition to** the scale, except
  the bottom navigation. Ripple alone is not a press state.
- Currently 8 of 71 layouts carry a press animation. The target is every clickable surface.

### 7.3 The choreography table

| Event | What moves | Duration | Curve | Notes |
|---|---|---|---|---|
| Tap anything | scale 1.0 to 0.97 | 90 | quart | Visible acknowledgement inside 100ms is a hard requirement |
| Release | scale 0.97 to 1.0 | 160 | quint | |
| Tab switch | outgoing alpha 1 to 0 **and simultaneously** incoming alpha 0 to 1 + translationY 8dp to 0 | 220 | standard (out), quint (in) | Simultaneous, not sequential. Plus `tickHaptic()`. The current 150+200 sequence is replaced |
| Bottom-nav indicator | pill translationX to the new item | 220 | quint | The pill moves; it does not fade and re-appear |
| Sub-page enter | translationX 16dp to 0 + alpha 0 to 1 | 300 | quint | `res/anim/subpage_enter.xml` |
| Sub-page exit | translationX 0 to 16dp + alpha 1 to 0 | 225 | standard | `res/anim/subpage_exit.xml` |
| Bottom sheet enter | standard Material sheet slide | 300 | quint | Scrim fades 0 to 60% over the same 300 |
| Bottom sheet exit | slide down | 225 | standard | |
| Toolbar hairline | alpha 0 to 1 | 220 | standard | On `scrollY > 0`; reverse at 0 |
| Row selection | background crossfade to P3 + state glyph alpha 0 to 1 | 220 | standard | |
| Switch toggle | Material's own switch animation, untouched | - | - | |
| Segmented change | thumb translationX + label weight step | 220 | quint | |
| Expand-in-place row | chevron rotation 0 to 90 + content height reveal | 220 / 300 | standard / quint | The only sanctioned height animation in the app |
| Skeleton to content | crossfade | 220 | standard | |
| List first load | per-item alpha 0 to 1 + translationY 8dp to 0 | 220, staggered 40 | quint | Cap total stagger at 400ms, so items 11+ arrive together. Fresh loads only, never on scroll |
| Connect: idle to connecting | arc appears, indeterminate sweep starts | 220 | standard | The sweep runs **only** while the core is negotiating |
| Connect: connecting to connected | signature moment 1 | 600 | quint | Section 1.2 |
| Connect: any to disconnected | shield crossfades to outline, arc removed | 220 | standard | No motion beyond the crossfade |
| Balance change | count-up from previous to new figure, reformatted per frame | 300 | quart | Only when the value changes while visible. First paint is instant |
| Status strip enter | translationY 8dp to 0 + alpha | 300 | quint | Exit 225 |
| Sign-in to Home | signature hand-off | 450 | **ease_out_expo** | Once per session, the only 450ms in the app |
| Screen rotation, theme change, font-scale change | nothing | 0 | - | State is restored, not animated |

### 7.4 What must NOT animate. Ever

- The page background. It never fades, tints, crossfades or parallaxes.
- The toolbar, other than its 1dp hairline.
- The wordmark.
- The bottom navigation bar itself (only the indicator pill moves).
- Skeletons. **They are static** (decision D-A5): a placeholder that pulses is a decoration that
  outlives its own purpose, and there is no token for 900ms.
- Any icon at rest. No idle spin, no breathe, no bob.
- The connect disc at idle. `bg_connect_glow`'s 850ms infinite-reverse breathe
  (`MainActivity.kt:1741`) is deleted with the drawable.
- The sonar at idle. One ring, once, at confirmation, and never otherwise.
- Section entrances. A screen appears; it does not perform. Stagger applies to a list of siblings,
  never to a screen's sections.
- Numbers, other than the one sanctioned balance count-up. A ping result lands; it does not tick up.
- Chips, badges, meters on first paint. A traffic meter animates only when the value changes while
  the screen is visible, over `motion_state` 220.
- Scroll-linked anything. No parallax, no collapsing hero, no scroll-driven alpha except the
  toolbar hairline.

### 7.5 Reduced motion is a contract

`util/MotionUtils.animationsEnabled(context)` / `View.reducedMotion()` already exists and already
guards the hero assemble, the connect confirm, the tab fade-through, the list stagger, the balance
count-up and the skeleton pulse. **Every new animator checks it and jumps to the end state.**
Declarative `stateListAnimator` collapses automatically at animator scale 0. An animation added
without the check is a P1 accessibility defect.

Under reduced motion:
- Signature moment 1 becomes an instant shield fill plus the haptic. No ring.
- Signature moment 2 becomes an instant appearance of the numeric strip.
- Tab switch, sub-page transitions and sheet transitions become instant.
- The list stagger becomes a single instant appearance.
- The balance count-up lands on the final figure.

### 7.6 Haptics

`View.pressHaptic()` on: connect, disconnect, purchase confirm, destructive confirm.
`View.tickHaptic()` on: tab switch, stepper increment, segmented change, switch toggle.
**Nothing else vibrates.** No haptic on scroll, on row tap, on navigation, on refresh.

---

## 8. The component atlas

Fourteen components. Every screen in this document is assembled from them. **Anything a screen
needs that is not here is a new component and gets added here first, with a spec, before it is
drawn in a layout.** The current build's central failure is that no component library exists, so 23
settings rows, 7 local-proxy rows, 9 provider rows, 4 backup rows and 6 sheet rows are six
hand-copies of the same object that drifted 2 to 4dp apart.

### 8.0 New tokens this atlas requires

Added to `res/values/dimens.xml` and `res/values/colors.xml` **before** any screen work, each with
a comment stating its purpose and, for colours, its measured contrast ratio.

```xml
<!-- dimens.xml additions -->
<dimen name="text_origin">68dp</dimen>        <!-- gutter 16 + tile 40 + gap 12; every hairline and every title -->
<dimen name="cta_height">52dp</dimen>         <!-- primary filled button; mirrors desktop Size.CtaTall -->
<dimen name="cta_height_secondary">48dp</dimen>
<dimen name="touch_min">48dp</dimen>          <!-- Material minimum; replaces view_height_dp48 in new code -->
<dimen name="toolbar_height">56dp</dimen>
<dimen name="glyph_toolbar">24dp</dimen>
<dimen name="glyph_inline">20dp</dimen>
<dimen name="glyph_chip">16dp</dimen>
<dimen name="chip_height">24dp</dimen>
<dimen name="meter_height">4dp</dimen>
<dimen name="stroke_hairline">1dp</dimen>
<dimen name="stroke_focus">2dp</dimen>
<dimen name="connect_disc">176dp</dimen>
<dimen name="connect_glyph">80dp</dimen>
<dimen name="connect_track">3dp</dimen>
<dimen name="empty_tile">56dp</dimen>
<dimen name="empty_glyph">28dp</dimen>
<dimen name="sheet_handle_width">32dp</dimen>
<dimen name="sheet_handle_height">4dp</dimen>
<dimen name="skeleton_bar_sm">14dp</dimen>
<dimen name="skeleton_bar_md">18dp</dimen>
<dimen name="nav_indicator_width">64dp</dimen>
<dimen name="nav_indicator_height">32dp</dimen>
<dimen name="flag_tile">28dp</dimen>
<dimen name="content_max_width">720dp</dimen>  <!-- values-sw600dp only -->
```

```xml
<!-- colors.xml / values-night/colors.xml additions -->
<!-- Warning. Amber is a status colour only: expiring, pending, meter over 90%. Never a button. -->
<color name="warning">#EAB308</color>          <!-- night: 9.6:1 on #141619 -->
<color name="warning">#A16207</color>          <!-- day:   4.9:1 on #FFFFFF -->
<!-- Chip fills: 12% of the status hue over the surface. Text uses the full hue. -->
<color name="chip_bg_success">#1F22C55E</color> <!-- green text on it: 6.6:1 -->
<color name="chip_bg_warning">#1FEAB308</color> <!-- amber text on it: 7.5:1 -->
<color name="chip_bg_error">#1FF04452</color>   <!-- #FF6069 on it:    5.5:1 -->
```

Deleted in the same change: `bg_home_gradient.xml` (+night, +mono), `bg_connect_glow.xml` (+mono),
`bg_bottom_nav_scrim.xml`, `bg_nav_header.xml`, `nav_header_bg.png`, `bg_traffic_gradient.xml`,
`bg_settings_glass.xml`, `bg_icon_green/orange/purple/yellow.xml`, `bg_chip_gold.xml`,
`bg_speed_chip.xml`, `bg_acc_option.xml`, `ripple_card.xml`, `ic_circle.xml`,
`res/font/montserrat_thin.ttf`, `res/menu/menu_bottom_nav.xml`,
`res/color/bottom_nav_item_color.xml`, `style/TabLayoutTextStyle`, `style/BrandedSwitch`,
`item_recycler_footer.xml`.

---

### 8.1 `Row` - the universal ledger row

**The single most important component in the product.** New file `res/layout/view_row.xml`,
replacing `layout_setting_row.xml` and `layout_setting_toggle_row.xml` (which are correct in spirit
and unused by any layout today) and replacing 60+ hand-inlined copies.

```
LinearLayout (horizontal, gravity center_vertical)
    minHeight            @dimen/row_min_height        56dp
    paddingStart/End     @dimen/screen_gutter         16dp
    paddingTop/Bottom    @dimen/space_8               8dp
    background           ?attr/selectableItemBackground
    stateListAnimator    @anim/press_scale
    focusable            true
├── FrameLayout  id=tile        40x40  (@dimen/tile_size)
│      background @drawable/bg_icon_neutral   (radius 12, #20242B)
│      └── ImageView  22x22 (@dimen/tile_glyph), tint @color/icon_glyph_neutral, gravity center
├── Space  12dp   (@dimen/space_12)
├── LinearLayout (vertical, weight 1)
│   ├── TextView  id=title      @style/TextAppearance.App.Title      maxLines 2, ellipsize end
│   └── TextView  id=subtitle   @style/TextAppearance.App.Subtitle   maxLines 2, marginTop 4dp
│                               visibility gone when empty
├── Space  12dp
├── TextView   id=value    @style/TextAppearance.App.Subtitle   marginEnd 8dp, maxLines 1
│                          (numeric variants add fontFeatureSettings via App.Numeric)
└── [ one trailing affordance ]
```

**The trailing affordance is exactly one of these six, and it declares what the row will do.**
This is the affordance-honesty grammar that `30-reference-analysis.md` 6.3a promotes to law, ported
from the desktop `SettingsView.axaml.cs:14-22` contract, and it is the direct answer to the owner's
demand that settings be one system «а не абы как».

| Trailing | Drawable / widget | Size | Promise to the user |
|---|---|---|---|
| Chevron | `ic_chevron_right` | 20dp, `?attr/colorOnSurfaceVariant` | Tapping pushes a screen |
| Rotating chevron | `ic_chevron_down`, rotation 0 to 90 over 220ms | 20dp | Tapping expands content inline, right here |
| Cycle | `ic_unfold_more` | 20dp | Tapping changes the value in place. No screen, no dialog |
| Switch | `MaterialSwitch` | Material default | This is a boolean, applied immediately |
| Nothing | - | - | This row is a read-only fact. It is not clickable, has no ripple, no press animation, and `focusable=false` |
| State marker | `ic_action_done` filled | 20dp, `?attr/colorPrimary` | This item is the current selection |

**The one sanctioned pairing.** A row may carry a `value` **and** a state marker (used by the
server row: ping value plus selected check). A state marker is not an action. No other pairing
exists: never a chevron and a switch, never two glyphs, never a value and a cycle glyph.

**Rules.**
- The whole row is the touch target, minimum 48dp, and it is 56dp in practice. Where the trailing
  is a switch, tapping the row toggles the switch.
- The hairline below a row is a sibling `View`, 1dp, `?attr/colorOutlineVariant`,
  `marginStart="@dimen/text_origin"`. The last row of a group has none.
- **The tile is always neutral** except the one accent row and the one destructive row per screen
  (3.4).
- A subtitle that restates its title is deleted, not written. «DNS / Настройки DNS» is noise.
  Say what the row does in six words or say nothing.
- **The current value belongs on the row** (Incy's single best idea, `30` 2.1.6). A user must be
  able to audit his configuration by scrolling once without opening anything. Rows that push a
  screen carry the value they will change: «DNS · Cloudflare», «Оформление · Тёмная»,
  «Прокси по приложениям · 12 приложений».

**Variants** (all the same file with a `style` or a bound visibility, never a new layout):
`Row.Navigation` (tile + title + value + chevron), `Row.Toggle` (tile + title + subtitle + switch),
`Row.Value` (tile + title + value + cycle), `Row.Fact` (tile + title + value, not clickable),
`Row.Destructive` (red tile + red title + no trailing), `Row.Selectable` (tile + title + subtitle +
value + state marker).

**States.** Default; pressed (scale 0.97 + ripple); focused (keyboard/TV: 2dp `?attr/colorPrimary`
outline at 2dp offset); disabled (content alpha 0.38, no ripple, no press, `isEnabled=false`);
selected (P3 background `?attr/colorSurfaceContainerHighest` + state marker).

---

### 8.2 `SectionHeader`

`@style/SettingsSectionLabel` applied to a `TextView`. 16sp/700, sentence case, no tracking, no
caps, `?attr/colorOnSurface`. Padding 16 / 24 top / 16 / 8 bottom.

**Never** an ALL-CAPS tracked eyebrow, never a divider under it, never a count in parentheses,
never blue. The 24dp above and 8dp below is the group rhythm; a header is what replaces a divider
between groups.

---

### 8.3 `Card`

```
MaterialCardView
    cardBackgroundColor  ?attr/colorSurface
    cardCornerRadius     @dimen/radius_card       20dp
    cardElevation        0dp
    strokeWidth          @dimen/stroke_hairline   1dp
    strokeColor          ?attr/colorOutlineVariant
    contentPadding       @dimen/space_16          16dp
```

**One card per screen, maximum.** It wraps the object the screen is about: the subscription on Home
and on Account, the checkout summary on Buy. A card is never inside a card. A card never contains
another bordered container; inside it, group with spacing and at most one hairline.

Screens that currently violate this and their fix:
`activity_buy_tariff.xml` (option rows inside a tariff card) becomes a list of tariff rows plus one
checkout card; `activity_devices.xml` and `activity_payment_history.xml` (N identical cards) become
divided lists; `activity_bypass_list.xml` (a card header above a card list) becomes a header block
plus a divided list.

---

### 8.4 `Chip`

```
TextView (or Chip with the Material chip style stripped to these values)
    minHeight        @dimen/chip_height    24dp
    paddingH         @dimen/space_8        8dp
    background       radius @dimen/radius_chip 12dp
    textAppearance   @style/TextAppearance.App.Chip     11sp/500
    (optional) leading 16dp glyph + 4dp gap
```

| Variant | Fill | Text | Glyph | Used for |
|---|---|---|---|---|
| Neutral | `?attr/colorSurfaceContainerHighest` | `?attr/colorOnSurfaceVariant` | none | Protocol (`VLESS`), transport, tariff badge, «JSON» |
| Success | `@color/chip_bg_success` | `?attr/colorTertiary` | `ic_action_done` 16dp | «Активна», «Оплачено» |
| Warning | `@color/chip_bg_warning` | `@color/warning` | `ic_warning` 16dp | «Истекает», «В обработке» |
| Error | `@color/chip_bg_error` | `@color/ping_bad` | `ic_error` 16dp | «Истекла», «Ошибка» |
| Selected | `?attr/colorPrimaryContainer` | `?attr/colorOnPrimaryContainer` | `ic_action_done` 16dp | A chosen filter or option |

**A chip never carries both a fill and a stroke** (that would be a hole with a rim, plane rule
2.1). A status chip **always** carries a word; the glyph is the second channel, never the only one.
The protocol chip's current 4.0:1 failure (`item_recycler_main.xml:86`, `chip_type_text` on
`colorPrimaryContainer`) is fixed by moving it to the Neutral variant, which measures 6.0:1.

---

### 8.5 `Button` - three tiers, no fourth

| Tier | Widget | Height | Radius | Fill | Label | Rule |
|---|---|---|---|---|---|---|
| Primary | `MaterialButton` filled | `@dimen/cta_height` 52dp | `@dimen/radius_pill` | `?attr/colorPrimary` | `App.Title` 16/700 in `?attr/colorOnPrimary` | **One per screen.** Full width at the gutter unless it sits in a row |
| Secondary | `MaterialButton` tonal | `@dimen/cta_height_secondary` 48dp | `@dimen/radius_pill` | `?attr/colorSecondaryContainer` | `App.Title` in `?attr/colorOnSecondaryContainer` | Never adjacent to another tonal button of equal weight |
| Tertiary | `MaterialButton` text | 48dp | - | none | `App.Title` in `?attr/colorPrimary` | Links, «Забыли пароль?», «Другой способ», undo |

- `app:cornerRadius="26dp"` appears on five buttons today and is deleted; `@dimen/radius_pill` 100
  fully rounds any of these heights.
- `android:textStyle="bold"` on a `MaterialButton` is a synthetic bold on a variable face and is
  deleted; weight comes from `App.Title`.
- Leading icon: 20dp, `app:iconPadding="8dp"`, tinted to the label colour.
- **Loading:** the label is replaced by a 20dp indeterminate `CircularProgressIndicator` in the
  label's colour, the button keeps its exact size so nothing reflows, and `isEnabled=false`.
- **Disabled:** alpha 0.38 on content, no ripple, no press animation.
- A destructive primary uses `?attr/colorError` as its fill and says what it destroys
  («Удалить устройство»), never «OK».

---

### 8.6 `Toolbar` - the seamless sub-page bar (owner request 0.4.6)

New shared layout `res/layout/view_toolbar.xml`, used by `activity_base.xml` and by every tab.

```
FrameLayout  height @dimen/toolbar_height 56dp, background ?attr/colorBackground
├── ImageButton  id=nav_back   48x48, gravity start|center_vertical, marginStart 4dp
│                 icon ic_chevron_left 24dp ?attr/colorOnSurface
│                 background ?attr/selectableItemBackgroundBorderless
│                 contentDescription "Назад"
│                 (absent on tab screens; the title then starts at the gutter)
├── TextView     id=title      @style/TextAppearance.App.Title, gravity start|center_vertical
│                 marginStart 56dp when a back button is present, else @dimen/screen_gutter
│                 maxLines 1, ellipsize end
└── ImageButton  id=action     48x48, gravity end|center_vertical, marginEnd 4dp
                  ONE action maximum; more go into an overflow whose glyph is ic_more_vert_24dp
── View  id=toolbar_hairline   height 1dp, background ?attr/colorOutlineVariant, alpha 0
```

- Background is `?attr/colorBackground`, the same as the page. **No `AppBarLayout` background tint,
  no elevation, no `liftOnScroll`, no shadow, no scrim.**
- The hairline animates its alpha 0 to 1 over `motion_state` 220ms when the attached scroll view
  passes `scrollY > 0`, and back to 0 at the top. That is the only permitted boundary.
- Tab screens use the same bar with no back button: Home carries the wordmark
  (`@style/ToolbarBrandTitle`) plus a `+` action; Servers, Аккаунт and Настройки carry their title
  plus at most one action. `MainActivity.showTab()` currently hides the `AppBarLayout` on every tab
  (`:441`), which is why the app has no title system; that line is deleted.

---

### 8.7 `BottomNav`

```
LinearLayout (horizontal)  height 56dp + navigationBar inset as bottom padding
    background ?attr/colorBackground
    a 1dp ?attr/colorOutlineVariant hairline sits above it as a sibling View
└── 4 x LinearLayout (vertical, weight 1, gravity center)
        minHeight 56dp, background @null (NO ripple - owner 0.4.8)
        stateListAnimator @anim/press_scale
    ├── FrameLayout 64x32 (@dimen/nav_indicator_*)
    │     background: radius 100 pill, ?attr/colorPrimaryContainer, alpha 0 when inactive
    │     └── ImageView 24dp glyph, centred
    └── TextView  @style/BottomNavLabel  11sp, marginTop 4dp
```

| | Inactive | Active |
|---|---|---|
| Indicator pill | alpha 0 | alpha 1, `?attr/colorPrimaryContainer` `#17325C` |
| Glyph | outline, `?attr/colorOnSurfaceVariant` | filled, `?attr/colorOnPrimaryContainer` `#CFE0FF` (9.57:1) |
| Label | `?attr/colorOnSurfaceVariant`, weight 500 | `?attr/colorOnSurface`, weight 700 |

Two channels (pill + weight), plus a third (glyph fill), and **no blue label**, so the accent budget
is spent on a tinted container rather than a saturated surface. The pill translates to the new
destination over 220ms `ease_out_quint` rather than fading out and in.

The bar is **always visible on every tab**, including first run. The current
`updateBottomNavVisibility()` (`MainActivity.kt:713`) hides the whole bar when signed out with no
servers, so a first-time user sees a bar-less screen and cannot discover the product; that is
deleted (decision D-A2).

At `sw600dp` the bar is replaced by a `NavigationRailView` with the same four destinations, the same
order, the same labels and the same indicator.

---

### 8.8 `EmptyState`

One grammar, replacing the three currently shipping (a card on Account, a 64dp tile block on
Devices, a `drawableTop` on a TextView on Payment history).

```
LinearLayout (vertical, gravity center_horizontal), maxWidth 320dp, centred in its container
├── FrameLayout 56x56 (@dimen/empty_tile), background radius 20 ?attr/colorSurfaceContainerHighest
│     └── ImageView 28dp (@dimen/empty_glyph), tint ?attr/colorOnSurfaceVariant
├── Space 16dp
├── TextView  title
│     full-screen empty  -> @style/TextAppearance.App.Headline  24sp/700
│     in-list / in-card  -> @style/TextAppearance.App.Title      16sp/700
│     gravity center
├── Space 8dp
├── TextView  line   @style/TextAppearance.App.Body  14sp, gravity center, maxWidth 320dp
├── Space 24dp
└── [ one action ]  primary filled 52dp if this is the screen's job, else tonal 48dp
```

**The formula is fixed** (`00-rules.md` 9.5): title says what is not here, one line says why or what
it gives you, one action. Never «Нет данных» alone, never two actions, never an illustration, never
a blue glyph, never an emoji.

---

### 8.9 `Skeleton`

**Static.** Fill `?attr/colorSurfaceContainerHigh` `#1A1D21`, radius 12 for bars and 20 for card
silhouettes. No pulse, no shimmer, no animator (decision D-A5: the 900ms
`AccelerateDecelerateInterpolator` in `AccountFragment.kt:413-430` is off-token and is deleted).

- A skeleton is **the shape of the result**, not a grey block: a subscription skeleton is a 20dp
  card with a 18dp title bar, a 14dp subtitle bar and a 4dp meter bar at the real positions; a
  server-list skeleton is six 56dp rows each with a 40dp tile square and two bars at 68dp.
- It appears only after **300ms** of waiting; a faster response never flashes a skeleton.
- Skeleton to content is a 220ms `ease_standard` crossfade.
- A centred indeterminate spinner over a blank screen is banned as a content loading state
  (`00-rules.md` 15). It survives only inside a button and inside a 48dp inline slot.

---

### 8.10 `StatusStrip` - the feedback surface

The app currently ships **zero Snackbars and about forty Toasts**, plus one deprecated custom-view
`Toast` (`toast_status.xml`, bottom gravity at a magic 110dp offset) that is invisible on Android
12+ when the app is not foreground. All of it is replaced by one component
(`30-reference-analysis.md` 6.2c).

```
LinearLayout (horizontal, gravity center_vertical)  id=status_strip
    layout_width match_parent, minHeight 48dp
    background ?attr/colorSurfaceContainerHigh  (P2 - it is transient by definition)
    a 1dp ?attr/colorOutlineVariant hairline sits above it
    paddingH 16dp, paddingV 12dp
    anchored directly above the bottom navigation, respecting the navigation-bar inset
├── ImageView 20dp   ic_info / ic_warning / ic_error, tinted onSurfaceVariant / warning / ping_bad
├── Space 12dp
├── TextView  @style/TextAppearance.App.Body, weight 1, maxLines 2
└── TextView  action  @style/TextAppearance.App.Title in ?attr/colorPrimary, minWidth 48dp,
               paddingH 8dp, 48dp touch height
```

- Enter `motion_reveal` 300 `ease_out_quint` (translationY 8dp to 0 + alpha), exit 225 `standard`.
- **Successes auto-dismiss at 5s. Errors persist until acted on or dismissed.** An error the user
  did not see is an error we did not report.
- One strip at a time; a new message replaces the current one with a 220ms crossfade of the text.
- Never floats, never overlays the connect control, never appears over a dialog or a sheet.
- Reduced motion: snap to the end state.
- It also gives the durable half a home: a full log lives under Настройки › О приложении › Журнал.

The permitted survivors of `Toast`: none. `toast_status.xml` and `bg_toast_status.xml` are deleted.

---

### 8.11 `Meter` - traffic and device allowance

```
LinearLayout (vertical)
├── LinearLayout (horizontal)
│   ├── TextView label   @style/TextAppearance.App.Subtitle, weight 1     "Трафик"
│   └── TextView value   @style/TextAppearance.App.Subtitle + Numeric     "12,4 из 50 ГБ"
├── Space 8dp
└── ProgressBar (horizontal, determinate)
      height @dimen/meter_height 4dp, radius pill
      track ?attr/colorSurfaceContainerHighest
      fill  ?attr/colorPrimary   (>= 90% -> @color/warning)
```

**The label is never printed on top of the fill.** The current subscription meta bar prints an 11sp
label centred over a moving `?attr/colorPrimary` fill, measured at **2.9:1 and changing mid-word as
the bar advances** (`layout_subscription_meta_bar.xml:163-176`). That component is deleted.

Unlimited renders as «12,4 ГБ · без ограничений» with **no bar at all**. A `subscription-userinfo: 0`
directive hides the whole block rather than showing `0 B / 0 B`.

---

### 8.12 `InputField`

```
com.google.android.material.textfield.TextInputLayout
    style   OutlinedBox
    boxCornerRadius* @dimen/radius_chip 12dp
    boxBackgroundColor ?attr/colorSurfaceContainerHighest   (P3 - a field is a hole in the panel)
    boxStrokeColor  ?attr/colorOutline / focused ?attr/colorPrimary / error ?attr/colorError
    hintEnabled false          <-- the placeholder is NEVER the label
└── TextInputEditText  height 52dp, @style/TextAppearance.App.Body, cursor ?attr/colorPrimary
```
with, above and below it:
```
TextView  label   @style/TextAppearance.App.Caption, marginBottom 4dp     <- always visible
[ field ]
TextView  helper  @style/TextAppearance.App.Caption, marginTop 4dp, minHeight 16dp
                  <- present in the markup even when empty, so the layout never jumps
                  error state: text @color/ping_bad + field stroke ?attr/colorError
```

- Validate **on blur**, never per keystroke.
- After a failed submit, focus moves to the first invalid field.
- Correct `android:inputType` and `android:autofillHints` per field, always.
- Password fields carry a 48dp show/hide toggle (`ic_eye` / `ic_eye_off`).
- **IME insets are applied** so the focused field is never under the keyboard. No screen in the app
  currently applies `ime()` insets; every form screen in this document does.

---

### 8.13 `Segmented`, `Stepper`, `SearchField`

**Segmented** - `MaterialButtonToggleGroup`, 2 to 4 options only, 5+ becomes a push screen.
Height 48dp, container radius 12 with fill `?attr/colorSurfaceContainerHighest`, thumb =
`?attr/colorPrimaryContainer` with `?attr/colorOnPrimaryContainer` label at weight 700; unselected
label `?attr/colorOnSurfaceVariant` at weight 500. Thumb slides 220ms `ease_out_quint`.
It replaces four of the six single-choice `AlertDialog`s.

**Stepper** - two 48dp `MaterialButton.IconButton`, radius 12, fill
`?attr/colorSurfaceContainerHighest`, glyph 20dp `?attr/colorOnSurface`, with a value between them
in `App.Title` + Numeric in a 48dp-minimum box. Disabled uses alpha **0.38** (the current
imperative `alpha = 0.4f` in `BuyTariffActivity:616` is off-token).

**SearchField** - 48dp, radius 12, fill `?attr/colorSurfaceContainerHighest`, leading 20dp
`ic_search` at 12dp padding, hint `?attr/colorOnSurfaceVariant` (6.0:1 on P3), trailing 40dp clear
button with `ic_close` visible only when non-empty. Filters in place; never navigates. Its empty
result is a designed state, not a blank list. `bg_search_pill.xml` (radius 14) is replaced by
`bg_input_field.xml` (radius 12).

---

### 8.14 `Sheet` and `Dialog`

**Sheet** (`BottomSheetDialogFragment`) is the per-item action surface and the choice-among-many
surface.

```
background @drawable/bg_sheet_top   (radius_sheet 24dp top only, ?attr/colorSurface)
scrim 60% ?attr/colorScrim
├── View handle  32x4 (@dimen/sheet_handle_*), radius 100, ?attr/colorOutline, marginTop 12dp
├── [ optional header: 40dp tile + title + subtitle, 56dp, gutter 16 ]
├── 1dp hairline at 68dp   (only between the header and the first row)
├── rows, 56dp, from 8.1, neutral tiles
└── bottom padding = navigationBar inset + 16dp
```

Esc and system Back close it, focus returns to the trigger, and the trigger keeps its position.

**Dialog** - `MaterialAlertDialogBuilder` with the existing `ThemeOverlay.Departament.Dialog`
(radius 20, themed title, accent text buttons). **A dialog is the last resort.** It survives for
exactly two purposes:

1. A genuinely irreversible, costly action: deleting a device, deleting a server, deleting a
   subscription, restoring a backup, resetting all settings. The confirm button is red, says the
   noun it destroys («Удалить устройство»), and sits on the right; «Отмена» is neutral on the left.
2. A single free-text entry that has no sensible inline home: the manual-config paste box.

Everything else that is a dialog today becomes inline, a segment, a cycle row, a push screen or a
sheet. See section 24.6 for the full conversion table.

Everything reversible uses **undo instead of confirmation**: the item is removed immediately and
the status strip offers «Отменить» for 5 seconds.


---

## 9. Information architecture and navigation

### 9.1 The destination set

Four destinations, always visible, in this order. `30-reference-analysis.md` 5.8 flagged that
Android and desktop disagree and that the parity contract (`00-rules.md` 13) requires one answer;
this is it, and desktop follows Android here because Android is the surface with the real user
volume and because a VPN with 80 to 150 servers per provider needs servers to be a destination.

| # | Id | Label | Glyph | Purpose |
|---|---|---|---|---|
| 1 | `nav_home` | **Главная** | `ic_nav_home` | Connect, and the state of your subscription |
| 2 | `nav_servers` | **Серверы** | `ic_nav_servers` | Choose where you come out |
| 3 | `nav_account` | **Аккаунт** | `ic_nav_account` | Money, subscription, devices, payments |
| 4 | `nav_settings` | **Настройки** | `ic_nav_settings` | Everything else |

Three changes from today:

1. **«Сервера» becomes «Серверы».** The current form is a colloquial plural
   (`@string/title_servers`, `@string/bottom_nav_servers`).
2. **Аккаунт moves ahead of Настройки** and is **always present**, signed in or out. Today
   `updateAccountGate()` (`MainActivity.kt:1048-1064`) removes the tab when signed out, which makes
   the bar change shape under the user and hides the product's whole commercial half from a new
   installer. Signed out, the destination is a sign-in gate (decision D-A3, and it answers the open
   question at the end of `21-account-survey.md`).
3. **The bar never disappears.** See 8.7.

### 9.2 The map

```
MainActivity  (singleTask, edge-to-edge, no ActionBar, four fragments in a FragmentContainerView)
│
├── Главная            HomeFragment
│     ├── + (toolbar)               -> AddSourceSheet          [sheet]
│     │                                  ├── ScannerActivity   [full screen]
│     │                                  └── ManualAddDialog   [dialog]
│     ├── subscription card action -> BuyTariffActivity / DeviceManagementActivity
│     └── server row               -> tab 2
│
├── Серверы            ServersFragment
│     ├── row tap                  -> select (stays)
│     ├── row long-press           -> ServerActionsSheet       [sheet]
│     │                                  ├── Изменить          -> ServerActivity + 8 siblings
│     │                                  ├── Поделиться (QR)   -> QrSheet
│     │                                  └── Удалить           -> undo strip
│     ├── section header tap       -> collapse / expand
│     └── overflow                 -> 4 list actions
│
├── Аккаунт            AccountFragment
│     ├── Купить / Продлить        -> BuyTariffActivity
│     ├── Устройства               -> DeviceManagementActivity
│     ├── История платежей         -> PaymentHistoryActivity
│     ├── Привязать Telegram       -> TelegramLinkSheet
│     ├── Пополнить                -> TopUpSheet -> PaymentMethodSheet
│     └── Выйти                    -> confirm dialog
│
└── Настройки          SettingsFragment  (hub, 15 rows in 4 groups)
      ├── Режим подключения        -> ConnectionSettingsActivity
      ├── Прокси по приложениям    -> PerAppProxyActivity -> AppPickerActivity
      ├── Маршрутизация            -> RoutingSettingActivity -> RoutingEditActivity
      ├── DNS                      -> DnsSettingsActivity
      ├── Обход блокировок         -> CircumventionActivity
      ├── Проверка серверов        -> PingSettingsActivity
      ├── Локальный прокси         -> LocalProxyActivity
      ├── Провайдеры               -> ProviderSettingsActivity -> SubEditActivity
      ├── Что настроил провайдер   -> OperatorSettingsActivity
      ├── Перенести подписку       -> TvSendActivity / TvReceiveActivity
      ├── Оформление               -> AppearanceActivity
      ├── Язык                     -> LanguageActivity
      ├── Запуск при загрузке      -> switch, in place
      ├── Резервное копирование    -> BackupActivity
      └── О приложении             -> AboutActivity -> LogcatActivity, UrlSchemeListActivity
```

**Depth law: two levels below a tab, never three** (`03-direction.md` 7.3). `Настройки ->
Маршрутизация -> правило` is the maximum. `Настройки -> Провайдеры -> провайдер -> сервер` would be
three and is why the server editor is reached from the Servers tab's action sheet instead.

### 9.3 The shell: `MainActivity`

**Files today:** `ui/MainActivity.kt` (2 777 lines, the largest file in the app),
`res/layout/activity_main.xml` (705 lines). **Verdict: REBUILD.**

`MainActivity` is not a container today; it is one layout holding four sibling `View` groups toggled
by `isVisible`, with `layout_settings_content.xml` (1 536 lines) inlined into it and only the
Account tab being a real Fragment. That is why the file is 2 777 lines and why the four tabs drifted
into four design languages.

**Target structure:**

```
activity_main.xml
├── FragmentContainerView   id=nav_host, weight 1     <- HomeFragment | ServersFragment |
│                                                        AccountFragment | SettingsFragment
├── FrameLayout             id=status_strip_host      <- 8.10, gone by default
├── View                    1dp ?attr/colorOutlineVariant
└── LinearLayout            id=bottom_nav             <- 8.7
```

- Four fragments, each owning its own toolbar (8.6), its own scroll container and its own state
  machine. `MainActivity` keeps: the connect state machine (`applyRunningState`, the watchdog,
  one-shot event consumption, live-transition gating - this is careful, correct work and is
  preserved verbatim), the service binding, the deep-link routing, and the tab switch.
- `layout_settings_content.xml` is deleted, not moved.
- **Insets:** one strategy for the whole app. `WindowCompat.setDecorFitsSystemWindows(window,
  false)`; the fragment container consumes the top system-bar inset; the bottom navigation consumes
  the navigation-bar inset; every scrolling child adds `navigationBar + 56 + 16` bottom padding;
  every form applies `ime()`. `activity_base.xml`'s `android:fitsSystemWindows="true"` is removed so
  the two current strategies become one.
- **Back.** Three competing handlers exist today and handler 2 (`onKeyDown`, `MainActivity.kt:2298`)
  returns `true` unconditionally and calls `moveTaskToBack(false)`, so the app **never finishes on
  Back** and predictive Back is not declared anywhere. Target:
  1. `android:enableOnBackInvokedCallback="true"` in `AndroidManifest.xml`.
  2. One `OnBackPressedCallback`: if a sheet is open, close it; else if the current tab is not
     Главная, go to Главная; else disable and re-dispatch so the system finishes the activity.
  3. `onKeyDown`'s `KEYCODE_BACK` branch is deleted.
  4. Back restores scroll position, the search query, the filter state and the expanded/collapsed
     state of every server group.

**Tab switch.** Simultaneous crossfade, 220ms; incoming also rises 8dp; `tickHaptic()`; the
indicator pill translates. Each fragment keeps its scroll position and its state across switches.

### 9.4 Cold start and the first frame

**Files today:** `AndroidManifest.xml`, `themes.xml`. **Verdict: REBUILD (the splash does not exist
as a designed thing).**

```
Android 12+  androidx.core.splashscreen
    windowSplashScreenBackground        ?attr/colorBackground   #0A0B0D dark / #F4F7FC light
    windowSplashScreenAnimatedIcon      ic_launcher_foreground, static, no animation
    windowSplashScreenIconBackgroundColor  none
    postSplashScreenTheme               Theme.Departament
    exit                                 the platform default fade. No custom exit animation
Android < 12  a themed window background of the same colour and nothing else.
```

**The first frame after the splash is always Главная.** Never the sign-in screen, never onboarding,
never a "choose your setup" wizard (decision D-A4). A user who has never signed in sees Главная in
its first-run state, which teaches the two things that must happen («Войти» and «Подключить») by
showing them, not by narrating them.

Cold start budget: first frame under 1s on a mid-range device. No synchronous I/O, JSON parsing or
crypto on the main thread in the launch path; the server list, the subscription and the profile all
load asynchronously behind skeletons.

---

# PART II - THE SCREENS

---

## 10. Sign-in - «Вход»

### 10.1 Purpose

One screen that turns a person into an account holder by the shortest path his situation allows,
with one obvious primary route and every alternative demoted to text.

### 10.2 Files today, and the verdict

| File | Lines | Fate |
|---|---|---|
| `res/layout/activity_login.xml` | 314 | **REBUILD from scratch** |
| `ui/LoginActivity.kt` | - | Rebuild the view layer; keep the Telegram poll, the token exchange and the 2FA exchange |
| `res/values/strings_auth.xml` | - | Rewrite, see 10.5 |
| `res/layout/layout_home_empty.xml` (the card that is the *real* sign-in for 100% of new users) | 139 | **Delete.** Its job moves into Главная's first-run state (11.4) and into this screen |
| `res/layout/layout_home_account.xml`'s `group_login` block | - | **Delete.** Permanently hidden dead markup containing a `✕` text glyph |

**Why rebuild.** Graded **D-** in `31-self-assessment.md`: two identical cards encoding "these are
equally weighted choices" when Telegram is one tap and site login is email plus password plus a
possible TOTP; **four blue controls on one screen** against a budget of one; the error line rendered
at the bottom of the scroll view *after both cards*, possibly below the fold; no helper slot so the
card jumps when validation appears; the 2FA block inserted *between* the submit button and the
register button so the layout mutates mid-login; `cornerRadius="26dp"` five times; a platform
`ProgressBar` instead of the Material indicator; and it inherits the sub-page toolbar chrome so it
looks like a settings page with two password fields on it. The owner named this screen. It is the
first screen a paying user sees and it is our worst.

### 10.3 Component tree

Root: `ScrollView` on `?attr/colorBackground`, `fillViewport`, IME insets applied, no toolbar and
no card. Content column: `layout_width` match_parent, `maxWidth 480dp`, centred,
`paddingHorizontal @dimen/screen_gutter`.

```
[ status-bar inset ]
[ 48dp ImageButton ic_chevron_left, start, only when reached from inside the app ]
[ 32 ]
FrameLayout 64x64, radius @dimen/radius_card 20, fill ?attr/colorPrimaryContainer
  └── ImageView 32dp ic_shield_outline, tint ?attr/colorOnPrimaryContainer          [9.57:1]
[ 16 ]
TextView   "departament"        @style/ToolbarBrandTitle    20sp/700 onBackground
[ 24 ]
TextView   "Вход"               @style/TextAppearance.App.Headline    24sp/700
[ 8 ]
TextView   "Почта и пароль, или Telegram"   @style/TextAppearance.App.Subtitle   13sp
[ 32 ]
TextView   "Почта"              @style/TextAppearance.App.Caption
[ 4 ]
TextInputLayout  id=til_email   8.12, inputType=textEmailAddress, autofillHints=emailAddress
[ 4 ]
TextView   id=helper_email      @style/TextAppearance.App.Caption, minHeight 16dp   [reserved]
[ 16 ]
TextView   "Пароль"             @style/TextAppearance.App.Caption
[ 4 ]
TextInputLayout  id=til_password  8.12, inputType=textPassword, 48dp eye toggle,
                                  autofillHints=password, imeOptions=actionDone
[ 4 ]
TextView   id=helper_password   @style/TextAppearance.App.Caption, minHeight 16dp   [reserved]
[ 8 ]
MaterialButton text  "Забыли пароль?"   48dp, gravity end
[ 16 ]
MaterialButton filled  id=btn_login  "Войти"   52dp, full width, radius_pill    <- THE lit element
[ 24 ]
LinearLayout (horizontal, gravity center_vertical)
  ├── View 1dp ?attr/colorOutlineVariant, weight 1
  ├── TextView "или"  @style/TextAppearance.App.Caption, paddingH 12dp
  └── View 1dp ?attr/colorOutlineVariant, weight 1
[ 24 ]
MaterialButton tonal  id=btn_telegram  "Войти через Telegram"  48dp, full width,
                      leading 20dp ic_telegram_24dp
[ 12 ]
MaterialButton text   id=btn_register  "Создать аккаунт"  48dp, full width
[ 32 ]
```

**Everything that is not «Войти» is text or tonal.** The screen carries one filled accent surface,
one tinted tile, and one focus ring at a time: three accent elements against a budget of four.

### 10.4 States

| State | Rendering |
|---|---|
| **First run** (reached from Главная) | Exactly the tree above, with no back button. Nothing is pre-filled, nothing is focused, the keyboard is not raised |
| **Reached from inside the app** | Identical plus the 48dp back button; Back and the button both return to the caller |
| **Focused field** | 2dp `?attr/colorPrimary` box stroke; the helper line is empty; the field scrolls above the IME |
| **Field error** | Field stroke `?attr/colorError`, helper text `@color/ping_bad` 12sp, focus moves to the first invalid field on a failed submit. Validation runs **on blur**, never per keystroke |
| **Submitting** | `btn_login`'s label swaps for a 20dp indeterminate indicator in `?attr/colorOnPrimary`; the button keeps its 52dp height and full width; every control on the screen is disabled; the keyboard is dismissed |
| **Auth error** | The status strip (8.10) appears above the bottom edge with the cause and «Повторить». The strip persists. No dialog. The `BuildConfig.DEBUG`-only raw-detail dialog is kept, debug-only |
| **Awaiting Telegram** | `btn_telegram` is replaced in place by a 48dp block: 20dp indeterminate indicator + «Ждём подтверждения в Telegram…» in Body + a text button «Начать заново». The email form stays visible and enabled, so the user can change his mind without cancelling anything |
| **Two-factor** | The whole column is **replaced by a step**, not extended: back affordance, 64dp tile, «Подтверждение» Headline, «Введите 6-значный код из приложения» Subtitle, a single 6-digit `InputField` (`inputType=numberPassword`, `maxLength=6`, `autofillHints=smsOTPCode`), a 52dp «Подтвердить» CTA, and a text button «Другой способ входа». Back returns to the password step with the email preserved |
| **Offline** | The status strip shows «Нет подключения к интернету. Проверьте сеть и повторите.» with «Повторить»; `btn_login` and `btn_telegram` are disabled at alpha 0.38 |
| **Rate limited** | Status strip: «Слишком много попыток. Повторите через минуту.», no action; the CTA stays disabled with a live countdown in its label: «Повторить через 42 с» |
| **Success** | No success screen, no checkmark flourish. The screen hands off (10.7) |
| **Long content** | A 64-character email ellipsises at the end inside the field; the error helper wraps to 2 lines and the column grows |
| **Font scale 200%** | The column scrolls; nothing clips; the CTA stays 52dp tall with a wrapped label if needed |

### 10.5 Copy

All strings move to `res/values/strings_auth.xml` and are rewritten. No dashes, no exclamation
marks, sentence case, no trailing periods on labels.

| Resource | Value |
|---|---|
| `auth_title` | `Вход` |
| `auth_subtitle` | `Почта и пароль, или Telegram` |
| `auth_label_email` | `Почта` |
| `auth_label_password` | `Пароль` |
| `auth_hint_email` | `name@example.com` |
| `auth_hint_password` | `Не менее 8 символов` |
| `auth_forgot` | `Забыли пароль?` |
| `auth_submit` | `Войти` |
| `auth_or` | `или` |
| `auth_btn_telegram` | `Войти через Telegram` |
| `auth_register` | `Создать аккаунт` |
| `auth_awaiting` | `Ждём подтверждения в Telegram…` |
| `auth_restart` | `Начать заново` |
| `auth_2fa_title` | `Подтверждение` |
| `auth_2fa_desc` | `Введите 6-значный код из приложения` |
| `auth_2fa_label` | `Код` |
| `auth_2fa_submit` | `Подтвердить` |
| `auth_2fa_other` | `Другой способ входа` |
| `auth_err_email_empty` | `Введите почту` |
| `auth_err_email_format` | `Похоже, в адресе опечатка. Пример: name@example.com` |
| `auth_err_password_empty` | `Введите пароль` |
| `auth_err_credentials` | `Неверная почта или пароль.` |
| `auth_err_code` | `Код состоит из 6 цифр.` |
| `auth_err_code_wrong` | `Код не подошёл. Проверьте приложение и повторите.` |
| `auth_err_gone` | `Ссылка устарела. Начните заново.` |
| `auth_err_unavailable` | `Сервис временно недоступен. Повторите через несколько минут.` |
| `auth_err_network` | `Нет подключения к интернету. Проверьте сеть и повторите.` |
| `auth_err_rate` | `Слишком много попыток. Повторите через минуту.` |
| `auth_err_rate_countdown` | `Повторить через %1$d с` |
| `auth_err_not_configured` | `Вход недоступен в этой сборке.` |
| `auth_err_generic` | `Что-то пошло не так. Повторите попытку.` |
| `auth_telegram_missing` | `Telegram не установлен. Откройте вход через почту.` |
| `auth_retry` | `Повторить` |

Deleted: `auth_tg_headline`, `auth_tg_desc`, `auth_site_headline`, `auth_site_desc`, `auth_btn_site`,
`auth_register_site`, `auth_fields_required`, `auth_email_invalid`, `auth_success`.

### 10.6 Interaction

- `btn_login` is disabled until both fields are non-empty. It never submits twice.
- `btn_telegram` opens `t.me/<bot>?start=auth_<token>` in a Custom Tab and starts the existing
  2-second poll. The held tab reference is closed on confirmation - **do not remove this**, it is
  what avoids the "switch back and close the tab yourself" complaint.
- `Забыли пароль?` opens `https://departament.site/reset` in a Custom Tab. If
  `RequestPasswordReset` is ported to the Android API client, it becomes an inline flow instead.
- `Создать аккаунт` opens `https://departament.site` in a Custom Tab.
- The referral code captured from a deep link is forwarded on **every** sign-up path, including the
  Telegram one. The site's known gap (Telegram sign-up loses attribution) is not reproduced here.
- IME `actionDone` on the password field submits.
- Nothing on this screen is a `Toast`.

### 10.7 Transitions

- **In:** from Главная's first-run CTA or the Аккаунт gate. Sub-page enter, 300ms
  `ease_out_quint`, translationX 16dp to 0 plus alpha.
- **Out on success:** the one hand-off in the product. The sign-in column fades and scales
  0.98 to 1.0 while Главная fades in beneath it, over `motion_handoff` **450ms**
  `ease_out_expo`. It happens once per session. Under reduced motion it is an instant cut.
  Главная arrives with its subscription card already in skeleton, never blank.
- **Out on Back:** sub-page exit 225ms, returning to whatever called it with its scroll intact.

---

## 11. Главная - launch, connect, and first run

### 11.1 Purpose

Turn the tunnel on in four seconds with a gloved thumb on a dark train, and say in one glance
whether the subscription behind it is healthy.

### 11.2 Files today, and the verdict

| File | Fate |
|---|---|
| `res/layout/activity_main.xml` lines 42-454 (`group_home`) | **REBUILD** as `res/layout/fragment_home.xml` |
| `ui/MainActivity.kt` home half | Split into `ui/HomeFragment.kt`; the connect state machine moves with it unchanged |
| `res/layout/layout_home_account.xml` (155 lines for one 36dp avatar row plus a dead signed-out half) | **Delete.** The account entry point is the bottom-navigation destination |
| `res/layout/layout_home_empty.xml` | **Delete.** First run becomes a state of this screen |
| `res/layout/layout_subscription_meta_bar.xml` (257 lines) + `ui/HomeMetaPagerAdapter.kt` | **Delete.** Replaced by the subscription card, 11.6 |
| `res/drawable/bg_home_gradient.xml` (+ night, + mono), `bg_connect_glow.xml` (+ mono), `bg_bottom_nav_scrim.xml`, `bg_connect_ring.xml` (+ mono) | **Delete** |
| `res/anim/connect_confirm.xml`, `shield_assemble.xml` | Keep, retimed to 7.1 |

**Why rebuild.** Graded **C-**. The first frame of the product is a navy radial gradient
(`#1B2D50` through `#0E141F` to `#0A0B0D`, 560dp) plus a blue radial glow plus two concentric blue
rings plus a 212dp indeterminate sweep plus a 176dp disc plus a 230dp ring frame: **five layers to
communicate one boolean**, and four of them carry no information. That is the category reflex
verbatim and it is four separate law violations in the most-seen pixels of the app. On top of it:
three live counters showing zero at the top of the page before the user has done anything, built
around a 42dp invisible spacer used to fake optical centring; `↑` and `↓` as literal text; a memory
card labelled "App memory" in English, gated on a preference no UI can reach; two runtime-toggled
spacers faking vertical centring; and a seven-affordance subscription carousel whose long-press
silently deletes a subscription.

### 11.3 Component tree - the default (populated) state

Root: `NestedScrollView` on `?attr/colorBackground`, `fillViewport`, bottom padding
`navigationBar + 56 + 16`.

```
Toolbar 56dp  (8.6, no back button)
  ├── TextView "departament"   @style/ToolbarBrandTitle, marginStart @dimen/screen_gutter
  └── ImageButton 48dp  ic_add_24dp 24dp ?attr/colorOnSurface   cd "Добавить сервер"
[ 32 ]
FrameLayout   id=connect_disc   176x176 (@dimen/connect_disc), layout_gravity center_horizontal
    background: 176dp oval, fill ?attr/colorSurfaceContainerHighest (P3),
                stroke 1dp ?attr/colorOutline
    stateListAnimator @anim/press_scale, contentDescription bound to the state word
  ├── CircularProgressIndicator  id=connect_arc  176dp, trackThickness @dimen/connect_track 3dp,
  │     indicatorColor ?attr/colorPrimary, trackColor @android:color/transparent,
  │     indeterminate, visibility GONE except while negotiating
  ├── ImageView  id=img_shield_outline  80dp (@dimen/connect_glyph) ic_shield_outline,
  │     tint ?attr/colorOnSurfaceVariant
  ├── ImageView  id=img_shield_filled   80dp ic_shield_filled, tint ?attr/colorPrimary, alpha 0
  └── View       id=view_connect_pulse  176dp oval, 1dp ?attr/colorPrimary stroke, alpha 0
                 (signature moment 1 only)
[ 16 ]
LinearLayout (horizontal, gravity center)   id=status_line
  ├── View 8x8 oval  id=status_dot   tint per state
  ├── Space 8
  └── TextView id=tv_status  @style/TextAppearance.App.Title  16sp/700
[ 24 ]
LinearLayout (horizontal)  id=numeric_strip   visible only when connected
     paddingHorizontal @dimen/screen_gutter, weightSum 3
  ├── column: ImageView 16dp ic_speed_down + Space 8 + TextView Numeric 16sp/500  "24,8 Мбит/с"
  ├── column: ImageView 16dp ic_clock      + Space 8 + TextView Numeric 16sp/500  "02:14:07"
  └── column: ImageView 16dp ic_speed_up   + Space 8 + TextView Numeric 16sp/500  "3,1 Мбит/с"
     each column: weight 1, gravity center, glyph tint ?attr/colorOnSurfaceVariant,
     value tint ?attr/colorOnSurface, reserved width from the 620/1000 advance so digits never shift
[ 32 ]
TextView  "Сервер"    @style/SettingsSectionLabel
[ (the header's own 8dp bottom padding) ]
Row  id=row_server    8.1 Row.Navigation
     tile: the unified server icon (6.3)
     title: "Нидерланды, Амстердам"          (remark, flag stripped, maxLines 1)
     subtitle: "VLESS · Reality"              (chip-free plain text on this surface)
     value: "48 мс"  Numeric
     trailing: chevron
[ 24 ]
TextView  "Подписка"  @style/SettingsSectionLabel
Card  id=card_subscription    8.3, the ONE card on this screen        <- 11.6
[ 32 ]
```

**Nothing else is on this screen.** No memory card, no account chip, no welcome heading, no stats
row above the fold, no server list (Incy duplicates its list onto Home and it is an IA failure;
`30-reference-analysis.md` 2.2.8 refuses it), and no second card.

### 11.4 States

| State | Disc | Status line | Numeric strip | Below |
|---|---|---|---|---|
| **Отключено** | P3 fill, 1dp outline, outline shield in `?attr/colorOnSurfaceVariant`. **Nothing blue** | grey dot + «Отключено» | absent | server row + subscription card |
| **Подключение** | Arc visible, indeterminate, `?attr/colorPrimary`, running **only** while the core negotiates | grey dot + «Подключение…» | absent | unchanged, actions that need the tunnel disabled |
| **Подключено** | Filled shield `?attr/colorPrimary`; arc gone | green dot `?attr/colorTertiary` + «Подключено» | present, entered once (moment 2) | unchanged |
| **Отключение** | Filled shield crossfades back to outline over 220 | grey dot + «Отключение…» | fades out 165 | unchanged |
| **Ошибка туннеля** | Disc returns to idle | red dot `?attr/colorError` + «Не подключено» | absent | status strip with the taxonomy message (11.8) and a recovery action |
| **Нет сервера** | Disc at alpha 0.38, not clickable | grey dot + «Сервер не выбран» | absent | the server row is replaced by a Row whose title is «Выбрать сервер» and whose trailing is a chevron |
| **First run, signed out** | Disc at alpha 0.38, not clickable | grey dot + «Не подключено» | absent | EmptyState (8.8): «Начните с входа» / «Войдите, чтобы получить серверы Departament и управлять подпиской.» / filled «Войти» + text «Добавить провайдера» |
| **First run, signed in, no subscription** | same | same | absent | EmptyState: «Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» / filled «Купить» + text «Добавить провайдера» |
| **Подписка истекла** | Disc at alpha 0.38, not clickable | grey dot + «Подписка истекла» | absent | subscription card in its `истекла` state with «Продлить» |
| **Лимит устройств** | Disc clickable; a connect attempt fails with the taxonomy message | - | - | status strip: «Достигнут лимит устройств. Отвяжите одно из устройств в разделе «Устройства».» + «Устройства» |
| **Загрузка** (cold start with a session) | Disc live and usable immediately | live | live | server row and subscription card render as skeletons (8.9) after 300ms |
| **Оффлайн** | Disc live (the tunnel may still work) | live | live | subscription card shows its last known data plus a caption «Данные могли устареть»; a persistent status strip says «Нет сети. Показаны последние данные.» with «Повторить»; «Продлить» and «Купить» are disabled |
| **Частично** | - | - | - | Servers loaded, subscription failed: the card renders its error state; the rest of the screen is normal |
| **Long content** | - | - | - | A 70-character remark ellipsises at the end on one line; the subscription name wraps to 2 lines |

### 11.5 Copy

| Resource | Value |
|---|---|
| `home_status_disconnected` | `Отключено` |
| `home_status_connecting` | `Подключение…` |
| `home_status_connected` | `Подключено` |
| `home_status_disconnecting` | `Отключение…` |
| `home_status_failed` | `Не подключено` |
| `home_status_no_server` | `Сервер не выбран` |
| `home_status_expired` | `Подписка истекла` |
| `home_section_server` | `Сервер` |
| `home_section_subscription` | `Подписка` |
| `home_pick_server` | `Выбрать сервер` |
| `home_first_run_signed_out_title` | `Начните с входа` |
| `home_first_run_signed_out_line` | `Войдите, чтобы получить серверы Departament и управлять подпиской.` |
| `home_first_run_signed_out_cta` | `Войти` |
| `home_first_run_no_sub_title` | `Подписки пока нет` |
| `home_first_run_no_sub_line` | `Купите тариф, чтобы подключаться к серверам Departament.` |
| `home_first_run_no_sub_cta` | `Купить` |
| `home_add_provider` | `Добавить провайдера` |
| `home_add_server_cd` | `Добавить сервер` |
| `home_connect_cd_idle` | `Подключиться` |
| `home_connect_cd_active` | `Отключиться` |
| `home_stale_data` | `Данные могли устареть` |

Deleted: `home_welcome_title` («Приветствуем!» - an exclamation mark, banned), `home_empty_title`
(«У вас пока не добавлены подписки.» - a trailing period on a title, passive voice),
`home_empty_subtitle` («…чтобы начать пользоваться» - a dangling verb), `home_or_sign_in`,
`home_not_connected`, `home_select_server`, and every `toast_status_*` string.

### 11.6 The subscription card (signature moment 3 lives here)

One card, on Home and on Аккаунт, rendered from one `SubscriptionState` resolved in one place.
This is `30-reference-analysis.md` 6.1 made concrete.

```
Card 8.3  (P1, radius 20, 1dp hairline, padding 16)
├── row 1   TextView title  @style/TextAppearance.App.Title, weight 1, maxLines 2
│           Chip 8.4, state variant, marginStart 8dp
├── row 2   TextView caption @style/TextAppearance.App.Caption, marginTop 4dp
│           "Обновлено 5 минут назад"   (or the expiry sentence, see the state table)
├── [ 16 ]
├── row 3   Meter 8.11  "Трафик" / "12,4 из 50 ГБ"     (omitted entirely when root-only data
│           is unavailable or when the operator sent subscription-userinfo: 0)
├── [ 12 ]
├── row 4   Meter 8.11  "Устройства" / "3 / 5"          (bar omitted when unlimited)
├── [ 16 ]  1dp ?attr/colorOutlineVariant, full width inside the card
├── row 5   operator message, dismissible, max 200 chars, max 5 lines  (24.4)
└── row 6   action zone: at most ONE filled accent button (52dp, full width)
            plus at most one text button beside it
```

| `SubscriptionState` | Condition | Chip | Caption | Action |
|---|---|---|---|---|
| `нет подписки` | no managed subscription | none; the card is replaced by the EmptyState | - | `Купить` |
| `триал` | `Subscription.isTrial` **from the backend**, never inferred from tariff name or squad | neutral «Пробный период» | `Активен до 12.08.2026` | text `Купить тариф` |
| `активна` | expiry > 7 days and quota < 90% | success «Активна» | `Действует до 12.08.2026` | **none.** A CTA that is always present is furniture |
| `истекает` | expiry <= 3 days **or** quota >= 90% | warning «Истекает» | `Осталось 3 дня` | filled `Продлить` |
| `истекла` | expiry in the past | error «Истекла» | `Истекла 09.07.2026` | filled `Продлить` |
| `лимит устройств` | devices used == allowed | warning «Лимит устройств» | `Отвяжите устройство, чтобы подключить это` | filled `Устройства` |

Rules that make it one object rather than four renderings:
- The chip always carries **a word, a colour and a glyph**. Never a bare dot, never colour alone.
- `isTrial` comes from the backend. In this deployment the trial squad **is** the paid base squad,
  so any squad-based or tariff-name-based detection misclassifies real paying customers.
- Expiry renders as `до 12.08.2026` when far and `осталось 3 дня` when near; the crossover is 7 days.
- A perpetual sentinel (year >= 2099, or more than 10 years out) renders «бессрочно», never
  «Действует до 04.06.2099». Desktop already has `IsEffectivelyPerpetual`; Android does not and
  would print the sentinel.
- Traffic is root-only in the API. A secondary subscription renders **no traffic meter** rather than
  an empty one.
- Tapping the card opens Аккаунт. Long-press does nothing. **The current long-press-to-delete
  (`HomeMetaPagerAdapter.kt:66`) is removed**: undiscoverable and destructive is the exact inverse
  of the destructive-action law.

### 11.7 Interaction

- **Tap the disc** connects, or disconnects if connected. Feedback inside 90ms via `press_scale`;
  the state word changes within 100ms of the service acknowledging; `pressHaptic()` on both.
- The disc is **not** clickable when there is no server or no valid subscription; it renders at
  alpha 0.38 and the reason is stated in the status line and in the block below.
- **VPN permission**: if `VpnService.prepare()` returns an intent, the system dialog is shown; a
  denial produces the status strip «Нужно разрешение на VPN-подключение.» with «Разрешить».
- **One silent retry.** A transient drop re-attempts once, with the arc visible, before any error
  surface appears. Only the second failure produces a status strip. Never punish the user with an
  error the app could have absorbed.
- **The `+` toolbar action** opens the add-source sheet (section 14).
- **The server row** switches to the Серверы tab.
- Pull-to-refresh is **not** on this screen. The subscription refreshes on resume and on a
  subscription-update event.

### 11.8 Error taxonomy shown here

One sealed type, one message per case, every case carrying a recovery action. No raw core strings,
no exit codes, ever.

| Case | Copy | Primary | Secondary |
|---|---|---|---|
| `NoNetwork` | `Нет подключения к интернету. Проверьте сеть и повторите.` | `Повторить` | - |
| `AllUnreachable` | `Не удалось подключиться ни к одному серверу. Так бывает в ограниченных сетях.` | `Другой сервер` | `Обход блокировок` |
| `HandshakeTimeout` | `Сервер не отвечает. Выберите другой сервер или повторите позже.` | `Повторить` | `Другой сервер` |
| `SubExpired` | `Подписка истекла. Продлите её, чтобы подключаться.` | `Продлить` | - |
| `DeviceLimit` | `Достигнут лимит устройств. Отвяжите одно из устройств в разделе «Устройства».` | `Устройства` | - |
| `PermissionDenied` | `Нужно разрешение на VPN-подключение.` | `Разрешить` | - |
| `SubUpdateFailed` | `Не удалось обновить подписку. Проверьте ссылку провайдера и повторите.` | `Повторить` | - |
| `CoreCrash` | `Что-то пошло не так. Повторите попытку.` | `Повторить` | `Отправить отчёт` |

### 11.9 Transitions

- **In from cold start:** no entrance animation. The screen is simply there. Skeletons appear after
  300ms if data is not ready.
- **In from a tab switch:** 220ms crossfade, scroll position restored.
- **In from sign-in:** the 450ms `ease_out_expo` hand-off (10.7). The only time Главная animates in.
- **Out to a sub-page:** 300ms `ease_out_quint`, translationX 16dp.
- **Back:** on Главная, Back finishes the activity (predictive Back honoured).


---

## 12. Серверы - the list, its grouping, and its search

### 12.1 Purpose

Find and select one endpoint among up to a few hundred, grouped under the provider that produced it,
with its latency legible and its per-item actions one deliberate gesture away.

### 12.2 Files today, and the verdict

| File | Verdict |
|---|---|
| `res/layout/layout_servers_header.xml` (108 lines) | **REBUILD** into the standard toolbar plus a search block |
| `res/layout/item_recycler_main.xml` (130 lines) | **RESTYLE** heavily: flag tile, neutral protocol chip, ping column, state marker, no emoji, delete the zero-size `layout_indicator` |
| `res/layout/item_section_header.xml` | **RESTYLE**, becomes sticky |
| `res/layout/layout_servers_empty.xml` (67 lines, 9 off-scale values) | **REBUILD** as `EmptyState` 8.8 |
| `ui/MainRecyclerAdapter.kt` | **Fix the P0 regression**: `bindServer()` sets only `setOnClickListener`; `onItemLongClick` is declared and never invoked, so `ServerActionsSheet` never opens and four editor activities are unreachable |
| `res/drawable/bg_search_pill.xml` (radius 14) | Delete, replaced by `bg_input_field.xml` (radius 12) |
| `res/drawable/bg_server_row.xml` (raw `#1F4C8DFF`) | **RESTYLE** to theme attrs |

**The P0.** `MainActivity.kt:610-611` assigns `serversAdapter.onItemLongClick` and
`homeAdapter.onItemLongClick`; `MainRecyclerAdapter.kt:56` declares the property with a comment
saying it is "no longer invoked by the adapter". Consequently **a user cannot delete, rename, share,
duplicate, QR or edit a single server from the UI**, `editServer()`, `shareServer()`,
`showQRCode()`, `share2Clipboard()`, `removeServer()` and `locateSelectedServer()` have no callers,
and `ServerActivity`, `ServerCustomConfigActivity`, `ServerGroupActivity` and
`ServerProxyChainActivity` are unreachable. This is a functional regression, not a design gap, and
it is the first thing fixed in wave 3.

### 12.3 Component tree

```
Toolbar 56dp (8.6)
  ├── TextView "Серверы"  @style/TextAppearance.App.Title, marginStart @dimen/screen_gutter
  └── ImageButton 48dp  ic_more_vert_24dp   cd "Ещё"
[ 8 ]
SearchField 8.13   id=et_search, marginHorizontal @dimen/screen_gutter, 48dp
     hint "Поиск по названию или стране"
[ 12 ]
LinearLayout (horizontal, gravity center_vertical, marginHorizontal 16dp, height 40dp)
  ├── TextView id=tv_count  @style/TextAppearance.App.Subtitle + Numeric, weight 1
  │            "15 серверов · 2 провайдера"
  └── MaterialButton text  id=btn_sort  48dp height, paddingH 8dp
               label "По задержке", trailing 20dp ic_unfold_more      <- cycles in place
[ 8 ]
RecyclerView  id=rv_servers   clipToPadding=false
     paddingBottom = navigationBar + 56 + 16
     ItemDecoration: 1dp ?attr/colorOutlineVariant hairline at marginStart 68dp between rows
                     of the same group, none before a header and none after the last row
     StickyHeaderDecoration for item_section_header
```

**One trailing toolbar action.** The four 36dp buttons crammed into the current header (collapse,
refresh, ping, add) all move into the overflow, where they are named rather than guessed at.

Overflow (`res/menu/menu_servers.xml`, new, 5 items maximum):

| Item | Action |
|---|---|
| `Добавить провайдера` | Opens the add-source sheet (14) |
| `Обновить подписки` | Refetches every provider; per-provider progress renders in each section header |
| `Проверить задержку` | Bulk ping; each row's value slot shows a 16dp indeterminate indicator until its result lands |
| `Свернуть все группы` / `Развернуть все группы` | Toggles, label reflects the next action |
| `Удалить недоступные` | Removes servers that failed the last check; **undo strip**, no dialog |

### 12.4 The server row

```
Row 8.1, variant Row.Selectable, minHeight 56dp
├── tile 40dp: the unified server icon (6.3) - 28dp flag raster, or 22dp ic_globe_24dp
├── 12
├── text column, weight 1
│   ├── title     "Нидерланды, Амстердам"   App.Title, maxLines 1, ellipsize end
│   │             (the leading flag emoji is stripped by the same transform that fills the tile)
│   └── subtitle  Chip 8.4 neutral "VLESS"  +  8  +  TextView "Reality · TCP"  App.Subtitle
│                 marginTop 4dp
├── 12
├── value  id=tv_ping   App.Subtitle + Numeric, right-aligned, minWidth 64dp
│          "48 мс"  in ?attr/colorOnSurfaceVariant
│          untested: "" (blank, not a dash, not "n/a")
│          testing:  16dp indeterminate indicator
│          unreachable: "нет ответа" in @color/ping_bad
└── state marker  id=iv_selected  20dp ic_action_done, ?attr/colorPrimary, alpha 0 when unselected
```

**Ping is neutral text, not a green or red dot.** Green in this product means «подключено» or
«оплачено»; using it for a fast ping would give the colour a third meaning. Only unreachable is
coloured, and it carries the **word** «нет ответа» so colour is never the only signal. This is a
deliberate divergence from Incy's dot-plus-value-plus-word and from our own current
`pingGood`/`pingBad` pair.

**Selected state, two axes:** row background `?attr/colorSurfaceContainerHighest` (P3) **and** the
20dp accent check in the state-marker slot. No side stripe (absolute ban), no 1.5dp accent stroke,
no fill tint. The zero-size `layout_indicator` `View` that survives only so
`MainRecyclerAdapter.kt:208` can call `setBackgroundColor` on it is deleted.

**Protocol chip contrast fix:** the chip moves from `chip_type_text #4C8DFF` on
`colorPrimaryContainer #17325C` (**4.0:1**, a failure at 11sp) to the Neutral chip variant,
`?attr/colorOnSurfaceVariant` on `?attr/colorSurfaceContainerHighest` (**6.0:1**). The gold `JSON`
chip becomes the same neutral chip; the word already carries the meaning.

### 12.5 The section header

```
LinearLayout, height 48dp, paddingHorizontal 16dp, background ?attr/colorBackground (sticky)
├── ImageView 20dp ic_chevron_down, rotation 0 collapsed / 90 expanded, 220ms ease_standard
├── Space 8
├── TextView  provider name   @style/SettingsSectionLabel (padding overridden to 0), weight 1
├── TextView  count  App.Subtitle + Numeric   "24"
└── [ optional 16dp indeterminate indicator while that provider is updating ]
```

Sticky, because the list is long enough to lose context (`00-rules.md` 4.6). Tapping the header
collapses the group; the state survives Back, tab switches and rotation. A provider whose last
update failed shows, in place of the count, «не обновился» in `@color/ping_bad` plus a 20dp
`ic_refresh_24dp` action - the fix is where the failure is.

**We refuse Happ's per-subscription tab strip** (`30` 1.2.1): a horizontal strip is the weakest
scanning affordance on a phone, it hides the provider count, and it makes one control mean both
"filter" and "where am I".

### 12.6 States

| State | Rendering |
|---|---|
| **First load** | Six skeleton rows (40dp tile square + an 18dp bar at 68dp + a 14dp bar) after 300ms; crossfade to content over 220ms; then one 40ms-staggered entrance capped at 400ms |
| **Loaded** | The tree above. The selected row is P3-filled and carries the check |
| **Empty, no providers** | EmptyState 8.8: glyph `ic_subscriptions_24dp`; «Серверов пока нет»; «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.»; filled «Добавить провайдера» |
| **Empty, provider returned nothing** | EmptyState inside the group: «Провайдер не вернул серверы»; «Проверьте ссылку в разделе «Провайдеры».»; tonal «Обновить» |
| **Search found nothing** | EmptyState in the list area: «Ничего не найдено»; «Попробуйте другой запрос.»; tonal «Сбросить поиск». The search field keeps its text and its focus |
| **Error** | The list keeps whatever it has; a status strip says «Не удалось обновить подписку. Проверьте ссылку провайдера и повторите.» with «Повторить» |
| **Offline** | List renders from cache; each section header carries «Данные могли устареть»; «Обновить подписки» and «Проверить задержку» are disabled at 0.38; one persistent status strip |
| **Partial** | Provider A loaded, provider B failed: A renders normally, B's header carries «не обновился» plus its retry |
| **Testing latency** | Each row's value slot shows a 16dp indeterminate indicator; results land per row with no animation and no re-sort unless "auto-sort after test" is on, in which case the list re-sorts **once**, after the last result, with a 220ms crossfade |
| **Long content** | A 70-character remark ellipsises at the end; a 40-character provider name wraps the header to 2 lines and the header grows to 64dp |
| **Short content** | One server, one provider: the section header still renders. A single row must not look broken |
| **Font scale 200%** | Rows grow; the ping column keeps its reserved width; nothing clips |

### 12.7 Copy

| Resource | Value |
|---|---|
| `servers_title` | `Серверы` |
| `servers_search_hint` | `Поиск по названию или стране` |
| `servers_count` | `%1$d серверов · %2$d провайдера` (with correct plural resources for both numbers) |
| `servers_sort_ping` | `По задержке` |
| `servers_sort_name` | `По названию` |
| `servers_sort_none` | `Как у провайдера` |
| `servers_menu_add` | `Добавить провайдера` |
| `servers_menu_update` | `Обновить подписки` |
| `servers_menu_ping` | `Проверить задержку` |
| `servers_menu_collapse` | `Свернуть все группы` |
| `servers_menu_expand` | `Развернуть все группы` |
| `servers_menu_prune` | `Удалить недоступные` |
| `servers_ping_none` | `нет ответа` |
| `servers_group_stale` | `не обновился` |
| `servers_empty_title` | `Серверов пока нет` |
| `servers_empty_line` | `Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.` |
| `servers_empty_cta` | `Добавить провайдера` |
| `servers_search_empty_title` | `Ничего не найдено` |
| `servers_search_empty_line` | `Попробуйте другой запрос.` |
| `servers_search_empty_cta` | `Сбросить поиск` |
| `servers_removed_undo` | `Сервер удалён` / action `Отменить` |

`@string/title_servers` («Сервера») and `@string/bottom_nav_servers` («Servers») are both replaced
by `servers_title` / «Серверы».

### 12.8 Interaction

- **Tap a row** selects it. Selection is instant and local; it does not navigate, does not connect,
  and does not scroll. If the tunnel is up, selecting a different server reconnects to it with the
  arc visible and the status word «Переключение…».
- **Long-press a row** opens the actions sheet (12.9). **This is the regression being repaired.**
  The affordance is also discoverable: the first time the Серверы tab is opened after the update, a
  one-time status strip says «Долгое нажатие на сервер открывает действия» with «Понятно».
- **Search** filters in place across remark, country, protocol and address. It never navigates.
- **Sort** cycles `По задержке -> По названию -> Как у провайдера` in place, `ic_unfold_more`,
  no dialog. The choice persists.
- **Swipe** does nothing. There is no swipe-to-delete; deleting is an explicit action in the sheet
  followed by an undo strip.

### 12.9 `ServerActionsSheet`

**Files:** `res/layout/sheet_server_actions.xml` (271 lines, correctly tokenised),
`ui/ServerActionsSheet.kt`. **Verdict: RESTYLE and REWIRE.** It is well built and completely dead.

```
Sheet 8.14
├── handle 32x4
├── header 56dp:  unified server icon 40dp  +  12  +  [ title App.Title / subtitle App.Subtitle ]
│                 title = the remark with the flag stripped; subtitle = "VLESS · Reality · 48 мс"
├── 1dp hairline at 68dp
├── Row  ic_qu_scan_24dp     "Поделиться (QR)"        chevron-free, closes the sheet
├── Row  ic_dl_copy          "Скопировать ссылку"
├── Row  ic_edit_24dp        "Изменить"                chevron
├── Row  ic_copy             "Дублировать"
├── Row  ic_action_done      "Сделать основным"        hidden when it already is
└── Row  ic_delete_24dp      "Удалить"                 RED tile, RED title (Row.Destructive)
```

- Every tile is **neutral** except the last, which is the one destructive tile. Today every tile in
  this sheet is `bg_icon_blue`, which makes the sheet a blue wall.
- «Удалить» removes the server **immediately** and shows the undo strip for 5 seconds. No
  confirmation dialog: a server is re-importable from its provider, so this is not irreversible.
  (`PREF_CONFIRM_REMOVE`, currently an unreachable preference, becomes a real toggle under
  Настройки › Провайдеры for users who want the dialog back.)
- The sheet is also reachable from the Home server row's long-press, with the same content.

---

## 13. QR display sheet

### 13.1 Purpose

Show one server or one subscription as a scannable code, and let it leave the device.

### 13.2 Files today, and the verdict

`res/layout/item_qrcode.xml` - a 336dp `fitXY` `ImageView` with no title, no framing and no share
action, shown inside a bare `AlertDialog`. **REBUILD as a sheet.**

### 13.3 Component tree

```
Sheet 8.14
├── handle 32x4
├── TextView  title  App.Title, gutter 16, marginTop 12dp    "Сервер: Нидерланды, Амстердам"
├── TextView  caption App.Caption, marginTop 4dp             "Отсканируйте код в другом устройстве"
├── [ 24 ]
├── FrameLayout 240x240, centred, background #FFFFFF (a QR must be black on white in every theme),
│      radius @dimen/radius_card 20, padding 16dp
│      └── ImageView 208dp, the code, scaleType fitCenter, filter false (never blur a QR)
├── [ 24 ]
├── Row  ic_share_24dp   "Поделиться"          -> system share sheet
├── Row  ic_dl_copy      "Скопировать ссылку"  -> clipboard + status strip «Ссылка скопирована»
└── [ navigationBar inset + 16 ]
```

### 13.4 States

Rendering (a 240dp skeleton square for the ~80ms of encoding); rendered; failed («Не удалось
построить код.» plus «Повторить»); a subscription URL hidden by an operator `hide-url` directive -
**the code is still shown to its owner**, with a caption «Ссылку скрыл провайдер. Она не попадёт в
резервную копию.» (we honour "do not put this in a backup"; we never remove the user's ability to
read data he owns).

---

## 14. Adding a source - the `+` sheet, the scanner, and manual entry

### 14.1 Purpose

Get a provider link or a single config into the app from any of the four places it can come from,
with one confirmation surface and no silent mutation.

### 14.2 Files today, and the verdict

| File | Verdict |
|---|---|
| `res/menu/menu_main.xml` (the `+` menu) | **REBUILD** as a sheet |
| `ui/ScannerActivity.kt` + `res/layout/activity_none.xml` (an empty `RelativeLayout`) + `res/menu/menu_scanner.xml` (two items with `android:title=""`) | **REBUILD**: the app's own scan screen currently has zero branding, zero instruction and zero framing |
| `MainActivity.showManualEntryDialog()` with two hardcoded Russian error literals at `:2016` and `:2018` | **RESTYLE**; strings move to resources |
| `ui/UrlSchemeActivity.kt` (`depv://import/{base64}` mutates the server list with no confirmation) | **REBUILD** the confirmation surface (24.5) |

### 14.3 The add-source sheet

```
Sheet 8.14
├── handle
├── TextView "Добавить"  App.Title, gutter, marginTop 12dp
├── 1dp hairline at 68dp
├── Row  ic_qu_scan_24dp      "Отсканировать QR-код"      "Код провайдера или сервера"
├── Row  ic_dl_copy           "Вставить из буфера"        "Ссылка уже скопирована"  (row disabled
│                                                          at 0.38 when the clipboard has no link)
├── Row  ic_edit_24dp         "Ввести ссылку вручную"
└── Row  ic_file_24dp         "Из файла"                  opens the system picker
```

### 14.4 The scanner

Full-screen `ScannerActivity`, camera preview edge to edge.

```
FrameLayout, background #000000
├── camera preview, match_parent
├── View  scrim  match_parent, ?attr/colorScrim at 60%, with a 240x240 centred hole
├── FrameLayout 240x240, centred: four 24dp bracket corners, 2dp stroke, ?attr/colorPrimary,
│      radius 12  <- the only accent on this screen
├── TextView  "Наведите камеру на QR-код"  App.Body, centred, 32dp below the frame
├── Toolbar 56dp, transparent over the preview: 48dp back + title "Сканирование" +
│      48dp ic_image_24dp  cd "Выбрать изображение"
└── MaterialButton tonal  "Фонарик"  48dp, centred, 32dp above the navigation inset,
       leading 20dp glyph, toggles
```

States: requesting permission (the system dialog; a denial renders an EmptyState with «Нужен доступ
к камере» / «Разрешите доступ, чтобы сканировать QR-коды.» / filled «Открыть настройки»); scanning;
recognised (the frame's brackets snap to `?attr/colorTertiary` for 220ms, `pressHaptic()`, then the
confirm sheet); unrecognised (status strip «Это не похоже на ссылку провайдера или сервера.» with
«Попробовать снова»); no camera hardware (the picture-picker path only).

### 14.5 The import confirmation sheet

Shown for **every** import path, including `depv://` and `v2rayng://` deep links, which today mutate
the server list with no confirmation at all.

```
Sheet 8.14
├── handle
├── header 56dp: 40dp neutral tile ic_subscriptions_24dp + "Добавить провайдера"
├── 1dp hairline
├── Row.Fact  "Название"     value "Departament"
├── Row.Fact  "Адрес"        value "sub.departament.site/…"   ellipsize middle
├── Row.Fact  "Серверов"     value "24"   Numeric
├── [ 24 ]
└── MaterialButton filled 52dp  "Добавить"      +  text button "Отмена"
```

Copy: `Добавить провайдера` / `Добавить сервер` / `Добавить 24 сервера`, matching what is actually
being imported. Result: status strip «Провайдер добавлен» + «Открыть» (switches to Серверы), or the
error taxonomy.


---

## 15. Аккаунт

### 15.1 Purpose

One place that answers "what do I have, until when, on how many devices, and what does it cost to
keep it", and gives every one of those answers an action.

### 15.2 Files today, and the verdict

| File | Verdict |
|---|---|
| `res/layout/activity_account.xml` (560 lines) | **REBUILD** as `fragment_account.xml`. The grammar is right, the content is a read-only dashboard |
| `ui/AccountFragment.kt` | Keep the four-state hero machine (it is the best state machine in the app), the cache-first loads, the payment poll; add the actions listed below |
| `res/layout/item_subscription_card.xml` (75 lines, fixed 152dp) | **REBUILD** into the subscription card of 11.6 |
| `ui/SubscriptionPagerAdapter.kt` | Keep, with the fixed height removed |
| `res/layout/dialog_top_up.xml` | **REBUILD** as a sheet (15.8) |
| `res/values/strings_account.xml` | Rewrite; 14 strings are dead and 4 carry em-dashes |

**Why rebuild rather than restyle.** Graded **B-**, the best Android screen, and still a read-only
summary of a control panel. `AccountViewModel` already exposes `upgrade()`, `addDevices()`,
`toggleAutoRenew()`, `togglePrimaryAutoRenew()`, `renameSubscription()`, `activateTrial()`,
`checkPromo()`, and `AccountRepository` exposes `getQr()` and `getReferralStats()` - **every one of
them is dead code on Android** and the desktop calls most of them. There is no renew, no auto-renew,
no add-devices, no upgrade, no rename, no sign-out and no «Привязать Telegram» (owner request
0.4.9). The subscription card has no state: an expired subscription renders identically to an
active one. Two filled accent buttons are on screen at once in the empty state. The error card
hard-wires «Что-то пошло не так» in XML so the five real causes `messageFor()` maps never reach it.
Raw HTTP codes are shown to end users in an «Ошибка оплаты» dialog.

**Endpoint gap that gates one row.** `RequestLinkTelegram()` exists on desktop's
`IDepartamentApiClient` and is **absent** from Android's `DepartamentApiClient`. Until it is ported,
«Привязать Telegram» routes to `https://departament.site/account` in a Custom Tab, and the row's
subtitle says so. It does not ship as a dead button.

### 15.3 Component tree

Root: `NestedScrollView` on `?attr/colorBackground`, gutter 16, bottom padding
`navigationBar + 56 + 16`.

```
Toolbar 56dp: title "Аккаунт" + 48dp overflow (ic_more_vert_24dp)
[ 24 ]
--- identity block, NOT a card ---
LinearLayout (horizontal, gravity center_vertical), paddingHorizontal 16dp
├── FrameLayout 48x48
│    ├── ImageView 48dp circle (bg_avatar_circle, ?attr/colorSurfaceContainerHighest)
│    │     or the photo, centerCrop, circle-clipped
│    ├── TextView monogram  @style/TextAppearance.App.Title, gravity center, onSurfaceVariant
│    └── ImageView 16dp ic_acc_camera badge, bottom|end, ?attr/colorPrimary circle,
│          2dp ?attr/colorBackground stroke
├── Space 12
└── LinearLayout (vertical, weight 1)
     ├── TextView  name        App.Title, maxLines 1, ellipsize end     "@ivan_petrov"
     └── TextView  identity    App.Subtitle, marginTop 4dp              "ivan@example.com"
[ 24 ]
TextView "Баланс"   @style/TextAppearance.App.Caption, paddingHorizontal 16dp
[ 4 ]
LinearLayout (horizontal, gravity bottom, paddingHorizontal 16dp)
├── TextView id=tv_balance  @style/TextAppearance.App.Display + Numeric, weight 1   "1 480 ₽"
└── MaterialButton tonal  "Пополнить"  48dp, radius_pill, paddingH 16dp
[ 32 ]
TextView "Подписка"   @style/SettingsSectionLabel
Card  11.6  (the ONE card on this screen; a ViewPager2 of cards when there is more than one
             subscription, with 8dp dots below and a 12dp MarginPageTransformer)
[ 24 ]
TextView "Управление"  @style/SettingsSectionLabel
[ rows, hairlines at 68dp, no card around them ]
├── Row  ACCENT tile ic_acc_upgrade   "Купить подписку"   chevron       <- the one accent tile
├── Row  neutral ic_acc_devices       "Устройства"        value "3 / 5" chevron
├── Row  neutral ic_acc_history       "История платежей"  value "12.06.2026" chevron
├── Row  neutral ic_link              "Привязать Telegram" value "не привязан" chevron
│                                     (hidden entirely when already linked; when linked, the
│                                      identity line above shows the handle)
└── Row  neutral ic_acc_gift          "Пригласить друга"  value "20 %" chevron
[ 24 ]
TextView "Реферальная программа"? -- NO. See 15.6: it is one row, not a section.
[ 32 ]
MaterialButton text  "Выйти"  48dp, full width, ?attr/colorError label
[ 32 ]
```

Overflow (3 items): `Обновить`, `Сменить фото`, `Скопировать реферальный код`.

**Accent count: one filled surface** («Продлить» or «Купить» inside the card, or the «Купить
подписку» row's accent tile - never both: when the card is in a state that carries «Продлить», the
management row's tile drops to neutral). Plus the tariff badge chip and the state chip. «Пополнить»
is **tonal**, not filled, which fixes the current two-filled-buttons defect.

### 15.4 States

| State | Rendering |
|---|---|
| **Signed out** | The whole screen is replaced by a gate: 64dp accent tile, «Войдите в аккаунт» Headline, «Здесь появятся подписка, устройства и платежи.» Body, filled «Войти» 52dp, text «Создать аккаунт». The destination does **not** disappear from the bottom navigation |
| **First load** | Identity block renders from the cached session instantly; balance, card and row values render as skeletons after 300ms |
| **Loaded** | The tree above |
| **No subscription** | The card is replaced by EmptyState 8.8 inside the «Подписка» section: «Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» / filled «Купить». The management «Купить подписку» row then drops to a neutral tile so only one accent surface exists |
| **Trial** | Card in its `триал` state; a text action «Купить тариф» |
| **Expiring / expired / device limit** | Card states per 11.6; the state chip is the screen's second accent-adjacent element and it is amber or red, never blue |
| **Multiple subscriptions** | `ViewPager2` of cards, 8dp/6dp dots, neighbour peek 16dp only when count > 1. The dots are the only page affordance; long-press does nothing |
| **Load error** | The card slot renders an error block: `ic_error` 28dp in a 56dp tile, «Не удалось загрузить подписку» Title, **the real cause** from `messageFor()` in Body (not a hard-wired string), tonal «Повторить». Identity and balance still render from cache |
| **Payment pending** | A status strip: «Платёж обрабатывается…» with no action, replaced automatically when the poll resolves. The current `tv_pending` chip block is deleted |
| **Offline** | Everything renders from `AccountCache`; a caption «Данные могли устареть» sits under the balance; «Пополнить», «Купить», «Продлить» and «Выйти» are disabled at 0.38; one persistent status strip |
| **Long content** | A 32-character Telegram handle ellipsises; a 12-digit balance (`1 284 371 ₽`) fits because Display reserves 5 tabular figures and wraps to a second line above that; a 40-character subscription name wraps to 2 lines |
| **Success** | A purchase, a top-up or a rename confirms with a 220ms state change plus the word in the status strip, then the screen moves on. No confetti, no checkmark animation |

### 15.5 Copy

| Resource | Value |
|---|---|
| `account_title` | `Аккаунт` |
| `account_balance` | `Баланс` |
| `account_top_up` | `Пополнить` |
| `account_section_subscription` | `Подписка` |
| `account_section_manage` | `Управление` |
| `account_row_buy` | `Купить подписку` |
| `account_row_devices` | `Устройства` |
| `account_row_history` | `История платежей` |
| `account_row_telegram` | `Привязать Telegram` |
| `account_row_telegram_value` | `не привязан` |
| `account_row_referral` | `Пригласить друга` |
| `account_logout` | `Выйти` |
| `account_logout_confirm_title` | `Выйти из аккаунта?` |
| `account_logout_confirm_body` | `Подписка и серверы останутся на устройстве. Войти можно будет снова.` |
| `account_logout_confirm_ok` | `Выйти` |
| `account_gate_title` | `Войдите в аккаунт` |
| `account_gate_line` | `Здесь появятся подписка, устройства и платежи.` |
| `account_gate_cta` | `Войти` |
| `account_empty_title` | `Подписки пока нет` |
| `account_empty_line` | `Купите тариф, чтобы подключаться к серверам Departament.` |
| `account_empty_cta` | `Купить` |
| `account_error_title` | `Не удалось загрузить подписку` |
| `account_retry` | `Повторить` |
| `account_sub_state_trial` | `Пробный период` |
| `account_sub_state_active` | `Активна` |
| `account_sub_state_expiring` | `Истекает` |
| `account_sub_state_expired` | `Истекла` |
| `account_sub_state_device_limit` | `Лимит устройств` |
| `account_sub_until` | `Действует до %1$s` |
| `account_sub_left_days` | `Осталось %1$s` |
| `account_sub_expired_on` | `Истекла %1$s` |
| `account_sub_perpetual` | `Бессрочно` |
| `account_traffic_label` | `Трафик` |
| `account_traffic_value` | `%1$s из %2$s` |
| `account_traffic_unlimited` | `%1$s · без ограничений` |
| `account_devices_label` | `Устройства` |
| `account_renew` | `Продлить` |
| `account_buy_tariff` | `Купить тариф` |
| `account_referral_copied` | `Реферальный код скопирован` |
| `account_avatar_updated` | `Фото обновлено` |
| `account_avatar_error` | `Не удалось загрузить фото. Попробуйте другое.` |

Deleted (currently unreferenced or superseded): `account_profile_title`, `account_sub_summary_title`,
`account_subs_empty`, `account_hub_devices_sub`, `account_hub_buy_sub`, `account_hub_history_sub`,
`account_trial_badge` («ПРОБНЫЙ», ALL-CAPS), `account_auto_renew`, `account_upgrade`,
`account_add_devices`, `account_promo_hint`, `account_trial`, `account_traffic`,
`account_payments_more`, `account_unlimited` («∞»), `account_no_subscription`,
`account_empty_title` in its «Оформите первую подписку» form (the terminology lock says «Купить»,
never «Оформить»), `account_price_option` («%1$d дн. — %2$s», an em-dash),
`account_payment_error_body` («HTTP %1$s\n%2$s», a raw status code shown to a user).

### 15.6 The referral row, not a referral section

One `Row.Navigation`: title «Пригласить друга», value = the current percent («20 %»), chevron into
a small sub-page carrying the code, a copy action, the share sheet, and the stats that
`getReferralStats()` already returns and neither client uses. Today the referral is a
`wrap_content` chip with no minimum height, so a short code produces a touch target well under
48dp.

### 15.7 Interaction

- **«Пополнить»** opens the top-up sheet (15.8), never a dialog.
- **«Продлить»** in the card calls the scoped renew endpoint and then polls. A Platega payment is
  **webhook-confirmed**: returning from the browser proves nothing, so the pending state and the
  6 x 8s poll stay exactly as they are. **Never claim success on return.**
- **«Выйти»** is the one destructive confirmation on this screen, because a session is not
  re-creatable by undo. Dialog per 8.14, red «Выйти» on the right.
- **Avatar** tap opens a sheet, not a list dialog: «Выбрать из галереи» / «Убрать фото» / «Отмена»
  is a sheet of two rows.
- **The subscription card's rename** is reached by long-pressing nothing; it is a row inside the
  card's own overflow (a 48dp `ic_more_vert_24dp` in the card's title row) containing
  «Переименовать», «Показать QR», «Автопродление» (a switch), and «Удалить подписку» (destructive,
  and the only place that action exists, with a confirmation dialog because it is irreversible).
- **No `Toast` anywhere.** Referral copied, avatar updated, top-up succeeded and every error use the
  status strip.
- **No raw HTTP codes.** The «Ошибка оплаты» dialog is replaced by the taxonomy message plus a
  «Написать в поддержку» action that attaches the code in the message body, not on screen.

### 15.8 Top-up sheet

Replaces `dialog_top_up.xml`, whose hint is its only label, which has no helper slot, whose invalid
state is a toast, and whose buttons are the system «OK» / «Отмена».

```
Sheet 8.14
├── handle
├── TextView "Пополнить баланс"  App.Title, gutter, marginTop 12dp
├── [ 16 ]
├── amount chips row: 4 x Chip 8.4 selected-variant   "500 ₽" "1 000 ₽" "2 000 ₽" "5 000 ₽"
├── [ 16 ]
├── InputField 8.12  label "Другая сумма", inputType=number, suffix "₽", helper reserved
├── [ 24 ]
└── MaterialButton filled 52dp  "Перейти к оплате"
```
Then the payment-method sheet (15.9). Validation on blur: «Минимальная сумма 100 ₽»,
«Максимальная сумма 100 000 ₽».

### 15.9 Payment-method sheet

**Files:** `res/layout/sheet_payment_method.xml`, `res/layout/item_payment_method.xml`,
`ui/PaymentMethodSheet.kt`. **Verdict: RESTYLE.** The grammar and the rotation-surviving callback
are correct.

Fixes: the balance row's **green** tile becomes neutral (green is a status colour, not a
differentiator); the divider above the first row (which puts a hairline directly under the title) is
removed; the trailing chevron is removed because tapping fires the payment immediately rather than
going further - a row that acts now carries **no** trailing affordance, per the grammar in 8.1;
SBP detection by string-matching `"sbp"` / `"СБП"` is replaced by the method id from
`/public/config`.

Copy: the balance row reads `С баланса · 1 480 ₽` (a middle dot, not an em-dash).

---

## 16. Купить - tariffs and checkout

### 16.1 Purpose

Choose a tariff and a period, see exactly what will be charged, and pay, without ever wondering what
was selected.

### 16.2 Files today, and the verdict

| File | Verdict |
|---|---|
| `res/layout/activity_buy_tariff.xml` (309 lines) | **RESTYLE**, structurally, into a list plus one card |
| `res/layout/item_buy_tariff.xml` | **REBUILD** as a selectable row, not a card |
| `res/layout/item_buy_option.xml` | **REBUILD** as a selectable row inside the checkout card |
| `ui/BuyTariffActivity.kt` | Keep `renderState()`, keep `currentTotal()` (the single source for both the displayed total and the charged amount - **this contract must survive**), keep the poll |

**Why.** Graded **B-**. The state machine is complete and correct and the money contract is sound.
The problems are all structural: **card-in-card** (bordered price rows inside a bordered tariff card
inside a scroll), **six radii on one screen** (20 card, 14 option, 20 selected option, 22 retry,
26 pay, 20 stepper), selection that **moves the layout** (card stroke 1dp to 2dp, option radius 14
to 20, option stroke 1dp to 1.5dp), 40dp steppers, a disabled alpha of 0.4 instead of 0.38, no
purchase summary (the checkout card never restates *which* tariff and *which* period is being
bought), and skeletons that do not match the real silhouette.

### 16.3 Component tree

```
Toolbar 56dp: back + "Купить подписку"
[ 24 ]
TextView "Выберите тариф"  @style/SettingsSectionLabel
[ rows, hairlines at 68dp ]
Row.Selectable  per tariff
   tile   neutral ic_acc_upgrade
   title  "Базовый"
   subtitle "3 устройства · без ограничений"
   value  "от 290 ₽"   App.Subtitle + Numeric
   state marker  20dp ic_action_done ?attr/colorPrimary when selected
   selected background ?attr/colorSurfaceContainerHighest
[ 24 ]  -- appears only after a tariff is chosen, entering over motion_reveal 300
TextView "Срок"  @style/SettingsSectionLabel
[ rows ]
Row.Selectable  per price option
   no tile (this group has a 16dp text origin; the whole group holds one origin)
   title  "3 месяца"
   value  "870 ₽"  Numeric
   subtitle  "290 ₽ в месяц"           <- the comparison the user actually wants
   state marker as above
[ 32 ]
Card 8.3  id=card_checkout   -- the ONE card on this screen
├── TextView "Базовый, 3 месяца"   App.Title            <- the purchase summary that is missing today
├── [ 12 ]
├── Row (inside the card, no tile, 16dp origin, 56dp)
│      title "Дополнительные устройства"  subtitle "50 ₽ за устройство"
│      trailing: Stepper 8.13, two 48dp buttons + a Numeric value
├── [ 16 ]  1dp ?attr/colorOutlineVariant
├── LinearLayout (horizontal): TextView "Итого" App.Body onSurfaceVariant, weight 1
│                              TextView App.Title + Numeric, onSurface   "1 020 ₽"
│      -- the total is NOT blue. It is a fact, not an action
└── [ 16 ]
MaterialButton filled 52dp  "Оплатить 1 020 ₽"     <- the one lit element, and it states the amount
[ 32 ]
```

**Selection never changes geometry.** A selected row gains a P3 background and a 20dp check. No
stroke width changes, no radius changes, no card grows.

### 16.4 States

| State | Rendering |
|---|---|
| **Loading** | Four skeleton rows shaped like tariff rows (40dp tile square, an 18dp title bar, a 14dp subtitle bar, a right-aligned 14dp price bar), after 300ms, static; crossfade 220ms to content |
| **Loaded, nothing selected** | Tariff rows only. No «Срок» group, no checkout card, no CTA |
| **Tariff selected** | «Срок» group reveals over 300ms `ease_out_quint` |
| **Option selected** | Checkout card and CTA reveal over 300ms |
| **Empty** | EmptyState: «Тарифы недоступны» / «Мы не смогли получить список тарифов. Попробуйте позже.» / tonal «Повторить» |
| **Error** | Same block with the real cause in the line and «Повторить» |
| **Offline** | Cached tariffs render with «Данные могли устареть»; the CTA is disabled at 0.38; persistent status strip |
| **In flight** | CTA label swaps for a 20dp indicator, size preserved, everything disabled |
| **Pending** | After returning from the browser: a status strip «Платёж обрабатывается…», the CTA disabled, a 5 x 8s poll. **Success is never claimed on return** |
| **Paid from balance** | Status strip «Подписка оплачена», then `finish()` back to Аккаунт with the card already updated |
| **Payment failed** | Status strip with the taxonomy message «Платёж не прошёл. Попробуйте другой способ оплаты.» and «Выбрать способ» |
| **Device limit reached for the stepper** | The `+` button disables at 0.38 and a caption appears under the row: «Больше устройств для этого тарифа недоступно» |
| **Long content** | A 40-character tariff name wraps to 2 lines; the price column keeps its reserved width |

### 16.5 Copy

| Resource | Value |
|---|---|
| `buy_title` | `Купить подписку` |
| `buy_section_tariff` | `Выберите тариф` |
| `buy_section_period` | `Срок` |
| `buy_tariff_info` | `%1$s · %2$s` (devices, traffic) |
| `buy_tariff_from` | `от %1$s` |
| `buy_per_month` | `%1$s в месяц` |
| `buy_extra_devices` | `Дополнительные устройства` |
| `buy_extra_devices_price` | `%1$s за устройство` |
| `buy_extra_devices_max` | `Больше устройств для этого тарифа недоступно` |
| `buy_total` | `Итого` |
| `buy_pay` | `Оплатить %1$s` |
| `buy_empty_title` | `Тарифы недоступны` |
| `buy_empty_line` | `Мы не смогли получить список тарифов. Попробуйте позже.` |
| `buy_retry` | `Повторить` |
| `buy_pending` | `Платёж обрабатывается…` |
| `buy_paid` | `Подписка оплачена` |
| `buy_failed` | `Платёж не прошёл. Попробуйте другой способ оплаты.` |
| `buy_pick_method` | `Выбрать способ` |
| `buy_estimate_note` | `Примерная сумма. Итог покажет платёжная страница.` |

`buy_loading`, `buy_pick_duration` and `buy_balance_label` are dead and are deleted.

### 16.6 Interaction

- The CTA is disabled until a tariff **and** an option are chosen. It never says «Оплатить» without
  the amount.
- The charged amount is `currentTotal(tariff, option)`; the displayed amount is the same call. They
  cannot drift.
- The device-count price shown before checkout is a client-side estimate; where it is an estimate it
  carries `buy_estimate_note` and never presents itself as final.
- Trial and promo code exist in the API (`trialEnabled`, `trialUsed`, `activateTrial()`,
  `checkPromo()`) and are currently offered nowhere. **Either they get a designed home or their
  strings are deleted; shipping the endpoints with no surface is a decision by omission.** The
  designed home: a `Row.Navigation` under the tariff list, «Промокод», value = the applied code or
  empty, opening a sheet with one field and one «Применить»; and, when `trialEnabled && !trialUsed`,
  a first row in the tariff list titled «Пробный период» with the subtitle «7 дней бесплатно» and a
  «Активировать» action.

### 16.7 Transitions

In: sub-page enter 300ms from Аккаунт or from Главная's first-run CTA. Out on success: `finish()`
with the standard exit; Аккаунт re-renders its card from the fresh poll result rather than
re-fetching from scratch, so the user sees the new expiry immediately.

---

## 17. Устройства

### 17.1 Purpose

See what is attached to the subscription and detach anything that is not you.

### 17.2 Files today, and the verdict

`res/layout/activity_devices.xml` (85 lines), `res/layout/item_device.xml`,
`ui/DeviceManagementActivity.kt`, `ui/adapter/DeviceAdapter.kt`, `res/values/strings_devices.xml`.
**RESTYLE**, with one deletion that is not negotiable.

**The deletion.** When the parsed list is empty but the subscription claims devices, or when the
fetch fails, the app shows a dialog titled «Ответ сервера (диагностика)» containing the HTTP status
and the raw response body, with copy asking the end user to screenshot it and send it in. **This
ships to production users and it must not survive.** The diagnostic goes to the log page under
Настройки › О приложении › Журнал, and the user sees the error taxonomy.

Other fixes: N identical 20dp cards become one divided list (the uniform-card tell); the 44dp delete
button becomes 48dp; the single generic `ic_acc_devices` glyph for every platform becomes the
five-glyph set of 6.2 (desktop already resolves Android/Apple/Windows/Router); the raw HWID third
line is demoted; «Удалить устройство» becomes «Отвязать устройство» to match desktop and the
terminology lock.

### 17.3 Component tree

```
Toolbar 56dp: back + "Устройства" + 48dp overflow ("Обновить")
[ 16 ]
TextView  App.Subtitle, gutter 16   "3 из 5 устройств подключено к подписке «Базовый»"
[ 16 ]
RecyclerView, hairlines at 68dp
└── Row per device
      tile    neutral, platform glyph (ic_device_android / apple / windows / router / unknown)
      title   "Pixel 8"        App.Title, maxLines 1
      subtitle "Android · был в сети 09.07.2026"   App.Subtitle + Numeric for the date
      value   Chip 8.4 neutral "Это устройство"    (only on the current device)
      trailing 48dp ImageButton ic_delete_24dp, tint ?attr/colorError, cd "Отвязать устройство"
```

The delete button is the row's **one** trailing affordance and the row itself is not clickable, so
the affordance grammar holds: nothing else on the row promises anything.

### 17.4 States

| State | Rendering |
|---|---|
| **First load** | Cache-first: a fresh (<1h) `AccountCache` entry renders with no network call. Otherwise five skeleton rows after 300ms |
| **Loaded** | The list; the current device carries its chip and sorts first |
| **Empty** | EmptyState: `ic_device_unknown`; «Устройств пока нет»; «Устройства появятся после первого подключения.»; **no action** |
| **No subscription** | EmptyState: «Нет активной подписки»; «Купите тариф, чтобы подключать устройства.»; filled «Купить» |
| **Error** | EmptyState: «Не удалось загрузить устройства»; the real cause; tonal «Повторить» |
| **Offline** | Cached list plus «Данные могли устареть»; the delete buttons disabled at 0.38 |
| **At the limit** | The subtitle line reads «5 из 5 устройств. Отвяжите одно, чтобы подключить новое.» and carries a warning chip |
| **Deleting** | That row's delete button becomes a 20dp indicator; the row stays in place |
| **Deleted** | The row animates out over 220ms; the status strip offers «Отменить» for 5 seconds. **A device detach is reversible on the server within the poll window, so it is an undo, not a dialog** |
| **Long content** | A 40-character device model wraps to 2 lines; a 64-character HWID is not displayed at all (it moves into the row's long-press copy action) |

### 17.5 Copy

| Resource | Value |
|---|---|
| `devices_title` | `Устройства` |
| `devices_summary` | `%1$s из %2$s устройств подключено к подписке «%3$s»` |
| `devices_summary_limit` | `%1$s из %2$s устройств. Отвяжите одно, чтобы подключить новое.` |
| `devices_this_device` | `Это устройство` |
| `devices_last_seen` | `%1$s · был в сети %2$s` |
| `devices_unlink` | `Отвязать устройство` |
| `devices_unlinked` | `Устройство отвязано` |
| `devices_undo` | `Отменить` |
| `devices_empty_title` | `Устройств пока нет` |
| `devices_empty_line` | `Устройства появятся после первого подключения.` |
| `devices_no_sub_title` | `Нет активной подписки` |
| `devices_no_sub_line` | `Купите тариф, чтобы подключать устройства.` |
| `devices_error_title` | `Не удалось загрузить устройства` |

`devices_diag_empty`, `devices_diag_failed` and `devices_diag_title` are **deleted**.

---

## 18. История платежей

### 18.1 Purpose

A ledger of what was paid, when, and whether it went through.

### 18.2 Files today, and the verdict

`res/layout/activity_payment_history.xml`, `res/layout/item_payment.xml`,
`ui/PaymentHistoryActivity.kt`, `ui/adapter/PaymentsAdapter.kt`. **RESTYLE.**

Fixes: N identical rounded cards become a divided list (a payment is a fact, not an object you act
on); the centred indeterminate spinner over a blank screen becomes skeleton rows; empty and error
stop sharing one `TextView` with a `drawableTop` and become two designed states; `btn_history_buy`
is `visibility="gone"` and unwired and either ships live or is deleted (it ships live, in the empty
state); amounts and dates get tabular figures so the right column stops jittering; the date gains a
time when two records can share a day; and the four status hues collapse to three.

### 18.3 Component tree

```
Toolbar 56dp: back + "История платежей"
SwipeRefreshLayout (accent colour scheme)
└── RecyclerView, hairlines at 68dp
    ├── sticky section header per month:  "Июль 2026"  @style/SettingsSectionLabel
    └── Row per payment, not clickable
          tile     neutral ic_acc_history
          title    "Базовый, 3 месяца"        App.Title, maxLines 1
          subtitle "12.06.2026 19:41"          App.Subtitle + Numeric
          value    "1 020 ₽"                   App.Subtitle + Numeric, onSurface
          trailing Chip 8.4, status variant
```

Grouping by month is what makes a ledger readable and it costs one sticky header.

### 18.4 Status mapping, three hues not four

| Raw statuses | Label | Chip variant |
|---|---|---|
| paid, success, succeeded, completed, confirmed, done | `Оплачено` | Success (green) |
| pending, processing, new, created, waiting, in_progress | `В обработке` | Warning (amber) |
| failed, error, declined, rejected | `Ошибка` | Error (red) |
| canceled, cancelled, expired | `Отменён` | **Neutral** (it invented a fifth meaning as yellow) |
| anything else | the raw status, verbatim | Neutral |

### 18.5 States

First load (six skeleton rows after 300ms, cache-first via `AccountCache` with the existing
`showingCache` guard); loaded; empty (EmptyState: «Платежей пока нет» / «Здесь появится история
покупок и продлений.» / tonal «Купить подписку» - the CTA that is currently shipped as dead
markup); error (EmptyState with the real cause and «Повторить»); offline (cache plus the stale
caption, refresh disabled); refreshing (`SwipeRefreshLayout` only, no skeleton over existing
content); long content (a 60-character description wraps to 2 lines); short content (one payment
still renders its month header).

### 18.6 Copy

`history_title` `История платежей`; `history_empty_title` `Платежей пока нет`;
`history_empty_line` `Здесь появится история покупок и продлений.`; `history_empty_cta`
`Купить подписку`; `history_error_title` `Не удалось загрузить историю`; `history_status_paid`
`Оплачено`; `history_status_pending` `В обработке`; `history_status_failed` `Ошибка`;
`history_status_canceled` `Отменён`.


---

## 19. Настройки - the hub

### 19.1 Purpose

Fifteen decisions, each showing its current value, each declaring what tapping it will do, arranged
so a user can audit his whole configuration by scrolling once.

### 19.2 Files today, and the verdict

| File | Verdict |
|---|---|
| `res/layout/layout_settings_content.xml` (**1 536 lines for 20 rows, zero reuse**) | **DELETE.** Replaced by `fragment_settings.xml`, a `RecyclerView` over a data-driven row model |
| `res/layout/layout_setting_row.xml`, `layout_setting_toggle_row.xml` | Superseded by `view_row.xml` (8.1). They are correct components that no layout ever included |
| `ui/SettingsActivity.kt` + `res/xml/pref_settings.xml` (354 lines, ~48 preferences) + `res/layout/activity_settings.xml` + `preference_with_help_link.xml` | **DELETE**, after every preference in it has a home in 19.5 |
| `ui/ProviderSettingsActivity.kt` + `activity_provider_settings.xml` | **REBUILD** (20.8) |
| `res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml`, `style/BottomNavIndicator` (orphans) | Delete |

**Why.** Graded **C+**: structurally the most correct screen and visually the loudest. 23 rows, six
declared tile colours, and `values/themes.xml:88-99` collapsing four of them into blue, so **22 of
23 rows render an identical blue tile** and the accent budget is spent about twenty times over on
one screen. 18dp chevrons where the icon scale says 20. Dividers at `marginStart="72dp"` where the
text origin is 68, on every settings screen in the app. Zero press animation on 23 clickable rows.
And roughly 30 real settings unreachable, including three the Home tab actively reads.

### 19.3 Component tree

```
Toolbar 56dp: title "Настройки", no action
RecyclerView (rows from 8.1, hairlines at 68dp, section headers from 8.2)
```

Four groups, 15 rows, all tiles **neutral**, **zero accent on this screen**.

**Подключение**

| Row | Trailing | Value example | Goes to |
|---|---|---|---|
| Режим подключения | chevron | `VPN` | 20.1 |
| Прокси по приложениям | chevron | `12 приложений` / `Выкл` | 20.2 |
| Маршрутизация | chevron | `4 правила` | 20.3 |
| DNS | chevron | `Cloudflare` | 20.4 |

**Обход блокировок**

| Row | Trailing | Value example | Goes to |
|---|---|---|---|
| Обход блокировок | chevron | `Mux, фрагментация` / `Выкл` | 20.5 |
| Проверка серверов | chevron | `HTTP-запрос` | 20.6 |
| Локальный прокси | chevron | `Выкл` / `Порт 10808` | 20.7 |

**Подписка**

| Row | Trailing | Value example | Goes to |
|---|---|---|---|
| Провайдеры | chevron | `2 провайдера` | 20.8 |
| Что настроил провайдер | chevron | `4 настройки` (row hidden entirely when the number is 0) | 20.9 |
| Перенести подписку | chevron | - | 20.10 |

**Приложение**

| Row | Trailing | Value example | Goes to |
|---|---|---|---|
| Оформление | chevron | `Тёмная` | 20.11 |
| Язык | chevron | `Русский` | 20.12 |
| Запуск при загрузке | **switch** | - | in place |
| Резервное копирование | chevron | `Копия 09.07.2026` | 20.13 |
| О приложении | chevron | `1.9.42` | 20.14 |

Maximum 7 rows per group, maximum 4 groups per screen, maximum 2 levels below a tab: all satisfied.
Every row shows its current value, which is Incy's single best idea and costs nothing.

### 19.4 States

Default (values render from local preferences instantly; nothing on this screen ever waits on the
network except the «Провайдеры» count and the «Что настроил провайдер» count, which render their
last known value and update silently); disabled rows (a row whose feature is unavailable on this
device renders at alpha 0.38 with its reason in the subtitle, for example «Always-on VPN» on a
device without the setting); offline (nothing changes; this screen is local); no states beyond that,
because a settings hub that can fail to load is a settings hub built wrong.

### 19.5 Where every currently hidden preference goes

`res/xml/pref_settings.xml` is loaded only by `SettingsActivity`, which nothing launches. Its
~30 orphan preferences are assigned here. **Nothing is left unassigned; a preference with no home is
deleted with its code, in the same change.**

| Preference | New home |
|---|---|
| `PREF_SNIFFING_ENABLED` | 20.1 Режим подключения › Дополнительно |
| `PREF_ALLOW_INSECURE` | Deleted from the UI. It is a per-server field in the editor (21) and a global switch for it is a footgun |
| `PREF_FAKE_DNS` | 20.4 DNS |
| `PREF_LOCAL_DNS_ENABLED`, `PREF_LOCAL_DNS_PORT` | 20.4 DNS › Дополнительно |
| `PREF_VPN_DNS`, `PREF_REMOTE_DNS`, `PREF_DOMESTIC_DNS`, `PREF_DNS_HOSTS` | 20.4 DNS |
| `PREF_VPN_MTU`, `PREF_VPN_INTERFACE_ADDRESS` | 20.1 › Дополнительно |
| `PREF_HEV_LOG_LEVEL`, `PREF_HEV_TIMEOUT` | 20.14 О приложении › Журнал › Уровень журнала |
| `PREF_LOGLEVEL` (core) | 20.14 О приложении › Журнал › Уровень журнала |
| `PREF_OUTBOUND_DOMAIN_RESOLVE` | 20.1 › Дополнительно |
| `PREF_FRAGMENT_LENGTH`, `PREF_FRAGMENT_INTERVAL`, `PREF_FRAGMENT_PACKETS` | 20.5, revealed inline when Фрагментация is on |
| `PREF_MUX_XUDP_QUIC` (and mux concurrency) | 20.5, revealed inline when Mux is on |
| `PREF_DELAY_TEST_URL`, `PREF_REAL_PING_CONCURRENCY` | 20.6 Проверка серверов |
| `PREF_IP_API_URL` | 20.6 Проверка серверов › Дополнительно |
| `PREF_DOUBLE_COLUMN_DISPLAY` | **Deleted.** A two-column server list contradicts the ledger |
| `PREF_GROUP_ALL_SERVERS` | 20.8 Провайдеры › Группировка |
| `PREF_CONFIRM_REMOVE` | 20.8 Провайдеры › «Спрашивать перед удалением» (default off, because deletes now undo) |
| `PREF_START_SCAN_IMMEDIATE` | **Deleted.** The scanner starts scanning; that is what a scanner does |
| `PREF_SPEED_ENABLED` | **Deleted.** The numeric strip is always shown when connected |
| `PREF_PREFER_IPV6` | 20.1 › Дополнительно |
| `PREF_APPEND_HTTP_PROXY` | 20.7 Локальный прокси |
| `PREF_AUTO_REMOVE_INVALID`, `PREF_AUTO_SORT_AFTER_TEST` | 20.6 Проверка серверов |
| `PREF_SHOW_MEMORY` | **Deleted with the memory card.** A RAM gauge is not a consumer home-screen citizen |
| `PREF_AUTO_FALLBACK` | 20.5 Обход блокировок › «Автоматически пробовать другой сервер» (**on by default**; this gates the post-connect health check the Home tab already reads) |
| `PREF_LANGUAGE` | 20.12 Язык |
| `PREF_UI_MODE_NIGHT` | 20.11 Оформление |

---

## 20. Every settings sub-page

**Shared shell.** Every page in this section is: the seamless toolbar (8.6) with a back button, a
title, at most one trailing action; a `NestedScrollView` on `?attr/colorBackground`; section headers
(8.2); rows (8.1) with hairlines at the page's single text origin; and, where a page's job is one
action, a single filled CTA 52dp at the bottom above 32dp of space.

**Text origin per page:** pages whose rows are toggles and values use the **16dp** origin and carry
no tiles. Pages that are themselves hubs (Провайдеры, О приложении) use the **68dp** origin with
neutral tiles. A page never mixes the two.

**A page never opens a single-choice `AlertDialog`.** 2 to 4 options is a `Segmented` (8.13); a
short cycle is `ic_unfold_more` in place; 5+ options is a list of `Row.Selectable` on this page or
one level down.

### 20.1 Режим подключения

**New activity** `ui/ConnectionSettingsActivity.kt` + `res/layout/activity_connection_settings.xml`.
Absorbs the Режим dialog, the IPv6 row, the bypass-LAN row, the Always-on row and six hidden
preferences.

```
Toolbar: back + "Режим подключения"
[ 24 ]
Segmented 8.13, full width, 48dp:  [ VPN ] [ Только прокси ] [ VPN + прокси ]
[ 8 ]
TextView App.Caption, gutter:  "VPN направляет весь трафик устройства через туннель."
                               (the line changes with the segment; it is the only explanatory
                                copy on the page and it is one sentence)
[ 24 ]
SectionHeader "Сеть"
Row.Toggle  "Обход локальной сети"   subtitle "Прямой доступ к устройствам в сети"      switch
Row.Toggle  "IPv6"                   subtitle "Включить IPv6-адресацию в туннеле"        switch
Row.Navigation "Always-on VPN"       value "Системная настройка"                          chevron
                -> opens the system VPN settings screen; the subtitle says so, honestly:
                   "Постоянное подключение включается в настройках Android"
[ 24 ]
SectionHeader "Дополнительно"
Row.Value   "Определение доменов"    value "Как в системе"    ic_unfold_more
Row.Toggle  "Приоритет IPv6"                                   switch
Row.Value   "MTU"                    value "1500"             ic_unfold_more  (cycles 1280/1400/1500)
Row.Value   "Адрес интерфейса"       value "10.10.14.1"       chevron -> a one-field sub-sheet
Row.Toggle  "Sniffing"               subtitle "Определять домен из трафика"  switch
```

**Honesty rule.** Where a switch cannot do what its label implies without the OS, the page says so
rather than shipping a lie: Always-on and the kill switch are OS features and are linked to, not
faked. Both reference apps ship the switches and say nothing; that is the one thing a trust-category
product must not do.

States: default; a segment change applies immediately and, if the tunnel is up, shows a status strip
«Изменения применятся при следующем подключении» with «Переподключить»; disabled rows on devices
without the capability, at 0.38, with the reason in the subtitle.

### 20.2 Прокси по приложениям, and the app picker

**Files:** `ui/PerAppProxyActivity.kt`, `res/layout/activity_bypass_list.xml`,
`res/layout/item_recycler_bypass_list.xml` (**18dp radius, the only 18 in the app**),
`ui/PerAppProxyAdapter.kt`, `res/menu/menu_bypass_list.xml` (6 items);
`ui/AppPickerActivity.kt`, `res/layout/activity_app_picker.xml` (**a bare 10-line
`RecyclerView`**), `ui/AppSelectorAdapter.kt`. **RESTYLE** the first, **REBUILD** the picker.

```
Toolbar: back + "Прокси по приложениям" + 48dp overflow
[ 16 ]
Row.Toggle  "Включить"   subtitle "Выбирать, какие приложения идут через туннель"   switch
[ 8 ]
Segmented, full width, 48dp:  [ Только выбранные ] [ Все, кроме выбранных ]
[ 8 ]
TextView App.Caption, gutter: "Через туннель пойдут только отмеченные приложения."
[ 16 ]
SearchField 8.13  "Поиск приложений"
[ 8 ]
TextView App.Subtitle + Numeric, gutter: "Выбрано 12 из 214"
[ 8 ]
RecyclerView, hairlines at 68dp
└── Row per app: 40dp app icon in the tile slot (rounded to radius_tile 12) +
      title = app label (App.Title, maxLines 1) +
      subtitle = package name (App.Caption, maxLines 1, ellipsize middle) +
      trailing = MaterialCheckBox
```

Overflow (4 items, down from 6): `Выбрать все`, `Снять выделение`, `Инвертировать`,
`Только с интернетом`. Import/export of the app list moves to Резервное копирование.

The app picker becomes the same list in a sub-page with its own toolbar («Выберите приложения»), a
search field, an EmptyState («Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск»)
and a 52dp «Готово» CTA. A bare `RecyclerView` with no header, no empty state and no title is not a
screen.

States: loading (eight skeleton rows while the package list is enumerated **off the main thread**);
loaded; search empty; none selected (a caption under the segment: «Пока ничего не выбрано»);
disabled (when the master switch is off, the whole list renders at 0.38 and is not interactive).

### 20.3 Маршрутизация, and the rule editor

**Files:** `ui/RoutingSettingActivity.kt`, `res/layout/activity_routing_setting.xml`,
`res/layout/item_recycler_routing_setting.xml`, `res/menu/menu_routing_setting.xml` (**five actions
buried in an overflow the rest of the app does not have**); `ui/RoutingEditActivity.kt`,
`res/layout/activity_routing_edit.xml` (**raw upstream: bare `TextView` labels, `EditText`s, a
`Spinner`, `padding_spacing_dp16`**). **RESTYLE** the list, **REBUILD** the editor.

List:
```
Toolbar: back + "Маршрутизация" + 48dp overflow
[ 16 ]
Row.Value "Стратегия доменов"  value "IPIfNonMatch"  ic_unfold_more
[ 24 ]
SectionHeader "Правила"
RecyclerView, hairlines at 68dp
└── Row per rule: neutral tile ic_routing_24dp +
      title = rule name +
      subtitle = "12 доменов · 3 IP"  App.Subtitle + Numeric +
      trailing = MaterialSwitch
      (a locked preset rule shows a 16dp ic_lock_24dp in the value slot and no switch)
[ 32 ]
MaterialButton filled 52dp  "Добавить правило"       <- this page's one action
```
Overflow (4): `Импортировать из буфера`, `Импортировать из QR`, `Готовые наборы`,
`Экспортировать в буфер`.

Editor (rebuilt on the form system of 21):
```
Toolbar: back + "Правило" + 48dp ic_delete_24dp (red, only when editing an existing rule)
[ 24 ]
InputField  label "Название"
[ 16 ]
SectionHeader "Условия"
InputField  label "Домены"   multiline, helper "По одному в строке"
InputField  label "IP"       multiline, helper "По одному в строке"
InputField  label "Порт"     inputType=number
[ 24 ]
SectionHeader "Действие"
Segmented 48dp: [ Через туннель ] [ Напрямую ] [ Заблокировать ]
[ 32 ]
MaterialButton filled 52dp  "Сохранить"
```
The `Spinner` disappears from the product entirely.

States: empty rule list (EmptyState: «Правил пока нет» / «Правила решают, что идёт через туннель, а
что напрямую.» / filled «Добавить правило»); validation on blur; delete with an undo strip.

### 20.4 DNS

**New page** `ui/DnsSettingsActivity.kt`, absorbing the DNS single-choice dialog, the «Свой…» text
dialog and six hidden preferences.

```
Toolbar: back + "DNS"
[ 24 ]
SectionHeader "Сервер"
Row.Selectable  "Cloudflare"      value "1.1.1.1"                   state marker
Row.Selectable  "Google"          value "8.8.8.8"                   state marker
Row.Selectable  "Cloudflare + Google"  value "1.1.1.1, 8.8.8.8"     state marker
Row.Selectable  "AdGuard"         value "94.140.14.14"              state marker
Row.Selectable  "Quad9"           value "9.9.9.9"                   state marker
Row.Selectable  "Свой"            value = the configured address, or empty   chevron
                -> a one-field sub-sheet with validation on blur
[ 24 ]
SectionHeader "Дополнительно"
Row.Toggle  "Локальный DNS"     subtitle "Отвечать на DNS-запросы локально"   switch
Row.Value   "Порт локального DNS"  value "10853"  chevron   (revealed only when the switch is on)
Row.Toggle  "Fake DNS"          subtitle "Ускоряет подключение, ломает часть приложений"  switch
Row.Navigation "Свои записи"    value "3 записи"  chevron   -> a multiline field page
Row.Value   "Домашний DNS"      value "223.5.5.5"  chevron
```

Six `Row.Selectable` is above the 4-option segment threshold, which is exactly why this is a page
and not a dialog. Selection is instant, marked on two axes (P3 fill plus the accent check).

### 20.5 Обход блокировок

**New page** `ui/CircumventionActivity.kt`, absorbing the Mux rows, the fragmentation row, the mux
concurrency dialog and the auto-fallback preference. `docs/circumvention-settings-design.md` covers
the engineering; this is the surface.

```
Toolbar: back + "Обход блокировок"
[ 24 ]
TextView App.Body, gutter, maxWidth 60 characters:
   "Эти настройки помогают подключиться в сетях с фильтрацией. Включайте их, только если
    обычное подключение не проходит."
[ 24 ]
SectionHeader "Соединение"
Row.Toggle  "Мультиплексирование"   subtitle "Объединяет запросы в один канал"   switch
   [ revealed inline when on, expanding over motion_reveal 300 ]
   Row.Value  "Число соединений"    value "8"      Stepper 8.13 inline, range 1..128
   Row.Toggle "XUDP через QUIC"                                        switch
Row.Toggle  "Фрагментация пакетов"  subtitle "Разбивает TLS-рукопожатие против DPI"  switch
   [ revealed inline when on ]
   Row.Value  "Длина"     value "10-20"     chevron
   Row.Value  "Интервал"  value "10-20"     chevron
   Row.Value  "Пакеты"    value "tlshello"  ic_unfold_more
Row.Toggle  "Шум перед рукопожатием"  subtitle "UDP-шум для обхода эвристик"  switch
[ 24 ]
SectionHeader "Если не подключается"
Row.Toggle  "Пробовать другой сервер"  subtitle "Автоматически, при неудаче"  switch  [default ON]
```

The inline reveal is the one sanctioned height animation in the app (7.3). A parameter row that is
hidden because its parent is off is **removed from the tree**, not disabled: a disabled row for a
feature that is off is noise.

### 20.6 Проверка серверов

**New page** `ui/PingSettingsActivity.kt`, absorbing the Пинг dialog and four hidden preferences.
`docs/ping-methods-design.md` covers the engineering.

```
Toolbar: back + "Проверка серверов"
[ 24 ]
SectionHeader "Способ"
Row.Selectable  "Реальная задержка"   subtitle "Через ядро, точнее всего"     state marker
Row.Selectable  "TCP-соединение"      subtitle "Быстро, без прокси"           state marker
Row.Selectable  "HTTP-запрос"         subtitle "Проверяет реальный доступ"    state marker
Row.Selectable  "ICMP"                subtitle "Системный ping, часто закрыт" state marker
[ 24 ]
SectionHeader "Параметры"
Row.Value  "Адрес проверки"       value "cp.cloudflare.com"  chevron
Row.Value  "Одновременных проверок"  value "8"                Stepper inline, range 1..32
[ 24 ]
SectionHeader "После проверки"
Row.Toggle "Сортировать по задержке"                          switch
Row.Toggle "Удалять недоступные"    subtitle "Только те, что не ответили дважды"  switch
[ 24 ]
SectionHeader "Дополнительно"
Row.Value  "Сервис определения IP"  value "ip-api.com"  chevron
```

### 20.7 Локальный прокси

**Files:** `ui/LocalProxyActivity.kt`, `res/layout/activity_local_proxy.xml` - **1 035 lines,
113 off-scale dp values, 37 raw `textSize`, zero token references. The single worst file in the
repo.** **REBUILD.**

It also ships five sections of developer plumbing to consumers (a memory-limit chip group, SOCKS5
credentials, a hotspot endpoint with warnings, domain routing). The rebuild keeps what a person
sharing a tunnel with a TV or a laptop actually needs and puts the rest behind one disclosure.

```
Toolbar: back + "Локальный прокси"
[ 24 ]
Row.Toggle "Включить"  subtitle "Раздавать туннель другим устройствам в сети"  switch
[ 8 ]
TextView App.Caption, gutter:  "Адрес для других устройств: 192.168.1.42:10808"
                               (a 48dp inline copy action sits at the end of the line)
[ 24 ]
SectionHeader "Доступ"
Row.Toggle "Запрашивать логин и пароль"                                  switch
   [ revealed when on ]
   InputField label "Логин"     (inside the page, gutter 16, not a row)
   InputField label "Пароль"    with the eye toggle
   MaterialButton text "Сгенерировать заново"  48dp
Row.Value  "Порт"      value "10808"  chevron -> one-field sheet, validated 1024..65535
Row.Toggle "HTTP-прокси"   subtitle "Дополнительно к SOCKS5"             switch
Row.Toggle "Блокировать UDP"                                             switch
[ 24 ]
SectionHeader "Дополнительно"
Row.Value  "Ограничение памяти"  value "80 МБ"  ic_unfold_more   (cycles 40/60/80/100/150/без)
Row.Toggle "Скрывать значок в статусе"                                   switch
```

States: off (every row below «Включить» renders at 0.38 and is not interactive; the address caption
is absent); on but no network («Устройство не в сети. Адрес появится после подключения к Wi-Fi.»);
port conflict (inline error on the port field: «Порт занят. Выберите другой.»); credentials
generated (status strip «Новый пароль сгенерирован» with «Скопировать»).

### 20.8 Провайдеры

**Files:** `ui/ProviderSettingsActivity.kt`, `res/layout/activity_provider_settings.xml` (648
lines, 84 off-scale dp, 15 raw `textSize`); `ui/SubSettingActivity.kt`,
`res/layout/activity_sub_setting.xml`, `res/layout/item_recycler_sub_setting.xml`,
`ui/SubSettingRecyclerAdapter.kt` (**unreachable today**); `ui/SubEditActivity.kt`,
`res/layout/activity_sub_edit.xml` (291 lines of raw upstream form, **unreachable today**).
**REBUILD**, and **merge**: this is one page with a list, not three screens.

Note the current duplication: «Автообновление подписки» exists in the Settings tab **and** in
`ProviderSettingsActivity`, in two different visual languages, two taps apart, writing the same
`SubscriptionItem` fields. After this page exists, it lives here and only here.

```
Toolbar: back + "Провайдеры" + 48dp overflow ("Добавить провайдера")
[ 16 ]
RecyclerView, hairlines at 68dp
└── Row per provider
      tile     neutral ic_subscriptions_24dp
      title    provider name (from profile-title, clamped to 25 characters in the parser)
      subtitle "24 сервера · обновлён 5 минут назад"    App.Subtitle + Numeric
      trailing chevron  -> the provider detail page
      (a provider whose last update failed shows «не обновился» in @color/ping_bad in the value
       slot and a 20dp ic_refresh_24dp as its state marker)
[ 24 ]
SectionHeader "Обновление"
Row.Toggle "Автообновление"                                          switch
Row.Value  "Как часто"   value "1 час"   ic_unfold_more  (1 ч / 6 ч / 12 ч / 24 ч)
Row.Toggle "Обновлять при запуске"                                   switch
Row.Toggle "Уведомлять об обновлении"                                switch
[ 24 ]
SectionHeader "Список серверов"
Row.Value  "Сортировка"     value "Как у провайдера"  ic_unfold_more
Row.Toggle "Объединять все провайдеры в один список"                 switch
Row.Toggle "Спрашивать перед удалением"                              switch   [default OFF]
[ 24 ]
SectionHeader "Сеть"
Row.Toggle "Отправлять идентификатор устройства"  subtitle "Нужно для лимита устройств"  switch
Row.Value  "User-Agent"     value "Departament"    chevron
```

Provider detail page (replacing `SubEditActivity`'s upstream form):
```
Toolbar: back + provider name + 48dp overflow
[ 24 ]
InputField label "Название"   (the user's own name for it; the operator's title is the default)
[ 16 ]
InputField label "Ссылка"     inputType=textUri, ellipsize middle when unfocused
[ 4 ]  helper: "Ссылку выдаёт провайдер"
[ 24 ]
SectionHeader "Состояние"
Row.Fact  "Серверов"      value "24"
Row.Fact  "Обновлён"      value "09.07.2026 19:41"
Row.Fact  "Трафик"        value "12,4 из 50 ГБ"      (present only when the operator sends it)
[ 32 ]
MaterialButton filled 52dp  "Обновить"
MaterialButton text  48dp, ?attr/colorError  "Удалить провайдера"
```
Deleting a provider is irreversible from the app's point of view (the link may not be recoverable),
so it is the one confirmation dialog on this page: «Удалить провайдера?» / «Его серверы исчезнут из
списка. Ссылку придётся вводить заново.» / «Удалить».

### 20.9 Что настроил провайдер

**New page** `ui/OperatorSettingsActivity.kt`. This screen does not exist in either reference app,
and it is the second of the three things this product must own
(`30-reference-analysis.md` 6.3b).

Incy lets a provider silently set per-app proxy mode and list, force TCP fragmentation **over the
user's own setting**, enable UDP noise, redirect DNS resolution, install an auto-updating routing
profile, and remove the subscription URL from the user's own Share, Copy, QR and backup. Happ lets a
provider pick a UI colour. Both treat the operator as trusted and invisible. We take the protocol,
because a Remnawave panel needs it, and we **render** it.

```
Toolbar: back + "Что настроил провайдер"
[ 24 ]
TextView App.Body, gutter:  "Провайдер может менять часть настроек на вашем устройстве.
                             Здесь видно, что именно, и это можно вернуть."
[ 24 ]
SectionHeader "Сообщения"
Row.Toggle "Показывать объявления провайдера"                        switch   [user-owned, always]
Row.Navigation "Поддержка"   value "@departamentvpn"   chevron
[ 24 ]
SectionHeader "Что настроил провайдер"
Row.Navigation "Прокси по приложениям"  subtitle "Задано провайдером"  value "12 приложений"  chevron
Row.Navigation "Фрагментация"           subtitle "Задано провайдером"  value "Включена"       chevron
Row.Navigation "Маршрутизация"          subtitle "Задано провайдером"  value "Обновляется"    chevron
Row.Navigation "Определение адресов"    subtitle "Задано провайдером"  value "DoH"            chevron
[ 24 ]
MaterialButton text 48dp, full width, ?attr/colorPrimary  "Вернуть мои настройки"
```

The five rules, restated so an implementer cannot soften them:

1. **Every directive that changes device behaviour appears here**, in Russian, with the subtitle
   «Задано провайдером» and the value it set. Nothing applies invisibly.
2. **Anything that overrides a user setting is revertable.** «Вернуть мои настройки» restores the
   local value and marks the subscription as locally overridden; the next refresh respects the mark.
3. **`hide-url` is refused as specified.** We may honour "keep this out of a shared backup"; we
   never remove the owner's ability to read his own URL. A managed product may keep secrets from a
   scanner, never from its owner.
4. **The operator supplies content and severity, never presentation.** Text, links, and one of three
   severities (`info` / `warning` / `error`) which we map onto our own tokens. Colour directives
   (`sub-info-color`, `banner-bg-color`, `banner-button-color`) are parsed and **the value is
   discarded**. Icon choices are an enumeration we render (Incy's `icon-presets` pattern
   generalised): the operator names a key, we draw our glyph, at our size, in our colour, with a
   documented fallback for unknown keys.
5. **No forced modal, at any duration.** The operator gets a row and a strip. He never takes the
   screen. `provider-notifications`' "enterprise modal timer 1-10 s" is refused outright.

The row is hidden from the Настройки hub when the count is zero, so a user whose provider changes
nothing never sees a page about it.

### 20.10 Перенести подписку

**Files:** `tv/TvSendActivity.kt`, `res/layout/activity_tv_send.xml` (128 lines, hardcoded 16/20dp);
`tv/TvReceiveActivity.kt`, `res/layout/activity_tv_receive.xml` (58 lines, landscape-locked,
overscan-padded). **RESTYLE** both; the flow is sound.

Send:
```
Toolbar: back + "Перенести подписку"
[ 24 ]
TextView App.Body, gutter: "Откройте Departament на телевизоре и выберите «Принять подписку».
                            Затем отсканируйте код с этого экрана."
[ 24 ]
Row.Navigation "Подписка"  value "Базовый"  chevron   (only when there is more than one)
[ 24 ]
The QR block from 13.3, 240dp, centred
[ 32 ]
MaterialButton tonal 48dp "Показать ссылку"   -> reveals the URL as selectable text
```
Receive keeps its overscan padding (48/27dp is TV-safe and is the one place off-scale values are
justified; document the exception in `dimens.xml` as `tv_overscan_h` / `tv_overscan_v`), its
landscape lock and its D-pad focus, and adopts the ramp, the toolbar and the EmptyState.

### 20.11 Оформление

**New page**, replacing the Оформление single-choice dialog.

```
Toolbar: back + "Оформление"
[ 24 ]
SectionHeader "Тема"
Row.Selectable "Системная"      subtitle "Как в настройках Android"   state marker
Row.Selectable "Тёмная"                                               state marker
Row.Selectable "Светлая"                                              state marker
Row.Selectable "Чёрно-белая"    subtitle "Без цветовых акцентов"      state marker
```
Four options is at the segment threshold, but each needs a subtitle, so rows win. The change applies
immediately with a 220ms `ease_standard` crossfade of the whole window (`recreate()` with the
default cross-fade, no custom animation).

Note: `AppearanceActivity` also owns the future app-icon chooser (a real safety feature for the
RU/FA audience, not vanity). Until those aliases exist, the section is absent, not stubbed.

### 20.12 Язык

**New page.** A list of `Row.Selectable`: `Системный`, `Русский`, `English`, `فارسی`, `中文`.
Each row's title is written **in its own language**, which is the only correct way to render a
language list. Change applies via `AppCompatDelegate.setApplicationLocales` and the window
re-creates with the standard cross-fade.

**Prerequisite (P0, blocks this page and half of this document):** the default string table is
384 English strings out of 463, so the permanent bottom navigation currently reads
`Home · Servers · Настройки · Аккаунт` on any non-Russian device. **The default locale becomes
Russian**: every string in `values/strings.xml` is translated, the current `values-ru/` (which
covers a different 459 and misses 321 of the base) is reconciled into it, and English moves to
`values-en/`. Until that lands, no screen in this document is honestly "done".

### 20.13 Резервное копирование

**Files:** `ui/BackupActivity.kt`, `res/layout/activity_backup.xml` (254 lines, 36 off-scale dp,
60dp rows, 14dp padding, 68dp divider inset, `textSize="16sp"`), `res/layout/dialog_webdav.xml`
(**four unlabelled `EditText`s in a bare ScrollView**). **RESTYLE** the page, **REBUILD** the WebDAV
form as a sub-page.

```
Toolbar: back + "Резервное копирование"
[ 24 ]
SectionHeader "На устройстве"
Row.Navigation "Создать копию"       subtitle "Серверы, настройки, провайдеры"   chevron
Row.Navigation "Восстановить из файла"                                           chevron
Row.Navigation "Отправить копию"     subtitle "Через любое приложение"           chevron
[ 24 ]
SectionHeader "Облако"
Row.Toggle  "Автокопия в WebDAV"                                                 switch
Row.Navigation "Настройки WebDAV"    value "cloud.example.com"                   chevron
Row.Fact    "Последняя копия"        value "09.07.2026 19:41"
```

WebDAV sub-page: four `InputField`s with labels above («Адрес», «Папка», «Логин», «Пароль»), helper
slots, validation on blur, and a 52dp «Проверить и сохранить» CTA whose in-flight state is the
button's own indicator and whose result is a status strip («Подключение проверено» /
«Не удалось подключиться. Проверьте адрес и данные входа.»).

**Restore is the one irreversible action here** and it is a confirmation dialog:
«Восстановить из копии?» / «Текущие серверы и настройки будут заменены.» / «Восстановить».

### 20.14 О приложении, Журнал, Схемы URL

**Files:** `ui/AboutActivity.kt`, `res/layout/activity_about.xml` (171 lines, **pure 2018 upstream**:
five unstyled 24dp-icon rows at `TextAppearance.AppCompat.Subhead`, `padding_spacing_dp16`, no
cards, no tiles, no brand font); `ui/LogcatActivity.kt` + `res/layout/activity_logcat.xml` +
`item_recycler_logcat.xml` + `menu_logcat.xml` (**unreachable**); `ui/UrlSchemeListActivity.kt` +
`activity_url_scheme_list.xml` (634 lines, 43 off-scale dp, 20 raw `textSize`, **a `depv://`
cheat-sheet shipped to consumers**); `ui/CheckUpdateActivity.kt` + `activity_check_update.xml`
(**unreachable, and this build is not distributed via GitHub releases - CUT**).
**REBUILD** About; **RESTYLE** the log; **REBUILD** URL schemes as one disclosure block.

```
Toolbar: back + "О приложении"
[ 32 ]
FrameLayout 64x64 radius 20 ?attr/colorSurfaceContainerHighest + 32dp ic_launcher_foreground
    layout_gravity center_horizontal
[ 16 ]
TextView "Departament VPN"  @style/TextAppearance.App.Title, centred
[ 4 ]
TextView "1.9.42 (1942)"    @style/TextAppearance.App.Caption + Numeric, centred
[ 32 ]
SectionHeader "Поддержка"
Row.Navigation  ic_telegram_24dp   "Телеграм-канал"    value "@departamentvpn"   chevron
Row.Navigation  ic_support_24dp    "Написать в поддержку"                        chevron
[ 24 ]
SectionHeader "Правовое"
Row.Navigation  ic_privacy_24dp    "Политика конфиденциальности"                 chevron
Row.Navigation  ic_description_24dp "Открытые лицензии"                          chevron
[ 24 ]
SectionHeader "Для разработчиков"
Row.Navigation  ic_logcat_24dp     "Журнал"          value "Выкл"                chevron
Row.Navigation  ic_hub_url_scheme  "Схемы URL"       subtitle "Команды depv:// для автоматизации"  chevron
Row.Navigation  ic_source_code_24dp "Исходный код"                               chevron
```

The developer group is the honest home for the log page and the URL cheat-sheet: present, findable,
and clearly not part of the consumer path. It also absorbs the memory reading that used to sit on
Home, as a `Row.Fact` «Память» with a value, visible only when the log level is above off.

**Журнал** keeps its `RecyclerView` and gains: the standard toolbar, a level filter as a
`Segmented` (`Всё` / `Ошибки`), a `SearchField`, monospace 12sp body text with the timestamp in the
Numeric role, a level chip per line, a 48dp «Поделиться» toolbar action, an EmptyState
(«Журнал пуст» / «Включите журнал, чтобы записывать события.» / tonal «Включить»), and a
`Row.Value` for «Уровень журнала» that absorbs `PREF_LOGLEVEL`, `PREF_HEV_LOG_LEVEL` and
`PREF_HEV_TIMEOUT`.

**Схемы URL** collapses from five section cards to one page: a single explanatory paragraph, then
one `SectionHeader "Команды"` and a divided list of `Row.Fact` rows whose title is the human name
(«Запустить»), whose subtitle is the scheme in monospace 12sp (`depv://start`), and whose one
trailing affordance is a 48dp copy button. No card per section, no five-card wall.


---

## 21. The server editor - one form system, nine screens

### 21.1 Purpose

Let a user create or correct a single endpoint by hand, in Russian, without leaving the product.

### 21.2 Files today, and the verdict

| File | Lines | Verdict |
|---|---|---|
| `activity_server_vmess.xml`, `_vless.xml`, `_trojan.xml`, `_shadowsocks.xml`, `_socks.xml`, `_hysteria2.xml`, `_wireguard.xml` | 7 files | **REBUILD** on the shared includes |
| `activity_server_custom_config.xml`, `activity_server_group.xml`, `activity_server_proxy_chain.xml` + `item_recycler_proxy_chain_member.xml` | 4 files | **REBUILD** |
| `layout_address_port.xml`, `layout_transport.xml`, `layout_tls.xml`, `layout_tls_hysteria2.xml` | 4 shared includes | **REBUILD these four and nine screens are fixed at once** |
| `ui/ServerActivity.kt`, `ServerCustomConfigActivity.kt`, `ServerGroupActivity.kt`, `ServerProxyChainActivity.kt`, `ServerProxyChainMemberAdapter.kt` | - | Keep the logic; replace the view layer |
| `res/menu/action_server.xml` | - | Restyle |

**Grade F.** Bare `EditText`s with the platform underline, bare `Spinner`s with the platform
triangle, labels as unstyled `TextView`s at `TextAppearance.AppCompat.Subhead`,
`padding_spacing_dp16` from the old scale, and **English lowercase field labels**: `remarks`,
`address`, `port`, `alterId`, `Password(Optional)`, `Reserved(Optional, separated by commas)`. A
user who taps «Изменить» on a server leaves a 2026 product and lands in a 2019 tool mid-session.
These screens are also **currently unreachable** because of the long-press regression (12.2); they
come back the moment that is fixed, so they cannot stay as they are.

### 21.3 The form system

Three components, defined once, used by all nine screens.

**`FormField`** = `InputField` 8.12 with its label above and its helper below, at the page gutter,
16dp between fields inside a group.

**`FormSelect`** = a `Row.Value` with `ic_unfold_more` for 2 to 4 choices, or a `Row.Navigation`
into a `Row.Selectable` list for 5+. **Every `Spinner` in the app is deleted.**

**`FormSection`** = `SectionHeader` 8.2 plus 8dp plus its fields.

```
Toolbar 56dp: back + "Сервер" (or the protocol name when editing) + 48dp overflow
ScrollView, gutter 16, IME insets applied
[ 24 ]
FormSection "Основное"
   FormField  "Название"            helper "Как он будет называться в списке"
   FormField  "Адрес"               inputType textUri
   FormField  "Порт"                inputType number, helper "1 - 65535"
   FormField  "Идентификатор"       (per protocol: id / пароль / ключ)
   FormSelect "Шифрование"          value "auto"
[ 24 ]
FormSection "Транспорт"            <- layout_transport.xml, rebuilt
   FormSelect "Протокол передачи"   value "TCP"
   FormField  "Хост"                (revealed per transport; hidden fields are removed, not disabled)
   FormField  "Путь"
[ 24 ]
FormSection "Шифрование канала"    <- layout_tls.xml, rebuilt
   FormSelect "TLS"                 value "Reality"
   FormField  "SNI"
   FormField  "Fingerprint"
   FormField  "Public key"
   FormField  "Short ID"
   Row.Toggle "Разрешить небезопасное"  subtitle "Отключает проверку сертификата"  switch
[ 32 ]
--- bottom bar, pinned above the navigation inset, background ?attr/colorBackground,
    1dp top hairline, padding 16 ---
MaterialButton filled 52dp full width  "Сохранить"        <- the one lit element
```

Overflow (3 items): `Проверить задержку`, `Дублировать`, `Удалить` (red, with the undo strip).

### 21.4 Copy

Every `server_lab_*` string is translated. The complete replacement set, in the order the fields
appear:

| Resource | Was | Becomes |
|---|---|---|
| `server_lab_remarks` | `remarks` | `Название` |
| `server_lab_address` | `address` | `Адрес` |
| `server_lab_port` | `port` | `Порт` |
| `server_lab_id` | `id` | `Идентификатор` |
| `server_lab_id3` | `password` | `Пароль` |
| `server_lab_alterid` | `alterId` | `alterId` (a protocol identifier; it stays Latin, per the copy law's technical-identifier exemption) |
| `server_lab_security` | `security` | `Шифрование` |
| `server_lab_network` | `Network` | `Протокол передачи` |
| `server_lab_more_function` | `Transport` | `Транспорт` |
| `server_lab_request_host` | `host` | `Хост` |
| `server_lab_path` | `path` | `Путь` |
| `server_lab_stream_security` | `TLS` | `Шифрование канала` |
| `server_lab_stream_fingerprint` | `Fingerprint` | `Отпечаток` |
| `server_lab_stream_alpn` | `Alpn` | `ALPN` |
| `server_lab_allow_insecure` | `allowInsecure` | `Разрешить небезопасное` |
| `server_lab_sni` | `SNI` | `SNI` |
| `server_lab_public_key` | `PublicKey` | `Открытый ключ` |
| `server_lab_secret_key` | `SecretKey` | `Закрытый ключ` |
| `server_lab_preshared_key` | `PreSharedKey(optional)` | `Общий ключ` + helper `Необязательно` |
| `server_lab_short_id` | `ShortId` | `Short ID` |
| `server_lab_reserved` | `Reserved(Optional, separated by commas)` | `Reserved` + helper `Необязательно, через запятую` |
| `server_lab_local_address` | `Local address (optional IPv4/IPv6, separated by commas)` | `Локальный адрес` + helper `Необязательно, через запятую` |
| `server_lab_local_mtu` | `Mtu(optional, default 1420)` | `MTU` + helper `По умолчанию 1420` |

**The `(Optional)` suffix pattern is deleted everywhere.** Optionality belongs in the helper line,
not in the label. `title_server` («Config») becomes «Сервер».

### 21.5 States

New (empty fields, «Сохранить» disabled until Адрес and Порт are valid); editing (fields pre-filled,
«Сохранить» enabled only after a change); field error (inline, on blur, red helper plus red stroke,
focus moves to the first invalid field on a failed save); saving (CTA indicator, form disabled);
saved (`finish()` plus a status strip «Сервер сохранён» on the Серверы tab); custom-config editor
(one monospace multiline field with a validity line under it: «Проверено» in
`?attr/colorTertiary` or the parse error in `@color/ping_bad`, updated on blur); unsaved changes on
Back (a confirmation dialog: «Не сохранять изменения?» / «Изменения будут потеряны.» /
«Не сохранять»); long content (a 300-character path field scrolls horizontally inside itself; the
label never truncates); font scale 200% (the form scrolls, the bottom bar stays pinned).

---

## 22. The surfaces outside the app window

### 22.1 The ongoing notification

**Files:** the notification builder in `service/`, `res/drawable/ic_notif_stop.xml`,
`ic_notif_restart.xml`, `ic_stat_name.xml`. **RESTYLE.**

```
Small icon:  ic_stat_name (monochrome, 24dp, the shield silhouette)
Title:       "Подключено · Нидерланды, Амстердам"
Text:        the subscription state line, and only when it is not `активна`:
             "Истекает через 3 дня"            <- signature moment 3's third surface
             otherwise: "12,4 ГБ · 02:14:07"   (traffic and uptime, tabular)
Colour:      ?attr/colorPrimary as the accent colour
Actions:     [ Отключить ]  [ Сменить сервер ]
Ongoing, not dismissible while connected; low priority, silent, no vibration
```

The notification never shows a raw speed pair, never shows an exit code, and never shows English.

### 22.2 Quick Settings tile

**File:** `service/QSTileService.kt`. **RESTYLE.**

| Tile state | Label | Subtitle | Icon |
|---|---|---|---|
| Inactive | `Departament` | `Отключено` | `ic_shield_outline` |
| Active | `Departament` | the server remark, flag stripped, clamped to 20 characters | `ic_shield_filled` |
| Unavailable | `Departament` | `Нет подписки` | `ic_shield_outline`, tile disabled |

### 22.3 Home-screen widget

**Files:** `receiver/WidgetProvider.kt`, `res/layout/widget_switch.xml` (a 45dp icon plus a white
`TextAppearance.AppCompat.Small` label - untouched by the redesign so far). **RESTYLE.**

```
Container: radius 20, ?attr/colorSurface, 1dp ?attr/colorOutlineVariant, padding 12
├── 40dp tile: the unified server icon (6.3) - the same tile as every other surface
├── 8
├── TextView  "Нидерланды"     App.Title, 1 line
├── TextView  "Подключено"     App.Caption, colour per state
```
Tapping toggles. The widget follows the app's theme, not the launcher's.

### 22.4 Launcher shortcuts

**Files:** `res/xml/shortcuts.xml`, `ui/ScStartActivity.kt`, `ScStopActivity.kt`,
`ScSwitchActivity.kt`, `ScScannerActivity.kt` (translucent, `excludeFromRecents`, no UI by design).
**KEEP**, with Russian labels and correct icons: `Подключить`, `Отключить`, `Переключить`,
`Сканировать QR`. Each stub gains a status-strip-equivalent: because it has no window, its result is
surfaced by the notification, not by a toast.

### 22.5 Tasker

**File:** `ui/TaskerActivity.kt`, `res/layout/activity_tasker.xml` (48 lines, toolbar title `""`).
**RESTYLE only:** give it the standard toolbar with the title «Действие Tasker», the ramp, and a
`Row.Selectable` list of the three actions. External integration; almost nobody sees it; it must
still not look like a different app.

### 22.6 Deep links and the confirmation surface

**File:** `ui/UrlSchemeActivity.kt`. Handles `v2rayng://install-config`,
`v2rayng://install-sub`, `depv://…` and `ACTION_SEND` text. **REBUILD the surface, keep the routing.**

Today `depv://import/{base64}` **silently mutates the user's server list**. Every import path now
lands on the confirmation sheet of 14.5 before anything is written. A deep link that arrives while
the app is closed opens `MainActivity` on Главная with the sheet already presented, never a bare
translucent activity that finishes.

---

## 23. Dialogs: what each of the eighteen becomes

Order of preference, always: **inline > expandable row > bottom sheet > dialog** (`00-rules.md`
7.6). A dialog is the last resort.

| Today | Becomes |
|---|---|
| Режим (single-choice) | `Segmented` on Режим подключения (20.1) |
| Пинг (single-choice) | `Row.Selectable` list on Проверка серверов (20.6) |
| DNS preset (single-choice) | `Row.Selectable` list on DNS (20.4) |
| Оформление (single-choice) | `Row.Selectable` list on Оформление (20.11) |
| Язык (single-choice) | `Row.Selectable` list on Язык (20.12) |
| Автообновление подписки (single-choice) | `ic_unfold_more` cycle row on Провайдеры (20.8) |
| DNS «Свой…» (text input) | One-field sheet from the «Свой» row (20.4) |
| Число соединений Mux (text input) | Inline `Stepper` on Обход блокировок (20.5) |
| «Ввести вручную» (text input, with **two hardcoded Russian literals in Kotlin** at `MainActivity.kt:2016` and `:2018`) | **Stays a dialog** (a free-text paste has no better home) with the literals moved to resources and the confirmation sheet of 14.5 after it |
| Удалить сервер | **Undo strip** (a server is re-importable) |
| Удалить все / дубликаты / недоступные | **Undo strip**, with the count in the message |
| Удалить подписку | **Dialog** (irreversible; the link may not be recoverable) |
| Удалить устройство | **Undo strip** (reversible within the poll window) |
| QR-код (content dialog) | Sheet (13) |
| Способ отправки (list) | System share sheet |
| Сумма пополнения (`dialog_top_up.xml`) | Sheet (15.8) |
| Аватар (list) | Sheet, two rows |
| Ошибка оплаты (**raw HTTP code shown to the user**) | Status strip with the taxonomy message; the code goes to the log |
| Ошибка входа (debug only) | Unchanged, still debug only |
| WebDAV (`dialog_webdav.xml`, four unlabelled fields) | Sub-page (20.13) |
| Фильтр конфигураций (`dialog_config_filter.xml`) | Sheet of `Row.Selectable` chips |
| Диагностика устройств (**"screenshot the raw server response"**) | **Deleted.** The body goes to the log |

Surviving dialogs, in total: manual entry, delete subscription, restore backup, sign out, unsaved
changes, and the debug auth diagnostic. Six, down from eighteen.

---

## 24. Cross-cutting: states, dead code, and the copy sweep

### 24.1 The state matrix

Columns are the states of `00-rules.md` 15. **A cell marked `-` means the state cannot occur on that
screen and the reason is stated in the screen's own section; every other cell is implemented work.**

| Screen | First run | Loading | Empty | Error | Offline | Partial | Gated | Long | Success |
|---|---|---|---|---|---|---|---|---|---|
| Главная | 11.4 | skeletons | 11.4 | strip | strip + stale | 11.4 | expired / no sub / device limit | 11.4 | moment 1 |
| Вход | 10.4 | CTA indicator | - | inline + strip | strip, CTA off | - | rate limit | 10.4 | hand-off |
| Серверы | 12.6 | 6 skeleton rows | 12.6 x3 | strip | cache + stale | per group | - | 12.6 | - |
| Server actions sheet | - | - | - | - | actions off | - | - | header wraps | undo strip |
| Аккаунт | gate | skeletons | 15.4 | error block | cache + stale | identity ok, card failed | signed out | 15.4 | strip |
| Купить | - | 4 skeleton rows | 16.4 | 16.4 | cache, CTA off | - | device max | 16.4 | strip + finish |
| Устройства | - | 5 skeleton rows | 17.4 x2 | 17.4 | cache, deletes off | - | no subscription | 17.4 | undo strip |
| История | - | 6 skeleton rows | 18.5 | 18.5 | cache | - | - | 18.5 | - |
| Настройки | - | - | - | - | local only | - | - | values ellipsise | - |
| Every sub-page | - | inline where remote | per page | per page | per page | - | per page | wraps | strip |
| Server editor | new | - | - | inline | save off | - | - | scrolls | finish + strip |
| Scanner | permission | - | - | strip | - | - | no camera | - | haptic + sheet |

### 24.2 Everything that gets deleted

**14 unreachable screens**, resolved one by one rather than left to rot:

| Screen | Resolution |
|---|---|
| `ServerActivity` and the 8 editor siblings | **Wired back** via the actions sheet (12.9) and rebuilt (21) |
| `SubSettingActivity`, `SubEditActivity` | **Merged** into Провайдеры (20.8) |
| `SettingsActivity` + `pref_settings.xml` | **Deleted** after 19.5 assigns every preference |
| `CheckUpdateActivity` | **Deleted.** This build is not distributed via GitHub releases |
| `LogcatActivity` | **Wired back** under О приложении › Журнал (20.14) |
| `ServerActionsSheet` | **Wired back** (12.9) |

**Orphan resources deleted:** `bg_nav_header.xml`, `nav_header_bg.png`, `bg_acc_option.xml`,
`bg_speed_chip.xml`, `ripple_card.xml`, `bg_chip_gold.xml`, `ic_circle.xml`,
`bg_settings_glass.xml`, `bg_traffic_gradient.xml`, `font/montserrat_thin.ttf` (152 KB of a second,
unused typeface), `layout_setting_row.xml`, `layout_setting_toggle_row.xml`,
`item_recycler_footer.xml`, `menu_bottom_nav.xml`, `color/bottom_nav_item_color.xml`,
`style/BottomNavIndicator`, `style/TabLayoutTextStyle`, `style/BrandedSwitch`.

**Dead code paths removed:** `MainActivity.locateSelectedServer()` (no caller),
`importManually()` (superseded), `MainAdapterListener.onShare/onEdit/onRemove` in
`ActivityAdapterListener` (dead once the sheet is wired, then re-pointed at it),
`layout_home_account.xml`'s entire `group_login` half, `measureHomeMetaHeight()` (which inflates and
measures **every** subscription page on **every** rebuild).

### 24.3 The copy sweep

Four jobs, all mechanical, all blocking:

1. **Locale.** `values/strings.xml` becomes Russian (384 strings translated); the existing
   `values-ru/` is reconciled into it; English moves to `values-en/`. Start with
   `bottom_nav_home` and `bottom_nav_servers`, which are on literally every frame.
2. **Dashes.** 22 hits across `values/strings.xml`, `strings_pay.xml`, `strings_account.xml`,
   `strings_local_proxy.xml`, `strings_devices.xml`, `strings_deeplink.xml`, `strings_perapp.xml`,
   `strings_auth.xml` and the `values-ru/`, `values-vi/`, `values-zh-rCN/`, `values-zh-rTW/`,
   `values-bn/`, `values-ar/` translations. Hyphen, comma, colon, full stop, or a line break.
   Verify with `grep -rn -e '—' -e '–' values*/strings*.xml`.
3. **Three dots to `…`.** Verify with `grep -rn '\.\.\.' values*/strings*.xml`.
4. **Hardcoded Russian in Kotlin.** `MainActivity.kt:2016`, `:2018`, `:2101`, `:2103`, `:2113`,
   `:2115` move to resources.

### 24.4 The operator message component

One component, one lifetime, one place: row 5 of the subscription card (11.6). It absorbs Happ's
three parallel channels (`announce`, `sub-info-*`, `sub-expire*`) and Incy's `announce`.

```
LinearLayout inside the card, marginTop 16dp, above the action zone
├── ImageView 20dp, severity glyph (ic_info / ic_warning / ic_error)
├── Space 12
├── TextView  App.Body, weight 1, maxLines 5, then ellipsis
├── [ optional text button, the operator's own label, max 20 characters ]
└── ImageButton 48dp ic_close, cd "Скрыть"
```

- Caps are enforced **in the parser, not in the view**: title <= 25 characters, message <= 200
  characters, per-server description <= 30 characters, 5 rendered lines. Happ's best idea: remove
  the truncation failure mode at the source.
- `0` is a real third state: absent / value / explicit off. An operator can retract a message and
  reclaim the space.
- Dismissal is keyed on a **hash of the message text**, so a new message re-appears while the same
  one stays gone.
- Severity maps onto our tokens. The operator's colour directives are parsed and discarded.

---

## 25. Decisions this plan takes

### 25.1 Taken here, inside existing law

| # | Decision |
|---|---|
| **D-A1** | Four destinations in the order Главная, Серверы, Аккаунт, Настройки, on both platforms. Desktop adds Серверы to match |
| **D-A2** | The bottom navigation is always visible, including first run and signed out |
| **D-A3** | The Аккаунт destination is always present; signed out it is a sign-in gate, not an absence. This answers the open question at the end of `21-account-survey.md` |
| **D-A4** | Cold start always lands on Главная. Sign-in is never the launch destination; first run is a state of Главная |
| **D-A5** | Skeletons are static. There is no pulse and no shimmer, because there is no token for one and because a placeholder that animates is decoration |
| **D-A6** | The 40dp tile is a closed three-value system: neutral, one accent per screen, one destructive per screen. `icon_tile_green/orange/purple/yellow` and their attrs are deleted |
| **D-A7** | Ping renders as neutral text with a unit; only "unreachable" is coloured, and it carries the word «нет ответа». Green is never a latency colour |
| **D-A8** | The connect ring stays neutral in every state except «подключение», where it carries the accent arc. The connected signal is the filled shield (accent) plus the green word and dot. This supersedes `30-reference-analysis.md` 2.3's "ring settles green", which would put two hues on one object |
| **D-A9** | A row may carry one value **and** one state marker. A state marker is not a trailing action. No other pairing exists |
| **D-A10** | `∞` is deleted from the product; unlimited is the words «без ограничений» |
| **D-A11** | Every destructive action is an undo strip except five genuinely irreversible ones: delete subscription, delete provider, restore backup, sign out, discard unsaved edits |
| **D-A12** | Trial and promo get a designed home on Купить (16.6). Endpoints shipped with no surface are a decision by omission, and this is the decision |

### 25.2 Needs an owner decision, in `00-rules.md` section 18 row format

| Date | Decision | Rule affected |
|---|---|---|
| pending | **D-A13.** The Russian UI face is `<Golos Text / Onest / platform face>`, vendored to `res/font/ui_face.xml`, because the vendored Space Grotesk binary contains zero Cyrillic codepoints. Inherits `03-direction.md` D-1 | 3.4, 5.1 |
| pending | **D-A14.** The default string table becomes Russian and English moves to `values-en/`. Half of the copy in this document is invisible until this lands | 9.1 |
| pending | **D-A15.** `RequestLinkTelegram()` is ported to the Android API client, or «Привязать Telegram» permanently routes to the site. Owner request 0.4.9 cannot be satisfied on Android without one of the two | 0.4.9 |
| pending | **D-A16.** Section 20.9 «Что настроил провайдер» ships. It costs one screen and it is the product's strongest answer to "someone else controls my connection" | new |

---

## 26. Implementation sequence

The rule for the order below: **the app is never visibly half-converted.** Each wave is shippable on
its own, and no wave leaves two design languages sitting next to each other on the same screen or in
the same tab. Waves 1 and 2 change nothing a user can name and make every later wave mechanical;
that is deliberate.

### Wave 0 - verification, half a day

1. Run the `03-direction.md` 6.3 variable-font check: is Space Grotesk rendering at Light 300
   regardless of the declared weight? Fix `res/font/space_grotesk.xml` if so.
2. Run the four mechanical greps of `00-rules.md` 1.5 and record the baseline: 325 off-scale dp,
   ~100 raw `textSize`, 22 dashes, 3 divider insets, 10 radii.
3. Decide D-A13 and D-A14 with the owner. Nothing after wave 2 is honestly done without them.

### Wave 1 - the token layer. Invisible, and it unblocks everything

Files: `res/values/dimens.xml`, `colors.xml`, `values-night/colors.xml`, `styles.xml`, `themes.xml`,
`motion.xml`, `res/interpolator/ease_out_expo.xml`, `res/anim/press_scale.xml`, `subpage_enter.xml`,
`subpage_exit.xml`, `res/font/`.

Add every token in 8.0. Fix `Numeric` to weight 500. Fix `SettingsSectionLabel` padding to 24/8.
Change `press_scale` to 0.97. Delete `nav_press.xml`. Add `ease_out_expo`. Delete the four coloured
tile drawables and their attrs (nothing references them after wave 3, and until then they resolve to
neutral, which is the target anyway). Draw the 20 new icons of 6.2 and port the flag rasters of 6.3.

**Visible change: none.** Ship it.

### Wave 2 - the component atlas. Invisible, and it is the whole leverage

Files: `res/layout/view_row.xml`, `view_toolbar.xml`, `view_empty_state.xml`, `view_chip.xml`,
`view_meter.xml`, `view_status_strip.xml`, `view_skeleton_row.xml`, `view_search_field.xml`,
`bg_input_field.xml`, plus `ui/component/*.kt` binders.

Build all fourteen components of section 8 with their full state sets, and a debug gallery activity
that renders every component in every state in all three themes at font scale 100% and 200%. Nothing
in the product uses them yet.

**Visible change: none.** Ship it.

### Wave 3 - the functional regressions. Small, visible, and overdue

1. Wire `MainRecyclerAdapter.bindServer()` back to `onItemLongClick` so `ServerActionsSheet` opens
   and the nine editors become reachable. Restyle the sheet's tiles to neutral (12.9).
2. Declare `android:enableOnBackInvokedCallback="true"`, delete the `onKeyDown` `KEYCODE_BACK`
   branch, and collapse the three Back handlers into one (9.3).
3. Delete the end-user diagnostic dialogs on Устройства and the raw HTTP code on Аккаунт.
4. Replace the top 10 `Toast` call sites with the status strip.

**Visible change:** things start working. No visual language changes yet.

### Wave 4 - the two screens the owner named

Sign-in (10) and Главная (11), rebuilt together, in one release, because they hand off to each
other and because Главная's first-run state replaces `layout_home_empty.xml`, which is today the
real sign-in screen. Delete every gradient and glow in the same change; there is no interim state in
which half the gradients are gone.

Also in this wave, because they are on the same screen: the subscription card (11.6), the
subscription state machine, the status strip's first real home, the bottom navigation (8.7) and the
tab toolbars (8.6).

**Visible change: large, and it is the whole first impression.** After this wave the two screens a
new user sees are finished, and every other screen is visibly older. That is the one moment of
inconsistency this plan accepts, and it is accepted because the alternative is shipping the current
sign-in screen for another three waves.

### Wave 5 - Серверы

The list, the row, the section header, the search field, the sort control, the empty states, the
add-source sheet, the scanner, the QR sheet (12, 13, 14). The Серверы tab is the second most-used
surface and it now matches Главная.

### Wave 6 - Настройки, top to bottom

The hub (19) first, then all fourteen sub-pages (20) in this order, because each later page reuses
the pattern the earlier one established:
Оформление, Язык (the two simplest `Row.Selectable` pages) ->
Режим подключения, DNS, Проверка серверов ->
Обход блокировок (the inline-reveal pattern) ->
Прокси по приложениям and the app picker ->
Маршрутизация and the rule editor (the first form page) ->
Локальный прокси ->
Провайдеры and the provider detail (the merge) ->
Резервное копирование and WebDAV ->
О приложении, Журнал, Схемы URL ->
Перенести подписку ->
Что настроил провайдер.

Delete `layout_settings_content.xml`, `SettingsActivity`, `pref_settings.xml` and
`CheckUpdateActivity` at the end of this wave, not before: every preference must have its new home
first.

### Wave 7 - Аккаунт and commerce

Аккаунт (15), Купить (16), Устройства (17), История платежей (18), the payment-method sheet and the
top-up sheet. This is the half we are ahead of both references on, and the half the owner asked to
have reworked button by button. It comes after Настройки because Настройки is where the design
system is proven at volume, and because the commerce screens depend on the subscription card that
wave 4 already shipped.

### Wave 8 - the editors and the long tail

The four shared includes and the nine editor screens (21), Tasker, the widget, the QS tile, the
shortcuts, the notification, the deep-link confirmation (22).

### Wave 9 - the copy sweep and the accessibility pass

The Russian default table, the dash sweep, the ellipsis sweep, the Kotlin literals (24.3). Then:
every icon-only control gets a `contentDescription`; every screen is opened at font scale 200%,
at 320dp width, in all three themes, with TalkBack on; every state in the matrix of 24.1 is
screenshotted.

### Wave 10 - the audit

Score every screen 0 to 4 on the five dimensions of `audit.native.md`. Ship bar: **>= 18/20 with no
dimension below 3.** Re-run every mechanical grep. Answer the seven questions of the Departament
slop test (`00-rules.md` 2.4) out loud, per screen, with the screenshot in front of you.

### The four checks that gate every wave

1. `grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint)="#'` over
   the touched layouts returns nothing.
2. Off-scale dp in the touched files is **zero**, not "fewer".
3. Every touched screen has its full state set from 24.1 implemented and looked at.
4. The screen was actually run: dark, light, mono; 100% and 200% font scale; 320dp and 600dp width.

---

## 27. One page for whoever joins mid-project

Departament VPN on Android is an instrument, not a storefront. Flat near-black planes, no shadows,
no gradients, no glows. One blue, spent on the single thing the screen wants you to press, and spent
nowhere at all on most screens: settings has none, servers has none, payments has none. A 56dp row
with a 40dp neutral tile at a 68dp text origin is the repeating unit of the entire product, and
every title and every hairline on every screen starts at that same x. Every quantity is set in Space
Grotesk with tabular figures at 620/1000 so numbers never move; all Russian prose is in a
Cyrillic-capable face, because the brand face has no Cyrillic at all. Density is comfortable, with a
four-value spacing melody instead of a uniform 16dp drone. Motion acknowledges in 90ms, changes
state in 220ms, reveals in 300ms, and performs exactly once in the whole product, for 600ms, when
the tunnel confirms. Every row declares what tapping it will do before you touch it. Every screen
ships its empty, error, offline and gated states or it is not finished. Every provider-applied
setting is listed and revertable, because the whole objection to a managed VPN is that someone else
controls your connection and every competitor answers that by hiding the control.
