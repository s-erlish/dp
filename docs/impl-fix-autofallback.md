# Implementation fix plan — auto-fallback correctness (review-04) + outstanding review items

Doc only. No code changed by this file. Targets the `V2rayNG/` Kotlin app on branch
`claude/vpn-client-happ-design-mq51pv`.

File paths (absolute):
- `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt`
- `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/viewmodel/MainViewModel.kt`
- `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt`
- `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt`

---

## 0. Current state (important — read first)

The review-04 **BLOCKER (reconnect loop)** and its companion **HIGH (state lost on
recreate)** are **already fixed** on this branch by commit
`a68139a fix(resilience): prevent auto-fallback reconnect loop (review-04 BLOCKER)`.

What that commit changed (verified against current source):

| Design point (from the task) | Status | Where |
|---|---|---|
| Move "already fell back this session" flag off the Activity and into the ViewModel so it survives the fallback's own restart **and** Activity recreate | **DONE** | `MainViewModel.autoFallbackUsed` (`MainViewModel.kt:66`); Activity's old `autoFallbackDone` field deleted |
| `STOP_SUCCESS → isRunning=false` must NOT reset the flag | **DONE** | `isRunning` observer now calls `cancelHealthCheck()` (which does **not** touch the flag), not the old `resetAutoFallback()` (`MainActivity.kt:202-205`, `:411-415`) |
| Reset only on a genuine user disconnect / new user connect | **DONE** | `autoFallbackUsed = false` set in `handleFabAction()` (`:247`) and `triggerFastConnect()` (`:284`) only |
| Exclude the just-failed server from the fallback selection | **DONE** | `selectFastestServer(excludeGuid)` (`MainViewModel.kt:349-362`), threaded via `fastConnect(excludeGuid)` (`:337-341`) and the observer's `fastConnect(excludeGuid = MmkvManager.getSelectServer())` (`MainActivity.kt:215`) |
| Require a clear failure (`time < 0`) before falling back | **DONE** | observer condition `... && time < 0 && ...` (`MainActivity.kt:210`) |
| Mark used **before** the restart so the restart's own `START_SUCCESS` can't re-arm | **DONE** | `mainViewModel.autoFallbackUsed = true` set before `fastConnect(...)` (`:212-215`) |
| Gate the shared `MSG_MEASURE_DELAY` channel so a manual "test connection" tap can't be mis-consumed as a health-check result | **OPEN** | see §2 |
| Require N consecutive failures / re-probe before switching (avoid switching on one transient test-URL blip) | **OPEN** | see §3 |
| `fallbackInProgress` flag to make the internal-restart-vs-user-stop distinction explicit | **OPEN (hardening, recommended)** | see §1 |
| On "no other server qualifies": stop / no restart | **PARTIAL** — no restart already holds; the stalling tunnel is currently left up | see §4 |

### How the loop is prevented (definitive)

Session invariant introduced by `a68139a`:

> `MainViewModel.autoFallbackUsed` is set to `true` exactly once, **before** the
> fallback restart is issued, and is cleared **only** by an explicit user action
> (`handleFabAction` / `triggerFastConnect`). Nothing on the internal stop→start
> path clears it.

Trace with the fix in place (the exact RKN "connect then stall" case):

1. User connects → `handleFabAction` sets `autoFallbackUsed=false` → `START_SUCCESS`
   → `isRunning=true` → `scheduleHealthCheckIfEnabled()` arms a one-shot +7s check
   (allowed because `autoFallbackUsed==false`).
2. +7s → `healthCheckRunnable` → `healthCheckPending=true` → `testCurrentServerRealPing()`
   → core `measureDelay` returns `-1` (tunnel stalls) → `MSG_STATE_DELAY_RESULT(-1)`.
3. `delayResultAction` observer: `healthCheckPending` true→false; guard
   `enabled && !autoFallbackUsed && time<0 && isRunning` passes → **set
   `autoFallbackUsed=true`** → `fastConnect(excludeGuid = current)`.
4. `onTestsFinished` → `selectFastestServer(excludeGuid)` returns another guid (or `null`)
   → `fastConnectAction` fires once (`consumeFastConnectEvent()`).
5. Observer → `restartV2Ray()` → `stopVService()` → `STOP_SUCCESS` → `isRunning=false`
   → `cancelHealthCheck()` **(does NOT reset `autoFallbackUsed`)** → +500ms → `startV2Ray()`
   → `START_SUCCESS` → `isRunning=true` → `scheduleHealthCheckIfEnabled()` → **returns
   immediately because `autoFallbackUsed==true`. No second health check is armed.**
6. Terminates. At most **one** fallback switch per user-initiated session.

Recreate (rotation / theme change): `autoFallbackUsed` lives in the retained ViewModel,
so a replayed `isRunning=true` cannot re-arm a fresh fallback after one already fired —
`scheduleHealthCheckIfEnabled()` sees `autoFallbackUsed==true` and no-ops.

The remaining sections harden and complete the design; they are **not** required to stop
the loop (the loop is already stopped), except that §2 closes a real false-positive path
that can *trigger* a fallback the user didn't warrant.

---

## 1. `fallbackInProgress` flag — make the internal-vs-user distinction explicit (recommended hardening)

**Why, given the loop is already fixed:** today the anti-loop invariant depends on the
fact that *no code path resets `autoFallbackUsed` on disconnect*. That is correct but
implicit — a future edit that adds a reset on `isRunning==false` (a natural-looking
change) would silently reintroduce the loop. An explicit `fallbackInProgress` flag makes
the intent legible and lets the disconnect handler safely reset session state for **user**
disconnects later without endangering the internal restart.

### 1a. `MainViewModel.kt` — add the flag

Near `autoFallbackUsed` (`MainViewModel.kt:66`):

```kotlin
// True only while the auto-fallback's own stop→start restart is in flight. Lets the
// disconnect handler tell an internal restart apart from a genuine user disconnect.
var fallbackInProgress = false
```

### 1b. `MainActivity.kt` — set it around the internal restart

In the `delayResultAction` observer (`:210-216`), set the flag when the fallback commits,
before requesting the switch:

- Before:
```kotlin
if (enabled && !mainViewModel.autoFallbackUsed && time < 0 && mainViewModel.isRunning.value == true) {
    mainViewModel.autoFallbackUsed = true
    toast(getString(R.string.auto_fallback_switching))
    mainViewModel.fastConnect(excludeGuid = MmkvManager.getSelectServer())
}
```
- After:
```kotlin
if (enabled && !mainViewModel.autoFallbackUsed && time < 0 && mainViewModel.isRunning.value == true) {
    mainViewModel.autoFallbackUsed = true
    mainViewModel.fallbackInProgress = true   // next stop→start is internal, not a user stop
    toast(getString(R.string.auto_fallback_switching))
    mainViewModel.fastConnect(excludeGuid = MmkvManager.getSelectServer())
}
```

Clear it once the internal restart has completed (or produced no candidate). Two spots:

- In the `fastConnectAction` observer, `guid == null` branch (`:189-193`) — the switch was
  abandoned, so the restart is over:
```kotlin
if (guid == null) {
    mainViewModel.fallbackInProgress = false
    setTestState(getString(R.string.connection_test_fail))
    toastError(R.string.toast_services_failure)
    return@observe
}
```
- In `scheduleHealthCheckIfEnabled()` (`:404-409`), at the point the post-restart
  `START_SUCCESS` re-enters — the restart has landed, clear it here:
```kotlin
private fun scheduleHealthCheckIfEnabled() {
    mainViewModel.fallbackInProgress = false   // internal restart (if any) has landed
    if (mainViewModel.autoFallbackUsed) return
    if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)) return
    timerHandler.removeCallbacks(healthCheckRunnable)
    timerHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_DELAY_MS)
}
```

### 1c. `MainActivity.kt` — guard the disconnect handler with it

`cancelHealthCheck()` currently (correctly) never resets the session flag. Keep that, but
document the invariant with the new flag so the coupling is explicit:

- Before (`:411-415`):
```kotlin
/** Cancels a pending health check (on disconnect) without clearing the session flag. */
private fun cancelHealthCheck() {
    healthCheckPending = false
    timerHandler.removeCallbacks(healthCheckRunnable)
}
```
- After:
```kotlin
/**
 * Cancels a pending health check. On a *genuine* user disconnect (not the fallback's own
 * internal restart) it also resets the once-per-session fallback state.
 */
private fun cancelHealthCheck() {
    healthCheckPending = false
    timerHandler.removeCallbacks(healthCheckRunnable)
    if (!mainViewModel.fallbackInProgress) {
        // A real user stop ends the session; a new connect starts fresh anyway via
        // handleFabAction, but resetting here keeps state tidy for programmatic stops.
        mainViewModel.autoFallbackUsed = false
    }
}
```

**Safety check for this edit:** the only `isRunning==false` that occurs *while*
`fallbackInProgress==true` is the fallback's own `STOP_SUCCESS`, so `autoFallbackUsed` is
preserved exactly across the internal restart — the loop invariant of §0 still holds. For a
user-initiated stop `fallbackInProgress` is `false`, so resetting is correct. (This is why
the flag must be set in 1b *before* `fastConnect` and cleared in 1a/1b only after the
restart re-enters.)

> If you prefer the smaller diff, §1 can be skipped entirely — the loop is already
> prevented by `a68139a`. Adopt §1 only for the explicitness/future-proofing benefit.

---

## 2. Gate the shared `MSG_MEASURE_DELAY` channel (REQUIRED — closes a real false-fallback path)

**Problem (review-04 MEDIUM).** Both the health check (`healthCheckRunnable →
testCurrentServerRealPing`) and the manual "test connection" tap (`handleLayoutTestClick →
testCurrentServerRealPing`) send the same `MSG_MEASURE_DELAY`, and the core replies on the
same `MSG_STATE_DELAY_RESULT(Long)` for both. The Activity distinguishes them only by the
`healthCheckPending` boolean. If the user taps "test connection" while a health check is in
flight, the tap's `Long` result arrives first, satisfies `healthCheckPending`, and is
consumed as the health-check verdict — a transiently blocked test URL (`-1`) then triggers
a fallback the tunnel didn't actually warrant (and the real health result is dropped).

`MSG_STATE_DELAY_RESULT` has exactly two touch points: emitted at
`CoreServiceManager.kt:380`, consumed at `MainViewModel.kt:622-626`. Nothing else reads it.
So the clean, minimal fix is: **the core emits `MSG_STATE_DELAY_RESULT` only for a
health-check-tagged request; manual taps emit only the human-readable
`MSG_MEASURE_DELAY_SUCCESS`.** After this, the `Long` channel carries health-check results
exclusively and cross-consumption is impossible.

### 2a. `AppConfig.kt` — add a request tag constant

After the `MSG_*` block (near `MainViewModel.kt`/`AppConfig.kt:172-174`):

```kotlin
/** Marks a MSG_MEASURE_DELAY request as the auto-fallback health check (vs a manual tap). */
const val HEALTH_CHECK_TAG = "auto_fallback_health_check"
```

### 2b. `MainViewModel.kt` — dedicated health-check request method

Leave `testCurrentServerRealPing()` (`:264-266`) unchanged (manual tap, content `""`). Add:

```kotlin
/**
 * Real-ping the current server for the auto-fallback health check. Tags the request so the
 * core echoes the numeric verdict on MSG_STATE_DELAY_RESULT only for this path, keeping it
 * off the shared channel used by a manual "test connection" tap.
 */
fun testCurrentServerHealthCheck() {
    MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, AppConfig.HEALTH_CHECK_TAG)
}
```

`sendMsg2Service(ctx, what, content: Serializable)` accepts the `String` tag directly
(`MessageUtil.kt:23`); the manual path already passes a `String` (`""`).

### 2c. `MainActivity.kt` — health check uses the tagged request

In `healthCheckRunnable` (`:61-66`):

- Before: `mainViewModel.testCurrentServerRealPing()`
- After: `mainViewModel.testCurrentServerHealthCheck()`

(`handleLayoutTestClick` keeps calling `testCurrentServerRealPing()` — the manual path.)

### 2d. `CoreServiceManager.kt` — emit the numeric result only for the tagged request

Receiver dispatch (`:517-519`):

- Before:
```kotlin
AppConfig.MSG_MEASURE_DELAY -> {
    measureV2rayDelay()
}
```
- After:
```kotlin
AppConfig.MSG_MEASURE_DELAY -> {
    val isHealthCheck = intent.getStringExtra("content") == AppConfig.HEALTH_CHECK_TAG
    measureV2rayDelay(isHealthCheck)
}
```

`measureV2rayDelay` (`:348-389`) — take the flag and gate the numeric emit:

- Before:
```kotlin
private fun measureV2rayDelay() {
    ...
    MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, result)
    // Numeric result for the auto-fallback health check (ms, or -1 on failure).
    MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_DELAY_RESULT, time)
    ...
}
```
- After:
```kotlin
private fun measureV2rayDelay(isHealthCheck: Boolean = false) {
    ...
    MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, result)
    // Numeric result is consumed by the auto-fallback health check only; keep it off the
    // shared channel for a manual "test connection" tap so results can't cross-consume.
    if (isHealthCheck) {
        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_DELAY_RESULT, time)
    }
    ...
}
```

**Result:** the `delayResultAction` observer now only ever receives health-check verdicts;
its existing `healthCheckPending` gate becomes belt-and-suspenders. A manual tap can no
longer be mis-consumed, and a manual `-1` can no longer trigger a fallback.

> Optional extra tidiness: with 2d in place, `MainActivity` could also skip issuing a manual
> `testCurrentServerRealPing()` while `healthCheckPending==true`, but it is no longer
> necessary for correctness.

---

## 3. Require a re-probe before switching (REQUIRED-ish — avoids switching a healthy tunnel on one blip)

**Problem (review-04 MEDIUM).** A single `time < 0` at the 7s mark forces a full
restart + server switch. `measureDelay` only retries the two delay-test URLs; a momentary
DNS hiccup or a transiently blocked test endpoint at exactly that instant looks identical
to a stalled tunnel. One transient failure should not tear down a working connection.

**Fix:** re-probe once ~2s later; fall back only if the confirmation probe also fails.
Keep it entirely in the Activity so no new message plumbing is needed.

### 3a. `MainActivity.kt` — add a one-shot confirmation state

Near the health-check fields (`:60`):

```kotlin
private var healthCheckConfirming = false   // second (confirmation) probe in flight
```

### 3b. `MainActivity.kt` — re-probe instead of switching on the first failure

`delayResultAction` observer (`:206-217`). On the first failure, arm a confirmation probe;
only switch if the confirmation also returns `< 0`:

```kotlin
mainViewModel.delayResultAction.observe(this) { time ->
    if (!healthCheckPending) return@observe
    healthCheckPending = false
    if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FALLBACK, true)) return@observe
    if (mainViewModel.autoFallbackUsed || mainViewModel.isRunning.value != true) return@observe
    if (time >= 0) { healthCheckConfirming = false; return@observe }   // tunnel healthy

    if (!healthCheckConfirming) {
        // First failure: confirm once before tearing down a possibly-healthy tunnel.
        healthCheckConfirming = true
        timerHandler.postDelayed({
            if (mainViewModel.isRunning.value == true && !mainViewModel.autoFallbackUsed) {
                healthCheckPending = true
                mainViewModel.testCurrentServerHealthCheck()
            }
        }, HEALTH_CHECK_RECHECK_MS)
        return@observe
    }

    // Second consecutive failure: commit the fallback.
    healthCheckConfirming = false
    mainViewModel.autoFallbackUsed = true
    mainViewModel.fallbackInProgress = true            // only if §1 adopted
    toast(getString(R.string.auto_fallback_switching))
    mainViewModel.fastConnect(excludeGuid = MmkvManager.getSelectServer())
}
```

Add the companion constant (`:76-79`):

```kotlin
const val HEALTH_CHECK_RECHECK_MS = 2000L
```

Reset `healthCheckConfirming = false` in `cancelHealthCheck()` (`:411-415`) alongside
`healthCheckPending` so a disconnect/reconnect starts clean. Also remove the confirmation
callback in `onDestroy` — since it is posted as an anonymous `Runnable` it can't be removed
by reference; either store it in a field like `healthCheckRunnable` and `removeCallbacks`
it in `cancelHealthCheck()`/`onDestroy()`, or (simpler) keep the anonymous post but guard
its body with the `isRunning`/`autoFallbackUsed` checks shown above (already done) so a
late fire is inert. **Preferred:** promote it to a named field for symmetric cleanup:

```kotlin
private val healthRecheckRunnable = Runnable {
    if (mainViewModel.isRunning.value == true && !mainViewModel.autoFallbackUsed) {
        healthCheckPending = true
        mainViewModel.testCurrentServerHealthCheck()
    }
}
```
…then `timerHandler.postDelayed(healthRecheckRunnable, HEALTH_CHECK_RECHECK_MS)` and add
`timerHandler.removeCallbacks(healthRecheckRunnable)` to both `cancelHealthCheck()` and
`onDestroy()` (`:899-905`).

**Anti-loop preserved:** the confirmation probe reuses `autoFallbackUsed`/`isRunning`
guards, so it cannot re-arm after a fallback has committed, and it is a single re-probe
(not a repeating timer). Net effect: fallback fires only on **two** consecutive failures
~2s apart, still at most once per session.

---

## 4. "No other server qualifies" behaviour (optional)

Today, when `selectFastestServer(excludeGuid)` returns `null` (no *other* server has a
valid latency), the `fastConnectAction` observer shows a failure toast and does **not**
restart — so there is no loop. It leaves the current (stalling) tunnel running.

The task's phrasing ("if no other server qualifies, stop (no restart), toast") suggests
also tearing the tunnel down. This is a product choice:

- **Keep-up (current):** safer default — the user stays connected to *something*; a stalled
  tunnel may still recover, and abruptly dropping the VPN can be worse than a slow one.
- **Stop:** matches the literal ask. If desired, in the `guid == null` branch
  (`MainActivity.kt:189-193`) add `CoreServiceManager.stopVService(this)` before the toast.

Recommendation: keep-up + a clearer toast (e.g. reuse `R.string.connection_test_fail` +
`toast_services_failure`, already present). Do **not** restart (that is the loop guard).
Document the decision in the commit; either way the no-restart property is the load-bearing
one and it already holds.

---

## 5. Other outstanding MEDIUM / LOW items (review-01 … review-04)

Grouped by whether they are still live on the current source.

### Already resolved on this branch (no action)
- **review-01 HIGH — `fastConnectAction` replays on recreate and auto-restarts VPN.** Fixed:
  `fastConnectEventPending` + `consumeFastConnectEvent()` one-shot guard
  (`MainViewModel.kt:60,72-76`; `MainActivity.kt:188`).
- **review-01 MEDIUM — uptime timer resets to 00:00:00 on recreate.** Fixed: start epoch
  persisted in MMKV (`KEY_CONNECTION_START`) and reused (`MainActivity.kt:380-398`).

### Still open — auto-fallback (covered above)
- review-04 MEDIUM — shared `MSG_MEASURE_DELAY` channel cross-consume → **§2**.
- review-04 MEDIUM — single transient failure switches a working tunnel → **§3**.

### Still open — subscription meta bar (review-02)
- **MEDIUM — ping spinner is a fixed 3s timer decoupled from real completion.**
  `GroupServerFragment.pingSub()` shows `progressAction` then hides it via
  `binding.root.postDelayed({…}, 3000L)` (`GroupServerFragment.kt:256-265`), unrelated to
  when `testAllServers()` actually finishes. Fix: drive the spinner from real completion —
  observe `mainViewModel.updateTestResultAction`/a running flag (the VM already emits
  `MSG_MEASURE_CONFIG_FINISH → onTestsFinished`), or drop the spinner and rely on per-row
  delay updates. Also `removeCallbacks` in `onDestroyView` to drop the transient
  fragment retain (review-02 LOW).
- **MEDIUM — refresh acts on `mainViewModel.subscriptionId`, not the fragment's `subId`.**
  `refreshSub()` calls `mainViewModel.updateConfigViaSubAll()` (`GroupServerFragment.kt:237`),
  which operates on the shared `subscriptionId`. Kept in sync via `onResume →
  subscriptionIdChanged(subId)` (`:99-112`), but the coupling is implicit. Fix: pass `subId`
  to a scoped refresh API, or assert `mainViewModel.subscriptionId == subId` before
  refreshing.
- **LOW — `skipCount`-only refresh result shows no toast** (disabled/invalid sub silently
  does nothing). Add an `else`/`skipCount > 0` toast branch.
- **LOW — nested `launch(IO){ launch(Main){…} }` in `refreshSub` writes the binding without
  an `isBindingInitialized` guard** (`GroupServerFragment.kt` refresh block). Add the guard
  used elsewhere for consistency.
- **LOW — `SubscriptionItem.hasUserInfo` can't distinguish "header absent" from
  "present but all-zero"**; an all-zero unlimited plan renders no traffic row. Track header
  presence explicitly if this matters.

### Still open — ping methods (review-03 / review-04 polish)
- **review-04 MEDIUM — `https://host:port/` reachability probe misclassifies non-TLS
  nodes.** Many VMess/VLESS/Trojan nodes don't speak HTTPS on their transport port → TLS
  handshake fails → `-1` even though reachable. `testAllDirectHttp()`
  (`MainViewModel.kt:283-306`). Fix/mitigation: fall back to a raw TCP connect when the HTTP
  probe fails, or clearly label this mode "internet reachability", not a per-node metric.
  (Note: this mode is already excluded from fast-connect/auto-select, which is correct.)
- **review-03 LOW — HEAD vs "GET" wording drift** in `SpeedtestManager.httpPing` / the
  `ping_method_http` string / KDoc. Align wording (say HEAD) or switch to `.get()`.
- **review-03 LOW — ICMP path fails for IPv6 literals** (`/system/bin/ping` is IPv4).
  Detect IPv6 and use `ping -6`/`ping6`, or document the limitation.

### Still open — cosmetic (review-01)
- **LOW — `handleFabAction` shows the "starting" style/label transiently on a STOP tap.**
  It calls `applyRunningState(isLoading = true, …)` unconditionally (`MainActivity.kt:244`).
  Only apply the loading style on the start branch (or use a neutral "stopping" label).
- **LOW — mono-theme dead colors / hardcoded hero-button colors** (review-01 MEDIUM+LOW).
  **Likely superseded**: the UI has since moved to the Incy dark theme
  (`4454ffb`, `d541aa4`). Re-verify against current `themes.xml`/`colors.xml` before acting;
  if the mono overlay is gone, delete the dead `mono_fab_active`/`mono_connected` resources.
- **LOW — adaptive icon foreground near the safe-zone edge**; scale the globe+sparkle group
  to ~70% within the inner 72dp safe zone.

### Still open — plumbing (review-04 LOW)
- **`getSerializableExtra(String) as? Long` deprecated on API 33+**
  (`MainViewModel.kt:623`). Guard with the typed
  `getSerializableExtra(name, Long::class.java)` overload behind an SDK check to clear the
  warning (also applies to the `LongArray` handler at `:629`). Compiles fine today; warning
  only.

---

## 6. Compile-safe commit plan

Each commit compiles independently; ordered so the required correctness fixes land first.

1. **`fix(resilience): tag health-check delay probe to isolate it from manual test`** — §2.
   Files: `AppConfig.kt` (+const), `MainViewModel.kt` (+`testCurrentServerHealthCheck()`),
   `MainActivity.kt` (`healthCheckRunnable` calls the tagged method),
   `CoreServiceManager.kt` (`measureV2rayDelay(isHealthCheck)` + receiver reads the tag).
   Self-contained; default param keeps any other `measureV2rayDelay()` caller valid (there
   are none besides the receiver). No resource/string changes.

2. **`fix(resilience): re-probe once before auto-fallback switches server`** — §3.
   Files: `MainActivity.kt` only (fields `healthCheckConfirming` + `healthRecheckRunnable`,
   observer logic, `HEALTH_CHECK_RECHECK_MS`, cleanup in `cancelHealthCheck`/`onDestroy`).
   Depends on commit 1 (`testCurrentServerHealthCheck`). No new strings required.

3. **`refactor(resilience): explicit fallbackInProgress guard`** — §1 (optional hardening).
   Files: `MainViewModel.kt` (+`fallbackInProgress`), `MainActivity.kt` (set/clear/guard).
   Independent of 1–2 except the observer edit overlaps §3's observer — apply §1's
   `fallbackInProgress = true` line inside §3's "second failure" branch to avoid a conflict.

4. **`fix(ui): drive sub ping spinner from real completion; scope refresh to subId`** —
   review-02 MEDIUMs. Files: `GroupServerFragment.kt` (+ optional scoped VM refresh API).
   Independent of 1–3.

5. **`fix(ping): TCP-connect fallback for non-TLS reachability probe`** — review-04 MEDIUM.
   Files: `MainViewModel.testAllDirectHttp` (+ `SpeedtestManager` TCP helper if not present).
   Independent.

6. **`chore: review-01…04 LOW cleanups`** — batch the LOWs (skipCount toast, binding guard,
   fab stop-label, IPv6 ICMP note, `getSerializableExtra` typed overload, dead mono
   resources if confirmed unused). Independent; split further if any single file is contentious.

Recommended minimum to close review-04's remaining correctness gap: commits **1 and 2**
(§2 required, §3 strongly recommended). Commit 3 (§1) is optional hardening. Commits 4–6
address the other reviews.

Post-change validation (no automated Android build available here — do these in CI / a
device build):
- `./gradlew :app:assembleDebug` (the branch already builds a debug APK in CI, `5bcde00`).
- Manual: connect to a node that "connects then stalls" and confirm exactly one fallback
  switch, then a stable state with no further restarts; tap "test connection" during the
  7s window and confirm it does **not** trigger a fallback (validates §2).
