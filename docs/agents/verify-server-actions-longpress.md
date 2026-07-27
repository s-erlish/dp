# Verification — "Every per-server action is unreachable: long-press callback assigned but never invoked"

**Verdict: CONFIRMED (real).** Mechanism is exactly as reported. Only the line numbers in the
report are stale (off by ~34 lines against the current working tree); the code behaves as claimed,
and the true blast radius is *larger* than the report states.

Repo: `/home/user/dp` (Android). All line references below are against the current working tree
(HEAD `5291d1e`).

---

## 1. The adapter never fires a long-press

`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainRecyclerAdapter.kt:52-56`

```kotlin
/**
 * Retained for host-activity API compatibility. The long-press server-actions menu was
 * removed, so this callback is no longer invoked by the adapter.
 */
var onItemLongClick: ((String) -> Unit)? = null
```

`MainRecyclerAdapter.kt:232-236` — the entire interaction wiring of a server row:

```kotlin
binding.infoContainer.setOnClickListener {
    adapterListener?.onSelectServer(guid)
}
// Long-press server-actions menu removed: long-press is a no-op (no listener set).
```

There is no `setOnLongClickListener` anywhere in the adapter, and no other reference to
`onItemLongClick` inside it (only the declaration at :56). Verified by grep across
`app/src/main`: the only `setOnLongClickListener` calls in the whole module are
`HomeMetaPagerAdapter.kt:64` (subscription card) and `LogcatRecyclerAdapter.kt:32` — none on a
server row.

`adapterListener` is referenced exactly twice in the file: the constructor param
(`MainRecyclerAdapter.kt:32`) and `onSelectServer` (`MainRecyclerAdapter.kt:233`). So
`MainAdapterListener.onEdit` / `onShare`
(`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/contracts/MainAdapterListener.kt:7-11`)
and the inherited `onRemove` (`contracts/BaseAdapterListener.kt`) are never invoked from a
server row either.

## 2. The host still wires the dead callback

`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt:650-652`

```kotlin
// Long-press a server row -> Incy server-actions bottom sheet (S3 moved inline actions here).
serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }
homeAdapter.onItemLongClick = { guid -> showServerActions(guid) }
```

`MainActivity.kt:651-652` are the **only** assignments and the **only** call sites of
`showServerActions` (`MainActivity.kt:660`) in the whole module (grep for `showServerActions`
returns lines 651, 652, 660, 662 only). Since the adapter never calls the property, the sheet
can never open.

Consequently dead (unreachable) in `MainActivity.kt`:

| symbol | line | reachable? |
|---|---|---|
| `showServerActions` | `MainActivity.kt:660` | only from :651/:652 → dead |
| `shareServer` | `MainActivity.kt:1359` | only from `ActivityAdapterListener.onShare` :1499/:1513 → dead |
| `showQRCode` | `MainActivity.kt:1376` | from `showServerActions` :665 and `shareServer` :1363 → dead |
| `share2Clipboard` | `MainActivity.kt:1383` | :666 / :1364 → dead |
| `shareFullContent` | `MainActivity.kt:1388` | :1365 → dead |
| `editServer` | `MainActivity.kt:1397` | :667 / :1366 / `onEdit` :1494 → dead |
| `removeServer` | `MainActivity.kt:1417` | :677 / :1367 / `onRemove` :1493 → dead |
| `removeServerSub` | `MainActivity.kt:1432` | only from `removeServer` → dead |

`ActivityAdapterListener` (`MainActivity.kt:1489-1515`) is instantiated once
(`MainActivity.kt:635`) and handed only to the two `MainRecyclerAdapter`s
(`MainActivity.kt:637`, `:643`), which call nothing but `onSelectServer`. So `onRemove`
(`:1493`), `onEdit(guid, position, profile)` (`:1494`) and `onShare` (`:1496`) are dead too.

`ServerActionsSheet` (`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/ServerActionsSheet.kt:21-63`)
and its layout `res/layout/sheet_server_actions.xml` are therefore unreachable UI.

## 3. No alternative entry point exists

Checked and ruled out, all in `app/src/main`:

- **No swipe/drag.** No `ItemTouchHelper` is attached to `binding.rvServers` or
  `binding.rvHomeServers` (`MainActivity.kt:637-648`); the adapter itself documents this and
  returns inert `onItemMove`/`onItemDismiss` (`MainRecyclerAdapter.kt:363-372`). The only
  `ItemTouchHelper(...).attachToRecyclerView` in the module is
  `ServerProxyChainActivity.kt:76` (a different list).
- **No per-row overflow button.** `res/layout/item_recycler_main.xml` contains only
  `info_container`, `layout_indicator` (0dp, `gone`), `tv_flag`, `tv_name`, `tv_type`,
  `tv_statistics`, `progress_ping`, `tv_test_result` — no share/edit/delete affordance.
- **No RecyclerView-level gesture.** No `addOnItemTouchListener` / `GestureDetector` anywhere
  in the module.
- **No second server list.** `ItemRecyclerMainBinding` / `item_recycler_main` is used only by
  `MainRecyclerAdapter.kt`, so these two lists are the only place a server row exists.
- **No toolbar/menu route.** `onCreateOptionsMenu` returns `false` (`MainActivity.kt:2060-2066`);
  the only menu is `res/menu/menu_main.xml`, four *import* items (scan QR, clipboard, TV send,
  manual), routed by `onOptionsItemSelected` (`MainActivity.kt:2068-2098`) and opened from
  `showImportMenu` (`MainActivity.kt:695`, anchored at `MainActivity.kt:293` and `:691`).

## 4. Extra findings the report missed — the impact is worse

- **Bulk deletion is dead as well.** `delAllConfig` (`MainActivity.kt:2307`),
  `delDuplicateConfig` (`MainActivity.kt:2326`) and `delInvalidConfig` (`MainActivity.kt:2345`)
  have **zero call sites** (grep across the module returns only the definitions). So not even a
  "delete all configs" escape hatch remains.
- **The only surviving destructive path is subscription-scoped**, not server-scoped:
  `confirmDeleteSubscription` (`MainActivity.kt:992`), reachable by long-pressing the Home
  subscription card (`HomeMetaPagerAdapter.kt:64`, wired at `MainActivity.kt:869`). It calls
  `MmkvManager.removeSubscription(subId)` and returns early when `subId.isEmpty()`
  (`MainActivity.kt:993`).
- **Therefore locally imported servers can never be removed at all.** Import is fully reachable
  (`showImportMenu` → QR / clipboard / manual, `MainActivity.kt:2069-2090`), but a server with an
  empty `subscriptionId` lands in the "Local" section (`MainRecyclerAdapter.kt:118-124`) and has
  no subscription card, so `confirmDeleteSubscription` cannot touch it. A user can add servers
  forever and delete none of them.
- **Contradictory intent inside one commit.** `MainRecyclerAdapter.kt:52-56` and `:235` state the
  long-press menu was *removed*; `MainActivity.kt:650`, `ServerActionsSheet.kt:13-15` and the
  header of `res/layout/sheet_server_actions.xml` all state long-press *opens* the sheet. Both
  sides were introduced by the same commit `5aba40f` (`git log -S` on both strings), so this is
  an unresolved half-refactor, not a later regression.

## 5. Corrections to the report

Substance is correct; only the citations drift. Correct anchors:

| report says | actual |
|---|---|
| MainActivity.kt:616-618 (long-press wiring) | `MainActivity.kt:650-652` |
| showServerActions :626 | `:660` |
| shareServer :1325 | `:1359` |
| showQRCode :1342 | `:1376` |
| share2Clipboard :1349 | `:1383` |
| shareFullContent :1354 | `:1388` |
| editServer :1363 | `:1397` |
| removeServer :1383 | `:1417` |

`MainRecyclerAdapter.kt:32`, `:52-56`, `:232-236`, `:233` and
`contracts/MainAdapterListener.kt:7-11` are all cited correctly.

## 6. Fix direction (one line, plus a decision)

Restore the invocation in `bindServer`, replacing the comment at `MainRecyclerAdapter.kt:235`:

```kotlin
binding.infoContainer.setOnLongClickListener {
    onItemLongClick?.invoke(guid)?.let { true } ?: false
}
```

Then delete the stale "retired" doc comment (`:52-56`). Note that long-press alone is a hidden
affordance for the app's *only* delete/edit/share route — an explicit per-row affordance (or a
Servers-tab edit mode) is the sturdier answer; `ServerActionsSheet` already handles the
locked-profile case (`ServerActionsSheet.kt:46-53`). Also decide the fate of the now-orphaned
`delAllConfig`/`delDuplicateConfig`/`delInvalidConfig` (`MainActivity.kt:2307/2326/2345`): wire
them into the Servers header menu or remove them.
