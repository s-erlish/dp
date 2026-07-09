# Ping / Connection-Test Methods — Design

Design for adding **4 user-selectable ping methods** to *departament VPN* (v2rayNG / Xray
fork, `com.v2ray.ang`). The goal is to match how real clients (v2rayNG, Happ, Hiddify,
NekoBox/sing-box, Clash/Mihomo) measure connection quality, and to let the user pick the
method that fits their situation (latency-of-the-tunnel vs. reachability-of-the-endpoint).

> Scope: this is a design document only. No code is changed here. File paths below are the
> exact places the implementation should touch.

---

## 0. What exists today

Two measurement paths already ship:

| Path | Code | What it measures | Through proxy? |
|------|------|------------------|----------------|
| **TCP "tcping"** | `SpeedtestManager.tcping()` → `socketConnectTime()` (`app/src/main/java/com/v2ray/ang/handler/SpeedtestManager.kt`) | Time to complete a raw TCP 3-way handshake to `server:port`, best of 2 tries, 3 s connect timeout | No — direct from the phone to the node's IP |
| **"Real ping"** (URL delay through the core) | Batch: `MainViewModel.testAllRealPing()` → `CoreTestService` → `RealPingWorkerService.startRealPing()` → `CoreNativeManager.measureOutboundDelay(config, url)` (`Libv2ray.measureOutboundDelay`). Current server: `CoreServiceManager.measureV2rayDelay()` → `coreController.measureDelay(url)` | Time for an HTTP request to the delay-test URL to complete **through a throw-away Xray instance built from that server's config** | Yes — full tunnel (TLS/REALITY/mux/fragment all exercised) |

Supporting facts already in the tree:

- Delay-test URL is user-configurable: `AppConfig.DELAY_TEST_URL = "https://www.gstatic.com/generate_204"`, fallback `DELAY_TEST_URL2 = "https://www.google.com/generate_204"`, read via `SettingsManager.getDelayTestUrl(second)` from pref `pref_delay_test_url` (`AppConfig.PREF_DELAY_TEST_URL`).
- Batch concurrency is user-configurable: `SettingsManager.getRealPingConcurrency()` (pref `pref_real_ping_concurrency`, default 16, clamp 1..128); `RealPingWorkerService` spins a fixed thread pool of that size.
- Results are persisted per server as `ServerAffiliationInfo.testDelayMillis` (`app/src/main/java/com/v2ray/ang/dto/entities/ServerAffiliationInfo.kt`) via `MmkvManager.encodeServerTestDelayMillis(guid, ms)`; `-1` = failed, `0` = untested.
- "Fast connect" picker (`MainViewModel.fastConnect()` → `selectFastestServer()`) chooses the smallest **positive** `testDelayMillis`, so any new method must write the same store with the same `-1`/positive convention.

So the app already has: (a) a direct TCP connect probe, and (b) a through-proxy HTTP/204
probe. The redesign turns these into an explicit, named, user-selectable set of **four**
methods and adds an **ICMP** probe and a **direct HTTP/204** probe.

---

## 1. The four recommended methods

The four methods answer different questions. Two are **direct** (phone → node IP, no tunnel;
cheap, but only tells you the node is reachable / the network path RTT). Two go **through the
tunnel** (expensive, but tells you the latency a real request would actually see).

### Method A — TCP connect ("tcping") — *direct*

- **Measures:** wall-clock time to complete the TCP 3-way handshake (SYN → SYN/ACK → the
  local stack's ACK) to `node_host:node_port`. Best-of-N, min value. This is one network RTT
  plus the remote's accept latency — it does **not** include TLS, REALITY, or any proxy
  protocol.
- **Endpoint:** the node's own address/port (from the outbound profile). No external URL.
- **DNS:** if `node_host` is a domain, the phone's system resolver resolves it first (that
  DNS time is *not* included — `socketConnectTime` starts the clock after `Socket()` and
  measures only `connect()`). A domain that fails to resolve → `UnknownHostException` → `-1`.
- **Pros:** fastest and cheapest; no core startup, no config parsing, high parallelism; great
  for quickly pruning dead nodes from a large subscription.
- **Cons:** says nothing about whether the *proxy* works — a node whose TCP port is open but
  whose REALITY/TLS is misconfigured, blocked by DPI, or throttled will still show a low
  "ping". CDN-fronted nodes (Cloudflare, etc.) answer TCP from the nearest edge, so the number
  reflects edge RTT, not the real exit. Blocked by carriers that RST after handshake would
  still measure the handshake.
- **When meaningful:** first-pass triage of many servers; comparing nodes that share a
  protocol; environments where you trust that "port open ⇒ proxy works".
- **Reuses:** `SpeedtestManager.tcping()` / `socketConnectTime()` almost unchanged.

### Method B — HTTP / URL test to a generic 204 endpoint — *direct* (new)

- **Measures:** wall-clock time from issuing an HTTP(S) `GET`/`HEAD` to a captive-portal-style
  **`generate_204`** endpoint until the response status line is received, made **directly**
  from the phone (no proxy). Includes DNS (optional), TCP connect, TLS handshake (for
  `https://`), request write, and **time-to-first-byte** of the response headers. The body is
  empty by design.
- **Endpoint (default):** `http://www.gstatic.com/generate_204`. Alternatives to expose:
  `https://www.gstatic.com/generate_204`, `http://cp.cloudflare.com/generate_204`,
  `http://connectivitycheck.gstatic.com/generate_204`,
  `http://edge-http.microsoft.com/captiveportal/generate_204`, `http://clients3.google.com/generate_204`.
- **Why `generate_204`:** these URLs return **HTTP 204 No Content** — a success with a
  zero-length body. They are backed by huge, always-up anycast infra (Google/Cloudflare),
  are the exact endpoints Android itself uses for captive-portal detection, and cost the
  server nothing, so timing reflects the *network*, not server render time. Success = the
  status code equals the expected code (`204`); anything else (200 login page, 302 to a
  captive portal, timeout) = failure. Cite: v2rayNG uses `https://www.google.com/generate_204`
  as the default test URL and lists `gstatic`/`clients3` alternatives; Mihomo's health-check
  default is `https://www.gstatic.com/generate_204` and supports an `expected-status` field.
- **Connect-vs-first-byte:** measure **time-to-first-byte** (status line received), not
  connect-only — that's what "URL latency" means in Clash/sing-box and it captures TLS cost.
  For an empty 204 body, TTFB ≈ TTLB, so no need to read the body.
- **DNS:** for `http(s)://<domain>/generate_204` the phone resolves the domain each run.
  To keep the number stable and comparable, either (a) accept that DNS is included (matches
  real "can I reach the internet" semantics), or (b) pre-resolve once and reuse — see §3.
- **Pros:** tells you whether the *phone's current network* can reach a real internet endpoint
  and how fast, using the same probe real captive-portal checks use; distinguishes "TCP open
  but HTTP hijacked/redirected" from "genuinely online". Cheap-ish (no core start).
- **Cons:** it is **direct**, so it measures the phone→internet path, *not* the node and
  *not* the tunnel. On a censored network the direct probe may itself be blocked/hijacked
  (that's actually useful signal, but it's not a per-node metric). Not a good "which node is
  fastest" ranker on its own — every node would get roughly the same direct number.
- **When meaningful:** a connectivity sanity check ("is my base internet up before I blame the
  proxy?"), and as the *building block* re-used by Method D (same probe, but pointed through
  the proxy). Best offered as a global check rather than a per-node ranker.
- **Reuses:** `HttpUtil` + OkHttp, but with `httpPort = 0` (no proxy) and timing wrapped
  around `client.newCall(...).execute()`.

### Method C — ICMP ping — *direct* (new)

- **Measures:** ICMP Echo Request/Reply round-trip time to the node's IP — the classic
  `ping`. Pure network-layer RTT, no transport/TLS at all.
- **Endpoint:** the node's IP (resolve `node_host` first; ICMP has no ports).
- **Implementation reality on Android:** `InetAddress.isReachable(timeout)` is **unreliable** —
  it needs a raw socket (root) for real ICMP and otherwise falls back to a TCP-echo (port 7)
  probe that almost always fails, and its timeout is honored inconsistently across OEMs. The
  robust approach used by network tools is to shell out to the system binary:
  `Runtime.exec("/system/bin/ping -c 1 -W <sec> <ip>")` and parse the `time=xx.x ms` field
  (or exit code for reachable/not). This works without root because the `ping` binary is
  setuid/uses `IPPROTO_ICMP` datagram sockets on modern Android.
- **Pros:** lightest possible latency signal; familiar; independent of any application-layer
  blocking.
- **Cons:** **Most VPN/proxy nodes and CDNs drop ICMP**, and many carriers filter it, so
  a large fraction of nodes return "no reply" even though the proxy works perfectly — ICMP
  failure ≠ node down. Parsing `ping` output is locale/format fragile; behind a CDN the reply
  comes from the edge, not the exit. Because of this, ICMP should be an **opt-in** method, never
  the default, and its `-1` must **not** be treated as "node dead" by auto-sort/fast-connect
  the way the other methods' `-1` is.
- **When meaningful:** diagnosing raw network reachability / packet loss to a node you control;
  comparing nodes you *know* answer ICMP.

### Method D — Proxied real-delay (URL test through the running core) — *through tunnel*

- **Measures:** time for an HTTP request to the `generate_204` URL to complete **through an
  Xray instance built from that node's full config** — DNS-in-tunnel + TCP + TLS/REALITY +
  proxy protocol handshake + one RTT to the origin. This is the "true delay" the user's real
  traffic experiences.
- **Endpoint:** `SettingsManager.getDelayTestUrl()` (`gstatic/generate_204`), with the
  `google/generate_204` fallback already wired in `measureV2rayDelay()`.
- **How it's implemented in Xray:** `Libv2ray.measureOutboundDelay(config, url)` strips the
  inbounds from the config, starts a temporary core, and issues an HTTP request (Xray uses a
  `HEAD` with `Connection: close`, `Accept-Encoding: gzip`, ~5 s handshake timeout) through the
  outbound dialer, returning elapsed ms or `-1`. This is exactly what `RealPingWorkerService`
  (batch) and `CoreServiceManager.measureDelay` (current server) already call.
- **Pros:** the only method that actually validates the proxy end-to-end; correctly ranks
  nodes for "which tunnel is fastest"; catches TLS/REALITY/SNI/expiry failures that TCP/ICMP
  miss.
- **Cons:** expensive — each probe parses config and boots a core instance, so it is CPU/mem
  heavy and must be concurrency-limited (see §3); slowest of the four; if the test URL is
  itself blocked at the exit, a good node looks bad (hence the URL is user-configurable).
- **When meaningful:** the **default** method and the one "fast connect" should rely on; final
  ranking before choosing a server; verifying a node truly works.
- **Reuses:** `RealPingWorkerService` / `CoreTestService` unchanged.

### Summary matrix

| | A. TCP connect | B. HTTP/204 direct | C. ICMP | D. Proxied real-delay |
|---|---|---|---|---|
| Layer | TCP handshake | HTTP TTFB (direct) | ICMP echo | HTTP TTFB (in-tunnel) |
| Target | node ip:port | gstatic/cf 204 URL | node ip | 204 URL via core |
| Through proxy | No | No | No | **Yes** |
| Validates proxy works | No | No | No | **Yes** |
| Cost / mem | Very low | Low | Very low | **High (boots core)** |
| Good ranker for "fastest node" | Rough | No | Rough | **Yes** |
| Default | | | | **✓** |

---

## 2. Implementation design in this codebase

### 2.1 `PingMethod` enum

Add `app/src/main/java/com/v2ray/ang/enums/PingMethod.kt` (package already used for
`NotificationChannelType` etc.):

```kotlin
package com.v2ray.ang.enums

enum class PingMethod(val prefValue: String) {
    TCP_CONNECT("tcp"),        // Method A — SpeedtestManager.tcping
    HTTP_URL("http"),          // Method B — direct HTTP GET to generate_204
    ICMP("icmp"),              // Method C — system ping
    PROXIED_REAL_DELAY("real"); // Method D — measureOutboundDelay (default)

    companion object {
        fun fromPref(v: String?) = entries.firstOrNull { it.prefValue == v } ?: PROXIED_REAL_DELAY
    }
}
```

### 2.2 Settings — `pref_ping_method` ListPreference

- **AppConfig** (`app/src/main/java/com/v2ray/ang/AppConfig.kt`): add
  `const val PREF_PING_METHOD = "pref_ping_method"`. Keep existing `PREF_DELAY_TEST_URL`,
  `PREF_REAL_PING_CONCURRENCY`. Add ICMP/HTTP timeouts if desired
  (`const val PING_ICMP_TIMEOUT = 2` s, reuse 3 s for TCP as today).
- **`res/values/arrays.xml`**: add `ping_method_entries` (human labels: "Proxied real delay
  (through tunnel)", "HTTP GET /generate_204 (direct)", "TCP connect (tcping)", "ICMP ping")
  and `ping_method_values` (`real`, `http`, `tcp`, `icmp`).
- **`res/xml/pref_settings.xml`**: add next to the existing test-URL block (currently lines
  ~309–318):
  ```xml
  <ListPreference
      android:defaultValue="real"
      android:entries="@array/ping_method_entries"
      android:entryValues="@array/ping_method_values"
      android:key="pref_ping_method"
      android:summary="%s"
      android:title="@string/title_pref_ping_method" />
  ```
- **`SettingsManager`** (`handler/SettingsManager.kt`): add
  ```kotlin
  fun getPingMethod(): PingMethod =
      PingMethod.fromPref(MmkvManager.decodeSettingsString(AppConfig.PREF_PING_METHOD))
  ```
  and add `ensureDefaultValue(AppConfig.PREF_PING_METHOD, PingMethod.PROXIED_REAL_DELAY.prefValue)`
  next to the existing `ensureDefaultValue(PREF_DELAY_TEST_URL, ...)` call.
- **Default:** `real` (Method D) — preserves current "real ping" behavior for existing users.

### 2.3 A single dispatch point in `MainViewModel`

Today the UI calls `testAllTcping()` and `testAllRealPing()` from different menu items.
Introduce one entry point that both the "test all" action and `fastConnect()` use, and route
by the pref. In `app/src/main/java/com/v2ray/ang/viewmodel/MainViewModel.kt`:

```kotlin
fun testAllServers() = when (SettingsManager.getPingMethod()) {
    PingMethod.TCP_CONNECT        -> testAllTcping()          // existing
    PingMethod.HTTP_URL           -> testAllDirectHttp()      // new (§2.4)
    PingMethod.ICMP               -> testAllIcmp()            // new (§2.4)
    PingMethod.PROXIED_REAL_DELAY -> testAllRealPing()        // existing
}
```

- Keep `testAllTcping()` and `testAllRealPing()` as-is.
- `fastConnect()` should call `testAllServers()` instead of hard-coding `testAllRealPing()`,
  so the fast-connect picker honors the chosen method. (Recommendation: force `PROXIED_REAL_DELAY`
  for fast-connect regardless of pref, since only Method D validates the tunnel — or at least
  fall back to it when the pref is ICMP, whose `-1` is not a reliability signal. Document this
  choice in the menu.)
- Every path continues to write results through `MmkvManager.encodeServerTestDelayMillis(guid, ms)`
  and drive `updateListAction` / `MSG_MEASURE_CONFIG_SUCCESS`, so the RecyclerView and
  `getTestDelayString()` need no change.

### 2.4 New probes in `SpeedtestManager`

Both new *direct* methods belong next to `tcping` in
`app/src/main/java/com/v2ray/ang/handler/SpeedtestManager.kt`, so they share the
socket-tracking/cancel plumbing (`tcpTestingSockets`, `closeAllTcpSockets`) and the
`currentCoroutineContext().isActive` cancellation pattern already used by `tcping`.

**Method B — direct HTTP/204 (time-to-first-byte):**
```kotlin
suspend fun httpPing(url: String, timeoutMs: Int = 5000): Long {
    val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs.toLong(), MILLISECONDS)
        .readTimeout(timeoutMs.toLong(), MILLISECONDS)
        .callTimeout(timeoutMs.toLong(), MILLISECONDS)
        .followRedirects(false)          // a redirect = captive portal = failure
        .build()
    val req = Request.Builder().url(url).head()   // HEAD: no body, matches Xray's probe
        .header("Connection", "close").build()
    val start = System.nanoTime()
    return try {
        client.newCall(req).execute().use { r ->
            val ms = (System.nanoTime() - start) / 1_000_000
            if (r.code == 204 || r.code == 200) ms else -1L   // expected-status check
        }
    } catch (_: Exception) { -1L }
}
```
Notes: no `proxy(...)` set ⇒ direct. Stop the clock when the response object returns (headers
received = TTFB). Treat non-204/redirect/timeout as `-1`. This mirrors Mihomo's
`expected-status` / gstatic-204 convention and v2rayNG's default URL. Best-of-2 like `tcping`
is optional; a single sample is usually enough for a direct probe.

**Method C — ICMP via system `ping`:**
```kotlin
fun icmpPing(host: String, timeoutSec: Int = 2): Long {
    val ip = HttpUtil.resolveHostToIP(host)?.firstOrNull() ?: host   // reuse existing resolver
    return try {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", ip)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() == 0) {
            Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloat()?.toLong() ?: -1L
        } else -1L
    } catch (_: Exception) { -1L }
}
```
Do **not** use `InetAddress.isReachable` (unreliable on Android, needs root for real ICMP,
falls back to a TCP-echo probe that usually fails). Because most nodes drop ICMP, surface a
help string warning the user that `-1` here often means "ICMP filtered", not "node down".

`testAllDirectHttp()` / `testAllIcmp()` mirror `testAllTcping()` exactly: cancel the prior
scope, `MmkvManager.clearAllTestDelayResults(...)`, iterate `serversCache`, `launch` on
`tcpingTestScope` (bounded — see §3), write `encodeServerTestDelayMillis`, post
`updateListAction`. For Method B the target is the fixed `getDelayTestUrl()` (all nodes get a
similar direct number — expose it mainly as a per-network check, or point B at each node's own
`https://host/` if a per-node direct-HTTP number is wanted).

### 2.5 Result store & fast-connect

No schema change. All four methods funnel into `ServerAffiliationInfo.testDelayMillis` with the
existing convention: **positive = ms, `-1` = failed/unreachable, `0` = not yet tested.**
`selectFastestServer()` already picks the smallest value in `1 until MAX`, so it works
unchanged. Caveat to encode: for **ICMP**, a `-1` should not evict a node from fast-connect
consideration — either exclude ICMP from fast-connect (recommended) or have fast-connect
always run Method D as noted in §2.3.

---

## 3. Timeout / concurrency / parallelism (low memory & speed)

| Method | Per-probe timeout | Concurrency | Rationale |
|--------|-------------------|-------------|-----------|
| A. TCP connect | 3 s connect (current), best-of-2 | High — 32–64 parallel; sockets are cheap. Bound the `tcpingTestScope` with a `Semaphore` or a fixed dispatcher instead of unbounded `launch` per server (current code launches one coroutine per server with no cap — fine for tens of nodes, risky for thousands). | Cheap, IO-bound. |
| B. HTTP/204 direct | 5 s call timeout | Medium-high — 16–32; each holds one OkHttp connection. Share **one** `OkHttpClient` across all probes (connection/thread pool reuse) rather than building per call. | TLS adds a little CPU; still light. |
| C. ICMP | 2 s (`-W 2`), 1 packet | Medium — 8–16; each spawns a `ping` **process**, which is the expensive part. Cap process fan-out to avoid a fork storm on low-RAM devices. | Process spawn dominates cost. |
| D. Proxied real-delay | ~5 s (Xray handshake timeout) + overall guard | **Low — the existing `getRealPingConcurrency()` (default 16, clamp 1..128). On low-memory devices recommend 4–8.** Each probe boots a full core instance = the dominant RAM/CPU cost. `RealPingWorkerService` already uses `Executors.newFixedThreadPool(concurrency)` and is independently cancellable. | Booting cores is heavy; over-parallelizing OOMs. |

General guidance:
- **Reuse the concurrency knob per method.** Add `getPingConcurrency(method)` in `SettingsManager`
  returning high for A/B, medium for C, and the existing `getRealPingConcurrency()` for D, so a
  cheap method isn't throttled to D's low cap and D isn't blown up to A's high cap.
- **Bound the direct methods.** Replace the current "one unbounded `launch` per server" in
  `testAllTcping()` with a `kotlinx.coroutines.sync.Semaphore(limit)` (or a fixed dispatcher)
  so a 2000-node subscription doesn't open 2000 sockets/processes at once.
- **Single shared client** for Method B; single resolver cache for B/C (pre-resolve each host
  once, reuse the IP — avoids repeated DNS and keeps numbers comparable).
- **Cancellation:** honor `currentCoroutineContext().isActive` in every loop (as `tcping` does),
  and destroy the `ping` `Process`/close OkHttp calls on cancel; `SpeedtestManager.closeAllTcpSockets()`
  and `CoreTestService`'s worker-cancel already cover A and D.
- **Timeouts should be per-probe, not global**, so one dead node can't stall the batch; the
  batch's overall progress is already surfaced via `RealPingEvent.Progress` for D and
  `updateListAction` for A/B/C.

---

## 4. Small-commit implementation plan (real files)

1. **Enum + constants.** Add `enums/PingMethod.kt`; add `PREF_PING_METHOD` (and optional
   `PING_ICMP_TIMEOUT`) to `AppConfig.kt`. *(no behavior change)*
2. **Settings plumbing.** `SettingsManager.getPingMethod()` + `ensureDefaultValue(PREF_PING_METHOD, "real")`;
   add `ping_method_entries`/`ping_method_values` to `res/values/arrays.xml`; add the
   `<ListPreference android:key="pref_ping_method">` to `res/xml/pref_settings.xml`; add
   `title_pref_ping_method` (+ ICMP warning) strings to `res/values/strings.xml`.
3. **Direct HTTP probe.** Add `SpeedtestManager.httpPing()`; add `MainViewModel.testAllDirectHttp()`
   mirroring `testAllTcping()`. Wire nothing yet.
4. **ICMP probe.** Add `SpeedtestManager.icmpPing()` (system `ping`, reuse `HttpUtil.resolveHostToIP`);
   add `MainViewModel.testAllIcmp()`.
5. **Dispatch.** Add `MainViewModel.testAllServers()` routing on `getPingMethod()`; point the
   "test all" menu item in `MainActivity.kt` at it; keep the old menu items or fold them in.
6. **Concurrency hardening.** Add `SettingsManager.getPingConcurrency(method)`; bound
   `testAllTcping()/testAllDirectHttp()/testAllIcmp()` with a `Semaphore`; leave
   `RealPingWorkerService` (Method D) as-is.
7. **Fast-connect policy.** Make `fastConnect()` call `testAllServers()`, but force/ fall back
   to `PROXIED_REAL_DELAY` (and/or ignore ICMP `-1`) so the picker only trusts a
   proxy-validating metric. Verify `selectFastestServer()` still holds.
8. **Polish.** Optional per-method result glyph in `MainRecyclerAdapter`/`ServerAffiliationInfo`
   help text clarifying that direct methods (A/B/C) don't validate the tunnel and ICMP `-1`
   usually means "filtered".

Each step compiles and is independently reviewable; steps 1–2 are inert until step 5 flips the
dispatch.

---

## Sources

- v2rayNG default/alternative test URLs (`google/gstatic/clients3 generate_204`) and "realPing" via HTTP GET to `generate_204`: [2dust/v2rayNG #956](https://github.com/2dust/v2rayNG/issues/956), [#3110](https://github.com/2dust/v2rayNG/issues/3110), [#3325 (switch to Cloudflare)](https://github.com/2dust/v2rayNG/issues/3325), [#2850 (URL test option)](https://github.com/2dust/v2rayNG/issues/2850)
- Xray `MeasureOutboundDelay` / `measureInstDelay` (strips inbounds, boots core, HTTP `HEAD` with `Connection: close`, ~5 s handshake timeout, through the outbound dialer): [AndroidLibXrayLite libv2ray package](https://pkg.go.dev/github.com/2dust/AndroidLibXrayLite)
- Mihomo/Clash URL-test health check (`url` default `https://www.gstatic.com/generate_204`, `interval`, `tolerance`, `timeout`, `expected-status`, `unified-delay` = two probes to cancel out handshake): [mihomo docs — url-test](https://wiki.metacubex.one/en/config/proxy-groups/url-test/), [mihomo unified-delay discussion #1026](https://github.com/MetaCubeX/mihomo/issues/1026), [delay 问题 #1618](https://github.com/MetaCubeX/mihomo/issues/1618)
- `generate_204` semantics (204 No Content, captive-portal probe, Google/Cloudflare/Microsoft endpoints): [antonz.org — Am I online?](https://antonz.org/is-online/), [2dust/v2rayN test-ping-server discussion #3250](https://github.com/2dust/v2rayN/discussions/3250)
- Android ICMP: `InetAddress.isReachable` unreliable (raw socket/root, TCP-echo fallback, inconsistent timeout) vs. system `ping` binary via `Runtime.exec`/`ProcessBuilder`: [javathinking — ping external IP from Android](https://www.javathinking.com/blog/how-to-ping-external-ip-from-java-android/), [javathinking — isReachable never times out](https://www.javathinking.com/blog/how-to-check-internet-access-on-android-inetaddress-never-times-out/)
