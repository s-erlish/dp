# Verification: "Five interactive archetypes below the 48px touch minimum; three sizes for 'an icon button'"

Target: `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml:842`
Verdict: **PARTLY REAL.** The headline framing is wrong; the underlying defect (the declared
icon-button consolidation did not land) is real and provable, and there is one concrete visual
bug the reporter did not name.

---

## 1. What the canonical block actually says — confirmed

`GlobalStyles.axaml:830-841` (comment) + `:842` (`<Style Selector="Button.IconButton40">`):

- `:831` — "ОДНА иконочная кнопка Incy (DESIGN_PLAN §0.1, REVIEW_BACKLOG P1 2)"
- `:833-836` — "КАНОНИЧЕСКОЕ РЕШЕНИЕ: Button.IconButton40 = 40×40 … Это **единый класс** для тулбаров,
  строк и мета-действий на **ВСЕХ** Incy экранах — **конец дрейфа 32/36/40**."
- `:838-840` — "Легаси Button.IconButton (32×32, выше) НЕ трогаем … Incy экраны **переходят на
  IconButton40** в проходе дедупликации."

So the file itself asserts (a) one class, (b) on all Incy screens, (c) legacy 32 confined to geek
screens. All three assertions are measurably false in the current tree — see §3.

Backing tokens: `GlobalResources.axaml:297` `Size.IconButton = 40`; `:298` `Size.SubToolbar = 56`;
`:300` `Size.SegmentChip = 44`; `:158` `Size.Glyph = 22`; `:154` `Radius.Pill = 100`.

---

## 2. Where the reporter is WRONG

### 2a. There is no "48px touch minimum" in this codebase

The desktop app's own declared icon-button box is **40** (`GlobalResources.axaml:297`), and the
canonical class it anchors (`GlobalStyles.axaml:845-846`) resolves to exactly that. The 48dp figure
comes from `/home/user/dp/CLAUDE.md`, which governs the **Android** app (`row_min_height 56`,
"≥48dp touch targets"). Measuring a mouse-driven Avalonia desktop app against it makes the canonical
class itself a violation — which proves it is the wrong yardstick. Counting "five archetypes below
48" is a category error, not a finding.

### 2b. Two of the five listed archetypes are not drift and not icon buttons

- **`Button.WinBtn` 44×22** (`MainWindow.axaml:52-55`) is an OS caption button. Its height is a
  direct consequence of an explicit owner decision recorded at `MainWindow.axaml:315-317`: "Слим-
  рамка-заголовок (было 40 → 32 → 28 — владелец: «слишком толстая»); держит … системные кнопки
  44×22." A 22-high button inside a 28-high title bar is correct by construction.
- **`Button.SegItem` 36 high** (`LoginView.axaml:78-79`) is the inner tab of `Border.SegTrack`
  (`LoginView.axaml:72-74`), which is `Height 44` with `Padding 4`. 44 − 2×4 = 36. Concentric by
  construction, and `:85` even documents the concentric corner-radius math (12 − 4 = 8). Not drift.
- **`ToggleButton.Segment` 44 high** (`GlobalStyles.axaml:1214-1217`) is a **text** chip on its own
  token (`Size.SegmentChip`, `GlobalResources.axaml:300`), used only in `SettingsView.axaml:261,265,
  722,726`. Different archetype from an icon button; not part of the 32/36/40 drift.

### 2c. Wrong file/line for "legacy Button.IconButton 32×32"

`GlobalResources.axaml:20-21` are the **tokens** (`IconButtonWidth`/`IconButtonHeight` = 32). The
style is `GlobalStyles.axaml:226-232`.

### 2d. "Three sizes" undercounts

Distinct icon-button boxes actually in play: **30, 32, 34, 40** — four, plus a fifth de-facto spelling
(legacy class forced to 40 inline). See §3.

### 2e. The `Classes="IconButton BackNav"` cascade analysis is correct but is not a live bug

`Button.BackNav` (`GlobalStyles.axaml:907-928`) is declared after `Button.IconButton` (`:226-232`),
so in Avalonia's later-wins style ordering BackNav overrides every conflicting setter:
Width/Height 40 over 32 (`:910-911` vs `:227-228`), `MinWidth 0` over 32 (`:913` vs `:229`), and
ContentPresenter `Radius.Pill` over `8` (`:929-931` vs `:236-239`). The doubled form therefore
renders **identically** to the single form. It is redundant and order-fragile, not broken.

---

## 3. Where the reporter is RIGHT — the consolidation genuinely did not land

### 3a. `Button.BackNav` is a verbatim second copy of the canonical class, at the same size

`GlobalStyles.axaml:907-945` repeats `Button.IconButton40`'s (`:842-880`) setters one for one — same
`BorderlessButton` theme, same `Size.IconButton` 40, same `MinWidth/MinHeight 0`, same `Padding 0`,
same `Radius.Pill`, same `Cursor Hand` (`:920` vs `:855`), same `:pressed scale(0.92)` (`:938` vs
`:874`), same hover `ContentPresenter` recolour, same 0.12s transform transition. Its own comment
(`:905-907`) concedes this: "семантика IconButton40 … Класс существует **ради читаемости разметки**".
A duplicated archetype maintained by copy-paste is exactly what `:831` ("ОДНА иконочная кнопка")
forbids, and it is the mechanism by which the `IconButton BackNav` double-spelling stays invisible.

### 3b. Legacy 32 is still on Incy screens, not just geek screens

`Classes="IconButton"` appears in 22 view files. The ones that are Incy screens, contradicting `:840`:

- `LoginView.axaml:246` (`IconButton BackNav`), `:377`
- `BuyView.axaml:245` (`IconButton BackNav`)
- `DevicesView.axaml:291`
- `ThemeSettingView.axaml:22` — bare `Classes="IconButton"`, no inline size, so a genuine 32×32 box.

Meanwhile only four files ever use the canonical class: `AccountView.axaml`,
`CompactServersView.axaml`, `ConnectHeroView.axaml`, `SubscriptionMetaView.axaml`.

### 3c. `Button.MetaIcon` 34 — real fourth size, and the comment at :102 is false

`SubscriptionMetaView.axaml:58-61` declares `Button.MetaIcon` at `Width/Height 34` inside
`UserControl.Styles`. Avalonia applies control-level styles after application-level ones, so 34 beats
the global 40 — the file's own comment at `:52-56` states this outright ("переопределяют глобальный
Button.IconButton40").

Consequently the comment at `:102` — "Шеврон-сворачивание … **Бокс 40 — ≥ touch target**" — is wrong
about the very button it annotates: `:107` gives it `Classes="IconButton40 MetaIcon"`, so it renders
**34×34**. Same falsity applies to the other five `MetaIcon` buttons at `:147, :158, :192, :208, :323`.

### 3d. `Button.RailToggle` 30×30 — real fifth size

`MainWindow.axaml:132-134`, `Width/Height 30`, with a fixed 30×30 `Border.WinBtnBg` press surface
(`:158-160`). Its comment (`:128-130`) justifies the *no-background* behaviour but never the 30 box.

---

## 4. Defect the reporter MISSED — the only concrete visual artifact

Two 40×40 icon buttons on Incy screens carry the **legacy** class with inline sizing:

- `LoginView.axaml:372-377` — password eye toggle: `Width="40" Height="40" Padding="0"
  Classes="IconButton"`, glyph 20.
- `DevicesView.axaml:284-291` — "Отвязать" (unlink device): `Width="40" Height="40" Padding="0"
  Classes="IconButton"`, glyph 22. Its own comment at `:283` says "«Отвязать» 40, глиф 22".

Inline attributes are LocalValue and beat style setters, so both render at 40 — the *box* is right.
But they inherit `Button.IconButton`'s template styling, which differs from the canonical class in
three user-visible ways:

1. **Hover surface is a rounded square, not a circle.** `GlobalStyles.axaml:236-238` sets
   `ContentPresenter#PART_ContentPresenter` `CornerRadius = 8`; the canonical path
   (`:865-867` + `:868-870`) sets `Radius.Pill` = 100 (`GlobalResources.axaml:154`). At a 40px box,
   8 vs 100 is the difference between a squircle and a full circle. Every other 40 icon button in
   the app shows a circle; these two show a rounded square.
2. **No press feedback.** `Button.IconButton` (`:226-232`) declares no `:pressed` `RenderTransform`
   and no `Transitions`. The canonical class declares `scale(0.92)` (`:872-874`) with a 0.12s
   `TransformOperationsTransition` (`:856-860`).
3. **No `Cursor="Hand"`.** Declared at `:855` for IconButton40 and `:920` for BackNav; absent from
   `Button.IconButton`.

This is a direct "every state designed" violation from `/home/user/dp/CLAUDE.md`, and unlike the rest
of the finding it is visible to a user rather than only to a reader of the XAML.

---

## 5. Corrected statement of the defect

> `GlobalStyles.axaml:830-841` declares `Button.IconButton40` the single icon-button archetype and
> announces "конец дрейфа 32/36/40". The consolidation never happened. Four distinct icon-button
> boxes are live — 30 (`Button.RailToggle`, `MainWindow.axaml:133-134`), 32 (`Button.IconButton`,
> `GlobalStyles.axaml:227-228`, still on Incy screens `ThemeSettingView.axaml:22`,
> `LoginView.axaml:246,377`, `BuyView.axaml:245`, `DevicesView.axaml:291`), 34 (`Button.MetaIcon`,
> `SubscriptionMetaView.axaml:58-61`, overriding the canonical 40 on all six meta actions), and 40
> — and 40 itself is served by **two** copy-pasted classes, `Button.IconButton40`
> (`GlobalStyles.axaml:842-880`) and its near-verbatim clone `Button.BackNav` (`:907-945`).
> The back button has three spellings (`IconButton BackNav`, `BackNav`, and legacy `IconButton` +
> inline 40). The only user-visible consequence: the password-eye (`LoginView.axaml:372-377`) and
> unlink-device (`DevicesView.axaml:284-291`) buttons render a `CornerRadius 8` hover surface instead
> of the circular `Radius.Pill`, with no press-scale and no hand cursor, because
> `Button.IconButton` (`:226-232`) declares none of those while `Button.IconButton40` does
> (`:855-860`, `:865-874`). The comment at `SubscriptionMetaView.axaml:102` ("Бокс 40 — ≥ touch
> target") is factually wrong about the button on the next line.

**Not part of the defect:** window caption buttons 44×22 (owner-mandated 28px title bar,
`MainWindow.axaml:315-317`), `Button.SegItem` 36 (concentric inside the 44 `SegTrack`,
`LoginView.axaml:72-79`), and `ToggleButton.Segment` 44 (a text chip on its own
`Size.SegmentChip` token). And there is no 48px minimum in this codebase — its declared
icon-button size is 40.

**Severity: medium, not high.** No functional breakage; the single visible artifact is a wrong
hover-corner radius plus missing press/cursor states on two buttons. The remainder is
consistency debt against the file's own stated law.

## 6. Minimal fix

1. `LoginView.axaml:377` and `DevicesView.axaml:291` → `Classes="IconButton40"`; drop the now-
   redundant inline `Width/Height/Padding` (`LoginView.axaml:374-376`, `DevicesView.axaml:286-289`).
   `DevicesView` also needs `.Row` if the 22 glyph should stay 22 — `IconButton40` defaults to 22
   (`GlobalStyles.axaml:881-883`), so no class is needed there; `LoginView`'s 20 glyph wants `.Row`
   (`:886-889`).
2. `ThemeSettingView.axaml:22` → `Classes="IconButton40"`.
3. Drop the redundant `IconButton` token at `LoginView.axaml:246` and `BuyView.axaml:245`, leaving
   `Classes="BackNav"` to match `DevicesView.axaml:116` and `PaymentHistoryView.axaml:46`.
4. Collapse `Button.BackNav` (`GlobalStyles.axaml:907-945`) into a selector group with
   `Button.IconButton40` (`:842-880`) so the two cannot diverge; keep `BackNav` only for the
   `Brush.OnSurface` glyph override (`:942-945`) and the toolbar readability it was created for.
5. Either delete `Button.MetaIcon`'s 34 (`SubscriptionMetaView.axaml:58-61`) and let the canonical
   40 stand, or keep 34 and fix the false comment at `:102`. Do not ship both.
6. `Button.RailToggle` 30 (`MainWindow.axaml:132-134`) — either justify in the comment block at
   `:128-130` or move to `Size.IconButton`.
