# Audit - Android account tab and the money path

**Verdict: 6/20. Does not ship.** Every one of the five `audit.native.md` dimensions is below the
floor of 3, and eleven of the twelve states `23-account-rework.md` 6.7 requires do not exist in the
code. The money path has two P0 defects that cost real money: a balance payment reports success
without reading the status the backend returned, and the charged currency is whatever the backend
says rather than `₽`.

Scope, verified file by file:
`res/layout/activity_account.xml`, `item_subscription_card.xml`, `item_device.xml`,
`item_payment.xml`, `activity_devices.xml`, `activity_payment_history.xml`, `activity_buy_tariff.xml`,
`item_buy_tariff.xml`, `item_buy_option.xml`, `sheet_payment_method.xml`, `item_payment_method.xml`,
`dialog_top_up.xml`; `ui/AccountFragment.kt`, `ui/SubscriptionPagerAdapter.kt`,
`viewmodel/AccountViewModel.kt`, `ui/DeviceManagementActivity.kt`, `ui/PaymentHistoryActivity.kt`,
`ui/BuyTariffActivity.kt`, `ui/PaymentMethodSheet.kt`, `ui/adapter/PaymentsAdapter.kt`,
`ui/adapter/DeviceAdapter.kt`, `auth/dto/PaymentDtos.kt`, `auth/dto/SubscriptionDtos.kt`.
Read for context, not audited: `ui/BaseActivity.kt`, `res/layout/activity_base.xml`,
`ui/MainActivity.kt`, `auth/AccountRepository.kt`, `res/values/{dimens,colors,styles,motion}.xml`.

Law: `docs/design2026/00-rules.md`, cited as **R§n**. Specs: `23-account-rework.md` (**A§n**),
`15-account-tab.md`, `21-account-survey.md`, `22-components.md` (**C§n**).

**Precedence note.** Where `23-account-rework.md` predates today's ratified rule bodies, R§ wins and
this audit follows R§: buttons are `@dimen/radius_button` 16, not the pill A§14 still counts;
`@dimen/meter_height` is 6, not the 4 in A§12.1; the skeleton pulses at `motion_pulse` 1000
(R§3.7, A§6.7) rather than staying static (A§13 A-8); `Size.SegmentChip` / `segment_height` 44 stays
retired (R§3.2, C§6.3). `row_text_origin` and `empty_icon_size` from A§12.1 do **not** exist in
`dimens.xml`; the shipped names are `divider_inset_start` 68 and `empty_icon` 64, and work orders
below use the shipped names.

---

## 1. Five-dimension score (R§17.1, ship bar >= 18/20, nothing below 3)

| # | Dimension | Score | The evidence that fixes the number |
|---|---|---|---|
| 1 | Accessibility | **1** | Icon-only unlink button is 44x44 (`item_device.xml:80-81`), under the 48dp floor (R§7.2). No focusable control in any of the twelve layouts draws a focus state - zero `state_focused`, zero `stroke_focus`, zero `@drawable/bg_row` (R§7.1 R7, mandatory). `«∞»` as the device limit (`strings_account.xml:40`, `strings_buy.xml:16`) is a glyph the vendored faces do not guarantee and TalkBack reads as nothing. `item_subscription_card.xml:12` is `layout_height="match_parent"` inside a fixed `@dimen/sub_card_height` 152dp pager (`activity_account.xml:297`), so at font scale 200% the expiry and device lines clip (R§14.5, P1). The balance is `maxLines="1" ellipsize="end"` at Display 34sp (`activity_account.xml:147-150`), so a 12-digit balance ellipsises the money instead of the label (A§4.1). Two credits: every icon-only control carries a `contentDescription`, and payment status carries a word as well as a hue. |
| 2 | Performance | **2** | All three adapters call `notifyDataSetChanged()` on a visible list with no `DiffUtil` and no stable ids (`SubscriptionPagerAdapter.kt:33`, `PaymentsAdapter.kt:26`, `DeviceAdapter.kt:25`), and `AccountFragment.kt:206` + `:213` fire it again on every `tariffs` and `deviceCount` emission - R§11.5 names this. The Buy catalogue is a non-virtualised `LinearLayout` of inflated cards inside a `NestedScrollView` (`activity_buy_tariff.xml:141-145`, built at `BuyTariffActivity.kt:226-235`), which R§4.6 makes a P1 once the catalogue can exceed ~20 rows. Credits: devices and history are real `RecyclerView`s, `AccountCache` is cache-first, and `subsJob`/`devicesJob` are latest-wins. |
| 3 | Appearance and theming | **1** | Zero raw hex in layouts - the one clean grep. Everything else is off-system: **four** button corner radii in play (20, 22, 26, `radius_pill`) and **not one** is `@dimen/radius_button` 16 (R§3.2 D-6); **12** `MaterialCardView` declarations where the tab is allowed one (R§4.4, A§5.3); **8** blue icon tiles where A§13 A-5 allows zero; **4** status hues where A§6.10 allows two plus neutral; **7** `android:textStyle="bold"` synthetic bolds (R§5.4); **2** inline `android:textSize` (R§5.2). Forty-plus `Widget.Departament.*` component styles and the whole `TextAppearance.App.Numeric*` family have landed in `values/styles.xml` and **zero** of them are referenced by any file in scope. |
| 4 | Platform conformance | **1** | `Toast` used for actionable feedback in 12 call sites across `AccountFragment`, `BuyTariffActivity` and `DeviceManagementActivity` (R§1.4.8). Two dialogs print raw HTTP status codes to the customer (`AccountFragment.kt:476-503`, `BuyTariffActivity.kt:520-547`, `DeviceManagementActivity.kt:164-173`), banned by R§9.4. Device removal is a confirm dialog where R§7.5 requires undo. The subscription switcher is a `ViewPager2` carousel with hand-built dots for a list that is almost always one item (`AccountFragment.kt:341-377`), which R§1.3 calls reinventing a standard affordance. The Account destination is **deleted from the navigation bar at runtime** when the session drops (`MainActivity.kt:1186-1188`), which R§7.7 forbids and A§13 A-3 already ruled against. Three dialogs use `android.R.string.ok` as the primary label (R§9.2 bans "OK"). |
| 5 | Adaptivity | **1** | Bottom inset is the literal `(96 * density)` (`AccountFragment.kt:114`) instead of a `WindowInsetsCompat` listener - the tab's own comment admits it. Fixed control heights that must be `minHeight`: `activity_buy_tariff.xml:298` `layout_height="52dp"` on the pay CTA, `item_device.xml:80-81` 44x44, `activity_account.xml:297` `@dimen/sub_card_height` on the pager (R§3.3 R2). Primary labels are `maxLines="1"` on the subscription name, the account name and the payment description. No `values-sw600dp` treatment and no `@dimen/content_max_width` cap on any of these screens (R§11.4). |

**Total 6/20.** Every dimension is a rework, not a polish pass.

---

## 2. Mechanical greps, scoped to the files in scope

Run from `/home/user/dp/V2rayNG/app/src/main/res`, over the twelve layouts named in section 0
(`item_subscription_card.xml` included because `activity_account.xml` cannot be read without it).

| # | Check (R§1.5 / R§9.7 / A§14) | Result | Verdict |
|---|---|---|---|
| 1 | `(android:(textColor\|background\|tint\|backgroundTint\|strokeColor)\|app:tint\|app:strokeColor)="#` | **0** | clean, keep it clean |
| 2 | `textAllCaps="true"` | **0** | clean |
| 2b | `textAllCaps="false"` (redundant, the ramp already sets it) | **6** (`activity_account` 3, `activity_buy_tariff` 2, `activity_payment_history` 1) | noise, delete with the styles |
| 3 | `android:fontFamily` in a layout | **0** | clean (R§5.1 enforcement) |
| 3b | `android:textSize` in a layout | **2** - `activity_account.xml:76` `20sp`, `item_buy_tariff.xml:48` `22sp` | **defect**, R§5.2 |
| 4 | `android:textStyle="bold"` (synthetic bold) | **7** - `activity_account.xml:77,126,277,354`, `item_payment.xml:81`, `activity_buy_tariff.xml:302`, `item_buy_option.xml:36` | **defect**, R§5.4 |
| 5 | off-scale `dp`, R§1.5 allowlist | **21 hits, 6 distinct**: `18` x14, `14` x2, `26`, `76` x3, `140`, `200`, `120` | **defect**, R§1.4.5 |
| 5b | off-scale `dp`, A§14 allowlist (`0 1 4 8 12 16 24 32 40 44 48 52 56 64`) | **39 hits, 13 distinct**: `2 14 18 20 22 26 28 36 72 76 120 140 200` | the number this tab is held to |
| 5c | total `dp` literals in scope | **121** | after rework, every one that is a gap must be a `@dimen/space_*` |
| 6 | `app:cornerRadius=` literals | `20dp` x2, `22dp` x2, `26dp` x1, `@dimen/radius_pill` x3 | **defect**: 4 distinct button radii, none is 16 (R§3.2 D-6) |
| 7 | fixed `android:layout_height="Ndp"` | **28**, of which 3 are control heights that must be `minHeight` | **defect**, R§3.3 R2 |
| 8 | `<ProgressBar` (centred spinner over a blank screen) | **2** - `activity_payment_history.xml:74`, `activity_buy_tariff.xml:28` | **defect**, R§15 forbids by name |
| 9 | `MaterialCardView` opening tags | **12** (`activity_account` 4, `activity_buy_tariff` 4, `item_*` 1 each x4) | **defect**, R§4.4 / A§5.3 |
| 10 | blue / green icon tiles (`bg_icon_blue`, `bg_icon_green`, `?attr/iconTileBgBlue`) | **8** across 6 files, plus 1 in `PaymentMethodSheet.kt:77` | **defect**, A§13 A-5 requires 0 |
| 11 | `stateListAnimator` on a row | **1** - `item_payment_method.xml:19` | **defect**, R§7.1 R5 (rows step their background, they do not scale) |
| 12 | `Widget.Departament.*` style references | **0** of 40+ landed styles | **defect**, C§R15 |
| 13 | `TextAppearance.App.Numeric` / `.Numeric.Money` references | **0** | **defect**, R§5.5 / R§3.4 |
| 14 | em / en dash in `values/strings_account.xml`, `strings_pay.xml`, `strings_devices.xml` | **4** - `strings_account.xml:47`, `strings_pay.xml:8`, `strings_devices.xml:21,22` | **defect**, R§1.4.11. No translated copies of these three files exist, so 4 is the whole debt |
| 15 | `\.\.\.` (three dots for `…`) in the same three files | **0** | clean |
| 16 | emoji in the same three files | **0** | clean |

**Money and date formatter census** (not a grep from R§1.5, but the number that matters here):
**3** money formatters, all private, all different -
`AccountFragment.kt:666-684`, `BuyTariffActivity.kt:632-646`, `PaymentsAdapter.kt:85-99`.
**4** date formatters, all private copies of the same six lines -
`AccountFragment.kt:686-691`, `SubscriptionPagerAdapter.kt:99-104`, `PaymentsAdapter.kt:101-106`,
`DeviceAdapter.kt:65-70`. Thin-space thousands separators: **0**. Non-breaking spaces before `₽`:
**0**. Tabular-figure roles applied to a price: **0**.

---

## 3. Ban hits (R§1.1 absolute, R§1.3 product, R§1.4 Departament)

| Ban | Hit | Where | Severity |
|---|---|---|---|
| Identical card grids (R§1.1) | Payment history is a scroll of visually identical bordered cards, one per payment; devices likewise. A payment is a fact, not an object you act on | `item_payment.xml:8-18`, `item_device.xml:2-13`, `activity_payment_history.xml:21-31` | P1 |
| Identical card grids (R§1.1) | Buy catalogue is N identical `radius_card` tiles with title + subtitle + trailing check | `item_buy_tariff.xml:9-22` | P1 |
| Nested cards (R§1.4.2) | Not literally nested, but four sibling cards stack on the Account tab where one is allowed, and the Buy screen puts a bordered checkout card underneath a column of bordered tariff cards | `activity_account.xml:31,236,313,369`; `activity_buy_tariff.xml:105,113,121,148` | P1 |
| No emoji as UI chrome (R§1.4.4) | `tv_group_emoji` still exists in the layout and is still populated at runtime before being forced `GONE` | `item_buy_tariff.xml:43-50`, `BuyTariffActivity.kt:298-303` | P2 (dead, but it is a live binding) |
| No off-scale spacing (R§1.4.5) | 39 hits, 13 distinct values | section 2 row 5b | P1 |
| No `android:textAllCaps` and no ALL-CAPS labels (R§1.4.7, R§9.2) | The string itself is upper-case: `«ПРОБНЫЙ»` | `strings_account.xml:41` | P1 |
| No `Toast` for anything actionable (R§1.4.8) | 12 call sites | `AccountFragment.kt:226-228` and its 8 callers, `BuyTariffActivity.kt:461,467,500,553,560,564,567`, `DeviceManagementActivity.kt:199,206,214` | P1 |
| No dialog for a decision that can be inline (R§1.4.9), undo beats confirm (R§7.5) | Device removal is `MaterialAlertDialogBuilder` with `«Удалить»` / `«Отмена»`; the device re-registers on the next connect, so it is reversible | `DeviceManagementActivity.kt:184-194` | P1 |
| No dialog where inline belongs (R§1.4.9), placeholder-as-label (R§7.4) | Top-up is an `AlertDialog` whose only field uses `android:hint` as its label and `android.R.string.ok` as its verb | `dialog_top_up.xml:16`, `AccountFragment.kt:518-533` | P1 |
| No Latin UI text (R§1.4.10) | `«HTTP %1$s»` rendered to the customer | `strings_account.xml:99-100`, `strings_devices.xml:20` | P1 |
| No em-dash / en-dash (R§1.4.11) | 4 strings, plus **two Kotlin literals**: the `else ->` branch of each `showPaymentErrorDialog`'s `code` expression is a bare U+2014, and it reaches the customer through `account_payment_error_body_nodetail`. Named rather than quoted, per A§8.11, so this document stays dash-clean | `AccountFragment.kt:486`, `BuyTariffActivity.kt:530` | P1 |
| Reinventing standard affordances (R§1.3) | `ViewPager2` + hand-built dot indicator + neighbour-peek padding for a list that is 1 item in the overwhelming majority of sessions | `AccountFragment.kt:136-162,341-377`, `activity_account.xml:286-310` | P1 |
| Heavy colour on inactive states (R§1.3) | The idle unlink glyph is `?attr/iconTintRed` on every resting row | `item_device.xml:90` | P2 |
| Display fonts / roles in UI labels (R§1.3) | `«Итого»`'s value is `TextAppearance.App.Headline` in `colorPrimary` - a headline role and the accent spent on a number the user does not control | `activity_buy_tariff.xml:289-290` | P2 |
| Accent budget, one filled accent surface per screen (R§3.6) | Buy screen: filled pay CTA **and** two accent-filled stepper buttons **and** an accent `«Итого»` **and** an accent price on every option row **and** an accent stroke + check on the selected card | `activity_buy_tariff.xml:224,230,253,259,290,304`; `item_buy_option.xml:35`; `item_buy_tariff.xml:84` | P1 |
| One primary action per screen (R§4.3) | Every tariff card carries its own price rows, and the screen additionally carries the filled CTA. Selecting is a card tap, buying is the CTA - two competing "this is the action" signals | `activity_buy_tariff.xml` + `item_buy_tariff.xml` | P1 |
| Colour is never the only signal (R§6.3) | Selected price option is a background swap only (`bg_buy_option` -> `bg_buy_option_selected`); selected tariff is stroke + a check glyph, which is two channels and is fine, but the option row is one | `BuyTariffActivity.kt:384-391` | P2 |
| A colour never means two things (R§6.2) | Payment history uses **four** hues: green paid, **orange** pending, red failed, **yellow** cancelled. Amber is reserved for `«истекает»` alone (R§1.4.1); orange is not in the palette at all | `PaymentsAdapter.kt:72-83` | P1 |
| Every screen ships its states (R§1.4.13, R§15) | See section 6 | P0/P1 |

---

## 4. The card rule, counted

R§4.4 and A§5.3: **an account screen is one card (the subscription) plus rows.**

Cards visible on the Account tab at once, today: **three.**

1. `activity_account.xml:31` - the profile card (avatar, name, top-up pill, balance, referral chip).
2. `activity_account.xml:236` / `:313` / `item_subscription_card.xml:8` - whichever of the four
   mutually-exclusive hero children is showing; three of the four are cards.
3. `activity_account.xml:369` - the management card wrapping the three rows.

The management card is the clearest failure: a card exists to bound a distinct object the user acts
on as a unit (R§4.4 criterion 1). Three navigation rows are not an object; they are a group, and a
group is spacing plus a section header plus hairlines. Its 72dp divider inset
(`activity_account.xml:434,497`) does not match any token - `@dimen/divider_inset_start` is 68 -
so the hairlines do not line up with the text they separate.

**No literally nested `MaterialCardView` exists in scope.** The pattern is worse in a different way:
four sibling cards on one screen (`activity_buy_tariff.xml` has four more), which is the
identical-card-grid tell rather than the nesting ban. `activity_account.xml:2-9`'s own header comment
claims "no nested cards" as if that were the whole rule; the rule it misses is R§4.4's third clause
plus R§2.4.3.

The correct shape, per A§2: head (no card) -> optional switcher -> **the one card** -> three
tile-less row groups on the page background at a 16 text origin with `@dimen/space_16` hairline
insets.

---

## 5. The trial rule, and what the code actually does

R§ and the ecosystem note are unambiguous: `isTrial` comes from the backend and must not be inferred
from tariff name or squad, because in this deployment the trial squad **is** the paid base squad, so
squad-based detection misclassifies paying customers.

**What the code does: it reads the flag correctly and then never uses it.**

- `SubscriptionDtos.kt:54` declares `val isTrial: Boolean = false` on `SubInfoDto`.
- `AccountViewModel.kt:220` carries it through the merge: `isTrial = rootFromAll?.isTrial ?: false`.
  No inference, no name matching, no squad check. This is right and must not be "improved".
- `grep -rn "isTrial" java/` returns exactly **two** hits - the declaration and that merge line.
  **Zero UI reads it.** There is no trial badge, no trial CTA, no suppression of renew/upgrade/
  add-devices, no auto-renew suppression.

Consequences, all P1:

1. A trial user sees the same card as a paying user, with the same tariff badge resolved from the
   catalogue by `tariffId` (`AccountFragment.kt:141-145`), so a trial reads as a paid plan.
2. `«Купить тариф»` never appears for a trial account (A§6.7 state 4), so the one CTA the business
   wants is missing exactly where it matters.
3. Auto-renew is never suppressed for a trial - which is moot only because auto-renew has no UI at
   all on this tab (section 7).

**The one place inference does creep in** is the badge, and it is defensible today but fragile:
`SubInfoDto.tariffBadgeName()` (`SubscriptionDtos.kt:69-75`) filters the literal strings
`"departament vpn"` and `"departament"` out of `tariffDisplayName`. That is a *generic-service-name*
filter, not a trial detector, and its doc comment correctly explains why the raw remnawave
`productName` is excluded. Keep the filter. Do not extend it to `«Тест»`, do not add a squad check,
and do not let the badge become the trial signal - `Widget.Departament.Chip.Neutral` reading
`«Пробный»` is the trial signal, driven by `isTrial` alone.

`AuthDtos.kt:115` `trialUsed` and `PublicDtos.kt:19` `trialEnabled` are also declared and also unread;
without them the empty card cannot choose between `«Начать пробный период»` and `«Купить»` (A§6.5).

---

## 6. Per-subscription uuid scoping

A§5.2 closing paragraph: *every subscription-scoped action passes the selected subscription's own
scope, never a cached root uuid.* Three violations, all live:

| # | Defect | Evidence | Severity |
|---|---|---|---|
| 1 | The Devices sub-page is **never told which subscription it is for**. `AccountFragment.openSubScreen` starts a bare `Intent` with no `EXTRA_REMNAWAVE_UUID` | `AccountFragment.kt:170,184-186` vs `DeviceManagementActivity.kt:58-59,235` | **P0** - on a multi-subscription account the page silently shows the root subscription's devices and unlinks against the root uuid, whatever card the user was looking at |
| 2 | The device count shown on **every** carousel page is the root subscription's count. `resolveUsedDevices` ignores its `SubInfoDto` argument and returns the single `viewModel.deviceCount` | `AccountFragment.kt:147`, `SubscriptionPagerAdapter.kt:24,88-90` | P1 - secondary subscriptions display a device figure that belongs to another subscription |
| 3 | The device count is fetched for the **first** subscription only, on every list publish | `AccountFragment.kt:317` `list.firstOrNull()?.remnawaveUuid` | P1 - and `renderDevicesRowValue` (`:323-334`) reads `currentSubs.firstOrNull()` for the limit too |
| 4 | `DeviceManagementActivity.resolveActiveSub()` picks `items.firstOrNull { it.remnawaveUuid.isNotBlank() }` from the **raw** `/subscription/all` list, not the merged list. `/all` items carry no `remnawaveUuid` (documented at `SubscriptionDtos.kt:35-37`), so this predicate almost always finds nothing and the screen falls through to the profile's root uuid | `DeviceManagementActivity.kt:140-143` | P2 - dead code that hides defect 1 |

Contrast with what is done right: `AccountViewModel.addDevices` (`:353-364`) and
`renameSubscription` (`:396-398`) both take an explicit `scope` plus `id`, and `upgrade`
(`:341-351`) takes an explicit `subscriptionUuid`. **None of the three has a caller anywhere in
`ui/`.** The plumbing is scope-correct; the UI that would use it does not exist.

One more data-contract trap, already respected and worth keeping: `SubInfoDto.connectedDevices` is
documented as always 0 from `/all` (`SubscriptionDtos.kt:50-51`) and the tab correctly reads
`GET /client/devices`.length instead. `DeviceManagementActivity.kt:90` still assigns
`expectedCount = sub?.connectedDevices ?: 0` and then uses it to trigger the diagnostic dialog at
`:122` - a comparison against a field that is always 0, so the branch is dead.

---

## 7. Money: what is displayed, and what is charged

### 7.1 Three formatters, five symbols, no separators

| Formatter | Blank currency | `RUB` | `USD` | `EUR`/`KZT`/`UAH` | unknown code | thousands | before symbol |
|---|---|---|---|---|---|---|---|
| `AccountFragment.kt:666-684` | `₽` | `₽` | **`₽`** | `€`/`₸`/`₴` | `₽` | none | plain space |
| `BuyTariffActivity.kt:632-646` | **no symbol** | `₽` | **`$`** | `€`/`₸`/`₴` | **the raw code** | none | plain space |
| `PaymentsAdapter.kt:85-99` | **no symbol** | `₽` | **`$`** | `€`/`₸`/`₴` | **the raw code** | none | plain space |

Against A§4.1 and R§0.4.4, every cell above except the `RUB` column is a defect:

- Rule 1, the symbol is always `₽`: broken five ways. The unknown-code branch (`else -> currency`)
  prints the ISO code itself, so a backend that answers `"RUB "` with a stray space, or `"KGS"`,
  renders `«299 KGS»` on the buy CTA. R§0.4.4 says the code is never displayed.
- Rule 2, U+2009 thin space as the thousands separator: absent. `1290` renders `«1290 ₽»`.
- Rule 3, U+00A0 before the symbol: absent. The price wraps between the figure and the `₽`.
- Rule 4, kopecks only when non-zero, comma decimal: `String.format(Locale.US, "%.2f", …)` gives a
  **dot** decimal, and only the whole-ruble branch is suppressed correctly.
- Rule 5, tabular figures: the Numeric role is applied to **zero** price views. The Account tab's
  balance sets `fontFeatureSettings` inline (`activity_account.xml:148`) and is the only figure in
  the product with `tnum` at all; `tv_total`, `tv_option_price`, `tv_payment_amount`,
  `tv_extra_devices_cost` and the pay-sheet balance label all have none, so a price ticking from
  `999 ₽` to `1000 ₽` reflows the row.
- `TextAppearance.App.Numeric.Money` **already exists** (`styles.xml:247-249`) with `tnum`+`lnum` on
  and `zero` off exactly as D-3 requires, and it is referenced nowhere.

### 7.2 Does the displayed total equal the charged amount?

Mostly yes, and this is the one part of the money path that was thought through.
`BuyTariffActivity.currentTotal()` (`:444-445`) is the single source for both `tv_total` (`:450`) and
the request `amount` (`:489`), with the reason stated in the comment. Keep that function and its
single-source property through the rework.

Two leaks:

1. The displayed total is formatted with a currency symbol derived from `tariff.currency`, while the
   request body sends `tariff.currency.ifBlank { "RUB" }` (`:486`). A tariff carrying `"USD"` shows
   `«$»` and is charged in `RUB`. A§4.1 rule 1 resolves this correctly: ignore the code for display,
   preserve it in the body.
2. Top-up sends `currency = "RUB"` hard-coded (`AccountFragment.kt:549,556`) while the balance is
   displayed through the currency-mapping formatter. A non-RUB profile therefore reads its balance in
   `€` and tops it up in `RUB`.

### 7.3 Double payment is not prevented by construction

R§16 requires every action button to be command-gated or wrapped in the `input_debounce` 500ms guard.
`@integer/input_debounce` exists (`motion.xml`) and `grep -rn 'input_debounce' java/` returns
**zero** call sites.

Concretely, four ways to fire two orders:

| Path | Why it is reachable |
|---|---|
| `btnPay` (`BuyTariffActivity.kt:113`) | never disabled, never debounced. Tapping it twice opens the method sheet twice; the second `show()` is a no-op only by luck of the tag |
| A method row (`PaymentMethodSheet.kt:118-121`) | `onPicked?.invoke(methodId)` fires **before** `dismissAllowingStateLoss()`, and the row has `stateListAnimator` but no click guard. A fast double-tap on one row dispatches twice before the window is torn down |
| Return from the browser | `pendingPayment = true` is set at `BuyTariffActivity.kt:557` and `AccountFragment.kt:627`, but nothing disables the CTA while polling. The user comes back from a checkout that has not been webhook-confirmed yet, sees no confirmation, and pays again |
| Top-up | `AccountFragment.kt:523-530` fires `showPaymentMethodSheet(amount)` from the dialog's positive button, which can be pressed once per dialog - but the dialog itself can be reopened while the previous checkout is still pending |

Nothing in `PaymentRequestDto` (`PaymentDtos.kt:15-24`) carries an idempotency key or a client order
id, so the backend cannot de-duplicate either. **Double payment is prevented by nothing.**

### 7.4 The payment failure states that actually cost money

| Failure | What the backend gives | What the app does | Verdict |
|---|---|---|---|
| **Declined** (Platega refuses the card) | The browser shows the provider's own failure page; the app learns nothing | Nothing. `pendingPayment` polling runs for 48s and then hides the hint with **no terminal message** (`AccountFragment.kt:652-660`, `BuyTariffActivity.kt:585-593`) | **P0** - the user is left staring at a card that never changed, with no statement of what happened and no retry |
| **Cancelled** (user backs out of the checkout tab) | Same as declined | Same as declined | **P0** |
| **Timed out** | The 6x8s / 5x8s window elapses | `pendingPayment = false`, the hint goes `GONE`, no copy, no `«История»` action. A§6.7 state 9 requires `«Не удалось подтвердить оплату. Проверьте историю платежей.»` + `«История»` | **P0** |
| **Pending** (webhook not yet delivered) | `PaymentDto.status` = `pending`/`processing`/`new` | The polling loop never reads `GET /client/payments` at all - it re-fetches `/subscription/all` and `/auth/me` and infers nothing. A§9 requires polling for a status in `{paid, success, succeeded, completed, confirmed, done}` | **P1** |
| **Succeeded** (balance path) | `PaymentResultDto(status, orderId)` - `PaymentDtos.kt:33-36` | **`status` is never read.** `AccountRepository.kt:105` returns the DTO, `AccountViewModel.payWithBalance` (`:337-339`) fires `onDone()` on any HTTP 2xx, and both callers report success: `AccountFragment.kt:550-553` toasts `«Баланс пополнен»`, `BuyTariffActivity.kt:498-502` toasts `«Подписка оплачена»` **and calls `finish()`** | **P0 - the single worst defect in scope.** A 200 with `status: "failed"` or `"pending"` closes the buy screen and tells the user they own a subscription they have not bought |
| **Succeeded but not reflected** | Webhook lands after the poll window, or the balance purchase happens in `BuyTariffActivity` | `BuyTariffActivity.finish()` returns to an `AccountFragment` that refreshes **only** when `pendingPayment` is true (`AccountFragment.kt:640-643`). A balance purchase sets `pendingPayment` in neither activity, and `AccountCache` is not invalidated, so the tab replays its stale flows and shows the old subscription | **P1** |

Two further cross-wirings in the failure surface:

- `awaitingPaymentError` (`AccountFragment.kt:92`, `BuyTariffActivity.kt:63`) arms a global flag and
  then claims the **next** emission of a **shared** `_error` flow. Five loads write that flow
  (`refreshProfile`, `loadSubscriptions`, `loadPublicConfig`, `loadTariffs`, `loadPayments`), so a
  `loadPublicConfig` failure during a purchase pops `«Ошибка оплаты»` for a payment that never
  failed - and swallows the real payment error when it arrives.
- The balance branch of `AccountFragment.showPaymentMethodSheet` (`:549`) does **not** arm the flag,
  so a declined balance top-up degrades to a generic `«Что-то пошло не так»` toast.

---

## 8. State matrix

`+` implemented and correct. `~` present but wrong. `-` absent. Product gate states are R§15's list;
the four tunnel states are marked n/a because no surface in scope owns the tunnel.

| State (R§15) | Account tab | Devices | History | Buy | Payment sheet | Top-up |
|---|---|---|---|---|---|---|
| Default | `~` carousel, 3 cards, no CTA | `~` card list | `~` card list | `~` | `~` | `~` dialog |
| First run | `-` | `-` | `-` | `-` | n/a | n/a |
| Loading | `~` skeleton exists but shows **immediately** (no 300ms gate) and pulses 900ms / `AccelerateDecelerate`, off-token (`AccountFragment.kt:413-430`) | `~` toolbar `LinearProgressIndicator` (`BaseActivity.showLoading`) | `-` centred `ProgressBar` (`activity_payment_history.xml:74`) | `+` skeleton + 220ms crossfade, the best state in scope | `-` | `-` |
| Empty | `~` card, but the CTA is a pill and the copy is `«Оформите первую подписку»` | `~` title + hint, **no action** | `~` title + a `«Купить подписку»` CTA that R§9.5 says must not be there | `~` text only, no action | `-` sheet opens with zero rows if `plategaMethods` is empty | n/a |
| Error | `~` cold-load card, generic copy, no cause mapping | `~` reuses the empty block, then pops a raw-body dialog | `~` reuses the empty block | `+` glyph + copy + retry | `-` | `-` toast |
| Offline | `-` nowhere | `-` | `-` | `-` | `-` | `-` |
| Partial | `-` a failed `loadPayments` toasts over a healthy screen | n/a | n/a | `-` | n/a | n/a |
| Long content | `~` every primary label is `maxLines="1"`; the balance ellipsises the money, not the label | `~` HWID line ellipsises `middle` | `~` description `maxLines="1"` | `~` | `~` `tv_pay_title` `maxLines="1"` | `-` |
| Short content | `~` one subscription still builds a pager, sets peek padding and calls `buildDots(1)` | `+` | `+` | `+` | `+` | n/a |
| Disabled / gated | `-` see the product rows below | `~` `«Активная подписка не найдена»`, no unlock action | `-` | `-` | `-` | `-` |
| Success | `~` toast only, no 220ms state change | `~` toast | n/a | `~` toast + `finish()` | `-` | `~` toast |

| Product gate state (R§15) | Status | Evidence |
|---|---|---|
| `нет подписки` | `~` | empty card exists (`activity_account.xml:236`); copy and CTA are wrong, and there is no trial branch because `trialEnabled`/`trialUsed` are unread |
| `подписка истекает` | **`-`** | no `daysLeft` computation exists anywhere in scope. `SubscriptionPagerAdapter.kt:72-78` prints `«Действует до dd.MM.yyyy»` and stops |
| `подписка истекла` | **`-`** | an expired subscription renders identically to an active one, in `colorOnSurface`, with no CTA |
| `триал` | **`-`** | section 5 |
| `Telegram не привязан` | **`-`** | `account_no_telegram` exists (`strings_account.xml:26`) and is referenced by **no** layout and **no** Kotlin file in scope. `AccountFragment.kt:249-252` uses `telegramUsername` only to build the name, then falls through to the e-mail silently. Owner request R§0.4.9's `«Привязать Telegram»` CTA does not exist |
| `нет серверов` | n/a | Servers tab owns it |
| `подключение` / `подключено` / `отключение` / `ошибка туннеля` | n/a | Home owns them |
| `лимит устройств` | **`-`** | `account_add_devices_limit` exists (`strings_account.xml:61`) and is unreferenced. Nothing in scope reads `totalDevices` against the live count to gate anything, and `«Добавить устройства»` has no UI |
| signed out | **`~` and wrong by rule** | the destination is removed from the navigation bar and Home is force-selected (`MainActivity.kt:1186-1188`). A§13 A-3 requires the tab to stay and render a gate |
| perpetual expiry | **`-`** | no `>= 2099` / `> 3650` sentinel. The card will print `«Действует до 04.06.2099»` |
| unknown expiry | `~` | `SubscriptionPagerAdapter.kt:72-74` hides the line entirely, leaving the card with a name and a device count and nothing else |

**Eleven of the twelve states in A§6.7 are missing or wrong.** Only "short content" is arguably
handled, and only by accident.

Actions that exist in the ViewModel with **no UI at all**: `toggleAutoRenew`,
`togglePrimaryAutoRenew`, `activateTrial`, `renameSubscription`, `upgrade`, `addDevices`,
`checkPromo`. Seven of the nine account actions the tab is supposed to own are unreachable. The
auto-renew line is the one A§1.1 ranks third of four in urgency ("сколько спишется дальше") and it
is simply not on the screen.

---

## 9. Copy

Proposed strings are taken from A§8, which is already ratified; where a string in scope has no A§8
counterpart it is written here to R§9 (Russian, sentence case, hyphens only, single `…`,
«ёлочки», `₽`, no final period on labels, terminology lock R§9.3).

### 9.1 Terminology lock violations (R§9.3)

| Concept | Locked | In the code | Where |
|---|---|---|---|
| The account screen | **Аккаунт** | `«Ваш профиль»` | `strings_account.xml:10` |
| Removing a device | **отвязать** | `«Удалить устройство?»`, `«Удалить»`, `«Устройство удалено»`, `«Не удалось удалить устройство»` | `strings_devices.xml:9-15` |
| Buying | **Купить** | `«Оформите первую подписку»`, `«Оформите её»` | `strings_account.xml:33-34` |
| Money in the account | **баланс** | correct throughout | - |
| A device on the account | **устройство** | correct, but `«ID: %1$s»` exposes HWID | `strings_devices.xml:18` |

### 9.2 Strings to delete outright

`account_payment_error_body`, `account_payment_error_body_nodetail`, `account_payment_error_title`
(HTTP codes to the customer, R§9.4); `devices_diag_title`, `devices_diag_http`, `devices_diag_empty`,
`devices_diag_failed`, `devices_diag_no_body` (a diagnostic dialog on a customer screen, A§6.9);
`account_trial_badge` (`«ПРОБНЫЙ»`, ALL-CAPS); `account_unlimited` and `buy_unlimited` (both `«∞»`,
A§4.5 bans the glyph); `account_price_option` and `pay_method_from_balance_fmt` (the dash debt);
plus the unreferenced set A§8.2 already names (`account_profile_title`, `account_sub_summary_title`,
`account_subs_empty`, `account_hub_*_sub`, `account_traffic`, `account_payments_more`,
`account_active_sub*`, `account_tariffs_*`, `account_option_duration`, `account_tariff_*`,
`account_promo_*`, `buy_loading`, `buy_pick_duration`, `buy_balance_label`).

### 9.3 Replacement copy, by surface

**Card, time block** (A§4.3, the shape is constant and only the colour changes):

| State | Label | Figure + word | Detail |
|---|---|---|---|
| Active > 30 d | `Активна до` | `3` `августа` | - |
| Active 8-30 d | `Активна до` | `3` `августа` | `Осталось 24 дня` |
| Expiring 1-7 d | `Осталось` | `5` `дней` (amber) | `Активна до 3 августа` |
| Expiring today | `Истекает` | `26` `июля` (amber) | `Сегодня последний день` |
| Expired | `Истекла` | `31` `мая` (red) | - |
| Perpetual | - | `Бессрочная подписка` / `Срок не ограничен` | - |
| Unknown | - | `Срок неизвестен` / `Обновите страницу или проверьте позже` | - |

**Card CTA:** `Продлить · 450 ₽` (healthy: `Widget.Departament.Button.Secondary`; expiring, expiring
today and expired: `…Button.Primary.Tall`) · `Купить` · `Купить тариф` (trial) ·
`Начать пробный период` (empty card, when `trialEnabled && !trialUsed`) · `Выбрать тариф` (no price
resolves) · `Улучшить тариф` (`…Button.Tertiary`, `wrap_content`, left-aligned).

**Rows:** `Устройства` `2 / 5` · `Купить подписку` › · `Баланс` `1 500 ₽` · `История платежей`
`12.06.2026` · `Реферальный код` `ABC123` · `Способы входа` `Telegram, почта` ·
`Привязать Telegram` / `Управление подпиской из бота` when `telegramLinked == false` · `Выйти`.

**Errors** (R§9.4, cause + fix, no codes):
`Нет подключения к интернету. Проверьте сеть и повторите.` ·
`Сервис временно недоступен. Повторите через пару минут.` ·
`Сервер не ответил вовремя. Повторите попытку.` ·
`Слишком много запросов. Повторите через минуту.` ·
`Платёж не прошёл. Попробуйте другой способ оплаты.` ·
`Достигнут лимит устройств. Отвяжите одно из устройств.` ·
`Не удалось загрузить устройства. Проверьте сеть и повторите.` ·
`Не удалось загрузить историю. Проверьте сеть и повторите.` ·
`Что-то пошло не так. Повторите попытку.` (last resort only).

**Payment lifecycle:** `Проверяем оплату…` + `Обновить` · `Оплата прошла` ·
`Не удалось подтвердить оплату. Проверьте историю платежей.` + `История` ·
`Завершите оплату в браузере` ·
`Не удалось открыть страницу оплаты. Проверьте браузер по умолчанию.`

**Offline** (R§9.6): `Нет сети. Показаны последние данные.` + `Повторить`.

**Empty** (R§9.5): `Подписки пока нет` / `Купите тариф, чтобы подключаться к серверам Departament.`
/ `Купить` · `Платежей пока нет` / `Здесь появится история покупок и продлений.` / no action ·
`Устройств пока нет` / `Устройства появятся после первого подключения.` / no action ·
`Подписка не активна` / `Купите тариф, чтобы подключать устройства.` / `Купить` ·
`Telegram не привязан` / `Привяжите Telegram, чтобы управлять подпиской из бота.` /
`Привязать Telegram`.

**Devices:** `Отвязать устройство` (icon `contentDescription`) · `Устройство отвязано` + `Отменить`
· `Не удалось отвязать устройство. Повторите.` + `Повторить` · `Это устройство` ·
`Неизвестное устройство` · `Подключено 2 из 5` / `Подключено 2, без ограничений`.

**Top-up sheet:** `Пополнение баланса` · label `Сумма` · `Введите сумму` ·
`Сумма должна быть больше 0` · `Введите сумму цифрами`.

**Payment sheet:** `Оплата` · `Итого` · `Оплатить с баланса` + `На балансе 1 500 ₽` ·
`Не хватает 200 ₽` · `Оплатить картой` · `Оплатить через СБП` · `Примерно 150 ₽` +
`Точную сумму покажем при оплате`.

**Sign-out dialog:** `Выйти из аккаунта?` / `Подписка останется активной. Чтобы вернуться, войдите
снова.` / `Отмена` + `Выйти`.

Voice: formal «вы» throughout, which the current copy mostly honours; the two imperative-familiar
strings `«Оформите первую подписку»` and `«Пришлите скриншот, чтобы мы исправили.»` go with their
screens.

---

## 10. Work order

Ordered by severity. Every item names the file it touches and the shipped token or style it uses;
none invents a component (C§R15 - the fifteen components in `22-components.md` are the entire
vocabulary).

### Preconditions (not this tab's work, but nothing below lands without them)

1. `22-components.md` migration steps 1-4 must produce the row layouts. The **styles** have landed
   (`Widget.Departament.Row`, `.Row.Value`, `.Row.Navigation`, `.Row.Toggle`, `.Row.Destructive`,
   `.Row.Action` at `values/styles.xml:707-751`) but the **layouts** have not:
   `ls res/layout/ | grep -E '^row_'` returns nothing, and neither `layout_state_empty.xml`,
   `layout_status_bar_inline.xml` nor `res/drawable/spinner_arc.xml` exists. `Row.Ledger` (C-1) and
   the inline status bar (C-2) are still amendments on paper.
2. `@dimen/sub_card_height` 152 and `@dimen/dot_size` / `dot_size_active` / `dot_gap` are deleted
   with the carousel; check for other referents first.
3. `BaseActivity`'s toolbar: `activity_base.xml:11` uses `?attr/actionBarSize` and `:19`
   `@style/ToolbarBrandTitle` for **every** sub-page. R§4.8 and owner request R§0.4.6 want
   `@dimen/toolbar_height` 56 and `@style/TextAppearance.App.Title`. One change, three sub-pages
   fixed.

### P0

| # | Title | Files | Change | Spec | Risk |
|---|---|---|---|---|---|
| P0-1 | A balance payment reports success without reading its status | `AccountViewModel.kt:337-339`, `AccountFragment.kt:549-553`, `BuyTariffActivity.kt:490-502`, `PaymentDtos.kt:33-36` | Make `payWithBalance` resolve `PaymentResultDto.status` against the same `{paid, success, succeeded, completed, confirmed, done}` set the poll uses. Only that set fires `onDone`. `pending`/`processing` enters the polling state; anything else is a failure with `«Платёж не прошёл. Попробуйте другой способ оплаты.»`. Remove the unconditional `finish()` | A§9 "confirmation semantics", R§7.3 | Low. Additive branch on a field that already exists in the DTO |
| P0-2 | Declined, cancelled and timed-out checkouts end in silence | `AccountFragment.kt:640-661`, `BuyTariffActivity.kt:572-594` | Poll `GET /client/payments` for the issued `orderId` (it is returned by `PaymentInitDto`, `PaymentDtos.kt:26-30`, and currently discarded), 6x8s. Terminal copy on all three outcomes: `«Оплата прошла»` (green, 2s, then re-render), `«Не удалось подтвердить оплату. Проверьте историю платежей.»` + `«История»`, `«Платёж не прошёл. Попробуйте другой способ оплаты.»` + `«Повторить»`. Render it in the C-2 inline status bar, polling variant, not in a bare `TextView` | A§6.7 state 9, A§10 | Medium. Needs the C-2 component |
| P0-3 | Double payment is prevented by nothing | `BuyTariffActivity.kt:113,457-517`, `PaymentMethodSheet.kt:118-121`, `AccountFragment.kt:518-559` | Disable the CTA and every method row while a request is in flight, using the C§2.7 loading state (hold the width, hide the label, 20dp arc) rather than the disabled look; wrap every payment entry point in the `@integer/input_debounce` 500ms guard; keep the CTA disabled while `pendingPayment` is true. Dismiss the sheet **before** invoking `onPicked`, not after | R§16 "command-gated or `input_debounce`", C§2.7 | Low |
| P0-4 | The Devices page is never told which subscription it is for | `AccountFragment.kt:170,184-186`, `DeviceManagementActivity.kt:58-59,140-143` | Pass `selectedSub.remnawaveUuid` through `EXTRA_REMNAWAVE_UUID` from the selected subscription, not the root. Delete `resolveActiveSub()` - it filters `/all` on a field `/all` never returns | A§5.2 closing paragraph | Low |
| P0-5 | The currency code decides the symbol | `AccountFragment.kt:666-684`, `BuyTariffActivity.kt:632-646`, `PaymentsAdapter.kt:85-99` | One `object Money { fun format(amount: Double): String }` in `com.v2ray.ang.util`. Always `₽`; U+2009 thousands; U+00A0 before the symbol; kopecks only when non-zero, comma decimal; `0 ₽` for zero. Delete all three private copies. The backend's `currency` is preserved in the request body and ignored for display | A§4.1, R§0.4.4 | Low. Pure formatting, one new file, three deletions |

### P1

| # | Title | Files | Change | Spec | Risk |
|---|---|---|---|---|---|
| P1-1 | Eleven of twelve states are missing | `activity_account.xml`, `AccountFragment.kt` | Build A§6.7's twelve: skeleton (after 300ms, `@integer/motion_pulse` 1000 on `@interpolator/ease_standard`), loaded, empty account, trial, expired, offline, cold error with a **mapped** cause, partial, payment polling, long content, short content, gated | R§15, R§1.4.13 | High - this is the tab rework |
| P1-2 | `isTrial` is read and never used | `AccountFragment.kt`, `activity_account.xml` | Badge -> `Widget.Departament.Chip` neutral reading `«Пробный»`; CTA -> `Widget.Departament.Button.Primary.Tall` `«Купить тариф»`; hide upgrade, hide the auto-renew row and its divider together, hide `«Купить подписку»`. Driven by `SubInfoDto.isTrial` **alone** - no tariff-name check, no squad check | A§6.7 state 4, ecosystem trial rule | Low. The flag is already correct through the merge |
| P1-3 | Expiry has no health | `AccountFragment.kt`, `SubscriptionPagerAdapter.kt` (deleted) | `daysLeft = ceil((expireAt - now) / 86400)` in the device zone; `perpetual = year >= 2099 \|\| daysLeft > 3650`. Seven variants per A§4.3, one silhouette, colour as the state channel: `colorOnSurface` / `@color/color_warning_text` / `@color/color_destructive_text` | A§4.3 | Medium |
| P1-4 | Three cards where one belongs | `activity_account.xml` | Head is a 40dp `Widget.Departament.Tile` plus two text lines, no card. Row groups sit on `?attr/colorBackground` with `@style/SettingsSectionLabel` headers and 1dp `?attr/colorOutlineVariant` hairlines inset by `@dimen/space_16`. The subscription card is the only `Widget.Departament.Card` | R§4.4, A§5.3 | Medium |
| P1-5 | The carousel | `AccountFragment.kt:136-162,301-377`, `SubscriptionPagerAdapter.kt`, `activity_account.xml:286-310` | Delete `ViewPager2`, the adapter, `buildDots`, `updateDotSelection` and the dot dimens. 1 sub -> the card alone; 2-3 -> `Widget.Departament.SegmentGroup` + `Widget.Departament.Segment`; 4+ -> a `Row.Value` opening a radio sheet. Selected index lives in the ViewModel and survives rotation | A§5.2, R§11.2 | Medium |
| P1-6 | Four button radii, none of them 16 | `activity_account.xml:127,278,355`, `activity_buy_tariff.xml:75,225,254,303`, `activity_payment_history.xml:70` | Every labelled button becomes `Widget.Departament.Button.{Primary.Tall, Secondary, Tertiary, Destructive}` at `@dimen/radius_button` 16 with `insetTop`/`insetBottom` 0 and `minHeight` `@dimen/btn_height{_tall}`. `radius_pill` survives only on the two 40dp steppers, which are square | R§3.2 D-6, R§3.3 R2 | Low |
| P1-7 | Toast for actionable feedback, x12 | `AccountFragment.kt:226-228` + callers, `BuyTariffActivity.kt`, `DeviceManagementActivity.kt` | `Snackbar` anchored above the bottom navigation, with an action wherever recovery exists. The `CoordinatorLayout` host is a prerequisite (A§6.2) | R§1.4.8 | Low |
| P1-8 | HTTP codes and raw response bodies shown to the customer | `AccountFragment.kt:476-503`, `BuyTariffActivity.kt:520-547`, `DeviceManagementActivity.kt:122-133,164-173`, `strings_account.xml:97-100`, `strings_devices.xml:19-23` | Delete both dialogs and all eight strings. Map `ApiError` to A§8.6 copy; the raw body goes to `Log.w` | R§9.4 | Low |
| P1-9 | `awaitingPaymentError` claims the wrong error | `AccountFragment.kt:92,460-472`, `BuyTariffActivity.kt:63,141-149` | Delete the flag. Payment calls carry their own failure callback instead of racing a shared `_error` flow that five loaders write to | A§10 | Low |
| P1-10 | Device removal is a confirm dialog | `DeviceManagementActivity.kt:184-217` | Undo: remove the row at `@integer/motion_state` 220 fade, Snackbar `«Устройство отвязано»` + `«Отменить»` for 5s, fire `deleteDevice` on dismiss, commit immediately on leaving the screen | R§7.5, A§13 A-10 | Medium |
| P1-11 | Payment history is a card grid with four status hues | `item_payment.xml`, `PaymentsAdapter.kt` | Tile-less divided ledger, not clickable; two columns; `TextAppearance.App.Numeric.Money` on the amount, `TextAppearance.App.Caption` in the figure face on the date. Statuses collapse to `?attr/colorTertiary` / `@color/color_destructive_text` / `?attr/colorOnSurfaceVariant` - `icon_orange` and `icon_yellow` leave | A§6.10, R§6.2 | Low |
| P1-12 | Devices list is a card grid with a red idle glyph and an HWID line | `item_device.xml`, `DeviceAdapter.kt` | Tiled row at `@dimen/row_min_height` 56 with a neutral `@color/icon_tile_neutral` tile and a platform glyph; the unlink button is `Widget.Departament.Button.Icon` at 48x48 tinted `?attr/colorOnSurfaceVariant`; the HWID line goes, returning only as a 4-char disambiguator when two titles collide | A§6.9, R§6.4, R§7.2 | Low |
| P1-13 | `notifyDataSetChanged` on visible lists | `SubscriptionPagerAdapter.kt:33`, `PaymentsAdapter.kt:26`, `DeviceAdapter.kt:25`, `AccountFragment.kt:206,213` | `ListAdapter` + `DiffUtil` + stable ids | R§11.5 | Low |
| P1-14 | No focus state on any focusable control in scope | all twelve layouts | The library's `@drawable/bg_row` selector on rows; `stroke_focus` 2dp at `focus_offset` 2dp outside non-filled controls, inside filled ones in `?attr/colorOnPrimary` at 40% | R§7.1 R7, R§14.3 | Medium |
| P1-15 | Account destination removed at runtime | `MainActivity.kt:1186-1188` | The tab stays and renders `14-auth.md` §5's gate. Removal survives only for `!BackendConfig.isConfigured()`, at start-up | A§13 A-3, R§7.7 | Medium. Outside these files, but it is the tab's own gated state |
| P1-16 | `«Telegram не привязан»` and the device limit are strings with no screen | `strings_account.xml:26,61`, `AccountFragment.kt` | `«Привязать Telegram»` as a state-driven `Row.Navigation` in the `«Вход»` group; the device-limit copy as the error the add-devices path returns | R§0.4.9, R§9.4 | Low |
| P1-17 | The top-up dialog uses its hint as a label | `dialog_top_up.xml`, `AccountFragment.kt:518-533` | `sheet_top_up.xml`: `«Сумма»` label above a `Widget.Departament.TextField` with `app:suffixText="₽"` and no hint, an always-present helper slot, validation on blur, `«Отмена»` / a real verb instead of `android.R.string.ok` | R§7.4, A§6.8 | Low |
| P1-18 | Two centred `ProgressBar`s over blank screens | `activity_payment_history.xml:74`, `activity_buy_tariff.xml:28` | Skeletons shaped like the result, after 300ms. The Buy screen's skeleton already exists and is good; delete its unused spinner | R§15 | Low |

### P2

| # | Title | Files | Change | Spec | Risk |
|---|---|---|---|---|---|
| P2-1 | 39 off-scale `dp` values | all twelve layouts | Every gap becomes `@dimen/space_*`; 18dp chevrons and glyphs become `@dimen/glyph_20`; the 22dp unlink glyph becomes `@dimen/tile_glyph`; 72dp divider insets become `@dimen/divider_inset_start` 68 (tiled surfaces) or `@dimen/space_16` (the tab) | R§1.4.5 | Low |
| P2-2 | 7 synthetic bolds, 2 inline `textSize` | as listed in section 2 | Replace with the ramp role that already carries the weight | R§5.2, R§5.4 | Low |
| P2-3 | Accent budget on the Buy screen | `activity_buy_tariff.xml`, `item_buy_option.xml`, `item_buy_tariff.xml` | One filled accent surface: the pay CTA. Steppers become `Widget.Departament.Button.Icon.Filled` per C§4.4 but stop being a second accent moment beside an accent `«Итого»`; `«Итого»` becomes `Row.Ledger.strong` in `?attr/colorOnSurface`; option prices become `TextAppearance.App.Numeric.Money` in `colorOnSurface` | R§3.6, R§4.3 | Low |
| P2-4 | Imperative alpha for a disabled control | `BuyTariffActivity.kt:616-619` | `0.38` on the whole control via a `ColorStateList`, not `alpha = 0.4f` on the view | R§7.1 R6 | Low |
| P2-5 | Balance count-up uses the wrong curve | `AccountFragment.kt:293` | `@interpolator/ease_out_quint` at `@integer/motion_reveal` 300 | A§6.12 | Low |
| P2-6 | Skeleton pulse is off-token | `AccountFragment.kt:422-429` | `@integer/motion_pulse` 1000, `@interpolator/ease_standard`, 0.45 to 1.0, infinite reverse; reduced motion holds 0.7 (already correct at `:415-419`) | R§3.7 | Low |
| P2-7 | `stateListAnimator` on a sheet row | `item_payment_method.xml:19` | Rows step their background to `?attr/colorSurfaceContainerHigh`; they do not scale | R§7.1 R5, C§R5 | Low |
| P2-8 | Bottom inset is a literal | `AccountFragment.kt:113-114` | `ViewCompat.setOnApplyWindowInsetsListener` on the scroll root | R§11.3 | Low |
| P2-9 | Dead emoji binding | `item_buy_tariff.xml:43-50`, `BuyTariffActivity.kt:298-303` | Delete the view and both branches | R§1.4.4 | Low |
| P2-10 | 4 dash-carrying strings and 2 bare U+2014 Kotlin literals | `strings_account.xml:47`, `strings_pay.xml:8`, `strings_devices.xml:21,22`, `AccountFragment.kt:486`, `BuyTariffActivity.kt:530` | The first four die with their features; the two literals die with the diagnostic dialogs | R§1.4.11 | Low |
| P2-11 | `«∞»` as a device / traffic limit | `strings_account.xml:40`, `strings_buy.xml:16`, `SubscriptionPagerAdapter.kt:83-87`, `AccountFragment.kt:330` | `«Без ограничений»` | A§4.5 | Low |
| P2-12 | Dead `expectedCount` branch | `DeviceManagementActivity.kt:45,90,122` | `connectedDevices` is always 0 from `/all`; the diagnostic it gates is being deleted anyway | A§9 | Low |
| P2-13 | Name precedence disagrees with the Home chip | `AccountFragment.kt:249-252` vs `MainActivity.kt:1199-1202` | The Home chip is already correct (`display ?: handle ?: email ?: «Аккаунт»`, `MainActivity.kt:1202`); the tab prefers the `@handle` first (`AccountFragment.kt:252`). Move the tab onto the chip's chain and hoist it into one helper: `telegramName` -> `@telegramUsername` -> `email` -> `«Аккаунт»` | A§9 | Low |
| P2-14 | Non-virtualised tariff list | `activity_buy_tariff.xml:141-145`, `BuyTariffActivity.kt:226-235` | `RecyclerView` + `ListAdapter` once the catalogue can exceed ~20 rows | R§4.6 | Medium |

### P3

| # | Title | Files | Change | Spec | Risk |
|---|---|---|---|---|---|
| P3-1 | 6 redundant `textAllCaps="false"` | `activity_account.xml`, `activity_buy_tariff.xml`, `activity_payment_history.xml` | The ramp styles already set it | R§1.4.7 | Low |
| P3-2 | 4 copies of one date formatter | `AccountFragment.kt:686`, `SubscriptionPagerAdapter.kt:99`, `PaymentsAdapter.kt:101`, `DeviceAdapter.kt:65` | One helper beside `Money`; the split day-figure + month-word form for the card, `dd.MM.yyyy` in the figure face for row values | A§4.2 | Low |
| P3-3 | Stale layout header comments | `activity_account.xml:2-9`, `item_subscription_card.xml:2-7`, `item_payment.xml:2-7` | They assert compliance the files do not have ("Every state present", "ONE blue accent", "no nested cards"). Rewrite or delete with the rework | R§17.3 | Low |
| P3-4 | `«Реф-код %1$s»` prints its own label inside the value | `strings_account.xml:24` | The row title is `«Реферальный код»`, the value is the bare code | A§8.4 | Low |

---

## 11. What is already right, and must survive the rework

Naming these so the rework does not regress them:

1. **`isTrial` is trusted, not inferred.** `AccountViewModel.kt:220`. `tariffBadgeName()`'s
   `"departament vpn"` filter (`SubscriptionDtos.kt:69-75`) is a generic-service-name filter and its
   exclusion of the stale remnawave `productName` is correct and documented. Keep both; extend
   neither.
2. **Displayed total == charged amount** on the Buy path. `BuyTariffActivity.currentTotal()`
   (`:444-445`) is the single source for both the label and the request body, deliberately.
3. **Webhook-only confirmation is understood.** Both poll loops carry the comment "the tab returning
   proves nothing". The mechanism is wrong (section 7.4); the premise is right.
4. **Top-up withholds the balance row.** `AccountFragment.kt:541-546` passes `balanceLabel = null`,
   because paying for balance from balance is circular. A§6.8 preserves this by name.
5. **Latest-wins job cancellation** on `subsJob` and `devicesJob` (`AccountViewModel.kt:112-117`,
   `:291-299`), and the deliberate swallowing of a device-count failure so a secondary datum cannot
   pop an error over the tab.
6. **`AccountCache` is cache-first with a `showingCache` guard** against the ViewModel's empty seed
   replay (`PaymentHistoryActivity.kt:41,97-101`). Subtle, correct, easy to break.
7. **The root subscription merge.** `AccountViewModel.buildRootSub` (`:188-226`) reconciles three
   payloads because `/subscription/all` returns `items: []` for a primary-only account. The comments
   are the only documentation of that contract; carry them forward.
8. **Zero raw colour literals** in all twelve layouts, and zero `android:fontFamily`.
9. **The Buy screen's skeleton -> content crossfade** (`BuyTariffActivity.kt:254-280`): one loading
   signal, reduced-motion gated, content built before the fade so it never reveals a blank frame.
   It is the one state in scope that meets the bar. Its interpolator should move to
   `ease_standard` for a two-way crossfade, but the structure is right.
10. **`@anim/press_scale` has already been corrected** to 0.97 / 90 / 160 with the split
    interpolators, and its header states the R5 row-versus-object rule. The layouts just need to
    stop attaching it to rows.

---

## 12. Departament slop test (R§2.4), answered

1. **Category reflex.** Fails. Eight blue tiles, a blue row title (`activity_account.xml:420`), an
   accent `«Итого»`, accent stepper fills and an accent price on every option row put the accent far
   past the 10% budget. The screen reads as a category-default VPN wallet.
2. **Second-order reflex.** Passes by luck: no terminal green, no violet.
3. **The uniform-card tell.** Fails outright. Twelve card declarations; payment history and the
   device list are both N identical rounded rectangles where a divided list belongs.
4. **The decoration tell.** Fails. The camera badge floating on the avatar
   (`activity_account.xml:89-97`), the copy glyph beside a row that is already entirely tappable
   (`:178-184`), the carousel dots for a one-item list, and the chevron on a payment-method row that
   charges money rather than navigating (`item_payment_method.xml:62-68`).
5. **The copy tell.** Fails. `«Ваш профиль»`, `«Все ваши операции»`, `«Оформите первую подписку»`,
   and `«HTTP 502»` in a customer dialog.
6. **The state tell.** Fails. Section 8.
7. **The trust test.** No. A user who lives in Telegram and Linear taps `«Оплатить»`, is sent to a
   browser, comes back, watches a hint appear and vanish with no verdict, and cannot tell whether
   they have been charged. That is the whole audit in one sentence.
