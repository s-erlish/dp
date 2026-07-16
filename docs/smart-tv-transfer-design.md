# Smart TV / Android TV support + QR Wi‑Fi subscription transfer — Design

App: **departament VPN** (v2rayNG / Xray fork, Kotlin), at `/home/user/dp/V2rayNG`.
Status: design only. No code changes are made by this document.

---

## 0. Executive summary

Two features:

1. **Android TV / Smart TV support** — make the existing phone app installable and fully
   D‑pad operable on Android TV / Google TV / Fire TV, with overscan‑safe, landscape,
   focus‑navigable layouts. The app is **already 90% TV‑declared** in the manifest
   (LEANBACK_LAUNCHER category, banner, `leanback`/`touchscreen` `required="false"`).
   Recommendation: **reuse the phone UI adapted for focus**, not a separate Leanback UI.

2. **QR Wi‑Fi subscription transfer** — the TV shows a QR code; the user scans it with the
   phone app; the phone then **POSTs the selected subscription URL to a tiny HTTP listener
   running on the TV** over the LAN. The QR carries only `IP:port + one‑time token` (never
   the secret subscription URL). The TV imports the received URL through the **existing
   `AngConfigManager` subscription plumbing**.

---

## 1. Relevant existing code (verified in this repo)

| Concern | Location | Notes |
|---|---|---|
| QR **encoding** | `app/.../util/QRCodeDecoder.kt` → `createQRCode(text, size=800): Bitmap?` | Uses ZXing `QRCodeWriter`. |
| QR **decoding (file)** | same file → `syncDecodeQRCode(...)` | ZXing `QRCodeReader`. |
| QR **scanning (camera)** | `app/.../ui/ScannerActivity.kt` | Uses `io.github.g00fy2.quickie` (CameraX + ML Kit). Returns `SCAN_RESULT` extra. |
| Scan launcher wrapper | `app/.../helper/QRCodeScannerHelper.kt` | `launch { result -> }` ergonomic wrapper around `ScannerActivity`. |
| QR **lib dependency** | `gradle/libs.versions.toml:55` → `com.google.zxing:core` | Encoder is ZXing; camera scan is Quickie/ML Kit. |
| Subscription import (batch) | `app/.../handler/AngConfigManager.kt:178` → `importBatchConfig(server, subid, append): Pair<Int,Int>` | Accepts raw configs **and** subscription URLs; auto‑calls `updateConfigViaSubAll()`. |
| Add a sub URL | `AngConfigManager.kt:643` → `importUrlAsSubscription(url)` (private) | Dedupes by URL, stores `SubscriptionItem(remarks,url)` via `MmkvManager.encodeSubscription`. |
| Batch sub parse | `AngConfigManager.parseBatchSubscription()` | Splits lines, validates `Utils.isValidSubUrl`, imports each. |
| Scan→import glue | `app/.../ui/MainActivity.kt:585,606` → `importBatchConfig(scanResult)` | Existing entry point that feeds scanned text into `AngConfigManager.importBatchConfig`. |
| Sub add/edit screen | `app/.../ui/SubEditActivity.kt` | `SubscriptionItem(remarks,url)`, validation via `SubscriptionGuard`, `Utils.isValidSubUrl`. |
| Share‑as‑QR (existing) | `app/.../ui/SubSettingActivity.kt:144` | Already renders a URL to a QR in `ItemQrcodeBinding` — reuse pattern for the TV pairing QR. |
| Navigation | `MainActivity.kt` — `BottomNavigationView` (`binding.bottomNav`) + `NavigationView` drawer | Home / Servers / More(drawer). This is the surface that must become D‑pad friendly. |

**Consequence:** both the QR encoder and the subscription import path already exist. The TV
feature is mostly *wiring* (a listener + a QR + a POST), not new subsystems.

---

## 2. Android TV support

### 2.1 Manifest — current vs required

The manifest (`app/src/main/AndroidManifest.xml`) **already satisfies** the Google Play TV
requirements:

```xml
<uses-feature android:name="android.software.leanback"  android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.camera"     android:required="false" />
...
<application android:banner="@mipmap/ic_banner" ...>
  <activity android:name=".ui.MainActivity" ...>
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
      <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
```

Against the official checklist this is correct:

- A TV app **must** declare a launcher activity with a `CATEGORY_LEANBACK_LAUNCHER`
  intent filter — this is what marks the app as TV‑enabled on Google Play. ✅ present.
  ([Get started with TV apps](https://developer.android.com/training/tv/start/start),
  [TV navigation](https://developer.android.com/training/tv/get-started/navigation))
- Touchscreen **must** be declared not required or the app cannot ship to TV. ✅ present.
- `android.software.leanback` should be declared (`required="false"` so phones still
  install the same APK). ✅ present.
- A **banner** (320×180 xhdpi) is required per‑localization when a Leanback filter is
  present; it is the launch point on the TV home rows. ✅ `android:banner` present — but
  **verify `ic_banner` is a real 320×180 asset with the app name baked in**, not a
  placeholder. ([Get started with TV apps](https://developer.android.com/training/tv/start/start))

**Camera caveat (action item):** the app requests `android.hardware.camera` `required=false`
(good), but the *phone‑side* scanner needs a camera. On the **TV side** there is no camera and
none is needed (the TV *shows* the QR, it never scans). Ensure no code path forces camera
permission at launch on TV. The manifest is already correct here.

**Optional polish:** add a distinct `<activity-alias>` or Google‑TV `<meta-data>` only if a
separate TV entry activity is later desired; not required for launch.

### 2.2 Reuse phone UI (focus‑adapted) vs dedicated Leanback UI — recommendation

**Recommendation: reuse the existing phone UI, adapted for focus. Do NOT build a Leanback/TV
UI fork.** Rationale:

- The Leanback UI toolkit is **deprecated**; Google now steers new TV UI toward
  **Compose for TV**. Adopting Leanback now would be building on a dead library, and the app
  is currently View/XML + `BottomNavigationView`, not Compose — a Leanback rewrite is a large,
  low‑value effort. ([Leanback libraries](https://developer.android.com/training/tv/playback/leanback/leanback-libraries))
- The app is a *utility* (connect button + server/subscription lists), not a
  media‑browse "10‑foot content" app that Leanback's `BrowseFragment` is designed for. The
  core requirement for a utility app is simply: **every visible control must be reachable and
  operable with the D‑pad**. ([TV navigation](https://developer.android.com/training/tv/get-started/navigation))
- Maintaining one codebase avoids feature drift between phone and TV.

So: keep `MainActivity` + `BottomNavigationView` + `RecyclerView` lists, and invest in
**focus correctness + overscan margins + landscape** rather than a parallel UI.

### 2.3 D‑pad navigation of the bottom‑nav app

The Android framework auto‑derives directional navigation from the relative on‑screen
position of focusable views; you then test with a D‑pad and add **explicit** overrides where
the auto scheme is wrong. ([Building layouts for TV](https://developer.android.com/training/tv/start/layouts),
[TV navigation](https://developer.android.com/training/tv/get-started/navigation))

Concrete work for this app:

1. **`BottomNavigationView` focus.** Material `BottomNavigationView` is touch‑oriented; its
   items are not reliably D‑pad focusable/traversable out of the box. On TV:
   - Ensure the bar and its items are `android:focusable="true"` and reachable.
   - Provide explicit `nextFocusUp`/`nextFocusDown` between the content area (`groupHome` /
     `groupServers` lists) and the nav bar so pressing *down* from the list lands on the tab
     strip and *up* returns to content.
   - Because the "More" tab opens a `NavigationView` **drawer** (`GravityCompat.START`),
     ensure the drawer, once open, grabs focus and that `BACK`/left closes it and restores
     focus to the "More" tab. Consider, on TV form factor, **moving secondary items out of
     the edge‑swipe drawer** (edge‑swipe is meaningless without touch) into an on‑screen,
     focusable list — a left **nav rail** reads better on TV than a hidden drawer.
   - If tab focus proves unreliable, the low‑risk fallback is a thin TV‑only layout variant
     (`res/layout-television/` or a `-television` / `sw600dp`+leanback qualifier) that
     replaces the bottom bar with a focusable vertical rail, while keeping the same
     Activities/Fragments/ViewModels.
2. **Lists.** `RecyclerView` server/subscription lists are naturally D‑pad traversable;
   confirm each row is focusable, shows a **visible focus highlight** (TV needs a strong
   focused state — background/scale/elevation), and that row action buttons (share, edit,
   delete, QR) are individually reachable.
3. **Initial focus.** Set a sensible default focus (the Connect button on Home) on each
   screen so the user isn't stranded with nothing highlighted.
4. **Dialogs.** The share `AlertDialog` and the new "Receive on TV" dialog must have
   focusable buttons and a default focus.
5. **No touch‑only affordances:** anything reachable only by long‑press, swipe, or a
   touch‑target with no focus state needs a D‑pad equivalent.

### 2.4 Responsive / overscan‑safe / landscape

- **Overscan:** TVs may crop edges. Add a **5% margin** (~27dp top/bottom, ~48dp left/right)
  — or the historically cited 10% — as an outer padding on root TV layouts so no control is
  clipped. Do **not** add these margins on top of Leanback browse widgets (N/A here since we
  are not using them). ([Building layouts for TV](https://developer.android.com/training/tv/start/layouts))
  Implement via a TV‑qualified dimens/style so phones are unaffected.
- **Any resolution:** use `dp`, `match_parent`/`0dp`+weights/`ConstraintLayout`,
  `wrap_content`, and avoid hard‑coded pixel sizes so 720p/1080p/4K all scale. Provide
  `layout-land` where a portrait‑biased screen exists.
- **Landscape:** TV is always landscape. Verify Home (connect hero) and the lists look right
  in landscape; the current phone portrait bias for Home may need a `layout-land`/TV variant
  (e.g., hero on the left, status on the right).
- **Density/text:** ensure text is legible at 10‑foot distance (bump base text sizes in the
  TV style), and focus highlights are high‑contrast.

### 2.5 TV scope of the transfer feature

On TV, the **camera path is unavailable and unnecessary**. The TV is the **display + receiver**:
it renders the pairing QR and runs the LAN listener. The **phone** is the **scanner + sender**.
The phone side already has the camera scanner (`ScannerActivity`/`QRCodeScannerHelper`).

---

## 3. QR Wi‑Fi subscription transfer

### 3.1 Goal & threat model

Move a **subscription URL** (a **secret** — it authorizes fetching the user's server list)
from an already‑signed‑in phone to a fresh TV on the **same Wi‑Fi**, with minimal typing
(TVs have painful on‑screen keyboards) and without leaking the secret.

Threats to defend against:
- **T1 Shoulder‑surfing / screenshot of the QR** — anyone who can see the TV screen sees the QR.
  ⇒ **The QR must not contain the subscription URL.**
- **T2 Malicious LAN peer pushing configs** — another device on the same Wi‑Fi POSTing a
  hostile subscription to the TV. ⇒ **one‑time token + short TTL + single‑use listener.**
- **T3 Off‑LAN / internet exposure** — listener reachable beyond the LAN. ⇒ **bind LAN‑only,
  no port forwarding, ephemeral lifetime.**
- **T4 AP client isolation / different subnets** — transfer simply can't work; must be
  detected and explained, not fail silently.

### 3.2 Recommended mechanism: **TV hosts a one‑shot HTTP listener; phone POSTs the sub**

```
        TV (receiver)                                Phone (sender, already signed in)
  ┌───────────────────────────┐                ┌────────────────────────────────────┐
  │ "Receive subscription"    │                │ Sub list → ⋮ → "Send to TV"          │
  │ 1. pick free ephemeral    │                │                                     │
  │    port P on wlan0        │                │ 1. scan QR (ScannerActivity)        │
  │ 2. mint token T (128-bit),│                │ 2. parse dvpntv://v1?ip&port&token  │
  │    TTL 120s, single use   │   QR (LAN      │ 3. POST http://IP:P/pair            │
  │ 3. start listener on      │    only, no    │      Authorization: Bearer <T>      │
  │    0.0.0.0:P (LAN)        │────secret)────▶│      body: {url, remarks}           │
  │ 4. render QR:             │                │ 4. show TV's 200/err response       │
  │    IP:P + T (+ fp)        │                │                                     │
  │ 5. on valid POST:         │◀───POST────────│                                     │
  │    verify T, import via   │    sub url     │                                     │
  │    AngConfigManager,      │                │                                     │
  │    return result, STOP    │                │                                     │
  └───────────────────────────┘                └────────────────────────────────────┘
```

**Why TV‑hosts‑and‑phone‑pushes is the right topology:**

- The QR only needs to carry **non‑secret rendezvous data** (`IP:port:token`) — safe to
  display on a big screen (defeats T1). The secret sub URL travels only over the direct
  LAN socket, never on screen.
- The phone is the trusted, authenticated party (user is signed in there); it *chooses* what
  to send and *initiates* the connection. The TV only accepts a single, token‑authorized POST.
- The token is generated on the TV, shown only via the QR, and required by the TV to accept
  the push — so only the person physically holding the phone in front of the TV (who scanned
  the QR) can push (defeats T2).
- The listener is **ephemeral and single‑use**: it exists only while the "Receive" screen is
  open, dies after one successful import or on TTL/back, minimizing exposure (mitigates T3).

**Payload (phone → TV), `POST /pair`:**
```
POST /pair HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{ "v": 1, "url": "<subscription URL>", "remarks": "<optional label>" }
```
TV response: `200 {"imported":1}` or `409 {"error":"duplicate"}` / `401` / `400`.

**TV import path — reuse existing plumbing:** on a valid POST the TV calls the *same* code the
scan/clipboard flows use:
- Preferred: `AngConfigManager.importBatchConfig(url, subId, append=true)` — it already
  detects a subscription URL via `parseBatchSubscription` → `importUrlAsSubscription`, stores
  the `SubscriptionItem`, and triggers `updateConfigViaSubAll()` to fetch servers. This is the
  exact behavior of pasting/scanning a sub link today (`MainActivity.importBatchConfig`,
  `AngConfigManager.kt:178`).
- If a custom remark is wanted, set it on the `SubscriptionItem` before encode (mirror
  `importUrlAsSubscription` at `AngConfigManager.kt:643`), or just pass the URL and let the
  existing dedupe/`SubscriptionGuard`/`isValidSubUrl` validation run unchanged.

No new import/parse/storage logic is introduced — the transfer feature terminates in the
existing subscription subsystem.

### 3.3 QR contents

Custom scheme, small, non‑secret:
```
dvpntv://v1?ip=192.168.1.42&port=48213&token=<base64url-128bit>[&fp=<cert-sha256-8bytes>]
```
- `ip`,`port`: the TV's `wlan0` address + chosen ephemeral port.
- `token`: 128‑bit random, base64url. One‑time, TTL ≤120s.
- `fp` (optional): first bytes of the self‑signed cert fingerprint if HTTPS is used (§3.5).

**Generation on TV:** reuse `QRCodeDecoder.createQRCode(text, size)` (ZXing encoder already in
the app) and display it in an `ImageView`, exactly like the existing share‑as‑QR dialog in
`SubSettingActivity` (`ItemQrcodeBinding`). No new dependency.

**Scanning on phone:** reuse `QRCodeScannerHelper.launch { scanResult -> }` →
`ScannerActivity` (Quickie/ML Kit camera). Add a branch: if `scanResult` starts with
`dvpntv://` treat it as a TV‑pair intent (parse + POST) instead of feeding it to
`importBatchConfig`. The phone is the one *sending*, so it reads its own currently‑selected
subscription URL from `MmkvManager`/`SubscriptionsViewModel` and puts it in the POST body.

### 3.4 Discovering the IP — put it in the QR (don't rely on mDNS)

Recommendation: **encode `IP:port` directly in the QR** (above). The TV knows its own LAN IP
(`WifiManager`/`ConnectivityManager` → link address on `wlan0`); baking it into the QR is
simplest and most robust.

**Alternative considered — NSD/mDNS discovery:** the TV could register an
`_dvpn._tcp` service via `NsdManager` (`registerService`, `PROTOCOL_DNS_SD`) and the phone
discover+resolve it. ([Use network service discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd),
[NsdManager](https://developer.android.com/reference/android/net/nsd/NsdManager)).
- Pros: no IP in the QR; survives DHCP changes; the QR could carry just the token + service
  instance name.
- Cons: **multicast/mDNS is frequently blocked** on consumer APs and guest networks, NSD
  resolve is flaky across OEMs, and it adds moving parts. Since the user is already pointing a
  camera at the TV, the IP is right there to encode — mDNS's main benefit (discovery without
  prior knowledge) is not needed.
- **Verdict:** IP‑in‑QR primary; NSD as an *optional* fallback if the encoded IP is
  unreachable (e.g., multi‑AP roaming changed the address).

### 3.5 Transport security: plain HTTP + token vs HTTPS self‑signed

The payload is confidential (the sub URL). Options:

| Option | Confidentiality on wire | Complexity | Verdict |
|---|---|---|---|
| **Plain HTTP + bearer token** | ❌ sub URL visible to a LAN sniffer / rogue AP | Low | Acceptable **only** as v1 MVP on a trusted home LAN; token still stops unauthorized *push* (T2) but not passive read. |
| **HTTPS, self‑signed cert, pinned via QR fingerprint** | ✅ encrypted; MITM‑resistant because phone pins `fp` from the QR | Medium | **Recommended.** TV generates an ephemeral self‑signed cert at listener start, puts its SHA‑256 (`fp`) in the QR, phone pins exactly that cert (ignores the normal CA chain). |
| Token‑derived key + app‑layer encryption (e.g., encrypt body with a key derived from the token) | ✅ | Medium | Good middle ground if adding TLS to the tiny server is awkward — the 128‑bit token doubles as a shared secret to AEAD‑encrypt the JSON body. |

**Recommendation:** ship **HTTPS self‑signed with QR‑pinned fingerprint** for confidentiality
+ integrity. If that's too heavy for v1, ship **plain HTTP + token** but keep the body small
and consider the **token‑as‑key AEAD** variant so the sub URL is never in cleartext. In all
cases the token is **mandatory**, **single‑use**, **TTL‑bound (≤120s)**, and the listener
**binds to the LAN interface only** and shuts down after one import / TTL / screen exit.

Note: the app manifest currently sets `android:usesCleartextTraffic="true"` and has a
`network_security_config` — a plain‑HTTP MVP works without config changes, but prefer HTTPS.

### 3.6 Listener implementation on TV

- A **minimal embedded HTTP server** (e.g., NanoHTTPD‑style ~1 file, or a raw
  `ServerSocket`/`SSLServerSocket` loop) started from the "Receive subscription" screen's
  lifecycle (`onStart`/`onStop`), **not** a long‑lived service. Bind to `0.0.0.0:P` but treat
  only same‑subnet clients as valid; reject anything without the exact token.
- **Single request contract:** accept exactly one authorized `POST /pair`; on success import
  + return result + stop the server. On TTL expiry, close and invalidate the token.
- Run off the main thread; show progress + result on the TV screen (D‑pad‑dismissable dialog).
- Rate‑limit / lock out after a few bad‑token attempts.

### 3.7 Alternatives compared (why not the others)

- **QR carries the subscription URL directly (QR‑only, no network).**
  ❌ Rejected: the sub URL is a **secret**; displaying it in a QR on a TV screen exposes it to
  anyone watching or screenshotting the screen (T1). Only acceptable for *non‑secret* configs;
  not for subscription links.
- **Phone hosts, TV polls/pulls.**
  The phone runs the listener; the QR (shown where? the phone would show it, but then the TV
  must scan — TVs have no camera). ❌ Rejected: TV can't scan a QR. Even if the TV typed a
  code, the phone hosting means the *TV* initiates and the phone must be discoverable — worse
  ergonomics and the same secret‑on‑wire concern, with no upside.
- **Pure NSD/mDNS (no IP in QR).** ⚠️ Viable but fragile on real Wi‑Fi (multicast blocked on
  guest/enterprise APs). Kept only as a fallback (§3.4).
- **Cloud relay / account sync.** Out of scope: requires backend, defeats the "local, no
  server" goal, and adds a data‑handling surface. The LAN push keeps the secret on the user's
  own network.

---

## 4. Implementation plan

**Phase A — TV enablement (UI/manifest), no transfer yet**
1. Audit/verify `ic_banner` is a real 320×180 TV banner; confirm no launch‑time camera
   requirement on TV. (Manifest itself already TV‑compliant.)
2. Add TV‑qualified resources: overscan margins (5% via TV dimens/style), landscape Home
   layout, larger focus‑highlight drawables, bigger 10‑foot text sizes.
3. Make `BottomNavigationView` + drawer D‑pad‑correct: focusable items, explicit
   `nextFocusUp/Down`, default focus per screen, drawer focus capture + BACK handling. If
   flaky, add a `-television` layout variant swapping the bottom bar for a focusable left rail
   (same Activities/ViewModels).
4. Make list rows + row actions (share/edit/delete/QR) individually focusable with visible
   focus state. Test end‑to‑end with a D‑pad / emulator TV image.

**Phase B — Transfer feature**
5. **TV "Receive subscription"** screen: pick ephemeral port, mint token+TTL, (self‑signed
   cert), start one‑shot listener, render `dvpntv://…` QR via `QRCodeDecoder.createQRCode`.
6. **Listener**: verify token, parse JSON, call
   `AngConfigManager.importBatchConfig(url, subId, append=true)` (reuse), return result, stop.
7. **Phone "Send to TV"** action on a subscription row: `QRCodeScannerHelper.launch{}`, detect
   `dvpntv://`, parse, read selected sub URL from storage, POST (pinned HTTPS or token),
   surface the TV's result.
8. Strings/i18n, error UX for AP isolation / unreachable IP (with NSD fallback attempt).
9. Security review: token entropy/TTL/single‑use, LAN‑only bind, cert pinning, no secret in
   QR, no secret in logs.

**Phase C — QA**
10. Test matrix: 1080p + 4K TV, Google TV + Fire TV, guest‑network AP isolation, dual‑band
    roaming (IP change), duplicate sub, expired token, wrong token.

---

## 5. Risks & caveats

- **AP client isolation / guest Wi‑Fi (T4):** many routers block client‑to‑client traffic;
  the POST will fail. Detect (connect timeout) and show a clear message ("phone and TV must be
  on the same Wi‑Fi without client isolation"), offer manual paste as fallback. This is a
  hard physical limitation, not fixable in‑app.
- **Different subnets / multi‑AP mesh:** the IP baked in the QR may be unreachable if the
  phone is on a different AP/subnet. Mitigate with NSD fallback + clear error.
- **LAN push abuse (T2):** without the token, any LAN device could push a hostile
  subscription. Token + TTL + single‑use + attempt lockout are **mandatory**; the listener
  must never accept an unauthenticated body.
- **Cleartext exposure (T3):** plain HTTP leaks the sub URL to a LAN sniffer / rogue AP.
  Prefer HTTPS‑pinned or token‑AEAD; if shipping plain HTTP MVP, document the trust
  assumption (home LAN) and gate behind an explicit user action.
- **Listener lifetime leaks:** a forgotten open port is an attack surface. Bind to LAN only,
  tie strictly to screen lifecycle, hard TTL, kill after one import.
- **Multicast unreliability:** don't hard‑depend on mDNS; IP‑in‑QR is primary.
- **Leanback deprecation:** avoid investing in the deprecated Leanback toolkit; the
  reuse‑phone‑UI approach sidesteps this. Future TV redesigns should target Compose for TV.
- **Banner/asset gaps:** shipping without a proper 320×180 banner fails Google Play TV review.

---

## Sources

- [Get started with TV apps — Android Developers](https://developer.android.com/training/tv/start/start)
- [Building layouts for TV (overscan, D‑pad focus) — Android Developers](https://developer.android.com/training/tv/start/layouts)
- [TV navigation (D‑pad) — Android Developers](https://developer.android.com/training/tv/get-started/navigation)
- [Leanback UI toolkit libraries (deprecated; use Compose for TV) — Android Developers](https://developer.android.com/training/tv/playback/leanback/leanback-libraries)
- [Use network service discovery (NSD/mDNS) — Android Developers](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [NsdManager API reference — Android Developers](https://developer.android.com/reference/android/net/nsd/NsdManager)
