# Sweep — the audit / hunt / verify / recon reports vs. the code today

**Written:** 2026-07-26 · **Branch (both repos):** `claude/app-audit-agents-hyyftk`
**Scope:** every report in `docs/agents/` — the six `audit-*.md`, four `hunt-*.md`, twenty
`verify-*.md`, eleven `recon-*.md`, the seven `audit2026/*.md`, plus `bugs-android-confirmed.md`
and `gap-desktop-to-android.md`.

**Method.** Every actionable finding was extracted from the reports and then re-read against the
working tree — `/home/user/dp/V2rayNG/app/src/main` (Android) and `/home/user/v2rayN/v2rayN`
(desktop). An item is listed OPEN only where I read the code and the implementing symbol is absent,
or present with no reader/writer. Line numbers are from the tree at sweep time and will drift;
symbol names will not. Read-only; no source file changed, no git command run.

**Headline.** The waves closed far more than the state audit could see — of the twenty `verify-*.md`
reports, **fourteen are now fixed**, and most of `audit-desktop-ui.md`'s medium tier landed. What is
left is not scattered polish. It clusters into five bodies of work, and **four of the five were
never assigned to anybody**:

1. **`audit-android-data.md` was never worked.** Twenty-six findings, including **two Critical and
   four High**, and not one of them entered `bugs-android-confirmed.md`'s D-list except the three
   that arrived from other documents (D19/D01/D25). The Critical at the top — a web page can fire
   `depv://` and wipe the ungrouped server bucket, install and *select* an attacker's server, drop
   the tunnel, and replace the routing rules, all with no confirmation — is the single most serious
   thing in this sweep.
2. **`recon-android-peripherals.md` §2 was never worked.** Every launcher shortcut is dead (wrong
   `targetPackage`), and every peripheral start path (tile, widget, shortcut, Tasker) skips the VPN
   consent dialog and fails silently.
3. **`audit2026/android-account.md`'s five money-path P0s were never worked.** A balance payment
   reports success without reading the status the backend returned; the Devices page is never told
   which subscription it is for; nothing prevents a double charge.
4. **`hunt-transient-ui.md` P9-P20 were never re-verified and are still open** — `bugs-android-confirmed.md`
   §4 says so in as many words. So is the Android half of the connect-flow group (D06/D07/D08),
   which the P1/P2 desktop half **was** fixed for.
5. **`gap-desktop-to-android.md`'s PORT NOW list is 1 of 8 done.** Six Android work orders (W1-W6)
   are untouched, and both desktop-bound orders (A1 auto-fallback, A3 provider controls) are
   untouched.

---

## 1. Never assigned to any wave — ranked

### 1.1 `audit-android-data.md` — the whole document (26 findings, 0 worked)

`bugs-android-confirmed.md` §4 says it "was read for its Android findings but its data-layer claims
below medium severity were not individually re-verified." In fact **nothing above medium was carried
across either**, except the three that reached the D-list from other documents. I re-checked the
whole severity index. Every row below is still exactly as the audit describes it.

| # | Finding | Verified today |
|---|---|---|
| **S1** | **Critical — a browsable deep link wipes the ungrouped bucket, installs + selects an attacker server, stops/starts the tunnel and replaces routing rules, with no confirmation UI.** | `ui/UrlSchemeActivity.kt:142` and `:186` still call `AngConfigManager.importBatchConfig(…, "", false)` — `append = false`, `subid = ""`. `ui/ScScannerActivity.kt:22` likewise. Tunnel control is live at `UrlSchemeActivity.kt:88, 90, 94, 96`; routing replacement at `:124` (`importRoutingRules(json, apply = …)`). There is no dialog anywhere in the file. Contrast `MainActivity` and `tv/TvReceiveActivity`, which both pass `append = true` |
| **S2** | **Critical — user-installed CA certificates trusted app-wide, cleartext permitted, no pinning.** | `res/xml/network_security_config.xml` is unchanged: `cleartextTrafficPermitted="true"` and a `<certificates src="user" />` trust anchor with `tools:ignore="AcceptsUserCertificates"`. For a product whose threat model is a national censor, this is the wrong default |
| **S3** | **High — Zip Slip and zip-bomb in backup restore.** | `util/ZipUtil.kt:88` still `zip.entries().asSequence().forEach { … }` with no canonical-path check, no entry cap and no total-size cap. Reachable from `ui/BackupActivity.kt` restore |
| **S4** | **High — exported broadcast receivers let any installed app stop the VPN.** | `util/Utils.kt:552-555` `receiverFlags()` returns `ContextCompat.RECEIVER_EXPORTED` on API 33+ |
| **S5** | **Medium-High — the "departament-only" guards accept attacker-controlled domains.** | `util/SubscriptionGuard.kt:19` `host.split(".").any { it == REQUIRED_LABEL }` accepts `departament.<any-tld>`; `util/SubscriptionOrigin.kt:35` `host.lowercase().contains("departament")` accepts `evil-departament.com` |
| **S6** | **Medium — no `callTimeout`, no body-size cap in the fetch path.** | `util/HttpUtil.kt:380-382` sets only `connectTimeout`/`readTimeout`. A server that dribbles bytes forever is unbounded in both time and memory |
| **S7** | **Medium — the SOCKS-share password uses a non-cryptographic RNG.** | `handler/SettingsManager.kt:37` imports `kotlin.random.Random`; `:364` `chars[Random.nextInt(chars.length)]` generates the LAN-exposed credential. `:496` uses the same RNG for the dynamic SOCKS port |
| **S8** | **Medium — the full subscription URL (a bearer-equivalent secret) is written to logcat.** | `handler/AngConfigManager.kt:892` `LogUtil.i(AppConfig.TAG, fetchUrl)` |
| **S9** | **Medium — deleted subscriptions resurrect.** | `handler/MmkvManager.kt:397-406` `initSubsList()` rebuilds `KEY_SUB_IDS` from `subStorage.allKeys()` whenever the list is empty, in arbitrary order |
| **S10** | **Medium — the updater points at upstream `2dust/v2rayNG` and offers its APK.** | `AppConfig.kt:144-145` `APP_URL` / `APP_API_URL`; `:148` privacy policy; `:150` upstream Telegram channel. `CheckUpdateActivity` is now reachable (`MainActivity.kt:3154`, `AboutActivity.kt:91`), so this ships a *live* button that offers a departament user a different app's build |
| **S11** | Medium-High — cross-process read-modify-write on the stored lists; migrations run unserialised in every process | not re-verified in depth this pass; the structural facts (`AngApplication.kt` `:bg` process, `MmkvManager` list rewrites) are unchanged |
| **S12** | Low-Medium — unbounded avatar cache; `ProfileItem.equals` without `hashCode`; HWID forwarded to redirect targets; sync clobbers the user's subscription name | not re-verified individually |

`S10` is now *worse* than when it was written: at the time `CheckUpdateActivity` had no entry point,
so the upstream URLs were unreachable. Giving it a door (a good fix) shipped this defect.

### 1.2 `recon-android-peripherals.md` §2 — the peripheral surfaces

| # | Finding | Verified today |
|---|---|---|
| **S13** | **Critical — every launcher shortcut is dead.** `res/xml/shortcuts.xml` pins `android:targetPackage="com.v2ray.ang"` on all four shortcuts, while `V2rayNG/app/build.gradle.kts:13` sets `applicationId = "com.departamentvpn.app"`. The intents resolve to nothing | read both files; unchanged |
| **S14** | **Critical — every peripheral start path skips VPN consent and fails silently.** `core/CoreServiceManager.kt:66-79` `startVServiceFromToggle` checks only for a selected server and then calls `startContextService`; there is no `VpnService.prepare()`. Its callers are `service/QSTileService.kt:78`, `receiver/WidgetProvider.kt:73`, `receiver/TaskerReceiver.kt:31`, `ui/ScStartActivity.kt:15`, `ui/ScSwitchActivity.kt:17`. Only `MainActivity.startVpnWithPermission()` prepares | unchanged |
| **S15** | **High — release builds are unminified.** `V2rayNG/app/build.gradle.kts:63` `isMinifyEnabled = false`. The release signing key (`:70`, `signingConfigs.getByName("debug")`) carries a comment explaining the choice, so treat *that* half as a stated decision, not a defect | unchanged |
| **S16** | High — the About screen points departament users at upstream v2rayNG (same root cause as S10) | `AppConfig.kt:144-150` |
| **S17** | Medium — `CoreProxyOnlyService` can be started as a foreground service without ever calling `startForeground`; the widget is below Android's widget-quality bar; backup round-trips the encrypted session store | not individually re-verified |

### 1.3 `audit2026/android-account.md` — the five money-path P0s

The account-tab wave in flight is a *design* rebuild. These are logic defects and none of them is on
any wave's list.

| # | Finding | Verified today |
|---|---|---|
| **S18** | **P0-1 — a balance payment reports success without reading its status.** `viewmodel/AccountViewModel.kt:344-346`: `repo.payWithBalance(req).onSuccess { onDone() }`. `PaymentResultDto.status` is never inspected. Both callers then report success and `BuyTariffActivity` calls `finish()`. A `200` carrying `status: "failed"` closes the buy screen and tells the user they own a subscription they did not buy | unchanged |
| **S19** | **P0-4 — the Devices page is never told which subscription it is for.** `ui/DeviceManagementActivity.kt:235` declares `EXTRA_REMNAWAVE_UUID` and `:58` reads it — **zero senders exist in the whole tree**. On a multi-subscription account the page shows the root subscription's devices and unlinks against the root uuid whatever card the user was on. **The desktop half of this exact defect was fixed** (`DevicesView.axaml.cs:31` now passes `AccountViewModel.Shared?.DevicesScopeUuid`); Android was left behind | unchanged |
| **S20** | **P0-3 — nothing prevents a double charge.** No in-flight flag, no disabled CTA, no debounce: `grep "isPaying\|isEnabled = false\|input_debounce"` over `BuyTariffActivity.kt`, `PaymentMethodSheet.kt`, `AccountFragment.kt` returns **0**. `progress_buy` is bound and set `GONE` at `BuyTariffActivity.kt:176, 187, 197, 218` and never set `VISIBLE` anywhere. (Same defect as `bugs-android-confirmed` D10 and `hunt-transient-ui` P7 — three documents, one hole) | unchanged |
| **S21** | **P0-2 — declined / cancelled / timed-out checkouts end in silence.** `ui/AccountFragment.kt:833-851` and `ui/BuyTariffActivity.kt:582-593` run a fixed `repeat(6)`/`repeat(5)` × 8 s poll, never inspect payment status, then hide the hint with no verdict, no copy and no action. The `orderId` from `PaymentInitDto` is discarded. (Same as `hunt-transient-ui` P9) | unchanged |
| **S22** | **P0-5 — the currency code decides the symbol.** Two private `currencySymbol` copies survive (`ui/BuyTariffActivity.kt:639`, `ui/AccountFragment.kt:862`) plus the mapping in `ui/adapter/PaymentsAdapter.kt:92`. The owner's ₽ decision is not enforced by one formatter | unchanged |

Also still true from the same audit: `res/values/strings_account.xml:44` `«ПРОБНЫЙ»` is ALL-CAPS
against `00-rules.md` 9.2, and all three account adapters still call `notifyDataSetChanged()`
(`SubscriptionPagerAdapter.kt:33`, `PaymentsAdapter.kt:26`, `HomeMetaPagerAdapter.kt:34`).

### 1.4 `hunt-transient-ui.md` P9-P20 — named as not-verified, still open

`bugs-android-confirmed.md` §4 lists these explicitly as "plausible and **not** re-verified …
they belong to a second pass". This is that pass.

| # | Item | Verified today |
|---|---|---|
| **S23** | **P16 (Android, medium) — every carousel page shows the ROOT subscription's device count.** `ui/AccountFragment.kt:174` `resolveUsedDevices = { viewModel.deviceCount.value ?: 0 }` **ignores its `SubInfoDto` argument**; `:403` fetches devices for `list.firstOrNull()` only; `ui/SubscriptionPagerAdapter.kt:88` applies the value to whichever card it binds. This is precisely the failure mode the repo `CLAUDE.md` warns about — a subscription-scoped value resolved from a client-level default instead of `selectedSub.uuid` | unchanged |
| **S24** | **P10 (Android, medium) — the payment poll window restarts indefinitely on tab re-entry.** `ui/AccountFragment.kt:826` `if (pendingPayment) startPaymentPolling()` in `onResume`; the job runs on `viewLifecycleOwner.lifecycleScope` and is cancelled by the tab switch **before** `pendingPayment = false` at `:848` | unchanged |
| **S25** | **P19 (Android, low) — re-tapping the already-selected server row does nothing at all.** `ui/MainActivity.kt:1815-1817`: `if (guid == selected) return` precedes every piece of feedback. **The desktop half of this was fixed** (`HomeViewModel.cs` connects explicitly on a re-tap while disconnected); Android was left behind | unchanged |
| **S26** | **P20 (Android, low) — the hero says «Подключение…» while the uptime clock keeps counting the old session.** `ui/MainActivity.kt:1998-2010`: the `isLoading` branch returns without calling `stopConnectionTimer()`. Secondary: the switch snackbar's label is built from the `guid` argument but its action calls `restartV2Ray()`, which starts `getSelectServer()` — if the selection moves while the bar is up, the label names one server and the action connects to another | unchanged |
| **S27** | **P11 (PC, medium) — a sub-page swallows clicks for 300 ms before it is visible.** `Views/MainWindow.axaml.cs:1156` sets `subPageHost.IsVisible = true` and then fades opacity 0 → 1. Avalonia hit-tests on `IsVisible`, not `Opacity`, so a fully-transparent full-screen page is interactive for the whole fade | unchanged |
| — | P13, P14, P15, P17, P18 | not re-verified this pass; recorded so the next sweep knows they are still unexamined, not cleared |

### 1.5 The Android connect flow — the desktop half was fixed, the Android half was not

`hunt-transient-ui.md` §"Notes for the fix phase" says P1/P2 (PC) and P3/P4/P5 (Android) "are the
same defect on two platforms and should be fixed as one contract." Only the PC side landed.

| # | Item | Verified today |
|---|---|---|
| **S28** | **D06 / P5 (high) — the connect control fires on every tap, including during «Подключение…».** `ui/MainActivity.kt:435-438` is a plain `setOnClickListener { animateConnectPress(); handleFabAction() }`, and `handleFabAction` (`:1852`) branches only on `isRunning.value`. A second tap during a connect issues another `startVpnWithPermission()` and another `scheduleConnectWatchdog()`, which is `removeCallbacks` + `postDelayed` — i.e. it pushes the 20 s deadline out. There is no way to cancel a connect. **The PC equivalent (P1) is fixed** (`HomeViewModel.cs:193` `if (IsConnecting \|\| _disconnecting)`). Note the guard primitive now exists: `ui/component/SingleClick.kt` is written and consumed by `ToolbarBinder`/`EmptyStateBinder`/two activities — it is simply not applied here |
| **S29** | **D07 / P4 (high) — the "no server" guard leaves the connecting state and the watchdog running.** `ui/MainActivity.kt:1894-1899` `startV2Ray()` toasts `title_file_chooser` (a borrowed file-chooser string) and returns without clearing `connectInProgress`, cancelling the watchdog or idling the shield. Twenty seconds later the user is told «Не удалось подключиться». **The PC equivalent (P2) is fixed** (`HomeViewModel.cs:207` `if (!HasServers) return;` — though see §5 for the residual dead-tap that fix introduced) |
| **S30** | **D08 / P3 (high) — cancelling Android's own VPN consent dialog is reported as a connection failure.** `ui/MainActivity.kt:363-367`: the `registerForActivityResult` callback has **no `else`**. A non-`RESULT_OK` result is dropped, leaving `connectInProgress` and the watchdog exactly as `handleFabAction` left them |
| **S31** | **D09 / P6 (high) — a failed payment leaves a live «Итого» and «Оплатить» for an invisible selection.** `ui/BuyTariffActivity.kt:206-245` `renderTariffs` clears `checkMarks`/`optionRows` and rebuilds the container, and never touches `selectedTariff`, `selectedOption`, `extraDevices` or `checkoutCard.visibility`. `selectTariff` (`:346`) returns early when the tariff is already selected, so re-tapping cannot recover the paint |
| **S32** | **D11 (high) — rotating with the payment-method sheet open crashes on pick.** `ui/PaymentMethodSheet.kt:135` still keeps the process-static lambda across configuration changes; the `AccountFragment` lambda captures the dead fragment instance. No `setFragmentResultListener`, no FragmentManager re-resolution |

### 1.6 `gap-desktop-to-android.md` — the PORT NOW list

Eight ordered work orders. **One is done, and by accident.**

| Order | What | State |
|---|---|---|
| **W1** — throughput test per server (F1+F5+F11) | **S33 · never started.** `grep "DoSpeedTest\|throughput\|Мбит/с\|speedMbps"` over `java/` + `res/` = **0**. `handler/SpeedtestManager.kt` still has only http/icmp/tcp/socket paths, and `dto/entities/ServerAffiliationInfo.kt` still persists one field | OPEN, M |
| **W2** — latency timeout + test address on the designed surface (S3+S4) | **S34 · half.** The *address* now has an editor (`pref_delay_test_url` in `res/xml/pref_settings.xml:251`, and `SettingsActivity` is reachable at `MainActivity.kt:3152`). The **timeout key was never created**: `grep PREF_PING_TIMEOUT` = 0, and `handler/SpeedtestManager.kt:305` is still `timeoutMs: Int = 3000` with its one caller passing no override | OPEN, S |
| **W3** — FakeIP toggle on the DNS page (S5) | **done, incidentally.** `pref_fake_dns_enabled` sits at `res/xml/pref_settings.xml:147` with the ratified copy (`adv_fake_dns_title/summary`) and a modelled dependency, and `SettingsActivity` now has a door. It is on `settings/advanced`, not the `settings/dns` page the order named — good enough to close the capability, wrong home for the design | DONE (surface differs) |
| **W4** — default uTLS fingerprint (S1) | **S35 · never started.** `grep "UTLS_FINGERPRINT\|utls_fingerprint\|defFingerprint"` = **0**. `core/CoreOutboundBuilder.kt` still passes only the per-node value, so a node imported without `fp=` runs with an empty fingerprint — the JA3/JA4 flag `strategy-russia-2026.md` §3.3 names as a direct detection signal. One `?:` plus one row | OPEN, S |
| **W5** — import routing rules from a URL (R2) | **S36 · never started.** `grep "import_rulesets_from_url\|importFromUrl"` = **0**. The clipboard/QR/predefined entries exist; the URL one does not. Copy is already drafted in §9.1 of the gap doc | OPEN, S |
| **W6** — WebDAV connection test (D3) | **S37 · never started.** `grep "checkConnection"` over `java/` = **0**. `ui/BackupActivity.kt` still saves four fields with no verification. `12-settings.md` 5.12 already ratifies the row and both result strings | OPEN, S |
| **A1** — auto-fallback → **desktop** | **S38 · never started.** `grep -rni "autofallback\|auto_fallback"` over `/home/user/v2rayN/v2rayN` = **0 hits**. The gap doc calls this "the single most user-visible behaviour difference"; `12-settings.md` 5.9 row f already declares the row, default on, platform **both**, desktop binding **NEW** | OPEN, M |
| **A3** — provider controls → **desktop** | **S39 · never started, and the page it would live on is still unreachable.** `grep "PingOnLaunch\|PingOnUpdate\|NotifyOnUpdate"` over the desktop tree = **0**, and `grep "new ProviderSettingsPage"` = **0 construction sites** — `Views/ProviderSettingsPage.axaml` still ships as dead code (`11-app-structure.md` 10.2 verdict WIRE) | OPEN, M |

Good news from the same document: **A6** (`subscription-userinfo` header parsing on desktop, ordered
PORT LATER) **landed** — `ServiceLib/Services/DownloadService.cs:322` reads the header and
`ServiceLib/Handler/SubscriptionHandler.cs:281-343` persists and parses it. Do not re-file it.

### 1.7 Assorted, still open, from documents nobody closed

| # | Item | Source | Verified today |
|---|---|---|---|
| **S40** | **D17 is only two-thirds done. `SubSettingActivity` still has zero references** — it is declared in the manifest and reachable from nowhere. `SettingsActivity`, `LogcatActivity` and `CheckUpdateActivity` all got doors (`MainActivity.kt:3152-3154`, `AboutActivity.kt:85,91`); the provider-list editor did not | `bugs-android-confirmed` D17, `audit-android-ui` A8 | `grep SubSettingActivity` outside its own file = 0 |
| **S41** | **D16's residue: ~10 preference keys still have a live reader and no writer anywhere.** `res/xml/pref_settings.xml:25-28` lists them as deliberately dropped, but the readers survive. Worst: **`PREF_CONFIRM_REMOVE`**, read at `MainActivity.kt:1757`, `ServerActivity.kt:669`, `ServerProxyChainActivity.kt:206`, `SubEditActivity.kt:290`, `SubSettingActivity.kt:180` with **no default**, i.e. effectively `false` — a fresh install deletes servers and subscriptions with no confirmation and cannot turn it on. Also `PREF_SHOW_MEMORY` (`MainActivity.kt:2254`), `PREF_PREFER_IPV6` (`CoreOutboundBuilder.kt:682`, `CoreConfigManager.kt:1002`), `PREF_GROUP_ALL_DISPLAY` (`MainViewModel.kt:581`), `PREF_MUX_XUDP_QUIC` (`CoreOutboundBuilder.kt:66`), `PREF_DYNAMIC_SOCKS_PORT` (`SettingsManager.kt:492`) | `bugs-android-confirmed` D16, state audit §4.2 | unchanged |
| **S42** | **D15 — a search with zero matches empties Home into the onboarding state and hides the whole bottom nav.** `MainViewModel.serversCache` is still the *filtered* list (`:246-259`), and both `MainActivity.updateHomeEmptyState():1079` and `updateBottomNavVisibility():1113` gate on `serversCache.isEmpty()`. The chain is live: `ServersFragment.kt:117` → `filterConfig` → `reloadServerList` → `updateListAction` → `refreshServerLists:1148` → `updateHomeEmptyState`. The new protocol-filter chips (`MainViewModel.applyProtocolFilter`) are a **second** way in. `prepareMenu:1025` already got this right and reads the store instead — the fix pattern exists in the same file | `bugs-android-confirmed` D15, `hunt-android-cold-start` F3 | unchanged |
| **S43** | **D22 — the dead «Привязать Telegram» banner is still shipped.** `MainActivity.kt:1428` `header.groupLogin.isVisible = false` unconditionally; `:1458-1465` `updateLoginCtaVisibility()` requires `!isLoggedIn()` while `:1427` `header.root.isVisible = loggedIn`. `ctaDismissed` (`:256`), both handlers (`:1356-1359`) and two strings are dead weight. `verify-link-telegram-cta-unreachable.md` confirmed it and downgraded it to *delete, do not repair* | `bugs-android-confirmed` D22 | unchanged |
| **S44** | **D21 — the `R.id.sub_update` branch at `MainActivity.kt:2468` dispatches an id declared only in `res/menu/action_sub_setting.xml`, which `MainActivity` never inflates** | `bugs-android-confirmed` D21 | unchanged |
| **S45** | **`audit-android-ui` A13 — `customProtoCache` is never invalidated.** `ui/MainRecyclerAdapter.kt:298-317`: a `HashMap<String, CustomProtoInfo?>` keyed by guid, written once and never cleared on rebind, refresh or subscription update. A guid reused after a subscription refresh renders the previous profile's protocol chip | `audit-android-ui` A13 | unchanged |
| **S46** | **`recon-android-selection` 5.6 — `updateListAction` carries a *position*, not a guid.** `viewmodel/MainViewModel.kt:61,430,879`. Any list mutation between publish and consume repaints the wrong row. The adapter has no stable ids and no `DiffUtil` | `recon-android-selection` 5.6, `audit2026/android-servers` | unchanged |
| **S47** | **`audit-android-copy` D1 / `sweep-plans` P2 — the accessibility pass was never started.** `grep -rc labelFor res/layout/` = **0 files**; `grep -rn announceForAccessibility java/` = **0**. No connection state is ever announced, and 57 `EditText`s have no accessible name | `audit-android-copy` D1, `ux-recommendations` P2-4 | unchanged |

---

## 2. Desktop — the medium and low tiers that were deprioritised

`audit-desktop-ui.md` fared well: findings **5, 6, 7, 8, 9, 12** are all fixed (see §4). These are
what is left, plus the untouched half of `audit-desktop-core.md`.

| # | Item | Verified today |
|---|---|---|
| **S48** | **`audit-desktop-core` M4 — the Xray `api` inbound is grafted onto every config for a feature that is switched off.** `ServiceLib/Manager/CoreManager.cs:55` `EnableHotSwapTier = false`, yet `ServiceLib/Handler/CoreConfigHandler.cs:240` calls `GraftXrayApi(root)` unconditionally. A mandatory `dokodemo-door` inbound on a TOCTOU-chosen port, whose only consumer is disabled: pure added connect-failure surface with zero benefit | unchanged |
| **S49** | **`audit-desktop-core` M5 — traffic stats read zero whenever the provider tags its outbound anything but `proxy*`.** `ServiceLib/Services/Statistics/StatisticsXrayService.cs:96` `if (key.StartsWith(Global.ProxyTag))`. Departament custom nodes keep the template's outbound tags as-authored, so a template naming its outbound `VLESS-out` yields a permanently 0 KB/s speed widget with no error. `CoreManager._runningProxyTag` already computes the right answer | unchanged |
| **S50** | **`audit-desktop-core` M7 — `ClientWebSocket` is aborted but never disposed on every reconnect.** `ServiceLib/Services/Statistics/StatisticsSingboxService.cs:35, 43, 57, 94` — `Abort()` then `= null`, never `Dispose()` | unchanged |
| **S51** | **`audit-desktop-core` M9 — a blocking `.Wait()` on the startup thread for a paged SQLite migration.** `ServiceLib/Manager/AppManager.cs:136-139` `Task.Run(async () => await MigrateProfileExtra()).Wait()` inside `InitComponents` | unchanged |
| **S52** | **`audit-desktop-core` M11 — the API `HttpClient` never refreshes DNS and inherits the app's own system proxy.** `v2rayN.Desktop/Account/DepartamentApiClient.cs:24-33`: a `static readonly HttpClient` over a plain `HttpClientHandler`, no `PooledConnectionLifetime`, `UseProxy` left at its default `true`. Additionally `:56` hard-codes `x-device-os: "windows"` on a client that ships for Linux and macOS | unchanged |
| **S53** | **`audit-desktop-core` M17 / `hunt-persistence` P2 — `AuthTokenStore.Persist` is a non-atomic whole-file write.** `v2rayN.Desktop/Account/AuthTokenStore.cs:188-198` `File.WriteAllBytes` straight over the live session file. A crash mid-write leaves an unreadable session and the user silently signed out | unchanged |
| **S54** | **`hunt-persistence` P3 — the AES key is derived from a machine seed that can silently change, with no re-key path.** `AuthTokenStore.cs:206` keys off `MachineSeed()`, which falls back to `MachineName\|UserName` when `MachineGuid`/`machine-id` is unreadable (`:256-300`). A hostname change makes the session permanently undecryptable and nothing tells the user | unchanged |
| **S55** | **`hunt-persistence` P5 — a corrupt-but-readable config resets every preference and then overwrites the file.** `ServiceLib/Handler/ConfigHandler.cs:19-36`: the guard added at `:29-33` only covers the *unreadable* file case. Non-empty malformed JSON deserialises to `null`, falls through to `config ??= new Config()` at `:36`, and the next save writes the defaults over the user's file | partly fixed, residue open |
| **S56** | **`audit-desktop-core` M3 — 33 Rx `Subscribe(async …)` handlers swallow every exception.** `grep -rn "Subscribe(async" --include=*.cs` = **33** | unchanged |
| **S57** | **`audit-desktop-core` L2 / L4 / L6 — `SysProxyHandler.UpdateSysProxy` always returns `true` (`ServiceLib/Handler/SysProxy/SysProxyHandler.cs:66`); `AppManager.ProfileModels` builds SQL by string concatenation (`ServiceLib/Manager/AppManager.cs:271-277`); `await _updateFunc?.Invoke(...)` throws `NullReferenceException` when the delegate is null (`TaskManager.cs:99,119`, `CoreAdminManager.cs:29`, `StatisticsManager.cs:126`, `CoreManager.cs:1364`)** | unchanged |
| **S58** | **`audit-desktop-ui` 14 / 16 / 17 / 19 — six `CancellationTokenSource` fields in `AccountViewModel` are `Cancel()`-then-replaced and never disposed; the post-top-up poll catches only `OperationCanceledException`; `MainWindow.OnClosing` is still `async void` (`Views/MainWindow.axaml.cs:1942`); the dead `obj is bool b` pattern survives at `:2109`** | `_programStartedWait` **was** fixed (`:369` stores it, `:1981` unregisters it) |
| **S59** | **`audit-desktop-ui` 15 — `ServerListView.RegisterInteractions` early-outs on a non-empty handler list, so a VM identity change leaves stale handlers, and both layout copies stay registered** | not re-verified in depth; the early-out shape is unchanged |

Still open and already logged by the state audit (repeated here only so this document is
self-contained — **do not double-count**): the TUN toggle guard at
`ServiceLib/ViewModels/StatusBarViewModel.cs:513`; the seven theme keys missing from
`BuildMonoOverlay` (`App.axaml.cs`); `PortInvalid` written at `SettingsViewModel.cs:410` and read
nowhere; «Автообновление провайдеров» still writing `GuiItem.AutoUpdateInterval`
(`SettingsViewModel.cs:461` → `TaskManager.cs:113`, uptime-hours modulo); `MsgViewModel` constructed
only in `DesignData.cs:26` so 156 message publishers vanish; `SettingsView.axaml:68` shadowing the
promoted `TextBox.IncyField` at `GlobalResources.axaml:635`; no `Key.Escape` / `XButton1` / `Ctrl+F`
in the shell.

One state-audit item has **partly closed** and should be re-measured rather than re-filed:
`AutomationProperties.Name` now appears in **7 of 50** views (the audit said 1), and an offline
state now exists (`AccountView.axaml:177` `Classes="OfflineBar"`, `ConnectHeroView.axaml:134`).

---

## 3. Confirmed by a `verify-*.md` and still not picked up

Two of the twenty verify reports confirmed a defect that no wave has closed. Both are already named
above; they are collected here because "a dedicated adversarial verification said yes and nothing
happened" is the strongest signal in the corpus.

| Report | Verdict | State today |
|---|---|---|
| `verify-link-telegram-cta-unreachable.md` | CONFIRMED (real), severity corrected to **low — delete, do not repair** | **still shipped** — S43 |
| `verify-settingsview-incyfield-shadowing.md` | CONFIRMED (real), severity corrected **high → medium** | **still shadowed** — `SettingsView.axaml:68` vs `GlobalResources.axaml:635` |

A third, `verify-subscriptionmetaview-theme-brushes.md`, is **half** closed: a `ResolveBrush(key,
fallback)` helper was added (`Views/SubscriptionMetaView.axaml.cs:465`) and the traffic gradient uses
it (`:441`), but the three static dark-theme literals at `:30-32` are still assigned directly to the
pin icon and the expiry text at `:400, :415, :423, :429, :637, :744, :747`. In Light and Mono those
two elements still paint dark-theme hexes.

---

## 4. Fixed since the report that raised it — do not re-report

Verified individually. This is the good news, and it is substantial.

**Android**
- `verify-showtab-fadethrough-race.md` / **D03** — closed properly, with a `tabSwapId` generation
  counter and a `settleTabs()` single authority (`MainActivity.kt:733-762, 787-797`).
- `verify-back-key-tab-navigation.md` / **D13** — closed. `onKeyDown` (`:3092-3097`) no longer
  consumes BACK; `KEYCODE_BUTTON_B` routes through `onBackPressedDispatcher`.
- `verify-server-actions-longpress.md` / **D04** — closed. `MainRecyclerAdapter.kt:252` sets the
  long-press listener; both hosts assign the callback.
- **D05** (testing sentinel), **D14** (`removeAllServer` no longer `clearAll`s a shared store,
  `MmkvManager.kt:303-315`), **D19** (staged XRAY_JSON parse, `AngConfigManager.kt:618-641`),
  **D23** (`serverRawStorage` now cleaned at `:215, :239, :313`), **D24** (`tag_last_click` consumed
  by `ui/component/SingleClick.kt`), **D27** (`broadcastRegistered` flag, `MainViewModel.kt:163`).
- **D17 in part** — `SettingsActivity`, `LogcatActivity`, `CheckUpdateActivity` all have doors now.
- **W3** — FakeIP has an editor (see §1.6).

**Desktop**
- `verify-desktop-sync-prune.md` — closed by a `canPrune` contract
  (`SubscriptionSyncManager.cs:90-122`), including the "candidates empty but remote reports
  something" case.
- `verify-coreopgate-removetundevice.md` — closed; `WindowsUtils.RemoveTunDevice` is now bounded
  (`:77`, `_tunRemoveTimeout`).
- `verify-profileex-queue-race.md` — closed; `ConcurrentQueue` + a membership companion
  (`ProfileExManager.cs:29-32`).
- `verify-realping-status-check.md` — closed; the probe now rejects a non-success status and uses
  `ResponseHeadersRead` (`ConnectionHandler.cs:86-100`).
- `verify-desktop-speedtest-custom-outbound.md` — closed; `FillOutbound` now throws on an
  unmapped `ConfigType` instead of shipping the sample decoy (`V2rayOutboundService.cs:62-71`).
- `verify-connect-disc-any-mouse-button.md` — closed; `e.InitialPressMouseButton == MouseButton.Left`
  (`ConnectHeroView.axaml.cs:488`).
- `verify-loginview-detached-handoff.md` — closed; `_detached = false` on re-attach
  (`LoginView.axaml.cs:180`).
- `verify-reactivecommand-subscribe-onerror.md` — closed; `grep "Execute().Subscribe()"` = 0.
- `verify-hasnextreloadjob-race.md` — closed; the flag protocol was rewritten around a
  strength-ordered pending job (`MainWindowViewModel.cs:838-851`).
- `verify-statuschip-contrast.md` — closed; Light now carries separate darkened text tones
  (`GlobalResources.axaml:190-222`) and the chips moved inside the theme dictionaries.
- `verify-tabswap-cancel-strand.md` — closed; the keep-alive loop now resets `Opacity`
  (`MainWindow.axaml.cs:455, 470`).
- `audit-desktop-core` M1 (`Observable.StartAsync`, `:467`), M2 (`Init` try/catch, `:330`),
  M6 (`processService.Dispose()`, `:310, 432, 494`), M13 (`PingMethod` falls back to the model
  default, `ConfigHandler.cs:152`).
- `audit-desktop-ui` 5 (try/catch + `finally` restore in the keyboard helpers, `:2066, 2084`),
  6 (`_pinning` / `_deleting` latches), 7 (`OnMetaAttached` re-hooks the group), 8 (`PushSubPage`
  dedups by type, `:1149`), 9 (all six animation tokens cancelled in `OnClosed`, `:1991-1995`),
  12 (`_proxyPanelBusy`).
- `hunt-transient-ui` P8 (pinned subscriptions sort first, `HomeViewModel.cs:747`),
  P12 (`_disconnecting`, `:217, 272`).
- `audit2026` P0-1 for **desktop** — `DevicesView.axaml.cs:31` passes the scoped uuid.
- **A6** from the gap doc — `subscription-userinfo` parsing landed on desktop.

---

## 5. Refused, superseded or contradicted — do NOT resurrect

| Item | Where it was raised | Why it is closed |
|---|---|---|
| Desktop «Серверы» destination | `33-master-plan-pc.md`; `audit2026/pc-servers-account.md` picks a canonical servers view | **Owner decision: overruled.** `BottomNavBar.axaml.cs:9` `enum AppTab { Home, Account, Settings }`. `ServersView` and `CompactServersView` still have **zero construction sites** — dead files, not progress. Harvest `CompactServersView.axaml:85-108` (the only desktop server search field ever written) before deleting |
| «Привязать Telegram» framed as a lost feature (HIGH) | `audit-android-ui` A5 | **Refuted** by `bugs-android-confirmed` §2 and `verify-link-telegram-cta-unreachable.md`: a live entry point exists at `layout_home_empty.xml:77`. What remains is dead-code cleanup (S43), not a functional regression |
| Per-app proxy never restarts the tunnel | `audit-android-ui` A12 | **Refuted** — `PerAppProxyActivity.kt` calls `SettingsChangeManager.makeRestartService()` and `MainActivity` consumes the flag |
| Rotation replays the connect state (toast, timer, animation) | `hunt-android-cold-start` F4 | **Refuted** — all three halves are guarded (`MainActivity.kt:890, 900-905`, persisted `KEY_CONNECTION_START`) |
| 19 amputated menu actions | `CONTINUE-HERE.md` 4.1, `audit-android-ui` A7 | **Closed by the salvage commit** — `menu_main.xml` is 12 items in two groups, all dispatched |
| "Ping all" / "Restart service" missing from the menu | salvage commit's own list | **Literally true, not defects** — both have live entry points elsewhere; adding menu duplicates is a design question |
| Provider toggles are write-only | `CONTINUE-HERE.md` 2.8 | **Still fixed**, all five consumers verified |
| Mixed test (F3), sort-by-column (F7), move-server-between-groups (F9), two extra export formats (F10), multiple routing sets (R1), import-rules-from-file (R3), seven advanced DNS knobs (R7), sniffing destination-override (S6), second local port (S7), Hysteria bandwidth hints (S8), font family/size (S17), sing-box TUN options (S19), SRS download (D5), certificate pinning fetch (D9), and the whole multi-core family (E1-E4, E6, E7, E10, E11) | `gap-desktop-to-android.md` §9.3 | Twenty-three capabilities refused with a reason on the row. `00-rules.md` 13 is satisfied by the argument, not by the port |
| Global hotkeys, system proxy/PAC, sudo prompt, window chrome/tray, per-window DPI, registry autostart plumbing, core-binary download, `sendThrough`/interface bind, root-certificate provider | `gap-desktop-to-android.md` §7.1 (N1-N9) | Genuine platform impossibilities, argued individually |
| `Geo.Nav.Servers` icon declared and unused | state audit | **Deliberate.** Do not "fix" |
| `StatusBarView` invisible at 0×0 | state audit | Load-bearing (tray, clipboard, sudo, TUN elevation). Do not delete |
| Release APK signed with the debug key | `recon-android-peripherals` 2.6 | The build file states the reason (directly installable artefact). The **`isMinifyEnabled = false`** half is still a live defect (S15) |

---

## 6. Order of work, by value per minute

**Tier 0 — security, and each is small.**
1. **S1** — `append = true` at `UrlSchemeActivity.kt:142, 186` and `ScScannerActivity.kt:22`, and a
   confirmation gate on every `depv://` action. Today a web page can wipe the user's server bucket
   and select an attacker's server. *S*
2. **S13 + S14** — one wrong string in `shortcuts.xml` and one missing `VpnService.prepare()` in
   `CoreServiceManager.startVServiceFromToggle`. Between them they are the reason every shortcut,
   the tile, the widget and Tasker are dead or silently fail. *S*
3. **S2** — drop the `user` trust anchor and `cleartextTrafficPermitted` from
   `network_security_config.xml`. *S*
4. **S3** — a canonical-path check and an entry/size cap in `ZipUtil.unzipToFolder`. *S*
5. **S10 / S16** — repoint `AppConfig.APP_URL` / `APP_API_URL` / the privacy policy / the Telegram
   channel at departament, or hide the update check. It now has a live button. *S*

**Tier 1 — money, and they are one afternoon together.**
6. **S18** — read `PaymentResultDto.status` in `payWithBalance`; drop the unconditional `finish()`.
7. **S20** — one `isPaying` flag; `progress_buy` already exists and is already bound.
8. **S19** — pass `selectedSub.remnawaveUuid` through the extra that is already declared and read.
9. **S23** — make `resolveUsedDevices` use the `SubInfoDto` it is handed.
10. **S21 / S24** — poll for the `orderId` that is already returned and thrown away; clear
    `pendingPayment` in a `finally`.

**Tier 2 — the connect flow, as one contract (the desktop half is already done and is the model).**
11. **S28 / S29 / S30** — a `connectInProgress` guard on the control, an idle-the-UI path in the
    "no server" guard, and an `else` on the permission callback. `SingleClick.kt` already exists.
12. **S42** — gate Home and the nav bar on the unfiltered stored count; `prepareMenu:1025` shows the
    pattern six hundred lines away.
13. **S41** — restore an editor for `PREF_CONFIRM_REMOVE` or delete its five readers.

**Tier 3 — the ports nobody started.**
14. **S38** — auto-fallback on desktop. Ordered, specified, zero lines written.
15. **S34 / S35 / S36 / S37** — W2's timeout, W4, W5, W6. Four `S`-sized orders, three with
    ratified copy already written.
16. **S39** — wire `ProviderSettingsPage` and add the four missing rows.
17. **S33** — W1, throughput test. The only `M` in the port list.

**Tier 4 — desktop hygiene:** S48, S49, S50, S51, S52, S53, S55.

---

## 7. What this sweep did not check

Stated so the next pass does not mistake silence for clearance.

- **`hunt-transient-ui` P13, P14, P15, P17, P18** — read, not re-verified against source.
- **`audit-android-data` #11, #22, #23, #24, #25, #26** — the medium-low and low tail.
- **`audit-desktop-core` M8, M10, M12, M14, M15, M16, M18 and L1, L3, L5, L7-L11** — sampled, not
  exhaustively re-read.
- **`audit-android-ui` section B (B1-B10) and `audit-android-copy` sections A/B/C/E** — the
  design-law and copy conformance bodies. These belong to the five in-flight screen rebuilds and to
  the copy register, whose enforcement has not started. Spot-checked facts confirm nothing has moved
  yet: `res/layout/fragment_home.xml` does not exist, `bg_home_gradient.xml` and
  `bg_connect_glow.xml` are still on disk and still applied, `activity_main.xml` still carries
  `android:text="↑"` / `"↓"` at `:87` / `:129` and 63 raw `dp` literals, and there is still no
  `res/values-en/`.
- **`audit2026/pc-home.md`, `pc-settings.md`, `pc-servers-account.md` and the three remaining
  `audit2026/android-*.md`** — read for non-design defects only. Their design findings are the
  in-flight screen waves' scope.
- **Anything requiring a device or a run.** `bugs-android-confirmed` §3 lists five claims whose
  user-visible half needs an emulator (D03, D05, D13, D25, D27); three of those five are now fixed
  in code regardless.
