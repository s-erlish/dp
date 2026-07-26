# Verify: "Speedtest/Mixedtest on CUSTOM nodes builds a bogus outbound yet reports Success"

Target: `/home/user/v2rayN/v2rayN/ServiceLib/Services/CoreConfig/V2ray/V2rayOutboundService.cs:303`
Verdict: **REAL — but latent (not reachable from the shipped Departament UI). Severity high → medium.**
The reporter's *mechanism* is correct in every step I could check. Two framing claims are wrong:
the user-visible symptom, and the reachability ("«Скорость» never works").

---

## 1. The mechanism — CONFIRMED, step by step

### 1.1 Custom rows do enter every test path

`Services/SpeedtestService.cs:70-114` (`GetClearItem`):

```
75  var ids = selecteds.Where(it => !it.IndexId.IsNullOrEmpty()
76      && (it.ConfigType.IsComplexType() || it.Port > 0))
83  if (!it.ConfigType.IsComplexType() && it.Port <= 0) { continue; }
94  if (it.ConfigType == EConfigType.Custom) { ...XrayJsonTemplateFmt.Introspect... }
```

`IsComplexType()` includes `Custom` (`Common/Extension.cs:93-96`), so Custom rows pass. The
upstream filter really was removed by the dep fork — `git log -p` on this file shows commit
`22978ca` ("Preserve Remnawave routing rules: keep XRAY_JSON as Custom") deleting
`&& it.ConfigType != EConfigType.Custom` and replacing it with the `IsComplexType()` test.

Departament subscription elements *are* Custom: `Handler/Fmt/XrayJsonTemplateFmt.cs:3-14` —
"A departament/Remnawave subscription element is a FULL Xray config … We deliberately store it
AS-IS as an `EConfigType.Custom` node".

### 1.2 The batch path was fixed, the per-node path was not

Batch (`Realping`/`UdpTest`): `Handler/CoreConfigHandler.cs:332-368`, with
`InjectCustomSpeedtestNodes(result, selecteds)` at `:360` (implementation `:378-485`) grafting each
Custom node's real proxy outbound + its own inbound + routing rule. Correct.

Per-node (`Speedtest`/`Mixedtest`): `Handler/CoreConfigHandler.cs:487-509` has **no** Custom branch —
compare `GenerateClientConfig` at `:16`, which *does* branch (`if (node.ConfigType ==
EConfigType.Custom) → GenerateClientCustomConfig`). `git show 22978ca --stat` confirms neither
`Manager/CoreManager.cs` nor `Services/CoreConfig/V2ray/CoreConfigV2rayService.cs` was touched by
that commit.

### 1.3 The per-node chain reaches `FillOutbound` with `_node.ConfigType == Custom`

- `Services/SpeedtestService.cs:433` — `LoadCoreConfigSpeedtest(it)` (single `ServerTestItem`).
- `Manager/CoreManager.cs:1155-1175` — loads the profile by `IndexId`, builds a context, calls
  `CoreConfigHandler.GenerateClientSpeedtestConfig(_config, context, testItem, configPath)`.
- `Handler/CoreConfigHandler.cs:494-501` — `RunCoreType != sing_box` → `new
  CoreConfigV2rayService(context).GenerateClientSpeedtestConfig(port)`.
- `Services/CoreConfig/V2ray/CoreConfigV2rayService.cs:232-300` — `:265 GenOutbounds()`.
- `Services/CoreConfig/V2ray/V2rayOutboundService.cs:5-19 → 21-33 → 51-58` — Custom is **not** a
  group type (`Extension.cs:88-91`), so `BuildProxyOutbound()` → `FillOutbound(outbound)`.

Every guard that could have stopped it passes for Custom:

| guard | file:line | result for Custom |
|---|---|---|
| `_node.IsValid()` | `CoreConfigV2rayService.cs:237` → `ProfileItem.cs:67-71` | `true` (short-circuits on `IsComplex()`) |
| `GetNetwork() is quic` | `CoreConfigV2rayService.cs:244` → `ProfileItem.cs:53-59` | `"raw"` (`Global.cs:72`), not quic |
| `NodeValidator.Validate` | `Builder/NodeValidator.cs:34-37` | returns immediately, Success |
| `ConfigType is Custom` skip | exists only in the **batch** generator, `CoreConfigV2rayService.cs:134` | absent here |

### 1.4 The throw and the swallow

`Services/CoreConfig/V2ray/V2rayOutboundService.cs:60-314`: the `switch (_node.ConfigType)` at `:66`
has cases only for VMess/Shadowsocks/SOCKS/HTTP/VLESS/Trojan/Hysteria2/WireGuard — no `Custom`.
Then:

```
303   outbound.protocol = Global.ProtocolTypes[_node.ConfigType];
...
310   catch (Exception ex)
312       Logging.SaveLog(_tag, ex);
```

`Global.cs:266-279` — `ProtocolTypes` has no `EConfigType.Custom` key ⇒ `KeyNotFoundException`,
caught and only logged. Note the throw happens **at** `:303`, so `FillBoundStreamSettings(outbound)`
at `:308` never runs either.

### 1.5 What the generated config actually contains

`outbound` is the deserialized `Sample/SampleOutbound` (`V2rayOutboundService.cs:53-54`), i.e.
**verbatim**:

```json
{ "tag":"proxy", "protocol":"vmess",
  "settings":{"vnext":[{"address":"v2ray.cool","port":10086,
     "users":[{"id":"a3482e88-686a-4a58-8126-99c9df64b7bf","security":"auto"}]}], ... },
  "streamSettings":{"network":"tcp"} }
```

`CoreConfigV2rayService.cs:296-298` then sets `ret.Success = true`. Routing is cleared and
`BuildFinalRule()` (`V2rayRoutingService.cs:211-232`) sends **all** tcp/udp to `outboundTag =
Global.ProxyTag` — i.e. to that dummy vmess server. So the claim "Success=true with the raw
V2raySampleOutbound template (no server)" is exactly right, except the template is not empty: it is a
hardcoded `v2ray.cool:10086` vmess with a canned UUID.

---

## 2. What the reporter got wrong

### 2.1 "reports Success" ≠ the user sees a green result

`Success=true` is at the `RetResult`/config-generation level. Its consequence is that
`CoreManager.LoadCoreConfigSpeedtest` does **not** return `null` (`CoreManager.cs:1167-1170`) and
starts the core on a structurally valid but wrong config, instead of failing cleanly. Downstream,
`RunMixedTestAsync` (`SpeedtestService.cs:442-458`) does `DoRealPing` through
`socks5://127.0.0.1:{port}`, which tunnels to `v2ray.cool:10086` and fails, so `delay <= 0` and the
row ends at `ResUI.SpeedtestingSkip`. The user sees a *failed* test, not a fake success. The real
harm is the silent misattribution ("this server is dead") plus a swallowed exception in the log.

### 2.2 "«Скорость» never works for the product's own servers" — the action is not exposed

`RunMixedTestAsync` is reached from only two places:

1. `SpeedtestService.cs:60-66` — `ESpeedActionType.Speedtest` / `Mixedtest`. These map to
   `SpeedServerCmd` / `MixedTestServerCmd` (`ViewModels/ProfilesViewModel.cs:195-198, 211-214`),
   which are bound **only** in `ProfilesView` (`v2rayN.Desktop/Views/ProfilesView.axaml.cs:70,74`
   and the WPF `v2rayN/Views/ProfilesView.xaml.cs:69,73`).
   - The Incy shell never hosts `ProfilesView`: `v2rayN.Desktop/Views/MainWindow.axaml.cs:230-235`
     adds only `_homeView, _compactHome, _settingsView, _accountView` to `contentHost`; no `.axaml`
     in `v2rayN.Desktop` binds a `ProfilesViewModel` as content (only `ProfilesView.axaml` itself).
   - The WPF `v2rayN` project is not what ships: `.github/workflows/departament-branch-build.yml:32`
     publishes `v2rayN.Desktop/v2rayN.Desktop.csproj` only.
   - The reachable Departament entry points are latency-only:
     `ServerListView.axaml:152` → `OnRowPing` → `ResolvePingAction()`
     (`ServerListView.axaml.cs:762-778`) = **Tcping or Realping**; and
     `CompactServersView.axaml:65-72` / `SubscriptionMetaView.axaml.cs:523` → `FastRealPingCmd` →
     `Realping` (`ProfilesViewModel.cs:791-796`). All three land in the **fixed batch** path.

2. `SpeedtestService.cs:229-236` — the Realping "retest the failed part" fallback. **This became
   unreachable in this fork.** `RunRealPingAsync` now returns `false` only at `:269-272`
   (`ShouldStopTest`), because the dep change at `:253-257` turned the old `return false` on a null
   process into the TCP fallback + `return true`. `ShouldStopTest` is monotone (the key is only ever
   removed by `ExitLoop`'s `Clear()`, `:24-32`, `:34-37`), so if it was true inside `RunRealPingAsync`
   it is still true at the `:221` guard, which returns before `:235`.

So today the defect cannot be triggered from the shipped product. It is a landmine, not a live bug.

---

## 3. Corrected description (what to file)

> **Latent:** `CoreConfigV2rayService.GenerateClientSpeedtestConfig(int port)` — the *per-node*
> speedtest config generator — has no `EConfigType.Custom` branch, unlike its batch sibling
> (`CoreConfigHandler.InjectCustomSpeedtestNodes`) and unlike the connect path
> (`CoreConfigHandler.GenerateClientConfig:16`). For a Custom node it calls
> `V2rayOutboundService.FillOutbound`, whose `switch` has no Custom case and whose
> `Global.ProtocolTypes[_node.ConfigType]` (`V2rayOutboundService.cs:303`) throws
> `KeyNotFoundException` — swallowed by the method's own `catch` (`:310-313`). The generator still
> returns `Success = true`, so the core is started on a config whose only proxy outbound is the
> untouched `Sample/SampleOutbound` template (vmess → `v2ray.cool:10086`) and whose final routing
> rule points everything at it. Any measurement taken through it is meaningless (always fails →
> `-1`/skip), and the root cause is invisible except as one logged exception.
>
> Not currently reachable: the only callers are `ESpeedActionType.Speedtest`/`Mixedtest`, exposed
> only by `ProfilesView`, which the Departament shell does not host, and the Realping failed-part
> fallback, which this fork made unreachable. It will become live the moment a speed test is wired
> into the Incy UI, `ProfilesView` is re-hosted, or the Realping fallback is restored.
>
> Two independent fixes are warranted regardless: (a) make the per-node generator handle Custom
> (reuse the batch graft, or bail with `Success=false` + a real message), and (b) stop
> `FillOutbound` from silently swallowing a missing-protocol failure — an unresolvable
> `ConfigType` must fail the generation, not produce a template-shaped decoy outbound.
