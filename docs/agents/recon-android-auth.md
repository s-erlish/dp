# Recon: Android account/auth layer — map + sign-in design critique

Scope: `com.v2ray.ang.auth.**`, `viewmodel/AuthViewModel.kt`, `viewmodel/AccountViewModel.kt`,
`ui/LoginActivity.kt`, `ui/AccountFragment.kt`, `ui/BuyTariffActivity.kt`,
`ui/PaymentHistoryActivity.kt`, `ui/DeviceManagementActivity.kt`, `ui/PaymentMethodSheet.kt`,
and the relevant layouts/resources. Everything below is cited to a file + line I actually read.

Gradle root: `/home/user/dp/V2rayNG`. Package `com.v2ray.ang`, applicationId
`com.departamentvpn.app` (`/home/user/dp/V2rayNG/app/build.gradle.kts:14`), versionName `2.2.1`
(`build.gradle.kts:17`), minSdk 24 / targetSdk 37 (`build.gradle.kts:15-16`).

---

## 0. File inventory

| File | Role |
| --- | --- |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/auth/BackendConfig.kt` | Base URL, bot username, endpoint path constants |
| `.../auth/AuthManager.kt` | The 3 login flows as a `LoginState` state machine |
| `.../auth/AuthTokenStore.kt` | Encrypted MMKV: JWT, cached profile, device id, managed-sub map |
| `.../auth/KeystoreKeyProvider.kt` | AndroidKeyStore AES-GCM sealing of the MMKV crypt key |
| `.../auth/AccountSession.kt` | `StateFlow` of LoggedIn/LoggedOut; the only `wipe()` owner |
| `.../auth/AccountRepository.kt` | `Result`-returning wrapper over the API client |
| `.../auth/AccountCache.kt` | Process-lifetime TTL cache (devices, payments) |
| `.../auth/SubscriptionSyncManager.kt` | Imports backend subs into the v2rayNG subscription plumbing |
| `.../auth/DepartamentApiClient.kt` / `DepartamentApiClientImpl.kt` | Interface + OkHttp/Gson impl |
| `.../auth/ApiError.kt` / `ApiGson.kt` | Sealed error type; null-tolerant Gson |
| `.../auth/dto/{AuthDtos,SubscriptionDtos,PaymentDtos,MiscDtos,PublicDtos}.kt` | Wire DTOs |
| `.../viewmodel/AuthViewModel.kt` | Drives `AuthManager` for `LoginActivity` |
| `.../viewmodel/AccountViewModel.kt` | Everything else (profile, subs, tariffs, payments, devices) |
| `.../ui/LoginActivity.kt` + `res/layout/activity_login.xml` | The sign-in screen |
| `.../ui/AccountFragment.kt` + `res/layout/activity_account.xml` | Account tab (in-place fragment) |
| `.../ui/BuyTariffActivity.kt`, `PaymentHistoryActivity.kt`, `DeviceManagementActivity.kt`, `PaymentMethodSheet.kt` | Account sub-screens |
| `res/values/strings_auth.xml` | All 24 auth strings (Russian only) |

---

## (a) Which auth methods exist, and which actually work

### Summary table

| Method | API layer | UI wired | Reachable | Verdict |
| --- | --- | --- | --- | --- |
| Telegram deep-link + poll | yes | yes | yes | **Works** (primary path) |
| Email + password (site account) | yes | yes | yes | **Works** |
| TOTP / 2FA second step | yes | yes | yes (only after a 2FA-enabled password login) | **Works** |
| Google (`POST /client/auth/google`) | yes | **no** | no | **Dead code** |
| Magic link / passwordless | no | no | no | **Does not exist** |
| Registration in-app | no | opens a browser | yes | **Not really** — see below |
| Password reset / "forgot password" | no | no | no | **Missing entirely** |
| Logout | `AccountViewModel.logout()` exists | **no caller** | no | **Unreachable** |

### 1. Telegram deep-link login — works

`AuthManager.beginTelegramLogin()` (`AuthManager.kt:49-96`) is a cold `Flow<LoginState>`:

1. `POST /client/auth/telegram-login-token` → `{ token }` (`AuthManager.kt:56`).
2. Builds `https://t.me/${BackendConfig.botUsername}?start=auth_${token}` (`AuthManager.kt:66`),
   bot username `departamentvpnbot` (`build.gradle.kts:45`).
3. Emits `AwaitingTelegram` then immediately `Polling` (`AuthManager.kt:67-68`) — `LoginActivity`
   opens the link on *either* state because `StateFlow` conflation can swallow the first
   (`LoginActivity.kt:188-198`).
4. Polls `GET /client/auth/telegram-login-check?token=…` every **2 s** for **3 min**
   (`AuthManager.kt:70-74`). `404 → NotYet`, `410 → Expired`, `200 + confirmed → Confirmed`
   (`DepartamentApiClientImpl.kt:122-135`).
5. On confirmation: `AccountSession.onAuthenticated(token, client)` then `Success`
   (`AuthManager.kt:83-87`).

Also serves a second mode — **Telegram linking** for an already-signed-in account
(`LoginActivity.kt:63`, `EXTRA_LINK`): the token-creation request carries the existing JWT via the
interceptor so the backend attaches Telegram to the current account instead of logging in a new one.
`MainActivity.openTelegramLink()` (`MainActivity.kt:1114-1119`) launches it with
`MODE_TELEGRAM` + `EXTRA_LINK`, and `LoginActivity` auto-starts the flow with no extra tap
(`LoginActivity.kt:159-161`).

### 2. Email + password — works

`AuthManager.loginSite()` (`AuthManager.kt:103-110`) → `POST /client/auth/login` with
`{email, password}` (`DepartamentApiClientImpl.kt:141-151`). The response is disambiguated in
the impl: `requires2FA + tempToken` → `LoginResult.Requires2FA`, else `token + client` →
`LoginResult.Success`, else `ApiError.Parse` (`DepartamentApiClientImpl.kt:146-150`).

### 3. TOTP 2FA — works

`AuthManager.submit2fa(tempToken, code)` (`AuthManager.kt:113-118`) → `POST /client/auth/2fa-login`
(`DepartamentApiClientImpl.kt:153-154`). The UI reveals `layout_2fa` when `AuthViewModel.twoFactor`
turns non-null (`AuthViewModel.kt:55-58`, `LoginActivity.kt:221-229`). 6-digit validation at
`LoginActivity.kt:275-276`.

### 4. Google — implemented in the data layer, **never called**

`DepartamentApiClient.loginGoogle(idToken, referralCode)` (`DepartamentApiClient.kt:39`),
implemented at `DepartamentApiClientImpl.kt:156-157` against `/client/auth/google`
(`BackendConfig.kt:55`), with `GoogleLoginRequestDto` (`AuthDtos.kt:28-31`).

A repo-wide grep for `loginGoogle` / `googleLogin` / `GoogleLoginRequestDto` finds hits **only** in
those four files — no ViewModel, no Activity, no button. There is also no
`play-services-auth` / `androidx.credentials` dependency in
`/home/user/dp/V2rayNG/app/build.gradle.kts` and no `VITE_GOOGLE_CLIENT_ID`-equivalent
BuildConfig field (only `BACKEND_BASE_URL`, `BOT_USERNAME`, `SUB_USER_AGENT` at
`build.gradle.kts:44-46`). **Google sign-in cannot work on Android today**, even though the
website supports it.

### 5. Magic link — does not exist

Grep for `magic|magiclink|passwordless` across `com/v2ray/ang/**` returns nothing. The backend's
magic-link/verify-email/password-reset emails (per the bot repo's CLAUDE.md) have no Android surface.

### 6. Registration — a browser hand-off, and the label lies

`btn_register_site` (`activity_login.xml:287-297`) calls `openRegister()`
(`LoginActivity.kt:380-391`), which opens a Custom Tab at
`private const val REGISTER_URL = "https://departament.site"` (`LoginActivity.kt:407`).
The button says **«Регистрация на сайте»** (`strings_auth.xml:22`) but the destination is the
site **home page**, not a registration route. The user lands on marketing copy and has to find
sign-up themselves.

### 7. No password recovery

`strings_auth.xml` has no "forgot password" string; `activity_login.xml` has no such control.
A user who forgets their password has no in-app path at all.

### 8. No logout anywhere in the app

`AccountViewModel.logout()` (`AccountViewModel.kt:400-417`) does the full correct teardown
(`AccountSession.wipe()`, `AccountCache.invalidateAll()`, cancel jobs, null the flows) — and
**nothing calls it**. Grep for `logout()` across `com/v2ray/ang/**` returns exactly that one
definition line.

Confirming from the UI side: `res/layout/activity_account.xml` has rows
`row_buy` (:385), `row_devices` (:439), `row_history` (:502), `row_referral` (:155),
`btn_top_up` (:112) — and no sign-out row. `MainActivity`'s Settings tab wiring
(`MainActivity.kt:2383-2419`) lists 25 rows, none account-related. **The only way to sign out of
this app is to clear app data.**

### 9. Entry points into `LoginActivity` — and the dead ends

Only three call sites exist (`MainActivity.kt`):

- `:995` `btnHomeLoginTg` → `openLoginScreen("telegram")`
- `:996` `btnHomeLoginSite` → `openLoginScreen("site")`
- `:1116-1117` `openTelegramLink()` → `MODE_TELEGRAM` + `EXTRA_LINK`

Two consequences:

- **The dual-card layout is never actually shown.** `LoginActivity` supports three modes
  (`LoginActivity.kt:79-94`); the "both cards" branch (`:90-93`) is only hit when `EXTRA_MODE`
  is absent, which no caller does. So the screen the owner is looking at is always a *single*
  card floating alone at the top of an otherwise empty scroll view — and once you're on it,
  **there is no way to switch to the other method**. Choose "site", discover you'd rather use
  Telegram, and you must press Back and re-navigate.
- **Signed-out users with any server at all cannot reach login.** Both buttons live inside
  `group_home_login` (`layout_home_empty.xml:91-135`), inside the home empty-state card, whose
  visibility is `binding.layoutHomeEmpty.homeEmptyRoot.isVisible = empty` where
  `empty = mainViewModel.serversCache.isEmpty()` (`MainActivity.kt:687-688`). Paste any
  subscription or scan any QR and the card — and with it every sign-in button — vanishes. The only
  remaining affordance is the `ctaLinkTelegram` banner, gated on
  `SubscriptionOrigin.hasDepartamentSubscription()` (`MainActivity.kt:1094-1096`), i.e. the stored
  sub URL must have `departament` in the host and `/sub` in the path
  (`util/SubscriptionOrigin.kt:23-37`). A signed-out user holding a *foreign* subscription is
  permanently locked out of sign-in.

---

## (b) Token storage, refresh, 401 handling, security gaps

### Storage

`AuthTokenStore` (`AuthTokenStore.kt`) is an MMKV instance `departament_auth` (`:23`) holding
`token` / `expires_at` / `user_json` / `device_id` / `managed_guids_json` (`:25-29`).

Encryption: `openStore()` (`:36-51`) asks `KeystoreKeyProvider.getOrCreateCryptKey()` for a 16-char
secret and opens `MMKV.mmkvWithID(ID, SINGLE_PROCESS_MODE, cryptKey)`. The secret is generated once
(8 random bytes → 16 hex chars, `KeystoreKeyProvider.kt:86-90`), sealed with a non-exportable
AES-256-GCM AndroidKeyStore key aliased `departament_auth_aes`
(`KeystoreKeyProvider.kt:55-69`), and the IV+ciphertext parked in a plaintext MMKV
`departament_keyholder` (`:44-48`). This part is sound: the sealed blob is useless without the
Keystore key.

Transport: a single OkHttp interceptor attaches `Accept`, `User-Agent`, `Authorization: Bearer …`
and (when enabled) the HWID headers (`DepartamentApiClientImpl.kt:71-87`).

### Refresh — there is none, by design

`AuthManager`'s KDoc states it outright (`AuthManager.kt:13-14`): "the JWT is 7-day and
non-refreshable". `BackendConfig.kt:11` repeats it. There is no refresh endpoint in
`BackendConfig.Endpoints` (`:44-86`).

### 401 handling

Deliberately narrow and well-reasoned:

- `mapError` treats **only 401** as `Unauthorized`; 403 becomes `Server(403)`
  (`DepartamentApiClientImpl.kt:338-349`) — explicitly so a permission failure can't wipe a live
  session.
- `AccountRepository.guard()` never wipes (`AccountRepository.kt:41-53`).
- `AccountRepository.refreshProfile()` (`GET /client/auth/me`) is the **only** wipe trigger
  (`AccountRepository.kt:66-82`): `catch (e: ApiError.Unauthorized) { AccountSession.wipe() }`.
- `CancellationException` is rethrown, not converted (`AccountRepository.kt:47-49`, `:76-78`).

### Security / robustness gaps

**G1 — `expires_at` is written but never populated, and `isLoggedIn()` ignores it.**
`saveSession(token, expiresAt, user)` (`AuthTokenStore.kt:96-100`) removes the key when
`expiresAt == null`, and the only caller —
`AccountSession.onAuthenticated` → `AuthTokenStore.saveSession(jwt, user = profile)`
(`AccountSession.kt:38`) — never passes it. So `getExpiresAt()` (`:109`) always returns `0`, and
`isLoggedIn()` is just `!getToken().isNullOrBlank()` (`:122`). Nothing decodes the JWT `exp`
claim. **The app cannot tell a dead token from a live one until a request fails.**

**G2 — A dead JWT silently deletes the user's servers.**
`AccountSession.wipe()` calls `subs.removeAllManaged()` (`AccountSession.kt:56`), which loops the
managed guid map and calls `SubscriptionUpdater.cancelOne` + `MmkvManager.removeSubscription`
(`SubscriptionSyncManager.kt:79-87`). Combined with G1 and with the fact that `refreshProfile()`
is only ever called from `AccountFragment.loadAll()` (`AccountFragment.kt:189`), the post-checkout
poller (`:655`), and `BuyTariffActivity` (`:131`, `:587`) — never from `MainActivity` — the failure
mode is: token expires → user never opens the Account tab → nothing happens → user finally opens
the Account tab → **every server disappears from Home with no explanation and no re-login prompt**.
There is no "session expired, sign in again" state anywhere in the codebase.

**G3 — Keystore loss silently downgrades to a plaintext store *and* orphans the existing data.**
`openStore()`'s fallback chain (`AuthTokenStore.kt:44-50`) catches everything and reopens the same
MMKV id **without** the crypt key. If the Keystore entry is ever invalidated (device restore,
factory-reset-protection edge cases, OEM Keystore bugs), `unseal` throws →
`getOrCreateCryptKey()` returns null (`KeystoreKeyProvider.kt:50-52`) → MMKV is opened unencrypted
against a file that *is* encrypted. Best case the session is silently lost; worst case the store is
recreated in plaintext and the next token is written **unencrypted** with no signal to the user or
the log. This is a silent security downgrade, not just a UX bug.

**G4 — `SINGLE_PROCESS_MODE` on a store touched from a second process.**
`AuthTokenStore` opens with `MMKV.SINGLE_PROCESS_MODE` (`:40`), but
`AngConfigManager.kt:799` calls `AuthTokenStore.deviceId()` during subscription updates, and the
manifest declares a separate `:RunSoLibV2RayDaemon` process for the core services
(`AndroidManifest.xml:147,153,159,234,252,262,271,297,318`) plus `:bg` (`:341`). If any
subscription-update path ever executes in a non-main process, single-process MMKV gives no
cross-process locking on the file that holds the JWT. Needs verification of which process the
subscription worker runs in; the mode choice is unsafe as written.

**G5 — Backup is on; the auth store is not excluded.**
`android:allowBackup="true"` (`AndroidManifest.xml:45`) with no `fullBackupContent` /
`dataExtractionRules` attributes anywhere in the manifest. The `departament_auth` MMKV file is
therefore in scope for cloud/adb backup. The ciphertext is Keystore-sealed so it won't restore
usefully on another device — but the *plaintext fallback* case (G3) would back up a bare JWT.

**G6 — User CA trust anchors + global cleartext.**
`res/xml/network_security_config.xml` sets `cleartextTrafficPermitted="true"` in `base-config` and
trusts `src="user"` certificates; `android:usesCleartextTraffic="true"`
(`AndroidManifest.xml:52`). There is no `domain-config` scoping `web.departament.site` and no
certificate pinning. A user-installed proxy CA (or malware that can install one) reads the bearer
JWT, the login password, and every subscription URL in the clear. For a VPN client the permissive
base config is defensible for arbitrary user subscriptions; the **backend domain should be pinned
in its own `domain-config`** regardless.

**G7 — Raw backend error bodies are shown to end users in release builds.**
`LoginActivity` correctly gates its diagnostic dialog behind `BuildConfig.DEBUG`
(`LoginActivity.kt:323`). The payment screens do not:
`AccountFragment.showPaymentErrorDialog` (`AccountFragment.kt:476-503`) and
`BuyTariffActivity.showPaymentErrorDialog` (`BuyTariffActivity.kt:520-547`) render
`ApiError.detail` — a 300-char slice of the backend response body — into a `MaterialAlertDialog`
unconditionally. `DeviceManagementActivity.showDiagnostic` (`DeviceManagementActivity.kt:164-173`)
does the same with the raw HTTP body. `sanitizeBody` (`DepartamentApiClientImpl.kt:356-368`) drops
only lines containing `token` / `authorization` / an http(s) URL — a JSON body with the token on the
same line as other fields, or any other internal field, passes straight through. Users see raw
server internals; this is both an info leak and (see part d) visible slop.

**G8 — Subscription URLs can reach logcat.**
`AuthTokenStore`'s KDoc promises "Tokens and subscription URLs are never logged"
(`AuthTokenStore.kt:19`), but `AngConfigManager.kt:789` is `LogUtil.i(AppConfig.TAG, url)` where
`url` is the subscription URL — which for a departament sub embeds the user's Remnawave token.
Mitigating: `LogUtil`'s default min level is `warning` (`util/LogUtil.kt:10`, `:17-26`,
`:51-53`), so `INFO` is dropped unless the user raises the log level in settings — and the app ships
`LogcatActivity` which displays that output. Conditional, but the invariant in the KDoc is false.

**G9 — HWID + `Settings.Secure.ANDROID_ID` derivation is sent on unauthenticated calls too.**
`deviceId()` = `MD5(ANDROID_ID)` as 32 hex chars (`AuthTokenStore.kt:74-93`), defaulted **on**
(`SettingsManager.isSendHwid()` → default `true`, `SettingsManager.kt:379-381`). The interceptor
attaches `X-HWID`, `x-device-os`, `x-ver-os`, `x-device-model`
(`DepartamentApiClientImpl.kt:78-85`, header names at `AppConfig.kt:236,244-246`) to **every**
request through this client, including `/public/config`, `/public/tariffs` and
`/public/server-status` where no session exists. That is a stable device fingerprint attached to
anonymous traffic.

**G10 — A fresh `OkHttpClient` per API-client instance.**
`DepartamentApiClientImpl(http: OkHttpClient = defaultClient())` (`:61-66`, `:89-93`) builds a new
client — new connection pool, new dispatcher thread pool — for every instance. Instances are
created by `AuthManager` (`AuthManager.kt:17`), by `AccountRepository` (`AccountRepository.kt:30`),
by `SubscriptionSyncManager`'s sibling default (`AccountRepository.kt:31`), by every
`AccountViewModel` (`AccountViewModel.kt:31`) — one per Activity — and directly by
`DeviceManagementActivity` (`DeviceManagementActivity.kt:38`). Should be one shared singleton.

**G11 — Telegram polling has no backoff and dies on a 429.**
Fixed 2 s × 90 attempts (`AuthManager.kt:70-74`). A `429` maps to `ApiError.RateLimited`
(`DepartamentApiClientImpl.kt:346`) which is thrown out of `checkTelegramLogin`, caught at
`AuthManager.kt:78`, and terminates the whole login flow with an error. Self-inflicted rate limiting
is a plausible failure here.

**G12 — The Telegram flow does not survive process death.**
The poll lives in `AuthViewModel.loginJob` on `viewModelScope` (`AuthViewModel.kt:39-43`). The user
leaves the app for Telegram; if Android kills the process, they confirm in Telegram, return, and the
app is on a fresh `LoginActivity` with nothing polling. `onSaveInstanceState` persists only
`currentDeepLink` and `lastAttemptWasSite` (`LoginActivity.kt:164-170`) — not the login token — so
there is nothing to resume with. The user just sees the button again with no explanation.

**G13 — The Telegram deep link is not package-targeted.**
`openTelegramOnce` does `startActivity(Intent(ACTION_VIEW, Uri.parse(deepLink)))`
(`LoginActivity.kt:369`) on an `https://t.me/...` URL. Any browser claims that scheme, so on a
device without Telegram the `ActivityNotFoundException` branch (`:370-372`) never fires and the
`auth_telegram_not_installed` string (`strings_auth.xml:14`) is effectively dead — the user gets a
t.me web page instead of a clear "install Telegram" message. There is also no
`setPackage("org.telegram.messenger")` attempt or app-chooser control.

**G14 — `AccountCache` is a process-global `object` with a lazy logout check.**
`AccountCache.get()` clears everything when `!AccountSession.isLoggedIn()` (`AccountCache.kt:47-50`),
which is correct, but writes (`put`, `putDevices`, `putPayments`) do **not** check the session
(`:34-37`, `:82-83`, `:90`). An in-flight response landing just after a wipe repopulates the cache
for the logged-out state until the next read evicts it. Low severity, but it is a real ordering hole.

**G15 — `ApiGson`'s global `String` adapter is a blunt instrument.**
`ApiGson.instance` registers a type adapter for `String::class.java` that maps JSON `null` → `""`
**and JSON booleans → their string form** (`ApiGson.kt:22-38`). It is applied to every DTO on the
wire. This fixed a real Account-tab NPE (documented at `:10-20`), but it means no DTO can ever
distinguish "absent" from "empty" — e.g. `UserProfileDto.email` is `""` for both a Telegram-only
user and a parse failure. Worth knowing before adding fields.

---

## (c) Full API surface used

Base URL `https://web.departament.site/api` (`build.gradle.kts:44`, exposed via
`BackendConfig.baseUrl`, `BackendConfig.kt:21`). All paths from `BackendConfig.Endpoints`
(`BackendConfig.kt:44-86`); all impls in `DepartamentApiClientImpl.kt`.

### Public (no auth required — but the interceptor still attaches the token + HWID if present)

| Method | Path | Impl | Response DTO |
| --- | --- | --- | --- |
| GET | `/public/config` | `:98-99` | `PublicConfigDto` (`PublicDtos.kt:14-21`) — `telegramBotUsername`, `publicAppUrl`, `siteUrl`, `plategaMethods[]`, `trialEnabled`, `defaultReferralPercent` |
| GET | `/public/tariffs` | `:101-102` | `TariffCatalogDto` → `TariffGroupDto` → `TariffDto` + `PriceOptionDto` (`PublicDtos.kt:30-64`) |
| GET | `/public/server-status` | `:104-107` | `List<ServerStatusDto>` (`PublicDtos.kt:67-70`) |

### Auth

| Method | Path | Impl | Notes |
| --- | --- | --- | --- |
| POST | `/client/auth/telegram-login-token` | `:113-114` | body `{}` → `TelegramTokenDto` |
| GET | `/client/auth/telegram-login-check?token=` | `:116-139` | 404/410/200 tri-state, **not** routed through `mapError` for those codes |
| POST | `/client/auth/login` | `:141-151` | `{email,password}` → token or `tempToken`+`requires2FA` |
| POST | `/client/auth/2fa-login` | `:153-154` | `{tempToken,code}` → `AuthResult` |
| POST | `/client/auth/google` | `:156-157` | **never invoked** |
| GET | `/client/auth/me` | `:159-160` | `UserProfileDto`; the sole 401→wipe endpoint |

`UserProfileDto` (`AuthDtos.kt:94-125`) carries `id`, `email`, `balance`,
`currency`/`preferredCurrency`, `telegramLinked`, `telegramId`, `telegramUsername`,
`telegramName` (+6 `@SerializedName` alternates), `referralCode`, `remnawaveUuid`, `trialUsed`,
`autoRenewEnabled`, `totpEnabled`, `avatarUrl` (+7 alternates).

Note: `totpEnabled` is parsed and **never used** in any UI — there is no 2FA management screen.

### Subscriptions

| Method | Path | Impl | Notes |
| --- | --- | --- | --- |
| GET | `/client/subscription` | `:166-167` | `PrimarySubscriptionDto` — authoritative active/root sub |
| GET | `/client/subscription/all` | `:169-170` | `SubscriptionAllDto{items:[SubInfoDto]}` |
| PATCH | `/client/subscription/{scope}/{id}/name` | `:172-177` | `{name}`. **Exposed on the repo/VM (`AccountRepository.kt:98`, `AccountViewModel.kt:396`) but no UI calls it** |
| GET | `/client/subscription/qr?uuid=` | `:179-187` | PNG bytes. `AccountRepository.getQr` (`:101`) — **no caller** |
| POST | `/client/subscription/{scope}/{id}/add-devices` | `:189-192` | `{extraDevices,method,paymentMethod}`. Repo + VM exist (`AccountRepository.kt:120`, `AccountViewModel.kt:353`) — **no UI caller** |
| GET | `/client/subscriptions/upgrade-quote?targetTariffId=` | `:194-198` | `UpgradeQuoteDto`. Repo `:110` — **no UI caller** |
| POST | `/client/subscriptions/upgrade` | `:200-203` | `{targetTariffId,method,paymentMethod,subscriptionUuid}`. Repo `:113`, VM `:341` — **no UI caller** |

`AccountViewModel.mergeSubscriptions/buildRootSub` (`:162-226`) reconstructs the root sub from
`/client/subscription` + the `/all` root entry + the profile, because `/all` returns `items: []`
for primary-only accounts (documented at `AccountViewModel.kt:104-111`).

### Devices

| Method | Path | Impl | Notes |
| --- | --- | --- | --- |
| GET | `/client/devices?uuid=` | `:209-221` | Returns `DevicesResult` incl. raw sanitized body for diagnostics; `DevicesDto.devices()` normalizes 3 wire shapes (`MiscDtos.kt:29-48`) |
| POST | `/client/devices/delete` | `:223-227` | `{hwid, uuid}` |

### Payments

| Method | Path | Impl | Notes |
| --- | --- | --- | --- |
| POST | `/client/payments/platega` | `:233-234` | `PaymentRequestDto` → `PaymentInitDto{paymentUrl,paymentId,orderId}` |
| POST | `/client/payments/balance` | `:236-237` | → `PaymentResultDto{status,orderId}` |
| GET | `/client/payments` | `:239-240` | `PaymentsDto{items:[PaymentDto]}` |

### Promo / trial / referral / auto-renew

| Method | Path | Impl | UI status |
| --- | --- | --- | --- |
| POST | `/client/promo-code/check` | `:246-247` | repo `:136`, VM `:366` — **no UI caller** |
| POST | `/client/promo-code/activate` | `:249-253` | repo `:137` — **no UI caller** |
| POST | `/client/trial` | `:255-259` | repo `:138`, VM `:392` — **no UI caller** |
| PATCH | `/client/secondary-subscriptions/{id}/auto-renew` | `:261-265` | repo `:139`, VM `:370` — **no UI caller** |
| PATCH | `/client/subscription/auto-renew` | `:267-271` | repo `:143`, VM `:382` — **no UI caller** |
| GET | `/client/referral-stats` | `:273-274` | repo `:145` — **no UI caller** |

**Roughly half the API surface is plumbed end-to-end through the repository and ViewModel and then
never wired to a control.** Upgrade, add-devices, rename, QR, promo codes, trial activation,
auto-renew toggles and referral stats are all reachable in code and invisible in the app.

### Headers sent

`Accept: application/json`, `User-Agent`, `Authorization: Bearer …`, and when
`SettingsManager.isSendHwid()`: `X-HWID`, `x-device-os: android`, `x-ver-os`, `x-device-model`
(`DepartamentApiClientImpl.kt:72-85`).

**Missing: `X-Client-Origin`.** The web SPA sends `X-Client-Origin: site` so the backend's
`clientReturnBase()` builds payment/auth return links against `siteUrl` rather than
`publicAppUrl` (documented in `/home/user/departament-site-v2/CLAUDE.md` and the bot repo's
CLAUDE.md). The Android client sends **no** origin header, so every checkout it opens
(`AccountFragment.openCheckout` `:618-638`, `BuyTariffActivity.openCheckout` `:550-570`) will be
returned to `web.departament.site` — the Telegram Mini App — instead of anywhere sensible for a
native app. Worth confirming against the backend, but from the header list it is unambiguous that
Android never identifies its origin.

**User-Agent is `v2rayNG/2.2.1`.** `BackendConfig.subscriptionUserAgent` (`:35-38`) rejects the
configured `SUB_USER_AGENT` value because `build.gradle.kts:46` ships exactly the branding string
`"DepartamentVPN/1.0"` that `:37` filters out, falling back to
`HttpUtil.DEFAULT_SUBSCRIPTION_USER_AGENT` = `"v2rayNG/${BuildConfig.VERSION_NAME}"`
(`util/HttpUtil.kt:34`). That's intentional for subscription-format negotiation, but it also means
**every API call to the account backend announces itself as v2rayNG**, which is both a branding
leak and a fingerprint.

---

## (d) Design critique of the login screen

Files: `res/layout/activity_login.xml` (314 lines), `ui/LoginActivity.kt`,
`res/values/strings_auth.xml`. Measured against the design law in `/home/user/dp/CLAUDE.md`
("Incy" = pure dark + ONE bright blue accent, Space Grotesk, Russian sentence-case, one spacing
scale, one gutter, one accent, consistent radii, every state designed).

The owner is right. Here is specifically why, ordered by how much it costs.

### D1 — There is no screen here. There are two settings cards on an empty page.

The entire layout is a `NestedScrollView` → `LinearLayout` → two `MaterialCardView`s
(`activity_login.xml:2-18`, cards at `:21` and `:112`) — both with the identical treatment:
`cardBackgroundColor=?attr/colorSurface`, `cardCornerRadius=@dimen/radius_card`,
`cardElevation=0dp`, `strokeColor=?attr/colorOutlineVariant`, `strokeWidth=1dp`
(`:25-29` and `:117-121`). Same container, same padding (`space_16` at `:35` and `:127`), same
`TextAppearance.App.Title` headline + `App.Subtitle` description pair (`:37-52`, `:129-144`).

There is no brand mark, no logo, no illustration, no hero, no headline in the content area. The only
identity on screen is the toolbar title — one word, «Вход» (`strings_auth.xml:6`, set at
`LoginActivity.kt:64-65`) — rendered by the generic `activity_base.xml` toolbar. A sign-in screen is
the highest-intent moment in a paid product; this one reads like a preferences sub-page.

### D2 — Two identical primary buttons compete for the same tap.

`btn_telegram` (`:54-67`) and `btn_site` (`:193-202`) are both `match_parent`, both `52dp`, both
`backgroundTint=?attr/colorPrimary`, both `textStyle="bold"`, both `cornerRadius="26dp"`. Two filled
blue full-width buttons of identical weight, stacked, with the *outlined*
`btn_register_site` (`:287-297`) below them and the *outlined* `btn_restart` (`:96-106`) hidden
inside the first card. Nothing signals which method the product actually wants you to use
(Telegram — that's the one that auto-links the account to the bot). Two co-equal primaries is
textbook hierarchy failure.

It gets worse in practice: because of the mode-locking described in (a)§9, the user *only ever sees
one of them*, so all the visual effort spent on presenting a choice is wasted, and the choice they'd
actually want to make is unavailable.

### D3 — Token violations everywhere the eye lands.

`/home/user/dp/CLAUDE.md` mandates one spacing scale, consistent radii
(`radius_chip 12` / `radius_card 20` / `radius_tile 12` / `radius_pill 100`), consistent heights.
`activity_login.xml` uses:

- `android:layout_height="52dp"` — a raw magic number, four times (`:57`, `:196`, `:267`, and
  `layout_home_empty.xml:111`). No `dimen` exists for it. `@dimen/row_min_height` is 56dp
  (`dimens.xml:33`).
- `app:cornerRadius="26dp"` — a raw magic number, four times (`:64`, `:105`, `:202`, `:273`, plus
  `:296`). `@dimen/radius_pill` (100dp, `dimens.xml:26`) exists precisely for this and is ignored.
- `ProgressBar` with `style="?android:attr/progressBarStyle"` (`:81`) — the **legacy platform
  spinner**, not `CircularProgressIndicator`, on a Material3 screen. Same for
  `progressBarStyleSmall` at `:206` and `:277`.
- `View` 1dp divider hand-rolled at `:225-228` instead of any shared divider.

None of the buttons carry `android:stateListAnimator="@anim/press_scale"` — which *every* button in
`layout_home_empty.xml` does (`:44`, `:53`, `:67`, `:78`, `:108`, `:125`). So the login screen is
the one screen in the app where buttons don't respond to being pressed. The design law's "pressed =
subtle scale" is unimplemented here specifically.

### D4 — The two sign-in surfaces disagree with each other.

The same product offers Telegram sign-in twice, styled differently:

| | `activity_login.xml` | `layout_home_empty.xml` |
| --- | --- | --- |
| Container | filled `colorPrimary` (`:63`) | tonal `colorPrimaryContainer` (`:117`) |
| Telegram glyph tint | `?attr/colorOnPrimary` — logo flattened to a solid fill (`:67`) | `@null` — brand colours preserved (`:121`) |
| Press feedback | none | `@anim/press_scale` (`:108`) |
| Label | «Войти через Telegram» (`strings_auth.xml:11`) | «Войти через Telegram» (`strings.xml:557`, a **duplicate string**) |

Tapping the tonal button on Home teleports you to a filled button that means the same thing. That's
a continuity break at the exact moment the user is deciding whether to trust the app.

### D5 — The error state is the worst-designed part of the screen.

`tv_error` is the **last child of the LinearLayout**, below both cards
(`activity_login.xml:301-311`): centred red text, no icon, no container, no background,
`TextAppearance.App.Subtitle` (13sp, `styles.xml:95-99`).

Failure sequence today: user types email + password inside the *second* card, keyboard up,
taps «Войти через сайт», the request fails → `showError()` (`LoginActivity.kt:317-324`) sets that
`TextView` visible **below the card, off-screen**, and nothing scrolls to it. `LoginActivity` has no
`windowSoftInputMode` in the manifest (`AndroidManifest.xml:124-125` declares only `name` and
`exported`; the four activities that do declare it are at `:75,79,83,87`), so the failure is
plausibly entirely invisible. From the user's side, the login button just does nothing.

The friendly strings themselves are fine and correctly sentence-cased
(`strings_auth.xml:35-40`) — the problem is purely that they're placed where nobody will see them.

Meanwhile the *diagnostic* dialog that would explain the real reason is `BuildConfig.DEBUG`-only
(`LoginActivity.kt:323`) while the payment screens show raw HTTP bodies to everyone (G7). The
error-surfacing policy is inverted across the app.

### D6 — The waiting state is a spinner wedged inside a card.

`layout_awaiting` (`:69-107`) sits **inside** the Telegram card, so revealing it grows the card and
shoves the site card down the page. It is: legacy spinner, one line of grey 13sp text
(«Ожидаем подтверждения в Telegram…», `strings_auth.xml:12`), an outlined «Начать заново» button.

No countdown, despite the flow having a hard **3-minute deadline** (`AuthManager.kt:71`). No
"didn't open? tap here" re-open affordance (the deep link is deliberately not re-fired —
`openTelegramOnce` guards on `currentDeepLink == deepLink`, `LoginActivity.kt:366`). No explanation
of what the user is supposed to be doing in Telegram. When the 3 minutes elapse the user gets
`ApiError.Timeout` → the generic «Что-то пошло не так, попробуйте снова»
(`LoginActivity.kt:360` maps `Timeout` to `auth_err_network`, i.e.
«Ошибка сети. Проверьте подключение») — which is a **wrong** message: nothing was wrong with the
network, they just didn't confirm in time.

### D7 — Copy and behaviour drift apart.

- «Регистрация на сайте» → opens `https://departament.site` root, not a registration page
  (`LoginActivity.kt:407`).
- In Telegram **link** mode, success toasts «Вход выполнен» (`auth_success`,
  `strings_auth.xml:34`, fired at `LoginActivity.kt:206`) — the user was linking an account, not
  signing in.
- «Вход через сайт» as a *button label* (`strings_auth.xml:21`) restates the card headline
  «Вход через сайт» (`:17`) verbatim, 4 lines below it. Redundant, and not an active verb for
  the action.
- «Подключите аккаунт Telegram, чтобы войти в один тап.» (`:10`) is the only sentence in the whole
  screen that carries a trailing period; `:18` also does. The rest of the file deliberately doesn't
  (per the comment at `:33`). Inconsistent within one file.

### D8 — Missing states and inputs.

- **No password recovery.** Nothing in the layout, nothing in the strings.
- **No "or" separator, no method switcher.** Once mode-locked you're stuck (see (a)§9).
- **`et_code` has no `autofillHints`** (`:249-256`) — Android can autofill SMS/TOTP codes with
  `autofillHints="smsOTPCode"`; the email and password fields do have hints (`:160`, `:182`),
  so the omission is inconsistent as well as a missed convenience.
- **No 6-box OTP treatment** — a 6-digit TOTP in a full-width `OutlinedBox` text field with a
  floating «6-значный код» hint is generic-form styling for what should be a distinct moment.
- **No offline/`NotConfigured` empty state** — `ApiError.NotConfigured` maps to
  «Вход недоступен» (`strings_auth.xml:39`) shown in that same invisible bottom `TextView`, with
  both fully-functional-looking cards still on screen.
- **No terms/privacy line.** The product has a terms-acceptance gate on the bot side; the Android
  sign-in screen never mentions terms.

### D9 — What a redesign has to deliver

Concretely, for the rebuild:

1. **One screen, one story.** Brand mark → one headline in `TextAppearance.App.Headline`
   (`styles.xml:65-71`, 24sp Space Grotesk 700) → one sentence of value copy → **one** primary
   action (Telegram, filled blue, brand-tinted glyph, `press_scale`) → one secondary text link
   («Войти по email») that swaps the panel **in place** rather than living in a second card.
2. **Kill both cards.** A sign-in screen shouldn't have chrome around its content; the surface *is*
   the page. This also removes the D4 mismatch — the Home tonal button becomes the only card-shaped
   Telegram affordance in the app.
3. **Every raw number → a token.** 52dp → a new `@dimen/button_height_cta` (or `row_min_height`),
   26dp → `@dimen/radius_pill`, legacy `ProgressBar` → `CircularProgressIndicator`,
   `press_scale` on every button.
4. **Errors inline, at the field or directly above the button that failed** — with an icon and a
   surface, never a bare centred `TextView` at the bottom of a scroll view. Add
   `windowSoftInputMode="adjustResize"` and scroll-to-error.
5. **Design the waiting state as a real state**, full-bleed: what to do in Telegram, a live
   countdown against the 3-minute deadline, «Открыть Telegram снова» and «Начать заново».
   Give `Timeout` its own copy («Не дождались подтверждения»), not the network string.
6. **Add the missing routes**: forgot-password, a truthful registration destination, and — the big
   one — a **sign-out** control on the Account tab, plus a "session expired" re-login prompt so G1/G2
   stop silently deleting people's servers.
7. **Decide about Google.** Either wire `loginGoogle` to Credential Manager, or delete the dead API
   surface. Shipping a website that offers Google and an app that doesn't is a support burden.

---

## Appendix — quick-reference defects list

| # | Severity | Where | Defect |
| --- | --- | --- | --- |
| A1 | high | `AccountViewModel.kt:400`; `activity_account.xml`; `MainActivity.kt:2383-2419` | No logout anywhere; `logout()` has zero callers |
| A2 | high | `AuthTokenStore.kt:96-122`; `AccountSession.kt:38,56`; `SubscriptionSyncManager.kt:79-87` | Expired JWT undetectable, then wipes the user's servers with no re-login prompt |
| A3 | high | `MainActivity.kt:687-688,995-996`; `layout_home_empty.xml:91-135` | Sign-in buttons disappear as soon as any server exists |
| A4 | high | `LoginActivity.kt:79-94` vs `MainActivity.kt:1103-1106` | Mode-locked login; no way to switch method; the both-cards layout is unreachable |
| A5 | high | `activity_login.xml:301-311`; `AndroidManifest.xml:124-125` | Error text below the fold, no scroll-to, no `windowSoftInputMode` |
| A6 | med | `AuthTokenStore.kt:44-50`; `KeystoreKeyProvider.kt:50-52` | Keystore failure silently downgrades the token store to plaintext |
| A7 | med | `AccountFragment.kt:476-503`; `BuyTariffActivity.kt:520-547`; `DeviceManagementActivity.kt:164-173` | Raw backend bodies shown to release users |
| A8 | med | `DepartamentApiClientImpl.kt:72-85` | No `X-Client-Origin` header → payment returns point at the Mini App |
| A9 | med | `network_security_config.xml`; `AndroidManifest.xml:45,52` | User CAs trusted, cleartext global, no `domain-config` for the backend, `allowBackup=true` |
| A10 | med | `DepartamentApiClient.kt:39`; `DepartamentApiClientImpl.kt:156` | Google login is dead code; no dependency, no UI |
| A11 | med | `AuthManager.kt:70-78` | 2 s polling, no backoff, a 429 kills the login |
| A12 | med | `AuthViewModel.kt:39-43`; `LoginActivity.kt:164-170` | Telegram flow does not survive process death |
| A13 | med | `AuthTokenStore.kt:40`; `AngConfigManager.kt:799`; `AndroidManifest.xml:147+` | `SINGLE_PROCESS_MODE` MMKV touched from a multi-process app |
| A14 | low | `DepartamentApiClientImpl.kt:61-93` | One `OkHttpClient` per API-client instance (≥5 live) |
| A15 | low | `LoginActivity.kt:369` | Telegram deep link not package-targeted; `auth_telegram_not_installed` is dead |
| A16 | low | `LoginActivity.kt:407`, `:206` | «Регистрация» opens the homepage; link mode toasts «Вход выполнен» |
| A17 | low | `AngConfigManager.kt:789` | Subscription URL logged at INFO (off by default, but contradicts the store's KDoc) |
| A18 | low | `DepartamentApiClientImpl.kt:78-85`; `SettingsManager.kt:379-381` | HWID + ANDROID_ID-derived id sent on unauthenticated `/public/*` calls by default |
| A19 | low | `BackendConfig.kt:35-38`; `build.gradle.kts:46`; `HttpUtil.kt:34` | API User-Agent resolves to `v2rayNG/2.2.1` |
| A20 | info | repo/VM layer | ~half the API (upgrade, add-devices, rename, QR, promo, trial, auto-renew, referral stats) is plumbed but unreachable in the UI |
