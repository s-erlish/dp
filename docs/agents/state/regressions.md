# Regression hunt — what the recent waves broke

Read-only audit. Every claim below was checked against source, not against a document.

## Scope and how to read line numbers

- **Android** `/home/user/dp`, branch `claude/app-audit-agents-hyyftk`, HEAD `b0fd267`.
- **PC** `/home/user/v2rayN`, same branch, HEAD `ccbec27`.

Android line numbers are quoted **against committed HEAD**, not the working tree. While this audit
ran, another agent was actively editing `MainActivity.kt`, `BaseFragment.kt`, `MainViewModel.kt`,
`AngConfigManager.kt`, `SettingsManager.kt`, `activity_main.xml` and adding `ServersFragment.kt` /
`fragment_servers.xml` / `EditorActionsSheet.kt` (the "shell split" — moving tabs from sibling view
groups into fragments). Mid-edit I observed `ServersFragment` calling nine `MainHost` members while
the interface declared five; by the end of the audit the interface had caught up. **That work is
in flight and is not assessed here** — HEAD is the only stable baseline. Anyone reading this after
those files land must re-verify sections 1 and 3 against the new code.

---

## 1. The six load-bearing behaviours — ALL SIX STILL HOLD

I read the implementing code for each. No wave broke any of them.

### 1.1 Tapping a server SELECTS and never connects — HOLDS

`ui/MainActivity.kt:1526` `setSelectServer` writes the selection, repaints both adapters, and stops:

```kotlin
private fun setSelectServer(guid: String) {
    val selected = MmkvManager.getSelectServer()
    if (guid == selected) return
    MmkvManager.setSelectServer(guid)
    serversAdapter.setSelectServer(selected, guid)
    homeAdapter.setSelectServer(selected, guid)
    updateSelectedServer()
    ...
    if (mainViewModel.isRunning.value == true) {
        promptApplySelectedServer(guid)          // :1543
    }
}
```

`promptApplySelectedServer` (`:1551`) is a Snackbar with an explicit action; declining leaves the
running tunnel untouched. The only path from a row tap is
`MainRecyclerAdapter.bindServer` → `adapterListener?.onSelectServer(guid)`
(`ui/MainRecyclerAdapter.kt:245-247`) → `ActivityAdapterListener.onSelectServer`
(`MainActivity.kt:1575`) → `setSelectServer`. No connect call anywhere on that path.

### 1.2 `stopCoreLoop()` announces the stop only after `stopLoop()` returns — HOLDS

`core/CoreServiceManager.kt:293-314`:

```kotlin
if (coreController.isRunning) {
    CoroutineScope(Dispatchers.IO).launch {
        try { coreController.stopLoop() } catch (e: Exception) { ... }
        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
    }
} else {
    MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
}
```

`CoreProxyOnlyService.onStartCommand` honours the start result
(`service/CoreProxyOnlyService.kt:36-42`):

```kotlin
if (!CoreServiceManager.startCoreLoop(null)) {
    LogUtil.e(AppConfig.TAG, "StartCore-Proxy: Failed to start core loop")
    stopSelf()
    return START_NOT_STICKY
}
```

and `startCoreLoop` still returns `false` on `coreController.isRunning`
(`CoreServiceManager.kt:202-205`).

### 1.3 Adapter mirrors the selected guid, re-reads on rebuild, falls back to full refresh — HOLDS

All three parts present in `ui/MainRecyclerAdapter.kt`:

- mirror: `private var selectedGuid: String? = MmkvManager.getSelectServer()` (`:150`), and
  `bindServer` paints from it, not from MMKV: `val selected = guid == selectedGuid` (`:238`).
- re-read on rebuild, in `setSections` (`:88-94`):
  ```kotlin
  val latestSelection = MmkvManager.getSelectServer()
  val selectionChanged = latestSelection != selectedGuid
  selectedGuid = latestSelection
  val flat = targetGuid?.let { flatPositionOf(it) } ?: -1
  if (flat >= 0 && !selectionChanged) notifyItemChanged(flat) else notifyDataSetChanged()
  ```
- full-refresh fallback, in `syncSelection` (`:341-348`):
  ```kotlin
  val fromResolved = previous == null || fromPos >= 0
  val toResolved   = guid == null || toPos >= 0
  if (!fromResolved || !toResolved) { notifyDataSetChanged(); return }
  ```

### 1.4 `getProxyOutbound()` resolves through routing/balancers; speedtest promotes the same one — HOLDS

`dto/V2rayConfig.kt:520-521` — `resolveRoutedOutbound() ?: firstProxyOutbound()`. The resolver
(`:541-570`) walks `routing.rules` in core order, skips rules narrowed by ip/domain/port/source/
user/inboundTag/process/protocol/attrs (`matchesGenericTraffic`, `:580-592`), refuses a
freedom/blackhole target (`if (!direct.isProxyProtocol()) continue`, `:553`), and resolves a
`balancerTag` through `balancer.selector` prefixes with a `fallbackTag` backstop (`:559-567`).

The speedtest builder promotes that same outbound —
`core/CoreConfigManager.kt:214-224` calls `effectiveOutboundTag(raw)`, which is
`…getProxyOutbound()?.tag` (`:262`), finds its index, and only falls back to
"first proxy-protocol outbound" when routing names nothing (`:225-229`).

### 1.5 Subscription User-Agent precedence, and non-ASCII cannot reach the builder — HOLDS

Precedence, resolved at the single fetch point `handler/AngConfigManager.kt:891-893`:

```kotlin
val userAgent = it.subscription.userAgent?.trim()?.ifBlank { null }   // per-subscription
    ?: SettingsManager.getSubscriptionUserAgent()                     // provider override
    ?: BackendConfig.subscriptionUserAgent                            // operator/build default
```

The provider override has both halves: written at
`ui/ProviderSettingsActivity.kt:244` (`encodeSettings(AppConfig.PREF_SUB_USER_AGENT, …)`), read at
`handler/SettingsManager.kt:415-417`.

Header safety is enforced at the request builder, not only at the editors —
`util/HttpUtil.kt:60-62`:

```kotlin
fun resolveSubscriptionUserAgent(userAgent: String?): String =
    userAgent?.trim()?.takeIf { it.isNotEmpty() && isHeaderSafe(it) }
        ?: DEFAULT_SUBSCRIPTION_USER_AGENT
```

applied on both fetch paths (`HttpUtil.kt:220` and `:289`), with `isHeaderSafe` = printable ASCII
plus tab (`:71`). Both editors now also reject on entry (`SubEditActivity.kt:148-153`,
`ProviderSettingsActivity.kt:253-257`), so the fallback is a backstop rather than the only guard.

### 1.6 Auto-fallback requires a confirming re-probe — HOLDS

`ui/MainActivity.kt:623-643`. A first negative probe only re-arms:

```kotlin
if (!healthCheckConfirming) {
    healthCheckConfirming = true
    timerHandler.postDelayed(healthRecheckRunnable, HEALTH_CHECK_RECHECK_MS)   // 2000L, :219
    return@observe
}
healthCheckConfirming = false
mainViewModel.autoFallbackUsed = true
mainViewModel.fallbackInProgress = true
```

A positive probe clears the confirming flag (`:629-631`). The re-probe re-checks the same
preconditions (`:187-192`), and the flag + callback are cleared on every exit path (`:1603-1605`,
`:2070-2071`, `:2086-2088`, `:3325`).

---

## 2. Newly introduced defects — ranked

### R1 (PC, high). The TUN toggle can be turned on but not off in a downgraded session

**`v2rayN/ServiceLib/ViewModels/StatusBarViewModel.cs:511-514`**, changed by `8778233`:

```diff
-        if (_config.TunModeItem.EnableTun == EnableTun)
+        if (_config.TunModeItem.EnableTunEffective == EnableTun)
             return;
```

`EnableTunEffective => EnableTun && !TunUnavailable` (`Models/Configs/ConfigItems.cs:203`).

Failure trace, non-admin Windows or any Linux/macOS launch (`AllowEnableTun()` false because
`LinuxSudoPwd` is empty this early — the ctor says so at `:150-155`):

1. ctor: `TunUnavailable = true`, `EnableTun` (the VM/toggle) is set to `EnableTunEffective` =
   **false** (`:155-156`). Persisted intent `_config.TunModeItem.EnableTun` may be **true**.
2. User switches the toggle **on** → `DoEnableTun`: guard compares `false == true`, proceeds,
   sets `_config.EnableTun = true`, `TunUnavailable = true` (`:520-522`), prompts/relaunches or
   returns with the session downgraded.
3. User switches the toggle **off** → `DoEnableTun`: `EnableTunEffective` is
   `true && !true` = **false**, and the VM `EnableTun` is now **false**. `false == false` →
   **early return**. Nothing is written.

Result: `_config.TunModeItem.EnableTun` is stuck at `true`, `_tunRequested` stays `true`, and
`TunRequestedButUnavailable` (`:563`) keeps the Home "TUN unavailable" banner up — including on
every subsequent launch, because the ctor re-reads the persisted `true` into `_tunRequested`
(`:147`). The user has no way to withdraw the request from the status bar. Before this commit the
old guard (`EnableTun == EnableTun`, i.e. `true == false`) let the write through and the toggle
worked.

`SettingsViewModel.SetTunMode` (`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:348-352`) uses a
**different** guard — `EnableTun == enable && EnableTunEffective == enable` — which does not trip,
so the Settings row can still turn it off. Two surfaces for one setting now disagree about whether
turning it off is possible.

### R2 (Android, high). Six settings the app still reads lost their only editor

The settings waves rewrote `res/xml/pref_settings.xml` (`202a2b5`, −283/+221 lines) and removed 36
preference keys. Its header comment (`pref_settings.xml:14-29`) justifies each removal as either
"dead key nobody reads" or "already editable on the settings tab / local proxy screen". I checked
every one of the 36 for a write site and a read site.

The "moved elsewhere" claim is **true** for 17 of them (`PREF_MODE`, `PREF_VPN_DNS`,
`PREF_VPN_BYPASS_LAN`, `PREF_IPV6_ENABLED`, `PREF_MUX_ENABLED`, `PREF_MUX_CONCURRENCY`,
`PREF_FRAGMENT_ENABLED`, `PREF_UI_MODE_NIGHT`, `PREF_COLOR_THEME`, `PREF_LANGUAGE`,
`PREF_REMOTE_DNS`, `PREF_PROXY_SHARING`, `PREF_PING_METHOD`, and the four SOCKS keys +
`PREF_ENABLE_LOCAL_PROXY` in `LocalProxyActivity`). The "dead key" claim is **true** for
`PREF_DOUBLE_COLUMN_DISPLAY`, `PREF_START_SCAN_IMMEDIATE` and `PREF_LOCAL_DNS_PORT`.

But these now have **a reader and no writer anywhere in the app** — the value is frozen at whatever
MMKV happens to hold, forever:

| Key | Readers | Effect |
|---|---|---|
| `PREF_CONFIRM_REMOVE` | `MainActivity.kt:1584`, `ServerActivity.kt:669`, `ServerProxyChainActivity.kt:141`, `SubEditActivity.kt:222`, `SubSettingActivity.kt:119` | Delete-confirmation is stuck. Read via `decodeSettingsBool(key)` with **no default**, i.e. `false` — so a fresh install deletes servers and subscriptions with no confirmation and cannot enable it; an existing user who had it on can never turn it off. |
| `PREF_SHOW_MEMORY` | `MainActivity.kt:1999` | The Home memory card can never be shown. `updateMemoryCard` reads it with default `false` and returns early; the 2 s `memoryRunnable` (`:207-211`) still re-posts forever while resumed, doing nothing but re-hiding a hidden card. `MemoryStatsManager`, `card_memory`/`tv_memory`/`dot_memory` in the layout, and `memory_*` strings are all now dead weight. |
| `PREF_PREFER_IPV6` | `CoreOutboundBuilder.kt:682`, `CoreConfigManager.kt:1002` | DNS resolution preference frozen at `false`. |
| `PREF_GROUP_ALL_DISPLAY` | `MainViewModel.kt:558` | The "all groups" pseudo-group can never be enabled. |
| `PREF_USE_HEV_TUNNEL` | `SettingsManager.kt:660` | Frozen at `true`. |
| `PREF_HEV_TUNNEL_LOGLEVEL` | `TProxyService.kt:91` | Frozen at `"warn"`. |
| `PREF_MUX_XUDP_QUIC` | `CoreOutboundBuilder.kt:66` | Frozen at `"reject"`. |
| `PREF_MUX_XUDP_CONCURRENCY` | `CoreOutboundBuilder.kt:65` | Frozen at the `ensureDefaultValue` seed. |
| `PREF_DYNAMIC_SOCKS_PORT` | `SettingsManager.kt:491` | Frozen at `false`. |
| `PREF_IP_API_URL`, `PREF_HEV_TUNNEL_RW_TIMEOUT` | `SpeedtestManager.kt:346`, `TProxyService.kt:81` | Only `SettingsManager.ensureDefaultValue` writes them; no user path. |

The last seven are arguably deliberate ("the app decides"), and the header comment says so. The
first two are not defensible on that reading: `PREF_CONFIRM_REMOVE` is a destructive-action safety
gate whose effective default is now *off*, and `PREF_SHOW_MEMORY` is a feature whose entire
implementation survives with no way to switch it on.

### R3 (Android, high). The screen those settings live on has no entry point at all

`SettingsActivity` — 599 lines of Kotlin, a 500-line `res/xml/pref_settings.xml`, `activity_settings.xml`,
and 212 lines of new strings in `strings_settings_advanced.xml`, all rewritten by `5736224` and
`13831ba` — **is never started**. The only occurrences of `SettingsActivity::class.java` in the
whole tree are inside `SettingsActivity.kt` itself:

```kotlin
// SettingsActivity.kt:75-81
/**
 * Готовый intent на экран целиком или на одну его группу. Вкладка настроек
 * зовёт его так: `startActivity(SettingsActivity.newIntent(this))`.
 */
@JvmStatic
fun newIntent(context: Context, section: String = SECTION_ADVANCED): Intent =
    Intent(context, SettingsActivity::class.java).putExtra(EXTRA_SECTION, section)
```

`newIntent` has **zero call sites**. The KDoc describes a caller that does not exist. The manifest
entry is `android:exported="false"` (`AndroidManifest.xml:88-90`), so nothing outside can launch it
either. The `EXTRA_SECTION` deep routes (`settings/dns`, `settings/fragment`, `settings/latency`)
are implemented (`:86`, `:185-190`) and unreachable for the same reason.

**Attribution, honestly:** the launch was removed earlier, in `5aba40f`, when the drawer became the
in-tab Settings surface — so R3 is not itself a wave regression. What the waves did is rebuild and
re-document an unreachable screen at length while *simultaneously* stripping the settings it hosts
of their last editor (R2). The two together are the regression: the net effect of the settings work
is that reachable settings got prettier and unreachable ones got emptier. Adding one row
(`R.string.adv_entry_sub` already exists, added by `13831ba`) to the Settings tab would fix R3 and
neutralise most of R2 at once.

### R4 (PC, medium). `PortInvalid` is written and never read

`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:76` declares `[Reactive] public bool PortInvalid`,
`:410` sets it, and **nothing reads it** — no `.axaml` binding, no code-behind. Its own XML doc
admits the gap: *"the inline caption that renders it is a markup change and is not part of this
pass."* The commit (`e6e6a91`) is explicitly a fix for a dropped error message
(*"the engine VM enqueued a real message for exactly this input … dropping it was a regression"*),
and the replacement message is dropped again, one layer further in.

Partial mitigation exists and does work: `SettingsView.axaml.cs:246-252` uses the new `bool` return
to keep the panel open and refocus the field. So the user sees the port snap back with the panel
still open — but never learns *why*. This is the project's chronic write-with-no-reader defect,
introduced fresh.

### R5 (PC, low). A startup window where the connect shield is a silent no-op

`v2rayN.Desktop/ViewModels/HomeViewModel.cs:207-210`, added by `e6e6a91`:

```csharp
if (!HasServers)
{
    return;
}
```

`HasServers` is derived at `:605`:

```csharp
var loaded = Profiles?.HasLoadedServers == true;
HasServers = loaded ? count > 0 : _storedServersAtLaunch == true;
IsEmpty    = loaded ? count == 0 : _storedServersAtLaunch == false;
```

When the launch snapshot is **null** ("unknown"), both flags are deliberately false — the comment at
`:596-602` explains that this is to avoid asserting anything before the DB load lands. In that
window the shield now swallows the tap entirely: no connect, no spinner, no message, and neither the
server list nor the empty state on screen. Before the guard the tap fired `Connect()`. Short-lived
and low-severity, but it is a new dead tap where there was none.

### R6 (both, low). Copy leaks and unbounded artefacts

- `v2rayN/ServiceLib/ViewModels/StatusBarViewModel.cs:561` hard-codes Russian in shared ServiceLib:
  `RoutingModeDisplay = tunActive ? "Весь трафик · TUN" : "Через системный прокси";`
  It is bound directly into `HomeView.axaml:61` and `CompactHomeView.axaml`, both of which localise
  everything else through `{loc:T …}`. The English UI will show these two strings in Russian.
- `viewmodel/MainViewModel.kt:166-173` — `onCleared` calls `cancelMeasurementsInFlight()`, which
  does `tcpingTestScope.coroutineContext[Job]?.cancelChildren()`. The `SupervisorJob` added by
  `2403118` is never itself cancelled. Harmless in practice (the scope holds no Context and the VM
  is activity-scoped), but it is an unclosed scope, not a closed one.
- `viewmodel/AccountViewModel.kt:463` — `viewModelScope.async(Dispatchers.IO + NonCancellable)`
  detaches the wipe deliberately, and the KDoc asserts "nothing inside can throw (every step is
  wrapped)". `val app = AngApplication.application` (`:466`) is the one statement *not* wrapped in
  `runCatching`; if it ever threw, the exception would be stored in a `Deferred` that the watchdog
  path (`withTimeoutOrNull`, `:475`) may already have abandoned, and would be lost silently.

---

## 3. Pre-existing unreachable features the waves touched and did not fix

These are **not** wave regressions — I traced each one and it predates the 15-commit window
(boundary `40f623d`). They belong here because a wave rewrote the surrounding code and, in one case,
rewrote the comments to describe the defect as an intentional removal.

### U1. Per-server actions — edit, share, QR, clipboard, duplicate, delete — have no live entry point

`MainRecyclerAdapter` invokes exactly one listener, ever:

```
$ grep -n "adapterListener\|onItemLongClick\|setOnLongClickListener" ui/MainRecyclerAdapter.kt
33:    private val adapterListener: MainAdapterListener?
64:    var onItemLongClick: ((String) -> Unit)? = null
197:        holder.binding.sectionHeaderRoot.setOnClickListener {
245:        binding.infoContainer.setOnClickListener {
246:            adapterListener?.onSelectServer(guid)
```

`onItemLongClick` is declared and **never invoked** — `git log -S "setOnLongClickListener"` on that
file returns nothing; the adapter has never had one. So:

- `MainActivity.kt:674-675` wires `serversAdapter.onItemLongClick = { guid -> showServerActions(guid) }`
  to a callback that never fires. `showServerActions` (`:683`) and the whole `ServerActionsSheet`
  (share QR / share clipboard / edit / duplicate / set default / delete) are dead code.
- `ActivityAdapterListener.onEdit`, `onShare`, `onRemove`, `onRefreshData` (`:1572-1594`) are never
  called, so `shareServer` (`:1439`), `editServer` (`:1477`) and `removeServer` (`:1497`) are
  reachable only from the dead sheet.

**A user cannot edit, rename, share, QR, duplicate or delete an individual server.** Only the
whole-list actions in `menu_main` survive.

What makes this a wave concern: `202a2b5` rewrote `bindServer` and left in place the comments
`5aba40f` had written — `"Retained for host-activity API compatibility. The long-press
server-actions menu was removed, so this callback is no longer invoked by the adapter."` (`:61-64`)
and `"// Long-press server-actions menu removed: long-press is a no-op (no listener set)."`
(`:248`). The code now *documents the defect as a decision* while `MainActivity` still wires it.
That is worse than an undocumented bug: the next reader will believe it.

(The in-flight `ServersFragment` does `adapter.onItemLongClick = { guid -> mainHost.showServerActions(guid) }`
— same wiring against the same never-invoked callback. The shell split does not fix this.)

### U2. The `ui/component` binder package is built and entirely unused

Nine files (`RowBinder`, `ChipBinder`, `EmptyStateBinder`, `ToolbarBinder`, `SkeletonBinder`,
`SelectionBinder`, `SubPage`, `SingleClick`, `ComponentSupport`) plus eleven `view_*.xml` layouts,
~1,400 lines, landed across `202a2b5`/`5736224`/`13831ba`. Grep for any of the binder names outside
`java/com/v2ray/ang/ui/component/` returns **nothing**. No screen consumes them.

`5736224`'s message is honest about this — *"One file fixes all of them as the screens migrate"* —
so this is foundation, not a claim of completion. Recording it so it is not later mistaken for
delivered UI. Note the consequence for `SingleClick`: its double-press guard is described as
enforced "by construction … the only place under ui allowed to call `setOnClickListener`", but since
no screen routes through the binders, the ~40 direct `setOnClickListener` calls in `MainActivity`
are still unguarded.

---

## 4. Checked and found clean

Recording the negatives so nobody re-spends the time.

- **View ids referenced from code but missing from resources.** Extracted every `R.id.X` in
  `java/` and diffed against every `@id`/`@+id` in `res/` plus `values/ids.xml`. The only
  non-matches are framework/library ids (`android.R.id.content`, `R.id.home`,
  `design_bottom_sheet`) and `tag_last_click`, which is a real declaration in
  `res/values/ids.xml`. **No dangling id.**
- **The sign-out feature (`3acbb07`) is genuinely complete and wired**: layout row
  `activity_account.xml:587`, listener `AccountFragment.kt:200`
  (`binding.rowLogout.setOnClickListener { confirmSignOut() }`), confirm at `:668`, VM call at
  `:709`, `_disconnecting`-equivalent busy state cleared on every path including
  `onDestroyView` (`:151-154`). The retry Snackbar is held and dismissed on late success (`:286-287`).
- **`ProviderSettingsActivity` IS reachable** — `MainActivity.kt:3061`,
  `s.rowProvider.setOnClickListener { startActivity(Intent(this, ProviderSettingsActivity::class.java)) }`.
  Unlike `SettingsActivity`.
- **PC `TunUnavailable` / `EnableTunEffective` are fully wired** (unlike `PortInvalid`): written in
  `StatusBarViewModel` and `SettingsViewModel`, read in `AppManager`, `CoreManager` ×4,
  `ConfigHandler` ×2, `CoreConfigContextBuilder` ×2, `CoreConfigClashService` ×2, and the banner
  binds to real members (`TunRequestedButUnavailable:127`, `RequestTunElevationCmd:48`,
  `RoutingModeDisplay:113`). The R1 defect is in the guard, not in the plumbing.
- **PC `V2rayOutboundService.FillOutbound` throw is safely contained** — the new
  `InvalidOperationException` (`:62-68`) is raised inside `GenOutbounds()`, which runs inside the
  `try` of both `GenerateClientConfig` and the speedtest generator, each with a
  `catch (Exception ex)` that sets `ret.Msg` and returns a failed result
  (`CoreConfigV2rayService.cs:83-88`, `:224-229`, `:313-318`). It cannot escape as an unhandled
  exception.
- **PC `processService.Dispose()`** (`SpeedtestService.cs`, three sites) is valid —
  `ProcessService : IDisposable` with `public void Dispose()` at `ProcessService.cs:196`.
- **PC `_disconnecting` guard cannot strand the shield** — set at `HomeViewModel.cs:286`, cleared in
  a `finally` at `:295-299`. **`IsConnecting` cannot strand it either** — `UpdateStateTick`
  (`:480-490`) keeps the 1 s timer alive precisely while `IsConnecting` is true, and the deadline
  branch (`:424-434`) clears it. R5 is the only dead-tap I found on that path.
- **Coroutine/listener leaks.** Only two `CoroutineScope(Dispatchers…)` outside
  `CoreServiceManager` (`NotificationManager.kt:52`, `ProcessService.kt:28`), both pre-existing and
  job-tracked. `registerReceiver` sites all have matching unregisters. PC `MainWindow.OnClosed`
  gained the *missing* teardown in `ccbec27` (static `UiScaleState`/`MotionState` handlers,
  `App.ThemeTransitionHook`, `_programStartedWait`, and all six animation CTSs) — that is a leak
  fix, not a leak.

---

## 5. What to do first

1. **R1** — revert the `StatusBarViewModel.cs:513` guard to compare against the persisted intent, or
   compare both intent and effective as `SettingsViewModel.SetTunMode` already does. One line.
2. **R3 + R2** — add the "Дополнительно" row to the Settings tab
   (`startActivity(SettingsActivity.newIntent(this))`); the string `adv_entry_sub` is already
   written. Then restore `pref_confirm_remove` and `pref_show_memory` to `pref_settings.xml`, or
   delete their readers, layout and strings outright. Do not leave them half-present.
3. **U1** — make `MainRecyclerAdapter.bindServer` actually call
   `onItemLongClick?.invoke(guid)`, and delete the two comments claiming the menu was removed.
   Until then the app has no way to delete one server.
4. **R4** — bind `PortInvalid` to an inline caption, or delete the property and say so.
