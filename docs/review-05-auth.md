# Review 05 — Module 4: Telegram auth scaffold (commit `2e5b59b`)

Scope: only the files added/changed in `2e5b59b` under `V2rayNG/`. No Android SDK available;
findings are from careful reading plus cross-checking every referenced symbol against the
existing codebase. Code was **not** modified.

## Verdict

- **BLOCKER:** none.
- **HIGH:** 1 (latent — manifests only once `BackendConfig.isConfigured()` is true; the app
  ships with a blank `BACKEND_BASE_URL`, so nothing crashes today, but the core deep-link
  login flow is broken when the backend is wired).
- Compilation: everything resolves. `buildConfig = true` is on (build.gradle.kts:135) and the
  three fields are generated (build.gradle.kts:43-45). All bridge signatures
  (`MmkvManager.encode/decode/removeSubscription`, `AngConfigManager.updateConfigViaSub`,
  `SubscriptionUpdater.syncOne/cancelOne`, `Utils.getUuid`, `JsonUtil.toJson/fromJsonSafe`)
  match call sites. MMKV usage is valid (`mmkvWithID(String)` overload; `remove(String)` comes
  from the `SharedPreferences.Editor` interface MMKV implements, same pattern as MmkvManager).
  View-binding field names match `activity_login.xml` ids. Both `when` blocks over
  `ApiError` / `LoginState` are exhaustive. Manifest, menu id, drawables
  (`ic_telegram_24dp` in `drawable/` + `drawable-night/`), dimens, `colorError` (Material3
  theme), and all referenced strings resolve. `xmllint` clean on all three XML files.

## Findings

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| HIGH | `auth/AuthManager.kt:155-156` (emit `AwaitingTelegram` then `Polling`) + `viewmodel/AuthViewModel.kt:32-36` + `ui/LoginActivity.kt:751-757` | `beginTelegramLogin()` emits `AwaitingTelegram(deepLink)` and `Polling` back-to-back with **no suspension point between them**. The ViewModel funnels every emission through a conflated `StateFlow` (`_state.value = state`). Because both assignments run synchronously on the Main dispatcher before the Activity's collector is resumed, `StateFlow` conflation drops the intermediate `AwaitingTelegram` value — the Activity only ever observes `Polling`. `render()` opens Telegram **only** in the `AwaitingTelegram` branch, and that branch is also the only place `currentDeepLink` is set, so the "Open Telegram again" button is dead too. Net effect: once a backend is configured, the deep link is never opened and the user is stuck on the spinner (only the manual-code fallback works). | Carry the deep link on the polling state (e.g. `Polling(deepLink)`), or emit the deep link via a non-conflating channel/`SharedFlow` / one-shot event, or open Telegram from the ViewModel side-effect rather than relying on observing a transient state. Simplest: merge into one state `AwaitingTelegram(deepLink)` and keep showing it during polling instead of emitting a separate `Polling`. |
| MEDIUM | `auth/AuthTokenStore.kt:291` | Session token + refresh token are persisted in **plain MMKV** (unencrypted at rest). Recoverable via `adb backup` / root. Acknowledged in the file's TODO, but worth tracking as a security item before shipping a real backend. | Derive a crypt key from an Android Keystore secret and pass it to `MMKV.mmkvWithID(ID, mode, cryptKey)`. |
| MEDIUM | `auth/SubscriptionSyncManager.kt:596-599` | `importOrUpdate` calls `AngConfigManager.updateConfigViaSub(...)` (immediate network fetch + parse) **and then** `SubscriptionUpdater.syncOne(subId=guid)`, which schedules a WorkManager job that fetches the same subscription again shortly after. Redundant double fetch of the subscription URL on every login. | Either rely on `syncOne` alone for the initial fetch, or schedule periodic-only work after the synchronous import. |
| MEDIUM | `auth/AuthManager.kt:199-213`, `auth/AuthTokenStore.kt:313` | `refreshIfNeeded()` / `getExpiresAt()` are defined but never called anywhere; there is no wiring that refreshes an expired token or reacts to `ApiError.Unauthorized` during subscription fetch (which would leave a stale managed sub and a dead session). Acceptable for a scaffold, but the "refresh" surface is currently inert. | Call `refreshIfNeeded()` before authenticated calls / on 401, and clear + re-prompt on failure. |
| LOW | `auth/AuthManager.kt:239-243` | `resolveDeepLink` falls back to `https://t.me/$bot?start=$nonce`; if both `start.botUsername` and `BackendConfig.botUsername` are blank it produces a malformed `https://t.me/?start=<nonce>`. Only reachable under misconfiguration (backend configured but `BOT_USERNAME` blank). | Guard for blank bot username and surface an error instead of building a bad URL. |
| LOW | `res/layout/activity_login.xml:1003` | `android:autofillHints=""` (empty string) on `et_code` triggers a lint warning; use `"off"` semantics via `android:importantForAutofill="no"` or a real hint. | Remove the empty attr or set `importantForAutofill="no"`. |
| LOW | `res/values/strings_auth.xml:9` + `res/layout/activity_login.xml` | `auth_awaiting_title` is unused; several views (`img_logo`, `tv_headline`, `tv_desc`, `pb_awaiting`, `tv_awaiting`, `tv_code_label`) have ids but are never referenced from code. Harmless. | Optional cleanup. |
| LOW | `ui/LoginActivity.kt:759` (`Polling` → `showAwaiting()`) | The manual-code path sets `Polling`, which renders the "awaiting / Open Telegram again" layout even though no deep link exists in that flow, so the visible "Open Telegram again" button does nothing. Minor UX inconsistency. | Distinguish code-submit progress from deep-link awaiting, or hide the re-open button when `currentDeepLink == null`. |

## Notes / things explicitly verified as correct

- `BuildConfig.BACKEND_BASE_URL/BOT_USERNAME/SUB_USER_AGENT` are generated: `buildConfig = true`
  (build.gradle.kts:135) and `buildConfigField(...)` at lines 43-45. `BackendConfig` reads them correctly.
- `AuthTokenStore` MMKV calls (`mmkvWithID(ID)`, `encode`, `decodeString`, `decodeLong(key, default)`,
  `remove`) all match the patterns used in `MmkvManager`. `managedSubGuid()` / `setManagedSubGuid()`
  are consistent (empty string == none), and `clear()` drops the managed-sub reference.
- `SubscriptionSyncManager` bridge signatures all line up: `SubscriptionCache(guid, item)` matches the
  2-arg data class; `SubscriptionItem` has `remarks/url/enabled/autoUpdate/userAgent`;
  `updateConfigViaSub` is a plain (non-suspend) fn called inside `withContext(Dispatchers.IO)` — no
  NetworkOnMainThread risk.
- `DepartamentApiClientImpl`: OkHttp `Request.Builder`, `toRequestBody(JSON)`, `toMediaType()`,
  `.execute().use { }` on `Dispatchers.IO`, Gson `fromJson`/`JsonSyntaxException` handling, and the
  status-code → `ApiError` mapping are all correct. `buildRequest` throws `ApiError.NotConfigured`
  when the base URL is blank, so no call is ever made against an empty host.
- `NotConfigured` handling is complete: `AuthManager` short-circuits in `beginTelegramLogin`,
  `submitCode`, `refreshIfNeeded`; `MainActivity` guards the drawer entry and toasts
  `auth_not_configured` when not configured. App stays fully usable with a blank backend.
- `LoginActivity` uses `setContentViewWithToolbar(binding.root, ...)` (a real `BaseActivity` overload),
  `repeatOnLifecycle(STARTED)` collection, `ActivityNotFoundException` fallback toast, and no leaked
  context. `MainActivity` change returns `Boolean` correctly and uses the existing
  `requestActivityLauncher`. Manifest registers `LoginActivity` with `exported="false"`.
