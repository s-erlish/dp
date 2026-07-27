# Android release readiness — Departament VPN

**Written:** 2026-07-26 · **Branch:** `claude/app-audit-agents-hyyftk` · **Method:** reading only.
No source, layout, `.axaml` or build script was edited. No git command and no build was run — two
other waves hold the edit and build locks. Every claim below is anchored to a file and line I read.

---

## The one-paragraph answer

**No release variant of this app has ever been built in this environment, and no CI workflow fires on
the branch the work is on.** The local gate is `./gradlew :app:assembleFdroidDebug`
(`docs/agents/verify-build.sh:47`) — one flavour, one build type, the *debug* one. That single task
skips the release manifest merge, the release resource link, `lintVital*` (which AGP runs on
`assembleRelease` and which *aborts the build* on any fatal-severity issue), the entire playstore
flavour, and R8. Meanwhile `app/build.gradle.kts:63` says `isMinifyEnabled = false` and `:70` signs
the release APK **with the debug key**, so today's "release" build is a debug build wearing a
different folder name: same applicationId, same versionCode, same signing key, interchangeable at
install time. Turning minification on later is not a flag flip — `proguard-rules.pro` contains
nineteen lines and every one of them is a comment, and this app hands Gson-serialised field names
straight to the Xray core as its configuration JSON, so R8 in its current state would produce an app
that cannot start a tunnel. Add to that: both flavours ship launcher shortcuts pointing at
`com.v2ray.ang`, a package that has not existed since the rebrand; the F-Droid flavour is still
*named* "v2rayNG (F-Droid)"; the in-app updater downloads APKs from `2dust/v2rayNG`; and an exported
`depv://` deep link lets any web page silently import routing rules and restart the tunnel.

---

## 1. Signing

### 1.1 There is no release signing config at all — the release APK is debug-signed

`app/build.gradle.kts:61-72`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        // Sign release builds with the debug key so the produced APK is directly installable
        // for full-app testing (no separate keystore/secrets required in CI).
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

There is **no `signingConfigs { create("release") { … } }` block anywhere** in the file. The comment
is honest about why, and it is a defensible choice for a test artefact. It is a release blocker as
written, for four separate reasons:

1. **The debug keystore is machine-local and auto-generated.** AGP creates
   `$ANDROID_USER_HOME/debug.keystore` (alias `androiddebugkey`, password `android`) on first use if
   it is absent. A GitHub-hosted runner starts with a fresh `HOME` every job, so **every CI run
   signs with a different key**. Two consecutive artefacts from `release.yml` cannot be installed
   over one another — users get `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and must uninstall, losing
   every server, subscription and their session.
2. **The debug key's private material is public knowledge.** Anyone can sign an APK with the AOSP
   debug key. For a VPN client that is a supply-chain hole: a repackaged, backdoored build is
   indistinguishable to the OS from the real one.
3. **Google Play rejects debug-signed uploads outright** ("You uploaded an APK signed in debug
   mode"), so the flavour literally called `playstore` cannot be published.
4. **Debug and release are install-interchangeable.** The `debug` build type declares no
   `applicationIdSuffix`, so both build types produce `com.departamentvpn.app[.fdroid]`; the
   `applicationVariants.all` block at `:105-147` applies its `versionCodeOverride` and output naming
   to *every* variant, not just release; and both are signed with the same debug key. A debug APK and
   a release APK of the same flavour therefore share appId, versionCode and key — the OS will install
   one over the other without a word, and the user ends up running a `debuggable` build against the
   production backend (`BACKEND_BASE_URL` is the same `https://web.departament.site/api` in both
   build types, `:44`). The output filenames make this worse: `departament_2.2.1_arm64-v8a.apk` is
   the name of *both*.

**Fix.** Add a real signing config that is absent-tolerant, and give debug a suffix:

```kotlin
// app/build.gradle.kts, before buildTypes {}
val ksFile = System.getenv("DEPARTAMENT_KEYSTORE") ?: "${rootDir}/keystore/release.jks"
val hasReleaseKey = file(ksFile).exists() &&
    listOf("DEPARTAMENT_KEYSTORE_PASSWORD", "DEPARTAMENT_KEY_ALIAS", "DEPARTAMENT_KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrBlank() }

signingConfigs {
    if (hasReleaseKey) create("release") {
        storeFile = file(ksFile)
        storePassword = System.getenv("DEPARTAMENT_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("DEPARTAMENT_KEY_ALIAS")
        keyPassword = System.getenv("DEPARTAMENT_KEY_PASSWORD")
        enableV1Signing = true; enableV2Signing = true
        enableV3Signing = true; enableV4Signing = true
    }
}

buildTypes {
    debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    release {
        signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        // …
    }
}
```

`signingConfig = null` on a machine without secrets produces `app-…-release-unsigned.apk` and a
clear message, instead of a debug-signed APK that *looks* shippable. Then put the build type into the
output filename in the `applicationVariants.all` block so a debug artefact can never be mistaken for
a release one.

### 1.2 How secrets are supplied today, and the two workflows that do not supply them

Only `.github/workflows/build.yml` signs properly, and it does so by bypassing the DSL entirely
(`:86-99`):

```yaml
- name: Decode Keystore
  uses: timheuer/base64-to-file@v2.0.0
  with: { fileName: "android_keystore.jks", encodedString: "${{ secrets.APP_KEYSTORE_BASE64 }}" }
- run: ./gradlew assembleRelease
    -Pandroid.injected.signing.store.file=… -Pandroid.injected.signing.store.password=${{ secrets.APP_KEYSTORE_PASSWORD }}
    -Pandroid.injected.signing.key.alias=${{ secrets.APP_KEYSTORE_ALIAS }} -Pandroid.injected.signing.key.password=${{ secrets.APP_KEY_PASSWORD }}
```

Four secrets: `APP_KEYSTORE_BASE64`, `APP_KEYSTORE_PASSWORD`, `APP_KEYSTORE_ALIAS`,
`APP_KEY_PASSWORD`. AGP's `android.injected.signing.*` properties take precedence over the variant's
DSL `signingConfig`, so this *does* override the debug key — but nothing in the repository states
that dependency, and the coupling is invisible from `build.gradle.kts`. Anyone reading only the
Gradle file concludes, correctly for two of the three workflows, that releases are debug-signed.
Worth verifying once with `apksigner verify --print-certs` on a build.yml artefact.

**On a machine without the secrets**, `secrets.APP_KEYSTORE_BASE64` expands to the empty string,
`base64-to-file` writes a zero-byte `.jks`, and Gradle fails at `validateSigningRelease` with a
keystore-format error. The step is unconditional, so **every push to `master` from a fork or from a
repo whose secrets are not configured fails**, and it fails at the last and slowest step, after the
NDK install and the hev-socks5-tunnel compile.

`release.yml` and `debug.yml` inject nothing. `release.yml:61-63` is explicit —
`Build release APK (arm64-v8a, playstore only, debug-signed)` — so the artefact people actually
download from the working branch is debug-signed with an ephemeral key.

---

## 2. Minification and resource shrinking

### 2.1 Both are off; the rules file is empty

- `app/build.gradle.kts:63` — `isMinifyEnabled = false`.
- `isShrinkResources` is **never set**, i.e. `false` (and AGP would reject `true` without minify).
- `app/proguard-rules.pro` is 19 lines, **all of them commented out** — it is the untouched AGP
  template. `getDefaultProguardFile("proguard-android-optimize.txt")` is listed at `:65` but a
  `proguardFiles` entry has no effect while `isMinifyEnabled = false`.
- `gradle.properties:31-32` sets `android.r8.strictFullModeForKeepRules=false` and
  `android.r8.optimizedResourceShrinking=false` — R8 opt-out flags carried over from an AGP upgrade,
  configuring a shrinker that never runs.

So the shipped APK carries every class, every method name, every debug-friendly symbol and every
unused resource. Practical costs: the APK is roughly twice the size it needs to be, the app is
trivially reverse-engineered (relevant for a censorship-circumvention client), and stack traces in
`LogcatActivity` are unminified.

**The important part is what happens the day someone flips the flag.** Below is every class and
member that would be stripped or renamed, verified against the libraries' own consumer rules that I
read out of the Gradle cache. Two of the four risks the brief names turn out to be already covered —
saying so is as much the job as finding the rest.

### 2.2 Already safe — do not write rules for these

| Risk | Why it is already covered |
|---|---|
| **MMKV** | `mmkv-static-1.3.16.aar` ships `proguard.txt`: `-keepclasseswithmembers,includedescriptorclasses class com.tencent.mmkv.** { native <methods>; long nativeHandle; private static *** onMMKVCRCCheckFail(***); private static *** onMMKVFileLengthError(***); private static *** mmkvLogImp(...); private static *** onContentChangedByOuterProcess(***); }` — the JNI surface **and** the four reverse-callbacks the C++ side resolves by name, including `onContentChangedByOuterProcess`, which is the one `MULTI_PROCESS_MODE` actually exercises (all seven stores in `MmkvManager.kt:35-41` plus `AuthTokenStore` and `KeystoreKeyProvider` open multi-process). No app rule needed. |
| **WorkManager** | `work-runtime`'s consumer rules include `-keepnames class * extends androidx.work.ListenableWorker` and `-keepclassmembers public class * extends androidx.work.ListenableWorker { public <init>(...); }`. `SubscriptionUpdater.UpdateTask` (`handler/SubscriptionUpdater.kt:231`) is a public nested class of a Kotlin `object`, i.e. `…SubscriptionUpdater$UpdateTask` with a public two-arg constructor — matched by both. `work-multiprocess` adds keeps for `RemoteWorkManagerClient` and `RemoteListenableDelegatingWorker`. The class name survives obfuscation, which is what matters: WorkManager persists the worker's FQN in its Room database, and `scheduleOne()` enqueues periodic work with `ExistingPeriodicWorkPolicy.REPLACE` (`:87`, `:117`) while `updateAllNow()` uses `ExistingWorkPolicy.KEEP` (`:152`), so rows do survive across app upgrades. No app rule needed. |
| **Custom views and Preferences in XML** | AGP generates `aapt_rules.txt` keeps for every class named in a manifest or resource file. `res/xml/pref_settings.xml` contains no custom `Preference` subclass and no `android:fragment` attribute (verified by grep), so even that path is inert. |

### 2.3 Casualty 1 — the Xray core config is Gson field names (catastrophic, 100 % reproducible)

This is the one that turns a minified build into an app that cannot connect at all.

```
core/CoreConfigManager.kt:599   content = JsonUtil.toJsonPretty(v2rayConfig) ?: ""
core/CoreServiceManager.kt:265  coreController.startLoop(result.content, tunFd)
```

`dto/V2rayConfig.kt` is **277 declared fields across ~40 nested data classes**, and exactly **two**
of them carry `@SerializedName` (`:177` `"User-Agent"`, `:179` `"Accept-Encoding"`). Every other JSON
key in the configuration handed to the native core — `inbounds`, `outbounds`, `streamSettings`,
`tlsSettings`, `sockopt`, `fingerprint`, `publicKey`, `shortId`, `serviceName`, all of it — **is the
Kotlin field name**. Gson's own bundled `META-INF/proguard/gson.pro` says so in its header: *"These
rules are not complete; users will most likely have to add additional rules for their specific
classes."* It keeps `Signature`, keeps `TypeToken` subclasses, and for fields it only emits
`-keepclassmembers,allowobfuscation` on **annotated** fields — explicitly allowing obfuscation on the
assumption that `@SerializedName` supplies the name. Here it does not.

Result with minify on: `startLoop()` receives `{"a":{"b":1},"c":[…]}`, Xray rejects it, the tunnel
never comes up. Every server, every protocol, every build.

The same mechanism silently destroys three more things:

- **Persisted state.** `MmkvManager` stores `ProfileItem`, `SubscriptionItem`, `AssetUrlItem`,
  `RulesetItem`, `ServerAffiliationInfo`, `WebDavConfig` as Gson JSON. `ProfileItem` alone has ~60
  unannotated fields. Obfuscation is *stable within one build*, so a fresh install writes `{"a":…}`
  and reads it back correctly — **the damage is invisible until the next release**, when R8 assigns
  different names and every stored server, subscription and asset URL becomes unreadable. Users lose
  their whole library on update, with no error.
- **Backend DTOs.** `auth/dto/{AuthDtos,SubscriptionDtos,PaymentDtos,MiscDtos,PublicDtos}.kt` parse
  the bot backend's responses through `ApiGson.instance`. A handful carry `@SerializedName`
  (`AuthDtos.kt:100,108,120`; `SubscriptionDtos.kt:92,154`; `MiscDtos.kt:30,44,57,59,61`); the
  overwhelming majority do not. Renamed → every field null/empty → sign-in, subscription, devices,
  balance and payment history all come back blank with no error, because `ApiGson`'s null-tolerant
  String adapter converts the failure into `""`.
- **Enums by name.** `ProfileItem.configType: EConfigType` is persisted; Gson writes enums via
  `Enum.name()`. `proguard-android-optimize.txt` keeps only `values()`/`valueOf()`, not the constant
  *fields*; R8 renames the constants and rewrites the name strings in `<clinit>` to match. Across two
  differently-obfuscated builds, `"VMESS"` becomes `"a"` and every stored profile's type is
  unresolvable.

**Rules that save it:**

```proguard
# ── Gson model classes: the field names ARE the wire format (Xray config + MMKV + backend API)
-keepclassmembers class com.v2ray.ang.dto.** { <fields>; }
-keepclassmembers class com.v2ray.ang.auth.dto.** { <fields>; }
-keep class com.v2ray.ang.dto.** { *; }
-keep class com.v2ray.ang.auth.dto.** { *; }
# Enum constants are serialised by name (ProfileItem.configType and friends)
-keep enum com.v2ray.ang.enums.** { *; }
-keepclassmembers enum com.v2ray.ang.** { *; }
# Gson generic reflection
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
```

### 2.4 Casualty 2 — the gomobile/libv2ray JNI surface (native abort at startup)

`libv2ray.aar` is generated by `gomobile bind`. **gomobile AARs carry no consumer ProGuard rules**,
and the Go runtime resolves the Java side by *literal name* through `FindClass` / `GetMethodID` /
`GetFieldID` at `Seq` init. The exact surface this app touches is recorded in
`docs/agents/setup-env.sh` (the stub mirrors the real AAR):

| Symbol | Used at | What R8 does to it |
|---|---|---|
| `go.Seq` (`setContext`, plus internal `Seq.Ref`, `Seq.Proxy`, `refnum`, `incRefnum`, `destroyRef`) | `core/CoreNativeManager.kt:7,31` | Package and class renamed → `FindClass("go/Seq$Ref")` returns null → abort in `_seq.go` init, before any UI. |
| `libv2ray.Libv2ray` (`initCoreEnv`, `reconcileBrowserDialer`, `checkVersionX`, `measureOutboundDelay`, `newCoreController`) | `CoreNativeManager.kt:34,48,63,79,94` | Its members *are* `native`, so `proguard-android-optimize.txt`'s `-keepclasseswithmembernames … native <methods>` saves the names — **but only the natives**. Any non-native helper gomobile emits alongside them is renamed. |
| `libv2ray.CoreController` (`startLoop`, `stopLoop`, `measureDelay`, `registerProcessFinder`, `queryAllOutboundTrafficStats`, `getIsRunning`) | `CoreServiceManager.kt:57,265` | Go constructs this object from C via `NewObject` on a name-resolved class. Renaming the class breaks construction even though its methods are native. |
| `libv2ray.CoreCallbackHandler` — `startup()`, `shutdown()`, `onEmitStatus(long, String)` | implemented by `private class CoreCallback` at `CoreServiceManager.kt:422-452` | **Pure Java interface, no native members — nothing keeps it.** Go calls `GetMethodID(cls,"onEmitStatus","(JLjava/lang/String;)J")`; after renaming that returns null and the core cannot report status or request shutdown. |
| `libv2ray.ProcessFinder` — `findProcessByConnection(String,String,long,String,long)` | implemented by `private class XrayProcessFinder` at `CoreServiceManager.kt:461-464` | Same shape, same failure. Per-app routing loses its uid lookup. |

**Rule that saves it:**

```proguard
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**
```

### 2.5 Casualty 3 — the hev-socks5-tunnel JNI entry points (currently saved by accident)

`service/TProxyService.kt:21-37` declares three `external` functions in a `@JvmStatic` companion:
`TProxyStartService`, `TProxyStopService`, `TProxyGetStats`, backed by
`System.loadLibrary("hev-socks5-tunnel")`. `compile-hevtun.sh:29` builds that library with
`-DPKGNAME=com/v2ray/ang/service`, so the C symbols are
`Java_com_v2ray_ang_service_TProxyService_TProxyStartService` and friends — bound to the **class
package**, `com.v2ray.ang` (unchanged by the rebrand: `namespace = "com.v2ray.ang"`,
`app/build.gradle.kts:8`), not to `applicationId`.

These survive today only because `proguard-android-optimize.txt` contributes
`-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }`. That is an
implicit dependency on which default ProGuard file `:65` names. Make it explicit:

```proguard
-keepclasseswithmembernames,includedescriptorclasses class com.v2ray.ang.service.TProxyService {
    native <methods>;
}
```

### 2.6 What else to add before flipping the flag

```proguard
# Kotlin coroutines / reflection metadata
-keepattributes InnerClasses, EnclosingMethod, *Annotation*
-dontwarn kotlinx.coroutines.**
# OkHttp 5 optional platform providers (build fails on missing classes without these)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

Then turn it on **one step at a time**, verifying a real tunnel between steps:

1. `isMinifyEnabled = true`, `isShrinkResources = false`. Install, connect, import a subscription,
   sign in, restart the app, confirm the server list survived.
2. Build a *second* release, confirm it installs over the first and the server list still survives —
   this is the step that catches the persisted-JSON problem, and only this step catches it.
3. Then `isShrinkResources = true`. No `Resources.getIdentifier` call exists anywhere in the tree
   (verified by grep), so the shrinker has no dynamic-lookup blind spot to worry about; still, check
   `build/outputs/mapping/*/resources.txt` for the eight `custom_routing_*` assets and
   `open_source_licenses.html` — assets are not shrunk, but the drawables the routing screens load
   are.

---

## 3. Flavours

`app/build.gradle.kts:74-85` declares one dimension, `distribution`, with `fdroid`
(`applicationIdSuffix = ".fdroid"`) and `playstore` (no suffix). The **complete** set of differences,
after grepping for every one:

| Difference | Reality |
|---|---|
| `applicationIdSuffix = ".fdroid"` | Works. `${applicationId}` placeholders in the manifest and `BuildConfig.APPLICATION_ID` in code follow it correctly (`BackupActivity.kt:243`, `LogcatActivity.kt:169`, `AppConfig.kt:7`). |
| `buildConfigField DISTRIBUTION` | **Zero readers.** `grep -rn "BuildConfig.DISTRIBUTION"` over the whole tree returns nothing. Declared in both flavours, read by neither. |
| `src/fdroid/res/values/strings.xml` | Overrides `app_name` to **`v2rayNG (F-Droid)`**, against `departament` in `src/main` (`values/strings.xml:3`). |
| `src/fdroid/res/xml/shortcuts.xml` | Overrides the four launcher shortcuts with `android:targetPackage="com.v2ray.ang.fdroid"` (`:15,30,45,60`). |
| Source, dependencies, permissions, manifest | **Identical.** There is no `src/playstore/` at all, no flavour-specific dependency, no flavour-specific manifest. |

Neither flavour builds a coherent product.

### 3.1 The F-Droid build is branded "v2rayNG (F-Droid)" (blocks release)

`src/fdroid/res/values/strings.xml:3` — `<string name="app_name">v2rayNG (F-Droid)</string>`. That
string is the launcher label (`AndroidManifest.xml:48`), the VPN service label (`:232`, `:251`) — the
name Android shows in the system VPN dialog and the persistent notification — and the app-name row
in About. The whole F-Droid distribution channel therefore ships under the upstream name.
**Fix:** delete `src/fdroid/res/values/strings.xml`; `src/main`'s `departament` then applies, and if
a channel marker is wanted, use `resValue("string","app_name","departament")` per flavour rather than
a whole override file.

### 3.2 Every launcher shortcut is broken, in *both* flavours (blocks release)

`src/main/res/xml/shortcuts.xml:14,28,42,56` → `android:targetPackage="com.v2ray.ang"`.
`src/fdroid/res/xml/shortcuts.xml:15,30,45,60` → `android:targetPackage="com.v2ray.ang.fdroid"`.
The real ids are `com.departamentvpn.app` and `com.departamentvpn.app.fdroid`
(`app/build.gradle.kts:13`, `:77`). The rebrand changed `applicationId` and left these behind.

This is not cosmetic. Grepping the whole Kotlin tree for the four shortcut targets returns
**nothing** — `ScSwitchActivity`, `ScScannerActivity`, `ScStartActivity` and `ScStopActivity` have
**zero code entry points**; the shortcut XML is their *only* door. So the four long-press launcher
actions (switch, scan, start, stop) are dead, and four registered activities
(`AndroidManifest.xml:141-160`) are unreachable in a shipped build.
**Fix:** replace both files' `android:targetPackage` with `${applicationId}` — manifest placeholders
are substituted in `res/xml` shortcut resources — or, better, delete
`src/fdroid/res/xml/shortcuts.xml` entirely and keep one file in `src/main`.

### 3.3 The playstore flavour ships a self-updater aimed at someone else's repository (blocks release)

`AppConfig.kt:144-148`:

```kotlin
const val APP_URL            = "$GITHUB_URL/2dust/v2rayNG"
const val APP_API_URL        = "https://api.github.com/repos/2dust/v2rayNG/releases"
const val APP_ISSUES_URL     = "$APP_URL/issues"
const val APP_PRIVACY_POLICY = "$GITHUB_RAW_URL/2dust/v2rayNG/master/CR.md"
```

`UpdateCheckerManager.checkForUpdate()` polls that API, compares the upstream tag against
`BuildConfig.VERSION_NAME` (`2.2.1`), and on a newer upstream release hands the user
`getDownloadUrl()` → `CheckUpdateActivity.kt:130` → `Utils.openUri(this, url)`. Three consequences:

- The user is told "an update is available" for a **different application**, and is handed an APK
  signed with a different key under a different applicationId (`com.v2ray.ang`) — it installs
  *alongside* departament, so they end up running upstream v2rayNG believing they updated.
- `getDownloadUrl()` (`UpdateCheckerManager.kt:90-105`) filters upstream's assets by whether the
  filename contains `fdroid`, matching this fork's own naming scheme against upstream's — brittle
  even in the best case.
- For the Play build this is a **policy violation** (Device and Network Abuse: apps distributed
  through Play must not offer their own update channel). `Utils.isGoogleFlavor()` exists at
  `util/Utils.kt:570` — `BuildConfig.FLAVOR == "playstore"` — and has **zero call sites**. The guard
  that would have solved this was written and never used.

**Fix:** point `APP_API_URL` / `APP_URL` / `APP_ISSUES_URL` / `APP_PRIVACY_POLICY` at this project's
own release feed and its own privacy policy; gate `s.rowCheckUpdate` (`MainActivity.kt:3179`) and the
About entry (`AboutActivity.kt:91`) on `!Utils.isGoogleFlavor()`; and if the playstore flavour is
real, add `src/playstore/` with the row removed rather than hidden.

### 3.4 The playstore flavour produces APKs, not a bundle

Nothing in the build generates an AAB — no `bundle {}` block, and no workflow invokes
`bundlePlaystoreRelease`. Google Play has required App Bundles for new applications since August
2021. Combined with §1.1 (debug signing) and §4.1 (identical versionCodes), the `playstore` flavour
cannot be uploaded to Play in any form today. Either commit to a bundle (`bundlePlaystoreRelease`,
with ABI splits handled by Play rather than by `splits { abi { … } }`), or rename the flavour to
something honest such as `direct`.

### 3.5 Orphan source sets

`app/src/dev/res/values/strings.xml` (`v2rayNG (DEV)`) and
`app/src/pre_release/res/values/strings.xml` (`v2rayNG (PR)`) survive from upstream build types that
no longer exist. Gradle silently ignores source sets with no matching variant, so they are inert —
and misleading to the next reader. Delete both.

---

## 4. Version and packaging

### 4.1 All five playstore APKs share one versionCode (blocks Play upload)

`app/build.gradle.kts:126-146`, the non-fdroid branch:

```kotlin
val versionCodes = mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)
…
output.versionCodeOverride = (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
```

Every entry maps to `4`, so **every ABI split gets `4 * 1000000 + 731 = 4000731`**. Multi-APK
delivery requires distinct, ordered versionCodes per ABI; Play rejects the second upload with
"Version code 4000731 has already been used". The fdroid branch at `:110-112` gets this right
(`arm64-v8a`→1, `armeabi-v7a`→2, `x86_64`→3, `x86`→4, `universal`→0, offset `5000000`), which makes
the playstore map look like a copy-paste that lost its distinct values.

**Fix:** give the playstore map the same distinct per-ABI values as fdroid, keeping `universal`
lowest so a device-specific split always wins:
`mapOf("universal" to 0, "arm64-v8a" to 1, "armeabi-v7a" to 2, "x86_64" to 3, "x86" to 4)`.

### 4.2 versionCode/versionName strategy is manual and undocumented

`versionCode = 731` / `versionName = "2.2.1"` (`:16-17`) are hand-edited literals. Nothing derives
one from the other, nothing derives either from a tag, and `build.yml`'s `release_tag` input is used
only to name the GitHub release — it never reaches the build. So the tag, the versionName and the
versionCode can disagree with no check anywhere. Worth at minimum a comment recording the scheme
(`731` ↔ `2.2.1` is not obvious), and better, a `versionCode` computed from `versionName`.

### 4.3 The legacy Variant API is load-bearing

`:105` uses `applicationVariants.all { … }` and `:115` casts to
`com.android.build.gradle.internal.api.ApkVariantOutputImpl` — an AGP **internal** class, imported by
fully-qualified name. It works today only because `gradle.properties` carries four AGP-9 opt-out
flags: `android.newDsl=false` (`:34`), `android.builtInKotlin=false` (`:33`),
`android.r8.strictFullModeForKeepRules=false` (`:31`),
`android.r8.optimizedResourceShrinking=false` (`:32`). These are transitional escape hatches. With
AGP 9.2.1 and Gradle 9.4.1 the whole per-ABI versionCode and output-naming block is one AGP upgrade
away from breaking, and there is no comment recording that. The modern replacement is the
`androidComponents { onVariants { … } }` API.

### 4.4 The libv2ray AAR the repo does not contain

`app/build.gradle.kts:164` pulls `fileTree("libs", include = ["*.aar","*.jar"])`, and `:87-91` points
`jniLibs.srcDirs` at the same `libs` directory. Nothing in the repository provides `libv2ray.aar`;
`.gitignore:31` excludes `*.aar` outright. The supply chain is:

1. `.gitmodules` pins `AndroidLibXrayLite` → `github.com/2dust/AndroidLibXrayLite`.
2. CI runs `git describe --tags --abbrev=0` **inside the submodule** to derive `CURRENT_TAG`
   (`build.yml:64-70`), then downloads `libv2ray.aar` from that tag's release with
   `robinraju/release-downloader`.
3. `hev-socks5-tunnel` (second submodule) is compiled to `.so` by `compile-hevtun.sh` and copied to
   `V2rayNG/app/libs/`.

Three fragilities worth writing down:

- **The version is implicit.** Nothing pins the AAR — it is whatever tag the submodule commit
  happens to describe as. A submodule bump silently changes the native core. There is no checksum
  and no `Verify AAR` step, so a compromised or re-tagged upstream release is consumed unchecked.
  Add a `sha256sum -c` step against a committed hash.
- **`git describe` needs tags.** It works because `actions/checkout` is configured
  `fetch-depth: '0'` with `submodules: 'recursive'`. Anyone who "optimises" that to a shallow clone
  breaks the build in a way whose error message points at the wrong thing.
- **The local stub collides with the real AAR if both are present.** `docs/agents/setup-env.sh`
  writes `app/libs/libv2ray-stub.jar`, matched by the same `fileTree` include. On a clean CI
  checkout only the AAR exists, so there is no clash — but on a developer machine that has run
  `setup-env.sh` and then downloads the real AAR, `libs/` holds both `go/Seq` and `libv2ray/*` twice
  and the build fails on duplicate classes. `.gitignore:78` keeps the stub out of git; a `README`
  line in `libs/` would keep it out of confusion.

### 4.5 ABI splits and native packaging

`:20-37` — splits are on with `arm64-v8a, armeabi-v7a, x86_64, x86`, and
`isUniversalApk = abiFilterList.isNullOrEmpty()`, so passing `-PABI_FILTERS=arm64-v8a` (as both
branch workflows do) drops the universal APK. Sound.

`:154-158` sets `jniLibs { useLegacyPackaging = true }`, which puts `android:extractNativeLibs="true"`
into the merged manifest. That is fine for `System.loadLibrary`, but Play Console flags it ("your app
can be smaller if you disable legacy packaging") and it costs the user roughly the size of the Go
core twice on disk. Since `minSdk = 24`, uncompressed packaging is available; test it, because the Go
`libgojni.so` is exactly the case legacy packaging was kept for.

`ndkVersion = "28.2.13676358"` (`:10`) is declared although the Gradle project has **no**
`externalNativeBuild` and no CMake/ndk-build of its own — both native libraries arrive prebuilt. AGP
still resolves it for symbol stripping, so a machine without that exact NDK revision fails a build
that has `.so` files present. Both workflows install it; the local `setup-env.sh` does not, which is
why the local gate only passes while `app/libs/` has no `.so`.

`multiDexEnabled = true` (`:18`) plus the `multidex` dependency (`:213`) and `MultiDexApplication`
(`AngApplication.kt:11`) are unnecessary at `minSdk = 24` — native multidex has been available since
API 21. Harmless, but it ships a redundant library and hides the real dex-count picture.

### 4.6 Localization ships a half-translated UI

`res/values/strings.xml` — the **default** locale — is Russian (`:5` `title_servers` = «Серверы»,
488 strings), and `res/values-ru/strings.xml` duplicates 476 of them. Alongside sit upstream's
`values-{ar,bn,bqi-rIR,fa,vi,zh-rCN,zh-rTW}` with 352-odd *English-derived* translations of the
*old* string set. A device set to Persian therefore gets ~352 Persian strings and ~140 Russian ones
in the same screen; a device set to English gets 100 % Russian.

The duplication is also an active trap for the copy waves: `values-ru` wins for the primary audience,
so **an edit made only to `values/strings.xml` is invisible to every Russian-locale user**.
**Fix:** pick one. Either make `values/` the Russian master and delete `values-ru/`, or make
`values/` English and keep `values-ru/`. Then add
`androidResources { localeFilters += listOf("ru","en") }` so the APK stops shipping five stale
partial translations. `android:localeConfig` is also absent, so the `PREF_LANGUAGE` setting cannot
appear in Android 13+'s system per-app-language picker.

### 4.7 The bundled OSS licence page is stale

`src/main/assets/open_source_licenses.html` (51 KB) is a checked-in file that
`AboutActivity.kt:139` loads into a WebView. `build.yml:98` runs `./gradlew licenseFdroidReleaseReport`,
but `com.jaredsburrows.license` writes to `build/reports/licenses/` and there is **no `licenseReport
{ copyHtmlReportToAssets = true }`** block — so the CI task's output is discarded and the shipped
asset is whatever was last pasted in by hand. It predates `androidx.browser` (`:178`) and the rest of
this fork's dependency changes, so the app ships an inaccurate attribution page.

---

## 5. Manifest for release

### 5.1 Exported components

| Component | Line | Assessment |
|---|---|---|
| `.ui.MainActivity` | 54-71 | Correct. LAUNCHER + LEANBACK_LAUNCHER + the QS tile preferences action. |
| `.ui.UrlSchemeActivity` | 162-190 | **Dangerous — see §5.2.** |
| `.receiver.WidgetProvider` | 268-280 | **Over-exported — see §5.3.** |
| `.receiver.BootReceiver` | 281-288 | Correct: `BOOT_COMPLETED` requires `exported="true"`, and `BootReceiver.kt` gates on `decodeStartOnBoot()` and a selected server. |
| `.service.QSTileService` | 290-304 | Correct: protected by `BIND_QUICK_SETTINGS_TILE`. `foregroundServiceType="specialUse"` on a `TileService` is meaningless but harmless. |
| `.ui.TaskerActivity` | 306-313 | Standard Tasker plugin contract. |
| `.receiver.TaskerReceiver` | 315-323 | **Unprotected.** `exported="true"`, `tools:ignore="ExportedReceiver"`, no permission — any installed app can fire `com.twofortyfouram.locale.intent.action.FIRE_SETTING` and start or stop the tunnel. Upstream behaviour, but it is a real surface on a privacy product. Guard with `com.twofortyfouram.locale.permission.FIRE_SETTING` or drop Tasker support. |

All other activities, both content providers, and `CoreVpnService` / `CoreProxyOnlyService` /
`CoreTestService` are `exported="false"` and correctly so; `CoreVpnService` additionally holds
`android:permission="android.permission.BIND_VPN_SERVICE"` (`:233`).

One more, not in the manifest: `Utils.receiverFlags()` (`util/Utils.kt:557-561`) returns
`ContextCompat.RECEIVER_EXPORTED` on API 33+, so the *dynamically* registered service/UI receivers
are exported to every app on the device too. Those carry `MSG_STATE_*` control messages. Unless a
third party genuinely needs to drive them, that should be `RECEIVER_NOT_EXPORTED`.

### 5.2 `depv://` deep links perform unconfirmed, destructive actions (blocks release)

`AndroidManifest.xml:182-189` registers `UrlSchemeActivity` for scheme `depv` with
`BROWSABLE` + `DEFAULT` and **no host restriction**, so *any* `depv://…` URI matches — from any app,
and from any web page the user taps a link on. `ui/UrlSchemeActivity.kt:83-120` then dispatches, with
no confirmation dialog and no origin check:

- `depv://connect` / `open` → `CoreServiceManager.startVService(this)`
- `depv://disconnect` / `close` → stops the tunnel
- `depv://toggle` → toggles it
- `depv://import/{base64}` → batch-imports servers
- `depv://add/{url}` → imports a subscription or config by URL
- `depv://routing/add/{base64}` and `routing/onadd/{base64}` → **imports routing rulesets, and
  `onadd` restarts the core to apply them**

The last one is the serious one: a web page can hand the app a routing table of its choosing and have
it applied to a live tunnel. `disconnect` is nearly as bad — a hostile page can drop the user's VPN
silently. The legacy `v2rayng://install-config|install-sub` filter (`:172-179`) has the same problem
for imports.

**Fix:** every deep link that mutates configuration or tunnel state must render a confirmation sheet
naming what will be added or changed and requiring an explicit tap. `connect`/`disconnect`/`toggle`
should additionally be restricted — either to callers holding a signature-level permission, or
removed from the browsable filter and kept only for the app's own widget and tile.

(Also: `UrlSchemeActivity` inflates `ActivityLogcatBinding` (`:25`) — it renders the logcat screen's
layout as its own. Cosmetic, but it is the screen a deep-link user briefly sees.)

### 5.3 The widget receiver accepts VPN control broadcasts from any app

`AndroidManifest.xml:268-280` exports `WidgetProvider` with an intent filter that includes
`${applicationId}.action.widget.click`. `WidgetProvider.onReceive` (`receiver/WidgetProvider.kt:69-75`)
maps that action straight to start/stop of the tunnel. `exported="true"` is mandatory for
`APPWIDGET_UPDATE`, but the custom action rides along on the same filter, so any app can broadcast it.

The widget's own PendingIntent is **explicit** (`Intent(context, WidgetProvider::class.java)`,
`:37-38`), and explicit broadcasts do not need a matching filter — so removing the
`${applicationId}.action.widget.click` `<action>` line costs nothing and closes the hole.
`${applicationId}.action.activity` is genuinely needed (it is an implicit, package-scoped broadcast
from `MessageUtil.kt:84`); protect it with a signature-level permission declared by the app and set
`android:permission` on the receiver.

### 5.4 Backup rules: none, and the session store is Keystore-sealed

`:45` sets `android:allowBackup="true"`, and there is **no `android:fullBackupContent`, no
`android:dataExtractionRules`**. At `targetSdk = 37` that means both Auto Backup to Google Drive and
device-to-device transfer copy the app's entire `files/` directory — which is where every MMKV store
lives.

That collides head-on with `auth/AuthTokenStore.kt` and `auth/KeystoreKeyProvider.kt`. The session
store (`ID = "departament_auth"`) is **encrypted with a key sealed by the AndroidKeyStore**, and the
sealed ciphertext+IV sit in a second plaintext MMKV, `"departament_keyholder"`
(`KeystoreKeyProvider.kt:33,97-98`). Backup restores both files; the AndroidKeyStore key is
hardware-bound and non-exportable, so it does **not** restore. On the new device the store resolves
to `CryptKeyState.Unsealable` → `store()` returns null → the user's servers come back but their
session does not, with no explanation.

Worse, MMKV files are memory-mapped with a companion `.crc`. A backup that captures the two out of
step yields a CRC mismatch on restore, which MMKV resolves by discarding the file — the exact
`onMMKVCRCCheckFail` path its consumer ProGuard rule exists to protect.

**Fix** — add `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`, wire both on
`<application>`, and exclude the two auth stores from both cloud backup and D2D transfer:

```xml
<!-- backup_rules.xml (API ≤ 30) -->
<full-backup-content>
    <exclude domain="file" path="mmkv/departament_auth" />
    <exclude domain="file" path="mmkv/departament_auth.crc" />
    <exclude domain="file" path="mmkv/departament_keyholder" />
    <exclude domain="file" path="mmkv/departament_keyholder.crc" />
</full-backup-content>
```

with the same four exclusions inside `<cloud-backup>` **and** `<device-transfer>` in
`data_extraction_rules.xml` (API 31+). Decide deliberately whether the server list should be backed
up at all — for a censorship-circumvention client, "my subscription URLs are in Google's cloud" is a
product decision, not a default.

### 5.5 `QUERY_ALL_PACKAGES` is a Play review blocker

`:27-29`. `tools:ignore="PackageVisibilityPolicy,QueryAllPackagesPermission"` silences lint; it does
not silence Play review, which requires a declaration form and grants the permission only for a
short list of use cases (accessibility, antivirus, device search, file managers, banking-fraud
prevention, device automation). "Per-app VPN routing" is not on it, and VPN apps requesting it are
routinely rejected.

The app needs it only to list launchable apps for per-app proxy (`util/AppManagerUtil.kt`). The
supported replacement covers exactly that:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

Ship that in `src/main` and keep `QUERY_ALL_PACKAGES` — if it is needed at all — only in a
`src/fdroid/AndroidManifest.xml`.

### 5.6 Other permission notes

| Permission | Verdict |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Needed. (`ACCESS_NETWORK_STATE` is declared twice — `:30` and a commented duplicate at `:39`; delete the comment.) |
| `CHANGE_NETWORK_STATE` | Genuinely needed — `CoreVpnService.kt:44` documents it as the requirement for `requestNetwork`. |
| `CAMERA` + the two `uses-feature camera … required="false"` | Needed for QR scanning; correctly marked optional so TV and camera-less devices still install. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (`minSdkVersion="34"`) | Needed. All three services declare `foregroundServiceType="specialUse"` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value (`vpn`/`proxy`/`test`). Play requires a written justification for `specialUse` at submission — prepare it. |
| `POST_NOTIFICATIONS` | Needed and correctly requested at runtime (`MainActivity.kt:453`). |
| `RECEIVE_BOOT_COMPLETED` | Needed by `BootReceiver`. |

### 5.7 Leanback / Android TV

Declared coherently: `android.software.leanback` optional (`:19-21`),
`android.hardware.touchscreen` optional (`:22-24`), `LEANBACK_LAUNCHER` on MainActivity (`:62`),
`android:banner="@mipmap/ic_banner"` on `<application>` (`:46`, backed by real
`mipmap-anydpi-v26/ic_banner.xml` + `mipmap-xhdpi/ic_banner.png`), and the two TV transfer activities
at `:129-137`. The `tools:ignore="MissingLeanbackLauncher"` on the `<manifest>` root (`:4`) is now
redundant — the leanback category *is* declared — and should be dropped so the check can do its job
again.

The real TV gap is not the manifest: the shipped UI is a phone bottom-nav layout with 36 dp touch
targets, and nothing in it is focus-navigable by D-pad. Declaring `LEANBACK_LAUNCHER` puts the app in
the TV launcher; keeping it there is a product commitment nobody has met yet. Either invest in a TV
layout or drop the category and the banner.

### 5.8 Components declared but unreachable

| Component | Manifest | Reachability |
|---|---|---|
| `.ui.ScSwitchActivity`, `.ui.ScScannerActivity`, `.ui.ScStartActivity`, `.ui.ScStopActivity` | 141-160 | **Unreachable.** Zero Kotlin references; only door is the broken `shortcuts.xml` (§3.2). |
| `.ui.SubSettingActivity` | 110-111 | **Unreachable.** Zero references of any kind in the tree. Either wire it or delete the class and its manifest entry. |
| `.ui.SettingsActivity` | 88-90 | **Now reachable** — `MainActivity.kt:3177` `s.rowAdvanced.setOnClickListener { requestActivityLauncher.launch(SettingsActivity.newIntent(this)) }`. This closes `STATE-OF-WORK.md` §3.1, the report's single worst item; the fix landed in the working tree after that report was written and nothing else records it. |
| `.ui.CheckUpdateActivity`, `.ui.LogcatActivity` | 191-193, 100-102 | Reachable (`MainActivity.kt:3178-3179`, `AboutActivity.kt:85,91`). Note `CheckUpdateActivity` should be flavour-gated — §3.3. |

### 5.9 Cleartext and user CAs

`:52` `android:usesCleartextTraffic="true"` plus `res/xml/network_security_config.xml`, whose
`base-config` permits cleartext **and** trusts `src="user"` certificates for every destination. The
app needs cleartext for `http://` subscription URLs and the loopback proxy; it does not need to trust
user-installed CAs for `web.departament.site`. As written, any user-installed certificate — including
one pushed by a hostile MDM or an interception proxy — can read the session JWT and every
subscription URL. Scope it:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors><certificates src="system"/></trust-anchors>
    </base-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">departament.site</domain>
        <trust-anchors><certificates src="system"/></trust-anchors>
    </domain-config>
    <debug-overrides>
        <trust-anchors><certificates src="user"/></trust-anchors>
    </debug-overrides>
</network-security-config>
```

`<debug-overrides>` applies only to `debuggable` builds, which is exactly where interception is
wanted.

---

## 6. CI workflows

### 6.1 No workflow runs on the branch the work is on (blocks release)

| Workflow | Trigger | Fires on `claude/app-audit-agents-hyyftk`? |
|---|---|---|
| `build.yml` | `push: master`, `workflow_dispatch` | No |
| `release.yml:7` | `push: claude/vpn-client-happ-design-mq51pv` | No |
| `debug.yml:7` | `push: claude/vpn-client-happ-design-mq51pv` | No |
| `fastlane.yml` | `push`/`pull_request` on `master` | No |

Both branch workflows point at a branch that predates the current one. **Nothing in CI has compiled
this code.** Every "the build is green" claim in the state documents rests on one local
`assembleFdroidDebug`. First fix: change both branch triggers to `claude/**`, or add
`pull_request: branches: [master]` so at least PR-time verification exists.

### 6.2 The local gate never builds a release variant, and never builds playstore

`docs/agents/verify-build.sh:47` runs `./gradlew :app:assembleFdroidDebug --no-daemon`, full stop.
What that never exercises:

- `lintVitalAnalyzeRelease` / `lintVitalReportRelease`, which AGP attaches to `assembleRelease` and
  which **abort the build** on any fatal-severity issue. There is no `lint { }` block and no
  `lint-baseline.xml` anywhere in the tree, so nothing is suppressed and nothing has been measured.
  Given how much layout and string churn the current UI waves are producing, this is where a release
  build will fail first.
- The **playstore** flavour — never built, locally or in CI on this branch.
- The release manifest merge (`tools:` attribute stripping, `extractNativeLibs`, `debuggable`
  removal) and the release resource link.
- R8 (moot today, §2).

**Fix:** add a release lane to the gate —
`./gradlew :app:lintVitalFdroidRelease :app:assemblePlaystoreRelease` — run less often than the debug
lane, but run at least once before anything is called shippable.

### 6.3 `build.yml` installs the wrong SDK packages

`build.yml:29`: `packages: 'platforms;android-36.1 build-tools;36.1.0 platform-tools'`, against
`compileSdk = 37` / `targetSdk = 37` (`app/build.gradle.kts:9,15`). AGP's SDK auto-download will
probably rescue this by fetching `platforms;android-37.0` itself, but it is unpinned, it fails on any
runner with restricted network, and it means the declared package list is a lie about what the build
uses. Set `packages: 'platforms;android-37.0 build-tools;37.0.0 platform-tools'` —
`docs/agents/setup-env.sh:23` already records that `platforms;android-37` (without `.0`) does not
exist and makes `sdkmanager` fail.

### 6.4 `build.yml` rewrites `build.gradle.kts` with a line-numbered `sed`

`build.yml:37-39`:

```yaml
sed -i '10i\
\
    ndkVersion = "28.2.13676358"' ${{ github.workspace }}/V2rayNG/app/build.gradle.kts
```

`app/build.gradle.kts:10` **already is** `ndkVersion = "28.2.13676358"`. The insert therefore adds a
second, identical assignment inside `android { }`. Kotlin permits reassigning a `var`, so it compiles
— today. It is a positional edit against a file two other waves are actively changing: move
`namespace` or `compileSdk` by one line and this injects Kotlin into the middle of a statement, and
the failure will be reported as a syntax error in a file nobody edited. Delete the step; the value is
already in the build file.

### 6.5 `build.yml` fails for anyone without the four signing secrets

The `Decode Keystore` step (`:86-91`) is unconditional and runs on every `push: master`. With
`APP_KEYSTORE_BASE64` unset it produces an empty `.jks` and Gradle fails at signing. Guard it:
`if: ${{ secrets.APP_KEYSTORE_BASE64 != '' }}`, and make the `assembleRelease` invocation append the
`-Pandroid.injected.signing.*` flags only when the keystore file exists.

### 6.6 Four workflows, four different action pin sets

`checkout@v6` (build, fastlane) vs `@v4` (release, debug); `upload-artifact@v7` vs `@v4`;
`setup-android@v4.0.1` vs `@v3`; `release-downloader@v1.13` vs `@v1.12`; `setup-java@v5` vs `@v4`.
`build.yml` and `release.yml`/`debug.yml` are now two independently maintained recipes for the same
build — the libhevtun cache, the `local.properties` write and the `licenseFdroidReleaseReport` call
exist only in the first. Collapse them into one reusable workflow (`workflow_call`) parameterised by
flavour, build type and whether to sign.

### 6.7 Artifact globs cross flavours

`build.yml:101-120` uploads `.../apk/*/release/*arm64-v8a*.apk` — the `*` matches both `fdroid` and
`playstore`, so the artefact named `arm64-v8a` contains two different APKs. `x86-apk`'s glob
`*x86*.apk` additionally catches `x86_64`. Harmless until someone downloads "the" APK.

### 6.8 Fastlane metadata is upstream's

`fastlane/metadata/android/en-US/` holds four files, and all four are 2dust's:

- `title.txt` → `v2rayNG`
- `short_description.txt` → `A V2Ray client for Android, support Xray core and v2fly core`
- `full_description.txt` → HTML pointing at `t.me/github_2dust`
- `images/icon.png` → upstream's icon

`fastlane.yml` validates it and will pass — the metadata is *valid*, just about a different product.
There is no `ru-RU` locale (for a Russian-language product), no `changelogs/` directory, and
`full_description.txt` uses `<p>`/`<h3>` markup that Play's `supply` does not accept. If this
metadata ever reaches a store listing, the store will say "v2rayNG".

### 6.9 The gradle-wrapper.jar hazard

`.gitignore:47` excludes `*.jar` with no negation, and `V2rayNG/gradle/wrapper/gradle-wrapper.jar`
(59 203 bytes) is exactly that pattern. It is presumably tracked from before the rule was added —
CI's `./gradlew` would fail immediately otherwise — but the rule means a future `git add` of that
path silently does nothing, and a clean re-add after any wrapper upgrade drops it. Add
`!V2rayNG/gradle/wrapper/gradle-wrapper.jar` to `.gitignore`, and confirm the current state with
`git ls-files --error-unmatch V2rayNG/gradle/wrapper/gradle-wrapper.jar` (I could not run git
commands in this wave). The same rule shape (`*.so`, `*.aar`) is correct for the generated native
artefacts and should stay.

---

## 7. One consequence of the rebrand worth flagging

`util/Utils.kt:563` — `fun isXray(): Boolean = BuildConfig.APPLICATION_ID.startsWith("com.v2ray.ang")`.
`applicationId` is now `com.departamentvpn.app`, so **`isXray()` is permanently `false`** in every
build of this fork. Two live readers change behaviour because of it:

- `handler/SettingsManager.kt:373` — `getHttpPort() = getSocksPort() + if (isXray()) 0 else 1`, so
  the HTTP port is now SOCKS+1 rather than SOCKS.
- `core/CoreConfigManager.kt:682` — `if (!Utils.isXray())` now adds a **second, HTTP inbound** on
  that port to every generated core configuration.

Both may be fine or even desirable, but neither was chosen: the flag stopped meaning what its name
says the moment the applicationId changed. Rename it to something the build actually knows
(`BuildConfig.FLAVOR`, or a `buildConfigField`), or delete it and make the HTTP inbound an explicit
setting.

---

## 8. Ordered fix list

**Before anything is called releasable** — these are the ones that ship a broken or unshippable
product:

| # | Fix | Where | Size |
|---|---|---|---|
| 1 | Real release signing config, absent-tolerant, plus `applicationIdSuffix = ".debug"` on the debug build type and the build type in the output filename | `app/build.gradle.kts:61-72`, `:105-147` | M |
| 2 | `android:targetPackage` → `${applicationId}` in both shortcut files (revives four dead activities) | `src/main/res/xml/shortcuts.xml:14,28,42,56`; `src/fdroid/res/xml/shortcuts.xml:15,30,45,60` | S |
| 3 | Delete `src/fdroid/res/values/strings.xml` so the F-Droid build stops calling itself v2rayNG | — | S |
| 4 | Distinct per-ABI versionCodes for playstore | `app/build.gradle.kts:127` | S |
| 5 | Confirmation sheet on every mutating `depv://` / `v2rayng://` deep link; restrict `connect`/`disconnect`/`toggle` | `ui/UrlSchemeActivity.kt:83-120`, `AndroidManifest.xml:162-190` | M |
| 6 | Backup + data-extraction rules excluding `departament_auth` and `departament_keyholder` | new `res/xml/*`, `AndroidManifest.xml:45` | S |
| 7 | Point the updater at this project's releases and gate it on `!Utils.isGoogleFlavor()` | `AppConfig.kt:144-148`, `MainActivity.kt:3179`, `AboutActivity.kt:91` | S |
| 8 | Fix the CI branch triggers, then run `lintVitalFdroidRelease` + `assemblePlaystoreRelease` once and fix what falls out | `.github/workflows/*`, `docs/agents/verify-build.sh` | M |

**Before turning minification on** (each is worthless alone):

| # | Fix | Size |
|---|---|---|
| 9 | Write the keep rules of §2.3-2.6 into `app/proguard-rules.pro` | S |
| 10 | `isMinifyEnabled = true`, verify a real tunnel, then build a **second** release and verify the server list survives the upgrade | M |
| 11 | `isShrinkResources = true`, check `resources.txt` | S |

**Store-policy and hygiene, before submission:**

| # | Fix | Size |
|---|---|---|
| 12 | Replace `QUERY_ALL_PACKAGES` with a `<queries>` element in `src/main` | S |
| 13 | Drop `.action.widget.click` from the widget's filter; permission-guard `.action.activity`; `RECEIVER_NOT_EXPORTED` for the dynamic receivers | S |
| 14 | Scope cleartext and user-CA trust to a `domain-config` + `debug-overrides` | S |
| 15 | Decide the playstore flavour: AAB, or rename it `direct` | M |
| 16 | Rewrite `fastlane/metadata/**` for this product; add `ru-RU` and `changelogs/` | S |
| 17 | Collapse `values-ru` into `values` (or vice versa), add `localeFilters`, add `android:localeConfig` | M |
| 18 | Delete `src/dev/` and `src/pre_release/`; remove the redundant `ACCESS_NETWORK_STATE` comment and the stale `MissingLeanbackLauncher` ignore | S |
| 19 | Checksum-verify the downloaded `libv2ray.aar` against a committed hash | S |
| 20 | Decide the TV story: invest in D-pad navigation, or drop `LEANBACK_LAUNCHER` and the banner | L |
| 21 | Rename or delete `Utils.isXray()`; make the second HTTP inbound an explicit decision | S |
| 22 | Move the OSS licence page onto the license plugin (`copyHtmlReportToAssets`) or regenerate it | S |
