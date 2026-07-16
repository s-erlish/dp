# Departament VPN — Telegram Auth + Subscription System (Design)

Status: **Design / not yet implemented**
Scope: client-side (Android, Kotlin, `com.v2ray.ang` fork). The Telegram bot + backend are provided later; this doc defines a **pluggable API layer** with a configurable base URL/bot so wiring the real bot is a config change, not a rewrite.

---

## 0. TL;DR recommendation

- **Auth method:** bot **deep-link + one-time code + short-poll** for the token. The app opens `https://t.me/<bot>?start=<nonce>`; the bot binds the Telegram user to that nonce; the app polls `POST /auth/telegram/poll {nonce}` until it gets an app token. This is the model Happ/Remnawave-style clients use in practice and it needs **no bot token in the app** and no WebView-hosted secrets. Telegram Login Widget is kept as a documented fallback; manual code-paste is the always-works escape hatch.
- **Subscription:** the backend returns the user's **Remnawave-style subscription URL** (`https://panel/api/sub/<shortUuid>`). The app imports it as a normal `SubscriptionItem` and lets the **existing** `SubscriptionUpdater` / `AngConfigManager.updateConfigViaSub` machinery fetch and parse it. No new parsing code.
- **Architecture:** one `DepartamentApiClient` interface (OkHttp + Gson, configurable base URL) + `AuthManager` (token lifecycle, MMKV-backed `AuthTokenStore`) + `SubscriptionSyncManager` (bridges API → existing subscription store) + a `LoginActivity`. Payments are a separately-staged `PaymentManager`/`PaymentGateway` interface, screens sketched only.

---

## 1. How the reference clients actually work (research)

### Happ (the client we are matching)
- Happ is a **client only** — it does not run the servers. A provider runs a **Telegram bot** that issues the user a **subscription key/link**; the user imports that link into Happ and connects. ([happvpn.app/setup](https://happvpn.app/en/setup/), [happ.su/faq/adding-configuration-subscription](https://www.happ.su/main/faq/adding-configuration-subscription))
- Import is done via a **subscription URL** (text list or JSON array of configs, optionally with routing/Reality params) **or** via a `happ://` deep link. Encrypted subscriptions use `happ://crypt4/<base64>` / `crypt5` (RSA-4096); routing profiles use `happ://routing/add/<base64>`. ([happ.su/dev-docs/crypto-link](https://www.happ.su/main/dev-docs/crypto-link), [happ.su/dev-docs/routing](https://www.happ.su/main/dev-docs/routing), [cryptohapp](https://github.com/kastov/cryptohapp))
- **Takeaway:** the auth/subscription intelligence lives in the **bot + panel**. The client's job is: authenticate → obtain a subscription URL → import it → keep it updated. Our fork already has all the import/update plumbing.

### Remnawave (the panel the bot fork will likely sit on)
- Each user has a **subscription short UUID**; the panel exposes a **subscription page/endpoint** (Remnawave subscription-page service, `REMNAWAVE_PANEL_URL` + `REMNAWAVE_API_TOKEN`). Clients fetch `…/api/sub/<shortUuid>` (or the panel's subscription host) and the response format is **negotiated by `User-Agent`** — a Happ UA returns Happ-formatted content, a generic UA returns a base64 config list. ([docs.rw subscription-page](https://docs.rw/install/subscription-page/separate-server/), [remnawave/subscription-page](https://github.com/remnawave/subscription-page), [remnawave-sdk](https://github.com/mishkatik/remnawave-sdk))
- Admin/API access to the panel uses a **bearer API token**. That token belongs to the **bot/backend, never the app.** The app only ever sees (a) its own app-session token and (b) the user's subscription URL.
- **Takeaway:** the backend contract we design should return a **subscription URL + profile metadata + expiry**, matching what a Remnawave-backed bot can produce.

### Telegram auth patterns
- **Login Widget:** JS widget → callback with `{id, first_name, username, photo_url, auth_date, hash}`. Integrity is verified server-side: `secret = SHA256(bot_token)`, then check `hex(HMAC_SHA256(data_check_string, secret)) == hash`, and that `auth_date` is fresh. Requires a **web page** (WebView) and a registered domain. ([core.telegram.org/widgets/login](https://core.telegram.org/widgets/login))
- **Bot deep-link:** `https://t.me/<bot>?start=<param>` (or `tg://resolve?domain=<bot>&start=<param>`). The `start` param (≤512 chars, `A–Za–z0–9_-`, base64url recommended) is delivered to the bot as the user presses Start, letting the bot bind the Telegram identity to a value the app generated. Standard pattern for "connect this app to the user's Telegram account." ([core.telegram.org/api/links](https://core.telegram.org/api/links), [bots/features](https://core.telegram.org/bots/features))

---

## 2. Auth flow — options compared

| Option | UX | App holds bot secret? | Backend work | Robustness | Verdict |
|---|---|---|---|---|---|
| **A. Login Widget in WebView** | Tap → Telegram web login inside app | No, but must host a widget page on our domain + WebView JS bridge | Serve HTML page, verify HMAC | WebView/cookie/JS-bridge fragility; needs registered domain | Fallback |
| **B. Bot deep-link + one-time nonce + poll** ★ | Tap → opens Telegram app → press Start → app auto-logs-in | **No** | One `nonce` table + 2 endpoints | High; degrades to manual code | **Recommended** |
| **C. Manual code paste** | User copies a code from the bot, pastes into app | No | 1 endpoint | Always works, clunky UX | Keep as escape hatch |

### Recommended flow (B, with C as fallback)

```
App                         Telegram                 Backend (bot + API)
 |  1. nonce = random128                                   |
 |  2. POST /auth/telegram/start {nonce, deviceId} ------> | store nonce (pending)
 |  <---------------------------------- {deepLink, expiresIn}
 |  3. open deepLink (t.me/<bot>?start=<nonce>) --> user presses Start
 |                                       bot receives start=<nonce>,
 |                                       binds tgUserId -> nonce, marks "ready"
 |  4. poll POST /auth/telegram/poll {nonce}  ----------->  |
 |     (backoff 2s..; timeout ~120s)                        |
 |  <---------------- 200 {status:"ready", token, refreshToken?, user, subscription}
 |  5. store token (AuthTokenStore, MMKV encrypted)         |
 |  6. import subscription.url -> existing subscription flow|
```

Fallback C: if the deep link can't open (Telegram not installed) or polling times out, show a field: the bot's Start reply includes a short code; user pastes it → `POST /auth/telegram/code {code, deviceId}` returns the same payload.

### Security tradeoffs (call-outs)
- **No bot token in the client.** All HMAC verification of Telegram data happens on the backend. This is the single most important property and rules out any "verify the Login Widget hash on-device" shortcut.
- **Nonce hygiene:** 128-bit CSPRNG, single-use, server-side TTL (~2 min), bound to `deviceId`. Poll endpoint must rate-limit and must not leak whether a nonce exists.
- **Transport:** HTTPS only; pin optional (see §6). The app already routes HTTP via OkHttp (`HttpUtil`) and the environment proxy is TLS-verified — do **not** disable verification.
- **Token at rest:** store in MMKV via a dedicated encrypted instance (`MMKV.mmkvWithID("AUTH", … , cryptKey)`) or wrap with `EncryptedSharedPreferences`/Keystore-derived key. Never log the token.
- **Subscription URL is a secret** (it grants VPN access): treat it like a credential, store in the auth store, and prefer importing it without echoing it in UI/logs. Remnawave supports Happ-style encrypted links if we later want the config bodies hidden from the user too.

---

## 3. Client architecture (concrete Kotlin)

New package: `com.v2ray.ang.auth` (client + managers + DTOs) and `com.v2ray.ang.ui` for screens. Uses libs already present: **OkHttp**, **Gson**, **coroutines**, **MMKV**, **Lifecycle**.

### 3.1 API layer — `DepartamentApiClient`

```kotlin
package com.v2ray.ang.auth

interface DepartamentApiClient {
    suspend fun startTelegramAuth(req: AuthStartRequest): AuthStartResponse
    suspend fun pollTelegramAuth(req: AuthPollRequest): AuthPollResponse
    suspend fun submitAuthCode(req: AuthCodeRequest): AuthPollResponse      // fallback C
    suspend fun getProfile(token: String): UserProfileDto
    suspend fun getSubscription(token: String): SubscriptionInfoDto
    suspend fun refresh(req: RefreshRequest): AuthPollResponse              // if backend issues refresh tokens
    suspend fun logout(token: String)
    // Future (Stage 2) — see §5
    // suspend fun listPlans(token: String): List<PlanDto>
    // suspend fun createInvoice(token: String, req: CreateInvoiceRequest): InvoiceDto
}
```

`DepartamentApiClientImpl(private val config: BackendConfig, private val http: OkHttpClient, private val gson: Gson)`:
- Builds requests against `config.baseUrl` + endpoint paths from `BackendConfig` (§4).
- Adds `Authorization: Bearer <token>` where required and the negotiated `User-Agent` (Happ/Departament) for subscription calls.
- Reuses the app's OkHttp construction style from `util/HttpUtil.kt` (timeouts, optional proxy). Runs on `Dispatchers.IO`.
- Maps non-2xx to a sealed `ApiError` (`Network`, `Unauthorized`, `RateLimited`, `Server`, `Parse`); callers pattern-match, never see raw exceptions.

### 3.2 DTOs (`com.v2ray.ang.auth.dto`) — Gson data classes

```kotlin
data class AuthStartRequest(val nonce: String, val deviceId: String, val platform: String = "android")
data class AuthStartResponse(val deepLink: String, val botUsername: String, val expiresInSec: Int)

data class AuthPollRequest(val nonce: String, val deviceId: String)
data class AuthCodeRequest(val code: String, val deviceId: String)

// status = "pending" | "ready" | "expired"
data class AuthPollResponse(
    val status: String,
    val token: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val user: UserProfileDto? = null,
    val subscription: SubscriptionInfoDto? = null
)

data class RefreshRequest(val refreshToken: String, val deviceId: String)

data class UserProfileDto(
    val id: String,
    val telegramId: Long?,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)

data class SubscriptionInfoDto(
    val subscriptionUrl: String,     // Remnawave-style /api/sub/<shortUuid>
    val remarks: String?,            // group name shown in the app
    val status: String?,            // active | expired | limited
    val expiresAt: Long?,           // epoch millis
    val trafficUsedBytes: Long?,
    val trafficLimitBytes: Long?,
    val userAgent: String? = null    // UA the backend wants us to send when fetching
)
```

### 3.3 Token storage — `AuthTokenStore`

```kotlin
object AuthTokenStore {                       // mirrors MmkvManager style
    private val store by lazy { MMKV.mmkvWithID("AUTH", MMKV.MULTI_PROCESS_MODE, /*cryptKey*/ deviceKey()) }
    fun saveSession(token: String, refresh: String?, expiresAt: Long?, user: UserProfileDto?) { … }
    fun getToken(): String?
    fun getRefreshToken(): String?
    fun getUser(): UserProfileDto?
    fun isLoggedIn(): Boolean = getToken() != null
    fun clear()                               // logout / 401
}
```
- Dedicated MMKV ID `"AUTH"` with a crypt key derived from an Android Keystore secret (`deviceKey()`), so tokens are encrypted at rest and isolated from the config stores in `MmkvManager`.

### 3.4 `AuthManager` (orchestration)

```kotlin
class AuthManager(
    private val api: DepartamentApiClient,
    private val tokens: AuthTokenStore,
    private val subs: SubscriptionSyncManager,
    private val config: BackendConfig
) {
    sealed interface LoginState { object Idle; data class AwaitingTelegram(val deepLink: String)
        object Polling; data class Success(val user: UserProfileDto); data class Error(val e: ApiError) }

    fun beginTelegramLogin(): Flow<LoginState>   // does start → open link → poll → onSuccess importSubscription
    suspend fun submitCode(code: String): LoginState
    suspend fun refreshIfNeeded(): Boolean
    suspend fun logout()                         // api.logout + tokens.clear + optional subs.removeManagedSubscription
    fun isLoggedIn() = tokens.isLoggedIn()
}
```
- On `Success`, calls `subs.importOrUpdate(subscription)` before emitting, so the UI can go straight to the connect screen with servers present.
- A lightweight OkHttp `Interceptor` (or manual check) triggers `logout()` on a `401` to keep local state honest.

### 3.5 Bridge to the existing subscription system — `SubscriptionSyncManager`

This is the **only** place that touches existing code, and it reuses it rather than duplicating:

```kotlin
class SubscriptionSyncManager {
    private val MANAGED_KEY_PREF = "departament_managed"   // stored guid in AUTH store

    suspend fun importOrUpdate(info: SubscriptionInfoDto) {
        val guid = AuthTokenStore.managedSubGuid()          // stable guid we own, or ""
        val item = (guid.takeIf { it.isNotBlank() }?.let { MmkvManager.decodeSubscription(it) }
                    ?: SubscriptionItem()).apply {
            remarks     = info.remarks ?: "Departament VPN"
            url         = info.subscriptionUrl
            enabled     = true
            autoUpdate  = true
            userAgent   = info.userAgent ?: config.subscriptionUserAgent
        }
        MmkvManager.encodeSubscription(guid, item)          // existing API; returns/uses stable guid
        AuthTokenStore.setManagedSubGuid(resolvedGuid)
        AngConfigManager.updateConfigViaSub(SubscriptionCache(resolvedGuid, item))  // existing fetch+parse
        SubscriptionUpdater.syncOne(subId = resolvedGuid)   // existing WorkManager scheduling
    }

    fun removeManagedSubscription() {
        AuthTokenStore.managedSubGuid().takeIf { it.isNotBlank() }?.let {
            SubscriptionUpdater.cancelOne(subId = it)
            MmkvManager.removeSubscription(it)              // existing
        }
    }
}
```

**Why this fits with zero new parsing:** `AngConfigManager.updateConfigViaSub` already does the HTTP fetch (with UA + proxy), base64/plain parsing, and server persistence; `SubscriptionUpdater` already schedules periodic refresh via WorkManager. We only *feed* it a `SubscriptionItem`. 

> Small required change in existing code (one commit): `AngConfigManager.importUrlAsSubscription(...)` is `private`. Either add a thin public `fun addOrGetSubscription(url, remarks, autoUpdate, userAgent): String` in `AngConfigManager`, or (preferred, lower blast radius) keep all bridging inside `SubscriptionSyncManager` using the already-public `MmkvManager.encodeSubscription` + `updateConfigViaSub` + `SubscriptionUpdater.syncOne`, as shown above.

### 3.6 UI — `LoginActivity`

- New `LoginActivity` (XML view, matches existing Activity style e.g. `SubEditActivity`), registered in `AndroidManifest.xml`.
- States: **intro** (logo + "Continue with Telegram") → **awaiting** (spinner + "Open Telegram / Waiting for confirmation…", secondary "Enter code manually" + "Open Telegram again") → **success** (auto-finish to `MainActivity`) → **error** (retry).
- Launches Telegram via `Intent(ACTION_VIEW, Uri.parse(deepLink))`; if `ActivityNotFoundException`, fall back to the HTTPS `t.me` URL / code entry.
- Backed by an `AuthViewModel` (Lifecycle, already a dependency) collecting `AuthManager.beginTelegramLogin()`.
- **Entry gating:** `MainActivity.onCreate` (or a `SplashActivity`) checks `AuthManager.isLoggedIn()`; if not, route to `LoginActivity`. The repo already has a `SubscriptionGuard` — reuse/extend it to also require a valid auth session, keeping one gate.
- **Deep-link return:** add a `departament://auth` intent-filter (mirroring the existing `v2rayng://install-sub` filter in the manifest and `UrlSchemeActivity`) as an optional faster path if the bot can deep-link back; polling remains the primary, install-agnostic mechanism.

### 3.7 Component diagram

```
LoginActivity ── AuthViewModel ── AuthManager ──┬── DepartamentApiClient ──HTTP──> backend/bot
                                                 ├── AuthTokenStore (MMKV "AUTH", encrypted)
                                                 └── SubscriptionSyncManager
                                                          │ reuses existing:
                                                          ├─ MmkvManager.encodeSubscription
                                                          ├─ AngConfigManager.updateConfigViaSub
                                                          └─ SubscriptionUpdater.syncOne
```

---

## 4. Config points (what changes when the bot fork arrives)

Centralize everything in one `BackendConfig` object so wiring the real bot = editing one file (or overriding via `BuildConfig`/remote config). **Do not scatter URLs across activities.**

```kotlin
// com.v2ray.ang.auth.BackendConfig
data class BackendConfig(
    val baseUrl: String,                 // e.g. https://api.departament.example
    val botUsername: String,             // e.g. departament_vpn_bot  (no @)
    val subscriptionUserAgent: String,   // UA to negotiate format, e.g. "Happ/... " or "DepartamentVPN/1.0"
    val endpoints: Endpoints = Endpoints()
) {
    data class Endpoints(
        val authStart: String = "/auth/telegram/start",
        val authPoll:  String = "/auth/telegram/poll",
        val authCode:  String = "/auth/telegram/code",
        val profile:   String = "/user/profile",
        val subscription: String = "/user/subscription",
        val refresh:   String = "/auth/refresh",
        val logout:    String = "/auth/logout",
        // Stage 2 (payments)
        val plans:     String = "/billing/plans",
        val invoice:   String = "/billing/invoice"
    )
    companion object { fun default() = /* read from BuildConfig fields */ }
}
```

Where to put the values:
- **`app/build.gradle.kts`** — `buildConfigField`s (`BACKEND_BASE_URL`, `BOT_USERNAME`, `SUB_USER_AGENT`) per build type/flavor, so debug vs prod bots differ without code edits. `BackendConfig.default()` reads them.
- Optionally a **`departament.properties`** (git-ignored) read at build time for secrets-free overrides.
- Optional runtime override in `SettingsActivity` (dev-only) writing to MMKV, so QA can point at a staging bot.
- The **bot username** also feeds the deep link if the backend doesn't return a full `deepLink` (build `https://t.me/<botUsername>?start=<nonce>` client-side).

---

## 5. Future stage — in-app payments (scaffold only, not built now)

> **Stage 2. Clearly out of scope for the first milestone.** Interfaces are defined so the auth/subscription work doesn't need reshaping later.

Options and reality:
- **Telegram Stars / Telegram Payments in-bot:** the app opens the bot's invoice deep link; the user pays inside Telegram; on success the bot extends the subscription and the app just **re-pulls** `getSubscription()`. Least app code, no PCI, no store-policy risk. **Recommended primary.**
- **External provider (Cryptomus / YooKassa / Stripe link):** app opens a hosted checkout URL (Custom Tab), then re-pulls subscription on return. Backend owns the provider.
- **Google Play Billing:** cleanest UX but Play's policy on VPN/subscription billing + revenue share makes it a deliberate, separate decision — flag, don't assume.

Interface sketch:
```kotlin
interface PaymentGateway {
    suspend fun listPlans(): List<PlanDto>
    suspend fun startPurchase(planId: String): PurchaseIntent   // returns deepLink/checkoutUrl OR native flow token
    suspend fun confirm(purchaseId: String): PurchaseResult     // or rely on getSubscription() refresh
}
class TelegramInvoiceGateway(api, config) : PaymentGateway     // opens bot invoice deep link
class ExternalCheckoutGateway(api, config) : PaymentGateway    // opens Custom Tab
class PaymentManager(private val gateway: PaymentGateway, private val subs: SubscriptionSyncManager)
```
Screens (later): `PlansActivity` (plan cards, price, duration, current expiry) → open pay flow → return → `subs.importOrUpdate(getSubscription())` refreshes servers + expiry. Add `PlanDto`, `PurchaseIntent`, `PurchaseResult`, `InvoiceDto` DTOs and the two `DepartamentApiClient` methods commented in §3.1.

---

## 6. Implementation plan (small commits)

**Milestone A — API layer (no UI)**
1. `feat(auth): BackendConfig + BuildConfig fields (base url, bot username, sub UA)` — config plumbing, placeholder values.
2. `feat(auth): auth DTOs (Gson data classes)` — §3.2.
3. `feat(auth): DepartamentApiClient interface + OkHttp/Gson impl + ApiError` — reuse HttpUtil's client style; unit-testable with MockWebServer.
4. `test(auth): DepartamentApiClient tests against MockWebServer` — start/poll/code/subscription happy + error paths.

**Milestone B — token + bridge**
5. `feat(auth): AuthTokenStore (encrypted MMKV "AUTH") + Keystore-derived key`.
6. `feat(auth): SubscriptionSyncManager bridging to MmkvManager/AngConfigManager/SubscriptionUpdater` (+ the tiny public accessor if chosen).
7. `feat(auth): AuthManager (start→poll→import) as a Flow<LoginState> + refresh/logout`.
8. `test(auth): AuthManager flow tests (fake api + in-memory token store)`.

**Milestone C — UI + gating**
9. `feat(ui): LoginActivity + AuthViewModel + layouts (intro/awaiting/error)`.
10. `feat(ui): deep-link launch to Telegram + manual code fallback field`.
11. `feat(ui): gate MainActivity/SubscriptionGuard on AuthManager.isLoggedIn()`.
12. `feat(manifest): register LoginActivity + optional departament://auth intent-filter`.
13. `feat(ui): logout entry in SettingsActivity (clears session + managed sub)`.

**Milestone D — polish**
14. `feat(auth): 401 interceptor → auto-logout; expiry banner on connect screen`.
15. `feat(auth): dev-only staging backend override in Settings`.
16. `docs: wire-up checklist for the real bot fork` (fill BuildConfig, confirm endpoint paths/response shapes vs this doc).

**Stage 2 (later) — payments**
17. `feat(billing): PaymentGateway/PaymentManager interfaces + plan DTOs`.
18. `feat(billing): TelegramInvoiceGateway (bot invoice deep link) + PlansActivity`.
19. `feat(billing): refresh subscription after purchase`.

**Wire-up checklist when the bot fork lands:** set `BACKEND_BASE_URL`, `BOT_USERNAME`, `SUB_USER_AGENT`; confirm the 4 auth endpoints + `/user/subscription` response match §3.2 (adjust DTO field names only); confirm the subscription URL is a plain/base64 config list or a `happ://crypt` link and set `subscriptionUserAgent` accordingly; verify nonce TTL/rate-limit on the backend.

---

## Sources
- Happ setup & subscription import — https://happvpn.app/en/setup/ , https://www.happ.su/main/faq/adding-configuration-subscription
- Happ crypto/routing deep links — https://www.happ.su/main/dev-docs/crypto-link , https://www.happ.su/main/dev-docs/routing , https://github.com/kastov/cryptohapp
- Remnawave subscription page/endpoints — https://docs.rw/install/subscription-page/separate-server/ , https://github.com/remnawave/subscription-page , https://github.com/mishkatik/remnawave-sdk
- Telegram Login Widget (HMAC verification) — https://core.telegram.org/widgets/login
- Telegram bot deep links / start parameter — https://core.telegram.org/api/links , https://core.telegram.org/bots/features
