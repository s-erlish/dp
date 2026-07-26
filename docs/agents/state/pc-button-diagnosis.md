# PC — why the buttons flicker on hover, and why the type reads heavy

Diagnosis only. **No product file was changed by this pass**; the only file written is this one.
Build gate re-run for the record: `bash /home/user/dp/docs/agents/verify-build.sh desktop` →
`BUILD: SUCCESSFUL`, `NEW WARNINGS: 0`, `VERIFY: PASS`.

Owner, F1: *«на пк также кнопки все баганные, при наведении моргают и так далее … почти все кнопки
такие куда не наведешься»*. Owner, D2: *«шрифт какой-то толстый»*.

---

## 0. Verdict in one paragraph

**Hover feedback in this product is animated, and it is the only feedback hover gives.** Every button
class crossfades its hover — 120 to 220 ms — on the property that carries the hover (the content
presenter's `Background`, or the glyph/label `Foreground`). A pointer crossing a toolbar or a settings
list moves faster than 150 ms per control, so at any instant two or three buttons are part-lit: the one
you left is still fading out while the one you are on is fading in. That is what "моргают" describes,
and it is why it is *everywhere* rather than on one screen. The codebase has already reached this exact
conclusion twice and fixed it twice — for the caption buttons (`MainWindow.axaml:72-88`, "Bug3") and for
the server rows (`GlobalStyles.axaml:932-941`, "BUG 1") — in both cases by **deleting the
`BrushTransition` on `Background`** and letting hover snap. Those two fixes were never generalised. The
remaining ~20 button, row and nav classes still animate.

Three further defects ride on top of the same hover code and make individual buttons look broken; they
are sections 2–5. The claim in the F1 brief that a hover setter changes layout is, for buttons,
**false** — I checked every `:pointerover` block in the project mechanically and none of them touches a
layout property (section 6). So the fix is not "find the bad setter", it is "hover must be instant, and
it must be an overlay, not a replacement".

---

## 1. Root cause — hover is a 120–220 ms crossfade, so hover trails the pointer

`Brush.Hover` is a single token (`GlobalResources.axaml:139` white @ 6 % on dark, `:224` black @ 6 % on
light). At 6 % over an almost-black ground the *difference* between hovered and not-hovered is already
small; spreading that small difference over 150 ms means the eye sees a wash rising and falling behind
the cursor rather than a control lighting up. Combined with the fact that `IsPointerOver` is true for the
hovered control **and all its ancestors**, a pointer moving down a settings list paints a row, then a
row + a trailing row, then a row + its child icon button at double the wash.

Every class below still carries the animation. Line numbers are
`v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml` unless noted.

| Class | Animated property | Duration | Where |
|---|---|---|---|
| `Button.Primary` | presenter `Background` | 150 ms | 642-648 (transition), 652-657 (hover/press values) |
| `Button.Tonal`, `Button.Secondary` | presenter `Background` | 150 ms | 759-765, 771-776 |
| `Button.Icon` (glyph) | `PathIcon.Foreground` | 150 ms | 2036-2041 + hover 2042-2044 |
| `Button.NavRailItem` | `PathIcon`/`TextBlock` `Foreground` | 200 ms | 1116-1135 + hover 1106-1111 |
| `Button.NavItem` | `PathIcon`/`TextBlock` `Foreground` | 220 ms | 2295-2323 + hover 2318-2323 |
| `Button.BottomNavItem` | `PathIcon`/`TextBlock.NavLabel` `Foreground` | 200 ms | `BottomNavBar.axaml:60-92` + hover `:97-102` |
| `Border.PriceOption` | `Background`, `BorderBrush` | 150 ms | 1691-1700 + hover 1698-1700 |
| `Border.Selectable` | `Background`, `BorderBrush` | 220 ms | 2463-2472 + hover 2470-2472 |
| `Button.MethodChip` | `BorderBrush` | 150 ms | `AccountView.axaml:106-118` |
| `Path.CloseGlyph` (window close) | `Stroke` | 120 ms | `MainWindow.axaml:99-112` |
| `Path.RailToggleGlyph` | `Stroke` | 120 ms | `MainWindow.axaml:201-220` |
| `TextBox.Incy`, `TextBox.IncyField` | `BorderBrush` | 150 ms | `GlobalResources.axaml:570-574, 614-616 / 657-661, 700-702` |
| `ConnectHeroView` disc | `Background`, ring `Opacity` | 120 ms | `ConnectHeroView.axaml.cs:149-151, 751-765` |

Already fixed, and therefore the precedent for the fix:

* `Border.ServerRow` — `GlobalStyles.axaml:932-941`: *«ховер-заливка МГНОВЕННА (Android-паритет).
  BrushTransition на Background убран — при переходе курсора между строками уходящая строка гасла
  плавно, а входящая проявлялась → казалось, что подсвечены ДВЕ строки.»*
* `Button.WinBtn` — `MainWindow.axaml:72-88`: *«Bug3: ховер-заливка caption-кнопок МГНОВЕННА … на ~120мс
  подсвечены ОБЕ … Тот же фикс, что для строк сервера.»*

**Fix.** Apply that same fix to the whole system, in one place:

1. Delete the `Transitions` setter at `GlobalStyles.axaml:642-648` (Primary) and `:759-765`
   (Tonal/Secondary). Hover then snaps, exactly as `PrimaryCompact` (688-696) and `Destructive`
   (1453-1464) already do — note that today those two snap while Primary and Tonal fade, so the
   product has two different hover behaviours for the same gesture.
2. Delete the `BrushTransition Property="Foreground"` from `Button.Icon PathIcon` (2036-2041),
   `Button.NavRailItem PathIcon`/`TextBlock` (1121-1126, 1130-1135), `Button.NavItem` (2299-2316) and
   `BottomNavBar.axaml` (60-92). Keep the colours, drop the twin.
   Caveat that must be preserved: the nav *active tint* (grey → accent when the tab changes) is a
   genuine state change and 200 ms is right for it. Split the two — hover instant, `.active`/`.sel`
   animated — by moving the transition onto a selector that hover cannot trigger, or by driving the
   active tint from the code-behind that already moves the rail indicator
   (`MainWindow.axaml.cs` `MoveRailIndicator`). Do **not** simply delete the active-tint animation;
   that is one of the animations the owner means when he says nothing may be cut.
3. Same for `Border.PriceOption` (1691-1700) and `Border.Selectable` (2463-2472): keep the
   `BorderBrush` transition (it only ever fires on *selection*, a real state change), drop the
   `Background` one (it fires on hover).
4. `Path.CloseGlyph` / `Path.RailToggleGlyph` `Stroke` at 120 ms: drop; those are hover-only.

Everything above is a deletion of a *transition*, not of a state, a control or an affordance. Nothing
the owner can see disappears; the same colours still appear, they just appear at once.

---

## 2. Second mechanism — press-scale lives on the button itself, so pressing changes the button's own hit area

Every button class sets `RenderTransform: scale(0.97)` on `:pressed` **on the button**, e.g. Primary
615-623, PrimaryCompact 697-704, Tonal/Secondary 745-752, OutlinedAccent 803-809, Stepper 866-873,
IconButton40 1194-1201, BackNav 1286-1293, Tertiary/LinkAction/TextAction 1360-1367, DestructiveText
1406-1413, Destructive 1465-1472, Icon 2018-2025, Segment 1810-1817, NavRailItem 1112-1114 (and again
`MainWindow.axaml:177-179`), `BottomNavBar.axaml:57-59`, `AccountView.axaml:119-121`,
`BuyView.axaml:80-82`, plus `Border.Card.Pressable` 2089-2096, `Border.ServerRow` 948-950,
`Border.AccountRow` `HomeAccountChip.axaml:57-59`.

Avalonia hit-tests **through** `RenderTransform`. A 200 × 48 CTA at 0.97 loses 3 px on each side
horizontally and 0.7 px vertically; the 76 × 64 rail item loses 1.1 × 1 px. While the 90 ms press-in and
the 160 ms press-out animations run, that edge sweeps across the cursor. Any pointer movement during
that window re-hit-tests and can land on the *parent* instead of the button — `:pointerover` drops, the
hover wash disappears, the scale returns, the next move puts it back. Near a button's edge that reads as
a blink; it is also why a tap sometimes does not register (the `Tapped` gesture is cancelled when the
control slides out from under the pointer — the same failure `Border.SettingRow` documents at
`GlobalStyles.axaml:970-973`).

This mechanism is already diagnosed in-tree, for exactly one control. `MainWindow.axaml:125-131`:
*«Press-scale живёт на ВНУТРЕННЕЙ подложке (Border.WinBtnBg, фикс. 30×30), а не на самой кнопке — хит-
область кнопки неизменна.»* That is the pattern; it was applied to `Button.RailToggle` and to nothing
else.

Two honest constraints on how strong this is:

* Avalonia updates `IsPointerOver` only from real pointer input (`MouseDevice.cs` — there is no
  pointer-over re-evaluation on scene invalidation), so a *stationary* cursor will not oscillate on its
  own. The chatter needs the mouse to be moving, which during a click-and-drag-off or a fast sweep it
  is.
* `TopLevel.SceneInvalidated → UpdateToolTip` (`TopLevel.cs:801-826`) **does** re-run
  `InputHitTest(clientPoint)` on every rendered frame whose dirty rect contains the pointer, and feeds
  the result to `ToolTipService`. 56 controls in this app carry `ToolTip.Tip`. So while a hovered
  button animates (section 1) or scales (this section), a full hit test runs per frame and the tooltip
  target is re-resolved per frame — a real, measurable cost and a plausible source of a *tooltip*
  that blinks on and off over a button. Worth confirming on the owner's machine before spending on it.

**Fix.** Give the button system the RailToggle treatment: the press scale moves onto an inner
presenter of fixed size, not the button. Concretely — add a `Border.PressSurface` (transparent,
`RenderTransformOrigin 50%,50%`, carrying the `TransformOperationsTransition`) that the button classes
target, and change `Button.X:pressed { RenderTransform }` to
`Button.X:pressed Border.PressSurface { RenderTransform }`. The press animation is *kept* — same 0.97,
same 90 ms in / 160 ms out asymmetry — it just stops moving the hit target.

---

## 3. Third mechanism — hover *replaces* the resting fill instead of tinting it

Semi's Button template binds the presenter's fill to the button:
`Style Selector="^ /template/ ContentPresenter#PART_ContentPresenter" { Background = {TemplateBinding
Background} }` (Semi.Avalonia `Controls/Button.axaml:132-136`). Our hover rules assign
`Brush.Hover` to **that same** `Background`. So on any button whose resting fill is not transparent,
hover does not lay 6 % of white over the fill — it **deletes the fill** and puts 6 % of white in its
place.

Measured, dark theme:

| Class | Rest | On hover | Reads as |
|---|---|---|---|
| `Button.Tonal` / `Secondary` (727, 771-776) | `SurfaceHighest` `#20242B` | white 6 % over the page `#0A0B0D` → `≈#191A1C` | the button's body **darkens and thins out**, then pops back on exit |
| `Button.Stepper` (847, 874-876) | `Tile.Blue` `#4C8DFF` @20 % | white 6 % | the blue ± tile **turns grey** while you hover it |
| `SubscriptionMetaView` support chip (`:295`) | inline `AccentContainer` | white 6 % | the accent chip **loses its colour** on hover |
| `Button.Icon.Filled` (2052-2054) | `SurfaceHighest` | — see §4 — | no hover at all |

The same trap was spotted and closed for the *selected* case — `Border.ServerRow.selected:pointerover`
(960-962), `Border.Selectable.selected:pointerover` (2482-2484),
`ToggleButton.Segment:checked:pointerover` (1831-1833) all re-assert the selected fill so hover cannot
erase it — but never for the *filled-at-rest* case.

**Fix.** Hover must be an overlay layer, not a value. Either

* give the templated buttons a hover overlay: a `Border`/`Panel` inside the presenter whose
  `Background` is `Brush.Hover` and whose `Opacity` goes 0 → 1 on `:pointerover` (this is what the
  Android ripple layer does, and it composes correctly over any rest fill); or
* per class, define the hover as *that class's own fill, one step*, the way `Primary` already does
  (`Accent` → `AccentHover`, 649-657). `Tonal` needs a `SurfaceHighestHover` token; `Stepper` needs a
  `Tile.BlueHover`.

The first is one change and covers every class including the ones added later. Note it also fixes the
double-wash where a hovered icon button sits inside a hovered row (both paint `Brush.Hover`; with an
overlay the two still stack, so the row's overlay should be suppressed while a child button is hovered —
`Border.Row.Action:pointerover` (517-519) is the existing precedent for suppressing a row's hover).

---

## 4. Fourth — two hover rules for the same surface, resolved by declaration order

`Button.Icon:pointerover /template/ ContentPresenter#PART_ContentPresenter` (2012-2014) and
`Button.Icon.Filled /template/ ContentPresenter#PART_ContentPresenter` (2052-2054) both set
`Background` on the same element, both carry an activator, both sit in the same `Styles` collection.
Avalonia breaks that tie by frame order — the later-declared wins — so `.Filled` wins **in both states**
and a filled icon button has **no hover feedback whatsoever**. Same shape at
`DnsSubView.axaml:145-150` (`Border.DnsChip:pointerover` then `Border.DnsChip.selected`), where the
order happens to give the right answer today.

**Fix.** Never rely on declaration order for state: add the explicit
`Button.Icon.Filled:pointerover /template/ ContentPresenter#PART_ContentPresenter` rule (and a
`:pressed` one), the way `ToggleButton.Segment` already spells out all four combinations
(1807, 1818, 1831).

---

## 5. Fifth — the one place where a `:pointerover` really does change layout

Not a button, but it is the pattern F1 asks about and it is real:
`GlobalStyles.axaml:185-188` and `:200-203` — the scrollbar thumb changes `Width 6 → 8` and
`Margin 3 → 2` on `ScrollBar:pointerover`, with a `DoubleTransition` on `Width`/`Height` (135-137).
That is a hover that re-measures. It cannot chatter (the `ScrollBar`'s own 12 px lane does not move), so
it is cosmetic — but it is the only layout-affecting hover in the product and should be re-expressed as
a scale or an opacity if it is kept.

Adjacent, and worth one check on the owner's machine: `ScrollViewer.AllowAutoHide=True` is set
globally (254-256), so the 12 px scrollbar lane is an **overlay** on the right edge of every scrollable
page, and our `ScrollBar` theme keeps `Background="Transparent"` (145-146) — which in Avalonia is
hit-testable. Any control whose right edge reaches under that lane loses hover there. The rail's resize
grips (`MainWindow.axaml:645-707`, 4 px transparent bands round the window) have the same property but
are correctly confined to the window frame.

---

## 6. Ruled out, with evidence

The F1 brief lists five candidate mechanisms. Four of them are **not present**; recording that so the
next pass does not re-walk it.

1. **A `:pointerover` setter that changes a layout property on a button — NO.** I extracted every
   `<Style Selector="…pointerover…">` block in all 52 `.axaml` files and listed the properties each
   sets. Result: **71 hover blocks**, of which 67 set only `Background`, `Foreground`, `BorderBrush`,
   `Stroke` or `Opacity`. The four exceptions are the scrollbar thumb, vertical and horizontal (§5,
   `Width`/`Height` + `Margin`), and two `TextBox.Bare` themes that set `BorderThickness` to **0 in
   every state** (`SettingsView.axaml:167-169`, `PerAppProxyPage.axaml:112-114`) — a no-op.
   **No button changes padding, margin, border thickness, font weight or size on hover.**
2. **A transition on a property the same style sets from two places — NO.** No property carries both a
   transition and two competing hover setters. (The nearest thing is `Button.NavRailItem:pressed`
   declared twice with different scales — `GlobalStyles.axaml:1112` says 0.92, `MainWindow.axaml:177`
   says 0.97 — which is dead code and an inconsistency, not a flicker.)
3. **A hover that re-templates the child — NO.** `Theme` is set from styles on 12 button classes
   (`BorderlessButton`), which does rebuild the template, but never from a `:pointerover` selector, and
   no code assigns `Theme` at runtime.
4. **A `RenderTransform` without a `RenderTransformOrigin` — NO.** Every scaling class sets
   `RenderTransformOrigin="50%,50%"`, and the two deliberate absolute `0.5,0.5` cases
   (`Border.ConnectDisc` 1030-1037, `Ellipse.Spinner` 1914-1923) are documented no-ops whose centring
   is done imperatively. The transform *does* still move the hit area — that is §2, and it is a
   different fault from a mis-set origin.
5. **A `BoxShadow`/blur animated per frame — NO.** Three `BoxShadow` declarations exist
   (`IncyFlyoutTheme` 78, `Border.Toast` 1749, and the flyout's shadow); all static, none in a
   transition or animation.

Also checked and cleared, so they are not re-investigated:

* `BrushTransition` does **not** drop `IBrush.Opacity` while interpolating — Avalonia's
  `ISolidColorBrushAnimator.Interpolate` interpolates colour *and* opacity — so the 6 %/12 %/20 %
  tokens do not flash at full strength mid-transition.
* Semi's Button `ControlTheme` declares **no** transitions, and `ButtonDefaultPointeroverBorderBrush`
  is `Transparent` in the dark theme, so the un-overridden half of Semi's hover rule (we override
  `Background` but not `BorderBrush`) draws nothing.
* The keep-alive tab host is correct: hidden tabs get `IsHitTestVisible = false`
  (`MainWindow.axaml.cs:246-251, 450-459`), so no invisible pane is eating hover.
* The server list reconciles in place and does not tear down containers
  (`HomeViewModel.cs:575-612, 763-800`), so rows are not being destroyed under the cursor.

---

## 7. D2 — the font. What actually resolves on Windows

### Measured, from the shipped binaries

`v2rayN/v2rayN.Desktop/Assets/Fonts/` (name table + `OS/2` + `fvar` read directly):

| File | nameID 1 (family) | nameID 16 (typographic) | `usWeightClass` | variable |
|---|---|---|---|---|
| `GolosText-Regular.ttf` | Golos Text | — | 400 | no |
| `GolosText-Medium.ttf` | **Golos Text Medium** | Golos Text | 500 | no |
| `GolosText-Bold.ttf` | Golos Text | — | 700 | no |
| `NotoSansSC-Regular.ttf` | Noto Sans SC | — | 400 | no |
| `SpaceGrotesk.ttf` | **Space Grotesk Light** | Space Grotesk | **300** | **yes — `wght` 300…700, default 300** |

`SpaceGrotesk.ttf` is byte-identical to the Android copy (`md5 effdd4f91ca207acce255f127a81d842` ==
`V2rayNG/app/src/main/res/font/spacegrotesk.ttf`).

### Body and title roles — correct

`Font.Ui` (`GlobalStyles.axaml:51`) is the folder form
`avares://departament/Assets/Fonts#Golos Text`. Avalonia 12's `FontCollectionBase.TryAddFontSource`
registers each face under **both** its `FamilyName` and its `TypographicFamilyName`, so the family
"Golos Text" ends up holding real 400 / 500 / 700 masters.

* `TextBlock.Body` 14/400 → **Golos Text Regular**, real master. Correct.
* `TextBlock.Title` / `SectionHeader` 16/700, `Headline` 24/700 → **Golos Text Bold**, real master.
  Correct, and it **matches Android** — `res/values/styles.xml:101-109` sets
  `TextAppearance.App.Title` to `@font/golos_text_bold` at `textFontWeight 700`, same 16sp/20sp.
* `TextBlock.TitleMedium` 16/500 → **Golos Text Medium**, real master, matching Android's
  `TextAppearance.App.Title.Medium` (`styles.xml:119-123`).

No synthetic bold anywhere in the Golos roles. **The body and title faces are not the complaint.**
One fragility to record: Medium only resolves because Avalonia indexes typographic family names; the
face's own nameID 1 is "Golos Text Medium", a *different* family. If the collection ever falls back to
nameID-1-only matching, every 500 collapses to 400 (CSS nearest-match descends from 500 to 400).
The comment at `GlobalStyles.axaml:40-49` predicted exactly this and chose the safe side; it is right.

### Where the weight is actually wrong — the brand face

`Font.Brand` (`GlobalStyles.axaml:52`) —
`avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk` — resolves (via nameID 16) to the
file's **default instance, Light 300**. There is no static master and Avalonia's `FontFamily` URI has
no way to pin a variation axis. Then `FontCollectionBase` (lines 490-522) applies
`FontSimulations.Bold` whenever the requested weight is ≥ Bold and the matched face is < Bold. So:

| Role | Asked for | Gets | Result |
|---|---|---|---|
| `TextBlock.Display` 34/Bold (337-345) | Space Grotesk 700 | Light 300 + **algorithmic emboldening** | the largest figure on the screen is a **fake bold** — smeared, blunt, too thick |
| `TextBlock.Wordmark` 20/Bold (430-437) — "departament" in the title bar | 700 | Light 300 + **fake bold** | same, on the one word that is the brand |
| `TextBlock.Chip` 11/Medium (396-403), 12 uses | 500 | Light 300, no synthesis | too **thin** |
| `TextBlock.Numeric` /Medium (408-413), 29 uses — traffic, speed, ping, price, balance | 500 | Light 300 | too **thin** |
| `TextBox.Field.Numeric` (2242-2245) | 500 | Light 300 | too thin |

So the brand roles are simultaneously too fat (the two Bold ones, which are the biggest type on the
screen) and too thin (the numerals). That is «шрифт какой-то толстый»: it is the Display figure and the
wordmark, drawn by Skia's fake-bold, not by a designed bold.

**Android already fixed this and the desktop never got the fix.** `res/font/space_grotesk.xml` pins the
instance per entry with `android:fontVariationSettings="'wght' 400/500/700"`, and its own comment says
it plainly: *«before this change every "700" brand run in the product was drawn at Light 300 while every
style file looked correct»*. The desktop is still in that "before" state.

Two further contributors to the same complaint:

* `AppBuilderExtension.cs:5-6, 35-39` sets `FontManagerOptions.DefaultFamilyName` to
  `Assets/Fonts#Noto Sans SC` **and** puts Noto Sans SC first in the fallback chain. Noto Sans SC is
  vendored Regular-400-only, so any run that reaches the default or the fallback at `FontWeight=Bold`
  is fake-bolded too. Golos covers Russian, so this bites only on glyphs Golos lacks — but it is the
  same failure mode and the same fix.
* `GlobalResources.axaml:301` (`Font.Grotesk`) and `:315` (`Font.Numeric`) are two more keys pointing
  at the same `SpaceGrotesk.ttf#Space Grotesk`, with 17 references between them. They inherit the
  defect verbatim.

**Fix, in order of value.**

1. Vendor real static masters — `SpaceGrotesk-Medium.ttf` (500) and `SpaceGrotesk-Bold.ttf` (700) —
   into `Assets/Fonts/`, and change `Font.Brand` (and `Font.Grotesk`, `Font.Numeric`) to the **folder**
   form `avares://departament/Assets/Fonts#Space Grotesk`, exactly as `Font.Ui` already is. Weight
   selection then hits real masters and `FontSimulations.Bold` never fires. This is the same vendoring
   the Android comment names as the endgame for both platforms, so doing it here should ship the same
   two files to `V2rayNG/app/src/main/res/font/` and retire the API-28 variation path there.
2. Until (1) lands, do not request Bold from `Font.Brand`. A Display figure at Light 300 is wrong but
   *honest*; a fake bold is wrong and ugly.
3. Point `FontManagerOptions.DefaultFamilyName` at Golos Text (the app's actual UI face) and keep Noto
   Sans SC only as a fallback entry for CJK.
4. Verify afterwards on a Windows build, at 100 % and 150 % scaling, that the wordmark, the Display
   figure and one Numeric run all render from the intended master. The measurement that proves it is
   the one used here: read `usWeightClass` from the resolved face, not a screenshot.

---

## 8. Ordered fix list

| # | Change | File | Why it is first |
|---|---|---|---|
| 1 | Delete the hover `BrushTransition`s listed in §1 (keep the *state* transitions) | `GlobalStyles.axaml`, `BottomNavBar.axaml`, `MainWindow.axaml`, `AccountView.axaml`, `GlobalResources.axaml` | one shared cause, ~20 classes, precedent already in-tree twice |
| 2 | Move press-scale off the button onto a fixed-size inner surface | `GlobalStyles.axaml` (all `:pressed` `RenderTransform` blocks) + the button markup | stops the hit area moving; also fixes taps that miss |
| 3 | Make hover an overlay layer instead of a replacement fill | `GlobalStyles.axaml` button templates | stops Tonal/Stepper/chips losing their body on hover |
| 4 | Spell out `Button.Icon.Filled:pointerover` / `:pressed` | `GlobalStyles.axaml:2052` | filled icon buttons currently have no hover at all |
| 5 | Re-express the scrollbar thumb hover without a re-measure | `GlobalStyles.axaml:185-203` | last layout-affecting hover in the product |
| 6 | Vendor Space Grotesk 500/700 static masters; switch `Font.Brand`/`Font.Grotesk`/`Font.Numeric` to the folder URI | `Assets/Fonts/`, `GlobalStyles.axaml:52`, `GlobalResources.axaml:301,315` | D2 — kills the fake bold on the Display figure and the wordmark |
| 7 | Default family → Golos Text, Noto Sans SC demoted to fallback | `Common/AppBuilderExtension.cs` | removes the last synthetic-bold path |

None of these removes an animation the owner values. Item 1 removes *hover* crossfades only; the press
gesture, the connect-hero choreography, the nav indicator slide, the tab crossfade, the skeleton pulse,
the spinner and the theme flood all stay untouched. Item 2 keeps the press scale, its 0.97 value and its
90/160 ms asymmetry — it only changes which element carries it.
