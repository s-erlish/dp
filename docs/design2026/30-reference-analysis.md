# 30 - Reference analysis: Happ and Incy

**Departament VPN - what the two reference clients actually do, what we take, what we refuse,
and where we honestly stand against them.**

Governed by `docs/design2026/00-rules.md`. Where this document and that one disagree, that one
wins and this one is a bug (00-rules 0.1). This document does not introduce tokens, does not
re-spec screens, and does not re-plan features that already have a spec file; it is the
competitive read that specs 03 and 10-24 are built on.

---

## 0. Evidence, and what I did not see

### 0.1 The ledger

I have not opened Happ or Incy myself and I have not seen the screenshots. Everything below is
sourced. Three confidence tiers, tagged inline:

| Tier | Meaning | Sources |
|---|---|---|
| **[P]** protocol | Read directly out of the vendors' own published docs / repos. Exact and quotable. | `incy-repo-findings.md` (four INCY-DEV repos cloned and read, 2026-07-09), `incy-analysis.md` §1/§3 (incy-docs `app-management`, `subscription-format`, `provider-notifications`, `icon-presets`), `happ-parity-details.md` §0 (Happ dev-docs `app-management`) |
| **[O]** observed | Written down by our team while looking at the shipping app or its store listing. Second-hand to me, first-hand to the note. | `incy-redesign-spec.md` (7 screenshots, Incy v3.3.0 - the only screenshot-derived visual record we have), `incy-settings-design.md` §1, `design-system-2026.md` §1 (Play listings, review complaints), `happ-parity-details.md` (owner uses Happ daily) |
| **[I]** inference | My reading of [P]+[O]. Argued, not sourced. | this document |

### 0.2 The asymmetry that matters

**Our evidence on Incy is visual; our evidence on Happ is protocolar.** We have seven Incy
screens written up shot by shot [O] and Incy's full provider spec [P]. For Happ we have the
complete directive set [P] and a structural description [O], but **no screenshot inventory**.

Consequence, stated up front so nobody over-reads section 1: I can be precise about *what Happ
does* and about *the shape of its information*, and I must not be precise about its pixels. Any
claim about Happ's exact spacing, type or colour that appears anywhere in our docs is
unsourced and should be treated as invention until someone puts a screenshot next to it.

### 0.3 One correction to the record

`incy-analysis.md` §0 and `incy-settings-design.md` §0 both establish that **Incy's Android app
is closed source** - `llc.itdev.incy`, no app repo, `incy-platforms` "Source code (zip)" is
`README.md` + `RELEASE.json` at every tag [P]. Happ publishes `Happ-proxy/happ-android`
(`design-system-2026.md` §1) but our notes contain no reading of it.

So: **there is no line of either app's UI code in our evidence base.** Everything in sections 1
and 2 that sounds like an implementation detail is either their published protocol [P] or our
description of their rendered output [O]. Nobody should read this document and go looking for a
source file to copy.

---

## 1. Happ

### 1.1 The decisions that make it work

**1.1.1 The subscription is a first-class object with a status line, not a settings entry. [O]**

Happ's signature element is the subscription meta bar: profile name (<= 25 chars), used traffic
on the left, total on the right, expiry, and small support / website icon buttons
(`design-system-2026.md` §1). The user's relationship with the operator has a permanent,
glanceable home on the screen where servers live.

The mechanism worth naming is not the bar, it is **who owns the bar**. Every field in it is
driven by HTTP response headers on the subscription URL, so the operator can change what the
user reads without shipping an app update [P] (`happ-parity-details.md` §0). Happ turned a
config client into a channel. That is a product decision expressed as a layout.

**1.1.2 Length caps in the protocol are a layout guarantee. [P]**

`profile-title` <= 25 chars, `announce` <= 200 chars, per-server description <= 30 chars,
announce rendered <= 5 lines then ellipsis. The client does not defend itself against long
operator strings by truncating in the view; the *protocol* refuses to carry them.

This is the most under-appreciated idea in either reference. Truncation is a last resort in our
own law (00-rules 5.9) and a truncated primary label is a defect (1.2). Happ removed the
failure mode at the source. Every one of our operator-authored fields should carry the same cap,
enforced in the parser, before it reaches a `TextView`.

**1.1.3 `0` clears. [P]**

`announce: 0`, `sub-info-text: 0` and `subscription-userinfo: 0` are not empty strings - they
are an explicit "hide this block". An operator can retract a message and reclaim the space,
rather than leaving a blank container behind. A three-state field (absent / value / explicit
off) where most protocols ship two.

**1.1.4 Header wins over body. [P]**

Every directive exists twice: as an HTTP header and as a `#directive:` line in the body, with
the header taking precedence and the body form existing so a static nginx host can still speak
the protocol. It costs the client one precedence rule and it means the operator is never
blocked by their own hosting.

**1.1.5 Pin, and pin changes the destination. [O]**

Pinning a subscription sorts it to the top of the tab strip **and makes it the default open
tab** (`happ-parity-details.md` #1). One gesture that changes both order and where the app
opens. For a user with three subscriptions and one that matters, this is the whole product.

**1.1.6 The operator-authored server label beats the protocol name. [O]**

A server row can carry a custom description (<= 30 chars) that *replaces* the raw protocol
string, so the list reads "Amsterdam, быстрый" instead of "VMess"
(`design-system-2026.md` §1). The single cheapest legibility upgrade available to a server list.

**1.1.7 Per-item actions live in a bottom sheet, and Happ is still investing there. [O]**

Recent Happ work is described as "redesign of the subscription-actions bottom sheet"
(`design-system-2026.md` §1). The row carries information; actions are one deliberate gesture
away. That is what keeps a 56dp row scannable.

**1.1.8 Ping renders as a time value **or** as an icon, user-selectable. [O]**

The density of the list is a user setting, not a designer decision. Paired with the adjustable
font size in Settings [O], Happ treats "how much information per row" as a preference. Most
apps in this category pick one and argue about it forever.

**1.1.9 Commerce without a store: the expiry prompt. [P]**

`sub-expire: true|1` plus `sub-expire-button-link` produce a "renew" prompt when the
subscription is within three days of expiry. Happ has no account system, so it built the one
commerce moment that can be done with a URL and a date.

**1.1.10 Subscription resilience as a protocol feature. [P]**

`fallback-url` / `new-url` / `new-domain`: on 3xx-5xx or timeout Happ swaps to a backup URL, and
the operator can rotate the subscription's domain server-side. In a blocking regime the app
never has to show the user a dead subscription if the operator can move it. This is a design
decision as much as an engineering one - it removes an error state from the UI by removing it
from reality.

### 1.2 The decisions that are mediocre, or bad

**1.2.1 The per-subscription tab strip. [O] [I]**

Servers are grouped by subscription into a horizontally scrollable tab strip, plus an "all
servers" pseudo-tab. Three problems: a horizontal strip is the weakest scanning affordance on a
phone; the strip hides the count (you cannot see that you have five subscriptions without
scrolling the strip); and the tab is simultaneously a filter and a navigation destination, so
"where am I" and "what am I looking at" are one control. It stops scaling at about four
subscriptions - which is exactly why pin (1.1.5) had to be invented.

**1.2.2 The announce is permanent. [P] [I]**

Happ's announce sits above the list and is not dismissible (`incy-analysis.md` §1.1 contrasts
this with Incy explicitly). An operator message the user has read and cannot clear is a
permanent tax on list real estate. Incy's dismissible-per-update behaviour is simply better.

**1.2.3 Two overlapping banner systems. [P]**

`announce` (plain, <= 200, `0` clears) and `sub-info-text` / `-color` / `-button-text` /
`-button-link` (a superset with a coloured block and a labelled button), plus `sub-expire*` as a
third message channel. Three ways to say "show the user something". The client then owns a
precedence problem it did not create, and every screen that renders operator text has to answer
"which of the three is this".

**1.2.4 `sub-info-color: red | blue | green`. [P] - the worst idea in either protocol**

The operator picks the colour of a UI block. This hands our accent budget (00-rules 3.6) to a
third party. It means any operator can make the client look like a different product, it breaks
the "a colour never means two things" rule (6.2) because red now means both "destructive" and
"this operator likes red", and it is unfixable client-side without ignoring the field.

Note the internal contrast: **Incy solved this exact problem correctly** with enumerated icon
keys (2.1.11). Same category of feature, opposite quality of answer.

**1.2.5 Settings grouped by engine concept. [O]**

Interface / Tunnel / Advanced / Other / Information (`happ-parity-details.md` #12). "Advanced"
and "Other" are the two group names that always mean the information architecture was
abandoned. A user looking for "stop the VPN when the screen locks" cannot predict which of the
five it is in.

**1.2.6 The platform's constraints are hidden behind a toggle. [O] [I]**

Auto-start on boot is Android-only; kill switch and always-on are OS features, not app features;
on most platforms kill switch and split tunnelling are mutually exclusive
(`ux-recommendations.md` P0-5, sourced to Proton's own docs). Happ exposes the switches and
says nothing about the constraints. In a category built on trust, a switch that silently cannot
do what its label implies is worse than no switch.

**1.2.7 Emoji as identity. [P] [O]**

`profile-title` routinely carries a leading clover or flag emoji and Happ renders it verbatim
(`happ-parity-details.md` #6 explicitly recommends we "keep the leading emoji from
`profile-title`, don't strip"). So the row's identity glyph is drawn by the OEM's emoji font: not
our stroke weight, not our corner treatment, not our colour, different on every device. Our law
bans emoji as UI chrome (00-rules 1.4.4) and this is precisely why.

### 1.3 Take / refuse / differ

| Verdict | Item | Where it lands |
|---|---|---|
| **TAKE** | Header-over-body directive capture, one parse point | `happ-parity-details.md` #2: widen `HttpUtil.UrlContentResult` (`util/HttpUtil.kt:201/247`), add a `#`-body scan, persist at `handler/AngConfigManager.kt:591-602` |
| **TAKE** | Protocol-enforced length caps as a layout guarantee | 25 / 200 / 30 chars, 5 lines - clamp in the parser, never in the view |
| **TAKE** | `0` clears, as a real third state | Every operator-authored block gets absent / value / explicit-off |
| **TAKE** | Pin changes both order and the launch destination | `SubscriptionItem.pinned`; sort in `MainViewModel.getSubscriptions()`; default index in `MainActivity` |
| **TAKE** | Per-item actions in a sheet, not inline icons | We already have `sheet_server_actions.xml` (271 ln, correctly tokenised) - it is dead (01-inventory §5.1) |
| **TAKE** | Sort and filter as real list controls | `sort-order: none \| ping \| name` is also an Incy header [P] |
| **TAKE** | The <= 3-day renewal moment | But as a **state of the subscription object**, not a banner (see 6.1) |
| **REFUSE** | `sub-info-color` and any operator-chosen colour | Parse the field, **discard the value**. Severity is ours (see 5.4 and 6.3) |
| **REFUSE** | The per-subscription tab strip | Sticky section headers in one list (00-rules 4.6) |
| **REFUSE** | Two/three parallel banner systems | One operator-message component, one severity model, one place |
| **REFUSE** | Verbatim emoji in titles | Strip the leading flag glyph and map it to our own tile (desktop already has `StripLeadingFlagConverter`, 02-inventory §1.2.4) |
| **REFUSE** | "Advanced" / "Other" as group names | Groups named for what the user is trying to do |
| **DIFFER** | Where the operator's message lives | Happ: above the list, permanent. Us: inside the subscription object, dismissible per unique message, and escalating into a *state* when it is about expiry |
| **DIFFER** | What the operator may style | Happ: colour. Incy: an enumerated icon key. Us: **nothing.** The operator supplies text, links, and a severity that we map onto our tokens |

---

## 2. Incy

### 2.1 The decisions that make it work

**2.1.1 The hero is a figure on a ground, not a container. [O]**

Incy's Home is a single near-black canvas; the connect control sits directly on it with no card,
no surface fill, no stroke (`incy-redesign-spec.md` §HOME.3; `design-home-polish.md` §"Why the
current Home isn't Incy"). Our Home wraps the same content in `card_hero` (a `MaterialCardView`
with `colorSurface` + 1dp stroke) *and* makes the connect control itself a bordered card - two
nested bordered surfaces on a flat background, which reads as "a card app".

The transferable lesson has nothing to do with the glow: **on the most-looked-at screen in the
product, delete the container.** One border, one fill and one radius removed from the hero is a
bigger visual win than anything you can add.

**2.1.2 The idle connect control is recessed, not saturated. [O]**

It is a dark disc wearing a ring, not a bright filled button. Idle reads as *off* because it is
darker than its surroundings, not because it is grey-tinted-blue. Colour arrives only on state
change.

This satisfies "inactive states are never saturated" (00-rules 6.4, `product.md` verbatim) more
completely than most apps in the category, which ship a saturated pill that shouts while
disconnected. It is also the reason Incy's connected state lands: there is contrast available to
spend, because idle spent none.

**2.1.3 One object carries the whole connect state machine. [O]**

Ring colour encodes idle / connecting / connected / error. No second status widget, no badge, no
banner. The thing you press is the thing that tells you what happened.

**2.1.4 The uptime timer is the middle stat. [O]**

`↑ 26 B/s · 3:11:08 · ↓ 40 B/s`, centred, as plain text on the canvas, no cards
(`incy-redesign-spec.md` §HOME.2). Speeds fluctuate and mean little; **a duration is a trust
signal.** Three hours eleven minutes of unbroken tunnel is the single most reassuring number a
VPN can show, and it costs one ticker.

**2.1.5 Grouped settings: the card is the group, the row is the item. [O]**

A section label sits *outside and above* one continuous rounded card; inside it, rows stack with
hairline dividers inset to the text origin, the first row rounding the top corners and the last
the bottom (`incy-settings-design.md` §1). Group boundaries become readable without any heading
weight at all. This is genuinely good IA rendering and it is directly reusable - the *structure*
is right even though the label style (2.2.2) and the tile colouring (2.2.3) are wrong.

**2.1.6 Every settings row shows its current value. [O]**

"Тема - Тёмная", "Пинг - HTTP GET", "Прокси по приложениям - Выкл", "Логи - None · 1 час"
(`incy-redesign-spec.md` §SETTINGS). You can audit your entire configuration by scrolling once,
without opening anything.

This is the most copyable decision in either app and it costs nothing. It is also the thing that
makes a two-level settings tree tolerable: you never have to push a screen to find out what a
setting currently is.

**2.1.7 Two levels, and the top level is short. [O]**

A hub of navigation rows (about eleven of them) leading to detail screens of grouped toggles.
The engine's ~50 preferences are not the top level; eleven decisions are
(`design-tg-and-settings-trim.md` §B.2 reconstructs the same shape for us).

**2.1.8 Two levels of encoding in one server row. [O]**

A *chip* for what the node is (`Auto` / `VLESS` blue, `JSON` gold) and *plain muted text* for how
it is transported (`TCP · REALITY`). Identity is emphasised, plumbing is legible but recessive.
The gold `JSON` chip is meaningful rather than decorative: it marks a full-Xray-config node,
which genuinely behaves differently [P] (`incy-analysis.md` §3.7).

**2.1.9 Ping is a dot **and** a value **and** a word. [O]**

Green/red dot, `454ms`, red `n/a` when untested or dead. Colour is never the only signal
(00-rules 6.3) - by accident, but correctly.

**2.1.10 Honesty details in the protocol that surface as UI honesty. [P]**

- `subscription-userinfo: 0` hides the traffic block entirely, rather than showing `0 B / 0 B`.
- `expire > 32000000000` is interpreted as milliseconds and divided - so a panel that emits ms
  does not display a year-5000 expiry.
- `profile-web-page-url` renders **grey when unset** rather than hidden, so the affordance
  teaches that the feature exists.

The third is arguable (a permanently disabled control is usually noise) but it is deliberate, and
in a managed product where the operator may switch the feature on tomorrow, it is defensible.

**2.1.11 Enumerated icon keys instead of operator-chosen styling. [P]**

`icon-presets` gives the provider **20 stable string keys** (`send, bot, chat, message, mail,
megaphone, bell, newspaper, rss, broadcast, help, support, lifebuoy, info, book, crown, star,
gem, rocket, heart`) for the bot / channel / support link slots, mapped client-side to Material
Icons on Android and SF Symbols on iOS, with documented fallbacks (`bot → send`,
`channel → megaphone`, `support → help`) for null or unknown values
(`incy-repo-findings.md` §3).

**This is the correct answer to the customisation problem, and it is the single best design idea
in either reference.** The operator gets meaningful choice; the client keeps the icon family,
the stroke weight, the size and the colour (00-rules 10.1-10.3). Compare Happ's
`sub-info-color`, where the operator gets a raw colour. Same problem, opposite quality of answer.
Adopt the *pattern*, not just the feature: **anything an operator can customise must be an
enumeration we render, never a value we display.**

**2.1.12 The announce is dismissible, in the card. [P]**

Incy renders `announce` as "a dismissible alert in the subscription card", against Happ's
permanent header (`incy-analysis.md` §1.1). Correct: an operator message is an interruption with
a lifetime, not furniture.

**2.1.13 App-icon chooser and multiple themes. [O]**

Sixteen selectable launcher icons. For our RU/FA audience a disguised icon is a safety feature,
not vanity (`ux-recommendations.md` P2-7).

### 2.2 The decisions that are mediocre, or bad

**2.2.1 "Шрифт очень мелкий и тесновато." [O] - the recurring review complaint**

Density without air (`design-system-2026.md` §1, sourced to store reviews). It is the reason
`VISUAL_DENSITY` is pinned at 4 in our law with that exact quote attached (00-rules 0.2). Incy
bought its information density with legibility, and the users noticed.

**2.2.2 ALL-CAPS grey section labels. [O] - a named Absolute Ban**

ОФОРМЛЕНИЕ / СОЕДИНЕНИЕ / ПРОВАЙДЕРЫ / ПРОИЗВОДИТЕЛЬНОСТЬ, small, grey, tracked. This is the
tiny-uppercase-tracked-eyebrow trope, banned verbatim in `impeccable/SKILL.md` and restated at
00-rules 1.1, 1.2 and 5.10, and it is Incy's *primary structural device*. It is also the single
thing our own early docs copied most faithfully - see section 4.

**2.2.3 A different coloured tile on every row. [O]**

Blue palette, blue globe, gold app-icon, yellow bolt, purple layers, green speedometer, indigo
moon, teal chip (`incy-settings-design.md` §1.3). Eight hues in one scrolling list.

00-rules 3.6 answers this verbatim: *"Coloured tiles are not decoration; they are a category
system, and a screen where every row has a different coloured tile has no category system, only
noise."* It also destroys the accent budget - at eight hues nothing is the accent, so nothing is
the primary action.

Our own state is the right instinct executed badly: `values/themes.xml:88-99` repoints
`iconTintGreen/Orange/Purple/Yellow → @color/icon_blue`, so every tile renders blue while every
layout still *says* green (01-inventory §3.5). The colour names in the layouts are lies, and the
next maintainer will reintroduce the rainbow by accident.

**2.2.4 The glow. [O]**

Radial navy canvas lifted behind the connect zone, a radial bloom behind the disc, layered alpha
rings. It photographs beautifully and it is exactly the failure 00-rules 2.4.1 names: *"If your
screen reads as 'gamer VPN', it failed."*

It also does not survive contact with a second theme. Our port needed `bg_home_gradient`
(560dp radial) + `bg_connect_glow` + `drawable-night/` + `_mono` variants + a breathing animator
(01-inventory §3.5), and in the light theme the near-white gradient centre makes the idle disc
almost invisible - `card_connect` on `colorSurfaceContainerHigh #EAEFF7` against a white centre,
held together only by the ring (`design-review-c942766.md` §1.4). Depth from a gradient is depth
you have to redraw per theme; depth from the surface ramp (00-rules 4.7) is depth you get for
free in all three.

**2.2.5 iOS pill switches on Android. [O]**

`incy-redesign-spec.md` records "Toggles: iOS pill switches (grey off / blue on)". This is the
tell `android.md` names verbatim (quoted at 00-rules 2.3): *"an iOS app wearing Android's skin:
... Cupertino-shaped switches and dialogs."* `MaterialSwitch` exists, is themeable, and is what a
fluent Android user expects to see.

**2.2.6 Provider directives that change device behaviour, applied silently. [P] - the sharpest problem**

Incy's provider headers include, beyond the message set:

| Header | What the operator gets to change on your device |
|---|---|
| `per-app-proxy-enable` / `-mode` / `-list` | Which of **your apps** are tunnelled, in bypass or proxy mode; the list may be a remote URL refetched on every update |
| `fragmentation-enable` / `-packets` / `-length` / `-interval` | TCP fragmentation, **explicitly overriding the user's global setting** |
| `noises-enable` / `-type` / `-packet` / `-delay` | UDP noise before handshake |
| `server-address-resolve-*` | DoH pre-resolution of server hostnames, with a bootstrap IP |
| `hide-url` | Removes the subscription URL from **your own** Share / Copy / QR / backup |
| `routing` / `autorouting` | Installs a routing profile, with auto-update when `autorouting` |

Two of these are not settings, they are capability removals: `hide-url` takes away the user's
ability to read data they own, and `per-app-proxy-list` re-scopes the tunnel across the whole
device. Nothing in the documented behaviour surfaces "your provider changed this" or offers a
revert.

I am not arguing against the protocol - we need most of it, it is how a Remnawave panel talks to
a client. I am arguing that **applying it invisibly is a design decision, and it is the wrong
one.** See 6.3.

**2.2.7 Provider push with a forced modal timer. [P]**

`provider-notifications`: FCM/APNs push, title <= 100, body <= 500, optional image, action URL,
button label, and an **"enterprise modal timer 1-10 s"** - a modal the user cannot dismiss for up
to ten seconds, sent by a third party, with `deliveredCount` / `failedCount` telemetry back to
the operator. That is a dark pattern with a spec number. Refuse the timer outright; the channel
itself is a separate, larger question (`incy-analysis.md` §1.2 correctly parks it as P3).

**2.2.8 The server list is on Home **and** in the Servers tab. [O]**

`incy-redesign-spec.md` §HOME.8: "Server list right here (below provider), same rows as Servers
tab." So Home is stats + connect + selected-server line + memory card + Проверить + provider card
+ the full server list, and the Servers tab is a near-duplicate with a search field.

Two destinations rendering the same list is an IA failure: neither is the answer to "where are my
servers", scroll position and selection state have to be reconciled across both, and the hero -
the reason Home exists - gets pushed above the fold. Our desktop compact layout has already
inherited exactly this: one scroll of a 440px-minimum hero followed by the whole list, so on a
630px window the list starts below the fold and the hero cannot be skipped (02-inventory §4.2).

**2.2.9 A RAM gauge on the consumer home screen. [O]**

"Память приложения / 25 MB · Норма" with a green dot (`incy-redesign-spec.md` §HOME.5). Developer
brain. It communicates nothing the user can act on, and "Норма" is a judgement the user cannot
verify or change.

Our version is the worst of both worlds: `card_memory` exists in `activity_main.xml`, is gated on
`PREF_SHOW_MEMORY`, and **the preference that controls it is not reachable from any UI** because
it lives only in the orphaned `pref_settings.xml` (01-inventory SCREEN 1 item 4, §5.2).

**2.2.10 `incy://crypt1/` presented as encryption when it is obfuscation. [P]**

AES-256-GCM with a key derived from constants baked into every client; the README says so
explicitly and the threat model is Telegram/RKN scanners, not Frida
(`incy-repo-findings.md` §4). The engineering is fine and honest in the repo. The risk is in the
*copy*: if a UI ever calls this "зашифрованная ссылка", the app has lied to a user whose safety
depends on knowing the difference. If we ship an equivalent, the string is
«Скрытая ссылка», never «Зашифрованная».

### 2.3 Take / refuse / differ

| Verdict | Item | Where it lands |
|---|---|---|
| **TAKE** | Figure on ground: no card under the hero | Spec 10 (Home). Delete `card_hero`'s surface, stroke and radius; keep the ids |
| **TAKE** | Recessed idle disc: darker than the page, never tinted | `?attr/colorSurfaceContainerHigh` `#1A1D21` on `?attr/colorBackground` `#0A0B0D` |
| **TAKE** | One object carries the state machine | The ring, not a second badge or banner |
| **TAKE** | Uptime as the middle stat | `HH:MM:SS`, `TextAppearance.App.Numeric` with `tnum` so it does not jitter |
| **TAKE** | Card = group, row = item, divider inset to the text origin (68dp) | Spec 12 (Settings), both platforms |
| **TAKE** | **Current value on every settings row** | 13sp `TextAppearance.App.Subtitle` / `TextBlock.Subtitle`, `onSurfaceVariant`, before the trailing affordance |
| **TAKE** | Two levels, ~11 rows at the top | `design-tg-and-settings-trim.md` §B.2 already has our list |
| **TAKE** | Chip for identity, muted text for transport | Already in `item_recycler_main.xml:75-97`; fix the contrast (5.3) |
| **TAKE** | Dismissible-per-message announce | Dismissal keyed on a hash of the text, so a *new* message re-appears |
| **TAKE** | **Enumerated keys, never operator values** | Generalise `icon-presets` into a rule (6.3) |
| **TAKE** | `subscription-userinfo: 0` hides the block; `expire > 32e9` is ms | Two parser rules, both cheap |
| **REFUSE** | Radial canvas, bloom, breathing halo, sonar-at-idle | 00-rules 1.4.3. Depth from the surface ramp only (4.7) |
| **REFUSE** | ALL-CAPS tracked section labels | 00-rules 1.1 / 5.10. `SettingsSectionLabel`, 16sp/700, sentence case |
| **REFUSE** | A different coloured tile per row | 00-rules 3.6. Neutral tile by default; colour marks a category, <= 3 per screen |
| **REFUSE** | iOS pill switches | `MaterialSwitch` on Android; the desktop's own toggle class on PC |
| **REFUSE** | Silent provider control of device behaviour | 6.3 |
| **REFUSE** | Operator push with a forced modal timer | Not at any duration |
| **REFUSE** | The server list duplicated on Home | One list, one destination |
| **REFUSE** | RAM gauge as a home-screen citizen | If it lives at all, it lives in О приложении behind a developer disclosure |
| **DIFFER** | How progress is drawn during connect | Incy: brightness and a pulsing halo. Us: **arc length.** An indeterminate 212dp `CircularProgressIndicator` / `#ConnectingArc` on a 3dp `colorOutlineVariant` track. Legible in mono, legible in light, no per-theme drawable |
| **DIFFER** | What "connected" looks like | Incy: brighter. Us: the ring completes and settles to `?attr/colorTertiary` `#22C55E` **with the word** «Подключено» (00-rules 6.3). Fixes the current defect where a green shield sits inside a blue halo (`design-review-c942766.md` §2.1) |
| **DIFFER** | Where operator settings become visible | Incy: nowhere. Us: one screen that lists them (6.3) |

---

## 3. The two together

### 3.1 What both get right, and what that tells us the category actually requires

Four things appear in both, from different directions. Treat them as table stakes, not as
differentiators:

1. **The operator relationship is a visible object** carrying traffic, expiry and a support
   line. A managed VPN where you cannot see your own quota is broken.
2. **One dominant connect control**, and the app's identity is that control.
3. **Servers are grouped under the thing that produced them.** Nobody ships a flat undifferentiated
   list.
4. **Per-item actions belong in a sheet**, not as inline icons on the row.

We have all four in some form. None of them is a place to win.

### 3.2 The shared blind spot - and it is where the opening is

Three things neither app does, in either evidence tier:

**3.2.1 Neither has an account.** Both are bring-your-own-config clients. There is no balance, no
tariff, no device list, no payment history, no renewal path inside the app. Happ's best answer is
a URL button at T-3 days [P]; Incy's is a push notification [P]. Neither can render
«Истекает через 3 дня» as an *actionable state of the object you are looking at*, because there
is no object - only a header the operator typed.

We have a real backend (`departament.site` plus the bot) and four commerce screens that our own
inventories rate as the best work in the codebase: `AccountFragment` "the best screen in the app"
(01-inventory verdict, KEEP), and `BuyView` "closest thing to a finished 2026 screen", `DevicesView`
and `PaymentHistoryView` both KEEP (02-inventory §4.4). **Neither reference can copy this without
becoming a different business.**

**3.2.2 Neither designs failure.** Nothing in either evidence base shows a structured recovery
layer: no error taxonomy, no "one silent retry before you show anything", no inline route from a
failure to the fix. Happ's answer to a dying subscription is server-side (`fallback-url`); Incy's
is out-of-band (push). For an audience in DPI networks, **failure is the normal path**, not the
exception (`ux-recommendations.md` §0, §5).

**3.2.3 Neither is honest about the platform.** Kill switch and always-on are OS features;
kill switch and split tunnelling are mutually exclusive on most platforms; screen-off disconnect
cannot be registered from a manifest. Both apps ship the switches and say nothing
(`ux-recommendations.md` P0-5, `incy-settings-design.md` §4.2).

---

## 4. Superseded reference decisions in our own docs

Several of our earlier documents faithfully copied things that 00-rules now bans. They were
written before the law. **They are superseded on these points and an implementer must not follow
them.** Listing them is the point of this section; the rest of each document stands.

| Our doc | What it specifies | Superseded by | Correct instruction |
|---|---|---|---|
| `design-home-polish.md` §1 | `bg_home_gradient.xml`, radial `#1B2D50 → #0E141F → #0A0B0D`, 560dp, on `group_home` | 00-rules 1.4.3, 6.5 | No page gradient. `?attr/colorBackground` `#0A0B0D` flat |
| `design-home-polish.md` §3 | `bg_connect_glow.xml` radial bloom + brightened halo rings on `bg_connect_ring.xml` | 00-rules 1.4.3, 4.7 | Ring + arc only. Depth from the surface ramp |
| `design-system-2026.md` §5.1 | Connect ring as a **gradient** `#1E5FC7 → #3B82F6`, "subtle glow (tinted shadow)", "breathing pulse" | 00-rules 1.1 (gradient), 1.4.3 (glow), 8.1 (decorative motion) | Solid `?attr/colorPrimary`; the only 600ms moment is the connect confirmation (8.4) |
| `design-system-2026.md` §4 | Glass / liquid-glass Settings, `RenderEffect` blur, `FLAG_BLUR_BEHIND`, three tiers | 00-rules 1.1 ("glassmorphism as default"), 1.4.3 | Solid `?attr/colorSurface`. The glass tiers are not built |
| `design-system-2026.md` §3.3 | Brand-tinted ambient/spot shadows at 6dp elevation | 00-rules 4.7 | Elevation 0, no shadow, 1dp `colorOutlineVariant` |
| `design-system-2026.md` §3.1/§3.2 | A second scale: `space_2/20/48`, `radius_xs/sm/md/lg/xl` | 00-rules 3.1, 3.2 | One scale: 4/8/12/16/24/32; radii 12/20/24/100 |
| `incy-redesign-spec.md` §SETTINGS, §HOME.7 | "Section labels: UPPERCASE grey, small"; `ТЕКУЩИЙ ПРОВАЙДЕР` | 00-rules 1.1, 5.10 | `SettingsSectionLabel`, sentence case, 16sp/700 |
| `incy-redesign-spec.md` §Palette | "Toggles: iOS pill switches"; purple `#9B7DFF` for download | 00-rules 2.3, 1.4.1 | `MaterialSwitch`; no second accent hue, purple is an alias of blue |
| `incy-settings-design.md` §5.3(a) | Category header "ALL-CAPS", 13sp/600 | 00-rules 1.1, 5.10 | Same as above |
| `incy-settings-design.md` §5.1 | Per-row pastel tile set: `tile_blue/gold/yellow/purple/green/indigo/teal/orange/gray` | 00-rules 3.6 | Neutral `#20242B` / `#9BA1AD` by default |
| `design-tg-and-settings-trim.md` §B.2 | "Grouped rounded cards under UPPERCASE labels" | 00-rules 5.10 | Same. The row **inventory** in that table is still correct and should be used |
| `ux-recommendations.md` §2.4 | "The glass Settings reveal" as a signature moment | 00-rules 1.1, 8.9 | Not a signature moment. Settings appears; it does not perform |

Everything else in those files - the directive catalogue, the pin design, the settings row
inventory, the error taxonomy, the empty-state copy, the perceived-performance section - is
current and load-bearing.

---

## 5. Us against them, screen by screen

Verdicts use three words with fixed meanings. **BEHIND**: they solve a user problem we do not.
**AHEAD**: we solve one they do not. **DIFFERENT**: a real divergence with no clear winner, which
we must therefore choose deliberately rather than drift into.

### 5.1 First frame and sign-in

**BEHIND, and the gap is self-inflicted.**

The reference apps do not have this screen because they do not have accounts. That is not an
excuse; it is the price of our advantage (3.2.1), and we are currently paying it badly.

- **Android.** The real sign-in screen for 100% of new users is `layout_home_empty.xml` - a
  `MaterialCardView` floating in the middle of an empty gradient with **two competing filled
  buttons** («Добавить QR» and «Купить подписку» depending on state), a 12sp «или войдите»
  divider, and a 52dp tonal pill at `cornerRadius=26dp`, a radius that appears nowhere else in
  the product. `LoginActivity` is only ever reached *from* it, and has three shapes
  (both / site / telegram) plus link mode (01-inventory SCREEN 2, SCREEN 7; both **REBUILD**).
- **Desktop.** `LoginView.axaml` is 954 lines plus 1 377 of code-behind: **20 buttons, 5 text
  boxes, 6 sign-in methods, 34 localisation keys in one scrolling column**, and the primary
  method - Telegram - is not first. It sits below the e-mail form under an «или» divider as a
  *tonal* button, while the accent `Primary` is spent on the e-mail submit (02-inventory §1.7;
  **REBUILD**). The hierarchy is literally inverted.

Neither reference would ship either screen, because neither has six ways to sign in. The fix is
not visual polish, it is subtraction: one accent action, one secondary, everything else behind
«Другой способ входа».

**One thing we are ahead on:** `AccountSyncView` (02-inventory §1.9, **KEEP**) - the post-login
import gate with a live stage line, a real failure state with two exits, and a success settle, so
the empty onboarding never flashes between "Вход" closing and Home filling. Neither reference has
anything to sync, so neither had to solve it, but it is a genuinely correct loading gate.

### 5.2 Home and the connect control

**DIFFERENT on approach, BEHIND on execution, AHEAD on the machinery underneath.**

Where we are heavier for no gain: our hero is a **five-layer stack to say "off"** -
`view_connect_glow` (radial gradient halo), `view_connect_ring` (two-stroke oval),
`view_connect_pulse` (one-shot sonar), `progress_connect` (212dp `CircularProgressIndicator`),
and a 176dp `MaterialCardView` at 88dp radius holding two 80dp shields that crossfade
(01-inventory SCREEN 1 item 3). Incy's is a disc and a ring. Ours costs four extra drawables per
theme and says the same thing.

Desktop is worse: `#AmbientSonar` → `#AmbientRing` → `#GlowHalo` → `#RingOuter` / `#RingHoverGlow`
/ `#RingInner` → `#SonarPulse` / `#SonarPulseEcho` → `#ConnectingArc` → `#ConnectDisc` → two
shields, described by our own inventory as **two competing idle animations on the same object**
(02-inventory §4.2).

Where we are behind on a detail both apps share: the stats row renders its arrows as literal
`android:text="↑"` / `"↓"` TextViews with a 42dp invisible `View` used to fake optical centring
(01-inventory SCREEN 1 item 1), and the desktop does the same with `Text="↑"` / `Text="↓"`
TextBlocks (02-inventory V10). Incy does this too [O] - so this is parity at a low bar, not a
deficit against them. It is a violation of *our* law (1.4.4, 10.1), which is the reason to fix it.

Where we are genuinely ahead:

- **The connect state machine.** `applyRunningState`, a watchdog, one-shot event consumption, and
  live-transition gating so a LiveData replay does not re-animate - described by our own blunt
  inventory as "careful, correct work" (01-inventory §6). Nothing in either reference's evidence
  base shows an equivalent.
- **Reduced motion as a live contract on both platforms.** `MotionUtils.animationsEnabled` /
  `View.reducedMotion()` guards the hero assemble, the connect confirm, the tab fade-through, the
  list stagger, the balance count-up and the skeleton pulse (01-inventory §6);
  `MotionState.IsLite` is broadcast live and subscribers re-apply on the spot (00-rules 12.5).
  Neither reference's evidence mentions reduced motion at all. This is a P1 accessibility
  contract for us (8.8) and a differentiator in a category that loves ambient animation.

### 5.3 The server list

**BEHIND on mechanism, at parity on styling, AHEAD on two small things.**

Two functional holes, both worse than any styling gap in this document:

1. **On Android a user cannot delete, rename, share, edit or QR a single server from the UI.**
   `MainActivity.kt:610-611` assigns `onItemLongClick`, `MainRecyclerAdapter.kt:56` declares it
   with a comment saying it is "no longer invoked by the adapter", and `bindServer()` (line 213)
   sets **only** `setOnClickListener`. Consequently `ServerActionsSheet` never opens, and
   `editServer()`, `shareServer()`, `showQRCode()`, `removeServer()`, duplicate and set-default
   have no callers, along with four whole editor activities (01-inventory §5.1). Happ and Incy
   both have the full per-item action set. This is a P0 regression, not a design gap.
2. **On desktop there is no server search anywhere in the shipping app.** The only search field
   is `CompactServersView.axaml:90` bound to `Profiles.ServerFilter`, in a view nothing
   instantiates (02-inventory §0.2, §4.7). With 80-150 servers per subscription that is a
   functional hole. Incy ships a search pill on its Servers tab [O].

Also missing against them: sort (`sort-order: none | ping | name` is in both protocols [P]),
protocol filtering that actually exists in the layout rather than being appended in code
(`design-review-c942766.md` §4.4), and bulk ping / multi-select on desktop.

At parity: our row already carries Incy's two-level encoding - chip `tv_type` for identity, muted
`tv_statistics` caption for transport, numeric ping at the end
(`item_recycler_main.xml:75-122`, read directly).

Ahead, narrowly but really:

- **The ping value uses the Numeric role with tabular figures** (`TextAppearance.App.Numeric`,
  `item_recycler_main.xml:118`), so the row does not jitter horizontally as results land.
  Incy's documented failure mode is crowding; a jittering list is how crowding becomes unusable.
- **The selected state is a full outline plus fill, not a side stripe**, and the retirement of the
  stripe is documented in the layout itself (`item_recycler_main.xml:30-33`) against the Absolute
  Ban. The zero-size `layout_indicator` View that survives only so the adapter can still call
  `setBackgroundColor` on it should go with it.

Merely different, and both wrong: country identity. Android uses emoji via `FlagUtil` with a
globe fallback (banned, 1.4.4); desktop uses 16 raster PNGs plus `xx.png` in an otherwise
all-vector UI (02-inventory V10). The references use emoji [O] [P]. **Nobody in this comparison is
right.** The unified server icon (00-rules 10.5) - flag tile at 28 inside the standard 40 slot,
globe glyph fallback, one treatment on every surface - is ours to define and is a small, clean win.

### 5.4 The subscription surface

**AHEAD on data capture, BEHIND on the container, BEHIND on protocol coverage.**

Ahead: we already parse and persist `subscription-userinfo` end to end - `SubscriptionItem`
traffic fields, `HttpUtil.getUrlContentWithUserAgentEx`, the `AngConfigManager` persist block -
and render traffic, expiry, announce and support (`subscription-meta-bar-design.md` §2.3,
implemented). The seam that unlocks the rest of both protocols is one function wide.

Behind, on the container, with three defects our own inventory names:

- The traffic pill prints an **11sp label on top of a moving progress fill**, so contrast is
  2.9:1 wherever the fill has advanced past the glyph - and **changes mid-word as the bar fills**
  (01-inventory §3.5, SCREEN 3). Below 4.5:1 (00-rules 6.8) and it is the single most-looked-at
  number in the subscription block.
- **Long-press on a carousel page deletes the subscription** (`HomeMetaPagerAdapter:66` →
  `confirmDeleteSubscription`). Undiscoverable and destructive - the exact inverse of 00-rules 7.5.
- The `ViewPager2` height is computed by inflating and measuring **every** subscription's page on
  **every** rebuild (`measureHomeMetaHeight`, `MainActivity.kt:888-910`).
- On desktop, `SubscriptionMetaView` locally overrides the global `IconButton40` down to **34×34
  with 20px glyphs** to fit four trailing actions into a 372px window (02-inventory V6, §4.2).
  That is a structural problem being solved by shrinking targets.

Behind, on protocol coverage. Captured today: `subscription-userinfo`. Not captured, and present
in one or both references [P]: `announce`, `announce-url`, `support-url`, `support-email`,
`profile-web-page-url` / `homepage`, `profile-title`, `profile-description`,
`profile-update-interval`, `sort-order`, `premium-url`, `hide-url`, the `banner-*` set, the
`per-app-proxy-*` set, `fragmentation-*`, `noises-*`, `server-address-resolve-*`,
`routing` / `autorouting`, `fallback-url` / `new-url` / `new-domain`.

The design consequence is not "add 20 fields". It is that **every one of those fields has to land
in a component with a defined severity, a defined lifetime and a defined owner**, or we
reproduce Happ's three-banner problem (1.2.3). Section 6.3 sets that rule.

### 5.5 Account and commerce

**AHEAD, decisively, and this is the only place we lead outright.**

- Android `AccountFragment` / `activity_account.xml`: one hero, one section header, and **four
  designed states** in the subscription slot (skeleton / carousel / empty / error) - "the only
  screen in the app with a complete state machine" and "the best screen in the app"
  (01-inventory SCREEN 6, verdict **KEEP**).
- Desktop `BuyView` (**KEEP**, "closest thing to a finished 2026 screen": five states, real
  skeletons, a proper sheet with scrim and Escape, one accent CTA), `DevicesView` (**KEEP**, five
  states, correct destructive treatment - red glyph plus confirm card, not a red row),
  `PaymentHistoryView` (**KEEP**, four states) (02-inventory §4.4).

Neither Happ nor Incy has any of this, and neither can without changing what they are.

The caveats are ours to fix, and the owner has already named them: the Account tab and every
button in it are to be reworked on both platforms. Desktop `AccountView` is **1 474 lines with a
four-panel flyout state machine inside a horizontal carousel inside a scroll** - the visual
grammar is right, the nesting is not; add-devices and upgrade belong on sub-pages
(02-inventory §4.4). Android's Account needs only the 72dp divider inset, the 52dp-container /
48dp-circle avatar mismatch, and `.Title` being used simultaneously as a row label and a list-item
title (01-inventory SCREEN 6, §3.4).

### 5.6 Settings

**BEHIND Incy on Android. AHEAD of Incy on desktop, on a mechanism worth making law.**

Android, behind:

- `layout_settings_content.xml` is **1 536 lines for 20 rows with zero reuse** - every row a
  hand-copied 55-line `LinearLayout` - while two correct reusable components,
  `layout_setting_row.xml` and `layout_setting_toggle_row.xml`, sit **unused by any layout**
  (01-inventory SCREEN 5, §5.3).
- **Roughly 30 real settings are unreachable**, living only in `res/xml/pref_settings.xml`, which
  is loaded only by `SettingsActivity`, which nothing launches - including three the Home tab
  actively *reads*: `PREF_SHOW_MEMORY`, `PREF_AUTO_FALLBACK`, `PREF_CONFIRM_REMOVE`
  (01-inventory §5.2). Features ship with hidden switches.
- «Автообновление подписки» exists **twice**, in two different visual languages, two taps apart -
  once in the Settings tab and once in `ProviderSettingsActivity` - writing the same fields
  (01-inventory SCREEN 12).
- Six single-choice `AlertDialog`s (Режим / DNS / Пинг / Оформление / Язык / Автообновление) where
  an inline segmented control or a push screen belongs (01-inventory §4 row 35; 00-rules 7.6).

Incy's answer to all of this - one continuous card per group, the current value on every row, a
short top level pushing to detail screens - is better than ours in every respect except the label
casing and the tile colouring.

Desktop, ahead, and this is the find:

`SettingsView` ships an **affordance-honesty grammar**, documented as a contract at
`SettingsView.axaml.cs:14-22` and applied to every row:

| Affordance | Promise |
|---|---|
| chevron `>` | this pushes a screen |
| chevron that rotates 0 → 90 | this expands inline, right here |
| `unfold_more` | this cycles the value in place, no screen, no dialog |
| segment | this is a 2-state change, applied immediately |
| toggle | this is a boolean |

Our own inventory calls it "the single best design decision in the codebase" and says to keep it
verbatim (02-inventory §4.3). **Incy has one affordance - chevron plus value - and hides the
outcome behind a push.** Ours tells you what a row will do *before* you touch it. That is a real
invention, it generalises far beyond settings, and it is the direct answer to the owner's demand
that settings be one system «а не абы как». Section 6.3 promotes it to law.

Desktop, behind: `SettingsView.axaml:216` is a bare `ScrollViewer` with **no `MaxWidth`**, so at
the app's own 1120×760 preset the rows run ~1030px edge to edge - while Account/Buy/History/Devices
clamp to 560 and the eight settings sub-pages clamp to 620 (02-inventory V3). No settings search.
About ten engine features exist only inside the dead `OptionSettingWindow` (02-inventory §1.3).

### 5.7 Feedback, errors, offline

**BEHIND both, and behind the platform.**

- **Android: zero `Snackbar`s.** Every transient message is a `Toast` - about forty of them, plus
  one custom-view `Toast` (`toast_status.xml`, bottom gravity with a magic 110dp y-offset) built
  on the deprecated API, which means it is **invisible on Android 12+ when the app is not
  foreground** (01-inventory §1.4, §3.7). 00-rules 1.4.8 bans `Toast` for anything actionable.
- **Desktop: there is no user-visible feedback surface at all.** `snackHost`
  (`MainWindow.axaml:623`) is permanently `IsVisible=False`; `DelegateSnackMsg`
  (`MainWindow.axaml.cs:1765`) forwards every message to `NoticeManager.SendMessage` →
  `MsgViewModel`; and **`MsgView` is never mounted anywhere in the shell**. Clipboard-import
  failures, subscription-update results and engine errors are written to a surface that has no
  window (02-inventory §0.3, V11).
- Neither reference has a structured recovery layer either (3.2.2) - so this is the shared blind
  spot rather than a competitive deficit. But right now we cannot even say «Готово».

### 5.8 Navigation and back

**BEHIND on both platforms, on platform conformance rather than on the references.**

- **Android: the app never finishes on Back.** Three competing handlers, and `onKeyDown`
  (`MainActivity.kt:2298`) returns `true` unconditionally for `KEYCODE_BACK` and calls
  `moveTaskToBack(false)`. `android:enableOnBackInvokedCallback` is declared nowhere, so
  **predictive Back is not supported at all** (01-inventory §2.4). On Android 14+ the system
  animates the app closing and then it does not close. This is the exact tell `android.md` names
  (quoted at 00-rules 2.3).
- **Desktop: Escape does not go back.** `_subStack` is popped only by the toolbar `←` button;
  `Key.Escape` is handled in four places, all local modals. The only exit from Buy, Devices,
  History, Login and the eight settings sub-pages is one 40px target in the top-left
  (02-inventory §0.4, §2.3). No mouse-button-4, no `Alt+←`. 00-rules 12.2 makes Escape mandatory.
- Sub-pages on desktop are global rather than per-tab, so Account → Buy → rail-click «Настройки»
  leaves Buy sitting on top of Settings (02-inventory §2.3).

Different, and not yet decided: **Android has four destinations (Главная / Сервера / Настройки /
Аккаунт), desktop has three, and desktop has no «Серверы» at all** - servers are a column inside
Home (02-inventory §1.1.2). The parity contract (00-rules 13) says the destination set and its
order are identical across platforms. We are currently in violation of our own law and, on
desktop, we have inherited Incy's duplicate-list problem (2.2.8) as a consequence. **This is the
question spec 03 has to answer first**, because it determines the rail, the bottom bar, the
compact scroll, and whether server search has a home.

### 5.9 The systems: type, colour, motion, icons

**Type - ahead in definition, behind in adoption.** The ramp is correct and complete
(01-inventory §3.1). Then: **34 of 73 Android layouts contain no `TextAppearance.App.*` at all**,
~100 raw `android:textSize` occurrences across 21 files, and layer-B rows render titles at a
fourth undeclared "16sp title" size (§3.4). Incy's typographic failure is that the type is too
small; ours is that it is *inconsistent*, which is worse, because it reads as three different
apps in one APK (§0). One positive: **no `textAllCaps` anywhere** and section headers are
sentence-case bold - the eyebrow ban is genuinely respected in the code even where the specs
still say UPPERCASE (see section 4).

**Colour - one real advantage, leaking.** The mono theme is a runtime `ThemeOverlay` that
neutralises every accent *attribute*, and it works precisely because the accents are attrs rather
than hex (01-inventory §6). Neither reference ships a documented monochrome theme. But every raw
`@color/` in a layout is a hole in it: the protocol chip, the JSON chip, the ping dot and value,
the selection indicator, the speed arrows, the traffic bar tint and all the settings tiles were
found rendering in colour inside the "black and white" theme (`design-review-c942766.md` §1.1,
a **blocker**). Two measured contrast failures also stand: the protocol chip at **4.0:1** in dark
(`item_recycler_main.xml:86` against `colorPrimaryContainer`) and the traffic pill at **2.9:1**
(01-inventory §3.5).

**Motion - ahead.** A real token scale (90 / 160 / 220 / 300 / 40 / 600 with three named
interpolators), mirrored 1:1 on both platforms, with reduced motion as a live contract
(00-rules 3.7, 8.8, 12.5). Nothing in either reference's evidence base is comparable. We spend
it badly - ambient breathing and sonar loops at idle on both platforms (01-inventory §3.5,
02-inventory §4.2) - and 00-rules 8.4 allows exactly one 600ms hero moment in the whole product.

**Icons - behind.** Desktop runs **two icon-button systems simultaneously**, the 32px legacy
`Button.IconButton` and the 40px canonical `Button.IconButton40`, with a comment admitting the
dedup pass never finished, plus nine verbatim copies of the same local pressed-style override
(02-inventory V7). Android runs **three row grammars** with three divider insets (44 / 68 / 72dp)
and four card radii (01-inventory §3.7, §3.3). Incy's icon language, whatever else is wrong with
it, is at least one language.

### 5.10 Scoreboard

| Surface | vs Happ | vs Incy | The one sentence |
|---|---|---|---|
| First frame / sign-in | n/a (no account) | n/a (no account) | **Behind ourselves.** 20 buttons on desktop, a floating card on Android |
| Home / connect | different | **behind** | Five layers to say "off"; they use two |
| Server list | **behind** | **behind** | Android cannot edit a server at all; desktop cannot search |
| Subscription block | **behind** on protocol, ahead on capture seam | **behind** on protocol | 2.9:1 label on a moving fill, delete on long-press |
| Account / commerce | **ahead** | **ahead** | They have no account; we have four good screens |
| Settings (Android) | ahead of "Advanced/Other" | **behind** | 1 536 lines, 20 rows, 30 hidden settings |
| Settings (desktop) | **ahead** | **ahead** | Affordance honesty: five row types, five promises |
| Feedback / errors | **behind** | **behind** | Android: 40 Toasts. Desktop: no surface at all |
| Back / navigation | **behind** platform | **behind** platform | Back never finishes; Escape never returns |
| Motion + reduced motion | **ahead** | **ahead** | A real token scale and a live lite-mode contract |
| Mono theme | **ahead** | **ahead** (leaking) | Right architecture, ~10 raw-colour holes |
| Type / icon consistency | **behind** | **behind** | Three row grammars, two icon-button systems |

---

## 6. The three things our app must own

Not features. Three principles neither reference can adopt without becoming a different product,
each with a mechanism, values, states and copy.

### 6.1 Подписка - a live object with states, not a header someone typed

**Why they cannot have it.** Both references are bring-your-own-config clients. The operator is a
URL. The best either can do at T-3 days is a header, a URL button or a push
(1.1.9, 2.2.7). Neither can say «Истекает через 3 дня» *as a property of the thing on screen*,
because there is no thing - only text.

**The mechanism.** One subscription state machine, resolved in one place, rendered identically on
four surfaces. The states are already named in 00-rules 15 and must not be re-invented per screen:

| State | Condition | Chip | Copy | Primary action |
|---|---|---|---|---|
| `нет подписки` | no managed sub | none | `Подписки пока нет` | `Купить` |
| `триал` | `Subscription.isTrial` from the backend, never inferred | neutral | `Пробный период` | `Купить` |
| `активна` | expiry > 7 days and quota < 90% | green `colorTertiary` `#22C55E` | `Активна до 12.08.2026` | none |
| `истекает` | expiry <= 3 days **or** quota >= 90% | warning chip (the one permitted yellow use, 00-rules 1.4.1) | `Истекает через 3 дня` | `Продлить` |
| `истекла` | expiry in the past | red fill `colorError` `#F04452`, text `@color/ping_bad` `#FF6069` | `Подписка истекла` | `Продлить` |
| `лимит устройств` | devices used == allowed | warning | `Достигнут лимит устройств` | `Устройства` |

**The four surfaces, one vocabulary:**

1. the subscription block on Home (replacing today's carousel page),
2. the subscription card in Аккаунт,
3. one gate line under the connect control - and only when the state is not `активна`,
4. the ongoing notification's second line.

**Rules that make it an object rather than four renderings:**

- The chip carries a **word and a colour and, when it is not neutral, a glyph** (00-rules 6.3).
  Never colour alone, never a bare dot.
- The state is computed once, from backend truth. `isTrial` comes from the backend and is never
  inferred from tariff name or squad - in this deployment the trial squad *is* the paid base
  squad, so squad-based detection misclassifies real paying customers.
- Expiry renders as `до 12.08.2026` when far and `через 3 дня` when near; the crossover is 7 days.
  Numbers use the Numeric role with `tnum` (00-rules 5.5) so a ticking countdown does not jitter.
- The renewal action is `Продлить`, is the **only** filled accent surface in its container
  (3.6), and disappears in `активна`. A CTA that is always present is furniture.
- Traffic renders as `12,4 ГБ из 50 ГБ` in `App.Subtitle` **beside** the bar, never on top of it -
  killing the 2.9:1 defect (5.4). Unlimited renders `12,4 ГБ · без ограничений`, and
  `subscription-userinfo: 0` hides the block entirely [P].
- Component tree, both platforms:

```
Subscription block                    [Card, radius 20, 1dp outlineVariant, elev 0, pad 16]
├─ row 1   title (App.Title 16/700, max 2 lines)   ·   state chip (App.Chip 11/500)
├─ row 2   caption (App.Caption 12)  "Обновлено 5 минут назад"
├─ row 3   traffic bar   4dp track colorSurfaceHighest / fill colorPrimary   +
│          label App.Subtitle 13 onSurfaceVariant, BESIDE the bar
├─ row 4   [operator message, if any - see 6.3]     dismissible
└─ row 5   one action zone: at most ONE filled accent + at most one text action
```

### 6.2 Отказ - a designed screen, not a log

**Why they cannot have it.** Happ's answer to a failing subscription is server-side
(`fallback-url`); Incy's is out-of-band (push). Neither is on the device at the moment the user is
staring at a tunnel that will not come up. For our audience - Russia, Iran, DPI networks - that
moment is the normal path (`ux-recommendations.md` §0).

**The mechanism, three parts:**

**(a) A closed error taxonomy.** One sealed type, every case carrying `{причина, основное
действие, второе действие, retryable}` (`ux-recommendations.md` §5). No raw core strings, no exit
codes, ever, on any product surface:

| Case | Копия (00-rules 9.4) | Основное | Второе |
|---|---|---|---|
| `NoNetwork` | `Нет подключения к интернету. Проверьте сеть и повторите.` | `Повторить` | - |
| `AllUnreachable` | `Не удалось подключиться ни к одному серверу. Так бывает в ограниченных сетях.` | `Другой сервер` | `Включить обход блокировок` |
| `HandshakeTimeout` | `Сервер не отвечает. Выберите другой сервер или повторите позже.` | `Повторить` | `Другой сервер` |
| `SubExpired` | `Подписка истекла. Продлите её, чтобы подключаться.` | `Продлить` | - |
| `DeviceLimit` | `Достигнут лимит устройств. Отвяжите одно из устройств в разделе «Устройства».` | `Устройства` | - |
| `PermissionDenied` | `Нужно разрешение на VPN-подключение.` | `Разрешить` | - |
| `SubUpdateFailed` | `Не удалось обновить подписку. Проверьте ссылку провайдера и повторите.` | `Повторить` | - |
| `CoreCrash` | `Что-то пошло не так. Повторите попытку.` | `Повторить` | `Отправить отчёт` |

**(b) One silent retry before any error is shown.** A transient drop re-attempts once with the
ring in a reconnecting micro-state at `motion_state` 220ms; only the second failure produces a
surface. Never punish the user with an error the app could have absorbed.

**(c) The feedback channel the owner will accept.** Toasts are rejected on Android by our own law
(1.4.8) and by the owner on desktop; there is nothing behind them today (5.7). Replace both with
**one inline status strip in the shell**, identical on both platforms:

```
Status strip                 [full width, 48dp min, colorSurfaceContainerHigh #1A1D21,
                              1dp top hairline colorOutlineVariant, NO shadow]
├─ 20dp glyph  (info / warning / error, from the one icon family)
├─ text        App.Body 14, max 2 lines
└─ text action App.Title 16/700 colorPrimary   ("Повторить" / "Продлить" / "Понятно")
```

- Position: Android - above the bottom navigation, respecting the inset. Desktop - docked below
  the sub-page toolbar, above content. Never floating, never over the connect control.
- Enter `motion_reveal` 300ms `ease_out_quint` translateY 8→0 + alpha; exit 225ms (00-rules 8.5).
- Auto-dismiss at 5s for successes; **errors persist until acted on or dismissed** - an error the
  user did not see is an error we did not report.
- Reduced motion: `Dur.Instant`, snap to end state.
- This strip also replaces the deprecated custom `toast_status.xml` and gives the desktop's dead
  `MsgView` a purpose: the strip is the transient view, a log page under Настройки › О приложении
  is the durable one.

**(d) Offline is a designed state, not an error.** Keep the last known data, mark it
`Данные могли устареть`, disable network-dependent actions, and show one quiet persistent bar:
`Нет сети. Показаны последние данные.` with `Повторить` (00-rules 9.6). Neither reference does
this; a blank screen in a censored network is ambiguous in the worst possible way.

### 6.3 Ничего не происходит незаметно

One principle, two applications. The first is ours already and needs promoting to law; the second
is a direct answer to the worst thing either reference does.

**(a) Row-level: the affordance declares the outcome.**

The desktop settings grammar (5.6) becomes the product's row law, on both platforms, on every
list - settings, account, providers, routing, devices:

| Trailing affordance | Promise | Android | Desktop |
|---|---|---|---|
| chevron 20dp `colorOnSurfaceVariant` | pushes a screen | `ic_chevron_right` | `Geo.Chevron` |
| chevron that rotates 0 → 90 over `motion_state` 220ms | expands inline, here | same glyph, rotated | same |
| `unfold_more` 20dp | cycles the value in place | `ic_unfold_more` | `Geo.UnfoldMore` |
| segmented control | 2-state change, applied now | `MaterialButtonToggleGroup` | segment class |
| switch | boolean | `MaterialSwitch` | toggle class |
| value text only, no glyph | read-only fact | `App.Subtitle` | `TextBlock.Subtitle` |

Enforcement: **exactly one trailing element per row** (00-rules 4.5); the current value always
precedes it (Incy's best idea, 2.1.6); the whole row is the target, minimum 48dp Android / 40px
desktop; and a row whose affordance does not match its behaviour is a defect, not a nit. This
retires the six single-choice `AlertDialog` pickers on Android (5.6) - a picker with more than six
options becomes a push screen, one with two to four becomes a segment, one with a short cycle
becomes `unfold_more`.

**(b) System-level: operator power is visible and revocable.**

Incy lets a provider silently set per-app proxy mode and list, force TCP fragmentation *over the
user's own setting*, enable UDP noise, redirect DNS resolution, install an auto-updating routing
profile, and remove the subscription URL from the user's own Share / Copy / QR / backup
(2.2.6) [P]. Happ lets a provider pick a UI colour (1.2.4) [P]. Both treat the operator as
trusted and invisible.

We take the protocol - we need it - and we render it. **One screen: Настройки › Провайдер.**

```
Настройки провайдера                                     [seamless toolbar, 56dp]

Сообщения                                                [section header 16/700]
  Объявление            Показывать              [switch]      ← local, always the user's
  Поддержка             @departamentvpn         [chevron]

Что настроил провайдер                                   [section header 16/700]
  Прокси по приложениям Задано провайдером      [chevron]     value = "12 приложений"
  Фрагментация          Задано провайдером      [chevron]     value = "Включена"
  Маршрутизация         Задано провайдером      [chevron]     value = "Обновляется"
  Определение адресов   Задано провайдером      [chevron]     value = "DoH"

  [text button, colorPrimary]  Вернуть мои настройки
```

The rules:

1. **Every directive that changes device behaviour appears here**, in Russian, with the subtitle
   `Задано провайдером` and the value it set. Nothing applies invisibly.
2. **Anything that overrides a user setting is revertable** - `Вернуть мои настройки` restores the
   local value and marks the subscription as "local override", which the next refresh respects.
3. **`hide-url` is refused as specified.** We may honour "do not put this in a shared backup"; we
   never remove the user's ability to read their own URL. A managed product may keep secrets from
   a scanner, never from its owner.
4. **The operator supplies content and severity, never presentation.** Text, links, and one of
   three severities (`info` / `warning` / `error`) which we map onto our own tokens. Colour
   directives (`sub-info-color`, `banner-bg-color`, `banner-button-color`) are parsed and the
   value discarded. Icon choices are an **enumeration we render**, per Incy's `icon-presets`
   pattern generalised (2.1.11): the operator names a key, we draw our glyph, at our size, in our
   colour, with a documented fallback for unknown keys.
5. **One operator-message component, one lifetime.** Not Happ's three channels. `announce` /
   `announce-url` / `sub-info-*` all resolve into the single message row in the subscription block
   (6.1, row 4), <= 200 chars and <= 5 lines enforced in the parser, dismissible, and dismissal is
   keyed on a hash of the message so a *new* message re-appears while the same one stays gone.
6. **No forced modal, at any duration.** The operator gets a row and a strip. It never takes the
   screen.

**Why this is a differentiator and not a compliance chore:** the whole objection to a managed VPN
is "someone else controls my connection". Every competitor answers that by hiding the control. We
answer it by showing it. That converts our structural disadvantage against a BYO-config client
into the reason to choose us, and it is not copyable by either reference without them first
building an account system to hang it on.

---

## 7. Handoff - what the downstream specs must carry

| Spec | Must resolve, from this analysis |
|---|---|
| **03 direction** | The destination set: 3 or 4, and whether «Серверы» is a destination or a column. Android and desktop currently disagree (5.8), and the answer decides where search lives. Also: formally record that the gradient/glow layer is replaced, not amended (section 4) |
| **10 Home / connect** | Figure on ground, recessed disc, arc-length progress, ring settles green **with the word** at connect. Kill both idle ambient loops. Replace the `↑`/`↓` text glyphs. One subscription state line, never the whole block, under the hero |
| **11 Servers** | Restore per-item actions on Android (P0, 01-inventory §5.1). Add search on desktop. Sort by `none / ping / name`. The unified server icon replacing emoji and raster flags. Fix the 4.0:1 protocol chip |
| **12 Settings** | Adopt the affordance grammar as law on both platforms (6.3a). Card = group, current value on every row, sentence-case headers. Surface the ~30 hidden Android settings or delete them explicitly. `MaxWidth` on desktop. One home for «Автообновление подписки» |
| **13 Account** | Keep the four-state grammar; un-nest the desktop flyout carousel into sub-pages; one accent CTA per card; the state machine of 6.1 as the card's header |
| **14 Sign-in / onboarding** | One accent action, one secondary, five methods behind «Другой способ входа». Both platforms, same shape |
| **15 Subscription + provider** | The directive catalogue with severities and caps; the single message component; Настройки › Провайдер (6.3b); `hide-url` refused as specified |
| **16 Feedback / errors** | The status strip component, the error taxonomy, the one-silent-retry rule, the offline state (6.2) |
| **20-24 systems** | Icon family unification (one desktop icon-button system, one Android row grammar); type-role adoption sweep; the mono-theme raw-colour leak list from `design-review-c942766.md` §1.1 |

---

## 8. Ten lines

1. Evidence is asymmetric: Incy is documented visually (7 screenshots) **and** protocolar; Happ is documented protocolar only. No claim here rests on a screenshot I have not been shown.
2. **Happ's best idea** is that the operator owns a strip of the UI over HTTP headers, with length caps in the protocol so the layout can never break.
3. **Happ's worst idea** is `sub-info-color` - the operator picks our accent - plus three overlapping banner channels and a tab strip that stops scaling at four subscriptions.
4. **Incy's best ideas** are the figure-on-ground hero with a recessed idle disc, the current value on every settings row, and `icon-presets`: the operator picks from an enumeration and we own the rendering.
5. **Incy's worst ideas** are the glow, ALL-CAPS tracked labels, eight tile colours, iOS switches on Android, a duplicated server list on Home, and provider directives that silently re-scope the tunnel and hide the user's own URL.
6. Both are right that the subscription must be a visible object, the connect control must dominate, servers group under their source, and item actions belong in a sheet. None of that is a place to win.
7. Both are blind to accounts, to designed failure, and to the platform's real constraints. That is the opening.
8. Honestly placed: we are **ahead** on account/commerce, motion tokens, reduced motion, the mono architecture and the desktop settings affordance grammar; **behind** on per-item server actions (Android cannot edit a server at all), desktop search, feedback (40 Toasts / no surface), Back and Escape, and system consistency; **heavier for no gain** on the connect hero.
9. Three things to own: **подписка as a live six-state object**, **отказ as a designed screen with one silent retry and an inline status strip**, and **ничего не происходит незаметно** - every row declares its outcome and every provider-applied setting is listed and revertable.
10. Four of our own documents specify gradients, glass, glows, UPPERCASE labels and iOS switches; section 4 supersedes them point by point so nobody implements the banned half of the reference.
