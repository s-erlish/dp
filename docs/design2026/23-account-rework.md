# 23 - Account tab rework (Android + Desktop)

**Status: specification. Implementable as written.** Where this file gives a number, that number ships.
Where it gives a Russian string, that string ships. Nothing here is a sketch. The one precondition this
file used to carry - which Cyrillic face the Russian UI is set in - is **resolved in section 4.0** and
recorded as decision A-15.

**Supersedes**, on every point where they disagree: any earlier document or habit that treats
`Views/AccountView.axaml` as the parity reference for Android. Both Account surfaces are subjects of
this redesign. The desktop is richer in *function* and wrong in *form*; the Android tab is wrong in
both. Neither is copied.

**Authority.**

1. `docs/design2026/00-rules.md` is law and is cited as **§n**.
2. **`22-components.md` is the control vocabulary and is cited as C§ with the section's own title**
   (`C§8.3 Row.Value`, `C§6 Segmented control`), per that file's §0.3 convention. It declares itself
   the entire component library (its R15) and rules at its §0.4 that *"if a screen needs a control
   that is not here, the control is added here first."* **This file invents no control.** Every
   button, row, chip, field, sheet, snackbar, skeleton and meter on the Account tab is a
   `22-components.md` component called by its registry name from that file's §19. The three things
   this tab needs that the library does not yet have are listed in section 3.3 as amendments to be
   made **to `22-components.md` first**, in its own format.
3. `03-direction.md` is the design direction and is cited as **D§n**.
4. The factual baseline is `20-control-survey.md` (cited as **CS**) and `21-account-survey.md`
   (cited as **AS**).

Where this file adds a token, a rule or a terminology entry, it says so explicitly in section 13 in
`00-rules.md` §18 row format, because §18 is the only way law changes. Where it takes a position
against `22-components.md`, it says so in the same format, naming the ruling it overrides
(section 13.2).

**Sibling specs, and who owns what.**

| Surface | Owner | This file |
|---|---|---|
| The component library | `22-components.md` | calls it, never redefines it |
| The signed-out gate, both platforms | `14-auth.md` §5 | states the two contract points the tab depends on (5.1) and one geometry correction (13.2) |
| The Android tab, implementation depth | `15-account-tab.md` | agrees with it; this file is the cross-platform contract both halves are held to |
| The token layer | `10-design-system.md` + `22-components.md` §20 | adds four tokens (section 12) |

**Scope.** The Account tab on both clients, plus the four surfaces that only it opens: Devices,
Payment history, Sign-in methods, and the shared payment surface. The Buy catalogue screen
(`BuyTariffActivity` / `BuyView`) is specified separately; this file specifies its entry points, its
money contract and the payment surface it must adopt.

---

## 0. Reading order for the implementer

1. Section 1 - what the tab is for. If a decision later in this file surprises you, it is derived here.
2. Section 3 - **which library components this tab uses**, by registry name. It is a mapping table,
   not a vocabulary: geometry, type and the eight states live in `22-components.md` and are not
   restated here, because two files describing one button is how the two apps drifted apart.
3. Section 4 - typesetting. The UI face (4.0), then money, dates and counts, which are the product's
   voice (D§3.1); get them wrong and nothing else matters.
4. Then your platform: section 6 (Android) or section 7 (Desktop). **The two sections carry the same
   twelve states and the same row lists.** Where a desktop value equals Android's, section 7 says
   "as 6.x" against that element rather than omitting it.
5. Section 8 is the complete copy. Do not invent a string; if you need one that is not here, the design
   is incomplete and you stop.
6. Section 9 binds every rendered value to the field it comes from. **A value not in section 9 does not
   exist and may not be drawn** (AS §4).

---

## 1. What this tab is for

### 1.1 The one sentence

> **Аккаунт - это счёт: сколько осталось, что спишется дальше, и одна кнопка, которая это меняет.**

Four questions, in this order of urgency:

| # | Question | Answered by | Rank on the screen |
|---|---|---|---|
| 1 | **До какого числа у меня есть доступ?** | the subscription card's time block | the one Display figure, always |
| 2 | **Что у меня есть?** | the card's name, tariff badge, traffic, and the devices row | Title weight, under the time block |
| 3 | **Сколько спишется дальше?** | the card's auto-renew line and the CTA's price suffix | Subtitle weight |
| 4 | **Что я могу с этим сделать?** | one CTA on the card, then rows | one filled button at most, then a ledger |

Everything that answers none of the four leaves the tab. What left, and where it went, is section 1.3.

### 1.2 The hierarchy this forces, and the inversion it produces

Both clients today make **balance** the 34sp Display hero (AS §1.2, §2.2). That is wrong. Balance
answers question 3 only indirectly and question 1 not at all. The scene sentence in D§2.1 is explicit:
*"He will open the app again that evening only if the subscription is running out."* The user opens
this tab because of **time**, not money.

So:

- **The hero figure is time, and it is always there.** The card's second line is always
  `[figure][word]` - a 34sp figure-face number beside a 16sp UI-face word, baseline-aligned. The
  **silhouette is constant**; the **colour is the state channel**. Healthy, it is the expiry date's
  day number in `colorOnSurface` («Активна до / **3** августа»). Under 7 days it becomes the day
  count in amber («Осталось / **5** дней»). Expired it becomes the date it died, in red
  («Истекла / **31** мая»). One shape, three temperatures, no layout change between them.
- **Balance drops to a row value** in the «Оплата» group, at Title/Numeric weight.
- **Identity drops to a two-line head** with a 40dp tile, no card, no 52dp avatar frame, no camera badge
  floating on it. This is owner request §0.4.5 («tightened profile») taken literally.

**Why a constant figure and not a state-dependent one.** An earlier draft of this file removed the
Display figure above 7 days, on the theory that a calm account deserves a calm screen. That failed
§4.3: with the figure gone the healthy tab - which is the state the overwhelming majority of sessions
open in - had one bordered card, three section headers at 16/700 and eight rows whose titles are also
16/700, and nothing visually dominant. Blurred, it was a dark settings list. Calm still needs a
hierarchy. Keeping the figure gives the tab a permanent first object; making its **colour** carry
health keeps the state legible without a second Display figure, a chip, or a badge, and it keeps
D§7.3's "one display figure per screen" at exactly one.

That inversion is also the answer to the category-reflex test (§2.4.1, D§2.3): a VPN account screen
whose largest element is a date, not a wallet, is not the category default, and it is honest about
what the product sells - time.

### 1.3 What leaves the tab, and where it goes

| Today | Verdict | New home |
|---|---|---|
| Balance as Display 34sp | demoted | row value in «Оплата», Title 16/700 numeric |
| Icon tiles on the tab's own rows | deleted | see 1.4; the Account tab's rows are tile-less, text origin 16 |
| Filled «Пополнить» button in the hero | deleted | the «Баланс» row opens the top-up sheet |
| Referral chip in the hero | moved | row «Реферальный код» in «Оплата», tap copies |
| Avatar 52dp frame + 18dp camera badge | deleted | 40dp tile; tapping it opens the avatar options |
| Subscription carousel (both platforms) | deleted | segmented switcher (2-3 subs) or a select row (4+) |
| Desktop kebab «Ещё» four-panel wizard | deleted | «Улучшить тариф» row; add-devices moves to Devices |
| Desktop health chip (`.paid/.pending/.failed`) | deleted | the state is the typography, not a chip |
| Desktop traffic gradient pill | deleted | a 4dp neutral meter under a labelled value |
| Desktop «Веб-кабинет» row | deleted | out of scope of the four questions; Android cannot do SSO |
| Desktop Google «Скоро» disabled button | deleted | the Google row exists only when Google is linked |
| Android device HWID third line | deleted | shown only as a 4-char disambiguator when two rows collide |
| Android «Ответ сервера (диагностика)» dialog | deleted | a designed error state; the raw body goes to the log |
| Android «HTTP %s\n%s» payment dialog | deleted | §9.4 error copy, no codes |
| QR / connect link (dead on both) | designed in | the **Devices** sub-page, «Подключить устройство» group |
| Add devices (desktop kebab, Android dead) | designed in | the **Devices** sub-page, one row + sheet |
| Rename subscription (dead on both) | designed in | a 48dp edit button in the card header |
| Referral **stats** (`getReferralStats`) | not surfaced | see decision A-11 |
| Promo code (`checkPromo`/`activatePromo`) | not surfaced | see decision A-12 - it cannot honour AS §4.4 |
| 2FA (`totpEnabled`) | not surfaced | read-only with no endpoint to change it is noise (§F16) |

### 1.4 Where the accent is spent on this tab (the behavioural rule)

D§3.2 allows one lit element per screen and says the Account tab's is «Купить», *"only when the account
state needs it"*. Made mechanical:

Accent-tinted means anything drawn in `colorPrimary` / `colorPrimaryContainer`: a Tertiary label, the
tariff badge, a selected segment, a focus ring while it is showing. C§R14 caps Tertiary at two visible
at once and allows the tariff badge as item 7; both caps hold here.

| Account state | Filled accent surfaces | Accent-tinted elements | Total |
|---|---|---|---|
| Signed out (gate) | 1 - «Войти через Telegram» | 1 - «Войти по почте» Tertiary label | 2 |
| No subscription, trial available | 1 - «Начать пробный период» | 1 - «Купить» Tertiary label | 2 |
| No subscription, trial used | 1 - «Купить» | 0 | 1 |
| Trial | 1 - «Купить тариф» | 0 - the «Пробный» badge is `Chip.Neutral` | 1 |
| Expired | 1 - «Продлить, 450 ₽» | 1 - tariff badge | 2 |
| Expiring (1-7 days) | 1 - «Продлить, 450 ₽» | 1 - tariff badge | 2 |
| **Active (> 7 days)** | **0** | 2 - tariff badge, «Улучшить тариф» Tertiary label when it exists | 2 |
| Perpetual | 0 | 2 - tariff badge, «Улучшить тариф» when it exists | 2 |
| Loading / error / offline | 0 | 1 - the «Повторить» / «Обновить» Tertiary label | 1 |

**Retry is `Button.Tertiary` everywhere on this tab and its sub-pages** - in the cold error card, in
the partial-failure half, in the offline bar, in the payment-polling bar, in every snackbar action.
One control, one colour, one word. It is counted as one accent-tinted element in the last row above.
An accent *label* on a transparent control is not a lit surface and does not compete with a CTA; and
an error state must never spend the screen's one **filled** accent on recovering from a failure. (An
earlier draft said "tonal" in two places and "V3 accent" in a third for the same «Повторить»; this
line is the resolution.)

**The tariff badge is the product's one `Chip.Accent`** - `colorPrimaryContainer` `#17325C` with
`colorOnPrimaryContainer` `#CFE0FF`, 9.57:1 - per C§R14 item 7 and C§10 Chip ("the tariff badge, and
nothing else"). D§3.2's Account row says "everything else, including the tariff badge" is neutral;
that sentence governs what may be **lit**, meaning `colorPrimary` at full strength, and a
container-tinted chip is not that. Recorded as decision 13.2 C-4. The **trial** badge is
`Chip.Neutral`: «Пробный» names a state, not a tariff.

**Icon tiles: the tab has none.** The earlier ruling - "every tile on the tab is neutral" - fixed the
colour and kept the noise: twelve identical 40dp grey squares with twelve identical grey glyphs,
distinguishing nothing, pushing every text origin to 68 on rows whose *value* is the point («Баланс»,
«Реферальный код», «История платежей»). §2.4.4 asks what each non-text pixel communicates; those
communicated nothing. So:

| Surface | Tiles? | Text origin | Why |
|---|---|---|---|
| The Account tab's three row groups | **none** | **16** | the group header already carries the category; the row's noun is the whole content |
| Devices | 40dp neutral tile, 22dp glyph | 68 | the platform glyph (Android / Apple / Windows / Router) is the one thing that tells two identically-named rows apart |
| Sign-in methods | 40dp neutral tile, 22dp glyph | 68 | the brand mark *is* the method's identity and is read faster than the word |
| Payment sheet / flyout, Payment history | none (ledger rows) | 16 | transaction surfaces, decision A-2 |

Every tile that survives is `icon_tile_neutral` `#20242B` / `Brush.Tile.Neutral` with a `#9BA1AD`
glyph. There are no blue tiles, no green tile on the balance payment row, no accent row titles, and
the tab uses **zero** of C§R14 item 8's three permitted categorical coloured tiles. That removes
CS §C.4.22 ("56 of 65 Android icon tiles are blue"), AS §1.4.6, AS §2.3.3 and AS §1.8's green tile,
and it makes the Account tab visibly quieter than Settings - which is correct, because Settings is a
catalogue of categories and this is a statement of account.

Non-accent colour on the tab, and its only permitted uses:

| Colour | Where, and nowhere else |
|---|---|
| Amber `?attr/colorWarning` / `Brush.WarnText` | the Display figure and its word in the **expiring** and **expiring-today** states; the traffic meter fill at ≥ 90 %; the «В обработке» status word in payment history (C§R12) |
| Red `?attr/colorErrorText` / `Brush.RedText` | the **expired** card's figure, word and label line; the traffic meter fill at 100 %; the «Выйти» row title; inline form errors; the «Ошибка» status word in history |
| Green `?attr/colorTertiary` | the «Оплачено» status word in history, and nothing else |

Colour is never alone (§6.3): the expiring state also carries the word «Осталось» and a filled CTA;
the expired state also carries the word «Истекла»; «Оплачено» is a word before it is a colour.

---

## 2. Information architecture

One order, both platforms, no exceptions (§13).

```
┌ P0 ground, gutter 16 ───────────────────────────────────────┐
│ [offline bar]           only when offline                    │
│ [payment bar]           only while a checkout is being polled │
│                                                              │
│ HEAD          avatar 40 · name · login handle    (no card)   │
│  32                                                          │
│ [SWITCHER]    segmented (2-3 subs) | select row (4+ subs)    │
│  12                                                          │
│ CARD          the subscription            ← the ONE card     │
│  24                                                          │
│ «Подписка»    Устройства              2 / 5                  │
│               Улучшить тариф                ›                │
│  24                                                          │
│ «Оплата»      Баланс                1 500 ₽                  │
│               Купить подписку               ›                │
│               История платежей     12.06.2026                │
│               Реферальный код          ABC123                │
│  24                                                          │
│ «Вход»        Способы входа     Telegram, почта              │
│               Выйти                                          │
│  32 + bottom inset                                           │
└──────────────────────────────────────────────────────────────┘
```

Counts against D§7.3: **3 row groups** (cap 4), **max 4 rows per group** (cap 7), **1 card** (cap 1),
**1 Display figure, sometimes 0** (cap 1), **2 levels below the tab** (cap 2 - tab → Devices → nothing).

**Row trailing discipline** (§4.5 - trailing is exactly one thing, never two). Applied as a rule you can
teach: **a row that carries a value carries no chevron; a row that carries no value carries a chevron.**
The whole row is the target either way.

| Row | Trailing | Why |
|---|---|---|
| Устройства | value `2 / 5` | the value is the point; the tap is a bonus |
| Улучшить тариф | chevron | navigation to a choice |
| Баланс | value `1 500 ₽` | the value is the point |
| Купить подписку | chevron | navigation |
| История платежей | value `12.06.2026` | the latest payment date is the point |
| Реферальный код | value `ABC123` | the code is the point; tap copies |
| Способы входа | value `Telegram, почта` | the summary is the point |
| Выйти | nothing | a terminal action, no destination and no value |

**Origin discipline.** Two surface families, never mixed inside one surface:

- **Management surfaces** (the Account groups, Devices, Sign-in methods): tiled rows, text origin
  **68** = 16 gutter + 40 tile + 12, hairlines start at 68 (`@dimen/row_text_origin`).
- **Transaction surfaces** (the payment sheet, Payment history): tile-less ledger rows, text origin
  **16**, hairlines start at 16.

This is a clarification of §4.1, recorded as decision A-2.

---

## 3. The control vocabulary of this tab

Nine variants. Every interactive thing on the tab and its sub-pages is one of them. Anything that is
not on this list is a defect. Heights, radii and type are fixed here and are not overridden per
instance (CS §C.1 is the disease this cures).

| # | Variant | Android | Desktop | Box | Radius | Label type | Used on this tab for | Accent |
|---|---|---|---|---|---|---|---|---|
| V1 | **Button / Primary** | `MaterialButton` filled, insets 0 | `Button.Primary.Tall` | full width × **52** | `radius_pill` / `Radius.Pill` | `App.Title` 16/700, UI face | the one CTA: «Купить» / «Продлить» / «Войти через Telegram» | **filled accent** |
| V2 | **Button / Tonal** | `MaterialButton` tonal, insets 0 | `Button.Tonal` | full width × **48** | pill | `App.Title.Medium` 16/500 | «Продлить» when the sub is healthy; «Повторить» in error states; «Отмена» in sheets | neutral fill `colorSurfaceContainerHighest` |
| V3 | **Button / Text** | `MaterialButton` text | `Button.LinkAction` | ≥ 48 tall | pill | `App.Title.Medium` 16/500 | «Улучшить тариф» in the card; «Войти по почте» on the gate | accent **label**, no fill |
| V4 | **Icon button** | `ImageButton`, `?attr/selectableItemBackgroundBorderless` | `Button.IconButton40` | **48×48** Android / **40×40** desktop, glyph **20** | pill | - | rename, refresh, unlink | neutral glyph |
| V5 | **Row / value** | `layout_row_value.xml` | `RowItem` with `Value` | min **56**, pad 16/8 | - | title `App.Title` 16/700, value `App.Subtitle` 13/400 numeric | Устройства, История, Реферальный код | none |
| V5s | **Row / value strong** | same layout, `App.Title` + Numeric on the value | same, `Classes="strong"` | min 56 | - | value 16/700 numeric `colorOnSurface` | **Баланс only** (and «Итого» in the sheet) | none |
| V6 | **Row / nav** | `layout_row_nav.xml` | `RowItem` with `Chevron` | min 56 | - | title `App.Title`, subtitle `App.Subtitle` | Улучшить тариф, Купить подписку, Способы входа, Выйти | none (Выйти title = `colorErrorText`) |
| V7 | **Row / switch** | `layout_row_switch.xml`, switch `clickable=false` | `RowItem` with `ToggleSwitch.iOS` | min 56 | - | title `App.Title`, subtitle `App.Subtitle` | Автопродление | accent = switch track when on (a state the user controls, §5.1) |
| V8 | **Segmented control** | `MaterialButtonToggleGroup`, `singleSelection`, `selectionRequired` | `ToggleButton.Segment` | **44** tall, weight 1 each | `radius_chip` 12 | `App.Title.Medium` → 16/**700** when selected | the 2-3 subscription switcher | 12 % accent fill + 1dp accent border + accent label |
| V9 | **Sheet / flyout** | `BottomSheetDialogFragment`, `bg_sheet_top` r24 top | `Flyout` with `IncyFlyoutTheme` r20, anchored | - | 24 top / 20 | - | payment, top-up, upgrade, sub-pick, QR, add-devices | none |

Notes that resolve open contradictions in CS:

- **V1/V2/V3 fix D5 and D6.** Android's five 52dp CTAs currently draw at 40dp because they never zero
  `insetTop`/`insetBottom` (CS §C.1.3). Every button on this tab sets `android:insetTop="0dp"`
  `android:insetBottom="0dp"` and its height comes from `@dimen/cta_height` 52 or
  `@dimen/view_height_dp48`. Every button carries
  `android:textAppearance="@style/TextAppearance.App.Title(.Medium)"` - so no button label is Roboto
  by accident and **no button carries `android:textStyle="bold"`** (§3.4 bans synthetic bold).
- **Tonal is neutral on both platforms.** `Button.Tonal` on desktop is already
  `SurfaceHighest`/`OnSurface`; Android's `Widget.Material3.Button.TonalButton` is restyled to
  `?attr/colorSurfaceContainerHighest` / `?attr/colorOnSurface`. This removes a second blue and makes
  the two platforms agree (§13).
- **V4 is 48 on Android and 40 on desktop** by §7.2. That is a permitted platform difference, not a
  parity gap. Android's current 44dp delete button (CS §A.3) and the desktop's three `Height="32"`
  shrinks (CS §B.3) both die.
- **V8 replaces both carousels.** See section 5.2 for why.

### 3.1 The eight states, per variant

Filled once here; not restated per screen. Reduced motion collapses every duration to 0 and jumps to the
end state (§8.8).

| Variant | Default | Hover (desktop) | Pressed | Focus | Disabled | Loading | Selected |
|---|---|---|---|---|---|---|---|
| V1 | accent fill, `colorOnPrimary` label | `Brush.AccentHover` 150 ms `Ease.Standard` | scale **0.97**, 90 in `ease_out_quart` / 160 out `ease_out_quint`; Android also ripple | 2dp accent ring, 2dp offset, pill | content alpha **0.38**, no ripple, `isEnabled=false` | label replaced in place by a **20dp** indeterminate indicator in `colorOnPrimary`; **the button keeps its width and height**; disabled while in flight | - |
| V2 | `colorSurfaceContainerHighest` fill | `Brush.Hover` | same 0.97 | same ring | 0.38 | same contract, indicator in `colorOnSurface` | - |
| V3 | accent label, no fill | `Brush.Hover` behind, r12 | same 0.97 | same ring, r12 | 0.38 | same contract | - |
| V4 | glyph `colorOnSurfaceVariant` | `Brush.Hover`, pill | 0.97 | ring, pill | 0.38 | glyph replaced by a 20dp indicator | - |
| V5 / V5s / V6 | transparent on P0 | `Brush.Hover` over the whole row | 0.97, whole row | 2dp ring inset to the row bounds, r12 | 0.38 on all content, row not clickable | the trailing value is replaced by a **16dp** indicator | - |
| V7 | as V5 | as V5 | as V5 | ring on the row | 0.38 | the **subtitle** swaps to «Сохраняем…»; the row is not clickable; the switch shows the optimistic position | switch on = accent track |
| V8 | transparent, 1dp `colorOutline`, label 16/500 `colorOnSurfaceVariant` | `Brush.Hover` on unselected only | 0.97 | ring, r12 | 0.38 | - | 12 % accent fill + 1dp `colorPrimary` + label 16/**700** `colorPrimary` - **two channels** (§5.4) |
| V9 | `colorSurface`, scrim 60 % | - | - | focus moves into the sheet on open and returns to the trigger on close | - | - | - |

### 3.2 Every named control on this tab, and its variant

The demand was "every one of those is a control; say which component-system variant it uses and why,
and where the accent is spent". Here is that list in full, in the order the tab renders them.

| Control | Variant | Why this variant | Accent |
|---|---|---|---|
| Identity / plan hero | **not a control** - a 40dp tile + two `TextBlock`/`TextView` lines | it answers none of the four questions by being pressable; only the tile is a target (avatar options) | none |
| Avatar | **V4** semantics inside a 48dp touch box | one icon-sized target, one action | none |
| Subscription switcher, 2-3 | **V8** segmented | §11.2 "choice among 2-4"; both options stay visible, keyboard-navigable, no drag | 12 % fill + accent border + accent label on the selected segment (a state the user controls, §5.1) |
| Subscription switcher, 4+ | **V5** value row → **V9** radio sheet | §11.2 "choice among many"; a 12-item segmented control does not exist | accent radio on the selected item |
| Tariff badge | a **chip**, not a control | it is a label, not a target; it carries the tariff, which the card title does not (so it is not §2.4.4's repeating chip) | **none** - D§3.2 lists the badge among the things that are explicitly not lit |
| Rename | **V4** icon button, 48/40 box | one icon, one action, no room for a labelled button in a header that already holds a name and a chip | none |
| Time block (expiry) | **not a control** | a fact; the action it implies is the CTA below it | amber figure when expiring, red title when expired; **never blue** |
| Traffic meter | **not a control** | a readout of a quantity the user does not directly control | **none** - neutral fill, amber ≥ 90 %, red at 100 % |
| Renew | **V1 filled** when expiring / expired / trial / empty, **V2 tonal** otherwise | the single lit element earns its light only when the state needs action (D§3.2); a permanently blue button teaches nothing | the tab's one filled accent surface, conditionally |
| Upgrade | **V3 text button** in the card, plus a **V6** row in the «Подписка» group | it is the second-most-wanted action, so it is one tap deep, but it must not compete with renew; a text button is visibly subordinate to a filled or tonal one | accent **label** only |
| Add devices | **V5** row on the Devices page → **V9** stepper sheet | it changes a number that is printed on the Devices page; putting it anywhere else separates the control from its value | none |
| Device management | **V5** value row → sub-page of tiled rows with a **V4** trailing unlink | the row is the target, the icon button is the exception; §4.5's "one trailing" is relaxed only here, and only because the row's content and its action are different things | none; the unlink glyph is neutral at rest (§6.4) |
| Balance | **V5s** value row (16/700 numeric) → **V9** top-up sheet | money is the one trailing value on this tab that carries hierarchy; the 13sp grey default would bury it | none - «Пополнить» is **not** a filled button any more |
| Top-up | **V9** sheet: label + field + method rows | §7.6 prefers a sheet over a dialog; §7.4 requires a visible label, a helper slot and blur validation, none of which the current dialog has | none |
| Auto-renew | **V7** switch row inside the card | §11.2 "toggle → `MaterialSwitch`"; the row owns the switch so there is one hit target, not two | the switch track when on |
| Payment method | **V9** sheet of tile-less **ledger rows**, one component for all five callers | the #1 defect in AS §5 is two components for one decision; a chevron on a row that charges money is a lie, so there is no chevron | none |
| Payment history | **V5**-shaped **value row** → a sub-page of tile-less ledger rows | a payment is a fact, not an object; §2.4.3's uniform-card tell is exactly what the current card list is | green «Оплачено» / red «Ошибка» words only |
| Referral code | **V5** value row, tap copies | one value, one action, one Snackbar; no chevron because it does not navigate | none |
| Account linking | **V5** value row → sub-page of **V5/V6** rows → **V9** sheets | linking is three methods and two flows; a sub-page keeps the tab at three groups (D§7.3) | none |
| Sign out | **V6** row, title in `colorErrorText`, then a confirm dialog | a dialog is correct here precisely because there is no undo path (§7.5) | red **text**, never a red button on the tab |
| Retry, in every error state | **V2 tonal** | an error state must not spend the screen's one filled accent on recovering from a failure | none |
| Sign-in gate | **V1** «Войти через Telegram» + **V3** «Войти по почте» | one primary, one alternate; D§10.1 forbids a card here | the gate's one filled surface |

**Press physics are one recipe.** `@anim/press_scale` is corrected from **0.96 → 0.97** and its
release interpolator from `ease_out_quart` → `@interpolator/ease_out_quint` (§7.1). Every clickable
surface on this tab and its sub-pages carries it. `@anim/nav_press` (0.92, linear, off-token durations)
is not used here.

---

## 4. Typesetting: money, dates, counts, traffic

D§3.1 makes numbers the product's identity. This section is the contract.

### 4.1 One money formatter, one currency

There are six formatters today and three of them print `$` (AS §3.3). All six are deleted.

- **Android:** `object Money { fun format(amount: Double): String }` in
  `com.v2ray.ang.util.Money`. Replaces `AccountFragment.currencySymbol`,
  `BuyTariffActivity.currencySymbol`, `PaymentsAdapter.formatMoney`.
- **Desktop:** `static class Money { static string Format(decimal amount) }` in
  `v2rayN.Desktop/Common/Money.cs`. Replaces `AccountViewModel.CurrencySymbol`,
  `PaymentHistoryViewModel.CurrencySymbol`, `BuyViewModel.CurrencySymbol`.

Rules, identical:

1. The symbol is always **`₽`** (U+20BD), whatever `currency` says (§0.4.4). The backend's currency code
   is ignored for display and preserved for the request body.
2. Thousands separator: **U+2009 THIN SPACE**. `1290` → `1 290`.
3. Before the symbol: **U+00A0 NO-BREAK SPACE**. `1 290 ₽`.
4. Kopecks are printed **only when non-zero**, with a **comma**: `1 290,50 ₽`, but `1 290 ₽`.
5. Rendered with `TextAppearance.App.Numeric` / `Font.Numeric`, `tnum` + `lnum` **on**, **`zero` off**
   (a slashed zero in a price reads as a correction mark, D§3.1).
6. Zero balance prints `0 ₽`, never «-», never an empty string.

Width reservation (D§3.1): a numeric column reserves `digits × 0.620 × fontSize`. The balance row's
trailing value is `wrap_content` with the title constrained to `0dp`/weight 1, so a 12-digit balance
ellipsises the **title**, never the money.

### 4.2 Dates, and the two-face rule applied

Space Grotesk contains **zero Cyrillic** (D§6.1). Therefore:

| Form | Face | Example | Where |
|---|---|---|---|
| Long, with a Russian month | **UI face** (the whole string) | `3 августа 2026` | card time lines, auto-renew line |
| Long, current year | UI face | `3 августа` (year omitted when it is this year) | same |
| Short numeric | **figure face**, `tnum`, `zero` off | `12.06.2026` | row trailing values, sub-pick sheet |
| Numeric with time | figure face | `12.06.2026, 14:32` | payment history rows |
| Month header | UI face, sentence case | `Июнь 2026` | payment history group headers |

**A string that contains Cyrillic is never set in the figure face** (§F5). Mechanically checkable: no
`TextView` with `TextAppearance.App.Numeric` and no `TextBlock` with `FontFamily={DynamicResource
Font.Numeric}` may contain a Cyrillic character.

The Display hero is the one place a figure gets its own slot: the number is a `TextView`/`TextBlock` in
the figure face, the word beside it is a **separate** element in the UI face, baseline-aligned. That is
exactly the split D§3.1 prescribes.

### 4.3 How an expiry reads at 30 days, at 3 days, and expired

`daysLeft = ceil((expireAt - now) / 86 400 s)` in the device's local zone.
`perpetual = year(expireAt) >= 2099 || daysLeft > 3650` (mirror of the desktop `IsEffectivelyPerpetual`;
Android has no such handling today and would print «Действует до 04.06.2099», AS §4.2).

| Health | Condition | Display figure | Line 1 | Line 2 | Colour | Card CTA |
|---|---|---|---|---|---|---|
| **Perpetual** | sentinel | none | «Бессрочная подписка» `App.Title` | «Срок не ограничен» `App.Subtitle` | onSurface / onSurfaceVariant | V2 tonal «Продлить · 450 ₽» (hidden if no price) |
| **Active** | `daysLeft > 7` | **none** | «Активна до 3 августа 2026» `App.Title` | «Осталось 214 дней» `App.Subtitle` | onSurface / onSurfaceVariant | **V2 tonal** «Продлить · 450 ₽» |
| **Expiring** | `1 ≤ daysLeft ≤ 7` | **`5`** `App.Display` 34/700 numeric + «дней» `App.Title` 16/700 UI face | *(the figure line is line 1)* | «Активна до 3 августа 2026» `App.Subtitle` | figure **and** unit `?attr/colorWarning`; line 2 onSurfaceVariant | **V1 filled** «Продлить · 450 ₽» |
| **Expiring today** | `daysLeft == 0`, not yet past | none | «Истекает сегодня» `App.Title` | «Доступ прервётся в 23:59» - **no**, see below | `colorWarning` | **V1 filled** |
| **Expired** | `expireAt` in the past | none | «Подписка истекла» `App.Title` | «Срок закончился 31 мая 2026» `App.Subtitle` | line 1 `?attr/colorErrorText`; line 2 onSurfaceVariant | **V1 filled** «Продлить · 450 ₽» |
| **Unknown** | `expireAtIso` null/blank | none | «Срок неизвестен» `App.Title` | «Обновите страницу или проверьте позже» `App.Subtitle` | onSurfaceVariant both | V2 tonal, or hidden if no price |

For **expiring today**, line 2 is «Активна до 3 августа 2026» like the other expiring states. We do not
print a wall-clock cut-off: the backend gives a date, and inventing an hour would be a value not in
section 9.

The three you were asked about, spelled out:

- **30 days:** no big number. The card says «Активна до 3 августа 2026» in 16/700 white, and under it
  «Осталось 30 дней» in 13/400 grey. Nothing on the screen is coloured. The CTA is a neutral tonal
  «Продлить · 450 ₽». The screen is calm because the account is calm.
- **3 days:** the card's first line becomes `3` at 34sp in the figure face, amber, with «дня» at 16/700
  amber beside it, baseline-aligned, and the date moves to line 2 in grey. The CTA becomes the filled
  accent «Продлить · 450 ₽». Exactly one thing on the screen is blue and exactly one is amber.
- **Expired:** the first line is «Подписка истекла» in `#FF6069`, line 2 «Срок закончился 31 мая 2026»
  in grey, the traffic meter is hidden (its data is stale and meaningless), the auto-renew row keeps its
  switch but its subtitle becomes «Продление вручную» or the next-charge line if one exists, and the CTA
  is the filled «Продлить · 450 ₽». The «Улучшить тариф» button is hidden - you cannot upgrade a dead
  subscription.

### 4.4 Russian plurals

Days, devices and payments need three forms. Android uses `<plurals>`; the desktop has no plural
machinery, so add `Common/Plural.cs`:

```
form(n): let a = n % 100, b = n % 10
  if 11 <= a <= 14        -> MANY   («дней», «устройств»)
  else if b == 1          -> ONE    («день», «устройство»)
  else if 2 <= b <= 4     -> FEW    («дня», «устройства»)
  else                    -> MANY
```

Required plural sets: `account_days` (день / дня / дней), `devices_count` (устройство / устройства /
устройств), `history_payments` (платёж / платежа / платежей - used only in accessibility labels).

### 4.5 Traffic and device counts

- **Traffic is root-only** (AS §4.2). The traffic block renders **only** when the raw remnawave record
  exists **and** `trafficLimitBytes != null`. On a secondary subscription and on any unlimited plan the
  block is **absent** - not an empty meter, not a «безлимит» line. Its absence is the statement.
- Formatter `Bytes.format(bytes)`: `< 1024 КБ` → `«%d КБ»`; `< 1024 МБ` → `«%d МБ»`; else
  `«%,.1f ГБ»` / `«%,.1f ТБ»` with a **comma** decimal. `12,4 ГБ`. Figure face, `tnum`, `zero` **on**
  (traffic is a technical figure, D§3.1).
- The value line reads `«12,4 из 100 ГБ»` - the unit appears once, at the end, in the UI face; the two
  figures are in the figure face. At 100 % the value line reads **«Трафик исчерпан»** in
  `?attr/colorErrorText` and the meter fill is `?attr/colorErrorText`.
- **Device counts.** The live count is `GET /client/devices`.length and exists **only for the active
  subscription** (AS §4.2). So:

| Case | Row value | Row subtitle |
|---|---|---|
| Active sub, limited | `2 / 5` figure face | none |
| Active sub, unlimited (`hwidDeviceLimit <= 0`) | `2` | «Без ограничений» |
| Secondary sub, limited | `5` | «Слотов на подписке» |
| Secondary sub, unlimited | none | «Без ограничений» |
| Count not yet loaded | the trailing 16dp indeterminate indicator (V5 loading) | none |

No `∞` glyph anywhere: it is not guaranteed present in the vendored font file, and «Без ограничений» is
a word, which §6.3 prefers.

---

## 5. Decisions this tab takes about its own structure

### 5.1 Signed out: the tab stays, and becomes a sign-in gate

AS §5 closes with the one question the redesign must answer deliberately. **The answer is the desktop's:
the Account destination always exists; signed out it renders a gate.**

Reasons, in order:

1. A navigation bar whose destination set changes at runtime is a defect: §7.7 fixes 3-5 destinations and
   §13 requires the set and order to be identical across platforms. Android currently hides
   `nav_account` (`MainActivity.updateAccountGate()`, `MainActivity.kt:1048-1064`) and **force-selects
   Home if the user is standing on the tab when the session drops** - that is §7.7's "never traps the
   user", violated.
2. It gives sign-in a home. Today sign-in is an Activity that a signed-out user has no obvious route to.
3. It matches the desktop, so §13 is satisfied without changing the desktop's answer.

**The one exception is a build-time exception, not a runtime one:** when `BackendConfig.isConfigured()`
is false the destination is removed at start-up and never re-appears. A build without a backend has no
account; a *session* without a token has a gate.

### 5.2 One, two, or many subscriptions

Both carousels are deleted: the Android `ViewPager2` + `SubscriptionPagerAdapter`, and the desktop's
hand-rolled drag/snap over a `ScrollViewer` with tunnel pointer handlers and a 16 ms timer tween
(AS §2.3.10 - §1.3 bans reinventing standard affordances, and the 6 px drag threshold exists only
because the control swallows its own card's button presses).

| Count | Surface | Component | Why |
|---|---|---|---|
| **0** | the empty card | - | §15 "Short content": a single-item layout must not look broken; a zero-item layout is a designed empty state |
| **1** | the card, full width, nothing above it | - | the overwhelmingly common case gets zero chrome |
| **2-3** | **V8 segmented control**, 12 above the card | `MaterialButtonToggleGroup` / `ToggleButton.Segment` | §11.2 "Choice among 2-4 → segmented". Every subscription is **visible** rather than hidden off-screen; keyboard-navigable for free; no drag |
| **4+** | **V6 nav row** «Подписка» + value = the selected name, 12 above the card | tap → **V9 sheet/flyout**, radio list | §11.2 "Choice among many → bottom sheet list with radio" |

Segment label = the subscription's display name, `maxLines=1`, ellipsize end, `layout_weight=1`. At
three segments on a 320dp screen each segment is 96dp wide, which fits «Подписка 2» at 16sp; longer
user names ellipsise, which is acceptable for a *switch* label because the card immediately below
prints the full name.

The selected index lives in the ViewModel, survives configuration changes and tab switches, resets to
the root subscription on a cold start, and is restored by Back (§7.7).

**Every subscription-scoped action passes the selected subscription's own scope**, never a cached root
uuid: `selectedSub.type` (`root`/`secondary`), `selectedSub.id`, and `selectedSub.remnawaveUuid` for the
device endpoints. This is the trap called out in the sibling web repo and in AS §4.2 and it is the
easiest thing to reintroduce.

### 5.3 The one card, and what is not in it

§4.4 permits a card only for a distinct object the user acts on as a unit. On this tab that is the
subscription, and it is the **only** card (D§3.3, D-D). The row groups sit directly on P0 with hairlines.
That removes the desktop's four-identical-cards column (AS §2.3.14) and Android's two-identical-boxes
hero (AS §1.4.5).

Deliberately **not** in the card:

- **Devices** - it is a row in the «Подписка» group, because it is a navigation to a sub-page and rows
  are what navigate.
- **Add devices** - it belongs on the Devices page, next to the count it changes.
- **Payment history** - it is account-scoped, not subscription-scoped.
- **A status chip** - the state is the typography (section 4.3). A chip that says «Истекает» beside a
  line that says «Осталось 5 дней» is §2.4.4's "a chip repeating what the title says".

---

## 6. ANDROID

### 6.1 Files

**Replaced (delete the old file in the same change):**

| Path | Fate |
|---|---|
| `res/layout/activity_account.xml` (560 lines) | → `res/layout/fragment_account.xml` |
| `res/layout/item_subscription_card.xml` | → `res/layout/layout_account_card.xml` (no fixed height) |
| `java/com/v2ray/ang/ui/SubscriptionPagerAdapter.kt` | deleted |
| `res/layout/activity_devices.xml`, `res/layout/item_device.xml` | rewritten |
| `res/layout/activity_payment_history.xml`, `res/layout/item_payment.xml` | rewritten |
| `res/layout/sheet_payment_method.xml`, `res/layout/item_payment_method.xml` | rewritten as the shared payment sheet |
| `res/layout/dialog_top_up.xml` | → `res/layout/sheet_top_up.xml` |
| `res/layout/layout_setting_row.xml`, `layout_setting_toggle_row.xml` | → the three row layouts below (they have **0 call sites** today, CS §A.4) |
| `res/layout/layout_home_account.xml` | the dead `group_login` block and the `✕` **text glyph** are deleted; the chip's avatar becomes the 40dp tile |

**New:**

```
res/layout/fragment_account.xml                  the tab scaffold
res/layout/layout_account_head.xml               identity
res/layout/layout_account_switcher_segments.xml  V8, 2-3 subs
res/layout/layout_account_card.xml               the subscription card, loaded state
res/layout/layout_account_card_empty.xml
res/layout/layout_account_card_error.xml
res/layout/layout_account_skeleton.xml           head + card + rows, static
res/layout/layout_account_gate.xml               signed out
res/layout/layout_status_bar_inline.xml          offline / payment-polling bar
res/layout/layout_row_nav.xml                    V6
res/layout/layout_row_value.xml                  V5 / V5s
res/layout/layout_row_switch.xml                 V7
res/layout/layout_ledger_row.xml                 tile-less transaction row (sheet + history)
res/layout/sheet_payment.xml                     V9, shared by buy / renew / upgrade / devices / top-up
res/layout/sheet_top_up.xml
res/layout/sheet_subscription_pick.xml
res/layout/sheet_upgrade.xml
res/layout/sheet_add_devices.xml
res/layout/sheet_qr.xml
res/layout/dialog_rename_subscription.xml
res/drawable/bg_meter_track.xml, bg_meter_fill.xml
res/drawable/bg_skeleton_bar.xml
res/drawable/bg_segment.xml                      selector for V8
res/drawable/ic_acc_edit.xml, ic_acc_logout.xml, ic_acc_key.xml, ic_acc_link_off.xml,
             ic_acc_qr.xml, ic_acc_cloud_off.xml, ic_acc_person.xml,
             ic_acc_telegram.xml, ic_acc_google.xml, ic_acc_mail.xml
```

The last three are **ports** of the desktop `Geo.Acc.Telegram` / `Geo.Acc.Google` / `Geo.Acc.Mail`
`StreamGeometry` data into 24dp-viewport vector drawables, not redraws (§10.1).

### 6.2 Scaffold, insets, toolbar

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout            <!-- Snackbar host -->
    android:id="@+id/account_root"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="?attr/colorBackground">

  <androidx.core.widget.NestedScrollView
      android:id="@+id/scroll_root"
      android:layout_width="match_parent" android:layout_height="match_parent"
      android:paddingStart="@dimen/screen_gutter" android:paddingEnd="@dimen/screen_gutter"
      android:clipToPadding="false"
      android:scrollbars="none">
    <LinearLayout android:id="@+id/content" android:orientation="vertical"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
  </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- Vertical padding comes from a real inset listener, **not** the current `96 * density` literal
  (`AccountFragment.kt:114`):
  `ViewCompat.setOnApplyWindowInsetsListener(scroll_root) { v, insets -> v.updatePadding(top = space_16, bottom = insets.systemBars.bottom + bottomNavHeight + space_16) }`.
- **No per-tab toolbar.** The tab renders under `activity_main.xml`'s shared wordmark toolbar; the head
  is the screen's title (D§4.3, the app has no chrome). The «Аккаунт» name lives in the bottom-nav label
  and in the accessibility pane title (`ViewCompat.setAccessibilityPaneTitle(root, "Аккаунт")`).
- `sw600dp`: `screen_gutter` becomes 24 via `values-sw600dp/dimens.xml`, and `content` gains
  `android:maxWidth="720dp"` with `layout_gravity="center_horizontal"` (§4.1). Nothing else changes.

### 6.3 Head (`layout_account_head.xml`)

```
LinearLayout  horizontal, gravity=center_vertical, minHeight=@dimen/row_min_height (56)
  FrameLayout  id=avatar_container  40×40 (@dimen/tile_size)
               background=@drawable/bg_icon_neutral   (r12 @dimen/radius_tile, #20242B)
               foreground=?attr/selectableItemBackground
               contentDescription=@string/account_change_avatar
               minWidth/minHeight for touch: wrapped in a 48dp touch delegate
    TextView   id=tv_avatar_initial  match_parent, gravity=center
               textAppearance=@style/TextAppearance.App.Title      (16sp/700)
               textColor=?attr/colorOnSurfaceVariant
    ImageView  id=img_avatar  match_parent, scaleType=centerCrop, visibility=gone
               (rounded to @dimen/radius_tile via a ShapeableImageView with
                shapeAppearanceOverlay cornerSize=@dimen/radius_tile)
    ImageView  id=img_avatar_person  22dp, gravity=center, src=@drawable/ic_acc_person
               tint=?attr/colorOnSurfaceVariant, visibility=gone   (no name, no photo)
  LinearLayout vertical, marginStart=@dimen/space_12, weight=1
    TextView   id=tv_name    textAppearance=@style/TextAppearance.App.Title
                             maxLines=2  ellipsize=end
    TextView   id=tv_handle  textAppearance=@style/TextAppearance.App.Subtitle
                             marginTop=@dimen/space_4  maxLines=1  ellipsize=end
```

- **No camera badge.** The 18dp `bg_avatar_edit` oval is deleted. The avatar tile itself is the
  affordance, with the existing contentDescription «Сменить фото» and the existing options dialog
  («Выбрать из галереи» / «Убрать фото» / «Отмена»).
- **Monogram** = the first grapheme of `telegramName` → `telegramUsername` → `email`, upper-cased. If
  none resolves, the tile shows `ic_acc_person` and no letter. The literal `text="?"` in the current
  layout is deleted.
- **Name precedence, unified across the tab and the Home chip** (they disagree today, AS §1.9):
  `telegramName` → `@telegramUsername` → `email` → «Аккаунт».
- **Handle line** = `@telegramUsername` when Telegram is linked; else `email`; else «Telegram не
  привязан».
- Below the head: `marginTop="@dimen/space_32"` before the switcher or the card (§4.2, "above the first
  section after a hero: 32").

### 6.4 Switcher

**2-3 subscriptions** (`layout_account_switcher_segments.xml`):

```
com.google.android.material.button.MaterialButtonToggleGroup
    id=group_subs  width=match_parent  height=@dimen/segment_height (44)
    app:singleSelection="true"  app:selectionRequired="true"
    android:layout_marginBottom="@dimen/space_12"
  ├ MaterialButton  style=@style/Widget.App.Segment  weight=1  (×2 or ×3)
```

`Widget.App.Segment`: `android:insetTop/Bottom=0dp`, `app:cornerRadius=@dimen/radius_chip`,
`app:strokeWidth=1dp`, `app:strokeColor=@color/segment_stroke` (selector: `colorPrimary` checked,
`colorOutline` unchecked), `android:background`/`backgroundTint=@color/segment_fill` (selector:
`colorPrimary` at 12 % checked, transparent unchecked), `android:textAppearance` =
`TextAppearance.App.Title.Medium`, `android:textColor=@color/segment_text` (selector: `colorPrimary`
checked, `colorOnSurfaceVariant` unchecked). Checked weight steps to 700 by swapping the
`textAppearance` in `MainActivity`-style code (`setTextAppearance`) on the check listener, exactly as
the bottom nav already does (`MainActivity.kt:333`). `stateListAnimator=@anim/press_scale`.

**4+ subscriptions:** a `layout_row_nav.xml` instance with the tile `ic_acc_upgrade`, title
«Подписка», **value** = the selected subscription's name (so V5, not V6), `marginBottom=space_12`,
opening `sheet_subscription_pick.xml`.

### 6.5 The subscription card (`layout_account_card.xml`)

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/card_sub"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="@dimen/radius_card"
    app:cardElevation="0dp"
    app:strokeWidth="1dp" app:strokeColor="?attr/colorOutlineVariant">

  <LinearLayout android:orientation="vertical"
      android:paddingStart="@dimen/space_16" android:paddingTop="@dimen/space_16"
      android:paddingEnd="@dimen/space_16" android:paddingBottom="0dp">

    <!-- 1. HEADER -->
    <LinearLayout android:orientation="horizontal" android:gravity="center_vertical"
                  android:minHeight="@dimen/view_height_dp48">
      <TextView android:id="@+id/tv_sub_name" android:layout_weight="1"
                android:textAppearance="@style/TextAppearance.App.Title"
                android:maxLines="1" android:ellipsize="end"/>
      <TextView android:id="@+id/chip_tariff"
                android:background="@drawable/bg_acc_badge"
                android:paddingStart="@dimen/space_8" android:paddingEnd="@dimen/space_8"
                android:paddingTop="@dimen/space_4"  android:paddingBottom="@dimen/space_4"
                android:layout_marginStart="@dimen/space_8"
                android:textAppearance="@style/TextAppearance.App.Chip"
                android:textColor="?attr/colorOnSurface"/>
      <ImageButton android:id="@+id/btn_rename"
                android:layout_width="@dimen/view_height_dp48"
                android:layout_height="@dimen/view_height_dp48"
                android:layout_marginStart="@dimen/space_4"
                android:padding="14dp"        <!-- 48 box, 20dp glyph -->
                android:src="@drawable/ic_acc_edit" app:tint="?attr/colorOnSurfaceVariant"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/account_card_rename"/>
    </LinearLayout>

    <!-- 2. TIME BLOCK -->
    <LinearLayout android:id="@+id/block_days" android:orientation="horizontal"
                  android:baselineAligned="true" android:layout_marginTop="@dimen/space_12"
                  android:visibility="gone">
      <TextView android:id="@+id/tv_days"
                android:textAppearance="@style/TextAppearance.App.Display"
                android:fontFeatureSettings="tnum, lnum"/>
      <TextView android:id="@+id/tv_days_unit" android:layout_marginStart="@dimen/space_8"
                android:textAppearance="@style/TextAppearance.App.Title"
                android:maxLines="2"/>
    </LinearLayout>
    <TextView android:id="@+id/tv_time_title" android:layout_marginTop="@dimen/space_12"
              android:textAppearance="@style/TextAppearance.App.Title"/>
    <TextView android:id="@+id/tv_time_detail" android:layout_marginTop="@dimen/space_4"
              android:textAppearance="@style/TextAppearance.App.Subtitle"/>

    <!-- 3. TRAFFIC (gone unless root && trafficLimitBytes != null) -->
    <LinearLayout android:id="@+id/block_traffic" android:orientation="vertical"
                  android:layout_marginTop="@dimen/space_16" android:visibility="gone">
      <LinearLayout android:orientation="horizontal">
        <TextView android:layout_weight="1" android:text="@string/account_card_traffic_label"
                  android:textAppearance="@style/TextAppearance.App.Subtitle"/>
        <TextView android:id="@+id/tv_traffic_value"
                  android:textAppearance="@style/TextAppearance.App.Subtitle"
                  android:textColor="?attr/colorOnSurface"
                  android:fontFeatureSettings="tnum, lnum, zero"/>
      </LinearLayout>
      <LinearLayout android:orientation="horizontal"
                    android:layout_height="@dimen/meter_height"   <!-- 4dp -->
                    android:layout_marginTop="@dimen/space_8"
                    android:background="@drawable/bg_meter_track">
        <View android:id="@+id/meter_fill" android:layout_width="0dp"
              android:layout_height="match_parent"
              android:background="@drawable/bg_meter_fill"/>
        <View android:id="@+id/meter_rest" android:layout_width="0dp"
              android:layout_height="match_parent"/>
      </LinearLayout>
    </LinearLayout>

    <!-- 4. CTA -->
    <com.google.android.material.button.MaterialButton android:id="@+id/btn_cta"
        android:layout_width="match_parent" android:layout_height="@dimen/cta_height"
        android:layout_marginTop="@dimen/space_16"
        android:insetTop="0dp" android:insetBottom="0dp"
        app:cornerRadius="@dimen/radius_pill"
        android:textAppearance="@style/TextAppearance.App.Title"/>

    <!-- 5. UPGRADE, V3 text button, gone unless upgrade targets exist -->
    <com.google.android.material.button.MaterialButton android:id="@+id/btn_upgrade"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent" android:layout_height="@dimen/view_height_dp48"
        android:layout_marginTop="@dimen/space_12"
        android:insetTop="0dp" android:insetBottom="0dp"
        app:cornerRadius="@dimen/radius_pill"
        android:textAppearance="@style/TextAppearance.App.Title.Medium"
        android:text="@string/account_card_upgrade" android:visibility="gone"/>

    <!-- 6. AUTO-RENEW -->
    <View android:id="@+id/div_auto" android:layout_width="match_parent"
          android:layout_height="1dp" android:layout_marginTop="@dimen/space_16"
          android:background="?attr/colorOutlineVariant"/>
    <include layout="@layout/layout_row_switch" android:id="@+id/row_auto_renew"/>
        <!-- with paddingStart/End = 0dp (the card already pads 16) and
             paddingBottom = @dimen/space_16 to close the card;
             the switch row on this surface carries NO tile, so its text
             origin is the card's inner 16 -->
  </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**Card geometry facts:**

- Height is `wrap_content`. `@dimen/sub_card_height` 152dp is deleted from `dimens.xml`.
- `bg_acc_badge.xml` is retinted from `?attr/iconTileBgBlue` to `?attr/colorSurfaceContainerHighest`
  (P3 inset, D§4.1) with `radius_chip` 12. Its text is `colorOnSurface` (14.14:1).
- `bg_meter_track.xml` and `bg_meter_fill.xml` are `<shape android:shape="rectangle">` with
  `<corners android:radius="@dimen/radius_pill"/>`; the track is `?attr/colorSurfaceContainerHighest`,
  the fill is tinted at runtime (`colorOnSurfaceVariant` → `colorWarning` at ≥ 90 % → `colorErrorText`
  at 100 %).
- The meter is driven by `layout_weight`: `meter_fill.weight = used`, `meter_rest.weight = total - used`,
  clamped so a non-zero usage always draws at least 2dp.
- The rename button is hidden when `selectedSub.id` is blank - a primary-only account gets a
  synthesised root with **no id** (AS §4.2) and `PATCH /client/subscription/{scope}/{id}/name` would
  400.

**Card variants:**

| Variant | Layout | Contents |
|---|---|---|
| Empty | `layout_account_card_empty.xml` | 64dp neutral tile + 32dp `ic_acc_upgrade`, centred; Title «Подписки пока нет»; Body «Купите тариф, чтобы подключаться к серверам Departament.» (`maxWidth` ≈ 60 ch); **V1** «Купить»; and when `trialEnabled && !trialUsed`, a **V3** «Начать пробный период» 12 below |
| Error | `layout_account_card_error.xml` | 64dp neutral tile + 32dp `ic_acc_alert`; Title «Не удалось загрузить аккаунт»; Body = the **mapped** cause (section 8.6), never a hard-wired string; **V2** «Повторить» |
| Skeleton | `layout_account_skeleton.xml` | see 6.7 |

### 6.6 Row groups

Section header: `@style/SettingsSectionLabel`, corrected to
`paddingStart/End=@dimen/space_16`, `paddingTop=@dimen/space_24`, `paddingBottom=@dimen/space_8`
(its `paddingTop` is 18dp today, off-scale, CS §A.1). Sentence case, 16sp/700, `colorOnSurface`.

The three row layouts, all sharing one geometry:

```
minHeight=@dimen/row_min_height (56)
paddingStart/End=@dimen/space_16   paddingTop/Bottom=@dimen/space_8
background=?attr/selectableItemBackground
android:stateListAnimator="@anim/press_scale"     (0.97)
android:focusable="true"
  [FrameLayout 40×40 @drawable/bg_icon_neutral, r12]
     [ImageView 22dp, tint=?attr/colorOnSurfaceVariant]
  [LinearLayout vertical, marginStart=@dimen/space_12, weight=1]
     [TextView title    @style/TextAppearance.App.Title    maxLines=2 ellipsize=end]
     [TextView subtitle @style/TextAppearance.App.Subtitle marginTop=@dimen/space_4
               maxLines=2  visibility=gone]
  trailing, exactly one of:
     layout_row_value.xml : [TextView value @style/TextAppearance.App.Subtitle
                             fontFeatureSettings="tnum, lnum" marginStart=@dimen/space_12]
                            (strong: @style/TextAppearance.App.Title + colorOnSurface)
     layout_row_nav.xml   : [ImageView 20dp @drawable/ic_chevron_right
                             tint=?attr/colorOnSurfaceVariant marginStart=@dimen/space_12]
     layout_row_switch.xml: [MaterialSwitch clickable=false focusable=false
                             importantForAccessibility=no marginStart=@dimen/space_12]
```

Text origin = 16 + 40 + 12 = **68** (`@dimen/row_text_origin`). Divider between rows in a group:
`<View height=1dp background="?attr/colorOutlineVariant" layout_marginStart="@dimen/row_text_origin"/>`.
No divider above the first row of a group and none under a section header (D§4.4). The current 72dp
divider inset and the 18dp chevrons both die (CS §C.3.18, §C.2.12).

Group contents are exactly section 2's table. Two conditional rules:

- **«Купить подписку» is hidden** when the card is already showing a filled «Купить…» CTA (empty /
  trial / expired states) - one entrance, never two.
- **«Способы входа» is replaced by «Привязать Telegram»** (V6, tile `ic_acc_telegram`, subtitle
  «Управление подпиской из бота») when `telegramLinked == false`. This is owner request §0.4.9,
  satisfied as an explicit, state-driven CTA row.

The «Выйти» row is the last row of the «Вход» group, separated by the normal hairline, title in
`?attr/colorErrorText`, tile neutral with `ic_acc_logout` in `colorOnSurfaceVariant`, no value, no
chevron. It opens a confirm dialog (§7.5 permits a dialog when there is no undo path, and there is
none: you cannot un-sign-out without credentials).

### 6.7 Every state

| State | Trigger | Rendering |
|---|---|---|
| **Skeleton** | `(pendingFirstLoad \|\| loading) && profile == null`, **after 300 ms** | `layout_account_skeleton.xml`: head silhouette (40dp tile block + a 45 %-width and a 30 %-width bar), one card silhouette (name bar 45 %, hero bar 30 %, detail bar 60 %, a full-width 52dp block), and three row silhouettes (40dp tile + 55 % bar). All blocks `@drawable/bg_skeleton_bar` (`colorSurfaceContainerHighest`, `radius_chip`), bars 16dp tall. **Static - no pulse.** D§8.5 permits no looping animation that is not real indeterminate progress; the 900 ms `AccelerateDecelerateInterpolator` pulse in `AccountFragment.kt:413-430` is deleted, off-token and off-direction |
| **Loaded** | profile and subs resolved | section 2 |
| **Empty account** | `subs.isEmpty()`, no error | head + empty card + the «Оплата» and «Вход» groups (the «Подписка» group is hidden - there is no subscription to manage) |
| **Trial** | `selectedSub.isTrial` | badge «Пробный» (neutral chip); time block per 4.3; traffic block if the data exists; **CTA V1 «Купить тариф»**; `btn_upgrade` hidden; the auto-renew row hidden (a trial does not auto-renew); «Улучшить тариф» row hidden |
| **Expired** | see 4.3 | as 4.3; traffic block hidden; `btn_upgrade` hidden |
| **Offline** | no connectivity, or the last refresh failed with `ApiError.Network` | `layout_status_bar_inline.xml` pinned as the first child of `content`: 40dp tall, `radius_chip`, `colorSurfaceContainerHighest`, 20dp `ic_acc_cloud_off` + Body «Нет сети. Показаны последние данные.» + a V3 «Повторить». **Last known data stays on screen.** Every network action is disabled at 0.38: the CTA, `btn_upgrade`, the auto-renew switch row, «Баланс», «Купить подписку», «Улучшить тариф», the rename button. **«Выйти» stays enabled** (it is local). §9.6 |
| **Error, cold** | `profile == null && error != null` | head renders from the session's cached email if there is one, else the person tile + «Аккаунт»; card slot = the error card with the **mapped** cause; groups render with values omitted (never a placeholder dash) |
| **Partial** | profile OK, subs failed (or vice versa) | render what resolved; the failed half shows its own error card / omits its values; a single V2 «Повторить» in the failed half only |
| **Payment polling** | `pendingPayment != null` | the same inline bar, with a 20dp indeterminate indicator + «Проверяем оплату…» + a V3 «Обновить». Offline wins if both apply. On confirmation the bar swaps to «Оплата прошла» in `colorTertiary` for 2 s, then hides, and the card re-renders. On timeout (6 × 8 s): «Не удалось подтвердить оплату. Проверьте историю платежей.» + V3 «История» |
| **Long content** | 60-char Telegram name, 40-char sub name, 12-digit balance | name wraps to 2 lines then ellipsises; the card name is 1 line ellipsised (the chip and the rename button are fixed); the balance value is `wrap_content` and the row title ellipsises |
| **Short content** | one subscription, one device, one payment | no switcher, no dots, no "1 of 1" - the card is simply the card |
| **Gated** | signed out | `layout_account_gate.xml` replaces the whole content |
| **Font scale 200 %** | - | every row is `wrap_content` with `minHeight`; the hero figure and its unit are a baseline-aligned `LinearLayout` with the unit at `maxLines=2` so «дней» drops to a second line rather than clipping |

**The gate** (`layout_account_gate.xml`): P0, no card (D§10.1 - "the sign-in form is not an object
floating on a surface; it is the screen"). A centred column, `maxWidth=320dp`, vertically centred in the
viewport:

```
TextView   @style/TextAppearance.App.Headline  «Вход в departament»   gravity=center
TextView   @style/TextAppearance.App.Body      «Здесь будут подписка, устройства и платежи.»
           colorOnSurfaceVariant, gravity=center, marginTop=@dimen/space_8
MaterialButton V1  «Войти через Telegram»  match_parent, 52dp, marginTop=@dimen/space_24
MaterialButton V3  «Войти по почте»        match_parent, 48dp, marginTop=@dimen/space_12
```

### 6.8 Sheets and dialogs

**The payment sheet (`sheet_payment.xml`) is one component with five callers.** This is the fix for the
#1 both-platform defect (AS §5.1: two components for the same decision).

```
BottomSheetDialogFragment, background=@drawable/bg_sheet_top (radius_sheet 24 top, colorSurface)
  View       36×4  @drawable/bg_sheet_handle (colorSurfaceContainerHighest, radius_pill)
             layout_gravity=center_horizontal  marginTop=@dimen/space_12
  TextView   @style/TextAppearance.App.Title      «Оплата»
             paddingH=@dimen/space_16  marginTop=@dimen/space_12
  TextView   id=tv_sheet_subject  @style/TextAppearance.App.Subtitle
             paddingH=@dimen/space_16  marginTop=@dimen/space_4
             ← WHAT IS BEING BOUGHT, restated. This closes the "no purchase summary"
               hole that both platforms have today (AS §1.5, §2.4).
  [space 16]
  include layout_ledger_row  id=row_total     «Итого» + value V5s (16/700 numeric, onSurface)
  View 1dp  colorOutlineVariant  marginStart/End=@dimen/space_16
  [runtime method rows: include layout_ledger_row ×N]
  paddingBottom = @dimen/space_16 + navigationBars inset
```

- `layout_ledger_row.xml` is the **tile-less** row: `minHeight=56`, `paddingH=@dimen/space_16`,
  `paddingV=@dimen/space_8`, title `App.Title` weight 1, optional subtitle, trailing value
  `App.Subtitle` numeric. **No tile, no chevron.** The current sheet's chevron implies "goes further"
  while the tap charges money (AS §1.8) - it is deleted. The green balance tile is deleted (§1.4.1:
  green is a status colour).
- Method rows are **verbs**: «Оплатить с баланса» (trailing value «На балансе 1 500 ₽»), «Оплатить
  картой», «Оплатить через СБП», «Оплатить через {label}» for any other `plategaMethods` entry. SBP is
  detected by the existing `"sbp"`/`"СБП"` match on id or label.
- **Insufficient balance:** the balance row is disabled at 0.38 and its subtitle reads «Не хватает
  200 ₽».
- **Top-up never shows the balance row** (paying for balance from balance is circular - the existing
  `AccountFragment.kt:535-559` behaviour is correct and is preserved).
- **In flight:** the tapped row's trailing area shows a 20dp indeterminate indicator; every other row
  goes to 0.38 and stops responding; the sheet cannot be dismissed by swipe while a balance payment is
  in flight (a card payment hands off to the browser and the sheet closes).
- **Estimate rows.** When the amount is a client-side estimate (add-devices only, AS §4.4) the total row
  reads «Примерно 150 ₽» and carries the subtitle «Точную сумму покажем при оплате». Every other caller
  shows an exact figure and **the charged amount is the displayed figure** (AS §4.4).

Callers and their subject line:

| Caller | `tv_sheet_subject` | Amount source |
|---|---|---|
| Renew from the card | «Продление {tariff}, {N} дней» | `renewalPrice` → `tariffPrice` → `autoRenewNextChargeAmount` |
| Upgrade confirm | «Улучшение до {tariff}, +{N} дней» | `GET /client/subscriptions/upgrade-quote` (exact) |
| Add devices (Devices page) | «{N} устройства к подписке {name}» | client estimate (**«Примерно»**) |
| Top-up | «Пополнение баланса» | the amount the user typed |
| Buy (catalogue screen) | «{tariff}, {N} дней» | `currentTotal(tariff, option)` |

**`sheet_top_up.xml`** = a label + field block above the payment sheet's own structure:

```
TextView  @style/TextAppearance.App.Title      «Пополнение баланса»
TextView  @style/TextAppearance.App.Subtitle   «Сумма»            ← the LABEL, above, always visible
          marginTop=@dimen/space_16
TextInputLayout  style=…OutlinedBox  boxCornerRadius* = @dimen/radius_chip
                 app:suffixText="₽"  android:hint=null       ← never placeholder-as-label
  TextInputEditText  inputType=numberDecimal  imeOptions=actionDone
                     textAppearance=@style/TextAppearance.App.Title  fontFeatureSettings="tnum, lnum"
TextView  id=tv_amount_error  @style/TextAppearance.App.Caption  textColor=?attr/colorErrorText
          minHeight=16dp   ← the helper slot is ALWAYS in the tree so the layout never jumps (§7.4)
[method rows, disabled until the amount is valid]
```

Validation on **blur** and on submit, never per keystroke (§7.4): empty → «Введите сумму»; `≤ 0` →
«Сумма должна быть больше 0»; unparseable → «Введите сумму цифрами». On a failed submit focus returns to
the field. `dialog_top_up.xml` (hint-as-label, system «OK»/«Отмена», toast on error) is deleted.

**`sheet_subscription_pick.xml`** (4+ subs): title «Выберите подписку», then a `RecyclerView`
(virtualised, §4.6) of `layout_ledger_row` with a leading 20dp `MaterialRadioButton`, title = the
subscription name, subtitle = short expiry («до 03.08.2026» / «истекла 31.05.2026» / «бессрочно»).
Selected row: radio accent + title weight 700 (two channels).

**`sheet_upgrade.xml`:** title «Улучшить тариф», subtitle «Доплата рассчитывается за оставшийся срок»,
then one `layout_ledger_row` per target (title = tariff name, trailing value = the catalogue price).
Tapping a target fetches `upgrade-quote` (the row shows the 16dp indicator while it does) and pushes the
**payment sheet** with the exact quote. Two steps, one payment component. No four-panel wizard.

**`sheet_add_devices.xml`** (on the Devices page): title «Добавить устройства», a stepper row
(V4 «−» / count at `App.Display` 34sp figure face / V4 «+», bounded by `maxExtraDevices`), the
estimate line «Примерно 150 ₽» + «Точную сумму посчитаем при оплате», then a V1 «Перейти к оплате» that
pushes the payment sheet. Stepper disabled state uses **0.38**, not the current imperative `0.4f`
(`BuyTariffActivity.kt:616-619`).

**`sheet_qr.xml`:** title «QR-код подписки», subtitle «Отсканируйте в приложении на другом устройстве»,
a centred bitmap from `repo.getQr(remnawaveUuid)` at 240dp on a white `radius_card` plate (a QR needs a
light quiet zone; this is the one white surface in the product and it is a *functional* requirement, not
decoration), then a V2 «Скопировать ссылку».

**`dialog_rename_subscription.xml`:** a themed `MaterialAlertDialog` (inherits
`ThemeOverlay.Departament.Dialog`), title «Название подписки», body = a label «Название» above an
`OutlinedBox` field with no hint, helper slot below, buttons «Отмена» / «Сохранить». 1-40 characters
after trimming; empty → «Введите название». Rename is **optimistic** (not money, §8.4): the card title
updates immediately, reverts on failure with a Snackbar «Не удалось переименовать. Повторите.» +
«Повторить».

**Sign-out dialog:** title «Выйти из аккаунта?», body «Подписка останется активной. Чтобы вернуться,
войдите снова.», negative «Отмена», positive «Выйти» as a **text button in `?attr/colorErrorText` on the
right** (§7.5).

### 6.9 Sub-page: Devices

`DeviceManagementActivity` + `activity_devices.xml`, rewritten.

**Toolbar.** `BaseActivity.setContentViewWithToolbar` currently applies `@style/ToolbarBrandTitle`
(20sp wordmark) to **every** sub-page (CS §A.7). For this tab's sub-pages the toolbar switches to
`app:titleTextAppearance="@style/TextAppearance.App.Title"` (16sp/700), height 56dp, background
`?attr/colorBackground`, elevation 0, no divider (§4.8, owner request §0.4.6). One trailing action: a
V4 refresh icon button. `SwipeRefreshLayout` also wraps the list.

**Content order:**

```
16 top
«Подключить устройство»                       section header
  V6  Показать QR-код          ›              (hidden when subscriptionUrl/uuid missing)
  V6  Скопировать ссылку       ›
24
«Устройства»                                  section header
  V5  Добавить устройства      +150 ₽         (hidden when maxExtraDevices <= 0)
  ── hairline @68 ──
  device rows …
```

Wait - the count line. It goes **above** the device rows as the section header's own value? No: the
section header is text only. The device count is stated once, as the **subtitle of the first device
row's group**: a single Body line under the «Устройства» header reading «Подключено 2 из 5» /
«Подключено 2, без ограничений», `colorOnSurfaceVariant`, `marginBottom=@dimen/space_8`. The current
subtitle «Устройства, подключённые к вашей подписке» is deleted (it restates the screen title, §F16).

**Device row** (V5-shaped, tiled, with an icon-button trailing - the one documented exception to "one
trailing", because the row's own action and its content are different things):

```
minHeight=56  paddingStart=@dimen/space_16  paddingEnd=@dimen/space_8  paddingV=@dimen/space_8
  [40dp neutral tile + 22dp platform glyph]
      Android → ic_acc_device_android | Apple → ic_acc_device_apple
      Windows → ic_acc_device_windows | Router → ic_acc_device_router | else ic_acc_devices
      (ported from the desktop's IsAndroid/IsApple/IsWindows/IsRouter resolution)
  [text column, weight 1]
      title    deviceModel → platform → «Неизвестное устройство»   App.Title  maxLines=1
      + chip   «Это устройство» when hwid == this device's hwid
               (P3 fill, App.Chip, marginStart=@dimen/space_8) - NOT an accent row wash
      subtitle «Android · 09.07.2026»                              App.Subtitle numeric-date
  [V4 icon button 48×48, ic_acc_link_off, tint=?attr/colorOnSurfaceVariant]
      contentDescription «Отвязать устройство»
```

- **The HWID line is deleted.** It is a diagnostic on a customer screen. When two rows resolve to the
  same title, and only then, the subtitle gains the last four characters of the hwid:
  «Android · 09.07.2026 · a1b2».
- **The unlink glyph is neutral at rest,** not red. §6.4: inactive states are never saturated, and five
  idle rows with five red glyphs is a wall of alarm. Red appears in the undo Snackbar's context, not on
  the resting list.
- **Unlink is undo, not confirm** (§7.5; the device simply re-registers on the next connect, so it is
  reversible). Tapping removes the row immediately with a 220 ms fade, shows a Snackbar «Устройство
  отвязано» + «Отменить» for 5 s, and **the network call fires when the Snackbar dismisses**. «Отменить»
  cancels the call and restores the row. Leaving the screen commits immediately. The desktop's in-view
  modal confirm and Android's `MaterialAlertDialog` both die.

**States:** skeleton (3 rows of the real geometry, static) · list · empty («Устройств пока нет» /
«Устройства появятся после первого подключения.» / no action, §9.5) · no subscription («Подписка не
активна» / «Купите тариф, чтобы подключать устройства.» / **V1** «Купить») · error («Не удалось
загрузить устройства. Проверьте сеть и повторите.» / **V2** «Повторить») · offline (the inline bar; the
list stays, unlink and add are disabled).

**The «Ответ сервера (диагностика)» dialog is deleted**, along with `devices_diag_*` in
`strings_devices.xml`. When the parsed list is empty but `totalDevices > 0`, the screen shows the
**error** state and the raw body goes to `Log.w`.

### 6.10 Sub-page: Payment history

`PaymentHistoryActivity`, rewritten. Toolbar as above, title «История платежей», one trailing V4
refresh, plus `SwipeRefreshLayout`.

A **tile-less divided ledger**, not a card grid (§2.4.3 - a payment is a fact, not an object you act on):

```
«Июнь 2026»                                        section header, sentence case
  Продление Plus, 30 дней                 450 ₽    ← desc Body 14/400 | amount App.Title numeric
  12.06.2026, 14:32                    Оплачено    ← Caption numeric  | Caption, status colour
  ── hairline, marginStart=@dimen/space_16 ──
  …
«Май 2026»
  …
```

- Row: `minHeight=56`, `paddingH=@dimen/space_16`, `paddingV=@dimen/space_12`, **not clickable** (there
  is no receipt endpoint; a row that looks pressable and does nothing is worse than a row that does
  not).
- Left column weight 1: description `maxLines=2 ellipsize=end`; the date+time line `App.Caption` in the
  **figure face** with `tnum`.
- Right column, right-aligned, `wrap_content`: amount `App.Title` + `tnum` + Money.format; status word
  `App.Caption`.
- **Two status hues, not four** (AS §1.7):

| Raw status | Label | Colour |
|---|---|---|
| paid, success, succeeded, completed, confirmed, done | «Оплачено» | `?attr/colorTertiary` |
| pending, processing, new, created, waiting, in_progress | «В обработке» | `?attr/colorOnSurfaceVariant` |
| failed, error, declined, rejected | «Ошибка» | `?attr/colorErrorText` |
| canceled, cancelled, expired | «Отменён» | `?attr/colorOnSurfaceVariant` |
| anything else | «Не определён» | `?attr/colorOnSurfaceVariant` |

  The raw status is **never** printed (§9.4 bans visible codes). «Отменён» loses its yellow: yellow means
  «истекает» and nothing else (§1.4.1).
- Description fallback chain: `description` → a mapped `kind` («Продление» / «Покупка подписки» /
  «Пополнение баланса» / «Дополнительные устройства» / «Улучшение тарифа») → «Платёж». `orderId` is
  never shown.
- **Loading is a skeleton, not the centred `ProgressBar`** (§15 forbids it by name): six row silhouettes,
  static, matching the two-column geometry.
- Empty: «Платежей пока нет» / «Здесь появится история покупок и продлений.» / **no action** (§9.5 is
  explicit; the desktop's «Купить подписку» CTA here is deleted).
- Error: «Не удалось загрузить историю. Проверьте сеть и повторите.» + V2 «Повторить».
- Cache-first via `AccountCache` is preserved, including the `showingCache` guard.

### 6.11 Sub-page: Sign-in methods

New on Android (`LinkingActivity` + `activity_linking.xml`). Toolbar «Способы входа».

```
16 top
  V5  Telegram    @sasha_erlish        ← linked: the handle as the value
      or V6  Telegram    ›  subtitle «Не привязан»   ← not linked
  ── hairline @68 ──
  V5  Почта       sasha@mail.ru
      or V6  Почта       ›  subtitle «Не привязана»
  ── hairline @68 ──
  V5  Google      sasha@gmail.com      ← shown ONLY when googleLinked == true
```

The desktop's permanently-disabled «Скоро» button is deleted (§17: unfinished work rendered as UI is not
a state). A method that cannot be linked in this build does not appear.

**Telegram linking** (`RequestLinkTelegram()`): tapping the unlinked row opens a V9 sheet - title
«Привязка Telegram», body «Откройте бота и подтвердите привязку.», the code in a P3 chip at
`App.Title` + `tnum`, a V1 «Открыть бота» (deep link `t.me/{telegramBotUsername}?start=link_{code}`),
and a quiet «Ждём подтверждения…» line with a 20dp indicator while the client polls. On success the
sheet closes and a Snackbar reads «Telegram привязан».

**Email linking** (`RequestLinkEmail(email)`): a sheet with a label «Почта», a field
(`inputType=textEmailAddress`, `autofillHints=emailAddress`), helper slot, V1 «Отправить». Success →
«Письмо отправлено. Проверьте почту.»

**Precondition, and it is blocking:** the Android `DepartamentApiClient` has **neither** endpoint
(AS §4.3). Until they are ported from the desktop `IDepartamentApiClient`, the two unlinked rows show
the subtitle «Привязка через сайт» and open `publicConfig.siteUrl` in a Custom Tab. That interim is
specified so the screen is never broken, and it is logged as parity gap **PG-1** in section 11.

### 6.12 Motion, haptics, accessibility

**Motion.** Every duration and curve on this tab comes from `res/values/motion.xml` and
`res/interpolator/`:

| Event | Token | Curve |
|---|---|---|
| Press in / out (all variants) | `motion_press_in` 90 / `motion_press_out` 160 | `ease_out_quart` / **`ease_out_quint`** |
| Skeleton → content, state → state, segment selection, tint changes | `motion_state` 220 | `ease_standard` |
| Sheet in | `motion_reveal` 300 | `ease_out_quint` |
| Sheet out | 225 (75 % of 300) | `ease_standard` |
| Balance count-up, **on a changed value only** | `motion_reveal` 300 | `ease_out_quint` |
| Device row removal | `motion_state` 220 fade | `ease_standard` |

**No stagger, no entrance choreography** on this tab (D§8.5: "no staggered section entrances"). The
desktop's `EntranceGroup2` has no Android counterpart and gains none.
Reduced motion: every animator checks `MotionUtils.animationsEnabled(context)` and jumps to the end
state; the balance lands instantly; `press_scale` collapses to 0 ms automatically.

**Haptics** (§8.10): `pressHaptic()` on exactly two things - confirming a payment (the tapped method
row) and confirming sign-out. Nothing else on this tab vibrates. `tickHaptic()` on the add-devices
stepper.

**No `Toast` for anything actionable** (§1.4.8). Every one of today's toasts becomes a Snackbar anchored
above the bottom navigation: «Код скопирован», «Ссылка скопирована», «Устройство отвязано» + «Отменить»,
«Аватар обновлён», «Не удалось загрузить фото» + «Повторить», «Не удалось изменить автопродление» +
«Повторить».

**Accessibility.**

- The hero pair is one node: the `block_days` container carries
  `contentDescription="Осталось 5 дней"` and both children are `importantForAccessibility="no"`.
- The switch row carries `contentDescription="Автопродление, включено"` / `"…, выключено"`, is
  `focusable`, and its `MaterialSwitch` is `clickable=false focusable=false
  importantForAccessibility=no`. This also removes the double-hit-target defect (CS §C.6.32).
- Every icon-only control has a `contentDescription`. Android currently passes this check outright
  (CS §A.3) and must keep passing it.
- Every state change that is drawn is also announced: `announceForAccessibility` on error appearance, on
  «Устройство отвязано», on «Оплата прошла».
- The tab sets `ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.account_tab_title))`.

---

## 7. DESKTOP

Same design, native mechanics (§12). Where a value is identical to Android's it is not repeated.

### 7.1 Files

| Path | Fate |
|---|---|
| `Views/AccountView.axaml` (1474) + `.axaml.cs` (524) | rewritten; all carousel drag/snap/tween code deleted |
| `ViewModels/AccountViewModel.cs` (2860) | carousel state, kebab-wizard state and the four-panel flyout machine deleted; `SelectedSubIndex`, `Health`, `SwitcherMode` added |
| `Views/DevicesView.axaml(.cs)` | rewritten |
| `Views/PaymentHistoryView.axaml(.cs)` | rewritten |
| `Views/BuyView.axaml` | keeps its catalogue; its bottom-sheet payment overlay is deleted and replaced by the shared flyout |
| `Views/LinkingView.axaml(.cs)` | **new** - the «Способы входа» sub-page, extracted from the tab |
| `Views/Controls/RowItem.axaml(.cs)` | **new** - one templated control implementing V5/V5s/V6/V7 |
| `Views/Controls/LedgerRow.axaml(.cs)` | **new** - the tile-less transaction row |
| `Common/Money.cs`, `Common/Plural.cs` | **new** |
| `Common/L.Account.cs`, `L.Buy.cs` | strings replaced 1:1 with section 8 |
| `Assets/GlobalResources.axaml` | the 16 local `Geo.Acc.*` geometries move here; the duplicate of `Geo.ChevronRight` is deleted; new tokens per section 12 |

### 7.2 Layout

```xml
<Panel Background="{DynamicResource Brush.Bg}">
  <!-- signed in -->
  <ScrollViewer x:Name="Scroll" IsVisible="{Binding IsSignedIn}"
                HorizontalScrollBarVisibility="Disabled">
    <StackPanel x:Name="Column" MaxWidth="720" HorizontalAlignment="Center"
                Margin="{Binding Gutter}">          <!-- 16,16,16,24 ; 24 sides at >=1000px -->

      <ContentControl x:Name="StatusBar"/>          <!-- offline / polling, 40px, Radius.Chip -->

      <!-- HEAD -->
      <Grid ColumnDefinitions="40,12,*" MinHeight="56">
        <Border Grid.Column="0" Classes="Tile" Width="40" Height="40"
                CornerRadius="{DynamicResource Radius.Tile}"
                Background="{DynamicResource Brush.Tile.Neutral}"
                Cursor="Hand" ToolTip.Tip="Сменить фото"/>   <!-- monogram / photo / person glyph -->
        <StackPanel Grid.Column="2" VerticalAlignment="Center">
          <TextBlock Classes="Title"    Text="{Binding UserName}" TextTrimming="CharacterEllipsis"
                     MaxLines="2"/>
          <TextBlock Classes="Subtitle" Text="{Binding LoginHandle}" Margin="0,4,0,0"
                     TextTrimming="CharacterEllipsis"/>
        </StackPanel>
      </Grid>

      <ContentControl x:Name="Switcher" Margin="0,32,0,12"/>   <!-- segments | select row | nothing -->
      <ContentControl x:Name="SubCard"/>                        <!-- the ONE Border.Card -->

      <TextBlock Classes="SectionHeader" Text="Подписка" Margin="0,24,0,8"/>
      <ctrl:RowItem Title="Устройства"      Value="{Binding DevicesValue}"  Command="{Binding DevicesCmd}"/>
      <Border Classes="Hairline" Margin="68,0,0,0"/>
      <ctrl:RowItem Title="Улучшить тариф"  Chevron="True" IsVisible="{Binding HasUpgradeTargets}"
                    Command="{Binding UpgradeCmd}"/>

      <TextBlock Classes="SectionHeader" Text="Оплата" Margin="0,24,0,8"/>
      <ctrl:RowItem Title="Баланс" Value="{Binding BalanceText}" ValueStrong="True"
                    Command="{Binding TopUpCmd}"/>
      …
      <TextBlock Classes="SectionHeader" Text="Вход" Margin="0,24,0,8"/>
      …
    </StackPanel>
  </ScrollViewer>

  <!-- signed out -->
  <StackPanel IsVisible="{Binding ShowLoginCta}" MaxWidth="320"
              HorizontalAlignment="Center" VerticalAlignment="Center"/>
</Panel>
```

- `MaxWidth="720"` and centred (§4.1), up from the current 560. At the 900×600 minimum the column is
  824-32 = 792 wide, so nothing clips and there is no horizontal scroll.
- The gutter switches 16 → 24 at a view width of 1000 px, set from a `Bounds` observer in code-behind.
- One scroll region. No nested scrollers (§12.3).

### 7.3 `RowItem`, the templated control

One control, four shapes, so that the desktop stops hand-rolling `Border.Row`, `Border.SettingRow`,
`Border.ServerRow`, `Button.MeterRow` and `Border.PriceOption` variants of the same thing (CS §C.3).

Styled properties: `Glyph` (StreamGeometry), `Title`, `Subtitle`, `Value`, `ValueStrong` (bool),
`Chevron` (bool), `Switch` (bool), `IsOn` (bool), `Command`, `IsBusy` (bool), `Destructive` (bool).

Template: `Border` MinHeight `{StaticResource Size.Row}` 56, Padding `16,8`,
`CornerRadius={DynamicResource Radius.Tile}`, `Background="Transparent"`, `Cursor="Hand"`,
`Focusable="True"`, containing `Grid ColumnDefinitions="40,12,*,12,Auto"`.

States (this is the part the desktop is missing on 16 classes today, CS §C.7):

```
:pointerover      Background = {DynamicResource Brush.Hover}, 150ms Ease.Standard
:pressed          RenderTransform = scale(0.97), Dur.PressIn 90 / Dur.PressOut 160
:focus-visible    outer 2px {DynamicResource Brush.Accent} adorner, 2px offset, CornerRadius 14
:disabled         Opacity 0.38, Cursor = Arrow, IsHitTestVisible = False
[IsBusy=True]     the Value slot is replaced by a 16px Ellipse.Spinner
[Destructive]     Title Foreground = {DynamicResource Brush.RedText}
KeyDown Enter/Space -> Command.Execute
```

`LedgerRow` is the same control minus the tile column, with `Padding="16,12"` and
`ColumnDefinitions="*,12,Auto"`.

### 7.4 The card, and the switcher

`Border.Card` (Surface, `Radius.Card` 20, 1px `Brush.OutlineVariant`, Padding `16,16,16,0`, **no
`BoxShadow`**). Contents mirror 6.5 exactly:

- Header `Grid ColumnDefinitions="*,8,Auto,4,40"`: name `Classes="Title"` trimmed; tariff
  `Border.ChipBadge` retinted to `Brush.SurfaceHighest` / `Brush.OnSurface` with Padding `8,4` (its
  current `10,4` is off-scale, CS §D13); rename `Button.IconButton40` with `Geo.Acc.Edit`,
  `AutomationProperties.Name="Переименовать подписку"`.
- Time block: `StackPanel Orientation="Horizontal"` with the figure `Classes="Display Numeric"` and the
  unit `Classes="Title"` `VerticalAlignment="Bottom"` `Margin="8,0,0,4"`; then two `TextBlock`s
  (`Title`, `Subtitle`).
- Traffic: label/value `Grid`, then `Border` Height 4, `CornerRadius={DynamicResource Radius.Pill}`,
  `Background={DynamicResource Brush.SurfaceHighest}` containing a `Border` whose width is
  `Grid` column-weighted. **`TrafficFillBrush`, the `LinearGradientBrush`, is deleted** (§6.5); the fill
  is a solid `Brush.OnSurfaceVariant` → `Brush.WarnText` ≥ 90 % → `Brush.RedText` at 100 %.
- CTA: `Button.Primary.Tall` (52) or `Button.Tonal` re-heighted to 52 by a `Height="52"` on the
  instance - **no**, add `Button.Tonal.Tall` to `GlobalStyles.axaml` and delete the two identical local
  declarations in `LoginView.axaml` and `OnboardingView.axaml` (CS §C.1.6).
- Upgrade: `Button.LinkAction`, `HorizontalAlignment="Stretch"`, Height 48. `Button.LinkAction` gains the
  `:pointerover` and `:disabled` states it lacks today (CS §C.7.33, §C.7.35).
- Auto-renew: a `RowItem` with `Switch="True"`, Padding `0,12,0,16`, preceded by a `Border.Hairline`
  spanning the card's inner width.

Switcher: `ToggleButton.Segment` ×2-3 inside a `UniformGrid Rows="1"`, Height
`{StaticResource Size.SegmentChip}` 44, `Radius.Chip`; or a `RowItem` with a `Value` opening a `Flyout`
list for 4+.

### 7.5 Desktop-only obligations

- **`DynamicResource` only.** Zero `StaticResource` on a theme brush (the repo is clean here; keep it).
- **Zero inline hex.** `AccountView.axaml:65` `#3D7EF0` and `:68` `#3877E0` become
  `Brush.AccentHover` / `Brush.AccentPressed` **inside** `ThemeDictionaries` (section 12).
  `DevicesView.axaml:451` `#80000000` becomes `Brush.Scrim`.
- **Zero off-scale spacing.** `Spacing="6"` ×2, `Spacing="10"`, `Spacing="20"`, `Margin="6,0,0,4"`,
  `Margin="0,3,0,0"`, `Margin="16,10"` are all replaced by scale values.
- **Zero Semi-default leakage.** The two Semi `TextBox`es on the tab (`:372` top-up amount, `:1223`
  link-email) become `TextBox.Incy`; the one classless `Button` gets a variant. §12.1 calls a
  non-restyled control a defect by name and these two are the money-entry and identity-linking fields.
- **Keyboard completeness.** Tab order equals visual order. Esc closes any flyout and returns focus to
  its trigger. Enter/Space activates a focused `RowItem`. `Ctrl+F` is not bound on this tab.
- **Every icon-only button** gets `AutomationProperties.Name` **and** `ToolTip.Tip`; the tab has 2
  missing today (CS §B.6).
- **Reduced motion** reads `MotionState.IsLite` **at play time**, never once in the constructor
  (§12.5).
- **DPI** 100/125/150/200 %: the column is `MaxWidth`-capped, every row is `MinHeight` not `Height`, and
  every `TextBlock` wraps or trims.

### 7.6 Desktop sub-pages

`DevicesView`, `PaymentHistoryView`, `LinkingView` and `BuyView` all adopt:

- the seamless `Border.SubToolbar` at `Size.SubToolbar` 56, page background, no divider, back button
  `Button.BackNav`, and the title at **`Classes="Title"` 16/700** - not `Headline` 24 (§4.8; three views
  get this wrong today, AS §2.4);
- one trailing V4 action maximum;
- the same states, in the same order, with the same strings as Android;
- `PaymentHistoryView`'s three hand-pasted 60-line skeleton blocks collapse into one
  `ItemsControl` over a 6-item dummy collection (CS/AS: the copy-paste is a guaranteed drift);
- `PaymentHistoryView`'s locally hard-coded 64×64 `CornerRadius="20"` empty tile is replaced by
  `Border.EmptyIcon` / `Size.EmptyIcon`;
- `DevicesView`'s current-device `Brush.Tile.Blue` row wash is deleted; the marker is the neutral
  «Это устройство» chip only;
- `DevicesView`'s modal unlink confirm is replaced by the undo toast (`Border.Toast`, 5 s, «Отменить»).

---

## 8. Copy - the complete Russian string table

Sentence case everywhere. No em-dash, no en-dash, hyphen only (§9.2). `…` is the single character.
«Ёлочки» for quotes. No final period on labels, titles, chips or buttons. Every string below is
identical on both platforms (§13).

### 8.1 Tab, head, gate

| Android name | Desktop key | Text |
|---|---|---|
| `account_tab_title` | `Account_Title` | Аккаунт |
| `account_change_avatar` | `Account_ChangeAvatar` | Сменить фото |
| `account_avatar_gallery` | `Account_AvatarGallery` | Выбрать из галереи |
| `account_avatar_remove` | `Account_AvatarRemove` | Убрать фото |
| `account_avatar_updated` | `Account_AvatarUpdated` | Аватар обновлён |
| `account_avatar_error` | `Account_AvatarError` | Не удалось загрузить фото |
| `account_name_fallback` | `Account_NameFallback` | Аккаунт |
| `account_no_telegram` | `Account_NoTelegram` | Telegram не привязан |
| `account_gate_title` | `Account_GateTitle` | Вход в departament |
| `account_gate_body` | `Account_GateBody` | Здесь будут подписка, устройства и платежи. |
| `account_gate_telegram` | `Account_GateTelegram` | Войти через Telegram |
| `account_gate_email` | `Account_GateEmail` | Войти по почте |

### 8.2 Switcher and card

| Android name | Desktop key | Text |
|---|---|---|
| `account_switcher_title` | `Account_SwitcherTitle` | Подписка |
| `account_switcher_sheet_title` | `Account_SwitcherSheetTitle` | Выберите подписку |
| `account_sub_default_name` | `Account_SubDefaultName` | Подписка %1$d |
| `account_card_perpetual_title` | `Account_PerpetualTitle` | Бессрочная подписка |
| `account_card_perpetual_detail` | `Account_PerpetualDetail` | Срок не ограничен |
| `account_card_active_title` | `Account_ActiveTitle` | Активна до %1$s |
| `account_card_active_detail` | `Account_ActiveDetail` | Осталось %1$s |
| `account_card_today_title` | `Account_TodayTitle` | Истекает сегодня |
| `account_card_expired_title` | `Account_ExpiredTitle` | Подписка истекла |
| `account_card_expired_detail` | `Account_ExpiredDetail` | Срок закончился %1$s |
| `account_card_unknown_title` | `Account_UnknownTitle` | Срок неизвестен |
| `account_card_unknown_detail` | `Account_UnknownDetail` | Обновите страницу или проверьте позже |
| `account_card_trial_badge` | `Account_TrialBadge` | Пробный |
| `account_card_traffic_label` | `Account_TrafficLabel` | Трафик |
| `account_card_traffic_value` | `Account_TrafficValue` | %1$s из %2$s |
| `account_card_traffic_over` | `Account_TrafficOver` | Трафик исчерпан |
| `account_card_renew` | `Account_Renew` | Продлить |
| `account_card_renew_price` | `Account_RenewPrice` | Продлить · %1$s |
| `account_card_buy` | `Account_Buy` | Купить |
| `account_card_buy_tariff` | `Account_BuyTariff` | Купить тариф |
| `account_card_pick_tariff` | `Account_PickTariff` | Выбрать тариф |
| `account_card_upgrade` | `Account_Upgrade` | Улучшить тариф |
| `account_card_rename` | `Account_Rename` | Переименовать подписку |
| `account_days` (plurals) | `Plural.Days` | день / дня / дней |

`account_trial_badge` («ПРОБНЫЙ», ALL-CAPS, §1.4.7) is deleted, along with the unreferenced
`account_profile_title`, `account_sub_summary_title`, `account_subs_empty`, `account_hub_*_sub`,
`account_traffic`, `account_payments_more`, `account_active_sub*`, `account_tariffs_*`,
`account_option_duration`, `account_tariff_*`, `account_promo_*`, `account_trial*`, `buy_loading`,
`buy_pick_duration`, `buy_balance_label` (AS §1.4.14).

### 8.3 Auto-renew

| Android name | Desktop key | Text |
|---|---|---|
| `account_auto_renew` | `Account_AutoRenew` | Автопродление |
| `account_auto_renew_next` | `Account_AutoRenewNext` | %1$s спишем %2$s |
| `account_auto_renew_on` | `Account_AutoRenewOn` | Продлим автоматически |
| `account_auto_renew_off` | `Account_AutoRenewOff` | Продление вручную |
| `account_auto_renew_risk` | `Account_AutoRenewRisk` | Без автопродления доступ прервётся %1$s |
| `account_auto_renew_saving` | `Account_AutoRenewSaving` | Сохраняем… |
| `account_auto_renew_failed` | `Account_AutoRenewFailed` | Не удалось изменить автопродление. Повторите. |

### 8.4 Groups and rows

| Android name | Desktop key | Text |
|---|---|---|
| `account_group_subscription` | `Account_GroupSubscription` | Подписка |
| `account_group_billing` | `Account_GroupBilling` | Оплата |
| `account_group_signin` | `Account_GroupSignIn` | Вход |
| `account_row_devices` | `Account_RowDevices` | Устройства |
| `account_devices_pair` | `Account_DevicesPair` | %1$d / %2$d |
| `account_devices_unlimited` | `Account_DevicesUnlimited` | Без ограничений |
| `account_devices_slots` | `Account_DevicesSlots` | Слотов на подписке |
| `account_row_balance` | `Account_RowBalance` | Баланс |
| `account_row_buy` | `Account_RowBuy` | Купить подписку |
| `account_row_history` | `Account_RowHistory` | История платежей |
| `account_row_referral` | `Account_RowReferral` | Реферальный код |
| `account_referral_copied` | `Account_ReferralCopied` | Код скопирован |
| `account_row_login_methods` | `Account_RowLoginMethods` | Способы входа |
| `account_row_link_telegram` | `Account_RowLinkTelegram` | Привязать Telegram |
| `account_row_link_telegram_sub` | `Account_RowLinkTelegramSub` | Управление подпиской из бота |
| `account_row_logout` | `Account_RowLogout` | Выйти |
| `account_logout_title` | `Account_LogoutTitle` | Выйти из аккаунта? |
| `account_logout_body` | `Account_LogoutBody` | Подписка останется активной. Чтобы вернуться, войдите снова. |
| `common_cancel` | `Common_Cancel` | Отмена |
| `common_save` | `Common_Save` | Сохранить |
| `common_retry` | `Common_Retry` | Повторить |
| `common_undo` | `Common_Undo` | Отменить |
| `common_close` | `Common_Close` | Закрыть |

### 8.5 Empty, offline, payment polling

| Android name | Desktop key | Text |
|---|---|---|
| `account_empty_title` | `Account_EmptyTitle` | Подписки пока нет |
| `account_empty_body` | `Account_EmptyBody` | Купите тариф, чтобы подключаться к серверам Departament. |
| `account_empty_trial` | `Account_EmptyTrial` | Начать пробный период |
| `account_trial_activated` | `Account_TrialActivated` | Пробный период активирован |
| `account_offline` | `Account_Offline` | Нет сети. Показаны последние данные. |
| `account_pay_checking` | `Account_PayChecking` | Проверяем оплату… |
| `account_pay_refresh` | `Account_PayRefresh` | Обновить |
| `account_pay_done` | `Account_PayDone` | Оплата прошла |
| `account_pay_unconfirmed` | `Account_PayUnconfirmed` | Не удалось подтвердить оплату. Проверьте историю платежей. |
| `account_pay_open_history` | `Account_PayOpenHistory` | История |
| `account_checkout_browser` | `Account_CheckoutBrowser` | Завершите оплату в браузере |
| `account_checkout_no_browser` | `Account_CheckoutNoBrowser` | Не удалось открыть страницу оплаты. Проверьте браузер по умолчанию. |

### 8.6 Errors (§9.4 - what happened, why, what to do; no codes)

| Cause | Android name | Text |
|---|---|---|
| cold-load title | `account_error_title` | Не удалось загрузить аккаунт |
| `ApiError.Network` | `account_error_network` | Нет подключения к интернету. Проверьте сеть и повторите. |
| `ApiError.ServiceUnavailable` | `account_error_service` | Сервис временно недоступен. Повторите через пару минут. |
| `ApiError.RateLimited` | `account_error_rate` | Слишком много запросов. Повторите через минуту. |
| `ApiError.Timeout` | `account_error_timeout` | Сервер не ответил вовремя. Повторите попытку. |
| generic | `account_error_generic` | Что-то пошло не так. Повторите попытку. |
| payment declined | `pay_failed` | Платёж не прошёл. Попробуйте другой способ оплаты. |
| device limit | `devices_limit` | Достигнут лимит устройств. Отвяжите одно из устройств. |

`ApiError.Unauthorized` is **not an error state**: it clears the session and renders the gate.
`account_payment_error_body` («HTTP %1$s\n%2$s») and `account_payment_error_body_nodetail` are deleted.

### 8.7 Payment / top-up sheet

| Android name | Desktop key | Text |
|---|---|---|
| `pay_sheet_title` | `Pay_SheetTitle` | Оплата |
| `pay_total` | `Pay_Total` | Итого |
| `pay_estimate` | `Pay_Estimate` | Примерно %1$s |
| `pay_estimate_note` | `Pay_EstimateNote` | Точную сумму покажем при оплате |
| `pay_from_balance` | `Pay_FromBalance` | Оплатить с баланса |
| `pay_balance_have` | `Pay_BalanceHave` | На балансе %1$s |
| `pay_balance_short` | `Pay_BalanceShort` | Не хватает %1$s |
| `pay_by_card` | `Pay_ByCard` | Оплатить картой |
| `pay_by_sbp` | `Pay_BySbp` | Оплатить через СБП |
| `pay_by_other` | `Pay_ByOther` | Оплатить через %1$s |
| `pay_subject_renew` | `Pay_SubjectRenew` | Продление %1$s, %2$s |
| `pay_subject_upgrade` | `Pay_SubjectUpgrade` | Улучшение до %1$s, +%2$s |
| `pay_subject_devices` | `Pay_SubjectDevices` | %1$s к подписке «%2$s» |
| `pay_subject_topup` | `Pay_SubjectTopUp` | Пополнение баланса |
| `topup_title` | `TopUp_Title` | Пополнение баланса |
| `topup_amount_label` | `TopUp_AmountLabel` | Сумма |
| `topup_error_empty` | `TopUp_ErrorEmpty` | Введите сумму |
| `topup_error_zero` | `TopUp_ErrorZero` | Сумма должна быть больше 0 |
| `topup_error_format` | `TopUp_ErrorFormat` | Введите сумму цифрами |
| `upgrade_sheet_title` | `Upgrade_SheetTitle` | Улучшить тариф |
| `upgrade_sheet_note` | `Upgrade_SheetNote` | Доплата рассчитывается за оставшийся срок |
| `rename_title` | `Rename_Title` | Название подписки |
| `rename_label` | `Rename_Label` | Название |
| `rename_error_empty` | `Rename_ErrorEmpty` | Введите название |
| `rename_failed` | `Rename_Failed` | Не удалось переименовать. Повторите. |

### 8.8 Devices

| Android name | Desktop key | Text |
|---|---|---|
| `devices_title` | `Devices_Title` | Устройства |
| `devices_connect_header` | `Devices_ConnectHeader` | Подключить устройство |
| `devices_row_qr` | `Devices_RowQr` | Показать QR-код |
| `devices_row_link` | `Devices_RowLink` | Скопировать ссылку |
| `devices_link_copied` | `Devices_LinkCopied` | Ссылка скопирована |
| `devices_qr_title` | `Devices_QrTitle` | QR-код подписки |
| `devices_qr_body` | `Devices_QrBody` | Отсканируйте в приложении на другом устройстве |
| `devices_add_row` | `Devices_AddRow` | Добавить устройства |
| `devices_add_title` | `Devices_AddTitle` | Добавить устройства |
| `devices_add_per_device` | `Devices_AddPerDevice` | %1$s за устройство |
| `devices_add_note` | `Devices_AddNote` | Точную сумму посчитаем при оплате |
| `devices_add_pay` | `Devices_AddPay` | Перейти к оплате |
| `devices_add_minus` | `Devices_AddMinus` | Убрать устройство |
| `devices_add_plus` | `Devices_AddPlus` | Добавить устройство |
| `devices_count_line` | `Devices_CountLine` | Подключено %1$d из %2$d |
| `devices_count_line_unlimited` | `Devices_CountLineUnlimited` | Подключено %1$d, без ограничений |
| `devices_this_device` | `Devices_ThisDevice` | Это устройство |
| `devices_unknown` | `Devices_Unknown` | Неизвестное устройство |
| `devices_unlink` | `Devices_Unlink` | Отвязать устройство |
| `devices_unlinked` | `Devices_Unlinked` | Устройство отвязано |
| `devices_empty_title` | `Devices_EmptyTitle` | Устройств пока нет |
| `devices_empty_body` | `Devices_EmptyBody` | Устройства появятся после первого подключения. |
| `devices_nosub_title` | `Devices_NoSubTitle` | Подписка не активна |
| `devices_nosub_body` | `Devices_NoSubBody` | Купите тариф, чтобы подключать устройства. |
| `devices_error` | `Devices_Error` | Не удалось загрузить устройства. Проверьте сеть и повторите. |

**Terminology lock addition:** removing a device is **отвязать**, never «удалить», on both platforms
(§9.3; the two platforms disagree today, AS §3.2). `devices_diag_*` are deleted.

### 8.9 Payment history

| Android name | Desktop key | Text |
|---|---|---|
| `history_title` | `History_Title` | История платежей |
| `history_status_paid` | `History_StatusPaid` | Оплачено |
| `history_status_pending` | `History_StatusPending` | В обработке |
| `history_status_failed` | `History_StatusFailed` | Ошибка |
| `history_status_canceled` | `History_StatusCanceled` | Отменён |
| `history_status_unknown` | `History_StatusUnknown` | Не определён |
| `history_kind_renew` | `History_KindRenew` | Продление |
| `history_kind_purchase` | `History_KindPurchase` | Покупка подписки |
| `history_kind_topup` | `History_KindTopUp` | Пополнение баланса |
| `history_kind_devices` | `History_KindDevices` | Дополнительные устройства |
| `history_kind_upgrade` | `History_KindUpgrade` | Улучшение тарифа |
| `history_kind_other` | `History_KindOther` | Платёж |
| `history_empty_title` | `History_EmptyTitle` | Платежей пока нет |
| `history_empty_body` | `History_EmptyBody` | Здесь появится история покупок и продлений. |
| `history_error` | `History_Error` | Не удалось загрузить историю. Проверьте сеть и повторите. |

### 8.10 Sign-in methods

| Android name | Desktop key | Text |
|---|---|---|
| `linking_title` | `Linking_Title` | Способы входа |
| `linking_telegram` | `Linking_Telegram` | Telegram |
| `linking_email` | `Linking_Email` | Почта |
| `linking_google` | `Linking_Google` | Google |
| `linking_not_linked_m` | `Linking_NotLinkedM` | Не привязан |
| `linking_not_linked_f` | `Linking_NotLinkedF` | Не привязана |
| `linking_via_site` | `Linking_ViaSite` | Привязка через сайт |
| `linking_tg_sheet_title` | `Linking_TgSheetTitle` | Привязка Telegram |
| `linking_tg_sheet_body` | `Linking_TgSheetBody` | Откройте бота и подтвердите привязку. |
| `linking_tg_open_bot` | `Linking_TgOpenBot` | Открыть бота |
| `linking_tg_waiting` | `Linking_TgWaiting` | Ждём подтверждения… |
| `linking_tg_done` | `Linking_TgDone` | Telegram привязан |
| `linking_email_sheet_title` | `Linking_EmailSheetTitle` | Привязка почты |
| `linking_email_send` | `Linking_EmailSend` | Отправить |
| `linking_email_sent` | `Linking_EmailSent` | Письмо отправлено. Проверьте почту. |

### 8.11 The dash debt this closes

Nine of the dash-carrying strings counted in §9.7 live on this tab. They are named here rather than
quoted, so that this document itself stays dash-clean:

| Resource | Fate |
|---|---|
| `account_price_option` | deleted with the rest of the dead tariff strings |
| `pay_method_from_balance_fmt` | replaced by `pay_from_balance` + `pay_balance_have` (section 8.7) |
| `devices_diag_empty`, `devices_diag_failed` | deleted with the diagnostic dialog |
| desktop `Account_TopUpHint` | deleted with the old top-up flyout; the new sheet has a label, not a hint |
| desktop `Account_EstimateNote` | replaced by `Devices_AddNote` «Точную сумму посчитаем при оплате» |
| desktop `Account_FirstSub` hint | replaced by `account_empty_body` (§9.5 copy) |
| desktop `Account_SignInHint` | replaced by `account_gate_body` |
| desktop `Buy_SuccessBody` | its dash becomes a comma: «Серверы уже добавлены, можно подключаться» |

After this rework, `grep -rn -e '-' -e '-' values*/strings_account.xml values*/strings_devices.xml
values*/strings_pay.xml` (with the two literal dash characters, as §9.7 writes it) returns nothing for
this tab's files on both platforms.

**Voice:** formal «вы» everywhere. The desktop's «Войди в departament», «Оформи первую подписку»,
«Выбери тариф» are gone (AS §3.2).

---

## 9. Data contract binding

**Rule: a value not in this table may not be drawn** (AS §4).

| Rendered | Source | Fallback chain | Trap |
|---|---|---|---|
| Head name | `/client/auth/me` | `telegramName` → `@telegramUsername` → `email` → «Аккаунт» | the Home chip must use the **same** chain (it does not today) |
| Head handle | same | `@telegramUsername` → `email` → «Telegram не привязан» | - |
| Monogram / photo | `avatarUrl` (+6 aliases) or the local gallery pick | first grapheme of the name, upper-cased → `ic_acc_person` | - |
| Balance | `balance` (+ `currency`, ignored for display) | `0 ₽` | never `$` |
| Referral code | `referralCode` | row hidden when blank | - |
| Sub list | `GET /client/subscription/all` merged with `GET /client/subscription` | a primary-only account yields a synthesised root with a **blank id** | rename hidden when `id` is blank |
| Sub name | `displayName` → `defaultLabel` → «Подписка N» | - | - |
| Tariff badge | catalogue by `tariffId` → catalogue by `tariffPriceOptionId` → `tariffBadgeName()` | badge **hidden** when nothing resolves | «departament vpn» is filtered out by `tariffBadgeName()`; keep that filter |
| Expiry | `subscription.raw().expireAt` → `expireAtIso` | «Срок неизвестен» | perpetual sentinel: year ≥ 2099 or > 3650 days |
| Traffic | `subscription.raw()` only (**root only**) | block absent | `trafficUsed` **or** `userTraffic.usedTrafficBytes`; `trafficLimitBytes == null` = unlimited |
| Device slots | `totalDevices` → `hwidDeviceLimit` | «Без ограничений» when `<= 0` | `deviceCount` is **extra purchased devices**, not usage |
| Devices used | `GET /client/devices?uuid=`.length, **active sub only** | secondary subs show slots only | `connectedDevices` from `/all` is **always 0** - never render it |
| Renewal price | `renewalPrice` → `tariffPrice` → `autoRenewNextChargeAmount` | CTA becomes «Выбрать тариф» and routes to Buy | root only |
| Next charge date | `autoRenewNextChargeAt` | «Продлим автоматически» | root only |
| Auto-renew flag | profile `autoRenewEnabled` for the root; `sub.autoRenewEnabled` for secondaries | off | the root's flag lives on the **profile** |
| Trial | `isTrial` on the `/all` root entry | false | a primary-only account can never be detected as trial; that is a backend limit, not a bug to paper over |
| Upgrade targets | `GET /public/tariffs` minus the current tariff | the row and the button are hidden | - |
| Upgrade amount | `GET /client/subscriptions/upgrade-quote` | the confirm step cannot open without it | exact, not an estimate |
| Add-devices price | `pricePerExtraDevice` × N, from the catalogue | «Примерно» wording is mandatory | client-side estimate, volume tiers unknown |
| Payment methods | `GET /public/config` `plategaMethods[{id,label}]` | «Способы оплаты недоступны» + the sheet does not open | - |
| Bot handle | `telegramBotUsername` | the Telegram deep link is not offered | - |
| Site URL | `siteUrl` | the interim linking route is not offered | - |
| Trial availability | `trialEnabled` && `!trialUsed` | the trial button is hidden | - |
| History | `GET /client/payments` | empty state | `createdAt` is the sort key and the group key |
| Latest payment date | `payments.first().createdAt` | the row's value is omitted | - |
| QR | `GET /client/subscription/qr?uuid=` | the QR row is hidden | root/active only |

**Confirmation semantics that must survive** (AS §4.4): a Platega payment is **webhook-confirmed**.
Returning from the browser proves nothing. Both clients keep a pending state and a poll
(6 × 8 s against `GET /client/payments` for a status in `{paid, success, succeeded, completed,
confirmed, done}`) and **never claim success on return**. The charged amount equals the displayed
total for every caller except add-devices, which is explicitly labelled an estimate.

---

## 10. In-flight action matrix

Every action, its optimism, its feedback, its failure. §7.3: acknowledge within 100 ms, loading state
after 300 ms, cancel path beyond 3 s.

| Action | Optimistic? | In flight | Success | Failure | Offline |
|---|---|---|---|---|---|
| Pull / refresh | - | swipe indicator (Android) or the V4 refresh glyph → 20dp indicator | content swaps at 220 ms | inline error bar + «Повторить» | disabled, the offline bar is already showing |
| Tap «Баланс» | - | sheet opens at 300 ms | - | - | row disabled |
| Top-up submit | **no** (money) | the tapped method row shows a 20dp indicator; other rows 0.38 | sheet closes, browser opens, the polling bar appears | inline error under the amount field, sheet stays open | rows disabled |
| Renew | **no** | the CTA's label swaps in place for a 20dp indicator, same width and height | balance path: card re-renders + Snackbar «Оплата прошла». card path: browser + polling bar | Snackbar «Платёж не прошёл. Попробуйте другой способ оплаты.» + «Повторить» | CTA disabled |
| Upgrade: pick target | - | that row's trailing shows a 16dp indicator while the quote loads | the payment sheet opens with the exact amount | inline row error «Не удалось получить сумму. Повторите.» | rows disabled |
| Upgrade: pay | **no** | as renew | card re-renders, badge updates | as renew | disabled |
| Add devices | **no** | as renew | Devices page re-renders, count updates | as renew | disabled |
| Toggle auto-renew | **no** (money-adjacent, §8.4) | the switch moves to the new position, the row goes non-clickable, the subtitle swaps to «Сохраняем…» | the subtitle becomes the real next-charge line | the switch animates back over 220 ms, Snackbar «Не удалось изменить автопродление. Повторите.» + «Повторить» | row disabled |
| Rename | **yes** (not money, §8.4) | title updates immediately | nothing further | title reverts, Snackbar «Не удалось переименовать. Повторите.» + «Повторить» | dialog not openable |
| Copy referral / link | **yes** (local) | - | Snackbar «Код скопирован» / «Ссылка скопирована» | - | still works |
| Unlink device | **yes, deferred** | row removed at 220 ms; Snackbar + «Отменить» for 5 s; the call fires on dismiss | nothing further | the row returns, Snackbar «Не удалось отвязать устройство. Повторите.» + «Повторить» | disabled |
| Link Telegram | **no** | sheet shows the code + «Ждём подтверждения…» with a 20dp indicator | sheet closes, Snackbar «Telegram привязан», the head's handle line updates | inline «Не удалось получить код. Повторите.» + «Повторить» | row disabled |
| Sign out | **yes** (local) | - | the gate renders, all account state is cleared | - | **enabled** |
| Activate trial | **no** | button label → 20dp indicator | card re-renders as a trial, Snackbar «Пробный период активирован» | Snackbar with the mapped cause + «Повторить» | disabled |

---

## 11. Parity gaps and preconditions

Logged per §13 ("a parity gap logged in the platform's spec file, not a silently different design").

| # | Gap | Blocking? | Resolution |
|---|---|---|---|
| **PG-1** | Android `DepartamentApiClient` has no `requestLinkTelegram()` / `requestLinkEmail()` (AS §4.3) | **blocks native linking only** | port both from the desktop `IDepartamentApiClient`. Interim, specified in 6.11: the unlinked rows read «Привязка через сайт» and open `siteUrl` |
| **PG-2** | Android has no `createAppHandoff()` | not blocking | the «Веб-кабинет» row is deleted from **both** platforms (section 1.3), so the gap disappears |
| **PG-3** | Android has no `purchaseDevices()` returning `AddDevicesResultDto`; it has `addDevices()` returning a checkout URL | not blocking | the shared payment sheet already handles a checkout-URL result; the desktop uses whichever it has |
| **PG-4** | Desktop has no avatar picker | not blocking | the desktop tile shows the monogram or `avatarUrl`; the tile is not clickable there and carries no tooltip |
| **PG-5** | Desktop has 3 destinations, Android 4 (CS §D3) | out of scope of this file | recorded; the navigation rework owns it |
| **PG-6** | The desktop accent is not theme-aware (CS §D2, 2.98:1 in light) | **blocks light theme sign-off for this tab** | section 12 moves it inside `ThemeDictionaries`; it is a systemic fix, not a per-screen one |

Everything else on this tab is implementable today on both platforms with the endpoints that already
exist (`renew` = `payPlatega` with `subscriptionUuid`; `upgrade`, `upgradeQuote`, `addDevices`,
`toggleAutoRenew`, `togglePrimaryAutoRenew`, `renameSubscription`, `getQr`, `getDevices`,
`deleteDevice`, `getPayments`, `activateTrial` are all present in `AccountRepository.kt` and its
desktop twin).

---

## 12. Tokens added or changed

Every addition carries a purpose comment and, for a colour, its measured contrast (§3.5, §F20).

### 12.1 Android `res/values/dimens.xml`

```xml
<!-- Primary CTA height. Mirrors desktop Size.CtaTall 52. -->
<dimen name="cta_height">52dp</dimen>
<!-- Segmented-control height. Mirrors desktop Size.SegmentChip 44. -->
<dimen name="segment_height">44dp</dimen>
<!-- Row text origin = screen_gutter 16 + tile_size 40 + space_12 12. Hairline inset. -->
<dimen name="row_text_origin">68dp</dimen>
<!-- Traffic meter track/fill height. -->
<dimen name="meter_height">4dp</dimen>
<!-- Skeleton bar height (one value for all bars, whatever text they stand for). -->
<dimen name="skeleton_bar_height">16dp</dimen>
<!-- Empty-state tile. Mirrors desktop Size.EmptyIcon / Size.EmptyGlyph. -->
<dimen name="empty_icon_size">64dp</dimen>
<dimen name="empty_glyph_size">32dp</dimen>
<!-- Android touch floor for an icon-only control (§7.2). -->
<dimen name="icon_button_touch">48dp</dimen>
```

**Deleted:** `sub_card_height` 152dp, `dot_size`, `dot_size_active`, `dot_gap` (no carousel, no dots).

### 12.2 Android colour attributes

Resource-qualified colours do not follow `ThemeOverlay.Mono`, so both new colours are **theme
attributes**, set in `Theme.Departament`, its night variant and `ThemeOverlay.Mono`:

```xml
<attr name="colorWarning"   format="color"/>   <!-- «истекает» only -->
<attr name="colorErrorText" format="color"/>   <!-- error TEXT only; fills keep colorError -->
```

| Attr | Dark | Light | Mono | Measured |
|---|---|---|---|---|
| `colorWarning` | `#EAB308` | **`#8A6100`** (new) | `#F2F4F8` / `#111826` (ink) | dark 9.45:1 on surface, 10.27:1 on background; light 5.54:1 on white, 5.16:1 on background |
| `colorErrorText` | `#FF6069` | `#C42B32` | ink | dark 6.15:1 on surface (already verified in §3.5) |

`#8A6100` is new and exists because `#EAB308` measures **1.92:1** on white and cannot ship in the light
theme. `@color/icon_yellow` stays what it is: a tile fill and a glyph colour, never text.

### 12.3 Desktop `Assets/GlobalResources.axaml`

**Moved inside `ResourceDictionary.ThemeDictionaries`** (they are declared outside it today, which is
CS §C.4.21 and PG-6): `Color.Accent`, `Brush.Accent`, `Brush.OnAccent`, every `Brush.Tile.*`,
`Brush.SelectedFill`, every `Brush.StatusChip.*`. Light values: accent `#1E5FC7`, onAccent `#FFFFFF`,
tiles at 20 % of their light hue.

**Added:**

```xml
<!-- Dark -->
<SolidColorBrush x:Key="Brush.AccentHover"   Color="#3D7EF0"/>  <!-- onAccent #00183A = 4.57:1 -->
<SolidColorBrush x:Key="Brush.AccentPressed" Color="#3877E0"/>
<SolidColorBrush x:Key="Brush.WarnText"      Color="#EAB308"/>  <!-- 9.45:1 on Surface -->
<!-- Light -->
<SolidColorBrush x:Key="Brush.AccentHover"   Color="#2A6BD6"/>  <!-- white label = 5.03:1 -->
<SolidColorBrush x:Key="Brush.AccentPressed" Color="#174EA6"/>  <!-- white label = 7.84:1 -->
<SolidColorBrush x:Key="Brush.WarnText"      Color="#8A6100"/>  <!-- 5.54:1 on Surface -->
```

**Added to `GlobalStyles.axaml`:** `Button.Tonal.Tall` (52), and `:pointerover` + `:disabled` +
`:focus-visible` on `Button.LinkAction`, `Button.Stepper`, `Button.IconButton40`, `Button.BackNav`,
`ToggleButton.Segment` and the new `RowItem` (CS §C.7.33-35).

**Deleted:** `TrafficFillBrush` (the `LinearGradientBrush`), `Size.TrafficPill`, `Border.StatusChip`'s
use for subscription health (the class survives for payment status only), the 16 local `Geo.Acc.*`
declarations in `AccountView.axaml` (moved to `GlobalResources.axaml`, and the duplicate of
`Geo.ChevronRight` is dropped outright).

### 12.4 Style corrections that this tab depends on

| Style | Change | Why |
|---|---|---|
| `@anim/press_scale` | 0.96 → **0.97**; release interpolator `ease_out_quart` → `ease_out_quint` | §7.1 |
| `@style/SettingsSectionLabel` | `paddingTop` 18dp → `@dimen/space_24`; `paddingBottom` → `@dimen/space_8` | §1.4.5 - 18 is off-scale |
| `@drawable/bg_acc_badge` | `?attr/iconTileBgBlue` → `?attr/colorSurfaceContainerHighest`; text → `colorOnSurface` | D§3.2: the tariff badge is not lit |
| `Widget.Material3.Button.TonalButton` (via `themes.xml`) | fill `?attr/colorSurfaceContainerHighest`, label `?attr/colorOnSurface` | parity with desktop `Button.Tonal`; removes a blue |
| `themes.xml` | set `materialButtonStyle`, `materialCardViewStyle`, `materialSwitchStyle`, `textInputStyle`, `chipStyle` and the three `shapeAppearance*Component` attrs | CS Part E: without these, every instance re-declares shape and height, which is the root cause of CS §A.2 |
| `BaseActivity` toolbar | `titleTextAppearance` → `@style/TextAppearance.App.Title`, height 56dp, background `?attr/colorBackground`, elevation 0 | §4.8, owner request §0.4.6; `ToolbarBrandTitle` is for the wordmark only |

---

## 13. Decisions this document takes, in §18 row format

Paste rows A-1 … A-14 into `00-rules.md` section 18 before implementation begins. Anything marked
**needs owner sign-off** does not ship until the row is pasted.

| # | Decision | Rule affected | Sign-off |
|---|---|---|---|
| A-1 | The Account tab's purpose is fixed as the four questions in 1.1; the hero figure is remaining **time**, not balance, and it exists only when the time is short | new (23) | taken |
| A-2 | §4.1's 68dp text origin applies to **tiled** rows. A tile-less list (payment sheet, payment history) uses the 16dp gutter as its origin and its hairline inset. A surface never mixes the two | §4.1 | taken |
| A-3 | Signed out, the Account destination **remains** and renders a sign-in gate on both platforms. Runtime removal of a navigation destination is a defect. A build without a backend removes it at start-up | §7.7, §13, AS §5 open question | taken |
| A-4 | Both subscription carousels are deleted. 1 sub = the card; 2-3 = a segmented control; 4+ = a select row with a radio sheet | §11.2, §1.3 | taken |
| A-5 | Every icon tile on the Account tab and its four sub-pages is neutral. No blue tile, no green balance tile, no accent row title | §3.6, D§3.2 | taken |
| A-6 | The subscription card carries **no status chip**. Health is expressed by typography, size and colour on the time block | §2.4.4 | taken |
| A-7 | The traffic meter fill is **neutral**, amber at ≥ 90 %, red at 100 %. Accent is never spent on a value the user does not control | §5.1, §6.5 | taken |
| A-8 | Skeletons **do not pulse**. They are static P3 blocks in the shape of the result, shown after 300 ms, crossfaded at 220 ms | D§8.5 | taken |
| A-9 | Tonal buttons are neutral (`colorSurfaceContainerHighest` / `SurfaceHighest`) on both platforms | §11.2, §13 | taken |
| A-10 | Removing a device is «отвязать» on both platforms, and it is **undo**, not a confirmation dialog | §9.3, §7.5 | taken |
| A-11 | `getReferralStats` is **not** surfaced. The referral **code** is one row; referral statistics are a web-cabinet surface | AS §5.7 | taken |
| A-12 | Promo codes are **not** surfaced until a server-side quote exists. The client cannot honour "displayed total == charged amount" for a discounted price without one, and inventing the arithmetic locally would violate AS §4.4 | AS §4.4, §5.7 | taken |
| A-13 | New colour tokens `colorWarning` / `Brush.WarnText` (`#EAB308` dark, `#8A6100` light) and `colorErrorText` / `Brush.RedText` as **theme attributes**, because resource-qualified colours do not follow `ThemeOverlay.Mono` | §3.5, §F20 | **needs owner sign-off** (new hue value) |
| A-14 | The «Веб-кабинет» row is deleted from the desktop rather than ported to Android | §13 | taken |

Two rows from `03-direction.md` §11.2 are **preconditions** for this tab and must be resolved first:
**D-1** (which Cyrillic UI face) and **D-2** (Space Grotesk scoped to figures). Every "UI face" in this
document means whatever D-1 resolves to; every "figure face" means Space Grotesk. If D-1 is unresolved
at implementation time, the fallback in D§6.1 clause 3 applies (`sans-serif` on Android, an explicit
per-OS stack on desktop) and the layouts do not change.

---

## 14. Acceptance for this tab

Run §16's full pre-flight. These are the additional checks this tab must pass, and they are all
mechanical or countable.

**Counting**

- [ ] Cards on the tab: **exactly 1** (the subscription). Screenshot it and count rectangles with a stroke.
- [ ] Filled accent surfaces: **1** when the state needs action, **0** when active or perpetual (table 1.4).
- [ ] Blue icon tiles on the tab and its four sub-pages: **0**.
- [ ] Distinct button heights on the tab: **2** (52 and 48). Distinct button radii: **1** (pill).
- [ ] Distinct chevron sizes: **1** (20dp). Distinct glyph sizes: 22 in a tile, 20 inline, 32 in an empty state.
- [ ] Row text origins on any one surface: **1**.
- [ ] Status hues in payment history: **2** (green, red) plus neutral.
- [ ] Display figures on the screen: **1** in the expiring state, **0** otherwise.
- [ ] Vertical gap values used: at least **3** distinct (4/8/12, 16, 24, 32). If everything is 16, the rhythm failed.

**Mechanical (must return nothing, run from `V2rayNG/app/src/main/res`)**

```bash
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint|app:strokeColor)="#' \
  layout/fragment_account.xml layout/layout_account_*.xml layout/layout_row_*.xml \
  layout/activity_devices.xml layout/activity_payment_history.xml layout/sheet_*.xml
grep -rn 'textAllCaps="true"\|android:textStyle="bold"\|android:textSize' \
  layout/fragment_account.xml layout/layout_account_*.xml layout/sheet_*.xml
grep -rnoE '"(-?[0-9]+)dp"' layout/fragment_account.xml layout/layout_account_*.xml \
  | grep -vE '"(0|1|4|8|12|16|24|32|40|44|48|52|56|64)dp"'
grep -rn -e '—' -e '–' values/strings_account.xml values/strings_devices.xml values/strings_pay.xml
grep -rn '\.\.\.' values/strings_account.xml
```

```bash
# Desktop, from v2rayN/v2rayN.Desktop
grep -rnE '(Background|Foreground|BorderBrush|Fill|Stroke)="#' Views/AccountView.axaml Views/DevicesView.axaml Views/PaymentHistoryView.axaml Views/LinkingView.axaml
grep -rn 'LinearGradientBrush\|BoxShadow' Views/AccountView.axaml
grep -rn 'Classes="Headline"' Views/DevicesView.axaml Views/PaymentHistoryView.axaml Views/LinkingView.axaml Views/BuyView.axaml
grep -rn 'FontSize=' Views/AccountView.axaml
grep -rnoE '(Margin|Padding|Spacing)="[0-9, ]+"' Views/AccountView.axaml \
  | grep -vE '"(0|4|8|12|16|24|32|68)[ ,0-9]*"'
```

**By eye, both themes, both platforms**

- [ ] 30 days, 3 days, expired, trial, perpetual, unknown-expiry: six screenshots of the card, each read aloud.
- [ ] 0 / 1 / 2 / 3 / 7 subscriptions: five screenshots of the switcher region.
- [ ] Skeleton, empty, error, offline, partial, payment-polling: six screenshots of the whole tab.
- [ ] Font scale 200 % at 320dp width; 200 % DPI at a 900×600 window. No clipping, no truncated primary label.
- [ ] Change the balance from `1 500 ₽` to `10 000 ₽` in the preview: nothing moves horizontally.
- [ ] Find a Russian string set in Space Grotesk. There must not be one.
- [ ] Tab through the desktop tab with the mouse unplugged: every row, every button, every switch reachable, every focus ring visible.
- [ ] TalkBack through the Android tab: the hero reads «Осталось 5 дней» as one phrase; the switch row announces its state.
- [ ] Toggle reduced motion on both platforms and repeat the six state screenshots.
- [ ] Crop the wordmark. Do the figure face, the single lit element and the hairline ledger still identify the product?
