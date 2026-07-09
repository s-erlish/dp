# INCY public-source investigation — findings

**Date:** 2026-07-09 · **Scope:** direct inspection of the four public `INCY-DEV` GitHub repos via
`git clone` (source tree only; no 120–160 MB desktop binaries downloaded).
**Rule:** this is an investigation doc only — no app code was modified.

Repos inspected (all cloned to a temp dir, since removed):

| Repo | URL | What it actually is |
|---|---|---|
| incy-platforms | https://github.com/INCY-DEV/incy-platforms | **Release-metadata repo only** — see §1 |
| incy-docs | https://github.com/INCY-DEV/incy-docs | Provider-integration docs (MkDocs, RU + `.en.md`) — the useful part |
| incy-icons | https://github.com/INCY-DEV/incy-icons | 58 MIT-licensed brand SVGs — see §3 |
| incy-link-encoder | https://github.com/INCY-DEV/incy-link-encoder | TS `incy://crypt1/` encoder — full source readable, see §4 |

---

## 1. incy-platforms — what "Source code (zip)" really contains  → DEAD END (as suspected)

The entire `main` tree is **two files**:

```
README.md        (marketing / download links / feature bullets)
RELEASE.json     (version + binary download URLs)
```

- Git history is a single squashed commit `404995e "Update RELEASE.json"`.
- The two release **tags** the owner pointed at — `desktop-v3.2.6` and `desktop-v3.3.0` —
  contain the **exact same two files** (verified with `git ls-tree -r <tag>`: only
  `README.md` + `RELEASE.json`). So the auto-generated "Source code (zip)" for every
  desktop release is just this metadata repo snapshotted — **there is no app source, no
  Electron/Tauri/Flutter wrapper, no build CI, no subscription/announce/settings logic** here.
- The desktop app is confirmed **Compose Multiplatform (JVM)** — but only by a passing mention
  in incy-docs/link-encoder ("Desktop uses `javax.crypto`… Compose Multiplatform JVM"); **no
  desktop code is published**.
- `RELEASE.json` versions (as of clone): android `3.2.1`, desktop `3.2.3`, ios `2.3.1`.
  Package ids: Android `llc.itdev.incy`, iOS App Store `id6756943388`. Copyright "INCY LLC".

**Conclusion:** the Android/iOS/Desktop apps are **closed source**. The only machine-readable
public artifact is the provider spec in incy-docs and the crypt1 encoder. Everything else
(APK 52 MB, desktop installers 150 MB) is a binary dead end — not downloaded.

---

## 2. incy-docs — the exact provider / subscription spec

Docs live in `ru/dev-docs/*.en.md`. The load-bearing files are `subscription-format`,
`app-management`, `share-links`, `deep-links`, `provider-notifications`, `icon-presets`.
Below is the precise, implementable spec. Header names are **case-insensitive**.

### 2a. Response HEADERS (server → client)

| Header | Type / values | base64? | body `#` form? | Notes |
|---|---|---|---|---|
| `profile-title` | string, **≤ 25 chars** | yes (`base64:` prefix) | yes | base64 decoded: **line 1 = name, rest = description** |
| `subscription-name` | string | — | — | fallback for `profile-title` |
| `content-disposition` | string | — | — | last-resort name; strips `.txt`/`.yaml`/`.yml` |
| `profile-description` | string | yes | — | separate description |
| `profile-update-interval` | int (**hours**, must be whole hours) | — | yes | |
| `subscription-userinfo` | `upload=..;download=..;total=..;expire=..` (bytes; expire=unix **seconds**) | — | — | see §2c |
| `support-url` | URL | — | yes | TG link → shows Telegram icon |
| `support-email` | email | — | yes | shows "Email" button; hidden if absent |
| `profile-web-page-url` (alt `homepage`) | URL | — | yes | website button |
| `announce` | string, **≤ 200 chars** | yes | yes | banner; UI shows **≤ 5 lines** then ellipsis |
| `announce-url` | URL | — | yes | clickable-link announce |
| `sort-order` | `none` \| `ping` \| `name` | — | — | applied to global sort on refresh |
| `autorouting` | URL | — | yes | routing profile w/ auto-update (sets `sourceURL`) |
| `routing` | base64 or link | — | yes | static routing profile |
| `premium-url` | URL | — | — | "Premium" button; hidden if absent |
| `hide-url` | `1`/`0`/`true`/`false`/`yes`/`no` (ci) | — | yes | hides URL from Share/Copy/QR/backup |
| `banner-text` | string | yes | — | overrides panel banner (premium only) |
| `banner-button-text` | string | — | — | |
| `banner-button-url` | URL | — | — | |
| `banner-bg-color` | hex `#RRGGBB` | — | — | |
| `banner-button-color` | hex `#RRGGBB` | — | — | |
| `per-app-proxy-enable` | `1`/`0` | — | — | **Android only** |
| `per-app-proxy-mode` | `bypass` \| `proxy` | — | — | bypass = listed apps skip VPN; proxy = only listed go through |
| `per-app-proxy-list` | CSV / newline / URL | yes | — | package names; URL = plain-text file, refetched each update |
| `fragmentation-enable` | `1`/`0` | — | — | TCP fragmentation (overrides user global) |
| `fragmentation-packets` | `tlshello` \| `1-3` \| `1` \| `all` | — | — | |
| `fragmentation-length` | `min-max` (bytes) | — | — | |
| `fragmentation-interval` | `min-max` (ms) | — | — | |
| `noises-enable` | `1`/`0` | — | — | UDP noise before handshake (WG/Hy2) |
| `noises-type` | `rand` \| `str` \| `hex` | — | — | |
| `noises-packet` | string (`min-max` for `rand`) | — | — | |
| `noises-delay` | `min-max` ms | — | — | |
| `server-address-resolve-enable` | `1`/`0` | — | — | DoH pre-resolve of server host |
| `server-address-resolve-dns-domain` | URL (`/dns-query`) | — | — | |
| `server-address-resolve-dns-ip` | IP | — | — | bootstrap IP |
| `no-limit-enabled` | `1`/`0` | — | — | **iOS only**; keeps NE under 50 MB cap; enable-only |

**Header ↔ body precedence:** HTTP header **always wins**; the `#directive:` in the body is a
fallback used only when the header is absent (designed for static-nginx hosting).
`base64:` prefix is accepted on `announce`, `profile-title`, `profile-description`,
`per-app-proxy-list`.

### 2b. Body `#`-directives / special strings

Extracted from the body and removed from the server list:

| Directive / pattern | Meaning |
|---|---|
| `#profile-title:` / `#profile-description:` / `#support-url:` / `#profile-web-page-url:` / `#announce:` / `#announce-url:` / `#profile-update-interval:` / `#hide-url:` | body-form of the headers above |
| `://autorouting/onadd/{url}` · `://autorouting/add/{url}` | auto-updating routing profile (sets `sourceURL`) |
| `://routing/onadd/{url}` · `://routing/{base64}` · `://routing/add/{base64}` · `://onadd/{url or base64}` | one-time routing import (no auto-update) |

**Routing source priority** (first match wins): `1` `autorouting` header → `2` body
`://autorouting/...` line → `3` `routing` header → `4` body `://routing/...` base64.
Only `://autorouting/` sets `sourceURL`/auto-update.

### 2c. subscription-userinfo details

`upload=0;download=1073741824;total=10737418240;expire=1735689600` (bytes; expire unix seconds).
- **`expire > 32000000000` → interpreted as milliseconds, divided to seconds.** (already known)
- **NEW: `subscription-userinfo: 0` hides the traffic block entirely** on the home screen.

### 2d. Body formats

base64 (std **and** URL-safe `-_`), plaintext (one link/line), JSON (array of full xray
configs, or single full config with `inbounds`/`outbounds`/`routing`/`dns`), mixed
(links + `://…` routing strings + `#` metadata), and **raw WireGuard/AmneziaWG `.conf`**
(detected by `[Interface]`+`PrivateKey`; AmneziaWG detected by obfusc params
`Jc, Jmin, Jmax, S1–S4, H1–H4, I1–I5`) → parsed as **one** server; client stores `.conf` verbatim.
Schemes **recognized but NOT parsed (skipped): `ssr://`, `tuic://`, `hysteria://`**.

### 2e. Request headers (client → server) on subscription refresh  — NEW

| Header | Value |
|---|---|
| `User-Agent` | `INCY/<version>/<platform>` |
| `Accept` | `*/*` |
| `Accept-Language` | device tag e.g. `ru-RU` |
| `Accept-Encoding` | iOS only: `gzip, deflate, br` |
| `x-app-version` | app version |
| `x-device-locale` | device language |
| `x-client` | `INCY` |
| `x-hwid` + `X-Device-ID` (Android alias) | HWID (only when HWID sending enabled) |
| `x-device-os` | `iOS`/`Android`/`Linux`/`Windows` |
| `x-ver-os` | OS version |
| `x-device-model` | device model |

### 2f. Share-link (per-server) params worth noting — NEW detail

- Name/description tail: `#Name?serverDescription=base64` — everything before first `?` is the
  URL-decoded name; `serverDescription` base64 **≤ 30 chars**.
- **`fp` (uTLS fingerprint) full accepted set:** `chrome, firefox, safari, ios, android, edge,
  360, qq, random, randomized, randomizednoalpn` + pinned `hellochrome_120/131/133,
  hellofirefox_120/148, helloios_13/14, helloedge_106, hellosafari_26_3, hello360_11_0,
  helloqq_11_1, hellogolang, unsafe`. Empty → `chrome`. Passed to xray as-is.
- VLESS advanced params: `pbk`/`sid`/`spx` (Reality), `mode`/`extra` (xhttp; `splithttp`→`xhttp`),
  mKCP `seed/mtu/tti`, QUIC `quicSecurity/key`, and security extras
  `fm` (uTLS mask), `pcs` (cert SHA-256 pin), `vcn` (verify-by-name), `ech` (ECH config),
  `pqv` (ML-DSA-65 post-quantum verify).
- **Per-server fragmentation in the share link:** `fragmentPackets` (`tlshello`/`1-3`/`1`/`all`),
  `fragmentLength` (`10-30`), `fragmentInterval` (`10-30`) — independent of the provider-level
  headers; applied when `security` is tls/reality.
- Hysteria2 multi-port `host:port1,port2-port3`; obfs `obfs`/`obfs-password`, `up`/`down` Mbps.
- WireGuard: `publickey` (req), `address`, `mtu` (1500), `reserved=1,22,33`, `allowinsecure`.

---

## 3. incy-icons — reusable? Partly.

- **58 SVGs**, files `INCY-00001.svg … INCY-00058.svg` at repo root. **MIT licensed, no
  attribution required, commercial use OK** (LICENSE + README explicit).
- BUT these are the **INCY brand mark set** — the "N" logo / wordmark glyphs in black/white
  (verified by reading several: e.g. INCY-00017/00024 are the stylized `N` logo; viewBoxes are
  `0 0 246 246` (×37), `0 0 256 256` (×16), plus a few `142×102`, `122×122`, `30×30`). They are
  **INCY-specific branding, not a generic UI/app-launcher icon library.**
- **This is NOT the "16 app icons" chooser** the marketing README advertises — those launcher
  icons ship inside the closed apps and are **not** in this repo.
- **Usefulness for our app-icon chooser: low.** We can legally reuse the SVGs (MIT), but shipping
  a competitor's logo as our app icon makes no sense. Value is only as style reference. The
  actually-relevant icon spec is **icon-presets** (below), which is a *link-icon* key set, not
  launcher icons.

### icon-presets (from incy-docs) — the real icon spec  — NEW

Providers set a **link icon** per slot (bot/channel/support) by **stable string key**, mapped
client-side to Material Icons (Android/Desktop) / SF Symbols (iOS). Fields in `SubscriptionSettings`:
`botIconKey`, `channelIconKey`, `supportIconKey` (delivered via Premium API `settings`).
Defaults when null/unknown: bot→`send`, channel→`megaphone`, support→`help`.

**20 keys:** `send, bot, chat, message, mail` (bot/msg) · `megaphone, bell, newspaper, rss,
broadcast` (news) · `help, support, lifebuoy, info, book` (support) · `crown, star, gem, rocket,
heart` (accent). Full Material/SF mapping table is in `icon-presets.en.md`.

---

## 4. incy-link-encoder (TS) — exact crypt1 encode/decode  — full source read

**Wire format (implementable exactly):**

```
incy://crypt1/<base64url( IV(12 bytes) || ciphertext || GCM-tag(16 bytes) )>
```

- **Cipher: AES-256-GCM.** IV = 12 random bytes; auth tag = 16 bytes; base64url **without
  padding** (`+`→`-`, `/`→`_`, strip `=`).
- **Plaintext = compact JSON with keys sorted alphabetically** (byte-identical across
  iOS CryptoKit / Android+Desktop `javax.crypto`):
  `{"n":"<name>","url":"<sub url>","v":1}` — `n` optional (**truncated to 128 chars**),
  `v` schema version = `1`, `url` required. (Note: docs sheet shows `v` too.)
- **Key K1 derivation (fully disclosed in `src/index.ts` + `src/keymat.ts`):**
  `SHA-256( "incy"+"deep"+"crypt1"+"v2026.06" + keymatA[1024:1056] + keymatB[2048:2080] )`
  where keymatA/B are two 4 KiB blobs base64-inlined in `keymat.ts` (from `assets/*.bin`).
  **Key fingerprint** `SHA-256(K1) = b6bf708471cc90043232967660aade86a50b4e57929db2e53c5fa34db624c08c`.
- **Pinned test vector** (deterministic, iv = `000102030405060708090a0b`, url `https://sub.example.com/token`):
  `incy://crypt1/AAECAwQFBgcICQoLNyIQL3rDwRZqnyoD8pGKSLXP6o8NdSXQVSSALNbbUyIr__tWGFUexdIfKvvmDnuDGbmBvuppfNef6aKNZUwOm4c-Sg`
- API: `encryptLink(url, {name?})`, `decryptLink(link) → {url, name?}`,
  `encryptLinkDeterministic(url,{iv,name?})`, consts `SCHEME_VERSION="crypt1"`, `KEY_FINGERPRINT`.
- **Explicitly obfuscation, not secrecy** — key is reconstructable from the package and baked
  into every client; README/threat-model defends against Telegram/RKN scanners + grep, NOT Frida.
  If burnt, a future `crypt2/` ships new keymat; **old `crypt1/` links decode forever** (schemes
  never removed).
- **Client UX on import:** decode → confirmation sheet with URL masked as `••••`; `n` prefills
  the Name field; sets internal `importedViaCrypt1=true`; **Share/Copy/QR re-emits crypt1** so the
  obfuscation survives re-forwarding. `crypt1` handler is INCY-only (V2Box/Shadowrocket/Happ
  don't support it), on clients **≥ June 2026**.

### deep-link (`incy://`) grammar — the full scheme table

Works with any registered scheme (`incy://…`). Verbs: `://connect`|`://open`, `://disconnect`|
`://close`, `://toggle`, `://status`. Import: `://import/{data}` (auto-detect: sub URL / server
link / multi-URL / raw `.conf`), `://add/{url}`, `://crypt1/{payload}` (§4). Routing:
`://routing/add/{b64}`, `://routing/onadd/{b64|url}`, `://autorouting/onadd|add/{url}`,
`://onadd/{url}`, plus `?data={base64}` query form (Android/iOS). If data after `onadd/` is
`http(s)` → download; else base64 → decode inline.

---

## 5. Concretely useful & NEW (not in incy-analysis.md / incy-settings-design.md / happ-parity-details.md)

Confirmed against our existing docs (grep): those cover announce (5-line/base64/ms→s), `sort-order`,
`subscription-userinfo`, `profile-update-interval`, AmneziaWG, and crypt1 at a *high level only*.
**New, with exact values:**

1. **crypt1 exact wire layout & JSON** — `base64url(iv12||ct||tag16)`, AES-256-GCM, sorted-keys
   `{"n","url","v":1}`, name ≤128, pinned test vector + key fingerprint (§4). Enough to
   interop-decode Incy links or copy the scheme.
2. **`hide-url`** header/body/API, value grammar, and full "what's blocked" matrix (§2a) — entirely absent from our docs.
3. **Provider banner** headers `banner-text/-button-text/-button-url/-bg-color/-button-color`,
   `header ?? panel` resolution, premium-gated (§2a) — absent.
4. **Per-app proxy** headers (Android) `per-app-proxy-enable/-mode/-list` incl. base64 + remote-URL list (§2a) — absent.
5. **Noise packets** `noises-enable/-type/-packet/-delay` (§2a) — absent.
6. **Fragmentation** — both the header set (`fragmentation-*`) and the **per-server share-link**
   variant (`fragmentPackets/Length/Interval`) with exact value ranges (§2a, §2f) — absent.
7. **DoH pre-resolve** `server-address-resolve-*` (§2a) — absent.
8. **`premium-url`, `support-email`, `no-limit-enabled`(iOS), `homepage` alt** (§2a) — absent.
9. **Exact string limits:** profile-title ≤25, announce ≤200, serverDescription ≤30 (§2a,§2f) — absent.
10. **`subscription-userinfo: 0` hides the traffic block** (§2c) — absent.
11. **Client→server request headers** (`User-Agent: INCY/<ver>/<platform>`, `x-hwid`+`X-Device-ID`,
    `x-device-os/-model`, `x-ver-os`, `x-client`, `x-app-version`) (§2e) — absent; useful if we
    ever mimic Incy for provider compatibility.
12. **Routing source priority** 4-tier table + `sourceURL`-only-on-autorouting rule (§2b) — absent.
13. **icon-presets: 20 stable link-icon keys** + Material/SF mapping + null/unknown fallback (§3) —
    directly reusable for a bot/channel/support link-icon picker.
14. **`fp` uTLS full value list** incl. pinned `hellochrome_133`, `hellosafari_26_3`, `unsafe`,
    default→`chrome` (§2f) — more complete than anything in our fingerprint notes.
15. **VLESS security extras** `fm/pcs/vcn/ech/pqv` (ECH + ML-DSA-65 post-quantum) (§2f) — absent.

### Dead ends (flagged, no fabrication)

- **incy-platforms "Source code (zip)" = README.md + RELEASE.json only** — at `main` and at both
  desktop tags. No app/desktop/CI source. (§1)
- **Android + iOS + Desktop apps are closed source.** Desktop = Compose Multiplatform JVM (only a
  mention). Binaries (APK 52 MB, installers 150 MB) deliberately not downloaded — no source inside.
- **incy-icons ≠ the app-icon chooser.** 58 MIT SVGs, but INCY-brand marks, not generic launcher
  icons; the advertised "16 app icons" are not published.
- **crypt1 "encryption" is not real secrecy** — key is public/derivable; treat as obfuscation only.

---

## Citations (all read directly from the cloned repos, 2026-07-09)

- incy-platforms tree/tags: `git ls-tree -r desktop-v3.2.6` / `desktop-v3.3.0` →
  `README.md`, `RELEASE.json`. https://github.com/INCY-DEV/incy-platforms
- Subscription/headers/body/protocols: `incy-docs/ru/dev-docs/subscription-format.en.md`,
  `app-management.en.md`, `share-links.en.md`, `deep-links.en.md`, `provider-notifications.en.md`,
  `icon-presets.en.md`. https://github.com/INCY-DEV/incy-docs
- crypt1 encoder: `incy-link-encoder/src/index.ts`, `src/keymat.ts`, `test/index.test.ts`,
  `README.md`, `package.json`. https://github.com/INCY-DEV/incy-link-encoder
- Icons: `incy-icons/README.md`, `LICENSE`, `INCY-000NN.svg`. https://github.com/INCY-DEV/incy-icons
