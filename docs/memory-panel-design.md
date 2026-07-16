# Memory Panel & App-Wide Low-Memory Discipline — Design Doc

**Project:** "departament VPN" — a v2rayNG / Xray fork (Kotlin, Android). Source root: `/home/user/dp/V2rayNG`.
**Scope:** Design only. No code is changed by this document.
**Goal:** (A) a live, honest "MB used" panel for *this* app, and (B) a lightweight-by-default memory discipline across the whole product, with a forward look at future iOS and desktop (Windows/Linux) ports.

---

## 0. Context from the existing codebase

Facts established by reading the repo (referenced so the implementation lands in the right places):

- **Multi-process app.** The UI runs in the default process; the Xray core runs in a *separate* process and WorkManager runs in a third:
  - `AndroidManifest.xml` puts the VPN service and core components in `:RunSoLibV2RayDaemon` (10 declarations).
  - `AngApplication.kt` configures WorkManager with `setDefaultProcessName("${ANG_PACKAGE}:bg")`, i.e. process `:bg`.
  - So the app has (at least) **three** OS processes: `<pkg>` (UI), `<pkg>:RunSoLibV2RayDaemon` (core/VPN), `<pkg>:bg` (WorkManager). **Any honest memory number must account for per-process reporting** — see §1.4.
- **No memory instrumentation exists today.** The only reference is a commented-out `onLowMemory()` in `CoreVpnService.kt` (lines 88–90). There is no `onTrimMemory`, no `Debug.MemoryInfo` usage anywhere in `app/src/main/java`.
- **About screen** (`ui/AboutActivity.kt` + `res/layout/activity_about.xml`) is a simple vertical `LinearLayout` of clickable rows (`layout_soure_ccode`, `layout_feedback`, `layout_oss_licenses`, `layout_tg_channel`, `layout_privacy_policy`) ending in a version block (`tv_version`, `tv_app_id`). This is the natural home for a read-only memory card.
- **Settings screen** (`ui/SettingsActivity.kt`) is an androidx `PreferenceFragmentCompat` backed by MMKV (`MmkvPreferenceDataStore`), inflating `R.xml.pref_settings`. A live-updating number is awkward inside a `Preference` list (summaries don't animate cleanly), so About is the preferred mount; Settings is a fallback via a non-persistent `Preference` whose summary we refresh.
- **Config building** is centralized in `handler/AngConfigManager.kt` (plus `core/CoreServiceManager`), and settings persistence in `handler/MmkvManager.kt`. This centralization matters for the cross-platform note (§3).
- **App class:** `AngApplication : MultiDexApplication()` — the correct place to hook `onTrimMemory` / `ComponentCallbacks2` app-wide (§2).

---

## 1. The RAM panel

### 1.1 What we want to show

A small, read-only entry — a **card at the bottom of the About screen** — showing this app's current memory usage in MB, updating live (~every 2 s) while the screen is visible. One honest headline number plus an optional breakdown line.

Recommended display:

```
Memory usage
  38 MB            ← headline: total PSS of the UI process (honest "used")
  Java 12 · Native 9 · Code 5 · Graphics 4 · Other 8 MB   ← optional breakdown
  + core process 22 MB (when VPN connected)               ← optional, see §1.4
```

### 1.2 The Android memory APIs — what each actually measures

| API | What it returns | Meaning | Good for a headline "MB used"? |
|---|---|---|---|
| `Runtime.getRuntime().totalMemory() - freeMemory()` | Bytes of **Java/Kotlin heap** currently allocated (Dalvik/ART managed heap only) | Only managed objects. **Excludes** native allocations, the Xray core's C/Go memory, graphics, code, stack. | **No** — badly undercounts a VPN app whose bulk is native/core. |
| `Runtime.maxMemory()` | The ART heap *limit* for this process | Useful as the denominator for a "Java heap X / Y MB" gauge, not as usage. | Denominator only. |
| `ActivityManager.MemoryInfo` (`getMemoryInfo`) | **System-wide** free/total RAM, `lowMemory` flag, `threshold` | Whole-device state, not this app. Useful for context ("device low on RAM") but not our number. | No (device-level). |
| `Debug.MemoryInfo` via `Debug.getMemoryInfo(mi)` | Detailed PSS breakdown for the **calling process**: `dalvikPss`, `nativePss`, `otherPss`, `getTotalPss()`, plus `getMemoryStat("summary.*")` keys on API 23+ (`summary.java-heap`, `summary.native-heap`, `summary.code`, `summary.graphics`, `summary.stack`, `summary.private-other`, `summary.total-pss`, `summary.total-swap`). | Full picture of *one* process. `getMemoryInfo` is a synchronous local call — cheap enough for a 2 s cadence. | **Yes** — `getTotalPss()` is our headline; the `summary.*` keys give the breakdown line. |
| `ActivityManager.getProcessMemoryInfo(int[] pids)` | Array of `Debug.MemoryInfo`, one per PID | The **only** way to read PSS of a *different* process (e.g. the `:RunSoLibV2RayDaemon` core). Historically rate-limited: since Android 8 (API 26) a background app gets stale/throttled data (~once every 5 min) for processes; the **foreground app** reading its own/related PIDs is fine. Slightly heavier than `Debug.getMemoryInfo`. | Yes, but use sparingly and only for the *other* process (§1.4). |
| `android.os.Process.getTotalMemory()` / reading `/proc/self/status` (`VmRSS`) | **RSS** of the process | RSS counts full resident pages including shared library pages not proportionally divided — **overcounts** shared memory across processes. | No — PSS is the fairer "this app's share." |

**Java heap vs native vs PSS/RSS in one paragraph.** *Java heap* is the ART-managed heap (Kotlin/Java objects) — `Runtime` sees only this. *Native heap* is C/C++/Go allocations (the Xray core, tun2socks, MMKV, image codecs) — invisible to `Runtime`, visible in `Debug.MemoryInfo.nativePss` / `summary.native-heap`. *RSS* (Resident Set Size) is all physical pages the process has resident, counting each shared page in full. *PSS* (Proportional Set Size) is RSS but with shared pages divided by the number of processes sharing them (a page mapped by 4 processes adds 1/4 of its size to each). The ordering is `VSS > RSS > PSS > USS`. **PSS is the industry-standard "how much RAM does this app really cost the system" number**, which is exactly what `dumpsys meminfo` and Android Studio's profiler report. ([Perfetto memory case study](https://perfetto.dev/docs/case-studies/memory), [Greenspector metric guide](https://greenspector.com/en/android-memory-the-ultimate-metric-guide-2/), [Debug.MemoryInfo reference](https://developer.android.com/reference/android/os/Debug.MemoryInfo)).

### 1.3 Recommendation — display **total PSS in MB**

- **Headline = `Debug.MemoryInfo.getTotalPss()` (KB) / 1024 → MB**, rounded to a whole MB. This is the honest, defensible "MB used" and matches what a developer sees in `dumpsys meminfo <pkg>`.
- **Breakdown (optional, API 23+)** from `getMemoryStat("summary.java-heap" | "summary.native-heap" | "summary.code" | "summary.graphics" | "summary.stack" | "summary.private-other")`, each `/1024` MB. These sum (with swap) to `summary.total-pss`, so the row reconciles with the headline.
- **Do NOT** headline `Runtime` heap — for this app it will read ~10–15 MB while the real cost is 2–3× that once the native core, tun2socks, and graphics are counted. If we also want a "Java heap X / limit Y" mini-gauge, compute it from `Runtime` and label it explicitly as *Java heap only*.

### 1.4 Per-process reporting (the multi-process subtlety)

`Debug.getMemoryInfo()` only ever measures the **calling process**. When the panel runs in the UI process it reports the UI's ~30–40 MB and **cannot see** the Xray core's footprint in `:RunSoLibV2RayDaemon`. Two honest options:

1. **Simplest / recommended v1:** label the headline **"UI process"** and show only what the panel process can measure locally. Truthful and cheap.
2. **Fuller picture (optional v2):** additionally show the core process. To read another process's PSS you must call `ActivityManager.getProcessMemoryInfo(intArrayOf(corePid))`. Get the core PID by scanning `ActivityManager.runningAppProcesses` for the process whose `processName` ends in `:RunSoLibV2RayDaemon` (available when the VPN is connected). Caveats: (a) `runningAppProcesses` only returns *your own* app's processes, which is fine here; (b) `getProcessMemoryInfo` is throttled/expensive — call it at a **slower cadence (e.g. every 10 s, not 2 s)** and only while connected; (c) it may return 0/stale on some OEM builds — degrade gracefully to "n/a". Present it as a separate line ("+ core NN MB"), never silently folded into the headline, because the two numbers come from different processes and different sampling rates.

A truly total figure ("UI + core + bg") is the sum of the three processes' PSS, but since they are sampled at different instants/rates, present them as itemized lines rather than one blended number.

### 1.5 Where to mount it

- **Primary: About screen.** Add a new `layout_memory` card row to `res/layout/activity_about.xml` just above the version block (`tv_version`), matching the existing row style, with a headline `TextView` (`tv_mem_usage`) and an optional secondary `TextView` (`tv_mem_detail`). Drive it from `AboutActivity.kt`.
- **Fallback / additional: Settings.** If we prefer it under Settings, add a **non-persistent** `Preference` (no key, `isPersistent=false`, not selectable) to `R.xml.pref_settings` and refresh its `summary` on a timer from `SettingsFragment`. Given the androidx preference list isn't ideal for a ticking number, About is preferred; Settings can carry a static "current memory: NN MB (tap About for live)" if desired.
- Keep it **developer-honest, not alarmist**: it is an informational readout, not a gauge that turns red.

### 1.6 Update cadence & keeping it cheap

- **Cadence: 2 s while the screen is visible.** Start sampling in `onResume()` / `onStart()`, stop in `onPause()` / `onStop()`. Never sample when the screen is not foregrounded.
- Use a lifecycle-scoped coroutine: `lifecycleScope.launch { repeatOnLifecycle(STARTED) { while (isActive) { update(); delay(2000) } } }`. This auto-cancels with the lifecycle — no leaked handler, no work in the background.
- `Debug.getMemoryInfo()` is a cheap synchronous local syscall-ish call; at 0.5 Hz its cost is negligible. Do the formatting off the allocation-hot path (reuse a `StringBuilder`, avoid per-tick object churn — see §2 memory-churn note).
- The **core-process** line (§1.4 option 2) uses a **slower 10 s cadence** and only runs while the VPN is connected, to respect `getProcessMemoryInfo` throttling.
- Sampling must **never** itself allocate meaningfully or force GC. Do not call `Runtime.gc()`.

---

## 2. App-wide low-memory discipline

The product must stay light. Concrete, prioritized recommendations, most tied to what this codebase actually does.

### 2.1 Don't retain large config strings
- Xray/v2ray configs are large JSON strings built in `handler/AngConfigManager.kt` and handed to the core. **Build, hand off, drop.** Do not cache the full assembled JSON in a long-lived singleton, companion object, or static field. Let it be a local that goes out of scope after the core consumes it.
- Avoid keeping both the raw subscription text *and* the parsed model alive. MMKV is the source of truth (`MmkvManager`); read on demand rather than mirroring large blobs in memory.
- Beware `String` duplication from repeated `substring`/`+` on big configs; prefer streaming/`Reader` where the core API allows, and `StringBuilder` for assembly.

### 2.2 Bounded caches only
- Every cache must have a hard ceiling. Use `androidx.collection.LruCache` (or `SparseArray` for int keys) with an explicit max size; never an unbounded `HashMap` that grows with subscription/server count.
- Size caches in **bytes/entries**, and wire them into `onTrimMemory` (below) so they shrink under pressure. Caches of derived data (parsed configs, geo lookups, per-app icon/label maps in `AppManagerUtil`/`PerAppProxy`) are the usual offenders.

### 2.3 Image / flag memory
- Country flags and app icons in per-app-proxy lists are the biggest bitmap cost. Load through a library that pools and right-sizes (the project already uses Glide-style loading in list adapters); **request the display size**, not full-res, and let the loader's memory cache be the bounded owner.
- Prefer vector/`VectorDrawable` flags over per-country PNGs where possible — one drawable, no per-item bitmap.
- Recycle/avoid caching decoded `Bitmap`s yourself; a decoded 96×96 ARGB_8888 icon is ~37 KB, and a few hundred in a list adapter without view recycling is multiple MB. Ensure adapters recycle (RecyclerView) and clear image requests on view recycle.

### 2.4 Coroutine scoping (prevent leaks & runaway work)
- UI work → `lifecycleScope` / `viewModelScope`; it cancels with the screen. **Never** launch UI-tied work on `GlobalScope`.
- Long-lived background work belongs in the `:bg` WorkManager process (already configured), not in a leaked `GlobalScope` job in the UI process.
- The memory panel's own loop must be lifecycle-scoped (see §1.6) so it can't outlive the Activity.
- Avoid capturing `Activity`/`Context`/`View` in coroutines or callbacks that outlive them; use `applicationContext` for anything long-lived.

### 2.5 The core process footprint
- The Xray core in `:RunSoLibV2RayDaemon` is the heaviest single consumer (native + Go runtime). Keep it **only alive while connected** — it already lives in its own process, so when the VPN stops, that process should be torn down, returning its RAM to the system (verify the service fully stops rather than lingering).
- Reconsider re-enabling a form of the commented-out `onLowMemory()` in `CoreVpnService.kt`: under genuine system pressure it is reasonable for the core to trim buffers, though killing an active tunnel is user-hostile — prefer trimming caches over dropping the connection.
- Keep the core's config minimal (no unused inbounds/outbounds/rules) — fewer objects in the Go runtime = smaller footprint.

### 2.6 Trim on `onTrimMemory`
- Implement `ComponentCallbacks2.onTrimMemory(level)` in `AngApplication` (and optionally register components). Per Android guidance, focus on `TRIM_MEMORY_UI_HIDDEN` (UI backgrounded → drop UI-only bitmap/flag/icon caches) and `TRIM_MEMORY_BACKGROUND`/`_MODERATE`/`_COMPLETE` (release anything reconstructable). Release large, rebuildable allocations; keep essentials. ([Android: Manage your app's memory](https://developer.android.com/topic/performance/memory), [onTrimMemory guide](https://medium.com/@gurpreetsk/memory-management-on-android-using-ontrimmemory-f500d364bc1a)).
- Route all bounded caches (§2.2/§2.3) through a single "trim()" entry point so one callback shrinks them all.
- Do **not** put heavy logic in `onTrimMemory`; it should be fast cache-dropping only.

### 2.7 Avoid memory churn
- Memory churn (many short-lived allocations → frequent GC → jank) comes from allocating inside loops/`onDraw`/`onBindViewHolder`/per-tick timers. Reuse buffers, hoist `Paint`/`StringBuilder`/formatters out of hot paths, and avoid boxing in tight loops (parsing configs, ping-all iterating servers). The memory panel's 2 s tick must obey this too.

### 2.8 Measure, don't guess
- Use `dumpsys meminfo <pkg>` (per-process totals), Android Studio Memory Profiler, and Perfetto for allocation/PSS timelines during typical flows (idle, connected, import-large-subscription, ping-all). ([Perfetto memory case study](https://perfetto.dev/docs/case-studies/memory)).
- The panel itself doubles as a lightweight always-available field probe; for deep work use LeakCanary in debug builds to catch retained Activities/Contexts (the classic source of multi-MB leaks).
- Set an internal budget (e.g. UI process idle < ~60 MB PSS; connected UI+core within a stated ceiling) and watch for regressions.

### 2.9 iOS-style background memory limits (future iOS port context)
- On iOS a VPN's packet-tunnel runs as a **Network Extension** (`NEPacketTunnelProvider`) with a **hard, kernel-enforced memory cap** — historically ~5–6 MB, raised to **15 MB in iOS 10**, **50 MB in iOS 15**, and reportedly **back to ~15 MB on iOS 17**; exceeding it gets the extension jetsam-killed with no graceful path. ([Apple forums: NEPacketTunnelProvider memory limits](https://developer.apple.com/forums/thread/106377), [thread/73148](https://developer.apple.com/forums/thread/73148), [openradar 27660401](http://www.openradar.appspot.com/27660401)).
- **Design implication now:** the core's steady-state footprint (buffers, routing tables, connection state) should be engineered to fit a **~15 MB** envelope so the same core can be reused inside an iOS Network Extension without a rewrite. This is the strongest external reason to keep §2.1/§2.5 tight. Android's per-app limit is far more generous, but designing to the iOS ceiling keeps the product genuinely light on every platform and de-risks the future port.

---

## 3. Cross-platform note (future Windows / Linux desktop port)

The app will later target desktop. A few low-cost choices now ease that without over-engineering:

- **Keep core-config building platform-agnostic.** `AngConfigManager` produces Xray JSON from a model. Keep that pipeline free of Android types (`Context`, `Uri`, `SharedPreferences`, `android.util.*`) so the *same* config-building logic can be reused on desktop. Where Android APIs leak in today, that's the refactor target for portability. The Xray/v2ray-core binary itself is already cross-platform (it runs on Windows/Linux/macOS), so the reusable asset is the **config generation + subscription parsing**, not the UI.
- **Separate "shared logic" from "Android glue."** Aim for a conceptual (eventually a Kotlin Multiplatform `commonMain`) core: models, subscription/URL parsing, config assembly, validation — all pure Kotlin with no Android/JVM-Android dependencies. Persistence (MMKV), UI, VPN service, and the memory-sampling code (which is inherently OS-specific: `Debug.MemoryInfo` is Android-only) stay in the platform layer behind small interfaces.
- **Abstract memory sampling behind an interface.** Define a tiny `MemoryStatsProvider` contract ("give me MB used"); the Android impl uses `Debug.MemoryInfo`, a future desktop impl uses JVM `ManagementFactory`/`OperatingSystemMXBean` or `/proc` on Linux / Windows APIs. The panel UI talks to the interface, not to `Debug`.
- **Avoid Android-only assumptions in shared logic:** no `Context` threading through parsers, no `android.util.Base64`/`Log` in the core (use `kotlin`/`java.*` or an injected logger), no reliance on `assets://`/`content://` paths in portable code.
- **Don't over-engineer:** do **not** stand up KMP or a plugin architecture now. Just (a) keep new shared code Android-free, and (b) put the two new pieces from this doc — `MemoryStatsManager` and the config pipeline touchpoints — behind thin seams. That's enough to make a later extraction mechanical rather than a rewrite.

---

## 4. Implementation plan (real files)

### New file
- **`app/src/main/java/com/v2ray/ang/util/MemoryStatsManager.kt`** — small stateless util (object). Responsibilities:
  - `data class MemSnapshot(totalPssMb: Int, javaMb: Int, nativeMb: Int, codeMb: Int, graphicsMb: Int, otherMb: Int)`.
  - `fun sampleCurrentProcess(): MemSnapshot` — `Debug.MemoryInfo mi = new; Debug.getMemoryInfo(mi)`; headline `mi.totalPss/1024`; breakdown from `mi.getMemoryStat("summary.*")` on API ≥ 23, else fall back to `dalvikPss`/`nativePss`/`otherPss`.
  - `fun sampleProcessPss(am: ActivityManager, pid: Int): Int?` — wraps `am.getProcessMemoryInfo(intArrayOf(pid))` for the core process; returns null on failure/throttle.
  - `fun findCoreProcessPid(am: ActivityManager): Int?` — scan `am.runningAppProcesses` for `processName.endsWith(":RunSoLibV2RayDaemon")`.
  - `fun formatMb(int): String` and a reusable formatter to avoid per-tick allocation.
  - Pure of Android UI types; sits behind a future `MemoryStatsProvider` interface (§3).

### Modified files
- **`res/layout/activity_about.xml`** — add a `layout_memory` row (style-matched to existing rows) above the `tv_version` block, containing `tv_mem_usage` (headline) and `tv_mem_detail` (breakdown / optional core line).
- **`ui/AboutActivity.kt`** — in `onCreate` bind the new views; in `onStart`/`onResume` launch a `lifecycleScope` + `repeatOnLifecycle(STARTED)` loop that every 2 s calls `MemoryStatsManager.sampleCurrentProcess()` and updates `tv_mem_usage`/`tv_mem_detail`; a slower 10 s branch (only while VPN connected) adds the core-process line. Loop auto-cancels with lifecycle (no manual teardown needed).
- **`AngApplication.kt`** — implement `ComponentCallbacks2.onTrimMemory(level)` (§2.6): on `TRIM_MEMORY_UI_HIDDEN`/`_BACKGROUND`+ route to a central `trimCaches(level)` that shrinks the bounded caches (§2.2/§2.3). Small, fast, cache-dropping only.
- **(Optional) `res/xml/pref_settings.xml` + `ui/SettingsActivity.kt`** — if a Settings entry is also wanted, add a non-persistent, non-selectable `Preference` and refresh its `summary` from the fragment; otherwise leave Settings untouched and keep the panel About-only.
- **(Optional) `service/CoreVpnService.kt`** — reconsider a trimming (not connection-dropping) response to memory pressure in place of the commented-out `onLowMemory()` (lines 88–90).

### Rollout
1. Land `MemoryStatsManager` + About card (headline PSS only, UI process). Ship & measure.
2. Add breakdown line and (optional) core-process line at 10 s cadence.
3. Add `onTrimMemory` + central `trimCaches`, wiring existing caches in.
4. Audit `AngConfigManager` for retained large strings and Android-type leakage (feeds §2.1 and §3).

---

## 5. Sources

- [Debug.MemoryInfo — Android Developers reference](https://developer.android.com/reference/android/os/Debug.MemoryInfo)
- [Manage your app's memory — Android Developers](https://developer.android.com/topic/performance/memory)
- [Optimize app memory (Build for Billions) — Android Developers](https://developer.android.com/guide/topics/androidgo/optimize-memory)
- [Debugging memory usage on Android — Perfetto case study](https://perfetto.dev/docs/case-studies/memory)
- [Android Memory: the Ultimate Metric Guide — Greenspector](https://greenspector.com/en/android-memory-the-ultimate-metric-guide-2/)
- [Listening to memory events using onTrimMemory() — Gurpreet Singh](https://medium.com/@gurpreetsk/memory-management-on-android-using-ontrimmemory-f500d364bc1a)
- [NEPacketTunnelProvider memory limits — Apple Developer Forums](https://developer.apple.com/forums/thread/106377)
- [Memory limit for a network extension — Apple Developer Forums](https://developer.apple.com/forums/thread/73148)
- [openradar 27660401 — raise memory limit for Packet Tunnel Provider](http://www.openradar.appspot.com/27660401)
