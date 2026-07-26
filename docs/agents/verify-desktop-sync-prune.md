# Verification: "Transient API failure during account sync deletes every imported subscription and its servers"

**Verdict: CONFIRMED (real, critical).** The mechanism the reporter describes is correct in its
load-bearing parts. One supporting sub-claim ("a failure of the single primary call is sufficient")
rests on a stale doc comment and is *probably* wrong against the live backend — but the defect is
strictly worse than reported, because a well-formed HTTP **200** response can trigger the same wipe.

---

## 1. The destructive path, line by line

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Account/SubscriptionSyncManager.cs`

```
:52-60   PrimarySubscriptionDto? primary = null;
         try { primary = await _api.GetPrimarySubscription(); }
         catch (ApiError) { /* fall through */ }

:62-70   List<SubInfoDto> all;
         try { all = (await _api.GetSubscriptionAll()).Items; }
         catch (ApiError) { all = new List<SubInfoDto>(); }

:73      return await Import(primary, all, profile);
```

Both `catch (ApiError)` blocks swallow the failure and leave `primary == null`, `all == []`
(SubscriptionSyncManager.cs:57-60, :67-70).

`BuildCandidates(null, [], profile)` returns an **empty** list:
- SubscriptionSyncManager.cs:149 — `primary?.HasActiveSubscription() == true` is false when `primary`
  is null, so no primary candidate.
- SubscriptionSyncManager.cs:156 — the `foreach (var info in all)` loop body never runs on an empty list.

Therefore the import loop at SubscriptionSyncManager.cs:84-122 never executes, `newMap` stays empty
(declared :81) and `resultGuids` stays empty (:82).

The prune loop then runs unconditionally — there is **no** "did we get any candidates?" guard:

```
:124-131  foreach (var kv in managed)
          {
              if (kv.Value.IsNotEmpty() && !newMap.Values.Contains(kv.Value))
              {
                  await ConfigHandler.DeleteSubItem(config, kv.Value);
              }
          }
:133      AuthTokenStore.SetManagedGuids(newMap);   // ← mapping cleared too
```

`managed` comes from `AuthTokenStore.GetManagedGuids()` (SubscriptionSyncManager.cs:79), which is a
**persisted, cross-launch** map — it is serialized into the encrypted store
(`AuthTokenStore.cs:33` `public Dictionary<string, string> ManagedGuids`, read at `:127-133`, written
at `:135-142`, loaded from disk at `:164-180`). It is only emptied on logout / 401 wipe
(`AuthTokenStore.cs:145-156`). So on any returning-user launch it is non-empty for anyone who has ever
imported successfully.

`ConfigHandler.DeleteSubItem` is genuinely destructive:

```
/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConfigHandler.cs
:2107-2124  DeleteSubItem(config, id)
:2114           await SQLiteHelper.Instance.DeleteAsync(item);          // the SubItem row
:2115           await RemoveServersViaSubid(config, id, false);         // ← every server
```

and `RemoveServersViaSubid(..., isSub: false)` deletes **all** profiles of that subid, not just
subscription-sourced ones, and also unlinks custom-config files from disk:

```
ConfigHandler.cs:2078-2099
:2091   delete from ProfileItem where subid = '<subid>'
:2093-2096  File.Delete(Utils.GetConfigPath(item.Address));  // custom profiles' files
```

**Net effect of one failed sync: every account-imported subscription row, every server under it, and
the uuid→guid mapping are gone from the local DB.**

## 2. It is on the launch path, and the failure is invisible

- `AccountRepository.AutoImportSubscriptions() => Guard(() => _subs.ImportAll())`
  (`AccountRepository.cs:93`). `Guard` (`AccountRepository.cs:31-45`) only converts *thrown* errors —
  and `ImportAll` no longer throws, so it returns `ApiResult.Success(empty list)`.
- `AccountViewModel.AutoImportAndRefreshHome` (`ViewModels/AccountViewModel.cs:1256-1261`; the
  reporter's `:1202-1207` is off by ~54 lines) does
  `var import = await _repo.AutoImportSubscriptions(); RunOnUi(() => import.OnFailure(Report));`.
  `ApiResult.OnFailure` only fires when `IsFailure` (`ApiResult.cs:41-48`), so **nothing is reported**.
- `RunSyncPhases` (`AccountViewModel.cs:1139-1172`) therefore sees no exception from the import phase
  and continues to `LoadAll()`.
- Reached on **every launch with a persisted session**: constructor
  `AccountViewModel.cs:327-329` (`hasSession`) → `:426` `_ = Task.Run(StartupLoad)` → `:445`
  `RunSyncPhases(includeSubFetch:false)` → `:1144` `await AutoImportAndRefreshHome()`.
- Also reached post-purchase: `ViewModels/BuyViewModel.cs:609`.

**The wipe defeats the app's own stated fallback.** `StartupLoad` at `AccountViewModel.cs:483-495`
comments: *"Failing to reach the ACCOUNT is not a reason to hide the servers this user already has on
disk"* and checks `AppManager.Instance.HasStoredProfilesAsync()` (`ServiceLib/Manager/AppManager.cs:241-252`,
`count(ProfileItem) > 0`). But `ImportAll` has already deleted those rows, so the count is 0 and the
user is pinned on the blocking retry surface with **zero** servers — exactly when they are offline and
most need the VPN.

## 3. Which failures reach it

Every transport and HTTP failure in `DepartamentApiClient` is normalised into an `ApiError`
subclass, so the two catches swallow all of them:

- `Execute` maps `TaskCanceledException`/`OperationCanceledException` → `ApiError.TimeoutError`
  (25s client timeout, `DepartamentApiClient.cs:30`, `:404-411`) and `HttpRequestException` →
  `ApiError.NetworkError` (`:412-415`).
- `MapError` (`:418-430`) maps 401/403/404/410/429/502/503/other → `Unauthorized`/`Server`/
  `NotFoundError`/`GoneError`/`RateLimited`/`ServiceUnavailable`/`Server`.
- `Parse` failures → `ApiError.Parse` (`:465-468`).
- Blank base URL → `ApiError.NotConfiguredError` (`:344-350`).

All of these derive from `ApiError` (`ApiError.cs:8-104`). So: **no network at launch, DNS failure,
TLS failure, backend restarting (502/503), rate limit (429), slow link (>25s), or a malformed
response — each one deletes the user's entire server list.**

## 4. Where the reporter is (probably) wrong, and where the bug is *worse*

The reporter says "the class doc at :141 states `/all` never exposes its own URL today, so a failure
of the single primary call is sufficient". The doc does say that
(`SubscriptionSyncManager.cs:14-19` "KEY FACT", and `:137-141`
*"then any /all item that happens to expose its OWN url (future-proof — today /all never does)"*),
and the DTO repeats it (`Account/Dto/SubscriptionDtos.cs:31-34`
*"NOT present on /all items"*). **That doc comment is stale.** The deployed backend puts the same
Remnawave payload on both endpoints:

- `/home/user/dep-vpn-bot-v2-stealth/backend/src/modules/client/client.routes.ts:3278`
  root item: `subscription: rootResult.data ?? null` where `rootResult = await remnaGetUser(...)` (`:3231`).
- `client.routes.ts:3379` secondary item: `subscription: secResult.data ?? null` (`:3352`).
- The primary endpoint builds its `subscription` from the identical call
  (`client.routes.ts:2924` `const result = await remnaGetUser(client.remnawaveUuid)`).

`remnaGetUser` is a raw `GET /api/users/{uuid}` passthrough
(`backend/src/modules/remna/remna.client.ts:79-81`), whose body is `{ response: { … subscriptionUrl } }` —
the exact shape `SubResponseWrapper.Raw()` reads (`Dto/SubscriptionDtos.cs:151-157`) and
`BuildCandidates` uses at `SubscriptionSyncManager.cs:158`. So a **primary-only** failure most likely
still yields candidates from `/all`, and does *not* on its own wipe everything.

**But the same backend code exposes a strictly worse trigger the reporter missed: a 200 OK wipe.**
When the upstream Remnawave panel is unreachable, both endpoints return **HTTP 200** with a null
subscription rather than an error:

- primary: `client.routes.ts:2925-2927` — `if (result.error) return res.json({ subscription: null,
  tariffDisplayName: null, … })`. On the client, `HasActiveSubscription()`
  (`Dto/SubscriptionDtos.cs:138-144`) is then false (no `Raw()`, no `TariffDisplayName`), so the
  primary candidate is skipped at `SubscriptionSyncManager.cs:149`.
- `/all`: `client.routes.ts:3278` / `:3379` — `subscription: … ?? null` for every item, so
  `info.Subscription?.Raw()?.SubscriptionUrl` is null and every item is `continue`d at
  `SubscriptionSyncManager.cs:159-162`.

Result: zero candidates from **two successful 200 responses**, and the prune loop deletes everything.
No exception is involved, so hardening the two `catch (ApiError)` blocks alone would **not** fix this
case. The `/all` `hasPaid` filter (`client.routes.ts:3401-3403`) is a second, benign-looking way for
the item set to shrink.

Also note the Android original does **not** have this hazard: `AccountRepository.kt:93-96` calls
`api.getSubscriptionAll()` *outside* any catch, inside `guard`, so a fetch failure propagates and
`subs.importAll(...)` (`auth/SubscriptionSyncManager.kt:32`, prune at `:69-75`) is **never reached**.
The desktop port introduced the swallowing try/catch and with it the destructive path.

## 5. Corrected description

> `SubscriptionSyncManager.Import` prunes the managed subscription set unconditionally
> (`SubscriptionSyncManager.cs:124-131`), with no guard for "the remote set could not be determined".
> Any run that produces zero candidates deletes every managed `SubItem` **and every server under it**
> via `ConfigHandler.DeleteSubItem` → `RemoveServersViaSubid` (`ConfigHandler.cs:2107-2124`, `:2078-2099`),
> then clears the uuid→guid map (`SubscriptionSyncManager.cs:133`). Two independent causes produce
> zero candidates without any user-visible error:
> (a) transient API failure — both fetches swallow `ApiError` (`:57-60`, `:67-70`), which covers
>     offline/DNS/TLS/timeout/429/502/503/parse; and
> (b) an upstream (Remnawave) outage, where the backend answers **200** with `subscription: null` on
>     both endpoints (`client.routes.ts:2925-2927`, `:3278`, `:3379`), so no exception is thrown at all.
> This runs on every launch with a stored session (`AccountViewModel.cs:426` → `:445` → `:1144` →
> `:1256-1261`) and after every purchase (`BuyViewModel.cs:609`). The failure is silent —
> `ImportAll` returns `ApiResult.Success(empty)`, so `import.OnFailure(Report)` never fires
> (`ApiResult.cs:41-48`) — and it defeats the deliberate "keep the servers already on disk" fallback
> at `AccountViewModel.cs:483-495`, because `HasStoredProfilesAsync` (`AppManager.cs:241-252`) now
> counts zero.

**Correct fix shape:** only prune when the remote set was authoritatively determined. Have `ImportAll`
track whether *both* fetches succeeded (and, ideally, whether at least one candidate/URL was resolved),
pass that as an `authoritative`/`canPrune` flag into `Import`, and skip the `:124-131` loop plus the
`SetManagedGuids(newMap)` overwrite when it is false — returning the previously managed guids instead,
and surfacing the failure to the caller so `import.OnFailure(Report)` can report it. Pruning must never
be driven by "we got nothing back".

**Secondary finding (documentation drift, worth fixing while in here):** the "KEY FACT" comment at
`SubscriptionSyncManager.cs:14-19` and `:137-141`, and the DTO comments at
`Dto/SubscriptionDtos.cs:31-34`, claim `/all` never carries `subscriptionUrl`. The deployed backend
does put it there for both root and secondary items (`client.routes.ts:3278`, `:3379`). Anyone
reasoning about failure modes from those comments (as the original report did) will get the risk
profile wrong.
