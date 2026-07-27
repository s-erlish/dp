# Persistence hunt — state the user expects to be remembered but can lose

Scope: every write to durable storage on both clients.
PC = `/home/user/v2rayN` (ServiceLib `ConfigHandler`/`AppManager`/SQLite + `v2rayN.Desktop/Account/AuthTokenStore`).
Android = `/home/user/dp` (MMKV via `handler/MmkvManager.kt` + `auth/AuthTokenStore.kt`).

Every claim below is anchored to a line I read. No code was changed in this phase.

---

## Verdict on the owner's complaint

> «запускаешь приложение, а подписка добавлена из буфера / ты через аккаунт — и бывает, что сессия
> слетает и опять это окно приветствия»

The onboarding gate is `(_isEmpty && !_isLoggedIn)` — `v2rayN.Desktop/Views/MainWindow.axaml.cs:869`.
So the welcome screen requires **both** "no servers" **and** "logged out" to be true at once. There are
four independent, provable code paths that produce exactly that pair, and they are findings
**P1, P2, P3 and P5** below. The launch-time *flash* was already fixed (`AppManager.HasStoredProfiles`,
`AppManager.cs:216-234`); what remains is real destruction of persisted state, not a rendering race.

---

# PC — critical

### P1. A transient network failure at launch DELETES every account subscription and its servers
`v2rayN.Desktop/Account/SubscriptionSyncManager.cs:52-73` and `:124-133`

`ImportAll()` swallows both fetches:

```csharp
try { primary = await _api.GetPrimarySubscription(); } catch (ApiError) { }   // :55-60
try { all = (await _api.GetSubscriptionAll()).Items; } catch (ApiError) { all = new(); } // :64-70
```

If both fail, `BuildCandidates` (`:142-169`) yields nothing, the import loop never runs, `newMap` stays
empty — and then the cleanup loop runs unconditionally:

```csharp
foreach (var kv in managed)
    if (kv.Value.IsNotEmpty() && !newMap.Values.Contains(kv.Value))
        await ConfigHandler.DeleteSubItem(config, kv.Value);   // :129
AuthTokenStore.SetManagedGuids(newMap);                        // :133
```

`DeleteSubItem` (`ServiceLib/Handler/ConfigHandler.cs:2107-2124`) drops the `SubItem` row **and** calls
`RemoteServersViaSubid` → `delete from ProfileItem where subid = …` (`:2091`).

This runs on **every launch** with a saved session: `AccountViewModel` ctor `:417-427` →
`StartupLoad` `:440-445` → `RunSyncPhases` `:1144` → `AutoImportAndRefreshHome` `:1258`.

**Repro:** log in, get servers, quit. Relaunch with Wi-Fi off / backend unreachable / VPN-gated DNS.
Both calls throw → all account subscriptions and servers are deleted from SQLite → Home is empty.
The same happens whenever the account has momentarily no *active* subscription
(`primary.HasActiveSubscription()` false at `:149`) — an expired plan wipes local servers.

**Fix direction:** distinguish "the server said you have nothing" from "I could not ask". Only run the
prune when at least one fetch actually **succeeded**; on a thrown/failed fetch return the existing
managed guids untouched.

---

### P2. The session file is written non-atomically and a failed read is immediately made permanent
`v2rayN.Desktop/Account/AuthTokenStore.cs:162-200`

```csharp
private static StoreData Data() => _data ??= Load();          // :162
...
catch { return new StoreData(); }                             // :182-186  — silently "no session"
private static void Persist() { ... File.WriteAllBytes(Utils.GetConfigPath(FileName), blob); } // :194
```

Two defects compound:

1. **`Persist()` uses `File.WriteAllBytes`** — truncate-then-write, no temp+rename. The very same file
   `ConfigHandler.SaveConfig` (`ServiceLib/Handler/ConfigHandler.cs:203-215`) is careful to write to
   `*_temp` and `File.Move(..., true)`. A crash/power loss/OS shutdown during that write leaves a
   truncated blob.
2. **`Load()` catches everything and returns an empty store** — a truncated blob, a locked file (AV,
   backup, indexer), or an undecryptable blob all read as "user was never logged in".

Then the empty state is written back over the good file: `DeviceId()` (`:43-57`) calls `Persist()` when
`data.DeviceId` is blank, and `DeviceId()` is invoked on **every** API request
(`Account/DepartamentApiClient.cs:54`) and by `Global.SubscriptionHwidProvider`
(`v2rayN.Desktop/App.axaml.cs:43`). On a healthy read `DeviceId` is already set and nothing is written —
so the destructive write happens *only* on the failure path, which is precisely when it must not.

**Repro:** kill the process (or lose power) while the store is being persisted. Next launch: token,
profile and `ManagedGuids` are gone, `AccountSession.Seed()` (`Account/AccountSession.cs:39-42`) reports
LoggedOut, the shell shows the login/onboarding gate — and because `ManagedGuids` is now empty, the
account subscriptions are orphaned too.

**Fix direction:** temp-file + `File.Move(..., true)` in `Persist()`; in `Load()` separate
"file absent" (legitimately no session) from "file present but unreadable" — on the latter, do **not**
return an empty store and do **not** allow any later `Persist()` to overwrite the file.

---

### P3. The AES key is derived from a machine seed that can silently change — with no re-key path
`v2rayN.Desktop/Account/AuthTokenStore.cs:206`, `:256-301`

```csharp
private static byte[] Key => _key ??= SHA256.HashData(... "departament-vpn|auth|v1|" + MachineSeed());
private static string MachineSeed()
{
    var guid = ReadMachineGuid();
    _machineSeed = guid.IsNotEmpty() ? guid! : $"{Environment.MachineName}|{Environment.UserName}"; // :263
}
```

`ReadMachineGuid()` (`:268-301`) swallows every exception (`catch { }` at `:296`) and returns null.
One failed registry open on Windows (policy/permission hiccup), or `/etc/machine-id` briefly
unreadable on Linux, silently swaps the key to the `MachineName|UserName` seed. Decryption then throws
→ `Load()`'s blanket catch (`:182`) → empty store → P2's overwrite makes it permanent. Renaming the PC
has the same effect on the fallback seed.

**Fix direction:** treat a *decrypt failure on an existing file* as an error, not as "no session"; try
the alternate seed before giving up; never overwrite a blob you failed to decrypt.

---

### P4. A subscription refresh deletes the servers BEFORE parsing, so a garbage-but-non-empty body wipes them
`ServiceLib/Handler/ConfigHandler.cs:1871-1896`

```csharp
lstOriSub    = await AppManager.Instance.ProfileItems(subid);
activeProfile = lstOriSub?.FirstOrDefault(t => t.IndexId == config.IndexId);
await RemoveServersViaSubid(config, subid, true);          // :1877  ← delete first
var counter = 0;
if (Utils.IsBase64String(strData)) counter = await AddBatchServersCommon(...);   // :1881+
```

`SubscriptionHandler.ProcessDownloadResult` correctly bails on an **empty** body (`:242-246`), but a
*non-empty* body that parses to nothing still reaches `AddBatchServers`. Every parser then returns
`< 1` and nothing is re-added; the servers are already gone. `ret <= 0` only logs (`:260-264`).

**Repro:** refresh a subscription behind a captive portal / on a hijacked DNS / while the panel serves
an HTML maintenance page. HTTP 200, non-empty body, zero parsed nodes → that subscription's server list
is emptied and never restored. Combined with a logged-out clipboard user this is exactly the
"onboarding came back" report.

Android does **not** have this bug on the link-list path — `AngConfigManager.kt:379-382` only deletes
`if (configs.isNotEmpty())`. PC should adopt the same ordering.

**Fix direction:** parse into a staging list first; delete + insert only when the parse produced ≥1 node.

---

# PC — high

### P5. A corrupt config file silently resets EVERY preference, then overwrites the file with defaults
`ServiceLib/Handler/ConfigHandler.cs:19-36`

```csharp
var result = EmbedUtils.LoadResource(path);
if (result.IsNotEmpty()) { config = JsonUtils.Deserialize<Config>(result); }   // returns null on bad JSON
else { if (File.Exists(path)) { Logging.SaveLog("LoadConfig Exception"); return null; } }
config ??= new Config();                                                        // :36
```

`JsonUtils.Deserialize` swallows parse errors and returns `default` (`ServiceLib/Common/JsonUtils.cs:66-80`).
So a truncated/corrupt `guiNConfig.json` yields a brand-new `Config`: language, TUN mode, routing,
DNS, ports, `IndexId` (the selected server) and `SubIndexId` all reset to defaults — and the very first
`SaveConfig` (`AppManager.cs:161`, or `TaskManager.cs:45` twenty minutes later) writes those defaults
over the file, destroying the original irrecoverably.

The unreadable-file branch is treated *more* safely (abort with `return null`) than the corrupt-file
branch. That asymmetry is the bug.

**Fix direction:** on a non-null file whose deserialize fails, back the file up and abort (or refuse to
save) rather than silently running on defaults.

---

### P6. Ping/speed results and per-server sort exist only in memory between saves, and the only guaranteed flush is a clean exit
`ServiceLib/Manager/ProfileExManager.cs:117-127`, `ServiceLib/Manager/StatisticsManager.cs:51-64`

`ProfileExManager.SaveTo()` callers: `AppManager.cs:162` (exit), `TaskManager.cs:46` (every 20 min),
`SpeedtestService.cs:19` (end of a full test run). `StatisticsManager.SaveTo()` has exactly **one**
caller: `AppManager.cs:163` — the exit path. It is deliberately absent from the 20-minute
`TaskManager.ScheduledTasks` block (`TaskManager.cs:43-52`), which saves the config and ProfileEx but
not the statistics.

So all accumulated per-server traffic counters (today/total up/down) are lost on any non-clean stop:
crash, `taskkill`, OOM, power loss, or an OS shutdown that outruns the save.

**Fix direction:** add `StatisticsManager.SaveTo()` to the 20-minute block alongside the other two.

---

### P7. The shutdown save sequence is a single `try` — one early throw skips every save
`ServiceLib/Manager/AppManager.cs:151-179`

```csharp
try {
    await SysProxyHandler.UpdateSysProxy(_config, true);   // :157  ← throws on a locked registry / dbus hiccup
    AppEvents.AppExitRequested.Publish();
    await Task.Delay(50);
    await ConfigHandler.SaveConfig(_config);               // :161  ← never reached
    await ProfileExManager.Instance.SaveTo();              // :162
    await StatisticsManager.Instance.SaveTo();             // :163
    ...
} catch { }                                                // :171  ← silent
```

The one path that is supposed to guarantee persistence (P6) is itself skippable, and the failure is
swallowed with a bare `catch { }`.

**Fix direction:** wrap each step in its own try/catch and log; system-proxy cleanup must not gate saving.

---

### P8. `OnClosing` is `async void`, so an OS shutdown can kill the process mid-save
`v2rayN.Desktop/Views/MainWindow.axaml.cs:1859-1881`

```csharp
protected override async void OnClosing(WindowClosingEventArgs e)
{
    ...
    case WindowCloseReason.ApplicationShutdown or WindowCloseReason.OSShutdown:
        await AppManager.Instance.AppExitAsync(false);   // :1876
```

`e.Cancel` is not set, and Avalonia does not await an `async void` override — the close proceeds past
the first `await`. On a Windows/Linux session shutdown the OS may terminate the process before
`SaveConfig` / `SaveTo` complete. Same class as P6/P7: the "clean exit" is not actually guaranteed.

---

### P9. `ProfileExManager` mutates a non-thread-safe `Queue<string>` from many parallel test tasks
`ServiceLib/Manager/ProfileExManager.cs:9`, `:35-41`, `:106-109`

```csharp
private readonly Queue<string> _queIndexIds = new();                       // :9  — NOT thread-safe
private void IndexIdEnqueue(string indexId)
{ if (indexId.IsNotEmpty() && !_queIndexIds.Contains(indexId)) _queIndexIds.Enqueue(indexId); }  // :37-40
private ProfileExItem GetProfileExItem(string? indexId)
=> _lstProfileEx.FirstOrDefault(t => t.IndexId == indexId) ?? AddProfileEx(indexId);             // :108
```

`SetTestDelay/SetTestSpeed/SetTestMessage/SetTestIpInfo` are called from inside parallel `Task.Run`
bodies in `ServiceLib/Services/SpeedtestService.cs:171-187`, `:274-279`, `:316-331`, `:393-398`,
`:428-475`. Unsynchronised `Enqueue` + `Contains` over a plain `Queue<T>` can corrupt its internal
array, drop entries or throw. Independently, `GetProfileExItem` is a lock-free check-then-add: two
threads for the same `IndexId` create **two** `ProfileExItem` rows in the `ConcurrentBag`, and
`SaveQueueIndexIds` then persists whichever `FirstOrDefault` returns (`:56`) — the other writer's
delay/speed is silently dropped.

**Fix direction:** `ConcurrentQueue` + a `ConcurrentDictionary<string, ProfileExItem>` keyed by IndexId
(GetOrAdd), instead of bag + queue.

---

### P10. `InitApp()` failing exits the app with no message at all
`v2rayN.Desktop/Program.cs:77-80`, `ServiceLib/Manager/AppManager.cs:100-104`

If `guiNConfig.json` exists but cannot be read (locked/permissions), `LoadConfig` returns null →
`InitApp` returns false → `OnStartup` returns false → `Environment.Exit(0)`. The user double-clicks the
app and nothing happens, ever, with no dialog and no hint that a file is the problem.

---

# Both platforms — high

### B1. Every subscription refresh mints brand-new server ids, so ping results (and PC sort order) can never survive one
Android: `handler/AngConfigManager.kt:410` (`val key = Utils.getUuid()` per config) +
`handler/MmkvManager.kt:224-230` (`serverAffStorage.remove(guid)` for every server of the sub).
PC: `ServiceLib/Handler/ConfigHandler.cs:1176` (new `IndexId` per imported profile) +
`ServiceLib/Manager/ProfileExManager.cs:30` (`delete from ProfileExItem where indexId not in (select indexId from ProfileItem)` at startup).

PC explicitly carries traffic statistics across a refresh — `CloneServerStatItem` in the "Keep the last
traffic statistics" block (`ConfigHandler.cs:1948-1960`) — but does **not** do the same for
`ProfileExItem`, which holds `Delay`, `Speed`, `IpInfo` **and `Sort`** (the user's manual ordering).
Android has no carry-over at all.

**Result:** after any auto-update (default interval) or manual refresh, all latency numbers are blank
and — on PC — the user's manual server ordering is gone. `FindMatchedProfileItem` /
`findMatchedProfileKey` already exist and are used to re-map the *selected* server; the same mapping
should carry ProfileEx/affiliation rows.

---

### B2. The account import overwrites a user-renamed subscription on every launch
Android `auth/SubscriptionSyncManager.kt:46-48`; PC `Account/SubscriptionSyncManager.cs:96`

```kotlin
remarks = info.displayName?.ifBlank { null } ?: info.tariffDisplayName?.ifBlank { null } ?: "Departament VPN"
```
```csharp
item.Remarks = candidate.Remarks;
```

Unconditional. Contrast `AngConfigManager.kt:864-873`, which goes out of its way to preserve a
user-typed name on the manual path ("a name the user typed in SubEditActivity must never be clobbered").
Rename a Departament subscription in the app → next launch it reverts to the backend label.

---

# Android — critical

### A1. The auth/session MMKV is opened `SINGLE_PROCESS_MODE` but is touched from two processes
`auth/AuthTokenStore.kt:34-51`, `auth/KeystoreKeyProvider.kt:36`, `AngApplication.kt:25-27`,
`handler/AngConfigManager.kt:799`, `handler/SubscriptionUpdater.kt:275`

```kotlin
MMKV.mmkvWithID(ID, MMKV.SINGLE_PROCESS_MODE, cryptKey)   // AuthTokenStore.kt:40
MMKV.mmkvWithID(HOLDER_ID)                                // KeystoreKeyProvider.kt:36 — default = single-process
```

Every other store in the app is deliberately `MMKV.MULTI_PROCESS_MODE` (`MmkvManager.kt:35-41`). But:

* `AngApplication` sets `setDefaultProcessName("${ANG_PACKAGE}:bg")` (`AngApplication.kt:26`) and
  `RemoteWorkManagerService` is declared `android:process=":bg"` (`AndroidManifest.xml:338-341`), so
  `SubscriptionUpdater.UpdateTask.doWork()` runs in **`:bg`**.
* `doWork()` calls `AngConfigManager.updateConfigViaSub` (`SubscriptionUpdater.kt:275`), which calls
  `AuthTokenStore.deviceId()` at `AngConfigManager.kt:799` — gated on `SettingsManager.isSendHwid()`,
  whose default is **true** (`SettingsManager.kt:379-381`).
* The UI process reads/writes the same store on every login, profile refresh and account screen.

Two processes holding the same MMKV file in single-process mode is exactly the configuration Tencent
MMKV documents as unsafe: there is no inter-process file lock, so one process's mmap flush can drop the
other's entries. The token, the cached profile and `managed_guids_json` all live there.

**Repro:** be logged in, let a scheduled subscription update fire in `:bg` while the app is in the
foreground, or log in while a worker is running. Session state can be lost or rolled back on the next
cold start → login/onboarding screen with the servers still on disk.

**Fix direction:** open both `departament_auth` and `departament_keyholder` with
`MMKV.MULTI_PROCESS_MODE`, or keep HWID derivation out of the `:bg` worker entirely.

---

### A2. The account import can import nothing and then delete every managed subscription
`auth/SubscriptionSyncManager.kt:37-42` and `:67-75`, fed by `auth/AccountRepository.kt:93-96`

```kotlin
for (info in items) {
    val raw = info.subscription?.response ?: continue      // :38
    ...
}
for ((uuid, guid) in managed) {                           // :68
    if (!newMap.containsKey(uuid)) {
        SubscriptionUpdater.cancelOne(subId = guid)
        MmkvManager.removeSubscription(guid)              // :71  ← deletes the sub AND its servers
    }
}
```

`items` comes from `GET /client/subscription/all` only (`AccountRepository.kt:94`). The app's own DTO
documents that this endpoint does **not** return the connect payload:

> `auth/dto/SubscriptionDtos.kt:33-38` — "NOT present on /all items — only on the GET
> /client/subscription summary / connect payload… stays blank/null from /all."

So `info.subscription` is null for every item, every item `continue`s, `newMap` is empty, and the prune
loop removes everything. The desktop port fixed exactly this and left the diagnosis in a header comment
that was never carried back:

> `v2rayN.Desktop/Account/SubscriptionSyncManager.cs:14-19` — "KEY FACT (why the previous import
> fetched nothing): the connect URL lives ONLY on GET /client/subscription… importing from /all alone
> yields no URL and therefore no servers."

Secondary defect on the same line: it reads `info.subscription?.response` directly instead of the
tolerant `SubResponseWrapper.raw()` (`SubscriptionDtos.kt:126-131`), so the documented
`data.response` shape is missed as well.

**Fix direction:** port the PC's `BuildCandidates` — fetch `getPrimarySubscription()` for the real URL,
merge any `/all` item that carries its own, use `.raw()`, and never prune on an empty candidate set
(see P1).

---

# Android — high

### A3. The "testing" spinner is written to persistent storage, survives restarts, and feeds the destructive "remove invalid" action
`ui/MainActivity.kt:782-785`, `handler/MmkvManager.kt:259-266`, `:301-321`, `viewmodel/MainViewModel.kt:647-655`

```kotlin
private fun markAllServersTesting() {
    mainViewModel.serversCache.forEach { MmkvManager.encodeServerTestDelayMillis(it.guid, -2L) }  // :783
}
```

`-2L` is a pure UI sentinel (`ui/MainRecyclerAdapter.kt:208-213`: `val testing = delay == -2L`) but it is
persisted into `serverAffStorage` like a real result. Two consequences:

1. **Rows never stop spinning.** `markAllServersTesting()` marks *every* row, but the tests skip rows
   `resolvePingHostPort` cannot resolve — group/balancer entries and unparseable CUSTOM profiles
   (`MainViewModel.kt:236-263`, used at `:275`, `:342`, `:369`). Those rows keep `-2` **forever**,
   across restarts, because nothing ever overwrites them.
2. **They get deleted.** `MmkvManager.removeInvalidServer` (`:301-321`) removes anything with
   `testDelayMillis < 0L` — which includes `-2`. `MainViewModel.onTestsFinished()` calls it
   automatically when `PREF_AUTO_REMOVE_INVALID_AFTER_TEST` is on (`:649-651`), and the menu action at
   `MainActivity.kt:2340` does it on demand.

**Repro:** enable "remove unreachable after test", run "test all", background/kill the app before it
finishes (or simply have a PolicyGroup row in the list). Servers that were never actually tested are
deleted.

Related: `onTestsFinished()` is only reached via `MSG_MEASURE_CONFIG_FINISH` (`MainViewModel.kt:727-732`,
raised by `service/CoreTestService.kt:125-127`), i.e. only for the proxied-real-delay method — so with
TCP/HTTP/ICMP the auto-sort/auto-remove promises never run at all and the sentinels are never cleared.

**Fix direction:** keep "testing" in memory (a ViewModel set), never in MMKV; and make
`removeInvalidServer` match `testDelayMillis < 0 && != TESTING`.

---

### A4. "Delete all servers" also destroys the WebDAV backup credentials, the subscription order and the selection
`handler/MmkvManager.kt:287-293`

```kotlin
fun removeAllServer(): Int {
    val count = profileFullStorage.allKeys()?.count() ?: 0
    mainStorage.clearAll()          // :289
    profileFullStorage.clearAll()
    serverAffStorage.clearAll()
    return count
}
```

`mainStorage` is not a server store — it also holds `KEY_WEBDAV_CONFIG` (`:33`, written at `:709`),
`KEY_SUB_IDS` (`:32`, the subscription **order**, incl. the pinned-first arrangement) and
`KEY_SELECTED_SERVER`. `WebDavConfig` carries `baseUrl`, `username`, `password`
(`dto/entities/WebDavConfig.kt`), typed in `ui/BackupActivity.kt:292-308`.

So "удалить все серверы" (`MainActivity.kt:2302`) silently wipes the user's backup credentials.
`initSubsList()` (`:354-363`) does rebuild `KEY_SUB_IDS` from `subStorage.allKeys()`, but in arbitrary
order — the user's arrangement is gone.

**Fix direction:** remove only the `SUB_SERVERS_*` keys and `KEY_SELECTED_SERVER`, never `clearAll()` on
a shared store.

---

### A5. A bad-but-JSON-shaped subscription body deletes the servers before anything is parsed
`handler/AngConfigManager.kt:605-637`

```kotlin
if (serverList.isNotEmpty()) {
    val removedSelected = getRemovedSelectedProfile(subid, append)
    if (!append) { MmkvManager.removeServerViaSubid(subid) }   // :613  ← delete first
    var count = 0
    for (srv in serverList.reversed()) {
        val config = CustomFmt.parse(rawConfig) ?: continue    // :620  ← may skip every element
        ...
    }
    return count                                               // may be 0
}
```

The link-list path is correctly guarded (`:379-382` deletes only when `configs.isNotEmpty()`), but the
XRAY_JSON path — which is what the Remnawave panel actually serves for these subscriptions — deletes on
`serverList.isNotEmpty()` and only *then* discovers that no element parses. Result: zero servers, none
restored. Same class as **P4**, narrower trigger.

---

# Android / PC — medium

### M1. Failing to unseal the MMKV crypt key falls back to a plaintext open of an encrypted file, permanently
`auth/KeystoreKeyProvider.kt:34-53`, `auth/AuthTokenStore.kt:36-51`

If the Keystore entry for `departament_auth_aes` is lost (credential reset, Keystore corruption,
some OEM upgrade paths), `getOrCreateKey()` (`:55-69`) generates a **new** key, `unseal` throws,
`getOrCreateCryptKey()` returns null (`:50-52`), and `AuthTokenStore.openStore()` falls back to
`MMKV.mmkvWithID(ID)` — no crypt key — against a file that *is* encrypted. The session reads as empty.

There is no self-heal: the holder MMKV still contains the old ciphertext sealed under an alias that now
holds a different key, so every subsequent launch takes the same failing branch. The session is gone
permanently, silently.

**Fix direction:** when `unseal` fails, clear the holder's `iv`/`cipher` and re-seal a fresh secret
(the old store is unrecoverable anyway), so at worst the user re-logs in once instead of every launch.

---

### M2. A subscription refresh writes back a stale `SubscriptionItem`, clobbering concurrent user edits
`handler/AngConfigManager.kt:729-731` → `:765` → `:875`

`updateConfigViaSubAll()` snapshots every `SubscriptionItem` via `decodeSubscriptions()` (`:729`), then
`updateConfigViaSub` performs a network fetch of up to 15 s + a 15 s retry (`:801-831`) and finally
writes the **whole snapshot object** back: `MmkvManager.encodeSubscription(it.guid, it.subscription)`
(`:875`, and `:841`). Anything the user changed in `SubEditActivity` during that window (name, filter,
user-agent, enabled, auto-update, interval — `ui/SubEditActivity.kt:125-157`, saved at `:182`) is
overwritten by the pre-fetch values.

PC solved exactly this and documented it:
> `ServiceLib/Handler/SubscriptionHandler.cs:283-290` — "Re-reads the row first so a concurrent writer
> (e.g. TaskManager's UpdateTime) is not clobbered."

`auth/SubscriptionSyncManager.kt:45-60` has the same shape.

**Fix direction:** re-read the item immediately before the write-back and merge only the fields this
refresh owns (traffic/expiry/directives/`lastUpdated`).

---

### M3. Ping results are read-modify-written from two processes with no synchronisation
`handler/MmkvManager.kt:259-266`

```kotlin
fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
    val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()   // read
    aff.testDelayMillis = testResult                                         // modify
    serverAffStorage.encode(guid, JsonUtil.toJson(aff))                      // write
}
```

Writers live in **different processes**: the UI process (`MainViewModel.kt:279`, `:350`, `:374`;
`MainActivity.kt:783`) and `CoreTestService`, declared `android:process=":RunSoLibV2RayDaemon"`
(`AndroidManifest.xml:258-266`), writing at `service/CoreTestService.kt:121`. MULTI_PROCESS_MODE makes
individual key writes safe but not read-modify-write sequences — one side's result is dropped. The
comment at `MainActivity.kt:776-781` already concedes the ordering is load-bearing, which is the symptom.

---

### M4. Raw server templates are never deleted — unbounded MMKV growth
`handler/MmkvManager.kt:192-212`, `:219-234`, `:287-293` vs `:329-331`

`encodeServerRaw` writes into `serverRawStorage` (`ID_SERVER_RAW`, `:24`) for every imported
CUSTOM/XRAY_JSON node (`AngConfigManager.kt:625`, `:650`, `:664`). None of `removeServer`,
`removeServerViaSubid` or `removeAllServer` ever touch `serverRawStorage`. Since each refresh mints new
guids (B1), every auto-update leaks one raw template per server, forever.

---

### M5. `DeleteSubItem` mutates `config.SubIndexId` without saving
`ServiceLib/Handler/ConfigHandler.cs:2117-2121`

```csharp
if (item.Id == config.SubIndexId) { config.SubIndexId = subs.LastOrDefault()?.Id; }   // no SaveConfig
```

Every sibling mutator saves (`SetDefaultServerIndex` at `:400-402`). Here the change survives only until
the next unrelated `SaveConfig` or a clean exit — and given P7/P8, it may not be written at all, leaving
`SubIndexId` pointing at a deleted subscription.

---

### M6. Migration guards are check-then-act and run in every process
`handler/SettingsManager.kt:44-50`, `:746-786`, `AngApplication.kt:32-47`

`AngApplication.onCreate()` runs in all three processes (main, `:bg`, `:RunSoLibV2RayDaemon`) and each
calls `SettingsManager.initApp()`. `migrateServerListToSubscriptions()` guards on a boolean
(`:748-751`) that is read and written non-atomically, and its body is a **full overwrite**:
`MmkvManager.encodeServerList(serverGuids, subId)` (`:783`). If two processes start close enough
together on a first-run-after-upgrade, the second can rewrite a server list from the legacy snapshot,
dropping anything added in between. Narrow window, destructive outcome.

---

## What I verified as SAFE (so it does not get "fixed" later by accident)

- Selected server persists immediately on both platforms: Android `MmkvManager.setSelectServer`
  (`:72-74`, mmap-backed, multi-process); PC `ConfigHandler.SetDefaultServerIndex` calls `SaveConfig`
  synchronously (`ConfigHandler.cs:400-402`).
- Selection is re-matched across a subscription refresh rather than reset: Android
  `resolveSelectedKey`/`findMatchedProfileKey` (`AngConfigManager.kt:437-529`), PC
  `FindMatchedProfileItem` (`ConfigHandler.cs:1938-1946`).
- A 401 on a non-identity endpoint does **not** wipe the session on either platform — only `getMe`
  does (`auth/AccountRepository.kt:66-82`; `Account/AccountRepository.cs:59-80`), and both carry the
  invariant in a comment. `AccountRepository.guard` rethrows `CancellationException` instead of turning
  a closed screen into a fake network failure (`AccountRepository.kt:44-49`).
- Logout keeps the stable device id and drops only session data on both platforms
  (`auth/AuthTokenStore.kt:139-144`; `Account/AuthTokenStore.cs:145-156`).
- `AccountCache` is process-lifetime only by design and cannot lose durable state
  (`auth/AccountCache.kt`; `Account/AccountCache.cs`).
- PC preference screens write through to `ConfigHandler.SaveConfig` on change rather than on close
  (`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:255`, `:277`, `:293`, `:299`, `:332`, `:421`, `:436`,
  `:465`, `:493`; `Views/PingSettingsPage.axaml.cs:69-80`; `Views/DnsSubView.axaml.cs:117`;
  `Views/PerAppProxyPage.axaml.cs:162`).
- Android preferences go straight to MMKV through `MmkvPreferenceDataStore`, so there is no
  SharedPreferences/MMKV split-brain (`helper/MmkvPreferenceDataStore.kt:17-73`).
- PC subscription refresh aborts on an **empty** download before touching stored servers
  (`SubscriptionHandler.cs:242-246`) — only the non-empty-but-unparseable case is unsafe (P4).
- I found **no** renamed persistence key without a migration. The `_v2` suffixes on
  `pref_use_hev_tunnel_v2` / `pref_hev_tunnel_rw_timeout_v2` (`AppConfig.kt:81`, `:83`) match their XML
  keys exactly (`res/xml/pref_settings.xml:143`, `:156`), and the two real migrations are both guarded
  and idempotent (`SettingsManager.kt:714-736`, `:746-786`).

---

## Suggested order of work

1. **P1 + A2** — the two prune loops that delete subscriptions on a failed/empty fetch. Same fix shape
   on both platforms; this is the owner's bug.
2. **P2 + P3 + A1 + M1** — make the session store durable: atomic write, no fail-open reset, correct
   MMKV process mode, re-key path.
3. **P4 + A5** — never delete servers before a parse has produced at least one node.
4. **A3 + A4** — stop persisting the `-2` UI sentinel; stop `clearAll()`-ing a shared store.
5. **P5 / P6 / P7 / P8** — config-corruption safety and a persistence path that does not depend on a
   clean exit.
6. **B1 + B2 + M2 + M3 + P9** — carry ping/sort across refreshes, stop clobbering user-entered names,
   fix the read-modify-write races.
