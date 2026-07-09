# Module 4 — Telegram Auth: Implementation Report

Implements Milestones A, B, C of `telegram-auth-design.md`. Login is **optional**; the app
stays fully usable with no backend. No unit tests (per instructions). No commit made.

## Files added

Package `com.v2ray.ang.auth`:
- `BackendConfig.kt` — `object` reading `BuildConfig.{BACKEND_BASE_URL,BOT_USERNAME,SUB_USER_AGENT}`; exposes `baseUrl`, `botUsername`, `subscriptionUserAgent`, `endpoints`, and `isConfigured()`.
- `dto/AuthDtos.kt` — Gson data classes (AuthStart/Poll/Code req+resp, Refresh, UserProfileDto, SubscriptionInfoDto).
- `ApiError.kt` — sealed class: NotConfigured, Network, Unauthorized, RateLimited, Server(code), Parse, Timeout.
- `DepartamentApiClient.kt` — interface.
- `DepartamentApiClientImpl.kt` — OkHttp + Gson impl; Bearer auth; runs on `Dispatchers.IO`; maps non-2xx/IO to `ApiError`. Throws `ApiError.NotConfigured` when base URL blank, so calls fail gracefully with no backend.
- `AuthTokenStore.kt` — `object` over a dedicated `MMKV.mmkvWithID("departament_auth")`; stores token/refresh/expiry/user, stable `deviceId()`, and the managed-sub guid. Never logs tokens.
- `SubscriptionSyncManager.kt` — bridges backend payload into existing subscription plumbing.
- `AuthManager.kt` — orchestration; `beginTelegramLogin(): Flow<LoginState>` (start→await→poll→import), `submitCode`, `refreshIfNeeded`, `logout`, `isLoggedIn`. Nested sealed interface `LoginState`.

UI:
- `viewmodel/AuthViewModel.kt` — exposes `StateFlow<LoginState>`; `startTelegramLogin()`, `submitCode()`.
- `ui/LoginActivity.kt` — `BaseActivity` + `setContentViewWithToolbar`; intro→awaiting→error states; opens deep link via `Intent(ACTION_VIEW)`, catches `ActivityNotFoundException`; manual code fallback.
- `res/layout/activity_login.xml` — Material/AppCompat style matching `activity_sub_edit.xml`.
- `res/values/strings_auth.xml` — all new user-facing strings (all `auth_*`, no collisions with strings.xml).

## Files changed

- `app/build.gradle.kts` — added 3 `buildConfigField("String", …)` in `defaultConfig` (blank base URL/bot, UA `DepartamentVPN/1.0`).
- `AndroidManifest.xml` — registered `.ui.LoginActivity` (`exported=false`, app theme). No intent-filter (optional, omitted).
- `res/menu/menu_drawer.xml` — added `@+id/telegram_login` item ("Sign in with Telegram", `ic_telegram_24dp`).
- `ui/MainActivity.kt` — handle `R.id.telegram_login` in `onNavigationItemSelected`: launches `LoginActivity` only when `BackendConfig.isConfigured()`, else toasts `auth_not_configured`. Added import `com.v2ray.ang.auth.BackendConfig`. **No startup gating/force-route added.**

## Real existing-API signatures bridged to

- `MmkvManager.decodeSubscription(subscriptionId: String): SubscriptionItem?`
- `MmkvManager.encodeSubscription(guid: String, subItem: SubscriptionItem)` — generates a UUID internally on blank guid but does **not** return it, so `SubscriptionSyncManager` generates its own stable guid via `Utils.getUuid()` on first import and persists it in `AuthTokenStore`.
- `MmkvManager.removeSubscription(subid: String)`
- `AngConfigManager.updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult` (blocking fetch+parse; called on IO)
- `SubscriptionUpdater.syncOne(context, subId)` and `SubscriptionUpdater.cancelOne(context, subId)` — the design's names were correct; called with the default `context` arg.
- `SubscriptionItem` fields used: remarks, url, enabled, autoUpdate, userAgent.
- `SubscriptionCache(guid, subscription)`; `JsonUtil.toJson/fromJsonSafe`; `Utils.getUuid`; toast extensions.

## Public wrapper added

None required — all bridging used already-public `MmkvManager` / `AngConfigManager` / `SubscriptionUpdater` methods, exactly as the design's "preferred, lower blast radius" option.

## Risks / TODO

- **No Keystore encryption on the token store yet.** Uses plain `MMKV.mmkvWithID("departament_auth")` (single-process). TODO noted in `AuthTokenStore` to derive a crypt key from Android Keystore. Acceptable for this scaffold.
- **No backend exists** — every API call currently returns `ApiError.NotConfigured` (blank base URL) or `ApiError.Network`. LoginActivity surfaces these as friendly messages. Wire up by filling the 3 BuildConfig fields.
- **`AuthTokenStore` is single-process MMKV.** The subscription auto-update worker runs in `:bg`, but it never touches the auth store (only reads the SUB store), so no cross-process access to the auth instance. Revisit if a `:bg`-process caller ever needs the token.
- **Human must verify at build time** (no Android SDK here): view-binding `ActivityLoginBinding` field names, BuildConfig field generation, and R.string references resolve. All were built by construction and XML validated with `xmllint`.
- Menu item is always visible; tapping when unconfigured shows "not available" rather than being hidden. Hide the item at runtime if a cleaner UX is wanted.
