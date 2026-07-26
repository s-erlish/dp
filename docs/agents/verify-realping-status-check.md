# Verification — "Real-ping reports a healthy latency for a proxy returning error pages"

Target: `/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConnectionHandler.cs:88`
Verdict: **REAL** (mechanism confirmed at the HTTP layer; two of the reporter's propagation claims
confirmed, one partly wrong; one stated trigger scenario is mechanistically wrong).

---

## 1. The probe itself — confirmed, no status validation

`ConnectionHandler.GetRealPingTime` (`/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConnectionHandler.cs:69-99`):

```csharp
 84            List<int> oneTime = [];
 85            for (var i = 0; i < 2; i++)
 86            {
 87                var timer = Stopwatch.StartNew();
 88                await client.GetAsync(url, cts.Token).ConfigureAwait(false);
 89                timer.Stop();
 90                oneTime.Add((int)timer.Elapsed.TotalMilliseconds);
 91                await Task.Delay(100, cts.Token);
 92            }
 93            responseTime = oneTime.Where(x => x > 0).OrderBy(x => x).FirstOrDefault();
 94        }
 95        catch
 96        {
 97        }
```

- The returned `HttpResponseMessage` at `:88` is discarded — never assigned, never inspected, never
  disposed. There is no `EnsureSuccessStatusCode()`, no `IsSuccessStatusCode`, no
  `StatusCode == 204` check anywhere in the method (`:69-99`).
- `HttpClient.GetAsync` does **not** throw on 4xx/5xx — only on transport/DNS/TLS failure, timeout,
  or cancellation. So *any* HTTP response — 403, 404, 503, a captive-portal 200 login page, an ISP
  block page — is timed and reported as a valid latency.
- The probe URL is `SpeedTestItem.SpeedPingTestUrl` (`:71`), which defaults to
  `https://www.google.com/generate_204` (`ServiceLib/Global.cs:189-197`, seeded at
  `ServiceLib/Handler/ConfigHandler.cs:133-136`). A `generate_204` endpoint has exactly one correct
  answer — HTTP 204 — and the code accepts every other answer equally.
- The URL is user-editable (`v2rayN.Desktop/Views/PingSettingsPage.axaml.cs:71-75`,
  `ServiceLib/ViewModels/OptionSettingViewModel.cs:396`), so a mistyped/redirecting/404 URL makes
  every server in the list report a fast, healthy ping.

**Contrast inside the same codebase**: every other HTTP path *does* validate status —
`DownloadService.UrlRedirectAsync` logs `"StatusCode error: "` for non-redirect responses
(`ServiceLib/Services/DownloadService.cs:92-99`) and the downloader calls
`response.EnsureSuccessStatusCode()` (`ServiceLib/Services/DownloadService.cs:316`). The ping path is
the outlier.

**Cross-platform contrast**: the Android sibling of this same product already does it right —
`/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/handler/SpeedtestManager.kt:53-73`:

```kotlin
 66                    if (r.code == 204 || r.code == 200) ms else -1L
```

with the doc comment at `:33` "latency in ms, or **-1 on failure / unexpected status / redirect**".
The desktop has no equivalent gate, so the two platforms disagree on what "reachable" means.

## 2. Where the bad number goes

### 2.1 Server-list ping column — confirmed, primary user-visible surface

`SpeedtestService.DoRealPing` (`/home/user/v2rayN/v2rayN/ServiceLib/Services/SpeedtestService.cs:478-499`):

```csharp
481        var responseTime = await ConnectionHandler.GetRealPingTime(webProxy);
483        ProfileExManager.Instance.SetTestDelay(it.IndexId, responseTime);
484        await UpdateFunc(it.IndexId, responseTime.ToString());
```

The value is written to the profile row unconditionally and rendered as the row's ping
(`ServiceLib/ViewModels/ProfilesViewModel.cs:306-310` sets `item.Delay` / `item.DelayVal`;
`v2rayN.Desktop/Views/ServerListView.axaml.cs:25` — "ping ← DelayVal").

This is the **default** path for Departament: the fork defaults `PingMethod` to `Realping`
(`ServiceLib/Handler/ConfigHandler.cs:145-149`, comment "departament: default latency probe = real
delay through the core"), and `ServerListView` maps the persisted method to
`ESpeedActionType.Realping` (`v2rayN.Desktop/Views/ServerListView.axaml.cs:768-778`) →
`SpeedtestService.RunAsync` case `Realping` (`:52-53`) → `RunRealPingBatchAsync` (`:198`) →
`RunRealPingAsync` (`:240`) → `DoRealPing` (`:276`).

### 2.2 Speed-test gate — confirmed

`SpeedtestService.RunMixedTestAsync` gates the download test on the same unvalidated number
(`ServiceLib/Services/SpeedtestService.cs:442-458`):

```csharp
442                    var delay = await DoRealPing(it);
451                        if (delay > 0)
452                        {
453                            await DoSpeedTest(downloadHandle, it);
```

So a node that only answers with an error page passes the gate and burns a full speed-test cycle.

### 2.3 Post-connect availability check — call sites confirmed, but the result is currently invisible in the Departament shell

The call sites are exactly as reported: `MainWindowViewModel.Reload` at
`/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/MainWindowViewModel.cs:848-851` and
`MainWindowViewModel.SwitchServer` at `:932-935`, both scheduling
`StatusBarViewModel.TestServerAvailability()`, which runs
`ConnectionHandler.RunAvailabilityCheck` (`ServiceLib/ViewModels/StatusBarViewModel.cs:391-405`) →
`ConnectionHandler.cs:10-16` → `string.Format(ResUI.TestMeOutput, time, ip)` = «Задержка: {0} мс, {1}»
(`ServiceLib/Resx/ResUI.ru.resx:294-295`).

Two corrections to the reporter's framing of this surface:

1. **It is not actually displayed in the Departament desktop UI today.** The result goes to
   `RunningInfoDisplay` (`StatusBarViewModel.cs:404, 417-421`), bound to `txtRunningInfoDisplay`
   inside `StatusBarView` (`v2rayN.Desktop/Views/StatusBarView.axaml:78`,
   `.axaml.cs:40`) — and the Departament shell hosts `StatusBarView` as a `0×0`, `Opacity=0`,
   non-hit-testable placeholder (`v2rayN.Desktop/Views/MainWindow.axaml:641-651`). The other sink,
   `NoticeManager.SendMessageEx` → `AppEvents.SendMsgViewRequested` (`ServiceLib/Manager/NoticeManager.cs:26-33, 18-24`)
   → `MsgViewModel` (`ServiceLib/ViewModels/MsgViewModel.cs:33`), has no host either: `MsgView` is
   registered in `v2rayN.Desktop/Common/SimpleViewLocator.cs:26` but appears in no `.axaml` and no
   `ViewModelViewHost` in `v2rayN.Desktop/Views/`. So the connect-time probe currently runs on every
   Reload/SwitchServer and its answer is dropped on the floor. The claimed user-facing string
   "connected, 45 ms" does not render in this build.
2. Even if it were shown, `RunAvailabilityCheck` has a partial self-check: at
   `ConnectionHandler.cs:13` the IP lookup runs only when `time > 0`, and it goes through
   `DownloadService.TryDownloadString` + `JsonUtils.Deserialize` (`ConnectionHandler.cs:104-136`),
   which fails on an HTML error page and yields `Global.None`. A captive portal would therefore read
   «Задержка: 45 мс, None» — misleading latency, but not a fully healthy line.

## 3. What the reporter got wrong

- **"the catch at :95-97 is empty" as part of the mechanism.** The empty catch is a real smell (it
  swallows the reason with no `Logging.SaveLog`, unlike `GetRealPingTimeInfo` at `:49-53`), but it is
  *not* what produces the false-healthy value: `responseTime` is only assigned at `:93`, after the
  loop, so any thrown exception correctly leaves `-1`. The false-healthy value comes purely from the
  missing status check at `:88`.
- **"a 403 from an expired subscription"** — mechanistically wrong for the default configuration. An
  expired/removed Remnawave user is rejected at the VLESS/TLS handshake, so the proxied TCP stream
  dies and `GetAsync` throws → `-1`. There is no HTTP 403 to mis-read. Same for "Remnawave 'app not
  supported' page": that page is served to a *subscription-URL* fetch, not to the `generate_204`
  probe. The realistic triggers are: the probe host itself answering non-2xx over the tunnel
  (403/429/503, Cloudflare/ISP block page), a transparent proxy or DNS hijack answering the probe, a
  captive portal when the probe is routed direct, and — most likely in practice — a user-edited
  `SpeedPingTestUrl` (`PingSettingsPage.axaml.cs:71-75`) that 404s or redirects to a login page.
- **Provenance**: this is upstream v2rayN code, untouched by the fork — `git blame` attributes
  `:69-99` to 2dust, commits `0d225cd` (2026-06-19) / `e1a5c36` (2026-07-06). Not a Departament
  regression, but it is Departament's default ping method and it contradicts the fork's own Android
  behaviour.

## 4. Adjacent defect found in the same three lines (not in the claim)

`:93` `oneTime.Where(x => x > 0).OrderBy(x => x).FirstOrDefault()` returns **0** — not `-1` — when
both samples truncate to 0 ms (`(int)timer.Elapsed.TotalMilliseconds` at `:90`, i.e. a sub-1 ms
answer such as a locally injected error page). Callers test `> 0`
(`ConnectionHandler.cs:13, 42`; `SpeedtestService.cs:451, 486`), so 0 reads as failure there, while
`SpeedtestService.cs:484` still writes the string `"0"` into the row and
`ProfilesViewModel.cs:309` displays it — an inconsistent "0 ms" row versus
`ProfilesViewModel.cs:477`, which maps `Delay == 0` to empty on reload. Cosmetic, low severity.

## 5. Suggested fix (shape only)

In `GetRealPingTime`, capture the response and require success — mirroring the Android rule at
`SpeedtestManager.kt:66` so both platforms agree:

```csharp
using var resp = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cts.Token).ConfigureAwait(false);
timer.Stop();
if (!resp.IsSuccessStatusCode) { return -1; }   // generate_204 → 204; 200 also acceptable
```

`ResponseHeadersRead` additionally makes the measurement time-to-first-byte instead of
time-to-full-body (an error page's body currently inflates the number), and `using` disposes the
response. Keep `-1` as the single failure sentinel and log the swallowed exception at `:95-97`.

---

### Files read for this verification

- `/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConnectionHandler.cs` (whole file)
- `/home/user/v2rayN/v2rayN/ServiceLib/Services/SpeedtestService.cs:240-535`
- `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/MainWindowViewModel.cs:800-998`
- `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/StatusBarViewModel.cs:370-421`
- `/home/user/v2rayN/v2rayN/ServiceLib/Services/DownloadService.cs:1-160, 316`
- `/home/user/v2rayN/v2rayN/ServiceLib/Handler/ConfigHandler.cs:125-160`
- `/home/user/v2rayN/v2rayN/ServiceLib/Global.cs:180-197`
- `/home/user/v2rayN/v2rayN/ServiceLib/Manager/NoticeManager.cs:1-60`
- `/home/user/v2rayN/v2rayN/ServiceLib/ViewModels/ProfilesViewModel.cs:295-320, 477`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/PingSettingsPage.axaml.cs`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml:630-660`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/StatusBarView.axaml:60-95`
- `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/handler/SpeedtestManager.kt:20-130`
