# Adversarial verification — "ReactiveCommand.Execute().Subscribe() with no onError → unhandled exception"

**Verdict: CONFIRMED (real defect), but the reporter's mechanism is partly wrong and two of the four
call sites are near-unreachable. Corrected severity: medium-high, not high.**

Everything below is from files I read. Library behaviour is verified against the exact pinned package
versions, not from memory.

---

## 1. The call sites exist (line numbers in the claim are off)

| Claimed | Actual | Code |
|---|---|---|
| `MainWindow.axaml.cs:1216` | **`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs:1240`** | `_accountVm.LoginTelegramCmd.Execute().Subscribe();` |
| `MainWindow.axaml.cs:1225` | **`…/MainWindow.axaml.cs:1249`** | `_accountVm.LoginBrowserCmd.Execute().Subscribe();` |
| `AccountView.axaml.cs:66` | **`…/Views/AccountView.axaml.cs:66`** ✓ | `LogoutRow.Tapped += (_, _) => (DataContext as AccountViewModel)?.LogoutCmd.Execute().Subscribe();` |
| `BuyViewModel.cs:642` | **`…/ViewModels/BuyViewModel.cs:642`** ✓ | `accountVm?.RetryCmd.Execute().Subscribe();` |

These are the only four no-onError sites in the desktop tree. The three correct sites the reporter
cites are real and do pass an onError:

- `…/Views/LoginView.axaml.cs:1359` — `command?.Execute().Subscribe(_ => { }, _ => { });`
- `…/Views/ServerListView.axaml.cs:793` — `profiles.CopyServerCmd.Execute().Subscribe(static _ => { }, static _ => { });`
- `…/Views/SubscriptionMetaView.axaml.cs:523` — `Profiles?.FastRealPingCmd.Execute().Subscribe(_ => { }, _ => { });`
  (reporter missed this one; it corroborates the pattern)

## 2. The dual-channel claim is CORRECT for the pinned ReactiveUI

`/home/user/v2rayN/v2rayN/Directory.Packages.props:22` pins `ReactiveUI` **23.2.28**.
Verbatim from `src/ReactiveUI/ReactiveCommand/ReactiveCommand.cs` at tag `23.2.28`
(`ReactiveCommand<TParam, TResult>.Execute`):

```csharp
.SelectMany(sourceAndCancellation =>
{
    var (sourceObservable, cancelCallback) = sourceAndCancellation;
    var sharedSource = sourceObservable.Publish().RefCount(2);

    // This is the subscription that survives for however long sourceObservable takes to complete (or fail).
    sharedSource
        .Do(result => _synchronizedExecutionInfo.OnNext(ExecutionInfo.CreateResult(result)))
        .Catch<TResult, Exception>(
            ex =>
            {
                _exceptions.OnNext(ex);
                return Observable.Empty<TResult>();
            })
        .Finally(() => _synchronizedExecutionInfo.OnNext(ExecutionInfo.CreateEnd()))
        .Subscribe();

    return sharedSource.Finally(() => cancelCallback());   // ← NOT wrapped in Catch
});
```

The internal subscriber catches and routes to `_exceptions` (`ThrownExceptions => _exceptions.AsObservable()`).
The **returned** sequence is bare `sharedSource`, so an external subscriber receives `OnError` too.
Both channels fire. ✅

And the parameterless `Subscribe()` really does install a rethrowing stub — `System.Reactive` **6.1.0**
(`/root/.nuget/packages/system.reactive/6.1.0`):

- `Rx.NET/Source/src/System.Reactive/Observable.Extensions.cs`:
  `return source.Subscribe(new AnonymousObserver<T>(Stubs<T>.Ignore, Stubs.Throw, Stubs.Nop));`
- `Rx.NET/Source/src/System.Reactive/Internal/Stubs.cs`:
  `public static readonly Action<Exception> Throw = static ex => { ex.Throw(); };` (ExceptionDispatchInfo rethrow)

## 3. The VM safety net genuinely does not cover this channel

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/AccountViewModel.cs:358-395` merges **only**
`…Cmd.ThrownExceptions` (incl. `LoginTelegramCmd` :365, `LoginBrowserCmd` :367, `LogoutCmd` :373,
`RetryCmd` :374). Its own comment at :358 says "a stray command exception surfaces as the error state
instead of crashing" — which is exactly the assumption the missing onError breaks.

No global backstop exists either: there is **no** `RxApp/RxState.DefaultExceptionHandler` override
anywhere in the repo. `/home/user/v2rayN/v2rayN/v2rayN.Desktop/App.axaml.cs:14-15` registers only
`AppDomain.CurrentDomain.UnhandledException` and `TaskScheduler.UnobservedTaskException`, and both
handlers (`:297-309`) merely `Logging.SaveLog(...)` — the AppDomain handler cannot stop termination.

## 4. Where the reporter is WRONG

### 4a. `ProcUtils.ProcessStart` cannot throw — this example is bogus
`/home/user/v2rayN/v2rayN/ServiceLib/Common/ProcUtils.cs:12-45` wraps the entire body in
`catch (Exception ex) { Logging.SaveLog(_tag, ex); }` and returns `null`. So
`ApplyLoginState` (`AccountViewModel.cs:1081-1095`; the `ProcessStart` line is **1089**, not the
claimed 1039) is not a fault source via `ProcessStart`.

### 4b. The scheduler explanation is wrong for this ReactiveUI version
The claim says "because the command's output scheduler is the main-thread scheduler the rethrow lands
inside a dispatcher work item." In 23.2.28 `Execute()` applies **no `ObserveOn(_outputScheduler)`** to
the returned sequence (see the verbatim body above). `_outputScheduler` is used only for `_exceptions`
(a `ScheduledSubject`) and `_synchronizedExecutionInfo` in the ctor. The rethrow therefore happens on
whatever thread completes the underlying task.

In practice it usually still lands on the UI thread, but by a different route: these are
`ReactiveCommand.CreateFromTask` commands (`AccountViewModel.cs:340,348,349`) →
`CreateFromObservable(() => execute().ToObservable())`; the delegate is started synchronously on the UI
thread by `Execute()`, and there is **no `ConfigureAwait(false)` anywhere in
`v2rayN.Desktop/Account/**` or `AccountViewModel.cs`**, so continuations resume on Avalonia's
SynchronizationContext. A fault raised on a pool thread would instead rethrow on a pool thread —
equally fatal. Either way the conclusion (process termination) stands; the stated cause does not.

### 4c. `LoginBrowserCmd` (MainWindow:1249) is a non-issue
`LoginBrowserCmd = ReactiveCommand.Create(OpenSiteLoginBrowser)` (`AccountViewModel.cs:342`), and
`OpenSiteLoginBrowser` (`:948-975`) already wraps `ProcUtils.ProcessStart` in its own try/catch — over
a method that itself cannot throw (4a). Nothing realistic faults here.

### 4d. `RetryCmd` (BuyViewModel:642) is far weaker than claimed
`RetryCmd = CreateFromTask(Retry)` → `Retry()` (`AccountViewModel.cs:1324-1333`) → `LoadAll()`
(`:558-565`), and **every** repository call is exception-proof:
`AccountRepository.Guard<T>` (`…/Account/AccountRepository.cs:30-44`) and `RefreshProfile`
(`:58-79`) both end in `catch (Exception e) → ApiResult.Failure(new ApiError.NetworkError(e))`.
Network/HTTP/parse faults cannot escape. On top of that, the call site is inside
`RunOnUi(() => { try { … } catch (Exception ex) { Logging.SaveLog("BuyRefreshAfterPurchase", ex); } })`
(`BuyViewModel.cs:616-647`), which swallows any *synchronous* rethrow — i.e. the case where `Retry()`
faults before its first real await. Only a post-await fault escapes.

## 5. Where the defect IS real (the two sites that matter)

### `LogoutCmd` — `AccountView.axaml.cs:66` — strongest case
`Logout()` (`AccountViewModel.cs:1289-1321`) has **no try/catch** and awaits
`AccountSession.Wipe()` (`…/Account/AccountSession.cs:109-117`):

```csharp
await StopEngine();                 // guarded (:124-135)
await _subs.RemoveAllManaged();     // NOT guarded
AuthTokenStore.Clear();             // Persist() swallows IO (AuthTokenStore.cs:188-200)
SetState(new AccountState.LoggedOut());  // → StateChanged?.Invoke on the caller's thread
```

`RemoveAllManaged()` (`…/Account/SubscriptionSyncManager.cs:176-188`) loops
`await ConfigHandler.DeleteSubItem(config, kv.Value)`, and
`/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConfigHandler.cs:2107-2123` does raw
`SQLiteHelper.Instance.DeleteAsync(item)` + `RemoveServersViaSubid(...)` with **no** try/catch.
A SQLite/IO failure there faults the command → `Subscribe()` rethrows → process dies mid-logout,
after the VPN has been stopped but before the session is cleared.
`SetState` → `StateChanged?.Invoke` is a second surface: four live subscribers
(`AccountViewModel.cs:401`, `DevicesViewModel.cs:108`, `BottomNavBar.axaml.cs:70`,
`HomeAccountChip.axaml.cs:49`) run on the caller's thread.

### `LoginTelegramCmd` — `MainWindow.axaml.cs:1240` — real but narrower
`StartTelegramLogin()` (`AccountViewModel.cs:855-881`) has **no try/catch**.
`AuthManager.BeginTelegramLogin` (`…/Account/AuthManager.cs:71-140`) catches only `ApiError`, at
**:84** and **:118** — the reporter's line cites are correct here. Outside any catch:
- `AccountSession.OnAuthenticated(confirmed.Token, confirmed.Client)` (`AuthManager.cs:127`) →
  `SetState` → `StateChanged?.Invoke` (same four subscribers).
- every `emit(...)` (`:88, :91, :95, :96, :123, :128, :138`) → `RunOnUi(() => ApplyLoginState(state))`;
  `RunOnUi` (`AccountViewModel.cs:2632-2642`) invokes **synchronously** when already on the UI thread,
  so a throw from `ApplyLoginState`/property-changed subscribers propagates back into the task.

Note the transport layer is tighter than the reporter implies: `DepartamentApiClient.Execute`
(`…/Account/DepartamentApiClient.cs:398-417`) maps `TaskCanceledException`,
`OperationCanceledException` and `HttpRequestException` to `ApiError`, `MapError` covers status codes
(`:420-433`) and `Parse<T>` maps `JsonException` to `ApiError.Parse` (`:454-469`). And
`UrlOf` (`:352`) builds from a hardcoded valid constant
(`BackendConfig.BaseUrl = "https://web.departament.site/api"`, `…/Account/BackendConfig.cs:14`), so
the `UriFormatException`-in-`HttpRequestMessage` hazard is unreachable. The escape hatches are the
non-API code above, not the HTTP client.

## 6. Fix (and a trap to avoid)

Pass a no-op onError, matching the three sites that already do it:

```csharp
_accountVm.LoginTelegramCmd.Execute().Subscribe(static _ => { }, static _ => { });
```

**Do not "fix" it by dropping `.Subscribe()`.** In 23.2.28 the shared source is
`sourceObservable.Publish().RefCount(2)` — the internal subscriber is #1 and the external subscriber
is #2, so without an external subscription the command **never connects and never runs**.

Optionally also close the underlying holes: wrap `Logout()`'s body (or `AccountSession.Wipe`'s
`RemoveAllManaged` loop) in try/catch, and give `StartTelegramLogin()` a `catch (Exception)` that
routes through the existing `ReportCommandException` (`AccountViewModel.cs:2002`).
