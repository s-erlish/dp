# 15 - Account tab, Android

**The complete Android specification for the Аккаунт destination and everything it opens, at full
functional parity with the desktop client.**

This document exists because of one owner sentence, quoted verbatim:

> «то что на пк мы сделали надо все переносить … с тем же аккаунтом, его вкладкой»

and because the Android Account tab today is a **read-only dashboard**: it cannot renew, cannot
toggle auto-renew, cannot add devices, cannot upgrade, cannot rename, cannot show a QR, cannot link
Telegram, cannot sign out, and shows no subscription state at all. Every one of those exists on the
desktop, and eleven of them are already wired end to end in
`java/com/v2ray/ang/viewmodel/AccountViewModel.kt` and `java/com/v2ray/ang/auth/AccountRepository.kt`
as **dead code with no view**.

Parity here means **functional parity, not visual copying.** The desktop is richer in function and
wrong in form (`21-account-survey.md` §2.3 lists fourteen defects on it, including a live gradient,
raw hex in a view, a four-panel purchase wizard inside a hand-rolled drag carousel, and a
permanently disabled button shipped as UI). Android gets everything the desktop can do, drawn to the
2026 system. The desktop then adopts this form; that half is `23-account-rework.md` §7.

---

## 0. How to read this document

### 0.1 Precedence

1. **`00-rules.md`** - the law. Cited as **§n**. Any value here that contradicts it is a bug in this
   file.
2. **`03-direction.md`** - why the product looks like this. Cited as **D§n**.
3. **`10-design-system.md`** - the token layer. Every token name below (`color_on_surface`,
   `space_16`, `radius_control`, `size_row`, `motion_state`) is defined there in sections 2 and 3,
   with its Android attribute and its desktop key. **This is the token authority for this file.**
4. **`11-app-structure.md`** §4.3 - the Account destination's IA skeleton. **This document is its
   completion.** The four places it deviates are recorded in section 18 with reasons.
5. **`22-components.md`** - component behaviour where the design system is silent (loading contract,
   double-press guard, selection indicator, status-hue ruling).
6. `20-control-survey.md` (**CS**), `21-account-survey.md` (**AS**), `23-account-rework.md` (**AR**)
   - the factual baseline and the cross-platform rework. Facts cited, decisions re-derived.

### 0.2 Reading order for the implementer

1. **Section 2** - the parity ledger. It tells you what to build and, for each item, whether the
   Android client can already call the backend for it. **Six items cannot.** Read this before
   estimating anything.
2. **Section 3** - the information architecture. One order, no exceptions.
3. **Section 4** - typesetting. Money, dates, counts and traffic are the product's voice (D§3.1).
   Get them wrong and nothing else matters.
4. **Sections 5-11** - the screens, top to bottom, with their trees.
5. **Section 12** - every state. A screen without its states is incomplete, not "phase one" (§15).
6. **Section 15** - the copy. **Do not invent a string.** If you need one that is not there, the
   design is incomplete and you stop.
7. **Section 16** - the data contract. **A value not in section 16 does not exist and may not be
   drawn.**

### 0.3 What a number in this file means

Where this file gives a dp value, that value ships. Where it gives a Russian string, that string
ships. Where it gives a duration or an interpolator, that one is used and no other. Nothing here is
a sketch.

---

## 1. The job of this tab

### 1.1 One sentence

> **Аккаунт - это счёт: что оплачено, сколько от этого осталось, и одна кнопка, которая это
> меняет.**

Four questions, in this order of urgency:

| # | Question | Answered by | Rank on the screen |
|---|---|---|---|
| 1 | Сколько у меня денег и до какого числа есть доступ? | the head's balance figure and the card's time block | the one Display figure (which of the two carries it is section 1.2) |
| 2 | Что именно у меня есть? | the card: name, tariff badge, traffic, devices | Title weight |
| 3 | Что спишется дальше? | the auto-renew line and the CTA's price suffix | Subtitle weight |
| 4 | Что я могу с этим сделать? | one CTA on the card, then three row groups | one filled button at most, then a ledger |

Anything answering none of the four leaves the tab. Section 1.4 lists what leaves and where it goes.

### 1.2 The Display figure moves with the account's state

`03-direction.md` §7.3 caps the screen at **one Display figure**. `00-rules.md` §3.4 names both
candidates for it: "One hero figure per screen: **balance**, connected timer, big status". D§2.1 is
explicit that the user "will open the app again that evening only if the subscription is running
out". Both are right, at different times. So the slot is **conditional**, and it is the only
conditional geometry on the tab:

```
urgent = health ∈ { Expiring (1-7 дней), ExpiringToday, Expired }
```

| | `urgent == false` | `urgent == true` |
|---|---|---|
| Head balance figure | **Display** 34sp/700, `color_on_surface`, `₽` at Headline 24/700 in `color_on_surface_variant` | **Title** 16sp/700, `color_on_surface`, `₽` at Title.Medium 16/500 in `color_on_surface_variant` |
| Card time block | line 1 «Активна до 3 августа 2026» at **Title** 16/700 | **Display** 34sp/700 figure + unit at Title 16/700, in `?attr/warning` (expiring) or `?attr/pingBad` (expired) |
| Card CTA | Secondary (neutral) | **Primary** (the tab's one filled accent surface) |
| Display figures on screen | exactly 1 | exactly 1 |

The head's balance block carries `android:minHeight="@dimen/btn_height_compact"` (48dp) so the
figure changes size **inside a fixed-height block** and nothing below it moves. The screen's
silhouette changes with the account's state; its layout does not jump.

This is also the answer to the category-reflex test (§2.4.1): a VPN account screen whose largest
element becomes a countdown the week before it lapses, and a wallet the rest of the time, is not the
category default.

### 1.3 Where the accent is spent, exhaustively

`22-components.md` R14 is the allow-list. On this tab it resolves to:

| Account state | Filled accent surface | Accent-container elements | Accent fill |
|---|---|---|---|
| Signed out (gate) | 1 - «Войти через Telegram» | 0 | 0 |
| No subscription | 1 - «Купить» | 0 | 0 |
| Trial | 1 - «Купить тариф» | tariff badge («Пробный» is `Chip.Neutral`, so 0) | traffic meter, if data exists |
| Expired | 1 - «Продлить · 450 ₽» | tariff badge | 0 (traffic block hidden) |
| Expiring | 1 - «Продлить · 450 ₽» | tariff badge, selected segment | traffic meter |
| **Active / perpetual** | **0** | tariff badge, selected segment | traffic meter |
| Loading / error / offline | 0 | 0 | 0 |

**Everything else on this tab and on all four of its sub-pages is neutral.** Every icon tile is
`@color/icon_tile_neutral` `#20242B` with a `@color/icon_glyph_neutral` `#9BA1AD` glyph. There are
no blue tiles (CS §C.4.22 counts 56 blue tiles out of 65 today), no green tile on the balance
payment row (AS §1.8), and **no accent-coloured row title** - `row_buy`'s
`android:textColor="?attr/colorPrimary"` in `activity_account.xml` is a named defect in R14.

Non-accent colour, and its only permitted uses on this tab:

| Colour | Token | Where, and nowhere else |
|---|---|---|
| Amber `#EAB308` | `?attr/warning` | the Display figure and its unit in the **expiring** state; the auto-renew risk line |
| Red `#FF6069` | `?attr/pingBad` | the **expired** card title; the traffic value at 100 %; the «Выйти» row title; inline form errors; the «Ошибка» payment chip label |
| Green `#22C55E` | `?attr/colorTertiary` | the «Оплачено» payment chip, and nothing else |

Colour is never alone (§6.3): expiring also carries the word «дней» and a filled CTA; expired also
carries the words «Подписка истекла»; «Оплачено» is a word before it is a colour.

### 1.4 What leaves the tab, and where it goes

| Today | Verdict | New home |
|---|---|---|
| 52dp avatar frame + 18dp floating camera badge | deleted | 40dp neutral tile; tapping it opens the avatar options |
| Filled accent «Пополнить» in the profile card | demoted | **Secondary** (neutral) 48dp button, same position |
| Profile card (`MaterialCardView` around the identity) | deleted | the head sits on the ground plane; the subscription card is the **only** card on the tab (§4.4) |
| Referral chip in the hero | moved | row «Реферальный код» in «Оплата», tap copies |
| `ViewPager2` subscription carousel + dots | deleted | segmented control (2-3 subs) or a select row (4+) - section 6 |
| Fixed 152dp subscription card | deleted | `wrap_content`; `@dimen/sub_card_height` is retired |
| «Ответ сервера (диагностика)» dialog | deleted | a designed error state; the raw body goes to `Log.w` |
| «Ошибка оплаты» dialog printing `HTTP %1$s` | deleted | §9.4 error copy, no codes ever visible |
| 900 ms `AccelerateDecelerateInterpolator` skeleton pulse | retimed | `@integer/motion_pulse` 1000 ms `ease_standard` |
| Centred `ProgressBar` on Payment history | deleted | a skeleton in the shape of the ledger (§15 forbids it by name) |
| Toasts for top-up success, referral copy, avatar errors | replaced | `Snackbar`, anchored above the bottom navigation (§1.4.8) |
| `getQr` (endpoint wired, no UI) | **designed in** | Devices sub-page, «Подключить устройство» group |
| `renameSubscription` (wired, no UI) | **designed in** | a 48dp icon button in the card header |
| `toggleAutoRenew` / `togglePrimaryAutoRenew` (wired, no UI) | **designed in** | Row.Toggle inside the card |
| `upgrade` + `upgradeQuote` (wired, no UI) | **designed in** | «Улучшить тариф» row → upgrade sheet → payment sheet |
| `addDevices` (wired, no UI) | **designed in** | «Добавить устройства» sub-page off Devices |
| `activateTrial` (wired, no UI) | **designed in** | a Tertiary button in the empty card, gated on `trialEnabled && !trialUsed` |
| `logout` (wired, no UI) | **designed in** | «Выйти» row in the «Вход» group |
| `getReferralStats` (wired, no UI) | **not surfaced** | decision A-11: the code is a row, the statistics are a web-cabinet surface |
| `checkPromo` / `activatePromo` (wired, no UI) | **not surfaced** | decision A-12: the client cannot honour "displayed total == charged amount" without a server-side quote |
| `totpEnabled` | not surfaced | read-only with no endpoint to change it is noise |

---

## 2. The parity ledger

**Read this first.** Column 4 is the one that decides whether a surface can ship. Endpoint paths are
from `auth/BackendConfig.kt` `Endpoints` (Android) and `Account/BackendConfig.cs` `Endpoints`
(desktop); both hang off base `https://web.departament.site/api`.

| # | Capability | Desktop today | Android today | Endpoint | Android client status |
|---|---|---|---|---|---|
| 1 | Identity, balance, referral code | yes | yes | `GET /client/auth/me` | **present** (`getMe`) |
| 2 | Avatar from gallery | no (monogram only) | **yes** | local only | present - Android is ahead; PG-4 |
| 3 | Subscription list + active summary | yes | yes | `GET /client/subscription/all` + `GET /client/subscription` | **present** |
| 4 | Tariff badge | yes | yes | `GET /public/tariffs` | **present** |
| 5 | Health: активна / истекает / истекла / бессрочно | yes | **no** | derived from `expireAtIso` | derivable, no endpoint |
| 6 | Traffic meter | yes | **no** | `subscription.raw()` fields | **present**, root only |
| 7 | Device count on the card | text only | text only | `GET /client/devices?uuid=` | **present** |
| 8 | Rename subscription | **no** | **no** | `PATCH /client/subscription/{scope}/{id}/name` | **present** (`renameSubscription`), unused by both |
| 9 | QR / connect link | **no** | **no** | `GET /client/subscription/qr?uuid=` | **present** (`getSubscriptionQr`), unused by both |
| 10 | Renew, **root**, by balance | yes | **no** | `POST /client/payments/balance` | **present** (`payBalance`) |
| 11 | Renew, **root**, by card | yes | **no** | desktop uses `POST /client/payments/tariff/platega` | **partial** - Android has only `POST /client/payments/platega`; see PG-2 |
| 12 | Renew, **secondary**, by card | yes | **no** | `POST /client/payments/tariff/platega` with `scope` + `subscriptionId` | **absent** - PG-2, blocking |
| 13 | Auto-renew toggle, secondary | yes | **no** | `PATCH /client/secondary-subscriptions/{id}/auto-renew` | present but **broken wire key** - PG-3 |
| 14 | Auto-renew toggle, root | yes | **no** | `PATCH /client/auto-renew` | **wrong path and wrong wire key** - PG-3, blocking |
| 15 | Upgrade quote | yes | **no** | `GET /client/subscriptions/upgrade-quote?targetTariffId=` | **present** (`getUpgradeQuote`) |
| 16 | Upgrade | yes | **no** | `POST /client/subscriptions/upgrade` | **present** (`upgrade`) |
| 17 | Add devices | yes (kebab wizard) | **no** | `POST /client/subscription/{scope}/{id}/add-devices` | **present** (`addDevices`, checkout-URL variant) |
| 18 | Devices list + unlink | yes | yes | `GET /client/devices`, `POST /client/devices/delete` | **present** |
| 19 | Payment history | yes | yes | `GET /client/payments` | **present** |
| 20 | Top up | yes | yes | `POST /client/payments/platega` | **present** |
| 21 | Payment methods, bot handle, site URL, trial flag | yes | yes | `GET /public/config` | **present** |
| 22 | Activate trial | **no** | **no** | `POST /client/trial` | **present** (`activateTrial`), unused by both |
| 23 | Link Telegram | yes | **no** | `POST /client/link-telegram-request` | **absent** - PG-1 |
| 24 | Link email | yes | **no** | `POST /client/link-email-request` | **absent** - PG-1 |
| 25 | Google linked flag | yes | **no** | `GET /client/auth/me` → `googleLinked` | **field absent from the DTO** - PG-4 |
| 26 | Sign out | yes | **no** | local | `AccountViewModel.logout()` exists, no view calls it |
| 27 | Signed-out gate inside the tab | yes | **no** (the tab is hidden) | n/a | decision A-3 |
| 28 | Post-login sync overlay | yes (`AccountSyncView`) | **no** | n/a | out of scope; owned by the launch spec |

### 2.1 The six parity gaps, in full

| # | Gap | Severity | Fix |
|---|---|---|---|
| **PG-1** | `DepartamentApiClient` has no `requestLinkTelegram()` / `requestLinkEmail()`. The desktop's `IDepartamentApiClient` declares both against `POST /client/link-telegram-request` and `POST /client/link-email-request`. | **Blocks native linking.** Owner request §0.4.9 («Привязать Telegram») has no backend on Android without it. | Port both, plus `LinkTelegramRequestDto` and `MessageResponseDto`. **Interim, specified in section 11.3:** the unlinked rows read «Привязка через сайт» and open `publicConfig.siteUrl` in a Custom Tab. The screen is never broken; it is merely thinner. |
| **PG-2** | No `payTariffPlatega()` (`POST /client/payments/tariff/platega`), and `PaymentRequestDto` has no `scope`, `subscriptionId` or `extraDevices` fields. The desktop sends all three. | **Blocks card renewal of a secondary subscription.** Root renewal by card degrades to `POST /client/payments/platega` with `tariffId` + `tariffPriceOptionId`, which the backend treats as a fresh purchase of the root. | Add the three fields to `PaymentRequestDto`, add `payTariffPlatega` to the interface, the impl and `AccountRepository.renew()`. Until then the card CTA on a **secondary** subscription is disabled with the subtitle «Продление доступно на сайте» and a Tertiary «Открыть сайт». |
| **PG-3** | Two independent auto-renew bugs. (a) `Endpoints.primaryAutoRenew = "/client/subscription/auto-renew"`; the real route is `/client/auto-renew` (the desktop records this as bug #29: the old path 404s). (b) `AutoRenewRequestDto(val autoRenew: Boolean)` serialises `{"autoRenew":true}`; **both** auto-renew routes read `enabled` (`Boolean(req.body.enabled)`), so the flag is silently ignored. | **Blocks the auto-renew toggle entirely.** Ship the switch on top of this and it will animate, report success, and change nothing. | `primaryAutoRenew = "/client/auto-renew"`, and `AutoRenewRequestDto` gets `@SerializedName("enabled")` on the field. Two one-line changes; both must land before the Row.Toggle is wired. |
| **PG-4** | `UserProfileDto` has no `googleLinked`, `appleLinked`, `hasPassword`. | Not blocking. | The Google row on «Способы входа» exists **only** when `googleLinked == true`; without the field it never appears, which is the same outcome as the desktop's permanently disabled «Скоро» button minus the defect. Add the three booleans when convenient. |
| **PG-5** | No `purchaseDevices()` returning `AddDevicesResultDto` (the desktop's balance-settling variant). Android has only `addDevices()` returning a checkout URL. | Not blocking. | The add-devices flow already routes through the shared payment component, which handles a checkout-URL result. Balance payment for devices is unavailable until the variant is ported; the balance row on that surface is hidden, not disabled (an invisible capability is better than a dead one). |
| **PG-6** | Desktop has no avatar picker. | Not blocking, and it is **Android that is ahead.** | Recorded so nobody "fixes" Android down to the desktop. The desktop tile shows the monogram or `avatarUrl` and is not clickable. |

### 2.2 Confirmation semantics that must survive

A Platega payment is **webhook-confirmed**. Returning from the browser proves nothing. Both clients
therefore keep a pending state and a poll: **6 iterations × 8 s** against `GET /client/payments`,
looking for a status in `{paid, success, succeeded, completed, confirmed, done}`. **No surface ever
claims success on return from the browser.** The current `AccountFragment.startPaymentPolling()`
(6 × 8 s) and `BuyTariffActivity` (5 × 8 s) are unified at 6 × 8 s.

The **charged amount equals the displayed total** for every caller except add-devices, whose price
is a client-side estimate (the volume `deviceDiscountTiers` are not exposed on `TariffDto`) and is
therefore labelled «Примерно» with the note «Точную сумму посчитаем при оплате».

---

## 3. Information architecture

One order. No exceptions.

```
┌ header 56 ───────────────────────────────────────────────────────────┐
│ Аккаунт                                            Title 16/700       │
├ content, gutter 16 (24 at sw600dp), max width 720, centred ──────────┤
│ [offline bar]          only when offline                              │
│ [payment bar]          only while a checkout is being polled          │
│                                                                       │
│ HEAD                   40 tile · имя · @handle       (no card)        │
│                        Баланс                                         │
│                        1 500 ₽                    [ Пополнить ]        │
│  32                                                                   │
│ [SWITCHER]             segmented (2-3 подписки) | select row (4+)     │
│  12                                                                   │
│ CARD                   подписка                   ← the ONE card      │
│  24                                                                   │
│ «Подписка»             Устройства                 2 / 5               │
│                        Улучшить тариф                    ›            │
│  24                                                                   │
│ «Оплата»               Купить подписку                   ›            │
│                        История платежей          12.06.2026           │
│                        Реферальный код               ABC123           │
│  24                                                                   │
│ «Вход»                 Способы входа        Telegram, почта           │
│                        Выйти                                          │
│  32 + navigationBars inset + bottom-nav height                        │
└───────────────────────────────────────────────────────────────────────┘
```

Counted against D§7.3: **3 row groups** (cap 4), **max 3 rows per group** (cap 7), **1 card**
(cap 1), **1 Display figure** (cap 1), **2 levels below the tab** (cap 2: tab → Devices → Добавить
устройства is the only 3-deep path and «Добавить устройства» is a leaf, so the cap holds by the
sheets-are-not-levels rule in `11-app-structure.md` §4).

### 3.1 Row trailing discipline

§4.5: a row's trailing slot holds **exactly one** thing, never two. The rule you can teach:

> **A row that carries a value carries no chevron. A row that carries no value carries a chevron.**
> The whole row is the target either way.

| Row | Trailing | Why |
|---|---|---|
| Устройства | value `2 / 5` | the value is the point; the tap is a bonus |
| Улучшить тариф | chevron | navigation to a choice |
| Купить подписку | chevron | navigation |
| История платежей | value `12.06.2026` | the latest payment date is the point |
| Реферальный код | value `ABC123` | the code is the point; the tap copies it |
| Способы входа | value `Telegram, почта` | the summary is the point |
| Выйти | nothing | a terminal action, no destination and no value |
| Автопродление (in the card) | switch | Row.Toggle |

### 3.2 Origin discipline

Two surface families, never mixed inside one surface:

- **Management surfaces** (the three Account groups, Devices, Способы входа): **tiled rows**, text
  origin `@dimen/row_text_origin` **68** = 16 gutter + 40 tile + 12 gap, hairlines inset to 68.
- **Transaction surfaces** (the payment component, Payment history): **tile-less ledger rows**, text
  origin **16**, hairlines inset to 16.

The current 72dp divider inset in `activity_account.xml` is debt and dies with the file.

### 3.3 Conditional rows

| Row | Hidden when |
|---|---|
| «Подписка» group as a whole | `subs.isEmpty()` - there is nothing to manage |
| «Улучшить тариф» | no upgrade targets, or the sub is trial / expired / perpetual-without-tariff |
| «Купить подписку» | the card is already showing a filled «Купить…» CTA (empty / trial state) - **one entrance, never two** |
| «Реферальный код» | `referralCode` is blank |
| «Способы входа» → replaced by **«Привязать Telegram»** (Row.Navigation, subtitle «Управление подпиской из бота») | `telegramLinked == false`. This is owner request §0.4.9 as an explicit, state-driven CTA row |

---

## 4. Typesetting

D§3.1 makes numbers the product's identity. This section is the contract.

### 4.1 One money formatter

There are **six** money formatters in the product today and three of them print `$`
(`BuyTariffActivity.currencySymbol`, `PaymentsAdapter.formatMoney`, desktop `BuyViewModel`). The same
balance can be signed `₽` on the Account tab and `$` on the Buy screen. All six are deleted.

New: `object Money` in `com/v2ray/ang/util/Money.kt`.

```kotlin
object Money {
    private const val THIN = ' '   // U+2009 THIN SPACE - thousands separator
    private const val NBSP = ' '   // U+00A0 NO-BREAK SPACE - before the symbol
    fun format(amount: Double): String   // 1290.0 -> "1 290 ₽" ; 1290.5 -> "1 290,50 ₽"
}
```

Rules, identical to the desktop's `Common/Money.cs`:

1. The symbol is always **`₽`** (U+20BD), whatever `currency` says (§0.4.4). The backend's currency
   code is ignored for **display** and preserved verbatim in the **request body**.
2. Thousands separator: **U+2009 THIN SPACE**.
3. Before the symbol: **U+00A0 NO-BREAK SPACE**, so a price never wraps between figure and sign.
4. Kopecks print **only when non-zero**, with a **comma**: `1 290,50 ₽`, but `1 290 ₽`.
5. Rendered with `TextAppearance.App.Numeric`, `android:fontFeatureSettings="tnum, lnum"`,
   **`zero` off** - a slashed zero in a price reads as a correction mark (D§3.1).
6. A zero balance prints `0 ₽`. Never «-», never an empty string, never a hidden row.

**Width reservation.** A numeric column reserves `digits × 0.620 × fontSize`. Every money value on
this tab is `wrap_content` with the **title** constrained to `0dp` + `layout_weight="1"`, so a
12-digit balance ellipsises the title and never the money.

### 4.2 Dates, and the two-face rule

Space Grotesk contains **zero Cyrillic** (D§6.1). Therefore:

| Form | Face | Example | Where |
|---|---|---|---|
| Long, with a Russian month | **UI face** (whole string) | `3 августа 2026` | card time lines, auto-renew line |
| Long, current year | UI face | `3 августа` (year omitted when it is this year) | same |
| Short numeric | **figure face**, `tnum`, `zero` off | `12.06.2026` | row trailing values, sub-pick sheet |
| Numeric with time | figure face | `12.06.2026, 14:32` | payment history rows |
| Month group header | UI face, sentence case | `Июнь 2026` | payment history group headers |

**A string containing Cyrillic is never set in the figure face.** Mechanically checkable: no
`TextView` carrying `TextAppearance.App.Numeric` may contain a Cyrillic character. The Display hero
is the one place a figure gets its own slot: the number is one `TextView` in the figure face, the
word beside it is a **separate** `TextView` in the UI face, baseline-aligned.

New: `object Dates` in `com/v2ray/ang/util/Dates.kt`, with
`longRu(iso): String`, `shortNumeric(iso): String`, `numericWithTime(iso): String`,
`monthHeader(iso): String`. The current inline `formatIsoDate()` at the bottom of
`AccountFragment.kt` (a `substringBefore('T')` + `split('-')` string shuffle) is deleted.

### 4.3 How an expiry reads, at every distance

```
daysLeft  = ceil((expireAt - now) / 86_400_000) in the device's local zone
perpetual = expireAtIso is blank
            || year(expireAt) >= 2099
            || daysLeft > 3650
```

The perpetual sentinel is a **mirror of the desktop's `IsEffectivelyPerpetual`**. Android has no
such handling today and would print «Действует до 04.06.2099» for a forever account (AS §4.2).

| Health | Condition | Display figure | Line 1 | Line 2 | Colour | CTA |
|---|---|---|---|---|---|---|
| **Perpetual** | sentinel | none | «Бессрочная подписка» Title | «Срок не ограничен» Subtitle | onSurface / onSurfaceVariant | Secondary «Продлить · 450 ₽», hidden if no price |
| **Active** | `daysLeft > 7` | none (balance keeps it) | «Активна до 3 августа 2026» Title | «Осталось 214 дней» Subtitle | onSurface / onSurfaceVariant | **Secondary** «Продлить · 450 ₽» |
| **Expiring** | `1 ≤ daysLeft ≤ 7` | **`5`** Display 34/700 + «дней» Title 16/700 | *(the figure line is line 1)* | «Активна до 3 августа 2026» Subtitle | figure **and** unit `?attr/warning`; line 2 onSurfaceVariant | **Primary** «Продлить · 450 ₽» |
| **Expiring today** | `daysLeft == 0`, not past | none | «Истекает сегодня» Title | «Активна до 3 августа 2026» Subtitle | line 1 `?attr/warning` | **Primary** |
| **Expired** | `expireAt` in the past | none | «Подписка истекла» Title | «Срок закончился 31 мая 2026» Subtitle | line 1 `?attr/pingBad`; line 2 onSurfaceVariant | **Primary** «Продлить · 450 ₽» |
| **Trial** | `selectedSub.isTrial` | none | per the row above that matches its expiry | same | same | **Primary** «Купить тариф» |
| **Unknown** | `expireAtIso` null and not perpetual-by-blank | none | «Срок неизвестен» Title | «Обновите позже или проверьте подключение» Subtitle | onSurfaceVariant both | Secondary, or hidden if no price |

Note that «Истекает сегодня» is a `urgent == true` state but has **no** Display figure: `0` as a
34sp number reads as "nothing left", which is wrong while access is still live. In that one state
the balance keeps the Display slot, so the "exactly one Display" count still holds.

Spelled out, the three the reviewer will check:

- **At 30 days:** no coloured pixel anywhere. The card reads «Активна до 3 августа 2026» in 16/700
  white over «Осталось 30 дней» in 13/400 grey. The CTA is a neutral Secondary «Продлить · 450 ₽».
  The screen is calm because the account is calm, and the balance is the big number.
- **At 3 days:** the card's first line becomes `3` at 34sp in the figure face, amber, with «дня» at
  16/700 amber beside it, baseline-aligned; the date drops to line 2 in grey. The CTA becomes the
  filled accent «Продлить · 450 ₽». The head's balance shrinks to 16/700. Exactly one thing on the
  screen is blue and exactly one is amber.
- **Expired:** line 1 «Подписка истекла» in `#FF6069`, line 2 «Срок закончился 31 мая 2026» in grey,
  the **traffic block is hidden** (its numbers are stale and meaningless), «Улучшить тариф» is hidden
  (a dead subscription cannot be upgraded), the auto-renew row keeps its switch, and the CTA is the
  filled «Продлить · 450 ₽».

### 4.4 Russian plurals

Three forms. Android uses `<plurals>`; there is no excuse for `"дней"` hard-coded.

```
form(n): a = n % 100, b = n % 10
  11 <= a <= 14   -> other   (дней, устройств, платежей)
  b == 1          -> one     (день, устройство, платёж)
  2 <= b <= 4     -> few     (дня, устройства, платежа)
  otherwise       -> other
```

Required sets in `res/values/strings_account.xml`:

```xml
<plurals name="account_days">
    <item quantity="one">%d день</item>
    <item quantity="few">%d дня</item>
    <item quantity="other">%d дней</item>
</plurals>
<plurals name="account_days_unit">     <!-- the unit alone, for the Display pair -->
    <item quantity="one">день</item>
    <item quantity="few">дня</item>
    <item quantity="other">дней</item>
</plurals>
<plurals name="devices_count">
    <item quantity="one">%d устройство</item>
    <item quantity="few">%d устройства</item>
    <item quantity="other">%d устройств</item>
</plurals>
```

### 4.5 Traffic and device counts

**Traffic is root-only** (AS §4.2): the raw remnawave record arrives on `GET /client/subscription`
and never on `/all`. The traffic block renders **only** when `selectedSub.subscription?.raw()` is
non-null **and** `trafficLimitBytes != null`. On a secondary subscription and on an unlimited plan
the block is **absent** - not an empty meter, not a «безлимит» line. Its absence is the statement.

`object Bytes` in `com/v2ray/ang/util/Bytes.kt`:

| Input | Output |
|---|---|
| `< 1024 КБ` | `«%d КБ»` |
| `< 1024 МБ` | `«%d МБ»` |
| else | `«%,.1f ГБ»` / `«%,.1f ТБ»`, **comma** decimal |

Figure face, `tnum, lnum, zero` (traffic is a technical figure, so the slashed zero is correct
there). The value line reads **«12,4 из 100 ГБ»**: the unit appears once, at the end, in the UI
face; the two figures are in the figure face. At 100 % the value line becomes **«Лимит исчерпан»** in
`?attr/pingBad` and the meter fill switches to `?attr/colorError`.

**Device counts.** The live count is `GET /client/devices`.length and exists **only for the active
subscription**. `connectedDevices` from `/all` is **always 0** and is never rendered. `deviceCount`
from `/all` is **extra purchased devices**, not usage.

| Case | Row value | Row subtitle |
|---|---|---|
| Active sub, limited | `2 / 5` figure face | none |
| Active sub, unlimited (`hwidDeviceLimit <= 0`) | `2` | «Без ограничений» |
| Secondary sub, limited | `5` | «Слотов на подписке» |
| Secondary sub, unlimited | none | «Без ограничений» |
| Count not yet loaded | a 20dp inline indeterminate indicator in the trailing slot | none |

**No `∞` glyph anywhere.** It is not guaranteed present in the vendored font file, and «Без
ограничений» is a word, which §6.3 prefers. `account_unlimited` («∞») is deleted.

---

## 5. The head

### 5.1 Header (`layout_account_header.xml`, 56dp)

```
FrameLayout  android:layout_height="@dimen/toolbar_height"        (56)
             android:background="?attr/colorBackground"
             elevation 0, no divider at rest
  TextView   id=tv_header_title
             layout_marginStart="@dimen/screen_gutter"            (16)
             layout_gravity="center_vertical"
             textAppearance="@style/TextAppearance.App.Title"     (16sp/700)
             text="@string/account_tab_title"                     («Аккаунт»)
  View       id=header_hairline
             layout_height="1dp"  layout_gravity="bottom"
             background="?attr/colorOutlineVariant"
             alpha="0"                                            → 1 over motion_state 220 when scrollY > 0
```

No trailing action. No wordmark (`11-app-structure.md` §3.3: the wordmark lives in exactly three
places and this is not one of them). No accent anywhere in the header.

### 5.2 Identity + balance (`layout_account_head.xml`)

```
LinearLayout  vertical, id=head

  ── identity row ──────────────────────────────────────────────────────
  LinearLayout  horizontal, gravity=center_vertical
                minHeight="@dimen/row_min_height"                  (56)
    FrameLayout  id=avatar_container
                 40×40 (@dimen/tile_size)
                 background="@drawable/bg_icon_neutral"            (r12, #20242B)
                 foreground="?attr/selectableItemBackground"
                 contentDescription="@string/account_change_avatar"
                 → wrapped in a 48dp TouchDelegate (§7.2)
      TextView    id=tv_avatar_initial   match_parent, gravity=center
                  textAppearance="@style/TextAppearance.App.Title"  (16sp/700)
                  textColor="?attr/colorOnSurfaceVariant"
      ShapeableImageView id=img_avatar   match_parent, scaleType=centerCrop
                  shapeAppearanceOverlay cornerSize="@dimen/radius_tile"
                  visibility="gone"
      ImageView   id=img_avatar_person   22dp, gravity=center
                  src="@drawable/ic_acc_person"
                  tint="?attr/colorOnSurfaceVariant"  visibility="gone"
    LinearLayout  vertical, layout_marginStart="@dimen/space_12", weight=1
      TextView    id=tv_name    textAppearance="@style/TextAppearance.App.Title"
                                maxLines="2" ellipsize="end"
      TextView    id=tv_handle  textAppearance="@style/TextAppearance.App.Subtitle"
                                layout_marginTop="@dimen/space_4"
                                maxLines="1" ellipsize="end"

  ── balance block ─────────────────────────────────────────────────────
  TextView   id=tv_balance_label
             layout_marginTop="@dimen/space_24"
             textAppearance="@style/TextAppearance.App.Caption"
             text="@string/account_balance_label"                 («Баланс»)
  LinearLayout  horizontal, id=block_balance
             layout_marginTop="@dimen/space_4"
             minHeight="@dimen/btn_height_compact"                (48 - the block never changes height)
             gravity="bottom"
    TextView   id=tv_balance         textAppearance="@style/TextAppearance.App.Display"
                                     fontFeatureSettings="tnum, lnum"
                                     maxLines="1"
    TextView   id=tv_balance_symbol  layout_marginStart="@dimen/space_4"
                                     layout_marginBottom="@dimen/space_4"
                                     textAppearance="@style/TextAppearance.App.Headline"
                                     textColor="?attr/colorOnSurfaceVariant"
                                     text="₽"
    Space      weight=1
    MaterialButton id=btn_top_up
                   style="@style/Widget.Departament.Button.Secondary"
                   layout_height="wrap_content"
                   minHeight="@dimen/btn_height_compact"          (48)
                   insetTop="0dp" insetBottom="0dp"
                   cornerRadius="@dimen/radius_control"           (16)
                   textAppearance="@style/TextAppearance.App.Title.Medium"
                   text="@string/account_top_up"                  («Пополнить»)
```

Below the head: `layout_marginTop="@dimen/space_32"` before the switcher or the card (§4.2, "above
the first section after a hero: 32").

**Rules that are not optional:**

- **The head is not a card.** It sits on `?attr/colorBackground`. The subscription card is the only
  card on the tab (§4.4). This removes AS §1.4.5's "two identical boxes, no hierarchy".
- **No camera badge.** The 18dp `bg_avatar_edit` oval floating on the avatar is deleted. The tile
  itself is the affordance, with `contentDescription` «Сменить фото» and the existing options
  dialog.
- **Monogram** = the first grapheme of the resolved name, upper-cased. When no name resolves, the
  tile shows `ic_acc_person` and no letter. The literal `android:text="?"` in
  `activity_account.xml:66` is deleted.
- **Name precedence, unified across the tab and the Home chip** (they disagree today - AS §1.9):
  `telegramName` → `@telegramUsername` → `email` → «Аккаунт». `MainActivity.bindAccountChip()` is
  changed to match; one chain, one product.
- **Handle line** = `@telegramUsername` when linked; else `email`; else «Telegram не привязан».
- **Balance count-up** is preserved from `AccountFragment.animateMoney()`, retimed to
  `@integer/motion_reveal` 300 ms with `@interpolator/ease_out_quint` (it uses `ease_out_quart`
  today, which is the press curve). It fires **only on a changed value**, never on first paint, and
  lands instantly under reduced motion. Whole-ruble balances count on integers so the tabular figure
  does not flicker decimals mid-flight - that behaviour is correct today and is kept.
- **«Пополнить» is Secondary, not Primary.** Topping up is not the tab's primary action in any
  state; the accent belongs to the CTA that changes the subscription. This removes AS §1.4.6's "two
  filled accent buttons on the empty state".

---

## 6. The subscription switcher

Both carousels are deleted: the Android `ViewPager2` + `SubscriptionPagerAdapter`, and the desktop's
hand-rolled drag/snap over a `ScrollViewer` with tunnel pointer handlers and a 16 ms timer tween.
§1.3 bans reinventing standard affordances, and §11.2 already names the right controls.

| Count | Surface | Component |
|---|---|---|
| **0** | the empty card, no switcher | - |
| **1** | the card, full width, nothing above it | - |
| **2-3** | **segmented control**, 12 above the card | `MaterialButtonToggleGroup` |
| **4+** | **Row.Value** «Подписка» + the selected name, 12 above the card, opening a radio sheet | `layout_row_value.xml` → `sheet_subscription_pick.xml` |

### 6.1 Segmented control (`layout_account_switcher.xml`)

Geometry from `10-design-system.md` §3 (`seg_*` component tokens):

```
com.google.android.material.button.MaterialButtonToggleGroup
    id=group_subs
    layout_width="match_parent"
    layout_height="@dimen/btn_height_compact"          (48 - seg_track_height)
    background="@drawable/bg_segment_track"            (solid ?attr/colorSurfaceContainerHighest, r16)
    padding="@dimen/space_4"                           (4 - seg_thumb_inset)
    app:singleSelection="true"
    app:selectionRequired="true"
    app:innerCornerSize="@dimen/radius_chip"           (12 - seg_thumb_radius)
    layout_marginBottom="@dimen/space_12"
  └ MaterialButton  style="@style/Widget.Departament.Segment"   ×2 or ×3
       layout_width="0dp" layout_weight="1" layout_height="40dp"
       insetTop="0dp" insetBottom="0dp"
       cornerRadius="@dimen/radius_chip"               (12)
       minWidth="64dp"  paddingHorizontal="@dimen/space_12"
       textAppearance="@style/TextAppearance.App.Title.Medium"
       maxLines="1" ellipsize="end"
```

| State | Segment fill | Label |
|---|---|---|
| Unselected | transparent | `?attr/colorOnSurfaceVariant` `#9BA1AD`, weight **500** |
| Unselected + pressed | `?attr/colorPrimary` @ 10 % ripple | unchanged |
| **Selected** | `?attr/colorPrimaryContainer` `#17325C` | `?attr/colorOnPrimaryContainer` `#CFE0FF` (**9.57:1**), weight **700** |
| Focused | unchanged | + outer 2dp `?attr/colorPrimary` ring at 2dp offset, radius 14 |
| Disabled (offline) | track and segments @ 0.38 | @ 0.38 |

Selection reads on **three** axes (fill, colour, weight), so it survives the mono theme and
colour-blindness. The fill crossfades over `@integer/motion_state` 220 ms `@interpolator/ease_standard`;
the weight **snaps** (a weight tween is not available and not wanted). **The fill does not slide** -
a sliding thumb needs a shared layer and is a per-item animation the stagger rules do not cover.

Weight is swapped by `setTextAppearance` on the check listener, exactly as the bottom navigation
already does (`MainActivity.kt:333`).

**Segment label** = the subscription's display name, `maxLines=1`, ellipsize end. At three segments
on a 320dp screen each segment is (320 − 32 gutter − 8 padding − 8 gaps) / 3 ≈ 90dp, which fits
«Подписка 2» at 16sp; a longer user rename ellipsises, which is acceptable for a **switch** label
because the card immediately below prints the full name.

### 6.2 The 4+ case

`layout_row_value.xml` with tile `ic_acc_upgrade`, title «Подписка», value = the selected
subscription's name, `layout_marginBottom="@dimen/space_12"`, opening
`sheet_subscription_pick.xml` (section 10.5).

### 6.3 Selection scope - the trap

The selected index lives in `AccountViewModel`, survives configuration changes and tab switches,
resets to the root subscription on a cold start, and is restored by Back (§7.7).

**Every subscription-scoped action passes the selected subscription's own scope**, never a cached
root uuid: `selectedSub.type` (`"root"` / `"secondary"`) as `{scope}`, `selectedSub.id` as `{id}`,
and `selectedSub.remnawaveUuid` for the device and QR endpoints. This is the exact bug the sibling
web repo's `CLAUDE.md` warns about ("the backend used to silently default to a stale root uuid"),
and it is the easiest thing in this spec to reintroduce.

---

## 7. The subscription card

`res/layout/layout_account_card.xml`. This replaces `item_subscription_card.xml` (fixed 152dp,
three lines, no state) and is the **only** card on the tab.

### 7.1 Tree

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/card_sub"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="@dimen/radius_card"          <!-- 20 -->
    app:cardElevation="0dp"
    app:strokeWidth="1dp"
    app:strokeColor="?attr/colorOutlineVariant">

  <LinearLayout android:orientation="vertical"
      android:paddingStart="@dimen/space_16" android:paddingTop="@dimen/space_16"
      android:paddingEnd="@dimen/space_16" android:paddingBottom="0dp">

    <!-- 1. HEADER -->
    <LinearLayout android:orientation="horizontal" android:gravity="center_vertical"
                  android:minHeight="@dimen/view_height_dp48">
      <TextView android:id="@+id/tv_sub_name"
                android:layout_width="0dp" android:layout_weight="1"
                android:textAppearance="@style/TextAppearance.App.Title"
                android:maxLines="1" android:ellipsize="end"/>
      <com.google.android.material.chip.Chip android:id="@+id/chip_tariff"
                style="@style/Widget.Departament.Chip.Accent"
                android:layout_marginStart="@dimen/space_8"
                android:clickable="false"
                android:visibility="gone"/>
      <ImageButton android:id="@+id/btn_rename"
                android:layout_width="@dimen/view_height_dp48"
                android:layout_height="@dimen/view_height_dp48"
                android:layout_marginStart="@dimen/space_4"
                android:padding="14dp"                 <!-- 48 box, 20dp glyph -->
                android:src="@drawable/ic_acc_edit"
                app:tint="?attr/colorOnSurfaceVariant"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/account_card_rename"/>
    </LinearLayout>

    <!-- 2. TIME BLOCK -->
    <LinearLayout android:id="@+id/block_days" android:orientation="horizontal"
                  android:baselineAligned="true"
                  android:layout_marginTop="@dimen/space_12"
                  android:visibility="gone"
                  android:importantForAccessibility="yes">
      <TextView android:id="@+id/tv_days"
                android:textAppearance="@style/TextAppearance.App.Display"
                android:fontFeatureSettings="tnum, lnum"
                android:importantForAccessibility="no"/>
      <TextView android:id="@+id/tv_days_unit"
                android:layout_marginStart="@dimen/space_8"
                android:textAppearance="@style/TextAppearance.App.Title"
                android:maxLines="2"
                android:importantForAccessibility="no"/>
    </LinearLayout>
    <TextView android:id="@+id/tv_time_title"
              android:layout_marginTop="@dimen/space_12"
              android:textAppearance="@style/TextAppearance.App.Title"/>
    <TextView android:id="@+id/tv_time_detail"
              android:layout_marginTop="@dimen/space_4"
              android:textAppearance="@style/TextAppearance.App.Subtitle"/>

    <!-- 3. TRAFFIC - gone unless root && trafficLimitBytes != null && health != Expired -->
    <LinearLayout android:id="@+id/block_traffic" android:orientation="vertical"
                  android:layout_marginTop="@dimen/space_16" android:visibility="gone">
      <LinearLayout android:orientation="horizontal">
        <TextView android:layout_width="0dp" android:layout_weight="1"
                  android:text="@string/account_card_traffic_label"
                  android:textAppearance="@style/TextAppearance.App.Subtitle"/>
        <TextView android:id="@+id/tv_traffic_value"
                  android:textAppearance="@style/TextAppearance.App.Subtitle"
                  android:textColor="?attr/colorOnSurface"
                  android:fontFeatureSettings="tnum, lnum, zero"/>
      </LinearLayout>
      <com.google.android.material.progressindicator.LinearProgressIndicator
                  android:id="@+id/meter_traffic"
                  style="@style/Widget.Departament.Progress.Linear"
                  android:layout_width="match_parent"
                  android:layout_marginTop="@dimen/space_8"
                  app:trackThickness="@dimen/meter_height"        <!-- 6 -->
                  app:trackCornerRadius="3dp"
                  app:trackColor="?attr/colorSurfaceContainerHighest"
                  app:indicatorColor="?attr/colorPrimary"
                  android:max="1000"/>
    </LinearLayout>

    <!-- 4. CTA -->
    <com.google.android.material.button.MaterialButton android:id="@+id/btn_cta"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="@dimen/btn_height"                     <!-- 52 -->
        android:layout_marginTop="@dimen/space_16"
        android:insetTop="0dp" android:insetBottom="0dp"
        app:cornerRadius="@dimen/radius_control"                  <!-- 16 -->
        android:textAppearance="@style/TextAppearance.App.Title"/>

    <!-- 5. AUTO-RENEW -->
    <View android:id="@+id/div_auto" android:layout_width="match_parent"
          android:layout_height="1dp" android:layout_marginTop="@dimen/space_16"
          android:background="?attr/colorOutlineVariant"/>
    <include layout="@layout/layout_row_switch" android:id="@+id/row_auto_renew"/>
    <!-- with paddingStart/End = 0dp (the card already pads 16), paddingBottom = space_16,
         and NO tile: on this surface the switch row's text origin is the card's inner 16 -->
  </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### 7.2 Card facts

- **Height is `wrap_content`.** `@dimen/sub_card_height` 152dp is deleted from `dimens.xml`. Three
  lines in a fixed 152dp box is why no state could ever be added to this card (AS §1.4.7).
- **The tariff badge is the one `Chip.Accent` in the product**: `?attr/colorPrimaryContainer`
  `#17325C` fill, `?attr/colorOnPrimaryContainer` `#CFE0FF` label at 9.57:1,
  `TextAppearance.App.Chip` 11sp/500, height 24, padding 8 × 4, radius 12,
  `android:clickable="false"` (a chip is a **label**, never a control). It is hidden when nothing
  resolves. This is owner request §0.4.5 and `22-components.md` R14 item 7.
  Badge precedence, unchanged from `AccountFragment.setupPager()`: catalogue by `tariffId` →
  catalogue by `tariffPriceOptionId` → `sub.tariffBadgeName()` (which filters the generic
  «departament vpn») → hidden. **Never guess a tariff name**; a wrong badge is worse than none.
  On a trial subscription the badge is `Chip.Neutral` reading «Пробный».
  `bg_acc_badge.xml` (currently `?attr/iconTileBgBlue`) is retired with the rest of the drawable
  chips.
- **The card carries no status chip.** Health is the typography (section 4.3). A chip saying
  «Истекает» beside a line saying «Осталось 5 дней» is §2.4.4's "a chip repeating what the title
  says".
- **The traffic meter** is `LinearProgressIndicator` at `progress = round(used / total × 1000)`,
  animated over `@integer/motion_state` 220 ms `ease_standard` on a value change (`setProgressCompat(p, true)`).
  Fill is **solid** `?attr/colorPrimary`; there is no gradient (§6.5 - the desktop's
  `TrafficFillBrush` `LinearGradientBrush` is deleted in the same change). At `used >= total` the
  `indicatorColor` becomes `?attr/colorError` and the value line becomes «Лимит исчерпан» in
  `?attr/pingBad`. The label is **beside** the bar, never printed on the fill (D§F11): an 11sp label
  over a moving accent fill measures 2.9:1.
- **The rename button is hidden when `selectedSub.id` is blank.** A primary-only account gets a
  synthesised root with **no id** (AS §4.2, `AccountViewModel.buildRootSub`), and
  `PATCH /client/subscription/{scope}/{id}/name` would 400.
- **Card press.** The card as a whole is **not** pressable (it is a container, not a target). Its
  three interactive children - rename, CTA, auto-renew row - carry their own feedback:
  `@anim/press_scale` (0.97) on the buttons, a background step on the row (`22-components.md` R5).

### 7.3 The CTA, exactly

| Health | Variant | Label | Action |
|---|---|---|---|
| Active / perpetual / unknown, price known | Secondary | «Продлить · 450 ₽» | payment sheet, subject «Продление {tariff}, {N} дней» |
| Active / perpetual / unknown, price unknown | Secondary | «Продлить» | `BuyTariffActivity`, scoped to this subscription |
| Expiring / expiring today / expired, price known | **Primary** | «Продлить · 450 ₽» | payment sheet |
| Expiring / expired, price unknown | **Primary** | «Выбрать тариф» | `BuyTariffActivity` |
| Trial | **Primary** | «Купить тариф» | `BuyTariffActivity` |
| Empty (no subscription) | **Primary** | «Купить» | `BuyTariffActivity` |
| Secondary subscription, card path, **PG-2 unresolved** | Secondary, **disabled** | «Продлить · 450 ₽» | subtitle line under the CTA: «Продление доступно на сайте» + Tertiary «Открыть сайт» |

Renewal price precedence: `renewalPrice` → `tariffPrice` → `autoRenewNextChargeAmount`. The last two
are **root-only** fields.

**Loading contract** (`22-components.md` R8, identical for every button on this tab): the label is
**hidden, not removed**, the width is **pinned** before the swap, a **20dp** arc spins in the
button's own foreground colour (`?attr/colorOnPrimary` inside a Primary, `?attr/colorOnSurface`
inside a Secondary), and the button is disabled while in flight. Loading is **not** the disabled
look: the container keeps full opacity.

**Double-press is impossible by construction** (R9): every action button on this tab is either bound
to a command that reports its own in-flight state, or wrapped in a `@integer/input_debounce` 500 ms
re-entry guard.

### 7.4 The auto-renew row

`layout_row_switch.xml`, included with `paddingStart="0dp" paddingEnd="0dp"`, no tile.

| Condition | Title | Subtitle |
|---|---|---|
| On, next charge known | «Автопродление» | «Спишем 450 ₽ 3 августа» |
| On, next charge unknown | «Автопродление» | «Продлим автоматически» |
| Off | «Автопродление» | «Продление вручную» |
| Off **and** expiring | «Автопродление» | «Без автопродления доступ прервётся 3 августа» in `?attr/warning` |
| Saving | «Автопродление» | «Сохраняем…», the row is not clickable, the switch shows the optimistic position |

- The row is **hidden entirely on a trial subscription** (a trial does not auto-renew).
- The switch is `android:clickable="false" android:focusable="false"
  android:importantForAccessibility="no"`; **the row owns the toggle**. This is not optional - it is
  what removes the double-hit-target defect (CS §C.6.32).
- The flag's source differs by scope: the **root**'s flag lives on the **profile**
  (`profile.autoRenewEnabled`), a **secondary**'s on the sub (`sub.autoRenewEnabled`). Two
  endpoints, two ViewModel methods, already present.
- **Not optimistic** (money-adjacent): the switch moves, the row goes non-clickable, the subtitle
  becomes «Сохраняем…». On failure the switch animates back over 220 ms and a Snackbar reads «Не
  удалось изменить автопродление. Повторите.» with «Повторить».
- **PG-3 must land first.** Without the path fix and the `enabled` wire key, this row will animate,
  report success and change nothing.

---

## 8. Row groups

### 8.1 Section header

`@style/SettingsSectionLabel`, corrected: `paddingStart/End="@dimen/space_16"`,
`paddingTop="@dimen/space_24"`, `paddingBottom="@dimen/space_8"` (its `paddingTop` is 18dp today,
off-scale). Sentence case, 16sp/700, `?attr/colorOnSurface`. **Never `textAllCaps`**, never an
ALL-CAPS tracked eyebrow (§1.1).

### 8.2 The three row layouts

One geometry, three trailings. These are new files; `layout_setting_row.xml` and
`layout_setting_toggle_row.xml` have **zero call sites** today (CS §A.4) and are deleted.

```
minHeight   = @dimen/row_min_height        (56)
paddingStart/End = @dimen/space_16         (16)
paddingTop/Bottom = @dimen/space_12        (12)
background  = @drawable/bg_row             (ripple over a selector; state_pressed = ?attr/colorSurfaceContainerHigh)
android:focusable="true"
                                            ← rows do NOT scale (22-components R5)
  [ FrameLayout 40×40, @drawable/bg_icon_neutral, radius_tile 12 ]
      [ ImageView 22dp (@dimen/tile_glyph), tint="?attr/colorOnSurfaceVariant" ]
  [ LinearLayout vertical, layout_marginStart="@dimen/space_12", layout_width=0dp, weight=1 ]
      [ TextView  title     @style/TextAppearance.App.Title      maxLines=2 ellipsize=end ]
      [ TextView  subtitle  @style/TextAppearance.App.Subtitle   layout_marginTop="@dimen/space_4"
                            maxLines=2  visibility=gone ]
  trailing, EXACTLY one, layout_marginStart="@dimen/space_12":
      layout_row_value.xml  → [ TextView value @style/TextAppearance.App.Subtitle
                                fontFeatureSettings="tnum, lnum" ]
      layout_row_nav.xml    → [ ImageView 20dp @drawable/ic_chevron_right
                                tint="?attr/colorOnSurfaceVariant" ]
      layout_row_switch.xml → [ MaterialSwitch clickable=false focusable=false
                                importantForAccessibility=no ]
```

Text origin = 16 + 40 + 12 = **68** (`@dimen/row_text_origin`). Divider between rows inside a group:
`<View layout_height="1dp" background="?attr/colorOutlineVariant"
layout_marginStart="@dimen/row_text_origin"/>`. **No divider above the first row of a group and none
below the last.** The current 72dp inset and the thirteen 18dp chevrons both die.

Group container: a `LinearLayout` on the ground plane. **Not a card** - the tab has one card and it
is the subscription (§4.4). Groups are separated by their section header's 24dp top padding.

### 8.3 The groups, in full

**«Подписка»** (hidden entirely when `subs.isEmpty()`)

| Row | Layout | Tile | Title | Trailing | Target |
|---|---|---|---|---|---|
| Устройства | `layout_row_value` | `ic_acc_devices` | «Устройства» | `2 / 5` | `DeviceManagementActivity` |
| Улучшить тариф | `layout_row_nav` | `ic_acc_upgrade` | «Улучшить тариф» | chevron | `sheet_upgrade` |

**«Оплата»**

| Row | Layout | Tile | Title | Trailing | Target |
|---|---|---|---|---|---|
| Купить подписку | `layout_row_nav` | `ic_acc_wallet` | «Купить подписку» | chevron | `BuyTariffActivity` |
| История платежей | `layout_row_value` | `ic_acc_history` | «История платежей» | `12.06.2026` | `PaymentHistoryActivity` |
| Реферальный код | `layout_row_value` | `ic_acc_gift` | «Реферальный код» | `ABC123` (figure face) | copies to clipboard + Snackbar «Код скопирован» |

**«Вход»**

| Row | Layout | Tile | Title | Trailing | Target |
|---|---|---|---|---|---|
| Способы входа | `layout_row_value` | `ic_acc_key` | «Способы входа» | «Telegram, почта» | `LinkingActivity` |
| *(or)* Привязать Telegram | `layout_row_nav` | `ic_acc_telegram` | «Привязать Telegram», subtitle «Управление подпиской из бота» | chevron | `LinkingActivity` |
| Выйти | `layout_row_nav` | `ic_acc_logout` | «Выйти» in `?attr/pingBad` | nothing | confirm dialog |

The «Выйти» row's **tile stays neutral**. A red tile plus red text is the same signal twice and
turns a row into an alarm (`22-components.md` §8.6). It opens a dialog rather than an undo snackbar
because there is genuinely no undo path: you cannot un-sign-out without credentials (§7.5).

The «Способы входа» value is built from the linked set, in this order, joined with «, »:
`Telegram` (when `telegramLinked`), `почта` (when `email` is non-blank), `Google` (when
`googleLinked` - PG-4). When none resolves, the row is the «Привязать Telegram» variant.

---

## 9. Every state

The state machine lives in one function, `renderState()`, and nothing else in the fragment may set a
visibility on a state container. Order matters: the first match wins.

| # | State | Trigger | Rendering |
|---|---|---|---|
| 1 | **Gate** | `!AccountSession.isLoggedIn()` | `layout_account_gate.xml` replaces the whole content; the header stays |
| 2 | **Skeleton** | `(pendingFirstLoad ‖ loading) && profile == null`, **after 300 ms** | `layout_account_skeleton.xml` |
| 3 | **Error, cold** | `profile == null && error != null` | head renders from the session's cached email if any, else the person tile + «Аккаунт»; the card slot shows the error card with the **mapped** cause; groups render with their values omitted (never a placeholder dash) |
| 4 | **Empty** | `subs.isEmpty()`, no error | head + empty card + «Оплата» and «Вход». The «Подписка» group is hidden |
| 5 | **Loaded** | otherwise | section 3 |

Orthogonal overlays, which may combine with 3-5:

| Overlay | Trigger | Rendering |
|---|---|---|
| **Offline** | no connectivity, or the last refresh failed with `ApiError.Network` | `layout_status_bar_inline.xml` pinned as the first child of `content` |
| **Payment polling** | `pendingPayment != null` | the same inline bar, different content. Offline wins if both apply |
| **Partial** | profile resolved, subs failed (or the reverse) | render what resolved; the failed half shows its own error card or omits its values; a **single** Tertiary «Повторить» in the failed half only |

### 9.1 Skeleton (`layout_account_skeleton.xml`)

The silhouette of the real content: same block count, same heights, same positions. The Buy screen's
three flat 76dp blocks are the counter-example - they do not match the tariff card and still read as
a pop when they swap.

```
head:      [40×40 block r12] [12] [bar 45 % width, 16dp]        ← identity row, minHeight 56
                                  [bar 30 % width, 16dp, marginTop 4]
           [bar 20 % width, 12dp, marginTop 24]                  ← «Баланс» caption
           [bar 35 % width, 34dp, marginTop 4]                   ← the Display figure
32
card:      [block, radius 20, stroke 1dp outlineVariant, padding 16]
             [bar 45 %, 16dp]                                    ← name
             [bar 55 %, 16dp, marginTop 12]                      ← time title
             [bar 35 %, 13dp, marginTop 4]                       ← time detail
             [bar 100 %, 52dp, radius 16, marginTop 16]          ← CTA
             [1dp divider, marginTop 16]
             [bar 40 %, 16dp, marginTop 12, marginBottom 16]     ← auto-renew
24
rows:      3 × [40×40 block r12][12][bar 55 %, 16dp]  minHeight 56
```

| Property | Value |
|---|---|
| Bar fill | `@color/skeleton` (`?attr/colorSurfaceContainerHighest` `#20242B`), radius `@dimen/radius_chip` 12 |
| Bar heights | 12 / 16 / 34, matching the type role each stands in for |
| Pulse | opacity **0.45 ↔ 1.0**, `@integer/motion_pulse` **1000 ms** each way, `@interpolator/ease_standard`, infinite reverse |
| Reduced motion | static at opacity **0.7**, no animator |
| Appears after | **300 ms** (§7.3) - a fast cache hit shows no skeleton at all |
| Swap to content | `@integer/motion_state` 220 ms crossfade, **no layout change** |

The 900 ms `AccelerateDecelerateInterpolator` in `AccountFragment.kt:413-430` is deleted: it exists
in no motion scale and its curve is banned (§8.3, ease-out only).

### 9.2 Empty card (`layout_account_card_empty.xml`)

One empty-state grammar for the whole product (`22-components.md` §15). Android has three today.

```
[ 64dp tile, @dimen/empty_icon, radius_card 20, ?attr/colorSurfaceContainerHighest ]
   [ 32dp glyph ic_acc_upgrade, tint ?attr/colorOnSurfaceVariant ]
16
Заголовок    @style/TextAppearance.App.Headline   24/700, centred, maxLines 2
8
Строка       @style/TextAppearance.App.Subtitle    13/400, centred, maxWidth ≈ 60ch, maxLines 2
24
[ Действие ]
```

| Slot | Copy |
|---|---|
| Title | «Подписки пока нет» |
| Line | «Купите тариф, чтобы подключаться к серверам Departament.» |
| Action | **Primary** 52dp «Купить» → `BuyTariffActivity` |
| Second action | **Tertiary** 48dp «Начать пробный период», 12 below, **only when** `publicConfig.trialEnabled && !profile.trialUsed` |

The tile is **neutral**. An empty state is not the screen's primary action surface and does not spend
the accent on decoration.

This replaces «Оформите первую подписку» / «Оформите её, чтобы подключиться». §9.3 locks buying to
«Купить»; «Оформить» is not a word this product uses.

### 9.3 Error card (`layout_account_card_error.xml`)

Same silhouette, alert glyph, and **the mapped cause** - never a hard-wired string.
`activity_account.xml` hard-codes «Что-то пошло не так» in XML while `messageFor()` already maps
five real causes; that is the defect.

```
[ 64dp neutral tile + 32dp ic_acc_alert ]
Заголовок  «Не удалось загрузить аккаунт»           Headline 24/700
Строка     ← messageFor(error), section 15.6         Subtitle 13/400
[ Повторить ]   Tertiary 48dp
```

The retry is **Tertiary**, not Primary: an error state must not spend the screen's one filled accent
on recovering from a failure.

`ApiError.Unauthorized` is **not an error state** - it clears the session and renders the gate.

### 9.4 Offline bar (`layout_status_bar_inline.xml`)

Offline is a designed state, not an error toast (§9.6).

```
LinearLayout  horizontal, gravity=center_vertical
              layout_height="@dimen/view_height_dp48"          (48)
              background="@drawable/bg_status_bar"             (?attr/colorSurfaceContainerHigh, radius_chip 12)
              paddingHorizontal="@dimen/space_12"
              layout_marginBottom="@dimen/space_16"
  ImageView   20dp  id=img_status  tint="?attr/colorOnSurfaceVariant"
  TextView    id=tv_status  layout_marginStart="@dimen/space_8" weight=1
              textAppearance="@style/TextAppearance.App.Body"
  MaterialButton id=btn_status_action  style=Tertiary  minHeight="@dimen/view_height_dp48"
```

| Variant | Glyph | Text | Action |
|---|---|---|---|
| Offline | `ic_acc_cloud_off` | «Нет сети. Показаны последние данные.» | «Повторить» |
| Polling | 20dp indeterminate arc | «Проверяем оплату…» | «Обновить» |
| Payment confirmed | `ic_action_done` in `?attr/colorTertiary` | «Оплата прошла» | none; auto-hides after 2 s |
| Payment unconfirmed (poll exhausted) | `ic_acc_alert` | «Не удалось подтвердить оплату. Проверьте историю платежей.» | «История» |

**Offline keeps the last known data on screen.** Every network-dependent control goes to alpha 0.38
and stops responding: the card CTA, the rename button, the auto-renew row, «Пополнить», «Купить
подписку», «Улучшить тариф», the switcher, «Способы входа». **«Выйти» stays enabled** - it is local.
Copying the referral code stays enabled - it is local.

### 9.5 The gate (`layout_account_gate.xml`)

**Signed out, the Account destination stays and renders a gate.** This is decision A-3 and it
reverses today's Android behaviour, where `MainActivity.updateAccountGate()` (`MainActivity.kt:1048-1064`)
removes `nav_account` from the bottom bar and **force-selects Home if the user is standing on the
tab when the session drops** - §7.7's "never traps the user", violated.

Reasons, in order: a navigation bar whose destination set changes at runtime is a defect (§7.7 fixes
3-5 destinations, §13 requires the set and order to be identical across platforms); sign-in needs a
home; and it matches the desktop, so §13 is satisfied without changing the desktop.

**The one exception is build-time, not runtime:** when `BackendConfig.isConfigured()` is false the
destination is removed at start-up and never reappears. A build without a backend has no account; a
*session* without a token has a gate.

Copy and geometry are `11-app-structure.md` §4.3.1, reproduced so this file is self-sufficient:

```
[ header 56: «Аккаунт» ]
32
«Войти в аккаунт»                                     Headline 24/700, at the gutter
«Подписка, устройства и платежи хранятся в аккаунте»  Body 14/400, onSurfaceVariant, maxWidth 60ch
24
[  Войти через Telegram  ]        Primary,  match_parent, minHeight 52, radius 16
12
[  Войти по почте  ]              Tertiary, match_parent, minHeight 48
12
[  Создать аккаунт  ]             Tertiary, match_parent, minHeight 48 → opens siteUrl
[ error line ]                    Caption 12/400, ?attr/pingBad, present in the markup even when empty
```

**No card. No illustration. No shield tile. No wordmark.** Ground plane, edge to edge. This is owner
request §0.4.10 («сейчас все выглядит плохо») taken literally: the sign-in surface is the screen, not
an object floating on it.

Awaiting state, inline, replacing the CTA **without changing its height**: a 20dp indeterminate
indicator, «Ждём подтверждения в Telegram», then two Tertiary buttons «Открыть Telegram» and
«Начать заново».

### 9.6 Long, short, and scaled content

| Case | Requirement |
|---|---|
| 60-character Telegram name | `tv_name` wraps to 2 lines then ellipsises; the avatar and the block height do not move |
| 40-character subscription name | `tv_sub_name` is 1 line ellipsised; the badge and the rename button are fixed-width and never pushed off |
| 12-digit balance | `tv_balance` is `wrap_content`; «Пополнить» is pushed right by the `Space` and the block never wraps; at the extreme the button drops to a second line rather than clipping |
| One subscription | no switcher, no dots, no "1 из 1". The card is simply the card |
| One device, one payment | the list renders one row with no group header for a single month |
| **Font scale 200 %** | every row is `wrap_content` + `minHeight`; every button is `wrap_content` + `minHeight`; the Display figure and its unit are a baseline-aligned `LinearLayout` with `maxLines="2"` on the unit so «дней» drops to a second line rather than clipping |
| `sw600dp` | `screen_gutter` becomes 24 via `values-sw600dp/dimens.xml`; `content` gains `android:maxWidth="@dimen/content_max_width"` (720) with `layout_gravity="center_horizontal"`. Nothing else changes |

---

## 10. Sheets and dialogs

Order of preference is law (§7.6): **inline > expandable row > bottom sheet > dialog.**

### 10.1 The payment component - one component, five callers

This is the fix for the #1 both-platform defect (AS §5.1): the payment-method decision currently
uses **two different components** - inline `Tonal`+`Primary` button pairs on the desktop card, a
bottom-sheet chevron list on Buy. §1.3's product ban is explicit: "if the save button looks
different in two places, one is wrong".

The rule that decides the container:

> **The payment surface is a bottom sheet when the flow has at most one input. It is a sub-page when
> the flow has a stepper whose value changes the price.** The rows inside are the same component
> either way.

`res/layout/sheet_payment.xml`, a `BottomSheetDialogFragment`:

```
background="@drawable/bg_sheet_top"          (radius_sheet 24 top only, ?attr/colorSurface)
  View      36×4 @drawable/bg_sheet_handle   (?attr/colorOutline, radius_pill)
            layout_gravity=center_horizontal  layout_marginTop="@dimen/space_12"
  TextView  @style/TextAppearance.App.Title  «Оплата»
            paddingHorizontal="@dimen/space_16"  layout_marginTop="@dimen/space_12"
  TextView  id=tv_sheet_subject  @style/TextAppearance.App.Subtitle
            paddingHorizontal="@dimen/space_16"  layout_marginTop="@dimen/space_4"
            ← WHAT IS BEING BOUGHT, restated. This closes the "no purchase summary"
              hole both platforms have today (AS §1.5, §2.4).
  [ space_16 ]
  include layout_ledger_row  id=row_total   «Итого» + value 16/700 numeric ?attr/colorOnSurface
  View 1dp ?attr/colorOutlineVariant  layout_marginStart/End="@dimen/space_16"
  [ runtime method rows: include layout_ledger_row × N ]
  paddingBottom = @dimen/space_16 + navigationBars inset
```

`layout_ledger_row.xml` is the **tile-less** row: `minHeight="@dimen/row_min_height"` 56,
`paddingHorizontal="@dimen/space_16"`, `paddingVertical="@dimen/space_12"`, title
`TextAppearance.App.Title` weight 1, optional subtitle, trailing value
`TextAppearance.App.Subtitle` numeric. **No tile. No chevron.**

- **The chevron is deleted.** Today's `item_payment_method.xml` puts a 20dp chevron on a row that
  charges money immediately; the glyph promises "goes further" and lies (AS §1.8).
- **The green balance tile is deleted.** Green is a status colour (§1.4.1), not a differentiator.
- **Method rows are verbs**: «Оплатить с баланса» (trailing value «На балансе 1 500 ₽»), «Оплатить
  картой», «Оплатить через СБП», «Оплатить через {label}» for any other `plategaMethods` entry. SBP
  keeps the existing `"sbp"` / `"СБП"` match on id or label.
- **Insufficient balance:** the balance row is disabled at 0.38 and its subtitle reads «Не хватает
  200 ₽».
- **Top-up never shows the balance row** - paying for balance from balance is circular. The existing
  `AccountFragment.kt:535-559` behaviour is correct and is preserved.
- **In flight:** the tapped row's trailing area shows a 20dp indeterminate indicator; every other row
  goes to 0.38 and stops responding; the sheet **cannot be dismissed by swipe** while a balance
  payment is in flight. A card payment hands off to the browser and the sheet closes.
- **Estimate rows.** When the amount is a client-side estimate (add-devices only) the total row reads
  «Примерно 150 ₽» with the subtitle «Точную сумму посчитаем при оплате». Every other caller shows an
  exact figure, and **the charged amount is the displayed figure**.
- **No methods available** (`plategaMethods` empty): the sheet does not open. A Snackbar reads
  «Способы оплаты недоступны. Повторите позже.»

| Caller | `tv_sheet_subject` | Amount source | Request |
|---|---|---|---|
| Renew from the card | «Продление {tariff}, {N} дней» | `renewalPrice` → `tariffPrice` → `autoRenewNextChargeAmount` | `payBalance` / `payTariffPlatega` (PG-2) |
| Upgrade confirm | «Улучшение до {tariff}, +{N} дней» | `GET /client/subscriptions/upgrade-quote` (**exact**) | `upgrade(targetTariffId, method, paymentMethod, subscriptionUuid)` |
| Add devices | «{N} устройства к подписке «{name}»» | client estimate (**«Примерно»**) | `addDevices(scope, id, extraDevices, method, paymentMethod)` |
| Top-up | «Пополнение баланса» | the amount the user typed | `payPlatega(amount, currency)` |
| Buy (catalogue) | «{tariff}, {N} дней» | `currentTotal(tariff, option)` | `payBalance` / `payPlatega` |

### 10.2 Top-up sheet (`sheet_top_up.xml`)

`dialog_top_up.xml` is deleted. It is placeholder-as-label (§7.4 forbids it), has no helper slot, no
inline error, and its buttons are the system «OK» / «Отмена» (§9.2: buttons are verbs, never «OK»).

```
TextView  @style/TextAppearance.App.Title      «Пополнение баланса»
          paddingHorizontal="@dimen/space_16"  layout_marginTop="@dimen/space_12"
TextView  @style/TextAppearance.App.Subtitle   «Сумма»       ← the LABEL, above, always visible
          layout_marginTop="@dimen/space_16"
TextInputLayout  style="@style/Widget.Departament.TextField"
                 boxCornerRadius* = @dimen/radius_control     (16)
                 minHeight="@dimen/field_height"              (52)
                 app:suffixText="₽"
                 android:hint="@null"                          ← never placeholder-as-label
  TextInputEditText  inputType="numberDecimal"  imeOptions="actionDone"
                     textAppearance="@style/TextAppearance.App.Title"
                     fontFeatureSettings="tnum, lnum"
TextView  id=tv_amount_error  @style/TextAppearance.App.Caption
          textColor="?attr/pingBad"  minHeight="16dp"
          ← the helper slot is ALWAYS in the tree so the layout never jumps (§7.4)
[ method rows, disabled at 0.38 until the amount is valid ]
```

Validation on **blur** and on submit, never per keystroke (§7.4):

| Input | Error |
|---|---|
| empty | «Введите сумму» |
| `<= 0` | «Сумма должна быть больше 0» |
| unparseable | «Введите сумму цифрами» |

On a failed submit, focus returns to the field.

### 10.3 Upgrade sheet (`sheet_upgrade.xml`)

The desktop runs this as a **four-panel wizard inside a kebab flyout on a card the user may have to
drag to** (AS §2.3.1). That is four levels of containment for a purchase. Here it is two taps.

```
TextView  @style/TextAppearance.App.Title     «Улучшить тариф»
TextView  @style/TextAppearance.App.Subtitle  «Доплата рассчитывается за оставшийся срок»
[ one layout_ledger_row per target ]
    title = tariff name (App.Title)
    trailing value = the catalogue price (App.Subtitle numeric)
```

Targets = `GET /public/tariffs` minus the current tariff. Tapping a target shows a **16dp**
indeterminate indicator in that row's trailing slot while `GET /client/subscriptions/upgrade-quote`
runs, then **pushes the payment sheet** with the exact quote. On quote failure the row shows an
inline error «Не удалось получить сумму. Повторите.» and stays open.

Two steps, one payment component. No wizard.

### 10.4 Rename dialog (`dialog_rename_subscription.xml`)

A themed `MaterialAlertDialog` inheriting `ThemeOverlay.Departament.Dialog` (already wired via three
theme attrs).

| Slot | Content |
|---|---|
| Title | «Название подписки» |
| Body | label «Название» (Subtitle) above an `OutlinedBox` field with `hint=@null`, helper slot below |
| Buttons | Tertiary «Отмена» then Primary «Сохранить», right-aligned, 8 apart |

Validation: 1-40 characters after trimming; empty → «Введите название».

Rename is **optimistic** (it is not money): the card title updates immediately, reverts on failure
with a Snackbar «Не удалось переименовать. Повторите.» + «Повторить».

### 10.5 Subscription picker (`sheet_subscription_pick.xml`)

Title «Выберите подписку», then a **virtualised** `RecyclerView` (§4.6) of `layout_ledger_row` with a
leading 20dp `MaterialRadioButton`:

- title = the subscription name
- subtitle = short expiry: «до 03.08.2026» / «истекла 31.05.2026» / «бессрочно»
- selected row: radio accent **and** title weight 700 (two axes, §7.1)

### 10.6 QR sheet (`sheet_qr.xml`)

```
TextView  Title      «QR-код подписки»
TextView  Subtitle   «Отсканируйте в приложении на другом устройстве»
[ 240dp bitmap from repo.getQr(remnawaveUuid), centred, on a WHITE radius_card plate ]
[ Secondary 48dp «Скопировать ссылку» ]
```

The white plate is the **one** white surface in the product and it is a **functional** requirement,
not decoration: a QR code needs a light quiet zone to scan. It is exempt from the dark-surface rule
for that reason and for no other.

States: loading (a 240dp skeleton block, `motion_pulse`), loaded, error («Не удалось получить
QR-код. Повторите.» + Tertiary «Повторить»).

### 10.7 Sign-out dialog

| Slot | Content |
|---|---|
| Title | «Выйти из аккаунта?» |
| Body | «Подписка останется активной. Чтобы вернуться, войдите снова.» |
| Buttons | Tertiary «Отмена» (auto-focused), then **«Выйти» as a text button in `?attr/pingBad`** on the right |

§7.5: the confirm says what it does. Never «OK», never «Да»/«Нет».

### 10.8 Sheet mechanics, all of them

| Property | Value |
|---|---|
| Shape | `@dimen/radius_sheet` 24, **top corners only** |
| Fill | `?attr/colorSurface` `#141619` |
| Handle | 36 × 4, `?attr/colorOutline`, radius pill, 12 above and below |
| Scrim | `?attr/colorScrim` @ 60 % |
| Title | `TextAppearance.App.Title` at the 16 gutter, 16 below |
| Rows | `layout_ledger_row`, dividers inset **16** (transaction surface, section 3.2) |
| Enter | slide + fade, `@integer/motion_reveal` 300 ms `ease_out_quint` |
| Exit | 225 ms (75 % of enter) `ease_standard` |
| Dismiss | scrim tap, drag down, system Back |
| Focus | moves into the sheet on open, returns to the trigger on close |
| Bottom padding | `@dimen/space_16` + the `navigationBars` inset |

---

## 11. Sub-pages

All four use the **seamless sub-page toolbar** (§4.8, owner request §0.4.6):

```
[16][ 24dp back glyph in a 48dp touch box ][16][ title, Title 16/700 ][ * ][ 0 or 1 trailing 40dp ][16]
height 56 (@dimen/toolbar_height) · background ?attr/colorBackground · elevation 0 · no divider at rest
```

`BaseActivity.setContentViewWithToolbar` currently applies `@style/ToolbarBrandTitle` (the 20sp
wordmark style) to **every** sub-page (CS §A.7). For these four it switches to
`app:titleTextAppearance="@style/TextAppearance.App.Title"`. `ToolbarBrandTitle` is for the wordmark
and the wordmark is not in a sub-page title.

### 11.1 Devices (`DeviceManagementActivity`, `activity_devices.xml`)

Toolbar «Устройства», one trailing 40dp refresh icon button. `SwipeRefreshLayout` wraps the list.

```
16 top
«Подключить устройство»                          section header
   Row.Nav   Показать QR-код          ›          hidden when remnawaveUuid is blank
   Row.Nav   Скопировать ссылку       ›          hidden when subscriptionUrl is blank
24
«Устройства»                                     section header
   TextView  «Подключено 2 из 5»                 Body 14/400 onSurfaceVariant,
             or «Подключено 2, без ограничений»   marginBottom = space_8
   Row.Value Добавить устройства    +150 ₽       hidden when maxExtraDevices <= 0
   ── hairline @68 ──
   device rows …
```

The current subtitle «Устройства, подключённые к вашей подписке» is deleted: it restates the screen
title.

**Device row** (`item_device.xml`, rewritten). This is the **one documented exception** to "one
trailing", because the row's content and its action are different things:

```
minHeight="@dimen/row_min_height"  paddingStart="@dimen/space_16"  paddingEnd="@dimen/space_8"
paddingVertical="@dimen/space_12"  background="@drawable/bg_row"
  [ 40dp neutral tile + 22dp platform glyph ]
      Android  → ic_acc_device_android
      Apple    → ic_acc_device_apple
      Windows  → ic_acc_device_windows
      Router   → ic_acc_device_router
      else     → ic_acc_devices
      (ported from the desktop's IsAndroid / IsApple / IsWindows / IsRouter resolution;
       Android draws ONE generic glyph for every device today)
  [ text column, weight 1 ]
      title     deviceModel → platform → «Неизвестное устройство»   App.Title  maxLines=1
      + chip    «Это устройство» (Chip.Neutral) when hwid == this device's hwid
                layout_marginStart="@dimen/space_8"
      subtitle  «Android · 09.07.2026»                              App.Subtitle, date in figure face
  [ ImageButton 48×48, ic_acc_link_off, tint="?attr/colorOnSurfaceVariant",
    contentDescription="@string/devices_unlink_cd" ]
```

- **The HWID line is deleted.** It is a diagnostic on a customer screen and it is the third line of
  every row. When two rows resolve to the **same title**, and only then, the subtitle gains the last
  four characters of the hwid: «Android · 09.07.2026 · a1b2».
- **The unlink glyph is neutral at rest**, not red. §6.4: inactive states are never saturated, and
  five idle rows with five red glyphs is a wall of alarm.
- **The current device is marked by the chip only.** The desktop washes the whole row in
  `Brush.Tile.Blue`, which puts an accent wash on a row that is not selected and dissolves the chip
  into its own background. Deleted on both platforms.
- **Unlink is undo, not confirm** (§7.5). Unlinking is **reversible** - the device re-registers on
  the next connect. Tapping removes the row immediately with a 220 ms fade, shows a Snackbar
  «Устройство отвязано» + «Отменить» for 5 s, and **the network call fires when the Snackbar
  dismisses**. «Отменить» cancels the call and restores the row. Leaving the screen commits
  immediately. The current `MaterialAlertDialog` and the desktop's in-view modal both die.
  Terminology: **«отвязать»** on both platforms (Android says «удалить» today - §9.3 allows one noun
  per concept).
- Delete target is **48dp**, not the current 44dp (§7.2).

**States:** skeleton (3 rows of the real geometry) · list · empty («Устройств пока нет» / «Устройства
появятся после первого подключения.» / no action) · no subscription («Подписка не активна» / «Купите
тариф, чтобы подключать устройства.» / **Primary** «Купить») · error («Не удалось загрузить
устройства. Проверьте сеть и повторите.» / **Tertiary** «Повторить») · offline (the inline bar; the
list stays, unlink and add are disabled).

**The «Ответ сервера (диагностика)» dialog is deleted**, along with `devices_diag_*` in
`strings_devices.xml`. It asks a paying customer to screenshot a raw HTTP body and send it to us.
When the parsed list is empty but `totalDevices > 0`, the screen shows the **error** state and the
raw body goes to `Log.w`.

Cache-first via `AccountCache` (fresh < 1 h) is preserved, including the invalidate-on-delete path.

### 11.2 Добавить устройства (`AddDevicesActivity`, `activity_add_devices.xml`)

A **sub-page**, not a sheet, because its stepper changes the price live: three decisions, not one
(section 10.1's container rule).

```
toolbar «Добавить устройства»
16
TextView   «Сейчас 5 слотов, занято 2»                        Body 14/400 onSurfaceVariant
24
LinearLayout horizontal, gravity=center_vertical, minHeight 56
  ImageButton  id=btn_minus  48×48  ic_remove  neutral glyph   contentDescription «Убрать устройство»
  TextView     id=tv_count   layout_weight=1  gravity=center
               @style/TextAppearance.App.Display  fontFeatureSettings="tnum, lnum"
  ImageButton  id=btn_plus   48×48  ic_add_24dp neutral glyph  contentDescription «Добавить устройство»
16
TextView   id=tv_estimate  «Примерно 150 ₽»                    App.Title numeric
TextView   «Точную сумму посчитаем при оплате»                 App.Caption, marginTop 4
24
── hairline @16 ──
[ layout_ledger_row × N method rows ]      ← the SAME component as the payment sheet
```

- The stepper is bounded by `tariff.maxExtraDevices`; at either bound the button is
  `isEnabled = false` and the style renders it at **0.38** (the current
  `BuyTariffActivity.setStepperEnabled` sets `alpha = 0.4f` imperatively - off-token and unstyled).
- Estimate: `pricePerExtraDevice × N × remainingDays / 30`, matching the desktop's
  `EstimateDevicePrice`. The volume `deviceDiscountTiers` are **not** exposed on `TariffDto`, so the
  estimate is an **upper bound** and the «Примерно» wording is **mandatory**.
- `tickHaptic()` on each stepper press. Nothing else on this screen vibrates.
- The balance row is **hidden** until PG-5 lands (`purchaseDevices` returning `AddDevicesResultDto`);
  card methods work today through `addDevices` → checkout URL → poll.

### 11.3 Способы входа (`LinkingActivity`, `activity_linking.xml`)

New on Android. Toolbar «Способы входа», no trailing action.

```
16
   Row.Value  Telegram    @sasha_erlish          ← linked: the handle IS the value
   or Row.Nav Telegram    ›   subtitle «Не привязан»
   ── hairline @68 ──
   Row.Value  Почта       sasha@mail.ru
   or Row.Nav Почта       ›   subtitle «Не привязана»
   ── hairline @68 ──
   Row.Value  Google      sasha@gmail.com        ← ONLY when googleLinked == true
```

The desktop's permanently-disabled «Скоро» button is deleted. **A method that cannot be linked in
this build does not appear.** Unfinished work rendered as UI is not a state (§17).

**Telegram linking** (`requestLinkTelegram()`, PG-1): tapping the unlinked row opens a sheet - title
«Привязка Telegram», body «Откройте бота и подтвердите привязку.», the code in a `Chip.Neutral` at
`TextAppearance.App.Title` + `tnum`, a **Primary** «Открыть бота» (deep link
`t.me/{telegramBotUsername}?start=link_{code}`), and a quiet «Ждём подтверждения…» line with a 20dp
indicator while the client polls. On success the sheet closes and a Snackbar reads «Telegram
привязан».

**Email linking** (`requestLinkEmail(email)`, PG-1): a sheet with the label «Почта» above a field
(`inputType="textEmailAddress"`, `autofillHints="emailAddress"`), a helper slot, and a **Primary**
«Отправить». Success → «Письмо отправлено. Проверьте почту.»

**Interim while PG-1 is open, and it is specified so the screen is never broken:** both unlinked rows
carry the subtitle «Привязка через сайт» and open `publicConfig.siteUrl` in a Custom Tab.

### 11.4 История платежей (`PaymentHistoryActivity`, rewritten)

Toolbar «История платежей», one trailing 40dp refresh, plus `SwipeRefreshLayout`.

A **tile-less divided ledger**, not a card grid. A payment is a **fact**, not an object you act on;
N identical rounded rectangles that do nothing is §2.4.3's uniform-card tell, and it is what ships
today.

```
«Июнь 2026»                                       section header, sentence case, UI face
   Продление Plus, 30 дней                450 ₽   ← desc Body 14/400 | amount App.Title numeric
   12.06.2026, 14:32                 [Оплачено]   ← Caption, figure face | Chip.Status.Ok
   ── hairline, layout_marginStart="@dimen/space_16" ──
   …
«Май 2026»
   …
```

| Property | Value |
|---|---|
| Row | `minHeight="@dimen/row_min_height"` 56, `paddingHorizontal="@dimen/space_16"`, `paddingVertical="@dimen/space_12"` |
| Clickable | **no.** There is no receipt endpoint; a row that looks pressable and does nothing is worse than one that does not |
| Left column | weight 1: description `maxLines=2 ellipsize=end`; date+time `App.Caption` in the **figure face** with `tnum` |
| Right column | `wrap_content`, right-aligned: amount `App.Title` + `tnum` + `Money.format`; then the status chip, `layout_marginTop="@dimen/space_4"` |
| Grouping | by month of `createdAt`, newest first, `Dates.monthHeader()` |

**Status: three hues plus neutral** (`22-components.md` R12). Four hues ship today, and «Отменён» is
yellow, which invents a fifth meaning - yellow means «истекает» and nothing else.

| Raw status | Label | Chip class |
|---|---|---|
| paid, success, succeeded, completed, confirmed, done | «Оплачено» | `Chip.Status.Ok` - `?attr/colorTertiary` @ 18 % fill, `#22C55E` label |
| pending, processing, new, created, waiting, in_progress | «В обработке» | `Chip.Status.Warn` - `#EAB308` @ 18 % fill, `#EAB308` label |
| failed, error, declined, rejected | «Ошибка» | `Chip.Status.Error` - `?attr/colorError` @ 18 % fill, `?attr/pingBad` label |
| canceled, cancelled, expired | «Отменён» | `Chip.Neutral` |
| anything else | «Не определён» | `Chip.Neutral` |

**The raw status is never printed** (§9.4 bans visible codes; `PaymentsAdapter` prints it verbatim
today for anything unmapped).

Description fallback chain: `description` → a mapped `kind` («Продление» / «Покупка подписки» /
«Пополнение баланса» / «Дополнительные устройства» / «Улучшение тарифа») → «Платёж». **`orderId` is
never shown.**

**States:** skeleton (six row silhouettes matching the two-column geometry - the centred
`ProgressBar` is deleted, §15 forbids it by name) · list · empty («Платежей пока нет» / «Здесь
появится история покупок и продлений.» / **no action**) · error («Не удалось загрузить историю.
Проверьте сеть и повторите.» + Tertiary «Повторить») · offline (bar; list stays; refresh disabled).

Cache-first via `AccountCache` is preserved, including the `showingCache` guard that ignores the
ViewModel's replayed empty seed.

---

## 12. Motion, haptics, accessibility

### 12.1 Motion

Every duration and curve comes from `res/values/motion.xml` and `res/interpolator/`. There is no
other value on this tab.

| Event | Token | ms | Curve |
|---|---|---|---|
| Press in / out (buttons, cards) | `motion_press_in` / `motion_press_out` | 90 / 160 | `ease_out_quart` / `ease_out_quint` |
| Row press (background step, **no scale**) | `motion_press_in` / `motion_press_out` | 90 / 160 | same |
| Skeleton → content, state → state, segment selection, tint change, meter value | `motion_state` | 220 | `ease_standard` |
| Sheet in | `motion_reveal` | 300 | `ease_out_quint` |
| Sheet out | (75 % of enter) | 225 | `ease_standard` |
| Sub-page in / out | `motion_reveal` / exit | 300 / 225 | `ease_out_quint` / `ease_standard` |
| Balance count-up, **on a changed value only** | `motion_reveal` | 300 | `ease_out_quint` |
| Device row removal | `motion_state` | 220 | `ease_standard` |
| Header hairline fade | `motion_state` | 220 | `ease_standard` |
| Skeleton pulse | `motion_pulse` | 1000 each way | `ease_standard` |
| Inline spinner | `motion_spin` | 1100 per revolution | linear (a continuous rotation, not a state transition) |

**No stagger and no entrance choreography on this tab** (D§8.5). A screen appears; it does not
perform. The desktop's `EntranceGroup2` staggered arrival has no Android counterpart and gains none.

**Reduced motion is a contract** (§8.8). Every imperative animator checks
`MotionUtils.animationsEnabled(context)` / `View.reducedMotion()` and jumps to the end state: the
balance lands instantly, the skeleton holds a static 0.7 alpha, the meter snaps, the segment fill
snaps, the device row disappears without a fade. Declarative `stateListAnimator` collapses
automatically at animator scale 0.

### 12.2 Haptics

`pressHaptic()` on **exactly two** things: confirming a payment (the tapped method row) and
confirming sign-out. `tickHaptic()` on the add-devices stepper. **Nothing else on this tab
vibrates** (§8.10).

### 12.3 Feedback channel

**No `Toast` for anything actionable** (§1.4.8). Every current toast becomes a `Snackbar` anchored
above the bottom navigation:

| Was a toast | Becomes |
|---|---|
| «Реферальный код скопирован» | Snackbar «Код скопирован» |
| «Аватар обновлён» | Snackbar «Аватар обновлён» |
| «Не удалось загрузить фото» | Snackbar «Не удалось загрузить фото» + «Повторить» |
| «Подписка оплачена» | Snackbar «Оплата прошла» |
| «Завершите оплату в браузере» | Snackbar «Завершите оплату в браузере» |
| every `toastError(messageFor(error))` | Snackbar with the mapped cause + «Повторить» |

Snackbar spec: `?attr/colorSurfaceContainerHigh` `#1A1D21`, radius `@dimen/radius_control` 16,
padding 16 × 12, Body 14/400 `?attr/colorOnSurface` max 2 lines, action = Tertiary label in
`?attr/colorPrimary`, 5000 ms with an action / 3000 ms without, one at a time.

### 12.4 Accessibility

| Requirement | How |
|---|---|
| Pane title | `ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.account_tab_title))` |
| The hero pair is **one** node | `block_days` carries `contentDescription="Осталось 5 дней"`; both children are `importantForAccessibility="no"` |
| The switch row announces its state | row `contentDescription` = «Автопродление, включено» / «…, выключено»; the `MaterialSwitch` is `clickable=false focusable=false importantForAccessibility=no` |
| Every icon-only control has a name | `android:contentDescription` on the avatar, rename, refresh, unlink, stepper buttons. Android passes this check outright today (CS §A.3) and must keep passing it |
| Selection is announced | the selected segment sets `AccessibilityNodeInfo.isSelected = true` |
| State changes are announced, not only drawn | `announceForAccessibility` on error appearance, on «Устройство отвязано», on «Оплата прошла» |
| Touch targets | 48 × 48 minimum, 8dp apart. The 40dp avatar tile gets a `TouchDelegate` to 48; the 44dp delete button becomes 48 |
| Reading order | matches visual order; nothing is unreachable; focus is not lost on a state swap |
| Contrast | verified in dark, light and mono. The only pairs on this tab below AAA are accent-on-surface (5.66:1), onAccent-on-accent (5.51:1) and error-fill (4.88:1, which is why error **text** is `?attr/pingBad` `#FF6069` at 6.15:1) |
| Font scale | 200 % at 320dp width, no clipping, no truncated primary label |

---

## 13. In-flight action matrix

§7.3: acknowledge within 100 ms, show a loading state after 300 ms, offer a cancel path beyond 3 s.

| Action | Optimistic? | In flight | Success | Failure | Offline |
|---|---|---|---|---|---|
| Pull to refresh | - | swipe indicator | content swaps at 220 ms | inline bar + «Повторить» | disabled; the offline bar is already up |
| Tap «Пополнить» | - | sheet opens at 300 ms | - | - | button disabled |
| Top-up submit | **no** (money) | tapped row shows a 20dp indicator; other rows 0.38 | sheet closes, browser opens, polling bar appears | inline error under the field; the sheet stays open | rows disabled |
| Renew | **no** | CTA label swaps in place for a 20dp arc, same width and height | balance path: card re-renders + Snackbar «Оплата прошла». card path: browser + polling bar | Snackbar «Платёж не прошёл. Попробуйте другой способ оплаты.» + «Повторить» | CTA disabled |
| Upgrade: pick target | - | that row's trailing shows a 16dp indicator while the quote loads | the payment sheet opens with the **exact** amount | inline row error «Не удалось получить сумму. Повторите.» | rows disabled |
| Upgrade: pay | **no** | as renew | card re-renders, badge updates | as renew | disabled |
| Add devices | **no** | as renew | the Devices page re-renders, the count updates | as renew | disabled |
| Toggle auto-renew | **no** (money-adjacent) | switch moves, row non-clickable, subtitle «Сохраняем…» | subtitle becomes the real next-charge line | switch animates back over 220 ms, Snackbar + «Повторить» | row disabled |
| Rename | **yes** (not money) | title updates immediately | nothing further | title reverts, Snackbar «Не удалось переименовать. Повторите.» + «Повторить» | dialog not openable |
| Copy referral / link | **yes** (local) | - | Snackbar «Код скопирован» / «Ссылка скопирована» | - | **still works** |
| Unlink device | **yes, deferred** | row removed at 220 ms; Snackbar + «Отменить» for 5 s; the call fires on dismiss | nothing further | the row returns, Snackbar «Не удалось отвязать устройство. Повторите.» + «Повторить» | disabled |
| Link Telegram | **no** | sheet shows the code + «Ждём подтверждения…» with a 20dp indicator | sheet closes, Snackbar «Telegram привязан», the head's handle line updates | inline «Не удалось получить код. Повторите.» + «Повторить» | row disabled |
| Activate trial | **no** | button label → 20dp arc | card re-renders as a trial, Snackbar «Пробный период активирован» | Snackbar with the mapped cause + «Повторить» | disabled |
| Sign out | **yes** (local) | - | the gate renders, all account state is cleared | - | **enabled** |

---

## 14. What loads, and when

`AccountFragment.loadAll()` today fires five requests on every `onViewCreated`. Keep the set, fix the
sequencing:

| Call | When | Why |
|---|---|---|
| `refreshProfile()` | on open, on resume after > 60 s, after any money action | identity, balance, root auto-renew flag |
| `loadSubscriptions()` (`/all` + `/subscription`, merged) | on open, after any money action, on each poll tick | the card |
| `loadPublicConfig()` | on open, cached for the process lifetime | payment methods, bot handle, site URL, trial flag |
| `loadTariffs()` | on open, cached for the process lifetime | the tariff badge and the upgrade targets |
| `loadPayments()` | on open | the «История платежей» row value |
| `loadDevices(selectedSub.remnawaveUuid)` | when the sub list lands **and** on every switcher change | the «Устройства» row value; also pre-warms `AccountCache` so the Devices sub-page opens instantly |

`loadDevices` is currently called for `list.firstOrNull()` only. It must follow the **selected**
subscription, or switching to a secondary shows the root's device count.

Nothing on this tab does I/O, JSON parsing or crypto on the main thread (§11.5).

---

## 15. Copy - the complete Russian string table

Sentence case everywhere. **No em-dash, no en-dash, hyphen only.** `…` is the single character
U+2026. «Ёлочки» for quotes. No final period on labels, titles, chips or buttons. Formal «вы»
throughout (the desktop's «Войди», «Оформи», «Выбери» are gone). `₽` always, `$` never.

### 15.1 Tab, head, gate

| Resource | Text |
|---|---|
| `account_tab_title` | Аккаунт |
| `account_name_fallback` | Аккаунт |
| `account_no_telegram` | Telegram не привязан |
| `account_change_avatar` | Сменить фото |
| `account_avatar_gallery` | Выбрать из галереи |
| `account_avatar_remove` | Убрать фото |
| `account_avatar_updated` | Аватар обновлён |
| `account_avatar_error` | Не удалось загрузить фото |
| `account_balance_label` | Баланс |
| `account_top_up` | Пополнить |
| `account_gate_title` | Войти в аккаунт |
| `account_gate_body` | Подписка, устройства и платежи хранятся в аккаунте |
| `account_gate_telegram` | Войти через Telegram |
| `account_gate_email` | Войти по почте |
| `account_gate_register` | Создать аккаунт |
| `account_gate_waiting` | Ждём подтверждения в Telegram |
| `account_gate_open_tg` | Открыть Telegram |
| `account_gate_restart` | Начать заново |

### 15.2 Switcher and card

| Resource | Text |
|---|---|
| `account_switcher_title` | Подписка |
| `account_switcher_sheet_title` | Выберите подписку |
| `account_sub_default_name` | Подписка %1$d |
| `account_card_perpetual_title` | Бессрочная подписка |
| `account_card_perpetual_detail` | Срок не ограничен |
| `account_card_active_title` | Активна до %1$s |
| `account_card_active_detail` | Осталось %1$s |
| `account_card_today_title` | Истекает сегодня |
| `account_card_expired_title` | Подписка истекла |
| `account_card_expired_detail` | Срок закончился %1$s |
| `account_card_unknown_title` | Срок неизвестен |
| `account_card_unknown_detail` | Обновите позже или проверьте подключение |
| `account_card_trial_badge` | Пробный |
| `account_card_traffic_label` | Трафик |
| `account_card_traffic_value` | %1$s из %2$s |
| `account_card_traffic_over` | Лимит исчерпан |
| `account_card_renew` | Продлить |
| `account_card_renew_price` | Продлить · %1$s |
| `account_card_buy` | Купить |
| `account_card_buy_tariff` | Купить тариф |
| `account_card_pick_tariff` | Выбрать тариф |
| `account_card_rename` | Переименовать подписку |
| `account_card_renew_site` | Продление доступно на сайте |
| `account_card_open_site` | Открыть сайт |
| `account_rename_title` | Название подписки |
| `account_rename_label` | Название |
| `account_rename_empty` | Введите название |
| `account_rename_failed` | Не удалось переименовать. Повторите. |

`account_trial_badge` («ПРОБНЫЙ», ALL-CAPS, banned by §1.4.7) is deleted, along with the
unreferenced `account_profile_title`, `account_sub_summary_title`, `account_subs_empty`,
`account_hub_*_sub`, `account_traffic`, `account_payments_more`, `account_active_sub*`,
`account_tariffs_*`, `account_option_duration`, `account_tariff_*`, `account_promo_*`,
`account_trial*`, `account_unlimited`, `buy_loading`, `buy_pick_duration`, `buy_balance_label`.

### 15.3 Auto-renew

| Resource | Text |
|---|---|
| `account_auto_renew` | Автопродление |
| `account_auto_renew_next` | Спишем %1$s %2$s |
| `account_auto_renew_on` | Продлим автоматически |
| `account_auto_renew_off` | Продление вручную |
| `account_auto_renew_risk` | Без автопродления доступ прервётся %1$s |
| `account_auto_renew_saving` | Сохраняем… |
| `account_auto_renew_failed` | Не удалось изменить автопродление. Повторите. |
| `account_auto_renew_cd_on` | Автопродление, включено |
| `account_auto_renew_cd_off` | Автопродление, выключено |

### 15.4 Groups and rows

| Resource | Text |
|---|---|
| `account_group_subscription` | Подписка |
| `account_group_billing` | Оплата |
| `account_group_signin` | Вход |
| `account_row_devices` | Устройства |
| `account_devices_pair` | %1$d / %2$d |
| `account_devices_unlimited` | Без ограничений |
| `account_devices_slots` | Слотов на подписке |
| `account_row_upgrade` | Улучшить тариф |
| `account_row_buy` | Купить подписку |
| `account_row_history` | История платежей |
| `account_row_referral` | Реферальный код |
| `account_referral_copied` | Код скопирован |
| `account_link_copied` | Ссылка скопирована |
| `account_row_login_methods` | Способы входа |
| `account_row_link_telegram` | Привязать Telegram |
| `account_row_link_telegram_sub` | Управление подпиской из бота |
| `account_row_logout` | Выйти |
| `account_logout_title` | Выйти из аккаунта? |
| `account_logout_body` | Подписка останется активной. Чтобы вернуться, войдите снова. |
| `common_cancel` | Отмена |
| `common_save` | Сохранить |
| `common_retry` | Повторить |
| `common_undo` | Отменить |
| `common_close` | Закрыть |

### 15.5 Empty, offline, polling

| Resource | Text |
|---|---|
| `account_empty_title` | Подписки пока нет |
| `account_empty_body` | Купите тариф, чтобы подключаться к серверам Departament. |
| `account_empty_trial` | Начать пробный период |
| `account_trial_activated` | Пробный период активирован |
| `account_offline` | Нет сети. Показаны последние данные. |
| `account_pay_checking` | Проверяем оплату… |
| `account_pay_refresh` | Обновить |
| `account_pay_done` | Оплата прошла |
| `account_pay_unconfirmed` | Не удалось подтвердить оплату. Проверьте историю платежей. |
| `account_pay_open_history` | История |
| `account_checkout_browser` | Завершите оплату в браузере |
| `account_checkout_no_browser` | Не удалось открыть страницу оплаты. Проверьте браузер по умолчанию. |

### 15.6 Errors - cause, then fix, no codes

| Cause | Resource | Text |
|---|---|---|
| cold-load title | `account_error_title` | Не удалось загрузить аккаунт |
| `ApiError.Network` | `account_error_network` | Нет подключения к интернету. Проверьте сеть и повторите. |
| `ApiError.ServiceUnavailable` | `account_error_service` | Сервис временно недоступен. Повторите через пару минут. |
| `ApiError.RateLimited` | `account_error_rate` | Слишком много запросов. Повторите через минуту. |
| `ApiError.Timeout` | `account_error_timeout` | Сервер не ответил вовремя. Повторите попытку. |
| generic | `account_error_generic` | Что-то пошло не так. Повторите попытку. |
| payment declined | `pay_failed` | Платёж не прошёл. Попробуйте другой способ оплаты. |
| device limit | `devices_limit` | Достигнут лимит устройств. Отвяжите одно из устройств. |

`ApiError.Unauthorized` is **not** an error state: it clears the session and renders the gate.
`account_payment_error_title`, `account_payment_error_body` («HTTP %1$s\n%2$s») and
`account_payment_error_body_nodetail` are **deleted**.

### 15.7 Payment component

| Resource | Text |
|---|---|
| `pay_sheet_title` | Оплата |
| `pay_total` | Итого |
| `pay_estimate` | Примерно %1$s |
| `pay_estimate_note` | Точную сумму посчитаем при оплате |
| `pay_from_balance` | Оплатить с баланса |
| `pay_balance_have` | На балансе %1$s |
| `pay_balance_short` | Не хватает %1$s |
| `pay_by_card` | Оплатить картой |
| `pay_by_sbp` | Оплатить через СБП |
| `pay_by_other` | Оплатить через %1$s |
| `pay_no_methods` | Способы оплаты недоступны. Повторите позже. |
| `pay_subject_renew` | Продление %1$s, %2$s |
| `pay_subject_upgrade` | Улучшение до %1$s, +%2$s |
| `pay_subject_devices` | %1$s к подписке «%2$s» |
| `pay_subject_topup` | Пополнение баланса |
| `pay_subject_buy` | %1$s, %2$s |
| `account_top_up_title` | Пополнение баланса |
| `account_top_up_label` | Сумма |
| `account_top_up_empty` | Введите сумму |
| `account_top_up_zero` | Сумма должна быть больше 0 |
| `account_top_up_nan` | Введите сумму цифрами |

`pay_method_from_balance_fmt` is deleted with its dash (it is named, not quoted, so this document
stays dash-clean); it is replaced by `pay_from_balance` + `pay_balance_have`.

### 15.8 Upgrade

| Resource | Text |
|---|---|
| `upgrade_sheet_title` | Улучшить тариф |
| `upgrade_sheet_body` | Доплата рассчитывается за оставшийся срок |
| `upgrade_quote_failed` | Не удалось получить сумму. Повторите. |

### 15.9 Devices

| Resource | Text |
|---|---|
| `devices_title` | Устройства |
| `devices_group_connect` | Подключить устройство |
| `devices_row_qr` | Показать QR-код |
| `devices_row_link` | Скопировать ссылку |
| `devices_group_list` | Устройства |
| `devices_connected_of` | Подключено %1$d из %2$d |
| `devices_connected_unlimited` | Подключено %1$d, без ограничений |
| `devices_row_add` | Добавить устройства |
| `devices_unknown_model` | Неизвестное устройство |
| `devices_this_device` | Это устройство |
| `devices_unlink_cd` | Отвязать устройство |
| `devices_unlinked` | Устройство отвязано |
| `devices_unlink_failed` | Не удалось отвязать устройство. Повторите. |
| `devices_empty_title` | Устройств пока нет |
| `devices_empty_body` | Устройства появятся после первого подключения. |
| `devices_nosub_title` | Подписка не активна |
| `devices_nosub_body` | Купите тариф, чтобы подключать устройства. |
| `devices_error` | Не удалось загрузить устройства. Проверьте сеть и повторите. |
| `devices_add_title` | Добавить устройства |
| `devices_add_current` | Сейчас %1$d слотов, занято %2$d |
| `devices_add_note` | Точную сумму посчитаем при оплате |
| `devices_add_minus_cd` | Убрать устройство |
| `devices_add_plus_cd` | Добавить устройство |
| `devices_qr_title` | QR-код подписки |
| `devices_qr_body` | Отсканируйте в приложении на другом устройстве |
| `devices_qr_copy` | Скопировать ссылку |
| `devices_qr_error` | Не удалось получить QR-код. Повторите. |

`devices_diag_title`, `devices_diag_http`, `devices_diag_empty`, `devices_diag_failed`,
`devices_diag_no_body`, `devices_hwid`, `devices_subtitle`, `devices_delete_*` are **deleted**.

### 15.10 Payment history

| Resource | Text |
|---|---|
| `history_title` | История платежей |
| `history_status_paid` | Оплачено |
| `history_status_pending` | В обработке |
| `history_status_failed` | Ошибка |
| `history_status_canceled` | Отменён |
| `history_status_unknown` | Не определён |
| `history_kind_renew` | Продление |
| `history_kind_purchase` | Покупка подписки |
| `history_kind_topup` | Пополнение баланса |
| `history_kind_devices` | Дополнительные устройства |
| `history_kind_upgrade` | Улучшение тарифа |
| `history_kind_other` | Платёж |
| `history_empty_title` | Платежей пока нет |
| `history_empty_body` | Здесь появится история покупок и продлений. |
| `history_error` | Не удалось загрузить историю. Проверьте сеть и повторите. |

### 15.11 Способы входа

| Resource | Text |
|---|---|
| `linking_title` | Способы входа |
| `linking_telegram` | Telegram |
| `linking_email` | Почта |
| `linking_google` | Google |
| `linking_not_linked_m` | Не привязан |
| `linking_not_linked_f` | Не привязана |
| `linking_via_site` | Привязка через сайт |
| `linking_tg_sheet_title` | Привязка Telegram |
| `linking_tg_sheet_body` | Откройте бота и подтвердите привязку. |
| `linking_tg_open_bot` | Открыть бота |
| `linking_tg_waiting` | Ждём подтверждения… |
| `linking_tg_done` | Telegram привязан |
| `linking_tg_failed` | Не удалось получить код. Повторите. |
| `linking_email_sheet_title` | Привязка почты |
| `linking_email_label` | Почта |
| `linking_email_send` | Отправить |
| `linking_email_sent` | Письмо отправлено. Проверьте почту. |

### 15.12 The dash debt this closes

Named rather than quoted, so this document stays dash-clean:
`account_price_option`, `pay_method_from_balance_fmt`, `devices_diag_empty`, `devices_diag_failed`.
After this rework, `grep -rn` for the two literal dash characters over
`values*/strings_account.xml`, `values*/strings_devices.xml`, `values*/strings_pay.xml`,
`values*/strings_buy.xml` returns nothing.

---

## 16. Data contract binding

**A value not in this table may not be drawn.**

| Rendered | Source | Fallback chain | Trap |
|---|---|---|---|
| Head name | `GET /client/auth/me` | `telegramName` → `@telegramUsername` → `email` → «Аккаунт» | the Home chip must use the **same** chain; it does not today |
| Head handle | same | `@telegramUsername` → `email` → «Telegram не привязан» | - |
| Monogram / photo | `avatarUrl` (+6 Gson alternates) or the local gallery pick | first grapheme of the name, upper-cased → `ic_acc_person` | - |
| Balance | `balance` (+ `currency`, **ignored for display**) | `0 ₽` | never `$`; the code goes in the request body verbatim |
| Referral code | `referralCode` | row hidden when blank | - |
| Sub list | `/client/subscription/all` merged with `/client/subscription` | a primary-only account yields a synthesised root with a **blank id** | rename hidden when `id` is blank |
| Sub name | `displayName` → `defaultLabel` → «Подписка N» | - | - |
| Tariff badge | catalogue by `tariffId` → catalogue by `tariffPriceOptionId` → `tariffBadgeName()` | **hidden** when nothing resolves | `tariffBadgeName()` deliberately ignores `productName` / `subscriptionProductName`, which go stale after an upgrade. Keep that. |
| Expiry | `subscription.raw().expireAt` → `expireAtIso` | «Срок неизвестен» | perpetual sentinel: blank, or year ≥ 2099, or > 3650 days |
| Traffic | `subscription.raw()` **only, root only** | block absent | used = `trafficUsed` **or** `userTraffic.usedTrafficBytes`; `trafficLimitBytes == null` means unlimited → block absent |
| Device slots | `totalDevices` → `raw().hwidDeviceLimit` | «Без ограничений» when `<= 0` | `deviceCount` is **extra purchased devices**, not usage |
| Devices used | `GET /client/devices?uuid=`.length, **active sub only** | secondary subs show slots only | `connectedDevices` from `/all` is **always 0** - never render it |
| Renewal price | `renewalPrice` → `tariffPrice` → `autoRenewNextChargeAmount` | CTA becomes «Выбрать тариф» and routes to Buy | root only |
| Next charge date | `autoRenewNextChargeAt` | «Продлим автоматически» | root only |
| Auto-renew flag | profile `autoRenewEnabled` for the root; `sub.autoRenewEnabled` for secondaries | off | the root's flag lives on the **profile**, not the sub |
| Trial | `isTrial` on the `/all` **root entry** | false | `buildRootSub` reads `rootFromAll?.isTrial ?: false`, so a primary-only account can never be detected as trial. That is a backend limit, not a bug to paper over. **Never infer trial from tariff name or squad** - in this deployment the trial squad *is* the paid base squad |
| Upgrade targets | `GET /public/tariffs` minus the current tariff | the row and the sheet are hidden | - |
| Upgrade amount | `GET /client/subscriptions/upgrade-quote` | the confirm step cannot open without it | **exact**, never an estimate |
| Add-devices price | `pricePerExtraDevice × N × remainingDays / 30` | «Примерно» wording is **mandatory** | client-side estimate; volume tiers unknown |
| Payment methods | `GET /public/config` `plategaMethods[{id,label}]` | «Способы оплаты недоступны. Повторите позже.» and the sheet does not open | - |
| Bot handle | `telegramBotUsername` | the Telegram deep link is not offered | - |
| Site URL | `siteUrl` | the interim linking route and «Открыть сайт» are not offered | - |
| Trial availability | `trialEnabled && !trialUsed` | the trial button is hidden | - |
| History | `GET /client/payments` | empty state | `createdAt` is the sort key and the month-group key |
| Latest payment date | `payments.maxBy(createdAt)` | the row's value is omitted | - |
| QR | `GET /client/subscription/qr?uuid=` | the QR row is hidden | root/active only |
| Subscription URL | `subscription.raw().subscriptionUrl` | the copy-link row is hidden | root/active only |

---

## 17. Tokens, styles and files

### 17.1 Android tokens added (`res/values/dimens.xml`)

Every one already exists in `10-design-system.md` §2.7 / §3; these are the declarations that are
missing from the file today.

```xml
<!-- Control radius. Completes the shape scale chip 12 / control 16 / card 20 / sheet 24.
     The owner rejected capsule CTAs in writing (Assets/GlobalStyles.axaml:3-14), so
     radius_pill is for circles and tracks only. -->
<dimen name="radius_control">16dp</dimen>
<!-- Primary CTA height, and the compact height for everything else in flow.
     Declared as minHeight, never as layout_height: a fixed height clips a
     two-line label at font scale 200 %. -->
<dimen name="btn_height">52dp</dimen>
<dimen name="btn_height_compact">48dp</dimen>
<dimen name="btn_min_width">96dp</dimen>
<!-- Input field. Radius 16, same family as a button. -->
<dimen name="field_height">52dp</dimen>
<!-- Row text origin = screen_gutter 16 + tile_size 40 + space_12 12. Hairline inset. -->
<dimen name="row_text_origin">68dp</dimen>
<!-- Determinate progress / traffic meter track. -->
<dimen name="meter_height">6dp</dimen>
<!-- Empty-state tile and its glyph. -->
<dimen name="empty_icon">64dp</dimen>
<dimen name="empty_glyph">32dp</dimen>
<!-- Seamless sub-page toolbar and tab header. Replaces ?attr/actionBarSize. -->
<dimen name="toolbar_height">56dp</dimen>
<!-- Content column cap on sw600dp. -->
<dimen name="content_max_width">720dp</dimen>
<!-- Inline glyph size (chevron, status). The thirteen 18dp chevrons die with this. -->
<dimen name="glyph_20">20dp</dimen>
```

**Deleted:** `sub_card_height` 152, `dot_size`, `dot_size_active`, `dot_gap` (no carousel, no dots).

### 17.2 Motion (`res/values/motion.xml`)

```xml
<!-- Skeleton pulse. Loading feedback, NOT the 600 ms hero moment: a skeleton
     conveys state, which 8.1 permits. Replaces the off-token 900 ms
     AccelerateDecelerate pulse in AccountFragment.kt:413-430. -->
<integer name="motion_pulse">1000</integer>
<!-- Inline spinner: one full revolution. Linear is correct for a continuous
     rotation and is not the 8.3 ban, which is about state transitions. -->
<integer name="motion_spin">1100</integer>
<!-- Re-entry guard for taps that are not command-gated. -->
<integer name="input_debounce">500</integer>
```

### 17.3 Colour attributes

Resource-qualified colours do not follow `ThemeOverlay.Mono`, so these are **theme attributes**, set
in `Theme.Departament`, its night variant and `ThemeOverlay.Mono`.

```xml
<attr name="warning"   format="color"/>   <!-- «истекает» and the auto-renew risk line, only -->
<attr name="pingBad"   format="color"/>   <!-- error TEXT only; fills keep colorError -->
<attr name="pingGood"  format="color"/>   <!-- «Оплачено» -->
```

| Attr | Dark | Light | Mono | Measured |
|---|---|---|---|---|
| `warning` | `#EAB308` | `#8A6300` | ink | dark 9.45:1 on surface, 10.27:1 on background; light 5.51:1 on white |
| `pingBad` | `#FF6069` | `#C42B32` | ink | dark 6.15:1 on surface |
| `pingGood` | `#22C55E` | `#065132` | ink | dark 7.95:1 on surface |

`#8A6300` exists because `#EAB308` measures **1.92:1** on white and cannot ship in the light theme.
`values-night/colors.xml` currently sets `ping_bad` to `#F04452` (4.88:1); it becomes `#FF6069`.

New colours: `@color/accent_fill_12` `#1F4C8DFF` (the 12 % selected fill), `@color/skeleton`
(alias of `colorSurfaceContainerHighest`), `@color/accent_pressed` `#3877E0`.

### 17.4 Styles this tab depends on

| Style | Change | Why |
|---|---|---|
| `@anim/press_scale` | 0.96 → **0.97**; release interpolator `ease_out_quart` → `ease_out_quint` | §7.1, one press recipe |
| `@style/SettingsSectionLabel` | `paddingTop` 18dp → `@dimen/space_24`; `paddingBottom` → `@dimen/space_8` | 18 is off-scale |
| `Widget.Departament.Button.*` (new) | `insetTop`/`insetBottom` **0dp**, `cornerRadius` `@dimen/radius_control`, `minHeight` from the token, `textAppearance` from the ramp, `textAllCaps=false` | Android's declared-52dp CTAs draw at **40dp** today because `Widget.Material3.Button` carries 6dp insets; and zero of 33 Android buttons carries a `textAppearance`, so every button label is Roboto |
| `Widget.Departament.Chip` + `.Accent` / `.Neutral` / `.Status.Ok` / `.Status.Warn` / `.Status.Error` (new) | height 24, padding 8 × 4, radius 12, `clickable=false` | Android has **no** `Chip` component; five `TextView`s wearing shape drawables do the job today, one with 2dp vertical padding |
| `Widget.Departament.Segment` (new) | per section 6.1 | - |
| `Widget.Departament.Progress.Linear` (new) | `trackThickness` 6, `trackCornerRadius` 3, `indicatorColor` `?attr/colorPrimary` | - |
| `Widget.Departament.Snackbar` + `.TextView` + `.Button` (new) | per section 12.3, wired via `snackbarStyle` / `snackbarTextViewStyle` / `snackbarButtonStyle` | - |
| `themes.xml` | set `materialButtonStyle`, `materialCardViewStyle`, `materialSwitchStyle`, `textInputStyle`, `chipStyle`, `snackbarStyle` and the three `shapeAppearance*Component` attrs | without these every instance re-declares its own shape and height, which is the root cause of the six drawn button heights |
| `BaseActivity` toolbar | `titleTextAppearance` → `@style/TextAppearance.App.Title`, height `@dimen/toolbar_height`, background `?attr/colorBackground`, elevation 0, no divider | §4.8, owner request §0.4.6 |

### 17.5 Files

**New**

```
res/layout/fragment_account.xml                  the tab scaffold
res/layout/layout_account_header.xml             the 56dp header
res/layout/layout_account_head.xml               identity + balance
res/layout/layout_account_switcher.xml           segmented, 2-3 subs
res/layout/layout_account_card.xml               the subscription card
res/layout/layout_account_card_empty.xml
res/layout/layout_account_card_error.xml
res/layout/layout_account_skeleton.xml
res/layout/layout_account_gate.xml
res/layout/layout_status_bar_inline.xml          offline / polling
res/layout/layout_row_nav.xml                    Row.Navigation
res/layout/layout_row_value.xml                  Row.Value
res/layout/layout_row_switch.xml                 Row.Toggle
res/layout/layout_ledger_row.xml                 tile-less transaction row
res/layout/layout_state_empty.xml                the ONE empty/error silhouette
res/layout/sheet_payment.xml
res/layout/sheet_top_up.xml
res/layout/sheet_upgrade.xml
res/layout/sheet_subscription_pick.xml
res/layout/sheet_qr.xml
res/layout/sheet_link_telegram.xml
res/layout/sheet_link_email.xml
res/layout/dialog_rename_subscription.xml
res/layout/activity_linking.xml
res/layout/activity_add_devices.xml
res/drawable/bg_row.xml                          ripple over selector, state_pressed + state_focused
res/drawable/bg_segment_track.xml
res/drawable/bg_status_bar.xml
res/drawable/ic_spinner_arc.xml + res/animator/spinner_rotate.xml + res/drawable/spinner_arc.xml
res/drawable/ic_acc_edit.xml, ic_acc_logout.xml, ic_acc_key.xml, ic_acc_link_off.xml,
             ic_acc_qr.xml, ic_acc_cloud_off.xml, ic_acc_person.xml, ic_acc_alert.xml,
             ic_acc_telegram.xml, ic_acc_google.xml, ic_acc_mail.xml,
             ic_acc_device_android.xml, ic_acc_device_apple.xml,
             ic_acc_device_windows.xml, ic_acc_device_router.xml
java/com/v2ray/ang/util/Money.kt, Dates.kt, Bytes.kt
java/com/v2ray/ang/ui/LinkingActivity.kt, AddDevicesActivity.kt, PaymentSheet.kt
```

The Telegram / Google / Mail glyphs are **ports** of the desktop's `Geo.Acc.Telegram` /
`Geo.Acc.Google` / `Geo.Acc.Mail` `StreamGeometry` data into 24dp-viewport vector drawables, not
redraws (§10.1: a glyph needed on the other platform is ported, never redrawn).

**Rewritten**

```
java/com/v2ray/ang/ui/AccountFragment.kt          state machine, no carousel, no toasts, no dialogs
java/com/v2ray/ang/ui/DeviceManagementActivity.kt undo unlink, platform glyphs, no diagnostic
java/com/v2ray/ang/ui/PaymentHistoryActivity.kt   ledger + month groups + skeleton
java/com/v2ray/ang/ui/adapter/DeviceAdapter.kt
java/com/v2ray/ang/ui/adapter/PaymentsAdapter.kt  three hues, chips, no raw status
java/com/v2ray/ang/viewmodel/AccountViewModel.kt  selectedIndex, health, switcherMode, scoped calls
java/com/v2ray/ang/auth/DepartamentApiClient*.kt  PG-1, PG-2, PG-3, PG-5
res/layout/activity_devices.xml, item_device.xml
res/layout/activity_payment_history.xml, item_payment.xml
res/layout/layout_home_account.xml                dead group_login + the ✕ text glyph deleted;
                                                  avatar becomes the 40dp tile; name chain unified
```

**Deleted**

```
res/layout/activity_account.xml            (560 lines)
res/layout/item_subscription_card.xml
res/layout/dialog_top_up.xml
res/layout/sheet_payment_method.xml, item_payment_method.xml
res/layout/layout_setting_row.xml, layout_setting_toggle_row.xml   (0 call sites)
res/drawable/dot_active.xml, dot_inactive.xml
res/drawable/bg_acc_chip.xml, bg_acc_badge.xml, bg_avatar_edit.xml, bg_skeleton.xml
java/com/v2ray/ang/ui/SubscriptionPagerAdapter.kt
```

---

## 18. Decisions this document takes

Paste rows **A-1 … A-16** into `00-rules.md` §18 before implementation begins. Anything marked
**needs owner sign-off** does not ship until the row is pasted.

| # | Decision | Rule affected | Status |
|---|---|---|---|
| A-1 | The tab's purpose is the four questions in §1.1. The Display slot is **conditional**: balance when the subscription is healthy, remaining days when it is expiring or expired. Exactly one Display figure is on screen at all times | §3.4, D§7.3 | taken |
| A-2 | §4.1's 68dp text origin applies to **tiled** rows. A tile-less list (the payment component, payment history) uses the 16dp gutter as its origin and its hairline inset. A surface never mixes the two | §4.1 | taken |
| A-3 | Signed out, the Account destination **remains** and renders a sign-in gate. Runtime removal of a navigation destination is a defect. A build with `BackendConfig.isConfigured() == false` removes it at start-up | §7.7, §13 | taken |
| A-4 | Both subscription carousels are deleted. 1 sub = the card; 2-3 = a segmented control; 4+ = a select row with a radio sheet | §11.2, §1.3 | taken |
| A-5 | Every icon tile on this tab and its four sub-pages is neutral. No blue tile, no green balance tile, no accent row title | §3.6 | taken |
| A-6 | The subscription card carries **no status chip**. Health is expressed by typography, size and colour on the time block | §2.4.4 | taken |
| A-7 | Removing a device is «отвязать» on both platforms, and it is **undo**, not a confirmation dialog | §9.3, §7.5 | taken |
| A-8 | Skeletons are the silhouette of the result, appear after 300 ms, pulse at `motion_pulse` 1000 ms `ease_standard`, and crossfade to content at 220 ms | §15 | taken |
| A-9 | The tariff badge is the product's **one** `Chip.Accent` (`colorPrimaryContainer` / `colorOnPrimaryContainer`). It is a label, never a control | §3.6 | taken |
| A-10 | «Улучшить тариф» has **one** entrance: a Row.Navigation in the «Подписка» group. The in-card text button is deleted, so the card never shows two competing actions | §4.3 | taken |
| A-11 | `getReferralStats` is **not** surfaced. The referral **code** is one row; referral statistics are a web-cabinet surface | - | taken |
| A-12 | Promo codes are **not** surfaced until a server-side quote exists. The client cannot honour "displayed total == charged amount" for a discounted price without one, and computing the discount locally would break that contract | §9.4 | taken |
| A-13 | New theme attributes `warning` (`#EAB308` dark / `#8A6300` light) and `pingBad` (`#FF6069` dark / `#C42B32` light), as **attributes** rather than qualified colours, because qualified colours do not follow `ThemeOverlay.Mono` | §3.5 | **needs owner sign-off** (new hue value) |
| A-14 | The payment surface is a **bottom sheet** when the flow has at most one input, and a **sub-page** when a stepper changes the price. The rows inside are the same component either way. This deviates from `11-app-structure.md` §4.3.2, which lists «Продление» as a sub-page: a two-tap money decision with nothing to choose but the method is what §7.6 calls a sheet | §7.6, `11-app-structure.md` §4.3.2 | taken |
| A-15 | The referral row lives in the «Оплата» group, not as a standalone row above the card. This deviates from `11-app-structure.md` §4.3.2 block 3: a 56dp row between the head and the one card pushes the card below the fold on a 640dp-tall viewport | `11-app-structure.md` §4.3.2 | taken |
| A-16 | The «Веб-кабинет» SSO row is deleted from the desktop rather than ported to Android. Android has no `createAppHandoff()`, and the row answers none of §1.1's four questions | §13 | taken |

**Preconditions from `03-direction.md` §11.2** that this tab inherits: **D-1** (which Cyrillic UI
face) and **D-2** (Space Grotesk scoped to figures). Every "UI face" in this document means whatever
D-1 resolves to; every "figure face" means Space Grotesk. If D-1 is unresolved at implementation
time the fallback in D§6.1 applies (`sans-serif`) and **no layout in this file changes**.

---

## 19. Acceptance

Run `00-rules.md` §16's full pre-flight first. These are the additional checks this tab must pass,
and every one is countable or mechanical.

### 19.1 Counting

- [ ] Cards on the tab: **exactly 1** (the subscription). Screenshot it and count stroked rectangles.
- [ ] Filled accent surfaces: **1** when the state needs action, **0** when active or perpetual (§1.3).
- [ ] Blue icon tiles on the tab and its four sub-pages: **0**.
- [ ] Distinct button heights: **2** (52 and 48). Distinct button radii: **1** (16). Distinct chevron sizes: **1** (20dp).
- [ ] Row text origins on any one surface: **1** (68 on management surfaces, 16 on transaction surfaces).
- [ ] Status hues in payment history: **3** plus neutral.
- [ ] Display figures on screen: **exactly 1**, in every state, including expiring and expired.
- [ ] Distinct vertical gap values used: at least **4** of {4, 8, 12, 16, 24, 32}. If everything is 16, the rhythm failed.
- [ ] Money formatters in the codebase: **1** (`util/Money.kt`).

### 19.2 Mechanical, from `V2rayNG/app/src/main/res`

Each must return nothing.

```bash
# raw colour literals
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint|app:strokeColor)="#' \
  layout/fragment_account.xml layout/layout_account_*.xml layout/layout_row_*.xml \
  layout/layout_ledger_row.xml layout/activity_devices.xml layout/activity_payment_history.xml \
  layout/activity_linking.xml layout/activity_add_devices.xml layout/sheet_*.xml

# synthetic bold, inline sizes, all-caps
grep -rn 'textAllCaps="true"\|android:textStyle="bold"\|android:textSize' \
  layout/fragment_account.xml layout/layout_account_*.xml layout/sheet_*.xml layout/item_*.xml

# off-scale dp
grep -rnoE '"(-?[0-9]+)dp"' layout/fragment_account.xml layout/layout_account_*.xml \
  layout/layout_row_*.xml layout/sheet_*.xml \
  | grep -vE '"(0|1|3|4|8|12|14|16|20|22|24|32|36|40|48|52|56|64|240)dp"'

# fixed button heights (must be minHeight)
grep -rn 'MaterialButton' -A6 layout/*.xml | grep 'layout_height="[0-9]'

# dashes and three-dot ellipses in shipped copy
grep -rn -e '—' -e '–' values*/strings_account.xml values*/strings_devices.xml \
  values*/strings_pay.xml values*/strings_buy.xml
grep -rn '\.\.\.' values*/strings_account.xml values*/strings_devices.xml

# the deleted surfaces must be gone
ls layout/activity_account.xml layout/item_subscription_card.xml layout/dialog_top_up.xml \
   layout/sheet_payment_method.xml 2>/dev/null

# Cyrillic set in the figure face
grep -rn -B2 -A2 'TextAppearance.App.Numeric' layout/*.xml | grep -P '[\x{0400}-\x{04FF}]'
```

From `V2rayNG/app/src/main/java/com/v2ray/ang`:

```bash
# the dead endpoints must be fixed
grep -rn 'subscription/auto-renew' auth/BackendConfig.kt        # must be /client/auto-renew
grep -rn 'class AutoRenewRequestDto' -A3 auth/dto/SubscriptionDtos.kt   # must carry @SerializedName("enabled")
# no toasts for actionable feedback on this tab
grep -rn 'toast\|toastError\|toastSuccess' ui/AccountFragment.kt ui/DeviceManagementActivity.kt \
  ui/PaymentHistoryActivity.kt ui/LinkingActivity.kt ui/AddDevicesActivity.kt
# one money formatter
grep -rn 'currencySymbol\|formatMoney' ui/ viewmodel/
```

### 19.3 By eye, dark and light and mono, at 100 % and 200 % font scale

- [ ] **Seven card screenshots**, each read aloud: 30 days, 7 days, 3 days, expiring today, expired, trial, perpetual, unknown expiry.
- [ ] **Five switcher screenshots**: 0, 1, 2, 3, 7 subscriptions.
- [ ] **Six whole-tab screenshots**: skeleton, empty, error, offline, partial, payment-polling.
- [ ] The gate, at 320dp width and at 200 % font scale.
- [ ] Change the balance from `1 500 ₽` to `10 000 ₽` in a preview: **nothing moves horizontally**, and the card below does not move vertically.
- [ ] Let the subscription cross the 7-day line in a preview: the balance shrinks, the card grows a Display figure, the CTA lights up, **and nothing else on the screen changes**.
- [ ] Find a Russian string set in Space Grotesk. **There must not be one.**
- [ ] TalkBack through the tab: the hero reads «Осталось 5 дней» as one phrase; the switch row announces «Автопродление, включено»; every icon-only control has a name.
- [ ] Toggle reduced motion and repeat the six state screenshots. The skeleton is static at 0.7, the balance lands instantly, the meter snaps.
- [ ] Kill the network mid-session: the data stays, the bar appears, «Пополнить» and the CTA go to 0.38, «Выйти» stays live, copying the referral code still works.
- [ ] Crop the header. Do the figure face, the single lit element and the hairline ledger still identify the product?

### 19.4 The parity check, run against the desktop side by side

Every row of section 2 with «yes» in the desktop column reads «yes» in the Android column, or is
listed in section 2.1 with a named gap, an owner-visible interim, and a fix. **No silent
difference.**
