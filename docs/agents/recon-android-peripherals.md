# Recon — Android peripheral surfaces and app plumbing

**Scope:** `receiver/**`, `service/QSTileService.kt`, `tv/**`, `ui/Sc{Start,Stop,Switch,Scanner}Activity`,
`ui/TaskerActivity.kt`, `ui/UrlSchemeActivity.kt` + `ui/UrlSchemeListActivity.kt`, `ui/BackupActivity.kt`,
`ui/LogcatActivity.kt`, `ui/AboutActivity.kt`, `ui/CheckUpdateActivity.kt`, `ui/SettingsActivity.kt`,
`ui/ProviderSettingsActivity.kt`, `ui/PerAppProxyActivity.kt`, `ui/RoutingSettingActivity.kt`,
`ui/SubSettingActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `proguard-rules.pro`,
notification construction.

**Repo state audited:** `/home/user/dp`, HEAD `b43d94f`, 2026-07-26T13:14Z.

> **Caveat — the working tree moved while this audit ran.** Other agents were editing the same
> checkout. `V2rayNG/app/src/main/java/com/v2ray/ang/ui/ProviderSettingsActivity.kt` changed under
> me mid-read (an earlier state referenced `toggleBool(...)` with no such function defined and
> unqualified `PREF_SERVER_SORT_ORDER` / `PREF_NOTIFY_ON_UPDATE` constants — i.e. it would not
> compile; the current working-tree version defines `toggleBool` at lines 234-242 and qualifies the
> constants). Everything below was re-verified against the tree at the timestamp above unless the
> finding says otherwise. Re-verify line numbers before acting on them.

---

## 1. What each surface actually does

### 1.1 `receiver/WidgetProvider.kt` — home-screen / lockscreen toggle widget
- `onUpdate` (`WidgetProvider.kt:23-26`) renders `R.layout.widget_switch` and paints it from
  `CoreServiceManager.isRunning()`.
- Click handling (`WidgetProvider.kt:67-94`): on `BROADCAST_ACTION_WIDGET_CLICK` it stops the
  service if running, else calls `CoreServiceManager.startVServiceFromToggle(context)`.
- State refresh: it also listens for `BROADCAST_ACTION_ACTIVITY` and repaints on
  `MSG_STATE_RUNNING / START_SUCCESS` (active) and `NOT_RUNNING / START_FAILURE / STOP_SUCCESS`
  (inactive) — `WidgetProvider.kt:75-93`.
- Declared at `AndroidManifest.xml:268-280`, exported, running in `:RunSoLibV2RayDaemon` (same
  process as the core), with intent filters for `${applicationId}.action.widget.click` and
  `${applicationId}.action.activity`. These match `AppConfig.BROADCAST_ACTION_*`
  (`AppConfig.kt:114-116`), which are derived from `BuildConfig.APPLICATION_ID` (`AppConfig.kt:7`),
  so the action strings are correct for every flavor.

### 1.2 `receiver/BootReceiver.kt` — start on boot
- `BootReceiver.kt:21-42`: guards on `ACTION_BOOT_COMPLETED`, `MmkvManager.decodeStartOnBoot()`, and
  a non-empty selected server, then `CoreServiceManager.startVService(context)` +
  `SubscriptionUpdater.sync(context)`.
- Manifest `AndroidManifest.xml:281-288`; permission `RECEIVE_BOOT_COMPLETED` present at
  `AndroidManifest.xml:41`.

### 1.3 `receiver/TaskerReceiver.kt` + `ui/TaskerActivity.kt` — Locale/Tasker plugin
- Receiver (`TaskerReceiver.kt:21-41`) reads the Locale bundle, and on `switch == true` starts
  either the toggle path (`TASKER_DEFAULT_GUID`) or a specific guid; `false` stops.
- Editor activity (`TaskerActivity.kt:24-47`) lists `Default` + every stored server in a
  `simple_list_item_single_choice` `ListView`, and writes back the Locale bundle + blurb in
  `confirmFinish()` (`TaskerActivity.kt:70-92`).
- Manifest: activity `AndroidManifest.xml:306-313` (exported, `EDIT_SETTING`), receiver
  `AndroidManifest.xml:315-323` (exported, `FIRE_SETTING`, `tools:ignore="ExportedReceiver"`).

### 1.4 `service/QSTileService.kt` — quick-settings tile
- `onStartListening` (`QSTileService.kt:42-54`) syncs tile state from `CoreServiceManager.isRunning()`,
  registers a receiver for `BROADCAST_ACTION_ACTIVITY`, and pings the service with
  `MSG_REGISTER_CLIENT`.
- `onClick` (`QSTileService.kt:74-85`) toggles via `startVServiceFromToggle` / `stopVService`.
- Label: app name when inactive, running server name when active (`QSTileService.kt:25-36`).
- Manifest `AndroidManifest.xml:290-304`.

### 1.5 `ui/ScStartActivity` / `ScStopActivity` / `ScSwitchActivity` / `ScScannerActivity` — launcher shortcuts
- Three one-shot translucent activities in `:RunSoLibV2RayDaemon` that call
  `startVServiceFromToggle` / `stopVService` and `finish()` (`ScStartActivity.kt:7-19`,
  `ScStopActivity.kt:7-19`, `ScSwitchActivity.kt:7-21`).
- `ScScannerActivity.kt:11-43` opens the QR scanner, runs `AngConfigManager.importBatchConfig`,
  toasts the outcome, then hands off to `MainActivity`.
- Manifest `AndroidManifest.xml:140-160`; wired from `res/xml/shortcuts.xml` (and the fdroid
  override `app/src/fdroid/res/xml/shortcuts.xml`).

### 1.6 `tv/**` — QR + LAN subscription transfer (phone → TV)
- `TvPairingProtocol.kt` defines the rendezvous URI `dvpntv://v1?ip&port&token`, a 128-bit
  `SecureRandom` one-time token (`TvPairingProtocol.kt:51-55`), a 120 s TTL
  (`TvPairingProtocol.kt:30`), and the JSON body/response helpers.
- `TvHttpReceiver.kt` is a hand-rolled single-request HTTP listener on an ephemeral wildcard port
  (`TvHttpReceiver.kt:58-79`) with: LAN-peer guard (`:133-136`), TTL guard (`:139-143`),
  `POST /pair`-only routing (`:172-175`), constant-time bearer comparison (`:184-193`,
  `:303-308`), 5-bad-attempt lockout (`:188-191`), 64 KiB body cap (`:196-200`), and
  consume-before-import so a second concurrent request cannot be serviced (`:208-210`).
  This is careful, well-built code.
- `TvNetworkUtils.kt:172-192` picks a site-local IPv4, preferring `wlan*` then `eth*`.
- `TvReceiveActivity.kt` (TV side) renders the QR, runs the listener bound to `onStart`/`onStop`
  (`:47-55`), and imports through the normal subscription plumbing (`:147`).
- `TvSendActivity.kt` (phone side) auto-launches the scanner once (`:68-75`), then shows a
  radio-group subscription picker (`:95-113`) and POSTs the sub URL with the bearer token
  (`:162-199`). The secret sub URL never appears in the QR.
- Manifest `AndroidManifest.xml:127-138`. Entry points: `MainActivity.kt:2067`, `:2461`
  (send), `:2466` (receive, only shown when `FEATURE_LEANBACK`, `MainActivity.kt:2464-2466`).

### 1.7 `ui/UrlSchemeActivity.kt` + `ui/UrlSchemeListActivity.kt` — `depv://` deeplinks
- `UrlSchemeActivity` (exported, `AndroidManifest.xml:162-190`) handles three filters:
  `ACTION_SEND text/plain`, `v2rayng://install-config|install-sub`, and the departament
  `depv://` scheme.
- `handleDepvScheme` (`UrlSchemeActivity.kt:82-135`) dispatches `connect|open`, `disconnect|close`,
  `toggle`, `import/{base64}`, `add/{url}`, `routing/add|onadd/{base64}`.
- `UrlSchemeListActivity.kt:13-40` is a reference screen that copies each `depv://` form to the
  clipboard. Strings in `res/values/strings_deeplink.xml`.

### 1.8 `ui/BackupActivity.kt` — config backup/restore/share
- Local backup via SAF `CreateDocument` (`BackupActivity.kt:158-188`), WebDAV backup/restore
  (`:194-287`), share-as-zip via `FileProvider` (`:56-74`), and a WebDAV credentials dialog
  (`:289-313`).
- Backup content is `MMKV.backupAllToDirectory(...)` (`BackupActivity.kt:106`); restore is
  `MMKV.restoreAllFromDirectory(...)` + `SettingsManager.initApp` (`:118-131`).
- `FileProvider` authority `${applicationId}.cache` declared at `AndroidManifest.xml:343-351`,
  paths in `res/xml/cache_paths.xml` (whole cache dir).

### 1.9 `ui/LogcatActivity.kt` — in-app log viewer
Pull-to-refresh log list, search filter, copy-all / share-as-file / clear
(`LogcatActivity.kt:104-153`). Share writes to `cacheDir/shared_logs` and hands out a
`FileProvider` uri (`:55-101`). **Not reachable from the UI — see 2.4.**

### 1.10 `ui/AboutActivity.kt`
Five rows: source code, feedback, OSS licenses (raw `WebView` inside an `AlertDialog`,
`AboutActivity.kt:27-35`, asset `app/src/main/assets/open_source_licenses.html` exists), Telegram
channel, privacy policy; plus version + application id (`:45-50`).

### 1.11 `ui/CheckUpdateActivity.kt`
Checks GitHub releases through `UpdateCheckerManager.checkForUpdate` and offers a download link
(`CheckUpdateActivity.kt:46-78`). **Not reachable from the UI — see 2.4.**

### 1.12 `ui/SettingsActivity.kt`
The legacy `PreferenceFragmentCompat` screen over `res/xml/pref_settings.xml`, backed by
`MmkvPreferenceDataStore` (`SettingsActivity.kt:63`), with a lot of cross-preference
enable/disable logic (`:198-303`). **Superseded by the in-`MainActivity` settings tab
(`MainActivity.kt:2424-2478`) and not reachable — see 2.4.**

### 1.13 `ui/ProviderSettingsActivity.kt` — «Настройки провайдеров»
Four cards: auto-update + interval (applied across every stored `SubscriptionItem`, then
`SubscriptionUpdater.sync(forceReschedule = true)`, `ProviderSettingsActivity.kt:128-165`),
launch-time toggles, HWID + subscription User-Agent, and server sort order. Entry point
`MainActivity.kt:2458`.

### 1.14 `ui/PerAppProxyActivity.kt`
Per-app proxy/bypass list with select-all / invert / auto-select-from-remote-list / import from
clipboard / export to clipboard (`PerAppProxyActivity.kt:131-237`). Auto-select downloads
`AppConfig.ANDROID_PACKAGE_NAME_LIST_URL` (`:191`). Entry `MainActivity.kt:2436`.

### 1.15 `ui/RoutingSettingActivity.kt`
Ruleset list with drag reorder, domain-strategy picker, import from presets / clipboard / QR,
export to clipboard (`RoutingSettingActivity.kt:74-190`). Entry `MainActivity.kt:2456`.

### 1.16 `ui/SubSettingActivity.kt`
Subscription-group CRUD list + "update all" (`SubSettingActivity.kt:67-102`). **Not reachable from
the UI — see 2.4.**

### 1.17 Notification construction
- **Ongoing VPN notification** — `handler/NotificationManager.kt`. Channel
  `AppConfig.RAY_NG_CHANNEL_ID = "DEPARTAMENT_VPN_CH_ID"` / name `"departament VPN"`
  (`AppConfig.kt:203-204`), `IMPORTANCE_LOW`, `VISIBILITY_PRIVATE`, no badge
  (`NotificationManager.kt:219-233`). Rich notification: flag + server name title, chronometer
  uptime, stop + restart actions (`:123-173`); hardened fallback notification if the rich build
  throws (`:97-105`, `:179-188`); `startForeground` itself is wrapped (`:107-116`). Small icon
  `R.drawable.ic_stat_name`, tint `R.color.icon_blue`.
- **Subscription-update + core-test notifications** — `util/NotificationHelper.kt` +
  `enums/NotificationChannelType.kt`: channels `subscription_update_channel` (id 13) and
  `core_test_channel` (id 12), both `IMPORTANCE_LOW` / `VISIBILITY_PRIVATE`
  (`NotificationHelper.kt:123-137`), same `ic_stat_name` small icon (`:153`).
- Icons: `res/drawable/ic_stat_name.xml`, `ic_notif_stop.xml`, `ic_notif_restart.xml` are all
  solid-white 24dp vectors — correct for status-bar alpha masking and for notification actions.
- Notification ids do not collide: 1 (VPN), 12 (core test), 13 (subscription update).

---

## 2. Broken / stubbed / dead

### 2.1 CRITICAL — every launcher shortcut is dead (wrong `targetPackage`)
`res/xml/shortcuts.xml` hardcodes `android:targetPackage="com.v2ray.ang"` on all four shortcuts
(lines 14, 28, 42, 56), and the fdroid override `app/src/fdroid/res/xml/shortcuts.xml` hardcodes
`"com.v2ray.ang.fdroid"` (lines 15, 30, 45, 60). The real application id is
`com.departamentvpn.app` (`app/build.gradle.kts:13`), fdroid `com.departamentvpn.app.fdroid`
(`app/build.gradle.kts:65-66`).

Consequence: long-pressing the launcher icon shows «Switch / Импорт из QR-кода / Запуск служб /
Остановка служб», and every one of them resolves to a package that is not installed. All four
static shortcuts — and therefore `ScSwitchActivity`, `ScStartActivity`, `ScStopActivity`,
`ScScannerActivity` — are unreachable. Those four activities exist only for these shortcuts; nothing
else in the codebase launches them (verified by grepping `Sc*Activity::class` across `src`).

Fix: use `${applicationId}` (manifest placeholders are not supported in `res/xml`, so this needs
either a `tools:` shortcut rewrite, `<shortcut>` `android:targetPackage="@string/..."` fed from a
`resValue`, or dropping `targetPackage/targetClass` in favour of a `ShortcutManager`-registered
dynamic shortcut).

### 2.2 CRITICAL — every peripheral start path skips VPN consent and fails silently
Only `MainActivity` requests VPN permission: `MainActivity.kt:1544-1552` calls
`VpnService.prepare(this)` and launches `requestVpnPermission` when it returns non-null.

Every other entry point goes straight to `CoreServiceManager.startVService*`:
- widget click — `WidgetProvider.kt:70-74`
- QS tile — `QSTileService.kt:76-84`
- shortcuts — `ScStartActivity.kt:14-16`, `ScSwitchActivity.kt:14-18`
- Tasker — `TaskerReceiver.kt:29-34`
- boot — `BootReceiver.kt:40`
- `depv://connect|open|toggle` — `UrlSchemeActivity.kt:88-98`

`CoreServiceManager.startContextService` (`core/CoreServiceManager.kt:131-195`) never checks
consent; it just `startForegroundService(CoreVpnService)` (`:182`). `CoreVpnService.setupVpnService`
then does:

```kotlin
// service/CoreVpnService.kt:186-192
private fun setupVpnService() {
    val prepare = prepare(this)
    if (prepare != null) {
        LogUtil.e(AppConfig.TAG, "StartCore-VPN: Permission not granted")
        stopSelf()
        return
    }
```

`stopSelf()` — no `reportStartFailure(...)` (which exists at `CoreVpnService.kt:143-149`), no
`stopAllService()`, no toast, no broadcast. So on a device where VPN consent has not been granted
(fresh install, or consent revoked by another VPN app):
- the widget and the tile stay showing the previous state, because they only repaint on
  `MSG_STATE_*` broadcasts (`WidgetProvider.kt:75-93`, `QSTileService.kt:91-114`) and none is sent;
- the user sees a foreground notification flash and disappear (`showNotification(null)` runs first,
  `CoreVpnService.kt:117-122`);
- Tasker / boot / deeplink callers get no signal at all.

### 2.3 HIGH — `depv://routing/...` replaces the user's routing rules with no confirmation
`UrlSchemeActivity` is `exported="true"` with `BROWSABLE` + `DEFAULT` on the bare `depv` scheme and
no host/path restriction (`AndroidManifest.xml:182-189`). `handleDepvScheme` routes
`routing/add|onadd/{base64}` into `importRoutingRules` (`UrlSchemeActivity.kt:118-131`,
`:156-170`), which calls `SettingsManager.resetRoutingRulesets(json)` and, for `onadd`, additionally
broadcasts `MSG_STATE_RESTART` to bounce a running tunnel.

`resetRoutingRulesets` is not additive: `resetRoutingRulesetsCommon`
(`handler/SettingsManager.kt:118-128`) keeps only rulesets flagged `locked == true` and replaces
everything else with the payload.

The in-app equivalents all gate this behind a confirmation dialog
(`routing_settings_import_rulesets_tip` — `RoutingSettingActivity.kt:101`, `:123`, `:171`). The
deeplink path does not. Any web page, chat message or QR the user taps can silently rewrite routing
and restart the VPN. `depv://import/{base64}` and `depv://add/{url}` are similarly unconfirmed
(they are at least gated by the departament-only subscription guard — see 2.7).

### 2.4 HIGH — four screens are dead code, two of them user-visible features
`SettingsActivity`, `LogcatActivity`, `CheckUpdateActivity` and `SubSettingActivity` are declared in
the manifest (`AndroidManifest.xml:88-90`, `:100-102`, `:109-111`, `:191-193`) but **nothing in the
codebase launches them.** Verified by grepping every `X::class.java` and every reference across
`app/src`; the only hits are the manifest, their own sources, and `tools:context` in their layouts.
`MainActivity.kt:2427` even acknowledges it: *"toggles/pickers read & write the same MMKV keys the
legacy SettingsActivity used"*.

Dead with them:
- `SettingsActivity` + `SettingsFragment`, `res/layout/activity_settings.xml`,
  `res/xml/pref_settings.xml`, `helper/MmkvPreferenceDataStore`, and `SettingsActivity.onModeHelpClicked`
  (`SettingsActivity.kt:306-308`, referenced from `R.layout.preference_with_help_link`).
- `LogcatActivity` + `LogcatRecyclerAdapter` + `viewmodel/LogcatViewModel` + `res/menu/menu_logcat.xml`
  + `res/layout/activity_logcat.xml` / `item_recycler_logcat.xml`.
- `CheckUpdateActivity` + `res/layout/activity_check_update.xml`.
- `SubSettingActivity` + `SubSettingRecyclerAdapter` + `res/menu/action_sub_setting.xml` +
  `res/layout/activity_sub_setting.xml` / `item_recycler_sub_setting.xml`.

Product consequence: **there is no way to view or share logs and no way to check for updates.**
The settings tab (`MainActivity.kt:2432-2477`) has rows for mode, per-app, DNS, ping method, local
proxy, always-on, mux, fragment, appearance, language, boot, sub auto-update, routing, assets,
provider, TV send/receive, about, URL schemes, backup — and nothing for logs, updates, or
subscription-group management.

Note `LogcatActivity.kt:70-73` uses `"${packageName}.cache"` for the FileProvider authority while
`BackupActivity.kt:66` uses `BuildConfig.APPLICATION_ID + ".cache"` — equivalent today, but two
spellings of the same contract.

### 2.5 HIGH — the About screen points departament users at upstream v2rayNG
`AboutActivity` opens `AppConfig.APP_URL`, `APP_ISSUES_URL`, `TG_CHANNEL_URL`,
`APP_PRIVACY_POLICY` (`AboutActivity.kt:19-43`). All four are still upstream
(`AppConfig.kt:144-150`):

| Row | Constant | Value |
|---|---|---|
| Исходный код | `APP_URL` | `https://github.com/2dust/v2rayNG` |
| Обратная связь | `APP_ISSUES_URL` | `https://github.com/2dust/v2rayNG/issues` |
| Telegram-канал | `TG_CHANNEL_URL` | `https://t.me/github_2dust` |
| Политика конфиденциальности | `APP_PRIVACY_POLICY` | `.../2dust/v2rayNG/master/CR.md` |

Also `APP_API_URL = https://api.github.com/repos/2dust/v2rayNG/releases` (`AppConfig.kt:145`) — the
update checker (`handler/UpdateCheckerManager.kt:17-76`) would compare against upstream releases and
offer upstream APKs for download. `SettingsActivity.onModeHelpClicked` opens
`APP_WIKI_MODE = github.com/2dust/v2rayNG/wiki/Mode` (`AppConfig.kt:147`).

The Telegram row is the worst of these: a departament customer tapping «Telegram-канал» lands in a
different product's channel.

### 2.6 HIGH — release builds are unminified and signed with the debug key
`app/build.gradle.kts:49-59`:
```kotlin
release {
    isMinifyEnabled = false
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("debug")
}
```
- `proguard-rules.pro` contains **zero active rules** — the whole file is the AGP boilerplate
  comment block (`proguard-rules.pro:1-19`). If `isMinifyEnabled` is ever flipped on, every
  Gson-reflected DTO (`dto/**`, `auth/dto/**`, `dto/GitHubRelease`, `RulesetItem`, `WebDavConfig`,
  the `libv2ray` JNI surface, `AndroidLibXrayLite`) loses its field names and the app breaks at
  runtime, not at build time.
- Debug-key signing means the "release" APK cannot be shipped to Play and cannot be upgraded over a
  properly-signed build. The comment at `:56-57` says this is deliberate for CI test installs — it
  still means there is no real release configuration in the repo.
- `isMinifyEnabled = false` also leaves `LogUtil` calls and the full class/method table in the
  shipped binary.

### 2.7 MEDIUM — deeplink and TV import paths swallow the "not a departament link" and "duplicate" outcomes
`AngConfigManager.ImportResult` carries `subDuplicate` and `subRejected`
(`handler/AngConfigManager.kt:56-62`), set when a subscription URL fails
`SubscriptionGuard.isAllowed` (`handler/AngConfigManager.kt:922-926`). Only two call sites read
them: `ScScannerActivity.kt:34-35` and `MainActivity.kt:2233`.

- `UrlSchemeActivity` destructures only the first two components
  (`UrlSchemeActivity.kt:142`, `:186`) and reports a generic
  `R.string.import_subscription_failure`. So `depv://add/<foreign-sub-url>` says "import failed"
  instead of "this link is not from departament".
- `TvReceiveActivity.handleImport` (`tv/TvReceiveActivity.kt:147-156`) does the same and then maps
  the empty result to `Result.DUPLICATE`, so the phone shows «Эта подписка уже добавлена»
  (`tv_receive_duplicate`) for a rejected foreign link. Actively misleading.

### 2.8 MEDIUM — `UrlSchemeActivity` inflates the logcat layout and always bounces into MainActivity
```kotlin
// ui/UrlSchemeActivity.kt:25, 29
private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }
...
setContentView(binding.root)
```
The deeplink handler renders the log-viewer layout (RecyclerView + SwipeRefreshLayout) as its
content view. Then, unconditionally, `startActivity(Intent(this, MainActivity::class.java))` and
`finish()` (`:63-64`) — so `depv://disconnect` and `depv://toggle`, which exist precisely for
automation, always yank the user into the app UI. And when anything throws, the `catch`
(`:65-67`) logs but never calls `finish()`, leaving the empty logcat-shaped activity on screen.

### 2.9 MEDIUM — `CoreProxyOnlyService` can be started as a foreground service without ever calling `startForeground`
`AndroidManifest.xml:247-256` declares `foregroundServiceType="specialUse"` and
`CoreServiceManager.startContextService` starts it with
`ContextCompat.startForegroundService(...)` (`core/CoreServiceManager.kt:173-182`). But
`CoreProxyOnlyService.onStartCommand` (`service/CoreProxyOnlyService.kt:32-45`) never calls
`startForeground` itself — the promotion happens indirectly, deep inside
`CoreServiceManager.doStartCoreLoop` at `core/CoreServiceManager.kt:255`
(`NotificationManager.showNotification(currentConfig)`).

Two paths reach `stopSelf()` without ever having promoted:
- `startCoreLoop` early-returns `false` when `coreController.isRunning` is already true
  (`core/CoreServiceManager.kt:203-206`) or when the service reference is null (`:208-212`);
- `doStartCoreLoop` throws before line 255 — e.g. `CoreConfigManager.getV2rayConfig` fails at `:232`
  or `error(result.errorMessage)` at `:235` — which is caught at `:217-223` and turned into `false`.

In those windows the process has an un-promoted `startForegroundService` in flight.
`CoreVpnService` gets this right (it promotes first thing in `onStartCommand`,
`service/CoreVpnService.kt:117-122`); `CoreProxyOnlyService` should mirror that.

### 2.10 MEDIUM — widget declaration is below Android's widget-quality bar
`res/xml/app_widget_provider.xml` declares only `initialLayout`, `minWidth="20dp"`,
`minHeight="20dp"`, `widgetCategory`. Missing: `android:description` (shown in the widget picker),
`previewLayout` / `previewImage`, `targetCellWidth` / `targetCellHeight`, `resizeMode`,
`updatePeriodMillis`. `20dp` is below one launcher cell, so the placed size is launcher-dependent.

`res/layout/widget_switch.xml` sets the icon with `app:srcCompat="@drawable/ic_stat_name"` — an
AppCompat attribute inside a `RemoteViews` layout, which the platform inflater does not process.
It happens to be harmless only because `WidgetProvider.updateWidgetBackground`
(`WidgetProvider.kt:47-53`) overwrites the image with `setInt(..., "setImageResource", ...)` on
every update. The widget also uses the same `ic_stat_name` shield as the notification, and shows a
hardcoded `@android:color/white` label (`widget_switch.xml:28-33`) that will be invisible on a
light wallpaper/lockscreen.

### 2.11 MEDIUM — backup/restore round-trips the encrypted session store
`BackupActivity` backs up **every** MMKV instance (`MMKV.backupAllToDirectory`,
`BackupActivity.kt:106`), which includes `departament_auth` — the store holding the session JWT,
the cached profile and the HWID (`auth/AuthTokenStore.kt:23-29`, `:96-99`). That zip is then
offered to any app the user picks via `Intent.ACTION_SEND` (`BackupActivity.kt:56-74`).

The store is Keystore-encrypted (`auth/AuthTokenStore.kt:35-50`) so the token is not plaintext in
the zip — but the Keystore key never leaves the device and is destroyed on uninstall. So
`restoreConfiguration` (`BackupActivity.kt:118-131`), which blindly
`MMKV.restoreAllFromDirectory(...)`, overwrites the live auth store with a file that a
different-device / post-reinstall keystore cannot decrypt. `openStore()` swallows this
(`auth/AuthTokenStore.kt:43-50`) and `getToken()` simply returns null — the user is silently signed
out after a restore, with no message explaining why. Nothing in `restoreConfiguration` excludes or
re-seeds the auth store.

### 2.12 MEDIUM — the departament screens are Russian-only while the language picker offers English
`res/values/arrays.xml:140-151` offers `auto / Русский / English`. But every departament-authored
string file lives in the **default** bucket in Russian:
`values/strings_provider.xml` (all `ps_*`), `values/strings_deeplink.xml` (all `url_scheme_*`),
`values/strings_tv.xml` (all `tv_*`), `values/strings_settings_hub.xml`, and the newer
departament strings inside `values/strings.xml` (e.g. `menu_add_tv_send`, `sub_delete` at
`values/strings.xml:30-34`). There is no `values-en/`.

Upstream strings, by contrast, are English in `values/` with a Russian override in `values-ru/`
(spot-checked: `notification_action_stop_v2ray`, `title_service_restart`, `app_tile_first_use`,
`toast_config_file_invalid`, `title_logcat`, `title_about`, `update_check_for_update` — all present
in both). Counts: 909 names in `values/`, 474 in `values-ru/`.

So picking «English» produces a screen-by-screen mix: About / Backup / Per-app in English,
Provider settings / TV / URL schemes / the whole settings tab in Russian.

Related: `res/values-ru/strings_tv.xml` is a byte-for-byte duplicate of `res/values/strings_tv.xml`
(same Russian text, same keys) — pure dead weight that will drift.

### 2.13 MEDIUM — user-facing Russian copy hardcoded in Kotlin
`ScScannerActivity.kt:28-35` ships four literals:
```kotlin
toastSuccess("Серверы добавлены: $loaded")
toastError("Не удалось загрузить серверы подписки")
toast("Подписка уже добавлена")
toast("Эта ссылка не от departament. Используйте подписку из нашего бота.")
```
The same last string is duplicated at `MainActivity.kt:2233`. These are untranslatable, un-reviewable
and duplicated. (`ScScannerActivity` is currently unreachable — see 2.1 — so this copy is also
dead today.)

Also `SubSettingActivity.kt:155`: `else -> ownerActivity.toast("else")` — a developer placeholder
in a user-visible branch.

### 2.14 LOW — dead source sets still branded "v2rayNG"
`app/src/dev/res/values/strings.xml` → `app_name = "v2rayNG (DEV)"` and
`app/src/pre_release/res/values/strings.xml` → `app_name = "v2rayNG (PR)"`. Neither `dev` nor
`pre_release` is a declared build type (`app/build.gradle.kts:49-60` declares only `release`;
flavors are `fdroid` / `playstore` at `:62-73`), so both directories are silently ignored by
Gradle. Delete them, or they will one day be resurrected and ship the wrong app name.

### 2.15 LOW — empty adapter-listener overrides
`RoutingSettingActivity.ActivityAdapterListener.onRemove` and `.onShare`
(`RoutingSettingActivity.kt:206-211`) are empty. Harmless today —
`RoutingSettingRecyclerAdapter` only ever calls `onEdit` (`RoutingSettingRecyclerAdapter.kt:35`) —
but it means "remove ruleset" has no implementation waiting if the adapter ever wires a delete
affordance. Contrast `SubSettingRecyclerAdapter.kt:34-58`, which calls all three.

### 2.16 LOW — `ProviderSettingsActivity` keeps a screen-local interval key that is not the source of truth
`PREF_UPDATE_INTERVAL = "pref_provider_update_interval"` is declared private to the activity
(`ProviderSettingsActivity.kt:42`) and is the only thing the interval row reads (`:111-113`), while
the interval that actually schedules work lives on each `SubscriptionItem`
(`:128-139`, `:145-165`). If a subscription is added after the interval was picked, it gets
`autoUpdate=false` and the screen still displays the stored interval. The auto-update switch has the
mirror problem: `isAutoUpdateOn()` is `any { it.subscription.autoUpdate }`
(`:108-109`), so with zero subscriptions the switch reads off and toggling it does nothing at all
(the `forEach` iterates an empty list) while `binding.switchAutoUpdate.isChecked = enable`
(`:138`) still flips the UI on — a switch that lies.

### 2.17 LOW — `TaskerActivity` polish
- Empty toolbar title: `setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = "")`
  (`TaskerActivity.kt:27`).
- Reuses `R.menu.action_server` and then hides `del_config` at runtime
  (`TaskerActivity.kt:94-99`), leaving a no-op `R.id.del_config -> true` branch (`:102-104`).
- Blurb text is English-only and built by string concatenation: `"Start $remarks"` / `"Stop $remarks"`
  (`TaskerActivity.kt:82-86`).
- Uses a raw `ListView` + `android.R.layout.simple_list_item_single_choice`
  (`TaskerActivity.kt:39-44`) — platform styling, entirely outside the Incy design system.

### 2.18 LOW — toasts from background contexts may never appear
`CoreServiceManager.startVServiceFromToggle` reports "no server selected" with
`context.toast(R.string.app_tile_first_use)` (`core/CoreServiceManager.kt:68`), and
`startContextService` toasts errors at `:97` and `:164`. `Context.toast` goes through
`Toasty.normal(...)` (`extension/_Ext.kt:25-36`), a **custom-view** toast. The callers here are a
`BroadcastReceiver` (widget), a `TileService`, and boot — all background contexts in the
`:RunSoLibV2RayDaemon` process. Custom-view toasts from a background app are suppressed on modern
Android. Combined with 2.2 this means the widget/tile "nothing happened" case is completely mute.

---

## 3. Permission / manifest issues

| # | Location | Issue |
|---|---|---|
| 3.1 | `AndroidManifest.xml:27-29` | `QUERY_ALL_PACKAGES` with `tools:ignore="PackageVisibilityPolicy,QueryAllPackagesPermission"`. Needed by `PerAppProxyActivity` / `AppManagerUtil`, but it is a Play-policy-restricted permission and the `playstore` flavor (`build.gradle.kts:69-72`) ships it. Requires a declared-use form or a `<queries>` narrowing. |
| 3.2 | `AndroidManifest.xml:36-38` | `<uses-permission android:name="...FOREGROUND_SERVICE_SPECIAL_USE" android:minSdkVersion="34" />`. The documented attributes for `<uses-permission>` are `android:name` and `android:maxSdkVersion`. Verify `minSdkVersion` is not being silently ignored — if it is, the declaration is fine; if the tooling treats it as unknown, this is the permission that gates every `specialUse` foreground service in the app. |
| 3.3 | `AndroidManifest.xml:39` | Dead commented-out duplicate `ACCESS_NETWORK_STATE` line left in the manifest. |
| 3.4 | `AndroidManifest.xml:45` | `android:allowBackup="true"` with **no** `android:dataExtractionRules` and no `android:fullBackupContent`. Cloud/D2D backup therefore includes the whole MMKV directory — server configs, subscription URLs, and the `departament_auth` store. Combine with 2.11: the auth store is Keystore-encrypted so it is unreadable off-device, but it will still be transferred, and it will still be un-decryptable on the target, silently signing the user out after a device transfer. |
| 3.5 | `AndroidManifest.xml:49` + `res/xml/network_security_config.xml` | `usesCleartextTraffic="true"` and a base config that permits cleartext **and** trusts `user` CAs (`tools:ignore="AcceptsUserCertificates"`). This is upstream's posture for arbitrary proxy endpoints, but for a product whose backend is `https://web.departament.site/api` (`build.gradle.kts:44`) it means the account/payment API is MITM-able by any user-installed CA. Consider a `domain-config` that pins the departament host to system trust only. |
| 3.6 | `AndroidManifest.xml:290-304` | `QSTileService` is declared with `android:foregroundServiceType="specialUse"` and a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property. A `TileService` is not a foreground service; both are meaningless here and add noise to the FGS-type declaration surface. |
| 3.7 | `AndroidManifest.xml:315-323` | `TaskerReceiver` is `exported="true"` with `tools:ignore="ExportedReceiver"` and **no permission guard**. Any app on the device can broadcast `com.twofortyfouram.locale.intent.action.FIRE_SETTING` with a crafted bundle and start the tunnel on an arbitrary stored server guid (`TaskerReceiver.kt:29-34` → `startVService(context, guid)` → `MmkvManager.setSelectServer(guid)`). The Locale plugin spec expects a `com.twofortyfouram.locale.permission.FIRE_SETTING`-style guard; there is none. |
| 3.8 | `AndroidManifest.xml:162-190` | `UrlSchemeActivity` is exported and `BROWSABLE` on the bare `depv` scheme with no `android:host`/`android:pathPrefix`, unlike the `v2rayng` filter above it which does restrict hosts (`:176-178`). See 2.3. |
| 3.9 | `AndroidManifest.xml:343-351` + `res/xml/cache_paths.xml` | The `FileProvider` exposes `<cache-path name="cache" path="/" />` — the **entire** cache dir, not just `shared_logs/` and the backup zip. Any `grantUriPermission` mis-scope leaks more than intended. |
| 3.10 | `AndroidManifest.xml:54-71` | `MainActivity` declares `LEANBACK_LAUNCHER` and the app declares `uses-feature android.software.leanback` (`:19-21`) — so the phone `MainActivity` is what launches on Android TV. The only TV-specific accommodation is hiding/showing one settings row (`MainActivity.kt:2464-2466`). No `TvReceiveActivity` is reachable from a TV launcher directly. |
| 3.11 | `AndroidManifest.xml:227-245` | Correct and worth keeping: `CoreVpnService` has `BIND_VPN_SERVICE`, `SUPPORTS_ALWAYS_ON` metadata, and the `vpn` FGS subtype property. No issue. |
| 3.12 | `app/build.gradle.kts:9,15` | `compileSdk = 37` / `targetSdk = 37` with AGP `9.2.1` (`gradle/libs.versions.toml:2`). Flagging for verification only — the peripheral surfaces here (exported receivers, FGS types, `BOOT_COMPLETED` → FGS, custom toasts from background) are exactly the areas each new API level tightens, and this project targets an SDK past the point I can assert behaviour for. Every "silently does nothing" path in section 2 gets worse, not better, as the target rises. |

---

## 4. Notification channel / icon correctness

Verified correct:
- Channel ids/names are departament-branded (`AppConfig.kt:203-204`), not `RayNG`.
- All three channels are `IMPORTANCE_LOW` + `VISIBILITY_PRIVATE`
  (`NotificationManager.kt:224-230`, `NotificationHelper.kt:129-136`) — silent, and the server name
  is hidden on the lockscreen. Correct for a VPN.
- Pre-O path passes `""` as the channel id (`NotificationManager.kt:91-95`,
  `NotificationHelper.kt:145-149`) — correct for `NotificationCompat`.
- Small icons are white-only vectors (`ic_stat_name.xml`, `ic_notif_stop.xml`,
  `ic_notif_restart.xml`) — they will alpha-mask correctly in the status bar.
- `setColor(R.color.icon_blue)` on the ongoing notification (`NotificationManager.kt:150`) — the one
  brand accent, as the design law requires.
- Notification ids are distinct: 1 / 12 / 13.
- The action broadcasts are correctly package-scoped: `stopV2RayIntent.package = AppConfig.ANG_PACKAGE`
  (`NotificationManager.kt:130`, `:135`) where `ANG_PACKAGE = BuildConfig.APPLICATION_ID`
  (`AppConfig.kt:7`). The rebrand did **not** break these, unlike `shortcuts.xml`.

Issues:
- **4.1 — `POST_NOTIFICATIONS` is only requested from `MainActivity`** (`MainActivity.kt:306`, the
  only call site of `checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS)` in the whole
  tree). A user who launches the tunnel from the widget/tile/Tasker/boot before ever opening the
  app has no notification permission; on API 33+ the foreground notification is not shown. The
  foreground service still runs, but the user gets a running VPN with no visible, dismissible
  control. `showNotification` returning `true` is not evidence the user can see anything.
- **4.2 — `NotificationHelper.builderCache` is an unbounded, never-invalidated `mutableMap`**
  (`NotificationHelper.kt:24`, `:63-65`). `updateNotification` reuses a builder created with an
  **empty title** (`:64` passes `""`, which `buildNotificationBuilder` turns into the app name at
  `:151`), so any subscription-update progress notification permanently loses its real title after
  the first `notify(...)`. Cleared only by `cancel()` (`:111`), never by `stopForeground`.
- **4.3 — `NotificationManager.updateNotification` takes two parameters it ignores**
  (`NotificationManager.kt:241-247`: `proxyTraffic`, `directTraffic` are unused). Dead signature
  left over from the speed-in-notification design that `updateSpeedNotificationOnce` deliberately
  abandoned (`:306-314`).
- **4.4 — `NotificationHelper` caches the `NotificationManager` in a process-wide `object`**
  (`:23`, `:116-121`) obtained from whatever `Context` called first. Since these services live in
  `:RunSoLibV2RayDaemon`, that is fine in practice, but it is a latent leak of a service context.
- **4.5 — no notification action strings for the subscription/test channels.** They are
  content-only (`NotificationHelper.kt:152-159`), with `setOngoing(false)` even when used via
  `startForeground` (`:80-89`, called by `CoreTestService.kt:81`). A foreground-service notification
  that is not ongoing can be swiped away while the service runs.

---

## 5. Design-system compliance of the peripheral screens

Per `/home/user/dp/CLAUDE.md`, all UI must use one spacing scale (`@dimen/space_4/8/12/16/24`), the
`TextAppearance.App.*` type scale, `@dimen/row_min_height 56`, and the `radius_*` tokens. Those
tokens exist (`res/values/dimens.xml:14-33`, `res/values/styles.xml:56-122`).

Compliant:
- `res/values/styles.xml:6-17` `SettingsSectionLabel` — Space Grotesk, weight 700, 16sp,
  `textAllCaps=false`, `letterSpacing=0`. Sentence-case bold, exactly as required.

Non-compliant:

| File | Violation |
|---|---|
| `res/layout/activity_about.xml` | Uses `@dimen/padding_spacing_dp16` (the legacy scale) and `@style/TextAppearance.AppCompat.Subhead` / `.Small` throughout (lines 27, 38, 40, 52, 63, 65, 77, 88, 90, 103, 114, 116, 128, 139, 141, 149, 156, 163). Zero `TextAppearance.App.*`. |
| `res/layout/activity_backup.xml` | Off-scale hardcoded spacing: `layout_marginHorizontal="12dp"` (26, 198), `layout_marginStart="14dp"` (68, 119, 170, 235), `layout_marginStart="68dp"` (86, 137), and hardcoded `textSize="16sp"` (72, 123, 174, 239). |
| `res/layout/activity_check_update.xml` | `padding_spacing_dp16` + `TextAppearance.AppCompat.*` (20, 33, 35, 48, 59, 61, 70, 77). |
| `res/layout/activity_bypass_list.xml` (Per-app) | `12dp` / `8dp` / `4dp` margins and `16sp` text (20, 21, 49, 61, 80, 106). |
| `res/layout/activity_routing_setting.xml` | `12dp`/`14dp`/`2dp`/`4dp` margins, `16sp`/`13sp` text (34, 71, 80, 86, 89, 95). |
| `res/layout/activity_tasker.xml` | `padding_spacing_dp16/dp8`, `TextAppearance.AppCompat.Medium` (8, 14, 32). |
| `res/layout/activity_tv_send.xml` | `14dp`/`18dp`/`16dp` margins, `14sp` text (45, 49, 55, 69, 72, 81). |
| `res/layout/activity_url_scheme_list.xml` | **No `@dimen` token at all.** Nine rows of `minHeight="60dp"`, `paddingVertical="10dp"`, `paddingStart="16dp"`, `paddingEnd="8dp"`, `textSize="15sp"` / `13sp`. `60dp` is off the `row_min_height=56dp` token; `10dp`/`15sp`/`13sp` are off-scale. |
| `res/layout/activity_provider_settings.xml` | Same shape, different numbers: `minHeight="60dp"`, `paddingVertical="10dp"`, `paddingStart/End="14dp"`, `textSize="16sp"`/`13sp`/`12sp`. So the **gutter differs between two sibling departament screens** (14dp here vs 16dp in the URL-scheme list) — the exact inconsistency the design law forbids. |

`res/layout/activity_base.xml` (the shared toolbar shell used by every sub-screen via
`BaseActivity.setContentViewWithToolbar`, `ui/BaseActivity.kt:151-178`) is clean: transparent
toolbar, zero elevation, `stateListAnimator=@null`, `ToolbarBrandTitle` — this is the "seamless
sub-screen toolbar" the owner asked for, and it is applied consistently by
`AboutActivity`, `BackupActivity`, `CheckUpdateActivity`, `LogcatActivity`, `PerAppProxyActivity`,
`ProviderSettingsActivity`, `RoutingSettingActivity`, `SubSettingActivity`, `TaskerActivity`,
`TvReceiveActivity`, `TvSendActivity`, `UrlSchemeListActivity`. The one hole is
`UrlSchemeActivity`, which uses raw `setContentView` (see 2.8).

---

## 6. Ranked fix list

1. Fix `shortcuts.xml` `targetPackage` in both source sets (2.1) — four features are 100% dead.
2. Move the `VpnService.prepare` check into the shared start path, and make the
   consent-missing case report `MSG_STATE_START_FAILURE` + a visible message (2.2, 2.18, 4.1).
3. Gate `depv://routing/*` (and ideally `import`/`add`) behind the same confirmation the in-app
   importers use, or restrict the `depv` intent filter to explicit hosts (2.3, 3.8).
4. Decide the fate of `SettingsActivity` / `LogcatActivity` / `CheckUpdateActivity` /
   `SubSettingActivity`: either add settings-tab rows (logs and update-check are real user needs) or
   delete the activities, layouts, menus, adapters, viewmodels and manifest entries (2.4).
5. Rebrand `AppConfig.APP_URL / APP_ISSUES_URL / TG_CHANNEL_URL / APP_PRIVACY_POLICY / APP_API_URL /
   APP_WIKI_MODE` (2.5).
6. Guard `TaskerReceiver` with a permission (3.7).
7. Add a real release signing config + a working ProGuard/R8 keep set before enabling minify (2.6).
8. Promote `CoreProxyOnlyService` to foreground in `onStartCommand` (2.9).
9. Add `dataExtractionRules` excluding `departament_auth`, and exclude/re-seed the auth store on
   restore (2.11, 3.4).
10. Add a `values-en/` bucket (or drop English from the language picker) and delete the duplicated
    `values-ru/strings_tv.xml` (2.12).
11. Extract the hardcoded Russian literals to resources; delete `toast("else")` (2.13).
12. Retokenize the peripheral layouts onto `space_*` / `row_min_height` / `TextAppearance.App.*`
    (section 5), starting with the two departament-authored screens whose gutters already disagree.
13. Flesh out `app_widget_provider.xml` (description, preview, target cells) and drop `srcCompat`
    from `widget_switch.xml` (2.10).
14. Surface `subRejected` / `subDuplicate` in the deeplink and TV import paths (2.7).
15. Housekeeping: delete `app/src/dev` and `app/src/pre_release` (2.14); drop the dead
    `proxyTraffic`/`directTraffic` params (4.3); bound or invalidate `NotificationHelper.builderCache`
    (4.2).
