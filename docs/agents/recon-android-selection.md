# Android server-list selection — bug hunt (recon)

Scope: the two defects the owner reported on the Android app (`/home/user/dp`, fork of v2rayNG,
package `com.v2ray.ang`):

1. tapping an **already-selected** server immediately connects — it must not;
2. picking a **different** server ends with **two rows painted as selected** and an error about a
   **wrong / invalid profile**.

Everything below comes from files read in this repo. Two code states are cited, because the tree
moved while this recon ran:

| state | commit | meaning |
|---|---|---|
| **PRE** | `bfd05fd` (`Merge pull request #1 …`) | the code the owner was running when the defects were reported |
| **POST** | `7e2baf4` (`Selecting a server no longer connects; fix the switch race and double selection`, authored 2026-07-26 12:40 by a parallel agent) | current working tree / HEAD |

PRE line numbers refer to `git show bfd05fd:<path>`; POST line numbers refer to the files on disk
now. Both are given wherever it matters.

---

## 0. Verdict in one paragraph

Both defects have **one shared root cause**: *the selection is stored in exactly one place
(`MMKV["SELECTED_SERVER"]`), but it is painted from a second, unsynchronised place (whatever each
`MainRecyclerAdapter` row happened to read at its last `onBindViewHolder`) — and MMKV is written by
five code paths that own no list and cannot notify anybody.* When the painted selection and the
stored selection drift apart, (a) tapping the row that **looks** selected takes the "different
server" branch and, in PRE, unconditionally called `restartV2Ray()` → "it connected by itself";
(b) the row that was actually painted blue is never repainted, so a second row goes blue next to it.
The "wrong profile" toast is a **third**, independent consequence of the same drift: the on-screen
`serversCache` can hold GUIDs that a background subscription refresh already deleted, so the tap
stores a **dead GUID**, and `CoreServiceManager.startContextService()` fails
`decodeServerConfig(guid) == null` → `R.string.toast_config_file_invalid` = «Неправильный профиль».
`7e2baf4` fixes the tap-to-connect behaviour and most of the double-paint, but **does not fix the
dead-GUID path**, and its restart-race fix waits on a signal that the daemon emits *before* the core
has actually stopped (§5.1, §5.2).

---

## 1. The moving parts (read in full)

| file | role |
|---|---|
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainRecyclerAdapter.kt` | the only server-row adapter; used **twice** (Servers tab + Home) |
| `V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt` | owns both adapters, the selection write, connect/disconnect/restart |
| `V2rayNG/app/src/main/java/com/v2ray/ang/handler/MmkvManager.kt` | `KEY_SELECTED_SERVER` storage, profile storage, removal |
| `V2rayNG/app/src/main/java/com/v2ray/ang/viewmodel/MainViewModel.kt` | `serversCache`, `updateListAction`, fast-connect |
| `V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt` | start/stop/restart plumbing, the "invalid profile" error |
| `V2rayNG/app/src/main/java/com/v2ray/ang/service/CoreVpnService.kt` | daemon-process service (`:RunSoLibV2RayDaemon`) |
| `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt` | subscription import — regenerates every GUID |
| `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionUpdater.kt` | periodic auto-update worker, runs in the `:bg` process |
| `V2rayNG/app/src/main/res/layout/item_recycler_main.xml` | the row; `info_container` is the click target |
| `V2rayNG/app/src/main/res/drawable/bg_server_row.xml` | the selection visual (`state_selected`) |

There is **no `DiffUtil` anywhere in the app** (`grep -rn DiffUtil --include=*.kt` → no hits) and
**no stable IDs** (`MainRecyclerAdapter` never overrides `getItemId`, never calls `setHasStableIds`).
All refresh decisions are hand-rolled `notifyItemChanged(pos)` / `notifyDataSetChanged()` calls.

### 1.1 Two adapters, one MMKV key

`MainActivity.setupServerLists()` (POST `MainActivity.kt:600-618`) creates **two** independent
`MainRecyclerAdapter` instances over the *same* `mainViewModel.serversCache`:

```kotlin
serversAdapter = MainRecyclerAdapter(mainViewModel, listener)   // rvServers  (Servers tab, grouped)
homeAdapter    = MainRecyclerAdapter(mainViewModel, listener)   // rvHomeServers (Home, flat)
```

Everything that changes selection therefore has to be mirrored into two adapters by hand
(`MainActivity.kt:1417-1418`), and each adapter keeps its own row list (`rows`), its own collapse
state, and — POST only — its own `selectedGuid` mirror.

### 1.2 The row is painted from MMKV at bind time (PRE)

`MainRecyclerAdapter.bindServer`, PRE `MainRecyclerAdapter.kt:204-216`:

```kotlin
// Selection: blue rounded outline via bg_server_row selected state.
// Indicator bar tint via theme attr (mono-safe).
val selected = guid == MmkvManager.getSelectServer()
binding.infoContainer.isSelected = selected
binding.layoutIndicator.setBackgroundColor(
    if (selected) MaterialColors.getColor(binding.layoutIndicator, R.attr.indicatorColor)
    else Color.TRANSPARENT
)

binding.infoContainer.setOnClickListener {
    adapterListener?.onSelectServer(guid)
}
```

There is no adapter-side memory of *which* row was painted. A row's blue outline is therefore a
snapshot of MMKV taken at the moment that row was last bound. Nothing repaints it unless somebody
explicitly notifies that exact position.

### 1.3 Five writers of `SELECTED_SERVER` that own no list

`MmkvManager.setSelectServer` (`MmkvManager.kt:72-74`) is a bare MMKV write — no LiveData, no
broadcast, no listener. Writers:

| # | writer | file:line |
|---|---|---|
| W1 | user tap → `MainActivity.setSelectServer` | `MainActivity.kt:1416` |
| W2 | subscription import/update (link list) | `AngConfigManager.kt:384` (`matchKey?.let { MmkvManager.setSelectServer(it) }`) |
| W3 | subscription import/update (raw xray-json — the departament case) | `AngConfigManager.kt:601` |
| W4 | implicit auto-select when the key is empty | `MmkvManager.kt:169-171` inside `encodeServerConfig` |
| W5 | fast-connect / auto-fallback | `MainViewModel.kt:404` (`bestGuid?.let { MmkvManager.setSelectServer(it) }`) |
| W6 | service start with an explicit guid (Tasker) | `CoreServiceManager.kt:89-91`, called from `TaskerReceiver.kt:33` |

W2/W3/W4 run **in the `:bg` process** when they come from the periodic auto-update
(`AngApplication.kt:25-27` sets `setDefaultProcessName("${ANG_PACKAGE}:bg")`;
`SubscriptionUpdater.UpdateTask.doWork` → `AngConfigManager.updateConfigViaSub`,
`SubscriptionUpdater.kt:185`). All MMKV instances are `MMKV.MULTI_PROCESS_MODE`
(`MmkvManager.kt:35-41`), so the UI process *sees* the new value on the next read — but is never
*told*, and its `serversCache` is never invalidated.

---

## 2. Defect (1) — tapping an already-selected server connects

### 2.1 The offending code (PRE)

`MainActivity.kt:1398-1417` (PRE):

```kotlin
private fun setSelectServer(guid: String) {
    val selected = MmkvManager.getSelectServer()
    if (guid != selected) {
        MmkvManager.setSelectServer(guid)
        serversAdapter.setSelectServer(selected, guid)
        homeAdapter.setSelectServer(selected, guid)
        updateSelectedServer()
        // Surface the selected server's subscription card in the carousel.
        mainViewModel.findSubscriptionIdBySelect()?.let { selectedSubId -> … }
        if (mainViewModel.isRunning.value == true) {
            restartV2Ray()                       // <-- tears down + reconnects, no confirmation
        }
    }
}
```

Two separate faults live in these 20 lines.

**Fault 1a — the "same server?" guard compares against the wrong thing.**
`selected` is the **stored** GUID. The row the user tapped is blue because of §1.2 — a *painted*
state that can be older than the stored one. So "tap the row that is already highlighted" is not the
same predicate as `guid == selected`. Whenever the two disagree (any W2…W6 write since that row was
bound), the tap on the visibly-selected row falls into the `guid != selected` branch and runs the
full "user picked a new server" path — including `restartV2Ray()`.

**Fault 1b — selecting is wired to connecting at all.**
Even without any drift, `setSelectServer` is a list-tap handler that starts a tunnel teardown +
restart with no confirmation. `restartV2Ray()` (PRE `MainActivity.kt:1491-1499`):

```kotlin
fun restartV2Ray() {
    if (mainViewModel.isRunning.value == true) {
        CoreServiceManager.stopVService(this)
    }
    lifecycleScope.launch {
        delay(500)
        startV2Ray()
    }
}
```

Because it never sets `connectInProgress`, the stop broadcast lands in the `isRunning` observer with
`connectInProgress == false` and `prev == true`, which fires the **«Отключено»** toast
(`MainActivity.kt:571-575`), followed ~500 ms later by **«Прокси подключён»**. From the user's seat
that is exactly "I tapped a server in the list and the app disconnected and connected by itself".

### 2.2 Concrete reproduction (PRE)

1. Rows **A** and **B** are on screen; **A** is selected, painted blue (correctly, from MMKV).
2. Anything from §1.3 rewrites the key to **B** — the most common in this build is the periodic
   subscription auto-update (`:bg`), which additionally regenerates every GUID (§4).
   Nothing repaints row A, because PRE `onResume` (`MainActivity.kt:1914-1922`) only called
   `updateSelectedServer()` — no adapter refresh of any kind:
   ```kotlin
   override fun onResume() {
       super.onResume()
       updateSelectedServer()
       updateAccountGate()
       …
   }
   ```
3. The user taps **A** (still blue = "already selected"). `guid("A") != selected("B")` → MMKV = A →
   `isRunning == true` → `restartV2Ray()` → disconnect + reconnect.

### 2.3 Status after `7e2baf4` (POST)

Fixed for the *behaviour*: `MainActivity.kt:1412-1431` now returns early on an exact match and, when
the tunnel is up, offers an explicit Snackbar action instead of restarting
(`promptApplySelectedServer`, `MainActivity.kt:1437-1453`). The stale-paint predicate (Fault 1a) is
mostly closed by the adapter's `selectedGuid` mirror + `onResume` resync
(`MainActivity.kt:1978-1979`) — but see §5.3 for the case that survives, and §5.4 for a new lie the
Snackbar introduced.

---

## 3. Defect (2a) — two rows painted as selected

### 3.1 The offending code (PRE)

`MainRecyclerAdapter.kt:290-293` (PRE):

```kotlin
/** Refreshes the two rows involved in a selection change (guids). */
fun setSelectServer(fromGuid: String?, toGuid: String?) {
    fromGuid?.let { flatPositionOf(it).takeIf { p -> p >= 0 }?.let { p -> notifyItemChanged(p) } }
    toGuid?.let { flatPositionOf(it).takeIf { p -> p >= 0 }?.let { p -> notifyItemChanged(p) } }
}
```

`fromGuid` is supplied by the caller as `MmkvManager.getSelectServer()` read *before* the write
(`MainActivity.kt:1399,1402` PRE) — i.e. **the stored previous selection, not the row the adapter
actually painted blue**. The adapter had no way to know which row it painted (§1.2), so:

* if the stored previous GUID is **not in `rows`** (deleted by a subscription refresh, filtered out
  by the search box / protocol chip, or inside a collapsed section) → `flatPositionOf` returns `-1`
  → **nothing is un-painted**, and the newly tapped row is painted → **two blue rows**;
* if the stored previous GUID *is* in `rows` but is **not** the row that is actually blue (drift from
  §1.3) → the wrong row is refreshed, the blue one is left alone → **two blue rows**.

That state is permanent: only a full `notifyDataSetChanged()` or scrolling the stale row out of and
back into the window can heal it.

### 3.2 The second amplifier: single-row refreshes on the list-rebuild path

`MainRecyclerAdapter.setSections` (PRE `MainRecyclerAdapter.kt:66-79`):

```kotlin
this.servers = newServers.toList()
…
val targetGuid = if (index in this.servers.indices) this.servers[index].guid else null
rebuildRows()
val flat = targetGuid?.let { flatPositionOf(it) } ?: -1
if (flat >= 0) notifyItemChanged(flat) else notifyDataSetChanged()
```

`index >= 0` arrives on every finished ping (`MainViewModel.kt:274, 345, 369, 712`:
`updateListAction.value = getPosition(item.guid)`), routed through
`MainActivity.refreshServerLists` (`MainActivity.kt:760-763`). So during a "проверить все"
sweep the list is rebuilt dozens of times while **only one row per rebuild is re-bound**. Every one
of those rebuilds re-reads the servers, but PRE never re-read the *selection*, so a selection change
that happened in between could sit invisible for the whole sweep — and the one row that does get
re-bound reads the *new* MMKV value and paints itself blue **next to** the stale blue row. This is
the fastest way to reproduce two blue rows on a real device.

### 3.3 Status after `7e2baf4` (POST)

Largely fixed. The adapter now mirrors what it painted (`MainRecyclerAdapter.kt:142`), re-reads MMKV
on every rebuild (`:81-86`), paints from the mirror (`:225`), and falls back to a full refresh when
either row can't be resolved (`:324-339`). `MainActivity.onResume` resyncs both adapters
(`:1978-1979`). **One hole remains — §5.3.**

---

## 4. Defect (2b) — «Неправильный профиль» after switching server

### 4.1 Where that exact string comes from

`values-ru/strings.xml:134`:
```xml
<string name="toast_config_file_invalid">Неправильный профиль</string>
```

It is toasted from exactly one place reachable from a connect: `CoreServiceManager.kt:131-155`,
surfaced by the `catch` in `startVService` (`CoreServiceManager.kt:93-98`,
`context.toast(e.message …)`):

```kotlin
val config = MmkvManager.decodeServerConfig(guid)
    ?: run {
        LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to decode server config")
        error(context.getString(R.string.toast_config_file_invalid))     // line 146
    }

if (!config.configType.isComplexType()
    && !Utils.isValidUrl(config.server)
    && !Utils.isPureIpAddress(config.server.orEmpty())
) {
    error(context.getString(R.string.toast_config_file_invalid))         // line 154
}
```

For this product the second branch is **unreachable**: departament subscriptions are Remnawave
`XRAY_JSON`, parsed by `AngConfigManager.parseCustomConfigServer` into `EConfigType.CUSTOM`
profiles (`AngConfigManager.kt:562-604`), and `isComplexType()` is true for `CUSTOM`
(`extension/_Ext.kt:220-222`). **Therefore, on this app, «Неправильный профиль» can only mean
`decodeServerConfig(guid) == null` — i.e. the selected GUID no longer exists in
`PROFILE_FULL_CONFIG`.**

### 4.2 How a dead GUID gets selected

Every subscription refresh deletes and re-mints every profile of that subscription:

* `AngConfigManager.parseBatchConfig` → `MmkvManager.removeServerViaSubid(subid)`
  (`AngConfigManager.kt:380`) then new keys from `Utils.getUuid()`
  (`AngConfigManager.kt:402-421`);
* the xray-json path does the same (`AngConfigManager.kt:584-598`).

`MmkvManager.removeServerViaSubid` (`MmkvManager.kt:219-234`):

```kotlin
serverList.forEach { guid ->
    if (getSelectServer() == guid) {
        mainStorage.remove(KEY_SELECTED_SERVER)
    }
    profileFullStorage.remove(guid)
    serverAffStorage.remove(guid)
}
```

Now the chain:

1. The periodic worker runs **in `:bg`** (`AngApplication.kt:25-27`, `SubscriptionUpdater.kt:120-135,
   150-191`) — including while MainActivity is in the **foreground**.
2. Every GUID in `MmkvManager.decodeServerList(subId)` is deleted; `SELECTED_SERVER` is cleared and
   later re-pointed to a **new** GUID (`resolveSelectedKey` → `AngConfigManager.kt:384 / 601`, or
   implicitly by `encodeServerConfig`, `MmkvManager.kt:169-171`).
3. **Nothing tells the UI process.** There is no broadcast, no `WorkInfo` observer, and
   `MainActivity.onResume` never calls `mainViewModel.reloadServerList()` — all 14 of its call sites
   in `MainActivity` are explicit user actions or in-process imports: `MainActivity.kt:228, 287, 640,
   965, 1154, 1172, 1181, 2168, 2173, 2220, 2249, 2268, 2287, 2304`.
4. `mainViewModel.serversCache` — and therefore both adapters' `rows` — still hold the **old,
   deleted** `ServersCache(guid, profile)` entries (`dto/entities/ServersCache.kt` is a plain
   `data class ServersCache(val guid: String, val profile: ProfileItem)`).
5. The user taps a row → `onSelectServer(oldGuid)` (`MainRecyclerAdapter.kt:232-234`) →
   `MmkvManager.setSelectServer(oldGuid)` (`MainActivity.kt:1416`) — **a dead GUID is now the
   selection**.
6. Connect (or, PRE, the automatic `restartV2Ray()` from that same tap) →
   `CoreServiceManager.startContextService` → `decodeServerConfig(deadGuid) == null` →
   **«Неправильный профиль»**.

The same step 5 also explains the paint: the row the user tapped goes blue, while the row that was
blue before is un-painted only if its GUID resolves — and after a refresh **neither** old GUID
matches the newly stored selection, so PRE's `setSelectServer(from, to)` could not un-paint anything
(§3.1, first bullet).

### 4.3 Status after `7e2baf4` (POST)

**Not fixed.** `7e2baf4` changed only *when* a connect is triggered and *how* rows are repainted. The
stale `serversCache` full of deleted GUIDs is untouched: nothing invalidates the list when the `:bg`
worker finishes, and `onResume` still does not reload it (POST `MainActivity.kt:1972-1988` adds
`syncSelection()` but no `reloadServerList()`). Tap a row after a background refresh and the app
still stores a dead GUID and still toasts «Неправильный профиль» on the next connect.

---

## 5. Defects still open in the current tree (POST `7e2baf4`)

### 5.1 `restartV2Ray()` waits on a signal that lies — the switch race is NOT closed  ·  HIGH

POST `MainActivity.kt:1538-1557` polls `mainViewModel.isRunning` until it is false, then starts.
But `isRunning == false` is produced by `MSG_STATE_STOP_SUCCESS`, which the daemon sends **before the
core has stopped** — `CoreServiceManager.stopCoreLoop()` (`CoreServiceManager.kt:293-313`):

```kotlin
if (coreController.isRunning) {
    CoroutineScope(Dispatchers.IO).launch {          // <-- fire and forget
        try { coreController.stopLoop() } catch (e: Exception) { … }
    }
}
…
MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")   // <-- sent immediately
```

So the new loop can fall through in **one 50 ms poll**, i.e. *sooner* than the old fixed
`delay(500)`. The race the commit message claims to fix can therefore still happen, and can happen
**more** often than before. What follows differs by mode:

* **VPN mode** — the new `startForegroundService` re-enters `CoreVpnService.onStartCommand`
  (`CoreVpnService.kt:115-138`) → `setupVpnService()` re-establishes the tun → `startService()` →
  `CoreServiceManager.startCoreLoop` returns `false` because `coreController.isRunning` is still true
  (`CoreServiceManager.kt:202-206`) → `stopAllService()` (`CoreVpnService.kt:160-164`) → the tunnel
  is torn down entirely and the UI ends at «Не удалось подключиться».
* **Proxy-only mode** — `CoreProxyOnlyService.onStartCommand` calls
  `CoreServiceManager.startCoreLoop(null)` and **ignores the return value**
  (`CoreProxyOnlyService.kt`, `onStartCommand`), so the old core keeps running the **previous
  server** while the UI reports the new one. This is the actual "silent switch" bug.

Note also that the commit message's premise ("startContextService() would see
`coreController.isRunning` and return silently") is wrong about *which* process: the guard at
`CoreServiceManager.kt:132-135` runs in the **UI** process, where `coreController` never runs a loop,
so it is always false there. The real guard is the daemon-side one at `:202-206`.

**Fix direction:** move `MessageUtil.sendMsg2UI(…, MSG_STATE_STOP_SUCCESS, …)` inside the coroutine
that awaits `coreController.stopLoop()`, or have `startCoreLoop` await `!coreController.isRunning`
with a bounded wait instead of bailing out; and make `CoreProxyOnlyService` honour the boolean.

### 5.2 A subscription auto-update silently invalidates the on-screen list  ·  HIGH

Root cause of §4. Nothing in the UI process learns that the `:bg` worker replaced the profiles.
Minimum fix: broadcast/`WorkInfo`-observe completion and call `mainViewModel.reloadServerList()`, and
defensively re-validate in `MainActivity.setSelectServer`:

```kotlin
if (MmkvManager.decodeServerConfig(guid) == null) { mainViewModel.reloadServerList(); toast(…); return }
```

Also worth doing on `onResume` — the current `syncSelection()` there fixes the *paint* but keeps the
dead GUIDs in the list.

### 5.3 `syncSelection` still trusts the caller's `previous` instead of its own mirror  ·  MEDIUM

POST `MainRecyclerAdapter.kt:324-339`:

```kotlin
fun syncSelection(guid: String? = MmkvManager.getSelectServer(), previous: String? = selectedGuid) {
    if (guid == selectedGuid && previous == selectedGuid) return
    selectedGuid = guid
    val fromPos = previous?.let { flatPositionOf(it) } ?: -1
    val toPos   = guid?.let { flatPositionOf(it) } ?: -1
    val fromResolved = previous == null || fromPos >= 0
    val toResolved   = guid == null || toPos >= 0
    if (!fromResolved || !toResolved) { notifyDataSetChanged(); return }
    if (fromPos >= 0) notifyItemChanged(fromPos)
    if (toPos >= 0 && toPos != fromPos) notifyItemChanged(toPos)
}
```

`MainActivity` still passes the **stored** previous (`MainActivity.kt:1413,1417-1418`), not the
adapter's mirror. If the stored previous is a *different but still resolvable* row than the one the
adapter actually painted (mirror `selectedGuid` = A, caller `previous` = C, both on screen), the
guard passes, C and B are refreshed, and **A stays blue** → two blue rows again. The mirror exists
precisely to prevent this; it just isn't used on the un-paint side. Fix: repaint the union
`{previous, selectedGuid_before}`:

```kotlin
val stale = selectedGuid                      // what this adapter actually painted
…
listOfNotNull(previous, stale, guid).distinct().forEach { g ->
    flatPositionOf(g).takeIf { it >= 0 }?.let(::notifyItemChanged) ?: run { needsFull = true }
}
```

### 5.4 The Home hero now claims you are connected to a server you are not  ·  MEDIUM (new in POST)

`applyConnectedState` paints the under-shield label from the **selected** server:

`MainActivity.kt:1610-1615`
```kotlin
binding.tvConnectionStatus.setTextColor(connected)
binding.cardConnect.contentDescription = getString(R.string.action_stop_service)
binding.tvConnectionStatus.text = selectedServerName()
```
and `selectedServerName()` (`MainActivity.kt:1885-1889`) is `MmkvManager.getSelectServer()` →
`remarks`. PRE this was always true, because changing the selection while running always restarted
onto it. POST, the user can decline the "Переподключиться" Snackbar; the tunnel keeps running server
A while the selection is B. The label does not change at that instant (`updateSelectedServer()`
returns early while running, `:1901-1906`), but any later `applyRunningState(false, true)` — a
rotation, a theme/language recreate, the LiveData replay — repaints it as **B**. The UI then lies
about which exit node is live. `CoreServiceManager.getRunningServerName()` exists
(`CoreServiceManager.kt:120`) but lives in the daemon process and is never sent to the UI; the
running server's name needs to travel with `MSG_STATE_RUNNING` / `MSG_STATE_START_SUCCESS`.

### 5.5 The long-press server-actions menu is dead code  ·  MEDIUM

`MainActivity.kt:616-618` wires it:

```kotlin
// Long-press a server row -> Incy server-actions bottom sheet (S3 moved inline actions here).
serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }
homeAdapter.onItemLongClick = { guid -> showServerActions(guid) }
```

but the adapter never invokes it — `MainRecyclerAdapter.kt:52-56` says so itself, and `bindServer`
sets no long-click listener (`:232-235`, "Long-press server-actions menu removed: long-press is a
no-op (no listener set)"). Consequences: `ServerActionsSheet` (`ui/ServerActionsSheet.kt`),
`showServerActions` (`MainActivity.kt:626-645`), `removeServer`/`editServer`/`showQRCode`/
`share2Clipboard` are unreachable from the list — **there is no way to delete a single server, edit
one, share it, or "set as default" from the UI**. `MainActivity.removeServer`'s selected-server guard
(`:1383-1387`) is likewise unreachable.

### 5.6 `updateListAction` carries a **position**, not a GUID  ·  MEDIUM (position vs stable-id)

`MainViewModel` posts `updateListAction.value = getPosition(item.guid)` from ping callbacks
(`MainViewModel.kt:274, 345, 369`) and from `MSG_MEASURE_CONFIG_SUCCESS` (`:712`). The value is an
index into `serversCache` captured on a background thread and consumed later on the main thread
(`MainActivity.kt:526` → `refreshServerLists(index)` → `MainRecyclerAdapter.setSections(…, index)`
→ `servers[index].guid`, `:75`). Between capture and consumption the cache can be rebuilt by
`filterConfig` / `applyProtocolFilter` / `reloadServerList` (`MainViewModel.kt:589-605, 110-119`),
so `index` can resolve to a **different server** — the wrong row is refreshed and the right one is
not. `updateListAction` should carry the GUID. Related: it is a `MutableLiveData` used as an event,
so its last index is **replayed** on every Activity recreate, re-running `refreshServerLists` with a
stale index (contrast `fastConnectAction`, which is explicitly one-shot via
`consumeFastConnectEvent()`, `MainViewModel.kt:78-82`).

### 5.7 Focus and selection are visually the same state  ·  MEDIUM (D-pad / TV / hardware keyboard)

`res/drawable/bg_server_row.xml` paints `state_focused` with `#1F4C8DFF` fill + 2 dp blue stroke and
`state_selected` with the same `#1F4C8DFF` fill + 1.5 dp blue stroke. On any D-pad/keyboard
navigation (the app ships TV entry points — `tv/TvReceiveActivity`, `FEATURE_LEANBACK` check at
`MainActivity.kt:2411`) the focused row and the selected row are indistinguishable, which *is*
"two servers selected" to the eye, with no state bug at all. The row is `android:focusable="true"`
(`item_recycler_main.xml:19-20`), so this is reachable. Selection and focus need different visual
languages (e.g. selection = fill + check/indicator, focus = outline only).

### 5.8 Raw configs are never deleted — unbounded MMKV growth  ·  LOW (adjacent, same code path)

`MmkvManager.encodeServerRaw` writes into `ID_SERVER_RAW` (`MmkvManager.kt:329-331`) for every
CUSTOM/xray-json profile (`AngConfigManager.kt:595`, `:619`), but **neither** `removeServer`
(`:192-212`) **nor** `removeServerViaSubid` (`:219-234`) removes anything from `serverRawStorage` —
they only clear `profileFullStorage` and `serverAffStorage`. Every subscription refresh of a
departament (XRAY_JSON) subscription therefore orphans one full xray JSON blob per server, forever.

### 5.9 `removeServerViaSubid` can leave the app with **no** selection  ·  LOW

`MmkvManager.kt:225-227` clears `KEY_SELECTED_SERVER` when the selected server belongs to the
subscription being replaced. It is re-pointed afterwards only when `resolveSelectedKey` returns
non-null (`AngConfigManager.kt:503-528`) — which it does not when `keyToProfile.isEmpty()` (a refresh
that yields zero parsable configs, `:509`) or in `append` mode (`:517`). The user is then left with
no selection at all; the connect button toasts `R.string.title_file_chooser` («Выберите профиль»,
`MainActivity.kt:1520-1523`) with no explanation of what happened.

### 5.10 Dead / misleading API surface in the adapter  ·  LOW

* `removeServerSub(guid, position)` (`MainRecyclerAdapter.kt:302-306`) ignores `position` entirely
  and always does `notifyDataSetChanged()`.
* `bindServer(holder, position, cache)` never uses `position` (`:196`).
* `onItemMove` / `onItemMoveCompleted` / `onItemDismiss` (`:365-372`) are inert (no
  `ItemTouchHelper` is attached), while `MainViewModel.swapServer` (`:139-148`) still exists and
  would corrupt ordering if ever wired, since it swaps by position in `serverList`/`serversCache`
  which are only aligned when no filter is active.
* `customProtoCache` (`:277`) is keyed by GUID and never evicted; because every subscription refresh
  mints new GUIDs, the map grows for the lifetime of the Activity.

---

## 6. Recommended fix order

1. **§5.2 + §4** — invalidate `serversCache` when the `:bg` worker finishes, and hard-guard
   `setSelectServer` against a GUID that no longer decodes. This is the only fix that actually
   removes «Неправильный профиль».
2. **§5.1** — send `MSG_STATE_STOP_SUCCESS` only after `coreController.stopLoop()` returns, and make
   `CoreProxyOnlyService` honour `startCoreLoop`'s result.
3. **§5.3** — un-paint from the adapter's own mirror, not the caller's `previous`.
4. **§5.4** — ship the *running* server name to the UI and label the hero from it.
5. **§5.5** — restore the long-press entry point (or move the per-server actions somewhere reachable).
6. **§5.6** — make `updateListAction` carry a GUID and make it a one-shot event.
7. **§5.7 / §5.8 / §5.9 / §5.10** — polish, storage hygiene, dead-code removal.

---

## 7. Appendix — index of every line quoted

**PRE (`bfd05fd`)**
`MainActivity.kt:1398-1417` selection+restart · `:1483-1499` startV2Ray/restartV2Ray ·
`:1914-1922` onResume · `MainRecyclerAdapter.kt:56` dead `onItemLongClick` ·
`:66-79` setSections · `:204-216` bindServer paint+click · `:290-293` setSelectServer.

**POST (`7e2baf4`, current tree)**
`MainActivity.kt:196-207` restart constants · `:272-275` connect button · `:526` updateListAction ·
`:531-549` fastConnect observer · `:550-579` isRunning observer/toasts · `:600-618` two adapters +
dead long-press wiring · `:626-645` showServerActions · `:686-710` home empty state ·
`:751-754` markAllServersTesting · `:760-778` refreshServerLists · `:1152-1157` onLoggedIn reload ·
`:1175-1193` refreshHomeSub · `:1383-1403` removeServer/removeServerSub · `:1405-1431` setSelectServer ·
`:1437-1453` promptApplySelectedServer · `:1455-1481` ActivityAdapterListener · `:1483-1501` handleFabAction ·
`:1519-1525` startV2Ray · `:1527-1557` restartV2Ray · `:1610-1615` applyConnectedState label ·
`:1885-1906` selectedServerName/idleStatusText/updateSelectedServer · `:1972-1988` onResume ·
`:2200-2226` importConfigViaSub · `:2339-2359` locateSelectedServer · `:2411` LEANBACK check ·
`:2479-2482` restartIfRunning.

`MainRecyclerAdapter.kt:40-50` Row model · `:52-56` dead `onItemLongClick` · `:65-87` setSections ·
`:95-127` rebuildRows · `:132-133` flatPositionOf · `:135-142` selectedGuid mirror ·
`:144-153` toggleCollapseAll · `:155-163` item count/type (footer at `rows.size`) ·
`:196-236` bindServer (paint `:225`, click `:232-234`) · `:277-298` customProtoCache ·
`:300-306` removeServerSub · `:308-311` setSelectServer · `:313-339` syncSelection ·
`:341-342` positionOfGuid · `:363-372` inert drag callbacks.

`MmkvManager.kt:35-41` MULTI_PROCESS_MODE storages · `:63-74` get/setSelectServer ·
`:98-130` decodeServerList/decodeAllServerList · `:139-148` decodeServerConfig ·
`:155-175` encodeServerConfig (implicit auto-select `:169-171`) · `:183-185` encodeProfileDirect ·
`:192-212` removeServer · `:219-234` removeServerViaSubid · `:242-266` affiliation/test delay ·
`:329-341` encodeServerRaw/decodeServerRaw.

`MainViewModel.kt:48-72` state · `:110-119` reloadServerList · `:125-132` removeServer ·
`:139-148` swapServer · `:153-184` updateCache · `:262-278` testAllTcping · `:328-374` http/icmp tests ·
`:381-406` fastConnect/selectFastestServer (`:404` MMKV write) · `:459-465` getPosition ·
`:589-605` filterConfig/applyProtocolFilter · `:624-639` getProviderGroups/findSubscriptionIdBySelect ·
`:641-663` onTestsFinished · `:665-729` broadcast receiver (`:712` positional update).

`CoreServiceManager.kt:66-79` startVServiceFromToggle · `:86-99` startVService (`:89-91` MMKV write,
`:95-98` the toast) · `:105-108` stopVService · `:131-155` startContextService (the «Неправильный
профиль» errors at `:146` and `:154`) · `:202-224` startCoreLoop (silent `false` at `:203-206`) ·
`:226-286` doStartCoreLoop · `:293-323` stopCoreLoop (async stop + premature STOP_SUCCESS) ·
`:497-531` daemon message handler (`:521-526` MSG_STATE_RESTART with `Thread.sleep(500)`).

`CoreVpnService.kt:115-138` onStartCommand · `:155-165` startService (teardown on
`startCoreLoop == false`) · `:186-238` setupVpnService/configureVpnService · `:376-421` stopAllService.
`CoreProxyOnlyService.kt` `onStartCommand` — ignores `startCoreLoop`'s result.

`AngConfigManager.kt:284-312` dedupServersViaSubid · `:355-392` parseBatchConfig (`:380` wipe,
`:384` MMKV write) · `:402-422` batchSaveConfigs (new UUIDs) · `:436-481` findMatchedProfileKey ·
`:503-528` resolveSelectedKey · `:533-540` getRemovedSelectedProfile · `:562-620` parseCustomConfigServer
(`:585` wipe, `:594-595` new key + raw, `:601` MMKV write) · `:696-706` updateConfigViaSubAll ·
`:734-853` updateConfigViaSub · `:863-872` parseConfigViaSub.

`SubscriptionUpdater.kt:36-59` sync · `:90-142` scheduleOne · `:150-191` UpdateTask (`:185`
updateConfigViaSub). `AngApplication.kt:25-27, 34-38` WorkManager `:bg` process + MMKV init.
`SubscriptionSyncManager.kt:32-73` importAll (login-time re-import).
`extension/_Ext.kt:211-222` isGroupType/isComplexType. `util/Utils.kt:203-205, 265-276`
isPureIpAddress/isValidUrl. `values-ru/strings.xml:134` «Неправильный профиль».
`item_recycler_main.xml:14-28` clickable `info_container` (`:19-20` focusable).
`bg_server_row.xml` focused vs selected states. `dto/entities/ServersCache.kt` guid+profile pair.
`contracts/MainAdapterListener.kt:9` `onSelectServer`.
