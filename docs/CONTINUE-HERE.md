# Continue here — departament VPN

Handoff for the next session. Verified against the code and the commit log, not assumed.
Rewritten 2026-08-04. The previous version pointed at `claude/app-audit-agents-hyyftk`, which has not
been worked on since 2026-07-26 and predates the owner's ruling in §1 — it was actively misleading.

**Branch, both repos: `claude/project-analysis-status-9cefax`.** Develop and push there.

| | Repo | Local path | Stack | Build root |
|---|---|---|---|---|
| Android | `s-erlish/dp` | `/home/user/dp` | Kotlin, Material 3, XML views, `com.v2ray.ang` | `V2rayNG/` |
| Desktop | `s-erlish/v2rayN` | `/home/user/v2rayN` | C# .NET 10, Avalonia 12.1, ReactiveUI | `v2rayN/` |

The owner writes in Russian and the product ships in Russian. Answer in Russian.

---

## 1. The rule that outranks every specification

**Refine the design. Never rebuild it.** The owner has said this twice, because two separate waves
read a document in `docs/design2026/` as an order to demolish a working screen:

> «главная суть, чтобы дизайн то остался тот же, что и был раньше, а просто переработан... просто
> нужно ДОРАБОТАТЬ дизайн, а не полностью его переделывать»

And its corollary from 2026-07-27: **refinement, never removal.** An element that was on screen and
is now gone is a regression, not a simplification — animations included. Recover it from git history.

Before touching a screen, ask: *does a user who knows this screen still recognise it after my change?*
If no, stop, whatever the specification says.

Read `docs/agents/state/OWNER-DECISION-2026-08-02.md` in full before any UI work. It names the four
specs that are void as work orders and records which product decisions are settled — notably that
editing a подписка is **not** a feature and must not be "restored" under the refine-never-remove rule.
`docs/agents/state/OWNER-FEEDBACK-2026-07-27.md` is the earlier ruling. `CLAUDE.md` carries the
design-token law: one spacing scale, one 16dp gutter, one blue accent, sentence-case Russian, no
nested cards, no decorative gradients.

## 2. Build and verify

`docs/agents/BUILD-VERIFY.md` has the detail. Short version:

```bash
# Android
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd /home/user/dp/V2rayNG && ./gradlew :app:assembleFdroidDebug --no-daemon

# Desktop
export DOTNET_ROOT=/opt/dotnet PATH=/opt/dotnet:$PATH
cd /home/user/v2rayN/v2rayN && dotnet build v2rayN.Desktop/v2rayN.Desktop.csproj -c Release
```

Two things a fresh container needs first, or neither app builds:

- **Android**: the native `libv2ray.aar` is not in the repo — CI downloads it from the core library's
  releases. A gitignored type-check stub lives at `V2rayNG/app/libs/libv2ray-stub.jar` and a fresh
  clone must regenerate it; the class surface is `go.Seq`, `libv2ray.Libv2ray`,
  `libv2ray.CoreController`, `libv2ray.CoreCallbackHandler`, `libv2ray.ProcessFinder`. It exists to
  type-check only — never reference it from app code, never commit it.
- **Desktop**: `git submodule update --init --recursive` for `GlobalHotKeys`, or the build fails CS0246.

Flavours are `fdroid` and `playstore`; there is no plain `compileDebugKotlin` task.

**The bar: builds succeed AND no new warnings.** Baselines are `docs/agents/.baseline-warnings.txt`
(Android, 12 entries) and `.baseline-warnings-desktop.txt` (desktop, 8 entries). Compare normalised —
strip `:line:col`, because line numbers shift and that is not a regression.

**An APK built here cannot be run** — the native library is absent. Diagnosis is a static trace;
confirmation comes from a CI build the owner installs. Separate "does not start" from "did not
install" before concluding anything.

## 3. Where the work stands

Both clients build clean. The owner has confirmed on a real device that the Android app connects,
updates subscriptions and pings. Each commit message on the branch states what was wrong rather than
what was touched; read the log for the full account.

**Android** — install blocker fixed (the APK was unsigned and its versionCode went backwards); two
add-server crashes; the blue accent restored, lost because an opaque fragment root covered the
gradient; the giant elliptical ambient ring and the missing add buttons, which were one geometry bug
on two axes; three subscription defects; per-device identity that survives reinstall; the `import sub`
placeholder purged from storage and UI; the reconnect offer, which one decline made unreachable
forever; upstream v2rayNG notifications silenced; the update checker, which pointed nowhere.

**Desktop** — the connect blocker (a `SysProxyType` default); decoy outbound resolution in ping; the
owner's seven screenshot items, two of which were one defect; Russian text no longer set in a
Latin-only face; per-machine device identity; the account tab brought to parity with Android; a games
preset for per-app bypass; clipboard add that promised servers before it had any; the same reconnect
hole as Android; hover flicker chased to a shared cause in `GlobalStyles.axaml`.

CI builds a ready-to-run Windows x64 artifact on every push to the branch:
`.github/workflows/departament-branch-build.yml` → artifact `departament-vpn-win-x64`.

### Invariants — do not regress these

The reasoning lives in code comments next to each; later work touching those files must preserve it.

- **Tapping a server selects, never connects** (`MainActivity.setSelectServer`). With a tunnel up the
  connection is left alone and an explicit «Переподключиться» offer appears. The offer is gated on
  *the running guid differing from the tapped one* — not on the tapped one differing from the stored
  selection, which is what made it vanish after a single decline.
- **The server-switch race** (`CoreServiceManager.stopCoreLoop`). `MSG_STATE_STOP_SUCCESS` must be
  sent *after* `stopLoop()` returns; the UI starts the next core on that message. Sending it early
  left the old core serving the previous server while the UI showed the new one.
- **A template's server comes from its routing**, not the first proxy outbound
  (`V2rayConfig.getProxyOutbound`). Operator templates ship several proxy outbounds and pick one with
  a rule; reading the first showed the wrong protocol and pinged a host that is not the server.
- **Subscription naming happens at import** (`handler/SubscriptionNaming.kt`, desktop twin
  `ServiceLib/Handler/SubscriptionNaming.cs`). There is no rename UI by owner decision, so a bad
  default name is permanent. Resolution order: account nickname → provider `profile-title` → stored
  remark → account default label → «Подписка», with placeholders rejected.
- **The desktop onboarding gate** takes a synchronous storage snapshot before the first frame.
  `_isEmpty` must mean *we know it is empty*, never *not loaded yet*.

## 4. Outstanding

- **Unverified, desktop commit `4f13ce6`** — the agent ended without delivering a report, so the
  commit message describes the diff rather than the work. Three claims are unconfirmed: whether the
  flicker audit covered every clickable control, the Telegram button's measured contrast in all four
  themes, and whether the QR add path matches the clipboard one.
- **Desktop has no three-way mode control** (TUN / Proxy / TUN + Proxy). Android has it. Owner
  feedback item C2, never done.
- **`DnsSubView.SaveAndBackAsync` restarts unconditionally** — the same defect fixed in
  `PerAppProxyPage`, still present in the second place.
- **Release machinery is deferred by the owner** («пока что без ключей, не релизное приложение
  пока»). When it resumes: AmazTool in the branch build, and the upstream publishers in
  `s-erlish/v2rayN` still push to `2dust.v2rayN` — `winget-publish.yml`, `package-zip.yml`,
  `upload-sign.yml`, `pub-key.yml`. They fire on a release in that repo, so a tag on `dp` cannot
  reach them, but they must be neutered before any desktop release is cut.

## 5. Tooling

`.mcp.json` declares the Stitch MCP server — Google's UI design tool — for cloud sessions. It reads
`${STITCH_API_KEY}` from the cloud environment's variables, so the key is never in the repo. If the
server does not appear, project-scope approval is missing; adding `.claude/settings.json` with
`{"enableAllProjectMcpServers": true}` is the owner's call, not an agent's.

Stitch emits HTML/CSS. This project is Android XML and Avalonia AXAML, so its output is a reference,
never a drop-in — and it generates whole screens from scratch, which is the exact failure mode §1
exists to prevent. Use it for one element's idea, never for a screen.
