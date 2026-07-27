# Desktop core correctness audit — `v2rayN/ServiceLib` (+ `v2rayN.Desktop/Account`)

Scope: `/home/user/v2rayN/v2rayN/ServiceLib/**` on branch `claude/app-audit-agents-hyyftk`, plus the
Departament client API layer, which actually lives in `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Account/**`
(it was named in the brief, so it is included and flagged as out of the literal ServiceLib path).

Method: read the full connect/disconnect/switch lifecycle, subscription update, config generation,
ping/speedtest, process management and settings persistence; diffed against `master` to separate
Departament changes from inherited upstream behaviour. Every claim below cites `file:line` and was
verified by reading the file. No style opinions.

Attribution convention used throughout:
- **[dep]** — introduced or materially changed by Departament work (present in `git diff master...HEAD`).
- **[upstream]** — inherited from v2rayN, but reachable/relevant on the Departament product path.

---

## CRITICAL

### C1. A transient API failure during account sync DELETES every imported subscription and its servers
`v2rayN.Desktop/Account/SubscriptionSyncManager.cs:52-73, 84, 124-134` **[dep]**

`ImportAll()` swallows `ApiError` from both network calls:

```csharp
try { primary = await _api.GetPrimarySubscription(); }
catch (ApiError) { /* fall through */ }          // :57-60

try { all = (await _api.GetSubscriptionAll()).Items; }
catch (ApiError) { all = new List<SubInfoDto>(); } // :67-70
```

It then calls `Import(primary, all, profile)`. With `primary == null` and `all` empty,
`BuildCandidates` (`:142-169`) returns an empty sequence, so the `foreach` at `:84` never runs and
`newMap` stays empty. Control reaches the prune loop:

```csharp
foreach (var kv in managed)                                   // :125
{
    if (kv.Value.IsNotEmpty() && !newMap.Values.Contains(kv.Value))
    {
        await ConfigHandler.DeleteSubItem(config, kv.Value);   // :129
    }
}
AuthTokenStore.SetManagedGuids(newMap);                        // :133  (now empty)
```

`ConfigHandler.DeleteSubItem` (`ServiceLib/Handler/ConfigHandler.cs:2107-2124`) deletes the `SubItem`
row **and** calls `RemoveServersViaSubid(config, id, false)` — every server of that subscription is
removed from the DB.

Failure scenario: user launches the app on a flaky Wi-Fi / the backend 502s / the VPN is up but the
tunnel is dead. `AccountViewModel.AutoImportAndRefreshHome` (`ViewModels/AccountViewModel.cs:1202-1207`)
runs on the startup sync phase, `GET /client/subscription` throws, `ImportAll` returns an empty list
**successfully**, and the user's entire Departament server list disappears. `AccountRepository.Guard`
(`Account/AccountRepository.cs:93`) never sees a failure because `ImportAll` already swallowed it, so
`import.OnFailure(Report)` at `AccountViewModel.cs:1205` reports nothing. The comment at
`AccountViewModel.cs:1198-1200` ("A transient import failure is surfaced but never blocks the rest of
the load") is not true on this path.

This is not even a rare edge: the class doc itself states at `SubscriptionSyncManager.cs:141`
*"today /all never does"* expose its own URL — so **any** failure of the single `GetPrimarySubscription`
call is sufficient to produce an empty candidate set and wipe everything.

Fix: never prune on an empty/partial result. Track whether *both* fetches succeeded and skip the prune
loop (and the `SetManagedGuids` overwrite) unless the remote set was fully and successfully enumerated;
propagate the `ApiError` to `Guard` so the UI reports it.

---

## HIGH

### H1. `RemoveTunDevice` runs an unbounded external process while holding the core-op gate — a hang wedges connect/disconnect for the session
`ServiceLib/Manager/CoreManager.cs:1257-1260` → `ServiceLib/Common/WindowsUtils.cs:55-75` → `ServiceLib/Common/Utils.cs:986-1017` **[dep call site]**

`CoreStopInternal` runs inside `_coreOpGate` (held by `CoreStop` at `CoreManager.cs:1197-1205`, and by
`LoadCoreInternal`'s stop-before-start at `CoreManager.cs:193`) and awaits:

```csharp
if (Utils.IsWindows() && _config?.TunModeItem?.EnableTun == true)
{
    await WindowsUtils.RemoveTunDevice();   // CoreManager.cs:1259
}
```

`RemoveTunDevice` loops over two adapter names and for each awaits
`Utils.GetCliWrapOutput(pnpUtilPath, arg)` (`WindowsUtils.cs:68`), which is
`await cmd.ExecuteBufferedAsync()` (`Utils.cs:1003`) — **no timeout, no `CancellationToken`**.

`pnputil /remove-device` is a PnP operation that can block indefinitely when the device is in use or
the PnP subsystem is busy. If it hangs, `_coreOpGate` is never released, and since `_coreOpGate` is
*the* single serialization point for every start/stop transition (documented at `CoreManager.cs:57-62`),
Connect, Disconnect, SwitchServer and every recovery reload deadlock permanently. `CoreStop` from
`AppExitAsync` (`AppManager.cs:166`) also blocks, so the app cannot even exit cleanly.

Secondary cost even in the happy path: `TunModeItem.EnableTun` now defaults to `true`
(`ServiceLib/Models/Configs/ConfigItems.cs:184`, `ServiceLib/Handler/ConfigHandler.cs:92-93`), so on
Windows **every** connect pays two `pnputil` process spawns before the core starts — and four when
`LoadCoreInternal`'s single retry fires (`CoreManager.cs:208`).

Fix: wrap the CLI call in a `CancellationTokenSource` (2–3 s) and drop it out of the gate — do the
adapter cleanup before acquiring `_coreOpGate`, or fire it as a bounded best-effort task.

### H2. `ProfileExManager` mutates a non-thread-safe `Queue<string>` from concurrent speedtest tasks
`ServiceLib/Manager/ProfileExManager.cs:9, 35-41, 43-89, 106-109` **[upstream, hit hard by dep changes]**

```csharp
private readonly Queue<string> _queIndexIds = new();          // :9  — NOT thread-safe

private void IndexIdEnqueue(string indexId)
{
    if (indexId.IsNotEmpty() && !_queIndexIds.Contains(indexId))  // :37 O(n) read
        _queIndexIds.Enqueue(indexId);                            // :39 write
}
```

`SetTestDelay` / `SetTestSpeed` / `SetTestMessage` / `SetTestIpInfo` (`:129-159`) all call
`IndexIdEnqueue`, and they are invoked from parallel `Task.Run` bodies:
`SpeedtestService.cs:171-184` (tcping), `:274-278` (real-ping), `:316-328` (the new dep TCP fallback),
`:393-397` (udp), `:428-473` (mixed). Up to `SpeedTestPageSize` tasks enqueue simultaneously while
`SaveQueueIndexIds` dequeues (`:54`) from `SpeedtestService.RunLoop`'s completion (`:19`),
`TaskManager.ScheduledTasks` (`Manager/TaskManager.cs:46`) and `AppManager.AppExitAsync`
(`Manager/AppManager.cs:162`).

Concurrent `Enqueue`/`Dequeue`/`Contains` on `Queue<T>` corrupts `_array`/`_head`/`_tail`. Observable
failures: `InvalidOperationException` ("Queue empty") or `IndexOutOfRangeException` inside `SaveTo`,
which is swallowed by `SaveTo`'s `catch` at `:123-126` — so **ping/speed results are silently not
persisted** and the columns are blank after restart; or duplicated/dropped ids so some rows persist
stale values.

Related check-then-act at `:106-109`: `_lstProfileEx.FirstOrDefault(...) ?? AddProfileEx(indexId)` can
insert two `ProfileExItem` rows for the same `IndexId` under concurrency; `SaveQueueIndexIds` then
picks an arbitrary one via `FirstOrDefault` (`:56`) and `InsertAllAsync` can hit a PK conflict
(swallowed at `:84-87`).

Fix: `ConcurrentQueue<string>` + a `ConcurrentDictionary<string,byte>` for the dedupe set, and make
`GetProfileExItem` use `ConcurrentDictionary.GetOrAdd` instead of the bag + `FirstOrDefault`.

### H3. The user's TUN preference is silently and permanently destroyed after one unelevated run
`ServiceLib/ViewModels/StatusBarViewModel.cs:147-157` **[dep]**

```csharp
_tunRequested = _config.TunModeItem.EnableTun;                 // :147 (VM field only)
if (_config.TunModeItem.EnableTun && AllowEnableTun()) { EnableTun = true; }
else { _config.TunModeItem.EnableTun = EnableTun = false; }    // :156 mutates the SHARED config
```

`_config` is `AppManager.Instance.Config` — the one global instance that `ConfigHandler.SaveConfig`
serializes wholesale (`Handler/ConfigHandler.cs:199-224`, `JsonUtils.Serialize(config, true, true)`,
which uses `DefaultIgnoreCondition.Never` — `Common/JsonUtils.cs:27-32`). The in-memory downgrade at
`:156` is therefore written to disk by the very next save:

- `TaskManager.ScheduledTasks` → `ConfigHandler.SaveConfig(_config)` every 20 minutes (`Manager/TaskManager.cs:39-52`);
- `AppManager.AppExitAsync` → `ConfigHandler.SaveConfig(_config)` on every exit (`Manager/AppManager.cs:161`).

`_tunRequested` (`StatusBarViewModel.cs:134`) preserves the intent only in a VM field that is never
persisted, so after one launch without admin rights the config permanently reads `EnableTun: false`.
The user's "all traffic through the tunnel" choice is gone, with no notice beyond a bindable flag.

Compounding: the elevation path itself loses the intent too. `DoEnableTun` sets
`_config.TunModeItem.EnableTun = false` and returns early into `RebootAsAdmin`
(`StatusBarViewModel.cs:527-530`) **before** reaching its own `SaveConfig` at `:544`. `RebootAsAdmin`
(`Manager/AppManager.cs:186-190`) then calls `AppExitAsync`, which saves the config — with
`EnableTun: false`. So after the user grants elevation exactly as `RequestTunElevationCmd` asks, the
app restarts elevated **with TUN off** and they must toggle it again.

Fix: keep the persisted `EnableTun` as the user's intent and carry the "cannot honour it right now"
state in a separate, non-persisted effective flag consumed by config generation; on the reboot-as-admin
path, persist `EnableTun = true` before restarting.

### H4. `_hasNextReloadJob` read-then-clear race silently strands the tunnel on the wrong server
`ServiceLib/ViewModels/MainWindowViewModel.cs:801, 816, 873-877, 897, 952-956` **[dep]**

```csharp
private bool _hasNextReloadJob = false;              // :801 — plain bool, not volatile, no lock
...
if (!await _reloadSemaphore.WaitAsync(0)) { _hasNextReloadJob = true; return; }   // :814-818 / :894-899
...
finally
{
    _reloadSemaphore.Release();
    if (_hasNextReloadJob) { _hasNextReloadJob = false; await Reload(); }         // :873-877 / :952-956
}
```

The set at `:816`/`:897` happens on whichever thread the Rx subscription delivers on; the
read-and-clear at `:873-875`/`:952-954` happens on the completing thread. A request that arrives
between the read and the clear is silently dropped, and the field is not `volatile` so the write may
not even be visible.

Why this is user-visible and *wrong*, not just a missed refresh: `ProfilesViewModel.SetDefaultServer`
persists the new default and refreshes the list **before** raising the switch
(`ViewModels/ProfilesViewModel.cs`, `ConfigHandler.SetDefaultServerIndex` → `await RefreshServers()` →
`SwitchRequested.Publish()`). The row for server B is already painted `IsActive` while the tunnel is
still on server A. Losing the queued job leaves exactly that state: **UI says B, exit IP is A**, with
no error anywhere. That is the same failure class the team already fixed once by disabling Tier 2
(`CoreManager.cs:47-55`).

Two more defects on the same lines:
- The follow-up is `await Reload()` (`:955`), not `await SwitchServer()` — a deferred switch degrades
  to a full restart with a visible tunnel drop, defeating the seamless-switch design.
- `CoreManager.SwitchServer`'s own fast-path comment (`CoreManager.cs:273-275`) claims the in-flight
  switch "re-read[s] from disk on the next tap"; it does not — it runs with the `mainContext` captured
  before the second tap. The guard at `:276-279` therefore drops the newer target outright if it is
  ever reached directly.

Fix: `Interlocked.Exchange` on an `int` flag (or a lock), and make the follow-up call `SwitchServer()`
when a core is running.

### H5. Speedtest / Mixedtest can never succeed on Departament CUSTOM (XRAY_JSON) nodes
`ServiceLib/Services/SpeedtestService.cs:75-102` + `ServiceLib/Services/CoreConfig/V2ray/CoreConfigV2rayService.cs:232-307` + `ServiceLib/Services/CoreConfig/V2ray/V2rayOutboundService.cs:303` + `ServiceLib/Global.cs:266-279` **[dep]**

The Departament change removed the `it.ConfigType != EConfigType.Custom` filter from `GetClearItem`
(`SpeedtestService.cs:75-79`, confirmed in the diff), so Custom rows now enter every test type. The
**batch** real-ping path was correspondingly fixed — `CoreConfigHandler.InjectCustomSpeedtestNodes`
(`Handler/CoreConfigHandler.cs:378-485`) grafts each Custom node's proxy outbound into the batch config.

The **per-node** path used by `ESpeedActionType.Speedtest` and `Mixedtest` was not.
`RunMixedTestAsync` (`SpeedtestService.cs:433`) calls the single-item
`CoreManager.LoadCoreConfigSpeedtest(ServerTestItem)` (`Manager/CoreManager.cs:1155-1175`), which routes
to `CoreConfigV2rayService.GenerateClientSpeedtestConfig(int port)` (`:232`). That method calls
`GenOutbounds()` (`:265`) → `BuildAllProxyOutbounds` → `BuildProxyOutbound` → `FillOutbound`
(`V2rayOutboundService.cs:60-313`). `FillOutbound`'s `switch (_node.ConfigType)` has **no `Custom`
case**, and then unconditionally executes:

```csharp
outbound.protocol = Global.ProtocolTypes[_node.ConfigType];   // V2rayOutboundService.cs:303
```

`Global.ProtocolTypes` (`Global.cs:266-279`) contains no `EConfigType.Custom` key, so this throws
`KeyNotFoundException` — which is caught and merely logged by `FillOutbound`'s own
`catch (Exception ex) { Logging.SaveLog(_tag, ex); }` (`V2rayOutboundService.cs:310-313`).

Net result: `GenerateClientSpeedtestConfig` returns `Success = true` with an outbound that is the raw
`V2raySampleOutbound` template (no server, no protocol). Either Xray refuses to start (→ the row shows
`ResUI.FailedToRunCore` at `SpeedtestService.cs:436`) or it starts and the download measures nothing.
Since the Departament subscription delivers exclusively Custom nodes, "Скорость" never works for the
product's own servers, and the failure is a swallowed log line.

Fix: mirror `InjectCustomSpeedtestNodes` in the single-node path, or make
`GenerateClientSpeedtestConfig(int port)` reject `EConfigType.Custom` up front so the row reports an
honest "not supported" instead of a bogus core start. Also make `Global.ProtocolTypes[...]` a
`TryGetValue` so an unmapped type fails loudly instead of into a swallowed catch.

### H6. Real-ping reports a healthy latency for a proxy that is returning error pages
`ServiceLib/Handler/ConnectionHandler.cs:69-99` **[upstream, on the dep default path]**

```csharp
await client.GetAsync(url, cts.Token).ConfigureAwait(false);   // :88 — response DISCARDED
timer.Stop();
oneTime.Add((int)timer.Elapsed.TotalMilliseconds);             // :90
```

The `HttpResponseMessage` is never inspected: no `EnsureSuccessStatusCode()`, no status check, and the
`catch` at `:95-97` is empty. `HttpClient.GetAsync` does not throw on 4xx/5xx. So a captive portal, a
Remnawave "app not supported" page, a 403 from a blocked/expired subscription, or any proxy error
response all produce a fast, *successful-looking* ping.

This value is what `RunAvailabilityCheck` (`:10-16`) surfaces after every connect
(`ViewModels/MainWindowViewModel.cs:848-851`, `:932-935`) and what
`SpeedtestService.DoRealPing` (`Services/SpeedtestService.cs:478-499`) writes into the server rows and
gates the speed test on (`:451`). A broken tunnel therefore reads "connected, 45 ms".

Fix: require `resp.IsSuccessStatusCode` (the default `SpeedPingTestUrl` is a 204 endpoint —
`Handler/ConfigHandler.cs:133-136`), and log rather than swallow.

---

## MEDIUM

### M1. `RefreshServersDispatcherAsync` does not actually await the refresh
`ServiceLib/ViewModels/MainWindowViewModel.cs:428-431` **[upstream helper, new dep call sites]**

```csharp
await Observable.Start(async () => await RefreshServers(), RxSchedulers.MainThreadScheduler);
```

`Observable.Start` has no `Func<Task>` overload that unwraps; the async lambda binds to
`Start<TResult>(Func<TResult>, IScheduler)` with `TResult = Task`, producing `IObservable<Task>`. Rx's
`GetAwaiter` yields the inner `Task`, which is then discarded. The `await` therefore completes as soon
as `RefreshServers()` hits its first incomplete await — not when it finishes.

Callers that assume the list is refreshed: `UpdateTaskHandler` (`:368`, then reads `_config.IndexId`
and decides whether to `Reload()`), `AddServerViaClipboardAsync` (`:509`),
`AddScanResultAsync` (`:595`), `DownloadImportedSubscriptionAsync` (`:646`), `Init` (`:339`, which
then enables the connect button). Identical in `master` (verified at `master:...:403-406`), so it is
inherited — but the Departament additions at `:509`, `:595`, `:646` are new places that depend on it.

Fix: `await Observable.StartAsync(RefreshServers, RxSchedulers.MainThreadScheduler)` (or
`await RxSchedulers.MainThreadScheduler.RunAsync(...)`).

### M2. `Init()` is fire-and-forget with no catch — one failure leaves Connect permanently disabled
`ServiceLib/ViewModels/MainWindowViewModel.cs:315, 318-346` **[dep-modified body]**

`_ = Init();` (`:315`) discards the task, and `Init` has no `try/catch`. Any throw from
`ConfigHandler.InitBuiltinDNS` (`:328`), `InitBuiltinFullConfigTemplate` (`:329`),
`ProfileExManager.Init` (`:330`), `CoreManager.Init` (`:331`), `CertPemManager.Init` (`:332`),
`StatisticsManager.Init` (`:337`) or `RefreshServersDispatcherAsync` (`:339`) skips
`SetReloadEnabled(true)` at `:345`. `BlReloadEnabled` stays `false` forever and the Connect affordance
is dead, with the exception unobserved (no `TaskScheduler.UnobservedTaskException` handler visible in
ServiceLib) and no message anywhere.

Fix: wrap the whole body, and put `SetReloadEnabled(true)` in a `finally`.

### M3. Ten Rx `Subscribe(async …)` handlers swallow every exception
`ServiceLib/ViewModels/MainWindowViewModel.cs:257, 262, 277, 285, 290, 295, 300-303, 308, 313` **[dep-added subscriptions at :285, :308, :313]**

`.Subscribe(async _ => await Reload())` and friends: the `Task` returned by the async lambda is
discarded by Rx (the delegate is bound as `Action<T>`, making the lambda effectively `async void`). A
throw inside `Reload()`, `SwitchServer()`, `UpdateSubscriptionProcess()` or
`ProfilesViewModel.SetDefaultServer()` is neither reported to the user nor logged. Combined with M2,
a DB error during `ConfigHandler.GetDefaultServer` produces a tap that does nothing at all.

Fix: wrap each handler body in try/catch → `Logging.SaveLog` + `NoticeManager.SendMessageEx`.

### M4. The Xray `api` inbound is grafted onto every Departament config for a feature that is switched off
`ServiceLib/Handler/CoreConfigHandler.cs:240, 253-306` + `ServiceLib/Services/CoreConfig/V2ray/V2rayInboundService.cs:133-173` + `ServiceLib/Manager/CoreManager.cs:55` **[dep]**

`GraftXrayApi` adds a mandatory `dokodemo-door` inbound bound to `AppManager.Instance.ApiPort`
(`Manager/AppManager.cs:41-48`) to every custom (Remnawave XRAY_JSON) run-config, and `GenApi` does
the same for typed nodes. Its only consumer is the Tier-2 hot swap, which is hard-disabled:

```csharp
private static readonly bool EnableHotSwapTier = false;   // CoreManager.cs:55
```

`ApiPort` is `Utils.GetFreePort(GetLocalPort(EInboundProtocol.api) + 100)` — and `GetFreePort`
(`Common/Utils.cs:751-771`) only *checks* the port, it never reserves it (classic TOCTOU), and its
fallback returns an ephemeral port from a `TcpListener` it immediately stops. If anything grabs that
port between the check and Xray's bind, Xray fails to start and **the whole connect fails** — for a
listener that nothing uses. Pure added failure surface with zero current benefit.

Fix: gate both `GraftXrayApi` and `GenApi` on `EnableHotSwapTier`.

### M5. Traffic stats silently read zero when the provider tags its outbound anything but `proxy*`
`ServiceLib/Services/Statistics/StatisticsXrayService.cs:96-100` + `ServiceLib/Handler/CoreConfigHandler.cs:130-132, 165-241` **[dep interaction]**

`ParseOutput` accumulates only outbounds whose stat key starts with `Global.ProxyTag` (`"proxy"`,
`Global.cs:77`):

```csharp
if (key.StartsWith(Global.ProxyTag)) { server.ProxyUp += …; server.ProxyDown += …; }
```

For a Departament custom node, `MergeAppInbounds` deliberately keeps the template's outbounds
**as-authored** (`CoreConfigHandler.cs:130-132, 162-163`) — the app never retags them.
`CoreManager.CaptureSwitchContext`'s own comment acknowledges this: *"the provider tag for a custom
node"* (`CoreManager.cs:536-537`). So any Remnawave template that names its outbound e.g. `VLESS-out`
or `remnawave` yields a permanently 0 KB/s speed widget and a flat "today's traffic", with no error.

Fix: resolve the live proxy tag once (the `_runningProxyTag` machinery already computes exactly this,
`CoreManager.cs:521-573`) and match against it, falling back to "everything that is not `direct`/`block`/`dns`/`api`".

### M6. Speedtest processes are killed but never disposed
`ServiceLib/Services/SpeedtestService.cs:287-291, 406-410, 467-472` + `ServiceLib/Manager/CoreManager.cs:1486-1489` **[upstream]**

Every finally block does `await processService?.StopAsync();` and stops there. `ProcessService`
implements `IDisposable` and owns a `System.Diagnostics.Process` (`Services/ProcessService.cs:5, 196-234`);
`StopAsync` only kills, it does not release the process handle or the redirected-stream resources. The
core lifecycle does it correctly (`CoreManager.cs:1234, 1242`), the speedtest paths do not. A mixed
test over 60 servers leaks 60 process handles per run.

Same class of leak at `CoreManager.RunProcessNormal:1486-1489` — when the just-started process has
already exited it `throw`s without disposing `procService`.

### M7. `ClientWebSocket` is aborted but never disposed on every reconnect
`ServiceLib/Services/Statistics/StatisticsSingboxService.cs:35, 43, 57-58, 94-95` **[dep-rewritten]**

`Init` does `webSocket?.Abort(); webSocket = new ClientWebSocket();` and the loop does
`webSocket.Abort(); webSocket = null;` on `Aborted`/`Closed`. `ClientWebSocket` is `IDisposable`;
`Abort()` tears down the connection but does not dispose the managed wrapper (which holds pooled
buffers and the underlying stream). The new lazy-reconnect design reconnects on every
connect/disconnect cycle, so this leaks per cycle. The pre-existing `Close()` (`:50-65`) has the same
gap.

Also: `webSocket` is written from `Close()` (any thread) and read/written by `Run()` (loop thread) with
no synchronisation; the NRE this can produce at `:92` is masked by the bare `catch {}` at `:135-137`.

### M8. A superseded switch leaves the UI stuck in "Connecting" for the full 12 s deadline
`ServiceLib/Manager/CoreManager.cs:276-279, 293-317` + `ServiceLib/ViewModels/MainWindowViewModel.cs:894-899, 912-921` **[dep]**

`AppEvents.CoreSwitchSettled` is documented as the completion signal for the seamless path
(`Events/AppEvents.cs:25-36`), but it is not published on several exits:
- `CoreManager.SwitchServer` `_switchSemaphore.WaitAsync(0)` fast-fail → `return false` (`:276-279`);
- the three "not a switch / shape changed" branches at `:293-299`, `:302-306`, `:311-317` (they run
  `LoadCoreInternal` and return without the publish; only the `:362-368` fallback publishes);
- `MainWindowViewModel.SwitchServer` `_reloadSemaphore.WaitAsync(0)` fast-fail (`:894-899`) and the
  two early `return`s at `:915` and `:920`.

The UI then holds "Connecting" until its own 12 s safety deadline.

### M9. Blocking `.Wait()` on the startup thread for a paged SQLite migration
`ServiceLib/Manager/AppManager.cs:136-139` **[upstream]**

```csharp
Task.Run(async () => { await MigrateProfileExtra(); }).Wait();
```

`MigrateProfileExtra` runs three paged migrations over the whole `ProfileItem` table
(`AppManager.cs:359-672`). `InitComponents` is called during app startup, so this blocks that thread
for the duration. It also wraps any failure in `AggregateException`, which propagates out of
`InitComponents` (no catch) — a single bad row aborts startup.

### M10. `DownloaderHelper` throws from library event handlers
`ServiceLib/Helper/DownloaderHelper.cs:44-50, 178-191` **[upstream]**

```csharp
downloader.DownloadFileCompleted += (sender, value) => { if (value.Error != null) throw value.Error; };
```

Throwing from an event handler does not propagate to the awaiting caller — it propagates into the
`Downloader` library's raise context. At best the error is swallowed (so
`DownloadService.DownloadFileAsync` reports success for a failed download); at worst it surfaces on a
thread-pool thread with no handler, which terminates the process in .NET. Either way, download failure
is not reliably observable by the caller.

### M11. `DepartamentApiClient` static `HttpClient` never refreshes DNS and inherits the app's own system proxy
`v2rayN.Desktop/Account/DepartamentApiClient.cs:24-33` **[dep]**

```csharp
private static readonly HttpClient _http = CreateClient();
… new HttpClient(new AuthMessageHandler(new HttpClientHandler())) { Timeout = 25s };
```

`HttpClientHandler` with no `PooledConnectionLifetime` pins DNS for the life of the process — a
backend IP change (Cloudflare rotation, VPS migration) is not picked up until restart. `HttpClientHandler`
also defaults to `UseProxy = true` with the **system** proxy, which this very app points at
`127.0.0.1:<socks>` in `ForcedChange`/`Pac` mode (`Handler/SysProxy/SysProxyHandler.cs:26-54`). So
account API calls are routed through the app's own tunnel: when the tunnel is up but broken, auth and
subscription sync fail — which then feeds **C1** and wipes the server list.

Fix: `SocketsHttpHandler { PooledConnectionLifetime = TimeSpan.FromMinutes(5), UseProxy = false }`.

### M12. Custom-node introspection does synchronous file I/O on the UI thread
`ServiceLib/Handler/Fmt/XrayJsonTemplateFmt.cs:56-71` + `ServiceLib/ViewModels/ProfilesViewModel.cs` (`let custom = … XrayJsonTemplateFmt.Introspect(t)` in the list-build query) **[dep]**

`IntrospectByAddress` calls `File.ReadAllText(path)` (`:64`) synchronously on a cache miss. It is
invoked from the LINQ projection that builds `ProfileItemModel`s, which runs inside
`RefreshServersDispatcherAsync` → `Observable.Start(…, RxSchedulers.MainThreadScheduler)`
(`MainWindowViewModel.cs:428-431`) — i.e. on the UI thread. After every subscription update every
custom node gets a **new** GUID-named file (`ConfigHandler.AddCustomServer:545`,
`$"{Utils.GetGuid()}{ext}"`), so the path-keyed cache (`XrayJsonTemplateFmt.cs:30`) misses for **all**
servers on every update: N synchronous disk reads on the UI thread. `SpeedtestService.GetClearItem:96`
hits the same method.

Secondary: `_cache` is a static `ConcurrentDictionary` with no eviction — entries for deleted profiles
accumulate for the process lifetime.

### M13. Two conflicting `PingMethod` defaults; the `Realping` one is dead code
`ServiceLib/Models/Configs/ConfigItems.cs:206-212` vs `ServiceLib/Handler/ConfigHandler.cs:124, 145-149` **[dep, both sides]**

`SpeedTestItem.PingMethod` has a property initializer `= nameof(ESpeedActionType.Tcping)`
(`ConfigItems.cs:212`, comment: *"Default = Tcping so a FRESH install pings successfully out of the box"*).
`InitConfig` then does `config.SpeedTestItem ??= new();` (`ConfigHandler.cs:124`) followed by:

```csharp
if (config.SpeedTestItem.PingMethod.IsNullOrEmpty())
{
    config.SpeedTestItem.PingMethod = nameof(ESpeedActionType.Realping);   // ConfigHandler.cs:148
}
```

Because the initializer already ran, `IsNullOrEmpty()` is never true — the `Realping` assignment (and
its "Android parity" comment) is unreachable. Two documented intentions directly contradict each
other and the reader cannot tell which is live.

### M14. `RunLoop` is fire-and-forget — an early throw leaves every row stuck on "Speedtesting"
`ServiceLib/Services/SpeedtestService.cs:14-22` **[upstream]**

```csharp
public void RunLoop(…) { Task.Run(async () => { await RunAsync(…); await ProfileExManager.Instance.SaveTo(); await UpdateFunc("", ResUI.SpeedtestingCompleted); }); }
```

No try/catch, task discarded. `GetClearItem` (`:70-147`) calls `AppManager.GetProfileItemsByIndexIdsAsMap`
(DB), `XrayJsonTemplateFmt.Introspect` (file I/O) and `UpdateFunc`; `RunAsync` (`:39-68`) dereferences
`_config.SpeedTestItem` (`:65`). A throw in any of them skips `SaveTo()` **and** the
`SpeedtestingCompleted` message, so the rows that `GetClearItem` already set to `ResUI.Speedtesting`
(`:124`, `:134`) stay in that state forever.

### M15. Real-ping retry re-tests every node, overwriting good results
`ServiceLib/Services/SpeedtestService.cs:229-236` **[upstream]**

```csharp
if (pageSizeNext > _config.SpeedTestItem.MixedConcurrencyCount)
    await RunRealPingBatchAsync(lstFailed, exitLoopKey, pageSizeNext);
else
    await RunMixedTestAsync(lstSelected, …);   // :235 — lstSelected, not lstFailed
```

The else-branch passes the full `lstSelected` instead of `lstFailed`, so nodes that already measured
fine are re-tested; a second, worse (or failed, `-1`) measurement then overwrites the good one via
`ProfileExManager.SetTestDelay` in `DoRealPing` (`:483`).

### M16. `GetTcpingTime` — unbounded DNS resolve and `.First()` on a possibly empty address list
`ServiceLib/Services/SpeedtestService.cs:537-565` **[upstream, now reachable for Custom nodes]**

```csharp
var ipHostInfo = await Dns.GetHostEntryAsync(url);   // :543 — no timeout/token
ipAddress = ipHostInfo.AddressList.First();          // :544 — throws on empty
```

The 5 s `CancellationTokenSource` at `:553` covers only `ConnectAsync`. A dead-DNS host blocks for the
OS resolver timeout (tens of seconds) inside a batch, and an `AddressList` with no entries throws
`InvalidOperationException` which the caller's `catch` logs and discards (`:180-183`, `:323-327`) —
leaving that row without a value. The dep TCP fallback (`:299-332`) uses the same helper.

### M17. `AuthTokenStore.Persist` is a non-atomic whole-file write
`v2rayN.Desktop/Account/AuthTokenStore.cs:188-200` **[dep]**

`File.WriteAllBytes(Utils.GetConfigPath(FileName), blob)` — a crash/power-loss mid-write leaves a
truncated blob. `Load` (`:164-186`) treats any decrypt/parse failure as "start fresh" (`catch { return
new StoreData(); }`), so the user is silently logged out and, via **C1**'s prune, may also lose their
subscriptions on the next sync. `ConfigHandler.SaveConfig` already uses the correct temp-file + `File.Move`
pattern (`ConfigHandler.cs:204-215`); this does not.

### M18. Failed sudo core start leaves a stale PID that a later teardown sudo-kills
`ServiceLib/Manager/CoreAdminManager.cs:32-59, 61-90` + `ServiceLib/Manager/CoreManager.cs:1444-1452` **[upstream]**

`CoreManager.RunProcess` sets `_linuxSudo = true` (`CoreManager.cs:1449`) *before* awaiting
`RunProcessAsLinuxSudo`. If that throws at `CoreAdminManager.cs:52-55` (process already exited),
`_linuxSudoPid` keeps its previous value and the `ProcessService` is leaked (never disposed). The next
`CoreStopInternal` sees `_linuxSudo == true` (`CoreManager.cs:1224`) and runs
`KillProcessAsLinuxSudo`, which executes `sudo -S <script> <stalePid>` (`CoreAdminManager.cs:76-80`) —
on a PID the OS may have reassigned to an unrelated process.

---

## LOW

### L1. `AppExitAsync` swallows a failed settings save
`ServiceLib/Manager/AppManager.cs:151-179` — `catch { }` at `:171` covers `SaveConfig`, `ProfileExManager.SaveTo`,
`StatisticsManager.SaveTo` and `CoreStop`. A write failure loses the session's settings with no trace.

### L2. `SysProxyHandler.UpdateSysProxy` always returns `true`
`ServiceLib/Handler/SysProxy/SysProxyHandler.cs:7-67` — returns `true` at `:66` even after the
`catch` at `:62-65` and even when no `switch` arm matched. Callers cannot distinguish "system proxy
set" from "silently failed". No current caller checks it, which is itself the reason a real failure is
invisible.

### L3. `SpeedtestService._lstExitLoop` is a static bag that only ever grows
`ServiceLib/Services/SpeedtestService.cs:10, 34-37, 42` — one GUID per run is added at `:42` and only
removed by `ExitLoop()`'s wholesale `Clear()` (`:30`). `ShouldStopTest` does an O(n) `All()` scan per
item per batch (`:36`). Also, `ExitLoop` clears **every** key, so cancelling one test stops all
concurrent ones.

### L4. `AppManager.ProfileModels` builds SQL by string concatenation
`ServiceLib/Manager/AppManager.cs:233-261` — `subid` is interpolated raw (`:249`) and `filter` is only
quote-stripped (`:253-257`), leaving `%`/`_` wildcards live and no parameterisation. Low risk today
(both values are locally generated / user-typed in a single-user app) but it is a real injection shape
one subscription-supplied string away from mattering.

### L5. `AuthTokenStore` "encryption at rest" is obfuscation
`v2rayN.Desktop/Account/AuthTokenStore.cs:206-236, 256-301` — AES-**CBC** with **no authentication tag**,
and the key is `SHA256("departament-vpn|auth|v1|" + MachineGuid)` where `MachineGuid`/`/etc/machine-id`
is readable by any local process. Any local process that can read the file can derive the key. The
class doc ("Encrypted-at-rest store", `:8-15`) over-claims; the real property is only "not portable to
another machine". Prefer DPAPI (`ProtectedData`) on Windows / AES-GCM elsewhere.

### L6. `await _updateFunc?.Invoke(...)` throws `NullReferenceException` when the delegate is null
`ServiceLib/Manager/CoreManager.cs:1357`, `ServiceLib/Services/ProcessService.cs:142`,
`ServiceLib/Manager/TaskManager.cs:99, 119`, `ServiceLib/Services/SpeedtestService.cs:587, 596`,
`ServiceLib/Handler/SubscriptionHandler.cs:7, 12, 29, 35, 47, 53-54, 58, 244, 248, 253, 256, 273`,
`ServiceLib/Manager/CoreAdminManager.cs:29` — `?.` yields a `null Task`, and `await null` throws.
Currently masked because every call site is wired, but `ProcessService.cs:142` sits inside a `catch`
block, so a null there converts a logged error into an unhandled throw.

### L7. `AccountSession.Wipe` stops the core without the user-stop flag
`v2rayN.Desktop/Account/AccountSession.cs:104-112, 118-131` — `await CoreManager.Instance.CoreStop()`
omits `byUser: true`, so the sticky `_userStopRequested` guard documented at `CoreManager.cs:72-74` is
not set. Safe today only because `RunningCoreType` is reset and the watchdog stopped; `AppExitAsync`
uses `byUser: true` for exactly this reason (`AppManager.cs:164-166`).

### L8. sing-box traffic samples are dropped, not deferred, while the window is hidden
`ServiceLib/Services/Statistics/StatisticsSingboxService.cs:106-109, 126-131` **[dep]** — the comment
justifying the skip cites cumulative counters (`StatisticsXrayService.cs:52-56`), which is correct for
Xray's `/debug/vars` (delta computed at `StatisticsXrayService.cs:114-121`) but **not** for the clash
`/traffic` websocket, which emits per-second **rates** (`:118-124` divides by 1000 and
`StatisticsManager.UpdateServerStat:113-119` accumulates them). Minimising the window therefore loses
that period's traffic accounting. Currently latent because `RunningCoreType` is set to the **main**
core (Xray) on the Departament path (`CoreManager.cs:224`), so this loop never activates.

### L9. `SanitizeBody` usually discards the whole server error message
`v2rayN.Desktop/Account/DepartamentApiClient.cs:436-452` — the filter drops any *line* containing
"token"/"authorization"/an http URL. Backend errors are typically single-line JSON, so one occurrence
of `token` blanks the entire body and `MapError` gets `null` — the user sees a bare status code.

### L10. `SubscriptionHandler.CreateDownloadHandler` discards the error-report task
`ServiceLib/Handler/SubscriptionHandler.cs:84-92` — `updateFunc?.Invoke(false, …)` inside the `Error`
handler is not awaited and its exceptions are unobserved, so subscription download errors may never
reach the message panel.

### L11. `SQLiteHelper.DisposeDbConnectionAsync` nulls non-nullable fields
`ServiceLib/Helper/SqliteHelper.cs:76-88` — sets `_db`/`_dbAsync` to `null`; any subsequent call on the
singleton NREs. Also runs two connections (`_db` sync at `:17`, `_dbAsync` at `:18`) against the same
file, which can produce `SQLITE_BUSY` if `CreateTable` ever overlaps async work.

---

## What I checked and found correct

- `_coreOpGate` / `_restartGate` / `_switchSemaphore` lock ordering in `CoreManager` is consistent with
  its documented invariants (`CoreManager.cs:57-66`); I found no lock-order inversion.
- `CoreStop`'s generation-bump + token-cancel + sticky `_userStopRequested` (`CoreManager.cs:1186-1206`,
  `ShouldAbortRecovery:831-836`) does correctly prevent an auto-restart from undoing a user disconnect.
- `ProcessService`'s `_stopping` / `_exitedRaised` guards (`ProcessService.cs:10-12, 100, 178-193`)
  correctly suppress a false crash on intentional teardown and fire `Exited` at most once.
- `ConfigHandler.AddSubItem(Config, SubItem)` (`ConfigHandler.cs:2021-2044`) copies only a whitelist,
  so `TaskManager`'s post-update `AddSubItem` (`TaskManager.cs:105-106`) does **not** clobber the
  `subscription-userinfo` metadata `SaveSubscriptionMetadata` just wrote — the guarding comment at
  `SubscriptionHandler.cs:280-285` holds.
- Speedtest run-configs (`configTest<guid>.json`) *are* cleaned up hourly by
  `TaskManager.cs:59` → `FileUtils.DeleteExpiredFiles(..., "Test")` (`FileUtils.cs:205-228`), which has
  its own catch so it cannot kill the scheduler loop.
- `JsonUtils` uses `WhenWritingNull` / `Never`, never `WhenWritingDefault`
  (`JsonUtils.cs:13-39`), so the new `= true` property initializers in `ConfigItems.cs` cannot
  resurrect a user's explicit `false`.
- `Global.Languages[5] == "ru"` (`Global.cs:512-522`) — the hardcoded index in
  `ConfigHandler.cs:111-113` is correct today (though index-based and fragile).
- `Mux4SboxItem.Protocol = string.Empty` really does gate mux off —
  `SingboxOutboundService.cs:369` guards on `Protocol.IsNotEmpty()`.
