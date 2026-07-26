# Android functional restorations — verified state

**Agent:** verify-android-features · **Date:** 2026-07-26
**Repo:** `/home/user/dp`, branch `claude/app-audit-agents-hyyftk`, HEAD `c9d7bf0`
**Scope:** the six functional claims raised in `docs/agents/recon-docs-plans.md` (P0-1, P0-2, P0-4,
P0-5, P1-14) and the five small ports in `docs/agents/gap-desktop-to-android.md` (W2–W6).

**Method:** read the source, not the reports. For every UI element: does the layout/menu resource
declare it, and does code bind it? For every preference: find the WRITE and find the READ. For every
previously-orphaned Activity: find a launch site a user can actually reach with a tap. No files were
changed except this one; no git state was modified.

**Caveat on line numbers.** The working tree is not clean — `MainActivity.kt`, `BaseFragment.kt` and
`activity_main.xml` carry uncommitted edits from a concurrently running session (+184 lines in
`MainActivity.kt` alone), and the file shifted under me between reads. Line numbers below are from
the working tree as read at 18:00–18:07Z; the function names are stable, the numbers may drift by
tens of lines. Everything reported was verified in the working tree — i.e. against what would build
right now, not against `HEAD`.

---

## Verdict table

| # | Claim | State |
|---|---|---|
| 1 | 19 amputated menu actions restored | **done** — 18 of 19 reachable, 1 (explicit "restart service") deliberately absent |
| 2 | Advanced settings reachable, keys have UI | **built, unreachable** — screen fully rebuilt, `SettingsActivity` still has **0 launch sites** |
| 3 | User-visible sign-out | **done** — real row, real dialog, real wipe |
| 4 | `CheckUpdateActivity` reachable | **not started** — still 0 launch sites |
| 5 | `PREF_SHOW_MEMORY` writable | **not started** — still 0 writers; the card is permanently dead |
| 6a | Latency probe timeout (W2) | **not started** — no key, still `timeoutMs = 3000` |
| 6b | Latency probe address (W2) | **built, unreachable** — UI only inside the orphaned advanced screen |
| 6c | FakeIP row (W3) | **built, unreachable** — same screen |
| 6d | Default uTLS fingerprint (W4) | **not started** |
| 6e | Routing-rule import from URL (W5) | **not started** |
| 6f | WebDAV connection test (W6) | **not started** |

---

## 1. The 19 menu actions — DONE (18/19 reachable)

`res/menu/menu_main.xml` now declares **12 items in two groups** (was 4):

- `group_import`: `import_qrcode`, `import_clipboard`, `import_manually_vless`, `import_create`,
  `import_file`, `tv_send`
- `group_server_list`: `servers_locate`, `servers_sort`, `servers_export`, `servers_del_duplicate`,
  `servers_del_invalid`, `servers_del_all`

**Entry points a user can tap** (both real, both bound):

- Home "+" → `MainActivity.kt:383` `binding.btnHomeAdd.setOnClickListener { showImportMenu(it) }`
  (`res/layout/activity_main.xml:162` `btn_home_add`, no visibility gating in code).
- Servers header "+" → `setupServersHeader()` `header.btnAdd.setOnClickListener { showImportMenu(it,
  withListActions = true) }` (`res/layout/layout_servers_header.xml:66`).

`showImportMenu()` inflates `menu_main` into a `PopupMenu` and routes clicks to
`onOptionsItemSelected`, which has a live branch for **every one of the 12 ids**. `prepareMenu()`
hides `group_server_list` when there are no servers, disables `servers_export` when every server is
operator-locked, and hides `servers_locate` when no selected server exists — so no item in the menu
is a dead end.

Mapping the 19 ids that `compile-review-final.md:20-42` listed as deleted, to what exists now:

| Old id | Now | Evidence |
|---|---|---|
| `import_local` | ✅ `import_file` → `importConfigLocal()` | `MainActivity.kt` `importConfigLocal()` → `showFileChooser()` |
| `import_manually_{vmess,ss,socks,http,trojan,wireguard,hysteria2}` | ✅ one picker | `import_create` → `pickManualServerType()` offers VLESS/VMess/Trojan/Shadowsocks/WireGuard/Hysteria2/SOCKS5/HTTP → `importManually(type)` |
| `import_manually_policy_group` | ✅ same picker ("Группа" → `EConfigType.POLICYGROUP`) | `pickManualServerType()` |
| `import_manually_proxy_chain` | ✅ same picker, offered only when ≥2 chainable servers exist (matches `ServerProxyChainActivity.saveServer`'s own requirement) | `pickManualServerType()` |
| `export_all` | ✅ `servers_export` → `exportAll()` | real body: `mainViewModel.exportAllServer()`, count reported |
| `ping_all` / `real_ping_all` | ✅ merged into one control | Servers header `btnSpeedtestAll` → `startLatencyCheckAll()` → `MainViewModel.testAllServers()` (`MainViewModel.kt:462`) which dispatches on `SettingsManager.getPingMethod()`; the method itself is user-selectable from the reachable settings tab (`rowPingMethod` → `pickPingMethod()`) |
| `del_all_config` | ✅ `servers_del_all` → `delAllConfig()` (confirm dialog, red confirm, WebDAV config carried across the wipe) | |
| `del_duplicate_config` | ✅ `servers_del_duplicate` → `delDuplicateConfig()` (delete + undo snackbar) | |
| `del_invalid_config` | ✅ `servers_del_invalid` → `delInvalidConfig()` (delete + undo, empty case offers the check) | |
| `sort_by_test_results` | ✅ `servers_sort` → `sortByTestResults()` (unmeasured case offers the check instead of spinning) | |
| `locate_selected_config` | ✅ `servers_locate` → `locateSelectedServer()` (clears the search and expands collapsed groups before it will admit failure) | |
| `service_restart` | ❌ **no user-facing action.** `restartV2Ray()` has 6 internal callers (settings changes, server switch, auto-fallback) and `restartIfRunning()` 8 more, but nothing the user taps says "restart". Practically covered by disconnect/connect on the hero control | |

All 12 titles resolve to Russian strings (`strings_menu_actions.xml`, e.g. `menu_actions_del_all` =
«Удалить все серверы»), all 10 icons exist in the default `drawable/` config, and an exhaustive
`R.string.*` check across `MainActivity`/`SettingsActivity`/`AccountFragment` found no missing string
resources.

**Not fully restored:** the granular per-protocol menu items are gone as *menu items* — they are now
one "Создать вручную" item plus a type picker. Functionally equivalent, one extra tap.

---

## 2. Advanced settings — BUILT, STILL UNREACHABLE

This is the headline failure. The screen was **rebuilt properly** and then **not wired to anything**.

### What was built

`res/xml/pref_settings.xml` was rewritten from 55 keys to **25 `android:key` values = 19 real
preferences + 5 `PreferenceCategory` keys + 1 non-persistent note row**. Every one of the 19 has
editing UI:

| Group | Keys |
|---|---|
| Ядро | `pref_core_loglevel`, `pref_sniffing_enabled`, `pref_route_only_enabled`, `pref_allow_insecure`, `pref_auto_fallback` |
| Туннель | `pref_vpn_mtu`, `pref_vpn_interface_address_config_index` |
| DNS | `pref_local_dns_enabled`, `pref_fake_dns_enabled`, `pref_domestic_dns`, `pref_dns_hosts`, `pref_outbound_domain_resolve_method` |
| Фрагментация | `pref_fragment_length`, `pref_fragment_interval`, `pref_fragment_packets` |
| Проверка задержки | `pref_delay_test_url`, `pref_real_ping_concurrency`, `pref_auto_sort_after_test`, `pref_auto_remove_invalid_after_test` |

`SettingsActivity.kt` (600 lines) binds 16 of them explicitly with input filters, range validation,
normalisation and rejection messages (`bindNumberField`, `bindRangeField`, `bindTextField`,
`normalizeDnsList`, `normalizeHosts`, `normalizeRange`, `normalizeProbeUrl`); the remaining three are
plain `CheckBoxPreference`s that persist through the data store. Dependencies are modelled
(`route_only` hidden unless sniffing is on; FakeIP disabled with a reason unless local DNS is on;
the three fragment fields disabled with a reason while fragmentation is off). Section deep-links
exist (`EXTRA_SECTION` → `SECTION_DNS`/`SECTION_FRAGMENT`/`SECTION_LATENCY` → `applyRequestedSection()`).

The write path is correct: `preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()`
(`SettingsActivity.kt:150`), and `MmkvPreferenceDataStore` writes each type straight to MMKV
(`helper/MmkvPreferenceDataStore.kt:17-73`) — the same store every reader uses. Defaults match the
readers (e.g. `pref_auto_fallback` default `true` in XML, `decodeSettingsBool(PREF_AUTO_FALLBACK,
true)` in `MainActivity`). Strings (64 in `values/strings_settings_advanced.xml`), all 8 arrays, and
both layouts (`activity_settings.xml`, `preference_with_help_link.xml`) exist.

### Why none of it works

```
grep -rn "SettingsActivity\." java/ --include=*.kt | grep -v ui/SettingsActivity.kt | grep -v Provider
→ 0
```

**Zero launch sites.** `SettingsActivity.newIntent()` (`SettingsActivity.kt:80`) has no callers. Its
own KDoc claims *«Вкладка настроек зовёт его так: `startActivity(SettingsActivity.newIntent(this))`»*
— that call does not exist. `setupSettings()` (`MainActivity.kt:3011`, 40 lines wiring every settings
row) has no "Дополнительно" row; `layout_settings_content.xml` contains no `adv_*` row. The manifest
declares it `android:exported="false"` with no intent-filter (`AndroidManifest.xml:89`), so there is
no deep link either.

**So: 19 of 19 keys have editing UI, and 0 of 19 are reachable.** The recon's P0-2 is not fixed; it
has changed shape from "29 keys with no UI" to "19 keys with good UI behind a door with no handle".

### Where the other 36 keys went

Of the 36 keys dropped from the old file: **20 now have reachable UI elsewhere** — settings tab
(`pref_mode`, `pref_vpn_bypass_lan`, `pref_ipv6_enabled`, `pref_vpn_dns`, `pref_remote_dns`,
`pref_ping_method`, `pref_mux_enabled`, `pref_mux_concurrency`, `pref_fragment_enabled`,
`pref_ui_mode_night`, `pref_color_theme`, `pref_language`, `pref_proxy_sharing_enabled`),
Local proxy (`pref_enable_local_proxy`, `pref_socks_port/username/password`, `pref_socks_enable_udp`,
`pref_append_http_proxy`), Per-app proxy (`pref_per_app_proxy`). 1 is set automatically
(`pref_speed_enabled`, forced `true` at `MainActivity.kt:349`). 3 are dead constants with zero
references anywhere (`pref_local_dns_port`, `pref_start_scan_immediate`, `pref_double_column_display`).
1 is internal (`pref_is_booted`).

**11 remain read-with-no-writer** — the exact defect this project keeps reintroducing. Verified
`encodeSettings(AppConfig.X…) == 0` for each:

```
pref_confirm_remove        (read in 5 screens; deletion confirmation can never be turned on)
pref_show_memory           (see §5)
pref_group_all_display     pref_prefer_ipv6           pref_ip_api_url
pref_mux_xudp_concurrency  pref_mux_xudp_quic         pref_dynamic_socks_port
pref_socks_share_port      pref_use_hev_tunnel_v2     pref_hev_tunnel_loglevel
pref_hev_tunnel_rw_timeout_v2
```

The file header calls most of these deliberate ("настройку принимает приложение", per
`12-settings.md` 6.1) — defensible for the tunnel internals, less so for `pref_confirm_remove`, which
is still branched on at `MainActivity.kt:1644` and can now only ever be `false`.

One inconsistency against the file's own claim of *«строк с двумя домами — 0»*:
`pref_route_only_enabled` has two editing homes — the advanced screen and the reachable Local-proxy
screen (`LocalProxyActivity.kt:224-230`, row `row_route_domain`).

---

## 3. Sign-out — DONE, end to end

Every link in the chain exists:

- **Row:** `res/layout/activity_account.xml:587` `row_logout` — a clickable 56dp row in its own card,
  destructive title style, with an inline `pb_logout` spinner. Always present (no visibility gating).
- **Binding:** `AccountFragment.kt:200` `binding.rowLogout.setOnClickListener { confirmSignOut() }`.
- **Confirm:** `confirmSignOut()` — `MaterialAlertDialog`, positive button labelled «Выйти» in
  `color_destructive_text`, cancel holds focus, body varies on `viewModel.isTunnelRunning()`.
- **Action:** `beginSignOut()` → `viewModel.logout(onFailure = ::onSignOutFailed)`; row disabled,
  spinner scheduled at 300 ms, failure path re-enables the row and shows a Snackbar with «Повторить».
- **Effect:** `AccountViewModel.logout()` (`viewmodel/AccountViewModel.kt:457`) runs
  `AccountSession.wipe()` on a `NonCancellable` job behind a watchdog, then stops the tunnel,
  invalidates `AccountCache`, clears the custom avatar. `AccountSession.wipe()`
  (`auth/AccountSession.kt:55`) = `subs.removeAllManaged()` + `AuthTokenStore.clear()` + state →
  `LoggedOut`; `removeAllManaged()` (`auth/SubscriptionSyncManager.kt:99`) cancels each managed
  subscription's updater and removes it.
- **Reachability:** the Account tab is shown whenever `AccountSession.isLoggedIn()`
  (`MainActivity.updateAccountGate()`, `binding.navAccount.isVisible = loggedIn`), so a signed-in
  user always has the tab that holds the row.
- **Strings:** Russian, present (`account_row_logout` = «Выйти», plus title/body/progress/failed).

---

## 4. `CheckUpdateActivity` — NOT STARTED

```
grep -rn "CheckUpdateActivity" java/ --include=*.kt | grep -v ui/CheckUpdateActivity.kt → 0
```

Declared at `AndroidManifest.xml:192`, `exported="false"`, no intent-filter, no launch site. The
"О приложении" row goes to `AboutActivity` (`MainActivity.kt:3049`); nothing offers an update check.
Consequence: `PREF_CHECK_UPDATE_PRE_RELEASE` is written only inside this unreachable Activity.

Same class of orphan, also still unreachable: `LogcatActivity` (contradicting `impl-s4-settings.md`,
which puts «Логи туннеля» under ОТЛАДКА), `SubSettingActivity`, `AppPickerActivity` (reachable only
indirectly from `RoutingEditActivity`).

---

## 5. `PREF_SHOW_MEMORY` — NOT STARTED

- **READ:** `MainActivity.updateMemoryCard()` — `MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_MEMORY,
  false)`; `binding.cardMemory.isVisible = show`.
- **WRITE:** none. `grep -rn "encodeSettings(AppConfig.PREF_SHOW_MEMORY" → 0`. The whole-tree grep for
  the literal returns only the constant, the one read, the `pref_settings.xml` comment listing it as
  *removed on purpose*, two English strings, and the layout comment.

Because the rewrite deliberately dropped `pref_show_memory` from `pref_settings.xml`, the last
theoretical writer is gone: the memory card in `activity_main.xml:311` is now permanently invisible
dead UI, and `MemoryStatsManager` is a subsystem nothing can turn on. `master-requirements-audit.md`
still calls this "13a DONE".

---

## 6. The five desktop→Android ports

### 6a. Latency probe timeout (W2, `PREF_PING_TIMEOUT`) — NOT STARTED
`grep -rn "PREF_PING_TIMEOUT" java/ res/ → 0`. `handler/SpeedtestManager.kt:305`
`fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 3000)` — still hard-coded, and its one
caller (`:281`) passes no override. `12-settings.md` 5.6 specifies the row and the 3/5/10/15 s picker;
nothing implements it.

### 6b. Latency probe address (W2, `PREF_DELAY_TEST_URL`) — BUILT, UNREACHABLE
WRITE: `pref_settings.xml:248-256` (`EditTextPreference`, validated by `normalizeProbeUrl` — http/https
only) → unreachable per §2. READ: real and live — `SettingsManager.getDelayTestUrl()` (`:577-581`),
`CoreConfigManager.kt:1173`, plus a default seeded at `SettingsManager.kt:705`.

### 6c. FakeIP row (W3, `PREF_FAKE_DNS_ENABLED`) — BUILT, UNREACHABLE
WRITE: `pref_settings.xml:145-151`, correctly gated on local DNS with the reason in the summary
(`SettingsActivity.updateLocalDnsDependants`) → unreachable per §2. READ: three real consumers —
`CoreConfigManager.kt:669` (sniffing), `:736-741` (`configureFakeDns`), `:790` (DNS server priority).
No FakeIP row exists on the reachable settings tab.

### 6d. Default uTLS fingerprint (W4) — NOT STARTED
No key (`AppConfig` has none matching), no UI, no read. `core/CoreOutboundBuilder.kt:564` still passes
`fingerprint = profileItem.fingerPrint.nullIfBlank()` straight through, so an empty per-server
fingerprint stays empty — exactly the state `gap-desktop-to-android.md` S1 and
`strategy-russia-2026.md` #3 flag.

### 6e. Routing-rule import from URL (W5) — NOT STARTED
`res/menu/menu_routing_setting.xml` has 5 items: `add_rule`, `import_predefined_rulesets`,
`import_rulesets_from_clipboard`, `import_rulesets_from_qrcode`, `export_rulesets_to_clipboard`. No
URL item; `RoutingSettingActivity.onOptionsItemSelected` (`:76-78`) has no URL branch; grep for
`import_rulesets_from_url`/`importRulesetsFromUrl` → 0.

### 6f. WebDAV connection test (W6) — NOT STARTED
`handler/WebDavManager.kt` exposes `init`, `uploadFile`, `downloadFile`, `buildRemoteUrl`, `applyAuth`,
`ensureRemoteDirs` — no `checkConnection`/PROPFIND probe (`grep -rni checkConnection java/ res/ → 0`).
`BackupActivity.showWebDavSettingsDialog()` (`:289`) still saves four fields on «Сохранить» with no
verification and a blanket success toast, exactly as `gap-desktop-to-android.md` D3 described.

---

## Adjacent findings (verified in passing, outside the six items)

- **P0-6 is fixed.** The six provider-settings keys are no longer write-only: `SettingsManager`
  (`:387-425`) reads all six and `SubscriptionUpdater` consumes them —
  `isNotifyOnSubscriptionUpdate()` at `:262`, `isUpdateSubscriptionOnLaunch()` at `:117`,
  `isPingOnLaunch()` at `:120`, `isPingOnSubscriptionUpdate()` at `:281`, `applyServerSortOrder()` at
  `:115`/`:279`. The global UA fallback is consumed at the single fetch point,
  `AngConfigManager.kt:891-893` (per-subscription UA → global → operator default).
- **P1-1 is fixed.** Auto-fallback now re-probes before switching: `healthCheckConfirming` +
  `healthRecheckRunnable` exist in `MainActivity` and the `delayResultAction` observer clears the
  confirming state when the tunnel answers.
- **Working tree is dirty.** `MainActivity.kt`, `BaseFragment.kt`, `activity_main.xml` and the two
  warning baselines carry uncommitted changes from another session; `docs/agents/state/` is untracked.
  Nothing verified above depends on those edits, but a reader comparing against `HEAD` will see drift.
- **Not verified:** I did not run a build (read-only mandate). Every claim here is source-level.
