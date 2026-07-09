# S3 Implementation Report — "Servers" Redesign (Incy parity)

Branch: `claude/vpn-client-happ-design-mq51pv`. Package `com.v2ray.ang`, module `V2rayNG/app`.
Not built locally (no Android SDK); every XML validated with `xmllint --noout` and all
`@id/@string/@drawable/@color/@style/@attr` references grep-checked against the repo.

## What changed

### ViewModel — `viewmodel/MainViewModel.kt`
- Added `var protocolFilter: EConfigType? = null`; `updateCache()` now skips profiles whose
  `configType` differs when the filter is non-null (folded into the single source of truth).
- Added `setProtocolFilter(type)`, `availableProtocols()` (distinct types in the full unfiltered
  list, first-appearance order), and `getProviderGroups()` (real subscriptions pinned-first, no
  synthetic "All" group) used to build section headers.

### Adapter — `ui/MainRecyclerAdapter.kt` (rewritten)
- New `VIEW_TYPE_HEADER=0` alongside `ITEM=1`/`FOOTER=2`; backing data is now a flat
  `List<Row>` (`Row.Header` | `Row.Server`) built by `setSections(servers, subs, showHeaders, index)`.
  Headers suppressed when `showHeaders=false` or only one provider present.
- Per-section collapse (`Set<String>` of subIds) + `toggleCollapseAll()`; collapsed sections omit
  their server rows. Un-subscribed servers group under a "Локальные" header.
- Removed all inline `layout_share/edit/remove/more` binding; short click = `onSelectServer`,
  long-press on `info_container` = `onShare(guid, profile, pos, more=true)`.
- Split protocol chips: `tv_type` (blue primary — "Auto" for policy group, "Chain"/"Custom"),
  gold `tv_json` shown for complex types, `tv_statistics` grey transport·security. Added `dot_ping`
  (green ok / red bad or untested). Selection uses `info_container.isSelected` → `bg_server_row`
  blue outline (indicator bar kept too).
- Kept `setData` shim (flat, no headers) so nothing else breaks. `setSelectServer(fromGuid,toGuid)`,
  `positionOfGuid(guid)`, `removeServerSub(guid,pos)` added/updated.

### Activity — `ui/MainActivity.kt`
- Deleted the ViewPager2/TabLayout wiring (`groupPagerAdapter`, `tabMediator`, `setupGroupTab`,
  `reloadSubscriptionTabs`, `TabLayoutMediator` import). Forces `subscriptionId = ""` in `onCreate`.
- Hosts two `MainRecyclerAdapter`s: `rv_servers` (grouped, headers) and `rv_home_servers` (flat),
  both `LinearLayoutManager`, sharing one `ActivityAdapterListener`. Observes `updateListAction` →
  `refreshServerLists()` (rebuilds both, updates subtitle counts / chips / empty state / home meta bar).
- Servers header wired: collapse-all, refresh-all (`importConfigViaSub`), speedtest-all
  (`testAllServers`), add (+ → `menu_main` PopupMenu), search (`et_search` → `filterConfig`),
  protocol chips (built from `availableProtocols()`, check → `setProtocolFilter`).
- Empty state buttons → `importClipboard()` / `importQRcode()`.
- Moved the meta-bar binder + `refreshSub`/`pingSub`/`togglePin`/`openSubUrl` and the
  `shareServer/showQRCode/share2Clipboard/shareFullContent/editServer/removeServer/setSelectServer`
  handlers from `GroupServerFragment` into `MainActivity`. Home meta bar (`layout_home_meta_bar`)
  binds to the selected server's subscription (or first provider) and is collapsible.
- Reimplemented `locateSelectedServer()` against `rv_servers` (removed fragment/pager scroll logic).
- `importBatchConfig` `countSub>0` and `consumeSetupGroupTab` now call `reloadServerList()`.

### Layouts
- `res/layout/activity_main.xml`: removed `tab_group`+`view_pager`; Servers = `layout_servers_header`
  + `FrameLayout(rv_servers + layout_empty)`; Home = existing hero + `layout_home_meta_bar` +
  non-nested `rv_home_servers`.
- `res/layout/item_recycler_main.xml`: dropped the action cluster + `nextFocusRight`; background →
  `bg_server_row`; added gold `tv_json` chip and `dot_ping`.
- `res/layout/layout_subscription_meta_bar.xml`: added `btn_collapse`; wrapped traffic/announce/
  support-website in `layout_meta_body` (collapsible).
- New: `layout_servers_header.xml`, `layout_servers_empty.xml`, `item_section_header.xml`.
- New drawables: `bg_server_row.xml` (selected outline), `bg_chip_gold.xml`, `bg_search_pill.xml`.
- New strings (values + values-ru): `title_servers`, `servers_count`, `providers_count`,
  `search_hint`, `servers_empty_title`, `servers_add_clipboard`, `servers_section_local`.

### Deleted
- `ui/GroupPagerAdapter.kt`, `ui/GroupServerFragment.kt`, `res/layout/fragment_group_server.xml`.

## IDs renamed / removed
- No Kotlin-referenced row ids were renamed (kept `tv_flag/tv_name/tv_type/tv_statistics/`
  `tv_test_result/layout_indicator/layout_subscription/tv_subscription/info_container`).
- Removed row ids: `layout_share`, `layout_edit`, `layout_remove`, `layout_more` (inline actions gone).
- Removed activity ids: `tab_group`, `view_pager`. Added: `rv_servers`, `rv_home_servers`,
  `layout_servers_header`, `layout_home_meta_bar`, `layout_empty`, plus new-layout ids.
- Meta bar gained `btn_collapse`, `layout_meta_body` (all previous meta ids preserved).

## Drag decision
**Disabled drag in the grouped all-servers list** — the simplest compile-safe option per §3.3.
No `ItemTouchHelper` is attached to `rv_servers`/`rv_home_servers` (the old per-fragment helper is
gone), and `MainViewModel.swapServer()` already no-ops when `subscriptionId==""`. Reordering relies
on `sort_by_test_results`. Adapter's `onItemMove` returns false (inert) to satisfy the interface.

## Risky / follow-ups
- Cannot compile locally — CI on push is the real gate. Main risk areas: view-binding field names
  for the three new includes and the two RecyclerViews, and programmatic Material `Chip` creation
  (built via a `ContextThemeWrapper(Widget.Material3.Chip.Filter)` so it renders/behaves as a
  checkable filter chip).
- `PREF_DOUBLE_COLUMN_DISPLAY` grid mode is no longer honored for the server lists (both use a
  single-column `LinearLayoutManager`); grouped headers don't fit a 2-column grid cleanly.
- `MainViewModel.getSubscriptions()` is now unused (kept, harmless) — can be removed in S4.
- `refreshServerLists` runs on every per-server ping tick; `buildProtocolChips` early-returns when
  the protocol set is unchanged, and header updates use an index fast-path (`notifyItemChanged`).
