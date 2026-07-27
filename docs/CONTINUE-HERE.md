# Continue here — Departament VPN, 2026 rebuild

Handoff for the next session. Everything below is verified against the code, not assumed.

**Branch (both repos): `claude/app-audit-agents-hyyftk`** — always develop and push there.

| | Repo | Stack | Build root |
|---|---|---|---|
| Android | `s-erlish/dp` | Kotlin + Material 3 + XML views, `com.v2ray.ang` | `V2rayNG/` |
| PC | `s-erlish/v2rayN` | C# .NET 10 + Avalonia 11 + ReactiveUI | `v2rayN/` |

---

## 1. Build and verify — both apps compile in this environment

Read `docs/agents/BUILD-VERIFY.md` for the full detail. Short version:

```bash
# Android
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
cd /home/user/dp/V2rayNG && ./gradlew :app:assembleFdroidDebug --no-daemon

# PC
export DOTNET_ROOT=/opt/dotnet PATH=/opt/dotnet:$PATH
cd /home/user/v2rayN/v2rayN && dotnet build v2rayN.Desktop/v2rayN.Desktop.csproj -c Release
```

Two things a fresh container needs first, or neither app builds:

- **Android**: the native `libv2ray.aar` is not in the repo (CI downloads it from the core library's
  releases). A local type-check stub lives at `V2rayNG/app/libs/libv2ray-stub.jar`; it is gitignored,
  so a fresh clone must regenerate it — the class surface needed is `go.Seq`, `libv2ray.Libv2ray`,
  `libv2ray.CoreController`, `libv2ray.CoreCallbackHandler`, `libv2ray.ProcessFinder`. It exists only
  to type-check; never reference it from app code and never commit it.
- **PC**: `git submodule update --init --recursive` for `GlobalHotKeys`, or the build fails CS0246.

Build flavours are `fdroid` and `playstore` — there is no plain `compileDebugKotlin` task.

**The bar is: builds succeed AND no new warnings.** Baselines are recorded in
`docs/agents/.baseline-warnings.txt` (Android, 21) and `.baseline-warnings-desktop.txt` (PC, 28).
Compare normalised (strip `:line:col`) — line numbers shift and that is not a regression.

---

## 2. What is already fixed — do not regress these

Ten defects are fixed, committed and build-verified. Three of them are load-bearing: later work
touching those files must preserve the logic, and the comments in the code explain why.

**Android**

1. **Tapping a server selects, never connects.** `MainActivity.setSelectServer()`. With a tunnel up,
   the connection is left alone and an explicit "Переподключиться" action is offered.
2. **The server-switch race, fixed at its root.** `CoreServiceManager.stopCoreLoop()` used to send
   `MSG_STATE_STOP_SUCCESS` from a fire-and-forget coroutine *before* the core had stopped. The UI
   starts the next core on that message, so it lied. In VPN mode `CoreVpnService` tore the tunnel
   down; in proxy-only mode `CoreProxyOnlyService` ignored `startCoreLoop`'s result entirely and the
   old core kept serving the **previous** server while the UI showed the new one. The message is now
   sent after `stopLoop()` returns, and the proxy service honours the result.
3. **Two rows painted as selected.** Selection lives in MMKV, which cannot notify, and is written by
   subscription import, fast-connect and service start. `MainRecyclerAdapter` now mirrors the guid,
   re-reads it on every rebuild, and falls back to a full refresh when a row cannot be located.
4. **A template's server is read from its routing**, not from the first proxy outbound —
   `V2rayConfig.getProxyOutbound()`. Operator templates ship several proxy outbounds and select one
   with a rule; reading the first one meant the row showed the wrong protocol, the TCP ping probed a
   host that is not the server (so the profile looked unpingable), and the delay test measured the
   wrong outbound after stripping routing. The speedtest builder promotes the same outbound.
5. **Subscription format negotiation.** Panels choose XRAY_JSON vs base64 from the User-Agent and the
   fetch used to overwrite the caller's value. Precedence is now per-subscription override → provider
   override → configured default, with a JSON-first `Accept`.
6. **Flags** require an explicit country marker and map `UK`→`GB`; no flag beats a wrong flag.
7. **Auto-fallback** waits for a confirming re-probe instead of switching on one failed health check.
8. **Provider-settings toggles** drive real behaviour instead of only storing a value.

**PC**

9. **The onboarding gate no longer greets a returning user.** It was decided from `_isEmpty = true`,
   a default indistinguishable from the fact "this user has nothing", while servers load
   asynchronously. It now takes a synchronous storage snapshot before the first frame;
   `_isEmpty` means *we know it is empty*, never *not loaded yet*.
10. **Windows autostart.** The toggle showed stored intent, not the registry; enabling now also
    clears the `StartupApproved` disable flag Windows keeps, and reconciles at startup. It stays a
    per-user `HKCU\...\Run` value named `departament` so it appears in Task Manager → Startup.

---

## 3. What is written but NOT implemented

`docs/design2026/` — 19 specifications, ~13,500 lines, all committed. **The design exists on paper
only. No screen has been rebuilt yet.** This is the largest remaining piece of work.

| File | What it decides |
|---|---|
| `00-rules.md` | The operational design law. Outranks every other spec on conflict. |
| `03-direction.md` | The committed visual direction and what it forbids. |
| `10-design-system.md` | Token set + component library, mapped to both platforms. |
| `11-app-structure.md` | Navigation model, launch flow, what gets merged or deleted. |
| `12-settings.md` | Every settings row on both platforms, with what it actually changes in code. |
| `13-start-screen.md` | The first tab at launch. |
| `14-auth.md` | Sign-in and first run, as a state machine. |
| `15-account-tab.md`, `23-account-rework.md` | Account, Android and both platforms. |
| `22-components.md` | The shared component and button system. |
| `24-tab-conformance.md` | Per-screen change list for every remaining screen. |
| `30-reference-analysis.md`, `31-self-assessment.md` | Happ/Incy analysis; honest verdict on our screens. |
| `32-`/`33-master-plan-*.md` | Screen-by-screen master plans, Android and PC. |

Missing: `16-servers.md` (the servers spec) — its workflow was stopped before writing it. The servers
sections of `32-master-plan-android.md` and `24-tab-conformance.md` cover the same ground meanwhile.

`docs/agents/` — 45 recon, audit, hunt and verification reports. The most useful when resuming:

- `recon-docs-plans.md` — the consolidated backlog, every claim checked against the source.
- `recon-android-selection.md` — the selection/connect defect analysis; it also caught an error in an
  earlier fix, so trust its method.
- `hunt-*.md` — cold-start, persistence and transient-UI defect hunts for both platforms.
- `verify-*.md` — adversarial verification of individual findings; some findings were refuted.

Also missing, because the recon wave was stopped in its synthesis phase:
`gap-desktop-to-android.md`, `bugs-android-confirmed.md`, `brief-android-redesign.md`.

---

## 4. The remaining backlog, in priority order

Verified against the source in `recon-docs-plans.md`; re-check before acting, the tree has moved.

**Functionality that exists but users cannot reach**

1. **19 amputated menu actions.** A compile error was resolved by deleting the `when` branches rather
   than restoring the menu ids, so the implementations are live but unreachable dead code: export all,
   ping all, delete all/duplicates/invalid, sort by test result, locate selected, manual import for
   every protocol, import from file. See `res/menu/menu_main.xml` and `MainActivity.kt`.
2. **Advanced settings unreachable.** `SettingsActivity` is declared in the manifest with no launch
   site anywhere; `res/xml/pref_settings.xml` declares 55 keys, **29 with no editing UI at all**.
3. **No user-visible logout.** `AccountViewModel.logout()` has zero call sites.
4. **`CheckUpdateActivity`** has zero launch sites — update checking is unreachable.
5. **RAM panel** can never be enabled: `PREF_SHOW_MEMORY` is read, never written.

**Then**

6. Implement the design specs — foundation tokens and components first, then screens. Doing screens
   first means rebuilding each one twice.
7. Localisation: 321 keys have no `values-ru` entry; 24 of those are Latin-only and user-facing.
8. Both READMEs are still unmodified upstream boilerplate.

---

## 5. How the work was run, and why

Agents did the work in waves, and the pattern is worth keeping because it caught real errors:

- **Disjoint file groups.** Every implementing agent owns an explicit file list and may not touch
  anything else; it reports what it needs outside that list instead of editing it. This is what
  allows parallelism without conflicts.
- **Build verification is not optional.** An agent that cannot show a green build has not finished.
- **Adversarial review of every implementation.** A second agent that does not trust the first one's
  report reads the actual files, and its first question is always *did this silently drop a feature*.
  This caught a wrong root-cause in a fix of mine, an incomplete User-Agent precedence chain, and a
  crash on a Cyrillic User-Agent.
- **Findings are refuted by default.** High-severity claims go to a verifier told to assume the claim
  is wrong. Several did not survive.

Do not let an agent commit. Review, build, then commit centrally — concurrent agent commits race.
