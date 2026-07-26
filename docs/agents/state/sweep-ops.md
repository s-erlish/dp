# Sweep — repository, build, packaging, release and CI

Scope: `/home/user/dp/CLAUDE.md`, both `README.md`, `docs/CONTINUE-HERE.md`,
`docs/agents/BUILD-VERIFY.md`, every `.github/` workflow in both repos, and the
`impl-*.md` / `review-*.md` / `compile-review-*.md` set in `/home/user/dp/docs/`.
Everything below was checked against the code. Read-only; nothing was modified, no git
command was run.

**Headline:** the *product* has been rebranded; the *pipeline that ships it* has not. Both
repositories still self-update from upstream, still package under upstream's name, still
publish upstream's store listing, and — on the desktop — the Linux/macOS packaging scripts are
now **broken by our own `AssemblyName` rename**. None of this appears in any state document:
grepping `docs/agents/state/` for `fastlane`, `APP_API_URL`, `2dust/v2rayNG`, `winget`,
`versionCode`, `signingConfig`, `AmazTool`, `Directory.Build` returns nothing.

Release-variant verdict: **both repos compile for release, and neither is releasable.** No
release-only *compile* risk exists (Android release has `isMinifyEnabled = false`, so R8 keep
rules cannot bite; the desktop's Release config is what the gate already builds). The failures
are all in signing, versioning, packaging, store metadata and update feeds — precisely the
steps no gate exercises.

---

## 1 · Self-update: both clients update themselves into upstream

### OPS-01 · Android "Проверить обновления" queries `2dust/v2rayNG` — and the entry point was just restored (high · M · open)

`AppConfig.kt:145`
```kotlin
const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"
```
`handler/UpdateCheckerManager.kt:19-21` is the only consumer; it compares the upstream tag
against `BuildConfig.VERSION_NAME` and, on a hit, calls `getDownloadUrl(latestRelease, abi)` and
offers the upstream APK.

This was harmless while `CheckUpdateActivity` had no launch site. It no longer does — the
entry-point restoration listed as already-committed work wired **two**:
`ui/MainActivity.kt:3179` (`rowCheckUpdate`) and `ui/AboutActivity.kt:91`. So a shipped build
now has a user-reachable control that reads a release feed for a different product. Best case
it silently never fires (our `versionName 2.2.1` outranks upstream's numbering); worst case it
hands the user a `v2rayNG` APK that cannot install over `com.departamentvpn.app` anyway. There
is no Departament release feed for it to point at — never implemented.

### OPS-02 · Desktop self-update downloads `v2rayN-windows-64.zip` from `2dust/v2rayN` (high · M · open)

`ServiceLib/Global.cs:674` → `{ ECoreType.v2rayN, "2dust/v2rayN" }`, consumed by
`ServiceLib/Manager/CoreInfoManager.cs:99` and `:108-118`:
```csharp
CoreType = ECoreType.v2rayN,
Url = GetCoreUrl(ECoreType.v2rayN),           // github.com/2dust/v2rayN/releases
DownloadUrlWin64 = urlN + "/download/{0}/v2rayN-windows-64.zip",
```
`ServiceLib/Services/UpdateService.cs:11 CheckUpdateGuiN` downloads it and
`ServiceLib/Common/Utils.cs:818` hands it to AmazTool for in-place replacement. Because
`v2rayN/Directory.Build.props:4` still reads `<Version>7.23.4</Version>` — upstream's number —
any upstream release above 7.23.4 is seen as an upgrade. The Departament desktop will overwrite
itself with stock v2rayN. `CheckUpdateViewModel` is registered in
`v2rayN.Desktop/Common/SimpleViewLocator.cs:19`; I found no navigation to it in the new shell,
so this is *implemented and currently unreachable* rather than live — but the wiring is one
menu row away, and the Android half of the same defect (OPS-01) is already reachable.

---

## 2 · Identity: what a user and a store actually see

### OPS-03 · The store listing is upstream's, and a workflow validates it green (high · S · open)

`fastlane/metadata/android/en-US/`:
- `title.txt` → `v2rayNG`
- `short_description.txt` → `A V2Ray client for Android, support Xray core and v2fly core`
- `full_description.txt` → 2dust's copy, linking `t.me/github_2dust`

`.github/workflows/fastlane.yml` runs `validate-fastlane-supply-metadata`, which checks
*structure*, so this passes and reads as covered. `README.md:38` describes `fastlane/` as
"Store metadata, validated by a workflow" without noting whose product it describes. Never
touched by any wave.

### OPS-04 · The fdroid flavour's launcher label is `v2rayNG (F-Droid)` (high · S · open)

`app/src/fdroid/res/values/strings.xml:3` overrides `app_name` (which is `departament` in
`res/values/strings.xml:3`). CI's `build.yml` runs `assembleRelease`, i.e. **both** flavours, so
the fdroid release APK ships with upstream's name on the home screen. `README.md:68-69` records
the fact but files it as trivia rather than a defect.

### OPS-05 · «О приложении» ships four rows that open upstream (high · S · open)

`ui/AboutActivity.kt`, all resolving through `AppConfig.kt:144-150`:

| Row | Line | Destination |
|---|---|---|
| Исходный код | `:97` → `APP_URL` | `github.com/2dust/v2rayNG` |
| Обратная связь | `:104` → `APP_ISSUES_URL` | `github.com/2dust/v2rayNG/issues` |
| Telegram | `:109` → `TG_CHANNEL_URL` | `t.me/github_2dust` — upstream's channel, not `@departamentvpn` |
| Политика конфиденциальности | `:121` → `APP_PRIVACY_POLICY` | `raw.githubusercontent.com/2dust/v2rayNG/master/CR.md` |

That last one is a customer-facing legal document: `CR.md` at this repo's root is 2dust's
**Chinese-language privacy policy for v2rayNG**, dated 2023-11-17, describing a different
operator. A Departament VPN user tapping "Политика конфиденциальности" reads it.

This is a **logged parity gap**: the desktop already fixed the equivalent —
`v2rayN.Desktop/Views/AboutPage.axaml.cs:24` builds its Telegram link from
`BackendConfig.BotUsername`. Android has the same `BackendConfig`/`BOT_USERNAME` available
(`app/build.gradle.kts:45` = `departamentvpnbot`) and does not use it here.

---

## 3 · Android release build: compiles, cannot be published

### OPS-06 · Every playstore ABI gets the same `versionCode` → Play rejects the upload (high · M · open)

`app/build.gradle.kts:127-142`, the non-fdroid branch:
```kotlin
val versionCodes = mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)
…
output.versionCodeOverride = (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
```
Every ABI maps to `4`, so all five outputs get `4000731`. ABI splits are enabled
(`:21-37`, `isUniversalApk` on when no `-PABI_FILTERS`), so `assemblePlaystoreRelease` produces
five APKs with **identical version codes and distinct filenames**. Google Play requires a
distinct `versionCode` per APK in a multi-APK release; the upload is refused. The fdroid branch
(`:109-125`) does it correctly (`100 * versionCode + {0,1,2,3,4} + 5_000_000`) — so the flavour
that would actually go to a store is the broken one. Nothing here ever ran against a store, so
nobody hit it.

### OPS-07 · Release APKs are debug-signed, with a per-machine key (med · S · open)

`app/build.gradle.kts:62-71`:
```kotlin
release {
    isMinifyEnabled = false
    signingConfig = signingConfigs.getByName("debug")
}
```
`README.md:66-67` says "CI overrides it with `-Pandroid.injected.signing.*` when it has the
secrets". Only `build.yml:99` does. `release.yml` — the workflow whose own step is titled
*"Build release APK (arm64-v8a, playstore only, debug-signed)"* — passes nothing, so it emits an
artifact signed with AGP's auto-generated `~/.android/debug.keystore`. That keystore is created
fresh per runner, so **two consecutive runs sign with different keys**: a tester who installed
build N cannot install build N+1 without uninstalling. There is no release keystore checked in
(`.gitignore` excludes `*.jks` and `signing.properties`) and no documented handling of
`APP_KEYSTORE_BASE64` outside `build.yml`.

### OPS-08 · Release is unminified and unshrunk; `proguardFiles` is dead configuration (med · M · open decision)

`isMinifyEnabled = false` with `proguardFiles(…, "proguard-rules.pro")` declared, and
`app/proguard-rules.pro` containing nothing but the AGP comment banner. No `shrinkResources`
anywhere. The release APK therefore carries every unused resource and unobfuscated class names,
including all of `auth/**`. This is upstream's posture and turning it on is real work (Gson DTOs,
MMKV, and the reflective `libv2ray` surface all need keep rules) — flagging it as **a decision
nobody has made**, not as a bug.

### OPS-09 · CI installs the wrong SDK platform for `compileSdk = 37` (med · S · open)

`.github/workflows/build.yml:29`:
```yaml
packages: 'platforms;android-36.1 build-tools;36.1.0 platform-tools'
```
`app/build.gradle.kts:9,15` set `compileSdk = 37` / `targetSdk = 37`, and
`docs/agents/BUILD-VERIFY.md:40-41` records — as a fact that "cost time to rediscover" — that the
package is `platforms;android-37.0`. This environment has `android-37.0` installed; CI asks for
`android-36.1`. The release pipeline only survives on AGP's implicit SDK auto-download
(`android.builder.sdkDownload` is not disabled in `gradle.properties`). It is papering over a
declared mismatch, and it will stop doing so the moment auto-download is unavailable or the
licence prompt changes.

### OPS-10 · The `ndkVersion` line-10 injection is now a duplicate (low · S · open)

`build.yml:37-39` does `sed -i '10i\ … ndkVersion = "28.2.13676358"'` on `build.gradle.kts`.
Line 10 of that file **already** is `ndkVersion = "28.2.13676358"` (added since the workflow was
written). Kotlin DSL tolerates the double assignment, so it does not break — but it is a
line-number-addressed patch against a file that has moved, and the next edit above line 10 will
inject `ndkVersion` into a random position.

---

## 4 · Desktop release: packaging broken by our own rename

### OPS-11 · Every `package-*.sh` launches an executable that no longer exists (high · M · open)

`v2rayN.Desktop.csproj:9` sets `<AssemblyName>departament</AssemblyName>`, so `dotnet publish`
emits `departament` / `departament.dll`. The packaging scripts were never updated:

`package-debian.sh:509-531` (identical in `package-debian-loong.sh`, `package-debian-riscv.sh`,
and the three `package-rhel*.sh`):
```bash
DIR="/opt/v2rayN"
if [[ -x "$DIR/v2rayN" ]]; then exec "$DIR/v2rayN" "$@"; fi
for dll in v2rayN.Desktop.dll v2rayN.dll; do … done
echo "v2rayN launcher: no executable found in $DIR" >&2 ; exit 1
```
None of the three candidates is produced any more, so the installed `.deb`/`.rpm` **cannot start
the app** — it prints "no executable found" and exits 1.

`package-osx.sh:14-15,45` is worse, because it fails silently:
```bash
cp -f "$PackagePath/v2rayN.app/Contents/MacOS/v2rayN.icns" …
chmod +x "$PackagePath/v2rayN.app/Contents/MacOS/v2rayN"
…  <key>CFBundleExecutable</key><string>v2rayN</string>
    <key>CFBundleIdentifier</key><string>2dust.v2rayN</string>
```
The `.app` is built with a `CFBundleExecutable` that is absent from `Contents/MacOS/` and a
bundle identifier belonging to upstream. `README.md:39` says only "Their output is still named
after upstream" — the truth is stronger: **the output does not run.**

### OPS-12 · The desktop release pipeline is entirely upstream's (med · M · open)

- `build-windows-desktop.yml` (push to `master`) → `build.yml` → `package-zip.yml:52` downloads
  `2dust/v2rayN-core-bin` and names the asset `v2rayN-windows-64.zip`, then
  `upload-sign.yml` GPG-signs and publishes it to *our* release tag.
- `winget-publish.yml` fires on `release: types: [released]` in **this** repo and then submits
  the package id `2dust.v2rayN`, reading assets from `api.github.com/repos/2dust/v2rayN/releases`
  — it publishes upstream's binaries under upstream's winget id whenever we cut a release.
- `build-all.yml` fans out to five upstream build workflows, all pinned to `ref: "master"`.

The only Departament-aware desktop workflow is `departament-branch-build.yml`, which is win-x64
only and uploads an artifact — never a release.

### OPS-13 · `departament-branch-build.yml` ships no AmazTool, so the upgrade path is dead (med · S · open)

`ServiceLib/Common/Utils.cs:818` resolves `GetExeName("AmazTool")` next to the executable to
apply a downloaded upgrade. Upstream's `build.yml:81-84` publishes it; the Departament branch
build does not (it publishes only `v2rayN.Desktop.csproj`, then bundles Xray + sing-box).
`README.md:71-72` documents the two-command publish including AmazTool, so the README is right
and the workflow is wrong.

### OPS-14 · `test.yml` pins .NET 8 against a net10.0 solution (low · S · open)

`.github/workflows/test.yml:24` → `dotnet-version: '8.0.x'`, while
`v2rayN/Directory.Build.props:10` sets `<TargetFramework>net10.0</TargetFramework>`. The
workflow cannot restore, let alone run `ServiceLib.Tests`. It only triggers on PRs to `master`
touching `CoreConfig/**` or `Handler/Fmt/**`, so it has stayed invisible.

### OPS-15 · Upstream attribution is inconsistent in the desktop's own version resource (low · S · open)

`v2rayN.Desktop.csproj:10-13` overrides `Product` / `AssemblyTitle` / `Company` to `departament`,
but `Directory.Build.props:14-16` still supplies `<Authors>2dust</Authors>` and
`<Copyright>Copyright © 2017-2026 2dust</Copyright>` for every assembly. The shipped binary
therefore reports Company `departament` and Copyright `2dust` side by side. (Keeping upstream's
copyright is correct under GPL-3.0; having `Company` disagree with it in the same resource is
the defect.)

---

## 5 · Nothing verifies a release, and nothing runs the tests

### OPS-16 · No gate ever builds an Android release variant or a desktop publish (med · S · open)

`docs/agents/verify-build.sh:51` builds `:app:assembleFdroidDebug`; `:84` builds
`dotnet build v2rayN.Desktop.csproj -c Release`. So:
- no `assemblePlaystoreRelease` / `assembleFdroidRelease` is ever produced locally,
- no `dotnet publish -r <rid> -p:SelfContained=true` is ever produced locally,
- and per OPS-09 the CI that would do it targets the wrong SDK platform.

`BUILD-VERIFY.md` describes the gate as "the single gate for both platforms" without stating
that it covers debug-Android and build-only-desktop. Everything in sections 3 and 4 above lives
in exactly that blind spot.

### OPS-17 · The Android unit tests are run by nothing (med · S · open)

`app/src/test/java/com/v2ray/ang/` holds five test classes, two of which were written to lock in
fixes from the covered work:
```
dto/ProxyOutboundResolutionTest.kt      util/FlagUtilTest.kt
fmt/ShadowsocksFmtTest.kt   HttpUtilTest.kt   UtilsTest.kt
```
No workflow in `.github/workflows/` invokes `test` or `testFdroidDebugUnitTest`, and
`verify-build.sh` only assembles. The two regression tests protecting "effective outbound
resolved through routing rules" and "flag accuracy" have never been executed by an automated
gate. On the desktop the mirror problem is OPS-14 (`ServiceLib.Tests` exists, its workflow is
broken).

### OPS-18 · CI never fires for the branch this work is on (med · S · open)

| Workflow | Push trigger | Current branch |
|---|---|---|
| `dp/.github/workflows/debug.yml` | `claude/vpn-client-happ-design-mq51pv` | `claude/app-audit-agents-hyyftk` |
| `dp/.github/workflows/release.yml` | `claude/vpn-client-happ-design-mq51pv` | ″ |
| `dp/.github/workflows/build.yml` | `master` | ″ |
| `v2rayN/.github/workflows/departament-branch-build.yml` | `claude/dp-desktop-incy` | ″ |

Every convenience workflow points at a dev branch that the work has since moved off. Combined
with OPS-16 this means the entire redesign has been verified only by a local debug assemble.
One-line fix each, but it is the reason none of OPS-06/07/09/11/12 has ever surfaced.

---

## 6 · Documents that no longer describe the code

### OPS-19 · `CLAUDE.md` names the wrong design law and the wrong font (med · S · open)

`/home/user/dp/CLAUDE.md` is the file loaded into every agent's context, and it is out of date on
its central claim:

- It designates `.claude/skills/` (`ui-ux-pro-max`, `impeccable`, …) as the mandatory standard
  and **never mentions `docs/design2026/`**. Both READMEs say the opposite:
  `dp/README.md:176` and `v2rayN/README.md:145` state that `docs/design2026/00-rules.md`
  "outranks taste, habit and upstream precedent". An agent obeying CLAUDE.md literally would
  never open the actual design law. (The skills themselves *are* vendored — all eight
  directories exist under `.claude/skills/` — so that half of the promise is kept.)
- `CLAUDE.md:16` calls Space Grotesk "brand font" with no qualifier. That was superseded:
  `docs/licenses/golos-text.txt` records that Space Grotesk maps 735 codepoints and **none of
  them are Cyrillic**, so every Russian string set in it silently fell back to the system face;
  Golos Text is now the Russian UI face (decisions D-1/D-2, `00-rules.md` §18) and Space Grotesk
  is scoped to digits, currency, units and the wordmark. The code already matches the new rule —
  `res/values/styles.xml:85,102,120,137,154,171` bind `@font/golos_text_*`, and `:66,195` keep
  `@font/space_grotesk` for the display/numeric roles. Only CLAUDE.md still says otherwise.
- `CLAUDE.md:19-21` lists the radius set as "`radius_chip 12` / `radius_card 20` /
  `radius_tile 12`". `res/values/dimens.xml:69-85` declares five: `radius_chip 12`,
  `radius_tile 12`, `radius_button 16` (aliased `radius_control`), `radius_card 20`,
  `radius_sheet 24`, `radius_pill 100`. An agent following CLAUDE.md's list would treat the
  16dp control radius — which every button and input uses — as off-scale.

`row_min_height 56` (`dimens.xml:149`) and the `TextAppearance.App.*` ramp
(`styles.xml:65-232`) are present as promised.

### OPS-20 · `docs/CONTINUE-HERE.md` overstates what is left (low · S · superseded)

Checked line by line; four of its claims are now false:

- §3 "The design exists on paper only. **No screen has been rebuilt yet.**" — contradicted by the
  whole `docs/design2026/`-conformant tree (`ui/component/`, `SubPage`, `RowBinder`,
  `ToolbarBinder`, `EmptyStateBinder`, the rebuilt `AboutActivity`/`CheckUpdateActivity`).
- §4.1 "19 amputated menu actions … unreachable dead code" — `res/menu/menu_main.xml` now
  declares `group_import` (6 items incl. `import_create`, `import_file`) and `group_server_list`
  (`servers_locate/sort/export/del_duplicate/del_invalid/del_all`). Ping-all is reachable via
  `MainActivity.startLatencyCheckAll()` (`:3002-3022` → `MainViewModel.testAllServers()`).
  Genuinely still absent: `service_restart` as a user action, and the per-protocol manual-import
  entries (vmess/ss/socks/http/trojan/wireguard/hysteria2/policy-group/proxy-chain) —
  `MainActivity.importManually(createConfigType)` (`:2546`) exists and only `import_manually_vless`
  (`:2452`) calls it, so eight protocols have an implementation and no caller.
- §4.2/4.4 SettingsActivity and CheckUpdateActivity unreachable — both wired
  (`MainActivity.kt:3174-3179`).
- §4.8 "Both READMEs are still unmodified upstream boilerplate" — both were rewritten (they now
  correctly describe flavours, the stub, the parity contract and the account layer). **This item
  is done**; the earlier audit's finding is closed.

The document is otherwise still the best handoff; it needs one revision pass, not a rewrite.

### OPS-21 · Upstream issue templates and privacy policy still in the repo root (low · S · open)

`.github/ISSUE_TEMPLATE/bug_cn.md` ("v2rayNG程序问题") and `config.yml` (contact link to
`v2fly/v2ray-core`) are upstream's Chinese templates. `CR.md` is 2dust's privacy policy — see
OPS-05, where the app links to the *upstream copy* of this same file.

---

## 7 · Smaller open items found while sweeping

### OPS-22 · `android:allowBackup="true"` with no backup rules (med · S · open)

`AndroidManifest.xml:45`, and no `android:fullBackupContent` / `android:dataExtractionRules`
anywhere in the manifest. Every MMKV store — server configs, subscription URLs, the Keystore-
sealed token blob — is eligible for Google cloud backup and `adb backup`. The token blob is
useless off-device (its key is Keystore-sealed and not backed up, per `README.md:144-146`), but
the subscription URLs are the operator's secret and are plaintext. Nobody has ruled on this.

### OPS-23 · `values/` still holds English for 375 keys that `values-ru/` translates (med · L · open, cross-ref)

`res/values/strings*.xml` = 1166 keys, `res/values-ru/` = 768, of which **375 differ**
(`action_stop_service` = "Stop service" vs «Остановить службу»; `bottom_nav_home` = "Home" vs
«Главная»; `connection_test_pending` = "Check Connectivity" vs «Проверить подключение»…).
So the *default* locale — what any non-Russian device shows — is a mix of new Russian copy and
leftover upstream English, and a copy fix landed in `values/` alone is invisible on a Russian
device because `values-ru/` shadows it.

Already logged as **L1** in `docs/agents/state/sweep-plans.md:39-63` with the two ways out.
Recorded here only because the copy register's enforcement has not started and this is the
mechanism by which enforcement will silently miss half its targets.

### OPS-24 · Dead payload in both bundles (low · S · open)

- `app/src/main/res/font/montserrat_thin.ttf` (152 KB) — zero references in `res/` or `java/`.
- `app/src/dev/` and `app/src/pre_release/` — one `strings.xml` each, matching no flavour in the
  `distribution` dimension, never built (`README.md:70-71` says so).
- `v2rayN.Desktop/Assets/Fonts/NotoSansSC-Regular.ttf` (10.5 MB) — upstream's Simplified-Chinese
  face, embedded via `<AvaloniaResource Include="Assets\**" />` into a Russian-UI product.

---

## 8 · `review-*.md` / `compile-review-*.md` — closure status

Every finding was re-checked against the current code.

**Closed (verified in code):**

| Finding | Where it is now |
|---|---|
| review-01 HIGH — `fastConnectAction` replays on recreate | `MainActivity.kt:892-893` consumes a one-shot event via `mainViewModel.consumeFastConnectEvent()` |
| review-01 MED — uptime resets on recreate | Persisted: `MainActivity.kt:343` `KEY_CONNECTION_START`, restored at `:2330-2334` |
| review-01 MED/LOW — `mono_fab_active`/`mono_connected` dead | Wired at `themes.xml:444-445` |
| review-03 MED — one `OkHttpClient` per probe | Shared lazy client (confirmed by review-04's own re-check) |
| review-03 LOW — ICMP has no IPv6 path | `SpeedtestManager.kt:29` `PING6`, `:119-122` explicit family selection with `ping -6` fallback |
| review-04 **BLOCKER** — auto-fallback reconnect loop | `MainViewModel.fallbackInProgress` (`:86`) survives recreate; `MainActivity.kt:2369-2377` documents why; `:964` sets `autoFallbackUsed` before the restart |
| review-04 MED — single transient failure switches server | Confirming re-probe at `MainActivity.kt:955-959` |
| review-04 MED — fallback can re-pick the failed server | `:969` `fastConnect(excludeGuid = MmkvManager.getSelectServer())` |
| review-05 HIGH — `AwaitingTelegram` conflated away by `StateFlow` | `AuthManager.kt:28` `Polling(deepLink)` carries the link; `LoginActivity.kt:193-194` handles the swallowed case |
| review-05 MED — token in plain MMKV | Keystore-sealed (`README.md:144-146`) |
| review-07 LOW — `FlagUtil` false positives / `UK` glyph | Covered work ("flag accuracy") |
| compile-review-c942766 M1 — `ServerActionsSheet.isLocked` stub | `ui/ServerActionsSheet.kt:69-70` returns `TemplateManager.isLocked(profile)` |
| compile-review-c942766 minor — orphaned `SettingsActivity` | Reachable, `MainActivity.kt:3174-3179` |
| compile-review-final B1 — 19 amputated menu ids | Largely restored under new ids; see OPS-20 for the eight that remain |

**Superseded (the code they describe no longer exists):**

- review-02 (all six findings) — `GroupServerFragment.kt` and `GroupPagerAdapter.kt` were
  deleted by the S3 rework (`docs/impl-s3-report.md`, "Deleted"). The meta-bar logic moved into
  `MainActivity`; the specific line numbers are meaningless now. Worth one re-read of the moved
  code for the `skipCount`-produces-no-feedback case, which is behaviour, not location.
- review-03 HIGH — direct-HTTP probe hitting one fixed URL for every server: superseded by
  `197a4d1`, which probes `https://<server>:<port>`; review-04 MED records the residual
  limitation (non-TLS transports read as unreachable) as accepted.

**Still open, small:**

- review-05 MED — `AuthManager.refreshIfNeeded()` is gone, but
  `auth/AuthTokenStore.kt:179 getExpiresAt()` survives with **zero call sites**. Nothing checks
  the 7-day JWT lifetime client-side. Consistent with "no refresh endpoint"
  (`README.md:143-144`), so this is dead code to delete, not a feature to build.
- review-03 LOW — `SpeedtestManager` uses `.head()` while the UI string and KDoc say GET.
- review-01 LOW — `ic_launcher_foreground.xml` content near the adaptive-icon safe-zone edge.
- review-05 LOW — `android:autofillHints=""` on `et_code` in `activity_login.xml`.

---

## 9 · Refused / decided — do not resurrect

| Item | Decision and reason |
|---|---|
| A **Серверы tab on the desktop** | Owner decision: the desktop must not gain one. Any document assuming it is overruled. |
| `ServiceLib/Global.cs:33 PromotionUrl` (base64 → `https://9.234456.xyz/abc.html`) | Upstream's promo link. Its **only** consumer is `v2rayN/Views/MainWindow.xaml.cs:248`, the WPF client this fork does not ship (`README.md:34`). Not reachable from `v2rayN.Desktop`. Leave it; touching `ServiceLib` for it only adds merge friction. |
| Double subscription fetch on login (`SubscriptionSyncManager.kt:88-89`) | Deliberate, and the reason is in the code at `:83`: `updateConfigViaSub` sets `lastUpdated`, and `syncOne` derives the periodic worker's initial delay from it. review-05 MED filed this as redundant; it is not. |
| Kotlin package `com.v2ray.ang` unchanged | Deliberate, so upstream merges stay reviewable (`README.md:21-23`). Same for the `v2rayN*` project/solution names on the desktop. |
| Upstream copyright in `LICENSE` / `Directory.Build.props` `Copyright` | Required by GPL-3.0. Only the `Company` inconsistency (OPS-15) is a defect. |
| `isMinifyEnabled = false` | Not refused — undecided. Listed as OPS-08 so it gets a ruling rather than drifting. |

---

## 10 · Suggested order

1. **OPS-01 / OPS-02** — point both update checkers at a Departament feed or disable the control.
   OPS-01 is live in a shipping build today.
2. **OPS-05 / OPS-21** — four About rows and a privacy policy naming another operator. One
   `AppConfig.kt` edit plus a real `CR.md`.
3. **OPS-11** — the desktop Linux/macOS packages do not launch. Broken by our own change.
4. **OPS-06 / OPS-07** — nothing can be published to a store until version codes are distinct and
   a real signing key exists.
5. **OPS-18 / OPS-16 / OPS-17** — retarget the workflows to the live branch, add a release-variant
   and a `test` task to the gate. Without this the rest regresses unnoticed.
6. **OPS-03 / OPS-04** — store listing and fdroid launcher label.
7. **OPS-19** — one revision of `CLAUDE.md`; it is the file every future agent reads first.
