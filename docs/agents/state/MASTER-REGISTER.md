# MASTER REGISTER — Departament VPN, both clients

**Written 2026-07-26, late.** Branch `claude/app-audit-agents-hyyftk`.
Sources: `register-android.md`, `register-pc.md`, and — outranking both — `OWNER-FEEDBACK-2026-07-27.md`.
Android code read at `/home/user/dp/V2rayNG/app/src/main/`; desktop at `/home/user/v2rayN/v2rayN/`.

**No source file was edited, no build was run, no git command was issued.**

---

## How to read this

One id per defect, `M-nn`, **merged across platforms**: where the same defect exists on both, it is
one row with both addresses, not two. Where a fix already exists on one platform, the row says so and
points at it — there are **six** of those and they are the cheapest work in the document.

Grouped by what the defect costs the user:

| § | Group | Meaning |
|---|---|---|
| **1** | Blocks a release | Cannot ship. The product does not do its job, or ships as someone else's app |
| **2** | Loses or corrupts data or money | Charges twice, deletes servers, leaks the subscription token |
| **3** | Visibly wrong | Wrong words, wrong face, clipped text, a control that does nothing, a screen missing what it had |
| **4** | Unfinished craft | States not designed, hover, motion, list performance, hygiene |

Every row was re-read against today's source. **An item is here only because I opened the file and
the defect was still there.** §5 lists what closed during this session and §6 what verification
refuted — both exist so nobody spends a wave re-reporting or refiling.

**Authority.** The owner's file wins over every spec, and over this file. Where a spec-derived row
contradicted him, the row is his way round. His own items are marked **OWNER** with his reference.

**Two waves are editing source right now.** `HomeFragment.kt`, `MainActivity.kt`, `fragment_home.xml`,
`layout_home_*.xml`, `strings_home.xml`, `LoginActivity.kt`, `AuthViewModel.kt`, `strings_auth.xml`,
`layout_auth_*.xml`. Rows whose only home is one of those files carry **⏳ IN FLIGHT** — re-check them
at the gate, do not start on them.

### Three corrections this pass made to the two registers

1. **A-05 (the six-item add menu) is CLOSED.** `res/menu/menu_main.xml` is now exactly QR + clipboard;
   the other four moved to `MainActivity.showAdvancedAddMethods()` (`:454`). Owner C1 is satisfied.
   Moved to §5.
2. **A-14 changed shape.** `group_server_list` is no longer declared-and-hidden — it is **deleted**.
   The dead-menu defect is closed; what survives is the feature loss, re-filed as **M-51**.
3. **A-30 is worse than filed, and is now a §2 item (M-19).** The register called it latent because
   «Удалить недоступные» had no entry point. It has an **automatic** one:
   `MainViewModel.onTestsFinished` (`viewmodel/MainViewModel.kt:809`) calls `removeInvalidServer()`
   whenever `PREF_AUTO_REMOVE_INVALID_AFTER_TEST` is set, and that preference is user-reachable at
   `res/xml/pref_settings.xml:280`. This deletes servers today.

### One item neither register carried

**M-07** — the updater. `register-pc` had the desktop half and called it latent. **The Android half is
live**: «Проверить обновления» is wired at `ui/SettingsTabFragment.kt:125` and `ui/AboutActivity.kt:91`,
and it queries `2dust/v2rayNG`'s releases and offers the user upstream's APK.

---

# §1 · Blocks a release

Nothing in this section is visible from the debug build the branch has been producing.

## 1.1 The product does not do its job

### M-01 · The desktop does not connect, and never says why · **PC** · OWNER H
> «на пк версии не подключается к впн, не знаю в чем причина»

**Mechanism — the certain half is not the gate, it is the silence.** The notice for this exact case is
built and rendered by nothing:

- string: `v2rayN.Desktop/Common/L.Home.cs:55` `Home_TunUnavailable` = «Режим «весь трафик» недоступен
  без прав администратора»; `:56` `Home_RestartElevated`.
- state: `ServiceLib/ViewModels/StatusBarViewModel.cs:127` `TunRequestedButUnavailable` (recomputed
  `:563`); escape hatch `:48` `RequestTunElevationCmd` (built `:203`).
- consumers: **zero.** Re-verified today — a grep for all four symbols over `v2rayN.Desktop/`
  returns the declaration at `L.Home.cs:55` and one comment at `Views/StatusBarView.axaml:22`.
  Nothing else. `Views/ConnectHeroView.axaml` has no match for `Tun` in 839 lines.

`StatusBarView.axaml:16-25` records the removal and a promise that was never kept: the banner was
deleted from three hosts and re-added to none.

**The gate itself is real but cannot fire on shipping Windows.** `AllowEnableTun()`
(`StatusBarViewModel.cs:566-583`) → `Utils.IsAdministrator()` → `TunUnavailable` →
`ConfigItems.cs:203 EnableTunEffective` → `CoreConfigContextBuilder.cs:45,204`. With it false,
`BuildPreSocksIfNeeded` (`:186-215`) never synthesises the sing-box pre-service that owns the tun
adapter: a core with a SOCKS inbound and no tunnel, reporting itself running. But
`v2rayN.Desktop/app.manifest:10` requests `requireAdministrator` and the csproj references it, so the
process is elevated or does not start. **Where the gate does close unconditionally today: Linux and
macOS** — `AllowEnableTun()` returns `LinuxSudoPwd.IsNotEmpty()` (`:572-579`), necessarily empty when
the constructor runs.

**Fix.** Render the banner **once**, in `ConnectHeroView`, bound to `TunRequestedButUnavailable`, with
`RequestTunElevationCmd` as its action. Do not re-add copies to `HomeView`/`CompactHomeView` — one
banner in the shared hero was the correct call.
**Then, and only then**, chase the Windows cause: sing-box pre-service failing to start or to create
the wintun device, then the port contract between it and the Xray SOCKS inbound
(`CoreConfigHandler.MergeAppInbounds:165`). Do not spend the day on elevation.
**Do not ship a build that reports itself connected while carrying nothing.**

### M-02 · The desktop measures a decoy: it takes the first proxy outbound where Android walks the routing · **PC** · OWNER A3 + I (raised twice) · **fix exists on Android**

`ServiceLib/Handler/Fmt/XrayJsonTemplateFmt.cs:122-129` — verified unchanged today:

```csharp
return config.outbounds.FirstOrDefault(o => o.protocol.IsNotEmpty() && _proxyProtocols.Contains(o.protocol));
```

Its own doc comment at `:118-120` still claims it *"mirrors Android's `getProxyOutbound()`"*. It does
not. Android is `V2rayNG/app/src/main/java/com/v2ray/ang/dto/V2rayConfig.kt:520-580`:
`resolveRoutedOutbound() ?: firstProxyOutbound()` — walks `routing.rules` in core order, skips rules
narrowed to a special case, skips a rule sending everything to freedom/blackhole, resolves balancer
members by tag prefix with a `fallbackTag`. Tests exist:
`app/src/test/java/com/v2ray/ang/dto/ProxyOutboundResolutionTest.kt`.

**Five desktop call sites read the wrong outbound:** `Services/SpeedtestService.cs:110` (pings a host
that is not the server), `Handler/CoreConfigHandler.cs:440` (batch speedtest measures the decoy),
`ViewModels/ProfilesViewModel.cs:459` (the row's protocol chip lies),
`Manager/CoreManager.cs:421` and `:549` (`_runningProxyTag` and the statistics key).

**Fix.** Port `resolveRoutedOutbound()` into `XrayJsonTemplateFmt` and port the test file. The desktop
`V2rayConfig` DTO already carries `routing.rules` and `routing.balancers` — a resolution change, not a
model change.

## 1.2 It ships as somebody else's application

### M-03 · The release APK is signed with the debug key · **Android**
`V2rayNG/app/build.gradle.kts:70` — `signingConfig = signingConfigs.getByName("debug")`. The debug key
is regenerated per machine and per CI run, so two "releases" cannot upgrade each other and no store
will take it.

### M-04 · All five playstore APKs get the same versionCode · **Android**
`build.gradle.kts:127-141` — every ABI maps to `4`, resolving to `4000731`. Play refuses a multi-APK
upload with colliding codes. The fdroid branch immediately above (`:109-121`) already has distinct
values; copy the shape.

### M-05 · Every launcher shortcut is dead, in both flavours · **Android**
`applicationId` is `com.departamentvpn.app` (`build.gradle.kts:13`), while
`res/xml/shortcuts.xml:14,28,42,56` still say `android:targetPackage="com.v2ray.ang"` and
`src/fdroid/res/xml/shortcuts.xml:15,30,45,60` say `com.v2ray.ang.fdroid`. Neither package exists.
`ScSwitchActivity`, `ScScannerActivity`, `ScStartActivity`, `ScStopActivity` have **zero** code entry
points, so this XML is their only door — all four long-press launcher actions do nothing in every
build. **Fix:** `${applicationId}` in both files.

### M-06 · The F-Droid build calls itself «v2rayNG (F-Droid)» · **Android**
`src/fdroid/res/values/strings.xml` overrides `app_name`. Delete the file; `src/main`'s `departament`
applies.

### M-07 · «Проверить обновления» offers the user a different project's application · **BOTH** · *not in either register*

**Android — live, reachable, two doors.** `AppConfig.kt:144-145`:

```kotlin
const val APP_URL     = "$GITHUB_URL/2dust/v2rayNG"
const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"
```

`handler/UpdateCheckerManager.kt:19-21` queries it; `ui/CheckUpdateActivity.kt:88` compares the result
against **this** app's version and `:115-129` presents «Скачать» pointing at upstream's APK asset. It
is reached from `ui/SettingsTabFragment.kt:125` and `ui/AboutActivity.kt:91`. Because the two apps have
different `applicationId`s **and** different signing keys, following that prompt does not upgrade
anything — it side-installs upstream v2rayNG alongside departament. `AboutActivity.kt:97` sends «О
приложении» to upstream's GitHub and `:109` sends the Telegram row to `t.me/github_2dust`
(`AppConfig.kt:150`).

**Desktop — same defect, currently latent.** `ServiceLib/Global.cs:674` resolves the asset from
`2dust/v2rayN` and `Services/UpdateService.cs:315-336` hands the download to `AmazTool`, which unzips
it over the install directory. Latent only because `CheckUpdateView` is unreachable (M-53) — restoring
that view without fixing this ships a self-overwriting updater pointed at another project.

**Fix.** Point both at this project's releases, or remove the feature and the rows. Whichever is
chosen, do it on both platforms in the same edit, and take the About/Telegram links with it.

### M-15 · The desktop reports itself as **v2rayN 7.23.4** in the About row · **PC**
`v2rayN/Directory.Build.props:4` — `<Version>7.23.4</Version>`, verified today. Upstream's version
number on a fork with its own product name.

## 1.3 The pipeline

### M-08 · The `.deb` and `.rpm` install a package that cannot launch · **PC**
The scripts look for a binary named `v2rayN`; the fork's `AssemblyName` is **`departament`**
(`v2rayN.Desktop/v2rayN.Desktop.csproj:8`, verified). So nothing is ever made executable and the
launcher exits with «no executable found». `package-debian.sh:515-541,600-677`;
`package-rhel.sh:500-571`; `ServiceLib/Common/Utils.cs:1271-1296`.
The macOS `.app` is the same class of defect: `package-osx.sh:15-17,37-47` declares a
`CFBundleExecutable` that is not in the bundle, so macOS refuses to launch it. No codesign, no
notarisation.

### M-09 · `package-debian.sh` can check out an upstream tag over this branch and ship upstream v2rayN · **PC**
`package-debian.sh:157-215` — `choose_channel` only prompts on a TTY, so in CI it falls through to
`latest` from **`2dust/v2rayN`** (`:184`, `:190`, verified). Locally it destroys uncommitted work with
no confirmation. Five sibling scripts share the shape.

### M-10 · The only workflow gating pull requests cannot compile the solution · **PC**
`.github/workflows/test.yml:25` installs `dotnet-version: '8.0.x'` and `:29` runs `dotnet test` against
a `net10.0` solution → `NETSDK1045`. Verified.

### M-11 · Four upstream release workflows fire on merge and publish `v2rayN-*` artifacts · **PC**
Twelve workflow files are present; exactly one — `departament-branch-build.yml` — is ours. The rest
(`build*.yml`, `package-zip.yml`, `upload-sign.yml`, `winget-publish.yml` → `2dust.v2rayN`) are
upstream's, and one of them builds the **WPF** project, which is not this app.

### M-12 · In a Release build, every framework error the user sees collapses to a resource key · **PC**
`v2rayN/Directory.Build.props:28` `<UseSystemResourceKeys>true</UseSystemResourceKeys>`, verified.
The user gets «Не удалось зарегистрировать: UnauthorizedAccess_IODenied_Path», and so does the log.
Honoured at runtime whether or not the app is trimmed.

### M-13 · Minification is off, and there are no keep rules to turn it on with · **Android**
`build.gradle.kts:63` `isMinifyEnabled = false`; `app/proguard-rules.pro` is 20 lines of commented AGP
boilerplate and **zero rules**. The app reflects across Gson DTOs (the Xray config's field names *are*
the wire format), the gomobile/libv2ray JNI surface, hev-socks5-tunnel's JNI entry points, and
WorkManager. MMKV and WorkManager ship consumer rules; the other three do not.
`release-android.md` §2.3-2.6 has the rules written out. **Apply the rules first, flip the flag
second**, and build the release variant once — no gate on this branch ever has.

### M-14 · `QUERY_ALL_PACKAGES` · **Android**
`AndroidManifest.xml:28`. A Play review blocker unless the per-app proxy picker is declared as the
qualifying use, or replaced with a `<queries>` element.

---

# §2 · Loses or corrupts the user's data or money

## 2.1 Money

### M-16 · Nothing prevents a double charge · **Android** · **the desktop already has this fix**
`ui/BuyTariffActivity.kt:113` binds `btnPay` straight to `onPayClicked()`. Neither `onPayClicked`
(`:457`) nor `onMethodPicked` (`:482`) sets an in-flight flag or disables the button, and neither does
the top-up path at `ui/AccountFragment.kt:628-644`. The indicator that would have shown it is declared
and never shown: `progressBuy` (`:94`) is set `GONE` at `:176,187,197,218` and `VISIBLE` at **no line
in `app/src/main`** — verified again today.

On a slow connection the screen does not change, the user taps «Оплатить» again, and pays twice.

**The desktop is correct and is the model.** `v2rayN.Desktop/ViewModels/BuyViewModel.cs:81`
`[Reactive] public bool IsPaying`, guarded at `:441` (`if (… || IsPaying) return;`), set `:447`,
cleared on both outcomes `:464`/`:476`; the view disables the button and swaps in a spinner at
`Views/BuyView.axaml:558,574,581-582`. **Port that shape.**
**Must not break:** `awaitingPaymentError` (`BuyTariffActivity.kt:484`) must stay armed across the
request, or a failure goes back to being silent.

### M-17 · A balance payment reports success without reading the status the backend returned · **Android**
`viewmodel/AccountViewModel.kt:344-346`:

```kotlin
fun payWithBalance(req: PaymentRequestDto, onDone: () -> Unit = {}) = viewModelScope.launch {
    repo.payWithBalance(req).onSuccess { onDone() }.onFailure { report(it) }
}
```

`PaymentResultDto.status` is never inspected. Both callers treat a 200 as a purchase, and
`BuyTariffActivity.onMethodPicked` (`:490-505`) calls `finish()` on it — so a `200 {status:"failed"}`
closes the buy screen and tells the user they own a subscription they did not buy.

### M-31 · A failed payment leaves a live «Итого» and «Оплатить» for a selection that is not on screen · **Android**
`ui/BuyTariffActivity.kt:206-245` (`renderTariffs`) rebuilds `tariffsContainer` and clears `checkMarks`
/ `optionRows`, and never touches `selectedTariff`, `selectedOption`, `extraDevices` or
`checkoutCard.visibility`. The rebuild fires on any `error` transition (`:135-137`), and `clearError()`
fires it a second time. `selectTariff` (`:342-344`) returns early when the tariff is already selected,
so re-tapping the same card cannot recover the paint. Every card reads neutral; the pay button is live.

### M-32 · Declined, cancelled and timed-out checkouts end in silence, and the poll restarts forever · **Android**
`ui/AccountFragment.kt:833-845` and `ui/BuyTariffActivity.kt:586-593` run a fixed `repeat(6)` /
`repeat(5)` × 8 s poll that **never inspects a payment status**, then hides the hint with no verdict, no
copy and no action. The `orderId` from `PaymentInitDto` is captured and discarded.
Compounding: `AccountFragment.kt:826` re-arms the poll in `onResume`, while the job lives on
`viewLifecycleOwner.lifecycleScope` and is cancelled by the tab switch **before** `pendingPayment =
false` at `:842` — so the 48-second window restarts indefinitely every time the user returns to the tab.

## 2.2 Servers and subscriptions

### M-18 · Signing out leaves the servers on screen, and they stay selectable with no session · **Android** · OWNER A1
`AccountSession.wipe()` **does** remove the account's subscriptions and servers from MMKV
(`auth/AccountSession.kt:82-86` → `SubscriptionSyncManager.removeAllManaged()` →
`MmkvManager.removeSubscription` → `removeServerViaSubid`, which also clears `KEY_SELECTED_SERVER` at
`handler/MmkvManager.kt:234-236`). **Nothing then re-reads the store.**
`MainViewModel.serversCache` is in-memory, rebuilt only by `reloadServerList()`
(`viewmodel/MainViewModel.kt:201`), and the logged-out transition calls it from nowhere — re-verified
today at `ui/HomeFragment.kt:1341-1358` (`applyAccountState`, the else branch empties `accountSubs`,
re-renders and refreshes the nav gates, and that is all) and `ui/AccountFragment.kt:282-300`
(`onSessionCleared`).

So Главная's list, its «Серверы» count and `MainActivity.updateBottomNavVisibility` (which gates the
whole bottom bar on `serversCache.isNotEmpty()`) all keep reading rows that no longer exist on disk.
Tapping one selects a guid `decodeServerConfig` answers null for.

**Fix.** Call `mainViewModel.reloadServerList()` on **every** account-state change, not only on logout —
one call in `applyAccountState` covers both the explicit sign-out and the 401 route.
**Must not break:** hand-added servers are not the account's and must survive; reloading deletes nothing.

### M-19 · A latency check can delete servers that were never tested · **Android** · **upgraded from latent**
`ui/HomeFragment.kt:980-983` (`markAllServersTesting`) persists the UI sentinel **`-2L`** into
`serverAffStorage` for every row in the cache, including rows the tests skip (PolicyGroup and balancer
entries, unparseable CUSTOM profiles). It is MMKV, so the spinner also survives a restart.

`MmkvManager.removeInvalidServer` (`handler/MmkvManager.kt:323-343`) deletes anything with
`testDelayMillis < 0L` — which includes `-2`.

**And it runs by itself.** `MainViewModel.onTestsFinished` (`viewmodel/MainViewModel.kt:806-812`) calls
`removeInvalidServer()` whenever `PREF_AUTO_REMOVE_INVALID_AFTER_TEST` is set, and that preference is
user-reachable at `res/xml/pref_settings.xml:280` («Автоудаление нерабочих серверов»). The ViewModel's
own guard (`:680-692`) only skips guids in `measuringGuids`; its doc comment at `:674-676` asserts
*"Nothing writes a negative 'in progress' value any more"* — **that is false as of today's source**,
`HomeFragment.kt:981` writes one.

**Fix.** Hold "testing" in memory (a ViewModel set), never in MMKV; and make `removeInvalidServer` test
`testDelayMillis < 0 && testDelayMillis != TESTING`. Correct the doc comment.
**Must not break:** the adapter's spinner rendering keyed on `-2L`, and `clearAllTestDelayResults`.
**Do this before anything restores a manual «Удалить недоступные» (M-51).**

### M-28 · The Devices page is never told which subscription it is for · **Android** · **the desktop already has this fix**
`ui/DeviceManagementActivity.kt:235` declares `EXTRA_REMNAWAVE_UUID` and `:58` reads it. The only
launcher is `ui/AccountFragment.kt:197` — `openSubScreen(DeviceManagementActivity::class.java)`, which
passes no extra (verified). On a multi-subscription account the page shows the root subscription's
devices and **unlinks against the root uuid** whatever card the user was on. This is the exact
per-item scoping rule this repo's own `CLAUDE.md` warns about.
**Desktop fix to copy:** `Views/DevicesView.axaml.cs:31` passes `AccountViewModel.Shared?.DevicesScopeUuid`.

### M-29 · Every subscription card shows the ROOT subscription's device count · **Android**
`ui/SubscriptionPagerAdapter.kt:24` types the hook `(SubInfoDto) -> Int` and `:88` calls it per card,
but `ui/AccountFragment.kt:174` supplies `resolveUsedDevices = { viewModel.deviceCount.value ?: 0 }` —
a lambda that ignores the `SubInfoDto` it is handed (verified). The fragment also fetches devices for
`list.firstOrNull()` only. Same root cause as M-28; fix them together.

### M-30 · Rotating with the payment-method sheet open crashes on pick · **Android**
`ui/PaymentMethodSheet.kt:155` parks the picker lambda in a process-static
`ConcurrentHashMap<Long, (String)->Unit>`; `onDestroy` (`:132-140`) deliberately keeps the entry when
`isChangingConfigurations` and `onCreate` (`:52-55`) re-binds it. Both callers hand it a lambda
capturing the **old** host — `ui/AccountFragment.kt:628-644` captures `viewModel` (a `by viewModels()`
that dies with the fragment), `toastSuccess`/`toastError` (→ `requireContext()`) and `::openCheckout`;
`ui/BuyTariffActivity.kt:474-479` captures the Activity. After rotation `requireContext()` throws
`IllegalStateException`.
**Fix:** resolve the host from the `FragmentManager` at pick time, or use `setFragmentResultListener`.
**Must not break:** process death must keep degrading to "dismiss without firing" (`:31-32`), and
`onDestroy` must keep dropping the entry on a real dismissal so the map cannot grow.

### M-26 · A crash or power-loss mid-write silently signs the desktop user out · **PC**
`v2rayN.Desktop/Account/AuthTokenStore.cs:194` — `File.WriteAllBytes` on the auth blob, no temp-file +
move; `Load` treats any parse failure as "start fresh". `ConfigHandler.SaveConfig:204-215` already has
the correct atomic pattern next door.

## 2.3 The subscription token leaves the device

### M-20 · The backup zip carries the subscription URLs — and the account token in them — in plaintext · **Android**
`ui/BackupActivity.kt:162` is `MMKV.backupAllToDirectory(backupDir)` → `ZipUtil.zipFromFolder` → a plain
zip the user can share (`backup_action_share`) or push to WebDAV. Verified today; a grep for `locked`
over `BackupActivity.kt`, `handler/WebDavManager.kt`, `util/ZipUtil.kt` returns **0**.

`hidden-templates-design.md` §3.4 + §5 step 10 required backup/export to skip locked profiles and subs.
The locked *template body* is encrypted (`template/TemplateManager.wrapRawForStorage`), but the thing
the feature exists to hide — the **subscription URL, which carries the account token** — sits in the
ordinary config MMKV in plaintext and rides along, defeating `ui/SubEditActivity.kt:93-97`'s careful
redaction. Restoring that zip on a *different* device also yields locked profiles whose raw is
`dpt-enc:`-prefixed and undecryptable (the Keystore key does not travel), so `unwrapStoredRaw` returns
`null` and the profile silently cannot connect. Nothing tells the user.

### M-21 · `allowBackup="true"` with no rules, over a Keystore-sealed session store · **Android**
`AndroidManifest.xml:45`, with **no** `android:dataExtractionRules`, **no** `android:fullBackupContent`,
and no `res/xml/backup_rules.xml` / `data_extraction_rules.xml`. At `targetSdk = 37` both Auto Backup
and device-to-device transfer copy the whole `files/` directory — every MMKV store, including the
subscription URLs (second channel of M-20).

It also breaks the session: `departament_auth` is encrypted with a hardware-bound AndroidKeyStore key
which does not restore, so on the new device the store resolves to `CryptKeyState.Unsealable` and the
user's servers come back but their session does not, unexplained. Worse, MMKV files are memory-mapped
with a companion `.crc`; a backup catching the two out of step yields a CRC mismatch that MMKV resolves
by discarding the file.
**Fix:** add both rule files, wire them on `<application>`, exclude `departament_auth` and
`departament_keyholder` from cloud backup **and** device transfer. Decide deliberately whether the
server list should be backed up at all — for a censorship-circumvention client, "my subscription URLs
are in Google's cloud" is a product decision, not a default.

### M-22 · TV pairing pushes the subscription URL over cleartext HTTP · **Android**
`tv/TvSendActivity.kt:176` — verified today:

```kotlin
.url("http://${info.ip}:${info.port}${TvPairingProtocol.PAIR_PATH}")
```

and the body is `TvPairingProtocol.buildRequestJson(url = sub.subscription.url, …)` — the subscription
URL itself, in the clear, to any LAN sniffer or rogue AP. `grep "SSL|Cipher|encrypt"` over `tv/` → 0.
`smart-tv-transfer-design.md` §3.5 wanted HTTPS with a QR-pinned self-signed fingerprint or a
token-derived AEAD and calls plain HTTP *"acceptable only as v1 MVP"*. Everything else §3.6 mandated
**is** implemented — single-use token, TTL close (`TvHttpReceiver.kt:99`), constant-time compare,
bad-attempt lockout — so this is one transport swap, not a rebuild.

### M-23 · User-installed CA certificates are trusted app-wide, and cleartext is permitted · **Android**
`res/xml/network_security_config.xml` is byte-for-byte upstream — verified:

```xml
<base-config cleartextTrafficPermitted="true">
  <trust-anchors>
    <certificates src="system" />
    <certificates src="user" tools:ignore="AcceptsUserCertificates" />
  </trust-anchors>
</base-config>
```

For a product whose stated threat model is a national censor, a user-installable MITM anchor over the
account API and the subscription fetch is the wrong default. (M-22 is what `cleartextTrafficPermitted`
is currently covering for.)

### M-27 · Account API calls are routed through the app's own tunnel, with DNS pinned for the process lifetime · **PC**
`v2rayN.Desktop/Account/DepartamentApiClient.cs:24-33` — `new HttpClientHandler()` with no
`PooledConnectionLifetime` and `UseProxy` defaulting to the system proxy, which this app points at
`127.0.0.1:<socks>` in `ForcedChange`/`Pac` mode. When the tunnel is up but broken, sign-in and
subscription sync fail — precisely when the user needs them to work.

## 2.4 Doors anything can walk through

### M-24 · A web page can connect, disconnect, import a server and rewrite the routing rules · **Android**
`AndroidManifest.xml:181-189` exports `UrlSchemeActivity` for `depv://` with `BROWSABLE`.
`ui/UrlSchemeActivity.kt:71-137` dispatches `connect|open`, `disconnect|close`, `toggle`,
`import/{base64}`, `add/{url}`, `routing/add/{base64}` and `routing/onadd/{base64}` — **with no
confirmation on any of them**. The import path passes `append = false, subid = ""`, so the ungrouped
bucket is **replaced**, not appended to (`ui/ScScannerActivity.kt:22` has the same `false`). A link in a
browser, a chat message or a QR code can stop the user's VPN, install and select an attacker's server,
or replace the routing rulesets and restart the tunnel onto them.
`11-app-structure.md` §7.2 marks all four **"confirm sheet, mandatory"**. Every mutating destination
needs a sheet that names what it will do.

### M-25 · Any installed app can toggle the VPN · **Android**
`AndroidManifest.xml:268-279` — `WidgetProvider` is `exported="true"` with an intent filter for
`${applicationId}.action.widget.click` and **no** `android:permission`.
`receiver/WidgetProvider.kt:67-74` acts on it via `stopVService` / `startVServiceFromToggle`. Any app
that knows the package name can broadcast it. Add a signature-level permission, or route the widget
click through a `PendingIntent` only the widget host holds.

---

# §3 · Visibly wrong

## 3.1 His words — copy · OWNER B

### M-33 · «провайдер» must be «подписка», everywhere, both platforms · OWNER B1
He overruled the terminology lock in `00-rules.md` 9.3 and every register row derived from it. His
product, his word. `strings_home.xml` was converted on Android; nothing else was.

**Android** — 6 strings, all verified present today:

| Key | File:line | Ships |
|---|---|---|
| `ps_title` | `res/values/strings_provider.xml:5` | «Настройки провайдеров» — the screen's own title |
| `settings_provider` | `res/values/strings_settings_hub.xml:8` | «Настройки провайдеров» — the Настройки row |
| `providers_count` | `res/values/strings.xml:11` + `values-ru/strings.xml:10` | «провайдеров: %d» |
| `menu_actions_ping_empty` | `res/values/strings_menu_actions.xml:37` | «…Добавьте провайдера или сервер.» |
| `menu_actions_del_all_body` | `res/values/strings_menu_actions.xml:60` | «Серверы провайдеров вернутся…» |
| `subs_ed_user_agent_hint` | `res/values/strings_editors.xml:162` + `values-ru/` | «…из настроек провайдеров» |

**Desktop** — 12 strings: `Common_AddSubscription` (`Common/L.Common.cs:28`), `Common_UpdateSubscription`
(`:31`), the plural set (`:77`), `Home_NoSubsHint` (`L.Home.cs:34`), `Onboarding_Title` (`:40`),
`Servers_EmptyHint` (`L.Servers.cs:24`), `Sub_Delete` (`:53`), `Sub_DeleteConfirm` (`:54`),
`Settings_SubAutoEmptyHint` (`L.Settings.cs:80`), `Settings_Providers` (`:82`), `Provider_Title` (`:175`),
`Backup_CreateHint` (`:201`).
**Leave `Dns_Provider` (`L.Settings.cs:151`)** — a different noun.
Delete the comment at `L.Common.cs:26-27` that justifies the split; it is the thing he overruled.

### M-34 · «departament» is always lowercase · OWNER B2
- **Android** — `res/values/strings_account.xml:34`: «Купите тариф, чтобы подключаться к серверам
  **D**epartament.» (verbatim his example), and `auth/SubscriptionSyncManager.kt:60`, where the fallback
  subscription remark is the literal `"Departament VPN"` — the string the subscription card and the
  header draw when the backend sends no display name.
- **Desktop** — `Common/L.Account.cs:33` and the English at `:34`.

Everything else on both platforms already reads lowercase, so this is three edits, not a sweep.

### M-35 · The mode row must read «TUN» / «Proxy» / «TUN + Proxy», in that order · OWNER B3
- **Android** — `res/values/strings_home_shell.xml:7-9`, verified: «VPN-туннель», «Прокси»,
  «VPN + прокси». The picker order in `ui/SettingsTabFragment.kt:202-237` is already TUN / Proxy /
  VPN+Proxy, so only the three values change; the same keys paint the row value at `:143-149`.
- **Desktop** — `Views/SettingsView.axaml:347-363`: «VPN» (a bare literal at `:353`),
  `Settings_ModeProxy` = «Прокси» (`L.Settings.cs:36`), `Settings_ModeBoth` = «Вместе» (`:37`). The
  third label does not say what it does, which is why `Settings_ModeBothHint` (`:39`) has to explain it.

**Note on owner C2** (the desktop segment "has two options"): it has **three** —
`SegModeVpn`/`SegModeProxy`/`SegModeBoth` in a `UniformGrid Columns="3"`, backed by
`ViewModels/SettingsViewModel.cs:40-42`. The count was never the defect; the names are. C2 is closed by
this row.

## 3.2 «шрифт какой-то толстый», «кривой шрифт местами», «у серверов везде кривой текст» · OWNER D2 + F4

The two platforms have **different** font defects with the same symptom. Do not apply one fix to both.

### M-36 · Android: twelve sub-screens draw their Russian title in a face with no Cyrillic
**The highest value-per-byte item in the whole register: one attribute.**
`res/values/themes.xml:302` already binds `toolbarStyle` to `Widget.Departament.Toolbar` — the fix —
and `res/layout/activity_base.xml:19` overrides it inline. Verified today:

```xml
app:titleTextAppearance="@style/ToolbarBrandTitle"
```

`ToolbarBrandTitle` (`styles.xml:281-282`) is `@font/space_grotesk`, whose vendored binary carries
**0** codepoints in U+0400-U+04FF (measured; Golos carries 170 each). Every screen inflating this host
through `setContentViewWithToolbar` puts a Russian title in it: `BuyTariffActivity`, `ServerActivity`,
`ProviderSettingsActivity`, `SettingsActivity`, `LocalProxyActivity`, `DeviceManagementActivity`,
`TaskerActivity`, `ScannerActivity`, `PaymentHistoryActivity`, `tv/TvSendActivity`, `tv/TvReceiveActivity`,
plus `ui/BaseActivity.kt`. «Настройки провайдеров», «Купить подписку», «История платежей», «Устройства»
and «Дополнительно» are drawn in an undeclared OS fallback today. The style's own comment
(`styles.xml:276-279`) says so.
**Fix:** delete the inline attribute. Three more deltas worth taking in the same edit — `:6`
`fitsSystemWindows="true"` fights the one inset strategy, `:11` `layout_height="?attr/actionBarSize"`
is a fixed height on a text-bearing bar (clips at font scale 200%), `:28`
`app:indicatorColor="@color/color_fab_active"` is a raw colour where a `?attr` belongs.

### M-37 · Desktop: the brand face is a variable font pinned to Light 300, so every Bold role is fake-bold · OWNER D2
`Assets/GlobalStyles.axaml:52` and `Assets/GlobalResources.axaml:301`/`:315` all resolve `Font.Brand`
/ `Font.Grotesk` / `Font.Numeric` to one file, `Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk`. Parsed
from the binary: `fvar` = 1 axis `wght`, min 300 · **default 300** · max 700; `OS/2 usWeightClass`
**300**; name ID 1 «Space Grotesk Light».

Avalonia loads an embedded font through `SKTypeface.FromStream`, which instantiates the **default**
instance, and has no `fontVariationSettings` equivalent on a `FontFamily` URI. So the family contains
exactly one face, at 300:
- `TextBlock.Display` (34/Bold, `:337-344`) and `TextBlock.Wordmark` (20/Bold, `:429-436`) request 700
  against a 300 master → Skia applies **synthetic emboldening**. That smeared over-thick look is his
  «толстый».
- `TextBlock.Chip` (11/Medium, `:395-402`) and `TextBlock.Numeric` (`:407-412`) request 500; weight
  simulation exists only for bold, so they render at **Light 300** — noticeably thinner than everything
  around them.

One face, two wrong directions, same screen. **Android does not have this problem and shows the fix**
(`V2rayNG/app/src/main/res/values/styles.xml:36` — a family XML pinning `wght` per entry).
**Fix:** ship **static** Space Grotesk masters (Regular 400 / Medium 500 / Bold 700) the way Golos Text
already is, and reference the folder, not the single variable file. A supply-the-file change.

### M-38 · Desktop: Space Grotesk has no Cyrillic, and two roles that keep it can still receive Russian
Verified against the `cmap`: `Я`, `б`, `и`, `ы` → glyph id **0**; all present in all three Golos files.
The blanket setter was already moved off the brand face (`GlobalStyles.axaml:304-316`, `Font.Ui`) and
`AccountView`'s three `Font.Grotesk` sites are gone. What remains is the roles that keep the brand face
**by design**: `TextBlock.Chip` (`:395`) and `TextBlock.Numeric` (`:407`) — a Russian chip label or unit
suffix mixes faces mid-line, at different metrics.

### M-39 · Desktop: four type roles declare a LineHeight below the face's natural height, so ascenders clip · OWNER F4
This is the metric he told us to fix rather than nudge. Natural line height from the shipped binaries
(`hhea` asc + desc + lineGap, unitsPerEm 1000): **Golos Text 1.200 em**, **Space Grotesk 1.276 em**.

| Role | Line | Face | Size | Needs | Declared | Verdict |
|---|---|---|---|---|---|---|
| `Display` | `:337-344` | Brand | 34 | 43.4 | 40 | **clips 3.4px** |
| `Wordmark` | `:429-436` | Brand | 20 | 25.5 | 24 | **clips 1.5px** |
| `Chip` | `:395-402` | Brand | 11 | 14.04 | 14 | **clips** |
| `Headline` | `:346-353` | Ui | 24 | 28.8 | 28 | **clips 0.8px** |
| `Title` | `:355-361` | Ui | 16 | 19.2 | 20 | fits — by 0.8px |

Avalonia distributes the difference as **half-leading**; when it is negative, half comes off the **top**
— «the tops of the capitals are cut off», exactly.
**And that is why the server names clip.** The server name is `Classes="Title"`
(`Views/ServerListView.axaml:260-263`), which fits by 0.8 px at 100% scale and by **nothing** the moment
one glyph falls back to a Windows system face at ~1.33 em. «Germany», «Latvia» and «LTE Белый интернет 1»
clip together, Latin and Cyrillic alike — metrics, not a glyph gap. **His read was right.**
**Fix:** set every `LineHeight` from the face's real metric (ceil to the 4-px grid at or above 1.30 em —
Display 46, Headline 32, Title 22, Wordmark 28, Chip 16), **after** M-37/M-38 land so there is one
metric per line to satisfy.

### M-40 · Android: ten synthetic-bold labels
`android:textStyle="bold"` on a face that ships a real 700 master makes the platform smear the 400
master — heavier, muddier, different from every other bold on screen. This is the Android half of
«шрифт какой-то толстый», and `styles.xml`'s own header bans it.
`layout/item_buy_option.xml:36`, `layout/activity_buy_tariff.xml:302`, `layout/activity_tv_receive.xml:47`,
`layout/activity_account.xml:77,126,277,354`, `layout/item_payment.xml:81`, `layout/toast_status.xml:20`,
`layout/activity_tv_send.xml:94`. **Four are on the Аккаунт tab, the screen he praised.**

### M-41 · Android: sixty-six raw `android:textSize` values across eleven layouts
A layout that sets its own size bypasses the role's weight, tracking, line height and face at once.
Distinct sizes in use: 11, 12, 13, 14, 15, 16, 18, 20, 22sp — five of which are not steps on the ramp.
Worst, both reachable from the Настройки tab: `layout/activity_local_proxy.xml` (37),
`layout/activity_provider_settings.xml` (15). Then `activity_tv_send.xml` (3),
`layout_subscription_meta_bar.xml` (2), `item_recycler_main.xml` (2), `activity_tv_receive.xml` (2), and
one each in `toast_status`, `layout_transport`, `layout_servers_header`, `item_buy_tariff`,
`activity_account`. ⏳ `res/layout/fragment_home.xml` is the only layout still setting
`android:fontFamily` (1 hit) — note it for the gate.

## 3.3 Every locale but Russian shows Russian

### M-42 · Picking «English» gives a half-Russian app · **Android**
`res/values/arrays.xml:140-151` offers Система / Русский / **English**. There is no `values-en`, so
English resolves to `values/` — and `values/` carries **847 Russian strings** across 20 files, because
every departament screen was written straight into the default bucket.

Re-measured today: `values/` holds **1255** unique string keys, `values-ru/` holds **880**. The ~372
keys with no `values-ru` entry are harmless on a Russian device and fatal everywhere else. The five
other shipped locales (`ar`, `bn`, `bqi-rIR`, `fa`, `vi`, `zh-rCN`, `zh-rTW`) carry ~352 keys each and
are in the same state.

**The good news, verified twice:** the reverse hazard is clean — **zero** keys where `values/` is
Russian and `values-ru/` shadows with leftover English, and `values/strings_home.xml` and
`values-ru/strings_home.xml` are identical key-for-key and body-for-body. Nobody has to untangle a
shadowing mess first.
**Fix — pick one and state it before writing code:** either make `values/` the Russian master and
remove the English option and the stale locale folders (smaller, matches the product), or move the 847
Russian strings into `values-ru/` and write English into `values/` (what the vendored locales assume).

### M-43 · Desktop: Russian literals that stay Russian in the English app
`Views/SettingsView.axaml:792` («Масштаб интерфейса») is a hardcoded literal, not a `loc:T` key; same
shape at `Views/StatusBarView.axaml:114,120`. Separately, every publish ships eight `ResUI` satellite
folders (fa, fr, hu, id, ru, zh-Hans, zh-Hant) into a directory called "departament" —
`Directory.Build.props`, `SatelliteResourceLanguages` unset.

### M-44 · Android: one hardcoded `contentDescription` in the tree
`res/layout/view_toolbar.xml:70` — `android:contentDescription="Назад"`. Every other content
description is a resource; this is the one string that never gets localised.

## 3.4 Screens missing what they used to have

### M-45 · Главная lost its content, its connect animation and the subscription pill · **Android** · OWNER A4 + E · ⏳ IN FLIGHT
> «все должно быть вернуто с пилюлей и инфой о подписке под кнопкой, чтобы при подтягивании подписки с
> акка писался ник подписки, в общем все как было раньше, не знаю почему ты это все убрал»

Under the connect object, exactly as before: the **pill** with the traffic figure; the subscription
**info block** — provider name with its emoji, the auto-update timestamp, the operator's notice, the
support and Telegram actions, and the refresh, pin and delete controls; and **when a subscription is
pulled from the account, its own name is what shows** — the nickname the account returns, not a generic
label.

The wave that owns Главная has landed the meta-bar carousel, the embedded server list,
`confirmDeleteSubscription`, `toggleHomePin`, the uptime clock and `markAllServersTesting` back into
`ui/HomeFragment.kt`. **Do not start work here.** Verify at the gate item by item against §E; the one
to check hardest is the naming rule, which is not a layout.

### M-46 · The Android login composition: buttons at the very bottom, headline stranded at the top · OWNER C3 · ⏳ IN FLIGHT · appears addressed
`res/layout/layout_auth_gate.xml:70-74` and `:281-285` now float the intro + actions between weighted
spacers (3 above, 4 below) inside `gate_scroll`, which is the fix. `LoginActivity.kt` was last written
at 22:17. Confirm at the gate.

### M-47 · The desktop sign-in is not redesigned · **PC** · OWNER C4
> «что это ваще за меню входа такое кривое и непеределанное» … «дизайн в целом хорош для входа, почему
> такого же 1 в 1 нет на пк?»

`Views/LoginView.axaml` (840 lines) is **not** an undesigned screen — sub-page toolbar (`:163`), method
block (`:197`), segment (`:239-245`), email + password with reveal (`:257-302`), inline error slots,
magic-link and forgot-password (`:411-420`), Telegram (`:456`), browser (`:480`), paste-code
(`:503-513`), a 6-cell 2FA block (`:553`), an awaiting state with its own animation (`:635-680`). What
is structurally wrong, verified:

- the segment is **mode** («Вход»/«Регистрация»), not **method**;
- `EmailBox:257`, `PasswordBox:274`, `ConfirmPasswordBox:320` have **no label element** — the
  `Watermark` does the label's job, so the field loses its name the moment you type;
- every error slot ships `IsVisible="False"` (`:264-268,330-334,579-583,619-624`) with no reserved
  space, so the form **jumps** when an error appears;
- the toolbar title is `Headline` 24 where sub-pages take `Title` 16/700 (same defect at
  `DevicesView.axaml:125`, `PaymentHistoryView.axaml:55`, `BuyView.axaml:254`);
- the alternative methods sit at the end of a long scroll (`AltMethodsBlock:431`) — the shape C3 describes.

**The instruction is «сделать 1 в 1 как на андроиде».** Rebuild against the Android sign-in; those five
are the minimum it must fix. *Open question, §7:* which screen was in his screenshot — `LoginView` or
the onboarding gate's sign-in row (`OnboardingView.axaml:161-190`). The five findings hold either way.

### M-48 · The desktop Аккаунт tab: the signed-**out** state is the bare one · **PC** · OWNER F2
> «вкладка аккаунт вообще хуёво выглядит не стилизованно, на андроиде в 100 раз лучше»

This cannot be about the signed-in tab: `Views/AccountView.axaml` is 1331 lines with an offline bar
(`:171-192`), a tightened profile header (`:196`), a subscription card with a traffic meter, a sub
selector, buy/devices/history rows and an inline sign-out confirm (`:1258-1273`).
The state that **is** «a bare card floating in an empty pane» is the signed-out gate, `:1276-1327`: a
`MaxWidth="320"` StackPanel, centred, holding a Headline, a Subtitle, one `Primary Tall` Telegram
button and one left-aligned `Tertiary` — inside a pane over 1160 px wide. It is the one account state
never designed.
Second, true of the signed-in tab too: a 720-capped single column centred in a 1160+ pane is the
Android layout transplanted, not a desktop composition. F3 asks for the phone one-to-one **in structure,
hierarchy and copy — natively expressed**. A narrow column in a wide window is the "stretched phone"
the desktop plan explicitly rejects.

### M-49 · The subscription editor is unreachable: two Activities with no door · **Android** · OWNER A2 (residue)
`ui/SubSettingActivity.kt` — the provider/subscription list editor — has **zero** `::class.java`
references anywhere in `java/` (verified today). `ui/SubEditActivity.kt` is referenced only from
`SubSettingActivity` itself (`:88`). Both are declared in `AndroidManifest.xml:110` and `:120`. The
Настройки row «Настройки провайдеров» goes to `ProviderSettingsActivity` (`SettingsTabFragment.kt:108`),
which is the *global* settings, not the per-subscription editor.
So on the phone there is no way to **rename** a subscription, **edit its URL**, set its **own
auto-update interval**, or set its **own User-Agent**. Deleting one is back on the Главная card (⏳ M-45),
which closes his A2 symptom; these four do not come with it.

### M-50 · Server search and the protocol filter are gone · **BOTH**
- **Android**: `MainViewModel.filterConfig()` (`viewmodel/MainViewModel.kt:744`) and
  `applyProtocolFilter()` (`:756`) have **no callers** (re-verified). The three layouts that carried the
  controls are orphaned: `res/layout/layout_servers_header.xml` (the search field),
  `res/layout/dialog_config_filter.xml`, `res/layout/layout_servers_empty.xml`. On an account with many
  servers the list is unnavigable.
- **Desktop**: `CompactServersView.axaml:88-113` holds **the only server search field in the product**,
  and the view is unreachable (M-53).

**Fix M-91 in the same edit** — restoring search without it empties Главная into the onboarding gate.

### M-51 · Six whole-list actions no longer exist · **Android** · *revised — the menu is now deleted, not hidden*
`res/menu/menu_main.xml` is now two items and `group_server_list` is **gone** (it went with the Серверы
destination). What remains is the feature loss, and the handlers are still there with nothing calling
them: `MainViewModel.exportAllServer()` (`:294`) — **zero callers**; `removeDuplicateServer()` (`:619`)
— **zero callers**; `filterConfig`/`applyProtocolFilter` — M-50; `locateSelectedServer` — no longer
exists at all. `removeInvalidServer()` and `sortByTestResults()` survive only through the automatic
post-test path (`onTestsFinished:806-812`), which is M-19.
With the server list back on Главная (⏳ M-45) these have a surface again.
**Decide per action, then either wire it or delete the handler — and fix M-19 before restoring any
manual «Удалить недоступные».**

### M-52 · Delete / edit / share / QR live only on a long-press · **Android**
`ui/MainRecyclerAdapter.kt:252-254` invokes `onItemLongClick` and `ui/HomeFragment.kt:733` wires it to
`mainHost.showServerActions(guid)` — so the sheet is reachable and this is no longer the dead end it
was. But long-press is a hidden affordance and it is the app's **only** route to those four actions.
His «удалять почему-то я тоже не могу» was a discoverability report as much as a functional one. Give
the row an explicit trailing control.

### M-53 · Fourteen of fifty desktop views are unreachable · **PC**
Zero constructors, zero XAML refs, no command raising their dialog: `ServersView`, `CompactServersView`,
`ProfilesView`, `ProviderSettingsPage`, `ThemeSettingView`, `BackupAndRestoreView`, `CheckUpdateView`,
`MsgView`, `ClashProxiesView`, `ClashConnectionsView`, `OptionSettingWindow` (1206 lines),
`GlobalHotkeySettingWindow`, `FullConfigTemplateWindow`, `SubSettingWindow`.
With them go core selection, log level, global hotkeys, the config template, check-for-updates and the
log viewer. Also: editing `ServersView.axaml` or `CompactServersView.axaml` ships **zero pixels** —
`BottomNavBar.axaml.cs:9-14` declares three tabs and `MainWindow.axaml.cs:175` wires three rail buttons.
Recorded so the next wave does not restyle a dead file.

## 3.5 Buttons · OWNER D1, D3, D4, F1

### M-54 · «при наведении моргают» — one cause, applied to one button out of fifty · **PC** · OWNER F1
The repo already diagnosed this, for exactly one control. `Views/MainWindow.axaml:125-133`, on
`Button.RailToggle`:

> «hover больше НЕ красит никакой фон — единственная реакция на ховер = ГЛИФ ТЕМНЕЕТ. Смена цвета глифа
> **не трогает границы элемента**… (**нет входа/выхода хит-теста → нет мигания**). Press-scale живёт на
> ВНУТРЕННЕЙ подложке (`Border.WinBtnBg`, фикс. 30×30), а не на самой кнопке»

and `:157-159`: «Транзишен ТОЛЬКО на RenderTransform — **нет BrushTransition фона, который мог бы
осциллировать/мигать**». Two named mechanisms, one fix, applied to one button. Every other button still
has both shapes:

**(a) `RenderTransform` scale on the Button itself.** Census over `Views/*.axaml` + `Assets/*.axaml`:
`scale(0.97)` ×20 · `scale(0.92)` ×11 · `scale(0.94)` ×1 · `scale(0.96)` ×1 · `scale(0.9)` ×1. Global
sites at `Assets/GlobalStyles.axaml:615,697,746,949,1113,1194,1287,1361,1407,1811,2019,2090`, plus
`scale(0.92)` redeclared verbatim in twelve view files. Every one scales the control that is
hit-testing: pointer near the edge falls outside → pseudo-class drops → scale reverts → pointer is
inside again. That is the oscillation.

**(b) a `BrushTransition` on a background whose rest value the app never declares.**
`GlobalStyles.axaml:641-647` attaches a 150 ms `BrushTransition` to
`Button.Primary /template/ ContentPresenter#PART_ContentPresenter`, and `:759-765` the same for
`Button.Tonal`/`Secondary`. The only values ever written to that presenter are **hover** (`:652`) and
**pressed** (`:655`) — there is no rest-state setter, so on pointer-exit the property falls back to a
value owned by the Semi theme **through the transition**.
`Button.PrimaryCompact` is the proof this is a bug, not a style: `:688-690` declares
`PART_ContentPresenter.Background = Brush.Accent` at rest. `Button.Primary` — **44 uses** — does not.

**Fix (one change, not fifty):** move press-scale onto an inner fixed-size presentation layer on every
archetype, exactly as `Border.WinBtnBg` does; declare the rest-state presenter background alongside
every hover/pressed setter that touches it; and settle on **one** press scale (D-11 says 0.97) — M-75.

### M-55 · The destructive confirm reads disabled next to a solid cancel · **PC** · OWNER D3
`Views/MessageBoxDialog.axaml:55-75` — verified today: cancel is `Classes="Tonal"`, confirm is
`Classes="Primary"`. This is M-54(b) seen from the front: `Button.Primary` declares its accent fill on
the **Button** (`GlobalStyles.axaml:595`) and never on the presenter that paints it, so at rest it
inherits the theme's neutral and only becomes accent-coloured under the pointer.
On top of that, a destructive confirm must not be the generic `Primary` accent at all.
`Button.Destructive` exists and the account tab already uses it (`Views/AccountView.axaml:1270`). Use it
here, and give the dialog the destructive verb — «Удалить», not `ResUI.TbConfirm` «Подтвердить».

### M-56 · The primary button is a flat fill with no depth and no gradient · **Android** · OWNER D1
> «кнопки должны все быть проработанные такие, а не просто сплошной цвет, может градиент какой-то,
> может даже анимированный»

`res/values/styles.xml:379-386` — `Widget.Departament.Button.Primary` is
`backgroundTint="@color/btn_primary_container"` on a base (`:356-378`) that sets `elevation 0dp`. Press
motion exists (`android:stateListAnimator="@anim/press_scale"` at `:365`), so that half is met; the
depth and the considered, possibly animated gradient are not.
**He overrode `00-rules.md`'s gradient ban for buttons specifically.** The ban still holds for page
backgrounds and decorative glows. D4 («много багов с кнопками») means this pass audits *every* button
on both platforms for state, contrast, alignment and hit area — not only the ones named here.

### M-57 · In the black/mono theme every primary button flashes brand blue on hover · **PC**
Verified today: seven theme-dependent keys are defined in Dark (`Assets/GlobalResources.axaml:74`) and
Light (`:166`) and **mirrored into the mono overlay zero times** —
`Brush.AccentHover`, `Brush.AccentPressed`, `Brush.OutlineControl`, `Brush.OnSurfaceVariantHover`,
`Brush.Amber`, `Brush.AmberText`, `Brush.Ping.Good` all return 0 matches in
`v2rayN.Desktop/App.axaml.cs` (`BuildMonoOverlay`, `:580`).
`Button.Primary:pointerover` (`GlobalStyles.axaml:653`) resolves `Brush.AccentHover`, falls through to
the base variant, and paints **#3D7EF0**. `Button.Primary` is used 44 times. Mono's whole contract is
"no accent hue".

### M-58 · The subscription meta-bar paints dark-theme hex literals, so the light theme is wrong · **PC**
`Views/SubscriptionMetaView.axaml.cs:29-32` — `static readonly IBrush _accent = Color.Parse("#4C8DFF")`,
`_muted "#9BA1AD"`, `_red "#F04452"`, with a comment that still says «тема одна, тёмная». In the light
theme its accent, muted text and destructive red are all wrong.

## 3.6 Behaviour that misleads

### M-59 · Cancelling Android's own VPN dialog is reported as a connection failure · **Android**
`ui/HomeFragment.kt:410-414`:

```kotlin
private val requestVpnPermission = registerForActivityResult(StartActivityForResult()) {
    if (it.resultCode == Activity.RESULT_OK) { startV2Ray() }
}
```

**No `else`.** A non-`RESULT_OK` result is dropped, leaving `connectInProgress = true` and the watchdog
armed. The disc spins «Подключение…» for 20 s and then says «Не удалось подключиться» — for something
the user cancelled. **A cancelled action is not a failure** (his G2 rule, first line).
**Fix:** add the else — `connectInProgress = false`, `cancelConnectWatchdog()`,
`applyRunningState(false, false)`, and either say nothing or «Разрешение на VPN не выдано.»
**Must not break:** proxy-only mode must keep skipping the prepare entirely (`:2128-2139`).

### M-60 · The no-server backstop idles the UI but leaves the watchdog armed · **Android**
`ui/HomeFragment.kt:2141-2149` clears `connectInProgress` and repaints but does not call
`cancelConnectWatchdog()`, so the watchdog armed one frame earlier fires 20 s later and sets
`tunnelError = true` — «Не удалось подключиться» for an attempt that never started. Same handler as
M-59; one line, one edit, do them together.

### M-61 · Re-tapping the already-selected server does nothing at all · **Android** · **the desktop is right here**
`ui/MainActivity.kt:917-919` — `if (guid == selected) return` precedes every piece of feedback. No
haptic, no snackbar, no connect. The desktop connects explicitly on a re-tap while disconnected;
Android answers a deliberate tap with silence.

### M-62 · The uptime clock counts the old session while the status says «Подключение…» · **Android**
`ui/HomeFragment.kt:2204-2210` no longer returns early on `isLoading` and the timer stops in the
`isRunning` observer's else branch (`:1202`), so a plain disconnect is correct. The restart path is not:
`applySelectionToRunningTunnel` sets `isLoading = true` while the tunnel is still up, so for the length
of the stop-then-start the clock keeps counting the previous session under the word «Подключение…».

### M-63 · Tapping a server on the desktop switches the live tunnel with no offer · **PC** · OWNER G1 · **the fix exists on Android**
> «при выборе серверов на андроиде удобно сделано что там предлагает переподключиться, я бы хотел чтобы
> ты такие фишки перенес и на пк 1 в 1»

**Android** (`ui/MainActivity.kt:925-948`): selecting a server never touches a running tunnel; it shows
a Snackbar naming the server (`server_selected_reconnect_prompt`) with a «Переподключиться» action, and
declining leaves the connection exactly as it was.
**Desktop** (`Views/ServerListView.axaml.cs:332-353`):

```csharp
if (IsCoreRunning()) { await vm.SelectServer(item.IndexId); return; }
```

A row click with the tunnel up **switches immediately** — no prompt, no naming, and no way to select
without switching: the context menu's «Сделать основным» (`:786-792`) calls the same `SelectRow`, and
«Подключиться» (`:776-782`) calls `vm.SelectServer` too. Connected, all three do the same thing, and it
is the destructive one. The markup comment at `ServerListView.axaml:174-181` asserts «ВЫБОР ≠
ПОДКЛЮЧЕНИЕ» — true only while disconnected.
**Port it exactly: same behaviour, same wording, same placement relative to the list, natively expressed.**

### M-64 · Traffic stats read a flat 0 KB/s for a working tunnel · **PC**
`ServiceLib/Services/Statistics/StatisticsXrayService.cs:96-100` accumulates only keys starting with
`Global.ProxyTag` (`"proxy"`), while `ServiceLib/Handler/CoreConfigHandler.cs:130-132,162-163`
deliberately keeps the template's outbounds as authored and never retags them. Any provider template
that tags its outbound anything else reads zero, with no error.

### M-65 · `StatePort2` applies the TUN offset outside its own memo, so two readers disagree by one · **PC**
`ServiceLib/Manager/AppManager.cs:25-32`:

```csharp
_statePort2 ??= Utils.GetFreePort(GetLocalPort(EInboundProtocol.api2));
return _statePort2.Value + (_config.TunModeItem.EnableTunEffective ? 1 : 0);
```

The port is memoised once; **the `+1` is recomputed on every read**, and `EnableTunEffective` changes
when the mode row is used (`SettingsViewModel.SetModeAsync:432`) or elevation is granted
(`StatusBarViewModel.cs:547`). A config generated before the change and a reader after it address
different ports: the app is up, the api channel talks to a port nothing is listening on, the speed
widget reads zero, nothing errors. Memoise the whole expression, or move the offset into `GetLocalPort`.

### M-66 · An Xray `api` inbound is grafted onto every config for a feature that is hard-disabled · **PC**
`ServiceLib/Handler/CoreConfigHandler.cs:240` calls `GraftXrayApi(root)` unconditionally. Its only
consumer is the Tier-2 hot swap, and `ServiceLib/Manager/CoreManager.cs:55` reads
`private static readonly bool EnableHotSwapTier = false;`. The grafted `dokodemo-door` binds
`AppManager.ApiPort` (`:41-47`), which comes from `Utils.GetFreePort` — a check, not a reservation. If
anything takes that port between check and bind, **Xray fails to start and the whole connect fails**,
for a listener nothing uses. Gate `GraftXrayApi` and `GenApi` on `EnableHotSwapTier`.

### M-67 · An out-of-range local-proxy port is rejected in silence · **PC**
`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:101` declares `PortInvalid` and `:481` sets it; a grep
over every `.cs` and `.axaml` returns those two lines and the doc comment. The state moved from "not
modelled" to "modelled and unrendered", which is not a fix.

### M-68 · The settings search field silently uses an older field style than every other input · **PC**
`Views/SettingsView.axaml:84` declares a **local** `ControlTheme x:Key="TextBox.IncyField"` that shadows
the promoted global one at `GlobalResources.axaml:635`; `{StaticResource}` resolves nearest-first. The
two bodies differ (radius, and the local one drops the `:disabled` opacity rule), and 75 lines of the
promoted global theme have no consumer at all.

### M-69 · Autostart is a no-op on the shipping build, and the toggle confidently reports success · **PC**
`Common/AutostartHelper.cs:46-48,85-104` writes `HKCU\…\Run`; Windows does not launch
elevation-requiring executables from the Run key at logon (and this app requests elevation — M-01).
`IsEnabled()` reads the value back and returns true, so the UI shows it on. Two competing autostart
mechanisms exist with different names (`ServiceLib/Handler/AutoStartupHandler.cs:83-125`;
`ViewModels/SettingsViewModel.cs:279-287`).

### M-70 · `AmazTool` kills and relaunches a process named `v2rayN` · **PC**
`AmazTool/Utils.cs:27,36`; `AmazTool/UpgradeApp.cs:24`. The fork's process is `departament`. Reachable
via `BackupAndRestoreViewModel.cs:141-147`'s `rebootas`. Same family as M-07 and M-08 — the fork was
renamed and the tooling was not.

---

# §4 · Unfinished craft

Nothing here misleads the user about money, data or connection state. All of it is the difference
between "works" and "finished".

## 4.1 States that were never designed · **PC** · OWNER G2

### M-71 · The connect disc is not operable without a mouse
`Views/ConnectHeroView.axaml` — a grep for `Focusable`, `IsTabStop`, `KeyDown`, `AutomationProperties`
over all 839 lines returns **nothing**. `#ConnectDisc` is a `Border` with raw pointer handlers
(`.axaml.cs:251-256`). The single action the product exists to perform has no keyboard path and no
accessible name. (The right/middle-button bug on the same control **is** fixed — `:694` filters
`IsLeftButtonPressed`.)

### M-72 · The G2 courtesy audit — the record he asked for, and what it found
He asked for the list *before* the fixing, so the list is the record. Seven rules, against the desktop:

| Rule | Desktop today |
|---|---|
| a cancelled action is never reported as a failure | **holds** (the Android equivalent is M-59) |
| an action that cannot work does not present an enabled control | **fails** — M-63, M-67 |
| an in-flight action shows it and cannot be fired twice | **partly** — sub-page push is idempotent by type (`MainWindow.axaml.cs:1118-1121`); the buy path is guarded (`IsPaying`); the **connect control has no in-flight lock** beyond its own 12 s deadline |
| a destructive action names what it will destroy | **holds for text**, **fails for form** — M-55 |
| an empty state says what to do next | **fails** — `ServerListView.axaml:305-326` is icon + title + line, **no action button** |
| a failure offers the retry where it reports it | **partly** — the account tab does (`AccountView.axaml:184-190`); nothing else |
| state changed elsewhere repaints here | **fails** — no offline state anywhere outside the account tab |

Four of the seven fail. Fixing them is this row.

### M-73 · The shell binds four keyboard shortcuts and no navigation
`Views/MainWindow.axaml.cs:1965-2025` binds Ctrl +/−/0, Ctrl+V, Ctrl+S, F5. Escape does not pop a
sub-page, the mouse back button does nothing, Ctrl+F does not reach search, Ctrl+, does not open settings.

### M-74 · The settings tree does not match its own spec
Six groups against the spec's four, 22 named rows, 8 reachable sub-pages out of 17 spec routes, and
**25 `Classes="Card"`** across a settings tree the spec says has none.
`Views/SettingsView.axaml:223,522,641,686,875,972`; `SettingsView.axaml.cs:43-50`.

## 4.2 The design system is not the thing the views speak

### M-75 · Five different press scales in one product · **PC**
`0.97` ×20, `0.92` ×11, `0.94` ×1, `0.96` ×1, `0.9` ×1, with `scale(0.92)` redeclared verbatim in twelve
view files. D-11 says one number. Land this with M-54 — same edit, same files.

### M-76 · 45 new class names and a whole motion vocabulary with zero consumers · **PC**
45 class names were added to `Assets/GlobalStyles.axaml` (+1197 lines) and **45 of 45 are referenced by
zero views and zero code-behind**; `Common/Motion.cs`'s new members
(`Dur.Pulse/Spin/Debounce/RevealExit/StateExit/Hover`, `PressScale`, `Play()`, `StaggerFor()`) have
**zero call sites**. Views still speak the old vocabulary plus 26 view-local rules in `AccountView`
alone. Not a user-visible bug; it is the reason the next design change costs twice.

### M-77 · Retired tokens still draw live UI · **PC**
`Radius.Search` is the corner radius of **the canonical server row** (`Views/ServerListView.axaml:156`)
and of both promoted text-box themes. `Brush.OutlineStrong`, required by `33-master-plan-pc` 2.12.2 for
the connect ring, does not exist.

### M-78 · Icon-only controls are unnamed for a screen reader · **PC**
`AutomationProperties.Name` appears in **1** of 50 views.

### M-79 · Two lists are unvirtualised · **PC**
`VirtualizingStackPanel` appears exactly once in the tree, at `Views/ServerListView.axaml:121`.
`PaymentHistoryView` and `DevicesView` render every row.

### M-80 · 91 off-scale spacing values · **PC**
Across `Views/` against the allowed 0/4/8/12/16/24/32/68. Worst: `SubscriptionMetaView` 10,
`AccountView` 9, `CompactServersView` 8, `RoutingSubView` 7.

### M-81 · `Font.Ui` is declared as a folder, sweeping a 10.5 MB Chinese font into the UI family · **PC**
`Assets/GlobalStyles.axaml:51` — `avares://departament/Assets/Fonts#Golos Text`. The folder form loads
every font asset under `Assets/Fonts`: three Golos files, `SpaceGrotesk.ttf`, **and
`NotoSansSC-Regular.ttf` (10 560 616 bytes)**, all embedded by `v2rayN.Desktop.csproj:43` and parsed at
collection build. Point the family at the three Golos files, or move Noto out of the folder.

## 4.3 Android craft and hygiene

| # | What | Where |
|---|---|---|
| **M-82** | Three private currency formatters each decide the symbol from the currency code; the owner's ₽ decision is enforced by none of them. One formatter should own it | `ui/BuyTariffActivity.kt:632`, `ui/AccountFragment.kt:850`, `ui/adapter/PaymentsAdapter.kt:87` |
| **M-83** | `ic_warning` and `ic_error` do not exist, so warn and error share the info glyph (in the correct tone, so two channels survive — but it is not the design). `ic_arrow_back` is also absent | `res/drawable/`, `HomeFragment.paintCondition` |
| **M-84** | `montserrat_thin.ttf` — 707 codepoints, referenced from nowhere, in every APK | `res/font/montserrat_thin.ttf` |
| **M-85** | Space Grotesk falls back to Light 300 on API 24-25: `res/font/space_grotesk.xml` pins `wght` via `fontVariationSettings`, which is API 26+/28+, against `minSdk = 24`. On 24-25 the Display/Chip weight difference does not exist. The fix is vendoring baked static masters — the same supply-the-file change as M-37 | `res/font/space_grotesk.xml` |
| **M-86** | Two built components with zero consumers — inflated by nothing, included by nothing. Give them a binder in `ui/component/` or delete them | `res/layout/view_chip.xml`, `res/layout/view_meter.xml` |
| **M-87** | Orphan resources left by removed screens: `dialog_config_filter`, `layout_servers_empty`, `layout_servers_header`, `layout_setting_row`, `layout_setting_toggle_row`, `preference_with_help_link`, `toast_status`. Plus `title_pref_show_memory`/`summary_pref_show_memory` (`res/values/strings.xml:366-367`, `values-ru/:345-346`) and `AppConfig.PREF_SHOW_MEMORY` (`AppConfig.kt:58`) — the key has **neither a reader nor a writer**. Decide it out loud and delete all four | — |
| **M-88** | **Latent, and a landmine under M-50.** `serversCache` is the *filtered* list (`viewmodel/MainViewModel.kt:244-274` applies `keywordFilter`/`protocolFilter` while building it), and both `MainActivity.updateBottomNavVisibility` and Главная's state resolver read it to answer "does this device have any servers at all". A filtered-to-zero list is indistinguishable from "no servers", so typing in a search box would empty Главная into the onboarding gate and hide the whole bottom navigation. Unreachable only because nothing writes either filter today. **Gate on the unfiltered stored count (`MmkvManager.decodeAllServerList()`) before restoring search** | `viewmodel/MainViewModel.kt:244-274`, `ui/MainActivity.kt:770` |

## 4.4 Desktop publish flags and one standing warning

| # | What | Where |
|---|---|---|
| **M-89** | `PublishReadyToRun=false` on a single-file self-contained Avalonia app — seconds of cold start for no correctness benefit. `CETCompat=false` opts a VPN client that runs elevated out of the hardware shadow-stack mitigation, inherited from upstream with no decision behind it | `Directory.Build.props:30`; `v2rayN.Desktop.csproj:15` |
| **M-90** | Per-node «Скорость» on a Departament server now reports an honest failure instead of a bogus measurement (`CoreConfigV2rayService.cs:249-261`), so this is a **capability gap**, not a bug: throughput on the product's own servers has no working path. Also `Global.ProtocolTypes[_node.ConfigType]` is an unguarded indexer whose `KeyNotFoundException` is swallowed | `Services/CoreConfig/V2ray/V2rayOutboundService.cs:314` |
| **M-91** | «Язык» — `SettingsViewModel.cs:603` no longer toggles `"en" ? "ru" : "en"`, but whether `Системный` is reachable was never re-traced end to end. One trace settles it | `ViewModels/SettingsViewModel.cs:603` |
| **M-92** | **Do not enable `PublishTrimmed`.** Eight reflection surfaces; two of them — `System.Text.Json` config/DTO loading and the `sqlite-net` server store — fail **silently** against the user's saved data. Recorded as a standing decision, not a task | — |

---

# §5 · Closed during this session — do not re-report

Each verified fixed in today's source. Listed so nobody spends a day re-finding them.

## Android

| Was | Now |
|---|---|
| **The add menu offers six things** (owner C1) — *closed under the register, during this pass* | `res/menu/menu_main.xml` is exactly QR + clipboard. «Отправить на ТВ» was a duplicate of `SettingsTabFragment.rowTvSend`; «Ввести ссылку» / «Создать вручную» / «Импортировать из файла» moved to `MainActivity.showAdvancedAddMethods()` (`:454`) and still work |
| **`group_server_list` declared, hidden, dispatched by nothing** | The group is **deleted** with the Серверы destination. The dead-menu defect is closed; the feature loss survives as M-51 |
| D01 Sign-in imports nothing and prunes everything | `AccountRepository.autoImportSubscriptions:108-123` merges `/subscription` and `/subscription/all`; `SubscriptionSyncManager.importAll` reads through `.raw()` (`:50`) and never prunes on an empty candidate set (`:101`) |
| D02 An expired JWT deletes every subscription and server | `refreshProfile`'s 401 calls `AccountSession.endSession()` (`AccountRepository.kt:75`), which clears the session only (`AccountSession.kt:65-68`). `wipe()` is explicit sign-out alone |
| D03 Quick tab switches strand the highlight | `showTab` (`MainActivity.kt:620-625`) has no animation left; `settleTabs` is the single authority on visibility |
| D04 Server rows have no long-press, so the actions sheet is unreachable | `MainRecyclerAdapter.kt:252-254` invokes it; `HomeFragment.kt:733` wires it. (Discoverability survives as M-52) |
| D06 Rapid taps issue a second start and push the deadline out | `handleConnectAction` (`HomeFragment.kt:2097-2105`) cancels an in-flight connect |
| D12 Sign-out leaves the account fragment collecting | `onSessionCleared` (`AccountFragment.kt:261-300`) cancels the poll, clears the ViewModel and blanks the render; tabs are `add`+`hide` found by tag, so a recreate cannot build a second instance |
| D13 Back minimises from every tab on API ≤34 | `onKeyDown` handles `KEYCODE_BUTTON_B` only; BACK belongs to the one `OnBackPressedCallback` (`MainActivity.kt:295-306`) |
| D14 «Удалить все серверы» loses the провайдер ordering | `MmkvManager.removeAllServer:303-317` removes only `SUB_SERVERS_*` and `KEY_SELECTED_SERVER` |
| D16 29 preference keys with no editing UI | `res/xml/pref_settings.xml` is down to 25 keys, each resolving to a literal in `java/` |
| D17 `CheckUpdateActivity` / `LogcatActivity` / `SettingsActivity` unreachable | Rows at `SettingsTabFragment.kt:123-125`. (`SubSettingActivity` was **not** given one — M-49. And what `CheckUpdateActivity` now reaches is M-07) |
| D18 No sign-out anywhere | `AccountFragment.confirmSignOut:668-689` → `beginSignOut:700` → `AccountViewModel.logout:457` |
| D19 An unparseable XRAY_JSON body wipes the provider's servers | `AngConfigManager.kt:618-642` stages the parse and deletes only on `staged.isNotEmpty()` |
| D21 / D22 Dead `R.id.sub_update` branch; dead «Привязать Telegram» banner | Both gone; the live entry point is `home_gate_link_telegram` |
| D23 `serverRawStorage` never deleted | Cleared in `removeAllServer` (`:314`), `removeServer` (`:215`), `removeServerViaSubid` (`:239`) |
| D25 Cross-process MMKV loses the session at random | `AuthTokenStore` opens `MULTI_PROCESS_MODE` (`auth/AuthTokenStore.kt:25-31`) |
| D26 One Keystore hiccup opens the encrypted file with no key | `KeystoreKeyProvider.CryptKeyState:39-58` distinguishes Available/Absent/Unsealable; an unsealable store is not opened, and only a successful open is cached |
| D27 The service receiver is registered once per recreate | `MainViewModel.broadcastRegistered` (`:163`, guarded `:176-181`) |
| U-21/U-22 Connect fires on every tap; the no-server guard leaves the UI connecting | Both closed by the move into `HomeFragment` (residue: M-60) |
| 22 dash/ellipsis copy hits | Zero remain in string bodies; the ` - ` matches in `res/values/` are all inside XML comments |
| Russian shadowed by leftover English in `values-ru/` | Measured across every paired file: **zero** such keys |

## Desktop

| Was | Now |
|---|---|
| A transient API failure at launch deletes every subscription and its servers | `Account/SubscriptionSyncManager.cs:57-100` — `primaryOk`/`allOk` gate a `canPrune` flag, plus a second guard for the 200-with-null case (`:112-122`) |
| `RemoveTunDevice` awaits an unbounded `pnputil` inside `_coreOpGate` | `Common/WindowsUtils.cs:77` passes `_tunRemoveTimeout` |
| `ProfileExManager` mutates a non-thread-safe `Queue<string>` from parallel speedtests | Rewritten |
| The user's TUN preference is permanently destroyed after one unelevated run | `ConfigItems.cs:199` `TunUnavailable` is `[JsonIgnore]`; `StatusBarViewModel.cs:150-156` keeps the intent; the reboot-as-admin path no longer writes `false` |
| `_hasNextReloadJob` read-then-clear race strands the tunnel on the wrong server | Replaced by an `Interlocked` `_pendingJob` ordered by strength (`MainWindowViewModel.cs:838-911`) |
| Real-ping reports healthy latency for a proxy returning error pages | `ConnectionHandler.cs:88-100` checks `IsSuccessStatusCode`, uses `ResponseHeadersRead` |
| Speedtest builds a bogus outbound for Custom nodes and reports success | `CoreConfigV2rayService.cs:249-261` rejects `EConfigType.Custom` with a real message (capability gap remains — M-90) |
| Right-click / middle-click on the connect disc toggles the VPN | `ConnectHeroView.axaml.cs:694` filters `IsLeftButtonPressed` |
| Double-activating the login CTA strands a `LoginView` forever | `LoginView.axaml.cs:180` resets `_detached` |
| A cancelled tab crossfade strands the two-steps-back tab opaque | `MainWindow.axaml.cs:450-458` normalises every uninvolved surface on each swap |
| `Execute().Subscribe()` with no `onError` (4 sites) | No matches remain |
| Compact/wide layout hardcoded to compact at first frame | `MainWindow.axaml.cs:256` reads `Width` |
| «Устройства» opens the **root** subscription's devices | `DevicesView.axaml.cs:31` passes `AccountViewModel.Shared?.DevicesScopeUuid`. **The Android half is still open — M-28** |
| «Обход локальной сети» writes the opposite direction | `SettingsViewModel.SetModeAsync:435` |
| «Автообновление подписки» configures geo-file updates in the wrong unit | `SettingsViewModel.cs:528-551` writes `SubItem.AutoUpdateInterval` in minutes, read at `TaskManager.cs:84-85` |
| Russian text set in the Cyrillic-free brand face in `AccountView` | No `Font.Grotesk` remains in any view's markup (the roles that keep it by design are M-38) |
| `ServerListView` rows are mouse-only | `Focusable`/`IsTabStop` at `:157-158`, `OnRowKeyDown` at `.cs:378-410`. *The Enter behaviour is itself M-63* |
| Inverted light-accent fallback comment; `ProviderSettingsPage` raw em-dash | Both gone |
| Content never capped at 720 | 21 `MaxWidth="720"` sites. *Whether 720 is right for a 1160 pane is M-48* |
| Em/en dashes in shipped copy; «Сервера» → «Серверы»; servers empty-state copy | Fixed (the empty state still lacks its **action** — M-72) |

---

# §6 · Refuted by verification — do not refile

Re-filing any of these wastes a wave. Each was checked by a named verification agent or by direct read.

| Claim | Verdict |
|---|---|
| «Привязать Telegram» is a lost feature | **Refuted.** The signed-out banner's removal was deliberate; a live entry point exists on the gate block. The dead banner was real and has been deleted — the *feature* was never lost |
| Changing per-app proxy never restarts the tunnel | **Refuted.** `PerAppProxyActivity.kt:239-242` calls `SettingsChangeManager.makeRestartService()`; the shell consumes it (`MainActivity.kt:246-248`) |
| Rotation replays the connect state: timer resets, animation replays, spurious toast | **Refuted, all three halves.** The confirm is gated on `liveTransition`, the toast on a known prior state, the uptime origin persisted in `KEY_CONNECTION_START` |
| 19 amputated menu actions | **Closed by the salvage commit**, then reduced by the tab removal. The live concern is M-51, and it is not nineteen |
| «Ping all» / «real-ping all» exists nowhere | **Refuted.** Two live entry points; a menu duplicate is a design question |
| «Restart service» exists nowhere | **Not a defect.** `restartV2Ray` is reachable from the reconnect snackbar, a core-config settings change, and the `SettingsChangeManager` flag. An explicit control is a product decision |
| Provider-settings toggles store a value and drive no behaviour | **Refuted.** All five consumers verified, including the sort order, applied through `SettingsManager.applyServerSortOrder()` and not the getter a grep would find |
| `locateSelectedServer` has zero callers | **Was closed**, then removed with the Серверы tab. Now part of M-51 |
| «The desktop mode control has two options where the phone has three» (owner C2) | The desktop has **three** (`SettingsView.axaml:348-363`). The **labels** are wrong — M-35 |
| «The Аккаунт tab is a bare card floating in an empty pane» (owner F2), *as a statement about the signed-in tab* | The signed-in tab is 1331 lines of built screen. The **signed-out gate** is the bare one — M-48. His complaint stands; its address moved |
| «Double-tapping the onboarding CTA» as the trigger for the stranded `LoginView` | `verify-loginview-detached-handoff.md` refuted the pointer trigger; the underlying flag is fixed anyway |
| «`_hasNextReloadJob`'s `:873-875` window is the lossy one» | `verify-hasnextreloadjob-race.md`: that window is provably self-healing. The real one was elsewhere and is fixed |
| «Speedtest never works for the product's servers», high severity | `verify-desktop-speedtest-custom-outbound.md` downgraded it — the path was not reachable from the shipped UI. Now guarded; the capability gap is M-90 |
| «Five interactive archetypes below the 48px touch minimum» | `verify-iconbutton-drift.md`: headline framing wrong. The real residue is the icon-button consolidation not landing — M-75/M-76 |
| «`ProfileExManager`'s queue race is high severity» | `verify-profileex-queue-race.md` corrected mechanism and severity; fixed since |
| Adding a «Серверы» destination to the desktop | **Owner decision**, `1b9d3cb` — the desktop does not get one. `HomeView`'s right column is the list |

---

# §7 · Open questions, and what only a device can settle

Two questions block nothing but would save a wave each:

1. **Which sign-in screen was in his screenshot** (M-47) — `LoginView` or the onboarding gate's sign-in
   row (`OnboardingView.axaml:161-190`). Both match «buttons at the very bottom». The five structural
   findings hold either way, so start on them and ask him in parallel.
2. **Was his Windows run actually elevated** (M-01) — the gate cannot close on an elevated process and
   the manifest requests elevation. If he accepted a UAC prompt and it still did not tunnel, the cause
   is downstream: the sing-box pre-service failing to start or to create the wintun device, or the port
   contract with the Xray SOCKS inbound. **M-01 must land regardless — it is what would have answered
   this without asking him.**

Needs a device, code fact already proven:

| Item | What a run must show |
|---|---|
| M-19 | Add a PolicyGroup or an unparseable CUSTOM profile, enable «Автоудаление нерабочих серверов», run the latency check — confirm the untested rows are deleted |
| M-36 | Open «Настройки провайдеров» and compare the title against a Golos-drawn row title on the same screen |
| M-30 | Open the payment-method sheet, rotate, pick a method. Expect `IllegalStateException` |
| M-42 | Set the in-app language to English and walk Аккаунт → Купить → Настройки → Дополнительно |

**Never examined in this session, on any screen, on either platform:** TalkBack/screen-reader
traversal, measured contrast on a real panel, touch-target measurement, frame timing. Their absence
from this register is not a clearance.

---

# §8 · The order to do the work in

Each block is one sitting for one wave. Blocks are ordered so nothing re-opens what came before.

**Block 1 — before he touches the build again** (see the gate below)
1. **M-01** — the desktop must say when it cannot tunnel. Every other answer to «не подключается» is
   guesswork until it does.
2. **M-18** — a signed-out app showing selectable servers. His own report, and a data-integrity bug.
   One call.
3. **M-19** — stop the latency check deleting servers. It runs by itself today.
4. **M-16** with **M-17** — the double charge and the unverified success. Port the desktop's `IsPaying`.
5. **The copy block: M-33, M-34, M-35.** Three edits, all his words, all visible in the first ten
   seconds. Both platforms in one pass.
6. **M-36** — delete one XML attribute and twelve Android screens stop drawing Russian in a fallback face.

**Block 2 — the font, and the buttons he called out**
7. **M-37 → M-39** — ship the static Space Grotesk masters, *then* set line heights from real metrics.
   One supply change, then one pass. M-39 is what fixes the clipped server names.
8. **M-54 with M-55 and M-75** — one flicker cause, the confirm button, one press scale. Same files.
9. **M-56** — the Android primary button: depth, gradient, press motion. Then D4: audit every button on
   both platforms.
10. **M-57, M-58** — the two theme leaks.

**Block 3 — the money screen and the connect flow, each as one unit**
11. **M-30, M-31, M-32** together with the Block-1 money items — one state machine, one screen; fixing
    them apart re-opens each other.
12. **M-59 with M-60** — same handler, one edit.
13. **M-61, M-62**, then **M-63** (port the reconnect offer from `MainActivity.kt:925-948`).
14. **M-28 with M-29** — per-subscription scoping, both halves, copying `DevicesView.axaml.cs:31`.

**Block 4 — the surfaces that lost their contents**
15. **M-51 with M-88, then M-50, M-49, M-52.** The list surface is back; decide what it carries, and fix
    the sentinel (M-19, already done in Block 1) and the filtered-cache gate *before* restoring search
    or any bulk delete.
16. **M-47, M-48** — the desktop sign-in and the account gate, rebuilt against the phone.
17. **M-53** — decide, per view, wire or delete.

**Block 5 — nobody calls a build releasable before this**
18. **M-03, M-04, M-05, M-06, M-07, M-13, M-14** (Android) and **M-08, M-09, M-10, M-11, M-12, M-15**
    (desktop). M-07 is one edit that covers both platforms.
19. **M-24, M-25, M-20, M-21, M-22, M-23, M-26, M-27** — the doors and the token leaks. Small, and they
    are open.

**Block 6 — localisation**
20. **M-42.** The largest single item, and the one that most wants a written decision before any code.
    Then M-43, M-44.

**Block 7 — craft**
21. §4 in order: M-71 → M-92, and the residue of §3.2 (M-40, M-41).

⏳ **M-45 and M-46 are on no block.** They belong to waves that are mid-edit. Re-read them at the gate
against §E and §C3 of his file before signing either off.

---

## The gate: what must be done before the owner tests again

He will install the next CI build and repeat his walk. These are the items that decide whether that run
tells us anything new. Everything else can wait for the run after.

| # | Why it must be in that build |
|---|---|
| **M-01** | Otherwise «не подключается» comes back identical and we have learned nothing. This is the single highest-value item in the document |
| **M-18** | His A1, verbatim, and it is data integrity, not paint |
| **M-19** | It deletes his servers, unattended, today |
| **M-16** + **M-17** | He is testing a payment flow with real money and no guard |
| **M-33, M-34, M-35** | His B1/B2/B3 word-for-word. If the next build still says «провайдер», the feedback loop is broken, not the code |
| **M-36** | Twelve screen titles in the wrong face. One attribute |
| **M-37 → M-39** | «шрифт какой-то толстый» and «у серверов везде кривой текст» are two of his four desktop complaints, and M-39 is the clipping he pointed at directly |
| **M-54 + M-55** | «кнопки все баганные, при наведении моргают» — the first thing he sees on every desktop screen |
| **M-63** | His G1, named as the example of what he wants ported. It is also the desktop dropping a live tunnel unasked |
| **M-02** | He raised it twice, which he told us means it matters more than its line count |
| **M-45, M-46** | Whatever the in-flight waves land, verify against §E and §C3 **before** the build goes out |

Two things in that build he cannot see and must be told: **M-03** (still debug-signed — that build
cannot upgrade to the next one, he must uninstall first) and **M-07** (do not tap «Проверить
обновления»; it offers a different application).
