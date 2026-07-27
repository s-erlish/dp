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
| Component styles | "being written now" | **landed**: 122 styles in the file, 66 of them `Widget.Departament.*` / `ShapeAppearance.Departament.*` - 5 button variants x 2 heights, rows, tiles, chips, fields, switch, nav bar, nav rail, toolbar, sheet, dialog, snackbar, empty state, skeleton, progress, plus 30 `Widget.App.*` compatibility aliases | `res/values/styles.xml:298-1295` |
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
| "19 amputated menu actions […] implementations are live but unreachable dead code" | **Closed.** `res/menu/menu_main.xml` declares `group_import` (6 items) and `group_server_list` (6 items); `MainActivity.onOptionsItemSelected` (`MainActivity.kt:2159-2228`) dispatches all 12 plus `sub_update` | `res/menu/menu_main.xml`, `MainActivity.kt:2159` |

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
| `res/values/styles.xml` (72 KB, 122 styles) | Every screen wants "just one more style" | One owner in wave 2 (T2). From wave 3 on it is **append-only, by request**: an agent that needs a style asks the wave lead, who appends it and tells everyone. A screen agent never edits it directly. Rationale: this file is the single largest merge hazard in the repo and two agents appending to it concurrently in a container that can restart is how a wave gets lost |
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

---

## 3. The wave plan

### 3.0 Numbering, and how it maps to the two specs

This brief keeps `32-master-plan-android.md` 26's numbering where it can, and inserts W4 because the
shell split is large enough to be its own shippable change rather than a preamble to Главная.

| This brief | `32` 26 | `24` 6.2 | Visible change |
|---|---|---|---|
| W1 tokens | wave 1 | wave 0 | **done, on disk** |
| W2 foundation completion: themes, attrs, component layouts, gallery | wave 1 remainder + wave 2 | wave 0 + wave 1 | none |
| W3 functional unblock | wave 3 | inside wave 0 | small, overdue |
| W4 shell | inside wave 4 | wave 2 | structural, not stylistic |
| W5 Вход + Главная | wave 4 | wave 4 (+ 3 partial) | large, the first impression |
| W6 Серверы | wave 5 | wave 5 | large |
| W7 Настройки | wave 6 | wave 6 | large |
| W8 Аккаунт + commerce | wave 7 | wave 3 | large |
| W9 editors, tail, copy, accessibility | waves 8 + 9 | wave 7 | medium |
| W10 audit | wave 10 | gate | none |

Three rules from `24` 6.1 are adopted verbatim and bind every wave here: **family atomicity** (a tab
ships with its sub-pages), **no orphan component** (the library gains it first, with all its states),
and **delete in the same change as the replacement** (no file is "kept for now").

### 3.1 How to read an agent row

`Owns` is exclusive: within a wave, no path appears in two agents' lists. A path that appears with a
`*` is created by that agent and did not exist before. `Reads` is everything else the agent may open
but not modify. An agent blocked on a file it does not own writes the request into its report and
stops - it does not edit.

---

### W2 - Foundation completion. Invisible, and it unblocks every later wave

**Goal.** Close the theme hole (0.1), build the component layouts and binders that do not exist
(0.2), and stand up a gallery so a component can be looked at in three themes at 200% font scale
before any screen consumes it.

**Visible change: T1's half is visible, the rest is not, and the two specs disagree about that.**
`32` 26 waves 1 and 2 both say "Visible change: none." `24` 6.2 wave 1 is titled "The control layer
(**every control changes at once**)" and its gate expects "button heights collapse from 11 to 2
across the product; chevrons collapse from 6 sizes to 1". **`24` is right and `32`'s "none" applies
only to the token files, which already landed.** Installing `materialButtonStyle`,
`materialCardViewStyle`, `materialSwitchStyle`, `textInputStyle`, `chipStyle` and the three
`shapeAppearance*Component` attributes re-skins every existing control by design; that is the
leverage, not a side effect. Saying it out loud matters, because a reviewer told to expect no change
would file the intended re-skin as a regression.

So the wave carries two gates, one per half, both stated at the end of this wave block.

**Serialisation inside the wave.** T1 lands first and alone. C1-C4 start only after T1's build is
green, because every component layout resolves attrs T1 declares. This is the one intra-wave
dependency in the whole plan and it is worth the half-day it costs.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **T1** theme wiring | Declare the missing attrs and install every `Widget.Departament.*` as a Material component default | `res/values/themes.xml`, `res/values-night/themes.xml`, `res/values/attrs.xml` | `res/values/styles.xml`, `colors.xml`, `dimens.xml`, `00-rules.md` 3.5/6.8/11.1, `22-components.md` |
| **C1** row + header + card | The universal 56dp row at the 68dp text origin, all five archetypes | `res/layout/view_row.xml`*, `view_row_toggle.xml`*, `view_row_value.xml`*, `view_section_header.xml`*, `ui/component/RowBinder.kt`*, `res/drawable/bg_row_group.xml`*, `divider_row.xml`* | `styles.xml` `Widget.Departament.Row*` / `.Tile` / `.Card`, `32` 8.1-8.3 |
| **C2** feedback + states | The one feedback channel and the state surfaces that replace `Toast` and blank screens | `res/layout/view_status_strip.xml`*, `view_empty_state.xml`*, `view_skeleton_row.xml`*, `view_skeleton_card.xml`*, `ui/component/StatusStrip.kt`*, `ui/component/Skeleton.kt`* | `styles.xml` `Widget.Departament.EmptyState.*` / `.Skeleton.*` / `.Snackbar`, `00-rules.md` 9.4/9.5/9.6/15, `32` 8.8-8.10 |
| **C3** chrome | The seamless sub-page toolbar and the search field, the two things ~30 screens share | `res/layout/view_toolbar.xml`*, `view_search_field.xml`*, `ui/component/SeamlessToolbar.kt`*, `res/anim/subpage_enter.xml`*, `res/anim/subpage_exit.xml`* | `styles.xml` `Widget.Departament.Toolbar*`, `motion.xml`, `00-rules.md` 4.8, `32` 8.6, 8.15 |
| **C4** small parts | Chip, meter, segmented, stepper | `res/layout/view_chip.xml`*, `view_meter.xml`*, `view_segmented.xml`*, `view_stepper.xml`*, `ui/component/Meter.kt`* | `styles.xml` `Widget.Departament.Chip*` / `.SegmentGroup` / `.Segment` / `.Progress.*`, `32` 8.4, 8.11, 8.13, 8.14 |
| **G1** gallery | A debug-only screen that renders every component in every state, in dark, light and mono, at 100% and 200% | `ui/GalleryActivity.kt`*, `res/layout/activity_gallery.xml`*, `AndroidManifest.xml` (the one `<activity>` block, debug flavour only) | everything |

**Contested-file handling in W2.** `styles.xml` has one reader-with-append-rights: T1. If C1-C4 need
a style, they file it with T1 and T1 appends. `dimens.xml` and `colors.xml` are frozen; a component
that cannot be built from the 57 dimens and 268 colours on disk reports that as a spec gap.

**Gate, T1's half (the re-skin).** `verify-build.sh android` green with `NEW WARNINGS: 0` and
`COMPILER: ran`. Then, on all four tabs plus five sub-pages: every button is 48 or 52 tall and 16
round with zero insets; every card is 20 round with a 1dp `colorOutlineVariant` hairline and
elevation 0; every field is 16 round at 56 minimum; every switch is the Departament switch. **No
control is clipped, no label is truncated and no layout is broken** at 100% and 200% font scale. A
control that moved is expected; a control that broke is T1's defect and is fixed in this wave, not
deferred - a broken control on a shipped screen is exactly the "visibly half-converted" state `24`
6.1 exists to prevent.

**Gate, C1-C4 and G1's half (the new components).** The gallery opens and renders every component in
every state in dark, light and mono, at 100% and 200%, at 320dp and 600dp width. **No shipped screen
references any `view_*.xml` yet**, so a screenshot of Главная, Серверы, Настройки and Аккаунт before
and after C1-C4's changes alone is identical. If a component layout changed a shipped screen, it was
wired in early and that is a scope breach.

---

### W3 - Functional unblock. Small, visible, and overdue

**Goal.** Fix what is broken on today's UI, without changing the visual language. Every item here is
a defect a user can hit right now. Parallel by construction: the three agents share no file.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **F1** long-press P0 | Restore the only route to rename, delete, share, QR and edit a server | `ui/MainRecyclerAdapter.kt` | `ui/MainActivity.kt` `:674`, `:683`, `ui/ServerActionsSheet.kt`, `24` A-15, `32` 12.9 |
| **F2** Back | One Back handler, predictive Back declared | `AndroidManifest.xml`, and **only** these two regions of `ui/MainActivity.kt`: `onKeyDown` at `:2859-2876` (delete) and the `OnBackPressedCallback` at `:296-308` | `00-rules.md` 11.3, `32` 9.3, `docs/agents/verify-back-key-tab-navigation.md` |
| **F3** feedback channel | Replace the top `Toast` sites with the status strip C2 built | `res/layout/toast_status.xml` (delete), plus a named list of `Toast` call sites agreed with the wave lead before starting | `ui/component/StatusStrip.kt`, `00-rules.md` 1.4.8, `24` A-39 |

**The `MainActivity.kt` contest in W3 is real and is handled by line-range assignment, not by file
assignment.** F2 owns two named regions; F3 owns the call sites it lists in advance; F1 does not open
the file. If F3's list and F2's regions overlap, F3's list changes. This is the only wave in the plan
where two agents touch one file, and it is allowed only because the regions are tiny, named in
advance, and the file is about to be split anyway.

**Gate.** Build green. Long-press a server row: `ServerActionsSheet` opens. Back from Серверы goes to
Главная; Back from Главная finishes the activity. No `Toast` remains in the files F3 listed.

---

### W4 - The shell. Structural, and behaviour-neutral

**Goal.** Turn `MainActivity` from a four-screens-in-one-layout monolith into a container with four
Fragment hosts, one inset strategy and a real `BottomNavigationView`. **Each Fragment initially
inflates the markup it inflates today.** Nothing is restyled in this wave. That is what makes it
verifiable: the app looks the same and the file structure is the target one.

**One agent, deliberately.** The split cannot be parallelised: every cut moves `binding.*` references
that another cut also touches.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **S1** shell | The split of 2.2, executed once | `ui/MainActivity.kt`, `res/layout/activity_main.xml`, `res/layout/activity_base.xml`, `res/layout/fragment_home.xml`*, `fragment_servers.xml`*, `fragment_settings.xml`*, `ui/HomeFragment.kt`*, `ui/ServersFragment.kt`*, `ui/SettingsFragment.kt`*, `ui/AccountFragment.kt`, `ui/BaseFragment.kt`, `res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml`, `res/values/strings_nav.xml`, `AndroidManifest.xml` | everything |
| **S2** parity log | Record what W4 changed on Android that desktop has not, as numbered gaps | `docs/design2026/24-tab-conformance.md` section 7.3 only | everything |

**S2 is not optional.** `00-rules.md` 13 is a parity contract; a wave that changes the navigation
model on one platform and logs nothing has broken it silently.

**What S1 must preserve verbatim, and the plan says so because these are the load-bearing fixes:**

- `setSelectServer()` at `MainActivity.kt:1537` and `promptApplySelectedServer()` at `:1562` - fixed
  defect 1 (tap selects, never connects).
- `scheduleHealthCheckIfEnabled()` at `MainActivity.kt:2075`, the field block at `:174-195`
  (`healthCheckPending` `:174`, `healthCheckConfirming` `:178`, `healthCheckRunnable` `:179`,
  `healthRecheckRunnable` `:187`) **and the observer that consumes them inside `setupViewModel()` at
  `:624-633`** - fixed defect 7. The `:624-633` half is the easy one to lose, because W4 splits
  `setupViewModel` across four Fragments and this block belongs to none of them; it stays in the
  Activity with the rest of the connect machine. The comment at `:175-177` explains why one negative
  probe is not evidence; do not "simplify" it.
- `connectWatchdogRunnable` at `:197-205` and `scheduleConnectWatchdog()` at `:2106` - the recovery
  path from a core that dies without broadcasting.
- `serversAdapter.syncSelection()` / `homeAdapter.syncSelection()` in `onResume()` at `:2128-2135` -
  fixed defect 3 (two rows painted as selected). The comment says exactly why it is in `onResume`.

**Gate.** Build green. `MainActivity.kt` is under 1 000 lines. All four tabs open, keep scroll
position across tab switches, and survive rotation and a font-scale change. The connect flow works
from a cold start: connect, switch to Серверы, come back, the timer is still counting.
`grep -c 'private fun' ui/MainActivity.kt` fell by roughly two thirds.

---

### W5 - Вход and Главная. The two screens the owner named

**Goal.** `00-rules.md` 0.4.10. Both screens rebuilt from scratch, together, in one release, because
Главная's first-run state is what replaces `layout_home_empty.xml` - which is today the real sign-in
screen for every new user (`32` 10.2). Every gradient, glow and ring drawable is deleted in the same
change; there is no interim state where half of them survive.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **A-AUTH** Вход | `32` 10, `14-auth.md` | `res/layout/activity_login.xml`, `ui/LoginActivity.kt`, `res/values/strings_auth.xml` | `auth/**` (read-only: `AccountSession.kt`, `AuthTokenStore.kt`, `AuthManager.kt`), `32` 10.3-10.7 |
| **A-HOME** Главная | `32` 11, `13-start-screen.md` | `res/layout/fragment_home.xml`, `ui/HomeFragment.kt`, `res/layout/layout_home_empty.xml` (delete), `layout_home_account.xml` (delete), `layout_subscription_meta_bar.xml` (delete), `ui/HomeMetaPagerAdapter.kt` (delete), `res/values/strings_home_shell.xml` | `32` 11.3-11.10, `22-components.md` |
| **A-CARD** subscription card | The card is a component, not a screen part: Главная and Аккаунт both consume it (`32` 11.6, 15.3) | `res/layout/view_subscription_card.xml`*, `ui/component/SubscriptionCardBinder.kt`*, `res/layout/item_subscription_card.xml` (rebuild), `ui/SubscriptionPagerAdapter.kt` | `32` 11.6, `23-account-rework.md` |
| **A-DECOR** decoration purge | Delete every gradient, glow and ring the two screens carried, and their mono variants, in the same change | `res/drawable/bg_home_gradient.xml`, `bg_home_gradient_mono.xml`, `bg_connect_glow.xml`, `bg_connect_glow_mono.xml`, `bg_connect_ring.xml`, `bg_connect_ring_mono.xml`, `bg_bottom_nav_scrim.xml`, `res/anim/connect_confirm.xml`, `res/anim/shield_assemble.xml` | `00-rules.md` 1.4.3, `32` 11.2 |

**Why A-CARD is a separate agent.** `24` 6.1 rule 4: no orphan components. If A-HOME builds the card
inside `fragment_home.xml`, W8's Аккаунт agent rebuilds it. Naming it a component now costs one agent
and saves one screen.

**Contested.** `res/values/strings_nav.xml` was closed by S1 in W4 and is not reopened.
`res/anim/nav_press.xml` is **not** in any list: its own header comment says to delete it together
with its four references when the hand-rolled nav is replaced by the M3 bar, which happened in W4, so
it is S1's deletion and if S1 missed it the wave-4 gate missed it too. Flag rather than fix.

**Gate.** All of section 5, plus: `grep -rn 'gradient\|Gradient' res/drawable/ res/layout/` returns
nothing outside the launcher icon. The first frame after cold start is Главная in every account
state, never a sign-in screen (`32` 9.4, decision D-A4).

---

### W6 - Серверы

**Goal.** `32` 12, 13, 14. The list, the row, the sticky section header, search (which the product has
never had), the sort control, the empty states, the add-source sheet, the scanner, the QR sheet.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **A-SRV** list | The tab and its row | `res/layout/fragment_servers.xml`, `ui/ServersFragment.kt`, `res/layout/item_recycler_main.xml`, `item_section_header.xml`, `layout_servers_header.xml`, `layout_servers_empty.xml`, `item_recycler_footer.xml`, `ui/MainRecyclerAdapter.kt`, `res/values/strings_menu_actions.xml`, `strings_manual_add.xml` | `32` 12.3-12.8 |
| **A-SRV-ACT** actions | The per-item sheet and the whole-list menu, converted to a sheet | `res/layout/sheet_server_actions.xml`, `ui/ServerActionsSheet.kt`, `res/menu/menu_main.xml`, `res/values/strings_server_actions.xml`, `res/layout/item_qrcode.xml`, `res/layout/dialog_config_filter.xml` (delete, `32` 23) | `32` 12.9, 13, 14.3 |
| **A-SCAN** scanner and import | The app's own scan screen currently has zero branding and zero instruction (`32` 14.2) | `ui/ScannerActivity.kt`, `ui/ScScannerActivity.kt`, `res/layout/activity_none.xml`, `res/menu/menu_scanner.xml`, `ui/UrlSchemeActivity.kt`, `ui/UrlSchemeListActivity.kt`, `res/layout/activity_url_scheme_list.xml` | `32` 14.4, 14.5, 22.6, `24` A-43 |
| **A-SRV-ICON** unified server icon | `00-rules.md` 0.4.7 and 10.5: one treatment everywhere a server appears - list row, connect hero, sheet header, notification | `util/FlagUtil.kt`, the flag raster set, `res/drawable/ic_server_*.xml`* | `32` 6.3, and every surface that draws a server |
| **A-SUB** provider merge | `24` A-17: sub setting merges and its screens are deleted | `res/layout/activity_sub_setting.xml`, `activity_sub_edit.xml`, `item_recycler_sub_setting.xml`, `res/menu/action_sub_setting.xml`, `ui/SubSettingActivity.kt`, `SubEditActivity.kt`, `SubSettingRecyclerAdapter.kt` | `32` 20.8, `24` A-17, A-24 |

**Contested.** `res/drawable/bg_search_pill.xml` (radius 14) and `bg_server_row.xml` are both named by
`32` 12.2. Assign both to A-SRV, since the search field is a W2 component and the row is A-SRV's.

**Gate.** Section 5, plus: long-press and overflow both reach every action; search filters in place
and never navigates (`00-rules.md` 4.6); the empty result state is designed, not a blank list; the
unified server icon is the same in the row, the hero, the sheet and the notification.

---

### W7 - Настройки, hub and all sub-pages

**Goal.** `32` 19 and 20. The largest single conversion: one hub plus fourteen sub-pages. This is also
where the 29 preferences with no editing UI get a home or a recorded cut.

**Sequencing inside the wave** follows `32` 26 wave 6 exactly, because each later page reuses the
pattern the earlier one set. The two simplest pages go first and their agent's output is the template
the others copy.

| Agent | Pages | Owns (exclusive) | Reads |
|---|---|---|---|
| **A-SET-HUB** | The hub | `res/layout/fragment_settings.xml`, `ui/SettingsFragment.kt`, `res/layout/layout_settings_content.xml` (delete), `layout_setting_row.xml` (delete), `layout_setting_toggle_row.xml` (delete), `res/values/strings_settings_hub.xml` | `32` 19.3-19.5, `12-settings.md` |
| **A-SET-LOOK** | Оформление, Язык (`32` 20.11, 20.12). **Goes first; its page is the template** | `ui/AppearanceActivity.kt`*, `res/layout/activity_appearance.xml`*, `ui/LanguageActivity.kt`*, `res/layout/activity_language.xml`* | `MainActivity.pickAppearance()` at `:3209`, `pickLanguage()` at `:3246` (logic to port) |
| **A-SET-NET** | Режим подключения, DNS, Проверка серверов, Локальный прокси (`32` 20.1, 20.4, 20.6, 20.7) | `ui/ConnectionSettingsActivity.kt`*, `res/layout/activity_connection_settings.xml`*, `ui/DnsSettingsActivity.kt`*, `res/layout/activity_dns_settings.xml`*, `ui/PingSettingsActivity.kt`*, `res/layout/activity_ping_settings.xml`*, `ui/LocalProxyActivity.kt`, `res/layout/activity_local_proxy.xml`, `res/values/strings_local_proxy.xml` | `MainActivity` `:2988-3200` (logic to port), `docs/agents/verify-local-proxy-port-silent-reject.md` |
| **A-SET-ROUTE** | Обход блокировок, Маршрутизация and the rule editor (`32` 20.3, 20.5) | `ui/RoutingSettingActivity.kt`, `RoutingEditActivity.kt`, `RoutingSettingRecyclerAdapter.kt`, `res/layout/activity_routing_setting.xml`, `activity_routing_edit.xml`, `item_recycler_routing_setting.xml`, `res/menu/menu_routing_setting.xml`, `ui/CircumventionActivity.kt`*, `res/layout/activity_circumvention.xml`* | `32` 20.3, 20.5, `docs/circumvention-settings-design.md` |
| **A-SET-APPS** | Прокси по приложениям and the app picker (`32` 20.2) | `ui/PerAppProxyActivity.kt`, `PerAppProxyAdapter.kt`, `AppPickerActivity.kt`, `AppSelectorAdapter.kt`, `res/layout/activity_app_picker.xml`, `activity_bypass_list.xml`, `item_recycler_bypass_list.xml`, `res/menu/menu_app_picker.xml`, `menu_bypass_list.xml`, `res/values/strings_perapp.xml` | `32` 20.2, `24` A-18, A-19 |
| **A-SET-PROV** | Провайдеры, Что настроил провайдер, Файлы ресурсов (`32` 20.8, 20.9) | `ui/ProviderSettingsActivity.kt`, `res/layout/activity_provider_settings.xml`, `ui/UserAssetActivity.kt`, `UserAssetUrlActivity.kt`, `UserAssetAdapter.kt`, `res/layout/activity_user_asset.xml`, `activity_user_asset_url.xml`, `item_recycler_user_asset.xml`, `res/menu/menu_asset.xml`, `res/values/strings_provider.xml` | `32` 20.8, 20.9, fixed defects 5 and 8 |
| **A-SET-DATA** | Резервное копирование and WebDAV (`32` 20.13) | `ui/BackupActivity.kt`, `res/layout/activity_backup.xml`, `res/layout/dialog_webdav.xml` | `32` 20.13, `24` A-29, A-30 |
| **A-SET-ABOUT** | О приложении, Журнал, Схемы URL (`32` 20.14) | `ui/AboutActivity.kt`, `LogcatActivity.kt`, `LogcatRecyclerAdapter.kt`, `res/layout/activity_about.xml`, `activity_logcat.xml`, `item_recycler_logcat.xml`, `res/menu/menu_logcat.xml`, `res/values/strings_deeplink.xml` | `32` 20.14 |
| **A-SET-KILL** | The deletion, **last in the wave and only after every other agent has reported** | `ui/SettingsActivity.kt` (delete), `res/xml/pref_settings.xml` (delete), `res/layout/activity_settings.xml` (delete), `preference_with_help_link.xml` (delete), `ui/CheckUpdateActivity.kt` (delete), `res/layout/activity_check_update.xml` (delete), `AndroidManifest.xml` | The inventory it is deleting |

**A-SET-KILL is the wave's most dangerous agent and its rule is explicit:** it may delete
`pref_settings.xml` only after producing a table with one row per preference key in it and a column
naming the new surface or the recorded cut. `32` 26 wave 6 says the same: "Delete
`layout_settings_content.xml`, `SettingsActivity`, `pref_settings.xml` and `CheckUpdateActivity` at
the end of this wave, not before: every preference must have its new home first."

**Contested.** `AndroidManifest.xml` is touched by A-SET-LOOK, A-SET-NET, A-SET-ROUTE (new
activities) and A-SET-KILL (deletions). **Resolution: A-SET-KILL owns the file for the whole wave.**
The other agents write their `<activity>` block into their report; A-SET-KILL applies all of them in
one edit at the end. This costs one round trip and removes the wave's worst merge hazard.

**Gate.** Section 5, plus: every key in the deleted `pref_settings.xml` appears in A-SET-KILL's table
with a home or a cut; zero single-choice `AlertDialog`s remain in the settings tree (`32` 20 preamble);
`grep -rn 'Spinner' res/layout/` returns nothing in settings layouts.

---

### W8 - Аккаунт and commerce

**Goal.** `32` 15-18, `15-account-tab.md`, `23-account-rework.md`. Аккаунт, Купить, Устройства,
История платежей, the payment-method sheet, the top-up sheet. Owner requests 0.4.5 (tightened
account, tariff badge), 0.4.9 (explicit «Купить» and «Привязать Telegram») land here.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **A-ACC** Аккаунт | `32` 15 | `res/layout/activity_account.xml` -> `fragment_account.xml`, `ui/AccountFragment.kt`, `res/values/strings_account.xml`, `strings_templates.xml` | `viewmodel/AccountViewModel.kt` (read-only), `view_subscription_card.xml` from W5 |
| **A-BUY** Купить + payment | `32` 16 | `res/layout/activity_buy_tariff.xml`, `item_buy_tariff.xml`, `item_buy_option.xml`, `ui/BuyTariffActivity.kt`, `res/layout/sheet_payment_method.xml`, `item_payment_method.xml`, `ui/PaymentMethodSheet.kt`, `res/layout/dialog_top_up.xml`, `res/values/strings_buy.xml`, `strings_pay.xml` | `32` 16.3-16.7, 15.8, 15.9 |
| **A-DEV** Устройства + История | `32` 17, 18 | `res/layout/activity_devices.xml`, `item_device.xml`, `ui/DeviceManagementActivity.kt`, `ui/adapter/DeviceAdapter.kt`, `res/layout/activity_payment_history.xml`, `item_payment.xml`, `ui/PaymentHistoryActivity.kt`, `ui/adapter/PaymentsAdapter.kt`, `res/values/strings_devices.xml`, `strings_history.xml` | `32` 17.3, 18.3 |
| **A-ACC-WIRE** unreachable features | Give «Выйти», «Привязать Telegram» and the referral row real call sites | Only `viewmodel/AccountViewModel.kt` and a named list of call-site insertions agreed with A-ACC before starting | `32` 15.3, 15.6, `docs/agents/verify-link-telegram-cta-unreachable.md` |

**What must survive this wave, verbatim:**

- `BuyTariffActivity.currentTotal()` - `32` 16.2 calls it "the single source for both the displayed
  total and the charged amount - **this contract must survive**". A rebuild that recomputes the
  displayed total separately from the charged amount is a money bug.
- `AccountFragment`'s four-state hero machine and its cache-first loads - `32` 15.2 calls it "the best
  state machine in the app".
- The payment poll in both `AccountFragment` and `BuyTariffActivity`.

**Contested.** A-ACC and A-ACC-WIRE both want `AccountFragment.kt`. **A-ACC owns the file;
A-ACC-WIRE owns only `AccountViewModel.kt` and hands A-ACC a patch list.** A-ACC-WIRE exists as a
separate agent because "did this wave silently drop a feature" is the review question that matters
most here, and the features at risk (logout, Telegram linking, referral) have zero call sites today,
so nobody would notice their absence.

**Gate.** Section 5, plus: `grep -rn 'logout()' --include=*.kt` returns at least one call site outside
`AccountViewModel.kt`; the terminology lock 9.3 holds on every visible string («Купить», not
«Оформить»; «Привязать Telegram», not «Подключить Telegram»); `₽` never "RUB" or "руб.".

---

### W9 - Editors, the long tail, copy and accessibility

**Goal.** `32` 21-24. The four shared form includes and the nine editor screens, the surfaces outside
the app window, then the two whole-product sweeps.

| Agent | Goal | Owns (exclusive) | Reads |
|---|---|---|---|
| **A-FORM** shared includes | Rebuild four files and nine screens are fixed at once (`32` 21.2) | `res/layout/layout_address_port.xml`, `layout_transport.xml`, `layout_tls.xml`, `layout_tls_hysteria2.xml` | `32` 21.3 |
| **A-EDIT** editors | The nine protocol editors on top of A-FORM's includes | `res/layout/activity_server_vmess.xml`, `_vless.xml`, `_trojan.xml`, `_shadowsocks.xml`, `_socks.xml`, `_hysteria2.xml`, `_wireguard.xml`, `activity_server_custom_config.xml`, `activity_server_group.xml`, `activity_server_proxy_chain.xml`, `item_recycler_proxy_chain_member.xml`, `res/menu/action_server.xml`, `ui/ServerActivity.kt`, `ServerCustomConfigActivity.kt`, `ServerGroupActivity.kt`, `ServerProxyChainActivity.kt`, `ServerProxyChainMemberAdapter.kt` | `32` 21, fixed defect 4 (`V2rayConfig.getProxyOutbound()`) |
| **A-TAIL** outside surfaces | Tasker, widget, QS tile, shortcuts, notification, TV transfer (`32` 22) | `ui/TaskerActivity.kt`, `res/layout/activity_tasker.xml`, `res/layout/widget_switch.xml`, `res/xml/app_widget_provider.xml`, `res/xml/shortcuts.xml`, `ui/ScStartActivity.kt`, `ScStopActivity.kt`, `ScSwitchActivity.kt`, `ui/TvSendActivity`-related layouts `activity_tv_send.xml`, `activity_tv_receive.xml`, `res/values/strings_tv.xml`, `tv/**` | `32` 22, `24` A-31, A-32, A-42, A-44 |
| **A-COPY** the copy sweep | `00-rules.md` 9 across every string file | `res/values/strings.xml`, `res/values/strings_ui_polish.xml`, all of `res/values-ru/`, `values-ar/`, `values-bn/`, `values-fa/`, `values-vi/`, `values-zh-rCN/`, `values-zh-rTW/`, `values-bqi-rIR/` | every `strings_*.xml` (read-only; their owners already swept them) |
| **A-A11Y** accessibility | `00-rules.md` 14 and 16 | No file exclusively; it files defects against the owning agent of each file, and owns only `docs/agents/` reports | everything |

**A-A11Y owns no code on purpose.** It runs last in the wave, on a frozen tree, and its output is a
defect list. Giving it edit rights across every file would make it the one agent that can collide
with all the others.

**Gate.** Section 5, plus: `grep -rn -e '—' -e '–' res/values*/strings*.xml` returns **0** (from 23);
`grep -rn '\.\.\.' res/values*/strings*.xml` returns **0** (from 10); every icon-only control has a
`contentDescription`; the 321 keys with no `values-ru` entry are either translated or recorded as
intentionally default-only.

---

### W10 - The audit

**Goal.** `00-rules.md` 17.1. One agent per screen family, scoring 0-4 on the five dimensions of
`audit.native.md`. Ship bar **>= 18/20, no dimension below 3**. Owns nothing but `docs/agents/`
reports. Anything below the bar goes back to the wave that built it, named.

---

## 4. Per screen: files, spec, states, and what "done" means

**State legend**, from `00-rules.md` 15. A screen implements every state marked for it; a marked
state that is not implemented is at least P1 by `00-rules.md` 17.2.

`Def` default - `FR` first run - `Ld` loading - `Emp` empty - `Err` error - `Off` offline -
`Par` partial - `Lng` long content - `Sht` short content - `Gat` disabled/gated - `Suc` success

### 4.1 Shell and chrome

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Shell | `activity_main.xml` (705 -> ~30) | `ui/MainActivity.kt` | `32` 9.3, `11-app-structure.md` | Def, Ld (cold start), Off | Four Fragment hosts, one inset strategy, one Back handler, `enableOnBackInvokedCallback` declared, bottom nav with four permanent destinations, `MainActivity.kt` under 1 000 lines, tab switch preserves scroll and search state |
| Sub-page host | `activity_base.xml` (41) | `ui/BaseActivity.kt`, `ui/HelperBaseActivity.kt` | `00-rules.md` 4.8, `24` A-38 | Def | Seamless 56dp toolbar sharing `?attr/colorBackground`, no elevation, no divider, 24dp back at the gutter, `android:fitsSystemWindows` removed so one inset strategy remains |
| Bottom navigation | inside `activity_main.xml`, `res/menu/menu_bottom_nav.xml`, `res/color/bottom_nav_item_color.xml` | `MainActivity.setupBottomNav()` at `:358` | `32` 8.7, `00-rules.md` 0.4.8 | Def, Suc | Real `BottomNavigationView`, four items always present signed in or out (`32` 9.1 change 2), «Серверы» not «Сервера», no ripple glow, the bar never disappears (`updateBottomNavVisibility()` at `:848` is deleted) |
| Cold start | `AndroidManifest.xml`, `res/values/themes.xml` | - | `32` 9.4 | Ld | `androidx.core.splashscreen`, background `?attr/colorBackground`, static icon, no custom exit; the first frame after the splash is **always Главная**, never sign-in, never onboarding |

### 4.2 W5 - the two screens the owner named

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Вход | `activity_login.xml` (314, rebuild from scratch) | `ui/LoginActivity.kt` (415) | `32` 10, `14-auth.md` | Def, FR, Ld, Err, Off, Lng, Suc | One obvious primary route, every alternative demoted to text. The Telegram poll, the token exchange and the 2FA exchange survive the view-layer rewrite (`32` 10.2). Copy from `32` 10.5. Wrong-credentials copy is exactly `Неверная почта или пароль.` (`00-rules.md` 9.4) |
| Главная | `fragment_home.xml`* (from `activity_main.xml:42-455`); deletes `layout_home_empty.xml` (139), `layout_home_account.xml` (155), `layout_subscription_meta_bar.xml` (257) | `ui/HomeFragment.kt`*; deletes `ui/HomeMetaPagerAdapter.kt` | `32` 11, `13-start-screen.md` | **all eleven** | Every gate state renders on this screen rather than as a different screen: `нет аккаунта`, `нет подписки`, `подписка истекает`, `подписка истекла`, `триал`, `нет серверов`, `подключение`, `подключено`, `отключение`, `ошибка туннеля`, `лимит устройств`. First-run state teaches «Войти» and «Подключить» by showing them. No gradient, no glow, no ring |
| Subscription card | `view_subscription_card.xml`*, `item_subscription_card.xml` (75, fixed 152dp removed) | `ui/component/SubscriptionCardBinder.kt`*, `ui/SubscriptionPagerAdapter.kt` | `32` 11.6, `23-account-rework.md` | Def, Ld, Emp, Err, Lng, Sht, Gat | Tariff badge present (owner request 0.4.5). Height is `minHeight`, never fixed (`00-rules.md` 3.3 R2). `Subscription.isTrial` comes from the backend flag and is never inferred from tariff name or squad. Consumed unchanged by Аккаунт in W8 |

### 4.3 W6 - Серверы

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Серверы | `fragment_servers.xml`*, `item_recycler_main.xml` (130), `item_section_header.xml` (49), `layout_servers_header.xml` (108), `layout_servers_empty.xml` (67), `item_recycler_footer.xml` | `ui/ServersFragment.kt`*, `ui/MainRecyclerAdapter.kt` | `32` 12, `24` 3.2 | Def, Ld, Emp, Err, Off, Par, Lng, Sht, Suc | Search filters in place and never navigates; the no-results state is designed. Section headers sticky. `RecyclerView` + `ListAdapter` + `DiffUtil`. 40-character server names wrap or ellipsise at the end, never mid-string. Selection reads on two axes, not tint alone |
| Server actions sheet | `sheet_server_actions.xml` (271) | `ui/ServerActionsSheet.kt` | `32` 12.9, `24` A-15 | Def, Gat | Reachable by long-press **and** by an explicit affordance; neutral tiles only (`00-rules.md` 3.6 D-5); locked-template state handled (`template/TemplateManager.isLocked`) |
| Add source | `res/menu/menu_main.xml` -> a sheet | `MainActivity.showImportMenu()` at `:728` moves to `ServersFragment` | `32` 14.3 | Def, Err, Suc | All six add routes present; no route silently lost in the menu-to-sheet conversion |
| Scanner | `activity_none.xml` (an empty `RelativeLayout`), `res/menu/menu_scanner.xml` (two items with `android:title=""`) | `ui/ScannerActivity.kt`, `ui/ScScannerActivity.kt` | `32` 14.4 | Def, Ld, Err, Gat (no camera permission) | The screen has branding, an instruction line and a framing rectangle. `24` A-16 |
| QR sheet | `item_qrcode.xml` | `MainActivity.showQRCode()` at `:1467` moves to `ServersFragment` | `32` 13, `24` A-40 | Def, Err, Lng | QR readable in all three themes; the string under it wraps |
| Deep-link confirm | - | `ui/UrlSchemeActivity.kt` | `32` 14.5, 22.6, `24` A-43 | Def, Err | `depv://import/{base64}` no longer mutates the server list without a confirmation surface |

### 4.4 W7 - Настройки

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Настройки hub | `fragment_settings.xml`*; deletes `layout_settings_content.xml` (1 536), `layout_setting_row.xml`, `layout_setting_toggle_row.xml` | `ui/SettingsFragment.kt`* | `32` 19, `12-settings.md`, `24` 3.4 | Def, Off, Gat | Data-driven rows over a `RecyclerView`, not 1 536 lines of hand-written markup. 16 rows in 4 groups. Every row declares what tapping it will do before it is touched |
| Режим подключения | `activity_connection_settings.xml`* | `ui/ConnectionSettingsActivity.kt`* | `32` 20.1 | Def, Err, Suc | Absorbs the Режим dialog (`MainActivity.pickMode()` at `:2988`), IPv6, bypass-LAN, Always-on and six hidden preferences. No single-choice `AlertDialog` |
| Прокси по приложениям | `activity_app_picker.xml`, `activity_bypass_list.xml`, `item_recycler_bypass_list.xml`, `res/menu/menu_app_picker.xml`, `menu_bypass_list.xml` | `ui/PerAppProxyActivity.kt`, `PerAppProxyAdapter.kt`, `AppPickerActivity.kt`, `AppSelectorAdapter.kt` | `32` 20.2, `24` A-18, A-19 | Def, Ld, Emp, Lng, Sht | App picker merges into the page (`24` A-19); list virtualised; icons loaded off the main thread (`00-rules.md` 11.5) |
| Маршрутизация + правило | `activity_routing_setting.xml`, `activity_routing_edit.xml`, `item_recycler_routing_setting.xml`, `res/menu/menu_routing_setting.xml` | `ui/RoutingSettingActivity.kt`, `RoutingEditActivity.kt`, `RoutingSettingRecyclerAdapter.kt` | `32` 20.3, `24` A-20, A-21 | Def, Emp, Err, Lng, Sht, Suc | The first form page; sets the form pattern the editors reuse in W9. Two levels below the tab, never three (`32` 9.2 depth law) |
| DNS | `activity_dns_settings.xml`* | `ui/DnsSettingsActivity.kt`* | `32` 20.4, `24` A-22 | Def, Err, Suc | Absorbs `MainActivity.editDns()` at `:3102` and `editDnsCustom()` at `:3130`; validation on blur, error below the field |
| Обход блокировок | `activity_circumvention.xml`* | `ui/CircumventionActivity.kt`* | `32` 20.5 | Def, Suc | The inline-reveal pattern; absorbs `toggleMux()` `:3150`, `editMuxConcurrency()` `:3160`, `toggleFragment()` `:3179` |
| Проверка серверов | `activity_ping_settings.xml`* | `ui/PingSettingsActivity.kt`* | `32` 20.6, `24` A-23 | Def, Err, Suc | Absorbs `pickPingMethod()` at `:3038`; the segmented control replaces the dialog |
| Локальный прокси | `activity_local_proxy.xml` (**75 off-scale dp, the worst file in the tree**) | `ui/LocalProxyActivity.kt` | `32` 20.7, `24` A-28 | Def, Err, Suc | Off-scale dp in this file goes to **zero**. A rejected port tells the user why (`docs/agents/verify-local-proxy-port-silent-reject.md`) |
| Провайдеры | `activity_provider_settings.xml` (62 off-scale dp) | `ui/ProviderSettingsActivity.kt` | `32` 20.8, `24` A-24 | Def, Ld, Emp, Err, Off, Par, Lng, Sht, Suc | The provider merge; every provider toggle drives real behaviour (fixed defect 8) |
| Что настроил провайдер | `activity_operator_settings.xml`* | `ui/OperatorSettingsActivity.kt`* | `32` 20.9 | Def, Emp | Every provider-applied setting is listed **and revertable**. `32` 27: "the whole objection to a managed VPN is that someone else controls your connection and every competitor answers that by hiding the control" |
| Файлы ресурсов | `activity_user_asset.xml`, `activity_user_asset_url.xml`, `item_recycler_user_asset.xml`, `res/menu/menu_asset.xml` | `ui/UserAssetActivity.kt`, `UserAssetUrlActivity.kt`, `UserAssetAdapter.kt` | `32` 20.14, `24` A-25, A-26 | Def, Emp, Err, Sht | Add-URL is a sheet over the page, not a third level (`32` 9.2) |
| Оформление / Язык | `activity_appearance.xml`*, `activity_language.xml`* | `ui/AppearanceActivity.kt`*, `ui/LanguageActivity.kt`* | `32` 20.11, 20.12 | Def, Suc | The two simplest `Row.Selectable` pages; **built first and used as the template** for the rest of the wave. All three themes selectable and each verified |
| Резервное копирование | `activity_backup.xml` (26 off-scale dp), `dialog_webdav.xml` | `ui/BackupActivity.kt` | `32` 20.13, `24` A-29, A-30 | Def, Ld, Err, Suc | WebDAV becomes a sub-page, not a dialog |
| О приложении / Журнал / Схемы URL | `activity_about.xml`, `activity_logcat.xml`, `item_recycler_logcat.xml`, `activity_url_scheme_list.xml` (37 off-scale dp), `res/menu/menu_logcat.xml` | `ui/AboutActivity.kt`, `LogcatActivity.kt`, `LogcatRecyclerAdapter.kt`, `UrlSchemeListActivity.kt` | `32` 20.14, `24` A-33, A-34, A-35 | Def, Emp, Lng | Журнал is wired from О приложении (`24` A-35 says "RESTYLE + WIRE") |
| Deleted at the end of W7 | `activity_settings.xml` (16), `res/xml/pref_settings.xml` (354), `preference_with_help_link.xml`, `activity_check_update.xml` | `ui/SettingsActivity.kt` (309), `ui/CheckUpdateActivity.kt` | `32` 19.2, `24` A-36, A-37 | - | Deleted **only after** A-SET-KILL's key-by-key table shows a home or a recorded cut for every one of the ~48 preferences |

### 4.5 W8 - Аккаунт and commerce

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Аккаунт | `activity_account.xml` (560, 17 off-scale dp) -> `fragment_account.xml` | `ui/AccountFragment.kt` (691) | `32` 15, `15-account-tab.md`, `23-account-rework.md`, `24` 3.3 | **all eleven** | One card (the subscription) plus rows (`00-rules.md` 4.4). Tightened per owner request 0.4.5. «Купить» and «Привязать Telegram» present where the state calls for them (0.4.9). «Выйти» has a call site. The four-state hero machine and the cache-first loads survive |
| Купить | `activity_buy_tariff.xml` (309), `item_buy_tariff.xml` (75), `item_buy_option.xml` | `ui/BuyTariffActivity.kt` (662) | `32` 16, `24` A-08 | Def, Ld, Emp, Err, Off, Lng, Sht, Gat, Suc | `currentTotal()` remains the single source of both the shown total and the charged amount. Tariff becomes a selectable **row**, not a card (`32` 16.2). `tv_group_emoji` in `item_buy_tariff.xml` is deleted, not left `GONE` |
| Payment method | `sheet_payment_method.xml` (51), `item_payment_method.xml` | `ui/PaymentMethodSheet.kt` | `32` 15.9, `24` A-11 | Def, Ld, Err, Sht | One payment grammar shared with Купить and top-up |
| Пополнить | `dialog_top_up.xml` (28) -> a sheet | inside `AccountFragment` | `32` 15.8, `24` A-12 | Def, Err, Suc | A sheet, not a dialog (`00-rules.md` 1.4.9) |
| Устройства | `activity_devices.xml` (85), `item_device.xml` | `ui/DeviceManagementActivity.kt`, `ui/adapter/DeviceAdapter.kt` | `32` 17, `24` A-09 | Def, Ld, Emp, Err, Off, Lng, Sht, Gat, Suc | `лимит устройств` is a designed gated state with the unlock action, not an error. Empty copy is `Устройств пока нет` / `Устройства появятся после первого подключения.` with no action (`00-rules.md` 9.5). End-user diagnostic dialogs deleted (`32` 26 wave 3.3) |
| История платежей | `activity_payment_history.xml` (83), `item_payment.xml` | `ui/PaymentHistoryActivity.kt`, `ui/adapter/PaymentsAdapter.kt` | `32` 18, `24` A-10 | Def, Ld, Emp, Err, Off, Lng, Sht | Empty copy `Платежей пока нет` / `Здесь появится история покупок и продлений.`, no action. Raw HTTP codes never shown (`32` 26 wave 3.3). Amounts use the Numeric role, `₽`, thin-space thousands |

### 4.6 W9 - editors and tail

| Screen | Layouts | Kotlin | Spec | States | Done means |
|---|---|---|---|---|---|
| Server form, 9 screens | `layout_address_port.xml`, `layout_transport.xml`, `layout_tls.xml`, `layout_tls_hysteria2.xml` (the four shared includes), then `activity_server_vmess.xml`, `_vless.xml`, `_trojan.xml`, `_shadowsocks.xml`, `_socks.xml`, `_hysteria2.xml`, `_wireguard.xml`, `activity_server_custom_config.xml`, `activity_server_group.xml`, `activity_server_proxy_chain.xml`, `item_recycler_proxy_chain_member.xml`, `res/menu/action_server.xml` | `ui/ServerActivity.kt` (32 KB), `ServerCustomConfigActivity.kt`, `ServerGroupActivity.kt`, `ServerProxyChainActivity.kt`, `ServerProxyChainMemberAdapter.kt` | `32` 21 | Def, Err, Lng, Suc | Rebuild the four includes and nine screens are fixed at once. Label above, error below, validate on blur, no placeholder-as-label (`00-rules.md` 7.4). `V2rayConfig.getProxyOutbound()` behaviour preserved (fixed defect 4) |
| Tasker / widget / QS tile / shortcuts / notification | `activity_tasker.xml`, `widget_switch.xml`, `res/xml/app_widget_provider.xml`, `res/xml/shortcuts.xml` | `ui/TaskerActivity.kt`, `ui/ScStartActivity.kt`, `ScStopActivity.kt`, `ScSwitchActivity.kt` | `32` 22, `24` A-42, A-44 | Def, Err | The unified server icon (0.4.7) is the same in the notification as in the list |
| Перенести подписку / TV | `activity_tv_send.xml`, `activity_tv_receive.xml` | `tv/**` | `32` 20.10, `24` A-31, A-32 | Def, Ld, Err, Suc | Restyled to tokens; the TV shell keeps its own receive screen |

---

## 5. Verification protocol, run at the end of every wave

Four parts. All four, every wave. A wave that skips one is not verified, it is hoped for.

### 5.1 The build gate

```bash
bash /home/user/dp/docs/agents/verify-build.sh android
```

**Pass is all three of these lines, and no fewer:**

| Line | Required value | Why |
|---|---|---|
| `BUILD:` | `SUCCESSFUL` | the obvious one |
| `COMPILER:` | `ran (Kotlin recompiled)` | `verify-build.sh:52-58` prints `COMPILER: UP-TO-DATE - nothing recompiled, so this run proves nothing.` when Gradle skipped `:app:compileFdroidDebugKotlin`. A green run whose compiler never executed is not evidence. If it says UP-TO-DATE, touch the changed files or use `--rerun-tasks` and run again |
| `NEW WARNINGS:` | `0` | compared against `docs/agents/.baseline-warnings.txt` (21 entries) with `:line:col` normalised away, so a line shift is not a regression |

The script serialises Gradle behind `flock /tmp/dep-android-build.lock` (`verify-build.sh:41`), so
parallel agents queue rather than corrupt the build tree. Waiting is correct; running Gradle outside
the script is not.

A resource-only wave still runs the full `assembleFdroidDebug`, not `compileFdroidDebugKotlin`:
`assemble` is what compiles layouts and links the APK, so a malformed layout only fails there.

### 5.2 The mechanical greps, scoped to the files the wave touched

Run from `/home/user/dp/V2rayNG/app/src/main/res`. Scope them to the wave's file list, not to the
whole tree: the whole-tree numbers are debt that other waves retire, and mixing the two makes it
impossible to tell whether this wave made things worse.

```bash
# 00-rules.md 1.5 - raw colour literals. Whole tree is 0 today; keep it 0.
grep -rnE '(android:(textColor|background|tint|backgroundTint|strokeColor)|app:tint|app:strokeColor)="#' <touched layouts>
# all-caps labels. Whole tree is 0 today; keep it 0.
grep -rn 'textAllCaps="true"' <touched layouts> values/
# a face or a size chosen in a layout instead of by the ramp role (D-2)
grep -rn 'android:fontFamily\|android:textSize' <touched layouts>
# off-scale spacing
grep -rnoE '"(-?[0-9]+)dp"' <touched layouts> | grep -vE '"(0|1|2|4|8|12|16|20|22|24|28|32|36|40|44|48|52|56|64|72|80|100|120|152|160|176|212|230)dp"'
# nested cards
grep -rn -A40 '<com.google.android.material.card.MaterialCardView' <touched layouts> | grep -c 'MaterialCardView'
# 00-rules.md 9.7 - dashes and three-dot ellipses in shipped copy
grep -rn -e '—' -e '–' <touched strings files>
grep -rn '\.\.\.' <touched strings files>
# emoji in shipped copy
python3 -c "import glob,re;p=re.compile('[\U0001F300-\U0001FAFF☀-➿]');[print(f,i,l.strip()[:80]) for f in glob.glob('values*/strings*.xml') for i,l in enumerate(open(f,encoding='utf-8'),1) if p.search(l)]"
```

**The bar for the touched files is zero, not "fewer"** (`32` 26, "the four checks that gate every
wave", check 2). The two greps whose whole-tree count is already 0 - raw hex and `textAllCaps` - stay
at 0 tree-wide; a wave that introduces one has regressed the baseline, not inherited debt.

Record the before/after numbers for the touched files in the wave report. `00-rules.md` 17.3: "A
clean grep is a floor, never a verdict."

### 5.3 The state walk

For each screen the wave touched, open it in each state marked for it in section 4 and look at it.
`00-rules.md` 15 and `32` 26 check 3. The four axes that catch the most, run per screen:

- three themes: dark, light, mono;
- font scale 100% and 200%;
- width 320dp and 600dp;
- the longest real Russian string in every label, plus a 40-character server name and a 60-character
  Telegram display name.

### 5.4 The review question that matters most

**Did this wave silently drop a feature?**

This is the question a second agent asks, and it does not trust the first agent's report;
`CONTINUE-HERE.md` 5 records that this method already caught a wrong root cause, an incomplete
User-Agent precedence chain and a crash on a Cyrillic User-Agent. The mechanics:

1. The reviewer lists every user-reachable action on the screen **before** the wave, from the old
   layout and the old Kotlin, not from the wave's report.
2. It matches each one against the new surface.
3. Anything unmatched is either present under a new name, or a **deliberate cut recorded in the
   wave report with a spec reference**, or a defect.

This project has already lost features exactly this way twice, and both are on record:

- `CONTINUE-HERE.md` 4.1: "A compile error was resolved by deleting the `when` branches rather than
  restoring the menu ids, so the implementations are live but unreachable dead code." Nineteen
  actions. (Since restored - see 0.4.)
- `MainRecyclerAdapter.kt:56`: `onItemLongClick` declared, assigned by the Activity at
  `MainActivity.kt:674-675`, and **never invoked**. Five per-server actions and nine editor
  activities unreachable, with no compile error and no warning to announce it.

Neither would be caught by a build gate or by a grep. Only the question catches them.

A second review question, cheaper and worth asking anyway: **does this wave's screen pass the seven
questions of the Departament slop test** (`00-rules.md` 2.4), answered out loud with the screenshot in
front of you. A "yes" on any of 1-6 or a "no" on 7 is rework, not polish.

---

## 6. Known hazards

Every item here is read off the code, not imagined.

### 6.1 The eight Android fixes that must not regress

`CONTINUE-HERE.md` 2 lists ten. Items 9 (the onboarding gate no longer greets a returning user;
`_isEmpty` now means "we know it is empty", never "not loaded yet") and 10 (Windows autostart reads
the registry, clears the `StartupApproved` disable flag, and reconciles at startup) are desktop-only
and out of scope here; they are named so nobody reads this table as eight of ten and assumes two were
dropped. The other eight are Android and are below. Each row says what the fix is, where it lives,
and **why it is shaped the way it is** - because the shape is what a redesign quietly undoes. The
comments quoted are in the source; read them before touching the function.

| # | Fix | Lives in | Why the shape matters | At risk in |
|---|---|---|---|---|
| 1 | Tapping a server selects, never connects | `MainActivity.setSelectServer()` at `:1537`, `promptApplySelectedServer()` at `:1562` | The doc comment is explicit: "Connecting is the connect button's job alone. When a tunnel is already up and the user picks a different server, the running tunnel is left untouched and an explicit 'apply it' action is offered instead, so a tap in the list can never silently tear down a working connection." A redesign that makes the server row "one-tap connect" because it feels faster re-breaks this | W4 (the split), W6 (the row rebuild) |
| 2 | The server-switch race, fixed at its root | `core/CoreServiceManager.stopCoreLoop()` at `:293-313` | `MSG_STATE_STOP_SUCCESS` is sent **after** `stopLoop()` returns, inside the coroutine, not before it. The comment at `:296-301` names both failure modes: VPN mode tore the tunnel down, proxy-only mode "silently kept the PREVIOUS server up while the UI showed the new one". Reordering those two lines for tidiness reintroduces both | Any wave that touches connect flow. **This file is in nobody's ownership list and must stay that way** |
| 3 | Two rows painted as selected | `MainRecyclerAdapter.selectedGuid` at `:142`, `syncSelection()` at `:324`, called from `MainActivity.onResume()` at `:2128-2135` | Selection lives in MMKV, which cannot notify, and is written by subscription import, fast-connect and service start. The comment at `:316-322`: refreshing only the two affected rows "is only correct when BOTH rows are currently in `rows`" - the old row can sit in a collapsed section or have been rebuilt away - "so: fall back to a full refresh whenever either row cannot be located." A rebuild that switches to `ListAdapter` + `DiffUtil` (which `00-rules.md` 11.2 requires) must carry this fallback across, because `DiffUtil` does not know about MMKV either | W6 |
| 4 | A template's server is read from its routing | `dto/V2rayConfig.getProxyOutbound()` at `:520`, `resolveRoutedOutbound()` at `:541` | Operator templates ship several proxy outbounds and pick one with a rule. Reading the first one showed the wrong protocol on the row, made the TCP ping probe a host that is not the server, and made the delay test measure the wrong outbound. Four consumers depend on it: `MainRecyclerAdapter.kt:285`, `fmt/CustomFmt.kt:19`, `viewmodel/MainViewModel.kt:277`, `core/CoreConfigManager.kt:262` | W6 (row rebuild reads `:285`), W9 (editors) |
| 5 | Subscription format negotiation | `MainActivity.importConfigViaSub()` at `:2436` and the fetch path it calls | Precedence is per-subscription override -> provider override -> configured default, with a JSON-first `Accept`. The fetch used to overwrite the caller's value. A rebuild that "simplifies" this to one setting breaks panels that choose XRAY_JSON vs base64 from the User-Agent | W6, W7 (Провайдеры) |
| 6 | Flags require an explicit country marker | `util/FlagUtil.kt`, `CODE_ALIASES` at `:264` maps `UK` -> `GB` | "No flag beats a wrong flag." The comment at `:82` explains that a non-ISO `UK` renders as boxed letters. The unified-server-icon agent (W6, A-SRV-ICON) is the one most likely to rewrite this file wholesale | W6 |
| 7 | Auto-fallback waits for a confirming re-probe | `MainActivity` fields at `:174-195`, `scheduleHealthCheckIfEnabled()` at `:2075`, observer at `:624-633` | The comment at `:175-177`: "A single negative probe is not evidence that the tunnel is dead - one dropped packet on a fine connection would otherwise tear the user off a working server - so the fallback needs two consecutive failures." The re-check re-tests the same conditions so "a tunnel the user stopped meanwhile - or a fallback that already fired - cannot be probed back into action". Three separate regions of one file, which is why the split is the risk | W4 (the split moves fields and observer apart) |
| 8 | Provider-settings toggles drive real behaviour | `ui/ProviderSettingsActivity.kt` | They used to only store a value. A rebuild that regenerates the screen from a preferences model can silently return to storing | W7 (A-SET-PROV) |

Also load-bearing, and not on the CONTINUE-HERE list:

- **The connect watchdog.** `connectWatchdogRunnable` at `MainActivity.kt:195-205`: if a start neither
  succeeds nor reports a failure within `CONNECT_TIMEOUT_MS` - "e.g. the core/daemon process crashed
  without broadcasting any state" - the UI recovers to idle instead of hanging on «Подключение…».
  This is why the state machine cannot live in a Fragment (1.4, conflict 2).
- **`connectInProgress` / live-transition gating**, same file. `32` 9.3 calls the whole cluster
  "careful, correct work" and says preserve it verbatim.

### 6.2 Unreachable features a redesign must restore, not bury

Section 0.5 lists all five with evidence. The hazard is specifically that **four of the five produce
no compile error and no warning**, so a wave can delete their code as "unused" and every gate stays
green:

| Feature | The trap |
|---|---|
| `SettingsActivity` + `pref_settings.xml` (~48 preferences, 29 with no editing UI at all) | It is declared in the manifest, so it looks reachable. Deleting it before W7's key-by-key table exists loses 29 settings silently. This is why A-SET-KILL runs last and owes a table |
| `AccountViewModel.logout()` at `:400` | Zero call sites. An IDE "unused" cleanup deletes it and nobody notices until a user asks how to sign out |
| `CheckUpdateActivity` | Zero call sites. `24` A-36 deletes it on Android while D-34 wires it on desktop. **That is a parity gap and must be written into `24` 7.3, not inherited by accident** |
| RAM panel, `PREF_SHOW_MEMORY` | Read at `MainActivity.kt:2010`, never written anywhere. Either W7 gives it a row or W7 deletes the read and the key together. A dead read that survives the rebuild is the same defect one wave later |
| Per-server actions via long-press | `MainRecyclerAdapter.kt:56` declares the callback; `MainActivity.kt:674` assigns it; nothing calls it. Five actions and nine editor activities unreachable, with a green build. W3 F1 fixes it; W6 must not undo it while rewriting the adapter |

### 6.3 String and localisation coupling

| Hazard | Detail |
|---|---|
| **Nine locale trees** | `values-ru/`, `values-ar/`, `values-bn/`, `values-fa/`, `values-vi/`, `values-zh-rCN/`, `values-zh-rTW/`, `values-bqi-rIR/`, plus `values-sw360dp-v13/`. Deleting a `<string>` from `values/` without deleting it from all of them leaves orphans; renaming one leaves eight stale translations that silently win on those locales |
| **Renames are invisible to the compiler in XML** | A layout referencing a deleted `@string/x` fails the build. A **Kotlin** `getString(R.string.x)` also fails. But a *translation* left behind for a removed key fails nothing at all |
| **321 keys have no `values-ru` entry, 24 of them Latin-only and user-facing** | `CONTINUE-HERE.md` 4.7. `00-rules.md` 1.4.10 bans Latin UI text outside protocol names, brand names, units and technical identifiers. So those 24 are live defects, not backlog |
| **Dash and ellipsis debt is spread across default and translated files** | 23 dash hits and 10 three-dot hits measured today, in `strings.xml`, `strings_pay.xml`, `strings_account.xml`, `strings_local_proxy.xml`, `strings_devices.xml`, `strings_deeplink.xml`, `strings_perapp.xml`, `strings_auth.xml` **and** the translation trees (`00-rules.md` 9.7). A per-screen sweep that only touches `values/` leaves the translated dashes shipping |
| **Hardcoded Russian literals in Kotlin** | `32` 14.2 names two in `MainActivity.showManualEntryDialog()`. Those never appear in any `strings*.xml` grep, so the copy sweep will not find them. Each screen agent greps its own Kotlin for Cyrillic literals: `grep -nP '"[^"]*[А-Яа-яЁё]' <its .kt files>` |
| **Terminology lock 9.3 is global, not per screen** | «тариф», «подписка», «сервер», «провайдер», «подключение», «устройство», «баланс», «Аккаунт», «Купить», «Привязать Telegram», «Войти». A wave that renames a concept on one screen creates two vocabularies. `24` 6.3: "the terminology lock is applied globally rather than per screen" |
| **`res/values/strings.xml` is 485 strings and is not per-screen** | Which is why nobody edits it until W9. A screen agent that needs a new string puts it in its own `strings_*.xml`, even when a nearly identical one exists in the legacy file |

### 6.4 Operational hazards

| Hazard | Mitigation |
|---|---|
| **A container restart kills a wave mid-flight** | This has already happened: a previous run of this documentation wave lost everything because nothing had been written to disk after thirty minutes. **Every agent writes a first draft of its deliverable within its first ten minutes and extends it in place.** For a code agent that means landing the smallest compiling slice early rather than composing the whole change and writing once at the end |
| **Two Gradle runs on one tree contend for locks** | `verify-build.sh:41` wraps the build in `flock /tmp/dep-android-build.lock`. Agents must verify through the script, never by calling `./gradlew` directly. `verify-build.sh:37-38`: "agents share this build tree, and two concurrent Gradle runs on one project contend for the same locks. Waiting for a turn is slow; interleaving is broken" |
| **`libv2ray.aar` is not in the repo and `github.com` is unreachable from this environment** | `V2rayNG/app/libs/libv2ray-stub.jar` is a gitignored type-check stub regenerated by `docs/agents/setup-env.sh`. Never reference it from app code, never commit it, and never reshape app code to fit it (`docs/agents/BUILD-VERIFY.md`) |
| **`compileSdk = 37` needs `platforms;android-37.0`** | `platforms;android-37` does not exist and `sdkmanager` fails with "Failed to find package" (`BUILD-VERIFY.md`) |
| **Agents must not commit** | `CONTINUE-HERE.md` 5: "Do not let an agent commit. Review, build, then commit centrally - concurrent agent commits race" |
| **`styles.xml` is the single worst merge target** | 72 KB, ~110 styles, and every agent wants to append to it. Hence the append-by-request rule in 2.1. Two agents appending concurrently in a container that can restart is precisely how a wave is lost |
| **Line numbers in every spec rot** | `32` 9.3 records that a third of its own `MainActivity.kt` citations had already drifted at revision time. Every line number in this brief is paired with a symbol; resolve by symbol, use the line only as a hint |

### 6.5 Things this brief could not verify

Stated rather than guessed.

| Claim | Why it is unverified |
|---|---|
| The emoji grep on `values*/strings*.xml` | Not re-run; `00-rules.md` 1.5 gives it as a `python3` one-liner and the last recorded result is 0. Re-run it in W9 rather than trusting this document |
| Whether tablet adaptivity exists in any form | `values-sw600dp/` **does not exist** (`ls -d values-*/` shows `values-sw360dp-v13/` and nothing at 600). `Widget.Departament.NavigationRail` was authored today at `styles.xml:947` and **no layout references `NavigationRailView`**. `00-rules.md` 3.1 and 11.4 require a 24dp gutter and a rail at `sw600dp`. The style exists, the qualifier does not, and the rail has no host - but the absence of a resource directory is not proof no adaptivity is implemented, only that none is done by qualifier. Whoever owns W4 confirms and schedules it |
| That the 29-preferences-with-no-UI count is still 29 | Taken from `CONTINUE-HERE.md` 4.2. `res/xml/pref_settings.xml` is 354 lines today; the per-key audit that produces the real number is A-SET-KILL's deliverable in W7, and this brief did not redo it |
| That `nav_press.xml`'s four consumers are still four | Its header comment says "delete it and its four references together". The four are the nav item containers in `activity_main.xml`; whether all four still reference it after W4 is S1's check, not this document's |
| Which of `24` A-36 (delete `CheckUpdateActivity` on Android) and `33-master-plan-pc.md` D-34 (wire it on desktop) the owner actually wants | Two committed specs, opposite verdicts, no owner decision in `00-rules.md` 18. Flagged, not resolved |
| Screenshot-level claims of any kind | Nothing in this environment renders the app. Every "looks like" statement here is inferred from markup and is marked as such |
