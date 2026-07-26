# Recon — `ui-ux-pro-max` + supporting design skills → actionable rules for departament VPN (Android)

**Agent:** recon-skill-promax
**Date:** 2026-07-26
**Scope read:** `/home/user/dp/.claude/skills/ui-ux-pro-max/**`, `ui-ux-design-system/**`,
`ui-ux-styling/**`, `ui-ux-brand/**`, `ui-ux-design/**` — every `SKILL.md` and every reference
file that carries transferable rules. Cross-referenced against the app's shipped tokens
(`V2rayNG/app/src/main/res/values/**`, `values-night/**`) and `docs/design-system-2026.md`.

**What this document is:** a rules digest. Every rule below is (a) traced to a line in a
vendored skill file, and (b) translated into what it concretely means for a **dark-first,
single-blue-accent, Russian-language, native Android (XML/Material3) VPN client**. Rules that
do not transfer are listed explicitly in §16 so nobody wastes time on them again.

---

## 0. Inventory of what is actually vendored (and what is not)

| Skill | Path | What is present | What the SKILL.md promises but is **missing** |
|---|---|---|---|
| `ui-ux-pro-max` | `.claude/skills/ui-ux-pro-max/` | **`SKILL.md` only** (703 lines, 47 KB) | `scripts/search.py` (cited at SKILL.md:368, 379, 421, 436, 444, 470, 533, 542, 545, 551, 564, 567), `data/stacks/` (cited at SKILL.md:497), `ui-reasoning.csv` (SKILL.md:373) |
| `ui-ux-design-system` | `.claude/skills/ui-ux-design-system/` | `SKILL.md` + 7 `references/*.md` | `scripts/generate-tokens.cjs`, `validate-tokens.cjs`, `search-slides.py`, `slide-token-validator.py`, `fetch-background.py` (SKILL.md:55, 59, 84-92); `templates/design-tokens-starter.json` (SKILL.md:96-98); all `data/slide-*.csv` (SKILL.md:136-147) |
| `ui-ux-styling` | `.claude/skills/ui-ux-styling/` | `SKILL.md` + 7 `references/*.md` + `canvas-fonts/` (27 files) + `scripts/` | `scripts/shadcn_add.py`, `scripts/tailwind_config_gen.py` (SKILL.md:215, 222) — `scripts/` contains only `requirements.txt` and `tests/requirements.txt`. `canvas-fonts/` holds **27 OFL licence `.txt` files, zero font binaries**; Space Grotesk is not among them |
| `ui-ux-brand` | `.claude/skills/ui-ux-brand/` | `SKILL.md` + 10 `references/*.md` + `templates/brand-guidelines-starter.md` | `scripts/inject-brand-context.cjs`, `sync-brand-to-tokens.cjs`, `validate-asset.cjs`, `extract-colors.cjs` (SKILL.md:26-40, 80-85) |
| `ui-ux-design` | `.claude/skills/ui-ux-design/` | `SKILL.md` + 18 `references/*.md` | all of `scripts/logo/*`, `scripts/cip/*`, `scripts/icon/generate.py` (SKILL.md:279-291); also needs `GEMINI_API_KEY` (SKILL.md:304) |

### 0.1 Operational consequence — do not try to run the CLI

`ui-ux-pro-max/SKILL.md:363-372` says *"Always start with `--design-system`"* and the entire
Step 2/3/4 workflow (SKILL.md:353-472) is built on `python3 scripts/search.py`. **That script
does not exist in this checkout.** The "161 colour palettes / 57 font pairings / 161 product
types / 99 UX guidelines / 25 chart types" database advertised in the frontmatter
(SKILL.md:3, 8) is **not vendored**.

**Therefore:** the **Quick Reference §1–§10 in `SKILL.md:65-300`** *is* the usable database —
approximately 190 named, one-line rules with platform provenance (Apple HIG / Material Design /
WCAG / Core Web Vitals). Treat it as the authoritative rule list. The only other colour/typography
"data" actually present in the vendored tree is:

- `ui-ux-brand/references/color-palette-management.md:152-179` — 3 example palettes (Tech/SaaS, Marketing, Corporate)
- `ui-ux-design/references/logo-color-psychology.md:5-51` — colour psychology per hue, `:53-63` industry pairing table, `:86-101` 5 quick palettes
- `ui-ux-brand/references/typography-specifications.md:26-44` — one 1.25-ratio type scale, `:181-197` 4 font pairings
- `ui-ux-design/references/icon-design.md:57-76` — 15 icon styles, `:78-92` 12 icon categories

That is the whole of the "reference data". Everything else is prose rules.

### 0.2 Skill routing for this repo

`ui-ux-design/references/design-routing.md:18-47` gives the split. Reduced to what matters here:

| Task on this repo | Skill to open | Why |
|---|---|---|
| New/changed screen, component, state, interaction | `ui-ux-pro-max` Quick Reference §1-§10 (`SKILL.md:65-300`) + §"Common Rules" (`SKILL.md:604-660`) | The only complete mobile rule list vendored |
| Adding/renaming a colour, dimen, style, motion token | `ui-ux-design-system/references/token-architecture.md` + `semantic-tokens.md` + `component-tokens.md` | Three-layer discipline, naming convention |
| Defining a component's full state matrix | `ui-ux-design-system/references/states-and-variants.md` + `component-specs.md` | State priority, sizes, contrast floors |
| Contrast / TalkBack / reduced-motion questions | `ui-ux-pro-max/SKILL.md:67-82` + `ui-ux-styling/references/shadcn-accessibility.md:359-447` | WCAG numbers + a checklist |
| Russian copy voice, brand consistency sweep | `ui-ux-brand/references/voice-framework.md` + `consistency-checklist.md` | Voice spectrums, audit list |
| Drawing/altering a vector icon | `ui-ux-design/references/icon-design.md:94-102` | SVG/VectorDrawable hygiene |
| Anything logo / CIP / slides / banners / social photos | **Skip** — `ui-ux-design/SKILL.md:40-241` | Out of scope for in-app UI; all generators are missing anyway |

---

## 1. The binding priority order

`ui-ux-pro-max/SKILL.md:52-63` ranks 10 rule categories by impact. This is the order to resolve
conflicts in — when two rules collide, the lower-numbered category wins.

| # | Category | Impact | Must-have checks (SKILL.md:52-63 verbatim columns) | Anti-patterns |
|---|---|---|---|---|
| 1 | Accessibility | **CRITICAL** | Contrast 4.5:1, alt text, keyboard nav, aria-labels | Removing focus rings; icon-only buttons without labels |
| 2 | Touch & Interaction | **CRITICAL** | Min 44×44px, 8px+ spacing, loading feedback | Hover-only reliance; instant (0 ms) state changes |
| 3 | Performance | HIGH | Lazy loading, reserve space (CLS < 0.1) | Layout thrashing |
| 4 | Style Selection | HIGH | Match product type, consistency, SVG icons (no emoji) | Mixing flat + skeuomorphic; emoji as icons |
| 5 | Layout & Responsive | HIGH | Mobile-first, no horizontal scroll | Horizontal scroll; fixed px widths; disabling zoom |
| 6 | Typography & Colour | MEDIUM | Base 16px, line-height 1.5, **semantic colour tokens** | Body < 12px; gray-on-gray; **raw hex in components** |
| 7 | Animation | MEDIUM | 150–300 ms, motion conveys meaning, spatial continuity | Decorative-only motion; animating width/height; no reduced-motion |
| 8 | Forms & Feedback | MEDIUM | Visible labels, error near field, progressive disclosure | Placeholder-only labels; errors only at top |
| 9 | Navigation | HIGH | Predictable back, bottom nav ≤ 5, deep linking | Overloaded nav; broken back; no deep links |
| 10 | Charts & Data | LOW | Legends, tooltips, accessible colours | Colour as the only channel |

**Decision gate (`SKILL.md:46`):** *"If the task will change how a feature looks, feels, moves, or
is interacted with, this Skill should be used."* — i.e. every ticket in the current
redesign/settings/account backlog is in scope.

---

## 2. Layer 1 — Non-negotiables (Accessibility + Touch)

These two categories are CRITICAL; nothing below §2 may be traded against them.

### 2.1 Contrast floors

Sources: `ui-ux-pro-max/SKILL.md:69, 172, 622, 641-647`; `ui-ux-design-system/references/states-and-variants.md:211-218`; `ui-ux-styling/references/shadcn-accessibility.md:363-366`; `ui-ux-brand/references/color-palette-management.md:75-79`.

| Element | Minimum ratio | Source |
|---|---|---|
| Body text (normal) | **4.5:1** (AA); 7:1 = AAA | pro-max:69; states-and-variants.md:214; color-palette-management.md:78 |
| Large text (≥18 px / ~18sp) | **3:1** (AA); 4.5:1 = AAA | pro-max:69; states-and-variants.md:215 |
| **Dark-mode primary text** | **≥4.5:1** on dark surfaces | pro-max:643 |
| **Dark-mode secondary text** | **≥3:1** on dark surfaces | pro-max:643 |
| UI components / larger glyphs | **3:1** | states-and-variants.md:216; pro-max:622 |
| Focus indicator | **3:1** | states-and-variants.md:217 |
| Icons (small elements) | **4.5:1**; larger UI glyphs 3:1 | pro-max:622 |
| Data lines/bars vs background | **3:1**; data text labels 4.5:1 | pro-max:286 |
| Error/success state colours | **4.5:1** | pro-max:237 |

**Android translation.** Dark mode must be contrast-verified **independently** — `pro-max:599`
("Check dark mode contrast independently — don't assume light mode values work") and
`pro-max:689` ("Both themes are tested before delivery, not inferred from a single theme").
The repo already does this correctly once: `values-night/colors.xml:62-65` documents lifting
`md_theme_onSurfaceVariant` from `#8A909C` → `#9BA1AD` precisely because the old value sat at
the AA edge for the 12–13sp `Subtitle`/`Caption` roles. **That comment is the template** — every
future colour change in `values-night/colors.xml` should carry the same "measured against which
surface, at which ratio" note.

**Concrete standing checks for this app:**
- `TextAppearance.App.Subtitle` (13sp, `styles.xml:95-99`) and `.Caption` (12sp, `styles.xml:102-106`)
  both resolve to `?attr/colorOnSurfaceVariant`. In dark that is `#9BA1AD`
  (`values-night/colors.xml:65`). These are *normal* text, not large text → they need **4.5:1**,
  not 3:1. Verify against **every** surface they land on, not just `md_theme_surface`: the ramp
  runs `#08090B` → `#20242B` (`values-night/colors.xml:80-84`), and `#20242B`
  (`surfaceContainerHighest`, also `icon_tile_neutral` at `values-night/colors.xml:27`) is the
  worst case.
- `chip_type_text` in dark is the raw accent `#4C8DFF` (`values-night/colors.xml:19`) at **11sp**
  (`TextAppearance.App.Chip`, `styles.xml:109-114`). 11sp is well below "large text" — it needs
  4.5:1, and a saturated blue on a dark chip fill is the classic failure. Measure it.
- `ping_bad` `#F04452` / `ping_good` `#22C55E` (`values-night/colors.xml:22-23`) carry meaning by
  hue. `pro-max:77` (`color-not-only`) and `pro-max:173` forbid colour-as-sole-channel → the ping
  value must always be accompanied by the numeric ms text (it is) **and** the good/bad split must
  survive greyscale (`logo-color-psychology.md:83` — "test in grayscale for clarity").

### 2.2 Touch targets and spacing

Sources: `ui-ux-pro-max/SKILL.md:86-87, 100, 233, 620, 633`.

- **Minimum 48×48dp on Android** (44×44pt iOS) — `pro-max:86`, `:633`. Extend the hit area beyond
  the visual bounds when the glyph is smaller (`pro-max:86` "extend hit area beyond visual bounds
  if needed"; `:620` "use hitSlop if icon is smaller").
- **Minimum 8dp gap between adjacent targets** — `pro-max:87`.
- **Never require pixel-perfect taps** on small icons or thin edges — `pro-max:100`.
- **Text inputs ≥44px tall** — `pro-max:233`.

**Applied to the shipped tokens (`values/dimens.xml`):**

| Token | Value | Verdict |
|---|---|---|
| `row_min_height` (dimens.xml:33) | 56dp | ✅ Exceeds the 48dp floor; matches `component-specs.md:237` "row height (comfortable) = 56px" |
| `tile_size` (dimens.xml:31) | 40dp | ⚠️ **Below 48dp.** Legal only because the tile is *decoration inside* a 56dp row — the **row** is the target. **Rule: a 40dp tile must never itself be the clickable/focusable view.** If a tile ever needs its own tap (e.g. a per-row action), give it a 48dp `minWidth`/`minHeight` or a `TouchDelegate`. |
| `tile_glyph` (dimens.xml:32) | 22dp | ✅ Consistent glyph size — satisfies `pro-max:617` "Define icon sizes as design tokens… avoid mixing 20/24/28 randomly" |
| `dot_size` / `dot_size_active` (dimens.xml:38-39) | 6dp / 8dp | ✅ **only** as non-interactive indicators. Making page dots tappable would violate `pro-max:86` and `:100` outright — page changes must come from the swipe, not the dots |
| `space_8` (dimens.xml:15) | 8dp | ✅ Exactly the `pro-max:87` inter-target minimum — use `space_8` as the *floor* gap between any two adjacent controls |

### 2.3 Screen-reader / Dynamic Type / reduced motion

- **Every icon-only control needs a label** — `pro-max:72` (`aria-label` / `accessibilityLabel`),
  `:54` (anti-pattern: "icon-only buttons without labels"), `:700` (checklist: "All meaningful
  images/icons have accessibility labels"). → Android: `android:contentDescription` on every
  `ImageButton`/`ImageView` that carries meaning; `null` on purely decorative tiles.
- **Reading order must match visual order** — `pro-max:73`, `:80`, `:631`, `:681`. → Android:
  verify TalkBack traversal after any `ConstraintLayout` reorder; use
  `android:accessibilityTraversalAfter` when the visual order and the XML order diverge.
- **Announce traits/roles/states** — `pro-max:704` ("selected, disabled, expanded are announced
  correctly"). → Android: `View.setSelected`/`isEnabled` (which map to TalkBack states)
  rather than only recolouring.
- **Dynamic Type** — `pro-max:78` ("support system text scaling; avoid truncation as text grows"),
  `:598` ("Verify… Dynamic Type at largest size"), `:703`. → Android: **sp everywhere for text**
  (the app already does — `styles.xml:59, 68, 77, 89, 96, 103, 112`), never a fixed `layout_height`
  on a text row (`row_min_height` is a *min*, which is correct), and test at `fontScale` 1.3 and 1.5.
- **Reduced motion** — `pro-max:79`, `:281`, `:703`; `shadcn-accessibility.md:411-430`. → Android:
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0`. The repo already documents this contract in
  `values/motion.xml:6-9` and points at `MotionUtils` — **all new animations must route through
  that helper**, not call `animate()` with a literal duration.
- **Escape routes** — `pro-max:81` ("provide cancel/back in modals and multi-step flows"). Applies
  to every bottom sheet and every multi-step auth/purchase flow.

---

## 3. The three-layer token architecture, mapped onto Android XML

Source: `ui-ux-design-system/references/token-architecture.md:5-26` (diagram + rationale),
`:137-147` (naming), `:149-158` (categories), `:186-207` (migration from flat tokens);
`ui-ux-design-system/SKILL.md:29-49`.

```
Component tokens   --button-bg, --card-padding      per-component overrides
        ↑
Semantic tokens    --color-primary, --spacing-…     purpose aliases; THE theme-switch layer
        ↑
Primitive tokens   --color-blue-600, --space-4      raw values; change rarely
```

`token-architecture.md:22-26`: primitives change **rarely**; semantics change **on theme switch**;
component tokens change **per component need**. `:124-135`: **dark mode overrides the semantic
layer only** — never the primitives, never the components.

### 3.1 The Android equivalent of each layer

| Layer | Android home | Present in this repo? |
|---|---|---|
| **Primitive** | `<color name="brand_blue">#1E5FC7`, raw `<dimen>` values | ✅ `values/colors.xml:4-6` (brand), `:8-24` (raw hues); `values/dimens.xml:14-19` (space), `:22-28` (radius) |
| **Semantic** | `md_theme_*` roles + theme `?attr/` (`colorPrimary`, `colorOnSurfaceVariant`, `colorOutlineVariant`…) | ✅ `values/colors.xml:53-96` (light) and `values-night/colors.xml:31-84` (dark) — **the dark override lives exactly where the skill says it should** |
| **Component** | `values/styles.xml` `TextAppearance.App.*`, widget styles, and the named `<dimen>` component tokens | ✅ `values/dimens.xml:31-34` (`tile_size`, `tile_glyph`, `row_min_height`, `screen_gutter`), `:37-40` (account hero); `styles.xml:56-127` |

**This mapping is already sound.** The architecture is not the problem; the leaks are (§17).

### 3.2 The hard rules from the token layer

1. **No raw hex in components.** `pro-max:59` ("Raw hex in components" is a listed anti-pattern),
   `:170` (`color-semantic`), `:646` (`token-driven theming` — "Hardcoded per-screen hex values"
   is the Don't), `design-system/SKILL.md:239` ("Never use raw hex in components — always
   reference tokens"), `semantic-tokens.md:193-206` (good/bad example).
   → **Android:** every layout/drawable references `?attr/…` or `@color/md_theme_…`, never
   `#RRGGBB`. A literal hex in `res/layout/**` or `res/drawable/**` is a defect.
2. **Components reference the *semantic* layer, not primitives.** `semantic-tokens.md:193-206`
   marks `background: var(--color-gray-50)` as **Bad** and `var(--color-card)` as **Good**.
   → **Android:** a row background should be `?attr/colorSurfaceContainer`, not `@color/brand_cream`.
3. **Naming = `{category}-{item}-{variant}-{state}`** — `token-architecture.md:139-147`.
   Names describe **purpose**, not the hue that happens to be in the slot today. (See §17.2 for
   `icon_purple`, which violates this.)
4. **Categories are fixed** — `token-architecture.md:149-158`: `color`, `space`, `font-size`,
   `radius`, `shadow`, `duration`. → The repo has `space_*`, `radius_*`, `motion_*` (duration).
   It is **missing an `elevation`/`shadow` scale** (§17.5).

### 3.3 Primitive scales the skill actually specifies

Use these as the reference ladders when a new token is needed
(`ui-ux-design-system/references/primitive-tokens.md`):

- **Spacing, 4px base** (`primitive-tokens.md:63-91`): 0, 1px, 2, 4, 6, 8, 10, 12, 14, **16**, 20,
  **24**, 28, **32**, 36, 40, **48**, 56, **64**, 80, **96**.
  Reinforced by `pro-max:150` ("4pt/8dp incremental spacing system"), `:656` ("Use a consistent
  4/8dp spacing system"), `:658` ("Define clear vertical rhythm tiers (e.g. 16/24/32/48)").
- **Type sizes** (`primitive-tokens.md:96-106`): 12 / 14 / 16 / 18 / 20 / 24 / 30 / 36 / 48.
  `pro-max:166` gives the mobile-relevant subset: **12 14 16 18 24 32**.
- **Line heights** (`primitive-tokens.md:108-114`): 1 / 1.25 / 1.375 / **1.5** / 1.625 / 2.
- **Weights** (`primitive-tokens.md:116-120`): 400 / 500 / 600 / 700.
- **Radius** (`primitive-tokens.md:131-145`): 0 / 2 / 4 / 6 / 8 / 12 / 16 / 24 / 9999.
- **Durations** (`primitive-tokens.md:166-184`): 75 / 100 / 150 / 200 / 300 / 500 / 700 / 1000,
  with semantic aliases fast=150, normal=200, slow=300.
- **Z-index** (`primitive-tokens.md:186-203`): 0/10/20/30/40/50 then dropdown 1000, sticky 1100,
  modal 1200, popover 1300, tooltip 1400. `pro-max:153` demands a defined layered scale.

### 3.4 Semantic tokens the skill expects to exist

`ui-ux-design-system/references/semantic-tokens.md`:

- Surfaces: `background`/`foreground`, `card`/`card-foreground`, `popover`/`popover-foreground` (`:9-22`)
- `primary` + `primary-hover` + `primary-active` + `primary-foreground` (`:27-33`) — **note the
  three-step accent ramp**; Android's `colorPrimary` alone is not enough for pressed/active states
- `secondary`, `muted` (+`muted-foreground`), `accent`, `destructive` (`:38-71`)
- Status: `success` / `warning` / `error` / `info` with paired foregrounds (`:76-89`)
- `border`, `input`, `ring` (`:94-99`)
- Spacing semantics: `spacing-component-{xs,sm,,lg}`, `spacing-section-{sm,,lg}`,
  `spacing-page-x/y` (`:104-120`)
- Typography semantics: `font-heading{,-lg,-xl}`, `font-body{,-sm,-lg}`, `font-label`,
  `font-caption` (`:125-140`)
- **Interactive-state semantics** (`:145-159`): `ring-width: 2px`, `ring-offset: 2px`,
  `ring-color`, **`opacity-disabled: 0.5`**, and named transition property groups

**Gap in this app:** there is no semantic `opacity-disabled`, no `ring-*` group, and no
`spacing-section`/`spacing-page` naming — screens hardcode `space_16`/`space_24` directly.
That is tolerable (Android convention), but the **disabled opacity must be tokenised**, because
`pro-max:216` pins it to a numeric band (§9.3).

---

## 4. Colour — rules for a dark, single-accent system

### 4.1 The governing rules

| Rule | Source | Meaning here |
|---|---|---|
| Dark mode uses **desaturated / lighter tonal variants, not inverted colours**; test contrast separately | `pro-max:171` | `brand_blue` correctly shifts `#1E5FC7` (light, `colors.xml:4`) → `#4C8DFF` (dark, `values-night/colors.xml:4`) — a *lighter, tonal* variant, exactly per rule. Keep this pattern for any new hue |
| Design light/dark variants **together** | `pro-max:136` (`dark-mode-pairing`) | Never add a `values/colors.xml` entry without its `values-night` counterpart in the same change |
| Functional colour must carry an icon/text too | `pro-max:77`, `:173`, `:222` (state indicators), `states-and-variants.md:222-225` | Connected/disconnected, ping good/bad, subscription active/expired — all need a glyph or word, never hue alone |
| Borders/dividers must be visible **in both themes** | `pro-max:644` | `divider_color_light` is `#E4E9F2` light (`colors.xml:25`) / `#20242B` dark (`values-night/colors.xml:16`) — both present ✅. But the *name* lies (§17.2) |
| Interaction states must be equally distinguishable in both themes | `pro-max:645` (`state contrast parity`) | Pressed/selected/disabled must be re-verified on `#0A0B0D` background, not just on `#F4F7FC` |
| Modal scrim strong enough to isolate foreground — **typically 40–60 % black** | `pro-max:647`, `:688`; `component-tokens.md:156` (`--dialog-overlay-bg: rgb(0 0 0 / 0.5)`) | `md_theme_scrim` is `#000000` in both themes (`colors.xml:96`, `values-night/colors.xml:77`); **verify the applied alpha for dialogs and bottom sheets lands in 0.40–0.60** |
| Limit the palette: **2–5 colours**, cohesive, each shade carries meaning | `canvas-design-system.md:150-154` | Reinforces the CLAUDE.md "ONE accent" law from a second direction |
| Colour ratio budget: primary 60–70 %, secondary 20–30 %, **accent 5–10 %**; ≤20 % off-palette | `color-palette-management.md:114-121` | On a near-black UI, "primary 60-70 %" = the neutral surface ramp; **blue is the 5-10 % accent**. If blue occupies more than ~10 % of a screen's pixels, it has stopped being an accent |
| Don't use more than 2–3 colours in a single component | `color-palette-management.md:143` | A settings row = surface + text + at most one accent. Never surface + blue + green + amber in one row |
| Never use pure `#000` for text | `color-palette-management.md:149` | (Background pure-dark is fine and intentional; this is about *text*) |
| Blue = trust, stability, professionalism, calm; industries: finance, healthcare, **tech**, corporate; pairs with white/light gray | `logo-color-psychology.md:5-10`, `:57` | Independent confirmation that ONE blue accent is the right call for a security/VPN product |
| Semantic naming: use `destructive`, not `red`; `muted`, not `gray` | `shadcn-theming.md:370` | See §17.2 |
| HSL/oklch stored **without** the wrapper so opacity can be composed | `shadcn-theming.md:140-151`; `design-system/SKILL.md:243` | Android has no direct equivalent; the app instead pre-bakes alpha into hex (`icon_tile_blue` `#334C8DFF`, `colors.xml:38`). Acceptable, but it means every alpha variant is a *new* token — keep the count small |

### 4.2 The one-accent discipline, restated in skill terms

`pro-max:140` (`primary-action`): **"Each screen should have only one primary CTA; secondary
actions visually subordinate."** Combined with the 5-10 % accent budget
(`color-palette-management.md:118`), this yields the operating rule for this app:

> **Blue is spent on exactly one thing per screen.** On Home that is the connect control.
> On Account it is the single purchase/renew CTA. Everywhere else blue is reserved for
> *selected state* and *links*. A screen with two blue-filled buttons is a defect.

The repo already invented the correct escape hatch: `icon_tile_neutral` / `icon_glyph_neutral`
(`colors.xml:45-50`, `values-night/colors.xml:25-28`) with the comment *"Spends no accent budget
so default/utility rows stay quiet and the blue reads as the only accent."* — that is a literal
implementation of `color-palette-management.md:114-121`. **Every new settings row should default
to the neutral tile**; a coloured tile requires a justification.

---

## 5. Typography

### 5.1 Rules

| Rule | Source |
|---|---|
| Body line-height **1.5–1.75** (headings 1.1–1.3; small text 1.4–1.5; long-form 1.6–1.75) | `pro-max:163`; `typography-specifications.md:79-85` |
| Line length: **mobile 35–60 chars**, desktop 60–75 | `pro-max:148`; `:164` gives 65-75; `typography-specifications.md:112-114` |
| Consistent type scale, e.g. 12 14 16 18 24 32 | `pro-max:166` |
| Use the **platform type system** — Material's 5 type roles (display, headline, title, body, label) | `pro-max:168` |
| Weight hierarchy: **bold headings 600–700, regular body 400, medium labels 500** | `pro-max:169`; `typography-specifications.md:64-75` |
| Respect **default platform letter-spacing**; avoid tight tracking on body text | `pro-max:175` |
| **Tabular/monospaced figures** for data columns, prices, timers — prevents layout shift | `pro-max:176` |
| **Prefer wrapping over truncation**; if truncating, ellipsis + full text reachable | `pro-max:174` |
| Whitespace groups related items and separates sections | `pro-max:177` |
| Locale-aware number/date/currency formatting | `pro-max:283` |
| Don't set long text in all caps; don't justify; don't use thin weights (<400) at small sizes | `typography-specifications.md:211-214` |
| Minimum sizes: body 16px, small 14px (not for long content), caption 12px "use sparingly" | `typography-specifications.md:201-204` |

### 5.2 How the shipped ramp scores

`values/styles.xml:56-127`:

| Style | Size / weight / tracking | Verdict against the rules |
|---|---|---|
| `.Display` (`:56-62`) | 34sp, w700, −0.02 tracking, Space Grotesk | ✅ Heading weight in the 600-700 band (`pro-max:169`); tight tracking is legitimate at display size (`typography-specifications.md:92`: Display → −0.02em) |
| `.Headline` (`:65-71`) | 24sp, w700, −0.01 | ✅ |
| `.Title` (`:74-80`) | 16sp, w700, 0.0 | ✅ Tracking 0 for headings matches `typography-specifications.md:93` |
| `.Title.Medium` (`:83-85`) | 16sp, w500 | ✅ Medium = labels/soft titles (`pro-max:169`) |
| `.Body` (`:88-92`) | 14sp, +0.01 | ⚠️ See §17.3 — 14sp vs the skill's 16px body minimum |
| `.Subtitle` (`:95-99`) | 13sp, muted | ⚠️ Below the 14px "small text" floor of `typography-specifications.md:203`; acceptable on Android only if 4.5:1 holds (§2.1) |
| `.Caption` (`:102-106`) | 12sp, +0.02, muted | ✅ At the floor (`pro-max:59` forbids body < 12px); `typography-specifications.md:204` says "use sparingly" — treat Caption as metadata only, never as a place to hide real information |
| `.Chip` (`:109-114`) | 11sp, w500, +0.04 | ⚠️ **11sp is below every stated floor.** Legal only because chips are short single-token labels, not reading text — but they then need the *large-glyph* treatment: high contrast (4.5:1, §2.1) and generous tracking (which +0.04 provides). Do not put sentences in a Chip |
| `.Numeric` (`:122-127`) | Space Grotesk + `"tnum" on, "lnum" on` | ✅✅ Direct implementation of `pro-max:176` (`number-tabular`). **Mandatory for**: traffic counters, ping ms, balance/₽ prices, countdown timers, device counts |

**Russian-language addendum (derived, not stated in the skills but forced by them):** Russian
strings run materially longer than English for the same meaning. Combined with `pro-max:174`
("prefer wrapping over truncation") and `pro-max:78` ("avoid truncation as text grows"), the rule
for this app is:

> Row titles get `maxLines="2"` + `ellipsize="end"`, **not** `singleLine="true"`. Any value that
> can be ellipsised must be reachable in full somewhere (detail screen, long-press, or the sheet
> that the row opens). Test every settings screen at `fontScale=1.5` with the longest Russian
> string in `res/values/strings_*.xml`.

Currency and numbers: `pro-max:283` → format ₽ with the ru-RU locale (comma decimal separator,
non-breaking space as the thousands separator, ₽ **after** the number) and render through
`.Numeric` so the digits don't jitter.

---

## 6. Layout, spacing and hierarchy

| Rule | Source | Applied |
|---|---|---|
| **One** 4/8dp spacing rhythm across component, section and page levels | `pro-max:150`, `:656`, `:696` | `space_4/8/12/16/24/32` (`dimens.xml:14-19`) is the scale. §17.1 flags the surviving second scale |
| Vertical rhythm **tiers** by hierarchy — 16/24/32/48 | `pro-max:658` | 16 intra-card, 24 between sections, 32 hero rhythm. 48 has no token yet (`dimens.xml` stops at 32) |
| Consistent content width per device class; adaptive gutters that grow on larger widths and in landscape | `pro-max:152`, `:655`, `:659`, `:695` | `screen_gutter` = 16dp (`dimens.xml:34`). Needs a `sw600dp` override (the doc plans 24dp — `design-system-2026.md:160-161`) |
| **Safe areas / system-bar clearance** for headers, tab bars, bottom CTA bars | `pro-max:99`, `:653-654`, `:692` | Edge-to-edge insets on the bottom nav and any bottom CTA; nothing tappable under the gesture bar |
| Scroll content must not hide behind fixed/sticky bars — reserve insets | `pro-max:154`, `:660`, `:693` | Bottom padding on every scrolling list equal to the bottom-nav height |
| Avoid nested scroll regions that fight the main scroll | `pro-max:155` | No horizontal `RecyclerView` inside a vertical one without a deliberate reason; the subscription carousel is the one sanctioned exception (`dimens.xml:36-40`) |
| Establish hierarchy via **size, spacing, contrast — not colour alone** | `pro-max:159` | Directly compatible with the single-accent rule: hierarchy comes from the type ramp and whitespace, and blue is not a hierarchy tool |
| Keep layout readable and operable in **landscape** | `pro-max:157`, `:694` | Test every screen rotated; the connect hero must not push the status text off-screen |
| Show core content first on mobile; fold/hide secondary | `pro-max:158` | Home = connect + current server + status. Everything else is one level down |
| Define a layered z-index scale | `pro-max:153`; `primitive-tokens.md:186-203` | Android: a documented elevation ladder (§17.5) |
| Nothing overlaps, nothing falls off, margins non-negotiable, breathing room and clear separation | `canvas-design-system.md:186-194` | The "does it look laboured over" bar |

---

## 7. Iconography

Source: `ui-ux-pro-max/SKILL.md:609-622` (full table) plus `:130`, `:137`;
`ui-ux-design/references/icon-design.md:57-76, 94-102`.

1. **No emoji as structural icons** — `pro-max:130`, `:613`, `:670`. *"Emojis are font-dependent,
   inconsistent across platforms, and cannot be controlled via design tokens."* → Zero emoji in
   navigation, settings, status, or empty states.
2. **Vector-only assets** — `pro-max:614`. → `VectorDrawable` only; no PNG icon in `res/drawable*`.
3. **Consistent icon sizing as tokens** — `pro-max:617`: define `icon-sm` / `icon-md (24pt)` /
   `icon-lg`; never mix 20/24/28 arbitrarily. → The app has `tile_glyph` 22dp (`dimens.xml:32`) and
   the legacy `image_size_dp24` (`dimens.xml:6`). **Two glyph sizes with no stated rule = drift.**
   Define: 22dp inside a 40dp tile, 24dp standalone (toolbar/inline), and nothing else.
4. **Stroke consistency** — `pro-max:618`: one stroke width per visual layer (1.5 px or 2 px).
   → Pick 2dp (matches `design-system-2026.md:210`) and audit every `ic_*.xml` against it.
5. **Filled vs outline discipline** — `pro-max:619`: one style per hierarchy level. → Outline at
   rest, filled for the *selected* nav destination is the standard Material pattern and is
   allowed **because they sit at different hierarchy levels (rest vs selected)**. Mixing filled and
   outline among peer settings rows is not.
6. **One icon set / one visual language** (stroke width + corner radius) — `pro-max:137`.
7. **Icon alignment** — `pro-max:621`: align to text baseline, consistent padding.
8. **Icon contrast** — `pro-max:622`: 4.5:1 small, 3:1 for larger UI glyphs.
9. **VectorDrawable hygiene** — `icon-design.md:96-102`: 24×24 viewport (or 16×16 compact),
   `currentColor`-equivalent (→ `android:tint`/`?attr/`, **never a baked-in fill colour**),
   minimal path nodes, `round` linecap/linejoin for outlined styles, **design at 24 and check at
   16 and 48**.
10. Style vocabulary for a security/tech product — `icon-design.md:63, 68` and
    `ui-ux-design/SKILL.md:210-211`: `sharp` ("Tech, fintech, enterprise") or `flat`; `rounded`
    reads "friendly apps, health" and is off-brief for a VPN.

---

## 8. Component specifications

Source: `ui-ux-design-system/references/component-specs.md` and `component-tokens.md`. Web pixel
values below translate 1 px → 1 dp for Android.

### 8.1 Buttons (`component-specs.md:5-46`, `component-tokens.md:5-47`)

**Variants** (`component-specs.md:9-17`): `default` (primary fill), `secondary`, `outline`,
`ghost`, `link`, `destructive`. → Exactly six; do not invent a seventh.

**Sizes** (`component-specs.md:20-25`):

| Size | Height | Pad X | Pad Y | Font | Icon |
|---|---|---|---|---|---|
| sm | 32 | 12 | 6 | 14 | 16 |
| default | 40 | 16 | 8 | 14 | 18 |
| lg | 48 | 24 | 12 | 16 | 20 |
| icon | 40 | 0 | 0 | — | 18 |

⚠️ **Android correction:** the 32dp and 40dp heights are *below* the 48dp touch minimum
(`pro-max:86`, `:633`). On this app, **`lg` (48dp) is the default button height**; `sm`/`default`
may only be used when wrapped in a ≥48dp touch region.

**States** (`component-specs.md:29-36`): default / hover / active(darkest) / focus / **disabled
(muted bg, muted fg, opacity 0.5)** / **loading (opacity 0.7, non-interactive)**.

**Token set to mirror** (`component-tokens.md:9-46`): bg, fg, hover-bg, active-bg per variant,
plus `padding-x/y` in three sizes, `radius`, `font-size`, `font-weight`. → In Android terms: a
`Widget.App.Button.*` style per variant, with a `ColorStateList` covering
`state_enabled=false` / `state_pressed` / `state_focused`.

### 8.2 Inputs (`component-specs.md:50-89`, `component-tokens.md:49-79`)

- Variants: text, textarea, select, checkbox, radio, switch (`component-specs.md:54-61`)
- Sizes: sm 32 / default 40 / **lg 48** (`:65-69`) — again, use 48 on Android (`pro-max:233`)
- States (`:73-79`): default / hover / **focus (primary border + primary/20 % ring)** /
  **error (red border + red/20 % ring)** / **disabled (muted border, muted bg)**
- Anatomy (`:83-89`): **Label above** → field (leading icon, value, trailing action) →
  **helper text or error message below**. This is the mandated order; a placeholder is not a label
  (`pro-max:208`, `:61`).

### 8.3 Cards (`component-specs.md:93-128`, `component-tokens.md:81-102`)

- Variants (`:97-102`): default (sm shadow + 1px border), elevated (lg shadow, no border),
  **outline (no shadow, 1px border)**, interactive (sm→md on press).
  → For a pure-dark theme, **`outline` is the correct default**: dark-mode shadows are invisible,
  so separation comes from `?attr/colorOutlineVariant` hairlines and the surface-container ramp.
  This matches `pro-max:186-187`'s reasoning and `design-system-2026.md:186-187`.
- Anatomy (`:106-118`): header (title + description) / content / footer (actions) — three zones,
  in that order.
- Spacing (`:122-128`): header `24 24 0`, content `24`, footer `0 24 24`, internal gap `16`.
  → On a 16dp-gutter phone layout, scale to `space_16` inner padding + `space_16` gap; `space_24`
  reserved for section separation.
- **Elevation must come from a consistent scale** — `pro-max:135` (`elevation-consistent`:
  "avoid random shadow values").
- **No nested cards** (CLAUDE.md law) — consistent with `pro-max:641` (surfaces must stay clearly
  separated; nesting destroys that) and `:177` (whitespace, not extra chrome, does the grouping).

### 8.4 Badges / chips (`component-specs.md:132-152`, `component-tokens.md:104-130`)

- Variants: default / secondary / outline / destructive / success / warning (`:136-143`)
- Sizes (`:147-151`): sm 20 h / 11 px text; **default 24 h / 12 px / 4×10 pad**; lg 28 h / 14 px
- `--badge-radius: var(--radius-full)` (`component-tokens.md:127`) — pill, i.e. `radius_pill`
- **Badges are never tappable at these heights.** 20–28dp is far below 48dp. If a chip must be
  interactive (filter chips), it is a *button* sized ≥48dp tall in its touch region, not a badge.

### 8.5 Dialogs / sheets (`component-specs.md:177-204`, `component-tokens.md:151-169`)

- Sizes (`:181-187`): sm 384 (simple confirmations) / default 512 / lg 640 / **full = 100 %−32 px
  on mobile**
- Anatomy (`:191-204`): header (title + description + close) / scrollable content / footer with
  actions **right-aligned, `[Cancel] [Confirm]`**
- Overlay 50 % black (`component-tokens.md:156`) — inside the 40-60 % band (`pro-max:647`)
- Radius from a token; padding 24 (`component-tokens.md:165-167`) → `radius_sheet` 24dp
  (`dimens.xml:28`) is already correct for sheet top corners
- **Must offer a clear dismiss affordance; swipe-down to dismiss on mobile** — `pro-max:251`
- **Confirm before dismissing with unsaved changes** — `pro-max:227`
- **Blur is for background dismissal (modals/sheets), never decoration** — `pro-max:139`
- **Modals must not be used for primary navigation flows** — `pro-max:263`

### 8.6 List rows / tables (`component-specs.md:208-237`)

- Row states (`:212-217`): default / hover / **selected = primary at 10 % overlay** / striped
- Alignment (`:221-226`): **text left, numbers right**, status/badge centre, actions right
  → Server rows: name left, ping/latency **right-aligned** (and in `.Numeric`)
- Row heights (`:233-236`): compact 40 / default 48 / **comfortable 56**
  → `row_min_height` = 56dp (`dimens.xml:33`) = the comfortable tier. Correct for touch.

---

## 9. Interaction states — the complete matrix

### 9.1 The state list and its priority

`states-and-variants.md:9-16` defines six states; `:18-27` fixes the resolution order when several
apply:

> **disabled > loading > active(pressed) > focus > hover > default**

On Android there is no hover (except mouse/TV), so the operative ladder is
**disabled > loading > pressed > focused > selected > default**. Encode it in that order in every
`ColorStateList` / `StateListDrawable` — Android picks the *first* matching item, so a
`state_pressed` entry placed above `state_enabled="false"` silently breaks the rule.

### 9.2 What each state must look like

| State | Requirement | Source |
|---|---|---|
| **Pressed** | Visible feedback within **80–150 ms** of touch (and *always* within 100 ms) | `pro-max:629`, `:121`, `:96` |
| **Pressed (form)** | Subtle scale **0.95–1.05** on tappable cards/buttons, restored on release | `pro-max:197` |
| **Pressed (stability)** | Must **not shift layout bounds** or move neighbouring content | `pro-max:615`, `:673` |
| **Focus** | Visible ring, **2–4 px**; ring width 2 / offset 2 / colour = primary; **≥3:1 contrast** | `pro-max:70`; `states-and-variants.md:50-66`, `:217`; `semantic-tokens.md:147-150` |
| **Focus** | **Never remove the focus indicator** | `pro-max:54`; `shadcn-accessibility.md:402-408` |
| **Selected** | Current location/selection visually highlighted via colour **+ weight or indicator** | `pro-max:249`; row selected = primary @10 % (`component-specs.md:215`) |
| **Disabled** | Opacity **0.38–0.5**, non-interactive, semantically disabled (`isEnabled=false`), and it must **not look tappable** | `pro-max:216`, `:632`; `states-and-variants.md:78-95`; `semantic-tokens.md:152` |
| **Disabled** | Still needs **3:1 contrast minimum** | `states-and-variants.md:101` |
| **Read-only** | Visually and semantically **distinct from disabled** | `pro-max:230` |
| **Loading** | Content opacity 0.7, pointer-events off, spinner placed per component: button → replace icon or centre; input → trailing; card → centre overlay; page → viewport centre | `states-and-variants.md:105-129`; `component-specs.md:36` |
| **Loading (button)** | Disable the control during async work; show spinner/progress | `pro-max:89` |
| **Loading (>300 ms)** | Show skeleton or progress indicator | `pro-max:183` |
| **Loading (>1 s)** | **Skeleton/shimmer, not a long blocking spinner** | `pro-max:119` |
| **Error** | Border + text in the error token, icon included, message **below the field**, clears on valid input | `states-and-variants.md:136-160`; `pro-max:209` |
| **Empty** | Helpful message **and an action** | `pro-max:212` |
| **All states** | Must be **equally distinguishable in light and dark** | `pro-max:645`, `:687` |

### 9.3 Concrete Android encoding

- Tokenise disabled alpha: add `<item name="alpha_disabled">0.38</item>` (Material's standard, and
  the low end of `pro-max:216`'s 0.38-0.5 band) rather than sprinkling `android:alpha="0.5"`.
- Every clickable row/tile needs a `StateListDrawable` or `MaterialCardView` `rippleColor` that
  covers **pressed + focused + selected + disabled**. A row that only styles `pressed` fails
  `pro-max:249` (selected) and `:632` (disabled clarity) — and fails TV/D-pad entirely
  (`strings_tv.xml` exists, so D-pad focus is a real surface here).
- Press feedback = the `motion_press_in` 90 ms / `motion_press_out` 160 ms pair
  (`values/motion.xml:12-16`), which sits inside `pro-max:629`'s 80-150 ms window on the way in.
- The scale-on-press (`pro-max:197`, 0.95–1.05) must use `scaleX/scaleY` (a transform), which
  satisfies `pro-max:182` and `:615` (no layout shift) simultaneously.

---

## 10. Motion

### 10.1 Rules

| Rule | Value | Source |
|---|---|---|
| Micro-interactions | **150–300 ms**; complex ≤400 ms; **avoid >500 ms** | `pro-max:181`, `:630` |
| Tap acknowledgement | within **80–150 ms** (visual feedback ≤100 ms) | `pro-max:629`, `:121` |
| Animate **transform/opacity only** | never width/height/top/left | `pro-max:182`, `:204` |
| Easing | **ease-out entering, ease-in exiting**; never linear for UI | `pro-max:185` |
| Spring/physics curves preferred over linear/cubic-bezier | | `pro-max:190` |
| **Exit ≈60–70 % of enter duration** | | `pro-max:191` |
| Stagger list/grid entrance **30–50 ms per item** | | `pro-max:192` |
| **Max 1–2 animated elements per view** | | `pro-max:184` |
| Every animation must express cause→effect; **no decorative motion** | | `pro-max:186`, `:60` |
| Animations must be **interruptible**; a tap cancels in-flight motion immediately | | `pro-max:194` |
| **Never block input** during an animation | | `pro-max:195` |
| State changes animate, don't snap | | `pro-max:187` |
| Screen transitions keep **spatial continuity** (shared element / directional slide) | | `pro-max:188`, `:193`, `:202` |
| **Forward = left/up, backward = right/down**, consistently | | `pro-max:203` |
| Crossfade for content replacement in the same container | | `pro-max:196` |
| Gestures get **real-time** visual response tracking the finger | | `pro-max:198` |
| Unify duration/easing tokens globally — one rhythm | | `pro-max:200` |
| Fading elements must not linger below opacity 0.2 | | `pro-max:201` |
| Respect reduced-motion; parallax sparingly | | `pro-max:79`, `:189`, `:281` |
| Standard transition durations: colour/background 150 ms ease-in-out, transform 200 ms ease-out, opacity 150 ms ease, shadow 200 ms ease-out | | `states-and-variants.md:40-46` |

### 10.2 The shipped motion tokens, scored

`values/motion.xml`:

| Token | ms | Verdict |
|---|---|---|
| `motion_press_in` (`:12`) | 90 | ✅ inside `pro-max:629` (80-150) and under the `pro-max:121` 100 ms tap-feedback ceiling |
| `motion_press_out` (`:16`) | 160 | ✅ release/settle |
| `motion_state` (`:18`) | 220 | ✅ inside `pro-max:181` (150-300) |
| `motion_reveal` (`:20`) | 300 | ✅ at the top of the micro-interaction band |
| `motion_stagger` (`:22`) | 40 | ✅ inside `pro-max:192` (30-50); the file's own cap comment ("total never exceeds ~400 ms") matches `pro-max:181`'s complex-transition ceiling |
| `motion_emphasis` (`:25`) | 600 | ⚠️ **exceeds `pro-max:181`'s "avoid >500 ms".** Defensible *only* as written in the comment — "reserve for the single primary action, never chrome" — which is also `pro-max:184` (1-2 animated elements). **Standing rule: `motion_emphasis` is legal on the connect hero and nowhere else.** Any second usage should be rejected in review |

**Two gaps:**
1. `motion.xml:5-6` states the intent *"Exit is always faster than enter"* (= `pro-max:191`), but
   there is **no exit token**. 60-70 % of `motion_reveal` 300 ms → **add `motion_exit` = 200**
   (and use `motion_state` 220 or the new 200 for dismissals), otherwise callers will reuse 300
   for both directions and the rule silently dies.
2. There is no **easing** token. `pro-max:185` and `states-and-variants.md:40-46` bind duration to
   easing. The file's header comment says "Ease-out only, no bounce" — that should be a real
   `@interpolator` resource referenced everywhere, not a prose comment.

---

## 11. Navigation

Category priority **HIGH** (`pro-max:62`). The full rule list is `pro-max:240-267`.

| Rule | Source | Applied to this app |
|---|---|---|
| Bottom nav **max 5 items**, with **icon + text label** | `:242`, `:248` | The plan is 3 tabs + settings hub (`design-system-2026.md:82`) ✅. **Verify labels are always visible** — `labelVisibilityMode` must not be `unlabeled`/`selected`, because `:248` says icon-only nav harms discoverability |
| Bottom nav is **top-level screens only** — never nest sub-navigation in it | `:258` | Sub-screens push onto the stack; they do not become tabs |
| Android: **Top App Bar** with a navigation icon for primary structure | `:247` | The "seamless sub-screen toolbar" owner request is compatible — seamless means no elevation/colour seam, not "no back affordance" |
| Current location visually highlighted (colour, weight, or indicator) | `:249` | The selected tab is the one sanctioned second use of blue outside the CTA |
| Primary nav (tabs) vs secondary nav (drawer/settings) clearly separated | `:250` | Settings hub is secondary; it must not appear as a peer of the primary tabs in any other surface |
| **Don't mix Tab + Sidebar + Bottom Nav at the same hierarchy level** | `:262` | One pattern per level |
| **Navigation placement stays identical across pages** | `:261` | The bottom bar does not move, resize, or hide per screen |
| Core navigation reachable from deep pages | `:265` | Don't strand the user in a sub-flow with no way back to a tab |
| **Back must be predictable and preserve scroll/state** | `:244`, `:254` | Returning from a server detail restores list scroll position, filter, and search text |
| **Never silently reset the stack or jump to home** | `:260` | Especially after auth or purchase callbacks |
| Support system gesture nav (Android **predictive back**) without conflict | `:255`, `:95` | Enable `android:enableOnBackInvokedCallback`; don't intercept the back swipe |
| Avoid horizontal swipe on main content (conflicts with back-swipe) | `:92`, `:634` | The subscription carousel is a deliberate, bounded exception — one primary gesture per region |
| Deep links to all key screens | `:245` | `strings_deeplink.xml` exists — audit that every key screen is addressable |
| Modals/sheets need a clear dismiss affordance | `:251` | |
| **Modals must not carry primary navigation flows** | `:263` | Purchase/auth must be real destinations, not sheet-only paths |
| Overflow menu instead of cramming actions | `:257` | |
| **Destructive actions visually and spatially separated** from normal nav items | `:266`, `:234` | Logout / delete account / reset config sit apart, in the error colour |
| Badges used sparingly; cleared after visit | `:256` | |
| If a destination is unavailable, **explain why** instead of hiding it silently | `:267` | E.g. a locked feature without a subscription: show it disabled with a reason, don't vanish it |
| Large screens (≥1024dp) prefer a sidebar; small screens bottom/top nav | `:259` | Relevant for the TV surface and tablets |

---

## 12. Forms & feedback

`pro-max:206-238` (31 rules). The ones that bind on this app's auth, purchase, DNS-input,
rename-subscription and settings flows:

- **Visible label per input**, never placeholder-only — `:208`, `:61`
- **Error below the related field**; for multiple errors, a summary at top with anchors — `:209`, `:232`
- **Errors must state cause + how to fix**, not "Invalid input" — `:228`; and include a recovery
  path (retry / edit / help) — `:224`
- **Validate on blur, not on every keystroke** — `:218`
- **After a submit error, focus the first invalid field** — `:231`
- **Persistent helper text below complex inputs**, not just a placeholder — `:215`
- **Semantic input types** so the right keyboard appears — `:219` → `android:inputType`
  `textEmailAddress` / `phone` / `number` / `numberPassword`
- **Password show/hide toggle** — `:220` → `passwordToggleEnabled` on the `TextInputLayout`
- **Autofill support** — `:221` → `android:autofillHints` on email/password/OTP fields
- **Submit feedback**: loading → success/error — `:210`; success confirmed with brief visual
  feedback — `:223`
- **Confirm before destructive actions** — `:214`; **offer undo** for destructive/bulk — `:222`
- **Destructive actions use the danger colour and are visually separated from primary** — `:234`
- **Progressive disclosure** — reveal complex options gradually — `:217` → this is the governing
  rule for the settings hub: advanced routing/DNS/fragment options live behind a disclosure, not
  on the first screen
- **Multi-step flows show a step indicator and allow back** — `:225`
- **Long forms auto-save drafts** — `:226`; **confirm before dismissing a sheet with unsaved
  changes** — `:227`
- **Group related fields** (visual or fieldset) — `:229`
- **Toasts auto-dismiss in 3–5 s** and must not steal focus — `:213`, `:235`
- **Timeouts show clear feedback with a retry** — `:238` → every network call in the VPN/auth path
- **Empty states carry a message *and* an action** — `:212`
- Required fields marked — `:211`
- Field/announcement accessibility: errors via `role="alert"`/live region — `:236` → Android:
  `announceForAccessibility` or `TextInputLayout.setError` (which announces natively)

---

## 13. Performance (as it applies to a native Android client)

`pro-max:104-124`. Web-specific items are excluded here (see §16).

- **Virtualise lists with 50+ items** — `:117` → `RecyclerView` with stable IDs and `DiffUtil` for
  the server list and payment history; never a `ScrollView` of inflated rows
- **Keep per-frame work under ~16 ms (60 fps)**; move heavy work off the main thread — `:118`
- **Input latency under ~100 ms** for taps and scrolls — `:120`
- **Visual feedback within 100 ms of tap** — `:121`
- **Debounce/throttle high-frequency events** (scroll, resize, text input) — `:122` → search/filter
  fields, ping polling, traffic-counter updates
- **Skeleton screens instead of long blocking spinners for >1 s** — `:119`
- **Reserve space for async content** so nothing jumps — `:115`, `:56` → fixed-height placeholders
  for subscription cards and ping values (`sub_card_height` 152dp, `dimens.xml:37`, already does
  this) — this is the native analogue of CLS
- **Offline state messaging and a basic fallback** — `:123` → a VPN client is *especially* obliged
  here: no-network, no-subscription, and core-not-running are three distinct states with three
  distinct messages
- **Degraded modes on slow networks** (fewer animations, lighter assets) — `:124`

---

## 14. Charts & data (traffic graph, ping history, usage)

`pro-max:269-300`. Applies to any traffic/usage visualisation.

- Match chart type to data: **trend → line**, comparison → bar, proportion → pie/donut — `:271`
- **Legend always visible**, positioned near the chart — `:275`
- **Tap (not hover) shows exact values**; interactive elements need **≥44pt tap area** — `:276`, `:284`
- **Axis labels with units**; no truncated/rotated labels on mobile; auto-skip ticks — `:277`, `:291`
- **Empty state**: "no data yet" + guidance, never a blank axis frame — `:279`
- **Loading**: skeleton/shimmer, not an empty axis frame — `:280`
- **Error**: message + retry, not a broken chart — `:297`
- Entrance animation must respect reduced motion; **data readable immediately** — `:281`
- **Locale-aware number/date/currency formatting** — `:283` → ru-RU
- **Data vs background ≥3:1; data text labels ≥4.5:1** — `:286`
- **Grid lines low-contrast** so they don't compete — `:294`; **emphasise the trend, not the
  decoration** — `:293` → no gradient fills or glows under the traffic line (also a CLAUDE.md ban)
- **Aggregate/sample for 1000+ points** — `:282`
- Time-series must label granularity (day/week/month) and let the user switch — `:300`
- Don't rely on colour alone; add pattern/shape/label — `:272`, `:274`
- Avoid pie/donut for >5 categories — `:285`

---

## 15. Copy and voice (Russian UI)

`ui-ux-brand/references/voice-framework.md` gives the framework; the app's voice is already fixed
by CLAUDE.md (Russian, sentence-case, active verbs). What the skill adds that is actionable:

- **Voice is constant, tone adapts** — `voice-framework.md:5-8`. So: the same personality in an
  error as in a success, but the *tone* shifts (empathetic on failure, matter-of-fact on success).
- **Four spectrums to place the product on** — `voice-framework.md:12-34`: Formal↔Casual,
  Simple↔Complex, Serious↔Playful, Reserved↔Expressive. For a security product bought by ordinary
  Russian consumers: **mid-casual, simple language, serious character, reserved emotion.** That
  rules out exclamation marks, jokes in error states, and marketing adjectives in settings.
- **3–5 traits phrased as "X, not Y"** — `voice-framework.md:39-45`. E.g. *понятный, не
  снисходительный* / *краткий, не грубый* / *уверенный, не хвастливый*.
- **Context→tone table** — `voice-framework.md:54-59`: support = empathetic, legal = formal,
  sales = confident. Map to: error/empty states, terms/privacy, purchase CTA.
- **Four voice tests** — `voice-framework.md:63-67`: does it sound like us / would a competitor say
  it / does it resonate / is it consistent.
- **Consistency audit list** — `consistency-checklist.md:30-48`: tone matches personality,
  **consistent capitalisation** (= the sentence-case law), consistent terminology, CTAs consistent.
  Concretely: pick one Russian word per concept and never alternate (подписка vs тариф vs план;
  сервер vs узел vs локация) and enforce it across `strings_*.xml`.
- Error copy specifically must state **cause + fix** (`pro-max:228`) and offer a **recovery path**
  (`pro-max:224`) — in Russian, in the same sentence structure every time.

---

## 16. Rules that do **not** transfer (stop re-reading these)

`ui-ux-pro-max/SKILL.md` carries two explicit scope notices — `:607` and `:667`: *"the rules below
are for App UI (iOS/Android/React Native/Flutter), not desktop-web interaction patterns."* That
covers §"Common Rules" and the Pre-Delivery Checklist. **The Quick Reference §1-§10 carries no
such notice and does mix web and app rules.** The following are web-only or need reinterpretation:

| Rule | Line | Why it doesn't apply / what replaces it |
|---|---|---|
| `cursor-pointer` | `:91` | No cursor on touch Android |
| `tap-delay` / `touch-action: manipulation` | `:93` | Browser-only 300 ms delay |
| `viewport-meta` | `:144` | HTML-only |
| `viewport-units` (`min-h-dvh` vs `100vh`) | `:156` | CSS-only |
| `breakpoint-consistency` 375/768/1024/1440 | `:146` | → Android resource qualifiers `sw600dp` / `sw720dp` / `land`; `:259` (≥1024 → sidebar) still applies conceptually |
| `container-width` `max-w-6xl/7xl` | `:152` | → a max content width in dp for tablets |
| `horizontal-scroll` "content fits viewport" | `:149` | → no horizontally clipped rows; still meaningful, just not a scroll bug |
| `skip-links` | `:75` | → no equivalent; TalkBack headings (`android:accessibilityHeading`) serve the same purpose |
| `heading-hierarchy` h1→h6 | `:76` | → `accessibilityHeading` + the type ramp |
| `keyboard-nav` / `focus-on-route-change` / `tooltip-keyboard` / `focusable-elements` | `:73`, `:264`, `:289`, `:295` | **Partly applies** — the app has a TV surface (`res/values/strings_tv.xml`), so D-pad focus order, visible focus rings and focusable chart points are real requirements there |
| `breadcrumb-web` | `:253` | Web-only |
| `image-optimization` / WebP / srcset / `font-display` / `critical-css` / `bundle-splitting` / `third-party-scripts` / `reduce-reflows` | `:106-114` | → VectorDrawables, no main-thread I/O, R8, `RecyclerView` recycling |
| CLS / Core Web Vitals framing | `:56`, `:107`, `:115` | → the *principle* (reserve space, no jumping) applies; the metric doesn't |
| All of `ui-ux-styling`'s shadcn/Radix/Tailwind code | `SKILL.md:56-113`, `references/shadcn-*.md`, `references/tailwind-*.md` | Only the **contrast numbers** (`shadcn-accessibility.md:359-382`), **focus-indicator discipline** (`:384-409`), **reduced-motion** (`:411-430`), **testing checklist** (`:432-447`) and the **light/dark CSS-variable *pattern*** (`shadcn-theming.md:100-137`, which is exactly the `values/` + `values-night/` split) transfer |
| `ui-ux-design`'s logo / CIP / slides / banner / social-photo generators | `SKILL.md:40-241` | Out of scope for in-app UI; scripts absent; needs a Gemini key |
| `canvas-design-system.md` museum/poster philosophy | whole file | Only three parts transfer: **2-5 colour limit** (`:150-154`), **spacing discipline / nothing overlaps** (`:186-194`), and the **"second pass refines, never adds"** rule (`:276-284`) |
| `ui-ux-design-system`'s slide system + Chart.js | `SKILL.md:108-235` | Presentation tooling, not app UI |

---

## 17. Conflicts and gaps between the skills and this repo's current state

Each item below is a concrete, checkable defect or divergence.

### 17.1 Two spacing vocabularies coexist in `dimens.xml`

`values/dimens.xml:3-11` still defines the legacy v2rayNG scale —
`padding_spacing_dp4/dp8/dp16`, `image_size_dp24`, `view_height_dp36/48/64/120/160` — alongside
the Incy scale at `:14-19` (`space_4/8/12/16/24/32`).

Violates `pro-max:150` / `:656` ("**a** consistent 4/8dp spacing system") and the CLAUDE.md "ONE
spacing scale" law. Two names for 4dp, 8dp and 16dp guarantee drift.
**Action:** migrate all `padding_spacing_dp*` / `view_height_dp*` references to the `space_*` and
component tokens, then delete lines 3-11. Note `view_height_dp36` (36dp) is below the 48dp touch
floor (`pro-max:86`) wherever it sizes a control.

### 17.2 Token names that describe a hue instead of a purpose

`token-architecture.md:139-147` (names encode purpose) and `shadcn-theming.md:370` ("use
`destructive` not `red`, `muted` not `gray`"):

- `icon_purple` = `#4C8DFF` (`values/colors.xml:21`) — **identical to `icon_blue`**
  (`colors.xml:19`). The de-purpling happened; the name didn't follow. Same for
  `icon_tile_purple` = `#334C8DFF` (`colors.xml:41`) = `icon_tile_blue` (`colors.xml:38`).
- `divider_color_light` (`colors.xml:25`) is overridden in `values-night/colors.xml:16` — so the
  token named "light" is the *dark* divider at runtime. Rename to `divider_color` / or better,
  use `?attr/colorOutlineVariant` and delete it.
- `brand_cream` (`colors.xml:6` `#F4F1EA`) becomes `#141619` in dark (`values-night/colors.xml:6`)
  — a "cream" that is near-black.
- `colorPingRed` / `colorPing` (`colors.xml:8-9`) alongside `ping_good` / `ping_bad`
  (`colors.xml:35-36`) — two naming generations for the same concept.

### 17.3 Body text size vs the skill's stated floor

`pro-max:147` (`readable-font-size`) says *"Minimum 16px body text on mobile"* — but its stated
justification is *"avoids iOS auto-zoom"*, a **mobile-Safari** behaviour. `pro-max:59` gives the
real floor as *"Text < 12px body"* being the anti-pattern.
`typography-specifications.md:201-204` says body 16 px, small 14 px, caption 12 px.

The app ships `.Body` 14sp (`styles.xml:89`), `.Subtitle` 13sp (`:96`), `.Caption` 12sp (`:103`),
`.Chip` 11sp (`:112`). `docs/design-system-2026.md:200` proposes Body **15sp** and `:204` states
the rule *"body text never below 14sp; secondary/caption never below 12sp"*.

**Resolution:** on native Android the binding constraint is not a fixed px floor — it is
(a) ≥12sp absolute (`pro-max:59`), (b) full `fontScale` support (`pro-max:78`, `:703`), and
(c) 4.5:1 contrast for anything that is normal-size text (`pro-max:643`). 14sp body is defensible;
**11sp chips are the actual risk** and must be short, high-contrast labels only. The live disagreement
between `docs/design-system-2026.md:200` (15sp) and `styles.xml:89` (14sp) should be resolved in
favour of the shipped token and the doc corrected — likewise `design-system-2026.md:190`, which
still says "keep the platform font (Roboto/system)" while the shipped ramp and CLAUDE.md mandate
**Space Grotesk** (`styles.xml:57, 66, 75, 110, 123`). **`docs/design-system-2026.md` §3.1-§3.4 is
stale relative to the shipped tokens** (it also names `radius_xs/sm/md/lg/xl` at `:166-171` while
`dimens.xml:22-28` ships `radius_chip/card/tile/pill/sheet`, and `space_2/20/48` at `:150-158`
which do not exist).

### 17.4 More than one red, and the error token isn't singular

`values/colors.xml` carries `colorPingRed` `#E5484D` (`:9`), `icon_red` `#F04452` (`:23`),
`ping_bad` `#C42B32` (`:36`) and `md_theme_error` `#BA1A1A` (`:71`) — four reds in the light
theme. Dark has `md_theme_error` `#F04452` (`values-night:49`), `ping_bad` `#F04452`
(`values-night:23`), `colorPingRed` `#F04452` (`values-night:9`) — consolidated to one there.

`pro-max:646` (token-driven theming) and `semantic-tokens.md:84-85` expect **one** `error` role
with a paired foreground. Light-mode should collapse to a single error hue plus, at most, one
contrast-corrected variant with the *reason* documented in a comment (the way
`values-night/colors.xml:62-65` documents its lift).

### 17.5 No elevation / z-index scale token

`pro-max:135` (`elevation-consistent`: "avoid random shadow values"), `pro-max:153`
(`z-index-management`: a defined layered scale), `token-architecture.md:155` (`shadow` is one of
the six token categories), `primitive-tokens.md:186-203` (explicit z ladder).

`values/dimens.xml` defines no elevation tokens. `docs/design-system-2026.md:176-187` describes an
elevation policy in prose (cards 0dp + hairline; floating 6dp; bars 0dp + hairline) but it is not
tokenised, so every layout picks its own number.
**Action:** add `elevation_flat 0` / `elevation_raised 6` / `elevation_overlay 12` (or equivalent)
and reference them, so "no random shadow values" is enforceable.

### 17.6 Missing motion pieces

Per §10.2: no `motion_exit` token despite `motion.xml:5-6` asserting the exit-faster-than-enter
rule (`pro-max:191`), and no easing/interpolator resource despite `pro-max:185` and
`states-and-variants.md:40-46` binding easing to duration. `motion_emphasis` 600 ms
(`motion.xml:25`) is a deliberate over-run of `pro-max:181` that must stay confined to one hero
moment.

### 17.7 Two glyph sizes with no stated rule

`tile_glyph` 22dp (`dimens.xml:32`) vs `image_size_dp24` 24dp (`dimens.xml:6`).
`pro-max:617` demands icon sizes be tokens with a rule, not arbitrary mixing. Write the rule
down (22dp in-tile / 24dp standalone) or unify.

### 17.8 Extra accent hues beyond the single-blue law

`values/colors.xml:19-24` defines `icon_blue`, `icon_green`, `icon_purple`, `icon_yellow`,
`icon_red`, `icon_orange` and `:38-43` their translucent tile fills. Six accent hues is in tension
with `color-palette-management.md:114-121` (accent budget 5-10 %) and
`canvas-design-system.md:150-154` (2-5 colours total). The repo's own answer —
`icon_tile_neutral` / `icon_glyph_neutral` (`colors.xml:45-50`) with the "spends no accent budget"
comment — is the correct default. **Audit which rows actually use a coloured tile and reduce to:
blue = accent/selected, red = destructive, green = connected/healthy. Retire yellow/orange/purple.**

### 17.9 Checks that need a look at layouts (not verified in this pass)

- Bottom-nav label visibility (`pro-max:248` requires icon **and** label).
- Scrim alpha applied to dialogs/sheets — must land in 0.40–0.60 (`pro-max:647`).
- Any literal `#RRGGBB` in `res/layout/**` or `res/drawable/**` (`pro-max:59`, `:646`).
- `singleLine="true"` on Russian row titles (`pro-max:174`).
- `ColorStateList`s missing `state_enabled="false"` / `state_focused` entries (§9.3).
- Whether any 40dp `tile_size` view is itself clickable (§2.2).

---

## 18. Pre-delivery checklist (adapted for this app)

Derived from `pro-max:664-704` (App-UI checklist, scope notice at `:667`), plus `pro-max:593-600`
(pre-delivery), plus `shadcn-accessibility.md:432-447`, plus `consistency-checklist.md:1-49`.

**Visual quality**
- [ ] No emoji used as icons anywhere (`pro-max:670`, `:613`)
- [ ] All icons from one family, one stroke width, one corner language (`pro-max:671`, `:618`, `:137`)
- [ ] Pressed visuals do not shift layout bounds (`pro-max:673`, `:615`)
- [ ] **Zero raw hex in layouts/drawables** — semantic theme attrs only (`pro-max:674`, `:646`, `:59`)
- [ ] No nested cards; no decorative gradients or glows (CLAUDE.md; `pro-max:293`, `:186`)
- [ ] Every dimension on the `space_*` / `radius_*` / component-token scale (`pro-max:150`, `:656`)

**Interaction**
- [ ] Every tappable element gives pressed feedback within 80-150 ms (`pro-max:677`, `:629`)
- [ ] Every touch target ≥48×48dp (`pro-max:678`, `:86`); ≥8dp between targets (`:87`)
- [ ] Micro-interactions 150-300 ms with ease-out (`pro-max:679`, `:181`, `:185`)
- [ ] Disabled states visually clear **and** non-interactive (`pro-max:680`, `:632`, `:216`)
- [ ] TalkBack order matches visual order; all controls labelled (`pro-max:681`, `:72`, `:73`)
- [ ] No nested/conflicting gestures; back-swipe not intercepted (`pro-max:682`, `:634`, `:255`)
- [ ] Every state exists: default / pressed / focused / selected / disabled / loading / empty / error (`states-and-variants.md:9-16`; CLAUDE.md)
- [ ] Animations interruptible and non-blocking (`pro-max:194`, `:195`)

**Dark / light**
- [ ] Primary text ≥4.5:1 **in both themes** (`pro-max:685`, `:643`)
- [ ] Secondary text ≥3:1 in both (`pro-max:686`) — and ≥4.5:1 where it is normal-size body copy
- [ ] Dividers, borders and interaction states distinguishable in both (`pro-max:687`, `:644-645`)
- [ ] Modal/sheet scrim 40-60 % black (`pro-max:688`, `:647`)
- [ ] **Both themes tested, neither inferred from the other** (`pro-max:689`, `:599`)
- [ ] Blue occupies ≤~10 % of any screen; exactly one primary CTA (`color-palette-management.md:118`; `pro-max:140`)

**Layout**
- [ ] Safe areas / system-bar clearance for app bar, bottom nav, bottom CTA (`pro-max:692`, `:653-654`)
- [ ] No scroll content hidden behind fixed bars (`pro-max:693`, `:660`)
- [ ] Verified on small phone, large phone, tablet, **portrait + landscape** (`pro-max:694`, `:157`)
- [ ] Gutters adapt by width/orientation (`pro-max:695`, `:659`)
- [ ] 4/8dp rhythm at component, section and page level (`pro-max:696`, `:658`)

**Accessibility**
- [ ] All meaningful icons have `contentDescription` (`pro-max:700`)
- [ ] Form fields have labels, helper text, and cause+fix error messages (`pro-max:701`, `:228`)
- [ ] Colour is never the only indicator (`pro-max:702`, `:77`)
- [ ] **`fontScale` 1.5 and reduced motion both verified with no layout breakage** (`pro-max:703`, `:598`)
- [ ] Selected / disabled / expanded states announced (`pro-max:704`)
- [ ] Tested at 375dp-equivalent narrow width (`pro-max:597`)

**Copy / brand**
- [ ] Sentence-case Russian, active verbs, one term per concept (`consistency-checklist.md:38-42`; CLAUDE.md)
- [ ] Errors state cause + fix and offer a recovery path (`pro-max:228`, `:224`)
- [ ] Empty states carry a message **and** an action (`pro-max:212`)
- [ ] Numbers/₽/dates formatted for ru-RU and rendered in `.Numeric` (`pro-max:283`, `:176`)

**Final pass (`canvas-design-system.md:276-284`)**
- [ ] The second pass **refined** what exists — it did not add more graphics

---

## 19. Source index (everything actually read for this report)

**Skills**
- `/home/user/dp/.claude/skills/ui-ux-pro-max/SKILL.md` (703 lines — full)
- `/home/user/dp/.claude/skills/ui-ux-design-system/SKILL.md` (244)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/token-architecture.md` (224)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/primitive-tokens.md` (203)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/semantic-tokens.md` (215)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/component-tokens.md` (214)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/component-specs.md` (236)
- `/home/user/dp/.claude/skills/ui-ux-design-system/references/states-and-variants.md` (241)
- `/home/user/dp/.claude/skills/ui-ux-styling/SKILL.md` (324)
- `/home/user/dp/.claude/skills/ui-ux-styling/references/shadcn-theming.md` (373)
- `/home/user/dp/.claude/skills/ui-ux-styling/references/shadcn-accessibility.md` (471)
- `/home/user/dp/.claude/skills/ui-ux-styling/references/canvas-design-system.md` (320)
- `/home/user/dp/.claude/skills/ui-ux-styling/references/tailwind-responsive.md` (first 120 of 382)
- `/home/user/dp/.claude/skills/ui-ux-styling/references/tailwind-customization.md` (first 90 of 483)
- `/home/user/dp/.claude/skills/ui-ux-brand/SKILL.md` (97)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/typography-specifications.md` (214)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/color-palette-management.md` (186)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/voice-framework.md` (88)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/consistency-checklist.md` (94)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/visual-identity.md` (96)
- `/home/user/dp/.claude/skills/ui-ux-brand/references/messaging-framework.md` (85)
- `/home/user/dp/.claude/skills/ui-ux-design/SKILL.md` (313)
- `/home/user/dp/.claude/skills/ui-ux-design/references/icon-design.md` (122)
- `/home/user/dp/.claude/skills/ui-ux-design/references/logo-color-psychology.md` (101)
- `/home/user/dp/.claude/skills/ui-ux-design/references/design-routing.md` (first 80 of 207)

**Repo files cross-referenced**
- `/home/user/dp/CLAUDE.md`
- `/home/user/dp/V2rayNG/app/src/main/res/values/dimens.xml` (41)
- `/home/user/dp/V2rayNG/app/src/main/res/values/colors.xml` (136)
- `/home/user/dp/V2rayNG/app/src/main/res/values-night/colors.xml` (112)
- `/home/user/dp/V2rayNG/app/src/main/res/values/motion.xml` (26)
- `/home/user/dp/V2rayNG/app/src/main/res/values/styles.xml` (lines 40-140 of 173)
- `/home/user/dp/docs/design-system-2026.md` (headings + lines 142-221 of 535)

**Not read (deemed non-transferable, listed for completeness):** all `ui-ux-design/references/`
logo-*, cip-*, slides-*, banner-*, social-photos-* files; `ui-ux-styling/references/`
shadcn-components.md, tailwind-utilities.md and the remainder of tailwind-responsive.md /
tailwind-customization.md; `ui-ux-design-system/references/tailwind-integration.md`;
`ui-ux-brand/references/` logo-usage-rules.md, asset-organization.md, approval-checklist.md,
brand-guideline-template.md, update.md; `templates/brand-guidelines-starter.md`; all of
`.claude/skills/superpowers/**` (engineering workflow, not design); `impeccable/**` and
`taste-skill/**` (covered by the sibling report `docs/agents/recon-skill-impeccable.md`).
