# 11 - App structure and information architecture

**Departament VPN - the one navigation model both clients are rebuilt on.**

This file decides *what screens exist, what each one owns, how you get there, and how you get
back*. It is the map that every later screen spec hangs off. It does not decide pixels: the token
values are `00-rules.md` section 3, the visual language is `03-direction.md`, and the per-screen
compositions are documents 12 and later.

**Precedence.** `00-rules.md` outranks this file on any value or ban. `03-direction.md` outranks it
on visual argument. Where those two documents disagree with each other, section 15 records the
resolution. Where this file needs a rule the law does not yet carry, it is written in section 16 in
`00-rules.md` section 18 row format and is not implemented until that row is pasted into the rules
file.

| | Android | Desktop |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Paths below are relative to | `/home/user/dp/V2rayNG/app/src/main/` | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/` |

Inputs read before writing this: `00-rules.md`, `01-inventory-android.md`, `02-inventory-pc.md`,
`03-direction.md`, `.claude/skills/impeccable/reference/product.md`, `reference/adapt.native.md`,
`reference/onboard.md`, `AndroidManifest.xml`, `res/xml/shortcuts.xml`,
`java/com/v2ray/ang/ui/UrlSchemeActivity.kt`, `Common/L.Shell.cs`,
`Assets/GlobalResources.axaml`.

---

## 1. What the product actually contains

Before choosing tabs, name the nouns. Everything the two clients hold is one of six things:

| Noun | Instances | Where it comes from | How often the user touches it |
|---|---|---|---|
| **Подключение** (the tunnel) | exactly one, on or off | the local core | daily, 4 seconds |
| **Сервер** | 15 to 150 | providers, QR, clipboard, manual | weekly, when the current one degrades |
| **Провайдер** (subscription URL that yields servers) | 1 to 4 | the account, or pasted by hand | monthly |
| **Подписка** (the paid service) | 1 to 4 | the Departament backend | monthly, and whenever it is running out |
| **Аккаунт** (identity, balance, devices, payments) | one | the Departament backend | monthly |
| **Настройка** | about 40 | local preferences plus the engine | rarely, and then in a burst |

Two of those six are the reason the app is opened at all: the tunnel and, near the end of the
month, the subscription. The scene sentence in `03-direction.md` 2.1 says the same thing: *"He wants
the shield blue, the name of the city under it, and the phone back in his pocket. He will open the
app again that evening only if the subscription is running out."*

Note that **сервер and провайдер are two different nouns that the current build conflates**:
`layout_subscription_meta_bar.xml` on Android and `SubscriptionMetaView.axaml` on desktop each try
to be a provider header, a subscription readout and a traffic meter at once. Section 4.2 splits
them.

---

## 2. The destination set

### 2.0 Owner decision, 2026-07-26 — desktop has no Серверы destination

**The owner has decided that the desktop must not gain a Серверы tab.** Per `00-rules.md` 0.1 the
owner's explicit request outranks this document, so section 2.1 below stands for **Android only**.

Desktop keeps **three** destinations: **Главная**, **Аккаунт**, **Настройки**. `Geo.Nav.Servers`
stays declared and unused, and `Nav_Servers` is not added to `Common/L.Shell.cs`. Section 2.3's
argument is recorded but overruled; sections 3.2 and 4.2 apply to Android alone, and the desktop rail
does **not** gain a fourth item at index 1.

The server list therefore stays inside Главная on desktop.

**Главная keeps the functionality it has today, on both platforms.** The owner has been explicit
twice: no Серверы destination on desktop, and **no search on Главная** — «как выглядела главная по
функционалу такая и должна остаться, ЧТО НА ПК, что на андроиде». Section 2.3's argument is recorded
but overruled, and so is the corrective work order that followed it: an earlier revision of this
section told the desktop wave to add in-place filtering to Главная, and that was my inference, not a
request. It is withdrawn. A search field was built on it and is being removed.

The owner then narrowed it once more: «просто убрать полностью вкладку сервера да и все, а функции из
вкладки сервера не надо никуда пихать». So the Серверы destination is simply gone on desktop, and
**nothing from it is relocated**. Not the search, not a row-actions control, not the compact-mode fold
fix — that last one reads as layout, but the list only sits below the fold because Главная is carrying
a screen's worth of list, and re-proportioning it to fix that is re-scoping by another name.

The three problems section 2.3 identified are therefore **acknowledged and not solved here**:

- the desktop has no server search at all, and the only field ever written lives in a view nothing
  instantiates;
- the seven per-server actions are reachable only from a right-click menu, which is the app's only
  route to edit, delete, share and QR;
- in compact mode the list starts below a 440px-minimum hero on a 630px window.

They are recorded so they are not mistaken for oversights. If any of them is ever worth solving, the
owner decides where — and the answer is not "quietly, inside Главная".

**The rule: Главная is a restyle.** Same controls, same capabilities, same information, drawn on the
new class styles. Anything that adds a capability to that screen needs the owner to ask for it, and a
spec that asks for one is overruled.

### 2.1 The decision

**Four destinations, fixed, identical on both platforms, in this order.**
(Android only — see 2.0. On desktop, drop row 2 and read the set as three.)

| # | Label (RU) | Android icon | Desktop glyph | Owns |
|---|---|---|---|---|
| 1 | **Главная** | `@drawable/ic_nav_home` | `Geo.Nav.Home` | The tunnel: connect, current server identity, live numbers, the two summary rows |
| 2 | **Серверы** | `@drawable/ic_nav_servers` | `Geo.Nav.Servers` | Every server and every provider: list, search, sort, per-item actions, add, edit |
| 3 | **Аккаунт** | `@drawable/ic_nav_account` | `Geo.Nav.Account` | Identity, sign-in, balance, subscriptions, devices, payments, buying |
| 4 | **Настройки** | `@drawable/ic_nav_settings` | `Geo.Nav.Settings` | Everything configurable, plus data, plus About and the log |

All four glyphs already exist on both platforms. `Geo.Nav.Servers` is already declared at
`Assets/GlobalResources.axaml:313` and is currently unused, because desktop has no Servers
destination. `@drawable/ic_nav_more.xml` on Android has no destination in the new set and is
deleted.

Labels are always visible, sentence case, and never change. `Common/L.Shell.cs` gains
`Nav_Servers` = «Серверы» / "Servers" alongside the existing `Nav_Home` / `Nav_Settings` /
`Nav_Account`.

### 2.2 The set never changes shape

Today the fourth item appears and disappears:

- Android hides `nav_account` unless `AccountSession.isLoggedIn()`, and
  `updateBottomNavVisibility()` (`ui/MainActivity.kt:713`) hides **the entire bar** when the user is
  signed out and has no servers, so first launch is a bar-less screen.
- Desktop collapses «Аккаунт» to zero width when signed out (`Views/BottomNavBar.axaml`).

Both stop. A navigation bar that grows an item after an action the user has not taken yet is the
opposite of `03-direction.md` 1.5 ("the interface predicts itself"), and it means the app teaches
one structure and then replaces it. **All four destinations are present from the first frame, in
every state, signed in or not.** When the user is signed out, «Аккаунт» is the sign-in screen. That
is a designed state (section 4.3), not an absence.

### 2.3 Why Серверы is a destination and not a column inside Главная

`02-inventory-pc.md` section 6 asks this question directly. The answer is: a destination.

- It is a **list of 15 to 150 records with its own search, sort, grouping, per-item actions and
  empty states**. `00-rules.md` 4.6 requires virtualisation, a designed no-results state and
  in-place filtering. That is a screen, not a panel.
- Desktop today buries it: in compact mode the list starts below a 440px-minimum hero on a 630px
  window, so the primary list in the app is permanently below the fold
  (`Views/CompactHomeView.axaml`). In wide mode it is a 440px column with no header, no count and
  no search (`Views/HomeView.axaml:211`).
- **The app has no server search at all today.** The only search field ever written lives in
  `Views/CompactServersView.axaml:90`, in a view nothing instantiates. With 150 servers that is a
  functional hole, and a hole with no surface to put the fix on.
- The seven per-server actions currently hide in a right-click menu on desktop
  (`Views/ServerListView.axaml:149`) and are **completely unreachable on Android**
  (`MainRecyclerAdapter.kt:56` no longer invokes `onItemLongClick`, so `ServerActionsSheet` never
  opens and `ServerActivity` and its four siblings have no caller). Those actions need a screen that
  owns them.
- Giving Servers a destination is what lets Главная become the four-second screen the scene
  sentence describes, instead of the seven unrelated blocks it is today.

**The cost, stated honestly:** the user needs one extra tap to change server from Главная. That is
paid back by the «Сервер» summary row on Главная (section 4.1), which shows the current server and
opens Серверы directly, and by the fact that changing server is a weekly action, not a daily one.

### 2.4 Alternatives considered and rejected

| Alternative | Why rejected |
|---|---|
| Three tabs (Главная / Аккаунт / Настройки), servers inside Главная | Today's desktop model. Produces a scroll where the hero cannot be skipped and the list has no home for search, sort or bulk actions. Fails `00-rules.md` 4.6. |
| Five tabs, splitting Подписка out of Аккаунт | `00-rules.md` 7.7 allows up to 5, but subscription, balance, devices and payments are one object seen from four angles. Splitting them means two tabs that both need the subscription card. |
| Four tabs with Аккаунт conditional | Section 2.2. |
| A drawer for Настройки | Drawers hide navigation behind a gesture and are a 2016 idiom. `00-rules.md` 7.7 says bottom navigation with 3 to 5 visible destinations. |
| «Профиль» or «Личный кабинет» instead of «Аккаунт» | Forbidden by the terminology lock, `00-rules.md` 9.3. |

---

## 3. The shell

### 3.1 Android

```
MainActivity  (singleTask, no ActionBar, edge-to-edge, enableOnBackInvokedCallback="true")
└─ activity_main.xml
   ├─ FragmentContainerView #tab_host          (fills, above the nav bar)
   │    HomeFragment · ServersFragment · AccountFragment · SettingsFragment
   │    keep-alive: show/hide, never replace, so scroll and filter state survive a tab switch
   ├─ View #sync_overlay                       (full-bleed, post-sign-in import gate, section 5.6)
   └─ BottomNavigationView #bottom_nav         (56dp + nav-bar inset, @menu/menu_bottom_nav)
```

Structural requirements:

1. **The `AppBarLayout` / `MaterialToolbar` at the top of `activity_main.xml` is deleted.** It is
   inflated, given the status-bar inset and then set to `GONE` on every tab switch
   (`MainActivity.kt:441`). It costs a measure pass and renders nothing.
2. **All four tabs become Fragments.** Today three of the four are sibling `View` groups toggled by
   `isVisible`, with 1 536 lines of settings markup inlined into the shell layout. That is why
   `MainActivity.kt` is 2 777 lines. Each Fragment owns its own scroll state, its own data binding
   and its own state machine.
3. **The bottom bar becomes a real `BottomNavigationView`.** The current bar is a hand-rolled
   `LinearLayout` with four weighted children and an `ArgbEvaluator` tint tween
   (`MainActivity.kt:343`). A custom bottom bar is the exact tell `reference/android.md` names
   ("a bottom-only navigation copied from iPhone"). Configuration:
   - `app:labelVisibilityMode="labeled"`
   - `app:itemRippleColor="@android:color/transparent"` - owner request `00-rules.md` 0.4.8, no
     ripple glow
   - `app:itemActiveIndicatorStyle="@style/BottomNavIndicator"` - the 64x34
     `colorPrimaryContainer` pill required by `00-rules.md` 7.7, which already exists at
     `res/values/styles.xml:27` and is currently orphaned. Its `marginHorizontal` is `6dp`, which
     is off-scale and becomes `@dimen/space_4`
   - `app:itemTextAppearanceActive` with `android:textFontWeight="700"`,
     `app:itemTextAppearanceInactive` at 500 - colour plus weight, two channels
     (`00-rules.md` 6.3)
   - `app:itemIconTint="@color/bottom_nav_item_color"` - the existing orphaned selector
   - `@menu/menu_bottom_nav.xml` becomes the single source of the four destinations. It exists and
     is unused today.
   This resurrects three orphaned resources, deletes about 180 lines of layout and about 60 lines
   of tint animation, and gets insets, TalkBack roles and the `sw600dp` rail conversion for free.
4. **`sw600dp`: `NavigationRailView` on the leading edge**, same menu resource, same order, same
   labels, gutter steps to 24dp, content capped at 720dp centred (`00-rules.md` 4.1, 11.4).
5. **One inset strategy.** `setupEdgeToEdge()` currently applies the top inset to four groups
   individually while `activity_base.xml` uses `fitsSystemWindows="true"`. The shell applies the
   status-bar inset once to `#tab_host`, the navigation-bar inset once to `#bottom_nav`, and the
   `ime()` inset to any Fragment that contains an input. `ime()` is applied **nowhere** today.
6. **Sub-pages stay Activities**, pushed on top of `MainActivity`, hosted by a rebuilt
   `activity_base.xml` that carries the seamless toolbar of section 3.3. Every sub-page Activity
   declares `android:parentActivityName=".ui.MainActivity"`; six of them do today.

### 3.2 Desktop

```
MainWindow  (default 1040x720, minimum 900x600, remembers size and position)
└─ Panel #windowRoot
   ├─ LayoutTransformControl #uiScaleHost         (Ctrl +/-/0 zoom, keep)
   │  └─ Grid #chromeRoot  RowDefinitions="32,*"
   │     ├─ [0] custom caption 32px: wordmark left, min/max/close right at 40x32 each
   │     └─ [1] Panel
   │        ├─ Grid #bodyRoot  ColumnDefinitions="76,1,*"
   │        │   ├─ [0] Border #railHost      NavRail, 4 items, travelling indicator
   │        │   ├─ [1] 1px Brush.OutlineVariant hairline
   │        │   └─ [2] Panel #contentHost    HomeView · ServersView · AccountView · SettingsView
   │        ├─ AccountSyncView #accountSyncView   (overlay, section 5.6)
   │        ├─ ContentControl #subPageHost        (per-tab stack, section 6.2)
   │        └─ Border #toastHost                  (feedback channel, section 8)
   └─ Grid #resizeGripHost                        (8 native resize zones, keep)
```

Structural requirements:

1. **Compact mode is deleted.** `CompactBreakpointWidth = 760`, `LayoutHysteresis = 24`
   (`Views/MainWindow.axaml.cs:31`), `ApplyLayoutMode(bool compact)` (`:696`), `ViewFor(tab)`
   (`:463`), `BindActiveHome()` (`:479`), `ToggleLayoutSize()` (`:1267`), `Views/BottomNavBar.axaml`
   and `Views/CompactHomeView.axaml` all go. A desktop application that opens as a 372px phone
   strip is a port artifact, `00-rules.md` 12.3 already mandates a 900x600 floor, and the compact
   branch is the reason two Home views and two Servers views exist. This deletes the
   `Nav.Scrim` gradient and the `navScrim` `OpacityMask` with it, both of which are banned
   decoration.
2. **The rail is always 76px wide and always shows labels.** `#btnRailToggle`
   (`Views/MainWindow.axaml:131`) animates the rail to `Width=0`, which hides navigation
   completely. It is deleted. `00-rules.md` 7.7 requires the same destinations with the same labels
   as Android.
3. **The rail gains a fourth item** at index 1: `#navServers` «Серверы», `Geo.Nav.Servers`.
   `RailSlotY` (`:529`) already computes `index * 64 + 18`, so the travelling 3x28 indicator needs
   no change.
4. Caption buttons go from **44x22 to 40x32** (`Views/MainWindow.axaml:52`, `:359`, `:373`, `:387`),
   which is the `00-rules.md` 7.2 desktop floor of 32px, and the caption row grows from 28 to 32.
5. `Brush.HomeGradient` is removed from `#bodyRoot` (`Views/MainWindow.axaml:434`) and from
   `#contentHost` (`:551`). Both become `Brush.Bg`. See section 15, conflict C-1.
6. **`StatusBarView` stops being a phantom.** It is mounted at `Width=0 Height=0 Opacity=0`
   (`Views/MainWindow.axaml:643`) purely to keep its handlers alive. Its handlers move into the
   shell; the view is deleted.
7. `#contentHost` keeps the keep-alive model (four permanently realised children, swapped by
   opacity plus 16px translate plus `ZIndex`). It is good work and it is why desktop preserves
   scroll position across tab switches, which Android does not yet.

### 3.3 The header model

**One header per tab, 56dp tall, on the ground plane, with no elevation, no divider at rest and no
separate background colour.** This is owner request `00-rules.md` 0.4.6 applied to top-level screens
as well as sub-pages.

| Tab | Leading | Title | Trailing (max 1) |
|---|---|---|---|
| **Главная** | 36dp avatar or initial tile | account row: `@handle` (Title 16/700) over «Управление аккаунтом» (Subtitle 13/400) | 22dp chevron, whole row taps through to Аккаунт |
| **Серверы** | none, title at the 16dp gutter | «Серверы» (Title 16/700) | 40dp icon button «Добавить», `@drawable/ic_add_24dp` |
| **Аккаунт** | none | «Аккаунт» (Title 16/700) | none |
| **Настройки** | none | «Настройки» (Title 16/700) | none |

Notes that make this a single model rather than four exceptions:

- **Главная has no title.** The bottom bar already says «Главная» and marks it selected; repeating
  the word in a bar above the content is chrome that carries no information
  (`03-direction.md` 1.7). Its 56dp header slot is spent on the account row instead, which already
  exists on both platforms (`res/layout/layout_home_account.xml`,
  `Views/HomeAccountChip.axaml`) and is a KEEP on desktop. Every tab therefore starts with 56dp of
  ground-plane header at the same height and the same gutter, and only Главная fills it with data
  instead of a label.
- **The wordmark is not in any tab header.** It appears in exactly three places in the product: the
  desktop title bar, the sign-in gate, and Настройки > О приложении. `03-direction.md` 2.3 makes
  the connect screen the least branded screen in the app, and 3.2's corollary forbids the brand
  spending the accent on advertising itself.
- **No accent in any header.** Header icon buttons are `colorOnSurfaceVariant`. The one lit element
  per screen is never a piece of chrome (`03-direction.md` 3.2).
- **On scroll**, a 1dp `?attr/colorOutlineVariant` / `Brush.OutlineVariant` hairline fades in under
  the header over `motion_state` 220ms once `scrollY > 0`, and fades out at 0. This is the single
  permitted variant in `00-rules.md` 4.8. Nothing else changes: no colour, no elevation, no shadow,
  no transform. `03-direction.md` 8.5 bans scroll-linked transforms, so there is no collapsing or
  resizing title anywhere in the product.

### 3.4 The sub-page toolbar

Unchanged from `00-rules.md` 4.8, restated so implementers do not have to cross-reference:

```
[16 gutter][ 24dp back glyph in a 48dp touch box ][ 16 ][ title, Title 16/700 ][ * ][ 0 or 1 trailing 40dp ][16]
height 56 · background = page background · elevation 0 · no divider at rest
```

Desktop uses the shared `Button.BackNav` class. The eight settings sub-pages and `LoginView`
currently redeclare a local `Button.IconButton:pressed` style verbatim nine times
(`02-inventory-pc.md` V7); they use the shared class instead, and the legacy 32px
`Button.IconButton` is deleted from `Assets/GlobalStyles.axaml:226`.

If a sub-page needs more than one trailing action, the extra actions go into a 40dp overflow
(`@drawable/ic_more_vert_24dp` / a `MenuFlyout`), not into a second visible button.

---

## 4. What each destination owns

Depth cap, from `03-direction.md` 7.3 and enforced throughout: **tab > sub-page > detail. Never a
fourth level.** Sheets, flyouts and dialogs are not levels.

### 4.1 Главная

**Job:** four seconds. Show the tunnel state, let it be changed, say which server, and warn about
the subscription.

Content order, top to bottom, single column, 16dp gutter:

| # | Block | Height | Notes |
|---|---|---|---|
| 1 | Account row (the header slot, 3.3) | 56 | Signed out it becomes a «Войти» row with the same geometry |
| 2 | Status strip, when a condition applies (section 8.2) | 40 | Offline, expired, tunnel error. Absent otherwise |
| 3 | Connect object | 176 disc inside a 200 frame | One disc, `03-direction.md` 10.2: P3 fill, 1dp outline, 80dp shield glyph. No glow, no page gradient, no ambient loop, no second ring |
| 4 | Status line | 2 lines | «Подключено» (Title 16/700) plus an 8dp status dot, then the current server name (Subtitle 13/400, 1 line, ellipsis at end) |
| 5 | Numeric strip | 44, reserved | Three right-aligned columns: приём, отдача, задержка. Numeric role, `tnum`. **Present only while connecting or connected**; the height is reserved and the content fades in over 220ms so nothing reflows |
| 6 | Row group, no header | 2 x 56 | «Сервер» with the current server name as its value, chevron, opens Серверы. «Подписка» with «до 14 августа» as its value plus an «истекает» / «истекла» chip when it applies, chevron, opens Аккаунт at that subscription |

Nothing else. No memory card (`card_memory` and its unreachable `PREF_SHOW_MEMORY` gate are
deleted), no welcome heading, no carousel, no server list, no add button.

**The one lit element** is the connect disc when connected, or the CTA when the screen is in a gated
state. Never both.

States:

| State | What Главная shows |
|---|---|
| **Signed out, no servers** | Account row = «Войти». Disc present, neutral, disabled at 0.38, no press feedback. No numeric strip, no summary rows. One 52dp pill CTA «Добавить провайдера», one text button «Войти в аккаунт» under it |
| **Signed in, no subscription** | Disc disabled. CTA «Купить подписку», subtitle row «Осталось выбрать тариф». Opens Аккаунт > Покупка |
| **Signed in, subscription active, no servers yet** | Disc disabled. CTA «Загрузить серверы», which runs the provider sync inline; on failure the CTA becomes «Повторить» and an error line appears |
| **Disconnected, ready** | Disc neutral and enabled, status «Отключено», server name, no numeric strip, both summary rows |
| **Connecting** | Disc carries the indeterminate ring, and **only while the core is actually negotiating** (`03-direction.md` 8.4). Status «Подключение…». Numeric strip fades in at zeroes |
| **Connected** | Shield filled blue, ring accent at 1dp, status «Подключено» plus green dot, numeric strip live, one 600ms sonar on entry and never again (`00-rules.md` 8.4) |
| **Disconnecting** | Status «Отключение…», disc returns to neutral at 165ms (state reverse) |
| **Tunnel error** | Disc neutral, status «Не удалось подключиться» in `@color/ping_bad` plus «Нажмите, чтобы повторить» as a 13sp line. The snackbar carries the cause and a «Повторить» action |
| **Subscription expired** | Persistent strip: «Подписка истекла. Продлите её, чтобы подключаться.» with a «Продлить» action. Disc disabled. Подписка row carries a red «Истекла» chip |
| **Offline** | Persistent strip: «Нет сети. Показаны последние данные.» with «Повторить». The disc stays enabled: the app does not know better than the OS. Account-dependent rows are marked stale |
| **Long content** | A 60-character server remark ellipsises at the end on one line. The subscription name wraps to 2 lines in its row |
| **200% font scale** | The disc keeps 176dp, everything below it scrolls |

Replaces: `res/layout/activity_main.xml` lines 58 to 517, `layout_home_account.xml`,
`layout_home_empty.xml`, `layout_subscription_meta_bar.xml`, `Views/HomeView.axaml`,
`Views/CompactHomeView.axaml`, `Views/ConnectHeroView.axaml`, `Views/HomeAccountChip.axaml`,
`Views/OnboardingView.axaml`.

### 4.2 Серверы

**Job:** find a server, know which one is current, act on it, and manage where servers come from.

Content order:

| # | Block | Height | Notes |
|---|---|---|---|
| 1 | Header (3.3): «Серверы» + «Добавить» | 56 | |
| 2 | Search field | 48 | Radius 12 per the shape lock. Filters in place, never navigates. Placeholder «Поиск по серверам». A 20dp clear glyph appears once there is text |
| 3 | Meta line | 24 | «15 серверов · 2 провайдера» (Caption 12/400) left; sort control right |
| 4 | Grouped list | virtualised | One group per provider, plus a «Добавленные вручную» group for servers with no provider |

**Sort control.** A text button showing the current value with a 20dp unfold glyph that **cycles in
place**: «По порядку» > «По задержке» > «По имени» > back. This is the affordance grammar already
documented at `Views/SettingsView.axaml.cs:14`, reused so the product has one vocabulary for
"a value that changes without leaving the screen".

**Provider group header** (sticky, 40dp, `00-rules.md` 4.6 allows sticky headers when the list is
long enough to lose context):
`[16][ 20dp collapse chevron, rotates 0>90 ][8][ provider name, Title 16/700 ][ count, Caption ][ * ][ 40dp kebab ][16]`

The kebab opens a sheet (Android) or a flyout (desktop) with: «Обновить», «Проверить задержку»,
«Переименовать», «Открыть ссылку», «Настройки провайдера», and «Удалить провайдера» in
`@color/ping_bad` at the bottom after a hairline.

**Server row** - the universal row of `00-rules.md` 4.5, with the unified server icon of 10.5:
`[16][ 40dp tile r12 containing the 28dp circular flag, globe glyph fallback ][12][ name Title 16/700 + protocol chip; transport line Subtitle 13/400 ][12][ ping value, Numeric 13/500, right aligned ][16]`

- Tap selects. Selection is two channels: P3 `#20242B` fill **and** a filled check glyph in the
  trailing slot, replacing the ping value. Never a left stripe (banned) and never tint alone.
- Long press (Android) or right click (desktop) opens the **server actions sheet**:
  «Сделать основным», «Проверить задержку», «Изменить», «Дублировать», «Поделиться QR-кодом»,
  «Скопировать ссылку», then a hairline, then «Удалить сервер» in red.
- **This is the P0 regression fix.** `MainActivity.kt:610` still assigns
  `serversAdapter.onItemLongClick`, but `MainRecyclerAdapter.kt:213` binds only
  `setOnClickListener`, so `ServerActionsSheet`, `editServer()`, `shareServer()`, `showQRCode()`
  and `removeServer()` have had no caller in the shipping build. A user currently cannot delete,
  rename, share or edit a single server.
- The zero-size `layout_indicator` `View` that survives only so
  `MainRecyclerAdapter.kt:208` can call `setBackgroundColor` on it is deleted.

**«Добавить» sheet:** «Сканировать QR-код», «Вставить из буфера», «Ввести ссылку», «Создать
вручную». Four rows, one sheet, one entry point for every import path in the product.

Sub-pages (level 1):

| Sub-page | Reached from | Replaces |
|---|---|---|
| **Сервер** (create / edit) | actions sheet > «Изменить»; add sheet > «Создать вручную» | Android `ServerActivity`, `ServerCustomConfigActivity`, `ServerGroupActivity`, `ServerProxyChainActivity` and their 10 layouts; desktop `AddServerWindow` (1 388 ln), `AddServer2Window`, `AddGroupServerWindow` |
| **Провайдер** (edit) | group kebab > «Настройки провайдера» | Android `SubEditActivity`; desktop `SubEditWindow` |
| **Сканер QR** | add sheet > «Сканировать QR-код» | `ScannerActivity` with its empty `RelativeLayout` |

**One server form, not eleven.** The Сервер sub-page is a single screen whose field sections are
driven by the selected protocol (VLESS, VMess, Trojan, Shadowsocks, SOCKS5, Hysteria2, WireGuard,
custom JSON, group, proxy chain). Protocol is the first control; the address and port block, the
TLS block and the transport block are shared includes shown or hidden by protocol. This replaces
`activity_server_*.xml` x10 plus `layout_address_port.xml`, `layout_tls.xml`,
`layout_tls_hysteria2.xml`, `layout_transport.xml` on Android, and the 1 388-line
`AddServerWindow.axaml` plus two siblings on desktop. `JsonEditor.axaml` survives as the control
used by the custom-JSON section.

States: default, first run (no providers), loading (6 skeleton rows), empty (no servers), empty
search («Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск»), error, offline
(cached list plus the strip, refresh and ping disabled), partial (one provider failed to refresh,
marked inline on its group header), one server, 150 servers, 60-character remark.

### 4.3 Аккаунт

**Job, signed in:** show what has been paid for and what is left of it. **Job, signed out:** get
signed in with one tap.

#### 4.3.1 Signed out - the sign-in gate

This is owner request `00-rules.md` 0.4.10 ("the sign-in screen redesigned from scratch,
minimalist") and it is a **state of the Аккаунт tab**, not a pushed screen. There is no
`LoginActivity` and no `LoginView` any more.

```
[ header 56: «Аккаунт» ]
[ 32 ]
«Войти в аккаунт»                                   Headline 24/700, at the gutter
«Подписка, устройства и платежи хранятся в аккаунте» Body 14/400, onSurfaceVariant, max 60ch
[ 24 ]
[  Войти через Telegram  ]                          52dp pill, accent fill, full width - THE one lit element
[ 12 ]
  Войти по почте                                    48dp text button, opens the sub-page
[ 12 ]
  Создать аккаунт                                   48dp text button, opens departament.site
[ error line, Caption 12/400, @color/ping_bad, present in the markup even when empty ]
```

No card. No illustration. No shield tile: `03-direction.md` F17 forbids shields beyond the one
connect object. No wordmark competing with the heading. Ground plane edge to edge.

Awaiting state, inline, replacing the CTA without changing its height:
a 20dp indeterminate indicator, «Ждём подтверждения в Telegram», then two text buttons
«Открыть Telegram» and «Начать заново».

Other states: submitting, error (inline, with the cause and the fix per `00-rules.md` 9.4), offline
(«Нет подключения к интернету. Проверьте сеть и повторите.» with the CTA disabled), and locked out.

**Sub-page «Вход по почте»** carries the entire rest of the auth surface, which today is 20 buttons
and 5 fields on one desktop column (`Views/LoginView.axaml`, 954 lines plus 1 377 lines of
code-behind) and two stacked cards on Android (`res/layout/activity_login.xml`):

- A 2-item segmented control: «Пароль» | «Код из письма»
- Почта field, label above, `inputType="textEmailAddress"`, autofill hint
- Пароль field with a show/hide toggle, or the 6-cell code field
- One 52dp accent CTA «Войти»
- Text buttons: «Забыли пароль?», «Отправить ссылку для входа»
- A hairline, then «Другой способ входа» as a disclosure row opening a sheet with «Через сайт» and
  «Через Google» (disabled, «Скоро»)

That is the answer to `02-inventory-pc.md` question 3: **the disclosure component is a sub-page for
the second method and a sheet for the remaining four**, not a flyout and not six blocks stacked on
one scroll.

#### 4.3.2 Signed in

| # | Block | Notes |
|---|---|---|
| 1 | Header 56: «Аккаунт» | |
| 2 | Identity block, on the ground plane, **not a card** | 48dp avatar, name or `@handle` (Headline 24/700), tariff caption. Then the balance: Display 34/700 Numeric with a muted ₽, and a «Пополнить» tonal button |
| 3 | Referral row | 56, the code as a value, tap copies, snackbar «Код скопирован» |
| 4 | **The one card:** the subscription | Paged. Name, tariff badge chip, «Действует до 14 августа», traffic meter with the label **above** the bar and the value to its right (never printed on the fill, `03-direction.md` F11), device gauge «2 / 5», one accent «Продлить» CTA, auto-renew toggle with the next-charge line. Dots below at 2+ subscriptions |
| 5 | Group «Управление» | «Купить подписку» ›, «Устройства» › with value «2 / 5», «История платежей» › with value «14 июля» |
| 6 | Group «Вход» | «Telegram» with a chip («Привязан @user» or «Не привязан» plus a «Привязать» action), «Почта» with the address as its value, then «Выйти» as quiet red text |

Sub-pages (level 1): **Покупка**, **Устройства**, **История платежей**, **Вход по почте**,
**Продление** and **Добавить устройства**.

The last two are new placements. Desktop currently runs the upgrade and add-device flows as a
**four-panel flyout stack inside a card inside a horizontal carousel inside a scroll**
(`Views/AccountView.axaml:484` onward). That is four levels of containment for a purchase. They
become sub-pages, which is one level, and which is where a flow that takes money belongs.

States: signed out (4.3.1), skeleton, one subscription, several subscriptions, no subscription
(«Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» / «Купить»),
expired, trial, error with retry, offline with stale marking, payment pending, 12-digit balance,
32-character Telegram handle.

`Subscription.isTrial` comes from the backend and is never inferred from tariff name or squad. In
this deployment the trial squad is the same Remnawave group as the paid base tariff, so any
squad-based detection misclassifies real paying customers. Trial subscriptions hide the renew and
add-device actions and show «Купить тариф» instead.

### 4.4 Настройки

Full model in section 9. At the destination level it is: a header, a search field, four named row
groups, and an unnamed footer pair.

---

## 5. Launch flow

### 5.1 The rule

**Cold start always lands on Главная.** No splash beyond the platform default, no onboarding gate,
no restored tab. The four-second task wins. A deep link overrides this and opens its destination
directly (`00-rules.md` 7.7).

**Warm resume** from recents or from the tray restores the last tab, its scroll offset, its search
query and its expanded groups.

**Onboarding is not a surface.** `Views/OnboardingView.axaml` (a full-bleed gate shown while
`HomeViewModel.IsEmpty`) and `res/layout/layout_home_empty.xml` (the card that is the real sign-in
screen for 100% of Android users today) are both deleted. `03-direction.md` 10.2 is explicit:
*"First-run is a state of Home, not a different Home."* `reference/onboard.md` agrees: empty states
are the onboarding, and users must never be blocked from the product.

### 5.2 Cold start decision tree

```
launch
 └─ render Главная from cached state immediately (no network wait, no gate)
     ├─ deep link present?              → open that destination (section 7), Главная stays beneath
     ├─ no account AND no servers       → variant A
     ├─ account, no subscription        → variant B
     ├─ account, subscription active    → variant C
     ├─ account, subscription expired   → variant D
     └─ no network at first frame       → variant E, layered on whichever of A to D applies
```

### 5.3 The five named variants

| Variant | Condition | Главная shows | Nav bar | Which tab can the user reach |
|---|---|---|---|---|
| **A - never signed in** | no `AccountSession`, zero servers | account row «Войти»; disc disabled; CTA «Добавить провайдера»; text button «Войти в аккаунт» | all four items, always | all four. Серверы shows its empty state, Аккаунт shows the gate |
| **B - signed in, no subscription** | session valid, zero active subscriptions | account row with the handle; disc disabled; CTA «Купить подписку», subtitle «Осталось выбрать тариф» | all four | Аккаунт shows the empty subscription state with the same CTA |
| **C - signed in, active** | session valid, subscription valid, servers present | the full Главная of 4.1, disconnected and ready | all four | everything |
| **D - signed in, expired** | session valid, `expireAt` in the past | persistent strip «Подписка истекла. Продлите её, чтобы подключаться.» + «Продлить»; disc disabled; Подписка row with a red «Истекла» chip | all four | the same strip appears on Серверы and Аккаунт. Connecting is blocked, browsing is not |
| **E - offline** | no network at first frame | last known everything, plus the strip «Нет сети. Показаны последние данные.» + «Повторить» | all four | network-dependent actions disabled with the reason on the disabled control's tooltip or helper line. The connect disc stays enabled |

**A sixth case that is not a variant: no servers but an active subscription.** The account has
paid, the provider list has not synced yet. Главная shows the disc disabled and one CTA «Загрузить
серверы» which runs the sync inline. On failure it becomes «Повторить» with the reason underneath.
This is the case that today silently produces an empty app after a successful purchase.

### 5.4 What is never shown at launch

- A tutorial, a carousel of value propositions, or a "welcome" heading. The current
  `tv_home_welcome` («Приветствуем!») is deleted.
- A modal asking for anything. The VPN permission prompt is raised by the first connect attempt,
  not by launch.
- A blocking sign-in wall. The app works without an account: QR and clipboard import are first
  class, and both live in Серверы > Добавить.
- A spinner over a blank screen. Any wait over 400ms is a skeleton in the shape of the result
  (`03-direction.md` 8.4).

### 5.5 Time budget

First frame under 1s on a mid-range device (`00-rules.md` 11.5). Главная renders from cache before
any network call resolves. The account, subscription and ping data arrive afterwards and fade in
over `motion_state` 220ms into slots whose height is already reserved.

### 5.6 Post-sign-in hand-off

The one place in the product where a gate is correct, because a real import is running and an empty
Главная flashing for two seconds is a worse lie.

1. Sign-in succeeds on the Аккаунт gate.
2. A full-bleed overlay covers the shell: a 64dp ring (static `Brush.OutlineVariant` track plus a
   spinning `Brush.Accent` arc), «Добавляем аккаунт», and a live stage line
   («Загружаем подписку», «Загружаем серверы»).
3. On success the arc stops, and the shell cross-fades to **Главная** at `Dur.Slow` 450ms
   `Ease.OutExpo`. This is the single 450ms hand-off the token scale reserves
   (`00-rules.md` 3.7) and the only entrance motion in the product.
4. On failure the overlay cross-fades in place to an error column with two exits: «Повторить» and
   «Войти заново».

Desktop has this already and it is correct (`Views/AccountSyncView.axaml`, KEEP per
`02-inventory-pc.md` 4.4). **Android has nothing equivalent and gets it**: a `#sync_overlay` view in
`activity_main.xml` with the same three states and the same copy. Logged as parity gap PG-9 until
it ships.

---

## 6. Back navigation

### 6.1 Android

Rules, in the order they are consulted:

1. An open bottom sheet or dialog closes.
2. An open search with text in it clears the text and keeps focus; a second Back closes the search.
3. An expanded inline panel collapses.
4. If the current tab is not Главная, go to Главная.
5. On Главная, the system default runs: the task finishes with the predictive Back animation.

Required changes:

- **Add `android:enableOnBackInvokedCallback="true"`** to `<application>` in
  `AndroidManifest.xml`. It is declared nowhere today, so predictive Back is not supported at all.
- **Delete the `onKeyDown` handler at `MainActivity.kt:2298`.** It intercepts `KEYCODE_BACK` and
  `KEYCODE_BUTTON_B`, calls `moveTaskToBack(false)` and returns `true` unconditionally, so the app
  never finishes on Back. Combined with `launchMode="singleTask"` this makes the Android 14
  predictive animation show the app closing and then not closing.
- Keep the `OnBackPressedCallback` at `:250` but let it disable itself on Главная so the system can
  finish the task.
- Every sub-page Activity declares `android:parentActivityName=".ui.MainActivity"`. Up is Back.
- Back restores: the tab, the scroll offset, the search query, the sort value, expanded provider
  groups, and the selected subscription page.

### 6.2 Desktop

| Input | Effect |
|---|---|
| `Esc` | closes the topmost flyout or sheet; if none, pops one sub-page; on a tab root it does nothing (it never closes the window) |
| `Alt` + `Left` | pops one sub-page |
| Mouse button 4 (`XButton1`) | pops one sub-page |
| The `←` in the sub-toolbar | pops one sub-page |
| `Ctrl` + `1..4` | switches to Главная / Серверы / Аккаунт / Настройки |
| `Ctrl` + `F` | focuses the search field on Серверы and Настройки |
| `Ctrl` + `,` | opens Настройки |
| `Ctrl` + `+` / `-` / `0` | UI zoom, existing, keep |
| Window close | hides to tray, existing, keep |

Today `Key.Escape` is handled in exactly four local modals (`DevicesView.axaml.cs:47`,
`BuyView.axaml.cs:167`, `SubSettingWindow.axaml.cs:100`, `ProfilesView.axaml.cs:309`) and
**nothing pops the sub-page stack**, so the only exit from Buy, Devices, History or any settings
sub-page is a 40px target in the top-left corner.

**The sub-page stack becomes per-tab.** `_subStack` (`Views/MainWindow.axaml.cs:74`) is one global
`List<Control>`, so opening Аккаунт > Покупка and then clicking «Настройки» in the rail leaves
Покупка sitting on top of Настройки. It becomes `Dictionary<Tab, List<Control>>`: switching tabs
hides the outgoing tab's stack and shows the incoming tab's stack, or its root if the stack is
empty. Returning to a tab returns to exactly where the user left it.

### 6.3 Both platforms

- Back never traps the user and never loses entered data without asking.
- A sheet or dialog returns focus to the control that opened it.
- Navigating away from a form with unsaved changes asks once, with the buttons «Не сохранять» and
  «Сохранить», never «OK» / «Отмена».

---

## 7. Routes and deep links

### 7.1 Route identity

Every destination and every sub-page has a stable string identity, identical on both platforms.
This is what makes deep links, session restore and the URL schemes page possible; desktop today has
no route vocabulary at all (`02-inventory-pc.md` 2.3).

| Route | Screen | Level |
|---|---|---|
| `home` | Главная | tab |
| `servers` | Серверы | tab |
| `servers/server/{guid}` | Сервер, edit | sub-page |
| `servers/server/new` | Сервер, create | sub-page |
| `servers/provider/{id}` | Провайдер | sub-page |
| `servers/scan` | Сканер QR | sub-page |
| `account` | Аккаунт | tab |
| `account/signin` | Вход по почте | sub-page |
| `account/buy` | Покупка | sub-page |
| `account/devices` | Устройства | sub-page |
| `account/history` | История платежей | sub-page |
| `account/renew/{uuid}` | Продление | sub-page |
| `account/devices-add/{uuid}` | Добавить устройства | sub-page |
| `settings` | Настройки | tab |
| `settings/perapp` | Прокси по приложениям | sub-page |
| `settings/routing` | Маршрутизация | sub-page |
| `settings/routing/rule/{id}` | Правило маршрутизации | detail |
| `settings/dns` | DNS | sub-page |
| `settings/advanced` | Дополнительно | sub-page |
| `settings/advanced/localproxy` | Локальный прокси | detail |
| `settings/providers` | Провайдеры | sub-page |
| `settings/latency` | Проверка задержки | sub-page |
| `settings/assets` | Файлы ресурсов | sub-page |
| `settings/data` | Данные и резервные копии | sub-page |
| `settings/data/webdav` | WebDAV | detail |
| `settings/hotkeys` | Горячие клавиши (desktop only) | sub-page |
| `settings/about` | О приложении | sub-page |
| `settings/about/log` | Журнал | detail |
| `settings/about/urlschemes` | Схемы URL-адресов | detail |
| `settings/tv` | Перенести на ТВ (Android only) | sub-page |

Opening a `sub-page` route from cold start puts its parent tab underneath it, so Back or Esc lands
somewhere sensible rather than on an empty shell.

### 7.2 The `depv://` scheme

One scheme, two families, identical vocabulary on both platforms. Registered on Android by
`UrlSchemeActivity` (`AndroidManifest.xml`, `<data android:scheme="depv" />`) and on desktop by
`Views/UrlSchemesPage.axaml.cs`.

**Actions** (existing, kept, all confirmed against `ui/UrlSchemeActivity.kt:73` onward):

| URL | Effect | Confirmation |
|---|---|---|
| `depv://connect` | start the tunnel | none needed |
| `depv://disconnect` | stop the tunnel | none needed |
| `depv://toggle` | stop if running, otherwise start | none needed |
| `depv://open` | legacy alias for `connect` | kept for existing automations, **not advertised** on the URL schemes page |
| `depv://close` | legacy alias for `disconnect` | same |
| `depv://import/{base64}` | batch import of a config payload | **confirm sheet, mandatory** |
| `depv://add/{url}` | import a provider or config by URL | **confirm sheet, mandatory** |
| `depv://routing/add/{base64}` | import routing rulesets | **confirm sheet, mandatory** |
| `depv://routing/onadd/{base64}` | import routing rulesets and restart if running | **confirm sheet, mandatory** |

**The confirm sheet is a new, required surface.** Today `depv://import/{base64}` silently mutates
the user's server list and shows a Toast afterwards. A link in a chat message must not be able to
rewrite what the user connects through. The sheet shows: what will be imported («2 сервера,
1 провайдер»), where it came from (the host, if the payload is a URL), «Импортировать» as the
accent action and «Отмена» as the text action. It is the same sheet on both platforms.

**Destinations** (new):

| URL | Opens |
|---|---|
| `depv://home` | Главная |
| `depv://servers` | Серверы |
| `depv://account` | Аккаунт |
| `depv://account/buy` | Аккаунт > Покупка |
| `depv://account/devices` | Аккаунт > Устройства |
| `depv://account/history` | Аккаунт > История платежей |
| `depv://subscription/{uuid}` | Аккаунт, scrolled to that subscription card |
| `depv://settings` | Настройки |
| `depv://settings/{group}` | Настройки, scrolled to that group, `{group}` in `connection`, `bypass`, `subscriptions`, `app` |
| `depv://link/{token}` | consumes a Telegram-link callback and lands on Аккаунт |

`depv://link/{token}` closes a real product gap: the Telegram-link flow returns the user to the app
today with no route to hand the token to.

Legacy `v2rayng://install-config?url=…` and `v2rayng://install-sub?url=…` keep working and route
through the same confirm sheet.

### 7.3 Other entry points

| Entry | Platform | Lands on | Change |
|---|---|---|---|
| Launcher icon | Android | Главная | |
| `ACTION_SEND` text/plain | Android | the confirm sheet, then Серверы | already declared, gains the sheet |
| App shortcuts (`res/xml/shortcuts.xml`) | Android | 3 shortcuts: «Переключить» (`ScSwitchActivity`), «Сканировать QR» (`ScScannerActivity`), «Серверы» (`depv://servers`) | today there are 4, and «Запустить» and «Остановить» are dropped: a long-press menu offering both when only one applies is a dead affordance. The toggle covers both |
| QS tile | Android | toggles the tunnel; long press opens Главная | keep |
| Home-screen widget | Android | toggles the tunnel | keep, restyle is out of IA scope |
| Foreground notification | Android | tap opens Главная. Two actions: «Отключить» and «Сменить сервер» (`depv://servers`) | «Сменить сервер» is new |
| Tray menu | desktop | «Перезапустить», «Подключить»/«Отключить», «Показать», «Выход» | keep exactly as it is, `App.axaml:26` |
| Tasker | Android | headless | keep, give `TaskerActivity` a real title |
| TV receive | Android TV | `TvReceiveActivity`, landscape, LEANBACK only | keep |

---

## 8. The feedback channel

`02-inventory-pc.md` question 5 asks what replaces toasts, because desktop currently has **no
user-visible feedback surface at all**: `snackHost` (`Views/MainWindow.axaml:623`) is permanently
`IsVisible=False`, `DelegateSnackMsg` (`.axaml.cs:1765`) forwards to `MsgViewModel`, and `MsgView`
is never mounted. Clipboard-import failures, subscription-refresh results and engine errors go
nowhere.

The answer is **two mechanisms with a clear division, plus a record**, on both platforms.

### 8.1 Transient: one message at a time

| | Android | Desktop |
|---|---|---|
| Component | `Snackbar`, anchored above the bottom navigation | `Border.Toast`, bottom centre, 24 above the window edge |
| Duration | 4s, or 6s when it carries an action | same |
| Actions | at most one, a verb: «Повторить», «Отменить», «Открыть» | same |
| Queue | one visible at a time, newest replaces oldest | same |
| Dismiss | swipe, or the action | click anywhere on it |

Used for: «Скопировано», «Подписка обновлена», «Сервер удалён» with «Отменить», «Не удалось
обновить подписку» with «Повторить».

**`res/layout/toast_status.xml` is deleted.** It is a deprecated custom-view `Toast` at a magic
110dp bottom offset, which Android 12+ does not render for backgrounded apps and which collides
with the navigation bar. The app currently contains about 40 `Toast` calls and zero `Snackbar`
calls; `00-rules.md` 1.4.8 forbids `Toast` for anything actionable.

### 8.2 Persistent: the status strip

For a condition, not an event. A 40dp bar at the top of the affected tab's scroll content, below
the header, on `?attr/colorSurfaceContainerHigh` / `Brush.SurfaceHigh`, with a 20dp leading glyph,
one line of text and one text action on the right.

| Condition | Text | Action | Appears on |
|---|---|---|---|
| Offline | «Нет сети. Показаны последние данные.» | «Повторить» | Главная, Серверы, Аккаунт |
| Subscription expired | «Подписка истекла. Продлите её, чтобы подключаться.» | «Продлить» | Главная, Серверы, Аккаунт |
| Expiring in under 3 days | «Подписка заканчивается 14 августа.» | «Продлить» | Главная |
| Device limit reached | «Достигнут лимит устройств.» | «Устройства» | Главная, Аккаунт |
| Provider refresh failed | «Не удалось обновить провайдера.» | «Повторить» | Серверы |
| Core not running / TUN unavailable | the concrete cause | «Как исправить» | Главная |

At most one strip at a time; the table order is the priority order. The strip is never a colour
wash and never carries the accent as a background: `00-rules.md` 6.3 requires a glyph plus a word,
which it has.

### 8.3 The record: Журнал

`settings/about/log`. A virtualised, monospaced, timestamped list with a level filter
(«Все» / «Ошибки»), a «Скопировать» action and a «Очистить» action. It is where an error the user
missed still lives.

This gives desktop `MsgView.axaml` a real home (rebuilt as `LogPage.axaml`) and gives Android
`LogcatActivity` an entry point for the first time. It also removes the excuse for the two
"screenshot the raw server response" diagnostic dialogs currently shipped to end users from
`DeviceManagementActivity` and the payment flow: those dialogs are deleted, and the raw body goes
to the log.

---

## 9. The Настройки information architecture

The owner's demand is explicit: *"проработать все настройки что на андроид версии что на пк версии,
чтобы все вкладки в настройках были проработаны под общий стиль и дизайн, а не абы как."*

### 9.1 Constraints being satisfied

- `03-direction.md` 7.3: maximum 7 rows per group, maximum 4 groups per screen, maximum 2 levels
  below a tab.
- `00-rules.md` 4.2: groups 24 apart with a sentence-case bold header 24 above and 8 below, rows
  0 apart with a 1dp hairline inset to 68.
- Every setting that exists in the engine has exactly one home. Today Android hides about 30
  settings in `res/xml/pref_settings.xml`, which is loaded only by `SettingsActivity`, which
  nothing launches; desktop hides about 10 in `OptionSettingWindow`, which nothing opens.
- No setting has two homes. Today «Автообновление подписки» exists both in the Android settings tab
  and in `ProviderSettingsActivity`, two taps apart, in two different visual languages, writing the
  same `SubscriptionItem.autoUpdate` field.

### 9.2 The hub

```
[ header 56 : «Настройки» ]
[ search field 48 : «Поиск по настройкам» ]      ← filters rows in place across all groups and sub-pages
[ 24 ]
Подключение                                       ← section header, 16sp/700, sentence case
  Режим подключения          [ segment: VPN · Прокси · Вместе ]
  Прокси по приложениям      ›
  Маршрутизация              ›
  DNS                        ›
  Обход локальной сети       [ toggle ]
  IPv6                       [ toggle ]
  Дополнительно              ›
[ 24 ]
Обход блокировок
  Mux                        [ toggle ]
  Число соединений           [ value, cycles ]      ← visible only while Mux is on
  Фрагментация               [ toggle ]
[ 24 ]
Подписки
  Автообновление подписок    [ value: каждый час · 6 часов · сутки · выключено ]
  Обновлять при запуске      [ toggle ]
  Проверка задержки          ›
  Провайдеры                 ›
  Файлы ресурсов             ›
[ 24 ]
Приложение
  Оформление                 [ segment: Тёмная · Светлая · Системная ]
  Чёрная тема                [ toggle ]
  Язык                       [ value, cycles ]
  Меньше движения            [ toggle ]
  Запуск при старте          [ toggle ]
  Масштаб интерфейса         [ value, cycles ]      ← desktop only
  Горячие клавиши            ›                      ← desktop only
[ 24 ]
  Данные и резервные копии   ›                      ← no section header: the footer pair
  О приложении               ›
[ 32 ]
```

**4 named groups. 22 rows on Android, 24 on desktop. Every group is 7 rows or fewer.**

The two footer rows have no section header, which is what makes them structurally not a group: a
group is defined by having one. Settings screens universally place "about" and "data" at the bottom
without a header, and putting «Резервное копирование» under «О приложении» would be a lie about
what backup is.

### 9.3 Affordance grammar

Kept verbatim from `Views/SettingsView.axaml.cs:14`, which is the single best design decision in
either codebase, and now applied on Android too:

| Trailing control | Contract |
|---|---|
| 22dp chevron `›` | navigates to a sub-page |
| chevron that rotates 0 to 90 | expands an inline panel on this screen |
| 20dp unfold glyph plus a value | the value cycles in place, no navigation |
| segmented control | 2 or 3 mutually exclusive options, changes in place |
| switch | boolean, changes in place |

Never two trailing controls on one row (`00-rules.md` 4.5). The whole row is the target; a switch
row is toggled by the row or by the switch.

**This kills six single-choice `AlertDialog`s on Android** (Режим, Пинг, DNS, Оформление, Язык,
Автообновление), which is `03-direction.md` F13 and the `product.md` "modal as first thought" ban.
Two of them also carry hardcoded Russian error strings in Kotlin at `MainActivity.kt:2016` and
`:2018`.

### 9.4 Sub-pages

| Route | Title | Contains | Absorbs |
|---|---|---|---|
| `settings/perapp` | Прокси по приложениям | mode toggle, search, app list with checkboxes, «Выбрать всё» / «Инвертировать» / «Импорт» / «Экспорт» in a 40dp overflow | Android `PerAppProxyActivity` + `AppPickerActivity`; desktop `PerAppProxyPage` |
| `settings/routing` | Маршрутизация | domain strategy, rule sets list, add rule, import from clipboard / QR / predefined, export | Android `RoutingSettingActivity`; desktop `RoutingSubView` + `RoutingRuleSettingWindow` |
| `settings/routing/rule/{id}` | Правило | name, outbound, domains, IPs, ports, protocol, enabled | Android `RoutingEditActivity`; desktop `RoutingRuleDetailsWindow` |
| `settings/dns` | DNS | preset chips (Cloudflare, Google, AdGuard, FakeIP, По умолчанию, Свой), custom address with validation, remote and domestic DNS, DNS hosts | Android DNS dialog + the hidden `pref_settings` DNS keys; desktop `DnsSubView` |
| `settings/advanced` | Дополнительно | ядро (desktop), уровень логов, sniffing, allow-insecure, разрешение доменов, MTU, адрес интерфейса, «Локальный прокси» › | most of the hidden `pref_settings.xml`; desktop `OptionSettingWindow` |
| `settings/advanced/localproxy` | Локальный прокси | enabled, порт, SOCKS5 логин и пароль, HTTP-авторизация, блокировать UDP, доступ из локальной сети | Android `LocalProxyActivity`, cut from 1 035 lines and 5 sections to about 8 rows |
| `settings/providers` | Провайдеры | User-Agent, отправлять HWID, порядок серверов, уведомлять об обновлении | Android `ProviderSettingsActivity`; desktop `ProviderSettingsPage`, which is fully built and referenced by nothing |
| `settings/latency` | Проверка задержки | метод (TCP / реальная задержка), адрес проверки, таймаут, параллельность, проверять при запуске, сортировать после проверки | Android «Пинг» dialog; desktop `PingSettingsPage` |
| `settings/assets` | Файлы ресурсов | geoip.dat, geosite.dat, custom sources, «Обновить сейчас» with progress and error | Android `UserAssetActivity` + `UserAssetUrlActivity`; desktop `GeoFilesPage` |
| `settings/data` | Данные и резервные копии | «Создать резервную копию», «Восстановить», «Поделиться», WebDAV ›, «Перенести на ТВ» › (Android), «Сбросить настройки» in red | Android `BackupActivity` + `dialog_webdav.xml`; desktop `BackupPage` |
| `settings/hotkeys` | Горячие клавиши | the four global hotkeys with capture fields | desktop `GlobalHotkeySettingWindow` |
| `settings/about` | О приложении | wordmark, версия (Numeric), «Проверить обновления» (desktop), сайт, Telegram-бот, «Схемы URL-адресов» ›, «Журнал» ›, лицензии, политика конфиденциальности | Android `AboutActivity` (pure 2018 upstream), `CheckUpdateActivity`, `UrlSchemeListActivity`, `LogcatActivity`; desktop `AboutPage`, `CheckUpdateView`, `UrlSchemesPage`, `MsgView` |
| `settings/about/urlschemes` | Схемы URL-адресов | one row per scheme: label, the `depv://…` string in the figure face, a copy button. Register and unregister on desktop | Android `activity_url_scheme_list.xml` cut from 634 lines and 5 section cards to one list |
| `settings/about/log` | Журнал | section 8.3 | |
| `settings/tv` | Перенести на ТВ | instructions, scan, subscription picker | Android `TvSendActivity` |

**Fifteen sub-pages, every one of them at level 1 or 2, every one of them reachable, none of them
duplicating another.** The Android settings surface goes from 20 hand-copied rows in a 1 536-line
layout plus 8 parallel activities in three visual languages, to one data-driven list plus 14
sub-pages built from two row components that already exist and are currently orphaned
(`res/layout/layout_setting_row.xml`, `layout_setting_toggle_row.xml`).

### 9.5 Settings search

New on both platforms. It filters row titles, subtitles and sub-page contents, showing matches as a
flat list with a breadcrumb caption («Подключение › Дополнительно») under each hit. With about 60
settings across 15 sub-pages, search is what stops the hierarchy from becoming a maze. The empty
result is a designed state: «Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск».

---

## 10. Placement map: every existing screen

Verdicts: **KEEP** ships with token cleanup only. **RESTYLE** structure is right, surface is
rebuilt. **REBUILD** start from the spec. **MERGE** its content moves into another surface.
**DELETE** it stops existing.

### 10.1 Android

Numbering follows `01-inventory-android.md` section 4.

| # | Today | Verdict | New home |
|---|---|---|---|
| 1 | Home tab, `activity_main.xml` 58 to 517 | REBUILD | **Главная** (4.1) |
| 2 | Home empty / sign-in card, `layout_home_empty.xml` | DELETE | sign-in becomes the Аккаунт gate (4.3.1); empty becomes a state of Главная |
| 3 | Subscription meta bar, `layout_subscription_meta_bar.xml` | MERGE + DELETE | traffic, expiry and tariff go to the Аккаунт subscription card; ping, refresh, pin, delete and collapse go to the Серверы provider group header. `HomeMetaPagerAdapter.kt` and `measureHomeMetaHeight()` deleted with it |
| 4 | Servers tab | RESTYLE | **Серверы** (4.2) |
| 5 | Server row, `item_recycler_main.xml` | RESTYLE | Серверы. Flag asset replaces the emoji, the 4.0:1 protocol chip is fixed, `layout_indicator` is deleted, long press is rewired |
| 6 | Settings tab, `layout_settings_content.xml` (1 536 ln) | REBUILD | **Настройки** (9.2), data driven |
| 7 | Account tab, `AccountFragment` + `activity_account.xml` | RESTYLE | **Аккаунт** (4.3.2). It also absorbs the sign-in gate |
| 8 | Login, `LoginActivity` + `activity_login.xml` | REBUILD | split: gate on the Аккаунт tab, email form at `account/signin`. `EXTRA_MODE` and its three shapes are deleted |
| 9 | Buy, `BuyTariffActivity` | RESTYLE | `account/buy` |
| 10 | Devices, `DeviceManagementActivity` | KEEP | `account/devices`. The 44dp delete target goes to 48; the raw-response diagnostic dialog is deleted in favour of the log |
| 11 | Payment history, `PaymentHistoryActivity` | RESTYLE | `account/history`. The dead `btn_history_buy` is wired or deleted |
| 12 | Local proxy, `LocalProxyActivity` (1 035 ln) | REBUILD + CUT | `settings/advanced/localproxy`, about 8 rows. The memory-limit chips are deleted with `card_memory`; the domain-routing section merges into `settings/routing` |
| 13 | Provider settings, `ProviderSettingsActivity` (648 ln) | MERGE + DELETE | `settings/providers` plus the Серверы group kebab |
| 14 | URL schemes, `UrlSchemeListActivity` (634 ln) | REBUILD | `settings/about/urlschemes`, one list |
| 15 | Backup, `BackupActivity` | REBUILD | `settings/data`. `dialog_webdav.xml` becomes a proper labelled form at `settings/data/webdav` |
| 16 | Routing list, `RoutingSettingActivity` | RESTYLE | `settings/routing`. The five overflow actions become visible rows or one designed overflow |
| 17 | Routing rule editor, `RoutingEditActivity` | REBUILD | `settings/routing/rule/{id}` |
| 18 | Assets, `UserAssetActivity` | RESTYLE | `settings/assets` |
| 18b | Add asset URL, `UserAssetUrlActivity` | DELETE | becomes a sheet on `settings/assets` |
| 19 | Per-app proxy, `PerAppProxyActivity` | RESTYLE | `settings/perapp` |
| 20 | App picker, `AppPickerActivity` (10-line bare `RecyclerView`) | DELETE | merged into `settings/perapp` as a picker sheet |
| 21 | TV send, `TvSendActivity` | RESTYLE | `settings/tv` |
| 22 | TV receive, `TvReceiveActivity` | KEEP | unchanged, LEANBACK only, tokenised |
| 23 | About, `AboutActivity` | REBUILD | `settings/about` |
| 24 | Scanner, `ScannerActivity` | REBUILD | `servers/scan`, with a framing overlay, an instruction line, a torch and a «Выбрать фото» action |
| 25 | Server editors x10 plus 4 includes | REBUILD as one | `servers/server/{guid}` |
| 26 | Sub setting, `SubSettingActivity` + `item_recycler_sub_setting.xml` | DELETE | the provider list is the Серверы group headers |
| 26b | Sub edit, `SubEditActivity` | REBUILD | `servers/provider/{id}` |
| 27 | Legacy `SettingsActivity` + `res/xml/pref_settings.xml` | DELETE | its 30 hidden settings are triaged into `settings/advanced`, `settings/dns`, `settings/latency`, `settings/providers`; the rest are dropped |
| 28 | Check update, `CheckUpdateActivity` | DELETE | Android is not distributed via GitHub releases. Parity gap PG-1 |
| 29 | Logcat, `LogcatActivity` | RESTYLE | `settings/about/log`, reachable for the first time |
| 30 | Tasker, `TaskerActivity` | KEEP | give it a real title instead of `""` |
| 31 | Deep-link handler and shortcut stubs | KEEP + HARDEN | the confirm sheet of 7.2 |
| 32 | Server actions sheet, `ServerActionsSheet` | REBUILD + REWIRE | Серверы. This is the P0 regression |
| 33 | Payment method sheet | KEEP | |
| 34 | Status toast, `toast_status.xml` | DELETE | replaced by section 8 |
| 35 | 18 dialogs | RESTYLE + CUT | six single-choice pickers become inline controls or sub-pages; the two debug diagnostic dialogs are deleted |
| 36 | Widget, QS tile, notification | RESTYLE | out of IA scope except the new «Сменить сервер» notification action |

### 10.2 Desktop

| Today | Verdict | New home |
|---|---|---|
| `MainWindow` chrome | RESTYLE | section 3.2. Caption targets to 40x32, chrome styles move out of the window into `Assets/`, compact machinery deleted |
| Nav rail | KEEP + EXTEND | gains «Серверы» at index 1; the collapse toggle is deleted |
| `BottomNavBar.axaml` | DELETE | compact mode is gone |
| `HomeView.axaml` | REBUILD | **Главная**, single column, max width 720 |
| `CompactHomeView.axaml` | DELETE | one Home |
| `ConnectHeroView.axaml` (839 + 1 156 ln) | RESTYLE | Главная. Keep the state machine, the wind-up arc and the reduced-motion gating. Delete `#GlowHalo`, `#AmbientSonar`, `#AmbientRing`, `#SonarPulseEcho`, `#RingHoverGlow`, the `↑` and `↓` text arrows and `#CornerAddButton` |
| `ServerListView.axaml` | RESTYLE | **Серверы**, plus search harvested from `CompactServersView.axaml:90` |
| `SubscriptionMetaView.axaml` | SPLIT | provider header in Серверы; subscription readout in Аккаунт. The local 34x34 icon-button override is deleted and the shared 40 is used |
| `HomeAccountChip.axaml` | KEEP | Главная's header slot. `FontSize="18"` goes to the ramp |
| `ServersView.axaml` (12 ln orphan) | DELETE and re-author | the path is reused for the new Серверы tab |
| `CompactServersView.axaml` | HARVEST then DELETE | its search field is the only one in the app |
| `SettingsView.axaml` | RESTYLE | **Настройки** (9.2). Add `MaxWidth=720`, add search, reorder to four groups |
| `PerAppProxyPage`, `DnsSubView`, `PingSettingsPage`, `RoutingSubView`, `GeoFilesPage`, `AboutPage`, `BackupPage`, `UrlSchemesPage` | RESTYLE | their routes in 9.4; all switch to the shared `BackNav` |
| `ProviderSettingsPage.axaml` | WIRE | `settings/providers`. Built, styled, referenced by nothing today |
| `AccountView.axaml` (1 474 ln) | RESTYLE | **Аккаунт** (4.3.2). The 4-panel flyout stack becomes the `account/renew` and `account/devices-add` sub-pages |
| `BuyView.axaml` | KEEP | `account/buy`. Closest thing to a finished 2026 screen |
| `DevicesView.axaml` | KEEP | `account/devices` |
| `PaymentHistoryView.axaml` | KEEP | `account/history` |
| `LoginView.axaml` (954 + 1 377 ln) | DELETE | gate on the Аккаунт tab plus `account/signin` |
| `OnboardingView.axaml` | DELETE | first run is a state of Главная |
| `AccountSyncView.axaml` | KEEP | the post-sign-in overlay of 5.6 |
| `MessageBoxDialog.axaml` | KEEP | |
| `QrcodeView.axaml` | REBUILD | a proper dialog with a card, a title and a copy action |
| `SudoPasswordInputView.axaml` | REBUILD | Linux users meet it on first TUN start |
| `MsgView.axaml` | REBUILD | `settings/about/log` |
| `AddServerWindow` (1 388 ln), `AddServer2Window`, `AddGroupServerWindow` | REBUILD as one | `servers/server/{guid}` |
| `SubEditWindow` | REBUILD | `servers/provider/{id}` |
| `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow` | REBUILD | `settings/routing` and `settings/routing/rule/{id}` |
| `ProfilesSelectWindow` | REBUILD | a picker sheet, called from 3 places |
| `OptionSettingWindow` (1 206 ln) | MIGRATE then DELETE | its unique controls go to `settings/advanced` |
| `SubSettingWindow` | MIGRATE then DELETE | the provider list is the Серверы group headers |
| `FullConfigTemplateWindow` | MIGRATE then DELETE | into `settings/advanced` |
| `GlobalHotkeySettingWindow` | MIGRATE then DELETE | into `settings/hotkeys` |
| `JsonEditor.axaml` | KEEP | restyle its chrome; it is the custom-JSON section of the server form |
| `ProfilesView.axaml` | DELETE | its handlers were already re-implemented at `ServerListView.axaml.cs:71` |
| `ThemeSettingView.axaml` | DELETE | superseded by Настройки > Приложение |
| `BackupAndRestoreView.axaml` | DELETE | superseded by `settings/data` |
| `CheckUpdateView.axaml` | WIRE | `settings/about`, desktop only |
| `ClashProxiesView.axaml`, `ClashConnectionsView.axaml` | DELETE | Mihomo proxy-group control is not part of this product. A deliberate cut, recorded in 16 as D-11 |
| `StatusBarView.axaml` | REFACTOR then DELETE | its handlers move into the shell; the 0x0 phantom view goes |

---

## 11. The delete list

An app that only grows is a failed restructure. Everything below stops existing in the same change
that replaces it. Nothing here is "kept for now".

### 11.1 Android, layouts

`layout_home_empty.xml`, `layout_home_account.xml` (absorbed into the Главная header),
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

**37 of 73 layout files.**

### 11.2 Android, classes

`ui/SettingsActivity.kt`, `ui/CheckUpdateActivity.kt`, `ui/AppPickerActivity.kt`,
`ui/UserAssetUrlActivity.kt`, `ui/SubSettingActivity.kt`, `ui/SubSettingRecyclerAdapter.kt`,
`ui/LocalProxyActivity.kt`, `ui/ProviderSettingsActivity.kt`, `ui/UrlSchemeListActivity.kt`,
`ui/ServerCustomConfigActivity.kt`, `ui/ServerGroupActivity.kt`, `ui/ServerProxyChainActivity.kt`,
`ui/ServerProxyChainMemberAdapter.kt`, `ui/HomeMetaPagerAdapter.kt`, `ui/LoginActivity.kt`.
`ui/ServerActivity.kt` is replaced by one `ServerEditActivity`.
Corresponding `<activity>` entries are removed from `AndroidManifest.xml`.

### 11.3 Android, resources

Drawables: `bg_home_gradient.xml`, `drawable-night/bg_home_gradient.xml`,
`bg_home_gradient_mono.xml`, `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`,
`bg_bottom_nav_scrim.xml`, `bg_nav_header.xml`, `nav_header_bg.png`, `bg_acc_option.xml`,
`bg_speed_chip.xml`, `ripple_card.xml`, `bg_chip_gold.xml`, `ic_circle.xml`, `ic_nav_more.xml`,
`ic_qu_start_24dp.xml`, `ic_qu_stop_24dp.xml`.
Fonts: `res/font/montserrat_thin.ttf` (152 KB of an unused second typeface with zero Cyrillic).
Styles: `TabLayoutTextStyle`, `BrandedSwitch`.
Menus: `menu_main.xml` items that route to deleted screens.

`menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml` and `style/BottomNavIndicator` are
**not** deleted: they become live again when the bar becomes a real `BottomNavigationView` (3.1).

### 11.4 Desktop, views

`OnboardingView.axaml(.cs)`, `LoginView.axaml(.cs)`, `CompactHomeView.axaml(.cs)`,
`CompactServersView.axaml(.cs)` (after harvesting the search field), `BottomNavBar.axaml(.cs)`,
`ProfilesView.axaml(.cs)`, `ThemeSettingView.axaml(.cs)`, `BackupAndRestoreView.axaml(.cs)`,
`ClashProxiesView.axaml(.cs)`, `ClashConnectionsView.axaml(.cs)`, `StatusBarView.axaml(.cs)`,
`MsgView.axaml(.cs)`, `AddServerWindow.axaml(.cs)`, `AddServer2Window.axaml(.cs)`,
`AddGroupServerWindow.axaml(.cs)`, `SubEditWindow.axaml(.cs)`, `SubSettingWindow.axaml(.cs)`,
`OptionSettingWindow.axaml(.cs)`, `FullConfigTemplateWindow.axaml(.cs)`,
`GlobalHotkeySettingWindow.axaml(.cs)`, `RoutingRuleSettingWindow.axaml(.cs)`,
`RoutingRuleDetailsWindow.axaml(.cs)`, `ProfilesSelectWindow.axaml(.cs)`, plus the old 12-line
`ServersView.axaml`.

**24 of 56 view files, including all 12 remaining upstream `Window`s.** After this change the
product contains **zero** `resx:ResUI` references in the UI layer and **zero** OS-decorated
secondary windows.

### 11.5 Desktop, resources and code

`Brush.HomeGradient` (dark and light), `Brush.ConnectGlow`, `Nav.Scrim`, `Radius.Search`,
`Radius.Button` (folded into the four documented radii), `Button.IconButton` (the legacy 32px
class), `Size.SegmentChip` if it survives the settings pass, and the `navScrim` `OpacityMask`
block at `Views/MainWindow.axaml:582`.
`Brush.Ring.Outer` and `Brush.Ring.Inner` **survive**: `00-rules.md` 1.4.3 permits them for the one
connect-sonar hero moment and for nothing else.
Code: `ApplyLayoutMode`, `ViewFor`, `BindActiveHome`, `ToggleLayoutSize`,
`CompactBreakpointWidth`, `LayoutHysteresis`, `DelegateSnackMsg`'s dead routing.

### 11.6 Net effect

| | Android | Desktop |
|---|---|---|
| Layout / view files before | 73 | 56 |
| Deleted | 37 | 24 |
| New | about 20 | about 12 |
| After | about 56 | about 44 |
| Unreachable screens before | 14 | 11 |
| Unreachable screens after | **0** | **0** |
| Parallel row grammars before | 3 | 2 |
| After | **1** | **1** |
| Spacing scales before | 2 | 1 with 14 violating files |
| After | **1** | **1** |

---

## 12. Cross-platform parity

### 12.1 Identical, by contract

The destination set and its order. Every tab label. Every route identity in 7.1. Every deep link in
7.2. The settings group order and every row title in 9.2. The default value of every setting. The
header model in 3.3. The sub-page toolbar in 3.4. The launch variants in 5.3. The state matrix. The
copy, string for string, via `res/values/strings*.xml` and `Common/L.*.cs`.

### 12.2 Allowed to differ

Navigation shape (bottom bar and rail versus rail only). Per-item action surface (bottom sheet
versus flyout). Hover, focus rings and keyboard shortcuts (desktop). Haptics (Android). Window
chrome and the tray. Nothing else.

### 12.3 Logged parity gaps

| ID | Gap | Platform | Resolution |
|---|---|---|---|
| PG-1 | «Проверить обновления» | desktop only | Desktop ships a downloadable binary; Android is distributed through the site and the bot. Android's `settings/about` shows the version and «Обновления приходят в Telegram-боте» with no check action |
| PG-2 | Горячие клавиши | desktop only | No Android equivalent exists |
| PG-3 | Масштаб интерфейса | desktop only | Android uses the system font scale, which the layouts must survive at 200% |
| PG-4 | Always-on VPN, per-app proxy | Android only | OS features with no desktop equivalent |
| PG-5 | Перенести подписку на ТВ | Android only | Android TV pairing |
| PG-6 | QS tile, widget, launcher shortcuts (Android); tray menu (desktop) | each | OS integration surfaces |
| PG-7 | sudo password prompt | Linux only | Raised by the first TUN start |
| PG-8 | Haptics; hover and focus rings | Android; desktop | Input model |
| PG-9 | Post-sign-in sync overlay | desktop only today | **Closes:** Android gets `#sync_overlay` (5.6) |
| PG-10 | Server search | neither, today | **Closes:** both get it (4.2) |
| PG-11 | Settings search | neither, today | **Closes:** both get it (9.5) |
| PG-12 | Feedback channel | Android has Toasts, desktop has nothing | **Closes:** both get section 8 |
| PG-13 | Route identity and deep links | Android has actions only, desktop has none | **Closes:** both get section 7 |

---

## 13. Every screen ships its states

`00-rules.md` 15 applies to every surface in this document. The states that this structure makes
load-bearing, and the surface that owns each:

| State | Owned by |
|---|---|
| `нет аккаунта` | Главная variant A, Аккаунт gate |
| `нет подписки` | Главная variant B, Аккаунт empty subscription |
| `подписка истекает` | Главная strip, Аккаунт subscription card chip |
| `подписка истекла` | Главная variant D strip on three tabs |
| `триал` | Аккаунт subscription card, using the backend `isTrial` flag only |
| `Telegram не привязан` | Аккаунт group «Вход» |
| `нет серверов` | Главная variant A CTA, Серверы empty state |
| `нет результатов поиска` | Серверы, Настройки |
| `подключение` / `подключено` / `отключение` | Главная connect object |
| `ошибка туннеля` | Главная status line plus a snackbar with «Повторить» |
| `лимит устройств` | Главная strip, Аккаунт > Устройства |
| `офлайн` | the strip on Главная, Серверы, Аккаунт |
| `частичная загрузка` | Серверы, marked on the failing provider's group header |
| `длинный контент` | 60-char remark, 32-char handle, 12-digit balance |
| `один элемент` | one server, one device, one payment |
| `200% масштаб шрифта` | every screen |

---

## 14. Acceptance checklist for this structure

Tick before any screen spec is written against this file.

- [ ] Four destinations, same order, same labels, on both platforms, visible in every state
- [ ] The nav bar never changes shape between signed in and signed out
- [ ] Every tab has a 56dp ground-plane header at the same gutter, with 0 or 1 trailing action
- [ ] No tab header contains the accent
- [ ] Cold start lands on Главная in every variant, and no gate covers it except the post-sign-in
      overlay
- [ ] The sign-in screen has exactly one filled accent control on it
- [ ] Nothing is more than two levels below a tab
- [ ] Every route in 7.1 exists on both platforms and is reachable by deep link
- [ ] `depv://import`, `depv://add` and `depv://routing/*` all go through the confirm sheet
- [ ] Android: predictive Back enabled, `onKeyDown` Back handler deleted, Back restores scroll,
      search and filter
- [ ] Desktop: Esc, `Alt+Left` and mouse button 4 all pop the sub-page; the stack is per tab
- [ ] Настройки is 4 named groups, every group 7 rows or fewer, plus 2 unheadered footer rows
- [ ] Every setting has exactly one home; the grep for a duplicate «Автообновление» returns one hit
- [ ] Every screen in section 10 has a verdict and a destination; none is "phase two"
- [ ] Everything in section 11 is deleted in the same change that replaces it
- [ ] Zero unreachable screens on both platforms
- [ ] Zero `resx:ResUI` references left in `v2rayN.Desktop/Views/`
- [ ] The feedback channel exists: snackbar or toast, the status strip, and the log page
- [ ] Every product state in section 13 has a named owner surface

---

## 15. Conflicts between the foundation documents, and how they are resolved

| ID | Conflict | Resolution |
|---|---|---|
| **C-1** | `02-inventory-pc.md` V1 records `Brush.HomeGradient` and `Brush.ConnectGlow` as a deliberate brand layer, and asks the spec to either amend the law or replace them. `00-rules.md` 1.4.3 and `03-direction.md` F1 both ban them outright, and F1 names the six Android drawable files to delete | **Replaced, not amended.** Both platforms go to a flat ground plane. `Brush.Ring.Outer` and `Brush.Ring.Inner` survive for the single 600ms connect sonar, which `00-rules.md` 1.4.3 explicitly permits |
| **C-2** | `00-rules.md` 12.3 says desktop content is "capped at 720px and centred"; `03-direction.md` 7.4 says "left aligned against the rail rather than stretched" | **Rules win** (precedence 0.1). Content is capped at 720 and centred within the area right of the rail |
| **C-3** | `00-rules.md` 13 says transient feedback is a `Snackbar` on Android and a `Border.Toast` on desktop; `02-inventory-pc.md` 4.1 asserts "the owner rejected toasts" and asks for an inline strip instead | **Both, split by kind.** Transient events use the snackbar and the toast per the rules. Persistent conditions use the status strip (8.2). The inventory's claim is treated as being about the *deprecated Android custom-view toast at a 110dp offset*, which is deleted |
| **C-4** | `00-rules.md` 12.3 requires a 900x600 minimum window; the shipping desktop app starts at 372x630 with `MinWidth=340` | **Rules win.** Default 1040x720, minimum 900x600, compact mode deleted (3.2) |
| **C-5** | `03-direction.md` 7.3 caps a screen at one card; the Аккаунт tab needs an identity block and N subscription cards | The identity block is **not a card**, it is a ground-plane block. The subscriptions are a **paged** carousel, so exactly one card is visible at any instant. Recorded as D-12 |
| **C-6** | `03-direction.md` 7.3 caps a screen at 4 groups; Настройки needs a home for «Данные» and «О приложении» | The two footer rows carry **no section header**, so they are structurally not a group. Recorded as D-13 |

---

## 16. Decisions this document takes

### 16.1 Taken here, inside existing law

- **D-6.** The destination set is exactly four, in the order Главная, Серверы, Аккаунт, Настройки,
  present in every state on both platforms.
- **D-7.** Sign-in is a state of the Аккаунт tab, not a screen reached from Главная. `LoginActivity`
  and `LoginView` are deleted.
- **D-8.** First run is a state of Главная. `OnboardingView` and `layout_home_empty.xml` are
  deleted.
- **D-9.** Desktop has no compact mode and no bottom navigation. One Home view, one Servers view,
  a rail that is always 76 wide with labels.
- **D-10.** The ten server-editor layouts on Android and the three server-editor windows on desktop
  collapse into one protocol-driven form at `servers/server/{guid}`, which is restored to
  reachability through the rewired server actions sheet.
- **D-11.** Clash and Mihomo proxy-group control is cut from the product.
  `ClashProxiesView.axaml` and `ClashConnectionsView.axaml` are deleted rather than wired.
- **D-12.** The Аккаунт identity block is not a card; the subscription carousel is paged so exactly
  one card is visible at a time. This satisfies the one-card cap.
- **D-13.** The Настройки footer pair («Данные и резервные копии», «О приложении») carries no
  section header and is therefore not one of the four groups.
- **D-14.** `depv://import`, `depv://add`, `depv://routing/add`, `depv://routing/onadd`,
  `v2rayng://install-config` and `v2rayng://install-sub` all require a confirm sheet before they
  mutate anything.
- **D-15.** Android launcher shortcuts go from four to three: «Переключить», «Сканировать QR»,
  «Серверы».

### 16.2 Needs an owner decision, in `00-rules.md` section 18 row format

| Date | Decision | Rule affected |
|---|---|---|
| pending | **D-16.** The default Android string resource set becomes Russian. `res/values/strings.xml` holds 469 strings of which only 76 are Cyrillic, so on any non-Russian device the app renders English chrome mixed with Russian product copy (`bottom_nav_settings` is «Настройки» while `bottom_nav_home` is "Home"). The current English default moves to `res/values-en/`. Without this, roughly half of the redesigned copy is never seen | 9.1, 13 |
| pending | **D-17.** The Android bottom bar becomes a Material `BottomNavigationView` with a transparent item ripple and `@style/BottomNavIndicator`, replacing the hand-rolled `LinearLayout`. The no-ripple requirement of 0.4.8 is met by `itemRippleColor`, not by avoiding the component | 7.7, 11.2 |
| pending | **D-18.** Android sub-pages remain Activities rather than becoming a single-Activity navigation graph. Lower risk, predictive Back works per Activity, and every sub-page already exists as one | 11.3 |
| pending | **D-19.** Мihomo and Clash support is removed from the desktop product surface (D-11). This is a feature cut, not a design change, and needs the owner's word | n/a |
| pending | **D-20.** The desktop window's phone-shaped 372x630 mode is removed (D-9). Users who deliberately kept the app as a narrow strip lose that; the window is still resizable down to 900x600 | 12.3 |

Nothing in 16.2 is implemented until the row is pasted into `00-rules.md` section 18 and the rule
body there is updated.

---

*End of app structure. Next documents: `12-…` the shell and Главная screen spec, `13-…` Серверы,
`14-…` Аккаунт and the sign-in gate, `15-…` Настройки and its fifteen sub-pages.*
