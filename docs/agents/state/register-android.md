# Android defect register — everything still open

**Read against the working tree on branch `claude/app-audit-agents-hyyftk`, 2026-07-26 22:28.**
Paths are relative to `V2rayNG/app/src/main/` unless stated otherwise.

## What this document is

Every Android defect raised anywhere in this session, re-read against today's source, with the ones
that have since been fixed **removed** and the ones a verification agent **refuted** quarantined in
§13 so nobody re-files them. An item is in §1-§11 only because I opened the file and the defect was
still there. Where a source document's line number had rotted, the symbol name won and the citation
was re-resolved.

**Order of authority.** `OWNER-FEEDBACK-2026-07-27.md` outranks every spec, and where a spec and the
owner disagree the owner wins. §1 is his list. Everything after it is subordinate to it.

**Two waves are editing source while this was written.** `HomeFragment.kt`, `MainActivity.kt`,
`fragment_home.xml`, `layout_home_*.xml`, `strings_home.xml`, `LoginActivity.kt`, `AuthViewModel.kt`,
`strings_auth.xml` and `layout_auth_*.xml` all moved during the pass — `HomeFragment.kt` grew from
1867 to 2341 lines between two reads. Items whose only home is one of those files carry an
**⏳ IN FLIGHT** marker and a timestamp; re-check them at the gate rather than starting work on them.
Everything else is in a file nobody is touching and is safe to act on now.

**The environment cannot run the APK.** `libv2ray.aar` is not in the repository and a type-check stub
returns placeholder values. Every claim below is a code fact; §12 lists the five that need a device
to settle their run-time half.

---

## 1 · The owner's own findings

His words are the specification. Nothing here is optional.

### A-01 · Signing out leaves the servers on screen, and they stay selectable with no session
**blocks-release · owner A1 · confirmed open**

`AccountSession.wipe()` does remove the account's subscriptions and their servers from MMKV
(`auth/AccountSession.kt:82-86` → `SubscriptionSyncManager.removeAllManaged()` →
`MmkvManager.removeSubscription` → `removeServerViaSubid`, which also clears `KEY_SELECTED_SERVER` at
`handler/MmkvManager.kt:234-236`). **Nothing then re-reads the store.**
`MainViewModel.serversCache` is an in-memory list rebuilt only by `reloadServerList()`
(`viewmodel/MainViewModel.kt:201-211`), and the logged-out transition calls it from nowhere:

- `ui/HomeFragment.kt:1336-1353` (`applyAccountState`) — on the logged-out branch it empties
  `accountSubs`, re-renders and refreshes the nav gates. No `reloadServerList()`.
- `ui/AccountFragment.kt:282-300` (`onSessionCleared`) — clears the ViewModel, the poll and the
  pending-payment state. No `reloadServerList()`.

So Главная's server list, its «Серверы» count, and `MainActivity.updateBottomNavVisibility`
(`ui/MainActivity.kt:770`, which gates the whole bottom bar on `serversCache.isNotEmpty()`) all keep
reading a list whose rows no longer exist on disk. Tapping one selects a guid `decodeServerConfig`
answers null for.

**Fix.** Call `mainViewModel.reloadServerList()` on **every** account-state change, not only on
logout — that is the second half of D02 as `AFTER-SCREENS.md` §1 records it. One call, in
`applyAccountState`, covers both the explicit sign-out and the 401 route.
**Must not break:** the servers the user added by hand are not the account's and must survive
(`AccountSession.wipe`'s own contract); reloading the list does not delete anything.

### A-02 · The mode row still reads «VPN-туннель / Прокси / VPN + прокси»
**high · owner B3 · confirmed open**

`res/values/strings_home_shell.xml:7-9`:

```xml
<string name="settings_mode_tun" translatable="false">VPN-туннель</string>
<string name="settings_mode_proxy_opt" translatable="false">Прокси</string>
<string name="settings_mode_vpn_proxy" translatable="false">VPN + прокси</string>
```

He asked for **«TUN»**, **«Proxy»**, **«TUN + Proxy»**, in that order, on both platforms. The picker
order in `ui/SettingsTabFragment.kt:202-237` is already TUN / Proxy / VPN+Proxy, so only the three
values change. The same three keys paint the row's value at `:143-149`.

### A-03 · «провайдер» survives in six user-visible strings
**high · owner B1 · confirmed open**

He calls this thing a **подписка**, and said so for «Настройки провайдеров» and «Автообновление
провайдеров» by name. `strings_home.xml` was converted; these were not:

| Key | File:line | Ships |
|---|---|---|
| `ps_title` | `res/values/strings_provider.xml:5` | «Настройки провайдеров» — the screen's own title |
| `settings_provider` | `res/values/strings_settings_hub.xml:8` | «Настройки провайдеров» — the Настройки-tab row |
| `providers_count` | `res/values/strings.xml:11`, `res/values-ru/strings.xml:10` | «провайдеров: %d» |
| `menu_actions_ping_empty` | `res/values/strings_menu_actions.xml:37` | «…Добавьте провайдера или сервер.» |
| `menu_actions_del_all_body` | `res/values/strings_menu_actions.xml:60` | «Серверы провайдеров вернутся…» |
| `subs_ed_user_agent_hint` | `res/values/strings_editors.xml:162` + `values-ru/` | «…из настроек провайдеров» |

### A-04 · «Departament» is still capitalised in two places the user sees
**high · owner B2 · confirmed open**

- `res/values/strings_account.xml:34` — `account_no_subscription` = «Купите тариф, чтобы подключаться
  к серверам **Departament**.» This is verbatim the example he gave.
- `auth/SubscriptionSyncManager.kt:60` — the fallback subscription remark is the literal
  `"Departament VPN"`, and that string is what the subscription card and the провайдер header draw
  when the backend sends no display name.

Everything else already reads lowercase (`app_name`, `auth_gate_title`, `home_gate_signin_caption`,
`tv_receive_instructions`, …), so this is two edits, not a sweep.

### A-05 · The add menu offers six things; he asked for two
**high · owner C1 · confirmed open**

`res/menu/menu_main.xml` `group_import` declares six items — scan QR, clipboard, «Ввести ссылку»,
«Создать вручную», import from file, send to TV — and `ui/MainActivity.kt:1014-1050` dispatches all
six. He was explicit: **QR and clipboard, and nothing else**; the rest move somewhere they belong or
go. «Отправить на ТВ» already has a home (`ui/SettingsTabFragment.kt:111`, row «Перенести подписку на
телевизор»), so it is a deletion here, not a relocation.

### A-06 · The primary button is a flat fill with no depth and no gradient
**medium · owner D1 · confirmed open**

`res/values/styles.xml:379-386` — `Widget.Departament.Button.Primary` is
`backgroundTint="@color/btn_primary_container"` on a base (`:356-378`) that sets `elevation 0dp`.
Press motion exists (`android:stateListAnimator="@anim/press_scale"` at `:365`), so that half of his
instruction is met; the depth and the "considered, possibly animated" gradient are not. He overrode
`00-rules.md`'s gradient ban **for buttons specifically**, so this is now permitted where it was not.
The ban still holds for page backgrounds and decorative glows.

### A-07 · Главная's content, its connect animation and the subscription pill
**⏳ IN FLIGHT (as of 22:26) · owner A4 and E**

He asked for the pill with the traffic figure, the subscription info block (provider name with its
emoji, the auto-update timestamp, the operator's notice, support + Telegram actions, refresh + pin +
delete), the account nickname as the subscription's name, and the connect animation — all as they
were. As of 22:26 the wave that owns Главная has landed the meta-bar carousel, the embedded server
list, `confirmDeleteSubscription`, `toggleHomePin`, the uptime clock and `markAllServersTesting` back
into `ui/HomeFragment.kt`, and `strings_home.xml` / `fragment_home.xml` are moving with it.
**Do not start work here.** Verify at the gate against his list item by item; the one to check
hardest is «при подтягивании подписки с акка писался ник подписки», which is a naming rule, not a
layout.

### A-08 · The login composition
**⏳ IN FLIGHT (as of 22:17) · owner C3 · appears addressed**

`res/layout/layout_auth_gate.xml:70-74` and `:281-285` now float the intro + actions between weighted
spacers (3 above, 4 below) inside `gate_scroll`, which is the fix for "buttons at the very bottom,
headline stranded at the top". `LoginActivity.kt` was last written at 22:17. Confirm at the gate.

---

## 2 · The font — «кривой шрифт местами», «шрифт какой-то толстый»

The two-face split is real and correct in `res/values/styles.xml`: Golos Text draws Russian, Space
Grotesk draws figures and Latin. I measured the vendored binaries' cmaps to check the claim it rests
on, and it holds exactly:

| File | codepoints | in U+0400-U+04FF |
|---|---|---|
| `golos_text_regular/medium/bold.ttf` | 566 each | **170** each |
| `spacegrotesk.ttf` | 735 | **0** |
| `montserrat_thin.ttf` | 707 | **0** |

So a Russian string that reaches Space Grotesk is not styled — it is handed to whatever face the OS
picks. That is what "wrong font in places" looks like. Four holes remain.

### A-09 · Twelve sub-screens draw their Russian title in the Cyrillic-free face
**high · confirmed open · the highest value-per-byte item in this register**

`res/values/themes.xml:302` already binds `toolbarStyle` to `Widget.Departament.Toolbar` — the fix —
and **`res/layout/activity_base.xml:19` overrides it inline**:

```xml
app:titleTextAppearance="@style/ToolbarBrandTitle"
```

`ToolbarBrandTitle` (`styles.xml:281-282`) is `@font/space_grotesk`. Every screen that inflates this
host through `setContentViewWithToolbar` puts a Russian title in it:
`BuyTariffActivity`, `ServerActivity`, `ProviderSettingsActivity`, `SettingsActivity`,
`LocalProxyActivity`, `DeviceManagementActivity`, `TaskerActivity`, `ScannerActivity`,
`PaymentHistoryActivity`, `tv/TvSendActivity`, `tv/TvReceiveActivity` (11 screens plus
`ui/BaseActivity.kt` itself). «Настройки провайдеров», «Купить подписку», «История платежей»,
«Устройства» and «Дополнительно» are all drawn in a fallback face today.

The style's own comment says so (`styles.xml:276-279`) and nobody acted. Delete the inline attribute;
the theme binding takes over. Same file, three more deltas worth taking in the same edit:
`:6` `fitsSystemWindows="true"` fights the one inset strategy, `:11` `layout_height="?attr/actionBarSize"`
is a fixed height on a text-bearing bar (clips at font scale 200%), and `:28`
`app:indicatorColor="@color/color_fab_active"` is a raw colour where a `?attr` belongs.

### A-10 · Ten synthetic-bold labels
**medium · confirmed open**

`android:textStyle="bold"` on a face that ships a real 700 master makes the platform smear the 400
master instead — heavier, muddier, and different from every other bold on the screen. That is the
«шрифт какой-то толстый» failure mode, and `styles.xml`'s own header bans it outright.

`layout/item_buy_option.xml:36`, `layout/activity_buy_tariff.xml:302`, `layout/activity_tv_receive.xml:47`,
`layout/activity_account.xml:77,126,277,354`, `layout/item_payment.xml:81`, `layout/toast_status.xml:20`,
`layout/activity_tv_send.xml:94`. Four of them are on the Аккаунт tab, which is the screen he praised.

### A-11 · Sixty-six raw `android:textSize` values, on eleven layouts, off the ramp
**medium · confirmed open**

A layout that sets its own size bypasses the role's weight, tracking, line height and face at once.
Distinct sizes in use: 11, 12, 13, 14, 15, 16, 18, 20, 22sp — five of which are not steps on the ramp
at all. Worst offenders, both reachable from the Настройки tab:
`layout/activity_local_proxy.xml` (37), `layout/activity_provider_settings.xml` (15). Then
`activity_tv_send.xml` (3), `layout_subscription_meta_bar.xml` (2), `item_recycler_main.xml` (2),
`activity_tv_receive.xml` (2), and one each in `toast_status`, `layout_transport`,
`layout_servers_header`, `item_buy_tariff`, `activity_account`.

`res/layout/fragment_home.xml` is the only layout in the tree still setting `android:fontFamily`
(1 hit) — ⏳ in flight, note it for the gate.

### A-12 · `montserrat_thin.ttf` is vendored and referenced from nowhere
**low · confirmed open** — 707 codepoints of dead weight in every APK. `res/font/montserrat_thin.ttf`.

### A-13 · Space Grotesk falls back to Light 300 on API 24-25
**low · documented in source, confirmed open**

`res/font/space_grotesk.xml` is one variable file behind three `<font>` entries pinned with
`fontVariationSettings`. That attribute is API 26+ (`app:` namespace) / API 28+ (`android:`), and
`minSdk = 24`. On 24-25 every brand run draws at the file's 300 default, so the Display/Chip weight
difference does not exist there. The fix is vendoring baked static masters; the file's own comment
tracks it as debt.

---

## 3 · Features the user had and no longer has

### A-14 · Six whole-list actions are declared, hidden, and dispatched by nothing
**high · confirmed open**

`res/menu/menu_main.xml` still declares `group_server_list` — «Найти выбранный», «Сортировать по
задержке», «Экспортировать все», «Удалить дубликаты», «Удалить недоступные», «Удалить все серверы» —
and `ui/MainActivity.kt:740` hides the whole group unconditionally
(`menu.setGroupVisible(R.id.group_server_list, false)`). `onOptionsItemSelected` (`:1014-1050`) has no
branch for any of the six ids. The handlers behind them are gone too:
`MainViewModel.exportAllServer()` (`viewmodel/MainViewModel.kt:294`) now has zero callers.

These were restored by the salvage commit and removed again when the Серверы destination went. With
the server list back on Главная (⏳ A-07) they have a surface again. **Decide per action, then either
wire it or delete the menu item — a declared item that can never be shown is not a feature.**

### A-15 · The subscription editor is unreachable: two Activities with no door
**high · confirmed open**

`ui/SubSettingActivity.kt` — the provider/subscription list editor — has **zero** `::class.java`
references in `java/`. `ui/SubEditActivity.kt` is referenced only from `SubSettingActivity` itself.
Both are declared in `AndroidManifest.xml:110` and `:120`. The Настройки tab's «Настройки
провайдеров» row goes to `ProviderSettingsActivity` instead (`ui/SettingsTabFragment.kt:108`), which
is the *global* provider settings, not the per-subscription editor.

So on the phone there is no way to rename a subscription, edit its URL, set its own auto-update
interval, or set its own User-Agent. Deleting one is back on the Главная card (⏳ A-07), which closes
the owner's A2 symptom; these four do not come with it.

### A-16 · Server search and the protocol filter are gone
**medium · confirmed open**

`MainViewModel.filterConfig()` (`viewmodel/MainViewModel.kt:744`) and `applyProtocolFilter()`
(`:756`) have **no callers**. The three layouts that carried the controls are orphaned:
`res/layout/layout_servers_header.xml` (the search field), `res/layout/dialog_config_filter.xml`,
`res/layout/layout_servers_empty.xml`. On an account with many servers the list is now unnavigable.

### A-17 · Delete / edit / share / QR live only on a long-press
**medium · confirmed open**

`ui/MainRecyclerAdapter.kt:252-254` invokes `onItemLongClick`, and `ui/HomeFragment.kt:733` wires it
to `mainHost.showServerActions(guid)` — so the sheet is reachable again and this is no longer the
dead end D04 described. But long-press is a hidden affordance and it is the app's **only** route to
those four actions. The owner's «удалять почему-то я тоже не могу» was a discoverability report as
much as a functional one. Give the row an explicit trailing control, as `bugs-android-confirmed.md`
D04's "what to change" already recommended.

---

## 4 · The money path

### A-18 · Nothing prevents a double charge
**blocks-release · D10 / U-14 · confirmed open**

`ui/BuyTariffActivity.kt:113` binds `btnPay` straight to `onPayClicked()`. Neither `onPayClicked`
(`:457`) nor `onMethodPicked` (`:482`) sets an in-flight flag or disables the button, and neither
does the `AccountFragment` top-up path (`ui/AccountFragment.kt:628-644`). The indicator that would
have shown it is bound and never shown: `progressBuy` (`BuyTariffActivity.kt:94`) is set `GONE` at
`:176`, `:187`, `:197`, `:218` and `VISIBLE` at **no line in `app/src/main`**.

On a slow connection the screen does not change, the user taps «Оплатить» again, and pays twice.
**Fix:** one `isPaying` flag that disables the button and shows `progress_buy`, cleared on both
outcomes. **Must not break:** `awaitingPaymentError` (`:484`) must stay armed across the request or a
failure goes back to being silent.

### A-19 · A balance payment reports success without reading the status the backend returned
**high · U-13 · confirmed open**

`viewmodel/AccountViewModel.kt:344-346`:

```kotlin
fun payWithBalance(req: PaymentRequestDto, onDone: () -> Unit = {}) = viewModelScope.launch {
    repo.payWithBalance(req).onSuccess { onDone() }.onFailure { report(it) }
}
```

`PaymentResultDto.status` is never inspected. Both callers treat a 200 as a purchase, and
`BuyTariffActivity.onMethodPicked` (`:490-505`) calls `finish()` on it — so a `200 {status:"failed"}`
closes the buy screen and tells the user they own a subscription they did not buy.

### A-20 · Rotating with the payment-method sheet open crashes on pick
**high · D11 / U-19 · confirmed open**

`ui/PaymentMethodSheet.kt:155` parks the picker lambda in a process-static
`ConcurrentHashMap<Long, (String)->Unit>`; `onDestroy` (`:132-140`) deliberately keeps the entry when
`isChangingConfigurations`, and `onCreate` (`:52-55`) re-binds it. Both callers hand it a lambda that
captures the **old** host:

- `ui/AccountFragment.kt:628-644` captures `viewModel` (a `by viewModels()` that dies with the
  fragment), `toastSuccess`/`toastError` (→ `requireContext()`) and `::openCheckout`.
- `ui/BuyTariffActivity.kt:474-479` captures the Activity through `onMethodPicked`.

After a rotation that instance is detached and `requireContext()` throws `IllegalStateException`.
**Fix:** resolve the host from the `FragmentManager` at pick time, or replace the static lambda with
`setFragmentResultListener`. **Must not break:** the process-death case must keep degrading to
"dismiss without firing" (`:31-32`), and `onDestroy` must keep dropping the entry on a real dismissal
so the map cannot grow.

### A-21 · A failed payment leaves a live «Итого» and «Оплатить» for an invisible selection
**high · D09 / U-18 · confirmed open**

`ui/BuyTariffActivity.kt:206-245` (`renderTariffs`) rebuilds `tariffsContainer` and clears
`checkMarks` / `optionRows`, and never touches `selectedTariff`, `selectedOption`, `extraDevices` or
`checkoutCard.visibility`. The rebuild is triggered by any `error` transition
(`viewModel.tariffs.combine(viewModel.error).collect { renderState() }`, `:135-137`) and
`clearError()` fires it a second time. `selectTariff` (`:342-344`) returns early when the tariff is
already selected, so re-tapping the same card cannot recover the paint. The user is left with every
card neutral and a live «Оплатить» for a selection that is not on screen.

### A-22 · Declined, cancelled and timed-out checkouts end in silence, and the poll restarts forever
**high · U-17 / P9+P10 · confirmed open**

`ui/AccountFragment.kt:833-845` and `ui/BuyTariffActivity.kt:586-593` run a fixed `repeat(6)` /
`repeat(5)` × 8 s poll that **never inspects a payment status**, then hides the hint with no verdict,
no copy and no action. The `orderId` from `PaymentInitDto` is captured and discarded.

Compounding: `ui/AccountFragment.kt:826` re-arms the poll in `onResume` (`if (pendingPayment)
startPaymentPolling()`), while the job lives on `viewLifecycleOwner.lifecycleScope` and is cancelled
by the tab switch **before** `pendingPayment = false` at `:842` — so the 48-second window restarts
indefinitely every time the user comes back to the tab.

### A-23 · The Devices page is never told which subscription it is for
**medium · U-15 · confirmed open**

`ui/DeviceManagementActivity.kt:235` declares `EXTRA_REMNAWAVE_UUID` and `:58` reads it. The only
launcher is `ui/AccountFragment.kt:197` — `openSubScreen(DeviceManagementActivity::class.java)`,
which passes no extra. On a multi-subscription account the page shows the root subscription's devices
and unlinks against the root uuid whatever card the user was on. This is the exact per-item scoping
rule the repo's own `CLAUDE.md` warns about. **The desktop half of this was fixed; Android was left
behind.**

### A-24 · Every subscription card in the carousel shows the ROOT subscription's device count
**medium · U-16 / P16 · confirmed open**

`ui/SubscriptionPagerAdapter.kt:24` types the hook as `(SubInfoDto) -> Int` and `:88` calls it per
card, but `ui/AccountFragment.kt:174` supplies `resolveUsedDevices = { viewModel.deviceCount.value ?: 0 }`
— a lambda that **ignores the `SubInfoDto` it is handed**. The fragment also fetches devices for
`list.firstOrNull()` only. Same root cause as A-23.

### A-25 · Three private currency formatters decide the symbol from the currency code
**low · U-20 · confirmed open**

`ui/BuyTariffActivity.kt:632`, `ui/AccountFragment.kt:850`, `ui/adapter/PaymentsAdapter.kt:87`. The
owner's ₽ decision is enforced by none of them; one formatter should own it.

---

## 5 · The connect flow

The state machine moved into `ui/HomeFragment.kt` and most of the group closed with the move.
Cancelling a connect works (`handleConnectAction:2097-2105`); the no-server guard idles the UI
(`startV2Ray:2141-2149`); the watchdog is cancelled on every definitive state
(`observeTunnel`, `:1180`). Three things did not come across.

### A-26 · Cancelling Android's own VPN dialog is reported as a connection failure
**high · D08 / U-23 · confirmed open**

`ui/HomeFragment.kt:410-414`:

```kotlin
private val requestVpnPermission = registerForActivityResult(StartActivityForResult()) {
    if (it.resultCode == Activity.RESULT_OK) {
        startV2Ray()
    }
}
```

**No `else`.** A non-`RESULT_OK` result is dropped, leaving `connectInProgress = true` and the
watchdog armed exactly as `handleConnectAction` left them. The disc spins «Подключение…» for a full
20 s and then says «Не удалось подключиться» — for something the user cancelled. A cancelled action is
not a failure. **Fix:** add the else — `connectInProgress = false`, `cancelConnectWatchdog()`,
`applyRunningState(false, false)`, and either say nothing or say «Разрешение на VPN не выдано.»
**Must not break:** proxy-only mode must keep skipping the prepare entirely
(`startVpnWithPermission`, `:2128-2139`).

### A-27 · The no-server backstop idles the UI but leaves the watchdog armed
**low · D07 residue · confirmed open**

`ui/HomeFragment.kt:2141-2149` clears `connectInProgress` and repaints, but does not call
`cancelConnectWatchdog()`. The watchdog armed one frame earlier still fires 20 s later and sets
`tunnelError = true`, so the screen says «Не удалось подключиться» for an attempt that never started.
The disc is disabled in `NO_SERVER`, so the normal path cannot reach it; a selection cleared between
the tap and the permission callback can. One line.

### A-28 · Re-tapping the already-selected server does nothing at all
**low · U-25 / P19 · confirmed open**

`ui/MainActivity.kt:917-919` — `if (guid == selected) return` precedes every piece of feedback. No
haptic, no snackbar, no connect. The desktop half was fixed (it connects explicitly on a re-tap while
disconnected); Android answers a deliberate tap with silence.

### A-29 · The uptime clock counts the old session while the status says «Подключение…»
**low · U-24 · confirmed open**

`ui/HomeFragment.kt:2204-2210` (`applyRunningState`) no longer returns early on `isLoading`, and the
timer is stopped in the `isRunning` observer's else branch (`:1202`) — so a plain disconnect is
correct. The restart path is not: `applySelectionToRunningTunnel` sets `isLoading = true` while the
tunnel is still up, so for the length of the stop-then-start the clock keeps counting the previous
session under the word «Подключение…».

---

## 6 · Persistence

### A-30 · The "testing" sentinel is written to disk, so rows spin forever across restarts
**medium · D05 · confirmed open**

`ui/HomeFragment.kt:966` (`markAllServersTesting`) persists the UI sentinel `-2L` into
`serverAffStorage` for **every** row in the cache, including the rows the tests skip (PolicyGroup and
balancer entries, unparseable CUSTOM profiles). Nothing ever clears it for those rows, and it is in
MMKV, so the spinner survives a restart.

`MmkvManager.removeInvalidServer` (`handler/MmkvManager.kt:323-343`) then deletes anything with
`testDelayMillis < 0L` — which includes `-2`. So «Удалить недоступные» deletes servers that were never
tested. That action currently has no entry point (A-14), which is the only reason this is not P0
today; restoring the action without fixing this ships data loss.

**Fix:** hold "testing" in memory (a ViewModel set), never in MMKV; and make `removeInvalidServer`
test `testDelayMillis < 0 && testDelayMillis != TESTING`. **Must not break:** the spinner rendering
keyed on `-2L` in the adapter, and `clearAllTestDelayResults`.

### A-31 · `serversCache` is the filtered list and gates the nav bar and Главная
**medium · D15 · latent, confirmed present**

`viewmodel/MainViewModel.kt:244-274` (`updateCache`) applies `keywordFilter` and `protocolFilter`
while building `serversCache`, and both `MainActivity.updateBottomNavVisibility` (`:770`) and
Главная's state resolver read `serversCache` to answer "does this device have any servers at all".
A filtered-to-zero list is indistinguishable from "no servers", so typing in a search box empties
Главная into the onboarding gate and hides the whole bottom navigation.

Nothing writes either filter today (A-16), so the symptom is unreachable — but restoring search
without fixing this re-ships the bug. Gate on the unfiltered stored count
(`MmkvManager.decodeAllServerList()`); keep `serversCache` for whatever list is on screen.

---

## 7 · Localisation

### A-32 · Picking «English» gives a half-Russian app
**high · U-26 · confirmed open, measured**

`res/values/arrays.xml:140-151` offers Система / Русский / **English**. There is no `values-en`, so
English resolves to `values/` — and `values/` now carries **847 Russian strings** across 20 files,
because every departament screen was written straight into the default bucket:

| File | Russian strings in `values/` |
|---|---|
| `strings_editors.xml` | 277 |
| `strings_account.xml` | 84 |
| `strings.xml` | 80 |
| `strings_home.xml` | 76 |
| `strings_settings_advanced.xml` | 60 |
| `strings_menu_actions.xml` | 38 |
| `strings_auth.xml` | 37 |
| `strings_local_proxy.xml` | 35 |
| …12 more | 160 |

Of 1254 translatable keys in `values/`, **372 have no `values-ru` entry** — harmless on a Russian
device (the default is already Russian), fatal everywhere else. The five other shipped locales
(`ar`, `bn`, `fa`, `vi`, `zh-rCN`, `zh-rTW`) carry ~352 keys each, so they are in the same state.

**The good news, verified:** the reverse hazard is clean. There are **zero** keys where `values/` is
Russian and `values-ru/` shadows it with leftover English, and `values/strings_home.xml` and
`values-ru/strings_home.xml` are key-for-key and body-for-body identical (75/75). Whoever fixes this
does not have to untangle a shadowing mess first.

**Fix, pick one and state it:** either make `values/` the Russian master and remove the English
option and the stale locale folders, or move the 847 Russian strings into `values-ru/` and write
English into `values/`. The first is smaller and matches the product; the second is what the
five vendored locales assume.

---

## 8 · Release blockers

None of these is visible in a debug build, which is the only build this branch has ever produced.

### A-33 · The release APK is debug-signed
**blocks-release · confirmed open** — `V2rayNG/app/build.gradle.kts:70`:
`signingConfig = signingConfigs.getByName("debug")`, with the comment saying it is deliberate for
testing. The debug key is regenerated per machine/CI run, so two "releases" cannot upgrade each other.

### A-34 · All five playstore APKs get the same versionCode
**blocks-release · confirmed open** — `V2rayNG/app/build.gradle.kts:127-128`:

```kotlin
val versionCodes =
    mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)
```

Every ABI resolves to `4 * 1_000_000 + 731 = 4000731`. Google Play refuses a multi-APK upload where
the codes collide. The fdroid branch two blocks up (`:109-112`) has the distinct values; copy them.

### A-35 · Every launcher shortcut is dead, in both flavours
**blocks-release · confirmed open, and worse than reported**

`applicationId` is now `com.departamentvpn.app` (`build.gradle.kts:13`), while
`res/xml/shortcuts.xml:14,28,42,56` still say `android:targetPackage="com.v2ray.ang"` and
`src/fdroid/res/xml/shortcuts.xml:15,30,45,60` say `com.v2ray.ang.fdroid`. Neither package exists.
`ScSwitchActivity`, `ScScannerActivity`, `ScStartActivity` and `ScStopActivity` have zero code entry
points, so the shortcut XML is their **only** door — all four long-press launcher actions do nothing
in every build. **Fix:** `${applicationId}` in both files.

### A-36 · The fdroid flavour's launcher label is «v2rayNG (F-Droid)»
**blocks-release · confirmed open** — `V2rayNG/app/src/fdroid/res/values/strings.xml` overrides
`app_name`. Delete the file and `src/main`'s `departament` applies.

### A-37 · Minification is off and there are no keep rules to turn it on with
**high · confirmed open**

`build.gradle.kts:63` `isMinifyEnabled = false`; `app/proguard-rules.pro` is 20 lines of commented
AGP boilerplate and **zero rules**. This app reflects across Gson DTOs (the Xray config's field names
*are* the wire format), a gomobile/libv2ray JNI surface, the hev-socks5-tunnel JNI entry points, and
WorkManager. MMKV and WorkManager ship their own consumer rules and are safe; the other three are not.
`release-android.md` §2.3-2.6 has the rules written out — they need applying, and the flag flipping
only after.

### A-38 · `allowBackup="true"` with no backup rules, over a Keystore-sealed session store
**medium · confirmed open** — `AndroidManifest.xml:45` sets `allowBackup="true"`; there is no
`android:dataExtractionRules`, no `android:fullBackupContent`, and no `res/xml/backup_rules.xml` /
`data_extraction_rules.xml`. `departament_auth` is encrypted with a device-bound Keystore key, so the
backed-up bytes are unreadable ciphertext on the restoring device — a restore hands the user a
corrupt session file rather than a signed-in app.

### A-39 · `QUERY_ALL_PACKAGES`
**medium · confirmed open** — `AndroidManifest.xml:28`. A Play review blocker unless the per-app proxy
picker is declared as the qualifying use, or replaced with a `<queries>` element.

---

## 9 · Live doors

### A-40 · A web page can connect, disconnect, import a server and rewrite the routing rules
**blocks-release · U-01 · confirmed open**

`AndroidManifest.xml:181-189` exports `UrlSchemeActivity` for `depv://` with `BROWSABLE`.
`ui/UrlSchemeActivity.kt:71-137` dispatches `connect|open`, `disconnect|close`, `toggle`,
`import/{base64}`, `add/{url}`, `routing/add/{base64}` and `routing/onadd/{base64}` — **with no
confirmation on any of them**. A link in a browser, a chat message or a QR code can therefore stop
the user's VPN, install and select a server of the attacker's choosing, or replace the routing
rulesets and restart the tunnel onto them. Every mutating destination needs a confirmation sheet that
names what it will do.

### A-41 · Any installed app can toggle the VPN
**high · U-05 · confirmed open**

`AndroidManifest.xml:268-279` — `WidgetProvider` is `exported="true"` with an intent filter for
`${applicationId}.action.widget.click` and no `android:permission`. `receiver/WidgetProvider.kt:67-74`
acts on it by calling `stopVService` / `startVServiceFromToggle`. Any app that knows the package name
can broadcast it. Add a signature-level permission, or route the widget click through a
`PendingIntent` only the widget host holds.

---

## 10 · Design-law and craft debt

### A-42 · `ic_warning` and `ic_error` do not exist, so warn and error share the info glyph
**low · U-38 · confirmed open** — `res/drawable/` has no `ic_warning`, no `ic_error` and no
`ic_arrow_back`. The status strip's warning and error severities carry the info glyph in the correct
tone (`ui/HomeFragment.kt`, `paintCondition`), which keeps two channels rather than one but is not
the design. Three icons.

### A-43 · One hardcoded `contentDescription` in the tree
**low · U-39 · confirmed open** — `res/layout/view_toolbar.xml:70`
`android:contentDescription="Назад"`. Every other content description is a resource. It is also the
one string that never gets localised.

### A-44 · Two built components with zero consumers
**low · confirmed open** — `res/layout/view_chip.xml` and `res/layout/view_meter.xml` are inflated by
nothing and included by nothing. Either give them a binder in `ui/component/` or delete them.

### A-45 · Orphan resources left by removed screens
**low · confirmed open**

Layouts with no inflater, no binding reference and no `<include>`:
`dialog_config_filter`, `layout_servers_empty`, `layout_servers_header`, `layout_setting_row`,
`layout_setting_toggle_row`, `preference_with_help_link`, `toast_status`, `view_chip`, `view_meter`.
Strings: `title_pref_show_memory` and `summary_pref_show_memory` (`res/values/strings.xml:366-367`,
`res/values-ru/strings.xml:345-346`) plus the `AppConfig.PREF_SHOW_MEMORY` constant
(`AppConfig.kt:58`) — the key now has **neither a reader nor a writer**, so the RAM panel D16
described cannot exist. Decide it out loud (the settings spec dropped it) and delete all four.

---

## 11 · Closed since the reports that raised them — do not re-file

Each was verified fixed in today's source. Listed so the next wave does not spend a day on them.

| Was | Now |
|---|---|
| **D01** Sign-in imports nothing and prunes everything | `AccountRepository.autoImportSubscriptions` (`:108-123`) merges `/subscription` and `/subscription/all`; `SubscriptionSyncManager.importAll` reads through `.raw()` (`:50`) and **never prunes on an empty candidate set** (`:101`). |
| **D02** An expired JWT deletes every subscription and server | `refreshProfile`'s 401 now calls `AccountSession.endSession()` (`AccountRepository.kt:75`), which clears the session only (`AccountSession.kt:65-68`). `wipe()` is explicit sign-out alone. |
| **D03** Quick tab switches strand the highlight | `showTab` (`MainActivity.kt:620-625`) has no animation left and `settleTabs` is the single authority on visibility. |
| **D04** Server rows have no long-press, so the actions sheet is unreachable | `MainRecyclerAdapter.kt:252-254` invokes it; `HomeFragment.kt:733` wires it. (Discoverability survives as A-17.) |
| **D06** Rapid taps issue a second start and push the deadline out | `handleConnectAction` (`HomeFragment.kt:2097-2105`) cancels an in-flight connect. |
| **D12** Sign-out leaves the account fragment collecting | `AccountFragment.onAccountState` → `onSessionCleared` (`:261-300`) cancels the poll, clears the ViewModel and blanks the render; tabs are `add`+`hide`, found by tag, so a recreate cannot build a second instance (`MainActivity.syncTabFragments:555-588`). |
| **D13** Back minimises from every tab on API ≤34 | `onKeyDown` (`MainActivity.kt:1347-1353`) handles `KEYCODE_BUTTON_B` only; BACK belongs to the one `OnBackPressedCallback` at `:295-306`. |
| **D14** «Удалить все серверы» loses the провайдер ordering | `MmkvManager.removeAllServer` (`:303-317`) removes only `SUB_SERVERS_*` and `KEY_SELECTED_SERVER`; no `clearAll()` on the shared store. |
| **D16** 29 preference keys with no editing UI | `res/xml/pref_settings.xml` is down to 25 keys and every one resolves to a literal in `java/`; `SettingsActivity` has a row (`SettingsTabFragment.kt:123`). |
| **D17** `CheckUpdateActivity` / `LogcatActivity` / `SettingsActivity` unreachable | Rows at `SettingsTabFragment.kt:123-125`. (`SubSettingActivity` was **not** given one — that is A-15.) |
| **D18** No sign-out anywhere | `AccountFragment.confirmSignOut` (`:668-689`) → `beginSignOut` (`:700`) → `AccountViewModel.logout` (`:457`). |
| **D19** An unparseable XRAY_JSON body wipes the provider's servers | `AngConfigManager.kt:618-642` stages the parse first and deletes only on `staged.isNotEmpty()`. |
| **D21** Dead `R.id.sub_update` branch | Gone from `onOptionsItemSelected`. |
| **D22** Dead «Привязать Telegram» banner | The live entry point is `home_gate_link_telegram`; the banner and its handlers are out of `MainActivity`. |
| **D23** `serverRawStorage` never deleted | Cleared in `removeAllServer` (`MmkvManager.kt:314`), `removeServer` (`:215`) and `removeServerViaSubid` (`:239`). |
| **D25** Cross-process MMKV loses the session at random | `AuthTokenStore` opens `MULTI_PROCESS_MODE` (class comment `auth/AuthTokenStore.kt:25-31`), like every store in `MmkvManager` (`:35-41`). |
| **D26** One Keystore hiccup opens the encrypted file with no key | `KeystoreKeyProvider.CryptKeyState` (`:39-58`) distinguishes `Available` / `Absent` / `Unsealable`; an unsealable store is **not opened**, and only a successful open is cached (`AuthTokenStore.kt:33-50`). |
| **D27** The service receiver is registered once per recreate | `MainViewModel.broadcastRegistered` (`:163`, guarded at `:176-181`); the `MSG_REGISTER_CLIENT` handshake still fires every time (`:180`). |
| **U-21/U-22** Connect fires on every tap; the no-server guard leaves the UI connecting | Both closed by the move into `HomeFragment` (residue: A-27). |
| 22 dash/ellipsis copy hits (`00-rules.md` 9.7) | Zero remain in string bodies; the ` - ` matches left in `res/values/` are all inside XML comments. |
| Russian shadowed by leftover English in `values-ru/` | Measured across every paired file: **zero** keys where `values/` is Russian and `values-ru/` is English. |

---

## 12 · Refuted — keep them out

From `bugs-android-confirmed.md` §2 and the `verify-*.md` reports. Re-filing any of these wastes a
wave.

| Claim | Verdict |
|---|---|
| «Привязать Telegram» is a lost feature | **Refuted.** The removal of the signed-out banner was deliberate; a live entry point exists on the gate block. The dead banner was real and has since been deleted — the *feature* was never lost. |
| Changing per-app proxy never restarts the tunnel | **Refuted.** `PerAppProxyActivity.kt:239-242` calls `SettingsChangeManager.makeRestartService()`; the shell consumes it (`MainActivity.kt:246-248`). |
| Rotation replays the connect state: timer resets, animation replays, spurious toast | **Refuted, all three halves.** The confirm is gated on `liveTransition`, the toast on a known prior state, and the uptime origin is persisted in `KEY_CONNECTION_START`. |
| 19 amputated menu actions | **Closed by the salvage commit**, then reduced again by the tab removal — the live version of that concern is A-14, which is about six specific ids, not nineteen. |
| «Ping all» / «real-ping all» exists nowhere | **Refuted.** Two live entry points; adding a menu duplicate is a design question. |
| «Restart service» exists nowhere | **Not a defect.** `restartV2Ray` is reachable from the reconnect snackbar, from a core-config settings change, and from the `SettingsChangeManager` flag. An explicit control is a product decision. |
| Provider-settings toggles store a value and drive no behaviour | **Refuted.** All five consumers verified, including the sort order, which is applied through `SettingsManager.applyServerSortOrder()` and not through the getter a grep would find. |
| `locateSelectedServer` has zero callers | **Was closed**, then removed with the Серверы tab. It is now part of A-14, not a separate defect. |

---

## 13 · Needs a device to settle

The code fact is proven for each; only the run-time half is open.

| Item | What a device run must show |
|---|---|
| **A-30**, the testing sentinel | Add a PolicyGroup or an unparseable CUSTOM profile, run the latency check, restart — confirm that row still spins, and that «Удалить недоступные» (once A-14 restores it) deletes it. |
| **A-09**, the toolbar face | Open «Настройки провайдеров» on a device with and without Roboto as the fallback and compare the title against a Golos-drawn row title on the same screen. |
| **A-20**, the sheet crash | Open the payment-method sheet, rotate, pick a method. Expect `IllegalStateException` from `requireContext()`. |
| **A-32**, the localisation split | Set the in-app language to English and walk Аккаунт → Купить → Настройки → Дополнительно. Count the Russian strings; expect them everywhere the departament waves wrote. |
| Accessibility, contrast, touch targets, frame timing | **Not examined in this pass at all**, on any screen. TalkBack traversal, a measured contrast reading on a real panel, and a systrace. Their absence here is not a clearance. |

---

## 14 · Suggested order

1. **A-01** — a signed-out app showing selectable servers is the owner's own report and it is a data-integrity bug, not a paint bug. One call.
2. **The copy block: A-02, A-03, A-04, A-05.** Four small edits, all his words, all visible in the first ten seconds of the build he will run next.
3. **A-09.** Delete one XML attribute and eleven screens stop drawing Russian in the wrong face.
4. **A-18, A-19, A-20, A-21, A-22 together.** They are one state machine and one screen; fixing them apart re-opens each other.
5. **A-26** with A-27 — same handler, one edit.
6. **A-14 with A-30, A-15, A-16, A-17.** The list surface is back; decide what it carries, and fix the sentinel *before* restoring «Удалить недоступные».
7. **A-33 … A-37**, before anyone calls a build releasable.
8. **A-40, A-41.** Small, and they are open doors.
9. **A-32.** The largest single item, and the one that most wants a decision stated before any code.
10. **A-06, A-10, A-11**, and the rest of §10 as craft passes.

⏳ **A-07 and A-08 are not on this list**: they belong to waves that are mid-edit. Re-read them at the
gate against `OWNER-FEEDBACK-2026-07-27.md` §E and §C3 before signing either off.
