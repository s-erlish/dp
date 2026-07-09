# Review 03 — Module 2: Selectable ping methods

Commit: `eddfac4` — feat(ping): 4 selectable connection-test methods
Scope reviewed: `git show HEAD -- V2rayNG/` (read-only; no code modified).

## Verdict

No BLOCKER / compile-breaking issues. All new symbols, imports and signatures
resolve. One HIGH functional-quality issue (HTTP method cannot differentiate
servers) plus minor efficiency/cosmetic notes.

## Compile / signature verification (all PASS)

| Check | Result |
|-------|--------|
| `okhttp3.*` fully-qualified refs | OK — okhttp3 on classpath (used in `util/HttpUtil.kt`); no import needed |
| `java.util.concurrent.TimeUnit.MILLISECONDS` fully-qualified | OK — resolves without import |
| `LogUtil.w(AppConfig.TAG, msg)` | OK — `LogUtil.w(tag, message)` overload exists (`util/LogUtil.kt`) |
| `HttpUtil.resolveHostToIP(host)` returns `List<String>?` | OK — `resolveHostToIP(host, ipv6Preferred=false): List<String>?`; `?.firstOrNull() ?: host` valid |
| ProcessBuilder / Regex / read-before-waitFor / `process?.destroy()` in finally | OK — reads `inputStream` before `waitFor()` (no pipe deadlock); cleanup present |
| `Semaphore` / `withPermit` imports (`kotlinx.coroutines.sync.*`) | OK — both added |
| `PingMethod` import in MainViewModel & SettingsManager | OK |
| `item.profile.server` nullability (`String?`) + `?: continue` | OK — `continue` inside `for` is valid Kotlin; server is `String?` |
| `encodeServerTestDelayMillis` / `clearAllTestDelayResults` / `closeAllTcpSockets` / `getDelayTestUrl` signatures | OK — all match usage |
| `tcpingTestScope` reuse + `cancelChildren()` | OK — mirrors existing `testAllTcping()` |
| `SettingsManager.getPingMethod()` + `ensureDefaultValue(PREF_PING_METHOD, ...)` + `AppConfig.PREF_PING_METHOD` | OK |
| Resource arrays `ping_method_entries`↔`ping_method_values` order | OK — both `real, http, tcp, icmp`; strings exist; `title_pref_ping_method` exists |
| ListPreference `key="pref_ping_method"` + `defaultValue="real"` matches `PREF_PING_METHOD` / `PROXIED_REAL_DELAY.prefValue` | OK |
| Wiring: `GroupServerFragment.pingSub` + `MainActivity.ping_all` → `testAllServers()` | OK |
| Fast-connect ignores `<=0` delays (`selectFastestServer`: `delay in 1 until bestDelay`) → ICMP `-1` handled | OK |

## Findings

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| HIGH | `MainViewModel.kt:281,287` (`testAllDirectHttp`) | The direct-HTTP probe hits ONE fixed URL (`getDelayTestUrl()`, e.g. gstatic `/generate_204`) for **every** server. It never connects to `item.profile.server`, so all nodes receive essentially the phone's single direct-internet latency → identical numbers. The "HTTP" ping cannot rank or differentiate servers; the per-row values it writes are misleading. Not a crash, but the feature does not do what a per-server test implies. | Either probe the node itself (e.g. `http(s)://<server>:<port>` / route through proxy), or clearly document/label this as "internet reachability", not a per-node metric. If kept, consider disabling fast-connect/sorting semantics for this mode (already excluded from fast connect — good). |
| MEDIUM | `SpeedtestManager.kt:36` (`httpPing`) | A fresh `OkHttpClient` is built on every call — one per server (hundreds on large subs, 24 concurrent). Each client owns its own dispatcher thread pool + connection pool → resource churn and GC pressure. OkHttp explicitly recommends sharing a single client. | Hoist a shared `OkHttpClient` (with per-call timeout via `callTimeout`, or `newBuilder()` per-call only if timeout must vary), reuse across probes. |
| LOW | `SpeedtestManager.kt:44` + `strings.xml` `ping_method_http` / KDoc | Method uses `.head()` (HEAD) but UI string says "HTTP GET /generate_204" and KDoc says "GET"/"time-to-first-byte". HEAD to Google `generate_204` returns 204 so it works, but text/impl drift. | Align wording (say HEAD) or switch to `.get()`. |
| LOW | `SpeedtestManager.kt:67` (`icmpPing`) | If `resolveHostToIP` yields an IPv6 literal (or host is a raw IPv6), `/system/bin/ping` (IPv4 toybox) will fail → `-1`. `ipv6Preferred=false` makes this rare, but IPv6-only nodes always read as filtered. | Detect IPv6 and invoke `ping6`/`ping -6`, or note the limitation. |

## Notes (no action needed)

- `testAllDirectHttp` / `testAllIcmp` intentionally omit fast-connect/auto-select; only `testAllRealPing` validates the tunnel and drives fast connect — correct per design ("fast connect keeps using proxied real delay").
- `withPermit` releases the permit after `httpPing`/`icmpPing` returns (the blocking work), before the fire-and-forget `launch(Dispatchers.Main)` UI update completes — matches existing `testAllTcping` pattern; fine.
- ICMP `-W "$timeoutSec"` is seconds on Android toybox ping — correct.
