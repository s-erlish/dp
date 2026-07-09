# departament VPN — Anti‑Censorship Strategy for Russia (mid‑2026)

**Status:** Research + design only. No app code is changed by this document.
**App:** v2rayNG / Xray‑core fork, package `com.v2ray.ang`, Kotlin + XML views, app label `departament`.
**Scope:** what Roskomnadzor (RKN) does in mid‑2026, what survives it, and the concrete,
prioritized product/engineering work that maximizes **survivability + UX** for Russian users.

**Read‑first / non‑duplication.** This complements — does not repeat — the three existing docs:
`docs/telegram-auth-design.md` (auth + subscription delivery), `docs/hidden-templates-design.md`
(operator‑controlled hidden JSON templates), `docs/design-system-2026.md` (UI/redesign). Where those
already cover a mechanism (e.g. subscription fetch plumbing, `SubscriptionGuard`, hidden templates,
response headers), this doc points to them and adds only the censorship‑resistance layer.

> **Honesty note (applies throughout).** There is no permanently unblockable protocol. RKN's TSPU is
> a moving target; the correct engineering posture is **agility** — many camouflage options, fast
> server rotation via subscriptions, automatic fallback, and resilient config delivery — not a single
> "magic" transport. Everything below is written to that posture.

---

## 1. Current RKN / DPI landscape — what breaks vs. survives

### 1.1 The infrastructure: TSPU is now everywhere
- **TSPU** (Технические средства противодействия угрозам — "technical means of countering threats"),
  mandated by the 2019 "Sovereign Internet" law, is a DPI "black box" installed inline at every ISP
  and remotely controlled by RKN; operators cannot inspect or change its rules.
  ([researchgate TSPU paper](https://www.researchgate.net/publication/364718059_TSPU_Russia's_decentralized_censorship_system),
  [iplogs](https://iplogs.com/blog/russia-tspu-how-it-blocks-vpns))
- **Nationwide inline deployment reached ~100% on 2026‑05‑25** — all 85 federal subjects upgraded,
  the big‑three carriers' backbones at 100% inline TSPU, covering nearly all fixed + mobile traffic.
  Processing capacity is being scaled 752 → **954 Tbit/s by 2030** with ML upgrades funded at
  ~2.27 bn₽ (Jan 2026) and ~20 bn₽/yr overall; stated goal **92% VPN‑blocking effectiveness by 2030**.
  ([tgvpn TSPU analysis](https://tgvpn.io/en/tspu-dpi-russia-2030-analysis.html),
  [zona.media](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026),
  [techradar 92%](https://www.techradar.com/vpn/vpn-privacy-security/russias-censor-body-roskomnadzor-wants-to-block-92-percent-of-vpn-apps-by-2030-and-its-investing-20-billion-rubles-a-year-to-build-a-permanent-vpn-censorship-system))

### 1.2 The four detection layers (2026)
Modern TSPU DPI combines: (1) **plaintext SNI** inspection (still the primary website‑block basis;
empty SNI lifts the block in ~100% of tests but breaks Reality's cover), (2) **JA3/JA4 TLS
fingerprinting** to separate real browsers from proxy clients, (3) **ML on packet timing / sizes /
entropy** (identifies raw WireGuard in ~100 packets; flags high‑entropy Shadowsocks‑like streams),
and (4) **active probing** — TSPU itself connects to a suspected server and, if it answers like a
proxy rather than a real web service, blacklists it.
([tgvpn](https://tgvpn.io/en/tspu-dpi-russia-2030-analysis.html),
[iplogs](https://iplogs.com/blog/russia-tspu-how-it-blocks-vpns),
[TLS Obscurity paper](https://ris.uni-paderborn.de/download/59824/59826/TLS_Obscurity.pdf))

### 1.3 The signature 2026 block: the "TLS freeze"
Since mid‑2025 and hardening through 2026, TSPU applies a **stateful freeze** to encrypted TCP to
**foreign datacenter IPs** (Hetzner, DigitalOcean, OVH, etc.): after roughly **25 packets / ~16 KB**
in either direction the server's packets simply **stop arriving** (no RST — a silent stall forcing a
timeout). It hits genuine HTTPS and protocol‑mimicry equally, and there are reports of the **initial
ClientHello being corrupted** rather than dropped.
([net4people/bbs #490](https://github.com/net4people/bbs/issues/490),
[Xeovo hub](https://hub.xeovo.com/posts/132-russia-widespread-vless-outages-due-to-tls-handshake-blockingdegradation-request-tlstransport-hardening-and-anti-probing),
[XTLS/Xray‑core #5332](https://github.com/XTLS/Xray-core/issues/5332))

### 1.4 The shift to allow‑listing (the strategic threat)
RKN is layering an **allow‑list model** on top of block‑lists:
- **SNI allow‑list**: only handshakes whose SNI is on a permitted set pass cleanly — so **Reality's
  `dest`/`serverName` must be a real, allow‑listed, RU‑reachable site** to survive.
- **CIDR / AS allow‑listing**: destination IP subnets are increasingly filtered; whole hosting ASes
  are cut (the Hetzner AS24940 case: handshake passes, then data dies).
- A civilian **"registry of socially‑significant services"** whitelist launched Sept 2025 (57 initial
  domains — state media, banks, VK, Max, Yandex, marketplaces) and is periodically expanded; during
  mobile shutdowns only these remain reachable.
  ([net4people/bbs #490](https://github.com/net4people/bbs/issues/490),
  [zona.media](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026),
  [github: vpn‑configs‑for‑russia white‑lists](https://github.com/igareck/vpn-configs-for-russia/blob/main/Vless-Reality-White-Lists-Rus-Mobile.txt))

### 1.5 Mobile shutdowns, throttling, and messenger blocks (context that shapes UX)
- **Mobile‑internet shutdowns** are now routine and urban: central Moscow was cut for ~3 weeks in
  March 2026; some regions start blackouts as early as 16:00. During shutdowns, only whitelist
  domains work — **no VPN helps**.
- **QUIC / UDP 443 is effectively blocked or heavily throttled**; broad throttling degrades HTTP/1.1,
  HTTP/2 and HTTP/3 alike. This directly threatens UDP‑only transports (below).
- **Telegram** restrictions escalated from 2026‑02‑10; **WhatsApp** throttled since late 2025; the
  state messenger **Max** is mandated. From 2026‑05, telecoms may bill international mobile traffic
  over 15 GB/mo. Providers were ordered to actively detect + block VPN users by 2026‑04‑15 (IP
  matching, parallel RU‑vs‑foreign probes, GPS/base‑station correlation).
  ([zona.media](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026),
  [wikipedia: Internet censorship in Russia](https://en.wikipedia.org/wiki/Internet_censorship_in_Russia),
  [OSW](https://www.osw.waw.pl/en/publikacje/analyses/2026-04-17/russia-blocks-telegram-and-cracks-down-vpns))

### 1.6 Protocol scorecard (mid‑2026, Russia)

| Transport | Status vs TSPU | Notes / why | Fork support |
|---|---|---|---|
| **VLESS + Reality + XTLS‑Vision** (TCP 443, allow‑listed SNI) | **Best survivor** | Vision hides the flow fingerprint; Reality borrows a real cert so JA3/SNI look legit; detection of well‑deployed Reality from residential‑looking IPs reported <5%. **Requires** allow‑listed `dest` + non‑DC IP + Vision to beat the 25‑packet freeze. | ✅ `realitySettings`, `flow`, `fingerprint`, `mldsa65Verify` in `V2rayConfig` |
| **Generic VLESS + TLS** (no Reality) | **Degraded / blocked** | Being actively blocked at the TLS layer since late 2025; hit by the 25‑packet freeze. | ✅ |
| **Shadowsocks‑2022** (blake3 AEAD) | **Partial** | High‑entropy stream is ML‑flaggable; "SS‑like" heuristics unstable on the big‑three carriers **without a plugin**; better on high ports / behind TLS‑ish plugin. | ✅ `SHADOWSOCKS` |
| **Shadowsocks + v2ray‑plugin (WS+TLS) / ShadowTLS** | **Good** | WebSocket‑in‑TLS looks like an ordinary HTTPS site; defeats entropy heuristics. | ⚠️ SS ✅; WS+TLS via CUSTOM template |
| **Trojan (TLS)** | **Degraded** | Same TLS‑layer freeze as generic VLESS unless the endpoint is CDN/allow‑listed. | ✅ `TROJAN` |
| **VMess + WS + TLS + CDN** | **Situational** | Survives if fronted behind a still‑reachable CDN edge; suffers from Cloudflare pressure (§1.7). | ✅ `VMESS` |
| **Hysteria2 / TUIC (QUIC/UDP)** | **Fragile in RU** | UDP 443 + QUIC broadly throttled/blocked; great throughput **when** UDP passes (often only on some fixed ISPs), poor on mobile. Keep as a fallback, never the default. | ✅ `HYSTERIA2`; `TUIC` present but commented out in `EConfigType` |
| **AmneziaWG 2.0** (obfuscated WG, junk/padding, QUIC/DNS mimicry) | **Good where UDP passes** | Randomizes WG's fixed sizes/headers, adds junk packets, can mimic a QUIC Initial with an allow‑listed SNI to break AS‑level cuts. Still UDP — shares QUIC‑throttling risk. | ❌ not in fork (WireGuard only) |
| **Raw WireGuard / OpenVPN** | **Blocked** | Fixed sizes/handshakes → ML‑classified in ~100 packets. | ✅ WG (not recommended for RU) |
| **mKCP (UDP obfuscation headers)** | **Fragile** | UDP‑based → throttling; header camouflage helps little vs ML. | ✅ (network=`kcp`) |

Sources for the scorecard: [tgvpn](https://tgvpn.io/en/tspu-dpi-russia-2030-analysis.html),
[net4people #490](https://github.com/net4people/bbs/issues/490),
[greatfirewallguide lab](https://greatfirewallguide.com/lab),
[valebyte SS‑2022](https://valebyte.com/en/blog/shadowsocks-2022-on-vps-setup-and-bypassing-blocks-in-2026/),
[nvovpn SS 2026](https://nvovpn.com/en/news/shadowsocks-nastroika-i-podkliucenie-v-2026-godu),
[ghacks AmneziaWG 2.0](https://www.ghacks.net/2026/03/25/amnezia-releases-amneziawg-2-0-to-bypass-advanced-internet-censorship-systems/),
[Amnezia docs](https://docs.amnezia.org/documentation/amnezia-wg/),
[mhioul TUIC/Hysteria2](https://www.mhioul.com/blog/tuic-hysteria2-udp-protocols).

### 1.7 CDN / domain‑fronting / ECH status
- **Cloudflare ECH is blocked** in Russia since 2024‑11‑05: the trigger is the pair `SNI ==
  cloudflare-ech.com` **plus** an ECH extension in the ClientHello. RKN publicly told site owners to
  stop using Cloudflare; Cloudflare RU traffic fell ~30%. So **ECH is not a reliable bypass** and can
  itself be a flag. ([net4people #417](https://github.com/net4people/bbs/issues/417),
  [therecord](https://therecord.media/russia-blocks-thousands-of-websites-that-use-cloudflare-service),
  [risky.biz](https://news.risky.biz/risky-biz-news-russia-blocks-cloudflare-ech-connections/))
- **Classic domain fronting** (mismatched SNI vs Host behind a big CDN) is largely dead at major
  CDNs; **CDN + WS/gRPC + TLS** to a still‑reachable edge can work but is brittle and provider‑led,
  not client‑led. Treat CDN as **one** operator‑side option, not the client default.

### 1.8 How popular clients cope (what to emulate)
- **Happ** — client‑only; intelligence lives in the operator's bot/panel. Ships **subscription
  templates + routing profiles** and **encrypted/"hidden" subscriptions** so the operator can rotate
  camouflage server‑side and hide node details. (Already the reference for our auth + hidden‑template
  docs.) ([happ.su dev‑docs](https://www.happ.su/main/dev-docs/app-management))
- **Hiddify** — auto‑converts subscription formats, **auto‑updates**, and its manager pushes new
  configs when the admin rotates servers; emphasizes "if one protocol dies, switch." ([Hiddify](https://hiddify.com/))
- **NekoBox / sing‑box** — broadest protocol set (SS, VMess, VLESS, Trojan, Hysteria1/2, TUIC, WG,
  **AnyTLS, ShadowTLS, NaïveProxy, Mieru**) and per‑node URL testing; the "many camouflages" model.
  ([grokipedia: NekoBox](https://grokipedia.com/page/NekoBox_for_Android))
- **v2rayNG** (our base) — has **Fragment** (splits the TLS ClientHello), Mux, per‑config test; lacks
  automatic cross‑protocol failover. ([Hiddify wiki: v2rayNG](https://github.com/hiddify/Hiddify-Manager/wiki/Tutorial-for-V2rayNG-app))
- **Amnezia** — ships **AmneziaWG** obfuscation + VLESS‑Reality; self‑host focus, per‑install unique
  obfuscation params. ([Amnezia docs](https://docs.amnezia.org/documentation/amnezia-wg/))

**Common winning pattern:** *thin client + operator‑controlled camouflage + fast rotation + auto
fallback + resilient subscription delivery.* Our fork already has most plumbing (subscription import/
update, Fragment, Reality, hidden templates); the gap is **automation, defaults, and delivery
resilience**, which §2–§3 target.

---

## 2. Feature recommendations for THIS app (prioritized)

Priorities: **P0** = highest survivability leverage, mostly reuses existing plumbing; **P3** = polish.
Each item says *why it helps under TSPU*. File references are to the fork's actual tree.

### P0 — Survivability core (do first)

1. **Automatic protocol fallback / failover chain.**
   On connect, if the selected node fails to establish or dies mid‑session (the 25‑packet freeze
   presents as "connects then stalls"), automatically try the next candidate in a ranked order
   (§3.1) **without user action**. Today the fork can *test* nodes (`handler/SpeedtestManager.kt`,
   `MainViewModel`) but connection is a manual single pick and services only restart the same config
   (`service/CoreVpnService.kt`, `service/TProxyService.kt`).
   *Why:* the #1 RU failure mode is silent stall; a human notices minutes later. Auto‑failover turns a
   dead node into a 3–5 s blip. This is the single biggest UX + survivability win.

2. **Reality‑first, allow‑listed‑SNI defaults + a "Russia mode" profile.**
   Ship a one‑switch **Russia mode** that: prefers VLESS+Reality+**XTLS‑Vision** (`flow =
   xtls-rprx-vision`), enforces `fingerprint` (uTLS) = a current browser (chrome/firefox), and — for
   operator subs — nudges `serverName`/`dest` toward known **allow‑listed** RU‑reachable SNIs. All
   fields already exist in `dto/V2rayConfig.kt` (`realitySettings`, `fingerprint`, `mldsa65Verify`)
   and `dto/entities/ProfileItem.kt` (`flow`, `security`, `fingerPrint`).
   *Why:* Reality+Vision from a non‑DC IP with an allow‑listed SNI is the best current survivor (§1.6);
   Vision specifically defeats the flow/packet‑count fingerprint behind the freeze.

3. **Fragment ON by default in Russia mode, tuned for the freeze.**
   `PREF_FRAGMENT_ENABLED/PACKETS/LENGTH/INTERVAL` already exist. Default them on with
   ClientHello‑splitting values, and expose a **preset** ("Aggressive DPI / Russia") so users don't
   hand‑tune. Consider surfacing fragment in the Russia‑mode toggle rather than buried in Settings.
   *Why:* fragmenting the ClientHello frustrates SNI extraction and the ClientHello‑corruption /
   freeze heuristics; it's the community‑proven first‑line DPI evasion for TLS transports.

4. **uTLS fingerprint enforcement + rotation.**
   Ensure every TLS/Reality outbound carries a **non‑empty, browser‑matching `fingerprint`**; default
   to a modern Chrome profile, allow randomization per‑connection. Validate that operator templates
   never ship empty SNI/fingerprint for RU.
   *Why:* JA3/JA4 fingerprinting is a core detection layer; a mismatched or missing uTLS profile is a
   direct flag.

5. **Resilient subscription fetch (censorship‑resistant delivery).**
   Harden `handler/AngConfigManager.updateConfigViaSub` / `SubscriptionUpdater`: multiple **mirror
   URLs** per subscription, fetch **through the active tunnel when one exists** (bootstrapping), retry
   with backoff, and fall back to **out‑of‑band delivery** (Telegram bot payload per
   `telegram-auth-design.md`, `happ://crypt` encrypted links, QR, clipboard). See §3.4.
   *Why:* the subscription URL is itself a censored HTTPS endpoint; if users can't refresh configs
   after a block wave, the app is dead even though working servers exist.

### P1 — Strong hardening

6. **Health‑check + auto‑rotate loop while connected.**
   Periodic lightweight reachability probe (small request to a neutral target) detects the freeze and
   triggers failover (#1) proactively, before the user sees a stall.
   *Why:* the freeze is silent; passive health‑checking converts it into automatic rotation.

7. **Add AmneziaWG (obfuscated WireGuard) as a transport.**
   New `fmt/AmneziaWgFmt.kt` + core support (junk packets `Jc/Jmin/Jmax`, magic headers `S1/S2/H1‑H4`,
   QUIC/DNS mimicry). Offer as a **UDP‑path alternative** where UDP passes.
   *Why:* broadens camouflage diversity (the NekoBox lesson); AmneziaWG 2.0's per‑install unique params
   + QUIC‑Initial mimicry can punch through some AS‑level cuts. Caveat: still UDP, so gate it behind a
   UDP‑reachability check (§3.1) given QUIC throttling.

8. **Kill switch (block‑on‑disconnect) + "no leak on failover".**
   True kill switch so that during a failover gap or tunnel drop, **no traffic egresses in the clear**
   (Android `setBlocking`/lockdown semantics on the `VpnService`). Critical because providers now
   actively detect the *user's real IP* leaking during drops (2026‑04‑15 mandate, §1.5).
   *Why:* prevents a momentary clear‑text leak from de‑anonymizing / flagging the user during exactly
   the rotation events this app will trigger often.

9. **Split tunneling defaults tuned for RU whitelist.**
   Per‑app proxy already exists; ship a **routing preset** that sends whitelist / socially‑significant
   RU domains (banks, gosuslugi, Max, Yandex) **direct**, and everything else through the tunnel. Pair
   with the hidden‑template routing engine (`hidden-templates-design.md`).
   *Why:* reduces tunnel load, avoids breaking RU banking/2FA, and keeps essential services working
   even during partial throttling — the exact UX Russians need day‑to‑day.

10. **On‑demand / Always‑On + auto‑reconnect after mobile‑shutdown windows.**
    Reconnect logic that survives the frequent mobile blackout → restore cycles without manual
    re‑tap; back off during total blackouts (nothing works) and re‑probe on network change.
    *Why:* daily mobile shutdowns make manual reconnection a constant chore; graceful auto‑recovery is
    a major UX differentiator.

### P2 — Diversity & UX

11. **Per‑node + per‑protocol "real" connectivity test (not just TCP ping).**
    Extend `SpeedtestManager` to do an actual handshake‑and‑payload test past the 16 KB freeze
    threshold, so a node that *pings* but *freezes* is scored as dead. Feed results into the failover
    ranking (#1).
    *Why:* TCP ping passes the freeze; only a >16 KB real test reveals the block. Ranking on real
    reachability is what makes auto‑failover pick a *working* node.

12. **Shadowsocks‑2022 + plugin (WS/TLS or ShadowTLS) template support.**
    Ensure operator CUSTOM/JSON templates carrying SS‑2022 behind a TLS‑ish plugin import and run
    cleanly (they already flow through `parseCustomConfigServer`).
    *Why:* gives a non‑TLS‑Vision fallback family that still looks like HTTPS, useful when Reality SNIs
    get burned.

13. **Multiple subscription groups + auto‑merge / de‑dupe with per‑group failover.**
    Let a user hold several operator subs (primary + backup mirrors) and fail over across *groups*, not
    just nodes.
    *Why:* operators rotate; holding backups from a second channel is resilience against a single
    channel being blocked.

14. **Diagnostics that name the RU failure mode.**
    A "Why can't I connect?" panel that distinguishes: DNS blocked / TLS freeze detected / UDP blocked
    (QUIC) / total mobile shutdown / subscription unreachable — with the right suggested action each.
    *Why:* turns opaque failures into actionable guidance; reduces support load and user churn.

### P3 — Polish

15. **Randomized connection timing / anti‑probing hints in templates** (avoid identical retry cadence),
    **warp/noise defaults**, and **battery‑aware health‑check intervals**.

---

## 3. Anti‑censorship connection strategy

### 3.1 Protocol auto‑selection & fallback order (Russia mode)
Rank candidates and fail over in this order (skip any the network proves unusable):

1. **VLESS + Reality + XTLS‑Vision**, TCP 443, allow‑listed `serverName`/`dest`, browser uTLS
   fingerprint, **Fragment on**. *(Primary — best survivor.)*
2. **VLESS + Reality + Vision on a high non‑standard port** (47000+), where shallow inspection lets
   ~80% of packets through. *(Secondary.)*
3. **Shadowsocks‑2022 behind WS+TLS / ShadowTLS** (looks like HTTPS). *(TLS‑family fallback.)*
4. **VMess/Trojan + WS + TLS to a still‑reachable CDN edge** (operator‑provided). *(CDN fallback.)*
5. **AmneziaWG 2.0** — *only if a UDP‑reachability probe passes.* *(UDP path.)*
6. **Hysteria2 / TUIC** — *only if UDP/QUIC probe passes;* excellent throughput when it does.
   *(Last‑resort UDP.)*

Rules of the road:
- **Probe UDP once per network** (cheap QUIC/UDP reachability check). If it fails, **drop 5–6 from the
  chain entirely** rather than wasting failover time — QUIC/UDP 443 is broadly throttled (§1.5).
- **Rank by real reachability** (§2 #11), not TCP ping — a node that stalls at 16 KB must sort below a
  node that streams.
- **Failover budget:** try next candidate after ~4–6 s of stall or handshake failure; cap total
  attempts, then surface the diagnostics panel (§2 #14).
- **Sticky success:** once a node/protocol works on a given network, pin it and re‑probe alternatives
  only on failure or network change (avoids thrash + battery drain).

### 3.2 Obfuscation defaults (Russia mode)
- **Fragment: ON**, ClientHello‑split preset ("Aggressive DPI"). (`PREF_FRAGMENT_*`.)
- **Security: `reality`** where the node supports it, else `tls`; **never empty SNI** for RU.
- **`flow = xtls-rprx-vision`** on Reality nodes.
- **Mux: default OFF for Reality/Vision** (Vision manages its own multiplexing; Mux can *hurt* here),
  ON only where a template calls for it. (`PREF_MUX_ENABLED`, `MuxBean`.)
- **mKCP/QUIC noise** only on UDP transports and only when UDP is proven reachable.
- **Post‑quantum:** honor `mldsa65Verify` when operator ships it (future‑proofs Reality key exchange).

### 3.3 TLS fingerprinting policy
- **Always send a non‑empty, browser‑matching uTLS `fingerprint`** (default modern Chrome; allow
  Firefox/Safari/random). Empty or exotic fingerprints are JA3/JA4 flags.
- **SNI must be a real, allow‑listed, RU‑reachable host** for Reality `dest` — validate operator
  templates and warn if a locked sub ships an SNI that is empty, obviously fake, or a known‑burned
  domain. Keep a small, updatable **allow‑listed‑SNI hint set** (delivered via subscription, not
  hardcoded) so it can track RKN changes.
- **Avoid ECH** for RU targets — the `cloudflare-ech.com` + ECH‑extension pair is a block trigger
  (§1.7). Do not enable ECH by default in Russia mode.

### 3.4 Subscription‑fetch resiliency (censorship‑resistant delivery)
The subscription endpoint is itself a censored HTTPS URL; make delivery multi‑path (builds on
`telegram-auth-design.md` §2–3 and `hidden-templates-design.md` §2):

1. **Mirror URLs:** each `SubscriptionItem` carries an ordered list of mirror hosts; try in turn with
   backoff. (Today `updateConfigViaSub` has a single URL + a proxy‑retry.)
2. **Fetch through the tunnel when one is up:** if any node is connected, pull the next update *inside*
   the tunnel so a blocked bare fetch still succeeds (bootstrap resilience).
3. **Out‑of‑band fallbacks, in priority order:**
   (a) **Telegram bot payload** — the bot returns the sub URL / encrypted config directly (per the
   auth doc's poll response), so config delivery rides Telegram, not a blockable CDN;
   (b) **`happ://crypt4/5` encrypted deep links** (hidden‑templates doc) pasted or scanned;
   (c) **QR / clipboard** manual import as the always‑works escape hatch.
4. **Cache last‑good configs** and never wipe them on a failed refresh — a user mid‑block must keep the
   servers they have. (Current refresh replaces profiles; guard against replacing with an empty/failed
   fetch.)
5. **Randomize update cadence** and use a neutral, non‑VPN `User-Agent` option for fetches to avoid
   fetch‑pattern fingerprinting.
6. **Domain‑agility for the backend:** operator should rotate subscription/API hosts; the app reads
   host from config (`BackendConfig`) so rotation is a pushed change, not an app update.

### 3.5 DNS strategy
- Use **DoH/DoT to foreign resolvers** (1.1.1.1, 8.8.8.8 remain reachable per §1.5) for the tunnel's
  remote DNS; keep a **domestic resolver for direct/whitelist domains** to avoid breaking RU banking.
- Enable **FakeDNS** (`PREF_FAKE_DNS_ENABLED`) for the proxied path to avoid leaking real lookups.
- Never resolve the proxy server's own domain via a censored resolver at bootstrap — prefer IP‑pinned
  or tunnel‑resolved entries in operator templates.

---

## 4. Risks, legal notes & store distribution

### 4.1 Legal / user‑safety context (not legal advice)
- Russia **bans "means of circumventing" blocks** and, since 2024, **advertising/promoting VPNs**;
  from 2026‑04‑15 providers must **actively detect and block VPN users**, and mobile carriers may bill
  heavy international traffic. Using a VPN is not (yet) itself an individual criminal offense, but the
  legal surface is expanding and enforcement is real. Keep in‑app copy **factual, non‑promotional**,
  and avoid language that markets circumvention.
  ([zona.media](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026),
  [OSW](https://www.osw.waw.pl/en/publikacje/analyses/2026-04-17/russia-blocks-telegram-and-cracks-down-vpns),
  [HRW](https://www.hrw.org/news/2026/03/12/russia-digital-iron-curtain-falls-on-internet-freedom-protection-day))
- **User de‑anonymization risk:** detection now correlates IP, RU‑vs‑foreign probe timing, and
  GPS/base‑station data. The **kill switch (§2 #8)** and no‑leak failover are therefore safety
  features, not just conveniences.
- **Operator‑trust surface:** the hidden‑template model gives the operator full control of routing/DNS
  (see `hidden-templates-design.md` §4). Keep those defensive validations (HTTPS‑only subs, reject
  non‑loopback inbounds, strip vendor directives) — doubly important under a regime that would love a
  malicious template.

### 4.2 Store distribution — the app itself is a target
- **Apple removed 761 VPN apps** from the RU App Store by April 2026 (including Happ, v2RayTun, V2Box,
  Streisand); **Google Play** removed 200+ on RKN request through 2025–26. Assume **neither official
  store is a reliable RU channel.**
  ([zona.media](https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026),
  [techradar: Apple removals](https://www.techradar.com/vpn/vpn-privacy-security/apple-removes-custom-vpn-clients-from-russian-app-store-amid-telegram-crackdown),
  [benzinga: Durov](https://www.benzinga.com/markets/tech/26/04/51591434/telegram-ceo-pavel-durov-slams-apple-for-removing-vpn-apps-russia))
- **Recommended distribution mix (Android‑first, our platform):**
  1. **Direct signed APK** from a rotating set of operator domains + **Telegram channel/bot** delivery
     (the bot already anchors auth; ship the APK there too). Primary RU channel.
  2. **In‑app self‑update** that pulls the APK **through the tunnel** and verifies signature — so an
     installed app can update even when its download domain is blocked. (The fork has a
     `activity_check_update` surface to build on.)
  3. **F‑Droid / IzzyOnDroid** and **GitHub Releases** as reproducible, harder‑to‑pressure mirrors.
  4. **RuStore** is available in‑country but is state‑adjacent — **do not** rely on it for a
     circumvention tool; treat as optional reach, not a trust anchor.
  5. **APK mirroring inside Telegram** (Durov's own point) is currently the most censorship‑resistant
     mass channel while Telegram remains partially reachable — but Telegram itself is under block
     pressure, so keep ≥2 independent channels.
- **Signing / integrity:** stable signing key + in‑app signature check on self‑update; publish
  checksums in the Telegram channel so sideloaders can verify.
- **Brand caution:** avoid store metadata / listing language that reads as "bypass Russian blocks" —
  that invites both store takedown and legal exposure.

---

## 5. Additional prioritized roadmap (complements existing plans)

This roadmap adds the **censorship‑resistance track** alongside the existing three docs. It does *not*
re‑plan auth (`telegram-auth-design.md`), hidden templates (`hidden-templates-design.md`), or UI
(`design-system-2026.md`); it references them and sequences the new work.

**Track R0 — Survivability MVP (highest ROI, mostly existing plumbing)**
- **R0.1** Auto protocol fallback engine (§2 #1, §3.1): candidate ranking + failover in the connect
  path. Touches `service/CoreVpnService.kt`, `core/CoreServiceManager.kt`, `MainViewModel`,
  `SpeedtestManager`. *No new protocol needed.*
- **R0.2** "Russia mode" one‑switch preset: Reality+Vision + uTLS + **Fragment on** defaults (§2 #2–4,
  §3.2–3.3). Touches `AppConfig` prefs, `CoreConfigManager`, a Settings toggle.
- **R0.3** Real‑reachability test past the 16 KB freeze; feed the ranking (§2 #11). Extend
  `SpeedtestManager`.
- **R0.4** Kill switch / no‑leak failover (§2 #8). `VpnService` lockdown semantics.

**Track R1 — Delivery & recovery resilience**
- **R1.1** Multi‑mirror + through‑tunnel + out‑of‑band subscription fetch, never‑wipe‑on‑fail
  (§3.4). Extends `AngConfigManager.updateConfigViaSub`, `SubscriptionUpdater`, `HttpUtil`; ties into
  the Telegram bot payload from the auth doc.
- **R1.2** Health‑check + auto‑rotate loop while connected (§2 #6).
- **R1.3** On‑demand/Always‑On + mobile‑shutdown‑aware auto‑reconnect (§2 #10).
- **R1.4** In‑app self‑update through the tunnel + signature verification; multi‑channel APK delivery
  (§4.2). Builds on `activity_check_update`.

**Track R2 — Camouflage diversity**
- **R2.1** AmneziaWG transport (`fmt/AmneziaWgFmt.kt` + core) behind a UDP‑reachability gate (§2 #7).
- **R2.2** SS‑2022 + WS/TLS/ShadowTLS template validation & presets (§2 #12).
- **R2.3** Multi‑subscription‑group failover (§2 #13).
- **R2.4** Allow‑listed‑SNI hint set delivered via subscription + template SNI/fingerprint validation
  (§3.3).

**Track R3 — Guidance & polish**
- **R3.1** RU‑aware diagnostics panel (§2 #14).
- **R3.2** Split‑tunnel RU‑whitelist routing preset (§2 #9), using the hidden‑template routing engine.
- **R3.3** Randomized timing / anti‑probing / battery‑aware intervals (§2 #15).

**Sequencing note.** R0 is the survivability floor and should ship before the payment stage in
`telegram-auth-design.md §5`. R1.1 should land *with* the Telegram‑bot subscription delivery so config
delivery is censorship‑resistant from day one. R2/R3 iterate as RKN evolves — the whole point is that
adding a transport or rotating an SNI set is a config/subscription push, not an app rewrite.

---

## Sources
- TSPU / DPI architecture & 2026 status — https://tgvpn.io/en/tspu-dpi-russia-2030-analysis.html · https://iplogs.com/blog/russia-tspu-how-it-blocks-vpns · https://www.researchgate.net/publication/364718059_TSPU_Russia's_decentralized_censorship_system
- 2026 censorship overview (shutdowns, whitelist, App Store, Telegram/WhatsApp/Max) — https://en.zona.media/article/2026/04/07/russian_internet_censorship_2026 · https://en.wikipedia.org/wiki/Internet_censorship_in_Russia · https://www.osw.waw.pl/en/publikacje/analyses/2026-04-17/russia-blocks-telegram-and-cracks-down-vpns · https://www.hrw.org/news/2026/03/12/russia-digital-iron-curtain-falls-on-internet-freedom-protection-day
- The "TLS freeze" / 25‑packet block & workarounds — https://github.com/net4people/bbs/issues/490 · https://hub.xeovo.com/posts/132-russia-widespread-vless-outages-due-to-tls-handshake-blockingdegradation-request-tlstransport-hardening-and-anti-probing · https://github.com/XTLS/Xray-core/issues/5332
- VLESS/Reality + allow‑listed SNI configs — https://github.com/igareck/vpn-configs-for-russia/blob/main/Vless-Reality-White-Lists-Rus-Mobile.txt · https://fexyn.com/blog/vless-reality-protocol-guide
- Shadowsocks‑2022 status — https://valebyte.com/en/blog/shadowsocks-2022-on-vps-setup-and-bypassing-blocks-in-2026/ · https://nvovpn.com/en/news/shadowsocks-nastroika-i-podkliucenie-v-2026-godu · https://github.com/net4people/bbs/issues/363
- QUIC/UDP + Hysteria2/TUIC — https://www.mhioul.com/blog/tuic-hysteria2-udp-protocols · https://greatfirewallguide.com/lab
- AmneziaWG 2.0 — https://www.ghacks.net/2026/03/25/amnezia-releases-amneziawg-2-0-to-bypass-advanced-internet-censorship-systems/ · https://docs.amnezia.org/documentation/amnezia-wg/
- Cloudflare ECH / CDN blocking — https://github.com/net4people/bbs/issues/417 · https://therecord.media/russia-blocks-thousands-of-websites-that-use-cloudflare-service · https://news.risky.biz/risky-biz-news-russia-blocks-cloudflare-ech-connections/
- App‑store removals / distribution — https://www.techradar.com/vpn/vpn-privacy-security/apple-removes-custom-vpn-clients-from-russian-app-store-amid-telegram-crackdown · https://www.benzinga.com/markets/tech/26/04/51591434/telegram-ceo-pavel-durov-slams-apple-for-removing-vpn-apps-russia · https://www.techradar.com/vpn/vpn-privacy-security/russias-censor-body-roskomnadzor-wants-to-block-92-percent-of-vpn-apps-by-2030-and-its-investing-20-billion-rubles-a-year-to-build-a-permanent-vpn-censorship-system
- Client comparison — https://grokipedia.com/page/NekoBox_for_Android · https://hiddify.com/ · https://github.com/hiddify/Hiddify-Manager/wiki/Tutorial-for-V2rayNG-app · https://www.happ.su/main/dev-docs/app-management
