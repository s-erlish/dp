# 13 - Start screen (Главная)

> ## ⚠ OWNER OVERRULE, 2026-07-26 — this file's SCOPE is void; only its VISUAL language survives
>
> This document specifies Главная "from nothing", and that is exactly what it must not do. The owner
> has ruled, twice and then a third time on seeing the result:
>
> - «как выглядела главная по функционалу такая и должна остаться, ЧТО НА ПК, что на андроиде»
> - «а что стало с главной на андроиде? оно же выглядело по другому»
> - «и дизайн кнопки другой совсем, хотя был другой раньше и анимация была»
>
> A build made from this file replaced Главная's inline **server list** and **subscription card**
> with two navigation rows, and replaced the connect visual — concentric rings, the country flag,
> the running animation — with a bare disc. That is a regression, and it was shipped because this
> file was read as authority over what the screen contains. It is not.
>
> **What Главная contains does not change.** The speed and uptime strip with its add action, the
> connect object with its rings, its flag and its animation, the subscription card with the provider
> name, traffic, auto-update time and its actions, and the server list inline beneath it — all of it
> stays, on both platforms.
>
> **What this file still governs:** tokens, type, spacing, colour, state treatment and motion timing
> for the elements that are already there. Sections 4, 5 and 8, which redesign the component tree and
> replace the list and the card with ledger rows, are void.
>
> The rule, restated because it was expensive: **Главная is a restyle.** Same controls, same
> capabilities, same information, drawn better. Anything else needs the owner to ask.

**Departament VPN. The screen the app opens on, specified from nothing, for Android first and then
for the desktop client.**

| | Android | Desktop |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Paths below are relative to | `/home/user/dp/V2rayNG/app/src/main/` | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/` |
| Route (`11-app-structure.md` 7.1) | `home` | `home` |

**Precedence.** `00-rules.md` is law. `03-direction.md` outranks this file on any question of what
the product is. `10-design-system.md` outranks it on any token, component or state. `11-app-structure.md`
outranks it on navigation and on what this destination owns. This file outranks nothing: it is the
implementation contract for one screen, and where the four documents above disagree with each other
it records the resolution in section 19 rather than inventing a third answer.

**Inputs read before writing this.** `00-rules.md`, `01-inventory-android.md`, `02-inventory-pc.md`,
`03-direction.md`, `10-design-system.md`, `11-app-structure.md`, `32-master-plan-android.md`
(sections 1, 2, 6, 7, 8), `33-master-plan-pc.md` (sections 2.6, 2.9),
`.claude/skills/impeccable/SKILL.md`, `reference/product.md`, `reference/android.md`,
`reference/animate.md`, and the source: `res/layout/activity_main.xml` (705 lines),
`res/layout/layout_home_account.xml`, `res/layout/layout_home_empty.xml`,
`res/layout/layout_subscription_meta_bar.xml`, `ui/MainActivity.kt` (2 777 lines, in particular
`applyRunningState()` at :1621, `applyConnectedState()` at :1649, `applyIdleState()` at :1712),
`Views/HomeView.axaml`, `Views/CompactHomeView.axaml`, `Views/ConnectHeroView.axaml` (839 lines) and
`.axaml.cs` (1 156 lines), `Views/HomeAccountChip.axaml`, `Views/OnboardingView.axaml`,
`Common/L.Home.cs`.

**A note on dashes.** `00-rules.md` 1.4.11 and `03-direction.md` F19 forbid em-dashes and en-dashes
in UI copy and in these documents. This file contains none. Hyphen only.

---

## 0. How to use this document

Sections 1 to 3 say what the screen is and what dies with it. Sections 4 to 10 are the composition,
block by block, with every dp value. Section 11 is the state matrix and is the acceptance surface:
a screen that has not been looked at in every row of section 11 is not finished. Section 12 is the
motion, section 13 is every string, section 14 accessibility, section 15 the Android file map,
section 16 the desktop build of the same screen, section 17 the parity contract, section 18 the
checklist, section 19 the decisions.

An implementer must never have to make a visual judgement call. If this document leaves one open,
that is a defect in this document; log it in section 19.

---

## 1. The job

**One glance has to answer three questions, in this order:**

1. **Am I protected?** The state of the tunnel.
2. **Through what?** The identity of the server carrying it.
3. **What do I do next?** Exactly one thing, and only when there is something to do.

That is the whole brief. The scene sentence (`03-direction.md` 2.1) is a man standing on a commuter
train with four seconds and one thumb, and it sets the bar: the answer to question 1 must be
readable at arm's length in a dark carriage at 30 percent brightness, and the action for question 3
must be a target he cannot miss with a gloveless thumb.

**What this screen is not.** It is not a dashboard, not a server browser, not a subscription manager
and not a place anyone browses. Servers are a destination (`11-app-structure.md` 2.3). The
subscription is an object that lives on Аккаунт. Главная holds a pointer to each and nothing more.

**The one lit element** (`03-direction.md` 3.2) is the connect disc when the tunnel is up, or the
gate CTA when the screen is gated. Never both, never anything else. On the disconnected, ungated
screen the count of accent pixels is **zero**.

**No Display figure.** The 34sp Display role does not appear on this screen. The hero is the disc,
not a number. Three 34sp readouts would be the banned hero-metric template
(`00-rules.md` 1.1, `03-direction.md` 7.3).

---

## 2. What this replaces, and what dies with it

### 2.1 Android

| File | Lines | Verdict |
|---|---|---|
| `res/layout/activity_main.xml` lines 40 to 517 (the `group_home` `NestedScrollView`) | ~478 | **REBUILD** as `res/layout/fragment_home.xml` |
| `res/layout/layout_home_account.xml` | 155 | **DELETE.** Its signed-out half (`group_login`) is dead code: `updateAccountGate()` (`ui/MainActivity.kt:1079`) sets `header.groupLogin.isVisible = false` unconditionally. Replaced by the header row in section 10 |
| `res/layout/layout_home_empty.xml` | 139 | **DELETE.** This card is the real sign-in screen for 100 percent of new users today, and it is the screen the owner called out. First run is a state of Home (section 9), not a card floating on a gradient |
| `res/layout/layout_subscription_meta_bar.xml` | 257 | **DELETE.** Moves to Серверы as the provider group header (`11-app-structure.md` 4.2). Its traffic pill prints an 11sp label on top of a moving accent fill at 2.9:1 |
| `res/layout/toast_status.xml`, `res/drawable/bg_toast_status.xml` | 2 files | **DELETE.** Replaced by the status strip (section 9) and by `Snackbar` |
| `res/drawable/bg_home_gradient.xml`, `drawable-night/bg_home_gradient.xml`, `bg_home_gradient_mono.xml` | 3 files | **DELETE.** Banned decorative gradient |
| `res/drawable/bg_connect_glow.xml`, `bg_connect_glow_mono.xml` | 2 files | **DELETE.** Banned glow. Its 850ms infinite-reverse breathe (`ui/MainActivity.kt:1741`) dies with it |
| `res/drawable/bg_bottom_nav_scrim.xml`, `bg_nav_header.xml`, `nav_header_bg.png` | 3 files | **DELETE.** Banned gradients, two of them orphans |
| `res/anim/shield_assemble.xml` | 1 file | **DELETE.** An entrance flourish on a screen that must not perform (`03-direction.md` 8.5) |
| `card_memory` and its `PREF_SHOW_MEMORY` gate (`activity_main.xml:307`, `ui/MainActivity.kt:1818`) | | **DELETE.** A debug readout gated by a preference that has no UI |
| `tv_home_welcome` («Приветствуем!») | | **DELETE.** `11-app-structure.md` 5.4 |

The 230dp `hero_frame` stack (`activity_main.xml:200` to `:290`) is five layers - radial glow, ring,
one-shot sonar, a 212dp `CircularProgressIndicator`, a 176dp `MaterialCardView` at 88dp radius with
two stacked 80dp shields - used to communicate one boolean. Four of the five carry no information.
Section 5 rebuilds it as **one disc, one ring, one glyph**.

### 2.2 Desktop

| File | Lines | Verdict |
|---|---|---|
| `Views/HomeView.axaml` | 74 | **REBUILD** (section 16) |
| `Views/CompactHomeView.axaml` + `.axaml.cs` | 94 | **DELETE.** One Home view with two internal layout bands replaces two views. This is correct whether or not the shell keeps a compact mode, so it does not depend on the unresolved conflict in section 19.2 |
| `Views/ConnectHeroView.axaml` + `.axaml.cs` | 839 + 1 156 | **DELETE.** Replaced by `Views/ConnectDiscView.axaml` (target: at most 140 lines of markup, 260 of code-behind) |
| `Views/OnboardingView.axaml` + `.axaml.cs` | 238 + 213 | **DELETE.** `11-app-structure.md` 5.1: onboarding is not a surface |
| `Views/HomeAccountChip.axaml` + `.axaml.cs` | 131 + 233 | **RESTYLE** into the header row of section 10. Fix `FontSize="18"`, which is not in the ramp |
| `Views/ServerListView.axaml`, `Views/SubscriptionMetaView.axaml` | 313, 335 | **MOVE** to Серверы. They are not part of Главная |
| `Brush.HomeGradient`, `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`, `Size.HeroFrame` 230, `Size.ConnectArc` 212 (`Assets/GlobalResources.axaml`) | | **DELETE** from the token dictionary |

---

## 3. The frame

```
?attr/colorBackground  #0A0B0D          <- P0 ground, edge to edge, one flat colour, no drawable
├─ status-bar inset  (applied as paddingTop on the header row only)
├─ [56]  Header row: account                          section 10
├─ [ 0 or 48 ]  Inline status strip, when a condition applies   section 9
├─ [24]
├─ [200] Connect frame, holding the 176 disc          section 5
├─ [16]
├─ [ >=42 ] Status line                               section 6
├─ [24]
├─ [ >=44 ] Numeric strip                             section 7
├─ [32]
├─ [ 112 or gate ] Ledger rows, or the gate block     section 8
└─ [24]  bottom padding.  The bottom navigation is a sibling in the shell, not an overlay,
         so this fragment does not consume the navigation-bar inset
```

**Gutter** 16dp (`@dimen/screen_gutter`), 24dp at `sw600dp`. **Content max width** 720dp, centred
(`00-rules.md` 4.1); on a phone this never binds.

**The rhythm is four values and they are not interchangeable** (`10-design-system.md` 2.5):
4/8/12 inside an object, 16 between the disc and its label, 24 between blocks, and **32 exactly
once**, after the hero, before the ledger. That single 32 is what makes the disc read as a hero and
the rows read as a footer. A screen where every gap is 16 fails the squint test and is the most
common defect in the current build.

**Total at font scale 1.0 on a 360 x 800dp phone:** 56 + 24 + 200 + 16 + 42 + 24 + 44 + 32 + 112 + 24
= **574dp** against 688dp of content height (800 minus a 24dp status bar minus a 64dp navigation bar
minus a 24dp gesture inset). The screen does not scroll at default settings, with 114dp of slack,
which is exactly enough to absorb the 48dp status strip plus its 12dp gap without scrolling. At
font scale 200 percent it scrolls. It is a `NestedScrollView` with `fillViewport="true"` in every
case; there is exactly one scroll region on this screen.

**Nothing is vertically centred and nothing is weighted.** The stack is top-aligned and the rhythm
is fixed, so the disc sits at the same y on a 640dp screen and a 900dp screen, and moving between
two phones does not move the control. The 176dp disc is its own reachability argument: any thumb
that reaches the lower half of the screen reaches the disc.

---

## 4. Component tree (Android)

Real ids, real styles, real drawables. `@style/...` and `@dimen/...` names are those of
`10-design-system.md` 4.3 and 4.5.

```
NestedScrollView  #home_scroll
  fillViewport=true, scrollbars=none, background=?attr/colorBackground
  clipChildren=false, clipToPadding=false          <- the confirm ring paints outside its frame
└─ LinearLayout (vertical)  #home_content
     paddingBottom=@dimen/space_24, clipChildren=false
     NO horizontal padding: rows bleed to the screen edge so their ripple does, and each row
     carries its own paddingHorizontal=@dimen/screen_gutter. Every non-row block below sets
     layout_marginHorizontal=@dimen/screen_gutter instead.

   ├─ include @layout/row_account  #row_account                      [56] section 10
   │     full bleed, paddingHorizontal=@dimen/screen_gutter inside the row
   │
   ├─ View #header_hairline                                          [1] alpha 0 at rest
   │     background=?attr/colorOutlineVariant
   │
   ├─ include @layout/status_strip  #status_strip                    [48] section 9, visibility=gone
   │     layout_marginTop=@dimen/space_12, layout_marginHorizontal=@dimen/screen_gutter
   │
   ├─ Space  #gap_hero        height=@dimen/space_24
   │
   ├─ FrameLayout  #connect_frame                                    [200 x 200]
   │     layout_gravity=center_horizontal, clipChildren=false
   │  ├─ View  #connect_ring_pulse      176x176, gravity=center, INVISIBLE   section 5.4
   │  ├─ View  #connect_disc            176x176, gravity=center             section 5.1
   │  ├─ View  #connect_ring            176x176, gravity=center             section 5.2
   │  ├─ CircularProgressIndicator #connect_sweep  gravity=center, GONE     section 5.3
   │  ├─ ImageView #shield_outline      80x80,  gravity=center
   │  └─ ImageView #shield_filled       80x80,  gravity=center, alpha=0
   │
   ├─ Space  height=@dimen/space_16
   │
   ├─ LinearLayout (vertical)  #status_line   gravity=center_horizontal     section 6
   │  ├─ TextView #tv_status         @style/TextAppearance.App.Title
   │  └─ TextView #tv_status_detail  @style/TextAppearance.App.Subtitle
   │        layout_marginTop=@dimen/space_4, marginHorizontal=@dimen/space_24
   │
   ├─ Space  height=@dimen/space_24
   │
   ├─ LinearLayout (horizontal)  #numeric_strip  minHeight=44dp             section 7
   │     layout_marginHorizontal=@dimen/screen_gutter
   │     baselineAligned=false, INVISIBLE at rest (never GONE)
   │  ├─ LinearLayout (vertical, weight 1)   value #tv_down   label #tv_down_label
   │  ├─ LinearLayout (vertical, weight 1)   value #tv_up     label #tv_up_label
   │  └─ LinearLayout (vertical, weight 1)   value #tv_ping   label #tv_ping_label
   │
   ├─ Space  height=@dimen/space_32
   │
   ├─ LinearLayout (vertical)  #ledger                                      section 8.1
   │     full bleed; each row carries paddingHorizontal=@dimen/screen_gutter
   │  ├─ include @layout/row_universal  #row_servers        [56]
   │  ├─ View  divider  height=1dp  marginStart=@dimen/divider_inset_start(68)
   │  │        background=?attr/colorOutlineVariant
   │  └─ include @layout/row_universal  #row_subscription   [56]
   │
   └─ LinearLayout (vertical)  #gate                                        section 8.2
          visibility=gone, gravity=center_horizontal
          layout_marginHorizontal=@dimen/screen_gutter
       ├─ TextView       #tv_gate_caption   @style/TextAppearance.App.Body
       ├─ Space          height=@dimen/space_16
       ├─ MaterialButton #btn_gate_primary  @style/Widget.App.Button   [52]
       ├─ Space          height=@dimen/space_12
       └─ MaterialButton #btn_gate_secondary @style/Widget.App.Button.Text [48]
```

`#ledger` and `#gate` are mutually exclusive and occupy the same slot. Exactly one is visible at any
time, and the rule that picks is in section 8.3.

---

## 5. The connect object

One disc. One ring. One glyph. Three states carried by colour on a geometry that never changes.

### 5.1 The disc

| Property | Value |
|---|---|
| Size | **176 x 176dp** (`@dimen/connect_disc`) |
| Shape | oval, `@dimen/radius_pill` (clamps to a circle) |
| Fill | `?attr/colorSurfaceContainerHighest` (P3 inset, `#20242B` dark, `#E3EAF4` light) |
| Elevation | 0. No shadow, no glow, no gradient, in any theme |
| Drawable | `res/drawable/bg_connect_disc.xml`: `<shape android:shape="oval">` with a `<solid>` only |
| Touch target | the disc itself, 176dp. The surrounding 200dp frame is **not** clickable |
| Ripple | `?attr/colorPrimary` at 10 percent, bounded to the oval, via a `<ripple>` wrapper with a matching `<mask>` |
| Press | `android:stateListAnimator="@anim/press_scale"`, scale 0.97, 90ms in `ease_out_quart`, 160ms out `ease_out_quint` |

P3 is the correct plane: the disc is **recessed into the panel**, not an object floating on it
(`03-direction.md` 4.1). It is the same tone as an input field and a chip, and it is the reason the
control reads as part of the instrument rather than as a widget dropped onto it.

### 5.2 The ring

A separate stroke-only oval, painted over the disc edge so its 3dp stroke straddles the boundary.

| Property | Value |
|---|---|
| Size | 176 x 176dp, same centre as the disc |
| Drawable | `res/drawable/bg_connect_ring.xml`: `<shape android:shape="oval">` with a `<stroke android:width="@dimen/stroke_ring" android:color="#FFFFFF"/>` and no `<solid>` |
| Width | **3dp** (`@dimen/stroke_ring`) in every state. Only the colour changes |
| Tint | `android:backgroundTint`, animated with an `ArgbEvaluator` over `motion_state` 220ms |

| State | Tint (dark) | Measured contrast |
|---|---|---|
| Disconnected, ready | `?attr/colorOnSurfaceDim` `#6E7480` | **4.19:1** against the ground `#0A0B0D`, **3.32:1** against the disc fill `#20242B`. Both edges clear the 3:1 UI-component-boundary floor (WCAG 1.4.11) |
| Connecting | unchanged `#6E7480`, and it serves as the track under the sweep | as above |
| Connected | `?attr/colorPrimary` `#4C8DFF` | **6.15:1** on the ground, **4.87:1** on the disc fill |
| Error | `?attr/colorError` `#F04452` | **5.30:1** on the ground, **4.19:1** on the disc fill |
| Disabled / gated | `?attr/colorOutline` `#2A2E36` | 1.45:1, deliberately below the floor. WCAG 1.4.11 exempts inactive components, and a disabled control should recede |

Light theme, same tokens: idle `#3C475E` measures **8.67:1** on `#F4F7FC` and **7.69:1** on
`#E3EAF4`; connected `#1E5FC7` measures **5.56:1** on the ground and **4.93:1** on the disc fill.
Mono dark: idle `#8A8A90` measures **6.12:1** on `#000000` and **4.57:1** on `#232326`.

**The ring never changes width.** A ring that thickens or thins between states makes the control
appear to change size, which is a layout animation in disguise. `10-design-system.md` 2.8 lists both
`stroke_ring` 3 and `stroke_emphasis` 2 against the connect ring, and `11-app-structure.md` 4.1 says
1dp; the resolution is in 19.1 (decision S-2).

### 5.3 The connecting sweep

`com.google.android.material.progressindicator.CircularProgressIndicator`, `#connect_sweep`.

| Property | Value |
|---|---|
| `app:indicatorSize` | **176dp** (the track's centre path radius is 88dp, exactly the disc edge, so the arc travels the ring) |
| `app:trackThickness` | **3dp** (`@dimen/stroke_ring`), identical to the static ring |
| `app:trackColor` | `@android:color/transparent`. The static ring underneath is the track |
| `app:indicatorColor` | `?attr/colorPrimary` |
| `app:indeterminateAnimationType` | `disjoint` |
| `app:trackCornerRadius` | 2dp |
| `app:showAnimationBehavior` / `hideAnimationBehavior` | `inward` |
| Visibility | `GONE` unless the core is actually negotiating |

**It runs only while the core is negotiating and stops the instant the state resolves**
(`03-direction.md` 8.4). An indeterminate indicator that spins while nothing is happening is a lie
about the system. This is the only looping animation permitted anywhere on this screen.

### 5.4 The confirm ring

`#connect_ring_pulse`, the same `bg_connect_ring` drawable at the same 176dp, tinted
`?attr/colorPrimary`, `INVISIBLE` at rest. It is emitted exactly once, at the instant the tunnel
confirms, by `res/anim/connect_confirm.xml` (section 12.3). It never loops, there is never a second
ring, and it is emitted by no other event in the product.

Because it scales to 1.35 (238dp) it paints outside the 200dp frame. `clipChildren="false"` is
required on `#connect_frame`, `#home_content` and `#home_scroll`.

### 5.5 The shield

Two 80dp `ImageView`s (`@dimen/shield_glyph`), stacked, crossfaded. Never tint-animated: the filled
shield is accent from frame zero and only its alpha moves.

| View | Drawable | Tint | Alpha at rest |
|---|---|---|---|
| `#shield_outline` | `@drawable/ic_shield_outline` | `?attr/colorOnSurfaceVariant` `#9BA1AD`, **6.00:1** on the disc fill | 1 |
| `#shield_filled` | `@drawable/ic_shield_filled` | `?attr/colorPrimary` `#4C8DFF`, **4.87:1** on the disc fill | 0 |

The current build tints the filled shield `?attr/connectedColor` (green `#22C55E`) and animates the
tint from grey. Both are wrong. Green is a **status** colour in this product and never a fill for
the one lit element (`03-direction.md` 5.3); the lit element is blue. Green survives on this screen
in exactly one place: the word «Подключено».

**Disabled / gated:** `#shield_outline` at `alpha 0.38`, `#connect_disc.isEnabled = false`, no
ripple, no press animator.

### 5.6 What the connect object is not

No glow. No halo. No second ring. No page gradient behind it. No breathing loop. No ambient sonar.
No 212dp arc separate from the 176dp ring. No `MaterialCardView` (the disc is a `View` with a
drawable; a card is an object with a boundary, and this is a control). No shadow in any theme.

---

## 6. The status line

Centred. This and the empty state are the only centred text in the product
(`10-design-system.md` 6.12).

```
[ tv_status         Title 16/700, 1 line ]
   4
[ tv_status_detail  Subtitle 13/400, 1 line, ellipsize=end, marginHorizontal 24 ]
```

| Connection state | `tv_status` | Colour | Contrast on ground | `tv_status_detail` |
|---|---|---|---|---|
| Disconnected, ready | «Отключено» | `?attr/colorOnSurface` | 17.88:1 | the current server name |
| Connecting | «Подключение…» | `?attr/colorOnSurface` | 17.88:1 | the current server name |
| Connected | «Подключено» | `?attr/colorTertiary` `#22C55E` | 8.64:1 | the current server name |
| Disconnecting | «Отключение…» | `?attr/colorOnSurface` | 17.88:1 | the current server name |
| Tunnel error | «Не удалось подключиться» | `@color/ping_bad` `#FF6069` | 6.68:1 | «Нажмите, чтобы повторить» |
| No server selected | «Сервер не выбран» | `?attr/colorOnSurface` | 17.88:1 | «Выберите сервер в разделе «Серверы»» |
| Gated (no subscription, expired, no servers) | the gate word, section 11 | `?attr/colorOnSurface` | 17.88:1 | empty, `INVISIBLE` |

**There is no status dot.** The word carries the state and the colour reinforces it, which is two
channels (`00-rules.md` 6.3). A coloured dot beside a word that already says «Подключено» is the
decoration tell named in `03-direction.md` 2.4 ("a status dot next to a label that already carries
the status: delete it"). `11-app-structure.md` 4.1 specifies a dot; the resolution is in 19.1
(decision S-1).

**The status word never animates.** It is swapped instantly, on the frame the state changes. Text
that crossfades is unreadable for the duration of the crossfade, and this is the one word on the
screen that must be legible in four seconds.

**`tv_status_detail` is `INVISIBLE`, never `GONE`**, so its 18dp line is always reserved and the
numeric strip below it never moves.

Server-name rules: 1 line, `ellipsize="end"`, never in the middle; a 60-character remark truncates
at the end. The leading country flag emoji that arrives in provider remarks is stripped before the
string reaches this `TextView` (`32-master-plan-android.md` 6.3); the flag renders as the tile on
the «Серверы» row and never as text.

---

## 7. The numeric strip

Three equal columns. This is signature one of the direction (`03-direction.md` 3.1) and the only
place on this screen where the brand face sets anything.

```
[ column, weight 1 ][ column, weight 1 ][ column, weight 1 ]

 within each column, left aligned:
   value   @style/TextAppearance.App.Numeric.Value    16sp / 500 / brand / tnum lnum zero
     4
   label   @style/TextAppearance.App.Caption          12sp / 400 / ui / onSurfaceVariant
```

| Column | Value example | `android:minWidth` | Label |
|---|---|---|---|
| 1 | `24,8` | `@dimen/strip_value_speed` **48sp** | «Приём, Мбит/с» |
| 2 | `3,1` | `@dimen/strip_value_speed` **48sp** | «Отдача, Мбит/с» |
| 3 | `48` | `@dimen/strip_value_latency` **32sp** | «Задержка, мс» |

**Why the unit lives in the label and not beside the figure.** It keeps the value a pure figure, so
the tabular 620/1000 advance does its job with nothing else on the line; it keeps the column's
maximum content at 5 characters, which is 48sp and therefore still inside a 109dp column at font
scale 200 percent; and it is what a bench instrument does (`03-direction.md` 2.4, anchor 1). A
figure and a unit on one line at 200 percent overflows a phone column, and solving that with a
second layout would be two layouts to maintain.

**Reservation, which is the whole point.** `minWidth` is declared in **sp**, not dp, so the reserve
scales with the text. These two dimensions are the only sp-valued dimensions in the product and they
exist for exactly this reason. A value going from `9,9` to `10,1`, or from `48` to `183`, moves
nothing.

**The strip is `INVISIBLE`, never `GONE`, when it has nothing to show.** That is the mechanism that
guarantees zero reflow at the moment the tunnel confirms, at every font scale, without hard-coding a
height. Its `minHeight` is 44dp and its `layout_height` is `wrap_content`, so at 200 percent the
labels wrap to two lines and the block grows in every state at once.

**When it is visible:** only while **connected**. Not while connecting. During negotiation the
throughput is genuinely zero and there is no latency measurement, and printing `0,0` there would be
a placeholder pretending to be a reading (`03-direction.md` 6.5: never a fake number).
`11-app-structure.md` 4.1 shows the strip appearing at connecting with zeroes; the resolution is in
19.1 (decision S-3).

**Formats** (`10-design-system.md` 2.10):

- Speed: one decimal below 100, none at or above 100. `0,4` / `24,8` / `248`. Comma decimal. The
  unit is fixed at Мбит/с in the label and never switches to Кбит/с mid-session; a real 40 Кбит/с
  renders `0,0`, which is a rounding and not a lie.
- Latency: integer milliseconds, from the last successful probe of the **active** server, refreshed
  every 30s while connected using the existing delay test.
- Before the first probe lands (typically under 2s) the latency value is an **empty string** in a
  box of reserved width, and it fades in over `motion_state` 220ms when it arrives. It is never a
  dash, never a zero, never a spinner.
- If three consecutive probes fail the value stays empty and the condition is raised on the status
  strip: «Сервер не отвечает. Выберите другой сервер.» with the action «Сменить сервер».
- `zero` (slashed zero) is **on** here: all three are technical figures (`10-design-system.md` 2.10).

**No glyphs.** The `↑` and `↓` text arrows in `activity_main.xml:99` and `:139` are typographic
characters used as UI chrome and are banned (`00-rules.md` 1.4.4, `03-direction.md` F10). They are
replaced by words, not by icons: a label already identifies the column, and adding a glyph beside it
doubles the ink for nothing. This removes `ic_speed_up.xml`, `ic_speed_down.xml` and `ic_clock.xml`
from the new-icon backlog in `32-master-plan-android.md` 6.2.

**No dividers between the columns.** Space separates them (`03-direction.md` 10.2).

---

## 8. The ledger slot

One slot, two possible occupants, never both.

### 8.1 The ledger rows

Two universal rows (`10-design-system.md` 6.4), 56dp each, separated by a 1dp
`?attr/colorOutlineVariant` hairline that starts at the 68dp text origin and runs to the screen
edge. No section header: two rows are not a group that needs naming, and a header here would be
chrome (`11-app-structure.md` 4.1).

```
[16][ tile 40, r12, glyph 22 ][12][ text column, weight 1 ][12][ chevron 20 ][16]
                                   Title     Title 16/700
                                   Subtitle  Subtitle 13/400
```

**Row 1, «Серверы».** Tile `@color/icon_tile_neutral` with `@drawable/ic_nav_servers` at 22dp in
`@color/icon_glyph_neutral` (6.00:1). Title «Серверы». Subtitle «15 серверов · 2 провайдера».
Trailing chevron `@drawable/ic_chevron_right` at 20dp in `?attr/colorOnSurfaceVariant` (7.59:1).
Tap switches the shell to the Серверы destination. The whole row is the target.

The subtitle is deliberately **not** the current server name: that name is already directly under
the disc, and repeating it 100dp lower is the decoration tell. This row answers "where do servers
come from and how many are there", which nothing else on the screen answers.

**Row 2, «Подписка».** Tile neutral with `@drawable/ic_subscriptions_24dp` at 22dp. Title «Подписка»
plus, in the two warning states only, an 8dp gap and a status chip on the same line (24dp tall,
radius 12, Chip role 11sp/500, `10-design-system.md` 6.6). Subtitle per state. Trailing chevron 20dp.
Tap opens Аккаунт, scrolled to that subscription (`account`, and `depv://subscription/{uuid}` when
routed).

| Subscription state | Subtitle | Chip | Chip fill / label |
|---|---|---|---|
| Active | «Действует до 14 августа» | none | |
| Trial | «Пробный период до 14 августа» | none | |
| Expiring, under 3 days | «Осталось 2 дня» | «Истекает» | `?attr/warning` at 18 percent / `warningText` |
| Expired | «Истекла 12 июля» | «Истекла» | `?attr/colorError` at 18 percent / `@color/ping_bad` |
| None | «Не оформлена» | none | |

There is no chip in the normal state: a chip that repeats what the title says is banned
(`10-design-system.md` 6.6). Colour is never the only signal here either, because the chip carries
the word and the subtitle states the date.

**Only one trailing element per row.** The state chip lives in the text column, exactly as the
protocol chip does on a server row; the chevron is the single trailing element.
`11-app-structure.md` 4.1 places the chip in the trailing slot; the resolution is in 19.1
(decision S-4).

**The figures inside these subtitles are set in the UI face, not the brand face.** «15 серверов»,
«Осталось 2 дня» and «до 14 августа» are figures inside running Russian phrases, and a sentence
never ripples between two faces (`10-design-system.md` 2.10, the one exception). The only brand-face
text on this screen is the three values in the numeric strip.

### 8.2 The gate block

```
[ tv_gate_caption   Body 14/400, centred, onSurfaceVariant, max 60 characters, wraps to 2 lines ]
   16
[ btn_gate_primary    52dp, full width at the gutter, radius 16, accent fill ]
   12
[ btn_gate_secondary  48dp, full width, text button, accent label ]     optional
```

One filled accent surface, one text button. The primary is the one lit element on a gated screen,
and on a gated screen the disc is disabled, so the accent count is still exactly one.

### 8.3 The rule that picks

**The gate block replaces the rows only when the rows would have nothing true to say**, that is
when there are no servers or no subscription. When servers and a subscription both exist, the rows
show and any actionable condition is carried by the inline status strip instead.

| Condition | Slot shows |
|---|---|
| No account and no servers | gate: «Войдите, чтобы получить серверы Departament.» / «Войти» / «Добавить провайдера» |
| Account, no subscription | gate: «Осталось выбрать тариф.» / «Купить подписку» |
| Account, subscription active, zero servers | gate: «Подписка активна, серверы ещё не загружены.» / «Загрузить серверы» |
| That sync failed | gate: the failure reason as the caption in `@color/ping_bad` / «Повторить» |
| Everything else, including expired and offline | the two ledger rows |

---

## 9. The inline status strip

A **condition**, not an event. Events are `Snackbar`s.

This is the same `StatusStrip` component as `32-master-plan-android.md` 8.10, in its **inline**
placement: in the scroll content, directly under the header row, 12dp below it. The **docked**
placement of the same component (above the bottom navigation, auto-dismissing after 5s) is the
transient surface and is a shell concern, not this screen's.

```
LinearLayout (horizontal, gravity center_vertical)  #status_strip
   minHeight 48dp, background ?attr/colorSurfaceContainerHigh (P2), radius @dimen/radius_control 16
   paddingHorizontal 16dp, paddingVertical 12dp
├─ ImageView  20dp   ic_info / ic_warning / ic_error
├─ Space 12dp
├─ TextView   @style/TextAppearance.App.Body, weight 1, maxLines 2, ellipsize=end
└─ TextView   action  @style/TextAppearance.App.Title in ?attr/colorPrimary,
              minWidth 48dp, minHeight 48dp, paddingHorizontal 8dp, gravity center
```

Height is **48dp**, not 40dp, because the action inside it is a touch target and 48dp is the floor
(`00-rules.md` 7.2). `11-app-structure.md` 8.2 says 40dp; the resolution is in 19.1 (decision S-5).

P2 is correct: a condition bar is transient by definition and P2 is the transient plane
(`32-master-plan-android.md` 2.1). Contrast: body `#F2F4F8` on `#1A1D21` is **15.36:1**; the accent
action `#4C8DFF` on `#1A1D21` is **5.28:1**.

**At most one strip at a time.** Priority order, highest first:

| # | Condition | Text | Action | Glyph |
|---|---|---|---|---|
| 1 | Subscription expired | «Подписка истекла. Продлите её, чтобы подключаться.» | «Продлить» | `ic_error`, `?attr/colorError` |
| 2 | Device limit reached | «Достигнут лимит устройств.» | «Устройства» | `ic_warning`, `?attr/warning` |
| 3 | Offline | «Нет сети. Показаны последние данные.» | «Повторить» | `ic_info`, `?attr/colorOnSurfaceVariant` |
| 4 | Active server silent while connected | «Сервер не отвечает. Выберите другой сервер.» | «Сменить сервер» | `ic_warning`, `?attr/warning` |
| 5 | Subscription expiring in under 3 days | «Подписка заканчивается 14 августа.» | «Продлить» | `ic_warning`, `?attr/warning` |
| 6 | TUN requested but unavailable | the concrete cause | «Как исправить» | `ic_info`, `?attr/colorOnSurfaceVariant` |

The strip never floats, never overlays the disc, never carries the accent as a background, and never
auto-dismisses in this placement. `ic_info`, `ic_warning` and `ic_error` are on the new-icon list at
`32-master-plan-android.md` 6.2.

---

## 10. The header row

The one place `11-app-structure.md` 3.3 spends a tab's 56dp header on data instead of a label,
because the bottom bar already says «Главная» and repeating it would be chrome.

```
[16][ leading slot 40 ][12][ text column, weight 1 ][12][ chevron 20 ][16]
      36 avatar circle          Title 16/700
      centred in the slot       Subtitle 13/400
height 56 (@dimen/row_min_height), whole row is the target, ripple + press_scale
```

**The leading slot is 40dp wide and the avatar is a 36dp circle centred inside it.** That keeps the
text origin at **68dp**, identical to every other row in the product, while honouring
`Size.AvatarChip` 36. A 36dp leading element on its own would put this screen's text origin at 64
and break the one continuity that is visible across the whole app (`03-direction.md` 3.3).

| State | Avatar | Title | Subtitle | Tap |
|---|---|---|---|---|
| Signed in, photo | the photo, circular-masked | `@handle` | «Управление аккаунтом» | Аккаунт |
| Signed in, no photo | `?attr/colorSurfaceContainerHighest` circle, initial in `?attr/colorOnSurfaceVariant` at Title.Medium 16/500 | `@handle` | «Управление аккаунтом» | Аккаунт |
| Signed out | neutral 40dp tile r12 with `@drawable/ic_nav_account` 22dp | «Аккаунт» | «Вход, подписка, устройства» | Аккаунт |
| Loading | two skeleton bars, 16dp and 12dp tall, 40 percent and 70 percent wide, radius 12, static | | | disabled |

**The avatar is neutral, never accent.** The brand does not spend the screen's one accent on
advertising the user's identity (`03-direction.md` 3.2, 5.7).

**The signed-out header is a navigation row, not a call to action.** It does not say «Войти» and it
is not styled as a button. The verb lives on the gate block's primary, once. Two controls saying
«Войти» on one screen is the duplicate-CTA-intent failure and is exactly the defect the owner
rejected in `layout_home_empty.xml` (two competing filled buttons).

**The wordmark does not appear on this screen.** It exists in three places in the product: the
desktop title bar, the sign-in gate, and Настройки > О приложении (`11-app-structure.md` 3.3).

**On scroll**, `#header_hairline` fades from alpha 0 to 1 over `motion_state` 220ms once
`scrollY > 0`, and back at 0. That is the only scroll-linked change on this screen: no colour
change, no elevation, no shadow, no collapsing title (`00-rules.md` 4.8).

---

## 11. States

Every row is a screenshot that must be taken before this screen is called done
(`00-rules.md` 15).

### 11.1 Launch variants (`11-app-structure.md` 5.3)

| Variant | Header | Disc | Status line | Strip | Slot |
|---|---|---|---|---|---|
| **A** never signed in, no servers | «Аккаунт» / «Вход, подписка, устройства» | disabled, ring `colorOutline`, shield 0.38 | «Нет серверов» / detail INVISIBLE | none | gate: «Войдите, чтобы получить серверы Departament.» / «Войти» / «Добавить провайдера» |
| **B** signed in, no subscription | `@handle` | disabled | «Подписки нет» / detail INVISIBLE | none | gate: «Осталось выбрать тариф.» / «Купить подписку» |
| **C** signed in, active, servers present | `@handle` | idle, enabled | «Отключено» / server name | none | ledger rows |
| **D** signed in, expired | `@handle` | disabled | «Подписка истекла» / detail INVISIBLE | strip 1 | ledger rows, Подписка row red with the «Истекла» chip |
| **E** offline | last known | **enabled** | last known | strip 3 | ledger rows, both subtitles marked stale |
| **F** active subscription, zero servers | `@handle` | disabled | «Нет серверов» / detail INVISIBLE | none | gate: «Подписка активна, серверы ещё не загружены.» / «Загрузить серверы» |

**E keeps the disc enabled.** The app does not know better than the OS whether a tunnel can be
raised; refusing to try because a captive-portal check failed is the app being clever at the user's
expense. Account-dependent rows are marked stale with the caption «Данные могли устареть» under the
ledger (`10-design-system.md` 8).

### 11.2 Connection states

| State | Disc | Ring | Sweep | Shield | Status line | Strip slot |
|---|---|---|---|---|---|---|
| Disconnected, ready | P3, enabled | `#6E7480` | gone | outline `#9BA1AD` | «Отключено» / server name | INVISIBLE |
| Connecting | P3, enabled (tap cancels) | `#6E7480` | running | outline `#9BA1AD` | «Подключение…» / server name | INVISIBLE |
| Connected | P3, enabled | `#4C8DFF` | gone | filled `#4C8DFF` | «Подключено» green / server name | **visible, live** |
| Disconnecting | P3, disabled | fading to `#6E7480` | gone | fading to outline | «Отключение…» / server name | fading out |
| Tunnel error | P3, enabled | `#F04452` | gone | outline `#9BA1AD` | «Не удалось подключиться» red / «Нажмите, чтобы повторить» | INVISIBLE |
| No server selected | P3, disabled 0.38 | `#2A2E36` | gone | outline 0.38 | «Сервер не выбран» / «Выберите сервер в разделе «Серверы»» | INVISIBLE |
| Gated | P3, disabled 0.38 | `#2A2E36` | gone | outline 0.38 | the gate word | INVISIBLE |

Tapping the disc while connecting **cancels** the attempt and returns to disconnected. That is
implemented today by the watchdog path (`ui/MainActivity.kt:2004`) and must stay reachable, because a
control that ignores a tap for ten seconds is a control the user stops trusting.

### 11.3 The remaining required states

| State | Behaviour |
|---|---|
| **First run** | Variant A. There is no separate onboarding screen, no welcome heading, no tutorial, no carousel and no modal |
| **Loading** | Only the header row and the Подписка row can be unresolved at first frame. Both render static skeletons after 300ms, never a spinner. The disc, the status line and the strip come from local state and are correct on frame one |
| **Partial** | If the subscription resolves and the account does not, the Подписка row is live and the header stays a skeleton. Never block one on the other |
| **Error, data** | A failed account or subscription fetch does not empty the row: the row keeps its last value, its subtitle turns to `@color/ping_bad` with «Не удалось обновить», and a `Snackbar` carries «Повторить» |
| **Long content** | A 60-character server remark ellipsises at the end on one line under the disc. A 32-character `@handle` ellipsises at the end in the header. The Подписка subtitle wraps to 2 lines and the row grows; it never clips |
| **Short content** | One server and one provider: «1 сервер · 1 провайдер». The layout is unchanged |
| **200 percent font scale** | The disc stays 176dp. Everything else grows and the screen scrolls. Verified: the numeric strip's widest column content is 5 characters at 48sp = 96dp inside a 109dp column, so the strip never clips; its labels wrap to two lines and the block grows in all three columns at once |
| **320dp width** | Columns become 96dp; the 48sp reserve still fits. The gate buttons keep their labels on one line: «Добавить провайдера» at 16sp/700 measures about 170dp |
| **Success** | Connection success is the hero moment of section 12.3 and nothing else. No confetti, no checkmark flourish, no toast |

---

## 12. Motion

Every duration and curve is from `10-design-system.md` 2.11. There are no others.

### 12.1 The rule for this screen

The screen **appears**; it does not perform. There is no entrance animation on Главная, no section
stagger, no page-load choreography, and no idle motion of any kind. The only looping animation
permitted is the connecting sweep, and it runs only while the core is negotiating.

### 12.2 Press

| Beat | Value |
|---|---|
| Finger down on the disc | scale 1.0 to 0.97, **90ms**, `@interpolator/ease_out_quart`, plus the bounded ripple |
| Release | scale 0.97 to 1.0, **160ms**, `@interpolator/ease_out_quint` |
| Haptic | `View.pressHaptic()` on the down beat |

Visible acknowledgement lands inside 90ms, under the 80ms-to-100ms perceived-instant threshold
(`animate.md`, `00-rules.md` 7.3). Every other pressable thing on this screen (both rows, the header
row, the gate buttons, the strip action) uses the identical press language.

### 12.3 Connect: the one hero moment in the product

600ms, once, `motion_emphasis`. Nothing else in the app is allowed this budget.

| t (ms) | What | Duration | Curve |
|---|---|---|---|
| 0 | The tap dispatches `startV2Ray()`. The disc's press-out is already running | | |
| on `isLoading` | `#connect_sweep` becomes `VISIBLE`, alpha 0 to 1 | 220 | `ease_standard` |
| on `isLoading` | `tv_status` swaps to «Подключение…». **No animation** | 0 | |
| **T = 0**, on `isRunning` | `#shield_filled` alpha 0 to 1 **and** `#shield_outline` alpha 1 to 0, simultaneously | 220 | `ease_standard` |
| T = 0 | `#connect_ring` tint `#6E7480` to `#4C8DFF` via `ArgbEvaluator` | 220 | `ease_standard` |
| T = 0 | `#connect_sweep` alpha 1 to 0, then `GONE` | 165 | `ease_standard` |
| T = 0 | `#connect_ring_pulse`: scale 1.0 to **1.35**, alpha **0.6 to 0**, once, then `INVISIBLE` | **600** | `ease_out_quint` |
| T = 0 | `pressHaptic()` (`HapticFeedbackConstants.CONFIRM`), same frame as the shield | | |
| T = 0 | `tv_status` swaps to «Подключено» in `?attr/colorTertiary`. **No animation** | 0 | |
| T = 220 | `#numeric_strip` becomes `VISIBLE`: alpha 0 to 1 **and** translationY 8dp to 0 | 300 | `ease_out_quint` |
| T = 520 | done. Total envelope 600ms | | |

The strip enters on the tail of the ring, inside the same 600ms envelope, because it cannot show a
reading before there is a tunnel to read. That is one state change with two dependent elements, not
choreography.

**Nothing else moves.** The background does not tint or flash. The rows do not shift, because the
strip's slot was reserved in every state. The bottom navigation does not react. The header does not
react. No second ring ever follows the first.

**Reduced motion** (`util/MotionUtils.animationsEnabled()`, `View.reducedMotion()`): the shield is
filled instantly, the ring tint is set instantly, the sweep is removed instantly, **the ring is not
emitted at all**, the strip appears instantly, and the haptic still fires.

### 12.4 Disconnect

Exit is 75 percent of enter, and it emits nothing.

| What | Duration | Curve |
|---|---|---|
| `tv_status` to «Отключение…», then «Отключено». No animation | 0 | |
| `#shield_filled` alpha 1 to 0 and `#shield_outline` alpha 0 to 1 | **165** | `ease_standard` |
| `#connect_ring` tint `#4C8DFF` to `#6E7480` | **165** | `ease_standard` |
| `#numeric_strip` alpha 1 to 0 and translationY 0 to 8dp, then `INVISIBLE` | **225** | `ease_standard` |
| Haptic | `pressHaptic()` on the tap, nothing on the confirm | | |

### 12.5 Everything else on this screen

| Event | What moves | Duration | Curve |
|---|---|---|---|
| Tunnel error arrives | ring tint to `#F04452`; status line text swap with no animation | 220 | `ease_standard` |
| Status strip enters | translationY 8dp to 0 + alpha 0 to 1 | 300 | `ease_out_quint` |
| Status strip exits | reverse | 225 | `ease_standard` |
| Strip message replaced | text crossfade only; the bar does not move | 220 | `ease_standard` |
| Header hairline | alpha 0 to 1 at `scrollY > 0` | 220 | `ease_standard` |
| Skeleton to content | crossfade. The skeleton itself is **static**, it does not pulse | 220 | `ease_standard` |
| Ledger to gate, or back | crossfade in place, both at the same y | 220 | `ease_standard` |
| Latency value first arrival | alpha 0 to 1 | 220 | `ease_standard` |
| Speed values updating | **nothing**. A figure lands; it does not tick, count or animate | 0 | |
| Tab arrival from another destination | the shell's fade-through, 220ms, owned by the shell | 220 | `ease_standard` |

**What must never animate on this screen:** the background, the disc at idle, the ring at idle, the
shield at idle, the wordmark (it is not here), the numbers, the bottom navigation, the header, the
strip's position, and the screen's sections on first paint.

---

## 13. Copy

Every visible string. Russian, sentence case, no final period on labels and buttons, full stops in
sentences, `…` as one character, «ёлочки», hyphen only.

### 13.1 Android, `res/values-ru/strings.xml` (and mirrored into `values/strings.xml`, which becomes Russian per `01-inventory-android.md` 5.4)

| Resource | Russian |
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
| `home_row_servers_value` | %1$s · %2$s |
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
| `home_gate_retry` | Повторить |
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
| `home_action_howto` | Как исправить |
| `home_cd_connect` | Подключить |
| `home_cd_disconnect` | Отключить |
| `home_cd_cancel` | Отменить подключение |

Plurals:

```xml
<plurals name="home_servers_count">
    <item quantity="one">%d сервер</item>
    <item quantity="few">%d сервера</item>
    <item quantity="many">%d серверов</item>
    <item quantity="other">%d сервера</item>
</plurals>
<plurals name="home_providers_count">
    <item quantity="one">%d провайдер</item>
    <item quantity="few">%d провайдера</item>
    <item quantity="many">%d провайдеров</item>
    <item quantity="other">%d провайдера</item>
</plurals>
<plurals name="home_sub_days_left">
    <item quantity="one">Остался %d день</item>
    <item quantity="few">Осталось %d дня</item>
    <item quantity="many">Осталось %d дней</item>
    <item quantity="other">Осталось %d дня</item>
</plurals>
```

Dates render as «14 августа» inside the current year and «14 августа 2027» otherwise. Never a
numeric date on this screen.

**Deleted strings:** `home_welcome_title`, `home_empty_title`, `home_empty_subtitle`,
`home_empty_add_qr`, `home_empty_add_clipboard`, `home_or_sign_in`, `home_not_connected`,
`home_select_server`, `speed_zero`, `connection_connected` («Connected, tap to check connection»),
`connection_not_connected`, `memory_app_usage`.

### 13.2 Desktop, `Common/L.Home.cs`

The same table, registered as `Add("Home_X", "<ru>", "<en>")` triples. Keys map one to one:
`Home_StatusDisconnected`, `Home_StatusConnecting`, `Home_StatusConnected`,
`Home_StatusDisconnecting`, `Home_StatusError`, `Home_StatusNoServer`, `Home_StatusNoServers`,
`Home_StatusNoSubscription`, `Home_StatusExpired`, `Home_DetailRetry`, `Home_DetailPickServer`,
`Home_StripDownLabel`, `Home_StripUpLabel`, `Home_StripPingLabel`, `Home_RowServers`,
`Home_RowSubscription`, `Home_SubActive`, `Home_SubTrial`, `Home_SubExpired`, `Home_SubNone`,
`Home_SubStale`, `Home_ChipExpiring`, `Home_ChipExpired`, `Home_AccountTitle`,
`Home_AccountSubtitle`, `Home_ManageAccount` (exists), `Home_GateSigninCaption`, `Home_GateSignin`,
`Home_GateAddProvider`, `Home_GateBuyCaption`, `Home_GateBuy`, `Home_GateSyncCaption`,
`Home_GateSync`, `Home_GateRetry`, `Home_ConditionExpired`, `Home_ConditionExpiring`,
`Home_ConditionDevices`, `Home_ConditionOffline`, `Home_ConditionSilent`, `Home_StaleHint`,
`Home_ActionRenew`, `Home_ActionRetry`, `Home_ActionDevices`, `Home_ActionChangeServer`,
`Home_ActionHowto`, `Home_CdConnect`, `Home_CdDisconnect`, `Home_CdCancel`.

**Deleted keys:** `Home_Welcome`, `Home_NoSubs`, `Home_NoSubsHint`, `Onboarding_Title`,
`Onboarding_Subtitle`, `Onboarding_OrSignInShort`, `Home_ChooseServer`, `Home_NotConnected`,
`Home_MyServers`. `Home_RetryHint` is renamed `Home_DetailRetry`. `Home_TunUnavailable` and
`Home_RestartElevated` survive as the condition-6 strip text and action.

Russian plurals on desktop go through a `Plural(int n, string one, string few, string many)` helper
in `Common/L.cs`; there is no `.resx` plural machinery and inventing one is out of scope. The three
plural sets above are the only ones this screen needs.

---

## 14. Accessibility

| Requirement | This screen |
|---|---|
| Contrast, body | Every pair measured in sections 5, 6, 7, 9 and 10. Lowest text pair on the screen is the accent strip action at 5.28:1 |
| Contrast, controls | The disc's ring clears 3:1 on both of its edges in all four themes (5.2). This is the single most-missed point in the current build, where the ring measures 1.45:1 |
| Touch targets | Disc 176dp. Rows 56dp full width. Gate primary 52dp, secondary 48dp. Strip action 48 x 48dp. Header row 56dp. Nothing under 48 |
| Target separation | 8dp minimum. The only adjacent pair is the two gate buttons at 12dp |
| Accessible names | `#connect_disc` carries `android:contentDescription` that states **state and action**: «Отключено. Нажмите, чтобы подключить» / «Подключение. Нажмите, чтобы отменить» / «Подключено. Нажмите, чтобы отключить» / «Недоступно. Нет подписки». `#shield_outline`, `#shield_filled`, `#connect_ring`, `#connect_ring_pulse` and `#connect_sweep` are all `importantForAccessibility="no"` |
| Live region | `#status_line` is `android:accessibilityLiveRegion="polite"`, so a state change is announced without stealing focus. The numeric strip is **not** a live region: announcing a speed twice a second is unusable |
| Reading order | header, strip, disc, status line, numeric strip, ledger. Matches visual order |
| Focus (keyboard and TV) | 2dp `?attr/colorPrimary` ring at 2dp offset. On the disc the ring follows the circle at radius 100 |
| Text scaling | Verified at 200 percent, section 11.3 |
| Reduced motion | Section 12.3 |
| Colour never alone | Connected: green word plus a filled shield. Error: red word plus «Нажмите, чтобы повторить». Expiring: amber chip plus the word «Истекает» |
| Screen-reader state | The disc exposes `isEnabled=false` when gated, and the gate caption states why |

---

## 15. Android implementation map

### 15.1 New files

| File | Contents |
|---|---|
| `res/layout/fragment_home.xml` | The tree of section 4. Target: under 260 lines |
| `res/layout/row_account.xml` | The header row of section 10, reused nowhere else |
| `res/layout/row_universal.xml` | The universal row of `10-design-system.md` 6.4, shared by this screen and every settings surface. This is the resurrection of the orphaned `layout_setting_row.xml` |
| `res/layout/status_strip.xml` | The component of section 9, shared with the shell's docked placement |
| `ui/home/HomeFragment.kt` | Owns this screen. Target: under 400 lines |
| `ui/home/HomeUiState.kt` | One sealed model carrying connection state, gate state, subscription state, condition and the three figures. The fragment renders it and does no branching of its own |
| `res/drawable/bg_connect_disc.xml` | Oval, solid `?attr/colorSurfaceContainerHighest`, wrapped in a `<ripple>` with a matching oval `<mask>` |

### 15.2 Changed files

| File | Change |
|---|---|
| `res/drawable/bg_connect_ring.xml` | Becomes stroke-only: `<stroke android:width="@dimen/stroke_ring" android:color="#FFFFFF"/>`, no `<solid>`, tinted at runtime. The current file's `#2E1E5FC7` and `#701E5FC7` raw hex disappear |
| `res/anim/connect_confirm.xml` | Retuned to scale 1.0 to 1.35, alpha 0.6 to 0, `android:duration="@integer/motion_emphasis"` 600, `android:interpolator="@interpolator/ease_out_quint"` |
| `res/anim/press_scale.xml` | 0.96 to **0.97**, 90ms in, 160ms out (`10-design-system.md` D-11) |
| `res/values/dimens.xml` | Add `strip_value_speed` **48sp**, `strip_value_latency` **32sp** (the product's only sp dimensions, and the comment must say why), `connect_frame` **200dp** |
| `res/values/styles.xml` | Add `TextAppearance.App.Numeric.Value`, parent `TextAppearance.App.Numeric`, `textSize` 16sp, `textFontWeight` 500, `fontFeatureSettings="'tnum' on, 'lnum' on, 'zero' on"`. Fix `TextAppearance.App.Numeric` itself, which declares no weight today (`res/values/styles.xml:122`) |
| `res/values/attrs.xml`, `themes.xml` | Add `colorOnSurfaceDim` (`#6E7480` dark, `#3C475E` light, `#8A8A90` mono dark, `#3C3C40` mono light) and `warning` / `warningText` per `10-design-system.md` 2.2 |
| `ui/MainActivity.kt` | Loses this screen entirely. `applyRunningState()`, `applyConnectedState()`, `applyIdleState()`, `startConnectingAnim()`, `stopConnectingAnim()`, `startConnectionTimer()`, `updateMemoryCard()`, `updateHomeMetaDots()`, `measureHomeMetaHeight()`, `updateAccountGate()` move or die. The file goes from 2 777 lines to a shell |

### 15.3 Deleted files

Listed in 2.1. Eleven layouts and drawables, one anim, plus the strings in 13.1.

### 15.4 Data contract for `HomeFragment`

The fragment renders and does not decide. It needs exactly:

```kotlin
data class HomeUiState(
    val connection: Connection,          // Disconnected | Connecting | Connected | Disconnecting | Error(cause) | NoServer
    val gate: Gate?,                     // SignIn | Buy | SyncServers | SyncFailed(reason) | null
    val account: Account?,               // handle, avatar, or null when signed out
    val accountLoading: Boolean,
    val serverName: String?,             // flag emoji already stripped
    val serverCount: Int,
    val providerCount: Int,
    val subscription: SubscriptionState, // Active(until) | Trial(until) | Expiring(daysLeft, until) | Expired(since) | None | Stale
    val condition: Condition?,           // the single highest-priority strip condition, already resolved
    val downMbps: Double?, val upMbps: Double?, val pingMs: Int?,
    val stale: Boolean,                  // offline: last known data is being shown
)
```

`condition` is resolved to **one** value by the ViewModel using the priority order in section 9; the
fragment never picks between conditions. `subscription` is the same `SubscriptionState` object that
Аккаунт and the ongoing notification render (signature moment 3 in
`32-master-plan-android.md` 1.2): one truth, three surfaces.

### 15.5 Session uptime

Uptime leaves this screen (section 19.1, decision S-3). It survives in the ongoing foreground
notification's second line, which is where a user checks it without unlocking the phone, and in
`settings/about/log`. `tv_connection_time` and `startConnectionTimer()` move to the notification
builder.

---

## 16. The same screen on desktop

Identical nouns, identical order, identical strings, identical states. What differs is what the
platform demands: pointer hover, keyboard focus, a resizable window, and no haptics
(`00-rules.md` 13).

### 16.1 Files

| File | Action |
|---|---|
| `Views/HomeView.axaml` + `.axaml.cs` | Rebuilt to this section |
| `Views/ConnectDiscView.axaml` + `.axaml.cs` | **New.** The disc, the ring, the sweep, the confirm ring, the shield, and their state machine. Nothing else |
| `Views/HomeAccountChip.axaml` | Restyled to the header row of section 10 (40 slot, 36 avatar, 68 origin, ramp sizes only) |
| `Views/StatusStripView.axaml` | **New**, shared with the shell's docked placement |
| `Views/CompactHomeView.axaml`, `Views/ConnectHeroView.axaml`, `Views/OnboardingView.axaml` | Deleted |

### 16.2 Layout

The content area is the window minus the 76px rail and its 1px hairline. Two bands, one view.

**Band 1, content width < 980px (single column).** The Android stack, verbatim.

```
Grid RowDefinitions="56,Auto,*"
 [0] HomeAccountChip                       Height 56, MaxWidth 720, Margin 16,0
 [1] StatusStripView                       MinHeight 48, Margin 16,12,16,0, IsVisible on condition
 [2] ScrollViewer
      StackPanel  MaxWidth="480"  HorizontalAlignment="Center"
        24 | ConnectDiscView (200) | 16 | status line | 24 | numeric strip (44) | 32 | ledger or gate | 24
```

**Band 2, content width >= 980px (split).** The threshold is the one already declared in
`33-master-plan-pc.md` 2.9.

```
Grid RowDefinitions="56,Auto,*"
 [0] HomeAccountChip                       Height 56, Margin 16,0, MaxWidth 1000, centred
 [1] StatusStripView                       spans the full content width, MaxWidth 1000, centred
 [2] Grid ColumnDefinitions="*,32,320"  MaxWidth="1000"  HorizontalAlignment="Center"  Margin="16,24,16,24"
      [0] connect pane, MinWidth 420, VerticalAlignment Center:
             ConnectDiscView (200) | 16 | status line | 24 | numeric strip (44)
      [2] ledger pane, VerticalAlignment Top, top aligned with the disc frame:
             the two rows, or the gate block
```

Below 980 the split would put a 56px row in a column narrower than its own content, which is the
desktop port failure `adapt.native.md` names. At the minimum 900 x 600 window the single column
applies and shows the header, the disc, the status line, the strip and both rows without a scroll.

### 16.3 Desktop-only behaviour

| Concern | Specification |
|---|---|
| Hover, disc | `color_state_hover` (white 6 percent on dark, black 6 percent on light) overlaid on the disc fill, 150ms `Ease.Standard`. The ring does not change. **No glow** |
| Hover, rows | the same overlay across the full row, radius 16, 150ms |
| Press, disc | `scale(0.94)` with `RenderTransformOrigin="50%,50%"`, `Dur.PressIn` 90 in, `Dur.PressOut` 160 out. 0.94 is the single documented exception to the product's 0.97 (`33-master-plan-pc.md` 2.6): at 176px, 0.97 is imperceptible |
| Press, everything else | `scale(0.97)`, same tempo |
| Focus | **Always rendered**, not only on keyboard. 2px `Brush.Accent` ring at 2px offset; on the disc the ring is a circle at radius 100 |
| Tab order | header row, disc, strip action, row 1, row 2. `Space` and `Enter` activate the focused element |
| `Ctrl+Enter` | toggles the tunnel from anywhere in the window. New shortcut, decision S-6 |
| Tooltips | Disc: «Подключить (Ctrl+Enter)» / «Отключить (Ctrl+Enter)». Rows: none, their labels are visible |
| Cursor | `Hand` on the disc, both rows, the header row and the strip action. Default everywhere else |
| Reduced motion | `MotionState.IsLite`, read **live** through `MotionState.Changed`, never once in a constructor. Under lite the confirm ring is never emitted and every transition uses `Dur.Instant` |
| Theme | Every brush through `{DynamicResource ...}`. A `StaticResource` on a theme brush freezes it and breaks live theme switching |
| DPI | Verified at 100 / 125 / 150 / 200 percent OS scaling and at in-app zoom 200 percent |

### 16.4 Desktop token changes

Add `Brush.OnSurfaceDim` (rename of `Brush.OnSurfaceVariantHover`), `Size.ConnectFrame` 200,
`Size.StripValueSpeed` 48, `Size.StripValueLatency` 32, `Size.LedgerPane` 320,
`Size.HomeSplitThreshold` 980, `Size.HomeColumn` 480.

Delete `Brush.HomeGradient`, `Brush.ConnectGlow`, `Brush.Ring.Outer`, `Brush.Ring.Inner`,
`Size.HeroFrame`, `Size.ConnectArc`. Six of the eight surfaces that paint `Brush.HomeGradient` today
(`Views/MainWindow.axaml:434`, `:551`, `Views/HomeView.axaml:16`, `Views/OnboardingView.axaml:43`,
`Views/LoginView.axaml:237`, `Views/AccountSyncView.axaml:47`) are covered by this spec and by
document 12; all of them become `Brush.Bg`.

---

## 17. Parity contract

**Identical, by contract:** the block order; every string; the six launch variants; the seven
connection states; the six strip conditions and their priority order; the disc at 176 with a 3dp
ring and an 80 shield; the three numeric columns, their labels, their formats and their reserved
widths; the 68 text origin; the ledger rows and what they open; the 600ms confirm and its four
simultaneous beats; the 75 percent exit; the rule that the strip is only visible while connected.

**Allowed to differ:**

| Concern | Android | Desktop |
|---|---|---|
| Layout at wide sizes | `sw600dp`: gutter 24, content capped at 720, centred. No split | split into two panes at content width >= 980 |
| Press scale on the disc | 0.97 | 0.94 |
| Hover | does not exist | `color_state_hover`, 150ms |
| Focus ring | keyboard and TV only | always |
| Haptics | `pressHaptic()` on connect and disconnect | none |
| Ripple | bounded to the disc oval | none |
| Toggle shortcut | none | `Ctrl+Enter` |
| Uptime | ongoing notification | tray tooltip |

**Logged parity gaps:** none introduced by this screen. The screen is the same object twice.

---

## 18. Acceptance

Run all of it. A box that cannot be ticked honestly means the screen is not done.

**Bans**
- [ ] `grep -rn "<gradient" res/drawable*/` returns nothing that this screen references
- [ ] No glow, no halo, no ring bloom, no second ring, no ambient loop, no breathing
- [ ] Zero cards on this screen. The disc is a `View` with a drawable, not a `MaterialCardView`
- [ ] No emoji and no typographic character used as chrome. No `↑`, no `↓`, no `∞`, no `✕`
- [ ] No `android:textSize` and no `FontSize` in the markup
- [ ] No raw hex in `fragment_home.xml` or in `HomeView.axaml`
- [ ] No dp value outside the scale. `grep -rnoE '"(-?[0-9]+)dp"' res/layout/fragment_home.xml` against the allow-list

**The direction**
- [ ] Count the blue: zero accent pixels when disconnected and ungated; exactly one accent object when connected (the shield plus its ring, which is one object); exactly one when gated (the primary button)
- [ ] Count the planes: ground plus the P3 disc plus, when a condition applies, the P2 strip. Never more
- [ ] Measure the text origin with a ruler on the screenshot: 68dp for the header row, both ledger rows and both hairlines
- [ ] Count the gaps: 16, 24 and 32 all present, 32 used exactly once
- [ ] Change a strip value from `9,9` to `10,1` in the preview and confirm nothing moves
- [ ] Find a Russian string set in Space Grotesk: there must not be one
- [ ] Squint: one object, one word, one list. The hierarchy survives
- [ ] Crop the wordmark (there is none) and ask whether the three signatures still identify the product

**States**
- [ ] All six launch variants screenshotted (11.1)
- [ ] All seven connection states screenshotted (11.2)
- [ ] Loading, partial, data error, long content, short content, 200 percent, 320dp width (11.3)
- [ ] Dark, light and mono, each of the above

**Interaction and motion**
- [ ] Press feedback visible within 90ms on the disc, both rows, both gate buttons and the strip action
- [ ] The sweep runs only while the core is negotiating, and stops on the frame the state resolves
- [ ] Exactly one ring is emitted per confirmation, and none on disconnect
- [ ] Toggle the system animation scale to 0 and confirm: instant fill, no ring, haptic still fires
- [ ] Tap the disc while connecting: the attempt cancels

**Copy**
- [ ] Every string Russian, sentence case, no ALL-CAPS
- [ ] The em-dash and en-dash grep of `00-rules.md` 9.7 returns nothing new for the `home_*` keys
- [ ] `…` is one character; `«»` used; `₽` does not appear on this screen and does not need to

---

## 19. Decisions

### 19.1 Conflicts between the foundation documents, resolved here

| # | Conflict | Resolution and why |
|---|---|---|
| **S-1** | `11-app-structure.md` 4.1 puts an 8dp status dot beside the status word; `03-direction.md` 2.4 names exactly that as the decoration tell | **No dot.** The word carries the state and its colour reinforces it, which is already two channels. `11-app-structure.md` says `03-direction.md` outranks it on visual argument |
| **S-2** | The connect ring is 3dp (`10-design-system.md` 2.8 `stroke_ring`), 2dp (`stroke_emphasis`) and 1dp (`11-app-structure.md` 4.1) in three documents | **Always 3dp; only the colour changes.** A ring that changes width makes the control appear to change size, which is a layout animation in disguise |
| **S-3** | `11-app-structure.md` 4.1 shows the numeric strip appearing at **connecting** with zeroes and containing **uptime**; `03-direction.md` 10.2 and 11 both name **latency** as the third column; `32-master-plan-android.md` 1.2 names uptime | **Down, up, latency, visible only while connected.** Zeroes during negotiation are placeholders pretending to be readings. Uptime is not a measurement of the tunnel's quality and moves to the ongoing notification; latency is, and it is the number that predicts whether video will stutter |
| **S-4** | `11-app-structure.md` 4.1 puts the «Истекает» chip in the row's trailing slot; `10-design-system.md` 6.4 allows exactly one trailing element and that slot is the chevron | **Chip in the text column**, on the title line, exactly as the protocol chip sits on a server row. The chevron stays the single trailing element |
| **S-5** | `11-app-structure.md` 8.2 sets the status strip at 40dp; it contains a text action, and `00-rules.md` 7.2 sets a 48dp floor for touch targets | **48dp.** Matches `32-master-plan-android.md` 8.10, which already specifies 48 |
| **S-6** | `11-app-structure.md` 8 puts the persistent strip in the content, `32-master-plan-android.md` 8.10 docks it above the bottom navigation and gives it auto-dismiss | **One component, two placements.** Inline in the content for a persistent condition (this screen), docked above the navigation with a 5s dismiss for a transient event (the shell). Same anatomy, same tokens, one file |
| **S-7** | `11-app-structure.md` 3.3 sets the header chevron at 22dp; `10-design-system.md` 2.12 allows only 16 / 20 / 22 / 24 and assigns 20 to inline chevrons, 22 to a glyph inside a 40 tile | **20dp**, which is what `10-design-system.md` 6.4 already specifies for the row archetype |
| **S-8** | `11-app-structure.md` 3.3 sets a 36dp avatar in the header, which puts the text origin at 64 and breaks the 68 origin held everywhere else | **36dp avatar centred in a 40dp slot.** Both rules are satisfied |
| **S-9** | `11-app-structure.md` 5.3 variant A makes «Добавить провайдера» the primary CTA and «Войти в аккаунт» a text button | **«Войти» is the primary, «Добавить провайдера» the text button.** This product sells subscriptions through `departament.site` and a Telegram bot; the overwhelmingly common first-run case is a user who has already paid and needs to sign in. QR and clipboard import stay first class, one tap away, and also live in Серверы > Добавить. Flagged for owner sign-off in 19.3 because it is a product call, not a visual one |
| **S-10** | `10-design-system.md` 2.8 assigns `color_outline` `#2A2E36` to the connect ring at idle, which measures **1.45:1** against the ground and fails WCAG 1.4.11 for the boundary of the product's primary control | **`color_on_surface_dim`**, measured at 4.19:1 on the ground and 3.32:1 on the disc fill in dark, 8.67 / 7.69 in light, 6.12 / 4.57 in mono dark. `color_outline` survives on this screen only for the **disabled** ring, where 1.4.11 exempts inactive components and receding is the correct behaviour |

### 19.2 Conflicts this screen does not resolve, and does not depend on

- **Desktop compact mode.** `11-app-structure.md` 3.2 deletes it; `33-master-plan-pc.md` 2.9 keeps
  two bands with a 380 x 620 floor. This spec defines Главная as one view with two internal layout
  bands keyed to content width, so it is correct either way.
- **Navigation order.** `11-app-structure.md` 2.1 orders the destinations Главная, Серверы, Аккаунт,
  Настройки; `10-design-system.md` 6.15 and `33-master-plan-pc.md` 1.2 order them Главная, Серверы,
  Настройки, Аккаунт. This screen links to Серверы and Аккаунт by identity, not by index.

### 19.3 Change-control rows for `00-rules.md` section 18

Nothing below is implemented until the row is pasted into `00-rules.md` section 18.

| Date | Decision | Rule affected |
|---|---|---|
| pending | **S-10.** The connect disc's ring at idle uses `color_on_surface_dim`, not `color_outline`, because `color_outline` measures 1.45:1 on the ground and fails the 3:1 UI-component-boundary floor for the product's primary control | `10-design-system.md` 2.8 |
| pending | **S-3b.** Session uptime leaves Главная on both platforms and lives in the Android ongoing notification and the desktop tray tooltip | `11-app-structure.md` 4.1 |
| pending | **S-9.** On first run with no account and no servers, the primary action is «Войти» and «Добавить провайдера» is the secondary text button | `11-app-structure.md` 5.3 |
| pending | **S-6b.** `Ctrl+Enter` toggles the tunnel on desktop from anywhere in the window | `11-app-structure.md` 6.2 |
| pending | **S-11.** Two sp-valued dimensions are added on Android, `strip_value_speed` 48sp and `strip_value_latency` 32sp, as the reserved widths of the numeric strip. They are the only sp dimensions in the product and exist so the reserve scales with the text | `00-rules.md` 3.1, `10-design-system.md` 4.3 |

### 19.4 Open questions for the owner

1. **S-9**, above: is «Войти» or «Добавить провайдера» the primary action on a fresh install?
2. The latency probe interval while connected is specified at 30s. If the existing delay test is
   expensive on battery, the interval is the dial to turn; the design does not change.
3. The «Серверы» row's subtitle counts providers. If a user with one provider should instead see the
   provider's name («Departament · 15 серверов»), that is a one-line copy change and not a layout
   change.
