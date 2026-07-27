# Departament VPN — Android client

This repository builds the Android client of Departament VPN: a VPN/proxy client that runs an Xray
core locally and gets its servers from the user's Departament subscription. It is one half of a
two-client product; the desktop half lives in a sibling repository (see "The desktop client").

The app works as a plain proxy client with no account at all. Signing in is what adds the
Departament side of the product: subscriptions, devices, tariffs, payments, promo codes, referrals.

## Relationship to upstream

This is a fork of [2dust/v2rayNG](https://github.com/2dust/v2rayNG). Everything that speaks the
protocols — the Xray core wrapper, subscription and share-link parsing, config generation, the VPN
service and the TUN plumbing — is upstream's work and is kept. What this fork adds on top:

- **The account layer** (`auth/**`) and the screens that use it: sign-in, account, tariffs,
  devices, payment history.
- **Operator subscription templates** (`template/**`): support for an operator-managed subscription
  that ships a whole Xray-JSON config — routing and DNS included — instead of a list of nodes.
- **A rebuilt Russian UI** in the Incy visual language, governed by `docs/design2026/`.
- **Product identity**: `applicationId com.departamentvpn.app`, its own launcher name, icon and APK
  naming, its own CI workflows.

Upstream's Kotlin package name `com.v2ray.ang` is deliberately unchanged (`namespace` in
`app/build.gradle.kts`), so file paths and `import` lines still match upstream and merges stay
reviewable. The package name is not the product name. One consequence is load-bearing: the
`hev-socks5-tunnel` JNI symbols are built against the *package*, not the applicationId
(`compile-hevtun.sh` passes `-DPKGNAME=com/v2ray/ang/service`), so renaming the package breaks the
tunnel.

## Repository layout

| Path | What it is |
|---|---|
| `V2rayNG/` | Gradle root. Single module, `:app`. |
| `V2rayNG/app/src/main/java/com/v2ray/ang/` | All Kotlin — see the table below. |
| `V2rayNG/app/src/main/res/` | Layouts, drawables, themes, strings. |
| `V2rayNG/app/src/fdroid/` | Flavour-specific resource overrides. |
| `AndroidLibXrayLite/` | Submodule. The Go project that produces the native core AAR. Not compiled here; only its tag is read (see below). |
| `hev-socks5-tunnel/` | Submodule. C sources of the tun2socks library, built by `compile-hevtun.sh`. |
| `compile-hevtun.sh` | Builds `libhev-socks5-tunnel.so` for four ABIs with the NDK into `./libs/`. |
| `docs/design2026/` | The design law and the screen specifications for both clients. |
| `docs/agents/` | Build gate, environment setup, audit and verification notes. |
| `fastlane/` | Store metadata, validated by a workflow. Still upstream's content — see `docs/agents/state/release-android.md` §6.8. |

Inside the Kotlin tree:

| Package | What lives there |
|---|---|
| `auth/**` | The Departament backend: config, API client, session, token store, subscription import. |
| `template/**` | Operator-managed hidden config templates: lock state, storage, encryption. |
| `core/**`, `fmt/**` | Config generation, core lifecycle, native `libv2ray` wrapper, link parsing. |
| `handler/**` | Config, subscription, storage, settings, speedtest, backup managers. |
| `service/**` | `CoreVpnService`, proxy-only and test services, `TProxyService` (TUN), the QS tile. |
| `ui/**`, `viewmodel/**` | Screens and their view models. `ui/component/**` is the shared row/toolbar/skeleton vocabulary. |
| `tv/**` | Android TV pairing and config transfer. |
| `dto/**`, `enums/**`, `util/**`, `receiver/**`, `helper/**`, `contracts/**` | Models, helpers, broadcast receivers. |

## Stack

Kotlin 2.3.10, AGP 9.2.1, Gradle wrapper 9.4.1, built with JDK 21 and targeting Java 17 bytecode
with core library desugaring. `compileSdk` and `targetSdk` 37, `minSdk` 24. XML layouts with
ViewBinding and Material 3 — there is no Compose in this app. MMKV for storage, OkHttp + Gson for
HTTP, coroutines, WorkManager for subscription refresh. Versions are pinned in
`V2rayNG/gradle/libs.versions.toml`.

Android TV is declared as a form factor (leanback launcher category, `mipmap/ic_banner`, and the
`tv/**` transfer screens), but the shipped UI is the phone layout and is not D-pad navigable. Treat
it as declared, not delivered.

## Flavours and variants

Two product flavours in the `distribution` dimension, `fdroid` and `playstore`, and only `release`
is declared under `buildTypes` (`debug` is the implicit AGP default). Every build is therefore
flavour-qualified: the variants are `fdroidDebug`, `fdroidRelease`, `playstoreDebug`,
`playstoreRelease`.

There is no flavour-less variant. `:app:assembleFdroidDebug` is a real task,
`:app:compileDebugKotlin` is not. If a command from upstream's docs or from habit does not exist,
that is usually why.

What actually differs between the two flavours, in full:

- `fdroid` gets `applicationIdSuffix = ".fdroid"`, so both flavours can be installed side by side.
- Each declares a `DISTRIBUTION` `buildConfigField`. Nothing reads it.
- `src/fdroid/res/` overrides `app_name` and `shortcuts.xml`. There is no `src/playstore/` at all,
  and no flavour-specific source, dependency or manifest.

Other things worth knowing before the first build:

- APKs are named `departament_<versionName>[-fdroid]_<abi>.apk` and land in
  `V2rayNG/app/build/outputs/apk/<flavour>/<buildType>/`. ABI splits produce four ABIs plus a
  universal APK; narrow that with `-PABI_FILTERS=arm64-v8a`.
- `release` is wired to the **debug** signing config, so a release build is installable without any
  keystore. Only `.github/workflows/build.yml` overrides that, with
  `-Pandroid.injected.signing.*` from repository secrets. Because `debug` declares no
  `applicationIdSuffix`, a debug and a release APK of the same flavour currently share
  applicationId, versionCode **and** signing key — they install over each other silently. Do not
  hand anyone a "release" APK from `release.yml` or a local build and call it shippable.
- `app/src/fdroid/res/values/strings.xml` still carries upstream's `app_name` override, so an
  fdroid build shows the upstream launcher label while a playstore build shows `departament`.
- `app/src/dev/` and `app/src/pre_release/` match no flavour that still exists and are not built.
  They are upstream leftovers.

`docs/agents/state/release-android.md` is the full pre-release assessment — signing, minification
keep rules, versionCodes, manifest exposure, CI. Read it before calling anything releasable.

## Native libraries are not in the repository

This is the thing that trips everyone up. `*.aar` and `*.so` are gitignored. Two native pieces have
to arrive before a build can produce a working APK, and the workflows in `.github/workflows/` are
the reference for how:

1. **`libv2ray.aar`** — the Xray core wrapped for Android. CI does not build it. It reads the tag
   the `AndroidLibXrayLite` submodule is pinned to (`git describe --tags --abbrev=0` inside the
   submodule) and downloads the `libv2ray.aar` asset from the matching release of
   `2dust/AndroidLibXrayLite` into `V2rayNG/app/libs/`. Bump the core by moving the submodule.
   Nothing pins or checksums the AAR beyond that tag, and `git describe` only works because
   `actions/checkout` runs with `fetch-depth: 0` and recursive submodules — a shallow clone breaks
   the build with an error that points somewhere else.
2. **`libhev-socks5-tunnel.so`** — tun2socks, used by `service/TProxyService.kt`. CI runs
   `compile-hevtun.sh`, which needs `NDK_HOME` (NDK 28.2.13676358) and builds `armeabi-v7a`,
   `arm64-v8a`, `x86` and `x86_64` into the repo-root `./libs/`; the workflow then copies that
   directory into `V2rayNG/app/`, because `app/libs` is the `jniLibs` source directory.

`app/libs` therefore holds both the AAR and the per-ABI `.so` tree in a real build, and
`build.gradle.kts` picks both up (`fileTree("libs", …)` plus `jniLibs.srcDirs("libs")`).

## Build

```bash
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd V2rayNG

./gradlew :app:assembleFdroidDebug          # Kotlin + resources + APK link
./gradlew :app:compileFdroidDebugKotlin     # fast: Kotlin type-check only
./gradlew :app:assemblePlaystoreRelease -PABI_FILTERS=arm64-v8a
```

Gradle locates the SDK through `ANDROID_HOME`, or through a `local.properties` holding
`sdk.dir=...` (gitignored; CI writes it). The SDK package for `compileSdk = 37` is
`platforms;android-37.0` — `platforms;android-37` does not exist and `sdkmanager` fails on it. The
Gradle build itself compiles no native code, so the NDK is needed only for `compile-hevtun.sh` —
but `ndkVersion` is declared, so once `.so` files are present AGP will demand exactly that NDK
revision for symbol stripping. Android Studio can open `V2rayNG/` directly.

CI workflows, all in `.github/workflows/`:

- `build.yml` — the release pipeline. Installs the NDK, builds or restores libhevtun, downloads
  `libv2ray.aar`, runs `licenseFdroidReleaseReport`, then `assembleRelease` signed from secrets,
  and uploads per-ABI APKs. Triggered on `push: master` and manual dispatch.
- `debug.yml` / `release.yml` — single-flavour, single-ABI convenience builds
  (`assemblePlaystoreDebug` and `assemblePlaystoreRelease`), unsigned by any real key. Manual
  dispatch, plus pushes to the one dev branch named inside each file.
- `fastlane.yml` — validates the store metadata under `fastlane/`.

Both branch workflows still name a branch that predates current work, so **nothing in CI compiles
the branch this work is on.** Fixing the triggers is the first item in the release assessment.

## Verifying a change

Every change to Kotlin or resources must pass the gate before it is finished.

```bash
bash docs/agents/setup-env.sh              # once per container, idempotent
bash docs/agents/verify-build.sh android   # or: desktop | both
```

The gate passes only on `BUILD: SUCCESSFUL` **and** `NEW WARNINGS: 0`, compared against the
recorded baselines next to the script (`.baseline-warnings.txt`, 21 warnings for Android;
`.baseline-warnings-desktop.txt`, 28 for the desktop). The bar is *no new warnings* — the baseline
is not a licence to add more. The script also reports whether the compiler actually ran, because an
`UP-TO-DATE` build emits no warnings and proves nothing. It serialises builds behind a `flock`, so
it can wait a while before starting.

Two limits of the gate, worth knowing rather than discovering:

- It builds `fdroidDebug` only. It never runs `lintVital*` (which AGP attaches to `assembleRelease`
  and which aborts the build on a fatal issue), never builds the `playstore` flavour, and never
  performs a release resource link.
- In an environment that cannot reach `github.com`, the real `libv2ray.aar` cannot be downloaded.
  `setup-env.sh` generates a small type-check stub, `app/libs/libv2ray-stub.jar`, carrying only the
  class surface the app compiles against (`go.Seq`, `libv2ray.Libv2ray`, `libv2ray.CoreController`,
  `libv2ray.CoreCallbackHandler`, `libv2ray.ProcessFinder`). An APK built against it links but
  cannot run a tunnel; runnable APKs come from CI. If a build fails on a missing `libv2ray` member,
  extend the stub in `setup-env.sh` — **never reshape app code to fit the stub**, and never commit
  it. If the real AAR is later downloaded onto a machine that has the stub, delete the stub first:
  both match the same `fileTree` include and the build fails on duplicate classes.

`docs/agents/BUILD-VERIFY.md` has the rest of the environment's sharp edges, including the rule
that only one wave may be editing a given platform at a time.

## Account, subscriptions and payments

The account layer lives in `app/src/main/java/com/v2ray/ang/auth/`:

- `BackendConfig.kt` is the single configuration point. The backend base URL, the Telegram bot
  username and the subscription `User-Agent` are `buildConfigField`s declared in
  `V2rayNG/app/build.gradle.kts` and are read only through this object, which also lists every
  endpoint path. Nothing else in the app should hardcode any of them.
- **Login is optional by design.** When no base URL is configured, `isConfigured()` is false and the
  app must stay fully usable with no backend. Every caller has to honour that.
- Auth is a bearer JWT with a 7-day lifetime and no refresh endpoint. `AuthTokenStore` keeps it in a
  dedicated MMKV store (`departament_auth`, `MULTI_PROCESS_MODE`, because the `:bg` WorkManager
  process reads it) encrypted with a crypt key sealed by the Android Keystore. When the key cannot
  be unsealed the store resolves to **null, not to a plaintext store** — that reads to callers like
  a signed-out device, and nothing is written, so an unreadable file is never overwritten. Do not
  "fix" that by opening it unencrypted. Tokens and subscription URLs are never logged.
- `SubscriptionSyncManager` is the only place that touches the existing config code. It imports the
  account's subscriptions through the app's normal plumbing — `MmkvManager`,
  `AngConfigManager.updateConfigViaSub`, `SubscriptionUpdater` — rather than parsing anything
  itself, and owns the uuid→guid map.
- Sign-in is Telegram (`AuthManager` mints a login token, opens
  `https://t.me/<bot>?start=auth_<token>`, then polls `telegram-login-check` every 2 s for up to
  three minutes) or email and password with a 2FA step (`ui/LoginActivity.kt`). A Google endpoint
  exists in the API client and has no UI entry point on Android yet. Payments and tariff checkout
  hand off to a Custom Tab.

The backend is the Departament bot backend, shared with the web dashboard and the Telegram mini
app, so endpoint semantics are not ours to change unilaterally.

## Operator subscription templates

A Departament subscription can deliver a full Xray-JSON configuration — outbounds *and* the
operator's routing and DNS rules — instead of a base64 list of `vless://` links. Getting that is a
negotiation, and the knob is one build field:

- **The panel picks the response format from the request's `User-Agent`**, using its own
  client→template mapping. Which string yields the template is therefore a property of the
  operator's panel, not of this app. `SUB_USER_AGENT` in `build.gradle.kts` is that operator's knob
  and is sent verbatim; `BackendConfig.subscriptionUserAgent` refuses only a value that cannot
  travel in an HTTP header, because the same string is also the API `User-Agent` and OkHttp throws
  while *building* a request on a non-ASCII value.
- Blank — what ships today — falls back to `HttpUtil.DEFAULT_SUBSCRIPTION_USER_AGENT`
  (`v2rayNG/<versionName>`), the client string every panel recognises, answered with the base64 link
  list, which the app parses. Do not put branding here: a name no panel knows gets the link list
  anyway *and* identifies the deployment on every request. `BackendConfig.isAppStampedUserAgent()`
  exists to recognise and drop values earlier builds stamped onto subscriptions.
- Four precedence tiers, all resolved at the single fetch point in
  `AngConfigManager.updateConfigViaSub`: a per-subscription override wins, then the global override
  from the provider screen, then `SUB_USER_AGENT`, then the app default. They are resolved there
  and nowhere else, so a scheduled refresh and a manual one can never ask for different formats.
  The request also sends `Accept: application/json, text/plain;q=0.9, */*;q=0.8` — prefer the
  template, still accept the link list.

`template/TemplateManager.kt` is the single entry point for the rest. A subscription the operator
marks hidden — via a `profile-hidden` response header or an in-body `#profile-hidden:` directive —
has its template stored obfuscated or Keystore-encrypted at rest (`dpt-enc:v1:` / `dpt-obf:v1:`
prefixes; unlocked configs are stored verbatim, byte-for-byte as before), applied as authored at
connect time by the config builder, and hidden from the user: every profile imported from it is
stamped `ProfileItem.locked`, which the UI uses to block share, QR, show-config, edit and export.
`TemplateCrypto` is honest about what that is — obfuscation plus UX gating, not DRM.

The design behind this is `docs/hidden-templates-design.md` and `docs/remnawave-templates-spec.md`.

## Design law and specifications

All UI work follows `docs/design2026/`, starting with **`00-rules.md`**, which outranks taste,
habit and upstream precedent — its own precedence list puts the owner's explicit requests first,
then that file, then the Absolute Bans and AI-slop test from `.claude/skills/impeccable/`, then
Material 3, then existing conventions, and personal taste last.

The visual language is **Incy**: a pure dark surface, one bright blue accent (red is destructive
only), Russian sentence-case copy, one spacing scale, one gutter, one radius ramp, one type ramp
(`TextAppearance.App.*`), and every state designed — pressed, selected, disabled, empty, loading,
error. Body text is Golos Text, which draws Cyrillic; Space Grotesk is the brand face and is scoped
to display, chip, numeric and wordmark roles only. Hardcoding a size, a face or a colour where a
token exists is a defect.

After `00-rules.md`: `03-direction.md` for the visual direction, `10-design-system.md` for the
tokens and `22-components.md` for the component vocabulary, `11-app-structure.md` for navigation,
and `32-master-plan-android.md` for this client screen by screen. `docs/design2026/42-copy-register.md`
is the register of every user-visible string.

Read the rules before designing, not after. `CLAUDE.md` at the repo root restates the
non-negotiable subset.

## State of the work

`docs/agents/state/STATE-OF-WORK.md` is the honest inventory: what is built, wired and reachable
versus what exists only as a specification or as finished code behind a door with no handle. It is
the right first read before picking up work, and it is deliberately more sceptical than the
per-feature documents around it. `docs/CONTINUE-HERE.md` is the shorter handoff.

## The desktop client

The Windows/Linux/macOS client is a separate repository, a fork of
[2dust/v2rayN](https://github.com/2dust/v2rayN). The two clients are one product under one design,
not independent apps:

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

Its release assessment is `docs/agents/state/release-desktop.md`.

## Licence and attribution

GPL-3.0, unchanged from upstream. The full text is in [`LICENSE`](LICENSE). Copyright in the
upstream code stays with the v2rayNG authors; the Departament changes are released under the same
licence, and any redistribution of a build must carry it.

Third-party components keep their own licences: `AndroidLibXrayLite` and the Xray core it wraps,
`hev-socks5-tunnel`, and the libraries listed in `V2rayNG/gradle/libs.versions.toml`. CI runs the
Gradle license plugin (`licenseFdroidReleaseReport`) as part of the release build, though its output
is not currently copied into the shipped `assets/open_source_licenses.html` — see
`docs/agents/state/release-android.md` §4.7.
