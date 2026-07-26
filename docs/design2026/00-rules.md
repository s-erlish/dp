# 00 - Rules

**Departament VPN - the operational design law for the 2026 rebuild.**

This document overrides taste disputes. Every designer, every implementer, every reviewer on
this project follows it. If a spec in `docs/design2026/` contradicts this file, this file wins
and the spec is a bug. If your instinct contradicts this file, your instinct is wrong until the
owner says otherwise in writing.

Scope: both clients, built from one product design.

| | Android | Desktop (Windows / Linux / macOS) |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Build root | `/home/user/dp/V2rayNG` | `/home/user/v2rayN/v2rayN` |
| UI code | `V2rayNG/app/src/main/java/com/v2ray/ang/ui/**` | `v2rayN/v2rayN.Desktop/Views/**` |
| UI resources | `V2rayNG/app/src/main/res/**` | `v2rayN/v2rayN.Desktop/Assets/**` |
| Stack | Kotlin + Material 3 + XML views | C# .NET + Avalonia (AXAML) |
| Shared core | n/a | `v2rayN/ServiceLib/**` |

---

## 0. Authority, precedence, and the design read

### 0.1 Precedence order

When two rules collide, the higher number wins. No exceptions without a written owner decision
recorded in section 18.

1. **The owner's explicit request**, in his own words, past or present. His standing requests are
   listed in 0.4 and are permanent until he retracts them.
2. **This file** (`docs/design2026/00-rules.md`).
3. **The Absolute Bans and the AI-slop test** from `.claude/skills/impeccable/SKILL.md`, quoted
   verbatim in section 1 and 2.
4. **Platform law**: Material 3 for Android (`.claude/skills/impeccable/reference/android.md`),
   and its desktop translation in section 12 of this file.
5. **Existing project conventions** already visible in `res/values/*.xml` and
   `Assets/Global*.axaml`.
6. **Personal taste.** Last. Always last.

### 0.2 The design read

> Reading this as: **product-register app UI** (design SERVES the product) for a **consumer VPN**
> on **native Android and native desktop**, with an **Incy** visual language (pure dark surface,
> one bright blue accent, Space Grotesk, Russian sentence-case), leaning toward **Material 3
> theming on Android and a hand-owned Avalonia token layer on desktop**.

That read is fixed. It is not re-negotiated per screen. One clarification the read predates: "Space
Grotesk" in that sentence is the **figure** face. Since D-1 and D-2 (section 18) the Russian text is
set in **Golos Text**, because the Space Grotesk binary carries no Cyrillic and never drew a Russian
letter in the first place. Everything else in the read stands as written.

**Register consequence** (from `reference/product.md`, verbatim):

> Product UI's failure mode isn't flatness, it's strangeness without purpose: over-decorated
> buttons, mismatched form controls, gratuitous motion, display fonts where labels should be,
> invented affordances for standard tasks. The bar is earned familiarity. The tool should
> disappear into the task.

**Dials** (taste-skill vocabulary, fixed for this project):
`DESIGN_VARIANCE 4` (product predictability, one deliberate asymmetric moment per screen max),
`MOTION_INTENSITY 4` (state-conveying motion only, one hero moment in the whole app),
`VISUAL_DENSITY 4` (comfortable, not a cockpit: the Incy review complaint "шрифт очень мелкий и
тесновато" is the failure we are avoiding).

### 0.3 Which skill file governs what

Read the file before you design the thing. Do not design from memory of it.

| You are doing | Read first |
|---|---|
| Anything at all | `impeccable/SKILL.md` (Absolute bans + AI slop test) and this file |
| Any app screen | `impeccable/reference/product.md` |
| Anything on Android | `impeccable/reference/android.md` |
| Spacing, grids, hierarchy, density | `impeccable/reference/layout.md` |
| Type ramp, weights, measure | `impeccable/reference/typeset.md` |
| Palette, contrast, dark theme | `impeccable/reference/colorize.md` |
| States, focus, forms, dropdowns, destructive actions | `impeccable/reference/interaction-design.md` |
| Any animation | `impeccable/reference/animate.md` |
| Phone to tablet, desktop window sizes, orientation | `impeccable/reference/adapt.native.md` |
| Cutting complexity out of a screen | `impeccable/reference/distill.md` |
| First-run, empty states, activation | `impeccable/reference/onboard.md` |
| Final pass before calling a screen done | `impeccable/reference/polish.md` |
| Reviewing someone else's screen | `impeccable/reference/audit.native.md` |
| Copy, labels, errors | `impeccable/reference/clarify.md` |
| Sanity-checking against category defaults | `taste-skill/SKILL.md` sections 4, 9, 14 |
| Component-level UX checks | `ui-ux-pro-max/SKILL.md` Quick Reference sections 1, 2, 8, 9 |

### 0.4 The owner's standing requests (permanent, non-negotiable)

Every one of these is already a decision. Do not re-litigate, do not "improve" past them.

1. Incy language: **pure dark surface, ONE bright blue accent**. Red is destructive only.
2. Brand font **Space Grotesk** for the figures, chips and the wordmark; **Golos Text** for every
   Russian string. The original request read "Space Grotesk for display, titles, chips and
   numerals", written before anyone measured that the vendored binary maps zero Cyrillic
   codepoints. The owner's choice of Golos Text (D-1, section 18) is the newer request and it wins;
   Space Grotesk keeps everything it can actually draw.
3. **Russian UI, sentence case.** No ALL-CAPS labels anywhere.
4. **₽** for currency, never "RUB", never "руб.".
5. **Tightened Account/profile screen**; tariff badge on the subscription card.
6. **Seamless sub-screen toolbar**: a sub-page toolbar shares the page background, no separate
   bar colour, no elevation line, no shadow.
7. **Unified server icon** treatment across every surface that lists servers.
8. **No ripple glow on the bottom navigation.**
9. Explicit **"Купить"** and **"Привязать Telegram"** CTAs where the account state calls for them.
10. **The sign-in screen and the first tab at launch are redesigned from scratch, minimalist.**
    "Сейчас все выглядит плохо" is the starting position; incremental polish of the old layout
    does not satisfy this.
11. **Settings on both platforms are worked through tab by tab**, all in one visual system.
    "Абы как" is a review failure.

---

## 1. The Absolute Bans

### 1.1 Quoted verbatim from `impeccable/SKILL.md`

> ### Absolute bans
>
> Match-and-refuse. If you're about to write any of these, rewrite the element with different structure.
>
> - **Side-stripe borders.** `border-left` or `border-right` greater than 1px as a colored accent on cards, list items, callouts, or alerts. Never intentional. Rewrite with full borders, background tints, leading numbers/icons, or nothing.
> - **Gradient text.** `background-clip: text` combined with a gradient background. Decorative, never meaningful. Use a single solid color. Emphasis via weight or size.
> - **Glassmorphism as default.** Blurs and glass cards used decoratively. Rare and purposeful, or nothing.
> - **The hero-metric template.** Big number, small label, supporting stats, gradient accent. SaaS cliché.
> - **Identical card grids.** Same-sized cards with icon + heading + text, repeated endlessly.
> - **Tiny uppercase tracked eyebrow above every section.** The 2023-era kicker (small all-caps text with wide tracking, "ABOUT" "PROCESS" "PRICING" above each heading) is now the saturated AI scaffold; it appears on 55-95% of generations regardless of brief, which is the definition of a tell. One named kicker as a deliberate brand system is voice; an eyebrow on every section is AI grammar. Choose a different cadence.
> - **Numbered section markers as default scaffolding (01 / 02 / 03).** Putting `01 · About / 02 · Process / 03 · Pricing` above every section is the eyebrow trope one tier deeper: reach for it because "landing pages do this" and you're scaffolding by reflex. Numbers earn their place when the section actually IS a sequence (a real 3-step process, an ordered flow, a typed timeline) and the order carries information the reader needs. One deliberate numbered sequence on one page is voice; numbered eyebrows on every section across the site is AI grammar.
> - **Text that overflows its container.** Long heading words plus large clamp scales plus narrow grids cause headline overflow on tablet/mobile. Test the heading copy at every breakpoint; if it overflows, reduce the clamp max or rewrite the copy. The viewport is part of the design.

### 1.2 What each ban means in this codebase

Web wording, native consequences. Each row is a defect if found.

| Ban | Android form | Desktop form | Rewrite as |
|---|---|---|---|
| Side-stripe borders | A `<View>` of width > 1dp used as a coloured left edge on a row or card; a `shape` drawable with a thick single-side stroke | `Border` with `BorderThickness="4,0,0,0"` and an accent `BorderBrush` | Full 1dp `?attr/colorOutlineVariant` hairline, or an 8% accent surface tint, or the leading 40dp icon tile |
| Gradient text | Any `LinearGradient` shader on a `TextView` paint | `LinearGradientBrush` as a `TextBlock.Foreground` | One solid colour. Emphasis via `textFontWeight`/`FontWeight` or the next step up the ramp |
| Glassmorphism as default | `RenderEffect.createBlurEffect`, translucent `android:background` over content, blurred bottom bars | `ExperimentalAcrylicBorder`, blurred backdrops on panels | Solid `?attr/colorSurface` / `Brush.Surface`. Depth comes from the surface-container ramp only |
| Hero-metric template | The connect screen reduced to a big number plus three stat chips plus an accent wash | Same on `HomeView.axaml` | Real live data with a labelled unit, one figure maximum per surface, no decorative supporting stats |
| Identical card grids | A `RecyclerView` of visually identical icon+title+subtitle cards where a plain divided list would read better | `ItemsRepeater` of identical `Border.Card` tiles | A divided list (`Border.Row` / row layout with hairline) at 56dp rows. Cards only when the criteria in 4.4 are met |
| Tiny uppercase tracked eyebrow | `android:textAllCaps="true"` plus `letterSpacing >= 0.08` on a label above a section | `TextBlock` with 10-11px + letter-spacing above a heading | `@style/SettingsSectionLabel` on Android, `TextBlock.SectionHeader` on desktop: sentence case, 16sp/16px, weight 700 |
| Numbered section markers | "01 Подключение", "02 Серверы" prefixes in settings groups or onboarding | Same | Nothing. The heading alone. Numbers only inside a genuine ordered flow (for example a 3-step Telegram link wizard) |
| Text that overflows | `singleLine="true"` hiding a real Russian string; a title clipped at 320dp width or at font scale 200% | `TextTrimming="CharacterEllipsis"` hiding a real string; clipping at the 900x600 minimum window | Shorten the Russian copy, or let it wrap to 2 lines with `maxLines="2"`. Never ship a truncated primary label |

### 1.3 Product-register bans, quoted verbatim from `impeccable/reference/product.md`

> ## Product bans (on top of the shared absolute bans)
>
> - Decorative motion that doesn't convey state.
> - Inconsistent component vocabulary across screens. If the "save" button looks different in two places, one is wrong.
> - Display fonts in UI labels, buttons, data.
> - Reinventing standard affordances for flavor (custom scrollbars, weird form controls, non-standard modals).
> - Heavy color or full-saturation accents on inactive states.
> - Modal as first thought. Modals are usually laziness. Exhaust inline / progressive alternatives first.

Note the third line against 0.4.2: **neither** of this product's two faces is a display face. Golos
Text is a Russian UI face used at real weights; Space Grotesk is a grotesque, legible at 11sp, used
at real weights, and scoped to figures, chips and the wordmark. That satisfies the ban. What the ban
forbids here is introducing a *third*, decorative family for headings.

### 1.4 Departament-specific bans

Additive. Same enforcement weight as 1.1.

1. **No second accent hue.** Blue (`#4C8DFF` dark, `#1E5FC7` light) is the only accent. Green
   (`#22C55E`) is a *status* colour for "подключено" / "оплачено" only. Red (`#F04452`) is
   destructive and error only. Amber (`color_warning` `#EAB308` dark, `#8A6300` light) exists only
   as the "истекает" / "ждёт оплаты" warning chip; it never becomes a button, a link, or a
   selection colour. No purple. Since D-5 the coloured **icon tile** system is exactly three -
   accent, destructive, neutral (3.6) - so orange, yellow, green and purple tiles are not used by
   new work; `icon_purple` and `Brush.Tile.Purple` remain aliases of blue and must stay so for as
   long as anything still references them.
2. **No nested cards.** A `MaterialCardView` inside a `MaterialCardView`, or a `Border.Card`
   inside a `Border.Card`, is a defect. Inside a card, group with spacing and one hairline.
3. **No decorative gradients or glows.** Includes: drop shadows used as glow, `elevation` above
   the values in 4.7, coloured outer glows on the connect control, `Brush.Ring.*` used anywhere
   except the single connect-sonar hero moment.
4. **No emoji as UI chrome.** Not in labels, not in buttons, not in settings rows, not in empty
   states, not in notifications, not in toasts. Emoji may appear only inside user-supplied
   content (a server remark the user typed, a Telegram display name).
5. **No off-scale spacing.** Any dp/px value not in the scale of section 3.1 is a defect. This
   includes 6dp, 10dp, 14dp, 18dp, 20dp, 28dp. This bans off-scale **spacing** - gaps, padding,
   margins, insets. Sizes come from 3.3 and radii from 3.2, so a declared token there
   (`meter_height` 6, `btn_min_width` 96, `radius_button` 16) is not an off-scale spacing value; a
   raw literal used as a gap is, whatever its number.
6. **No raw colour literals in layouts or views.** Android layouts use `?attr/...` theme
   attributes; AXAML uses `{DynamicResource ...}`. Hex belongs in `res/values*/colors.xml` and
   `Assets/GlobalResources.axaml` and nowhere else. The single tolerated exception is the icon
   tile fill set already defined as named colours.
7. **No `android:textAllCaps="true"`, no `ToUpper()` in a view or converter,** anywhere.
8. **No `Toast` on Android for anything the user can act on.** Snackbar with an action, or
   inline. Toast survives only for fire-and-forget confirmations already in the codebase, and new
   code does not add them.
9. **No dialog for a decision that can be inline.** See 7.6.
10. **No Latin UI text.** Every user-visible string is Russian, except protocol names (VLESS,
    VMess, Trojan, Shadowsocks, WireGuard, Hysteria2, SOCKS5), brand names (Telegram,
    Departament), units, and technical identifiers.
11. **No em-dash (`—`) or en-dash (`–`) in any user-visible string.** Hyphen `-` only. This
    applies to every `<string>` in `res/values*/strings*.xml` and every literal in
    `v2rayN.Desktop/Common/L.*.cs`. See 9.7.
12. **No new icon family.** One set, one stroke weight, one corner treatment. See section 10.
13. **No screen without its states.** A screen that ships with only the happy path is
    incomplete, not "phase one". See section 15.

### 1.5 Mechanical checks

Run these before claiming a UI change is done. A hit is a defect until justified in writing.

Android, from `/home/user/dp/V2rayNG/app/src/main/res`:

```bash
# raw colour literals in layouts
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint|app:strokeColor)="#' layout/ menu/
# all-caps labels
grep -rn 'textAllCaps="true"' layout/ values/
# a face chosen in a layout instead of by the ramp role (D-2 enforcement)
grep -rn 'android:fontFamily\|android:textSize' layout/
# off-scale spacing (allow 4/8/12/16/24/32/40/48/56/64)
grep -rnoE '"(-?[0-9]+)dp"' layout/ | grep -vE '"(0|1|2|4|8|12|16|20|22|24|28|32|36|40|44|48|52|56|64|72|80|100|120|152|160|176|212|230)dp"'
# em/en dash in shipped copy (literal form; the PCRE \x{2014} form fails in this environment)
grep -rn -e '—' -e '–' values*/strings*.xml
# emoji in shipped copy
python3 -c "import glob,re;p=re.compile('[\U0001F300-\U0001FAFF☀-➿]');[print(f,i,l.strip()[:80]) for f in glob.glob('values*/strings*.xml') for i,l in enumerate(open(f,encoding='utf-8'),1) if p.search(l)]"
# nested cards
grep -rn -A40 '<com.google.android.material.card.MaterialCardView' layout/ | grep -c 'MaterialCardView'
```

Desktop, from `/home/user/v2rayN/v2rayN/v2rayN.Desktop`:

```bash
# inline hex outside the token dictionary
grep -rnE '(Background|Foreground|BorderBrush|Fill|Stroke)="#' Views/ | grep -v GlobalResources
# off-scale spacing
grep -rnoE '(Margin|Padding|Spacing)="[0-9, ]+"' Views/
# em/en dash in shipped copy
grep -rn -e '—' -e '–' Common/L.*.cs
# StaticResource where theme-switching requires DynamicResource
grep -rn 'StaticResource Brush\.' Views/
# a face or a size chosen in a view instead of by the ramp class (D-2 enforcement)
grep -rn 'FontFamily=\|FontSize=' Views/
# the brand face must not be a blanket setter (GlobalStyles.axaml:257-265)
grep -rn 'Font.Brand\|Font.Grotesk' Assets/GlobalStyles.axaml
```

**Baseline scan, 2026-07-26.** Already clean and to be kept clean: raw colour literals in Android
layouts 0, `textAllCaps` 0, emoji in Android strings 0, desktop `StaticResource Brush.*` 0.
Existing debt to clear during the rebuild: **325 off-scale `dp` values across 25 Android layout
files** (3, 6, 10, 13, 14, 18, 26, 27, 34, 42, 45, 60, 68, 76, 88, 140, 200dp), 3 inline hex
values in desktop `Views/`, and the dash debt in 9.7. Every screen touched during the rebuild
leaves its own files at zero.

---

## 2. The AI-slop test

### 2.1 Quoted verbatim from `impeccable/SKILL.md`

> ### The AI slop test
>
> If someone could look at this interface and say "AI made that" without doubt, it's failed. Cross-register failures are the absolute bans above. Register-specific failures live in each reference.
>
> **Category-reflex check.** Run at two altitudes; the second one catches what the first one misses.
>
> - **First-order:** if someone could guess the theme + palette from the category alone, it's the first training-data reflex. Rework the scene sentence and color strategy until the answer isn't obvious from the domain.
> - **Second-order:** if someone could guess the aesthetic family from category-plus-anti-references ("AI workflow tool that's not SaaS-cream → editorial-typographic", "fintech that's not navy-and-gold → terminal-native dark mode"), it's the trap one tier deeper. The first reflex was avoided; the second wasn't. Rework until both answers are not obvious.

### 2.2 The product slop test, verbatim from `reference/product.md`

> Not "would someone say AI made this." Familiarity is often a feature here. The test is: would a user fluent in the category's best tools (Linear, Figma, Notion, Raycast, Stripe come to mind) sit down and trust this interface, or pause at every subtly-off component?

### 2.3 The Android slop test, verbatim from `reference/android.md`

> Would a fluent Android user trust this app, or trip on off-spec components? The most common tell is an iOS app wearing Android's skin: a bottom-only navigation copied from iPhone, a back arrow that ignores the system Back gesture, Cupertino-shaped switches and dialogs. Material 3 is the rulebook; follow its components and theme the brand through it.

### 2.4 The Departament slop test (operational, run on every screen)

Answer all seven out loud. A "yes" on any of 1-6, or a "no" on 7, means rework, not polish.

1. **Category reflex.** Could someone guess this palette from the words "VPN app"? Dark + blue
   *is* the category reflex. Our answer is not "avoid dark blue" (the owner chose it); our answer
   is that the blue must be **rationed to ~10% of coloured pixels** and the surface must be a
   genuine near-black ramp, not the category's navy-plus-neon-cyan-plus-glow. If your screen
   reads as "gamer VPN", it failed.
2. **Second-order reflex.** Having avoided navy-and-cyan, did you land on the next default:
   terminal-green mono, or Linear-clone grey-on-grey with a violet CTA? Both fail.
3. **The uniform-card tell.** Does the screen consist of N identical rounded rectangles with an
   icon, a title and a subtitle? If yes, at least one of them should have been a divided list row.
4. **The decoration tell.** Point at every non-text pixel and say what it communicates. A glow, a
   gradient, a hairline that separates nothing, a chip repeating what the title says, a status dot
   next to a label that already carries the status: delete it.
5. **The copy tell.** Read every visible string aloud in Russian. Does any of it sound translated,
   marketing-y, or like a machine trying to sound warm ("Ваша безопасность - наш приоритет")? See
   section 9.
6. **The state tell.** Open the screen with no data, with a failed request, with the device
   offline, with a 40-character server name, and at font scale 200%. Does any of those look
   unconsidered? See section 15.
7. **The trust test.** Would a user who lives in Raycast, Linear and Telegram sit down in front of
   this screen and trust it? Every control standard-shaped, every state legible, nothing that
   makes them pause and wonder if they clicked the right thing.

---

## 3. Tokens: the single source of truth

**Rule: no UI file contains a raw value that a token covers.** Both platforms already carry a
mirrored token set. Use it. If you need a value that does not exist, you add it to the token file
first, with a comment saying what it is for, and only then use it.

Files:
- Android: `res/values/dimens.xml`, `res/values/colors.xml`, `res/values-night/colors.xml`,
  `res/values/styles.xml`, `res/values/themes.xml`, `res/values-night/themes.xml`,
  `res/values/attrs.xml`, `res/values/motion.xml`, `res/interpolator/*.xml`, `res/anim/*.xml`,
  `res/font/*.xml` and the vendored faces (`golos_text_regular|medium|bold.ttf`,
  `space_grotesk*`, plus `GOLOS-TEXT-LICENSE.txt`)
- Desktop: `Assets/GlobalResources.axaml` (tokens), `Assets/GlobalStyles.axaml` (component
  styles), `Common/Motion.cs` (C# mirror of the motion scale), `Common/MotionState.cs`
  (reduced-motion broadcast), `Assets/Fonts/*` (`GolosText-Regular|Medium|Bold.ttf`,
  `SpaceGrotesk.ttf`)

### 3.1 Spacing scale (the ONLY spacing values)

| Token (Android) | Token (Desktop) | Value | Use |
|---|---|---|---|
| `@dimen/space_4` | `Space.4` | 4dp/px | Glyph-to-label, chip inner vertical, hairline offsets |
| `@dimen/space_8` | `Space.8` | 8dp/px | Between tightly-related siblings, chip inner horizontal, minimum gap between two touch targets |
| `@dimen/space_12` | `Space.12` | 12dp/px | Row inner vertical, card inner tight, icon tile to text |
| `@dimen/space_16` | `Space.16` | 16dp/px | Screen gutter, card padding, default block separation |
| `@dimen/space_24` | `Space.24` | 24dp/px | Between distinct sections inside a screen |
| `@dimen/space_32` | `Space.32` | 32dp/px | Above the first section after a hero; below the last section before a bottom CTA |

Derived, allowed, already tokenised: `@dimen/screen_gutter` = 16dp (`Size.Gutter`, `Gutter`
Thickness), `@dimen/row_min_height` = 56dp (`Size.Row`), `@dimen/tile_size` = 40dp (`Size.Tile`),
`@dimen/tile_glyph` = 22dp (`Size.Glyph`).

**Rhythm rule** (from `reference/layout.md`): related elements 4-12, sections 24-32. Do not make
every gap 16. A screen where every vertical gap is identical has no hierarchy and fails the squint
test.

**Tablet / wide window:** gutter steps 16 -> 24 at `sw600dp` (Android `values-sw600dp`) and at
window width >= 1000px (desktop). Nothing else in the scale changes.

### 3.2 Radius scale

| Token (Android) | Token (Desktop) | Value | Applies to |
|---|---|---|---|
| `@dimen/radius_chip` | `Radius.Chip` | 12dp | Chips, badges, small pills that are not fully round, segmented-control thumb |
| `@dimen/radius_tile` | `Radius.Tile` | 12dp | 40dp icon tiles, flag tiles, avatar squares |
| `@dimen/radius_button` | `Radius.Button` | 16dp | **Every button variant that carries a label**, input field, search field, price option, segmented-control track, snackbar action hit shape (D-6, D-7) |
| `@dimen/radius_card` | `Radius.Card` | 20dp | Cards, dialogs, flyout bodies, elevated panels, bottom-sheet body, toast surface, empty-state tile |
| `@dimen/radius_sheet` | `Radius.Sheet` | 24dp top only | Bottom sheet / drawer top corners |
| `@dimen/radius_pill` | `Radius.Pill` | 100dp | Full-round, and **only** where width == height or the shape is intrinsically a track: icon-only buttons, avatars, page dots, sheet drag handle, connect disc, progress meter ends, switch track, the M3 navigation active indicator |
| (n/a) | `Radius.Search` | 14px | Desktop search field. **Retired by D-7**: new work uses `Radius.Button` 16. The key stays in the token file until the last reference migrates |
| (n/a) | `Radius.Traffic` | 8px | Desktop traffic meter bar. **Retired by D-7**: a meter uses `Radius.Pill`, which clamps to half its own 6px height and gives true round ends. The key stays until the last reference migrates |

**Shape consistency lock (D-6, D-7).** Four live radii and one lip:

- **12** - fittings: chips, badges, icon tiles, flag tiles, avatar squares, the segmented thumb.
- **16** - controls you press or type into: all five button variants, inputs, the search field, the
  price option, the segmented track.
- **20** - objects: cards, dialogs, flyout and sheet bodies, the toast surface.
- **24 top only** - the bottom-sheet lip.
- **Full round** - circles and tracks only, per the `radius_pill` row above.

The teachable line: **pill is for circles and tracks, never for a wide capsule with a label in it.**
A 52x342 stadium CTA is the shape the owner rejected; a 48x48 round icon button is not that shape
and is not affected. **Concentricity:** an inner radius is the outer radius minus the padding
between them - a 16 track with 4 padding holds a 12 thumb, a 20 card with 16 padding holds a 16
button. Never place a 20 radius inside a 16. Anything outside this list is a defect. Do not mix a
16dp card in among 20dp cards "because it looked better".

### 3.3 Size tokens

| Token (Android) | Token (Desktop) | Value | Meaning |
|---|---|---|---|
| `@dimen/tile_size` | `Size.Tile` | 40 | The leading icon tile on every settings/account row |
| `@dimen/tile_glyph` | `Size.Glyph` | 22 | The glyph inside that tile |
| `@dimen/row_min_height` | `Size.Row` | 56 | Minimum height of any list row |
| `@dimen/view_height_dp48` | (n/a) | 48 | Minimum touch target on Android |
| `@dimen/btn_height` | `Size.Btn` | 48 | **Default button height.** Everything in flow (R2) |
| `@dimen/btn_height_tall` | `Size.BtnTall` | 52 | The screen's one full-width primary CTA, the `.Tall` modifier (R2) |
| `@dimen/btn_min_width` | `Size.BtnMinWidth` | 96 | Minimum button width, so a two-word label never draws a stub |
| `@dimen/field_min_height` | `Size.Field` | 56 | Input field. Deliberately the same number as `row_min_height`, so a form and a list share one rhythm and Material's `OutlinedBox` 56dp minimum is not fought (R10) |
| `@dimen/toolbar_height` | `Size.SubToolbar` | 56 | Seamless sub-page toolbar (4.8). Replaces `?attr/actionBarSize` |
| `@dimen/meter_height` | `Size.Meter` | 6 | Determinate progress / traffic meter bar |
| (n/a) | `Size.IconButton` | 40 | Desktop icon button hit box |
| (n/a) | `Size.CtaTall` | 52 | The existing desktop CTA-height key. Superseded by `Size.BtnTall`; it stays until the last reference migrates |
| `@dimen/sub_card_height` | (n/a) | 152 | Subscription carousel card |
| `@dimen/dot_size` / `_active` / `dot_gap` | `Dot` / `Dot.Active` / `Dot.Gap` | 6 / 8 / 8 | Carousel page dots |

**Heights are minimums, never fixed** (R2). Every one of the control heights above is declared as
`android:layout_height="wrap_content"` plus `android:minHeight`, and as Avalonia `MinHeight` -
never `android:layout_height="52dp"`, never `Height="48"`. A fixed height clips a two-line label at
font scale 200% or 200% DPI, which is a P1 accessibility defect by 14.5.

**Every Departament button style sets `android:insetTop="0dp"` and `android:insetBottom="0dp"`**
(R2). `Widget.Material3.Button` carries 6dp insets top and bottom, which is why Android's declared
52dp CTAs draw at 40dp. This one line is what makes the shipped button match the layout that
declares it.

### 3.4 Type ramp

**Two faces, split by script** (D-1, D-2). **Golos Text** is the UI face and draws every Russian
string. **Space Grotesk** is the brand / figure face and draws digits, units, currency, Latin
technical tokens, chip labels and the wordmark, and nothing else. Golos Text ships as three **static
instances** at 400 / 500 / 700 (`res/font/golos_text_regular|medium|bold.ttf` behind
`@font/ui_sans`; `Assets/Fonts/GolosText-*.ttf` behind `Font.Ui`) - static rather than variable
because `fontVariationSettings` needs API 26 and minSdk is 24. Real weights only, on either face:
never a synthetic bold, never `android:textStyle="bold"`.

Face column below: **ui** = Golos Text, **brand** = Space Grotesk.

| Role | Android style | Desktop class | Face | Size | Weight | Line height | Colour | Use |
|---|---|---|---|---|---|---|---|---|
| Display | `TextAppearance.App.Display` | `TextBlock.Display` | brand | 34sp/34px | 700 | 40 (1.18) | onSurface | One hero **figure** per screen: balance, connected timer, days left. A Russian word never uses Display; a word-sized hero is Headline in the ui face |
| Headline | `TextAppearance.App.Headline` | `TextBlock.Headline` | ui | 24sp/24px | 700 | 28 (1.17) | onSurface | Screen title on a scroll-title screen, welcome, empty-state title |
| Title | `TextAppearance.App.Title` | `TextBlock.Title` | ui | 16sp/16px | 700 | 20 (1.25) | onSurface | Row titles, card titles, Primary and Destructive button labels |
| Title medium | `TextAppearance.App.Title.Medium` | `TextBlock.TitleMedium` | ui | 16sp/16px | 500 | 20 (1.25) | onSurface | A softer title inside a dense card; Secondary and Tertiary button labels |
| Body | `TextAppearance.App.Body` | `TextBlock.Body` | ui | 14sp/14px | 400 | 20 (1.43) | onSurface | Primary reading copy, dialog body |
| Subtitle | `TextAppearance.App.Subtitle` | `TextBlock.Subtitle` | ui | 13sp/13px | 400 | 18 (1.38) | onSurfaceVariant | Row subtitles, supporting line |
| Caption | `TextAppearance.App.Caption` | `TextBlock.Caption` | ui | 12sp/12px | 400 | 16 (1.33) | onSurfaceVariant | Metadata, timestamps, helper text |
| Chip | `TextAppearance.App.Chip` | `TextBlock.Chip` | brand | 11sp/11px | 500 | 14 (1.27) | contextual | Chip and badge labels only |
| Numeric | `TextAppearance.App.Numeric` | `TextBlock.Numeric` | brand | inherits | 500 | inherits | onSurface | Any live-updating number. The 500 is declared (D-4) |
| Section header | `@style/SettingsSectionLabel` | `TextBlock.SectionHeader` | ui | 16sp/16px | 700 | 20 (1.25) | onSurface | Group headers in settings and account |
| Toolbar brand | `@style/ToolbarBrandTitle` | `TextBlock.Wordmark` | brand | 20sp/20px | 700 | 24 (1.20) | onBackground | The wordmark only |

Ratio between adjacent steps is 1.15-1.4 which is correct for product UI (`typeset.md`: 1.125-1.2
typical, tighter than brand). **Do not add a step.** 15sp does not exist. 18sp does not exist.

**Line height is declared, not inherited from the platform** (D-12). The numbers in the column above
are absolute sp/px and they are part of the ramp style, not the layout: Android sets it in the
`TextAppearance` (`android:lineHeight`, with AppCompat's `app:lineHeight` as the pre-API-28 path),
desktop sets `LineHeight` on the ramp class. Before this decision neither platform declared leading
at all, so the same screen had different rhythm on Android and on Windows. A layout that sets its
own line spacing is a defect.

### 3.5 Colour roles (dark, the default theme)

Values from `res/values-night/colors.xml` and the `Dark` theme dictionary in
`Assets/GlobalResources.axaml`. They are already identical between platforms. Keep them identical.

| Role | Android attr | Desktop key | Dark value | Light value |
|---|---|---|---|---|
| Background | `?attr/colorBackground` | `Brush.Bg` | `#0A0B0D` | `#F4F7FC` |
| Surface | `?attr/colorSurface` | `Brush.Surface` | `#141619` | `#FFFFFF` |
| Surface high | `?attr/colorSurfaceContainerHigh` | `Brush.SurfaceHigh` | `#1A1D21` | `#EAEFF7` |
| Surface variant | `?attr/colorSurfaceVariant` | `Brush.SurfaceVariant` | `#1E2126` | `#E9EEF7` |
| Surface highest | `?attr/colorSurfaceContainerHighest` | `Brush.SurfaceHighest` | `#20242B` | `#E3EAF4` |
| On surface | `?attr/colorOnSurface` | `Brush.OnSurface` | `#F2F4F8` | `#111826` |
| On surface variant | `?attr/colorOnSurfaceVariant` | `Brush.OnSurfaceVariant` | `#9BA1AD` | `#54607A` |
| Accent | `?attr/colorPrimary` | `Brush.Accent` | `#4C8DFF` | `#1E5FC7` |
| On accent | `?attr/colorOnPrimary` | `Brush.OnAccent` | `#00183A` | `#FFFFFF` |
| Accent hover | (desktop only) | `Brush.AccentHover` | `#3D7EF0` | `#1A54B4` |
| Accent pressed | (desktop only) | `Brush.AccentPressed` | `#3877E0` | `#17499E` |
| Accent fill 12% (selection) | `@color/accent_fill_12` | `Brush.SelectedFill` | `#1F4C8DFF` | `#1F1E5FC7` |
| Accent container | `?attr/colorPrimaryContainer` | `Brush.AccentContainer` | `#17325C` | `#D8E4FF` |
| On accent container | `?attr/colorOnPrimaryContainer` | `Brush.OnAccentContainer` | `#CFE0FF` | `#14468F` |
| Success / connected | `?attr/colorTertiary` | `Brush.Green` | `#22C55E` | `#0B7D4A` |
| Success text on a chip (`color_success_text`) | `?attr/pingGood` | `Brush.Ping.Good` | `#22C55E` | `#065132` |
| Destructive / error | `?attr/colorError` | `Brush.Red` | `#F04452` | `#C42B32` |
| Destructive / error text (`color_destructive_text`) | `?attr/pingBad` (`@color/ping_bad`) | `Brush.RedText` | `#FF6069` | `#C42B32` |
| Warning / expiring (`color_warning`) | `?attr/warning` | `Brush.Amber` | `#EAB308` | `#8A6300` |
| Warning text on a chip (`color_warning_text`) | `?attr/warningText` | `Brush.AmberText` | `#EAB308` | `#6B5000` |
| Outline | `?attr/colorOutline` | `Brush.Outline` | `#2A2E36` | `#C3CCDC` |
| Outline variant (hairline) | `?attr/colorOutlineVariant` | `Brush.OutlineVariant` | `#20242B` | `#DCE3EF` |
| **Control outline** (`color_outline_control`) | `?attr/colorOutlineControl` | `Brush.OutlineControl` | `#646C7C` | `#7D8BA3` |
| Scrim | `?attr/colorScrim` @ 60% | `Brush.Scrim` | `#000000` 0.6 | `#000000` 0.6 |

**Verified contrast (dark theme, computed, WCAG 2.1):**

| Pair | Ratio | Verdict |
|---|---|---|
| onSurface `#F2F4F8` on background `#0A0B0D` | 17.88:1 | AAA |
| onSurface `#F2F4F8` on surface `#141619` | 16.46:1 | AAA |
| onSurface `#F2F4F8` on surfaceHighest `#20242B` | 14.14:1 | AAA |
| onSurfaceVariant `#9BA1AD` on surface `#141619` | 6.99:1 | AA body, close to AAA |
| onSurfaceVariant `#9BA1AD` on surfaceHighest `#20242B` | 6.00:1 | AA body |
| accent `#4C8DFF` on surface `#141619` | 5.66:1 | AA body |
| onAccent `#00183A` on accent `#4C8DFF` | 5.51:1 | AA body (button label) |
| green `#22C55E` on surface `#141619` | 7.95:1 | AAA |
| red `#F04452` on surface `#141619` | 4.88:1 | AA body, **only just** |
| redText `#FF6069` on surface `#141619` | 6.15:1 | AA body, use this for error *text* |
| onAccentContainer `#CFE0FF` on accentContainer `#17325C` | 9.57:1 | AAA |
| warning `#EAB308` on background `#0A0B0D` | 10.27:1 | AAA |
| **outlineControl `#646C7C` on background `#0A0B0D`** | 3.73:1 | Clears the 3:1 control-boundary floor (D-9) |
| **outlineControl `#646C7C` on surface `#141619`** | 3.43:1 | Clears it |
| outline `#2A2E36` on background `#0A0B0D` | 1.45:1 | **Fails** 1.4.11. This is why D-9 exists: `colorOutline` may not draw a control boundary |
| onSurface on the 12% accent fill over ground (`#121B2A`) | 15.68:1 | AAA |

**Light theme is verified too** (onSurface 17.76:1, onSurfaceVariant 6.30:1 on white and 5.87:1 on
background, accent 5.97:1, onAccentContainer 7.15:1, green 5.19:1, red 5.62:1, warning 5.43:1,
outlineControl `#7D8BA3` 3.45:1 on surface and 3.21:1 on background). Both themes ship. Neither is an
afterthought. Mono theme inherits the same structure through `ThemeOverlay.Mono`, where
`color_outline_control` is `#6A6A6E` dark (3.90:1) and `#767679` light (4.53:1).

**Light-theme status text is a separate token (D-10).** On light, a status chip's text is **not** the
status colour: the hue on its own 18% fill measures **4.05:1** green, **4.22:1** red and **4.82:1**
amber and fails AA. Use `color_success_text` `#065132` (7.34:1 on the green chip fill),
`color_destructive_text` `#C42B32` and `color_warning_text` `#6B5000` (6.72:1 on the amber chip
fill). On dark the chip fill is dark enough that the status colour itself clears AA (green 6.60,
red text 5.65, amber 7.51), which is why the dark and light columns of those three tokens differ.

**Rule:** `#F04452` is fine as a fill or an icon. For error *text* on a dark surface use
`@color/ping_bad` / `Brush.RedText`. Never introduce a new red.

**Rule (R11): the accent keys live inside the theme dictionaries.** `Brush.Accent`, `Brush.OnAccent`,
every `Brush.Tile.*`, `Brush.SelectedFill` and every `Brush.StatusChip.*` are declared **outside**
`ResourceDictionary.ThemeDictionaries` in `Assets/GlobalResources.axaml` today (lines 39-51,
226-258), so the light theme keeps `#4C8DFF` and measures **2.98:1** on `#F4F7FC` - below even the
3:1 UI floor. That single mistake draws every light-theme focus ring, every checked segment label and
17 `LinkAction` buttons at that ratio. Light accent is `#1E5FC7`. Moving those keys inside the `Dark`
and `Light` dictionaries is **the only P1 accessibility defect in the whole token system** and it is
fixed before any component work.

### 3.6 Accent budget

The accent is the scarcest resource in the product. Per screen, at most:

- **one** filled accent surface (the primary CTA, or the connect control, never both at full
  strength on the same screen),
- accent used additionally only for: the current navigation destination, the selected item's
  state, a focus ring, a link, and a live progress indicator.

Everything else is neutral. Settings rows use the **neutral** icon tile
(`@color/icon_tile_neutral` `#20242B` with `@color/icon_glyph_neutral` `#9BA1AD`, desktop
`Brush.Tile.Neutral`) unless the row is genuinely a coloured category. Coloured tiles are not
decoration; they are a category system, and a screen where every row has a different coloured tile
has no category system, only noise.

**The coloured tile system is exactly three (D-5):**

| Tile | Fill | Glyph | When |
|---|---|---|---|
| Accent | `?attr/iconTileBgBlue` / `Brush.Tile.Blue` (accent @20%) | accent | The one lit row on a screen, if any |
| Destructive | `?attr/iconTileBgRed` / `Brush.Tile.Red` (red @20%) | `colorError` | Delete, unlink, reset |
| Neutral | `@color/icon_tile_neutral` / `Brush.Tile.Neutral` | `@color/icon_glyph_neutral` | Everything else, which is most rows |

Three, and at most three coloured tiles visible on one screen. The purple, orange, yellow and green
tile colours are **not used by new work**; their resources stay in the colour files until the last
screen that references them migrates. Live reference count, measured 2026-07-26: Android
`icon_purple` 12, `icon_orange` 10, `icon_green` 15, `icon_yellow` 4, the four `icon_tile_*` colours
1 each, the four `bg_icon_*` drawables 11 / 8 / 12 / 2, the four `iconTileBg*` theme attrs 4 each;
desktop `Brush.Tile.Purple` 3, `.Orange` 3, `.Green` 4, `.Yellow` 3, `Brush.Icon.Orange` 4,
`Brush.Icon.Yellow` 3. Reaching for a fourth tile colour means the screen is trying to encode a
category system that does not exist.

### 3.7 Motion tokens

Android `res/values/motion.xml`; desktop the literal table in `Assets/GlobalResources.axaml`
mirrored in `Common/Motion.cs` as `Motion.Dur.*` / `Motion.Ease.*`. They are already 1:1. Keep
them 1:1.

| Token | ms | Curve | Use |
|---|---|---|---|
| `motion_press_in` / `Dur.PressIn` | 90 | `ease_out_quart` / `Ease.OutQuart` (0.25,1,0.5,1) | Finger or pointer down |
| `motion_press_out` / `Dur.PressOut` | 160 | `ease_out_quint` / `Ease.OutQuint` (0.22,1,0.36,1) | Release, settle back to rest |
| `motion_state` / `Dur.State` | 220 | `ease_standard` / `Ease.Standard` (0.2,0,0,1) | Selection, enable/disable, tint crossfade |
| `motion_reveal` / `Dur.Reveal` | 300 | `Ease.OutQuint` | Show/hide, expand, sheet and page entrance |
| (use 225 reverse) / `Dur.Exit` | 150 | `Ease.Standard` | Screen or sub-page exit |
| (n/a) / `Dur.Shell` | 200 | `Ease.Standard` | Desktop shell overlay crossfade |
| (n/a) / `Dur.Slow` | 450 | `Ease.OutExpo` (0.16,1,0.3,1) | The single auth -> home hand-off |
| `motion_stagger` / `Dur.Stagger` | 40 | n/a | Per-item list delay, cap total at 400ms |
| `motion_emphasis` / `Dur.Emphasis` | 600 | `Ease.OutQuint` | The single hero moment: connect sonar |
| `motion_pulse` / `Dur.Pulse` | 1000 | `ease_standard` / `Ease.Standard` | Skeleton pulse, opacity 0.45 to 1.0 each way, infinite reverse |
| `motion_spin` / `Dur.Spin` | 1100 | linear | One revolution of the 20dp inline spinner arc |
| `input_debounce` / `Dur.Debounce` | 500 | n/a | Re-entry guard on a tap that is not command-gated |
| (n/a) / `Dur.Instant` | 0 | n/a | Reduced-motion fallback: snap to end state |

**Exit is 75% of enter.** State reverse = 165ms, reveal reverse = 225ms.

**Press scale is 0.97, everywhere (D-11)**, in over `motion_press_in` 90 and out over
`motion_press_out` 160. One gesture, one number, both platforms. Today `res/anim/press_scale.xml`
uses 0.96, `res/anim/nav_press.xml` uses 0.92 at hard-coded 100ms with no interpolator (so: linear,
which 8.3 bans), and desktop uses 0.97 at 120ms in both directions. All four are defects against
this row, not variants.

**Why `motion_pulse`, `motion_spin` and `input_debounce` are not violations of section 8.** Section
8.1 permits motion that conveys state and 8.4 reserves 600ms for the single hero moment.

- `motion_pulse` is **loading feedback**, which is a state, and at 1000ms it is slower than the hero
  moment rather than competing with it. It replaces the off-token 900ms `AccelerateDecelerate` pulse
  that exists in no scale, and it is the only looping opacity animation in the product. Reduced
  motion holds the skeleton static at opacity 0.7.
- `motion_spin` is linear because it is a **continuous rotation, not a state transition**. The
  ease-out law in 8.3 governs transitions between two states; a spinner has no end state to settle
  into, and an eased revolution visibly stutters once per turn. This is the one linear exemption in
  the product and it applies to nothing else.
- `input_debounce` is not an animation at all. It is a 500ms re-entry window that makes a
  double-press impossible by construction (R9), and it is in this table because it is a duration and
  durations live here.

---

## 4. Layout law

### 4.1 The gutter

One horizontal screen gutter: **16dp/px** (`@dimen/screen_gutter`, `Gutter`). It applies to the
screen's scroll content. Cards sit at the gutter, and their own inner padding is another 16. A
list row's text starts at gutter + 40 (tile) + 12 = 68 from the screen edge; every row on the
screen shares that text origin. Dividers between rows start at the text origin, not at the screen
edge, and never extend under the tile.

At `sw600dp` and at desktop window width >= 1000px the gutter becomes 24 and content is capped at
a max width of **720dp/px** and centred. Do not stretch a phone layout to fill a 1920px window
(`adapt.native.md`: "Restructure, don't stretch").

### 4.2 Grouping and rhythm

- Elements that belong together: 4 or 8 apart.
- Rows in the same group: 0 apart, separated by a 1dp `?attr/colorOutlineVariant` hairline, or
  8 apart with no hairline. Pick one per screen and hold it.
- Groups: 24 apart, with a section header (16sp/700, sentence case) at 24 above and 8 below.
- Above the first section after a hero: 32. Below the last section before a bottom CTA bar: 32.

### 4.3 Hierarchy

The squint test from `reference/layout.md` is the acceptance test. Blur your eyes: primary,
secondary and the groupings must still be identifiable. Build hierarchy from **2 to 3** of:
size, weight, colour, position, space. Not from colour alone.

Per screen: exactly **one** primary action, visually dominant. Everything else is tonal, text, or
a row. Two filled accent buttons on one screen is a defect.

### 4.4 Cards: when you may use one

From `SKILL.md`: "Cards are the lazy answer. Use them only when they're truly the best affordance.
Nested cards are always wrong."

A card is allowed only when **all three** hold:
1. The content is a distinct object the user can act on as a unit (a subscription, a server, a
   payment), and
2. it needs a boundary that spacing alone cannot give (because a neighbouring block would
   otherwise read as part of it), and
3. it is not inside another card.

Otherwise: rows with hairlines, or plain blocks with spacing. A settings screen is rows, not
cards. An account screen is one card (the subscription) plus rows.

Card spec: `Brush.Surface` / `?attr/colorSurface` fill, `Radius.Card` 20, 1dp
`colorOutlineVariant` hairline border, 16 padding, **elevation 0 and no shadow**. Depth in dark
mode comes from the surface ramp, never from shadow (`colorize.md`: "In dark mode, depth comes
from surface lightness, not shadow").

### 4.5 Rows

The universal row, used on both platforms:

```
[ 16 gutter ][ 40dp tile, radius 12, 22dp glyph ][ 12 ][ text column, weight 1 ][ 12 ][ trailing ][ 16 gutter ]
                                                        Title  (16sp/700, onSurface, max 2 lines)
                                                        Subtitle (13sp/400, onSurfaceVariant, max 2 lines)
```
- Min height 56 (`row_min_height` / `Size.Row`); with a two-line subtitle the row grows, it does
  not clip.
- Trailing is exactly one of: chevron (22dp, `colorOnSurfaceVariant`), switch, value text
  (13sp onSurfaceVariant), or a 40dp icon button. Never two.
- The whole row is the touch/click target, not just the trailing control (except when the trailing
  control is a switch, where both the row and the switch toggle it).

### 4.6 Lists

- Any list that can exceed ~20 items is virtualised (`RecyclerView`, `ItemsRepeater` with
  virtualisation). Non-virtualised long lists are a P1 performance defect
  (`audit.native.md` dimension 2).
- Section headers inside lists are sticky only when the list is long enough to lose context
  (servers grouped by subscription: yes; settings groups: no).
- Search filters in place, it does not navigate. The empty result state is a designed state
  (section 15), not a blank list.

### 4.7 Elevation and depth

Dark theme: elevation is expressed by the surface ramp only.

| Layer | Dark surface | Elevation dp | Shadow |
|---|---|---|---|
| Screen background | `#0A0B0D` | 0 | none |
| Card / sheet body / toolbar-on-scroll | `#141619` | 0 | none |
| Raised control, hovered row (desktop) | `#1A1D21` | 0 | none |
| Chip fill, selected row, input field | `#20242B` | 0 | none |
| Dialog / bottom sheet | `#141619` over 60% black scrim | 0 | none |

Light theme may use Material's own tonal elevation; it must not use a coloured or offset shadow.
`android:elevation` above 0 is permitted only where Material requires it for the FAB and the
bottom sheet, and its value is the Material default, unmodified.

### 4.8 The seamless sub-screen toolbar (owner request 0.4.6)

A sub-page (anything reached from a tab) has a toolbar that:
- uses the same background as the page (`?attr/colorBackground` / `Brush.Bg`),
- has no elevation, no divider, no shadow, no separate scrim,
- carries a 24dp back affordance at the gutter, then 16, then the title at
  `TextAppearance.App.Title` (16sp/700),
- is 56 tall,
- keeps at most one trailing action; more go in an overflow.

On scroll it does **not** change colour or gain a line. If a boundary is genuinely needed at
scroll, it is a 1dp `colorOutlineVariant` hairline that fades in over `motion_state` 220ms, and
that is the only permitted variant.

---

## 5. Typography law

1. **Two faces, no more, and they are split by script** (D-1, D-2).
   - **Golos Text** is the **UI face** and draws every Russian string: Headline, Title, Title
     medium, Body, Subtitle, Caption, Section header, and every button label.
     `res/font/golos_text_regular.ttf` / `_medium.ttf` / `_bold.ttf` (400 / 500 / 700, static
     instances) behind the family XML `@font/ui_sans`, and `Assets/Fonts/GolosText-Regular|Medium|Bold.ttf`
     behind `Font.Ui`. Verified coverage: all 66 Russian letters plus Ё/ё, `₽`, `…`, «».
   - **Space Grotesk** is the **brand / figure face** and draws Display figures, Chip labels, the
     Numeric role, the wordmark, units, currency and Latin technical tokens (`VLESS`, `Reality`,
     `WS`, `TCP`, host names, ports). `res/font/space_grotesk.xml`,
     `Assets/Fonts/SpaceGrotesk.ttf`.
   - This is a deliberate contrast pairing (geometric-mono figures against a humanist Russian
     face), not two similar sans faces, which `typeset.md` forbids. The two never occupy the same
     role and never appear on the same line inside a sentence.
   - **A Russian string never gets Space Grotesk.** The measured reason: the vendored binary maps
     735 codepoints and **zero** in U+0400-U+04FF, so every `fontFamily="@font/space_grotesk"` on
     a Russian string has always been a no-op that handed the choice to Roboto on Android and to
     whatever the OS picked on desktop - the same screen set in three faces on three operating
     systems. Enforcement, mechanical: the face is a property of the **ramp style**, so no layout
     and no view sets a family at all. `grep -rn 'android:fontFamily' res/layout/` returns
     nothing; `grep -rn 'FontFamily=' Views/` returns nothing; and the three blanket setters at
     `Assets/GlobalStyles.axaml:257-265` (`TopLevel`, `TextBlock`, `TemplatedControl`), which
     currently apply the Cyrillic-free brand face to every string in the desktop app, carry the UI
     face instead. A Russian string found in the brand face is a P1 defect, not a polish item.
2. **Roles, not sizes.** Text is styled by applying a ramp style from 3.4. A layout that sets
   `android:textSize` directly, or a `TextBlock` that sets `FontSize` directly, is a defect.
3. **sp on Android, never dp, for text.** Layouts must survive font scale 200%. Any layout that
   clips at 200% is a P1 accessibility defect.
4. **Real weights only.** 400 / 500 / 700, on both faces, from real masters. No 600. No italic. No
   synthetic bold: `android:textStyle="bold"` on a ramp style is a defect. `TextAppearance.App.Numeric`
   declares `android:textFontWeight="500"` (D-4); it declared no weight at all before.
5. **Numbers.** Every live-updating number (traffic, speed, ping, balance, price, uptime, device
   count) uses the Numeric role: Space Grotesk with `tnum` and `lnum` on, plus `zero` (slashed
   zero) **on for technical figures and off for currency, identically on both platforms** (D-3) -
   a slashed zero in a price reads as a symbol. This stops digit jitter. Prices are `1 290 ₽`
   (non-breaking space before ₽, thin space as thousands separator). One exception: a figure inside
   a running Russian sentence («Осталось 3 дня») is set in the UI face like the rest of the
   sentence. A sentence never ripples between two faces.
6. **Letter-spacing** is what the ramp says and nothing else: Display -0.02em, Headline -0.01em,
   Title 0, Body/Subtitle +0.01em, Caption +0.02em, Chip +0.04em. No per-screen tuning.
7. **Line height is a declared number from the 3.4 ramp, not a ratio and not a platform default**
   (D-12): 40 / 28 / 20 / 20 / 20 / 18 / 16 / 14 / 20 / 24 for Display / Headline / Title / Title
   medium / Body / Subtitle / Caption / Chip / Section header / Toolbar brand, with Numeric
   inheriting its host. The ratios that fall out are 1.18 / 1.17 / 1.25 / 1.25 / 1.43 / 1.38 /
   1.33 / 1.27, which supersedes the old "titles 1.2, body 1.45, caption 1.35" wording; that
   wording was never declared anywhere in either codebase, which is precisely the defect D-12
   fixes. Light-on-dark needs the compensation `typeset.md` describes, and the ramp carries it in
   the tracking values; do not add more.
8. **Measure.** Any paragraph longer than one line is capped at roughly 60 characters on phone
   and 65-70 on desktop. On desktop that means a `MaxWidth` on the `TextBlock`, not on the panel.
9. **Truncation is a last resort.** Prefer 2 lines and wrap. If a primary label truncates, the
   copy is too long: rewrite it. Server remarks and Telegram usernames are user content and may
   ellipsise at the end, never in the middle.
10. **Section headers are sentence-case bold**, per 0.4 and the eyebrow ban. `SettingsSectionLabel`
    / `TextBlock.SectionHeader`. Never `textAllCaps`, never letter-spacing above 0.02em at a
    heading size.

---

## 6. Colour law

1. **Strategy: Restrained** (`SKILL.md` colour strategies). Tinted near-black neutrals plus one
   accent at <= 10% of coloured surface. This is the product floor and we do not exceed it.
2. **Meaning is fixed and global.** Blue = primary action, current selection, focus, link,
   progress. Green = connected, active, paid. Red = destructive action, error, disconnected-with-
   fault. Yellow/orange = expiring soon, warning. Neutral grey = everything else. A colour never
   means two things.
3. **Never colour alone.** Every state carried by colour also carries a glyph or a word
   ("Подключено" with a green dot AND the word; a failed payment with a red chip AND the label
   "Не оплачен").
4. **Inactive states are never saturated.** An unselected tab, a disabled button, an idle connect
   control: neutral only. `product.md` bans "heavy color or full-saturation accents on inactive
   states".
5. **No gradients.** Not on backgrounds, not on buttons, not on the connect control, not behind
   the hero. The only permitted non-solid fills in the codebase are the pre-existing
   `Brush.Ring.Outer` / `Brush.Ring.Inner` alpha values used by the connect sonar, which are
   flat colours at alpha, not gradients.
6. **No shadows as glow.** See 4.7.
7. **Grey text never sits on a coloured background.** On the accent container use
   `onAccentContainer`. On a status chip in the **light** theme use the dedicated darkened text
   token - `color_success_text` `#065132`, `color_destructive_text` `#C42B32`, `color_warning_text`
   `#6B5000` (D-10) - because the status hue on its own fill measures 4.05 / 4.22 / 4.82:1 and
   fails AA. On dark the status colour itself clears AA on the dark fill and no second token is
   needed. Never `onSurfaceVariant` on a tinted fill.
8. **Contrast floors:** body text 4.5:1, large text (>=18sp or >=14sp bold) 3:1, icons and UI
   component boundaries 3:1, placeholder text 4.5:1 (not the muted grey default). Verify with the
   ratios in 3.5 before inventing a new pair. If you introduce a colour, compute the ratio and put
   it in the token comment.
   **The boundary of a control is drawn with `color_outline_control`** (D-9): 3.43:1 on the dark
   surface, 3.45:1 on light. `colorOutline` measures 1.45:1 on the dark ground and may never draw
   an input, an outlined button or a segmented track. The 1dp `colorOutlineVariant` hairline keeps
   its role on row separators and card borders at 1.16:1: that is structural decoration, not a
   control boundary, and WCAG 1.4.11 does not apply to it. Knowing which of the two a given line is
   is the single most-missed accessibility point in the current build.
9. **Three themes ship**: dark (default), light, mono. Every new component is checked in all
   three before it is done. Mono is not a colour-strip: it remaps the accent to ink and must keep
   the same hierarchy.
10. **Dynamic Color (Material You)** is **off** for this product. The brand's single blue is the
    point; a wallpaper-derived scheme would break it. This is a deliberate documented deviation
    from `android.md`'s "Dynamic Color where it fits" and it stays.

---

## 7. Interaction and state law

### 7.1 The eight states

From `reference/interaction-design.md`. Every interactive element implements all that apply. Half
a set is not a component.

| State | Android | Desktop |
|---|---|---|
| Default | Ramp style + theme attrs | Class style + `DynamicResource` |
| Hover | Does not exist. Do not design for it | `:pointerover` -> `Brush.Hover` overlay: **white 6% on dark, black 6% on light** (D-8), or one surface step up to `color_surface_raised` `#1A1D21` per 4.7. 150ms `Ease.Standard`. The overlay covers the whole row or control, never just the label |
| Focus | **Mandatory on every focusable control** (R7). `android:focusable` + a 2dp ring: filled controls (Primary, Destructive, filled icon buttons) draw it **inside**, in the control's own on-colour at 40%, at the control's own radius; everything else draws it **outside**, 2dp `colorPrimary` at 2dp offset, radius = control radius + 2 | **Mandatory.** `:focus-visible` -> the same two-case ring, 2px wide (`Brush.OnAccent` at 40% inside a filled control, `Brush.Accent` outside everything else), 2px offset, never removed. The `FocusAdorner` is **not** suppressed under `.lite`: accessibility outranks motion reduction |
| Pressed | `android:stateListAnimator="@anim/press_scale"` -> **scale 0.97** (D-11), 90ms in `ease_out_quart`, 160ms out `ease_out_quint`; plus `?attr/selectableItemBackground` ripple, except bottom nav (0.4.8). **Rows do not scale, objects do** (R5): a row inside a card or group steps its background to `colorSurfaceContainerHigh` instead, because scaling a slice of a surface tears the hairlines above and below it | `:pressed` -> `scale(0.97)`, `Dur.PressIn` 90 / `Dur.PressOut` 160, asymmetric (the transition lives on the `:pressed` selector for the in, on the base selector for the out). Same row-versus-object rule |
| Selected | Accent text/icon + weight 700 + a 2-3dp accent indicator or an 12% accent fill (`Brush.SelectedFill`). Two axes minimum, never tint alone | Same, `.active` class |
| Disabled | **0.38 on the whole control** (R6), not on the label alone: a `ColorStateList` carrying `android:alpha="0.38"` on the `state_enabled="false"` item for `backgroundTint`, `textColor`, `iconTint` and `strokeColor`, because a MaterialButton style cannot set a state-dependent `android:alpha`. No ripple, `isEnabled=false` | `:disabled` -> `Opacity` 0.38 on the control, no pointer cursor, `IsEnabled=False` |
| Loading | **Holds the width, hides the label, spins a 20dp arc, and is not the disabled look** (R8): the control keeps its exact size, the label goes to alpha 0 (never to `wrap_content` of nothing), a 20dp indeterminate arc in `onAccent` rotates at `motion_spin` 1100 linear, the control is not tappable but is not drawn at 0.38. Screen-level: skeleton at `motion_pulse` 1000, never a centred spinner | Same, `Ellipse.Spinner`; `Size.SkeletonCard` skeletons already exist |
| Error | Inline under the field, `Brush.RedText`, 12sp, plus a red 1dp border on the field | Same |
| Success | 220ms tint to green plus the word. No confetti, no checkmark flourish | Same |

**State priority**, highest first: **disabled > loading > pressed > focus > selected > hover >
default.** A disabled control shows no hover and no focus ring.

**Why the Android focus rule changed (R7).** This row used to read "only for hardware keyboard and
TV", which sounded like a scope limit and worked as an excuse: Android ships exactly **one**
`state_focused` drawable in the whole app (`res/drawable/bg_server_row.xml`) - including on the two
D-pad-only TV activities, so the one platform the old wording carved out is the one it failed.
External keyboards, tablet keyboard cases, TV D-pads and switch access all move focus on Android, and
14.4 already requires that focus is never lost. A control that can take focus and does not draw it is
unreachable in practice. So: focus is drawn on **every** focusable control, on both platforms, with
the two-case ring above.

### 7.2 Touch and pointer targets

- Android: **48x48dp minimum**, 8dp minimum between adjacent targets. If the visual is smaller
  (a 24dp icon), expand the hit box with padding or `TouchDelegate`.
- Desktop: **32x32px minimum** for pointer-only controls, **40px** (`Size.IconButton`) for
  anything in a toolbar or row, **52px** (`Size.CtaTall`) for a primary CTA.
- Nothing important lives within 8dp of a screen edge or under a system bar.

### 7.3 Feedback timing

Visual acknowledgement of a tap or click within **100ms**. If the operation takes longer than
300ms, show a loading state. If it can exceed 3s, show progress with a cancel path. Never leave a
pressed control with no response.

### 7.4 Forms

- The field is `field_min_height` 56 tall (as a **minimum**, per 3.3), radius `radius_button` 16
  (D-7), with a 1dp `color_outline_control` border (D-9) and a `color_surface_inset` fill.
- Label **above** the input, always visible. Placeholder is never the label.
- Helper text below, present in the markup even when empty so the layout does not jump.
- Validate on **blur**, not per keystroke. Exception: password strength.
- Error text **below** the field, in `Brush.RedText`, with the field border in red. Error text
  states the cause and the fix (section 9.4).
- After a failed submit, focus moves to the first invalid field.
- Correct keyboard type per field on Android (`inputType="textEmailAddress"`, `numberPassword`,
  etc.) and autofill hints on both platforms.
- Password fields have a show/hide toggle.
- The submit button is disabled while the request is in flight and shows the loading state.

### 7.5 Destructive actions

`interaction-design.md`: "Undo is better than confirmation dialogs." Default to remove-immediately
plus an undo snackbar (Android) or an undo toast (desktop, `Border.Toast` already exists), 5
seconds. Confirm with a dialog **only** when the action is genuinely irreversible and costly:
deleting an account, wiping all subscriptions, resetting the whole config. Destructive confirms
use a red text button on the right, a neutral cancel on the left, and the button says what it
does ("Удалить подписку"), never "OK".

### 7.6 Modals, sheets, dialogs

Order of preference, always: **inline > expandable row > bottom sheet (Android) / flyout (desktop)
> dialog**. A dialog is the last resort, per `product.md`.

- Android: per-item actions use a Material bottom sheet (`ServerActionsSheet.kt`,
  `PaymentMethodSheet.kt` are the reference implementations). Radius `radius_sheet` 24 top, drag
  handle 36x4, scrim 60%.
- Desktop: per-item actions use a `Flyout` or a `MenuFlyout` anchored to the row. A modal window is
  used only for genuinely separate tasks (server editor, subscription editor).
- Every sheet and dialog: Esc / system Back closes it, the trigger keeps its position, focus moves
  into it, focus returns to the trigger on close.

### 7.7 Navigation

- Android: bottom navigation, **3-5 destinations**, icon + Russian label always visible, current
  destination marked by colour AND weight 700, no ripple glow (0.4.8), `BottomNavIndicator`
  64x34 with `colorPrimaryContainer`. At `sw600dp` it becomes a navigation rail.
- Desktop: a left navigation rail with the same destinations in the same order and the same
  labels; the indicator moves with `Motion.Dur.State` 220 `Ease.OutQuint`.
- System Back / predictive Back always works and never traps the user. Back restores scroll
  position, filter state and input.
- Deep links open the destination screen directly, not the home tab.
- Sub-pages do not appear inside the bottom nav or the rail. Nothing nests inside a tab bar.

---

## 8. Motion law

1. **Motion conveys state.** Feedback, state change, reveal, loading, navigational continuity.
   Nothing else. Decorative motion is a defect (`product.md`).
2. **The scale in 3.7 is the whole vocabulary.** No other durations. No other curves.
3. **Ease-out only.** `ease_out_quart` for press, `ease_out_quint` for reveal and settle,
   `ease_standard` for two-way tint and crossfade, `ease_out_expo` reserved for the one auth ->
   home hand-off. **No bounce, no elastic, no spring overshoot, no linear** on UI transitions. The
   single exemption is `motion_spin`, the continuous rotation of the indeterminate spinner arc,
   which is not a transition between two states and stutters once per revolution if it is eased
   (3.7). It applies to that one drawable and to nothing else.
4. **One hero moment in the whole product**: the connect confirmation (Android
   `res/anim/connect_confirm.xml` + `shield_assemble.xml`, desktop `ConnectHeroView`), at
   `motion_emphasis` 600ms. Nothing else gets 600ms. Chrome never gets it.
5. **Exit is 75% of enter.**
6. **Stagger** is 40ms per item, capped so total stagger never exceeds 400ms (so: at most 10
   staggered items, then the rest appear together). Stagger is for a list of siblings appearing,
   never for a whole screen's sections.
7. **Never animate layout properties casually.** No animating width, height, top, left, margins
   for effect. Transform, alpha, colour, and clip only. Desktop: the two `DoubleTransition
   Property="Width"/"Height"` cases already in `GlobalStyles.axaml` are grandfathered for the rail;
   no new ones.
8. **Reduced motion is a contract, not a nicety.**
   - Android: `MotionUtils.animationsEnabled(context)` / `View.reducedMotion()` in
     `util/MotionUtils.kt`. When false, jump to the end state. Every imperative animator checks it.
     Declarative `stateListAnimator` collapses automatically at scale 0.
   - Desktop: `MotionState.IsLite` in `Common/MotionState.cs`, broadcast live from
     `SettingsViewModel`. Subscribers re-apply on the spot. `Motion.Dur.Instant` is the fallback.
   - A new animation that does not honour one of these is a P1 accessibility defect.
9. **No page-load choreography.** A screen appears; it does not perform. The only entrance motion
   allowed on a screen is: the sub-page slide/fade at `Dur.Reveal` 300, and a single list stagger
   for freshly loaded content.
10. **Haptics** (Android only): `View.pressHaptic()` on primary confirmations (connect, purchase,
    delete confirm), `View.tickHaptic()` for stepping and incremental selection. Nothing else
    vibrates.

---

## 9. Copy law (Russian)

### 9.1 Voice

Direct, calm, technical without jargon. The interface speaks to a competent adult who wants to be
connected and left alone. Active verbs. No exclamation marks. No marketing. No apologies. No
personality where a label is needed.

### 9.2 Form

- **Sentence case everywhere.** "Локальный прокси", not "Локальный Прокси", not "ЛОКАЛЬНЫЙ ПРОКСИ".
- Buttons are verbs: "Подключить", "Купить", "Привязать Telegram", "Сохранить", "Удалить".
  Never "OK", never "Да"/"Нет" as the primary pair.
- Row titles are nouns. Row subtitles say what the row does, in <= 6 words, no final period.
- Toggles say what is on when they are on: "Автообновление подписки", not "Не отключать
  автообновление".
- No final period on labels, row titles, subtitles, chips, or buttons. Full stops only in
  sentences of body copy and error messages.
- Quotes are «ёлочки». Ellipsis is the single character `…`, not three dots.
- **No em-dash and no en-dash.** Hyphen only. Where a dash was carrying a pause, use a comma, a
  colon, a full stop, or a line break.
- Numbers: thin space as a thousands separator, `₽` after a non-breaking space, sizes as `12,4 ГБ`
  (comma decimal), speeds as `24,8 Мбит/с`, latency as `48 мс`.

### 9.3 Terminology lock

One noun per concept, across both platforms, everywhere. Changing one of these is a product
decision, not a copy edit.

| Concept | Use | Never |
|---|---|---|
| The paid plan | **тариф** | план, пакет, подписочный план |
| The user's active service | **подписка** | абонемент, аккаунт-подписка |
| One VPN endpoint | **сервер** | нода, узел, конфиг, профиль |
| A subscription URL that yields servers | **провайдер** | источник, подписка (ambiguous), remote |
| The tunnel being up | **подключение** / **подключено** | соединение, коннект, VPN активен |
| A device on the account | **устройство** | девайс, HWID |
| Money in the account | **баланс** | счёт, кошелёк |
| The account screen | **Аккаунт** | Профиль, Личный кабинет |
| Buying | **Купить** | Оформить, Приобрести, Оплатить (that is for an existing invoice) |
| Linking Telegram | **Привязать Telegram** | Подключить Telegram, Войти через Telegram (that is the auth action) |
| Signing in | **Войти** | Авторизоваться, Логин |

### 9.4 Error messages

Formula: **what happened + why + what to do**, one or two short sentences, no error codes visible
to the user, no blame.

| Situation | String |
|---|---|
| No network | `Нет подключения к интернету. Проверьте сеть и повторите.` |
| Server unreachable | `Сервер не отвечает. Выберите другой сервер или повторите позже.` |
| Subscription update failed | `Не удалось обновить подписку. Проверьте ссылку провайдера и повторите.` |
| Wrong credentials | `Неверная почта или пароль.` |
| Payment declined | `Платёж не прошёл. Попробуйте другой способ оплаты.` |
| Subscription expired | `Подписка истекла. Продлите её, чтобы подключаться.` |
| Device limit reached | `Достигнут лимит устройств. Отвяжите одно из устройств в разделе «Устройства».` |
| Unknown failure | `Что-то пошло не так. Повторите попытку.` (last resort only; log the real cause) |

Every error message ships with a recovery affordance: a "Повторить" action on the snackbar, or a
retry button in the error state.

### 9.5 Empty states

Formula: title (what is not here), one line (why or what it gives you), one action. Never "Нет
данных" alone.

| Screen | Title | Line | Action |
|---|---|---|---|
| No servers | `Нет серверов` | `Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.` | `Добавить провайдера` |
| Search found nothing | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| No subscription | `Подписки пока нет` | `Купите тариф, чтобы подключаться к серверам Departament.` | `Купить` |
| No payments | `Платежей пока нет` | `Здесь появится история покупок и продлений.` | none |
| No devices | `Устройств пока нет` | `Устройства появятся после первого подключения.` | none |
| Telegram not linked | `Telegram не привязан` | `Привяжите Telegram, чтобы управлять подпиской из бота.` | `Привязать Telegram` |

### 9.6 Offline

Offline is a designed state, not an error toast. The screen keeps its last known data, marks it as
stale ("Данные могли устареть"), disables the actions that need the network, and shows one
persistent, quiet bar: `Нет сети. Показаны последние данные.` with a `Повторить` action.

### 9.7 Enforcement

```bash
# dashes in shipped copy. Use this literal form: the PCRE \x{2014} form fails in this environment.
cd /home/user/dp/V2rayNG/app/src/main/res  && grep -rn -e '—' -e '–' values*/strings*.xml
cd /home/user/v2rayN/v2rayN/v2rayN.Desktop && grep -rn -e '—' -e '–' Common/L.*.cs
# three dots where the single ellipsis character belongs
cd /home/user/dp/V2rayNG/app/src/main/res  && grep -rn '\.\.\.' values*/strings*.xml
```

**Known debt, baseline scan 2026-07-26: 22 hits on Android, 44 on desktop.** Android:
`values/strings.xml`, `strings_pay.xml`, `strings_account.xml`, `strings_local_proxy.xml`,
`strings_devices.xml`, `strings_deeplink.xml`, `strings_perapp.xml`, `strings_auth.xml`, plus the
`values-ru/`, `values-vi/`, `values-zh-rCN/`, `values-zh-rTW/`, `values-bn/`, `values-ar/`
translations. Desktop: `Common/L.*.cs`. Clearing these is part of the copy pass, not optional
cleanup.

---

## 10. Iconography

1. **One family.** The project's existing vector drawable set on Android
   (`res/drawable/ic_*.xml`) and the `StreamGeometry` set in `Assets/GlobalResources.axaml` on
   desktop. A glyph that exists on one platform and is needed on the other is ported, not
   redrawn in a different style.
2. **One stroke weight** across a hierarchy level. One fill discipline: outline glyphs in rows and
   toolbars, filled glyphs only for a selected navigation destination and for status.
3. **Sizes are tokens:** 22dp glyph inside a 40dp tile (`tile_glyph` / `tile_size`), 24dp for
   toolbar and navigation glyphs, 20dp for inline chevrons and inline status glyphs, 16dp for
   glyphs inside chips. Nothing else.
4. **Vector only.** No PNG icons. Flags are the one exception and they are the existing
   `Assets/Flags/*.png` set at `Size.FlagTile` 28, circular-masked, with `xx.png` as the fallback
   (`FlagResolver.cs`, `RemarkToFlagConverter`, `util/FlagUtil.kt`).
5. **The unified server icon** (0.4.7): one treatment for a server everywhere it appears (list
   row, connect hero, sheet header, notification): the flag tile at 28 inside the standard 40 tile
   slot, falling back to the globe glyph. No screen invents its own server visual.
6. **No emoji.** See 1.4.4.
7. **Every icon-only control has an accessible name**: `android:contentDescription` /
   `AutomationProperties.Name`. An icon-only control with no name is a P1 defect.
8. Icons are optically aligned to the text baseline of the label they sit beside, and optically
   centred in their tile (a play glyph is nudged right; a chevron is not).

---

## 11. Android translation (Material 3 + XML views)

### 11.1 Theming

- Colours are consumed as **theme attributes** (`?attr/colorPrimary`, `?attr/colorSurface`,
  `?attr/colorOnSurfaceVariant`, ...). A layout referencing `@color/md_theme_*` directly is a
  defect: it breaks the light and mono themes.
- Text is styled by `android:textAppearance="@style/TextAppearance.App.*"`. Setting `textSize`,
  `fontFamily` or `textColor` inline is a defect except when the colour is a genuine state
  (error, success) taken from a theme attr.
- Every theme (`Theme.Departament`, its night variant, `ThemeOverlay.Mono`) is verified for each
  new component.
- `alertDialogTheme` / `materialAlertDialogTheme` are already wired to
  `ThemeOverlay.Departament.Dialog`. Every `AlertDialog` inherits it. Do not build a custom dialog
  layout when the themed dialog does the job.

### 11.2 Components (allowed vocabulary)

| Need | Use | Not |
|---|---|---|
| Primary action | `MaterialButton` filled, `radius_button` 16, `btn_height_tall` 52 as `minHeight`, `insetTop`/`insetBottom` 0 | Custom `TextView` with a background drawable; a pill; a fixed `layout_height` |
| Secondary action | `MaterialButton` tonal (`colorSecondaryContainer`), `radius_button` 16, `btn_height` 48 | A second filled accent button; an outlined accent button |
| Tertiary action | `MaterialButton` text, `btn_height` 48 | An underlined `TextView` |
| Toggle | `MaterialSwitch` | Custom switch, checkbox for a setting |
| Choice among 2-4 | `MaterialButtonToggleGroup` (segmented) | A spinner |
| Choice among many | Bottom sheet list with radio | `Spinner`, `AlertDialog` single-choice for > 6 items |
| Per-item actions | `BottomSheetDialogFragment` | Long-press context menu only |
| Transient feedback | `Snackbar` (with action where useful) | `Toast` |
| Interrupting decision | `MaterialAlertDialogBuilder` | Full-screen activity |
| Container | `MaterialCardView`, elevation 0, stroke 1dp | Nested cards |
| Chip | `Chip` styled to `radius_chip` | A `TextView` with a rounded drawable |
| List | `RecyclerView` + `ListAdapter` + `DiffUtil` | `ScrollView` of inflated rows |
| Screen container | `CoordinatorLayout` / `ConstraintLayout` | Deeply nested `LinearLayout` |

### 11.3 Window, insets, back

- **Edge-to-edge** with `WindowCompat.setDecorFitsSystemWindows(window, false)` and explicit
  `WindowInsetsCompat` handling for status bar, navigation bar, display cutout and IME. Content
  never sits under a system bar or the keyboard.
- **Predictive Back** is honoured. `OnBackPressedCallback` never swallows Back without giving the
  user an equivalent exit.
- Configuration changes (rotation, font scale, theme, split screen) preserve state.

### 11.4 Adaptivity

- `sw600dp`: gutter 24, content max width 720dp centred, bottom navigation becomes a
  `NavigationRailView`, per-item sheets may become dialogs anchored centre.
- Landscape phone: the connect hero shrinks and the content scrolls; nothing is clipped or
  letterboxed. Orientation is never locked to dodge a layout problem.
- The layout is driven by window size classes, never by a device model check.

### 11.5 Performance floor

- No synchronous I/O, JSON parsing or crypto on the main thread in any UI path.
- `RecyclerView` adapters use stable IDs and `DiffUtil`; no `notifyDataSetChanged()` on a visible
  list.
- Images decoded at the size they are displayed.
- First frame after launch under 1s on a mid-range device.

---

## 12. Desktop translation (Avalonia)

Avalonia is not Material and not the web. It has a pointer, a keyboard, resizable windows, and no
touch. The rules below are the desktop equivalent of section 11; the *design* is identical, the
*mechanics* are not.

### 12.1 Theming

- Every colour comes from `{DynamicResource Brush.*}`. `StaticResource` on a theme-dependent brush
  is a defect: it freezes the value at load and breaks live theme switching.
- Every text element carries a ramp class (`Classes="Title"`, `Classes="Subtitle"`, ...). A
  `TextBlock` that sets `FontSize` inline is a defect.
- Component styles live in `Assets/GlobalStyles.axaml` as class selectors (`Border.Card`,
  `Border.Row`, `Button.NavRailItem`, `Border.ChipBadge`, `Border.StatusChip`, `Border.Toast`,
  `Border.ProtocolChip`). A view that hand-rolls a card is a defect; extend the class.
- **No default Fluent/Semi look may leak.** Any control that has not been restyled to the token
  set is a defect: it will look like a different application.

### 12.2 Pointer, keyboard, focus

- `:pointerover` is a real state here and must be designed for every clickable surface (rows,
  cards, nav items, chips, icon buttons). Use `Brush.Hover` or one step up the surface ramp,
  150ms `Ease.Standard`.
- **Keyboard focus is mandatory**, not optional: a visible 2px ring on every focusable control -
  `Brush.Accent` outside the control at 2px offset, or inside it in the control's own on-colour at
  40% when the control is filled (7.1). Tab order follows visual order. Nothing is reachable only by
  mouse.
- Standard shortcuts work: Esc closes a flyout or modal, Enter submits a form, Ctrl+F focuses
  search, Ctrl+, opens settings.
- Cursor: `Hand` on clickable rows and links, default arrow elsewhere. Never a custom cursor.

### 12.3 Windows and layout

- **Minimum window 900x600.** Every view must be usable at that size with no clipping and no
  horizontal scroll.
- Content is capped at 720px and centred inside wider windows; the nav rail is fixed width and
  does not stretch. A stretched phone layout across 1920px is the desktop version of the
  "scaled-up phone UI on a tablet" failure.
- Scroll regions: one per view. No nested scrollers. The scrollbar is the themed thin overlay
  already in `GlobalStyles.axaml`; no custom scrollbar (`product.md` bans reinvented scrollbars).
- DPI scaling is honoured through `UiScaleState`; the layout must survive 100%, 125%, 150%, 200%.
- Modal windows are used only for separate tasks; everything else is a flyout, an inline panel, or
  a sub-page inside the shell.

### 12.4 The desktop-specific surfaces

`LoginView.axaml`, `OnboardingView.axaml`, `HomeView.axaml`, `CompactHomeView.axaml`,
`ConnectHeroView.axaml`, `AccountView.axaml`, `ServersView.axaml`, `SettingsView.axaml` and the
`*SubView` / `*Page` set are the redesign surface. They share:

- the same nouns, the same order, and the same defaults as their Android counterparts (section 13),
- the seamless sub-page toolbar (`Size.SubToolbar` 56, page background, no divider),
- the same states, in the same language, with the same strings from `Common/L.*.cs`.

### 12.5 Reduced motion and lite mode

`MotionState.IsLite` is the single source of truth and it is live. Any animation added to a view
subscribes to `MotionState.Changed` or reads `MotionState.IsLite` at play time and uses
`Motion.Dur.Instant` when lite is on. A view that reads the setting once in its constructor is the
exact bug `MotionState.cs` was written to fix; do not reintroduce it.

---

## 13. Cross-platform parity contract

The two clients are one product. A user who learns one must recognise the other.

**Identical across platforms:** the destination set and its order; every user-visible Russian
string for the same concept; the terminology in 9.3; the default value of every setting; the group
order inside settings; the state matrix in section 15; the token values in section 3; the motion
tempo.

**Allowed to differ:** navigation shape (bottom bar vs left rail), per-item action surface (bottom
sheet vs flyout), hover (desktop only), haptics (Android only), keyboard shortcuts (desktop only),
window chrome, and any platform capability the other does not have.

**Translation table** (from `adapt.native.md`, adapted for our pair):

| Concept | Android | Desktop |
|---|---|---|
| Top-level navigation | Bottom navigation, 3-5 items | Left navigation rail, same items, same order |
| Sub-page entry | Activity or fragment with seamless toolbar | Shell sub-page with `Size.SubToolbar` toolbar |
| Per-item actions | Bottom sheet | Flyout anchored to the row |
| Transient feedback | Snackbar | Toast (`Border.Toast`) |
| Interrupting decision | Material dialog | Modal window, same layout |
| Selection among many | Bottom sheet list | Flyout list |
| Back | System Back / predictive Back | Back button in the sub-toolbar + Esc + mouse button 4 |
| Press feedback | Ripple + scale 0.97 (rows step their background instead, R5) | Scale 0.97, no ripple (same row rule) |
| Hover | none | `:pointerover`, 6% overlay |
| Focus ring | always, on every focusable control | always, on every focusable control |

When a feature exists on one platform and not the other, it is a **parity gap logged in the
platform's spec file**, not a silently different design.

---

## 14. Accessibility floor

Non-negotiable. Each item is a P1 defect when missing.

1. **Contrast**: body >= 4.5:1, large text >= 3:1, icons and control boundaries >= 3:1,
   placeholders >= 4.5:1. Verified in all three themes. A control boundary is drawn with
   `color_outline_control` (D-9), never with `colorOutline`; the 1dp `colorOutlineVariant` hairline
   on row separators and card borders is structural decoration and is exempt from WCAG 1.4.11.
2. **Touch/pointer targets**: 48dp Android, 32px desktop minimum, 8dp separation.
3. **Names**: every interactive element has an accessible name; every icon-only control has an
   explicit one; every image that carries meaning has a description, and decorative images are
   marked as such.
4. **Order**: reading and focus order matches visual order. Nothing is unreachable. Focus is not
   lost on navigation.
5. **Text scaling**: Android at font scale 200% and desktop at 200% DPI must not clip, overlap or
   lose actions. Test the longest Russian string, not the shortest.
6. **Reduced motion**: honoured on both platforms (8.8).
7. **Colour is never the only signal** (6.3).
8. **Keyboard**: on desktop every task is completable without a mouse.
9. **State announcements**: selected, disabled, expanded, loading and error states are exposed to
   TalkBack / screen readers, not only drawn.

---

## 15. Every screen ships its states

A screen is not done until each applicable state below is designed, implemented and looked at.
"Phase one, happy path" is not a thing on this project.

| State | Requirement |
|---|---|
| **Default** | The normal case, with realistic data |
| **First run** | The user has never used this screen. Teaches the interface, offers the one action that populates it |
| **Loading** | Skeletons shaped like the final content, never a centred spinner over a blank screen. Appears only after 300ms |
| **Empty** | Title + one line + one action, per 9.5 |
| **Error** | Cause + fix + retry affordance, per 9.4 |
| **Offline** | Stale data preserved and marked, network-dependent actions disabled, one quiet bar, per 9.6 |
| **Partial** | Some data loaded, some failed. Show what you have; mark what failed inline |
| **Long content** | 40-character server names, 60-character Telegram display names, 12-digit balances: wrap or ellipsise gracefully, never break the layout |
| **Short content** | One server, one device, one payment: the layout must not look broken with a single item |
| **Disabled / gated** | No subscription, expired subscription, device limit reached: the screen explains the gate and offers the unlock action |
| **Success** | Confirmed with a 220ms state change and a word, then it moves on |

Product-specific states that every relevant screen must handle:
`нет подписки`, `подписка истекает`, `подписка истекла`, `триал`, `Telegram не привязан`,
`нет серверов`, `подключение`, `подключено`, `отключение`, `ошибка туннеля`, `лимит устройств`.

---

## 16. Pre-flight check

Mechanical. Run every box before calling any UI work done. A box that cannot be honestly ticked
means the work is not done. Adapted from `taste-skill` section 14, `impeccable/reference/polish.md`
and `audit.native.md`.

**Tokens and system**
- [ ] Zero raw hex in layouts / views (1.5 greps clean)
- [ ] Zero off-scale spacing values (1.5 greps clean)
- [ ] Every text element uses a ramp style; no inline `textSize` / `FontSize`; no inline font family
- [ ] Radii follow the shape lock (3.2): 12 chips, tiles and the segmented thumb; **16 buttons,
      inputs, search field, price option and segmented track**; 20 cards, dialogs and sheet bodies;
      24 the sheet lip; pill only where width == height or the shape is a track
- [ ] Control heights are `minHeight` / `MinHeight`, never a fixed height, and every button style
      sets `insetTop`/`insetBottom` 0
- [ ] No new token invented without a comment stating its purpose and its contrast ratio

**Bans**
- [ ] No side-stripe accent borders
- [ ] No gradient text, no gradient fills, no glows, no decorative blur
- [ ] No nested cards
- [ ] No identical-card grid where a divided list belongs
- [ ] No ALL-CAPS tracked eyebrow; section headers are sentence-case bold
- [ ] No numbered section scaffolding
- [ ] No emoji as UI chrome
- [ ] No second accent hue; accent budget <= 10% of coloured surface, one filled accent surface per screen
- [ ] No text truncated at any supported width or at font scale 200%

**Colour and type**
- [ ] All three themes checked: dark, light, mono
- [ ] Body >= 4.5:1, large >= 3:1, icons >= 3:1, placeholders >= 4.5:1 in every theme
- [ ] Colour never the only signal
- [ ] Inactive and disabled states desaturated
- [ ] Numbers use the Numeric role with tabular figures, `zero` on for technical figures and off
      for currency
- [ ] No Russian string set in the brand face; no layout or view sets a font family at all
- [ ] Line height comes from the ramp on every text element; no platform-default leading
- [ ] Control boundaries use `color_outline_control`; hairlines use `colorOutlineVariant`
- [ ] Light-theme status-chip text uses the darkened `*_text` tokens, not the status hue
- [ ] Longest real Russian string fits

**Interaction**
- [ ] Every interactive element has: default, pressed (scale 0.97, or a background step if it is a
      row), disabled (0.38 on the whole control), focus (**mandatory on both platforms**), plus
      hover on desktop (6% overlay)
- [ ] Selected state reads on two axes, not tint alone
- [ ] Touch targets 48dp Android / 32px desktop, 8dp apart
- [ ] Feedback within 100ms; loading state after 300ms, holding the control's width and hiding its
      label rather than wearing the disabled look
- [ ] Every action button is command-gated or wrapped in the `input_debounce` 500ms guard
- [ ] Forms: label above, error below, validate on blur, no placeholder-as-label
- [ ] Destructive actions use undo, or a dialog only when irreversible
- [ ] Back / Esc always works, restores scroll and filter state

**Motion**
- [ ] Every duration and curve comes from the token scale
- [ ] Ease-out only; no bounce, elastic or linear, the one exemption being `motion_spin`'s
      continuous rotation
- [ ] Press scale is 0.97, in 90 / out 160, everywhere it applies
- [ ] Exit is 75% of enter
- [ ] Only one 600ms hero moment exists in the product, and this is not a new one
- [ ] Reduced motion honoured through `MotionUtils` / `MotionState`, verified by toggling it
- [ ] No animated layout properties; no page-load choreography

**Copy**
- [ ] Every string Russian, sentence case, active verb where it is an action
- [ ] Terminology matches 9.3 exactly
- [ ] Zero em-dashes and en-dashes (9.7 grep clean)
- [ ] Errors state cause and fix and offer recovery
- [ ] Empty states are title + line + action
- [ ] `₽` used, `…` used, «ёлочки» used

**States**
- [ ] Default, first run, loading, empty, error, offline, partial, long, short, gated, success (whichever apply) all implemented and looked at
- [ ] Product-specific gate states handled

**Platform**
- [ ] Android: edge-to-edge insets, predictive Back, bottom nav <= 5 with labels, no `Toast` for actionable feedback, `sw600dp` rail and 24 gutter
- [ ] Desktop: usable at 900x600, DynamicResource only, keyboard-complete, focus ring visible, no default Fluent/Semi leakage, no nested scrollers
- [ ] Parity: same destinations, same order, same strings, same defaults as the other platform

**Verification**
- [ ] The screen was actually run and looked at on both platforms, in dark and light, at default and 200% scale
- [ ] The seven questions of the Departament slop test (2.4) answered honestly

---

## 17. Definition of done and review protocol

### 17.1 Scoring

Every screen is reviewed against the five dimensions of `audit.native.md`, scored 0-4:

1. Accessibility (TalkBack / screen reader, targets, scaling, reduced motion, contrast)
2. Performance (startup, list virtualisation, main-thread work, image handling)
3. Appearance and theming (tokens, all three themes, no hard-coded values)
4. Platform conformance (Material 3 on Android, native desktop idioms, no web shapes, no AI tells)
5. Adaptivity (window sizes, orientation, keyboard/IME, DPI, split screen)

**Ship bar: >= 18/20, with no dimension below 3.** Anything less goes back.

### 17.2 Severity

- **P0 blocking**: prevents task completion. Fix now.
- **P1 major**: platform-guideline or accessibility violation, or a ban in section 1. Fix before
  release.
- **P2 minor**: annoyance with a workaround. Next pass.
- **P3 polish**: no real user impact.

Any hit against section 1 (Absolute Bans, product bans, Departament bans) is **at least P1** by
definition. Any missing state from section 15 is **at least P1**.

### 17.3 Evidence

A review finding cites file and line. A "done" claim states what was run and looked at: which
platform, which theme, which window size or device, which states. A clean grep is a floor, never a
verdict (`polish.md`: "Detector or QA output is defect evidence only; never proof the work is
finished").

---

## 18. Change control

This file changes only by owner decision. When it does:

1. Add the decision to the table below with the date and the reason.
2. Update the affected rule in place, so the body of the document is always current law.
3. Update the token files if a value changed, on **both** platforms in the same change.

| Date | Decision | Rule affected |
|---|---|---|
| 2026-07-26 | Initial rule set established from `.claude/skills/` and the existing token layer in both repos | all |
| 2026-07-26 | Dynamic Color (Material You) is off; the single brand blue wins over wallpaper theming | 6.10 |
| 2026-07-26 | Space Grotesk is the UI face, not a decorative display face; the product-register "no display fonts in UI" ban is satisfied by using it at real weights and legible sizes (**narrowed the same day by D-1 and D-2 below**: it is one of two UI faces and it does not draw Russian) | 1.3, 5.1 |
| 2026-07-26 | Red `#F04452` may fill and tint, but error *text* on dark uses `#FF6069` (`Brush.RedText` / `@color/ping_bad`) because `#F04452` measures 4.88:1 | 3.5, 7.1 |
| 2026-07-26 | **D-1.** The Russian UI face is **Golos Text**, vendored to both platforms as static 400 / 500 / 700 instances, because the owner chose it and the Space Grotesk binary maps 735 codepoints with **zero** in U+0400-U+04FF, so every Russian string set in the brand face has always been drawn by an undeclared per-OS fallback | 0.2, 0.4.2, 1.3, 3.4, 5.1 |
| 2026-07-26 | **D-2.** Space Grotesk is scoped to digits, units, currency, Latin technical tokens, chip labels and the wordmark, and is never applied to a Russian string, because a face that cannot draw the script must not be given the role | 3.4, 5.1, 5.5 |
| 2026-07-26 | **D-3.** Numerals: `zero` on for technical figures and off for currency, `tnum` and `lnum` on wherever the Numeric role is used, identically on both platforms, because a slashed zero in a price reads as a symbol and an untabulated live figure reflows on every tick | 3.4, 5.5 |
| 2026-07-26 | **D-4.** `TextAppearance.App.Numeric` carries `android:textFontWeight="500"`, because the style declares no weight at all today while the ramp specifies 500 | 3.4, 5.4 |
| 2026-07-26 | **D-5.** Coloured icon tiles are a closed system of exactly three - accent, destructive, neutral - because purple / orange / yellow / green encode a category system the product does not have; the old colour resources stay in the files until the last screen that references them migrates, and no new work uses them | 1.4.1, 3.6 |
| 2026-07-26 | **D-6.** Buttons are a **16dp rounded rectangle** (`radius_button` / `Radius.Button`), not a pill; `radius_pill` survives only where width == height or the shape is intrinsically a track, on the strength of the owner's recorded rejection of capsule CTAs at `Assets/GlobalStyles.axaml:3-14`, which outranks this file under 0.1.1. Supersedes 3.2's "buttons are pill" and 16's "pill buttons" | 3.2, 11.2, 16, and `03-direction.md` 4.5 |
| 2026-07-26 | **D-7.** Inputs, the search field, the price option and the segmented track share the button radius **16**, because that collapses nine radii in use to four and a 12dp field beside a 16dp button reads as two systems. Supersedes 3.2's "inputs are 12"; retires `Radius.Search` 14 and `Radius.Traffic` 8 | 3.2, 7.4, 16 |
| 2026-07-26 | **D-8.** Desktop hover is a **6% white overlay on dark and a 6% black overlay on light**, replacing the 32% black scrim, because 32% black over the `#0A0B0D` ground yields `#070709`, a 1.16:1 delta that is invisible | 7.1, 12.2 |
| 2026-07-26 | **D-9.** New semantic colour `color_outline_control` (`#646C7C` dark, `#7D8BA3` light, `#6A6A6E` / `#767679` mono) for input, outlined-button and segmented-track boundaries, because `color_outline` measures 1.45:1 on the dark ground and fails the WCAG 1.4.11 3:1 floor for UI component boundaries; `color_outline_variant` keeps its 1dp hairline role, which is structural decoration and exempt | 3.5, 6.8, 14.1 |
| 2026-07-26 | **D-10.** Light-theme status-chip **text** uses dedicated darkened tokens (`color_success_text` `#065132`, `color_destructive_text` `#C42B32`, `color_warning_text` `#6B5000`), because the status hue on its own 18% fill measures 4.05:1 green, 4.22:1 red and 4.82:1 amber and fails AA | 3.5, 6.7, 6.8 |
| 2026-07-26 | **D-11.** Press scale is **0.97 everywhere**, 90ms in `ease_out_quart` and 160ms out `ease_out_quint`, because one gesture is currently drawn four ways: `press_scale.xml` 0.96, `nav_press.xml` 0.92 at hard-coded 100/120ms with no interpolator, desktop 0.97 at 120ms both directions | 3.7, 7.1, 8.3, 13 |
| 2026-07-26 | **D-12.** The type ramp gains a **declared line-height column** (40 / 28 / 20 / 20 / 20 / 18 / 16 / 14 / 20 / 24), because neither platform declares leading today, so the ramp's stated ratios are unenforced and the two platforms render different leading | 3.4, 5.7 |
