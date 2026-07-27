# PC cold-start sweep — "UI decided from a default before the truth loaded"

Scope: `v2rayN.Desktop/Views/MainWindow.axaml.cs`, all `v2rayN.Desktop/ViewModels/*.cs`,
`v2rayN.Desktop/Account/*`, and the `ServiceLib` startup path (`Program.cs` → `App.axaml.cs` →
`AppManager.InitApp/InitComponents` → `MainWindowViewModel.Init` → `ProfilesViewModel.RefreshServersBiz`).

Method: for every piece of state that drives what the user sees, ask (a) what is its value in the
first frame, (b) can that value render something FALSE, (c) does it survive a restart and is it
written at the right moment, (d) what happens when the load fails/is slow/errors, (e) is there a
rebuild that briefly publishes an empty collection.

Findings are ordered by severity. Every claim below is anchored to a `file:line` that was read.

---

## Launch-path state table (what the first frame actually reads)

| State | First-frame source | Truth source | Verdict |
|---|---|---|---|
| has-servers / is-empty | `AppManager.HasStoredProfiles()` sync snapshot (`MainWindow.axaml.cs:215`) | `ProfilesViewModel.ProfileItems` after `HasLoadedServers` (`ProfilesViewModel.cs:414`) | correct in principle — but the truth source is **filtered** (F3) |
| signed-in | `AccountSession.Seed()` → `AuthTokenStore.IsLoggedIn()` (`AccountSession.cs:39-42`) | same (local) | OK |
| startup-loading gate | `AccountViewModel` ctor, before any await (`AccountViewModel.cs:327-329`) | — | OK |
| `MainWindow._isLoggedIn` | field default `false` (`MainWindow.axaml.cs:68`) | `SetupHome` subscription (`:1065`) | masked by `_isStartupLoading`; latent |
| plan / expiry / balance | cached `AuthTokenStore.GetUser()` (`AccountViewModel.cs:332`) | `GET /client/auth/me` | OK (cache-first), but see F2 |
| subscription list | `Subscriptions = new()` empty | `/subscription/all` + primary | **renders "нет подписки" — F2** |
| running/stopped | `IsCoreRunning()` probe in `HomeViewModel` ctor (`:172`) | live events | OK |
| language | `L.Init()` before window build (`App.axaml.cs:38`) | config | OK |
| theme | `ApplyTheme(...)` before window build (`App.axaml.cs:33`) | config | OK |
| window size | `WindowBase.OnLoaded` restores persisted (`WindowBase.cs:41-61`) | config | restored — but **layout mode is not** (F5) |
| compact/wide layout | hardcoded `_compactMode = true` (`MainWindow.axaml.cs:33`) | window Bounds | **F5** |
| TUN / "Режим" row | `StatusBarViewModel` ctor downgrade (`StatusBarViewModel.cs:147-157`) | config | **F4 — and the downgrade is persisted** |
| pinned subscription order | `Profiles.SubItems` (async, unwatched) (`HomeViewModel.cs:691`) | DB `SubItem.Pinned` | **F6** |
| selected tab / rail collapsed | hardcoded (`:59`, `:947`) | never persisted | F9 (low) |

---

## F1 — CRITICAL: a network failure at launch permanently DELETES the account's subscription and all its servers

`v2rayN.Desktop/Account/SubscriptionSyncManager.cs:46-74` and `:124-133`.

```csharp
// SubscriptionSyncManager.cs:52-70
PrimarySubscriptionDto? primary = null;
try { primary = await _api.GetPrimarySubscription(); }
catch (ApiError) { /* fall through */ }

List<SubInfoDto> all;
try { all = (await _api.GetSubscriptionAll()).Items; }
catch (ApiError) { all = new List<SubInfoDto>(); }
```

```csharp
// SubscriptionSyncManager.cs:124-133
// Drop any previously managed subscription whose guid is not in the freshly imported set.
foreach (var kv in managed)
{
    if (kv.Value.IsNotEmpty() && !newMap.Values.Contains(kv.Value))
    {
        await ConfigHandler.DeleteSubItem(config, kv.Value);
    }
}
AuthTokenStore.SetManagedGuids(newMap);
```

`DepartamentApiClient` maps **every** transport failure to an `ApiError` subclass —
`TaskCanceledException`/`OperationCanceledException` → `ApiError.TimeoutError`,
`HttpRequestException` → `ApiError.NetworkError`, and every non-2xx status →
`ApiError.*` (`DepartamentApiClient.cs:402-429`). So both `catch (ApiError)` blocks above swallow
offline, DNS failure, TLS failure, the 25 s client timeout (`DepartamentApiClient.cs:30`), 500, 502,
503 and 401 alike.

When both calls fail: `primary == null`, `all == []` → `BuildCandidates` returns nothing
(`:142-169`) → `newMap` stays empty → the loop at `:125` deletes **every** managed subscription.
`ConfigHandler.DeleteSubItem` does `SQLiteHelper.DeleteAsync(item)` **and**
`RemoveServersViaSubid(config, id, false)` (`ConfigHandler.cs:2114-2115`) — the servers are gone
from the DB, not just from the view.

Reproduction (this is the owner's complaint, in its worst form):
1. Sign in, let the Departament subscription import; servers appear on Home.
2. Quit. Go offline (or wait for one of the documented VPS network outages).
3. Launch the app. `AccountViewModel` ctor → `_ = Task.Run(StartupLoad)` (`AccountViewModel.cs:426`)
   → `RunSyncPhases(includeSubFetch:false)` → `AutoImportAndRefreshHome` (`:1256-1261`) →
   `_repo.AutoImportSubscriptions()` → `SubscriptionSyncManager.ImportAll()`.
4. Both GETs fail → subscription + servers deleted → `RequestHomeServerRefresh()` (`:1260`) →
   `ProfileItems` empty with `HasLoadedServers == true` → `HomeViewModel.IsEmpty = true`
   (`HomeViewModel.cs:552`) → `MainWindow.ApplyShellVisibility` shows the onboarding welcome gate
   (`MainWindow.axaml.cs:867-870`).
5. Go back online. The servers do not come back until a later successful import — and `_config.IndexId`
   now points at a deleted profile, so the previously-selected server is lost too.

Note the failure is also **silent**: `ImportAll` returns a successful (empty) list, so
`import.OnFailure(Report)` at `:1259` never fires for the deletion itself.

Fix direction: only reconcile deletions from an **authoritative** fetch. `ImportAll` must distinguish
"the server told us this subscription is gone" from "we could not reach the server". Concretely:
have `ImportAll` propagate/flag the failures instead of swallowing them, and skip the `:124-133`
prune entirely unless **both** `GetPrimarySubscription` and `GetSubscriptionAll` completed
successfully. A prune must never run off a default-empty list.

---

## F2 — HIGH: the Account hero resolves to «У вас нет подписки» whenever the profile is cached but the subscription list has not loaded

`v2rayN.Desktop/ViewModels/AccountViewModel.cs:2170-2200`.

```csharp
var coldLoading = _pendingFirstLoad || IsLoading;
var subsNotEmpty = Subscriptions.Count > 0;
if (subsNotEmpty)            { active   = true; }
else if (coldLoading && Profile == null) { skeleton = true; }
else if (Error != null && !_hasSubData)  { error    = true; }
else                          { empty    = true; }   // ← «У вас нет подписки»
```

The skeleton branch is gated on `Profile == null`. But the ctor deliberately seeds the profile from
the token store for a returning user (`AccountViewModel.cs:330-333`), so `Profile` is **never** null
on a cold start with a session. Result: from the very first `Recompute()` (`:415`) until
`/subscription/all` lands, the VM's hero state is `ShowEmpty` — a positive claim ("you have no
subscription") derived purely from an unloaded collection.

This is masked in the happy path by the sync overlay, but it becomes visible on two real paths:

* **Watchdog path.** `StartupLoad` drops the gate after 30 s when local servers exist
  (`AccountViewModel.cs:451-470`):
  ```csharp
  if (hasLocalServers) { IsStartupLoading = false; } else { SyncFailed = true; }
  Recompute();
  ```
  The restore is still running, `Subscriptions` is still empty, `Error` is still null → the Account
  tab paints «У вас нет подписки» + «Купить подписку» to a paying subscriber whose backend is merely
  slow.
* **Retry path.** `Retry()` (`:1324-1333`) sets `_pendingFirstLoad = true`, then `ClearError()`, then
  `Recompute()` — clearing the error before the reload starts. With `Subscriptions` still empty and
  `Profile` non-null, the honest error surface ("не удалось загрузить" + Повторить) flips to
  «У вас нет подписки» for the entire duration of `LoadAll()`, then flips back to the error. The
  user watches their subscription "disappear" every time they tap Повторить.

Fix direction: gate the skeleton on "no subscription result has landed yet" — i.e.
`coldLoading && !_hasSubData` — not on `Profile == null`. `_hasSubData` already exists and already
means exactly "a subscription list actually came back" (`:608-613`). `Profile` is a cache, not a
load-completion signal, and must not be used as one.

---

## F3 — HIGH: `IsEmpty` is computed from a subscription-FILTERED server list, so a stale `SubIndexId` shows the onboarding gate to a user who has servers

Three facts that combine:

1. `ProfilesViewModel.RefreshServersBiz` loads the list **filtered by `_config.SubIndexId`**
   (`ProfilesViewModel.cs:393`) → `AppManager.ProfileModels` appends `and a.subid = '<subid>'`
   (`AppManager.cs:285-288`).
2. It then sets `HasLoadedServers = true` unconditionally (`ProfilesViewModel.cs:414`), so the
   filtered count is treated as the whole truth.
3. `HomeViewModel.ReconcileGroups` reads that count as the fact
   (`HomeViewModel.cs:550-552`):
   ```csharp
   var loaded = Profiles?.HasLoadedServers == true;
   HasServers = loaded ? count > 0 : _storedServersAtLaunch == true;
   IsEmpty    = loaded ? count == 0 : _storedServersAtLaunch == false;
   ```
   and `MainWindow` turns `IsEmpty` into the onboarding gate (`MainWindow.axaml.cs:867-870`).

Meanwhile the launch snapshot the shell trusts for the first frame counts **all** profiles with no
filter: `select count(*) from ProfileItem` (`AppManager.cs:227`).

So the first frame (unfiltered snapshot = "has servers") and the settled frame (filtered count = 0)
can disagree, and the app visibly crossfades from the working shell into the welcome screen a second
after launch — the exact symptom the owner reported, from a different cause than the one already fixed.

`SubIndexId` is persisted (`Config.cs:9`) and is set to a **non-empty** value behind the user's back
by `ConfigHandler.DeleteSubItem` (`ConfigHandler.cs:2117-2121`):

```csharp
if (item.Id == config.SubIndexId)
{
    var subs = await AppManager.Instance.SubItems();
    config.SubIndexId = subs.LastOrDefault()?.Id;   // ← now filters the Home list to ONE sub
}
```

`ConfigHandler.DeleteSubItem` is reachable from the Incy UI (`SubscriptionMetaView.axaml.cs:647`,
the per-group delete button) and from F1's prune loop. The Incy shell has no group selector, so once
`SubIndexId` is stuck on a subscription, nothing in the redesigned UI can clear it; on the next launch
`RefreshSubscriptions` re-selects that same sub and leaves `SubIndexId` set
(`ProfilesViewModel.cs:433-435` + the `_config.SubIndexId != y.Id` guard at `:122`).

Fix direction: the Home shell is a whole-account view — `HomeViewModel`/`ReconcileGroups` must answer
"is this user empty?" from an **unfiltered** count, not from whatever group filter the legacy list VM
happens to hold. Either read the unfiltered list for the Home projection, or have `RefreshServersBiz`
record `HasLoadedServers` together with the filter it applied so consumers can tell "0 in this group"
from "0 in total".

---

## F4 — HIGH: the TUN preference is silently downgraded at every launch and then written back to disk, permanently erasing the user's choice

`ServiceLib/ViewModels/StatusBarViewModel.cs:145-158`:

```csharp
_tunRequested = _config.TunModeItem.EnableTun;
if (_config.TunModeItem.EnableTun && AllowEnableTun()) { EnableTun = true; }
else { _config.TunModeItem.EnableTun = EnableTun = false; }   // ← writes the LIVE config
```

`AllowEnableTun()` (`:559-574`) returns `Utils.IsAdministrator()` on Windows and
`AppManager.Instance.LinuxSudoPwd.IsNotEmpty()` on Linux/macOS. `LinuxSudoPwd` is a plain property
with no initializer (`AppManager.cs:50`) and is assigned in exactly one place — after the user types a
sudo password (`v2rayN.Desktop/Views/StatusBarView.axaml.cs:212`). It is therefore **always empty at
process start**, so on Linux/macOS this branch downgrades on *every* launch; on Windows it downgrades
on every non-elevated launch.

The downgrade is not merely in-memory. `AppManager.AppExitAsync` calls
`ConfigHandler.SaveConfig(_config)` on every exit (`AppManager.cs:161`), and every settings toggle
calls it too (`SettingsViewModel.cs:299`). The `false` is persisted; `_tunRequested` (the recorded
intent, `:134`) is never written back.

User-visible consequence, all from a transient runtime capability rather than the stored preference:
`SettingsViewModel.LoadFromConfig` derives the «Режим» row from `StatusBarViewModel.Instance.EnableTun`
(`SettingsViewModel.cs:172-173`), so the row reads «Прокси» even though the user selected TUN. Turn
TUN on, restart, and the setting is gone — from the UI *and* from `config.json`.

Note `SettingsViewModel.SetTunMode` writes `_config.TunModeItem.EnableTun = enable` directly and
deliberately skips the elevation path (`SettingsViewModel.cs:324-345`), which makes the round-trip
loss guaranteed rather than occasional.

Fix direction: keep `TunModeItem.EnableTun` as the persisted **intent** and carry the effective
(possibly downgraded) state in a separate runtime field. `TunRequestedButUnavailable`/`_tunRequested`
already model the intent — they just must not be shadowed by an overwrite of the persisted field.

---

## F5 — MEDIUM: the compact/wide layout is chosen from a hardcoded default, so a wide-window user gets a compact first frame and a crossfade on every launch

`MainWindow.axaml.cs:33` — `private bool _compactMode = true;  // старт компактный (дефолт 372×630 < 760)`
— and `:239` — `ApplyLayoutMode(_compactMode);` runs in the ctor, before any real size is known.
`:243` then subscribes to `BoundsProperty`; the ctor-time emission is `0` and is discarded by the
`width <= 0` guard in `UpdateLayoutMode` (`:695-707`).

The real size arrives later: `WindowBase.OnLoaded` restores the persisted `WindowSizeItem`
(`WindowBase.cs:41-61`). When that is ≥ 760 wide, the Bounds watcher fires a **second**
`ApplyLayoutMode(false)` — this time with `_layoutInitialized == true`, so it takes the animated
branch and crossfades `contentArea` (`MainWindow.axaml.cs:757-780`, `:783-801`). It also tears down
and rebuilds the Home binding (`BindActiveHome`, `:495-518`) and re-runs `ShowTab`.

So every launch of a wide window: compact bottom-nav layout paints first, then morphs. The persisted
size is available synchronously in the ctor via
`ConfigHandler.GetWindowSizeItem(AppManager.Instance.Config, GetType().Name)`
(`ConfigHandler.cs:2795-2804`) — the same call `WindowBase.OnLoaded` already makes — so the layout
mode can be seeded from a fact instead of from a default.

---

## F6 — MEDIUM: pinned-subscription ordering is decided from an unloaded cache and never re-decided

`HomeViewModel.BuildGroupPlan` (`HomeViewModel.cs:684-694`):

```csharp
Pinned = Profiles?.SubItems.FirstOrDefault(s => s.Id == g.Key.Key)?.Pinned ?? false,
```

Two separate problems:

* **Cold start race.** `SubItems` is filled by `ProfilesViewModel.RefreshSubscriptions`
  (`ProfilesViewModel.cs:425-436`), kicked off fire-and-forget from `ProfilesViewModel.Init`
  (`:270`, `:279`). `ReconcileGroups` runs off `ProfileItems.CollectionChanged`
  (`HomeViewModel.cs:139`, `:502`), driven by `MainWindowViewModel.Init` → `RefreshServersDispatcherAsync`
  (`MainWindowViewModel.cs:339`). Nothing orders the two. If the profile load wins,
  `SubItems` is still empty, every group reads `Pinned == false`, and — because `HomeViewModel`
  never subscribes to `SubItems` — the order is **never corrected** for the rest of the session.
  `RefreshSubscriptions` also does `SubItems.Clear(); SubItems.AddRange(...)` (`:430-431`), so even a
  well-timed read can land inside the empty window.
* **Pin toggle has no live effect.** `SubscriptionMetaView.OnPinClick` (`:583-609`) flips
  `sub.Pinned`, persists it with `SQLiteHelper.UpdateAsync(sub)`, and updates only the local icon
  tint. It never refreshes `ProfilesViewModel.SubItems` and never triggers a reconcile, so the group
  does not move to the top — and on the next launch it only moves if the race above happens to go the
  right way.

Fix direction: read `Pinned` from the same load that produces the groups (or re-run `ReconcileGroups`
when `SubItems` changes), and have the pin toggle refresh the in-memory cache + reconcile.

---

## F7 — MEDIUM: window size and app-exit handling are registered inside `WhenActivated`, which this codebase documents as being torn down at runtime

`MainWindow.axaml.cs:335-345` registers both:

```csharp
AppEvents.AppExitRequested.AsObservable()...Subscribe(_ => StorageUI())        // :335-339
AppEvents.ShutdownRequested.AsObservable()...Subscribe(Shutdown)               // :341-345
```

inside `this.WhenActivated(disposables => …)` (`:303`).

The repository's own comments state that handlers registered there were observed disappearing on
window deactivation, which is precisely why the clipboard/scan interactions were moved out to a
window-lifetime `CompositeDisposable` (`MainWindow.axaml.cs:110-113` and `:970-975`:
«Bug8: интеракции буфера/скана регистрируются на ВРЕМЯ ЖИЗНИ окна (а не под WhenActivated, что
снимало их при деактивации)»). The two subscriptions above were not moved. If they are torn down the
same way, `StorageUI()` (the only writer of the window size, `:2041-2050`) never runs, and
`AppManager.Shutdown()` publishes to nobody, so `desktop.Shutdown()` is never called.

Separately, and independent of the activation question, the write moment itself is late:
`ConfigHandler.SaveWindowSizeItem` only mutates the in-memory config — it contains no disk write
(`ConfigHandler.cs:2806-2819`). The actual `SaveConfig` happens in `AppManager.AppExitAsync`
(`AppManager.cs:161`) after `AppExitRequested` is published (`:158`). So a crash, a kill, or a power
loss discards the resized window entirely.

Fix direction: move both subscriptions to the window-lifetime `_windowInteractions` bundle that
already exists for exactly this reason, and persist the size on a debounced resize rather than only
on a clean exit.

---

## F8 — MEDIUM: every `Recompute()` rebuilds the subscription cards wholesale, discarding live card UI state

`AccountViewModel.Recompute` (`:2126-2144`) builds a brand-new `AccountSubCard` per subscription and
assigns `SubCards = cards`. Only `CardWidth` is carried over (`:2135-2138`) and `CarouselIndex` is
clamped (`:2141-2144`). Everything the user was doing on a card — `MenuMode` / `DeviceMode` /
`UpgradeMode` / `UpgradeConfirmMode` (`:2749-2752`), `RenewExpanded` (`:2710-2711`), `ExtraDevices`
(`:2761`) — is reset to its default.

`Recompute()` is called from ~25 sites, including background pollers the user does not initiate.
The clearest failure: after a balance top-up, `SchedulePostTopUpBalanceRefresh` (`:1407-1433`) calls
`RefreshProfile()` **every 5 s for up to 60 s**; each one runs `Recompute()` (`:583`) — and
`RefreshProfile` also calls `AccountSession.UpdateProfile` (`AccountRepository.cs:64`), which raises
`StateChanged` → `OnSessionStateChanged` → a second `Recompute()` (`AccountViewModel.cs:2512-2527`).
So while the top-up poll runs, an open device-picker or upgrade panel collapses under the user's
cursor roughly every 5 seconds, twice.

Fix direction: reconcile `SubCards` in place by subscription id (the same discipline
`HomeServerGroup.ReconcileServers` already uses for server rows, `HomeViewModel.cs:901-952`), or carry
the per-card interaction state across the rebuild.

---

## F9 — LOW: shell state that never survives a restart

* `_currentTab` (`MainWindow.axaml.cs:59`) always starts on `AppTab.Home`. Never read from or written
  to config. (Possibly intentional — flagging for a decision, not asserting a bug.)
* `_railCollapsed` (`MainWindow.axaml.cs:947`, toggled at `:949-961`) is never persisted, so the
  navigation rail re-expands on every launch even for a user who collapsed it.
* `HomeViewModel._groupExpanded` (`HomeViewModel.cs:42`, written at `:765`) is per-process only —
  collapsed subscription groups re-expand on every launch (`:700` defaults missing keys to expanded).

---

## Things checked and found CORRECT (so they are not re-broken later)

* `HomeViewModel.ReconcileGroups` correctly refuses to answer from an unloaded list: both `HasServers`
  and `IsEmpty` stay `false` while the truth is unknown (`HomeViewModel.cs:550-552`), and
  `ServerListView.axaml:83` / `:294` bind the list and the empty state to those two independent
  flags, so "unknown" renders neither.
* The `Clear()+AddRange()` burst that `RefreshServersBiz` emits (`ProfilesViewModel.cs:396-397`) is
  coalesced to one deferred reconcile, so the transient `count == 0` never latches `IsEmpty`
  (`HomeViewModel.cs:502-518`).
* Theme and language are applied from config **before** the window is built
  (`App.axaml.cs:33`, `:38`), so the first frame is never wrong-theme or wrong-language.
* `MotionState`/`UiScaleState` are seeded in the `MainWindow` ctor body (`:128`, `:145`), i.e. *after*
  the XAML tree is built by field initializers. `OnboardingView` reads `MotionState.IsLite` in its
  ctor (`OnboardingView.axaml.cs:54`) and would therefore read the stale default — but it re-checks in
  `OnFirstLoaded` and restores full visibility (`:78-82`). `ConnectHeroView` reads it only in
  `OnAttachedToVisualTree` (`ConnectHeroView.axaml.cs:515`). `SettingsViewModel` explicitly reads the
  config rather than `UiScaleState.Current` for this reason (`SettingsViewModel.cs:184-187`).
* `AccountSyncView` resolves `AccountViewModel.Shared` in its ctor (`AccountSyncView.axaml.cs:44`);
  this is safe only because `_accountVm` is a **field initializer** in `MainWindow`
  (`MainWindow.axaml.cs:74-75`), which C# runs before `InitializeComponent()` at `:117`. Fragile but
  currently correct — worth a comment if either is ever reordered.
* `ConfigHandler.SetDefaultServer` re-checks the DB before reassigning `config.IndexId`
  (`ConfigHandler.cs:414-432`), so a filtered/empty in-memory list cannot silently move the user's
  default server.
* `DevicesViewModel` and `PaymentHistoryViewModel` both gate their empty state on an explicit
  "first result landed" flag (`DevicesViewModel.cs:42`, `PaymentHistoryViewModel.cs:50`) and
  distinguish a transport failure from "no subscription" (`DevicesViewModel.cs:202-212`).
* `HomeHeroPresenter` seeds `_firstApply = true` so a rebind jumps to the current connect state
  instead of animating into it (`HomeHeroPresenter.cs:32`, `:144-150`).

---

## Suggested fix order

1. **F1** — stop the destructive prune on an unverified fetch. This is data loss, not a flash.
2. **F4** — stop persisting the TUN downgrade.
3. **F2** — gate the Account skeleton on `_hasSubData`, not on `Profile == null`.
4. **F3** — make the Home empty decision unfiltered.
5. **F6**, **F8** — ordering/rebuild correctness.
6. **F5**, **F7**, **F9** — first-frame polish and persistence timing.
