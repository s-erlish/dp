# 03 - Direction

**Departament VPN - what "2026-grade" means for this product, and the one visual direction both
clients commit to.**

`00-rules.md` is the law: tokens, bans, floors, the definition of done. It answers *what the
values are*. This file answers *why the product looks like this and not like every other dark VPN
app*, and it decides the questions the rule set left implicit: what the surfaces mean, what the
accent is spent on, what the typography sounds like, how dense the product is, how it moves, and
what it refuses.

**Precedence.** `00-rules.md` outranks this file on any numeric value or ban. Where this document
needs something the rules do not yet carry, it is written here as a **decision** in section 11, in
the exact shape of a `00-rules.md` section 18 row, and it is not implemented until that row is
pasted into the rules file. Nothing in this document silently overrides law.

| | Android | Desktop |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Paths below are relative to | `/home/user/dp/V2rayNG/app/src/main/` | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/` |

Read before using this file: `.claude/skills/impeccable/SKILL.md`,
`.claude/skills/impeccable/reference/product.md`, `reference/craft.md`, `reference/shape.md`,
`reference/colorize.md`, `reference/typeset.md`, `reference/android.md`,
`.claude/skills/taste-skill/SKILL.md` sections 4, 9 and 14.

---

## 1. What "2026-grade" means for this product

Trend words are not a specification. "Bento", "glass", "aurora", "neo-brutalist", "spatial",
"liquid" are all rejected here as design arguments: none of them survives contact with a settings
list, and every one of them is a first-order AI reflex under `SKILL.md`'s slop test. This product
is **product register** (`reference/product.md`): design serves the task, and the bar is *earned
familiarity*, not novelty.

So "2026-grade" is defined as ten pass/fail properties. Each one is measurable, each one is
currently failed somewhere in the build, and each one is failed by the average competitor. That is
the whole point: the shipping bar is not a mood, it is this list.

### 1.1 It renders the language it ships in

The brand face must be able to set the words on the screen. Measured facts about the vendored
binary (`res/font/spacegrotesk.ttf`, and the byte-identical
`Assets/Fonts/SpaceGrotesk.ttf`):

| Property | Measured value |
|---|---|
| Family name in the file | `Space Grotesk Light` |
| Variable axes | `wght` 300 to 700, **default instance 300** |
| Total mapped codepoints | 735 |
| Codepoints in U+0400-U+04FF (Cyrillic) | **0** |
| `montserrat_thin.ttf` Cyrillic coverage | **0** (orphan file, no reference in any layout) |

The product's UI is Russian. The declared brand face cannot draw a single Russian letter. Every
Russian string in both clients is therefore already being drawn by an undeclared fallback: Roboto
on Android, and on desktop whatever the OS font manager picks (Segoe UI on Windows, DejaVu or Noto
on Linux, and possibly `Assets/Fonts/NotoSansSC-Regular.ttf`, which carries exactly 66 Cyrillic
glyphs at CJK proportions). The same screen is set in three different faces on three operating
systems. 2026-grade means that is decided, not accidental. Section 6 decides it.

### 1.2 Numbers do not move

A VPN client is a number surface: traffic, speed, latency, days left, balance, device count,
uptime. In Space Grotesk the proportional digit advances run from 404 units (`one`) to 638 units
(`zero`) on a 1000-unit em. A live counter re-flows on every tick unless tabular figures are on.
The font carries `tnum` (all ten tabular digits at exactly 620 units), `lnum`, and `zero` (slashed
zero). Desktop already applies `FontFeatures="tnum,lnum,zero"` in
`Views/AccountView.axaml`, `Views/DevicesView.axaml`, `Views/PaymentHistoryView.axaml`,
`Views/LoginView.axaml`. Android declares `"tnum" on, "lnum" on` in
`TextAppearance.App.Numeric` (`res/values/styles.xml:122-127`) but applies it in only two layouts
(`res/layout/activity_account.xml:148`, `res/layout/item_subscription_card.xml:71`). Everywhere
else the digits jitter. 2026-grade means every number in the product sits in a fixed column.

### 1.3 Every state exists, and each one was designed

Not "the happy path plus a spinner". Default, pressed, focused, disabled, selected, loading,
empty, error, offline, and first-run, per `00-rules.md` section 15 and
`reference/interaction-design.md`. A screen that has a beautiful populated state and a blank
`RecyclerView` for its empty state is not finished, it is half-built. `layout_home_empty.xml` and
`layout_servers_empty.xml` exist; `AppPickerActivity` (a bare 10-line `RecyclerView`) does not
have one.

### 1.4 Thirty-six screens speak one grammar

`01-inventory-android.md` measured three parallel row grammars, two spacing scales, three type
systems, four card radii (12 / 14 / 18 / 20 dp) and three divider insets (44 / 68 / 72 dp) inside
one APK. A user cannot learn an interface that changes its rules every third screen. 2026-grade
means one row, one card, one chip, one header, one empty state, used everywhere, on both
platforms. This is `product.md`'s "consistency over surprise", and it is worth more than any
individual screen being clever.

### 1.5 The interface predicts itself

Before the user touches a control they should already know what it will do and what state it is
in. Concretely: a tappable row looks tappable at rest (chevron, or a value, or a switch, never
nothing); a selected item is marked on two channels, not one; a destructive action is red and says
the noun it destroys; a control that is going to open a sheet does not look like a control that
toggles. The current Servers tab fails this: row actions were silently unwired
(`MainActivity.kt:610-611` assigns `onItemLongClick`, `MainRecyclerAdapter.kt:56` no longer calls
it), so the affordance is invisible and also absent.

### 1.6 Motion is a status channel

Motion exists to say "I heard you", "this is now that", "work is happening". Nothing else moves.
No page-load choreography, no section reveals, no looping ambience. `product.md` bans decorative
motion outright; `animate.md` sets the 80ms perceived-instant threshold that the press feedback
has to beat.

### 1.7 Nothing on the screen is decoration

Every pixel is either content, structure, or state. The current home screen fails this five times
over in one composition: a radial glow (`res/drawable/bg_connect_glow.xml`), a full-page radial
gradient (`res/drawable/bg_home_gradient.xml`), a 230dp ring frame, a 212dp indeterminate sweep,
and a sonar pulse, all stacked to communicate one boolean (`res/layout/activity_main.xml:184-290`).
`SKILL.md` bans the glow family; more importantly, four of those five layers carry no information.

### 1.8 Nothing ships that cannot be reached

Roughly 14 registered, built, styled screens are unreachable from any UI path
(`01-inventory-android.md` section 5). Dead surfaces are not neutral: they rot, they confuse
search, and they are where inconsistency hides. 2026-grade means every screen has an entry point
or it is deleted in the same change.

### 1.9 It survives the hostile cases

Font scale 200%, screen width 320dp, a 900x600 desktop window, a 70-character Russian server
remark, a balance of `1 284 371 ₽`, zero servers, no network, an expired subscription, and a
Telegram username of 32 characters. `SKILL.md`'s "text that overflows its container" is an
absolute ban, and Russian runs 10 to 15% longer than English for the same label.

### 1.10 It is honest about what it is

A consumer VPN in Russia in 2026. The copy does not promise "military-grade encryption", the
iconography does not lean on padlocks and globes, and the interface does not perform security
theatre. `clarify.md` voice rules plus `00-rules.md` section 9: direct, calm, technical without
jargon, no exclamation marks, no marketing.

**None of the ten is about taste.** That is deliberate. Taste enters in section 2, on top of a
floor that is already objective.

---

## 2. The direction

### 2.1 The scene sentence

Required by `shape.md` before dark or light can be chosen, and it must be specific enough to force
the answer:

> A man on the 08:40 commuter train into Moscow in February, standing, one hand on the rail,
> phone at 30% brightness in a dark carriage, gloves off for four seconds. He wants the shield
> blue, the name of the city under it, and the phone back in his pocket. He will open the app
> again that evening only if the subscription is running out.

That sentence forces dark (a bright surface at 30% brightness in a dark carriage is a physical
insult), forces one large touch target reachable with a thumb, forces high contrast over
elegance, and forces a four-second primary task. It also tells us what the app is *not*: it is not
a dashboard he studies, and it is not a place he browses.

### 2.2 The statement

> **Прибор, а не витрина. An instrument, not a storefront.** Departament VPN is a black metal
> panel with one lit control on it. The surface is a near-black neutral that never draws
> attention to itself; structure comes from hairlines and 56dp rows, never from cards stacked
> inside cards or from shadows pretending the screen has depth; exactly one object per screen is
> allowed to emit the brand blue, and on most screens that number is zero; the product's voice is
> carried by its numbers, which are set in a mono-derived grotesque at fixed 620/1000 advances so
> that a live traffic counter is as still as a printed one; density is comfortable rather than
> clinical, with a four-value spacing melody instead of a uniform 16dp drone; and motion is
> instrument feedback measured in 90 to 300 milliseconds, with a single 600ms moment in the whole
> product reserved for the instant the tunnel confirms. It reads as engineered rather than
> decorated, calm rather than dramatic, and Russian rather than translated.

### 2.3 The category-reflex check

`SKILL.md` requires this at two altitudes. Failing either means the direction was picked by
reflex.

**First order.** Could someone guess the palette and theme from the category alone? The VPN
reflex is: near-black, neon green or cyan accent, a shield, a globe, a radial glow behind a big
circular power button, a "military-grade" line of copy, and a speed gauge. We are dark and we do
have a circular connect control, so we are inside the reflex on two counts and must escape on the
others: **no glow, no globe, no gauge, no neon, no security theatre in the copy, and blue rather
than the category's green**. Green in this product is demoted to a status colour (connected,
paid), which is the opposite of how the category uses it.

**Second order.** Could someone guess the aesthetic from category-plus-anti-reference? "A VPN
that is not neon-cyber" lands, in 2026, on exactly one thing: the Mullvad or Proton lane, a large
white or grey Swiss surface, an enormous circular button, a wordmark in a geometric sans, and
almost no content. That is the trap one tier deeper, and it fails our scene sentence (light
surface, dark carriage) and our product (a subscription with money, devices, payments and
providers in it, not a single toggle).

**How this direction escapes both.** The identity is not carried by the connect control at all.
It is carried by **the numbers and the ledger**: a product whose recognisable surface is a
hairline-separated list of real quantities set in a distinctive figure face. The connect screen is
the least branded screen in the app, not the most. That inverts the category's own reflex, and it
is defensible on product grounds: the user spends four seconds on connect and several minutes,
across the month, on subscription, traffic, devices and payments.

### 2.4 Anchor references

Named objects, not adjectives, per `shape.md`:

1. **A Fluke or Keysight bench meter.** Black instrument face, one bright readout, hairline
   separators between functional blocks, legends in a technical grotesque, zero ornament. This is
   the surface and the numeric voice.
2. **Linear's dark theme** (the settings and list surfaces only). Row rhythm, hairline structure,
   restrained accent, keyboard-obvious focus, no card grids. This is the ledger.
3. **A Russian bank statement printed on a good laser printer.** Thin-space thousands separators,
   comma decimals, `₽` after a non-breaking space, columns that align because the digits are
   tabular, sentence-case labels. This is the copy and the number formatting.

Explicitly **not** anchors: Apple's control-centre glass, any crypto wallet, any gaming
peripheral suite, any "AI product" with a violet gradient.

### 2.5 Colour strategy

**Restrained**, per `SKILL.md`'s four-step commitment axis and `product.md`'s "Restrained is the
floor". Tinted near-black neutrals plus a single accent held under 10% of coloured surface. This
is not a compromise; a Committed strategy (one saturated colour carrying 30 to 60% of the
surface) would put a blue wash behind a screen whose job is to show a man his remaining traffic in
a dark train carriage.

---

## 3. The three things that make it recognisably THIS product

The test for each: crop the wordmark out of a screenshot and hand it to someone who has seen the
app twice. All three must survive that crop, and all three must be implementable in both an XML
layout and an AXAML view.

### 3.1 Signature one: «Цифра» - the figure face

**The rule.** Every quantity in the product is set in Space Grotesk with `tnum` and `lnum` on, at
the tabular 620/1000 advance, and Space Grotesk appears **nowhere else inside a Russian sentence**.
Numbers, units, currency, latency, codes and the wordmark are the brand face. Prose is not.

This is the direction turning a hard constraint into an identity. The brand face has no Cyrillic
(1.1), so its only honest home is the part of the interface that is not Cyrillic. Rather than
fighting that, the direction makes the split load-bearing: **the product's brand shows up wherever
there is a number, which in a VPN client is everywhere that matters**.

**What it looks like in practice.**

| Slot | Set in | Example string |
|---|---|---|
| Traffic used / total | Numeric | `12,4 ГБ` (unit in UI face, figure in brand face) |
| Speed | Numeric | `24,8 Мбит/с` |
| Latency | Numeric | `48 мс` |
| Price | Numeric | `1 290 ₽` (U+2009 thin space, U+00A0 before `₽`) |
| Days remaining | Numeric | `27` with the word «дней» in the UI face |
| Device count | Numeric | `3 / 5` |
| Uptime | Numeric | `02:14:07` |
| Wordmark | Brand face, 20sp/700 | `departament` |
| Protocol and transport chips | Brand face, 11sp/500 | `VLESS`, `Reality`, `WS` |
| Russian prose, labels, buttons | UI face | «Подключить», «Осталось трафика» |

**Why this is recognisable and not generic.** Space Grotesk's figures are mono-derived: the `1`
has a full foot serif, the `7` is unbarred, the slashed `0` is available through the `zero`
feature, and at 620 units they are noticeably narrower than the Cyrillic text they sit beside.
A number in this app looks stamped. Two competitors using Inter for everything cannot produce that
line.

**The measurements that make it implementable.**

- Tabular digit advance: **620/1000 em**. A five-digit figure at 34sp reserves
  `5 x 0.620 x 34 = 105.4sp`; reserve that width so a balance going from `9 999` to `10 000` does
  not shift the layout. Right-align every numeric column.
- Thousands separator inside a number: **U+2009 THIN SPACE** (188/1000 em in this font).
- Column padding when the digit count changes and the column must not move: **U+2007 FIGURE
  SPACE**, which this font sets at exactly 620/1000, one digit wide.
- Currency: **U+00A0** (258/1000) then **U+20BD** (632/1000). Never the string `RUB`, never
  `руб.` (`00-rules.md` 0.4.4).
- Decimal separator in Russian is a comma. `12,4`, never `12.4`.
- x-height of the brand face is **486/1000**; Roboto is near 528 and Inter near 546. The brand
  figures therefore read roughly 8% smaller than the Cyrillic beside them at the same sp value.
  **Do not compensate by inventing a size step** (`00-rules.md` 5: "15sp does not exist"). Compensate
  by never putting the two faces inside one running sentence: numbers live in their own slot,
  their own column, or their own line.

**The one exception.** Inside a sentence of body copy that must contain a figure
(«Осталось 3 дня подписки»), the figure is set in the UI face like the rest of the sentence. A
sentence never ripples between two faces. If the figure matters enough to be branded, it is not a
sentence, it is a value, and it gets its own slot.

**Slashed zero.** On for technical figures (latency, ports, traffic, identifiers, device
fingerprints), off for money. A slashed zero in a price reads as a correction mark. This resolves
the current parity gap where desktop passes `zero` and Android does not (decision D-3, section 11).

### 3.2 Signature two: the single lit element

**The rule.** On any screen, at most one element emits the accent at full strength, and that
element is the thing the user came to do. Everything else on that screen is neutral. On most
screens the count is **zero**, and a settings screen with no blue on it anywhere is correct, not
unfinished.

**Why this is a signature and not just restraint.** Every dark app claims a single accent and then
spends it on eight things: the active tab, the toggle, the link, the badge, the icon tile, the
progress bar, the chart, the header. The result is that the accent stops meaning anything. Holding
the count at one produces a specific, visible effect: **the eye lands on the same place on every
screen before the user has read anything**. That is a recognisable behaviour of the interface, not
just a palette rule.

**What is allowed to be lit, per screen.**

| Screen | The one lit element | Everything else |
|---|---|---|
| Home, disconnected | The connect control's ring, at rest, unfilled | Neutral: server name, stats, account chip |
| Home, connected | The shield fill (blue) | Neutral, plus green status word and dot |
| Sign-in | The primary button «Войти» | Neutral: the alternate paths are text buttons |
| Servers | The selected row's state marker | Neutral rows, neutral chips, neutral flags |
| Settings hub | Nothing | Everything neutral, including all icon tiles |
| Account | The «Купить» button, only when the account state needs it | Everything else, including the tariff badge |
| Buy | The selected price option | Neutral option rows |

The current build breaks this most visibly on Home, where the accent appears in the page gradient,
the glow, the ring, the sweep and the sonar simultaneously, and on the settings surfaces, where
`res/values/colors.xml` defines six coloured icon tiles (`icon_tile_blue/green/orange/purple/red/
yellow`) that turn a settings list into a paint chart. Coloured tiles are a **category system**
with at most four categories, or they are noise; `icon_tile_neutral` `#20242B` with
`icon_glyph_neutral` `#9BA1AD` is the default and covers most rows.

**The corollary that makes it strict.** The wordmark is not blue. `@style/ToolbarBrandTitle`
already sets `?attr/colorOnBackground`; keep it. The brand does not spend its one accent on
advertising itself while the user is trying to press a button.

### 3.3 Signature three: the hairline ledger

**The rule.** The repeating structural unit of the entire product, on both platforms, is a 56dp
row on a flat surface, with a 1dp `colorOutlineVariant` hairline that begins at the 68dp text
origin and never runs under the leading tile. Lists are rows. Settings are rows. Servers are rows.
Devices are rows. Payments are rows. **Cards are reserved for objects, maximum one card per
screen.**

```
[16 gutter][40dp tile r12, 22dp glyph][12][ text column, weight 1 ][12][ one trailing ][16 gutter]
                                            Title     16sp / 700 / onSurface / max 2 lines
                                            Subtitle  13sp / 400 / onSurfaceVariant / max 2 lines
|<--------------- 68dp --------------->|<---- hairline starts here, 1dp #20242B ------------->|
```

**Why this, and not cards.** `SKILL.md`: "Cards are the lazy answer... Nested cards are always
wrong", and "Identical card grids" is an absolute ban. The current build has four card radii and
a card-in-card price list in `activity_buy_tariff`. More importantly, a ledger is the correct
metaphor for what this product actually contains: a subscription, a traffic allowance, a device
list, a payment history and a server list are all **records**, and records belong in a ruled list,
not in floating tiles. The one card per screen is the *object* the screen is about (the
subscription card on Account, `res/layout/item_subscription_card.xml`), and its singularity is
what gives it weight.

**The 68dp origin is the thing you can see across screens.** Every title on every screen starts at
the same x. Every hairline starts at the same x. Scrolling from Settings into Servers into
Payments, the text column does not move. That continuity is the recognisable part, and it is
mechanically checkable.

**Desktop parity.** Same row, same 56 (`Size.Row`), same 40 (`Size.Tile`), same origin, hairline in
`Brush.OutlineVariant`. The rail replaces the bottom bar; the row does not change.

---

## 4. The surface and elevation model

### 4.1 Four planes, no shadows

Dark mode gets depth from surface lightness, never from shadow (`colorize.md`: "In dark mode,
depth comes from surface lightness, not shadow"). The product has exactly four planes and they
have fixed meanings. Values are `00-rules.md` 3.5 and are not restated as new tokens here; what is
new is **what each plane is allowed to mean**.

| Plane | Dark | Light | Meaning. Nothing else uses this plane. |
|---|---|---|---|
| P0 Ground | `#0A0B0D` | `#F4F7FC` | The screen itself, and the toolbar, which shares it |
| P1 Object | `#141619` | `#FFFFFF` | A card, a sheet body, a dialog body. A discrete thing the user acts on as a unit |
| P2 Raised | `#1A1D21` | `#EAEFF7` | Transient raise only: desktop hover, pressed row, drag |
| P3 Inset | `#20242B` | `#E3EAF4` | Something recessed into a plane: input field, chip fill, neutral icon tile, selected row |

Two reads follow from this table and both are directional:

- **P2 is a verb, not a noun.** Nothing is P2 at rest. If a component is P2 when nobody is
  touching it, it is wrong. This is what keeps a settings screen from turning into a stack of
  grey slabs.
- **P3 is inset, not elevated.** A chip and an input field are holes in the panel, not objects on
  top of it. This is why they share a tone and a 12dp radius, and it is why a chip never carries a
  hairline border as well as a fill (that would be a hole with a rim).

### 4.2 The plane budget

**At most two planes stacked above ground in any one region, three counting ground.**
Legal: `P0 -> P1 card -> P3 chip inside it`. Illegal: `P0 -> P1 -> P1` (nested cards, banned),
`P0 -> P1 -> P2 -> P3` (a raised thing inside a card), `P0 -> P3 -> P1` (a card inside an input).

### 4.3 The app has no chrome

This is the direction's most visible structural decision, and it follows from owner request
0.4.6. The toolbar is P0. The status bar area is P0. The scroll container is P0. There is no
bar-coloured band at the top of any screen, no elevation line, no shadow, and no scrim.
`res/drawable/bg_nav_header.xml` and `res/drawable/bg_bottom_nav_scrim.xml` are gradients that
exist to fake such a band and are deleted.

The single permitted exception, and it is opt-in per screen: when a scrolling list would otherwise
slide under a title with no boundary at all, a **1dp `colorOutlineVariant` hairline** fades in
under the toolbar over `motion_state` 220ms once `scrollY > 0`, and fades out again at 0. Never a
colour change, never elevation, never a shadow.

Consequence for the bottom navigation on Android: it sits on P0, it has no scrim, and it has no
ripple glow (owner request 0.4.8). The active destination is marked by
`@style/BottomNavIndicator` (the accent container pill) plus the label stepping from weight 500 to
700, which is two channels as required by section 5.

### 4.4 Separators

- Between rows in a group: 1dp `colorOutlineVariant`, inset to 68dp, never full-bleed, never under
  the tile.
- Between groups: 24dp of space and a section header. **No divider under a section header** and no
  divider above the first row of a group. Space separates groups; hairlines separate siblings.
- Never a `border-top` *and* a `border-bottom` on the same row (taste-skill 9.F).
- On desktop, the rail and the content share P0 and are separated by a single 1dp
  `Brush.OutlineVariant`, not by a tone change.

### 4.5 Radius, restated as meaning

The values are fixed in `00-rules.md` 3.2. The direction assigns them meaning so that a new
component picks its radius by asking what it is, not by eye:

- **Pill (100)**: things you press that are the point. Primary CTA, the connect disc, a segmented
  thumb.
- **20**: objects. Cards, dialogs, sheet bodies.
- **12**: fittings. Chips, tiles, inputs, badges, flags.
- **24 top only**: the bottom sheet lip.

Anything that is not one of those four is a defect. A 26dp pill (currently in the sign-in card)
and an 18dp card (currently in per-app proxy) are both defects by this rule, not matters of taste.

### 4.6 The AMOLED / mono theme

The third theme is not a separate design. It remaps P0 to `#000000` and shifts P1 to P3 down by
one step, and in mono the accent remaps to ink. Everything structural, the 68dp origin, the
hairlines, the row height, the two-channel selection, is identical. A theme that needs a different
layout is not a theme.

---

## 5. Accent strategy

### 5.1 One hue, two jobs

`#4C8DFF` on dark, `#1E5FC7` on light. It has exactly two jobs:

1. **Action.** The one thing the screen wants you to press.
2. **State that the user controls.** Current destination, current selection, focus, link,
   determinate progress.

It has no third job. It is not a brand wash, not a heading colour, not an icon tint for
decoration, not a divider, not a chart palette.

### 5.2 The budget, stated as arithmetic

Per screen: **one** filled accent surface, plus at most **three** further accent-tinted elements
(a selected state, a focus ring, a link or a progress fill). If a screen needs a fifth accent
element, the screen has more than one primary action and the screen is wrong.

Measured as area: on a 1080 x 2400 phone screen, accent pixels stay under about 6% of the visible
surface. The 52dp pill CTA at full width is roughly 2.5%; the connect disc ring is under 1%. There
is room for one of those and nothing more.

### 5.3 What the other colours are, and why they are not accents

| Colour | Role | May it be an action? |
|---|---|---|
| Green `#22C55E` | Status only: подключено, оплачено, активно | **No.** A green button does not exist in this product |
| Red `#F04452` fill / `#FF6069` text | Destructive action and error only | Yes, and only for destroy or fail |
| Amber `#EAB308` | Warning only: истекает, ждёт оплаты | **No** |
| Neutral `#9BA1AD` | Everything else | n/a |

The category reflex is a green "connect" button. We refuse it: green in this product means *a
fact about your account or your tunnel*, never *press me*. That distinction is one of the reasons
a screenshot of this app does not look like the other twelve.

### 5.4 Selection is always two channels

Never colour alone (`colorize.md`, WCAG, and 8% of men). The permitted pairs:

- Accent fill **and** weight step (500 to 700). Used by the bottom bar and the segmented control.
- Accent fill **and** a check glyph. Used by single-choice lists and price options.
- Accent left-side *state marker* is **forbidden** (side-stripe ban). A selected server row is
  marked by P3 fill plus a filled state glyph, not by a stripe.

### 5.5 Inactive, disabled, and idle

`product.md` bans "heavy color or full-saturation accents on inactive states". So:

- Unselected tab: `colorOnSurfaceVariant`, weight 500, no tint.
- Disabled control: `colorOnSurface` at 38% alpha, no accent, no desaturated blue.
- Idle connect control: neutral ring on P0, neutral shield outline. **The disconnected state is not
  a dim blue, it is grey.** If the user's connection is off, nothing on the screen is blue.

### 5.6 Focus

2dp accent ring, 2dp offset, radius following the control. Always rendered on desktop (pointer and
keyboard), rendered on Android for keyboard and TV D-pad only. The ring is accent, and it is one of
the three permitted extra accent elements in 5.2.

### 5.7 Where accent is banned outright

Backgrounds. Section headers. Dividers. The wordmark. Empty-state artwork. List icon tiles that
are not a category. Chips that are not selected. Any inactive state. Any shadow or glow, since
those do not exist. Any gradient, since those do not exist.

---

## 6. Typographic voice

### 6.1 The two faces, and what each one is for

`00-rules.md` 5.1 sets the two-face rule. This section decides the split by **script**, because
the measurement in 1.1 leaves no alternative.

| | Face | Carries | Weights |
|---|---|---|---|
| **Brand / figure face** | Space Grotesk (variable, `wght` 300-700) | Digits, units, currency, Latin technical tokens (`VLESS`, `Reality`, `WS`, `TCP`, host names, ports), the wordmark, chip labels | 500, 700 |
| **UI face** | one Cyrillic-capable grotesque, identical on all four operating systems | All Russian prose: titles, labels, buttons, subtitles, captions, errors, empty states | 400, 500, 700 |

**Why this is a legitimate pairing and not the banned "two similar sans".** `typeset.md` forbids
pairing two faces that are similar but not identical, because the reader sees an inconsistency
without seeing a reason. Here the two faces never occupy the same role, never occupy the same
line inside a sentence, and are partitioned by script, which is the one partition a reader
perceives as intentional. The pairing axis is geometric-mono (figures) against humanist
(prose), which is exactly the contrast axis `typeset.md` sanctions.

**Which Cyrillic face.** This needs an owner decision (D-1, section 11). The direction's
recommendation, in order:

1. **Golos Text** (OFL, variable, Cyrillic-first, designed for Russian UI). Humanist-leaning
   neo-grotesque, which gives real contrast against the geometric figures instead of the
   near-miss similarity `typeset.md` warns about. Vendored to `res/font/` and `Assets/Fonts/`,
   subset to Cyrillic plus Latin plus punctuation.
2. **Onest** (OFL, variable, Cyrillic) if a slightly more neutral voice is wanted.
3. **Fallback if no new binary is allowed:** the platform face, pinned explicitly. Android:
   `sans-serif` (Roboto). Desktop: an explicit per-OS stack rather than the current implicit one,
   so that Windows, Linux and macOS stop rendering three different products. Inter is *not*
   chosen by default (`typeset.md` anti-reflex, taste-skill 4.1); if it is chosen it must be
   because someone argued for it.

**What must stop immediately either way.** `Assets/GlobalStyles.axaml` currently sets
`FontFamily="{DynamicResource Font.Grotesk}"` in sixteen places, including body text classes.
Since the file has no Cyrillic, those setters do nothing for Russian text except hand the
decision to the OS. The UI face must be its own token (`Font.Ui` / a Cyrillic-capable
`@font/` family), and `Font.Grotesk` must be applied only to the slots in the table above.

### 6.2 Weights, and the ban on everything between

Three weights, and the file supplies real masters for all three (`wght` axis 300-700, named
instances Light 300 / Regular 400 / Medium 500 / Bold 700):

| Weight | Used for | Never used for |
|---|---|---|
| **400** | Body, subtitle, caption, all prose | Titles, numbers that matter |
| **500** | Chip labels, selected states, values, the Numeric role, inactive nav labels | Body prose |
| **700** | Display, headline, title, section header, active nav label, the wordmark | Body prose, subtitles, more than one element per row |

**600 does not exist.** **300 does not exist in the UI** even though the file's default instance is
Light. **Italic does not exist**: the variable font carries a `wght` axis only, no italic axis and
no companion italic file, so any italic in the product would be a synthetic oblique. Emphasis is
weight or the next step up the ramp, never slant, never colour, never a second family.

### 6.3 The variable-font verification item (P1, do before any type work)

The vendored file's **default instance is `wght` 300**, its internal family name is
`Space Grotesk Light`, and `res/font/space_grotesk.xml` declares three `<font>` entries at 400,
500 and 700 that all point at that same file **without `android:fontVariationSettings`**. There is
no `fontVariationSettings` anywhere in `res/` or `java/` (verified by grep). This creates a
concrete risk that every Space Grotesk run on Android renders at Light 300 regardless of the
declared weight, which on a dark surface is exactly the "light text on dark reads thinner"
failure `typeset.md` warns about, and which would make the whole brand voice look wrong while
every style file appears correct.

**Verification procedure, mandatory before the type work starts.**

1. Build a debug screen containing `0123456789` at 34sp in `TextAppearance.App.Display` and the
   same string with `android:fontVariationSettings="'wght' 700"`.
2. Screenshot on an API 28+ device and an API 26 device.
3. Compare stem widths. If they differ, the family XML is not applying the axis.

**Fix if confirmed:** add `android:fontVariationSettings="'wght' 400"` / `500` / `700` to the three
entries in `res/font/space_grotesk.xml` (API 28+), and ship named static instances as a fallback
family for API 26-27, or bake three static instances and drop the variable file. Also add
`android:textFontWeight="500"` to `TextAppearance.App.Numeric`, which currently declares no
weight at all (`res/values/styles.xml:122-127`) while `00-rules.md` 3.4 specifies 500.

### 6.4 Tracking, and why the ramp already carries the dark-mode compensation

`typeset.md`: light-on-dark needs compensation on three axes (line height, tracking, weight). The
ramp in `00-rules.md` 3.4 and 5.6 already carries it: Display -0.02em, Headline -0.01em, Title 0,
Body and Subtitle +0.01em, Caption +0.02em, Chip +0.04em. Do not add more per screen, and never add
tracking to make a label "look designed". Tracking above +0.02em at a heading size is the eyebrow
tell and is banned.

### 6.5 Numerals, in full

- `tnum` on, `lnum` on, everywhere a number appears, both platforms.
- `zero` on for technical figures, off for currency (D-3).
- Every numeric column is right-aligned, and its width is reserved for the maximum expected digit
  count using the 620/1000 advance.
- A number that updates more than once per second (speed, uptime, live traffic) never changes its
  own layout: reserve, then fill.
- Formatting is Russian: comma decimal, U+2009 thousands, U+00A0 before `₽`, `12,4 ГБ`,
  `24,8 Мбит/с`, `48 мс`, `02:14:07`.
- Never a fake-perfect number in any placeholder, mock, or empty state (`99,9%`, `1234567`).
  Realistic values only: `47,2 ГБ`, `1 290 ₽`, `183 мс`.

### 6.6 Voice of the words themselves

Section 9 of `00-rules.md` is the copy law and is not restated. Two directional additions:

- **No security theatre.** The product never says «военный уровень шифрования», «максимальная
  анонимность», «100% защита». It says what is true: «Подключено», «Трафик: 12,4 из 50 ГБ»,
  «Подписка до 14 августа».
- **The interface addresses a competent adult.** No tutorials that explain what a VPN is, no
  reassurance copy, no exclamation marks. First-run teaches by showing the two things that must
  happen («Войти» and «Подключить»), not by narrating.

---

## 7. Density and rhythm

### 7.1 The target

`VISUAL_DENSITY 4` on the taste-skill dial: **comfortable instrument**. Not a cockpit. The failure
we are explicitly avoiding is the recorded Incy review complaint, «шрифт очень мелкий и
тесновато». Body text is 14sp and never smaller for prose; 11sp exists only for chip labels.

On a 1080 x 2400 phone at 56dp rows, a screen shows roughly eleven rows plus a header. That is the
intended density: enough to scan a settings group without scrolling, not enough to feel like a
table.

### 7.2 The spacing melody

`layout.md`'s rhythm rule, made concrete. A screen uses four gap values and they are not
interchangeable:

| Gap | Between |
|---|---|
| 4 / 8 / 12 | Parts of one object: glyph to label, title to subtitle, chip padding |
| 16 | Objects: card to card, row group to row group, the screen gutter |
| 24 | Sections, and the space that replaces a divider under a section header |
| 32 | Used **at most twice per screen**: after a hero, before a bottom CTA |

**A screen where every vertical gap is 16 has no hierarchy and fails the squint test.** That is the
single most common defect in the current build and it is why several screens read as "abbly"
despite using correct tokens.

### 7.3 Information architecture density

These are direction rules, and they are what turn "settings" from a dump into a designed surface:

- **Maximum 7 rows per group.** An eighth row means the group is really two groups, or it belongs
  on a sub-page.
- **Maximum 4 groups per screen.** A fifth group means the screen is a hub and its groups are
  sub-pages.
- **Maximum 2 levels of navigation depth** below a tab. Tab -> sub-page -> detail. A third level
  is a design failure, not a routing decision.
- **One display figure per screen.** The 34sp Display role appears exactly once, or not at all.
  Two big numbers on one screen is the banned hero-metric template.
- **No screen has two primary actions.** If two things look equally pressable, one of them is
  wrong (taste-skill: no duplicate CTA intent).

### 7.4 Horizontal rhythm

- Phone: single column, 16dp gutter, text origin 68dp, everything left-aligned. Centred text is
  used only in a genuinely empty state and in the connect control's own label.
- Tablet and `sw600dp`: gutter steps to 24. Nothing else changes.
- Desktop at width >= 1000: gutter steps to 24 **and the content column caps at 720px**, left
  aligned against the rail rather than stretched. A 56dp row stretched across 1600px is
  unreadable and is the most common desktop port failure (`adapt.native.md`: restructure, do not
  stretch).
- Minimum window 900 x 600 must show the rail, the toolbar, and at least six rows without
  horizontal scroll.

### 7.5 Touch and pointer

48dp minimum target with 8dp between targets (`android.md`). The row is the target, not the
trailing glyph. On desktop the row is the click target and the hover raise is the whole row, not
the label.

---

## 8. Motion personality

### 8.1 The personality in one line

**It acknowledges, it never performs.** The product moves like a physical control panel: instant
under the finger, decisive when it changes state, and completely still the rest of the time. There
is no ambience, no idle motion, and no entrance choreography anywhere in the product.

### 8.2 The four tempos

The token values are `00-rules.md` 3.7 and are not re-specified. Their *character*:

| Tempo | ms / curve | Character | Where |
|---|---|---|---|
| Touch | 90 in / 160 out, `ease_out_quart` / `quint` | Immediate, physical, under the 80ms perceived-instant threshold | Every pressable thing, scale to 0.97 |
| Change | 220, `ease_standard` | Deliberate, two-way, reversible | Tint crossfade, selection, enable/disable |
| Reveal | 300 in / 225 out, `ease_out_quint` | Confident arrival | Sheets, sub-pages, expands |
| The moment | 600, `ease_out_quint` | Once in the product | Connect confirmation |

Exit is 75% of enter, always. Ease-out only. No bounce, no elastic, no spring, no linear.

### 8.3 The one hero moment, specified as an experience

The instant the tunnel confirms, and nothing else in the product, gets `motion_emphasis` 600ms:

1. The shield outline crossfades to the filled shield (alpha 0 to 1, 220ms, `ease_standard`).
2. **One** ring is emitted from the disc edge, scaling from 1.0 to 1.35 while fading 0.6 to 0,
   over 600ms, `ease_out_quint`. It never loops and there is never a second ring.
3. A single `pressHaptic()` fires on the same frame as step 1.
4. The status word changes from «Отключено» to «Подключено» with no animation at all.

Everything else about that transition is still. The page does not flash, the background does not
tint, the nav bar does not react.

### 8.4 The latency contract

- Any tap produces a visible change within **80ms** (`animate.md`).
- Any operation that can exceed **400ms** shows a **skeleton in the shape of the result**, never a
  centred spinner (`product.md`). The subscription card, the server list and the payment history
  all need skeletons.
- The connect control's indeterminate sweep runs **only while the core is actually negotiating**,
  and stops the moment the state resolves. An indeterminate indicator that runs while nothing is
  happening is a lie about the system.
- Optimistic UI is allowed for toggles that cannot fail locally, and is forbidden for anything
  involving money or a tunnel state.

### 8.5 What never moves

No looping animation exists in this product except a genuine indeterminate progress indicator
during real work. No parallax, no scroll-linked transforms, no animated gradients (there are no
gradients), no pulsing glow (there is no glow), no shimmer on anything that is not a skeleton, no
staggered section entrances, no cross-fade on tab switch beyond the 220ms state tempo, and no
animated splash beyond the platform default.

List stagger is 40ms per item, capped at 400ms total, and only for freshly loaded siblings. It is
not applied to a screen's sections.

### 8.6 Reduced motion

A contract, not a nicety, per `00-rules.md` 8.8. Android `MotionUtils.animationsEnabled()`,
desktop `MotionState.IsLite`. When off, jump to the end state; the hero moment becomes an instant
shield fill plus the haptic. Any new animation that does not check is a P1 accessibility defect.

---

## 9. What this direction forbids

Tier one is `00-rules.md` section 1 (the absolute bans and their per-codebase forms) and is not
repeated. Tier two is what **this direction** adds, with the rewrite in every case.

| # | Forbidden | Why | Rewrite as |
|---|---|---|---|
| F1 | Any decorative gradient or glow. Named files to delete: `res/drawable/bg_home_gradient.xml`, `bg_home_gradient_mono.xml`, `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`, `bg_nav_header.xml`, `bg_bottom_nav_scrim.xml` | 4 of the 5 hero layers carry no information; `SKILL.md` bans the glow family | Flat `?attr/colorBackground`. State lives in the shield and the ring |
| F2 | More than one card on a screen, or any card inside a card | `SKILL.md` absolute ban plus 3.3 | Rows with hairlines |
| F3 | A third font family. `res/font/montserrat_thin.ttf` is an orphan and is deleted | Two faces, no more | The two faces in 6.1 |
| F4 | Italic, synthetic bold, and weight 600 | The file has no italic master and no 600 need | Weight or a ramp step |
| F5 | Space Grotesk applied to a Russian string | It has zero Cyrillic; the setter is a no-op that hands the choice to the OS | The UI face token |
| F6 | Any number without `tnum` | Digits jitter from 404 to 638 units | `TextAppearance.App.Numeric` / `Font.Numeric` + `FontFeatures` |
| F7 | A blue wordmark, a blue empty state, a blue section header, a blue divider | The accent belongs to the user's action | `colorOnBackground` / `colorOnSurfaceVariant` |
| F8 | A green button, an amber button, or a blue "delete" | Colour meaning is global and fixed | Blue for action, red for destroy |
| F9 | Colour as the only carrier of a state | `colorize.md`, WCAG, colour blindness | Colour plus glyph, or colour plus word |
| F10 | Emoji as UI chrome, including the emoji flags currently used in server rows | `00-rules.md` 0.4, plus emoji render differently per OS and per Android version | Real flag assets at `Radius.Tile` 12, or a two-letter typographic tile |
| F11 | A label printed on top of a moving progress fill (the current subscription meta bar, measured at 2.9:1) | Contrast floor | Label above the bar, value to its right, both on the plane |
| F12 | A spinner as the primary loading affordance for content | `product.md` | Skeleton in the shape of the result |
| F13 | A dialog for a choice that fits inline. Currently six single-choice `AlertDialog`s (Режим, DNS, Пинг, Оформление, Язык, Автообновление) | "Modal as first thought" is a product ban | Inline segmented control (2-3 options) or a push sub-page (4+) |
| F14 | A toast at a magic offset (`toast_status.xml` at 110dp) | Competes with the nav bar, breaks on gesture nav | `Snackbar` anchored above the bar / `Border.Toast` |
| F15 | Any screen without an entry point | Dead surfaces hide inconsistency | Wire it or delete it in the same change |
| F16 | A settings subtitle that restates its title | Noise (`distill.md`) | Say what the row does in 6 words, or nothing |
| F17 | Security theatre in copy or iconography: padlocks, globes, shields beyond the one connect object, «военный уровень», «100% анонимность» | 1.10, and it is the category's own reflex | State facts |
| F18 | Trend vocabulary as a design argument: bento, glass, neumorphism, mesh gradient, aurora, neon, 3D globe, blob | None survives a settings list; each is a documented AI tell | Argue from the ten properties in section 1 |
| F19 | An em-dash or an en-dash anywhere in UI copy, code comments visible to users, or these docs | `00-rules.md` 9.2 and taste-skill 9.G | Hyphen, comma, colon, full stop, or a line break |
| F20 | A new radius, a new duration, a new grey, a new size step | The token set is closed | Add to the token file first with a comment and a contrast ratio, or use what exists |

---

## 10. The direction applied to the two screens the owner named

Not the screen specs. Those are separate documents. This is the direction proving it produces an
answer, so the implementer can see what "on direction" looks like before designing anything else.

### 10.1 Sign-in (`res/layout/activity_login.xml`, `Views/LoginView.axaml`)

Today: two stacked cards, four buttons, two spinners, one error line, 26dp pill radii that appear
nowhere else, and a 12sp «или войдите» divider. Verdict in `01-inventory-android.md`: REBUILD.

What the direction produces:

- **Ground plane, no card at all.** The screen is P0 from edge to edge. The sign-in form is not an
  object floating on a surface; it is the screen.
- **One display line, one figure face moment.** The wordmark `departament` at 20sp/700 in the
  brand face, sitting at the gutter, 32 below the top inset. No logo lockup, no tagline, no
  illustration.
- **One heading in the UI face**, 24sp/700: «Вход». One subtitle, 13sp/400: «Почта и пароль, или
  Telegram».
- **Two inputs**, P3 inset, radius 12, label above the field, never placeholder-as-label. Error
  text below the field in `#FF6069`, formula "what happened, why, what to do".
- **One lit element**: the pill CTA «Войти», full width at the gutter, accent fill, 52 tall.
  Desktop already has `Size.CtaTall` 52; Android has no mirrored token, so per F20 add
  `<dimen name="cta_height">52dp</dimen>` to `res/values/dimens.xml` **before** using it.
- **Everything else is a text button**: «Войти через Telegram», «Забыли пароль?»,
  «Создать аккаунт». Three text buttons stacked with 12 between, at 48 tall each. They do not
  compete with the CTA because they are not filled.
- **States**: idle, focused (2dp accent ring), submitting (CTA label swaps to a 20dp indeterminate
  indicator, the CTA stays the same size so nothing reflows), error (inline, field-level), locked
  out, offline («Нет сети. Проверьте подключение и повторите»).
- **Motion**: nothing on entry. The one exception in the whole app is the hand-off out of this
  screen to Home at `Dur.Slow` 450 with `Ease.OutExpo`, which is already tokenised.

### 10.2 First tab at launch (`res/layout/activity_main.xml`, `layout_home_empty.xml`, `Views/HomeView.axaml`)

Today: a 230dp stack of glow plus gradient plus ring plus sweep plus sonar to say one boolean,
then seven unrelated blocks with no rhythm, and an empty state that is a different screen sharing
one file.

What the direction produces:

- **P0 from edge to edge.** Delete the page gradient. Delete the glow.
- **The connect object is one disc**: 176dp, P3 fill, 1dp outline hairline, a 80dp shield glyph
  inside. Disconnected the whole thing is neutral. Connecting, the ring carries the sweep.
  Connected, the shield is filled blue and the ring is accent at 1dp. That is three states on one
  object, with no extra layers.
- **One line under the disc**: the server name in the UI face, 16sp/700, plus the status word in
  13sp/400 and a status dot. Two channels, per 5.4.
- **One numeric strip**, not a stat card grid: `24,8 Мбит/с` down, `3,1 Мбит/с` up, `48 мс`, all
  in the figure face at 500 with `tnum`, in a single row of three right-aligned columns with
  reserved widths, separated by space and not by dividers.
- **Then the ledger begins.** Subscription row, servers row, and nothing else above the fold. Every
  further block is a row group with a sentence-case bold section header and 24 between groups.
- **The empty state is the same screen**, not a second layout: the disc is present and disabled,
  the numeric strip is absent, and one CTA row says «Купить подписку» with a subtitle
  «Осталось выбрать тариф». First-run is a state of Home, not a different Home.

---

## 11. Decisions this document takes, and what needs sign-off

### 11.1 Taken here (no owner decision required, inside existing law)

- **D-A.** Colour strategy is Restrained on every surface of both clients. No screen is promoted
  to Committed.
- **D-B.** The accent is banned from backgrounds, section headers, dividers, the wordmark, empty
  states, non-category tiles, and every inactive state (5.7).
- **D-C.** P2 is a transient plane only. Nothing is P2 at rest (4.1).
- **D-D.** Maximum one card per screen; the ledger row is the universal unit (3.3, F2).
- **D-E.** Maximum 7 rows per group, 4 groups per screen, 2 levels below a tab, 1 display figure
  per screen (7.3).
- **D-F.** Green and amber can never be actions (5.3).
- **D-G.** No italic anywhere; the vendored variable font has no italic master (6.2).
- **D-H.** The following drawables are deleted rather than restyled: `bg_home_gradient.xml`,
  `bg_home_gradient_mono.xml`, `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`,
  `bg_nav_header.xml`, `bg_bottom_nav_scrim.xml`, and the orphan `font/montserrat_thin.ttf`.

### 11.2 Needs an owner decision, in `00-rules.md` section 18 row format

| Date | Decision | Rule affected |
|---|---|---|
| pending | **D-1.** The Russian UI face is `<Golos Text / Onest / platform face>`, vendored identically to Android and desktop, because the vendored Space Grotesk binary contains zero Cyrillic codepoints and the Russian UI is currently rendered by an undeclared per-OS fallback | 5.1, 3.4 |
| pending | **D-2.** Space Grotesk is scoped to digits, units, currency, Latin technical tokens, chip labels and the wordmark, and is never applied to a Russian string | 5.1, 3.4 |
| pending | **D-3.** The `zero` (slashed zero) feature is on for technical figures and off for currency, identically on both platforms, replacing the current desktop-only use | 5.5 |
| pending | **D-4.** `TextAppearance.App.Numeric` gains `android:textFontWeight="500"` to match the ramp, and `res/font/space_grotesk.xml` gains explicit `android:fontVariationSettings` per entry if the 6.3 verification confirms the default-instance risk | 3.4, 5.4 |
| pending | **D-5.** Coloured icon tiles are a closed category system of at most four categories; every other row uses `icon_tile_neutral`. `icon_tile_purple` / `icon_purple` are deleted: they resolve to the same hex as blue (`#334C8DFF` / `#4C8DFF`), so the 10+ rows using `@drawable/bg_icon_purple` in `layout_settings_content.xml`, `activity_local_proxy.xml`, `activity_provider_settings.xml` and `activity_backup.xml` are a category that does not exist | 3.6 |

Nothing in 11.2 is implemented until the row is pasted into `00-rules.md` section 18 and the rule
body is updated there.

---

## 12. Acceptance: is a screen on direction?

Run this before calling any screen done. It is deliberately mechanical where it can be.

### 12.1 Mechanical (must return nothing)

```bash
# Android, from /home/user/dp/V2rayNG/app/src/main
grep -rn "<gradient" res/drawable*/                       # F1: no gradients survive
grep -rn "android:textAllCaps=\"true\"" res/              # eyebrow ban
grep -rn "android:textSize" res/layout/                   # roles, not sizes (00-rules 5.2)
grep -rn "android:textStyle=\"bold\"" res/layout/ res/values/styles.xml   # no synthetic bold
grep -rn "android:elevation\|app:cardElevation=\"[1-9]" res/layout/       # no shadows
grep -rn "montserrat" res/ java/                          # F3: orphan face gone
grep -rn "—\|–" res/values*/strings*.xml                  # F19: no em or en dash
grep -rLn "fontFeatureSettings" $(grep -rl "App.Numeric" res/layout/)     # F6: tnum applied

# Desktop, from /home/user/v2rayN/v2rayN/v2rayN.Desktop
grep -rn "LinearGradientBrush\|BoxShadow\|ExperimentalAcrylic" Views/ Assets/
grep -rn "FontFamily=\"{DynamicResource Font.Grotesk}\"" Views/          # F5: only in brand slots
grep -rn "—\|–" Views/ Assets/
```

### 12.2 By eye, with the screenshot in front of you

1. **Count the blue.** One filled accent surface at most, three tinted elements at most, zero on a
   settings screen. If you cannot find the primary action in half a second, the count is wrong.
2. **Count the planes.** No more than three including ground. Nothing raised at rest.
3. **Measure the text origin.** Every title and every hairline starts at 68dp. Put a ruler on the
   screenshot.
4. **Count the gaps.** Are there at least three distinct values? If every gap is 16, the screen
   has no rhythm.
5. **Count the cards.** One, or zero.
6. **Find a number.** Is it in the figure face, right-aligned, tabular? Change it from `1` to `8`
   in the layout preview and confirm nothing moves.
7. **Find a Russian string set in Space Grotesk.** There must not be one.
8. **Squint.** The hierarchy should survive: one heading, one action, a list. If everything
   greys into one texture, the weights are wrong.
9. **Ten states.** Default, pressed, focused, disabled, selected, loading, empty, error, offline,
   first-run. Screenshot each. Missing states are missing work, not edge cases.
10. **Crop the wordmark.** Do the three signatures still identify the product? Figure face,
    single lit element, hairline ledger. If not, the screen is generic and it will look generic
    on a store page next to eleven competitors.
11. **The category question.** Could this screenshot be any dark VPN app? If yes, name which of
    the three signatures is missing and add it.
12. **200% font scale, 320dp width, 900x600 window.** No clipping, no truncated primary label, no
    horizontal scroll.

---

## 13. One-paragraph summary for anyone joining mid-project

Departament VPN is an instrument, not a storefront. Pure near-black planes with no shadows and no
gradients; one blue, spent on the single thing the screen wants you to press, and spent nowhere at
all on most screens; a 56dp hairline-ruled row at a 68dp text origin as the repeating unit of the
entire product on both platforms; every quantity set in Space Grotesk at fixed tabular 620/1000
advances so numbers are the brand and never move; all Russian prose in one Cyrillic-capable
grotesque, because the brand face physically cannot draw Cyrillic; a comfortable density with a
four-value spacing melody instead of a uniform 16dp drone; and motion that acknowledges in 90ms,
changes state in 220ms, and performs exactly once in the whole product, at the instant the tunnel
confirms.
