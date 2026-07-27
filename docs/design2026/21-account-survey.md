# 21 - Account surface survey (Android + Desktop)

**Status:** survey, not a spec. This file records what the Account surfaces render **today**, in what
order, in every state, what data they can actually reach, and what is weak about them. It is the
factual base the Account redesign builds on.

**Framing (owner's demand, verbatim):**

> «вкладку аккаунт всю надо переработать тоже, переделать весь дизайн стиль, там также все кнопки и в
> них стиль переделать, все надо переработать полностью под общий концепт приложений, это касается и
> пк версии и андроид версии, все надо прорабатывать, все вкладки и тп»

**Critical framing:** the desktop Account tab is **not** the reference to copy. Both platforms'
Account surfaces are subjects of this redesign. Any earlier document that treats the desktop as the
parity target is superseded on this point. The desktop is richer in *function*; it is not more correct
in *form*, and section 3 lists its own defects.

Law referenced throughout: `docs/design2026/00-rules.md` (cited as **§n**), `CLAUDE.md`,
`.claude/skills/impeccable/**`.

---

## 0. File inventory

### 0.1 Android (`/home/user/dp`, build root `V2rayNG`)

| File | Lines | Role |
|---|---|---|
| `app/src/main/java/com/v2ray/ang/ui/AccountFragment.kt` | 691 | Account tab host: profile, carousel, states, top-up, avatar, checkout polling |
| `app/src/main/res/layout/activity_account.xml` | 560 | Account tab layout (4-state hero + management card) |
| `app/src/main/java/com/v2ray/ang/ui/SubscriptionPagerAdapter.kt` | 105 | One subscription per carousel page |
| `app/src/main/res/layout/item_subscription_card.xml` | 75 | Subscription card (name, badge, expiry, devices) |
| `app/src/main/java/com/v2ray/ang/ui/BuyTariffActivity.kt` | 662 | Buy flow: catalog, selection, extra devices, checkout |
| `app/src/main/res/layout/activity_buy_tariff.xml` | 309 | Buy screen |
| `app/src/main/res/layout/item_buy_tariff.xml` | 99 | Tariff card |
| `app/src/main/res/layout/item_buy_option.xml` | 38 | Duration/price row |
| `app/src/main/java/com/v2ray/ang/ui/DeviceManagementActivity.kt` | 237 | Devices list + delete + raw-response diagnostic |
| `app/src/main/res/layout/activity_devices.xml` | 85 | Devices screen |
| `app/src/main/res/layout/item_device.xml` | 92 | Device card |
| `app/src/main/java/com/v2ray/ang/ui/adapter/DeviceAdapter.kt` | ~70 | Device row binding |
| `app/src/main/java/com/v2ray/ang/ui/PaymentHistoryActivity.kt` | 164 | History list, cache-first, pull-to-refresh |
| `app/src/main/res/layout/activity_payment_history.xml` | 83 | History screen |
| `app/src/main/res/layout/item_payment.xml` | 97 | Payment card |
| `app/src/main/java/com/v2ray/ang/ui/adapter/PaymentsAdapter.kt` | ~110 | Payment row + status chip tinting |
| `app/src/main/java/com/v2ray/ang/ui/PaymentMethodSheet.kt` | 185 | Payment-method bottom sheet |
| `app/src/main/res/layout/sheet_payment_method.xml` | 51 | Sheet shell |
| `app/src/main/res/layout/item_payment_method.xml` | 70 | Method row |
| `app/src/main/res/layout/dialog_top_up.xml` | 28 | Top-up amount dialog body |
| `app/src/main/res/layout/layout_home_account.xml` | 155 | Home account chip (+ dead sign-out group) |
| `app/src/main/java/com/v2ray/ang/viewmodel/AccountViewModel.kt` | ~430 | State flows + repository actions |
| `app/src/main/java/com/v2ray/ang/auth/**` | - | Session, repository, API client, cache, DTOs |
| `app/src/main/res/values/strings_account.xml` / `strings_buy.xml` / `strings_devices.xml` / `strings_pay.xml` | - | Russian copy |

### 0.2 Desktop (`/home/user/v2rayN`, build root `v2rayN/v2rayN`)

| File | Lines | Role |
|---|---|---|
| `v2rayN.Desktop/Views/AccountView.axaml` | 1474 | Account tab: hero, carousel, linking, management, logout, login gate |
| `v2rayN.Desktop/Views/AccountView.axaml.cs` | 524 | Carousel drag/snap/dots, press-scale, balance crossfade, entrance stagger |
| `v2rayN.Desktop/ViewModels/AccountViewModel.cs` | 2860 | All account state, login flows, renew/upgrade/devices/linking, polls |
| `v2rayN.Desktop/Views/BuyView.axaml` | 709 | Buy screen + payment-method sheet overlay |
| `v2rayN.Desktop/Views/BuyView.axaml.cs` | 173 | Total money typesetting, card stagger, sheet Esc |
| `v2rayN.Desktop/ViewModels/BuyViewModel.cs` | 840 | Catalog, selection, stepper, checkout, poll |
| `v2rayN.Desktop/Views/DevicesView.axaml` | 491 | Devices: 5 states + in-view unlink confirm |
| `v2rayN.Desktop/Views/DevicesView.axaml.cs` | 73 | Focus/Esc/scrim |
| `v2rayN.Desktop/ViewModels/DevicesViewModel.cs` | - | Device rows, platform detection, delete |
| `v2rayN.Desktop/Views/PaymentHistoryView.axaml` | 351 | History: 4 states |
| `v2rayN.Desktop/Views/PaymentHistoryView.axaml.cs` | 40 | Back / Buy events |
| `v2rayN.Desktop/ViewModels/PaymentHistoryViewModel.cs` | - | Rows, status classes |
| `v2rayN.Desktop/Views/AccountSyncView.axaml(.cs)` | 176 / 324 | Post-login / cold-start sync overlay |
| `v2rayN.Desktop/Views/HomeAccountChip.axaml(.cs)` | 131 / 233 | Home account chip (shared wide + compact) |
| `v2rayN.Desktop/Views/SubscriptionMetaView.axaml(.cs)` | 335 / 687 | Home server-group header (duplicates traffic/expiry rendering) |
| `v2rayN.Desktop/Common/L.Account.cs`, `L.Buy.cs` | - | Russian + English copy |
| `v2rayN.Desktop/Account/**` | - | Session, repository, API client, cache, DTOs |

---

## 1. ANDROID

### 1.1 Entry and gating

- The Account tab is bottom-nav item `R.id.nav_account` (`MainActivity.kt:329`). It is a real content
  tab hosting `AccountFragment`, attached lazily once (`MainActivity.kt:439-442`, flag
  `accountFragmentAdded`).
- **The tab only exists while signed in.** `updateAccountGate()` (`MainActivity.kt:1048-1064`) hides
  `navAccount` when `AccountSession.isLoggedIn()` is false, and if the user signs out while on the
  tab it force-selects Home. A pasted subscription never unlocks it.
- The whole account header (`layout_home_account`) is also hidden when `BackendConfig.isConfigured()`
  is false, i.e. builds without a backend have no account surface at all.
- Second entry point: the Home account chip (`MainActivity.kt:993`) → `selectNav(R.id.nav_account)`.
- **There is no toolbar and no screen title on the Account tab.** Its name exists only in the bottom
  nav label.

### 1.2 Account tab - information architecture, in render order

Root: `NestedScrollView` (`scroll_root`), `paddingHorizontal=@dimen/screen_gutter` 16dp,
`paddingTop=@dimen/space_12` 12dp, `paddingBottom=@dimen/space_24` 24dp, plus a runtime bottom pad of
`96 * density` px applied in `AccountFragment.kt:114` so the last card clears the bottom nav.

**1. Profile card** (`activity_account.xml:31-187`)
`MaterialCardView`, `colorSurface`, `radius_card` 20dp, elevation 0, 1dp `colorOutlineVariant` stroke,
padding 16dp.

| Element | Spec as built | Data |
|---|---|---|
| `avatar_container` | 52dp `FrameLayout`, `selectableItemBackgroundBorderless`, contentDescription «Сменить фото» | tap → avatar options |
| avatar circle | 48dp `bg_avatar_circle` (oval, `?attr/iconTileBgBlue`) | - |
| `tv_avatar_initial` | 48dp, `textSize="20sp"`, `textStyle="bold"`, `textColor="?attr/iconTintBlue"`, literal `text="?"` | `AvatarManager.setMonogram(primary)` |
| `img_avatar` | 48dp `centerCrop`, GONE until a photo resolves | custom gallery avatar or `profile.avatarUrl` |
| `img_avatar_edit` | 18dp badge bottom\|end, `bg_avatar_edit` (oval `colorPrimary` + 2dp surface stroke), `ic_acc_camera` | tap → avatar options |
| `tv_username` | weight 1, marginStart 12dp, `maxLines=1`, ellipsize end, `TextAppearance.App.Title` | `@telegramUsername` → `telegramName` → `email` (`AccountFragment.kt:249-253`) |
| `btn_top_up` | filled `MaterialButton`, `radius_pill`, padding 16/8, icon `ic_add_24dp` 18dp, `textAllCaps=false`, **`textStyle="bold"`** | label «Пополнить» |
| balance label | Caption `colorOnSurfaceVariant`, marginTop 16dp | «Баланс» |
| `tv_balance` | `TextAppearance.App.Display` 34sp/700, `tnum`+`lnum`, maxLines 1, marginTop 4dp | `profile.balance` + symbol |
| `row_referral` | wrap_content, marginTop 12dp, clickable, cd «Скопировать код»; chip `bg_acc_chip` (surfaceVariant, radius 12) padding 12/8 Caption + 18dp `ic_copy` | «Реф-код ABC123»; hidden when `referralCode` blank |

**2. `tv_pending`** (`:190-199`) - full-width `bg_acc_chip` block, padding 12dp, Body, marginTop 12dp,
text «Платёж обрабатывается…». GONE except during post-checkout polling.

**3. Subscription slot** - a `FrameLayout` (marginTop 12dp) holding **four mutually exclusive**
children, switched by `renderHeroState()` (`AccountFragment.kt:389-406`):

| State | Condition | What renders |
|---|---|---|
| `CAROUSEL` | `subs.isNotEmpty()` | `ViewPager2` 152dp (`sub_card_height`) + dots |
| `SKELETON` | `(pendingFirstLoad \|\| loading) && profile == null` | 152dp `bg_skeleton` silhouette, 3 bars (140×18, 200×14, 120×14) |
| `ERROR` | `profile == null && error != null` | card: Title «Не удалось загрузить» + Body «Что-то пошло не так» + tonal «Повторить» |
| `EMPTY` | otherwise | card: Title «Оформите первую подписку» + Body «Пока нет активной подписки. Оформите её, чтобы подключиться.» + filled «Купить подписку» (icon `ic_acc_upgrade`) |

Carousel details: `MarginPageTransformer` 12dp; neighbour peek padding 16dp left/right only when
`count > 1` (`AccountFragment.kt:307-309`); dots rebuilt imperatively (`buildDots`), 6dp inactive /
8dp active with a 4dp gap, container visible only when `count > 1`.

Skeleton motion: alpha 0.45↔1.0, **900 ms**, `AccelerateDecelerateInterpolator`, INFINITE/REVERSE
(`AccountFragment.kt:413-430`). Reduced motion → static alpha 0.7.

**Subscription card** (`item_subscription_card.xml`, bound in `SubscriptionPagerAdapter.kt:52-91`):
card 20dp radius, 1dp stroke, padding 16dp, fixed 152dp height.
- header row: `tv_sub_name` Title weight 1 maxLines 1 + `tv_tariff_badge` chip (`bg_acc_badge`,
  `iconTileBgBlue`, `TextAppearance.App.Chip` 11sp/500, `colorPrimary`), GONE when unresolved
- `tv_sub_expiry` Subtitle marginTop 12dp - «Действует до 31.12.2026»; GONE when `expireAtIso` blank
- `tv_sub_devices` Subtitle marginTop 4dp, `tnum` - «Устройства: 1 / 3» (∞ when unlimited)

Name precedence: `displayName` → `tariffDisplayName` → `defaultLabel` → «Мои подписки».
Badge precedence: catalog by `tariffId` → catalog by `tariffPriceOptionId` → `tariffBadgeName()`
(which filters out the generic «departament vpn»); null hides the badge.

**4. Section header** «Управление» - `TextAppearance.App.Title` (16sp/700), marginTop 24dp,
marginBottom 8dp. **Not** `@style/SettingsSectionLabel`, which §3.4 names as the section-header role.

**5. Management card** - one card, three rows separated by 1dp `colorOutlineVariant` dividers at
`marginStart="72dp"`. Every row: `paddingStart/End` 16dp, `paddingVertical` 8dp,
`minHeight=@dimen/row_min_height` 56dp, 40dp tile with a 22dp glyph, title marginStart **16dp**,
optional trailing value (Subtitle 13sp) marginEnd 8dp, then an **18dp** chevron.

| Row | Tile | Title | Trailing | Target |
|---|---|---|---|---|
| `row_buy` | `bg_icon_blue` + `ic_acc_upgrade` (`iconTintBlue`) | «Купить подписку», `textColor="?attr/colorPrimary"` | chevron only | `BuyTariffActivity` |
| `row_devices` | `bg_icon_neutral` + `ic_acc_devices` (`icon_glyph_neutral`) | «Устройства» | `tv_row_value_devices` = «1 / 3» | `DeviceManagementActivity` |
| `row_history` | `bg_icon_neutral` + `ic_acc_history` | «История платежей» | `tv_row_value_history` = «12.06.2026» | `PaymentHistoryActivity` |

Trailing values: devices = live `GET /client/devices` count over `sub.totalDevices` (∞ when
`isUnlimitedDevices()`), hidden when there is no subscription; history = latest `payment.createdAt`
formatted `dd.MM.yyyy`, hidden when there are no payments.

### 1.3 Account tab - behaviours and secondary surfaces

- **Balance count-up.** On a *changed* balance, `animateMoney()` counts from the previous figure over
  `motion_reveal` (300 ms) with `ease_out_quart`, reformatting each frame; first paint lands
  instantly; reduced motion lands instantly (`AccountFragment.kt:278-299`).
- **Avatar options.** Tap avatar or badge → `MaterialAlertDialogBuilder.setItems` with
  «Выбрать из галереи» (+ «Убрать фото» when a custom avatar exists) and a Cancel button
  (`android.R.string.cancel`). Result → toast «Аватар обновлён» or toastError «Не удалось загрузить фото».
- **Top-up.** Tap «Пополнить» → `MaterialAlertDialog` titled «Сумма пополнения» hosting
  `dialog_top_up.xml`: a single `TextInputLayout` (OutlinedBox) whose **hint is the only label**
  («Введите сумму»), `inputType=numberDecimal`, `imeOptions=actionDone`; buttons are
  `android.R.string.ok` / `android.R.string.cancel` (system «OK» / «Отмена»).
  Invalid amount → toastError «Введите корректную сумму». Valid → `PaymentMethodSheet` **without** the
  balance row (deliberate: topping up from the balance is circular, `AccountFragment.kt:535-559`).
- **Checkout.** `openCheckout()` opens the provider URL in a Custom Tab, falls back to `ACTION_VIEW`,
  else toastError «Не удалось открыть страницу оплаты». Sets `pendingPayment`; `onResume` starts
  `startPaymentPolling()`: 6 iterations × 8 s ≈ 48 s of `loadSubscriptions()` + `refreshProfile()`
  with `tv_pending` visible.
- **Errors.** Cold-load failure (no profile, no subs) → the ERROR hero card, error kept set until
  «Повторить». Otherwise → `toastError(messageFor(error))` mapping
  ServiceUnavailable/Network/Unauthorized/RateLimited/Timeout/generic. A failed purchase or top-up
  arms `awaitingPaymentError` and surfaces a `MaterialAlertDialog` titled «Ошибка оплаты» whose body
  is `"HTTP %1$s\n%2$s"` - the raw status code plus the backend detail string.
- **Referral copy** → `ClipData` + toast «Реферальный код скопирован».
- **Loads on open** (`loadAll()`): `refreshProfile`, `loadSubscriptions`, `loadPublicConfig`,
  `loadTariffs`, `loadPayments`; plus `loadDevices(rootUuid)` once the sub list lands (pre-warms
  `AccountCache` so the Devices screen opens instantly).

### 1.4 Account tab - honest critique

1. **It is a read-only dashboard for data the user cannot change.** There is no renew, no auto-renew
   toggle, no add-devices, no upgrade, no rename, no QR/connect link, no trial activation, no promo
   code. `AccountViewModel` already exposes `upgrade()`, `addDevices()`, `toggleAutoRenew()`,
   `togglePrimaryAutoRenew()`, `renameSubscription()`, `activateTrial()`, `checkPromo()`,
   and `AccountRepository` also exposes `getQr()` and `getReferralStats()` - **every one of them is
   dead code on Android**. The desktop calls most of them.
2. **The subscription card has no state.** An expired subscription renders identically to an active
   one; only the date differs. §15 requires `подписка истекает`, `подписка истекла`, `триал` to be
   designed. `SubInfoDto.isTrial` is parsed and never displayed. `account_trial_badge` («ПРОБНЫЙ»,
   ALL-CAPS, banned by §1.4.7) exists in strings and is referenced by nothing.
3. **You cannot sign out.** `AccountViewModel.logout()` exists; no view calls it. There is no «Выйти»
   anywhere in the Android app. The desktop has it on the Account tab.
4. **No «Привязать Telegram».** Owner request §0.4.9 is unfulfilled on Android. `telegramLinked`,
   `telegramUsername`, `email` are all in the profile, and `account_telegram` /
   `account_no_telegram` strings exist unused. **Worse: the Android API client has no link endpoint
   at all** (see §4.3) - the redesign cannot simply draw the CTA.
5. **Two identical boxes, no hierarchy.** Profile card and subscription card are the same recipe
   12dp apart. The squint test (§4.3) returns "two rectangles". The hero has one Display figure and
   nothing else establishing rank.
6. **Accent budget blown (§3.6, §4.3).** In the EMPTY state two filled accent buttons are on screen
   at once («Пополнить» + «Купить подписку»), plus the accent-tinted «Купить подписку» row title and
   the blue tile. §4.3 allows exactly one visually dominant primary action per screen.
7. **Fixed 152dp card.** Three lines of content in a 152dp box; nothing can be added (trial badge,
   urgency line, auto-renew state) without breaking it, and long Russian names still truncate at
   `maxLines=1`.
8. **Off-scale and off-ramp values** (§1.4.5, §5.2): `52dp` avatar container around a 48dp circle;
   `18dp` chevrons ×13 (the icon scale says 20dp inline); `14dp`/`120dp`/`140dp`/`200dp` skeleton
   bars; `72dp` divider inset; `textSize="20sp"` + `textStyle="bold"` inline on the monogram;
   `android:textStyle="bold"` on three `MaterialButton`s = synthetic bold on Space Grotesk (§5.4).
9. **Motion off-token.** The 900 ms `AccelerateDecelerateInterpolator` skeleton pulse exists in no
   motion token (§3.7 has 90/160/220/300/600 with ease-out curves only).
10. **Copy drift from §9.** Empty state should be «Подписки пока нет» / «Купите тариф, чтобы
    подключаться к серверам Departament.» / «Купить» (§9.5). It currently reads «Оформите первую
    подписку» and «Оформите её» - and §9.3 locks buying to «Купить», never «Оформить». The error card
    hard-wires «Что-то пошло не так» in XML, so the actual cause never reaches the card even though
    `messageFor()` maps five real causes (§9.4 requires cause + fix).
11. **No offline state** (§9.6, §15). Losing the network mid-session produces a toast and stale data
    with no marking.
12. **Toasts for actionable feedback** (§1.4.8): top-up success, referral copied, avatar errors.
13. **Raw HTTP codes shown to end users** in the «Ошибка оплаты» dialog (§9.4 bans visible error
    codes).
14. **Dead copy.** Unreferenced strings in `strings_account.xml`: `account_profile_title`,
    `account_sub_summary_title`, `account_subs_empty`, `account_hub_devices_sub`,
    `account_hub_buy_sub`, `account_hub_history_sub`, `account_trial_badge`, `account_auto_renew`,
    `account_upgrade`, `account_add_devices`, `account_promo_hint`, `account_trial`,
    `account_traffic`, `account_payments_more`. In `strings_buy.xml`: `buy_loading`,
    `buy_pick_duration`, `buy_balance_label`.
15. **Dash debt** (§9.7): `account_price_option` («%1$d дн. — %2$s»), `pay_method_from_balance_fmt`
    («С баланса — %1$s»), `devices_diag_empty`, `devices_diag_failed`.

### 1.5 Buy subscription (`BuyTariffActivity` + `activity_buy_tariff.xml`)

**Reached from:** Account «Купить подписку» row, Account empty-state CTA, Home empty-state
`btn_home_buy`, History empty-state CTA. Toolbar from `BaseActivity.setContentViewWithToolbar`,
title «Купить подписку».

**States** (single source of truth `renderState()`, `BuyTariffActivity.kt:159-169`):

| State | Trigger | Rendering |
|---|---|---|
| Loading | not `loaded`, no tariffs | `ll_skeleton`: three `MaterialCardView`s 76dp tall, `colorSurfaceVariant`, radius 20, 12dp gaps. No spinner, no label (deliberate, `:171-184`) |
| Error | `error != null` | `iv_state_icon` 48dp `ic_dl_info` + centred Body «Не удалось загрузить тарифы. Проверьте соединение и повторите.» + tonal «Повторить» (cornerRadius **22dp**) |
| Empty | `loaded`, no tariffs | same glyph + «Тарифы недоступны», **no retry** |
| Content | any group has tariffs | header + cards (+ checkout card when an option is picked) |
| Pending | after a browser checkout | `tv_pending` «Платёж обрабатывается…» in `colorPrimary`; poll 5 × 8 s |

Skeleton → content is a 220 ms (`motion_state`) cross-fade, reduced-motion aware
(`crossFadeSkeletonToContent`, `:254-280`).

**Content order:** «Выберите тариф» Title (marginStart 4dp) → runtime column of `item_buy_tariff`
cards → `card_checkout`.

**Tariff card** (`item_buy_tariff.xml`): radius 20, 1dp `colorOutlineVariant`, `press_scale`,
marginBottom 12dp.
- header row `header_tariff`: minHeight 56dp, padding 16dp; hidden `tv_group_emoji` (kept for
  binding, always GONE, but the activity still assigns `.text = emoji` first); `tv_tariff_name` Title
  maxLines 1; `tv_tariff_info` Subtitle marginTop **2dp** - «Устройства: 3 · Трафик: ∞»;
  `iv_check` 24dp `ic_action_done` `colorPrimary`, `INVISIBLE` until selected.
- `ll_price_options`: paddingH 16dp, paddingBottom 16dp, GONE until this tariff is selected.
- **Selection mutates geometry:** card stroke 1dp→**2dp** and `strokeColor`→`colorPrimary`
  (`:359-360`); the previously selected card is reset in a loop.

**Price option row** (`item_buy_option.xml`): marginTop 8dp, `bg_buy_option` (radius **14dp**,
transparent fill, 1dp outlineVariant), `minHeight="48dp"` literal, paddingH/V 12dp; duration Body left,
price Body **bold** `colorPrimary` right. Selected swaps the background to `bg_buy_option_selected`
(radius **20dp**, fill `#1F4C8DFF` raw hex, stroke **1.5dp** `colorPrimary`) - **the row changes
radius and stroke width on selection**, so it visibly jumps.

**Checkout card** (`card_checkout`, GONE until an option is chosen): radius 20, 1dp stroke, padding 16.
- Extra devices row (`ll_extra_devices`, hidden when `maxExtraDevices <= 0`): 40dp blue tile + Body
  «Дополнительные устройства» + Caption «+ 50 ₽» (marginTop 2dp) + `btn_dev_minus` / count / `btn_dev_plus`.
  The steppers are 40dp `Widget.Material3.Button.IconButton` with `cornerRadius="20dp"`,
  `backgroundTint="?attr/iconTileBgBlue"`, 20dp icon in `colorPrimary`; count is Title in a 28dp
  minWidth box with `marginHorizontal=4dp`. Disabled is applied imperatively as `alpha = 0.4f`
  (`setStepperEnabled`, `:616-619`) - the token is 0.38 (§7.1).
- 1dp divider, marginTop 16dp.
- Total row marginTop 12dp: «Итого» Body `colorOnSurfaceVariant` + `tv_total`
  `TextAppearance.App.Headline` (24sp) in `colorPrimary`.
- `btn_pay`: full-width, height 52dp, `cornerRadius="26dp"` (not `radius_pill`), icon `ic_acc_wallet`,
  «Оплатить», `textStyle="bold"`.

**Pay flow:** no selection → toast «Выберите срок подписки»; no methods in public config → toastError
«Способы оплаты недоступны»; else `PaymentMethodSheet` **with** a balance row labelled
«С баланса — 1 500 ₽». Balance payment → toastSuccess «Подписка оплачена» + `finish()`. Card payment →
`openCheckout` + toast «Завершите оплату в браузере». Failure → «Ошибка оплаты» dialog with the raw
HTTP code.

**Money contract worth preserving:** `currentTotal(tariff, option)` (`:444`) is the single source for
both the displayed «Итого» and the charged `amount` (`:489`), so the two can never drift.

**Critique**
- **Card-in-card** (§1.4.2): the price options live inside the tariff card, and each option row is
  itself a bordered rounded container. A bordered box inside a bordered box inside a scroll.
- **Selection moves the layout** (stroke 1→2, option radius 14→20, stroke 1→1.5). §7.1 wants the
  selected state on two axes without geometry shift.
- **Six radii on one screen**: 20 (cards), 14 (option), 20 (selected option), 22 (retry), 26 (pay),
  20 (stepper). The shape lock (§3.2) is pill / 20 / 12.
- **No purchase summary.** The checkout card never restates *which tariff* and *which period* is being
  bought - only the stepper and the total. The user pays without a written confirmation of what.
- **No trial, no promo.** `trialEnabled` (public config), `trialUsed` (profile), `activateTrial()`,
  `checkPromo()`/`activatePromo()` all exist and none is offered.
- Dead `progress_buy` `ProgressBar` (never made visible) and dead emoji slot.
- Skeleton silhouettes (three flat 76dp blocks) do not match the real card silhouette, so the swap
  still reads as a pop despite the cross-fade.
- Accent everywhere: blue tile + blue price + blue Headline total + filled blue CTA.

### 1.6 Devices (`DeviceManagementActivity` + `activity_devices.xml`)

**Reached from:** Account «Устройства» row. Toolbar «Устройства» with back.

Order: subtitle «Устройства, подключённые к вашей подписке» (Subtitle, `colorOnSurfaceVariant`,
marginH 16, marginTop 16) → `rv_devices` (paddingH 16, paddingBottom 16) → centred empty/error block.

**Device card** (`item_device.xml`): radius 20, 1dp stroke, marginBottom 8dp, `press_scale`, padding
12dp, minHeight 56dp.
- 40dp `bg_icon_blue` tile + 22dp `ic_acc_devices` (`iconTintBlue`) - the **same** glyph for every
  device regardless of platform.
- `tv_device_name` Title maxLines 1 - `deviceModel` → `platform` → «Неизвестное устройство»
- `tv_device_meta` Subtitle marginTop 4dp - «Android · Активно: 09.07.2026» (parts omitted when absent)
- `tv_device_hwid` Caption marginTop 4dp, `ellipsize="middle"` - «ID: a1b2c3…»
- `btn_device_delete` **44dp** icon button, 22dp `ic_delete_24dp` in `iconTintRed`

**States**

| State | Rendering |
|---|---|
| Loading | `showLoading()` from the base activity (no state in this layout) |
| List | cards; subtitle visible |
| Empty | centred: 64dp `bg_icon_blue` tile + 32dp glyph, Title «Нет подключённых устройств», Subtitle «Устройства появятся здесь после первого подключения к VPN.»; subtitle line hidden |
| No subscription | same block, Title «Активная подписка не найдена», hint hidden |
| Error | same block, Title «Не удалось загрузить устройства. Попробуйте позже.», hint hidden |

**Cache-first:** a fresh (<1 h) `AccountCache` entry renders with no network call; a delete
invalidates and force-refreshes.

**Delete:** `MaterialAlertDialog` «Удалить устройство?» / «Устройство «Pixel 8» будет отключено от
подписки. Продолжить?» / «Удалить» / «Отмена» → `repo.deleteDevice` → toastSuccess «Устройство
удалено» → invalidate + reload; failure → toastError.

**Diagnostic dialog:** when the parsed list is empty but the subscription claims devices, or when the
fetch fails, the app shows «Ответ сервера (диагностика)» containing the HTTP status and the raw
response body, with copy that asks the end user to *screenshot it and send it to us*
(`strings_devices.xml:20-23`). This ships to production users.

**Critique**
- The end-user diagnostic dialog is a developer tool on a customer screen. It must not survive.
- 44dp delete target (< 48dp, §7.2), and the only affordance on a device row is destructive.
- One generic glyph for all platforms; desktop already resolves Android/Apple/Windows/Router.
- No «Это устройство» marker, no device count in the toolbar - both exist on desktop.
- No loading state designed in the layout (§15 requires skeletons, not an inherited overlay).
- The raw HWID line is noise for a normal user and is the third line of every row.
- Terminology drift: Android «Удалить устройство», desktop «Отвязать устройство» (§9.3, §13 require
  one noun per concept across platforms).

### 1.7 Payment history (`PaymentHistoryActivity` + `activity_payment_history.xml`)

Toolbar «История платежей» with back. `SwipeRefreshLayout` (accent scheme colour) + `RecyclerView`
(paddingH 16, top 12, bottom 24) of `item_payment` cards. Centred block doubles as empty and error.

**Payment card**: radius 20, 1dp stroke, marginBottom 12dp, padding 16dp, minHeight 56dp; 40dp blue
tile + 22dp history glyph; `tv_payment_desc` Body maxLines 1 (`description` → `kind` → `orderId`);
`tv_payment_date` Caption marginTop **2dp** (`dd.MM.yyyy`, no time, no tabular figures); right column
`tv_payment_amount` Body **bold** and `tv_payment_status` chip (`bg_acc_chip`, paddingH 8dp, paddingV
**2dp**, Chip appearance) tinted at runtime - text at full colour, background at the same colour with
alpha `0x24`.

**Status mapping** (`PaymentsAdapter.statusStyle`):

| Raw statuses | Label | Colour |
|---|---|---|
| paid, success, succeeded, completed, confirmed | «Оплачено» | `icon_green` |
| pending, processing, new, created, waiting, in_progress | «В обработке» | `icon_orange` |
| failed, error, declined, rejected | «Ошибка» | `icon_red` |
| canceled, cancelled, expired | «Отменён» | `icon_yellow` |
| anything else | raw status verbatim | neutral surfaceVariant |

**States:** loading = a centred indeterminate `ProgressBar`; list; empty = `tv_empty` with a
`drawableTop` history glyph + `btn_history_buy` tonal «Купить подписку» (cornerRadius 22dp); error =
same block with the message swapped and the CTA hidden. Cache-first via `AccountCache` with a
`showingCache` guard that ignores the ViewModel's replayed empty seed.

**Critique**
- **Four status hues** (green/orange/red/yellow). §1.4.1 gives green = paid, red = error,
  yellow/orange = "expiring" warning only. «Отменён» in yellow invents a fifth meaning; it should be
  neutral.
- **A centred spinner over a blank screen** (§15 explicitly forbids it; the desktop already has
  skeletons for exactly this list).
- **N identical rounded rectangles that do nothing** - the uniform-card tell (§2.4.3). A payment is a
  fact, not an object you act on: this is a divided list, not a card grid.
- Numbers are not tabular (`fontFeatureSettings` absent on both the amount and the date) so the right
  column jitters and the dates do not align.
- Dates carry no time, so two payments on one day are indistinguishable and unordered to the eye.
- A **third empty-state grammar** in one app: Account uses a card, Devices a 64dp tile block, History
  a `drawableTop` on a TextView.
- No grouping by period, no receipt/detail, no filter.

### 1.8 Payment-method sheet + top-up dialog

`PaymentMethodSheet` (`sheet_payment_method.xml` + `item_payment_method.xml`): `bg_sheet_top`
(radius_sheet 24 top), 36×4 handle, Title (marginTop 12, paddingH 16), then runtime rows. Each row
carries a 1dp `colorOutlineVariant` divider **above itself**, including the first (so a divider sits
directly under the title). Row: minHeight 56dp, paddingH 16dp, paddingV 12dp, 40dp tile, label Body
marginStart 16dp, 20dp chevron. The callback survives rotation via a static keyed holder.

- The balance row (only when `balanceLabel != null`) uses a **green** tile (`bg_icon_green` +
  `icon_green`) as a deliberate differentiator - a second accent hue on a non-status element
  (§1.4.1: green is a status colour only).
- SBP is detected by string matching `"sbp"` / `"СБП"` on the id or label.
- The chevron implies "goes further", but tapping fires the payment immediately.

`dialog_top_up.xml`: one `TextInputLayout` whose **hint is the label** (§7.4 forbids
placeholder-as-label), no helper-text slot, no inline error (invalid → toast), and the actions are
system «OK» / «Отмена» (§9.2: buttons are verbs, never "OK").

### 1.9 Home account chip (`layout_home_account.xml`)

Two mutually exclusive groups in a `FrameLayout`. `group_login` (with the dismissible «Привязьте
Telegram, чтобы управлять подпиской» banner) is **permanently hidden** by
`updateAccountGate()` (`MainActivity.kt:1058`) - dead markup that still contains a `✕` **text
character used as a close glyph** in a 40dp box at `textSize="16sp"` (§1.4.4/§10: no text glyphs as
chrome). `chip_account`: `bg_card_incy`, paddingStart 12 / V 8 / End 16, `press_scale`, 36dp avatar
circle with a 16sp bold monogram, name Title, sub Caption «Управление аккаунтом», 18dp chevron.
Bound in `MainActivity.bindAccountChip()` with a *different* name precedence from the Account tab
(`telegramName` first here, `@handle` first there).

---

## 2. DESKTOP

### 2.1 Entry and gating

- `AccountView` is one of the shell's keep-alive tabs; `MainWindow` owns a **single**
  `AccountViewModel` shared with `LoginView` (`MainWindow.axaml.cs:66-70, 229-239`), so login state
  propagates between the tab and the sign-in sub-page.
- **The Account tab is always present.** When logged out, the tab itself renders a centred sign-in
  gate (`ShowLoginCta`) instead of being hidden. This is the opposite of Android's answer.
- Sub-pages are pushed on the shell stack: `BuyView`, `DevicesView`, `PaymentHistoryView`, `LoginView`
  (`MainWindow.axaml.cs:1164-1194`), each raising `BackRequested` for the host to pop.
- `AccountSyncView` is a static full-screen overlay in the shell, bound to `AccountViewModel.Shared`.

### 2.2 Account tab - information architecture, in render order

Root `Panel` with two mutually exclusive surfaces. Logged in: `ScrollViewer` → `StackPanel`
`MaxWidth="560"` `Margin="16,12,16,24"`.

**Zone 1 - HERO: one `Border.Card`, three zones, two 1px hairlines** (`AccountView.axaml:253-479`)

| Sub-zone | Contents |
|---|---|
| A identity | 48px `Border.Avatar` with a 20px bold accent monogram (`AvatarInitial`); `Username` at **Headline** (24px/700), ellipsised; `TariffCaptionText` Caption - «Тариф · Base» or «Пробный период», hidden when neither resolves |
| hairline | `Height=1`, `Brush.OutlineVariant`, Margin 0,16,0,0 |
| B money | Caption «Баланс»; `BalanceAmountText` at **Display** 34px `Font.Numeric` with `tnum,lnum,zero`; `BalanceCurrencyText` («₽») at **Headline** in `Brush.OnSurfaceVariant`, `Margin="6,0,0,4"`, bottom-aligned; `TopUpButton` `Classes="Primary"` bottom-right with icon + «Пополнить» |
| hairline | only when `HasReferral` |
| C referral | Caption «Код друга» + a `Brush.SurfaceVariant` chip (Padding 12,8, `Radius.Chip`) with the code in `Font.Numeric` (MaxWidth 180) + a 40px `IconButton40 Row` copy button, tooltip «Скопировать код» |

**Top-up flyout** (opens downward, `IncyFlyoutTheme`, Width 264, Spacing 12): Title «Пополнение
баланса» → Caption «Введите сумму в рублях — откроется страница оплаты.» → numeric `TextBox`
(watermark «Сумма, ₽», Enter bound to `TopUpCmd`) → inline `Brush.RedText` error line (`TopUpError`,
e.g. «Введите сумму больше 0») → method chips (`Button.MethodChip` in a `WrapPanel`, `.selected` =
`Brush.SelectedFill` + accent border) when 2+ methods, or a quiet caption for a single method →
Primary «Продолжить» gated on `CanTopUp`. The flyout closes **only** on success
(`TopUpCheckoutOpened`), so validation errors stay visible.

**Zone 2 - SUBSCRIPTION** (inside `EntranceGroup2`, which plays a +40 ms staggered
opacity/translateY entrance when the tab becomes active; skipped under lite)

Four mutually exclusive `Panel.subreveal` slots (each fades in over 220 ms `Ease.OutQuart`, no fade
under lite), each `Margin="0,24,0,0"`:

| State | Rendering |
|---|---|
| `ShowSkeleton` | `Card SkeletonPulse` MinHeight 208: 180px `SkelBar` + 72×24 chip placeholder, 220px bar, two 160×14 pill placeholders (mirrors the loaded silhouette so there is no CLS jump) |
| `ShowActiveSub` | the carousel, below |
| `ShowEmpty` | `Card` MinHeight 120: `Border.EmptyIcon` with the upgrade glyph, Title «Оформи первую подписку», Subtitle «Выбери тариф — оплата в рублях, подключение сразу.», Primary «Выбрать тариф» |
| `ShowError` | `Card`: neutral `Border.Tile` + alert glyph, Title «Не удалось загрузить», Subtitle `ErrorText` (mapped from `ApiError`), Primary «Повторить» |

State machine (`AccountViewModel.Recompute()`, `:2095-2135`): logged out → `ShowLoginCta` only;
otherwise subs non-empty → active; `(_pendingFirstLoad || IsLoading) && Profile == null` → skeleton;
`Profile == null && Error != null` → error; else empty.

**Carousel** (`SubCarousel`): a horizontal `ScrollViewer` of `Border.Card` items, card width computed
in code-behind (`viewport − 32` peek, min 240; full viewport when there is one card), 12px gap.
Interaction: pointer drag with a 6px threshold and pointer capture (tunnel handlers with
`handledEventsToo: true`), snap to the nearest index via a 16 ms `DispatcherTimer` tween over
`Motion.Dur.Reveal` 300 ms `Ease.OutQuint`; Left/Right keys page; dots (`Ellipse.Dot`/`.active`) and
prev/next 40px icon buttons appear only when `HasMultipleSubs`; the tween is cancelled when the tab
goes hit-test-invisible.

**Subscription card contents** (`AccountSubCard`, built in `BuildCard()` `:2152-2334`):

1. **Header** - `Name` Title («Ваша подписка» / «Подписка N» / the user's rename) + `Border.StatusChip`
   with classes `paid`/`pending`/`failed` carrying `HealthLabel` («Активна» / «Истекает» / «Истекла»)
   + a 40px kebab «Ещё» (`ShowMore` only). The tariff word is deliberately absent here - it lives once,
   on the hero identity line.
2. **Kebab flyout** - a **four-panel wizard** in a 272px `Panel`:
   - *menu*: `LinkAction` «Докупить устройства» (when `CanBuyDevices`) and «Улучшить тариф» (when
     `HasUpgradeTargets`)
   - *devices*: back chevron + Title, 40px `−` / Headline numeric count / 40px `+`, Title numeric
     estimate «≈ 150 ₽», Caption «Примерная сумма — точную посчитаем при оплате», then Tonal
     «С баланса» + Primary «Картой», both disabled while `IsDeviceBusy`
   - *upgrade targets*: back + Title + a list of Tonal buttons (name + muted price)
   - *upgrade confirm*: back + `UpgradeConfirmTitle` («Улучшить до Plus») + Title numeric
     `UpgradeConfirmDetail` («450 ₽ · +18 дн.») + Tonal «С баланса» + Primary «Картой»
3. **Meters** (`StackPanel.Meters`, Spacing 12, `.dim` → Opacity 0.5 when expired):
   - expiry line, Subtitle numeric, classes `.urgent` (`Brush.Icon.Orange`) / `.expired`
     (`Brush.RedText`): «Активна до 31.12.2026» / «Осталось 5 дн.» / «Истекла 01.06.2026» / «Бессрочно»
   - traffic pill: `Size.TrafficPill` 160 track (`Radius.Traffic` 8) with a **`LinearGradientBrush`**
     fill (white-blended accent → accent) whose width encodes usage, plus Caption numeric
     «12,4 ГБ / 100 ГБ» or «12,4 ГБ · безлимит». Shown only when the raw remnawave record exists
   - devices row: a `Button.MeterRow` (MinHeight 48, whole row clickable, trailing 16px chevron)
     with the same pill (only for the active card) and «2 / 5 устройств» / «Безлимит устройств» /
     «5 устройств» (secondary cards) → `DevicesCmd`
4. **Action** - one full-width button, `Primary` when the sub is expiring/expired (`RenewTopPrimary`),
   `Tonal` otherwise, label «Продлить», with an 18px arc spinner replacing the label while
   `IsRenewing`. With a known tariff it expands an inline payment choice (Tonal «С баланса · 1 500 ₽»
   + Primary «Оплатить картой»); without a tariff it raises the Buy intent.
5. **Auto-renew** - iOS-style `ToggleSwitch` + Subtitle numeric caption: «Продлится 03.06 — спишем
   150 ₽» / «Продлится 03.06» / «Автопродление включено» / «Автопродление выключено» /
   «Включите автопродление, чтобы не прерывать» (the nudge only when expiring and off).

**Zone 3 - «Способы входа»** (`ShowLinking`): `SectionHeader` + one `Border.Card` (Padding 0,
`ClipToBounds`) of four 56px rows with 72px-inset hairlines:

| Row | Tile | Right side |
|---|---|---|
| Telegram | blue | linked → muted `@handle` + neutral 16px check; pending → `Border.CodePill` with the link code + `LinkAction` «Открыть бота»; else `LinkAction` «Привязать» |
| Google | neutral | linked → email + check; else a **disabled** `LinkAction` «Скоро» |
| «Email и пароль» | neutral | linked → email + check; else `LinkAction` «Добавить» with a flyout (Title «Привязать почту», Caption hint, `TextBox`, Primary «Отправить») |
| «Веб-кабинет» | neutral | `LinkAction` «Открыть» → SSO handoff |

**Zone 4 - «Управление»**: `SectionHeader` + `Border.Card` (Padding 0) with two `Border.Row`s
(MinHeight `Size.Row` 56, Padding 16,0, `Cursor=Hand`, `:pointerover` → `Brush.Hover`, `.pressed` →
`scale(0.99)` wired in code-behind):
- «Купить подписку» - blue tile + **accent-coloured title** + chevron
- «История платежей» - neutral tile + trailing latest-payment date in `Font.Numeric` + chevron

**«Выйти»** - a separate `Border.Row` 24px below, outside the card: neutral tile + title in
`Brush.RedText`, no fill.

**Logged-out gate** (`ShowLoginCta`, `:1403-1471`): centred `Border.Card` MaxWidth 380: 56px
`Border.Avatar` with a Telegram glyph, Title «Войди в departament», Subtitle «Через Telegram —
быстро, без пароля. Или войди по почте на сайте.», `Primary Tall` «Войти через Telegram», `Tonal`
«Войти через сайт».

**Motion:** balance crossfade (opacity 0.25→1, Y −6→0, `Motion.Dur.State`) only on a *real* change
(`Skip(1)`), suppressed when the tab is not hit-test-visible or lite; group-2 entrance stagger;
carousel snap tween. All reduced-motion gated through `MotionState.IsLite`.

### 2.3 Account tab - honest critique

1. **The two most valuable actions are three levels deep.** «Докупить устройства» and «Улучшить
   тариф» live inside a kebab flyout, on a card the user may have to drag to, behind a four-panel
   wizard. §7.6 orders the alternatives inline > expandable row > flyout > dialog; a purchase flow
   with a stepper, a price estimate and two payment buttons is not a per-item overflow action.
2. **One decision, two grammars.** Choosing how to pay is inline Tonal+Primary buttons here (three
   separate places: renew, devices, upgrade confirm) and a bottom-sheet chevron list on `BuyView`.
   §1.3 product ban: "if the save button looks different in two places, one is wrong".
3. **Accent budget** (§3.6, §4.3). On a default logged-in view there are simultaneously: the filled
   «Пополнить», a filled «Продлить» (whenever the sub is expiring/expired), an accent-coloured
   «Купить подписку» row title, a blue Telegram tile, a blue buy tile, and an accent traffic fill.
   The allowance is one filled accent surface.
4. **A live gradient** (§6.5). `TrafficFillBrush` is a `LinearGradientBrush`. The comment argues it
   "encodes a value" - a solid accent fill of the same width encodes it identically.
5. **Raw hex in a view** (§1.4.6, mechanical check §1.5): `AccountView.axaml:65` `#3D7EF0` and `:68`
   `#3877E0` for the top-up hover/pressed states.
6. **Off-scale spacing** (§1.4.5) in `AccountView.axaml`: `Spacing="6"` ×2, `Spacing="10"`,
   `Spacing="20"`, `Margin="6,0,0,4"` on the ₽ symbol.
7. **The health chip reuses payment-status classes.** `Classes.paid/.pending/.failed` are bound to
   `IsHealthActive/IsHealthExpiring/IsHealthExpired`. One component, two meanings, and the class names
   lie about the semantics.
8. **A permanently disabled control on screen.** Google's «Скоро» is `IsEnabled="False"` and will
   never enable in this build. That is unfinished work rendered as UI, not a state (§15, §17).
9. **The hero name at Headline competes with the ₽.** Display 34 (amount) + Headline 24 (name) +
   Headline 24 (currency symbol) + two Captions inside one card. The "one Display per screen" rule
   holds, but the identity and the money fight at the same weight.
10. **A bespoke carousel.** A hand-rolled drag/snap over a `ScrollViewer` with tunnel pointer handlers
    (`handledEventsToo: true`) and a 16 ms timer tween. It works and it is reduced-motion aware, but
    §1.3 bans reinventing standard affordances, and the 6px drag threshold exists precisely because
    the control swallows the card's own button presses.
11. **No rename affordance** although the card title reads `sub.DisplayName` and the repository
    exposes `RenameSubscription`.
12. **No QR / connect-link surface** although `GetQr` and `subscriptionUrl` are both available.
13. **The logged-out gate lives inside the tab** while Android hides the tab. §13 requires one answer.
14. Section headers, hero, linking and management stack four `Card`s of the same recipe down one
    column; the linking card and the management card are visually identical, so the page reads as a
    settings list wearing an account's name.

### 2.4 Buy (`BuyView`)

Seamless `Border.SubToolbar` (56px) with a `Button.BackNav` and the title «Купить подписку» at
`Classes="Headline"`. Content `ScrollViewer` → `StackPanel MaxWidth="560" Margin="16,8,16,24"`.

**States** (all `StackPanel.reveal`, 220 ms `Ease.OutQuart` fade, none under lite):
`ShowSkeleton` (three `Border.SkelCard` 76px with `SkeletonPulse`) · `ShowError` (`Border.EmptyIcon` +
`ErrorText` + Tonal «Повторить») · `ShowEmpty` (same tile + `EmptyText`) · `ShowSuccess`
(check tile + Title «Подписка оплачена» + Subtitle «Серверы уже добавлены — можно подключаться») ·
`ShowContent`. Plus a `ShowPending` accent line «Платёж обрабатывается…».

**Content:** Title «Выберите тариф» (Margin 4,0,0,8) → `ItemsControl` of tariff cards, each entering
with a capped +40 ms stagger (max 6 steps ≈ 240 ms) → checkout card.

- `Border.TariffCard`: `Brush.Surface`, `Radius.Card` 20, **constant 1.5px** border (selection changes
  colour and fill only - no layout shift, better than Android), `.selected` → `Brush.SelectedFill` +
  `Brush.Accent`.
- `Border.TariffHeader`: MinHeight 56, Padding 16, `:pointerover` → `Brush.Hover`; name Title, info
  Subtitle numeric «Устройства: 3 · Трафик: ∞», 24px check `PathIcon.check` whose slot is always
  reserved and fades in over 150 ms.
- Options (`StackPanel.optreveal`, fade + 6px rise over 300 ms `OutQuint`): global
  `Border.PriceOption` (radius 14, MinHeight 48, 1.5px border, `.selected` → SelectedFill + accent),
  duration Body numeric left, price Body bold accent right.
- Checkout card (`Border.Card`, `ShowCheckout`): stepper row (blue `Tile`, «Дополнительные
  устройства» + `ExtraCostText`, two `Button.Stepper`s with `CanDevMinus`/`CanDevPlus`, 28px min-width
  count) → hairline → «Итого» row where the amount is Headline accent numeric and the ₽ is Title
  muted, both set from `TotalText` in code-behind and crossfaded on recompute → inline payment notice
  (`PaymentNoticeTitle` bold in **`Brush.Red`** + `PaymentNoticeBody`) → `Primary Tall` «Оплатить»
  with a wallet glyph that swaps in place for an arc spinner while `IsPaying`.
- **Payment-method sheet**: a window-bottom overlay - `Brush.Scrim` + `Border.SheetTop` (radius
  24,24,0,0) + 36×4 handle + Title «Способ оплаты» + flat rows (blue tile, green `.balance` tile,
  label numeric, chevron). Opens with a 220 ms fade + 24px slide; closes on scrim tap or Esc.

**Critique**
- Same **card-in-card** structure as Android (bordered option rows inside a bordered tariff card), and
  a **radius 14 inside a radius 20** (§3.2 shape lock).
- A **bottom sheet on desktop** (§13's translation table says the desktop equivalent of a bottom sheet
  is a flyout; a slide-up sheet at the bottom of a 900×600 window is a phone idiom), and it disagrees
  with the account tab's own inline payment buttons.
- **Error text in `Brush.Red`** (#F04452, 4.88:1) where §3.5 mandates `Brush.RedText` (#FF6069) for
  error *text*.
- **Toolbar title at Headline 24** where §4.8 specifies Title 16/700 for the seamless sub-toolbar.
  `DevicesView` and `PaymentHistoryView` repeat this.
- The **success state is a dead end**: a tile, two lines, and no way forward except Back. No
  "connect now" / "go to servers".
- No purchase summary in the checkout card - same hole as Android: the user never sees the chosen
  tariff and period restated next to the total.
- No trial, no promo, same as Android.

### 2.5 Devices (`DevicesView`)

Transparent root over the shell gradient; `DockPanel MaxWidth="560"`. Seamless toolbar: back +
«Устройства» (Headline) + a `Brush.SurfaceHigh` count chip (`CountText`, visible only with a list).
Subtitle «Устройства, подключённые к вашей подписке», hidden in the empty/no-sub/error states.

**Five mutually exclusive states**: `ShowList` · `ShowLoading` (one card with three pulsing rows of
the *same* geometry as the real rows) · `ShowEmpty` («Нет подключённых устройств» + hint) ·
`ShowNoSub` («Активная подписка не найдена» + hint + Primary «Перейти в аккаунт») · `ShowError`
(`ErrorText` + Tonal «Повторить»).

**Row**: one `Border.Card` containing all rows with a single 68px-inset hairline between them (no
nested cards); Grid `Margin="16,10"` MinHeight 56; a 40px blue `Tile` whose glyph is resolved per
platform (`IsAndroid` / `IsApple` / `IsWindows` / `IsRouter` / `IsGenericPlatform`); name Title +
a «Это устройство» chip when `IsCurrent`; meta Subtitle numeric; `HwidText` Caption numeric; a 40px
icon button with a `Brush.Red` trash glyph, tooltip «Отвязать устройство».
The current device's whole row is washed with `Brush.Tile.Blue`.

**Unlink**: an in-view confirmation - a `Border.Card ModalCard` centred on a scrim (scale 0.96→1 +
fade, 220 ms) with Title «Отвязать устройство?», body «Устройство «X» будет отключено от подписки.»,
`Tonal` «Отмена» (auto-focused) + `Destructive` «Отвязать»; both disabled while `IsDeleting`. Esc and
a scrim click cancel.

**Critique**
- `Background="#80000000"` raw hex on the scrim (`DevicesView.axaml:451`) instead of `Brush.Scrim`.
- Off-scale `Margin="0,3,0,0"` on the meta and HWID lines; `Margin="16,10"` on the row.
- The current-device highlight paints `Brush.Tile.Blue` across the row **and** uses the same brush for
  the «Это устройство» chip, so the chip dissolves into its own background, and an accent wash sits on
  a list row that is not selected (§3.6, §6.4).
- The only action on a device is destructive, and it is an unlabelled red glyph.
- Three lines per row of which the third is a raw HWID.
- §7.5 prefers undo over confirmation; this is a confirm dialog for a reversible action (the device
  simply re-registers on next connect).

### 2.6 Payment history (`PaymentHistoryView`)

Seamless toolbar (back + «История платежей» at Headline). Four states: `ShowList` ·
`ShowLoading` · `ShowEmpty` · `ShowError`.

- **List**: `ItemsControl MaxWidth="560" Margin="16,12,16,24"` of `Border.Card` rows (Margin
  0,0,0,12): blue 40px `Tile` + history glyph; `Description` Body + `Date` Caption numeric; right
  column `Amount` Body bold numeric + `Border.StatusChip` with classes `paid`/`pending`/`failed`/
  `canceled` carrying `StatusLabel` («Оплачено» / «В обработке» / «Ошибка» / «Отменён»).
- **Loading**: three hand-copied ~60-line blocks of identical skeleton markup with hard-coded widths
  160/128/144 to fake variety.
- **Empty**: a **locally hard-coded** 64×64 tile with `CornerRadius="20"` (instead of the
  `Border.EmptyIcon` class / `Size.EmptyIcon` token used elsewhere) + Body «Платежей пока нет» +
  Primary «Купить подписку» → `BuyRequested`.
- **Error**: same silhouette with a neutral tile + `ErrorText` + Tonal «Повторить». No buy CTA.

**Critique**
- Four status hues, same objection as Android.
- The skeleton is copy-pasted three times - a maintenance hazard and a guarantee the skeleton will
  drift from the row.
- The empty state hard-codes what a class already provides.
- No pull-to-refresh equivalent, no manual refresh at all (Android at least has swipe-to-refresh).
- Same "identical non-interactive cards" tell as Android.

### 2.7 Account sync overlay (`AccountSyncView`)

A full-screen overlay on `Brush.HomeGradient`, centred column MaxWidth 400 with a 16px gutter. A 64px
ring (static `Brush.OutlineVariant` track + an accent arc that spins via the global
`Ellipse.Spinner.spinning`, attached only while visible) around the 30px brand shield; Headline
«Добавляем аккаунт»; a live single-line stage caption (`SyncStageText`, ellipsised, crossfaded per
phase): «Проверяем аккаунт» → «Загружаем подписки…» → «Обновляем серверы».
On `SyncFailed` the column crossfades in place to an alert ring (`Brush.Red` glyph), Headline
«Не удалось синхронизировать», Body «Проверьте соединение и попробуйте снова.», Primary «Повторить»,
Tonal «Войти заново».

**Critique:** the strongest state surface in either app - it has a real failure path instead of an
eternal spinner. Two notes: the key naming drifts (`Account_SyncStageAccount` /
`Account_SyncSubtitle` / `Account_SyncStageServers` - the middle one is misnamed), and it is the only
account-adjacent surface painted on a gradient.

### 2.8 Home account chip / subscription meta (context)

- `HomeAccountChip`: 64px `Border.AccountChip` (Surface, 1px outline, `Radius.Card`, `Cursor=Hand`,
  hover → `SurfaceHigh`, `.pressed` → `scale(0.99)`), 40px avatar + Title name + Caption «Управление
  аккаунтом» + 18px chevron; a **skeleton variant** while the profile resolves; focusable with
  Enter/Space. Android's equivalent has a 36dp avatar, no skeleton, and no keyboard path.
- `SubscriptionMetaView` is a Home surface, not Account, but it renders the **same facts** (traffic
  pill + expiry) with a **different treatment** (11px text inside the pill, expiry as «до dd.MM.yyyy»
  / «Просрочено»). The account card puts the caption beside the pill and says «Активна до …». Two
  renderings of one fact, in one product.

---

## 3. CROSS-PLATFORM DIFF

### 3.1 Feature parity

| Capability | Android | Desktop |
|---|---|---|
| Profile identity + avatar | yes (avatar is user-changeable from gallery) | yes (monogram only, no photo, no picker) |
| Balance | yes, Display + count-up | yes, Display + muted ₽ + crossfade |
| Top up | dialog → bottom sheet | flyout with inline validation + method chips |
| Referral code display + copy | yes | yes |
| Referral stats (earned, count, %) | endpoint wired, **no UI** | endpoint wired, **no UI** |
| Subscription carousel | yes (ViewPager2, dots) | yes (custom drag/snap, dots + arrows) |
| Tariff badge | chip on the card | caption on the hero identity line |
| Health state (active/expiring/expired) | **absent** | chip + coloured expiry + dimmed meters |
| Traffic meter | **absent** | pill + gradient fill + caption |
| Device meter | text «1 / 3» | pill + text + clickable row → Devices |
| Renew | **absent** | inline CTA + inline balance/card choice + poll |
| Auto-renew toggle | **absent** (endpoints exist) | toggle + next-charge caption |
| Add devices | **absent** (endpoints exist) | kebab → stepper → estimate → pay |
| Upgrade tariff | **absent** (endpoints exist) | kebab → targets → quote → pay |
| Rename subscription | **absent** (endpoint exists) | **absent** (endpoint exists) |
| QR / connect link | **absent** (endpoint exists) | **absent** (endpoint exists) |
| Trial | **absent** (`isTrial`, `trialEnabled`, `activateTrial` exist) | caption «Пробный период» only; upgrade/top-up gated off trial |
| Promo code | **absent** (endpoints exist) | **absent** (endpoints exist) |
| Linking (Telegram / Google / email / web cabinet) | **absent, and no endpoints** | full section |
| Sign out | **absent** | «Выйти» row |
| Logged-out handling | tab hidden entirely | in-tab sign-in gate |
| Buy | activity, sheet for methods | sub-page, overlay sheet for methods |
| Devices | list + delete + raw diagnostic | 5 states, platform glyphs, current-device marker, count chip, in-view confirm |
| History | list + swipe refresh + spinner | list + skeletons, no refresh |
| Post-login sync surface | **absent** | `AccountSyncView` with a failure path |

### 3.2 Copy divergence (§13 requires identical strings for identical concepts)

| Concept | Android | Desktop |
|---|---|---|
| Remove a device | «Удалить устройство» / «Удалить устройство?» | «Отвязать устройство» / «Отвязать устройство?» |
| Empty subscription title | «Оформите первую подписку» | «Оформи первую подписку» (informal "ты") |
| Empty subscription CTA | «Купить подписку» | «Выбрать тариф» |
| Devices count on the card | «Устройства: 1 / 3» | «1 / 3 устройств» |
| Expiry | «Действует до 31.12.2026» | «Активна до 31.12.2026» / «Осталось 5 дн.» / «Истекла …» / «Бессрочно» |
| Balance row | «С баланса — 1 500 ₽» | «С баланса · 1 500 ₽» (card) / «С баланса — 1 500 ₽» (Buy) |
| Pay CTA family | «Оплатить» only | «Оплатить» / «С баланса» / «Картой» / «Оплатить картой» |
| Voice | formal «вы» throughout | mixed: «Войди», «Выбери» on the gate/empty, «вы» elsewhere |

Desktop also ships English for every key; Android has no English account strings.

### 3.3 Five independent money formatters

| Formatter | USD | unknown code |
|---|---|---|
| `AccountFragment.currencySymbol` | ₽ | ₽ |
| `BuyTariffActivity.currencySymbol` | **$** | **the raw code** |
| `PaymentsAdapter.formatMoney` | **$** | **the raw code** |
| `AccountViewModel.CurrencySymbol` (desktop) | ₽ | ₽ |
| `PaymentHistoryViewModel.CurrencySymbol` (desktop) | ₽ | ₽ |
| `BuyViewModel.CurrencySymbol` (desktop) | **$** | **the raw code** |

The same account balance can be signed `₽` on the Account tab and `$` on the Buy screen. §0.4.4 says
`₽`, always. Also: none of the six inserts a thin thousands separator or a non-breaking space
(§5.5 requires `1 290 ₽`).

---

## 4. DATA CONTRACT - what the redesign may use

**Rule: the redesign may not invent a value that is not in this section.**

### 4.1 Per-surface data availability

| Surface | Endpoint(s) | Payload actually used |
|---|---|---|
| Account hero | `GET /client/auth/me` | `id`, `email`, `balance`, `currency`/`preferredCurrency`, `telegramLinked`, `telegramId`, `telegramUsername`, `telegramName`(+6 aliases), `referralCode`, `remnawaveUuid`, `trialUsed`, `autoRenewEnabled`, `totpEnabled`, `avatarUrl`(+6 aliases); desktop only: `googleLinked`, `appleLinked`, `hasPassword` |
| Subscription list | `GET /client/subscription/all` + `GET /client/subscription` | see 4.2 |
| Tariff badge / upgrade targets | `GET /public/tariffs` | groups → `TariffDto{id,name,durationDays,trafficLimitBytes,includedDevices,pricePerExtraDevice,maxExtraDevices,price,currency,priceOptions[{id,durationDays,price,sortOrder}]}` |
| Payment methods, bot handle, site URL, trial flag | `GET /public/config` | `telegramBotUsername`, `publicAppUrl`, `siteUrl`, `plategaMethods[{id,label}]`, `trialEnabled`, `defaultReferralPercent` |
| Devices | `GET /client/devices?uuid=` | `DeviceDto{hwid, platform, deviceModel(+3 aliases), appVersion(+2), lastActiveAt(+3)}` + raw HTTP code/body |
| History | `GET /client/payments` | `PaymentDto{id, orderId, amount, currency, status, provider, kind, description, createdAt}` |
| Upgrade quote | `GET /client/subscriptions/upgrade-quote` | `{amount, effectiveDays, currency}` |
| Referral stats | `GET /client/referral-stats` | `{referralCode, referralPercent, totalReferrals, totalEarned, currency}` - **wired on both, used by neither** |
| Promo | `POST /client/promo-code/check` | `{type, discountPercent, durationDays}` - **wired on both, used by neither** |

### 4.2 Subscription fields - the traps

`SubInfoDto` merging is identical on both platforms (`AccountViewModel.mergeSubscriptions` /
`MergeSubscriptions` + `buildRootSub` / `BuildRootSub`). Read this before designing any subscription
state:

| Field | Where it really comes from | Trap |
|---|---|---|
| `type` (`root`/`secondary`), `id` | `/all` only | a primary-only account gets `items: []`, so the synthesized root has a **blank id** |
| `remnawaveUuid` | profile, or `/all` | **not** on `/all` items |
| `subscription` (raw remnawave record) | `/client/subscription` **only** | so `subscriptionUrl`, `hwidDeviceLimit`, `trafficLimitBytes`, `trafficUsed`/`userTraffic.usedTrafficBytes`, `expireAt`, `status` exist **only for the root/active subscription** |
| `tariffDisplayName` | `/client/subscription` | may be the generic «departament vpn» - both clients filter it |
| `displayName` / `defaultLabel` / `subscriptionIndex` | `/all` | user rename, else «Подписка #N» |
| `tariffId` / `tariffPriceOptionId` | `/all`, falling back to the primary payload's own `tariffId` | may be blank → the badge must be hideable and renew must be gated |
| `deviceCount` | `/all` | **extra purchased devices**, not the used count |
| `totalDevices` | `/all`, else `hwidDeviceLimit` | total slots; `<= 0` or `isUnlimitedDevices()` = ∞ |
| `connectedDevices` | `/all` | **always 0** - the live count is `GET /client/devices`.length, and only for the **active** subscription. Secondary subs have **no** live device count on either platform |
| `autoRenewEnabled` | profile for the root, `/all` for secondaries | the root's flag is on the profile, not the sub |
| `expireAtIso` | raw `expireAt`, else `/all` | year ≥ 2099 or > 10 y out = "perpetual" sentinel (desktop `IsEffectivelyPerpetual`); Android has no such handling and would print «Действует до 04.06.2099» |
| `isTrial` | `/all` root entry only | `buildRootSub` uses `rootFromAll?.isTrial ?: false` → **a primary-only account can never be detected as trial by either client** |
| `tariffPrice` / `tariffCurrency` / `renewalPrice` | `/all` + primary `autoRenewNextChargeAmount/At/Currency` | next-charge date and amount exist **only for the root** |

**Traffic is root-only.** Any design that puts a traffic meter on every subscription card will render
an empty meter for every secondary subscription.

### 4.3 Endpoint parity - what Android cannot call today

Present on desktop `IDepartamentApiClient`, **absent** from Android `DepartamentApiClient`:

- `RequestLinkTelegram()` → the link code + bot deep link. **Without this, an Android «Привязать
  Telegram» CTA (owner request §0.4.9) has no backend.** Either port the endpoint or route the CTA to
  the site.
- `RequestLinkEmail(email)`
- `CreateAppHandoff()` (SSO handoff into the web cabinet)
- `RequestMagicLink(email)`, `RequestPasswordReset(email)`
- `PayTariffPlatega(req)` (the scoped card-renew endpoint desktop uses for «Продлить»)
- `PurchaseDevices(...)` returning `AddDevicesResultDto` (Android has only the `AddDevices` variant
  returning a checkout URL)

Present on Android and desktop alike, unused by **both** UIs: `getQr`, `renameSubscription`,
`checkPromo`, `activatePromo`, `activateTrial` (desktop uses none of these three either),
`getReferralStats`.

### 4.4 Confirmation semantics that must survive

- A Platega payment is **webhook-confirmed**. Returning from the browser proves nothing. Both
  platforms therefore re-poll (`AccountFragment` 6 × 8 s, `BuyTariffActivity` 5 × 8 s, desktop
  `ScheduleRenewPoll` / `OpenCheckoutAndPoll` against `GET /client/payments` for a status in
  `{paid, success, succeeded, completed, confirmed, done}`). Any redesign of the buy/renew flow keeps
  a pending state and a poll; it must never claim success on return.
- The charged amount must remain the displayed total (`currentTotal` on Android; `TotalText` on
  desktop).
- The device top-up price shown on desktop is a **client-side estimate** (`EstimateDevicePrice`,
  volume tiers unavailable to the client) - hence the «≈» and the «Примерная сумма» note. Do not
  present it as final.

---

## 5. WHAT MUST NOT SURVIVE THE REDESIGN

Ranked. Every item is at least P1 by §17.2 (a section-1 ban or a missing §15 state).

**Both platforms**

1. Two different components for the same payment-method decision (inline buttons vs sheet).
2. Four status hues for payments (green/orange/red/yellow) and the reuse of payment-status classes
   for subscription health.
3. Card-in-card in the Buy screen, and the option row's radius/stroke changing on selection (Android).
4. Six money formatters with three different USD behaviours; no thin space, no NBSP before ₽.
5. Non-interactive payment cards laid out as a uniform card grid where a divided list belongs.
6. Copy divergence in §3.2, the informal/formal voice mix, and the dash debt in §9.7.
7. `getQr`, `renameSubscription`, `getReferralStats`, promo and trial: either design them in or
   delete the dead strings - shipping the endpoints with no surface is a decision by omission.

**Android specifically**

8. A read-only account: no renew, no auto-renew, no add-devices, no upgrade, no sign-out, no linking.
9. No subscription state at all (expiring / expired / trial), and no perpetual-expiry handling.
10. The end-user "screenshot the raw server response" diagnostic dialog, and the raw HTTP code in the
    «Ошибка оплаты» dialog.
11. Toasts for actionable feedback; the centred spinner on History; the hard-wired «Что-то пошло не
    так» error body.
12. The fixed 152dp subscription card; the 52/48 avatar mismatch; 18dp chevrons; inline
    `textSize`/`textStyle="bold"`; the `✕` text glyph in the dead home CTA.
13. Two filled accent buttons on the empty Account state.

**Desktop specifically**

14. Add-devices and upgrade buried in a four-panel kebab wizard on a draggable card.
15. Raw hex (`#3D7EF0`, `#3877E0`, `#80000000`) and off-scale spacing (3/6/10/20).
16. The traffic gradient; the accent wash on the current-device row.
17. A permanently disabled «Скоро» button; `Brush.Red` used for error text; Headline sub-toolbar
    titles.
18. The triple-pasted history skeleton and the hand-rolled empty-state tile.

**And the one question the redesign must answer deliberately, not by accident:** when the user is
signed out, does the Account destination disappear (Android today) or become a sign-in gate (desktop
today)? One product, one answer, both platforms.
