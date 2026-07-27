# 24 - Per-tab conformance spec

**Departament VPN - every tab, every screen, both clients: what changes, in what order.**

This file answers the owner's «все надо прорабатывать, все вкладки и тп». It is the execution
document that turns the system files into a per-screen work order. It states, for **every** surface
in both clients: the verdict (restyle / rebuild / merge / delete), the header treatment, the spacing
rhythm, which component replaces what is there now, what the empty and error states become, what is
deleted, and which wave it ships in.

**Framing, non-negotiable.** The desktop Account tab is **not** the reference. Both platforms'
Account tabs are subjects of this redesign. `01-inventory-android.md` 4 row 7 ("Account tab: KEEP
(restyle lightly)") and `02-inventory-pc.md` 4.4 ("AccountView: RESTYLE") are **superseded**: the
Account family is a REBUILD on both platforms. The same supersession applies to `BuyView`,
`DevicesView` and `PaymentHistoryView`, which `02-inventory-pc.md` marks KEEP: under the shared
concept they carry defects that no token cleanup can fix (card-in-card, four status hues, a
phone bottom-sheet on desktop, Headline sub-toolbar titles), so they are RESTYLE and REBUILD as
listed in section 5.

## Precedence

1. The owner's requests, `00-rules.md` 0.4.
2. `00-rules.md` - the law.
3. `22-components.md` - the closed component vocabulary and rulings R1 to R15.
4. `10-design-system.md` - the token names used throughout this file.
5. `11-app-structure.md` - the destination set, the placement map and the delete list.
6. `12-settings.md` - the settings hub and its 17 sub-pages, row by row.
7. `32-master-plan-android.md` / `33-master-plan-pc.md` - the per-screen component trees.
8. **This file** - verdicts, conformance deltas, merge and delete lists, and the order of work.

Where a master plan gives a component tree and this file gives a verdict, both apply. Where they
disagree on a verdict, this file wins and the master plan is corrected. Where this file and
`12-settings.md` disagree about a settings row, `12-settings.md` wins.

Every `§n` below with no file name cites `00-rules.md`.

---

# PART 0 - HOW TO READ THIS FILE

## 0.1 Verdicts

| Verdict | Meaning | What the change actually contains |
|---|---|---|
| **KEEP** | Ships with token cleanup only | Swap raw values for tokens, add the missing states. No structural edit |
| **RESTYLE** | Structure is right, surface is replaced | Same screen, same order of blocks, every control swapped for a library component, every state added |
| **REBUILD** | Structure itself is wrong | Delete the file, author from the master plan. The data contract survives, the layout does not |
| **MERGE** | Its content moves into another surface | Named target. The source file is deleted in the same change |
| **DELETE** | It stops existing | Named replacement or a recorded cut |
| **WIRE** | Built and correct, referenced by nothing | Give it an entry point, then RESTYLE it |

A screen with two verdicts (for example `MERGE + DELETE`) means part of it moves and the rest goes.

## 0.2 The five header treatments

Every screen in the product uses exactly one of these. There are no other headers.

**H1 - Tab header.** Height `size_toolbar` 56. Background `color_background`. Elevation 0, no
divider at rest. No leading element: the title sits at the 16 gutter. Title `Title` 16/700 in
`color_on_surface`. Trailing: **0 or 1** `Button.Icon` at `size_icon_button` 40 (Android hit box 48
via padding), glyph 24 in `color_on_surface_variant` - never accent. A 1dp `color_outline_variant`
hairline fades in under the header over `dur_220` 220ms `ease_standard` once scroll offset > 0, and
fades out at 0. Nothing else changes on scroll: no colour, no elevation, no transform.

**H2 - Identity header.** Главная only, and it replaces H1 there. Same 56 height, same background,
same scroll hairline. `[16][36 avatar or monogram tile, radius_round][12][text column: Title 16/700
+ Subtitle 13/400][12][20 chevron][16]`. The whole row is the target and it opens Аккаунт. Signed
out it keeps the geometry and reads «Войти» / «Подписка, устройства и платежи».

**H3 - Sub-page toolbar.** The seamless bar of §4.8 and owner request 0.4.6.
`[16][24 back glyph inside a 48 touch box][16][title Title 16/700][*][0 or 1 trailing 40][16]`,
height 56, page background, elevation 0, no divider at rest, same scroll hairline as H1. A second
trailing action goes into a 40 overflow, never onto the bar.

**H4 - Sheet header.** Bottom sheet (Android) and flyout (desktop). Drag handle 36x4 at
`space_8` from the top on Android only; title `Title` 16/700 at the 16 gutter, `space_12` above and
`space_8` below; no back arrow, no close button. Dismiss is scrim tap, Esc, system Back, or drag.

**H5 - Dialog header.** The question **is** the title (`Title` 16/700), body `Body` 14/400 under it,
actions right-aligned at the bottom: Tertiary «Отмена» then Primary or Destructive carrying the
verb. No icon, no illustration, no «OK».

## 0.3 The six spacing rhythms

Named so a per-screen entry can cite one instead of re-listing gaps. All values are from
`10-design-system.md` 2.5. The rule underneath them is `layout.md`: parts of one object 4 to 12,
objects 16, sections 24, and exactly two 32s per screen at most.

| Rhythm | Used by | Vertical formula, top to bottom |
|---|---|---|
| **R1 Ledger** | Настройки hub, every settings sub-page, Серверы | header 56 · 8 · search 48 (when the screen has one) · 24 · [section header · 8 · rows at 0 with a 68-inset hairline] · 24 · [next group] · 32 |
| **R2 Object** | Аккаунт, Подписка | header 56 · 16 · identity block · 24 · the one card · 24 · [group header · 8 · rows] · 24 · [next group] · 32 |
| **R3 Feed** | Устройства, История платежей, Журнал, Файлы ресурсов | header 56 · 12 · meta line 24 · 8 · one card holding all rows at 0 with a 68-inset hairline · 32 |
| **R4 Form** | server form, provider form, routing rule, WebDAV, вход по почте, локальный прокси | header 56 · 24 · [label · 8 · field 56 · 4 · helper] blocks 16 apart · 24 between field sections · 24 · bottom CTA · 32 |
| **R5 Hero** | Главная | header 56 · 8 · status strip 40 when a condition applies · 24 · connect object 176 in a 200 frame · 16 · status line · 12 · numeric strip 44 reserved · 24 · 2 summary rows · 32 |
| **R6 Focus** | sign-in gate, sync overlay, full-screen empty and error | header 56 · 32 · Headline 24/700 · 8 · Body 14/400 capped at 60 characters · 24 · Primary.Tall · 12 · Tertiary · 12 · Tertiary · 8 · error line slot, present even when empty |

Content column: 16 gutter on phone, 24 at `sw600dp` and at desktop width >= 1000, capped at
`size_content_max` 720 and centred. Desktop `MaxWidth="560"` on Account, Buy, Devices and History
today becomes 720 like everything else.

## 0.4 The empty and error grammar

**One component, `EmptyState` (`10-design-system.md` 6.12), used for empty, error, first run and
gated states on every screen in the product.** Three grammars exist today on Android alone (a card
on Account, a 64 tile block on Devices, a `drawableTop` on History). After this spec there is one.

```
[ 64 tile, radius_object 20, color_tile_neutral, 32 glyph in color_tile_glyph_neutral ]
[ 16 ]
Title            Title 16/700, color_on_surface, centred, max 2 lines
[ 8 ]
Body             Body 14/400, color_on_surface_variant, centred, max 60 characters
[ 24 ]
[ one action ]   Primary when the action populates the screen; Secondary «Повторить» on error;
                 nothing at all when there is genuinely no action
```

Inside a list the block is centred in the content area, not in the window. It never uses an accent
tile: the tile is neutral unless the action beneath it is the screen's one lit element, in which
case the button carries the accent and the tile still does not.

Standard copy, extending §9.5. These strings are identical on both platforms.

| Situation | Title | Body | Action |
|---|---|---|---|
| No servers | `Нет серверов` | `Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.` | `Добавить провайдера` |
| Search found nothing | `Ничего не найдено` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| No subscription | `Подписки пока нет` | `Купите тариф, чтобы подключаться к серверам Departament.` | `Купить` |
| No payments | `Платежей пока нет` | `Здесь появится история покупок и продлений.` | `Купить подписку` |
| No devices | `Устройств пока нет` | `Устройства появятся после первого подключения.` | none |
| No devices, no subscription | `Активной подписки нет` | `Купите тариф, чтобы подключать устройства.` | `Купить` |
| Telegram not linked | `Telegram не привязан` | `Привяжите Telegram, чтобы управлять подпиской из бота.` | `Привязать Telegram` |
| No providers | `Провайдеров пока нет` | `Добавьте ссылку провайдера, чтобы серверы обновлялись сами.` | `Добавить провайдера` |
| No routing rules | `Правил пока нет` | `Добавьте правило или загрузите готовый набор.` | `Добавить правило` |
| No rules in a set | `В наборе нет правил` | `Добавьте первое правило для этого набора.` | `Добавить правило` |
| Empty log | `Записей пока нет` | `Журнал заполнится при следующем подключении.` | none |
| No apps found (per-app) | `Приложения не найдены` | `Попробуйте другой запрос.` | `Сбросить поиск` |
| No URL schemes | `Схемы не зарегистрированы` | `Зарегистрируйте схему, чтобы открывать ссылки в приложении.` | `Зарегистрировать` |
| No update | `Обновлений нет` | `Установлена последняя версия.` | none |
| Generic load failure | `Не удалось загрузить` | the mapped cause from §9.4, never `Что-то пошло не так` when the cause is known | `Повторить` |
| Offline | *(not an empty state)* | the persistent strip of §9.6: `Нет сети. Показаны последние данные.` | `Повторить` |

## 0.5 What "conformant" means, mechanically

A screen is on the concept when **all fourteen** are true. The per-screen lists in Parts 4 and 5
only name the deltas that are not already covered by this list; everything here is required
everywhere and is not repeated per screen.

1. **Header** is exactly one of H1 to H5, at 56, on the page background, with no divider at rest.
2. **Rhythm** is exactly one of R1 to R6. No gap outside `space_4/8/12/16/24/32`.
3. **Rows** are one of the five archetypes in `22-components.md` 8: Navigation, Value, Action,
   Toggle, Destructive. Text origin **68**. Hairline inset **68**, between rows only. Title
   `Title` 16/700. Tile 40, radius 12, **neutral** by default. One trailing element, never two.
4. **Buttons** are one of the five variants in `22-components.md` 2.1: Primary, Secondary,
   Tertiary, Destructive, Icon, plus `.Tall` and `.Filled`. Radius `radius_control` 16 (R1).
   Heights are `minHeight` 48, or 52 for the screen's one full-width CTA (R2). Labels are `Title`
   16/700 or `Title.Medium` 16/500 (R3). No outlined variant, no synthetic bold, no inline size.
5. **Fields** are the text field of `22-components.md` 4: label above at `Title.Medium`, field at
   `size_field` 52 minimum with `radius_control` 16 on `color_surface_inset`, helper or error slot
   below at `Caption` 12, present in the markup even when empty. Validate on blur. No
   placeholder-as-label anywhere.
6. **Selects** are a sheet (Android) or a flyout (desktop). Zero `Spinner`, zero `ComboBox`, zero
   single-choice `AlertDialog` survive.
7. **One accent surface** per screen, per R14. No accent row titles, no accent tile on a
   non-categorical row, no second filled accent button, no accent gradient.
8. **Every state** of §15 that applies is drawn: default, first run, loading (skeleton shaped like
   the content, after 300ms), empty, error, offline, partial, long content, short content, gated,
   success. Plus the product gates: `нет подписки`, `подписка истекает`, `подписка истекла`,
   `триал`, `Telegram не привязан`, `нет серверов`, `подключение`, `подключено`, `отключение`,
   `ошибка туннеля`, `лимит устройств`.
9. **Press** is R4: scale 0.97 on objects, background step on rows, 90ms in `ease_out_quart`,
   160ms out `ease_out_quint`. **Focus** is drawn on every focusable control (R7). **Disabled** is
   0.38 on the whole control (R6). **Loading** holds the width and swaps the label for a 20 arc
   (R8). Double-press is impossible (R9).
10. **Motion** uses only the tokens in `10-design-system.md` 2.11, honours reduced motion through
    `MotionUtils` / `MotionState.IsLite`, and adds no second hero moment.
11. **Copy** is Russian, sentence case, formal «вы», the terminology lock of §9.3, `₽`, `…`,
    «ёлочки», no dash characters, no final period on labels, no visible error codes.
12. **Numbers** use the Numeric role with `tnum`+`lnum`, thin-space thousands, non-breaking space
    before `₽`, one money formatter per platform.
13. **Tokens only.** Zero raw hex in layouts and views, zero inline text sizes, zero off-scale dp,
    zero `StaticResource` on a theme brush, zero view-local control styles.
14. **Accessibility.** 48dp Android / 32px desktop targets with 8 separation, an accessible name on
    every icon-only control, reading order matching visual order, survives font scale 200% and DPI
    200%, colour never the only signal.

## 0.6 Verdict revisions this file records

| Screen | Earlier verdict | New verdict | Why |
|---|---|---|---|
| Android Account tab | `01-inventory-android.md` 4: KEEP (restyle lightly) | **REBUILD** | The owner's demand, plus `21-account-survey.md` 1.4: a read-only dashboard with no subscription state, no sign-out, no linking, two filled accent buttons on one state, and a fixed 152 card that cannot hold what the screen must say |
| Desktop `AccountView` | `02-inventory-pc.md` 4.4: RESTYLE | **REBUILD** | A four-panel purchase wizard inside a flyout inside a hand-rolled carousel inside a scroll is four levels of containment for a flow that takes money (`21-account-survey.md` 2.3.1) |
| Desktop `BuyView` | KEEP | **RESTYLE** | Card-in-card, a radius 14 inside a radius 20, a phone bottom-sheet on desktop, error text in `Brush.Red`, Headline toolbar title, no purchase summary |
| Desktop `DevicesView` | KEEP | **RESTYLE** | Raw `#80000000`, off-scale margins, an accent wash on an unselected row, a confirm dialog for a reversible action, a raw HWID as the third line |
| Desktop `PaymentHistoryView` | KEEP | **REBUILD** | N identical non-interactive cards where a divided list belongs, four status hues, a triple-pasted skeleton, a hand-coded empty tile, no refresh |
| Android Devices | KEEP | **RESTYLE** | The end-user "screenshot the raw server response" dialog must not survive, and the delete target is 44 |
| Android Servers tab | RESTYLE | **RESTYLE (rehosted)** | Structure survives, but it moves out of `activity_main.xml` into `ServersFragment` and gains sort, sticky group headers and the rewired actions |

---

# PART 1 - THE UNIVERSAL REPLACEMENTS

These substitutions happen everywhere, in Wave 1, from the theme and the style sheet rather than
screen by screen. They are listed once here so no per-screen entry has to repeat them. Counts come
from `20-control-survey.md`.

| What exists today | Count | What replaces it | Where the replacement is defined |
|---|---|---|---|
| 33 `MaterialButton` with 5 declared heights, 5 radii, 8 `textStyle="bold"`, 6 inline `textSize`, 0 `textAppearance` | 33 | `Button.Primary` / `.Secondary` / `.Tertiary` / `.Destructive` / `.Icon` (+ `.Tall`, `.Filled`) | `22-components.md` 2; Android `materialButtonStyle` in `res/values/themes.xml` |
| 186 desktop buttons across 28 class combinations, 54 with no class, 12 with the non-existent `Success` | 186 | the same five variants | `Assets/GlobalStyles.axaml`, all 11 view-local classes deleted |
| 35 Android icon affordances across 6 box sizes, 34 of them under 48dp | 35 | `Button.Icon` 40 visual in a 48 touch box | `22-components.md` 3 |
| 65 desktop icon buttons, legacy `Button.IconButton` 32 re-declared in 10 views | 65 | `Button.Icon` at `size_icon_button` 40 | legacy class deleted from `GlobalStyles.axaml:226` |
| 99 hand-rolled clickable containers on Android, 2 shared row layouts with 0 call sites | 101 | five row layouts: `row_navigation.xml`, `row_value.xml`, `row_action.xml`, `row_toggle.xml`, `row_destructive.xml` | `22-components.md` 8 |
| 36 desktop clickable `Border`s plus `SettingRow`, `Row`, `ServerRow`, `MeterRow` | 40 | `Border.Row` + `.Value` / `.Action` / `.Toggle` / `.Destructive` | `22-components.md` 8.7 |
| 58 bare `EditText`, 7 `bg_lp_input`, 1 search pill, 4 `TextInputLayout` | 70 | one text field, `size_field` 52, `radius_control` 16, label above, helper slot | `22-components.md` 4 |
| 126 desktop `TextBox` across 3 themes, 118 of them Semi default | 126 | the same one field | `TextBox.Incy` rewritten as the only theme |
| 15 `Spinner` + 4 `AutoCompleteTextView` (Android), 66 `ComboBox` (desktop) | 85 | Select: bottom sheet with radio rows (Android), anchored flyout (desktop) | `22-components.md` 5 |
| 6 single-choice `AlertDialog` pickers on Android (Режим, Пинг, DNS, Оформление, Язык, Автообновление) | 6 | segmented control for 2 to 3 options, Select for 4+ | `12-settings.md` 3, 9.3 |
| 23 `MaterialSwitch` (5 with two independent hit targets), 57 desktop `ToggleSwitch` across 3 appearances | 80 | `Row.Toggle` owning a non-interactive switch | `22-components.md` 7, 8.5 |
| 5 chip `TextView` + shape drawable (Android), 3 desktop `Border` chip classes with 2 paddings | 8 | `Chip.Accent` / `.Status.*` / `.Neutral`, radius 12, padding 8/4 | `22-components.md` 10 |
| 50 `MaterialCardView` across 4 radii, 25 raw `20dp` | 50 | one card: `color_surface`, `radius_object` 20, 1dp `color_outline_variant`, padding 16, elevation 0 | `22-components.md` 9 |
| hand-rolled `LinearLayout` bottom bar + `nav_press.xml`, and two desktop navigations (`NavRailItem` + `BottomNavItem`) | 3 | one `BottomNavigationView` with `@menu/menu_bottom_nav`, one desktop rail with 4 items | `11-app-structure.md` 3.1, 3.2 |
| 4 status hues on payments (green / orange / red / yellow), health chips reusing payment classes | all | 3 hues; `Отменён` is neutral; `Chip.Status.Active/.Expiring/.Expired` for health | R12 |
| 7 Android icon-tile fills (56 of 65 blue), 7 desktop `Border.Tile` variants | 14 | 3: neutral (default), accent (the one lit row), destructive | R14, `10-design-system.md` 2.2 |
| `Toast` for actionable feedback, `toast_status.xml`, zero `Snackbar` | all | Snackbar with an action (Android), `Border.Toast` (desktop), plus the persistent status strip | `11-app-structure.md` 8 |
| `ToolbarBrandTitle` 20sp wordmark applied to **every** Android sub-page | ~20 | H3 with `Title` 16/700 | §4.8 |
| `Headline` 24 sub-toolbar titles on desktop (`BuyView`, `DevicesView`, `PaymentHistoryView`, all 8 settings sub-pages) | 11 | H3 with `Title` 16/700 | §4.8 |
| 6 money formatters, 3 of which sign USD as `$` | 6 | one per platform: `₽` always, thin-space thousands, NBSP before the symbol | §0.4.4, §5.5 |
| 3 chevron sizes on Android (18/20/22), 3 on desktop (16/18/22) | 51 | one: **20** | `22-components.md` 8.2 |
| 3 press recipes with 6 scales (0.99, 0.97, 0.96, 0.94, 0.92, none) | all | one: 0.97 for objects, background step for rows | R4, R5 |

**Nothing in this table is a per-screen task.** If a screen still shows one of the left-hand items
after Wave 1, the theme change was incomplete, not the screen.

---

# PART 2 - THE CONFORMANCE MATRIX

Every surface in both clients, in one table. `W` is the wave from Part 6. Header is H1 to H5 from
0.2. Rhythm is R1 to R6 from 0.3.

## 2.1 Android

| # | Surface | Primary file(s) | Verdict | H | R | W |
|---|---|---|---|---|---|---|
| A-01 | Главная | `activity_main.xml:58-517`, `layout_home_account.xml`, `layout_home_empty.xml`, `layout_subscription_meta_bar.xml` | REBUILD | H2 | R5 | 4 |
| A-02 | Серверы | `activity_main.xml` servers group, `layout_servers_header.xml`, `layout_servers_empty.xml`, `item_recycler_main.xml`, `item_section_header.xml` | RESTYLE (rehosted) | H1 | R1 | 5 |
| A-03 | Аккаунт, signed in | `AccountFragment.kt`, `activity_account.xml`, `item_subscription_card.xml`, `SubscriptionPagerAdapter.kt` | REBUILD | H1 | R2 | 3 |
| A-04 | Аккаунт, signed out (gate) | new; replaces `activity_login.xml` + `layout_home_empty.xml` | REBUILD | H1 | R6 | 3 |
| A-05 | Настройки | `layout_settings_content.xml` (1 536 ln) | REBUILD | H1 | R1 | 6 |
| A-06 | `account/signin` Вход по почте | `LoginActivity.kt`, `activity_login.xml` | REBUILD | H3 | R4 | 3 |
| A-07 | `account/subscription/{id}` Подписка | new | NEW | H3 | R2 | 3 |
| A-08 | `account/buy` Покупка | `BuyTariffActivity.kt`, `activity_buy_tariff.xml`, `item_buy_tariff.xml`, `item_buy_option.xml` | REBUILD | H3 | R1 | 3 |
| A-09 | `account/devices` Устройства | `DeviceManagementActivity.kt`, `activity_devices.xml`, `item_device.xml` | RESTYLE | H3 | R3 | 3 |
| A-10 | `account/history` История платежей | `PaymentHistoryActivity.kt`, `activity_payment_history.xml`, `item_payment.xml` | REBUILD | H3 | R3 | 3 |
| A-11 | Payment-method surface | `PaymentMethodSheet.kt`, `sheet_payment_method.xml`, `item_payment_method.xml` | RESTYLE | H4 | - | 3 |
| A-12 | Top-up | `dialog_top_up.xml` | REBUILD as a sheet | H4 | R4 | 3 |
| A-13 | `servers/server/{guid}` Сервер | `ServerActivity.kt` + 9 siblings, `activity_server_*.xml` x10, `layout_address_port.xml`, `layout_tls.xml`, `layout_tls_hysteria2.xml`, `layout_transport.xml` | REBUILD as one | H3 | R4 | 5 |
| A-14 | `servers/provider/{id}` Провайдер | `SubEditActivity.kt`, `activity_sub_edit.xml` | REBUILD | H3 | R4 | 5 |
| A-15 | Server actions sheet | `ServerActionsSheet.kt`, `sheet_server_actions.xml` | REBUILD + REWIRE | H4 | - | 5 |
| A-16 | `servers/scan` Сканер | `ScannerActivity.kt`, `activity_none.xml` | REBUILD | H3 | - | 5 |
| A-17 | Sub setting (provider list) | `SubSettingActivity.kt`, `activity_sub_setting.xml`, `item_recycler_sub_setting.xml` | MERGE + DELETE | - | - | 5 |
| A-18 | `settings/perapp` Прокси по приложениям | `PerAppProxyActivity.kt`, `activity_bypass_list.xml`, `item_recycler_bypass_list.xml` | RESTYLE | H3 | R1 | 6 |
| A-19 | App picker | `AppPickerActivity.kt`, `activity_app_picker.xml` | MERGE + DELETE | - | - | 6 |
| A-20 | `settings/routing` Маршрутизация | `RoutingSettingActivity.kt`, `activity_routing_setting.xml`, `item_recycler_routing_setting.xml` | RESTYLE | H3 | R1 | 6 |
| A-21 | `settings/routing/rule/{id}` Правило | `RoutingEditActivity.kt`, `activity_routing_edit.xml` | REBUILD | H3 | R4 | 6 |
| A-22 | `settings/dns` DNS | new; absorbs the DNS `AlertDialog` and the hidden `pref_settings` keys | NEW | H3 | R1 | 6 |
| A-23 | `settings/latency` Проверка задержки | new; absorbs the «Пинг» dialog and the ping rows of `activity_provider_settings.xml` | NEW | H3 | R1 | 6 |
| A-24 | `settings/providers` Провайдеры | `ProviderSettingsActivity.kt`, `activity_provider_settings.xml` (648 ln) | REBUILD | H3 | R1 | 6 |
| A-25 | `settings/assets` Файлы ресурсов | `UserAssetActivity.kt`, `activity_user_asset.xml`, `item_recycler_user_asset.xml` | RESTYLE | H3 | R3 | 6 |
| A-26 | Add asset URL | `UserAssetUrlActivity.kt`, `activity_user_asset_url.xml` | MERGE + DELETE | - | - | 6 |
| A-27 | `settings/advanced` Дополнительно | new; absorbs `res/xml/pref_settings.xml` | NEW | H3 | R1 | 6 |
| A-28 | `settings/advanced/localproxy` Локальный прокси | `LocalProxyActivity.kt`, `activity_local_proxy.xml` (1 035 ln) | REBUILD + CUT | H3 | R1 | 6 |
| A-29 | `settings/data` Данные и резервные копии | `BackupActivity.kt`, `activity_backup.xml` | REBUILD | H3 | R1 | 6 |
| A-30 | `settings/data/webdav` Облачная копия | `dialog_webdav.xml` | REBUILD as a sub-page | H3 | R4 | 6 |
| A-31 | `settings/tv` Перенести на ТВ | `TvSendActivity.kt`, `activity_tv_send.xml` | RESTYLE | H3 | R4 | 7 |
| A-32 | TV receive | `TvReceiveActivity.kt`, `activity_tv_receive.xml` | KEEP + tokenise | H3 | R6 | 7 |
| A-33 | `settings/about` О приложении | `AboutActivity.kt`, `activity_about.xml` | REBUILD | H3 | R1 | 6 |
| A-34 | `settings/about/urlschemes` Схемы URL-адресов | `UrlSchemeListActivity.kt`, `activity_url_scheme_list.xml` (634 ln) | REBUILD | H3 | R3 | 6 |
| A-35 | `settings/about/log` Журнал | `LogcatActivity.kt`, `activity_logcat.xml`, `item_recycler_logcat.xml` | RESTYLE + WIRE | H3 | R3 | 6 |
| A-36 | Check update | `CheckUpdateActivity.kt`, `activity_check_update.xml` | DELETE | - | - | 6 |
| A-37 | Legacy settings | `SettingsActivity.kt`, `activity_settings.xml`, `res/xml/pref_settings.xml`, `preference_with_help_link.xml` | DELETE | - | - | 6 |
| A-38 | Sub-page host | `activity_base.xml` | REBUILD | H3 | - | 2 |
| A-39 | Status toast | `toast_status.xml` | DELETE | - | - | 2 |
| A-40 | QR share dialog | `item_qrcode.xml` | RESTYLE | H5 | - | 5 |
| A-41 | Config filter dialog | `dialog_config_filter.xml` | MERGE + DELETE | - | - | 5 |
| A-42 | Tasker | `TaskerActivity.kt`, `activity_tasker.xml` | KEEP + title | H3 | R1 | 7 |
| A-43 | Deep-link handler | `UrlSchemeActivity.kt` | KEEP + HARDEN | H4 | - | 5 |
| A-44 | Widget / QS tile / notification | `widget_switch.xml`, `WidgetProvider`, `QSTileService` | RESTYLE | - | - | 7 |
| A-45 | Dialogs (18) | `MainActivity.kt` builders and the 4 dialog layouts | RESTYLE + CUT | H5 | - | 6 |

## 2.2 Desktop

| # | Surface | Primary file(s) | Verdict | H | R | W |
|---|---|---|---|---|---|---|
| D-01 | Shell | `MainWindow.axaml` (+ 2 029 ln code-behind) | RESTYLE | - | - | 2 |
| D-02 | Nav rail | `MainWindow.axaml:439-545` | KEEP + EXTEND | - | - | 2 |
| D-03 | Compact bottom bar | `BottomNavBar.axaml(.cs)` | DELETE | - | - | 2 |
| D-04 | Главная | `HomeView.axaml` | REBUILD | H2 | R5 | 4 |
| D-05 | Compact Главная | `CompactHomeView.axaml(.cs)` | DELETE | - | - | 2 |
| D-06 | Connect hero | `ConnectHeroView.axaml` (839 ln) + `.cs` (1 156 ln) | RESTYLE | - | - | 4 |
| D-07 | Home account chip | `HomeAccountChip.axaml(.cs)` | RESTYLE into H2 | H2 | - | 4 |
| D-08 | Subscription meta | `SubscriptionMetaView.axaml` (335 ln) + `.cs` (687 ln) | SPLIT + DELETE | - | - | 5 |
| D-09 | Серверы | `ServersView.axaml` (12 ln orphan) | DELETE and re-author | H1 | R1 | 5 |
| D-10 | Server list | `ServerListView.axaml` (313 ln) + `.cs` (939 ln) | RESTYLE | - | R1 | 5 |
| D-11 | Compact servers | `CompactServersView.axaml(.cs)` | HARVEST + DELETE | - | - | 5 |
| D-12 | Настройки | `SettingsView.axaml` (1 075 ln) + `.cs` (359 ln) | REBUILD | H1 | R1 | 6 |
| D-13 | Аккаунт | `AccountView.axaml` (1 474 ln) + `.cs` (524 ln) | REBUILD | H1 | R2 | 3 |
| D-14 | Sign-in | `LoginView.axaml` (954 ln) + `.cs` (1 377 ln) | DELETE | - | - | 3 |
| D-15 | Onboarding | `OnboardingView.axaml(.cs)` | DELETE | - | - | 2 |
| D-16 | Account sync overlay | `AccountSyncView.axaml(.cs)` | RESTYLE | - | R6 | 3 |
| D-17 | `account/buy` Покупка | `BuyView.axaml` (709 ln) + `.cs` (173 ln) | RESTYLE | H3 | R1 | 3 |
| D-18 | `account/devices` Устройства | `DevicesView.axaml` (491 ln) | RESTYLE | H3 | R3 | 3 |
| D-19 | `account/history` История платежей | `PaymentHistoryView.axaml` (351 ln) | REBUILD | H3 | R3 | 3 |
| D-20 | `account/subscription/{id}` Подписка | new | NEW | H3 | R2 | 3 |
| D-21 | `settings/perapp` | `PerAppProxyPage.axaml(.cs)` | RESTYLE | H3 | R1 | 6 |
| D-22 | `settings/routing` | `RoutingSubView.axaml(.cs)` | RESTYLE | H3 | R1 | 6 |
| D-23 | `settings/routing/rule/{id}` | `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow` | REBUILD as sub-pages | H3 | R4 | 6 |
| D-24 | `settings/dns` | `DnsSubView.axaml(.cs)` | RESTYLE | H3 | R1 | 6 |
| D-25 | `settings/latency` | `PingSettingsPage.axaml` | RESTYLE | H3 | R1 | 6 |
| D-26 | `settings/providers` | `ProviderSettingsPage.axaml(.cs)` (zero references) | WIRE + RESTYLE | H3 | R1 | 6 |
| D-27 | `settings/assets` Файлы ресурсов | `GeoFilesPage.axaml(.cs)` | RESTYLE | H3 | R3 | 6 |
| D-28 | `settings/data` | `BackupPage.axaml(.cs)` | RESTYLE | H3 | R1 | 6 |
| D-29 | `settings/advanced` | `OptionSettingWindow.axaml` (1 206 ln), `FullConfigTemplateWindow.axaml` | MIGRATE + DELETE, new sub-page | H3 | R1 | 6 |
| D-30 | `settings/window` Окно и горячие клавиши | `GlobalHotkeySettingWindow.axaml` | MIGRATE + DELETE, new sub-page | H3 | R1 | 6 |
| D-31 | `settings/about` | `AboutPage.axaml` | RESTYLE | H3 | R1 | 6 |
| D-32 | `settings/about/urlschemes` | `UrlSchemesPage.axaml(.cs)` | RESTYLE + re-parent | H3 | R3 | 6 |
| D-33 | `settings/about/log` Журнал | `MsgView.axaml(.cs)` (never built) | REBUILD | H3 | R3 | 6 |
| D-34 | Check update | `CheckUpdateView.axaml(.cs)` (never built) | WIRE + RESTYLE | H5 | - | 6 |
| D-35 | Theme settings | `ThemeSettingView.axaml(.cs)` | DELETE | - | - | 6 |
| D-36 | Backup and restore (old) | `BackupAndRestoreView.axaml(.cs)` | DELETE | - | - | 6 |
| D-37 | `servers/server/{guid}` | `AddServerWindow.axaml` (1 388 ln), `AddServer2Window`, `AddGroupServerWindow` | REBUILD as one sub-page | H3 | R4 | 5 |
| D-38 | `servers/provider/{id}` | `SubEditWindow.axaml` | REBUILD as a sub-page | H3 | R4 | 5 |
| D-39 | Provider list window | `SubSettingWindow.axaml` | MIGRATE + DELETE | - | - | 5 |
| D-40 | Server picker | `ProfilesSelectWindow.axaml` | REBUILD as a flyout picker | H4 | - | 5 |
| D-41 | Profiles view | `ProfilesView.axaml(.cs)` (322 ln, dead) | DELETE | - | - | 2 |
| D-42 | Confirm dialog | `MessageBoxDialog.axaml` | KEEP + tokenise | H5 | - | 2 |
| D-43 | QR dialog | `QrcodeView.axaml` (25 ln) | REBUILD | H5 | - | 5 |
| D-44 | sudo password | `SudoPasswordInputView.axaml` | REBUILD | H5 | R4 | 7 |
| D-45 | JSON editor | `JsonEditor.axaml` | KEEP + RESTYLE | - | - | 5 |
| D-46 | Status bar phantom | `StatusBarView.axaml(.cs)` mounted at 0x0 | REFACTOR + DELETE | - | - | 2 |
| D-47 | Clash proxies / connections | `ClashProxiesView.axaml(.cs)`, `ClashConnectionsView.axaml(.cs)` | DELETE | - | - | 2 |
| D-48 | Status strip / feedback | new, in the shell | NEW | - | - | 2 |

**Totals.** Android, 45 surfaces: 20 REBUILD, 11 RESTYLE, 4 NEW, 4 MERGE + DELETE, 3 DELETE, 3 KEEP.
Desktop, 48 surfaces: 12 REBUILD, 18 RESTYLE, 2 NEW, 8 DELETE, 3 MIGRATE + DELETE, 3 more that end
in deletion (SPLIT, HARVEST, REFACTOR), 2 KEEP. 93 surfaces in the product, and after Wave 7 none of
them is unreachable.

---

# PART 3 - THE FOUR TABS

The four destinations are fixed and identical on both platforms, in this order: **Главная ·
Серверы · Аккаунт · Настройки** (`11-app-structure.md` 2.1). All four are present in every state,
signed in or not (2.2). Android's `updateAccountGate()` (`MainActivity.kt:1082`) and
`updateBottomNavVisibility()` (`:754`) stop hiding items; desktop's zero-width collapse of
«Аккаунт» stops.

*(Line numbers throughout this file were re-verified against the working tree on 2026-07-26. Where
they differ from `20-control-survey.md` and `21-account-survey.md`, the file has drifted since those
surveys and the numbers here are the current ones.)*

## 3.1 Главная - A-01 / D-04 - REBUILD - Wave 4

**Files replaced.** Android: `activity_main.xml` lines 58 to 517, `layout_home_account.xml`,
`layout_home_empty.xml`, `layout_subscription_meta_bar.xml`, `HomeMetaPagerAdapter.kt`. Desktop:
`HomeView.axaml`, `CompactHomeView.axaml`, `HomeAccountChip.axaml`, `OnboardingView.axaml`, and the
decorative half of `ConnectHeroView.axaml`.

**Header:** H2. **Rhythm:** R5.

Change list:

1. The tab becomes `HomeFragment` (Android) and a single `HomeView` (desktop). The compact desktop
   branch, `CompactBreakpointWidth`, `LayoutHysteresis`, `ApplyLayoutMode`, `ViewFor`,
   `BindActiveHome` and `ToggleLayoutSize` are deleted.
2. Six blocks and nothing else, in R5 order: identity header, conditional status strip, connect
   object, status line, numeric strip, two summary rows.
3. **Deleted content:** the memory card (`card_memory` and the unreachable `PREF_SHOW_MEMORY`
   gate), the welcome heading, the onboarding card, the sign-in card, the subscription carousel,
   the embedded server list, the corner add button, and the whole subscription meta bar.
4. **Deleted decoration:** `bg_home_gradient*`, `bg_connect_glow*`, `bg_bottom_nav_scrim`,
   `Brush.HomeGradient`, `Brush.ConnectGlow`, `Nav.Scrim`, `#GlowHalo`, `#AmbientSonar`,
   `#AmbientRing`, `#SonarPulseEcho`, `#RingHoverGlow`, and the `↑` / `↓` text arrows.
5. The connect object is one disc: `size_connect_disc` 176 inside a 200 frame, `color_surface_inset`
   fill, `stroke_ring` 3 in `color_outline` idle and `color_accent` connected, `size_shield` 80
   glyph. `card_connect` stops being a `MaterialCardView` with `rippleColor="@android:color/transparent"`
   and becomes a real control with focus and disabled states, and its `contentDescription` stops
   being the Tasker string.
6. The numeric strip is three right-aligned columns (приём, отдача, задержка) in the Numeric role
   with `tnum`. Its 44 height is reserved always; the content fades in over `dur_220` so nothing
   reflows. No text arrows, no invisible spacer.
7. The two summary rows are `Row.Value`: «Сервер» with the current server name and a chevron into
   Серверы, «Подписка» with «до 14 августа» plus an «Истекает» or «Истекла» chip and a chevron into
   Аккаунт.
8. The one hero moment (600ms connect sonar) stays, once per connect, reduced-motion gated. Nothing
   else on this screen exceeds 300ms.
9. **Empty is not a screen.** First run is a state of Главная: the disc renders disabled at 0.38,
   the strip carries the gate, and one Primary.Tall CTA appears under the status line
   («Добавить провайдера» / «Купить подписку» / «Загрузить серверы» / «Войти»). There is no second
   filled button anywhere on this screen.
10. **Error:** tunnel failure sets the status line to «Не удалось подключиться» in
    `color_destructive_text` with a 13sp «Нажмите, чтобы повторить», and the snackbar carries the
    cause plus «Повторить». **Offline:** the persistent strip «Нет сети. Показаны последние данные.»
    with «Повторить»; the disc stays enabled.
11. States to draw: signed out with no servers, signed in with no subscription, subscription active
    with no servers, disconnected, connecting, connected, disconnecting, tunnel error, expired,
    offline, 60-character server remark, font scale 200%.

## 3.2 Серверы - A-02 / D-09 + D-10 - RESTYLE (rehosted) - Wave 5

**Files.** Android: the servers group of `activity_main.xml`, `layout_servers_header.xml`,
`layout_servers_empty.xml`, `item_recycler_main.xml`, `item_section_header.xml`,
`item_recycler_footer.xml`, `MainRecyclerAdapter.kt`. Desktop: `ServersView.axaml` (the 12-line
orphan, deleted and re-authored), `ServerListView.axaml`, plus the search field harvested from
`CompactServersView.axaml:90`.

**Header:** H1, title «Серверы», one trailing `Button.Icon` «Добавить». **Rhythm:** R1.

Change list:

1. Android: the tab moves out of the shell layout into `ServersFragment`. Desktop: the tab becomes
   a real destination at rail index 1 using the already-declared, currently unused
   `Geo.Nav.Servers` (`Assets/GlobalResources.axaml:313`).
2. Search field at 48, `radius_control` 16, `color_surface_inset`, leading 20 glyph, trailing 20
   clear glyph when filled, placeholder «Поиск по серверам». It filters in place and never
   navigates. This is the **first** server search in the product on either platform.
3. Meta line at 24: «15 серверов · 2 провайдера» in `Caption`, with the sort control on the right as
   a Tertiary button whose label is the current value and whose 20 unfold glyph cycles it in place:
   «По порядку» > «По задержке» > «По имени».
4. Provider group header, sticky, 40:
   `[16][20 collapse chevron, rotates 0 to 90][8][provider name Title 16/700][count Caption][*][40 kebab][16]`.
   The kebab opens a sheet or flyout: «Обновить», «Проверить задержку», «Переименовать», «Открыть
   ссылку», «Настройки провайдера», hairline, «Удалить провайдера» in `color_destructive_text`.
5. Server row: the universal row with the **unified server icon** (0.4.7) - the 28 circular flag
   inside the standard 40 tile, globe glyph fallback. The emoji flags in `item_recycler_main.xml`
   are deleted (§1.4.4). Name `Title` 16/700 plus a protocol chip; transport line `Subtitle`; ping
   value right-aligned in the Numeric role.
6. Selection is two channels: `color_selected_fill` 12% accent **and** a filled check glyph
   replacing the ping value. Never a left stripe, never tint alone. The zero-size `layout_indicator`
   `View` that exists only so `MainRecyclerAdapter.kt:227` can call `setBackgroundColor` on it is
   deleted.
7. **The P0 rewire.** `MainActivity.kt:651-652` assigns `serversAdapter.onItemLongClick` and
   `homeAdapter.onItemLongClick`, but `MainRecyclerAdapter.kt:232` binds only `setOnClickListener`
   and its own comment at `:56` says the callback is "no longer invoked by the adapter". Binding
   long press again gives `ServerActionsSheet`, `editServer()`, `shareServer()`, `showQRCode()` and
   `removeServer()` a caller for the first time. Desktop keeps right-click and adds the same actions
   to the row kebab, because a right-click-only path is undiscoverable.
8. «Добавить» opens one sheet with four rows: «Сканировать QR-код», «Вставить из буфера», «Ввести
   ссылку», «Создать вручную». Every import path in the product enters here.
9. **Empty:** «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились
   серверы.» / «Добавить провайдера». **Empty search:** «Ничего не найдено» / «Попробуйте другой
   запрос.» / «Сбросить поиск». **Error:** the mapped cause plus «Повторить». **Partial:** a
   provider that failed to refresh is marked on its own group header, inline, and the rest of the
   list still renders.
10. Virtualised, stable IDs, `DiffUtil`, no `notifyDataSetChanged()` on a visible list. 150 servers
    and a 60-character remark are test cases, not edge cases.

## 3.3 Аккаунт - A-03 + A-04 / D-13 - REBUILD - Wave 3

**Files replaced.** Android: `AccountFragment.kt`, `activity_account.xml`,
`item_subscription_card.xml`, `SubscriptionPagerAdapter.kt`, `activity_login.xml`,
`LoginActivity.kt`. Desktop: `AccountView.axaml` + `.cs`, `LoginView.axaml` + `.cs`.

**Header:** H1, title «Аккаунт», no trailing. **Rhythm:** R2 signed in, R6 signed out.

**The signed-out answer, decided once for both platforms:** the destination is always present and
signed out it **is** the sign-in gate. Android stops hiding `nav_account`; desktop keeps its in-tab
gate. There is no `LoginActivity` and no `LoginView`.

Change list, signed in:

1. **The identity block is not a card.** Ground plane: 48 avatar, name or `@handle` at `Headline`
   24/700, tariff caption at `Caption`. Then the balance at `Display` 34/700 Numeric with a muted
   `₽`, and «Пополнить» as a **Secondary** button. Today Android and desktop both wrap this in a
   card, which produces two identical rectangles and a squint test that returns "two boxes".
2. **One card on the screen: the subscription.** It carries name, tariff badge chip
   (`Chip.Accent`, one per card), health chip (`Chip.Status.Active/.Expiring/.Expired` - new class
   names, they stop borrowing the payment classes), expiry line, traffic meter with the label
   **above** the bar and the value beside it (never printed on the fill), device gauge, one accent
   «Продлить» CTA, and the auto-renew toggle with its next-charge line. The fixed
   `sub_card_height` 152 is deleted: the card grows.
3. **Traffic is root-only** (`21-account-survey.md` 4.2). Secondary subscription cards render
   without the meter rather than with an empty one.
4. **Perpetual expiry** (`expireAt` year >= 2099 or more than 10 years out) renders «Бессрочно» on
   both platforms. Android currently prints «Действует до 04.06.2099».
5. **The carousel.** Desktop's hand-rolled drag/snap over a `ScrollViewer` with tunnel pointer
   handlers and a 16ms timer tween is deleted (§1.3 bans reinventing standard affordances, and the
   6px drag threshold exists only because the control swallows its own card's button presses).
   Both platforms use the platform pager with dots below, shown only at 2+ subscriptions.
6. **The four-panel kebab wizard is deleted.** «Докупить устройства» and «Улучшить тариф» become
   rows on the new `account/subscription/{id}` sub-page (A-07 / D-20). A flow that takes money is
   not a per-item overflow action (§7.6).
7. Group «Управление»: «Купить подписку» ›, «Устройства» › with the live «2 / 5» value, «История
   платежей» › with the latest date. The accent-coloured row title on «Купить подписку»
   (`activity_account.xml` `row_buy`, `AccountView.axaml:1305`) is deleted - a row title is
   `color_on_surface` (R14).
8. Group «Вход»: «Telegram» with «Привязан @user» or a Tertiary «Привязать» (owner request 0.4.9),
   «Почта» with the address as its value, «Веб-кабинет» ›, then «Выйти» as a `Row.Destructive`.
   **Android has no link endpoint today** (`21-account-survey.md` 4.3): until `RequestLinkTelegram`
   is ported, the Android «Привязать» opens the site handoff, and this is logged as parity gap PG-3,
   not silently dropped.
9. Referral becomes a `Row.Action`: value = the code in the Numeric role, trailing `Button.Icon`
   «Скопировать код», snackbar «Код скопирован». The subtitle carries the stats that
   `getReferralStats` already returns: «Приглашено 3 · начислено 450 ₽». One row, no new screen.
10. **Deleted:** the second filled accent button in the empty state, the `18dp` chevrons (x13), the
    `52` avatar container around a 48 circle, the 900ms `AccelerateDecelerateInterpolator` skeleton
    pulse (replaced by the token skeleton), the raw HTTP code in the «Ошибка оплаты» dialog, the
    toasts for top-up / referral / avatar, the permanently disabled Google «Скоро» button, the
    `LinearGradientBrush` traffic fill, and the raw hex `#3D7EF0` / `#3877E0` at
    `AccountView.axaml:65,68`.
11. **Empty:** «Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» /
    «Купить». This replaces «Оформите первую подписку» on Android and «Оформи первую подписку» on
    desktop; §9.3 locks buying to «Купить» and §9.1 locks the voice to «вы».
12. **Error:** the card shows the mapped cause from `messageFor()` / `ApiError`, not the XML-hardwired
    «Что-то пошло не так», plus «Повторить».
13. **Offline:** last known data stays, is marked «Данные могли устареть», network actions are
    disabled, and one quiet strip carries «Нет сети. Показаны последние данные.» with «Повторить».
14. **Trial:** `Subscription.isTrial` comes from the backend and is never inferred from tariff name
    or squad. A trial card hides renew and add-devices and shows «Купить тариф» instead. The
    ALL-CAPS `account_trial_badge` string («ПРОБНЫЙ») is deleted (§1.4.7).
15. States: signed out, skeleton, one subscription, several, none, expired, expiring, trial, error,
    offline, payment pending, 12-digit balance, 32-character handle.

Change list, signed out (the gate, R6):

16. No card, no illustration, no shield tile, no wordmark. Ground plane, edge to edge:
    «Войти в аккаунт» (Headline) / «Подписка, устройства и платежи хранятся в аккаунте» (Body) /
    Primary.Tall «Войти через Telegram» / Tertiary «Войти по почте» / Tertiary «Создать аккаунт» /
    an error line slot present even when empty.
17. Awaiting state replaces the CTA **without changing its height**: a 20 indeterminate indicator,
    «Ждём подтверждения в Telegram», then «Открыть Telegram» and «Начать заново».
18. Everything else that lives in `LoginView` today (954 lines, 20 buttons, 5 fields, 6 methods)
    moves to the `account/signin` sub-page (A-06). The «Другой способ входа» disclosure opens a
    sheet with «Через сайт» and «Через Google»; Google is listed only when it can actually be
    enabled, never as a permanently disabled row.

## 3.4 Настройки - A-05 / D-12 - REBUILD - Wave 6

**Files replaced.** Android: `layout_settings_content.xml` (1 536 lines, 23 hand-inlined rows),
`SettingsActivity.kt` + `res/xml/pref_settings.xml`. Desktop: `SettingsView.axaml` (1 075 lines) and
its 359-line code-behind, whose affordance contract is kept.

**Header:** H1, title «Настройки». **Rhythm:** R1.

Row-by-row content, defaults, bindings and effects are `12-settings.md` 4 and 5 and are **not**
duplicated here. The conformance deltas:

1. The hub becomes a **data-driven list** on both platforms: 4 named groups (Подключение, Обход
   блокировок, Подписки, Приложение), 22 rows on Android and 24 on desktop, plus the unheaded
   footer pair (Данные и резервные копии, О приложении). No group exceeds 7 rows.
2. Settings search at 48, new on both platforms, filtering titles, subtitles and sub-page contents
   with a breadcrumb caption under each hit («Подключение › Дополнительно»). Empty result:
   «Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск».
3. Every row is one of the six archetypes of `12-settings.md` 3. The 23 hand-inlined Android rows
   at `Body` 14/400 become `Row.*` at `Title` 16/700; the two orphaned row layouts are rewritten as
   the five archetype layouts and are actually included.
4. The affordance grammar from `SettingsView.axaml.cs:14` is kept verbatim and applied on Android
   too: chevron navigates, rotating chevron expands in place, unfold plus value cycles in place,
   segment selects 2 to 3, switch toggles. Never two trailing controls.
5. **Six single-choice `AlertDialog`s are deleted** (Режим, Пинг, DNS, Оформление, Язык,
   Автообновление), along with the two hardcoded Russian error strings at `MainActivity.kt:2144`
   and `:2146` («Вставьте ссылку подписки или конфигурацию сервера», «Не похоже на ссылку или
   конфигурацию…»), which move into `strings_manual_add.xml`.
6. **Every setting has exactly one home.** «Автообновление подписки» stops existing in two places.
   The ~30 settings hidden in `pref_settings.xml` and the ~10 hidden in `OptionSettingWindow` are
   triaged into `settings/advanced`, `settings/dns`, `settings/latency` and `settings/providers`.
7. Desktop gains `MaxWidth` 720 (it has none today) and the four-group order.
8. Section headers are sentence-case bold `Title` 16/700 at the gutter, 24 above and 8 below.
   `SettingsSectionLabel`'s off-scale `paddingTop` 18 becomes `space_16`.
9. Every sub-page uses H3 and R1 (or R4 when it is a form), and they all ship in the same wave as
   the hub: a converted hub above a stack of unconverted sub-pages is exactly the «абы как» the
   owner rejected.

---

# PART 4 - ANDROID SUB-SCREENS

Format: verdict, header, rhythm, then the change list. Only deltas beyond Part 0.5 and Part 1 are
listed.

## A-06 `account/signin` - Вход по почте - REBUILD - Wave 3

H3 «Вход по почте» · R4 · replaces `activity_login.xml` + `LoginActivity.kt` (314 + 3 shapes via
`EXTRA_MODE`, which is deleted).

- 2-item segmented control «Пароль» | «Код из письма» at the top; the rest of the form follows the
  selection.
- Почта field: label above, `inputType="textEmailAddress"`, `autofillHints`, helper slot.
- Пароль field with a show/hide toggle, or the 6-cell code field.
- One Primary.Tall «Войти». Tertiary «Забыли пароль?» and «Отправить ссылку для входа».
- Hairline, then «Другой способ входа» as a disclosure row opening a sheet.
- **Deleted:** two stacked cards, four competing buttons, two spinners, the 26dp radii, the
  `52dp`-declared buttons that draw at 40, and the «или войдите» 12sp divider.
- States: default, submitting (inline loading on the CTA), invalid credentials
  («Неверная почта или пароль.» under the field), rate-limited, offline (CTA disabled, strip
  visible), success (220ms then the hand-off).

## A-07 `account/subscription/{id}` - Подписка - NEW - Wave 3

H3 with the subscription name as the title · R2.

- Exists because the account card cannot hold a purchase flow. Reached from the subscription card
  and from Главная's «Подписка» row.
- Blocks: the card's own readout (expiry, traffic, devices), then group «Действия»: «Продлить»,
  «Докупить устройства», «Улучшить тариф», «Автопродление» (Row.Toggle with the next-charge
  subtitle), «Переименовать» (Row.Value, opens a one-field sheet - `renameSubscription` exists on
  both platforms and is used by neither), «Показать QR-код» (`getQr`, likewise unused), then
  «Устройства» ›.
- Renew and add-devices open the payment surface (A-11), not a bespoke inline pair of buttons.
- The device-price estimate keeps its «≈» and its «Примерная сумма - точную посчитаем при оплате»
  caption: it is a client-side estimate and must not read as final.
- States: active, expiring, expired, trial (actions replaced by «Купить тариф»), busy (inline
  loading per action), error, offline.

## A-08 `account/buy` - Покупка - REBUILD - Wave 3

H3 «Покупка» · R1 · replaces `activity_buy_tariff.xml`, `item_buy_tariff.xml`, `item_buy_option.xml`.

- **Card-in-card is deleted.** Tariffs become a divided list of selectable rows inside one card, not
  bordered boxes inside a bordered box inside a scroll.
- Selection changes **fill and glyph only**, never geometry: today the card stroke goes 1 to 2 and
  the option row goes radius 14 to 20 with a 1.5 stroke, so the row visibly jumps.
- **Six radii on one screen become two** (20 for the card, 16 for controls): `22dp` retry, `26dp`
  pay, `20dp` stepper and `14dp` option are all deleted.
- The steppers become `Button.Icon.Filled` in 48 boxes; `alpha = 0.4f` applied imperatively becomes
  the 0.38 token via `isEnabled`.
- **A purchase summary is added** above the total: the chosen tariff, the period and the device
  count, restated in words. Today the user pays without a written confirmation of what.
- Total row: «Итого» `Body` in `color_on_surface_variant`, amount in the Numeric role. The
  `Headline` accent total is dropped to `Title` so the screen's one accent surface is the CTA.
- One Primary.Tall «Оплатить» opening the payment surface (A-11).
- **Trial and promo are designed in, not left dead:** when `trialEnabled && !trialUsed`, a single
  row above the list offers «Активировать пробный период»; a «Промокод» Row.Value opens a one-field
  sheet wired to `checkPromo` / `activatePromo`. Both endpoints exist on both platforms and neither
  UI uses them.
- **Deleted:** `progress_buy` (never made visible), the hidden `tv_group_emoji` slot the activity
  still assigns, the 76dp flat skeleton blocks (replaced by skeletons shaped like the real rows).
- The money contract survives: `currentTotal(tariff, option)` remains the single source for both the
  displayed total and the charged amount.
- **Pending stays pending.** A Platega payment is webhook-confirmed; returning from the browser
  proves nothing. The poll and the pending state are kept exactly as they are.
- **Empty:** «Тарифы недоступны» / «Попробуйте позже или напишите в поддержку.» / none.
  **Error:** the mapped cause + «Повторить». **Success:** «Подписка оплачена» / «Серверы уже
  добавлены, можно подключаться» + Primary «Подключиться» (the current success state is a dead end).

## A-09 `account/devices` - Устройства - RESTYLE - Wave 3

H3 «Устройства» with a count chip · R3 · `activity_devices.xml`, `item_device.xml`.

- N cards become **one card holding all rows**, hairline inset 68 between them.
- Row: 40 neutral tile with a **platform-resolved glyph** (Android / Apple / Windows / router /
  generic - desktop already resolves this, Android shows one glyph for everything); name `Title`
  plus a «Это устройство» chip when current; meta `Subtitle` in the Numeric role.
- **The raw HWID line is deleted from the row.** It moves into the row's action sheet, which also
  carries «Скопировать ID» and «Отвязать устройство».
- Delete target goes from 44 to 48. The destructive action stops being the only affordance on the
  row.
- **Undo replaces the confirm** (§7.5, `22-components.md` 8.6): unlinking is reversible, the device
  re-registers on the next connect. Act immediately, then a 5-second snackbar «Устройство отвязано»
  with «Отменить».
- Terminology unified to «Отвязать устройство» on both platforms; Android's «Удалить устройство»
  is retired.
- **The diagnostic dialog is deleted.** «Ответ сервера (диагностика)», with its HTTP status, its raw
  body and its instruction to screenshot it and send it to us, must not ship to customers. The same
  data goes to `settings/about/log`.
- **Empty:** «Устройств пока нет» / «Устройства появятся после первого подключения.» / none.
  **Gated (no subscription):** «Активной подписки нет» / «Купите тариф, чтобы подключать
  устройства.» / «Купить». **Error:** cause + «Повторить». **Loading:** three skeleton rows with the
  real row geometry.
- The device-limit state is drawn: «Достигнут лимит устройств. Отвяжите одно из устройств.» on the
  card, with the buy-more action when `CanBuyDevices`.

## A-10 `account/history` - История платежей - REBUILD - Wave 3

H3 «История платежей» · R3 · `activity_payment_history.xml`, `item_payment.xml`, `PaymentsAdapter`.

- **N identical non-interactive cards become a divided list inside one card.** A payment is a fact,
  not an object you act on; this is the uniform-card tell (§2.4.3).
- Rows grouped by month with a plain `Caption` month label above each group («Июль 2026»), not a
  sticky header.
- Row: description `Body` maxLines 1, date `Caption` **with time** in the Numeric role (two payments
  on one day are currently indistinguishable), amount `Body` 500 in the Numeric role right-aligned,
  status chip below it.
- **Four status hues become three** (R12): green «Оплачено», amber «В обработке», red «Ошибка»,
  and **neutral** «Отменён». A cancelled payment is not a warning.
- The centred indeterminate `ProgressBar` over a blank screen is deleted; skeletons shaped like the
  rows replace it.
- Swipe-to-refresh is kept and mirrored on desktop as a toolbar refresh action (desktop has no
  refresh at all today).
- **Empty:** «Платежей пока нет» / «Здесь появится история покупок и продлений.» / «Купить
  подписку» (the currently dead `btn_history_buy` is wired). **Error:** cause + «Повторить», no CTA.

## A-11 Payment-method surface - RESTYLE - Wave 3

H4 «Способ оплаты» · `PaymentMethodSheet.kt`, `sheet_payment_method.xml`, `item_payment_method.xml`.

- **One grammar for the payment decision in the whole product.** Today Android uses a sheet on Buy
  and desktop uses inline Tonal+Primary pairs in three separate places on the Account tab plus a
  sheet on Buy. After this wave: Android bottom sheet, desktop anchored flyout, same rows, same
  order, same copy, called from buy, renew, upgrade, add-devices and top-up.
- Rows are `Row.Action`, 56, with a **neutral** tile. The green tile on the balance row is deleted:
  green is a status colour, not a differentiator (§1.4.1). The balance row is distinguished by its
  value text «С баланса · 1 500 ₽» and its position first.
- The divider above the first row (which currently sits directly under the title) is deleted;
  hairlines go between rows only.
- The trailing chevron is deleted: the row fires the payment, it does not go further. Trailing slot
  holds the balance amount on the balance row and nothing elsewhere.
- SBP stops being detected by string-matching `"sbp"` / `"СБП"`; the method id from `/public/config`
  drives the glyph, with a neutral fallback.
- States: default, single method (no sheet at all - go straight to checkout), insufficient balance
  (row disabled at 0.38 with the reason in its subtitle), busy (inline loading on the tapped row,
  the rest disabled), error.

## A-12 Top-up - REBUILD as a sheet - Wave 3

H4 «Пополнение баланса» · R4 · replaces `dialog_top_up.xml`.

- The `AlertDialog` with a hint-as-label and system «OK» / «Отмена» is deleted (§7.4, §9.2).
- One field block: label «Сумма», field 52, `inputType="numberDecimal"`, helper «От 100 ₽», error
  below in `color_destructive_text`, validated on blur.
- Actions: Tertiary «Отмена», Primary «Продолжить». The sheet stays open on a validation error and
  closes only on success, matching the desktop flyout behaviour that already works.
- Invalid amount stops being a toast.

## A-13 `servers/server/{guid}` - Сервер - REBUILD as one - Wave 5

H3 «Сервер» (or the remark when editing) · R4.

- **Ten activities and four includes become one form.** Deleted: `activity_server_vmess.xml`,
  `_vless`, `_trojan`, `_shadowsocks`, `_socks`, `_hysteria2`, `_wireguard`, `_group`,
  `_proxy_chain`, `_custom_config`, `layout_address_port.xml`, `layout_tls.xml`,
  `layout_tls_hysteria2.xml`, `layout_transport.xml`, `item_recycler_proxy_chain_member.xml`, and
  the classes `ServerCustomConfigActivity`, `ServerGroupActivity`, `ServerProxyChainActivity`,
  `ServerProxyChainMemberAdapter`.
- Protocol is the first control (a Select). The address/port block, the TLS block and the transport
  block are shared sections shown by protocol.
- All 21 bare `EditText`s and 5 `Spinner`s in this family become the library field and the library
  Select. Labels above, helper slots, blur validation, correct `inputType`, autofill hints.
- The FAB (`fab_add_proxy_chain_member`) becomes a `Button.Icon.Filled` in the toolbar: the app has
  one FAB and it does not earn a floating layer.
- Bottom CTA: Primary.Tall «Сохранить». Destructive «Удалить сервер» lives at the bottom of the
  form as a `Row.Destructive`, not in the toolbar.
- States: create, edit, invalid field (error below, focus moves to the first invalid field on
  submit), saving (inline loading), save failure, unsaved-changes confirm on Back.

## A-14 `servers/provider/{id}` - Провайдер - REBUILD - Wave 5

H3 «Провайдер» · R4 · replaces `activity_sub_edit.xml` + `SubEditActivity.kt`.

- Fields: имя, ссылка, User-Agent, «Автообновление» (Row.Toggle), «Только для маршрутизации», фильтр.
- The two `AutoCompleteTextView`s and two `ImageButton` dropdown arrows become Selects.
- Bottom: Primary.Tall «Сохранить», then `Row.Destructive` «Удалить провайдера» with an undo
  snackbar (removing a provider is re-addable from the link).
- States: create, edit, validating the URL, save error, delete with undo.

## A-15 Server actions sheet - REBUILD + REWIRE - Wave 5

H4, title = the server remark · `sheet_server_actions.xml`, `ServerActionsSheet.kt`.

- **This is the P0 regression fix** and it is scheduled twice: the *rewire* lands in Wave 0 as a
  functional fix on today's sheet; the *redesign* lands here.
- Rows: «Сделать основным», «Проверить задержку», «Изменить», «Дублировать», «Поделиться QR-кодом»,
  «Скопировать ссылку», hairline, «Удалить сервер» in `color_destructive_text`.
- Six hand-rolled 56 rows become `Row.Action` / `Row.Destructive` with press feedback (they have
  none today).
- The sheet header gets the unified server icon so the sheet and the row agree about what a server
  looks like.

## A-16 `servers/scan` - Сканер - REBUILD - Wave 5

H3 «Сканировать QR-код», trailing torch `Button.Icon` · replaces the empty `RelativeLayout` of
`activity_none.xml`.

- A framing overlay with a 240 cut-out, an instruction line «Наведите камеру на QR-код», a torch
  toggle and a «Выбрать из галереи» Tertiary at the bottom.
- Menu items with empty titles are deleted.
- States: permission not granted (explain and offer «Разрешить доступ»), permission denied
  permanently (offer «Открыть настройки»), scanning, decoded but invalid («Ссылка не распознана.
  Проверьте код и повторите.»), success (220ms then Back with a snackbar «Сервер добавлен»).

## A-17 Sub setting - MERGE + DELETE - Wave 5

`SubSettingActivity.kt`, `SubSettingRecyclerAdapter.kt`, `activity_sub_setting.xml`,
`item_recycler_sub_setting.xml`. The provider list **is** the Серверы group headers. Its per-item
actions become the group kebab of 3.2 item 4. The screen stops existing.

## A-18 `settings/perapp` - Прокси по приложениям - RESTYLE - Wave 6

H3 «Прокси по приложениям», trailing 40 overflow · R1 · `activity_bypass_list.xml`,
`item_recycler_bypass_list.xml`.

- Mode segment («Все приложения» | «Только выбранные» | «Кроме выбранных») at the top, then search,
  then the app list.
- App row: 40 icon tile, name `Title`, package `Subtitle`, trailing `MaterialCheckBox` becomes a
  switch for consistency with every other boolean in the product.
- The 6-item overflow menu becomes: «Выбрать всё», «Инвертировать», «Импорт», «Экспорт».
- Off-token `18dp` radius, `15sp` and `12sp` inline sizes are deleted.
- **Empty search:** «Приложения не найдены» / «Попробуйте другой запрос.» / «Сбросить поиск».
  **Loading:** skeleton rows while the package list resolves (it is slow on cold start and currently
  shows nothing).

## A-19 App picker - MERGE + DELETE - Wave 6

`AppPickerActivity.kt` + `activity_app_picker.xml` (a 10-line bare `RecyclerView` with no header, no
empty state, no title logic). It becomes a picker sheet inside `settings/perapp`.

## A-20 `settings/routing` - Маршрутизация - RESTYLE - Wave 6

H3 «Маршрутизация», trailing 40 overflow · R1 · `activity_routing_setting.xml`,
`item_recycler_routing_setting.xml`.

- Order: «Стратегия доменов» (Row.Value), section «Наборы правил» with one row per set showing
  «{n} правил» as its value and a toggle, then Action rows «Добавить правило», «Импорт из буфера»,
  «Импорт из QR-кода», «Готовые наборы», «Экспорт», then `Row.Destructive` «Сбросить правила».
- The five actions currently buried in a toolbar overflow that the rest of the app does not have
  become visible rows.
- **Empty:** «Правил пока нет» / «Добавьте правило или загрузите готовый набор.» / «Добавить
  правило».

## A-21 `settings/routing/rule/{id}` - Правило - REBUILD - Wave 6

H3 «Правило» · R4 · replaces `activity_routing_edit.xml` (7 bare `EditText`, 2 `ImageButton`
dropdowns, 1 `AutoCompleteTextView`, no cards, no tokens, no brand face).

- Field blocks: имя, исходящее соединение (Select), домены, IP-адреса, порты, протокол, «Включено»
  (Row.Toggle). Multi-line fields keep a monospaced input but the label/helper/error structure is
  identical to every other field.
- Bottom: Primary.Tall «Сохранить», `Row.Destructive` «Удалить правило» with undo.
- States: create, edit, invalid (per-field error text naming the expected format), save error.

## A-22 `settings/dns` - DNS - NEW - Wave 6

H3 «DNS» · R1.

- Preset chips (Cloudflare, Google, AdGuard, FakeIP, По умолчанию, Свой) as a single-select chip
  row; «Свой» reveals the field block with validation.
- Rows: удалённый DNS, внутренний DNS, DNS-хосты ›.
- Absorbs the DNS single-choice `AlertDialog` and the `editDnsCustom` text dialog, plus the DNS keys
  hidden in `pref_settings.xml`.
- Error: «Адрес DNS указан неверно. Пример: 1.1.1.1 или https://dns.google/dns-query».

## A-23 `settings/latency` - Проверка задержки - NEW - Wave 6

H3 «Проверка задержки» · R1.

- Rows: метод (segment: «TCP» | «Реальная задержка»), адрес проверки (field), таймаут (Value),
  параллельность (Value), «Проверять при запуске» (Toggle), «Сортировать после проверки» (Toggle).
- Absorbs the «Пинг» single-choice dialog and the ping rows currently living in
  `activity_provider_settings.xml`.

## A-24 `settings/providers` - Провайдеры - REBUILD - Wave 6

H3 «Провайдеры» · R1 · replaces `activity_provider_settings.xml` (648 lines, 66 hardcoded dp, 6
switches, and a row duplicated from the settings tab).

- Rows: User-Agent (Value), «Отправлять HWID» (Toggle), «Порядок серверов» (Value), «Уведомлять об
  обновлении» (Toggle).
- The duplicate «Автообновление подписки» is deleted here and lives only in the hub group
  «Подписки».

## A-25 `settings/assets` - Файлы ресурсов - RESTYLE - Wave 6

H3 «Файлы ресурсов», trailing `Button.Icon` «Добавить» · R3 · `activity_user_asset.xml`,
`item_recycler_user_asset.xml`.

- One card, rows per asset: name `Title`, size and date `Subtitle` in the Numeric role, trailing
  overflow with «Обновить», «Изменить ссылку», «Удалить».
- One Action row «Обновить сейчас» with a determinate progress state and a per-file error line.
- **Empty:** «Файлов пока нет» / «Добавьте geoip.dat и geosite.dat, чтобы работала маршрутизация.» /
  «Добавить файл».

## A-26 Add asset URL - MERGE + DELETE - Wave 6

`UserAssetUrlActivity.kt` + `activity_user_asset_url.xml` become a one-field sheet on
`settings/assets`.

## A-27 `settings/advanced` - Дополнительно - NEW - Wave 6

H3 «Дополнительно» · R1.

- Absorbs the reachable remainder of `res/xml/pref_settings.xml`: уровень логов, sniffing,
  разрешение доменов, allow-insecure, MTU, адрес интерфейса, шаблон конфигурации, plus «Локальный
  прокси» › as the only navigation row.
- Every row states its current value. Dangerous rows (allow-insecure) carry a subtitle stating the
  risk in one sentence, not a warning dialog.

## A-28 `settings/advanced/localproxy` - Локальный прокси - REBUILD + CUT - Wave 6

H3 «Локальный прокси» · R1 · replaces `activity_local_proxy.xml` (1 035 lines, 112 hardcoded dp, 37
raw text sizes, zero tokens).

- Cut from five sections to about eight rows: «Включён» (Toggle), порт (field), логин и пароль
  SOCKS5 (fields, revealed only while the toggle is on), «HTTP-авторизация» (Toggle), «Блокировать
  UDP» (Toggle), «Доступ из локальной сети» (Toggle).
- **Deleted:** the memory-limit `MaterialButtonToggleGroup` (`btn_mem_40/60/80/100/150`) with the
  card it fed, the seven bespoke `bg_lp_input` fields, the seven 44dp copy/reveal `ImageButton`s
  (they become one `Button.Icon` per credential field), and the domain-routing section (it merges
  into `settings/routing`).
- Credentials get a show/hide toggle and a copy action, not a bespoke reveal button per field.

## A-29 `settings/data` - Данные и резервные копии - REBUILD - Wave 6

H3 «Данные и резервные копии» · R1 · replaces `activity_backup.xml`.

- Action rows: «Создать резервную копию», «Восстановить из файла», «Поделиться копией»,
  «Облачная копия» ›, «Перенести на ТВ» ›, then `Row.Destructive` «Сбросить настройки» (confirm
  dialog, irreversible).
- Each action states its result inline (last backup date as the row value), not in a toast.
- States: idle, in progress (row loading), success (row value updates plus a snackbar), failure
  («Не удалось создать копию. Проверьте место на устройстве и повторите.»).

## A-30 `settings/data/webdav` - Облачная копия - REBUILD as a sub-page - Wave 6

H3 «Облачная копия» · R4 · replaces `dialog_webdav.xml` (four unlabelled `EditText`s in a dialog).

- Field blocks: адрес, папка, логин, пароль (with show/hide). Then Secondary «Проверить
  подключение» and Primary.Tall «Сохранить».
- States: unconfigured, testing (inline loading), test failed (cause + fix), saved, sync error.

## A-31 `settings/tv` - Перенести на ТВ - RESTYLE - Wave 7

H3 «Перенести на ТВ» · R4 · `activity_tv_send.xml`.

- The `RadioGroup` becomes a Select or a segment; hardcoded 16/20dp go to the scale; the two
  buttons become Secondary «Сканировать» and Primary «Отправить».
- States: idle, scanning, sending, sent, failed.

## A-32 TV receive - KEEP + tokenise - Wave 7

`activity_tv_receive.xml`. Overscan-safe and landscape-locked; leave the structure. Tokenise colours
and sizes, put the plain AppCompat `Button` on the library Secondary, and **add focus states**: this
is a D-pad-only surface and the app has exactly one `state_focused` drawable in total (§7.1, R7).

## A-33 `settings/about` - О приложении - REBUILD - Wave 6

H3 «О приложении» · R1 · replaces `activity_about.xml` (pure 2018 upstream:
`TextAppearance.AppCompat.Subhead`, untinted 24dp icons, no cards).

- Order: wordmark block (one of the only three places the wordmark appears in the product), версия
  as a Row.Value in the Numeric role with a copy action, «Сайт», «Telegram-бот», «Схемы
  URL-адресов» ›, «Журнал» ›, «Лицензии» ›, «Политика конфиденциальности».
- Android has no «Проверить обновления» row: this build is not distributed via GitHub releases.
  Logged as parity gap PG-1.

## A-34 `settings/about/urlschemes` - Схемы URL-адресов - REBUILD - Wave 6

H3 «Схемы URL-адресов» · R3 · replaces `activity_url_scheme_list.xml` (634 lines, 5 section cards,
9 icon buttons at 42dp).

- One list. Row per scheme: label `Title`, the `depv://…` string as a `Subtitle` in the Numeric
  role, trailing `Button.Icon` «Скопировать».
- A single leading `Body` paragraph explains what the schemes are for, capped at 60 characters per
  line.
- **Empty:** «Схемы не зарегистрированы» / «Зарегистрируйте схему, чтобы открывать ссылки в
  приложении.» / «Зарегистрировать».

## A-35 `settings/about/log` - Журнал - RESTYLE + WIRE - Wave 6

H3 «Журнал», trailing 40 overflow · R3 · `activity_logcat.xml`, `item_recycler_logcat.xml`.

- Reachable for the first time. Overflow: «Копировать всё», «Поделиться», «Очистить».
- Rows are monospaced `Caption` lines with a level chip (`Chip.Status.*` reused for
  info / warn / error, three hues, no fourth).
- The Devices diagnostic payload and the payment error detail land here instead of in a customer
  dialog.
- **Empty:** «Записей пока нет» / «Журнал заполнится при следующем подключении.» / none.

## A-36 Check update - DELETE - Wave 6

`CheckUpdateActivity.kt` + `activity_check_update.xml`. Unreachable, upstream-styled, and this build
is not distributed via GitHub releases. Desktop keeps its own (D-34). Parity gap **PG-1**.

## A-37 Legacy settings - DELETE - Wave 6

`SettingsActivity.kt`, `activity_settings.xml`, `res/xml/pref_settings.xml`,
`preference_with_help_link.xml`. 354 lines of `PreferenceScreen` that nothing launches, holding ~30
settings the tab never surfaces. The settings are triaged into A-22, A-23, A-24, A-27 in the same
change; the files go.

## A-38 Sub-page host - `activity_base.xml` - REBUILD - Wave 2

- The single change that converts every sub-page title at once: `ToolbarBrandTitle` (20sp wordmark)
  stops being the toolbar title style, `?attr/actionBarSize` becomes `size_toolbar` 56, and the bar
  takes the page background with no elevation and no divider.
- `fitsSystemWindows="true"` is replaced by the one inset strategy of `11-app-structure.md` 3.1.5.
- Back is a 24 glyph in a 48 box, predictive Back is honoured, and scroll position and filter state
  are restored on return.

## A-39 Status toast - DELETE - Wave 2

`toast_status.xml` plus the deprecated custom-view `Toast` API and its magic 110dp offset. Replaced
by the Snackbar (anchored above the bottom bar) and the persistent status strip.

## A-40 QR share dialog - RESTYLE - Wave 5

H5 · `item_qrcode.xml`. Gains a card, a title, the server name, a «Скопировать ссылку» Secondary and
a «Поделиться» Primary. The bare 336dp `fitXY` `ImageView` is replaced by a centred, aspect-correct
code on a white plate at `radius_object`.

## A-41 Config filter dialog - MERGE + DELETE - Wave 5

`dialog_config_filter.xml` (an `EditText` plus a `Spinner`). Server filtering belongs to the Серверы
search field and its sort control; the dialog goes.

## A-42 Tasker - KEEP + title - Wave 7

`activity_tasker.xml`. Give it a real title instead of `""`, put its `Spinner` on the library Select,
tokenise. No structural change: it is an external integration surface.

## A-43 Deep-link handler - KEEP + HARDEN - Wave 5

`UrlSchemeActivity.kt`. `depv://import/{base64}` currently mutates the server list with no
confirmation. It gains an H4 confirm sheet: «Добавить серверы?» / «Ссылка добавит 12 серверов от
провайдера «X».» / Tertiary «Отмена» + Primary «Добавить».

## A-44 Widget / QS tile / notification - RESTYLE - Wave 7

`widget_switch.xml` (a 45dp icon plus an `AppCompat.Small` white label), `WidgetProvider`,
`QSTileService`, the notification. Tokenise colours and type, use the connected/disconnected
semantics of the product, and add the «Сменить сервер» notification action. Not an IA change.

## A-45 Dialogs - RESTYLE + CUT - Wave 6

All 18 `AlertDialog` builders. Six single-choice pickers are deleted (A-05 item 5). The two
diagnostic dialogs are deleted (A-09, A-03 item 10). What remains uses H5, verbs on both buttons, no
«OK», no raw codes, and `ThemeOverlay.Departament.Dialog` which is already correct.

---

# PART 5 - DESKTOP SCREENS

## D-01 Shell - `MainWindow` - RESTYLE - Wave 2

- Caption row 28 becomes 32; caption buttons 44x22 become 40x32, which is the §7.2 desktop floor.
- The ~260 lines of chrome styles and 32 view-local style rules move out of the window into
  `Assets/GlobalStyles.axaml`. Target: **zero** view-local control styles in `MainWindow.axaml`.
- `Brush.HomeGradient` is removed from `#bodyRoot` and `#contentHost`; both become `Brush.Bg`.
- Minimum window 900x600 is enforced and every view is checked at it. Content caps at 720 and
  centres; the rail does not stretch.
- The keep-alive tab host is kept (it is why desktop preserves scroll across tab switches). Sub-page
  stack gains Esc, mouse button 4, per-tab scoping and route identities.
- `#uiScaleHost` and the 8-zone native resize are kept unchanged.

## D-02 Nav rail - KEEP + EXTEND - Wave 2

- Gains a fourth item at index 1: «Серверы» with `Geo.Nav.Servers`. `RailSlotY` already computes
  `index * 64 + 18`, so the travelling 3x28 indicator needs no change.
- `#btnRailToggle`, which animates the rail to `Width=0` and hides navigation entirely, is deleted.
- The rail is always 76 wide and always shows labels.
- **Focus is added** (R7): the rail has none today, and neither does the bottom bar it replaces.

## D-03 / D-05 / D-11 Compact mode - DELETE - Wave 2

`BottomNavBar.axaml(.cs)`, `CompactHomeView.axaml(.cs)`, `CompactServersView.axaml(.cs)` (after
harvesting its search field, the only one in the app). With them go `ApplyLayoutMode`, `ViewFor`,
`BindActiveHome`, `ToggleLayoutSize`, `CompactBreakpointWidth`, `LayoutHysteresis`, the `Nav.Scrim`
gradient and the `navScrim` `OpacityMask`. A desktop application that opens as a 372px phone strip
is a port artefact.

## D-04 Главная - REBUILD - Wave 4

See 3.1. Desktop specifics:

- One `HomeView`, single column, capped at 720, centred. The 440 | 1 | * split is deleted: the
  server list lives in its own destination now.
- Hover on every clickable surface, focus ring on every focusable control, `Cursor=Hand` on rows.
- Keyboard: Space toggles connect, Ctrl+F focuses the Серверы search after navigating there.

## D-06 Connect hero - RESTYLE - Wave 4

`ConnectHeroView.axaml` (839) + `.cs` (1 156).

- **Keep:** the state machine, the wind-up arc, the press physics, the reduced-motion gating.
- **Delete:** `#GlowHalo`, `#AmbientSonar`, `#AmbientRing`, `#SonarPulseEcho`, `#RingHoverGlow`, the
  `↑` and `↓` text arrows, `#CornerAddButton`, and the raw `#000000` at `:526` (it becomes
  `Brush.Scrim`).
- Two competing idle animations on one object become none: the disc is still at rest.
- `Brush.Ring.Outer` / `Brush.Ring.Inner` survive **only** for the one connect-sonar hero moment.

## D-07 Home account chip - RESTYLE into H2 - Wave 4

`HomeAccountChip.axaml(.cs)`. It becomes the H2 header rather than a card inside the content: same
avatar, same two lines, same chevron, same skeleton variant, same keyboard activation, but on the
ground plane at 56 with no border. `FontSize="18"` goes to the ramp. Android's equivalent gains the
skeleton and the keyboard path it lacks.

## D-08 Subscription meta - SPLIT + DELETE - Wave 5

`SubscriptionMetaView.axaml` (335) + `.cs` (687).

- The provider header half (ping, refresh, pin, delete, collapse) becomes the Серверы provider group
  header (3.2 item 4).
- The subscription readout half (traffic, expiry, tariff) is already rendered by the Account
  subscription card; the duplicate rendering is deleted. Two renderings of one fact in one product
  is the defect being removed.
- The local 34x34 icon-button override and the 11px text printed inside the traffic pill go with it.

## D-09 / D-10 Серверы - Wave 5

See 3.2. Desktop specifics:

- `ServersView.axaml` (12-line orphan) is deleted and the path re-authored as the destination.
- `ServerListView` keeps its virtualisation, row grammar and divider inset, and gains search, sort,
  ping-all, the group kebab and a discoverable actions path (the context menu is currently the only
  route to seven actions).
- Multi-select is added for bulk delete and bulk ping, with a selection-count toolbar replacing the
  header while active.

## D-12 Настройки - REBUILD - Wave 6

See 3.4. Desktop specifics: `MaxWidth` 720, the search field, the four-group order, and the removal
of the inline local-proxy panel and the `unfold_more` cycle affordance where `12-settings.md` 14
(D-S2, D-S4) records it.

## D-13 Аккаунт - REBUILD - Wave 3

See 3.3. Desktop specifics beyond the shared list:

- The 26 view-local style rules and 16 locally re-declared `StreamGeometry` keys in
  `AccountView.axaml` are deleted; `Geo.Acc.Chevron` is byte-identical to the global
  `Geo.ChevronRight` and is one of them.
- The two Semi-default `TextBox`es (`:372` top-up amount, `:1223` link email) and the one unstyled
  `Button` opt into the library. These are the money-entry and identity-linking fields on the tab
  being rebuilt.
- The three `Height="32"` shrinks on `IconButton40` (`:620`, `:705`, `:761`, the kebab-flyout back
  buttons) are deleted; they take a 40 class down to the bare §7.2 minimum.
- `Button.MethodChip` and `Button.MeterRow` are deleted: the first becomes the shared payment
  surface, the second becomes `Row.Navigation`.
- The two icon-only buttons with no `ToolTip.Tip` or `AutomationProperties.Name` gain them.
- Off-scale `Spacing="6"` x2, `Spacing="10"`, `Spacing="20"` and `Margin="6,0,0,4"` are corrected.

## D-14 Sign-in - DELETE - Wave 3

`LoginView.axaml` (954) + `.cs` (1 377): 20 buttons, 5 fields, 6 methods, 34 copy keys on one
scrolling column, with the primary method demoted to a tonal button under an «или» divider. Replaced
by the Аккаунт gate (3.3 items 16 to 18) and `account/signin`. `Button.SegItem` and the duplicated
`Button.Tonal.Tall` declaration go with it.

## D-15 Onboarding - DELETE - Wave 2

`OnboardingView.axaml(.cs)`. First run is a state of Главная, not a gate. Its "I already have this
configured" gap is answered by the Главная first-run state and the Аккаунт gate.

## D-16 Account sync overlay - RESTYLE - Wave 3

`AccountSyncView.axaml(.cs)`. The strongest state surface in either app: keep the stage line, the
real failure path and the two exits. Changes: move off `Brush.HomeGradient` onto `Brush.Bg`, rename
the misnamed `Account_SyncSubtitle` key to match its siblings, and mirror the whole surface on
Android, which has no post-sign-in sync surface at all (parity gap **PG-2**, closed in Wave 3).

## D-17 `account/buy` - RESTYLE - Wave 3

See A-08 for the shared change list. Desktop specifics:

- H3 title drops from `Headline` 24 to `Title` 16/700.
- **The bottom sheet becomes a flyout anchored to «Оплатить»** (§13 translation table). A slide-up
  sheet at the bottom of a 900x600 window is a phone idiom, and it disagrees with the Account tab's
  own payment buttons, which are also being unified.
- `PaymentNoticeTitle` moves from `Brush.Red` to `Brush.RedText` (4.88:1 to 6.15:1).
- The three view-local `Button.Flat` instances are deleted.
- `Border.PriceOption` (radius 14 inside a radius 20) is deleted along with the card-in-card.

## D-18 `account/devices` - RESTYLE - Wave 3

See A-09. Desktop specifics:

- `Background="#80000000"` at `:451` becomes `Brush.Scrim`; `Margin="0,3,0,0"` and `Margin="16,10"`
  go on scale.
- The current-device row stops being washed with `Brush.Tile.Blue`; the «Это устройство» chip stops
  dissolving into its own background and becomes `Chip.Neutral` on the plain row.
- The in-view confirm card is replaced by act-plus-undo (`22-components.md` 8.6). The `ModalCard`
  and its scrim are deleted.
- The count chip in the toolbar is kept; Android gains it.

## D-19 `account/history` - REBUILD - Wave 3

See A-10. Desktop specifics: the three hand-copied ~60-line skeleton blocks become one templated
skeleton; the locally hard-coded 64x64 `CornerRadius="20"` empty tile becomes `Border.EmptyIcon`; a
refresh action is added to the toolbar.

## D-20 `account/subscription/{id}` - NEW - Wave 3

See A-07. Desktop uses the same route and the same rows; the flows that currently live in the
four-panel kebab flyout land here.

## D-21 to D-33 Settings sub-pages - Wave 6

All thirteen share one contract: **H3 with the shared `Button.BackNav`** (eight of them redeclare a
local `Button.IconButton:pressed` style verbatim today), page background, `MaxWidth` 720, R1 or R4,
the six row archetypes, the library field and Select, focus on everything, and the empty/error
grammar of 0.4. The legacy 32px `Button.IconButton` class is deleted from
`Assets/GlobalStyles.axaml:226` in the same change.

| Page | File | Verdict | Deltas beyond the shared contract |
|---|---|---|---|
| Прокси по приложениям | `PerAppProxyPage.axaml` | RESTYLE | Mode segment, search, app rows with switches; add-`.exe` becomes a picker flyout; loading skeletons for the package scan |
| Маршрутизация | `RoutingSubView.axaml` | RESTYLE | **The escape hatch into `RoutingRuleSettingWindow` (900x600, `resx` strings) is closed**; rule sets get the empty state; reset becomes `Row.Destructive` |
| Правило | `RoutingRuleDetailsWindow` | REBUILD as a sub-page | R4, field blocks, one Select for outbound, save + delete with undo |
| DNS | `DnsSubView.axaml` | RESTYLE | Preset chips keep their behaviour; «Свой» gains validation states and an error line |
| Проверка задержки | `PingSettingsPage.axaml` | RESTYLE | Gains the 6 rows of `12-settings.md` 5.6; its 2 Semi `TextBox`es opt in |
| Провайдеры | `ProviderSettingsPage.axaml` | **WIRE** + RESTYLE | Built, styled, referenced by nothing. Given the `settings/providers` route and reached from the hub |
| Файлы ресурсов | `GeoFilesPage.axaml` | RESTYLE | R3; adds determinate progress and a per-file error state for the download |
| Данные и резервные копии | `BackupPage.axaml` | RESTYLE | Absorbs export/import; adds the WebDAV sub-page and «Сбросить настройки» as `Row.Destructive` |
| Дополнительно | new; absorbs `OptionSettingWindow` (1 206 ln, 91 `resx` refs) and `FullConfigTemplateWindow` | MIGRATE + DELETE | ~10 engine controls that exist nowhere in the shipping UI get a home; 30 Semi `ComboBox`es and 24 Semi `ToggleSwitch`es die with the window |
| Окно и горячие клавиши | new; absorbs `GlobalHotkeySettingWindow` | MIGRATE + DELETE | Four hotkey capture fields, UI scale, tray behaviour, start minimised |
| О приложении | `AboutPage.axaml` | RESTYLE | Wordmark block, version in the Numeric role with copy, links, «Схемы URL-адресов» ›, «Журнал» ›, «Проверить обновления» (desktop only) |
| Схемы URL-адресов | `UrlSchemesPage.axaml` | RESTYLE + re-parent | Moves under About; register/unregister keep their desktop-only rows |
| Журнал | `MsgView.axaml` (registered, never built) | REBUILD | R3, level chips in three hues, copy/share/clear overflow, the empty state of 0.4 |

## D-34 Check update - WIRE + RESTYLE - Wave 6

`CheckUpdateView.axaml(.cs)`, registered and never built: there is no "check for updates" in the
shipping UI at all. It becomes an H5 dialog opened from `settings/about`, with states: idle,
checking (inline loading), up to date («Обновлений нет» / «Установлена последняя версия.»), update
available (version, size, «Скачать»), downloading (determinate progress with cancel), failed
(cause + «Повторить»). Its 2 Semi `ToggleSwitch`es opt into the library. Desktop only; Android is
PG-1.

## D-35 / D-36 Theme settings, Backup and restore - DELETE - Wave 6

`ThemeSettingView.axaml(.cs)` (superseded by Настройки > Приложение) and
`BackupAndRestoreView.axaml(.cs)` (superseded by `settings/data`). Both are registered and never
built.

## D-37 `servers/server/{guid}` - REBUILD as one sub-page - Wave 5

Replaces `AddServerWindow.axaml` (1 388 lines, 94 `resx` refs, 0 Incy classes, 54 Semi `TextBox`es,
16 Semi `ComboBox`es, 8 Semi `ToggleSwitch`es), `AddServer2Window.axaml` and
`AddGroupServerWindow.axaml`. This is the single highest-value conversion in the desktop client: it
alone removes 87 of the 289 Semi-default controls.

- H3, R4, protocol-driven sections, the library field and Select throughout.
- `JsonEditor.axaml` is kept as the custom-config section and its chrome is restyled.
- No OS-decorated window: it is a sub-page on the shell stack, with Esc and mouse button 4.

## D-38 / D-39 Провайдер and the provider list - Wave 5

`SubEditWindow.axaml` becomes the `servers/provider/{id}` sub-page (R4). `SubSettingWindow.axaml`
(no UI binding in this shell) is deleted: the provider list is the Серверы group headers.

## D-40 Server picker - REBUILD as a flyout picker - Wave 5

`ProfilesSelectWindow.axaml`, called from three places (`SubEditViewModel:85`,
`AddGroupServerViewModel:116`, `RoutingRuleDetailsViewModel:105`). It becomes the library Select
flyout with search, not an 800x450 window.

## D-41 / D-46 / D-47 Dead views - DELETE - Wave 2

- `ProfilesView.axaml(.cs)` (322 lines): its interaction handlers were already re-implemented at
  `ServerListView.axaml.cs:71-133`.
- `StatusBarView.axaml(.cs)`: mounted at `Width=0 Height=0 Opacity=0` purely to keep handlers alive.
  Handlers move into the shell, the phantom view goes.
- `ClashProxiesView.axaml(.cs)` and `ClashConnectionsView.axaml(.cs)`: Mihomo proxy-group control is
  not part of this product. A deliberate cut, recorded as decision D-11 in `11-app-structure.md` 16.
  Their 12 `Classes="IconButton Success"` buttons - the third-party green that exists nowhere in
  this repository - die with them.

## D-42 Confirm dialog - KEEP + tokenise - Wave 2

`MessageBoxDialog.axaml`. Already Incy and already H5-shaped (the question is the title). Changes:
the `BoxShadow 0 16 40 0 #73000000` goes (depth comes from the surface ramp, §4.7), and its buttons
adopt the library variants.

## D-43 QR dialog - REBUILD - Wave 5

`QrcodeView.axaml` (25 lines of raw upstream inside our `DialogHost`: a 400x400 `Image`, a read-only
`TextBox`, legacy `Margin8` resources). Becomes H5 with a card, the server name, an aspect-correct
code on a white plate, «Скопировать ссылку» and «Поделиться». Same design as A-40.

## D-44 sudo password - REBUILD - Wave 7

`SudoPasswordInputView.axaml`: `resx:ResUI` strings, the legacy `CardBorder` theme, `Width="100"`
buttons. Linux users meet this on first TUN start, so it is the first Departament dialog they see.
H5, one field block with a show/hide toggle, Tertiary «Отмена» + Primary «Продолжить», Russian copy,
and an explicit line saying why the password is needed.

## D-45 JSON editor - KEEP + RESTYLE - Wave 5

`JsonEditor.axaml`. Kept as a control; its chrome adopts the token set. It is the custom-config
section of the server form.

## D-48 Status strip and feedback channel - NEW - Wave 2

There is no feedback surface on desktop today: `snackHost` routes nowhere and `MsgView` is never
built. Wave 2 adds the shell-level channel of `11-app-structure.md` 8: one transient `Border.Toast`
at a time with an optional action, plus the persistent status strip for offline, expired
subscription and tunnel error. Android's Snackbar and the same strip are the mirror.

---

# PART 6 - THE ORDER OF WORK

## 6.1 The rule that makes this safe

An app is "visibly half-converted" when two surfaces the user can reach **in the same session**
speak different languages. The mitigation is not to convert faster; it is to convert in an order
where each step leaves a self-consistent product.

Five ordering rules, in force for every wave:

1. **System before screens.** Tokens, then component defaults, then screens. On Android
   `res/values/themes.xml` sets **zero** component styles today, so declaring `materialButtonStyle`,
   `materialCardViewStyle`, `materialSwitchStyle`, `textInputStyle`, `chipStyle` and the three
   `shapeAppearance*Component` attributes re-shapes 33 buttons, 50 cards, 23 switches and 70 fields
   **in one change with no layout edits**. On desktop, removing 190 view-local style rules and 11
   bespoke classes and letting 289 Semi-default controls fall through to the library does the same.
   After Wave 1 the whole product already looks like one app, and every later wave is a structural
   improvement inside an already-consistent skin.
2. **Family atomicity.** A tab ships with its sub-pages. Настройки and its 15 sub-pages are one
   wave. Аккаунт with buy, devices, history, subscription, the gate and the payment surface is one
   wave. Серверы with the server form, provider form, actions sheet and scanner is one wave. A
   converted hub above unconverted children is precisely «абы как».
3. **Both platforms in the same wave, or a logged gap.** A wave is not done when only one client
   has it. If a platform genuinely cannot have the feature, it becomes a numbered parity gap in 7.3,
   not a silent difference.
4. **No orphan component.** A component is never introduced for one screen. If a screen needs
   something the library does not have, the library gains it first (`22-components.md` 0.4), with
   all its states, and then every screen that should use it uses it in the same wave.
5. **Delete in the same change as the replacement.** No file is "kept for now". A wave that adds a
   screen and leaves its predecessor in the tree has not finished.

## 6.2 The waves

### Wave 0 - Foundation and functional unblock (no visible design change)

| Work | Platform | Why it is first |
|---|---|---|
| Move `Color.Accent`, `Brush.Accent`, `Brush.OnAccent`, `Brush.Tile.*`, `Brush.SelectedFill`, `Brush.StatusChip.*` **inside** `ResourceDictionary.ThemeDictionaries` | Desktop | R11. Light-theme accent measures 2.98:1 today, below even the 3:1 UI floor, on every link button, every checked segment and **every focus ring**. No component spec can work around it |
| Add the missing tokens: `radius_control` 16, `field_min_height` / `size_field` 52, `btn_height` 48 / `btn_height_tall` 52, `input_debounce` 500, `color_warning*`, `color_outline_control`, `color_on_surface_dim` | Both | R1, R2, R9, R10 |
| Rewrite `res/anim/press_scale.xml` to 0.97 / 90 `ease_out_quart` / 160 `ease_out_quint`; delete `res/anim/nav_press.xml` | Android | R4. `nav_press` uses linear easing, banned by §8.3 |
| Verify the Space Grotesk variable-font axis actually renders 400/500/700 | Both | `03-direction.md` 6.3. If it does not, every weight decision downstream is wrong |
| **Rewire `MainRecyclerAdapter` long press** so `ServerActionsSheet` has a caller again | Android | P0: a user currently cannot delete, rename, share or edit a single server. This is a bug fix on today's UI and must not wait for Wave 5 |
| One money formatter per platform; delete the other four | Both | The same balance is signed `₽` on one screen and `$` on another |
| Clear the dash debt: 22 hits on Android, 44 on desktop | Both | §9.7 |

**Gate:** the mechanical greps of §1.5 return zero on the token files; the app looks **identical**
to before; the server actions work.

### Wave 1 - The control layer (every control changes at once)

| Work | Platform |
|---|---|
| Declare all component default styles in `res/values/themes.xml`; author `Widget.Departament.*` for button (5 variants x 2 heights), icon button, card, switch, chip, field, segmented control | Android |
| Author the five row layouts and the `bg_row` / `divider_row` drawables; delete `layout_setting_row.xml` and `layout_setting_toggle_row.xml` | Android |
| Rewrite `Assets/GlobalStyles.axaml` to the closed vocabulary; delete all 11 bespoke button classes, the legacy 32px `Button.IconButton`, and the 190 view-local style rules | Desktop |
| Delete `Button.Success` usages (12) and `Border.PriceOption`, `Radius.Search`, `Radius.Traffic` | Desktop |
| Add `:disabled` to the 10 classes missing it and `FocusAdorner` to the 16 missing it | Desktop |
| Implement the inline loading contract (R8) and the double-press guard (R9) on every button | Both |

**Gate:** every screen still opens and functions; button heights collapse from 11 to 2 across the
product; chevrons collapse from 6 sizes to 1; the Semi-default control count falls from 289 to 0 on
every view that was not deleted; zero screens have been restructured.

### Wave 2 - The frame

Shell, navigation, headers, feedback, and the deletion of dead shells.

- Android: `MainActivity` shell rebuild, four Fragments, real `BottomNavigationView` with
  `@menu/menu_bottom_nav` (resurrecting `BottomNavIndicator` and `bottom_nav_item_color`), one inset
  strategy, `sw600dp` rail, `activity_base.xml` rebuilt to H3 (A-38), `toast_status.xml` deleted
  (A-39), Snackbar and status strip added.
- Desktop: D-01, D-02, D-03/D-05/D-11, D-15, D-41, D-46, D-47, D-48, D-42.
- The four destinations become permanent on both platforms in this wave.

**Gate:** every sub-page in the product carries the same 56 seamless toolbar with a `Title` 16/700
title (this is one change on each platform and it converts ~30 screens at once); the nav has four
items everywhere; there is exactly one feedback channel per platform; no dead view remains in either
tree.

### Wave 3 - Аккаунт family

A-03, A-04, A-06, A-07, A-08, A-09, A-10, A-11, A-12 / D-13, D-14, D-16, D-17, D-18, D-19, D-20.

First of the four families because it is the owner's live demand, because it is where the money
lives, and because Главная depends on its identity row, its subscription chip and its gate states.

**Gate:** one payment grammar in all five places money is taken; three status hues; one money
format; the sign-in gate is a state of the tab on both platforms; Android has renew, auto-renew,
add-devices, upgrade, rename, QR, sign-out and Telegram linking (or a logged gap for linking);
`getQr`, `renameSubscription`, `getReferralStats`, promo and trial each have a surface or a recorded
cut.

### Wave 4 - Главная and connect

A-01 / D-04, D-06, D-07.

**Gate:** the launch screen is six blocks; the glow, gradient and ambient loop stack is gone from
both platforms; the first frame after cold start is under 1s on a mid-range device; every gate state
(no account, no subscription, no servers, expired, offline, tunnel error) renders on the same screen
rather than as a different screen.

### Wave 5 - Серверы family

A-02, A-13, A-14, A-15, A-16, A-17, A-40, A-41, A-43 / D-08, D-09, D-10, D-37, D-38, D-39, D-40,
D-43, D-45.

**Gate:** the product has server search for the first time; the unified server icon renders
identically in the list, the hero, the sheet and the notification; one server form replaces 13
Android layouts and 3 desktop windows; zero OS-decorated secondary windows remain on desktop except
the main one.

### Wave 6 - Настройки family

A-05, A-18 to A-37, A-45 / D-12, D-21 to D-36.

Last of the four families because it is the largest single conversion (1 hub plus 15 sub-pages per
platform), the least frequently visited, and because after Wave 1 its rows already carry the library
look while their structure is being rewritten.

**Gate:** every setting has exactly one home; the ~40 settings that exist only in dead files are
either surfaced or recorded as cut; settings search works on both platforms; zero single-choice
`AlertDialog`s remain; zero `Spinner` and zero `ComboBox` remain in the product.

### Wave 7 - Tail and sweep

A-31, A-32, A-42, A-44 / D-44, plus the whole-product sweep:

- Copy pass: §9 across every string file, both platforms, including the English desktop keys.
- State pass: walk §15's eleven states on all 93 surfaces and fix what was missed.
- Accessibility pass: TalkBack and screen-reader walk-through, font scale 200%, DPI 200%, keyboard-
  only completion of every desktop task, contrast verification in dark, light and mono.
- Deletion sweep: confirm all 37 Android layouts, 15 Android classes and 24 desktop views on the
  delete list are gone and nothing references them.
- The §16 pre-flight, run per screen, with the seven questions of §2.4 answered in writing.

**Gate:** `17.1` scoring of >= 18/20 with no dimension below 3, on every screen, on both platforms.

## 6.3 What may run in parallel

- Wave 0's P0 rewire and the money-formatter unification are code fixes with no design dependency
  and should start immediately.
- Android and desktop work inside the same wave is parallel by construction, provided the wave
  closes on both before the next opens.
- The copy pass (Wave 7) can begin per family as each family lands, provided the terminology lock is
  applied globally rather than per screen.

---

# PART 7 - THE EXPLICIT LISTS

## 7.1 MERGED - every screen whose content moves into another surface

| Screen | Platform | Merges into | What survives the move |
|---|---|---|---|
| `layout_home_account.xml` (home account chip) | Android | Главная H2 header | avatar, name, subtitle, chevron; the dead `group_login` block and its `✕` text-glyph close button do not |
| `layout_subscription_meta_bar.xml` | Android | Серверы provider group header **and** the Аккаунт subscription card | ping/refresh/pin/delete/collapse to the group header; traffic/expiry/tariff to the account card |
| `layout_home_empty.xml` | Android | Главная first-run state **and** the Аккаунт gate | the two CTAs, at 52 and as one primary each |
| `AppPickerActivity` + `activity_app_picker.xml` | Android | `settings/perapp` as a picker sheet | the package list and its filter |
| `UserAssetUrlActivity` + `activity_user_asset_url.xml` | Android | `settings/assets` as a one-field sheet | the URL field and its validation |
| `SubSettingActivity` + `activity_sub_setting.xml` + `item_recycler_sub_setting.xml` | Android | Серверы provider group headers | per-provider actions, as the group kebab |
| `ProviderSettingsActivity` + `activity_provider_settings.xml` | Android | `settings/providers` + `settings/latency` + the Серверы group kebab | User-Agent, HWID, order, notify; ping rows to `settings/latency` |
| `dialog_webdav.xml` | Android | `settings/data/webdav` sub-page | the four fields, now labelled |
| `dialog_config_filter.xml` | Android | Серверы search and sort | filtering behaviour |
| `dialog_top_up.xml` | Android | the top-up sheet (A-12) | the amount field, now with a label and an inline error |
| `res/xml/pref_settings.xml` (~30 settings) | Android | `settings/advanced`, `settings/dns`, `settings/latency`, `settings/providers` | every setting that has a user-facing meaning; the rest are recorded as cut |
| `activity_server_*.xml` x10 + 4 includes | Android | `servers/server/{guid}` | every protocol's fields, as sections of one form |
| `SubscriptionMetaView` | Desktop | Серверы group header + Аккаунт card | as above, mirrored |
| `CompactServersView` search field | Desktop | Серверы search | the only search field in the desktop client today |
| `OptionSettingWindow` (1 206 ln) | Desktop | `settings/advanced` | ~10 engine controls that exist nowhere else in the shipping UI |
| `FullConfigTemplateWindow` | Desktop | `settings/advanced/template` | the template editor, using `JsonEditor` |
| `GlobalHotkeySettingWindow` | Desktop | `settings/window` | the four hotkey capture fields |
| `SubSettingWindow` | Desktop | Серверы group headers | the provider list |
| `RoutingRuleSettingWindow` | Desktop | `settings/routing` | the rule-set list |
| `ProfilesSelectWindow` | Desktop | the Select flyout | the picker with search |
| `AddServer2Window`, `AddGroupServerWindow` | Desktop | `servers/server/{guid}` | custom-config and group sections of the one form |
| `StatusBarView` handlers | Desktop | the shell | the handlers; the 0x0 phantom view does not |

## 7.2 DELETED - every screen and file that stops existing

**Android layouts (37).** `layout_home_empty.xml`, `layout_home_account.xml`,
`layout_subscription_meta_bar.xml`, `activity_login.xml`, `activity_settings.xml`,
`res/xml/pref_settings.xml`, `activity_check_update.xml`, `activity_local_proxy.xml`,
`activity_provider_settings.xml`, `activity_url_scheme_list.xml`, `activity_sub_setting.xml`,
`item_recycler_sub_setting.xml`, `activity_user_asset_url.xml`, `activity_app_picker.xml`,
`activity_about.xml`, `activity_routing_edit.xml`, `activity_sub_edit.xml`,
`activity_server_vmess.xml`, `activity_server_vless.xml`, `activity_server_trojan.xml`,
`activity_server_shadowsocks.xml`, `activity_server_socks.xml`, `activity_server_hysteria2.xml`,
`activity_server_wireguard.xml`, `activity_server_group.xml`, `activity_server_proxy_chain.xml`,
`activity_server_custom_config.xml`, `layout_address_port.xml`, `layout_tls.xml`,
`layout_tls_hysteria2.xml`, `layout_transport.xml`, `item_recycler_proxy_chain_member.xml`,
`toast_status.xml`, `item_recycler_footer.xml`, `preference_with_help_link.xml`,
`dialog_webdav.xml`, `dialog_config_filter.xml`.
Plus `layout_setting_row.xml` and `layout_setting_toggle_row.xml`, replaced by the five archetype
layouts.

**Android classes (15).** `SettingsActivity.kt`, `CheckUpdateActivity.kt`, `AppPickerActivity.kt`,
`UserAssetUrlActivity.kt`, `SubSettingActivity.kt`, `SubSettingRecyclerAdapter.kt`,
`LocalProxyActivity.kt`, `ProviderSettingsActivity.kt`, `UrlSchemeListActivity.kt`,
`ServerCustomConfigActivity.kt`, `ServerGroupActivity.kt`, `ServerProxyChainActivity.kt`,
`ServerProxyChainMemberAdapter.kt`, `HomeMetaPagerAdapter.kt`, `LoginActivity.kt`. `ServerActivity.kt`
is replaced by one `ServerEditActivity`. The matching `<activity>` entries leave
`AndroidManifest.xml` in the same change.

**Android resources.** Drawables `bg_home_gradient.xml` (+ night, + mono), `bg_connect_glow.xml`
(+ mono), `bg_bottom_nav_scrim.xml`, `bg_nav_header.xml`, `nav_header_bg.png`, `bg_acc_option.xml`,
`bg_speed_chip.xml`, `ripple_card.xml`, `bg_chip_gold.xml`, `ic_circle.xml`, `ic_nav_more.xml`,
`ic_qu_start_24dp.xml`, `ic_qu_stop_24dp.xml`. Animations `nav_press.xml`. Fonts
`res/font/montserrat_thin.ttf` (152 KB of an unused second typeface with no Cyrillic). Styles
`TabLayoutTextStyle`, `BrandedSwitch`. Colour tokens `icon_purple`, `icon_tile_purple`,
`icon_orange`, `icon_tile_orange`, `icon_green`, `icon_tile_green`, `icon_yellow`,
`icon_tile_yellow`. Strings: the 17 unreferenced keys in `strings_account.xml` and `strings_buy.xml`
listed in `21-account-survey.md` 1.4.14, including the ALL-CAPS `account_trial_badge`.
**Not deleted:** `menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml` and
`style/BottomNavIndicator` become live in Wave 2.

**Desktop views (24).** `OnboardingView.axaml(.cs)`, `LoginView.axaml(.cs)`,
`CompactHomeView.axaml(.cs)`, `CompactServersView.axaml(.cs)` (after harvesting the search field),
`BottomNavBar.axaml(.cs)`, `ProfilesView.axaml(.cs)`, `ThemeSettingView.axaml(.cs)`,
`BackupAndRestoreView.axaml(.cs)`, `ClashProxiesView.axaml(.cs)`, `ClashConnectionsView.axaml(.cs)`,
`StatusBarView.axaml(.cs)`, `MsgView.axaml(.cs)` (rebuilt under a new name as the log page),
`AddServerWindow.axaml(.cs)`, `AddServer2Window.axaml(.cs)`, `AddGroupServerWindow.axaml(.cs)`,
`SubEditWindow.axaml(.cs)`, `SubSettingWindow.axaml(.cs)`, `OptionSettingWindow.axaml(.cs)`,
`FullConfigTemplateWindow.axaml(.cs)`, `GlobalHotkeySettingWindow.axaml(.cs)`,
`RoutingRuleSettingWindow.axaml(.cs)`, `RoutingRuleDetailsWindow.axaml(.cs)`,
`ProfilesSelectWindow.axaml(.cs)`, and the 12-line orphan `ServersView.axaml`.
After this the product contains **zero** `resx:ResUI` references in the UI layer and **zero**
OS-decorated secondary windows.

**Desktop resources and code.** `Brush.HomeGradient` (dark and light), `Brush.ConnectGlow`,
`Nav.Scrim`, `Radius.Search`, `Radius.Traffic`, `Radius.Button` (renamed `Radius.Control`),
`Button.IconButton` (legacy 32px), `Button.Success` usages, `Border.PriceOption`, `Button.MethodChip`,
`Button.MeterRow`, `Button.Flat`, `Button.SegItem`, `Button.BottomNavItem`, `Button.WinBtn`,
`Button.RailToggle`, `Button.MetaIcon` / `.MetaDanger`, the duplicate `Button.Tonal.Tall`
declarations, `Brush.Tile.Purple/Orange/Green/Yellow`, `Brush.Icon.Orange`, `Brush.Icon.Yellow`,
`Size.HeroFrame`, `Size.ConnectArc`, `Size.TrafficPill`, the `navScrim` `OpacityMask` block, and the
16 locally re-declared `StreamGeometry` keys in `AccountView.axaml`. Code: `ApplyLayoutMode`,
`ViewFor`, `BindActiveHome`, `ToggleLayoutSize`, `CompactBreakpointWidth`, `LayoutHysteresis`, the
carousel drag/snap/tween in `AccountView.axaml.cs`, and `DelegateSnackMsg`'s dead routing.
`Brush.Ring.Outer` / `Brush.Ring.Inner` **survive** for the one connect-sonar hero moment.

**Behaviours deleted.** The Devices raw-response diagnostic dialog (both the dialog and its three
strings). The «Ошибка оплаты» dialog's raw HTTP code. The Google «Скоро» permanently disabled
button. The four-panel kebab purchase wizard. The hand-rolled account carousel. The `✕` text
character used as a close glyph. Every `Toast` used for actionable feedback. The six single-choice
settings dialogs. Compact layout mode. Onboarding as a gate.

## 7.3 Parity gaps, logged rather than hidden

| # | Gap | Direction | Decision |
|---|---|---|---|
| PG-1 | «Проверить обновления» | Desktop only | Android is not distributed via GitHub releases. The row does not exist on Android and no placeholder is shown |
| PG-2 | Post-sign-in sync overlay | Desktop only today | Closed in Wave 3: Android gets the same surface, same stages, same failure path |
| PG-3 | Telegram / email link endpoints | Desktop only | `RequestLinkTelegram`, `RequestLinkEmail`, `CreateAppHandoff` are absent from the Android API client. Until they are ported, Android's «Привязать» opens the site handoff. Re-evaluated at the end of Wave 3 |
| PG-4 | Global hotkeys, UI scale, tray | Desktop only | Platform capability. `settings/window` exists only on desktop; the Android hub has 22 rows where desktop has 24 |
| PG-5 | «Перенести на ТВ» | Android only | Platform capability. `settings/tv` exists only on Android |
| PG-6 | Per-app proxy app list | Both, different sources | Android enumerates packages, desktop enumerates executables. Same screen, same archetypes, different picker contents |
| PG-7 | Avatar photo picker | Android only | Desktop has monogram only. Either port the picker in Wave 7 or drop the Android picker; recorded as an open decision |
| PG-8 | Multi-select on the server list | Desktop only | Added in Wave 5. Android gets long-press selection in the same wave or this gap stands |

## 7.4 Decisions this file takes

Recorded here so they are not re-litigated per screen. Each is inside existing law; none needs an
owner ruling.

| # | Decision | Basis |
|---|---|---|
| C-1 | Account is REBUILD on both platforms; the desktop is not the parity target | Owner's demand; `21-account-survey.md` 1.4 and 2.3 |
| C-2 | Signed out, «Аккаунт» stays in the navigation and becomes the sign-in gate. Android stops hiding it | `11-app-structure.md` 2.2; one product, one answer |
| C-3 | The purchase decision has one grammar everywhere: sheet on Android, anchored flyout on desktop | §13 translation table; `21-account-survey.md` 5.1 |
| C-4 | Payment history is a divided list, not a card grid; devices are rows in one card | §2.4.3; §4.4 |
| C-5 | Device unlink uses act-plus-undo, not a confirm | §7.5; the device re-registers on the next connect |
| C-6 | «Отменён» is neutral, not yellow. Health chips get their own class names | R12 |
| C-7 | The raw HWID leaves the device row and lives in the row's action sheet | `21-account-survey.md` 1.6 |
| C-8 | Trial, promo, QR, rename and referral stats each get a minimal surface rather than being deleted: trial and promo on `account/buy`, QR and rename on `account/subscription/{id}`, referral stats as the referral row's subtitle | §5.7 of the account survey: shipping endpoints with no surface is a decision by omission |
| C-9 | Clash proxies and connections are cut from the product | `11-app-structure.md` 16, D-11 |
| C-10 | Buy, Devices and History on desktop are RESTYLE/REBUILD, not KEEP | 0.6 |
| C-11 | The tariff word appears once per subscription: as the badge chip on the card. The hero identity line does not repeat it | §2.4.4, the decoration tell |
| C-12 | Sub-page titles are `Title` 16/700 on both platforms; `ToolbarBrandTitle` and desktop `Headline` toolbars are both wrong | §4.8 |

---

# PART 8 - PER-SCREEN ACCEPTANCE

Copy this block into the pull request for each screen. A box that cannot be honestly ticked means
the screen is not done. It is the §16 pre-flight, narrowed to what a single screen can be judged on.

```
Screen: ______________________  Platform: Android / Desktop  Wave: __  Verdict: __________

Frame
[ ] Header is H1 / H2 / H3 / H4 / H5, at 56, page background, no divider at rest,
    scroll hairline fades in over 220ms
[ ] Rhythm is R1..R6 and every gap is 4 / 8 / 12 / 16 / 24 / 32
[ ] Gutter 16 (24 at sw600dp / >= 1000px), content capped at 720 and centred

Components
[ ] Every row is one of the five archetypes; text origin 68; hairline inset 68; one trailing element
[ ] Every button is one of the five variants; radius 16; minHeight 48 or 52; label on the ramp
[ ] Every field has a label above, a helper slot, blur validation, an error below
[ ] Zero Spinner / ComboBox / single-choice AlertDialog
[ ] Zero screen-local control styles; zero components not in 22-components.md

Colour and type
[ ] One filled accent surface; no accent row title; no accent tile on a non-categorical row
[ ] Tiles neutral unless genuinely categorical (max 3 coloured on one screen)
[ ] All text via a ramp role; no inline size; no synthetic bold
[ ] Numbers in the Numeric role with tnum; ₽ with a non-breaking space; thin-space thousands
[ ] Checked in dark, light and mono

States
[ ] default  [ ] first run  [ ] loading (skeleton, after 300ms)  [ ] empty  [ ] error
[ ] offline  [ ] partial  [ ] long content  [ ] short content  [ ] gated  [ ] success
[ ] Empty and error use the EmptyState component and the copy table of 0.4
[ ] Product gates that apply: нет подписки / истекает / истекла / триал / Telegram не привязан /
    нет серверов / подключение / подключено / отключение / ошибка туннеля / лимит устройств

Interaction
[ ] Pressed (0.97 objects, background step rows), focus on every focusable, disabled 0.38
[ ] Inline loading holds the width and hides the label
[ ] Double-press impossible
[ ] Targets 48dp Android / 32px desktop, 8 apart
[ ] Back / Esc works and restores scroll, filter and input

Motion
[ ] Only tokenised durations and ease-out curves; exit is 75% of enter
[ ] Reduced motion verified by toggling it
[ ] No new hero moment; no page-load choreography

Copy
[ ] Russian, sentence case, formal «вы», terminology lock of §9.3
[ ] No dash characters, no three-dot ellipsis, no final period on labels, no visible error codes
[ ] Errors state cause and fix and offer recovery

Deletion
[ ] Every file this screen replaces is deleted in this same change
[ ] Nothing references the deleted files

Verification
[ ] Run and looked at: dark + light, default + 200% scale, minimum window / smallest device
[ ] The seven questions of §2.4 answered in writing
[ ] Score >= 18/20 on the five dimensions of 17.1, no dimension below 3
```

---

## Change log

| Date | Change |
|---|---|
| 2026-07-26 | Created. Per-tab conformance verdicts for 45 Android and 48 desktop surfaces, the universal replacement table, the merge and delete lists, the 8-wave order, and the per-screen acceptance block. Records verdict revisions C-1 and C-10, which supersede `01-inventory-android.md` 4 and `02-inventory-pc.md` 4.4 on the Account family |
