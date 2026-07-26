# Adversarial verification — `_hasNextReloadJob` read-then-clear race

**Target:** `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/MainWindowViewModel.cs:873`
**Verdict:** **REAL defect, wrong mechanism.** The named window (`:873`–`:875`) is provably
self-healing. A different, genuinely lossy window exists in the same flag protocol, with exactly the
user-visible consequence the reporter describes. The second half of the claim (`await Reload()` at
`:955`) is confirmed verbatim and is the part that actually fires in normal use.
**Corrected severity:** medium (was: high).

---

## 1. What the code actually is

```
801  private bool _hasNextReloadJob = false;
802  private readonly SemaphoreSlim _reloadSemaphore = new(1, 1);
```

`Reload()` (`:811`):

```
814      if (!await _reloadSemaphore.WaitAsync(0))
815      {
816          _hasNextReloadJob = true;
817          return false;
818      }
...
868      finally
869      {
870          SetReloadEnabled(true);
871          _reloadSemaphore.Release();
873          if (_hasNextReloadJob)
874          {
875              _hasNextReloadJob = false;
876              await Reload();
877          }
878      }
```

`SwitchServer()` (`:892`) is the same protocol: defer-set at `:894`–`:898`, release-then-check at
`:951`–`:956`.

Facts the claim asserts about the field, all confirmed:

- Not `volatile`, no `lock`, no `Interlocked` — `MainWindowViewModel.cs:801`. A repo-wide grep finds
  the field only at `:801, :816, :873, :875, :897, :952, :954`; nothing else touches it. (Contrast
  `CoreManager._stopping` / `_userStopRequested`, which *are* `volatile` —
  `Manager/CoreManager.cs:39,74`.)
- It is genuinely touched from more than one thread. Not every caller is on the UI dispatcher:
  `MainWindowViewModel.Init` registers the background scheduler at `:333`
  (`TaskManager.Instance.RegUpdateTask(_config, UpdateTaskHandler)`), and `TaskManager.cs:15` starts
  it with `Task.Run(ScheduledTasks)` — a bare thread-pool loop with no `SynchronizationContext`. That
  loop reaches `UpdateTaskRunSubscription` (`TaskManager.cs:81`) → `_updateFunc` →
  `MainWindowViewModel.UpdateTaskHandler` (`:362`), which calls `await Reload()` at `:383` and `:391`
  whenever a core is already running (`:376`–`:394`). `UpdateSubscriptionProcess` likewise wraps the
  handler in `Task.Run` (`:716`). Meanwhile a user server pick enters `SwitchServer()` on the UI
  scheduler (`:282`–`:285`). So a thread-pool `Reload()` and a UI `SwitchServer()` really can hit
  `_hasNextReloadJob` concurrently — the claim is not arguing about a single-threaded VM.

---

## 2. Why the *named* window (`:873`–`:875`) does **not** drop a request

This is where the report is wrong. Note the order in the `finally`: `Release()` happens at `:871`,
**one statement before** the read at `:873`. For a competing thread to reach `_hasNextReloadJob =
true` (`:816`/`:897`) at all, its `WaitAsync(0)` must have returned false, i.e. it observed the
semaphore *taken*. At the instant of the `:873` read the finishing thread has already released, so
exactly two cases exist:

- **(a) Nobody else holds the semaphore.** The arriving request's `WaitAsync(0)` *succeeds* — it
  never touches the flag; it runs the reload/switch itself. Nothing to lose.
- **(b) A third caller grabbed the semaphore in between.** The arriving request sets the flag, and
  the finishing thread clears it at `:875` — but its very next statement is `await Reload()`
  (`:876` / `:955`), whose own `WaitAsync(0)` now fails against that third holder, so `:816`
  **re-sets `_hasNextReloadJob = true`** and returns `false`. The third holder's `finally` picks it
  up. Still not lost.

So "a request arriving between the read and the clear is silently dropped" is refuted by the code as
written. The flag is a coalescing bit, not a payload — the follow-up job re-reads the live default
via `ConfigHandler.GetDefaultServer(_config)` (`:830`/`:911`) — so any surviving follow-up converges
on the newest server regardless of who queued it.

---

## 3. The window that *is* lossy (corrected mechanism)

The orphan is on the **requester** side, between its failed `WaitAsync(0)` and its write — raced
against the holder's release-then-check:

```
T_holder (thread-pool Reload from UpdateTaskHandler)   T_req (UI SwitchServer, user picked server B)
------------------------------------------------------ ------------------------------------------------
inside try, semaphore count == 0
                                                       :894  WaitAsync(0) -> false   (sees count 0)
                                                       ...   ** preempted here **
:950  SetReloadEnabled(true)   (non-blocking Schedule)
:951  Release()
:952  if (_hasNextReloadJob)  -> false
:957  returns; NOTHING is in flight
                                                       :897  _hasNextReloadJob = true
                                                       :898  return
```

The flag is now `true` with no owner running and nobody scheduled to check it. The switch is never
performed; the stale `true` only causes one spurious extra reload the next time some *unrelated*
`Reload()`/`SwitchServer()` happens to complete (which, for an idle user, may be never — the
subscription auto-update loop is the only other trigger, `TaskManager.cs:81`).

This is the classic "signal published after the consumer's last check, outside the lock" lost wakeup.
The missing `volatile` is a real second-order contributor rather than the primary cause: the holder's
read at `:873`/`:952` follows `SemaphoreSlim.Release()` (a fence for *that* thread), but the
requester's plain store at `:816`/`:897` carries no release fence, so on the arm64 builds the store
can become visible to the holder later than program order suggests — widening exactly this window.

**Window size:** a few instructions on each side (`SemaphoreSlim.WaitAsync(0)` returns a
already-completed task, so the `await` at `:894` does **not** yield — the gap to `:897` is real
machine-level preemption, not a scheduler hop; and `SetReloadEnabled` at `:950` only posts to the
dispatcher, so the holder's `finally` is equally short). Rare — but this is the same class of
"low-probability, silent, wrong-server" hazard the team already refused to tolerate when it disabled
Tier 2 (`Manager/CoreManager.cs:47`–`55`).

---

## 4. The consequence chain the reporter describes is accurate

Every link verified:

1. `ProfilesViewModel.SetDefaultServer` persists first, paints second, requests third —
   `ProfilesViewModel.cs:646` `SetDefaultServerIndex(...) == 0` → `:648` `await RefreshServers()` →
   `:657` `SwitchRequested.Publish()`. `RefreshServers` sets `IsActive = t.IndexId == _config.IndexId`
   (`ProfilesViewModel.cs:473`), so row **B** is already painted active before the switch is even
   requested.
2. `SwitchRequested` is a plain `Subject` fan-out with no replay/backpressure
   (`Events/EventChannel.cs:7,14-17`) — a dropped delivery is unrecoverable; the subscription is
   `Subscribe(async _ => await SwitchServer())` at `MainWindowViewModel.cs:282`–`285`.
3. If the job is orphaned, `CoreManager` is untouched: `_lastMainContext` still holds server A
   (`CoreManager.cs:333`–`334` only runs inside a real `SwitchServer`), so even the watchdog /
   auto-restart recovery keeps re-establishing **A**.
4. The UI never reports an error. `HomeViewModel.SelectServer` set `IsConnected = false;
   _awaitingCoreCycle = true` (`HomeViewModel.cs:279`–`280`); with the core still up and no
   stop/settle event ever arriving, `SyncState` holds Connecting until the 12 s deadline
   (`HomeViewModel.cs:333`–`340`), then falls through to `IsConnected = true; ConnectFailed = false`
   (`:342`–`:349`). Net result: shield says **Connected**, list says **B**, traffic exits **A**,
   no error anywhere.

---

## 5. Second half of the claim — confirmed, and this one is deterministic

`SwitchServer`'s follow-up is `await Reload()` (`:955`), not `await SwitchServer()`. Consequence
verified in the core layer:

- `Reload()` → `LoadCore` (`:844`, `:976`) → `CoreManager.LoadCore*` which calls `CoreStopInternal()`
  before starting (`CoreManager.cs:193`), and `CoreStopInternal` publishes
  `AppEvents.CoreRunningStateChanged.Publish(false)` (`CoreManager.cs:1278`) — the visible drop, plus
  the TUN adapter flap on Windows.
- `SwitchServer` → `CoreManager.SwitchServer` (`:927`, `CoreManager.cs:270`) whose Tier 1
  (`TryRestartMainOnly`, `CoreManager.cs:354`) keeps sing-box + the tun adapter alive, publishes
  `CoreSwitchSettled` (`:357`) and never publishes a `false` running state — the whole point of the
  feature (`CoreManager.cs:264`–`267`, `MainWindowViewModel.cs:883`–`890`).

So **every** deferred switch — which happens on the completely ordinary path of "user picks a server
while the hourly subscription auto-update reload is in flight", or "user picks two servers in quick
succession" — is silently downgraded from seamless to a full stop/start with a visible disconnect.
No race needed; this fires whenever the semaphore is contended. The root cause is that
`_hasNextReloadJob` is a single bit that records *that* a job is pending but not *which kind*, so the
switch/reload distinction is destroyed at defer time.

---

## 6. What a fix has to do

1. Make the pending-job state atomic *and* consumed inside the critical section, with a re-check loop
   so a set that races the release cannot be orphaned — e.g. keep an `int _pendingJob` mutated only
   via `Interlocked`, and have the requester, after setting it, retry `WaitAsync(0)` once (if it now
   succeeds, nobody is home and it must run the job itself). Simply marking the field `volatile`
   closes the visibility half but **not** the interleaving half.
2. Record the pending job's kind (switch vs reload) so `:955` replays `SwitchServer()` for a deferred
   switch and `Reload()` for a deferred reload — a deferred reload must never be downgraded to a
   switch, but a deferred switch must not be upgraded to a tunnel drop.
3. Cheap belt-and-braces regardless of (1)/(2): after any completed reload/switch, compare
   `AppManager.Instance.RunningCoreType` / the served config against `_config.IndexId` and
   re-converge, so "list says B, tunnel on A" cannot persist silently.

(Non-blocking side observation found while reading: the `DesignMode` early-outs at `:820`–`:824` and
`:901`–`:905` `Release()` without the flag check, so they drop a queued job unconditionally. Harmless
— `DesignMode` is a designer-only flag, `MainWindowViewModel.cs:12` — but it is the same protocol
hole in miniature.)
