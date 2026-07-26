# Design audit — Departament VPN desktop (`v2rayN.Desktop`)

**Scope:** `/home/user/v2rayN/v2rayN/v2rayN.Desktop/**` — `App.axaml`, `App.axaml.cs` (theme overlay),
`Assets/GlobalResources.axaml`, `Assets/GlobalStyles.axaml`, `Views/LoginView.axaml`,
`Views/OnboardingView.axaml`, `Views/MainWindow.axaml`, `Views/SettingsView.axaml`,
`Views/ServerListView.axaml`, `Views/SubscriptionMetaView.axaml`, `Views/PaymentHistoryView.axaml`
(+ targeted reads of `SettingsViewModel.cs`, `SubscriptionMetaView.axaml.cs`, `ServerListView.axaml.cs`,
`MainWindow.axaml.cs`, `DevicesView.axaml`, `BuyView.axaml`, `BottomNavBar.axaml`).

**Rules applied:** `/home/user/dp/CLAUDE.md` (Incy design law) +
`/home/user/dp/.claude/skills/impeccable/SKILL.md` (Absolute Bans, AI-slop test) +
`reference/audit.md` (5-dimension scoring, P0–P3 severity) + `reference/craft.md` (production bar).

Every claim below is anchored to a file:line I read. Contrast ratios are WCAG 2.x relative-luminance
computations from the literal hex values in the token files, stated with the two colours used.

---

## Audit health score

| # | Dimension | Score | Key finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | 2/4 | Status chips fail contrast catastrophically in the Light theme (1.70:1 – 1.96:1); five distinct interactive archetypes sit below the 48px touch minimum |
| 2 | Performance | 3/4 | Virtualization, keep-alive tab host and a real reduced-motion lever are all in place; the Home radial gradient is painted twice in overlapping coordinate spaces |
| 3 | Theming | 2/4 | A 3-theme token system that is genuinely good — undermined by dark-theme hex literals applied directly from code-behind in `SubscriptionMetaView`, bypassing Light and Mono entirely |
| 4 | Responsive | 3/4 | Compact/wide morph, in-app UI scale, traffic-row reflow and ellipsis discipline are all deliberate; a few fixed `MaxWidth` values on values columns |
| 5 | Anti-patterns | 4/4 | No gradient text, no glassmorphism, no ALL-CAPS eyebrows, no emoji chrome, no nested cards, no hero-metric template. This does not read as AI-generated. |
| **Total** | | **14/20** | **Good — address the two weak dimensions (a11y, theming)** |

### Anti-patterns verdict: **PASS**

Run against impeccable's Absolute Bans and the AI-slop test, this codebase comes out clean, and it is
worth saying plainly because it is rare:

- **No gradient text** — no `background-clip: text` equivalent anywhere; every heading is a solid token.
- **No glassmorphism** — no decorative blur/backdrop surfaces.
- **No tiny uppercase tracked eyebrows** — `TextBlock.SectionHeader` is explicitly bold sentence-case at
  16px (`GlobalStyles.axaml:322-327`), with the rule written into the comment at line 321. The only
  uppercase in the product is `VLESS` / `TCP` / `REALITY` (`Common/ProfileDisplay.cs:15,30,39`), which
  are protocol acronyms, not kickers.
- **No numbered section markers**, no hero-metric template, no identical card grid.
- **No emoji as UI chrome** — the one flag emoji that could leak into a server name is *stripped*
  (`ServerListView.axaml:34`, `StripLeadingFlagConverter`) so the country reads from its tile instead.
- **No nested cards** — `Border.Card` is applied once per surface; `SubscriptionMetaView` is a card that
  *precedes* its rows rather than containing them (`ServerListView.axaml:92`).
- **Copy is genuinely in voice** — Russian sentence-case with active verbs throughout
  (`Common/L.Settings.cs:26-70`), locale-neutral technical tokens deliberately excluded from
  translation (documented at `Common/L.Settings.cs:16-18`).

**Two judgement calls I am explicitly *not* scoring as violations:**

1. **The rail active-indicator is not a banned side-stripe.** `MainWindow.axaml:500-509` is a 3×28 accent
   bar, which pattern-matches the "side-stripe border" ban — but it is a single *travelling* indicator
   that slides between slots (`GlobalStyles.axaml:816-820`), i.e. the Material 3 nav-rail idiom, not a
   static coloured stripe decorating a list item. Correct as built.
2. **The radial gradients are parity, not decoration.** `Brush.HomeGradient`
   (`GlobalResources.axaml:88-96, 124-132`) and `Brush.ConnectGlow` (`GlobalResources.axaml:269-277`)
   read as a conflict with the design law's "no decorative gradients/glows". They are not decorative:
   they are 1:1 ports of Android's `bg_home_gradient.xml`, `bg_connect_glow.xml`, `bg_connect_ring.xml`
   (confirmed present in `/home/user/dp/V2rayNG/app/src/main/res/drawable/`), where the glow is the
   *connected-state signal*. Cross-platform parity wins. See P2-4 for the one place this genuinely
   misfires.

---

## Executive summary

- **Total: 21 findings** — P0: 0 · P1: 5 · P2: 9 · P3: 7
- The design *system* here is unusually strong: one accent, one font, a real spacing scale, a real radius
  scale, a real motion token catalogue with reduced-motion gating, and a full three-theme token set with a
  monochrome overlay. Nearly every rule in the design law is written down somewhere in a comment.
- **The failures are all leaks out of that system, not absences of it.** The recurring shape is: a global
  token or component class exists, is correct, and is then locally overridden or bypassed by a view that
  hard-codes the same value slightly differently.

**Top 5:**

1. **P1** — Status chips are unreadable in the Light theme (1.70:1 and 1.96:1 against a 4.5:1 requirement)
   because the chip fill and ink tokens were never made theme-dependent.
2. **P1** — `SubscriptionMetaView.axaml.cs:30-32` caches three dark-theme hex literals and assigns them
   directly to live controls; expiry text renders at 2.59:1 in Light and a blue pin icon leaks into the
   monochrome theme.
3. **P1** — `SettingsView.axaml:68-135` re-declares a *local* `TextBox.IncyField` ControlTheme that shadows
   the global one, reinstating the exact radius drift (12 vs 14) that `GlobalResources.axaml:488-489`
   documents as fixed, and dropping the `:disabled` state.
4. **P1** — An invalid local-proxy port is silently reverted with zero user feedback
   (`SettingsViewModel.cs:369-375`, comment: *"Reject silently"*). No error state is designed.
5. **P1** — Five distinct interactive archetypes are below the 48px touch minimum: 44×22, 30×30, 32×32,
   34×34, 36 and 44.

---

## Detailed findings by severity

### P1 — Major (fix before release)

---

#### [P1-1] Status chips fail WCAG AA in the Light theme — two of four are effectively invisible

**Location:** `Assets/GlobalResources.axaml:226-231, 255-258`; consumed at
`Assets/GlobalStyles.axaml:1106-1137`, rendered at `Views/PaymentHistoryView.axaml:122-132`
**Category:** Accessibility / Theming

`Brush.StatusChip.*` (lines 255-258) and `Brush.Icon.Orange` / `Brush.Icon.Yellow` (lines 230-231) are
declared **outside** `ResourceDictionary.ThemeDictionaries` (which spans lines 59-135). They therefore hold
their dark-theme values in every theme. The chip renders ink = full colour on fill = same colour @ 18%,
composited over `Brush.Surface`, which in Light is `#FFFFFF` (line 102):

| Chip | Ink | Fill over white | Contrast | AA (4.5:1) |
|---|---|---|---|---|
| `pending` | `#FB923C` | `#FB923C` @18% ≈ `#FEEBDC` | **1.96:1** | fail |
| `canceled` | `#EAB308` | `#EAB308` @18% ≈ `#FBF1D2` | **1.70:1** | fail |
| `paid` | `#0B7D4A` (Light `Brush.Green`) | `#22C55E` @18% | **≈4.46:1** | borderline fail |
| `failed` | `#C42B32` (Light `Brush.Red`) | `#F04452` @18% | **≈4.48:1** | borderline fail |

The text is `TextBlock.Chip` at 11px (`GlobalStyles.axaml:313`), so the 4.5:1 small-text threshold applies
with no large-text exemption.

Note the second-order defect: `paid` and `failed` are *hue-mismatched* in Light — the ink resolves to the
Light green/red (`#0B7D4A` / `#C42B32`, lines 115-116) while the fill stays keyed to the dark green/red
(`#22C55E` / `#F04452`). A dark-green word on a light-mint pill.

**Impact:** In Light theme the payment-status colour — the only thing carrying "paid vs failed vs pending"
besides the word itself — is nearly unreadable. The colour is doing the semantic work and it fails.

**Recommendation:** Move all four `Brush.StatusChip.*` and both `Brush.Icon.*` keys into
`ResourceDictionary.ThemeDictionaries` and give Light its own values: darker inks (~L 35-40) on lighter
fills, and derive the fill hue from the same theme-resolved ink so the pair can never drift.
**Suggested command:** `/impeccable colorize`

---

#### [P1-2] `SubscriptionMetaView` paints dark-theme hex literals directly, bypassing Light and Mono

**Location:** `Views/SubscriptionMetaView.axaml.cs:29-32`, applied at lines `375, 390, 398, 404, 602, 679, 682`
**Category:** Theming / Accessibility

```csharp
//  Кэш-кисти повторяют токены Incy (тема одна, тёмная) — как в ConnectHeroView/MainWindow.
private static readonly IBrush _accent = new SolidColorBrush(Color.Parse("#4C8DFF"));   // Brush.Accent
private static readonly IBrush _muted  = new SolidColorBrush(Color.Parse("#9BA1AD"));   // Brush.OnSurfaceVariant
private static readonly IBrush _red    = new SolidColorBrush(Color.Parse("#F04452"));   // Brush.Red
```

The comment's premise — *"тема одна, тёмная"* (there is only one theme, dark) — is stale. Three themes now
ship: Dark, Light (`GlobalResources.axaml:59-135`) and the Mono/AMOLED overlay
(`App.axaml.cs:576-665`), all three surfaced in the UI at `SettingsView.axaml:698-766`. These three
brushes are assigned **unresolved** to `PinIcon.Foreground` and `ExpiryText.Foreground`. Line 416 in the
same file proves the correct pattern was known — it calls `ResolveBrush("Brush.Accent", _accent)`.

Measured consequences:

- **Light theme:** the subscription expiry date renders `#9BA1AD` on `Brush.Surface` `#FFFFFF` =
  **2.59:1** (needs 4.5:1 — it is 12px `Caption`, line 267). The "Просрочено" state renders `#F04452` on
  white = **3.71:1**, also failing.
- **Mono/AMOLED:** the pinned-subscription icon renders **blue** `#4C8DFF` in a theme whose entire purpose
  is collapsing the accent to grey (`App.axaml.cs:618` sets `Brush.Accent` to `#FFFFFF`/`#111214`, with the
  comment at line 590 *"mono connected (не синий)"*). One stray blue glyph in a monochrome UI.

**Recommendation:** Route all six assignments through the existing `ResolveBrush(key, fallback)` helper, or
better, bind `Foreground` to `{DynamicResource ...}` in XAML and drive state with a class
(`Classes.pinned`, `Classes.expired`) so the theme system stays in charge.
**Suggested command:** `/impeccable harden`

---

#### [P1-3] `SettingsView` shadows the global `TextBox.IncyField`, reviving the radius drift it claims to have fixed

**Location:** `Views/SettingsView.axaml:68-135` vs `Assets/GlobalResources.axaml:494-568`
**Category:** Theming / Anti-pattern (system leak)

`GlobalResources.axaml:488-489` states the fix explicitly:

> *"Радиус СВЕДЁН 12→14 (Radius.Search) к единой шкале полей ввода (SearchPill / PriceOption /
> TextBox.Incy = 14) — конец дрейфа radius 12 vs 14 (REVIEW_VISUAL L9). Добавлен :disabled (opacity 0.38)"*

`SettingsView.axaml` still carries the pre-fix local copy in `UserControl.Resources`:

- line 72: `CornerRadius` = `Radius.Tile` (**12**) — the global is `Radius.Search` (**14**), `GlobalResources.axaml:498`
- lines 129-134: only `:pointerover` and `:focus` — **no `:disabled` style**, which the global has at line 565

Because `Theme="{StaticResource TextBox.IncyField}"` (used at lines 487, 499, 508) resolves from the
nearest resource scope outward, the local dictionary wins. The three local-proxy fields render at radius
12 while every other Incy field in the app renders at 14, and they have no disabled appearance.

**Impact:** The documented "end of the radius drift" is not true in the shipped build; the drift lives in
exactly one screen, which is the hardest kind to notice and the easiest kind to re-introduce.

**Recommendation:** Delete `SettingsView.axaml:68-135` outright. The global theme is a superset.
**Suggested command:** `/impeccable distill`

---

#### [P1-4] Invalid local-proxy port is silently discarded — no error state exists

**Location:** `ViewModels/SettingsViewModel.cs:369-375`, field at `Views/SettingsView.axaml:483-487`
**Category:** Accessibility / State coverage

```csharp
var portOk = int.TryParse(LocalPortText?.Trim(), out var port) && port > 0 && port < Global.MaxPort;
if (!portOk)
{
    // Reject silently and restore the real value so the UI never shows an un-persisted port.
    LocalPortText = inbound.LocalPort.ToString();
```

The commit handler is `LostFocus` (`Views/SettingsView.axaml.cs:62`). A user types `99999`, clicks away,
and the field silently snaps back to the old number with no message, no red border, no explanation. The
design law requires *"every state designed (… error)"*, and the codebase already owns both primitives:
`TextBox.fieldError` (`LoginView.axaml:122-124`) flashes the border red, and `Brush.RedText` exists for the
message.

**Impact:** The user cannot distinguish "my port was rejected" from "the app didn't register my typing".
This is the single worst interaction in Settings.

**Recommendation:** On invalid input, apply `.fieldError` to `ProxyPortBox` and show a `Caption` line in
`Brush.RedText` under it stating the valid range. Reuse the exact pattern at `LoginView.axaml:353-360`.
**Suggested command:** `/impeccable clarify`

---

#### [P1-5] Five interactive archetypes below the 48px touch minimum, and three different sizes for "an icon button"

**Location:** across the shell
**Category:** Accessibility / Design-system consistency

The design law requires `row_min_height 56` and `≥48dp touch targets`. `GlobalStyles.axaml:831-841`
declares the canonical resolution — *"Button.IconButton40 = 40×40 … это единый класс … конец дрейфа
32/36/40"*. That consolidation did not land:

| Control | Size | Location |
|---|---|---|
| Window caption buttons (min/max/close) | **44×22** | `MainWindow.axaml:54-55` |
| Rail collapse toggle | **30×30** | `MainWindow.axaml:133-134` |
| Legacy `Button.IconButton` | **32×32** | `GlobalResources.axaml:20-21` |
| `Button.MetaIcon` (subscription actions) | **34×34** | `SubscriptionMetaView.axaml:58-61` |
| `Button.SegItem` (Вход/Регистрация tabs) | **36** high | `LoginView.axaml:79` |
| `ToggleButton.Segment` (Mode/Appearance) | **44** high | `GlobalStyles.axaml:1217` |
| Canonical `Button.IconButton40` | 40×40 | `GlobalStyles.axaml:842-861` |

Two secondary consequences worth naming:

- `SubscriptionMetaView.axaml:102` comments *"Бокс 40 — ≥ touch target"* on `CollapseButton`, but the same
  element carries `Classes="IconButton40 MetaIcon"` (line 107) and `MetaIcon` forces it to 34. The comment
  documents a size the code does not produce.
- The back button is spelled two different ways: `Classes="IconButton BackNav"`
  (`LoginView.axaml:246`, `BuyView.axaml:245`) versus `Classes="BackNav"`
  (`PaymentHistoryView.axaml:46`, `DevicesView.axaml:116`). The doubled form stacks two competing
  archetypes and only works because `Button.BackNav` (`GlobalStyles.axaml:907`) is declared *after*
  `Button.IconButton` (line 226) and therefore wins the cascade. That is load-bearing declaration order.
- A third spelling exists at `LoginView.axaml:372-377`: `Classes="IconButton"` plus inline `Width="40"
  Height="40"`.

**Recommendation:** Migrate `MetaIcon`, the rail toggle and the remaining `IconButton` sites onto
`IconButton40`; if the meta bar genuinely needs density, add `IconButton40.Dense` as a *sanctioned*
variant with a documented minimum rather than a per-view override. Raise `SegItem`/`Segment` to 48. Caption
buttons are a legitimate desktop-chrome exception but 22px is small even by that standard — 44×32 matches
Windows 11.
**Suggested command:** `/impeccable extract`

---

### P2 — Minor (fix in next pass)

---

#### [P2-1] Three competing scrim depths, one of which is dead code

**Location:** `GlobalResources.axaml:261`, `MainWindow.axaml:308`, `DevicesView.axaml:451-452`,
`BuyView.axaml:619`
**Category:** Theming

- `Brush.Scrim` = `#000000` @ **0.6** (the token), correctly used at `BuyView.axaml:619`.
- `MainWindow.axaml:308` — `DialogHost Background="#B3000000"` = **70%**.
- `DevicesView.axaml:451-452` — `Background="#80000000"` (**50%**) *and* `Classes="Scrim"` on the same
  element. The inline local value beats the class setter, so `Border.Scrim`
  (`GlobalStyles.axaml:1185-1187`) is inert there and the modal dims to 50%, not the token's 60%.

Three modal surfaces, three different depths of black behind them. **Fix:** delete both literals, keep the
token. **Suggested command:** `/impeccable polish`

---

#### [P2-2] `ServerRow` geometry is overridden per-row, and the comment names a fourth number

**Location:** `Views/ServerListView.axaml:136-143` vs `Assets/GlobalStyles.axaml:609-627`
**Category:** Design-system consistency

| Property | Global `Border.ServerRow` | Local override | Comment claims |
|---|---|---|---|
| `MinHeight` | `Size.Row` = **56** (line 611) | **52** (line 137) | — |
| `CornerRadius` | `Radius.Card` = **20** (line 612) | `Radius.Search` = **14** (line 143) | **16** (line 130) |
| `Padding` | **12,8** (line 613) | **12,6** (line 139) | — |

Three sources disagree about the corner radius of the most-used row in the app, and the row rhythm is 52
against a design law that fixes it at 56. `Margin="16,2"` (line 138) and `Padding="12,6"` also put `2` and
`6` on the row, neither of which is on the 4/8/12/16/24/32 scale.

**Fix:** decide one radius, put it in the class, delete the inline value, and correct the comment.
**Suggested command:** `/impeccable layout`

---

#### [P2-3] `SubscriptionMetaView` re-styles the global button system, which the system forbids

**Location:** `Views/SubscriptionMetaView.axaml:295-317`
**Category:** Design-system consistency / State coverage

`GlobalStyles.axaml:368-370` states the contract: *"ОДНА система кнопок … никакой экран НЕ задаёт свою
высоту/радиус/паддинг кнопки."* The "Поддержка" button breaks all three plus the type ramp and the fill:

- `Height="34"` (line 298) — global `Button.Tonal` is 48 (`GlobalStyles.axaml:454`)
- `Padding="10,0,14,0"` (line 299) — global is `24,0` (line 455); `10` and `14` are both off-scale
- `FontSize="12"` (line 303) — global is 15 (line 458); 12 is the `Caption` step
- `Background="{DynamicResource Brush.AccentContainer}"` (line 301) — global is
  `Brush.SurfaceHighest` (line 451)

The `Background` override is the one with a functional consequence. `Button.Tonal:pointerover` repaints
`PART_ContentPresenter.Background` to `Brush.Hover` (`GlobalStyles.axaml:488-490`), a translucent black.
Since the accent-container fill is supplied via the control's `Background` (template-bound into that same
presenter), hovering replaces the blue container with a dark wash rather than tinting it — the button
changes identity on hover instead of acknowledging the pointer.

Also off-scale in the same file: card `Padding="14,10,10,10"` (line 96, whose own comment on line 91 says
*"Паддинг 16/12/12/12"* — the comment and the code disagree), `Margin="0,0,2,0"` (line 105),
`Margin="0,2,0,0"` (line 132), `Margin="0,10,0,0"` (line 292), `Spacing="6"` (line 309), `PathIcon`
`15×15` (lines 311-312), and a local `Border.TrafficPill` height of 14 against the global 16 (lines 85-87).

**Fix:** add a sanctioned `Button.Tonal.Compact` (or `.AccentContainer`) variant to `GlobalStyles` and
apply it, rather than overriding five properties inline.
**Suggested command:** `/impeccable extract`

---

#### [P2-4] The Home radial gradient is painted twice in overlapping coordinate spaces — the seam it was added to remove

**Location:** `Views/MainWindow.axaml:429-435` and `:550-551`, with `MainWindow.axaml.cs:705-718`
**Category:** Performance / Visual craft

`MainWindow.axaml:429-435` adds a full-bleed `Border` spanning `Grid.RowSpan="2" Grid.ColumnSpan="2"` of
`bodyRoot` filled with `Brush.HomeGradient`, and its comment (lines 423-428) explains the intent precisely:
the rail must *share the same gradient* as the content so the collapsed rail does not read as a flat block.

But `MainWindow.axaml:551` then paints **the same brush again**, opaquely, on a `Border` inside
`contentArea` — and `MainWindow.axaml.cs:717` places `contentArea` in **column 1** in the wide layout.
`Brush.HomeGradient` is a `RadialGradientBrush` with *relative* `Center="50%,30%"` and
`RadiusX/Y="75%"` (`GlobalResources.axaml:88-92`), so it resolves against each `Border`'s own bounds. The
inner copy is therefore centred on the content column, the outer copy on the whole body — two different
centres, the inner one opaque and on top.

**Impact:** In the wide layout the gradient is discontinuous exactly at the rail/content seam, which is the
defect the outer border exists to prevent. It also visibly shifts during the layout swap and tab crossfade,
because `MainWindow.axaml.cs:744` and `:773` drive `contentArea.Opacity` to 0 and back, cross-fading
between two differently-centred copies of the same radial. Plus one full-viewport overdraw per frame.

**Fix:** delete the inner `Border` at `MainWindow.axaml:551` and let `contentHost` inherit the full-bleed
gradient — which is what the outer border's own comment says it is for.
**Suggested command:** `/impeccable optimize`

---

#### [P2-5] Icon tiles fail the 3:1 non-text contrast floor in the Light theme

**Location:** `GlobalResources.axaml:45` (`Brush.Tile.Blue`), consumed at `GlobalStyles.axaml:1260-1273`
(`Border.EmptyIcon`), `Views/SettingsView.axaml:236-244`, `Views/PaymentHistoryView.axaml:79-90`,
`Views/LoginView.axaml:282-296`, `Views/OnboardingView.axaml:61-74`
**Category:** Accessibility

`Brush.Tile.Blue` is `#4C8DFF` @ 20%, theme-invariant (declared at line 45, outside the theme
dictionaries). In Light it composites over `#FFFFFF` to ≈`#DBE8FF`. The glyph inside is `Brush.Accent`
`#4C8DFF` — the same hue at full strength. Contrast: **2.61:1**, below WCAG 1.4.11's 3:1 floor for
meaningful non-text graphics.

This affects the brand shield on both first-run screens, every empty-state hero, and the payment-row tile —
i.e. the highest-emphasis graphic on several screens is the lowest-contrast thing on them in Light.

**Fix:** make `Brush.Tile.Blue` theme-dependent (a deeper tint in Light), or darken the glyph to
`Brush.OnAccentContainer` (`#14468F` in Light, line 114) which already reads correctly on that fill.
**Suggested command:** `/impeccable colorize`

---

#### [P2-6] Mono overlay is missing `Brush.OnSurfaceVariantHover` — an off-palette tone leaks into every hover

**Location:** `App.axaml.cs:576-665` (overlay), key defined only at `GlobalResources.axaml:74, 110`
**Category:** Theming

`BuildMonoOverlay` overrides 30+ keys and is otherwise meticulous, but `Brush.OnSurfaceVariantHover` is not
among them (`grep -c 'OnSurfaceVariantHover' App.axaml.cs` → 0). It is consumed in four places:
`GlobalStyles.axaml:787, 790` (nav rail), `MainWindow.axaml:198` (rail toggle),
`SubscriptionMetaView.axaml:73, 76` (delete-subscription), `BottomNavBar.axaml:106, 109`.

In Mono-dark, resting glyphs are `#B0B0B4` (`App.axaml.cs:592`) but hovering darkens them to `#6E7480` —
the *blue-grey* Incy dark tone from the non-mono palette. Both a hue leak into a monochrome theme and an
unusually large luminance step for a hover.

**Fix:** add `["Brush.OnSurfaceVariantHover"] = Solid(light ? "#3E3E42" : "#8A8A8E")` (or equivalent
one-step values) to the overlay dictionary.
**Suggested command:** `/impeccable polish`

---

#### [P2-7] Text-selection colour is a hard-coded blue that survives the monochrome theme

**Location:** `GlobalResources.axaml:417, 504`; `SettingsView.axaml:78`
**Category:** Theming

All three `TextBox` control themes set `SelectionBrush="#334C8DFF"` — a literal 20%-alpha accent. The mono
overlay collapses `Brush.Accent` to grey but cannot reach a literal, so selecting text in any field in the
monochrome theme highlights it **blue**. Same class of leak as P1-2, smaller blast radius.

**Fix:** promote to a `Brush.SelectionFill` token alongside `Brush.SelectedFill`
(`GlobalResources.axaml:242`, which the overlay *does* handle at `App.axaml.cs:648`) and reference it.
**Suggested command:** `/impeccable polish`

---

#### [P2-8] `PaymentHistoryView` hand-rolls the empty-state hero that a global class already provides — twice

**Location:** `Views/PaymentHistoryView.axaml:283-296` and `:319-332` vs `GlobalStyles.axaml:1260-1273`
**Category:** Design-system consistency

`Border.EmptyIcon` exists precisely for the 64px tile + 32px accent glyph construct, and five other views
use it correctly (`ServerListView.axaml:296`, `DevicesView.axaml:368, 394, 426`, `BuyView.axaml:272, 294,
310`, `AccountView.axaml:996`). `PaymentHistoryView` instead writes it out longhand twice, with
`CornerRadius="20"` as a **literal** rather than `Radius.Card`, and hard-coded `Width/Height="64"` and
glyph `32` rather than `Size.EmptyIcon` / `Size.EmptyGlyph` (`GlobalResources.axaml:301-302`).

The error variant does need a neutral tile — that is a legitimate reason to want a variant, not a reason to
abandon the class.

**Fix:** use `Classes="EmptyIcon"`, and add `Border.EmptyIcon.Neutral` to `GlobalStyles` for the error case.
**Suggested command:** `/impeccable extract`

---

#### [P2-9] Two hard-coded Russian strings bypass the localization layer

**Location:** `Views/SettingsView.axaml:776, 792`
**Category:** i18n / Copy

```xml
ToolTip.Tip="Ctrl + / Ctrl − — масштаб, Ctrl 0 — сброс">
...
Text="Масштаб интерфейса" />
```

Every other row on this screen uses `{loc:T Settings_*}` against `Common/L.Settings.cs`, which carries
paired ru/en values. These two are Russian-only literals, so the UI-scale row stays Russian when the app is
switched to English. (`Views/StatusBarView.axaml:101, 114, 120` has the same problem, out of scope here.
`App.axaml:39-45` is a deliberate exception — the tray menu is OS-drawn, and the comment at lines 33-36
says so.)

**Fix:** add `Settings_UiScale` / `Settings_UiScaleHint` keys to `L.Settings.cs`.
**Suggested command:** `/impeccable clarify`

---

### P3 — Polish

---

#### [P3-1] Off-scale spacing values across the target views

**Category:** Layout · **Design law:** ONE spacing scale (`Space.4/8/12/16/24/32`, `GlobalResources.axaml:138-143`)

| Location | Value | Note |
|---|---|---|
| `LoginView.axaml:266` | `Margin="16,8,16,28"` | `28` |
| `LoginView.axaml:571` | `Margin="0,14,0,0"` | `14` |
| `LoginView.axaml:807, 901` | `Margin="0,20,0,0"` | `20` |
| `LoginView.axaml:146` | `Margin="3,0"` (CodeCell) | `3` |
| `MainWindow.axaml:324` | `Margin="14,0"` | `14` |
| `MainWindow.axaml:328` | `Spacing="7"` | `7` |
| `MainWindow.axaml:354` | `Margin="0,0,6,0"` | `6` |
| `MainWindow.axaml:358` | `Spacing="2"` | `2` |
| `MainWindow.axaml:518` | `Spacing="10"` | `10` |
| `SettingsView.axaml:180` | `Margin="16,18,16,8"` | `18` |
| `SettingsView.axaml:258, 481, 494, 502, 719` | `Spacing="6"` | `6` ×5 |
| `ServerListView.axaml:181` | `Padding="1.5"` | sub-pixel |
| `ServerListView.axaml:196` | `Margin="0,2,0,0"` | `2` |
| `ServerListView.axaml:295` | `Spacing="14"` | `14` |
| `SubscriptionMetaView.axaml:96, 105, 132, 292, 309` | see P2-3 | `14/10/2/6` |

None of these is individually visible; collectively they are why the vertical rhythm reads as
approximately-right rather than exactly-right. **Suggested command:** `/impeccable layout`

---

#### [P3-2] Off-token corner radii

**Category:** Design-system consistency · **Token set:** `Chip 12 · Tile 12 · Search 14 · Button 16 · Card 20 · Sheet 24 · Pill 100`

- `MainWindow.axaml:81` — `CornerRadius="8"` (caption-button hover plate)
- `MainWindow.axaml:333` — `CornerRadius="6"` (wordmark badge)
- `MainWindow.axaml:508` — `CornerRadius="2"` (rail indicator)
- `LoginView.axaml:112` — `CornerRadius="8"` (`Border.SoonPill`)
- `GlobalStyles.axaml:239, 776` — `CornerRadius="8"` / `"14"` inline on content presenters
- `PaymentHistoryView.axaml:288, 324` — `CornerRadius="20"` literal instead of `Radius.Card`

`LoginView.axaml:84` (`CornerRadius="8"` on `SegItem`) is **correct and should stay** — the comment on
line 83 derives it as concentric with the track (`Radius.Chip 12 − 4 padding = 8`). That is the reasoning
the others are missing. **Suggested command:** `/impeccable polish`

---

#### [P3-3] Password mask glyph differs between screens, against a documented decision

`LoginView.axaml:365, 414` use `PasswordChar="•"` (U+2022), with lines 126-128 explaining the choice:
*"лёгкий глиф • (U+2022) вместо тяжёлого ● (U+25CF)"*, plus `LetterSpacing="2"` on the presenter so the
dots breathe. `SettingsView.axaml:506` still uses `PasswordChar="●"` (U+25CF) with no letter-spacing —
the exact glyph the login screen rejected. **Suggested command:** `/impeccable polish`

---

#### [P3-4] Base `PathIcon` style defaults to a Semi theme token, not an Incy one

`GlobalStyles.axaml:204-208` sets the global `PathIcon` foreground to
`{DynamicResource ButtonDefaultTertiaryForeground}` — a **Semi.Avalonia** key, not an Incy token. Every
`PathIcon` not covered by a more specific rule inherits a colour from outside the design system, and it
will not follow the Mono overlay. In practice most call sites set `Foreground` inline or are covered by a
component rule, so nothing is visibly broken today; it is a hole in the token boundary.
**Fix:** default to `Brush.OnSurfaceVariant`. **Suggested command:** `/impeccable harden`

---

#### [P3-5] Duplicated icon geometry between Login and Onboarding

The Telegram plane is declared as a resource at `LoginView.axaml:36` and pasted inline as a raw path at
`OnboardingView.axaml:197`; the globe likewise at `LoginView.axaml:45` vs `OnboardingView.axaml:225`, and
again at `SettingsView.axaml:27` and `ServerListView.axaml:31`. The shield is correctly shared via
`Geo.Shield` (`GlobalResources.axaml:316`) — that is the pattern to follow. impeccable's production bar
calls for one coherent icon set; four copies of one glyph is how sets drift.
**Fix:** promote `Geo.Telegram` and `Geo.Globe` to `GlobalResources.axaml` beside `Geo.Shield`,
`Geo.ArrowBack` and `Geo.ChevronRight`. **Suggested command:** `/impeccable extract`

---

#### [P3-6] Skeleton loading state is three hand-copied 40-line blocks

`PaymentHistoryView.axaml:149-273` repeats a near-identical card silhouette three times, differing only in
one bar width (160 / 128 / 144). Any change to the payment row must now be made in four places (the real
row plus three skeletons) or the skeleton→list transition will start to jump — which is the exact thing the
comment at lines 141-142 says the geometry match exists to prevent.
**Fix:** one `ItemsControl` over three width values, or a small `PaymentSkeletonRow` UserControl.
**Suggested command:** `/impeccable distill`

---

#### [P3-7] Small comment/implementation drifts

- `MainWindow.axaml:675` — comment says *"8 тонких (6px) ПРОЗРАЧНЫХ зон"*; the grid at lines 685-686 is
  `4,*,4` / `4,*,4`, i.e. 4px. Resize grips are 33% narrower than documented and harder to grab.
- `SettingsView.axaml:437` — comment says *"Локальный прокси (КРАСНАЯ плитка)"*; line 442 is
  `Classes="Tile"` (neutral). Correct as built — the design law allows one accent and red only for
  destructive, so the *comment* is what is wrong.
- `SubscriptionMetaView.axaml:91` vs `:96` — comment says padding `16/12/12/12`, code says `14,10,10,10`.
- `SettingsView.axaml:917-938` — `RowRouting` declares four columns but populates only 0, 1 and 3;
  column 2 collapses to zero width so it renders correctly, but the intent is unclear.
- `SettingsView.axaml:752` labels the AMOLED theme **"Монохром"** (`L.Settings.cs:57`) while
  `GlobalResources.axaml:32-34` and the row comment at `SettingsView.axaml:734` call it
  **"Чёрная (AMOLED)"**. The overlay does both things (it desaturates *and* takes the background to
  `#000000`, `App.axaml.cs:608`), so the label tells the user only half of what the switch does.

---

## Patterns & systemic issues

**1. Local overrides silently defeat a correct global system.** This is the dominant failure mode and it
accounts for P1-3, P2-1, P2-2, P2-3, P2-8 and most of P3-2. The global layer is right nearly every time;
individual views then re-specify the same value slightly differently, and because XAML resolves the nearest
scope, the local wrong value wins. Three of these overrides are documented in comments as *already fixed*.

**2. Theme-invariant tokens in a three-theme system.** `Brush.StatusChip.*`, `Brush.Icon.*` and
`Brush.Tile.*` sit outside `ResourceDictionary.ThemeDictionaries` while carrying dark-tuned values. Every
one of them fails or degrades in Light (P1-1, P2-5). The structural fix is a lint-style rule: any token
whose value encodes a *luminance relationship* must live in the theme dictionaries; only hue-anchored
brand constants may live outside.

**3. Code-behind assigns brushes without resolving them.** P1-2 and P2-7 are the same bug in two places,
and `SubscriptionMetaView.axaml.cs:416` shows the author already knew the right pattern. Worth a
convention: **no `SolidColorBrush` construction in view code** — bind `{DynamicResource}` and drive state
with classes.

**4. Comments have drifted ahead of the code.** Six comments describe values, sizes or fixes the code no
longer matches (P1-3, P2-2, P2-3, P3-7). The comments in this codebase are unusually thorough and are
clearly used as the design record — which makes each stale one actively misleading to the next reader.

---

## Positive findings — keep and replicate

These are genuinely above the bar and should not be touched:

- **Motion system.** `GlobalResources.axaml:182-214` defines a full easing/duration catalogue mirrored in
  C# (`Common/Motion.cs`), ease-out only, no bounce or elastic, exit faster than entry — exactly
  impeccable's motion guidance. The reserved single `Ease.OutExpo` moment for the auth→home hand-off
  (line 196-198) is a real design decision, not a default.
- **Reduced motion is treated as a first-class requirement.** The `.lite` window class
  (`GlobalStyles.axaml:1360-1446`) zeroes transitions *and* gates cyclic keyframes at the **selector**
  level, with the comment at lines 1365-1367 correctly noting that a competing style cannot cancel a
  running Avalonia animation. That is a real platform insight, not boilerplate.
- **Focus-visible is systematic.** `GlobalStyles.axaml:1019-1098` uses `FocusAdorner` so rings cost zero
  layout, are never clipped by a card or scroll viewport, and appear only on keyboard focus — with an
  inner ring on filled accent CTAs where an outer blue ring would be invisible. It deliberately survives
  `.lite` because a11y outranks motion (lines 1029-1031).
- **Contrast reasoning is written down and, where I checked it, correct.** `GlobalResources.axaml:81-84`
  introduces a separate `Brush.RedText` brighter than `Brush.Red` specifically to clear 4.5:1 on dark
  surfaces. I verified the placeholder claim at `GlobalResources.axaml:403` — `#9BA1AD` on
  `Brush.SurfaceVariant` `#1E2126` computes to **6.15:1** against the documented "6.2:1", and the
  onboarding subtitle claim at `OnboardingView.axaml:93-94` computes to **5.26:1** on the gradient's
  lightest stop, comfortably above the claimed 4.6:1.
- **`PaymentHistoryView` is the reference implementation for state coverage** — all four states (loading /
  list / empty / error) are mutually exclusive and individually designed, with the empty state carrying a
  forward CTA and the error state carrying a retry and no CTA, correctly distinguished at lines 312-313.
- **Hover was deliberately made instantaneous** on `ServerRow` (`GlobalStyles.axaml:617-620`),
  `SettingRow` (lines 653-655) and caption buttons (`MainWindow.axaml:71-78`) to stop two rows appearing
  highlighted during pointer transit. That is a bug found by looking, not by linting.
- **The Login screen's information architecture was reasoned about and corrected.** The comments at
  `LoginView.axaml:516-520` and `:541-544` describe demoting Telegram out of the hero slot so one filled
  accent remains, and explicitly reject a stack of identical buttons as an *"AI-slop tell"*. The skill's
  own vocabulary is being applied by the authors.
- **Honest disabled affordance.** The Google button is `IsEnabled="False"` with a "Скоро" pill
  (`LoginView.axaml:617-643`) rather than a dead control that fails on click.

---

## Recommended actions, in priority order

1. **[P1] `/impeccable colorize`** — move `Brush.StatusChip.*`, `Brush.Icon.*` and `Brush.Tile.Blue` into
   the theme dictionaries with Light-tuned values; fixes P1-1 and P2-5 together.
2. **[P1] `/impeccable harden`** — remove the three cached brushes in `SubscriptionMetaView.axaml.cs:30-32`
   and route through `ResolveBrush` / `DynamicResource`; add the missing `Brush.OnSurfaceVariantHover` and
   a `Brush.SelectionFill` token to the mono overlay (P1-2, P2-6, P2-7, P3-4).
3. **[P1] `/impeccable distill`** — delete the shadowing `TextBox.IncyField` at `SettingsView.axaml:68-135`
   and collapse the three copied skeleton blocks (P1-3, P3-6).
4. **[P1] `/impeccable clarify`** — design the local-proxy port error state; localize the two hard-coded
   Russian strings (P1-4, P2-9).
5. **[P1] `/impeccable extract`** — consolidate the six icon-button sizes onto `IconButton40` (+ one
   sanctioned dense variant), promote `Geo.Telegram` / `Geo.Globe`, add `Button.Tonal` and
   `Border.EmptyIcon` variants so views stop overriding (P1-5, P2-3, P2-8, P3-5).
6. **[P2] `/impeccable optimize`** — delete the duplicated `Brush.HomeGradient` paint at
   `MainWindow.axaml:551` (P2-4).
7. **[P2] `/impeccable layout`** — settle `ServerRow` height/radius/padding against the tokens and sweep
   the off-scale spacing table (P2-2, P3-1).
8. **[P3] `/impeccable polish`** — single scrim token, off-token radii, password glyph, and the six stale
   comments (P2-1, P3-2, P3-3, P3-7).

Re-run the audit after 1–5; those five close both weak dimensions and should move the score to ~18/20.
