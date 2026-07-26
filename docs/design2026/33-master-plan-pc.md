# 33 - Master design plan: PC (Windows / Linux / macOS)

**Departament VPN, desktop client. The complete 2026 rebuild, screen by screen.**

| | |
|---|---|
| Repo | `/home/user/v2rayN` |
| UI project | `/home/user/v2rayN/v2rayN/v2rayN.Desktop` |
| Stack | C# .NET + Avalonia 11 + ReactiveUI + Semi.Avalonia + DialogHost.Avalonia |
| Shared core | `/home/user/v2rayN/v2rayN/ServiceLib` |
| Paths below | relative to `v2rayN/v2rayN.Desktop/` unless stated |
| Sibling document | `32-master-plan-android.md` (the Android half of the same product design) |
| Date | 2026-07-26 |

---

## 0. How to read this document

### 0.1 Precedence

1. The owner's explicit requests (`00-rules.md` 0.4), verbatim and permanent.
2. `00-rules.md` - the operational design law. Any number in it beats any number here.
3. `03-direction.md` - the product direction. Where it and `30-reference-analysis.md`
   disagree, the direction wins; every such conflict is resolved explicitly in 1.6 below.
4. This file.
5. Existing code.

Where this plan needs a value the law does not carry, it is written as a **decision** in section 12
in `00-rules.md` section 18 row format, and it is not implemented until that row is pasted into the
rules file.

### 0.2 What this document inherits and does not repeat

| Already established, not restated here | Where |
|---|---|
| Token values (spacing, radii, colour, motion, type ramp) | `00-rules.md` 3 |
| The bans and the slop tests | `00-rules.md` 1, 2; `03-direction.md` 9 |
| The three product signatures (figure face, single lit element, hairline ledger) | `03-direction.md` 3 |
| The four planes and what each means | `03-direction.md` 4 |
| What Happ and Incy do right and wrong | `30-reference-analysis.md` 1, 2 |
| Today's desktop inventory, file by file, with line numbers | `02-inventory-pc.md` |
| Today's desktop control census, class by class | `20-control-survey.md` Part B |
| Today's account surface, field by field | `21-account-survey.md` 2 |
| Today's honest grade sheet | `31-self-assessment.md` Part B |

### 0.3 What this document is

A build order. For every surface the desktop client has - shell, both layout modes, every
destination, every sub-page, every dialog, every window, the tray - it states: the concept, its
Android counterpart, the exact `.axaml` file, whether it is **restyle** or **rebuild** or **delete**,
the component tree with real values, every state, every interaction, the keyboard path, the motion,
and the Russian copy. It ends with an ordered implementation sequence.

Verdict vocabulary, fixed:

- **KEEP** - ships as-is; only token and spacing cleanups.
- **RESTYLE** - the structure is right; the surface, states and tokens change.
- **REBUILD** - start from this spec, not from the current file.
- **DELETE** - the file goes; its feature, if any, is migrated to a named destination.

### 0.4 A note on dashes

This document contains no em-dash and no en-dash, in prose or in copy, per `00-rules.md` 1.4.11 and
`03-direction.md` F19. Hyphen only. The 44 dashes currently in `Common/L.*.cs` are cleared as part of
wave 1 (section 13).

---

## 1. The desktop thesis

### 1.1 One sentence

> The desktop client is the same instrument, mounted on a bench instead of held in a hand: the same
> near-black planes, the same single blue, the same 56px hairline ledger at the same 68px text
> origin, the same figure face on every number - re-laid for a pointer that hovers, a keyboard that
> must be able to finish every task alone, and a window the user resizes from 380px to 3840px.

Nothing about the *product* changes between the two clients. What changes is the input device, the
number of things that fit on screen at once, and the fact that on desktop a control has a hover
state and a focus ring and both are mandatory.

### 1.2 What is identical to Android, and is not allowed to drift

These are the parity contract (`00-rules.md` 13). A difference in any of them is a defect on the
platform that diverged, not a platform adaptation.

| Identical | Value |
|---|---|
| Destination set and order | Главная · Серверы · Настройки · Аккаунт |
| Every user-visible string for the same concept | `Common/L.*.cs` mirrors `res/values*/strings*.xml` |
| Terminology | `00-rules.md` 9.3, no exceptions |
| The row | 56 min height, 40 tile, 22 glyph, 12 gap, 68 text origin, hairline from 68 |
| The card | `Brush.Surface`, radius 20, 1px `Brush.OutlineVariant`, padding 16, no shadow |
| The affordance grammar | chevron / rotating chevron / unfold / segment / switch / value, one trailing element per row |
| Colour meaning | blue = action and user-controlled state; green = status; red = destroy and error; amber = warning |
| The accent budget | one filled accent surface per screen, at most three further tinted elements |
| Type roles and their sizes | 34 / 24 / 16 / 14 / 13 / 12 / 11 |
| Spacing scale | 4 / 8 / 12 / 16 / 24 / 32 |
| Motion tempo | 90 / 160 / 220 / 300 / 40 / 600 with the four named easings |
| Every state a screen must ship | `00-rules.md` 15 |
| The subscription state machine | six states, `30-reference-analysis.md` 6.1 |
| The error taxonomy | eight cases, `30-reference-analysis.md` 6.2a |
| Default value of every setting | shared through `ServiceLib` |

### 1.3 What deliberately differs, and why the platform demands it

| Concept | Android | Desktop | Why |
|---|---|---|---|
| Top-level navigation | Bottom navigation, 4 items | Left rail 76px wide, 4 items, in compact a bottom bar with the same 4 | A pointer travels to a fixed edge cheaply; a thumb does not reach the top of a 6.7" phone. The rail is the desktop's cheapest fixed target and it survives a 3840px window without stretching |
| Hover | Does not exist | Mandatory on every clickable surface | A pointer needs to be told what is clickable before it commits. `Brush.Hover` or one surface step, 150ms `Ease.Standard` |
| Focus ring | Keyboard and TV only | Always, on every focusable control, 2px `Brush.Accent`, 2px offset | Desktop tasks must be completable with no mouse (`00-rules.md` 14.8) |
| Press feedback | Ripple plus scale 0.97 | Scale 0.97 only, no ripple | Avalonia has no Material ripple and a synthesised one reads as a web imitation |
| Per-item actions | Bottom sheet | `MenuFlyout` anchored to the row, opened by right-click, by a kebab that appears on hover or focus, and by the Menu key | A sheet is a thumb affordance. A flyout is where a pointer already is |
| Selection among many | Bottom sheet list | Flyout list anchored to the row | Same reason |
| Transient feedback | Snackbar above the bottom nav | Docked status strip at the bottom of the content area | The owner rejected floating toasts. Section 3.8 |
| Back | System Back, predictive Back | Toolbar back **and** Escape **and** mouse button 4 **and** `Alt+Left` | There is no system Back on a desktop; four affordances is the platform norm, not redundancy |
| Text input | IME, autofill hints, input types | Real keyboard, `Ctrl+A/C/V/X/Z`, tab order, Enter submits, Esc cancels | |
| Window | One size, one orientation, insets | 380x620 to unbounded, two layout modes, DPI 100 to 200 percent, in-app zoom | Section 3.2 |
| Density | 11 rows on a 1080x2400 phone | 11 rows at 900x600, 18 at 1080x900; the row does not shrink, the column count and the visible count change | `adapt.native.md`: restructure, do not stretch |
| Haptics | `pressHaptic` on confirmations | None | No hardware |
| Keyboard shortcuts | None | 22 (section 2.8) | |
| Window chrome | None | Custom 32px caption, min / max / close, drag, 8-zone resize | `WindowDecorations="None"` is already correct and stays |
| Tray | Ongoing notification | Tray icon plus native menu | Section 3.11 |
| Multi-select | Not offered | `Ctrl+click`, `Shift+click`, `Ctrl+A` on the server list | A pointer can rubber-band; a finger cannot |
| Editors | Full-screen activities | Sub-pages in the shell, never OS-decorated windows | Section 8 |

### 1.4 What the desktop must stop doing

Six failures, each measured in `02-inventory-pc.md`, `20-control-survey.md` or
`31-self-assessment.md`, each closed by a named part of this plan.

| # | Failure today | Measured | Closed by |
|---|---|---|---|
| F1 | The shell, Home, Login, Onboarding and AccountSync are painted with a navy radial gradient, and the connect control carries a radial glow, two alpha rings, an ambient breathing loop and a sonar loop | `GlobalResources.axaml:88`, `:124`, `:269`, `:280-281`; `MainWindow.axaml:434`, `:551`; `ConnectHeroView.axaml:342-470` | 2.1 and 5.3. The gradient tokens are deleted, not restyled |
| F2 | 289 of 483 controls fall through to the Semi default look; 22 of 49 views speak upstream `ResUI` strings | `20-control-survey.md` B.2 | Section 8. Every reachable surface is converted or deleted |
| F3 | There is no user-visible feedback surface at all. `snackHost` is permanently invisible and `MsgView` is never mounted | `MainWindow.axaml:623`, `MainWindow.axaml.cs:1765`, `SimpleViewLocator.cs:26` | 3.8 and 7.12 |
| F4 | Escape does not go back; the sub-page stack is global rather than per-destination | `MainWindow.axaml.cs:74`, `:1086` | 3.7 |
| F5 | There is no server search, no sort, no multi-select, and no Servers destination | `CompactServersView.axaml:90` is dead; the rail has three items | Section 6 |
| F6 | Seven press scales, ten glyph sizes, two icon-button systems, 28 button class combinations, 190 view-local style rules, 10 copies of one back-arrow geometry | `20-control-survey.md` B.3, B.5 | 2.7 and 2.5 |

### 1.5 The desktop slop test

`00-rules.md` 2.4, run with the pointer in mind. Answer all nine out loud per screen.

1. **Category reflex.** Would this screenshot be indistinguishable from any dark VPN client? If the
   answer is a gradient, a glow, a globe or a neon ring, it failed.
2. **Second-order reflex.** Having avoided that, did it land on Linear-clone grey with a violet CTA,
   or on terminal-green mono? Both fail.
3. **The Semi tell.** Is there a control on this screen that a Semi.Avalonia demo would also
   render? A stock `ComboBox`, a stock `TextBox`, a stock `ToggleSwitch`, a `Width="100"` button
   pair centred at the bottom of a dialog. Any hit is a P1.
4. **The phone-port tell.** Is there a bottom sheet, a horizontal card carousel, a pull-to-refresh
   affordance, or a 440px-tall hero on a 630px window? Any hit is a P1.
5. **The stretch tell.** Widen the window to 1920. Does a 56px row now run 1700px wide with a tile
   hard left and a value hard right? Any hit is a P1.
6. **The hover tell.** Put the pointer on every clickable pixel. Does anything clickable fail to
   respond, and does anything unclickable respond?
7. **The keyboard tell.** Unplug the mouse. Complete the screen's primary task. If it cannot be
   done, or if focus is invisible at any step, it is a P1.
8. **The decoration tell.** Point at every non-text pixel and say what it communicates.
9. **The trust test.** Would a user who lives in Raycast, Linear and Telegram Desktop trust this
   window?

### 1.6 The five open questions from `02-inventory-pc.md` 6, answered

The inventory ended with seven questions. Here are the answers this plan implements. They are
binding for both platforms; the Android half of each is noted for the sibling document.

**Q1. Gradients and glow: amend the law, or replace them?**
**Replace.** `Brush.HomeGradient`, `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`
and `Nav.Scrim` are deleted from `Assets/GlobalResources.axaml` and every consumer moves to flat
`{DynamicResource Brush.Bg}`. `03-direction.md` D-H already deletes the Android equivalents. The
connect control's depth comes from `Brush.SurfaceHigh #1A1D21` on `Brush.Bg #0A0B0D`, which is a
1.4:1 luminance step and is exactly Incy's own recessed-idle idea (`30-reference-analysis.md`
2.1.2) executed without a bloom.

**Q2. Where do servers live: a destination or a column?**
**A destination.** The rail and the compact bar carry four items, matching Android. Home keeps the
connect object and **one** server identity row; it does not carry the list. This closes the
duplicate-list failure inherited from Incy (`30-reference-analysis.md` 2.2.8), gives server search
a home, and fixes the compact layout where a 440px hero pushed the list below the fold on a 630px
window.

**Q3. The auth screen: one method promoted, five disclosed. What is the disclosure?**
**A panel step inside the same page,** not a dialog and not a new route. The sign-in page is a
single column with a small internal panel stack: `Method` (default) → `Email` → `Code` →
`AwaitingTelegram` → `PendingEmail`. Moving between panels is a 220ms crossfade plus a 16px
directional slide, Escape steps back one panel, and each panel has exactly one filled accent
control. Section 7.9.

**Q4. The upstream stratum: which are rebuilt, which are deleted?**
Six rebuilt as shell sub-pages, four deleted with their features migrated into Settings, three
dialogs rebuilt, one control restyled. Full table in section 8.

**Q5. The feedback channel, given the owner rejected toasts.**
**A docked status strip in the shell** plus a durable **log page** under Настройки › Ядро и логи ›
Журнал. `Border.Toast` is deleted. Section 3.8 and 7.12.

**Q6. Back semantics.**
Escape, mouse button 4 (`XButton1`), `Alt+Left`, and the toolbar back button, all popping a
**per-destination** stack. Every sub-page has a stable route id so `depv://` and session restore
can target it. Section 3.7.

**Q7. File layout.**
Real directories. `Assets/Tokens.axaml`, `Assets/Themes/{Dark,Light,Mono}.axaml`,
`Assets/Styles/*.axaml`, `Assets/Icons.axaml`, and `Views/{Shell,Home,Servers,Account,Settings,Auth,
Dialogs,Components}/`. The monochrome theme stops being C# (`App.axaml.cs:580 BuildMonoOverlay`) and
becomes a resource dictionary that can be reviewed as data. Section 2.11.

**Two conflicts between source documents, resolved here:**

- **Connected-state colour.** `03-direction.md` 10.2 says the shield fills blue and the ring stays
  accent; `30-reference-analysis.md` 2.3 says the ring settles green with the word. Resolution: the
  **shield fills `Brush.Accent`** (it is the single lit element, and it is the thing the user
  pressed), the **ring returns to a neutral 1px hairline**, and the **status line below carries an
  8px `Brush.Green` dot plus the word «Подключено»**. Two channels, one lit element, and it removes
  today's defect of a green shield sitting inside a blue halo.
- **Button radius.** `00-rules.md` 3.2 says buttons are pill. `Assets/GlobalStyles.axaml:2-16`
  records that **the owner rejected capsules** and sets `Radius.Button` to 16. Per `00-rules.md`
  0.1.1 the owner's recorded decision outranks the rule. Desktop keeps 16, Android moves to 16, and
  `00-rules.md` 3.2 is amended by decision **PC-D4** in section 12.

---

## 2. The systems, expressed for Avalonia

Everything in this section is a change to `Assets/`. No view file may contain a raw value that a
token covers, and after this section no view file may declare a `<Style Selector=...>` block at all
except the four documented exceptions in 2.7.5.

### 2.1 Surfaces and elevation

Four planes, from `03-direction.md` 4.1, with their Avalonia keys and the rule that governs each.

| Plane | Key | Dark | Light | Mono dark | Allowed to be |
|---|---|---|---|---|---|
| P0 ground | `Brush.Bg` | `#0A0B0D` | `#F4F7FC` | `#000000` | The window, every page, every sub-page toolbar, the rail, the compact bar |
| P1 object | `Brush.Surface` | `#141619` | `#FFFFFF` | `#0B0B0B` | A card, a dialog body, a flyout body, the status strip |
| P2 raised | `Brush.SurfaceHigh` | `#1A1D21` | `#EAEFF7` | `#151515` | Transient only: `:pointerover`, `.pressed`, drag. Plus the connect disc, which is the one object whose *resting* recess is its state |
| P3 inset | `Brush.SurfaceHighest` | `#20242B` | `#E3EAF4` | `#1E1E1E` | Input field, chip fill, neutral tile, selected row fill, skeleton bar |

Rules, enforced by review:

- **Nothing is P2 at rest** except `Border.ConnectDisc`. A settings screen that stacks P2 slabs is a
  defect.
- **At most two planes above ground in one region.** `P0 → P1 card → P3 chip` is legal.
  `P0 → P1 → P1` (nested cards) is banned. `P0 → P1 → P2` (a raised thing inside a card) is banned.
- **No `BoxShadow` anywhere.** Delete it from `IncyFlyoutTheme` (`GlobalStyles.axaml:42`),
  `Border.Toast` (`:1198`, and the class itself is deleted) and `MessageBoxDialog.axaml`. A flyout
  separates from the card under it by being P1 with a 1px `Brush.Outline` border on a P0 page; a
  dialog separates by sitting on `Brush.Scrim` at 60 percent black.
- **No `ExperimentalAcrylicBorder`, no blur, no `OpacityMask` used as a fade.** The `navScrim`
  `OpacityMask` gradient at `MainWindow.axaml:582-586` is deleted with the compact scrim.
- **Scrim is one token.** `Brush.Scrim` `#000000` at 0.6. The three inline hexes
  (`MainWindow.axaml:308` `#B3000000`, `DevicesView.axaml:451` `#80000000`,
  `ConnectHeroView.axaml:526` `#000000`) all become `{DynamicResource Brush.Scrim}`.

**Separators.** One hairline: `Height="1"`, `Background="{DynamicResource Brush.OutlineVariant}"`.
Inside a card between rows it starts at the 68px text origin (`Margin="68,0,0,0"` relative to the
card's content box, which is `Margin="68,0,16,0"` when the card padding is 0 and the row padding is
16). It never runs under the tile, never full-bleed, never top **and** bottom on the same row.
Between the rail and the content: one 1px `Brush.OutlineVariant` vertical hairline, no tone change.

### 2.2 Accent strategy, per screen

`Brush.Accent` `#4C8DFF` dark / `#1E5FC7` light. Two jobs only: the one action the screen wants, and
state the user controls (current destination, current selection, focus ring, link, determinate
progress).

**The accent is not theme-aware today.** `Color.Accent`, `Brush.Accent`, `Brush.OnAccent`,
`Brush.Tile.*`, `Brush.SelectedFill` and `Brush.StatusChip.*` are declared **outside**
`ResourceDictionary.ThemeDictionaries` in `GlobalResources.axaml:39-51`, so the light theme renders
`#4C8DFF` on white at **2.98:1**. That is a P1 accessibility defect
(`20-control-survey.md` D2). All of them move inside the theme dictionaries in wave 1.

| Screen | The one filled accent surface | Additional tinted elements (max 3) | Accent count must be |
|---|---|---|---|
| Главная, disconnected | none | the rail's current destination | 1 |
| Главная, connecting | the connect arc | rail | 2 |
| Главная, connected | the filled shield | rail | 2 |
| Главная, no subscription | «Купить подписку» row CTA | rail | 2 |
| Серверы | none | rail, selected row fill, focus ring | 3 |
| Настройки hub | none | rail, focus ring | 2 |
| Any settings sub-page | none, or one «Сохранить» when the page has unsaved state | selected chip, focus ring | <= 3 |
| Аккаунт | «Продлить» on the subscription card, or «Купить подписку» when there is none | rail, focus ring | 3 |
| Подписка sub-page | «Продлить» | focus ring | 2 |
| Покупка | «Оплатить» | selected tariff fill, selected price option, focus ring | 4 |
| Устройства | none (destructive is red) | focus ring | 2 |
| История платежей | «Купить подписку» only in the empty state | focus ring | <= 2 |
| Вход, panel Method | «Войти через Telegram» | focus ring | 2 |
| Вход, panel Email | «Войти» | focus ring | 2 |
| Онбординг | «Добавить по QR-коду» | focus ring | 2 |
| Синхронизация | the progress arc | none | 1 |

**Banned outright** (`03-direction.md` 5.7): backgrounds, section headers, dividers, the wordmark,
empty-state artwork, non-category tiles, unselected chips, any inactive state, any shadow or glow.

**Tiles.** `Border.Tile` neutral (`Brush.Tile.Neutral` fill, `Brush.OnSurfaceVariant` glyph) is the
default on every row. Coloured tiles are a **closed category system of at most three categories per
screen**. `Brush.Tile.Purple` is an alias of blue and is deleted; `Brush.Tile.Yellow` and
`Brush.Tile.Orange` survive only as the warning-state tile on a subscription in
`истекает`. `SettingsView.axaml` today already gets this right: 21 neutral tiles, 1 blue. That is
the model for every list in the product.

**The `Success` class does not exist.** Twelve buttons apply `Classes="IconButton Success"`
(`ClashConnectionsView`, `ClashProxiesView`, `MsgView`, `ProfilesView`, `ProfilesSelectWindow`) and
resolve against Semi.Avalonia's own semantic green. All twelve views are deleted or rebuilt in
section 8 and 9; no replacement class is created.

### 2.3 Type

**The split.** `03-direction.md` 6.1 and F5: `Font.Grotesk` (Space Grotesk) has **zero Cyrillic
codepoints**, so applying it to a Russian string is a no-op that silently hands the choice to the OS
font manager, and the same screen is currently set in Segoe UI on Windows and DejaVu or Noto on
Linux. Two font tokens, and the split is by script:

| Token | File | Carries |
|---|---|---|
| `Font.Grotesk` | `Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk` | Digits, units, currency, Latin technical tokens (`VLESS`, `Reality`, `WS`, `TCP`, `SOCKS5`, host names, ports, HWID), chip labels, the wordmark |
| `Font.Numeric` | same file, semantic alias, always paired with `FontFeatures="tnum,lnum"` (`+zero` for technical figures, `-zero` for money) | Every live-updating or column-aligned number |
| `Font.Ui` | **new**, pending owner decision D-1 in `03-direction.md` 11.2 | All Russian prose: titles, labels, buttons, subtitles, captions, errors, empty states |

Until D-1 lands, `Font.Ui` is declared as an explicit per-OS stack rather than the current implicit
one, so the three operating systems stop rendering three different products. It is a single token
and it is swapped in one line when the vendored face is chosen.

`GlobalStyles.axaml` currently sets `FontFamily="{DynamicResource Font.Grotesk}"` in **16 places**,
including `TopLevel`, `TextBlock` and `TemplatedControl` (`:257-265`) and every body class. All of
those move to `Font.Ui`. `Font.Grotesk` survives only on `TextBlock.Chip`, `TextBlock.Numeric`, the
wordmark, and the protocol chip.

**The ramp.** Nine classes, unchanged in value, corrected in family and completed in tracking.

| Class | Size | Weight | Tracking | Line height | Family | Foreground |
|---|---|---|---|---|---|---|
| `TextBlock.Display` | 34 | Bold 700 | -0.68 | 1.2 | `Font.Grotesk` | `Brush.OnSurface` |
| `TextBlock.Headline` | 24 | Bold 700 | -0.24 | 1.2 | `Font.Ui` | `Brush.OnSurface` |
| `TextBlock.Title` | 16 | Bold 700 | 0 | 1.2 | `Font.Ui` | `Brush.OnSurface` |
| `TextBlock.TitleMedium` | 16 | Medium 500 | 0 | 1.2 | `Font.Ui` | `Brush.OnSurface` |
| `TextBlock.Body` | 14 | Regular 400 | +0.14 | 1.45 | `Font.Ui` | `Brush.OnSurface` |
| `TextBlock.Subtitle` | 13 | Regular 400 | +0.13 | 1.45 | `Font.Ui` | `Brush.OnSurfaceVariant` |
| `TextBlock.Caption` | 12 | Regular 400 | +0.24 | 1.35 | `Font.Ui` | `Brush.OnSurfaceVariant` |
| `TextBlock.Chip` | 11 | Medium 500 | +0.44 | 1.2 | `Font.Grotesk` | contextual |
| `TextBlock.SectionHeader` | 16 | Bold 700 | 0 | 1.2 | `Font.Ui` | `Brush.OnSurface` |
| `TextBlock.Numeric` (modifier) | inherits | Medium 500 | inherits | inherits | `Font.Numeric` + `FontFeatures` | inherits |

**Rules.**

- A `TextBlock` that sets `FontSize` inline is a defect. Current violations: `FontSize="20"` at
  `AccountView.axaml:268` (avatar monogram) and in `LoginView.axaml` (code cells);
  `FontSize="18"` in `HomeAccountChip.axaml`. All three become ramp classes.
- **Weight 600 does not exist.** `FontWeight="SemiBold"` currently appears in `DnsSubView.axaml`
  (`Border.DnsChip TextBlock`) and in `LoginView.axaml`'s segment. Both become `Bold` or `Medium`.
- **15 does not exist either.** `Button.Primary`, `Button.Tonal`, `Button.OutlinedAccent` and
  `Button.Destructive` are all `FontSize="15"` today. They become **14 Medium** for tonal, outlined
  and text buttons and **14 Bold** for the filled primary and destructive, which puts every button
  label on the `Body` step. This is a real change to five global styles and it is intentional: 15 is
  not a step in the ramp and a button label is body copy with a weight.
- **Measure.** Any paragraph longer than one line carries `MaxWidth` on the `TextBlock` itself, not
  on the panel, capped at 560 (roughly 68 Russian characters at 14px in the UI face).
- **Truncation.** Only user content ellipsises, and only at the end:
  `TextTrimming="CharacterEllipsis"` on a server remark, a Telegram display name, a subscription
  name, a file path. A primary label never truncates; if it would, the Russian copy is rewritten.
- **Numbers.** Every quantity uses `TextBlock.Numeric`, right-aligned, with its column width reserved
  from the 620/1000 tabular advance: `width = digits x 0.620 x fontSize`. A ping column at 13px with
  four characters reserves `4 x 0.62 x 13 = 33px`, rounded up to the 40px slot. A balance at 34px
  with six digits reserves `6 x 0.62 x 34 = 127px`.
- **Russian number formatting**: comma decimal, U+2009 THIN SPACE thousands, U+00A0 before `₽`,
  `12,4 ГБ`, `24,8 Мбит/с`, `48 мс`, `02:14:07`, `1 290 ₽`.

### 2.4 Spacing, rhythm and the desktop grid

**The scale is 4 / 8 / 12 / 16 / 24 / 32 and nothing else.** `20-control-survey.md` B.6 counts **97
off-scale occurrences across 14 values** (`6` x25, `10` x23, `2` x11, `14` x9, `20` x4, `3` x4,
`72` x4, `68` x3, `40` x2, `18` x2, `28`, `7`). Hairline values `1` and `1.5` and the derived
origins `68` and `72` are legitimate and stay; everything else is a defect. Per-file list in
`02-inventory-pc.md` V4.

**The melody** (`03-direction.md` 7.2). Four gap values, not interchangeable:

| Gap | Between |
|---|---|
| 4 / 8 / 12 | Parts of one object: glyph to label, title to subtitle, chip padding, stepper to count |
| 16 | Objects: card to card, the screen gutter, row padding |
| 24 | Sections; the space that replaces a divider under a section header |
| 32 | At most twice per screen: after a hero, before a bottom action bar |

A screen where every vertical gap is 16 has no hierarchy and fails the squint test. That is the most
common defect in the current build.

**The column model.** Three tokens replace the current mixture of 440 / 560 / 620 / none.

| Token | Value | Used by |
|---|---|---|
| `Size.Content` | **720** | Every scrolling ledger: Home, Настройки hub and all settings sub-pages, Аккаунт, Подписка, Покупка, Устройства, История платежей, Журнал |
| `Size.Form` | **480** | Вход, Онбординг, Синхронизация, every dialog body |
| `Size.PanePrimary` | **300** | The provider pane on the Серверы destination in wide mode |

- Content is **centred** inside the content area, not left-aligned against the rail. A 720px column
  hard against a 76px rail in a 2560px window reads as a broken layout; centred, it reads as a
  document.
- The gutter is **16** below 1000px of content width and **24** at or above it
  (`00-rules.md` 4.1). Nothing else in the scale changes with width.
- `SettingsView.axaml:216` is a bare `ScrollViewer` with no `MaxWidth`; at the app's own 1120x760
  preset its rows run about 1030px wide. This is V3 and it is closed by `Size.Content`.
- One scroll region per view. No nested scrollers, ever. The Серверы destination in wide mode is the
  single exception: two side-by-side scrollers that never overlap.

**The row, restated with desktop numbers.**

```
Border.Row  (MinHeight 56, Padding 16,12, Cursor=Hand, focusable)
├─ 40x40 Border.Tile        radius 12, 22px PathIcon, optically centred
├─ 12
├─ text column (star)       Title 16/700   max 2 lines, wraps, never truncates
│                           Subtitle 13/400 max 2 lines, muted
├─ 12
├─ value (optional)         Subtitle 13/400 muted, right-aligned, Numeric when a quantity
├─ 8
└─ ONE trailing             chevron 20 | rotating chevron 20 | unfold 20 | switch 52x32 | 40px icon button | nothing
```

Text origin = `16 + 40 + 12 = 68`. Every title on every screen starts there. Every hairline starts
there. Put a ruler on the screenshot.

### 2.5 Iconography

**One dictionary.** `Assets/Icons.axaml` holds every `StreamGeometry` under a `Geo.*` key.
`Assets/GlobalResources.axaml` currently holds **8** and views declare **more than 40 locally**,
including `Geo.Sub.Back` **nine times byte-identically**, `Geo.Login.Back` a tenth time, and
`Geo.Acc.Chevron` (`AccountView.axaml:42`) duplicating `Geo.ChevronRight`
(`GlobalResources.axaml:320`) outright. All local declarations are deleted.

Naming: `Geo.<Domain>.<Name>` where domain is one of `Nav`, `Action`, `State`, `Set`, `Dev`, `Pay`,
`Auth`, `Server`. Examples: `Geo.Nav.Servers`, `Geo.Action.Back`, `Geo.Action.Copy`,
`Geo.State.Check`, `Geo.Set.Dns`, `Geo.Server.Globe`, `Geo.Action.ArrowUp`.

**Four glyph sizes, and only four** (`00-rules.md` 10.3):

| Size | Where |
|---|---|
| 24 | Toolbar and navigation glyphs; rail and compact-bar items |
| 22 | Inside a 40px `Border.Tile`; inside `Button.IconButton40` |
| 20 | Inline chevron, unfold, inline status glyph, `Button.IconButton40.Row` |
| 16 | Inside a chip; the up/down arrows in the connect stats row |

`20-control-survey.md` B.5 counts **10 distinct explicit widths** today (`30`, `28`, `26`, `18`,
`15`, `32` are all off-token; `32` survives only inside `Border.EmptyIcon`). Three chevron sizes
(18 in `SettingsView.axaml:159`, 18 and 16 in `AccountView.axaml`) collapse to 20.

**Stroke and fill discipline.** Outline glyphs in rows and toolbars. Filled glyphs only for the
selected navigation destination and for a status glyph (a filled check on a selected server, a
filled shield when connected).

**The unified server icon** (`00-rules.md` 10.5, owner request 0.4.7). One treatment everywhere a
server appears - the server row, the connect hero, the server editor header, the tray tooltip, the
subscription group header:

```
Border 40x40, radius 12, Brush.Tile.Neutral
└─ Image 28x28, Assets/Flags/<cc>.png, clipped to an Ellipse 28x28
   fallback: PathIcon Geo.Server.Globe, 22px, Brush.OnSurfaceVariant
```

Raster flags are the one sanctioned exception to vector-only (`00-rules.md` 10.4). `FlagResolver.cs`
and `RemarkToFlagConverter` stay. `StripLeadingFlagConverter` stays and must also be adopted on
Android, which today renders the emoji flag in the tile **and** leaves it in the remark text.

**Every icon-only control gets a name.** 18 of 65 icon-only buttons carry neither `ToolTip.Tip` nor
`AutomationProperties.Name` (`MainWindow` 6, `BottomNavBar` 3, `AccountView` 2, and five others).
That is a P1 each.

**No emoji as chrome, no text glyphs as icons.** `ConnectHeroView` renders its speed arrows as
literal `Text="↑"` and `Text="↓"` TextBlocks. They become `Geo.Action.ArrowUp` and
`Geo.Action.ArrowDown` at 16px.

### 2.6 Motion

Tokens are `Common/Motion.cs` mirroring the XAML `Ease.*` keys. They are already 1:1 and stay 1:1.

| Token | ms | Easing | Spline | Use on desktop |
|---|---|---|---|---|
| `Dur.Instant` | 0 | none | | Reduced-motion fallback, every animation |
| `Dur.PressIn` | 90 | `Ease.OutQuart` | 0.25,1,0.5,1 | Pointer down: `scale(0.97)` |
| `Dur.PressOut` | 160 | `Ease.OutQuint` | 0.22,1,0.36,1 | Release, settle to rest |
| `Dur.State` | 220 | `Ease.Standard` | 0.2,0,0,1 | Selection, enable/disable, tint crossfade, rail indicator, panel crossfade |
| `Dur.Reveal` | 300 | `Ease.OutQuint` | | Sub-page enter, flyout open, inline expand, status strip enter |
| `Dur.Exit` | 150 | `Ease.Standard` | | Sub-page exit, flyout close (225 for a reveal reverse) |
| `Dur.Shell` | 200 | `Ease.Standard` | | Shell overlay crossfade, layout-mode morph |
| `Dur.Slow` | 450 | `Ease.OutExpo` | 0.16,1,0.3,1 | The single auth to Home hand-off. Nothing else |
| `Dur.Stagger` | 40 | none | | Per-item list delay, total capped at 400ms (max 10 items) |
| `Dur.Emphasis` | 600 | `Ease.OutQuint` | | The one hero moment: connect confirmation |

**One press language.** `scale(0.97)`, `RenderTransformOrigin="50%,50%"` (relative, never
`0.5,0.5`, which Avalonia reads as absolute pixels), in at `Dur.PressIn` with `Ease.OutQuart`, out
at `Dur.PressOut` with `Ease.OutQuint`. The seven values shipping today (`0.92` x15, `0.97` x8,
`0.99` x4, `0.96` x4, `0.9` x3, `0.94` x2, `0.98` x1) collapse to one. The connect disc keeps
`scale(0.94)` as the single documented exception, because it is a 176px object and 0.97 is
imperceptible at that size; that exception is written into `GlobalStyles.axaml` with a comment.

**One hover language.** `Brush.Hover` overlay (dark: black at 0.32; light: black at 0.06) or one
step up the surface ramp, crossfaded over **150ms** `Ease.Standard`. Never both.

**What never moves.** No ambient loops. No breathing. No idle sonar. No parallax. No page-load
choreography. No animated gradients (there are none). No shimmer except on a skeleton. The two
grandfathered `DoubleTransition Property="Width"/"Height"` cases on the rail collapse are the only
animated layout properties in the product and no new ones are added.

**The one hero moment.** Connect confirmation, 600ms, specified in 5.3.6. It exists once in the
whole product. The disconnect reverse runs at 75 percent tempo (450ms) and emits nothing.

**Reduced motion is a contract.** `MotionState.IsLite`, broadcast live from `SettingsViewModel`, with
subscribers re-applying on the spot. Any animation that reads the flag once in a constructor is the
exact bug `Common/MotionState.cs` was written to fix. The `.lite` class on the window zeroes
`Transitions` on every interactive class and de-selects cyclic keyframes at the selector level
(`GlobalStyles.axaml:1383-1446`); every new component adds its selector to that block in the same
change.

### 2.7 The component library, closed

After this rebuild the desktop has **one** of each of the following, defined in `Assets/Styles/`, and
a view that hand-rolls one is a defect.

#### 2.7.1 Buttons

| Class | Height | Radius | Type | Rest | Hover | Pressed | Focus | Disabled |
|---|---|---|---|---|---|---|---|---|
| `Button.Primary` | 48 | `Radius.Button` 16 | 14 Bold `Font.Ui` | `Brush.Accent` / `Brush.OnAccent` | `#3D7EF0`, 150ms | `#3877E0` + `scale(0.97)` | inner 2px `Brush.OnAccent` @40%, r16 | Opacity 0.38 |
| `Button.Primary.Tall` | 52 | 16 | inherits | | | | | |
| `Button.Tonal` | 48 | 16 | 14 Medium | `Brush.SurfaceHighest` / `Brush.OnSurface` | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px `Brush.Accent`, r18 | 0.38 |
| `Button.Tonal.Tall` | **52** | 16 | inherits | **promoted to global**; the identical local copies in `LoginView.axaml:19-22` and `OnboardingView.axaml` are deleted | | | | |
| `Button.Outlined` | 48 | 16 | 14 Medium | transparent + 1px `Brush.Outline` | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px r18 | 0.38 |
| `Button.Destructive` | 48 | 16 | 14 Bold | `Brush.Red` / `#FFFFFF` | `Brush.RedPressed` | `Brush.RedPressed` + 0.97 | outer 2px r18 | 0.38 |
| `Button.Text` | 40 | `Radius.Chip` 12 | 14 Medium | transparent / `Brush.Accent` | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px r14 | 0.38 |
| `Button.IconButton40` | 40x40 | `Radius.Pill` | glyph 22 | transparent | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px, pill | 0.38 |
| `Button.IconButton40.Row` | 40x40 | pill | glyph 20 | | | | | |
| `Button.IconButton40.Accent` | 40x40 | pill | glyph 22 `Brush.Accent` | | | | | |
| `Button.Stepper` | 40x40 | 12 | glyph 20 `Brush.Accent` | `Brush.Tile.Neutral` | `Brush.Hover` | `scale(0.97)` | outer 2px r14 | 0.38 |
| `Button.NavItem` | 76x64 rail / star x56 bar | 12 | glyph 24 + label 11 | `Brush.OnSurfaceVariant` | glyph to `Brush.OnSurfaceVariantHover` | `scale(0.97)` | inner 2px r12 | n/a |
| `Button.BackNav` | 40x40 | pill | glyph 22 | transparent | `Brush.Hover` | `Brush.Hover` + 0.97 | outer 2px, pill | n/a |
| `Button.Caption` | 46x32 | 0 | glyph 10 | transparent | `Brush.Hover`; close to `#8E1D23` | one step darker | inner 2px | n/a |

Deleted: `Button.IconButton` (the legacy 32x32 class at `GlobalStyles.axaml:226-245` **and its ten
verbatim view-local re-declarations**), `Button.LinkAction` (renamed `Button.Text`),
`Button.OutlinedAccent` (renamed `Button.Outlined`, and the accent border becomes neutral because an
outlined button is never the primary action), `Button.SegItem` (local, `LoginView`),
`Button.MethodChip` and `Button.MeterRow` (local, `AccountView` - both become rows),
`Button.MetaIcon` and `Button.MetaDanger` (local, `SubscriptionMetaView` - the 34x34 shrink is a
structural problem solved by an overflow menu, not by shrinking targets), `Button.Flat` (local,
`BuyView`), `Button.BottomNavItem` (local, `BottomNavBar` - merged into `Button.NavItem`),
`Button.RailToggle` (local, `MainWindow` - becomes `IconButton40`), `Button.WinBtn` (local,
`MainWindow` - becomes `Button.Caption`), and the phantom `Button.Success`.

**Result: 186 buttons across 28 class combinations become 186 buttons across 13 classes, with zero
view-local button styles.**

#### 2.7.2 Rows and containers

| Class | Spec |
|---|---|
| `Border.Card` | `Brush.Surface`, `Radius.Card` 20, 1px `Brush.OutlineVariant`, `Padding` 16 (0 when it hosts rows), elevation 0, no shadow |
| `Border.Row` | MinHeight `Size.Row` 56, Padding 16,12, transparent, `Cursor=Hand`, `:pointerover` `Brush.Hover`, `.pressed` `scale(0.97)`, focus ring inner 2px r12, `Focusable=True` `IsTabStop=True` |
| `Border.Row.static` | same geometry, `Cursor=Arrow`, no hover, no focus: a read-only fact row |
| `Border.ServerRow` | `Border.Row` plus the selection contract: `.selected` gets `Brush.SelectedFill` **and** a filled 20px `Geo.State.Check` in `Brush.Accent`. Radius 12, not 20. The permanent 1.5px border is deleted; a hairline between rows replaces it |
| `Border.Tile` | 40x40, `Radius.Tile` 12, `Brush.Tile.Neutral`. Modifiers `.Blue`, `.Green`, `.Red`, `.Amber` only |
| `Border.Chip` | `Radius.Chip` 12, `Padding` 8,4, `TextBlock.Chip`. Modifiers `.neutral` `.accent` `.green` `.amber` `.red`, each with its own fill at 18 percent and full-colour text |
| `Border.StatusChip` | **deleted**; `.paid/.pending/.failed/.canceled` were a payment vocabulary reused for subscription health, so the class names lied. Replaced by `Border.Chip` with a semantic modifier |
| `Border.ProtocolChip` | `Radius.Chip` 12, `Padding` 8,4, `TextBlock.Chip` in `Font.Grotesk`, `Brush.SurfaceHighest` fill, `Brush.OnSurface` text. Not accent-tinted: a protocol name is a fact, not a state |
| `Border.Meter` | the traffic and device bar: 4px track `Brush.SurfaceHighest` r2, fill `Brush.Accent` (solid, **never** a `LinearGradientBrush`), label **beside** the bar, never on it |
| `Border.Skeleton` | `Brush.SurfaceHighest`, r12, with the `.SkeletonPulse` opacity loop; one class replaces `Border.SkelBar` and `Border.SkelCard` and the three hand-copied silhouettes in `PaymentHistoryView.axaml` |
| `Border.EmptyIcon` | 64x64, r20, `Brush.Tile.Neutral`, 32px glyph. `PaymentHistoryView.axaml`'s hand-coded copy is deleted |
| `Border.StatusStrip` | section 3.8 |
| `Border.SubToolbar` | section 3.6 |
| `Border.Scrim` | `Brush.Scrim`, full bleed, click closes |

Deleted: `Border.SheetTop`, `Border.SheetHandle` (bottom sheets do not exist on desktop),
`Border.Toast` (replaced by the status strip), `Border.SearchPill` (folded into
`TextBox.Field.search`), `Border.AccountChip` (becomes `Border.Card` with a row inside),
`Border.PriceOption` (becomes `Border.Row.selectable`), `Border.TrafficPill` (becomes
`Border.Meter`), `Border.DnsChip` (local, `DnsSubView` - becomes `Border.Row.selectable`),
`Border.MethodRow` (local, `PingSettingsPage` - same), `Border.RailIndicator` and `Border.NavRail`
(fold into the shell styles).

#### 2.7.3 Inputs

**One field theme.** `TextBox.Field`, replacing `TextBox.Incy` (min height 52, r14) and
`TextBox.IncyField` (min 44, r14) and the duplicate re-declaration of `TextBox.Incy` inside
`SettingsView.axaml:84-115`.

```
TextBox.Field
  Height 48                       (Size.Field, new token, PC-D5)
  CornerRadius Radius.Chip 12     (the shape lock: inputs are 12, not 14)
  Background Brush.SurfaceHighest (P3: a field is a hole in the panel)
  BorderThickness 1  BorderBrush Brush.Outline
  Padding 12,0   FontSize 14  Font.Ui
  Watermark Brush.OnSurfaceVariant   (>= 4.5:1, not the muted default)
  :pointerover  BorderBrush Brush.OnSurfaceVariant
  :focus        BorderBrush Brush.Accent + outer 2px ring r14
  .error        BorderBrush Brush.Red, and an error TextBlock below in Brush.RedText 12
  :disabled     Opacity 0.38
  .search       leading 20px Geo.Action.Search glyph, trailing 20px clear button when non-empty
  .numeric      Font.Numeric + FontFeatures tnum,lnum
```

**Field anatomy, always.** Label above in `Caption`, field, helper slot below **present in the markup
even when empty** so the layout never jumps. Placeholder is never the label. Validation on blur, not
per keystroke. After a failed submit, focus moves to the first invalid field.

**126 `TextBox` instances exist and 118 fall through to Semi.** All of them are inside views this
plan rebuilds or deletes, except the two on the Account tab (`AccountView.axaml:372` top-up amount,
`:1223` link-email), which opt in during wave 4.

**`ComboBox` is deleted from the product.** All 66 instances live in views that are rebuilt or
deleted. A choice among many becomes a **row plus a picker flyout**: the row shows the current value
(Incy's best idea), the trailing affordance is `unfold_more` when the choice cycles in place or a
chevron when it opens a flyout list, and the flyout is a list of `Border.Row.selectable` with a
filled check on the current item. A choice among two to four becomes `ToggleButton.Segment`.

**`ToggleSwitch.iOS` stays** as the single switch theme (52x32 track, 26 knob, `Brush.SurfaceHighest`
off / `Brush.Accent` on, knob squash 0.9 at 90ms). It gains a focus ring, which it does not have
today. The 43 stock `ToggleSwitch` instances are in views that are rebuilt or deleted. The switch is
removed from the tab order and the **row** owns the tab stop and the `Space` activation - the pattern
`SettingsView.axaml.cs` already implements, generalised.

#### 2.7.4 Overlays

| Component | Spec |
|---|---|
| `FlyoutPresenter` (`IncyFlyoutTheme`) | `Brush.Surface` P1, 1px `Brush.Outline`, r20, `Padding` 16, **no BoxShadow**, `MaxHeight` 480 with an internal `ScrollViewer`, placement anchored to the invoking control, Esc closes, focus enters on open and returns to the trigger on close |
| `MenuFlyout` | same chrome; items are 40px rows with a 20px leading glyph, 12 gap, 14 label; destructive items in `Brush.RedText` at the bottom after a hairline |
| Modal window | `Brush.Bg` window, `WindowDecorations="None"`, `SizeToContent`, body is one `Border.Card` at `Size.Form` 480, sits on `Brush.Scrim`. Used **only** for a genuinely separate task, and after section 8 there are exactly three of them |
| `Border.StatusStrip` | section 3.8 |

**Bottom sheets do not exist on desktop.** `BuyView`'s payment-method sheet is a phone idiom in a
900x600 window and is replaced by an inline row group (7.10).

#### 2.7.5 The four permitted view-local styles

After the rebuild, `<Style Selector=...>` inside a view is a defect except for:

1. `MainWindow.axaml` window-chrome styles - **moved out** to `Assets/Styles/Shell.axaml`, so this
   exception is zero after wave 1.
2. A one-off animation storyboard bound to a single named element in that view (the connect
   sonar, the sync ring).
3. A `DataTemplate`-scoped selector that cannot be expressed globally.
4. Design-time-only styles under `d:` .

Target: **190 view-local style rules across 24 files becomes fewer than 10.**

### 2.8 Keyboard, focus and pointer

**Focus is mandatory and always visible.** 2px `Brush.Accent` ring, 2px offset, radius following the
control. It is never removed and never made "focus-visible only": on desktop a user who tabbed here
must see where they are, and a user who clicked here loses nothing by the ring appearing.

**Tab order follows visual order**, top to bottom, left to right. The rail is one tab stop that
cycles its items with arrow keys. A row is one tab stop, not three.

**Every task is completable without a mouse.** This is the acceptance test for every screen in
section 5 to 7, and it is stated per screen.

**Shortcuts.** `MainWindow_KeyDown` (`MainWindow.axaml.cs:1875`) currently handles six. The full set:

| Shortcut | Action | Scope |
|---|---|---|
| `Esc` | Pop the current sub-page; if none, close a flyout; if none, clear a search field; if none, nothing | Shell |
| `Alt+Left` / mouse button 4 | Pop the current sub-page | Shell |
| `Alt+Right` / mouse button 5 | Re-enter the popped sub-page, if the stack was popped in the last 10 seconds | Shell |
| `Ctrl+1` .. `Ctrl+4` | Go to Главная / Серверы / Настройки / Аккаунт | Shell |
| `Ctrl+Tab` / `Ctrl+Shift+Tab` | Next / previous destination | Shell |
| `Ctrl+,` | Настройки | Shell |
| `Ctrl+F` | Focus the search field of the current destination (Серверы, Настройки, Журнал, Прокси по приложениям) | Destination |
| `Ctrl+K` | Command palette **(not built; reserved, do not bind)** | - |
| `Ctrl+N` | Add a server or a provider, from Серверы | Destination |
| `Ctrl+V` | Add from clipboard | Shell |
| `Ctrl+S` | Scan the screen for a QR code | Shell |
| `F5` | Refresh the current destination (subscriptions on Серверы, profile on Аккаунт) | Destination |
| `Ctrl+R` | Reconnect | Shell |
| `Ctrl+Enter` | Connect or disconnect | Shell |
| `Ctrl+P` | Ping all servers in the current group | Серверы |
| `Ctrl+A` | Select all servers | Серверы, list focused |
| `Delete` | Remove the selected servers, with an undo strip | Серверы |
| `Enter` | Activate the focused row; submit the focused form | Everywhere |
| `Space` | Toggle the focused switch row; toggle selection on a server row | Everywhere |
| `Up` / `Down` | Move within a list; `Home` / `End` jump | Any list |
| `Ctrl+=` / `Ctrl+-` / `Ctrl+0` | UI zoom step and reset | Shell |
| `Ctrl+Shift+L` | Open Журнал | Shell |
| `Ctrl+W` | Hide to tray (never quit; quit is tray only) | Shell |

`Ctrl+Enter`, `Ctrl+R`, `Ctrl+P` and `Ctrl+Shift+L` are new and must not collide with a focused text
field: when a `TextBox` has focus, only `Esc`, `Enter`, `Ctrl+A/C/V/X/Z` and the zoom set are
handled, everything else falls through.

**Cursor.** `Hand` on rows, cards that navigate, links and nav items. Default arrow everywhere else.
`SizeWE` / `SizeNS` / `SizeNWSE` on the eight resize zones. Never a custom cursor bitmap.

**Pointer minimums** (`00-rules.md` 7.2): 32x32 for a pointer-only control, 40 for anything in a
toolbar or row, 52 for a primary CTA. Today's violations: the caption buttons at 44x22
(`MainWindow.axaml:52-61`), the rail collapse toggle at 30x30 (`:131-143`), `SubscriptionMetaView`'s
four buttons shrunk to 34x34, and the three `Height="32"` overrides on `IconButton40` in
`AccountView.axaml:619,704,759`. All are corrected in the screens that own them.

### 2.9 Window sizes and the density model

| Band | Window width | Shell chrome | Content |
|---|---|---|---|
| **Compact** | < 760 | Bottom bar, 4 items, MinHeight 56 | One column, gutter 16, `MaxWidth` = viewport |
| **Wide** | >= 760 (hysteresis: back to compact only below 736) | Left rail 76, collapsible to 0 | Centred column at `Size.Content` 720, gutter 16 below 1000px of content width, 24 above |

Two modes, exactly as the shell implements today (`CompactBreakpointWidth = 760.0`,
`LayoutHysteresis = 24.0`, `MainWindow.axaml.cs:31-32`). Inside **wide**, two screens have an
internal split that appears above a content-width threshold; that is a layout decision inside a
destination, not a third shell mode:

- **Главная** splits into connect pane plus ledger at content width >= 980 (5.2).
- **Серверы** splits into provider pane plus list at content width >= 900 (6.2).

**Sizes.**

| | Today | After |
|---|---|---|
| Default | 372x630 (compact) | **1080x720** (wide) |
| Minimum, wide | n/a | **900x600** |
| Minimum, compact | 340x560 | **380x620** |
| Wide toggle target | 1120x760 | 1080x720 |
| Compact toggle target | 372x630 | 400x680 |

The current default means **the two-pane layout the team built is not the layout anyone sees on
first run** (`31-self-assessment.md` B9). `00-rules.md` 12.3 sets a 900x600 usability floor;
compact mode below that floor is an explicit user choice reached by the layout toggle or by dragging
the window narrow, and every view is verified at 380x620 as well. This needs decision **PC-D3**.

**DPI and zoom.** The `LayoutTransformControl` in-app zoom (`#uiScaleHost`) is kept: on a 4K monitor
at 100 percent OS scaling everything is physically tiny, and this layer lets the user enlarge the
whole interface without touching OS DPI. Presets 100 / 125 / 150 / 175 / 200 percent, persisted in
`UiItem.UiScale`, live through `Common/UiScaleState.cs`. `MinWidth` and `MinHeight` scale with the
factor, as they already do. Every view is verified at OS DPI 100 / 125 / 150 / 200 **and** at zoom
200, which is the worst case: a 380x620 window at zoom 2.0 has 190x310 of layout space and must
still show the compact bar and one full row without clipping.

### 2.10 Copy

`00-rules.md` 9 is the law. Desktop specifics:

- **Russian and English only.** `Common/L*.cs` registers `(key, ru, en)` triples consumed by the
  `{loc:T Key}` markup extension. Every string on every surface in this plan has a key. A view with
  a literal Russian string in the markup is a defect; a view with a `resx:ResUI` reference is a
  defect **and** a parity break, because `ResUI` is the upstream Chinese-origin resource set with no
  Russian at all.
- **44 em-dashes and en-dashes in `Common/L.*.cs`** are cleared in wave 1. Where a dash carried a
  pause, use a comma, a colon, a full stop or a line break. Where it carried a range, use a hyphen.
  Where it was a placeholder for a missing value, use the word: `DelayDisplayConverter`
  (`ServerListView.axaml:44`) renders a failed probe as «-», an em-dash, **as a value the user reads
  on every row of the main screen**; it becomes «нет ответа».
- **Desktop-only strings** that Android does not need, and therefore the only place a string may
  exist on one platform and not the other: keyboard shortcut hints in tooltips, the caption button
  names, «Свернуть панель» / «Развернуть панель», the tray items, and «Перезапустить с правами»
  (Linux TUN elevation).
- **Tooltips.** Every icon-only control has one. Format: the action, then the shortcut in
  parentheses, both sentence case: «Проверить задержку (Ctrl+P)». Delay 600ms, no animation.
- **Copy that dies in this rebuild:** «Приветствуем!» (exclamation mark, banned), «Пока нет
  подписок» as a *welcome* headline, «Сервера» (the term is «Серверы»), «App memory» (untranslated),
  and every «Скоро» on a permanently disabled control.

### 2.11 File layout

`02-inventory-pc.md` V14: there is no `Views/Styles/` and no `Views/Themes/`; theming lives in two
1000-line-plus files plus 260 lines inside `MainWindow.axaml` plus a C# function. Target:

```
v2rayN.Desktop/
├── App.axaml, App.axaml.cs                 ← theme selection + tray only; BuildMonoOverlay deleted
├── Assets/
│   ├── Tokens.axaml                        ← Space.*, Radius.*, Size.*, Dot.*, Font.*
│   ├── Icons.axaml                         ← every Geo.* StreamGeometry
│   ├── Themes/
│   │   ├── Dark.axaml                      ← every Brush.* for ThemeVariant Dark
│   │   ├── Light.axaml                     ← every Brush.* for ThemeVariant Light
│   │   └── Mono.axaml                      ← the greyscale overlay, as DATA not C#
│   ├── Styles/
│   │   ├── Text.axaml                      ← the 10 ramp classes
│   │   ├── Buttons.axaml                   ← the 13 button classes
│   │   ├── Rows.axaml                      ← Row, ServerRow, Tile, Chip, Meter, Card
│   │   ├── Inputs.axaml                    ← TextBox.Field, ToggleSwitch.iOS, Segment
│   │   ├── Overlays.axaml                  ← flyout, menu, scrim, dialog, status strip
│   │   ├── Shell.axaml                     ← caption, rail, compact bar, sub-toolbar, scrollbar
│   │   └── Motion.axaml                    ← Ease.*, transitions, the .lite suppression block
│   ├── Fonts/, Flags/, *.ico
├── Views/
│   ├── Shell/       MainWindow, NavRail, CompactNavBar, SubPageHost, StatusStrip, TitleBar
│   ├── Home/        HomePage, ConnectControl, ServerIdentityRow, SubscriptionRow
│   ├── Servers/     ServersPage, ProviderPane, ServerList, ServerRow, ProviderGroupHeader,
│   │                ServerEditorPage, ProviderEditorPage
│   ├── Account/     AccountPage, SubscriptionPage, BuyPage, DevicesPage, PaymentHistoryPage
│   ├── Auth/        LoginPage, OnboardingPage, AccountSyncPage
│   ├── Settings/    SettingsPage + 14 sub-pages
│   ├── Dialogs/     MessageDialog, QrDialog, SudoDialog
│   └── Components/  SubPage.cs (the shell control), EmptyState, ErrorState, SkeletonList,
│                    SearchField, PickerFlyout, MeterBar, ServerIcon
```

Every rename in that tree is mechanical and is done in wave 1, before any redesign work, so that
later waves do not re-file code they just wrote.

---

## 3. The window shell

**Files:** `Views/MainWindow.axaml` (737 ln) + `MainWindow.axaml.cs` (2 029 ln, the largest file in
the project), `Views/BottomNavBar.axaml` (180 ln) + `.axaml.cs` (206 ln).
**Verdict: RESTYLE the chrome and the mechanics, REBUILD the navigation model.**

The mechanics here are hard-won and are kept: the `LayoutTransformControl` zoom host, the eight-zone
native resize grid, the keep-alive tab host that never re-parents (re-parenting was a documented
crash source: compact to Settings to widen), the directional slide on tab change, the theme
flood-reveal. What changes: four destinations instead of three, per-destination sub-page stacks,
Escape and mouse-back, a real feedback surface, flat surfaces, 32px caption targets, and 260 lines of
chrome styling moved out of the window into `Assets/Styles/Shell.axaml`.

**Android counterpart:** `MainActivity` plus its `BottomNavigationView`. Same destination set, same
order, same labels, same gate logic (syncing > empty > content). The rail is the desktop's expression
of the bottom bar and appears at >= 760px, exactly as Android's bottom bar becomes a
`NavigationRailView` at `sw600dp`. Everything else about the shell is desktop-only because Android
has no window.

### 3.1 Visual tree

```
Window  1080x720, MinWidth 900 MinHeight 600 (wide) | 380x620 (compact)
        WindowDecorations=None, Background=Brush.Bg, FontFamily=Font.Ui
│
Panel #windowRoot
├─ LayoutTransformControl #uiScaleHost              ScaleTransform, Ctrl +/-/0
│   └─ Panel #dialogLayer                           replaces DialogHost (see 9.1)
│       └─ Grid #chromeRoot   RowDefinitions="32,*"
│           ├─ [0] Grid #titleBar        32px custom caption          (3.2)
│           └─ [1] Panel
│                 ├─ Grid  #bodyRoot     the shell                    (3.3)
│                 ├─ OnboardingPage      full bleed, first run        (7.11)
│                 ├─ AccountSyncPage     post-login import overlay    (7.12)
│                 └─ ContentControl #subPageHost   the sub-page stack (3.7)
├─ Border #themeTransitionOverlay + Image           theme flood snapshot (3.10)
└─ Grid #resizeGripHost   4/*/4 x 4/*/4             8 transparent 4px resize zones
```

Deleted from the tree: `Border #snackHost` (`MainWindow.axaml:623`, permanently invisible),
`ContentControl #contentStatusBarView` (`StatusBarView` mounted at 0x0 with Opacity 0 purely to keep
its interaction handlers alive - the handlers move to the shell, the phantom view is deleted), the
full-bleed `Brush.HomeGradient` border spanning rail and content (`:429-435`), the second gradient
under `#contentHost` (`:551`), and `Border #navScrim` with its `OpacityMask` gradient (`:582-586`).

### 3.2 The title bar

32px tall, `Brush.Bg`, no border, no shadow, no separate tone. It is P0, like everything else.

```
Grid #titleBar  Height=32  Background=Brush.Bg  (PointerPressed → BeginMoveDrag)
├─ [left, Margin 12,0]  StackPanel Orientation=Horizontal Spacing=8
│    ├─ Border 18x18 r6  Brush.Accent   → TextBlock "d"  11 Bold Font.Grotesk  Brush.OnAccent
│    └─ TextBlock "departament"  14 Bold  Font.Grotesk  Brush.OnSurface
├─ [centre]  nothing. The window title is the wordmark; there is no per-page title here
└─ [right, Margin 0,0,4,0]  StackPanel Orientation=Horizontal
     ├─ Button.Caption #btnMin    46x32  glyph Geo.Caption.Min    10px
     ├─ Button.Caption #btnMax    46x32  glyph Geo.Caption.Max / Geo.Caption.Restore
     └─ Button.Caption.close #btnClose 46x32  glyph Geo.Caption.Close
```

- **46x32** replaces today's 44x22, which is under the 32px pointer minimum. 46x32 is the Windows 11
  caption metric and reads native on all three platforms.
- Hover: `Brush.Hover` on min and max; **`#8E1D23`** on close with a white glyph, pressed one step
  darker at `#7A181D`. This is the current behaviour and it is well judged: a deep red that belongs
  to the chrome rather than the neon `Brush.Red` accent that used to fill the whole pill.
- Double-click on the bar toggles maximise. Drag from the bar moves the window and, in compact mode,
  dragging to the top or side edge of the working area expands to wide (`EdgeSnapThreshold = 6`,
  kept).
- On macOS the three buttons are hidden and the native traffic lights are used; the wordmark shifts
  right by 72px. `MacAppUtils.cs` already carries the platform check.
- Names: `AutomationProperties.Name` «Свернуть», «Развернуть» / «Восстановить», «Закрыть».

### 3.3 `#bodyRoot`, the two layout modes

One `Grid`, re-laid by `ApplyLayoutMode(bool compact)` (`MainWindow.axaml.cs:696`). Controls are
never re-parented; only `Grid.Row` / `Grid.Column` and visibility change.

**Wide** (width >= 760):

```
Grid  ColumnDefinitions="Auto,1,*"  RowDefinitions="*"
├─ [col 0] Border #railHost        Width 76 (or 0 when collapsed)   Brush.Bg
├─ [col 1] Border                  Width 1   Brush.OutlineVariant
└─ [col 2] Panel  #contentArea
      ├─ Panel #contentHost        the four keep-alive destinations
      └─ Border.StatusStrip #statusStrip   DockPanel.Dock=Bottom, collapsed by default
```

**Compact** (width < 760):

```
Grid  ColumnDefinitions="*"  RowDefinitions="*,Auto,Auto"
├─ [row 0] Panel #contentArea
├─ [row 1] Border.StatusStrip #statusStrip
└─ [row 2] CompactNavBar        MinHeight 56, 1px top hairline Brush.OutlineVariant
```

The compact bar's 24px gradient scrim is deleted; a 1px hairline separates it, which is the same
device used everywhere else in the product and survives all three themes.

**The layout morph.** The explicit toggle (`ToggleLayoutSize`, `:1267`) animates the window between
1080x720 and 400x680 over `Dur.Shell` 200ms `Ease.Standard`, with a crossfade of the chrome. Under
`.lite` it snaps. The mode also changes on user resize with the 24px hysteresis, and the current
destination and its scroll position survive the change.

### 3.4 The navigation rail (wide)

```
Border #railHost  Width 76  Background Brush.Bg
└─ DockPanel
   ├─ [Top] StackPanel #navItems  Margin 0,8,0,0
   │    ├─ Button.NavItem #navHome     76x64   Geo.Nav.Home     «Главная»
   │    ├─ Button.NavItem #navServers  76x64   Geo.Nav.Servers  «Серверы»
   │    ├─ Button.NavItem #navSettings 76x64   Geo.Nav.Settings «Настройки»
   │    └─ Button.NavItem #navAccount  76x64   Geo.Nav.Account  «Аккаунт»
   │    (overlaid) Border #railIndicator  3x28  Brush.Accent  r2
   └─ [Bottom] StackPanel Margin 0,0,0,12  Spacing 8  HorizontalAlignment=Center
        ├─ Ellipse #railStatusDot  8x8         (3.4.3)
        └─ Button.IconButton40 #btnRailToggle  40x40  Geo.Action.ChevronLeft
```

**3.4.1 The item.** 76 wide, 64 tall, glyph 24 centred at y=14, label 11 centred at y=42.

| State | Glyph | Label | Fill |
|---|---|---|---|
| Rest | `Brush.OnSurfaceVariant`, outline | 11 Medium `Brush.OnSurfaceVariant` | transparent |
| Hover | `Brush.OnSurfaceVariantHover` | unchanged | transparent. **No background box** |
| Pressed | unchanged | unchanged | `scale(0.97)` at `Dur.PressIn` |
| Current | `Brush.Accent`, **filled** variant of the glyph | 11 **Bold** `Brush.Accent` | transparent + the indicator |
| Focus | unchanged | unchanged | inner 2px `Brush.Accent` ring r12 |

Selection reads on **three** channels (colour, weight, the indicator bar) which is one more than the
required two, and it is the reason this component is already the best-executed piece in the app.
**No ripple, no glow** (owner request 0.4.8), and hover does not paint a box: the glyph darkens.
That restraint is kept verbatim.

**3.4.2 The indicator.** 3x28, `Brush.Accent`, `CornerRadius=2`, positioned by
`Y = index * 64 + 26` and slid on `TranslateTransform.Y` over `Dur.State` 220ms `Ease.OutQuint`.
First paint is instant, not animated (`_railIndicatorSeeded`). Under `.lite` every move is instant.
With four items the travel range is 0 to 192.

**3.4.3 The connection dot.** 8x8 `Ellipse`, bottom of the rail, above the collapse toggle.
`Brush.OnSurfaceVariant` when disconnected, `Brush.Accent` while connecting (with a 1.2s opacity
pulse 1.0 to 0.4 that runs **only while the core is actually negotiating**), `Brush.Green` when
connected. Tooltip carries the word: «Не подключено» / «Подключение…» / «Подключено · Нидерланды».
Colour is never the only signal, and here the word lives in the tooltip and in Home's status line.

**3.4.4 Collapse.** `Width 76 → 0` and `Opacity 1 → 0` over `Dur.Shell` 200ms. The toggle moves to a
40x40 `Button.IconButton40` (up from 30x30) and, when collapsed, re-appears as a 40x40 floating
button at the top-left of the content area with `Geo.Action.ChevronRight`.
Tooltip: «Свернуть панель» / «Развернуть панель». State persists.

### 3.5 The compact navigation bar

`Views/Shell/CompactNavBar.axaml`, replacing `BottomNavBar.axaml`. **The two navigation
implementations merge into one `Button.NavItem` class** (`20-control-survey.md` B.4 documents two
complete independent components with two class names, `.active` and `.sel`, and two indicator
mechanisms).

```
Border  MinHeight 56  Background Brush.Bg  BorderThickness 0,1,0,0  BorderBrush Brush.OutlineVariant
└─ Grid ColumnDefinitions="*,*,*,*"
     4 x Button.NavItem   (star width x 56, glyph 24 at y=8, label 11 at y=36)
     each with a 34x3 Brush.Accent pill at the bottom of the current item, r2
```

The travelling pill uses the same `Dur.State` 220ms `Ease.OutQuint` slide as the rail indicator, not
a per-item show and hide. Press is `scale(0.97)`, not the current `0.92`.

**The «Аккаунт» item is never collapsed to zero width.** Today it is column-collapsed when signed out
and expands on sign-in, which makes the bar's geometry change under the user. Signed out, the item
stays and its label stays «Аккаунт»; the destination renders its sign-in gate. This also matches
Android, where the tab is always present.

### 3.6 The sub-page shell

One reusable control, `Views/Components/SubPage.cs` plus `SubPage.axaml`, replacing **nine
hand-rolled copies** of the same chrome (`02-inventory-pc.md` 1.4: all eight settings sub-pages plus
`LoginView` re-declare the back-arrow geometry, a local `Button.IconButton:pressed` style
contradicting the global press scale, and their own toolbar `Grid`).

```
UserControl SubPage    Background=Brush.Bg
└─ DockPanel
   ├─ [Top] Border.SubToolbar   Height Size.SubToolbar 56   Background Brush.Bg
   │        no border, no shadow, no elevation, no separate tone   (owner request 0.4.6)
   │   └─ Grid ColumnDefinitions="Auto,*,Auto"  Margin 8,0,8,0
   │        ├─ [0] Button.BackNav  40x40  Geo.Action.Back  22px  ToolTip «Назад (Esc)»
   │        ├─ [1] TextBlock.Title  «{Title}»  16/700  Margin 8,0,0,0  VerticalAlignment=Center
   │        └─ [2] ContentPresenter #toolbarAction   at most ONE 40x40 action;
   │                                                 more go into a kebab MenuFlyout
   └─ [Fill] ScrollViewer #scroll
        └─ ContentPresenter  MaxWidth={Size.Content} or {Size.Form}  HorizontalAlignment=Center
```

- **The toolbar title is `Title` 16/700, not `Headline` 24.** `BuyView`, `DevicesView` and
  `PaymentHistoryView` all use Headline today; `00-rules.md` 4.8 specifies Title.
- **On scroll the toolbar does not change colour and does not gain elevation.** The single permitted
  variant is a 1px `Brush.OutlineVariant` hairline that fades in under the toolbar over `Dur.State`
  220ms once `scroll.Offset.Y > 0`, and fades out at 0. It is opt-in per page and is used only where
  content would otherwise slide under the title with no boundary at all (the long settings
  sub-pages, the log).
- `ISubPage.BackRequested` stays as the contract; the host wires it to `PopSubPage`.
- Every sub-page declares `RouteId` (3.7) and `Title`.

### 3.7 Routes, the sub-page stack, and back

**3.7.1 Routes.** Every destination and every sub-page has a stable id string. This is what makes
`depv://` deep links, session restore and the status strip's actions targetable, and it is what the
current model has no vocabulary for at all.

```
home
servers
servers/provider/{subId}
servers/editor/{serverId?}          (new server when the id is absent)
servers/provider-editor/{subId?}
settings
settings/perapp | routing | routing/ruleset/{id} | dns | bypass | provider | ping
         | geofiles | urlschemes | core | core/log | core/advanced | hotkeys
         | backup | update | about
account
account/subscription/{subId}
account/buy
account/devices
account/history
auth/login
```

**3.7.2 The stack is per destination.** `_subStack` (`MainWindow.axaml.cs:74`) becomes
`Dictionary<AppTab, List<Control>>`. Today it is global, so «Аккаунт → Покупка → click Настройки in
the rail» leaves Покупка sitting on top of Настройки. After the change, switching destinations
switches to that destination's own stack, and switching back restores the sub-page you left, its
scroll position and its filter state.

**Depth is capped at 2 below a destination** (`03-direction.md` 7.3). Three levels means the
information architecture is wrong, not that routing needs another push. The two legitimate depth-2
routes are `settings/routing/ruleset/{id}` and `settings/core/log`.

**3.7.3 Back, four ways.**

| Affordance | Handler |
|---|---|
| `Button.BackNav` in the sub-toolbar | `ISubPage.BackRequested` |
| `Esc` | `MainWindow_KeyDown`, only when no flyout is open and no `TextBox` with content is focused |
| Mouse button 4 (`PointerPressed` with `XButton1`) | `MainWindow_PointerPressed`, tunnel |
| `Alt+Left` | `MainWindow_KeyDown` |

Escape order of precedence, evaluated top down: an open `MenuFlyout` or `Flyout` closes; an open
modal dialog cancels; a non-empty search field clears; the top sub-page pops; otherwise nothing (the
window never closes on Escape).

**3.7.4 Motion.** Push: the incoming page enters from `TranslateX +16 → 0` with `Opacity 0 → 1` over
`Dur.Reveal` 300ms `Ease.OutQuint`; the page under it fades to `Opacity 0` over `Dur.Exit` 150ms.
Pop: mirrored, `-16 → 0` for the revealed page, and the leaving page exits to `+16` over 225ms
(75 percent of the enter). `.lite` snaps. This is the same 16px slide vocabulary the destination
change uses, so the shell has one motion grammar rather than two.

**3.7.5 Session restore.** On launch the shell restores the last destination and, if the last route
was a sub-page whose data is still valid, that sub-page. It does **not** restore
`servers/editor/{id}` or `account/buy` (in-flight tasks), and it never restores over the onboarding
or sync gate.

### 3.8 The status strip: the feedback channel

**This is a new component and it closes F3.** Today there is no user-visible feedback surface at all:
`snackHost` is permanently `IsVisible=False` by design, `DelegateSnackMsg`
(`MainWindow.axaml.cs:1765`) forwards every message to `NoticeManager.SendMessage` and
`MsgViewModel`, and `MsgView` is registered in `SimpleViewLocator.cs:26` but never instantiated.
Clipboard-import failures, subscription-update results and engine errors are written to a surface
that has no window.

The owner rejected floating toasts. `Border.Toast` is deleted. The replacement is docked, identical
in structure to the Android snackbar's content but never floating.

```
Border.StatusStrip   MinHeight 48   Background Brush.Surface
                     BorderThickness 0,1,0,0   BorderBrush Brush.OutlineVariant
                     Padding 16,8   docked at the BOTTOM of #contentArea
                     (wide: full content width; compact: full width, above the nav bar)
└─ Grid ColumnDefinitions="Auto,*,Auto,Auto"  ColumnSpacing 12
   ├─ [0] PathIcon 20   Geo.State.Info | Geo.State.Warning | Geo.State.Error
   ├─ [1] TextBlock.Body   MaxLines 2   TextWrapping=Wrap
   ├─ [2] Button.Text      the recovery action, or collapsed
   └─ [3] Button.IconButton40.Row  Geo.Action.Close  20px   ToolTip «Закрыть»
```

| Severity | Glyph tint | Persistence |
|---|---|---|
| `info` / success | `Brush.Accent` | Auto-dismiss after **5 s** |
| `warning` | `Brush.Icon.Yellow` `#EAB308` | Auto-dismiss after 8 s |
| `error` | `Brush.RedText` `#FF6069` | **Persists until acted on or dismissed.** An error the user did not see is an error we did not report |

- Enter: `TranslateY 8 → 0` plus `Opacity 0 → 1`, `Dur.Reveal` 300ms `Ease.OutQuint`. Exit: 225ms.
  `.lite` snaps.
- **One at a time.** A new message replaces the current one with a `Dur.State` 220ms crossfade. The
  queue holds at most three; a fourth drops the oldest `info`.
- The strip never covers content: it is a docked row, so the content area shrinks by its height and
  the scroll position is preserved.
- The action is a route (`Повторить`, `Продлить`, `Устройства`, `Открыть журнал`), executed through
  the route table in 3.7.1.
- Every message is also appended to the durable log (7.20), so «Открыть журнал» always has
  somewhere to go.

**The eight error cases** (`30-reference-analysis.md` 6.2a) render here with their copy and their
primary action, and **one silent retry precedes any of them**: a transient drop re-attempts once with
the connect ring in a reconnecting micro-state at `Dur.State` 220ms, and only the second failure
produces a strip.

**Offline** is not an error strip. It is a persistent `info` strip that never auto-dismisses:
`Нет сети. Показаны последние данные.` with the action `Повторить`, while the screens keep their last
known data and disable the actions that need the network.

### 3.9 The shell gates

Three-way, `ApplyShellVisibility()` (`MainWindow.axaml.cs:823`), precedence `syncing > empty >
content`. Kept, with the launch bug closed.

| Gate | Condition | Surface |
|---|---|---|
| Syncing | post-login import in flight | `AccountSyncPage`, full bleed under the caption (7.12) |
| Startup loading | cold start with a stored session, before the first profile resolve | `AccountSyncPage` in its «Загружаем» variant. **Not** the sign-in gate |
| Empty | `HomeViewModel.IsEmpty` is a **known** fact, and the user is not signed in | `OnboardingPage` (7.11) |
| Content | otherwise | `#bodyRoot` |

The distinction between "we know the user has nothing" and "we do not know yet" is load-bearing:
`_isEmpty` starts `false` and is raised from a synchronous snapshot of local storage taken before the
first frame (`_storedServersAtLaunch`). A returning user must never see «Добавьте подписку» for one
frame. Crossfade between gates is `Dur.Shell` 200ms.

### 3.10 The theme transition

Kept as the one piece of chrome flourish in the product, because it communicates a real state change
and is bounded. `Border #themeTransitionOverlay` holds a `RenderTargetBitmap` snapshot of the old
theme, revealed away with an expanding circular clip originating at the click point, ~520ms
`Ease.OutQuint`, driven by `App.ThemeTransitionHook`. Skipped entirely under `.lite`. It does not use
`Dur.Emphasis` and it is not a second hero moment: it is a masked crossfade of a static bitmap.

Three themes ship: **Тёмная** (default), **Светлая**, and **Монохром**, the last being an overlay on
top of either. After 2.11 the mono overlay is `Assets/Themes/Mono.axaml`, not
`App.axaml.cs:580 BuildMonoOverlay`, so it can be reviewed as data and diffed against the other two.

### 3.11 The tray

`App.axaml:26-48`. Native, OS-drawn; it cannot be Incy-styled, so the design work is the item set,
the labels, the icon states and the click semantics.

**Icon states** (`Assets/*.ico`, 16 / 24 / 32 / 48 px, monochrome-safe on both light and dark task
bars):

| State | Icon | Tooltip |
|---|---|---|
| Disconnected | `NotifyShieldIdle.ico` outline shield, neutral | `departament · не подключено` |
| Connecting | `NotifyShieldIdle.ico` with a 4-frame sweep | `departament · подключение…` |
| Connected | `NotifyShieldOn.ico` filled shield, accent | `departament · Нидерланды, Amsterdam` |
| Error | `NotifyShieldIdle.ico` with a red dot | `departament · нет подключения` |

The four legacy `NotifyIcon1-4.ico` files are deleted.

**Menu**, six entries, Russian sentence case:

```
┌──────────────────────────────────────┐
│  Нидерланды · Amsterdam    (disabled)│  ← the live identity line; «Не подключено» when off
├──────────────────────────────────────┤
│  Отключить                            │  ← label follows core state: Подключить / Отключить
│  Перезапустить                        │
├──────────────────────────────────────┤
│  Показать                             │
│  Выход                                │
└──────────────────────────────────────┘
```

- The identity line is a disabled `NativeMenuItem`; it is the only place the tray states *which*
  server is up, and it means a user does not have to open the window to answer that.
- Labels come from `L.Shell.cs` (`Tray_Restart`, `Tray_Connect`, `Tray_Disconnect`, `Tray_Show`,
  `Tray_Exit`) and are re-applied through `App.axaml.cs`'s `LocalizeTray()` hook on
  `L.Instance.LanguageChanged`, which already exists.
- **Left click shows or hides the window. It never toggles the tunnel.** Toggling a VPN by
  single-clicking a task-bar icon is a destructive misfire waiting to happen; the explicit menu item
  and `Ctrl+Enter` cover the intent.
- Double click shows the window and focuses Главная.
- `Ctrl+W` and the window close button hide to tray. **Quit is the tray menu only**, and on quit the
  core is stopped and the user is told nothing, because the app is gone.

### 3.12 Shell acceptance

- [ ] Four destinations, in the Android order, with the Android labels
- [ ] Rail at >= 760, compact bar below, one `Button.NavItem` class, one travelling indicator
- [ ] Zero gradients, zero shadows, zero `OpacityMask` fades in the shell
- [ ] Caption buttons 46x32, all three named, close hover `#8E1D23`
- [ ] Escape, `Alt+Left`, mouse button 4 and the toolbar button all pop the stack
- [ ] Sub-page stacks are per destination and survive a destination switch
- [ ] The status strip renders an info, a warning and an error, with the error persisting
- [ ] `Ctrl+1..4`, `Ctrl+Tab`, `Ctrl+,`, `Ctrl+F`, `Ctrl+Enter` all work with no mouse
- [ ] Usable at 900x600 wide, 380x620 compact, and at UI zoom 200 percent
- [ ] Dark, light and mono all verified; the accent is inside the theme dictionaries
- [ ] Reduced motion: every shell animation snaps

---

## 4. The three product surfaces the desktop must own

Restated here only where the desktop expression differs from the shared spec.

### 4.1 Подписка as a live object

The six states (`30-reference-analysis.md` 6.1) are computed **once**, in
`ServiceLib`, and rendered on four desktop surfaces with one vocabulary:

| State | Condition | Chip | Copy | Primary action |
|---|---|---|---|---|
| `нет подписки` | no managed subscription | none | `Подписки пока нет` | `Купить` |
| `триал` | `Subscription.IsTrial` **from the backend**, never inferred from tariff name or squad | `Border.Chip.neutral` | `Пробный период` | `Купить` |
| `активна` | expiry > 7 days and quota < 90 percent | `.green` + `Geo.State.Check` | `Активна до 12.08.2026` | none |
| `истекает` | expiry <= 3 days **or** quota >= 90 percent | `.amber` + `Geo.State.Warning` | `Истекает через 3 дня` | `Продлить` |
| `истекла` | expiry in the past | `.red` + `Geo.State.Error` | `Подписка истекла` | `Продлить` |
| `лимит устройств` | devices used == allowed | `.amber` + `Geo.State.Warning` | `Достигнут лимит устройств` | `Устройства` |

The four surfaces: the subscription row on **Главная**, the subscription card on **Аккаунт**, the
group header on **Серверы**, and the tray tooltip. A fifth, the gate line under the connect control,
appears **only when the state is not `активна`**.

Desktop specifics: the chip carries a word **and** a colour **and** a glyph; expiry renders
`до 12.08.2026` beyond 7 days and `через 3 дня` within; traffic renders `12,4 ГБ из 50 ГБ` in
`Subtitle` **beside** the meter, never printed on top of a moving fill (the current
`SubscriptionMetaView` prints an 11px label on the fill at **2.9:1**, changing contrast mid-word as
the bar advances); unlimited renders `12,4 ГБ · без ограничений`; and `subscription-userinfo: 0`
hides the block entirely rather than showing `0 Б / 0 Б`.

### 4.2 Отказ as a designed screen

Three parts, all desktop-visible:

1. **The closed taxonomy** of eight cases with `{причина, основное действие, второе действие,
   retryable}`. No raw core strings, no exit codes, ever, on any surface. Copy in
   `30-reference-analysis.md` 6.2a and `00-rules.md` 9.4.
2. **One silent retry** before anything is shown.
3. **The status strip** (3.8) as the transient surface and **Журнал** (7.20) as the durable one.

### 4.3 Ничего не происходит незаметно

**(a) The affordance grammar is law on every list in the product**, not only Settings. It is the
single best design decision in the codebase (`SettingsView.axaml.cs:14-22`) and this plan promotes
it from one screen to the row component itself.

| Trailing affordance | Promise | Desktop glyph |
|---|---|---|
| chevron 20 | pushes a sub-page | `Geo.Action.ChevronRight` |
| chevron that rotates 0 → 90 over `Dur.State` 220ms | expands inline, right here | same glyph |
| unfold 20 | cycles the value in place, no page, no dialog | `Geo.Action.Unfold` |
| segment | 2-state change, applied immediately | `ToggleButton.Segment` |
| switch | boolean | `ToggleSwitch.iOS` |
| value text only, no glyph | read-only fact | `TextBlock.Subtitle` |

Enforcement: exactly **one** trailing element per row; the current value always precedes it; the
whole row is the target; and a row whose affordance does not match its behaviour is a defect, not a
nit.

**(b) Provider power is visible and revocable.** Настройки › Провайдеры (7.17) lists every directive
that changed device behaviour, in Russian, with the subtitle `Задано провайдером` and the value it
set, and one `Button.Text` «Вернуть мои настройки». `hide-url` is refused as specified: we may honour
"keep this out of a shared backup", we never remove the user's ability to read their own URL. Colour
directives are parsed and the value discarded. Icon choices are an enumeration we render.

---
