# S3 Implementation Spec — "Servers" Redesign (Incy parity)

Scope: HOME shows the server list under the provider/meta bar; SERVERS drops the subscription
TabLayout/ViewPager and shows one flat, provider-grouped, searchable, protocol-filterable list with
flat rows, long-press actions, and an empty state. Doc only — no code in this file.

Package `com.v2ray.ang`, module `V2rayNG/app`. All paths below are under
`V2rayNG/app/src/main/`.

---

## 0. Current architecture (what we are replacing)

- `activity_main.xml`: `group_home` (NestedScrollView: stats row + `card_hero` + `card_memory`) and
  `group_servers` (`tab_group` TabLayout + `view_pager` ViewPager2). `MainActivity.showHomeTab()`
  toggles their visibility from the bottom nav.
- `GroupPagerAdapter` (FragmentStateAdapter) → one `GroupServerFragment` per subscription group.
- Each `GroupServerFragment` = `fragment_group_server.xml` = `layout_meta_bar` include +
  `refresh_layout` + `recycler_view` driven by `MainRecyclerAdapter`.
- `MainViewModel.subscriptionId` scopes `serverList`/`serversCache`. `subscriptionId == ""` is the
  pseudo "all servers" mode (`reloadServerList()` → `MmkvManager.decodeAllServerList()`), already
  supported and gated on Home by `PREF_GROUP_ALL_DISPLAY` in `getSubscriptions()`.
- `updateListAction` (`MutableLiveData<Int>`) drives `adapter.setData(serversCache, index)`; the
  fragment ignores the event when `mainViewModel.subscriptionId != subId`.
- Row layout `item_recycler_main.xml`: `layout_indicator`, `tv_flag`, `tv_name`, `tv_type`,
  `tv_statistics`, `tv_test_result`, `layout_subscription`/`tv_subscription`, and the inline action
  cluster `layout_share`/`layout_edit`/`layout_remove`/`layout_more`.

### Key decision — flat list vs ConcatAdapter (task item 3)

**Recommendation: one `RecyclerView` + one `MainRecyclerAdapter` in all-servers mode
(`subscriptionId == ""`) with an added SECTION-HEADER view type.** Reasons:
- Lightest: reuses the single existing adapter, the single `ItemTouchHelper`, and the existing
  `updateListAction`/`serversCache` pipeline. No `ConcatAdapter`, no per-group fragments, no
  `TabLayoutMediator`.
- Selection already keys off `guid == MmkvManager.getSelectServer()`, independent of grouping.
- Grouping headers are derived rows, not real servers, so `serversCache` stays a pure server list
  and subscription scoping logic is untouched.
- ConcatAdapter would need N sub-adapters + N header adapters rebuilt on every reload — heavier and
  it breaks a single cross-list `ItemTouchHelper`.

Drag caveat: `MainViewModel.swapServer()` early-returns when `subscriptionId.isEmpty()`, so persisted
reorder does not work in all-servers mode today. See §3 for the guarded-drag handling.

---

## 1. Layout changes

### 1.1 `res/layout/activity_main.xml`

**`group_servers` — remove tabs/pager, add the Servers surface.** Replace the
`TabLayout tab_group` + `ViewPager2 view_pager` block (lines ~329–346) with a header + list:

```
<LinearLayout android:id="@+id/group_servers" orientation=vertical visibility=gone>
  <!-- Servers header (static, non-scrolling) -->
  <include android:id="@+id/layout_servers_header" layout="@layout/layout_servers_header"/>
  <FrameLayout weight=1>
     <androidx.recyclerview.widget.RecyclerView android:id="@+id/rv_servers"/>
     <include android:id="@+id/layout_empty" layout="@layout/layout_servers_empty"
              android:visibility="gone"/>
  </FrameLayout>
</LinearLayout>
```

Delete: `tab_group`, `view_pager`. New ids to add: `rv_servers`, `layout_servers_header`,
`layout_empty` (see 1.3–1.5).

**`group_home` — embed the list under the provider bar.** Inside the existing
`NestedScrollView > LinearLayout`, after `card_memory`, append:
- `<include android:id="@+id/layout_home_meta_bar" layout="@layout/layout_subscription_meta_bar"/>`
  (the collapsible provider header — see 1.2).
- `<androidx.recyclerview.widget.RecyclerView android:id="@+id/rv_home_servers"
   android:nestedScrollingEnabled="false" android:layout_height="wrap_content"/>`

Home stays a single `NestedScrollView`; the list is a non-nested-scrolling `RecyclerView` with
`wrap_content` height so the whole page scrolls as one. This is the lightest way to satisfy "server
list right here below the provider" without a nested-scroll conflict, and avoids converting the hero
into RecyclerView header items.

Keep these existing ids referenced by `MainActivity` unchanged: `card_connect`, `img_connect`,
`tv_connection_status`, `layout_server_info`, `tv_selected_server`, `tv_test_state`,
`tv_connection_time`, `tv_upload_speed`, `tv_download_speed`, `card_memory`, `tv_memory`,
`dot_memory`, `bottom_nav`, `drawer_layout`, `nav_view`, `toolbar`, `progress_bar`.

### 1.2 `res/layout/layout_subscription_meta_bar.xml` — make it a collapsible header

Add a chevron toggle and wrap the collapsible content:
- In Row 1 (title + actions), add before `tv_sub_title` an `ImageView android:id="@+id/btn_collapse"`
  (`app:srcCompat="@drawable/ic_chevron"` rotating; reuse an existing chevron drawable or add one).
- Wrap Row 2 (`layout_traffic`), `tv_announce`, and the support/website row in a single container
  `LinearLayout android:id="@+id/layout_meta_body"`. Collapse = set `layout_meta_body` to `View.GONE`
  and rotate `btn_collapse` 180°. `tv_sub_title` + action buttons stay visible when collapsed.

Keep all existing meta-bar ids (`meta_bar_root`, `tv_sub_title`, `btn_pin`, `btn_ping`,
`btn_refresh`, `progress_action`, `layout_traffic`, `tv_traffic`, `tv_expiry`, `progress_traffic`,
`tv_announce`, `btn_support`, `btn_website`) — `GroupServerFragment.bindMetaBar()` logic is reused
verbatim (see §3, moved into `MainActivity`/a binder).

### 1.3 New `res/layout/layout_servers_header.xml` (Servers tab header)

Static (non-scrolling) block:
- Title row: `TextView` "Сервера" (new string `title_servers` = "Сервера", RU already has
  `bottom_nav_servers`) + right-aligned circular `ImageView` buttons:
  `btn_collapse_all`, `btn_refresh_all` (`ic_refresh_24dp`), `btn_speedtest_all` (`ic_speed_24dp`),
  `btn_add` (`ic_add_24dp`, tint primary). Reuse the meta-bar button styling
  (`?attr/selectableItemBackgroundBorderless`, 36dp, padding 8dp).
- `TextView android:id="@+id/tv_servers_subtitle"` — "N серверов · M провайдеров" (new plurals/format
  strings `servers_count`, `providers_count`).
- Search field: a `com.google.android.material.textfield.TextInputLayout` +
  `TextInputEditText android:id="@+id/et_search"` styled as a pill (or an `EditText` with a
  rounded `bg_server_card` background), hint `search_hint` = "Поиск серверов…".
- Protocol chips: `com.google.android.material.chip.ChipGroup android:id="@+id/chip_group_protocol"`
  with `app:singleSelection="true"` and `app:selectionRequired="true"`. Chips are added
  programmatically (see §2.4) so the set matches present protocol types; a permanent "Все" chip
  (`chip_all`) is defined in XML as the default-checked chip.

### 1.4 `res/layout/item_recycler_main.xml` — flat rows, drop inline actions

- **Remove** the entire trailing action cluster: `layout_share`, `layout_edit`, `layout_remove`,
  `layout_more` (lines ~135–225). Keep the row otherwise.
- Flat look: change `info_container` background from `@drawable/bg_server_card` to a transparent /
  ripple background with a thin bottom divider (keep the existing `custom_divider` ItemDecoration,
  see §2). Selected state = blue rounded outline: add a selectable-state background drawable
  `bg_server_row` (default transparent, selected = 1dp `?attr/colorPrimary` stroke + slightly lighter
  fill) toggled in `onBindViewHolder` instead of / in addition to `layout_indicator`.
- **Protocol chips in-row**: today `tv_type` shows the joined string from `getProtocolDescription()`.
  For Incy parity split into: `tv_type` (blue chip: `VLESS`/`Auto`), an optional gold `tv_json` chip
  (`JSON` for `CUSTOM`/complex types, background `bg_type_chip` tinted gold), and `tv_statistics`
  keeps transport·security in grey. Add `tv_json` (gold chip) next to `tv_type`; keep ids `tv_type`,
  `tv_statistics`, `tv_flag`, `tv_name`, `tv_test_result`, `layout_indicator`,
  `layout_subscription`/`tv_subscription` stable (all referenced by `MainRecyclerAdapter`).
- Ping dot: add a small `View android:id="@+id/dot_ping"` (reuse `@drawable/bg_status_dot`) before
  `tv_test_result`, tinted green/red by the same rule already in the adapter.

### 1.5 New `res/layout/layout_servers_empty.xml` (empty state)

Centered `MaterialCardView` (reuse `bg_card` styling, radius 20, `strokeColor` outline):
- Icon + title "Нет серверов" (new string `servers_empty_title`).
- `MaterialButton android:id="@+id/btn_import_clipboard"` — "Добавить из буфера"
  (new string `servers_add_clipboard`).
- `MaterialButton android:id="@+id/btn_scan_qr"` — "Сканировать QR"
  (reuse existing `R.string.menu_item_scan_qrcode` = "Сканировать QR-код").

New string resources to add (values + values-ru): `title_servers`, `servers_count`,
`providers_count`, `search_hint`, `servers_empty_title`, `servers_add_clipboard`. Row-level
`chip` text uses `EConfigType.name`.

---

## 2. Adapter changes (`ui/MainRecyclerAdapter.kt`)

### 2.1 View types + section headers
- Add `VIEW_TYPE_HEADER = 0` alongside `VIEW_TYPE_ITEM = 1`, `VIEW_TYPE_FOOTER = 2`.
- Change the backing data from `MutableList<ServersCache>` to a flat `List<Row>` where
  `Row = Header(subId, remarks, count)` | `Server(ServersCache)`. Build it in a new
  `setSections(servers, subs)` (or keep `setData` and compute headers internally by grouping
  `ServersCache.profile.subscriptionId`). `getItemViewType` returns HEADER for header rows, FOOTER at
  the end, ITEM otherwise. `onBindViewHolder` for HEADER inflates a lightweight section header
  (a small new `item_section_header.xml`: chevron + provider remark + count) and toggles collapse.
- Collapse-per-section: keep a `Set<String>` of collapsed subIds in the adapter; collapsed sections
  omit their `Server` rows from the flat `Row` list. "Collapse all" (header button, §4) flips all.
- Note: when only one provider exists, headers can be suppressed (Home already shows the meta bar as
  the single provider header) — pass a flag or skip HEADER rows when `subs.size <= 1`.

### 2.2 Remove inline actions
- Delete all `layout_share`/`layout_edit`/`layout_remove`/`layout_more` binding + click code
  (`onBindViewHolder` lines ~89–114) and the `doubleColumnDisplay` branch that toggles them. The
  `PREF_DOUBLE_COLUMN_DISPLAY` grid can stay for layout only; action visibility logic is removed.
- Replace with a single long-press handler on `info_container` (or `holder.itemView`):
  `holder.itemMainBinding.infoContainer.setOnLongClickListener { adapterListener?.onShare(guid,
  profile, position, true); true }`. Short click keeps `onSelectServer(guid)`.
- `MainAdapterListener`/`BaseAdapterListener` interfaces are unchanged (share/edit/remove callbacks
  still exist and are reused by the long-press bottom sheet). `onShare(guid, profile, position, more)`
  remains the entry point.

### 2.3 Flag + chips + selection
- `tv_flag` already bound via `FlagUtil.resolveFlag(profile)`; keep.
- Split `getProtocolDescription()`: set `tv_type` to the primary protocol chip
  (`profile.configType.name`, or "Auto" for policy groups), set `tv_json` visible+"JSON" when
  `profile.configType.isComplexType()`, and put transport·security (existing hide-tcp/hide-tls logic)
  into `tv_statistics` grey text.
- Selection: keep the `guid == MmkvManager.getSelectServer()` check; set `bg_server_row` selected
  state (blue outline) on `info_container` instead of only `layout_indicator` color. Keep
  `setSelectServer(from,to)` → `notifyItemChanged` (translate to flat-row positions).
- Ping: bind `dot_ping` tint from `aff.testDelayMillis` sign (same green/red rule as `tv_test_result`).

### 2.4 Search + protocol filter feeding the adapter
- Both are computed in `MainViewModel.updateCache()` (single source of truth), NOT in the adapter.
  The adapter only renders `serversCache`.
- Search: wire `et_search` `doAfterTextChanged` → `mainViewModel.filterConfig(text)` (existing;
  sets `keywordFilter`, calls `reloadServerList()` → `updateCache()` → `updateListAction`). The
  toolbar `SearchView` in `menu_main` can be removed or left as a redundant path.
- Protocol filter: add `var protocolFilter: EConfigType? = null` to `MainViewModel`; extend
  `updateCache()` to skip profiles whose `configType != protocolFilter` when it is non-null. Add
  `fun setProtocolFilter(type: EConfigType?)` that sets it and calls `reloadServerList()`. Add
  `fun availableProtocols(): List<EConfigType>` returning the distinct `configType`s present in the
  full (unfiltered) server list, used to build the chips. Chip check → `setProtocolFilter`; "Все" →
  `null`.

---

## 3. Eliminating the ViewPager/tabs without breaking scoping (task item 3)

### 3.1 Delete
- `ui/GroupPagerAdapter.kt` (whole file).
- `MainActivity`: `groupPagerAdapter`, `tabMediator`, `setupGroupTab()`, `reloadSubscriptionTabs()`,
  `TabLayoutMediator` import, the `view_pager`/`tab_group` setup in `onCreate`,
  `consumeSetupGroupTab()` handling, and the `f$itemId`/`findFragmentByTag` logic in
  `locateSelectedServer()`/`scrollToSelectedServer()` (reimplement locate against `rv_servers`).
- `ui/GroupServerFragment.kt` is no longer hosted by a pager. Two options:
  - **(A, recommended)** Retire the fragment; move its reusable logic — `bindMetaBar()`/`setupMetaBar`
    /`refreshSub()`/`pingSub()`/`togglePin()` and the share/edit/remove/select handlers
    (`shareServer`, `showQRCode`, `share2Clipboard`, `shareFullContent`, `editServer`,
    `removeServer`, `setSelectServer`, `ActivityAdapterListener`) — into `MainActivity` (or a small
    `ServerListController` helper) that binds both `rv_home_servers` and `rv_servers` to one shared
    `MainRecyclerAdapter` each, plus the meta-bar binder used for `layout_home_meta_bar`.
  - (B) Keep `GroupServerFragment` as a single embedded fragment used by both tabs. Heavier (two
    fragment instances / nested-scroll issues on Home); not recommended.

### 3.2 Scoping
- Force all-servers mode: on `onCreate`, set `mainViewModel.subscriptionId = ""` (or call
  `subscriptionIdChanged("")`) so `reloadServerList()` uses `decodeAllServerList()` and the flat list
  contains every provider. `CACHE_SUBSCRIPTION_ID` persistence in `subscriptionIdChanged` is harmless.
- `updateListAction` observer: drop the `subscriptionId != subId` guard (there is now one list).
  Just `adapter.setSections(mainViewModel.serversCache, subs)` on each event; keep the index fast-path
  by translating server index → flat-row position.
- Provider grouping for headers: group `serversCache` by `profile.subscriptionId`, resolve remarks via
  `MmkvManager.decodeSubscription(subId)?.remarks`. Order pinned-first (reuse the
  `sortedByDescending { pinned }` logic from `getSubscriptions`). Un-subscribed servers ("") group
  under a default "Локальные"/"Default" header.
- Meta bar on Home (`layout_home_meta_bar`): bind to the currently selected server's subscription
  (or the single/first provider) using the moved `bindMetaBar` logic. On Servers, the per-provider
  section header (`item_section_header.xml`) is the collapsible header; the full traffic/announce
  meta bar can also be shown as the first expanded section body if desired (optional for S3).

### 3.3 Drag persistence
- `swapServer()` returns early when `subscriptionId.isEmpty()` — so in all-servers mode reorder won't
  persist. For S3 keep drag but constrain it: in `onItemMove`, reject moves that cross a section
  header or change `profile.subscriptionId` (compare neighbours), and persist within a provider by
  extending `swapServer` to reorder that provider's `MmkvManager.decodeServerList(subId)` and re-save
  via `encodeServerList(list, subId)`. Simplest compile-safe fallback: disable drag on the grouped
  list (don't attach `ItemTouchHelper`) and rely on `sort_by_test_results`; note this in the commit.

---

## 4. Empty state + header actions (task items 4 & 1)

- Visibility: after each `updateListAction`, if `mainViewModel.serversCache.isEmpty()` (with no
  keyword/protocol filter active) show `layout_empty` and hide `rv_servers`; else inverse. (When a
  filter is active but empty, show a lighter "no matches" text, not the import card.)
- `btn_import_clipboard` → `MainActivity.importClipboard()` (existing; wraps
  `Utils.getClipboard` → `importBatchConfig` → `AngConfigManager.importBatchConfig`).
- `btn_scan_qr` → `MainActivity.importQRcode()` (existing; `launchQRCodeScanner { importBatchConfig }`
  → `ScannerActivity`).
- Header buttons (Servers tab, `layout_servers_header`):
  - `btn_add` → reuse the existing import menu (show the `menu_main` import submenu as a popup, or
    call `importClipboard()`/`importManually(...)`).
  - `btn_refresh_all` → `importConfigViaSub()` (existing).
  - `btn_speedtest_all` → `mainViewModel.testAllServers()` + the existing testing toast.
  - `btn_collapse_all` → adapter collapse-all toggle (§2.1).
  - `tv_servers_subtitle` → set to `getString(servers_count, n)` + `providers_count, m` from
    `serversCache.size` and distinct `subscriptionId` count.
  - Chips built from `mainViewModel.availableProtocols()` (§2.4).

---

## 5. Step-by-step, compile-safe commit plan (real symbols)

Each step compiles and runs on its own.

1. **ViewModel filter plumbing.** Add `protocolFilter`, `setProtocolFilter()`, `availableProtocols()`
   to `MainViewModel`; extend `updateCache()` with the `configType` guard. No UI change yet. Compiles;
   `filterConfig` still works. (`viewmodel/MainViewModel.kt`)

2. **Row layout + adapter: flat rows, drop inline actions, add flag/chips/ping dot.** Edit
   `item_recycler_main.xml` (remove `layout_share/edit/remove/more`, add `tv_json`, `dot_ping`,
   `bg_server_row`); edit `MainRecyclerAdapter.onBindViewHolder` to delete the action-cluster code and
   add long-press → `onShare(...,more=true)`, split chips, bind `dot_ping`, selected outline. Still
   pager-hosted; `GroupServerFragment` unchanged. Compiles + runs under existing tabs.

3. **Servers header + empty state layouts.** Add `layout_servers_header.xml`,
   `layout_servers_empty.xml`, `item_section_header.xml`, new strings, chevron/add drawables. Not yet
   wired. Compiles.

4. **Adapter section headers + collapse.** Add `VIEW_TYPE_HEADER`, `Row` model, `setSections()`,
   collapse set, collapse-all, `item_section_header` binding in `MainRecyclerAdapter`. Keep a
   `setData` shim delegating to `setSections` so `GroupServerFragment` still compiles. Compiles.

5. **Rewire `activity_main.xml`.** Replace `group_servers` internals with `layout_servers_header` +
   `rv_servers` + `layout_empty`; add `layout_home_meta_bar` + `rv_home_servers` to `group_home`;
   make `layout_subscription_meta_bar` collapsible (`btn_collapse`, `layout_meta_body`). Delete
   `tab_group`, `view_pager`. (`res/layout/activity_main.xml`, `layout_subscription_meta_bar.xml`)

6. **MainActivity: drop pager, host lists.** Remove `groupPagerAdapter`/`tabMediator`/`setupGroupTab`/
   `reloadSubscriptionTabs`/`TabLayoutMediator`; delete `GroupPagerAdapter.kt`. Set
   `mainViewModel.subscriptionId = ""`. Create one `MainRecyclerAdapter` for each of `rv_servers` and
   `rv_home_servers`, attach `ActivityAdapterListener` logic (moved from `GroupServerFragment`),
   observe `updateListAction` → `setSections(...)`, wire header buttons, search
   (`et_search`→`filterConfig`), chips (→`setProtocolFilter`), empty-state buttons
   (`importClipboard`/`importQRcode`), and the meta-bar binder for `layout_home_meta_bar`.
   Reimplement `locateSelectedServer()`/`scrollToSelectedServer()` against `rv_servers`. Remove
   `consumeSetupGroupTab()` usage. (`ui/MainActivity.kt`)

7. **Retire `GroupServerFragment.kt`.** Delete the file (its logic now lives in MainActivity /
   `ServerListController`). Remove `fragment_group_server.xml` if unused. Clean up
   `SettingsChangeManager.consumeSetupGroupTab` call sites. Compiles.

8. **Long-press bottom sheet (optional polish).** Replace the `AlertDialog.setItems` share menu with a
   Material `BottomSheetDialog` reusing the same `share_method`/`share_method_more` arrays and the
   moved `shareServer(...)` dispatcher (QR/clipboard/full/edit/remove). Behaviour-equivalent.

Backward-compat guardrails: keep `MainAdapterListener`/`BaseAdapterListener` signatures, keep
`serversCache`/`updateListAction`/`filterConfig`/`subscriptionIdChanged` as-is, keep row ids
`tv_flag/tv_name/tv_type/tv_statistics/tv_test_result/layout_indicator/tv_subscription`, and keep all
Home hero ids MainActivity binds to.

---

## Summary (8 lines)

1. Recommended structure: one `RecyclerView` + one `MainRecyclerAdapter` in all-servers mode
   (`subscriptionId=""`) with an added SECTION-HEADER view type — lightest, reuses selection/drag/
   `updateListAction`; avoid `ConcatAdapter` and per-group fragments.
2. `activity_main.xml`: drop `tab_group`+`view_pager`; Servers = `layout_servers_header` + `rv_servers`
   + `layout_empty`; Home = existing hero + `layout_home_meta_bar` + non-nested `rv_home_servers`.
3. Make `layout_subscription_meta_bar` collapsible (`btn_collapse` + `layout_meta_body`), reused as the
   provider header on Home; per-provider `item_section_header` collapses sections on Servers.
4. Adapter: delete `layout_share/edit/remove/more`, add long-press→`onShare(more=true)`, split
   protocol chips (`tv_type` blue / `tv_json` gold / `tv_statistics` grey), add `dot_ping`, blue-outline
   selected state; keep stable ids.
5. Search reuses `MainViewModel.filterConfig`; protocol chips via new `protocolFilter`/
   `setProtocolFilter`/`availableProtocols` folded into `updateCache()`.
6. Scoping preserved by forcing `subscriptionId=""` and grouping `serversCache` by
   `profile.subscriptionId` (pinned-first); drop the per-fragment `subscriptionId!=subId` guard.
7. Drag caveat: `swapServer()` no-ops when `subscriptionId` is empty — either constrain drag within a
   provider (extend `swapServer` to save that sub's list) or disable drag in grouped mode.
8. Empty state card wires `btn_import_clipboard`→`importClipboard()` and `btn_scan_qr`→`importQRcode()`
   (`ScannerActivity`); delete `GroupPagerAdapter`/`GroupServerFragment` per the 8-step commit plan.
