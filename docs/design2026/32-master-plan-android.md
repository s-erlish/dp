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

