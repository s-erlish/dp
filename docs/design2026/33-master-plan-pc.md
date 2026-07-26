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

## 5. Destination 1 - Главная

**Files today:** `Views/HomeView.axaml` (74 ln), `Views/CompactHomeView.axaml` (94 ln),
`Views/ConnectHeroView.axaml` (839 ln) + `.axaml.cs` (1 156 ln), `Views/HomeAccountChip.axaml`
(131 ln), `Views/ServerListView.axaml` (313 ln), `Views/SubscriptionMetaView.axaml` (335 ln).
**Files after:** `Views/Home/HomePage.axaml` (one file, both modes),
`Views/Home/ConnectControl.axaml`, `Views/Home/StatsRow.axaml`.
**Verdict: REBUILD `HomeView` and `CompactHomeView` into one `HomePage`. RESTYLE `ConnectHeroView`
into `ConnectControl` (delete five of its nine layers). MOVE `ServerListView` and
`SubscriptionMetaView` to the Серверы destination. FOLD `HomeAccountChip` into the account row.**

**Android counterpart:** `activity_main.xml` Home tab. Same concept: one connect object, one server
identity, one subscription state, three live numbers, nothing else. Same states, same copy, same
motion tempo. **Deliberate difference:** the desktop splits into two panes above 980px of content
width, because a 1600px window with a single 720px column and a 176px disc leaves the right half
empty; Android has no such width to answer for. **Deliberate difference:** on Android the account
chip is a row at the top of Home; on desktop it is a row in the ledger, because the rail already
carries the «Аккаунт» destination and a second permanent account affordance at the top of the page
would be two entrances to one room.

### 5.1 What Главная is for

Four seconds. Is it on, what am I connected to, is my subscription alive, and the single control
that changes the first of those. It is **not** where servers are browsed (that is Серверы) and it is
not a dashboard.

`03-direction.md` 3.2: the connect screen is the *least* branded screen in the app, not the most.
The identity of this product lives in its numbers and its ledger, not in the disc.

### 5.2 Layout

**Compact and wide below 980px of content width - one centred column, `MaxWidth` 560:**

```
ScrollViewer  (one scroller, Padding 0,0,0,16)
└─ StackPanel  MaxWidth 560  HorizontalAlignment=Center  Margin 16,0
   ├─ 32                                              (space above the hero)
   ├─ ConnectControl                     176px disc inside a 200px frame, centred
   ├─ 16
   ├─ TextBlock #statusLine              Title 16/700 + status dot + word
   ├─ 4
   ├─ TextBlock #gateLine                Subtitle 13, only when the subscription is not «активна»
   ├─ 24
   ├─ StatsRow                           only when connected; 3 columns, 44px tall
   ├─ 32
   ├─ TextBlock.SectionHeader «Подключение»
   ├─ 8
   ├─ Border.Card  Padding 0             the ledger
   │   ├─ Border.Row  #rowServer         flag tile · name · protocol chip · ping · chevron
   │   ├─ hairline at 68
   │   ├─ Border.Row  #rowSubscription   tile · «Подписка» · state chip + expiry · chevron
   │   ├─ hairline at 68
   │   └─ Border.Row  #rowAccount        avatar tile · @handle · «Управление аккаунтом» · chevron
   └─ 16
```

**Wide at content width >= 980 - two panes, both centred as one group, total `MaxWidth` 988:**

```
Grid  ColumnDefinitions="420,48,*"   MaxWidth 988   HorizontalAlignment=Center
├─ [0] StackPanel  VerticalAlignment=Center        ← the connect pane
│      ConnectControl · 16 · statusLine · 4 · gateLine · 24 · StatsRow
└─ [2] StackPanel  VerticalAlignment=Center  MaxWidth 520    ← the ledger pane
       SectionHeader «Подключение» · 8 · Border.Card with the three rows
```

No divider between the panes. 48px of space is the separator; a 1px line there would be the third
background decision on a screen that needs one (today `HomeView` has a full-bleed gradient, a
per-column gradient and a 1px divider).

**Why the split is at 980 and not at the shell breakpoint:** at 760 window width the content area is
`760 - 76 rail - 1 hairline - 32 gutter = 651px`. A 420px pane plus a 520px pane needs 988. Below
that the single column is the correct layout, and it is the same column the compact mode shows, so
there is one Home to design and one to test rather than two files that have already drifted (today
`HomeView` and `CompactHomeView` inline the same 25-line TUN banner character for character, with
`Padding="14,12"` and `Spacing="10"` in both copies).

### 5.3 The connect control

`Views/Home/ConnectControl.axaml`. The current `ConnectHeroView` stacks **nine** layers to express
one boolean plus two transitions: `#AmbientSonar` → `#AmbientRing` → `#GlowHalo` → `#RingOuter` /
`#RingHoverGlow` / `#RingInner` → `#SonarPulse` / `#SonarPulseEcho` → `#ConnectingArc` →
`#ConnectDisc` → two shields. Our own inventory calls it **two competing idle animations on the same
object**. Five layers are deleted.

**5.3.1 The object, after.**

```
Panel #connectFrame  200x200  ClipToBounds=False
├─ Ellipse #ring       200x200  StrokeThickness 1   Stroke Brush.Outline
├─ Arc     #arc        200x200  StrokeThickness 3   Stroke Brush.Accent   (connecting only)
├─ Ellipse #sonar      200x200  StrokeThickness 2   Stroke Brush.Accent   (the hero moment only)
└─ Border  #disc       176x176  CornerRadius 88     Background Brush.SurfaceHigh
     └─ Viewbox 80x80
        ├─ PathIcon #shieldOutline  Geo.Server.ShieldOutline
        └─ PathIcon #shieldFilled   Geo.Server.ShieldFilled   (Opacity 0 at rest)
```

Deleted: `#GlowHalo` and `Brush.ConnectGlow`, `#AmbientSonar`, `#AmbientRing`, `#RingHoverGlow`,
`#SonarPulseEcho`, `Brush.Ring.Outer`, `Brush.Ring.Inner`. The frame drops from 230 to 200 because
nothing needs to bloom outside the ring any more; 200 and 176 are both off the spacing scale and both
are legitimate component sizes carried as tokens (`Size.ConnectFrame` 200, `Size.ConnectDisc` 176,
`Size.ShieldGlyph` 80).

**5.3.2 The four states.**

| State | Disc | Ring | Shield | Status line |
|---|---|---|---|---|
| Отключено | `Brush.SurfaceHigh` `#1A1D21` | 1px `Brush.Outline` `#2A2E36` | outline, `Brush.OnSurfaceVariant` | 8px `Brush.OnSurfaceVariant` dot + «Не подключено» |
| Подключение | `Brush.SurfaceHigh` | the arc replaces the ring: 3px `Brush.Accent`, 90 degrees of sweep, rotating | outline, `Brush.OnSurfaceVariant` | 8px `Brush.Accent` dot + «Подключение…» |
| Подключено | `Brush.SurfaceHigh` | 1px `Brush.Outline`, back to rest | **filled, `Brush.Accent`** | 8px `Brush.Green` dot + «Подключено» |
| Ошибка | `Brush.SurfaceHigh` | 1px `Brush.Red` | outline, `Brush.RedText` | 8px `Brush.Red` dot + the taxonomy's cause line, plus «Нажмите, чтобы повторить» |

The idle state is **recessed, not tinted**: it is darker than the page because `Brush.SurfaceHigh` on
`Brush.Bg` is a step up in a dark theme and reads as an inset control, and it carries **no blue at
all**. `03-direction.md` 5.5: if the user's connection is off, nothing on the screen is blue. That is
also what leaves contrast available to spend when it comes on.

Colour is never the only signal: every state pairs the dot with the word.

**5.3.3 Interaction.**

| Input | Behaviour |
|---|---|
| Click on the disc | Toggle. Disabled while `Подключение` unless the press is a second press, which cancels |
| `Ctrl+Enter` | Same |
| Hover | `Brush.SurfaceHighest` on the disc, 150ms `Ease.Standard`. **No glow ring** (`#RingHoverGlow` deleted) |
| Press | `scale(0.94)` at `Dur.PressIn` 90ms, out at `Dur.PressOut` 160ms. The documented exception to 0.97, because 0.97 is imperceptible on a 176px object |
| Focus | 2px `Brush.Accent` ring at 2px offset, radius 88, drawn outside the ring |
| Disabled | No subscription, or no server selected: `Opacity` 0.38, `IsEnabled=False`, cursor default, and the gate line explains why |
| Tooltip | «Подключить (Ctrl+Enter)» / «Отключить (Ctrl+Enter)» |
| Accessible name | «Кнопка подключения. Состояние: не подключено» |

**5.3.4 The connecting arc.** 3px stroke on the 200px circle, 90 degrees of sweep, one rotation per
**1.2 s**, linear (a genuine indeterminate progress indicator is the one place linear is correct),
preceded by a one-shot 200ms `Ease.OutQuint` wind-up from 0 to 90 degrees of sweep so it starts
decisively rather than snapping into a spin. It runs **only while the core is actually negotiating**
and stops the frame the state resolves. An indeterminate indicator that runs while nothing is
happening is a lie about the system. Under `.lite` the arc is a static 90-degree accent segment.

**5.3.5 What was deleted and why.**

| Deleted | Reason |
|---|---|
| `#GlowHalo` + `Brush.ConnectGlow` radial | Absolute ban on decorative glow. It carries no information and it needs a redrawn variant per theme; in light it made the idle disc almost invisible |
| `#AmbientSonar`, `#AmbientRing` (850ms breathing loops) | Idle ambience. `03-direction.md` 8.5: no looping animation exists in this product except a genuine indeterminate indicator |
| `#SonarPulseEcho` | A second ring. The hero moment emits exactly one |
| `#RingHoverGlow` | Hover is a surface step, not a bloom |
| `Brush.Ring.Outer` / `.Inner` (alpha rings) | Two extra strokes saying what one 1px ring says |
| `#CornerAddButton` | The app's primary "add subscription" action was parked in the hero's corner, in the wide layout only. It moves to the Серверы destination, where adding a server or a provider belongs, and to the onboarding page |
| `Text="↑"` / `Text="↓"` | Typographic characters standing in for icons. `Geo.Action.ArrowUp/Down` at 16px |

**5.3.6 The hero moment.** The one 600ms event in the entire product, at the instant the tunnel
confirms:

1. `#shieldOutline` `Opacity 1 → 0` and `#shieldFilled` `Opacity 0 → 1`, both **220ms**
   `Ease.Standard`, starting at t=0.
2. `#sonar` emits **one** ring from the disc edge: `scale 1.0 → 1.35` with `Opacity 0.6 → 0`, over
   **600ms** `Ease.OutQuint`, starting at t=0. It never loops. There is never a second ring.
3. The status dot crossfades to `Brush.Green` over 220ms; the word «Подключено» **changes with no
   animation at all**.
4. Nothing else moves. The page does not flash, the background does not tint, the rail does not
   react, the stats row fades in over `Dur.State` 220ms as ordinary content arriving.

Disconnect is the same in reverse at **75 percent tempo** (450ms) and emits **no** ring.

Under `.lite`: the shield swaps instantly, no sonar, the dot and the word change instantly.

### 5.4 The status line and the gate line

```
StackPanel Orientation=Horizontal Spacing=8 HorizontalAlignment=Center
├─ Ellipse 8x8   (the state dot, 5.3.2)
└─ TextBlock.Title   «Подключено»
```

Below it, at 4px, `#gateLine`, `TextBlock.Subtitle` centred, `MaxWidth` 420, **present only when the
subscription state is not `активна`**:

| Subscription state | Gate line |
|---|---|
| `активна` | (nothing) |
| `истекает` | `Подписка истекает через 3 дня` |
| `истекла` | `Подписка истекла. Продлите её, чтобы подключаться.` |
| `лимит устройств` | `Достигнут лимит устройств. Отвяжите одно в разделе «Устройства».` |
| `нет подписки` | `Купите тариф, чтобы подключаться к серверам Departament.` |
| `триал` | `Пробный период. Осталось 5 дней.` |

A CTA that is always present is furniture; a gate line that appears only when something is wrong is
information.

### 5.5 The stats row

`Views/Home/StatsRow.axaml`. **Visible only when connected.** Today three live counters, all reading
zero, sit at the top of the page before the user has done anything, which is the hero-metric template
inverted.

```
Grid ColumnDefinitions="*,Auto,*"  Height 44  MaxWidth 420
├─ [0] StackPanel Orientation=Horizontal Spacing 4  HorizontalAlignment=Right
│        PathIcon Geo.Action.ArrowUp 16 Brush.OnSurfaceVariant
│        TextBlock.Numeric «3,1»   +  TextBlock.Caption «Мбит/с»
├─ [1] TextBlock.Numeric  «02:14:07»  16/500  Brush.OnSurface   Margin 24,0
└─ [2] StackPanel Orientation=Horizontal Spacing 4  HorizontalAlignment=Left
         PathIcon Geo.Action.ArrowDown 16
         TextBlock.Numeric «24,8»  +  TextBlock.Caption «Мбит/с»
```

- **Uptime is the middle stat and it is the largest of the three.** Speeds fluctuate and mean little;
  a duration is a trust signal. Two hours fourteen minutes of unbroken tunnel is the single most
  reassuring number a VPN can show, and it costs one ticker. This is Incy's best Home idea
  (`30-reference-analysis.md` 2.1.4) and it is free.
- Every figure is `TextBlock.Numeric` with `tnum,lnum` so nothing jitters. Each speed column reserves
  `5 x 0.62 x 13 = 41px` for its digits so `9,9` to `12,4` does not shift the row. Uptime reserves
  `8 x 0.62 x 16 = 80px`.
- The 42px invisible spacer that fakes optical centring today is deleted; a three-column `Grid` with
  a centred middle column does it honestly.
- Units are in the UI face at `Caption` 12; figures are in the figure face. A sentence never ripples
  between two faces, but these are values in their own slots, which is exactly where the split
  belongs.

### 5.6 The ledger rows

**`#rowServer`** - the unified server icon, and the only server affordance on Главная.

```
Border.Row  #rowServer
├─ ServerIcon 40   flag tile 28 circular, globe fallback
├─ 12
├─ text     Title    «Нидерланды, Amsterdam»          (remark, flag emoji stripped)
│           Subtitle «VLESS · Reality · TCP»          (protocol chip + transport)
├─ value    Numeric  «48 мс»   right-aligned, 48px reserved
└─ chevron 20   → route `servers`
```

Click, `Enter` or `Space` opens the Серверы destination with that server scrolled into view and
selected. Right-click opens the same `MenuFlyout` the server list uses (6.5). When no server is
selected: title «Сервер не выбран», subtitle «Выберите сервер, чтобы подключиться», tile is the globe
glyph, no ping, and the row is the primary affordance on the page.

**`#rowSubscription`** - the subscription object's Home rendering (4.1).

```
Border.Row  #rowSubscription
├─ Border.Tile   Geo.Set.Shield 22    neutral, or .amber / .red in the warning states
├─ 12
├─ text     Title    «Подписка»
│           Subtitle «12,4 ГБ из 50 ГБ»               traffic, beside a 4px meter, never on it
├─ trailing Border.Chip  «Активна до 12.08.2026»      state word + colour + glyph
└─ chevron 20   → route `account/subscription/{id}`
```

With two or more subscriptions the row's title becomes «Подписки», the subtitle becomes
«3 подписки · ближайшая истекает 12.08.2026», and the chip carries the **worst** state of the set.

**`#rowAccount`** - replaces `HomeAccountChip.axaml` as a standalone component.

```
Border.Row  #rowAccount
├─ Border.Avatar 40   monogram Title 16/700 Brush.OnAccent on Brush.Tile.Blue, or the photo
├─ 12
├─ text     Title    «@username»                      user content, ellipsises at the end
│           Subtitle «Управление аккаунтом»
└─ chevron 20   → destination `account`
```

Signed out, the row becomes: neutral tile with `Geo.Auth.Telegram`, title «Войти в departament»,
subtitle «Через Telegram, быстро и без пароля», chevron → route `auth/login`. It is never hidden,
because a signed-out user needs the entrance more than a signed-in one needs the exit.

While the profile resolves, the row shows the skeleton variant: a 40px `Border.Skeleton` circle, a
120x14 bar, a 90x12 bar, all pulsing. `HomeAccountChip` already does this correctly and the behaviour
is carried over verbatim.

### 5.7 The TUN banner

Docked above the hero, both modes, one definition (today it is inlined twice, character for
character). It is an `info` variant of the status strip component, not a bespoke banner:

```
Border.StatusStrip.warning   MinHeight 48   Margin 0,0,0,16
├─ PathIcon 20 Geo.State.Warning  Brush.Icon.Yellow
├─ TextBlock.Body  «Режим «весь трафик» недоступен без прав администратора»
└─ Button.Text     «Перезапустить с правами»
```

Visible only when TUN is selected and elevation is missing. On Linux this is the first thing a user
meets, so its action must work: it triggers the elevation prompt, which on Linux is the sudo dialog
(9.3).

### 5.8 Every state of Главная

| State | Rendering |
|---|---|
| **Default, disconnected** | Neutral disc, «Не подключено», no stats row, three ledger rows |
| **Default, connected** | Filled shield, green dot, «Подключено», stats row visible with live values |
| **Connecting** | Arc, «Подключение…», stats row hidden, disc still clickable to cancel |
| **First run** | Reached only past onboarding, so: disc **disabled**, gate line «Купите тариф, чтобы подключаться к серверам Departament.», `#rowServer` in its "no server" form, `#rowSubscription` in its `нет подписки` form with the chip absent and one `Button.Primary` «Купить подписку» as the row's trailing element instead of a chevron |
| **Loading** | The three ledger rows render as skeletons; the disc is disabled with no gate line. Appears only after 300ms |
| **Empty (no servers, signed in)** | Disc disabled, `#rowServer` reads «Серверов пока нет» / «Добавьте провайдера, чтобы появились серверы», chevron → `servers` |
| **Error (tunnel)** | Red ring, `Brush.RedText` shield outline, status «Не удалось подключиться», gate line = the taxonomy's cause, «Нажмите, чтобы повторить» under the disc, and the status strip carries the recovery action |
| **Offline** | Persistent info strip `Нет сети. Показаны последние данные.` with «Повторить»; the ledger keeps its last values; the disc is enabled (connecting is the way out of offline) |
| **Partial** | The subscription row failed but servers loaded: the subscription row shows «Не удалось загрузить» with a `Button.Text` «Повторить» as its trailing element; everything else renders |
| **Long content** | A 70-character server remark wraps to two lines and the row grows; a 40-character Telegram handle ellipsises at the end |
| **Short content** | One server, no subscription: the layout still has three rows and does not look broken |
| **Gated** | Expired subscription: disc disabled, gate line, and `#rowSubscription`'s trailing element becomes `Button.Primary` «Продлить» |
| **Success** | The hero moment (5.3.6), then stillness |

### 5.9 Keyboard path

`Tab` order: rail, then `#connectFrame`, then `#rowServer`, `#rowSubscription`, `#rowAccount`, then
the status strip's action if present. `Ctrl+Enter` toggles from anywhere. `Ctrl+2` leaves for
Серверы. Every task on this page is completable with no mouse.

### 5.10 Главная acceptance

- [ ] Zero gradients, zero glows, zero looping idle animation
- [ ] Exactly one accent element at rest (the rail); two when connected or connecting
- [ ] The disc is neutral when disconnected
- [ ] Stats appear only when connected; uptime is the middle and largest figure; nothing jitters
- [ ] One card, three rows, one section header, and gaps of 32 / 24 / 16 / 8 / 4 present
- [ ] Two panes above 980, one column below, no divider between panes
- [ ] All thirteen states in 5.8 implemented and looked at
- [ ] The server row and the subscription row use the same vocabulary as Серверы and Аккаунт

---

## 6. Destination 2 - Серверы

**New destination.** Files today: `Views/ServerListView.axaml` (313 ln) + `.axaml.cs` (939 ln),
`Views/SubscriptionMetaView.axaml` (335 ln) + `.axaml.cs` (687 ln), plus three dead files that
contain features: `Views/ServersView.axaml` (12 ln, orphan wrapper),
`Views/CompactServersView.axaml` (116 ln, **contains the app's only search field**, bound to
`Profiles.ServerFilter`, instantiated by nothing), `Views/ProfilesView.axaml` (322 ln, registered in
`SimpleViewLocator:29`, never shown).
**Files after:** `Views/Servers/ServersPage.axaml`, `ProviderPane.axaml`, `ServerList.axaml`,
`ServerRow.axaml`, `ProviderGroupHeader.axaml`, `ServerEditorPage.axaml`,
`ProviderEditorPage.axaml`.
**Verdict: REBUILD as a destination. HARVEST the search from `CompactServersView` and delete it.
DELETE `ServersView` and `ProfilesView`.**

**Android counterpart:** the Серверы tab (`layout_servers_header.xml`, `item_recycler_main.xml`,
`item_section_header.xml`, `layout_servers_empty.xml`). Same concept: one list, grouped under the
provider that produced it, one search, one sort, per-item actions one deliberate gesture away.
**Deliberate differences:** desktop adds a provider pane at width, multi-select with `Ctrl` and
`Shift`, right-click, keyboard navigation of the list, and a kebab that appears on hover; Android has
none of those and uses a bottom sheet where desktop uses a flyout. The **row** is identical on both.

### 6.1 Why it is a destination

Three reasons, all of them structural rather than aesthetic:

1. **Parity.** `00-rules.md` 13 fixes the destination set as identical. Android has four. Desktop
   has three and hides servers in a column of Home.
2. **Search has no home otherwise.** With 80 to 150 servers per subscription, a list with no search
   is a functional hole, not a polish item. The only search field in the codebase is in a dead file.
3. **The duplicate-list failure.** Putting the full list on Home is Incy's own IA mistake
   (`30-reference-analysis.md` 2.2.8) and the desktop inherited it: on the default 372x630 window a
   440px-minimum hero means the list starts below the fold and the hero cannot be skipped.

### 6.2 Layout

**Wide, content width >= 900 - two panes:**

```
Grid  ColumnDefinitions="300,1,*"
├─ [0] ProviderPane        Width Size.PanePrimary 300, own ScrollViewer
├─ [1] Border              Width 1  Brush.OutlineVariant
└─ [2] DockPanel           the list pane
      ├─ [Top] the list toolbar     56px: search field + sort + actions
      └─ ServerList                 own ScrollViewer, virtualised
```

Two scrollers is the single documented exception to "one scroll region per view" and it is safe
because they never overlap and never nest.

**Compact, and wide below 900 - one column, `MaxWidth` `Size.Content` 720:**

```
DockPanel
├─ [Top] the list toolbar   56px
└─ ScrollViewer
   └─ ItemsControl over provider groups
      ├─ ProviderGroupHeader   sticky, 56px
      ├─ ServerRow x N
      ├─ ProviderGroupHeader   sticky
      └─ ...
```

Group headers are **sticky** here because the list is long enough to lose context
(`00-rules.md` 4.6). Settings groups are not sticky, because they are not.

### 6.3 The list toolbar

```
Grid  Height 56  Margin 16,0  ColumnDefinitions="*,Auto,Auto,Auto"  ColumnSpacing 8
├─ [0] TextBox.Field.search   Height 40   MaxWidth 360   HorizontalAlignment=Left
│        watermark «Поиск серверов…»   leading Geo.Action.Search 20
│        trailing clear button when non-empty     Ctrl+F focuses, Esc clears
├─ [1] Button.IconButton40  Geo.Action.Sort     ToolTip «Сортировка»       → flyout
├─ [2] Button.IconButton40  Geo.Action.Speed    ToolTip «Проверить задержку (Ctrl+P)»
└─ [3] Button.IconButton40  Geo.Action.Add      ToolTip «Добавить (Ctrl+N)» → MenuFlyout
```

- **Search filters in place; it never navigates.** It matches the remark, the address, the protocol
  and the provider name, case-insensitive, and it is debounced at 120ms.
- **Sort flyout**, three options, radio semantics with a filled check on the current one:
  «По умолчанию» · «По задержке» · «По названию». Persisted per provider. This mirrors the
  `sort-order: none | ping | name` header both reference protocols carry.
- **Add flyout**: «Добавить провайдера» · «Добавить по QR-коду (Ctrl+S)» · «Добавить из буфера
  обмена (Ctrl+V)» · hairline · «Создать сервер вручную» → route `servers/editor`.
- Four trailing controls is the ceiling. A fifth goes into a kebab. (Android's header today carries
  four 36px icon buttons crammed against the right edge, all under the 48dp floor; desktop's are 40px
  and meet the desktop floor.)

### 6.4 The provider pane (wide only)

```
Border  Width 300  Background Brush.Bg
└─ DockPanel
   ├─ [Top]  TextBlock.SectionHeader «Провайдеры»   Margin 16,16,16,8
   ├─ [Fill] ScrollViewer
   │   └─ StackPanel
   │      ├─ Border.Row.selectable  «Все серверы»       value «147»
   │      ├─ 8
   │      ├─ Border.Row.selectable  per provider:
   │      │     Border.Tile   Geo.Set.Provider 22   neutral, or .amber/.red on subscription state
   │      │     Title      «Departament»            (profile-title, capped at 25 chars in the parser)
   │      │     Subtitle   «84 сервера · до 12.08.2026»
   │      │     trailing   Border.Chip when the state is not «активна»
   │      │     kebab 40   on hover / focus  → provider MenuFlyout
   │      └─ ...
   └─ [Bottom] Button.Text  «Добавить провайдера»   Margin 16,8,16,16
```

Selection is `Brush.SelectedFill` plus a 2px `Brush.Accent` left-edge **indicator is banned**
(side-stripe). Instead: `Brush.SelectedFill` fill plus the title stepping to weight 700. Two channels.

**Provider `MenuFlyout`:** «Обновить» · «Переименовать» · «Закрепить» / «Открепить» · «Открыть
поддержку» · «Скопировать ссылку» · hairline · «Удалить провайдер» in `Brush.RedText`.

**Pin** sorts the provider to the top **and** makes it the pane's default selection on launch. One
gesture that changes both order and where the app opens (Happ's best small idea,
`30-reference-analysis.md` 1.1.5).

### 6.5 The server row

`Views/Servers/ServerRow.axaml`. One definition, used in both layout modes, and its anatomy is
byte-for-byte the concept Android's `item_recycler_main.xml` renders.

```
Border.ServerRow   MinHeight 56   Padding 16,8   CornerRadius 12
├─ ServerIcon 40                 flag tile 28 circular · globe fallback
├─ 12
├─ text column (star)
│    Title     «Нидерланды, Amsterdam»        16/700, max 2 lines, wraps
│    Subtitle  Border.ProtocolChip «VLESS» + «Reality · TCP»   13/400 muted
├─ 12
├─ ping        TextBlock.Numeric «48 мс»      13/500, right-aligned, 56px reserved
│              or an 18px arc spinner while probing
│              or «нет ответа» in Brush.OnSurfaceVariant on failure
└─ kebab 40    Button.IconButton40.Row  Geo.Action.More   Opacity 0 → 1 on :pointerover or :focus
```

A 1px `Brush.OutlineVariant` hairline separates rows, inset to **68**, hidden on the selected row and
on the row above it. The permanent 1.5px transparent border each row carries today is deleted; it
existed so selection could change a border colour without a layout shift, and the same result comes
from a fill plus a glyph.

| State | Rendering |
|---|---|
| Rest | transparent |
| Hover | `Brush.Hover`, 150ms |
| Pressed | `scale(0.97)` |
| Selected (this is the active server) | `Brush.SelectedFill` `#4C8DFF` at 0.12 **and** a filled 20px `Geo.State.Check` in `Brush.Accent` replacing the kebab slot until hover |
| Multi-selected (`Ctrl+click`) | `Brush.SurfaceHighest` fill plus a 20px checkbox glyph at the leading edge, replacing the flag tile |
| Focus | inner 2px `Brush.Accent` ring, r12 |
| Probing | ping slot shows an 18px indeterminate arc |
| Unreachable | ping «нет ответа» in `Brush.OnSurfaceVariant`; the row is not dimmed, because it may still connect |

**Ping is a value and a word, never a bare colour dot.** The ping figure never uses colour alone: a
good latency is plain `Brush.OnSurface`, a bad one is `Brush.OnSurfaceVariant` with the word.

**Selection uses two channels and no side stripe.** The zero-size indicator `View` that exists only
so an adapter can still call `setBackgroundColor` is an Android artefact; desktop must not grow one.

### 6.6 Actions

**Discovery is the problem today**: the context menu is the only route to seven actions and nothing
on the row says so. Three entrances, all opening the same `MenuFlyout`:

- Right-click anywhere on the row.
- The kebab, which fades in at `Opacity 0 → 1` over 150ms on hover or keyboard focus.
- The `Menu` key or `Shift+F10` when the row has focus.

```
MenuFlyout, 40px rows, 20px leading glyph, 12 gap
├─ Подключиться                Geo.Action.Connect        (Enter)
├─ Сделать основным            Geo.State.Check
├─ Проверить задержку          Geo.Action.Speed          (Ctrl+P)
├─ hairline
├─ Изменить                    Geo.Action.Edit           → route servers/editor/{id}
├─ Дублировать                 Geo.Action.Copy
├─ Поделиться · QR-код         Geo.Action.Qr             → QR dialog (9.2)
├─ Поделиться · ссылка         Geo.Action.Link           → copies, status strip confirms
├─ hairline
└─ Удалить                     Geo.Action.Delete   Brush.RedText   (Delete)
```

**Delete is undo, not confirm** (`00-rules.md` 7.5): the row disappears immediately and the status
strip shows `Сервер удалён` with `Отменить` for 5 seconds. A dialog is reserved for genuinely
irreversible costly actions, and deleting one server out of 147 is not one.

**Multi-select.** `Ctrl+click` toggles, `Shift+click` extends, `Ctrl+A` selects all in the current
group, `Esc` clears. With two or more selected, the list toolbar's right side is replaced by a
selection bar: `«Выбрано 12»` plus `Button.Text` «Проверить задержку», `Button.Text` «Дублировать»,
`Button.Text` «Удалить» in `Brush.RedText`, and a 40px close button. Deleting many is still undo, one
strip for the whole batch.

### 6.7 The provider group header (compact, and «Все серверы» in wide)

This is `SubscriptionMetaView` rebuilt. Today it is a card that carries **four** trailing icon
buttons and locally shrinks the global `IconButton40` to **34x34 with 20px glyphs** to fit them into
a 372px window. That is a structural problem solved by shrinking targets; the structural fix is an
overflow.

```
Border  Height 56  Background Brush.Bg   (sticky)
└─ Grid ColumnDefinitions="Auto,*,Auto,Auto"  Margin 16,0  ColumnSpacing 12
   ├─ [0] Button.IconButton40  Geo.Action.ChevronDown   rotates 0 → -90 when collapsed
   ├─ [1] StackPanel
   │        TextBlock.Title    «Departament»
   │        TextBlock.Subtitle «84 сервера · 12,4 ГБ из 50 ГБ · до 12.08.2026»
   ├─ [2] Border.Chip           only when the state is not «активна»
   └─ [3] Button.IconButton40   Geo.Action.More  → the provider MenuFlyout (6.4)
```

**Four trailing buttons become one kebab plus a collapse chevron.** Ping, refresh, pin and delete all
move into the flyout. Both remaining buttons are 40x40 at full token size.

The traffic figure lives in the subtitle as text; the 160px `Border.TrafficPill` with a label printed
on a moving `LinearGradientBrush` fill is deleted. If a meter is wanted, it is a 4px `Border.Meter`
below the subtitle with the label beside it, and the fill is a **solid** accent.

**Operator message.** One component, one lifetime (`30-reference-analysis.md` 6.3b rule 5).
`announce`, `announce-url` and the `sub-info-*` family all resolve into a single dismissible row
directly under the group header:

```
Border  Background Brush.SurfaceHighest  r12  Padding 12  Margin 16,0,16,8
├─ PathIcon 20   from the enumerated icon key, our glyph, our colour
├─ TextBlock.Body  <= 200 chars, <= 5 lines, enforced in the parser not the view
├─ Button.Text     the operator's labelled action, if any
└─ Button.IconButton40.Row  Geo.Action.Close
```

Dismissal is keyed on a hash of the text, so a **new** message re-appears while the same one stays
gone. The operator supplies text, a link, an enumerated icon key and one of three severities. Colour
directives are parsed and **the value discarded**.

### 6.8 Every state of Серверы

| State | Rendering |
|---|---|
| **Default** | Provider pane with N providers, list with the current provider's servers, one selected |
| **First run** | Reached only past onboarding; shows the empty state below |
| **Loading** | Eight `Border.Skeleton` rows in the exact silhouette of a real row (40 circle, 180x16 bar, 120x13 bar, 40x13 bar), pulsing. Provider pane shows three skeleton rows. After 300ms only |
| **Empty, no providers** | `Border.EmptyIcon` 64 with `Geo.Set.Provider`, Title «Нет серверов», Body «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.», `Button.Primary` «Добавить провайдера», `Button.Text` «Добавить по QR-коду» |
| **Empty, provider has no servers** | Same tile, Title «В этом провайдере нет серверов», Body «Обновите подписку или проверьте ссылку провайдера.», `Button.Primary` «Обновить» |
| **No search results** | Title «Ничего не найдено», Body «Попробуйте другой запрос.», `Button.Text` «Сбросить поиск». The search field keeps its text and its focus |
| **Error** | Title «Не удалось обновить подписку», Body «Проверьте ссылку провайдера и повторите.», `Button.Tonal` «Повторить». The last known list stays visible below, marked «Данные могли устареть» |
| **Offline** | Last known list, the persistent offline strip, refresh and ping disabled, connect still enabled |
| **Partial** | Provider A loaded, provider B failed: B's group header carries `Border.Chip.red` «Не обновлено» and a `Button.Text` «Повторить» in its flyout; A renders normally |
| **Long content** | A 70-character remark wraps to two lines and the row grows to 72px; a provider name over 25 chars is capped **in the parser** |
| **Short content** | One provider, one server: the pane still renders, «Все серверы» still renders, nothing looks broken |
| **Probing all** | Every ping slot shows its arc; the toolbar's speed button becomes a cancel button |
| **Multi-select** | The selection bar replaces the toolbar's right side |

### 6.9 Keyboard path

`Tab`: rail → search → sort → ping-all → add → provider pane (one stop, arrows move within) → list
(one stop, arrows move within). Inside the list: `Up`/`Down` move focus, `Home`/`End` jump,
`Enter` connects to the focused server, `Space` toggles selection, `Delete` removes with undo,
`Menu` opens the flyout, `Ctrl+A` selects all, `Ctrl+P` pings the group, `Ctrl+F` returns to search,
`Esc` clears search then clears selection. The list is virtualised, so focus movement must scroll the
container itself, not rely on realisation.

### 6.10 Серверы acceptance

- [ ] Search exists, filters in place, and has a designed no-results state
- [ ] Sort exists with three options and persists per provider
- [ ] Every one of the seven per-item actions is reachable by mouse **and** by keyboard
- [ ] Delete is undo; no confirmation dialog for a single server
- [ ] One unified server icon; no emoji in the tile and none left in the remark text
- [ ] Ping never uses colour as its only signal, and never jitters
- [ ] Group headers carry two buttons, both 40x40; no local size overrides
- [ ] The list is virtualised and stays smooth at 500 rows
- [ ] Two panes above 900px of content width, one column below; no nested scrollers
- [ ] All twelve states in 6.8 implemented and looked at

---

## 7. Destinations 3 and 4, and every sub-page

### 7.1 Destination 4 - Аккаунт

**File today:** `Views/AccountView.axaml` (1 474 ln, the largest view) + `.axaml.cs` (524 ln).
**Files after:** `Views/Account/AccountPage.axaml` (the tab) and
`Views/Account/SubscriptionPage.axaml` (a sub-page).
**Verdict: REBUILD.** The visual grammar is right and is kept: one hero card, three zones separated
by hairlines, one `Display` figure, quiet red text for sign-out. The **structure** is not: a
four-panel flyout wizard inside a hand-rolled drag-snap carousel inside a vertical scroll, in one
1 474-line file. The owner has already said the Account tab and every button in it are to be
reworked on both platforms.

**Android counterpart:** `AccountFragment` / `activity_account.xml`. Same three zones in the same
order, same four-state subscription slot (skeleton / content / empty / error), same copy.
**Deliberate differences:** desktop replaces the carousel with a vertical list because a horizontal
drag carousel is a touch idiom and a pointer has a scrollbar; desktop moves the upgrade and
add-devices flows onto a real sub-page because it has one and a phone bottom sheet is the Android
answer; desktop shows the sign-in gate **inside** the tab while Android hides the tab, and this plan
picks the desktop behaviour for both (`21-account-survey.md` 2.3 item 13 flags the divergence and
`00-rules.md` 13 demands one answer).

#### 7.1.1 Layout

```
ScrollViewer
└─ StackPanel  MaxWidth Size.Content 720  Margin 16,16,16,32  HorizontalAlignment=Center
   ├─ Border.Card  #hero        Padding 16              ZONE 1
   ├─ 24
   ├─ TextBlock.SectionHeader «Подписка»                ZONE 2
   ├─ 8
   ├─ Panel #subscriptionSlot   four exclusive states
   ├─ 24
   ├─ TextBlock.SectionHeader «Способы входа»           ZONE 3
   ├─ 8
   ├─ Border.Card  Padding 0    rows
   ├─ 24
   ├─ TextBlock.SectionHeader «Управление»              ZONE 4
   ├─ 8
   ├─ Border.Card  Padding 0    rows
   ├─ 24
   └─ Border.Row  #rowSignOut   outside any card
```

#### 7.1.2 Zone 1, the hero card

Three sub-zones, two 1px `Brush.OutlineVariant` hairlines, one card. **No nested cards.**

```
Border.Card #hero  Padding 16
├─ A identity     Grid ColumnDefinitions="48,12,*"
│   ├─ Border.Avatar 48   monogram TextBlock.Headline 24/700 Brush.OnAccent on Brush.Tile.Blue
│   └─ StackPanel
│        TextBlock.Title      «Александр»            16/700, ellipsises   ← was Headline 24
│        TextBlock.Caption    «Тариф · Base»          or «Пробный период»
├─ hairline  Margin 0,16,0,16
├─ B money        Grid ColumnDefinitions="*,Auto"  VerticalAlignment=Bottom
│   ├─ StackPanel
│   │    TextBlock.Caption  «Баланс»
│   │    StackPanel Orientation=Horizontal  VerticalAlignment=Bottom
│   │      TextBlock.Display.Numeric «1 240»   34/700, tnum lnum, zero OFF (money)
│   │      TextBlock.Title «₽»  Brush.OnSurfaceVariant  Margin 4,0,0,4
│   └─ Button.Tonal  «Пополнить»   48h            ← was Primary; see the accent note
├─ hairline  (only when HasReferral)
└─ C referral     Grid ColumnDefinitions="*,Auto"
    ├─ TextBlock.Caption «Код друга» + Border.Chip.neutral with the code in Font.Numeric
    └─ Button.IconButton40.Row  Geo.Action.Copy   ToolTip «Скопировать код»
```

Two changes with reasons:

- **The name drops from `Headline` 24 to `Title` 16/700.** Today `Display` 34 (balance) plus
  `Headline` 24 (name) plus `Headline` 24 (the ₽ symbol) sit inside one card and the identity fights
  the money at the same weight. One `Display` per screen is the rule; the *second* loudest thing
  should not tie with it.
- **«Пополнить» becomes `Button.Tonal`.** The tab's single filled accent belongs to «Продлить» on the
  subscription, or to «Купить подписку» when there is none. Today a default logged-in view carries
  simultaneously: a filled «Пополнить», a filled «Продлить», an accent-coloured «Купить подписку» row
  title, a blue Telegram tile, a blue buy tile and an accent traffic fill. The allowance is one.

**Top-up flyout**, anchored to «Пополнить», `IncyFlyoutTheme`, Width 280, Spacing 12:
Title «Пополнение баланса» → Caption «Введите сумму в рублях. Откроется страница оплаты.» →
`TextBox.Field.numeric` (watermark «Сумма, ₽», `Enter` submits) → an error line in `Brush.RedText` 12
that is always present in the markup → method rows when there are two or more (`Border.Row.selectable`
with a filled check, **not** the current `Button.MethodChip` wrap panel) → `Button.Primary`
«Продолжить», disabled until valid. The flyout closes only on success, so a validation error stays
visible. Both fields opt into `TextBox.Field`; today they are stock Semi.

#### 7.1.3 Zone 2, the subscription

**The carousel is deleted.** Four exclusive states, and in the content state a **vertical list**:

| State | Rendering |
|---|---|
| `ShowSkeleton` | One `Border.Card` MinHeight 176 with `Border.Skeleton` bars in the loaded silhouette: 180x16, 72x24 chip, 220x4 meter, 160x13 x2. No layout shift when the real card arrives |
| `ShowContent`, one subscription | One `Border.Card`, the anatomy below |
| `ShowContent`, two or more | A `StackPanel` of `Border.Row` summary rows inside one card, 8 apart, each opening `account/subscription/{id}`. No dots, no arrows, no drag |
| `ShowEmpty` | `Border.Card` MinHeight 120: `Border.EmptyIcon` + Title «Подписки пока нет» + Subtitle «Купите тариф, чтобы подключаться к серверам Departament.» + `Button.Primary` «Купить» |
| `ShowError` | `Border.Card`: neutral tile + alert glyph + Title «Не удалось загрузить» + Subtitle from the taxonomy + `Button.Tonal` «Повторить» |

**The single-subscription card:**

```
Border.Card  Padding 16
├─ header    Grid "*,Auto,Auto"
│     TextBlock.Title  «Ваша подписка»  (or the user's rename)
│     Border.Chip      state word + colour + glyph        (4.1)
│     Button.IconButton40.Row  Geo.Action.More            → MenuFlyout
├─ 16
├─ meters    StackPanel Spacing 12
│     expiry   TextBlock.Subtitle.Numeric  «Активна до 12.08.2026»
│              classes .urgent → Brush.Icon.Yellow, .expired → Brush.RedText
│     traffic  Grid "*,Auto":  Border.Meter 4px  +  Subtitle.Numeric «12,4 ГБ из 50 ГБ»
│              label BESIDE the bar, fill SOLID Brush.Accent, never a gradient
│     devices  Border.Row.static:  Subtitle «2 из 5 устройств»  + chevron → account/devices
├─ 16
├─ action    ONE full-width button, 48h
│              Button.Primary «Продлить»   when истекает / истекла
│              Button.Tonal   «Продлить»   otherwise
├─ 12
└─ autorenew Border.Row  «Автопродление»  + ToggleSwitch.iOS
              Subtitle.Numeric «Продлится 03.08, спишем 150 ₽»
```

**The kebab flyout is two items, not four panels:** «Улучшить тариф» and «Докупить устройства», both
routing to `account/subscription/{id}` with that section expanded. The four-panel wizard (menu →
device stepper → upgrade list → upgrade confirm), with its own back buttons and its own titles inside
a popup inside a card inside a carousel, is deleted. `00-rules.md` 7.6 orders inline > expandable row
> flyout > dialog; a purchase flow with a stepper, a price estimate and two payment buttons is not a
per-item overflow action.

**The multi-subscription summary row:**

```
Border.Row   → account/subscription/{id}
├─ Border.Tile  Geo.Set.Shield 22   neutral / .amber / .red
├─ text  Title «Подписка · Base»   Subtitle «12,4 ГБ из 50 ГБ · до 12.08.2026»
├─ Border.Chip  state
└─ chevron 20
```

#### 7.1.4 Zone 3, «Способы входа»

One card, four 56px rows, 68px hairlines.

| Row | Tile | Value | Trailing |
|---|---|---|---|
| Telegram | `.Blue` `Geo.Auth.Telegram` | `@username` when linked | 20px `Geo.State.Check` `Brush.OnSurfaceVariant`; when pending, a `Border.Chip` with the code plus `Button.Text` «Открыть бота»; when unlinked, `Button.Text` «Привязать» |
| «Почта и пароль» | neutral `Geo.Auth.Mail` | the address when linked | check, or `Button.Text` «Добавить» with a flyout: Title «Привязать почту», `TextBox.Field`, `Button.Primary` «Отправить» |
| Google | neutral `Geo.Auth.Google` | the address when linked | check, or the row is **hidden entirely** |
| «Веб-кабинет» | neutral `Geo.Action.Globe` | - | `Button.Text` «Открыть» |

**The permanently disabled «Скоро» is deleted.** A control that is `IsEnabled="False"` and will never
enable in this build is unfinished work rendered as UI, not a state. The Google row appears when
Google sign-in ships.

#### 7.1.5 Zone 4, «Управление», and sign-out

One card, three rows: «История платежей» with the latest payment date as its value and a chevron;
«Купить подписку» with a `.Blue` tile and a chevron (the title is **not** accent-coloured; an
accent-tinted row title is a fifth accent element on a tab that already has its one); «Веб-кабинет».

Sign-out is a `Border.Row` **outside** the card, 24 below it: neutral tile with
`Geo.Action.SignOut`, title in `Brush.RedText`, no fill, no chevron. Clicking it opens the one
genuinely destructive confirm on this tab (a modal, 9.1), because signing out on desktop discards the
local session and the answer to "did I mean that" is not recoverable by undo.

#### 7.1.6 The signed-out gate

Rendered **inside** the tab, not instead of it:

```
StackPanel  MaxWidth Size.Form 480  VerticalAlignment=Center  Spacing 16
├─ Border.Avatar 56   Geo.Auth.Telegram 28  Brush.Tile.Blue
├─ TextBlock.Headline  «Войдите в departament»
├─ TextBlock.Body      «Через Telegram, быстро и без пароля. Или по почте на сайте.»  MaxWidth 420
├─ Button.Primary.Tall «Войти через Telegram»   52h  → route auth/login (panel Method, Telegram)
└─ Button.Text         «Другие способы входа»          → route auth/login (panel Method)
```

#### 7.1.7 Every state of Аккаунт

Signed out · loading (hero skeleton plus subscription skeleton) · signed in with no subscription ·
one subscription in each of the six subscription states · two or more subscriptions · balance zero ·
balance six digits (`1 284 371 ₽`, and the column does not shift) · Telegram pending · error on
profile · error on subscriptions only (partial: hero renders, zone 2 shows its error card) · offline
· sign-out in flight (row disabled with an inline arc).

#### 7.1.8 Motion

The entrance stagger stays: group 1 at delay 0, group 2 at +40ms, capped at 400ms total, played
once per session per tab and only when the tab becomes active. The balance crossfades (`Opacity`
0.25 → 1, `TranslateY` -6 → 0, `Dur.State`) **only on a real change**, never on first paint, never
when the tab is not hit-test-visible, never under `.lite`. Everything else is still.

#### 7.1.9 Acceptance

- [ ] Exactly one filled accent surface on the tab in every state
- [ ] Exactly one `Display` figure
- [ ] No carousel, no four-panel flyout, no drag threshold, no tunnel pointer handlers
- [ ] Both `TextBox`es use `TextBox.Field`; zero Semi defaults on the tab
- [ ] Zero raw hex (`AccountView.axaml:65` `#3D7EF0` and `:68` `#3877E0` move to the button class)
- [ ] Zero off-scale spacing (today: `Spacing="6"` x2, `"10"`, `"20"`, `Margin="6,0,0,4"`)
- [ ] The traffic meter fill is solid; the label is beside the bar
- [ ] The health chip does not reuse payment-status class names
- [ ] Both icon-only buttons without names get names
- [ ] The file is under 400 lines and the sub-page carries the rest

---

### 7.2 `account/subscription/{id}` - the subscription sub-page

**New file:** `Views/Account/SubscriptionPage.axaml`. This is where the deleted four-panel flyout
goes, and it is the answer to «Докупить устройства» and «Улучшить тариф» being three levels deep.

**Android counterpart:** the subscription card's expanded actions. Android reaches the same two flows
through `PaymentMethodSheet` and a stepper dialog; desktop uses a page. Same copy, same order, same
prices, same confirmations.

```
SubPage  Title = the subscription's name   MaxWidth Size.Content 720
├─ header block                 Border.Card Padding 16
│    Title + rename affordance (Button.IconButton40.Row Geo.Action.Edit → inline TextBox.Field)
│    Border.Chip state
│    expiry · traffic meter · device meter, the same three meters as the card
├─ 24
├─ SectionHeader «Действия»
├─ Border.Card Padding 0
│    Border.Row  «Продлить»            value «1 500 ₽ · 30 дней»    chevron → payment picker
│    Border.Row  «Улучшить тариф»      value «Base»                  chevron → tariff picker
│    Border.Row  «Докупить устройства» value «2 из 5»                rotating chevron → inline stepper
│    Border.Row  «Автопродление»       value «Продлится 03.08»       ToggleSwitch.iOS
├─ 24
├─ SectionHeader «Подключение»
├─ Border.Card Padding 0
│    Border.Row  «Ссылка подписки»     value the host, ellipsised    Button.IconButton40.Row copy
│    Border.Row  «QR-код»                                            chevron → QR dialog
│    Border.Row  «Устройства»          value «2 из 5»                chevron → account/devices
└─ 24
   Border.Row   «Удалить подписку»  Brush.RedText, outside the card
```

- **«Продлить» is the one filled accent** on this page, and it is rendered as a `Button.Primary` bar
  docked at the bottom of the page when the state is `истекает` or `истекла`; otherwise it is the row
  shown above. One page, one primary.
- **The device stepper is an inline expand**, not a flyout: the row's chevron rotates 0 to 90 and a
  panel opens below it with `Button.Stepper` minus / `TextBlock.Headline.Numeric` count /
  `Button.Stepper` plus, an estimate line `TextBlock.Title.Numeric` «≈ 150 ₽», a caption «Примерная
  сумма. Точную посчитаем при оплате.», and the payment choice as two rows.
- **The payment choice is one grammar everywhere**: two `Border.Row.selectable` rows, «С баланса ·
  1 500 ₽» and «Картой», with a filled check on the selected one, then one `Button.Primary`
  «Оплатить». Today the same decision is inline buttons in three places on Аккаунт and a bottom sheet
  on Покупка. If the save button looks different in two places, one is wrong.
- **`hide-url` is refused**: the «Ссылка подписки» row always shows and always copies. We may keep
  the URL out of a shared backup; we never remove the owner's ability to read it.

States: loading (skeleton rows) · content · renewing (the action row's trailing becomes an 18px arc
and the row is disabled) · upgrade in flight · error (status strip plus the row's inline retry) ·
offline (all money actions disabled, the copy and QR rows still work).

---

### 7.3 `account/buy` - Покупка

**File:** `Views/BuyView.axaml` (709 ln) + `.axaml.cs` (173 ln) → `Views/Account/BuyPage.axaml`.
**Verdict: RESTYLE.** Our own inventory calls it the closest thing to a finished 2026 screen: five
states, real skeletons, a proper scrim, Escape handling, one accent CTA. Six defects to close.

**Android counterpart:** `activity_buy_tariff.xml` plus `PaymentMethodSheet`. Identical tariff card
anatomy, identical price-option rows, identical total line, identical copy. **Deliberate
difference:** the payment picker is a bottom sheet on Android and inline rows on desktop.

```
SubPage Title «Купить подписку»   MaxWidth Size.Content 720
└─ StackPanel Margin 16,8,16,32
   ├─ TextBlock.Title «Выберите тариф»   Margin 0,0,0,8
   ├─ ItemsControl of Border.TariffCard, 12 apart, entering with a +40ms stagger capped at 6 steps
   │    Border.TariffCard  Brush.Surface  r20  1.5px border (constant, colour changes on select)
   │      header  Border.Row MinHeight 56:
   │         Title «Base»   Subtitle.Numeric «3 устройства · трафик без ограничений»
   │         20px Geo.State.Check, slot always reserved, fades in over 150ms
   │      options (revealed when selected, fade + 6px rise, Dur.Reveal 300 OutQuint)
   │         Border.Row.selectable  r12  MinHeight 48   ← was Border.PriceOption r14
   │            Body.Numeric «30 дней»            Body Bold accent «1 290 ₽»
   └─ Border.Card #checkout   (visible when a price option is chosen)
        Border.Row  «Дополнительные устройства»  value ExtraCostText  two Button.Stepper
        hairline
        Grid  «Итого»  +  TextBlock.Headline.Numeric accent «1 440»  +  Title muted «₽»
        hairline
        SectionHeader-less group: two Border.Row.selectable payment rows + filled check
        Button.Primary.Tall  «Оплатить»  52h, wallet glyph swaps for an 18px arc while paying
```

**The six fixes:**

1. **Card in card.** A bordered option row inside a bordered tariff card is the nested-card ban.
   Option rows lose their border and become plain rows separated by a hairline inset to 16, with the
   selected one carrying `Brush.SelectedFill` plus a filled check.
2. **Radius 14 inside radius 20.** `Border.PriceOption` at `Radius.Search` 14 breaks the shape lock.
   It becomes a row at 12.
3. **The bottom sheet.** A slide-up sheet at the bottom of a 900x600 window is a phone idiom. It
   becomes two inline rows in the checkout card, matching 7.2.
4. **Error text in `Brush.Red`** (`#F04452`, 4.88:1) becomes `Brush.RedText` (`#FF6069`, 6.15:1).
5. **Toolbar title at `Headline` 24** becomes `Title` 16/700.
6. **The success state is a dead end.** It becomes: check tile, Title «Подписка оплачена», Subtitle
   «Серверы уже добавлены. Можно подключаться.», `Button.Primary` «Подключиться» which pops to
   `home` and starts the tunnel, plus `Button.Text` «К списку серверов».

Also added: a **purchase summary** in the checkout card (the chosen tariff name and period restated
above the total), because today the user never sees what they are buying next to what they are
paying. Same hole exists on Android and is closed there too.

States: skeleton (three `Border.Skeleton` cards at 76px) · content · pending (`Платёж обрабатывается…`
as an info strip) · success · empty (`Тарифы недоступны`) · error · offline (the CTA is disabled and
the strip explains).

---

### 7.4 `account/devices` - Устройства

**File:** `Views/DevicesView.axaml` (491 ln) → `Views/Account/DevicesPage.axaml`.
**Verdict: RESTYLE.** Five states, one card with inset dividers, correct destructive treatment.

**Android counterpart:** `activity_devices.xml` plus `item_device.xml`, which today renders each
device as its own 20dp card with 8dp gaps. Both platforms converge on **one card, divided rows**.

```
SubPage Title «Устройства»   trailing: Border.Chip.neutral «2 из 5»   MaxWidth Size.Content 720
└─ StackPanel Margin 16,8,16,32
   ├─ TextBlock.Subtitle «Устройства, подключённые к вашей подписке»   (hidden in empty/error)
   ├─ 16
   └─ Border.Card  Padding 0
        Border.Row per device   MinHeight 56  Padding 16,12
        ├─ Border.Tile 40   platform glyph: Geo.Dev.Windows | Android | Apple | Router | Generic
        ├─ text   Title «MacBook Pro»  +  Border.Chip.accent «Это устройство» when current
        │         Subtitle.Numeric «Подключено 12.08.2026 · 192.168.1.14»
        └─ Button.IconButton40.Row  Geo.Action.Unlink  Brush.Red  ToolTip «Отвязать устройство»
        hairline at 68
```

**Five fixes:**

1. **The raw HWID third line is deleted from the row.** It moves into the row's tooltip and into a
   `Button.Text` «Показать идентификатор» in the row's flyout. Three lines per row of which the third
   is a hex fingerprint is a developer surface.
2. **The current-device wash is deleted.** Today `Brush.Tile.Blue` is painted across the whole row
   **and** used as the chip's background, so the chip dissolves into its own backdrop, and an accent
   wash sits on a row that is not selected. The chip alone carries the fact.
3. **`Background="#80000000"`** (`:451`) becomes `{DynamicResource Brush.Scrim}`.
4. **Off-scale `Margin="0,3,0,0"` and `Margin="16,10"`** become 4 and 16,12.
5. **Unlink becomes undo, not confirm.** The device re-registers on the next connect, so this is a
   reversible action and `00-rules.md` 7.5 prefers undo: the row disappears, the status strip shows
   `Устройство отвязано` with `Отменить` for 5 seconds. The centred confirm card on a scrim is
   deleted.

States: list · loading (three skeleton rows with the real row's geometry) · empty («Устройств пока
нет» / «Устройства появятся после первого подключения.») · no subscription («Активная подписка не
найдена» + `Button.Primary` «Купить подписку») · error · offline · at limit (the toolbar chip turns
`.amber` and a warning strip carries «Достигнут лимит устройств»).

---

### 7.5 `account/history` - История платежей

**File:** `Views/PaymentHistoryView.axaml` (351 ln) → `Views/Account/PaymentHistoryPage.axaml`.
**Verdict: RESTYLE.**

**Android counterpart:** `activity_payment_history.xml` plus `item_payment.xml`. Same row, same
status vocabulary, same empty CTA. Android has swipe-to-refresh; desktop gets `F5` and a toolbar
refresh button, which is the pointer equivalent.

```
SubPage Title «История платежей»   trailing Button.IconButton40 Geo.Action.Refresh (F5)
└─ Border.Card Padding 0   MaxWidth Size.Content 720
     Border.Row.static per payment   MinHeight 56
     ├─ Border.Tile 40  neutral  Geo.Pay.History 22
     ├─ text   Body «Продление подписки Base»   Caption.Numeric «12.08.2026, 14:32»
     ├─ right  Body Bold Numeric «1 290 ₽»
     │         Border.Chip  «Оплачено» .green | «В обработке» .amber | «Ошибка» .red | «Отменён» .neutral
     hairline at 68
```

Fixes: the three hand-copied 60-line skeleton blocks become one `SkeletonList` component with a count
parameter; the hand-coded 64x64 `CornerRadius="20"` empty tile becomes `Border.EmptyIcon`; the
identical non-interactive card stack becomes one card with divided rows; the toolbar title drops to
`Title` 16/700.

States: list · loading · empty («Платежей пока нет» / «Здесь появится история покупок и продлений.» /
`Button.Primary` «Купить подписку») · error (`Button.Tonal` «Повторить», **and** the buy CTA, which
the error state lacks today) · offline · partial (older pages failed to load: an inline
`Button.Text` «Загрузить ещё» at the bottom with its own error line).

---

### 7.6 Destination 3 - Настройки

**File:** `Views/SettingsView.axaml` (1 075 ln) + `.axaml.cs` (359 ln) →
`Views/Settings/SettingsPage.axaml`.
**Verdict: RESTYLE the rows, REBUILD the information architecture.**

The affordance-honesty grammar documented at `SettingsView.axaml.cs:14-22` is the single best design
decision in the codebase, it is now product law (4.3a), and it is kept verbatim. What changes: the
missing `MaxWidth`, the group count and order, a search field, the eight rows that today have no home
at all, and the ten engine features that exist only inside the dead `OptionSettingWindow`.

**Android counterpart:** `layout_settings_content.xml`. `00-rules.md` 13 fixes the group order as
identical, and it currently is not (desktop has 5 groups, Android 6, in different orders). **This
plan sets the order for both platforms.** Everything else is identical: same rows, same values, same
defaults, same sub-page destinations, same copy keys.

#### 7.6.1 The hub

```
SubPage-less destination (no back button; it is a tab)
DockPanel
├─ [Top] Grid Height 56  Margin 16,8,16,0   MaxWidth Size.Content 720
│     TextBox.Field.search   Height 40   watermark «Поиск по настройкам…»   Ctrl+F
└─ ScrollViewer
   └─ StackPanel  MaxWidth Size.Content 720  HorizontalAlignment=Center  Margin 0,0,0,32
        SectionHeader + Border.Card(Padding 0) x 4, 24 apart
```

**Four groups, maximum seven rows each** (`03-direction.md` 7.3). Twenty rows total, down from
twenty-two but covering ten features that are currently unreachable.

**Group 1 - «Подключение»** (6 rows)

| Row | Value shown | Affordance | Target |
|---|---|---|---|
| Режим | - | segment «Весь трафик» / «Прокси» | in place |
| Прокси по приложениям | «Кроме 12 приложений» / «Выкл» | chevron | `settings/perapp` |
| Маршрутизация | «Стандартные · 42 правила» | chevron | `settings/routing` |
| DNS | «Cloudflare» | chevron | `settings/dns` |
| Обход блокировок | «Mux, фрагментация» / «Выкл» | chevron | `settings/bypass` |
| Локальный прокси | «127.0.0.1:10808» | rotating chevron | inline panel |

**Group 2 - «Интерфейс»** (6 rows)

| Row | Value | Affordance |
|---|---|---|
| Оформление | - | segment «Тёмная» / «Светлая» |
| Монохром | - | switch |
| Масштаб интерфейса | «125 %» | unfold, cycles 100 / 125 / 150 / 175 / 200 |
| Язык | «Русский» | unfold, cycles Русский / English |
| Облегчённый режим | - | switch (reduced motion, live) |
| Запуск при входе в систему | - | switch |

**Group 3 - «Подписки и серверы»** (4 rows)

| Row | Value | Affordance | Target |
|---|---|---|---|
| Провайдеры | «Автообновление · 6 ч» | chevron | `settings/provider` |
| Задержка | «Реальная · 5 с» | chevron | `settings/ping` |
| Файлы ресурсов | «geoip 8,2 МБ · обновлён 12.08» | chevron | `settings/geofiles` |
| Схемы URL-адресов | «Зарегистрирована» / «Не зарегистрирована» | chevron | `settings/urlschemes` |

**Group 4 - «Система»** (5 rows)

| Row | Value | Affordance | Target |
|---|---|---|---|
| Ядро и журнал | «Xray · предупреждения» | chevron | `settings/core` |
| Горячие клавиши | «12 назначено» | chevron | `settings/hotkeys` |
| Резервная копия | - | chevron | `settings/backup` |
| Обновления | «Версия 7.13.4 · актуальна» | chevron | `settings/update` |
| О приложении | - | chevron | `settings/about` |

**«Автообновление подписки» exists once**, inside `settings/provider`. Today Android carries it in
two places, in two visual languages, two taps apart, writing the same fields.

**Where the theme settings live, and why there is no theme page.** `Views/ThemeSettingView.axaml`
(67 ln, registered and never built) is deleted rather than rebuilt. A theme picker with three
controls does not earn a push: «Оформление» is a two-state change and is therefore a segment applied
in place, «Монохром» is a boolean and is therefore a switch, and «Масштаб интерфейса» is a short
cycle and is therefore `unfold_more`. All three obey the affordance grammar, all three are visible
and adjustable from the hub without navigating, and the theme flood-reveal (3.10) plays from the
segment's own click point. This is the one place where the desktop deliberately has **fewer** pages
than a naive port of Android's settings tree would produce, and the reason is the grammar, not
laziness: a page whose entire content is three rows is a row group.

#### 7.6.2 The row

```
Border.SettingRow   MinHeight 56  Padding 16,12   Focusable  IsTabStop
├─ Border.Tile 40   NEUTRAL by default; today 21 of 22 already are, and that is the model
├─ 12
├─ text  Title 16/700  «DNS»
│        Subtitle 13   «Через какой сервер приложение разрешает домены»   (only when it adds something)
├─ value Subtitle 13 muted, right-aligned    ← Incy's best idea: the current value on every row
├─ 8
└─ ONE trailing affordance
```

A subtitle that restates its title is deleted (`03-direction.md` F16). The **value** is what earns
the space: you can audit your entire configuration by scrolling once, without opening anything.

Rows are `Focusable`, `Enter` and `Space` activate, the focus ring is drawn inside the row at r12,
and the trailing switch is removed from the tab order so the row owns the stop. `SettingsView` already
implements this and it is the pattern for every list in the product.

#### 7.6.3 Search

Typing filters the hub's rows **and** the rows of every sub-page. A matched sub-page row renders as:

```
Border.Row
├─ tile
├─ Title «Тайм-аут проверки»
├─ Caption «Подключение › Задержка»          ← the breadcrumb, so the result is locatable
└─ chevron  → opens that sub-page with the row highlighted for 1.2 s (Brush.SelectedFill, fading out)
```

`Esc` clears the field; a second `Esc` returns focus to the list. No results shows the standard
«Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск».

#### 7.6.4 Inline expansion

«Локальный прокси» expands in place (rotating chevron), revealing a panel inside the same card:
port `TextBox.Field.numeric`, «SOCKS5-авторизация» switch, login and password fields, and a caption
«Адрес: 127.0.0.1. Пустые логин и пароль отключают SOCKS5-авторизацию.» Reveal is `Dur.Reveal` 300ms
`Ease.OutQuint` on height plus opacity; collapse is 225ms. This is correct per `00-rules.md` 7.6 and
it stays.

#### 7.6.5 States

Default · search active · no results · a row disabled because the platform cannot do it (see 7.6.6) ·
a value that is still resolving (the value slot shows a 40x13 skeleton bar, never «…») · error
applying a setting (the status strip, and the control reverts).

#### 7.6.6 Platform honesty

Neither reference app does this and it is a trust differentiator (`30-reference-analysis.md` 3.2.3).
Where a setting cannot do what its label implies on this OS, the row is **disabled with an
explanation**, never silently present:

| Setting | Constraint | Row behaviour |
|---|---|---|
| Режим «Весь трафик» (TUN) | needs elevation on Linux and macOS | Enabled, but selecting it raises the elevation prompt; if refused, the row reverts and the TUN banner appears on Главная |
| Прокси по приложениям | TUN mode only, sing-box only | Disabled with the subtitle «Работает в режиме «весь трафик»» when the mode is Прокси |
| Запуск при входе в систему | not available in some Linux sandboxes | Disabled with the subtitle «Недоступно в этой сборке» |
| Схемы URL-адресов | Windows only | Disabled with the subtitle «Регистрация схемы доступна только в Windows» |

#### 7.6.7 Acceptance

- [ ] `MaxWidth Size.Content` 720, centred; rows never run 1030px wide
- [ ] Four groups, in this order, matching Android exactly
- [ ] Every row shows its current value
- [ ] Every row's affordance matches its behaviour
- [ ] 20 neutral tiles, at most one coloured, and only if it is a category
- [ ] Search covers the hub and every sub-page and has a no-results state
- [ ] Zero accent elements at rest except the rail and the focus ring
- [ ] The duplicate `TextBox.Incy` re-declaration inside the view is deleted
- [ ] Card bottom margins are `space_24` between groups, not `8 + header padding`

---

### 7.7 Settings sub-pages: the shared contract

All fourteen are `SubPage` instances (3.6) with `MaxWidth Size.Content` 720. Each one:

- uses the **shared** `Button.BackNav` and the shared toolbar; the nine local
  `Button.IconButton:pressed { scale(0.92) }` re-declarations and the nine local `Geo.Sub.Back`
  copies are deleted,
- opens with an intro paragraph in `TextBlock.Body`, `MaxWidth` 560, `Margin 16,8,16,16`, that says
  what the page controls in one or two sentences and never markets,
- groups rows in `Border.Card Padding=0` with 68px hairlines under a sentence-case `SectionHeader`,
- carries **at most one** filled accent control, and most carry none,
- ships default, loading, empty, error and offline where each applies,
- is completable with the keyboard alone,
- has a stable `RouteId` from 3.7.1.

Below, only what is specific to each page.

---

### 7.8 `settings/perapp` - Прокси по приложениям

**File:** `Views/PerAppProxyPage.axaml` (163 ln) + `.cs` (238 ln). **RESTYLE.**
**Android counterpart:** `activity_bypass_list.xml` plus `AppPickerActivity`, which today is a bare
10-line `RecyclerView` with no empty state at all. Same concept, same two modes, same copy.

```
intro «Выберите, какие программы идут через VPN. Правила применяются при следующем подключении.»
SectionHeader «Режим»
Border.Card Padding 0
  Border.SettingRow «Раздельное туннелирование»   switch
  Border.SettingRow.segmentRow  segment «Кроме выбранных» / «Только выбранные»
     subtitle changes with the mode:
       «Кроме выбранных: они идут напрямую, минуя VPN»
       «Только выбранные: через VPN идут лишь они»
SectionHeader «Приложения»   +   trailing Button.Text «Добавить .exe»
TextBox.Field.search   Height 40   watermark «Поиск программ…»   Ctrl+F
Border.Card Padding 0
  virtualised list of Border.Row:
    Border.Tile 40 with the app icon (extracted, 24px, or Geo.Set.Grid fallback)
    Title  the app name          Subtitle  the executable path, ellipsised at the END
    trailing CheckBox styled as a 20px Geo.State.Check inside a 24px box
```

Selected apps sort to the top of the list, above a hairline, with a `Caption` «Выбрано 12» above
them. Fixes: `Margin="10"` off-scale; the local `Button.IconButton` style; the list must virtualise
(a Windows machine has 200 to 400 installed programs). States: loading the app list (skeleton rows),
empty («Программы не найдены»), no search results, disabled (mode is Прокси, per 7.6.6).

---

### 7.9 `settings/routing` - Маршрутизация

**Files:** `Views/RoutingSubView.axaml` (184 ln) + `.cs` (140 ln), and the two upstream windows it
escapes into: `RoutingRuleSettingWindow.axaml` (259 ln, 27 `resx:`) and
`RoutingRuleDetailsWindow.axaml` (263 ln, 16 `resx:`).
**Verdict: RESTYLE the page, REBUILD both windows as one depth-2 sub-page. The escape hatch into a
900x600 Chinese-string window closes here.**

**Android counterpart:** `activity_routing_setting.xml` plus `activity_routing_edit.xml`, which have
exactly the same hole.

**Page 1, `settings/routing`:**

```
intro «Наборы правил решают, какой трафик идёт через VPN, а какой напрямую. Выберите активный набор.»
SectionHeader «Наборы правил»
Border.Card Padding 0
  Border.Row.selectable per rule set
    Border.Tile 40  Geo.Set.Routing
    Title «Стандартные»    Subtitle.Numeric «42 правила»
    trailing: 20px Geo.State.Check Brush.Accent when active; kebab on hover
    row click = make active (immediate, no confirm)
    kebab MenuFlyout: «Изменить» → settings/routing/ruleset/{id} · «Дублировать» ·
                      «Экспорт» · hairline · «Удалить» Brush.RedText
  Border.Row  «Создать набор»   Geo.Action.Add   chevron
SectionHeader «Разрешение доменов»
Border.Card Padding 0
  Border.SettingRow «Стратегия доменов»  value «IP при несовпадении»  unfold, cycles 3
     subtitle «Как ядро сопоставляет домены с правилами»
SectionHeader «Обслуживание»
Border.Card Padding 0
  Border.Row «Стандартные правила»  Subtitle «Пересоздать встроенные наборы»  Button.Text «Сбросить»
```

**Page 2, `settings/routing/ruleset/{id}` (depth 2, the rebuild of both windows):**

```
SubPage Title = the rule set's name   trailing Button.IconButton40 Geo.Action.Add «Добавить правило»
TextBox.Field  «Название набора»  (label above, helper slot below)
SectionHeader «Правила»  +  Caption «Порядок важен: применяется первое совпавшее правило»
Border.Card Padding 0
  Border.Row per rule, draggable by a 20px Geo.Action.Drag handle at the leading edge
    Border.Tile 40  tinted by outbound: .Blue = проксировать, neutral = напрямую, .Red = блокировать
    Title  «Домены: 12 · IP: 3»  or the rule's name when it has one
    Subtitle  «Через прокси»  /  «Напрямую»  /  «Блокировать»
    trailing ToggleSwitch.iOS (enabled / disabled) and a kebab
  click on the row → the rule editor, INLINE (rotating chevron), not a third page
```

The **rule editor is an inline expansion**, which is what keeps navigation depth at 2:

```
panel inside the card, revealed at Dur.Reveal 300
  segment  «Проксировать» / «Напрямую» / «Блокировать»
  TextBox.Field multiline «Домены»       helper «Один на строку. Поддерживаются geosite:, regexp:»
  TextBox.Field multiline «IP-адреса»    helper «Один на строку. Поддерживаются geoip:, CIDR»
  TextBox.Field «Порты»                  helper «Например: 80, 443, 1000-2000»
  TextBox.Field «Протокол»               picker flyout: любой / http / tls / bittorrent
  row  «Входящий тег»                    picker flyout
  Button.Text «Удалить правило» Brush.RedText   +   Button.Primary «Готово»
```

Every field validates on blur with a specific message: «Укажите домен, порт или IP-адрес», «Порт
должен быть числом от 1 до 65535», «Неверный формат CIDR». Reordering is drag by the handle **and**
`Alt+Up` / `Alt+Down` on the focused row, because reordering must not be mouse-only.

---

### 7.10 `settings/dns` - DNS

**File:** `Views/DnsSubView.axaml` (162 ln) + `.cs` (130 ln). **RESTYLE.**
**Android counterpart:** the DNS `AlertDialog` today, which becomes the same page.

```
intro «DNS-сервер, через который приложение разрешает домены при подключении. По умолчанию
       используется встроенный резолвер.»
SectionHeader «Провайдер»
Border.Card Padding 0
  Border.Row.selectable x6:  «По умолчанию» · «Cloudflare» · «Google» · «AdGuard» · «FakeIP» · «Свой»
    Subtitle carries the actual address, e.g. «https://1.1.1.1/dns-query»
    trailing 20px Geo.State.Check on the selected one
Border.Card  (visible only when «Свой» is selected)
  TextBox.Field  label «Свой DNS-адрес»
     helper «DoH-адрес (https://…/dns-query), DoT или обычный IP: 1.1.1.1»
     validation on blur: «Укажите адрес в формате https://…/dns-query или IP»
SectionHeader «Дополнительно»
Border.Card Padding 0
  Border.SettingRow «Локальные ответы DNS»  switch
     subtitle «Ускоряет соединение, отвечая на DNS-запросы локально (sing-box)»
```

**`Border.DnsChip` is deleted.** It was a fully accent-**filled** selected chip at `Padding="16,10"`
with `FontWeight="SemiBold"` - an off-scale padding, a weight the ramp does not have, and a filled
accent surface on a settings page whose accent budget is zero. Six selectable rows with a check is
the same choice, in the product's own vocabulary, and it survives the mono theme.

---

### 7.11 `settings/bypass` - Обход блокировок

**New page**, absorbing the current hub group and the fragmentation controls that live only in
`OptionSettingWindow`.
**Android counterpart:** the «Обход блокировок» settings group, promoted to the same page.

```
intro «Приёмы против DPI. Включайте по одному: лишние могут замедлить соединение.»
SectionHeader «Мультиплексирование»
Border.Card Padding 0
  «Mux»                       switch      subtitle «Объединяет запросы в один канал соединения»
  «Число соединений»          unfold 4/8/16/32   visible only when Mux is on
SectionHeader «Фрагментация»
Border.Card Padding 0
  «Фрагментация пакетов»      switch      subtitle «Разбивает TLS-рукопожатие против DPI»
  «Размер фрагмента»          unfold      visible only when on
  «Интервал»                  unfold      visible only when on
SectionHeader «Шум UDP»
Border.Card Padding 0
  «Шум перед рукопожатием»    switch      subtitle «Маскирует начало соединения»
  «Тип шума»                  picker      visible only when on
```

Rows that appear conditionally do so with the `Dur.Reveal` 300ms height-plus-opacity expansion, never
by popping in. When a provider has set any of these (4.3b), the row's subtitle becomes «Задано
провайдером» and a `Button.Text` «Вернуть мои настройки» appears at the bottom of the page.

---

### 7.12 `settings/provider` - Провайдеры

**File:** `Views/ProviderSettingsPage.axaml` (138 ln) + `.cs` (86 ln). **Fully built, fully styled,
zero references.** Verdict: **WIRE and RESTYLE**, and extend it with the provider-transparency
section that neither reference app has.

```
intro «Как приложение обновляет подписки и что о нём знает провайдер.»
SectionHeader «Обновление»
Border.Card Padding 0
  «Автообновление подписок»   switch
  «Интервал обновления»       unfold «6 ч» (1 / 3 / 6 / 12 / 24)     visible when on
  «Обновить все сейчас»       Button.Text, with an inline arc while running
SectionHeader «Сеть»
Border.Card Padding 0
  «User-Agent»                value the current UA, ellipsised    rotating chevron → TextBox.Field
     subtitle «Отправляется ядром на исходящих соединениях»
  «Идентификатор устройства»  value the HWID in Font.Numeric      Button.IconButton40.Row copy
SectionHeader «Что настроил провайдер»          ← only when at least one directive is applied
Border.Card Padding 0
  Border.Row per applied directive:
    Title «Прокси по приложениям»   Subtitle «Задано провайдером»   value «12 приложений»  chevron
    Title «Фрагментация»            Subtitle «Задано провайдером»   value «Включена»       chevron
    Title «Маршрутизация»           Subtitle «Задано провайдером»   value «Обновляется»    chevron
    Title «Определение адресов»     Subtitle «Задано провайдером»   value «DoH»            chevron
Button.Text «Вернуть мои настройки»   Brush.Accent   Margin 16,16,16,0
```

The four rules from 4.3b apply here in full: every directive that changes device behaviour appears,
anything that overrides a user setting is revertable, `hide-url` is refused, and the operator
supplies content and severity but never presentation.

---

### 7.13 `settings/ping` - Задержка

**File:** `Views/PingSettingsPage.axaml` (160 ln). **RESTYLE.**

```
intro «Как измерять задержку серверов. Ниже адрес и тайм-аут проверки.»
SectionHeader «Метод»
Border.Card Padding 0
  Border.Row.selectable «Реальная задержка»  Subtitle «Через ядро, как при подключении»  check
  Border.Row.selectable «TCP»                Subtitle «TCP-подключение к серверу»        check
SectionHeader «Параметры»
Border.Card Padding 0
  TextBox.Field  label «Адрес проверки задержки»   default https://www.gstatic.com/generate_204
  TextBox.Field.numeric  label «Тайм-аут проверки, сек»   default 5   range 1..30
```

`Border.MethodRow`, invented locally in this file, is deleted in favour of `Border.Row.selectable`.

---

### 7.14 `settings/geofiles` - Файлы ресурсов

**File:** `Views/GeoFilesPage.axaml` (100 ln) + `.cs` (99 ln). **RESTYLE.**

```
intro «Базы geoip и geosite нужны для маршрутизации по странам и доменам. Обновляются с GitHub.»
Border.Card Padding 0
  Border.Row  «geoip.dat»    value.Numeric «8,2 МБ · обновлён 12.08.2026»   or «Не загружен»
  Border.Row  «geosite.dat»  value.Numeric «12,7 МБ · обновлён 12.08.2026»
Button.Primary «Обновить сейчас»  48h  Margin 16,16,16,0
```

**The download states this page currently lacks:** while running, the button's label swaps for an
18px arc and the button is disabled; each row's value becomes «Загрузка… 42 %» with a 4px
`Border.Meter` under it showing real progress; on success the values update and an info strip says
`Базы обновлены`; on failure the row's value becomes «Не удалось обновить» in `Brush.RedText`, the
button re-enables, and an error strip carries `Повторить`. Offline disables the button and explains.

---

### 7.15 `settings/urlschemes` - Схемы URL-адресов

**File:** `Views/UrlSchemesPage.axaml` (115 ln) + `.cs` (157 ln). **RESTYLE.**

```
intro «Быстрые команды depv://. Нажмите на схему, чтобы скопировать. Используйте их в ярлыках,
       скриптах или других приложениях.»
SectionHeader «Регистрация»
Border.Card Padding 0
  Border.Row  «Схема depv://»  value «Зарегистрирована» / «Не зарегистрирована»
              trailing Button.Text «Зарегистрировать» / «Убрать»
SectionHeader «Команды»
Border.Card Padding 0
  Border.Row per scheme, click copies:
    Title «Запустить туннель»    Subtitle.Numeric «depv://connect»
    trailing Button.IconButton40.Row Geo.Action.Copy
  ... «Открыть приложение» · «Остановить соединение» · «Переключить соединение»
      · «Импорт (автоопределение типа)» · «Добавить по URL»
```

Copy confirms through the status strip (`Скопировано`), never through a toast. On non-Windows the
registration card is disabled with «Регистрация схемы доступна только в Windows» and the command list
still renders, because the schemes still work when passed on the command line.

---

### 7.16 `settings/core` - Ядро и журнал

**New page.** This is where roughly ten features currently reachable only through the **unreachable**
`OptionSettingWindow` (1 206 ln, 91 `resx:`, 74 controls, 30 of them stock `ComboBox`) come back into
the product, in our own language.

```
intro «Движок, который поднимает туннель, и его журнал.»
SectionHeader «Ядро»
Border.Card Padding 0
  «Ядро»                    value «Xray»            picker flyout: Xray / sing-box
  «Версия ядра»             value.Numeric «25.1.30»  read-only, no affordance
  «Уровень журнала»         value «Предупреждения»   unfold: Выкл / Ошибки / Предупреждения / Отладка
  «Журнал»                  value «312 записей»      chevron → settings/core/log
SectionHeader «Порты»
Border.Card Padding 0
  «SOCKS5»                  value.Numeric «10808»    rotating chevron → TextBox.Field.numeric
  «HTTP»                    value.Numeric «10809»    rotating chevron → TextBox.Field.numeric
  «Разрешить из локальной сети»  switch   subtitle «Другие устройства смогут использовать прокси»
SectionHeader «Дополнительно»
Border.Card Padding 0
  «Шаблон конфигурации»     Subtitle «Для опытных пользователей»   chevron → settings/core/advanced
  «Сбросить настройки ядра» Brush.RedText  Button.Text
```

**`settings/core/log` (depth 2)** - the durable half of the feedback channel, and the reason
`MsgView` existed:

```
SubPage Title «Журнал»   trailing Button.IconButton40 Geo.Action.More → kebab
├─ toolbar row: TextBox.Field.search (Ctrl+F) + level chips «Все» «Ошибки» «Предупреждения» «Отладка»
├─ virtualised list of Border.Row.static  MinHeight 40  (not 56: this is dense technical output)
│     Caption.Numeric «14:32:07»  fixed 64px column
│     Body  the message, wrapping, selectable text
│     leading 4px colour bar? NO - side stripes are banned. Instead a 16px leading glyph:
│       Geo.State.Info | Geo.State.Warning | Geo.State.Error, tinted
└─ kebab: «Копировать всё» · «Сохранить в файл…» · hairline · «Очистить» Brush.RedText
```

Text is selectable. `Ctrl+A` then `Ctrl+C` works. Auto-scroll follows the tail and stops the moment
the user scrolls up, with a `Button.Text` «К последним» appearing at the bottom right. States: empty
(«Записей пока нет» / «Здесь появятся сообщения ядра и приложения.»), filtered-empty, error.

**`settings/core/advanced` (depth 2)** - the rebuild of `FullConfigTemplateWindow` (197 ln, 15
`resx:`). One `JsonEditor` (kept as a control, restyled: `Brush.SurfaceHighest` background, our
scrollbar, our font at 13, `Font.Numeric` for the gutter), a validation line under it in
`Brush.RedText` that reports the parse error and its line number, and two buttons: `Button.Tonal`
«Сбросить» and `Button.Primary` «Сохранить», the latter disabled while the JSON is invalid. A
warning strip at the top: `Неверный шаблон может сломать подключение.`

---

### 7.17 `settings/hotkeys` - Горячие клавиши

**Rebuild** of `GlobalHotkeySettingWindow.axaml` (133 ln, 11 `resx:`, currently unreachable). Global
hotkeys are a desktop-native expectation with no UI at all right now.

```
intro «Сочетания работают, даже когда окно свёрнуто.»
Border.Card Padding 0
  Border.Row per action:
    Title  «Подключить или отключить»
    trailing Border.Chip.neutral with the binding in Font.Numeric «Ctrl+Alt+V»
             or Button.Text «Назначить» when unset
    click → the row enters capture mode: the chip becomes «Нажмите сочетание…» in Brush.Accent,
            the next key chord is captured, Esc cancels, Backspace clears
  actions: Подключить или отключить · Показать окно · Скрыть окно · Перезапустить ядро ·
           Следующий сервер · Предыдущий сервер
Caption «Занятые системой сочетания подсвечиваются: назначить их не получится.»
```

A conflict with another application shows the chip in `Brush.RedText` with «Сочетание занято» below
the row. This page also lists **the in-app shortcuts** from 2.8 as a read-only section, because a
user who comes here to find a shortcut should find all of them.

---

### 7.18 `settings/backup` - Резервная копия

**File:** `Views/BackupPage.axaml` (96 ln) + `.cs` (91 ln). **RESTYLE.**
`Views/BackupAndRestoreView.axaml` (213 ln, registered and never built, with a WebDAV panel) is
**DELETED**; its WebDAV feature is not migrated, because no UI ever exposed it and no owner request
covers it. That deletion is explicit, not silent.

```
intro «Сохраните все настройки, подписки и серверы в один .zip-файл или восстановите их из
       сохранённой копии.»
Border.Card Padding 0
  Border.Row «Экспорт»   Subtitle «Сохранить копию в файл»                  Button.Text «Сохранить…»
  Border.Row «Импорт»    Subtitle «Восстановить из файла. Приложение перезапустится»
                                                                            Button.Text «Восстановить…»
```

Import is the one place a **confirmation dialog** is correct on this page: it is irreversible and it
restarts the app. `Восстановить из копии?` / `Текущие настройки, подписки и серверы будут заменены.`
/ `Отмена` + `Восстановить` (destructive, right). Progress is an info strip; failure is an error
strip with the file name and the parse error.

---

### 7.19 `settings/update` - Обновления

**Rebuild** of `Views/CheckUpdateView.axaml` (95 ln, registered, never built). **There is no "check
for updates" anywhere in the shipping UI**, which on a desktop app that ships outside a store is a
functional hole.

```
Border.Card Padding 16
  TextBlock.Title.Numeric «Версия 7.13.4»
  TextBlock.Subtitle «Последняя проверка: сегодня, 14:32»
  Button.Tonal «Проверить обновления»   48h
States on the same card:
  checking   → the button's label becomes an 18px arc, disabled
  up to date → Subtitle «Установлена последняя версия», Border.Chip.green «Актуальна»
  available  → Title «Доступна версия 7.14.0», a changelog block (Body, MaxWidth 560,
               scrollable, MaxHeight 240), Button.Primary «Скачать и установить»
  downloading→ 4px Border.Meter with real percentage and Caption.Numeric «12,4 МБ из 38,1 МБ»
  failed     → Subtitle in Brush.RedText «Не удалось проверить обновления» + Button.Tonal «Повторить»
SectionHeader «Компоненты»
Border.Card Padding 0
  Border.Row «Ядро Xray»     value.Numeric «25.1.30»   Button.Text «Обновить»
  Border.Row «Ядро sing-box» value.Numeric «1.11.1»    Button.Text «Обновить»
Border.SettingRow «Проверять автоматически»  switch
```

---

### 7.20 `settings/about` - О приложении

**File:** `Views/AboutPage.axaml` (105 ln). **RESTYLE.** Its `MaxWidth="620"` becomes
`Size.Content` 720 like every other sub-page.

```
StackPanel  HorizontalAlignment=Center  Spacing 16  Margin 0,32,0,0
├─ Border 64x64 r20  Brush.Tile.Neutral  →  PathIcon Geo.Server.ShieldOutline 32
├─ TextBlock «departament»   Title 16/700  Font.Grotesk   Brush.OnSurface   (NOT accent)
├─ TextBlock.Caption.Numeric «Версия 7.13.4 (2 041)»
SectionHeader «Сведения»
Border.Card Padding 0
  Border.Row.static «Операционная система»  value «Windows 11 26100»
  Border.Row.static «Архитектура»           value «x64»
  Border.Row.static «.NET»                  value.Numeric «9.0.1»
  Border.Row.static «Ядро»                  value «Xray 25.1.30»
  Border.Row  «Копировать сведения»   Button.IconButton40.Row Geo.Action.Copy
SectionHeader «Ссылки»
Border.Card Padding 0
  Border.Row «Сайт departament.site»  chevron  → browser
  Border.Row «Telegram-бот»           chevron  → browser
  Border.Row «Поддержка»              chevron  → browser
```

The wordmark is **not** blue (`03-direction.md` 3.2 corollary): the brand does not spend its one
accent on advertising itself.

---

### 7.21 `auth/login` - Вход

**File:** `Views/LoginView.axaml` (954 ln) + `.axaml.cs` (1 377 ln) → `Views/Auth/LoginPage.axaml`.
**Verdict: REBUILD.** The owner named this screen: «сейчас все выглядит плохо».

**The measured problem.** 20 buttons, 5 text boxes, 6 sign-in methods and 34 localisation keys in one
scrolling column, with the primary method - Telegram - **not first**: it sits below the e-mail form,
under an «или» divider, as a **tonal** button, while the accent `Primary` is spent on the e-mail
submit. The hierarchy is inverted and the page carries every method at once instead of one path with
the rest behind a disclosure.

**Android counterpart:** `activity_login.xml`, graded **D-** in `31-self-assessment.md`, with four
blue controls, two identical cards, the error line at the very bottom of the scroll after both cards,
and the 2FA block inserted between the submit button and the register button. **Both platforms get
this exact structure**; the desktop version is the reference implementation and Android ports it.

#### 7.21.1 Structure: one column, five panels

```
SubPage Title «Вход»   (back = close the page, Esc = step back one panel then close)
└─ Panel  MaxWidth Size.Form 480  HorizontalAlignment=Center  VerticalAlignment=Center  Margin 16,0
   five z-stacked panels, exactly one visible, crossfaded at Dur.State 220ms
   with TranslateX +16 → 0 forward and -16 → 0 backward
```

**Panel 1 - `Method` (default)**

```
StackPanel Spacing 0
├─ Border 64x64 r20  Brush.Tile.Blue  →  PathIcon Geo.Server.ShieldOutline 30 Brush.Accent
├─ 16
├─ TextBlock «departament»   Title 16/700  Font.Grotesk
├─ 8
├─ TextBlock.Headline  «Вход в departament»
├─ 4
├─ TextBlock.Body      «Через Telegram, быстро и без пароля.»   Brush.OnSurfaceVariant  MaxWidth 380
├─ 32
├─ Button.Primary.Tall «Войти через Telegram»   52h, full width      ← THE one lit element
├─ 12
├─ Button.Tonal.Tall   «Войти через сайт»       52h, full width
├─ 12
└─ Button.Text         «Другой способ входа»    40h, centred, rotating chevron 20 trailing
                                                 → panel Email
```

Four controls. Not twenty.

**Panel 2 - `Email`**

```
├─ Button.Text «Назад» with a leading 20px Geo.Action.Back, left-aligned, 40h
├─ 8
├─ TextBlock.Headline  «Почта и пароль»
├─ 24
├─ ToggleButton.Segment pair  «Вход» | «Регистрация»    44h track, neutral thumb (Brush.Bg), NOT blue
├─ 16
├─ field  label Caption «Почта»           TextBox.Field 48h   helper slot always present
├─ 16
├─ field  label Caption «Пароль»          TextBox.Field 48h with a 40px eye toggle in InnerRightContent
├─ 16
├─ field  label Caption «Повторите пароль» (registration only, revealed at Dur.Reveal 300)
├─ 8
├─ TextBlock.Caption   password rules (registration only): «Не короче 8 символов»
├─ 24
├─ Button.Primary.Tall «Войти» / «Создать аккаунт»   52h            ← THE one lit element here
├─ 12
├─ Button.Text «Забыли пароль?»           40h
├─ Button.Text «Войти по коду из письма»  40h   → panel Code
└─ TextBlock #errorLine  Brush.RedText 12, present in the markup, empty by default
```

**Panel 3 - `Code`** - six `TextBox.Field` cells 48x56 with `Radius.Chip` 12, 8 apart, auto-advancing,
paste-aware (pasting six digits fills all six), `Backspace` steps back, `Enter` submits. Title
«Код из письма», Body «Отправили шестизначный код на a@b.ru.», `Button.Primary.Tall` «Подтвердить»,
`Button.Text` «Отправить ещё раз» with a 60-second cooldown rendered as «Отправить ещё раз через
0:47» in `Font.Numeric`.

**Panel 4 - `AwaitingTelegram`** - the confirmation wait. A 64px ring: static
`Brush.OutlineVariant` track plus a `Brush.Accent` arc spinning at 1.2s, with the brand shield inside
at 30px. Title «Ждём подтверждения», Body «Откройте бота и нажмите «Подтвердить».»,
`Button.Primary.Tall` «Открыть Telegram», `Button.Text` «Начать заново», `Button.Text` «Другой способ
входа». On success the arc completes into a `Geo.State.Check` over 220ms, then the page hands off.
The breathing plane animation is deleted; the arc alone carries the wait.

**Panel 5 - `PendingEmail`** - Title «Проверьте почту», Body «Отправили ссылку для входа на a@b.ru.
Откройте её на этом устройстве.», an 18px arc, `Button.Text` «Отправить ещё раз», `Button.Text`
«Назад».

#### 7.21.2 States

Idle · focused (2px ring) · submitting (the CTA's label swaps for a 20px arc, **the button keeps its
exact size so nothing reflows**, and it is disabled) · field error (inline under the field, red 1px
border, focus moves to the first invalid field) · form error (the `#errorLine` above the CTA, never at
the bottom of the scroll) · locked out («Слишком много попыток. Повторите через 5 минут.») · offline
(«Нет сети. Проверьте подключение и повторите.» with every submit disabled) · success (the hand-off).

#### 7.21.3 Motion

**Nothing on entry.** The one exception in the entire product is the hand-off out of this screen to
Главная: `Dur.Slow` 450ms `Ease.OutExpo`, already tokenised and reserved for exactly this. Panel
changes are 220ms crossfades. Under `.lite` everything snaps.

#### 7.21.4 What gets deleted

The two-block z-stack with 20 buttons; the local `Geo.Login.Back` copy; the local
`Button.SegItem` class; the four hand-rolled spinner `Ellipse`s with `StrokeDashArray="6.9,20.8"`
re-declared in one file; `CornerRadius="8"` on `SegItem` and `SoonPill`; `FontSize="20"` on the code
digits; the off-scale margins 14 / 20 / 28 / 40 / 3; the permanently disabled Google «Скоро» button;
and `Brush.HomeGradient` as the page background.

#### 7.21.5 Acceptance

- [ ] Four controls visible on first paint, one of them filled accent
- [ ] Telegram is first and is the filled control
- [ ] Every panel has exactly one filled accent control
- [ ] Errors appear under their field; the form error appears above the CTA
- [ ] The helper slot exists in the markup even when empty; nothing jumps
- [ ] The submit button does not change size while loading
- [ ] Completable with the keyboard alone, including the six code cells
- [ ] Flat `Brush.Bg`; no gradient
- [ ] Under 350 lines of AXAML

---

### 7.22 Онбординг - the first frame

**File:** `Views/OnboardingView.axaml` (238 ln) + `.axaml.cs` (213 ln) →
`Views/Auth/OnboardingPage.axaml`. **RESTYLE.**

It is already close: one accent, one tonal, one demoted link, correct rhythm. Three changes.

```
Border  Background Brush.Bg                                    ← was Brush.HomeGradient
└─ ScrollViewer → Panel Margin 16,0 MinHeight={Scroll.Bounds.Height}
   └─ StackPanel MaxWidth Size.Form 480  VerticalAlignment=Center
      ├─ Border 64x64 r20 Brush.Tile.Blue → shield 30
      ├─ 16   TextBlock «departament»  Title 16/700 Font.Grotesk
      ├─ 24   TextBlock.Headline  «Добавьте подписку»          ← was Display 34
      ├─ 8    TextBlock.Body «Отсканируйте QR-код или вставьте ссылку из буфера. Доступ появится
      │        сразу.»   MaxWidth 380
      ├─ 32   Button.Primary.Tall  «Добавить по QR-коду»    52h
      ├─ 12   Button.Tonal.Tall    «Добавить из буфера»     52h
      ├─ 24   two hairlines with «или войдите в аккаунт» between them, Caption
      ├─ 16   Button.Tonal.Tall    «Войти через Telegram»   52h
      ├─ 12   Button.Text          «Войти через сайт»       40h
      └─ 12   Button.Text          «Восстановить из копии»  40h   ← NEW third path
```

Changes: flat background; the headline drops from `Display` 34 to `Headline` 24, because `Display` is
reserved for one live figure per screen and «Добавьте подписку» is not a figure; and the third path
is added, because a returning user who reinstalled has neither a QR code nor a wish to sign in again
and today has no route at all. The locally declared `Button.Tonal.Tall` override is deleted once the
class is global.

---

### 7.23 Синхронизация - the post-login gate

**File:** `Views/AccountSyncView.axaml` (176 ln) + `.axaml.cs` (324 ln) →
`Views/Auth/AccountSyncPage.axaml`. **KEEP**, with the background fixed.

This is the strongest state surface in either app and it is not touched beyond two things: the
`Brush.HomeGradient` becomes `Brush.Bg`, and the misnamed key `Account_SyncSubtitle` (which carries a
*stage* string, not a subtitle) is renamed `Account_SyncStageSubscriptions`.

Kept verbatim: the 64px ring (static `Brush.OutlineVariant` track plus a spinning `Brush.Accent` arc,
`StrokeDashArray="16.75,50.25"`) around the brand shield; the live stage caption crossfading through
«Проверяем аккаунт» → «Загружаем подписки…» → «Обновляем серверы»; the failure state that
crossfades **in place** to a red alert ring, «Не удалось синхронизировать», «Проверьте соединение и
попробуйте снова.», `Button.Primary` «Повторить» and `Button.Tonal` «Войти заново»; and the success
settle (1.0 → 1.04 → 1.0) before the shell crossfades to Главная.

It also serves the **cold-start loading** case (3.9): same ring, same shield, caption «Загружаем
данные», no stage list. That is what stops a returning user from seeing the sign-in gate for one
frame.

---

## 8. The upstream stratum: 15 windows, converted or deleted

**The single most damaging fact about the desktop client:** right-click any server, choose
«Изменить», and a 900x600 OS-decorated window opens with Chinese-origin resource strings, stock Semi
controls, a `TabControl`, 54 unstyled `TextBox`es and two `Width="100"` buttons centred at the
bottom. That is `AddServerWindow.axaml` (1 388 ln, 94 `resx:` references, 87 controls, **zero** Incy
classes), reached from `ServerListView.axaml:154` → `.axaml.cs:780` →
`ProfilesViewModel.EditServerAsync()` → `ServiceLib/ViewModels/ProfilesViewModel.cs:527`. The 2026
redesign stops dead at that click.

`AddServerWindow` and `OptionSettingWindow` alone hold **161 controls, a third of the desktop
client**, and not one of them uses an Incy style. Together the stratum is 22 of 49 views and 289 of
483 controls.

**The rule after this plan: nothing reachable is unstyled, and nothing unreachable survives.**

| Window | Lines / `resx:` | Verdict | Becomes |
|---|---|---|---|
| `AddServerWindow.axaml` | 1 388 / 94 | **REBUILD** | `servers/editor/{id}` sub-page (8.1) |
| `AddServer2Window.axaml` | 160 / 14 | **REBUILD, merged** | the «Свой конфиг» mode of the same sub-page |
| `AddGroupServerWindow.axaml` | 258 / 32 | **REBUILD, merged** | the «Группа» mode of the same sub-page |
| `SubEditWindow.axaml` | 272 / 29 | **REBUILD** | `servers/provider-editor/{id}` sub-page (8.2) |
| `RoutingRuleSettingWindow.axaml` | 259 / 27 | **REBUILD, merged** | `settings/routing/ruleset/{id}` (7.9) |
| `RoutingRuleDetailsWindow.axaml` | 263 / 16 | **REBUILD, merged** | the inline rule editor on that page (7.9) |
| `ProfilesSelectWindow.axaml` | 129 / 12 | **REBUILD** | `PickerFlyout`, a component, not a window (8.3) |
| `FullConfigTemplateWindow.axaml` | 197 / 15 | **REBUILD** | `settings/core/advanced` (7.16) |
| `GlobalHotkeySettingWindow.axaml` | 133 / 11 | **REBUILD** | `settings/hotkeys` (7.17) |
| `OptionSettingWindow.axaml` | 1 206 / 91 | **DELETE** | its ~10 unique controls migrate to `settings/core`, `settings/bypass`, `settings/ping` (7.11, 7.13, 7.16) |
| `SubSettingWindow.axaml` | 82 / 16 | **DELETE** | the provider list is the Серверы destination's provider pane (6.4) |
| `QrcodeView.axaml` | 31 / - | **REBUILD** | `Dialogs/QrDialog.axaml` (9.2) |
| `SudoPasswordInputView.axaml` | 66 / - | **REBUILD** | `Dialogs/SudoDialog.axaml` (9.3) |
| `MsgView.axaml` | 104 / - | **REBUILD** | `settings/core/log` (7.16) |
| `JsonEditor.axaml` | 26 / - | **KEEP as a control, RESTYLE its chrome** | used by `settings/core/advanced` and the editor's raw mode |

### 8.1 `servers/editor/{id}` - the server editor

**The highest-value single conversion in the project.** One sub-page replaces three windows.

**Android counterpart:** the nine `activity_server_*.xml` layouts plus their three shared
`<include>` blocks (`layout_address_port.xml`, `layout_transport.xml`, `layout_tls*.xml`), which have
the identical problem. One `ServerEditorRow` component applied to those three includes fixes nine
Android screens at once, and the desktop page below is the shape both platforms build to.

```
SubPage  Title «Изменить сервер» / «Новый сервер»
         trailing Button.IconButton40 Geo.Action.More → kebab
         MaxWidth Size.Content 720
└─ StackPanel Margin 16,8,16,32

├─ Border.Card Padding 16                       ← IDENTITY, always visible
│    ServerIcon 40 + TextBox.Field «Название»  (label above, the remark)
│    Border.ProtocolChip «VLESS»                (read-only; the protocol is chosen below)
│
├─ 24  SectionHeader «Протокол»
├─ Border.Card Padding 0
│    Border.Row «Протокол»   value «VLESS»   chevron → PickerFlyout
│      (VLESS · VMess · Trojan · Shadowsocks · SOCKS5 · HTTP · WireGuard · Hysteria2 · TUIC ·
│       Свой конфиг · Группа)
│
├─ 24  SectionHeader «Адрес»
├─ Border.Card Padding 0
│    TextBox.Field «Адрес»            helper «Домен или IP»
│    TextBox.Field.numeric «Порт»     helper «1-65535»
│    TextBox.Field «Идентификатор»    protocol-dependent label: UUID / пароль / метод + пароль
│
├─ 24  SectionHeader «Транспорт»
├─ Border.Card Padding 0
│    Border.Row «Транспорт»  value «TCP»  chevron → PickerFlyout (TCP · WS · HTTP/2 · gRPC · QUIC · KCP · HTTPUpgrade · XHTTP)
│    fields revealed by transport, at Dur.Reveal 300, never popped in:
│      WS      → «Путь», «Хост»
│      gRPC    → «Имя сервиса», «Режим»
│      HTTP/2  → «Путь», «Хост»
│      KCP     → «Seed», «Маскировка»
│
├─ 24  SectionHeader «Шифрование»
├─ Border.Card Padding 0
│    Border.Row «TLS»  segment «Нет» / «TLS» / «Reality»
│    revealed by choice:
│      TLS     → «SNI», «ALPN», «Отпечаток», switch «Разрешить небезопасное»
│      Reality → «SNI», «Публичный ключ», «Short ID», «SpiderX», «Отпечаток»
│
├─ 24  SectionHeader «Дополнительно»   (collapsed by default, rotating chevron)
├─ Border.Card Padding 0
│    «Sniffing» switch · «Mux для этого сервера» switch · «Заметка» multiline
│
└─ docked bottom bar, 72px, Brush.Bg, 1px top hairline
     Button.Text «Проверить»  (runs a real-delay probe against the edited values, inline result)
     spacer
     Button.Tonal «Отмена»    Button.Primary «Сохранить»   both 48h, 120 min width
```

**Rules that make this a Departament screen rather than a form dump:**

- **No `TabControl`.** Sections in one scroll, with headers. A tab strip in an editor hides half the
  object being edited and is what makes the current window feel like a different application.
- **Progressive disclosure by protocol and transport.** A VLESS-over-TCP-with-Reality server shows
  eleven fields, not fifty-four. The revealed fields animate in at `Dur.Reveal` 300ms.
- **Label above, helper below, always present in the markup.** Validation on blur. Error text below
  the field in `Brush.RedText` 12 with a red 1px border and a specific message: «Укажите адрес»,
  «Порт должен быть числом от 1 до 65535», «Неверный формат UUID».
- **Save is disabled until the form is valid**, and shows an inline arc while saving. After a failed
  submit, focus moves to the first invalid field.
- **The kebab** carries: «Показать как ссылку» (a read-only `TextBox.Field` with a copy button),
  «Показать QR-код», «Открыть в редакторе JSON» (`JsonEditor`, the raw mode that replaces
  `AddServer2Window`), hairline, «Удалить сервер» in `Brush.RedText`.
- **The «Группа» mode** (replacing `AddGroupServerWindow`) swaps the Адрес and Транспорт sections for
  a member list: `Border.Row` per member with a drag handle, a `PickerFlyout` to add, and a
  «Стратегия» row (`По очереди` / `Случайно` / `Наименьшая задержка`).
- **Unsaved changes** are guarded: Escape and back raise `Отменить изменения?` /
  `Введённые данные не сохранятся.` / `Продолжить редактирование` + `Отменить` (destructive).
  This is one of the three legitimate confirmation dialogs in the product.
- Keyboard: `Tab` walks fields in visual order, `Enter` in any single-line field moves to the next,
  `Ctrl+S` saves, `Esc` cancels with the guard.

**States:** new (all defaults, title «Новый сервер») · editing · validating · saving · save failed
(error strip plus the field-level errors) · probing (the «Проверить» button shows an arc, then a
result chip «48 мс» in `.green` or «Не отвечает» in `.red`) · protocol not supported by the selected
core (an inline warning row: «Xray не поддерживает Hysteria2. Переключите ядро в настройках.» with a
`Button.Text` linking to `settings/core`).

### 8.2 `servers/provider-editor/{id}` - the provider editor

Replaces `SubEditWindow.axaml` (272 ln, 29 `resx:`).

```
SubPage Title «Провайдер»   MaxWidth Size.Content 720
├─ Border.Card Padding 16
│    TextBox.Field «Название»     helper «Не длиннее 25 символов»   ← the protocol cap, in the view too
│    TextBox.Field «Ссылка»       helper «https://…»    multiline, wraps, monospaced via Font.Numeric
├─ 24  SectionHeader «Обновление»
├─ Border.Card Padding 0
│    «Автообновление»           switch
│    «Интервал»                 unfold «6 ч»                  visible when on
│    «User-Agent»               value, rotating chevron → field
│    «Обновлять при запуске»    switch
├─ 24  SectionHeader «Фильтр»
├─ Border.Card Padding 0
│    TextBox.Field «Фильтр серверов»   helper «Регулярное выражение. Пустое поле берёт все серверы»
│    Caption showing a live match count: «Подходит 84 из 147 серверов»
├─ 24  SectionHeader «Прокси для обновления»
├─ Border.Card Padding 0
│    Border.Row «Через сервер»  value «Не использовать»  chevron → PickerFlyout (8.3)
└─ docked bottom bar: Button.Text «Обновить сейчас» · Button.Tonal «Отмена» · Button.Primary «Сохранить»
```

### 8.3 `PickerFlyout` - the component that kills `ComboBox`

Replaces `ProfilesSelectWindow.axaml` (an 800x450 **window** for choosing one item from a list,
called from three places) and all 66 stock `ComboBox` instances.

```
Flyout (IncyFlyoutTheme)  MinWidth 280  MaxWidth 420  MaxHeight 400
└─ DockPanel
   ├─ [Top] TextBox.Field.search  Height 40   shown only when the list exceeds 8 items
   └─ ScrollViewer → virtualised list of Border.Row.selectable
        Border.Tile 40 (optional)  ·  Title  ·  Subtitle  ·  20px Geo.State.Check when selected
```

Opens anchored to the invoking row, `Dur.Reveal` 300ms. `Up` / `Down` move, `Enter` selects and
closes, `Esc` closes and returns focus to the row, typing filters. The invoking row shows the chosen
value immediately, per the affordance grammar.

### 8.4 What migrates out of `OptionSettingWindow` before it is deleted

The window is unreachable today (`MainWindowViewModel.cs:726` has no UI binding), so these are
features that exist in the engine and have **no** UI at all:

| Feature | New home |
|---|---|
| Core selection (Xray / sing-box) | `settings/core` › Ядро |
| Log level, log enable | `settings/core` › Уровень журнала, and `settings/core/log` |
| SOCKS5 and HTTP inbound ports, LAN exposure | `settings/core` › Порты |
| Speed-test URL and timeout | `settings/ping` › Параметры |
| Mux concurrency, protocol scope | `settings/bypass` › Мультиплексирование |
| Fragmentation packets, length, interval | `settings/bypass` › Фрагментация |
| Sniffing, route-only sniffing | `settings/bypass` and per-server in the editor |
| Auto-update check on launch | `settings/update` › Проверять автоматически |
| Clipboard and URL-scheme import toggles | `settings/urlschemes` |
| Custom DNS per core (raw JSON) | `settings/core/advanced` |

Nothing is dropped silently. Anything not in that table and not otherwise specified in this document
is deliberately not migrated, and the decision is recorded in section 12.

---

## 9. Dialogs and windows

After this plan the desktop client has **three** modal windows, down from fifteen-plus. Everything
else is a sub-page, a flyout or an inline panel, per `00-rules.md` 7.6: inline > expandable row >
flyout > dialog.

### 9.1 `Dialogs/MessageDialog.axaml` - the one confirmation

**File:** `Views/MessageBoxDialog.axaml` (74 ln). **KEEP**, minus the shadow.

```
Window  WindowDecorations=None  SizeToContent=WidthAndHeight  Background=Brush.Scrim
└─ Border.Card  Width Size.Form 480  Padding 24  r20  1px Brush.OutlineVariant  NO BoxShadow
   ├─ TextBlock.Title      the question IS the title: «Удалить подписку?»
   ├─ 8
   ├─ TextBlock.Body       the consequence: «Все её серверы исчезнут из списка.»  MaxWidth 400
   ├─ 24
   └─ StackPanel Orientation=Horizontal HorizontalAlignment=Right Spacing 12
        Button.Tonal «Отмена»          48h, min width 120, auto-focused
        Button.Destructive «Удалить»   48h, min width 120
```

- The buttons **say what they do**. Never «OK», never «Да» / «Нет».
- Destructive on the right, neutral on the left, and the neutral one takes focus, so `Enter` on a
  dialog nobody read cancels rather than destroys.
- `Esc` cancels. The scrim click cancels. Focus is trapped inside and returns to the trigger on
  close.

**The only four dialogs that exist after this plan**, because each is genuinely irreversible and
costly: delete a provider (and its servers), restore a backup (replaces everything and restarts),
discard unsaved editor changes, sign out. Everything else is undo through the status strip.

### 9.2 `Dialogs/QrDialog.axaml` - QR share

**File:** `Views/QrcodeView.axaml` (31 ln). **REBUILD.** Today it is 25 lines of raw upstream inside
our `DialogHost`: a 400x400 `Image` and a read-only `TextBox` with legacy `Margin8` resources, no
card, no title, no copy button, no tokens.

```
Window  as 9.1
└─ Border.Card  Width Size.Form 480  Padding 24
   ├─ Grid: TextBlock.Title «QR-код сервера»  ·  Button.IconButton40.Row Geo.Action.Close
   ├─ 16
   ├─ Border  320x320  Background #FFFFFF  r12  Padding 16     ← the QR needs a white quiet zone;
   │    └─ Image 288x288                                          this is the one white surface in
   │                                                              the dark theme and it is functional
   ├─ 16
   ├─ TextBlock.Body  the server name, centred, ellipsised
   ├─ 8
   ├─ TextBox.Field  read-only, the link, selectable, 3 lines max, Font.Numeric 13
   ├─ 16
   └─ StackPanel Orientation=Horizontal Spacing 12
        Button.Tonal «Скопировать ссылку»   Button.Tonal «Сохранить изображение…»
```

Also used for the subscription link (7.2) with the title «QR-код подписки».

### 9.3 `Dialogs/SudoDialog.axaml` - elevation on Linux

**File:** `Views/SudoPasswordInputView.axaml` (66 ln). **REBUILD.** Today it uses
`Theme="{DynamicResource CardBorder}"`, `resx:ResUI.TbConfirm` / `TbCancel`, and `Width="100"`
buttons. **Linux users see this on the first TUN start**, so it is a first-run screen wearing
somebody else's chrome.

```
Window as 9.1
└─ Border.Card  Width Size.Form 480  Padding 24
   ├─ TextBlock.Title  «Нужны права администратора»
   ├─ 8
   ├─ TextBlock.Body   «Режим «весь трафик» создаёт сетевой интерфейс. Введите пароль пользователя.»
   ├─ 24
   ├─ field  label Caption «Пароль»   TextBox.Field  PasswordChar, eye toggle, Enter submits
   │         helper slot; on failure «Неверный пароль» in Brush.RedText
   ├─ 16
   ├─ Border.Row  «Запомнить на эту сессию»  +  ToggleSwitch.iOS
   ├─ 24
   └─ Button.Tonal «Отмена»  ·  Button.Primary «Продолжить»
```

The password is never logged, never echoed to the status strip, and the field is cleared on close.

### 9.4 Flyouts, in full

| Flyout | Anchor | Contents |
|---|---|---|
| Server actions | a server row | 7 items + 1 destructive (6.6) |
| Provider actions | a provider row or group header | 6 items + 1 destructive (6.4) |
| Subscription actions | the subscription card's kebab | 2 items (7.1.3) |
| Sort | the sort button | 3 selectable rows |
| Add | the add button | 4 items |
| Top-up | «Пополнить» | amount field, error line, method rows, CTA (7.1.2) |
| Link e-mail | «Добавить» on the e-mail row | field + CTA |
| Picker (generic) | any row with a chevron that chooses one of many | 8.3 |
| Log actions | the log kebab | 3 items + 1 destructive |

All of them: `Esc` closes, focus enters on open and returns to the anchor on close, `Dur.Reveal`
300ms in and 225ms out, and none of them carries a shadow.

---

## 10. Dead code: eleven files, eleven explicit decisions

`02-inventory-pc.md` 4.7 lists eleven built, styled, compiled and unreachable surfaces. Dead surfaces
are not neutral: they rot, they confuse search, and they are where inconsistency hides
(`03-direction.md` 1.8, F15). Every one gets a decision in the same change.

| File | Lines | Decision |
|---|---|---|
| `ServersView.axaml` | 12 | **DELETE.** An orphan wrapper around `ServerListView` with no reference anywhere |
| `CompactServersView.axaml` | 116 | **HARVEST then DELETE.** It contains the app's only search field (`:90`, bound to `Profiles.ServerFilter`). The binding moves to `ServersPage`'s toolbar (6.3) in the same commit that deletes the file |
| `ProfilesView.axaml` | 322 | **DELETE.** Registered in `SimpleViewLocator:29`; `ProfilesViewModel` is never shown as a dialog; its interaction handlers were already re-implemented in `ServerListView.axaml.cs:71-133` |
| `ClashProxiesView.axaml` | 158 | **DELETE.** Mihomo/Clash proxy-group control is absent from the product and no owner request covers it. If it is ever wanted, it is a new destination, not a resurrected view |
| `ClashConnectionsView.axaml` | 104 | **DELETE**, with a note: a live connection list is a real desktop expectation, and it is recorded as a **future** feature in section 12, not as dead code kept "just in case" |
| `ThemeSettingView.axaml` | 67 | **DELETE.** Superseded by Настройки › Интерфейс |
| `CheckUpdateView.axaml` | 95 | **REBUILD and WIRE** as `settings/update` (7.19) |
| `BackupAndRestoreView.axaml` | 213 | **DELETE.** Superseded by `settings/backup`; its WebDAV panel is explicitly not migrated |
| `ProviderSettingsPage.axaml` | 138 | **WIRE and RESTYLE** as `settings/provider` (7.12) |
| `StatusBarView.axaml` | 125 | **REFACTOR.** It is mounted at `Width=0 Height=0 Opacity=0` (`MainWindow.axaml:643-651`) purely to keep its interaction handlers and `StatusBarViewModel` alive. The handlers move to the shell; the phantom view is deleted |
| `MsgView.axaml` | 104 | **REBUILD** as `settings/core/log` (7.16) |

Net: **six deletions, three rebuilds-and-wires, one harvest, one refactor.** After this section the
project has zero unreachable views, and that becomes a standing check: a view with no route and no
call site is a defect.

---

## 11. Parity with Android, screen by screen

Read this table with `32-master-plan-android.md` open. **Same concept** means the two clients render
the same information in the same order with the same words; **native expression** is how the platform
draws it; **deliberate difference** is a divergence with a reason, and anything not listed as
deliberate is a drift to be fixed.

| Surface | Android | Desktop | Same concept | Native expression | Deliberate difference |
|---|---|---|---|---|---|
| Navigation | Bottom nav, 4 items, labels always visible | Rail 76 at >= 760, compact bar below | Same 4 destinations, same order, same labels, same current-marking on two channels | Bottom bar vs left rail; the rail is the desktop's fixed-edge target | Desktop adds `Ctrl+1..4` and `Ctrl+Tab`; the rail collapses to 0 and Android's bar does not |
| Window chrome | none | 32px caption, 3 buttons, drag, 8-zone resize, in-app zoom | n/a | n/a | Desktop-only, entirely |
| Главная | Connect object, status line, gate line, stats, 3 ledger rows | identical set | Yes, item for item | Two panes above 980px content width | Android never splits; the desktop hover state on the disc; `Ctrl+Enter` |
| Connect control | 176dp disc, 200 ring, arc, one sonar | 176px disc, 200 ring, arc, one sonar | Yes, same layer count, same states, same 600ms hero | Same | Haptic on Android; hover on desktop |
| Серверы | Search, sort, grouped list, per-item sheet | Search, sort, grouped list **plus a provider pane at width**, per-item flyout | Same row, same grouping, same 7 actions, same empty states | Sheet vs flyout; pane vs single column | Desktop adds multi-select, right-click, keyboard list navigation, `Ctrl+P`; Android does not offer them |
| Server row | flag tile 28 in a 40 slot, name, protocol chip, transport, ping | identical | Yes | Same | Desktop's kebab appears on hover; Android's opens by long-press |
| Server editor | 9 activities to be unified around 3 shared includes | one `servers/editor` sub-page | Same sections, same progressive disclosure, same validation copy | Activity vs sub-page | none |
| Настройки | 4 groups, same 20 rows, same values, same affordance grammar | identical | Yes, row for row | Same | Desktop adds settings search; Android's is a future item |
| Settings sub-pages | 14 activities | 14 sub-pages | Same content, same order, same copy keys | Activity vs sub-page | Desktop's `settings/hotkeys` has no Android counterpart (no global hotkeys on Android); Android's per-app list reads installed packages, desktop's reads `.exe` paths |
| Аккаунт | hero card, subscription slot, sign-in methods, management, sign-out | identical | Yes, zone for zone | Same | Android hides the tab when signed out **today**; this plan makes both show the gate inside the tab |
| Subscription | one card, six states | one card plus a sub-page for the two purchase flows | Same six states, same chips, same copy | Sheet vs sub-page for upgrade and add-devices | Desktop has a sub-page; Android uses `PaymentMethodSheet` and a stepper dialog |
| Покупка | tariff cards, price options, checkout, payment picker | identical, picker inline | Same anatomy, same total line, same copy | Bottom sheet vs inline rows | The picker surface only |
| Устройства | one divided list, undo unlink | identical | Yes | Same | none |
| История | one divided list, status chips | identical + a refresh button | Yes | Swipe-to-refresh vs `F5` and a button | The refresh affordance only |
| Вход | 5 panels, one filled accent per panel | identical | Yes, panel for panel | Same | Desktop's code cells accept paste; `Esc` steps back |
| Онбординг | shield, 2 CTAs, divider, 2 links | identical | Yes | Same | none |
| Синхронизация | ring, stage line, failure with two exits | identical | Yes | Same | none |
| Feedback | Snackbar above the bottom nav, 48dp, one action | Status strip docked above the bar or at the content bottom | Same anatomy, same severities, same persistence rules, same copy | Snackbar vs docked strip | Desktop's is docked and never floats; Android's is a Snackbar because that is the platform component |
| Log | Settings › Ядро и журнал › Журнал | identical | Yes | Same | Desktop's text is selectable and copyable |
| Dialogs | Material dialog, themed | modal window, same layout | Same four dialogs, same copy, same button order | Material vs `Border.Card` on a scrim | none |
| Tray / notification | ongoing notification: state, server, two actions | tray icon, tooltip, 6-item menu | Same facts, same words | Notification vs tray | Desktop-only |

**Known parity gaps, logged rather than silently different** (`00-rules.md` 13):

1. Global hotkeys exist on desktop only. Android has no equivalent and none is planned.
2. Multi-select on the server list is desktop-only.
3. In-app UI zoom is desktop-only; Android uses the system font scale, which desktop must also honour
   through DPI.
4. The provider pane on Серверы is a desktop layout at width; Android renders the same providers as
   sticky group headers at every size.
5. Per-app proxy targets packages on Android and executables on desktop. Same page, same copy,
   different unit, and the subtitle says which.

---

## 12. Decisions

### 12.1 Taken here, inside existing law

- **PC-A.** Gradients and glows are **replaced, not amended**: `Brush.HomeGradient`,
  `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner` and `Nav.Scrim` are deleted.
- **PC-B.** Servers is a **destination**, making four on both platforms.
- **PC-C.** The sub-page stack is **per destination**, and every route has a stable id.
- **PC-D.** The feedback channel is a **docked status strip** plus a **log page**. `Border.Toast` is
  deleted.
- **PC-E.** Escape, `Alt+Left` and mouse button 4 all pop the stack.
- **PC-F.** The horizontal subscription **carousel is deleted** and replaced by a list; the
  four-panel flyout wizard becomes a sub-page.
- **PC-G.** Bottom sheets do not exist on desktop; `BuyView`'s becomes inline rows.
- **PC-H.** One press language (`scale(0.97)`), one hover language (`Brush.Hover` at 150ms), one
  icon-button system (`IconButton40`), one geometry dictionary, one field theme, one nav-item class.
- **PC-I.** `ComboBox` is removed from the product in favour of a row plus `PickerFlyout`.
- **PC-J.** Six dead views are deleted, three are wired, one is harvested, one is refactored
  (section 10).
- **PC-K.** Every reachable surface speaks `Common/L.*.cs`; `resx:ResUI` disappears from the desktop
  UI entirely.
- **PC-L.** Connected state: the **shield** fills accent, the **ring** returns to neutral, the
  **status line** carries a green dot and the word. This resolves the `03-direction.md` 10.2 versus
  `30-reference-analysis.md` 2.3 divergence in favour of the direction.

### 12.2 Needs an owner decision, in `00-rules.md` section 18 row format

| Date | Decision | Rule affected |
|---|---|---|
| pending | **PC-D1.** The desktop destination set becomes four (Главная · Серверы · Настройки · Аккаунт), matching Android, and `Nav_Servers` is added to `Common/L.Shell.cs` | 13, 7.7 |
| pending | **PC-D2.** `Brush.HomeGradient` and `Brush.ConnectGlow` are deleted rather than exempted; the brand layer on both clients is the surface ramp, the single accent and the figure face | 1.4.3, 6.5 |
| pending | **PC-D3.** The default window is 1080x720 in wide mode with a 900x600 wide floor; compact mode is an explicit user choice with a 380x620 floor, and `00-rules.md` 12.3's "minimum window 900x600" is read as "every view must be usable at 900x600", which compact mode also satisfies at its own floor | 12.3 |
| pending | **PC-D4.** Buttons are a rounded rectangle at radius 16 on both platforms, not a pill. The owner's recorded rejection of capsules (`GlobalStyles.axaml:2-16`) outranks the rule, so the rule changes | 3.2 |
| pending | **PC-D5.** A field-height token is added: `Size.Field` 48 on desktop, `@dimen/field_height` 48dp on Android, and input radius is `Radius.Chip` 12 on both. `Radius.Search` 14 is deleted | 3.2, 3.3 |
| pending | **PC-D6.** Button label type is 14 (Bold on filled, Medium elsewhere) on both platforms; the desktop's 15 and Android's `textAppearanceLabelLarge` both go | 3.4 |
| pending | **PC-D7.** `Font.Ui` is introduced as a separate token from `Font.Grotesk` on both platforms, and `Font.Grotesk` is never applied to a Russian string. This is the desktop half of `03-direction.md` D-1 and D-2 and cannot ship without them | 5.1, 3.4 |
| pending | **PC-D8.** `Brush.Accent`, `Brush.OnAccent`, `Brush.Tile.*`, `Brush.SelectedFill` and `Brush.StatusChip.*` move inside `ResourceDictionary.ThemeDictionaries`; the light-theme accent becomes `#1E5FC7` (5.97:1) instead of `#4C8DFF` (2.98:1) | 3.5, 14.1 |
| pending | **PC-D9.** Clash/Mihomo proxy-group control and the live connection list are **not** features of this product in 2026. `ClashProxiesView` and `ClashConnectionsView` are deleted. If either is wanted later it is a new destination with its own spec | n/a, records a deletion |
| pending | **PC-D10.** WebDAV backup is not a feature; `BackupAndRestoreView` is deleted without migration | n/a |

Nothing in 12.2 is implemented until the row is pasted into `00-rules.md` section 18 and the rule body
is updated there.

### 12.3 Recorded as future, not built

Written down so nobody rediscovers them as "missing":

- A command palette (`Ctrl+K`). The shortcut is reserved and unbound.
- A live connection list (what Clash's connections view would have been), as a destination under
  Настройки › Ядро и журнал.
- Per-server routing overrides beyond the Mux and sniffing switches already in the editor.
- Multi-window (a detached log or server list).
- macOS menu-bar integration beyond the tray.

---

## 13. Implementation sequence

Ten waves. The order is dependency-driven, not importance-driven: every wave leaves the app shippable,
and no wave asks a screen to be redesigned twice. Each wave has an exit criterion that is checkable
without judgement.

### Wave 0 - Unblock (before any UI work)

| # | Task | Files |
|---|---|---|
| 0.1 | Get the pending owner decisions signed: PC-D1 (four destinations), PC-D2 (delete the gradients), PC-D3 (window sizes), PC-D4 (radius 16), PC-D7 (`Font.Ui`), plus `03-direction.md` D-1 and D-2 | `00-rules.md` 18 |
| 0.2 | Choose and vendor the Cyrillic UI face; verify it renders the longest Russian string in the product at 11, 13, 14, 16, 24 and 34 | `Assets/Fonts/` |
| 0.3 | Re-measure the two contrast pairs that were hand-computed and never verified: `Brush.OnSurfaceVariant` on `Brush.SurfaceHigh`, and every pair in the light theme after the accent moves into the theme dictionaries | - |

**Exit:** the six decision rows are in `00-rules.md` 18 and the UI face is in the repo.

### Wave 1 - The token and file foundation

Nothing visible changes. Everything after this wave depends on it.

| # | Task | Files |
|---|---|---|
| 1.1 | Split `GlobalResources.axaml` into `Assets/Tokens.axaml`, `Assets/Icons.axaml` and `Assets/Themes/{Dark,Light,Mono}.axaml`. Move the mono overlay out of `App.axaml.cs:580 BuildMonoOverlay` and delete that function | `Assets/`, `App.axaml.cs` |
| 1.2 | Move `Color.Accent`, `Brush.Accent`, `Brush.OnAccent`, `Brush.Tile.*`, `Brush.SelectedFill`, `Brush.StatusChip.*` inside `ThemeDictionaries`; set the light accent to `#1E5FC7` | `Assets/Themes/` |
| 1.3 | Delete `Brush.HomeGradient`, `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`, `Nav.Scrim`. Point all six consumers at `Brush.Bg` | `Assets/`, 6 views |
| 1.4 | Split `GlobalStyles.axaml` into `Assets/Styles/{Text,Buttons,Rows,Inputs,Overlays,Shell,Motion}.axaml`. Move `MainWindow`'s 260 lines of chrome styles into `Shell.axaml` | `Assets/Styles/`, `MainWindow.axaml` |
| 1.5 | Add `Size.Content` 720, `Size.Form` 480, `Size.PanePrimary` 300, `Size.Field` 48, `Size.ConnectFrame` 200. Delete `Radius.Search`. Move `Radius.Button` into `Tokens.axaml` | `Assets/Tokens.axaml` |
| 1.6 | Consolidate every `Geo.*` into `Icons.axaml`; delete the 40-plus local `StreamGeometry` declarations including the nine copies of `Geo.Sub.Back` | `Assets/Icons.axaml`, 24 views |
| 1.7 | Add `Font.Ui`; repoint the 16 `Font.Grotesk` setters in the style sheet; leave `Font.Grotesk` only on `Chip`, `Numeric`, the wordmark and the protocol chip | `Assets/Styles/Text.axaml` |
| 1.8 | Clear all 44 em-dashes and en-dashes from `Common/L.*.cs`; replace `DelayDisplayConverter`'s «-» with «нет ответа» | `Common/L.*.cs`, `ServerListView.axaml.cs` |
| 1.9 | Rename the view directories per 2.11 | `Views/**` |

**Exit:** the four mechanical greps in `00-rules.md` 1.5 return zero for inline hex, zero for
`StaticResource Brush.*` and zero for dashes; the app builds and looks unchanged except that five
gradients are gone; all three themes still switch live.

### Wave 2 - The component library

| # | Task |
|---|---|
| 2.1 | Write the 13 button classes; delete `Button.IconButton` (32), `LinkAction`, `OutlinedAccent`, and the 12 view-local button classes; delete the ten local `IconButton:pressed` re-declarations |
| 2.2 | Write `Border.Row` and its modifiers (`.selectable`, `.static`, `.pressed`), `Border.Tile`, `Border.Chip`, `Border.Meter`, `Border.Skeleton`; delete `Border.StatusChip`, `Border.TrafficPill`, `Border.PriceOption`, `Border.SearchPill`, `Border.AccountChip`, `Border.SheetTop`, `Border.SheetHandle`, `Border.Toast`, and the four view-local row classes |
| 2.3 | Write `TextBox.Field` with its `.search`, `.numeric` and `.error` modifiers; delete `TextBox.Incy`, `TextBox.IncyField` and the duplicate declaration inside `SettingsView.axaml` |
| 2.4 | Write `Views/Components/SubPage.axaml` (toolbar + back + title + one trailing slot + content slot with `MaxWidth`) |
| 2.5 | Write `PickerFlyout`, `EmptyState`, `ErrorState`, `SkeletonList`, `SearchField`, `MeterBar`, `ServerIcon` |
| 2.6 | Unify press to `scale(0.97)` with the one documented disc exception; unify hover to `Brush.Hover` at 150ms; add focus rings to every focusable class including `ToggleSwitch.iOS` |
| 2.7 | Add every new class to the `.lite` suppression block |

**Exit:** `grep -c '<Style Selector=' Views/**/*.axaml` is under 20; every control in the component
gallery renders correctly in dark, light and mono, at DPI 100 and 200, with hover, press, focus and
disabled all visible.

### Wave 3 - The shell

| # | Task |
|---|---|
| 3.1 | Four destinations in the rail and the compact bar; add `Nav_Servers`; merge `NavRailItem` and `BottomNavItem` into `Button.NavItem` |
| 3.2 | Caption buttons to 46x32 with names; rail toggle to 40x40 |
| 3.3 | Per-destination sub-page stacks; route ids; `Esc`, `Alt+Left`, mouse button 4 |
| 3.4 | The status strip: component, queue, severities, auto-dismiss, and rewire `DelegateSnackMsg` into it. Delete `snackHost` |
| 3.5 | Default window 1080x720; wide floor 900x600; compact floor 380x620 |
| 3.6 | Delete the phantom `StatusBarView` mount; move its handlers to the shell |
| 3.7 | The full shortcut table from 2.8 |
| 3.8 | Tray: identity line, six items, left-click shows, icon states |

**Exit:** the shell acceptance list in 3.12 is fully ticked; a message sent through
`NoticeManager` is visible to a user for the first time in the product's history.

### Wave 4 - The two screens the owner named

These come before the rest because they are the standing bar: «сейчас все выглядит плохо».

| # | Task |
|---|---|
| 4.1 | `auth/login` rebuilt as five panels (7.21) |
| 4.2 | `Онбординг` restyled: flat background, `Headline` not `Display`, the backup path (7.22) |
| 4.3 | `Главная` rebuilt as one `HomePage` with both modes; `ConnectControl` with five layers deleted; `StatsRow`; the three ledger rows (5) |
| 4.4 | `AccountSyncPage`: flat background, the renamed stage key, the cold-start variant (7.23) |

**Exit:** the first frame at launch and the sign-in screen both pass the nine-question slop test in
1.5, in all three themes, at 900x600 and 380x620.

### Wave 5 - Серверы

| # | Task |
|---|---|
| 5.1 | `ServersPage` with the two-pane and single-column layouts (6.2) |
| 5.2 | The toolbar: search harvested from `CompactServersView`, sort, ping-all, add (6.3) |
| 5.3 | `ProviderPane` with pin, and the provider flyout (6.4) |
| 5.4 | `ServerRow` with the unified server icon, the selection contract and the kebab (6.5) |
| 5.5 | Per-item actions: right-click, kebab, `Menu` key; delete with undo; multi-select (6.6) |
| 5.6 | `ProviderGroupHeader` rebuilt from `SubscriptionMetaView`: two 40px buttons, no local shrink, meter beside its label (6.7) |
| 5.7 | The operator-message component with its parser caps and hash-keyed dismissal |
| 5.8 | Delete `ServersView`, `CompactServersView`, `ProfilesView` |

**Exit:** a user can find, sort, edit, share, duplicate and delete a server, by mouse and by
keyboard, at 500 rows, without a single Semi-default control appearing.

### Wave 6 - The server and provider editors

| # | Task |
|---|---|
| 6.1 | `servers/editor/{id}` (8.1), absorbing `AddServerWindow`, `AddServer2Window`, `AddGroupServerWindow` |
| 6.2 | `servers/provider-editor/{id}` (8.2), absorbing `SubEditWindow` |
| 6.3 | `PickerFlyout` replaces `ProfilesSelectWindow` at all three call sites |
| 6.4 | Restyle `JsonEditor`'s chrome |
| 6.5 | Delete the four windows and `SubSettingWindow` |

**Exit:** `grep -rl 'resx:ResUI' Views/` returns nothing outside the three files scheduled for wave 8;
right-clicking a server and choosing «Изменить» stays inside the shell.

### Wave 7 - Настройки and its fourteen sub-pages

| # | Task |
|---|---|
| 7.1 | The hub: `MaxWidth`, four groups in the agreed order, values on every row, search (7.6) |
| 7.2 | Rebuild all eight existing sub-pages onto `SubPage`; delete the nine local chrome copies |
| 7.3 | New pages: `bypass`, `core`, `core/log`, `core/advanced`, `hotkeys`, `update` |
| 7.4 | Wire `ProviderSettingsPage` as `settings/provider` and add the provider-transparency section |
| 7.5 | `settings/routing` plus the depth-2 rule-set page with the inline rule editor (7.9) |
| 7.6 | Migrate the ten features out of `OptionSettingWindow`, then delete it (8.4) |
| 7.7 | Delete `ThemeSettingView`, `BackupAndRestoreView`, `CheckUpdateView` (rebuilt), `MsgView` (rebuilt) |

**Exit:** every setting the engine supports is reachable from the hub in at most two pushes; the
affordance grammar holds on all 20 hub rows and every sub-page row; settings search finds a row on a
sub-page and lands on it.

### Wave 8 - Аккаунт and commerce

| # | Task |
|---|---|
| 8.1 | `AccountPage` rebuilt: hero tightened, carousel deleted, flyout wizard deleted, Google row hidden, both fields on `TextBox.Field` (7.1) |
| 8.2 | `account/subscription/{id}` built (7.2) |
| 8.3 | `BuyPage`: sheet to inline rows, nested card removed, radius fixed, error colour fixed, success state given a forward path, purchase summary added (7.3) |
| 8.4 | `DevicesPage`: HWID out of the row, current-device wash removed, unlink to undo, scrim token (7.4) |
| 8.5 | `PaymentHistoryPage`: one skeleton component, `Border.EmptyIcon`, one card, refresh (7.5) |

**Exit:** the owner's "every button in the Account tab" review passes; exactly one filled accent
surface on the tab in every state; `AccountView.axaml` is replaced by two files each under 400 lines.

### Wave 9 - The remaining dialogs, and the sweep

| # | Task |
|---|---|
| 9.1 | `QrDialog`, `SudoDialog` rebuilt; `MessageDialog` shadow removed (9.1-9.3) |
| 9.2 | The full off-scale spacing sweep: 97 occurrences, 14 values, to zero |
| 9.3 | The glyph-size sweep: 10 sizes to 4 |
| 9.4 | Accessible names on the 18 unnamed icon-only controls |
| 9.5 | The state sweep: open every screen in section 15 of `00-rules.md`'s eleven states and fix what was skipped |
| 9.6 | The 200 percent sweep: every screen at DPI 200 and at UI zoom 200, at 900x600 and 380x620 |
| 9.7 | The three-theme sweep: every screen in dark, light and mono |

**Exit:** the pre-flight checklist in `00-rules.md` 16 is fully ticked for every screen, and every
screen scores at least 18/20 on the five `audit.native.md` dimensions with no dimension below 3.

### Wave 10 - Parity reconciliation

| # | Task |
|---|---|
| 10.1 | Diff every user-visible string between `Common/L.*.cs` and `res/values*/strings*.xml` for the same concept; fix the platform that drifted |
| 10.2 | Diff the settings group order, row order and default values between platforms |
| 10.3 | Diff the state matrix per screen |
| 10.4 | Update section 11's parity table with what actually shipped, and log every remaining gap |

**Exit:** a user who learns one client recognises the other; every remaining difference appears in
section 11 as deliberate, with a reason.

### 13.1 What can be done in parallel

Waves 0 and 1 are strictly serial. After wave 2 the following are independent and can run
concurrently: wave 4 (the two named screens), wave 7 (settings), wave 8 (account). Wave 5 must
precede wave 6. Wave 9 must come last except for 9.1, which can land any time after wave 2.

### 13.2 If only five things can be done

In this order, and this is the honest triage:

1. **Delete the five gradient and glow tokens and repoint their six consumers.** One afternoon, and
   the product stops looking like every other VPN on a store page.
2. **Rebuild the sign-in screen** (7.21). It is the first screen a paying user sees, it carries 20
   buttons, and its hierarchy is inverted.
3. **Build the status strip and wire `DelegateSnackMsg` into it** (3.8). The product currently cannot
   say «Готово», and it cannot report a failed clipboard import at all.
4. **Add the Серверы destination with search** (section 6). With 80 to 150 servers per subscription,
   a list with no search is a functional hole and the shipping app has none.
5. **Build `SubPage` and the row component, and delete the sixty hand-rolled copies** (2.7). This is
   what stops the drift that produced nine chrome copies, seven press scales and ten glyph sizes.

Everything else in this document is downstream of those five.

---

## 14. File index and verdicts

Every `.axaml` in `v2rayN.Desktop`, with its verdict and its destination. **50 view files today**,
plus `App.axaml` and the two `Assets/` dictionaries. After the rebuild: 17 deleted outright, 9
deleted-by-merge into a rebuilt surface, 11 new files created, and the two `Assets/` dictionaries
split into thirteen.

### 14.1 Shell and system

| File | Lines | Verdict | Becomes |
|---|---|---|---|
| `App.axaml` / `.axaml.cs` | 49 / 707 | RESTYLE | theme selection + tray only; `BuildMonoOverlay` deleted |
| `Assets/GlobalResources.axaml` | 569 | SPLIT | `Tokens.axaml` + `Icons.axaml` + `Themes/{Dark,Light,Mono}.axaml` |
| `Assets/GlobalStyles.axaml` | 1 448 | SPLIT | `Styles/{Text,Buttons,Rows,Inputs,Overlays,Shell,Motion}.axaml` |
| `Views/MainWindow.axaml` / `.cs` | 737 / 2 029 | RESTYLE | `Views/Shell/MainWindow` + the chrome styles moved out |
| `Views/BottomNavBar.axaml` | 180 | REBUILD | `Views/Shell/CompactNavBar` on the shared `Button.NavItem` |
| `Views/StatusBarView.axaml` | 125 | REFACTOR then DELETE | handlers move to the shell |
| - | - | NEW | `Views/Shell/StatusStrip.axaml` |
| - | - | NEW | `Views/Components/SubPage.axaml` |

### 14.2 Главная

| File | Lines | Verdict | Becomes |
|---|---|---|---|
| `HomeView.axaml` | 74 | REBUILD | `Views/Home/HomePage.axaml` (both modes) |
| `CompactHomeView.axaml` | 94 | DELETE, merged | same |
| `ConnectHeroView.axaml` / `.cs` | 839 / 1 156 | RESTYLE | `Views/Home/ConnectControl.axaml`, five layers deleted |
| `HomeAccountChip.axaml` | 131 | RESTYLE | the account row inside `HomePage` |
| - | - | NEW | `Views/Home/StatsRow.axaml` |

### 14.3 Серверы

| File | Lines | Verdict | Becomes |
|---|---|---|---|
| `ServerListView.axaml` / `.cs` | 313 / 939 | RESTYLE | `Views/Servers/ServerList.axaml` + `ServerRow.axaml` |
| `SubscriptionMetaView.axaml` / `.cs` | 335 / 687 | REBUILD | `Views/Servers/ProviderGroupHeader.axaml` |
| `ServersView.axaml` | 12 | DELETE | - |
| `CompactServersView.axaml` | 116 | HARVEST then DELETE | its search becomes the toolbar's |
| `ProfilesView.axaml` | 322 | DELETE | - |
| `AddServerWindow.axaml` | 1 388 | REBUILD | `Views/Servers/ServerEditorPage.axaml` |
| `AddServer2Window.axaml` | 160 | DELETE, merged | the editor's raw-JSON mode |
| `AddGroupServerWindow.axaml` | 258 | DELETE, merged | the editor's group mode |
| `SubEditWindow.axaml` | 272 | REBUILD | `Views/Servers/ProviderEditorPage.axaml` |
| `SubSettingWindow.axaml` | 82 | DELETE | the provider pane |
| `ProfilesSelectWindow.axaml` | 129 | REBUILD | `Views/Components/PickerFlyout.axaml` |
| - | - | NEW | `Views/Servers/ServersPage.axaml`, `ProviderPane.axaml` |

### 14.4 Настройки

| File | Lines | Verdict | Becomes |
|---|---|---|---|
| `SettingsView.axaml` / `.cs` | 1 075 / 359 | RESTYLE | `Views/Settings/SettingsPage.axaml`, 4 groups |
| `PerAppProxyPage.axaml` | 163 | RESTYLE | on `SubPage` |
| `DnsSubView.axaml` | 162 | RESTYLE | on `SubPage`, `Border.DnsChip` deleted |
| `PingSettingsPage.axaml` | 160 | RESTYLE | on `SubPage`, `Border.MethodRow` deleted |
| `RoutingSubView.axaml` | 184 | RESTYLE | on `SubPage` |
| `RoutingRuleSettingWindow.axaml` | 259 | REBUILD | `settings/routing/ruleset/{id}` |
| `RoutingRuleDetailsWindow.axaml` | 263 | DELETE, merged | the inline rule editor |
| `GeoFilesPage.axaml` | 100 | RESTYLE | plus real download states |
| `UrlSchemesPage.axaml` | 115 | RESTYLE | on `SubPage` |
| `BackupPage.axaml` | 96 | RESTYLE | on `SubPage` |
| `AboutPage.axaml` | 105 | RESTYLE | `MaxWidth` 620 to 720 |
| `ProviderSettingsPage.axaml` | 138 | WIRE + RESTYLE | `settings/provider` |
| `OptionSettingWindow.axaml` | 1 206 | DELETE | 10 features migrated (8.4) |
| `FullConfigTemplateWindow.axaml` | 197 | REBUILD | `settings/core/advanced` |
| `GlobalHotkeySettingWindow.axaml` | 133 | REBUILD | `settings/hotkeys` |
| `MsgView.axaml` | 104 | REBUILD | `settings/core/log` |
| `CheckUpdateView.axaml` | 95 | REBUILD + WIRE | `settings/update` |
| `ThemeSettingView.axaml` | 67 | DELETE | Настройки › Интерфейс |
| `BackupAndRestoreView.axaml` | 213 | DELETE | - |
| `JsonEditor.axaml` | 26 | KEEP + RESTYLE | used by `core/advanced` |
| - | - | NEW | `settings/bypass`, `settings/core` |

### 14.5 Аккаунт and auth

| File | Lines | Verdict | Becomes |
|---|---|---|---|
| `AccountView.axaml` / `.cs` | 1 474 / 524 | REBUILD | `Views/Account/AccountPage.axaml` (< 400 ln) |
| - | - | NEW | `Views/Account/SubscriptionPage.axaml` |
| `BuyView.axaml` | 709 | RESTYLE | `Views/Account/BuyPage.axaml` |
| `DevicesView.axaml` | 491 | RESTYLE | `Views/Account/DevicesPage.axaml` |
| `PaymentHistoryView.axaml` | 351 | RESTYLE | `Views/Account/PaymentHistoryPage.axaml` |
| `LoginView.axaml` / `.cs` | 954 / 1 377 | REBUILD | `Views/Auth/LoginPage.axaml` (< 350 ln) |
| `OnboardingView.axaml` | 238 | RESTYLE | `Views/Auth/OnboardingPage.axaml` |
| `AccountSyncView.axaml` | 176 | KEEP | `Views/Auth/AccountSyncPage.axaml`, flat background |

### 14.6 Dialogs and deletions

| File | Lines | Verdict |
|---|---|---|
| `MessageBoxDialog.axaml` | 74 | KEEP, shadow removed |
| `QrcodeView.axaml` | 31 | REBUILD as `Dialogs/QrDialog.axaml` |
| `SudoPasswordInputView.axaml` | 66 | REBUILD as `Dialogs/SudoDialog.axaml` |
| `ClashProxiesView.axaml` | 158 | DELETE |
| `ClashConnectionsView.axaml` | 104 | DELETE |

---

## 15. The desktop definition of done

A desktop screen is finished when every box is honestly ticked. This is `00-rules.md` 16 with the
desktop-specific rows filled in.

**Tokens and system**
- [ ] Zero inline hex in the view; zero `StaticResource` on a theme brush
- [ ] Zero off-scale `Margin`, `Padding` or `Spacing`
- [ ] Zero inline `FontSize`; every text element carries a ramp class
- [ ] Zero view-local `<Style Selector=...>` outside the four permitted cases
- [ ] Radii: 16 buttons, 20 cards, 12 chips and tiles and inputs
- [ ] No new token without a comment stating its purpose and its contrast ratio

**Bans**
- [ ] No gradient, no glow, no blur, no `BoxShadow`, no side stripe
- [ ] No nested cards; at most one card per region and no card inside a card
- [ ] No identical-card grid where a divided list belongs
- [ ] Section headers sentence-case bold; no ALL-CAPS, no numbered scaffolding
- [ ] No emoji as chrome, no text characters standing in for icons
- [ ] One filled accent surface; the accent count matches the table in 2.2
- [ ] No bottom sheet, no carousel, no pull-to-refresh, no phone idiom

**Colour and type**
- [ ] Dark, light and mono all checked
- [ ] Body >= 4.5:1, large >= 3:1, icons >= 3:1, placeholders >= 4.5:1, in all three
- [ ] Colour is never the only signal
- [ ] Inactive and disabled states are desaturated
- [ ] Every number uses `Font.Numeric` with `tnum,lnum` and a reserved column width
- [ ] No Russian string is set in `Font.Grotesk`
- [ ] The longest real Russian string fits at 900x600 and at 380x620

**Interaction**
- [ ] Default, hover, pressed, focus, disabled, loading and error all implemented
- [ ] Focus is visible on every focusable control and tab order matches visual order
- [ ] Selection reads on two channels
- [ ] Pointer targets: 32 minimum, 40 in rows and toolbars, 52 for a primary CTA
- [ ] Feedback within 100ms; a loading state after 300ms; skeletons shaped like the result
- [ ] Forms: label above, helper slot always present, validate on blur, error below
- [ ] Destructive actions use undo; a dialog only for the four irreversible cases
- [ ] `Esc`, `Alt+Left` and mouse button 4 all go back; back restores scroll and filter
- [ ] The whole screen is completable with the keyboard alone

**Motion**
- [ ] Every duration and curve is a token; ease-out only; exit is 75 percent of enter
- [ ] Press is 0.97 everywhere except the documented connect disc
- [ ] No looping idle animation; no page-load choreography; no animated layout property
- [ ] Only one 600ms hero moment exists in the product and this is not a new one
- [ ] `.lite` verified by toggling it live, not by reading the code

**Copy**
- [ ] Every string in `Common/L.*.cs`; zero `resx:ResUI`
- [ ] Russian, sentence case, active verbs, terminology per `00-rules.md` 9.3
- [ ] Zero em-dashes and en-dashes; `₽`, `…` and «ёлочки» used correctly
- [ ] Errors state the cause and the fix and offer recovery
- [ ] Empty states are title plus one line plus one action

**States**
- [ ] Default, first run, loading, empty, error, offline, partial, long, short, gated, success
- [ ] The product gate states: нет подписки, истекает, истекла, триал, Telegram не привязан, нет
      серверов, подключение, подключено, отключение, ошибка туннеля, лимит устройств

**Platform**
- [ ] Usable at 900x600 wide and 380x620 compact, with no horizontal scroll
- [ ] DPI 100 / 125 / 150 / 200 and UI zoom 100 / 200 all verified
- [ ] No Semi or Fluent default leaks anywhere on the screen
- [ ] One scroll region, no nested scrollers (the Серверы two-pane layout excepted)
- [ ] The window can be resized across the 760 breakpoint without losing state

**Verification**
- [ ] The screen was run and looked at, in three themes, at two window sizes, at two zoom levels
- [ ] The nine questions of the desktop slop test (1.5) answered out loud
- [ ] Parity checked against the Android counterpart in section 11

---

## 16. One paragraph, for whoever picks this up

The desktop client is the same instrument as the phone, mounted on a bench. It keeps the near-black
surface ramp with no shadows and no gradients, the one blue spent on the single thing the screen
wants you to press, the 56px hairline-ruled row at the 68px text origin as the repeating unit of the
whole product, and every quantity set in tabular figures so a live counter is as still as a printed
one. It gains four things the phone cannot have and must not fake: a hover state on everything
clickable, a focus ring that is always visible, a keyboard path through every task, and a window that
restructures rather than stretches between 380px and 3840px. It loses four things it should never
have had: five layers of glow around a 176px disc, a horizontal card carousel, a bottom sheet, and
fifteen OS-decorated windows speaking a language the product does not. The build order is: settle the
tokens, build the components, fix the shell, then the sign-in screen and the first frame the owner
named, then servers, then the editors, then settings, then the account, then the sweep. Nothing ships
with only a happy path, nothing ships unreachable, and nothing ships that a user fluent in Raycast,
Linear and Telegram Desktop would pause at.

---

## Change log

| Date | Change |
|---|---|
| 2026-07-26 | First issue. Built on `00-rules.md`, `02-inventory-pc.md`, `03-direction.md`, `20-control-survey.md`, `21-account-survey.md`, `30-reference-analysis.md`, `31-self-assessment.md`. Answers the seven open questions of `02-inventory-pc.md` 6 and resolves the two conflicts between `03-direction.md` and `30-reference-analysis.md` |
