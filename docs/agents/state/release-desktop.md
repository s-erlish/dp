# Desktop release readiness — departament VPN (PC / Avalonia)

**Written:** 2026-07-26 · **Branch:** `claude/app-audit-agents-hyyftk` · **Repo:** `/home/user/v2rayN`
**Method:** read-only. No file in either tree was edited, no build was run, no git command was issued.
Every claim below cites the line I read it on. Two other waves were editing `v2rayN.Desktop/Views/**`
and the Android tree while I worked; nothing I looked at is in their file set, but line numbers in
`Views/` may drift.

---

## Verdict in one paragraph

The desktop app **builds and publishes**, and the one departament-branded workflow
(`departament-branch-build.yml`) does produce a runnable, correctly-named `departament.exe` for
Windows x64 with both cores bundled. Everything *around* that one workflow is still upstream v2rayN.
The seven packaging scripts at the repo root, all six inherited release workflows, the in-app
updater and `AmazTool` were written against `AssemblyName=v2rayN`; the fork renamed the assembly to
`departament` (`v2rayN.Desktop.csproj:8`) and nothing downstream was updated. Concretely: **the .deb
and .rpm install a package whose launcher cannot find the binary, the macOS .app declares an
executable that does not exist, `package-debian.sh` will `git checkout -f` an *upstream* tag over
this branch and silently ship upstream v2rayN, and the in-app "check for updates" points at
`2dust/v2rayN` releases.** Separately, the Windows app now requests `requireAdministrator`
(`app.manifest:10`) while the new autostart helper writes an `HKCU\…\Run` value — a combination
Windows does not honour, so the autostart toggle is a no-op on the shipping build. None of these is
visible from a green `dotnet build`.

Windows x64 is the only target that could ship today, and only as an unsigned zip.

---

## 1. Publish shape — what is actually produced

### 1.1 The properties that decide it

`v2rayN/Directory.Build.props` (applies to every project in the solution):

| Line | Property | Value | Effect |
|---|---|---|---|
| 4 | `Version` | `7.23.4` | Still upstream's version. Shown by `Utils.GetVersionInfo()` in the About row |
| 8 | `TargetFramework` | `net10.0` | The Avalonia app is TFM-neutral; the WPF project overrides to `net10.0-windows10.0.19041.0` |
| 16 | `InvariantGlobalization` | `false` | **Requires ICU at runtime on Linux** — see §5.4 |
| 28 | `UseSystemResourceKeys` | `true` (Release only) | See §2.5 — user-visible |
| 29 | `PublishSingleFile` | `true` (Release only) | Global, not per-project |
| 30 | `PublishReadyToRun` | `false` (Release only) | Explicitly off |

There is **no `PublishAot`** anywhere in the repo, and **no `PublishTrimmed`** in any `.csproj` or
`.props`. Trimming exists only as a CI command-line flag on `AmazTool` (§2.1).

### 1.2 What each workflow emits

| Workflow | Project | RIDs | Self-contained | Single-file | Trimmed | R2R | Signed |
|---|---|---|---|---|---|---|---|
| `departament-branch-build.yml` | `v2rayN.Desktop` | **win-x64 only** | yes (`:32`) | yes (inherited) | no | no | **no** |
| `build-windows-desktop.yml` → `build.yml` | `v2rayN.Desktop` | win-x64, win-arm64 | yes (`build.yml:79`) | yes (inherited) | no | no | GPG detached sig on the zip only |
| `build-windows.yml` → `build.yml` | **`v2rayN.csproj` (WPF)** | win-x64, win-arm64 | yes | yes | no | no | GPG sig only |
| `build-linux.yml` → `build.yml` | `v2rayN.Desktop` | linux-x64, linux-arm64 | yes | yes | no | no | GPG sig only |
| `build-osx.yml` → `build.yml` | `v2rayN.Desktop` | osx-x64, osx-arm64 | yes | yes | no | no | GPG sig only; **no Apple notarisation, no codesign** |
| `build-windows-x86.yml` | both flavours | win-x86 | yes | yes | AmazTool only | no | — |
| `build-linux.yml` deb/rpm jobs | `v2rayN.Desktop` | linux-x64/arm64/riscv64/loong64 | yes | **`false`** (scripts override) | no | no | — |

Three consequences worth stating plainly:

- **Only `departament-branch-build.yml` produces a departament-branded artifact**, and only for
  win-x64. There is no branded Linux, macOS or ARM64 build anywhere.
- **`PublishSingleFile` is global and Release-only.** A manual `dotnet publish -c Release` with no
  `-r` fails outright (`NETSDK1098`: single-file requires a RuntimeIdentifier). Anyone publishing by
  hand must pass `-r`. The `.deb`/`.rpm` scripts sidestep this by forcing
  `-p:PublishSingleFile=false` (`package-debian.sh:506`, `package-rhel.sh:478`, and the four
  arch-variant scripts).
- **`PublishReadyToRun=false` is a deliberate startup-time cost.** For a single-file self-contained
  Avalonia app this is the difference between roughly a second and several seconds to first frame on
  a cold Windows start. If cold start matters at release, `-p:PublishReadyToRun=true` on win-x64 is
  the single largest available win and carries no correctness risk (R2R is a pre-JIT cache, not a
  trimmer). It roughly doubles the payload.
- **No Authenticode signing exists.** `upload-sign.yml` imports a GPG key and produces `*.zip.sig`
  detached signatures (`:47-58`) — that authenticates the *archive*, not the executable.
  `departament-branch-build.yml` does not sign at all. A `requireAdministrator` manifest on an
  unsigned exe means every launch shows the yellow "Unknown publisher" UAC dialog, and SmartScreen
  will interstitial the first download. See §5.1.

### 1.3 Satellite assemblies

`SatelliteResourceLanguages` is unset, so every publish ships eight `ResUI` satellite folders (`fa`,
`fr`, `hu`, `id`, `ru`, `zh-Hans`, `zh-Hant`, plus neutral) — `ServiceLib.csproj:62-91`. For a
Russian/English product `<SatelliteResourceLanguages>ru;en</SatelliteResourceLanguages>` in
`Directory.Build.props` removes six of them. Cosmetic, but it is also six folders of Chinese and
Persian strings sitting in a "departament" install directory.

---

## 2. Trimming and AOT

**Trimming is off for the shipping app.** It is on for exactly one thing: `AmazTool`, via
`build.yml:84` (`-p:PublishTrimmed=true`) and `build-windows-x86.yml:82`. AOT is nowhere.

### 2.1 The one trimmed component, and the hazard it already carries

`AmazTool` is a console updater. Trimming it is safe on its own — no reflection, no JSON, no ORM
(`AmazTool/Program.cs`, `UpgradeApp.cs`, `Utils.cs` are plain `File`/`Process`/`ZipFile` code) —
except for one structural detail: **`build.yml` publishes the trimmed, self-contained AmazTool into
the same `-o $Output` directory as the untrimmed app** (`:79` and `:84` share `$Output`). Today this
is harmless only because `PublishSingleFile=true` bundles each app's managed assemblies inside its
own host, so the two publishes overlap only on identical native libraries. **If anyone ever sets
`PublishSingleFile=false` for a CI publish** — as the Linux packaging scripts already do — the
trimmed AmazTool's `System.*.dll` copies will overwrite the app's, and the app will fail at runtime
with missing-member exceptions that point nowhere near the cause. Publish AmazTool to a subdirectory,
or drop `PublishTrimmed` from it; the binary is ~50 KB of logic.

### 2.2 If trimming were enabled on `v2rayN.Desktop` — what breaks

Asked for completeness, because the temptation to add `PublishTrimmed=true` to shrink a
~140 MB self-contained publish is real. Every one of these is a **silent** failure: the trimmer emits
`IL2026`/`IL2075` warnings that are not errors, publish succeeds, and the app misbehaves at runtime.

| # | Surface | Where | What breaks | Preserve with |
|---|---|---|---|---|
| T1 | **`System.Text.Json` reflection serializer** — there is no `JsonSerializerContext` anywhere in the solution (verified: zero matches for `JsonSerializerContext`, `JsonSourceGenerationOptions`) | `ServiceLib/Common/JsonUtils.cs` — `Deserialize<T>`, `Serialize`, `DeepCopy<T>` | Config models, all Xray/sing-box config DTOs, every backend API DTO under `v2rayN.Desktop/Account/**` lose properties that are only ever touched by the serializer. Config silently loads with default values; the tunnel builds a config missing outbound fields | A `[JsonSerializable]` partial `JsonSerializerContext` per root type and `TypeInfoResolver` wiring — the correct fix. Stopgap: `ILLink.Descriptors.xml` rooting `ServiceLib.Models.*` and the account DTO namespace |
| T2 | **`sqlite-net` ORM** (`sqlite-net-e`, `ServiceLib/Helper/SqliteHelper.cs:21-23`) | `AppManager.cs:114-122` — `CreateTable<SubItem>`, `<ProfileItem>`, `<ServerStatItem>`, `<RoutingItem>`, `<ProfileExItem>`, `<DNSItem>`, `<FullConfigTemplateItem>`, `<ProfileGroupItem>` | The ORM reflects over public properties and `[PrimaryKey]`/`[Indexed]` attributes. Trimmed property accessors ⇒ tables created with missing columns, or `CreateTable` throwing. **This is the user's server list and subscription store** | `[DynamicallyAccessedMembers(DynamicallyAccessedMemberTypes.PublicProperties \| PublicParameterlessConstructor)]` on the `T` of `CreateTable<T>`/`Table<T>` wrappers, plus a descriptor rooting all eight entity types |
| T3 | **YamlDotNet** (`ServiceLib.csproj:18`) — Clash mixin/tun YAML | `Sample/clash_mixin_yaml`, `clash_tun_yaml` deserialisation | Same reflection-over-properties failure as T1, for Clash config | Root the Clash model types in a descriptor |
| T4 | **`Microsoft.Win32.TaskScheduler`** (`ServiceLib.csproj:22`) | `AutoStartupHandler.AutoStartTaskService` (`:83-115`) | The library is COM interop over `ITaskService` — `Type.GetTypeFromCLSID` + `Activator.CreateInstance`. Trimming/AOT-hostile by construction; `BuiltInComInteropSupport=true` (`v2rayN.Desktop.csproj:6`) keeps the interop shim but not the type graph | Root the whole assembly: `<assembly fullname="Microsoft.Win32.TaskScheduler" preserve="all"/>` |
| T5 | **ReactiveUI `WhenAnyValue` / `ObservableAsPropertyHelper`** | 185 `[Reactive]` properties in `v2rayN.Desktop`, 243 in `ServiceLib` | Expression-tree property fetchers resolve `PropertyInfo` at runtime. Most survive because the expression carries an `ldtoken`, but `WhenAnyValue` chains that cross into a type reached only via `IDataTemplate` do not | Root the view-model namespaces; keep `SimpleViewLocator` as-is (see below) |
| T6 | **Avalonia XAML** | All `.axaml` in `v2rayN.Desktop/Views` | `AvaloniaUseCompiledBindingsByDefault=true` (`v2rayN.Desktop.csproj:7`) makes bindings compile-time, which is what makes this *mostly* trim-safe. The residue is `{DynamicResource}` brush lookups (correct and required — see STATE-OF-WORK §7), style selectors keyed on type name, and `Assets/**` resources | `AvaloniaResource Include="Assets\**"` (`:45`) already roots the resources. Verify no `{ReflectionBinding}` / no `x:DataType`-less `{Binding}` before enabling |
| T7 | **`ReactiveUI.Fody`** (v19.5.41 against ReactiveUI 23.2.28) | `FodyWeavers.xml` in `ServiceLib`, `v2rayN.Desktop`, `v2rayN` | Fody is a build-time IL weaver, not a runtime reflection surface — it does **not** break under trimming. Worth flagging anyway: `ReactiveUI.Fody` is deprecated upstream in favour of `ReactiveUI.SourceGenerators`, and an IL weaver in the pipeline is the usual reason a trimmer/AOT publish produces unexplainable results | Not a trimming fix; a migration to note for a later cycle |
| T8 | **Resx / `ResourceManager`** | `ServiceLib/Resx/ResUI.*` | Satellite assemblies survive trimming, but `UseSystemResourceKeys=true` already degrades *framework* messages regardless (§2.5) | — |

**The one thing that is already trim-clean and should stay that way:** `SimpleViewLocator`
(`v2rayN.Desktop/Common/SimpleViewLocator.cs`) resolves views through an explicit
`Dictionary<Type, Func<Control?>>` populated by 21 `RegisterViewFactory<TVm, TView>()` calls
(`:15-36`), not through ReactiveUI's default name-mangling `ViewLocator`. That is the single design
decision that would make a trimmed build feasible at all. Anyone "simplifying" it back to a
convention-based locator re-introduces `Type.GetType(string)` and breaks trimming permanently.

### 2.3 Recommendation

Do **not** enable trimming for this release. The payoff is size on a product that already ships two
Go core binaries beside itself; the cost is eight reflection surfaces, two of which (T1, T2) fail
silently on the user's saved data. If size is the goal, `SatelliteResourceLanguages` (§1.3) and
dropping the unused `Avalonia.Controls.DataGrid`/`Semi.Avalonia.DataGrid` pair — both marked
`<TreatAsUsed>true</TreatAsUsed>` in `v2rayN.Desktop.csproj:22-33`, which is a comment that they are
*not* statically referenced — are risk-free and get most of it.

### 2.4 `CETCompat=false`

`v2rayN.Desktop.csproj:15` (and `v2rayN.csproj:13`) opt the process out of Control-flow Enforcement
Technology, the hardware shadow-stack mitigation, on Windows. Inherited from upstream. For a VPN
client that runs elevated this is a security-posture item worth a deliberate decision rather than an
inherited default: remove the line, test, and only restore it if a core or interop path actually
faults.

### 2.5 `UseSystemResourceKeys=true` is user-visible, not just a size knob

`Directory.Build.props:28`, Release only. It writes `System.Resources.UseSystemResourceKeys=true`
into `runtimeconfig.json`, and CoreCLR honours it at runtime **whether or not the app is trimmed**:
every framework exception message collapses to its resource key. The app surfaces raw `ex.Message`
to users in several places — for example `UrlSchemesPage.axaml.cs:110`
(`L.T("UrlSchemes_RegisterFailed") + ex.Message`) and `:144`. In a Release build a failed scheme
registration therefore reads «Не удалось зарегистрировать: UnauthorizedAccess_IODenied_Path» instead
of a sentence. The same applies to everything `Logging.SaveLog` records, which is the only diagnostic
channel a released build has. **Remove this property.** It saves a few hundred KB and costs the
ability to diagnose a user's crash report.

---

## 3. The `GlobalHotKeys` submodule

**A clean checkout does produce a buildable tree — but only with `--recursive`, and the failure mode
of forgetting is bad.**

- `.gitmodules` declares one submodule: `v2rayN/GlobalHotKeys` ← `https://github.com/2dust/GlobalHotKeys`.
- `v2rayN.Desktop.csproj:46` has a hard `ProjectReference` to
  `..\GlobalHotKeys\src\GlobalHotKeys\GlobalHotKeys.csproj`. It is **not** conditioned on
  `Exists(...)` and **not** conditioned on `$(OS)`, so a non-recursive clone fails at restore with
  `MSB3202: The project file … was not found` — before any compiler runs, with no hint that a
  submodule is involved.
- In this container the submodule is present and populated (`src/GlobalHotKeys/HotKeyManager.cs`,
  `NativeFunctions.cs`, `NativeTypes.cs`, `HotKey.cs`, `IRegistration.cs`) with `.git` as a gitlink
  file pointing at `../../.git/modules/v2rayN/GlobalHotKeys`. It is intact.
- **CI does initialise it.** `build.yml:50-53`, `build-windows-x86.yml:31-34`, `test.yml:16-19` and
  `departament-branch-build.yml:20-23` all pass `submodules: recursive`. The six Linux packaging
  scripts run `git submodule sync --recursive` + `git submodule update --init --recursive`
  themselves (`package-debian.sh:143-144, 196-198`). **`package-osx.sh` does not** — but it operates
  on an already-built artifact directory, so it does not need to.
- **No MSBuild-property collision.** The submodule ships its own `src/Directory.Packages.props`, so
  NuGet central package management stops there and does not inherit the outer pin list. The outer
  `Directory.Build.props` *does* reach the submodule (it has no `Directory.Build.props` of its own),
  but `GlobalHotKeys.csproj` re-declares `TargetFramework`, `ImplicitUsings` and `Nullable` after the
  import, so it wins. Nothing to fix.
- **Platform guard is correct.** `GlobalHotKeys.HotKeyManager` is pure Win32
  (`RegisterClassEx`/`CreateWindowEx`/`WM_HOTKEY`), and it is only ever constructed from
  `v2rayN.Desktop/Manager/HotkeyManager.cs:46`, itself reached only from
  `MainWindow.axaml.cs:366-371` behind `if (Utils.IsWindows() && !Design.IsDesignMode)`. Linux and
  macOS builds link the assembly but never touch it. Fine as-is.

**One-line hardening worth taking:** make the reference self-diagnosing rather than cryptic —

```xml
<ProjectReference Include="..\GlobalHotKeys\src\GlobalHotKeys\GlobalHotKeys.csproj"
                  Condition="Exists('..\GlobalHotKeys\src\GlobalHotKeys\GlobalHotKeys.csproj')" />
<Target Name="WarnMissingSubmodule" BeforeTargets="Restore"
        Condition="!Exists('..\GlobalHotKeys\src\GlobalHotKeys\GlobalHotKeys.csproj')">
  <Error Text="GlobalHotKeys submodule is not checked out. Run: git submodule update --init --recursive" />
</Target>
```

(The `Condition` alone would silently break the hotkey feature; pair it with the `Error` target, or
skip both and just keep the hard reference. Do not add the condition without the error.)

---

## 4. The packaging scripts: inherited from upstream, left behind

All seven scripts at the repo root are upstream v2rayN, unmodified. **Zero of them contain the string
`departament`** — the only file outside `.github/workflows/departament-branch-build.yml` that does is
none. They assume a binary named `v2rayN`, and the fork renamed it.

### 4.1 `package-debian.sh` / `package-rhel.sh` (+ the four riscv/loong variants) — **produce a broken package**

`v2rayN.Desktop.csproj:8` sets `<AssemblyName>departament</AssemblyName>`, so
`bin/Release/net10.0/<rid>/publish/` contains `departament`, `departament.dll`,
`departament.runtimeconfig.json`. The scripts look for `v2rayN`:

| What the script does | Line | Result on this fork |
|---|---|---|
| `cp -a "$pubdir/." "$stage/opt/v2rayN/"` | `:601` | Copies `departament` into `/opt/v2rayN/` — the directory name is now wrong too |
| `find … -type f -exec chmod 0644` then `[[ -f "$stage/opt/v2rayN/v2rayN" ]] && chmod 0755` | `:676-677` | The test fails, so **nothing is ever made executable**. Every file in `/opt/v2rayN` ships mode 0644 |
| Launcher `/usr/bin/v2rayn`: `if [[ -x "$DIR/v2rayN" ]]; then exec …` | `:518-519` | False (wrong name *and* not executable) |
| Launcher fallback: `for dll in v2rayN.Desktop.dll v2rayN.dll` | `:522` | Both absent; the real file is `departament.dll` |
| Launcher final line | `:528` | **`echo "v2rayN launcher: no executable found in $DIR"; exit 1`** |

So `apt install ./v2rayn_*.deb` succeeds, the menu entry appears, and clicking it does nothing. The
`.rpm` is identical (`package-rhel.sh:522-537, 571`). Then the cosmetic layer on top: `Name=v2rayN`
and `Comment=v2rayN for Debian GNU Linux` in the `.desktop` file (`package-debian.sh:540-541`),
`Package: v2rayn`, `Maintainer: 2dust`, `Homepage: https://github.com/2dust/v2rayN` (`:663-670`),
icon copied from `v2rayN.Desktop/v2rayN.png` (`:604`).

**Fix:** introduce one variable at the top of each script — `APP_BIN="departament"`,
`APP_ID="departament"`, `APP_NAME="departament"`, `INSTALL_DIR="/opt/departament"` — and thread it
through `:601, :604, :515-528, :540-541, :600, :663-677`. Note `ServiceLib/Common/Utils.cs:1284-1296`
(`IsPackagedInstall`) hard-codes `/opt/v2rayN`, `/usr/lib/v2rayN`, `/usr/share/v2rayN`; changing the
install prefix requires changing that list in the same commit, or the packaged build will offer
self-update to a user who installed from a package.

### 4.2 `package-debian.sh` will check out upstream over your branch — **the most dangerous item here**

`resolve_version()` (`:157-179`) → `git_try_checkout()` (`:~200`) → `apply_channel_or_keep()`:

```bash
git fetch --tags --force --prune --depth=1 || true
git rev-parse "refs/tags/${want}" >/dev/null 2>&1 && ref="$want"
...
git checkout -f "$ref"        # ← discards the working tree
```

and when the requested tag is not found, `choose_channel()` falls through to `latest`, which resolves
a tag from **`https://api.github.com/repos/2dust/v2rayN/releases/latest`** (`:184`) and checks *that*
out (`apply_channel_or_keep`: `git_try_checkout "$tag" || die`). `choose_channel` only prompts when
stdin is a TTY (`:[[ -t 0 ]]`), so **in CI it never asks — it silently picks upstream's latest
release**.

Two failure modes, both real:

1. **Locally:** `bash package-debian.sh` on a developer machine `git checkout -f`s an upstream tag,
   destroying uncommitted departament work with no confirmation. Combined with the current state of
   this repo — STATE-OF-WORK §4.1 records ~22 dirty desktop files with no committed checkpoint —
   running this script today would delete in-flight work.
2. **In CI:** `build-linux.yml:51-52` calls `./package-debian.sh "${RELEASE_TAG}" --arch all`. If
   `RELEASE_TAG` is not a tag that exists in the fork, the deb job builds and publishes
   **upstream v2rayN** under a departament release. That is a supply-chain-grade defect, not a
   packaging annoyance.

**Fix:** delete `resolve_version`'s checkout behaviour entirely and replace it with
`VERSION="$(git describe --tags --always --dirty)"` on the current tree — i.e. make `--buildfrom 3`
("keep") the only mode. A packaging script must never move HEAD.

### 4.3 `package-osx.sh` — the `.app` cannot launch

Only 71 lines, all upstream:

| Line | Assumption | Reality |
|---|---|---|
| `:7-8` | downloads `v2rayN-${Arch}.zip` from `2dust/v2rayN-core-bin` | upstream cores, upstream naming |
| `:13-15` | builds `v2rayN.app`, copies `v2rayN.icns` → `AppIcon.icns` | the icns file does exist (`v2rayN.Desktop/v2rayN.icns`, `CopyToOutputDirectory=Always`) |
| `:17` | `chmod +x "…/MacOS/v2rayN"` | **file does not exist**; the script has no `set -e`, so it prints an error and continues |
| `:37-47` | `CFBundleExecutable=v2rayN`, `CFBundleName=v2rayN`, `CFBundleIdentifier=2dust.v2rayN`, `CFBundleDisplayName=v2rayN` | **`CFBundleExecutable` names a file that is not in the bundle → macOS refuses to launch the app** |
| `:63-70` | `create-dmg --volname "v2rayN Installer"`, icon/app names `v2rayN.app` | branding |

Also missing for any real macOS release: no `codesign`, no `--options runtime` hardened runtime, no
`notarytool` submission, no `xcrun stapler`. An unsigned, un-notarised `.dmg` on current macOS is
gatekeeper-blocked with no user-accessible override in the default flow. The bundle identifier must
also change off `2dust.v2rayN` before it is ever signed.

### 4.4 `v2rayN.slnx` references a script that does not exist

`v2rayN.slnx` lists `../package-release-zip.sh` in the "GitHub Action" solution folder. There is no
such file at the repo root (the seven that exist are `package-debian{,-loong,-riscv}.sh`,
`package-rhel{,-loong,-riscv}.sh`, `package-osx.sh`). Harmless — IDE noise only.

Also note both `v2rayN.sln` and `v2rayN.slnx` exist side by side. `dotnet build` run from
`/home/user/v2rayN/v2rayN` with no argument fails with `MSB1011` (more than one solution file). Every
workflow passes an explicit `.csproj`, so CI is unaffected; a human following
`docs/agents/BUILD-VERIFY.md` (which does pass the csproj explicitly) is also fine. Worth deleting
one of the two.

---

## 5. Windows specifics that only matter at release

### 5.1 The manifest: `requireAdministrator` is the decision everything else hangs off

`v2rayN.Desktop/app.manifest`:

| Line | Setting | Assessment |
|---|---|---|
| `:10` | `requestedExecutionLevel level="requireAdministrator"` | **Correct for the feature, wrong for the install model — see below** |
| `:18-19` | `dpiAware true/pm` + `dpiAwareness permonitorv2,permonitor` | **Correct.** Per-monitor v2 with a v1 fallback is exactly right for Avalonia; multi-monitor mixed-DPI will scale properly and the app will not be bitmap-stretched |
| `:20` | `longPathAware true` | Correct and worth having — config paths under deep user directories |
| `:27` | `supportedOS {8e0f7a12-…}` (Windows 10/11) | Correct. Absent, `GetVersionEx` and some shell APIs lie about the OS version |
| `:3` | `assemblyIdentity name="departament.app"` | Fine |

The elevation request is defensible on its own terms — TUN mode creates a wintun adapter and that
needs admin. But it is a **whole-app** elevation for a feature the user may never turn on, and it
breaks three things that a per-user, non-elevated install depends on:

**(a) Autostart is dead on the shipping build.** `AutostartHelper` (`v2rayN.Desktop/Common/AutostartHelper.cs`)
writes `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` → `departament` = `"<exe>"` (`:46-48`).
Windows **does not launch elevation-requiring executables from the Run key at logon** — UAC prompts
are suppressed for startup items, so the entry is skipped silently. The helper's own
`IsEnabled()` (`:85-104`) reads the Run value back, finds it present, and reports `true`. So the
settings toggle shows «включено», the app never starts at logon, and — by the design in the file's
own comment (`:80-83`, "показывать ФАКТ, а не намерение") — the UI is *confidently* wrong. This is
the same class of bug the helper was written to fix, one layer down.

The codebase already contains the correct implementation:
`ServiceLib/Handler/AutoStartupHandler.AutoStartTaskService` (`:83-115`) registers a Task Scheduler
job with `LogonTrigger` + `TaskRunLevel.Highest`, which is the only mechanism that starts an elevated
app at logon without a prompt. `SetTaskWindows` (`:57-74`) already branches on
`Utils.IsAdministrator()` and picks the scheduled task when elevated.

**Fix:** in `SettingsViewModel.OnAutoStartChanged` (`v2rayN.Desktop/ViewModels/SettingsViewModel.cs:279-287`),
stop calling `AutostartHelper.Apply(v)` on Windows and call `AutoStartupHandler.UpdateTask(_config)`
on all platforms; and in `LoadFromConfig` (`:177-179`), make `Reconcile` query the scheduled task,
not the Run value — otherwise the toggle keeps lying, just about a different key.

**(b) There are now two competing autostart mechanisms with different names.** `AutostartHelper`
writes the value `departament`; `AutoStartupHandler` writes `v2rayNAutoRun_<md5(startupPath)>`
(`:122-125`) *and*, when elevated, a scheduled task of the same name. Both are reachable: the new
settings screen uses the first (`SettingsViewModel.cs:282`), and the still-registered legacy
`OptionSettingWindow` uses the second (`OptionSettingViewModel.cs:429`, registered in
`SimpleViewLocator.cs:27`). A user who touches both ends up with two startup entries, one of which
does not work, and neither screen can see the other's state.

**(c) A standard (non-administrator) user is a different user after the UAC prompt.** On an account
without admin rights, UAC shows a *credential* prompt; the process then runs as the entered admin
account. From that point `Registry.CurrentUser` is the **admin's** hive and
`Environment.SpecialFolder.LocalApplicationData` is the **admin's** profile. Consequences, all
verified against the code paths: the `depv://`/`departamentvpn://` scheme registrations
(`UrlSchemesPage.axaml.cs:117-127`, `App.axaml.cs:174-186`) land under the admin's `Software\Classes`
and therefore **never fire for the logged-in user's browser** — which silently breaks the
browser→app SSO return the whole `AppHandoffChannel` machinery exists to serve; the autostart entry
lands in the admin's hive; and the config falls back to the admin's `%LOCALAPPDATA%\v2rayN`
(`Utils.cs:1091-1098`) if the install directory is not writable, so the user's servers and session
disappear when the app is later run any other way.

**Recommendation.** Ship the app unelevated (`asInvoker`) and elevate only the TUN path — which the
codebase already supports: `AppManager.RebootAsAdmin()` (`:186-189`),
`StatusBarViewModel.cs:533`, and the `TunUnavailable`/`EnableTunEffective` capability model
(`ConfigItems.cs:199-203`) exist precisely to handle "running unelevated, TUN not available, offer to
restart elevated". Keeping `requireAdministrator` is a valid alternative, but then autostart **must**
move to the scheduled task, and the scheme registration must be written before elevation or by an
installer running as the real user. Doing neither — which is today's state — means autostart and
browser sign-in are both broken on a fresh install by a normal user.

### 5.2 The registry writes, individually

| Write | Location | Needs admin? | Verdict |
|---|---|---|---|
| `HKCU\…\Run` → `departament` | `AutostartHelper.cs:46-48` | no | Written correctly (quoted path for spaces), but **not honoured** because the target requires elevation (§5.1a) |
| `HKCU\…\Explorer\StartupApproved\Run` clear-disable-byte | `AutostartHelper.cs:130-151` | no | Genuinely good work — this is the "I enabled it and nothing happened" cause most implementations miss. Keep the logic; move it to whichever mechanism ends up shipping |
| `HKCU\Software\Classes\depv` + `departamentvpn` | `UrlSchemesPage.axaml.cs:115-128` | no | Correct per-user protocol registration. Fires a UAC prompt on every browser-initiated launch while the manifest requires admin |
| `HKCU\Software\Classes\departamentvpn` (auto, at startup) | `App.axaml.cs:174-186` | no | Duplicates the manual registration on every launch, which is fine and idempotent — but it means the app writes to the registry unprompted at first run. Note that `UrlSchemesPage.Unregister()` (`:138-139`) deletes it and the next launch silently re-creates it, so the "unregister" button does not stick |
| `HKLM\SOFTWARE\Microsoft\Cryptography` → `MachineGuid` (**read only**) | `Account/AuthTokenStore.cs:271-278` | no (read) | Correct — `OpenBaseKey(LocalMachine, Registry64)` + read is allowed for standard users. Falls back to machine/user name if unavailable (`:262-265`). Fine unelevated |
| `HKCU\…\Themes\Personalize` (read) | `v2rayN/Common/WindowsUtils.cs:77` | no | WPF project only |

Nothing writes to `HKLM` or `HKCR`. That part of the design is right.

### 5.3 Single-file interactions on Windows — checked, all clean

- `Environment.ProcessPath` is used everywhere a path to the exe is needed (`Utils.GetExePath()`
  `:1086-1089`, `AutostartHelper.cs:41`, `UrlSchemesPage.axaml.cs:97`, `App.axaml.cs:170`). Correct
  under `PublishSingleFile` — unlike `Assembly.Location`, which returns `""` in a single-file bundle.
  No `Assembly.Location` use found.
- `AppDomain.CurrentDomain.BaseDirectory` (`Utils.GetBaseDirectory()` `:1078-1081`) returns the exe's
  directory for a single-file app, so `bin/Xray/xray.exe` and `bin/sing_box/sing-box.exe` — exactly
  where `departament-branch-build.yml:70-92` puts them, matching `Global.cs:376-377` — resolve.
- The updater's "am I the Avalonia build?" probe (`UpdateService.cs:331-335`) tests for
  `libHarfBuzzSharp.dll` next to the exe. Native libraries are *not* embedded by default
  (`IncludeNativeLibrariesForSelfExtract` is unset), so the file is there and the probe works.
- Single-instance gating (`Program.cs:36-42`) keys an `EventWaitHandle` off `md5(exe path)`, and
  `AppHandoffChannel.PipeName()` does the same. Both the running instance and the scheme-launched
  second instance run at the same (High) integrity level because both come from the same elevated
  exe, so the named pipe handoff works. **If elevation is dropped to `asInvoker` per §5.1, re-verify
  this** — a medium-IL process cannot write to a pipe created by a high-IL one, and mixing the two
  (one instance started elevated for TUN, another launched by the browser) would break SSO handoff
  silently.

### 5.4 Linux/macOS release specifics found while reading the scripts

- **`InvariantGlobalization=false` + no ICU dependency.** `Directory.Build.props:16` requires ICU at
  runtime; a self-contained .NET app that cannot find `libicuuc`/`libicui18n` aborts at startup with
  `Couldn't find a valid ICU package installed on the system`. The `.deb`'s `extra_depends`
  (`package-debian.sh:614`) lists `libc6, fontconfig, desktop-file-utils, xdg-utils, coreutils, bash,
  libfreetype6` — **no `libicu*`, no `libssl*`**. `dpkg-shlibdeps` will not find them either, because
  .NET `dlopen`s ICU rather than linking it. The `.rpm` at least requires `openssl`
  (`package-rhel.sh:500`) but likewise omits ICU. Usually latent on a desktop install where something
  else pulls ICU in; a hard startup failure on a minimal system. Add `libicu (>= 72)` / `libicu` to
  both dependency lists.
- **`SkiaSharp.NativeAssets.Linux` is referenced unconditionally** (`v2rayN.Desktop.csproj:40`). It
  contributes only `runtimes/linux-*/native`, so RID-specific Windows/macOS publishes ignore it. No
  action, noted so nobody "fixes" it.

---

## 6. What would fail in CI today

| # | Workflow | Line | Failure |
|---|---|---|---|
| C1 | `test.yml` | `:24` | Installs **`dotnet-version: '8.0.x'`** and then runs `dotnet test ./ServiceLib.Tests` against a `net10.0` solution (`Directory.Build.props:8`). `NETSDK1045: The current .NET SDK does not support targeting .NET 10.0`. It has simply never been updated. This is the only workflow gating pull requests, and it is gated on paths (`ServiceLib/Services/CoreConfig/**`, `ServiceLib/Handler/Fmt/**`) that the departament work does touch. **Fix: `10.0.1xx`.** |
| C2 | `departament-branch-build.yml` | `:11` | Triggers on push to **`claude/dp-desktop-incy`**. The current work is on `claude/app-audit-agents-hyyftk`, so the only branded build never fires automatically — it must be run by hand via `workflow_dispatch`. Either add the branch or switch the trigger to `branches: ['claude/**']` |
| C3 | `departament-branch-build.yml` | whole file | Does **not** publish `AmazTool`, so the shipped build has no updater binary. `Utils.UpgradeAppExists` (`Utils.cs:816-820`) returns false and `CheckUpdateViewModel.cs:320-324` reports «UpgradeAppNotExistTip». Combined with §7.1 this is arguably the safe state — but it is accidental, not chosen |
| C4 | `build-windows.yml`, `build-windows-desktop.yml`, `build-linux.yml`, `build-osx.yml` | each `:9-11` | All four trigger on **push to `master`**. When the departament branch merges, four upstream release pipelines start producing `v2rayN-*`-named artifacts. `build-windows.yml:22` builds the **WPF** `v2rayN.csproj`, which is not the departament app at all. Disable or re-scope these before merging to master |
| C5 | `build-linux.yml` deb/rpm jobs | `:51-52`, `:110-111` | Call the packaging scripts described in §4.1/§4.2 — they will publish a package that cannot launch, and can silently publish upstream code. Gated behind `refs/tags/`, so not live until the first tag |
| C6 | `winget-publish.yml` | `:19, :27-28` | Submits `2dust.v2rayN` to the Windows Package Manager on every `release: released` event, looking for assets named `v2rayN-windows-64.zip`. It has no departament identity and would either fail or, with the right secret configured, publish under upstream's package id. Delete it or re-point it |
| C7 | Action version skew | — | `build.yml`/`build-linux.yml`/`package-zip.yml`/`upload-sign.yml` use `actions/checkout@v7`, `upload-artifact@v7.0.1`, `download-artifact@v8`, `setup-dotnet@v5.4.0` and the `case()` expression function; `departament-branch-build.yml` uses `checkout@v4`, `setup-dotnet@v4`, `upload-artifact@v4`. Two different eras of the runner API in one repo. Not a today-failure, but pin one set — a `case()` that the runner does not understand fails at expression-evaluation time with a message that points at the wrong line |

---

## 7. Release-behaviour defects that are not build or packaging

### 7.1 "Check for updates" would install upstream v2rayN over departament

`ServiceLib/Global.cs:674` maps `ECoreType.v2rayN → "2dust/v2rayN"`, and `UpdateService.GetUrlFromCore`
(`:315-336`) resolves the asset from that repository's latest release, preferring the
`…-desktop.zip` variant when `libHarfBuzzSharp.dll` is present. The download is then handed to
`AmazTool`, which unzips it over the install directory.

Mitigating fact: **`CheckUpdateView` appears to be unreachable in the current shell.** Its only
references are `SimpleViewLocator.cs:19` (registration) and `DesignData.cs:36`; there is no
navigation to it from `MainWindow` or `SettingsView`. So this is latent, exactly like the Android
`CheckUpdateActivity` recorded in STATE-OF-WORK §3.5. Before anything wires an "обновить приложение"
row, `Global.cs:674` must point at the fork's release feed (or the row must be removed).

### 7.2 `AmazTool` cannot upgrade a binary called `departament`

`AmazTool/Utils.cs:27` — `public static string V2rayN => "v2rayN";` — is used twice:

- `UpgradeApp.cs:24`: `Process.GetProcessesByName(Utils.V2rayN)` to terminate the running app before
  overwriting it. With `AssemblyName=departament` the process is named `departament`, so **nothing is
  killed** and the unzip fails on a locked `departament.exe`.
- `Utils.cs:36`: `FileName = V2rayN` to relaunch after upgrade → starts nothing.

Both are one-word fixes (`=> "departament"`), and both should be made even though §7.1 currently
keeps the code unreachable — `BackupAndRestoreViewModel.cs:141-147` also invokes AmazTool for
`rebootas`, and that path *is* reachable.

### 7.3 Version string still reads 7.23.4

`Directory.Build.props:4`. `Utils.GetVersionInfo()` (`Utils.cs:857`) feeds the About row
(`SettingsViewModel.cs:196` → `AboutText`). A departament release that reports itself as v2rayN
7.23.4 makes every user bug report ambiguous. Pick a fork version scheme before the first tag; note
that `package-debian.sh`'s `resolve_version` derives the package version from git tags, so the two
must agree.

---

## 8. Ordered fix list

Sizes: **S** ≈ under an hour · **M** ≈ half a day · **L** ≈ a day or more.

### Blocks any release

| # | Fix | Where | Size |
|---|---|---|---|
| R1 | **Decide the elevation model.** Either drop to `asInvoker` and elevate only for TUN (the code already supports it), or keep `requireAdministrator` and move autostart to the scheduled task. Doing neither leaves autostart and browser SSO broken for a normal user | `app.manifest:10`; `SettingsViewModel.cs:177-179, 279-287` | **M** |
| R2 | **Stop `package-debian.sh`/`package-rhel.sh` (+4 variants) from moving HEAD.** Replace `resolve_version`'s checkout with `git describe` on the current tree | `package-debian.sh:157-215`, and the same block in five siblings | **S** |
| R3 | **Rename the binary through the packaging scripts** — `APP_BIN`/`INSTALL_DIR` variables; fix the launcher, the `chmod`, the `.desktop` entry, the control/spec metadata. Update `Utils.IsPackagedInstall`'s prefix list in the same commit | `package-debian.sh:515-541, 600-677`; `package-rhel.sh:500-571`; `ServiceLib/Common/Utils.cs:1284-1296` | **M** |
| R4 | **Fix `package-osx.sh`'s `CFBundleExecutable`** and the bundle identifier, or mark macOS explicitly out of scope for this release | `package-osx.sh:15-17, 37-47, 63-70` | **S** |
| R5 | **`test.yml` → .NET 10.** It cannot pass today | `.github/workflows/test.yml:24` | **S** |

### Should be fixed before the first public build

| # | Fix | Where | Size |
|---|---|---|---|
| R6 | **Remove `UseSystemResourceKeys`** — it turns every framework error message, including the ones the app shows users and writes to logs, into an opaque key | `Directory.Build.props:28` | **S** |
| R7 | **Point `ECoreType.v2rayN` at the fork's release feed, or delete the update path.** Do not leave an updater aimed at upstream in a shipped build | `ServiceLib/Global.cs:674` | **S** |
| R8 | **`AmazTool`: `V2rayN => "departament"`** (both uses) | `AmazTool/Utils.cs:27` | **S** |
| R9 | **Set a fork version** and make `Directory.Build.props` and the git tag scheme agree | `Directory.Build.props:4` | **S** |
| R10 | **Point `departament-branch-build.yml` at the branch that is actually being built**, and publish `AmazTool` alongside the app if the update path is kept | `departament-branch-build.yml:11, 30-32` | **S** |
| R11 | **Disable or re-scope the four upstream `push: master` release workflows and `winget-publish.yml`** before this branch merges | `build-windows.yml`, `build-windows-desktop.yml`, `build-linux.yml`, `build-osx.yml`, `winget-publish.yml` | **S** |
| R12 | **Add `libicu` to the deb/rpm dependency lists** (`InvariantGlobalization=false` makes it mandatory), and `libssl` to the deb | `package-debian.sh:614`; `package-rhel.sh:500-507` | **S** |
| R13 | **Resolve the two competing autostart mechanisms** — one name, one mechanism, one reader. Keep `ClearStartupApprovedFlag`, whichever survives | `AutostartHelper.cs`; `AutoStartupHandler.cs:44-125`; `OptionSettingViewModel.cs:429` | **M** |

### Release quality, not correctness

| # | Fix | Where | Size |
|---|---|---|---|
| R14 | **Authenticode-sign the Windows exe** (and, if macOS ships, codesign + notarise). Unsigned + `requireAdministrator` is the worst first-run impression available | `departament-branch-build.yml`; `upload-sign.yml` | **M** |
| R15 | **Turn on `PublishReadyToRun` for win-x64.** Largest available cold-start win, zero correctness risk. Keep trimming off | `Directory.Build.props:30` | **S** |
| R16 | **`SatelliteResourceLanguages=ru;en`** — drops six unused localisation folders from the install | `Directory.Build.props` | **S** |
| R17 | **Publish `AmazTool` to a subdirectory**, or drop `-p:PublishTrimmed=true` from it, so a future `PublishSingleFile=false` cannot overwrite the app's assemblies with trimmed copies | `build.yml:79-84`; `build-windows-x86.yml:69-82` | **S** |
| R18 | **Re-evaluate `CETCompat=false`.** Inherited; a VPN client running elevated should opt out of a hardware exploit mitigation only for a demonstrated reason | `v2rayN.Desktop.csproj:15` | **S** |
| R19 | **Delete one of `v2rayN.sln` / `v2rayN.slnx`** and the dangling `package-release-zip.sh` reference | repo root | **S** |
| R20 | **Make the `GlobalHotKeys` reference self-diagnosing** (`Error` target on missing submodule) — optional; CI already initialises it correctly | `v2rayN.Desktop.csproj:46` | **S** |

### Explicitly do not do

- **Do not enable `PublishTrimmed` on `v2rayN.Desktop`.** Eight reflection surfaces (§2.2); two of
  them — `System.Text.Json` config/DTO loading and the `sqlite-net` server store — fail *silently*
  against the user's saved data.
- **Do not replace `SimpleViewLocator` with a convention-based `ViewLocator`.** It is the one thing
  in the app that keeps a future trimmed build possible.
- **Do not run any `package-*.sh` on this working tree** until R2 lands. `git checkout -f` against
  ~22 uncommitted desktop files is unrecoverable.
