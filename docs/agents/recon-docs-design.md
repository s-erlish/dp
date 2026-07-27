# Recon — Design docs distilled (departament VPN / Android)

**Agent:** docs-design recon · **Date:** 2026-07-26 · **Repo:** `/home/user/dp`
**Branch:** `claude/app-audit-agents-hyyftk` · **HEAD:** `bfd05fd`

**Method.** I read all 19 design markdown files named in the task, end to end, plus
`/home/user/dp/CLAUDE.md`. I then verified every "is it built?" claim against the actual
tree (`V2rayNG/app/src/main/res/**`, `V2rayNG/app/src/main/java/com/v2ray/ang/**`). Every
statement below is traceable to a file I opened; where I cite `file:line`, I read that line.
Nothing here is inferred from a doc alone when code could confirm or refute it.

---

## 0. READ THIS FIRST — the authority order of these docs

The 19 docs were **not** written against one continuous design direction. There are two
distinct eras, and mixing them will produce wrong work.

| Era | Docs | Direction | Status |
|---|---|---|---|
| **Era 1 — "white-blue 2026"** | `design-system-2026.md` | Premium **white/light-blue** default theme, `#1E5FC7` primary, glass/liquid-glass Settings, `radius_xs/sm/md/lg/xl` + `space_2/20/48` token names, `BottomNavigationView` migration, gradient connect ring | **SUPERSEDED** on palette/tokens/glass. Still the only source for a few things (responsive buckets, iOS-parity mapping, elevation philosophy). |
| **Era 2 — "Incy 1:1"** | `incy-redesign-spec.md`, `incy-analysis.md`, `incy-settings-design.md`, `incy-repo-findings.md`, `design-home-polish.md`, `design-tg-and-settings-trim.md`, `design-review-c942766.md` | **Dark-first**, near-black `#0A0B0D`, **ONE** bright blue `#4C8DFF`, iOS-style grouped cards, de-carded hero + glow, frameless nav | **CURRENT** |
| **Era 3 — the ruling law** | `/home/user/dp/CLAUDE.md:14-32` | Distilled non-negotiables. **Overrides both eras where they conflict.** | **BINDING** |

Concrete proof this is the real order, from the shipped tree:

- The token names that actually exist are the **Era-2/3** ones —
  `V2rayNG/app/src/main/res/values/dimens.xml:14-19` (`space_4/8/12/16/24/32`) and
  `:22-28` (`radius_chip 12` / `radius_card 20` / `radius_tile 12` / `radius_pill 100` /
  `radius_sheet 24`). The Era-1 names `space_2`, `space_20`, `space_48`, `radius_xs/sm/md/lg/xl`
  **do not exist in the tree at all**.
- The default palette is dark: `values-night/colors.xml:35` `md_theme_background #0A0B0D`,
  `:29` `md_theme_primary #4C8DFF`.
- `CLAUDE.md:24` bans "decorative gradients/glows" and `:23` bans ALL-CAPS eyebrows — both of
  which Era-1 and even parts of Era-2 (`incy-redesign-spec.md:11` "Section labels: UPPERCASE
  grey"; `design-home-polish.md` §3 glow) explicitly asked for. The tree follows CLAUDE.md:
  `values/styles.xml:6-17` `SettingsSectionLabel` is **sentence-case, 16sp, 700, `textAllCaps=false`**.

**Rule for anyone doing UI work here: CLAUDE.md wins → Era-2 Incy docs → Era-1 only for the
gaps Era-2 never covered (responsiveness buckets, iOS mapping).**

---

# (a) THE DESIGN LAW — authoritative, consolidated

Everything in this section is either quoted from `CLAUDE.md` (binding) or is a token that
actually exists in the tree and is consistent with it. Where a doc says something different,
I flag it.

## a.1 Identity

- Product: **departament VPN**, Android, Kotlin + XML Material 3 views, package `com.v2ray.ang`,
  app label `departament` (`design-system-2026.md:3-4`, `incy-analysis.md:4`).
- Brand wordmark: "**departament**" — blue. Brand anchor hex `#1E5FC7`
  (`values/colors.xml:4` `brand_blue`), which in the **dark default** becomes `#4C8DFF`
  (`values-night/colors.xml:4`).
- Brand font: **Space Grotesk**, variable, used at **real weights** via
  `android:textFontWeight` — never `textStyle="bold"` (synthetic bold is banned;
  `values/styles.xml:47-53`).
- UI language: **Russian**, **sentence case** (`CLAUDE.md:16`, `:31`).
- Currency: **₽** (`CLAUDE.md:31`).

## a.2 Colour law

**ONE accent.** Blue. Red only for destructive. (`CLAUDE.md:19-20`.)

This is enforced structurally, not by convention: every accent is a **theme attr**, declared in
`values/attrs.xml:15-42`, so the Mono overlay can neutralise it:

| Attr | Blue theme | Mono theme |
|---|---|---|
| `chipTypeText` / `chipJsonText` / `chipJsonBg` | `values/themes.xml:78-80` | greyscale, `:170-172` |
| `pingGood` / `pingBad` | `:81-82` | `:173-174` |
| `indicatorColor` | `:83` | `:175` |
| `iconTintBlue/Green/Orange/Purple/Yellow` | **all → `@color/icon_blue`** `:88-93` | all → `mono_onSurfaceVariant` `:176-181` |
| `iconTintRed` | `@color/icon_red` `:92` | neutralised `:181` |
| `iconTileBg*` | all → `icon_tile_blue` except red `:94-99` | `mono_surfaceContainerHighest` `:182-187` |
| `connectIdleColor` / `connectActiveColor` / `connectedColor` | `:73-75` — **connected is BLUE, not green** | `:165-167` |

Note the two deliberate collapses vs. the docs:

1. `incy-redesign-spec.md:9` asked for **purple** download and **green** ok. The tree collapsed
   both to blue: `values/colors.xml:16` `color_download #1E5FC7`, `:21` `icon_purple #4C8DFF`,
   `themes.xml:89` `iconTintGreen → icon_blue`. **This is correct per `CLAUDE.md:19` and must not
   be "fixed back".**
2. `design-review-c942766.md` §2.1 flagged "green shield in blue halo". The fix taken was to make
   **connected blue** (`themes.xml:71-75`), not to make the glow green. Finding closed.

**Dark palette (default) — `values-night/colors.xml`:**

| Role | Hex | Line |
|---|---|---|
| background (L0) | `#0A0B0D` | `:35` |
| surface / card (L2) | `#141619` | `:40` |
| surfaceContainerLow (L1) | `#111316` | `:81` |
| surfaceContainerHigh (L3) | `#1A1D21` | `:83` |
| surfaceContainerHighest | `#20242B` | `:84` |
| primary (the ONE accent) | `#4C8DFF` | `:29` |
| onSurface | `#F2F4F8` | `:41` |
| onSurfaceVariant | `#9BA1AD` | `:47` — deliberately lifted from `#8A909C` for AA at 12-13sp |
| outline / outlineVariant | `#2A2E36` / `#20242B` | `:70-71` |
| tertiary (green, semantic only) | `#22C55E` | `:50` |
| error / ping bad | `#F04452` | `:55`, `:24` |

**Depth is expressed by tone, not shadow** — the surface ramp is documented in
`values/themes.xml:37-45` with an explicit rule: *"reference `?attr/colorSurfaceContainerLow|…|High(est)`
— never raw hex — so light/mono resolve automatically."*

**Light theme** (`values/colors.xml`) keeps `#1E5FC7` primary and adds *darkened* accent text
so small type clears 4.5:1 — `chip_type_text #14468F` (`:30`), `chip_json_text #7A5C00` on
`chip_json_bg #F5E6B0` (`:32-33`), `ping_good #0B7D4A` / `ping_bad #C42B32` (`:35-36`).
These exist **because** `design-review-c942766.md` §1.2/§1.3 flagged the gold JSON chip
(~1.8:1) and green ping (~2.9:1) as unreadable in light. Those two majors are closed.

**Contrast floor:** body text ≥ **4.5:1** (`CLAUDE.md:25`).

## a.3 Spacing law

**ONE scale, ONE gutter.** `@dimen/space_4 / 8 / 12 / 16 / 24` (`CLAUDE.md:18`), plus
`space_32` in the tree (`dimens.xml:19`). **16dp screen gutter**, tokenised as
`@dimen/screen_gutter` (`dimens.xml:34`). **No off-scale spacing** (`CLAUDE.md:24`).

Superseded: `design-system-2026.md:147-158` proposed `space_2` (2dp) and `space_48`; and §3.1
set the gutter to 24dp at `sw600dp`. Neither shipped.

## a.4 Radius law

| Token | Value | File |
|---|---|---|
| `radius_chip` | 12dp | `dimens.xml:22` |
| `radius_card` | 20dp | `dimens.xml:23` |
| `radius_tile` | 12dp | `dimens.xml:24` |
| `radius_pill` | 100dp (effective pill) | `dimens.xml:26` |
| `radius_sheet` | 24dp (bottom-sheet top corners) | `dimens.xml:28` |

Superseded: `design-system-2026.md:165-171` (`radius_xs 8 / sm 12 / md 16 / lg 24 / xl 28`) and
`incy-redesign-spec.md:10` ("cards 20–24dp, chips 8dp, colored setting icons 12dp (44dp box)").
The shipped chip radius is **12**, not 8; the tile box is **40dp**, not 44.

## a.5 Component tokens

| Token | Value | File | Doc origin |
|---|---|---|---|
| `tile_size` | **40dp** | `dimens.xml:31` | `CLAUDE.md:21` ("consistent 40dp tiles") |
| `tile_glyph` | **22dp** | `dimens.xml:32` | `CLAUDE.md:21` |
| `row_min_height` | **56dp** | `dimens.xml:33` | `CLAUDE.md:21`; `design-system-2026.md:430` |
| touch target | **≥48dp** | — | `CLAUDE.md:21` |
| `sub_card_height` | 152dp | `dimens.xml:37` | account subscription carousel |
| `dot_size` / `dot_size_active` / `dot_gap` | 6 / 8 / 8dp | `dimens.xml:38-40` | home meta pager dots |

`design-review-c942766.md` §4.5 / §5.5 flagged 36dp header buttons, 28-30dp meta-bar buttons and
34dp flag tiles as below spec. **Those are still to be verified against the current layouts** —
see (c).

## a.6 Type scale

Declared once in `values/styles.xml`, consumed as `android:textAppearance`
(`CLAUDE.md:22` mandates this family):

| Style | Size / weight / font | Line |
|---|---|---|
| `TextAppearance.App.Display` | 34sp / 700 / Space Grotesk, tracking −0.02 | `:56-62` |
| `TextAppearance.App.Headline` | 24sp / 700 / Space Grotesk, −0.01 | `:65-71` |
| `TextAppearance.App.Title` | 16sp / 700 / Space Grotesk, 0 | `:74-80` |
| `TextAppearance.App.Title.Medium` | 16sp / 500 | `:83-85` |
| `TextAppearance.App.Body` | 14sp / system / +0.01 | `:88-92` |
| `TextAppearance.App.Subtitle` | 13sp / system / onSurfaceVariant | `:95-99` |
| `TextAppearance.App.Caption` | 12sp / system / onSurfaceVariant | `:102-106` |
| `TextAppearance.App.Chip` | 11sp / 500 / Space Grotesk / +0.04 | `:109-114` |
| `TextAppearance.App.Numeric` | tabular figures (`"tnum" on, "lnum" on`) — for counters/ping/balance so digits don't jitter | `:122-127` |
| `SettingsSectionLabel` | 16sp / 700 / Space Grotesk / **sentence case, `textAllCaps=false`, letterSpacing 0** | `:6-17` |
| `BottomNavLabel` | 11sp / 500 baseline; **stepped to 700 at runtime when selected** so the active tab reads on two axes | `:22-25` |
| `ToolbarBrandTitle` | 20sp / 700 / Space Grotesk / −0.01 | `:35-41` |

**Section headers are sentence-case bold — NOT tiny ALL-CAPS tracked eyebrows** (`CLAUDE.md:23`).
This directly overrides `incy-redesign-spec.md:11,49` and `incy-settings-design.md:48,297`, which
both specified ALL-CAPS grey headers.

Body-text floor from `design-system-2026.md:204`: body never below 14sp, caption never below 12sp.
The shipped ramp honours this (Body 14, Caption 12).

## a.7 Motion law

Tokenised in `values/motion.xml` — **one tempo for the whole app, ease-out only, no bounce**:

| Token | ms | Line | Use |
|---|---|---|---|
| `motion_press_in` | 90 | `:12` | finger-down acknowledgement |
| `motion_press_out` | 160 | `:15` | release / settle |
| `motion_state` | 220 | `:17` | selection, enable/disable, colour shift |
| `motion_reveal` | 300 | `:19` | show/hide, expand, sheet entrance |
| `motion_stagger` | 40 | `:22` | per-item list stagger (cap total ≈400ms) |
| `motion_emphasis` | 600 | `:25` | the ONE hero moment (connect sonar / assemble) — never chrome |

Reduced motion: honoured at the call site via `util/MotionUtils.kt` — when
`Settings.Global.ANIMATOR_DURATION_SCALE == 0` views jump to the end state
(`values/motion.xml` header comment).

This band (90–300ms, 600 reserved) is consistent with `ux-recommendations.md:58` and `:132`
("motion **100–300 ms**"), and with `CLAUDE.md:26` ("pressed = subtle scale").

## a.8 State law

**Every state designed** (`CLAUDE.md:26`): pressed (subtle scale), selected, disabled, empty,
loading, error. Copy in the interface's voice, active verbs (`CLAUDE.md:27`).

Concrete state tables that the docs already specify and that any new surface should mirror:

- Connect button: `design-system-2026.md:383-390` (disconnected / connecting / connected / error)
  and `incy-redesign-spec.md:20-22` (idle = dim grey-blue shield; connecting = pulsing;
  connected = bright shield + brighter ring; **NOT a solid filled bright button**).
- Subscription meta bar: `subscription-meta-bar-design.md:245-260` — 9 rows
  (Hidden / No metadata / Normal / Near limit ≥0.9 / Unlimited / Expiry / Expired / Updating /
  Pinging / Error). This is the most complete state table in the docs; reuse its shape.
- Empty / loading / offline: `ux-recommendations.md:161-171` — six named states, each
  **one glyph, one sentence, one action**, and "**skeleton screens, not spinners**" for lists
  (`:172`).
- Error taxonomy: `ux-recommendations.md:215-223` — `NoSubscription`, `AllUnreachable`,
  `HandshakeTimeout`, `AuthExpired`, `PermissionDenied`, `NoNetwork`, `CoreCrash`, each with
  {plain cause, primary fix, secondary fix, retryable}.

## a.9 Copy voice

From `ux-recommendations.md:189-206` (the only doc that specifies voice concretely) plus
`CLAUDE.md:27,31`:

- Voice: **calm, plain, trustworthy. Never alarmist, never jargon on Home.** Russian, sentence case.
- Connect ladder: "Tap to connect" → "Connecting…" → "Securing your connection…" → "Connected".
  Idle is **"Not connected"**, *not* "Disconnected" (which reads as failure) — `:191`.
- Connected detail: "Connected · {city} · {ms} ms"; reassurance beat "You're protected." (`:192`).
- Expiry: "Access expires in {n} days" → at ≤3 days red "Your access ends {date} — renew to stay
  connected." action **"Renew"** (`:197-198`).
- Errors lead with **cause + fix**: "Couldn't reach this server. Your network may be blocking it."
  → "Try another server" (`:199-200`).
- **Never** show raw protocol/core errors on Home; `VMess`/`Reality`/exit codes live in diagnostics
  only (`:205-206`).
- Honesty rule for circumvention copy: describe function, never promise a bypass; persistent footer
  "These settings help on some networks and not others…" (`circumvention-settings-design.md:294-298`).
  Russian law restricts *advertising* circumvention (`:294`) — copy must be factual, non-promotional.

## a.10 ABSOLUTE BANS

Verbatim from `CLAUDE.md:24-25`, plus the ones the review doc enforced:

1. **No nested cards.**
2. **No decorative gradients/glows.**
3. **No emoji as UI chrome.**
4. **No off-scale spacing.**
5. Body text contrast **< 4.5:1**.
6. **No ALL-CAPS tracked eyebrow headers** (`:23`).
7. **No synthetic bold on Space Grotesk** — use `textFontWeight` (`styles.xml:47-53`).
8. **No raw hex in layouts for anything themable** — use `?attr/...` so mono/light resolve
   (`themes.xml:43-45`; this was the fix for `design-review-c942766.md` §1.1, the only *blocker*
   in that review).
9. **No `Color.LTGRAY`-style hardcodes** in adapters (`design-review-c942766.md` §4.3).
10. **No second accent hue.** Green/purple/gold/orange all collapse to blue except semantic red
    (`themes.xml:86-99`).

Ban #2 is in direct tension with `design-home-polish.md` (whose entire §1/§3 is a radial glow
canvas + connect bloom) — see §Conflicts below.

## a.11 Accessibility

- ≥48dp touch targets (`CLAUDE.md:21`).
- Body ≥4.5:1, large/icon ≥3:1 (`CLAUDE.md:25`, `design-system-2026.md:311`).
- `contentDescription` on the connect ring and every icon-only control; state changes announced
  via `announceForAccessibility` ("Connected to Amsterdam"); TalkBack focus order
  Home→status→server→speed; honour `fontScale` with autoSize bounds; **reduced motion respected**
  (`ux-recommendations.md:99`).
- RTL: use `start/end`, never `left/right`; mirror the subscription meta bar
  (`design-system-2026.md:471-472`). Locales present: `values-ar`, `values-fa`, `values-bqi-rIR`,
  `values-ru`, `values-bn`, `values-vi`, `values-zh-rCN`, `values-zh-rTW`.
- FA/RU depth: locale-aware byte/date formatting, Persian digit shaping, and **no truncation of
  RU strings (~30% longer — reserve space, avoid fixed-width chips)**
  (`ux-recommendations.md:100`).

---

# (b) Every design decision the owner explicitly asked for

Sourced from the docs' own attributions ("the owner", "the product owner", "the owner likes",
"the owner requested") plus `CLAUDE.md:29-32`, which is itself a roll-up of owner requests.

## b.1 The roll-up in CLAUDE.md:29-32 (binding list)

> "Honor every design request the owner has made across the project (Incy dark + blue, tightened
> profile, tariff badge, ₽ currency, seamless sub-screen toolbar, unified server icon, no ripple
> glow on nav, buy/link-Telegram CTAs, sentence-case Russian copy, etc.)."

Itemised: **(1)** Incy dark + blue · **(2)** tightened profile · **(3)** tariff badge ·
**(4)** ₽ currency · **(5)** seamless sub-screen toolbar · **(6)** unified server icon ·
**(7)** no ripple glow on nav · **(8)** buy / link-Telegram CTAs · **(9)** sentence-case Russian copy.

Corroborated in the commit log at HEAD: `50a2003` "Account tab: wire hero states + subscription
carousel; fix tariff badge", `03a73aa` "Fix tariff badge showing Base for a Plus subscription",
`2b09fd6` "Settings headers sentence-case; remove leftover rim tokens", `fd47c47` "Account polish:
remove stray rim-light borders…".

## b.2 Owner asks recorded inside the docs

| # | Owner ask | Where it is attributed | Status |
|---|---|---|---|
| 1 | **Rebuild the UI 1:1 from 7 Incy screenshots** — "the fork is used only for its core/settings; the UI must be rebuilt to match Incy" | `incy-redesign-spec.md:3-4` | The governing brief |
| 2 | **Dark-only, blue accent, iOS-style grouped cards** | `incy-redesign-spec.md:4` | Shipped (`values-night/colors.xml`) |
| 3 | **Announce banner** — "the owner runs a channel and wants to talk to users in-app" (`Без рекламы на YouTube… @departamentvpn`) | `incy-analysis.md:108` | Built (`layout_subscription_meta_bar.xml` `tv_announce`) |
| 4 | **"Поддержка" support button** to `@departamentvpn` | `incy-analysis.md:109` | Built (`btn_support`, `btn_telegram`) |
| 5 | **Memory card on Home** — "owner cares about a *light* app; honest MB + status reads as quality" | `incy-analysis.md:121` | Built (`activity_main.xml` `card_memory`, `dot_memory`, `tv_memory`) |
| 6 | **Collapse chevron on the sub card** — "owner has 15 servers" | `incy-analysis.md:128` | Built (`btn_collapse`, `btn_collapse_all`) |
| 7 | **Language globe in the top bar** — "owner ships RU+EN; quick locale swap" | `incy-analysis.md:140` | **NOT built** — see (c) |
| 8 | **Subscription PIN/UNPIN** — "the owner has several subs and wants the 'departament' one always first"; called "the flagship" and "Highest owner value" | `happ-parity-details.md:64`, `:188` | Built (`SubscriptionItem.pinned`, `btn_pin`) |
| 9 | **Make the app *feel* like Happ "for a product owner who uses Happ daily"** | `happ-parity-details.md:6-7` | Governing brief for the meta bar |
| 10 | **Rich `sub-info-*` block + expiry "Renew" CTA** — "Monetization / renewal nudge the owner will want" | `happ-parity-details.md:93` | **NOT built** |
| 11 | **Minimal settings: exactly 11 curated top-level rows**, everything else folded behind one "Расширенные настройки (Xray Core)" row — "matching the owner's requested list" | `design-tg-and-settings-trim.md:179-183` | **Partially built, different sections** — see (c) |
| 12 | **Telegram login moved out of the drawer onto Home** | `design-tg-and-settings-trim.md:5-6` | Built (`layout_home_account.xml`, `MainActivity.updateOnboardingLogin():1124`) |
| 13 | **Drawer deleted entirely; "No hamburger anywhere"** | `incy-redesign-spec.md:15`, `design-tg-and-settings-trim.md:140-143` | Built — no `menu_drawer.xml` in `res/menu/`; `menu_bottom_nav.xml` present |
| 14 | **"Проверить" outlined pill on Home** (runs latency test) | `incy-redesign-spec.md:26`, `incy-analysis.md:122`, `design-review-c942766.md` §6.2 | **NOT built** |
| 15 | **Red "Сбросить настройки" at the bottom of Settings** | `incy-redesign-spec.md:60`, `incy-settings-design.md:58,174`, `design-review-c942766.md` §5.1 | **NOT built** |
| 16 | **App-icon chooser (16 icons, Incy parity)** | `incy-redesign-spec.md:53`, `incy-settings-design.md:181,192-202` | **NOT built** |
| 17 | **"Мониторинг памяти" toggle in Settings** that gates the Home memory card | `incy-redesign-spec.md:58`, `incy-settings-design.md:168,212-218` | Pref exists (`PREF_SHOW_MEMORY`), **Settings row missing** |
| 18 | **Ping-methods picker ("Настройки пинга · HTTP GET")** | `incy-redesign-spec.md:56` | Built end-to-end |
| 19 | **Smart-TV support + QR Wi-Fi subscription transfer** | `smart-tv-transfer-design.md:9-22` | Built (`tv/` package, `activity_tv_send/receive.xml`, settings rows) |
| 20 | **Hidden/locked operator templates — user must not view/copy/QR/export the config** | `hidden-templates-design.md:9-14` | Built (`template/TemplateCrypto.kt`, `SubscriptionItem.locked`, gating in `ServerActionsSheet.kt`, `SubEditActivity.kt`) |

---

# (c) Design work SPECIFIED but still UNBUILT

Every row below was checked against the tree. "Verified absent" means I grepped `java/` + `res/`
and got zero hits, or read the layout and the element is not there.

## c.1 Home screen

| Item | Spec | Verified state |
|---|---|---|
| **"Проверить" outlined latency pill** | `incy-redesign-spec.md:26`; `incy-analysis.md:122`; flagged **major** in `design-review-c942766.md` §6.2 | **ABSENT.** `activity_main.xml` ids are `home_root … tv_connection_status, card_memory, dot_memory, tv_memory, …` — no check button. The only `Проверить` string in `res/values/` is `strings_account.xml:63 account_promo_check`, which is the promo-code screen, not this. |
| **Blue wordmark + version + globe in top bar** | `incy-redesign-spec.md:18`; **major** in `design-review-c942766.md` §6.1 | **PARTIAL/ABSENT.** `ToolbarBrandTitle` (`styles.xml:35-41`) still resolves colour to `?attr/colorOnBackground`, not `colorPrimary`. No version TextView and no globe/language action in `activity_main.xml`. |
| **Language globe / in-app locale switcher** | `incy-analysis.md:140`; `ux-recommendations.md:100` (FA users on EN-locale devices) | **ABSENT.** No `AppCompatDelegate.setApplicationLocales` call anywhere; no globe view id. |
| **Selected-server row ≥48dp target** | `design-review-c942766.md` §6.3 (row ≈32dp) | Needs re-measure — `layout_server_info` no longer appears in the `activity_main.xml` id list, so the row was restructured; re-verify against the current hero. |

## c.2 Settings

| Item | Spec | Verified state |
|---|---|---|
| **Red "Сбросить настройки" row + confirm dialog** | `incy-redesign-spec.md:60`; `incy-settings-design.md:174-176`; **major** `design-review-c942766.md` §5.1 | **ABSENT.** `layout_settings_content.xml` ends at the "О приложении" card (last row id `row_url_scheme`). Zero reset strings in `strings_settings_hub.xml`. |
| **Section set doesn't match the Incy spec** | `incy-redesign-spec.md:52-59`; `design-tg-and-settings-trim.md:163-177`; **major** `design-review-c942766.md` §5.2 | **STILL DIVERGENT.** Shipped sections (`layout_settings_content.xml:23,548,760,966,1216,1342`) are **Подключение · Обход блокировок · Интерфейс · Подписка · Устройства · О приложении**. Spec asked for ПРОКСИ ПО ПРИЛОЖЕНИЯМ · ОФОРМЛЕНИЕ · СОЕДИНЕНИЕ · ТУННЕЛЬ · ПРОВАЙДЕРЫ · ПРИЛОЖЕНИЕ · ПРОИЗВОДИТЕЛЬНОСТЬ · ОТЛАДКА. **Note the shipped headers are sentence-case, which is correct per CLAUDE.md:23 and supersedes the spec's ALL-CAPS.** Only the *set/order* is open. |
| **"Мониторинг памяти" toggle row** | `incy-redesign-spec.md:58`; `incy-settings-design.md:212-218` | **ABSENT as a row.** `PREF_SHOW_MEMORY` exists (`AppConfig.kt`, consumed in `MainActivity.kt` + `activity_main.xml`) but no `row_memory` in `layout_settings_content.xml` — the user cannot toggle the card they can see. |
| **"Логи" row → LogcatActivity** | `incy-redesign-spec.md:59`; `design-tg-and-settings-trim.md:174` | **ABSENT.** No `row_logs` id; `LogcatActivity.kt` exists but is unreachable from the hub. |
| **"Оценить приложение"** | `incy-redesign-spec.md:57`; `incy-settings-design.md:161` | **ABSENT.** |
| **"Иконка приложения" + app-icon chooser** | `incy-redesign-spec.md:53`; `incy-settings-design.md:192-202` | **ABSENT.** Zero `<activity-alias>` in `AndroidManifest.xml`, zero `PREF_APP_ICON`. Also blocks `ux-recommendations.md:102`'s "discreet/stealth icon" safety feature. |
| **"Тема" as ONE picker row** (not two toggles) | `incy-redesign-spec.md:53`; `design-review-c942766.md` §5.3 | Shipped as `row_appearance` + `value_appearance` — looks like it was consolidated; verify it is a picker and not two switches. |
| **"Расширенные настройки (Xray Core)" escape row** | `design-tg-and-settings-trim.md:152-155,175` — the whole "FOLD" contract depends on it | **NOT VISIBLE in the hub.** `layout_settings_content.xml` has no row pointing at `SettingsActivity`; `res/xml/pref_settings.xml` still exists and `SettingsActivity.kt` still exists, so ~44 folded prefs may currently be **orphaned/unreachable from the new hub**. This is the highest-risk gap in the settings work — *verify reachability before shipping*. |
| **Disconnect-on-screen-lock toggle** | `incy-redesign-spec.md:58`; `incy-settings-design.md:204-210` | **ABSENT.** No `PREF_DISCONNECT_ON_SCREEN_LOCK`. |

## c.3 Circumvention / bypass settings — **entirely unbuilt**

`circumvention-settings-design.md` is a 315-line spec that has **not landed**:

- `res/xml/pref_bypass.xml` — **does not exist** (`res/xml/` contains only
  `app_widget_provider.xml`, `cache_paths.xml`, `network_security_config.xml`,
  `pref_settings.xml`, `shortcuts.xml`).
- `PREF_UTLS_FINGERPRINT` — **zero hits** in `java/` or `res/`. This is the doc's *one genuinely
  new pref* (`:62-63`), closing a real JA3/JA4 fingerprint leak: profiles whose share link had no
  `fp=` currently run with an **empty** uTLS fingerprint.
- `PREF_BYPASS_PRESET` — **zero hits**. None of the 4 presets (Standard / Russia strict-DPI /
  Iran / Low-latency, table at `:105-118`) exist.
- `handler/BypassPresets.kt` — **does not exist**.
- The single config-generation change the doc asks for (`core/CoreOutboundBuilder.kt`
  `populateTlsSettings`, node `fp=` wins, global pref fills the empty case — `:241-247`) is not applied.

What *did* ship instead: a "Обход блокировок" section in the hub with `row_mux`, `switch_mux`,
`row_mux_concurrency`, `row_fragment`, `switch_fragment` — i.e. the raw Tier-1 toggles, but with
**no presets, no fingerprint pref, no `?`-help copy, no reconnect badge**.

## c.4 Subscription directive capture — partial

| Directive | Spec | Verified |
|---|---|---|
| `announce`, `support-url`, `profile-web-page-url`, `profile-title` | `happ-parity-details.md:33-36`, `incy-analysis.md:80-86` | **Built** — `util/HttpUtil.kt:273-280` reads all four + `profile-hidden`/`hidden`; persisted at `handler/AngConfigManager.kt:818-828` with `decodeSubDirective` handling `base64:` and `"0"`-clears (`:711-719`). |
| **`#body` directive fallback scan** | `incy-analysis.md:88` and `incy-repo-findings.md:92-94` both state HTTP header wins but `#directive:` in the body is a **required fallback** for static-nginx hosting; `happ-parity-details.md:49-52` warns the departament Remnawave panel *may* emit `#announce:` lines | **ABSENT.** `HttpUtil.kt` only reads `response.header(...)`. There is no `util/HappDirectives.kt` / `IncyDirectives.kt`. A panel that emits body directives gets silently ignored. |
| **URL-safe base64 alphabet** | `incy-repo-findings.md:95` / `incy-analysis.md:54` — `base64:` accepts **std *and* URL-safe (`-`/`_`)** | **INCOMPLETE.** `AngConfigManager.kt:719` uses `android.util.Base64.DEFAULT`, which rejects `-`/`_`. A URL-safe payload throws and the directive is dropped. |
| **`expire` ms→s auto-convert when `> 32e9`** | `incy-analysis.md:69,155`; `incy-repo-findings.md:114` | **ABSENT.** `util/SubscriptionUserInfo.kt` `parse()` assigns `expire = map["expire"] ?: 0` with no guard. A panel emitting milliseconds renders a year-5000 expiry. **Two-line fix, real bug.** |
| **`subscription-userinfo: 0` hides the traffic block** | `incy-repo-findings.md:115` | **ABSENT.** |
| `announce-url` (clickable-link notice) | `incy-analysis.md:58,84`; `incy-repo-findings.md:65` | **ABSENT** — zero hits for `announceUrl`. |
| `profile-description` (subtitle) | `incy-analysis.md:66`; `incy-repo-findings.md:58` | **ABSENT** as a captured field. |
| `sort-order: ping\|name\|none` | `incy-analysis.md:67`; `incy-repo-findings.md:66` | **ABSENT** — zero hits. |
| `profile-update-interval` (hours) | `happ-parity-details.md:37,96`; `incy-repo-findings.md:59` | **ABSENT** from the capture struct. |
| `sub-info-text/-color/-button-*`, `sub-expire*` renew CTA | `happ-parity-details.md:39-40,93` | **ABSENT.** |
| `hide-url`, `premium-url`, `support-email`, `banner-*`, `per-app-proxy-*`, `fragmentation-*`, `noises-*`, `server-address-resolve-*` | `incy-repo-findings.md:64-90` — full table, called out at `:241-249` as "entirely absent from our docs" | **ABSENT.** Lowest priority; catalogued for completeness. |

## c.5 UX-recommendations — mostly unbuilt

| Item | Doc | Verified |
|---|---|---|
| **P0-1 3-screen first-run onboarding** before login | `ux-recommendations.md:73` | **ABSENT.** No `OnboardingActivity`. (`Onboarding` hits in `MainActivity.kt` / `layout_home_account.xml` are the *login CTA* — `updateOnboardingLogin()` at `MainActivity.kt:1124` — not a first-run flow. Desktop `/home/user/v2rayN` **does** have `OnboardingView`; this is a phone/desktop parity gap.) |
| **P0-2 staged connect status** (Preparing → Handshaking → Testing route → Connected) | `:74` | **ABSENT.** `applyRunningState(isLoading, isRunning)` at `MainActivity.kt:1523` is binary + `applyConnectedState:1552` / `applyIdleState:1619`. No sub-states. |
| **P0-4 `ConnectError` taxonomy + recovery sheet** | `:76`, `:215-223` | **ABSENT.** Zero hits for `ConnectError`. |
| **P0-5 kill-switch guidance cards** (deep-link to `Settings.ACTION_VPN_SETTINGS`, state detection, state the kill-switch ⊕ split-tunnel exclusivity) | `:77` | **PARTIAL.** `row_always_on` exists in the hub; no exclusivity copy, no state detection. `happ-parity-details.md:86` separately flags "a true kill switch / block-outside-tunnel pref" as a **gap**. |
| **P1-1 in-place server hot-swap sheet from Home** | `:84`, signature moment `:140-143` | **ABSENT.** `showServerActions()` at `MainActivity.kt:619` is the per-item sheet, not a swap picker. |
| **P1-2 Quick Settings tile + widget** | `:85` | **BUILT** — `service/QSTileService.kt`, `receiver/WidgetProvider.kt`, `res/xml/app_widget_provider.xml`. |
| **P1-3 trusted/untrusted Wi-Fi auto-connect** | `:86` | **ABSENT.** No `NetworkMonitor`. |
| **P1-7 "Fastest / Auto" pseudo-server + favourites** | `:90` | Balancer exists (`EConfigType.POLICYGROUP`, `happ-parity-details.md:169-177`) but no "Hybrid (Автовыбор)" first row / favourites star. |
| **P2-4 accessibility pass** | `:99` | **ABSENT** at the announce layer — zero hits for `announceForAccessibility`. Haptics **are** built (`util/MotionUtils.kt`). |
| **P2-6 skeleton empty/loading states** | `:101`, `:161-172` | **PARTIAL.** Skeletons exist only on `activity_buy_tariff.xml` / `activity_account.xml` (`BuyTariffActivity.kt`, `AccountFragment.kt`). Home/server list still lack the six named states. |
| **P2-7 stealth app icon + name alias** | `:102` | **ABSENT** (blocked by the same missing `activity-alias`, c.2). |
| **Low-RAM / power-save auto-degrade of motion + glass** | `:251-252`; `design-system-2026.md:353-356` | **ABSENT.** Zero hits for `isLowRamDevice` / `isPowerSaveMode`. |
| **`onTrimMemory` + central `trimCaches`** | `memory-panel-design.md:113-116,158` | **ABSENT.** Zero hits for `onTrimMemory`; `AngApplication.kt` does not implement it. `util/MemoryStatsManager.kt` **is** built (the panel half of that doc landed; the *discipline* half did not). |

## c.6 Responsiveness / form factor

| Item | Doc | Verified |
|---|---|---|
| `values-sw600dp` bucket, 560dp max card width, `NavigationRail`, two-pane | `design-system-2026.md:450-466` | **ABSENT.** Only `values-sw360dp-v13/` exists, and it contains just two AndroidX-preference overrides (`values-preference.xml`) — not the spec's ring/button/gutter overrides. |
| `values-sw360dp` small-phone bucket (ring 156dp / button 120dp / gutter 12dp) | `design-system-2026.md:451` | **ABSENT.** |
| `layout-land` landscape hero | `design-system-2026.md:468`; `smart-tv-transfer-design.md:155-157` | **ABSENT.** `ls -d layout-*` → none. Relevant because TV is always landscape. |
| TV overscan margins (5%), 10-foot text sizes, focus highlights, D-pad `nextFocusUp/Down` | `smart-tv-transfer-design.md:145-158,336-344` (Phase A) | **UNVERIFIED / likely absent** — no `values-television` or `layout-television` qualifier exists. The transfer feature (Phase B) shipped; the **TV UI adaptation (Phase A) did not**. |

## c.7 Glass / liquid-glass Settings

`design-system-2026.md` §4 (`:316-373`) specifies a full three-tier glass system
(RenderEffect blur API 31+, `FLAG_BLUR_BEHIND` on sheets, tint alphas, low-RAM/power-save
auto-drop), behind a "Glass surfaces" toggle.

**Verified: essentially abandoned.** Zero hits for `RenderEffect`. `glass` appears only in
`values/colors.xml` and `activity_settings.xml` (a leftover `bg_settings_glass`-era reference).
`design-home-polish.md:85-98` explicitly re-litigated and **rejected** live blur
("ship §1 gradient + §3 bloom as the primary, universal treatment… skip [blur] as primary"),
and `CLAUDE.md:24` then banned decorative glows outright. **Treat glass as dead unless the owner
revives it.**

---

## Conflicts the next agent MUST resolve before touching UI

1. **Glow vs. the glow ban.** `design-home-polish.md` is a whole doc about adding
   `bg_home_gradient.xml` (radial `#1B2D50 → #0E141F → #0A0B0D`, `:41-57`) and
   `bg_connect_glow.xml` (radial `#594C8DFF → #264C8DFF → #004C8DFF`, `:229-243`).
   `CLAUDE.md:24` says **"No decorative gradients/glows."** Both drawables **exist and are wired**
   (`res/drawable-night/bg_home_gradient.xml`, `bg_connect_glow.xml`, referenced from
   `activity_main.xml` + `MainActivity.kt`), and the hero has `view_connect_glow`,
   `view_connect_ring`, `view_connect_pulse`. **Reading:** the connect hero is the one sanctioned
   exception (it is the single hero moment, cf. `motion_emphasis` "reserve for the single primary
   action"). Do **not** propagate glow to any other surface, and do not remove the hero glow
   without asking.

2. **Bottom nav: pill indicator or not.** `incy-redesign-spec.md:14-15` demands a "blue rounded-pill
   indicator behind icon+label"; `design-home-polish.md:277-347` demands the exact opposite
   ("frameless… no pill behind the active item… `itemActiveIndicatorStyle=@null`");
   `design-review-c942766.md` §3.1 filed the missing pill as a **major**. The tree resolved this a
   third way: a **hand-rolled nav** (`activity_main.xml` ids `nav_home/nav_home_icon/nav_home_label/
   nav_home_dot` ×4) using a **dot** indicator plus a runtime weight step (`styles.xml:19-25`).
   `BottomNavIndicator` (`styles.xml:27-32`) is now **dead code referencing a raw
   `@color/md_theme_primaryContainer`** — delete it or it will leak colour into mono if reused.
   Also `CLAUDE.md:31` says "**no ripple glow on nav**" — that's the owner's ruling; the frameless
   reading wins.

3. **Settings section taxonomy.** Three competing lists exist
   (`incy-redesign-spec.md:52-59`, `design-tg-and-settings-trim.md:163-177`, and what shipped).
   Pick one and write it down. My recommendation: keep the shipped Russian sentence-case headers
   (CLAUDE-compliant) and only close the *content* gaps in c.2.

4. **Mono-dark idle connect colour — real defect.** `values/themes.xml:165` maps
   `connectIdleColor → @color/mono_fab_inactive`, defined as `#C7C7CC` at
   `values/colors.xml:120`. **`values-night/colors.xml` never overrides `mono_fab_inactive`**
   (verified: `grep -c` → 0). So in Mono + Dark the *idle* shield renders near-white on
   `#000000`, i.e. brighter than the connected state (`mono_connected #FFFFFF`,
   `values-night/colors.xml:105`). Idle and connected are visually indistinguishable. Add a dark
   `mono_fab_inactive` (something in the `#3A3A3D`–`#5A5A5E` band).

5. **`design-tg-and-settings-trim.md` is corrupt at the tail.** The file ends at line 280-281 with
   literal `</content>` / `</invoke>` XML from a tool call, not markdown. Content itself is intact;
   just be aware the last two lines are junk.

6. **`happ-parity-details.md` has a stray fenced block** — line 209 is a bare ` ``` ` before the
   footnote at `:211`. Cosmetic.

---

## Doc-by-doc index (what each file is *for*)

| Doc | Lines | Use it for | Don't use it for |
|---|---|---|---|
| `design-system-2026.md` | 535 | Responsive buckets (§6), iOS-parity token mapping (`:474-480`), elevation philosophy (§3.3), screen inventory (§2) | Palette, spacing/radius token names, glass — all superseded |
| `incy-redesign-spec.md` | 73 | The 1:1 Incy brief: home layout order, servers tab, settings sections, staging S1–S5 (`:68-73`) | ALL-CAPS headers, purple/green accents, 8dp chips, 44dp tiles — superseded |
| `incy-analysis.md` | 205 | Incy announce/support/web protocol exactness; prioritized P0–P3 catalog (`:105-145`); top-8 (`:165-176`) | — |
| `incy-repo-findings.md` | 283 | **The complete Incy header table** (`:53-90`), body-directive table (`:99-110`), crypt1 wire format (§4), 20 icon-preset keys (`:177-186`) | Nothing here is UI design; it's protocol |
| `incy-settings-design.md` | 394 | Option A vs B build decision for Settings (`:69-113`); Incy-setting → our-pref map (§3); new-feature feasibility (`:180-186`) | Its ALL-CAPS headers + 8dp/32dp tiles |
| `design-home-polish.md` | 405 | Exact home canvas/glow/nav hex + file targets (`:383-405`) | Its pill-removal reasoning conflicts with `incy-redesign-spec` — see Conflicts |
| `design-tg-and-settings-trim.md` | 281 | The **complete FOLD/KEEP/REMOVE map of every pref** (`:186-256`) — the single best reference for what to do with `pref_settings.xml` | — |
| `design-review-c942766.md` | 209 | A 22-finding QA checklist (1 blocker, 8-9 majors, ~13 minors) at `c942766`; top-5 at `:199-209`. Several are now closed (see a.2) | Treat as a snapshot, re-verify each |
| `subscription-meta-bar-design.md` | 340 | The 9-row **state table** (`:245-260`) — best state-design template in the repo | Implementation is done |
| `server-flags-design.md` | 172 | Flag resolver ladder a→b→globe (`:36-45`); emoji-vs-bundled-asset decision (`:50-57`); collapse Option 2 (`:138-145`) | — |
| `memory-panel-design.md` | 180 | Which Android memory API to use and why (`:42-51`); PSS-not-Runtime rule (`:53-57`); 2s/10s cadences (`:74-81`) | Panel is built; §2 discipline is not |
| `notification-design.md` | 273 | Standard-template-not-RemoteViews decision (`:76-92`); channel `IMPORTANCE_NONE→LOW` fix (`:195-204`); single-toggle rationale (§3) | Largely built (`setUsesChronometer`, `IMPORTANCE_LOW` both present) |
| `hidden-templates-design.md` | 365 | The **leak-point gating table** (`:254-266`) — every UI path that can expose a locked config; honesty statement (`:212-218`); template-trust validation (`:286-299`) | — |
| `telegram-auth-design.md` | 336 | Auth flow B (deep-link + nonce + poll) and why (`:36-59`); `BackendConfig` shape (`:240-262`); `LoginActivity` states (`:215-220`) | Largely built (`auth/` package) |
| `ping-methods-design.md` | 366 | The 4-method matrix (`:148-156`); per-method timeout/concurrency table (`:306-312`); ICMP `-1` ≠ dead caveat | Built end-to-end |
| `circumvention-settings-design.md` | 315 | **Fully unbuilt.** Preset table (`:105-118`), per-item plain-language copy + `?`-help (§4.2/§4.4), honesty guardrails (§7) | — |
| `smart-tv-transfer-design.md` | 394 | TV Phase A UI checklist (`:109-158`) — **unbuilt**; transfer topology + threat model (§3.1-3.2) — built | — |
| `ux-recommendations.md` | 288 | **The copy voice bible** (`:189-206`); signature moments (§2); empty-state table (§3); error taxonomy (§5); perceived-perf rules (§6) | — |
| `happ-parity-details.md` | 211 | 19-detail Happ catalog P0–P3; PIN deep-dive (§2); balancer/"Hybrid" mapping (§3) | — |

---

## Top 10 concrete gaps, ranked

1. **Verify "Расширенные настройки (Xray Core)" is reachable** — ~44 folded prefs may be orphaned
   from the new hub (c.2).
2. **`expire` ms→s guard** in `util/SubscriptionUserInfo.kt` — a real year-5000 rendering bug,
   two lines (c.4).
3. **Mono-dark `mono_fab_inactive`** — idle shield brighter than connected (Conflicts #4).
4. **Red "Сбросить настройки" row** — owner-requested, filed as major twice (c.2).
5. **"Мониторинг памяти" + "Логи" rows** — a visible feature with no toggle; an activity with no
   entry point (c.2).
6. **Body `#directive:` fallback scan + URL-safe base64** — silently drops directives from
   static-hosted panels (c.4).
7. **`PREF_UTLS_FINGERPRINT` global fallback** — closes a real JA3/JA4 leak; one line in
   `CoreOutboundBuilder.populateTlsSettings` (c.3).
8. **"Проверить" pill + blue wordmark/version/globe on Home** — three open majors from the review (c.1).
9. **`ConnectError` taxonomy + staged connect status** — the emotional spine of `ux-recommendations`
   P0, entirely absent (c.5).
10. **TV Phase A** (overscan, landscape, D-pad focus) — the transfer feature ships into a UI that
    was never focus-adapted (c.6).
