# Recon: Desktop design language → Android parity map

**Agent task:** extract the full desktop (Avalonia/C#) design language — tokens, motion choreography,
signature moments — and map each to its nearest Android equivalent.

**Repos read**

- Desktop: `/home/user/v2rayN/v2rayN/v2rayN.Desktop/**` (branch `claude/app-audit-agents-hyyftk`).
  NOTE: the task brief said `/home/user/v2rayN/v2rayN.Desktop`; the real path is one level deeper —
  `/home/user/v2rayN/v2rayN/v2rayN.Desktop`.
- Android: `/home/user/dp/V2rayNG/app/src/main/**` (for the parity map — only cited where I read the file).

**Files read in full or in the cited ranges**

| File | Why |
| --- | --- |
| `Common/Motion.cs` (74 ll) | C# motion token catalogue |
| `Common/MotionState.cs` (39 ll) | runtime reduced-motion broadcast |
| `Common/UiScaleState.cs` (59 ll) | in-app zoom broadcast |
| `Common/AppBuilderExtension.cs` (49 ll) | font manager defaults/fallbacks |
| `App.axaml` (51 ll), `App.axaml.cs` (470–707) | theme variant, style stack, mono overlay |
| `Assets/GlobalResources.axaml` (569 ll) | ALL colour / spacing / radius / size / font / easing tokens |
| `Assets/GlobalStyles.axaml` (1448 ll) | type scale, component styles, focus rings, lite gates |
| `Views/ConnectHeroView.axaml` (839 ll) + `.axaml.cs` (1156 ll) | connect shield — the signature surface |
| `Views/MainWindow.axaml` (1–560) + `.axaml.cs` (70–230, 400–690, 760–970, 1100–1200, 1320–1450, 1630–1770, 1790–1840) | shell, nav indicator, tab swap, sub-pages, theme reveal |
| `Views/BottomNavBar.axaml` (180 ll) + `.axaml.cs` (206 ll) | travelling bottom indicator |
| `Views/LoginView.axaml` (1–260, 745–800, 933) + `.axaml.cs` (930–1010, 1180–1380) | success beat, entrance beats |
| `Views/OnboardingView.axaml.cs` (213 ll) | entrance choreography (4 authored beats) |
| `Views/AccountSyncView.axaml.cs` (324 ll) | sync settle / stage dip |
| `Views/ServerListView.axaml.cs` (225–360, 457–700) | list stagger + accordion |
| `Views/AccountView.axaml` (90–230) + `.axaml.cs` (160–250, 465–524) | usage-pill grow, balance settle, carousel snap |
| `Views/SettingsView.axaml.cs` (190–305) | value crossfade, chevron, inline panel |
| `Views/SubscriptionMetaView.axaml.cs` (180–215) | instance-transition lite gating |
| `Views/BuyView.axaml` (140–230), `Views/DevicesView.axaml` (55–100), `Views/MessageBoxDialog.axaml` (34) | sheet/modal motion, elevation |
| `Views/HomeAccountChip.axaml.cs` (134–190) | entrance chip |
| Android: `res/values/motion.xml`, `res/interpolator/*.xml`, `res/anim/*.xml`, `res/values/dimens.xml`, `res/values/styles.xml` (50–130), `res/values-night/colors.xml` (1–110), `res/layout/activity_main.xml` (237–247, 555–600), `java/.../util/MotionUtils.kt`, `java/.../ui/MainActivity.kt` (140–160, 319–321, 415–435, 460–495, 815–840, 1650–1770, 1800–1900) | parity baseline |

---

## 1. Token set

### 1.1 Colour — accent (theme-invariant)

Declared once, outside the theme dictionaries, so the accent is literally ONE blue everywhere
(`Assets/GlobalResources.axaml:38–51`):

| Token | Value | Note |
| --- | --- | --- |
| `Color.Accent` / `Brush.Accent` | `#4C8DFF` | the single accent, all themes |
| `Color.OnAccent` / `Brush.OnAccent` | `#00183A` | ink on accent fills |
| `Brush.Tile.Blue` | `#4C8DFF` @ opacity 0.20 | icon-tile fill |
| `Brush.Tile.Green` | `#22C55E` @ 0.20 | |
| `SemiColorPrimary` | `= Color.Accent` | forces the vendored Semi theme onto our accent |
| `SemiColorPrimaryHover` | `#5F9AFF` | |
| `SemiColorPrimaryActive` | `#3D7EF0` | |

Accent-derived, also theme-invariant (`GlobalResources.axaml:226–231, 242, 250–261, 269–281`):

| Token | Value | Line |
| --- | --- | --- |
| `Brush.Tile.Orange` | `#FB923C` @ 0.20 | 226 |
| `Brush.Tile.Purple` | `#4C8DFF` @ 0.20 (purple ≡ blue by decree) | 227 |
| `Brush.Tile.Red` | `#F04452` @ 0.20 | 228 |
| `Brush.Tile.Yellow` | `#EAB308` @ 0.20 | 229 |
| `Brush.Icon.Orange` | `#FB923C` | 230 |
| `Brush.Icon.Yellow` | `#EAB308` | 231 |
| `Brush.SelectedFill` | `#4C8DFF` @ 0.12 | 242 |
| `Color.RedPressed` / `Brush.RedPressed` | `#D93844` | 250–251 |
| `Brush.StatusChip.Green` | `#22C55E` @ 0.18 | 255 |
| `Brush.StatusChip.Orange` | `#FB923C` @ 0.18 | 256 |
| `Brush.StatusChip.Red` | `#F04452` @ 0.18 | 257 |
| `Brush.StatusChip.Yellow` | `#EAB308` @ 0.18 | 258 |
| `Brush.Scrim` | `#000000` @ 0.6 | 261 |
| `Brush.ConnectGlow` | RadialGradient `#594C8DFF` → `#264C8DFF` @0.5 → `#004C8DFF` | 269–277 |
| `Brush.Ring.Outer` | `#334C8DFF` (20% blue) | 280 |
| `Brush.Ring.Inner` | `#804C8DFF` (50% blue) | 281 |

### 1.2 Colour — theme dictionaries (Dark / Light)

`GlobalResources.axaml:59–135`. Both variants carry an **identical key list** — this is the contract
that makes the mono overlay and every `DynamicResource` lookup safe.

| Key | Dark | Light |
| --- | --- | --- |
| `Brush.Bg` | `#0A0B0D` | `#F4F7FC` |
| `Brush.Surface` | `#141619` | `#FFFFFF` |
| `Brush.SurfaceHigh` | `#1A1D21` | `#EAEFF7` |
| `Brush.SurfaceVariant` | `#1E2126` | `#E9EEF7` |
| `Brush.SurfaceHighest` | `#20242B` | `#E3EAF4` |
| `Brush.OnSurface` | `#F2F4F8` | `#111826` |
| `Brush.OnSurfaceVariant` | `#9BA1AD` | `#54607A` |
| `Brush.OnSurfaceVariantHover` | `#6E7480` | `#3C475E` |
| `Brush.Outline` | `#2A2E36` | `#C3CCDC` |
| `Brush.OutlineVariant` | `#20242B` | `#DCE3EF` |
| `Brush.AccentContainer` | `#17325C` | `#D8E4FF` |
| `Brush.OnAccentContainer` | `#CFE0FF` | `#14468F` |
| `Brush.Green` | `#22C55E` | `#0B7D4A` |
| `Brush.Red` | `#F04452` | `#C42B32` |
| `Brush.RedText` | `#FF6069` | `#C42B32` |
| `Brush.Tile.Neutral` | `#20242B` | `#E3EAF4` |
| `Brush.Hover` | `#000000` @ 0.32 | `#000000` @ 0.06 |
| `Brush.Toast.Bg` | `#20242B` | `#E3EAF4` |
| `Brush.HomeGradient` | radial c(50%,30%) r75%: `#1B2D50` → `#0E141F`@0.55 → `#0A0B0D` | `#FFFFFF` → `#EEF3FB`@0.55 → `#DFE6F1` |

Two tokens Android does **not** have as named resources:

- **`Brush.RedText`** — a *brighter* red reserved for TEXT so error copy clears 4.5:1
  (`#FF6069` = 6.7:1 on `#0A0B0D`, ~5.9:1 on `#141619`; `GlobalResources.axaml:81–84`). `Brush.Red`
  stays for fills/strokes/glyphs.
- **`Brush.OnSurfaceVariantHover`** — one step darker than `OnSurfaceVariant`, used because icon
  navigation has **no hover background at all**; the *glyph itself* darkens
  (`GlobalResources.axaml:70–74, 108–110`; consumed at `GlobalStyles.axaml:786–791`).

Also desktop-only: `Brush.CloseHover` / `Brush.ClosePressed` for the window close button
(`Views/MainWindow.axaml:32–46` — Dark `#C43843`/`#A62C36`, Light `#C42B32`/`#A31F26`), plus
`Brush.OnClose` `#FFFFFF`.

### 1.3 Colour — mono ("Чёрно-белая") overlay

Built in code, **not** a theme variant: a `ResourceDictionary` appended last to
`Application.Resources.MergedDictionaries` so it shadows the theme dictionaries and repaints live via
`DynamicResource` (`App.axaml.cs:544–574`, built at `580–663`). Two cached instances, one per base
(`_monoLight` / `_monoDark`, `App.axaml.cs:482–484`).

Key values (`App.axaml.cs:586–662`) — light / dark:

- accent → `#111214` / `#FFFFFF`; onAccent → `#FFFFFF` / `#111214`;
  accentContainer `#E6E6E8` / `#2A2A2E`; onAccentContainer `#111214` / `#F4F4F5`
- Bg `#FFFFFF` / `#000000` (true AMOLED black); Surface `#FFFFFF` / `#121214`;
  SurfaceHigh `#EEEEEF` / `#1B1B1E`; SurfaceVariant `#F1F1F2` / `#1E1E20`;
  SurfaceHighest `#E7E7E9` / `#232326`
- OnSurface `#111214` / `#F4F4F5`; OnSurfaceVariant `#5A5A5E` / `#B0B0B4`;
  Outline `#D2D2D6` / `#38383C`; OutlineVariant `#E6E6E8` / `#28282C`
- **`Brush.Green` collapses to the mono "connected" tone** (`App.axaml.cs:629`) — success is grey/white,
  not green
- **Red survives** — it is the only non-neutral tone kept, for destructive/failed
  (`Brush.Red` `#C42B32` / `#E5484D`; `Brush.RedText` `#C42B32` / `#FF6069`, `App.axaml.cs:595–599`)
- every colour tile → grey tint @ 0.10; `Tile.Red` stays red @ 0.20 (`:635–640`)
- `Brush.Hover` inverts: light `#000000` @ 0.05, dark `#FFFFFF` @ 0.06 (a *lift*, not a darken) (`:652`)
- `Brush.HomeGradient` rebuilt in code (`BuildMonoHomeGradient`, `:665–687`); dark stops
  `#1B1B1E` → `#121214`@0.55 → `#000000`
- `Brush.ConnectGlow` rebuilt from the mono connected tone at alphas `0x59 → 0x26 → 0x00`
  (`BuildMonoConnectGlow`, `:690–704`); `Ring.Outer` = connected @ 0.20, `Ring.Inner` = connected @ 0.50

### 1.4 Spacing

`GlobalResources.axaml:137–147`. ONE scale, no half-steps:

`Space.4` 4 · `Space.8` 8 · `Space.12` 12 · `Space.16` 16 · `Space.24` 24 · `Space.32` 32
Helpers: `Pad.12` = 12, `Pad.16` = `Pad.Card` = 16, `Gutter` = `16,0`, `Size.Gutter` = 16.

Legacy v2rayN thicknesses still exist for the geek windows (`Margin2/4/8`, `MarginLr4/8`, `MarginTb8`,
`GlobalResources.axaml:24–29`) — do **not** port these; they are not part of the design language.

### 1.5 Radii

`GlobalResources.axaml:149–154, 283–285` plus `GlobalStyles.axaml:16`:

| Token | Value | Used for |
| --- | --- | --- |
| `Radius.Chip` | 12 | chips, badges, list tiles, status chips, code cells |
| `Radius.Tile` | 12 | 40dp icon tiles, plain rows |
| `Radius.Search` | 14 | ALL inputs + price options (`TextBox.Incy`, `IncyField`, `SearchPill`, `PriceOption`) |
| `Radius.Button` | 16 | every CTA — declared in `GlobalStyles.axaml:16`, not GlobalResources |
| `Radius.Card` | 20 | cards, flyouts, empty-state hero tile, server rows |
| `Radius.Sheet` | `24,24,0,0` | bottom-sheet top |
| `Radius.Pill` | 100 | icon buttons, scrollbar thumb, toast, avatar, sheet handle |
| `Radius.Traffic` | 8 | traffic/usage pill track |

The `Radius.Button` = 16 decision is documented at length (`GlobalStyles.axaml:2–14`): Android's
`cornerRadius=26dp` on a 52dp button clamps to a stadium capsule, **which the owner rejected**. 16
completes the ladder chip 12 / button 16 / card 20 / sheet 24 and stays inside the card radius so a
button nested in a card is concentric.

### 1.6 Size tokens

`GlobalResources.axaml:156–163, 287–309`:

| Token | Value | Token | Value |
| --- | --- | --- | --- |
| `Size.Tile` | 40 | `Size.HeroFrame` | 230 |
| `Size.Glyph` | 22 | `Size.ConnectDisc` | 176 |
| `Size.Row` | 56 | `Size.ShieldGlyph` | 80 |
| `Size.IconButton` | 40 | `Size.ConnectArc` | 212 *(declared, unused — the arc is inlined at 190, see §5.3)* |
| `Size.SubToolbar` | 56 | `Size.TrafficPill` | 160 |
| `Size.CtaTall` | 52 | `Size.FlagTile` | 28 |
| `Size.SegmentChip` | 44 | `Size.AvatarChip` | 36 |
| `Size.EmptyIcon` | 64 | `Size.AvatarAcc` | 48 |
| `Size.EmptyGlyph` | 32 | `Size.AvatarBadge` | 18 |
| `Dot` | 6 | `Size.SheetHandleW` / `H` | 36 / 4 |
| `Dot.Active` | 8 | `Size.SkeletonCard` | 76 |
| `Dot.Gap` | 8 | `IconButtonWidth/Height` (legacy) | 32 |

### 1.7 Type scale

Family: **Space Grotesk**, `avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk`
(`GlobalResources.axaml:166`). `Font.Numeric` is a *separate semantic token* pointing at the **same
file** (`:180`) — deliberate, documented at `:168–179`: no second sans is introduced; consumers pair it
with `FontFeatures="tnum,lnum,zero"` (tabular + lining + slashed zero) so live numbers do not jitter.

The family is forced onto three roots so every popup/sub-window inherits it, because Avalonia does not
propagate `FontFamily` across visual roots (`GlobalStyles.axaml:257–265`): `TopLevel`, `TextBlock`,
`TemplatedControl`.

`GlobalStyles.axaml:272–327`:

| Class | Size | Weight | Tracking | Default ink |
| --- | --- | --- | --- | --- |
| `.Display` | 34 | Bold | −0.7 | `OnSurface` |
| `.Headline` | 24 | Bold | −0.24 | `OnSurface` |
| `.Title` | 16 | Bold | 0 | `OnSurface` |
| `.TitleMedium` | 16 | Medium | 0 | `OnSurface` |
| `.Body` | 14 | (inherit) | 0 | `OnSurface` |
| `.Subtitle` | 13 | (inherit) | 0 | `OnSurfaceVariant` |
| `.Caption` | 12 | (inherit) | 0 | `OnSurfaceVariant` |
| `.Chip` | 11 | Medium | 0 | `OnSurface` |
| `.Numeric` | (inherits) | (inherits) | — | `OnSurface`, `FontFeatures=tnum,lnum,zero` |
| `.SectionHeader` | 16 | Bold | 0 | `OnSurface` — **sentence case, never ALL-CAPS eyebrow** |

Note the desktop tracking is expressed in Avalonia `LetterSpacing` (px-ish), Android in em: `-0.7`
desktop ≈ `-0.02em` Android at 34sp; `-0.24` ≈ `-0.01em` at 24sp. Android's `.Body`/`.Subtitle`/
`.Caption` carry `0.01/0.01/0.02` em tracking (`res/values/styles.xml:88–107`) that the desktop drops
to 0 — a real, small divergence.

Body/Subtitle/Caption inherit family from the global `TextBlock` setter, so they are Space Grotesk on
desktop; Android deliberately leaves them on the **system font** (`res/values/styles.xml:87, 94, 101` —
no `fontFamily` item). This is the single biggest typographic divergence between the platforms.

### 1.8 Elevation / depth

There is essentially **no elevation system**. Depth is carried by surface tiers + 1px hairlines:

`Bg` → `Surface` → `SurfaceHigh` → `SurfaceVariant` → `SurfaceHighest`, with `Brush.OutlineVariant`
1px borders. `Border.Card` explicitly ships **without a shadow** (`GlobalStyles.axaml:329–336`,
comment "без тени").

Exactly three shadows exist in the whole app, all on floating layers:

| Surface | BoxShadow | File:line |
| --- | --- | --- |
| Flyout (`IncyFlyoutTheme`) | `0 12 32 0 #66000000` | `GlobalStyles.axaml:42` |
| Toast (`Border.Toast`) | `0 8 24 0 #40000000` | `GlobalStyles.axaml:1198` |
| Message dialog | `0 16 40 0 #73000000` | `Views/MessageBoxDialog.axaml:34` |

Plus one gradient scrim under the compact bottom bar: `#00000000 → #33000000` vertical
(`Views/BottomNavBar.axaml:24–27`).

### 1.9 Motion tokens

Declared **twice, deliberately mirrored**: XAML easings + a documented duration table
(`GlobalResources.axaml:182–214`) and a C# catalogue (`Common/Motion.cs`). The XAML side keeps
durations as *literals* because "the Avalonia XAML compiler does not support the `x:TimeSpan`
intrinsic" (`GlobalResources.axaml:200–201`, `Motion.cs:8–10`). `Motion.cs` is the single source of
truth for every imperative animator (`Motion.cs:12–17`).

**Durations** (`Motion.cs:22–53`, table at `GlobalResources.axaml:202–212`):

| Token | ms | Curve | Role |
| --- | --- | --- | --- |
| `Dur.Instant` | 0 | — | lite / reduced-motion fallback: snap to end state |
| `Dur.PressIn` | 90 | `OutQuart` | finger-down |
| `Dur.PressOut` | 160 | `OutQuint` | release / small settle |
| `Dur.State` | 220 | `Standard` | state change / tint crossfade (bidirectional) |
| `Dur.Reveal` | 300 | `OutQuint` | screen entry, entrance chip, sync settle, glow reveal |
| `Dur.Exit` | 150 | `Standard` | screen / sub-page exit — **shorter than entry** |
| `Dur.Shell` | 200 | `Standard` | 3-way shell overlay crossfade |
| `Dur.Slow` | 450 | `OutExpo` | ONE decisive auth→home hand-off |
| `Dur.Emphasis` | 600 | `OutQuint` | ONE hero moment: the connect sonar |
| `Dur.Stagger` | 40 | — | per-item list delay / entrance chip delay |

**Reverse tempo = 75%** of forward: `revState` 165, `revReveal` 225
(`GlobalResources.axaml:212`, implemented `ConnectHeroView.axaml.cs:661–685`).

**Easings** — all four are `SplineEasing`, explicitly NOT Avalonia's built-in
`QuarticEaseOut`/`QuinticEaseOut` (different control points; warned at `GlobalResources.axaml:188–189`
and `Motion.cs:56–58`):

| Token | cubic-bezier | Role |
| --- | --- | --- |
| `Ease.OutQuart` | `0.25, 1, 0.5, 1` | press feedback, small settles |
| `Ease.OutQuint` | `0.22, 1, 0.36, 1` | confident reveals/settles, glow, sonar, disc release |
| `Ease.Standard` | `0.2, 0, 0, 1` | tint/crossfade, bidirectional state changes, reversal |
| `Ease.OutExpo` | `0.16, 1, 0.3, 1` | **reserved** for the single auth→home hand-off |

> **Finding:** `Ease.OutExpo` and `Dur.Slow` (450) are declared in both catalogues
> (`GlobalResources.axaml:196–198, 209`; `Motion.cs:45–46, 71–72`) but a repo-wide grep finds **zero
> consumers** — the auth→home hand-off is currently the ordinary 200ms `Dur.Shell` crossfade
> (`MainWindow.axaml.cs:919, 929`). This is documented intent that was never wired. Android should
> either implement it or drop the token, not copy a dead token.

**The discipline** (`Motion.cs:15–17`, `GlobalResources.axaml:184–186`): ease-out only, exit faster
than entry, **no bounce, no elastic, no overshoot below rest**.

---

## 2. Motion choreography catalogue

Every entry below is `from → to`, duration, easing, with its file:line and the Android construct that
maps to it.

### 2.1 Press (the ONLY press feedback — no ripple, no glow anywhere)

| Target | Scale | Timing | File:line | Android |
| --- | --- | --- | --- | --- |
| `Button.Primary` / `.Tall` | 1 → 0.97 | 120ms `OutQuart`, both legs (a `TransformOperationsTransition`) | `GlobalStyles.axaml:400–408` | `res/anim/press_scale.xml` (0.96, 90/160) via `stateListAnimator` |
| `Button.Tonal` | 1 → 0.97 | 120ms `OutQuart` | `GlobalStyles.axaml:462–470` | same |
| `Button.OutlinedAccent` | 1 → 0.97 | 120ms `OutQuart` | `GlobalStyles.axaml:512–520` | same |
| `Button.Destructive` | 1 → 0.97 | 120ms `OutQuart` | `GlobalStyles.axaml:994–1014` | same |
| `Button.LinkAction` | 1 → 0.97 | 120ms `OutQuart` | `GlobalStyles.axaml:963–974` | same |
| `Button.Stepper` (±) | 1 → 0.94 | 120ms `OutQuart` | `GlobalStyles.axaml:557–571` | `press_scale.xml` variant |
| `Button.IconButton40` / `.BackNav` | 1 → 0.92 | 120ms (no explicit easing → linear) | `GlobalStyles.axaml:856–875, 921–939` | `press_scale.xml` variant |
| `Button.NavRailItem` | 1 → 0.92 | **160ms `OutQuint`** | `GlobalStyles.axaml:763–768, 792–794` | `res/anim/nav_press.xml` (0.92, **100/120, no interpolator**) |
| `Button.BottomNavItem` | 1 → 0.92 | **160ms `OutQuint`** | `Views/BottomNavBar.axaml:46–49` | same |
| `Border.ServerRow` | 1 → 0.96 | 120ms (no easing) | `GlobalStyles.axaml:621–633` | `press_scale.xml` on the row |
| `ToggleButton.Segment` | 1 → 0.96 | 120ms | `GlobalStyles.axaml:1231–1247` | `press_scale.xml` |
| `Button.RailToggle` inner bg | 1 → 0.92 | 120ms | `Views/MainWindow.axaml:162–173` | n/a (desktop chrome) |
| iOS toggle knob fill | 1 → 0.9 | 90ms `OutQuart` | `GlobalResources.axaml:369–373, 388–390` | `SwitchMaterial` thumb |
| **Connect disc** | 1 → 0.94 | **90ms `OutQuart` in / 160ms `OutQuint` out** (asymmetric) | `ConnectHeroView.axaml.cs:696–706, 731–739` | `MainActivity.animateConnectPress()` — **identical**, `MainActivity.kt:1805–1821` |
| **Shield glyph parallax dip** | 1 → 0.97 | mirrors the disc (90/160) | `ConnectHeroView.axaml.cs:702–705, 736–739` | **missing on Android** |
| **Press scrim ("well")** | opacity 0 → 0.12 black ellipse under the glyph | mirrors the disc (90/160) | `ConnectHeroView.axaml:524–528`, `.cs:702–704, 736–738` | **missing on Android** |

`Border.SettingRow` deliberately has **no** press-scale — documented at `GlobalStyles.axaml:650–653`:
the old `.pressed` scale 0.98 made the row slide out from under the cursor and cancelled `Tapped`, so
taps fired "every other time". Hover carries the acknowledgement instead.

**Press-origin trap, worth porting as a comment, not code** (`GlobalStyles.axaml:374–382`):
`RenderTransformOrigin` must be the *relative* `"50%,50%"`. `"0.5,0.5"` is parsed as **absolute
pixels** (0.5px ≈ top-left corner) and the scale visibly "falls" up-left. Two elements intentionally
keep the absolute `0.5,0.5` no-op because they self-centre imperatively — `Border.ConnectDisc`
(`GlobalStyles.axaml:711–717`) and `Ellipse.Spinner` (`:1320–1327`).

### 2.2 Hover (desktop-only, no Android analogue — keep it out of the phone build)

| Target | Change | Timing | File:line |
| --- | --- | --- | --- |
| `Button.Primary` | bg `#4C8DFF` → `#3D7EF0` (darker), pressed `#3877E0` | 150ms `Standard` crossfade | `GlobalStyles.axaml:426–438` |
| `Button.Tonal` / `Outlined` / rows / icon buttons | overlay `Brush.Hover` | 150ms `Standard` (Tonal), instant on rows | `GlobalStyles.axaml:476–493, 530–537` |
| **Nav items (rail + bottom)** | **glyph + label darken to `OnSurfaceVariantHover`, NO background** | 200ms `Standard` (`BrushTransition Foreground`) | `GlobalStyles.axaml:786–791, 800–806`; `BottomNavBar.axaml:107–113` |
| Connect disc | surface `SurfaceHigh` → `SurfaceHighest` | 120ms `OutQuart`, **0ms in lite** | `ConnectHeroView.axaml:302–304`, `.cs:149–151, 652–658, 747–759` |
| Connect ring | overlay `RingHoverGlow` opacity 0 → 0.5 | 120ms `OutQuart` | `ConnectHeroView.axaml:415–424`, `.cs:751` |
| Connect glyph (Idle only) | `OnSurfaceVariant` → `OnSurface` (warm to ink, **not** to accent) | 220ms via the outline's own transition | `ConnectHeroView.axaml.cs:755–758` |
| Window close button | bg → `Brush.CloseHover`, glyph → white | **instant bg** (transition removed on purpose), 120ms glyph | `MainWindow.axaml:71–111` |

Two hover transitions were **deliberately removed** and the reasoning is worth carrying into any
Android hover/focus work (TV, mouse): a `BrushTransition` on row/button backgrounds made the outgoing
row fade while the incoming one appeared, so **two rows looked highlighted at once**
(`GlobalStyles.axaml:617–620` for `ServerRow`, `:654–655` for `SettingRow`, `MainWindow.axaml:71–78`
for caption buttons). Row hover fills are therefore **instant**.

### 2.3 Navigation indicator — the travelling bar

Both navigations replaced three per-item pills with **ONE bar that physically slides**.

**Rail (wide layout)** — `Border x:Name="railIndicator"`, 3 × 28, `CornerRadius 2`, `Brush.Accent`
(`MainWindow.axaml:500–509`). Slot geometry is fixed because the rail always shows 3 items:
`Y = index·64 + 18` (`MainWindow.axaml.cs:545`, derived from item height 64 and indicator height 28:
`(64−28)/2 = 18`). Slide: `TranslateTransform.Y`, `Motion.Dur.State` 220ms `Ease.OutQuint`,
`FillMode.Forward` (`MainWindow.axaml.cs:579–602`).

**Bottom bar (compact)** — `Border x:Name="BottomIndicator"`, 34 × 3, `CornerRadius 2`
(`BottomNavBar.axaml:167–177`). X target is computed from **live bounds** so it is correct in both the
3-item (signed-in) and 2-item (signed-out) layouts:
`targetX = item.Bounds.X + w/2 − 34/2` (`BottomNavBar.axaml.cs:126–160`). Same 220ms `OutQuint`.

Three guards, all worth porting:

1. **First show is instant** (`_railIndicatorSeeded` / `_indicatorSeeded`) so it does not slide in from
   Y=0/X=0 (`MainWindow.axaml.cs:562`, `BottomNavBar.axaml.cs:145`).
2. **Read `from` BEFORE `Cancel()`** — cancelling reverts the property to its base, so reading inside
   the animator produced a visible "rollback frame" on fast triple-taps
   (`MainWindow.axaml.cs:563–566`, `BottomNavBar.axaml.cs:146–149`).
3. **Idempotence guard** — `_lastTargetX` within 0.5px is a no-op so a double `SetSelected`
   (`Raise` + host `ShowTab` on one tap) does not restart the slide (`BottomNavBar.axaml.cs:136–143`).

Repositioning is driven by `LayoutUpdated` (not a single button's `Bounds`) so every item's bounds are
final before the centre is computed (`BottomNavBar.axaml.cs:56–61`).

**Android today:** three independent `View`s `nav_home_dot` / etc., 34dp × 3dp,
`@drawable/bg_nav_dot`, toggled by visibility in `updateNavSelection`
(`res/layout/activity_main.xml:565–574`). They blink in place. **This is the clearest single-file
parity gap.**

Nav tint on selection: desktop `BrushTransition Foreground` 200ms `Standard` + weight Medium→Bold
(`GlobalStyles.axaml:800–828`, `BottomNavBar.axaml:71–87, 95–102`). Android already matches exactly —
`tweenNavItemColor` uses `ValueAnimator.ofObject(ArgbEvaluator)` 200ms `easeStandard`
(`MainActivity.kt:420–436`) plus a Typeface bold swap (`:415`).

### 2.4 Tab swap (keep-alive content host)

Desktop, `MainWindow.axaml.cs:450–476`:

- **Entering** view: `translateX ±16 → 0` + `opacity 0 → 1`, `Dur.State` 220ms `Ease.OutQuint`
- **Leaving** view: `opacity → 0` only, `Dur.Exit` 150ms `Ease.Standard` — **runs in parallel**, no
  counter-slide
- **Direction** comes from the nav-index delta: deeper along Home▸Settings▸Account → enters from the
  right (+16); back → from the left (−16) (`MainWindow.axaml.cs:87–93, 457`)
- Incoming view always gets `ZIndex = ++_contentZ` so the rise reads correctly (`:416`)
- 16px is deliberately the **same slide vocabulary** as sub-pages (`:91–92`)

Instant paths: first show, `MotionState.IsLite`, layout swap, or window off-screen (`:431`).

**Android today:** `updateTabVisibility` does a **sequential** fade-through — outgoing fades out 150ms
`easeStandard`, and only in `withEndAction` does the incoming fade in + rise **8dp** over 200ms
`easeOutQuint` (`MainActivity.kt:483–493`). Differences to reconcile: sequential vs parallel, vertical
8dp vs directional horizontal ±16dp, 200 vs 220ms, and Android has no direction. Android also fires a
`tickHaptic()` on the change (`:485`) that the desktop has no equivalent for.

### 2.5 Shell overlay crossfade (3-way gate)

`accountSyncView` / `onboardingView` / `bodyRoot`, gate priority SYNCING > EMPTY > CONTENT
(`MainWindow.axaml.cs:839–871`). Crossfade is **opacity-only**, 200ms `Ease.Standard` both directions
(`CrossfadeShellTo` `:876–915`, `FadeShellIn` `:917–925`, `FadeShellOutThenHide` `:927–941`).

Two safety behaviours: the third (neither in nor out) overlay is hidden **instantly** so an
interrupted crossfade can never leave three surfaces visible (`:896–905`); and the outgoing overlay is
only actually hidden if it did not become the target again in the meantime (`:935–940`).

The layout morph (compact ⇄ wide) uses the same `Dur.Shell` 200ms `Standard` fade on `contentArea`,
explicitly matched to the window-resize animation so content re-materialises on the exact frame the
window stops growing (`AnimateLayoutSwap` `:783–801`, rationale `:790–793`).

### 2.6 Sub-page push / pop

`MainWindow.axaml.cs:1120–1171`. Translate + opacity only — never scale or rotate.

- **Push:** `translateX 16 → 0`, `opacity 0 → 1`, **300ms `Ease.OutQuint`** (`:1139`)
- **Pop:** `translateX 0 → 16`, `opacity 1 → 0`, **200ms `Ease.Standard`** (`:1160`), then the previous
  page (if any) pushes back in with the same 300ms recipe (`:1169`)
- Lite → instant (`:1128–1133, 1151–1155`)

Note these are 300/200, i.e. `Reveal`/`Shell` values, not `State`/`Exit` — a small inconsistency with
the tab swap (220/150) that exists in the shipped code.

### 2.7 List stagger

`Views/ServerListView.axaml.cs:225–360`:

- rows 0…7 only (`MaxStaggerRows = 8`, `:237`)
- per-row delay `index × 40ms` (`StaggerMs = 40`, `:236, 311`)
- `translateY 12 → 0` + `opacity 0 → 1` over **300ms** `SplineEasing(0.22,1,0.36,1)` (`:318–319, 328–356`)
- **one-shot per bound VM** — latched in a `static ConditionalWeakTable` so tearing down and
  re-creating the view when the Home tab is re-shown cannot replay it (`:244–257, 296–297`)
- `FillMode.None` (not `Forward`) so the animation **releases** `RenderTransform`/`Opacity` back to the
  control base and cannot shadow the row's `:pressed` scale-0.96 (`:334–337`)
- rows are visible **by default**; the hidden start state is set only as part of actually running, and
  a safety timer + `finally` always restore rest, so a row can never be stranded invisible (`:230–233`)
- expressed as `TransformOperations.Parse("translateY(12px)")` — the *same transform vocabulary* the
  row's press-scale uses, because a raw `TranslateTransform` would clash with the style's
  `TransformOperationsTransition` on `RenderTransform` (`:314–319`)

**Android already matches this exactly** — `revealListStagger` (`MainActivity.kt:815–839`): 12dp rise,
`i * durStagger`, `durReveal`, `easeOutQuint`, capped at 8, guarded by a flag, no-op under reduced
motion. Nothing to port.

The desktop **shell-level** region stagger is different and has no Android twin: on a tab's first
activation per session, its ≤3 top-level regions rise `translateY 6 → 0` + fade, `Dur.State` 220ms
`OutQuint`, delayed `Dur.Stagger × index` = 40ms steps, `FillMode.Both` so the start frame holds
during the delay (`MainWindow.axaml.cs:604–690`). It explicitly **skips** the Account tab (it plays its
own) and the connect hero (it owns its cold-start assemble) so nothing animates twice (`:619–621,
646–649`).

### 2.8 Inputs, chips, options

| Element | Motion | File:line |
| --- | --- | --- |
| `TextBox.Incy` / `.IncyField` border | `BorderBrush` 150ms; rest `OutlineVariant` → hover `Outline` → focus `Accent` | `GlobalResources.axaml:429–433, 473–478` (and 516–520, 559–564) |
| `Border.PriceOption` | bg + border 150ms; border is **permanently 1.5px** (transparent at rest) so selection never shifts layout | `GlobalStyles.axaml:1145–1165` |
| `Border.ServerRow` | border 150ms; **background instant** (see §2.2); selected = `SelectedFill` + 1.5px `Accent` | `GlobalStyles.axaml:621–642` |
| `ToggleButton.Segment` | border 150ms + press 0.96 | `GlobalStyles.axaml:1231–1254` |
| 2FA `Border.CodeCell` | `BorderBrush` 150ms, rest → `.filled` (`Outline`) → `.active` (`Accent`) | `LoginView.axaml:140–161` |
| Login field error flash | `.fieldError` paints the inner border `Brush.Red`; return rides the template's own 150ms `BrushTransition` — **colour only, no shake** | `LoginView.axaml:126–133` |
| iOS `ToggleSwitch` | track colour 220ms `Standard`; knob `translateX(20px)` 220ms `OutQuint`; knob fill squash 0.9 @ 90ms `OutQuart` | `GlobalResources.axaml:329–396` |
| Settings value change | `opacity 0.3 → 1`, `Dur.PressOut` 160ms `Standard` | `SettingsView.axaml.cs:196–215` |
| Settings chevron | `RotateTransform.Angle 0 ↔ 90`, `Dur.State` 220ms `Standard` | `SettingsView.axaml.cs:238–259` |
| Settings inline panel | open: fade + `translateY −6 → 0`, `Dur.Reveal` 300 `OutQuint`; close: `0 → −6`, `Dur.Exit` 150 `Standard` | `SettingsView.axaml.cs:263–304` |
| Subscription-meta chevron | `Angle` 220ms `Standard` (instance transition, nulled in lite) | `SubscriptionMetaView.axaml.cs:185–199` |
| Subscription-meta pin glyph | `Foreground` 200ms `Standard` | `SubscriptionMetaView.axaml.cs:200–209` |
| Usage/traffic pill fill | `Width` grows to target, **300ms `OutQuint`** | `AccountView.axaml:126–133` |
| Balance change | `opacity 0.25 → 1` + `translateY −6 → 0`, `Dur.State` 220 `Standard` — settles **downward** | `AccountView.axaml.cs:228–247` |
| Subscription carousel snap | manual 16ms-tick tween on `ScrollViewer.Offset`, `Dur.Reveal` 300ms `OutQuint` (Offset is not transitionable) | `AccountView.axaml.cs:472–508` |

### 2.9 Sheets, modals, toast, skeletons, spinners

| Element | Motion | File:line |
| --- | --- | --- |
| Buy payment sheet | scrim opacity 0→1 220ms; surface `translateY 24 → 0` + opacity 220ms; close = reverse | `BuyView.axaml:205–230` |
| Buy state reveal | fade 220ms `OutQuart` on `IsVisible=True` | `BuyView.axaml:145–160` |
| Buy price-option reveal | fade + `translateY 6 → 0`, 300ms `OutQuint` | `BuyView.axaml:162–180` |
| Devices modal | scrim fade 150ms; card `scale 0.96 → 1` + fade 220ms `Standard` | `DevicesView.axaml:62–97` |
| Account subscription state | fade 220ms `OutQuart` | `AccountView.axaml:149–160` |
| Skeleton pulse | opacity `0.45 ↔ 1.0`, **900ms `SineEaseInOut`, `Alternate`, infinite** — no shimmer | `GlobalStyles.axaml:1285–1300` |
| Generic spinner | dashed-arc `Ellipse.Spinner.spinning`, rotate 0→360, **1.1s linear**, infinite | `GlobalStyles.axaml:1331–1345` |
| Scrollbar | thumb hidden at rest (opacity 0) → `:expanded` 0.45 → `:pointerover` 0.8 → dragging `Accent` @ 1.0; width 6 → 8 on hover; all 150ms `Standard` | `GlobalStyles.axaml:86–190` |
| Rail collapse | `navItems` Width 76 → 0 over **200ms `OutQuint`** + Opacity 160ms; chevron `rotate(0 ↔ 180deg)` 200ms `OutQuint`; `railIndicator` fades over the same 160ms | `MainWindow.axaml:180–246` |
| Window resize (layout toggle) | manual 16ms loop, **200ms `OutQuint`**, centre-anchored once at start | `MainWindow.axaml.cs:1338–1392` |

---

## 3. Signature moments

These are the moments the design language is actually *about*. Each has an explicit budget and a
single owner.

### 3.1 Cold-start assemble (once per process)

`Panel.assembling` on `HeroFrame`: `scale 0.9 → 1` + `opacity 0 → 1`, **400ms**, inline
`SplineEasing(0.22,1,0.36,1)` = `Ease.OutQuint`, `FillMode.Forward`, ends exactly at rest — nothing to
clean up (`ConnectHeroView.axaml:270–295`).

Driven by `OnFirstLoaded` with a `static bool _assembled` process guard and `await Task.Delay(460)`
(`ConnectHeroView.axaml.cs:1078–1099`). The hero is pre-hidden (`HeroFrame.Opacity = 0`) in the ctor so
it never flashes at rest before assembling (`:271–273`), and a **700ms insurance timer** forces
visibility if `Loaded` never arrives (`:275–279`) — plus a `finally` (`:1093–1098`) and an idempotent
`EnsureHeroVisible()` (`:1103–1110`). Three independent guarantees that the shield can never be blank.

Origin must be relative `"50%,50%"` on `HeroFrame` because the assemble uses a bare `ScaleTransform`
with no centre (`ConnectHeroView.axaml:323–333`).

**Android:** exact equivalent already exists — `res/anim/shield_assemble.xml` (400ms,
`ease_out_quint`, scale 0.9→1, alpha 0→1, `pivot 50%,50%`, `fillAfter=false`), loaded at
`MainActivity.kt:319–321` behind `binding.heroFrame.reducedMotion()`. The **process guard and the
blank-shield insurance are what Android lacks.**

### 3.2 Connect payoff (the one Emphasis moment)

Entering `Connected` with `animate: true`, on-screen, motion enabled (`ConnectHeroView.axaml.cs:354–377`)
fires **four things at once**, all inside the 600ms `Dur.Emphasis` budget:

1. **Shield crossfade + tint** — outline `opacity 1 → 0`, filled `0 → 1`, fill `grey → Accent`,
   **220ms `Ease.Standard`** (`ConnectHeroView.axaml:548–579`, driven `:355–359`). Transition order is
   pinned and load-bearing: `ShieldOutline.Transitions[0] = Opacity`, `[1] = Fill`;
   `ShieldFilled.Transitions[0] = Opacity` (`.cs:194–198`).
2. **Glow reveal** — `GlowHalo.Opacity → 1`, **300ms `Ease.OutQuint`** (`.cs:985–989`; duration/curve
   swapped per direction by `PrepareStateTiming`, `:680–684`).
3. **Arc dissolve** — see §3.3.
4. **Double sonar ping + disc bloom** — see below.

**Sonar (lead ring):** `Ellipse.Sonar.pulsing`, `scale 1 → 1.6`, `opacity 1 → 0`, **600ms**, inline
`OutQuint`, `IterationCount=1`, `FillMode.Forward` (`ConnectHeroView.axaml:111–135`). Base ellipse is
200×200, stroke 2, `Brush.Ring.Inner` (`:437–448`).

**Sonar echo:** a second, quieter ring — starts at `opacity 0.5`, `scale 1 → 1.5`, same 600ms
`OutQuint`, launched **+120ms** after the lead (`ConnectHeroView.axaml:137–161`; timer at
`.cs:1017–1025`). Stroke 1.5 (`:452–463`). The intent is stated explicitly: "a settled double ping —
*locked* — at most TWO rings, not a radar" (`:137–139`).

**Connect bloom:** the disc *lands* — `scale 1 → 1.04` over **180ms**, then `1.04 → 1.0` over **260ms**,
both legs `OutQuint`, via the same self-centring `_discScale` used by press
(`PlayConnectBloom` → `PlayDiscSettle(1.04, 180, 260)`, `.cs:1036–1075`). Explicitly a *settle*, not a
bounce: peak ≤1.04, never dips below rest, no elastic (`:1036–1039`). Leg 2 ends ≈440ms, settling on
top of the fully-revealed glow, inside the 600ms budget.

Both bloom and sonar bail if a press starts mid-flight so the two animators never fight over
`_discScale` (`.cs:1051, 1063–1066`).

**Disconnect** is the same choreography at **75% tempo**: state 165ms, reveal 225ms, and the glow's
easing flips from `OutQuint` (reveal) to `Standard` (hide) (`PrepareStateTiming`, `.cs:661–685`;
selected by `state is Idle or Error`, `:326`).

**Android:** the crossfade + tint + glow reveal + single sonar already exist and match token for token
— `applyConnectedState` (`MainActivity.kt:1658–1712`) with `durState`/`durReveal`/`connect_confirm.xml`
(`res/anim/connect_confirm.xml` = scale 1→1.6, alpha 1→0, `motion_emphasis` 600, `ease_out_quint`), and
the 75% reversal (`revState = durState*3/4`, `revReveal = durReveal*3/4`, `MainActivity.kt:1753–1754`).
Android also fires `HapticFeedbackConstants.CONFIRM` on the fill beat — **even under reduced motion**
(`:1669`), which the desktop has no analogue for and should not try to fake.
**Missing on Android: the echo ping (+120ms, α0.5, ×1.5) and the disc bloom (1.0→1.04→1.0).**

### 3.3 Arc wind-up and dissolve

The connecting arc is one `Ellipse`, Ø190, stroke 3 `Brush.Accent`, `StrokeDashArray="56,143"`,
`StrokeLineCap=Round` — a single ~28% dashed segment (`ConnectHeroView.axaml:476–495`). Its centre is
pinned **three ways** (cell alignment Center, `RenderTransformOrigin="0.5,0.5"`, and explicit
`RotateTransform.CenterX/Y = 95`) so orbiting is physically impossible; the comment at `:465–475`
records that a second counter-arc was removed because it *did* orbit for lack of an origin.

**Steady spin:** `.spinning` = rotate 0→360, **1.2s linear**, infinite. The period was moved 1.1 → 1.2s
so that against the 850ms glow/shield breathe it forms a calm ~3:2 relationship and the three
connecting tempos read as one system (`ConnectHeroView.axaml:22–39`).

**Wind-up (entering Connecting from rest):** `.arc-windup` = a **one-shot** 0→360° ramp over **200ms**
`OutQuint`, `FillMode.Forward`, run in parallel with an opacity fade `0 → 1` (200ms `OutQuint`), then
handed off to `.spinning`. 360° ≡ 0°, so the seam is invisible (`ConnectHeroView.axaml:41–64`; driver
`StartArcWindup`, `.cs:823–863`).

Two subtleties worth copying: the hand-off timer is created **inside** the dispatcher `Post` so its
200ms is measured from when the animation actually attached — otherwise it led the animation by a
frame and produced a visible angle rollback (`.cs:832–836`); and wind-up runs only on a *fresh* entry,
computed from the **previous** `_visualState` rather than from the `animate` flag, because the state
arrives with `animate: false` (`.cs:336, 348–349`).

**Dissolve (Connecting → Connected):** the arc is **not** hidden on the payoff frame (that read as a
blink). Opacity `1 → 0` over **220ms `Ease.Standard`**, `.spinning` left attached so it keeps rotating
while it dissolves into the glow; `IsVisible=false` and `Opacity=1` reset happen 220ms later, and only
if we are still `Connected` (`DissolveArc`, `.cs:869–895`).

**Connecting-state secondary signals:**
- **Glow breathe:** `scale 0.96 ↔ 1.04`, `opacity 0.3 ↔ 0.6`, **850ms `SineEaseInOut`, `Alternate`,
  infinite** (`ConnectHeroView.axaml:66–86`); base opacity is seeded to 0.6 so the hand-off to the
  connected reveal is smooth (`.cs:977`).
- **Shield breathe:** the outline (already accent-filled) pulses `opacity 1 ↔ 0.8` on the **same 850ms
  sine, `Alternate`** — deliberately in unison, so two synchronised waves read as one calm breath
  (`ConnectHeroView.axaml:88–109`). It is **opacity-only**: "no transform, no centre — it physically
  cannot fly out of a corner" (`:90–92`).

**Android:** the connecting arc is a Material `CircularProgressIndicator` at 212dp
(`res/layout/activity_main.xml:237–247`) shown/hidden via its own `show()`/`hide()` grow/shrink
(`refreshConnectArc`, `MainActivity.kt:1880–1892`), and the glow breathe matches exactly (850ms,
0.3↔0.6, 0.96↔1.04, `INFINITE`/`REVERSE`, `AccelerateDecelerateInterpolator` —
`startConnectingAnim`, `MainActivity.kt:1828–1859`). **Missing on Android:** the 200ms wind-up ramp,
the 220ms dissolve, and the shield-outline breathe. Android's arc is also ref-counted so subscription
loads reuse it (`connectArcSubLoads`, `MainActivity.kt:1884, 1898–1908`) — a feature the desktop lacks.

### 3.4 Ambient "alive" layer (desktop-only)

Two very slow, low-contrast loops behind the shield so the hero breathes in **Idle** and **Connected**
too — not a frozen picture (`ConnectHeroView.axaml:163–268`).

| Layer | Idle | Connected |
| --- | --- | --- |
| `AmbientRing` (Ø222, stroke 1.5, `Ring.Inner`) | `scale 0.99 ↔ 1.04`, `α 0.35 ↔ 0.7`, **6s** `SineEaseInOut` `Alternate` | `scale 1 ↔ 1.05`, `α 0.5 ↔ 0.95`, **5s** |
| `AmbientSonar` (Ø200, stroke 1.5) | `scale 1 → 1.3`, `α 0 → 0.45 @18% → 0`, **6.5s** `OutQuint`, **not** Alternate | `scale 1 → 1.34`, `α 0 → 0.6 @15% → 0`, **5.5s** |

Design notes worth carrying: the two periods are deliberately **out of phase** (6s + 6.5s) so it reads
as "alive", not "busy" (`:169`); the wave is *not* `Alternate` because `α = 0` at both ends means the
scale snap-back at the loop seam is invisible — no click (`:216–218`); and the ring base was moved
220 → **222** so its 1.04 peak (≈231) reads as one ring expanding past the static 228 ring rather than
beating against it (moiré) (`:354–355`).

Gating (`SetAmbient`, `.cs:919–952`): present only in Idle and Connected; suppressed in Connecting
(already has motion) and Error (static); off under lite, off in the empty/onboarding state, off when
suppressed, and — **P0-4** — off in Idle when there is no server, because there is nothing to invite
(`:926–928`). All ambient layers are `IsHitTestVisible=False` and sit first in the `Panel` so they
render **under** the disc.

**Android:** nothing equivalent. This is the largest purely-additive item.

### 3.5 Error state

A fourth visual state beyond Idle/Connecting/Connected (`ConnectHeroView.axaml.cs:29–40`), because a
failure that silently fell back to Idle looked like an ordinary disconnect (`:35–38`).

- shield outline tinted `Brush.Red`, caption + hint red, no loops, no sonar — a static end state
  (`.cs:379–402`)
- **Error contract:** one quiet dip `1 → 0.98 → 1`, **150 + 150ms `OutQuint`**
  (`PlayErrorContract` → `PlayDiscSettle(0.98, 150, 150)`, `.cs:1042–1044`) — explicitly *not* a shake
- **Retry hint** fades `0 → 1` over 220ms `Standard` on fresh entry only, instant otherwise; hidden and
  reset outside Error (`ConnectHeroView.axaml:615–626`, `.cs:154, 430–455`)
- the disc stays a button: tapping it re-fires `ConnectToggleRequested`, and the next `Connecting` wipes
  the Error branch (`.cs:380–383`)
- reached on the **reverse tempo** (165/225) since Error is a "cooling" target (`.cs:326`)

**Android:** no Error visual state in `MainActivity` — failures fall to idle. Full gap.

### 3.6 Entrance choreography — 4 authored beats (not a drip)

Both the onboarding and the login method column use the **same** recipe: 4 beats, members of a beat
**share** its delay so a group appears as one semantic unit.

`OnboardingView.axaml.cs:144–150`:

| Beat | Children | Delay |
| --- | --- | --- |
| 1 · shield mark | 0 | 0ms |
| 2 · identity (wordmark + title + subtitle) | 1–3 | 60ms |
| 3 · "get access" (QR + clipboard) | 4–5 | 140ms |
| 4 · "sign in" (divider + Telegram + site) | 6+ | 200ms |

`LoginView.axaml.cs:1219–1225`:

| Beat | Children | Delay |
| --- | --- | --- |
| 1 · shield mark | 0 | 0ms |
| 2 · identity | 1–3 | 60ms |
| 3 · segment "Вход \| Регистрация" | 4 | 120ms |
| 4 · form + demoted alternatives, as ONE group | 5+ | 180ms |

Per-element reveal (`PlayReveal`, `OnboardingView.axaml.cs:157–196`, mirrored `LoginView.axaml.cs:1229–1268`):

- child 0 (the shield): `scale 0.90 → 1` — the **same** scale-in vocabulary as the connect hero
  (`OnboardingView.axaml.cs:34–37`)
- every other child: `translateY 8 → 0`
- both + `opacity 0 → 1`, `Motion.Dur.Reveal` **300ms `Ease.OutQuint`**
- total ≈500ms (200 delay + 300), then **complete stillness** — no ambient loops on a product-register
  screen; the shield is a brand mark, not an indicator (`OnboardingView.axaml.cs:20–23`)
- `FillMode.None` + restore-to-base in `finally`, so the reveal cannot shadow a button's `:pressed`
  scale (`:165, 189–195`)
- a safety `DispatcherTimer.RunOnce` at `delay + 300 + 250` guarantees full visibility if the animation
  is interrupted by detachment (`:174–181`)
- **hit-testing is never gated by the animation** — buttons are clickable throughout (`:26`)
- children are pre-hidden **only when motion is on**; under lite/preview/design mode they are simply
  left visible and the stagger never runs, because "a reveal must improve something already visible"
  (`:51–61, 24–25`)

**Entrance chip** (`HomeAccountChip.axaml.cs:134–190`): on account resolve, fade + `translateY 8 → 0`,
`Dur.Reveal` 300ms `OutQuint`, delayed **120ms** so it reads as landing *after* Home paints. The
shell's region stagger deliberately excludes it to avoid two animators on one `Opacity`
(`MainWindow.axaml.cs:646–649`).

**Android:** no entrance choreography on the login/onboarding screens at all.

### 3.7 Success beat (login)

`LoginView.axaml.cs:930–1008`, guarded once by `_beatStarted`, and `TryHandoff()` runs in `finally` so a
real success can **never** strand the user on the login page (`:949–957`).

**Path A — the awaiting ring** (`PlayAwaitingSuccess`, `:961–989`), the arc *completes into a check*:

1. arc-spinner fades out **and** the full ring fades in — both `Motion.Dur.State` 220ms `OutQuint`
2. at **+160ms** (overlapping step 1): plane glyph fades out `Dur.PressOut` 160ms `OutQuint`, and the
   check scale-fades in (`0.9 → 1` + `0 → 1`), same 160ms `OutQuint`
3. **hold 120ms** — deliberately "a truth signal, not decor" (`:987`)

**Path B — badge success** (`PlayBadgeSuccess`, `:993–1008`), for site/2FA/register/verify-email: the
active block fades out `Dur.PressOut` 160ms `Standard` while a 64 check badge scale-fades in
`Dur.State` 220ms `OutQuint`, then the same **120ms hold**.

Lite path for both: jump to the end frame, still hold 120ms (`:966–975, 998–1004`).

Supporting loops on the awaiting screen: the arc spinner (1.1s linear infinite,
`LoginView.axaml:178–195`) and the **plane "listening breathe"** — `opacity 1 → 0.55 → 1` +
`scale 1 → 0.94 → 1` over **1.6s `Ease.Standard`**, symmetric, infinite (`LoginView.axaml:200–226`).
The 1.6s period is explicitly a *second, slower rhythm* over the 1.1s spin.

Block-to-block crossfade inside the page (`BuildScaleFade`, `LoginView.axaml.cs:1283–1294`, used at
`:511–512`): incoming `scale 0.98 → 1` + fade in, outgoing `1 → 0.98` + fade out, `Dur.State` 220ms
`Ease.Standard`.

**Android:** no success beat — the login activity finishes.

### 3.8 Sync settle (post-login / cold start)

`AccountSyncView.axaml.cs`:

- **Stage line dip** — when the real phase advances, `opacity → 0` (75ms `Standard`), swap text,
  `0 → 1` (75ms `Standard`) (`:158–173`)
- **Column crossfade** (loading ⇄ error), **in place**, 150ms `Standard` both ways — the overlay never
  drops, the shell gate is not touched (`:230–244`)
- **Success settle** — when syncing goes true→false with no error and still signed in, the arc **stops**
  (class removed, not abruptly cut) and the shield plays `scale 1.0 → 1.04 → 1.0` at cue 0/0.5/1 over
  `Motion.Dur.Reveal` **300ms `OutQuint`**, transform-only (`RunSettle` `:259–271`, `RunSettlePop`
  `:273–306`)
- the arc spins **only while visible and not in error** (`UpdateSpinner`, `:119–136`), and every
  in-flight animation is cancelled the moment the overlay hides (`OnPropertyChanged`, `:80–115`)

**Android:** no sync overlay of this shape.

### 3.9 Theme transition — circular reveal

`MainWindow.axaml.cs:1636–1767`. This is a genuinely distinctive moment.

1. Snapshot the current chrome into a `RenderTargetBitmap` at render scaling (`:1656–1672`)
2. Show the snapshot full-screen over an opaque **old** `Brush.Bg` backdrop so the transparent title
   strip cannot leak the new theme (`:1674–1681`)
3. Apply the theme swap **underneath** the opaque snapshot — live controls repaint invisibly, same UI
   tick (`:1683–1685`)
4. Animate a clip = `window rect EXCLUDE growing circle` at 16ms ticks; radius =
   `Ease.OutQuint(t) × maxCornerDistance`, over **`ThemeRevealDuration` = 520ms** (`:1626, 1692–1727`)
5. Origin = the last pointer-press point inside the window (captured on a **tunnelling**
   `handledEventsToo` handler at `:156, 1632–1633`), falling back to the centre (`:1730–1737`);
   radius covers the farthest corner (`MaxCornerDistance`, `:1740–1745`)
6. Bitmap is disposed in `FinishThemeTransition` — no `RenderTargetBitmap` leak (`:1751–1761`)

Escape hatches: lite, hidden window, or a zero-size chrome → instant swap (`:1644–1651`); any render
failure → dispose + instant swap (`:1666–1672`).

**Android:** `ViewAnimationUtils.createCircularReveal` on a snapshot `ImageView` is the direct
equivalent. Nothing like it exists today.

---

## 4. Reduced-motion contract

Two independent inputs, one broadcast:

- **App setting** — `_config.UiItem.LiteMode`, pushed live through
  `MotionState.SetLite` / `MotionState.Changed` (`Common/MotionState.cs`), seeded without notifying at
  startup (`Initialize`, `:27`). Before this existed, the flag was read **once per constructor**, so
  flipping it left the shield spinning until the next launch (`MotionState.cs:6–11`).
- **OS setting** — Win32 `SPI_GETCLIENTAREAANIMATION` (0x1042) via P/Invoke, non-Windows returns
  `true` (`ConnectHeroView.axaml.cs:1131–1155`, duplicated in `SubscriptionMetaView.axaml.cs:211+` and
  `ServerListView.axaml.cs:427–440`). Effective flag =
  `LiteModeEnabled() || !SystemAnimationsEnabled()` (`ConnectHeroView.axaml.cs:188`).

**The two-lever rule** (`GlobalStyles.axaml:1360–1379`) — Avalonia cannot let a competing style
*cancel* a running keyframe animation, only a non-matching selector removes it. So:

1. **Transitions** are emptied under `:is(Window).lite …` — buttons, toggle buttons, `ServerRow`,
   `PriceOption`, `ConnectDisc`, nav glyphs/labels, scrollbar thumb, and (because window-level
   selectors do not reach template children) the iOS switch's `track` / `PART_SwitchKnob` / `knobFill`
   parts individually (`GlobalStyles.axaml:1383–1446`).
2. **Looping keyframe animations** are gated at the **selector**: `:is(Window):not(.lite) …`
   — `Ellipse.Spinner.spinning` (`:1331`), `:is(Control).SkeletonPulse` (`:1285`), `Button.Primary`
   and `Button.Tonal` hover crossfades (`:426, 476`), plus per-view copies in `BuyView.axaml:145, 162`,
   `AccountView.axaml:127, 150`, `LoginView.axaml:178, 200`.

Imperative animators check `MotionState.IsLite` at the call site and jump to the end frame:
`SwapContent` (`MainWindow.axaml.cs:431`), `AnimateSubPageIn/Out` (`:1128, 1151`), `CrossfadeShellTo`
(`:887`), `PlayTabEntrance` (`:615`), `MoveRailIndicator` (`:562`), `PositionIndicator`
(`BottomNavBar.axaml.cs:145`), `RunThemeTransition` (`:1647`), `AnimateWindowSize` (`:1346`),
`CrossfadeValue`/`SetProxyChevron`/`RevealPanel` (`SettingsView.axaml.cs:198, 241, 265`), and the whole
hero (`ConnectHeroView.axaml.cs:619–648`).

Two hero-specific lite behaviours: the connecting **arc is hidden entirely**, not frozen — "the owner
does not want a frozen blue arc in lite mode"; the caption alone carries the state
(`ConnectHeroView.axaml.cs:785–790`); and the **speed/uptime stats row is hidden completely** in lite
(`:190–191, 625–626`).

**Off-screen / inactive discipline** (a real CPU regression the repo fights): every infinite loop is
torn down when the window is minimised or hidden-to-tray (`UpdateVisibilityPause`,
`ConnectHeroView.axaml.cs:547–575`) **and** when this hero's layout goes inactive — the inactive
keep-alive hero sits at `Opacity=0`, which does **not** stop Style animations (`Deactivate`/`Activate`,
`:585–606`). Both fold into `MotionSuppressed` (`:112`). Indicator animators check `IsWindowLive()`
(`MainWindow.axaml.cs:562`, `BottomNavBar.axaml.cs:185–186`).

**Android equivalent:** `MotionUtils.animationsEnabled()` reads
`Settings.Global.ANIMATOR_DURATION_SCALE != 0`, with `View.reducedMotion()` as the inverse
(`java/com/v2ray/ang/util/MotionUtils.kt:26–50`), already used at every Android call site
(`MainActivity.kt:319, 470, 822, 1671, 1744, 1806, 1834`). **What Android lacks: an in-app "Lite mode"
toggle and a live broadcast** — there is no `MotionState` analogue, so the setting cannot exist yet.
The Android port would be a small `object MotionState { var isLite; val changed: ... }` plus
`fun View.reducedMotion() = MotionState.isLite || !context.animationsEnabled()`.

---

## 5. Focus-visible handling

Mechanism: `Control.FocusAdorner` — the ring is drawn in the **adorner layer** above the control, so it
(1) causes zero reflow, (2) is not clipped by a card or scroll parent, and (3) Avalonia shows it
**only** on keyboard focus-visible, never on a mouse click (`GlobalStyles.axaml:1019–1037`).

| Target | Ring | File:line |
| --- | --- | --- |
| `Button.Tonal`, `.OutlinedAccent`, `.Destructive` | outer, `Margin -2`, 2px `Accent`, radius **18** (= 16 + 2) | `:1040–1047` |
| `Button.LinkAction` | outer, `-2`, radius **14** (= chip 12 + 2) | `:1050–1057` |
| `TextBox` (both Incy themes) | outer, `-2`, radius **16** (= search 14 + 2) | `:1060–1067` |
| `Border.AccountChip` | outer, `-2`, radius **22** (= card 20 + 2) | `:1070–1077` |
| `ToggleButton.Segment` | outer, `-2`, radius **14** | `:1081–1088` |
| `Button.Primary` / `.Tall` | **inner** ring, no negative margin, radius 16, `Brush.OnAccent` @ **0.4** — an outer blue ring is invisible on a blue fill | `:1091–1098` |
| `Border.SettingRow` | **inner** ring, `Margin 3`, radius 12 — the row spans the card's full width so an outer ring would escape the card's rounded edge | `:661–668` |

Rule: **adorner radius = control radius + 2** for outer rings; inner rings use the control radius.
Colour is `{DynamicResource Brush.Accent}` so mono neutralises it to grey.

**The ring survives lite** — deliberately: "a11y outranks motion", and lite only zeroes `Transitions`,
which a `FocusAdorner` is not (`GlobalStyles.axaml:1029–1031`, `:662`).

**Android equivalent:** there is no adorner layer. The nearest constructs are a
`android:foreground` state-list drawable keyed on `state_focused` (ideally with `state_hovered=false`
for mouse/TV separation), or a `MaterialShapeDrawable` stroke, applied per-widget. The
`radius + 2dp` rule and the *inner ring on filled accent* rule must be reproduced by hand — an outer
2dp inset drawable does not exist for free.

---

## 6. Brand assets

- **Font:** `Assets/Fonts/SpaceGrotesk.ttf` (136,676 bytes — a variable font; the type styles use
  `FontWeight` Bold/Medium against genuine masters, no synthetic bold). Registered as
  `Font.Grotesk` and `Font.Numeric` (`GlobalResources.axaml:166, 180`).
- **CJK fallback:** `Assets/Fonts/NotoSansSC-Regular.ttf` (10.5 MB) — set as the *default* family
  and first fallback, with per-OS emoji/symbol fallbacks after it
  (`Common/AppBuilderExtension.cs:5–48`, wired at `Program.cs:95`).
- **Assembly identity:** `AssemblyName` / `Product` / `AssemblyTitle` / `Company` = **`departament`**
  (`v2rayN.Desktop.csproj:9–14`) — Task Manager reads these. Asset URIs are therefore
  `avares://departament/...`.
- **Wordmark:** an 18×18 `Brush.Accent` square, `CornerRadius 6`, containing the letter **"d"**
  (Grotesk 11 Bold, `Brush.OnAccent`), followed by "departament" at Grotesk 14 Bold `OnSurface`
  (`MainWindow.axaml:329–349`). Title bar height **28** (was 40 → 32 → 28; `:314–317`).
- **Tray icons:** `Assets/NotifyShieldIdle.ico` / `NotifyShieldOn.ico` (the two departament shields),
  plus legacy `NotifyIcon1–4.ico` and `v2rayN.ico`. `App.axaml:31` uses `NotifyShieldIdle.ico`;
  `MainWindow.axaml:22` uses `NotifyShieldOn.ico` for the window icon.
- **Shield geometry:**
  `Geo.Shield` = `M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z` (`GlobalResources.axaml:316`),
  reused as `Geo.Login.Shield` (`LoginView.axaml:34`). The hero uses a **higher-fidelity pair** —
  `ShieldOutline` with an evenodd hole (`F0 M12,2.2 L19.6,5.2 …`) and `ShieldFilled` with the same
  silhouette (`ConnectHeroView.axaml:542–580`), ports of Android's `ic_shield_outline` /
  `ic_shield_filled` (both present at `res/drawable/`).
- **Nav glyphs:** `Geo.Nav.Home` / `.Servers` / `.Settings` / `.Account` — Material 24dp filled paths
  ported from Android `ic_nav_*` (`GlobalResources.axaml:311–315`). Shared toolbar glyphs
  `Geo.ArrowBack`, `Geo.ChevronRight`, `Geo.Check` (`:319–323`).
- **Flags:** 16 circular PNGs at `Assets/Flags/*.png` (de, eu, fi, fr, gb, jp, lv, nl, pl, ru, se, sg,
  tr, ua, us + `xx.png` globe fallback), resolved by `RemarkToFlagConverter` from the server remark
  (`GlobalResources.axaml:5–6`, `Converters/RemarkToFlagConverter.cs`, `Common/FlagResolver.cs`).
- **Legacy v2rayN icons:** the `building_*` StreamGeometries at `GlobalResources.axaml:11–18` belong to
  the inherited geek windows — not part of the design language, do not port.

---

## 7. Android parity map — consolidated

### 7.1 Already at parity (verified — do not re-do)

| Item | Desktop | Android |
| --- | --- | --- |
| Duration tokens 90/160/220/300/40/600 | `Motion.cs:22–53` | `res/values/motion.xml` |
| The three easing curves (identical control points) | `Motion.cs:60–73` | `res/interpolator/ease_out_{quart,quint}.xml`, `ease_standard.xml` |
| 75% reverse tempo (165/225) | `ConnectHeroView.axaml.cs:661–685` | `MainActivity.kt:1753–1754` |
| Cold-start assemble 400ms, 0.9→1 + fade | `ConnectHeroView.axaml:270–295` | `res/anim/shield_assemble.xml`, `MainActivity.kt:319–321` |
| Sonar 1→1.6 + fade, 600ms `OutQuint`, once | `ConnectHeroView.axaml:111–135` | `res/anim/connect_confirm.xml` |
| Connect press 0.94, 90 quart-in / 160 quint-out | `ConnectHeroView.axaml.cs:696–739` | `MainActivity.kt:1805–1821` |
| Glow breathe 850ms, α0.3↔0.6, s0.96↔1.04 | `ConnectHeroView.axaml:66–86` | `MainActivity.kt:1846–1858` |
| Shield crossfade + tint over `motion_state` | `ConnectHeroView.axaml:548–579` | `MainActivity.kt:1682–1694` |
| Glow reveal over `motion_reveal` `OutQuint` | `ConnectHeroView.axaml.cs:985–989` | `MainActivity.kt:1697–1700` |
| List stagger 12dp, ×40ms, 300ms `OutQuint`, cap 8 | `ServerListView.axaml.cs:225–356` | `MainActivity.kt:815–839` |
| Nav tint tween 200ms `Standard` + bold swap | `GlobalStyles.axaml:800–828` | `MainActivity.kt:415, 420–436` |
| Reduced-motion at every call site | `MotionState` + SPI | `MotionUtils.kt`, used 7× in `MainActivity` |
| Colour palette (dark) incl. `#4C8DFF`, `#0A0B0D`, `#141619`, `#1A1D21`, `#20242B`, `#9BA1AD`, `#2A2E36`, `#17325C`, `#CFE0FF`, `#22C55E`, `#F04452` | `GlobalResources.axaml:39, 63–96` | `res/values-night/colors.xml` |
| Spacing 4/8/12/16/24/32, radius 12/20/12/100/24, tile 40, glyph 22, row 56, gutter 16, dots 6/8/8 | `GlobalResources.axaml:137–163` | `res/values/dimens.xml` |
| Type scale 34/24/16/16/14/13/12/11 + tnum numerics | `GlobalStyles.axaml:272–327` | `res/values/styles.xml:56–127` |

### 7.2 Missing on Android — ranked by impact

| # | Desktop moment | Spec | Android construct to use |
| --- | --- | --- | --- |
| 1 | **Travelling nav indicator** | one 34×3dp accent bar slides X to the active third's live centre, 220ms `OutQuint`; first show instant; read `from` before cancel | Replace the three `nav_*_dot` Views (`activity_main.xml:565–574`) with ONE View + `ObjectAnimator.ofFloat(view, TRANSLATION_X, …).setDuration(220).setInterpolator(easeOutQuint)`; compute target from `navItem.left + width/2 − 17dp` after `doOnPreDraw` |
| 2 | **Arc wind-up** | opacity 0→1 + one-shot 0→360° over 200ms `OutQuint`, then hand off to steady 1.2s linear spin; angle 360≡0 = seamless | Swap the `CircularProgressIndicator` for a custom arc `View`, or keep it and add a `ValueAnimator.ofFloat(0f,360f)` 200ms `easeOutQuint` on `rotation` before starting an infinite `LinearInterpolator` `ObjectAnimator`; schedule the hand-off from the animator's own `doOnStart`, not a `postDelayed` before it |
| 3 | **Arc dissolve** | on connect: `alpha 1→0` over 220ms `Standard` **while still rotating**, hide after | `arc.animate().alpha(0f).setDuration(220).setInterpolator(easeStandard).withEndAction{ hide() }` — do **not** call `progress.hide()` on the payoff frame |
| 4 | **Connect bloom** | disc `1 → 1.04` 180ms `OutQuint`, then `1.04 → 1.0` 260ms `OutQuint` | Two chained `ViewPropertyAnimator`s on `cardConnect` in `applyConnectedState`; bail if a press is in flight |
| 5 | **Sonar echo** | second ring, `α 0.5`, `scale 1→1.5`, 600ms `OutQuint`, **+120ms** after the lead | Duplicate `viewConnectPulse`, `postDelayed(120)` + a variant of `connect_confirm.xml` with `fromAlpha=0.5`, `toXScale=1.5` |
| 6 | **Error state** | red shield/caption, retry hint fade-in 220ms, disc contract `1→0.98→1` (150+150 `OutQuint`), no loops; entered on reverse tempo | New `applyErrorState(animate)` alongside `applyConnectedState`/`applyIdleState` in `MainActivity` |
| 7 | **Ambient alive layer** | ring 6s/5s breathe + wave 6.5s/5.5s, out of phase, `Ring.Inner` tone, off in Connecting/Error/empty/no-server/lite/background | Two `ObjectAnimator`s with `REVERSE`/`RESTART` on new sibling Views under the disc; must be cancelled in `onStop` |
| 8 | **Press depth on the disc** | glyph parallax dip `1→0.97` + black scrim `0→0.12`, both mirroring 90/160 | Add an overlay `View` (circle) + animate `imgConnect.scaleX/Y` alongside `animateConnectPress()` |
| 9 | **Shield-outline breathe** | `alpha 1↔0.8`, 850ms sine, `Alternate`, **in unison with the glow breathe** | Add an `ALPHA` `PropertyValuesHolder` on `imgConnect` to the existing `connectPulse` animator |
| 10 | **Login entrance beats** | 4 beats 0/60/120/180ms; beat 1 = `scale 0.90→1`, rest `translateY 8→0`; all + fade, 300ms `OutQuint`; ≈500ms then still | Per-child `ViewPropertyAnimator` with `setStartDelay(beatDelay)`; pre-hide only when motion is on; restore in `withEndAction` **and** a safety `postDelayed` |
| 11 | **Onboarding entrance beats** | same, delays 0/60/140/200ms | same |
| 12 | **Success beat** | arc→full ring 220ms `OutQuint`; at +160ms plane→check (`0.9→1`) 160ms `OutQuint`; **hold 120ms**; badge variant 160 out / 220 in | New views in the auth layout + a coroutine with `delay(160)` / `delay(120)` |
| 13 | **Directional tab slide** | enter `±16dp X` + fade 220ms `OutQuint` **in parallel with** exit fade 150ms `Standard`; sign from nav-index delta | Rewrite `updateTabVisibility` (`MainActivity.kt:483–493`) — drop the `withEndAction` chaining, animate both at once, use `TRANSLATION_X` |
| 14 | **Theme circular reveal** | 520ms `OutQuint` growing circle from the tap point, over a bitmap snapshot of the old theme | `ViewAnimationUtils.createCircularReveal` on a snapshot `ImageView` overlay, recreate() underneath |
| 15 | **Sub-page push/pop** | push `X 16→0` + fade 300ms `OutQuint`; pop `0→16` + fade 200ms `Standard` | Fragment transaction `setCustomAnimations` with 4 XML anims on these exact values |
| 16 | **Region stagger on tab activation** | ≤3 top-level regions, `translateY 6→0` + fade, 220ms `OutQuint`, 40ms steps, once per session per tab | Same pattern as `revealListStagger`, applied to the tab group's direct children with a `Set<Int>` guard |
| 17 | **Sync settle** | stage-line dip 75/75ms; column crossfade 150ms; success `scale 1→1.04→1` 300ms `OutQuint` | Only if a sync overlay screen is built |
| 18 | **Lite mode + live broadcast** | in-app toggle, instant effect, no restart | `object MotionState` + a settings switch; fold with `animationsEnabled()` |
| 19 | **`Brush.RedText`** | brighter error red for TEXT (`#FF6069` dark / `#C42B32` light) | New `@color/red_text` + `?attr/redTextColor` in all 4 theme sets |
| 20 | **`OnSurfaceVariantHover`** | one step darker glyph for hover/focus (mouse + TV) | New colour; only needed if TV/mouse focus states are pursued |
| 21 | **`Radius.Search` 14dp / `Radius.Button` 16dp** | desktop input radius = 14, CTA radius = 16 (capsules rejected) | Add `@dimen/radius_search 14dp`, `@dimen/radius_button 16dp` and replace `cornerRadius=26dp` on `btn_pay` / `btn_telegram` (see §1.5) |
| 22 | **Focus rings** | radius + 2, inner ring on accent fills | `state_focused` foreground drawables per widget class |

### 7.3 Divergences to *reconcile* (both sides already have something, they disagree)

| Item | Desktop | Android | Recommendation |
| --- | --- | --- | --- |
| Generic button press timing | 120ms `OutQuart` both legs, scale **0.97** (`GlobalStyles.axaml:400–408`) | 90ms in / 160ms out `ease_out_quart`, scale **0.96** (`press_scale.xml`) | Android's is the token-correct one (90/160). Desktop is the outlier — align desktop to 90/160, or accept 120 as a mouse-vs-finger difference and document it. |
| Nav press | 160ms `OutQuint`, unified across rail + bottom bar (`GlobalStyles.axaml:763–768`, `BottomNavBar.axaml:48`) | **100ms down / 120ms up, no interpolator** (`nav_press.xml`) | Fix Android: `@integer/motion_press_out` + `@interpolator/ease_out_quint`. The desktop comment (`GlobalStyles.axaml:760–762`) records this exact unification as "P1-3". |
| Tab transition | parallel, directional X ±16, 220/150 | sequential, vertical 8dp, 200/150 | Adopt the desktop shape (item 13 above). |
| Sub-page transition | 300/200 | n/a | Note the internal inconsistency with the 220/150 tab swap; pick one before porting. |
| Body/Subtitle/Caption family | Space Grotesk (global `TextBlock` setter) | system font (`styles.xml:87, 94, 101`) | Decide once. Grotesk for body at 13–14sp is legible but changes the entire feel of the Android app. |
| Body tracking | 0 | 0.01/0.01/0.02 em | Trivial; match Android's (it is the more considered value). |
| Arc size | Ø190, dash `56,143` (~28% arc), stroke 3 (`ConnectHeroView.axaml:476–495`) | `indicatorSize=212dp` `CircularProgressIndicator` (`activity_main.xml:247`) | Desktop has a dead `Size.ConnectArc = 212` token that matches Android; the shipped arc is 190. Pick one number and delete the other. |
| `Ease.OutExpo` / `Dur.Slow` | declared, **zero consumers** | absent | Either wire the auth→home hand-off on both or delete the token pair. Do not port a dead token. |

---

## 8. Caveats

- Everything above comes from files I opened in this session; every claim carries a `file:line`.
- I did **not** build or run either app — all timings are as declared in source, not measured.
- Android citations are limited to the files listed in the header table; I did not audit every Android
  layout, so "missing on Android" means "absent from the files I read" (`MainActivity.kt`,
  `activity_main.xml`, `motion.xml`, `res/anim`, `res/interpolator`, `MotionUtils.kt`, `styles.xml`,
  `dimens.xml`, `values-night/colors.xml`). Auth/onboarding Android activities were not read.
- The two "declared but unused" findings (`Ease.OutExpo`/`Dur.Slow`, `Size.ConnectArc`) are stated as
  grep results over `v2rayN.Desktop` excluding `obj/` and `bin/`.
