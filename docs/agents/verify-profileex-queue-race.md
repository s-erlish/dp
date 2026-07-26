# Verification — "Non-thread-safe `Queue<string>` in `ProfileExManager` silently loses ping/speed results"

**Target:** `/home/user/v2rayN/v2rayN/ServiceLib/Manager/ProfileExManager.cs:9`
**Claimed severity:** high
**Verdict: REAL defect — but the reporter's mechanism is partly wrong. Corrected below. Severity: medium, not high.**

Everything below is read from the checked-out working tree (`v2rayN`, branch `claude/app-audit-agents-hyyftk`,
`net10.0`). Every claim is cited.

---

## 1. What is confirmed

### 1.1 The queue really is unsynchronized shared mutable state

`ProfileExManager.cs:9`

```csharp
private readonly Queue<string> _queIndexIds = new();
```

A `grep` over the whole file for `lock|Interlocked|Semaphore|Concurrent` returns exactly two hits, both for a
*different* field (`_lstProfileEx`, `ProfileExManager.cs:8` and `:23`). **There is no lock, no
`ConcurrentQueue`, no `Interlocked`, no semaphore protecting `_queIndexIds` anywhere in the file.** The BCL
`Queue<T>` is documented as not thread-safe.

The queue is written by:

- `IndexIdEnqueue` — `Contains` at `:37`, `Enqueue` at `:39` (a non-atomic check-then-act on its own).
- `SaveQueueIndexIds` — `Dequeue` at `:54`, inside the `for` loop at `:52-70`.

### 1.2 `SaveTo()` is reachable from three independent, uncoordinated execution contexts

| Caller | Context |
|---|---|
| `Services/SpeedtestService.cs:19` | inside the fire-and-forget `Task.Run` of `RunLoop` (`:16-21`), i.e. a pool thread, at the end of every speedtest |
| `Manager/TaskManager.cs:46` | the `while (true)` background loop started by `Task.Run(ScheduledTasks)` (`TaskManager.cs:15`), fires every 20th minute (`:39`) for the app's entire lifetime |
| `Manager/AppManager.cs:162` | `AppExitAsync`, on whatever thread shutdown runs on |

None of them coordinate. `grep -rn "ProfileExManager.Instance"` confirms these are the only three `SaveTo` sites.

### 1.3 The exception path really is swallowed, and the batch really is dropped

`SaveQueueIndexIds` has its `try` starting at `:72` — **after** the dequeue loop (`:52-70`). So anything the
loop throws escapes the method and is caught only by `SaveTo`'s outer catch:

```csharp
// ProfileExManager.cs:117-127
public async Task SaveTo()
{
    try { await SaveQueueIndexIds(); }
    catch (Exception ex) { Logging.SaveLog(_tag, ex); }
}
```

At that point the ids already pulled out of the queue by `Dequeue()` are **gone from the queue** and the
`lstInserts`/`lstUpdates` accumulated so far (`:49-50`, `:64`, `:68`) are discarded before ever reaching the DB
writes at `:74-82`. The user sees nothing; only a line lands in the log file.

### 1.4 There is a genuine overlap window that needs no true parallelism at all

```csharp
// ProfileExManager.cs:45-54
var cnt = _queIndexIds.Count;                                                    // :45
if (cnt > 0)
{
    var lstExists = await SQLiteHelper.Instance.TableAsync<ProfileExItem>().ToListAsync();  // :48  ← yield point
    ...
    for (var i = 0; i < cnt; i++)
    {
        var id = _queIndexIds.Dequeue();                                         // :54
```

`cnt` is captured at `:45`, then the method **awaits** a DB read at `:48` before it starts dequeuing. Two
overlapping `SaveTo` flows (e.g. a speedtest finishing at `SpeedtestService.cs:19` while the 20-minute
`TaskManager.cs:46` tick fires, or app-exit at `AppManager.cs:162` landing on a finishing test) both capture the
same `cnt`, both resume after the await, and the loser's `Dequeue()` hits an empty queue →
`InvalidOperationException` → §1.3 → batch silently dropped. This is reproducible from interleaved async
continuations alone; it does not require multi-core races.

### 1.5 Overlapping speedtest runs are directly user-reachable — there is no re-entrancy guard

`ViewModels/ProfilesViewModel.cs:788-820` — `ServerSpeedtest` reuses one instance (`_speedtestService ??=`,
`:810`) and calls `RunLoop` (`:819`), which is **fire-and-forget** (`SpeedtestService.cs:16-21`, `Task.Run`
without awaiting). Nothing checks whether a run is already in flight.

The desktop per-row ping menu makes this a two-click operation:

```csharp
// v2rayN.Desktop/Views/ServerListView.axaml.cs:760-766
private void OnRowPing(object? sender, RoutedEventArgs e)
{
    if (SelectTargetRow() is { } profiles)
        _ = profiles.ServerSpeedtest(ResolvePingAction());
}
```

Ping row A, then ping row B before A finishes → run A's `SaveTo()` (`SpeedtestService.cs:19`) drains the queue
while run B is enqueueing/reading it, and the two `SaveTo`s can overlap outright (§1.4).

### 1.6 Abandoned tasks keep writing after `RunAsync` returns

Four loops `return` out of the task-spawning `foreach` **without** awaiting the `Task.WhenAll` for tasks already
started, when the user presses stop (`ExitLoop`, `SpeedtestService.cs:24-32`, clears `_lstExitLoop` so
`ShouldStopTest` flips true):

- `RunTcpingAsync` `:166-169` (tasks added at `:171`)
- `RunRealPingAsync` `:269-272` (tasks added at `:274`)
- `RunRealPingTcpFallbackAsync` `:304-307` (tasks added at `:316`)
- `RunUdpTestAsync` `:388-391` (tasks added at `:393`)

Those orphans keep calling `SetTestDelay` (`:177`, `:321`, `:483`, `:532`) while `RunAsync` unwinds and
`RunLoop:19` starts dequeuing. Real concurrent write/read on `_queIndexIds`.

### 1.7 A second, unrelated background thread genuinely `Enqueue`s

`TaskManager.ScheduledTasks` checks subscriptions **every minute** (`TaskManager.cs:26`, `:31`) →
`UpdateTaskRunSubscription` (`:80-109`) → `SubscriptionHandler.UpdateProcess` →
`ConfigHandler.AddBatchServers` (`SubscriptionHandler.cs:259`) → `AddServerCommon`
(`ConfigHandler.cs:1148`) → for a *new* profile with an empty `IndexId`:

```csharp
// ConfigHandler.cs:1174-1186
if (profileItem.IndexId.IsNullOrEmpty()) { profileItem.IndexId = Utils.GetGuid(false); maxSort = ...GetMaxSort(); }
...
if (maxSort > 0) ProfileExManager.Instance.SetSort(profileItem.IndexId, maxSort + 1);   // :1185
```

`SetSort` → `IndexIdEnqueue` (`ProfileExManager.cs:166`). These are brand-new ids, so the `Contains` guard does
**not** suppress them — this is a real `Enqueue` on the TaskManager thread, able to land in the middle of a
speedtest's queue reads or a `SaveQueueIndexIds` drain. Same for the UI-thread paths
`ConfigHandler.MoveServer` (`:473`, `:525`) and `SortServers` (`:1032`, `:1041`, `:1051`).

### 1.8 Parallelism magnitude is as large as claimed

- `Global.cs:117` — `public const int SpeedTestPageSize = 1000;` → Tcping / Realping / UdpTest spawn up to
  **1000** `Task.Run` bodies per batch (`SpeedtestService.cs:151-152`, `:162-185`).
- `ConfigHandler.cs:137-139` — `MixedConcurrencyCount` defaults to **5** for the mixed test
  (`SpeedtestService.cs:65`, `:416`).
- Tcping is the **default** ping method on a fresh install (`Models/Configs/ConfigItems.cs:213`,
  `PingMethod = nameof(ESpeedActionType.Tcping)`), so the 1000-way path is the normal one.

---

## 2. What is refuted

### 2.1 REFUTED: "parallel speedtest tasks concurrently `Enqueue`"

This is the reporter's headline mechanism and it is **wrong for a single speedtest run**. `GetClearItem` runs
**single-threaded on the `RunAsync` thread before any test task starts** (`SpeedtestService.cs:44`, loop at
`:117-139`) and pre-enqueues *every* id under test:

```csharp
// SpeedtestService.cs:121-137 — all five ESpeedActionType values covered
case ESpeedActionType.Tcping: case ESpeedActionType.Realping: case ESpeedActionType.UdpTest:
    ... ProfileExManager.Instance.SetTestDelay(it.IndexId, 0); break;
case ESpeedActionType.Speedtest:
    ... ProfileExManager.Instance.SetTestSpeed(it.IndexId, 0); break;
case ESpeedActionType.Mixedtest:
    ... SetTestDelay(it.IndexId, 0); SetTestSpeed(it.IndexId, 0); break;
```

Every subsequently tested id therefore already sits in the queue, so when the parallel tasks later call
`SetTestDelay`/`SetTestSpeed`/`SetTestMessage`/`SetTestIpInfo`, the guard at `ProfileExManager.cs:37`

```csharp
if (indexId.IsNotEmpty() && !_queIndexIds.Contains(indexId))
```

is `false` and **`Enqueue` at `:39` is never reached**. The parallel phase performs concurrent `Contains`
(read-only) calls, which are harmless *unless something else is concurrently writing. The dedup guard
accidentally protects the common path.* Batches also run strictly sequentially (`:154`, `:207`, `:343`), so
batch N+1 cannot overlap batch N.

Corollary: the reporter's cited "concurrent Enqueue" sites (`SpeedtestService.cs:171-184, 274-278, 316-328,
393-397, 428-473`) do concurrent *reads*, not writes, on their own.

### 2.2 REFUTED: "can create duplicate `ProfileExItem` **rows** for one IndexId"

`IndexId` is the primary key:

```csharp
// Models/Entities/ProfileExItem.cs:6-7
[PrimaryKey]
public string IndexId { get; set; }
```

Duplicate rows are impossible — SQLite rejects them. The check-then-act at `ProfileExManager.cs:106-109`
(`FirstOrDefault(...) ?? AddProfileEx(indexId)`) has a **different** consequence, which is worth reporting on its
own (see §3.3): duplicate *in-memory* objects and, if a duplicate ever reaches `lstInserts`, a UNIQUE-constraint
failure that rolls back **the whole batch**, because both bulk writes are transactional:

```csharp
// Helper/SqliteHelper.cs:28,48
await _dbAsync.InsertAllAsync(models, runInTransaction: true)
await _dbAsync.UpdateAllAsync(models, runInTransaction: true)
```

That is worse than the reporter's stated outcome (one duplicate row) — one bad entry loses every insert in the
save — but it is also harder to reach, since within a single drain the `Contains` guard prevents an id appearing
twice, and `AddProfileEx` is normally executed single-threaded by `GetClearItem`.

### 2.3 Partly wrong: "silently"

It is silent *to the user*, but `Logging.SaveLog(_tag, ex)` at `ProfileExManager.cs:86` and `:125` does write a
log line. The no-exception loss modes (§3.2) are genuinely silent everywhere.

---

## 3. Corrected description of the real defect

> `ProfileExManager._queIndexIds` (`ProfileExManager.cs:9`) is a plain `Queue<string>` with **no
> synchronization of any kind**, yet it is mutated from at least four uncoordinated contexts: the speedtest
> pool threads (`IndexIdEnqueue`, `:37-39`, via `SetTest*` `:129-159`), the subscription auto-update background
> thread (`SetSort` `:161-167` ← `ConfigHandler.cs:1185` ← `TaskManager.cs:31`), the UI command thread
> (`ConfigHandler.MoveServer` `:473`/`:525`, `SortServers` `:1032-1051`), and the `Dequeue` drain in
> `SaveQueueIndexIds` (`:52-70`) which itself runs from three independent callers
> (`SpeedtestService.cs:19`, `TaskManager.cs:46`, `AppManager.cs:162`).
>
> The single-run speedtest path is *accidentally* safe, because `GetClearItem` (`SpeedtestService.cs:117-139`)
> pre-enqueues every tested id single-threaded and the `Contains` guard at `:37` then suppresses the parallel
> re-enqueues — so the parallel tasks only *read*. The defect fires whenever a **writer** overlaps them.

### 3.1 Loss mode A — exception, batch dropped (most deterministic)

Two `SaveTo` flows overlap → the second `Dequeue()` (`:54`) hits an emptied queue →
`InvalidOperationException` → escapes the loop (the `try` only starts at `:72`) → caught and logged at
`:123-126`. The ids already dequeued in that iteration are gone from the queue **and** their
`lstInserts`/`lstUpdates` never reach the DB. Their `Delay`/`Speed`/`Sort` are never persisted.

### 3.2 Loss mode B — no exception, entry just disappears (fully silent)

Once a drain is in flight, ids get removed from the queue, so speedtest tasks' `Contains` starts returning
`false` and they *do* `Enqueue` — now genuinely concurrently, from up to 1000 continuations
(`Global.cs:117`). `Queue<T>` is not thread-safe: interleaved `Enqueue`/`Dequeue`/`Contains` can drop entries,
double-count `Count`, or hand back a slot that `Dequeue` already cleared. A `null` id reaching
`SaveQueueIndexIds` is silently skipped by design:

```csharp
// ProfileExManager.cs:55-60
var itemNew = _lstProfileEx?.FirstOrDefault(t => t.IndexId == id);
if (itemNew is null) { continue; }
```

No exception, no log line, result never written.

### 3.3 Loss mode C — check-then-act on `_lstProfileEx`

`GetProfileExItem` (`:106-109`) is a non-atomic `FirstOrDefault ?? AddProfileEx` over a `ConcurrentBag`
(`:8`). Two threads reaching it for the same missing id both `AddProfileEx` (`:91-104`) → two live objects for
one id. `SaveQueueIndexIds`'s `FirstOrDefault` (`:56`) then picks an arbitrary one and the other's values are
lost. If both ever land in `lstInserts`, the transactional `InsertAllAsync` (`SqliteHelper.cs:28`) rolls back
the **entire** insert batch on the PK violation, and the exception is swallowed at `:84-87`. Reachable when the
subscription-update thread and a speedtest touch the same new id.

---

## 4. Observable impact

`_lstProfileEx` is only reloaded from the DB at `InitData` (`:32`), called once at startup
(`MainWindowViewModel.cs:330`). So a lost save is **invisible during the session** — the UI keeps rendering
from the in-memory objects (`ProfilesViewModel.cs:450`, `:474-479`). The damage appears **after the next
launch**:

- ping / speed columns revert to stale or empty values for some servers;
- **server ordering silently reverts**, because `Sort` travels through the same queue
  (`ProfileExManager.cs:161-167`) — a drag-reorder or a column sort done near a save collision is simply lost.

## 5. Why medium, not high

- Not corruption of profiles or credentials; no crash path was proven (the throw sites sit inside
  `try/catch` at `SpeedtestService.cs:180-183`, `:323-327`, `:461-464`, or under an unobserved
  fire-and-forget `Task.Run` at `:16-21`, which .NET does not escalate by default).
- The everyday single-ping path is protected by the pre-enqueue + `Contains` dedup (§2.1), so it needs an
  overlap: a second ping before the first finishes (`ServerListView.axaml.cs:760-766` — easy), the 20-minute
  `TaskManager.cs:46` tick landing inside a long test, quitting mid-test (`AppManager.cs:162`), or a
  subscription auto-update mid-test (`TaskManager.cs:26,31`).
- Worst outcome is stale persisted metrics and lost sort order, recoverable by re-running the test.

## 6. Fix

1. `Queue<string> _queIndexIds` → `ConcurrentQueue<string>` **plus** a `HashSet<string>`/`ConcurrentDictionary`
   for the dedup that `Contains` currently does (`:37`), since `ConcurrentQueue.Contains` is an O(n) snapshot
   enumeration, not an atomic membership test. Or keep the queue and guard every touch with one `lock`.
2. Serialize `SaveQueueIndexIds` behind a `SemaphoreSlim(1,1)` so the three `SaveTo` callers cannot interleave
   across the `await` at `:48`.
3. Replace the `for (i < cnt) { Dequeue() }` loop (`:52-70`) with `while (_queIndexIds.TryDequeue(out var id))`
   — removes the stale-`cnt` empty-queue throw entirely.
4. Move the dequeue loop **inside** the `try` at `:72`, or at minimum re-enqueue what was drained when a save
   fails, so a failure does not consume the pending work.
5. Make `GetProfileExItem` (`:106-109`) atomic — back `_lstProfileEx` with a
   `ConcurrentDictionary<string, ProfileExItem>` and use `GetOrAdd`.
6. Add a re-entrancy guard to `ProfilesViewModel.ServerSpeedtest` (`:788-820`) and await/cancel the abandoned
   task lists at `SpeedtestService.cs:166-169`, `:269-272`, `:304-307`, `:388-391` instead of `return`ing out
   of the spawn loop.
