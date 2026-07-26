# Brief - Android redesign build plan

**What this is.** `docs/design2026/` decides what the Android app should look like. This file decides
in what order it gets built, which agent owns which file in each wave, and what has to be true before
a wave can be called finished. It is a work order, not a design document: where it and a spec
disagree on appearance, the spec wins; where they disagree on sequence or ownership, this file wins.
`00-rules.md` outranks both.

**Scope.** Android only (`/home/user/dp`, build root `V2rayNG`). Branch
`claude/app-audit-agents-hyyftk`. The desktop half is `33-master-plan-pc.md` and
`24-tab-conformance.md` sections D-01 to D-48; the parity contract in `00-rules.md` 13 still binds,
so every wave here logs its desktop counterpart as either shipped-in-parallel or a numbered gap.

**How to read the tables.** Every path is repo-relative to `/home/user/dp` unless it starts with
`res/` or `ui/`, which are relative to `V2rayNG/app/src/main/` and
`V2rayNG/app/src/main/java/com/v2ray/ang/` respectively. Line numbers were resolved against the tree
on 2026-07-26 and rot fast; each one is paired with a symbol name so it can be found again.

---

## 0. State of the tree, checked 2026-07-26

The brief that commissioned this document said "the token layer landed today (29 dimens, 73 colours,
motion, press recipe, ids), and the type ramp, component styles and themes are being written right
now by another wave." That was true when it was written and is already stale. What is actually on
disk:

| Layer | Claimed | On disk now | Evidence |
|---|---|---|---|
| Spacing / size / radius tokens | 29 dimens | **57 dimens** in one file | `res/values/dimens.xml`, `grep -c '<dimen '` = 57 |
| Colours | 73 | **167** light + **101** night | `res/values/colors.xml`, `res/values-night/colors.xml` |
| Motion scale | landed | landed, 13 durations + `@fraction/press_scale` 97% | `res/values/motion.xml` |
| Press recipe | landed | `press_scale.xml` on 0.97 / 90 / 160 with named interpolators; `nav_press.xml` **also** rewritten to 0.97 and deliberately kept | `res/anim/press_scale.xml`, `res/anim/nav_press.xml` header comment |
| Declared ids | landed | one: `tag_last_click` | `res/values/ids.xml` |
| Interpolators | - | all four exist: `ease_out_quart`, `ease_out_quint`, `ease_standard`, `ease_out_expo` | `res/interpolator/` |
| Fonts | - | Golos Text 400/500/700 + Space Grotesk vendored; family XMLs `golos_text.xml`, `space_grotesk.xml` | `res/font/` |
| Type ramp | "being written now" | **landed**: 16 `TextAppearance.App.*` styles with declared `lineHeight`, per-role face, D-4 weight on Numeric | `res/values/styles.xml:65-296` |
| Component styles | "being written now" | **landed**: ~95 `Widget.Departament.*` / `ShapeAppearance.Departament.*` styles, 5 button variants x 2 heights, rows, tiles, chips, fields, switch, nav, toolbar, sheet, dialog, snackbar, empty state, skeleton, progress | `res/values/styles.xml:298-1295` |
| Colour state lists the styles depend on | - | **landed**, 33 files, none dangling | `res/color/`; every `@color/*` referenced by `styles.xml` resolves |
| **Themes** | "being written now" | **NOT landed.** `res/values/themes.xml` last modified 14:20, four hours before `styles.xml` | see 0.1 |

### 0.1 The one hole in the foundation, and it is load-bearing

`res/values/themes.xml` has not been touched by the token wave. Three consequences, each verified:

1. **No component defaults are declared.** `AppThemeBase` (`res/values/themes.xml:4-108`) sets no
   `materialButtonStyle`, no `materialCardViewStyle`, no `materialSwitchStyle`, no `textInputStyle`,
   no `chipStyle`, no `shapeAppearanceCornerSmall|Medium|Large`. Every one of the ~95 component
   styles that landed today is therefore **opt-in per widget instance**. `24-tab-conformance.md` 6.1
   rule 1 is explicit that declaring those defaults "re-shapes 33 buttons, 50 cards, 23 switches and
   70 fields **in one change with no layout edits**". Until that change lands, every screen wave has
   to touch every widget by hand, which multiplies the work of waves 4 through 8.
2. **`color_outline_control` (D-9) has no theme attr.** `res/values/attrs.xml` declares 15 colour
   attrs and `colorOutlineControl` is not among them; `00-rules.md` 3.5 and 6.8 both reference
   `?attr/colorOutlineControl`. The component styles worked around it with colour state lists
   (`@color/field_stroke_selector`, `@color/btn_outlined_stroke`), which is correct for those
   controls but leaves no attr for a layout that needs a control boundary directly.
3. **`?attr/warning` / `?attr/warningText` are likewise undeclared**, while `00-rules.md` 3.5 lists
   both. The colours exist (`color_warning_text` at `res/values/colors.xml:240` and
   `res/values-night/colors.xml:138`); only the attrs are missing.

**This is wave 2's first task and nothing else in wave 2 may start before it.** It is one file, one
owner, one hour.

### 0.2 What has *not* been built at all

| Missing | Spec | Consequence |
|---|---|---|
| `res/layout/view_row.xml`, `view_toolbar.xml`, `view_empty_state.xml`, `view_chip.xml`, `view_meter.xml`, `view_status_strip.xml`, `view_skeleton_row.xml`, `view_search_field.xml` | `32-master-plan-android.md` 8.1-8.16, `22-components.md` | The styles exist; the reusable **layouts** that consume them do not. `ls res/layout/ \| grep '^view_'` returns nothing |
| `ui/component/` package (binders) | `32` 26 wave 2 | No `ui/component` directory exists; `ui/` has exactly one subpackage, `ui/adapter/` (2 files) |
| Component gallery / debug harness | `32` 26 wave 2 | There is no way to look at a component in all three themes at 200% font scale without shipping it into a screen first |
| `res/anim/subpage_enter.xml`, `subpage_exit.xml` | `32` 26 wave 1 | `res/anim/` holds four files: `connect_confirm`, `nav_press`, `press_scale`, `shield_assemble` |
| Fragments | `32` 9.2 | `ui/` has exactly one fragment class, `AccountFragment.kt`. There is no `HomeFragment`, `ServersFragment` or `SettingsFragment` |

### 0.3 Mechanical baseline, re-measured today

Run from `V2rayNG/app/src/main/res`. These are the numbers a wave gate compares against.

| Check (`00-rules.md` 1.5 / 9.7) | Baseline 2026-07-26 in the rules file | Measured now | Verdict |
|---|---|---|---|
| Raw colour literals in `layout/` + `menu/` | 0 | **0** | hold at zero |
| `textAllCaps="true"` in `layout/` + `values/` | 0 | **0** | hold at zero |
| Off-scale `dp` in `layout/` | 325 across 25 files | **325 across 26 files** | debt, retired file by file |
| `android:fontFamily` or `android:textSize` in `layout/` | ~100 raw `textSize` | **118 lines** | debt, retired file by file |
| Em-dash / en-dash in `values*/strings*.xml` | 22 | **23** | debt, one owner, one wave |
| Three dots where `…` belongs | not baselined | **10** | debt, same owner as the dashes |
| Emoji in `values*/strings*.xml` | 0 | not re-run (needs `python3`; the rules file's own command) | assume 0, re-verify in the copy wave |

The off-scale `dp` debt is concentrated, which is what makes it schedulable:
`activity_local_proxy.xml` 75, `activity_provider_settings.xml` 62, `activity_url_scheme_list.xml`
37, `layout_settings_content.xml` 36, `activity_backup.xml` 26, `activity_main.xml` 21,
`activity_account.xml` 17. Those seven files carry 274 of the 325. Six of the seven are deleted or
rebuilt by this plan anyway, so the debt retires as a side effect of the waves rather than as a
separate cleanup task.

### 0.4 Defects that CONTINUE-HERE.md lists as open and are now closed

Read before planning around them.

| CONTINUE-HERE.md 4 item | Status now | Evidence |
|---|---|---|
| "19 amputated menu actions ... implementations are live but unreachable dead code" | **Closed.** `res/menu/menu_main.xml` declares `group_import` (6 items) and `group_server_list` (6 items); `MainActivity.onOptionsItemSelected` (`MainActivity.kt:2159-2228`) dispatches all 12 plus `sub_update` | `res/menu/menu_main.xml`, `MainActivity.kt:2159` |

Everything else in that list is still open and is scheduled below: `SettingsActivity` unreachable
(0.5), no user-visible logout (0.5), `CheckUpdateActivity` unreachable (0.5), RAM panel unreachable
(0.5), 321 keys with no `values-ru` entry (wave 9).

### 0.5 Features that exist in code and no user can reach

A redesign that does not deliberately route these buries them. Each is verified by grep today.

| Feature | Code that exists | Why it is unreachable | Where this plan puts it |
|---|---|---|---|
| Advanced settings tree | `ui/SettingsActivity.kt` (309 lines), `res/xml/pref_settings.xml` (354 lines) | Declared at `AndroidManifest.xml:89`; **zero launch sites**. `grep -rn SettingsActivity --include=*.kt` returns only the class itself and one unrelated comment at `MainActivity.kt:2873` | Wave 7. Every preference gets a home in `32` 20 **before** the file is deleted |
| Sign out | `viewmodel/AccountViewModel.kt:400` `fun logout()` | Zero call sites | Wave 8, Аккаунт. `32` 15.3 puts «Выйти» in the account list with a confirm dialog |
| Update check | `ui/CheckUpdateActivity.kt` | Declared at `AndroidManifest.xml:192`; **zero launch sites** (`grep -rn CheckUpdateActivity --include=*.kt` returns nothing outside the class) | Wave 7. `24-tab-conformance.md` A-36 says DELETE on Android; D-34 says WIRE on desktop. That is a deliberate parity gap and must be logged, not silently inherited |
| RAM panel | `MainActivity.updateMemoryCard()` reads `AppConfig.PREF_SHOW_MEMORY` at `MainActivity.kt:2010` | The key is read and **never written**: `grep -rn PREF_SHOW_MEMORY --include=*.kt` returns exactly two hits, the read and the constant at `AppConfig.kt:58` | Wave 7. Either a row in Настройки writes it, or the panel and the key are deleted together. Leaving a dead read is not an option |
| Per-server actions: rename, delete, share, QR, edit | `ui/ServerActionsSheet.kt`, wired at `MainActivity.kt:674-675` and `showServerActions()` at `:683` | **P0, still live.** `MainRecyclerAdapter` declares `onItemLongClick` at `MainRecyclerAdapter.kt:56` and never invokes it - there is no `setOnLongClickListener` anywhere in the file. The callback the Activity assigns is dead. Nine editor activities are unreachable | Wave 3, first item. This is a bug fix on today's UI and does not wait for the Серверы wave |

---

## 1. Dependency order, argued

### 1.1 The five layers

Each layer is consumed by every layer below it. Building downward is mechanical; building upward
means rebuilding.

```
L0  Theme wiring        themes.xml, attrs.xml, values-night/themes.xml
      declares the attrs and the component defaults everything else resolves through
L1  Tokens              dimens.xml, colors.xml, values-night/colors.xml, motion.xml,
      LANDED            ids.xml, interpolator/, anim/press_scale.xml, font/
L2  Component styles    styles.xml  (TextAppearance.App.*, Widget.Departament.*, ShapeAppearance.*)
      LANDED            plus res/color/*.xml state lists
L3  Component layouts   view_row.xml, view_toolbar.xml, view_empty_state.xml, view_chip.xml,
      NOT BUILT         view_meter.xml, view_status_strip.xml, view_skeleton_row.xml,
                        view_search_field.xml, and ui/component/* binders
L4  Shell               activity_main.xml, MainActivity.kt, the four Fragments, insets, Back,
      NOT BUILT         bottom navigation, sub-page host activity_base.xml
L5  Screens             everything in PART II of 32-master-plan-android.md
```

L1 and L2 landed today in the wrong order relative to L0: the styles were written before the theme
that is supposed to install them as defaults. That is recoverable - L0 is one file - but it is why
0.1 is wave 2's blocking task rather than a tidy-up.

### 1.2 Why the order is what it is

| Edge | The dependency | What happens if it is inverted |
|---|---|---|
| L0 before L3 | A component layout that needs a control boundary writes `?attr/colorOutlineControl`. The attr does not exist yet, so the layout either hard-codes a colour (bans 1.4.6) or invents a private state list | Every `view_*.xml` gets written twice: once against a missing attr, once after it lands |
| L0 before L5 | Declaring `materialButtonStyle` etc. re-skins 33 buttons, 50 cards, 23 switches, 70 fields with **zero layout edits** (`24-tab-conformance.md` 6.1) | Every screen wave hand-applies `style="@style/Widget.Departament.Button.Primary"` to every button. Roughly 176 widget-level edits that one theme line would have made for free |
| L3 before L4 | The shell's four Fragments each own a toolbar (`32` 8.6) and an empty state (`32` 8.8). Those are components | `HomeFragment`, `ServersFragment`, `AccountFragment`, `SettingsFragment` each grow a private toolbar and a private empty state. Four dialects, then a fifth when the component finally lands |
| L3 before L5 | `00-rules.md` and `24-tab-conformance.md` 6.1 rule 4: "A component is never introduced for one screen" | The row component is invented on Настройки, re-invented on Аккаунт, re-invented on Серверы. This already happened once: `layout_setting_row.xml` and `layout_setting_toggle_row.xml` exist and **no layout includes them** (`32` 19.2) |
| L4 before L5 | Every screen becomes a Fragment inside the shell. A screen rebuilt while it is still a `View` group toggled by `isVisible` inside `activity_main.xml` has to be re-hosted afterwards | Named below |
| Component wave before screen waves | The status strip (`32` 8.10) replaces `Toast`. `showStatusToast()` at `MainActivity.kt:1874` and `toast_status.xml` are the current mechanism | Each screen wave invents its own feedback surface and the product ends with three |

### 1.3 The concrete cost of violating it, by name

These are the screens that get built twice if the shell (L4) is deferred until after any screen wave.
All four currently live as sibling `View` groups inside one 705-line layout, toggled by
`MainActivity.showTab()` (`MainActivity.kt:475`):

| Screen | Lives today as | If rebuilt before the shell | Rework |
|---|---|---|---|
| Главная | `activity_main.xml` `group_home`, lines 42-455 | A rebuilt `group_home` still sits in the shared layout, still shares `binding` with three other tabs, still inherits `bg_home_gradient` from `home_root` at `activity_main.xml:8` | Whole layout re-parented into `fragment_home.xml`; every `binding.x` in the home half of `MainActivity` re-resolved against a Fragment binding |
| Серверы | `activity_main.xml` `group_servers`, lines 456-492 | Same | Same |
| Настройки | `activity_main.xml` `group_settings` line 493, which `<include>`s `layout_settings_content.xml` (1 536 lines) | The include is the thing being deleted (`32` 19.2). Rebuilding rows inside it is work thrown away | Total: `layout_settings_content.xml` is deleted, not migrated |
| Аккаунт | `activity_main.xml` `group_account` line 503; the only real Fragment | Least affected, but its toolbar and insets come from the Activity | Toolbar and inset handling redone |

The shell also owns two defects that every screen inherits, so fixing them once in L4 fixes them
four times:

- **Insets.** `activity_main.xml` handles them at runtime via `setupEdgeToEdge()`
  (`MainActivity.kt:533`), while `activity_base.xml` (the sub-page host, 41 lines) uses
  `android:fitsSystemWindows="true"`. Two strategies. `32` 9.3 collapses them to one.
- **Back.** `MainActivity.onKeyDown()` at `MainActivity.kt:2859-2865` intercepts `KEYCODE_BACK`,
  calls `moveTaskToBack(false)` and returns `true` unconditionally, which swallows the key before
  the `OnBackPressedCallback` registered at `MainActivity.kt:296` can run. `enableOnBackInvokedCallback`
  is **not declared** in `AndroidManifest.xml`. So the app never finishes on Back and predictive
  Back is off. Every screen built before this is fixed ships with broken Back.

### 1.4 The two spec conflicts this brief settles

Both are real contradictions between two committed specs, and a build plan cannot straddle them.

**Conflict 1: which screen family goes first.**

| Source | Order |
|---|---|
| `32-master-plan-android.md` 26 | wave 4 Вход + Главная, wave 5 Серверы, wave 6 Настройки, wave 7 Аккаунт |
| `24-tab-conformance.md` 6.2 | wave 3 Аккаунт, wave 4 Главная, wave 5 Серверы, wave 6 Настройки |

**Ruling: `32`'s order wins for the first family; `24`'s family-atomicity rule (6.1 rule 2) and its
gate discipline are adopted wholesale.** Reason, in precedence order:

1. `00-rules.md` 0.4.10 is a standing owner request naming exactly two screens: "**The sign-in
   screen and the first tab at launch are redesigned from scratch, minimalist.**" Under `00-rules.md`
   0.1.1 an owner request in his own words outranks both specs. `24` 6.2's justification for putting
   Аккаунт first is "the owner's live demand", which is a claim about the same authority without a
   quotation attached to it.
2. `24` 6.2 argues "Главная depends on [Аккаунт's] identity row, its subscription chip and its gate
   states." The dependency is real but it points at the **subscription card**, not at the Аккаунт
   screen, and `32` 26 wave 4 already assigns the subscription card to the Главная wave: "Also in
   this wave, because they are on the same screen: the subscription card (11.6)". Building the card
   with Главная and consuming it in Аккаунт satisfies the dependency in the order `32` gives.
3. Аккаунт is the least broken of the four. `32` 15.2 keeps `AccountFragment`'s four-state hero
   machine and calls it "the best state machine in the app"; `32` 11.2 and 10.2 mark Главная and
   Вход as full rebuilds with every gradient and glow deleted.

**Conflict 2: who owns the connect state machine after the split.**

`32` 9.3 says `MainActivity` keeps it: "the connect state machine (`applyRunningState`, the watchdog,
one-shot event consumption, live-transition gating - this is careful, correct work and is preserved
verbatim), the service binding, the deep-link routing, and the tab switch." `32` 11.2 says the
opposite: "`ui/MainActivity.kt` home half | Split into `ui/HomeFragment.kt`; the connect state
machine moves with it unchanged."

**Ruling: 9.3 wins. The state machine stays in the Activity; `HomeFragment` renders it and owns no
part of it.** The deciding evidence is in the code, not in either spec: `scheduleConnectWatchdog()`
(`MainActivity.kt:2106`) posts on a `Handler` with a `CONNECT_TIMEOUT_MS` delay, and `onPause()`
(`MainActivity.kt:2146`) removes only the memory runnable, deliberately leaving the watchdog armed
across a pause. A `Fragment` detached by a tab switch would tear that down every time the user looks
at Серверы, and a connect attempt started on Главная would lose its timeout the moment the user
navigated away. The same argument applies to the service binding and to `restartV2Ray()`
(`MainActivity.kt:1669`), which is called from outside the home surface.

The seam is therefore: **Activity owns connect state and service lifetime; Fragment owns the hero
view and observes.** Concretely, `applyRunningState` / `applyConnectedState` / `applyIdleState`
(`MainActivity.kt:1720`, `:1749`, `:1816`) stop touching `binding.*` and instead push a sealed
`ConnectUiState` into a `StateFlow` that `HomeFragment` collects.

---

## 2. Contested files, and how two agents are kept out of each one

### 2.1 The register

A file is contested when more than one screen's work would naturally touch it. The rule for all of
them is the same and it is not negotiable: **exactly one owner per wave, named in the wave table.**
An agent that needs a change in a file it does not own reports the change and does not make it.

| File | Why contested | Rule |
|---|---|---|
| `res/values/themes.xml` + `res/values-night/themes.xml` + `res/values/attrs.xml` | Every screen wants an attr; every component wants a default style | **Serialised to wave 2, agent T1, and then frozen.** After wave 2 a change here is a written request to the wave lead, not an edit. Adding an attr mid-wave invalidates every other agent's build |
| `res/values/styles.xml` (72 KB, ~110 styles) | Every screen wants "just one more style" | One owner in wave 2 (T2). From wave 3 on it is **append-only, by request**: an agent that needs a style asks the wave lead, who appends it and tells everyone. A screen agent never edits it directly. Rationale: this file is the single largest merge hazard in the repo and two agents appending to it concurrently in a container that can restart is how a wave gets lost |
| `res/values/dimens.xml` | Same | **Frozen after wave 2.** 57 tokens cover the whole spec. A new dimen is a spec change and needs a `00-rules.md` 18 entry or a documented derivation |
| `res/values/colors.xml`, `res/values-night/colors.xml` | Same | **Frozen after wave 2.** 167 + 101 colours plus 33 state lists is already more than the spec needs |
| `ui/MainActivity.kt` (3 339 lines) | Four screens live inside it | Split. See 2.2. This is the hardest case and it is answered plainly there |
| `res/layout/activity_main.xml` (705 lines) | Same | Owned by the shell agent in wave 4 and reduced to about 30 lines in that wave. No screen agent touches it before then; after wave 4 there is nothing in it worth contesting |
| `res/menu/menu_main.xml` | Both Главная's `+` and Серверы' `+` inflate it (`MainActivity.showImportMenu()` at `:728`, `prepareMenu()` at `:752`) | Owned by the Серверы agent in wave 6, because `32` 14.2 converts it from a `PopupMenu` into the add-source sheet, which is a Серверы deliverable. Главная's `+` is deleted in wave 5 (`32` 11.3 gives Главная no `+`), so the contest ends before it starts |
| `res/values/strings.xml` (485 strings) | Every screen | **Nobody edits it during a screen wave.** New strings go in the per-screen file below. The legacy file is swept exactly once, in wave 9, by one owner |
| `res/values/strings_*.xml` (19 files) | Mostly already per-screen | Assigned per wave in 2.3. This split is the reason string work parallelises at all and it must not be undone by "tidying" them back into one file |
| `res/values-ru/**`, `values-ar/`, `values-bn/`, `values-fa/`, `values-vi/`, `values-zh-rCN/`, `values-zh-rTW/`, `values-bqi-rIR/` | Translations of everything | One owner, wave 9. A screen agent never adds a translation; it adds the default string and the sweep picks it up |
| `AndroidManifest.xml` | Activities are added and deleted by several waves | One owner per wave, always the shell/infra agent of that wave. Deletions are batched at the end of a wave, never mid-wave |
| `res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml` | Orphans today (`32` 19.2 lists them as delete candidates; `24` wave 2 lists them as resurrected by the real `BottomNavigationView`) | Owned by the shell agent in wave 4, who resolves the contradiction one way and records which |
| `res/xml/pref_settings.xml` | 354 lines, ~48 preferences, half of them the only home of a setting | Read-only for everyone until wave 7. It is the **inventory** of what must not be lost; the Настройки agent uses it as a checklist and deletes it last |

### 2.2 `MainActivity.kt`: split it, and along these seams

**Plain answer: yes, split it, and the seams are already there.** The file is 3 339 lines and its
functions are grouped in contiguous blocks by concern, with no interleaving. Every boundary below was
read off `grep -n 'private fun \|override fun ' MainActivity.kt` on 2026-07-26. This is not a
refactor that has to be invented; it is a set of cuts that already exist as comment-delimited
regions.

| Lines today | First symbol | Concern | Destination |
|---|---|---|---|
| 1-270 | imports, fields, `companion object` | Mixed state for all four tabs | Split with its consumers; the connect fields stay |
| 271-357 | `onCreate`, `playColdStartAssemble`, `onSaveInstanceState` | Shell bootstrap | **Stays** in `MainActivity` |
| 358-532 | `setupBottomNav`, `selectNav`, `updateNavSelection`, `navDot`, `applyNavLabelWeight`, `tweenNavItemColor`, `tabGroup`, `showTab`, `maybeRevealServersTab` | Hand-rolled bottom navigation and tab switching | **Stays**, rewritten onto `BottomNavigationView` (`32` 8.7) |
| 533-562 | `setupEdgeToEdge` | Insets | **Stays**, becomes the app's one inset strategy |
| 563-656 | `setupViewModel` | Observers for all four tabs | Split: each observer moves to the Fragment that renders it; the connect and service observers stay |
| 657-952 | `setupServerLists`, `showServerActions`, `setupServersHeader`, `showImportMenu`, `prepareMenu`, `paintMenuItem`, `setupEmptyState`, `updateHomeEmptyState`, `updateBottomNavVisibility`, `applyHomeListVisibility`, `markAllServersTesting`, `refreshServerLists`, `revealListStagger`, `updateServersChrome` | Server lists, both the Главная preview and the Серверы tab | `ui/ServersFragment.kt`, except `updateHomeEmptyState` / `applyHomeListVisibility` which go to `HomeFragment` |
| 953-1108 | `setupHomeMetaPager`, `rebuildHomeMeta`, `measureHomeMetaHeight`, `buildHomeMetaDots`, `updateHomeMetaDots`, `confirmDeleteSubscription`, `toggleHomeServerList` | The subscription meta pager | **Deleted.** `32` 11.2 deletes `layout_subscription_meta_bar.xml` (257 lines) and `ui/HomeMetaPagerAdapter.kt`; the subscription card (`32` 11.6) replaces them |
| 1109-1332 | `setupAccountHeader`, `applyAccountState`, `accountAccessAllowed`, `updateAccountGate`, `bindAccountChip`, `updateLoginCtaVisibility`, `openLoginScreen`, `openTelegramLink`, `updateOnboardingLogin`, `onLoggedIn`, `currentMetaSubId`, `toggleHomePin`, `refreshHomeSub`, `openSubUrl` | Account gate + the home account chip | Split: `updateAccountGate` is **deleted** (`32` 9.1 change 2: the Аккаунт destination is always present); the rest goes to `HomeFragment` in wave 5 and `AccountFragment` in wave 8 |
| 1333-1449 | `metaTitle`, `metaSubtitle`, `bindMetaBar` | Meta-bar binding | **Deleted** with the pager above |
| 1450-1607 | `shareServer`, `showQRCode`, `share2Clipboard`, `shareFullContent`, `editServer`, `removeServer`, `removeServerSub`, `setSelectServer`, `promptApplySelectedServer`, the `MainAdapterListener` object | Per-server actions | `ui/ServersFragment.kt`. **`setSelectServer` at `:1537` and `promptApplySelectedServer` at `:1562` carry fixed defect 1 and move verbatim** |
| 1608-2008 | `handleFabAction`, `startVpnWithPermission`, `startV2Ray`, `restartV2Ray`, `applyThemeDecorations`, `applyRunningState`, `applyConnectedState`, `applyIdleState`, `themeColor`, `showStatusToast`, `animateConnectPress`, `startConnectingAnim`, `stopConnectingAnim`, `refreshConnectArc`, `showLoading`, `hideLoading` | The connect state machine | **Stays** (1.4, conflict 2). Rendering calls are replaced by a `StateFlow` emission |
| 2009-2150 | `updateMemoryCard`, `selectedServerName`, `idleStatusText`, `updateSelectedServer`, `startConnectionTimer`, `stopConnectionTimer`, `scheduleHealthCheckIfEnabled`, `cancelHealthCheck`, `scheduleConnectWatchdog`, `cancelConnectWatchdog`, `onResume`, `onPause` | Timers, watchdog, auto-fallback | **Stays.** `scheduleHealthCheckIfEnabled` at `:2075` carries fixed defect 7 |
| 2151-2858 | `onCreateOptionsMenu`, `onOptionsItemSelected`, `pickManualServerType`, `importManually`, `showManualEntryDialog`, `looksImportable`, `importQRcode`, `importClipboard`, `importBatchConfig`, `showImportResult`, `importConfigLocal`, `importConfigViaSub`, `exportAll`, `delAllConfig`, `delDuplicateConfig`, `delInvalidConfig`, `runBulkDelete`, `snapshotServers`, `undoBulkDelete`, `bulkDeleteAllowed`, `sortByTestResults`, `startLatencyCheckAll`, `showActionSnackbar`, `showFileChooser`, `readContentFromUri`, `locateSelectedServer` | Import, export, bulk list actions - **708 lines, the largest single block** | `ui/ServersFragment.kt`. `importConfigViaSub` at `:2436` carries fixed defect 5 |
| 2859-2876 | `onKeyDown` | The broken Back handler | **Deleted** (1.3) |
| 2877-3339 | `setupSettings`, `bindSettingsState`, `isBypassLanOn`, `isMonoOn`, `restartIfRunning`, `pickMode`, `pingMethodLabelRes`, `pickPingMethod`, `toggleBypassLan`, `toggleIpv6`, `openAlwaysOnSettings`, `dnsLabel`, `editDns`, `editDnsCustom`, `toggleMux`, `editMuxConcurrency`, `toggleFragment`, `currentAppearanceIndex`, `pickAppearance`, `pickLanguage`, `toggleStartOnBoot`, `subAutoUpdateLabel`, `currentSubAutoUpdateLabel`, `pickSubAutoUpdate`, `onDestroy` | The Настройки tab, all 20 rows wired by hand | `ui/SettingsFragment.kt` in wave 7, then dissolved into the sub-page activities of `32` 20. `onDestroy` stays |

**What `MainActivity` is left holding:** roughly 900 lines - bootstrap, insets, Back, tab switch,
bottom navigation, the connect state machine, the watchdog and timers, service binding, deep links.
That is a shell, and it is what `32` 9.3 asks for.

**The split is done in one wave by one agent (wave 4, agent S1), not incrementally.** A half-split
`MainActivity` is the worst of both worlds: the Fragment cannot own its views because the Activity
still binds them, and two agents editing a 3 000-line file in the same wave is exactly the collision
this plan exists to prevent. The cut is mechanical because the seams are contiguous; the risk is not
in the cutting, it is in doing it twice.

**Order inside wave 4.** S1 lands the shell and the four empty Fragment hosts in one change, each
Fragment initially inflating the same markup it inflates today. That change is behaviour-neutral and
build-verifiable on its own. Only then do the screen waves rebuild what is inside each Fragment.

### 2.3 String-file ownership

`res/values/` already carries 19 per-area string files plus the 485-string legacy `strings.xml`. That
split is what lets copy work run in parallel with layout work. Assignment:

| File | Strings | Owner wave / agent |
|---|---|---|
| `strings_auth.xml` | 28 | Wave 5, A-AUTH |
| `strings_home_shell.xml` | 3 | Wave 5, A-HOME |
| `strings_nav.xml` | 6 | Wave 4, S1 (nav labels; «Сервера» becomes «Серверы» here, `32` 9.1) |
| `strings_menu_actions.xml` | 38 | Wave 6, A-SRV |
| `strings_server_actions.xml` | 8 | Wave 6, A-SRV |
| `strings_settings_hub.xml` | 19 | Wave 7, A-SET-HUB |
| `strings_local_proxy.xml` | 35 | Wave 7, A-SET-NET |
| `strings_provider.xml` | 27 | Wave 7, A-SET-PROV |
| `strings_perapp.xml` | 5 | Wave 7, A-SET-APPS |
| `strings_deeplink.xml` | 18 | Wave 7, A-SET-ABOUT |
| `strings_tv.xml` | 23 | Wave 8, A-TAIL |
| `strings_account.xml` | 79 | Wave 8, A-ACC |
| `strings_buy.xml` | 26 | Wave 8, A-BUY |
| `strings_devices.xml` | 21 | Wave 8, A-DEV |
| `strings_history.xml` | 4 | Wave 8, A-DEV |
| `strings_pay.xml` | 4 | Wave 8, A-BUY |
| `strings_templates.xml` | 2 | Wave 8, A-ACC |
| `strings_manual_add.xml` | 1 | Wave 6, A-SRV |
| `strings_ui_polish.xml` | 1 | Wave 9, A-COPY (single `toast_updated`; likely deleted with the Toast sweep) |
| `strings.xml` | 485 | Wave 9, A-COPY, and nobody before |
