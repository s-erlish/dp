# Gap analysis: desktop to Android

**What the desktop client has that Android does not, what crosses over, what does not, and what it
costs.**

The desktop client is upstream v2rayN plus the Departament customisations. Android is upstream
V2rayNG plus the same customisations. The two upstreams are separate codebases with separate
histories, so the two clients differ in ways that have nothing to do with our design work.
`00-rules.md` 13 makes parity a contract, so every difference has to be a decision on the record
rather than an accident of ancestry.

This document is that record and it is the source for a future porting wave. A row that says PORT
is a work order. A row that says NO or N/A is an argued refusal that a later agent does not have to
re-litigate.

| | Android | Desktop |
|---|---|---|
| Build root | `/home/user/dp/V2rayNG` | `/home/user/v2rayN/v2rayN` |
| UI | `app/src/main/java/com/v2ray/ang/ui/**`, `app/src/main/res/**` | `v2rayN.Desktop/Views/**` |
| Core wrapper | `com/v2ray/ang/core/**`, `handler/**`, `service/**` | `ServiceLib/**` |
| Engine | libv2ray (Xray) only, `core/CoreNativeManager.kt:7-10` | fourteen declared core types, `ServiceLib/Enums/ECoreType.cs:5-19` |

All paths below are relative to those two build roots. Line numbers were read on branch
`claude/app-audit-agents-hyyftk` on 2026-07-26.

---

## 1. Summary

The two clients are far closer than their file counts suggest. Desktop has 50 `.axaml` views to
Android's 73 layouts, and screen-for-screen the destination set already matches: every desktop view
either has an Android counterpart, is already marked DELETE in `11-app-structure.md` 10.2, or is
genuine window chrome.

The real gaps are not screens. They are three things:

1. **Engine breadth.** Desktop declares fourteen core types and eleven protocols; Android runs one
   core and nine protocols. Everything that follows from that (TUIC, AnyTLS, Naive, Clash and
   sing-box subscription formats, per-protocol core selection) is an engine decision, not a UI
   decision.
2. **Measurement.** Desktop runs four kinds of per-server test - TCP latency, real latency through
   the tunnel, UDP reachability, download throughput (`Enums/ESpeedActionType.cs:5-10`) - and
   persists five fields about each server (`Models/Entities/ProfileExItem.cs:4-14`). Android runs
   latency only, and persists one field (`dto/entities/ServerAffiliationInfo.kt:3`). The direction is
   not uniform: Android offers **four** latency methods to desktop's two
   (`enums/PingMethod.kt:12-15` against `Views/PingSettingsPage.axaml:100,119`), and
   `12-settings.md` 6.2 cuts two of Android's four. What Android has no path to at all is throughput
   and UDP.
3. **Settings depth.** Desktop's `OptionSettingWindow` carries 75 named controls across five tabs;
   nine of them have no Android key at all and six more are only partly matched.

Counted, over the 65 capabilities classified in sections 4 to 7:

| Class | Count | Meaning |
|---|---|---|
| PRESENT | 17 | Android already does this, possibly on a different surface |
| PARTIAL | 12 | Android does part of it, or does it without a designed surface |
| ABSENT | 27 | No Android implementation of any kind |
| NOT APPLICABLE | 9 | Argued in section 7, not asserted |

Of the 39 PARTIAL and ABSENT capabilities: **9 port in the next wave** (as 6 work orders, since W1
carries three of them), **7 port later**, and **23 are refused with a reason on the row**. The screen
map in section 3 is separate from this count: it lists surfaces, not capabilities.

---

## 2. Method

### 2.1 What was read

Desktop, in this order:

- every file in `v2rayN.Desktop/Views/` (50 `.axaml` plus code-behind), sized and opened where the
  name did not settle the question;
- both view-model layers: `ServiceLib/ViewModels/` (21 files, the upstream layer) and
  `v2rayN.Desktop/ViewModels/` (7 files, the Departament layer: `Account`, `Buy`, `Devices`, `Home`,
  `PaymentHistory`, `Settings`, `ThemeSetting`);
- `ServiceLib/Services/`, `ServiceLib/Handler/`, `ServiceLib/Manager/`, `ServiceLib/Enums/`,
  `ServiceLib/Models/Entities/`;
- the settings surface named in the task: `SettingsView.axaml` (1 075 ln),
  `OptionSettingWindow.axaml` (1 206 ln), `PingSettingsPage`, `ProviderSettingsPage`, `DnsSubView`,
  `RoutingSubView`, `GeoFilesPage`, `UrlSchemesPage`, `GlobalHotkeySettingWindow`,
  `PerAppProxyPage`, `BackupPage`.

Android:

- `app/src/main/java/com/v2ray/ang/ui/` (48 files plus `ui/adapter/`), `viewmodel/` (8 files),
  `handler/` (9), `core/` (5), `service/` (10), `fmt/` (9), `enums/`, `dto/entities/`, `tv/`,
  `template/`, `util/`;
- `res/layout/` (73 files), `res/xml/pref_settings.xml` (354 ln, 55 `android:key` entries),
  `res/menu/` (10 files), `AndroidManifest.xml`.

Design law: `00-rules.md` (the governing file, sections 13 and 18 in particular),
`11-app-structure.md` sections 10 and 12.3, `12-settings.md` sections 5.0 and 6.

### 2.2 Verdict vocabulary

| Verdict | Meaning |
|---|---|
| **PRESENT** | Android implements the capability. The surface may differ; the capability does not |
| **PARTIAL** | Android implements some of it, or implements it but does not expose it on a designed surface |
| **ABSENT** | No Android implementation |
| **N/A** | The capability cannot exist on Android, argued in section 7 |

Decisions on PARTIAL and ABSENT rows:

| Decision | Meaning |
|---|---|
| **PORT NOW** | Goes into the next wave. Named target surface, named effort |
| **PORT LATER** | Real value, but it waits behind the rebuild waves already planned |
| **NO** | Refused, with the reason on the row |

### 2.3 Effort scale

| | Meaning |
|---|---|
| **S** | One row or one screen. The Android core already supports it. No new persisted field |
| **M** | A sub-page plus wiring through `handler/` or `core/`, or one new persisted field, or a new background task |
| **L** | A new native core or binary in the APK, or a data-model migration. Changes what ships |

An estimate is for implementation against an existing spec, not for writing the spec.

### 2.4 What this document does not decide

It does not re-open anything already decided in `00-rules.md` 18, `11-app-structure.md` 10 and 12.3,
or `12-settings.md` 6. Where one of those files has already ruled, the row cites it and stops. In
particular: the desktop Mihomo surface (`ClashProxiesView`, `ClashConnectionsView`) is cut by
`11-app-structure.md` 10.2 and `12-settings.md` 6.1, so it is not a port candidate and does not
appear in the tables below except as a note.

---

## 3. Surface map: every desktop view

Sorted by the Android answer, not alphabetically. "Deleted" means the desktop view is already on the
delete list in `11-app-structure.md` 11.4, so the parity question is moot.

### 3.1 Desktop views with a live Android counterpart

| Desktop view | Android counterpart | Note |
|---|---|---|
| `HomeView` / `ConnectHeroView` / `HomeAccountChip` | `MainActivity` home tab, `activity_main.xml` | Both REBUILD per `11-app-structure.md` 4.1 |
| `ServerListView` | `MainActivity` servers tab + `MainRecyclerAdapter.kt` | |
| `SubscriptionMetaView` | `layout_subscription_meta_bar.xml` + `HomeMetaPagerAdapter.kt` | Both SPLIT per 10.1/10.2 |
| `SettingsView` | `layout_settings_content.xml` | |
| `AccountView` | `AccountFragment` + `activity_account.xml` | |
| `BuyView` | `BuyTariffActivity` + `activity_buy_tariff.xml` | |
| `DevicesView` | `DeviceManagementActivity` + `activity_devices.xml` | |
| `PaymentHistoryView` | `PaymentHistoryActivity` + `activity_payment_history.xml` | |
| `LoginView` | `LoginActivity` + `activity_login.xml` | Both DELETE, replaced by the Аккаунт gate |
| `AccountSyncView` | none today | PG-9 in `11-app-structure.md` 12.3 already closes this on Android |
| `PerAppProxyPage` | `PerAppProxyActivity` + `AppPickerActivity` | Different matcher: desktop matches process name or `.exe` path (`Views/PerAppProxyPage.axaml.cs:8-18`), Android matches package |
| `DnsSubView` | `MainActivity.editDns()` `:3024` + `editDnsCustom()` `:3052` | Android has no DNS page, only a two-step dialog |
| `PingSettingsPage` | `MainActivity.pickPingMethod()` `:3038` | |
| `ProviderSettingsPage` | `ProviderSettingsActivity` | Desktop's is wired to nothing (`11-app-structure.md` 10.2) and Android's is the richer of the two - see 8.2 |
| `RoutingSubView` / `RoutingRuleSettingWindow` / `RoutingRuleDetailsWindow` | `RoutingSettingActivity` + `RoutingEditActivity` | Model differs, see 6.1 |
| `GeoFilesPage` | `UserAssetActivity` + `UserAssetUrlActivity` | |
| `UrlSchemesPage` | `UrlSchemeListActivity` | |
| `BackupPage` / `BackupAndRestoreView` | `BackupActivity` | Both do local zip and WebDAV, see 7.2 |
| `AboutPage` | `AboutActivity` | |
| `MsgView` | `LogcatActivity` + `LogcatViewModel` | |
| `QrcodeView` | `item_qrcode.xml` + `AngConfigManager.share2QRCode()` `:153` | |
| `AddServerWindow` / `AddServer2Window` | the ten `activity_server_*.xml` editors | Both REBUILD as one |
| `AddGroupServerWindow` | `ServerGroupActivity` + `activity_server_group.xml` | Both create a policy group with a strategy, a subscription and a filter |
| `SubEditWindow` / `SubSettingWindow` | `SubEditActivity` / `SubSettingActivity` | |
| `JsonEditor` | `ServerCustomConfigActivity` | |
| `CheckUpdateView` | `CheckUpdateActivity` + `UpdateCheckerManager.kt:17` | Both exist; Android's is DELETE per PG-1 |
| `OnboardingView` | none | Both DELETE; first run becomes a state of Главная |
| `ThemeSettingView` | the `Оформление` row | Desktop's view is DELETE |
| `ProfilesView` | `MainActivity` list | Desktop's is DELETE, already superseded |
| `MessageBoxDialog` | `MaterialAlertDialogBuilder` + `ThemeOverlay.Departament.Dialog` | |

### 3.2 Desktop views with no Android counterpart

| Desktop view | Class | Section |
|---|---|---|
| `OptionSettingWindow` | PARTIAL - of its 75 controls, nine have no Android key and six more are only partly matched | 5 |
| `FullConfigTemplateWindow` | ABSENT | 6.3 |
| `GlobalHotkeySettingWindow` | N/A | 7.1 |
| `SudoPasswordInputView` | N/A | 7.1 |
| `MainWindow` chrome, `BottomNavBar`, `CompactHomeView`, `CompactServersView` | N/A - window chrome and a compact mode that is DELETE anyway | 7.1 |
| `StatusBarView` | N/A - the desktop status strip; Android's equivalent is the notification | 7.1 |
| `ProfilesSelectWindow` | PRESENT in substance - Android picks profiles inside `ServerGroupActivity` / `ServerProxyChainActivity` | - |
| `ClashProxiesView`, `ClashConnectionsView` | Cut by `11-app-structure.md` 10.2 (D-11) and `12-settings.md` 6.1. Not a port candidate | - |

---

## 4. Engine and protocol gaps

This is the largest and least tractable group. Everything here follows from one fact: Android links
`libv2ray` and nothing else (`core/CoreNativeManager.kt:7-10`, and `app/libs/` contains only
`libv2ray-stub.jar`), while desktop resolves a core per profile and defaults to Xray
(`ServiceLib/Manager/AppManager.cs:735-744`).

| # | Capability | Desktop | Android | Class | Decision |
|---|---|---|---|---|---|
| E1 | Fourteen core types (`v2fly`, `Xray`, `v2fly_v5`, `mihomo`, `hysteria`, `naiveproxy`, `tuic`, `sing_box`, `juicity`, `hysteria2`, `brook`, `overtls`, `shadowquic`, `mieru`), selectable per config type | `Enums/ECoreType.cs:5-19`; the eight per-type dropdowns at `OptionSettingWindow.axaml:1106-1197`; resolution at `Manager/AppManager.cs:735-744` | Xray only | ABSENT | **NO** - see 4.1 |
| E2 | TUIC protocol | `Handler/ConfigHandler.cs:795` `AddTuicServer` | commented out at `enums/EConfigType.kt:14` | ABSENT | **NO** - needs E1 |
| E3 | AnyTLS protocol | `Handler/ConfigHandler.cs:890` `AddAnytlsServer` | none | ABSENT | **NO** - needs E1 |
| E4 | Naive protocol | `Handler/ConfigHandler.cs:918` `AddNaiveServer` | none | ABSENT | **NO** - needs E1 |
| E5 | Hysteria v1 protocol | none: `Enums/EConfigType.cs:3-18` has `Hysteria2` only | `enums/EConfigType.kt:16` `HYSTERIA(900)` | reverse gap, A4 in 8.1 | not counted in the desktop-to-Android totals |
| E6 | Clash / Mihomo YAML subscription import | `Handler/Fmt/ClashFmt.cs:5` `ResolveFull` | none | ABSENT | **NO** - the resulting profile is `ECoreType.mihomo` (`ClashFmt.cs:13`), which needs E1 |
| E7 | sing-box JSON subscription import | `Handler/Fmt/SingboxFmt.cs:5` `ResolveFullArray` | none | ABSENT | **NO** - needs E1 |
| E8 | Shadowsocks SIP008 JSON import | `Handler/ConfigHandler.cs:1749` `AddBatchServers4SsSIP008`, reached from `:1896` | none; `handler/AngConfigManager.kt:356` parses line-by-line URIs only | ABSENT | **PORT LATER**, S. Pure parsing, no core work |
| E9 | WireGuard `.conf` file import | `Handler/ConfigHandler.cs:1776` `AddBatchServers4Wireguard`, reached from `:1902` | none | ABSENT | **PORT LATER**, S. Android already has `fmt/WireguardFmt.kt`, only the `.conf` reader is missing |
| E10 | User-editable full config template per core | `ViewModels/FullConfigTemplateViewModel.cs:10-37` | none. Android's `template/TemplateManager.kt` is the Departament hidden-template feature, an unrelated thing with a similar name | ABSENT | **NO** - `12-settings.md` 5.0 already marks `settings/advanced/template` **desktop** |
| E11 | Subscription conversion service URL | `OptionSettingWindow.axaml:779` `cmbSubConvertUrl` | none | ABSENT | **NO** - see 4.2 |

### 4.1 Why the multi-core gap is refused, not deferred

Adding a second core to Android is not a port, it is a different product. Concretely: sing-box for
Android ships as a separate AAR with its own JNI surface and its own config schema; the app would
then carry two engines, two config builders (`core/CoreConfigManager.kt` would gain a sibling the
size of `ServiceLib/Services/CoreConfig/Singbox/`, nine files on desktop), two statistics readers,
and two sets of failure modes on top of an APK that grows by the size of the second core.

The product does not need it. Departament issues VLESS/Reality and Xray-JSON template profiles
(`Handler/Fmt/XrayJsonTemplateFmt.cs:3-14` documents the contract and says it mirrors Android), all
of which Xray runs. E2, E3, E4, E6 and E7 exist on desktop because upstream v2rayN is a generalist
client for any subscription a user pastes in; that is not what this app is.

If the provider ever issues a protocol Xray cannot run, this becomes a product decision at the
provider level first and an L-sized engine wave second. Until then it stays refused.

### 4.2 Why the subscription-conversion URL is refused

`cmbSubConvertUrl` sends the user's subscription URL to a third-party subconverter host, which then
returns the profile list. That is the whole subscription, including its credentials, handed to a host
neither we nor the user controls. It also contradicts `12-settings.md` 6.1's treatment of
`pref_ip_api_url` ("operator-set, not user-set"). Refused on both grounds.

---

## 5. Settings surface gaps

Compared control by control: desktop `OptionSettingWindow.axaml` (five tabs at `:38`, `:478`, `:865`,
`:958`, `:1094`; 75 named controls) and `SettingsView.axaml` against Android
`res/xml/pref_settings.xml` (55 keys) and `layout_settings_content.xml`.

Most of `OptionSettingWindow` already has an Android key. The rows below are the ones that do not,
or that Android has but does not surface.

| # | Setting | Desktop | Android | Class | Decision |
|---|---|---|---|---|---|
| S1 | Default uTLS fingerprint for all outbounds | `OptionSettingWindow.axaml:218` `cmbdefFingerprint` | per-server only (`res/layout/layout_tls.xml`); no app-wide default | ABSENT | **PORT NOW**, S. `settings/advanced` |
| S2 | Default User-Agent for core outbounds | `OptionSettingWindow.axaml:231` `cmbdefUserAgent` | only the **subscription** User-Agent exists (`util/HttpUtil.kt:52`, `ProviderSettingsActivity` row `:505`), which is a different thing | ABSENT | **PORT LATER**, S. `settings/advanced` |
| S3 | Latency test timeout | `Views/PingSettingsPage.axaml:150-153` `txtTimeout` | no key; `SpeedtestManager.socketConnectTime()` `:218` hard-codes `timeoutMs = 3000` | ABSENT | **PORT NOW**, S. `settings/latency` |
| S4 | Latency test address on a designed surface | `Views/PingSettingsPage.axaml:140-143` `txtPingUrl` | `pref_delay_test_url` exists but only in the hidden `pref_settings.xml` | PARTIAL | **PORT NOW**, S. `settings/latency` per `12-settings.md` 5.6 |
| S5 | FakeIP toggle on a designed surface | `Views/DnsSubView.axaml:140-153` `switchFakeIp` | `pref_fake_dns_enabled` exists but only in hidden `pref_settings.xml` | PARTIAL | **PORT NOW**, S. `settings/dns` per `12-settings.md` 5.4 |
| S6 | Sniffing destination-override selection (which of http/tls/quic to sniff) | `OptionSettingWindow.axaml:111` `clbdestOverride` | `pref_sniffing_enabled` is a single boolean | PARTIAL | **NO** - a three-checkbox refinement of one working toggle. `12-settings.md` 2.8: "a setting that the app can decide correctly is not a setting" |
| S7 | Second local listening port | `OptionSettingWindow.axaml:72` `togSecondLocalPortEnabled` | `pref_append_http_proxy` is the near equivalent (an extra HTTP inbound beside SOCKS) | PARTIAL | **NO** - equivalent behaviour already reachable |
| S8 | Hysteria up/down bandwidth hints | `OptionSettingWindow.axaml:281,286` `txtUpMbps` / `txtDownMbps` | none | ABSENT | **NO** - `12-settings.md` 6.1 already rules that per-protocol transport tuning belongs to the server form, not app settings |
| S9 | Fragment: max split, final fragment | `OptionSettingWindow.axaml:394` `txtFragmentMaxSplit`, `:421` `togenableFinalFragment` | `pref_fragment_length/interval/packets` only | PARTIAL | **PORT LATER**, S. `settings/fragment` (`12-settings.md` 5.5) is the page that would hold them |
| S10 | Bind to a named network interface / `sendThrough` source address | `OptionSettingWindow.axaml:441` `txtbindInterface`, `:461` `txtsendThrough` | none | N/A | N8 in 7.1 |
| S11 | Root certificate provider | `OptionSettingWindow.axaml:642` `cmbRootCertificateProvider` | none | N/A | N9 in 7.1 |
| S12 | Mixed-test concurrency | `OptionSettingWindow.axaml:697` `cmbMixedConcurrencyCount` | `pref_real_ping_concurrency` is the equivalent | PRESENT | - |
| S13 | Geo file source, SRS source, routing-rules source | `OptionSettingWindow.axaml:806,827,848` | `PREF_GEO_FILES_SOURCES` with a picker at `ui/UserAssetActivity.kt:80-83` | PRESENT for geo; SRS is sing-box-only so it needs E1 | - |
| S14 | Core log level | `OptionSettingWindow.axaml:205` `cmbloglevel` | `pref_core_loglevel` | PRESENT | - |
| S15 | Autostart on boot | `Handler/AutoStartupHandler.cs:9`, `OptionSettingWindow.axaml:493` | `receiver/BootReceiver` (`AndroidManifest.xml:281-288`), `pref_is_booted`, settings row `row_boot` | PRESENT | - |
| S16 | UI scale | `SettingsView.axaml:774-797` `RowUiScale` | none | N/A | N5 in 7.1; PG-3 in `11-app-structure.md` 12.3 |
| S17 | Font family / font size | `OptionSettingWindow.axaml:676` `cmbcurrentFontFamily` | none | ABSENT | **NO** - `12-settings.md` 6.1 already cuts this on desktop too: the type ramp owns the face (`00-rules.md` 5.1) |
| S18 | System proxy tab in full | `OptionSettingWindow.axaml:865-956`, `Handler/SysProxy/` | none | N/A | N2 in 7.1 |
| S19 | TUN stack, strict route, ICMP policy, route-exclude list | `OptionSettingWindow.axaml:958-1092` | Android has its own tunnel keys: `pref_vpn_mtu`, `pref_vpn_bypass_lan`, `pref_vpn_interface_address_config_index`, `pref_use_hev_tunnel_v2` | PARTIAL, different implementation | **NO** - two different tunnels. `TProxyService.kt` is hev-socks5-tunnel; sing-box TUN options do not map |

---

## 6. Feature gaps outside settings

### 6.1 Server list, testing and profiles

| # | Capability | Desktop | Android | Class | Decision |
|---|---|---|---|---|---|
| F1 | **Download throughput test per server** | `Services/SpeedtestService.cs:501` `DoSpeedTest`, dispatched at `:60` and `:64`; command at `ViewModels/ProfilesViewModel.cs:89` `SpeedServerCmd` | none. `handler/SpeedtestManager.kt` has `httpPing:63`, `icmpPing:99`, `tcping:191`, `socketConnectTime:218` and no throughput path | ABSENT | **PORT NOW**, M |
| F2 | **UDP reachability test per server** | `Services/SpeedtestService.cs:519` `DoUdpTest`, `:334` `RunUdpTestBatchAsync`; `ProfilesViewModel.cs:88` `UdpTestServerCmd` | none | ABSENT | **PORT LATER**, M |
| F3 | Mixed test (latency and throughput in one pass, concurrent) | `Services/SpeedtestService.cs:414` `RunMixedTestAsync`, reached from `:64`; `Speedtest` reaches the same method at `:60` with concurrency 1; `ProfilesViewModel.cs:84` | none | ABSENT | **NO** - it is F1 with a concurrency argument. Once F1 lands, a separate «смешанная проверка» verb is a second Russian name for one action, which is what `00-rules.md` 9.3 exists to stop |
| F4 | **Per-server exit-IP readout** | `Services/SpeedtestService.cs:594` `UpdateIpInfoFunc`, persisted at `Models/Entities/ProfileExItem.cs:13` `IpInfo` | `SpeedtestManager.getRemoteIPInfo():258` exists but is used only for the **current** connection at `core/CoreServiceManager.kt:403` | PARTIAL | **PORT LATER**, M |
| F5 | Persisted per-server test fields (delay, speed, sort, message, ip) | `Models/Entities/ProfileExItem.cs:4-14`, five fields in SQLite | one field: `dto/entities/ServerAffiliationInfo.kt:3` `testDelayMillis` | PARTIAL | **PORT NOW** as part of F1, S. F1 has nowhere to store its result otherwise |
| F6 | Cumulative per-server traffic (total up/down, today up/down) | `Models/Entities/ServerStatItem.cs:4-17`, fed by `Manager/StatisticsManager.cs:19` `Init` | live only: `core/CoreServiceManager.kt:337` `queryAllOutboundTrafficStats`, read by `handler/NotificationManager.kt:284`, never persisted | PARTIAL | **NO** - see 6.4 |
| F7 | Sort the list by an arbitrary column | `ViewModels/ProfilesViewModel.cs:710` `SortServer(colName)`, `Handler/ConfigHandler.cs:951` `SortServers`; the fifteen sortable columns are `Enums/EServerColName.cs:5-20` and include `DelayVal`, `SpeedVal`, `IpInfo`, `TodayDown` and `TotalUp` | `viewmodel/MainViewModel.kt:551` `sortByTestResults()` only | PARTIAL | **NO** - Серверы is a single-column grouped list (`11-app-structure.md` 4.2), not a data grid. There are no columns to sort by |
| F8 | Auto-generate a policy group for all servers / one per region | `ViewModels/ProfilesViewModel.cs:685` `GenGroupAllServer`, `:697` `GenGroupRegionServer` | manual only: `ui/ServerGroupActivity.kt` + `res/layout/activity_server_group.xml:55,75,95` (strategy, subscription, filter) | PARTIAL | **PORT LATER**, S |
| F9 | Move servers to another provider group | `Handler/ConfigHandler.cs:2133` `MoveToGroup`, `ProfilesViewModel.cs:81` | none | ABSENT | **NO** - servers arrive from a provider subscription and are replaced wholesale on refresh (`AngConfigManager.kt:356`, `!append` branch). A hand-moved server is silently destroyed on the next update |
| F10 | Export a profile as a full client config file / base64 batch / inner URI | `ProfilesViewModel.cs:95-100`, five export commands | three: `AngConfigManager.kt:103` `share2Clipboard`, `:126` `shareNonCustomConfigsToClipboard`, `:153` `share2QRCode` | PARTIAL | **NO** - the three Android has cover the user-facing case; the other two are debugging aids for a generalist client |
| F11 | Per-server test from the item action surface | desktop tests the selection from the toolbar; `ProfilesViewModel.cs:86-92` | `res/layout/sheet_server_actions.xml` offers share QR `:44`, share `:82`, edit `:120`, duplicate `:158`, set default `:196`, delete `:234` - no test | ABSENT | **PORT NOW**, S. It is the natural home for F1 |
| F12 | Duplicate / set default / delete / share per server | `ProfilesViewModel.cs:69,70,67,71` | `sheet_server_actions.xml:158,196,234,44` | PRESENT | - |
| F13 | Drag reorder | `ProfilesViewModel.cs:76-80` (four move commands) plus drag | `MainViewModel.kt:148` `swapServer` | PRESENT | - |
| F14 | Remove duplicates / remove invalid / remove all | `ProfilesViewModel.cs:68,91` | `MainViewModel.kt:481,535,517`, surfaced at `res/menu/menu_main.xml:64,69,74` | PRESENT | - |
| F15 | Real-delay test over **every** server rather than the selection | `ESpeedActionType.FastRealping` (`Enums/ESpeedActionType.cs:10`), bound at `ProfilesViewModel.cs:92`; at `ProfilesViewModel.cs:791-798` it rewrites itself to `Realping` and swaps the selection for the whole ordered list | `viewmodel/MainViewModel.kt:292` `testAllRealPing` does exactly this | PRESENT | - |

### 6.2 Routing and DNS

| # | Capability | Desktop | Android | Class | Decision |
|---|---|---|---|---|---|
| R1 | Two-level routing model: several named rule **sets**, one active | `ViewModels/RoutingSettingViewModel.cs:9` `RoutingItems`, `:24` `RoutingAdvancedSetDefaultCmd`; storage `Handler/ConfigHandler.cs:2297` `SetDefaultRouting` | one flat rule list: `dto/entities/RulesetItem.kt:3-14` is a single rule; storage is one key, `handler/MmkvManager.kt:510,521` | ABSENT | **NO** - `12-settings.md` 5.2 and 5.3 specify one level: a list of rules with `settings/routing/rule/{id}` as the editor. Adding a set layer would contradict the ratified spec |
| R2 | Import rules from a URL | `ViewModels/RoutingRuleSettingViewModel.cs:26` `ImportRulesFromUrlCmd` | clipboard `res/menu/menu_routing_setting.xml:14`, QR `:18`, predefined `:10`, export `:22`; no URL | ABSENT | **PORT NOW**, S. Becomes a fourth entry in the `Импортировать набор` picker of `12-settings.md` 5.2 |
| R3 | Import rules from a local file | `RoutingRuleSettingViewModel.cs:24` `ImportRulesFromFileCmd` | none | ABSENT | **NO** - R2 covers the same need with one fewer permission and one fewer file picker |
| R4 | Move a rule to top / up / down / bottom | `RoutingRuleSettingViewModel.cs:29-32` | drag reorder, `viewmodel/RoutingSettingsViewModel.kt:25` `swap` | PRESENT | - |
| R5 | Per-rule enable switch | `Models/Entities/RulesItem.cs:16` `Enabled` | `dto/entities/RulesetItem.kt:12` `enabled` | PRESENT | - |
| R6 | Domain strategy | `RoutingSettingViewModel.cs:17` | `ui/RoutingSettingActivity.kt:83-98` + `pref_outbound_domain_resolve_method` | PRESENT | - |
| R7 | DNS: bootstrap server, expected direct IPs, parallel query, serve-stale, block binding query, system hosts, common hosts | `ViewModels/DNSSettingViewModel.cs:7-19` | none of the seven; Android has `pref_remote_dns`, `pref_domestic_dns`, `pref_dns_hosts`, `pref_vpn_dns`, `pref_local_dns_enabled`, `pref_fake_dns_enabled` | ABSENT | **NO** - `12-settings.md` 5.4 defines the DNS page and none of the seven is in it. Adding them breaks `12-settings.md` 2.5 outright: max 7 rows per group, max 4 groups per screen, and the page already has three groups |
| R8 | DNS provider presets | `Views/DnsSubView.axaml:105-119` (Default, Cloudflare, Google, AdGuard, Custom) | `MainActivity.editDns():3025` reads `R.array.dns_preset_names` / `dns_preset_values` | PRESENT | - |

### 6.3 Data, updates, security

| # | Capability | Desktop | Android | Class | Decision |
|---|---|---|---|---|---|
| D1 | Local zip backup and restore | `Views/BackupPage.axaml.cs:5-10`, `ViewModels/BackupAndRestoreViewModel.cs` | `ui/BackupActivity.kt:158` `backupViaLocal`, `:190` `restoreViaLocal` | PRESENT | - |
| D2 | WebDAV backup and restore | `Manager/WebDavManager.cs:128` `PutFile`, `:154` `GetRawFile`, `:98` `CheckConnection` | `handler/WebDavManager.kt:46` `uploadFile`, `:89` `downloadFile`; `ui/BackupActivity.kt:194,244` | PRESENT | - |
| D3 | WebDAV connection test before saving | `Manager/WebDavManager.cs:98` `CheckConnection` | none - `ui/BackupActivity.kt:289` `showWebDavSettingsDialog` saves four unlabelled fields with no verification | ABSENT | **PORT NOW**, S. `12-settings.md` 5.12 already specifies the page; a test action is one row on it |
| D4 | Geo file download | `Services/UpdateService.cs:140` `UpdateGeoFileAll` | `viewmodel/UserAssetViewModel.kt:63` `downloadGeoFiles` | PRESENT | - |
| D5 | sing-box SRS ruleset download | `Services/UpdateService.cs:400` `UpdateSrsFileAll` | none | ABSENT | **NO** - SRS is a sing-box artefact and is dead weight without E1, which is refused |
| D6 | Core binary update | `Services/UpdateService.cs:50` `CheckUpdateCore` | none | N/A | N7 in 7.1 |
| D7 | App update check | `Services/UpdateService.cs:10` `CheckUpdateGuiN` | `handler/UpdateCheckerManager.kt:17` `checkForUpdate`, deleted by `11-app-structure.md` 10.1 row 28 | PRESENT, then removed by design | PG-1 |
| D8 | Scheduled background subscription and geo update | `Manager/TaskManager.cs:80` `UpdateTaskRunSubscription`, `:111` `UpdateTaskRunGeo` | `handler/SubscriptionUpdater.kt`, plus provider rows `row_auto_update` / `row_interval` (`res/layout/activity_provider_settings.xml:43,111`) | PRESENT | - |
| D9 | TLS certificate fetch with CA pinning, for filling a server form | `Manager/CertPemManager.cs:9` `CertPemManager`, method doc at `:27-30` | none | ABSENT | **NO** - it fills the pinned-certificate field of a hand-entered server. Departament servers come from the provider, so the field has no consumer on Android |

### 6.4 Why per-server cumulative traffic is refused (F6)

Desktop persists total and daily up/down per server (`Models/Entities/ServerStatItem.cs:4-17`) because a v2rayN user
curates a list of servers from many sources and wants to know which one he has been using. In this
product the number that matters is the **subscription's** traffic, and that comes from the backend
and is already on the Аккаунт subscription card (`11-app-structure.md` 4.3). A second, local,
per-server counter would be a different number for the same-sounding thing, on a screen next to the
authoritative one. `00-rules.md` 6.2 ("a colour never means two things") is about colour, but the
principle is the same one and 2.4.4's decoration test kills it outright: point at the number and say
what it communicates that the account card does not.

---

## 7. Not applicable, argued

`00-rules.md` 13 allows platforms to differ on "any platform capability the other does not have".
Each row below is claimed under that clause, and each is argued rather than asserted.

### 7.1 The nine genuine cases

| # | Capability | Desktop implementation | Why Android cannot have it |
|---|---|---|---|
| N1 | **Global hotkeys** | `Views/GlobalHotkeySettingWindow.axaml`, `Enums/EGlobalHotkey.cs:3-10` (five bindable actions), `ViewModels/GlobalHotkeySettingViewModel.cs` | Android has no system-wide keyboard grab for a non-foreground app. The nearest equivalents already exist and are Android-only: the QS tile (`AndroidManifest.xml:291`), the launcher shortcuts (`:69-70`, `res/xml/shortcuts.xml`) and the home-screen widget (`:268-280`). Already PG-2 |
| N2 | **System proxy modes and PAC** | `Handler/SysProxy/SysProxyHandler.cs` with `ProxySettingWindows/Linux/OSX.cs`, `Manager/PacManager.cs`, `Enums/ESysProxyType.cs`, UI at `OptionSettingWindow.axaml:865-956` | There is no OS-wide HTTP proxy an app may set on Android. The platform's answer is `VpnService`, which the app already uses, and per-app selection, which the app already has (`PerAppProxyActivity`). `12-settings.md` 6.1 additionally cuts `SystemProxyItem.*` on desktop |
| N3 | **Sudo password prompt** | `Views/SudoPasswordInputView.axaml`, `Manager/CoreAdminManager.cs` | Raised on Linux when TUN needs privileges. Android grants the tunnel through the `VpnService` consent dialog, which the OS owns and the app cannot restyle or replace. Already PG-7 |
| N4 | **Window chrome, tray, minimise-to-tray, compact layout** | `Views/MainWindow.axaml`, `BottomNavBar.axaml`, `CompactHomeView.axaml`, `OptionSettingWindow.axaml:566` `togAutoHideStartup`, `:580` `togHide2TrayWhenClose`, `:602` `togMacOSShowInDock` | Android has no window, no tray and no dock. The app's background presence is the foreground-service notification (`handler/NotificationManager.kt`), which is a different contract with different rules |
| N5 | **Per-window DPI / UI scale** | `SettingsView.axaml:774-797`, `UiScaleState` | Android scales through the system font-scale and display-size settings, which the app must survive rather than override. `00-rules.md` 14.5 makes surviving 200% mandatory; adding an in-app second scale would fight it. Already PG-3 |
| N6 | **Registry / launchd / systemd autostart plumbing** | `Handler/AutoStartupHandler.cs:9-42` (three OS branches) | Android's equivalent is `BOOT_COMPLETED` and it already exists (`AndroidManifest.xml:281-288`). The capability is PRESENT; only the plumbing is inapplicable |
| N7 | **Core binary download and replacement** | `Services/UpdateService.cs:50` `CheckUpdateCore` | The core is compiled into the APK. Downloading and executing a replacement binary is not permitted for a Play-distributed app and is a poor idea for a sideloaded one |
| N8 | **Bind to a named interface, `sendThrough` source address** | `OptionSettingWindow.axaml:441,461` | Inside `VpnService` the app owns exactly one virtual interface and the OS chooses the underlying transport. Setting a source address or binding a physical interface is not available to an unprivileged Android app |
| N9 | **Root certificate provider selection** | `OptionSettingWindow.axaml:642` `cmbRootCertificateProvider` | Android's trust store is the OS trust store; an app selects between the system store and its own pinned set, not between "provider" implementations the way .NET on Windows does |

### 7.2 Rejected as not-applicable

Two things that look like N/A cases and are not, so nobody files them as such later:

- **Per-app proxy.** It exists on **both** platforms - `Views/PerAppProxyPage.axaml.cs:8-18` on
  desktop (process name or `.exe` path, injected as sing-box `process_name` matchers) and
  `ui/PerAppProxyActivity.kt` on Android (package name, applied through `VpnService.Builder`). PG-4
  in `11-app-structure.md` 12.3 lists it as Android-only, which is true of the **OS** mechanism but
  not of the feature. Not a gap in either direction.
- **Backup.** Both platforms have local zip and WebDAV (D1, D2). Desktop splits it across two views
  and Android across one activity plus a four-field dialog; both are REBUILD targets, not gaps.

---

## 8. The reverse direction: Android features desktop lacks

`00-rules.md` 13 is symmetric, so this half is not optional. Where a row is already logged in
`11-app-structure.md` 12.3 it says so and stops.

### 8.1 Real gaps in the desktop client

| # | Capability | Android | Desktop | Decision |
|---|---|---|---|---|
| A1 | **Automatic fallback to another server on tunnel failure** | `ui/MainActivity.kt:626` and `:2078` gate on `PREF_AUTO_FALLBACK` (`AppConfig.kt:57`, default **true**); the switch lands via `viewmodel/MainViewModel.kt:390` `fastConnect` | no equivalent - `grep -rn "AutoFallback" ServiceLib/ v2rayN.Desktop/` returns nothing | **PORT to desktop NOW**, M. **Already ordered:** `12-settings.md` 5.9 group «Ядро» row f declares `Переключать сервер при сбое`, helper `Если сервер не отвечает после подключения`, default вкл, platform **both**, with the desktop binding marked **NEW**. It is the single most user-visible behaviour difference and it has its own design record (`docs/review-04-auto-fallback.md`, `docs/impl-fix-autofallback.md`) |
| A2 | **Four ping methods with a designed picker** | `enums/PingMethod.kt:12-15`: TCP, HTTP, ICMP, proxied real delay | two: `Views/PingSettingsPage.axaml:100,119` real and TCP only | **NO** - `12-settings.md` 6.2 already cuts HTTP and ICMP **on Android**. Desktop is where the product is heading; Android is the one that changes |
| A3 | **Provider controls: notify on update, update on launch, ping on launch, ping on update, HWID toggle, list sort order** | `res/layout/activity_provider_settings.xml:174,260,313,366,438,590` | `Views/ProviderSettingsPage.axaml` has auto-update `:70`, interval `:89`, HWID readout `:104`, User-Agent `:125`, and nothing else - and it is referenced by nothing (`11-app-structure.md` 10.2 verdict WIRE) | **PORT to desktop NOW**, M. `12-settings.md` 5.6 has already ordered part of it: `Проверять при запуске` and `Проверять после обновления подписки` bind to `SpeedTestItem.PingOnLaunch` / `PingOnUpdate`, both marked **NEW** on the desktop side. The remaining four rows and the page's own wiring are the rest |
| A4 | **Hysteria v1 profiles** | `enums/EConfigType.kt:16` `HYSTERIA(900)` | `Enums/EConfigType.cs` has `Hysteria2` only | **NO** - Hysteria v1 is superseded; nothing in the provider issues it. Logged so it is not mistaken for a desktop defect |
| A5 | **In-app memory reading for the core process** | `util/MemoryStatsManager.kt`, gated by `pref_show_memory` | none | **NO** - `12-settings.md` 6.1 deletes the memory card and its toggle on Android. Do not port a feature that is being removed |
| A6 | **`subscription-userinfo` header parsing (traffic and expiry straight from the provider URL)** | `util/SubscriptionUserInfo.kt`, `util/HttpUtil.kt:261-275` `getUrlContentWithUserAgentEx`, which reads the header alongside the body | no header path. `Views/SubscriptionMetaView.axaml:227-249` renders traffic and expiry, but the values come from the Departament backend account response (`ViewModels/AccountViewModel.cs:2232` reads `raw?.TrafficUsed` / `raw?.UserTraffic?.UsedTrafficBytes`), not from the subscription response | **PORT to desktop LATER**, S. For a Departament provider the two paths agree, so this only bites on a third-party provider URL, where desktop shows nothing and Android shows the real figures |

### 8.2 Android-only by platform, already logged

`11-app-structure.md` 12.3 covers these; repeated here only so this document is self-contained.

| Capability | Android | Log |
|---|---|---|
| Always-on VPN handoff | `layout_settings_content.xml:483` `row_always_on` | PG-4 |
| Transfer the subscription to a TV | `tv/TvSendActivity.kt`, `tv/TvReceiveActivity.kt`, `tv/TvPairingProtocol.kt`, settings rows `:1236,1290` | PG-5 |
| Quick-settings tile, home-screen widget, launcher shortcuts | `AndroidManifest.xml:291`, `:268-280`, `:69-70` | PG-6 |
| Tasker integration | `ui/TaskerActivity.kt`, `AndroidManifest.xml:315-323` | not previously logged. Proposed as **PG-14** for `11-app-structure.md` 12.3: Android-only, no desktop equivalent exists or is wanted |
| Camera QR scanning | `ui/ScannerActivity.kt`, `ui/ScScannerActivity.kt` | not a gap. Desktop reads a QR from a screen region (`ViewModels/MainWindowViewModel.cs:548` `ScanScreenInteraction`, `Views/MainWindow.axaml.cs:1962` `ScanScreenTaskAsync`) or from an image file (`MainWindowViewModel.cs:564` `ScanImageResult`); different input device, same capability |
| Haptics | `00-rules.md` 8.10 | PG-8 |

---

## 9. Work orders

The PORT rows, collected. Each is a task a later agent can pick up without re-reading this document
from the top.

### 9.1 Port now

| # | Work order | Reads | Android home | Core work | Effort |
|---|---|---|---|---|---|
| W1 | **Throughput test per server** (F1 + F5 + F11) | `ServiceLib/Services/SpeedtestService.cs:501` `DoSpeedTest`, `:414` `RunMixedTestAsync`, `ServiceLib/Services/DownloadService.cs` | Серверы item sheet (`sheet_server_actions.xml`, gains a row) plus a batch action; the result renders on the server row beside the latency figure | **Yes.** `handler/SpeedtestManager.kt` has no throughput path. The mechanism exists next door: `service/RealPingWorkerService.kt:81` already builds a throwaway core from one profile (`core/CoreConfigManager.kt:58` `getV2rayConfig4Speedtest`), so the new code downloads a fixed-size body through that core's local SOCKS port and divides. Also needs a persisted speed field beside `dto/entities/ServerAffiliationInfo.kt:3` | **M** |
| W2 | **Latency test timeout and test address on the designed surface** (S3 + S4) | `Views/PingSettingsPage.axaml:140-153` | `settings/latency`. Already fully specified: `12-settings.md` 5.6 group «Проверка» declares `Адрес проверки` bound to `PREF_DELAY_TEST_URL`, and `Тайм-аут` bound to `PREF_PING_TIMEOUT` marked **NEW**, default `5 с`, picker `3 с / 5 с / 10 с / 15 с` | No. `pref_delay_test_url` exists; the new timeout key replaces the hard-coded `timeoutMs = 3000` at `handler/SpeedtestManager.kt:218` | **S** |
| W3 | **FakeIP toggle on the DNS page** (S5) | `Views/DnsSubView.axaml:140-153` | `settings/dns`. Already fully specified: `12-settings.md` 5.4 group «Дополнительно» declares it as an A3 row, label `FakeIP`, helper `Ускоряет соединение, отвечая на запросы локально`, default выкл, bound to `PREF_FAKE_DNS_ENABLED` | No. `pref_fake_dns_enabled` already exists and is read by the config builder | **S** |
| W4 | **Default uTLS fingerprint** (S1) | `OptionSettingWindow.axaml:218` | `settings/advanced` (`12-settings.md` 5.9) | Light. A new key, applied in `core/CoreOutboundBuilder.kt` where the per-server fingerprint is empty | **S** |
| W5 | **Import routing rules from a URL** (R2) | `ViewModels/RoutingRuleSettingViewModel.cs:26` | `settings/routing`, as a fourth entry in the `Импортировать набор` picker of `12-settings.md` 5.2 | No. `util/HttpUtil.kt` fetches; `ui/RoutingSettingActivity.kt:122` `importFromClipboard` already parses the same payload | **S** |
| W6 | **WebDAV connection test** (D3) | `Manager/WebDavManager.cs:98` `CheckConnection` | `settings/data/webdav`. Already fully specified: `12-settings.md` 5.12 declares an A5 row `Проверить подключение` that "shows loading, then `Подключение работает` or the error under the first field" | No. `handler/WebDavManager.kt` can already issue the request; it needs a HEAD/PROPFIND probe and a result state | **S** |

**Copy.** W2, W3 and W6 already carry ratified strings in `12-settings.md` 5.6, 5.4 and 5.12; those
are used verbatim and are not re-invented here. W1, W4 and W5 have no string yet. Proposed below,
obeying `00-rules.md` 9 (Russian, sentence case, no final period on a label, hyphens only, `…` as
one character):

| Control | Label | Helper |
|---|---|---|
| W1 batch action | `Проверить скорость` | - |
| W1 item-sheet row | `Проверить скорость` | - |
| W1 in-flight state on the row | `Проверяем…` | - |
| W1 result on the row | `24,8 Мбит/с` | - |
| W1 failure on the row | `Не удалось измерить` | - |
| W4 row | `Отпечаток TLS` | `Применяется к серверам, у которых он не задан` |
| W5 picker entry | `По ссылке` | - |
| W5 field | `Ссылка на набор правил` | - |
| W5 failure | `Не удалось загрузить правила. Проверьте ссылку и повторите.` | - |

Notes. W1's unit follows `00-rules.md` 9.2, which fixes speeds as `24,8 Мбит/с` with a comma decimal
and a non-breaking space, and 5.5, which puts the figure in the Numeric role with `zero` on because
it is a technical figure and not currency. W1's failure string avoids «ошибка» because 9.4's formula
wants the state, not the category. W5's failure string carries cause and fix per 9.4 and ships with
a `Повторить` action per 9.4's closing rule. W4's row is a new addition to `settings/advanced`, whose
«Ядро» group in `12-settings.md` 5.9 holds six rows a to f of which row a is desktop-only. The
fingerprint row therefore makes six on Android and seven on desktop, which is the 2.5 ceiling
exactly: whatever is added to that group after this one forces a split.

### 9.2 Port later

| # | Work order | Effort | Why it waits |
|---|---|---|---|
| W7 | UDP reachability test (F2) | M | Narrow audience. It answers "will voice and games work", which matters to a minority; W1 answers a question everyone has |
| W8 | Per-server exit-IP readout (F4) | M | Needs W1's persisted-field work first, then one more field and one more row of copy |
| W9 | Auto-generate policy groups, all and by region (F8) | S | Convenience on top of `ServerGroupActivity`, which already builds the same object by hand |
| W10 | Default outbound User-Agent (S2) | S | Only matters against servers that filter on it, which the provider's do not today |
| W11 | Fragment max-split and final-fragment (S9) | S | `settings/fragment` exists in the spec (`12-settings.md` 5.5) but declares only three fields - `Длина`, `Интервал`, `Пакеты`. These would be a fourth and fifth, so the spec has to be amended first, and 5.5's own closing line («Значения по умолчанию подходят большинству сетей») argues against widening it |
| W12 | SIP008 subscription import (E8) | S | Pure parsing, no core work. Waits because no Departament provider emits SIP008 |
| W13 | WireGuard `.conf` file import (E9) | S | `fmt/WireguardFmt.kt` already exists; only the file reader is missing |

### 9.3 Refused, with the reason

| # | Capability | Reason, in one line |
|---|---|---|
| E1-E4, E6, E7 | Multi-core, TUIC, AnyTLS, Naive, Clash and sing-box import | A second engine in the APK for protocols the provider does not issue. Section 4.1 |
| E10 | User-editable full config template | Already ruled desktop-only by `12-settings.md` 5.0 |
| E11 | Subscription conversion URL | Hands the subscription to a third-party host. Section 4.2 |
| F3 | Mixed test | A second name for W1 |
| F6 | Per-server cumulative traffic | Competes with the authoritative subscription figure. Section 6.4 |
| F7 | Sort by column | Серверы has no columns |
| F9 | Move server between groups | Destroyed by the next provider refresh |
| F10 | Two extra export formats | Debugging aids for a generalist client |
| R1 | Multiple routing sets | Contradicts `12-settings.md` 5.2/5.3, which specify one level |
| R3 | Import rules from a file | W5 covers the need with fewer permissions |
| R7 | Seven advanced DNS knobs | Not in the ratified DNS page; expert surface on a consumer screen |
| S6 | Sniffing destination-override selection | A three-checkbox refinement of one working toggle |
| S7 | Second local port | `pref_append_http_proxy` is the equivalent |
| S8 | Hysteria bandwidth hints | Per-protocol tuning belongs to the server form (`12-settings.md` 6.1) |
| S17 | Font family and size | The type ramp owns the face (`00-rules.md` 5.1) |
| S19 | sing-box TUN options | A different tunnel implementation; the options do not map |
| D5 | sing-box SRS ruleset download | Dead weight without E1 |
| D9 | Certificate fetch with pinning | Fills a field that has no consumer on Android |

Twenty-three capabilities, matching the count in section 1.

---

## 10. Open points

Items 1, 2 and 4 could not be verified from the source and are stated as such rather than guessed.
Item 3 was verified and is recorded because it belongs to somebody else's pass.

1. **Whether desktop `Speedtest` and `Mixedtest` differ in more than concurrency.** Both dispatch to
   `RunMixedTestAsync` at `Services/SpeedtestService.cs:60` and `:64`, with `concurrencyCount` 1
   versus `_config.SpeedTestItem.MixedConcurrencyCount` and `blSpeedTest` true in both. That reading
   is what F3's refusal rests on; if the two differ deeper in the call chain, F3 should be
   re-examined.
2. **Whether the real `libv2ray` artefact is Xray-only.** `app/libs/` contains only
   `libv2ray-stub.jar` on this checkout, so the engine claim in section 4 rests on
   `core/CoreNativeManager.kt:7-10` (which imports `libv2ray` and nothing else), on
   `enums/EConfigType.kt` and on the absence of any sing-box or mihomo symbol anywhere in
   `app/src/main/java/`. The real AAR was not present to inspect.
3. **`ProviderSettingsPage.axaml:109` carries a raw em-dash character in its `Text=` attribute** as the HWID
   placeholder. That is a defect against `00-rules.md` 1.4.11 and 9.2, and it is outside this
   document's scope to fix, but it is recorded here because the 1.5 desktop dash grep only scans
   `Common/L.*.cs` and would never find it. Whoever runs the desktop copy pass should widen that
   grep to `Views/`.
4. **Effort estimates are unvalidated.** Nothing in this document was built. S/M/L is a reading of
   how much code the equivalent occupies on desktop plus what Android is missing underneath it, and
   it should be re-checked by whoever picks up the work order.
