# Host-Bound JSON Config Templates (Hidden Configs) — Design Doc

Status: Draft / design only (no app code in this doc)
App: v2rayNG fork, package `com.v2ray.ang`, Xray-core, Kotlin + XML views
Author context: fork already carries `SubscriptionGuard` (host must contain the `departament`
label) and a "departament" theme, so this feature is meant for a managed/branded deployment.

## 0. Goal in one paragraph

A subscription URL owned by the operator returns a **full Xray JSON config/routing template**
(routing, DNS, fragment, sniffing, balancers, outbound tuning, etc.) that is bound to the host.
The client must apply **every** rule in that template and keep it working across updates, while the
end user must **not be able to view, copy, QR, or export** the template or the underlying node
credentials. This mirrors how **Happ** ships "Happ Routing" / subscription templates and encrypted
("hidden") configs, and how **Remnawave** and **3x-ui** deliver `XRAY_JSON` templates with the
per-host connection data injected server-side. This is UX/obfuscation hiding, **not DRM** — see §4.

---

## 1. Format research

### 1.1 Remnawave

Remnawave is a panel that generates subscriptions and auto-detects the client to pick a format
family: **Mihomo (Clash)**, **Base64** (legacy newline list), **Xray-json**, and **Sing-box**.
Since panel v2.2.0 an admin can define multiple **Templates per core** and route users/clients to a
specific template via *External Squads* / *Routing Rules*.

The relevant feature here is the **XRAY_JSON template**: the admin writes a *complete* Xray config
(`dns`, `routing`, `inbounds`, `outbounds`, `burstObservatory`, `policy`, fragment settings, …) and
the panel fills in the real node connection data at subscription-generation time.

Injection is driven by a root-level `remnawave` directive object that the panel **strips out before
sending to the client**:

```jsonc
{
  "remnawave": {
    "injectHosts": [
      { "selector": { "type": "uuids", "values": ["uuid1","uuid2"] }, "tagPrefix": "proxy" }
    ],
    "addVirtualHostAsOutbound": false
  },
  "routing": {
    "balancers": [
      { "tag": "Super_Balancer", "selector": ["proxy"], "strategy": { "type": "leastLoad" }, "fallbackTag": "direct" }
    ],
    "rules": [ { "network": "tcp,udp", "balancerTag": "Super_Balancer" } ]
  },
  "outbounds": [ { "tag": "direct", "protocol": "freedom" }, { "tag": "block", "protocol": "blackhole" } ]
}
```

Key mechanics:
- `injectHosts` substitutes each selected host's outbound (address, port, security, keys, transport)
  into the template. `tagPrefix: "proxy"` yields tags `proxy`, `proxy-2`, `proxy-3`, …
  (alternatives: `useHostRemarkAsTag`, `useHostTagAsTag`). Selector types: `uuids`, `remarkRegex`,
  `tagRegex`, `sameTagAsRecipient`.
- Xray balancer `selector` is a **prefix match**: `["proxy"]` matches all injected outbounds;
  `["proxy-"]` matches all except the first (`proxy`), enabling failover patterns.
- The whole `remnawave` object is deleted before the client sees the config — **the client only ever
  receives final, already-injected JSON**. That is the important architectural fact for us (§2).

Remnawave also drives the **Happ** client via `happ://` routing deeplinks and standard subscription
response headers (below).

### 1.2 3x-ui

3x-ui (MHSanaei) is an Xray panel whose subscription endpoint returns the classic
**Base64 newline-separated list** of share links (`vless://`, `vmess://`, `trojan://`, …) plus the
standard subscription response headers (`profile-title`, `profile-update-interval`,
`subscription-userinfo`). Its subscription-settings screen has a **Happ routing rules** field that
attaches a Happ routing profile to the subscription; there is an open feature request
(MHSanaei/3x-ui #4479) noting this routing is currently **global** — the same routing profile is
delivered to every client regardless of which inbound was subscribed. Takeaway: 3x-ui itself is not
an `XRAY_JSON`-template engine; it reaches the same end result (server-defined routing pushed to the
client) by piggy-backing Happ's routing deeplinks/headers on top of a normal share-link list.

### 1.3 Happ client (subscription template, routing, hidden configs)

Happ is the reference client for this UX. It reads both **HTTP response headers** and equivalent
**in-body directives** (a line prefixed with `#`, e.g. `#profile-title: ...`). Documented headers:

| Header | Meaning | Notes |
|---|---|---|
| `profile-title` | Subscription display name | plaintext or `base64:...` UTF-8, ~25 chars |
| `profile-update-interval` | Auto-update period | whole hours, e.g. `1` |
| `subscription-userinfo` | Traffic/expiry | `upload=..; download=..; total=..; expire=<unixts>` |
| `announce` | Banner text (≤200 chars) | plaintext or `base64:...` |
| `support-url`, `profile-web-page-url` | Buttons | Telegram/website |
| `routing` | Attach a Happ routing profile | value is a `happ://routing/...` deeplink |
| `routing-enable`, `custom-tunnel-config`, `socks-auth-mode`, … | Behaviour toggles | `true`/`1` on, anything else off |

**Happ routing profile** is its own JSON (not raw Xray JSON), carried as **Base64-of-JSON** inside a
deeplink:

- `happ://routing/add/{base64}` — add, activate after geo files download
- `happ://routing/onadd/{base64}` — add and activate immediately
- `happ://routing/off` — disable routing

The routing profile fields: `Name`, `GlobalProxy`, `RemoteDNSType`/`DomesticDNSType` (DoH/DoU),
`RemoteDNSDomain`/`RemoteDNSIP` (+ domestic), `Geoipurl`/`Geositeurl`, `DnsHosts` (map),
`DirectSites`/`DirectIp`, `ProxySites`/`ProxyIp`, `BlockSites`/`BlockIp`, `DomainStrategy`
(`IPIfNonMatch`/`AsIs`), `FakeDNS`, `LastUpdated`. A profile with a matching `Name` is overwritten.

**Hidden / encrypted subscriptions**: links beginning `happ://crypto...`. Per Happ's docs, in an
encrypted subscription **"the server settings and the subscription URL are hidden"** — the user sees
a working profile but cannot read the node list or the sub URL. Happ does not publish the encryption
scheme; functionally it is on-device concealment of an otherwise ordinary payload. Our §3 mirrors
this behaviour honestly.

**Sources**
- Remnawave — Xray JSON Advanced (injectHosts): https://docs.rw/learn/xray-json-advanced/
- Remnawave — Templates: https://docs.rw/docs/learn-en/templates/
- Remnawave — Config Profiles: https://docs.rw/learn-en/config-profiles/
- Remnawave templates repo: https://github.com/remnawave/templates
- Happ — App management (headers): https://www.happ.su/main/dev-docs/app-management
- Happ — Routing (profile JSON + deeplinks): https://www.happ.su/main/dev-docs/routing
- Happ — Adding Configuration/Subscription (encrypted/hidden): https://www.happ.su/main/faq/adding-configuration-subscription
- 3x-ui — per-inbound Happ routing request: https://github.com/MHSanaei/3x-ui/issues/4479
- XTLS subscription standards discussion: https://github.com/XTLS/Xray-core/discussions/4877

---

## 2. How it maps to this app

### 2.1 What already exists

The fork already has the full machinery to treat a fetched JSON blob as a runnable config:

- **`EConfigType.CUSTOM(2)`** (`enums/EConfigType.kt`) — the "full Xray JSON" profile type.
  `POLICYGROUP`/`PROXYCHAIN` also ride the `CUSTOM` scheme.
- **Raw storage** (`handler/MmkvManager.kt`): custom JSON is stored verbatim in a **separate** MMKV
  store `SERVER_RAW` via `encodeServerRaw(guid, json)` / `decodeServerRaw(guid)`. The lightweight
  `ProfileItem` (in the normal profile store) only keeps `remarks`, `server`, `serverPort` — parsed
  by `fmt/CustomFmt.parse` from the template's proxy outbound.
- **Import** (`handler/AngConfigManager.kt`):
  - `parseCustomConfigServer()` already accepts a template that `contains("inbounds") &&
    contains("outbounds") && contains("routing")`. It handles both a **JSON array** of full configs
    (each element → one CUSTOM profile, raw saved with `encodeServerRaw`) and a **single** JSON
    object (compat path). WireGuard `.conf` is also handled here.
  - Subscription fetch: `updateConfigViaSub()` downloads the URL body (honouring `userAgent`,
    `allowInsecureUrl`, socks proxy), then `parseConfigViaSub()` tries, in order: base64-decoded
    batch links → plain batch links → `parseCustomConfigServer()`. So a subscription that returns a
    JSON template array **already lands as CUSTOM profiles today**.
- **Runtime** (`core/CoreConfigManager.kt`): `getV2rayConfig()` detects `configContext.isCustom` and
  calls `buildV2rayCustomConfig()`, which:
  1. reads the raw template from `SERVER_RAW`,
  2. rewrites `routing.rules[].process` package names → UIDs when process routing is on,
  3. injects the `tun` inbound (with the user's MTU) if the template lacks one,
  4. returns the (mostly untouched) template to the core.

  → **The template's routing/DNS/fragment/balancer/outbound settings are applied as-authored.**
  This is exactly the Remnawave/3x-ui/Happ "server owns the config" model, and it is the
  **primary/MVP path**: the panel injects host data server-side (Remnawave `injectHosts`), the app
  just fetches, stores raw, and runs.

### 2.2 What a fetched template becomes (MVP path — server-side injection)

```
Subscription URL (host = *.departament.*)
   → HttpUtil.getUrlContentWithUserAgent (User-Agent = "Happ" or app UA)
   → AngConfigManager.parseConfigViaSub
        → base64? batch links? → parseCustomConfigServer (inbounds+outbounds+routing present)
        → each JSON element → CustomFmt.parse → ProfileItem(configType=CUSTOM)
        → MmkvManager.encodeServerRaw(guid, prettyJson)   // full template kept verbatim
   → CoreConfigManager.buildV2rayCustomConfig → core runs the template unchanged
```

No new parsing is strictly required for the MVP; the template already flows end-to-end. The new work
is (a) the **hidden** flag and gating (§3), and (b) parsing the **response headers** (§2.4).

### 2.3 Per-server outbound injection *inside* the client (optional, richer path)

Remnawave injects host data server-side, so in the MVP each returned config is self-contained. If we
want **one template + a switchable list of nodes on the device** (Happ "routing applies to all
servers"), we inject client-side. Design:

- The subscription returns **two parts**: one **template** (config with a placeholder outbound tagged
  `proxy`, no real credentials) and a **node list** (ordinary `vless://`/`vmess://`/… share links, or
  a small JSON array of node params). Delivery options: template in body + nodes base64 below it, or
  template via a `routing`/custom header and nodes in the body.
- On apply, for the selected node:
  1. `CoreOutboundBuilder.convert(profileItem)` already converts a `ProfileItem` → an
     `OutboundBean`. Build the outbound for the chosen node.
  2. Deep-clone the template JSON; **replace the outbound whose `tag == "proxy"`** (or a
     placeholder token like `"__PROXY__"`) with the built outbound, preserving the template's
     `streamSettings.sockopt`/fragment if the template set them and the node did not.
  3. For multi-node balancer templates, inject the whole list as `proxy`, `proxy-2`, … and let the
     template's balancer `selector` prefix-match them (same convention as Remnawave §1.1).
  4. Store the merged result as the runtime raw (or better: keep template + node separate and merge
     at `buildV2rayCustomConfig` time so a node switch does not require re-fetching).

This is more code and can come in a later phase; the tag/selector convention is deliberately chosen
to match Remnawave so operator templates are portable.

### 2.4 Subscription response headers

Extend the subscription fetch to read the Happ/Remnawave headers (§1.3) and the in-body `#key: value`
form: map `profile-title` → `SubscriptionItem.remarks`, `profile-update-interval` (hours) →
`updateInterval` (minutes), `subscription-userinfo` → a stored quota/expiry to show in the list,
`announce` → a banner. `HttpUtil.getUrlContentWithUserAgent` must be extended to surface headers
(today it returns only the body string).

---

## 3. The "not readable / not exportable" requirement

### 3.1 Threat model & honesty statement

This is **obfuscation + UX hiding, not DRM**. The device runs the config, so the plaintext must exist
in memory at connect time; a determined user with root, a debugger, a rooted MMKV dump, or a MITM box
on their own network can always recover it. The goal is only: a **normal user in the app UI cannot
view, copy, QR, edit, or export** the template or node credentials, and casual inspection of app
storage is not trivially readable. State this plainly in any operator-facing docs. Do **not** claim
the config is "secure" or "unextractable".

### 3.2 Data-model flag

Add a boolean to both levels so the flag survives subscription refresh (profiles are wiped and
re-imported on each update, so the source of truth is the **subscription**, which then stamps each
imported profile):

```kotlin
// dto/entities/SubscriptionItem.kt
var locked: Boolean = false        // operator-managed, hidden subscription

// dto/entities/ProfileItem.kt
var locked: Boolean = false        // stamped from the owning subscription at import time
```

Naming: use one term consistently — `locked` (or `hidden`). During
`AngConfigManager.parseConfig` / `parseCustomConfigServer`, set `config.locked = subItem?.locked`.
Optionally auto-set `locked = true` when the source host matches the managed label
(`SubscriptionGuard.REQUIRED_LABEL == "departament"`) so operator subs are hidden by default.

### 3.3 Storage at rest (optional hardening)

Raw templates already live in a dedicated `SERVER_RAW` MMKV store, separate from `ProfileItem`. To
raise the bar beyond "obfuscation by separate store":

- Encrypt the raw value with an **AES-GCM key from the Android Keystore** (`AndroidKeyStore`,
  non-exportable, optionally `setUnlockedDeviceRequired`). Write via a new
  `encodeServerRawSecure`/`decodeServerRawSecure` pair, or transparently inside the existing
  `encodeServerRaw`/`decodeServerRaw` when `locked`. Core reads the decrypted value only in-process.
- Alternatively MMKV's built-in encryption (`MMKV.mmkvWithID(ID_SERVER_RAW, MULTI_PROCESS_MODE,
  cryptKey)`) with the key held in Keystore. Simpler, coarser.

Keep decryption confined to `CoreConfigManager.buildV2rayCustomConfig`; never return decrypted raw to
any UI/clipboard path.

### 3.4 UI actions to gate

All leak points, and the gate when `profile.locked` (or the profile belongs to a `locked` sub):

| Location | Action | Gate |
|---|---|---|
| `ui/GroupServerFragment.kt` `shareServer()` dialog | Build the options list so **QR (0)**, **share link (1)**, **full content (2)**, **edit (3)** are omitted for locked profiles — leave only *remove* (and *select*). | Filter `shareOptions`/`skip` by `locked`. |
| `AngConfigManager.shareFullContent2Clipboard()` | Returns the decrypted template today via `CoreConfigManager.getV2rayConfig`. | Early-return `-1` when `locked`. |
| `AngConfigManager.share2Clipboard()` / `share2QRCode()` / `shareConfig()` | For CUSTOM these already yield empty, but harden: return empty/`-1` when `locked`. | guard in `shareConfig`. |
| `AngConfigManager.shareNonCustomConfigsToClipboard()` | Bulk export loop. | Skip locked guids. |
| `editServer()` → `ServerCustomConfigActivity` | Shows raw JSON in an editable text box. | Block open (toast) when `locked`, **or** open strictly read-only + redacted (no raw shown). Preferred: block. |
| `ui/SubEditActivity.kt` | Shows/edits the subscription **URL** and lets user copy it. | When `sub.locked`, hide/redact the URL field (show `••••`), disable copy, keep only enable/auto-update toggles. Mirrors Happ hiding the sub URL. |
| Long-press / context menus, "export all", backup/share-to-file, `WebDavConfig` cloud backup | Any path that serialises profiles/raw. | Exclude locked profiles/subs from any export or backup that leaves the device. |
| `service/*` logcat / `LogcatActivity` | Core may log the config. | Ensure loglevel and log sharing do not dump the full config for locked profiles. |

Also gate the ability to **delete-and-thereby-inspect** indirectly: none, deletion is fine.

### 3.5 What stays working

- Selecting the profile, connecting, latency test, auto-update, traffic stats, `subscription-userinfo`
  display, `announce` banner — all unaffected; they never expose raw.

---

## 4. Security / trust note

Applying a remote JSON template means the **operator/host fully controls the client's network stack**:

- Routing rules can send arbitrary domains **direct** (bypassing the tunnel) or to **block**.
- DNS servers, `hosts` overrides, and `fakedns` can be redefined — the host can steer name
  resolution for any domain, including exfiltration or censorship of specific sites.
- Outbound settings, fragment, and `sockopt` (incl. `dialerProxy`, `domainStrategy`) can be changed.
- A malicious template could add an `inbound` that listens on a non-loopback interface, weaken TLS
  (`allowInsecure`, custom `pinnedCA`), or route to attacker infrastructure.

Because the value proposition is "trust your operator", validate defensively before running:

1. **Transport trust**: require HTTPS for the subscription (the fork's `SubscriptionGuard` already
   forces the `departament` host label; keep HTTPS-only for locked subs — do not honour
   `allowInsecureUrl` for them).
2. **Schema validation**: parse into `V2rayConfig` and reject configs that fail to deserialize or
   that are missing `outbounds`. Reject/strip inbounds that `listen` on anything other than
   loopback/`tun` unless explicitly expected.
3. **Sanitise dangerous inbounds/ports**: don't let a template open a public inbound on the device.
4. **Size/sanity caps**: cap template size and rule counts to avoid pathological configs.
5. **No code execution**: Xray JSON is declarative; still, never `eval` any `remnawave`/vendor
   directive — strip unknown vendor objects (like Remnawave does) rather than acting on them.
6. **Pin the origin**: consider pinning the operator's cert/host for locked subscriptions.
7. **Be explicit to users** (even briefly) that a managed profile applies operator-defined routing
   and DNS.

---

## 5. Step-by-step implementation plan (small commits)

Each step is independently reviewable and, where possible, shippable.

1. **Data model flag.** Add `locked: Boolean = false` to `dto/entities/SubscriptionItem.kt` and
   `dto/entities/ProfileItem.kt`. No behaviour change yet. (Backwards-compatible: default false.)

2. **Stamp the flag on import.** In `handler/AngConfigManager.kt` (`parseConfig`,
   `parseCustomConfigServer`) set `config.locked = subItem?.locked == true`. Optionally auto-enable
   for hosts matching `SubscriptionGuard.REQUIRED_LABEL`.

3. **Gate share/QR/full-content/bulk export.** In `handler/AngConfigManager.kt` guard
   `shareConfig`, `share2Clipboard`, `share2QRCode`, `shareFullContent2Clipboard`,
   `shareNonCustomConfigsToClipboard` to no-op for locked profiles.

4. **Gate the list UI.** In `ui/GroupServerFragment.kt` `shareServer()` build the options list and
   `skip` so locked profiles expose only non-revealing actions; block `editServer()` →
   `ServerCustomConfigActivity` for locked (toast "managed profile").

5. **Gate the subscription editor.** In `ui/SubEditActivity.kt` redact/disable the URL field and its
   copy action when `sub.locked`; keep enable/auto-update/interval controls.

6. **Response-header parsing.** Extend `util/HttpUtil` to return headers alongside the body, and in
   `handler/AngConfigManager.updateConfigViaSub` apply `profile-title` → `remarks`,
   `profile-update-interval` → `updateInterval`, `subscription-userinfo` → stored quota/expiry,
   `announce` → banner. Support the in-body `#key: value` form too.

7. **Template validation.** Add a `fmt/`-level validator (e.g. extend `fmt/CustomFmt.kt` or a new
   `fmt/TemplateValidator`) called from `parseCustomConfigServer`: deserialize into
   `dto/V2rayConfig`, require `outbounds`, reject/strip non-loopback inbounds, cap size/rule count,
   strip unknown vendor directive objects (e.g. a leftover `remnawave` key). Reject on failure.

8. **Encrypt raw at rest (hardening).** In `handler/MmkvManager.kt` add Keystore-backed AES-GCM
   encryption for `SERVER_RAW` when `locked` (`encodeServerRawSecure`/`decodeServerRawSecure`), and
   decrypt only inside `core/CoreConfigManager.buildV2rayCustomConfig`. Migrate existing raw lazily.

9. **(Optional) Client-side per-node injection.** New `core/` helper that clones the template and
   replaces the `proxy`-tagged (or `__PROXY__` placeholder) outbound with
   `CoreOutboundBuilder.convert(selectedNode)`, supporting `proxy`/`proxy-2`… multi-node balancer
   templates. Wire into `buildV2rayCustomConfig` so a node switch re-merges without re-fetch. Keep
   template and node list stored separately.

10. **Backup/WebDav exclusion.** Ensure `dto/entities/WebDavConfig` backup/export paths and any
    "export all" flow skip locked profiles/subs.

11. **Docs + honesty note.** Operator/README note that hiding is obfuscation, not DRM (§4), and that
    a managed template controls routing/DNS.

### Likely files touched
- `dto/entities/SubscriptionItem.kt`, `dto/entities/ProfileItem.kt` — `locked` flag
- `dto/V2rayConfig.kt` — validation targets / optional vendor directive strip
- `fmt/CustomFmt.kt` (+ optional new `fmt/TemplateValidator.kt`) — parse + validate template
- `handler/AngConfigManager.kt` — stamp flag, gate share/export, apply headers
- `handler/SubscriptionUpdater.kt` — no logic change (uses `updateConfigViaSub`); confirm interval mapping
- `handler/MmkvManager.kt` — encrypted `SERVER_RAW` (`encode/decodeServerRawSecure`)
- `core/CoreConfigManager.kt` — decrypt raw, optional per-node injection at `buildV2rayCustomConfig`
- `core/CoreOutboundBuilder.kt` — reused for client-side injection
- `enums/EConfigType.kt` — reuse `CUSTOM`; no new type required (a new `MANAGED_TEMPLATE` value is
  optional if we want to distinguish managed configs from user-imported CUSTOM in the UI)
- `ui/GroupServerFragment.kt`, `ui/SubEditActivity.kt`, `ui/ServerCustomConfigActivity.kt` — gating
- `util/HttpUtil.kt` — expose response headers
