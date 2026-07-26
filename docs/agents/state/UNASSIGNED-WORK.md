# UNASSIGNED WORK — the single list of what nobody is doing

**Written:** 2026-07-26 · **Branch (both repos):** `claude/app-audit-agents-hyyftk`
**Inputs:** `sweep-plans.md`, `sweep-specs.md`, `sweep-audits.md`, `sweep-ops.md`, de-duplicated
against each other and re-checked against `/home/user/dp/V2rayNG/app/src/main` (Android) and
`/home/user/v2rayN/v2rayN` (desktop). Read-only: no source file was changed, no git command run.

## How this list was built

Every actionable item in the four sweeps was pooled, then filtered four ways:

1. **Dropped if the code already has it.** I re-grepped, I did not trust the sweeps. Six claims did
   not survive — they are in §6 so nobody re-files them.
2. **Dropped if a running wave covers it.** In flight: the MainActivity split stages 3-4; the five
   Android screens (home, sign-in, account, settings, servers); the desktop screens (shell+home,
   account, settings, server list inside Главная, sign-in). The copy register's *enforcement* has
   not started, so copy items stay on this list.
3. **Dropped if a document refused it.** Those are §5, with their reasons.
4. **Merged where two sweeps found the same hole from different directions.** 23 pairs collapsed;
   the merges are named on each row so the provenance survives.

Everything below was read in the tree at sweep time. Line numbers drift (both trees are being
edited); symbol names do not. Where a sweep's line number had moved I re-located the symbol and
cite the new position.

**The shape of what is left.** Four bodies of work, in descending order of what the user loses:

- **A shipping pipeline that cannot ship.** Release APKs collide on `versionCode`, are signed with a
  throwaway per-runner key, the Linux/macOS desktop packages **do not launch**, the store listing is
  upstream's, and no gate has ever built a release variant. §1.
- **Security holes with a live door.** A web page can rewrite the user's server list and select an
  attacker's server, with no confirmation. User CA certificates are trusted app-wide. The backup zip
  carries the operator's subscription tokens in plaintext. §2.
- **The money path reports success it never verified.** A balance payment closes the buy screen
  without reading the status the backend returned; nothing prevents a double charge; the Devices page
  never learns which subscription it is for. §3.
- **Everything specified between screens.** Offline as a state, the status strip, the route
  vocabulary, adaptive layout, the list-performance floor, the expiry warning asked for by four
  separate documents. §4.

And one item that is not in any of those buckets and is bigger than all of them: **every non-Russian
locale falls back to Russian** (U-13).

---

## 1 · RELEASE / STORE BLOCKERS — a green debug build hides all of these

Called out first, as requested. None of these can be caught by `assembleFdroidDebug` or by
`dotnet build -c Release`, which is the entire gate today (`docs/agents/verify-build.sh:51,84`).

### R-01 · All five playstore APKs get the same `versionCode` — Google Play refuses the upload
**value: high · size: S · never implemented**
`V2rayNG/app/build.gradle.kts:127-141`, the non-fdroid branch, maps every ABI to `4`:
```kotlin
val versionCodes = mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)
output.versionCodeOverride = (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
```
ABI splits are on, so `assemblePlaystoreRelease` emits five APKs with identical version codes and
distinct filenames. Play requires one distinct code per APK. The **fdroid** branch (`:109-125`) does
it correctly — the flavour that would actually go to a store is the broken one.
*Files:* `V2rayNG/app/build.gradle.kts`.

### R-02 · Release APKs are debug-signed with a key that changes every CI run
**value: high · size: S · never implemented**
`V2rayNG/app/build.gradle.kts:70` `signingConfig = signingConfigs.getByName("debug")`. Only
`build.yml:99` injects real signing secrets; `release.yml` — the workflow whose own step is titled
"Build release APK … debug-signed" — passes nothing, so it signs with AGP's auto-generated
`~/.android/debug.keystore`, created fresh per runner. **Two consecutive release builds sign with
different keys**: a tester who installed build N cannot install N+1 without uninstalling. No release
keystore is checked in and `APP_KEYSTORE_BASE64` is handled in exactly one workflow.
*Files:* `V2rayNG/app/build.gradle.kts`, `.github/workflows/release.yml`.

### R-03 · Every desktop Linux/macOS package launches a binary that no longer exists
**value: high · size: M · broken by our own change**
`v2rayN.Desktop.csproj:8` sets `<AssemblyName>departament</AssemblyName>`. The packaging scripts were
never updated. `package-debian.sh:515-528` (identical in `package-debian-loong.sh`,
`package-debian-riscv.sh`, `package-rhel.sh`, `package-rhel-loong.sh`, `package-rhel-riscv.sh`):
```bash
DIR="/opt/v2rayN"
if [[ -x "$DIR/v2rayN" ]]; then exec "$DIR/v2rayN" "$@"; fi
for dll in v2rayN.Desktop.dll v2rayN.dll; do … done
echo "v2rayN launcher: no executable found in $DIR" >&2 ; exit 1
```
None of the three candidates is produced any more — the installed `.deb`/`.rpm` prints an error and
exits 1. `package-osx.sh` fails *silently*: `:38-39` `CFBundleExecutable` = `v2rayN`, absent from
`Contents/MacOS/`; `:45` `CFBundleIdentifier` = `2dust.v2rayN`.
*Files:* the seven `package-*.sh` in `/home/user/v2rayN/`.

### R-04 · The store listing is upstream's, and a workflow validates it green
**value: high · size: S · never touched**
`fastlane/metadata/android/en-US/title.txt` = `v2rayNG`; `short_description.txt` = "A V2Ray client
for Android, support Xray core and v2fly core"; `full_description.txt` is 2dust's copy linking
`t.me/github_2dust`. `.github/workflows/fastlane.yml` runs `validate-fastlane-supply-metadata`, which
checks *structure* — so this passes CI and reads as covered.
*Files:* `fastlane/metadata/android/en-US/*`.

### R-05 · The fdroid release APK's launcher label is `v2rayNG (F-Droid)`
**value: high · size: S · never touched**
`V2rayNG/app/src/fdroid/res/values/strings.xml:3` overrides `app_name` (which is `departament` in
`res/values/strings.xml`). `build.yml` runs `assembleRelease`, i.e. both flavours.
*Files:* `V2rayNG/app/src/fdroid/res/values/strings.xml`.

### R-06 · `winget-publish.yml` publishes **upstream's** binaries under **upstream's** id when we cut a release
**value: high · size: S · never touched**
`/home/user/v2rayN/.github/workflows/winget-publish.yml:19` `$wingetPackage = "2dust.v2rayN"`, `:22`
reads assets from `api.github.com/repos/2dust/v2rayN/releases` — and it fires on
`release: types: [released]` in **this** repo. The whole desktop release pipeline is upstream's:
`build-windows-desktop.yml` → `build.yml` → `package-zip.yml` (downloads `2dust/v2rayN-core-bin`,
names the asset `v2rayN-windows-64.zip`) → `upload-sign.yml` publishes it to our tag. The only
Departament-aware workflow, `departament-branch-build.yml`, is win-x64 only and uploads an artifact.
*Files:* `/home/user/v2rayN/.github/workflows/*.yml`.

### R-07 · No gate ever builds a release variant, and the unit tests are run by nothing
**value: high · size: S · never implemented**
`docs/agents/verify-build.sh:51` builds `:app:assembleFdroidDebug`; `:84` builds
`dotnet build v2rayN.Desktop.csproj -c Release`. No `assemblePlaystoreRelease`, no
`dotnet publish -r <rid>`. Separately, `grep "gradlew test\|dotnet test"` over `.github/workflows/`
and `verify-build.sh` = **0**: the five Android test classes
(`app/src/test/java/com/v2ray/ang/{HttpUtilTest,UtilsTest,dto/ProxyOutboundResolutionTest,fmt/ShadowsocksFmtTest,util/FlagUtilTest}.kt`)
have never been executed by an automated gate — including the two written specifically to lock in
the "effective outbound through routing" and "flag accuracy" fixes. `ServiceLib.Tests` has a
workflow, `test.yml:25`, pinned to `dotnet-version: '8.0.x'` against
`v2rayN/Directory.Build.props:8` `<TargetFramework>net10.0</TargetFramework>` — it cannot restore.
*Files:* `docs/agents/verify-build.sh`, `.github/workflows/` (both repos).

### R-08 · Every convenience workflow points at a branch the work left
**value: medium · size: S · one line each**
| Workflow | Push trigger | Actual branch |
|---|---|---|
| `dp/.github/workflows/debug.yml:7` | `claude/vpn-client-happ-design-mq51pv` | `claude/app-audit-agents-hyyftk` |
| `dp/.github/workflows/release.yml:7` | `claude/vpn-client-happ-design-mq51pv` | ″ |
| `dp/.github/workflows/build.yml:11` | `master` | ″ |
| `v2rayN/.github/workflows/departament-branch-build.yml` | `claude/dp-desktop-incy` | ″ |
Combined with R-07 this is *why* R-01 … R-06 have never surfaced.

### R-09 · CI installs SDK platform 36.1 for a `compileSdk = 37` project
**value: medium · size: S · open**
`.github/workflows/build.yml:29` `packages: 'platforms;android-36.1 …'` against
`app/build.gradle.kts:9,15` `compileSdk = 37` / `targetSdk = 37`.
`docs/agents/BUILD-VERIFY.md:40-41` already records that the correct package is
`platforms;android-37.0` and that rediscovering this "cost time". The pipeline survives only on
AGP's implicit SDK auto-download.

### R-10 · `departament-branch-build.yml` ships no AmazTool, so the desktop upgrade path is dead
**value: medium · size: S · open**
`ServiceLib/Common/Utils.cs:818` resolves `GetExeName("AmazTool")` next to the executable to apply a
downloaded upgrade. Upstream's `build.yml:81-84` publishes it; the Departament branch build
(`:32`) publishes only `v2rayN.Desktop.csproj`. `README.md:71-72` documents the two-command publish
including AmazTool — the README is right, the workflow is wrong.

### R-11 · `isMinifyEnabled = false` is undecided, not decided
**value: medium · size: M · needs a ruling, not a fix**
`V2rayNG/app/build.gradle.kts:63`, with `proguardFiles(…, "proguard-rules.pro")` declared and
`app/proguard-rules.pro` containing nothing but the AGP comment banner. No `shrinkResources`. The
release APK ships every unused resource and unobfuscated class names including all of `auth/**`.
Turning it on is real work (Gson DTOs, MMKV, the reflective `libv2ray` surface all need keep rules).
Listed so it gets a ruling rather than drifting. *(Same item as `recon-android-peripherals` S15; the
debug-signing half of that finding is R-02, the release-key comment there is a stated decision.)*

### R-12 · `ndkVersion` is injected at line 10 of a file whose line 10 already is `ndkVersion`
**value: low · size: S · open**
`build.yml:37-39` does `sed -i '10i\ … ndkVersion = "28.2.13676358"'`; `app/build.gradle.kts:10`
already is that line. Kotlin DSL tolerates the double assignment today; the next edit above line 10
injects it into a random position.

---

## 2 · SECURITY — small fixes, live doors

### U-01 · A web page can wipe the server list, install and select an attacker's server, and swap the routing rules — no confirmation anywhere
**value: high · size: M · never implemented**
*(merges `sweep-audits` S1 with `sweep-specs` B2 — the same hole found as a data defect and as a
missing design surface.)*

`AndroidManifest.xml:163-190` exports `UrlSchemeActivity` with
`<category android:name="android.intent.category.BROWSABLE" />` and `<data android:scheme="depv" />`.
`ui/UrlSchemeActivity.kt:82 handleDepvScheme()` dispatches and **executes immediately**:

| Line | Host | Effect |
|---|---|---|
| `:88,:90` | `connect`/`disconnect` | starts / stops the tunnel |
| `:94-96` | `toggle` | ″ |
| `:100` | `import` | `importDecodedConfig(decoded)` → `:142` `AngConfigManager.importBatchConfig(content, "", false)` |
| `:110` | `add` | `parseUri(raw, null)` → `:186` same call |
| `:118` | `routing` | `importRoutingRules(json, apply = op == "onadd")` → `SettingsManager.resetRoutingRulesets` |

`append = false` and `subid = ""` mean the ungrouped bucket is **replaced**, not appended to.
Contrast `MainActivity` and `tv/TvReceiveActivity`, which both pass `append = true`.
`ui/ScScannerActivity.kt:22` has the same `false`.

`11-app-structure.md` §7.2 marks all four **"confirm sheet, mandatory"** with the reason: *"A link in
a chat message must not be able to rewrite what the user connects through."*
`32-master-plan-android.md` §22.6 adds that a deep link arriving while the app is closed must open
`MainActivity` on Главная with the sheet already presented — today `UrlSchemeActivity.kt:63-64` does
`startActivity(MainActivity); finish()` from a bare translucent activity.
*Files:* `ui/UrlSchemeActivity.kt`, `ui/ScScannerActivity.kt`, `AndroidManifest.xml`, a new confirm sheet.

### U-02 · User-installed CA certificates are trusted app-wide and cleartext is permitted
**value: high · size: S · never implemented**
`res/xml/network_security_config.xml` is byte-for-byte upstream:
```xml
<base-config cleartextTrafficPermitted="true">
  <trust-anchors>
    <certificates src="system" />
    <certificates src="user" tools:ignore="AcceptsUserCertificates" />
  </trust-anchors>
</base-config>
```
For a product whose stated threat model is a national censor, a user-installable MITM anchor over
the account API and the subscription fetch is the wrong default. *(sweep-audits S2.)*

### U-03 · The backup zip carries the operator's subscription tokens off the device in plaintext
**value: high · size: M · never implemented**
*(merges `sweep-plans` S1 with `sweep-ops` OPS-22 — the same secret leaving by two channels.)*

`hidden-templates-design.md` §3.4 + §5 step 10 required backup/export to skip locked profiles and
subs. `ui/BackupActivity.kt:162` is `MMKV.backupAllToDirectory(backupDir)` → `ZipUtil.zipFromFolder`
→ a plain zip the user can share (`backup_action_share`) or push to WebDAV.
`grep -n "locked" ui/BackupActivity.kt handler/WebDavManager.kt util/ZipUtil.kt` → **0**.

The locked *template body* is encrypted (`template/TemplateManager.wrapRawForStorage`), but the thing
the feature exists to hide — the **subscription URL, which carries the account token** — sits in the
ordinary config MMKV in plaintext and rides along. Two consequences: the managed sub URL leaves the
device in a shareable file, defeating `SubEditActivity.kt:93-97`'s careful redaction; and restoring
that zip on a *different* device yields locked profiles whose raw is `dpt-enc:`-prefixed and
undecryptable (the Keystore key does not travel), so `unwrapStoredRaw` returns `null` and the profile
silently cannot connect. Nothing tells the user.

Second channel: `AndroidManifest.xml:45` `android:allowBackup="true"` with **no**
`android:fullBackupContent` and **no** `android:dataExtractionRules` anywhere in the manifest — every
MMKV store is eligible for Google cloud backup and `adb backup`.
*Files:* `ui/BackupActivity.kt`, `util/ZipUtil.kt`, `handler/WebDavManager.kt`, `AndroidManifest.xml`.

### U-04 · Zip Slip and zip-bomb in backup restore
**value: high · size: S · never implemented**
`util/ZipUtil.kt:80-100` `unzipToFolder` iterates `zip.entries().asSequence().forEach` and builds
`destDirectory + File.separator + entry.name` with **no canonical-path check, no entry cap, no total
size cap**. Reachable from `ui/BackupActivity.kt` restore, i.e. from any zip the user was sent.
*(sweep-audits S3.)*

### U-05 · Exported broadcast receivers let any installed app stop the VPN
**value: medium-high · size: S · never implemented**
`util/Utils.kt:552-556` `receiverFlags()` returns `ContextCompat.RECEIVER_EXPORTED` on API 33+.
*(sweep-audits S4.)*

### U-06 · The "departament-only" guards accept attacker-controlled domains
**value: medium-high · size: S · never implemented**
`util/SubscriptionGuard.kt:19` `host.split(".").any { it == REQUIRED_LABEL }` accepts
`departament.<any-tld>`; `util/SubscriptionOrigin.kt:33` `host.lowercase().contains("departament")`
accepts `evil-departament.com`. Both gate whether a subscription is treated as the operator's.
*(sweep-audits S5.)*

### U-07 · The full subscription URL is written to logcat
**value: medium · size: S · one line**
`handler/AngConfigManager.kt:892` `LogUtil.i(AppConfig.TAG, fetchUrl)`, and `ui/UrlSchemeActivity.kt`
logs the incoming URI twice more. The URL is bearer-equivalent. Compounds with U-19 (the log viewer
has a door now and no redaction filter). *(sweep-audits S8.)*

### U-08 · The LAN-exposed SOCKS password comes from a non-cryptographic RNG
**value: medium · size: S · never implemented**
`handler/SettingsManager.kt:37` `import kotlin.random.Random`; `:364`
`chars[Random.nextInt(chars.length)]` generates the share credential; `:496` uses the same RNG for
the dynamic SOCKS port. *(sweep-audits S7.)*

### U-09 · TV pairing pushes the subscription URL over cleartext HTTP
**value: medium-high · size: M · shipped as the MVP the design called MVP-only**
`tv/TvSendActivity.kt:176` `.url("http://${info.ip}:${info.port}${TvPairingProtocol.PAIR_PATH}")`.
`grep "SSL\|Cipher\|encrypt"` over `tv/` → **0**. `smart-tv-transfer-design.md` §3.5 recommends
HTTPS with a QR-pinned self-signed fingerprint or token-derived AEAD, and calls plain HTTP
*"acceptable only as v1 MVP … the sub URL is visible to a LAN sniffer / rogue AP"*. Everything else
§3.6 mandated **is** implemented — single-use token, TTL close (`TvHttpReceiver.kt:99`),
constant-time compare and bad-attempt lockout — so this is one transport swap, not a rebuild.
*(sweep-plans S2.)*

### U-10 · No template validator: a locked operator template can open a public inbound on the device
**value: medium · size: S-M · never implemented**
`hidden-templates-design.md` §5 step 7 and `remnawave-templates-spec.md` §6.3 both ask for a
validator requiring `outbounds`, **rejecting or stripping inbounds that listen on anything other than
loopback/tun**, and capping template size and rule count. What exists is
`AngConfigManager.stripVendorRootKey()` (`:571`) and a substring test. `find java -name
"TemplateValidator*"` → **0**. *(sweep-plans S3.)*

### U-11 · The fetch path has no `callTimeout` and no body-size cap
**value: medium · size: S · never implemented**
`util/HttpUtil.kt:380-383` sets only `connectTimeout` / `readTimeout`. A server that dribbles bytes
forever is unbounded in both time and memory. *(sweep-audits S6.)*

---

## 3 · THE PERIPHERALS ARE DEAD, AND THE MONEY PATH LIES

### U-12 · Every launcher shortcut is dead, and every peripheral start path skips VPN consent
**value: high · size: S · never implemented**
Two one-line-ish fixes that together are the reason four surfaces do not work.

- **Dead shortcuts.** `res/xml/shortcuts.xml` pins `android:targetPackage="com.v2ray.ang"` on all
  four shortcuts (`:14, :28, :42, :56`); `V2rayNG/app/build.gradle.kts:13` sets
  `applicationId = "com.departamentvpn.app"`. Every intent resolves to nothing.
- **No VPN consent.** `core/CoreServiceManager.kt:66-79` `startVServiceFromToggle` checks only for a
  selected server, then calls `startContextService` — there is no `VpnService.prepare()`. Callers:
  `service/QSTileService.kt`, `receiver/WidgetProvider.kt`, `receiver/TaskerReceiver.kt`,
  `ui/ScStartActivity.kt:14`, `ui/ScSwitchActivity.kt:16`. Only
  `MainActivity.startVpnWithPermission():1931` prepares. On a device that has never granted consent,
  the tile, the widget, Tasker and the shortcuts all fail silently.

*(sweep-audits S13 + S14.)* *Files:* `res/xml/shortcuts.xml`, `core/CoreServiceManager.kt`.

### U-13 · A balance payment reports success without reading the status the backend returned
**value: high · size: S · never implemented**
`viewmodel/AccountViewModel.kt:344-346`:
```kotlin
fun payWithBalance(req: PaymentRequestDto, onDone: () -> Unit = {}) = viewModelScope.launch {
    repo.payWithBalance(req).onSuccess { onDone() }.onFailure { report(it) }
}
```
`PaymentResultDto.status` is never inspected. Both callers report success and `BuyTariffActivity`
calls `finish()`. A `200` carrying `status: "failed"` closes the buy screen and tells the user they
own a subscription they did not buy. *(sweep-audits S18 / `audit2026/android-account` P0-1.)*

### U-14 · Nothing prevents a double charge
**value: high · size: S · never implemented**
`grep "isPaying\|isEnabled = false\|input_debounce"` over `ui/BuyTariffActivity.kt`,
`ui/PaymentMethodSheet.kt`, `ui/AccountFragment.kt` → **0**. The progress view already exists and is
already bound (`ui/BuyTariffActivity.kt:94` `progressBuy = findViewById(R.id.progress_buy)`) and is
set `GONE` in four places and `VISIBLE` in none. One `isPaying` flag closes it.
*(sweep-audits S20 = `bugs-android-confirmed` D10 = `hunt-transient-ui` P7 — three documents, one hole.)*

### U-15 · The Devices page is never told which subscription it is for
**value: high · size: S · never implemented**
`ui/DeviceManagementActivity.kt:235` declares `EXTRA_REMNAWAVE_UUID` and `:58` reads it. **Zero
senders exist in the whole tree** — the only three matches are the declaration, the read and the
KDoc. On a multi-subscription account the page shows the root subscription's devices and unlinks
against the root uuid whatever card the user was on. This is the exact failure mode the repo
`CLAUDE.md` warns about. **The desktop half of this defect was fixed** —
`Views/DevicesView.axaml.cs:31` passes `AccountViewModel.Shared?.DevicesScopeUuid`; Android was left
behind. *(sweep-audits S19.)*

### U-16 · Every subscription card in the carousel shows the ROOT subscription's device count
**value: medium-high · size: S · never implemented**
`ui/AccountFragment.kt:174` `resolveUsedDevices = { viewModel.deviceCount.value ?: 0 }` — the lambda
**ignores the `SubInfoDto` it is handed** (`ui/SubscriptionPagerAdapter.kt:24` types it as
`(SubInfoDto) -> Int`, `:88` calls it per card). `AccountFragment` also fetches devices for
`list.firstOrNull()` only. Same root cause as U-15. *(sweep-audits S23 / `hunt-transient-ui` P16.)*

### U-17 · Declined, cancelled and timed-out checkouts end in silence, and the poll restarts forever
**value: high · size: M · never implemented**
`ui/AccountFragment.kt:833-851` and `ui/BuyTariffActivity.kt:582-593` run a fixed `repeat(6)` /
`repeat(5)` × 8 s poll, **never inspect payment status**, then hide the hint with no verdict, no copy
and no action. The `orderId` from `PaymentInitDto` is captured and discarded. Compounding:
`AccountFragment.kt:826` `if (pendingPayment) startPaymentPolling()` in `onResume`, while the job
lives on `viewLifecycleOwner.lifecycleScope` and is cancelled by the tab switch **before**
`pendingPayment = false` at `:848` — so the window restarts indefinitely on tab re-entry.
*(sweep-audits S21 + S24 merged / `hunt-transient-ui` P9 + P10.)*

### U-18 · A failed payment leaves a live «Итого» and «Оплатить» for an invisible selection
**value: medium-high · size: S · never implemented**
`ui/BuyTariffActivity.kt:206-245` `renderTariffs` clears `checkMarks`/`optionRows` and rebuilds the
container, and never touches `selectedTariff`, `selectedOption`, `extraDevices` or
`checkoutCard.visibility`. `selectTariff` (`:346`) returns early when the tariff is already selected,
so re-tapping cannot recover the paint. *(sweep-audits S31 / D09 / P6.)*

### U-19 · Rotating with the payment-method sheet open crashes on pick
**value: medium-high · size: S · never implemented**
`ui/PaymentMethodSheet.kt:135` keeps a process-static lambda across configuration changes; the
`AccountFragment` lambda captures the dead fragment instance. No `setFragmentResultListener`, no
FragmentManager re-resolution. *(sweep-audits S32 / D11.)*

### U-20 · Three private currency formatters decide the symbol from the currency code
**value: low-medium · size: S · never implemented**
`ui/BuyTariffActivity.kt:639`, `ui/AccountFragment.kt:862`, `ui/adapter/PaymentsAdapter.kt:92`. The
owner's ₽ decision is not enforced by one formatter. *(sweep-audits S22.)*

---

## 4 · THE CONNECT FLOW — the desktop half was fixed as one contract, Android was not

`hunt-transient-ui.md` says P1/P2 (PC) and P3/P4/P5 (Android) "are the same defect on two platforms
and should be fixed as one contract." Only the PC side landed. The guard primitive already exists:
`ui/component/SingleClick.kt` is written and consumed by `ToolbarBinder`/`EmptyStateBinder`.

### U-21 · The connect control fires on every tap, including during «Подключение…», and there is no way to cancel
**value: high · size: S · never implemented**
`ui/MainActivity.kt:441-444` is a plain `binding.cardConnect.setOnClickListener { animateConnectPress(); handleFabAction() }`;
`handleFabAction()` (`:1896`) branches only on `mainViewModel.isRunning.value`. A second tap during a
connect issues another `startVpnWithPermission()` **and another `scheduleConnectWatchdog()`, which is
`removeCallbacks` + `postDelayed` — i.e. it pushes the 20 s deadline out**. PC equivalent fixed at
`HomeViewModel.cs:193` `if (IsConnecting || _disconnecting)`. *(sweep-audits S28 / D06 / P5.)*

### U-22 · The "no server" guard leaves the connecting state and the watchdog running
**value: high · size: S · never implemented**
`ui/MainActivity.kt:1939-1944`:
```kotlin
private fun startV2Ray() {
    if (MmkvManager.getSelectServer().isNullOrEmpty()) {
        toast(R.string.title_file_chooser)   // a borrowed file-chooser string
        return
    }
```
No `connectInProgress = false`, no `cancelConnectWatchdog()`, no idle shield. Twenty seconds later
the user is told «Не удалось подключиться». PC equivalent fixed at `HomeViewModel.cs:207`.
*(sweep-audits S29 / D07 / P4.)*

### U-23 · Cancelling Android's own VPN consent dialog is reported as a connection failure
**value: high · size: S · never implemented**
`ui/MainActivity.kt:370-374`:
```kotlin
private val requestVpnPermission = registerForActivityResult(StartActivityForResult()) {
    if (it.resultCode == RESULT_OK) { startV2Ray() }
}
```
**No `else`.** A non-`RESULT_OK` result is dropped, leaving `connectInProgress` and the watchdog
exactly as `handleFabAction` left them. *(sweep-audits S30 / D08 / P3.)*

### U-24 · The uptime clock keeps counting the old session while the hero says «Подключение…»
**value: medium · size: S · never implemented**
`ui/MainActivity.kt:2008-2021` `applyRunningState`: the `if (isLoading)` branch `return`s without
calling `stopConnectionTimer()`. Secondary, same area: the switch snackbar
(`:1857-1864`) builds its label from the passed guid but its action calls `restartV2Ray()`, which
resolves `getSelectServer()` — if the selection moves while the bar is up, the label names one server
and the action connects to another. *(sweep-audits S26 / P20.)*

### U-25 · Re-tapping the already-selected server row does nothing at all
**value: low · size: S · never implemented**
`ui/MainActivity.kt:1826-1827` `val selected = MmkvManager.getSelectServer(); if (guid == selected) return`
precedes every piece of feedback. **The desktop half was fixed** (`HomeViewModel.cs` connects
explicitly on a re-tap while disconnected). *(sweep-audits S25 / P19.)*

---

## 5 · LOCALISATION — the largest single item on this list

### U-26 · Every non-Russian locale falls back to Russian, and Russian is shadowed by leftover English
**value: high · size: L · never implemented**
*(merges `sweep-plans` L1 with `sweep-ops` OPS-23.)*

`roadmap-wave3.md §1` and `next-plan.md §J` set the convention "add `values/` + `values-ru/` pairs
together, run lint `MissingTranslation`". The waves inverted it. Measured today:

```
res/values/           22 string files, 1166 <string>
res/values-en/        ABSENT
res/values-ru/         3 files (strings.xml, strings_editors.xml, strings_tv.xml), 768 <string>
res/values-fa/        341   res/values-ar|bn|bqi-rIR|vi|zh-rCN|zh-rTW/  upstream v2rayNG only
```

Most of `res/values/` is **Russian source with no English original** — e.g.
`res/values/strings_settings_hub.xml:25` = «Постоянный VPN и блокировка»,
`res/values/strings_account.xml` opens with a comment that says so outright: *"Russian strings for
the Account / Payments screen"*. There is no locale a non-Russian speaker can select that avoids
these. `ux-recommendations.md §P2-5` calls RU/EN/**FA** first-class; FA is ~690 strings behind.

The other half of the same defect: of the 1166 default keys, **375 are English in `values/` and
translated in `values-ru/`** (`action_stop_service`, `bottom_nav_home`, `connection_test_pending`, …).
So the default locale is a *mix* of new Russian copy and leftover upstream English, and a copy fix
landed in `values/` alone is invisible on a Russian device because `values-ru/` shadows it. **This is
the mechanism by which the copy register's enforcement will silently miss half its targets.**

Two ways out, both real work: create `values-en/` with the English source and keep `values/` as the
English default (correct, larger), or accept Russian-only and strip the dead locale folders (honest,
smaller). Doing neither is the current state, and it is what ships.

### U-27 · No in-app language switcher
**value: low-medium · size: S · never implemented · blocked on U-26**
`next-plan.md §L.5`, `ux-recommendations.md §P2-5`. `grep "setApplicationLocales\|LocaleListCompat"`
→ 0. Today there is nothing to switch *to*.

---

## 6 · CROSS-CUTTING DESIGN LAW THAT NO WAVE OWNS

### U-28 · `res/layout/activity_base.xml` silently defeats the theme-level Cyrillic fix on twelve screens
**value: high · size: S · never implemented · the highest value-per-byte item here**
`res/values/themes.xml:302` already binds `toolbarStyle` to `Widget.Departament.Toolbar` — the fix —
**and the inline attribute at `activity_base.xml:19` overrides it**:

| Line | Ships | Spec |
|---|---|---|
| `:6` | `android:fitsSystemWindows="true"` | A-38: the one inset strategy of `11-app-structure.md` §3.1.5 |
| `:11` | `android:layout_height="?attr/actionBarSize"` | `@dimen/toolbar_height` 56 as a **minimum** — a fixed height on a text-bearing bar clips at font scale 200%, P1 by §14.5 |
| `:19` | `app:titleTextAppearance="@style/ToolbarBrandTitle"` | §4.8: `TextAppearance.App.Title` 16/700 |
| `:28` | `app:indicatorColor="@color/color_fab_active"` | §11.1: colours consumed as `?attr/…` |

`ToolbarBrandTitle` sets `@font/space_grotesk`, which maps 735 codepoints and **zero** in
U+0400-U+04FF. `00-rules.md` §5.1: *"A Russian string found in the brand face is a P1 defect, not a
polish item."* The style's own comment at `styles.xml:276-279` says exactly this and no one acted.

Twelve activities still inflate this host with Russian titles via `setContentViewWithToolbar`:
`BuyTariffActivity`, `ServerActivity`, `ProviderSettingsActivity`, `SettingsActivity`,
`LocalProxyActivity`, `DeviceManagementActivity`, `TaskerActivity`, `LoginActivity`,
`ScannerActivity`, `PaymentHistoryActivity`, `tv/TvSendActivity`, `tv/TvReceiveActivity`. (Sixteen
other screens were migrated to `res/layout/view_toolbar.xml` — the seamless bar is real; these twelve
were never swept.) *(sweep-specs B1.)*

### U-29 · Offline is a designed state in three documents and exists on one screen
**value: high · size: M · never implemented (Android) / incomplete (desktop)**
`00-rules.md` §9.6 and §15 list Offline among the eleven states every screen ships;
`10-design-system.md` §8 expands it; §17.2 makes any missing §15 state **at least P1**.

- **Android: nothing.** `grep -rn 'Нет сети\|могли устареть\|Показаны последние данные'` over `res/`
  and `java/` returns exactly one hit — `res/layout/view_status_strip.xml:84`, a `tools:text`
  preview attribute on a layout nothing inflates (see U-30). No bar, no string, no stale caption, no
  gating of network-dependent actions.
- **Desktop: one surface, with the wrong copy.** `Views/AccountView.axaml:177` `Classes="OfflineBar"`
  bound to `AccountViewModel.cs:217/2270` `IsOffline`. It renders `Common_NetworkError` = «Нет
  подключения к интернету. Проверьте сеть и повторите.» — that is §9.4's *error* string. §9.6 and
  `10-design-system.md` §8 specify **«Нет сети. Показаны последние данные.»** HomeView,
  ServerListView, SettingsView, BuyView, DevicesView, PaymentHistoryView and CheckUpdateView all read
  the network and have none.
- **The stale marker was never written on either platform.** `grep -rn 'могли устареть'` over both
  trees = **0**. *(sweep-specs A1.)*

### U-30 · The persistent status strip is built, correct, and wired to nothing
**value: high · size: M · implemented but nothing reads it**
`res/layout/view_status_strip.xml` exists — 98 lines, correct anatomy, `accessibilityLiveRegion` at
`:78`. **Consumers: zero.** No `<include layout="@layout/view_status_strip">` in any layout, no
`R.layout.view_status_strip` in any Kotlin file, and no `StatusStripBinder.kt` —
`ui/component/` holds exactly nine files (ChipBinder, ComponentSupport, EmptyStateBinder, RowBinder,
SelectionBinder, SingleClick, SkeletonBinder, SubPage, ToolbarBinder). None of
`11-app-structure.md` §8.2's six conditions (offline · подписка истекла · истекает <3 дней · лимит
устройств · провайдер не обновился · ядро/TUN) is implemented on either platform. Absent on desktop
entirely. **This is not covered by the desktop toast refusal (§9 row 1)** — that refused *bottom
transient notifications*; the strip is a top-of-content condition bar and a separate mechanism.
Giving it a binder lands six conditions at once, including half of U-29 and U-31.
*(sweep-specs A2.)* *Files:* `res/layout/view_status_strip.xml`, new `ui/component/StatusStripBinder.kt`.

### U-31 · The subscription-expiry warning: four documents asked, zero code exists
**value: high · size: M · never implemented**
`next-plan.md §C.2` (home banner at ≤3 days), `next-plan.md §F` (WorkManager reminders at 3/1/0 days
with MMKV dedup, gated by `PREF_SUB_EXPIRY_REMINDERS`, tap → plans), `ux-recommendations.md §P1-6`
(«Access expires in {n} days» turning red, "Renew"), `happ-parity-details.md #15` (`sub-expire` /
`sub-expire-button-link` directives). Verified absent:

- `grep -rn "SUB_EXPIRY\|expiry_remind\|ExpiryReminder"` over `app/src/main` → **0**.
- `res/values/strings.xml:347` `sub_days_left` (`%1$s · %2$dd`) has **zero readers** — the string was
  written and never used.
- The meta bar prints a bare date and stops — no threshold, no colour change, no CTA.
- `AccountFragment` / `AccountViewModel` surface no expiry warning.
- `grep -rn "sub-expire"` → 0; the directive is never parsed.

Payments shipped (`ui/BuyTariffActivity.kt`, `PaymentMethodSheet`, `PaymentHistoryActivity`); the
nudge to use them did not. This is the retention feature the plan ranked P1 and the one thing a
paying user notices when it is missing. *(sweep-plans E1.)*

### U-32 · Adaptive layout: no `sw600dp`, the 720 cap has zero readers, the rail has zero instances
**value: medium-high · size: M · never implemented**
`00-rules.md` §3.1 (gutter 16 → 24 at `sw600dp` and at window width ≥ 1000px), §4.1 (content capped
at 720 and centred), §7.7 / §11.4 (bottom nav becomes a `NavigationRailView` at `sw600dp`), §12.3
(*"a stretched phone layout across 1920px is the desktop version of the scaled-up-phone-UI failure"*).

- `res/values-sw600dp/` **does not exist**. `ls -d res/values*` yields values, -ar, -bn, -bqi-rIR,
  -fa, -night, -ru, **-sw360dp-v13**, -vi, -zh-rCN, -zh-rTW.
- `@dimen/content_max_width` 720dp (`res/values/dimens.xml:50`) — the only occurrence in the tree is
  its own declaration.
- `Widget.Departament.NavigationRail` (`styles.xml:947`) is bound at `themes.xml:308` and there are
  **zero `NavigationRailView` instances** — `themes.xml:303` even carries a comment saying so.
- Desktop: `Assets/GlobalResources.axaml:253` `Gutter` = `16,0`, one gutter, no ≥1000 branch. The 720
  cap is applied on AccountView, BuyView, DevicesView, PaymentHistoryView and on **none** of HomeView,
  SettingsView, ServerListView, LoginView, ConnectHeroView, CheckUpdateView, DnsSubView.
*(sweep-specs A3.)*

### U-33 · The list-performance floor: zero `ListAdapter`, zero `DiffUtil`, zero stable IDs, 19 `notifyDataSetChanged()`
**value: high · size: L · never implemented**
`00-rules.md` §11.2 ("List → `RecyclerView` + `ListAdapter` + `DiffUtil`") and §11.5 ("adapters use
stable IDs and `DiffUtil`; **no `notifyDataSetChanged()` on a visible list**") — scored dimension 2
of §17.1 under a "no dimension below 3" ship bar. Measured in
`V2rayNG/app/src/main/java/`: `grep -rn 'ListAdapter\|DiffUtil'` → **0**;
`grep -rn 'setHasStableIds\|getItemId'` → **0**; `grep -rn 'notifyDataSetChanged'` → **19**
(`MainRecyclerAdapter` ×5, `PerAppProxyAdapter` ×2, `AccountFragment` ×2, and one each in
`PaymentsAdapter`, `DeviceAdapter`, `SubscriptionPagerAdapter`, `HomeMetaPagerAdapter`,
`UserAssetActivity`, `SubSettingActivity`, `ServerProxyChainMemberAdapter`, `RoutingSettingActivity`).
The desktop half of the rule is satisfied (`Views/ServerListView.axaml:119-123`,
`VirtualizingStackPanel`).

**Fold in `recon-android-selection` 5.6 (sweep-audits S46):** `viewmodel/MainViewModel.kt:61,430,879`
publish `updateListAction` as a **position**, not a guid — any list mutation between publish and
consume repaints the wrong row. That is the same root cause and the same fix.
*(sweep-specs A4 + sweep-audits S46 merged.)*

### U-34 · `Select` was never built, and 89 controls are waiting on it
**value: medium · size: L · never implemented**
`22-components.md` R15 names Select in the vocabulary, §5 specifies it, §11.2 forbids `Spinner` by
name; `24-tab-conformance.md` Part 1 budgets 85 replacements. Android has **13 `<Spinner>`**
(`activity_server_shadowsocks.xml`, `activity_server_vless.xml`, `activity_server_vmess.xml`,
`dialog_config_filter.xml`, `layout_tls.xml` ×4, `layout_tls_hysteria2.xml` ×2, `layout_transport.xml`
×3) plus 1 `AutoCompleteTextView`; desktop has **76 `<ComboBox>`** across `Views/`. The consumers are
the server-editor family and the TLS/transport partials — screens no wave lists.
*(sweep-specs A5.)*

### U-35 · Segmented control built, zero consumers; the one screen that needs it is unowned
**value: medium · size: S · implemented but nothing reads it**
`Widget.Departament.SegmentGroup` (`styles.xml:523`) and `Widget.Departament.Segment` (`:545`) are
referenced only by two aliases at `:1280-1281` and by nothing in `res/layout`, `res/menu`, `res/xml`
or `java/`. The app's only segmented control, `res/layout/activity_local_proxy.xml:91-165` (A-28,
«Локальный прокси»), is still five `?attr/materialButtonOutlinedStyle` `MaterialButton`s at a fixed
`44dp` height with inline `13sp` text and 14dp margins — three §3.3/§5.2/§1.4.5 defects in one
control. Doing this one screen also retires the largest single block of off-scale values in the tree
(see U-40). *(sweep-specs A6.)*

### U-36 · The `depv://` destinations family and §7.1's route vocabulary
**value: high · size: L · never implemented**
`handleDepvScheme`'s `when (uri.host)` handles connect/open, disconnect/close, toggle, import, add,
routing. **Everything else falls through to `ui/UrlSchemeActivity.kt:132
`else -> toastError(R.string.editor_failed)`.** So every destination link in `11-app-structure.md`
§7.2's second table errors out: `depv://home`, `depv://servers`, `depv://account`,
`depv://account/buy`, `depv://account/devices`, `depv://account/history`,
`depv://subscription/{uuid}`, `depv://settings`, `depv://settings/{group}` — and **`depv://link/{token}`**,
which the spec singles out as closing *"a real product gap: the Telegram-link flow returns the user to
the app today with no route to hand the token to."*

Upstream of that, §7.1's route-identity table (30 stable route strings, *"identical on both
platforms… this is what makes deep links, session restore and the URL schemes page possible"*) exists
on **neither** platform: no route constant table, no parser, no restore. Gates U-37's notification
action and the §7.3 shortcut set. *(sweep-specs B3.)*

### U-37 · The four surfaces outside the app window
**value: medium · size: M · never implemented**
*(merges `sweep-specs` B4 with `sweep-plans` P4, P5, P10 — the same four surfaces from the design
side and the plan side.)*

- **Launcher shortcuts.** Beyond U-12's dead `targetPackage`, all four carry borrowed labels
  (`@string/app_widget_name`, `@string/menu_item_import_config_qrcode`, `@string/toast_services_start`,
  `@string/toast_services_stop`) — none of the Russian labels §22.4 names. **The two specs disagree
  and need an owner call:** `11-app-structure.md` §7.3 drops start/stop and adds «Серверы» →
  `depv://servers` (3 shortcuts, and depends on U-36); `32-master-plan-android.md` §22.4 says KEEP
  all four with Russian labels.
- **QS tile.** `service/QSTileService.kt:25-37` sets `qsTile?.label` only and handles
  `STATE_INACTIVE` / `STATE_ACTIVE` — `grep "setSubtitle\|STATE_UNAVAILABLE"` → **0**. So §22.2's
  three-row label/subtitle/icon matrix does not exist, the `Unavailable` → «Нет подписки» state does
  not exist, and `next-plan.md §H.1`'s third state (подключается) reads as already-off.
- **Home-screen widget.** `res/layout/widget_switch.xml` is byte-for-byte pre-redesign: `:19-20` a
  45dp icon, `:30` `@style/TextAppearance.AppCompat.Small`, `:31`
  `android:textColor="@android:color/white"` (a raw literal §1.5's hex-only grep does not catch).
  `receiver/WidgetProvider.kt` sets exactly two things, an icon and a background —
  `grep "setTextViewText\|remarks\|flag"` over it → **0**. `next-plan.md §H.2` and
  `ux-recommendations.md §P1-2` want server name + flag + state + optional live ↑/↓. Tap-to-toggle
  does work (subject to U-12).
- **Notification.** `handler/NotificationManager.kt:160,165` builds two actions, Stop and Restart.
  §22.1 and §7.3 both require **«Сменить сервер»** → `depv://servers` (blocked on U-36);
  `ux-recommendations.md §P1-5` wants Disconnect · Switch server · Pause 5 min. Also
  `notification-design.md §4`'s `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` is not
  set. *(Everything else in `notification-design.md` landed — chronometer, `IMPORTANCE_LOW`,
  `PRIORITY_LOW`, the versioned channel id, flag in the title. Do not re-open those.)*

### U-38 · ICON GAP — `ic_arrow_back`, `ic_warning`, `ic_error` were filed with an owner who does not exist
**value: medium · size: S · never assigned**
Not an inference — the waves wrote it into the source.
`res/layout/view_toolbar.xml:59-61`: *"`res/drawable` has no back glyph. `22-components.md` 12.1 asks
for a 24dp `ic_arrow_back`; until the icon owner lands it, the 24dp chevron is mirrored."* It ships as
`app:icon="@drawable/ic_chevron_right"` + `android:scaleX="-1"` on **every** sub-page in the app.
`ls res/drawable | grep -i "arrow\|warning\|error"` returns exactly one file:
`ic_arrow_drop_down.xml`. `res/layout/view_status_strip.xml:33-35` records the second half: no
`ic_warning`, no `ic_error`, so the strip's warning and error severities carry the info glyph —
against §6.3, "colour is never the only signal". *(sweep-specs D1.)*

### U-39 · STRING GAP — «Назад» is the one hardcoded `contentDescription` in the tree
**value: medium · size: S · never assigned**
`res/layout/view_toolbar.xml:70` `android:contentDescription="Назад"`, with the comment at `:63`:
*"there is no «Назад» in `res/values*/strings*.xml` and this wave may not add one; the literal below
wants to become `@string/cd_back`."* Every other `contentDescription` in `res/layout/` is a
`@string/…` or explicitly `tools:ignore`. Consequence: the back affordance on every migrated sub-page
carries an unlocalised accessible name, against §10.7 / §14.3. *(sweep-specs D2.)*

### U-40 · The off-scale and inline-face debt lives mostly on screens no wave lists
**value: medium · size: M · never assigned**
Off-scale `dp` literals per layout, measured today:

| File | Off-scale dp | Owner |
|---|---|---|
| `res/layout/activity_local_proxy.xml` | 110 | A-28 — **nobody** |
| `res/layout/activity_provider_settings.xml` | 80 | A-24 — **nobody** |
| `res/layout/fragment_settings_tab.xml` | 42 | settings tab, in flight |
| `res/layout/activity_main.xml` | 31 | home, in flight |
| `res/layout/activity_account.xml` | 19 | account, in flight |
| `res/layout/layout_servers_header.xml` | 11 | servers, in flight |
| `res/layout/layout_subscription_meta_bar.xml` | 10 | home, in flight |
| `res/layout/activity_tv_receive.xml` / `activity_tv_send.xml` | 4 / 3 | A-31 / A-32 — **nobody** |
| `res/layout/widget_switch.xml` | 2 | A-44 — **nobody** (U-37) |
| `res/layout/item_qrcode.xml` | 2 | A-40 — **nobody** |

Roughly **200 of the ~330** sit on screens no wave lists, and the two biggest files are one A-28 and
one A-24. The same split holds for the `android:fontFamily` / `android:textSize` hits (§5.2 "roles,
not sizes"): the non-in-flight offenders are `activity_local_proxy.xml`,
`activity_provider_settings.xml`, `activity_tv_receive.xml`, `activity_tv_send.xml`,
`layout_transport.xml`, `item_buy_tariff.xml`. Clean and to be kept clean: raw hex in layouts **0**,
`textAllCaps` **0**. *(sweep-specs B6.)*

### U-41 · Form law §7.4 — blur validation, autofill, password toggles
**value: medium · size: M · partially implemented, no owner**
`setOnFocusChangeListener` appears in exactly **one** file (`ui/ProviderSettingsActivity.kt`) against
**19** `addTextChangedListener`/`doAfterTextChanged`/`doOnTextChanged` — i.e. validation is
per-keystroke, which §7.4 forbids. `android:autofillHints` = **4** occurrences in all layouts.
`endIconMode="password_toggle"` = **2** (`activity_login.xml:174`, `dialog_webdav.xml:87`); other
secret fields have none. Part 1's "58 bare `EditText` → one text field": **46 bare `<EditText>`**
remain against 30 `TextInputEditText`. *(sweep-specs A7.)*

### U-42 · The accessibility pass was never started
**value: medium · size: M · never implemented**
`grep -rn "announceForAccessibility"` over the whole Android tree → **0**: no connection state is
ever announced, on an app whose primary control is a state machine.
`grep -rc labelFor res/layout/` → **0 files**; 57 `EditText`s have no accessible name;
`contentDescription` appears in 13 of 82 layouts. *(sweep-plans P2 + sweep-audits S47 +
`ux-recommendations` §P2-4 + `next-plan` §J.2 — four sources, one item.)*

### U-43 · A-42 Tasker
**value: low · size: S · never implemented**
`ui/TaskerActivity.kt:27` still `setContentViewWithToolbar(binding.root, …, title = "")`. A-42 / §22.5:
give it «Действие Tasker», put its `Spinner` on the Select (U-34), tokenise. *(sweep-specs B5.)*

### U-44 · Eleven built, correct artefacts with zero consumers
**value: medium · size: S each · implemented but nothing reads it**
Not missing work — work that will silently rot unless someone is told to place it.

| Artefact | Consumers | Note |
|---|---|---|
| `res/layout/view_status_strip.xml` | 0 | U-30 |
| `res/layout/view_chip.xml` | 0 | `ChipBinder` has one real consumer, `LogcatRecyclerAdapter.kt:49` |
| `res/layout/view_meter.xml` | 0 | Its header says it exists to end the meta-bar defect (an 11sp label over a moving accent fill at 2.9:1). `layout_subscription_meta_bar.xml` still ships |
| `res/menu/menu_bottom_nav.xml` | 0 | `activity_main.xml` still hand-rolls a `LinearLayout` bar |
| `Widget.Departament.SegmentGroup` / `.Segment` | 0 | U-35 |
| `Widget.Departament.NavigationRail` | 0 | U-32 |
| `Widget.Departament.Row.Toggle` / `.Row.Destructive` | 0 | Archetypes 4 and 5 of §8 |
| `Widget.Departament.Tile.Accent` / `.Tile.Destructive` | 0 | Two of D-5's three tiles |
| `Widget.Departament.Card.Pressable` / `.Card.Selectable` | 0 | The pressable/selectable treatment of R5 |
| `Widget.Departament.Skeleton.Block` | 0 | `.Skeleton.Bar` has 1 |
| `@dimen/content_max_width` | 0 | U-32 |

(`Widget.Departament.Snackbar`, `.Sheet`, `.Toolbar.Brand`, `.Divider` and the dialog styles show
zero *layout* references but are correctly bound through `themes.xml:302-333` — those are fine.)
*(sweep-specs C.)*

---

## 7 · PRODUCT FEATURES SPECIFIED AND NEVER BUILT

### U-45 · `PREF_UTLS_FINGERPRINT` — the one new pref the circumvention design introduces
**value: high · size: S · never implemented · four documents**
`circumvention-settings-design.md` §1.2/§5.2, `strategy-russia-2026.md` §3.3 / R0.2,
`gap-desktop-to-android.md` W4, `sweep-audits` S35. `grep -rn "UTLS_FINGERPRINT\|utls_fingerprint\|defFingerprint"`
over `app/src/main` → **0**. `core/CoreOutboundBuilder.kt:564` is still
`fingerprint = profileItem.fingerPrint.nullIfBlank(),` with no global fallback: **a node imported
from a share link without `fp=` runs with an empty uTLS fingerprint** — the exact JA3/JA4 flag §3.3
names as a direct detection signal. The value array already exists (`res/values/arrays.xml:72`
`streamsecurity_utls`). This is one `?:` plus one settings row, and it is on this list four times.

### U-46 · `handler/BypassPresets.kt` and the four presets
**value: high · size: M · never implemented**
`circumvention-settings-design.md` §3, §5.3-5.5: Standard / Russia-strict-DPI / Iran / Low-latency,
each a data-driven `Map<String,String>` applied through `MmkvManager.encodeSettings` +
`SettingsChangeManager.makeRestartService()`. `grep "BypassPresets\|BYPASS_PRESET\|pref_bypass"` →
**0**; `res/xml/pref_bypass.xml` does not exist. The individual knobs are all editable
(fragment length/interval/packets in `res/xml/pref_settings.xml:192-234`, mux/fragment toggles in
`MainActivity`) — but a non-expert has to know what a ClientHello is to use them, which is precisely
the problem the document exists to solve. *(sweep-plans C2.)*

### U-47 · Russia mode and periodic liveness re-check
**value: high · size: M · never implemented**
`roadmap-wave3.md` §6a/§6d, `strategy-russia-2026.md` R0.2/R1.2, `next-plan.md` §I.3.
`grep "RUSSIA_MODE\|LIVENESS\|liveness"` → **0**. The health check is still the one-shot post-connect
probe (already fixed for the re-probe confirmation) — nothing runs on a 30-60 s cadence while
connected, so the RU "TLS freeze" (passes TCP, dies at ~16 KB) is caught once, at connect time, and
never again. *(sweep-plans C3.)*

### U-48 · No per-node liveness memory / temporary avoid-set
**value: medium · size: M · never implemented**
`next-plan.md §I.3` ("память о плохих нодах" with a TTL), `new-modules-proposals-3.md` N9 ("avoid this
server for 30 min"). `grep "avoidUntil\|avoid_until\|nextServer"` → 0. Auto-fallback excludes exactly
one guid for exactly one attempt (`fastConnectExcludeGuid`); a node that flaps is re-selected on the
next connect. *(sweep-plans C4.)*

### U-49 · Auto-connect on app launch
**value: high · size: S · never implemented**
`next-plan.md §E.1`: `PREF_AUTO_CONNECT_ON_LAUNCH`, mirroring the existing `BootReceiver` gate.
`grep -rn "AUTO_CONNECT\|autoConnectOnLaunch"` over `app/src/main` → **0**. Boot autostart works;
opening the app with a server already selected still requires a tap. Small, and the most-noticed
missing behaviour in the whole corpus. *(sweep-plans U1.)*
*(§E.2, the kill-switch surface, DID land — deep link to `Settings.ACTION_VPN_SETTINGS`,
`row_always_on`, `SUPPORTS_ALWAYS_ON` at `AndroidManifest.xml:240`. Do not re-open it.)*

### U-50 · First-run onboarding and the VPN-permission priming sheet
**value: high · size: M · never implemented**
`roadmap-wave3.md` §2a + §2c, `ux-recommendations.md` §P0-1.
`grep "ONBOARDING_SHOWN\|OnboardingActivity"` → **0**. What exists is
`MainActivity.updateOnboardingLogin()` — the *home empty-state card*, a different surface that only
appears when there are zero servers. A fresh install has no first-run explanation and no trust note.
`MainActivity.startVpnWithPermission():1931` hands the raw system `VpnService.prepare()` dialog
straight to the user; `grep "perm_vpn_priming\|VPN_PERMISSION_PRIMED"` → 0.
*(sweep-plans U2 + U3 merged.)*

### U-51 · No staged connect status, and the haptics cannot be turned off
**value: medium · size: M · never implemented**
`ux-recommendations.md §P0-2` (Preparing → Handshaking → Testing route → Connected) and
`roadmap-wave3.md §5.1` (three states). Only one intermediate state exists —
`MainActivity.kt:2018` sets `connection_connecting`. The haptics from §5.4 **did** land
(`util/MotionUtils.kt:56,63`) but without `PREF_HAPTICS` (`grep PREF_HAPTIC` → 0), so they cannot be
turned off, which §5.4 required. *(sweep-plans U4.)*

### U-52 · No structured `ConnectError` taxonomy or recovery sheet
**value: medium · size: M · never implemented**
`ux-recommendations.md §P0-4, §5` — seven named classes each with a plain-language cause and a primary
fix button. `grep "ConnectError"` → 0 (only `auth/ApiError.kt`, the network-API taxonomy). Failures
still surface as toasts. Directly compounds U-22/U-23, where the toast is a borrowed file-chooser
string. *(sweep-plans U5.)*

### U-53 · No last-known-good server, no favourites, no synthetic "Fastest / Auto" row, no hot-swap sheet
**value: medium · size: M · never implemented**
- `ux-recommendations.md §P0-3` one-tap reconnect: `grep "lastConnected\|last_connected"` → 0.
- §P1-7 / `happ-parity-details.md #9` favourites: `grep "favourit\|favorite"` → 0. Fast-connect exists
  only as a menu action. `EConfigType.POLICYGROUP` and its balancer are fully wired
  (`ui/MainRecyclerAdapter.kt:261` even labels it "Auto"), but no subscription auto-creates one and it
  is never surfaced as a first "Hybrid (Auto-select)" row — the presentation half of #9 that the doc
  says is all that is left.
- §P1-1 / signature moment §2.3 in-place hot-swap sheet: `grep "ServerSwitchSheet"` → 0.
*(sweep-plans U6 + U7 + U8 merged.)*

### U-54 · Trusted/untrusted Wi-Fi auto-connect
**value: low · size: L · never implemented**
`ux-recommendations.md §P1-3`. `grep "trustedWifi\|SSID"` → 0. Ranked P1 by the doc but it is the
largest single item in it — recorded so it is a decision rather than an oversight.

### U-55 · Subscription directives: in-body `#` lines, `profile-update-interval`, `sub-info-*`, `fallback-url`
**value: medium · size: S (first two) / M-L (rest) · never implemented**
- **In-body directives other than lock state.** `happ-parity-details.md §0`: *"the departament
  Remnawave panel may emit `#announce:` / `#support-url:` lines at the top of the base64-decoded body,
  so the parser must scan leading `#` lines too."* The **headers** are all read
  (`util/HttpUtil.kt:319-323` — announce, support-url, profile-web-page-url, profile-title,
  profile-hidden). The **body scan**, `template/TemplateManager.resolveBodyDirective():90-105`, matches
  `profile-hidden|hidden|locked` and nothing else. A panel that ships directives in the body loses its
  announce banner and support button silently.
- **`profile-update-interval` never read.** `grep -rn "profile-update-interval"` → **0**.
  `HttpUtil.UrlContentResult:266-269` captures five headers and not this one, so the operator cannot
  push an auto-update cadence. (`happ-parity-details` #7/#18, `hidden-templates-design` §2.4.)
- **`sub-info-*` rich block, `fallback-url`/`new-url`/`new-domain`.** `grep "sub-info\|fallback-url\|new-domain"`
  → 0. #17 is the same capability as U-56 arriving from the Happ side.
- **`sort-order` never honoured.** `next-plan.md §I.2`; the only sort is the user's own
  `PREF_SERVER_SORT_ORDER`. *(sweep-plans H1, H2, H3, C5.)*

### U-56 · Delivery resilience stopped half-way: no mirrors, no fetch-through-tunnel, no out-of-band fallback
**value: high · size: M-L · never implemented**
`strategy-russia-2026.md` §3.4 #1-#3 / R1.1, `happ-parity-details.md #17`. `SubscriptionItem` carries
a single `url`; `grep "mirror\|altUrl\|fallbackUrls"` finds only unrelated prose. `AngConfigManager`
has a proxy retry (`:912-918`, `SettingsManager.getHttpPort()`) but no ordered mirror list and no
deliberate "pull the next update inside the live tunnel" path. If the single sub URL is blocked, the
user has no route back.
**Done, do not re-open:** §3.4 #4 *never wipe a working list on a failed fetch* **is** implemented —
`AngConfigManager.kt:612-620` stages the parse and only deletes once `staged.isNotEmpty()`.
*(sweep-plans D1.)*

### U-57 · No RU-aware diagnostics panel, and the log viewer has a door but no redaction
**value: medium · size: M · never implemented**
`strategy-russia-2026.md` §2 #14 / R3.1, `ux-recommendations.md §P2-2` (guided "Having trouble?" +
**redacted** share-debug), `new-modules-proposals-3.md N11` (the redaction contract).
`grep -i diagnos` finds only the payment-error dialogs. `LogcatActivity` now **has** entry points, and
it has no redaction filter — combined with U-07 (`AngConfigManager.kt:892` logs the full subscription
URL) that is a token on screen, one "share" away from a support chat. *(sweep-plans D3.)*

### U-58 · TV Phase A: D-pad focus, overscan, landscape
**value: medium · size: L · never started**
`smart-tv-transfer-design.md` §4 Phase A, `new-modules-proposals.md M6`,
`master-requirements-audit.md` §12. Phase B (the transfer) is done and reachable. Phase A is not:
```
res/values-television/  ABSENT    res/layout-television/  ABSENT
res/layout-land/        ABSENT    res/values-sw600dp/     ABSENT
nextFocus* in res/layout/ → only layout_tls.xml (10) and layout_tls_hysteria2.xml (2)
```
`AndroidManifest.xml:62` still declares `LEANBACK_LAUNCHER`, so the app ships on TV home screens with
a phone-only focus model. *(sweep-plans P1.)*

### U-59 · `onTrimMemory` / `ComponentCallbacks2` never implemented
**value: medium · size: S · never implemented**
`memory-panel-design.md` §2.6 — the central `trimCaches(level)` entry point all bounded caches route
through. `grep -rn "onTrimMemory\|ComponentCallbacks2"` over `app/src/main` → **0**;
`AngApplication.kt` overrides only `attachBaseContext` and `onCreate:32`. §2.5's suggestion to revive
a *trimming* (not connection-dropping) response also went nowhere — `service/CoreVpnService.kt:89-91`
is still the commented-out `onLowMemory()` the doc describes. *(sweep-plans P3.)*

### U-60 · No flag on the home current-server label
**value: low-medium · size: S · never implemented**
`master-requirements-audit.md §1d`, `next-plan.md §L.1`. `ui/MainActivity.kt:2312-2316`
`selectedServerName()` returns `remarks` verbatim. `FlagUtil` is applied in exactly two places —
`ui/MainRecyclerAdapter.kt:210-211` and `handler/NotificationManager.kt:143` — so the row and the
notification resolve a flag and **the home hero, the most-looked-at surface in the app, does not**.
*(sweep-plans P6. Note: the home screen is in flight; if that wave does not pick this up it stays open.)*

### U-61 · App-icon alias chooser / stealth icon
**value: medium · size: S-M · never implemented**
`master-requirements-audit.md §5c`, `next-plan.md §L.4`, `ux-recommendations.md §P2-7`,
`new-modules-proposals.md M2`. `grep -c "activity-alias" AndroidManifest.xml` → **0**. For the RU/FA
audience the documents frame this as a safety feature, not personalisation. *(sweep-plans P7.)*

### U-62 · Daily traffic statistics
**value: medium · size: M · never implemented**
`roadmap-wave3.md §4`. `grep "DailyTraffic\|SHOW_DAILY_TRAFFIC\|TrafficStatsActivity"` → 0. The
integration point the doc identified is sitting there ready: `handler/NotificationManager.kt:284`
already iterates `queryAllOutboundTrafficStats()` and computes the per-interval deltas the design
wanted to accumulate. *(sweep-plans P9.)*

### U-63 · DNS-leak discipline never verified or enforced
**value: medium · size: M · never implemented**
`next-plan.md §I.1`, `strategy-russia-2026.md §3.5`, `new-modules-proposals.md M11`. No leak test, no
IPv6 route/block control. `PREF_PREFER_IPV6` has readers (`core/CoreOutboundBuilder.kt:682`,
`core/CoreConfigManager.kt:1002`) and **no writer** — see U-70. *(sweep-plans C6.)*

### U-64 · The portable core-contract document was never written
**value: low · size: S · never written**
`next-plan.md §K` acceptance criterion 3: *"Есть краткий документ «портируемый core-контракт» (API +
формат подписки + конфиг)"*. No such file in `docs/`. Criteria 1 and 2 hold — endpoints are
centralised in `auth/BackendConfig.kt:33,80` and `AuthManager`/`SubscriptionSyncManager` carry no
`Activity`/`View` references. Now that a desktop client exists, this is the document that would keep
the two API surfaces honest with each other. *(sweep-plans P11.)*

---

## 8 · THE PORTS THAT WERE ORDERED AND NEVER STARTED

`gap-desktop-to-android.md`'s PORT NOW list is **1 of 8 done**, and the one that closed did so
incidentally (W3, FakeIP — it landed on `settings/advanced`, not the `settings/dns` page the order
named). Three of the six remaining have **ratified copy already written**.

| # | Order | State today |
|---|---|---|
| **U-65** | **A1 — auto-fallback → desktop.** *value: high · size: M* | `grep -rni "autofallback\|auto_fallback"` over `/home/user/v2rayN` = **0 hits**. The gap doc calls this "the single most user-visible behaviour difference"; `12-settings.md` 5.9 row f already declares the row, default on, platform **both**, desktop binding **NEW** |
| **U-66** | **A3 — provider controls → desktop, and the page they live on is unreachable.** *value: medium · size: M* | `grep "PingOnLaunch\|PingOnUpdate\|NotifyOnUpdate"` over the desktop tree = **0**. `Views/ProviderSettingsPage.axaml.cs` exists as an `ISubPage` and has **zero construction sites** — `Views/SettingsView.axaml.cs:50` wires `new UrlSchemesPage()` and nothing wires this one (`11-app-structure.md` 10.2 verdict WIRE) |
| **U-67** | **W1 — throughput test per server.** *value: medium · size: M* | `grep "DoSpeedTest\|throughput\|Мбит/с\|speedMbps"` over `java/` + `res/` = **0**. `handler/SpeedtestManager.kt` still has only http/icmp/tcp/socket paths |
| **U-68** | **W2 — latency timeout key.** *value: low-medium · size: S* | Half done: the *address* now has an editor (`pref_delay_test_url` in `res/xml/pref_settings.xml:251`). `grep PREF_PING_TIMEOUT` = **0**; `handler/SpeedtestManager.kt:305` is still `timeoutMs: Int = 3000` with its one caller passing no override |
| **U-69** | **W5 — import routing rules from a URL · W6 — WebDAV connection test.** *value: low-medium · size: S each* | `grep "import_rulesets_from_url\|importFromUrl\|checkConnection"` over `java/` + `res/` = **0**. The clipboard/QR/predefined routing entries exist; the URL one does not. `ui/BackupActivity.kt` still saves four WebDAV fields with no verification. Copy for both is already drafted (`gap` §9.1, `12-settings.md` 5.12) |

*(A6, `subscription-userinfo` header parsing on desktop, **landed** — `ServiceLib/Services/DownloadService.cs:322`
+ `ServiceLib/Handler/SubscriptionHandler.cs:281-343`. Do not re-file it.)*

---

## 9 · RESIDUE, DEAD CODE AND SMALL LOGIC HOLES NOBODY CLOSED

### U-70 · ~10 preference keys have a live reader and no writer — worst is `PREF_CONFIRM_REMOVE`
**value: high · size: S · implemented but nothing writes it**
`res/xml/pref_settings.xml:25-28` lists these as deliberately dropped editors; the readers survived.
**`PREF_CONFIRM_REMOVE` is read at five sites with no default supplied** —
`ui/MainActivity.kt:1801`, `ui/ServerActivity.kt:669`, `ui/ServerProxyChainActivity.kt:206`,
`ui/SubEditActivity.kt:290`, `ui/SubSettingActivity.kt:180` — i.e. effectively `false`. **A fresh
install deletes servers and subscriptions with no confirmation and cannot turn the confirmation on.**
Also reader-no-writer: `PREF_SHOW_MEMORY` (so the memory panel cannot be shown at all),
`PREF_PREFER_IPV6` (U-63), `PREF_GROUP_ALL_DISPLAY`, `PREF_MUX_XUDP_QUIC`, `PREF_DYNAMIC_SOCKS_PORT`.
Either restore editors or delete the readers — but not neither. *(sweep-audits S41 / D16 residue.)*

### U-71 · A search with zero matches empties Home into the onboarding state and hides the bottom nav
**value: high · size: S · never implemented**
`viewmodel/MainViewModel.updateCache():246-275` builds `serversCache` as the **filtered** list
(keyword regex + protocol chip). `ui/MainActivity.kt:1123` (`updateHomeEmptyState`) and `:3015` both
gate on `mainViewModel.serversCache.isEmpty()`, and `ui/ServersFragment.kt:172` does too. Type a
non-matching query and the app looks freshly installed. The protocol-filter chips
(`MainViewModel.applyProtocolFilter`) are a **second** way in. The fix pattern already exists in the
same file — `prepareMenu` reads the store rather than the cache. *(sweep-audits S42 / D15 /
`hunt-android-cold-start` F3.)*

### U-72 · `SubSettingActivity` is declared in the manifest and reachable from nowhere
**value: medium · size: S · implemented but unreachable**
`grep -rn "SubSettingActivity"` outside its own file = **0**; `AndroidManifest.xml:110` declares it.
`SettingsActivity`, `LogcatActivity` and `CheckUpdateActivity` all got doors in the covered work; the
provider-list editor did not, so D17 is two-thirds done. *(sweep-audits S40.)*

### U-73 · The dead «Привязать Telegram» banner is still shipped
**value: low · size: S · delete, do not repair**
`ui/MainActivity.kt:1472` `header.groupLogin.isVisible = false` unconditionally, while `:1502`
`updateLoginCtaVisibility()` requires `!isLoggedIn()` and the header root is only visible when logged
in. `ctaDismissed` (`:263`), both handlers and two strings are dead weight.
`verify-link-telegram-cta-unreachable.md` CONFIRMED it and corrected the severity to *low — delete, do
not repair*. A dedicated adversarial verification said yes and nothing happened.
*(sweep-audits S43 / D22.)*

### U-74 · `R.id.sub_update` dispatches a menu id `MainActivity` never inflates
**value: low · size: S · dead branch**
`ui/MainActivity.kt:2513` handles `R.id.sub_update`, declared only in
`res/menu/action_sub_setting.xml`. *(sweep-audits S44 / D21.)*

### U-75 · `customProtoCache` is never invalidated
**value: medium · size: S · never implemented**
`ui/MainRecyclerAdapter.kt:298-317`: a `HashMap<String, CustomProtoInfo?>` keyed by guid, written once
(`:317`) and never cleared on rebind, refresh or subscription update. A guid reused after a
subscription refresh renders the previous profile's protocol chip. *(sweep-audits S45 /
`audit-android-ui` A13.)*

### U-76 · Deleted subscriptions resurrect
**value: medium · size: S · never implemented**
`handler/MmkvManager.kt:397-406` `initSubsList()` rebuilds `KEY_SUB_IDS` from `subStorage.allKeys()`
**whenever the list is empty**, in arbitrary order. Delete your last subscription and it comes back.
*(sweep-audits S9.)*

### U-77 · Dead payload in both bundles
**value: low · size: S · delete**
- `V2rayNG/app/src/main/res/font/montserrat_thin.ttf` (152 KB) — zero references in `res/` or `java/`.
- `V2rayNG/app/src/dev/` and `app/src/pre_release/` — one `strings.xml` each, matching no flavour in
  the `distribution` dimension, never built.
- `v2rayN.Desktop/Assets/Fonts/NotoSansSC-Regular.ttf` (**10.5 MB**) — upstream's Simplified-Chinese
  face, embedded via `<AvaloniaResource Include="Assets\**" />` into a Russian-UI product. This is the
  single largest removable item in the desktop bundle.
- `auth/AuthTokenStore.kt:179 getExpiresAt()` — zero call sites; nothing checks the 7-day JWT lifetime
  client-side. Consistent with "no refresh endpoint", so this is dead code to delete, not a feature.
*(sweep-ops OPS-24 + review-05 residue.)*

---

## 10 · IDENTITY: WHERE THE APP POINTS ITS USERS

### U-78 · «Проверить обновления» offers the user a different product's APK — and the button is live
**value: high · size: M · never implemented**
`AppConfig.kt:145` `const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"`.
`handler/UpdateCheckerManager.kt:19-21` is the only consumer; on a hit it calls
`getDownloadUrl(latestRelease, abi)` and offers the upstream APK. This was harmless while
`CheckUpdateActivity` had no launch site — **the entry-point restoration wired two**
(`ui/MainActivity.kt:3179` `rowCheckUpdate`, `ui/AboutActivity.kt:91`). Giving it a door shipped the
defect. There is no Departament release feed for it to point at, and no signature verification either
(`strategy-russia-2026.md` §4.2 / R1.4: `grep "signature\|checksum\|verifySignature"` over
`UpdateCheckerManager.kt` → 0) — on a premise that neither app store is a reliable RU channel.
*(sweep-ops OPS-01 + sweep-audits S10 + sweep-plans D2 merged.)*

### U-79 · The desktop self-update will overwrite Departament with stock v2rayN
**value: high · size: M · implemented and currently unreachable**
`ServiceLib/Global.cs:674` `{ ECoreType.v2rayN, "2dust/v2rayN" }` → `CoreInfoManager.cs:99,108-118`
`DownloadUrlWin64 = urlN + "/download/{0}/v2rayN-windows-64.zip"`;
`ServiceLib/Services/UpdateService.cs:11 CheckUpdateGuiN` downloads it and
`ServiceLib/Common/Utils.cs:818` hands it to AmazTool for in-place replacement. Because
`v2rayN/Directory.Build.props:4` still reads `<Version>7.23.4</Version>` — **upstream's number** —
any upstream release above 7.23.4 reads as an upgrade. `CheckUpdateViewModel` is registered in
`SimpleViewLocator.cs:19` and I found no navigation to it in the new shell, so this is *unreachable
today* — one menu row away from live, and the Android half (U-78) already is.
*(sweep-ops OPS-02.)*

### U-80 · «О приложении» ships four rows that open upstream, including the privacy policy
**value: high · size: S · never implemented**
`ui/AboutActivity.kt`, all resolving through `AppConfig.kt:144-150`:

| Row | Line | Destination |
|---|---|---|
| Исходный код | `:97` → `APP_URL` | `github.com/2dust/v2rayNG` |
| Обратная связь | `:103` → `APP_ISSUES_URL` | `github.com/2dust/v2rayNG/issues` |
| Telegram | `:109` → `TG_CHANNEL_URL` | `t.me/github_2dust` — upstream's channel, not `@departamentvpn` |
| Политика конфиденциальности | `:121` → `APP_PRIVACY_POLICY` | `raw.githubusercontent.com/2dust/v2rayNG/master/CR.md` |

That last one is customer-facing legal text: `CR.md` at this repo's root opens
`**v2rayNG 隐私权政策** / 本政策自2023年11月17日起施行` — 2dust's Chinese-language privacy policy for a
different operator, dated 2023-11-17. A Departament user tapping «Политика конфиденциальности» reads
it. **This is a logged parity gap:** the desktop already fixed the equivalent
(`v2rayN.Desktop/Views/AboutPage.axaml.cs:24` builds its Telegram link from
`BackendConfig.BotUsername`); Android has the same value available
(`auth/BackendConfig.kt:36` → `BuildConfig.BOT_USERNAME` = `departamentvpnbot`) and does not use it.
Adjacent: `.github/ISSUE_TEMPLATE/bug_cn.md` ("v2rayNG程序问题") and `config.yml` (contact link to
`v2fly/v2ray-core`) are upstream's Chinese templates. *(sweep-ops OPS-05 + OPS-21 + sweep-audits S16.)*

---

## 11 · DESKTOP HYGIENE — the tier that was deprioritised and never returned to

All from `audit-desktop-core` / `audit-desktop-ui` / `hunt-persistence`, all re-verified unchanged.
Grouped because one wave can take the lot.

| # | Item | Value / size |
|---|---|---|
| **U-81** | **`AuthTokenStore.Persist` is a non-atomic whole-file write.** `v2rayN.Desktop/Account/AuthTokenStore.cs:194` `File.WriteAllBytes` straight over the live session file. A crash mid-write leaves an unreadable session and the user silently signed out. Compounds with the next row | high · S |
| **U-82** | **The AES key derives from a machine seed that can silently change, with no re-key path.** `AuthTokenStore.cs:206` keys off `MachineSeed()`, which falls back to `MachineName\|UserName` when `MachineGuid`/`machine-id` is unreadable. A hostname change makes the session permanently undecryptable and nothing tells the user | high · M |
| **U-83** | **A corrupt-but-readable config resets every preference and then overwrites the file.** `ServiceLib/Handler/ConfigHandler.cs:19-36` — the guard at `:29-33` covers only the *unreadable* case; non-empty malformed JSON deserialises to `null`, falls to `config ??= new Config()` at `:36`, and the next save writes defaults over the user's file | high · S |
| **U-84** | **Traffic stats read zero whenever the provider tags its outbound anything but `proxy*`.** `ServiceLib/Services/Statistics/StatisticsXrayService.cs:96` `if (key.StartsWith(Global.ProxyTag))`. Departament custom nodes keep the template's tags as authored, so a template naming its outbound `VLESS-out` yields a permanently 0 KB/s speed widget with no error. `CoreManager._runningProxyTag` already computes the right answer | medium-high · S |
| **U-85** | **The Xray `api` inbound is grafted onto every config for a feature that is switched off.** `ServiceLib/Manager/CoreManager.cs:55` `EnableHotSwapTier = false`, yet `ServiceLib/Handler/CoreConfigHandler.cs:240` calls `GraftXrayApi(root)` unconditionally — a mandatory `dokodemo-door` inbound on a TOCTOU-chosen port whose only consumer is disabled. Pure added connect-failure surface | medium · S |
| **U-86** | **A blocking `.Wait()` on the startup thread for a paged SQLite migration.** `ServiceLib/Manager/AppManager.cs:136-139` `Task.Run(async () => await MigrateProfileExtra()).Wait()` inside `InitComponents` | medium · S |
| **U-87** | **`ClientWebSocket` aborted but never disposed on every reconnect.** `StatisticsSingboxService.cs:35,43,57,94` — `Abort()` then `= null` | medium · S |
| **U-88** | **The API `HttpClient` never refreshes DNS and inherits the app's own system proxy.** `v2rayN.Desktop/Account/DepartamentApiClient.cs:24-33` — `static readonly HttpClient` over a plain `HttpClientHandler`, no `PooledConnectionLifetime`, `UseProxy` at its default `true`. Also `:56` hard-codes `x-device-os: "windows"` on a client that ships for Linux and macOS | medium · S |
| **U-89** | **33 Rx `Subscribe(async …)` handlers swallow every exception.** `grep -rn "Subscribe(async" --include=*.cs` = **33**, re-counted today | medium · M |
| **U-90** | **A sub-page swallows clicks for 300 ms before it is visible.** `Views/MainWindow.axaml.cs:1156` sets `subPageHost.IsVisible = true` then fades opacity 0 → 1; Avalonia hit-tests on `IsVisible`, not `Opacity` | medium · S |
| **U-91** | **`SettingsView.axaml:68` shadows the promoted `TextBox.IncyField`** at `GlobalResources.axaml:635`. `verify-settingsview-incyfield-shadowing.md` CONFIRMED it (severity corrected high → medium) and nothing happened | medium · S |
| **U-92** | **`SubscriptionMetaView` still assigns three static dark-theme literals.** `Views/SubscriptionMetaView.axaml.cs:30-32` applied directly at `:400, 415, 423, 429, 637, 744, 747` to the pin icon and expiry text. The `ResolveBrush(key, fallback)` helper was added at `:465` and the traffic gradient uses it — the job is half done. In Light and Mono those two elements paint dark-theme hexes | medium · S |
| **U-93** | **`SysProxyHandler.UpdateSysProxy` always returns `true`** (`ServiceLib/Handler/SysProxy/SysProxyHandler.cs:66`); **`AppManager.ProfileModels` builds SQL by string concatenation** (`:271-277`); **`await _updateFunc?.Invoke(...)` throws `NullReferenceException` when the delegate is null** (`TaskManager.cs:99,119`, `CoreAdminManager.cs:29`, `StatisticsManager.cs:126`, `CoreManager.cs:1364`) | medium · S |
| **U-94** | **Six `CancellationTokenSource` fields in `AccountViewModel` are `Cancel()`-then-replaced and never disposed; the post-top-up poll catches only `OperationCanceledException`; `MainWindow.OnClosing` is `async void` (`Views/MainWindow.axaml.cs:1942`); the dead `obj is bool b` pattern survives at `:2109`.** (`_programStartedWait` **was** fixed — do not re-file that half) | low-medium · S |
| **U-95** | **`ServerListView.RegisterInteractions` early-outs on a non-empty handler list**, so a VM identity change leaves stale handlers and both layout copies stay registered | low-medium · S |

Also still open and already logged by the state audit — listed once so this file is self-contained,
**do not double-count**: the TUN toggle guard at `StatusBarViewModel.cs:513`; seven theme keys missing
from `BuildMonoOverlay`; `PortInvalid` written at `SettingsViewModel.cs:410` and read nowhere;
«Автообновление провайдеров» writing `GuiItem.AutoUpdateInterval` (uptime-hours modulo);
`MsgViewModel` constructed only in `DesignData.cs:26` so 156 message publishers vanish; no
`Key.Escape` / `XButton1` / `Ctrl+F` in the shell.

---

## 12 · THE MODULE PROPOSALS — never scheduled, recorded so they are a choice

`new-modules-proposals.md` (M1-M13) and `new-modules-proposals-3.md` (N1-N11) were advisory and no
wave adopted them. Verified absent by grep, each returning **0 hits**: `BypassLinter` (N1),
clock-skew check (N2), post-connect 204 reachability + captive-portal classification (N3), inline
settings glossary (N4), foreground clipboard offer + QR-from-gallery (N5), `ConnectionEventLog` (N6),
subscription update diff (N7), `LITE_MODE` (N8), rotate/avoid (N9 — see U-48), pre-flight checklist
(N10), `SupportBundle` (N11 — see U-57); ad/tracker blocking (M1), app-lock/duress (M2), DNS control
centre (M3), throughput speed test (M4 — see U-67), `NetworkStatsManager` dashboard (M5), scenes (M7),
guided MTU/Mux tuning (M8), routing preset library + geo auto-update (M9), Clash/sing-box importer
(M10), leak test (M11 — see U-63), OEM battery guardian (M12), multi-hop UX (M13).

**Three are cheap and disproportionately useful for the stated audience, and worth promoting out of
the proposal bucket:**

- **U-96 · N2, a clock-skew banner** *(medium · S)* — TLS/Reality fails hard on skew and presents as
  "nothing works"; the `Date:` header from the existing 204 probe is already in hand.
- **U-97 · N3, one post-connect 204 through the tunnel** *(medium · S)* — turns an opaque green
  "connected" into an honest state, and it is the missing half of U-47.
- **U-98 · N1, a pure-Kotlin `BypassLinter(profile, prefs)`** *(medium · M)* — catches exactly the
  empty-SNI / missing-fingerprint / Mux-on-Vision cases U-45 leaves open.

---

## 13 · REFUSED, DECIDED OR SUPERSEDED — do NOT resurrect these

Every row is a recorded decision with a reason. Consolidated from all four sweeps.

| Item | Where | Why it is closed |
|---|---|---|
| **A «Серверы» tab on the desktop** | Owner decision | Overrules `33-master-plan-pc.md`, `11-app-structure.md` §3.2 item 3, `24-tab-conformance.md` D-09/D-10 and `audit2026/pc-servers-account.md`. `BottomNavBar.axaml.cs:9` `enum AppTab { Home, Account, Settings }`. `ServersView.axaml` / `CompactServersView.axaml` have zero construction sites — dead files, not progress. `Geo.Nav.Servers` is unused **on purpose**. Harvest `CompactServersView.axaml:85-108` (the only desktop server search field ever written) before deleting |
| **Desktop bottom notifications / the transient toast channel** | `Views/MainWindow.axaml:590-597`, verbatim | «Владелец не хочет НИКАКИХ нижних уведомлений». `snackHost` keeps its markup but ships `IsVisible="False"` + `IsHitTestVisible="False"`; `DelegateSnackMsg` is a deliberate no-op; connection errors surface through the connect shield's Error state. Overrules §8.1's desktop column and the transient half of D-48. **Does NOT cover the persistent status strip (U-30)** — different mechanism |
| **Self-rolled in-app kill switch** | `next-plan.md §E.2` overriding `strategy §2 #8` | «не изобретать блокировку в приложении». The system Always-on deep link is the chosen path and it shipped. `VpnService` lockdown/`setBlocking` deliberately not used |
| **ECH enabled by default** | `strategy-russia-2026.md §1.7, §3.3`; `circumvention §6` | `cloudflare-ech.com` + ECH is itself an RU block trigger. `CoreOutboundBuilder.kt:566` passes only the per-node `echConfigList`; no global enable is the intended state |
| **Client-side `injectHosts`** | `remnawave-templates-spec.md §6.4 branch B` | Remnawave injects host data server-side; the client receives final JSON. Branch B is for a Happ-style template+node-list operator, which this deployment is not |
| **GeoIP-based flag resolution** | `server-flags-design.md §1 option (c)` | Explicitly a last resort; the shipped emoji→ISO layered resolver is the design's own recommendation |
| **250 bundled flag vectors / PNGs** | `server-flags-design.md §2` | "Overkill for v1"; emoji chosen |
| **Custom `RemoteViews` notification** | `notification-design.md §2` | Phase-2 only, "not worth the risk" |
| **NSD / mDNS TV discovery** | `smart-tv-transfer-design.md §3.4` | IP-in-QR is primary; mDNS is blocked on many consumer APs. Fallback only |
| **Unified single-scroll server list with sticky section headers** | `server-flags-design.md §4 Option 1` | Option 2 (collapse within a group) chosen for blast radius |
| **Google Play Billing** | `next-plan.md §D` | «осознанный отдельный выбор», flavour-gated, not the default path |
| **AmneziaWG / TUIC / new UDP transports** | `roadmap-wave3.md §6`; `strategy R2.1` | Not in this fork; recorded as debt |
| **KMP / aggressive cross-platform refactor** | `next-plan.md §K` | «не рефакторить агрессивно» — discipline plus a document only (that document is U-64) |
| **`chrome_pq` uTLS value** | `circumvention §4.2` | Breaks Reality; intentionally excluded from the entry list |
| **PSS as the memory headline** | `memory-panel-design.md §1.3` | The code chose Java heap and documents the choice (`util/MemoryStatsManager.kt:4-9`: shared framework pages "are not really the app"). Contradicted-by-decision, not a gap — though the panel cannot be shown at all, which is U-70 |
| **`res/anim/nav_press.xml` kept as a separate file** | its own header | The four bottom-nav items are discrete objects with `@null` backgrounds, so R5's "rows do not scale" does not apply and §0.4.8 forbids the ripple — scale is the only acknowledgement allowed inside §7.3's 100 ms. Delete only together with its four references when the M3 nav bar lands |
| **`motion_spin` is linear** | `res/values/motion.xml`, `00-rules.md §3.7` | The one exemption to §8.3's linear ban |
| **`accent_hover` / `accent_pressed` have no Android readers** | `res/values/colors.xml:270-271` | Parity tokens with the desktop client; Android has no hover state (§7.1) |
| **`icon_purple` / `icon_orange` / `icon_yellow` / `icon_green` / `Brush.Tile.Purple` stay** | D-5 | Retired tile colours stay alive as aliases of blue until the last referencing screen migrates. Deleting them early breaks live screens |
| **`StatusBarView` mounted at 0×0 on desktop** | state audit | Load-bearing: tray icon, clipboard, sudo password, TUN elevation. Notwithstanding `11-app-structure.md` §3.2 item 6 |
| **Sign-out is deliberately not undoable** | `ui/AccountFragment.kt:658`, in code | Reasoned against §7.5's undo-over-confirmation default; uses a dialog plus a «Повторить» retry |
| **«Привязать Telegram» framed as a lost feature (HIGH)** | `audit-android-ui` A5 | **Refuted** — a live entry point exists at `layout_home_empty.xml:77`. What remains is dead-code cleanup (U-73), not a regression |
| **Per-app proxy never restarts the tunnel** | `audit-android-ui` A12 | **Refuted** — `PerAppProxyActivity` calls `SettingsChangeManager.makeRestartService()` and `MainActivity` consumes the flag |
| **Rotation replays the connect state** | `hunt-android-cold-start` F4 | **Refuted** — all three halves guarded, `KEY_CONNECTION_START` persisted |
| **19 amputated menu actions** | `CONTINUE-HERE.md` 4.1, `audit-android-ui` A7 | **Closed by the salvage commit** — `menu_main.xml` is 12 items in two groups, all dispatched |
| **"Ping all" / "Restart service" missing from the menu** | salvage commit | Literally true, not defects — both have live entry points elsewhere; menu duplicates are a design question |
| **Provider toggles are write-only** | `CONTINUE-HERE.md` 2.8 | **Still fixed**, all five consumers verified |
| **23 desktop capabilities** — mixed test (F3), sort-by-column (F7), move-server-between-groups (F9), two export formats (F10), multiple routing sets (R1), import-rules-from-file (R3), seven advanced DNS knobs (R7), sniffing destination-override (S6), second local port (S7), Hysteria bandwidth hints (S8), font family/size (S17), sing-box TUN options (S19), SRS download (D5), certificate pinning fetch (D9), the multi-core family (E1-E4, E6, E7, E10, E11) | `gap-desktop-to-android.md §9.3` | Refused with a reason on each row. `00-rules.md` §13 is satisfied by the argument, not by the port |
| **9 platform impossibilities** — global hotkeys, system proxy/PAC, sudo prompt, window chrome/tray, per-window DPI, registry autostart plumbing, core-binary download, `sendThrough`/interface bind, root-certificate provider | `gap-desktop-to-android.md §7.1 N1-N9` | Argued individually |
| **`ServiceLib/Global.cs:33 PromotionUrl`** | sweep-ops | Upstream's promo link; its only consumer is `v2rayN/Views/MainWindow.xaml.cs:248`, the WPF client this fork does not ship. Not reachable from `v2rayN.Desktop`. Touching `ServiceLib` for it only adds merge friction |
| **Double subscription fetch on login** | `SubscriptionSyncManager.kt:83`, in code | Deliberate: `updateConfigViaSub` sets `lastUpdated`, from which the periodic worker derives its initial delay. review-05 MED filed this as redundant; it is not |
| **Kotlin package `com.v2ray.ang` unchanged; `v2rayN*` project/solution names on desktop** | `README.md:21-23` | Deliberate, so upstream merges stay reviewable |
| **Upstream copyright in `LICENSE` and `Directory.Build.props`** | GPL-3.0 | Required. Only the `Company` / `Copyright` disagreement inside one version resource is a defect — see U-99 below |
| **Release APK signed with the debug key *as a stated choice*** | `V2rayNG/app/build.gradle.kts:70` comment | The build file states the reason (a directly installable artefact). **That reasoning does not extend to CI emitting a different key every run — R-02 is still live**, and the `isMinifyEnabled = false` half is R-11 |

---

## 14 · CLAIMS I CHECKED AND DROPPED — the sweeps were wrong or stale on these

Recorded so nobody re-opens them, and so the next sweep knows these six were tested.

1. **`sweep-specs` §E — "the two `PREF_UI_MODE_NIGHT` defaults disagree and a fresh install renders
   light".** **Not reproducible.** `AngApplication.onCreate:41-42` calls `SettingsManager.initApp(this)`
   → `ensureDefaultSettings()` → `handler/SettingsManager.kt:708`
   `ensureDefaultValue(AppConfig.PREF_UI_MODE_NIGHT, "2")` **before** `setNightMode()` at `:42`. The key
   is always present by the time `:629`'s `"0"` fallback could bite. What remains is a cosmetic
   inconsistency — three literal defaults (`"0"` at `:629`, `"2"` at `:708`, `"2"` at
   `SettingsTabFragment.kt:410`) for one key — worth one line, not a P1. *(The related §13 obligation,
   a cross-platform table of every setting's default value, genuinely does not exist anywhere and is
   folded into U-64.)*
2. **`sweep-specs` §E — "`MainActivity.kt:3422` reads the key".** Stale location: the appearance picker
   moved to `ui/SettingsTabFragment.kt:406-446` with the settings wave.
3. **`sweep-plans` P10 / `sweep-specs` B4 — "the notification is missing the channel/priority/
   chronometer work".** No: `notification-design.md` landed except the two items now in U-37.
4. **`sweep-audits` S38 A1 desktop auto-fallback "0 hits" — re-verified 0 hits today**, but note the
   Android side of the same feature is *fixed and shipping*, so this is a port, not a design. Kept as
   U-65 with that framing.
5. **`sweep-plans` §1.9 "M4 throughput speed test" and `gap` W1 are the same item** — filed once, U-67.
6. **`sweep-ops` OPS-20 — `docs/CONTINUE-HERE.md` overstates what is left.** Four of its claims are now
   false (no screen rebuilt; 19 amputated menu actions; SettingsActivity/CheckUpdateActivity
   unreachable; both READMEs unmodified boilerplate). **This is not open work, it is a stale document**
   — but leaving it uncorrected actively misdirects the next agent, so it is U-100 below.

---

## 15 · TWO DOCUMENT FIXES THAT MISDIRECT EVERY FUTURE AGENT

### U-99 · `CLAUDE.md` names the wrong design law, the wrong font and an incomplete radius set
**value: medium · size: S · this is the file loaded into every agent's context**
- It designates `.claude/skills/` as the mandatory standard and **never mentions `docs/design2026/`**.
  Both READMEs say the opposite: `dp/README.md:176` and `v2rayN/README.md:145` state that
  `docs/design2026/00-rules.md` "outranks taste, habit and upstream precedent". An agent obeying
  `CLAUDE.md` literally would never open the actual design law. (The skills themselves *are* vendored,
  so that half of the promise is kept.)
- `CLAUDE.md:16` calls Space Grotesk "brand font" with no qualifier. Superseded:
  `docs/licenses/golos-text.txt` records that Space Grotesk maps 735 codepoints and **none are
  Cyrillic**; Golos Text is now the Russian UI face (decisions D-1/D-2, `00-rules.md` §18) and Space
  Grotesk is scoped to digits, currency, units and the wordmark. **The code already matches the new
  rule** — `res/values/styles.xml:85,102,120,137,154,171` bind `@font/golos_text_*` and `:66,195` keep
  `@font/space_grotesk` for display/numeric roles; `res/font/` holds all four faces. Only `CLAUDE.md`
  still says otherwise — and it is what makes U-28's `ToolbarBrandTitle` look correct to a reader.
- `CLAUDE.md:19-21` lists three radii; `res/values/dimens.xml:69-85` declares six (`radius_chip 12`,
  `radius_tile 12`, `radius_button 16` aliased `radius_control`, `radius_card 20`, `radius_sheet 24`,
  `radius_pill 100`). An agent following the list would treat the 16dp control radius — which every
  button and input uses — as off-scale.

Also worth adding while there: the desktop's own version resource disagrees with itself —
`v2rayN.Desktop.csproj:11-13` sets `Product`/`AssemblyTitle`/`Company` to `departament` while
`v2rayN/Directory.Build.props:13-15` supplies `<Authors>2dust</Authors>` and the matching
`<Copyright>`. Keeping upstream's copyright is correct under GPL-3.0; having `Company` disagree with
it in the same resource is the defect. *(sweep-ops OPS-19 + OPS-15.)*

### U-100 · `docs/CONTINUE-HERE.md` needs one revision pass
**value: low · size: S · superseded content**
See §14 item 6. Genuinely still absent and worth keeping in the document: `service_restart` as a user
action, and the per-protocol manual-import entries — `MainActivity.importManually(createConfigType)`
exists and only `import_manually_vless` calls it, so **eight protocols have an implementation and no
caller**. The document is otherwise still the best handoff; it needs a revision, not a rewrite.

---

## 16 · IF ONLY TEN THINGS GET DONE

Ranked by what the user loses, with the release gate first because it is binary.

1. **R-07 + R-08** — retarget the four workflows and add a release variant plus `test` to the gate.
   Without this everything below regresses unnoticed, and it is why R-01 … R-06 were never seen. *S*
2. **U-01** — the `depv://` confirm sheet and `append = true`. Today a web page can wipe the server
   bucket and select an attacker's server. *M*
3. **R-01 + R-02 + R-03** — distinct version codes, a real signing key, and desktop packages that
   launch. Nothing can be published until all three are true. *S/S/M*
4. **U-26** — decide the locale story and execute it. Everything else is polish on an app that
   currently speaks Russian to Farsi and English users, and it is the mechanism by which the copy
   register will silently miss half its targets. *L*
5. **U-13 + U-14 + U-15 + U-16** — the money path, one afternoon together. Read the status, add one
   `isPaying` flag, pass the uuid through the extra that is already declared and read, and make
   `resolveUsedDevices` use the argument it is handed. *S each*
6. **U-12** — one wrong string in `shortcuts.xml` and one missing `VpnService.prepare()`. Between them
   they are the reason every shortcut, the tile, the widget and Tasker are dead or fail silently. *S*
7. **U-21 + U-22 + U-23** — the connect flow as one contract. The desktop half is done and is the
   model; `ui/component/SingleClick.kt` already exists. *S*
8. **U-28** — three lines in `res/layout/activity_base.xml`; fixes the P1 brand-face defect on twelve
   screens at once. Highest value-per-byte item in this file. *S*
9. **U-31 + U-45** — the expiry warning (four documents, zero code, and it is what protects revenue
   now that payments ship) and one `?:` in `core/CoreOutboundBuilder.kt:564` that closes a live
   detection vector for every node imported without `fp=`. *M / S*
10. **U-02 + U-03 + U-04** — the user CA anchor, the plaintext token in the backup zip, and Zip Slip.
    Three small edits, three different ways the operator's secret or the user's device is exposed. *S*

Then: **U-30** (give the status strip a binder — six conditions land free, including half of U-29),
**U-38 + U-39** (three drawables and one string, which unblock the correct back glyph everywhere and
the strip's severity channel), **U-70** (a fresh install currently deletes without confirmation and
cannot turn confirmation on), **U-71**, **U-49**, **U-78 + U-80**.
