# Module 9 — Android TV support + QR Wi-Fi subscription transfer (implementation report)

Implements `docs/smart-tv-transfer-design.md` for the departament VPN fork
(v2rayNG/Xray, Kotlin, `com.v2ray.ang`). The change set is deliberately
**self-contained and additive** (mostly new files in a new `com.v2ray.ang.tv`
package) to avoid conflicts with the in-parallel UI refactor. No existing
Activity/ViewModel/adapter/layout was modified.

## Files added

New Kotlin package `com.v2ray.ang.tv`
(`app/src/main/java/com/v2ray/ang/tv/`):

- `TvPairingProtocol.kt` — the rendezvous protocol. Generates the 128-bit
  one-time token, builds/parses the `dvpntv://v1?ip&port&token` QR payload, and
  builds/parses the JSON request/response bodies. QR carries **no secret**.
- `TvNetworkUtils.kt` — finds the device's site-local IPv4 (prefers `wlan*`) and
  classifies remote peers as LAN-local.
- `TvHttpReceiver.kt` — the TV-side listener. A raw `ServerSocket` (no framework)
  bound to `0.0.0.0` on an **ephemeral** port. Accepts exactly one token-authorized
  `POST /pair`, then stops. Enforces TTL, LAN-only peers, single-use, and bad-token
  lockout. Runs on a daemon thread.
- `TvReceiveActivity.kt` — TV "Receive subscription" screen. Mints a token, starts
  the listener, renders the QR via the existing `QRCodeDecoder.createQRCode`, runs a
  TTL countdown, and on a valid POST imports through the **existing** subscription
  plumbing. Listener is tied strictly to `onStart`/`onStop`.
- `TvSendActivity.kt` — phone "Send to TV" screen. Lists existing subscriptions
  (`MmkvManager.decodeSubscriptions`), scans the TV QR via the existing
  `QRCodeScannerHelper`/`ScannerActivity`, and POSTs the selected subscription URL to
  the TV with OkHttp (already a project dependency), authorized by the QR token.

New resources:

- `app/src/main/res/layout/activity_tv_receive.xml` — QR + status + regenerate
  button; overscan-safe outer padding (~5%: 48dp horizontal, 27dp vertical).
- `app/src/main/res/layout/activity_tv_send.xml` — pick-subscription + scan flow.
- `app/src/main/res/values/strings_tv.xml` — **new file** for all TV strings (kept
  separate from `strings.xml` to avoid merge collisions).

Report:

- `docs/impl-module9-tv-report.md` (this file).

## Manifest changes (`app/src/main/AndroidManifest.xml`)

- **Added** two activity declarations (only additions — no existing element touched):
  - `.tv.TvReceiveActivity` — `exported="false"`, `screenOrientation="landscape"`.
  - `.tv.TvSendActivity` — `exported="false"`.
- **TV enablement was already present** and verified, so it was intentionally left
  unchanged (re-adding would duplicate):
  - `MainActivity` already declares the `CATEGORY_LEANBACK_LAUNCHER` intent-filter.
  - `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`
    already present.
  - `<uses-feature android:name="android.software.leanback" android:required="false" />`
    already present.
  - `<application android:banner="@mipmap/ic_banner" ...>` already present (real
    xhdpi asset `mipmap-xhdpi/ic_banner.png` exists).
  - Camera is already `required="false"`; the TV side never touches the camera.

## Import entry point wired

The TV terminates in the **existing** subscription subsystem — no new import/parse/
storage logic. On a validated POST, `TvReceiveActivity.handleImport` calls:

```
AngConfigManager.importBatchConfig(url, subid = "", append = true)
```

This is the exact path used by the scan/clipboard flows
(`MainActivity.importBatchConfig` → `AngConfigManager.importBatchConfig`, handler
line ~178). For a subscription URL it detects the sub via `parseBatchSubscription`
→ `importUrlAsSubscription` (stores a `SubscriptionItem` via
`MmkvManager.encodeSubscription`) and triggers `updateConfigViaSubAll()` to fetch
servers. Outcome mapping: `count>0 || countSub>0` → `200 {"imported":n}`; valid sub
URL but nothing imported (dedupe) → `409 {"error":"duplicate"}`; invalid URL →
`400`; exception → `500`.

## Security notes

- **No secret in the QR.** The QR carries only `ip:port` + a one-time token. The
  secret subscription URL travels only over the direct LAN socket in the POST body
  (defeats shoulder-surfing / screenshot of the TV screen — T1).
- **One-time token, short TTL.** 128-bit `SecureRandom` token, base64url; TTL 120s
  (`TvPairingProtocol.TOKEN_TTL_MILLIS`). Single-use: the listener marks the request
  consumed before import and stops after one success. Constant-time token compare
  (`MessageDigest.isEqual`). Bad-token lockout after 5 attempts (defeats malicious
  LAN push — T2).
- **LAN-only.** The listener rejects non-LAN peers (`TvNetworkUtils.isLanAddress`,
  site/link-local/loopback only) and is ephemeral — it exists only while the Receive
  screen is open and dies after one import / TTL / screen exit (limits off-LAN
  exposure — T3). No secrets are logged.
- **AP client isolation caveat (T4).** Many routers / guest Wi-Fi block
  client-to-client traffic and multi-AP meshes may put phone and TV on different
  subnets, so the encoded IP is unreachable. This is a **physical limitation, not
  fixable in-app**: the phone detects it via connect timeout and shows a clear
  message (`tv_send_unreachable`) telling the user both devices must be on the same
  Wi-Fi without client isolation.
- **Transport (v1 = plain HTTP + token).** Per the design's MVP option, v1 ships
  plain HTTP guarded by the mandatory one-time token. The token stops unauthorized
  *push*, but a passive same-LAN sniffer could read the sub URL on the wire. The
  design's recommended hardening (HTTPS with a QR-pinned self-signed fingerprint, or
  token-derived AEAD of the body) is a documented follow-up; the `dvpntv` URI already
  reserves an optional `fp` fingerprint field for it. The app manifest already sets
  `usesCleartextTraffic="true"`, so no manifest change was required for the MVP.

## Build / compatibility notes

- No new Gradle dependencies: reuses ZXing (`QRCodeDecoder`), OkHttp, Quickie
  scanner, and kotlinx-coroutines already in the project.
- `minSdk 24` compatible (no APIs above 24 used; `Char.code`, `NetworkInterface`,
  `SecureRandom`, `MessageDigest.isEqual` all fine).
- All four touched/added XML files validated with `xmllint --noout`; every
  referenced string/id/drawable was verified to exist.
- `QRCodeScannerHelper` is created in `onCreate` (before STARTED) to satisfy the
  `registerForActivityResult` lifecycle requirement.

## Not included (per scope / design phase boundaries)

- Focus/overscan polish of the *existing* phone UI (Phase A items 2–4 in the design)
  was intentionally **not** done here — it would require editing MainActivity /
  activity_main / adapters, which the task explicitly forbids to avoid clashing with
  the parallel UI refactor. Entry points to `TvReceiveActivity` (TV) and
  `TvSendActivity` (phone) should be wired from the menus during that UI work.
- HTTPS/AEAD transport hardening (design §3.5) is left as the documented follow-up.
