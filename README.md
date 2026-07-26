# Departament VPN - Android client

This repository builds the Android client of Departament VPN: a VPN/proxy client that runs an Xray
core locally and gets its servers from the user's Departament subscription. It is one half of a
two-client product; the desktop half lives in a sibling repository (see "The desktop client").

The app works as a plain proxy client with no account at all. Signing in is what adds the
Departament side of the product: subscriptions, devices, tariffs, payments, promo codes, referrals.

## Relationship to upstream

This is a fork of [2dust/v2rayNG](https://github.com/2dust/v2rayNG). Everything that speaks the
protocols - the Xray core wrapper, subscription and share-link parsing, config generation, the
VPN service and the TUN plumbing - is upstream's work and is kept. What this fork adds on top:

- the Departament account layer (`auth/**`) and the screens that use it,
- a rebuilt Russian UI following the design law in `docs/design2026/`,
- product identity: `applicationId com.departamentvpn.app`, its own launcher name, icon and APK
  naming, its own CI workflows.

Upstream's Kotlin package name `com.v2ray.ang` is deliberately unchanged, so file paths and
`import` lines still match upstream and merges stay reviewable. The package name is not the product
name.

## Repository layout

| Path | What it is |
|---|---|
| `V2rayNG/` | Gradle root. Single module, `:app`. |
| `V2rayNG/app/src/main/java/com/v2ray/ang/` | All Kotlin. `ui/**` screens, `viewmodel/**`, `service/**` (VPN + TUN + tile), `handler/**` (config, subscriptions, storage), `core/**` and `fmt/**` (config generation, link parsing), `auth/**` (Departament backend), `tv/**` (Android TV). |
| `V2rayNG/app/src/main/res/` | Layouts, drawables, themes, strings. |
| `V2rayNG/app/src/fdroid/` | Flavour-specific resource overrides. |
| `AndroidLibXrayLite/` | Submodule. The Go project that produces the native core AAR. Not compiled here; only its tag is read (see below). |
| `hev-socks5-tunnel/` | Submodule. C sources of the tun2socks library, built by `compile-hevtun.sh`. |
| `compile-hevtun.sh` | Builds `libhev-socks5-tunnel.so` for four ABIs with the NDK. |
| `docs/design2026/` | The design law and the screen specifications for both clients. |
| `docs/agents/` | Build gate, environment setup, audit and verification notes. |
| `fastlane/` | Store metadata, validated by a workflow. |

## Stack

Kotlin 2.3.10, AGP 9.2.1, Gradle wrapper 9.4.1, built with JDK 21 and targeting Java 17 bytecode
with core library desugaring. `compileSdk` and `targetSdk` 37, `minSdk` 24. XML layouts with
ViewBinding and Material 3 (`Theme.Material3.DayNight`) - there is no Compose in this app.
MMKV for storage, OkHttp + Gson for HTTP, coroutines, WorkManager for
subscription refresh. Android TV is a supported form factor (leanback launcher entry plus `tv/**`).
Versions are pinned in `V2rayNG/gradle/libs.versions.toml`.

## Flavours and variants

Two product flavours in the `distribution` dimension, `fdroid` and `playstore`, and only `release`
is declared under `buildTypes` (`debug` is the implicit AGP default). Every build is therefore
flavour-qualified: the variants are `fdroidDebug`, `fdroidRelease`, `playstoreDebug`,
`playstoreRelease`.

There is no flavour-less variant. `:app:assembleFdroidDebug` is a real task,
`:app:compileDebugKotlin` is not. If a command from upstream's docs or from habit does not exist,
that is usually why.

Other things worth knowing before the first build:

- `fdroid` gets `applicationIdSuffix .fdroid`, so both flavours can be installed side by side.
- APKs are named `departament_<versionName>[-fdroid]_<abi>.apk` and land in
  `V2rayNG/app/build/outputs/apk/<flavour>/<buildType>/`. ABI splits produce four ABIs plus a
  universal APK; narrow that with `-PABI_FILTERS=arm64-v8a`.
- `release` is wired to the debug signing config, so a release build is installable without any
  keystore. CI overrides it with `-Pandroid.injected.signing.*` when it has the secrets.
- `app/src/fdroid/res/values/strings.xml` still carries upstream's `app_name` override, so an
  fdroid build shows the upstream launcher label while a playstore build shows `departament`.
- `app/src/dev/` and `app/src/pre_release/` match no flavour that still exists and are not built.
  They are upstream leftovers.

## Native libraries are not in the repository

`*.aar` and `*.so` are gitignored. Two native pieces have to arrive before a build can produce a
working APK, and the workflows in `.github/workflows/` are the reference for how:

1. **`libv2ray.aar`** - the Xray core wrapped for Android. CI does not build it. It reads the tag
   the `AndroidLibXrayLite` submodule is pinned to (`git describe --tags --abbrev=0` inside the
   submodule) and downloads the `libv2ray.aar` asset from the matching release of
   `2dust/AndroidLibXrayLite` into `V2rayNG/app/libs/`. Bump the core by moving the submodule.
2. **`libhev-socks5-tunnel.so`** - tun2socks, used by `service/TProxyService.kt`. CI runs
   `compile-hevtun.sh`, which needs `NDK_HOME` (NDK 28.2.13676358) and builds `armeabi-v7a`,
   `arm64-v8a`, `x86` and `x86_64` into `./libs/`, then copies that directory into
   `V2rayNG/app/` - `app/libs` is the `jniLibs` source directory.

`app/libs` therefore holds both the AAR and the per-ABI `.so` tree in a real build.

## Build

```bash
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd V2rayNG

./gradlew :app:assembleFdroidDebug          # Kotlin + resources + APK link
./gradlew :app:compileFdroidDebugKotlin     # fast: Kotlin type-check only
./gradlew :app:assemblePlaystoreRelease -PABI_FILTERS=arm64-v8a
```

Gradle locates the SDK through `ANDROID_HOME`, or through a `local.properties` holding
`sdk.dir=...` (gitignored; CI writes it). The Gradle build itself compiles no native code, so the
NDK is needed only for `compile-hevtun.sh`. Android Studio can open `V2rayNG/` directly.

CI workflows, all in `.github/workflows/`:

- `build.yml` - the release pipeline. Installs the NDK, builds or restores libhevtun, downloads
  `libv2ray.aar`, runs `licenseFdroidReleaseReport`, then `assembleRelease` signed from secrets,
  and uploads per-ABI APKs.
- `debug.yml` / `release.yml` - single-flavour, single-ABI convenience builds
  (`assemblePlaystoreDebug` and `assemblePlaystoreRelease`). Manual dispatch, plus pushes to the
  one dev branch named inside each file.
- `fastlane.yml` - validates the store metadata under `fastlane/`.

## Verifying a change in this environment

This working environment cannot reach `github.com`, so the real `libv2ray.aar` cannot be
downloaded. Setup generates a small type-check stub instead:

```bash
bash /home/user/dp/docs/agents/setup-env.sh              # once per container, idempotent
bash /home/user/dp/docs/agents/verify-build.sh android    # or: desktop | both
```

The gate passes only on `BUILD: SUCCESSFUL` **and** `NEW WARNINGS: 0`, compared against the
recorded baselines next to the script; it also reports whether the compiler actually ran, because
an up-to-date build proves nothing. It serialises builds behind a lock, so it can wait a while
before starting.

The stub carries only the class surface the app compiles against. An APK built here links against
it and cannot run a tunnel - runnable APKs come from CI. Never reshape app code to fit the stub;
extend the stub in `setup-env.sh`. Details and the rest of the environment's sharp edges are in
`docs/agents/BUILD-VERIFY.md`.

## Account, subscriptions and payments

The account layer lives in `app/src/main/java/com/v2ray/ang/auth/`:

- `BackendConfig.kt` is the single configuration point. The backend base URL, the Telegram bot
  username and the subscription `User-Agent` are `buildConfigField`s declared in
  `V2rayNG/app/build.gradle.kts` and are read only through this object, which also lists every
  endpoint path. Nothing else in the app should hardcode any of them.
- Login is optional by design. When no base URL is configured, `isConfigured()` is false and the
  app must stay fully usable with no backend.
- Auth is a bearer JWT with a 7-day lifetime and no refresh endpoint. `AuthTokenStore` keeps it in
  a dedicated MMKV store encrypted with a key sealed by the Android Keystore, falling back to plain
  MMKV rather than crashing. Tokens and subscription URLs are never logged.
- `SubscriptionSyncManager` imports the account's subscriptions into the app's normal subscription
  plumbing (`MmkvManager`, `AngConfigManager.updateConfigViaSub`, `SubscriptionUpdater`) rather
  than parsing anything itself.
- Sign-in is Telegram (deep link to the bot, then polling) or email and password with a 2FA step
  (`ui/LoginActivity.kt`). A Google endpoint exists in the API client but has no UI entry point on
  Android yet. Payments and tariff checkout hand off to a Custom Tab.

The backend is the Departament bot backend and is shared with the web dashboard and the Telegram
mini app, so endpoint semantics are not ours to change unilaterally.

## The desktop client

The Windows/Linux/macOS client is a separate repository, a fork of
[2dust/v2rayN](https://github.com/2dust/v2rayN) (in this environment: `/home/user/v2rayN`). The two
clients are one product under one design, not independent apps:

- both talk to the same backend, and the desktop `Account/**` code is a deliberate port of this
  repo's `auth/**`: same endpoint contract, same session rules, same DTO shapes, with a handful of
  extra endpoints the desktop start page needs;
- `docs/design2026/` specifies both from one product design, with `32-master-plan-android.md` and
  `33-master-plan-pc.md` as the two halves of one screen specification;
- the parity contract is written down: `docs/design2026/00-rules.md` section 13 fixes what must be
  identical (destinations and their order, every Russian string for the same concept, default
  values, tokens, motion tempo) and what may differ (navigation shape, action surfaces, hover,
  haptics, shortcuts). A feature on one client and not the other is a logged parity gap, not a
  platform difference to be shrugged at.

## Design law and specifications

All UI work follows `docs/design2026/`, starting with **`00-rules.md`**, which outranks taste,
habit and upstream precedent. It defines the tokens (one spacing scale, one accent, the radius and
type ramps), the absolute bans, the per-state requirements, and the register of every user-visible
string. Then `03-direction.md` for the visual direction, `10-design-system.md`,
`11-app-structure.md`, and `32-master-plan-android.md` for this client screen by screen.

Read the rules before designing, not after.

## Licence and attribution

GPL-3.0, unchanged from upstream. The full text is in [`LICENSE`](LICENSE). Copyright in the
upstream code stays with the v2rayNG authors; the Departament changes are released under the same
licence, and any redistribution of a build must carry it.

Third-party components keep their own licences: `AndroidLibXrayLite` and the Xray core it wraps,
`hev-socks5-tunnel`, and the libraries listed in `V2rayNG/gradle/libs.versions.toml`. CI runs the
Gradle license plugin (`licenseFdroidReleaseReport`) as part of the release build.
