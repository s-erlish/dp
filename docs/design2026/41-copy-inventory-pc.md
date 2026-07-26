# 41 — Copy inventory, PC client

**Scope.** Every user-facing string in the Departament desktop client (`/home/user/v2rayN`,
project `v2rayN/v2rayN.Desktop`, Avalonia). Three layers were read in full: the Departament copy
table `v2rayN/v2rayN.Desktop/Common/L.*.cs`, the upstream resource tables
`v2rayN/ServiceLib/Resx/ResUI.*.resx`, and every literal in the 53 `.axaml` files and their
code-behind. Companion document to `01-inventory-android.md` / `02-inventory-pc.md` and to
`/home/user/dp/docs/agents/audit-android-copy.md`.

**Binding law.** `00-rules.md` §9 (9.1 voice, 9.2 form, 9.3 terminology lock, 9.4 errors,
9.5 empty states, 9.6 offline, 9.7 enforcement), §13 parity contract, §0.4 owner requests.
The owner's requirement, verbatim: *«главное, чтобы дизайн весь был под программу и все было чётко
выверено по тексту что на пк и что на телефонной версии, главное чтобы был единый красивый стиль и
всё выглядело правильно»*. The operative half of that for this document is **выверено по тексту**:
the same concept must be worded **identically** on both clients.

**This wave writes documents only.** No source file was modified and no git command was run.

---

## 1. The headline numbers

| Layer | Where | Keys / strings | Russian coverage | Reachable in the shipped UI |
|---|---|---|---|---|
| Departament table `L` | `v2rayN.Desktop/Common/L.*.cs` (7 partials) | **403 keys** (RU + EN each) + 2 plural sets = **405 entries** | 405 / 405 | **391** (14 dead, §5.11) |
| Upstream `ResUI` | `ServiceLib/Resx/ResUI.resx` + `.ru.resx` | **570 keys** | 570 / 570, none empty | **~8** (§4.2) |
| Raw literals | `.axaml` attributes + code-behind | **75** in AXAML, **12** Cyrillic in C# | n/a | **35** in reachable screens |

Binding counts: 299 `{loc:T …}` bindings in AXAML, 211 `L.T/L.F/L.Plural` calls in C#,
480 `{x:Static resx:ResUI.…}` bindings in AXAML, 321 `ResUI.*` call sites in `ServiceLib`.

**The single most important structural fact:** the 480 AXAML `ResUI` bindings and 321 `ServiceLib`
`ResUI` call sites are almost entirely **dead copy**. See §2.

---

## 2. Three copy layers, and only one of them is alive

### 2.1 The `L` table — the live Departament layer

`L` is a singleton string table with `(Russian, English)` per key and live switching
(`L.Instance.SetLanguage`), consumed as `{loc:T Key}` in AXAML and `L.T/L.F/L.Plural` in code.
It is split into seven owner-scoped partials:

| File | Prefixes | Keys |
|---|---|---|
| `L.Common.cs` | `Common_*` | 40 (+2 plural sets) |
| `L.Home.cs` | `Home_*`, `Onboarding_*` | 14 |
| `L.Servers.cs` | `Servers_*`, `Sub_*` | 17 |
| `L.Settings.cs` | `Settings_*`, `Dns_*`, `Routing_*`, `PerApp_*`, `Ping_*`, `Geo_*`, `About_*`, `Backup_*`, `UrlSchemes_*`, `Provider_*` | 134 |
| `L.Account.cs` | `Account_*`, `Login_*`, `Onboarding_*` | 139 |
| `L.Buy.cs` | `Buy_*`, `Devices_*`, `History_*` | 45 |
| `L.Shell.cs` | `Tray_*`, `Nav_*`, `Status_*` | 14 |

This layer is in good shape. Zero missing Russian, zero em/en-dashes in shipped values, zero
three-dot ellipses, zero ALL-CAPS Cyrillic, zero terminology-lock violations. The defects that
remain are listed in §5 and are mostly **omissions** (error strings that stop at "what happened")
and **cross-platform divergence** (§6), not sloppiness.

> Note on `00-rules.md` §9.7: the recorded baseline says *"44 hits on desktop"* for em/en-dashes in
> `Common/L.*.cs`. Re-running that scan today returns **0**. The baseline is stale — it counted the
> `─` box-drawing characters in the section comments, not the shipped values. The live dash debt on
> PC is 6 AXAML literals and 16 `ResUI.ru` values (§5.4).

### 2.2 The `ResUI` table — upstream copy, essentially unreachable

`ResUI` is 2dust/v2rayN's resource table: 570 keys × 8 languages, Russian complete. It is bound
from 480 places in AXAML — but **every one of those bindings lives in an upstream window the
Departament shell never opens**:

| View | `ResUI` keys used | Opened from |
|---|---|---|
| `OptionSettingWindow` | 88 | `SimpleViewLocator` only — no live call site |
| `AddServerWindow` | 70 | `SimpleViewLocator` only |
| `ProfilesView` | 50 | `SimpleViewLocator` only |
| `AddGroupServerWindow` | 25 (+7 code-behind) | `SimpleViewLocator` only |
| `RoutingRuleSettingWindow` | 24 | `SimpleViewLocator` only |
| `SubEditWindow` | 22 | `SimpleViewLocator` only |
| `RoutingRuleDetailsWindow` | 16 | `SimpleViewLocator` only |
| `AddServer2Window` | 14 | `SimpleViewLocator` only |
| `ClashProxiesView` | 13 | `SimpleViewLocator` only |
| `ProfilesSelectWindow`, `SubSettingWindow` | 12 each | `SimpleViewLocator` only |
| `BackupAndRestoreView`, `FullConfigTemplateWindow`, `GlobalHotkeySettingWindow` | 11 each | `SimpleViewLocator` only |
| `ClashConnectionsView` | 10 | `SimpleViewLocator` only |
| `MsgView` | 7 | `SimpleViewLocator` only |
| `JsonEditor` | 4 | inside `FullConfigTemplateWindow` |
| `ThemeSettingView` | 3 | `SimpleViewLocator` only; not placed in any layout |
| `CheckUpdateView` | 3 | `SimpleViewLocator` only |
| `StatusBarView` | 7 | hosted, but `Width="0"` / hidden (`MainWindow.axaml:641`) |

The shell (`MainWindow.axaml.cs`) constructs exactly three tabs (`HomeView`, `SettingsView`,
`AccountView`) and pushes exactly these sub-pages: `BuyView`, `DevicesView`, `PaymentHistoryView`,
`PerAppProxyPage`, `DnsSubView`, `PingSettingsPage`, `RoutingSubView`, `GeoFilesPage`, `AboutPage`,
`BackupPage`, `UrlSchemesPage`. None of the upstream windows above is on that list, and the only
non-design-mode `new *ViewModel()` calls in the whole desktop project are `MainWindowViewModel`,
`HomeViewModel`, `AccountViewModel`, `SettingsViewModel`, `BuyViewModel`, `DevicesViewModel`,
`PaymentHistoryViewModel`, `RoutingSettingViewModel`, `ThemeSettingViewModel`.

**Consequence for this inventory:** ~346 `ResUI` keys are wired into the desktop project and are
still *not user-facing*. They are excluded from the defect counts below and listed as dead copy.
They matter for one reason only: if any of those windows is ever re-hooked, 346 strings written in
upstream's voice (`Операция не удалась, проверьте и повторите попытку`, `Недопустимая конфигурация`,
`Группа {0} имеет циклическую зависимость на дочерний узел {1}`) land in the product at once, in
flat violation of §9.1-9.4.

### 2.3 The dropped-notice pipeline — 321 strings that reach nobody

`ServiceLib` reports every operation outcome through `NoticeManager`:

```
NoticeManager.Enqueue(msg)      → AppEvents.SendSnackMsgRequested
NoticeManager.SendMessage(msg)  → AppEvents.SendMsgViewRequested
```

In the desktop client the chain terminates:

1. `MainWindow.axaml.cs:330` subscribes to `SendSnackMsgRequested` and calls `DelegateSnackMsg`.
2. `DelegateSnackMsg` (`MainWindow.axaml.cs:1843`) deliberately does **not** show the toast — the
   owner banned bottom pills — and instead forwards to `NoticeManager.SendMessage(content)`.
3. `SendMessage` publishes `SendMsgViewRequested`. The **only** subscriber to that channel is
   `MsgViewModel` (`ServiceLib/ViewModels/MsgViewModel.cs:33`), and `MsgViewModel` is instantiated
   **only in `DesignData.cs`**. `MsgView` is not hosted in any layout.

So every string routed through `NoticeManager` is silently discarded at runtime. That covers all
321 `ResUI` call sites in `ServiceLib` **and** ~40 Departament `L` strings the account/devices code
publishes directly to `SendSnackMsgRequested`, including:

`Common_Copied`, `Common_SomethingWrong`, `Common_CouldntOpenPayment`,
`Common_CompletePaymentInBrowser`, `Account_AmountGtZero`, `Account_RenewDone`,
`Account_DevicesAdded`, `Account_UpgradeDone`, `Account_LinkDone`, `Account_EmailSent`,
`Buy_NoPaymentMethods`, `Devices_Unlinked`, `Devices_UnlinkFailed`, `Login_EmailInvalid`.

The comment at `MainWindow.axaml.cs:1836-1839` claims the feedback is *"routed to the inline message
panel"* and that this fixes the "adding a provider does nothing, with no explanation" complaint. It
does not — the panel does not exist in the shell. **Copying a referral code, unlinking a device,
renewing a subscription, adding devices, upgrading a plan and linking an email all complete with
zero visible confirmation, and every failure in those flows is invisible too.** This is the single
largest copy defect on PC: correct strings, written to law, that the user never sees.

`AccountView.axaml.cs:147` is the clearest case — clicking "Скопировать код" publishes
`Common_Copied` («Скопировано») and nothing appears.

### 2.4 Raw literals

75 literal text attributes in AXAML and 12 Cyrillic string literals in C#. Full table in §8.
Of the 75, 40 sit in the unreachable upstream windows. The 35 in reachable screens split into brand
tokens (`departament`, `departament.site`), locale-neutral technical tokens (`DNS`, `TUN`, `IPv6`,
`FakeIP`, `TCP`, `User-Agent`, `Cloudflare`, `Google`, `AdGuard`, `geoip.dat`, `geosite.dat`) and
**4 genuine defects** (§5.3).

---

## 3. Defect counts at a glance

| # | Category | Count | Severity |
|---|---|---|---|
| F1 | Missing Russian | **0** in `L`, **0** in `ResUI.ru` | — |
| F2 | Latin-only Russian shown to a Russian user | **9** reachable (+21 in `ResUI.ru`, unreachable) | P1 ×3, P2 ×6 |
| F3 | Literals that bypass the resource layer | **4** reachable defects (+ 3 hidden, + 50 unreachable, + 6 design-time) | P1 ×2, P2 ×2 |
| F4 | ALL-CAPS | **3** reachable (all Latin; 0 Cyrillic) | P2 |
| F5 | Terminology-lock (9.3) violations | **0** in `L`; **25** in `ResUI.ru` (unreachable); **1** cross-platform break | P1 ×1 |
| F6 | Error messages failing the 9.4 formula | **17** of 36 error-family strings | P1 ×9, P2 ×8 |
| F7 | Duplicates (one concept, two or more strings) | **14** groups | P2 |
| F8 | Placeholder standing in for a field label | **5** fields (§7) | P1 ×3, P2 ×2 |
| F9 | Empty states that break 9.5 | **4** screens | P1 ×2, P2 ×2 |
| F10 | Offline state (9.6) | **not implemented at all** — 0 strings exist | P1 |
| F11 | Dead copy (defined, never rendered) | **14** `L` keys + ~346 `ResUI` keys + ~361 dropped notices | P1 (the notices) |
| F12 | Cross-platform wording divergence | **31** concepts worded differently on PC vs Android | P1 ×12, P2 ×19 |

---

## 4. Reachability

### 4.1 What the user can actually reach on PC

Tabs: **Главная** (`HomeView` / `CompactHomeView` / `ConnectHeroView` / `ServerListView` /
`CompactServersView` / `SubscriptionMetaView` / `HomeAccountChip`), **Настройки** (`SettingsView`),
**Аккаунт** (`AccountView`).
Sub-pages pushed onto the shell stack: `BuyView`, `DevicesView`, `PaymentHistoryView`,
`PerAppProxyPage`, `DnsSubView`, `PingSettingsPage`, `RoutingSubView`, `GeoFilesPage`, `AboutPage`,
`BackupPage`, `UrlSchemesPage`.
Pre-shell: `LoginView`, `OnboardingView`, `AccountSyncView`.
Dialogs: `MessageBoxDialog` (yes/no), `QrcodeView`, `SudoPasswordInputView` (Linux, TUN enable).
Native: the tray menu in `App.axaml`.
`ProviderSettingsPage` exists and is fully written but **is not wired to any settings row** —
`SettingsView.axaml.cs:43-50` opens eight pages and `ProviderSettingsPage` is not among them. Its
9 `Provider_*` strings are therefore unreachable (they do not appear in the dead-key list because
the page file itself references them).

### 4.2 The `ResUI` strings a user can still hit

Only eight, and all through paths the Departament UI did not rewrite:

| Key | Русский | Where it surfaces |
|---|---|---|
| `RemoveServer` | Вы уверены, что хотите удалить сервер? | Delete a server from the server list (`ServerListView.axaml.cs:102` → `UI.ShowYesNo`) |
| `TbConfirm` | Подтвердить | `MessageBoxDialog.axaml:69`, `SudoPasswordInputView.axaml:24` |
| `TbCancel` | Отмена | `MessageBoxDialog.axaml:62`, `SudoPasswordInputView.axaml:30` |
| `TbSettingsLinuxSudoPassword` | Пароль sudo системы | Linux TUN enable (`StatusBarView.axaml.cs:202`) |
| `TbSettingsLinuxSudoPasswordTip` | Пароль sudo будет проверен в терминале. Если из-за ошибки проверки приложение начнёт работать некорректно, перезапустите его. Пароль не сохраняется — его нужно вводить после каждого перезапуска. | same |
| `SudoIncorrectPasswordTip` | Неверный пароль, попробуйте ещё раз. | same |
| `TbConfigTypePolicyGroup` | Группа политик | Appended to a group server's name by `ConfigHandler.cs:1385`/`:1453` — rendered in the server list |
| `AllGroupServers` | Все | Provider filter head (`ProfilesViewModel.cs:428`) |

`MessageBoxDialog`'s window `Title` is `Global.AppName`, not a localized string.

All eight bypass the `L` table, so **they do not follow the live language switch**. `L.SetLanguage`
sets `Thread.CurrentUICulture` on the calling (UI) thread only; `ResUI` values resolved on a
background thread keep the process default culture. Switching the app to English therefore leaves
the delete-server confirmation and the sudo prompt in whatever language the culture happened to be.

---

## 5. Findings

### 5.1 F1 — Missing Russian: none

`ResUI.resx` and `ResUI.ru.resx` both carry exactly 570 `<data>` entries; zero keys are absent from
the Russian file and zero have an empty `<value>`. Every `L` entry has a non-empty Russian and a
non-empty English string. The `L` indexer's fallback ladder (`en → ru → the key itself`, logged
once) is therefore never exercised in production.

### 5.2 F2 — Latin-only Russian shown to a Russian user

**Reachable (9):**

| # | String | Where | Why it is a defect |
|---|---|---|---|
| 1 | `0 KB/s` | `HomeViewModel.cs:103,105,421,422`; `ConnectHeroView.axaml.cs:399,400,416,417`; `ConnectHeroView.axaml:653,678` | The idle value of the two speed readouts on the connect shield — the most-looked-at number in the app. Hardcoded English units. |
| 2 | live speed, e.g. `1.2 MB/s` | `HomeViewModel.cs:409-410` → `Utils.HumanFy` (`ServiceLib/Common/Utils.cs:162`) | `HumanFy` is EN-invariant: `B, KB, MB, GB, TB, PB` with a **dot** decimal. §9.2 requires Cyrillic units and a **comma** decimal (`12,4 ГБ`). `Common_ByteUnits` («Б,КБ,МБ,ГБ,ТБ,ПБ») exists and is used by Account/Buy/SubscriptionMeta — Home is the only surface that ignores it. |
| 3 | `NONE` | `ProfileDisplay.cs:39` | The transport line of every server row reads e.g. `TCP · NONE` when no TLS is set. `NONE` is an English word, ALL-CAPS, hardcoded. |
| 4 | `CUSTOM` | `ProfileDisplay.cs:15` via `EConfigType.Custom` | Protocol chip for a raw-JSON node. An English word, not a protocol name. |
| 5 | `TUN` | `SettingsView.axaml:262`, `SettingsViewModel.cs:122,187` | The «Режим» segment. Android names the same mode **«VPN-туннель»** (`settings_mode_tun`). See F12. |
| 6 | `INCY/1.0` | `ProviderSettingsPage.axaml:127` | Watermark of the User-Agent field. `INCY` is the internal design codename, not the product name. |
| 7 | `00:00:00` | `ConnectHeroView.axaml.cs:401,418` | Session timer idle value — locale-neutral, acceptable, listed for completeness. |
| 8 | `∞` | `SubscriptionMetaView.axaml.cs:414` | Unlimited traffic. Acceptable as a symbol, but PC also has `Account_DevicesUnlimited` («Безлимит устройств») — two representations of one idea (F7). |
| 9 | `d` | `MainWindow.axaml:341` | Collapsed-rail wordmark initial. Brand, acceptable. |

**Unreachable (21):** `ResUI.ru` values with no Cyrillic — `LabLAN`, `LvTLS`, `TbAlpn`,
`TbAlterId`, `TbBootstrapDNS`, `TbEchConfigList`, `TbFakeIP`, `TbFinalmask`, `TbHy2RealmUrl`,
`TbId`, `TbId5`, `TbMldsa65Verify`, `TbMtu`, `TbSNI`, `TbSettingsDefUserAgent`, `TbShortId`,
`TbSpiderX`, `TbStreamSecurity`, `TransportPathTip5`, `LvWebDavUrl`, `SpeedDisplayText`. All are
protocol-token field labels in upstream server-editor windows; all are locale-neutral and
defensible **if** those windows are ever revived. `SpeedDisplayText` (`{0} : {1}/s↑ | {2}/s↓`) is
the exception — it is a format, not a token, and it would need Russian units.

### 5.3 F3 — Literals that bypass the resource layer

**Reachable defects (4):**

| # | File:line | Literal | Defect |
|---|---|---|---|
| 1 | `SettingsView.axaml:792` | `Text="Масштаб интерфейса"` | A Russian **settings row label** hardcoded in AXAML. It is the only row in Настройки that is not `{loc:T}`, so switching the app to English leaves it in Russian. Needs `Settings_UiScale`. |
| 2 | `SettingsView.axaml:776` | `ToolTip.Tip="Ctrl + / Ctrl − — масштаб, Ctrl 0 — сброс"` | Same row's tooltip: hardcoded Russian, plus **two em-dashes** (§9.2 bans them) and a U+2212 minus sign. |
| 3 | `ProviderSettingsPage.axaml:109` | `Text="—"` | Em-dash as the placeholder for the HWID value before it loads. §9.2 bans the em-dash; and a lone dash tells the user nothing. Same pattern at `AboutPage.axaml:95`, `GeoFilesPage.axaml:73,80`, `UrlSchemesPage.axaml:74`. |
| 4 | `ProfileDisplay.cs:39` | `"NONE"` | Already counted in F2; it is also a literal that should be an `L` key. |

**Hidden but present (3):** `StatusBarView.axaml:101` `Text="Режим:"`, `:114`
`Text="Весь трафик недоступен без прав администратора"`, `:120`
`Content="Перезапустить с правами"` — hardcoded Russian in a view that is hosted at `Width="0"`.
The same two sentences exist correctly in `L` as `Home_TunUnavailable` / `Home_RestartElevated`.
If the status bar is ever un-hidden, these three literals ship untranslatable.

**Design-time only (6):** `MessageBoxDialog.axaml.cs:19` («Удалить подписку?» — note: uses
**подписку** where 9.3 requires **провайдера**), `SubscriptionMetaView.axaml.cs:735,736,740,743,745`,
`BuyViewModel.cs:167`, `SettingsViewModel.cs:142-149`. Never rendered at runtime; listed because
`SubscriptionMetaView.axaml.cs:736` («Автообновление **—** 1 ч.») carries an em-dash that will get
copy-pasted into shipped copy sooner or later.

**Not a defect:** `"СБП"` at `BuyViewModel.cs:423` is a comparison against a backend label, not
display text.

**Unreachable (50):** literal text in the upstream windows — `Min`/`Max`/`Up`/`Down`/`Ipv4,Ipv6`
(`AddServerWindow`), `outboundTag`/`port`/`protocol`/`inboundTag`/`network`/`domain / ip / process`
(`RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow`), the eight protocol names in
`OptionSettingWindow`, `xray config template json` ×4 (`FullConfigTemplateWindow`), and so on.

### 5.4 Dash and ellipsis debt (§9.7 enforcement)

```bash
cd /home/user/v2rayN/v2rayN/v2rayN.Desktop && grep -rn -e '—' -e '–' Common/L.*.cs   # → 0
```

| Location | Em/en-dash | Three dots |
|---|---|---|
| `Common/L.*.cs` (shipped values) | **0** | **0** |
| AXAML literals | **6** (`AboutPage:95`, `GeoFilesPage:73,80`, `ProviderSettingsPage:109`, `UrlSchemesPage:74`, `SettingsView:776` ×2) | 0 |
| `ResUI.ru.resx` | **11** | **5** (`Downloading`, `MsgStartUpdating`, `MsgUpdateV2rayCoreSuccessfullyMore`, `StartService`, `Speedtesting`) |
| C# design-time samples | 1 (`SubscriptionMetaView.axaml.cs:736`) | 0 |

The `L` layer is clean. Note the inconsistency inside `ResUI.ru` itself: `Speedtesting` ends in
`...` while `SpeedtestingWait` ends in `…`.

### 5.5 F4 — ALL-CAPS

Zero ALL-CAPS **Cyrillic** anywhere on PC — `TextBlock.SectionHeader` is `FontWeight=Bold`,
`FontSize=16`, sentence-case (`GlobalStyles.axaml:420`), exactly as §0.4.3 and §9.2 require. No
`TextCasing` / `CharacterCasing` setter exists in any style.

Three ALL-CAPS Latin strings are generated at runtime by `ProfileDisplay.cs`:
`Protocol()` → `VLESS`, `VMESS`, `TROJAN`, `SHADOWSOCKS`, `HYSTERIA2`, `WIREGUARD`, `CUSTOM`;
`NormalizeNetwork()` → `TCP`, `WS`, `GRPC`, `H2`, `KCP`, `QUIC`;
`Transport()` → `NONE`.
Protocol and transport acronyms are legitimately upper-case. **`CUSTOM` and `NONE` are not
acronyms** and are the two that need fixing.

For the record, **Android is the platform with the ALL-CAPS Cyrillic defect**:
`values/strings_account.xml:44` → `account_trial_badge` = `ПРОБНЫЙ`. PC's counterpart
`Account_TrialPeriod` = «Пробный период» is correct. Fixing that is the Android wave's job; it is
recorded here because the two must end up identical (F12).

### 5.6 F5 — Terminology lock (§9.3)

**The `L` table is clean.** Every scan for a banned noun returns either nothing or a false positive:
`Settings_Fragment` («Фрагментация пакетов» — network packets, not a tariff package) and
`Settings_Appearance` («Оформление» — appearance, not «оформить заказ»). The comments in
`L.Common.cs:26-27` and `L.Settings.cs:64` show the lock was applied deliberately: subscription URLs
are **провайдер**, the paid service is **подписка**.

**`ResUI.ru` breaks the lock 25 times** (all unreachable, all upstream): 13 uses of **узел** for a
server (`menuProxiesDelaytestPart`, `menuProxiesSelectActivity`, `MsgRoutingRuleOutboundNodeWarning`,
`MsgRoutingRuleOutboundNodeError`, `MsgGroupCycleDependency`, `MsgGroupChildNodeWarning`,
`MsgGroupChildNodeError`, `MsgGroupChildGroupNodeWarning`, `MsgGroupChildGroupNodeError`,
`MsgGroupNoValidChildNode`, `MsgRoutingRuleEmptyOutboundTag`, `MsgRoutingRuleOutboundNodeNotFound`,
`TbVerifyPeerCertByName`), 8 uses of **соединение** for the tunnel (`TbConnections`,
`menuConnectionClose`, `menuConnectionCloseAll`, `menuModeDirect`, `TipDisplayLog`,
`TbSettingsIPAPIUrl`, `TbDirectResolveStrategy`, `TbRemoteResolveStrategy`), 3 uses of **источник**
for a provider (`TbSettingsGeoFilesSource`, `TbSettingsSrsFilesSource`,
`TbSettingsRoutingRulesSource`), and 1 **профиль** for a server (`TbSelectProfile`).

**One live cross-platform lock break (P1):** the concept *"remove a device from the account"* is
**«Отвязать»** on PC (`Devices_Unlink`, `Devices_UnlinkConfirm`, `Devices_UnlinkShort`,
`Devices_Unlinked`, `Devices_UnlinkFailed`) and **«Удалить»** on Android (`devices_delete_title`,
`devices_delete_confirm`, `devices_deleted`, `devices_error_delete`, `devices_delete_cd`). One
action, two verbs, and «Удалить» collides with the destructive verb used for deleting a *server*.
§9.4's own device-limit string uses «Отвяжите», so **PC is right and Android must change** — but
this is a product decision to record, not a copy edit.

**One missing locked term (P1):** §9.3 fixes *"Linking Telegram"* as **«Привязать Telegram»**, and
§0.4.9 makes that CTA an explicit owner request. `grep "Привязать" Common/L.*.cs` returns only
`Account_LinkAction` = «Привязать» and `Account_EmailLinkTitle` = «Привязать почту». **PC has no
«Привязать Telegram» string at all.** Android has it (`strings.xml:21` `home_link_telegram`).

**One near-break:** `Provider_Hwid` = «Идентификатор устройства (HWID)». §9.3 bans **HWID** as a
word for *устройство*; here it labels a technical identifier and is parenthetical, so it stands —
but Android words the same row «Отправлять идентификатор устройства» (`ps_send_hwid`) with no HWID
at all. Align.

### 5.7 F6 — Error messages that break the §9.4 formula

§9.4: **what happened + why + what to do**, one or two short sentences, no error codes, no blame,
and *every* error ships with a recovery affordance.

36 strings in the `L` table are error-family. **19 pass, 17 fail.** The failures all fail the same
way — they stop after "what happened":

| # | Key | Current | Screen | Required by §9.4 |
|---|---|---|---|---|
| 1 | `Common_CouldntConnect` | Не удалось подключиться | Главная · щит | §9.4 gives this verbatim: **`Сервер не отвечает. Выберите другой сервер или повторите позже.`** The shield's only affordance is `Home_RetryHint` («Нажмите, чтобы повторить»), which names the gesture, not the fix. |
| 2 | `Common_CouldntOpenPayment` | Не удалось открыть страницу оплаты | Аккаунт, Купить | Android says **`…​. Попробуйте другой способ оплаты.`** (`account_checkout_no_browser`, `buy_no_browser`). PC drops the second sentence. |
| 3 | `Common_ServiceUnavailable` | Сервис временно недоступен | Аккаунт, Устройства, Вход | Android: **`Сервис временно недоступен. Повторите попытку позже.`** |
| 4 | `Common_Timeout` | Превышено время ожидания | Аккаунт, Устройства | Android: **`Сервер не ответил вовремя. Повторите попытку.`** PC's wording is also more technical. |
| 5 | `Common_SignInRequired` | Требуется вход в аккаунт | Аккаунт, Устройства | Android: **`Сессия истекла. Войдите снова, чтобы продолжить.`** PC states a requirement, not a cause or an action. |
| 6 | `Common_TooManyRequests` | Слишком много запросов. Попробуйте позже | Аккаунт, Устройства | Android: **`Слишком много запросов. Подождите минуту и повторите.`** PC is also missing the final stop. |
| 7 | `Common_CouldntLoad` | Не удалось загрузить | Аккаунт | No object, no cause, no action. |
| 8 | `Buy_NoPlans` | Тарифы недоступны | Купить | Android: **`Тарифов пока нет. Загляните позже, список обновляется автоматически.`** |
| 9 | `Buy_NoPaymentMethods` | Способы оплаты недоступны | Аккаунт, Купить | Android: **`Способы оплаты недоступны. Повторите попытку позже.`** |
| 10 | `Buy_PaymentError` | Ошибка оплаты | Купить | §9.4 gives **`Платёж не прошёл. Попробуйте другой способ оплаты.`** — Android has it (`account_payment_error_body`); PC ships only the dialog title. |
| 11 | `History_ErrLoad` | Не удалось загрузить историю платежей | История платежей | Needs `…​ Проверьте подключение и повторите.` — the sibling `Devices_ErrLoad` already does exactly that. |
| 12 | `Login_ErrUnavailable` | Вход недоступен | Вход | Android splits this: `auth_err_unavailable` = «Сервис временно недоступен. Повторите попытку позже.», `auth_err_not_configured` = «Вход сейчас недоступен. Повторите попытку позже.» PC collapses both onto one recovery-free string. |
| 13 | `Login_ErrLinkExpired` | Ссылка устарела, начните заново | Вход | Android: **`Ссылка устарела. Начните вход заново.`** Comma-splice vs two sentences; no final stop. |
| 14 | `Login_PasswordMismatch` | Пароли не совпадают | Вход | Field-level; acceptable as a hint, but has no final stop while its sibling `Login_ErrBadCreds` does. Pick one. |
| 15 | `Account_SyncErrorTitle` | Не удалось синхронизировать | Аккаунт · синхронизация | Title only — the recovery lives in `Account_SyncErrorHint`, so this one **passes** as a pair. Listed to note that the pair pattern is the right one and should be used for 1-11 too. |
| 16 | `Home_TunUnavailable` | Режим «весь трафик» недоступен без прав администратора | Главная | States the cause, no action sentence. The button next to it (`Home_RestartElevated`) is the affordance, so this **passes** as a pair. |
| 17 | `Devices_UnlinkFailed` | Не удалось отвязать устройство. Повторите попытку позже. | Устройства | Passes §9.4 — but it is published to `SendSnackMsgRequested` and therefore **never rendered** (§2.3). |

**Raw exception text appended to Russian copy (5 sites, P1).** §9.4 forbids showing the real cause.
Five strings in `L` carry a deliberate **trailing space** so a .NET exception message can be glued on:

| Site | Code |
|---|---|
| `GeoFilesPage.axaml.cs:89` | `txtStatus.Text = L.T("Geo_Failed") + ex.Message;` |
| `BackupPage.axaml.cs:51` | `L.T("Backup_ExportError") + ex.Message` |
| `BackupPage.axaml.cs:84` | `L.T("Backup_ImportError") + ex.Message` |
| `UrlSchemesPage.axaml.cs:110` | `L.T("UrlSchemes_RegisterFailed") + ex.Message` |
| `UrlSchemesPage.axaml.cs:144` | `L.T("UrlSchemes_RemoveFailed") + ex.Message` |

The result is e.g. `Не удалось сохранить копию. Выберите другую папку и повторите. Access to the
path 'C:\…' is denied.` — a Russian sentence followed by an untranslated .NET error. The `L`
comments even acknowledge it (*"the view can drop that concatenation without a new string"*). Drop
the concatenation and drop the five trailing spaces.

**Every error message ships with a recovery affordance — except that no snackbar exists.** §9.4's
closing sentence requires a «Повторить» action on the snackbar or a retry button in the error
state. On PC the snackbar is `IsVisible="False"` by design (`MainWindow.axaml:624`) and the message
panel is unhosted (§2.3). Screens with a real inline error state and retry button — `AccountView`,
`DevicesView`, `PaymentHistoryView`, `BuyView`, `AccountSyncView` — satisfy the rule. Everything
routed through `SendSnackMsgRequested` does not, because nothing is shown at all.

### 5.8 F7 — Duplicates

**14 groups where one Russian string is defined under two or more keys:**

| Russian | Keys | Verdict |
|---|---|---|
| Истекла | `Account_HealthExpired`, `Account_ExpiredOn`, `Sub_Expired` | 3 keys, 1 word. `Account_ExpiredOn` is dead. Collapse to one. |
| Нет серверов | `Home_NoSubs`, `Servers_Empty` | Same 9.5 title on two surfaces — intentional, but should reference one key. |
| Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | `Home_NoSubsHint`, `Servers_EmptyHint` | Same. |
| Вход | `Login_SignIn`, `Login_TabSignIn` | Screen title and segment label; keep both only if the tab ever differs. |
| Купить | `Account_PickPlan`, `Buy_Pay` | Two keys for the locked term. |
| Назад | `Common_Back`, `Account_BackAction` | `Account_BackAction` is redundant. |
| Добавить | `Common_Add`, `Account_AddAction` | Redundant. |
| Открыть | `Common_Open`, `Account_OpenAction` | Redundant. |
| Повторить | `Common_Retry`, `Account_SyncRetry` | Redundant (EN differs: "Retry" vs "Try again" — pick one). |
| Скоро | `Account_SoonAction`, `Login_ComingSoon` | Redundant. |
| Способ оплаты | `Account_TopUpMethod`, `Buy_PaymentMethod` | Redundant. |
| С баланса: {0} | `Account_RenewFromBalance`, `Buy_FromBalance` | Redundant (comment admits it). |
| Что-то пошло не так. Повторите попытку. | `Common_SomethingWrong`, `Login_ErrRetry` | Redundant. |
| Пополнение баланса | `Account_TopUpTitle`, `History_SampleTopUp` | The second is a design-time sample — acceptable. |

**Plus 4 groups where one concept has several *different* wordings, which is worse:**

| Concept | Competing strings |
|---|---|
| Subscription expiry date | `Account_ValidUntil` «Действует до {0}», `Account_ActiveUntil` «Активна до {0}», `Account_ExpiresUntil` «До {0}» (dead), `Sub_Until` «до {0:dd.MM.yyyy}» |
| Device count | `Account_DevicesCount` «Устройства: {0} / {1}», `Account_DevicesShort` «{0} / {1} устройств», `Account_DevicesUsage` «{0} из {1} устройств» (dead), `Account_DevicesTotal` «{0} устройств» |
| Not connected | `Home_NotConnected` «Не подключено», `Status_Disconnected` «Отключено» (dead) — two nouns for one state |
| Sign-in screen title | `Login_SignIn` «Вход», `Login_Title` «Вход в departament», `Account_SignInTitle` «Войдите в departament» |

**And one duplicate *label* on two different rows (P2, real bug):** `UrlSchemesPage.axaml.cs:33-34`
labels both `depv://disconnect` **and** `depv://close` with `UrlSchemes_Stop` («Отключиться»). The
correct string `UrlSchemes_Close` («Закрыть приложение») is defined at `L.Settings.cs:175` and never
used — the comment there says so explicitly.

### 5.9 F9 — Empty states (§9.5)

§9.5 formula: title (what is not here) + one line (why / what it gives you) + one action.

| Screen | Title | Line | Action | Verdict |
|---|---|---|---|---|
| Главная, no servers | `Home_NoSubs` «Нет серверов» | `Home_NoSubsHint` ✓ verbatim | «Добавить по QR-коду» / «Добавить из буфера обмена» | **passes** |
| Серверы, no servers | `Servers_Empty` + `Servers_EmptyHint` | ✓ verbatim | (buttons live on the meta bar) | **passes** |
| Аккаунт, no subscription | `Account_FirstSub` «Подписки пока нет» | `Account_NoSubHint` ✓ verbatim | `Account_PickPlan` «Купить» ✓ | **passes** |
| Устройства, none | `Devices_Empty` + `Devices_EmptyHint` | ✓ verbatim | none ✓ | **passes** |
| **История платежей, none** | `History_Empty` «Платежей пока нет» | **missing** — `History_EmptyHint` («Здесь появится история покупок и продлений.») is defined at `L.Buy.cs:66` and **never rendered**; `PaymentHistoryView.axaml:296-303` draws the title only | **`Common_BuySubscription`** — but §9.5 specifies **no action** for this state | **fails ×2** |
| **Серверы, search found nothing** | **does not exist** | — | — | **fails** — §9.5 requires «Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск». `CompactServersView.axaml:108` has the search box; no filtered-empty state is rendered anywhere. Android has the pieces (`menu_actions_ping_filtered`, `menu_actions_reset_search`). |
| **Telegram не привязан** | **does not exist** | — | — | **fails** — §9.5 specifies the trio «Telegram не привязан» / «Привяжите Telegram, чтобы управлять подпиской из бота.» / «Привязать Telegram». PC's linking block (`AccountView.axaml`) shows only `Account_LinkAction` «Привязать». Android has `account_no_telegram` = «Telegram не привязан». |
| **Купить, no plans** | `Buy_NoPlans` «Тарифы недоступны» | none | none | **fails** — see F6 #8. |

### 5.10 F10 — Offline (§9.6)

**Not implemented.** §9.6 requires that offline be a *designed state*: keep the last known data,
mark it stale («Данные могли устареть»), disable network-dependent actions, and show one quiet
persistent bar `Нет сети. Показаны последние данные.` with a `Повторить` action.

Searching the whole desktop project for «Нет сети», «Показаны последние», «устарел», `offline`
returns nothing but `Login_ErrLinkExpired` and one code comment. **Zero of the three required
strings exist on PC.** The only network-loss copy is `Common_NetworkError`
(«Нет подключения к интернету. Проверьте сеть и повторите.») — correct per §9.4, but it is an
error message on a failed request, not the persistent stale-data state §9.6 describes. Android is
in the same position (`account_error_network` / `auth_err_network` only), so this is a **shared
gap**, not a divergence.

### 5.11 F11 — Dead copy

**14 `L` keys are defined and never referenced anywhere outside `L.*.cs`:**

| Key | Russian | Why it matters |
|---|---|---|
| `Account_AutoRenew` | Автопродление | The auto-renew row label — the card uses `Account_AutoRenewOn/Off` sentences instead. |
| `Account_DevicesUsage` | {0} из {1} устройств | Third device-count format (F7). |
| `Account_ExpiredOn` | Истекла | Duplicate of `Account_HealthExpired`. |
| `Account_ExpiresUntil` | До {0} | Third expiry format (F7). |
| `Account_ExtraDevicesN` | +{0} к устройствам | The add-devices sheet never shows the delta. |
| `Account_Linked` | Привязан | The «Способы входа» rows show a check glyph with **no word** — colour/icon is the only signal, which §14.7 forbids. |
| `Account_NoUpgrades` | Вы на максимальном тарифе | A user already on the top plan gets no explanation for the missing upgrade action. |
| `Account_TgLinkWaiting` | Ожидаем подтверждения в Telegram… | The Telegram-link flow has no waiting state. |
| `History_EmptyHint` | Здесь появится история покупок и продлений. | §9.5 line that is never drawn (F9). |
| `Login_SignUp` | Регистрация на сайте | Superseded by the in-app register tab; Android still shows it (`auth_register_site`). |
| `Onboarding_OrSignIn` | Или войдите в свой аккаунт | Superseded by `Onboarding_OrSignInShort`. |
| `Status_ConnectedTo` | Подключено · {0} | The shield never names the connected server. |
| `Status_Disconnected` | Отключено | Second noun for `Home_NotConnected` (F7). |
| `UrlSchemes_Close` | Закрыть приложение | The correct label for `depv://close`, which is mislabelled «Отключиться» (F7). |

**~346 `ResUI` keys** are wired into upstream windows the shell never opens (§2.2), plus **30
`ResUI` keys referenced from nowhere at all** (`FillCorrectConfigTemplateText`,
`LvEncryptionMethod`, `MsgNotSupportProtocol`, `PleaseSelectProtocol`, `RemoveRules`,
`ServerNameMustBeValidDomain`, `TbEnabletDnsViaProxy`, `TbHeaderType`,
`TbSettingsDefAllowInsecure`, `TbSettingsException`, `TbSettingsFragmentFallbackDelay`(+Tip),
`TbSettingsLogEnabled`, `TbSorting*` ×4, `TransportHeaderType3`, `TransportPathTip1/2/3/5`,
`TransportRequestHostTip1/2/3/4`, `menuExitTips`, `menuModeNothing`,
`menuShowOrHideMainWindow`, `menuTestServerResult`).

**~361 strings are rendered into a channel with no subscriber** (§2.3): all 321 `ResUI` call sites
in `ServiceLib` plus ~40 `L` strings published to `SendSnackMsgRequested`. This is the dominant
finding of the whole inventory.

---

## 6. F12 — Cross-platform wording divergence

This is the owner's actual requirement. §13 makes it binding: *"every user-visible Russian string
for the same concept"* is **identical across platforms**. Below, every concept present on both
clients where the two strings differ. `✓` rows (identical) are omitted except where they prove a
neighbour is wrong.

### 6.1 Divergences that must be fixed (P1)

| # | Concept | PC | Android | Verdict |
|---|---|---|---|---|
| 1 | Remove a device from the account | «Отвязать устройство» / «Отвязать устройство?» / «Устройство отвязано» / «Не удалось отвязать устройство…» | «Удалить устройство» / «Удалить устройство?» / «Устройство удалено» / «Не удалось удалить устройство…» | **PC is right** (§9.4 says «Отвяжите одно из устройств»). Android changes. |
| 2 | Link Telegram | *no string* — only «Привязать» | `home_link_telegram` «Привязать Telegram» | **Android is right** (§9.3 + §0.4.9). PC must add `Account_LinkTelegram`. |
| 3 | Telegram not linked (empty state) | *no string* | `account_no_telegram` «Telegram не привязан» | PC must add the §9.5 trio. |
| 4 | «Весь трафик» / TUN mode name | `TUN` (raw literal) | `settings_mode_tun` «VPN-туннель» (plus 4 more competing labels) | **Neither is right.** Pick one Russian noun and use it in both mode segments, the status bar and `Home_TunUnavailable`. |
| 5 | Payment declined | `Buy_PaymentError` «Ошибка оплаты» (title only) | `account_payment_error_body` «Платёж не прошёл. Попробуйте другой способ оплаты.» | §9.4 gives Android's string verbatim. PC adopts it. |
| 6 | Couldn't open the payment page | «Не удалось открыть страницу оплаты» | «Не удалось открыть страницу оплаты. Попробуйте другой способ оплаты.» | Android's is complete; PC adopts it. |
| 7 | Service unavailable | «Сервис временно недоступен» | «Сервис временно недоступен. Повторите попытку позже.» | Android's; PC adopts. |
| 8 | Session expired / sign-in required | «Требуется вход в аккаунт» | «Сессия истекла. Войдите снова, чтобы продолжить.» | Android's; PC adopts. |
| 9 | Request timed out | «Превышено время ожидания» | «Сервер не ответил вовремя. Повторите попытку.» | Android's; PC adopts. |
| 10 | Rate limited | «Слишком много запросов. Попробуйте позже» | «Слишком много запросов. Подождите минуту и повторите.» | Android's; PC adopts. |
| 11 | No plans available | «Тарифы недоступны» | `buy_empty` «Тарифов пока нет. Загляните позже, список обновляется автоматически.» **and** `account_tariffs_empty` «Тарифы недоступны» | Android contradicts itself; pick `buy_empty`'s form for both, delete the other. |
| 12 | Trial badge | `Account_TrialPeriod` «Пробный период» | `account_trial_badge` «ПРОБНЫЙ» (ALL-CAPS) | **PC is right**; §0.4.3 bans ALL-CAPS. Android changes. |

### 6.2 Divergences to reconcile (P2)

| # | Concept | PC | Android |
|---|---|---|---|
| 13 | Home / Servers nav labels | `Nav_Home` «Главная» | `bottom_nav_home` **«Home»**, `bottom_nav_servers` **«Servers»**, `bottom_nav_more` **«More»** — English in a Russian UI |
| 14 | No servers, hint line | «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» (§9.5 verbatim) | `home_empty_subtitle` «Добавьте **подписку**, чтобы появились серверы.» — breaks §9.3 |
| 15 | Delete a provider | `Sub_DeleteConfirm` «Удалить **провайдера** и его серверы?» | `sub_delete_confirm` «Удалить **подписку** и её серверы?» — breaks §9.3 |
| 16 | Subscription auto-update row | `Settings_SubAutoUpdate` «Автообновление **провайдеров**» | `settings_sub_auto_update` «Автообновление **подписки**» — breaks §9.3 |
| 17 | Mux connection count | `Settings_MuxCount` «Число **подключений** Mux» | `settings_mux_concurrency` «Число **соединений** Mux» — breaks §9.3 |
| 18 | Mux hint | «Объединяет запросы в один канал» | «Объединяет запросы в один канал **соединения**» — breaks §9.3 |
| 19 | Ping method: real | `Ping_RealTitle` «Реальная задержка» + `Ping_RealHint` «Через ядро, как при подключении» | `settings_ping_method_real` «Реальная задержка (через ядро)» |
| 20 | Ping method: TCP | `Ping_TcpHint` «TCP-подключение к серверу» | `settings_ping_method_tcp` «TCP-**соединение**» — breaks §9.3 |
| 21 | Black-and-white theme | `Settings_Monochrome` «Чёрно-белый режим» | two strings: `settings_theme_mono` «Чёрно-белый режим», `settings_appearance_mono` «Чёрно-белая» |
| 22 | Share a server | `Servers_ShareQr` «Поделиться · QR-код», `Servers_ShareLink` «Поделиться · ссылка» | `server_action_share_qr` «Поделиться (QR)», `server_action_share_clipboard` «Поделиться (**буфер**)» — different separator *and* different object |
| 23 | Top-up amount | `Account_TopUpTitle` «Пополнение баланса», `Account_TopUpHint` «Введите сумму в рублях. Откроется страница оплаты.», `Account_AmountGtZero` «Введите сумму больше 0» | `account_top_up_title` «Сумма пополнения», `account_top_up_hint` «Введите сумму», `account_top_up_invalid` «Введите корректную сумму» |
| 24 | Waiting for Telegram | `Login_WaitingConfirm` «Ожидаем подтверждения в Telegram» (no ellipsis) | `auth_awaiting` «Ожидаем подтверждения в Telegram…» |
| 25 | Link expired | «Ссылка устарела, начните заново» | «Ссылка устарела. Начните вход заново.» |
| 26 | Sign-in screen title | three PC strings: «Вход», «Вход в departament», «Войдите в departament» | `auth_title` «Вход» |
| 27 | No subscription (headline) | `Account_FirstSub` «Подписки пока нет» | `account_empty_title` «Подписки пока нет» ✓ **but also** `auth_subscription_none` «Подписка не подключена» |
| 28 | User-Agent hint | `Provider_UserAgentHint` «Отправляется ядром на исходящих подключениях» | `ps_user_agent_hint` «User-Agent для запросов подписки» — the two describe **different behaviour** |
| 29 | Provider auto-update hint | `Provider_AutoUpdateHint` «Автоматически обновлять серверы провайдеров» | `ps_auto_update_sub` «Автоматически обновлять подписки» |
| 30 | Local proxy row subtitle | `Settings_LocalProxyHint` «Порт, имя пользователя и пароль SOCKS5» | `settings_local_proxy_sub` «SOCKS5-авторизация, память, доступ по сети» |
| 31 | Per-app screen name | header `Settings_PerApp` «Прокси по приложениям», in-page title `PerApp_SplitTunnel` «Раздельное туннелирование» | `pa_title` «Прокси по приложениям» only |

### 6.3 What already matches, exactly — keep it that way

`Settings_BypassLan`/`Hint` ≡ `settings_bypass_lan`/`_sub`; `Settings_Ipv6Hint` ≡ `settings_ipv6_sub`;
`Settings_Fragment`/`Hint` ≡ `settings_fragment`/`_sub`; `Settings_Mux` ≡ `settings_mux`;
`Settings_UrlSchemes`/`Hint` ≡ `settings_url_scheme`/`_sub`; `Settings_Appearance` ≡ `settings_appearance`;
`Settings_Language` ≡ `settings_language`; `Settings_Routing` ≡ `settings_routing`;
`Settings_GeoFiles` ≡ `settings_assets`; `Settings_About` ≡ `settings_about`;
`Settings_Backup` ≡ `settings_backup`; `Settings_PerApp` ≡ `settings_per_app`;
all five section headers («Подключение», «Обход блокировок», «Интерфейс», «Подписка», «О приложении»);
`Nav_Settings`/`Nav_Account` ≡ `bottom_nav_settings`/`bottom_nav_account`;
`Home_NotConnected` ≡ `home_not_connected`; `Home_ChooseServer` ≡ `home_select_server`;
`Servers_Empty` ≡ `servers_empty_title`; `Servers_SearchPlaceholder` ≡ `search_hint`;
`Servers_MakeDefault` ≡ `server_action_set_default`; `Servers_Duplicate` ≡ `server_action_duplicate`;
`Common_TestLatency` ≡ `menu_actions_ping_cd`; `Account_Balance` ≡ `account_balance`;
`Account_TopUp` ≡ `account_top_up`; `Account_MySubs` ≡ `account_subs_header`;
`Account_ValidUntil` ≡ `account_expires`; `Account_DevicesCount` ≡ `account_devices`;
`Account_ReferralCode` ≡ `account_referral`; `Account_CopyReferralCode` ≡ `account_copy_referral`;
`Account_NoSubHint` ≡ `account_no_subscription`; `Account_UpgradeTariff` ≡ `account_upgrade`;
`Buy_ChoosePlan` ≡ `buy_pick_tariff`; `Buy_Total` ≡ `buy_total`; `Buy_Pay` ≡ `buy_pay`;
`Buy_AdditionalDevices` ≡ `buy_extra_devices_title`; `Buy_AddDevice`/`RemoveDevice` ≡ `buy_extra_devices_plus`/`_minus`;
`Buy_ErrLoadPlans` ≡ `buy_error`; `Buy_FromBalance` ≡ `pay_method_from_balance_fmt`;
`Buy_Processing` ≡ `buy_pending`; `Buy_Paid` ≡ `buy_success`;
`Devices_Subtitle` ≡ `devices_subtitle`; `Devices_Empty`/`EmptyHint` ≡ `devices_empty`/`_hint`;
`Devices_ErrLoad` ≡ `devices_error_generic`; `Devices_NoSub`+`NoSubHint` ≡ `devices_error_no_subscription`;
`Devices_Active` ≡ `devices_last_active`; `Devices_Id` ≡ `devices_hwid`;
`Devices_Unknown` ≡ `devices_unknown_model`; `History_Empty` ≡ `history_empty`;
`History_ErrLoad` ≡ `history_error_generic`; `History_Status*` ≡ `account_status_*`;
`Login_Email` ≡ `auth_email_hint`; `Login_Password` ≡ `auth_password_hint`;
`Login_EmailInvalid` ≡ `auth_email_invalid`; `Login_EnterCode` ≡ `auth_2fa_desc`;
`Login_CodeIs6` ≡ `auth_code_invalid`; `Login_Confirm` ≡ `auth_btn_2fa`;
`Login_StartOver` ≡ `auth_restart`; `Login_ErrBadCreds` ≡ `auth_err_credentials`;
`Login_ErrRetry`/`Common_SomethingWrong` ≡ `auth_err_generic`/`account_error_generic`;
`Common_NetworkError` ≡ `auth_err_network`/`account_error_network`;
`Common_SignInTelegram` ≡ `auth_btn_telegram`; `Common_SignInWebsite` ≡ `auth_sign_in_site`;
`Common_BuySubscription` ≡ `buy_title`/`account_hub_buy`; `Common_PaymentHistory` ≡ `history_title`;
`Common_Copied` ≡ `lp_copied`; `Settings_Username` ≡ `lp_socks_login`; `Settings_Port` ≡ `lp_socks_port`;
`Login_ShowPassword`/`HidePassword` ≡ `lp_show_password`/`lp_hide_password`;
`Home_ManageAccount` ≡ `auth_open_account`; `Account_YourSubscription` ≡ `account_sub_summary_title`;
`Account_Devices` ≡ `account_hub_devices`; `Provider_Title` ≡ `ps_title`;
`Provider_AutoUpdate` ≡ `ps_auto_update`; `Provider_Interval` ≡ `ps_interval`;
`Provider_SecUpdates` ≡ `ps_section_update`; `Provider_SecNetwork` ≡ `ps_section_network`;
`Routing_DomainStrategy` ≡ `routing_domain_strategy`; `About_Version` ≡ `about_version`.

### 6.4 Features that exist on one platform only

Not divergences — parity gaps to log, per §13.

**PC only:** «Облегчённый режим» + hint, «Производительность» section, «Масштаб интерфейса»,
`UrlSchemes_*` registration flow (11 strings), `Backup_*` local zip export/import (13 strings),
`Geo_*` (7 strings), `Account_Sync*` overlay (7 strings), the whole in-app register/magic-link/
password-reset family (`Login_Tab*`, `Login_Magic*`, `Login_Reset*`, `Login_Verify*`,
`Login_CreateAccount`, `Login_ForgotPassword`, `Login_ContinueGoogle`, `Login_ByCode`,
`Login_CodePaste`, `Login_SiteHandoff` — 20 strings), `Home_TunUnavailable`/`Home_RestartElevated`,
`Sub_Pin`/`Sub_CollapseServers`.

**Android only:** «Постоянный VPN и блокировка», «Устройства» settings section, promo codes
(`account_promo_*`), avatar (`account_change_avatar`, `account_avatar_*`), trial activation
(`account_trial`), device-list diagnostics (`devices_diag_*`), «Ядро»/«Журнал»/«Идентификатор» in
About, WebDAV backup, TV pairing (`settings_tv_*`), hotspot proxy (`lp_hotspot_*`), memory limit
(`lp_memory_*`), `menu_actions_*` bulk server operations (24 strings), `routing_ed_*` rule editor
(20 strings).

---

## 7. Text that only *looks* like a label

The specific ask: **placeholder / watermark text standing in for a field label disappears the moment
the user types, so it cannot carry the field's meaning.** All 14 `Watermark=` sites in the desktop
client, audited.

### 7.1 Defects — the watermark is the only carrier of meaning

| # | File:line | Field | Watermark | What is lost |
|---|---|---|---|---|
| 1 | `LoginView.axaml:351` | Email, sign-in **and** register | `{loc:T Login_Email}` «Электронная почта» | **No persistent label anywhere.** The nearest text above is the `Login_TabSignIn`/`TabRegister` segment. Once the user types `a`, nothing on screen says this field is the email. On the register tab the form is email + password + confirm-password, three unlabelled boxes; the only distinguishing text is the masking dots. **P1.** |
| 2 | `LoginView.axaml:370` | Password | `{loc:T Login_Password}` «Пароль» | Same. `Login_PasswordHint` («Минимум 8 символов») is rendered *below*, but only in register mode and only as a requirement, not a name. **P1.** |
| 3 | `LoginView.axaml:417` | Repeat password | `{loc:T Login_ConfirmPassword}` «Повторите пароль» | Same, and worse: once both password fields hold text they are visually **identical** — two masked boxes, no way to tell which is which. **P1.** |
| 4 | `LoginView.axaml:603` | Handoff code | `{loc:T Login_CodePaste}` «Вставьте код из браузера» | Revealed by the «Войти по коду» link; the link text is a *verb*, not a field name. After pasting, the field is a bare box of characters. **P2.** |
| 5 | `AccountView.axaml:377` | Top-up amount | `{loc:T Account_AmountRub}` «Сумма, ₽» | Inside a flyout titled `Account_TopUpTitle` «Пополнение баланса» with the line «Введите сумму в рублях…», so the *purpose* survives — but the **currency unit `₽` is only in the watermark** and vanishes on the first keystroke, exactly when the user needs to know whether they are typing rubles or kopecks. **P2.** |

Fix pattern already present in the same codebase: `SettingsView.axaml:481-509` puts a
`TextBlock Classes="Subtitle"` above each field (`Settings_Port`, `Settings_Username`,
`Login_Password`) and uses the watermark for a *value hint* (`Settings_NotSet` «Не задан»). Apply
that pattern to LoginView and to the amount field.

### 7.2 Correct usage — a real label exists, the watermark is an example value

| File:line | Persistent label | Watermark |
|---|---|---|
| `SettingsView.axaml:500` | «Имя пользователя» (`Settings_Username`) | «Не задан» |
| `SettingsView.axaml:509` | «Пароль» (`Login_Password`) | «Не задан» |
| `DnsSubView.axaml:131` | section header «Свой DNS-адрес» + hint | `https://example.com/dns-query` |
| `PingSettingsPage.axaml:144` | «Адрес проверки задержки» | `https://www.gstatic.com/generate_204` |
| `PingSettingsPage.axaml:155` | «Тайм-аут проверки, сек» | `5` |
| `ProviderSettingsPage.axaml:127` | section header «User-Agent» + hint | `INCY/1.0` (but see F2 #6 — the codename) |
| `AccountView.axaml:1225` | flyout title «Привязать почту» + hint | «Электронная почта» — single-field flyout, meaning survives |
| `CompactServersView.axaml:108` | — | «Поиск серверов…» — a search box with a magnifier glyph; the accepted exception |
| `PerAppProxyPage.axaml:120` | — | «Поиск…» — same exception |

### 7.3 The other kind of fake label — a dash where a value belongs

Five places render `Text="—"` as the pre-load state of a value:
`AboutPage.axaml:95`, `GeoFilesPage.axaml:73`, `GeoFilesPage.axaml:80`,
`ProviderSettingsPage.axaml:109`, `UrlSchemesPage.axaml:74`.
A lone em-dash is not copy: it names nothing, it bans itself under §9.2, and it is
indistinguishable from a genuinely empty value. `Geo_NotDownloaded` («Не загружен») already exists
for the `GeoFilesPage` case and is the right pattern for all five.

### 7.4 Placeholders in the unreachable upstream windows

`AddServerWindow.axaml:448,453,524,529,652` (`Min`, `Max`, `Up`, `Down`, `Ipv4,Ipv6`),
`OptionSettingWindow.axaml:284,289,882,927` (`Up`, `Down`, `proxy_set.sh`, `pac.txt`) —
`PlaceholderText` on `ComboBox`/`TextBox` with no label. Listed for completeness; unreachable today,
and every one of them is the same defect if those windows are ever revived.

---

## 8. Literal user-facing text in AXAML

Every non-binding text attribute in the 53 view files. `Достижимо = нет` means the view is registered in `SimpleViewLocator` but has no live call site (§2.2).

| Файл:строка | Атрибут | Литерал | Экран | Достижимо |
|---|---|---|---|---|
| `App.axaml:39` | `Header` | `Перезапустить` | Трей | да |
| `App.axaml:43` | `Header` | `Подключить` | Трей | да |
| `App.axaml:44` | `Header` | `Показать` | Трей | да |
| `App.axaml:45` | `Header` | `Выход` | Трей | да |
| `Views/AboutPage.axaml:67` | `Text` | `departament` | Настройки · о приложении | да |
| `Views/AboutPage.axaml:79` | `Content` | `departament.site` | Настройки · о приложении | да |
| `Views/AboutPage.axaml:95` | `Text` | `—` | Настройки · о приложении | да |
| `Views/AccountView.axaml:1079` | `Text` | `Telegram` | Аккаунт | да |
| `Views/AccountView.axaml:1143` | `Text` | `Google` | Аккаунт | да |
| `Views/AddServerWindow.axaml:448` | `PlaceholderText` | `Min` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:453` | `PlaceholderText` | `Max` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:524` | `PlaceholderText` | `Up` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:529` | `PlaceholderText` | `Down` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:652` | `PlaceholderText` | `Ipv4,Ipv6` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:726` | `Text` | `QUIC` | устаревшее окно upstream | **нет** |
| `Views/AddServerWindow.axaml:952` | `Text` | `Seed` | устаревшее окно upstream | **нет** |
| `Views/ConnectHeroView.axaml:365` | `Text` | `0 KB/s` | Главная · щит | да |
| `Views/ConnectHeroView.axaml:401` | `Text` | `0 KB/s` | Главная · щит | да |
| `Views/DnsSubView.axaml:90` | `Text` | `DNS` | Настройки · DNS | да |
| `Views/DnsSubView.axaml:109` | `Text` | `Cloudflare` | Настройки · DNS | да |
| `Views/DnsSubView.axaml:112` | `Text` | `Google` | Настройки · DNS | да |
| `Views/DnsSubView.axaml:115` | `Text` | `AdGuard` | Настройки · DNS | да |
| `Views/DnsSubView.axaml:131` | `Watermark` | `https://example.com/dns-query` | Настройки · DNS | да |
| `Views/DnsSubView.axaml:144` | `Text` | `FakeIP` | Настройки · DNS | да |
| `Views/FullConfigTemplateWindow.axaml:101` | `Header` | `xray config template json` | устаревшее окно upstream | **нет** |
| `Views/FullConfigTemplateWindow.axaml:111` | `Header` | `xray tun config template json` | устаревшее окно upstream | **нет** |
| `Views/FullConfigTemplateWindow.axaml:179` | `Header` | `sing-box config template json` | устаревшее окно upstream | **нет** |
| `Views/FullConfigTemplateWindow.axaml:189` | `Header` | `sing-box tun config template json` | устаревшее окно upstream | **нет** |
| `Views/GeoFilesPage.axaml:72` | `Text` | `geoip.dat` | Настройки · файлы ресурсов | да |
| `Views/GeoFilesPage.axaml:73` | `Text` | `—` | Настройки · файлы ресурсов | да |
| `Views/GeoFilesPage.axaml:79` | `Text` | `geosite.dat` | Настройки · файлы ресурсов | да |
| `Views/GeoFilesPage.axaml:80` | `Text` | `—` | Настройки · файлы ресурсов | да |
| `Views/LoginView.axaml:304` | `Text` | `departament` | Вход | да |
| `Views/MainWindow.axaml:12` | `Title` | `departament VPN` | Оболочка | да |
| `Views/MainWindow.axaml:341` | `Text` | `d` | Оболочка | да |
| `Views/MainWindow.axaml:349` | `Text` | `departament` | Оболочка | да |
| `Views/OnboardingView.axaml:81` | `Text` | `departament` | Онбординг | да |
| `Views/OptionSettingWindow.axaml:284` | `PlaceholderText` | `Up` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:289` | `PlaceholderText` | `Down` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:882` | `PlaceholderText` | `proxy_set.sh` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:927` | `PlaceholderText` | `pac.txt` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1104` | `Text` | `VMess` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1117` | `Text` | `Custom` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1130` | `Text` | `Shadowsocks` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1143` | `Text` | `Socks` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1156` | `Text` | `VLESS` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1169` | `Text` | `Trojan` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1182` | `Text` | `Hysteria2` | устаревшее окно upstream | **нет** |
| `Views/OptionSettingWindow.axaml:1195` | `Text` | `Wireguard` | устаревшее окно upstream | **нет** |
| `Views/PingSettingsPage.axaml:122` | `Text` | `TCP` | Настройки · пинг | да |
| `Views/PingSettingsPage.axaml:144` | `Watermark` | `https://www.gstatic.com/generate_204` | Настройки · пинг | да |
| `Views/ProviderSettingsPage.axaml:109` | `Text` | `—` | Настройки · провайдеры | **нет** — строка настроек не заведена |
| `Views/ProviderSettingsPage.axaml:121` | `Text` | `User-Agent` | Настройки · провайдеры | **нет** — строка настроек не заведена |
| `Views/ProviderSettingsPage.axaml:127` | `Watermark` | `INCY/1.0` | Настройки · провайдеры | **нет** — строка настроек не заведена |
| `Views/RoutingRuleDetailsWindow.axaml:79` | `Text` | `outboundTag` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleDetailsWindow.axaml:109` | `Text` | `port` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleDetailsWindow.axaml:131` | `Text` | `protocol` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleDetailsWindow.axaml:156` | `Text` | `inboundTag` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleDetailsWindow.axaml:178` | `Text` | `network` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:233` | `Header` | `outboundTag` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:237` | `Header` | `port` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:241` | `Header` | `protocol` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:245` | `Header` | `inboundTag` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:249` | `Header` | `network` | устаревшее окно upstream | **нет** |
| `Views/RoutingRuleSettingWindow.axaml:253` | `Header` | `domain / ip / process` | устаревшее окно upstream | **нет** |
| `Views/SettingsView.axaml:262` | `Content` | `TUN` | Настройки | да |
| `Views/SettingsView.axaml:355` | `Text` | `IPv6` | Настройки | да |
| `Views/SettingsView.axaml:390` | `Text` | `DNS` | Настройки | да |
| `Views/SettingsView.axaml:776` | `ToolTip.Tip` | `Ctrl + / Ctrl − — масштаб, Ctrl 0 — сброс` | Настройки | да |
| `Views/SettingsView.axaml:792` | `Text` | `Масштаб интерфейса` | Настройки | да |
| `Views/StatusBarView.axaml:101` | `Text` | `Режим:` | Статус-бар | **нет** — Width=0, скрыт |
| `Views/StatusBarView.axaml:114` | `Text` | `Весь трафик недоступен без прав администратора` | Статус-бар | **нет** — Width=0, скрыт |
| `Views/StatusBarView.axaml:120` | `Content` | `Перезапустить с правами` | Статус-бар | **нет** — Width=0, скрыт |
| `Views/SubscriptionMetaView.axaml:325` | `ToolTip.Tip` | `Telegram` | Серверы · плашка провайдера | да |
| `Views/UrlSchemesPage.axaml:74` | `Text` | `—` | Настройки · схемы URL | да |

Всего литералов: 75, из них в достижимых экранах: 35

---

## 9. Full `L` table — every Departament string

405 entries, in file order. `Экран` lists the views/view-models that reference the key;
**нет** means the key is defined and never referenced outside `L.*.cs` (§5.11).

Two caveats on the `Достижимо` column. The nine `Provider_*` keys read `да` because
`ProviderSettingsPage` references them, but that page has no settings row and is never opened
(§4.1) — treat them as unreachable. The ~40 keys published to `SendSnackMsgRequested`
(`Common_Copied`, `Account_RenewDone`, `Devices_Unlinked`, …) also read `да` because the code
path runs, but nothing is rendered (§2.3) — treat them as unreachable too.

| Ключ | Русский | English | Экран | Достижимо |
|---|---|---|---|---|
| `Account_Balance` | Баланс | Balance | Аккаунт | да |
| `Account_TopUp` | Пополнить | Top up | Аккаунт | да |
| `Account_TopUpTitle` | Пополнение баланса | Top up balance | Аккаунт | да |
| `Account_TopUpHint` | Введите сумму в рублях. Откроется страница оплаты. | Enter an amount in rubles. The payment page will open. | Аккаунт | да |
| `Account_AmountRub` | Сумма, ₽ | Amount, ₽ | Аккаунт | да |
| `Account_Continue` | Продолжить | Continue | Аккаунт | да |
| `Account_TopUpMethod` | Способ оплаты | Payment method | Аккаунт | да |
| `Account_TopUpVia` | Оплата · {0} | Payment · {0} | Аккаунт | да |
| `Account_CopyReferralCode` | Скопировать код | Copy code | Аккаунт | да |
| `Account_FirstSub` | Подписки пока нет | No subscription yet | Аккаунт | да |
| `Account_NoSubHint` | Купите тариф, чтобы подключаться к серверам Departament. | Buy a plan to connect to Departament servers. | Аккаунт | да |
| `Account_Devices` | Устройства | Devices | Аккаунт, Устройства | да |
| `Account_SignOut` | Выйти | Sign out | Аккаунт | да |
| `Account_SignInTitle` | Войдите в departament | Sign in to departament | Аккаунт | да |
| `Account_SignInHint` | Через Telegram быстро и без пароля. Или войдите по почте на сайте. | Telegram is fast and needs no password. Or sign in by email on the website. | Аккаунт | да |
| `Account_AmountGtZero` | Введите сумму больше 0 | Enter an amount greater than 0 | Аккаунт | да |
| `Account_ReferralCode` | Реф-код {0} | Referral code {0} | Аккаунт | да |
| `Account_ReferralBenefit` | Код друга | Referral code | Аккаунт | да |
| `Account_MySubs` | Мои подписки | My subscriptions | Аккаунт | да |
| `Account_ValidUntil` | Действует до {0} | Valid until {0} | Аккаунт | да |
| `Account_DevicesCount` | Устройства: {0} / {1} | Devices: {0} / {1} | Аккаунт | да |
| `Account_TariffCaption` | Тариф · {0} | Plan · {0} | Аккаунт | да |
| `Account_TrialPeriod` | Пробный период | Trial period | Аккаунт | да |
| `Account_HealthActive` | Активна | Active | Аккаунт | да |
| `Account_HealthExpiring` | Истекает | Expiring | Аккаунт | да |
| `Account_HealthExpired` | Истекла | Expired | Аккаунт | да |
| `Account_ExpiresUntil` | До {0} | Until {0} | — | **нет** |
| `Account_ExpiresInDays` | Осталось {0} дн. | {0} days left | Аккаунт | да |
| `Account_ExpiredOn` | Истекла | Expired | — | **нет** |
| `Account_Perpetual` | Бессрочно | No expiry | Аккаунт | да |
| `Account_DevicesUsage` | {0} из {1} устройств | {0} of {1} devices | — | **нет** |
| `Account_DevicesTotal` | {0} устройств | {0} devices | Аккаунт | да |
| `Account_Renew` | Продлить | Renew | Аккаунт | да |
| `Account_PrevSub` | Предыдущая | Previous | Аккаунт | да |
| `Account_NextSub` | Следующая | Next | Аккаунт | да |
| `Account_YourSubscription` | Ваша подписка | Your subscription | Аккаунт | да |
| `Account_SubscriptionN` | Подписка {0} | Subscription {0} | Аккаунт | да |
| `Account_ActiveUntil` | Активна до {0} | Active until {0} | Аккаунт | да |
| `Account_ExpiredOnDate` | Истекла {0} | Expired {0} | Аккаунт | да |
| `Account_DevicesShort` | {0} / {1} устройств | {0} / {1} devices | Аккаунт | да |
| `Account_DevicesUnlimited` | Безлимит устройств | Unlimited devices | Аккаунт | да |
| `Account_TrafficUnlimited` | {0} · безлимит | {0} · unlimited | Аккаунт | да |
| `Account_AutoRenew` | Автопродление | Auto-renew | — | **нет** |
| `Account_AutoRenewNext` | Продлится {0}, спишем {1} | Renews {0}, we'll charge {1} | Аккаунт | да |
| `Account_AutoRenewOn` | Автопродление включено | Auto-renew is on | Аккаунт | да |
| `Account_AutoRenewOnDate` | Продлится {0} | Renews {0} | Аккаунт | да |
| `Account_AutoRenewOff` | Автопродление выключено | Auto-renew is off | Аккаунт | да |
| `Account_AutoRenewNudge` | Включите автопродление, чтобы не прерывать | Turn on auto-renew so it doesn't lapse | Аккаунт | да |
| `Account_RenewFromBalance` | С баланса: {0} | From balance: {0} | Аккаунт | да |
| `Account_RenewWithCard` | Оплатить картой | Pay by card | Аккаунт | да |
| `Account_RenewDone` | Подписка продлена | Subscription renewed | Аккаунт | да |
| `Account_PickPlan` | Купить | Buy | Аккаунт | да |
| `Account_More` | Ещё | More | Аккаунт | да |
| `Account_AddDevices` | Докупить устройства | Add devices | Аккаунт | да |
| `Account_UpgradeTariff` | Улучшить тариф | Upgrade plan | Аккаунт | да |
| `Account_ExtraDevicesN` | +{0} к устройствам | +{0} devices | — | **нет** |
| `Account_DeviceEstimate` | ≈ {0} | ≈ {0} | Аккаунт | да |
| `Account_EstimateNote` | Примерная сумма, точную посчитаем при оплате | Approximate amount, the exact one is set at checkout | Аккаунт | да |
| `Account_PayFromBalance` | С баланса | From balance | Аккаунт | да |
| `Account_PayWithCard` | Картой | By card | Аккаунт | да |
| `Account_DevicesAdded` | Устройства добавлены | Devices added | Аккаунт | да |
| `Account_UpgradeTo` | Улучшить до {0} | Upgrade to {0} | Аккаунт | да |
| `Account_UpgradeQuote` | {0} · +{1} дн. | {0} · +{1} days | Аккаунт | да |
| `Account_UpgradeDone` | Тариф улучшен | Plan upgraded | Аккаунт | да |
| `Account_NoUpgrades` | Вы на максимальном тарифе | You're on the top plan | — | **нет** |
| `Account_BackAction` | Назад | Back | Аккаунт | да |
| `Account_LinkingTitle` | Способы входа | Sign-in methods | Аккаунт | да |
| `Account_LinkEmail` | Почта и пароль | Email & password | Аккаунт | да |
| `Account_WebCabinet` | Сайт departament | The departament website | Аккаунт | да |
| `Account_Linked` | Привязан | Linked | — | **нет** |
| `Account_LinkAction` | Привязать | Link | Аккаунт | да |
| `Account_AddAction` | Добавить | Add | Аккаунт | да |
| `Account_OpenAction` | Открыть | Open | Аккаунт | да |
| `Account_SoonAction` | Скоро | Soon | Аккаунт | да |
| `Account_TgLinkCode` | Код: {0} | Code: {0} | Аккаунт | да |
| `Account_OpenBot` | Открыть бота | Open the bot | Аккаунт | да |
| `Account_TgLinkWaiting` | Ожидаем подтверждения в Telegram… | Waiting for confirmation in Telegram… | — | **нет** |
| `Account_EmailLinkTitle` | Привязать почту | Link an email | Аккаунт | да |
| `Account_EmailLinkHint` | Пришлём ссылку для подтверждения на этот адрес. | We'll email a confirmation link to this address. | Аккаунт | да |
| `Account_EmailSent` | Письмо отправлено на {0} | Email sent to {0} | Аккаунт | да |
| `Account_Send` | Отправить | Send | Аккаунт | да |
| `Account_LinkDone` | Готово | Done | Аккаунт | да |
| `Login_SignIn` | Вход | Sign in | Вход | да |
| `Login_Title` | Вход в departament | Sign in to departament | Вход | да |
| `Login_Subtitle` | Войдите по почте и паролю. Или через Telegram, без пароля. | Sign in with your email and password. Or with Telegram, no password. | Вход | да |
| `Login_Or` | или | or | Вход | да |
| `Login_Email` | Электронная почта | Email | Аккаунт, Вход | да |
| `Login_EmailInvalid` | Введите корректный адрес почты, например name@example.com | Enter a valid email, for example name@example.com | Аккаунт, Вход | да |
| `Login_Password` | Пароль | Password | Вход, Настройки | да |
| `Login_ShowPassword` | Показать пароль | Show password | Вход | да |
| `Login_HidePassword` | Скрыть пароль | Hide password | Вход | да |
| `Login_EnterCode` | Введите 6-значный код из приложения | Enter the 6-digit code from your app | Вход | да |
| `Login_CodeIs6` | Код состоит из 6 цифр | The code is 6 digits | Вход | да |
| `Login_Confirm` | Подтвердить | Confirm | Вход | да |
| `Login_SignUp` | Регистрация на сайте | Sign up on the website | — | **нет** |
| `Login_WaitingConfirm` | Ожидаем подтверждения в Telegram | Waiting for Telegram confirmation | Вход | да |
| `Login_TelegramConfirmHint` | Подтвердите вход в открывшемся приложении и вернитесь сюда. Остальное сделаем сами. | Confirm the sign-in in the app that opened, then come back here. We'll take care of the rest. | Вход | да |
| `Login_OpenTelegram` | Открыть Telegram | Open Telegram | Вход | да |
| `Login_StartOver` | Начать заново | Start over | Вход | да |
| `Login_ChooseAnother` | Другой способ входа | Use another method | Вход | да |
| `Login_TabSignIn` | Вход | Sign in | Вход | да |
| `Login_TabRegister` | Регистрация | Register | Вход | да |
| `Login_TitleRegister` | Создайте аккаунт | Create your account | Вход | да |
| `Login_SubtitleRegister` | Зарегистрируйтесь по почте. Или войдите через Telegram, без пароля. | Register with your email. Or sign in with Telegram, no password. | Вход | да |
| `Login_PasswordRegister` | Пароль (не менее 8 символов) | Password (at least 8 characters) | Вход | да |
| `Login_PasswordHint` | Минимум 8 символов | At least 8 characters | Вход | да |
| `Login_ConfirmPassword` | Повторите пароль | Repeat password | Вход | да |
| `Login_PasswordMismatch` | Пароли не совпадают | The passwords don't match | Вход | да |
| `Login_CreateAccount` | Создать аккаунт | Create account | Вход | да |
| `Login_MagicLink` | Войти по ссылке | Sign in with a link | Вход | да |
| `Login_ForgotPassword` | Забыли пароль? | Forgot password? | Вход | да |
| `Login_ContinueGoogle` | Продолжить с Google | Continue with Google | Вход | да |
| `Login_ComingSoon` | Скоро | Soon | Вход | да |
| `Login_SubmitSignIn` | Войти | Sign in | Вход | да |
| `Login_ByCode` | Войти по коду | Sign in with a code | Вход | да |
| `Login_CodePaste` | Вставьте код из браузера | Paste the code from your browser | Вход | да |
| `Login_SiteHandoff` | Завершаем вход через сайт… | Finishing sign-in via the website… | Вход | да |
| `Login_VerifyTitle` | Подтвердите почту | Confirm your email | Вход | да |
| `Login_VerifyHint` | Мы отправили ссылку на {0}. Откройте её, чтобы подтвердить вход. Остальное сделаем сами. | We've sent a link to {0}. Open it to confirm your sign-in. We'll take care of the rest. | Вход | да |
| `Login_MagicSentTitle` | Ссылка отправлена | Link sent | Вход | да |
| `Login_MagicSentHint` | Если аккаунт с {0} существует, мы отправили ссылку для входа. Откройте её в браузере. | If an account for {0} exists, we've sent a sign-in link. Open it in your browser. | Вход | да |
| `Login_ResetSentTitle` | Письмо отправлено | Email sent | Вход | да |
| `Login_ResetSentHint` | Если аккаунт с {0} существует, мы отправили ссылку для сброса пароля. Задайте новый пароль и вернитесь ко входу. | If an account for {0} exists, we've sent a password-reset link. Set a new password, then return to sign in. | Вход | да |
| `Login_Resend` | Отправить снова | Send again | Вход | да |
| `Login_BackToSignIn` | Вернуться ко входу | Back to sign in | Вход | да |
| `Login_ErrBadCreds` | Неверная почта или пароль. | Incorrect email or password. | Вход | да |
| `Login_ErrLinkExpired` | Ссылка устарела, начните заново | The link has expired, start over | Вход | да |
| `Login_ErrUnavailable` | Вход недоступен | Sign-in is unavailable | Вход | да |
| `Login_ErrEmailTaken` | Аккаунт с этой почтой уже существует | An account with this email already exists | Вход | да |
| `Login_ErrRetry` | Что-то пошло не так. Повторите попытку. | Something went wrong. Try again. | Вход | да |
| `Onboarding_OrSignIn` | Или войдите в свой аккаунт | Or sign in to your account | — | **нет** |
| `Account_SyncTitle` | Добавляем аккаунт | Adding your account | Аккаунт · синхронизация | да |
| `Account_SyncStageAccount` | Проверяем аккаунт | Checking your account | Аккаунт · синхронизация, Аккаунт | да |
| `Account_SyncSubtitle` | Загружаем подписки… | Loading subscriptions… | Аккаунт | да |
| `Account_SyncStageServers` | Обновляем серверы | Refreshing servers | Аккаунт | да |
| `Account_SyncErrorTitle` | Не удалось синхронизировать | Sync didn't finish | Аккаунт · синхронизация | да |
| `Account_SyncErrorHint` | Проверьте подключение и повторите. | Check your connection and try again. | Аккаунт · синхронизация | да |
| `Account_SyncRetry` | Повторить | Try again | Аккаунт · синхронизация | да |
| `Account_SyncReLogin` | Войти заново | Sign in again | Аккаунт · синхронизация | да |
| `Buy_Paid` | Подписка оплачена | Subscription paid | Купить | да |
| `Buy_PaidSubtitle` | Серверы уже добавлены, можно подключаться | Servers are already added, you can connect | Купить | да |
| `Buy_ChoosePlan` | Выберите тариф | Choose a plan | Купить | да |
| `Buy_AdditionalDevices` | Дополнительные устройства | Additional devices | Купить | да |
| `Buy_RemoveDevice` | Убрать устройство | Remove device | Купить | да |
| `Buy_AddDevice` | Добавить устройство | Add device | Купить | да |
| `Buy_Total` | Итого | Total | Купить | да |
| `Buy_Pay` | Купить | Buy | Купить | да |
| `Buy_PaymentMethod` | Способ оплаты | Payment method | Купить | да |
| `Buy_Processing` | Платёж обрабатывается… | Processing payment… | Купить | да |
| `Buy_ErrLoadPlans` | Не удалось загрузить тарифы. Проверьте подключение и повторите. | Couldn't load plans. Check your connection and try again. | Купить | да |
| `Buy_NoPlans` | Тарифы недоступны | No plans available | Купить | да |
| `Buy_ChoosePeriod` | Выберите срок подписки | Choose a subscription period | Купить | да |
| `Buy_NoPaymentMethods` | Способы оплаты недоступны | No payment methods available | Аккаунт, Купить | да |
| `Buy_FromBalance` | С баланса: {0} | From balance: {0} | Купить | да |
| `Buy_PaymentError` | Ошибка оплаты | Payment error | Купить | да |
| `Buy_DevicesTraffic` | Устройства: {0} · Трафик: {1} | Devices: {0} · Traffic: {1} | Купить | да |
| `Devices_Subtitle` | Устройства, подключённые к вашей подписке | Devices connected to your subscription | Устройства | да |
| `Devices_ThisDevice` | Это устройство | This device | Устройства | да |
| `Devices_Unlink` | Отвязать устройство | Unlink device | Устройства | да |
| `Devices_Empty` | Устройств пока нет | No devices yet | Устройства | да |
| `Devices_EmptyHint` | Устройства появятся после первого подключения. | Devices appear after your first connection. | Устройства | да |
| `Devices_NoSub` | Активной подписки нет | No active subscription | Устройства | да |
| `Devices_NoSubHint` | Купите тариф, чтобы управлять устройствами. | Buy a plan to manage your devices. | Устройства | да |
| `Devices_GoToAccount` | Перейти в аккаунт | Go to account | Устройства | да |
| `Devices_UnlinkConfirm` | Отвязать устройство? | Unlink device? | Устройства | да |
| `Devices_UnlinkShort` | Отвязать | Unlink | Устройства | да |
| `Devices_UnlinkBody` | Устройство «{0}» будет отключено от подписки. | Device \"{0}\" will be disconnected from your subscription. | Устройства | да |
| `Devices_UnlinkFailed` | Не удалось отвязать устройство. Повторите попытку позже. | Couldn't unlink the device. Try again later. | Устройства | да |
| `Devices_Unlinked` | Устройство отвязано | Device unlinked | Устройства | да |
| `Devices_ErrLoad` | Не удалось загрузить устройства. Проверьте подключение и повторите. | Couldn't load devices. Check your connection and try again. | Устройства | да |
| `Devices_PlatformActive` | {0} · Активно: {1} | {0} · Active: {1} | Устройства | да |
| `Devices_Active` | Активно: {0} | Active: {0} | Устройства | да |
| `Devices_Id` | ID: {0} | ID: {0} | Устройства | да |
| `Devices_Unknown` | Неизвестное устройство | Unknown device | Устройства | да |
| `History_Empty` | Платежей пока нет | No payments yet | История платежей | да |
| `History_EmptyHint` | Здесь появится история покупок и продлений. | Your purchases and renewals will appear here. | — | **нет** |
| `History_ErrLoad` | Не удалось загрузить историю платежей | Couldn't load payment history | История платежей | да |
| `History_StatusPaid` | Оплачено | Paid | История платежей | да |
| `History_StatusProcessing` | В обработке | Processing | История платежей | да |
| `History_StatusFailed` | Ошибка | Failed | История платежей | да |
| `History_StatusCanceled` | Отменён | Canceled | История платежей | да |
| `History_SampleRenewal` | Продление подписки | Subscription renewal | История платежей | да |
| `History_SampleTopUp` | Пополнение баланса | Balance top-up | История платежей | да |
| `History_SamplePlan` | Тариф Base | Base plan | История платежей | да |
| `Common_Back` | Назад | Back | Настройки · о приложении, Настройки · резервная копия, Купить, Устройства, Настройки · DNS, Настройки · файлы ресурсов, Вход, История платежей, Настройки · по приложениям, Настройки · пинг, Настройки · провайдеры, Настройки · маршрутизация, Настройки · схемы URL | да |
| `Common_Retry` | Повторить | Retry | Аккаунт, Купить, Устройства, История платежей | да |
| `Common_Cancel` | Отмена | Cancel | Устройства | да |
| `Common_Delete` | Удалить | Delete | Серверы | да |
| `Common_Edit` | Изменить | Edit | Серверы | да |
| `Common_Add` | Добавить | Add | Серверы (компакт) | да |
| `Common_Copy` | Копировать | Copy | Настройки · провайдеры, Настройки · схемы URL | да |
| `Common_Open` | Открыть | Open | Настройки · о приложении | да |
| `Common_Refresh` | Обновить | Refresh | Настройки · по приложениям | да |
| `Common_Manage` | Управление | Manage | Аккаунт | да |
| `Common_AddSubscription` | Добавить провайдера | Add provider | Главная · щит | да |
| `Common_AddFromClipboard` | Добавить из буфера обмена | Add from clipboard | Серверы (компакт), Главная · щит, Онбординг | да |
| `Common_AddViaQr` | Добавить по QR-коду | Add via QR code | Серверы (компакт), Главная · щит, Онбординг | да |
| `Common_UpdateSubscription` | Обновить провайдера | Update provider | Серверы (компакт), Серверы · плашка провайдера | да |
| `Common_TestLatency` | Проверить задержку | Test latency | Common/LocExtension, Серверы (компакт), Серверы, Серверы · плашка провайдера | да |
| `Common_SignInTelegram` | Войти через Telegram | Sign in with Telegram | Аккаунт, Вход, Онбординг | да |
| `Common_SignInWebsite` | Войти через сайт | Sign in via website | Аккаунт, Вход, Онбординг, Настройки · схемы URL | да |
| `Common_BuySubscription` | Купить подписку | Buy subscription | Аккаунт, Купить, История платежей | да |
| `Common_PaymentHistory` | История платежей | Payment history | Аккаунт, История платежей | да |
| `Common_Copied` | Скопировано | Copied | Аккаунт | да |
| `Common_Default` | По умолчанию | Default | Настройки · DNS, Настройки | да |
| `Common_Custom` | Свой | Custom | Настройки · DNS, Настройки | да |
| `Common_On` | Вкл | On | Настройки | да |
| `Common_Off` | Выкл | Off | Настройки · провайдеры, Настройки | да |
| `Common_SearchPlaceholder` | Поиск… | Search… | Настройки · по приложениям | да |
| `Common_CouldntConnect` | Не удалось подключиться | Couldn't connect | Главная · щит | да |
| `Common_CouldntLoad` | Не удалось загрузить | Couldn't load | Аккаунт | да |
| `Common_CouldntOpenPayment` | Не удалось открыть страницу оплаты | Couldn't open the payment page | Аккаунт, Купить | да |
| `Common_CompletePaymentInBrowser` | Завершите оплату в браузере | Complete the payment in your browser | Аккаунт, Купить | да |
| `Common_ServiceUnavailable` | Сервис временно недоступен | Service is temporarily unavailable | Аккаунт, Устройства, Вход | да |
| `Common_NetworkError` | Нет подключения к интернету. Проверьте сеть и повторите. | No internet connection. Check your network and try again. | Аккаунт, Устройства, Вход, История платежей | да |
| `Common_SignInRequired` | Требуется вход в аккаунт | Sign-in required | Аккаунт, Устройства | да |
| `Common_TooManyRequests` | Слишком много запросов. Попробуйте позже | Too many requests. Try again later | Аккаунт, Устройства | да |
| `Common_Timeout` | Превышено время ожидания | Request timed out | Аккаунт, Устройства | да |
| `Common_SomethingWrong` | Что-то пошло не так. Повторите попытку. | Something went wrong. Try again. | Аккаунт | да |
| `Common_ByteUnits` | Б,КБ,МБ,ГБ,ТБ,ПБ | B,KB,MB,GB,TB,PB | Аккаунт, Купить, Серверы · плашка провайдера | да |
| `Common_ZeroBytes` | 0 Б | 0 B | Аккаунт, Купить, Серверы · плашка провайдера | да |
| `Common_HoursShort` | {0} ч. | {0} h | Настройки · провайдеры, Настройки, Серверы · плашка провайдера | да |
| `Common_MinutesShort` | {0} мин | {0} min | Серверы · плашка провайдера | да |
| `Common_DaysShort` | {0} дн. | {0} days | Купить | да |
| `Common_ServersPlural` | "сервер", "сервера", "серверов" | "server", "servers" | Common/ProfileDisplay, Главная | да |
| `Common_ProvidersPlural` | "провайдер", "провайдера", "провайдеров" | "provider", "providers" | Common/ProfileDisplay, Главная | да |
| `Home_NotConnected` | Не подключено | Not connected | Common/LocExtension, Главная · щит | да |
| `Home_ChooseServer` | Выберите сервер | Choose a server | Главная · щит | да |
| `Home_RetryHint` | Нажмите, чтобы повторить | Tap to retry | Главная · щит | да |
| `Home_Welcome` | Добро пожаловать | Welcome | Главная · щит | да |
| `Home_NoSubs` | Нет серверов | No servers | Главная · щит | да |
| `Home_NoSubsHint` | Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | Add a provider or scan a QR code to get servers. | Главная · щит | да |
| `Onboarding_Title` | Добавьте провайдера | Add a provider | Онбординг | да |
| `Onboarding_Subtitle` | Отсканируйте QR-код или вставьте ссылку из буфера. Доступ появится сразу. | Scan a QR code or paste a link from the clipboard. Access appears right away. | Онбординг | да |
| `Onboarding_OrSignInShort` | или войдите в аккаунт | or sign in to your account | Онбординг | да |
| `Home_MyServers` | Мои серверы | My servers | Главная | да |
| `Home_ServersProvidersMeta` | {0} · {1} | {0} · {1} | Главная | да |
| `Home_TunUnavailable` | Режим «весь трафик» недоступен без прав администратора | Whole-traffic mode isn't available without administrator rights | Главная (компакт), Главная | да |
| `Home_RestartElevated` | Перезапустить с правами | Restart as administrator | Главная (компакт), Главная | да |
| `Home_ManageAccount` | Управление аккаунтом | Manage account | Главная · чип аккаунта | да |
| `Servers_Title` | Серверы | Servers | Серверы (компакт) | да |
| `Servers_MakeDefault` | Сделать основным | Make default | Серверы | да |
| `Servers_Duplicate` | Дублировать | Duplicate | Серверы | да |
| `Servers_ShareQr` | Поделиться · QR-код | Share · QR code | Серверы | да |
| `Servers_ShareLink` | Поделиться · ссылка | Share · link | Серверы | да |
| `Servers_Empty` | Нет серверов | No servers | Серверы | да |
| `Servers_EmptyHint` | Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | Add a provider or scan a QR code to get servers. | Серверы | да |
| `Servers_SearchPlaceholder` | Поиск серверов… | Search servers… | Common/LocExtension, Серверы (компакт) | да |
| `Sub_CollapseServers` | Свернуть серверы | Collapse servers | Серверы · плашка провайдера | да |
| `Sub_Pin` | Закрепить | Pin | Серверы · плашка провайдера | да |
| `Sub_Delete` | Удалить провайдера | Delete provider | Серверы · плашка провайдера | да |
| `Sub_DeleteConfirm` | Удалить провайдера и его серверы? | Delete the provider and its servers? | Серверы · плашка провайдера | да |
| `Sub_OpenSupport` | Открыть поддержку | Open support | Серверы · плашка провайдера | да |
| `Sub_Support` | Поддержка | Support | Серверы · плашка провайдера | да |
| `Sub_Expired` | Истекла | Expired | Серверы · плашка провайдера | да |
| `Sub_Until` | до {0:dd.MM.yyyy} | until {0:dd.MM.yyyy} | Серверы · плашка провайдера | да |
| `Sub_AutoUpdate` | Автообновление · {0} | Auto-update · {0} | Серверы · плашка провайдера | да |
| `Settings_SecConnection` | Подключение | Connection | Настройки | да |
| `Settings_Mode` | Режим | Mode | Настройки | да |
| `Settings_ModeProxy` | Прокси | Proxy | Настройки | да |
| `Settings_PerApp` | Прокси по приложениям | Per-app proxy | Настройки · по приложениям, Настройки | да |
| `Settings_BypassLan` | Обход локальной сети | Bypass local network | Настройки | да |
| `Settings_BypassLanHint` | Прямой доступ к устройствам в локальной сети | Direct access to devices on the local network | Настройки | да |
| `Settings_Ipv6Hint` | Включить IPv6-адресацию в туннеле | Enable IPv6 addressing in the tunnel | Настройки | да |
| `Settings_Ping` | Пинг | Ping | Настройки · пинг, Настройки | да |
| `Settings_LocalProxy` | Локальный прокси | Local proxy | Настройки | да |
| `Settings_LocalProxyHint` | Порт, имя пользователя и пароль SOCKS5 | Port, username and password for SOCKS5 | Настройки | да |
| `Settings_Port` | Порт | Port | Настройки | да |
| `Settings_Socks5Auth` | SOCKS5-авторизация | SOCKS5 authentication | Настройки | да |
| `Settings_Username` | Имя пользователя | Username | Настройки | да |
| `Settings_NotSet` | Не задан | Not set | Настройки | да |
| `Settings_Socks5Hint` | Адрес: 127.0.0.1. Пустые имя пользователя и пароль отключают SOCKS5-авторизацию. | Address: 127.0.0.1. Empty username and password disable SOCKS5 authentication. | Настройки | да |
| `Settings_SecBypass` | Обход блокировок | Bypass censorship | Настройки | да |
| `Settings_Mux` | Мультиплексирование (Mux) | Multiplexing (Mux) | Настройки | да |
| `Settings_MuxHint` | Объединяет запросы в один канал | Combines requests into a single channel | Настройки | да |
| `Settings_MuxCount` | Число подключений Mux | Mux connection count | Настройки | да |
| `Settings_Fragment` | Фрагментация пакетов | Packet fragmentation | Настройки | да |
| `Settings_FragmentHint` | Разбивает TLS-рукопожатие против DPI | Splits the TLS handshake to defeat DPI | Настройки | да |
| `Settings_SecPerformance` | Производительность | Performance | Настройки | да |
| `Settings_LiteMode` | Облегчённый режим | Lite mode | Настройки | да |
| `Settings_LiteModeHint` | Отключает анимации, снижает нагрузку | Disables animations, reduces load | Настройки | да |
| `Settings_SecInterface` | Интерфейс | Interface | Настройки | да |
| `Settings_Appearance` | Оформление | Appearance | Настройки | да |
| `Settings_Monochrome` | Чёрно-белый режим | Black and white | Настройки | да |
| `Settings_MonochromeHint` | Поверх тёмной или светлой темы | Over the dark or light theme | Настройки | да |
| `Settings_Language` | Язык | Language | Настройки | да |
| `Settings_Autostart` | Запуск при загрузке | Launch at startup | Настройки | да |
| `Settings_AutostartHint` | Открывать departament при входе в систему | Open departament when you sign in | Настройки | да |
| `Settings_SecSubscription` | Подписка | Subscription | Настройки | да |
| `Settings_SubAutoUpdate` | Автообновление провайдеров | Auto-update providers | Настройки | да |
| `Settings_Routing` | Маршрутизация | Routing | Настройки · маршрутизация, Настройки | да |
| `Settings_GeoFiles` | Файлы ресурсов | Resource files | Настройки · файлы ресурсов, Настройки | да |
| `Settings_About` | О приложении | About | Настройки · о приложении, Настройки | да |
| `Settings_Backup` | Резервное копирование | Backup | Настройки · резервная копия, Настройки | да |
| `Settings_UrlSchemes` | Схемы URL-адресов | URL schemes | Настройки, Настройки · схемы URL | да |
| `Settings_UrlSchemesHint` | Быстрые команды depv:// | Quick depv:// commands | Настройки | да |
| `Settings_PerAppExcept` | кроме | except | Настройки | да |
| `Settings_PerAppOnly` | только | only | Настройки | да |
| `Settings_ThemeLight` | Светлая | Light | Настройки | да |
| `Settings_ThemeDark` | Тёмная | Dark | Настройки | да |
| `Settings_LangRussian` | Русский | Russian | Настройки | да |
| `Dns_Intro` | DNS-сервер, через который приложение разрешает домены при подключении. По умолчанию используется встроенный резолвер. | The DNS server the app uses to resolve domains when connecting. The built-in resolver is used by default. | Настройки · DNS | да |
| `Dns_Provider` | Провайдер | Provider | Настройки · DNS | да |
| `Dns_CustomAddress` | Свой DNS-адрес | Custom DNS address | Настройки · DNS | да |
| `Dns_CustomHint` | DoH-адрес (https://…/dns-query), DoT или обычный IP: 1.1.1.1 | DoH address (https://…/dns-query), DoT, or a plain IP: 1.1.1.1 | Настройки · DNS | да |
| `Dns_Advanced` | Дополнительно | Advanced | Настройки · DNS | да |
| `Dns_AdvancedHint` | Ускоряет подключение, отвечая на DNS-запросы локально (sing-box) | Speeds up connections by answering DNS queries locally (sing-box) | Настройки · DNS | да |
| `Routing_Intro` | Наборы правил определяют, какой трафик идёт через VPN, а какой напрямую. Выберите активный набор. | Rule sets decide which traffic goes through the VPN and which goes direct. Pick the active set. | Настройки · маршрутизация | да |
| `Routing_RuleSets` | Наборы правил | Rule sets | Настройки · маршрутизация | да |
| `Routing_RulesCount` | {0} правил | {0} rules | Настройки · маршрутизация | да |
| `Routing_Active` | Активен | Active | Настройки · маршрутизация | да |
| `Routing_DomainStrategy` | Стратегия доменов | Domain strategy | Настройки · маршрутизация | да |
| `Routing_DomainResolution` | Разрешение доменов | Domain resolution | Настройки · маршрутизация | да |
| `Routing_DomainHint` | Как ядро сопоставляет домены с правилами | How the core matches domains against rules | Настройки · маршрутизация | да |
| `Routing_Maintenance` | Обслуживание | Maintenance | Настройки · маршрутизация | да |
| `Routing_DefaultRules` | Стандартные правила | Default rules | Настройки · маршрутизация | да |
| `Routing_DefaultRulesHint` | Пересоздать встроенные наборы правил | Rebuild the built-in rule sets | Настройки · маршрутизация | да |
| `Routing_Reset` | Сбросить | Reset | Настройки · маршрутизация | да |
| `Routing_DsAsIs` | Как есть | As is | Настройки · маршрутизация | да |
| `Routing_DsIpIfNonMatch` | IP при несовпадении | IP if no match | Настройки · маршрутизация | да |
| `Routing_DsIpOnDemand` | IP по запросу | IP on demand | Настройки · маршрутизация | да |
| `PerApp_SplitTunnel` | Раздельное туннелирование | Split tunneling | Настройки · по приложениям | да |
| `PerApp_SplitTunnelHint` | Выберите, какие программы идут через VPN | Choose which apps go through the VPN | Настройки · по приложениям | да |
| `PerApp_BypassHint` | Выбранные идут напрямую, минуя VPN | Selected apps go direct, bypassing the VPN | Настройки · по приложениям | да |
| `PerApp_OnlyHint` | Только выбранные идут через VPN | Only selected apps go through the VPN | Настройки · по приложениям | да |
| `PerApp_Apps` | Приложения | Apps | Настройки · по приложениям | да |
| `PerApp_AddExe` | Добавить .exe | Add .exe | Настройки · по приложениям | да |
| `PerApp_TunHint` | Работает в режиме TUN (sing-box). Правила применяются при следующем подключении. | Works in TUN mode (sing-box). Rules apply on the next connection. | Настройки · по приложениям | да |
| `PerApp_ProgramFileType` | Программа | Program | Настройки · по приложениям | да |
| `Ping_Intro` | Как измерять задержку серверов. Ниже задаются адрес и тайм-аут проверки. | How to measure server latency. The test address and timeout are set below. | Настройки · пинг | да |
| `Ping_RealTitle` | Реальная задержка | Real latency | Настройки · пинг | да |
| `Ping_RealHint` | Через ядро, как при подключении | Through the core, as when connected | Настройки · пинг | да |
| `Ping_TcpHint` | TCP-подключение к серверу | TCP connection to the server | Настройки · пинг | да |
| `Ping_TestAddress` | Адрес проверки задержки | Latency test address | Настройки · пинг | да |
| `Ping_Timeout` | Тайм-аут проверки, сек | Test timeout, sec | Настройки · пинг | да |
| `Ping_Real` | Реальная | Real | Настройки | да |
| `Geo_Intro` | Базы geoip и geosite нужны для маршрутизации по странам и доменам. Обновляются с GitHub. | The geoip and geosite databases are used for routing by country and domain. Updated from GitHub. | Настройки · файлы ресурсов | да |
| `Geo_UpdateNow` | Обновить сейчас | Update now | Настройки · файлы ресурсов | да |
| `Geo_NotDownloaded` | Не загружен | Not downloaded | Настройки · файлы ресурсов | да |
| `Geo_SizeUpdated` | {0} МБ · обновлён {1} | {0} MB · updated {1} | Настройки · файлы ресурсов | да |
| `Geo_Updating` | Обновление… | Updating… | Настройки · файлы ресурсов | да |
| `Geo_Downloading` | Загрузка баз… | Downloading databases… | Настройки · файлы ресурсов | да |
| `Geo_Done` | Базы обновлены. | Databases updated. | Настройки · файлы ресурсов | да |
| `Geo_Failed` | Не удалось обновить базы. Проверьте сеть и повторите.  | Couldn't update the databases. Check your network and try again.  | Настройки · файлы ресурсов | да |
| `About_Version` | Версия | Version | Настройки · о приложении | да |
| `About_VersionValue` | Версия {0} | Version {0} | Настройки · о приложении | да |
| `About_TitleVersion` | departament · Версия {0} | departament · Version {0} | Настройки · о приложении | да |
| `About_OpenSite` | Открыть сайт | Open website | Настройки · о приложении | да |
| `About_TelegramBot` | Telegram-бот | Telegram bot | Настройки · о приложении | да |
| `About_Details` | Сведения | Details | Настройки · о приложении | да |
| `About_CopyDetails` | Копировать сведения | Copy details | Настройки · о приложении | да |
| `About_SystemInfo` | ОС: {0}\nАрхитектура: {1}\n.NET: {2} | OS: {0}\nArchitecture: {1}\n.NET: {2} | Настройки · о приложении | да |
| `Backup_Intro` | Сохраните все настройки, провайдеров и серверы в один .zip-файл или восстановите их из ранее сохранённой копии. | Save all settings, providers, and servers to a single .zip file, or restore them from a previous backup. | Настройки · резервная копия | да |
| `Backup_Export` | Экспорт | Export | Настройки · резервная копия | да |
| `Backup_ExportHint` | Сохранить копию в файл | Save a backup to a file | Настройки · резервная копия | да |
| `Backup_Save` | Сохранить… | Save… | Настройки · резервная копия | да |
| `Backup_Import` | Импорт | Import | Настройки · резервная копия | да |
| `Backup_ImportHint` | Восстановить из файла, приложение перезапустится | Restore from a file, the app will restart | Настройки · резервная копия | да |
| `Backup_Restore` | Восстановить… | Restore… | Настройки · резервная копия | да |
| `Backup_Saving` | Сохранение… | Saving… | Настройки · резервная копия | да |
| `Backup_Saved` | Копия сохранена: {0} | Backup saved: {0} | Настройки · резервная копия | да |
| `Backup_SaveFailed` | Не удалось сохранить копию. Выберите другую папку и повторите. | Couldn't save the backup. Pick another folder and try again. | Настройки · резервная копия | да |
| `Backup_ExportError` | Не удалось сохранить копию. Выберите другую папку и повторите.  | Couldn't save the backup. Pick another folder and try again.  | Настройки · резервная копия | да |
| `Backup_Restoring` | Восстановление… Приложение перезапустится. | Restoring… The app will restart. | Настройки · резервная копия | да |
| `Backup_ImportError` | Не удалось восстановить из файла. Выберите другой файл и повторите.  | Couldn't restore from that file. Pick another file and try again.  | Настройки · резервная копия | да |
| `UrlSchemes_Registration` | Регистрация схемы depv:// | depv:// scheme registration | Настройки · схемы URL | да |
| `UrlSchemes_Register` | Зарегистрировать | Register | Настройки · схемы URL | да |
| `UrlSchemes_Remove` | Убрать | Remove | Настройки · схемы URL | да |
| `UrlSchemes_Hint` | Нажмите на схему, чтобы скопировать. Используйте их в ярлыках, скриптах или других приложениях. | Tap a scheme to copy it. Use them in shortcuts, scripts, or other apps. | Настройки · схемы URL | да |
| `UrlSchemes_StartTunnel` | Запустить туннель | Start the tunnel | Настройки · схемы URL | да |
| `UrlSchemes_OpenApp` | Открыть приложение | Open the app | Настройки · схемы URL | да |
| `UrlSchemes_Stop` | Отключиться | Disconnect | Настройки · схемы URL | да |
| `UrlSchemes_Close` | Закрыть приложение | Close the app | — | **нет** |
| `UrlSchemes_Toggle` | Переключить подключение | Toggle the connection | Настройки · схемы URL | да |
| `UrlSchemes_Import` | Импорт (автоопределение) | Import (auto-detect) | Настройки · схемы URL | да |
| `UrlSchemes_AddByUrl` | Добавить по URL | Add by URL | Настройки · схемы URL | да |
| `UrlSchemes_WindowsOnly` | Регистрация схемы доступна только на Windows. | Scheme registration is available on Windows only. | Настройки · схемы URL | да |
| `UrlSchemes_Registered` | Схема зарегистрирована. Ссылки depv:// открывают departament. | Scheme registered. depv:// links open departament. | Настройки · схемы URL | да |
| `UrlSchemes_NotRegistered` | Схема не зарегистрирована. | Scheme not registered. | Настройки · схемы URL | да |
| `UrlSchemes_NoPath` | Не удалось определить путь к программе. Переустановите departament и повторите. | Couldn't determine the app's path. Reinstall departament and try again. | Настройки · схемы URL | да |
| `UrlSchemes_RegisterFailed` | Не удалось зарегистрировать схему. Запустите departament от имени администратора и повторите.  | Couldn't register the scheme. Run departament as administrator and try again.  | Настройки · схемы URL | да |
| `UrlSchemes_RemovedOk` | Схема удалена. | Scheme removed. | Настройки · схемы URL | да |
| `UrlSchemes_RemoveFailed` | Не удалось убрать схему. Запустите departament от имени администратора и повторите.  | Couldn't remove the scheme. Run departament as administrator and try again.  | Настройки · схемы URL | да |
| `Provider_Title` | Настройки провайдеров | Provider settings | Настройки · провайдеры | да |
| `Provider_SecUpdates` | Обновление | Updates | Настройки · провайдеры | да |
| `Provider_AutoUpdate` | Автообновление | Auto-update | Настройки · провайдеры | да |
| `Provider_AutoUpdateHint` | Автоматически обновлять серверы провайдеров | Refresh provider servers automatically | Настройки · провайдеры | да |
| `Provider_Interval` | Интервал обновления | Update interval | Настройки · провайдеры | да |
| `Provider_SecNetwork` | Сеть | Network | Настройки · провайдеры | да |
| `Provider_Hwid` | Идентификатор устройства (HWID) | Device ID (HWID) | Настройки · провайдеры | да |
| `Provider_UserAgentHint` | Отправляется ядром на исходящих подключениях | Sent by the core on outbound connections | Настройки · провайдеры | да |
| `Tray_Restart` | Перезапустить | Restart | Трей | да |
| `Tray_Connect` | Подключить | Connect | Трей | да |
| `Tray_Disconnect` | Отключить | Disconnect | Трей | да |
| `Tray_Show` | Показать | Show | Трей | да |
| `Tray_Exit` | Выход | Exit | Трей | да |
| `Nav_Home` | Главная | Home | Оболочка · нижняя навигация, Common/LocExtension, Оболочка | да |
| `Nav_Settings` | Настройки | Settings | Оболочка · нижняя навигация, Оболочка | да |
| `Nav_Account` | Аккаунт | Account | Оболочка · нижняя навигация, Оболочка | да |
| `Nav_CollapsePanel` | Свернуть панель | Collapse panel | Оболочка | да |
| `Nav_ExpandPanel` | Развернуть панель | Expand panel | Оболочка | да |
| `Status_Connecting` | Подключение… | Connecting… | Главная · щит | да |
| `Status_Disconnected` | Отключено | Disconnected | — | **нет** |
| `Status_Connected` | Подключено | Connected | Главная · щит | да |
| `Status_ConnectedTo` | Подключено · {0} | Connected · {0} | — | **нет** |

---

## 10. Fix list, ordered

**P1 — a user is harmed today**

1. **Give the notice pipeline a surface.** `SendMsgViewRequested` has no live subscriber, so ~361
   strings are discarded (§2.3). Either host a message surface or make `DelegateSnackMsg` render
   an inline, non-floating status line on the active screen. Until this lands, "Скопировано",
   "Устройство отвязано", "Подписка продлена", "Тариф улучшен", "Устройства добавлены" and every
   failure in those flows are invisible.
2. **Label the login fields.** Add persistent `Subtitle` labels above email / password /
   repeat-password in `LoginView.axaml` (§7.1 ##1-3), matching the pattern already used in
   `SettingsView.axaml:481-509`.
3. **Add «Привязать Telegram»** as an `L` key and use it on the Account linking row (§9.3, §0.4.9).
   Add the §9.5 «Telegram не привязан» trio.
4. **Complete the 11 recovery-free error strings** in §5.7, adopting the Android wording where
   §9.4 or Android already has the complete sentence.
5. **Drop `+ ex.Message` at the 5 sites** in §5.7 and remove the 5 trailing spaces from the `L`
   values that exist only to accommodate it.
6. **Localize the speed readout.** `HomeViewModel` / `ConnectHeroView` must use `Common_ByteUnits`
   and a comma decimal instead of `Utils.HumanFy`; replace the six `"0 KB/s"` literals with
   `Common_ZeroBytes` + a unit. Decide with Android whether the product shows МБ/с or Мбит/с and
   write it once (§9.2 currently says Мбит/с; both clients show MB/s).
7. **Ship the search-empty state** on Серверы («Ничего не найдено» / «Попробуйте другой запрос.» /
   «Сбросить поиск») — §9.5.
8. **Ship the offline state** on both clients — §9.6, three strings, currently zero.
9. **Fix the Устройства verb split**: PC's «Отвязать» is correct; Android's «Удалить» must change
   (Android wave).
10. **Fix `Account_Linked`**: the linking rows signal "linked" with a check glyph alone. §14.7 —
    colour/icon is never the only signal. Render the existing «Привязан» string.

**P2 — correctness and consistency**

11. Move `SettingsView.axaml:792` («Масштаб интерфейса») and `:776` (the Ctrl tooltip) into `L`;
    strip the two em-dashes and the U+2212 from the tooltip.
12. Replace the five `Text="—"` value placeholders with real copy (`Geo_NotDownloaded` pattern).
13. Label `depv://close` with `UrlSchemes_Close`, not `UrlSchemes_Stop`
    (`UrlSchemesPage.axaml.cs:34`).
14. Render `History_EmptyHint` on the payments empty state and drop the «Купить подписку» button
    there — §9.5 specifies no action for that state.
15. Replace `"NONE"` and `"CUSTOM"` in `ProfileDisplay.cs` with `L` keys.
16. Change the `INCY/1.0` watermark to a `departament`-flavoured example.
17. Collapse the 14 duplicate groups and the 4 competing-wording groups in §5.8 to one key each.
18. Delete or wire the 14 dead `L` keys in §5.11.
19. Move the three hardcoded Russian literals out of `StatusBarView.axaml` into `L` before that
    view is ever un-hidden.
20. Decide the fate of `ProviderSettingsPage`: either add its settings row (Android has
    `settings_provider` + `_sub`) or delete the page and its 9 strings.
21. Reconcile the 19 P2 divergences in §6.2, string by string, one owner per concept.
22. Give `Servers_SearchPlaceholder` / `Common_SearchPlaceholder` a single owner.
23. Clean the 11 em/en-dashes and 5 three-dot ellipses in `ResUI.ru.resx`, or accept them as
    upstream debt and record that decision.

**P3 — hygiene**

24. Update the §9.7 baseline in `00-rules.md`: the "44 desktop hits" figure is stale; the live
    numbers are 0 in `L.*.cs`, 6 in AXAML, 11 in `ResUI.ru`.
25. Decide what happens to the ~346 unreachable `ResUI` keys and the 16 upstream windows behind
    them. Leaving them wired means one accidental navigation re-introduces upstream's voice
    wholesale.
26. `L.SetLanguage` sets `Thread.CurrentUICulture` on the calling thread only; `ResUI` strings
    resolved on background threads keep the process default culture, so the eight reachable
    `ResUI` strings (§4.2) can disagree with the rest of the UI after a language switch.

---

## 11. Method and reproducibility

```bash
# L table: keys, values, definition sites
grep -rn 'Add(' /home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.*.cs

# resx coverage (EN vs RU), Latin-only RU values, reachability
python3 - <<'EOF'
import xml.etree.ElementTree as ET
d=lambda p:{e.get('name'):(e.find('value').text or '') for e in ET.parse(p).getroot().findall('data')}
en=d('/home/user/v2rayN/v2rayN/ServiceLib/Resx/ResUI.resx')
ru=d('/home/user/v2rayN/v2rayN/ServiceLib/Resx/ResUI.ru.resx')
print(len(en), len(ru), [k for k in en if k not in ru], [k for k in ru if not ru[k].strip()])
EOF

# literals in AXAML
grep -rnE '(Text|Content|Header|Watermark|ToolTip\.Tip|Title|PlaceholderText)="[^{][^"]*"' \
  /home/user/v2rayN/v2rayN/v2rayN.Desktop --include=*.axaml

# every Watermark (the placeholder-as-label audit)
grep -rn 'Watermark=' /home/user/v2rayN/v2rayN/v2rayN.Desktop --include=*.axaml

# §9.7 dash / ellipsis enforcement
grep -rn -e '—' -e '–' /home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.*.cs
grep -rn -e '—' -e '–' -e '\.\.\.' /home/user/v2rayN/v2rayN/ServiceLib/Resx/ResUI.ru.resx

# the dead notice channel
grep -rn 'SendMsgViewRequested' /home/user/v2rayN/v2rayN --include=*.cs
```

Screen attribution in §9 is computed by scanning every `.axaml` / `.cs` outside `Common/L.*.cs`
for each key as a whole word, then mapping the file to its screen. A key marked **нет** appears in
no file outside the `L` table.
