# Android data + network layer — rigorous audit

**Scope**: `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/` — `util/**`, `handler/**`,
`fmt/**`, `dto/**`, `auth/**`, `template/**`, plus the manifest / network-security config that
these layers depend on.

**Method**: every claim below is derived from source I read in this session and is cited with
`file:line`. Nothing was inferred from behaviour, logs, or runtime observation. Where a claim
depends on a runtime condition I could not observe (e.g. what the live backend actually returns),
I say so explicitly.

**Headline**: three findings are severe enough to fix before the next release —
(1) an **exported, BROWSABLE deep-link surface that silently deletes the user's servers and
installs + selects an attacker-supplied one** (§1), (2) **user-installed CA certificates are
trusted for all traffic including login, payments and subscription fetches, with no pinning**
(§2), and (3) **a malformed/hostile subscription body deletes the whole subscription's server
list before it fails** (§3). Everything else is ranked below.

---

## Severity index

| # | Severity | Finding | Anchor |
|---|---|---|---|
| 1 | **Critical** | Drive-by deep link wipes ungrouped servers, installs + selects attacker server, can stop/start the tunnel and replace routing rules | `ui/UrlSchemeActivity.kt:87,142,178,186` |
| 2 | **Critical** | User CA certs trusted app-wide; no pinning; cleartext permitted; manual redirect follower allows HTTPS→HTTP downgrade | `res/xml/network_security_config.xml`, `util/HttpUtil.kt:359` |
| 3 | **High** | `parseCustomConfigServer` deletes the server list *before* parsing; any parse failure leaves the subscription empty | `handler/AngConfigManager.kt:613` |
| 4 | **High** | `SubscriptionSyncManager.importAll` reads `.response` directly, silently skips subs, then **deletes** them as "gone remotely" | `auth/SubscriptionSyncManager.kt:38,68` |
| 5 | **High** | `V2rayConfig` non-null fields are Gson-nullable → NPE inside `CustomFmt.parse`; combines with #3 | `dto/V2rayConfig.kt:10-15,508` |
| 6 | **High** | `ZipUtil.unzipToFolder` has no Zip-Slip guard and no size/entry cap | `util/ZipUtil.kt:88-98` |
| 7 | **High** | `AuthTokenStore`/`KeystoreKeyProvider` open MMKV in `SINGLE_PROCESS_MODE` but are used from the `:bg` process | `auth/AuthTokenStore.kt:40`, manifest:339-341 |
| 8 | **High** | Exported broadcast receivers (`RECEIVER_EXPORTED`) let any app stop the VPN | `util/Utils.kt:552`, `core/CoreServiceManager.kt:242,524` |
| 9 | **Medium-High** | `SubscriptionGuard` / `SubscriptionOrigin` accept `departament.<attacker-tld>` and `evil-departament.com` | `util/SubscriptionGuard.kt:19`, `util/SubscriptionOrigin.kt:35` |
| 10 | **Medium-High** | Concurrent read-modify-write on the stored server/sub lists across two processes | `handler/MmkvManager.kt:158-175`, `AngConfigManager.kt:403-423` |
| 11 | **Medium-High** | Migrations + `ensureDefaultSettings` run in **every** process, unserialised | `AngApplication.kt:32-47`, `SettingsManager.kt:662-703` |
| 12 | **Medium** | No `callTimeout`, no body size cap anywhere in `HttpUtil` / API client → unbounded memory | `util/HttpUtil.kt:328-357,154,210,280` |
| 13 | **Medium** | `FmtBase.getQueryParam` throws on `?flag` and silently truncates values containing `=` | `fmt/FmtBase.kt:43-46` |
| 14 | **Medium** | `removeAllServer()` also destroys the WebDAV config and the subscription id list | `handler/MmkvManager.kt:287-293` |
| 15 | **Medium** | Secrets written to plaintext MMKV; backup zip exports them unencrypted | `SettingsManager.kt:354-355`, `MmkvManager.kt:708`, `ui/BackupActivity.kt:106` |
| 16 | **Medium** | SOCKS-share password generated with non-crypto `kotlin.random.Random` | `handler/SettingsManager.kt:348-365` |
| 17 | **Medium** | Full subscription URL (a bearer-equivalent secret) written to logcat | `handler/AngConfigManager.kt:789` |
| 18 | **Medium** | `SpeedtestManager.socketConnectTime` leaks the socket + list entry on every failure | `handler/SpeedtestManager.kt:125-147` |
| 19 | **Medium** | `initSubsList()` resurrects deleted subscriptions whenever the id list is empty | `handler/MmkvManager.kt:354-363` |
| 20 | **Medium** | `ensureDefaultSubscription` reorders the user's subscription list | `handler/SettingsManager.kt:718-721` |
| 21 | **Medium** | Update checker points at upstream `2dust/v2rayNG` and offers its APK | `AppConfig.kt:129`, `UpdateCheckerManager.kt:19,103` |
| 22 | **Low-Medium** | Unbounded `HashMap<String, Bitmap>` avatar cache + unbounded remote body read | `util/AvatarManager.kt:51,188` |
| 23 | **Low-Medium** | `ProfileItem` overrides `equals` without `hashCode`, and casts unchecked | `dto/entities/ProfileItem.kt:91-93` |
| 24 | **Low-Medium** | HWID + device model forwarded to arbitrary redirect targets | `util/HttpUtil.kt:256,306-312` |
| 25 | **Low-Medium** | `importAll` clobbers the user's subscription name on every sync | `auth/SubscriptionSyncManager.kt:46-48` |
| 26 | **Low** | Assorted parsing/robustness defects (IPv6 WireGuard endpoints, routing index bounds, `toIdnUrl` global replace, `compareVersions`, `ApiGson` escape hatch, dead `KEY_EXPIRES_AT`, Telegram poll aborts on one blip) | see §26 |

---

## 1. Critical — exported deep link deletes servers, installs and selects an attacker's server

`UrlSchemeActivity` is `android:exported="true"` with two `BROWSABLE` intent filters
(`AndroidManifest.xml:162-190`): `v2rayng://install-config|install-sub` and a wildcard
`depv://` scheme. Anything a browser can navigate to — an `<iframe src="depv://…">`, a QR
code, another app — reaches it, and `onCreate` acts immediately with **no confirmation UI**
(`ui/UrlSchemeActivity.kt:27-67`).

Three distinct capabilities are exposed.

**1a. Server-list wipe + attacker server becomes the selected server.**

```kotlin
// ui/UrlSchemeActivity.kt:186
val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
//                                                          subid=""  ^^^^^ append = false
```

Follow the `append = false` / `subid = ""` pair through:

* `AngConfigManager.parseBatchConfig` (`:356`) resolves `getRemovedSelectedProfile("", false)`
  → `if (subid.isBlank() || append) return null` (`:535`) → `removedSelected = null`.
* Once at least one config parses, `:380-381` runs
  `MmkvManager.removeServerViaSubid(subid)`.
* `MmkvManager.getSubscriptionId("")` maps the empty id to `DEFAULT_SUBSCRIPTION_ID`
  (`handler/MmkvManager.kt:347-349`), so `removeServerViaSubid` (`:219-234`) deletes **every
  ungrouped/default-bucket server profile** and its affiliation info, and clears
  `KEY_SELECTED_SERVER` if it pointed at one of them.
* `resolveSelectedKey(keyToProfile, null, "", false)` (`:504-529`) then: `removedSelected` is
  null, `append` is false, `getSelectServer()` is now null (just cleared), so it falls through
  to `return keyToProfile.keys.lastOrNull()` — **the freshly imported, attacker-supplied
  profile is set as the active server** at `:385`.

`SubscriptionGuard` does not help here: it is deliberately applied only to *subscription*
URLs, not to single-server `vless://`/`vmess://` links (documented at
`AngConfigManager.kt:914-919`). That design decision is defensible for a paste the user
initiates; it is not defensible for a link a web page can fire without interaction.

`ScScannerActivity` has the same `append = false` (`ui/ScScannerActivity.kt:22`), so scanning
any QR from the launcher shortcut also wipes the ungrouped bucket. Contrast
`MainActivity.kt:2190` and `tv/TvReceiveActivity.kt:147`, which both pass `append = true` —
the inconsistency is the bug.

**1b. Remote tunnel control.** `depv://disconnect` / `depv://close` →
`CoreServiceManager.stopVService(this)` (`ui/UrlSchemeActivity.kt:87`); `depv://connect`,
`depv://toggle` likewise (`:85,89-95`). A web page can silently drop the user's VPN and then
observe their real IP.

**1c. Remote routing-rule replacement.** `depv://routing/onadd/<base64>` →
`importRoutingRules(json, apply = true)` (`:120-123`) → `SettingsManager.resetRoutingRulesets`
(`handler/SettingsManager.kt:95-112`), which replaces every non-`locked` ruleset
(`:118-128`) and then restarts a running tunnel (`ui/UrlSchemeActivity.kt:158-160`). Rules
control which traffic goes `direct` vs `proxy`, so this is a remote de-anonymisation primitive.

**Fix.** (i) Change `importBatchConfig(..., append = false)` to `true` in
`UrlSchemeActivity:142,186` and `ScScannerActivity:22` — nothing on those paths intends a
destructive replace. (ii) Gate every `depv://`/`v2rayng://` action behind an explicit
in-app confirmation dialog showing what will be added/changed. (iii) Consider dropping the
`connect`/`disconnect`/`toggle`/`routing` hosts from the BROWSABLE filter entirely, or moving
them to a non-browsable scheme only the bot deep-link can use.

---

## 2. Critical — user CA certificates trusted app-wide; no pinning; downgradeable redirects

`app/src/main/res/xml/network_security_config.xml`:

```xml
<base-config cleartextTrafficPermitted="true">
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" tools:ignore="AcceptsUserCertificates" />
    </trust-anchors>
</base-config>
```

and `AndroidManifest.xml:49,52` wires it up plus `android:usesCleartextTraffic="true"`.

This is `base-config`, so it applies to **all** app traffic, including:

* the login / 2FA / Google-token / payment endpoints in
  `auth/DepartamentApiClientImpl.kt:141-157,233-241` (email + password go out in the body at
  `:142`);
* the `Authorization: Bearer <jwt>` header attached to every API call
  (`auth/DepartamentApiClientImpl.kt:75-77`);
* the subscription fetch, whose body **is** the VPN configuration — outbounds, routing, DNS
  (`util/HttpUtil.kt:241-297` → `AngConfigManager.updateConfigViaSub:844`).

I grepped the whole source tree for `CertificatePinner`, `X509TrustManager`,
`HostnameVerifier` and `sslSocketFactory`: **zero hits**. There is no second layer.

Consequence: any user-installed or MDM-pushed CA (a category that includes "helpful" corporate
roots, debug proxies the user forgot to remove, and social-engineering malware) can read the
session JWT, read login credentials, and — worse — **rewrite the subscription response**, which
means full control of where the user's traffic goes.

**Compounding: the manual redirect follower permits an HTTPS→HTTP downgrade.**
`getUrlContentWithUserAgent` / `…Ex` build the client with `followRedirects = false`
(`util/HttpUtil.kt:179,248`) and hand-roll the redirect at `:197-207` and `:266-276`.
`resolveLocation` (`:359-376`) resolves the `Location` header against the base URI and returns
whatever comes out — **there is no check that the scheme stays `https`**. Because
`usesCleartextTraffic="true"`, a `302 Location: http://…` on a subscription fetch is followed
in the clear.

**Fix.** Remove `<certificates src="user" />` from `base-config` (keep it in a
`debug-overrides` block if needed for development). Scope `cleartextTrafficPermitted` to a
`domain-config` for `127.0.0.1` only. Add `CertificatePinner` for `web.departament.site` on the
API client. In `resolveLocation`, reject a resolved URL whose scheme is not `https` when the
current URL is `https`.

---

## 3. High — a malformed subscription body deletes the whole server list before failing

`AngConfigManager.parseCustomConfigServer` (`handler/AngConfigManager.kt:590-673`):

```kotlin
val looksLikeJson = (trimmedServer.startsWith("{") || trimmedServer.startsWith("["))
    && server.contains("outbounds")                                    // :603-604
if (looksLikeJson) {
    try {
        val serverList: Array<Any> = JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()  // :607
        if (serverList.isNotEmpty()) {
            val removedSelected = getRemovedSelectedProfile(subid, append)
            if (!append) {
                MmkvManager.removeServerViaSubid(subid)               // :613  ← DESTRUCTIVE, PRE-PARSE
            }
            var count = 0
            for (srv in serverList.reversed()) {
                val rawConfig = stripVendorRootKey(JsonUtil.toJsonPretty(srv) ?: "")
                val config = CustomFmt.parse(rawConfig) ?: continue    // :620  ← can THROW
                …
                count += 1
            }
            if (count > 0) { … }
            return count
        }
    } catch (e: Exception) { LogUtil.e(…) }                            // :635-637  ← count discarded
```

Two things go wrong together:

* The delete at `:613` happens **before** a single element has been parsed. Compare
  `parseBatchConfig`, which correctly guards it behind `if (configs.isNotEmpty())`
  (`:379-381`) — the link-list path got this right and the JSON path did not.
* `CustomFmt.parse` is declared `fun parse(str: String): ProfileItem` — **non-null**
  (`fmt/CustomFmt.kt:15`). The `?: continue` at `:620` and the `?: return 0` at `:642` are
  therefore dead code the compiler will flag as "elvis always returns left". What actually
  happens on bad input is an *exception*, not a null.

`CustomFmt.parse` throws on at least two hostile inputs (see §5): a Gson `JsonSyntaxException`
for a shape mismatch, and an NPE from `getProxyOutbound()` when `outbounds` is absent.

**Reproduction from the file contents alone.** A subscription that answers with
`[{"note":"outbounds"}]`:
`looksLikeJson` is true (starts with `[`, contains the substring `outbounds`) → the array
parses non-empty → **`removeServerViaSubid` deletes every server in the subscription** →
`CustomFmt.parse` throws (no `outbounds` key → `outbounds` is null → `outbounds.forEach` NPEs
at `dto/V2rayConfig.kt:508`) → caught at `:635`, `count` lost → the compatibility path at
`:639-654` re-parses the array as a single `V2rayConfig` and throws again → `return 0`.

`updateConfigViaSub` then reports `SubscriptionUpdateResult(failureCount = 1)` (`:883`) — the
user sees "update failed" and has **zero servers**, with no undo.

**Fix.** Move `removeServerViaSubid` to after the loop, behind `if (count > 0)`, exactly as
`parseBatchConfig` does. Change `CustomFmt.parse` to return `ProfileItem?` and swallow its own
parse failures (or wrap the per-element `parse` in a `runCatching`), so one bad element in a
50-element template does not abort the batch.

---

## 4. High — managed-subscription sync silently skips, then deletes, subscriptions

`auth/SubscriptionSyncManager.kt:32-77`:

```kotlin
for (info in items) {
    val raw = info.subscription?.response ?: continue      // :38
    val url = raw.subscriptionUrl
    if (url.isBlank()) continue                           // :40
    …
}
// Drop any previously managed subscription that is gone remotely.
for ((uuid, guid) in managed) {
    if (!newMap.containsKey(uuid)) {
        SubscriptionUpdater.cancelOne(subId = guid)
        MmkvManager.removeSubscription(guid)              // :71  ← deletes sub AND all its servers
    }
}
```

Two defects, and they amplify each other.

**4a. It bypasses the tolerant accessor.** The DTO file explicitly defines
`SubResponseWrapper.raw()` to handle *both* nesting shapes the backend uses:

```kotlin
// auth/dto/SubscriptionDtos.kt:128-133
data class SubResponseWrapper(
    val response: RawSubDto? = null,
    val data: SubDataWrapper? = null,
) {
    fun raw(): RawSubDto? = response ?: data?.response
}
```

`importAll:38` reads `.response` directly, so every item delivered in the `data.response`
shape is skipped with no log and no error.

**4b. The DTO says the field it depends on is not present on this endpoint.** `importAll` is
fed by `getSubscriptionAll()` (`auth/AccountRepository.kt:93-96` →
`DepartamentApiClientImpl.kt:169-170`), and the DTO comments on that exact field read:

```kotlin
// auth/dto/SubscriptionDtos.kt:34-37
// NOT present on /all items — only on the GET /client/subscription summary / connect payload.
// Kept so the device-management / QR / import paths compile; stays blank/null from /all.
val remnawaveUuid: String = "",
val subscription: SubResponseWrapper? = null,
```

If that comment is accurate for the live backend, `importAll` `continue`s on **every** item,
imports nothing, and `autoImportSubscriptions()` still returns `Result.success(emptyList())` —
a completely silent no-op after a successful login. There is no fallback to
`getPrimarySubscription()` (`DepartamentApiClientImpl.kt:166-167`), which the same DTO file
says *is* the endpoint carrying the connect URL. I cannot verify the live response shape from
this repo; the contradiction between `:34-37` and `:38` is the provable part.

**4c. The skip is destructive.** Because a skipped item never lands in `newMap`, the cleanup
loop at `:68-73` classifies it as "gone remotely" and calls `MmkvManager.removeSubscription`,
which cascades into `removeServerViaSubid` (`handler/MmkvManager.kt:389-396`). So a transient
backend shape change, or one item missing its `response` wrapper, **deletes the user's
subscription and every server in it**.

**Fix.** Use `info.subscription?.raw()`. Fall back to `getPrimarySubscription()` when `/all`
carries no connect payload. Only run the cleanup loop when the fetch produced at least one
successfully-imported item, and never delete on a partial result.

---

## 5. High — `V2rayConfig`'s non-null fields are null in practice, and `CustomFmt` trusts them

```kotlin
// dto/V2rayConfig.kt:7-22
data class V2rayConfig(
    var remarks: String? = null,
    …
    val log: LogBean,                      // :10  no default, non-null
    val inbounds: ArrayList<InboundBean>,  // :11  no default, non-null
    var outbounds: ArrayList<OutboundBean>,// :12  no default, non-null
    …
    val routing: RoutingBean,              // :15  no default, non-null
```

Gson instantiates via `Unsafe` and writes fields reflectively — it does not run the Kotlin
constructor and does not honour Kotlin nullability. Any JSON object missing `outbounds`
(Remnawave XRAY_JSON templates legitimately omit `inbounds`/`routing`, per the comment at
`AngConfigManager.kt:598-601`) yields an object whose non-null-typed field is null at runtime.

`getProxyOutbound` then dereferences it with no guard:

```kotlin
// dto/V2rayConfig.kt:507-516
fun getProxyOutbound(): OutboundBean? {
    outbounds.forEach { outbound ->   // :508  NPE when outbounds is null
```

and `CustomFmt.parse` calls it unconditionally:

```kotlin
// fmt/CustomFmt.kt:15-25
fun parse(str: String): ProfileItem {            // non-null return type
    val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)   // :18  can throw
    val outbound = fullConfig?.getProxyOutbound()                      // :19  can NPE
```

A second throw site in the same call chain: `getServerPort()` for a WireGuard outbound does
`endpoint?.substringAfterLast(":")?.toInt()` (`dto/V2rayConfig.kt:373`) — `NumberFormatException`
for any endpoint without a numeric tail.

`ServerCustomConfigActivity` wraps the call in a `try/catch` (`ui/ServerCustomConfigActivity.kt:87-93`),
which is the correct pattern; the subscription path does not, and that is what makes §3 destructive.

**Fix.** Give every `V2rayConfig` collection/object field a default
(`= arrayListOf()` / `= RoutingBean(...)`), or null-guard `getProxyOutbound`/`getServerPort`.
Make `CustomFmt.parse` return `ProfileItem?` and catch inside.

---

## 6. High — Zip Slip and zip-bomb in the backup restore path

```kotlin
// util/ZipUtil.kt:87-98
ZipFile(zipFile).use { zip ->
    zip.entries().asSequence().forEach { entry ->
        zip.getInputStream(entry).use { input ->
            val filePath = destDirectory + File.separator + entry.name   // :90
            if (!entry.isDirectory) {
                extractFile(input, filePath)                              // :92
```

`entry.name` is used verbatim. There is no canonical-path containment check, no entry-count
cap, and no per-entry or total size cap (`extractFile:115-123` copies until EOF).

Reachable from two places, both taking attacker-influenceable archives:

* `ui/BackupActivity.kt:133-156` — the user picks any `.zip` through SAF; it is copied to
  `cacheDir` and passed straight to `unzipToFolder` (`:121`).
* `ui/BackupActivity.kt:244` — `restoreViaWebDav()` downloads the archive from the configured
  WebDAV server (`handler/WebDavManager.kt:89-114`) and restores it.

`destDirectory` is `cacheDir/<timestamp>` (`ui/BackupActivity.kt:119`), so a
`../../files/assets/geosite.dat` entry writes into the app's private data directory. Combined
with `MMKV.restoreAllFromDirectory(backupDir)` at `:125`, a crafted archive gets both arbitrary
file placement and full control of MMKV contents.

**Fix.** Before writing, resolve `File(destDirectory, entry.name).canonicalPath` and reject
anything not prefixed by `File(destDirectory).canonicalPath + File.separator`. Cap entry count
and cumulative uncompressed bytes.

---

## 7. High — the auth MMKV is opened `SINGLE_PROCESS_MODE` but read from a second process

```kotlin
// auth/AuthTokenStore.kt:36-51
private fun openStore(): MMKV {
    return try {
        val cryptKey = KeystoreKeyProvider.getOrCreateCryptKey()
        if (!cryptKey.isNullOrBlank()) {
            MMKV.mmkvWithID(ID, MMKV.SINGLE_PROCESS_MODE, cryptKey)   // :40
        } else {
            MMKV.mmkvWithID(ID)                                        // :42  ← plaintext fallback
        }
    } catch (e: Throwable) {
        try { MMKV.mmkvWithID(ID) } catch (e2: Throwable) { MMKV.defaultMMKV() }  // :46-48
    }
}
```

`KeystoreKeyProvider` likewise opens its key holder with the default (single-process) mode
(`auth/KeystoreKeyProvider.kt:36`).

But `AuthTokenStore` is touched from **two** processes:

* the UI process, via `DepartamentApiClientImpl.authInterceptor` (`:75,81`) and
  `AccountSession` (`auth/AccountSession.kt:27-28,38`);
* the `:bg` process, because `AngConfigManager.updateConfigViaSub:799` calls
  `AuthTokenStore.deviceId()`, and `updateConfigViaSub` runs inside
  `SubscriptionUpdater.UpdateTask.doWork` (`handler/SubscriptionUpdater.kt:150-191`), which
  WorkManager executes in `:bg` — `AngApplication.kt:25-27` sets
  `setDefaultProcessName("${ANG_PACKAGE}:bg")` and `AndroidManifest.xml:339-341` declares
  `RemoteWorkManagerService` with `android:process=":bg"`.

`deviceId()` is not read-only — it *writes* on first call (`auth/AuthTokenStore.kt:65-71`).
MMKV documents `SINGLE_PROCESS_MODE` across processes as a data-corruption condition. The
comparison is stark: `MmkvManager` correctly uses `MMKV.MULTI_PROCESS_MODE` for all seven of
its instances (`handler/MmkvManager.kt:35-41`); the auth store — the one holding the session
JWT — does not.

**Three secondary problems in the same function.**

* The `?:` fallback at `:42` opens the *same file ID* without a crypt key. If the Keystore is
  transiently unavailable (direct boot, OEM breakage, key invalidation), MMKV cannot parse the
  existing encrypted file and the user is silently logged out — and from then on the JWT is
  written **in plaintext** to `departament_auth`, contradicting the class KDoc at `:18-20`.
* The last-resort `MMKV.defaultMMKV()` at `:48` writes keys named `"token"`, `"user_json"`,
  `"device_id"`, `"managed_guids_json"` into the shared default instance.
* `KeystoreKeyProvider.randomSecret()` (`:86-90`) draws **8** random bytes and hex-encodes
  them to 16 characters. The comment says "16-char hex secret == exactly 16 bytes"; the byte
  *length* is 16 but the *entropy* is 64 bits, not 128. Low practical impact (the secret is
  sealed in the Keystore), but the comment overstates the guarantee.

**Fix.** Open both `departament_auth` and `departament_keyholder` with
`MMKV.MULTI_PROCESS_MODE`. Never fall back to the same ID unencrypted — use a distinct ID, or
fail closed. Draw 16 bytes in `randomSecret()`.

---

## 8. High — exported broadcast receivers let any installed app stop the VPN

```kotlin
// util/Utils.kt:552-556
fun receiverFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.RECEIVER_EXPORTED
} else {
    ContextCompat.RECEIVER_NOT_EXPORTED
}
```

Used at `core/CoreServiceManager.kt:242`, `viewmodel/MainViewModel.kt:98`,
`service/QSTileService.kt:52`. On API 33+ these receivers are **exported**, so any app can
send `com.v2ray.ang.action.service` with `key = MSG_STATE_STOP` (4):

```kotlin
// core/CoreServiceManager.kt:505-534
override fun onReceive(ctx: Context?, intent: Intent?) {
    val serviceControl = serviceControl?.get() ?: return
    when (intent?.getIntExtra("key", 0)) {
        …
        AppConfig.MSG_STATE_STOP -> { serviceControl.stopService() }        // :524-527
        AppConfig.MSG_STATE_RESTART -> {
            serviceControl.stopService()
            Thread.sleep(500L)                                              // :532  ANR on receiver thread
            startVService(serviceControl.getService())
        }
```

There is no sender validation of any kind. The app's own sends are package-restricted
(`util/MessageUtil.kt:84` sets `intent.package`), but that restricts *sending*, not
*receiving*. The logic returns `RECEIVER_EXPORTED` on the newer platform, which is backwards:
API 33 introduced the flag precisely so apps could declare `NOT_EXPORTED`.

**Fix.** Return `RECEIVER_NOT_EXPORTED` unconditionally — nothing outside the app needs to
send these. Move the `Thread.sleep(500L)` off the receiver's main thread.

---

## 9. Medium-High — the "departament-only" guards accept attacker-controlled domains

```kotlin
// util/SubscriptionGuard.kt:18-19
val host = uri.host?.lowercase() ?: return false
return host.split(".").any { it == REQUIRED_LABEL }   // REQUIRED_LABEL = "departament"
```

Any label match anywhere passes. `https://departament.attacker-tld/sub` and
`https://departament.co/sub` both satisfy it — an attacker only needs to register
`departament.<some-tld>`. This guard is the sole thing standing between a pasted/scanned/
deep-linked URL and `importUrlAsSubscription` (`handler/AngConfigManager.kt:922-926`), whose
content becomes the user's VPN configuration.

`SubscriptionOrigin` is weaker still:

```kotlin
// util/SubscriptionOrigin.kt:35-37
if (!host.lowercase().contains("departament")) return false
val p = path ?: return false
return p.contains("/sub")
```

`contains` on the host matches `evil-departament-phish.com`; `contains("/sub")` on the path
matches `/subscribe-to-anything`. Per the class KDoc (`:6-14`) this decides whether the
**Account tab and payment entry points** are shown, so a foreign subscription can unlock the
payment UI.

**Fix.** Match against an allow-list of exact hostnames/suffixes —
`host == "departament.site" || host.endsWith(".departament.site")` — shared by both objects.

---

## 10. Medium-High — cross-process read-modify-write on the stored lists

Every list mutation is a non-atomic decode → mutate → encode:

```kotlin
// handler/MmkvManager.kt:158-175  encodeServerConfig
val serverList = decodeServerList(subId)
if (!serverList.contains(key)) {
    serverList.add(0, key)
    encodeServerList(serverList, subId)
```

```kotlin
// handler/AngConfigManager.kt:403-423  batchSaveConfigs
val serverList = MmkvManager.decodeServerList(subid)   // read
configs.forEach { … serverList.add(0, key) }           // mutate
MmkvManager.encodeServerList(serverList, subid)        // write
```

Same shape at `MmkvManager.kt:391-393` (`removeSubscription`), `:408-412`
(`encodeSubscription`), `:263-265` (`encodeServerTestDelayMillis`),
`AuthTokenStore.kt:125-136` (`getManagedGuids`/`setManagedGuids`), and
`SettingsManager.kt:152-162,172-176,221-227,234-239`.

MMKV's `MULTI_PROCESS_MODE` (`handler/MmkvManager.kt:35-41`) makes each individual `encode`
atomic; it does **not** make a read-modify-write pair atomic. And there genuinely are two
writers: `SubscriptionUpdater.UpdateTask` runs `updateConfigViaSub` → `parseBatchConfig` →
`batchSaveConfigs` in `:bg` (see §7 for the process proof) while the UI process runs the same
code from `MainActivity`/`SubSettingActivity`/`SubscriptionSyncManager.importAll`
(`auth/SubscriptionSyncManager.kt:60`). Last write wins; servers imported by one process
vanish when the other writes its stale copy back.

**Fix.** Funnel all list mutations through a single `@Synchronized` accessor in
`MmkvManager` *and* a cross-process lock (MMKV exposes `lock()`/`unlock()`), or move the
subscription worker into the main process so a JVM monitor suffices.

---

## 11. Medium-High — migrations run in every process, concurrently

`AngApplication.onCreate` runs in **every** process the app starts — main, `:bg`,
`:RunSoLibV2RayDaemon` — and unconditionally calls:

```kotlin
// AngApplication.kt:32-47
override fun onCreate() {
    super.onCreate()
    MMKV.initialize(this)
    WorkManager.initialize(this, workManagerConfiguration)
    SettingsManager.initApp(this)      // :41
```

`initApp` (`handler/SettingsManager.kt:44-50`) runs `ensureDefaultSettings()`,
`initRoutingRulesets()`, `migrateServerListToSubscriptions()` and
`migrateHysteria2PinSHA256()`. Nothing serialises them across processes. When the user opens
the app while `:bg` is starting for a scheduled subscription update, both processes execute:

```kotlin
// handler/SettingsManager.kt:696-702
subscriptionServerMap.forEach { (subId, serverGuids) ->
    MmkvManager.encodeServerList(serverGuids, subId)   // :697  overwrite, not merge
}
MmkvManager.encodeSettings(migrationKey, true)         // :702
```

Three separate problems:

* **The flag is written last** (`:702`), well after the mutations at `:697`. A crash or a
  process kill in between re-runs the whole migration on next start.
* **`encodeServerList` overwrites rather than merges.** If the subscription already has a
  stored list (because the other process finished its update first), that list is replaced by
  whatever the legacy `KEY_ANG_CONFIGS` blob contained.
* **The KDoc is wrong.** `:660` states "After migration, `KEY_ANG_CONFIGS` is removed" —
  nothing in `:662-703` removes it, and `MmkvManager.readLegacyServerList` (`:53-55`) has no
  companion delete. The legacy key lingers forever.

**Fix.** Guard `initApp` so it only runs in the main process
(`if (packageName != currentProcessName) return`), write the migration flag *before* the
mutation or wrap the pair in a cross-process lock, merge instead of overwrite, and actually
remove `KEY_ANG_CONFIGS`.

---

## 12. Medium — no call timeout and no body cap anywhere in the fetch path

`HttpUtil.buildOkHttpClient` sets only connect + read timeouts:

```kotlin
// util/HttpUtil.kt:335-339
val builder = OkHttpClient.Builder()
    .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
    .followRedirects(followRedirects)
    .followSslRedirects(followRedirects)
```

`readTimeout` is per-read, so a server that drips one byte every 14 seconds holds the call
open indefinitely. `DepartamentApiClientImpl.defaultClient()` has the same gap
(`:89-93`). By contrast `WebDavManager.init` (`:29-34`) and
`SpeedtestManager.httpPingClient` (`:36-43`) *do* set `callTimeout` — the inconsistency shows
the omission is accidental.

The bodies are read whole, unbounded:

* `util/HttpUtil.kt:154` `response.body?.string()`
* `util/HttpUtil.kt:210,280` `response.body?.string()`
* `auth/DepartamentApiClientImpl.kt:305,214,185` `it.body?.string()` / `it.body?.bytes()`
* `util/AvatarManager.kt:188` `resp.body?.bytes()`

Then `AngConfigManager.importBatchConfig:229` calls `Utils.decode(server)`, which
base64-decodes the **entire** body into a second copy, and `parseBatchConfig:368` materialises
`servers.lines()` as a full `List<String>` — so peak footprint is roughly 3× the response for
a link-list subscription, and `parseCustomConfigServer:607` additionally builds an
`Array<Any>` of Gson `LinkedTreeMap`s plus a pretty-printed re-serialisation per element
(`:619`). A 20 MB response is an OOM on a low-RAM device, and the subscription server is the
one entity in this system that is *not* fully trusted (§2).

**Fix.** Add `.callTimeout(...)` to both clients. Cap the body:
`response.peekBody(MAX_SUBSCRIPTION_BYTES)` or check `Content-Length` and stream-with-limit,
rejecting anything over a few MB.

---

## 13. Medium — `getQueryParam` throws on valueless params and truncates values containing `=`

```kotlin
// fmt/FmtBase.kt:43-46
fun getQueryParam(uri: URI): Map<String, String> {
    return uri.rawQuery.split("&")
        .associate { it.split("=").let { (k, v) -> k to Utils.decodeURIComponent(v) } }
}
```

* **Throws** on a param with no `=`. `"flow".split("=")` is a one-element list, and
  destructuring `(k, v)` calls `component2()` → `IndexOutOfBoundsException`. A link such as
  `vless://uuid@h:443?type=tcp&flow` kills the whole parse.
* **Silently truncates** on a param whose value contains an unencoded `=`.
  `"extra={\"a\":1}=x".split("=")` yields three elements; destructuring takes only the first
  two, so everything after the second `=` is dropped. Affects `extra` (xhttp JSON),
  `path`, and any base64 value carrying `=` padding (`pbk`, `sid`, `ech`, `pcs`) — the exact
  fields `getItemFormQuery` reads at `fmt/FmtBase.kt:84-93`.
* `uri.rawQuery` is a platform type; the three callers that guard it
  (`VlessFmt.kt:24`, `VmessFmt.kt:161`, `WireguardFmt.kt:23`) return null first,
  `TrojanFmt.kt:29` and `Hysteria2Fmt.kt:33` branch on it — so the null case is covered, but
  only by convention at each call site.

Impact is contained (per-line, absorbed by `parseConfig`'s catch at
`AngConfigManager.kt:716-719`), but a valid subscription line is silently dropped or, worse,
imported with a corrupted transport parameter.

Related, in the same family: `TrojanFmt.parse` and `Hysteria2Fmt.parse` are declared to return
a **non-null** `ProfileItem` (`fmt/TrojanFmt.kt:19`, `fmt/Hysteria2Fmt.kt:21`) and validate
nothing — `trojan://` alone produces a profile with `server = ""` and `serverPort = "-1"`
(`uri.port` is `-1` when absent). `ShadowsocksFmt.parseSip002` (`:33-35`) and
`SocksFmt.parse` (`:21-22`) do validate; Trojan and Hysteria2 don't.

**Fix.**
```kotlin
fun getQueryParam(uri: URI): Map<String, String> =
    (uri.rawQuery ?: "").split("&")
        .filter { it.isNotEmpty() }
        .associate { p ->
            val i = p.indexOf('=')
            if (i < 0) p to "" else p.substring(0, i) to Utils.decodeURIComponent(p.substring(i + 1))
        }
```
and add the `idnHost.isEmpty()` / `port <= 0` guards to Trojan and Hysteria2.

---

## 14. Medium — "remove all servers" also destroys the WebDAV config and the subscription index

```kotlin
// handler/MmkvManager.kt:287-293
fun removeAllServer(): Int {
    val count = profileFullStorage.allKeys()?.count() ?: 0
    mainStorage.clearAll()          // :289
    profileFullStorage.clearAll()
    serverAffStorage.clearAll()
    return count
}
```

`mainStorage` is not a server-only namespace. It holds `KEY_SELECTED_SERVER`,
`KEY_ANG_CONFIGS`, every `SUB_SERVERS_<id>` list, `KEY_SUB_IDS`, **and** `KEY_WEBDAV_CONFIG`
(`:29-33`, written at `:708-710`, read at `:715-718`).

So `MainActivity.kt:2276` → `MainViewModel.kt:514-517` → "delete all servers" silently deletes
the user's WebDAV backup endpoint, credentials and remote path. It also wipes `KEY_SUB_IDS`,
which then gets rebuilt from `subStorage.allKeys()` by `initSubsList` (§19) — in arbitrary
iteration order, so the user's subscription ordering is lost too.

**Fix.** Remove only the server-related keys:
`mainStorage.allKeys()?.filter { it.startsWith(KEY_SUB_SERVER_PREFIX) }?.forEach { mainStorage.remove(it) }`,
plus `KEY_SELECTED_SERVER`. Leave `KEY_WEBDAV_CONFIG` and `KEY_SUB_IDS` alone.

---

## 15. Medium — secrets stored in plaintext MMKV and exported unencrypted by backup

`settingsStorage` and `mainStorage` are plain, unencrypted MMKV instances
(`handler/MmkvManager.kt:35-41` — no crypt key, unlike `AuthTokenStore`). Written into them:

* **SOCKS proxy password** — `MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PASSWORD, pass)`
  (`handler/SettingsManager.kt:355`), read back at `:320-322`.
* **WebDAV password** — `WebDavConfig(password = …)` serialised to JSON and stored
  (`dto/entities/WebDavConfig.kt:6`, `handler/MmkvManager.kt:708-710`).
* **Subscription URLs** — the bearer-equivalent secret for the VPN account
  (`dto/entities/SubscriptionItem.kt:5`, stored via `MmkvManager.encodeSubscription:404-406`).
* **Per-server credentials** — `ProfileItem.password`, `.secretKey`, `.preSharedKey`,
  `.obfsPassword` (`dto/entities/ProfileItem.kt:18,52,53,58`), stored as JSON at
  `MmkvManager.kt:158-160`.

`BackupActivity.backupConfigurationToCache` then calls `MMKV.backupAllToDirectory(backupDir)`
(`ui/BackupActivity.kt:106`) and zips the result with no encryption (`:111`), producing a file
the user can save anywhere or upload to WebDAV (`handler/WebDavManager.kt:46-80`) over
HTTP Basic. Everything above travels in that zip in cleartext.

**Fix.** Either encrypt the two MMKV instances that hold credentials (same
`KeystoreKeyProvider` pattern already used for auth), or encrypt the backup archive with a
user-supplied passphrase, and warn in the restore/backup UI that the file contains
credentials.

---

## 16. Medium — the LAN-exposed SOCKS password uses a non-cryptographic RNG

```kotlin
// handler/SettingsManager.kt:347-365
@Synchronized
fun ensureSocksShareCredentials(): Pair<String, String> {
    var user = getSocksUsername()
    var pass = getSocksPassword()
    if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
        user = "dep_" + randomHex(6)
        pass = randomHex(12)
        …
}

private fun randomHex(length: Int): String {
    val chars = "0123456789abcdef"
    return buildString(length) { repeat(length) { append(chars[Random.nextInt(chars.length)]) } }  // :363
}
```

`Random` here is `kotlin.random.Random` (imported at `:37`) — a linear-congruential PRNG, not
a CSPRNG. Its state is recoverable from a modest number of outputs.

The KDoc immediately above (`:339-345`) states the threat exactly: *"The LAN/hotspot inbound is
bound to 0.0.0.0, so it must NEVER be reachable without authentication (an open relay)."* The
credential protecting that open relay has 48 bits of *predictable* material.

**Fix.** `java.security.SecureRandom` (or `kotlin.random.Random.Default` replaced with
`SecureRandom().nextBytes`), and widen the password to 16+ hex chars.

---

## 17. Medium — the subscription URL is logged

```kotlin
// handler/AngConfigManager.kt:789
LogUtil.i(AppConfig.TAG, url)
```

`url` is the full subscription URL, which for Remnawave contains the account's secret
subscription token. Two other files in this layer state the opposite policy:
`auth/AuthTokenStore.kt:19` — *"Tokens and subscription URLs are never logged"* — and
`auth/DepartamentApiClientImpl.kt:56-57` — *"tokens and subscription URLs are never logged"*.

The default log level is `warning` (`util/LogUtil.kt:10`), and `isEnabled` gates INFO out
(`:51-53`), so this is dormant by default. But `PREF_LOGLEVEL` is user-settable
(`AppConfig.kt:75`, surfaced through `MmkvPreferenceDataStore`), and `LogcatActivity` reads
logcat back for the user to copy/share — a support request with an attached log hands over the
VPN account.

`ui/UrlSchemeActivity.kt:176,184` logs the incoming deep-link URL for the same reason and with
the same exposure.

**Fix.** Log a redacted form — scheme + host + `/…` — never the path/query.

---

## 18. Medium — socket and list-entry leak on every failed TCP ping

```kotlin
// handler/SpeedtestManager.kt:125-147
fun socketConnectTime(url: String, port: Int): Long {
    try {
        val socket = Socket()
        synchronized(this) { tcpTestingSockets.add(socket) }       // :129
        val start = System.currentTimeMillis()
        socket.connect(InetSocketAddress(url, port), 3000)         // :132  throws on timeout/refusal
        val time = System.currentTimeMillis() - start
        synchronized(this) { tcpTestingSockets.remove(socket) }    // :135  never reached on failure
        socket.close()                                              // :137  never reached on failure
        return time
    } catch (e: UnknownHostException) { … } catch (e: IOException) { … }
```

On any failure the socket is neither closed nor removed from `tcpTestingSockets`. The list is
a process-lifetime `ArrayList` (`:18`) only ever drained by `closeAllTcpSockets()` (`:152-159`).
`tcping` runs the probe twice per call (`:104-116`), and real-ping fans out over up to 128
concurrent servers (`SettingsManager.getRealPingConcurrency:504-507`), so a user with a large
subscription of unreachable nodes accumulates hundreds of leaked file descriptors and list
entries per test round.

**Fix.** `try { … } finally { synchronized(this) { tcpTestingSockets.remove(socket) }; socket.closeQuietly() }`.

Adjacent, lower severity: `icmpPing` (`:87`) passes the profile-supplied host straight into
`ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", ip)`. No shell is involved
so there is no command injection, but a server address beginning with `-` is interpreted by
`ping` as a flag (argument injection). Prefix with `--` or validate the host first.

---

## 19. Medium — deleted subscriptions can resurrect

```kotlin
// handler/MmkvManager.kt:354-363
private fun initSubsList() {
    val subsList = decodeSubsList()
    if (subsList.isNotEmpty()) return
    subStorage.allKeys()?.forEach { key -> subsList.add(key) }
    encodeSubsList(subsList)
}
```

Called first thing in `decodeSubscriptions()` (`:371`). The invariant it assumes — "an empty
`KEY_SUB_IDS` means the index was never built" — is false in at least two reachable states:

* after `removeAllServer()` clears `mainStorage` (§14), `KEY_SUB_IDS` is gone but
  `subStorage` still holds every `SubscriptionItem`, so all of them are re-indexed;
* if a `removeSubscription` (`:389-396`) is interrupted between `subStorage.remove(subid)` at
  `:390` and `encodeSubsList` at `:393`, or if the cross-process race in §10 loses the
  `encodeSubsList` write, a deleted subscription's entry survives in `subStorage` and comes
  back the next time the list happens to be empty.

Order is also lost: `allKeys()` has no defined ordering, so the user's arranged subscription
order (maintained by `swapSubscriptions`, `SettingsManager.kt:234-239`) is randomised.

**Fix.** Use an explicit "index built" flag rather than emptiness, and delete from
`subStorage` **after** the index write so a crash leaves an orphan index entry (harmless)
rather than an orphan record (resurrectable).

---

## 20. Medium — `ensureDefaultSubscription` shuffles the user's subscription order

```kotlin
// handler/SettingsManager.kt:710-723
private fun ensureDefaultSubscription() {
    if (decodeSubscription(DEFAULT_SUBSCRIPTION_ID) == null) {
        encodeSubscription(DEFAULT_SUBSCRIPTION_ID, SubscriptionItem(remarks = "Default"))
        // Move top
        val subsList = decodeSubsList()
        if (subsList.count() > 1) {
            swapSubscriptions(0, subsList.count() - 1)   // :720
        }
    }
}
```

`encodeSubscription` **appends** the new id (`handler/MmkvManager.kt:408-412`), so DEFAULT is
last. The intent ("Move top") is to bring it to index 0. `swapSubscriptions(0, last)` swaps
the two ends: for `[A, B, DEFAULT]` the result is `[DEFAULT, B, A]` — DEFAULT is at the top as
intended, but **A and B have traded places**. With four or more subscriptions the corruption is
worse. A rotation, not a swap, is what's wanted.

**Fix.** `subsList.add(0, subsList.removeAt(subsList.lastIndex))` and persist, or have
`encodeSubscription` accept an insert position.

---

## 21. Medium — the in-app updater points at upstream's repository

```kotlin
// AppConfig.kt:128-129
const val APP_URL = "$GITHUB_URL/2dust/v2rayNG"
const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"
```

`UpdateCheckerManager.checkForUpdate` fetches that URL (`:18-22`) and
`getDownloadUrl` returns `asset.browserDownloadUrl` from an upstream release (`:103`), which
`CheckUpdateActivity` offers to the user (`ui/CheckUpdateActivity.kt:72`).

This app is `departament` (`versionName = "2.2.1"`, `app/build.gradle.kts:16-17`, output name
`departament_${versionName}_${abi}.apk` at `:126`). Upstream v2rayNG is at 1.x, so
`compareVersions` (`:78-88`) will normally report "no update" — but the moment upstream crosses
2.2.1 the app will tell departament users to download and install a **different app signed by a
different key**. It is also a supply-chain dependency on a third party's release feed for a
security product.

Secondary: `compareVersions` calls `v1[i].toInt()` (`:83`) with no `toIntOrNull`, so any
non-numeric tag segment (`"1.10.0-beta"` → `"0-beta"`) throws `NumberFormatException`. It is
absorbed by `CheckUpdateActivity.kt:58`'s `catch (e: Exception)`, so this degrades to "update
check silently fails" rather than a crash.

**Fix.** Point `APP_API_URL`/`APP_URL` at the departament release feed, or disable the updater
for this fork. Use `toIntOrNull() ?: 0` in `compareVersions`.

---

## 22. Low-Medium — unbounded avatar cache and unbounded remote avatar read

```kotlin
// util/AvatarManager.kt:51
private val memory = HashMap<String, Bitmap>()
```

Never evicted, never size-capped. Keys are `custom:<lastModified>` (a new key each time the
user changes their photo — `:65,98`) and every distinct `avatarUrl` (`:130`). Bitmaps are
capped at 512 px (`:39`, `sampleSizeFor:225-235`), i.e. up to ~1 MB each in ARGB_8888. It is a
plain `HashMap` mutated from `saveCustomAvatar` (`:65`, callable from any thread) and from the
coroutine continuations at `:108,130` — no synchronisation.

```kotlin
// util/AvatarManager.kt:186-189
http.newCall(req).execute().use { resp ->
    if (!resp.isSuccessful) return null
    val bytes = resp.body?.bytes() ?: return null   // unbounded
```

`url` comes from `profile.avatarUrl` (`:119`), i.e. from the backend — and the client has no
`callTimeout` (`:43-48`). Under §2's MITM, this is a direct OOM.

**Fix.** Replace `memory` with an `LruCache` sized from
`ActivityManager.getMemoryClass()`. Cap the avatar body (a few hundred KB) and add
`callTimeout`.

---

## 23. Low-Medium — `ProfileItem.equals` without `hashCode`, and an unchecked cast

```kotlin
// dto/entities/ProfileItem.kt:91-93
override fun equals(other: Any?): Boolean {
    if (other == null) return false
    val obj = other as ProfileItem     // ClassCastException for any non-ProfileItem
```

`ProfileItem` is a `data class`, so the compiler still generates `hashCode()` from *all*
properties — including `remarks`, `description`, `addedTime`, `subscriptionId`, which the
hand-written `equals` deliberately ignores. Two "equal" profiles therefore have different hash
codes, breaking the `Set`/`Map`/`distinct()` contract.

`AngConfigManager.kt:279-281` documents the hazard and works around it with a linear scan —
which is correct — but the trap remains for the next person, and `other as ProfileItem` will
throw rather than return `false` if a `ProfileItem` is ever compared with anything else
(e.g. inside a heterogeneous list or a `DiffUtil` callback).

**Fix.** `if (other !is ProfileItem) return false`, and override `hashCode()` over exactly the
same fields `equals` compares.

---

## 24. Low-Medium — device identity forwarded to redirect targets

```kotlin
// util/HttpUtil.kt:306-312
private fun attachDeviceHeaders(request: UrlContentRequest, requestBuilder: Request.Builder) {
    val hwid = request.hwid?.takeIf { it.isNotBlank() } ?: return
    requestBuilder.addHeader(AppConfig.HEADER_HWID, hwid)
    requestBuilder.addHeader(AppConfig.HEADER_DEVICE_OS, "android")
    requestBuilder.addHeader(AppConfig.HEADER_VER_OS, Build.VERSION.RELEASE ?: "")
    requestBuilder.addHeader(AppConfig.HEADER_DEVICE_MODEL, Utils.getDeviceName())
}
```

Called inside the redirect loop (`:187,256`), so the headers are re-attached to whatever host
the `Location` header names — with no same-origin check (see §2 on `resolveLocation`). A
subscription server can 302 the client to any third party and leak the stable HWID
(`AuthTokenStore.deviceId()` = MD5 of `ANDROID_ID`, `auth/AuthTokenStore.kt:73-93`) plus the
exact device model. That HWID is designed to be stable across reinstall (`:56-63`), so it is a
durable tracking identifier.

By contrast, `applyEmbeddedBasicAuthHeader` (`:314-326`) correctly re-derives credentials from
the *current* URL each iteration, so Basic-auth creds are not carried across hosts. The device
headers should follow the same rule.

**Fix.** Only attach device headers when the request host matches the originally-configured
subscription host.

---

## 25. Low-Medium — sync clobbers the user's subscription name

```kotlin
// auth/SubscriptionSyncManager.kt:45-53
val item = (MmkvManager.decodeSubscription(guid) ?: SubscriptionItem()).apply {
    remarks = info.displayName?.ifBlank { null }
        ?: info.tariffDisplayName?.ifBlank { null }
        ?: "Departament VPN"
    …
```

`remarks` is unconditionally overwritten on every sync. `AngConfigManager.updateConfigViaSub`
goes to real trouble to *avoid* exactly this — it adopts the provider title only while the
subscription is still unnamed (`:864-873`, *"a name the user typed in SubEditActivity must
never be clobbered"*). The sync path ignores that contract.

Same block also forces `enabled = true` and `autoUpdate = true` (`:49-50`), re-enabling a
subscription the user deliberately disabled.

**Fix.** Only set `remarks` when the current value is blank or the generic placeholder, mirroring
`AngConfigManager.kt:864-873`. Leave `enabled` alone on subsequent syncs.

---

## 26. Low — assorted parsing and robustness defects

**26a. WireGuard IPv6 endpoints are parsed wrong.**
```kotlin
// fmt/WireguardFmt.kt:86-94
val endpoint = peerParams["endpoint"].orEmpty()
val endpointParts = endpoint.split(":", limit = 2)
if (endpointParts.size == 2) { config.server = endpointParts[0]; config.serverPort = endpointParts[1] }
```
For `[2001:db8::1]:51820` this yields `server = "[2001"`, `serverPort = "db8::1]:51820"`.
Should split on the last `:` outside brackets. (`dto/V2rayConfig.kt:352,373` uses
`substringBeforeLast`/`substringAfterLast`, which is closer but still wrong for a bracketed
IPv6 host.)

**26b. Routing-ruleset index has no upper bound.**
`SettingsManager.getRoutingRuleset(index)` checks `index < 0` then does `rulesetList[index]`
(`:136-142`); `removeRoutingRuleset` checks `index < 0` then `rulesetList.removeAt(index)`
(`:169-177`). A stale index from the UI after a concurrent modification throws
`IndexOutOfBoundsException`. Add `index >= rulesetList.size` guards.

**26c. `toIdnUrl` replaces the host string globally.**
```kotlin
// util/HttpUtil.kt:58-67
val asciiHost = IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
if (host != asciiHost) return str.replace(host, asciiHost)
```
`String.replace` rewrites *every* occurrence, so a URL whose path or query repeats the host
name gets corrupted. Rebuild the URL from its components instead. `URL(str)` at `:59` also
throws `MalformedURLException` for an unknown scheme — absorbed by the caller's outer catch
(`AngConfigManager.kt:885-888`), which turns it into a generic `failureCount = 1`.

**26d. A user-supplied subscription filter regex is compiled per line, uncaught.**
```kotlin
// handler/AngConfigManager.kt:703-707
if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
    val matched = Regex(pattern = subItem?.filter.orEmpty()).containsMatchIn(input = config.remarks)
```
An invalid pattern throws `PatternSyntaxException` on every line; the per-line catch at
`:716-719` returns null each time, so the entire subscription silently imports **zero** servers
and reports a generic failure. Also: the `Regex` is recompiled for every line, and a
catastrophic-backtracking pattern will hang the import. Compile once outside the loop, wrap the
compile in `runCatching`, and surface "invalid filter" to the user.

**26e. `ApiGson`'s String adapter can throw a type Gson callers don't catch.**
```kotlin
// auth/ApiGson.kt:23-33 — registered for String::class.java globally
else -> reader.nextString()
```
For a JSON object or array where a `String` is expected, `nextString()` throws
`IllegalStateException`, not `JsonSyntaxException`. `DepartamentApiClientImpl.parse`
(`:370-376`) catches only `JsonSyntaxException`, so the error escapes as a generic exception
and is relabelled `ApiError.Network` by `AccountRepository.guard` (`:50-52`) — misleading
diagnostics for what is actually a parse failure. Catch `RuntimeException` in `parse`.

**26f. `KEY_EXPIRES_AT` is dead weight.** `AuthTokenStore.saveSession` accepts an `expiresAt`
(`:96`) but every caller omits it — `AccountSession.onAuthenticated:38`, `AuthManager:84,107,116`
— so `getExpiresAt()` (`:109`) always returns 0. Nothing anywhere checks local token expiry;
the app shows "logged in" with a dead 7-day JWT until a `getMe` 401 (`AccountRepository:71-73`).
Either populate it from the login response and check it at startup, or delete the field.

**26g. Telegram login aborts on a single network blip.**
`AuthManager.beginTelegramLogin` polls every 2 s for 3 minutes, but any `ApiError` — including
a transient `ApiError.Network` — terminates the flow (`:76-81`). One dropped packet during the
three-minute window ends the login. Only abort on `Expired`/`NotConfigured`; count consecutive
network errors and keep polling otherwise.

**26h. `Utils.decode` logs two full stack traces per non-base64 input.**
`tryDecodeBase64` logs at ERROR for both the standard and URL-safe attempt (`util/Utils.kt:124,129`),
and `Utils.decode` is called on the *entire* subscription body on every update
(`AngConfigManager.kt:229,900`). For link-list subscriptions — the common case — that is two
stack traces per refresh, per subscription, at the default log level. Downgrade to debug, or
pre-check the charset.

**26i. Blocking network on the WorkManager coroutine dispatcher.**
`SubscriptionUpdater.UpdateTask.doWork` (`handler/SubscriptionUpdater.kt:154-191`) calls
`AngConfigManager.updateConfigViaSub` (`:185`) directly. `CoroutineWorker` runs `doWork` on
`Dispatchers.Default` by default, and `updateConfigViaSub` performs two blocking OkHttp calls
with 15 s timeouts (`AngConfigManager.kt:801-831`). Wrap in `withContext(Dispatchers.IO)`.

**26j. The subscription-fetch retry drops the timeout.**
The proxied attempt passes `timeout = 15000` explicitly (`AngConfigManager.kt:808`); the
direct retry omits it (`:820-826`) and relies on `UrlContentRequest`'s default
(`dto/UrlContentRequest.kt:5`, also 15000). Harmless today, but the two will drift.

---

## What I checked and found clean

* `util/JsonUtil.kt` — `fromJsonSafe`/`parseString` correctly catch and return null
  (`:46-53,85-94`). The unsafe `fromJson` (`:34-36`) is the one that propagates, and every
  problematic call site is listed above.
* `util/MessageUtil.kt:84` — `intent.package = ANG_PACKAGE` correctly scopes outgoing
  broadcasts (the *receiving* side is the problem, §8).
* `template/TemplateCrypto.kt` — AES-256/GCM, Keystore-backed, random IV per encryption
  (`:71`), IV prepended and length-checked on decrypt (`:90-92`). Correct.
  The KDoc is honest that this is concealment, not DRM (`:18-26`).
* `template/TemplateManager.kt` — prefix-based storage wrapping is unambiguous and the
  non-locked path is byte-identical (`:116-138`). The `#profile-hidden:` body scan is bounded
  to 24 lines (`:33,92`).
* `util/SubscriptionUserInfo.kt:21-38` — fully defensive header parsing, `toLongOrNull`
  throughout, returns null on nothing usable.
* `auth/AccountCache.kt` — `@Synchronized` throughout, monotonic `elapsedRealtime` timestamps
  (the KDoc at `:13-14` explains why), and session-tied invalidation (`:47-50`). Correct.
  Only nit: entries are evicted lazily on read, so the map has no hard bound.
* `auth/AccountRepository.kt` — the 401-only-from-`getMe` wipe policy (`:66-82`) and the
  `CancellationException` rethrow (`:46-49,76-78`) are both right, and the reasoning is
  documented.
* `auth/DepartamentApiClientImpl.mapError:338-349` — correctly refuses to treat 403 as
  `Unauthorized`; `sanitizeBody:356-368` strips token/authorization/URL lines before surfacing
  a diagnostic.
* `handler/SettingsManager.getVpnDnsServers:465-478` — the empty-list safeguard is correct and
  well-reasoned.

---

## Recommended order of work

1. §1 — flip `append` to `true` on the three deep-link/QR call sites; gate `depv://` actions
   behind confirmation. One-line changes, removes a critical remote primitive.
2. §2 — drop `<certificates src="user" />`, scope cleartext to loopback, add scheme-downgrade
   check in `resolveLocation`. Config + 3 lines.
3. §3 + §5 — move the delete after the parse loop; give `V2rayConfig` collection defaults;
   make `CustomFmt.parse` nullable. Stops the "update failed and now I have no servers" class.
4. §4 — use `raw()`, add the primary-subscription fallback, don't delete on partial results.
5. §6, §7, §8 — Zip Slip guard, `MULTI_PROCESS_MODE` on the auth store,
   `RECEIVER_NOT_EXPORTED`.
6. §9-§12 — guard hostnames, serialise list writes, single-process migrations, timeouts +
   body caps.
7. The rest as cleanup.
