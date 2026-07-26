# Android - Главная (home / connect tab) audit

**Verdict: REBUILD. 6 / 20 against the five dimensions of `audit.native.md`. Ship bar is 18 / 20
with nothing below 3. Four of the five dimensions are below 3.**

Law: `docs/design2026/00-rules.md`, sections 18 D-1 to D-12 binding. Governing spec:
`13-start-screen.md`. Supporting: `11-app-structure.md`, `22-components.md` (R15 vocabulary),
`24-tab-conformance.md` A-01 / R5 / H2, `32-master-plan-android.md`, `31-self-assessment.md`.
Owner request 0.4.10 applies: this screen is redesigned from scratch, minimalist. Polish does not
satisfy it, and nothing below is written as polish.

Every claim carries file:line. Every number in section 4 was produced by a grep scoped to the six
layouts in section 1 and is reproducible from the command printed above it.

---

## 1. The surface under audit

| File | Lines | Role today | Verdict |
|---|---|---|---|
| `res/layout/activity_main.xml` | 705 | Shell + all four tabs. Home is `group_home` `NestedScrollView` `:41` to `:452` | **REBUILD** `:41-452` into `fragment_home.xml`; the shell keeps `:455-705` |
| `res/layout/layout_home_account.xml` | 155 | Account chip + a dead signed-out block | **DELETE.** `group_login` (`:23`) is unreachable: `MainActivity.kt:1178` sets `header.groupLogin.isVisible = false` unconditionally |
| `res/layout/layout_home_empty.xml` | 139 | The de-facto sign-in screen for every new install | **DELETE.** Replaced by a state of Главная (13 s. 9, 24 s. 3.1.9) |
| `res/layout/layout_subscription_meta_bar.xml` | 257 | Provider card, one per carousel page | **DELETE from Главная.** Moves to Серверы as the provider group header |
| `res/layout/item_subscription_card.xml` | 75 | **Not a Home file.** Used only by `SubscriptionPagerAdapter`, whose only caller is `AccountFragment.kt:137` | Out of scope here; see W-14, it is the second visual treatment of one object |
| `res/layout/toast_status.xml` | 20 | Custom `Toast` pill | **DELETE.** 1.4.8 and 22 s. 14: Snackbar or inline |
| `ui/MainActivity.kt` | 3 261 | Shell + Home + Servers + Settings + account gate | Home logic moves out; the file becomes a shell |
| `ui/HomeMetaPagerAdapter.kt` | 68 | Home carousel adapter | **DELETE** with the meta bar |
| `ui/SubscriptionPagerAdapter.kt` | 104 | Аккаунт carousel adapter | Out of scope; W-14 only |
| `viewmodel/MainViewModel.kt` | 739 | Server cache, provider groups, ping | Gains `HomeUiState`; keeps its data role |

**The one-glance job (13 s. 1)** is: am I protected, through what, what do I do next. The shipped
screen answers none of the three cleanly. The tunnel state is carried by a shield tint with no word
(`activity_main.xml:293-306` comment: "Connection STATE is conveyed by the shield/ring colour, not
by text"), which is a direct hit on 6.3 "never colour alone". The server identity appears under the
disc only while connected (`MainActivity.kt:2017-2022`). The next action is spread across a `+`
button, two onboarding buttons, a buy button, a link-Telegram button and two login buttons, up to
four of which can be on screen at once.

---

## 2. Scores, per `audit.native.md`

| # | Dimension | Score | Load-bearing evidence |
|---|---|---|---|
| 1 | Accessibility | **1** / 4 | Connect disc has no focus state and its `contentDescription` is the Tasker string (`activity_main.xml:259`, `MainActivity.kt:1747`, `:1815`); 7 controls under 48dp; state carried by colour alone; traffic label 2.91:1; no live region; 8 raw `sp` literals defeat the ramp |
| 2 | Performance | **1** / 4 | `rv_home_servers` is de-virtualised inside a `NestedScrollView` (`activity_main.xml:447`); `measureHomeMetaHeight()` inflates, binds and measures **every** subscription on the main thread in a pre-draw pass (`MainActivity.kt:1014-1036`); `notifyDataSetChanged()` on the visible carousel (`HomeMetaPagerAdapter.kt:34`, `MainActivity.kt:860`) |
| 3 | Appearance and theming | **1** / 4 | Four banned gradients and one banned glow are the screen's ground; 6 raw hex in the connect drawables; 109 raw `dp` literals; every Russian string on this screen is set in a Cyrillic-free face |
| 4 | Platform conformance | **2** / 4 | Hand-rolled bottom bar instead of `NavigationBarView`; `MaterialCardView` used as a control; custom `Toast`; no `Fragment` for the tab; the `↑` `↓` `✕` text glyphs |
| 5 | Adaptivity | **1** / 4 | Fixed `52dp` button height (`layout_home_empty.xml:111`); fixed `230dp` hero; `maxLines="1"` on the one status string; two weighted spacers make the first-run layout depend on viewport height; no `values-sw600dp` for this screen |

**Total 6 / 20.** Section 17.2: every section 1 ban hit is at least P1 and every missing section 15
state is at least P1. This screen carries 9 ban hits and is missing 8 states.

---

## 3. The Departament slop test (2.4), answered

| # | Question | Answer |
|---|---|---|
| 1 | Category reflex | **Fail.** A radial navy-to-black page gradient (`drawable-night/bg_home_gradient.xml`, `#1B2D50` centre), a radial accent halo behind the shield (`drawable-night/bg_connect_glow.xml`), a double accent ring at rest and a breathing loop is the "gamer VPN" reading named in 2.4.1, not the Incy near-black ramp |
| 2 | Second-order reflex | Pass. Not terminal-green, not Linear-grey-with-violet |
| 3 | Uniform-card tell | **Fail.** The home stack is: account card, hero, memory card, onboarding card, meta card, then N server cards. Six rounded rectangles before any content |
| 4 | Decoration tell | **Fail.** Point at the glow, the outer ring, the inner ring, the page gradient, the nav scrim, the `42dp` invisible spacer (`activity_main.xml:73-77`) and the `↑` `↓` arrows: none communicates anything a word does not already carry |
| 5 | Copy tell | **Fail.** The default locale is English. «Приветствуем!», «Не подключено», «Соединено, нажмите для проверки», «Автообновление — 1 ч.» |
| 6 | State tell | **Fail.** See section 6 |
| 7 | Trust test | **Fail.** The primary control is a card that does not draw focus, does not draw disabled, and whose accessible name says «Выберите профиль» |

---

## 4. Mechanical checks, scoped to this screen

Scope for every command below:

```bash
cd /home/user/dp/V2rayNG/app/src/main/res
F="layout/activity_main.xml layout/layout_home_account.xml layout/layout_home_empty.xml \
   layout/layout_subscription_meta_bar.xml layout/item_subscription_card.xml layout/toast_status.xml"
```

| 00-rules 1.5 / 9.7 check | Command | Result | Floor |
|---|---|---|---|
| Raw colour literals in layouts | `grep -rnE '(android:(textColor\|background\|tint\|backgroundTint\|strokeColor)\|app:tint\|app:strokeColor)="#' $F` | **0** | 0. Clean, and it must stay clean |
| `textAllCaps="true"` | `grep -rn 'textAllCaps="true"' $F` | **0** | 0. Clean. Two `="false"` remain at `layout_home_empty.xml:114`, `:131`, which are dead weight once the button style owns it |
| Face or size chosen in a layout (D-2) | `grep -rn 'android:fontFamily\|android:textSize' $F` | **8** | 0 |
| Off-scale `dp` against the 1.5 allow-list | `grep -rnoE '"(-?[0-9]+)dp"' $F \| grep -vE '"(0\|1\|2\|4\|8\|12\|16\|20\|22\|24\|28\|32\|36\|40\|44\|48\|52\|56\|64\|72\|80\|100\|120\|152\|160\|176\|212\|230)dp"'` | **25** | 0 |
| **Raw `dp` literals of any value** (rule 3: no UI file holds a value a token covers) | `grep -rnoE '"(-?[0-9]+)dp"' $F` | **109** | 0 |
| Raw `sp` literals | `grep -rnoE '"(-?[0-9]+)sp"' $F` | **8** | 0 |
| Nested `MaterialCardView` | per-file count | `activity_main.xml` **4**, `layout_home_empty.xml` **2**, `item_subscription_card.xml` **2** | Open tags: 2 / 1 / 1. No nesting found. **Pass** |
| `android:textStyle="bold"` (5.4, synthetic bold) | `grep -rn 'textStyle="bold"' $F` | **3** (`layout_home_account.xml:108`, `layout_home_empty.xml:116`, `toast_status.xml:20`) | 0 |
| Typographic characters as chrome (1.4.4) | `grep -rn '↑\|↓\|✕\|∞' $F` | **3 shipped** (`activity_main.xml:98`, `:140`, `layout_home_account.xml:71`) plus `∞` in `tools:` and comments | 0 |
| Em / en dash in shipped copy (9.7) | `grep -rn -e '—' -e '–' values*/strings*.xml` filtered to this screen's keys | **2**: `values/strings.xml:346`, `values-ru/strings.xml:353` (`sub_auto_update_label`, rendered by `MainActivity.kt:1359`) | 0 |
| Three dots for `…` | `grep -rn '\.\.\.' values*/strings*.xml` | **0** | 0. Clean |
| Emoji in shipped copy | the 1.5 python one-liner | **0** | 0. Clean |

**The 25 off-scale values, itemised.** `42dp` x3 (`activity_main.xml:74`, `:163`, `:164`), `3dp` x13
(`:251` track thickness, then `:560/569-571`, `:601/608-610`, `:641/648-650`, `:685/692-694` - the
four nav items' `3dp` margins and `3dp` indicator height), `88dp` x1 (`:262`, the disc corner
radius), `34dp` x4 (the nav indicators), `18dp` x2 (`layout_home_account.xml:148-149`, the chevron),
`26dp` x1 (`layout_home_empty.xml:118`, `cornerRadius` - a capsule, the exact shape D-6 rejects),
`13dp` x1 (`layout_subscription_meta_bar.xml:39`).

**The 8 face / size hits.** `activity_main.xml:113` `13sp`, `:128` `14sp`, `:155` `13sp`;
`layout_home_account.xml:73` `16sp`, `:107` `16sp`; `layout_subscription_meta_bar.xml:175` `11sp`,
`:226` `12sp`; `toast_status.xml:19` `14sp`. Every one overrides a `textAppearance` that was already
applied on the same view, so the ramp is declared and then defeated on the same element
(`activity_main.xml:110-113` is the clearest case: `TextAppearance.App.Numeric` then `13sp`).

**D-1 / D-2, the whole-screen finding.** `res/font/` holds `golos_text_regular|medium|bold.ttf`, so
the faces are vendored. `res/values/styles.xml` has **not** migrated: `TextAppearance.App.Display:57`,
`.Headline:66`, `.Title:75`, `.Chip:110`, `.Numeric:123`, `SettingsSectionLabel:7` and
`ToolbarBrandTitle:36` all still declare `android:fontFamily="@font/space_grotesk"`, and there is no
`@font/ui_sans` family. Consequence for this screen: «Приветствуем!», «Не подключено», «Выберите
сервер», the account name, the subscription title and every row title are handed to a binary that
maps zero codepoints in U+0400-U+04FF, so Android silently substitutes Roboto. This is a P1 by 5.1
and it is a token-layer fix, not a Home fix - Home must not ship before it lands.

---

## 5. Ban hits

Section 1 of the law. Each row is at least P1 by 17.2.

| # | Ban | Hit | Evidence |
|---|---|---|---|
| B-1 | 1.4.3 no decorative gradients | Page background is a radial gradient | `activity_main.xml:8` -> `drawable/bg_home_gradient.xml`, `drawable-night/bg_home_gradient.xml` (`#1B2D50` -> `#0E141F` -> `#0A0B0D`), `drawable/bg_home_gradient_mono.xml` |
| B-2 | 1.4.3 no glows | Radial accent halo behind the shield | `activity_main.xml:207-212` -> `drawable/bg_connect_glow.xml`, `drawable-night/bg_connect_glow.xml` (`#594C8DFF` -> `#004C8DFF`) |
| B-3 | 8.1 decorative motion | The glow **breathes** on a 850ms infinite reverse with `AccelerateDecelerateInterpolator` | `MainActivity.kt:1932-1943`. 850 is in no scale (3.7) and `AccelerateDecelerate` is not ease-out (8.3) |
| B-4 | 1.4.3 no decorative gradients | Bottom-nav scrim gradient | `activity_main.xml:512-517` -> `drawable/bg_bottom_nav_scrim.xml` |
| B-5 | 1.4.4 no typographic chrome | `↑` and `↓` as speed glyphs, `✕` as a close button | `activity_main.xml:98`, `:140`; `layout_home_account.xml:71` |
| B-6 | 1.4.6 no raw colour literals | 6 raw hex in the connect ring and glow drawables | `bg_connect_ring.xml` `#2E1E5FC7` `#701E5FC7`, night `#334C8DFF` `#804C8DFF`, mono `#33808080` `#66808080` |
| B-7 | 1.4.5 no off-scale spacing | 25 off-scale, 109 raw `dp` | section 4 |
| B-8 | 1.4.8 no `Toast` | `showStatusToast()` is the tunnel's entire feedback channel, fired on connect, connected, failed, disconnected | `MainActivity.kt:1868-1879`, called at `:202`, `:576`, `:612`, `:616`, `:617`, `:1621`, `:1679`, `:1275`, `:1306` |
| B-9 | 1.4.10 no Latin UI text | Default locale is English for the strings this screen renders | `values/strings.xml:56-59` («Connecting…», «Proxy connected», «Disconnected», «Couldn't connect»), `:137` «Select a config», `:334` `0 KB/s`, `:355` «App memory», `:445-446`, `:350-354` |
| B-10 | 8.9 no page-load choreography | `playColdStartAssemble()` runs `shield_assemble.xml` on the hero at every cold start | `MainActivity.kt:327`, `:339-346` |
| B-11 | 1.1 the hero-metric template | Big timer figure + two stat chips + an accent-washed hero | `activity_main.xml:58-172` (`tv_connection_time` centred between `↑`/`↓` speeds) sitting on B-1 and B-2 |
| B-12 | 1.4.11 no em/en dash | «Автообновление — 1 ч.» | `values-ru/strings.xml:353`, `values/strings.xml:346` |
| B-13 | 3.6 accent budget, one filled accent surface | Up to four accent surfaces at once in the signed-out empty state: tonal Telegram button (`layout_home_empty.xml:106-121`, `colorPrimaryContainer`), filled `btn_home_add_qr:42`, the accent ring, the accent glow | 4.3: "Two filled accent buttons on one screen is a defect" |
| B-14 | 1.3 display fonts in UI labels / D-2 | Every Russian string on this screen drawn by a Cyrillic-free face | `values/styles.xml:57,66,75,110,123` |

**Not a hit, recorded so no one re-opens it:** nested cards. The four `MaterialCardView` tokens in
`activity_main.xml` are two open tags (`card_connect:253`, `card_memory:312`) plus two close tags,
and neither contains the other. `layout_home_empty.xml` and `item_subscription_card.xml` are one card
each.

---

## 6. State matrix

Section 15 plus the product gate states. `Y` = drawn today, `part` = partially drawn, `-` = absent.

### 6.1 Section 15 states

| State | Today | Evidence | Required |
|---|---|---|---|
| Default | Y | - | keep |
| First run | part | `layout_home_empty.xml` card floating on a gradient, centred by two weighted spacers (`activity_main.xml:374-408`) | Variant A of 13 s. 11.1: a state of Главная, disc disabled 0.38, one gate CTA. No card, no welcome heading |
| Loading | **-** | No skeleton anywhere on Home. `showLoading()` routes every subscription load onto the **connect arc** (`MainActivity.kt:1983-1995`, `:1968-1976`) | Static skeletons on the header row and the Подписка row after 300ms (13 s. 11.3). The connect arc means only "the tunnel is negotiating" |
| Empty | part | `home_empty_title` «Пока нет подписок» + subtitle + **two** buttons | 9.5 formula, one action: «Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» / «Купить» |
| Error | **-** | A tunnel failure produces `Toast` «Не удалось подключиться» (`MainActivity.kt:1679`, `:616`) and nothing on the screen | Status line «Не удалось подключиться» in `@color/ping_bad` + «Нажмите, чтобы повторить» + Snackbar with «Повторить» |
| Offline | **-** | No offline concept on this screen | 9.6: keep last data, mark stale, strip 3 «Нет сети. Показаны последние данные.» / «Повторить», disc stays enabled |
| Partial | **-** | `updateAccountGate()` (`:1167`) and `refreshServerLists()` (`:879`) both hide-or-show wholesale | Subscription row live while the header row is still a skeleton, never blocked on each other |
| Long content | **-** | `tv_connection_status` is `maxLines="1"` `ellipsize="end"` at `activity_main.xml:302-304`; `tv_sub_title` and `tv_account_name` likewise | A 60-char remark ellipsises on one line under the disc; the Подписка subtitle wraps to 2 and the row grows |
| Short content | Y | one server renders | keep; «1 сервер · 1 провайдер» |
| Disabled / gated | part | Shield dims to 0.38 when no server is selected (`MainActivity.kt:1823-1824`), but `card_connect.isEnabled` is never set false, so the disc still takes the tap and `startV2Ray()` answers with a `Toast` (`:1645-1647`) | 13 s. 11.2: disc `isEnabled=false`, ring `colorOutline`, shield 0.38, gate block carries the unlock action |
| Success | part | 600ms sonar + `CONFIRM` haptic fire (`MainActivity.kt:1754`, `:1794-1802`) | keep the beat, retune it: 1.35 not 1.6, alpha 0.6 -> 0, and add the word «Подключено» |

### 6.2 Product gate states

| Gate state | Today | Evidence | Required |
|---|---|---|---|
| нет подписки | part | Signed in with no servers: buy CTA + optional link-Telegram, both filled-weight, inside the onboarding card (`MainActivity.kt:1250-1265`) | Gate block: «Осталось выбрать тариф.» / «Купить подписку». One filled button |
| подписка истекает | **-** | Nothing on Home reads expiry. `tv_expiry` on the meta card prints «до 12.08.2026» in Caption with no warning treatment (`bindMetaBar`, `MainActivity.kt:1433-1437`) | Strip 5 «Подписка заканчивается 14 августа.» / «Продлить» + the «Истекает» chip on the Подписка row |
| подписка истекла | part | `bindMetaBar` `MainActivity.kt:1429-1432` paints `tv_expiry` `@color/colorPingRed` `#E5484D` - a **third red**, outside the token set (1.4.1) | Strip 1 + the «Истекла» chip; red text from `@color/ping_bad` |
| триал | **-** | No trial concept on Android at all | «Пробный период до 14 августа» on the Подписка row; no upgrade or renew affordance on this screen |
| Telegram не привязан | part | `btn_home_link_tg` appears only in the signed-in **and** no-servers state (`MainActivity.kt:1264`); the CTA banner path is dead (`:1178` kills `groupLogin` unconditionally, which owns `cta_link_telegram`, so `updateLoginCtaVisibility()` at `:1208-1216` sets visibility on a view inside a `GONE` parent) | Not a Главная concern. It belongs to Аккаунт. Delete both paths from Home |
| нет серверов | part | `updateHomeEmptyState()` `:805-829` hides the hero entirely (`:809 binding.cardHero.isVisible = !empty`) | The disc never disappears. It renders disabled with the gate below it (13 s. 11.1 variant F) |
| подключение | Y | arc + breathing glow + «Подключение…» toast | Sweep on the ring only, status word «Подключение…», no glow, tap cancels |
| подключено | part | Shield fills blue, timer starts, toast «Прокси подключён» | Add the word «Подключено» in `colorTertiary`; the numeric strip becomes visible |
| отключение | **-** | No disconnecting state exists. `handleFabAction():1612-1617` calls `stopVService` and waits for the observer | «Отключение…», disc disabled, strip fading out at 165ms |
| ошибка туннеля | **-** | Toast only | Ring `colorError`, status «Не удалось подключиться» + «Нажмите, чтобы повторить» |
| лимит устройств | **-** | Not modelled on Android | Strip 2 «Достигнут лимит устройств.» / «Устройства» |

**8 of 11 section-15 states and 6 of 11 gate states are absent or wrong. By 1.4.13 and 17.2 that
alone forces the rebuild.**

---

## 7. The connect control

13 s. 5 specifies one disc, one ring, one glyph. The shipped control is a five-layer stack in a
230dp frame (`activity_main.xml:200-291`) communicating one boolean.

| Layer | Line | What it is | Verdict |
|---|---|---|---|
| `view_connect_glow` | `:207-212` | radial accent halo, breathes at 850ms | **DELETE** (B-2, B-3) |
| `view_connect_ring` | `:214-218` | `bg_connect_ring.xml`, **two** strokes at 1.5dp and 2dp, raw hex, accent-tinted at rest | **REPLACE** with one 3dp stroke, `@dimen/stroke_ring`, tinted at runtime |
| `view_connect_pulse` | `:223-230` | the confirm ring, same drawable | **KEEP the beat**, single stroke, retune the anim |
| `progress_connect` | `:237-251` | `CircularProgressIndicator` at `indicatorSize` **212dp**, `trackThickness` 3dp, `trackCornerRadius` **4dp** | **RESIZE to 176dp**, corner 2dp, so the arc travels the ring instead of orbiting outside it |
| `card_connect` | `:253-289` | `MaterialCardView` 176dp, `cardCornerRadius="88dp"`, `cardBackgroundColor="?attr/colorSurfaceContainerHigh"`, `rippleColor="@android:color/transparent"`, `strokeWidth="0dp"` | **REPLACE** with a `View` + `bg_connect_disc.xml` |

Findings against the law, each independently P1:

1. **No focus state.** `card_connect` sets `focusable="true"` (`:258`) and draws nothing on focus.
   R7 makes a 2dp ring mandatory on every focusable control, and 7.1's note records that
   `bg_server_row.xml` is the only `state_focused` drawable in the entire app. The product's primary
   control is unreachable by keyboard, D-pad and switch access in practice.
2. **No disabled state.** `isEnabled` is never set false. `applyIdleState()` `MainActivity.kt:1823-1824`
   dims only `imgConnect` to `0.38`, so the disc still accepts the tap, `startV2Ray()` finds no
   server and answers with `toast(R.string.title_file_chooser)` = «Выберите профиль» (`:1645-1647`).
   R6 requires 0.38 on the whole control and `isEnabled=false`.
3. **The accessible name is the wrong string.** `android:contentDescription="@string/tasker_start_service"`
   (`activity_main.xml:259`), and at runtime it toggles between `tasker_start_service` and
   `action_stop_service` (`MainActivity.kt:1747`, `:1815`). 10.7 and 13 s. 14 require state **and**
   action: «Отключено. Нажмите, чтобы подключить» / «Подключение. Нажмите, чтобы отменить» /
   «Подключено. Нажмите, чтобы отключить» / «Недоступно. Нет подписки».
4. **Press scale is 0.94, imperatively.** `animateConnectPress()` `MainActivity.kt:1889-1905` animates
   to `0.94f`. D-11 and R4 fix the number at **0.97** on Android; 0.94 is the documented **desktop**
   exception (13 s. 16.3), and it has leaked to the wrong platform. `@anim/press_scale` is already
   correct at 0.97 and is not attached to this control.
5. **No double-press guard.** `binding.cardConnect.setOnClickListener { animateConnectPress(); handleFabAction() }`
   (`MainActivity.kt:310-313`). R9 and `@integer/input_debounce` 500 exist, `@id/tag_last_click` is
   declared in `values/ids.xml:13`, and neither is used here. A double tap dispatches
   `handleFabAction()` twice; the second call reads `isRunning` before the first has settled.
6. **The ring is accent at rest, and it fails the contrast floor.** Night `bg_connect_ring.xml`
   draws `#804C8DFF` (accent at 50%) and `#334C8DFF` (accent at 20%). Composited on `#0A0B0D` those
   are **2.33:1** and **1.29:1**. 6.8 and 14.1 set a 3:1 floor for a control boundary, and D-9's
   `color_outline_control` `#646C7C` exists precisely for this. 13 s. 19.1 S-10 resolves it to
   `color_on_surface_dim` `#6E7480` at 4.19:1 on the ground and 3.32:1 on the disc fill; the token
   `md_theme_onSurfaceDim` already exists (`values-night/colors.xml:121` -> `ink_50` `#6E7480`) and
   is not wired to a theme attribute yet.
7. **Accent pixels at rest.** 13 s. 1 requires **zero** accent pixels on the disconnected, ungated
   screen. Today the ring, the glow, the two `↑`/`↓` arrows (`activity_main.xml:100`, `:142`) and the
   nav indicator are all accent before the user touches anything.
8. **The disc plane is wrong.** `colorSurfaceContainerHigh` `#1A1D21` is P2, the transient plane.
   13 s. 5.1 puts the disc on `colorSurfaceContainerHighest` `#20242B`, P3, the recessed-inset plane,
   so the control reads as part of the instrument.
9. **`cardCornerRadius="88dp"`** is a raw literal where `@dimen/radius_pill` 100 clamps to the same
   circle. This is legal by shape (width == height) and illegal by token discipline (rule 3).
10. **`connect_confirm.xml` overshoots the spec.** It scales 1.0 -> **1.6** with alpha 1.0 -> 0;
    13 s. 12.3 specifies 1.0 -> **1.35** with alpha **0.6** -> 0. Duration and curve are already
    correct (`@integer/motion_emphasis`, `ease_out_quint`). The 600ms hero moment stays; it is
    retuned, never duplicated.
11. **The connect arc is overloaded.** `refreshConnectArc()` `MainActivity.kt:1968-1976` spins the
    arc when `connectArcConnecting || connectArcSubLoads > 0`, so a background subscription refresh
    makes the connect control look like it is negotiating a tunnel. 13 s. 5.3: the sweep runs only
    while the core is negotiating. This is the "indeterminate indicator that lies about the system"
    case by name.

**Spec correction, recorded here so `13-start-screen.md` can be amended.** 13 s. 5.5 states that the
current build tints the filled shield `?attr/connectedColor` green `#22C55E`. It does not. Both
themes map `connectedColor` to `color_fab_active` (`values/themes.xml:75`), which is `#1E5FC7` light
and `#4C8DFF` dark (`values-night/colors.xml:23`). The shipped filled shield is already blue and the
comment at `values/themes.xml:71` says so. The green `color_connected` `#22C55E`
(`values-night/colors.xml:25`) is referenced only by `updateMemoryCard()` (`MainActivity.kt:2009`),
which dies with the memory card. The rule the spec is defending is right; the observation is stale.

---

## 8. Accent budget and the hero-metric ban

3.6 allows **one** filled accent surface per screen, plus accent for the current nav destination,
the selected item, a focus ring, a link and a live progress indicator.

Counted on the signed-out first-run screen as shipped:

| # | Accent surface | Line |
|---|---|---|
| 1 | `btn_home_add_qr`, default filled `MaterialButton` | `layout_home_empty.xml:42-50` |
| 2 | `btn_home_login_tg`, `backgroundTint="?attr/colorPrimaryContainer"` at `52dp` with `cornerRadius="26dp"` | `layout_home_empty.xml:106-121` |
| 3 | the connect ring, accent at 20% and 50% | `activity_main.xml:214-218` |
| 4 | the connect glow, accent radial | `activity_main.xml:207-212` |
| 5 | the nav active indicator | `activity_main.xml:567-573` (legitimate) |

Four illegitimate accent surfaces where the budget is one. Item 2 is additionally a **capsule** -
`cornerRadius="26dp"` on a `52dp` control is a full stadium, the exact shape D-6 records the owner
rejecting, and `layout_home_empty.xml:111` fixes `layout_height="52dp"` where R2 requires
`wrap_content` + `minHeight` + zeroed insets.

**Hero-metric template (1.1).** `activity_main.xml:58-172` is the template verbatim: a centred
figure (`tv_connection_time`, `00:00:00`, `14sp` 700) flanked by two stat readouts
(`tv_upload_speed`, `tv_download_speed` at `13sp` 700 with `↑`/`↓` glyphs), over an accent-washed
hero. 13 s. 19.1 S-3 removes uptime from this screen entirely: it is not a measurement of the
tunnel's quality and it moves to the ongoing notification. The replacement strip is **приём /
отдача / задержка**, visible only while connected, so a negotiating tunnel never prints a fake `0,0`.

---

## 9. The subscription meta bar carousel, and per-subscription scoping

`HomeMetaPagerAdapter` puts one `layout_subscription_meta_bar.xml` page per subscription
(`MainActivity.kt:944-1007`). Its own doc comment (`HomeMetaPagerAdapter.kt:12-16`) states the
contract: "Per-page actions carry the page's own subscription id; list-wide actions (collapse / ping
/ refresh) are page-independent."

**That contract is the defect.** Three of the six controls on a page act on every subscription while
sitting on one subscription's card.

| Control | Page-scoped? | Wiring | Consequence |
|---|---|---|---|
| `btn_pin` | **yes**, `subId` | `HomeMetaPagerAdapter.kt:61` -> `toggleHomePin(subId)` `MainActivity.kt:1286` | correct |
| `btn_support` | **yes**, `subId` | `:62` -> `openSubUrl(...supportUrl)` `MainActivity.kt:955` | correct |
| `btn_telegram` | **yes**, `subId` | `:63` -> `openSubUrl(...supportUrl)` `MainActivity.kt:956` | correct scope, **wrong target**: both buttons open the same `supportUrl` field. Two controls, one destination |
| long-press delete | **yes**, `subId` | `:64` -> `confirmDeleteSubscription(subId)` | correct scope, but a destructive action bound to an undiscoverable long press on a card (7.5, 7.6) |
| `btn_refresh` | **NO** | `:60` -> `onRefreshAll()` -> `refreshHomeSub()` `MainActivity.kt:1294` -> `mainViewModel.updateConfigViaSubAll()` `MainViewModel.kt:199-201` | Pressing refresh on provider B's card refreshes **A, B and C**. A per-subscription path exists and is unused: `MainViewModel.kt:204` `AngConfigManager.updateConfigViaSub(SubscriptionCache(subscriptionId, subItem))` |
| `btn_ping` | **NO** | `:59` -> `onPingAll()` -> `mainViewModel.testAllServers()` `MainActivity.kt:948-951` | Pings every server of every provider |
| `btn_collapse` | **NO** | `:58` -> `onToggleList()` -> `toggleHomeServerList()` | Collapses the whole list, not this provider's group |

**The list under the carousel is not scoped either.** `refreshServerLists()` `MainActivity.kt:882`
feeds `homeAdapter` the entire `mainViewModel.serversCache` with `showHeaders = false`. So the page
dots say "you are looking at provider B" and the list below shows A + B + C with no headers. There
is no visual or behavioural relationship between the carousel page and the list it appears to head.

**Resolution.** 13 s. 2.1 and 24 s. 3.1.3 delete the meta bar from Главная outright and move it to
Серверы as the provider group header, where the group it heads is directly beneath it and every
action has an unambiguous scope. Главная keeps one «Серверы» row whose subtitle is a count, and one
«Подписка» row that opens Аккаунт scoped to that subscription's uuid. That removes the class of bug
rather than re-scoping three lambdas.

**When the meta bar is rebuilt on Серверы**, the three list-wide actions become group-scoped and the
Telegram button either gets its own field or is deleted. The `updateConfigViaSub` overload at
`MainViewModel.kt:204` is the call the group header uses.

**Two further defects the carousel carries today:**

- `measureHomeMetaHeight()` `MainActivity.kt:1014-1036` inflates a full `layout_subscription_meta_bar`
  binding, calls `MmkvManager.decodeSubscription(id)`, runs `bindMetaBar` and `measure()` for
  **every** subscription, inside `doOnPreDraw`, on the main thread, on every list rebuild. With four
  providers that is four inflations plus four MMKV reads plus four measure passes before the frame.
  11.5 forbids synchronous I/O on the main thread in a UI path, and this is in the pre-draw path.
- `HomeMetaPagerAdapter.submit()` `:31-35` and `MainActivity.kt:860` call `notifyDataSetChanged()`
  on a visible pager. 11.5 forbids it. No `DiffUtil`, no stable IDs.
- The traffic pill prints an `11sp` label directly over a moving `colorPrimary` fill
  (`layout_subscription_meta_bar.xml:153-177`). `#F2F4F8` on `#4C8DFF` measures **2.91:1**, below the
  4.5:1 floor, and the ratio changes as the bar fills, which is the failure mode 6.7 names.

---

## 10. Load-bearing fixes on this screen. Do not undo.

Three defects were fixed and their code comments carry the reasoning. Every work item in section 12
was checked against all three; none of them touches these paths except as noted.

| # | Defect | Fix location | The invariant |
|---|---|---|---|
| **L-1** | Tapping a server row silently connected or reconnected | `MainActivity.kt:1524-1550` `setSelectServer()`, with `promptApplySelectedServer()` at `:1552-1572` | A tap in any server list **selects only**. Connecting is the connect control's job alone. When a tunnel is up and the user picks another server, the tunnel is left running and an explicit «Переподключиться» Snackbar action is offered. Any rewrite of the Home list or the disc keeps this split |
| **L-2** | Server-switch race: a fixed delay let the new start arrive while the old core was still up, `startContextService()` saw `coreController.isRunning` and returned silently, and the tunnel kept running the previous server while the UI showed the new one | `MainActivity.kt:1652-1690` `restartV2Ray()` | Wait for a **real** stopped state polled off `MainViewModel.isRunning` up to `RESTART_STOP_TIMEOUT_MS`, and report failure rather than pretend. The `fallbackInProgress` release point at `:1687` is deliberate: released earlier, the stop lands in the user-disconnect branch of `cancelHealthCheck()` and re-opens the switch/restart loop. Do not "simplify" the flag |
| **L-3** | Two rows painted as selected at once | `MainRecyclerAdapter.kt:223-226` (paint from the mirrored `selectedGuid`, never from MMKV), `:78-86` (re-read the selection on every rebuild so a single-row refresh cannot leave a stale row painted), `:313-339` `syncSelection()` (fall back to `notifyDataSetChanged()` whenever either row cannot be located, because the old row may sit in a collapsed section) | Selection is painted from one mirrored field, and a partial refresh is only taken when **both** rows resolve |

**Interaction with the rebuild.** `homeAdapter` is deleted with the embedded Home list (W-2), and
its two calls into these paths go with it: `MainActivity.kt:1520` `homeAdapter.removeServerSub(...)`
and `:1537` `homeAdapter.setSelectServer(selected, guid)`. `serversAdapter` keeps both, so L-3 stays
live on Серверы, which is the only surface that lists servers after the rebuild. L-1 and L-2 are
untouched: `setSelectServer()` and `restartV2Ray()` are shell functions and both survive verbatim.

**One dead assignment found in the same area, and it is not a load-bearing fix.**
`MainActivity.kt:674-675` assigns `onItemLongClick` on both adapters, but `MainRecyclerAdapter.kt:232-235`
binds only `setOnClickListener` and its comment says "Long-press server-actions menu removed:
long-press is a no-op (no listener set)". `showServerActions()`, `ServerActionsSheet`, `editServer()`,
`shareServer()`, `showQRCode()` and `removeServer()` have had no caller since. 24 s. 3.2.7 calls
this the P0 rewire and assigns it to Серверы, not to Главная. Recorded here only so the Home rebuild
does not delete `MainActivity.kt:675` and take the Серверы fix's context with it.

---

## 11. Copy

Every string below is Russian, sentence case, no final period on labels, `…` as one character,
«ёлочки», hyphen only. Terminology from 9.3: подписка, тариф, сервер, провайдер, подключение,
устройство, Купить, Привязать Telegram, Войти.

### 11.1 New keys, mirrored into `values/strings.xml` and `values-ru/strings.xml`

`values/strings.xml` becomes Russian per `01-inventory-android.md` 5.4; the English text moves to
`values-en/`.

| Key | Russian |
|---|---|
| `home_status_disconnected` | Отключено |
| `home_status_connecting` | Подключение… |
| `home_status_connected` | Подключено |
| `home_status_disconnecting` | Отключение… |
| `home_status_error` | Не удалось подключиться |
| `home_status_no_server` | Сервер не выбран |
| `home_status_no_servers` | Нет серверов |
| `home_status_no_subscription` | Подписки нет |
| `home_status_expired` | Подписка истекла |
| `home_detail_retry` | Нажмите, чтобы повторить |
| `home_detail_pick_server` | Выберите сервер в разделе «Серверы» |
| `home_strip_down_label` | Приём, Мбит/с |
| `home_strip_up_label` | Отдача, Мбит/с |
| `home_strip_ping_label` | Задержка, мс |
| `home_row_servers` | Серверы |
| `home_row_subscription` | Подписка |
| `home_sub_active` | Действует до %1$s |
| `home_sub_trial` | Пробный период до %1$s |
| `home_sub_expired` | Истекла %1$s |
| `home_sub_none` | Не оформлена |
| `home_sub_stale` | Не удалось обновить |
| `home_chip_expiring` | Истекает |
| `home_chip_expired` | Истекла |
| `home_account_title` | Аккаунт |
| `home_account_subtitle` | Вход, подписка, устройства |
| `home_account_manage` | Управление аккаунтом |
| `home_gate_signin_caption` | Войдите, чтобы получить серверы Departament. |
| `home_gate_signin` | Войти |
| `home_gate_add_provider` | Добавить провайдера |
| `home_gate_buy_caption` | Осталось выбрать тариф. |
| `home_gate_buy` | Купить подписку |
| `home_gate_sync_caption` | Подписка активна, серверы ещё не загружены. |
| `home_gate_sync` | Загрузить серверы |
| `home_condition_expired` | Подписка истекла. Продлите её, чтобы подключаться. |
| `home_condition_expiring` | Подписка заканчивается %1$s. |
| `home_condition_devices` | Достигнут лимит устройств. |
| `home_condition_offline` | Нет сети. Показаны последние данные. |
| `home_condition_silent` | Сервер не отвечает. Выберите другой сервер. |
| `home_stale_hint` | Данные могли устареть |
| `home_action_renew` | Продлить |
| `home_action_retry` | Повторить |
| `home_action_devices` | Устройства |
| `home_action_change_server` | Сменить сервер |
| `home_cd_connect` | Отключено. Нажмите, чтобы подключить |
| `home_cd_cancel` | Подключение. Нажмите, чтобы отменить |
| `home_cd_disconnect` | Подключено. Нажмите, чтобы отключить |
| `home_cd_locked` | Недоступно. Нет подписки |

Plurals `home_servers_count`, `home_providers_count`, `home_sub_days_left` exactly as
`13-start-screen.md` 13.1 prints them. Dates render «14 августа» inside the current year, «14
августа 2027» otherwise. Never a numeric date on this screen, which retires the
`SimpleDateFormat("dd.MM.yyyy")` at `MainActivity.kt:1434`.

### 11.2 Deleted keys

`home_welcome_title` («Приветствуем!»), `home_empty_title`, `home_empty_subtitle`,
`home_empty_add_qr`, `home_empty_add_clipboard`, `home_or_sign_in`, `home_not_connected`
(«Не подключено»), `home_select_server`, `speed_zero` (`0 KB/s`), `connection_connected`
(«Соединено, нажмите для проверки»), `connection_not_connected`, `memory_app_usage`, `memory_value`,
`memory_normal`, `memory_elevated`, `memory_high`, `toast_status_connecting`,
`toast_status_connected`, `toast_status_disconnected`, `toast_status_failed`.

`title_file_chooser` («Выберите профиль») stops being used as the connect disc's default label
(`activity_main.xml:305`), as its `contentDescription` and as the no-server toast
(`MainActivity.kt:1646`). `sub_auto_update_label` loses its em-dash (B-12): «Автообновление - %1$s»,
or better, the whole subtitle moves to Серверы with the meta bar.

---

## 12. Work order

Severity per 17.2. Every item names files, the change, the spec clause and the risk. Ordered by
severity, then by dependency.

| # | Sev | Title |
|---|---|---|
| W-1 | P0 | Connect control loses its focus, disabled and accessible-name states |
| W-2 | P0 | Rebuild the tab as `HomeFragment` + `fragment_home.xml`; delete the embedded server list, the carousel, the memory card and the onboarding card |
| W-3 | P1 | Delete the gradients, the glow and the breathing loop; ground becomes `?attr/colorBackground` |
| W-4 | P1 | Rebuild the connect object as one disc, one 3dp ring, one glyph, on the D-9 boundary colour |
| W-5 | P1 | Ship the 11 missing states |
| W-6 | P1 | Replace `showStatusToast()` with the inline status strip plus Snackbar |
| W-7 | P1 | Numeric strip replaces the hero-metric row; uptime leaves the screen |
| W-8 | P1 | Russian default locale for every string this screen renders |
| W-9 | P1 | Press recipe, double-press guard and the retuned 600ms confirm |
| W-10 | P1 | Delete `playColdStartAssemble()` and `shield_assemble.xml` |
| W-11 | P1 | Zero raw `dp`, `sp`, hex and font references in the new layout |
| W-12 | P2 | Header row at the 68dp text origin |
| W-13 | P2 | Delete the dead signed-out account block and the dead link-Telegram CTA path |
| W-14 | P2 | One subscription treatment, not two |
| W-15 | P2 | `sw600dp` and font-scale-200% adaptivity |
| W-16 | P3 | Retire the em-dash in `sub_auto_update_label` |

---

### W-1 - P0 - Connect control loses its focus, disabled and accessible-name states

**Files.** `res/layout/activity_main.xml:253-289` (until W-2 lands, then `fragment_home.xml`),
`ui/MainActivity.kt:310-313`, `:1645-1647`, `:1747`, `:1815`, `:1823-1824`; new
`res/drawable/bg_connect_disc.xml`, new `res/drawable/focus_ring_circle.xml`;
`res/values/strings.xml`, `values-ru/strings.xml`.

**Change.** Replace the `MaterialCardView` with a `View` carrying `bg_connect_disc.xml`: a
`<ripple android:color="?attr/colorPrimary">` wrapping an oval `<solid ?attr/colorSurfaceContainerHighest>`
with a matching oval `<mask>`. Add `android:foreground` = a `state_focused` 2dp
`?attr/colorPrimary` oval at `@dimen/focus_offset` 2, radius = 100 + 2. Set
`android:stateListAnimator="@anim/press_scale"` and delete `animateConnectPress()`. Gate the control:
when there is no server, no subscription or the subscription is expired, set
`isEnabled = false` and `alpha = 0.38f` on the whole control (R6), and route the reason to the gate
block instead of `toast(R.string.title_file_chooser)`. Set `contentDescription` from the four new
`home_cd_*` strings on every state transition. Wrap the click in the `@integer/input_debounce` 500ms
guard keyed on `@id/tag_last_click` (R9; both resources already exist).

**Spec.** 00-rules 7.1 (focus row, disabled row), R6, R7, R9, D-11; 10.7; 13 s. 5.1, s. 14; 24 s. 3.1.5.

**Risk.** Low. Does not touch L-1, L-2 or L-3. `handleFabAction()` `MainActivity.kt:1602-1626` keeps
its shape; the debounce wraps the listener, not the function, so the Snackbar reconnect path at
`:1565-1570` is unaffected. Verify by tabbing with a hardware keyboard and by connecting with no
server selected.

---

### W-2 - P0 - Rebuild the tab as `HomeFragment` + `fragment_home.xml`

**Files.** New `res/layout/fragment_home.xml`, `res/layout/row_account.xml`,
`res/layout/row_universal.xml`, `res/layout/status_strip.xml`, `res/drawable/bg_connect_disc.xml`,
`ui/home/HomeFragment.kt`, `ui/home/HomeUiState.kt`. Delete `activity_main.xml:41-452`,
`layout_home_account.xml`, `layout_home_empty.xml`, `layout_subscription_meta_bar.xml`,
`toast_status.xml`, `ui/HomeMetaPagerAdapter.kt`. From `MainActivity.kt` remove
`setupHomeMetaPager():944`, `rebuildHomeMeta():987`, `measureHomeMetaHeight():1014`,
`buildHomeMetaDots():1039`, `updateHomeMetaDots():1060`, `bindMetaBar():1368`, `metaTitle():1327`,
`metaSubtitle():1343`, `updateMemoryCard():2003`, `updateHomeEmptyState():805`,
`applyHomeListVisibility():854`, `updateOnboardingLogin():1250`, `updateLoginCtaVisibility():1208`,
`currentMetaSubId():1280`, `toggleHomePin():1286`, `refreshHomeSub():1294`,
`playColdStartAssemble():339`, `startConnectionTimer():2045`, `stopConnectionTimer():2058`,
`showStatusToast():1868`, `revealListStagger():906`, and the `homeAdapter` field `:114` with its
uses at `:666`, `:671`, `:675`, `:882`, `:1520`, `:1537`.

**Change.** Six blocks in R5 order and nothing else: identity header 56, conditional status strip 48,
connect frame 200 holding the 176 disc, status line, numeric strip 44 reserved, two summary rows,
with the single 32 gap before the rows. The fragment renders `HomeUiState`
(`13-start-screen.md` 15.4) and branches on nothing: the ViewModel resolves `condition` to one value
using the section 9 priority order. `rv_home_servers` and the carousel do not exist on this screen;
the «Серверы» row is a pointer with a count subtitle.

**Spec.** 24 s. 3.1.1-3.1.3; 13 s. 3, s. 4, s. 15; 11-app-structure 2.3 (servers are a destination).

**Risk.** Medium, and it is the whole rebuild. Three specific hazards. (a) `MainActivity.kt:1520` and
`:1537` call into the L-3 selection paths through `homeAdapter`; delete the calls, not the paths -
`serversAdapter` keeps both and L-3 must stay intact on Серверы. (b) `updateBottomNavVisibility()`
`:839-851` currently hides the nav in the signed-out empty state; once first run is a state of
Главная the nav is always visible and that function's early-return condition changes, so re-check
the "valid selected tab" fallback at `:844-850`. (c) `showLoading()` / `hideLoading()` `:1983-1995`
are `BaseActivity` overrides that currently drive the connect arc; they must stop touching the disc
before the arc becomes tunnel-only (W-4).

---

### W-3 - P1 - Delete the gradients, the glow and the breathing loop

**Files.** Delete `res/drawable/bg_home_gradient.xml`, `res/drawable-night/bg_home_gradient.xml`,
`res/drawable/bg_home_gradient_mono.xml`, `res/drawable/bg_connect_glow.xml`,
`res/drawable-night/bg_connect_glow.xml`, `res/drawable/bg_connect_glow_mono.xml`,
`res/drawable/bg_bottom_nav_scrim.xml`. Edit `activity_main.xml:8`, `:207-212`, `:512-517`;
`ui/MainActivity.kt:1697-1705` `applyThemeDecorations()`, `:1761-1762`, `:1782-1785`, `:1832-1833`,
`:1851-1853`, `:1913-1944`, `:1951-1960`.

**Change.** `home_root` background becomes `?attr/colorBackground`. Every `viewConnectGlow` reference
and the 850ms `AccelerateDecelerate` `ObjectAnimator` at `:1932-1943` go. `applyThemeDecorations()`
loses the gradient and glow swaps; with a single flat ground and a runtime-tinted ring it has nothing
left to do and is deleted. The bottom nav sits on the flat ground.

**Spec.** 1.4.3, 6.5, 6.6, 8.1, 8.3, 4.7 ("in dark mode depth comes from surface lightness, not
shadow"); 24 s. 3.1.4.

**Risk.** Low, visual only. The mono theme currently depends on `applyThemeDecorations()` to swap
three drawables; with all three deleted, verify mono still reads correctly - the ring is the only
remaining themed element and it is tinted from `?attr/colorOnSurfaceDim`, which
`ThemeOverlay.Mono` must map to `mono_onSurfaceDim` `#8A8A90` (`values-night/colors.xml:216`,
already present).

---

### W-4 - P1 - Rebuild the connect object

**Files.** `res/drawable/bg_connect_ring.xml`, `res/drawable-night/bg_connect_ring.xml`,
`res/drawable/bg_connect_ring_mono.xml` (the two variants are deleted, the base becomes stroke-only
and theme-agnostic), `res/values/attrs.xml`, `res/values/themes.xml`, `fragment_home.xml`,
`ui/home/HomeFragment.kt`.

**Change.** `bg_connect_ring.xml` becomes a single `<shape android:shape="oval">` with
`<stroke android:width="@dimen/stroke_ring" android:color="#FFFFFF"/>` and no `<solid>`, tinted at
runtime via `backgroundTint` with an `ArgbEvaluator` over `@integer/motion_state` 220. The six raw
hex values disappear. Add the theme attribute `colorOnSurfaceDim` mapped to the existing
`md_theme_onSurfaceDim` / `mono_onSurfaceDim` colours and use it for the idle ring. Ring tints:
idle `?attr/colorOnSurfaceDim`, connecting the same, connected `?attr/colorPrimary`, error
`?attr/colorError`, disabled `?attr/colorOutline`. Width never changes (13 s. 19.1 S-2). Resize
`progress_connect` from `indicatorSize` 212dp to `@dimen/connect_disc` 176dp with
`trackCornerRadius` 2dp so the arc travels the ring. Disc fill moves from
`colorSurfaceContainerHigh` to `colorSurfaceContainerHighest`. Split `refreshConnectArc()` so
`showLoading()` / `hideLoading()` no longer spin it.

**Spec.** 6.8, 14.1, D-9; 13 s. 5.2, s. 5.3, s. 19.1 S-2 and S-10; 24 s. 3.1.5.

**Risk.** Low-medium. The `connectArcSubLoads` counter at `MainActivity.kt:1985`, `:1992` is the only
other consumer of the arc; give subscription loads their own inline 20dp spinner on Серверы (R8)
rather than deleting the counter blind. Measure the new ring against both edges in all three themes
before closing: idle must clear 3:1 on the ground **and** on the disc fill.

---

### W-5 - P1 - Ship the 11 missing states

**Files.** `ui/home/HomeUiState.kt`, `ui/home/HomeFragment.kt`, `viewmodel/MainViewModel.kt`,
`res/layout/status_strip.xml`, `res/values*/strings*.xml`.

**Change.** Implement section 6 of this document in full: the six launch variants of 13 s. 11.1, the
seven connection states of 11.2, and loading / partial / data error / long / short / 200% / 320dp of
11.3. The ViewModel resolves exactly one `Condition` from the six-row priority table in 13 s. 9. The
gate block and the ledger rows occupy one slot and are mutually exclusive per the rule table in
13 s. 8.3. Skeletons are static, never pulsing, and appear only after 300ms.

**Spec.** 00-rules 15, 1.4.13, 9.4, 9.5, 9.6; 13 s. 8, s. 9, s. 11; 24 s. 3.1.9-3.1.11.

**Risk.** Medium. `подписка истекает`, `подписка истекла`, `триал` and `лимит устройств` have no
Android data source today - `bindMetaBar` reads only the provider's `userinfo` header, not the
account's subscription. The account-side `SubInfoDto` used by `AccountFragment` carries the fields
(`SubscriptionPagerAdapter.kt:72-90`). One `SubscriptionState` must serve Главная, Аккаунт and the
ongoing notification (`32-master-plan-android.md` 1.2, signature moment 3). Do not infer trial from
squad or tariff name; take the backend's flag.

---

### W-6 - P1 - Status strip plus Snackbar replaces the toast

**Files.** New `res/layout/status_strip.xml`; delete `res/layout/toast_status.xml` and
`res/drawable/bg_toast_status.xml`; `ui/MainActivity.kt:1868-1879` and all nine call sites (`:202`,
`:576`, `:612`, `:616`, `:617`, `:1275`, `:1306`, `:1621`, `:1679`).

**Change.** Persistent conditions become the inline strip: 48dp minimum,
`?attr/colorSurfaceContainerHigh`, `@dimen/radius_control` 16, 20dp glyph, `Body` text at 2 lines
max, a 48x48 accent text action. Transient events become a `Snackbar` anchored to the bottom nav,
with an action where one exists. The four toast strings die with `showStatusToast()`.

**Spec.** 1.4.8; 22 s. 14; 13 s. 9 and s. 19.1 S-5 (48, not 40); 9.4 (every error ships a recovery
affordance).

**Risk.** Low. The `Toast` at `MainActivity.kt:1504` (`toast_action_not_allowed`, removing a selected
server) and `:1646` are separate paths; `:1646` dies with W-1 and `:1504` belongs to Серверы.

---

### W-7 - P1 - Numeric strip replaces the hero-metric row

**Files.** Delete `activity_main.xml:58-172`; new numeric strip in `fragment_home.xml`;
`res/values/dimens.xml` (add `strip_value_speed` 48sp, `strip_value_latency` 32sp, `connect_frame`
200dp); `res/values/styles.xml` (add `TextAppearance.App.Numeric.Value`, fix
`TextAppearance.App.Numeric` which declares no weight at `:122-127`, D-4);
`ui/MainActivity.kt:2045-2063` (the uptime timer moves to the notification builder).

**Change.** Three equal columns: приём / отдача / задержка, value in the Numeric role at 16sp/500
with `tnum lnum zero` on, label in `Caption` beneath. `minWidth` in **sp** so the reserve scales with
the text; these are the product's only two sp dimensions and the token comment must say why (S-11).
The block is `INVISIBLE`, never `GONE`, and is visible only while **connected** - never during
negotiation, where a printed `0,0` would be a placeholder pretending to be a reading. No `↑`/`↓`, no
dividers, no `42dp` invisible spacer.

**Spec.** 1.1 (hero-metric ban), 5.5, D-3, D-4; 13 s. 7, s. 19.1 S-3 and S-11; 24 s. 3.1.6.

**Risk.** Low-medium. Latency has no producer while connected today; the existing delay test is the
source, on a 30s interval (13 s. 19.4 q2). Before the first probe lands the value is an **empty
string** in a reserved box that fades in at 220ms - never a dash, never a zero, never a spinner.

---

### W-8 - P1 - Russian default locale

**Files.** `res/values/strings.xml` (`:56-59`, `:137`, `:334`, `:350`, `:352`, `:354-359`, `:445-446`,
`:614-616`), new `res/values-en/strings.xml`, `res/values-ru/strings.xml`.

**Change.** `values/strings.xml` becomes Russian; the current English moves to `values-en/`. Add the
`home_*` keys of section 11.1 and delete the keys of 11.2. Fold the three Russian plural sets in.

**Spec.** 1.4.10; 9.2, 9.3; `01-inventory-android.md` 5.4.

**Risk.** Low mechanically, wide in blast radius: `values/strings.xml` is the fallback for every
locale, so a key that exists only in `values-ru/` today will start resolving differently. Diff the
key sets of `values/` and `values-ru/` before and after.

---

### W-9 - P1 - Press recipe, double-press guard, retuned confirm

**Files.** `res/anim/connect_confirm.xml`, `ui/MainActivity.kt:1889-1905`, `:310-313`,
`fragment_home.xml`.

**Change.** `connect_confirm.xml` scale `1.0 -> 1.35` and alpha `0.6 -> 0`; duration and curve are
already correct. Every pressable object on this screen carries
`android:stateListAnimator="@anim/press_scale"` (0.97, 90 / 160) and the imperative 0.94 animator is
deleted. Rows do **not** scale (R5); the two ledger rows and the header row step their background to
`?attr/colorSurfaceContainerHigh` on press. The debounce from W-1 covers the disc; the gate buttons
and the strip action take the same guard.

**Spec.** D-11, R4, R5, R9; 8.4 (one hero moment, and this is it, retuned not duplicated); 13 s. 12.2,
s. 12.3.

**Risk.** Low. Confirm on a real device that the retuned 1.35 ring still paints outside the 200dp
frame - `clipChildren="false"` is required on the connect frame, the content column and the scroll
view (13 s. 5.4).

---

### W-10 - P1 - Delete the cold-start choreography

**Files.** `ui/MainActivity.kt:327`, `:333-346`, the `heroAssembled` flag; delete
`res/anim/shield_assemble.xml`.

**Change.** The screen appears; it does not perform. The only entrance motion permitted on Главная is
the shell's 220ms fade-through when arriving from another destination.

**Spec.** 8.9; 13 s. 12.1; 24 s. 3.1.4.

**Risk.** None.

---

### W-11 - P1 - Zero raw values in the new layout

**Files.** `fragment_home.xml`, `row_account.xml`, `row_universal.xml`, `status_strip.xml`,
`res/values/dimens.xml`, `res/values/styles.xml`.

**Change.** The new files ship with zero raw `dp`, zero raw `sp`, zero raw hex, zero
`android:fontFamily` and zero `android:textSize`. Every text element carries only
`android:textAppearance`. Any value the token set does not cover is added to `dimens.xml` **first**,
with a comment stating what it is for. The three `android:textStyle="bold"` uses die with their
layouts (5.4: no synthetic bold).

**Acceptance.** Re-run every command in section 4 with `F` pointed at the new files. Required
results: raw hex 0, `textAllCaps` 0, `fontFamily|textSize` 0, off-scale `dp` 0, **raw `dp` of any
value 0**, raw `sp` 0, `textStyle="bold"` 0, typographic chrome 0.

**Spec.** 00-rules 3 (rule), 1.4.5, 1.4.6, 5.2, D-2; 16 pre-flight.

**Risk.** None, but it is the gate: an item that cannot tick every box in the acceptance line is not
done.

---

### W-12 - P2 - Header row at the 68dp text origin

**Files.** New `res/layout/row_account.xml`; delete `layout_home_account.xml`;
`ui/MainActivity.kt:1191-1202` `bindAccountChip()` moves to the fragment.

**Change.** `[16][40 slot holding a 36 avatar][12][text column][12][20 chevron][16]`, height
`@dimen/row_min_height` 56, whole row is the target. The avatar is neutral, never accent - today
`tv_avatar_initial` is drawn in `?attr/iconTintBlue` at `layout_home_account.xml:106`. The chevron
goes from `18dp` (`:148-149`, off-scale) to `@dimen/glyph_20` (13 s. 19.1 S-7). Signed out, the row
is a navigation row reading «Аккаунт» / «Вход, подписка, устройства» and is not styled as a button:
the verb lives on the gate primary, once.

**Spec.** 4.5; 13 s. 10, s. 19.1 S-7 and S-8; 24 H2.

**Risk.** Low. `AvatarManager.setMonogram` / `applyAvatar` (`MainActivity.kt:1198-1199`) move
unchanged.

---

### W-13 - P2 - Delete the two dead account paths

**Files.** `layout_home_account.xml:22-75` (`group_login` and `cta_link_telegram`),
`ui/MainActivity.kt:1176-1178`, `:1204-1216` `updateLoginCtaVisibility()`, `:1208-1215`; the
`ctaDismissed` flag; `res/values/strings_nav.xml:14-15`.

**Change.** `group_login` is set `isVisible = false` unconditionally at `MainActivity.kt:1178`, and
`cta_link_telegram` is its only child, so `updateLoginCtaVisibility()` sets visibility on a view
inside a permanently-`GONE` parent: the link-Telegram CTA has been unreachable. Delete both, with the
`✕` text-glyph close button at `:71` (B-5). Linking Telegram belongs to Аккаунт (9.3: «Привязать
Telegram»), not to Главная.

**Spec.** 1.4.4; 13 s. 10; 24 s. 3.1.3.

**Risk.** Low. Confirm no other surface reaches `SubscriptionOrigin.hasDepartamentSubscription()`
only through this path before deleting the helper.

---

### W-14 - P2 - One subscription treatment, not two

**Files.** `res/layout/item_subscription_card.xml`, `ui/SubscriptionPagerAdapter.kt`,
`ui/AccountFragment.kt:137`.

**Change.** The product renders a subscription two ways: `layout_subscription_meta_bar.xml` on
Главная (title + traffic pill + announce + support row) and `item_subscription_card.xml` on Аккаунт
(name + tariff badge + expiry + devices). Once the meta bar leaves Главная, `item_subscription_card`
is the single treatment; the provider group header on Серверы is a **different object** (a provider,
not a subscription) and says so. `SubscriptionPagerAdapter.submit():30-34` also calls
`notifyDataSetChanged()` and needs `DiffUtil` and stable IDs.

**Spec.** 1.3 ("inconsistent component vocabulary across screens - if the save button looks different
in two places, one is wrong"), R15; 11.5.

**Risk.** Low, and it is an Аккаунт work item recorded here because Главная is where the duplication
was visible.

---

### W-15 - P2 - Adaptivity

**Files.** `fragment_home.xml`, new `res/values-sw600dp/dimens.xml`.

**Change.** `screen_gutter` 16 -> 24 at `sw600dp`, content capped at `@dimen/content_max_width` 720
and centred. No fixed control heights anywhere: every button is `wrap_content` + `minHeight` with
`insetTop`/`insetBottom` 0 (R2), which retires `layout_home_empty.xml:111`'s `52dp`. Delete the two
weighted spacers (`activity_main.xml:374-408`) that make the first-run layout depend on viewport
height. Verify at 320dp width and font scale 200%: the disc stays 176, everything else grows, the
screen scrolls, the numeric strip's widest column is 5 characters at 48sp = 96dp inside a 109dp
column.

**Spec.** 4.1, 11.4, 14.5, R2; 13 s. 3, s. 11.3.

**Risk.** Low.

---

### W-16 - P3 - Retire the em-dash

**Files.** `res/values/strings.xml:346`, `res/values-ru/strings.xml:353`.

**Change.** «Автообновление — %1$s» becomes «Автообновление - %1$s». The string moves to Серверы with
the meta bar; the dash is fixed either way so the 9.7 grep does not carry a Home-owned hit forward.

**Spec.** 1.4.11, 9.2, 9.7.

**Risk.** None.

---

## 13. Acceptance

Run every box. A box that cannot be ticked honestly means Главная is not done.

**Bans and tokens**
- [ ] The six greps of section 4, re-run against `fragment_home.xml`, `row_account.xml`,
      `row_universal.xml`, `status_strip.xml`: raw hex 0, `textAllCaps` 0, `fontFamily|textSize` 0,
      raw `dp` 0, raw `sp` 0, `textStyle="bold"` 0
- [ ] `grep -rn '<gradient' res/drawable*/` returns nothing this screen references
- [ ] No glow, no halo, no second ring, no breathing loop, no ambient sonar
- [ ] Zero cards on this screen. The disc is a `View` with a drawable
- [ ] No `↑`, no `↓`, no `✕`, no `∞`, no emoji
- [ ] `grep -rn -e '—' -e '–' values*/strings*.xml` returns nothing for any `home_*` key

**Direction**
- [ ] Count the blue: **zero** accent pixels when disconnected and ungated; exactly one accent object
      when connected; exactly one when gated
- [ ] Count the planes: ground, plus the P3 disc, plus the P2 strip when a condition applies. Never more
- [ ] Text origin measured at 68dp on the header row, both ledger rows and both hairlines
- [ ] Gaps 16, 24 and 32 all present; 32 used exactly once
- [ ] A strip value moved from `9,9` to `10,1` moves nothing
- [ ] No Russian string set in Space Grotesk

**States**
- [ ] Six launch variants screenshotted (13 s. 11.1)
- [ ] Seven connection states screenshotted (13 s. 11.2)
- [ ] Loading, partial, data error, long content (60-char remark), short content, 200% font scale,
      320dp width
- [ ] All eleven product gate states of section 6.2
- [ ] Every one of the above in dark, light and mono

**Interaction and motion**
- [ ] Press feedback inside 90ms on the disc, both rows, the header row, both gate buttons and the
      strip action
- [ ] Focus ring visible on every focusable control, driven from a hardware keyboard
- [ ] Disc disabled at 0.38 on the **whole** control when gated, and it does not take the tap
- [ ] TalkBack reads state **and** action on the disc; the status line announces as a polite live
      region; the numeric strip does not
- [ ] The sweep runs only while the core is negotiating and stops on the frame the state resolves
- [ ] Exactly one confirm ring per connect, none on disconnect
- [ ] Animation scale 0: instant fill, no ring, haptic still fires
- [ ] Tapping the disc while connecting cancels the attempt
- [ ] Double-tapping the disc dispatches once

**Regression, the three load-bearing fixes**
- [ ] L-1: tapping a server row on Серверы selects and never connects; with a tunnel up it offers
      «Переподключиться» and declining leaves the tunnel exactly as it was
- [ ] L-2: switching servers while connected lands on the **new** server, and a stop that does not
      land reports failure instead of a false success
- [ ] L-3: select a server inside a collapsed provider group, then select another: exactly one row
      is painted selected

---

## 14. Spec corrections this audit produces

| Doc | Clause | Correction |
|---|---|---|
| `13-start-screen.md` | 5.5 | The shipped filled shield is **blue**, not green. `connectedColor` maps to `color_fab_active` = `#4C8DFF` dark / `#1E5FC7` light (`values/themes.xml:75`, `values-night/colors.xml:23`). The green `color_connected` `#22C55E` is referenced only by `updateMemoryCard()` (`MainActivity.kt:2009`) and dies with the memory card. The rule stands; the observation is stale |
| `13-start-screen.md` | 2.1 | The Home block is `activity_main.xml:41-452`, not `:40-517`. `:455` onward is the Servers tab and `:512-705` is the shell nav |
| `13-start-screen.md` | 5.2, 19.1 S-10 | `md_theme_onSurfaceDim` already exists in `values/colors.xml:222` and `values-night/colors.xml:121`; what is missing is the `colorOnSurfaceDim` **theme attribute** in `values/attrs.xml` and its mapping in `values/themes.xml`. S-10 is a wiring task, not a colour task |
| `13-start-screen.md` | 15.2 | `res/anim/press_scale.xml` is **already** 0.97 at 90 / 160 with the right interpolators, and so is `nav_press.xml`. The surviving 0.94 is the imperative animator at `MainActivity.kt:1894`, which the entry does not name |
| `00-rules.md` | 18, pending rows | S-10, S-3b, S-9 and S-11 are still `pending` in `13-start-screen.md` 19.3 and are not in section 18. W-4, W-7 and W-5 depend on all four. They need owner sign-off before the rebuild starts, not after |
| `24-tab-conformance.md` | 3.1.5 | Specifies the idle ring as `color_outline`; 13 s. 19.1 S-10 overrides that to `color_on_surface_dim` on contrast grounds. 13 wins as the screen's implementation contract; 24 should be amended so an implementer reading only 24 does not ship a 1.45:1 ring |
