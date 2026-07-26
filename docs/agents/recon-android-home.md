# Recon — Android home / start experience (map + critique)

Scope: the first screen a user sees at launch — `MainActivity` and its `nav_home` tab, plus the
shell it lives in (toolbar, bottom nav, tab swapping, empty states). Everything below was read
directly from the files cited; every claim carries a `file:line`.

Branch: `claude/app-audit-agents-hyyftk` (HEAD `7e2baf4`).

## Files actually read

| Path | Lines |
|---|---|
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt` | 2840 |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/HomeMetaPagerAdapter.kt` | 68 |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/SubscriptionPagerAdapter.kt` | 104 |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/BaseFragment.kt` | 56 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/activity_main.xml` | 705 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_home_account.xml` | 155 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_home_empty.xml` | 139 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_servers_empty.xml` | 67 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/item_subscription_card.xml` | 75 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_subscription_meta_bar.xml` | 257 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_servers_header.xml` | 108 |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/item_recycler_main.xml` | 130 |
| `res/values/{dimens,styles,themes,motion,strings,strings_nav,strings_home_shell}.xml`, `res/values-night/colors.xml`, `res/values/colors.xml`, `res/values-ru/strings.xml` | — |
| `res/drawable{,-night}/bg_home_gradient.xml`, `bg_connect_ring.xml`, `bg_connect_glow.xml`, `bg_card_incy.xml`, `bg_server_card.xml`, `bg_server_row.xml`, `bg_nav_dot.xml`, `bg_bottom_nav_scrim.xml`, `bg_avatar_circle.xml`, `bg_acc_badge.xml`, `bg_traffic_gradient.xml`, `bg_flag_tile.xml`, `bg_type_chip.xml`, `ic_shield_{outline,filled}.xml`, `ic_nav_*.xml` | — |
| `res/anim/{nav_press,press_scale,shield_assemble,connect_confirm}.xml` | — |
| `docs/design-home-polish.md` (intent doc, for intent-vs-shipped diff) | 405 |

---

## 1. Composition map

`activity_main.xml` is a single flat `LinearLayout` (`home_root`) that carries **the whole app shell**
— there is no `Fragment` per tab except Account. Tabs are four sibling views toggled by visibility.

```
LinearLayout  home_root                      activity_main.xml:2   bg = @drawable/bg_home_gradient (:8)
├─ AppBarLayout appbar_layout                :13   elevation 0, transparent
│   └─ MaterialToolbar toolbar               :20   style ToolbarBrandTitle — PERMANENTLY HIDDEN, see §6.1
└─ FrameLayout (weight 1)                    :30
    ├─ FrameLayout main_content              :35
    │   ├─ NestedScrollView group_home       :41   fillViewport, transparent  ← HOME TAB
    │   │   └─ LinearLayout (vertical)       :49
    │   │       ├─ LinearLayout home_stats_row        :58    ↑speed · timer · ↓speed  +  "+" ImageButton
    │   │       │   ├─ View (42dp invisible spacer)   :73
    │   │       │   ├─ LinearLayout (weight 1)        :80
    │   │       │   │   ├─ ↑ + tv_upload_speed        :95/:102   Caption arrow + Numeric 13sp/700
    │   │       │   │   ├─ tv_connection_time         :116       Numeric 14sp/700, onSurfaceVariant
    │   │       │   │   └─ ↓ + tv_download_speed      :137/:144
    │   │       │   └─ ImageButton btn_home_add       :161   42×42, borderless, ic_add_24dp
    │   │       ├─ include layout_home_account        :175   margins gutter/8/gutter
    │   │       ├─ LinearLayout card_hero             :187   pad 24 top/bottom, gutter sides
    │   │       │   ├─ FrameLayout hero_frame 230dp   :200   clipChildren=false
    │   │       │   │   ├─ View view_connect_glow     :207   bg_connect_glow, invisible at rest
    │   │       │   │   ├─ View view_connect_ring     :214   bg_connect_ring (2 concentric strokes)
    │   │       │   │   ├─ View view_connect_pulse    :224   sonar, reuses ring drawable, invisible
    │   │       │   │   ├─ CircularProgressIndicator progress_connect :237  212dp, 3dp track, gone
    │   │       │   │   └─ MaterialCardView card_connect 176dp :253  radius 88, surfaceContainerHigh,
    │   │       │   │       ├─ ImageView img_connect        :268   80dp ic_shield_outline
    │   │       │   │       └─ ImageView img_connect_filled :279   80dp ic_shield_filled, alpha 0
    │   │       │   └─ TextView tv_connection_status  :295   App.Title, marginTop 16
    │   │       ├─ MaterialCardView card_memory       :312   GONE unless PREF_SHOW_MEMORY
    │   │       ├─ View home_empty_spacer_top         :374   weight 1, gone
    │   │       ├─ TextView tv_home_welcome           :383   App.Headline, gone
    │   │       ├─ include layout_home_empty          :396   ← NO margins on the include (§6.2)
    │   │       ├─ View home_empty_spacer_bottom      :402   weight 1, gone
    │   │       ├─ LinearLayout group_home_meta       :413
    │   │       │   ├─ ViewPager2 vp_home_meta        :420   pages = layout_subscription_meta_bar
    │   │       │   └─ LinearLayout ll_home_meta_dots :428   built in code, gone when ≤1 page
    │   │       └─ RecyclerView rv_home_servers       :441   flat, nestedScrolling off
    │   ├─ LinearLayout group_servers        :455   gone   ← SERVERS TAB
    │   ├─ include group_settings            :492          ← SETTINGS TAB
    │   └─ FrameLayout group_account         :502   gone   ← ACCOUNT TAB (AccountFragment, lazy :439)
    ├─ View bottom_nav_scrim                 :512   160dp tall, bg_bottom_nav_scrim
    └─ LinearLayout bottom_nav               :525   4× weighted item: icon 24 + label 11sp + 34×3 pill
```

Key wiring:

- Edge-to-edge: `setupEdgeToEdge()` (`MainActivity.kt:495-523`) pads `appbar_layout`, and **every tab
  root individually** with the status inset (`:503-506`), pads `bottom_nav` with the full bottom inset
  (`:513`), and pads both RecyclerViews with `inset + 56dp + 16dp` (`:516-520`).
- Tab swap: `showTab()` (`:437-479`) hides the AppBar on **all** tabs (`:448`), then cross-fades
  outgoing 150 ms → incoming 200 ms + 8dp rise, with a tick haptic (`:466-478`).
- Nav paint: `updateNavSelection()` (`:350-380`) tweens icon+label grey↔blue over 200 ms (`:406-422`),
  steps label weight 500→700 (`:393-403`), and toggles a 34×3 blue pill (`:378`).
- Connect: `card_connect` click → `animateConnectPress()` + `handleFabAction()` (`:272-275`).
- Home meta carousel: `HomeMetaPagerAdapter` (`HomeMetaPagerAdapter.kt:17-67`) inflates
  `layout_subscription_meta_bar` per subscription; `MainActivity.bindMetaBar()` (`:1249-1321`) paints it;
  page height is fixed by measuring every page and taking the max (`measureHomeMetaHeight()` `:895-917`).

---

## 2. What is rendered, state by state

### 2.1 Signed out, zero servers — "pure onboarding" (the true first launch)

Driven by `updateHomeEmptyState()` (`MainActivity.kt:686-710`) + `updateOnboardingLogin()` (`:1131-1146`)
+ `updateAccountGate()` (`:1048-1064`) + `updateBottomNavVisibility()` (`:720-732`).

- Canvas: `bg_home_gradient`. Night: radial `#1B2D50 → #0E141F → #0A0B0D`, centre (0.5, 0.30), r 560dp
  (`drawable-night/bg_home_gradient.xml`). Day: `#FFFFFF → #EEF3FB → #DFE6F1` (`drawable/bg_home_gradient.xml`).
- **Hidden:** toolbar (`:448`), `home_stats_row` incl. the "+" (`:697`), account header (`:1056`),
  `card_hero` (`:691`), meta carousel (`:701`), server list (`:702`), **and the entire bottom nav +
  scrim** (`:723-724`).
- **Shown:** `tv_home_welcome` "Приветствуем!" (App.Headline 24sp/700 Space Grotesk, centred,
  `activity_main.xml:383-393`), vertically centred by two weight-1 spacers (`:374`, `:402`; toggled `:695-696`).
- Below it, `layout_home_empty` — one `MaterialCardView`, `colorSurface` `#141619`, radius 20, 1dp
  `colorOutlineVariant` `#20242B`, 24dp inner vertical padding, everything `center_horizontal`:
  1. `home_empty_title` App.Title 16sp/700 — default locale "У вас пока не добавлены подписки." ; ru locale "Пока нет подписок"
  2. `home_empty_subtitle` App.Subtitle 13sp muted "Добавьте подписку, чтобы начать пользоваться"
  3. `btn_home_add_qr` filled M3 button "Добавить по QR-коду", `ic_scan_24dp` (marginTop 24)
  4. `btn_home_add_clipboard` outlined "Добавить из буфера обмена", `ic_copy` (marginTop 8)
  5. `group_home_login` (visible only when `BackendConfig.isConfigured()` and signed out, `:1139`):
     caption "Или войдите в свой аккаунт" (marginTop 16) → **52dp** tonal pill "Войти через Telegram"
     (`colorPrimaryContainer` `#17325C` on `#CFE0FF`, bold, cornerRadius 26, `ic_telegram_24dp` untinted)
     → outlined "Войти через сайт" with `ic_globe_24dp`.
- Net: **two headings, one caption and four stacked full-width buttons inside a single card**, all
  centre-aligned, on an otherwise empty glowing canvas, with no navigation.

### 2.2 Signed in, zero subscriptions

Same skeleton, with:
- `layout_home_account` root visible, `chip_account` visible (`:1056`, `:1060`): 36dp `bg_avatar_circle`
  (`iconTileBgBlue`) holding either `tv_avatar_initial` (monogram, `iconTintBlue`, 16sp bold) or
  `img_avatar` (`AvatarManager.applyAvatar`, `:1080`); primary line = Telegram name → `@handle` →
  e-mail → "Аккаунт" (`:1074-1078`); secondary line = the identity, or the literal hint
  "Управление аккаунтом" (`:1081-1082`); 18dp `ic_chevron_right`. Background `bg_card_incy`
  (surface + 20dp + 1dp outline), `press_scale` state-list animator.
- Onboarding card mutates (`:1141-1145`): QR and clipboard buttons **hidden**, login block hidden,
  `btn_home_buy` "Купить подписку" shown → `BuyTariffActivity` (`:675-677`), and `btn_home_link_tg`
  "Привязать Telegram" shown only while `profile.telegramLinked == false`.
- Bottom nav returns with **four** items (`:721`, `:1061`).

### 2.3 Has servers (the everyday state)

- `home_stats_row` visible: an invisible 42dp spacer, then a 3-column weighted block
  (↑/uptime/↓), then the 42dp "+" — the spacer exists purely so `tv_connection_time` lands at the
  true screen centre (`activity_main.xml:70-77`). At rest it reads `↑ 0 KB/s   00:00:00   ↓ 0 KB/s`.
- `card_hero`: 230dp frame. `bg_connect_ring` draws **two** concentric ovals — outer 1.5dp
  `#334C8DFF`, inner (14dp inset) 2dp `#804C8DFF`. Inside, a 176dp disc `colorSurfaceContainerHigh`
  `#1A1D21` at radius 88 with `rippleColor` and `cardForegroundColor` both cleared
  (`activity_main.xml:264-265`). Centre: 80dp `ic_shield_outline` tinted `colorOnSurfaceVariant`.
  When no server is selected the shield rests at **alpha 0.38** (`MainActivity.kt:1690-1691`).
- Label under the shield: `idleStatusText()` (`:1895-1899`) — "Не подключено" if a server is selected,
  "Выберите сервер" otherwise. The **XML default** is `@string/title_file_chooser` = "Выберите профиль"
  (ru) / "Select a config" (en) (`activity_main.xml:305`), which is what paints on the very first frame.
- Meta carousel: one `layout_subscription_meta_bar` per provider group (`rebuildHomeMeta()` `:868-888`),
  page dots only when >1 (`:886`). Each page carries: 48dp collapse chevron, title (`metaTitle()` `:1208-1216`),
  subtitle "09.07.2026 07:08 · Автообновление — 1 ч." (`metaSubtitle()` `:1224-1241`), three 36dp icon
  buttons (ping / refresh / pin), a 160dp traffic pill centred with the expiry marker pinned right,
  an optional announce paragraph, a tonal "Поддержка" button bottom-left and a 36dp Telegram icon
  bottom-right. Long-press deletes the subscription (`HomeMetaPagerAdapter.kt:64` → `:958-969`).
- Server rows (`item_recycler_main.xml`): 28dp flag tile, name App.Title, protocol chip, statistics,
  ping value. Selected row = `bg_server_row` selected state — 1.5dp `colorPrimary` outline + `#1F4C8DFF`
  fill. On Home the section headers are suppressed (`showHeaders = false`, `MainActivity.kt:763`).

### 2.4 Connecting

`handleFabAction()` (`:1483-1501`) → `applyRunningState(isLoading = true, …)` (`:1581-1592`):
- gray status toast «Подключение…» positioned 110dp above the bottom edge (`:1735-1746`);
- `progress_connect` shown at 212dp / 3dp, colour pinned to `connectActiveColor` (`:1838-1839`);
- `view_connect_glow` **breathes** — alpha 0.3↔0.6, scale 0.96↔1.04, 850 ms, `INFINITE`/`REVERSE`,
  `AccelerateDecelerateInterpolator` (`:1799-1810`);
- shield stays outline at full alpha, tinted `connectActiveColor`; label = "Подключение…" in the same blue;
- 20s watchdog (`:200`, `:1950-1953`) → on timeout, forced idle + «Не удалось подключиться» (`:179-186`).

Visually there are now **four concentric strokes**: outer ring 230dp, arc 212dp, inner ring 202dp, disc 176dp.

### 2.5 Connected

`isRunning` observer (`:550-579`) → `applyConnectedState()` (`:1610-1670`):
- `CONFIRM` haptic; outline→filled crossfade over `motion_state` (220 ms) with the tint warming
  grey→`connectedColor`; glow reveals over `motion_reveal` (300 ms); one sonar ring 1.0→1.6 + fade
  over `motion_emphasis` (600 ms) (`anim/connect_confirm.xml`);
- label switches to `selectedServerName()` — raw server remarks, e.g. `🇳🇱 Netherlands • Amsterdam` (`:1885-1889`);
- uptime timer starts, start time persisted in MMKV so it survives recreate (`:1912-1923`);
- live speeds via `updateSpeedAction` (`:527-530`); stats pipeline force-enabled in `onCreate` (`:244`);
- toast «Прокси подключён»; 7 s later a one-shot real-ping health check may trigger auto-fallback
  (`:198`, `:1936-1941`, `:580-591`).

### 2.6 Disconnected / idle

`applyIdleState()` (`:1677-1721`): reverse of the above at 75% tempo, label back to "Не подключено",
speeds reset to `speed_zero`, timer zeroed and cleared from MMKV (`:1925-1930`), toast «Отключено».

### 2.7 Error

There is **no error state on the screen**. Every failure path is a 2-second Toast:
`toast_status_failed` from the watchdog (`:185`), from a failed fast-connect (`:536`), from a failed
restart (`:1551`), or from the `isRunning=false` + `connectInProgress` branch (`:573`). The shield
returns to plain idle grey — indistinguishable from "user never pressed anything".

### 2.8 Loading (subscription refresh)

`showLoading()/hideLoading()` are overridden to ref-count onto **the same connect arc** (`:1850-1862`).
Refreshing a subscription therefore makes the connect knob spin exactly as if it were connecting.

---

## 3. Design critique

The owner's read ("looks bad") is correct, and it is not one thing. Below, ordered by how much each
costs the screen.

### 3.1 There is no hierarchy — the screen is five equal-weight bands

Home is a vertical stack of five independent blocks, each with its own visual language, none
subordinate to another:

1. a metrics strip (`home_stats_row`),
2. an identity card (`chip_account`),
3. a 230dp hero,
4. a dense control panel (`layout_subscription_meta_bar`),
5. a list.

Nothing tells you which one matters. The hero is the biggest, but it is bracketed above by a live
numeric readout and below by a card containing seven interactive targets, so the eye never rests on it.
A VPN home has exactly one job — *is it on, and can I turn it on* — and exactly one secondary job —
*which exit am I using*. Everything else on this screen is instrumentation.

**Arithmetic on the fold.** Summing the declared boxes for the signed-in / has-servers state:
`home_stats_row` 4+42+4 = 50; account chip 8 + (8+36+8) = 60; `card_hero` 4+24+230+16+~20+24 = 318;
meta carousel 8 + (12+48+8+16+8+~36+12) = 148; list marginTop 4. Total **≈ 580dp** before the first
server row. On a 1080×2400 phone the content area is ~748dp after insets, and the nav overlay eats
another ~72dp of it, leaving ~96dp of visible list — **one and a half server rows**. The primary
navigational content of the screen is functionally below the fold.

### 3.2 Spacing is not on a scale, and the one scale that exists is not used here

`res/values/dimens.xml` defines the Incy scale (`space_4/8/12/16/24/32`, `radius_chip/card/tile`,
`tile_size 40`, `tile_glyph 22`, `row_min_height 56`, `screen_gutter 16`). Home ignores most of it:

- Off-scale dimensions on Home: `42dp` (`btn_home_add`, `activity_main.xml:163`), `36dp` (all five
  meta-bar icon buttons), `18dp` (chip chevron, `layout_home_account.xml:148`), `28dp` (flag tile),
  `34×3dp` (nav pill), `52dp` (Telegram login button, `layout_home_empty.xml:111`), `160dp`
  (traffic pill), `176/212/230dp` (hero), `13dp` padding (meta chevron, `layout_subscription_meta_bar.xml:39`),
  `3dp` (nav label/pill gaps, `activity_main.xml:560/571`).
- `layout_servers_empty.xml` is off-scale from top to bottom: root padding 24, card padding 20/28/20/24,
  `marginTop 64`, `14dp`, `20dp`, `10dp`, icon 56dp, and a hardcoded `cardCornerRadius="20dp"` instead
  of `@dimen/radius_card` (`:14-17`, `:25-28`, `:40`, `:48`, `:58`).
- Same hardcoded radius in `layout_home_empty.xml:13` and `activity_main.xml:322` (memory card).
- Declared-but-unused tokens: `dot_gap` (8dp) — the code uses `space_4` instead (`MainActivity.kt:926`);
  `sub_card_height` (152dp); `radius_pill`. `tile_size`/`tile_glyph` are used everywhere in Settings
  and nowhere on Home.

The result is that no two gaps on the screen agree, which is exactly what "looks unpolished" means
before anyone can articulate why.

### 3.3 The connect affordance is over-drawn and under-informative

- **Four concentric strokes.** Outer ring 1.5dp, arc 3dp, inner ring 2dp, disc edge — three of them
  visible simultaneously while connecting. Incy's whole point is one glowing ring on a black canvas.
  `bg_connect_ring.xml` shipping two rings is the single biggest departure from `docs/design-home-polish.md`.
- **The hero glyph is a 24dp icon at 3.3×.** `ic_shield_outline.xml` is a 24×24 viewport rendered at
  80dp. The "outline" is an `evenOdd` fill between two shield silhouettes ~2.4 units apart — at 80dp
  that is an **8dp-thick** stroke. The shoulders are straight `L` segments with hard corners and no
  radius; only the bottom has curves. At 80dp on a dark field it reads blobby and hand-drawn, not
  crafted. This is the one element the entire screen is built around.
- **State is carried only by colour.** `applyConnectedState` sets the label to the *server name*
  (`:1615`, `:1885-1889`) and the idle label to "Не подключено". So "connected" and "connecting" and
  "idle" are distinguished by shield tint and one word — while the same blue (`color_fab_active`
  `#4C8DFF`) is used for `connectActiveColor` **and** `connectedColor` (`themes.xml`). Connecting-blue
  and connected-blue are literally the same hex. The user's only reliable connected signal is a
  crossfade they may not have been looking at, plus a 2-second toast.
- **The knob is huge and empty.** 176dp of `#1A1D21` disc carrying an 80dp glyph — 96dp of dead
  surface. Meanwhile the two things people actually want next to the button (which server, how much
  traffic is left) are 300dp further down.
- **Disabled state is a 0.38 alpha with no explanation** (`:1690-1691`). A dimmed shield with the
  label "Выберите сервер" is a dead end — there is no tappable path from that state to picking a server.
- **Press feedback contradicts the ripple removal.** `card_connect` clears `rippleColor` and
  `cardForegroundColor` (`activity_main.xml:264-265`) so the *only* feedback is a 0.94 scale
  (`:1756-1772`). On a 176dp target, a 6% scale is nearly imperceptible.

### 3.4 The empty states are the weakest surface in the app — and there are two of them, disagreeing

`layout_home_empty.xml` and `layout_servers_empty.xml` solve the identical problem ("no servers, add
one") and share nothing:

| | Home empty | Servers empty |
|---|---|---|
| icon | none | 56dp `ic_cloud_download_24dp`, `colorPrimary` |
| title | "У вас пока не добавлены подписки." (ru: "Пока нет подписок") | "Нет серверов" |
| subtitle | present | none |
| primary button | QR (filled) | Clipboard (filled) |
| secondary button | Clipboard (outlined) | QR (outlined) |
| clipboard copy | "Добавить из буфера **обмена**" | "Добавить из буфера" |
| spacing | 24/8/24/8 | 64/14/20/10 |
| radius token | hardcoded 20dp | hardcoded 20dp |

The primary/secondary buttons are **swapped between the two screens**. That is not a style nit — it
teaches the user the wrong muscle memory.

Beyond the inconsistency:
- Everything is centre-aligned inside the card (`layout_home_empty.xml:21`, `:38-39`), including the
  body copy. Centred multi-line body text is a slop tell and hurts scan speed.
- Four full-width stacked pill buttons in one card (QR, clipboard, Telegram, site) with no visual
  ranking beyond filled-vs-outlined. Two of them are "add a config", two are "sign in" — different
  intents, identical treatment, separated only by an 12sp caption.
- The greeting is "Приветствуем!" — an exclamation, and a word the interface never uses again. It
  also stacks a 24sp/700 heading 12dp above a 16sp/700 heading above a 13sp subtitle: three type
  sizes in a ~60dp band, all bold-ish, all centred.
- `home_empty_title` still carries a trailing full stop in the default locale
  (`values/strings.xml:14`) but not in ru (`values-ru/strings.xml:12`) — the same screen is punctuated
  differently depending on device language.

### 3.5 The account chip is a card pretending to be a chip

`layout_home_account.xml:78-154`. It is a full-width 20dp-radius outlined surface with a 36dp avatar,
two text lines and a chevron — i.e. a **row**, not a chip. It sits directly above the hero, so the
first thing on the screen after the numbers is the user's own name, which is the least actionable
information Home carries. And it duplicates the Account tab that is *already visible in the nav bar
at the same moment* (`MainActivity.kt:1061`) — two entry points, one destination, both permanently on
screen. The secondary line's default text is "Управление аккаунтом", an instruction, where an
information design would put the thing the user actually needs (days remaining, plan name).

### 3.6 The subscription meta bar is a control panel wearing a status card's clothes

`layout_subscription_meta_bar.xml` packs, into ~140dp:

- a 48dp disclosure chevron that **collapses the server list, not the card it lives in**
  (`MainActivity.kt:971-978`) — a disclosure triangle that discloses someone else's content;
- title + a metadata subtitle ("09.07.2026 07:08 · Автообновление — 1 ч.") that exposes sync plumbing
  to a consumer;
- three 36dp icon buttons (ping / refresh / pin) whose meanings are not guessable;
- a 160dp traffic pill with the number set **inside** the bar at 11sp (`:172-176`) — text on a
  progress fill, which breaks contrast as the fill crosses under it;
- an expiry marker pinned right;
- a tonal "Поддержка" button and a 36dp Telegram icon **that do the exact same thing** —
  `onOpenSupport` and `onOpenTelegram` both call `openSubUrl(sub.supportUrl)`
  (`MainActivity.kt:836-837`);
- a destructive long-press delete with no visible affordance (`HomeMetaPagerAdapter.kt:64`).

That is seven interactive targets, five of them undiscoverable, immediately below the hero. It is
also a *third* card recipe: the chip uses `bg_card_incy`, the empty/memory cards use `MaterialCardView
+ colorSurface + 1dp outlineVariant`, the meta bar uses `bg_server_card` (ripple variant), rows use
`bg_server_row` (selector). Four drawables expressing one intent.

And the app renders a **subscription** two completely different ways: this bar on Home, versus
`item_subscription_card.xml` (name + tariff badge + expiry + devices, clean) on the Account tab via
`SubscriptionPagerAdapter` (`AccountFragment.kt:137`). Same object, two designs, two mental models.

### 3.7 Bottom navigation

- **Three simultaneous active-state signals**: colour tween (`:406-422`), weight 500→700 (`:393-403`),
  and a 34×3 pill *below the label* (`:378`, `activity_main.xml:567-573`). Any one would do; the pill
  under the label is the least conventional and the most likely to read as a stray line.
- **Stock Material glyphs.** `ic_nav_home` is the plain house, `ic_nav_servers` the stock storage
  rectangles, `ic_nav_account` the stock person. They also carry `android:tint="?attr/colorControlNormal"`
  inside the vector *and* `app:tint` in the layout *and* a runtime `setColorFilter` — triple tinting.
  Against a hand-built hero shield this reads as two different products.
- **The scrim is the wrong colour.** `bg_bottom_nav_scrim.xml` fades to `?attr/colorSurface` `#141619`,
  but the page behind it is `android:colorBackground` `#0A0B0D` (and the home gradient's edge is also
  `#0A0B0D`). The bottom 160dp of *every tab* therefore fades to a band that is **lighter** than the
  screen it sits on. 160dp is also ~2.8× the 56dp bar it is meant to soften.
- **The nav vanishes entirely** in the signed-out/no-servers state (`:723-724`). Defensible as an
  onboarding choice, but it means the very first screen has zero wayfinding and no way to reach
  Settings — including Language, which a non-Russian speaker will want immediately (see §3.10).

### 3.8 Typography

The ramp itself (`values/styles.xml:56-127`) is sound: Display 34/700, Headline 24/700, Title 16/700,
Body 14, Subtitle 13, Caption 12, Chip 11/500, Numeric with `tnum`. Home undermines it:

- **Inline overrides everywhere** instead of using the ramp: `textSize="13sp"` + `textFontWeight="700"`
  on the speeds (`activity_main.xml:112-113`, `:154-155`), `14sp`/`700` on the timer (`:127-128`),
  `11sp`/`500` on the traffic label (`layout_subscription_meta_bar.xml:174-175`), `16sp` bold on the
  avatar monogram (`layout_home_account.xml:107-108`), `12sp`/`700` on ping results
  (`item_recycler_main.xml:120-121`), `14sp` on the search field (`layout_servers_header.xml:104`).
  Six ad-hoc sizes on one screen, none of them a token.
- **Inverted emphasis in the stats row.** The uptime is the *largest* (14sp) and the *dimmest*
  (`colorOnSurfaceVariant`, `activity_main.xml:126`), while the speeds are smaller (13sp) but brighter
  (`colorOnSurface`). The number people glance at is the one de-emphasised.
- **`↑` and `↓` are text glyphs**, not icons (`:98`, `:140`) — they inherit Space Grotesk metrics, sit
  on the text baseline, and are read aloud by TalkBack as arrows.
- Three bold weights stack in the empty state (Headline 700 → Title 700 → button label) with nothing
  at a normal weight to give the eye a rest.

### 3.9 Colour, background, and a direct contradiction with the repo's own design law

`CLAUDE.md` states, as non-negotiable: *"No decorative gradients/glows."* The shipped Home is built
out of three:

1. `bg_home_gradient` — a radial bloom on the root (`activity_main.xml:8`);
2. `bg_connect_glow` — a radial halo behind the shield (`:211`);
3. `bg_bottom_nav_scrim` — a linear fade (`:517`).

`docs/design-home-polish.md` asked for all three, so the contradiction is between two internal
documents, not a mistake by one author — but it needs resolving before any redesign, because the
answer determines whether the hero keeps its bloom.

Two concrete consequences of how the gradient was applied:

- The doc explicitly recommended putting the gradient on **`group_home`** so the glow is confined to
  the Home tab (`design-home-polish.md`, "option A"). It shipped on **`home_root`**
  (`activity_main.xml:5-8`). `group_servers` (`:455-460`), `layout_servers_header` (`:8`),
  `layout_settings_content` (`:7`, transparent) and `group_account` all have transparent backgrounds —
  so **Servers, Settings and Account all render on the connect-glow**, with a navy bloom behind a
  settings list. That is the shipped behaviour, not a hypothesis.
- A full **light theme exists** (`values/colors.xml`: background `#F4F7FC`, surface `#FFFFFF`, primary
  `#1E5FC7`, plus a light `bg_home_gradient` and light ring/glow) even though the design law is
  "Incy = pure dark". Every fix now has to be made twice, and the light variant is visibly less
  considered (the light ring is `#2E1E5FC7`/`#701E5FC7`, i.e. the dark ring's alpha recipe with a
  different hue).

Positive: text contrast is fine. `#9BA1AD` on `#0A0B0D` computes to ≈7.6:1, comfortably AA.

### 3.10 Copy and localisation — the default locale is a language salad

`values/strings.xml` is the fallback for every non-Russian device, and it mixes languages **inside the
same bottom bar**:

- `bottom_nav_home` = "Home" (`:336`), `bottom_nav_servers` = "Servers" (`:337`), `bottom_nav_settings`
  = "Настройки" (`:565`). One bar, two alphabets.
- `home_welcome_title` "Приветствуем!" (`:13`), `home_empty_title` (`:14`), `home_or_sign_in` (`:18`),
  `home_buy_subscription` (`:20`) are Russian-only — no `values-ru` override needed because the default
  *is* Russian; an English device gets them verbatim.
- Meanwhile `sub_expires` "Until %1$s" (`:341`), `sub_expired` "Expired" (`:342`),
  `sub_auto_update_label` "Auto-update — %1$s" (`:346`), `sub_support` "Support" (`:352`),
  `memory_app_usage` "App memory" (`:355`), `memory_normal/elevated/high` (`:357-359`),
  `connection_connecting` "Connecting…" (`:445`), `toast_status_*` (`:56-59`),
  `server_selected_reconnect_prompt` (`:612`) are English.
- `memory_app_usage`, `memory_normal`, `memory_elevated`, `memory_high` have **no `values-ru`
  override** — so on a Russian device the Home memory card reads "App memory / 25 MB · Normal".
- `toast_updated` = "Обновлено" lives in `values/strings_ui_polish.xml:4` with no English variant — an
  English device gets a Russian toast after refreshing a subscription.
- **Broken plurals**: `servers_count` = "%d серверов" (`:6`) and `providers_count` = "%d провайдеров"
  (`:7`) with no `values-ru` override and no `<plurals>`. The Servers header therefore renders
  "1 серверов · 1 провайдеров" (`MainActivity.kt:809-811`).
- Hardcoded Russian literals in Kotlin, bypassing resources entirely: `MainActivity.kt:2079-2082`
  (manual-entry validation), `:2162-2179` (import result toasts, incl. "Эта ссылка не от departament…").

Copy quality, separately from language: the meta subtitle exposes a timestamp and an auto-update
interval; the memory card exposes RSS in MB with a severity word; the connect label shows a raw
subscription remark including a flag emoji. None of that is written in a product voice.

### 3.11 Motion

The tempo tokens (`values/motion.xml`) and the ease-out-only rule are good, and most of Home honours
them. Two places do not:

- The connecting glow breathe is `INFINITE`/`REVERSE` at 850 ms with
  `AccelerateDecelerateInterpolator` (`MainActivity.kt:1799-1810`) — an ease-in-out loop, off-token
  duration, in a system that declares "ease-out only, no bounce".
- The nav colour tween hardcodes `duration = 200` (`:413`) instead of `durState` (220) — the tokens
  exist and are read three lines away.

Also: the cold-start `shield_assemble` (`:301-308`) fires once per process, but in the **empty**
state `card_hero` is `GONE` (`:691`), so the one designed entrance animation plays on an invisible
view on a true first launch and is then permanently consumed by the static `heroAssembled` flag (`:211`).

### 3.12 Accessibility and touch targets

`CLAUDE.md` mandates ≥48dp touch targets. Home violates it in eight places:

| Target | Size | File:line |
|---|---|---|
| `btn_home_add` ("+") | 42dp | `activity_main.xml:163-164` |
| `btn_ping` | 36dp | `layout_subscription_meta_bar.xml:76-77` |
| `btn_refresh` | 36dp | `:89-90` |
| `btn_pin` | 36dp | `:112-113` |
| `btn_telegram` | 36dp | `:240-241` |
| `btn_cta_dismiss` ("✕") | 40dp | `layout_home_account.xml:64-65` |
| servers-header buttons ×4 | 36dp | `layout_servers_header.xml:30-75` |
| `btn_support` | `minHeight="0dp"` | `layout_subscription_meta_bar.xml:222` |

Other a11y problems:

- `card_connect`'s content description is `@string/tasker_start_service` — "Запуск службы" /
  "Start Service", the **Tasker plugin's** string (`activity_main.xml:259`, `values/strings.xml:408`).
  `applyIdleState` re-sets the same string (`MainActivity.kt:1682`). TalkBack announces a
  developer-facing automation label for the app's primary control, and it never announces state.
- Both shields are `importantForAccessibility="no"` (`:273`, `:285`) and `tv_connection_status` is a
  separate node, so the button and its state are never announced together.
- The `✕` dismiss is a `TextView` with `android:text="✕"` (`layout_home_account.xml:71`), not a button.
- The stats row has no content descriptions; TalkBack reads "↑ 0 KB/s", "00:00:00", "↓ 0 KB/s".
- Page dots are bare `View`s with no accessibility role (`MainActivity.kt:930-936`).

---

## 4. Concrete defects worth fixing regardless of redesign

1. **The onboarding card is edge-to-edge.** `activity_main.xml:396-400` includes `layout_home_empty`
   specifying `layout_width` **and** `layout_height` but no margins. Android's documented `<include>`
   rule is that once you override both dimensions, *all* layout params come from the `<include>` tag —
   so the root card's own `marginStart/End="@dimen/screen_gutter"` and `marginTop/Bottom="@dimen/space_4"`
   (`layout_home_empty.xml:8-11`) are **discarded**. The onboarding card renders full-bleed with its
   20dp corners clipped against the screen edges, while the welcome heading above it has 24dp margins
   and the account chip has 16dp. Compare `activity_main.xml:175-182`, where the author *did* repeat
   the margins on the include — proving the pattern was understood and this one was missed.

2. **The "Привяжите Telegram" CTA is unreachable dead code.** `updateAccountGate()` unconditionally
   sets `header.root.isVisible = loggedIn` (`MainActivity.kt:1056`) **and**
   `header.groupLogin.isVisible = false` (`:1059`). `updateLoginCtaVisibility()` (`:1089-1097`) then
   sets `ctaLinkTelegram.isVisible = show` — but its parent and grandparent are both `GONE` whenever
   the condition (`!isLoggedIn`) can be true. It is called from three sites (`:769`, `:1023`, `:1984`)
   and can never produce visible UI. `layout_home_account.xml:22-75` is 54 lines of unreachable layout.

3. **Support and Telegram buttons are duplicates.** `MainActivity.kt:836-837` — both lambdas call
   `openSubUrl(MmkvManager.decodeSubscription(subId)?.supportUrl)`. Two visually distinct controls,
   identical behaviour.

4. **First-frame label is wrong copy.** `tv_connection_status` defaults to `@string/title_file_chooser`
   ("Выберите профиль" / "Select a config", `activity_main.xml:305`) — a file-chooser string — until
   `applyRunningState`/`updateSelectedServer` overwrite it.

5. **The memory card survives the empty state.** `updateMemoryCard()` (`MainActivity.kt:1870-1882`)
   keys only off `PREF_SHOW_MEMORY`; `updateHomeEmptyState()` (`:686-710`) never hides
   `card_memory`. With the pref on, first launch shows a debug readout floating above the centred
   welcome block. `card_memory` also has no `marginTop` (`activity_main.xml:312-319`), so it butts
   against the hero's bottom padding.

6. **Signed-in users lose every import path on Home.** In the signed-in/no-servers state,
   `updateOnboardingLogin()` hides QR and clipboard (`:1141-1142`) while `updateHomeEmptyState()` has
   already hidden `home_stats_row` — and with it `btn_home_add` (`:697`). The only Home affordance
   left is "Купить подписку". A user with a valid pasted subscription link must discover the Servers tab.

7. **Bottom scrim is lighter than the page.** `bg_bottom_nav_scrim.xml` fades to `?attr/colorSurface`
   `#141619` over a `#0A0B0D` background — a visible lighter band across the bottom 160dp of every tab.

8. **The home glow leaks into every tab.** Gradient set on `home_root` (`activity_main.xml:8`) rather
   than `group_home`, and all other tab roots are transparent.

9. **Dead toolbar.** `setupToolbar(binding.toolbar, …)` runs at `MainActivity.kt:236` and
   `onCreateOptionsMenu` returns `false` (`:1995-2001`), while `showTab` hides the AppBar on every tab
   (`:448`). The `AppBarLayout` + `MaterialToolbar` (`activity_main.xml:13-26`) still inflate, still
   become the support ActionBar, and still receive inset padding (`:499`).

10. **`notifyDataSetChanged()` on both pagers** — `HomeMetaPagerAdapter.kt:34` and
    `SubscriptionPagerAdapter.kt:33`. Every subscription refresh rebuilds all pages, which is what
    forces the expensive `measureHomeMetaHeight()` re-measure of every page
    (`MainActivity.kt:895-917`).

11. **`String.format` without a Locale** for the uptime (`MainActivity.kt:1967`) — will emit
    Eastern-Arabic digits under `fa`/`ar`, both of which have `values-*` dirs in this repo.

12. Broken plurals and mixed-language defaults — see §3.10.

---

## 5. What a top-tier minimalist redesign changes

Concrete, in priority order. Each is stated as a decision, not an option.

### 5.1 Collapse five bands into two

**Above the fold: one hero unit. Below it: the list.** Everything else is either folded into the hero
or moved off Home.

- **Merge the stats row into the hero.** Uptime and up/down speeds only exist while connected. Put
  them *under the connect control* as a single quiet line, revealed on connect and absent otherwise.
  Delete the invisible-42dp-spacer centring hack (`activity_main.xml:70-77`) with it.
- **Delete the account chip from Home.** The Account tab is already in the nav. If identity must be on
  Home, it is a 32dp avatar in the top-right corner of the hero region, not a full-width row.
- **Replace the meta bar with one line.** The only two facts a user needs on Home are *plan / days
  left* and *traffic used*. That is one 13sp line plus a 2dp progress hairline directly under the
  connect label — no card, no chevron, no icon cluster. Ping, refresh, pin, delete, support, Telegram
  move to the Servers tab header and the Account tab, where they already have homes.
- **Delete the memory card from Home** (it belongs in Settings → diagnostics).

Result: hero occupies the top ~55% of the viewport, first server row lands around 400dp instead of
580dp, and 3–4 rows are visible without scrolling.

### 5.2 Rebuild the connect control

- **One ring, not four.** A single 2dp ring at the hero diameter. The connecting arc *replaces* it
  (same radius, same thickness) rather than adding a third circle. Remove the inner ring from
  `bg_connect_ring.xml` and remove `progress_connect`'s separate 212dp diameter — set it equal to the
  ring.
- **Redraw the shield on a 96 or 128 grid**, as a proper 2-master pair (outline / filled) with rounded
  shoulders and a stroke that reads at 3–3.5dp *optical* weight at final size. The current 8dp-at-80dp
  fill-derived outline is the single most visible quality problem on the screen.
- **Shrink the disc.** 176dp of flat fill around an 80dp glyph is 96dp of nothing. Either the glyph
  grows to ~110dp or the disc drops to ~132dp. Target a glyph:disc ratio near 0.62, not 0.45.
- **Give the three states three distinct colours**, not two. Idle = `colorOnSurfaceVariant`;
  connecting = a desaturated blue (or the accent at 60%); connected = the full accent. Today
  `connectActiveColor` and `connectedColor` are the same token value (`themes.xml`).
- **State must be legible without motion and without colour alone.** Under the shield: a status word
  in the accent (`Подключено` / `Подключение…` / `Не подключено`) *and* the server name on a second,
  quieter line. Right now the server name replaces the status word, so a colour-blind user in a
  screenshot cannot tell connected from idle.
- **Press feedback**: 0.94 on a 176dp target is invisible. Combine scale with a 1-frame ring
  brightness step, or scale the ring rather than the card.
- **No-server state becomes actionable**: instead of a 0.38-alpha dead knob, the label becomes a
  tappable "Выберите сервер →" that jumps to the Servers tab.

### 5.3 One empty state, one recipe, left-aligned

- Delete `layout_servers_empty.xml`'s bespoke geometry and rebuild both empties from a single
  `layout_empty_state.xml`: optional 40dp glyph, **left-aligned** title (App.Title), left-aligned
  body (App.Subtitle, max ~40ch), then **one** primary action and at most one text-button secondary.
- Split intents. Onboarding is two *questions*, not four buttons: "У меня есть подписка" (→ QR /
  clipboard / manual, in a sheet) and "Войти" (→ Telegram / site). Present two choices, then reveal
  the sub-options. Four equal pills in one card is why it reads as a form.
- Same primary action, same wording, in both empties. Pick one: `Добавить из буфера обмена`.
- Kill "Приветствуем!". The first line should say what the app is for: e.g. "Подключение защищено" /
  "Добавьте подписку — и всё". Sentence case, no exclamation.
- Restore the gutter (fix defect #1) so the card never touches the screen edge.

### 5.4 Nav: one active signal, custom glyphs, correct scrim

- Keep **colour + weight**; delete the 34×3 pill under the label (`activity_main.xml:567-573` and its
  three siblings) — or replace both with a Material-correct pill *behind the icon*. Three signals for
  one boolean is noise.
- Commission/redraw the four glyphs as one family at 24dp with matching stroke weight, replacing the
  three stock Material paths. Remove the baked `android:tint` from the vectors since the code sets a
  colour filter anyway.
- Change the scrim's end colour to `?android:attr/colorBackground` and shorten it from 160dp to
  ~96dp (bar height + inset + one fade).
- Keep the "no nav during pure onboarding" behaviour, but add a single top-right text affordance for
  language, so a non-Russian first-run user is not trapped (see §3.10).

### 5.5 Tokenise Home the way Settings already is

- Every dimension on Home comes from `dimens.xml`. Replace `42/36/28/18/52/13/3` with `tile_size 40`,
  `tile_glyph 22`, `space_*`, and add exactly two new tokens if genuinely needed (`hero_size`,
  `hero_glyph`). Delete `dot_gap`, `sub_card_height`, `radius_pill` or start using them.
- One card drawable. `bg_card_incy` becomes the single surface recipe; `bg_server_card` becomes
  `bg_card_incy` + ripple; the two `MaterialCardView` instances adopt `@dimen/radius_card` and the
  same stroke. Four recipes → one.
- Every text node uses a `TextAppearance.App.*` with no inline `textSize`/`textFontWeight`. If a
  13sp/700 numeric is needed, it becomes `TextAppearance.App.Numeric.Strong` — a token, once.
- Enforce 48dp minimum on all eight offenders in §3.12 (grow the touch target, keep the 22dp glyph).

### 5.6 Resolve the gradient question, then apply it in one place

Decide between `CLAUDE.md` ("no decorative gradients") and `design-home-polish.md` (the Incy bloom).
The defensible middle: **keep exactly one bloom**, behind the connect control, scoped to the Home tab
(move the background from `home_root` to `group_home`, as the doc originally specified), and delete
the other two — the nav scrim becomes a flat `colorBackground` fade, and `view_connect_glow` becomes
the *only* radial in the app. Then the glow means something (it is the connected signal) instead of
being ambient decoration on the settings screen.

### 5.7 Fix language before fixing pixels

Nothing above matters if the bottom bar says "Home / Servers / Настройки". Move all Russian copy into
`values-ru`, make `values/` fully English, convert `servers_count`/`providers_count` to `<plurals>`,
add `values-ru` entries for `memory_*`, move the four hardcoded Kotlin literals
(`MainActivity.kt:2079-2082`, `:2162-2179`) into resources, and give `card_connect` a real, stateful
content description instead of `tasker_start_service`.

---

## 6. Appendix — notable line references

| Concern | Where |
|---|---|
| Toolbar hidden on all tabs | `MainActivity.kt:448` |
| Empty-state visibility switchboard | `MainActivity.kt:686-710` |
| Onboarding auth/buy switchboard | `MainActivity.kt:1131-1146` |
| Account gate (chip + nav tab) | `MainActivity.kt:1048-1064` |
| Bottom-nav hide during onboarding | `MainActivity.kt:720-732` |
| Connect state machine | `MainActivity.kt:1581-1721` |
| Connecting breathe (off-token motion) | `MainActivity.kt:1799-1810` |
| Loading arc shared with connect | `MainActivity.kt:1850-1862` |
| Status toast (only error surface) | `MainActivity.kt:1735-1746` |
| Meta bar bind | `MainActivity.kt:1249-1321` |
| Meta pager height probe | `MainActivity.kt:895-917` |
| Page dots built in code | `MainActivity.kt:920-955` |
| Hero geometry | `activity_main.xml:200-291` |
| Stats row centring hack | `activity_main.xml:58-172` |
| Nav item template | `activity_main.xml:536-575` |
| Empty-state include (margins lost) | `activity_main.xml:396-400` |
| Dead login group | `layout_home_account.xml:22-75` |
| Type ramp | `values/styles.xml:56-127` |
| Spacing/radius tokens | `values/dimens.xml` |
| Motion tokens | `values/motion.xml` |
| Dark palette | `values-night/colors.xml` |
| Light palette | `values/colors.xml:12-90` |
