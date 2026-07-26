# Android sweep — cold start, session persistence, replayed state

Scope: the same class of defect the owner reported on PC ("launch the app and the session is gone,
the welcome / add-a-subscription screen is back, even though the subscription was already added").

Every finding below is derived from files actually read; each carries `file:line`. No code was
changed in this phase.

Files read: `ui/MainActivity.kt`, `ui/AccountFragment.kt`, `ui/BuyTariffActivity.kt`,
`ui/LoginActivity.kt`, `ui/BaseActivity.kt`, `viewmodel/MainViewModel.kt`,
`viewmodel/AccountViewModel.kt`, `viewmodel/AuthViewModel.kt`, `auth/AccountSession.kt`,
`auth/AccountCache.kt`, `auth/AuthTokenStore.kt`, `auth/AccountRepository.kt`,
`auth/SubscriptionSyncManager.kt`, `auth/KeystoreKeyProvider.kt`, `auth/BackendConfig.kt`,
`auth/DepartamentApiClientImpl.kt`, `handler/MmkvManager.kt`, `handler/AngConfigManager.kt`,
`handler/SubscriptionUpdater.kt`, `util/SubscriptionOrigin.kt`, `AngApplication.kt`,
`res/layout/activity_main.xml`.

---

## The question set applied to every user-visible state

| State | Survives process death? | Survives config change? | Replay mistaken for event? | MMKV read before async write? |
|---|---|---|---|---|
| `AccountSession.state` (logged in/out) | yes (MMKV) | yes (object) | **no — F1** | — |
| imported subscriptions / servers | **destroyed by 401 — F2** | yes | — | **yes — F1** |
| Home empty/welcome state | n/a (derived) | derived | — | **derived from a FILTERED list — F3** |
| bottom-nav visibility | n/a | derived | — | same as F3 |
| `isRunning` / connect hero | n/a | **no — F4** | **yes — F4** | — |
| connection uptime timer | n/a | **no — F4** | — | — |
| broadcast receiver registration | n/a | **leaks — F5** | — | — |
| Account tab (fragment + VM) | **no — F6** | **no — F6** | — | — |
| `pendingPayment` (checkout poll) | **no — F6** | **no — F6** | — | — |
| session token readability | **no — F8** | yes | — | — |
| `AccountCache` | n/a (process) | yes | — | **not account-scoped — F9** |
| dismissed CTA / collapsed list / carousel page | no | **no — F10** | — | — |

---

## F1 — CRITICAL. A subscription is imported **only** on a login transition observed by a live MainActivity. Everything else leaves the user signed in with zero servers.

`AccountRepository.autoImportSubscriptions()` has exactly one caller in the whole app:

- `MainActivity.onLoggedIn()` — `MainActivity.kt:1183-1188`
- reached only from `MainActivity.kt:1061`: `if (loggedIn && !accountLoggedIn) onLoggedIn()`
- `accountLoggedIn` is seeded at **field-initialisation** from the persisted session —
  `MainActivity.kt:120`: `private var accountLoggedIn = AccountSession.isLoggedIn()`

`AccountViewModel.autoImportSubscriptions()` (`AccountViewModel.kt:315`) is dead code — no caller.

Consequences, all provable:

1. **Process death during the Telegram login hop.** `openLoginScreen` launches `LoginActivity`
   (`MainActivity.kt:1134-1138`), which deep-links out to Telegram. MainActivity is then two levels
   down in the background and is a prime kill candidate. When the user returns, MainActivity is
   **constructed fresh**, so line 120 already reads `true`, the transition at line 1061 never
   happens, and the import never runs. The user is signed in, the Account tab shows their real
   subscription, and Home shows the onboarding welcome + "Купить подписку" — the owner's exact
   symptom.
2. **Any purchase.** `BuyTariffActivity` after a successful checkout only polls
   `viewModel.refreshProfile()` / `viewModel.loadSubscriptions()` (`BuyTariffActivity.kt:585-590`)
   — display-only calls. `AccountFragment.loadAll()` is the same (`AccountFragment.kt:188-196`).
   Neither imports, and neither calls `mainViewModel.reloadServerList()`.
3. **A subscription bought on the site or via the bot** is never picked up at launch: `onCreate`
   runs `SubscriptionUpdater.sync()` + `mainViewModel.reloadServerList()`
   (`MainActivity.kt:301-302`), which only refresh/read subscriptions that are *already* local.
4. **The dead end is sealed.** In the signed-in empty state the QR/clipboard buttons are hidden —
   `MainActivity.kt:1172-1173`: `empty.btnHomeAddQr.isVisible = !buyState`. So the only offered
   action is "Купить подписку", which (per 2) does not produce servers either.

The import is already idempotent — `SubscriptionSyncManager.importAll` keys by uuid and reuses the
stored guid (`SubscriptionSyncManager.kt:42-43`, `75`) — so running it on every start while signed
in is safe.

**Fix direction:** run the import (a) once per cold start whenever `AccountSession.isLoggedIn()`,
(b) after a confirmed paid purchase in both purchase screens, then `reloadServerList()` on the main
list. Keep the `accountLoggedIn` edge-detect for the *toast*, not for the import.

---

## F2 — CRITICAL. A `getMe` 401 deletes the user's subscriptions and servers, and the live UI is never told.

Chain:

1. `AccountFragment.loadAll()` → `viewModel.refreshProfile()` (`AccountFragment.kt:189`,
   `AccountViewModel.kt:93`).
2. `AccountRepository.refreshProfile()` catches `ApiError.Unauthorized` → `AccountSession.wipe()`
   (`AccountRepository.kt:71-73`).
3. `AccountSession.wipe()` → `subs.removeAllManaged()` (`AccountSession.kt:56`).
4. `SubscriptionSyncManager.removeAllManaged()` → `MmkvManager.removeSubscription(guid)` for every
   managed guid (`SubscriptionSyncManager.kt:83-91`).
5. `MmkvManager.removeSubscription` → `removeServerViaSubid` (`MmkvManager.kt:389-396`), which
   deletes every profile, every affiliation record, **and clears `KEY_SELECTED_SERVER`**
   (`MmkvManager.kt:219-234`, in particular `226-228`).

This is not a rare path. The JWT is 7-day and non-refreshable (`AuthTokenStore.kt:13-19`), and
nothing checks expiry locally: `isLoggedIn()` is a pure "is a token string present" test
(`AuthTokenStore.kt:122`), and `KEY_EXPIRES_AT` is never populated because
`AccountSession.onAuthenticated` calls `saveSession(jwt, user = profile)` with no `expiresAt`
(`AccountSession.kt:38` → `AuthTokenStore.kt:96-100` takes the `else` branch and *removes* the key).
So every user's token dies within 7 days, and the first Account-tab visit after that wipes their
local VPN configuration.

The UI is then inconsistent until the next launch:

- `applyAccountState` reacts to the LoggedOut flip (`MainActivity.kt:1041-1063`) but **never calls
  `mainViewModel.reloadServerList()`**, so `serversCache` still holds the now-deleted servers.
- Home keeps listing them; `updateBottomNavVisibility` still evaluates
  `serversCache.isNotEmpty()` as true (`MainActivity.kt:752`).
- Tapping a listed row resolves nothing — `MmkvManager.decodeServerConfig` returns null for a guid
  whose profile was deleted (`MmkvManager.kt:139-148`).
- Next cold start: `reloadServerList()` reads an empty store → `updateHomeEmptyState()` →
  welcome + "add a subscription" (`MainActivity.kt:717-741`).

**Fix direction:** an expired/dead token must clear the *session* only. The imported subscription
URLs are still valid config the user paid for and are re-importable; deleting them is a data-loss
side effect of an auth event. Separately, every session-state change must trigger
`mainViewModel.reloadServerList()` so the on-screen list can never outlive the store.

---

## F3 — HIGH. A search string on the Servers tab rewrites Home into the onboarding welcome state and hides the entire bottom nav.

`serversCache` is the **filtered** list, not the stored one — `MainViewModel.updateCache()` applies
`keywordFilter` (and `protocolFilter`) while building it (`MainViewModel.kt:160-190`).

- The Servers tab's own empty state correctly excludes filters:
  `MainActivity.kt:844-846` — `val filtersActive = mainViewModel.keywordFilter.isNotEmpty()` …
  `val showEmpty = serverCount == 0 && !filtersActive`.
- `updateHomeEmptyState()` has **no such guard**: `MainActivity.kt:718` —
  `val empty = mainViewModel.serversCache.isEmpty()`.
- `updateBottomNavVisibility()` has no guard either: `MainActivity.kt:752` —
  `val show = AccountSession.isLoggedIn() || mainViewModel.serversCache.isNotEmpty()`.

Repro: signed out, subscription imported from the clipboard (the owner's scenario) → Servers tab →
type anything that matches no server (`MainActivity.kt:688` →
`MainViewModel.filterConfig`, `MainViewModel.kt:595-601` → `reloadServerList` →
`refreshServerLists` → `updateHomeEmptyState`, `MainActivity.kt:797`). Result: the connect hero
disappears, the welcome heading + "add a subscription" card appear, and the whole bottom navigation
bar and its scrim are hidden (`MainActivity.kt:754-755`). Clearing the search restores everything —
but for the duration the app looks exactly like a fresh install that lost its subscription.

Latent twin: `protocolFilter` also feeds `serversCache` (`MainViewModel.kt:53`, `171`) and is
likewise absent from `filtersActive`. `applyProtocolFilter` (`MainViewModel.kt:607`) currently has
no caller, so this is dormant — it will reproduce the moment protocol chips are wired back up.

**Fix direction:** the onboarding/empty and nav-visibility decisions must be taken from the
*unfiltered* stored server count, never from `serversCache`.

---

## F4 — HIGH. Rotation / theme / language change replays the connect state: the uptime timer resets, the confirm animation replays, and a spurious "Прокси подключён" toast fires.

`MainViewModel` is retained across a configuration change (`by viewModels()`,
`MainActivity.kt:106`), but `setupViewModel()` runs again in every `onCreate`
(`MainActivity.kt:300`) and calls `mainViewModel.startListenBroadcast()`
(`MainActivity.kt:623`), whose **first statement** is `isRunning.value = false`
(`MainViewModel.kt:96`).

Sequence while the tunnel is up and the activity is recreated:

1. `isRunning` is forced to `false` in `onCreate`.
2. At STARTED the observer receives `false` with `lastRunningState == null`
   (`MainActivity.kt:135`, `567`) → `applyRunningState(false, false, animate = false)` →
   `applyIdleState` → `stopConnectionTimer()` →
   `MmkvManager.encodeSettings(KEY_CONNECTION_START, 0L)` (`MainActivity.kt:1962-1967`).
   **This erases exactly the value that was persisted to make the uptime survive the recreate**
   (`MainActivity.kt:1949-1956`, whose comment claims the opposite).
3. The service answers `MSG_REGISTER_CLIENT` with `MSG_STATE_RUNNING` (`MainViewModel.kt:99`,
   `674-687`) → the observer sees `true` with `prev == false`, so:
   - `showStatusToast(getString(R.string.toast_status_connected))` fires — `MainActivity.kt:584-587`
     — a "connected" toast on a connection that never changed;
   - `liveTransition` is true (`MainActivity.kt:575`) → `applyConnectedState(animate = true)` replays
     the whole crossfade + halo + sonar ring **and the `CONFIRM` haptic** (`MainActivity.kt:1658`);
   - `startConnectionTimer()` finds the stored value at `0L` and restarts the uptime from
     `00:00:00`.
4. `cancelHealthCheck()` then `scheduleHealthCheckIfEnabled()` (`MainActivity.kt:577`) re-arm the
   auto-fallback probe on every recreate, so a device that rotates a few times keeps re-probing.

This is the "replayed value mistaken for a live event" pattern that `lastRunningState`,
`consumeFastConnectEvent` (`MainViewModel.kt:84-88`) and the static `heroAssembled`
(`MainActivity.kt:226`) were introduced to prevent everywhere else — `startListenBroadcast` defeats
all of it by resetting the source of truth.

**Fix direction:** do not reset `isRunning` from `startListenBroadcast`; make the registration
idempotent (see F5) and let the retained value stand until the service reports otherwise.

---

## F5 — HIGH. The service broadcast receiver is re-registered on every activity recreate and unregistered once.

`MainViewModel.startListenBroadcast()` calls
`ContextCompat.registerReceiver(getApplication(), mMsgReceiver, mFilter, …)`
(`MainViewModel.kt:98`) with the *same* receiver instance and the *same* Application context every
time `MainActivity.onCreate` runs (`MainActivity.kt:300` → `623`). The ViewModel survives the
recreate, so nothing unregisters in between — `unregisterReceiver` only happens in `onCleared()`
(`MainViewModel.kt:106`), i.e. when the activity is finally finishing.

After N configuration changes the receiver is registered N+1 times, so each `MSG_STATE_*`,
`MSG_STATE_SPEED_UPDATE` and `MSG_STATE_DELAY_RESULT` broadcast is handled N+1 times, and the
Application context holds N+1 registrations for the lifetime of the process.

---

## F6 — HIGH. The Account tab is torn down and cold-reloaded on every configuration change, and a pending payment poll is silently lost.

`accountFragmentAdded` is a plain instance field (`MainActivity.kt:441`) and is **not** in
`onSaveInstanceState`, which persists only the selected tab (`MainActivity.kt:326-329`).

On a recreate with the Account tab selected (`MainActivity.kt:268-271` restores `KEY_SELECTED_NAV`),
`showTab` finds `accountFragmentAdded == false` and runs
`supportFragmentManager.beginTransaction().replace(R.id.group_account, AccountFragment()).commit()`
(`MainActivity.kt:454-459`) — while the FragmentManager has already restored the previous
`AccountFragment` from its own saved state. The restored fragment is therefore **removed**, taking
its `ViewModelStore` with it, so the fragment-scoped `AccountViewModel` (`AccountFragment.kt:65`)
is cleared and a brand-new one is created.

Consequences:

- `pendingFirstLoad` is back to `true` (`AccountFragment.kt:77`) → `renderHeroState` picks
  `Hero.SKELETON` (`AccountFragment.kt:389-406`) → the loading silhouette flashes on **every**
  rotation, theme switch and language switch, even though the data was already on screen.
- `loadAll()` re-issues five network calls (`AccountFragment.kt:188-196`).
- **`pendingPayment` (`AccountFragment.kt:82`) and `pollJob` (`AccountFragment.kt:83`) are lost.**
  `onResume` then sees `pendingPayment == false` (`AccountFragment.kt:640-643`) and the
  post-checkout poll never runs, so a payment confirmed by webhook is never picked up — the user
  paid and the app shows nothing. The identical hole exists in `BuyTariffActivity`
  (`BuyTariffActivity.kt:70-71` fields, `572-574` onResume): neither class implements
  `onSaveInstanceState`, so this also loses the poll whenever the process is killed while the user
  is paying inside the Custom Tab — the single most likely moment for a kill, since the app is
  fully backgrounded behind a browser.

**Fix direction:** persist `accountFragmentAdded` (or use `findFragmentById` before adding, and
`add` rather than `replace`), and persist `pendingPayment` in `onSaveInstanceState` in both payment
screens.

---

## F7 — HIGH. A non-empty subscription response that fails to parse deletes all of that subscription's servers.

`AngConfigManager.parseCustomConfigServer` deletes **before** it parses:

```
AngConfigManager.kt:610   if (serverList.isNotEmpty()) {
AngConfigManager.kt:612       if (!append) {
AngConfigManager.kt:613           MmkvManager.removeServerViaSubid(subid)
...
AngConfigManager.kt:617       for (srv in serverList.reversed()) {
AngConfigManager.kt:620           val config = CustomFmt.parse(rawConfig) ?: continue
```

If the body is a valid JSON array containing `outbounds` (`AngConfigManager.kt:603-605`) but every
entry fails `CustomFmt.parse`, the loop `continue`s through all of them, `count` stays `0`, and the
user is left with **zero** servers for that subscription. `removeServerViaSubid` also clears
`KEY_SELECTED_SERVER` (`MmkvManager.kt:226-228`), and the selection is only restored when
`count > 0` (`AngConfigManager.kt:629-632`).

The sibling code path does it correctly — parse into a list first, delete only if that list is
non-empty: `AngConfigManager.kt:379-382`
(`if (configs.isNotEmpty()) { if (!append) { MmkvManager.removeServerViaSubid(subid) } … }`).
The asymmetry is the defect.

This runs unattended, not just on a user action: `SubscriptionUpdater.sync()` on every
`MainActivity.onCreate` (`MainActivity.kt:301`), the per-subscription periodic worker scheduled at
import time (`SubscriptionSyncManager.kt:61`), and the launch task `updateAllNow`
(`SubscriptionUpdater.kt:110-113`). One malformed or changed panel response therefore wipes the
user's servers in the background, and the next launch shows the welcome screen — the owner's
complaint with no user action at all.

Note this deletion is *not* protected by the earlier fetch guards: an empty body returns early with
`failureCount` and leaves the store alone (`AngConfigManager.kt:833-835`), so this is specifically
the "server answered, content unusable" case.

---

## F8 — MEDIUM. A single Keystore hiccup makes the persisted session unreadable for the rest of the process, and silently signs the user out.

`AuthTokenStore.store` is opened once per process (`by lazy`, `AuthTokenStore.kt:34`):

```
AuthTokenStore.kt:38   val cryptKey = KeystoreKeyProvider.getOrCreateCryptKey()
AuthTokenStore.kt:39-42  if (!cryptKey.isNullOrBlank()) MMKV.mmkvWithID(ID, SINGLE_PROCESS_MODE, cryptKey)
AuthTokenStore.kt:43-44  else                            MMKV.mmkvWithID(ID)          // same file, NO key
AuthTokenStore.kt:44-50  catch (Throwable) -> MMKV.mmkvWithID(ID) / MMKV.defaultMMKV()
```

`getOrCreateCryptKey()` swallows **any** `Throwable` and returns `null`
(`KeystoreKeyProvider.kt:50-52`) — including a `KeyStore.getInstance("AndroidKeyStore").load(null)`
failure, which is a real transient on cold boot / user-unlock races and on OEM keystore quirks. The
fallback then opens the *same encrypted file* without its crypt key, so nothing decodes:
`getToken()` returns null, `isLoggedIn()` is false (`AuthTokenStore.kt:122`), `AccountSession.seed()`
returns `LoggedOut` (`AccountSession.kt:26-32`), and the user is shown the sign-in / welcome screen
despite a perfectly valid session on disk. This is the Android twin of the PC symptom.

It also does not self-heal within the process: the mis-opened instance is cached by `by lazy` and is
written to on the very next API call, because the auth interceptor calls
`AuthTokenStore.deviceId()` (`DepartamentApiClientImpl.kt:81`), which writes `KEY_DEVICE_ID`
(`AuthTokenStore.kt:65-71`).

Nothing anywhere distinguishes "no session" from "session unreadable", so the app can neither warn
the user nor retry with the key on the next launch.

**Fix direction:** treat a null crypt key as a hard "cannot read the session" condition rather than
silently degrading to a plaintext store on the same ID (separate ID, or fail closed and retry), and
surface it instead of rendering the onboarding screen.

---

## F9 — MEDIUM. `AccountCache` is not account-scoped and is never invalidated on sign-in.

- `AccountSession.onAuthenticated` (`AccountSession.kt:37-40`) and `AccountSession.wipe`
  (`AccountSession.kt:55-59`) never touch `AccountCache`.
- The only automatic clear happens inside `get()` **while logged out**
  (`AccountCache.kt:47-50`); `put()` has no such check (`AccountCache.kt:34-37`).
- `KEY_PAYMENTS` is a global key with no user component (`AccountCache.kt:74`, `88-90`), and
  `PaymentHistoryActivity` renders it cache-first (`PaymentHistoryActivity.kt:71`).
- The 401 wipe path calls only `AccountSession.wipe()` (`AccountRepository.kt:72`) — not
  `AccountCache.invalidateAll()`.

So: account A's payments are cached, A's token dies (401 → wipe, no cache clear), account B signs
in on the same device with no intervening `AccountCache.get`, and B's payment history screen renders
**A's payments** for up to the 1-hour TTL (`AccountCache.kt:27`).

Related: `AccountViewModel.logout()` — the only function that calls
`AccountCache.invalidateAll()` (`AccountViewModel.kt:400-417`) — **has no caller anywhere in the
app**. There is no sign-out entry point at all, which is also why the 401 wipe is the only way a
session ever ends (see F2).

---

## F10 — MEDIUM. Per-session UI state that the code deliberately treats as sticky is thrown away on every configuration change.

`onSaveInstanceState` persists only the selected tab (`MainActivity.kt:326-329`). Everything below
is a plain instance field and resets on rotation / theme change / language change / the app's own
`recreate()` (`MainActivity.kt:235-238`):

- `ctaDismissed` (`MainActivity.kt:122`) — the user dismisses the "link Telegram" banner
  (`MainActivity.kt:1019-1022`) and it comes straight back. The comment at `MainActivity.kt:121`
  says "dismissible for the current session", which a config change is not the end of.
- `homeListCollapsed` (`MainActivity.kt:110`) — the collapsed Home server list re-expands.
- `homeMetaPage` / `homeMetaSubIds` (`MainActivity.kt:114-115`) — the meta carousel snaps back to
  page 0, because `rebuildHomeMeta`'s restore-by-subscription-id logic
  (`MainActivity.kt:901`, `912`) reads the pre-change `homeMetaSubIds`, which is empty after a
  recreate.
- `homeListRevealed` / `serversListRevealed` (`MainActivity.kt:162-163`) — the "plays once per
  list" reveal stagger replays on every recreate. The intent is explicit two lines above
  (`MainActivity.kt:160-161`), and `heroAssembled` is correctly `static` for exactly this reason
  (`MainActivity.kt:225-226`) — the asymmetry shows these three were simply missed.

---

## F11 — LOW/MEDIUM. Token expiry is stored but never used, so a dead session renders as signed-in until the first `getMe`.

- `isLoggedIn()` is `!getToken().isNullOrBlank()` (`AuthTokenStore.kt:122`) — a presence test, not a
  validity test.
- `KEY_EXPIRES_AT` is only written when a non-null `expiresAt` is passed
  (`AuthTokenStore.kt:96-100`), and the single caller never passes one
  (`AccountSession.kt:38`), so `getExpiresAt()` (`AuthTokenStore.kt:109`) always returns `0` and has
  no caller anywhere.

So after the 7-day token dies, the app still shows the Account nav item, the account chip and the
signed-in onboarding shape (`MainActivity.kt:1086-1092`, `1162-1177`) until the user opens the
Account tab, at which point F2 fires and takes their servers with it.

---

## Suggested order of work

1. **F2 + F1** together — they are the two halves of the owner's complaint. Stop the 401 from
   deleting subscriptions; make the import run on every signed-in start and after a confirmed
   purchase; call `reloadServerList()` on every session-state change.
2. **F7** — a background worker must never delete servers it cannot replace.
3. **F3** — derive the empty/onboarding and nav-visibility states from the unfiltered count.
4. **F4 + F5** — stop resetting `isRunning` and stop re-registering the receiver.
5. **F6** — do not re-`replace` the Account fragment; persist `pendingPayment` in both payment
   screens.
6. **F8, F9, F10, F11** as follow-ups.
