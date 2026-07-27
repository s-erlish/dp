# PC - Home / Connect surface and shell audit

Scope: `Views/HomeView`, `CompactHomeView`, `ConnectHeroView`, `HomeAccountChip`,
`HomeHeroPresenter.cs`, `StatusBarView`, `SubscriptionMetaView`, `BottomNavBar`, `MainWindow`,
`MsgView`, `SudoPasswordInputView` (+ `.axaml.cs`), and their ViewModels.
Root: `/home/user/v2rayN/v2rayN/v2rayN.Desktop`. Branch `claude/app-audit-agents-hyyftk`.
Law: `docs/design2026/00-rules.md` sections 1-10, 12-16, and the twelve ratified decisions in 18.
Specs: `13-start-screen.md` 16-17, `33-master-plan-pc.md` 2.9 / 2.12 / 5, `11-app-structure.md`,
`24-tab-conformance.md` 3.1 / D-03..D-07, `02-inventory-pc.md`, `31-self-assessment.md` B3/B4/B9.
No source file was edited. No git command was run.

---

## 1. Verdict

**8 / 20. Ship bar is >= 18 with no dimension below 3. This surface goes back.**

| # | Dimension (`00-rules.md` 17.1) | Score | The single reason |
|---|---|---|---|
| 1 | Accessibility | **1** | The product's primary control cannot be reached, focused or activated without a mouse, and carries no accessible name |
| 2 | Performance | **2** | Four infinite compositor loops run in the **idle** state; they stop only under lite, window-hide or layout-deactivate |
| 3 | Appearance and theming | **2** | Token discipline is real (0 `StaticResource Brush.*`, live 3-theme re-resolve), but a radial page gradient, a radial glow and four blue rings at rest break the accent budget and three bans |
| 4 | Platform conformance | **1** | A desktop app that opens as a 372px phone strip, with no «Серверы» destination, two idle animations on one object, and two upstream Semi/`resx` views still in the tree |
| 5 | Adaptivity | **2** | Two bands with hysteresis, in-app zoom and resize grips all work; content is never capped at 720 or centred, the wide pane has no scroll region, the compact page nests two |

Severity floor applies: every section-1 ban hit and every missing section-15 state is **at least P1**
by `00-rules.md` 17.2. This surface carries 9 distinct ban hits and 12 missing states.

---

## 2. Canonical view determination

**Both views are live. Neither is dead. The one a user sees on first run is `CompactHomeView`.**

Traced from what constructs each:

| Step | Evidence |
|---|---|
| Both are constructed unconditionally as fields | `MainWindow.axaml.cs:19` `private readonly Control _homeView = new HomeView();` and `:27` `private readonly CompactHomeView _compactHome = new();` |
| Both are permanent children of one host | `MainWindow.axaml.cs:230-235` adds `_homeView`, `_compactHome`, `_settingsView`, `_accountView` to `contentHost.Children` at `Opacity=0`, `IsHitTestVisible=false` (keep-alive shell) |
| One is picked per frame by window width | `MainWindow.axaml.cs:483` `_ => _compactMode ? _compactHome : _homeView` |
| The breakpoint | `:31` `CompactBreakpointWidth = 760.0`, `:32` `LayoutHysteresis = 24.0`, watcher at `:243` |
| The default is compact | `:33` `_compactMode = true`; window declares `Width="372" Height="630"` (`MainWindow.axaml:13-14`), 372 < 760 |
| Exactly one holds the live VM | `BindActiveHome()` (`:493-515`) assigns `_homeViewModel` to the active layout and nulls the other's `DataContext`; `HomeHeroPresenter.Bind` / `hero.Deactivate()` gate the inactive hero's loops |

Consequence: an earlier grep for `new CompactHomeView` returns nothing because the construction is
target-typed `new()`. **A future agent that concludes "CompactHomeView is dead" from that grep and
deletes it removes the Home screen every first-run user actually sees.**

**Which one a rebuild targets:**

| Spec | Verdict on `HomeView` | Verdict on `CompactHomeView` |
|---|---|---|
| `24-tab-conformance.md` D-04 / D-05 | REBUILD, wave 4 - "One `HomeView`, single column, capped at 720, centred. The 440 \| 1 \| * split is deleted" | **DELETE**, wave 2, together with `BottomNavBar`, `ApplyLayoutMode`, `ViewFor`, `BindActiveHome`, `ToggleLayoutSize`, `CompactBreakpointWidth`, `LayoutHysteresis` |
| `33-master-plan-pc.md` 2.9 / 5.2 | REBUILD into `Views/Home/HomePage.axaml`, one file, both modes | Kept as a **band**, not a file: the compact layout is the same single centred column the wide layout shows below 980 of content width |
| `13-start-screen.md` 2.2 / 16.1 | REBUILD | DELETE - "One Home view with two internal layout bands replaces two views" |

**Determination: `HomeView` is the canonical file; `CompactHomeView` is a band, not a view.** All
three specs converge on one Home file with two internal layout bands. `CompactHomeView` may not be
deleted before either (a) that single file implements the < 980 single-column band, or (b) the
default window changes to 1080x720 wide (`33-master-plan-pc.md` 2.9, decision **PC-D3**, still
un-ratified). Deleting it first strands every default-sized window with no Home.

`ConnectHeroView` is shared by both and is the only connect pipeline (`HomeHeroPresenter.Bind`,
called from `HomeView.axaml.cs:62` and `CompactHomeView.axaml.cs:100`). It is RESTYLE, not delete.

---

## 3. The connect control - the ONE 600ms hero moment

### 3.1 Layer census

`ConnectHeroView.axaml` renders **nine** layers to express one boolean plus two transitions.
`33-master-plan-pc.md` 5.3 deletes five of them and 2.12.2 deletes the frame as well.

| # | Element | Line | Spec verdict | Status |
|---|---|---|---|---|
| 1 | `#AmbientSonar` Ø200, `Brush.Ring.Inner`, 6.5s / 5.5s infinite wave | `:341-353` | **DELETE** (5.3.5) | present, running at idle |
| 2 | `#AmbientRing` Ø222, `Brush.Ring.Inner`, 6s / 5s infinite breathe | `:356-368` | **DELETE** (5.3.5) | present, running at idle |
| 3 | `#GlowHalo` Ø220, `Brush.ConnectGlow` radial | `:373-396` | **DELETE** (5.3.5) | present, visible when connected, breathing 850ms when connecting |
| 4 | `#RingOuter` Ø228, `Brush.Ring.Inner` 1.5 | `:402-409` | DELETE - the ring is the disc's own 1.5px border at 176 (2.12.2) | present |
| 5 | `#RingHoverGlow` Ø228 overlay | `:415-424` | **DELETE** (5.3.5) | present |
| 6 | `#RingInner` Ø210, `Brush.Ring.Outer` 1.5 | `:425-432` | DELETE (2.12.2) | present |
| 7 | `#SonarPulse` Ø200 | `:437-448` | **KEEP** - the one hero moment | present |
| 8 | `#SonarPulseEcho` Ø200, +120ms | `:452-463` | **DELETE** - "there is never a second ring" (5.3.6, 13-start 12.3) | present |
| 9 | `#ConnectingArc` Ø190, 3px accent, dashed | `:476-495` | KEEP, move onto the 176 circle | present at 190, not 176 |
| - | `#HeroFrame` 230x230 (`Size.HeroFrame`) | `:327-333` | **DELETE** - "`Size.ConnectFrame` is not created" (2.12.2) | present at 230, spec floor was 200, canonical is none |
| - | `#ConnectDisc` 176, `Brush.SurfaceHigh` | `:511`, `GlobalStyles.axaml:710-727` | fill must be `Brush.SurfaceHighest` P3 (2.12.2) | wrong plane, one step too dark |
| - | `#CornerAddButton` | `:743-761` | **DELETE** - moves to Серверы (5.3.5) | present, wide layout only |

`ConnectHeroView.axaml` is 839 lines against a budget of **< 120**; `.axaml.cs` is 1 156 against
**< 260** (`33-master-plan-pc.md` 5.3). The deleted layers' state machines live in the second file.

### 3.2 `Brush.Ring.*` consumer report (requested explicitly)

`00-rules.md` 1.4.3: `Brush.Ring.*` may be used **nowhere except the single connect-sonar hero
moment**. Full consumer list, whole repo:

| Consumer | File:line | Key | Permitted? |
|---|---|---|---|
| `#SonarPulse` | `Views/ConnectHeroView.axaml:447` | `Brush.Ring.Inner` | **YES** - this is the hero moment |
| `#SonarPulseEcho` | `Views/ConnectHeroView.axaml:462` | `Brush.Ring.Inner` | **NO** - a second ring; the hero emits exactly one |
| `#AmbientSonar` | `Views/ConnectHeroView.axaml:352` | `Brush.Ring.Inner` | **NO** - infinite idle wave |
| `#AmbientRing` | `Views/ConnectHeroView.axaml:367` | `Brush.Ring.Inner` | **NO** - infinite idle breathe |
| `#RingOuter` | `Views/ConnectHeroView.axaml:408` | `Brush.Ring.Inner` | **NO** - static decoration |
| `#RingHoverGlow` | `Views/ConnectHeroView.axaml:423` | `Brush.Ring.Inner` | **NO** - hover bloom |
| `#RingInner` | `Views/ConnectHeroView.axaml:431` | `Brush.Ring.Outer` | **NO** - static decoration |
| mono-overlay rebuild | `App.axaml.cs:660-661` | both keys | token layer, not a surface - fine |

**1 permitted consumer, 6 violating consumers, all in one file.** `Brush.ConnectGlow`
(`ConnectHeroView.axaml:380`) is a separate ban hit (1.4.3 glow) and its mono variant is rebuilt at
`App.axaml.cs:659`.

### 3.3 Motion budget

`00-rules.md` 8.2: the 3.7 scale is the whole vocabulary. 8.4: one 600ms hero moment, nothing else.
8.3: ease-out only, no linear on transitions, no overshoot. 13-start 12.1: "no idle motion of any
kind".

| Animation | Duration | Curve | On the scale? | Verdict |
|---|---|---|---|---|
| `Sonar.pulsing` | 600 | OutQuint | `Dur.Emphasis` | correct - this is the hero |
| `Sonar.pulsing-echo` | 600 +120 delay | OutQuint | - | a **second** 600ms emphasis event |
| `ConnectArc.spinning` | 1200 linear | linear | `Dur.Spin` is **1100** | off-token by 100ms; the linear exemption itself is correct |
| `ConnectArc.arc-windup` | 200 | OutQuint | `Dur.Shell` 200 exists | value on-scale, role is not `Shell`; 5.3.4 specifies exactly this so it is fine |
| `Glow.breathing` | 850 | **SineEaseInOut** | no | off-token duration **and** a banned two-way ease-in-out |
| `Path.shieldbreathe` | 850 | SineEaseInOut | no | second idle-ish loop on the same object |
| `AmbientRing.breathe-idle` | **6000** | SineEaseInOut | no | infinite loop at rest |
| `AmbientRing.breathe-live` | **5000** | SineEaseInOut | no | infinite loop while connected |
| `AmbientSonar.rest-idle` | **6500** | OutQuint | no | infinite loop at rest |
| `AmbientSonar.rest-live` | **5500** | OutQuint | no | infinite loop while connected |
| `Panel.assembling` | 400 markup / **460** code (`:1091`) | OutQuint | no | page-load choreography, banned by 8.9; `13-start-screen.md` 2.1 deletes its Android twin `shield_assemble.xml` |
| connect bloom | **180 + 260**, peak **1.04** | OutQuint | no | off-token, and a scale peak above rest is overshoot (8.3) |
| error contract | **150 + 150**, peak 0.98 | OutQuint | no | off-token |
| hover disc / ring | 120 | OutQuart | no (`Dur.State` 220 or 150 per 7.1) | off-token |
| press disc | 90 in / 160 out, scale **0.94** | Quart / Quint | yes | correct - 0.94 is the one documented exception (2.12.2) |

**Two competing idle animations on one object** is `02-inventory-pc.md`'s own words (`:56`) and
`33-master-plan-pc.md` 5.3's. At rest, with nothing happening, the desktop Home runs a 6s breathe
and a 6.5s wave; connected, it runs a 5s breathe, a 5.5s wave and a static glow.

Loop suppression is genuinely well built (`MotionSuppressed` folds window-hidden and
layout-deactivated, `ConnectHeroView.axaml.cs:99-112`, `:547-606`) and reduced motion is read live
through `MotionState.Changed` (`:497`, `:608`) rather than once in a constructor. That machinery is
correct and should survive the deletion of what it suppresses.

### 3.4 Accent budget at rest

`00-rules.md` 3.6 and 2.4.1: accent <= 10% of coloured pixels, one filled accent surface per screen.
`13-start-screen.md` 1: "On the disconnected, ungated screen the count of accent pixels is **zero**."
`33-master-plan-pc.md` 5.10: "Accent count is 0 when disconnected and 1 when connected or connecting."

Counted on the disconnected, ungated, signed-in desktop Home:

| Blue object at rest | File:line | Should be |
|---|---|---|
| `#RingOuter` Ø228, `#804C8DFF` (50% accent) | `ConnectHeroView.axaml:408` | deleted |
| `#RingInner` Ø210, `#334C8DFF` (20% accent) | `ConnectHeroView.axaml:431` | deleted |
| `#AmbientRing` Ø222, accent, animating | `ConnectHeroView.axaml:367` | deleted |
| `#AmbientSonar` Ø200, accent, animating | `ConnectHeroView.axaml:352` | deleted |
| `↑` arrow, `Brush.Accent` | `ConnectHeroView.axaml:645` | replaced by `Geo.Action.ArrowUp` in `Brush.OnSurfaceVariant` |
| `↓` arrow, `Brush.Accent` | `ConnectHeroView.axaml:670` | same |
| protocol chip label, `Brush.Accent` | `ConnectHeroView.axaml:727` | `Brush.OnSurfaceVariant` on `Border.ProtocolChip` |
| account-chip initial, `Brush.Accent` | `HomeAccountChip.axaml:89` | the chip is deleted (2.12.3) |
| wordmark tile, accent fill | `MainWindow.axaml:332` | chrome, out of scope of the count, but it is a ninth blue object in the frame |

**Nine blue objects where the contract says zero.** The rail's current-destination indicator
(`MainWindow.axaml:506`) is the one legitimate accent on the frame and it is currently the least
visible of the nine.

---

## 4. The hero-metric ban and the stats row

`00-rules.md` 1.1: the hero-metric template is an absolute ban. 1.2 desktop row: "The connect screen
reduced to a big number plus three stat chips plus an accent wash - same on `HomeView.axaml`."

The row at `ConnectHeroView.axaml:632-680` is not the classic template (there is no 34px Display
figure, and `TextBlock.Display` appears nowhere on this surface - correct per `33` 5.1), but it
fails on four counts:

| Fault | Evidence | Law |
|---|---|---|
| Three type sizes on one 44px strip: 13 / 14 / 13, all inline | `:651`, `:659`, `:676` `FontSize="13"`/`"14"` | 12.1 - inline `FontSize` is a defect; 2.12.3 - all three columns are `Numeric` **16/500** |
| `↑` and `↓` typographic characters used as icons | `:646`, `:671` | 1.4.4, `13-start-screen.md` 7, `33` 5.3.5 - `Geo.Action.ArrowUp/Down` at 16px |
| Latin units, no comma decimal, no `Мбит/с` | `HomeViewModel.cs:103-107`, `:355-356` `"0 KB/s"`, `Utils.HumanFy` | 9.2 - `24,8 Мбит/с`; 1.4.10 |
| Visible when **disconnected**, reading zeroes | `StatsRow` has no visibility gate; only lite hides it (`:191`, `:626`) | 2.12.3 / 5.5 - "**Visible only when connected**"; `33` 5.5 names three zero counters at the top of the page as "the hero-metric template inverted" |

Compounding: `StatsRow.IsVisible = !ReducedMotion` (`ConnectHeroView.axaml.cs:191`, `:626`) **deletes
live data when the user asks for less motion**. Reduced motion is a motion contract (8.8); it does
not license removing information. This is an accessibility regression dressed as an accessibility
feature.

Third column: `13-start-screen.md` 19.1 S-3 puts **latency** there and moves uptime off Главная,
but decision **S-3b is still `pending`** in `13-start-screen.md` 19.3 and does **not** appear in
`00-rules.md` 18. Until it is pasted there, `33-master-plan-pc.md` 2.12.3 governs: **download ·
uptime · upload**, in that order, download first. The shipped order is up · uptime · down - the
mirror image.

---

## 5. Nav rail and bottom bar against 7.7 and 12.4

`00-rules.md` 7.7: Android bottom navigation 3-5 destinations, desktop a left rail with **the same
destinations in the same order and the same labels**. 13 (parity contract): the destination set and
its order are identical across platforms, by contract.

| | Android | Desktop rail | Desktop bottom bar |
|---|---|---|---|
| Source | `res/menu/menu_bottom_nav.xml` | `MainWindow.axaml:460-495` | `BottomNavBar.axaml:132-159` |
| 1 | `nav_home` «Главная» | `navHome` «Главная» | `ItemHome` «Главная» |
| 2 | `nav_servers` «Сервера» | - **missing** - | - **missing** - |
| 3 | `nav_settings` «Настройки» | `navSettings` «Настройки» | `ItemSettings` «Настройки» |
| 4 | - | `navAccount` «Аккаунт» | `ItemAccount` «Аккаунт», `IsVisible="False"` until signed in |

**Four parity defects, not one:**

1. **No «Серверы» destination on desktop.** `L.Shell.cs:27-29` registers `Nav_Home`, `Nav_Settings`,
   `Nav_Account` and no `Nav_Servers`. Servers are buried in the left column of the wide Home
   (`HomeView.axaml:35`) and inside the compact page scroll (`CompactHomeView.axaml:91`). The
   comment at `MainWindow.axaml:445-447` states this as a design intent; the parity contract
   forbids it.
2. **«Аккаунт» exists on desktop and not on Android.** Directly contradicts the 13 contract.
3. **Order differs.** Android Home > Servers > Settings. Desktop Home > Settings > Account.
   `24-tab-conformance.md` 3.1 fixes the set at **Главная · Серверы · Аккаунт · Настройки**;
   `33-master-plan-pc.md` 1.2 and `10-design-system.md` 6.15 order it **Главная · Серверы ·
   Настройки · Аккаунт**. The two specs disagree with each other; logged in section 9.
4. **A destination is hidden by state.** `ItemAccount.IsVisible="False"` until signed in.
   `24-tab-conformance.md` 3.1: "All four are present in every state, signed in or not... desktop's
   zero-width collapse of «Аккаунт» stops."

Component-level defects on both navs:

| Defect | File:line | Law |
|---|---|---|
| Press scale **0.92** on both navs | `BottomNavBar.axaml:62`, `GlobalStyles.axaml:793` | D-11 - 0.97 everywhere, one gesture one number |
| Russian labels in the brand face | `BottomNavBar.axaml:78` `Font.Grotesk`; rail uses `Classes="Chip"` (`MainWindow.axaml:468`, `:480`, `:492`) which is `Font.Brand` (`GlobalStyles.axaml:389`) | D-1 / D-2 / 5.1 - Space Grotesk maps zero Cyrillic; «Главная» is drawn by an undeclared OS fallback. **P1, not polish** |
| Inline `FontSize="11"` on the nav label | `BottomNavBar.axaml:79` | 12.1 |
| `Nav.Scrim` linear gradient under the bar | `BottomNavBar.axaml:24-27`, applied at `:130` via `StaticResource` | 1.4.3 / 6.5; `24-tab-conformance.md` 3.1.4 deletes it by name |
| `navScrim` `OpacityMask` gradient | `MainWindow.axaml:581-587` | same |
| Fixed `Height="64"` on the rail item | `GlobalStyles.axaml:753` | R2 / 3.3 - heights are `MinHeight`, never fixed |
| No accessible name on any nav item | 0 `AutomationProperties` in the whole file set | 10.7 / 14.3 |
| The nav bar has no focus ring path | no `Focusable` and no `:focus-visible` selector on `BottomNavItem` | 7.1 / 12.2 |

The travelling indicators themselves (rail `railIndicator` 3x28 sliding on Y, bottom
`BottomIndicator` 34x3 sliding on X, both at `Dur.State` 220 `OutQuint`) are correct M3 idiom and
correctly are **not** side-stripes. Keep them.

---

## 6. What clips first as the window shrinks to 900x600

Measured against the declared geometry, wide band (rail 76 + 1px hairline + content).

| Window width | Content pane | `HomeView` right column (`ColumnDefinitions="440,1,*"`) | Usable after `DockPanel Margin="16"` | Hero needs 230 |
|---|---|---|---|---|
| 1080 | 1004 | 563 | 531 | fits, 301px of dead air |
| 900 | 824 | 383 | 351 | fits, 121px of dead air |
| **795** | 719 | 278 | **246** | fits by 16px |
| **779** | 703 | 262 | **230** | exactly flush |
| **760** (band floor) | 684 | 243 | **211** | **overflows by 19px** |

**Answer: nothing clips at 900x600. The first failure is the 230px `#HeroFrame` and its 228px rings
overflowing their column between 760 and 779 of window width - a band the wide layout still owns
(the flip to compact is at 760 with 24 of hysteresis).** Because `ClipToBounds="False"` on
`#HeroFrame` (`ConnectHeroView.axaml:332`) and no clip on the parent `DockPanel`, the overflow does
not truncate - it **paints across the 1px divider into the server list**.

Second failure in the same band: `StatusText` `MaxWidth="320"` plus `Margin="24,16,24,0"` needs 368
of column; below ~797 of window width the longest status string
(«Не удалось подключиться») begins to ellipsise. 1.1 bans shipping a truncated primary label.

Three further adaptivity defects that are not clipping but are the same law:

| Defect | Evidence | Law |
|---|---|---|
| Content is never capped at 720 or centred | zero `MaxWidth` on any Home container; the only four `MaxWidth` values in the file set are 320/320/240/400 on leaf text (`ConnectHeroView.axaml:592`, `:618`, `:709`, `:769`) | 4.1, 12.3 - "a stretched phone layout across 1920px is the desktop version of the scaled-up-phone failure" |
| Wide Home has **no** scroll region; compact Home has **two** | `HomeView.axaml` right pane is a bare `DockPanel`; `CompactHomeView.axaml:38` `PageScroll` wraps `ServerListView.axaml:83`'s own `ScrollViewer` | 12.3 - "Scroll regions: one per view. No nested scrollers" |
| The window minimum is below the usability floor | `MainWindow.axaml:13-16` `Width="372" Height="630" MinWidth="340" MinHeight="560"` | 12.3 - 900x600 floor; `33-master-plan-pc.md` 2.9 targets default **1080x720**, min wide 900x600, min compact 380x620 (decision **PC-D3**, un-ratified) |

At the shipped default 372x630, `CompactHomeView` gives `ConnectHeroView` `MinHeight="440"`
(`CompactHomeView.axaml:86`) inside 630 - 28 chrome - 64 account chip - ~64 bottom nav = **474px** of
page. The hero alone is 93% of it. `31-self-assessment.md` B4 measured 70% and graded the compact
Home **C+**; the number is worse than the assessment recorded.

---

## 7. State matrix

`33-master-plan-pc.md` 5.8 enumerates fifteen states. `00-rules.md` 15: a missing state is at least
P1. `ConnectHeroView.ConnectVisualState` has **four** members: `Idle`, `Connecting`, `Connected`,
`Error` (`:29-40`).

| # | State (33 §5.8) | Implemented | Evidence / gap |
|---|---|---|---|
| 1 | Default, disconnected | **partial** | Disc + word render, but 9 blue objects at rest, no gate line, no server row, no subscription card |
| 2 | Default, connected | **partial** | Shield + glow + stats; stats are up/uptime/down at 13/14/13 in `KB/s`, spec is down/uptime/up at 16/500 in `Мбит/с` |
| 3 | Connecting | **yes** | Arc wind-up 200 > spin 1200; plus a banned 850ms glow-breathe and shield-breathe |
| 4 | Disconnecting | **MISSING** | No enum member. Tear-down collapses straight to `Idle`; «Отключение…» is never shown, and `L.Shell.cs` has no key for it |
| 5 | First run, signed out | **MISSING as a Home state** | It is a separate full-screen gate, `OnboardingView`, hoisted above the shell (`MainWindow.axaml:596`). 5.8 and `24-tab-conformance.md` 3.1.9: "Empty is not a screen" |
| 6 | First run, signed in, no subscription | **MISSING** | `LayerEmpty` (`ConnectHeroView.axaml:767-837`) shows «Приветствуем!» + «Пока нет подписок» + **two** filled CTAs (`Classes="Primary"` and `Classes="Tonal"`). Spec: one `Border.EmptyIcon` + «Подписки пока нет» + `Button.Primary` «Купить» + one `Button.Text` |
| 7 | Loading | **MISSING** | No skeleton on Home. `HomeAccountChip` has one (`:116-128`); the surface it belongs to is deleted by 2.12.3 |
| 8 | Empty, no servers, signed in | **partial** | `hasServer:false` dims the shield to 0.38 and swaps the word, but the disc stays clickable (`IsEnabled` is never set false anywhere in the file) |
| 9 | Error, tunnel | **partial** | Red shield + `Home_RetryHint`; no cause, no gate line, no status strip, no recovery action |
| 10 | Offline | **MISSING** | No offline signal anywhere in `HomeViewModel` or the hero |
| 11 | Partial | **MISSING** | No independent subscription/server resolution on Home |
| 12 | Long content | **partial** | `TextTrimming="CharacterEllipsis"` on `StatusText` and `ServerName`; spec wants a 70-char remark to **wrap to two lines** and the row to grow to 72 |
| 13 | Short content | **yes** | Layout holds with one server |
| 14 | Gated (expired subscription) | **MISSING** | No subscription state reaches Главная at all |
| 15 | Success | **partial** | The 600ms sonar fires once per genuine transition (`HomeHeroPresenter.cs:144-148`, `_firstApply` guard is correct) - but **two** rings are emitted |

**3 of 15 states fully present.** Product-specific gate states required by `00-rules.md` 15
(`нет подписки`, `подписка истекает`, `подписка истекла`, `триал`, `Telegram не привязан`,
`лимит устройств`) are **all absent** from this surface.

The one state machine that is right: `HomeHeroPresenter.ApplyConnectState` (`:126-151`). Error wins
over Idle so a failed attempt cannot silently read as an ordinary disconnect, and `_firstApply`
makes a layout swap while connected jump to the end state instead of re-firing the hero moment.
Preserve both behaviours through any rebuild.

---

## 8. Mechanical grep results

Run over the eleven files in scope, from `/home/user/v2rayN/v2rayN/v2rayN.Desktop`.

| Check (`00-rules.md` 1.5 / 9.7) | Count | Detail |
|---|---|---|
| `StaticResource Brush.*` (theme-freezing) | **0** | clean - hold this |
| Inline hex on a paint property | **2** | `ConnectHeroView.axaml:526` `Fill="#000000"` (press scrim, should be `Brush.Scrim`); `MainWindow.axaml:308` `Background="#B3000000"` (DialogHost scrim) |
| Inline `FontSize=` | **9** | `ConnectHeroView.axaml:651`, `:659`, `:676`; `HomeAccountChip.axaml:87`; `SubscriptionMetaView.axaml:255`, `:303`; `MainWindow.axaml:338`, `:346`; `BottomNavBar.axaml:79` (setter form) |
| Inline `FontFamily=` | **8** | `MainWindow.axaml:21` (on the `Window` itself), `:337`, `:345`; `HomeAccountChip.axaml:86`; `SubscriptionMetaView.axaml:134`, `:253`, `:268`; `BottomNavBar.axaml:78` (setter form) |
| Off-scale spacing (allow 0/4/8/12/16/24/32) | **29** | see table below |
| `GradientBrush` declared in a view | **4 lines / 2 brushes** | `BottomNavBar.axaml:24-27` `Nav.Scrim`; `MainWindow.axaml:582-586` `navScrim` mask |
| `Brush.HomeGradient` consumed | **3 in scope** (8 repo-wide) | `HomeView.axaml:16`, `MainWindow.axaml:434`, `:551` |
| `Brush.ConnectGlow` consumed | **1** | `ConnectHeroView.axaml:380` |
| `Brush.Ring.*` consumed | **7** (1 permitted) | section 3.2 |
| `AutomationProperties` | **0** | every icon-only control in the file set is unnamed |
| `Focusable="True"` | **2** | `HomeAccountChip.axaml:67`, `SudoPasswordInputView.axaml:54`. The connect disc is not among them |
| em/en dash in `Common/L.Home.cs` | **5 lines**, 1 in shipped copy | `:40` `Onboarding_Subtitle` - «…из буфера — доступ появится сразу.» (the offending character is quoted here as evidence; it is the only one in this audit) |
| em/en dash, all `Common/L.*.cs` | 44 lines | `L.Account.cs` 20, `L.Settings.cs` 11, `L.Home.cs` 5, `L.Buy.cs` 3, `L.Servers.cs` 2, `L.Shell.cs` 2, `L.Common.cs` 1 |
| `TextBlock.Display` on this surface | **0** | correct per `33` 5.1 |
| `Headline` on this surface | **1** | `ConnectHeroView.axaml:777` in `LayerEmpty` - 5.10 forbids `Headline` and `Display` on Главная |

Off-scale spacing, by file:

| File | Hits | Values |
|---|---|---|
| `SubscriptionMetaView.axaml` | 6 | `:96` 14/10, `:106` 2, `:132` 2, `:292` 10, `:299` 10/14, `:309` 6 |
| `StatusBarView.axaml` | 6 | `:20` 1, `:29` 1, `:77` 1, `:91` 14, `:96` 10, `:110` 10 |
| `MainWindow.axaml` | 5 | `:324` 14, `:328` 7, `:354` 6, `:358` 2, `:518` 10 |
| `HomeView.axaml` | 2 | `:51` 14/12, `:57` 10 |
| `CompactHomeView.axaml` | 2 | `:56` 14/12, `:62` 10 |
| `ConnectHeroView.axaml` | 2 | `:637` 20, `:689` 6 |
| `HomeAccountChip.axaml` | 2 | `:68` 10, `:96` 1 |
| `BottomNavBar.axaml` | 2 | `:39` 6, `:82` 3 (setter form) |
| `MsgView.axaml` | 1 | `:14` 2 |
| `SudoPasswordInputView.axaml` | 0 | uses `{StaticResource Margin4/Margin8/MarginLr8}` throughout |
| **Total** | **29** | |

`HomeView.axaml:51/57` and `CompactHomeView.axaml:56/62` are the **same two off-scale values in two
character-for-character copies of the same 25-line TUN banner** - the duplication `33-master-plan-pc.md`
5.7 and 5.2 both call out by name.

---

## 9. Ban hits

Each is at least P1 by `00-rules.md` 17.2.

| # | Ban | Where | Rule |
|---|---|---|---|
| 1 | Decorative gradient - radial page wash | `HomeView.axaml:16`, `MainWindow.axaml:434`, `:551` (`Brush.HomeGradient`) | 1.4.3, 6.5 |
| 2 | Decorative glow - radial halo behind the disc | `ConnectHeroView.axaml:380` (`Brush.ConnectGlow`), breathing 850ms at `:66-86` | 1.4.3, 6.5, 4.7 |
| 3 | Decorative gradient - navigation scrims | `BottomNavBar.axaml:24-27`, `MainWindow.axaml:581-587` | 1.4.3 |
| 4 | `Brush.Ring.*` outside the connect sonar | 6 consumers, section 3.2 | 1.4.3 |
| 5 | Decorative motion that conveys no state | `AmbientRing` + `AmbientSonar` infinite loops at idle **and** connected; `Panel.assembling` cold-start choreography | 1.3 (product bans), 8.1, 8.9, 13-start 12.1 |
| 6 | Typographic characters as UI chrome | `ConnectHeroView.axaml:646` `↑`, `:671` `↓` | 1.4.4 |
| 7 | Russian strings in a face that maps zero Cyrillic | `BottomNavBar.axaml:78`; rail `Classes="Chip"` at `MainWindow.axaml:468`/`:480`/`:492`; `HomeAccountChip.axaml:86` (the avatar initial is a Cyrillic letter) | 1.3, 5.1, D-1, D-2 |
| 8 | Second accent-strength surface / accent budget | 9 blue objects at rest, section 3.4; and `LayerEmpty` shows a `Primary` **and** a `Tonal` CTA (`ConnectHeroView.axaml:798`, `:821`) | 1.4.1, 3.6, 4.3 |
| 9 | Raw colour literal in a view | `ConnectHeroView.axaml:526` `#000000`, `MainWindow.axaml:308` `#B3000000` | 1.4.6 |
| 10 | Off-scale spacing | 29 hits, section 8 | 1.4.5 |
| 11 | Latin UI text in a shipped surface | `MsgView.axaml` and `SudoPasswordInputView.axaml` render `resx:ResUI.*` upstream strings with `Theme="{DynamicResource CardBorder}"` / `SimpleToggleSwitch` - default Semi look, English copy | 1.4.10, 12.1 |
| 12 | Em-dash in shipped copy | `Common/L.Home.cs:40` | 1.4.11, 9.7 |

Two Absolute Bans this surface **passes**, worth recording so they are not reintroduced: no
side-stripe borders anywhere in the file set, and no nested cards (`ConnectHeroView.axaml:780`
`Border.Card` and `SubscriptionMetaView.axaml:95` `Border.Card` are never nested inside one another).

---

## 10. Parity gaps against the Android counterpart

`00-rules.md` 13: destination set and order, every user-visible string for the same concept, the
default of every setting, the state matrix and the motion tempo are **identical by contract**. A
difference is a defect or a logged gap; each row below says which.

| # | Concern | Android | Desktop | Verdict |
|---|---|---|---|---|
| 1 | «Серверы» destination | `menu_bottom_nav.xml` `nav_servers` | absent; servers live inside Home | **defect** - 7.7, 13 |
| 2 | «Аккаунт» destination | absent from the bottom nav | present, and hidden until signed in | **defect** - 13, `24-tab-conformance.md` 3.1 |
| 3 | Destination order | Home > Servers > Settings | Home > Settings > Account | **defect** - 13 |
| 4 | Idle status word | `home_status_disconnected` = «Отключено» | `Home_NotConnected` = «Не подключено» (`L.Home.cs:23`) | **defect** - 2.12.1 names «Не подключено» as the **failure** word; the desktop spends it on idle and has none left for a failure. Named verbatim in 2.12 as the parity break that motivated the shared table |
| 5 | No-server word | «Сервер не выбран» | «Выберите сервер» (`L.Home.cs:24`) | **defect** - 2.12.1 |
| 6 | Disconnecting word | «Отключение…» | no state, no key | **defect** - 2.12.1, 5.8 |
| 7 | Connected colour signal | green word + filled shield | accent word, no dot (`ConnectHeroView.axaml.cs:358-359`) | **defect** - 2.12.1 requires an 8px `Brush.Green` dot; `13-start-screen.md` 6 requires the word in `colorTertiary` |
| 8 | Numeric strip | down · uptime · up, three equal columns, unit inside the value | up · uptime · down at 13/14/13, `KB/s` | **defect** - 2.12.3 |
| 9 | Numeric strip visibility | only when connected | always, reading zeroes; hidden by lite | **defect** - 2.12.3, 5.5 |
| 10 | Subscription on Home | one card, state chip, expiry line | absent | **defect** - 2.12.3 element 6 |
| 11 | Server row on Home | one `Border.Row` with the unified server icon | absent (a whole server **list** is embedded instead) | **defect** - 2.12.3 element 5 |
| 12 | Status strip / condition bar | six prioritised conditions | absent; one bespoke TUN banner, inlined twice | **defect** - `33` 5.7, `13-start-screen.md` 9 |
| 13 | Press scale on rows/nav | 0.97 | 0.92 on both navs | **defect** - D-11 |
| 14 | Press scale on the disc | 0.97 | 0.94 | **allowed** - the one documented exception (2.12.2, `13-start-screen.md` 17) |
| 15 | Hover | none | 6% overlay | **allowed** - 13 translation table. But the disc's hover is a surface-lift **plus** a ring bloom (`RingHoverGlow`); the bloom is not allowed |
| 16 | Focus ring | on every focusable control | absent on the disc and on both navs | **defect** - 13 translation table, last row |
| 17 | Toggle shortcut | none | `Ctrl+Enter` specified, **not implemented** | **defect** - `33` 5.3.3 / 5.9, `13-start-screen.md` 19.3 S-6b (still `pending` in `00-rules.md` 18) |
| 18 | Uptime location | ongoing notification (S-3b) | on Главная | **logged gap** - S-3b is `pending`, so 2.12.3's uptime-on-Главная governs both platforms until it is ratified |
| 19 | Android nav label «Сервера» | `values-ru/strings.xml:517` | n/a | **logged gap on the Android side** - 9.3 locks the noun as «сервер»/«Серверы» |

---

## 11. Load-bearing fixes no work item may undo

Both were verified in the working tree. Any rebuild of this surface must carry them forward
verbatim; a reviewer should treat their loss as a P0 regression.

### 11.1 The onboarding gate's synchronous storage snapshot before the first frame

`Views/MainWindow.axaml.cs:207-216`

```
_storedServersAtLaunch = Design.IsDesignMode ? null : AppManager.Instance.HasStoredProfiles();
_isEmpty = _storedServersAtLaunch == false;
```

Why it exists: the engine loads servers **asynchronously** (`MainWindowViewModel.Init` >
`RefreshServersDispatcherAsync`), so at first paint the in-memory list is empty and is
indistinguishable from "this user genuinely has nothing". Before this snapshot, `_isEmpty` defaulted
to `true` and a returning user with a clipboard-imported subscription saw the «добавьте подписку»
onboarding gate on **every** launch until the database arrived.

Three properties that must survive together:

1. The question is asked **synchronously in the constructor**, before the first
   `ApplyShellVisibility()`.
2. `null` means *unknown* and is **not** treated as empty - unknown shows the shell, not the gate
   (`ApplyShellVisibility`, and `_isEmpty = _storedServersAtLaunch == false` is deliberately not
   `!= true`).
3. The same snapshot is handed to the view model - `new HomeViewModel(vm, _storedServersAtLaunch)`
   (`:1000`) - so one question yields one answer on both sides. `HomeViewModel.cs:95-99` documents
   the matching "stay false while the answer is unknown" rule.

The 3-way gate it feeds (`ApplyShellVisibility`: `syncing > empty > content`, with `_isSyncing` from
post-login import and `_isStartupLoading` from a restored session) is part of the same fix. Moving
first-run from a separate `OnboardingView` into a **state of Главная** (`24-tab-conformance.md`
3.1.9) must preserve the snapshot and the 3-way precedence, not just the visuals.

### 11.2 The autostart registry reconciliation

`Common/AutostartHelper.cs`, called at `ViewModels/SettingsViewModel.cs:172`
(`AutostartHelper.Reconcile(_config.GuiItem.AutoRun)`).

It reconciles two independent registry facts, not one:

| Fact | Key |
|---|---|
| The Run entry itself | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, value `departament` |
| Task Manager's separate enable/disable flag | `HKCU\...\Explorer\StartupApproved\Run`, odd first byte = user-disabled |

`IsEnabled()` returns true only when the Run value is non-empty **and** Task Manager has not marked
it disabled; `Set()` calls `ClearStartupApprovedFlag()` so enabling actually takes effect. Without
the second half, the toggle writes a Run value that Windows silently ignores and the settings switch
lies. `Reconcile` runs at startup so a config that says "on" and a registry that says otherwise
(renamed executable, previous implementation, startup cleaner) converge instead of diverging
forever. Non-Windows is a no-op and `AutoStartupHandler` owns autostart there.

Neither fix is cosmetic and neither is visible in the UI. Both are easy to delete by accident during
a rewrite of the files that host them.

---

## 12. Documentation conflicts found (not resolved here)

Recording these so no implementer silently picks one. Each needs an owner decision routed through
`00-rules.md` 18.

| # | Conflict | Documents | Note |
|---|---|---|---|
| C-1 | Status dot beside the status word | `33-master-plan-pc.md` 2.12.1 requires an 8px dot in all six states; `13-start-screen.md` 19.1 **S-1** forbids it as the decoration tell | 2.12 self-declares canonical and is the shared table both plans inherit; S-1 cites `03-direction.md` 2.4. Unresolved |
| C-2 | Third numeric column | 2.12.3 = uptime (centre); `13-start-screen.md` 19.1 **S-3** = latency, uptime leaves Главная | **S-3b is `pending`** and absent from `00-rules.md` 18, so 2.12.3 governs today |
| C-3 | `HomeAccountChip` | `13-start-screen.md` 16.1 RESTYLE into the header row; 2.12.3 and `33` 5.6 **delete** it ("no account row, and no account chip") | 2.12.3 canonical |
| C-4 | The 200px connect frame | `13-start-screen.md` 3 / 16.4 add `Size.ConnectFrame` 200; 2.12.2 states "`Size.ConnectFrame` is not created" and deletes the frame | 2.12.2 canonical |
| C-5 | Ring geometry | `13-start-screen.md` 5.2 = a separate 3dp ring at 176 in `color_on_surface_dim`; 2.12.2 = 1.5px `Brush.OutlineStrong` as the disc's own border | 2.12.2 canonical. **`Brush.OutlineStrong` does not exist in `Assets/GlobalResources.axaml` yet** |
| C-6 | Compact mode | `24-tab-conformance.md` D-03/D-05 **delete** `BottomNavBar` + `CompactHomeView` + the whole breakpoint machinery; `33-master-plan-pc.md` 2.9 **keeps** two bands with a 380x620 floor; `13-start-screen.md` 19.2 logs it as unresolved | Blocks W-02 sequencing. See section 2 |
| C-7 | Destination order | `24-tab-conformance.md` 3.1 and `11-app-structure.md` 2.1 = Главная · Серверы · Аккаунт · Настройки; `33-master-plan-pc.md` 1.2 and `10-design-system.md` 6.15 = Главная · Серверы · Настройки · Аккаунт | Both orders are "identical across platforms" claims; they cannot both be |
| C-8 | `Brush.Ring.*` fate | `24-tab-conformance.md` D-06 keeps both keys "only for the one connect-sonar hero moment"; `33-master-plan-pc.md` 5.3.5 deletes both outright in favour of `Brush.Accent` | Minor; affects the token file only |

---

## 13. Work order

Ordered by severity, then by blocking relationship. Every item cites the rule or spec that requires
it. `Assets/GlobalResources.axaml`, `Assets/GlobalStyles.axaml` and `Common/Motion.cs` are owned by
another wave right now; items that need them are marked **[cross-wave]** and must be coordinated,
not edited unilaterally.

### P0

**W-01 - The connect control is not operable without a mouse.**
Files: `Views/ConnectHeroView.axaml` (`:511-584`), `Views/ConnectHeroView.axaml.cs` (`:251-256`),
`Views/MainWindow.axaml.cs` (`MainWindow_KeyDown`, `:1899`).
`#ConnectDisc` is a `Border` with `PointerPressed`/`PointerReleased` handlers only. It has no
`Focusable`, no `IsTabStop`, no `KeyDown`, no focus adorner, no `AutomationProperties.Name`, no
tooltip, and `Ctrl+Enter` is not bound anywhere in the window. The single action the application
exists to perform cannot be reached by keyboard or announced by a screen reader.
Change: make the connect object `Focusable="True" IsTabStop="True"`, activate on `Space` and `Enter`,
draw the 2px `Brush.Accent` focus ring at 2px offset (circle, radius 90), bind `Ctrl+Enter` at window
level, add `AutomationProperties.Name` bound to the state word ("Кнопка подключения. Состояние:
отключено"), add the tooltip «Подключить (Ctrl+Enter)» / «Отключить (Ctrl+Enter)», and set
`IsEnabled=False` (not merely `Opacity` 0.38) in the gated and no-server states.
Spec: `00-rules.md` 7.1 / 10.7 / 12.2 / 14.3 / 14.8; `33-master-plan-pc.md` 5.3.3, 5.9.
Risk: low. The press physics live on the same element and are unaffected; adding focus must not
re-introduce the `RenderTransformOrigin` trap documented at `GlobalStyles.axaml:711-716`.

### P1

**W-02 - Delete the decorative layers and the two idle loops from the connect object.**
Files: `Views/ConnectHeroView.axaml` (`:341-368` ambient, `:373-396` glow, `:402-432` rings,
`:452-463` echo, `:66-268` their styles), `Views/ConnectHeroView.axaml.cs` (`:913-994` `SetAmbient`
/ `RemoveAmbientLoops` / `SetGlow`, `:996-1034` echo half of `PlaySonar`, `:1077-1110` cold-start
assemble).
Delete `#AmbientSonar`, `#AmbientRing`, `#GlowHalo`, `#RingOuter`, `#RingInner`, `#RingHoverGlow`,
`#SonarPulseEcho`, `Panel.assembling`, and the 230px `#HeroFrame`. Ring, arc and sonar all draw on
the same 176 circle; the ring becomes the disc's own 1.5px border. Keep `#SonarPulse`, the arc, the
wind-up, the press physics, `MotionSuppressed` and the live `MotionState` subscription.
Spec: `00-rules.md` 1.4.3, 8.1, 8.9; `33-master-plan-pc.md` 5.3.1, 5.3.5, 2.12.2;
`24-tab-conformance.md` D-06.
Risk: medium. `13-start-screen.md` 16.4 asks for a `Size.ConnectFrame` 200 that 2.12.2 forbids (C-4)
- build to 2.12.2. Requires `Brush.OutlineStrong`, which does not exist yet **[cross-wave]**.
Deleting markup without deleting the matching code-behind state machines is the failure
`33-master-plan-pc.md` 5.3 names explicitly.

**W-03 - Remove every non-hero `Brush.Ring.*` consumer; one ring per confirmation.**
Files: `Views/ConnectHeroView.axaml` (`:352`, `:367`, `:408`, `:423`, `:431`, `:462`).
Six of seven consumers are illegal. `#SonarPulseEcho` in particular makes the product's one hero
moment emit two rings, against 5.3.6 and `13-start-screen.md` 12.3 and its own acceptance line
"exactly one ring is emitted per confirmation".
Spec: `00-rules.md` 1.4.3; `33-master-plan-pc.md` 5.3.6.
Risk: low. Subsumed by W-02; listed separately because it is the item most likely to be
half-completed.

**W-04 - Delete `Brush.HomeGradient` and the two navigation scrims from this surface.**
Files: `Views/HomeView.axaml:16`, `Views/MainWindow.axaml:434`, `:551`, `:581-587`;
`Views/BottomNavBar.axaml:24-27`, `:130`.
All become flat `Brush.Bg`. `Nav.Scrim` and the `navScrim` `OpacityMask` are deleted outright.
Spec: `00-rules.md` 1.4.3, 6.5; `24-tab-conformance.md` 3.1.4; `13-start-screen.md` 16.4.
Risk: low. `Brush.HomeGradient` has five further consumers outside this surface
(`LoginView.axaml:237`, `AccountSyncView.axaml:47`, `OnboardingView.axaml:43`) - the token can only
be dropped from the dictionary once they are all migrated **[cross-wave]**.

**W-05 - Reduce the accent to zero at rest.**
Files: `Views/ConnectHeroView.axaml` (`:645`, `:670` arrows; `:727` protocol chip),
`Views/HomeAccountChip.axaml:89`.
After W-02 removes the four blue rings, the remaining blue at rest is the two arrows, the protocol
chip label and the avatar initial. Arrows become `Geo.Action.ArrowUp/Down` glyphs in
`Brush.OnSurfaceVariant`; the protocol chip uses `Border.ProtocolChip`'s own neutral treatment; the
account chip is deleted by W-11.
Spec: `00-rules.md` 3.6, 2.4.1; `13-start-screen.md` 1; `33-master-plan-pc.md` 5.10.
Risk: low.

**W-06 - Rebuild the numeric strip to 2.12.3 and gate it on connected.**
Files: `Views/ConnectHeroView.axaml:632-680`, `Views/ConnectHeroView.axaml.cs:191`, `:399-401`,
`:416-418`, `:461-468`, `:626`; `ViewModels/HomeViewModel.cs:103-107`, `:355-356`, `:366-368`.
Three equal columns, **download first**, then uptime, then upload; all three `Classes="Numeric"` at
16/500 with no inline `FontSize`; unit **inside** the value string (`24,8 Мбит/с`, comma decimal,
`tnum lnum zero`); a 16px glyph then an 8 gap then the value; the whole strip visible **only** while
connected; each column reserves its width from the 620/1000 tabular advance. Stop hiding the row
under reduced motion - `IsVisible` must not depend on `ReducedMotion`.
Spec: `00-rules.md` 1.1 (hero-metric), 1.4.4, 5.5, 9.2, 12.1, 8.8; `33-master-plan-pc.md` 2.12.3, 5.5.
Risk: medium. `Utils.HumanFy` returns `KB/s`-style strings; a Russian bits-per-second formatter is
new code in `HomeViewModel`, not a view change.

**W-07 - Fix the connect-state vocabulary and the colour signal.**
Files: `Common/L.Home.cs:23-24`, `Common/L.Shell.cs:34-37`,
`Views/ConnectHeroView.axaml.cs:341-419`.
Six words, keyed identically on both platforms: `Отключено` (idle), `Подключение…`, `Подключено`,
`Отключение…` (new state, new key, new enum member), `Не подключено` (**failure only**),
`Сервер не выбран`. Add the state dot as the second channel, or resolve C-1 first. Connected must
read green, not accent.
Spec: `00-rules.md` 6.2, 6.3, 13; `33-master-plan-pc.md` 2.12.1.
Risk: medium. Blocked on C-1 (dot yes/no). The word change alone is unblocked and is a straight
parity defect.

**W-08 - Add the missing twelve states.**
Files: `Views/HomeView.axaml`, `Views/ConnectHeroView.axaml.cs`, `ViewModels/HomeViewModel.cs`.
`Disconnecting`, first-run signed-out, first-run signed-in-no-subscription, loading skeletons,
offline, partial, gated/expired, long-content wrap, plus a real disabled disc in the no-server case.
Each needs a state on the view model, not a branch in the view: `33-master-plan-pc.md` 5.8's fifteen
rows and `13-start-screen.md` 15.4's `HomeUiState` are the same object.
Spec: `00-rules.md` 15, 17.2; `33-master-plan-pc.md` 5.8.
Risk: high. This is the largest item and it is the one that turns Home from a shield into a screen.

**W-09 - Add the gate line, the status strip, the server row and the subscription card.**
Files: `Views/HomeView.axaml` (new), `Views/Home/StatusStrip` (new, shared with the shell).
2.12.3's composition is seven elements in one order; four of them do not exist. The status strip
replaces both inlined TUN banners with one component and carries the six prioritised conditions.
Spec: `33-master-plan-pc.md` 2.12.3, 5.4, 5.6, 5.7; `13-start-screen.md` 8, 9.
Risk: high. Depends on subscription state reaching the desktop Home, which it does not today.

**W-10 - One Home file, single column, capped at 720 and centred, one scroll region.**
Files: `Views/HomeView.axaml`, `Views/CompactHomeView.axaml(.cs)`,
`Views/MainWindow.axaml.cs:19`, `:27`, `:31-33`, `:230-243`, `:483`, `:493-515`, `:700-780`.
Delete the `440 | 1 | *` split - the server list leaves for its own destination. Implement the two
internal bands (single column below 980 of content width, two panes above) inside the one file. One
`ScrollViewer` per view; remove the compact page-scroll-over-list-scroll nesting.
Spec: `00-rules.md` 4.1, 12.3; `33-master-plan-pc.md` 5.2; `24-tab-conformance.md` D-04.
Risk: high, and **sequenced**: see section 2. Do not delete `CompactHomeView` before either the band
exists or the default window changes (C-6, PC-D3). Preserve `CompactHomeView.axaml.cs:104-143`'s
scroll-offset restoration behaviour in whatever replaces it.

**W-11 - Delete `HomeAccountChip` from Главная.**
Files: `Views/HomeAccountChip.axaml(.cs)`, `Views/HomeView.axaml:29-32`,
`Views/CompactHomeView.axaml:48`, `Views/MainWindow.axaml.cs:179`.
The rail already carries the «Аккаунт» destination; a second permanent entrance to one room is the
duplicate-affordance failure. Harvest its skeleton (`:116-128`) into `Border.Skeleton` for the
Аккаунт hero. Its defects die with it: `FontSize="18"` off-ramp (`:87`), `Font.Grotesk` on a
Cyrillic initial (`:86`), fixed `Height="64"` (`:65`), `Radius.Card` 20 on a row.
Spec: `33-master-plan-pc.md` 2.12.3, 5.6; `13-start-screen.md` 16.1 disagrees (C-3).
Risk: medium. Blocked on C-3.

**W-12 - Add the «Серверы» destination; stop hiding «Аккаунт».**
Files: `Views/MainWindow.axaml:456-496`, `Views/BottomNavBar.axaml:131-159`,
`Views/MainWindow.axaml.cs:174-179`, `:483`, `Common/L.Shell.cs:27-29`.
Four destinations, present in every state, in one agreed order. Remove
`ItemAccount.IsVisible="False"`. Add `Nav_Servers`.
Spec: `00-rules.md` 7.7, 13; `24-tab-conformance.md` 3.1.
Risk: medium. Blocked on C-7 (order) and on the Серверы destination existing at all.

**W-13 - Russian strings out of the brand face.**
Files: `Views/BottomNavBar.axaml:78-79`, `Views/MainWindow.axaml:468`, `:480`, `:492` (and `:21`,
`:337`, `:345`), `Views/HomeAccountChip.axaml:86`, `Views/SubscriptionMetaView.axaml:134`, `:253`,
`:268`.
`GlobalStyles.axaml:297-303` already carries the correct blanket `Font.Ui`; these are the local
overrides that defeat it. Nav labels stop using `Classes="Chip"` (a `Font.Brand` role at 11px with
0.04em tracking - the tracked-eyebrow shape) and take a ui-face role. `MainWindow.axaml:21`
`FontFamily="{DynamicResource Font.Grotesk}"` on the `Window` must go: 5.1's enforcement is
"`grep -rn 'FontFamily=' Views/` returns nothing".
Spec: `00-rules.md` 5.1, 5.2, 12.1, D-1, D-2.
Risk: low, high value. A Russian string in the brand face is P1 by 5.1, not a polish item.

**W-14 - Accessible names on every icon-only control.**
Files: all eleven; currently **0** `AutomationProperties` in the set. `btnMin`, `btnMax`, `btnClose`,
`btnRailToggle`, `railStatusDot`, `CornerAddButton`, every nav item, the connect disc.
Spec: `00-rules.md` 10.7, 14.3.
Risk: low.

**W-15 - Press scale 0.92 becomes 0.97 on both navigations.**
Files: `Views/BottomNavBar.axaml:62`; `Assets/GlobalStyles.axaml:793` **[cross-wave]**.
Spec: D-11.
Risk: low.

**W-16 - Motion tokens: remove every off-scale duration and every ease-in-out.**
Files: `Views/ConnectHeroView.axaml:25-304`, `Views/ConnectHeroView.axaml.cs:1040-1075`, `:149-154`.
After W-02 deletes the ambient and glow loops, what remains to fix is: arc spin 1200 > `Dur.Spin`
1100; hover 120 > 150 `Ease.Standard`; connect bloom (180/260, peak 1.04) and error contract
(150/150) deleted - a scale peak above rest is overshoot and 8.3 bans it, and 5.3.6 says "nothing
else moves".
Spec: `00-rules.md` 3.7, 8.2, 8.3; `33-master-plan-pc.md` 2.12.2.
Risk: low.

**W-17 - Rebuild the first-run / empty layer as a state of Главная with one filled CTA.**
Files: `Views/ConnectHeroView.axaml:764-837`, `Common/L.Home.cs:31-41`,
`Views/MainWindow.axaml:596` (`OnboardingView`).
`LayerEmpty` today shows a `Headline` «Приветствуем!» (5.10 forbids `Headline` on this screen), then
`Classes="Primary"` **and** `Classes="Tonal"` - two filled CTAs, the exact defect the owner rejected
in `layout_home_empty.xml`. Replace with `Border.EmptyIcon` + title + one line + one
`Button.Primary` + one `Button.Text`. Delete `Home_Welcome`, `Home_NoSubs`, `Home_NoSubsHint`.
Spec: `00-rules.md` 4.3, 9.5; `33-master-plan-pc.md` 5.8, 5.10; `24-tab-conformance.md` 3.1.9.
Risk: medium. Must preserve W-19's snapshot semantics.

**W-18 - Two upstream views still leak the default Semi look and English copy.**
Files: `Views/MsgView.axaml`, `Views/SudoPasswordInputView.axaml`.
Both render `x:Static resx:ResUI.*` strings, `Theme="{DynamicResource CardBorder}"`,
`SimpleToggleSwitch`, `Classes="IconButton Success"` and unstyled `Button`s with `Width="100"`.
`SudoPasswordInputView` is reachable (`StatusBarView.axaml.cs:202`) and is the **first thing a Linux
user meets** when TUN needs elevation (`33-master-plan-pc.md` 5.7 / 9.3). `MsgView` is registered in
`SimpleViewLocator.cs:26` but has no reachable route in the current shell - confirm before deleting.
Spec: `00-rules.md` 1.4.10, 12.1.
Risk: low for `SudoPasswordInputView` (restyle); `MsgView` needs a reachability decision first.

**W-19 - `StatusBarView` is a 0x0 hidden host carrying live interaction handlers.**
Files: `Views/MainWindow.axaml:643-651`, `Views/StatusBarView.axaml`, `.axaml.cs`.
The view is mounted at `Width="0" Height="0" Opacity="0" IsHitTestVisible="False"` purely to keep its
handlers (clipboard, tray icon, sudo password) and its view model alive. Its second row - the
routing-mode banner with two **hard-coded Russian literals** (`:101` «Режим:», `:114`, `:120`) - can
never be seen; two visible copies of the same banner were pasted into `HomeView.axaml:48-74` and
`CompactHomeView.axaml:54-79` instead.
Change: move the handlers to a non-visual owner, delete the dead markup, and replace all three banner
copies with one `Border.StatusStrip.warning` component (W-09).
Spec: `00-rules.md` 1.3 (inconsistent component vocabulary), 12.1; `33-master-plan-pc.md` 5.7.
Risk: medium. The handlers are load-bearing on Linux; move them before deleting anything.

### P2

**W-20 - Off-scale spacing: 29 hits.**
Files and values in section 8. The two TUN-banner copies contribute four of them and are fixed for
free by W-19. `StatusBarView`'s three `Margin="1"` spacers and `MsgView`'s `Margin="2"` are upstream
leftovers.
Spec: `00-rules.md` 1.4.5, 3.1.
Risk: low.

**W-21 - Two raw colour literals.**
`Views/ConnectHeroView.axaml:526` `Fill="#000000"` becomes `Brush.Scrim`;
`Views/MainWindow.axaml:308` `Background="#B3000000"` becomes the scrim token.
Spec: `00-rules.md` 1.4.6; `24-tab-conformance.md` D-06 names `:526` explicitly.
Risk: low.

**W-22 - Em-dash in shipped copy.**
`Common/L.Home.cs:40` `Onboarding_Subtitle`. The string is deleted by W-17; if W-17 slips, fix the
dash independently.
Spec: `00-rules.md` 1.4.11, 9.7.
Risk: none.

**W-23 - Default window size and minimums.**
`Views/MainWindow.axaml:13-16`. Target 1080x720 default, 900x600 wide minimum, 380x620 compact
minimum (`33-master-plan-pc.md` 2.9). Today the desktop opens as a 372px phone strip, which means the
two-pane layout the team built is the layout nobody sees.
Spec: `00-rules.md` 12.3; `33-master-plan-pc.md` 2.9 (**PC-D3**, un-ratified).
Risk: medium, and it is the lever that unblocks C-6 / W-10. Owner decision required.

### P3

**W-24 - Fixed heights that should be minimums.**
`GlobalStyles.axaml:391` `Button.Primary Height="48"`, `:753` `Button.NavRailItem Height="64"`,
`Views/HomeAccountChip.axaml:65` `Height="64"`. A fixed height clips a two-line label at 200% DPI.
Spec: `00-rules.md` 3.3 (R2), 14.5. **[cross-wave]** for the first two.
Risk: low.

---

## 14. What is right and must not be lost

Recording these so a rebuild does not regress them while chasing the list above.

| Behaviour | Evidence |
|---|---|
| Zero `StaticResource` on a theme brush across the whole file set | live theme switching works |
| Live 3-theme re-resolution of snapshot brushes | `ConnectHeroView.axaml.cs:512`, `:616-617` - the idle caption used to capture a near-white dark brush and vanish on the light theme |
| Reduced motion read **live**, never once in a constructor | `:497`, `:608`, `:619-648`; `MotionState.Changed` is the contract `Common/MotionState.cs` exists to enforce |
| Infinite loops stopped when the window is hidden **and** when the layout is inactive | `MotionSuppressed` at `:112`, `Deactivate()` at `:585`, `UpdateVisibilityPause()` at `:547` |
| The hero moment does not re-fire on a rebind | `HomeHeroPresenter.cs:26-32`, `:144-148` (`_firstApply`) |
| Error latched distinctly from an ordinary disconnect | `HomeHeroPresenter.cs:132-138` |
| One connect pipeline shared by both layouts | `HomeHeroPresenter.Bind`, called from both hosts |
| Keep-alive tab host, no reparenting, no first-layout under the transition frame | `MainWindow.axaml.cs:225-235`, `:483` |
| Scroll offset preserved across tab switch and minimise/restore | `CompactHomeView.axaml.cs:104-143` |
| Travelling nav indicators rather than per-item pills | `MainWindow.axaml:500-509`, `BottomNavBar.axaml:166-177` |
| Onboarding gate snapshot | section 11.1 |
| Autostart reconciliation | section 11.2 |
