# How to verify Android changes in this environment

## Fresh container: run the setup once

A new container has **no** Android SDK, no .NET SDK, no submodule and no libv2ray stub. One command
installs all of it, idempotently:

```bash
bash /home/user/dp/docs/agents/setup-env.sh
```

Then the single gate for both platforms - build plus a normalised diff against the recorded
warning baselines:

```bash
bash /home/user/dp/docs/agents/verify-build.sh both      # or: android | desktop
```

Pass means `BUILD: SUCCESSFUL` **and** `NEW WARNINGS: 0` on the platform you touched. The script
also prints whether the compiler actually ran: an `UP-TO-DATE` compile emits no warnings and
proves nothing, so a green line there is not verification.

Two environment facts that cost time to rediscover:

- The SDK package for `compileSdk = 37` is **`platforms;android-37.0`**. `platforms;android-37`
  does not exist and `sdkmanager` fails with "Failed to find package".
- `github.com` is **not reachable** from this environment, so the real `libv2ray.aar` cannot be
  downloaded from `2dust/AndroidLibXrayLite` releases the way CI does. `setup-env.sh` generates the
  type-check stub instead, and that stub's exact class surface is recorded there. If a build ever
  fails on a missing `libv2ray` member, add the member to the stub in `setup-env.sh`; never reshape
  app code to fit the stub.

---

The Android app **compiles here**. Any agent changing Kotlin or resources must verify before finishing.

```bash
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd /home/user/dp/V2rayNG

# fast: Kotlin type-check only
./gradlew :app:compileFdroidDebugKotlin --no-daemon

# full: also compiles resources/layouts and links the APK
./gradlew :app:assembleFdroidDebug --no-daemon
```

Notes:

- Build flavours are `fdroid` and `playstore` — there is no plain `compileDebugKotlin` task.
- `app/libs/libv2ray-stub.jar` is a **local type-check stub** for the native `libv2ray.aar`
  that CI downloads from the core-library release. It exists only so Kotlin type-checks here.
  It is gitignored and must never be committed, and it must never be referenced from app code.
- Baseline before this work: **BUILD SUCCESSFUL with 21 warnings** — the list is in
  `.baseline-warnings.txt` next to this file. The bar is *no new warnings*, and ideally fewer.
## Desktop (PC) app

The desktop app **also builds here**. Any agent changing C# or `.axaml` must verify before finishing.

```bash
export DOTNET_ROOT=/opt/dotnet PATH=/opt/dotnet:$PATH DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
cd /home/user/v2rayN/v2rayN
dotnet build v2rayN.Desktop/v2rayN.Desktop.csproj -c Release
```

Notes:

- The `GlobalHotKeys` submodule is required and is already initialised in this checkout.
- Avalonia XAML is compiled by the build, so a malformed `.axaml` **fails the build** — there is no
  excuse for shipping broken markup.
- Baseline before this work: **Build succeeded, 0 errors, 28 warnings** — list in
  `.baseline-warnings-desktop.txt`. The bar is *no new warnings*.
