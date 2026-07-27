# Recon — Android connection stack, end to end

Scope: `/home/user/dp` (fork of v2rayNG, applicationId `com.departamentvpn.app`, namespace
`com.v2ray.ang`). Everything below was read from source; every claim carries a `file:line`.

Files read in full or in the cited regions:

- `V2rayNG/app/src/main/java/com/v2ray/ang/core/` — `CoreServiceManager.kt`, `CoreConfigManager.kt`,
  `CoreConfigContextBuilder.kt`, `CoreNativeManager.kt`, `CoreOutboundBuilder.kt` (outline)
- `.../service/` — `CoreVpnService.kt`, `CoreProxyOnlyService.kt`, `CoreTestService.kt`,
  `RealPingWorkerService.kt`, `TProxyService.kt`, `QSTileService.kt`, `ProcessService.kt`,
  `DialerWebviewService.kt`, `IDialerService.kt`
- `.../handler/` — `MmkvManager.kt`, `AngConfigManager.kt`, `SettingsManager.kt`,
  `SubscriptionUpdater.kt`, `NotificationManager.kt`, `SpeedtestManager.kt`, `SettingsChangeManager.kt`
- `.../util/` — `MessageUtil.kt`, `HttpUtil.kt` (proxy path), `Utils.kt` (`isXray`, `receiverFlags`),
  `LogUtil.kt`
- `.../ui/` — `MainActivity.kt`, `MainRecyclerAdapter.kt`, `UrlSchemeActivity.kt`,
  `ScStart/ScStop/ScSwitchActivity.kt`
- `.../receiver/` — `BootReceiver.kt`, `TaskerReceiver.kt`, `WidgetProvider.kt`
- `.../viewmodel/MainViewModel.kt`, `.../auth/SubscriptionSyncManager.kt`,
  `.../template/TemplateManager.kt` (outline), `AngApplication.kt`, `AppConfig.kt`
- `app/src/main/AndroidManifest.xml`, `app/src/main/assets/v2ray_config*.json`,
  `res/values/strings.xml`, `res/values-ru/strings.xml`

> **Note on freshness.** `MainActivity.kt` and `MainRecyclerAdapter.kt` were modified *during* this
> recon (mtime 12:38 vs. 12:20 for the rest; `git status` shows both dirty against `bfd05fd`). All
> line numbers below reflect the **current working tree**. The section
> [“Switching servers while connected”](#5-switching-servers-while-connected) explicitly separates
> what the in-flight change already fixed from what is still broken underneath it.

---

## 1. Process topology — read this first, everything else depends on it

`AndroidManifest.xml` splits the app across **three OS processes**:

| Process | Components | Manifest |
|---|---|---|
| main (`com.departamentvpn.app`) | every `ui.*` Activity incl. `MainActivity`, `UrlSchemeActivity`; `receiver.BootReceiver` | default (no `android:process`) |
| `:RunSoLibV2RayDaemon` | `CoreVpnService`, `CoreProxyOnlyService`, `CoreTestService`, `QSTileService`, `WidgetProvider`, `TaskerReceiver`, `ScStart/ScStop/ScSwitchActivity` | `AndroidManifest.xml:147,153,159,234,252,262,271,297,318` |
| `:bg` | `androidx.work.multiprocess.RemoteWorkManagerService` → the subscription auto-update worker | `AndroidManifest.xml:338-341`, `AngApplication.kt:25-27` |

Consequences that drive most of the bugs in this report:

1. **`CoreServiceManager` is a Kotlin `object` → one instance *per process*.**
   `core/CoreServiceManager.kt:44` creates a `CoreController` at class-init time in *whichever*
   process first touches the object. In the main process that controller is never started, so
   `CoreServiceManager.isRunning()` (`:114`) **always returns `false` in the main process**.
2. `AngApplication.onCreate` (`AngApplication.kt:32-47`) runs in all three processes, so
   `MMKV.initialize`, `WorkManager.initialize` and `SettingsManager.initApp` (migrations!) run three
   times, concurrently.
3. All MMKV instances are opened `MULTI_PROCESS_MODE` (`MmkvManager.kt:35-41`), which is correct —
   but any **in-memory** state in a `object` (e.g. `SettingsManager.runtimeSocksPort`
   `SettingsManager.kt:41-42`, `SettingsChangeManager` flags, `CoreConfigManager.initConfigCache`)
   is *not* shared and silently diverges per process.
4. The only cross-process channel is `Intent` broadcasts via `MessageUtil`
   (`util/MessageUtil.kt:80-91`), package-scoped (`intent.package = ANG_PACKAGE`) but registered
   `RECEIVER_EXPORTED` on API 33+ (`Utils.kt:552-556`).

---

## 2. "User taps connect" → "tunnel is up", step by step

### 2.1 Main process — UI

1. `MainActivity.onCreate` wires the hero: `binding.cardConnect.setOnClickListener { animateConnectPress(); handleFabAction() }` (`MainActivity.kt:272-275`).
2. `handleFabAction()` (`MainActivity.kt:1483-1501`)
   - resets `mainViewModel.autoFallbackUsed = false` (`:1485`);
   - if `mainViewModel.isRunning.value == true` → **stop** branch: `connectInProgress = false`,
     `cancelConnectWatchdog()`, `CoreServiceManager.stopVService(this)` (`:1487-1492`);
   - else **start** branch: `connectInProgress = true`, gray toast «Подключение…»
     (`R.string.toast_status_connecting`, `values-ru/strings.xml:50`), `applyRunningState(isLoading = true, …)`,
     `scheduleConnectWatchdog()` (20 s, `:200`, `:1950-1952`), `startVpnWithPermission()` (`:1493-1500`).
3. `startVpnWithPermission()` (`:1506-1517`) — in VPN mode (`SettingsManager.isVpnMode()`,
   `SettingsManager.kt:575-578`, default `VPN` via `ensureDefaultSettings` `:607`) calls
   `VpnService.prepare(this)`; a non-null intent goes through `requestVpnPermission`
   (`MainActivity.kt:214-218`) and only `RESULT_OK` re-enters `startV2Ray()`. In proxy-only mode it
   calls `startV2Ray()` directly.
4. `startV2Ray()` (`:1519-1525`) — guard: `if (MmkvManager.getSelectServer().isNullOrEmpty()) { toast(R.string.title_file_chooser); return }`
   («Выберите профиль», `values-ru/strings.xml:131`). Then `CoreServiceManager.startVService(this)`.

### 2.2 Main process — `CoreServiceManager.startVService` / `startContextService`

`core/CoreServiceManager.kt:86-99` → `startContextService(context)` (`:130-195`):

1. `if (coreController.isRunning) { LogUtil.w("Core already running"); return }` (`:132-135`).
   **In the main process this is always false** (§1.1) — so this early-out never protects a
   MainActivity-initiated start, but it *does* fire for daemon-initiated starts (QS tile, widget,
   `MSG_STATE_RESTART`).
2. `val guid = MmkvManager.getSelectServer() ?: error(getString(R.string.app_tile_first_use))` (`:137-141`).
3. `val config = MmkvManager.decodeServerConfig(guid) ?: error(getString(R.string.toast_config_file_invalid))` (`:143-147`).
4. Sanity check on the address: `if (!config.configType.isComplexType() && !Utils.isValidUrl(config.server) && !Utils.isPureIpAddress(config.server.orEmpty())) error(getString(R.string.toast_config_file_invalid))` (`:149-155`).
5. `SettingsManager.refreshRuntimeSocksPort()` (`:158`) — only meaningful when
   `PREF_DYNAMIC_SOCKS_PORT` is on, and it mutates **this process's** `runtimeSocksPort` (see F-6).
6. Proxy-sharing warning toast if `PREF_PROXY_SHARING` (`:163-170`); the old «Запуск служб» toast is
   deliberately suppressed (`:166-169`).
7. Picks the service class from `SettingsManager.isVpnMode()`: `CoreVpnService` or
   `CoreProxyOnlyService` (`:172-179`), then
   `ContextCompat.startForegroundService(context, intent)` (`:182`) with explicit handling for
   `SecurityException` and `ForegroundServiceStartNotAllowedException` → rethrown as
   `IllegalStateException` (`:183-194`), caught by `startVService` (`:95-98`) which toasts
   `e.message`.

### 2.3 Daemon process — `CoreVpnService`

`service/CoreVpnService.kt`:

1. `onCreate` (`:76-82`): `StrictMode` thread policy permit-all, then
   `CoreServiceManager.serviceControl = SoftReference(this)`. The setter
   (`CoreServiceManager.kt:50-59`) also runs `CoreNativeManager.initCoreEnv(service)`
   (`CoreNativeManager.kt:28-44` → `Libv2ray.initCoreEnv(assetPath, deviceId)`) and, on API 29+,
   registers the `XrayProcessFinder` (`CoreServiceManager.kt:55-58`, impl `:453-484`).
2. `onStartCommand` (`:115-138`), in order:
   - `NotificationManager.showNotification(null)` **first**, because the FGS must call
     `startForeground` within ~5 s (`NotificationManager.kt:74-117`; hardened so it can never throw,
     falls back to `buildFallbackNotification` `:179-188`). Returning `false` →
     `reportStartFailure("Foreground service could not start")` + `stopAllService()` +
     `START_NOT_STICKY` (`:120-125`).
   - `setupVpnService()` (`:186-201`): `prepare(this)` re-check → `stopSelf()` if permission missing;
     `configureVpnService()` → `stopSelf()` if it returns non-true; then `runTun2socks()`.
   - `startService()` (`:155-165`) → `CoreServiceManager.startCoreLoop(mInterface)`.
   - Any throw is converted to `MSG_STATE_START_FAILURE` + `stopAllService()` (`:128-135`) —
     explicitly so the daemon process can't die and strand the UI on «Подключение…».
   - returns `START_STICKY` (`:136`).
3. `configureVpnService()` (`:207-238`) builds the tun:
   - `configureNetworkSettings` (`:246-286`): MTU from `SettingsManager.getVpnMtu()`,
     `addAddress(vpnConfig.ipv4Client, 30)`, routes = `AppConfig.ROUTED_IP_LIST` when bypass-LAN is
     on else `0.0.0.0/0`, optional IPv6 (`PREF_IPV6_ENABLED`), DNS from
     `SettingsManager.getVpnDnsServers()` (`SettingsManager.kt:465-476`, guarded against an empty list).
   - `configurePerAppProxy` (`:322-355`): with per-app proxy **off** it calls
     `addDisallowedApplication(BuildConfig.APPLICATION_ID)` — this is the *only* thing keeping the
     core's own outbound sockets out of the tunnel (`vpnProtect`/`protect()` exists at `:171-173`
     but **is never called from Kotlin**; grep shows no caller).
   - closes the previous `mInterface` (`:217-223`), `configurePlatformFeatures` (`:293-310`:
     `requestNetwork` + `setUnderlyingNetworks` on P+, `setMetered(false)` and optional
     `setHttpProxy(127.0.0.1:httpPort)` on Q+), then `builder.establish()` → `mInterface`,
     `isRunning = true` (`:229-232`).
4. `runTun2socks()` (`:361-374`): when `SettingsManager.isUsingHevTun()` (**default `true`**,
   `SettingsManager.kt:567-569`) it constructs `TProxyService` and calls `startTun2Socks()`
   (`TProxyService.kt:42-58`) → writes `filesDir/hev-socks5-tunnel.yaml` (`:60-93`) and calls the
   JNI `TProxyStartService(configPath, vpnInterface.fd)`. The yaml points hev at
   `127.0.0.1:<socksPort>` with `udp: 'udp'`.
   **Ordering note:** hev starts *before* the core, so for the window between `establish()` and
   `startLoop()` the tun is up and every packet is forwarded to a SOCKS port nobody is listening on.

### 2.4 Daemon process — `CoreServiceManager.startCoreLoop` / `doStartCoreLoop`

`CoreServiceManager.kt:202-286`:

1. `startCoreLoop` (`:202-224`) — `if (coreController.isRunning) return false` (`:203-206`,
   **silent: no `MSG_STATE_START_FAILURE`**), `getService()` null-check, then `doStartCoreLoop` in a
   try/catch that converts any exception into `MSG_STATE_START_FAILURE` + `cancelNotification()`
   (`:217-223`).
2. `doStartCoreLoop` (`:226-286`):
   - re-reads `MmkvManager.getSelectServer()` and `decodeServerConfig(guid)` — errors
     `"No server selected"` / `"Failed to decode server config"` (`:228-229`, raw English strings,
     not resources);
   - `CoreConfigManager.getV2rayConfig(service, guid)` (`:232`), `if (!result.status) error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })` (`:234-236`);
   - registers `mMsgReceive` for `BROADCAST_ACTION_SERVICE` + `SCREEN_ON/OFF/USER_PRESENT` (`:238-242`);
   - `currentConfig = config` (`:244`); `tunFd = vpnInterface?.fd ?: 0`, **forced to 0 when hev-tun is
     used** (`:245-253`);
   - optional browser-dialer address (`:246-250`), `NotificationManager.showNotification(currentConfig)` (`:255`),
     `CoreNativeManager.reconcileBrowserDialer(dialerAddr)` (`:256`);
   - `coreController.startLoop(result.content, tunFd)` (`:265`) — the actual native start;
   - `if (!coreController.isRunning) error("Core failed to start")` (`:267-269`);
   - starts the OkHttp/WebView browser dialer if the profile asks for one (`:271-281`);
   - `MessageUtil.sendMsg2UI(service, MSG_STATE_START_SUCCESS, "")` (`:283`) and
     `NotificationManager.startSpeedNotification()` (`:284`).
   - Dead weight: `:258-264` documents that the `PREF_MEMORY_LIMIT*` setting **cannot be enforced**
     because libv2ray exposes no setter — the Settings toggle is inert.

### 2.5 Config build (`CoreConfigManager.getV2rayConfig`)

`core/CoreConfigManager.kt:35-51` →

- `CoreConfigContextBuilder.build(context, guid)` (`CoreConfigContextBuilder.kt:30-52`): loads the
  profile; `CUSTOM` short-circuits with `isCustom = true` (`:34-36`); otherwise resolves the primary
  outbound as `TAG_PROXY` (`:39-42`) plus every non-builtin routing outbound tag (`:96-137`).
  POLICYGROUP → member list (`:139-173`), PROXYCHAIN → chain list (`:175-193`), plain profiles may
  still become a chain via the subscription's `prevProfile`/`nextProfile` (`:200-216`).
- **CUSTOM path** `buildV2rayCustomConfig` (`CoreConfigManager.kt:82-168`):
  `TemplateManager.decodeRuntimeRaw(guid)` (transparent decrypt of locked templates,
  `TemplateManager.kt:149`) with a fallback to `MmkvManager.decodeServerRaw` on any throw (`:90-95`);
  hard-fails with `"Custom config is empty"`, `"Custom config is not a JSON object"`,
  `"Custom config has no outbounds"` (`:95,100-104,113-119`); strips non-Xray root keys
  (`sanitizeXrayRootKeys`, `:257-278` — this is what makes Remnawave `XRAY_JSON` payloads safe);
  rewrites `routing.rules[].process` package names to UIDs when
  `SettingsManager.canUseProcessRouting()` (`:128-143`); injects the `tun` inbound when
  `needTun()` and the raw config lacks one (`:145-165`).
- **Unified path** `buildUnifiedConfig` (`:286-347`): loads the asset template
  (`initV2rayConfig` `:577-595` → `assets/v2ray_config_with_tun.json` when `needTun()`, else
  `assets/v2ray_config.json`; cached per-process in `initConfigCache*` `:27-28`), sets log level and
  `remarks`, `configureInbounds` (`:610-702`), converts every resolved outbound
  (`buildOutbounds` `:353-390` → `handleNormal/ProxyChain/PolicyGroup…`), then routing
  (`configureRouting` `:1057`), fake-DNS, DNS, local DNS, balancer catch-all rule (`:317-340`),
  observability, `applySpeedDisabled`, `resolveOutboundDomainsToHosts` (`:342-344`).
- `needTun()` = `isVpnMode() && !isUsingHevTun()` (`:603-605`). With the default hev-tun on, the
  **core never gets the tun fd**; the plain `v2ray_config.json` template is used and everything rides
  the loopback SOCKS inbound.
- `configureInbounds` (`:610-702`): inbound[0] is pinned to `127.0.0.1` + `auth = "noauth"`
  unconditionally (`:632-639`, with a long comment explaining that changing it breaks hev);
  a second HTTP inbound is added when `!Utils.isXray()` (`:653-664`); an authenticated
  `socks-lan` inbound on `0.0.0.0:<sharePort>` is added only when `PREF_PROXY_SHARING` **and** the
  local proxy are on (`:666-691`).

### 2.6 State propagation back to the UI

`MainViewModel.startListenBroadcast()` (`MainViewModel.kt:89-94`) registers `mMsgReceiver`
(`:665-729`) for `BROADCAST_ACTION_ACTIVITY` and sends `MSG_REGISTER_CLIENT`, which the daemon
answers with `MSG_STATE_RUNNING` / `MSG_STATE_NOT_RUNNING`
(`CoreServiceManager.kt:500-506`). `MSG_STATE_START_SUCCESS`/`STOP_SUCCESS`/`START_FAILURE` flip
`isRunning` (`MainViewModel.kt:668-692`). `MainActivity`'s observer (`MainActivity.kt:550-579`)
cancels the watchdog, paints the state, arms/cancels the health check and emits exactly one status
toast per genuine transition. The same broadcast also drives `QSTileService`
(`QSTileService.kt:89-115`) and `WidgetProvider` (`WidgetProvider.kt:75-93`).

---

## 3. Where the active/selected server lives

**Single source of truth: `MMKV(ID_MAIN)["SELECTED_SERVER"]`** — one GUID string.

| Concern | Location |
|---|---|
| Key name | `MmkvManager.kt:29` (`KEY_SELECTED_SERVER = "SELECTED_SERVER"`), store `mainStorage` = `MMKV.mmkvWithID("MAIN", MULTI_PROCESS_MODE)` (`:35`) |
| Read | `MmkvManager.getSelectServer()` `:63-65` |
| Write | `MmkvManager.setSelectServer(guid)` `:72-74` |
| Profile bodies | `MMKV(ID_PROFILE_FULL_CONFIG)[guid]` → JSON `ProfileItem`; `decodeServerConfig` `:139-148`, `encodeServerConfig` `:158-175` |
| Raw xray-json (CUSTOM) | `MMKV(ID_SERVER_RAW)[guid]` — `encodeServerRaw`/`decodeServerRaw` `:329-341` |
| Ping results | `MMKV(ID_SERVER_AFF)[guid]` → `ServerAffiliationInfo.testDelayMillis` `:242-280` |
| Ordering / grouping | `mainStorage["SUB_SERVERS_<subId>"]` per subscription (`:83-107`), `decodeAllServerList` `:115-130` |

Everyone who writes it:

- `MmkvManager.encodeServerConfig` auto-selects the first-ever server (`:169-171`).
- `MmkvManager.removeServer` clears it if it was the removed one (`:207-209`);
  `removeServerViaSubid` does the same for every GUID in a subscription (`:225-227`);
  `removeAllServer` nukes it via `mainStorage.clearAll()` (`:287-293`).
- `CoreServiceManager.startVService(context, guid)` writes it when a GUID is passed (`:89-91`) —
  used by Tasker (`TaskerReceiver.kt:33`).
- `MainActivity.setSelectServer` on a row tap (`MainActivity.kt:1412-1435`).
- `MainViewModel.selectFastestServer` after a fast-connect test (`MainViewModel.kt:393-406`, write at `:404`).
- `AngConfigManager.resolveSelectedKey` after every subscription import/refresh
  (`AngConfigManager.kt:503-528`, written at `:384` and `:601`).

The **adapter mirror** was just added: `MainRecyclerAdapter.selectedGuid` (`:142`) is re-read from
MMKV on every rebuild (`:81-83`) and repainted through `syncSelection()` (`:324-338`), with
`MainActivity.onResume` calling `syncSelection()` on both adapters (`MainActivity.kt:1978-1979`).
This is what stops "two rows painted as selected" after an out-of-band selection change.

---

## 4. Where the tunnel can be started, stopped or restarted

**Start**
| Entry point | Code | Process |
|---|---|---|
| Connect hero | `MainActivity.kt:1483-1525` | main |
| Boot | `BootReceiver.kt:29-41` (gated on `decodeStartOnBoot()` + a non-empty selection) | main |
| QS tile | `QSTileService.kt:74-85` → `startVServiceFromToggle` | daemon |
| Home-screen widget | `WidgetProvider.kt:67-74` | daemon |
| Launcher shortcuts | `ScStartActivity.kt:14-16`, `ScSwitchActivity.kt:14-18` | daemon |
| Tasker | `TaskerReceiver.kt:29-34` (can pass an explicit GUID) | daemon |
| `depv://connect|open|toggle` | `UrlSchemeActivity.kt:88-98` | main |

**Stop** — every path funnels into `CoreServiceManager.stopVService` (`:105-108`), which only
broadcasts `MSG_STATE_STOP`; the daemon's `ReceiveMessageHandler` (`:516-519`) calls
`serviceControl.stopService()` → `CoreVpnService.stopAllService(true)` (`:376-421`). The ongoing
notification's "Stop" action posts the same broadcast (`NotificationManager.kt:129-132`).

**Restart** — five distinct mechanisms:

1. `MainActivity.restartV2Ray()` (`:1538-1557`) — stop, poll `isRunning` for up to
   `RESTART_STOP_TIMEOUT_MS = 6000` ms in 50 ms steps (`:203-204`), then `startV2Ray()`.
   Callers: the "apply selected server" snackbar (`:1437-1455`), `fastConnectAction`
   (`:531-549`), `requestActivityLauncher` when `SettingsChangeManager.consumeRestartService()`
   (`:219-230`), and `restartIfRunning()` (`:2480-2482`) called from nine Settings toggles
   (`:2520` mode, `:2566` bypass-LAN, `:2573` IPv6, `:2624`/`:2646` DNS, `:2659` mux,
   `:2675` mux concurrency, `:2685` fragment).
2. `MSG_STATE_RESTART` broadcast → `CoreServiceManager.ReceiveMessageHandler` (`:521-526`):
   `stopService(); Thread.sleep(500L); startVService(serviceControl.getService())`.
   Senders: the notification "restart" action (`NotificationManager.kt:134-137`) and
   `UrlSchemeActivity.importRoutingRules` (`UrlSchemeActivity.kt:159-161`).
3. `SettingsChangeManager.makeRestartService()` (in-memory flag) from `ServerActivity.kt:531`,
   `ServerGroupActivity.kt:105`, `ServerProxyChainActivity.kt:131`, `ServerCustomConfigActivity.kt:106`,
   `PerAppProxyActivity.kt:241`, `PerAppProxyViewModel.kt:62`, `MmkvPreferenceDataStore.kt:86`,
   `BackupActivity.kt:127` — consumed **only** in `requestActivityLauncher` (`MainActivity.kt:224`).
4. Auto-fallback: `delayResultAction` observer (`MainActivity.kt:580-591`) →
   `mainViewModel.fastConnect(excludeGuid = …)` → real-ping-all → `selectFastestServer` →
   `fastConnectAction` → `restartV2Ray()`.
5. Android itself: `CoreVpnService.onStartCommand` returns `START_STICKY` (`:136`), so the system
   re-delivers a **null** intent after a process kill and the service reconnects using whatever
   `getSelectServer()` says *at that moment*.

---

## 5. Switching servers while connected

### 5.1 Current behaviour (after the in-flight change)

`MainActivity.setSelectServer` (`:1412-1435`) now **only selects**: it writes MMKV, repaints both
adapters, moves the meta carousel, and — if a tunnel is up — shows a `Snackbar`
(`promptApplySelectedServer` `:1437-1455`) offering «Переподключиться»
(`values-ru/strings.xml` `server_selected_reconnect_prompt` / `…_action`, added in the same change).
Only the snackbar action calls `restartV2Ray()`. So a tap in the list can no longer tear down a
working tunnel, and the previous "UI shows server B, traffic goes through A" window is closed for
this specific path.

`restartV2Ray` (`:1538-1557`) no longer uses a blind `delay(500)`; it waits for `isRunning == false`
and, on timeout, reports «Не удалось подключиться» and repaints the *still connected* state
(`:1549-1554`).

### 5.2 What is still broken underneath

The daemon side was **not** changed, and it defeats part of the fix:

- `CoreServiceManager.stopCoreLoop` (`:293-323`) launches `coreController.stopLoop()` on a
  **fire-and-forget IO coroutine** (`:297-303`) and then broadcasts `MSG_STATE_STOP_SUCCESS`
  **immediately** (`:313`) — i.e. before the native core has actually stopped. So
  `isRunning == false` in the UI is *not* evidence that the core released its ports; the new
  polling loop can still exit early.
- If the new start then reaches the daemon while the old core is still up,
  `startCoreLoop` hits `if (coreController.isRunning) return false` (`:203-206`) — **without**
  broadcasting `MSG_STATE_START_FAILURE`. `CoreVpnService.startService` reacts by calling
  `stopAllService()` (`:160-164`), which broadcasts another `MSG_STATE_STOP_SUCCESS`.
  Net effect: the user asked to switch servers and ends up **disconnected**; the UI shows
  «Не удалось подключиться» (because `connectInProgress` is set at `:1448`) but the tunnel is gone.
- The `MSG_STATE_RESTART` path (`:521-526`) still uses `Thread.sleep(500L)` **on the daemon's main
  thread inside a `BroadcastReceiver.onReceive`**, after `stopCoreLoop` has already unregistered
  that very receiver (`:317`). It then calls `startVService(service)` on a service that has already
  called `stopSelf()`; on Android 12+ that `startForegroundService` can throw
  `ForegroundServiceStartNotAllowedException` (converted to `IllegalStateException` at
  `CoreServiceManager.kt:186-192`, swallowed into a toast at `:95-98`). The notification's
  **"Перезапуск службы" button therefore frequently just stops the VPN**.

---

## 6. Where a stale config can be used

1. **`startContextService` early-return** (`:132-135`). Any daemon-process start attempt while the
   core is up silently no-ops, leaving the *previous* server's config running. Reachable from the QS
   tile, the widget, launcher shortcuts, Tasker, and `MSG_STATE_RESTART`.
2. **`startCoreLoop` early-return** (`:203-206`). Same shape, and it returns `false` without any
   failure broadcast, so the UI cannot distinguish "already running" from "failed".
3. **Subscription refresh mints new GUIDs under a live tunnel.**
   `AngConfigManager.parseBatchConfig` with `append = false` calls
   `MmkvManager.removeServerViaSubid(subid)` (`:380`) — deleting every profile of that subscription,
   including the one the core is currently running — then `batchSaveConfigs` creates **fresh
   `Utils.getUuid()` keys** (`:409`) and `resolveSelectedKey` re-points the selection (`:383-384`).
   The running core is never restarted. From that moment `MmkvManager.decodeServerConfig(oldGuid)`
   returns `null`, and `MainActivity.applyConnectedState` renders
   `selectedServerName()` (`:1615`, `:1885-1889`) — i.e. **the newly selected server's name while
   traffic still flows through the deleted one**. If the old selection cannot be matched,
   `findMatchedProfileKey` falls back to `keyToProfile.keys.lastOrNull()` (`:475-481`), i.e. it
   silently *changes which server the user is on*. This runs unattended from the `:bg`
   WorkManager worker (`SubscriptionUpdater.kt:150-192`) and from `SubscriptionSyncManager.importAll`
   (`SubscriptionSyncManager.kt:56`).
4. **`START_STICKY` re-delivery** (`CoreVpnService.kt:136`): after a process kill the service
   restarts and `doStartCoreLoop` re-reads `getSelectServer()` (`:228`) — which may now be a
   different server than the one that was running (see 6.3).
5. **`CoreServiceManager.currentConfig` is never cleared** (`:46`, written only at `:244`). After a
   stop or a failed start it still holds the last profile, and `getRunningServerName()` (`:120`) —
   used as the QS tile label (`QSTileService.kt:32`) — reports it.
6. **Logout while connected**: `SubscriptionSyncManager.removeAllManaged` (`:79-87`) →
   `MmkvManager.removeSubscription` → `removeServerViaSubid` clears `SELECTED_SERVER`
   (`MmkvManager.kt:225-227`) but never stops the tunnel. The hero then shows «Выберите сервер»
   (`MainActivity.kt:1888`) on top of a live connection.
7. **Per-process config template cache** `initConfigCache` / `initConfigCacheWithTun`
   (`CoreConfigManager.kt:27-28`, `:577-595`) — only assets, so benign, but never invalidated.

---

## 7. Failure paths and exact error messages

### 7.1 «Неправильный профиль» — the string you asked about

- Resource: `toast_config_file_invalid`.
  - RU: `res/values-ru/strings.xml:134` → **«Неправильный профиль»**
  - EN: `res/values/strings.xml:140` → `Invalid config`
  - also ar `:103`, bn `:102`, bqi `:102`, fa `:102`, vi `:102`, zh-rCN `:102`, zh-rTW `:102`
- **It is produced from exactly two places, both in `core/CoreServiceManager.kt:startContextService`:**
  - `:143-147` — `MmkvManager.decodeServerConfig(guid)` returned `null`
    (selected GUID has no profile JSON: deleted profile, wiped storage, or a GUID minted away by a
    subscription refresh);
  - `:149-155` — the profile's `server` is neither a valid URL nor a pure IP **and** the type is not
    a complex type (`isComplexType()` covers CUSTOM/POLICYGROUP/PROXYCHAIN).
- Delivery: `error(...)` throws `IllegalStateException`; `startVService`'s catch
  (`:95-98`) does `context.toast(e.message ?: e.javaClass.simpleName)` → a plain Toasty toast
  (`extension/_Ext.kt:34-41`). Note this runs in **whichever process called it** — for the QS tile /
  widget / shortcuts that is the daemon, so the toast appears with no visible Activity.
- Sibling message from the same function: `app_tile_first_use` (`:137-141` and `:67-68`) —
  RU `res/values-ru/strings.xml:31` «Первое использование этой функции, пожалуйста, используйте
  приложение, чтобы добавить профиль»; EN `res/values/strings.xml:37`.

### 7.2 Complete failure table

| Failure | Detected at | Surfaced as |
|---|---|---|
| No server selected (UI start) | `MainActivity.kt:1520` | toast `title_file_chooser` «Выберите профиль» |
| No server selected (toggle/tile/widget) | `CoreServiceManager.kt:67-69` | toast `app_tile_first_use`, returns `false` |
| No server selected (deep start) | `CoreServiceManager.kt:137-141` | toast `app_tile_first_use` |
| Profile missing / bad address | `CoreServiceManager.kt:143-155` | toast **`toast_config_file_invalid` = «Неправильный профиль»** |
| FGS not allowed / SecurityException | `CoreServiceManager.kt:183-194` | toast of `e.message` |
| `startForeground` refused | `CoreVpnService.kt:120-125` → `NotificationManager.kt:107-116` | `MSG_STATE_START_FAILURE("Foreground service could not start")` |
| VPN permission revoked between prepare and start | `CoreVpnService.kt:186-192` | **silent** `stopSelf()`, no broadcast → the UI only recovers via the 20 s watchdog |
| `builder.establish()` throws | `CoreVpnService.kt:229-237` | `stopAllService()`, no failure broadcast |
| Config build failed (custom empty / not JSON / no outbounds / template) | `CoreConfigManager.kt:95,100-104,113-119,38,48` | `ConfigResult.errorMessage` → `error()` in `doStartCoreLoop:234-236` → `MSG_STATE_START_FAILURE` with the raw **English** text |
| Core already running | `CoreServiceManager.kt:203-206` | **silent `false`** → `stopAllService()` → looks like a disconnect |
| `startLoop` ran but core not up | `CoreServiceManager.kt:267-269` | `MSG_STATE_START_FAILURE("Core failed to start")` |
| Any `MSG_STATE_START_FAILURE` | `MainViewModel.kt:683-688` | `isRunning = false`; `MainActivity.kt:566` toast `toast_status_failed` «Не удалось подключиться» |
| Nothing at all within 20 s | `MainActivity.kt:179-190`, `:1950-1952` | idle state + «Не удалось подключиться» |
| Restart stop never confirmed (6 s) | `MainActivity.kt:1549-1554` | «Не удалось подключиться», stays painted connected |
| Post-connect health check failed | `MainActivity.kt:580-591` | toast `auto_fallback_switching` (**English only — no `values-ru` entry**, `values/strings.xml:333`) then fast-connect |
| Fast-connect found no server | `MainActivity.kt:534-537` | «Не удалось подключиться» |
| Delay measurement | `CoreServiceManager.kt:384-388` | `connection_test_available` / `connection_test_error` («Сбой проверки интернет-соединения: %s», `values-ru:405`) |

The failure text emitted by `doStartCoreLoop` is never localized — `"No server selected"`,
`"Failed to decode server config"` (`:228-229`), `"Core failed to start"` (`:268`),
`"Failed to get V2Ray config: …"` (`CoreConfigManager.kt:48`) — but it is also never displayed:
`MainViewModel` drops the payload (`:683-688`) and MainActivity shows its own toast. So the real
cause is only visible in logcat (`LogUtil`, default min level `warning`, `LogUtil.kt:10,16-26`).

---

## 8. The second core: ping / speedtest path

Separate from the tunnel, using `Libv2ray.measureOutboundDelay` rather than `startLoop`:

- `MainViewModel.testAllServers()` (`:316-323`) dispatches on `SettingsManager.getPingMethod()`
  (default `PROXIED_REAL_DELAY`, `SettingsManager.kt:614`):
  - `TCP_CONNECT` → `testAllTcping` (`:262-278`) → `SpeedtestManager.tcping` (`:104-116`)
  - `HTTP_URL` → `testAllDirectHttp` (`:328-350`), semaphore 24
  - `ICMP` → `testAllIcmp` (`:355-374`) → `/system/bin/ping` (`SpeedtestManager.kt:83-102`)
  - `PROXIED_REAL_DELAY` → `MSG_MEASURE_CONFIG_START` to `CoreTestService`
- `CoreTestService` (daemon, `:61-107`) spins a `RealPingWorkerService` (`:79-96`) that, per GUID,
  builds a stripped config via `CoreConfigManager.getV2rayConfig4Speedtest`
  (`CoreConfigManager.kt:58-77`, `postProcessForSpeedtest` `:543-561`, and the CUSTOM-specific
  `buildV2rayCustomConfig4Speedtest` `:189-238` which promotes the first real proxy outbound and
  drops balancer/observatory/routing/dns) and calls `CoreNativeManager.measureOutboundDelay`.
- The *connected* tunnel's own delay test is `MSG_MEASURE_DELAY` → `measureV2rayDelay`
  (`CoreServiceManager.kt:359-400`), which also emits `MSG_STATE_DELAY_RESULT` (`:391`) — the numeric
  signal the auto-fallback health check consumes.
- `resolvePingHostPort` (`MainViewModel.kt:230-257`) returns `null` for group types so balancer rows
  stay untested rather than showing `-1 ms`.

---

## 9. Findings, ranked

**F-1 (high) — a server switch can silently disconnect instead of switching.**
`stopCoreLoop` broadcasts `MSG_STATE_STOP_SUCCESS` (`CoreServiceManager.kt:313`) *before*
`coreController.stopLoop()` finishes (`:297-303`), so the UI's "stopped" signal is a lie. When the
new start lands too early, `startCoreLoop` returns `false` silently (`:203-206`) and
`CoreVpnService.startService` tears everything down (`:160-164`). Fix: make `stopCoreLoop` suspend
until the native loop is down (or broadcast `STOP_SUCCESS` from the coroutine's completion), and make
`startCoreLoop`'s "already running" branch send `MSG_STATE_START_FAILURE`.

**F-2 (high) — the notification "Перезапуск службы" button mostly just stops the VPN.**
`CoreServiceManager.kt:521-526`: `Thread.sleep(500)` on the daemon main thread, inside a receiver
that `stopCoreLoop` has already unregistered (`:317`), then a foreground-service start from a
stopped service. Same 500 ms race as F-1 plus an FGS-start-from-background hazard.

**F-3 (high) — subscription refresh re-points the active server under a live tunnel.**
`AngConfigManager.kt:380-385` + `:475-481`. The running config is deleted from storage, new GUIDs are
minted, and `findMatchedProfileKey` falls back to "the first server of the subscription" when
matching fails. The tunnel is not restarted, so the displayed server name
(`MainActivity.kt:1615`) stops corresponding to the traffic path. Fix: on a refresh that replaces the
currently-running GUID, either keep the tunnel and pin the UI to the *running* profile, or prompt to
re-apply (same UX as the new snackbar).

**F-4 (high) — `depv://import` and `depv://add` wipe the default server group.**
`UrlSchemeActivity.kt:142` / `:186` call `AngConfigManager.importBatchConfig(content, "", false)` —
`append = false` with a blank subid. `parseBatchConfig` (`:380`) →
`MmkvManager.removeServerViaSubid("")` → `getSubscriptionId("")` resolves to
`DEFAULT_SUBSCRIPTION_ID` (`MmkvManager.kt:347-349`), deleting **every ungrouped server** and
clearing the selection (`:225-227`). MainActivity's own importer correctly uses `append = true`
(`MainActivity.kt:2138`). Any link tapped in a browser or messenger can therefore erase manually
added servers.

**F-5 (medium) — `CoreServiceManager.isRunning()` is meaningless in the main process.**
`UrlSchemeActivity.kt:93` (`depv://toggle`) and `:159` (routing "apply") consult the *main*
process's never-started `CoreController` (`CoreServiceManager.kt:44,114`), so toggle always takes the
"start" branch and the routing restart never fires. `ScStart/ScStop/ScSwitchActivity`, `QSTileService`
and `WidgetProvider` are correct only because the manifest pins them to `:RunSoLibV2RayDaemon`
(`AndroidManifest.xml:147,153,159,271,297`). The UI's `MainViewModel.isRunning` is the only reliable
signal in the main process.

**F-6 (medium) — dynamic SOCKS port desyncs across processes.**
`SettingsManager.runtimeSocksPort` is a per-process `@Volatile` field (`:41-42`) refreshed only from
`startContextService` (`CoreServiceManager.kt:158`). With `PREF_DYNAMIC_SOCKS_PORT` on, the `:bg`
worker's `SettingsManager.getHttpPort()` (`AngConfigManager.kt:766`) and the daemon's
`SpeedtestManager.getRemoteIPInfo` (`:167`) each generate their **own** random port
(`SettingsManager.kt:297-314`, `:402-404`), so the proxied subscription fetch and IP lookup point at
a dead port and silently fall back to a direct (unproxied) request (`AngConfigManager.kt:782-795`).

**F-7 (medium) — any installed app can stop or restart the VPN.**
Both service-side and UI-side receivers are registered with `Utils.receiverFlags()`, which is
`RECEIVER_EXPORTED` on API 33+ (`Utils.kt:552-556`), used at `CoreServiceManager.kt:242` and
`MainViewModel.kt:92`. A third-party app can `sendBroadcast(Intent("com.departamentvpn.app.action.service").setPackage("com.departamentvpn.app").putExtra("key", 4))`
and kill the tunnel (`MSG_STATE_STOP`), or `key = 5` to force the F-2 restart path. It can equally
spoof `…action.activity` to fake a connected UI. These broadcasts are purely internal —
`RECEIVER_NOT_EXPORTED` is the correct flag.

**F-8 (medium) — silent start failures leave the UI to a 20 s timeout.**
`CoreVpnService.setupVpnService` `stopSelf()`s without any broadcast when `prepare()` is non-null or
`configureVpnService()` fails (`:186-198`), and `configureVpnService` swallows an `establish()`
throw into `stopAllService()` (`:229-237`). Only the watchdog (`MainActivity.kt:179-190`) rescues the
UI, 20 s later. These should call `reportStartFailure(...)` (`:143-149`), which already exists.

**F-9 (low) — tun is up before the core is.** `runTun2socks()` (`CoreVpnService.kt:200,361-374`)
starts hev before `startCoreLoop` (`:127`), so early packets are black-holed into a closed SOCKS
port. Starting the core first (or holding hev until `MSG_STATE_START_SUCCESS`) removes the gap.

**F-10 (low) — dead tun2socks watchdog.** `TProxyService` takes `isRunningProvider` and
`restartCallback` (`TProxyService.kt:18-19`, passed at `CoreVpnService.kt:365-367`) and **never uses
either**. There is no supervision of the hev tunnel; if the JNI side dies the VPN stays "connected"
with no traffic. (The auto-fallback health check at `MainActivity.kt:580-591` is the only backstop,
and it fires once per session.)

**F-11 (low) — `removeAllServer` over-deletes.** `MmkvManager.kt:287-293` calls
`mainStorage.clearAll()`, which also drops `KEY_SUB_IDS` and `KEY_WEBDAV_CONFIG` (`:32-33`). The
subscription id list self-heals via `initSubsList()` (`:354-363`), the WebDAV config does not.

**F-12 (low) — inert memory-limit setting.** `SettingsManager.getMemoryLimit`/`isMemoryLimitEnabled`
(`:387-396`) are read nowhere except the Settings UI; `CoreServiceManager.kt:258-264` documents that
libv2ray exposes no setter. The toggle promises something the app cannot do.

**F-13 (low) — `auto_fallback_switching` has no Russian translation** (`values/strings.xml:333`,
absent from `values-ru/strings.xml`), so Russian users get an English sentence at exactly the moment
the connection is failing.

**F-14 (info) — `Utils.isXray()` is false for this fork.**
`Utils.kt:563` tests `BuildConfig.APPLICATION_ID.startsWith("com.v2ray.ang")`, but the fork ships
`applicationId = "com.departamentvpn.app"` (`app/build.gradle.kts:13`). The app therefore takes the
"not Xray" branches — an extra HTTP inbound at `socksPort + 1` (`CoreConfigManager.kt:653-664`,
`SettingsManager.kt:372`) — even though it runs the Xray core. Harmless today (HttpUtil relies on
that HTTP inbound, `HttpUtil.kt:335-336`), but the predicate is now lying about what it tests.

**F-15 (info) — `vpnProtect` is implemented and never called.** `ServiceControl.vpnProtect`
(`contracts/ServiceControl.kt:27`), `CoreVpnService.kt:171-173`. Socket protection depends entirely
on `addDisallowedApplication(self)` (`CoreVpnService.kt:322-341`); a future per-app-proxy change that
lets the app itself into the tunnel would create a routing loop with no second line of defence.

**F-16 (info) — `SettingsChangeManager` is per-process and only drained in one place.**
`SettingsChangeManager.kt:5-45`; consumed exclusively in `MainActivity.requestActivityLauncher`
(`:219-230`). A settings screen reached by any other route leaves `_restartService = true` pending
until the next `requestActivityLauncher` result, which then restarts the tunnel at a surprising
moment.

---

## 10. Quick map for follow-up work

```
tap  MainActivity.handleFabAction:1483
  → startVpnWithPermission:1506 → VpnService.prepare
  → startV2Ray:1519            (guard: getSelectServer)
  → CoreServiceManager.startVService:86 → startContextService:130
        guid  = MmkvManager.getSelectServer          MmkvManager.kt:63
        cfg   = MmkvManager.decodeServerConfig       MmkvManager.kt:139
        ↳ errors here = «Неправильный профиль»       values-ru/strings.xml:134
  → startForegroundService(CoreVpnService)           CoreServiceManager.kt:182
====== process boundary → :RunSoLibV2RayDaemon ======
  CoreVpnService.onStartCommand:115
    NotificationManager.showNotification(null):120
    setupVpnService:186 → configureVpnService:207 → builder.establish():230
                        → runTun2socks:361 → TProxyService.startTun2Socks (hev, JNI)
    startService:155 → CoreServiceManager.startCoreLoop:202
        doStartCoreLoop:226
          CoreConfigManager.getV2rayConfig:35
            CoreConfigContextBuilder.build:30
            buildUnifiedConfig:286 | buildV2rayCustomConfig:82
          coreController.startLoop(json, tunFd):265
          MSG_STATE_START_SUCCESS:283
====== broadcast → main process ======
  MainViewModel.mMsgReceiver:665 → isRunning = true
  MainActivity.isRunning.observe:550 → applyConnectedState:1610 + health check:1936
```

