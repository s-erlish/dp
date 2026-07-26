# Recon — desktop server list + connection core (behaviour Android must match)

Repo: `/home/user/v2rayN`, branch `claude/app-audit-agents-hyyftk`.
All paths below are relative to `/home/user/v2rayN/v2rayN/` unless written in full.

Everything in this document was read out of the files cited. Line numbers are from the checked-out
working tree at the time of writing.

---

## 0. TL;DR — the three behaviours the task asks about

| Gesture | Desktop semantics | Where it is implemented |
|---|---|---|
| **Tap the ALREADY-selected server while DISCONNECTED** | Connects. The tap is never dead. `SetDefaultServer` returns `true` without reloading (nothing to persist), and the caller explicitly calls `Connect()`. | `ProfilesViewModel.SetDefaultServer` `ServiceLib/ViewModels/ProfilesViewModel.cs:621-666` (esp. `:635-638`), `HomeViewModel.SelectServer` `v2rayN.Desktop/ViewModels/HomeViewModel.cs:246-305` (esp. `:297-300`) |
| **Tap the ALREADY-selected server while CONNECTED** | Nothing happens: no reload, no bounce, no spinner. The shield stays `Connected`. This is the ONLY tap that spins nothing. | `HomeViewModel.cs:266-268` (`willConnect = changed \|\| !wasConnected`), `ProfilesViewModel.cs:635-638` |
| **Tap a DIFFERENT server while CONNECTED** | Seamless *make-before-break* switch. UI drops to `Connecting` immediately and holds it (`_awaitingCoreCycle`) so the still-running OLD core can't read as "Connected"; the engine restarts ONLY the Xray main core with the new config while sing-box + the TUN adapter stay alive; `CoreSwitchSettled` resolves the hold the moment the switch lands. `CoreStop` is never called, `RunningCoreType` is never reset, so no subscriber ever sees "disconnected" mid-switch. | `HomeViewModel.cs:263-305`, `ProfilesViewModel.cs:646-664`, `MainWindowViewModel.SwitchServer` `ServiceLib/ViewModels/MainWindowViewModel.cs:892-958`, `CoreManager.SwitchServer` `ServiceLib/Manager/CoreManager.cs:270-391` |
| **Ping / speedtest results on rows** | Results are reported by MUTATING the engine's `ProfileItemModel` instances in place (per-item `PropertyChanged`, **no** `CollectionChanged`). The Home list keeps *distinct retained row instances*, so it subscribes to every source item's `PropertyChanged` and mirrors `Delay/DelayVal/SpeedVal/IpInfo/IsActive` onto the matching displayed row by `IndexId`. Without that mirror the spinner and the ms value never appear. | `ProfilesViewModel.SetSpeedTestResult` `ProfilesViewModel.cs:292-320`, `HomeViewModel.ResyncItemSubscriptions/OnSourceItemChanged` `HomeViewModel.cs:567-638` |

---

## 1. Object graph — who owns what

```
MainWindow (v2rayN.Desktop/Views/MainWindow.axaml.cs)
 └── MainWindowViewModel                      (ServiceLib/ViewModels/MainWindowViewModel.cs)
      ├── ProfilesViewModel                   (ServiceLib/ViewModels/ProfilesViewModel.cs)   ← server list engine
      ├── StatusBarViewModel  (singleton)     (ServiceLib/ViewModels/StatusBarViewModel.cs)  ← running server / speed / TUN
      └── Reload() / SwitchServer()           ← the only two core-load entry points from the UI
 └── HomeViewModel  (created once)            (v2rayN.Desktop/ViewModels/HomeViewModel.cs)
      ├── wraps Profiles + StatusBar (does NOT duplicate engine logic — HomeViewModel.cs:10-28)
      ├── ServerGroups : ObservableCollection<HomeServerGroup>   ← grouped projection of ProfileItems
      └── IsConnected / IsConnecting / ConnectFailed / HasServers / IsEmpty / UpSpeed / DownSpeed / Uptime

CoreManager (singleton)                       (ServiceLib/Manager/CoreManager.cs)
      ├── LoadCore / CoreStop / SwitchServer  ← process lifecycle
      └── AppEvents.CoreRunningStateChanged / CoreSwitchSettled   (ServiceLib/Events/AppEvents.cs)
```

* Exactly ONE `HomeViewModel` exists (`MainWindow.axaml.cs:1000`), shared by the widescreen `HomeView`
  and the compact `CompactHomeView`; only the *active layout's* Home holds it as DataContext
  (`MainWindow.axaml.cs:495-518` `BindActiveHome`), the inactive one is unbound to `null` to release
  its rows. Both layouts wire the same `ConnectHeroView` through the shared
  `HomeHeroPresenter.Bind` (`v2rayN.Desktop/Views/HomeHeroPresenter.cs:40-124`), so connect state is
  identical at any width.
* `ServerListView` is re-hosted verbatim by both layouts (`HomeView.axaml:35`,
  `CompactServersView.axaml.cs:1-38`) — one list implementation, two shells.

---

## 2. Connect lifecycle

### 2.1 App start = DISCONNECTED, always

`MainWindowViewModel.Init` (`MainWindowViewModel.cs:318-346`) deliberately does **not** auto-connect:

```
// Consumer-VPN (Happ) model: the app starts DISCONNECTED. Do NOT auto-connect the core on
// startup. The core is started only on an explicit user action …
SetReloadEnabled(true);              // MainWindowViewModel.cs:341-345
```

A subscription refresh must also never connect a disconnected app — `UpdateTaskHandler`
(`MainWindowViewModel.cs:362-401`) only calls `Reload()` when a core was **already running** before
the refresh (`:376-394`, the "OFF-model guard (A2)"). The import-triggered download uses a
separate log-only handler with no `Reload()` at all (`MainWindowViewModel.cs:689-699`).

### 2.2 Shield tap

`HomeViewModel.ConnectToggle` (`HomeViewModel.cs:184-194`): connected → `Disconnect()`, else
`Connect()`.

**`Connect()` — `HomeViewModel.cs:196-225`:**

1. `BeginConnecting()` (`:307-317`) — sets `IsConnecting=true`, clears `ConnectFailed`, arms a
   **12-second safety deadline** (`_connectingUntil`), and starts the transient 1 s tick.
2. `var executed = await _main.Reload();`
3. **Failure is painted only when `executed == true`.** `Reload()` returns `false` *only* when it
   deferred to an in-flight reload (semaphore contended, e.g. a rapid second tap). In that case the
   in-flight owner's follow-up job will still bring the core up, so painting an error would be a lie.
   This is the **"fix first-tap connect"** UI half (commit `a33f492`).

```csharp
if (executed && !IsCoreRunning())      // HomeViewModel.cs:215-223
{
    IsConnecting = false;
    _connectingUntil = null;
    ConnectFailed = true;              // sticky → hero paints the Error shield
}
SyncState();
```

**`Reload()` contract — `MainWindowViewModel.cs:804-881`:**

```csharp
public async Task<bool> Reload()
{
    if (!await _reloadSemaphore.WaitAsync(0)) { _hasNextReloadJob = true; return false; }   // :814-818
    …
    var profileItem = await ConfigHandler.GetDefaultServer(_config);      // :830
    var allResult   = await CoreConfigContextBuilder.BuildAll(_config, profileItem);        // :836
    await Task.Run(async () => {
        await LoadCore(allResult.MainResult.Context, allResult.PreSocksResult?.Context);    // :844
        await SysProxyHandler.UpdateSysProxy(_config, false);                               // :845
        await Task.Delay(1000);                                                             // :846
    });
    RxSchedulers.MainThreadScheduler.Schedule(async () => await StatusBarViewModel.TestServerAvailability()); // :848-851
    …
    finally { SetReloadEnabled(true); _reloadSemaphore.Release();
              if (_hasNextReloadJob) { _hasNextReloadJob = false; await Reload(); } }        // :868-878
    return true;
}
```

Key points to port:
* the semaphore is **non-blocking** (`WaitAsync(0)`) plus a single "one more job queued" latch —
  never a queue, so bursts of taps collapse into at most one follow-up run;
* the return value distinguishes *ran* from *deferred*; the UI must not treat "deferred" as failure;
* a connectivity self-check (`TestServerAvailability`, `StatusBarViewModel.cs:391-405`) runs right
  after a successful load and writes `RunningInfoDisplay`.

### 2.3 `CoreManager.LoadCore` → the "first tap does nothing" engine fix

`LoadCore` (`CoreManager.cs:142-156`) takes the single `_coreOpGate`, clears sticky user-stop intent,
re-arms the restart-loop CTS, and calls `LoadCoreInternal`.

`LoadCoreInternal` (`CoreManager.cs:163-248`):

1. Cache the contexts for crash recovery **before** starting (`:173-174`).
2. Generate the run-config on disk (`:178-183`); a generation failure reports and returns.
3. `await CoreStopInternal(); await Task.Delay(100);` (`:193-194`) — stop-before-start, which on
   Windows+TUN also removes the wintun adapter so a stale adapter can never break this connect.
4. **One clean re-arm** (this is the engine half of `a33f492`):

```csharp
var started = await TryStartCoresOnce(mainContext, preContext);      // CoreManager.cs:203
if (!started)
{
    await CoreStopInternal();          // free the port / remove the stale adapter
    await Task.Delay(300);             // let the OS settle a touch longer
    started = await TryStartCoresOnce(mainContext, preContext);      // :208-211
}
```

  It is a **single retry, not a loop** — a genuinely broken config still fails on the second attempt
  and surfaces an honest error.

5. On success (`:213-236`): mark `AppManager.RunningCoreType = mainContext.RunCoreType` (**the MAIN
   core's type — never the pre-service's**, or stats polling and the UI mode break, `:218-224`),
   capture the hot-swap surface, publish `AppEvents.CoreRunningStateChanged(true)`, stamp
   `_coreUpSince`, start the watchdog.
6. On failure (`:238-247`): `CoreStopInternal()` so no orphan lingers **and** `RunningCoreType` is
   reset — otherwise `IsRunningCore()` would falsely report "connected" with a dead tunnel behind it.

`TryStartCoresOnce` (`CoreManager.cs:1304-1312`) is the honesty rule:

```csharp
await CoreStart(mainContext);
await WaitForProxyPort(preContext);          // SOCKS5 greeting poll, 5 s budget (:1360-1427)
await CoreStartPreService(preContext);
var preServiceRequiredButFailed = preContext != null && _processPreService is null;
return _processService is { HasExited: false } && !preServiceRequiredButFailed;
```

* `HasExited` (not merely non-null) — a core that started and then died during the socks wait counts
  as a **failed** attempt.
* A required-but-missing pre-service is **not** success. Documented cause of the false «Подключено»
  with no traffic (`CoreManager.cs:1294-1302`).

### 2.4 Disconnect

`HomeViewModel.Disconnect` (`HomeViewModel.cs:227-243`):

```csharp
IsConnecting = false; _connectingUntil = null;
_awaitingCoreCycle = false;                 // a deliberate stop ends any mid-switch hold
ConnectFailed = false;                      // a user stop is not a failure
await CoreManager.Instance.CoreStop(byUser: true);
await SysProxyHandler.UpdateSysProxy(_config, true);   // force-clear OS proxy or the user loses internet
_connectedSince = null;
SyncState();
```

`CoreManager.CoreStop(byUser)` (`CoreManager.cs:1186-1206`) — **before** taking the gate it:
* `Interlocked.Increment(ref _coreStopGeneration)` — any recovery loop that captured the old
  generation bails permanently;
* sets sticky `_userStopRequested` when `byUser`;
* `CancelRestartLoop()` so a backoff wait breaks immediately.

`CoreStopInternal` (`CoreManager.cs:1213-1283`) brackets the whole teardown with `_stopping = true`
(so the `Exited` callback and the watchdog cannot mistake it for a crash), stops watchdog, kills main
+ pre process (detaching `Exited` first), removes the wintun adapter, clears the hot-swap surface,
resets `RunningCoreType` to the idle sentinel, and publishes `CoreRunningStateChanged(false)`.

### 2.5 The UI connect state machine (`HomeViewModel.SyncState`, `:322-382`)

```
running = IsRunningCore()   // AppManager.IsRunningCore(Xray) || (sing_box)   HomeViewModel.cs:319-320

if (running && _awaitingCoreCycle):
      if (now <= _connectingUntil)  -> return           # HOLD: keep showing Connecting
      else                          -> _awaitingCoreCycle = false   # defensive give-up

if (running):
      _connectedSince ??= now
      IsConnected = true; IsConnecting = false; _connectingUntil = null; ConnectFailed = false
      Uptime = hh:mm:ss since _connectedSince
      Up/DownSpeed from the last statistics event
else:
      _awaitingCoreCycle = false; _connectedSince = null
      IsConnected = false; Uptime = 00:00:00; speeds = 0 KB/s
      if (IsConnecting && now > _connectingUntil):
            IsConnecting = false; _connectingUntil = null; ConnectFailed = true   # honest timeout
```

Event sources (no permanent polling):
* `AppEvents.CoreRunningStateChanged` → `OnCoreRunningStateChanged` (`:391-403`). A **stop** transition
  clears `_awaitingCoreCycle` using *the event's own flag*, never a live `IsCoreRunning()` probe —
  the marshalled callback can arrive after `LoadCore`'s back-to-back stop→start already brought the
  new core up, which a probe would misread as "still running".
* `AppEvents.CoreSwitchSettled` → `OnCoreSwitchSettled` (`:410-419`): clears the hold, snaps to
  `Connected`.
* A **transient** 1 s `DispatcherTimer` exists ONLY while connected (to advance uptime) or while a
  connect is pending (to enforce the 12 s deadline) — `UpdateStateTick` `:426-441`. A disconnected
  idle app has no timer at all.

`ConnectFailed` is a first-class fourth state, not "silently back to idle": the hero paints
`ConnectVisualState.Error` (`ConnectHeroView.axaml.cs:29-40`) and `HomeHeroPresenter.ApplyConnectState`
(`HomeHeroPresenter.cs:126-151`) resolves precedence **Connected > Connecting > Error > Idle**.

Tray/window icon derives from the same truth, event-driven, in `StatusBarView.axaml.cs:102-125` +
`:132-152` (`Connecting` == a reload is in flight, read from `MainWindowViewModel.BlReloadEnabled`
with a "seen enabled" latch that disambiguates the initial false).

---

## 3. Server list: projection, reconcile, row tap

### 3.1 Engine list

`ProfilesViewModel.RefreshServersBiz` (`ProfilesViewModel.cs:391-423`) rebuilds `ProfileItems`
**wholesale** — `Clear()` then `AddRange()` of brand-new `ProfileItemModel` instances — on *every*
change, including a mere active-flag flip. It then sets `HasLoadedServers = true` (`:414`) *after*
the AddRange.

`HasLoadedServers` (`ProfilesViewModel.cs:35-42`) is the "empty vs not-loaded-yet" discriminator:

> Until then an EMPTY `ProfileItems` only means "not loaded yet" — it does NOT mean the user has no
> servers.

`GetProfileItemsEx` (`:443-487`) builds each row: joins statistics + `ProfileExItem` (persisted
`Delay`/`Speed`/`IpInfo`), sets `IsActive = t.IndexId == _config.IndexId` (`:473`), and for CUSTOM
(raw xray-json) nodes introspects the wrapped proxy outbound so the row chip shows the real
protocol/transport instead of "CUSTOM" (`:456-470`).

### 3.2 Home projection: reconcile IN PLACE, never Clear()+rebuild

`HomeViewModel.ReconcileGroups` (`:538-562`) + `ReconcileServerGroups` (`:709-745`) +
`HomeServerGroup.ReconcileServers` (`:901-952`):

* Coalescing: `OnProfileItemsChanged` → `ScheduleReconcile` (`:502-518`) posts **one** deferred
  reconcile at `DispatcherPriority.Background`. Reconciling synchronously on the `Clear()` would
  observe a transient `count == 0`, latch `IsEmpty=true` for one frame (the "black flash" /
  onboarding flash on every select) and tear down every container.
* Empty/onboarding is a FACT, never a default (`:550-552`):

```csharp
var loaded = Profiles?.HasLoadedServers == true;
HasServers = loaded ? count > 0 : _storedServersAtLaunch == true;
IsEmpty    = loaded ? count == 0 : _storedServersAtLaunch == false;
```

  `_storedServersAtLaunch` is a **synchronous launch-time snapshot** taken before the first frame
  (`MainWindow.axaml.cs:215`, `AppManager.HasStoredProfiles()` `ServiceLib/Manager/AppManager.cs:223-234`,
  `null` = UNKNOWN). Unknown leaves **both** flags false so neither the list nor the empty state is
  asserted. This is what stops a returning user seeing "add a subscription" for the first ~second.
* Groups diff by `Key = "{subid}|{name}"`; rows diff by `IndexId`. A persisting row **keeps its
  container** and only its reactive fields are copied over (`CopyLiveState` `:980-991`:
  `IsActive, Delay, DelayVal, SpeedVal, IpInfo, TodayUp/Down, TotalUp/Down`). A row whose *displayed
  non-reactive* fields changed (rename/protocol shift) is swapped so only that one row re-renders
  (`SameDisplay` `:968-975`).
* Pinned subscriptions float to the top via a **stable** `OrderByDescending` (`:684-694`), so
  unpinned groups keep their existing order underneath.
* Group expand state is remembered per key in `_groupExpanded` (`:42`, `:700`, `:765`).

### 3.3 Row tap (the input layer matters)

`ServerListView.axaml.cs:136-219` deliberately does **not** use `Tapped`:

> the rows live inside a ScrollViewer, and Avalonia cancels the Tapped gesture on the slightest
> pointer movement / scroll-drag, so a click frequently never selected.

Manual press→release: `OnRowPointerPressed` (`:150-171`) records `_rowPressTarget`;
`OnRowPointerReleased` (`:175-194`) fires `vm.SelectServer(item.IndexId)` **only** if still pressing,
left button, and the pointer is still over the same row; `OnRowPointerCaptureLost` (`:206-213`)
cancels — a scroll-drag steals capture, so it can never select.

Android equivalent to preserve: a row tap must be cancelled by a scroll gesture, and must be
attributed to the row the press *started* on.

---

## 4. Tapping a server — exact semantics

### 4.1 The engine contract — `ProfilesViewModel.SetDefaultServer(indexId)` (`:619-666`)

```csharp
if (indexId.IsNullOrEmpty())            return false;
if (indexId == _config.IndexId)         return true;    // ALREADY the default → success, NO reload  (:635-638)
var item = await AppManager.Instance.GetProfileItem(indexId);
if (item is null)  { notice(PleaseSelectServer); return false; }

if (await ConfigHandler.SetDefaultServerIndex(_config, indexId) == 0)   // persists config.IndexId (:646)
{
    await RefreshServers();                                             // rebuild list → IsActive flips (:648)
    var running = IsRunningCore(Xray) || IsRunningCore(sing_box);       // (:653-654)
    if (running) SwitchRequested.Publish();      // seamless switch      (:657)
    else         Reload();                       // fresh connect        (:661)
    return true;
}
return false;
```

Documented rationale at `:626-634`: tapping the already-active server "must not be a dead action";
it previously early-returned with no signal, which blocked the connect path. Now it reports success
**without reloading** — the default is already correct, so there is nothing to persist and *a running
core must not be bounced*. **The connect decision belongs to the caller.**

Note that callers who only want to set a default (tray/status-bar picker
`StatusBarViewModel.ServerSelectedChanged` `:374-389` → `SetDefaultServerRequested` →
`MainWindowViewModel.cs:305-308`; context-menu «Сделать основным» `ServerListView.axaml.cs:752-758`)
ignore the return value, so their behaviour is unchanged (still a no-op in that branch).

### 4.2 The UI contract — `HomeViewModel.SelectServer(indexId)` (`:246-305`)

```csharp
var changed      = indexId != _config?.IndexId;   // captured BEFORE SetDefaultServer mutates config
var wasConnected = IsConnected;

var willConnect = changed || !wasConnected;       // the ONLY tap that spins nothing:
                                                  //   !changed && wasConnected  (re-tap while connected)
if (willConnect)
{
    BeginConnecting();                            // spinner + 12 s deadline
    if (wasConnected)
    {
        IsConnected = false;                      // drop Connected NOW so the hero shows Connecting
        _awaitingCoreCycle = true;                // hold it: the still-running OLD core must not read Connected
    }
}

if (!await Profiles.SetDefaultServer(indexId))    // invalid / failed pick
{
    IsConnecting = false; _connectingUntil = null; _awaitingCoreCycle = false;
    SyncState(); return;                          // abort the spinner, do NOT connect
}

if (!changed && !wasConnected) await Connect();   // re-tap while disconnected: nothing reloaded → connect explicitly
else                          SyncState();        // changed pick already reloaded/switched inside SetDefaultServer
```

**Full truth table**

| `changed` | `wasConnected` | Spinner? | What actually runs | End state |
|---|---|---|---|---|
| no | no | yes | `SetDefaultServer` → `true`, no reload; then `HomeViewModel.Connect()` → `Reload()` → `LoadCore` | Connected (or honest failure) |
| no | yes | **no** | `SetDefaultServer` → `true`, no reload, no bounce | stays Connected on the same server |
| yes | no | yes | `SetDefaultServer` persists → `RefreshServers` → `ReloadRequested` → `MainWindowViewModel.Reload()` | Connected on the new server |
| yes | yes | yes (held) | `SetDefaultServer` persists → `RefreshServers` → `SwitchRequested` → `MainWindowViewModel.SwitchServer()` → `CoreManager.SwitchServer` | Connected on the new server, tunnel never visibly dropped |
| invalid id | any | briefly, then cleared | nothing | previous state restored, no error shield |

Note the double-connect hazard the code explicitly avoids (`HomeViewModel.cs:296-300`): when the pick
*changed*, `SetDefaultServer` already triggers the reload/switch, so calling `Connect()` again would
connect twice.

---

## 5. Switching servers while connected — the seamless path

### 5.1 `MainWindowViewModel.SwitchServer` (`:883-958`)

Mirrors `Reload()` but:
* routes to `CoreManager.SwitchServer` instead of `LoadCore` (`:927`);
* **drops the unconditional 1 s settle delay** — a switch keeps the same deterministic ports/TUN, so
  there is nothing to wait for (`:929-930`);
* still re-asserts the system proxy (idempotent, ports unchanged);
* shares `_reloadSemaphore` with `Reload` so a switch and a reload can never run concurrently
  (`:894-898`).
* **Nuance worth knowing when porting:** if the semaphore was contended, it sets `_hasNextReloadJob`
  and returns; and its `finally` runs the follow-up as `await Reload()` (`:952-956`), i.e. the
  follow-up of a deferred switch is a *full reload*, not another seamless switch.

### 5.2 `CoreManager.SwitchServer` (`:252-391`) — the tier chain

Guards, in order:

1. `_switchSemaphore.WaitAsync(0)` — **non-blocking debounce** (`:276-279`). If a switch is already
   in flight this one is skipped; the newest target already sits in the persisted config, so the
   in-flight switch (or the next tap) converges. Hot-swaps can never stack.
2. `_coreOpGate` (`:285`) — the single serialization point shared with `LoadCore`/`CoreStop`/recovery.
   Lock order is `_switchSemaphore → _coreOpGate`, never reversed (`:281-284`).
3. Clear `_userStopRequested`, re-arm the restart CTS (`:289-290`) — a switch is user intent to be connected.
4. **Not actually connected** (`_processService` null/exited, or `RunningCoreType == v2rayN`) → this
   is a fresh start, do `LoadCoreInternal` and return `false` (`:293-299`).
5. **Main-core TYPE changed** (Xray ↔ sing-box) → full reload (`:302-306`).
6. **Pre-service SHAPE changed** (required-now vs alive) → full reload (`:311-317`).
7. Regenerate the run-config for the new server on disk up-front (`:322-328`); failure → full reload.
8. Refresh `_lastMainContext`/`_lastPreContext` so a crash-restart reloads the NEW server (`:333-334`).

Then:

**Tier 2 — live outbound hot-swap: DISABLED.** `EnableHotSwapTier = false`
(`CoreManager.cs:47-55`, commit `6d55081`). Reason, verbatim from the source:

> It declared success on the api command's exit code alone, which does not prove traffic actually
> moved: against the panel's custom XRAY_JSON (Remnawave) configs the swap could exit 0 yet leave
> routing on the previous outbound, so the UI painted "connected → new server" while the real exit IP
> stayed on the FIRST server, and the "success" suppressed the fallback.

The implementation is retained behind the flag (`TryHotSwapOutbound` `:402-455`: read the new config,
lift the first proxy-protocol outbound, re-tag it to `_runningProxyTag`, strip `mux`, then
`xray api rmo` + `xray api ado`). **Do not port this to Android.** The lesson to port is the rule:
*a switch may only be reported as successful when the new server is guaranteed to be carrying
traffic; an exit code is not proof.*

**Tier 1 — restart ONLY the main core (`TryRestartMainOnly` `:457-512`)** — this is the live path:

```csharp
if (preContext != null && _processPreService is null or { HasExited: true }) return false;  // can't keep tunnel up

if (_processService != null)
{
    _stopping = true;                                  // bracket: this exit is intentional
    _processService.Exited -= OnCoreProcessExited;     // detach crash hook FIRST
    await _processService.StopAsync();
    _processService.Dispose(); _processService = null;
    _stopping = false;
}
await Task.Delay(100);                                 // let the OS release the freed socks port
await CoreStart(mainContext);
if (_processService is null or { HasExited: true }) return false;   // caller does a full restart
await CaptureSwitchContext(mainContext, <config path>);             // refresh hot-swap surface
return true;
```

It deliberately does **not** call `CoreStop` (which would kill the pre-service, destroy the wintun
adapter and publish the stopped state). sing-box keeps forwarding into the same deterministic socks
port while the new Xray rebinds it — a few hundred ms, no adapter flap, no OS-route drop.

**Final fallback — full `LoadCoreInternal`** (`:361-369`), and even the `catch` path recovers with a
full restart (`:371-385`). *The user is never left disconnected.*

### 5.3 Switch completion signalling

`AppEvents.CoreSwitchSettled` (`ServiceLib/Events/AppEvents.cs`, the `CoreSwitchSettled` field) is
published on **every** successful switch path — Tier 2 (`CoreManager.cs:349`), Tier 1 (`:357`), the
full-restart fallback (`:365-368`) and the catch-recovery (`:380-383`). Payload is always `true`;
it is never raised on failure. It fires on a background thread — subscribers marshal themselves.

This exists precisely because the seamless tiers raise **no** `CoreRunningStateChanged(false)`, so
without it the UI's mid-switch `Connecting` hold would linger up to the 12 s deadline after an
instant switch (`HomeViewModel.cs:162-169`, `:405-419`).

### 5.4 The invariant Android must reproduce

While a switch is in flight:
* `IsRunningCore()` stays **true** throughout (no `CoreStop`, no `RunningCoreType` reset) — no
  subscriber (shield, tray, status bar) may observe "disconnected";
* the *displayed* state is nevertheless `Connecting`, because `HomeViewModel` forces
  `IsConnected=false` + `_awaitingCoreCycle=true` (`HomeViewModel.cs:277-281`) and `SyncState`
  refuses to report Connected while the hold is set (`:333-340`);
* the hold is released by exactly one of: a real stop transition (`:397-400`), `CoreSwitchSettled`
  (`:410-419`), the 12 s deadline (`:335-339`), or an aborted pick (`:286-291`).

---

## 6. Ping / speedtest — why results reach the rows (commit `d4a5a09`)

### 6.1 How a test is started

* Per-subscription meta-bar "check ping" → `SubscriptionMetaView.OnPingClick`
  (`v2rayN.Desktop/Views/SubscriptionMetaView.axaml.cs:520-524`) → `Profiles.FastRealPingCmd`
  → `ProfilesViewModel.ServerSpeedtest(ESpeedActionType.FastRealping)` (`ProfilesViewModel.cs:191-194`).
  `FastRealping`/`Mixedtest` test **all currently displayed** `ProfileItems` (`:791-799`), other
  actions test the current selection (`:802`).
* Per-row context menu "тест задержки" → `ServerListView.axaml.cs:760-778`. The probe is resolved
  from the user's ping-method setting, and the resolver **only ever yields the two working probes**:

```csharp
private static ESpeedActionType ResolvePingAction()
    => AppManager.Instance.Config.SpeedTestItem.PingMethod == "Tcping"
        ? ESpeedActionType.Tcping
        : ESpeedActionType.Realping;      // any other/stale value falls back safely
```

  (`ServerListView.axaml.cs:768-778` — the comment flags Httping/Icmping as dead options with no
  engine probe behind them.)

### 6.2 How a result travels

`SpeedtestService` (`ServiceLib/Services/SpeedtestService.cs`):
* `GetClearItem` (`:70-147`) first pushes the **"Testing…" placeholder** into every row it is about to
  test (`:124` `await UpdateFunc(it.IndexId, ResUI.Speedtesting, "")`) and zeroes the stored delay.
  `ResUI.Speedtesting == "Testing..."` (`ServiceLib/Resx/ResUI.resx:390-392`).
* Results: `DoRealPing` (`:478-499`), `RunTcpingAsync` (`:149-196`), `DoUdpTest` (`:519-535`),
  `DoSpeedTest` (`:501-517`) each call `ProfileExManager.SetTestDelay/SetTestSpeed` (persistence) and
  `UpdateFunc(indexId, value)`.
* **Realping → Tcping graceful fallback** (`:295-332`): when the speedtest core cannot be started
  (e.g. "real delay" chosen while disconnected), each node's *pre-rewrite* real address/port is probed
  with the same TCP handshake, so a row still shows a number instead of «—». The real targets are
  snapshotted BEFORE `GenerateClientSpeedtestConfig` rewrites `ServerTestItem.Port` to a local
  inbound (`:243-248`).
* The callback is marshalled to the UI thread by `ProfilesViewModel.ServerSpeedtest`
  (`:810-818`), then `SetSpeedTestResult` (`:292-320`) **mutates the existing `ProfileItemModel`
  in place**:

```csharp
var item = ProfileItems.FirstOrDefault(it => it.IndexId == result.IndexId);
if (result.Delay.IsNotEmpty()) { item.Delay = result.Delay.ToInt(); item.DelayVal = result.Delay; }
if (result.Speed.IsNotEmpty())   item.SpeedVal = result.Speed;
if (result.IpInfo.IsNotEmpty())  item.IpInfo   = result.IpInfo;
```

  (`ProfileItemModel` — `ServiceLib/Models/Dto/ProfileItemModel.cs` — marks
  `IsActive/Delay/DelayVal/SpeedVal/IpInfo/TodayUp/TodayDown/TotalUp/TotalDown` `[Reactive]`.)

### 6.3 The bug and the fix

That mutation raises a **per-item `PropertyChanged` and NO `CollectionChanged`**. The Home list only
reconciled on `CollectionChanged`, and its displayed rows are *distinct retained instances* (they are
the objects that survived the last reconcile). So neither the "Testing…" spinner nor the ms result
ever reached a visible row — pressing "check ping" looked completely dead
(`HomeViewModel.cs:51-57`, commit `d4a5a09`).

The fix, in `HomeViewModel`:

```csharp
private readonly List<ProfileItemModel> _observedItems = new();          // :57

private void ResyncItemSubscriptions()                                    // :567-585
{
    foreach (var it in _observedItems) it.PropertyChanged -= OnSourceItemChanged;
    _observedItems.Clear();
    foreach (var it in Profiles.ProfileItems) { it.PropertyChanged += OnSourceItemChanged; _observedItems.Add(it); }
}
// called at the end of EVERY ReconcileGroups (:561) because the engine rebuilds ProfileItems wholesale

private void OnSourceItemChanged(object? sender, PropertyChangedEventArgs e)   // :591-619
{
    // only Delay / DelayVal / SpeedVal / IpInfo / IsActive are mirrored
    var row = FindRowByIndexId(src.IndexId);                                   // :621-638
    if (row == null || ReferenceEquals(row, src)) return;   // no displayed copy, or row IS the source
    row.Delay = src.Delay; row.DelayVal = src.DelayVal; row.SpeedVal = src.SpeedVal;
    row.IpInfo = src.IpInfo; row.IsActive = src.IsActive;
}
```

Teardown removes every hook (`:778-794`).

### 6.4 How a row renders the result (`ServerListView.axaml:219-260` + converters)

Three mutually-exclusive presentations sharing one right-aligned `Auto` column:

| Condition | Converter | Rendering |
|---|---|---|
| `DelayVal` non-empty and **not** parseable as int → a test is in flight | `DelayTestingConverter` (`ServerListView.axaml.cs:860-869`) | 15 px arc spinner; **never** the literal "Testing…" text |
| `DelayVal` parses as int → a real result exists | `DelayResultConverter` (`:843-852`) | the value, bold, tabular digits |
| result `<= 0` (core writes `-1`) | `DelayDisplayConverter` (`:877-890`) | em-dash «—», never the raw "-1" |
| ink | `DelayInkConverter` (`:899-939`) | real reading → theme ink (accent on light / on-surface on dark); failure → muted variant tone. **No green/red good-bad signal.** |

The spinner's visibility is bound to the *same lever* as its rotation (base hidden; shown only by
`:is(Window):not(.lite) Ellipse.Spinner.spinning`, `ServerListView.axaml:64-69`) so reduced-motion
collapses it entirely instead of leaving a frozen half-ring, and reacts live to the lite toggle.

Persistence: results survive a list rebuild because `GetProfileItemsEx` re-joins `ProfileExItem`
(`ProfilesViewModel.cs:449-479`), and `ProfileExManager.SaveTo()` is called at the end of every test
run (`SpeedtestService.cs:16-21`) plus every 20 min by `TaskManager` (`ServiceLib/Manager/TaskManager.cs:39-52`).

---

## 7. "Never self-restart a live tunnel" — watchdog, crash detection, auto-restart

The self-disconnect-under-load bug (commit `a33f492`): the watchdog ran a SOCKS5 readiness probe every
~7 s and, after 3 misses, killed+restarted the core. On a weak/CPU-starved PC the local handshake is
slow, so the probe timed out against a perfectly healthy core.

**Current rule (`WatchdogLoopAsync` `CoreManager.cs:1001-1068`):**

* 15 s cadence (`:1026`), live only while a core is up.
* It does **only** a cheap `HasExited` liveness check on the main core and on a *required* pre-service
  (`:1045-1051`). No readiness probe. A living process is left running, full stop (`:1008-1013`).
* It skips entirely while `_stopping`, `_userStopRequested`, a switch holds `_switchSemaphore`, or
  `RunningCoreType == v2rayN` (`:1034-1040`).
* It resets the auto-restart attempt budget after a stretch of stable uptime (`:1053-1061`).

**Crash detection** is primarily the immediate `Process.Exited` callback,
`OnCoreProcessExited` (`:649-674`), which is NOT a crash when: `_stopping`, `_userStopRequested`,
a seamless switch holds the semaphore, the app is idle, or the sender is a stale process already
replaced by a Tier-1 swap (`:659-671`).

**Recovery** — `HandleUnexpectedExitAsync` (`:676-725`): mark idle (`RunningCoreType = v2rayN`),
clear a stranded system proxy for `ForcedChange`/`Pac` modes so the user isn't routed through a dead
`127.0.0.1:port`, publish `CoreRunningStateChanged(false)` (**honest shield drop**), then
`AttemptAutoRestartAsync`.

**`AttemptAutoRestartAsync` (`:727-827`)** — one recovery driver at a time (`_restartGate`), backoff
1 s → 2 → 4 → 8 … capped at 30 s (`:816`), at most 5 attempts per rolling 60 s window
(`:88-89`, `:764-791`), then it gives up rather than hammer. It bails **permanently** the moment an
external/user stop is observed — `ShouldAbortRecovery` (`:829-836`) checks `_stopping`,
`_userStopRequested`, token cancellation, a changed `_coreStopGeneration`, or a non-idle
`RunningCoreType`. The abort is re-checked **under the gate** in `RestartLoadCoreAsync` (`:838-867`),
which is the definitive close on the hand-off race: *a user Disconnect during the backoff window can
never be silently undone.*

**On-demand health check** — `RequestHealthCheckAsync` (`:911-974`), called by the OS resume /
network-change hooks, debounced 2.5 s. Liveness (`HasExited`) stays authoritative and immediate; the
readiness probe now demands a **sustained** failure before concluding the tunnel is wedged:

```csharp
// ProbeSocksReadySustainedAsync — CoreManager.cs:1070-1102
// 3 attempts, 3000 ms each, 500 ms apart; ALIVE if ANY attempt answers.
// port <= 0 answers true (nothing to probe ⇒ do not manufacture a failure)
```

Single-shot probe = a real SOCKS5 greeting handshake (`:1104-1132`), the same greeting
`WaitForProxyPort` uses at connect time (`:1360-1427`).

**Rules to port to Android:**
1. Never restart a process that is still alive. Liveness ≠ readiness.
2. A slow handshake under CPU load is not evidence of a dead tunnel; require a sustained failure.
3. Every intentional teardown must be bracketed by a flag so its process death is not read as a crash.
4. A user disconnect must supersede any in-flight recovery — with a generation counter *and* a sticky
   intent flag, both checked again after acquiring the lock.
5. Rate-limit restarts; give up rather than crash-loop.

---

## 8. Subscription update

* Manual: meta-bar refresh (`SubscriptionMetaView.axaml.cs:526-580`) → `MainWindowViewModel.
  UpdateSubscriptionProcess(subId, blProxy)` (`MainWindowViewModel.cs:714-717`) →
  `SubscriptionHandler.UpdateProcess` on a background task. It shows an **in-place** spinner in the
  same 40 px slot so the action row never reflows, then re-reads the persisted `SubItem` and
  re-projects the traffic pill / expiry / announce (`:567-579`).
* Scheduled: `TaskManager.ScheduledTasks` ticks every 60 s and runs `UpdateTaskRunSubscription`
  (`ServiceLib/Manager/TaskManager.cs:23-35`). Core/app update checks are deliberately disabled for
  this consumer build (`:73-75`).
* `SubscriptionHandler.UpdateProcess` (`ServiceLib/Handler/SubscriptionHandler.cs:5-59`) iterates
  every enabled `SubItem` (filtered by `subId` when given), downloads main + additional subs together
  with the response headers, then imports servers and persists `subscription-userinfo` +directives.
* **User-Agent contract** (`SubscriptionHandler.cs:92-113`): the Remnawave/departament panel serves
  its real server list only to a v2rayNG-family UA; anything else gets an "app not supported"
  placeholder node. So a v2rayNG UA is forced for **every** fetch (account-imported and the user's own
  manually added sub), except when the item already carries an explicit v2rayNG-family UA.
  Explicitly documented as 1:1 with Android's `HttpUtil.getUrlContentWithUserAgentEx`.
* Post-update: `UpdateTaskHandler` (`MainWindowViewModel.cs:362-401`) refreshes the list, and reloads
  **only if a core was already running** (see §2.1).

---

## 9. Status bar / routing honesty (context for parity)

`StatusBarViewModel`:
* `RoutingModeDisplay` + `TunAvailable` + `TunRequestedButUnavailable` (`:109-134`, `:549-557`) —
  when TUN "all traffic" was requested but the process is not elevated, the config is downgraded so
  core-config generation stays valid, **but** `_tunRequested` preserves the user's real intent and the
  UI surfaces "requested but unavailable" with an explicit elevation CTA
  (`RequestTunElevationCmd` `:204-218`; banner in `HomeView.axaml:47-74`). Traffic is never silently
  downgraded behind the user's back.
* `TestServerAvailability` (`:391-405`) writes «Testing…» then the real
  `ConnectionHandler.RunAvailabilityCheck` result (`ServiceLib/Handler/ConnectionHandler.cs:10-16`,
  which retries the real-ping twice at `:32-55`).

---

## 10. Reference pseudo-code for the Android port

```text
onServerRowTapped(guid):
    changed      = (guid != persistedSelectedGuid)
    wasConnected = uiState.isConnected

    if (changed || !wasConnected):
        beginConnecting()                    # spinner + 12s deadline + clear ConnectFailed
        if (wasConnected):
            uiState.isConnected = false      # show Connecting during the switch
            awaitingCoreCycle   = true       # the OLD core must not read as Connected

    ok = setDefaultServer(guid)              # false only for invalid/missing/failed
    if (!ok):
        cancelConnecting(); awaitingCoreCycle = false; sync(); return

    if (!changed && !wasConnected):
        connect()                            # nothing was reloaded → connect explicitly
    else:
        sync()                               # setDefaultServer already reloaded/switched


setDefaultServer(guid):
    if (guid is empty)              return false
    if (guid == persisted)          return true                  # NO reload, NO bounce
    if (profile not found)          { notify; return false }
    persist(guid); refreshList()                                 # IsActive flips on rows
    if (coreRunning) requestSwitch() else requestReload()
    return true


switchServer(newContext):                    # engine side
    if (!tryAcquireNonBlocking(switchLock))  return             # debounce: newest target already persisted
    acquire(coreOpGate)
    clearUserStopIntent()
    if (!coreActuallyRunning || coreTypeChanged || preServiceShapeChanged): fullReload(); return
    if (!regenerateConfigOnDisk()):                                        fullReload(); return
    cacheRecoveryContexts(newContext)                            # a crash must restart the NEW server
    if (restartMainCoreOnly()):                                  # keep tun/pre-service alive
        publish(SwitchSettled); return
    fullReload(); publish(SwitchSettled if running)


syncConnectState():
    running = coreRunning()
    if (running && awaitingCoreCycle && now <= connectDeadline): return      # HOLD
    if (running && awaitingCoreCycle):                awaitingCoreCycle = false
    if (running):  connected(); clearFailure(); tickUptime()
    else:          awaitingCoreCycle = false; disconnected()
                   if (connecting && now > connectDeadline): connecting=false; connectFailed=true

onCoreStopped():         awaitingCoreCycle = false; sync()       # use the EVENT flag, never a live probe
onSwitchSettled():       awaitingCoreCycle = false; connecting=false; connected=true; sync()
onPingResult(guid,ms):   mirror ms onto the DISPLAYED row instance for guid (spinner → value)
```

---

## 11. Where the Android app currently differs (verified call sites, for the fix agents)

These are the exact Android sites that implement the three behaviours. Facts below come from reading
the files; they are the counterparts to port against.

* **`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt:1446-1464`
  (`setSelectServer`, doc comment from `:1435`)** — its own doc-comment states "Tapping a server row SELECTS it — it never
  connects and never reconnects", and line `1448` is `if (guid == selected) return`, i.e. re-tapping
  the already-selected server is a **hard no-op in both connected and disconnected states**. Desktop's
  rule is different: a re-tap while *disconnected* must connect (`HomeViewModel.cs:297-300`); only a
  re-tap while *connected* is a no-op.
* **`MainActivity.kt:1462-1463` → `promptApplySelectedServer` (`:1471-1487`)** — switching while
  connected shows a Snackbar asking to apply, and only on the action does it `restartV2Ray()`.
  Desktop switches immediately and seamlessly (§5). Whatever product decision is made about the
  prompt, the *engine* behaviour to match is: guaranteed new server, no visible drop, and a positive
  "switch settled" signal that resolves the UI hold.
* **`MainActivity.kt:1578-1600` (`restartV2Ray`)** — full stop → poll `isRunning` until stopped
  (deadline `RESTART_STOP_TIMEOUT_MS = 6000L`, `MainActivity.kt:218`) → start. This is the equivalent
  of desktop's *final fallback* tier only; there is no Tier-1 "restart main core, keep the tunnel
  interface up" path. Its comment already documents the race it fixes (a new start arriving while the
  old core is still up, silently keeping the PREVIOUS server) — the same class of bug desktop hit with
  Tier 2 and solved by refusing to declare success without a genuine config reload.
* **`MainActivity.kt:1524-1538`** — connect/disconnect toggle with `connectInProgress` +
  `scheduleConnectWatchdog()`; the desktop equivalent is `BeginConnecting()`'s 12 s deadline +
  `ConnectFailed` (`HomeViewModel.cs:307-317`, `:370-380`).
* **`viewmodel/MainViewModel.kt:116-124` (`reloadServerList`) and `:671-698`** — the list is rebuilt
  wholesale and connect state is driven by daemon broadcasts (`MSG_STATE_RUNNING` /
  `MSG_STATE_NOT_RUNNING` / `MSG_STATE_START_SUCCESS` / `MSG_STATE_START_FAILURE` /
  `MSG_STATE_STOP_SUCCESS`). That is the Android analogue of
  `AppEvents.CoreRunningStateChanged`; what is missing on that channel is a **positive
  "switch settled"** event (desktop `AppEvents.CoreSwitchSettled`) — without it a mid-switch
  "Connecting" hold has nothing to resolve it except a timeout.
* **Ping results**: `MainViewModel.kt:700-720` maps `MSG_MEASURE_DELAY_SUCCESS` →
  `updateTestResultAction`, `MSG_MEASURE_CONFIG_SUCCESS` → `updateListAction.value = getPosition(guid)`,
  observed at `MainActivity.kt:541` (`refreshServerLists(index)`). The desktop invariant to hold on
  Android is the one from §6.3: **whatever object the visible row is bound to must be the object the
  test result is written to** (or be mirrored from it by id). Desktop's failure mode was exactly a
  second, retained row instance that nobody updated.
* **Ping method**: desktop resolves only two working probes and safely falls back
  (`ServerListView.axaml.cs:768-778`), while Android's `MainViewModel.testAllServers()`
  (`viewmodel/MainViewModel.kt:325-333`) dispatches four (`TCP_CONNECT`, `HTTP_URL`, `ICMP`,
  `PROXIED_REAL_DELAY`). If Android keeps four, desktop's picker needs the two dead rows removed
  (already flagged in `ServerListView.axaml.cs:768-774` as owned by the settings owner).

---

## 12. Complete file inventory read for this report

Desktop:
* `v2rayN.Desktop/Views/ServerListView.axaml` (313 lines) and `.axaml.cs` (939 lines)
* `v2rayN.Desktop/Views/HomeView.axaml` (80) / `.axaml.cs` (82)
* `v2rayN.Desktop/Views/CompactHomeView.axaml.cs` (150), `CompactServersView.axaml.cs` (38)
* `v2rayN.Desktop/Views/HomeHeroPresenter.cs` (202)
* `v2rayN.Desktop/Views/ConnectHeroView.axaml.cs` (partial: `:29-40`, `:293-311`, `:319-337`, `:461-491`)
* `v2rayN.Desktop/Views/StatusBarView.axaml.cs` (220)
* `v2rayN.Desktop/Views/SubscriptionMetaView.axaml.cs` (687)
* `v2rayN.Desktop/Views/MainWindow.axaml.cs` (partial: `:215-216`, `:495-518`, `:985-1030`)
* `v2rayN.Desktop/ViewModels/HomeViewModel.cs` (1012)
* `ServiceLib/Manager/CoreManager.cs` (1509)
* `ServiceLib/Manager/AppManager.cs` (partial: `:64-79`, `:223-252`)
* `ServiceLib/Manager/ProfileExManager.cs` (partial: `:1-140`)
* `ServiceLib/Manager/TaskManager.cs` (partial: `:1-80`)
* `ServiceLib/ViewModels/MainWindowViewModel.cs` (998)
* `ServiceLib/ViewModels/ProfilesViewModel.cs` (988)
* `ServiceLib/ViewModels/StatusBarViewModel.cs` (632)
* `ServiceLib/Services/SpeedtestService.cs` (598)
* `ServiceLib/Handler/ConfigHandler.cs` (partial: `:385-451`)
* `ServiceLib/Handler/ConnectionHandler.cs` (partial: `:10-75`)
* `ServiceLib/Handler/SubscriptionHandler.cs` (partial: `:1-120`)
* `ServiceLib/Events/AppEvents.cs`, `ServiceLib/Events/EventChannel.cs`
* `ServiceLib/Models/Dto/ProfileItemModel.cs`
* `ServiceLib/Resx/ResUI.resx` (`Speedtesting`, `SpeedtestingWait`)
* Commits inspected: `a33f492`, `6d55081`, `d4a5a09` (`git show`, full diffs for the latter two).

Android (counterpart call sites only):
* `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt` (`:218`, `:1435-1500`, `:1520-1610`)
* `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/viewmodel/MainViewModel.kt` (`:100-140`, `:300-340`, `:650-730`)
