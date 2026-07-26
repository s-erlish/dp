# 16 - Серверы

**Departament VPN. The list the user spends the most time in, specified to the pixel, for Android
and for the desktop client.**

| | Android | Desktop |
|---|---|---|
| Repo root | `/home/user/dp` | `/home/user/v2rayN` |
| Paths below are relative to | `/home/user/dp/V2rayNG/app/src/main/` | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/` |
| Route (`11-app-structure.md` 7.1) | `servers` | `servers` |
| Header treatment (`24-tab-conformance.md` 0.2) | H1 | H1 |
| Rhythm (`24-tab-conformance.md` 0.3) | R1 Ledger | R1 Ledger |

**Precedence.** `00-rules.md` is law, and since its section 18 carries twelve ratified decisions its
rule bodies win over any older sentence in a sibling spec. `03-direction.md` outranks this file on
what the product is. `10-design-system.md` outranks it on any token. `22-components.md` outranks it
on any component's anatomy and states. `11-app-structure.md` outranks it on navigation and on what
this destination owns. This file outranks nothing; where those documents disagree with each other or
with the code, it records the resolution in section 19 instead of inventing a third answer.

**Inputs read before writing this.** `00-rules.md` in full, `10-design-system.md`,
`11-app-structure.md` 4.2, `12-settings.md` (5.6, 14), `13-start-screen.md`, `22-components.md`
(8, 10, 13, 15, 16, 17, 18), `24-tab-conformance.md` (0.2, 0.3, 0.4, 3.2, A-13 to A-17, D-09/D-10),
`30-reference-analysis.md` (1.1.6 to 1.2.1, 5.3), `32-master-plan-android.md` 12,
`33-master-plan-pc.md` 6, `docs/ping-methods-design.md`.

**Source read before writing this.** Android: `res/layout/item_recycler_main.xml` (130 lines),
`layout_servers_header.xml` (108), `layout_servers_empty.xml` (67), `item_section_header.xml` (49),
`sheet_server_actions.xml` (271), `res/menu/menu_main.xml`, `res/drawable/bg_server_row.xml`,
`bg_search_pill.xml`, `bg_type_chip.xml`, `custom_divider.xml`, `ui/MainActivity.kt` (3 339),
`ui/MainRecyclerAdapter.kt` (373), `ui/ServerActionsSheet.kt` (72), `ui/ServerGroupActivity.kt`
(170), `viewmodel/MainViewModel.kt` (739), `handler/SpeedtestManager.kt`, `handler/MmkvManager.kt`,
`util/FlagUtil.kt`, `enums/PingMethod.kt`, `dto/entities/ServerAffiliationInfo.kt`,
`values*/strings*.xml`, `values*/colors.xml`. Desktop: `Views/ServersView.axaml` (12),
`CompactServersView.axaml` (116), `ServerListView.axaml` (313) + `.axaml.cs` (939),
`ProfilesView.axaml` (322) + `.axaml.cs` (532), `HomeView.axaml`, `MainWindow.axaml`,
`MainWindow.axaml.cs`, `Common/SimpleViewLocator.cs`, `Common/L.Servers.cs`,
`Assets/GlobalStyles.axaml`, `Assets/GlobalResources.axaml`, `ServiceLib/ViewModels/ProfilesViewModel.cs`,
`ServiceLib/Models/Dto/ProfileItemModel.cs`, `ServiceLib/Enums/ESpeedActionType.cs`,
`v2rayN.Desktop/ViewModels/HomeViewModel.cs`.

**A note on dashes.** `00-rules.md` 1.4.11 forbids em-dashes and en-dashes in UI copy and in these
documents. This file contains none. Hyphen only. One shipped em-dash is named as a defect in 9.4.

**A note on citations.** Every factual claim below was read out of the source on 2026-07-26. The
`.kt` and `.axaml.cs` files were **observed changing while this document was being written** - other
agents are editing both repos right now, and `MainViewModel.kt` grew by 113 lines and
`MainRecyclerAdapter.kt` by 13 between two reads an hour apart - so claims about those files cite the
**symbol** (`MainRecyclerAdapter.bindServer()`), which does not drift, rather than a line number,
which does. Resources, layouts, AXAML markup and token files, which are stable, are cited by line.
A line citation that no longer matches is evidence the file moved, not that the claim was wrong;
re-check by symbol before treating it as a correction.

---

## 0. How to use this document

Section 1 is the job. Section 2 is what dies. Sections 3 and 4 are the frame and the Android tree.
Section 5 is the information architecture and contains the one distinction the current build gets
wrong on one platform and conflates on the other. Section 6 is the row, to the pixel. Section 7 is
the provider group header, 8 the per-item actions, 9 latency, 10 bulk. Section 11 is the state
matrix and is the acceptance surface: a screen not looked at in every row of section 11 is not
finished. Section 12 is motion, 13 every string, 14 accessibility, 15 the Android file map, 16 the
desktop build, 17 parity, 18 the checklist, 19 the decisions.

An implementer must never have to make a visual judgement call. If this document leaves one open,
that is a defect in this document; log it in 19.4.

---

## 1. The job

**One job, and it is a race.** A user opens Серверы because the server carrying the tunnel is not
the one he wants. The screen has to take him from "this one is bad" to "now I am on that one" in
**two gestures and under three seconds**: find the row, tap the row.

Everything else on this screen is subordinate to that. Search exists so the row can be found in one
gesture instead of a scroll. Latency exists so the choice between two rows takes no thought.
Grouping exists so 150 rows read as four short lists. Per-item actions exist, but they are one
deliberate gesture off the fast path and they never occupy it.

**The three questions the screen answers, in this order:**

1. **Which server am I on?** The selected row, and separately whether the tunnel is actually up on
   it.
2. **What else is there, and is it better?** The rows, their provider, their latency.
3. **Where do these come from, and how do I get more?** The provider group headers and one add
   affordance.

**What this screen is not.** It is not a connection screen: the tunnel is raised and lowered on
Главная (`13-start-screen.md` 5). It is not a subscription manager: traffic, expiry and the tariff
live on Аккаунт (`11-app-structure.md` 4.3), and a provider group header carries a name and a count,
not a traffic meter. It is not a config editor: the server form is a sub-page
(`24-tab-conformance.md` A-13).

**The one lit element** (`03-direction.md` 3.2) is **the selected row**. On a populated, ungated
Серверы the accent appears exactly there: a 12 percent accent fill and a 1dp accent border on one
row out of N. Nothing else on the screen is accent: not the header action, not the search glyph, not
the sort control, not the add control, not a latency figure. A screen with two accent objects on it
is a defect.

**No Display figure and no hero.** Latency is a `Subtitle`-sized figure in the Numeric role. The
34sp Display role does not appear on this screen and neither does a card.

---

## 2. What this replaces, and what dies with it

### 2.1 Android

| File | Lines | Verdict |
|---|---|---|
| `res/layout/layout_servers_header.xml` | 108 | **DELETE.** Four 36dp icon buttons crammed against the right edge (`:29-75`), all under the 48dp floor, one of them tinted `?attr/colorPrimary` against H1's "never accent"; a `Headline` 24/700 tab title where H1 declares `Title` 16/700; a hand-rolled `EditText` at a fixed `44dp` height with `textSize="14sp"`, `paddingStart="14dp"` and a filter glyph standing in for a search glyph (`:88-106`). Replaced by section 3 |
| `res/layout/item_recycler_main.xml` | 130 | **REBUILD** as `res/layout/item_server.xml` (section 6). Its emoji flag `TextView` at `textSize="18sp"` (`:40-50`), its zero-size `layout_indicator` `View` (`:34-38`), its `2dp` chip padding (`:82`, `:84`), its `4dp` margins (`:105`, `:114`) and its `12sp` inline size (`:121`) all die |
| `res/layout/item_section_header.xml` | 49 | **REBUILD** as `res/layout/item_provider_header.xml` (section 7). It is 43dp tall in practice (12 + 22 + 4 + padding), carries no count affordance beyond a bare number, has no kebab, and is not sticky |
| `res/layout/layout_servers_empty.xml` | 67 | **DELETE.** A `MaterialCardView` floating at `layout_marginTop="64dp"` inside `padding="24dp"`, with `20dp`/`28dp`/`14dp`/`10dp` off-scale values and a 56dp accent-tinted glyph. Replaced by the one `EmptyState` component (`24-tab-conformance.md` 0.4) |
| `res/drawable/bg_search_pill.xml` | | **DELETE.** `radius 14dp` is not on the scale; D-7 sets the search field at `radius_button` 16 |
| `res/drawable/bg_server_row.xml` | | **REBUILD.** Raw `#1F4C8DFF` twice, `radius_card` 20 on a list row, and a stroke that is 0 at rest, `1.5dp` selected and `2dp` focused, so the row's geometry changes on selection. Replaced by `bg_row_selectable.xml` (6.5) |
| `res/drawable/bg_type_chip.xml` | | **REBUILD** as the neutral chip fill. `?attr/colorPrimaryContainer` under `?attr/chipTypeText` (`@color/chip_type_text` `#4C8DFF` night, `values-night/colors.xml:31`) is the 4.0:1 pair `32-master-plan-android.md` 12.4 measured, at 11sp |
| `res/drawable/custom_divider.xml` | | **DELETE.** Its inset is `left="44dp"`, which matches no text origin in the product. Replaced by an `ItemDecoration` at the 68 origin |
| `res/menu/menu_main.xml` `group_server_list` | | **KEEP the actions, MOVE the menu.** The six whole-list items move out of the "+" popup into the header overflow of 3.1 and 10.2, where "add a source" and "act on the whole list" stop sharing one control |
| `ui/ServerGroupActivity.kt` + `res/layout/activity_server_group.xml` | 170 | **DELETE.** Folded into the one server form (`24-tab-conformance.md` A-13). Its three bare `EditText`s and two `Spinner`s become the library field and the library Select |
| `MainRecyclerAdapter.setData()`, `removeServerSub()`, the `ItemTouchHelperAdapter` stubs | | **DELETE.** `setData` is a shim with one caller shape, `onItemMove`/`onItemMoveCompleted`/`onItemDismiss` all return or do nothing and drag arrives properly in 10.4 |
| `homeAdapter` and `binding.rvHomeServers` (`ui/MainActivity.kt`, `setupServerLists()`) | | **DELETE.** The second instance of this list on Главная is `30-reference-analysis.md` 2.2.8's named IA mistake, inherited. One list, one destination |

**The P0 that ships broken today.** `ui/MainActivity.kt`, `setupServerLists()`, assigns
`serversAdapter.onItemLongClick` and `homeAdapter.onItemLongClick` to `showServerActions(guid)`.
`ui/MainRecyclerAdapter.kt` declares `onItemLongClick` with the comment "The long-press
server-actions menu was removed, so this callback is no longer invoked by the adapter", and
`bindServer()` ends with a `setOnClickListener` and the line "Long-press server-actions menu removed: long-press is a no-op (no
listener set)". The two facts are consistent with each other and fatal:
**`ServerActionsSheet` has no caller in the shipping build**, so a user cannot delete, rename,
share, duplicate, QR or edit one server. `showServerActions()`, `ServerActionsSheet.kt` (72 lines,
fully written) and `sheet_server_actions.xml` (271 lines, fully styled) are all dead code reachable
by nothing. Binding one listener restores six actions. **This lands before any of the
visual work in this document**, as `24-tab-conformance.md` A-15 already schedules it.

### 2.2 Desktop, and which view is canonical

Four files claim to be the server list. Three of them are dead. **Targeting the wrong one wastes the
whole effort, so this is stated first and stated plainly.**

| File | Lines | Instantiated by | Verdict |
|---|---|---|---|
| **`Views/ServerListView.axaml` + `.axaml.cs`** | 313 + 939 | `Views/HomeView.axaml:35`, and by both dead wrappers | **CANONICAL. This is the only server list the user has ever seen on desktop.** REBUILD in place as the list pane of the new destination |
| `Views/ServersView.axaml` + `.axaml.cs` | 12 + 13 | **nothing** | **DELETE.** A wrapper whose whole body is `<local:ServerListView />`. Nothing in `v2rayN.Desktop` references the type; the shell's tab set is `[navHome, navSettings, navAccount]` (`Views/MainWindow.axaml.cs:174`) and has no servers entry |
| `Views/CompactServersView.axaml` + `.axaml.cs` | 116 + 38 | **nothing** | **HARVEST, then DELETE.** It contains the only search field in the desktop product (`:100-108`, two-way bound to `Profiles.ServerFilter`) and the only tab-level header for servers (`:52-86`). Both are harvested into section 16 |
| `Views/ProfilesView.axaml` + `.axaml.cs` | 322 + 532 | registered at `Common/SimpleViewLocator.cs:29`, resolved only by `Manager/WindowDialog.cs:13`, which is never called with a `ProfilesViewModel` | **DELETE.** The upstream v2rayN `DataGrid`. It is the only place several `ProfilesViewModel` commands have ever had a UI, and section 10 gives each of those a home before this file goes |

**Consequences that follow from that table.**

1. **Desktop has no Серверы destination at all.** `Geo.Nav.Servers` is declared at
   `Assets/GlobalResources.axaml:454` and referenced by no view. Servers live in a 440px fixed column
   of `HomeView.axaml:19`, which is `30-reference-analysis.md` 2.2.8 exactly. Section 16 makes
   Серверы rail index 1 and empties that column.
2. **Desktop has no server search in any reachable view.** Adding it is a functional hole being
   closed, not a polish item.
3. `Views/SubscriptionMetaView.axaml` (335 lines) moves here from Главная as the provider group
   header and is rebuilt to section 7; `13-start-screen.md` 2.2 already lists it as MOVE.
4. `Views/HomeView.axaml`'s `ColumnDefinitions="440,1,*"`, its `Brush.HomeGradient` root and its
   embedded `ServerListView` all go, per `13-start-screen.md` 16.

---

## 3. The frame

R1 Ledger (`24-tab-conformance.md` 0.3), with the meta line R3 contributes.

```
?attr/colorBackground  #0A0B0D          <- flat, edge to edge, no drawable
├─ status-bar inset (paddingTop on the header only)
├─ [56]  H1 header: «Серверы» + one 40 overflow           section 3.1
├─ [ 1]  header hairline, alpha 0 until scrollY > 0
├─ [ 8]
├─ [48]  search field                                      section 5.3
├─ [12]
├─ [48]  meta line: count + sort control                   section 5.4
├─ [ 8]
└─ [ * ] the list                                          sections 6, 7
          ├─ provider header 56 (sticky)
          ├─ server row 56 · hairline 1 at origin 68 · server row 56 · …
          ├─ provider header 56 (sticky)
          └─ trailing clearance 24 + bottom-navigation inset
```

**Gutter** 16dp (`@dimen/screen_gutter`), 24dp at `sw600dp` and at desktop content width >= 1000.
**Content max width** 720 (`00-rules.md` 4.1). Rows bleed to the screen edge so their press feedback
does; each row carries its own `paddingHorizontal=@dimen/screen_gutter`.

**One scroll region.** The header, the search field and the meta line do not scroll; the list does.
This is the only screen in the product where the chrome above a scroll region is three blocks tall,
and it is justified: search and sort operate on the list and must stay reachable at row 140.

**Fixed values above the list total 173dp** (56 + 1 + 8 + 48 + 12 + 48). On a 360 x 800dp phone that
leaves 515dp of list against a 24dp status bar and a 64dp navigation bar, which is nine full rows.
At font scale 200 percent the search field and the meta line grow with their text (both are
`minHeight`, never a fixed height, per `00-rules.md` 3.3) and the list shrinks; nothing clips and
nothing is hidden, because the list is the only thing that was ever going to be short.

### 3.1 The header

H1 verbatim: 56 tall, `?attr/colorBackground`, elevation 0, no leading element, title «Серверы» in
`TextAppearance.App.Title` 16/700 at the 16 gutter, and **exactly one** trailing `Button.Icon` at 40
(48 hit box on Android via padding), glyph 24 in `?attr/colorOnSurfaceVariant`, **never accent**.

That one action is the **overflow**, `ic_more_vert_24dp`, accessible name «Ещё». Its contents are in
10.2. Adding a source lives inside it, first, and is not a second header button: `00-rules.md` 4.3
allows one primary action per screen and on this screen that budget is spent on the selected row, not
on chrome.

On scroll, a 1dp `?attr/colorOutlineVariant` hairline fades in under the header over `motion_state`
220ms once `scrollY > 0` and out at 0. That is the only scroll-linked change. No colour, no
elevation, no collapsing title.

---

## 4. Component tree (Android)

Real ids, real styles. `@style/...` and `@dimen/...` names are those of `10-design-system.md` 4.3 and
4.5 and `22-components.md` 19.

```
LinearLayout (vertical)  #servers_root
  background=?attr/colorBackground, fitsSystemWindows handled by the shell

├─ include @layout/header_tab  #header                        [56]  section 3.1
│     TextView #tv_title    @style/TextAppearance.App.Title, text «Серверы», marginStart 16
│     ImageButton #btn_more @style/Widget.Departament.Button.Icon, 40 visual / 48 hit box,
│                           ic_more_vert_24dp, tint ?attr/colorOnSurfaceVariant, cd «Ещё»
│
├─ View #header_hairline                                      [1]   alpha 0 at rest
│     background=?attr/colorOutlineVariant
│
├─ include @layout/field_search  #search                      [48]  marginTop 8, marginHorizontal 16
│     background=@drawable/bg_field  (radius_button 16, 1dp ?attr/colorOutlineControl,
│                                     fill @color/color_surface_inset)
│  ├─ ImageView 20dp  ic_search_24dp   NEW glyph, tint ?attr/colorOnSurfaceVariant, marginStart 12
│  ├─ EditText #et_search  @style/TextAppearance.App.Body, weight 1, marginStart 8,
│  │     hint @string/servers_search_hint, imeOptions=actionSearch, inputType=text, maxLines 1
│  └─ ImageButton #btn_search_clear  40 visual / 48 hit box, ic_close_24dp NEW, GONE while empty
│
├─ LinearLayout (horizontal) #meta_line   minHeight 48, marginTop 12, marginHorizontal 16
│  ├─ TextView #tv_count   @style/TextAppearance.App.Subtitle, weight 1, gravity center_vertical
│  └─ MaterialButton #btn_sort  @style/Widget.Departament.Button.Text, minHeight 48,
│        paddingHorizontal 8, label = the current sort value,
│        app:icon=@drawable/ic_unfold_more_24dp at 20dp, app:iconGravity=textEnd
│
├─ FrameLayout  weight 1                                      [ * ]
│  ├─ RecyclerView #rv_servers
│  │     clipToPadding=false, paddingBottom = 24 + bottom-navigation inset
│  │     LinearLayoutManager, setHasFixedSize(true), stable ids, ListAdapter + DiffUtil
│  │     ItemDecoration 1: hairline 1dp ?attr/colorOutlineVariant, marginStart
│  │                       @dimen/divider_inset_start 68, between two server rows of the
│  │                       same group only - never above a header, never below the last row
│  │     ItemDecoration 2: StickyHeaderDecoration over item_provider_header
│  ├─ include @layout/empty_state  #empty     GONE      section 11
│  └─ include @layout/skeleton_list #skeleton GONE      section 11
```

`#empty`, `#skeleton` and `#rv_servers` are mutually exclusive and occupy the same slot. Exactly one
is visible at any time; the rule that picks is in 11.1.

**Two adapters become one.** `MainRecyclerAdapter` today serves both `rv_servers` and
`rv_home_servers` (`ui/MainActivity.kt`, `setupServerLists()`) with a `showHeaders` boolean deciding whether it
groups. The second list goes (2.1) and the boolean with it: this adapter always groups.

**`notifyDataSetChanged()` goes.** It is called on every path in the current adapter -
`setSections`, `toggleCollapseAll`, `bindHeader`'s click, `removeServerSub` and `syncSelection`'s
fallback, five call sites - which is a P1 performance defect
against `00-rules.md` 11.5 on a 150-row list. `ListAdapter` + `DiffUtil` + stable ids replaces all
five.

---

## 5. Information architecture

### 5.1 Grouping: one group per provider, always

Servers group under the provider that produced them. This is settled (`30-reference-analysis.md`
473: "Servers are grouped under the thing that produced them"), and the current adapter already does
it - with one condition that is removed here.

**Today** `rebuildRows()` (`ui/MainRecyclerAdapter.kt`) suppresses headers entirely when
`showHeaders` is false **or** `distinctProviderCount() <= 1`, so a user with one provider sees a flat
list with no indication of where the servers came from, no per-provider refresh, and no per-provider
actions. **From now on the group header always renders**, at any provider count including one. A
single group is not a decoration: it is the only place the provider's name, its server count, its
refresh and its «Удалить провайдера» live, and 11.9 requires that a one-provider layout not look
broken.

Group order:

1. Pinned providers, in pin order.
2. The remaining providers, in the order the store holds them (`subs.map { it.id }`,
   `ui/MainRecyclerAdapter.kt`, `rebuildRows()`).
3. **«Добавленные вручную»** last: every server whose `subscriptionId` is empty or names a provider
   that no longer exists. The current code already computes this bucket (`:118-124`) and labels it
   `servers_section_local` = «Локальные», which is renamed (13.1).

Collapse state is per group, survives Back, a tab switch, rotation and a provider refresh, and is
**not** persisted across a cold start: a collapsed group on launch hides servers the user forgot he
collapsed a week ago.

**We refuse the per-subscription tab strip** (`30-reference-analysis.md` 1.2.1). A horizontal strip
hides the provider count, scans worse than a vertical list on a phone, and makes one control mean
both "filter" and "where am I".

### 5.2 The selected server and the connected server are two facts

**They are not the same thing and the UI must never conflate them.** This is the single most
important paragraph in this document.

What the code does today:

| | Android | Desktop |
|---|---|---|
| What a tap does | `MainRecyclerAdapter.bindServer()` calls `onSelectServer(guid)` -> `MainActivity.ActivityAdapterListener.onSelectServer` -> `setSelectServer()`, which writes MMKV and repaints. **It does not connect.** If a tunnel is already up it calls `promptApplySelectedServer()`, which asks whether to move the running tunnel onto the new pick; declining leaves the connection exactly as it was | `ServerListView.axaml.cs`, `OnRowPointerReleased`, calls `HomeViewModel.SelectServer()` (`ViewModels/HomeViewModel.cs:246`), which sets the default **and connects or reconnects**: `willConnect = changed \|\| !wasConnected` (`:268`), then `BeginConnecting()` |
| What the row highlight means | `state_selected` on `bg_server_row` from `guid == selectedGuid` (`MainRecyclerAdapter.bindServer()`), where `selectedGuid` mirrors `MmkvManager.getSelectServer()` | `Classes.selected="{Binding IsActive}"` (`ServerListView.axaml:141`), where `IsActive = t.IndexId == _config.IndexId` (`ServiceLib/ViewModels/ProfilesViewModel.cs:473`) |
| Is "the tunnel is up on this row" drawn anywhere in the list | **no** | **no** |

So both platforms paint **selection** and neither paints **connection**, and on Android the two
routinely disagree: select a row while connected, decline the prompt, and the highlighted row is not
the row carrying your traffic. That is the state the user is most likely to be confused by and it is
currently invisible.

**The decision.** Both facts are drawn, on different channels, on the same row:

| Fact | Meaning | Channel |
|---|---|---|
| **Selected** | this is the server the next connect will use | 12 percent accent fill + 1dp accent border + title weight 700 (6.5) |
| **Connected** | the tunnel is up **on this server right now** | a `Chip.Status.Ok` reading «Подключено» on the subtitle line, replacing that one row's transport caption (6.4) |

At most one row carries each. In the common case one row carries both, and the row reads as fill plus
chip. When they disagree the screen says so without a word of explanation, and Главная's status line
agrees with the chip, never with the fill.

**Tap behaviour is unified: a tap selects, it does not connect.** Desktop's connect-on-tap is
removed. Three reasons: it makes an accidental click on a scrolling list tear down a live tunnel; it
gives the same gesture two meanings depending on connection state (`HomeViewModel.cs:268`); and it
contradicts Android, so a user who learns one client is punished in the other, which
`00-rules.md` 13 forbids. Connecting to a specific server from this screen stays available as the
first item of the per-item actions («Подключиться», 8.2) and on Android as the existing
`promptApplySelectedServer` snackbar path.

### 5.3 Search

**One field, filters in place, never navigates** (`00-rules.md` 4.6).

| Property | Value |
|---|---|
| Geometry | `minHeight` 48, full width at the gutter, `radius_button` 16 (D-7) |
| Fill | `?attr/colorSurfaceContainerHighest` `#20242B` dark, `#E3EAF4` light: the P3 inset plane, the same tone a chip and the connect disc use |
| Border | 1dp `?attr/colorOutlineControl` (D-9). **The colour exists, the attribute does not**: `md_theme_outlineControl` is declared at `values/colors.xml:215` and `values-night/colors.xml:113` but `values/attrs.xml` declares no `colorOutlineControl` and no theme wires it. Adding both is a prerequisite of this screen, not an extra. Note the warning in the token's own comment: on the P3 plane it measures 2.95:1 dark, so this field's fill is P3 **and** its border must be lifted, or the fill dropped to `?attr/colorSurface`. **Decision: the field sits on `colorSurface` `#141619`, where the border measures 3.43:1** |
| Leading | 20dp `ic_search_24dp` in `?attr/colorOnSurfaceVariant`, 12 from the field edge, 8 to the text. The current build uses `ic_outline_filter_alt_24` (`layout_servers_header.xml:105`), a filter glyph on a search field |
| Trailing | 20dp `ic_close_24dp` inside a 48 hit box, `GONE` while the field is empty |
| Text | `TextAppearance.App.Body` 14/400. Placeholder `?attr/colorOnSurfaceVariant`, which clears the 4.5:1 placeholder floor of `00-rules.md` 6.8 |
| Hint | «Поиск серверов» |
| Debounce | 120ms. Android's current watcher fires `filterConfig()` on every keystroke (`MainActivity.setupServersHeader()`), and `filterConfig` calls `reloadServerList()` (`MainViewModel.kt`), which re-decodes the whole store |

**What it matches**, case-insensitive, in this order of usefulness: the server remark, the provider
name, the protocol, the address. It does not match the port or the guid.

**Search narrows groups, it does not flatten them.** A group with no matching server is hidden
entirely; a group with matches keeps its header and shows only the matches, with its count showing
the matched number. Collapse state is ignored while a search is active: a collapsed group that
contains a match expands for the duration of the search and returns to collapsed when the field is
cleared. A user who typed a query has asked to see the results, not to be told a group is closed.

**The field keeps its text and its focus in the no-results state** (11.5). Back with a non-empty
search clears the search first and leaves the destination second (`00-rules.md` 7.7: Back restores
filter state).

**Search defines the scope of every bulk action** (10.1). This is what makes bulk safe on a phone
without multi-select.

### 5.4 Sort

A **picker**, not a cycling control. `12-settings.md` D-S2 retired cycle-in-place across the product
because it advances a value blindly: the user cannot see the option set, cannot jump to an option,
and must tap n-1 times to reach the last one. `11-app-structure.md` 4.2 and `24-tab-conformance.md`
3.2 both specify a cycling text button here; D-S2 is newer and wins (19.1, decision V-3).

The control is a Tertiary button on the meta line whose **label is the current value**, with a 20dp
`ic_unfold_more_24dp` trailing glyph. It opens a bottom sheet (Android) or a flyout (desktop) with
radio semantics and one 20dp `ic_check` on the current option.

| Option | Order | Notes |
|---|---|---|
| «Как у провайдера» | the stored order for that provider | **Default.** Mirrors the `sort-order: none` both reference protocols carry |
| «По задержке» | ascending measured latency; unmeasured and failed rows sink to the bottom in their provider order | See 9.6 for what happens when nothing has been measured |
| «По названию» | natural sort on the remark, `ru_RU` collation, digits compared as numbers so «Node 2» precedes «Node 10» | |
| «Вручную» | the user's own order | Selecting it enables drag and `Alt+Up` / `Alt+Down` (10.4). It is the only order in which the stored order is rewritten |

Four options, and «По протоколу» is deliberately absent: typing `vless` into the search field does
the same job in one gesture, and a fifth option pushes the picker past the point where scanning it
is slower than typing.

**Sort is a view order, applied per provider, persisted per provider, and it does not rewrite
storage.** Today «Сортировать по задержке» calls `MainViewModel.sortByTestResults()`
(`viewmodel/MainViewModel.kt`, `sortByTestResults()` and `sortByTestResultsForSub()`), which for every subscription rewrites the stored server list
with `MmkvManager.encodeServerList(sortedServerList, subId)`. A sort that silently and permanently
destroys the provider's own order is a surprise the user cannot undo, and it is why the same action
has to exist twice today (as a sort and as a bulk action). Here it exists once, as a view state, and
only «Вручную» writes.

### 5.5 Filter

**There is no protocol filter and no filter chips row.** `MainViewModel.applyProtocolFilter()`
(`viewmodel/MainViewModel.kt`) and its `protocolFilter` field exist and are called by
nothing; the chips they were written for were never built. They are deleted with the rest of 2.1.

The reasoning is 5.3: search already filters by protocol because the protocol is one of the four
things it matches, and a permanent chip row would cost 48dp of every session to serve a filter most
users never use. A filter that is one word of typing away does not need chrome.

---

## 6. The server row

`res/layout/item_server.xml` (Android), `Views/Servers/ServerRow.axaml` (desktop). One definition,
one anatomy, both platforms.

```
[16][ 40 tile, r12 ][12][ text column, weight 1 ][12][ trailing 64 reserved ][16]
                          Title     16/700, 1 line, ellipsize end
                          Subtitle  chip + caption, or chip + status chip
```

| Property | Value |
|---|---|
| Min height | `row_min_height` 56, grows with a two-line title, never clips |
| Vertical padding | `space_12` 12 |
| Horizontal padding | `screen_gutter` 16, inside a full-bleed row |
| **Text origin** | **68** = 16 + 40 + 12. Every row, every header, every hairline on this screen shares it |
| Divider | 1dp `?attr/colorOutlineVariant`, inset 68, **between two server rows of the same group only**. Never above a group header, never below the last row of a group, never under the tile |
| Background | `@drawable/bg_row_selectable` (6.5) |
| Press | **background step to `?attr/colorSurfaceContainerHigh`, not a scale** (`00-rules.md` 7.1 R5). A row is a slice of a surface with hairlines above and below it; scaling it tears them. Today Android scales (`item_recycler_main.xml:28`) and desktop scales (`Assets/GlobalStyles.axaml:929-931`). Both change |
| Target | the whole row |

### 6.1 The unified server icon

Owner request 0.4.7 and `00-rules.md` 10.5, one treatment everywhere a server appears: the list row,
the actions sheet header, the Главная status line and the ongoing notification.

```
40dp tile, radius_tile 12, fill @color/icon_tile_neutral
└─ 28dp country flag, circular-masked, centred
   or, when no country can be resolved:
   22dp @drawable/ic_globe_24dp in @color/icon_glyph_neutral
```

**The flag is a raster asset, not an emoji.** Android has no flag assets at all today:
`util/FlagUtil.kt` resolves a regional-indicator **emoji** pair and falls back to the globe emoji
the globe emoji U+1F310 (`:18`), rendered by a `TextView` at `textSize="18sp"` (`item_recycler_main.xml:42-50`). That is
`00-rules.md` 1.4.4 (no emoji as UI chrome) and it also renders differently on every OEM font.
Desktop already ships `Assets/Flags/*.png` with an `xx.png` fallback, resolved by
`Common/FlagResolver.cs` and `Converters/RemarkToFlagConverter.cs`.

**Decision: the 16-flag desktop set is ported to Android as `res/drawable-nodpi/flag_*.png` and both
platforms resolve from the same country code.** `FlagUtil` keeps its country-detection logic
(bracketed `[NL]`, country and city names, a leading upper-case token) and loses its emoji output;
`extractFlagEmoji` and `stripLeadingFlag` survive because a provider remark still arrives with an
emoji in it and that emoji must be stripped from the name (6.2). A country with no asset falls back
to the globe glyph, never to a letter pair and never to `xx.png` on Android.

Sixteen flags is a small set for a product whose providers name dozens of countries. Growing it is a
content task, not a design task; the design is the tile and the fallback, and the fallback is not a
failure state.

**The icon never changes on selection** (`22-components.md` 18.1). No accent ring, no tint, no swap.

### 6.2 The title line

`TextAppearance.App.Title` 16/700 in `?attr/colorOnSurface`, one line, `ellipsize="end"`.

The leading flag emoji is stripped by the same transform that fills the tile
(`FlagUtil.stripLeadingFlag`, already called in `MainRecyclerAdapter.bindServer()`;
`StripLeadingFlagConverter` on desktop, `ServerListView.axaml:193`). The flag renders once, as the
tile. A remark that is nothing but a flag renders its raw remark, because an empty title is worse
than a duplicated flag.

**One line, not two.** `33-master-plan-pc.md` 6.5 allows the title to wrap to two lines and the row
to grow to 72. This document holds it at one: a list whose rows are different heights cannot be
scanned by latency, which is the column the user is actually comparing, and a 40-character remark
(11.8) is legible truncated at the end because the distinguishing part of a provider remark is at
the front. Resolution V-6.

**A 40-character name** («Нидерланды, Амстердам, резервный узел 2») ellipsises at the end at 360dp
width with the trailing slot reserved. Never in the middle, never with the tail preserved.

### 6.3 The protocol chip

**`@style/Widget.Departament.Chip.Technical`, which already exists** (`res/values/styles.xml:866`,
parent `Widget.Departament.Chip` at `:843`): `chipMinHeight` 24, `chipStartPadding` /
`chipEndPadding` `space_8`, `chipStrokeWidth` 0, `ShapeAppearance.Departament.Fitting` (radius 12),
fill `?attr/colorSurfaceContainerHighest` `#20242B`, label `TextAppearance.App.Chip` 11/500 in the
brand face, text `?attr/colorOnSurfaceVariant` - **6.00:1** (`00-rules.md` 3.5). The whole chip
system of `22-components.md` 10 is already implemented in `styles.xml:843-899`; the server row is
one of the surfaces that has not adopted it.

It replaces the hand-rolled `TextView` + `bg_type_chip` + `?attr/chipTypeText`, which is `#4C8DFF`
on `colorPrimaryContainer` `#17325C`: 4.0:1 at 11sp, a failure, and additionally an accent object competing with the selected
row for the screen's one accent (`32-master-plan-android.md` 12.4).

Content: the protocol, upper-case as a technical token in the brand face - `VLESS`, `VMESS`,
`TROJAN`, `SS`, `SOCKS`, `HY2`, `WG`. A policy group reads `Auto`, a proxy chain reads `Chain`, a
custom config reads the wrapped outbound's real protocol and falls back to `Custom`
(`MainRecyclerAdapter.primaryProtocol()`, kept verbatim). The gold `JSON` chip variant is deleted;
the word already carries the meaning.

The chip never shrinks and never ellipsises. It is `wrap_content` and the caption beside it yields.

### 6.4 The subtitle line

Two mutually exclusive compositions, both 8 from the chip:

| Row state | Subtitle |
|---|---|
| Ordinary | the protocol chip + 8 + `TextAppearance.App.Subtitle` caption «Reality · TCP» in `?attr/colorOnSurfaceVariant`, weight 1, `ellipsize="end"`, `GONE` when it would render fewer than 6 characters |
| **This row is carrying the tunnel** | the protocol chip + 8 + `@style/Widget.Departament.Chip.Status.Ok` «Подключено» (fill `?attr/colorTertiaryContainer`, label `@color/color_success_text`, which is `#22C55E` on dark and `#065132` on light per D-10). The transport caption is dropped for this one row |

The transport caption is the least valuable string on the screen and the connected row is the one row
whose transport the user is currently experiencing rather than choosing. Trading it for the
connection state costs nothing and buys the fact section 5.2 exists for. Colour is never the only
signal: the chip carries the word (`00-rules.md` 6.3).

### 6.5 Selection, on two axes and no geometry change

`22-components.md` 18.1, minus its trailing check, which this row spends on latency instead (19.1,
decision V-4).

| Axis | Unselected | Selected |
|---|---|---|
| Fill | transparent | `@color/accent_fill_12` `#1F4C8DFF` dark / `#1F1E5FC7` light |
| Border | none | **1dp** `?attr/colorPrimary` |
| Title weight | 700 | 700, unchanged |
| Radius | `radius_button` 16 | 16, unchanged |

Three notes, each of which is a current defect:

- **The border width never changes.** `bg_server_row.xml` draws 0 at rest, `1.5dp` selected and
  `2dp` focused, and desktop carries a permanent `BorderThickness="1.5"`
  (`Assets/GlobalStyles.axaml:911`) so the colour can change without a reflow. 1dp in one state and
  nothing in the other, with the row's padding unchanged, reflows nothing either.
- **The radius is 16, not 20.** `bg_server_row.xml` uses `@dimen/radius_card` 20 and
  `ServerListView.axaml:143` uses `Radius.Search` 14, which D-7 retired. 16 is the control radius and
  a selectable row is a control.
- **The title weight does not carry selection here**, because the row title is already 700 in every
  state (`22-components.md` 8.1). The third axis is the border. Fill plus border plus the fact that
  only one row in the list can ever have either is more than the two axes `00-rules.md` 7.1 demands.

**Never a left stripe** (Absolute Ban 1.1), never a scale change, never a shadow, never a second
badge repeating what the fill says. The zero-size `layout_indicator` `View`
(`item_recycler_main.xml:34-38`) that survives only so `MainRecyclerAdapter.bindServer()` can call
`setBackgroundColor` on it is deleted with the binding.

**Focus** (`00-rules.md` 7.1 R7, mandatory on both platforms): a 2dp `?attr/colorPrimary` ring drawn
**outside** the row at 2dp offset, radius 18. Not the current 2dp inset stroke that changes the row's
apparent size, and not a state that only the TV activities get.

### 6.6 The trailing slot

**Exactly one element, `@dimen/value_w_ping` 64dp reserved in every state** so a result landing on
row 3 moves nothing on rows 1 to 150.

| Row state | Trailing content |
|---|---|
| Measured, fresh | `TextAppearance.App.Subtitle` + Numeric role, 13/500 brand face, `tnum lnum zero` on, right-aligned, `?attr/colorOnSurface`: «48 мс» |
| Measured, stale | the same figure in `?attr/colorOnSurfaceDim` (9.5) |
| Never measured | **empty**. Not `0`, not `0 мс`, not a dash, not «n/a» |
| Measuring | a 20dp indeterminate arc in `?attr/colorPrimary` at `motion_spin` 1100 linear |
| Failed | «нет ответа» in `?attr/colorOnSurfaceVariant`, `TextAppearance.App.Caption` 12/400, right-aligned, wrapping never |

Latency is neutral ink, not a green or red value. Green in this product means «подключено» or
«оплачено» and red means destroyed or broken; spending either on a latency band gives the colour a
third meaning, which `00-rules.md` 6.2 forbids. The current build colours every reading
`?attr/pingGood` or `?attr/pingBad` (`MainRecyclerAdapter.bindServer()`) and desktop colours it by
theme ink (`DelayInkConverter`, `ServerListView.axaml.cs:899`). Only the failure is distinguished,
and it is distinguished by the **word**, so colour is never the only signal.

### 6.7 What is deliberately not on the row

Each of these was considered and refused, with the reason.

| Not on the row | Why |
|---|---|
| A kebab or any per-item icon button | The row's press target is the row. A trailing button inside a row that is itself a target is the two-targets-that-do-different-things mistake `22-components.md` 8.4 names. Desktop's hover kebab (`33-master-plan-pc.md` 6.5) is refused for the same reason: right-click, the `Menu` key and long-press are the three routes, and 8.4 makes them discoverable without spending the row |
| The address and port | It is not a choosing criterion, it is 25 characters wide, and it is one tap away in «Изменить» |
| A speed value | Nothing measures per-server throughput outside `SpeedServerCmd`, which runs one server at a time for tens of seconds. A column that is empty for 149 of 150 rows is not a column |
| A traffic meter or an expiry date | Those belong to the provider, not the server, and they live on Аккаунт |
| A country name as text | The flag tile carries it, and the remark usually repeats it |
| A selection checkbox at rest | Multi-select is a mode, not a permanent affordance (10.3) |
| A status dot beside «Подключено» | The chip already carries colour and the word. A dot next to a label that states the status is the decoration tell (`03-direction.md` 2.4) |
| Drag handles | Visible only in «Вручную» (10.4) |

---

## 7. The provider group header

`res/layout/item_provider_header.xml` (Android), `Views/Servers/ProviderGroupHeader.axaml`
(desktop). This is what `Views/SubscriptionMetaView.axaml` becomes when it moves off Главная, and
what `item_section_header.xml` becomes.

```
[16][ 40 chevron button ][ 8 ][ text column, weight 1 ][12][ 40 kebab ][16]
                                Title     provider name, Title 16/700, 1 line, ellipsize end
                                Subtitle  «84 сервера», Subtitle 13/400
```

| Property | Value |
|---|---|
| Height | **56** (`row_min_height`). It has to hold a 40dp target with padding; 40 cannot and 48 gives it 4dp |
| Background | `?attr/colorBackground`, opaque, because it is sticky and must not let rows show through |
| Sticky | yes (`00-rules.md` 4.6: sticky when the list is long enough to lose context) |
| Leading | `Button.Icon` 40, `ic_arrow_drop_down` 20dp in `?attr/colorOnSurfaceVariant`, rotation 0 expanded / -90 collapsed |
| Trailing | `Button.Icon` 40, `ic_more_vert_24dp` 24dp in `?attr/colorOnSurfaceVariant`, accessible name «Действия провайдера» |
| Target | The **text column and the chevron** toggle the group. The kebab is its own target. Two targets, and they are 12 apart, which clears the 8dp separation floor |

Three trailing values conflict across the foundation documents (40 in `11-app-structure.md` 4.2 and
`24-tab-conformance.md` 3.2, 48 in `32-master-plan-android.md` 12.5, 56 in `33-master-plan-pc.md`
6.7); the resolution is 19.1, decision V-5.

**The subtitle is a count and nothing else.** «84 сервера», through the `plural_servers` plurals of
13.1. Not traffic, not an expiry date, not a tariff: a provider's traffic and expiry are subscription
facts and they live on Аккаунт, where the user can act on them. The 160px `Border.TrafficPill` that
prints an 11sp label on a moving `LinearGradientBrush` at 2.9:1 (`30-reference-analysis.md` 5.4) is
deleted here rather than ported.

**Conditional additions to the subtitle**, at most one at a time, highest first:

| Condition | Subtitle becomes | Colour |
|---|---|---|
| The last refresh failed | «Не обновился» | `@color/color_destructive_text` |
| Data is being shown offline | «Данные могли устареть» | `?attr/colorOnSurfaceVariant` |
| A refresh is running | «Обновляется…» plus a 16dp arc in place of the kebab glyph | `?attr/colorOnSurfaceVariant` |
| Search is active | «Найдено: 6 из 84» | `?attr/colorOnSurfaceVariant` |

**The pinned provider is not marked with a badge.** Pinning sorts it first and makes it the group the
screen opens on; being first **is** the signal (`30-reference-analysis.md` 1.1.5). A chip saying
«Закреплён» on the first item in a list is the decoration tell.

**«Добавленные вручную»** renders the same header with no kebab-only actions that do not apply:
«Обновить», «Переименовать», «Открыть ссылку», «Настройки провайдера» and «Удалить провайдера» are
absent; «Проверить задержку» remains.

---

## 8. Per-item actions

Order of preference is law (`00-rules.md` 7.6): inline > expandable row > sheet or flyout > dialog.
Everything in this section is a sheet on Android and a flyout on desktop, and **exactly one dialog
exists on this whole screen** (10.2, «Удалить все серверы»).

### 8.1 How the actions are reached

| Route | Android | Desktop |
|---|---|---|
| Primary | **long press** on the row. This is the P0 rewire of 2.1 | **right click** anywhere on the row |
| Keyboard | the `Menu` key or `Shift+F10` on the focused row | the same |
| Discoverability | the first long press in a session raises a one-time snackbar «Удерживайте сервер, чтобы открыть действия», dismissible, never shown again | the row's cursor is `Hand` and the flyout is the platform idiom; no hover kebab (6.7) |

**Undo, not confirmation** (`00-rules.md` 7.5). Deleting one server out of 147 is not irreversible
and costly: it removes immediately and a snackbar (Android) or toast (desktop) offers «Отменить» for
5 seconds. Desktop's current `ShowYesNoInteraction` confirm (`Views/ServerListView.axaml.cs`, `RegisterInteractions()`)
is replaced. Android's `runBulkDelete` snapshot-and-restore machinery
(`ui/MainActivity.runBulkDelete()`, `snapshotServers()`, `undoBulkDelete()`) is already the right
shape and is reused for the single delete.

### 8.2 The sheet, in order

H4 sheet header (`24-tab-conformance.md` 0.2): the **unified server icon** at 40, then the server
name as `Title` 16/700, so the sheet and the row agree about what a server looks like. Drag handle
36x4 on Android. `radius_sheet` 24 top, scrim 60 percent.

| # | Row | Glyph | Kind | Notes |
|---|---|---|---|---|
| 1 | «Подключиться» | `ic_shield_outline` | `Row.Action` | Selects **and** raises the tunnel on this server. The one place on this screen that connects |
| 2 | «Сделать основным» | `ic_action_done` | `Row.Action` | Selects without connecting. Hidden when the row is already selected |
| 3 | «Проверить задержку» | `ic_ping_24dp` | `Row.Action` | One server, the method of 9.1 |
| 4 | «Изменить» | `ic_edit_24dp` | `Row.Navigation` | Opens `servers/server/{guid}` |
| 5 | «Дублировать» | `ic_copy` | `Row.Action` | Appends « (копия)» to the remark, as today (`ui/MainActivity.showServerActions()`) |
| 6 | «Переместить» | `ic_arrow_drop_down` | `Row.Navigation` | **Present only while the sort order is «Вручную»** (10.4). Opens the move sub-sheet |
| 7 | «Скопировать ссылку» | `ic_link_24dp` NEW | `Row.Action` | Reports «Ссылка скопирована» |
| 8 | «Поделиться QR-кодом» | `ic_qu_scan_24dp` | `Row.Action` | Opens the QR dialog |
| | hairline, inset 68 | | | |
| 9 | «Удалить сервер» | `ic_delete_24dp` | `Row.Destructive` | Title in `@color/color_destructive_text`, **neutral tile** (`22-components.md` 8.6: a red tile plus red text is the same signal twice). Removes, then «Сервер удалён» · «Отменить» |

Nine rows, one of them conditional. Every row is a `Row.Action` / `Row.Navigation` /
`Row.Destructive` from `22-components.md` 8, with a neutral 40 tile, the 68 text origin, press
feedback and a focus ring. The six hand-rolled `LinearLayout` rows of `sheet_server_actions.xml`
have none of those today, and five of them carry a **blue** `bg_icon_blue` tile
(`sheet_server_actions.xml:60`, `:98`, `:136`, `:174`, `:212` - five of the six rows) against D-5, which allows the accent tile only on the one lit row of a screen.

**Order rationale.** The two that change what you are connected to are first, because that is the
job (section 1). Measurement is third because it feeds that decision. Editing and duplicating are the
middle. Sharing is near the end because it serves a different person. Destroying is last, after a
hairline, alone.

**Locked profiles.** `TemplateManager.isLocked(profile)` hides rows 4, 5, 7 and 8, and keeps 1, 2, 3
and 9. This is the existing guard (`ui/ServerActionsSheet.kt:48-53`) with «Проверить задержку» added
to the kept set: measuring a locked server tells the user nothing he is not allowed to know.

**Desktop flyout: the same nine rows, the same order, the same labels**, as a `MenuFlyout` with 40px
rows, a 20px leading glyph and a 12 gap, with the shortcut right-aligned in the item text where one
exists (`Enter` on «Подключиться», `Ctrl+P` on «Проверить задержку», `Delete` on «Удалить сервер»).
`33-master-plan-pc.md` 6.6.1's twelve items in five groups with three submenus is reduced to this on
purpose: a flyout with submenus is a menu bar, and the four extra export formats it nests
(«Ссылка в Base64», «Конфиг в файл…», «Конфиг в буфер», «Внутренняя ссылка») serve a user moving a
config into another client, which is a task for the server form's own overflow, not for the fast
path. Resolution 19.1, decision V-8.

### 8.3 The provider kebab

Sheet (Android) or flyout (desktop), header = the provider name.

| # | Row | Kind |
|---|---|---|
| 1 | «Обновить» | `Row.Action` |
| 2 | «Проверить задержку» | `Row.Action`, scoped to this provider's servers |
| 3 | «Переименовать» | `Row.Action`, inline field in the sheet, not a dialog |
| 4 | «Закрепить» / «Открепить» | `Row.Action`, the label is the next action |
| 5 | «Открыть ссылку» | `Row.Action` |
| 6 | «Настройки провайдера» | `Row.Navigation`, opens `servers/provider/{id}` |
| | hairline, inset 68 | |
| 7 | «Удалить провайдера» | `Row.Destructive`, removes with «Провайдер удалён» · «Отменить» |

Deleting a provider is undoable because it is re-addable from the link, which the app still holds
until the undo window closes (`24-tab-conformance.md` A-14 already takes this position).

### 8.4 The add sheet

One sheet, one entry point for every import path in the product, opened from the header overflow's
first row.

| Row | Glyph | Wires to |
|---|---|---|
| «Добавить провайдера» | `ic_cloud_download_24dp` | The provider form, `servers/provider/new` |
| «Сканировать QR-код» | `ic_scan_24dp` | `importQRcode()` |
| «Вставить из буфера» | `ic_dl_copy` | `importClipboard()` |
| «Ввести ссылку» | `ic_edit_24dp` | `showManualEntryDialog()` |
| «Импортировать из файла» | `ic_file_24dp` | `importConfigLocal()` |
| «Создать вручную» | `ic_add_24dp` | `pickManualServerType()` -> the server form |

Six rows, all six already implemented and dispatched from `onOptionsItemSelected`
(`ui/MainActivity.onOptionsItemSelected()`). «Отправить на TV» (`R.id.tv_send`) leaves this menu: it sends
**to** a TV, it does not add a server here, and `12-settings.md` 5.13 owns `settings/tv`.

---

## 9. Ping and latency

### 9.1 The methods that exist, and the two that survive

`enums/PingMethod.kt` declares four and `MainViewModel.testAllServers()`
(`viewmodel/MainViewModel.kt`, `testAllServers()`) dispatches all four on Android:

| Enum | Pref | Probe | Through the tunnel | Implemented |
|---|---|---|---|---|
| `PROXIED_REAL_DELAY` | `real` | HTTP/204 through a throwaway core built from the server's own config | **yes** | Android `testAllRealPing()` via `CoreTestService`; desktop `ESpeedActionType.Realping` |
| `TCP_CONNECT` | `tcp` | TCP handshake to `host:port`, best of two, 3s timeout (`handler/SpeedtestManager.tcping()` / `socketConnectTime()`) | no | Android `testAllTcping()`; desktop `ESpeedActionType.Tcping` |
| `HTTP_URL` | `http` | direct HTTPS to the node, any response counts, 24 in parallel (`testAllDirectHttp()`) | no | **Android only.** `ServiceLib/Enums/ESpeedActionType.cs` has no HTTP probe |
| `ICMP` | `icmp` | ICMP echo, 12 in parallel (`testAllIcmp()`) | no | **Android only.** No desktop equivalent |

`Views/ServerListView.axaml.cs`, `ResolvePingAction()`, resolves the desktop picker's value and falls back to
`Realping` for anything that is not `Tcping`, with a comment stating that the HTTP and ICMP rows in
`PingSettingsPage.axaml` are dead options.

**Decision, already taken by `12-settings.md` 5.6 and D-S13 and adopted here without change: the
product ships two methods.** «Реальная задержка» (default) and «TCP-подключение». `HTTP_URL` and
`ICMP` are removed and stored values migrate to `PROXIED_REAL_DELAY`. ICMP is silently dropped by
most mobile carriers and reports a failure that is not the server's; direct HTTP duplicates real
delay with a strictly worse signal. Removing them also closes the parity gap in the table above
rather than asking the desktop core to grow two probes it has never had.

### 9.2 What the number means

**«48 мс» is the time a request took to complete against the delay-test endpoint using this server,
by the method the user chose in Настройки > Проверка задержки.** It is not a round trip to the
server, it is not a throughput figure, and under «Реальная задержка» it includes TLS, REALITY, mux
and fragmentation, which is exactly why it is the default: it is the only method that answers "will
this actually work".

Formatting (`00-rules.md` 9.2): integer milliseconds, a non-breaking space before «мс», Numeric role,
`tnum lnum zero` on because it is a technical figure (D-3). No decimal. No range. No «ms».

**The number is per server and is never aggregated.** There is no "average latency" anywhere on this
screen, because a mean over 150 servers is a number about the list, not about a choice.

### 9.3 Where a measurement is started

| Scope | Route |
|---|---|
| One server | The actions sheet, row 3 (8.2) |
| One provider | The provider kebab, row 2 (8.3) |
| Everything in view | The header overflow, «Проверить задержку» (10.2). Scope is the current search filter, and the label states it |
| Automatically | `settings/latency` group «Автоматически»: on launch, and after a provider refresh (`12-settings.md` 5.6). Neither is on by default |

Nothing on this screen measures on scroll, on open, or on a timer. A list that pings itself is a list
that drains a battery to keep a column of numbers warm.

### 9.4 The in-flight state

The row's trailing slot shows a **20dp indeterminate arc** in `?attr/colorPrimary`, rotating at
`motion_spin` 1100 linear, in the same 64dp reserved box the value uses. The value is not dimmed and
not left behind: it is replaced, because a stale figure next to a spinner reads as the new result.

`00-rules.md` 8.8: under reduced motion the arc is not drawn at all and the slot holds a static 20dp
`ic_ping_24dp` at `?attr/colorOnSurfaceVariant`. Desktop already collapses the spinner under `.lite`
(`Views/ServerListView.axaml:64-69`) and this keeps that behaviour while giving the state something
to show.

**A bulk run marks every row in scope at once**, which is what `markAllServersTesting()` does today
by writing a `-2L` sentinel (`ui/MainActivity.markAllServersTesting()`). The sentinel survives; the header
overflow's «Проверить задержку» item becomes «Остановить проверку» while a run is in flight, and it
cancels through the existing `MSG_MEASURE_CONFIG_CANCEL` path (`viewmodel/MainViewModel.testAllRealPing()`).

### 9.5 Stale, failed, and never measured

The store today is a single `Long` (`dto/entities/ServerAffiliationInfo.kt:3`) with four meanings:
`0` never measured or cleared, `-1` failed, `-2` in flight, positive = milliseconds. It has **no
timestamp**, so a reading from six days ago is indistinguishable from one from six seconds ago.

**`ServerAffiliationInfo` gains `testedAt: Long`**, written by
`MmkvManager.encodeServerTestDelayMillis` alongside the value. Without it "stale" cannot be drawn,
and drawing a six-day-old number as if it were current is the failure this section exists to prevent.

| Condition | Trailing slot | Why |
|---|---|---|
| `testedAt` within 30 minutes | «48 мс» in `?attr/colorOnSurface` | A current reading |
| `testedAt` older than 30 minutes | «48 мс» in `?attr/colorOnSurfaceDim` | Still information, visibly not fresh. `colorOnSurfaceDim` exists (`values/attrs.xml:79`, `md_theme_onSurfaceDim`) |
| Value `-1` | «нет ответа» in `?attr/colorOnSurfaceVariant`, Caption 12/400 | A failure is a fact, and the word states it |
| Value `0`, or the server has never been measured | **empty** | There is nothing to say |
| Value `-2` | the arc (9.4) | |

A row whose last probe failed is **not dimmed and not disabled**. The probe failing does not mean the
server will fail to connect, especially under TCP-connect, and refusing to let the user try is the
app being clever at his expense.

### 9.6 What must never look like a real measurement

Absolute, both platforms. Each of these ships somewhere today.

| Forbidden | Where it is today |
|---|---|
| **An em-dash (U+2014) as the failed-probe value** | `Views/ServerListView.axaml.cs:884` returns that character for any value `<= 0`, and `DelayInkConverter`'s own comment describes rendering "its em-dash". This is a `00-rules.md` 1.4.11 violation in shipped UI, and it also reads as a typographic placeholder rather than as a stated failure |
| **`0` or `0 мс`** | `getTestDelayString()` (`dto/entities/ServerAffiliationInfo.kt:4-9`) returns `""` for `0`, which is right, but returns `"-1ms"` for a failure, which prints a negative latency |
| **`«ms»` as the unit** | the same function concatenates `"ms"`; the product's unit is «мс» |
| **The engine's «Testing…» placeholder as text** | `DelayTestingConverter` exists precisely to keep it out of the slot (`ServerListView.axaml:37-39`); nothing may put it back |
| **A dash, `n/a`, `--`, `?`, or `∞`** | not currently shipped; named so it is not invented |
| **A green or red latency band** | `MainRecyclerAdapter.bindServer()` colours every reading `pingGood` / `pingBad` today |
| **A latency on a row that has not been measured in this install** | would require inventing one |

The rule underneath all seven: **the slot is empty or it is true.** An empty 64dp box is a correct
statement about the world; a placeholder is a lie the user will act on.

---

## 10. Multi-select and bulk actions

The bulk actions are being restored right now. This section decides where each one lives, on both
platforms, and it is the single place that decision is recorded.

### 10.1 Scope, before anything else

**Every bulk action states its scope in its own label or in its result, and the scope is always
one of three things:** the whole list, the current provider group, or the current search filter.
Nothing on this screen acts on an invisible set.

That is what makes bulk usable on a phone without multi-select. A user who wants to ping the four
Amsterdam servers types `amster`, and «Проверить задержку» in the overflow reads
«Проверить задержку: 4 сервера». The existing code is already scope-aware in exactly this way and
has the recovery copy for it: `startLatencyCheckAll()` distinguishes an empty list from a search that
matched nothing and offers «Сбросить» in the second case (`ui/MainActivity.startLatencyCheckAll()`).

### 10.2 The header overflow, in order

The one trailing action of the H1 header (3.1). It is a sheet on Android and a `MenuFlyout` on
desktop, and it holds nine rows in four groups.

| # | Row | Scope | Kind | Wired to |
|---|---|---|---|---|
| 1 | «Добавить» | n/a | `Row.Navigation` | The add sheet of 8.4 |
| 2 | «Обновить подписки» | all providers | `Row.Action` | `importConfigViaSub()`; per-provider progress renders on each group header (section 7) |
| | hairline | | | |
| 3 | «Проверить задержку: N серверов» | current filter | `Row.Action` | `startLatencyCheckAll()`. Becomes «Остановить проверку» while a run is in flight |
| 4 | «Свернуть все группы» / «Развернуть все группы» | all groups | `Row.Action` | `toggleCollapseAll()`. The label is the next action |
| | hairline | | | |
| 5 | «Экспортировать в буфер» | current filter | `Row.Action` | `exportAll()` (`ui/MainActivity.kt`). Disabled at 0.38 when every server in scope is operator-locked, as `prepareMenu()` already decides. Reports «Серверы скопированы в буфер: 42» |
| | hairline | | | |
| 6 | «Удалить дубликаты» | whole list | `Row.Destructive` | `delDuplicateConfig()`. Removes, then «Дубликаты удалены: 14» · «Отменить» |
| 7 | «Удалить недоступные» | whole list | `Row.Destructive` | `delInvalidConfig()`. Removes, then «Недоступные серверы удалены: 6» · «Отменить». With nothing to remove it says «Недоступных серверов нет. Сначала проверьте задержку.» and offers «Проверить» |
| 8 | «Удалить все серверы» | whole list | `Row.Destructive` | `delAllConfig()`. **The one dialog on this screen** |

**«Найти выбранный сервер» is deleted as a menu item.** It exists today
(`res/menu/menu_main.xml` `servers_locate`, `ui/MainActivity.locateSelectedServer()`) and it is a workaround
for a list that lost the user's place. It is replaced by **behaviour**: opening Серверы always
scrolls the selected server into view, expands its group if collapsed, and clears a search that hides
it - which is precisely what `locateSelectedServer()` already implements, minus the menu item. A
control whose entire job is "put me back where I was" is a bug report with an icon.

**«Сортировать по задержке» is deleted as a menu item** and folded into the sort picker as «По
задержке» (5.4). One concept, one control, and it stops rewriting the stored order.

**«Удалить все серверы» keeps a dialog** and is the only thing on this screen that gets one, because
it is the only action here that is genuinely irreversible and costly: provider servers return on the
next refresh, manually added ones do not. The existing dialog already says exactly that and its
positive button already carries the verb, in red, on the right
(`ui/MainActivity.delAllConfig()`, `menu_actions_del_all_body` / `menu_actions_del_all_confirm`).
Nothing about it changes except its rendering as an H5 dialog.

**Bulk deletes are blocked while the tunnel is up**, with «Нельзя удалять серверы во время
подключения. Отключитесь и повторите.» and an «Отключить» action - the existing `bulkDeleteAllowed()`
guard and its copy, kept verbatim.

### 10.3 Multi-select is desktop-only, and that is a logged parity gap

| | Android | Desktop |
|---|---|---|
| How a set is defined | the **search filter** (10.1) | pointer selection: `Ctrl+click` toggles, `Shift+click` extends, `Ctrl+A` selects all in the focused group, `Esc` clears |
| Why | long press is the actions gesture and restoring it is the P0 of 2.1. Overloading it with "enter selection mode" would take back the fix, and hand-picking 12 of 150 rows with a thumb is not a journey anyone completes | the pointer can express a set in one gesture and `ProfilesViewModel.SelectedProfiles` already exists to receive it |

The **design** is identical on both: a bulk action names its scope and reports a count with an undo.
Only the mechanism for naming the scope differs, which `00-rules.md` 13 permits under "any platform
capability the other does not have". Logged as a parity gap in 17.

**The desktop selection bar.** With two or more rows selected, the meta line's right side is replaced
in place - the search field stays, because narrowing and then selecting is the real workflow:

```
«Выбрано 12»  ·  Button.Text «Проверить»  ·  Button.Text «Дублировать»
              ·  Button.Text «Удалить» in Brush.RedText
              ·  Button.Icon «Снять выделение (Esc)»
```

A multi-selected row is `?attr/colorSurfaceContainerHighest` fill plus a 20px check at the **leading
edge, replacing the flag tile**. That is the one moment the unified server icon may be replaced, and
it is replaced because in selection mode the row's identity as a checkbox outranks its identity as a
place. Deleting many is one undo for the whole batch: «Удалено серверов: 12» · «Отменить».

### 10.4 Manual order

Available only while the sort order is «Вручную» (5.4). Under any other order the actions sheet's
«Переместить» row is **hidden, not disabled**: an item that can never enable under the user's current
choice is noise.

| Input | Behaviour |
|---|---|
| Android, the «Переместить» sub-sheet | Four `Row.Action` rows: «В начало», «Выше», «Ниже», «В конец», plus «В другую группу…» which opens a provider picker. The sheet stays open after a move so a user can press «Выше» four times, and the sheet's header shows «Позиция 4 из 147» |
| Android, drag | A long press inside the sub-sheet is not a drag. Dragging on the list itself is **not** offered on Android: the gesture is already spent on the actions sheet, and a phone list that both long-press-drags and long-press-menus has to guess which one the user meant |
| Desktop, drag | The grabbed row lifts to `?attr/colorSurfaceContainerHigh`, `scale(1.02)`, opacity 0.9, and follows the pointer. A 2px `Brush.Accent` **insertion line between two rows** shows where it lands. A line between rows is not a stripe on a row and is not the banned side-stripe |
| Desktop, auto-scroll | Within 48px of the viewport edge, 240px/s accelerating to 720px/s |
| Desktop, keyboard | `Alt+Up` / `Alt+Down` / `Alt+Home` / `Alt+End` move the focused row and **keep focus on it**, announcing «Нидерланды, Амстердам, позиция 4 из 147» |
| Both, reduced motion | The lift, the settle and the displacement snap |

Manual order is stored per provider and survives a refresh: a server still present keeps its
position, a new one appends, a vanished one is removed silently. This is the only order that writes
to the store (5.4), and it is what gives `MoveTopCmd` / `MoveUpCmd` / `MoveDownCmd` / `MoveBottomCmd`
(`ServiceLib/ViewModels/ProfilesViewModel.cs`, the four `Move*Cmd`) a UI for the first time outside the dead
`ProfilesView`.

### 10.5 Commands that lose their only UI when `ProfilesView` dies, and where they go

`24-tab-conformance.md` D-09 and `33-master-plan-pc.md` 6.10 require that every `ProfilesViewModel`
command is either reachable or deliberately deleted. This screen's answer:

| Command | Where it lands |
|---|---|
| `SetDefaultServerCmd`, `RemoveServerCmd`, `EditServerAsync`, `CopyServerCmd`, `ShareServerAsync`, `Export2ShareUrlCmd` | Actions sheet, 8.2 |
| `RealPingServerCmd`, `TcpingServerCmd` | Actions sheet row 3, method per 9.1 |
| `SortServerResultCmd` | The sort picker, «По задержке», 5.4 |
| `RemoveDuplicateServerCmd`, `RemoveInvalidServerResultCmd` | Header overflow rows 6 and 7 |
| `FastRealPingCmd` | Header overflow row 3 |
| `MoveTopCmd`, `MoveUpCmd`, `MoveDownCmd`, `MoveBottomCmd`, `MoveToGroupCmd` | «Переместить», 10.4 |
| `AddSubCmd`, `EditSubCmd`, `DeleteSubCmd` | The provider kebab, 8.3, and the add sheet, 8.4 |
| `Export2ClientConfigCmd`, `Export2ClientConfigClipboardCmd`, `Export2ShareUrlBase64Cmd`, `Export2InnerUriCmd` | **Not here.** They move to the server form's overflow (`24-tab-conformance.md` A-13); they serve a user moving a config into a different client, which is not this screen's job |
| `SpeedServerCmd`, `UdpTestServerCmd`, `MixedTestServerCmd` | **Not here.** Three more probe types on a screen that ships two methods (9.1) is the option sprawl `12-settings.md` 6 exists to stop. **OPEN**, 19.4 |
| `GenGroupAllServerCmd`, `GenGroupRegionServerCmd` | **Not here.** Balancer-group generation is a server-form concern; a group is a server, and the form creates servers |

---

## 11. States

Every row is a screenshot that must be taken before this screen is called done
(`00-rules.md` 15). Copy is section 9 of the law: Russian, sentence case, `…` as one character,
«ёлочки», hyphen only, no final period on a label, full stops inside sentences.

### 11.1 The rule that picks what fills the list area

| Condition, highest first | List area shows |
|---|---|
| First load has not returned and 300ms have passed | skeleton (11.3) |
| No subscription and no manually added server | `EmptyState` «Подписки пока нет» (11.10) |
| Zero servers | `EmptyState` «Нет серверов» (11.4) |
| A search is active and matched nothing | `EmptyState` «Ничего не найдено» (11.5) |
| Anything else | the grouped list |

### 11.2 Default

Four provider groups, 84 + 31 + 12 + 4 servers, one selected row inside the first group, latency on
every row, the search field empty, the sort control reading «Как у провайдера». The accent appears
exactly once, on the selected row.

### 11.3 First run and loading

**First run** is not a separate screen. A user arriving here with no providers sees 11.4, which
carries the one action that populates the screen. There is no carousel, no tour and no modal
(`11-app-structure.md` 5.1).

**Loading**: after 300ms, **eight skeleton rows** in the silhouette of a real row - a 40 circle, a
180x16 bar at the 68 origin, a 120x13 bar under it, a 40x13 bar in the trailing slot - plus one
skeleton group header. `Border.Skeleton` / `@drawable/bg_skeleton`, `radius_chip` 12, pulsing at
`motion_pulse` 1000 between opacity 0.45 and 1.0 (`22-components.md` 16). Never a centred spinner
over a blank screen. Under reduced motion the skeleton holds static at 0.7.

Skeleton to content is a **220ms crossfade of the whole block**. There is no per-item stagger on this
screen, in any state - see 12.1.

### 11.4 Empty: no servers

`EmptyState` (`24-tab-conformance.md` 0.4), centred in the list area, not in the window:

| Part | Value |
|---|---|
| Tile | 64, `radius_card` 20, `@color/icon_tile_neutral`, 32dp `ic_cloud_download_24dp` in `@color/icon_glyph_neutral` |
| Title | «Нет серверов» |
| Body | «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» |
| Action | Primary «Добавить провайдера» |

The title is `00-rules.md` 9.5's, not `32-master-plan-android.md` 12.7's «Серверов пока нет»; the law
wins (19.1, V-9). The search field and the meta line stay on screen and the search field is disabled
at 0.38: there is nothing to search.

### 11.5 Empty: search found nothing

| Part | Value |
|---|---|
| Title | «Ничего не найдено» |
| Body | «Попробуйте другой запрос.» |
| Action | Tertiary «Сбросить поиск» |

**The field keeps its text and its focus and the keyboard stays up.** Clearing the field is the
action, not the state. This state is reached in under 200ms of typing, so it never animates in: it
is a content swap with no transition (12.2).

### 11.6 Error

The list keeps whatever it has. A failed provider refresh does not empty the screen.

| Surface | Content |
|---|---|
| The failing group's header | subtitle «Не обновился» in `@color/color_destructive_text` (section 7) |
| The kebab of that group | «Обновить» is the first row, unchanged: the fix is where the failure is |
| A snackbar or toast, once | «Не удалось обновить подписку. Проверьте ссылку провайдера и повторите.» with «Повторить» |

A failure that is total - **every** provider failed and there is no cached list at all - falls back
to `EmptyState` with «Не удалось загрузить» / the mapped cause from `00-rules.md` 9.4 / Secondary
«Повторить». `Что-то пошло не так` is used only when the cause is genuinely unknown.

### 11.7 Offline and partial

**Offline** (`00-rules.md` 9.6): the cached list renders in full and stays interactive. One quiet
persistent strip under the header, 48 tall, `?attr/colorSurfaceContainerHigh`, radius 16:
«Нет сети. Показаны последние данные.» with «Повторить». Every group header's subtitle gains «Данные
могли устареть». «Обновить подписки» and «Проверить задержку» are disabled at 0.38 on the whole
control. **Selecting a server stays enabled**, and so does «Подключиться»: the app does not know
better than the OS whether a tunnel can be raised (`13-start-screen.md` 11.1).

**Partial**: provider A loaded, provider B failed. A renders normally, B keeps its last known rows
with «Не обновился» on its header, and the rest of the screen behaves as if nothing had happened. The
list is never blocked on its slowest member.

### 11.8 Long content

| Case | Behaviour |
|---|---|
| A 40-character server name | One line, `ellipsize="end"`, trailing slot still reserved at 64. Measured worst case at 360dp width: 16 + 40 + 12 + text + 12 + 64 + 16 leaves 200dp for the title, about 22 characters at 16sp/700 |
| A 60-character provider name | One line on the group header, `ellipsize="end"`. It is capped at 25 characters in the parser where the provider protocol supplies it (`30-reference-analysis.md` 1.1.6), and truncation is the fallback for a name the user typed |
| A latency of 4 digits | «1 240 мс», thin space, fits the 64 reserve at 13sp; above 9 999 ms the probe has timed out and the row reads «нет ответа» |
| Font scale 200 percent | Rows grow, the title wraps to a second line and the row grows with it, the trailing reserve scales with the text because it is declared in sp on Android as `@dimen/value_w_ping` **32sp**. Nothing clips |
| 320dp width | The title column falls to 160dp. Still one line, still ellipsised at the end |
| 500 servers | Virtualised, `DiffUtil`, stable ids. Scroll stays smooth, including during a desktop drag |

### 11.9 Short content

**One provider, one server.** The group header renders (5.1), the single row renders, the meta line
reads «1 сервер · 1 провайдер», and the empty space below the row is empty space, not a filler
illustration and not a promotional card. A single row must not look like a mistake.

### 11.10 Gated states

| Gate | Rendering |
|---|---|
| **No subscription** | `EmptyState`: «Подписки пока нет» / «Купите тариф, чтобы подключаться к серверам Departament.» / Primary «Купить», which opens `account/buy`. Manually added servers, if any, still render **above** the block - a user with his own server does not have a subscription problem |
| **Subscription expired** | The list renders in full and stays selectable. One persistent strip: «Подписка истекла. Продлите её, чтобы подключаться.» with «Продлить». «Подключиться» in the actions sheet is disabled at 0.38; selecting is not |
| **Device limit reached** | The list renders in full. One persistent strip: «Достигнут лимит устройств.» with «Устройства», which opens `account/devices`. Nothing on this screen is disabled: the limit is about devices, not servers, and disabling a server list would blame the wrong object |

The strip is the same `StatusStrip` component as `13-start-screen.md` 9, in its inline placement, and
**at most one is ever shown**. Priority, highest first: expired, device limit, offline, provider
refresh failed.

### 11.11 Success

| Event | Feedback |
|---|---|
| A server is selected | The fill and border crossfade over 220ms. No snackbar: the result is visible and a message for something visible is noise |
| A server is deleted | «Сервер удалён» · «Отменить», 5s |
| A provider is refreshed | The group's subtitle count updates. A snackbar only when the count changed: «Обновлено: 84 сервера» |
| A latency run completes | Nothing. The numbers landed; that is the feedback |
| Bulk delete | «Дубликаты удалены: 14» · «Отменить» |
| Export | «Серверы скопированы в буфер: 42» |

No confetti, no checkmark flourish, no full-screen success (`00-rules.md` 7.1).

---

## 12. Motion

Every duration and curve is from `00-rules.md` 3.7. There are no others.

### 12.1 The rule for this screen

**A list of 150 rows must not perform.** There is no entrance animation, no stagger, no page-load
choreography and no idle motion anywhere on Серверы. The skeleton already announced the list; making
the real rows arrive one by one announces it twice, and at 40ms per row a stagger capped at 400ms
covers ten rows out of a screenful of nine, which means the cap fires on almost every load and the
effect is a hitch rather than a rhythm.

This deletes desktop's current one-shot reveal stagger of the first eight rows
(`Views/ServerListView.axaml`, `OnServerRowLoaded`) and contradicts nothing in
`00-rules.md` 8.6, which permits a stagger without requiring one.

### 12.2 The table

| Event | What moves | Duration | Curve |
|---|---|---|---|
| Row press | background to `?attr/colorSurfaceContainerHigh`, and back | 90 / 160 | `ease_out_quart` / `ease_out_quint` |
| Row hover (desktop) | `Brush.Hover`, white 6 percent on dark | 150 | `Ease.Standard` |
| Selection moves | old row's fill and border to transparent, new row's in, both simultaneously | 220 | `ease_standard` |
| Connected chip appears or leaves | alpha only, the row does not reflow because the subtitle line's height is identical either way | 220 | `ease_standard` |
| Group collapse | the group's rows clip from measured height to 0 **and** fade, together | 225 | `ease_standard` |
| Group expand | the reverse | 300 | `ease_out_quint` |
| Collapse chevron | rotation 0 to -90 | 220 | `ease_standard` |
| Sticky header pinning | **nothing.** It pins and unpins on the frame | 0 | |
| Search filter applied | **nothing.** Item animations are suspended while the search field has text. A list that animates on every keystroke is unreadable | 0 | |
| A latency result lands | **nothing.** The arc is replaced by the figure on the frame. 150 simultaneous 220ms fades is a dropped-frame machine, and a result is a fact, not a transition | 0 | |
| Sort applied | the list re-orders once and crossfades as one block | 220 | `ease_standard` |
| A row is deleted | the row fades and collapses | 150 | `ease_standard` |
| A row is added or restored by undo | **nothing.** It is there on the frame | 0 | |
| Skeleton to content | crossfade of the whole block | 220 | `ease_standard` |
| Empty state appears or leaves | alpha only, in place | 220 | `ease_standard` |
| Status strip enters | translationY 8 to 0 plus alpha | 300 | `ease_out_quint` |
| Status strip exits | reverse | 225 | `ease_standard` |
| Sheet or flyout | the platform reveal | 300 / 225 | `ease_out_quint` / `ease_standard` |
| Header hairline | alpha 0 to 1 at `scrollY > 0` | 220 | `ease_standard` |
| Ping arc | continuous rotation, the one linear exemption (`00-rules.md` 3.7) | 1100 | linear |
| Skeleton pulse | opacity 0.45 to 1.0, infinite reverse | 1000 | `ease_standard` |
| Desktop drag lift and settle | see 10.4 | 220 | `ease_out_quint` |

**Press scale is 0.97 everywhere in the product (D-11), and this screen applies it to nothing**,
because everything pressable on it is a row or an icon button in a row's group and rows step their
background instead (R5). The sheet's rows, the header buttons and the search clear button follow the
same rule. Nothing on Серверы scales.

**No 600ms.** The one hero moment in the product is the connect confirmation on Главная
(`00-rules.md` 8.4). Nothing here is allowed that budget, including selecting a server.

**Reduced motion** (`util/MotionUtils.animationsEnabled()`, `MotionState.IsLite` read live through
`MotionState.Changed`): every row above collapses to its end state, the group accordion becomes an
instant show and hide, the ping arc is not drawn (9.4), the skeleton holds static at 0.7, and the
drag lift and settle snap.

---

## 13. Copy

Every visible string. Russian, sentence case, no final period on labels and buttons, full stops
inside sentences, `…` as one character, «ёлочки», hyphen only, `₽` nowhere on this screen.

### 13.1 Android, `res/values-ru/strings_servers.xml` (new file), mirrored into `values/`

A new file for the same reason `strings_menu_actions.xml` and `strings_server_actions.xml` exist:
several agents edit `strings.xml` concurrently. The two existing files are absorbed into it and
deleted.

| Resource | Russian | Status |
|---|---|---|
| `servers_title` | Серверы | **changed**, `title_servers` is «Сервера» today |
| `servers_search_hint` | Поиск серверов | changed from «Поиск серверов…»: a placeholder is a label and takes no ellipsis |
| `servers_search_clear_cd` | Очистить поиск | new |
| `servers_more_cd` | Ещё | keeps `menu_actions_more_cd` |
| `servers_count` | %1$s · %2$s | assembled from two plurals, because one format string cannot host two `<plurals>` |
| `servers_group_actions_cd` | Действия провайдера | new |
| `servers_group_local` | Добавленные вручную | **changed**, `servers_section_local` is «Локальные» |
| `servers_group_stale` | Не обновился | new |
| `servers_group_offline` | Данные могли устареть | new |
| `servers_group_updating` | Обновляется… | new |
| `servers_group_found` | Найдено: %1$d из %2$d | new |
| `servers_sort_provider` | Как у провайдера | new |
| `servers_sort_ping` | По задержке | new |
| `servers_sort_name` | По названию | new |
| `servers_sort_manual` | Вручную | new |
| `servers_sort_title` | Сортировка | new |
| `servers_ping_unit` | %1$d мс | new |
| `servers_ping_none` | нет ответа | new |
| `servers_status_connected` | Подключено | shared with `home_status_connected` |
| `servers_action_connect` | Подключиться | new |
| `servers_action_set_default` | Сделать основным | keeps `server_action_set_default` |
| `servers_action_ping` | Проверить задержку | keeps `menu_actions_ping_cd` |
| `servers_action_edit` | Изменить | keeps `server_action_edit` |
| `servers_action_duplicate` | Дублировать | keeps `server_action_duplicate` |
| `servers_action_duplicate_suffix` | (копия) | keeps `server_action_duplicate_suffix` |
| `servers_action_move` | Переместить | new |
| `servers_action_move_top` | В начало | new |
| `servers_action_move_up` | Выше | new |
| `servers_action_move_down` | Ниже | new |
| `servers_action_move_bottom` | В конец | new |
| `servers_action_move_group` | В другую группу… | new |
| `servers_action_position` | Позиция %1$d из %2$d | new |
| `servers_action_copy_link` | Скопировать ссылку | **changed**, `server_action_share_clipboard` is «Поделиться (буфер)» |
| `servers_action_share_qr` | Поделиться QR-кодом | **changed**, `server_action_share_qr` is «Поделиться (QR)» |
| `servers_action_delete` | Удалить сервер | **changed**, `server_action_delete` is «Удалить» |
| `servers_provider_refresh` | Обновить | new |
| `servers_provider_rename` | Переименовать | new |
| `servers_provider_pin` | Закрепить | new |
| `servers_provider_unpin` | Открепить | new |
| `servers_provider_open_link` | Открыть ссылку | new |
| `servers_provider_settings` | Настройки провайдера | new |
| `servers_provider_delete` | Удалить провайдера | new |
| `servers_add_title` | Добавить | keeps `menu_add_title` |
| `servers_add_provider` | Добавить провайдера | new |
| `servers_add_qr` | Сканировать QR-код | keeps `menu_add_scan_qr` |
| `servers_add_clipboard` | Вставить из буфера | **changed**, `menu_add_clipboard` is «Добавить из буфера обмена» |
| `servers_add_link` | Ввести ссылку | keeps `menu_actions_add_link` |
| `servers_add_file` | Импортировать из файла | keeps `menu_actions_add_file` |
| `servers_add_manual` | Создать вручную | keeps `menu_actions_add_create` |
| `servers_menu_update` | Обновить подписки | new |
| `servers_menu_ping_scoped` | Проверить задержку: %1$s | new |
| `servers_menu_ping_stop` | Остановить проверку | new |
| `servers_menu_collapse` | Свернуть все группы | new |
| `servers_menu_expand` | Развернуть все группы | new |
| `servers_menu_export` | Экспортировать в буфер | keeps `menu_actions_export` |
| `servers_menu_del_duplicate` | Удалить дубликаты | keeps `menu_actions_del_duplicate` |
| `servers_menu_del_invalid` | Удалить недоступные | keeps `menu_actions_del_invalid` |
| `servers_menu_del_all` | Удалить все серверы | keeps `menu_actions_del_all` |
| `servers_empty_title` | Нет серверов | keeps `servers_empty_title` |
| `servers_empty_body` | Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | new |
| `servers_search_empty_title` | Ничего не найдено | new |
| `servers_search_empty_body` | Попробуйте другой запрос. | new |
| `servers_search_empty_cta` | Сбросить поиск | new |
| `servers_error_title` | Не удалось загрузить | new |
| `servers_error_sub` | Не удалось обновить подписку. Проверьте ссылку провайдера и повторите. | new |
| `servers_offline_strip` | Нет сети. Показаны последние данные. | shared with `home_condition_offline` |
| `servers_gate_no_sub_title` | Подписки пока нет | shared with the account copy |
| `servers_gate_no_sub_body` | Купите тариф, чтобы подключаться к серверам Departament. | shared |
| `servers_gate_expired` | Подписка истекла. Продлите её, чтобы подключаться. | shared with `home_condition_expired` |
| `servers_gate_devices` | Достигнут лимит устройств. | shared with `home_condition_devices` |
| `servers_deleted` | Сервер удалён | new |
| `servers_provider_deleted` | Провайдер удалён | new |
| `servers_link_copied` | Ссылка скопирована | new |
| `servers_refreshed` | Обновлено: %1$s | new |
| `servers_undo` | Отменить | keeps `menu_actions_undo` |
| `servers_retry` | Повторить | shared with `home_action_retry` |
| `servers_longpress_hint` | Удерживайте сервер, чтобы открыть действия | new |
| `servers_row_cd` | %1$s, %2$s | new: name, then the state, for TalkBack (14) |

Plurals:

```xml
<plurals name="plural_servers">
    <item quantity="one">%d сервер</item>
    <item quantity="few">%d сервера</item>
    <item quantity="many">%d серверов</item>
    <item quantity="other">%d сервера</item>
</plurals>
<plurals name="plural_providers">
    <item quantity="one">%d провайдер</item>
    <item quantity="few">%d провайдера</item>
    <item quantity="many">%d провайдеров</item>
    <item quantity="other">%d провайдера</item>
</plurals>
```

`plural_servers` and `plural_providers` are shared with `13-start-screen.md` 13.1, which declares
them as `home_servers_count` / `home_providers_count`. **One pair, declared once**, named
`plural_servers` / `plural_providers`; the `home_*` names in that document are aliases of these and
the duplicate declaration is a defect if it ships.

Kept verbatim, because their copy is already correct and already reviewed:
`menu_actions_ping_empty`, `menu_actions_ping_filtered`, `menu_actions_reset_search`,
`menu_actions_no_delay`, `menu_actions_check`, `menu_actions_duplicates_none`,
`menu_actions_invalid_none`, `menu_actions_export_done`, `menu_actions_export_failed`,
`menu_actions_duplicates_deleted`, `menu_actions_invalid_deleted`, `menu_actions_all_deleted`,
`menu_actions_restored`, `menu_actions_busy`, `menu_actions_busy_action`,
`menu_actions_del_all_body`, `menu_actions_del_all_confirm`, `menu_actions_cancel`,
`menu_actions_file_failed`, `server_selected_reconnect_prompt`,
`server_selected_reconnect_prompt_generic`.

**Deleted strings:** `title_servers`, `search_hint`, `servers_add_clipboard` (the old empty-state
button), `servers_section_local`, `menu_actions_locate`, `menu_actions_locate_none`,
`menu_actions_locate_missing`, `menu_actions_sort`, `menu_actions_sorted`, `menu_add_manual`,
`menu_add_tv_send`, `server_actions_title`, `server_action_share_clipboard`,
`server_action_share_qr`, `server_action_delete`.

### 13.2 Desktop, `Common/L.Servers.cs`

The same table, as `Add("Servers_X", "<ru>", "<en>")` triples, keys mapping one to one:
`Servers_Title`, `Servers_SearchHint`, `Servers_SearchClearCd`, `Servers_MoreCd`, `Servers_Count`,
`Servers_GroupActionsCd`, `Servers_GroupLocal`, `Servers_GroupStale`, `Servers_GroupOffline`,
`Servers_GroupUpdating`, `Servers_GroupFound`, `Servers_SortProvider`, `Servers_SortPing`,
`Servers_SortName`, `Servers_SortManual`, `Servers_SortTitle`, `Servers_PingUnit`,
`Servers_PingNone`, `Servers_StatusConnected`, `Servers_ActionConnect`, `Servers_ActionSetDefault`
(exists as `Servers_MakeDefault`), `Servers_ActionPing`, `Servers_ActionEdit`,
`Servers_ActionDuplicate` (exists), `Servers_ActionMove*`, `Servers_ActionPosition`,
`Servers_ActionCopyLink`, `Servers_ActionShareQr`, `Servers_ActionDelete`, `Servers_Provider*`,
`Servers_Add*`, `Servers_Menu*`, `Servers_Empty*`, `Servers_SearchEmpty*`, `Servers_Error*`,
`Servers_Deleted`, `Servers_ProviderDeleted`, `Servers_LinkCopied`, `Servers_Refreshed`,
`Servers_Undo`, `Servers_Retry`, `Servers_SelectedCount`, `Servers_ClearSelection`.

**Changed on desktop:** `Servers_Title` «Сервера» becomes «Серверы»; `Servers_ShareQr`
«Поделиться · QR-код» becomes `Servers_ActionShareQr` «Поделиться QR-кодом»; `Servers_ShareLink`
«Поделиться · ссылка» becomes `Servers_ActionCopyLink` «Скопировать ссылку»; `Servers_Empty`
«Список пуст» becomes `Servers_EmptyTitle` «Нет серверов»; `Servers_EmptyHint` «Добавьте подписку,
чтобы увидеть серверы» becomes `Servers_EmptyBody` «Добавьте провайдера или отсканируйте QR-код,
чтобы появились серверы.» - the current line uses «подписку» for what 9.3 calls a **провайдер**;
`Servers_SearchPlaceholder` loses its ellipsis and becomes `Servers_SearchHint`.

**The `Sub_*` block moves with `SubscriptionMetaView`** and is rewritten to section 7: `Sub_Until`,
`Sub_AutoUpdate` and the traffic strings are deleted here because a group header carries a name and
a count. `Sub_Pin`, `Sub_Delete` and `Sub_OpenSupport` become `Servers_ProviderPin`,
`Servers_ProviderDelete` and `Servers_ProviderOpenLink`. `Sub_DeleteConfirm` «Удалить подписку?» is
deleted outright: the provider delete is undo, not a confirm (8.3).

Russian plurals on desktop go through the `Plural(int n, string one, string few, string many)`
helper in `Common/L.cs`, as `13-start-screen.md` 13.2 establishes. This screen needs the same two
sets and no others.

---

## 14. Accessibility

| Requirement | This screen |
|---|---|
| Contrast, text | Title `#F2F4F8` on `#0A0B0D` 17.88:1; subtitle and latency `#9BA1AD` 6.99:1 on surface; protocol chip `#9BA1AD` on `#20242B` 6.00:1; «Подключено» `#22C55E` on its 18 percent fill 7.95:1; «нет ответа» 6.99:1. The current protocol chip is 4.0:1 and is the one text failure on the screen |
| Contrast, control boundaries | The search field border is `colorOutlineControl` `#646C7C`, 3.43:1 on `colorSurface`, clearing 1.4.11. The selected row's 1dp accent border is `#4C8DFF`, 6.15:1 on the ground |
| Touch and pointer targets | Rows 56 full width. Group header: the text column plus chevron is one 56 target, the kebab is a 48 hit box, 12 apart. Header overflow 48. Search clear 48. Sort control `minHeight` 48. Sheet rows 56. Nothing under 48 on Android, nothing under 40 in a desktop toolbar |
| Accessible names | Every icon-only control is named: «Ещё», «Очистить поиск», «Действия провайдера», «Свернуть группу» / «Развернуть группу». The row's own name is `servers_row_cd`: the server name, then its state, so TalkBack reads «Нидерланды, Амстердам, выбран, подключено, 48 миллисекунд» and never reads the flag or the chip as separate nodes. The flag tile is `importantForAccessibility="no"` |
| State announcement | The selected row exposes `AccessibilityNodeInfo.isSelected` / `AutomationProperties.IsSelected`; the connected row appends «подключено»; a measuring row announces «идёт проверка»; a collapsed group header announces its expanded state and reads its count |
| Live regions | **None.** A list where 150 rows announce a landing latency is unusable. The bulk-run completion is announced once, by the snackbar |
| Reading and focus order | header, search, clear, sort, then the list in visual order: group header, its chevron, its kebab, then its rows. Focus is never lost when a group collapses: it moves to the group's header |
| Focus | Mandatory on both platforms, 2dp `?attr/colorPrimary` outside the row at 2dp offset, radius 18 (6.5). The list is virtualised, so keyboard movement scrolls the container rather than relying on realisation |
| Text scaling | Verified at 200 percent, 11.8. The trailing reserve is declared in **sp**, so it scales with the text; this and `13-start-screen.md`'s two strip reserves are the only sp dimensions in the product |
| Reduced motion | 12.2 |
| Colour never alone | Selected = fill **and** border **and** being the only such row. Connected = colour **and** the word «Подключено». A failed probe = the word «нет ответа», never a red figure. A failed provider = the word «Не обновился», never a red header |
| Keyboard completeness (desktop) | 16.4. Every action on this screen is reachable without a mouse |

---

## 15. Android implementation map

### 15.1 New files

| File | Contents |
|---|---|
| `res/layout/fragment_servers.xml` | The tree of section 4. Target: under 140 lines |
| `res/layout/item_server.xml` | The row of section 6. Target: under 90 lines |
| `res/layout/item_provider_header.xml` | Section 7 |
| `res/layout/field_search.xml` | The search block of 5.3, shared with `settings` search and the per-app picker |
| `res/layout/header_tab.xml` | H1, shared by all four destinations |
| `res/layout/empty_state.xml` | `24-tab-conformance.md` 0.4, shared product-wide |
| `res/layout/skeleton_list.xml` | Eight skeleton rows, shared |
| `res/layout/sheet_list.xml` | The generic action sheet: H4 header plus a `RecyclerView` of `Row.*`. The server sheet, the provider sheet, the add sheet, the sort picker and the move sub-sheet are all this file with different data |
| `ui/servers/ServersFragment.kt` | Owns the screen. Target: under 400 lines |
| `ui/servers/ServersUiState.kt` | One model: groups, rows, selection, connection, sort, query, gate, condition. The fragment renders it and branches on nothing |
| `ui/servers/ServerListAdapter.kt` | `ListAdapter` + `DiffUtil` + stable ids, two view types |
| `ui/servers/ProviderStickyHeaderDecoration.kt` | The sticky header |
| `res/drawable/bg_row_selectable.xml` | 6.5. A `<ripple>` over a `<selector>`: `state_activated` = 12 percent accent fill + 1dp accent stroke at radius 16; `state_pressed` = `colorSurfaceContainerHigh`; default transparent |
| `res/drawable/bg_field.xml` | radius 16, `colorSurface` fill, 1dp `colorOutlineControl` |
| `res/drawable/ic_search_24dp.xml`, `ic_close_24dp.xml`, `ic_unfold_more_24dp.xml`, `ic_link_24dp.xml` | Four new glyphs, one family, one stroke weight (`00-rules.md` 10) |
| `res/drawable-nodpi/flag_*.png` | The 16 flags ported from `v2rayN.Desktop/Assets/Flags/`, 6.1 |

### 15.2 Changed files

| File | Change |
|---|---|
| `res/values/attrs.xml`, `themes.xml`, `values-night/themes.xml` | Add `colorOutlineControl`, wired to the already-declared `md_theme_outlineControl` in all three themes. This is D-9 finished; the colour exists and the attribute does not |
| `res/values/dimens.xml` | Add `value_w_ping` **32sp** (sp, so the reserve scales with the text; the comment must say why) |
| `res/values/styles.xml` | **Nothing to add.** `Widget.Departament.Chip` (`:843`), `.Technical` (`:866`), `.Status.Ok` (`:879`) and `Widget.Departament.Button.Icon` (`:477`) all exist and are used exactly as declared. The work is adoption, not authoring |
| `util/FlagUtil.kt` | `resolveFlag` returns a drawable id instead of an emoji string; `extractFlagEmoji` and `stripLeadingFlag` survive unchanged |
| `dto/entities/ServerAffiliationInfo.kt` | Add `testedAt: Long`. Delete `getTestDelayString()`, which emits `"-1ms"` and `"48ms"`; formatting moves to the row binder and uses «мс» |
| `handler/MmkvManager.kt` | `encodeServerTestDelayMillis` writes `testedAt` alongside the value |
| `viewmodel/MainViewModel.kt` | Delete `testAllDirectHttp()`, `testAllIcmp()`, `applyProtocolFilter()`, `protocolFilter`. `sortByTestResults()` stops writing `encodeServerList` and returns a view order. `filterConfig` gains the 120ms debounce |
| `enums/PingMethod.kt` | Two values, `PROXIED_REAL_DELAY` and `TCP_CONNECT`; `fromPref` migrates `http` and `icmp` to `real` |
| `ui/MainActivity.kt` | Loses this screen. `setupServerLists()`, `setupServersHeader()`, `showImportMenu()`, `prepareMenu()`, `paintMenuItem()`, `startLatencyCheckAll()`, `locateSelectedServer()`, `sortByTestResults()`, `exportAll()`, `delDuplicateConfig()`, `delInvalidConfig()`, `delAllConfig()`, `runBulkDelete()`, `showServerActions()` and the `rvHomeServers` block all move to `ServersFragment` or die |
| `res/menu/menu_main.xml` | Deleted. Both of its groups become sheets (8.4, 10.2), which is what `00-rules.md` 11.2 requires: a `PopupMenu` is not in the allowed component vocabulary and `setForceShowIcon` plus per-item `SpannableString` tinting is the workaround that proves it |

### 15.3 Deleted files

Listed in 2.1: five layouts, four drawables, one activity plus its layout, one menu, two string files
folded into one.

### 15.4 Data contract for `ServersFragment`

The fragment renders and does not decide.

```kotlin
data class ServersUiState(
    val groups: List<ProviderGroup>,     // already ordered, already filtered, already sorted
    val selectedGuid: String?,           // what the next connect will use
    val connectedGuid: String?,          // what the tunnel is on right now, or null
    val query: String,                   // the live search text
    val sort: SortOrder,                 // Provider | Ping | Name | Manual
    val totalServers: Int,               // unfiltered, for the meta line
    val totalProviders: Int,
    val matchedServers: Int?,            // non-null only while a query is active
    val gate: Gate?,                     // NoSubscription | Expired | DeviceLimit | null
    val condition: Condition?,           // the single highest-priority strip condition
    val loading: Boolean,
    val stale: Boolean,                  // offline: last known data is being shown
)

data class ProviderGroup(
    val subId: String, val name: String, val serverCount: Int,
    val collapsed: Boolean, val pinned: Boolean,
    val status: GroupStatus,             // Ok | Updating | Failed | Stale
    val rows: List<ServerRow>,
)

data class ServerRow(
    val guid: String, val name: String,  // flag already stripped
    val flag: Int?,                      // drawable id, null -> globe
    val protocol: String,                // chip text
    val transport: String,               // «Reality · TCP», may be blank
    val latency: Latency,                // Fresh(ms) | Stale(ms) | Failed | Measuring | Never
)
```

`selectedGuid` and `connectedGuid` are separate fields, from separate sources, and the fragment
never derives one from the other. That is section 5.2 expressed as a type.

---

## 16. The same screen on desktop

Identical nouns, identical order, identical strings, identical states. What differs is what the
platform demands: a pointer, a keyboard, a resizable window, a second pane at width, and no haptics.

### 16.1 Files

| File | Action |
|---|---|
| `Views/Servers/ServersPage.axaml` + `.axaml.cs` | **New.** The destination, both layout bands |
| `Views/Servers/ServerList.axaml` | **Rebuilt from `ServerListView.axaml`**, the canonical one (2.2). Keeps its `VirtualizingStackPanel`, its converters and its scroll-extent trailing spacer, all of which are correct and hard-won |
| `Views/Servers/ServerRow.axaml` | **New**, extracted from the 100-line inline `DataTemplate` at `ServerListView.axaml:136-262` (verified against the file as read) |
| `Views/Servers/ProviderGroupHeader.axaml` | **New**, replaces `SubscriptionMetaView.axaml` (335 lines) |
| `Views/Servers/ProviderPane.axaml` | **New**, wide layout only (16.3) |
| `Views/ServersView.axaml`, `CompactServersView.axaml`, `ProfilesView.axaml` and their `.cs` | **Deleted**, after the search field and the tab header are harvested from `CompactServersView` (2.2) |
| `Views/SubscriptionMetaView.axaml` + `.axaml.cs` | **Deleted** |
| `Views/MainWindow.axaml` | `navServers` added between `navHome` and `navSettings`, using the already-declared `Geo.Nav.Servers` (`Assets/GlobalResources.axaml:454`) |
| `Views/MainWindow.axaml.cs` | `AppTab.Servers` added; `_navButtons` (`:174`) becomes four; `TabIndex` (`:389`) and `ViewFor` (`:481`) gain their cases |
| `Views/HomeView.axaml` | Loses `ColumnDefinitions="440,1,*"` and its embedded `ServerList`, per `13-start-screen.md` 16 |
| `Common/L.Servers.cs` | 13.2 |

### 16.2 Rail order

Главная, **Серверы**, Настройки, Аккаунт. `10-design-system.md` 6.15 and `33-master-plan-pc.md` 1.2
already fix that order and Android's bottom bar matches it. `11-app-structure.md` 2.1 orders them
Главная, Серверы, Аккаунт, Настройки; this screen links by identity and not by index and so does not
depend on the resolution, which stays where `13-start-screen.md` 19.2 left it.

### 16.3 Layout, two bands, one view

**Band 1, content width < 900 - one column.** The Android stack verbatim, capped at 720 and centred
(`Size.ContentMax`, a new token; no content-width cap exists in `Assets/GlobalResources.axaml`
today).

```
DockPanel
├─ [Top] header 56        «Серверы» + one Button.Icon «Ещё»
├─ [Top] search 48        Margin 16,8,16,0     MaxWidth 360, left-aligned
├─ [Top] meta 48          Margin 16,12,16,8    count left, sort right
└─ ScrollViewer           virtualised, sticky group headers
```

**Band 2, content width >= 900 - two panes.**

```
Grid ColumnDefinitions="300,1,*"
├─ [0] ProviderPane   Width Size.PanePrimary 300, its own ScrollViewer
├─ [1] Border         Width 1, Brush.OutlineVariant
└─ [2] DockPanel      the list pane: header 56, search+meta 56, ScrollViewer
```

Two scrollers is the one documented exception to "one scroll region per view"
(`00-rules.md` 12.3): they never overlap and never nest.

**The provider pane replaces the group headers, it does not duplicate them.** In band 2 the list
shows one provider at a time and carries no group headers; in band 1 there is no pane and the group
headers do the work. A screen that shows both is showing the same fact twice.

```
ProviderPane
├─ TextBlock.SectionHeader «Провайдеры»        Margin 16,16,16,8
├─ Border.Row.selectable «Все серверы»          value «147»
├─ 8
├─ Border.Row.selectable per provider:
│     Border.Tile 40 neutral, Geo.Set.Provider 22
│     Title «Departament»     Subtitle «84 сервера»
│     trailing Button.Icon 40 kebab, Opacity 0 until :pointerover or :focus
└─ Button.Text «Добавить провайдера»            Margin 16,8,16,16
```

Pane selection is `Brush.SelectedFill` plus the title stepping to weight 700 - two channels, and
**no left-edge indicator**, which is the side-stripe ban and would break the 68 text origin every
other list in the product holds.

At the minimum 900x600 window, content width after the 76px rail and its hairline is 823, so band 1
applies and the whole screen works with no horizontal scroll.

### 16.4 Desktop-only behaviour

| Concern | Specification |
|---|---|
| Hover | `Brush.Hover`, white 6 percent on dark and black 6 percent on light (D-8), over the full row at radius 16, 150ms `Ease.Standard`. Instant on the way in and out with no `BrushTransition`, which is the bug `Assets/GlobalStyles.axaml:913-916` already documents and fixes |
| Press | background step to `Brush.SurfaceHigh`, **not** `scale(0.97)`. `Border.ServerRow.pressed`'s scale (`:929-931`) is deleted: these rows sit in a divided list and scaling one tears the hairlines above and below it (R5) |
| Cursor | `Hand` on rows, group headers, pane rows and the sort control. Default elsewhere |
| Focus | Always drawn, 2px `Brush.Accent` outside at 2px offset, radius 18 |
| Tab order | rail, search, sort, overflow, provider pane (one stop, arrows move within), list (one stop, arrows move within) |
| Inside the list | `Up`/`Down` move focus, `Home`/`End` jump, `Space` selects the server, `Enter` connects to it, `Delete` removes with undo, `Menu` or `Shift+F10` opens the flyout, `Ctrl+A` selects all in the group, `Alt+Up`/`Alt+Down`/`Alt+Home`/`Alt+End` reorder under «Вручную» |
| Shortcuts | `Ctrl+F` focuses search, `Esc` clears search then clears selection then closes a flyout, `Ctrl+P` measures the current scope, `Ctrl+N` opens the add sheet |
| Tooltips | On the two icon buttons only, each carrying its shortcut: «Ещё», «Проверить задержку (Ctrl+P)». Rows have none: their labels are visible |
| Drag and drop onto the window | A dropped `.json`, a dropped link and pasted text all enter through the same import path as the add sheet |
| Reduced motion | `MotionState.IsLite`, read **live** through `MotionState.Changed`, never once in a constructor |
| Theme | Every brush through `{DynamicResource ...}`. `CornerRadius="{StaticResource Radius.Search}"` at `ServerListView.axaml:143` becomes `{DynamicResource Radius.Button}` |
| DPI | Verified at 100 / 125 / 150 / 200 percent and at in-app zoom 200 percent |

### 16.5 Desktop token changes

Add `Size.PanePrimary` 300, `Size.ValuePing` 64, `Size.ServersSplitThreshold` 900,
`Size.ContentMax` 720. None of the four exists today.
`Radius.Search` 14 (`Assets/GlobalResources.axaml:423`) loses its last reference on this screen and
can be retired with D-7's remaining migrations. `Size.TrafficPill` 160 (`:427`) loses its only user
when `SubscriptionMetaView` dies. `Brush.HomeGradient` loses one more of its eight paint sites when
`HomeView` empties.

---

## 17. Parity contract

**Identical, by contract:** the destination and its position in the navigation; the block order
header, search, meta, list; the row anatomy, its 68 text origin, its 64 trailing reserve and its
one-line title; the unified server icon and its globe fallback; the neutral protocol chip; the
separation of selected from connected and the two channels that carry them; the four sort options
and their labels; what search matches; the group header and its kebab contents in order; the nine
rows of the actions sheet in order; the two ping methods; what a latency figure means and the seven
things that must never look like one; the header overflow's nine rows in order; every state in
section 11 and every string in section 13; the motion table of 12.2; undo instead of confirmation
everywhere except «Удалить все серверы».

**Allowed to differ:**

| Concern | Android | Desktop |
|---|---|---|
| Per-item action surface | bottom sheet | `MenuFlyout` |
| Opening it | long press | right click, `Menu`, `Shift+F10` |
| Layout at width | `sw600dp`: gutter 24, capped at 720, centred. No second pane | two panes at content width >= 900, provider pane at 300 |
| Grouping affordance at width | always group headers | group headers in band 1, provider pane in band 2 |
| Hover | does not exist | 6 percent overlay |
| Haptics | `tickHaptic()` on selecting a server, `pressHaptic()` on «Подключиться» and on a destructive confirm | none |
| Shortcuts | none | 16.4 |
| Transient feedback | `Snackbar` | `Border.Toast` |
| Reordering | the «Переместить» sub-sheet | drag, plus `Alt+Up` / `Alt+Down` / `Alt+Home` / `Alt+End` |

**Logged parity gaps, both deliberate:**

| # | Gap | Why, and what the other platform has instead |
|---|---|---|
| **PG-1** | **Multi-select is desktop-only** | Long press is the actions gesture on Android and restoring it is the P0 of 2.1; hand-picking twelve rows of 150 with a thumb is not a journey anyone finishes. Android defines a bulk scope with the search filter, and every bulk action states its scope (10.1) |
| **PG-2** | **Drag-to-reorder is desktop-only** | The same gesture collision. Android reorders through the «Переместить» sub-sheet, which is keyboard- and screen-reader-complete and which desktop also gets through `Alt+Arrow` (10.4) |

Nothing else differs. If a third gap appears during implementation it is logged here before it
ships, not after.

---

## 18. Acceptance

Run all of it. A box that cannot be ticked honestly means the screen is not done.

**The P0 and the dead code**
- [ ] Long press on a server row opens the actions sheet on a real device. `ServerActionsSheet` has a
      caller
- [ ] `grep -rn 'onItemLongClick' ui/` shows the property bound **and invoked**
- [ ] `Views/ServersView.axaml`, `CompactServersView.axaml` and `ProfilesView.axaml` are gone, and
      the search field they carried is alive in the new destination
- [ ] Desktop has a Серверы entry in the rail and `Geo.Nav.Servers` has a reference
- [ ] `applyProtocolFilter`, `protocolFilter`, `layout_indicator`, `setData`, the `ItemTouchHelper`
      stubs and `rvHomeServers` are all gone

**Tokens and bans**
- [ ] `grep -rnoE '"(-?[0-9]+)dp"' res/layout/fragment_servers.xml res/layout/item_server.xml res/layout/item_provider_header.xml` against the allow-list returns nothing
- [ ] No raw hex in any of the new layouts or views; `bg_server_row.xml`'s two `#1F4C8DFF` are gone
- [ ] No `android:textSize`, no `android:fontFamily`, no `FontSize`, no `FontFamily` in the markup
- [ ] Radii: 12 on the tile and the chips, 16 on the search field and the row, 24 on the sheet lip.
      No 14, no 20 on a row
- [ ] `colorOutlineControl` exists as an attribute in all three themes and the search field uses it
- [ ] No emoji anywhere: the flag is a raster in a tile, `FlagUtil` no longer returns U+1F310
- [ ] Zero cards. The list is rows and hairlines
- [ ] No side stripe, no gradient, no glow, no nested card, no ALL-CAPS

**The direction**
- [ ] Count the blue: exactly one accent object on a populated screen, and it is the selected row
- [ ] Measure the text origin on a screenshot: 68 for every row, every group header and every
      hairline, and the hairline never runs under a tile
- [ ] Change a latency from `48` to `1 240` in the preview and confirm nothing moves
- [ ] Select a server while connected, decline the reconnect, and confirm the screen shows the fill
      on one row and «Подключено» on another
- [ ] Find a Russian string set in Space Grotesk: there must not be one. The chip labels and the
      latency figures are the only brand-face text on the screen
- [ ] Squint: one list, one lit row, four group titles. The hierarchy survives

**States**
- [ ] Default, first run, loading, empty, empty search, error, offline, partial, long, short,
      no subscription, expired, device limit, success: screenshotted (section 11)
- [ ] Dark, light and mono, each of the above
- [ ] One provider and one server does not look broken
- [ ] 500 servers scrolls smoothly, including during a desktop drag

**Latency**
- [ ] The trailing slot is empty, a figure, an arc or «нет ответа». Never `0`, never a dash, never
      `-1ms`, never `ms`, never «Testing…»
- [ ] The dash grep of `00-rules.md` 1.5 over `Views/` and `Common/` returns nothing: the em-dash at
      `ServerListView.axaml.cs:884` is gone
- [ ] A reading older than 30 minutes is visibly dimmed
- [ ] Only two ping methods are offered, and the picker has no dead options
- [ ] Latency is never green and never red

**Interaction**
- [ ] Press feedback within 90ms on every row, header, sheet row and button, and it is a background
      step, not a scale
- [ ] Focus ring visible on every row on both platforms, and the list scrolls to keep focus visible
- [ ] Search filters in place, keeps its text and focus in the no-results state, and Back clears it
      before leaving the destination
- [ ] Every bulk action names its scope and reports a count
- [ ] Every destructive action except «Удалить все серверы» is undo, and undo actually restores
- [ ] The whole screen is completable from the keyboard on desktop
- [ ] `Ctrl+F`, `Esc`, `Ctrl+P`, `Ctrl+N`, `Menu`, `Alt+Arrow` all work

**Copy**
- [ ] Every string Russian, sentence case, no ALL-CAPS
- [ ] «Серверы», never «Сервера». «провайдер», never «подписка», for the thing that yields servers
- [ ] The dash grep of `00-rules.md` 9.7 returns nothing new for `servers_*` or `Servers_*`
- [ ] `…` is one character; «ёлочки» used; no final period on a label

**Performance**
- [ ] `grep -rn 'notifyDataSetChanged' ui/servers/` returns nothing
- [ ] Stable ids and `DiffUtil` on Android, virtualisation on both
- [ ] No JSON parsing on the main thread in a row bind. The `customProtoCache`
      (`MainRecyclerAdapter.customProtoCache`) survives, because parsing a stored config per bind is exactly
      the defect it was written to avoid

---

## 19. Decisions

### 19.1 Conflicts between the foundation documents and the code, resolved here

| # | Conflict | Resolution and why |
|---|---|---|
| **V-1** | Selection and connection are one visual fact in both builds: Android paints `selectedGuid` only and does not connect on tap; desktop paints `IsActive` and **does** connect on tap (`ViewModels/HomeViewModel.cs:268`) | **Two facts, two channels: fill plus border for selected, a `Chip.Status.Ok` «Подключено» for connected (5.2, 6.4). A tap selects on both platforms and connects on neither.** Desktop's connect-on-tap is removed: it gives one gesture two meanings depending on connection state, it lets a mis-click on a scrolling list tear down a live tunnel, and it makes the two clients behave differently, which `00-rules.md` 13 forbids |
| **V-2** | `24-tab-conformance.md` 3.2 item 2 states this is "the **first** server search in the product on either platform"; Android has shipped one since `layout_servers_header.xml:88-106` and desktop has one in a dead file (`CompactServersView.axaml:100-108`) | **The claim is wrong and the work is smaller than it says.** Android's field is rebuilt to the token set, desktop's is harvested from the dead view. Recorded so nobody budgets a from-scratch feature |
| **V-3** | `11-app-structure.md` 4.2 and `24-tab-conformance.md` 3.2 item 3 make sort a text button that **cycles in place**; `12-settings.md` D-S2 retired cycle-in-place product-wide | **A picker with radio semantics** (5.4). D-S2 is the newer decision and its reasoning applies verbatim here: with four options a cycle needs up to three taps to reach the one you want and never shows the set |
| **V-4** | `22-components.md` 18.1 requires a trailing 20dp check on every selectable item; `11-app-structure.md` 4.2 has the check **replace** the ping value; `32-master-plan-android.md` 12.4 has both a ping value and a state marker, which is two trailing elements and breaks `00-rules.md` 4.5 | **The trailing slot is always the latency value. Selection is fill plus border plus being the only such row; connection is the chip on the subtitle line** (6.4, 6.5). Precedent: `13-start-screen.md` 19.1 S-4 already moved a state chip out of the trailing slot into the text column for exactly this reason. Losing the check costs nothing; losing the latency of the row you are about to compare against costs the screen its job |
| **V-5** | The provider group header is 40 (`11-app-structure.md` 4.2, `24-tab-conformance.md` 3.2), 48 (`32-master-plan-android.md` 12.5) and 56 (`33-master-plan-pc.md` 6.7) | **56.** It carries a 40dp kebab, and 40 leaves it no padding while 48 leaves it 4. 56 is also `row_min_height`, so the header and the rows share one rhythm |
| **V-6** | `33-master-plan-pc.md` 6.5 lets the row title wrap to two lines and the row grow to 72; `32-master-plan-android.md` 12.4 holds it at one line | **One line, ellipsised at the end, on both platforms.** A list of unequal row heights cannot be scanned down the latency column, which is the comparison the user is actually making, and the distinguishing part of a provider remark is at its front |
| **V-7** | `32-master-plan-android.md` 12.6 forbids a stagger; `00-rules.md` 8.6 permits one for a list of siblings appearing; desktop ships one for the first eight rows | **No stagger anywhere on this screen** (12.1). The skeleton already announced the list, and a 400ms cap covering ten rows of a nine-row screenful fires on every load |
| **V-8** | `33-master-plan-pc.md` 6.6.1 specifies a twelve-item row flyout with three submenus; `24-tab-conformance.md` A-15 specifies a seven-row Android sheet | **Nine rows, flat, identical on both** (8.2). A flyout with submenus is a menu bar. The four extra export formats move to the server form's overflow, where the person who needs them already is |
| **V-9** | `32-master-plan-android.md` 12.7 sets the empty title to «Серверов пока нет»; `00-rules.md` 9.5 and `24-tab-conformance.md` 0.4 set it to «Нет серверов» | **«Нет серверов».** The law wins over the plan |
| **V-10** | `MainViewModel.sortByTestResults()` rewrites the stored order with `MmkvManager.encodeServerList` for every subscription | **Sort is a view order, persisted per provider, and never writes storage. Only «Вручную» writes** (5.4). A sort that silently destroys the provider's own order is a surprise with no undo |
| **V-11** | `00-rules.md` 3.5 maps error text to `?attr/pingBad` `@color/ping_bad` `#FF6069`, but `values-night/colors.xml:35` sets `ping_bad` to `#F04452` (4.88:1) and the `#FF6069` value lives in `color_destructive_text` (`values-night/colors.xml:153` via `red_65`) | **This screen uses `@color/color_destructive_text` for every red string** and never `ping_bad`. `13-start-screen.md` cites `@color/ping_bad` `#FF6069` in five places on the strength of the same rule row; that is a token mismatch to fix in `00-rules.md` 3.5, not a licence to ship 4.88:1 text |
| **V-12** | `MainRecyclerAdapter.rebuildRows()` suppresses group headers when there is one provider | **The group header always renders** (5.1). It is the only home for the provider's name, count, refresh and delete, and 11.9 requires a one-provider layout that does not look broken |
| **V-13** | `00-rules.md` 10.4 describes the flag set as "the existing `Assets/Flags/*.png` set … (`FlagResolver.cs`, `RemarkToFlagConverter`, `util/FlagUtil.kt`)", implying Android already resolves rasters; `util/FlagUtil.kt` returns **emoji** and Android ships no flag asset at all | **The 16 desktop PNGs are ported to Android** (6.1). The rule row is describing desktop and naming an Android file that does something else |

### 19.2 Conflicts this screen does not resolve, and does not depend on

- **Navigation order.** `11-app-structure.md` 2.1 orders the destinations Главная, Серверы, Аккаунт,
  Настройки; `10-design-system.md` 6.15 and `33-master-plan-pc.md` 1.2 order them Главная, Серверы,
  Настройки, Аккаунт. Серверы is index 1 in both, which is all this screen needs.
- **Desktop compact mode.** `11-app-structure.md` 3.2 deletes it; `33-master-plan-pc.md` 2.9 keeps
  two bands. This screen is one view with two internal bands keyed to content width, so it is correct
  either way.
- **Whether `AppTab` gains a fourth member or the shell moves to a route table.** Section 16.1 states
  what has to be reachable, not how the shell stores it.

### 19.3 Change-control rows for `00-rules.md` section 18

Nothing below is implemented until the row is pasted into `00-rules.md` section 18 and the rule body
there is updated.

| Date | Decision | Rule affected |
|---|---|---|
| pending | **V-1.** A tap on a server row selects and does not connect, on both platforms. Connecting to a specific server is the first row of its actions sheet | `11-app-structure.md` 4.2 |
| pending | **V-4.** The server row's trailing slot is always the latency value; the selection check of `22-components.md` 18.1 does not apply to this row, and connection state is carried by a status chip in the text column | `22-components.md` 18.1 |
| pending | **V-10.** Sort is a view order persisted per provider and never rewrites the stored server list; only «Вручную» writes | `11-app-structure.md` 4.2 |
| pending | **V-11.** `00-rules.md` 3.5's "Destructive / error text" row names the wrong token: on dark, `@color/ping_bad` is `#F04452` (4.88:1) and `#FF6069` is `@color/color_destructive_text`. Every spec that cites `ping_bad` for error text is citing a 4.88:1 pair | `00-rules.md` 3.5, `13-start-screen.md` 6, 8.1, 11.2, 11.3 |
| pending | **V-13.** The 16-flag raster set is vendored to Android and `FlagUtil` stops emitting emoji, so the unified server icon of 0.4.7 exists on both platforms | `00-rules.md` 10.4, 10.5 |
| pending | **V-14.** `ServerAffiliationInfo` gains `testedAt`, so a latency reading can be shown as stale instead of as current | `00-rules.md` 15 (the Partial state) |
| pending | **PG-1, PG-2.** Multi-select and drag-to-reorder are desktop-only, because long press is Android's actions gesture; Android's bulk scope is the search filter and its reordering is the «Переместить» sub-sheet | `00-rules.md` 13 |

### 19.4 Open questions for the owner

1. **OPEN: `SpeedServerCmd`, `UdpTestServerCmd`, `MixedTestServerCmd`** (10.5). Three further probe
   types exist in the desktop engine and have never had a reachable UI outside the dead
   `ProfilesView`. This document keeps them out, because `12-settings.md` D-S13 already cut the
   method list to two and five probe types on one screen is the sprawl that decision was fighting.
   **Decided by the owner**, on the evidence of whether anyone uses per-server throughput testing;
   if kept, they belong in a «Проверить» submenu on the desktop flyout and nowhere on Android.
2. **OPEN: the flag set.** Sixteen countries is small for providers that name dozens. Growing it is a
   content task with a per-flag licensing question. **Decided by the owner** on whether to vendor a
   complete ISO 3166-1 set or to keep the globe fallback doing most of the work. The design does not
   change either way.
3. **OPEN: the stale threshold.** 30 minutes is chosen, not measured. **Decided by the owner** after
   watching how often a stored reading is still right an hour later on a real subscription; the
   number is a dial, the two-tier presentation is the design.
4. **OPEN: whether «Все серверы» exists in the desktop provider pane** (16.3). A cross-provider list
   is useful for search and useless for anything else, and it is the one thing in the pane with no
   Android counterpart. Kept for now. **Decided by the owner** on whether users search across
   providers or within one.
