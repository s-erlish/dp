# Android UI audit — defects and design-law violations

Scope: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/**` (all Activities, Fragments, Adapters,
Sheets) and the layouts they inflate in `V2rayNG/app/src/main/res/layout/**`.
Every claim below cites a file and line I read. Nothing is inferred from filenames or comments.

Design law referenced: `/home/user/dp/CLAUDE.md` ("Incy": pure dark, ONE blue accent,
Space Grotesk, Russian sentence-case, ONE spacing scale, ONE 16dp gutter, radius tokens,
40dp tiles / 22dp glyphs, ≥48dp touch targets, no decorative gradients/glows, no nested cards,
no emoji chrome, no ALL-CAPS eyebrows, every state designed).

Absolute paths are used throughout (`/home/user/dp/V2rayNG/app/src/main/…`).

---

## A. Runtime defects

### A1 — CRITICAL: every per-server action is unreachable (share / QR / edit / duplicate / set-default / delete)

`MainActivity` wires long-press on both server lists to the Incy actions sheet:

```
MainActivity.kt:616-618
    // Long-press a server row -> Incy server-actions bottom sheet (S3 moved inline actions here).
    serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }
    homeAdapter.onItemLongClick   = { guid -> showServerActions(guid) }
```

But the adapter never invokes that callback. `MainRecyclerAdapter.kt:52-56` documents it as retired,
and `bindServer` sets no long-click listener at all:

```
MainRecyclerAdapter.kt:232-236
    binding.infoContainer.setOnClickListener { adapterListener?.onSelectServer(guid) }
    // Long-press server-actions menu removed: long-press is a no-op (no listener set).
```

`grep -n "adapterListener" MainRecyclerAdapter.kt` returns exactly two hits — the constructor
param (`:32`) and `onSelectServer` (`:233`). So `MainAdapterListener.onShare`, `onEdit`, `onRemove`
(`contracts/MainAdapterListener.kt:7-11`) are never called either.

Consequences, all confirmed by call-graph:
* `ServerActionsSheet` (`ServerActionsSheet.kt:32-63`) can never be shown.
* `showServerActions` (`MainActivity.kt:626-645`), `shareServer` (`:1325`), `showQRCode` (`:1342`),
  `share2Clipboard` (`:1349`), `shareFullContent` (`:1354`), `editServer` (`:1363`),
  `removeServer` (`:1383`) are dead.
* **A user cannot delete a server, copy a server link, show its QR, edit it, or duplicate it.**
  The only thing a row does is select (`setSelectServer`, `:1412`).

Severity: critical — a whole feature surface that the code, comments and the sheet layout
(`res/layout/sheet_server_actions.xml`) all still ship.

### A2 — HIGH: `onKeyDown` swallows BACK, so the tab-back navigation callback is dead code

```
MainActivity.kt:257-270   onBackPressedDispatcher.addCallback(...)  // "back on a non-Home tab -> Home"
MainActivity.kt:2361-2367
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
```

`onKeyDown` consumes `KEYCODE_BACK` on key-DOWN and returns `true` without `event.startTracking()`,
so `Activity.onKeyUp` never runs `onBackPressed()` and `OnBackPressedDispatcher` is never consulted.
Pressing Back on Servers / Settings / Account minimises the app instead of returning to Home.
`AndroidManifest.xml:54-70` sets no `enableOnBackInvokedCallback`, so the legacy key path is the
one in effect.

### A3 — HIGH: rapid tab switching leaves the wrong tab on screen (fade-through race)

```
MainActivity.kt:466-478
    outgoing.animate().alpha(0f).setDuration(150)…withEndAction {
        outgoing.isVisible = false; outgoing.alpha = 1f
        incoming.alpha = 0f; incoming.translationY = dy; incoming.isVisible = true
        incoming.animate().alpha(1f).translationY(0f).setDuration(200)…start()
    }.start()
```

A→B then B→C within 150 ms: the second call starts an `alpha(0f)` animation on **B** with its own
`withEndAction` that would reveal C. When A's end-action fires (t=150 ms) it calls
`B.animate().alpha(1f)…`, which cancels B's in-flight animation on the same `ViewPropertyAnimator`.
`withEndAction` is documented not to run on cancel, so **C is never made visible**: `selectedNavId`
and the nav pill say C while B is what the user sees, until the next tab switch.

### A4 — HIGH: `PaymentMethodSheet` callback survives rotation and then crashes the caller

`PaymentMethodSheet.show` stores the picker lambda in a process-static map
(`PaymentMethodSheet.kt:154-155, 170-171`) and recovers it in `onCreate` (`:53-56`).
`onDestroy` deliberately keeps it across configuration changes (`:132-138`).

From `AccountFragment`, the lambda captures the *fragment instance*:

```
AccountFragment.kt:540-558
    PaymentMethodSheet.show(parentFragmentManager, …) { id ->
        … viewModel.buy(PaymentRequestDto(…), ::openCheckout)
    }
AccountFragment.kt:618-635  openCheckout → toastError(...) / CustomTabsIntent…launchUrl(requireContext(), uri)
```

Rotate while the sheet is open → the sheet is recreated and re-binds the **old** fragment's lambda.
Tapping a method calls `::openCheckout` on a fragment whose view is destroyed;
`requireContext()` (`:629`) and `toastError` → `requireContext()` (`:227`) throw
`IllegalStateException`. `viewModel` is likewise the dead instance.

### A5 — HIGH: the "Привязать Telegram" CTA banner can never be shown

`updateAccountGate` hard-disables the whole signed-out group and only shows the header when signed in:

```
MainActivity.kt:1056-1061
    header.root.isVisible   = loggedIn
    header.groupLogin.isVisible = false          // <-- always GONE
    header.chipAccount.isVisible = loggedIn
```

The CTA lives *inside* `group_login` (`res/layout/layout_home_account.xml:22-27, 30-31`), and
`updateLoginCtaVisibility` only ever wants it when **signed out** (`MainActivity.kt:1094-1096`) —
which is exactly when `header.root` is GONE. So `cta_link_telegram`, its dismiss button
(`layout_home_account.xml:62-73`), the `ctaDismissed` flag (`MainActivity.kt:122`) and both click
handlers (`MainActivity.kt:987-991`) are unreachable dead UI/state.

### A6 — HIGH: the Account fragment is never detached on logout and keeps running

`showTab` attaches `AccountFragment` once and latches the flag (`MainActivity.kt:439-444`).
On logout `updateAccountGate` only hides the nav item and falls back to Home
(`MainActivity.kt:1061-1063`) — it never removes the fragment. The fragment stays attached to the
GONE `group_account` container, so its `repeatOnLifecycle(STARTED)` collectors
(`AccountFragment.kt:199-222`) and `AccountViewModel` stay live and keep issuing authenticated
account/subscription/payments calls with a dropped session. Signing back in re-uses the stale
fragment (or, after an activity recreate, `replace()` builds a *second* one — `:441-443` —
while the FragmentManager has already restored the first).

### A7 — MEDIUM: eight implemented MainActivity features have no entry point

`menu_main.xml` (the only menu still inflated, via `showImportMenu`, `MainActivity.kt:661-666`)
contains four items: `import_qrcode`, `import_clipboard`, `tv_send`, `import_manually_vless`.
The following private methods are defined and never called anywhere in the file:

| Feature | Line |
| --- | --- |
| `importManually(createConfigType)` | `MainActivity.kt:2035` |
| `importConfigLocal()` → `showFileChooser()`/`readContentFromUri()` | `:2186`, `:2313`, `:2326` |
| `exportAll()` | `:2228` |
| `delAllConfig()` | `:2242` |
| `delDuplicateConfig()` | `:2261` |
| `delInvalidConfig()` | `:2280` |
| `sortByTestResults()` | `:2299` |
| `locateSelectedServer()` | `:2339` |
| `currentMetaSubId()` | `:1161` |

Also `onOptionsItemSelected`'s `R.id.sub_update` branch (`:2027-2030`) can never fire — no menu
resource inflated by this activity declares that id.

Net effect: "удалить все / дубликаты / нерабочие", "экспортировать все", "сортировать по пингу",
"найти выбранный сервер" and "импорт из файла" are gone from the UI while their code still ships.

### A8 — MEDIUM: four declared Activities are unreachable

`grep` across `java/` + `res/` finds zero references outside their own file for:
`SettingsActivity` (declared `AndroidManifest.xml:89`), `SubSettingActivity`, `LogcatActivity`,
`CheckUpdateActivity`. `MainActivity.kt:2375` even calls `SettingsActivity` "legacy", but the class
(309 lines) and its manifest entry are still shipped, so a stale, un-Incy preferences screen is one
`adb`/intent away and adds maintenance surface.

### A9 — MEDIUM: `notifyDataSetChanged` on the Home carousel fires per ping result

`updateListAction` is posted **once per server** while pinging (`viewmodel/MainViewModel.kt:274`,
`:345`, `:369`). Its observer (`MainActivity.kt:526`) runs `refreshServerLists`, which calls
`rebuildHomeMeta()` (`:768`) → `homeMetaAdapter.submit(ids)` →
`notifyDataSetChanged()` (`HomeMetaPagerAdapter.kt:31-35`) **and** `measureHomeMetaHeight()`
(`:887`), which inflates a fresh `LayoutSubscriptionMetaBarBinding` and binds+measures it for every
subscription inside `doOnPreDraw` (`MainActivity.kt:895-917`).

"Проверить все" on a 15-server list therefore triggers 15 full ViewPager2 rebuilds plus 15 layout
inflations and pre-draw measure passes. `notifyDataSetChanged()` on a ViewPager2 adapter with no
stable ids also resets page state. This is the classic "notifyDataSetChanged hiding a real diff".

### A10 — MEDIUM: `pendingPayment` is never cleared when the poll is cancelled

```
AccountFragment.kt:649-661
    pollJob = viewLifecycleOwner.lifecycleScope.launch {
        repeat(6) { … delay(8000L) }
        pendingPayment = false
        binding.tvPending.visibility = View.GONE
    }
```
`onResume` restarts polling whenever `pendingPayment` is true (`:640-643`). If the view is
destroyed mid-poll (tab recreate, theme/language change — `MainActivity.pickAppearance` calls
`recreate()`, `:2742`) the coroutine is cancelled before those two lines, so `pendingPayment`
stays `true` forever and the fragment re-polls the backend on every single resume.
`BuyTariffActivity.kt:582-594` has the same shape (its `pendingPayment` field is simply lost on
rotation instead, so the pending hint silently disappears).

### A11 — MEDIUM: BuyTariff loses its selection but keeps the checkout card

`selectedTariff` / `selectedOption` / `extraDevices` are plain fields with no
`onSaveInstanceState` (`BuyTariffActivity.kt:52-54`). Worse, `observe()` collects
`tariffs.combine(error)` (`:135-138`), so **any** error transition (including
`viewModel.clearError()`) re-runs `renderState()` → `renderTariffs()`, which does
`tariffsContainer.removeAllViews(); checkMarks.clear(); optionRows.clear()` and rebuilds every card
(`:226-235`) **without re-applying the selection highlight**. `checkoutCard.visibility` is left
untouched (only set at `:371`/`:380`), so the user is left looking at a checkout card showing an
«Итого» for a tariff that is no longer visibly selected.

### A12 — MEDIUM: changing per-app proxy never restarts the tunnel

```
PerAppProxyActivity.kt:51-59
    binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, isChecked) }
```
and `PerAppProxyAdapter.onClick` (`PerAppProxyAdapter.kt:79-83`) writes the app selection.
Neither calls `SettingsChangeManager.makeRestartService()` — only the overflow menu actions do
(`PerAppProxyActivity.kt:239-242`). `MainActivity`'s launcher only restarts when that flag is set
(`MainActivity.kt:224-226`), so toggling the switch or picking apps while connected silently does
nothing until the user manually reconnects.

Two more issues in the same file: the `setOnCheckedChangeListener` is installed *before*
`isChecked` is seeded (`:51-54`, `:56-59`), and `filterProxyApp` allocates a **new adapter and
re-attaches it on every keystroke** (`:294-316`), re-inflating and re-loading app icons per
character.

### A13 — MEDIUM: `customProtoCache` is never invalidated

`MainRecyclerAdapter.kt:277-298` caches parsed protocol/transport info per guid in a plain
`HashMap` that is never cleared — not in `setSections`, not in `removeServerSub`. Editing a
`CUSTOM` (Remnawave XRAY_JSON) profile leaves the old protocol/transport chips on the row until the
process restarts, and the map grows unbounded across subscription refreshes.

### A14 — MEDIUM: dialogs are leaked/lost on every rotation

`MainActivity` has no `android:screenOrientation` and no `android:configChanges`
(`AndroidManifest.xml:54-70`), yet every dialog is fire-and-forget with no reference kept and no
`onDestroy` dismissal (`MainActivity.onDestroy`, `:2832-2840`, only cancels handlers/toast):
`showManualEntryDialog` (`:2062-2091`), `pickMode` (`:2503`), `pickPingMethod` (`:2551`),
`editDns` (`:2611`), `editDnsCustom` (`:2638`), `editMuxConcurrency` (`:2668`),
`pickAppearance` (`:2718`), `pickLanguage` (`:2753`), `pickSubAutoUpdate` (`:2809`),
`confirmDeleteSubscription` (`:960`), `delAllConfig`/`delDuplicateConfig`/`delInvalidConfig`.
Rotating with one open produces `android.view.WindowLeaked` and drops any typed input
(e.g. a half-pasted subscription link in the manual-entry dialog).
`ServerActionsSheet` (`ServerActionsSheet.kt:34-62`) has the same shape with a raw
`BottomSheetDialog`.

### A15 — LOW/MEDIUM: `notifyDataSetChanged` used as the only refresh primitive across adapters

`SubscriptionPagerAdapter.submit` (`:30-34`), `HomeMetaPagerAdapter.submit` (`:31-35`),
`DeviceAdapter.submit` (`adapter/DeviceAdapter.kt:22-26`), `PaymentsAdapter.submit`
(`adapter/PaymentsAdapter.kt:23-27`), `RoutingSettingActivity.refreshData` (`:192-196`),
`SubSettingActivity.refreshData` (`:104-108`), `PerAppProxyActivity.refreshData` (`:318-321`).
`AccountFragment` additionally calls `subAdapter.notifyDataSetChanged()` from two separate flow
collectors (`AccountFragment.kt:206`, `:213`), rebuilding the whole subscription carousel every
time the tariff catalog or device count lands.

### A16 — LOW: stale positions captured in `onBindViewHolder`

`RoutingSettingRecyclerAdapter.kt:34-42` captures the bind-time `position` in both the edit click
(`adapterListener?.onEdit("", position)`) and the enable switch
(`viewModel.update(position, ruleset)`), while drag-reorder is attached
(`RoutingSettingActivity.kt:55-56`). In practice the window is small because
`SimpleItemTouchHelperCallback.clearView` → `onItemMoveCompleted()` → `refreshData()` →
`notifyDataSetChanged()` (`helper/SimpleItemTouchHelperCallback.kt:125-132`,
`RoutingSettingRecyclerAdapter.kt:74-76`), but the pattern is one `notifyItemMoved`-only change
away from writing a toggle into the wrong rule. Same pattern in
`SubSettingRecyclerAdapter.kt:33-39` and `ServerProxyChainMemberAdapter.kt:34, 47-60`
(`adapterPos` is snapshotted at bind time, so it is no fresher than `position`).

### A17 — LOW: dead `position` parameter creates a false invariant

`MainRecyclerAdapter.removeServerSub(guid, position)` ignores `position` entirely
(`:302-306`). Callers pass **mismatched** values into it: `MainActivity.kt:1400-1401` feeds the
same position to both the header-bearing `serversAdapter` and the header-less `homeAdapter`, and
`MainActivity.kt:643` computes `serversAdapter.positionOfGuid(guid)` even when the action came from
the Home list. Harmless today only because the parameter is unused — it will bite the moment anyone
"optimises" the removal into `notifyItemRemoved(position)`.

### A18 — LOW: disk I/O per row bind

`UserAssetAdapter.onBindViewHolder` calls `extDir.listFiles()` on the UI thread for every row
(`UserAssetAdapter.kt:36-38`) and formats a `DateFormat` instance per bind (`:43`).

### A19 — LOW: English string used as the connect-hero placeholder and the "no server" toast

`values/strings.xml:137` — `title_file_chooser` = `"Select a config"` (English) in the default
resource set, which is the Russian-facing one (`values/strings.xml:23` = `"Выберите сервер"`).
It is used as the layout default for the hero label (`res/layout/activity_main.xml:305`) and as the
toast when connecting with no server selected (`MainActivity.kt:1520-1522`) and in
`locateSelectedServer` (`:2342`). `values-ru/strings.xml:131` overrides it, so the English text only
shows on a device whose locale is not `ru` — the exact case the in-app language picker creates
(`MainActivity.pickLanguage`, `:2748-2763`). Semantically it is also the wrong string: a file-chooser
title standing in for "no server selected".

### A20 — LOW: hard-coded formatting / dimensions in Kotlin

* `MainActivity.kt:1967` — `String.format("%02d:%02d:%02d", …)` with no `Locale` (lint
  `StringFormatMatches`/locale; renders Eastern-Arabic digits under `values-ar`, which ships).
* `MainActivity.kt:1929` — timer reset writes the literal `"00:00:00"`.
* `MainActivity.kt:512-518` — insets padding hard-codes `56 * density` and `16 * density` instead of
  `@dimen/row_min_height` / `@dimen/space_16`; `:1742` hard-codes `110 * density` for the toast
  offset; `:789` hard-codes `12f * density`; `:468` hard-codes `8f * density`.
* `AccountFragment.kt:113-114` — `updatePadding(bottom = (96 * density))` for the bottom-nav
  clearance, ignoring the system nav inset entirely (Home/Servers use
  `bars.bottom + nav + breathing`, `MainActivity.kt:516-520`), so on 3-button navigation the last
  Account card can sit under the bar.
* `MainActivity.kt:1734-1746` — `showStatusToast` builds a `Toast` with `setView` (deprecated since
  API 30 and ignored for background apps); every VPN state change goes through it.

---

## B. Design-law violations in the layouts

### B1 — Decorative gradients and a glow, explicitly banned

* `res/layout/activity_main.xml:8` — `android:background="@drawable/bg_home_gradient"`, a **radial**
  gradient over the whole home screen (`drawable/bg_home_gradient.xml`, `drawable-night/bg_home_gradient.xml`,
  plus `_mono` variants applied at `MainActivity.kt:1564-1572`).
* `res/layout/activity_main.xml:207-212` — `view_connect_glow` with `drawable/bg_connect_glow.xml`
  (radial blue halo), animated as a breathing pulse (`MainActivity.kt:1794-1810`) and a reveal
  (`:1649-1652`).
* `drawable/bg_nav_header.xml` — 135° `brand_blue → brand_blue_dark` linear gradient.
* `drawable/bg_bottom_nav_scrim.xml` used at `activity_main.xml:512-517` is a functional
  content-fade scrim (defensible), but it is still a 160dp gradient slab.

Note: `drawable/bg_traffic_gradient.xml` has already been converted to a flat pill and documents the
rule in its own comment — the home gradient and connect glow are the surviving violations.

### B2 — Touch targets under 48dp (10 layouts)

| Element | File:line | Size |
| --- | --- | --- |
| `btn_ping`, `btn_refresh`, `btn_pin` | `layout_subscription_meta_bar.xml:75-85, 88-98, 111-121` | 36×36dp |
| `btn_telegram` | `layout_subscription_meta_bar.xml:239-251` | 36×36dp |
| `btn_support` | `layout_subscription_meta_bar.xml:216-230` | `minHeight="0dp"` + `wrap_content` ⇒ ≈30dp tall |
| `btn_collapse_all`, `btn_refresh_all`, `btn_speedtest_all`, `btn_add` | `layout_servers_header.xml:29-75` | 36×36dp |
| `et_search` | `layout_servers_header.xml:88-91` | 44dp tall |
| `btn_home_add` | `activity_main.xml:161-164` | 42×42dp |
| `btn_cta_dismiss` | `layout_home_account.xml:62-73` | 40×40dp |
| `img_avatar_edit` (click handler at `AccountFragment.kt:169`) | `activity_account.xml:89-97` | 18×18dp |
| `btn_device_delete` | `item_device.xml:77-80` | 44×44dp |
| 5 × preset buttons, 6 × copy/eye buttons | `activity_local_proxy.xml:101,114,127,140,153,374,420,430,847,895,943,953` | 44dp |
| 9 × scheme buttons | `activity_url_scheme_list.xml:128,180,251,303,374,445,497,568,620` | 42×42dp |
| `section_header_root` (whole clickable row) | `item_section_header.xml:5-16` | ≈38dp (22dp glyph + 12dp/4dp padding, no `minHeight`) |

`item_section_header.xml:18-20` explicitly *claims* "the whole section_header_root row carries the
click + the ≥48dp touch target" — the layout has no `minHeight`, so the claim is false.
`layout_subscription_meta_bar.xml:30-42` gets it right (48dp box, 13dp inset), which makes the
36dp siblings in the same file an internal inconsistency.

### B3 — Emoji / text glyphs used as UI chrome

* Server-row country flag is a **regional-indicator emoji pair**, with a 🌐 globe fallback:
  `util/FlagUtil.kt:18` (`private const val GLOBE = "🌐"`), `:27-32` (`resolveFlag`), rendered into
  a TextView tile at `item_recycler_main.xml:41-50` (`android:textSize="18sp"`). This also
  contradicts the owner's "unified server icon" requirement (CLAUDE.md).
* `layout_home_account.xml:71` — `android:text="✕"` as the CTA dismiss button.
* `activity_main.xml:98` / `:140` — `android:text="↑"` / `"↓"` as the up/down speed icons.
* `activity_account.xml:74` and `layout_home_account.xml:105` — `android:text="?"` as the avatar
  monogram placeholder.

### B4 — Two competing spacing scales still coexist

`res/values/dimens.xml:3-10` keeps the legacy scale (`padding_spacing_dp4/8/16`, `image_size_dp24`,
`view_height_dp36/48/64/120/160`) alongside the Incy scale at `:14-19`
(`space_4/8/12/16/24/32`). The legacy tokens are still live in **24 layouts** — every server-editor
screen, `activity_routing_edit.xml`, `activity_sub_edit.xml`, `dialog_webdav.xml`,
`dialog_config_filter.xml`, `item_recycler_routing_setting.xml`, `item_recycler_bypass_list.xml`,
`item_recycler_proxy_chain_member.xml`, `activity_about.xml`, `activity_check_update.xml`, …
Meanwhile `view_height_dp36/48/64/120/160` and `dot_gap` (`dimens.xml:40`) are referenced by
nothing — dead tokens. Direct violation of "ONE spacing scale".

### B5 — The 16dp gutter is not one gutter

* `layout_servers_header.xml:10-12` — `paddingStart="@dimen/screen_gutter"` (16dp) but
  `paddingEnd="@dimen/space_12"` (12dp).
* `layout_subscription_meta_bar.xml:14-16` — `paddingStart="@dimen/space_16"` /
  `paddingEnd="@dimen/space_12"`.
* `layout_servers_header.xml:93` — `layout_marginEnd="4dp"` on the search field only.
* `item_recycler_main.xml:24-27` — row inner padding 12dp against a 16dp screen gutter
  (`:8`, `:10`).

### B6 — Radius tokens bypassed

`@dimen/radius_card` is 20dp and `@dimen/radius_chip`/`radius_tile` are 12dp
(`dimens.xml:22-28`), yet:
* `activity_main.xml:321` and `layout_home_empty.xml:13` — `app:cardCornerRadius="20dp"` written as
  a literal.
* `activity_main.xml:262` — `app:cardCornerRadius="88dp"` on the connect knob.
* `layout_home_empty.xml:118`, `activity_login.xml:269, 294` (and siblings) —
  `app:cornerRadius="26dp"` on buttons, a value that exists in no token.
* `activity_main.xml:250` — `app:trackCornerRadius="4dp"`.

### B7 — Glyph sizes are not the 22dp token

`@dimen/tile_glyph` is 22dp (`dimens.xml:32`) and is used correctly at
`item_section_header.xml:23-24` and in the Account row tiles (`activity_account.xml:404-405`,
`:458-459`, `:521-522`). But every chevron in the app is 18dp:
`layout_setting_row.xml:74-75`, `layout_home_account.xml:148-149`,
`activity_account.xml:423-424`, `:486-487`, `:549-550`. And the meta-bar chevron is a
48dp box with a 13dp pad (`layout_subscription_meta_bar.xml:32-39`). Three different disclosure
chevrons — the "unify expand chevrons" commit (`2fef5f0`) did not land everywhere.
Similarly the server-row flag tile is 28dp (`item_recycler_main.xml:42-43`) against
`@dimen/tile_size` 40dp.

### B8 — Synthetic bold on brand type

`values/styles.xml:53-56` states the rule explicitly: *"No `android:textStyle="bold"` on any Space
Grotesk style: the variable ttf carries genuine masters, so no synthetic bold."* Violated at:
`activity_account.xml:77` (`tv_avatar_initial`), `:126` (`btn_top_up`), `:277` (`btn_buy_first`),
`:354` (`btn_retry_load`); `layout_home_account.xml:108` (`tv_avatar_initial`);
`layout_home_empty.xml:116` (`btn_home_login_tg`); `activity_login.xml:269` (`btn_confirm_2fa`)
and its siblings.

### B9 — Type scale bypassed by inline `textSize`

`activity_main.xml:113` (13sp), `:128` (14sp), `:155` (13sp) override
`TextAppearance.App.Numeric`; `layout_subscription_meta_bar.xml:175` (11sp), `:226` (12sp);
`layout_servers_header.xml:104` (14sp); `item_recycler_main.xml:49` (18sp), `:121` (12sp);
`layout_home_account.xml:73` (16sp), `:107` (16sp); `activity_account.xml:76` (20sp).

### B10 — Off-scale spacing literals

Values not on the 4/8/12/16/24/32 scale, all in layouts the redesign owns:
`3dp` nav-label/pill margins and `34dp` pill width (`activity_main.xml:560, 570-571, 601, 610-611,
641, 650-651, 685, 690-691`); `42dp` (`:74, 163-164`); `13dp`
(`layout_subscription_meta_bar.xml:39`); `14dp` (`layout_servers_header.xml:100-101`);
`2dp` chip padding (`item_recycler_main.xml:82, 84`, `item_payment.xml:62, 91`);
`52dp` avatar container (`activity_account.xml:55-56`); `72dp` divider inset
(`activity_account.xml:434, 497`); `140/200/120/18/14dp` skeleton bars
(`activity_account.xml:218-232`); `160dp` traffic pill and `16dp` bar
(`layout_subscription_meta_bar.xml:146, 157`); `160dp` scrim (`activity_main.xml:515`);
`230/212/176/80dp` hero stack (`activity_main.xml:202-203, 247, 255-256, 270-271, 281-282`).

### B11 — Checks that pass (recorded so they are not re-litigated)

* **No nested `MaterialCardView`** anywhere — verified by a tag-depth scan of all 76 layouts.
* **No ALL-CAPS eyebrows** — `SettingsSectionLabel` (`values/styles.xml:5-17`) is 16sp/700
  sentence-case with `textAllCaps=false` and `letterSpacing=0`, and the six section strings are
  sentence-case Russian (`values/strings.xml:566-571`).
* **No `textAllCaps="true"`** anywhere; the global default is `false`
  (`values/styles.xml:11`, `values/attrs.xml:5`).
* `AccountFragment` — the only Fragment with view binding — nulls `_binding` in `onDestroyView`
  and cancels both animators (`AccountFragment.kt:122-129`); `BaseFragment` does the same
  (`BaseFragment.kt:34-37`).
* `MainActivity.onDestroy` removes all four `Handler` callbacks and cancels the status toast
  (`:2832-2840`); `AccountSession` is collected under `repeatOnLifecycle(STARTED)` (`:999-1003`).

---

## C. Suggested order of work

1. **A1** — restore the long-press → `ServerActionsSheet` wiring in `MainRecyclerAdapter.bindServer`
   (or move the actions onto a visible affordance). Nothing else in this list is user-visible-broken
   at that scale.
2. **A2**, **A3** — delete the `onKeyDown` override and serialise `showTab`'s cross-fade
   (cancel + jump-to-end on re-entry, or drive it with a single `TransitionManager` pass).
3. **A4**, **A5**, **A6** — sheet callback lifecycle, dead CTA branch, fragment detach on logout.
4. **A9**, **A15** — diff-based updates (`DiffUtil` / stable ids) for the two carousels and
   `MainRecyclerAdapter`; cache the measured meta-bar height instead of re-inflating.
5. **B1**, **B2**, **B3** — the three violations a designer will see first: kill the home gradient
   and connect glow, raise every listed target to ≥48dp, replace the flag/✕/↑↓/? glyphs with vectors.
6. **B4**–**B10** — token sweep: delete the legacy dimens, one gutter, one radius set, one 22dp
   glyph size, drop inline `textSize`/`textStyle`.
7. **A7**, **A8** — decide per feature: re-expose or delete. Shipping four unreachable Activities
   and nine dead methods is its own risk.
