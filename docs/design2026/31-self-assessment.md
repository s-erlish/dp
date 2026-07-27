# 31 - Self-assessment: what our two apps actually look like today

**Date:** 2026-07-26
**Reviewer stance:** I opened every layout and every view as if I had just downloaded a competitor's
build and been asked whether it is better than Happ or Incy. Nothing here is written to be kind.
**Method:** read the source, not the docs. Android: all 71 files in
`/home/user/dp/V2rayNG/app/src/main/res/layout/` plus `res/values/{colors,dimens,styles,themes}.xml`,
`res/values-night/colors.xml`, `res/drawable/bg_*.xml`, `res/anim/*`, `res/values/strings*.xml`, and
the two Activities the owner named. Desktop: all 49 `.axaml` in
`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/` plus `Assets/GlobalResources.axaml`,
`Assets/GlobalStyles.axaml`, `Common/L.*.cs`.
**Law applied:** `/home/user/dp/docs/design2026/00-rules.md`, `/home/user/dp/CLAUDE.md`,
`/home/user/dp/.claude/skills/impeccable/**`.

---

## 0. The one-paragraph verdict

We do not have one app with a design. We have **four**: a finished-looking desktop product
(Login / Onboarding / Account / ConnectHero), a half-finished Android product (Home / Account /
Buy / Devices), a second Android product built by someone who never read the token file
(Local proxy / Provider settings / URL schemes / Backup - 12dp gutters, 14dp paddings, 60dp rows,
inline `textSize`), and the raw 2019 upstream (every server editor, the routing editor, the sub
editor on Android; 22 of 49 windows on desktop). The owner's word for this is «абы как» and he is
right. On top of that, three things are wrong at the system level and they poison every screen at
once: **the home background is a navy radial gradient** (the exact "gamer VPN" reflex `00-rules.md`
§2.4.1 bans), **every coloured icon tile in Android Settings resolves to the same blue** so the
accent budget is spent ~20x over on one screen, and **the app has no single language** - the default
Android string table is 384/463 English, so the permanent bottom navigation literally reads
`Home · Servers · Настройки · Аккаунт`.

**Overall grade today: Android C-, Desktop B-.** Neither is "готовое приложение с ахуенным дизайном
в стиле 2026". The desktop is two-thirds of the way there. Android is one-third.

---

# PART A - ANDROID, SCREEN BY SCREEN

## A1. Sign-in - `res/layout/activity_login.xml` + `ui/LoginActivity.kt`

**Grade: D-.** The worst screen we ship. The owner said «сейчас все выглядит плохо». He is
understating it.

**What a user sees first.** Two grey rounded rectangles stacked on a dark page, both the same width,
both the same corner radius, both with a bold heading and a small grey line under it, both with a
full-width blue pill at the bottom. There is no logo, no wordmark, no product name, no headline, no
single sentence explaining what they are signing into. The only branding is a system toolbar with
the word «Вход» in it (`LoginActivity.kt:64` - `setContentViewWithToolbar(..., title = "Вход")`),
which comes from `activity_base.xml` and is the same chrome the routing editor uses.

**What the eye goes to second.** Nothing, or rather: it ping-pongs. There are **two filled accent
buttons on the same screen** - `btn_telegram` (`activity_login.xml:54-67`) and `btn_site`
(`:193-202`), both `?attr/colorPrimary`, both 52dp, both full width, both `textStyle="bold"`. Add
`btn_register_site` (`:287-297`, outlined blue) and `btn_restart` (`:96-106`, outlined blue) and the
screen carries **four blue controls**. `00-rules.md` §4.3 says "Two filled accent buttons on one
screen is a defect", §3.6 caps a screen at one filled accent surface. We are at 400% of the budget.
The user's eye has no idea which is the intended path.

**What is competing for attention that should not be.** The Telegram card is a *card*. The site card
is a *card*. They are visually identical siblings, which encodes "these two are equally weighted
choices" - but they are not: Telegram is one tap, site login is email + password + possibly a 6-digit
TOTP. The layout tells the user the opposite of the truth. Nothing is a default. Nothing is demoted.

**What is unreadable or cramped.** Nothing is cramped - it is the opposite, it is loose and shapeless.
But it is *broken* in three places a designer would flag on sight:

1. **The error message is nowhere near the error.** `tv_error` (`:301-311`) is a centred `TextView`
   at the very bottom of the whole `NestedScrollView`, *after* both cards. If email/password fails,
   the message renders below the "Регистрация на сайте" button, possibly below the fold. §7.4 of
   `00-rules.md`: "Error text **below the field**, in `Brush.RedText`, with the field border in red."
   We have neither. The colour is `?attr/colorError` = `#F04452`, which measures 4.88:1 - the rules
   explicitly reserve `@color/ping_bad` for error *text* because of that (§3.5, change log 2026-07-26).
2. **No helper-text slot.** `til_email` and `til_password` have no reserved helper line, so the
   moment a validation message appears the whole card jumps. §7.4: "Helper text below, present in
   the markup even when empty so the layout does not jump."
3. **The 2FA block is inserted in the wrong place.** `layout_2fa` (`:216-285`) appears *between*
   the primary submit button and the "Регистрация на сайте" button. So after a successful password
   the layout mutates in the middle: a hairline, a paragraph, a field, a second 52dp blue button,
   and then the registration button is still sitting underneath, offering to create a new account
   in the middle of an in-flight login.

**Off-system details.** `app:cornerRadius="26dp"` on four buttons (`:64, :105, :202, :273, :296`) -
26 is not a token; the token is `@dimen/radius_pill` 100dp. `android:layout_height="52dp"` is a raw
literal (three times) where `Size.CtaTall` exists on the other platform and no Android dimen exists
at all. `android:textStyle="bold"` on Space Grotesk buttons is a synthetic bold, banned by §5.4.
`ProgressBar style="?android:attr/progressBarStyle"` (`:79-84`) is the *platform* spinner, not the
Material circular indicator used everywhere else in the app. Eight off-scale dp values in a 314-line
file.

**States.** Loading exists (`pb_site`, `pb_awaiting`) but is a bare spinner overlaid on a still-blue
button, so it reads "the button is now broken" rather than "we are working". There is no disabled
state, no success state, no offline state, no first-run guidance, no "Забыли пароль?" path, no
password-strength, no magic link - all of which **exist on desktop** (see B1). Android's sign-in is
a strictly worse product than the same company's desktop sign-in.

**The single worst thing about it:** it does not look like a sign-in screen. It looks like a settings
page that happens to have two password fields on it, because it *is* one - it inherits the sub-page
toolbar chrome, it has no hero, no brand, no single decision, and it hands the user four blue buttons
and no opinion.

**Two grades up in one move:** delete both cards; make it one centred column on the page background
- shield tile 64 + `departament` wordmark + `Headline` «Вход в departament» + one subtitle - then the
email/password form with **one** filled `Войти`, and demote Telegram to a single tonal button under
an «или» divider, with site-register and forgot-password as text links. That is exactly what
`LoginView.axaml` already does on desktop; the design work is done, Android just never received it.

---

## A2. First tab at launch (Home) - `res/layout/activity_main.xml` + `ui/MainActivity.kt`

**Grade: C-.** Better than the sign-in screen, but it reads as a build-in-progress with three
different design decisions layered on top of each other and none of them removed.

**What a user sees first.** A **navy radial gradient**. `activity_main.xml:8` sets
`android:background="@drawable/bg_home_gradient"`, and `drawable-night/bg_home_gradient.xml` is a
560dp radial from `#1B2D50` (navy) through `#0E141F` to `#0A0B0D`. Behind the shield sits
`bg_connect_glow` (`:207-212`), a second radial `#594C8DFF → #264C8DFF → #004C8DFF`, i.e. a **blue
glow**. Behind that, `bg_connect_ring` (`:214-218`), two concentric blue strokes at 20% and 50%
alpha. Behind the nav, `bg_bottom_nav_scrim`, a **third** gradient, 160dp tall (`:512-517`).

So the first frame of our product is: navy gradient + blue halo + concentric blue rings + a big
circular disc + a shield. That is the category reflex, verbatim. `00-rules.md` §2.4.1: "if your
screen reads as 'gamer VPN', it failed"; §6.5: "**No gradients.** Not on backgrounds, not on
buttons, not on the connect control, not behind the hero"; §1.4.3: "No decorative gradients or
glows". Four separate law violations in the app's most-seen 40 pixels of every screen. And the light
theme is worse: `drawable/bg_home_gradient.xml` is `#FFFFFF → #EEF3FB → #DFE6F1`, so on light the
whole app sits on a blue-tinted wash that no other screen shares.

**What the eye goes to second.** A row of three tiny numbers glued to the very top of the scroll -
`↑ 0 B/s`, `00:00:00`, `↓ 0 B/s` (`:58-172`), at 13sp / 14sp / 13sp. This is the hero-metric template
(`00-rules.md` §1.1: "Big number, small label, supporting stats" - SaaS cliché) turned inside out:
three live counters, all zero, all at the top of the page, before the user has done anything. And
they are built from a **42dp invisible spacer** (`:73-77`) whose only job is to fake-centre a
weighted row against the "+" button opposite it. That is a layout hack in the app's first screen.

The up/down arrows are literal text: `android:text="↑"` and `android:text="↓"` (`:98`, `:140`) -
typographic characters standing in for icons, in a codebase that has a full vector icon set. They
will not align to the numeral baseline, they will not scale with the icon system, and they change
shape with the system font.

**What is competing for attention that should not be.**

- The connect disc is 176dp inside a 230dp ring frame with a 212dp progress indicator (`:202, :247,
  :255`). Three unrelated circles at 176/212/230. None of those numbers are tokens.
- The account chip, the "+" button, the stats row, the shield, the server name label, the
  subscription meta card, and the server list all appear in the same scroll with roughly equal
  visual pressure. `layout_subscription_meta_bar.xml` alone carries **five** interactive controls in
  its title row (chevron, ping, refresh, pin, plus a spinner) and **two more** at the bottom
  (Поддержка, Telegram). Seven affordances on a card that is nominally "info about your subscription".
- The empty state ships **two centring spacers** (`:374-380`, `:402-408`) that are toggled
  `visibility` at runtime to fake vertical centring inside a `fillViewport` scroll. Again: a hack in
  the first screen.

**What is unreadable or cramped.** The bottom navigation. `BottomNavLabel` is 11sp
(`values/styles.xml`), the icons are 24dp, the active pill is 34x3dp, and the vertical rhythm is
`3dp / 3dp` margins (`:560, :570-572`) - **3dp is not on the scale** (§3.1 bans 6/10/14/18/20/28;
3 is not even in that list of near-misses). The whole bar is `minHeight="56dp"` with icon + label +
pill stacked inside, so at font scale 200% it will clip. Meanwhile the nav labels themselves read
**`Home · Servers · Настройки · Аккаунт`** - `values/strings.xml:336-337` are still the upstream
English strings while `:565` and `strings_nav.xml:8` are Russian. On any non-Russian device that is
the permanent, always-visible chrome of the app.

**What is inconsistent with the screen next to it.** The Home tab has no title. The Servers tab has
a 24sp `Headline` title («Сервера») plus four 36dp icon buttons plus a search pill
(`layout_servers_header.xml`). The Settings tab has no title, it starts straight into a section
label. The Account tab has no title. `MainActivity.showTab()` hides the AppBarLayout on every tab
(`MainActivity.kt:448`), so the product has no screen-title system at all - one tab invented its own
and the other three have none.

**Off-system details.** 32 off-scale dp values in this one file. Three inline `textSize`. The memory
card (`:312-367`) is a full card with a status dot and the label **"App memory"** in English
(`values/strings.xml:355`) parked in the middle of the connect flow. `app:cardCornerRadius="20dp"`
hardcoded (`:321`) where `@dimen/radius_card` exists.

**Copy.** `home_welcome_title` = **«Приветствуем!»** - an exclamation mark, banned by §9.1, and a
greeting no product in this category uses. `home_empty_title` = «У вас пока не добавлены подписки.»
- a trailing full stop on a title (§9.2), passive voice, and it is not the §9.5 formula.
`home_empty_subtitle` = «Добавьте подписку, чтобы начать пользоваться» - «пользоваться» dangling
with no object.

**The single worst thing about it:** the screen has three competing centres of gravity - the stats
strip, the shield, and the subscription card - and the background gradient is louder than all three.
Squint at it (the §4.3 acceptance test) and you see a blue smear with a circle in it. There is no
hierarchy, only decoration.

**Two grades up in one move:** delete every gradient (`bg_home_gradient`, `bg_connect_glow`,
`bg_bottom_nav_scrim`) and put the page on flat `#0A0B0D`; move the three counters *under* the
shield and show them only when connected. The hero becomes the only thing on the screen above the
fold, and the screen immediately reads as designed rather than decorated.

---

## A3. Servers tab - `layout_servers_header.xml`, `item_recycler_main.xml`, `item_section_header.xml`, `layout_servers_empty.xml`

**Grade: C.**

**First.** A 24sp «Сервера» title with **four** 36dp icon buttons crammed against the right edge
(collapse, refresh, speedtest, add). Four trailing actions in one header; §4.5 allows one trailing
affordance per row and §4.8 allows one trailing action on a toolbar with the rest in an overflow.
Three of the four are grey and one is blue (`btn_add`, `app:tint="?attr/colorPrimary"`), so the blue
one wins - which is correct - but the other three are then just noise at 36dp with 8dp padding, i.e.
**36dp touch targets, under the 48dp floor** (§7.2). Same problem in
`layout_subscription_meta_bar.xml` (four 36dp buttons) and `activity_devices.xml`'s row delete at
44dp.

**Second.** The search field. It is a raw `EditText` with `bg_search_pill` (14dp radius - not a
token; the shape lock says inputs are 12), `44dp` height, `12dp` top margin, `14dp` horizontal
padding, `4dp` end margin, `textSize="14sp"` inline, and a **filter** glyph
(`ic_outline_filter_alt_24`) where a magnifier belongs. Five off-scale values and a wrong icon in
one 20-line control.

**Competing for attention.** Every server row carries a flag **emoji** in a 28dp tile
(`item_recycler_main.xml:44-53`, `android:textSize="18sp"`, `tools:text="🇳🇱"`) *and* the same emoji
is often still in the remark text (`tools:text="🇳🇱 Netherlands • Amsterdam"`). §1.4.4 bans emoji as
UI chrome, §10.5 mandates one unified server icon - the flag PNG tile at 28 with a globe fallback,
which is exactly what desktop does (`ServerListView.axaml` even ships a `StripLeadingFlagConverter`
so the flag is not duplicated). Android has neither the tile nor the converter.

**Inconsistent with the screen next to it.** Rows on this tab are cards-in-disguise: a
`bg_server_row` selector with 20dp radius, 12dp padding, floating in 16dp side margins. Rows in
Settings are flush hairline-divided rows inside a card. Rows in Account are flush rows inside a card.
Rows in Devices are individual 20dp cards with 8dp gaps. Four row idioms, four screens.

**The single worst thing:** the empty state (`layout_servers_empty.xml`) is a different design
system from the header above it - `padding="24dp"`, `marginTop="64dp"`, `paddingStart="20dp"`,
`paddingTop="28dp"`, `marginTop="14dp"`, `marginTop="20dp"`, `marginTop="10dp"`, a 56dp blue icon,
and a title with no supporting line and no §9.5 formula. Seven off-scale values in 67 lines.

**Two grades up:** one unified `ServerRow` (flag tile 28 in the 40 slot, name, protocol chip, ping,
no emoji) and a header that keeps only «Сервера» + search + a single overflow.

---

## A4. Settings tab - `layout_settings_content.xml` (1536 lines)

**Grade: C+.** Structurally the most correct screen we have, and visually the loudest.

**First.** A wall of blue. There are 23 rows in 6 card groups. The tiles are declared as
`bg_icon_blue` x7, `bg_icon_green` x6, `bg_icon_orange` x3, `bg_icon_purple` x5, `bg_icon_yellow` x1,
`bg_icon_red` x1 - **but** `values/themes.xml` maps `iconTileBgGreen`, `iconTileBgOrange`,
`iconTileBgPurple`, `iconTileBgYellow` all to `@color/icon_tile_blue` and their glyph tints all to
`@color/icon_blue`. So **22 of 23 rows render an identical 20%-blue tile with an identical blue
glyph**, and one renders red. `00-rules.md` §3.6: "Coloured tiles are not decoration; they are a
category system, and a screen where every row has a different coloured tile has no category system,
only noise." We managed the inverse failure - a category system where every category is the same
colour - which is worse, because the code still *pretends* there are six categories. Accent budget
target is ≤10% of coloured pixels; this screen is at roughly 95%.

**Second.** The section labels, which are correct: `@style/SettingsSectionLabel`, 16sp/700,
sentence case, no tracking, no caps. That is the one part of the app that fully obeys the eyebrow ban.

**Competing for attention.** The chevrons are 18dp (`:88, :149, :340, ...` - 22 occurrences), which
is not a token (§10.3: 20dp for inline chevrons). Dividers are `marginStart="72dp"` (19 occurrences)
where the text origin per §4.1 is gutter 16 + tile 40 + gap 12 = **68**. So every divider is 4dp
short of the text it is supposed to align to, on every settings screen in the app.

**Inconsistent with itself.** The project ships `layout_setting_row.xml` and
`layout_setting_toggle_row.xml` - a proper reusable row with a **neutral** tile, a subtitle slot,
`stateListAnimator="@anim/press_scale"`, and correct tokens. `layout_settings_content.xml` does not
use them; it hand-inlines all 23 rows with coloured tiles and **no press animation at all**. Two row
components that disagree with each other, and the good one is dead code.

**Feedback.** 23 clickable rows, zero `press_scale`. Only `?attr/selectableItemBackground`. Across
the whole Android app, **8 of 71 layouts** use the press animation; 22 layouts have clickable rows
with none. §7.1 requires pressed state on every interactive element.

**The single worst thing:** it is a 1536-line hand-written wall. Any change to a row's anatomy has to
be made 23 times. That is why the Local proxy and Provider screens drifted (see A6) - there was
never a component to drift *from*.

**Two grades up:** move all 23 rows to `layout_setting_row.xml` / `layout_setting_toggle_row.xml`
with the neutral tile, and let exactly one row per screen carry the blue tile.

---

## A5. Account tab - `activity_account.xml` + `item_subscription_card.xml`

**Grade: B-.** The best-designed Android screen. Still noticeably thinner than its desktop twin.

**First.** Avatar 48 in a 52 box, name, and a blue pill «Пополнить» on the same line, then the
balance as `TextAppearance.App.Display` 34sp with tabular figures. That is a correct hierarchy and
it reads well.

**Second.** The referral chip. It is a `bg_acc_chip` pill with an 18dp copy glyph beside it, and the
tap target is `wrap_content` in both axes with no minimum height - so on a short code the whole row
can be well under 48dp tall (§7.2 violation).

**Competing for attention.** «Пополнить» is a filled accent button *and* the promoted «Купить
подписку» row below is `?attr/colorPrimary` text on a blue tile (`:401-420`) *and* the empty-state
`btn_buy_first` is another filled accent button (`:270-281`) *and* `btn_retry_load` is a tonal
button. Depending on state, two or three accent surfaces are visible at once.

**Unreadable/cramped.** `tv_avatar_initial` uses `android:textSize="20sp"` + `textStyle="bold"`
inline (`:76-77`) - the only inline textSize in the file, and a synthetic bold.

**Inconsistent with the screen next to it.** Rows here use `paddingVertical="@dimen/space_8"` and
`paddingStart="@dimen/screen_gutter"`; rows in Settings use `space_12` and `space_16`. Same visual
component, two paddings, two files. The divider inset is `72dp` here too - same 4dp error.

**What is missing versus desktop** (`AccountView.axaml`, 1474 lines vs our 560): health chip on the
subscription card, traffic meter, device meter, «Продлить» CTA, auto-renew toggle, upgrade flow,
add-devices stepper, payment-method chips, inline top-up flyout, sign-out. Android's Account tab is
a read-only summary; desktop's is a control panel. The owner asked for "the Account tab and every
button in it" to be reworked on both platforms - on Android most of those buttons do not exist yet.

**The single worst thing:** the subscription card (`item_subscription_card.xml`) is 75 lines and
shows a name, a badge, a date and a device count. It is the object the entire product sells and it
has less information density than the meta-bar on the Home tab.

**Two grades up:** port the desktop subscription card wholesale - health chip, expiry line that
leads with state, traffic pill, device meter, one full-width «Продлить».

---

## A6. Local proxy / Provider settings / URL schemes / Backup - `activity_local_proxy.xml`, `activity_provider_settings.xml`, `activity_url_scheme_list.xml`, `activity_backup.xml`

**Grade: D+.** These are the screens the owner means by «абы как». They *look* like the Settings tab
from three metres away and fall apart at arm's length.

Measured drift, per file, against the token scale:

| File | off-scale dp | inline `textSize` | gutter | row height | card radius |
|---|---|---|---|---|---|
| `activity_local_proxy.xml` | **113** | **37** | 12dp | 60dp | 20dp literal |
| `activity_provider_settings.xml` | **84** | **15** | 12dp | 60dp | 20dp literal |
| `activity_url_scheme_list.xml` | **43** | **20** | 12dp | - | 20dp literal |
| `activity_backup.xml` | **36** | 4 | 12dp | 60dp | 20dp literal |
| (`layout_settings_content.xml` for reference) | 53 | 0 | **16dp** | **56dp** | `@dimen/radius_card` |

Every row in these four screens is `minHeight="60dp"`, `paddingStart/End="14dp"`,
`paddingVertical="10dp"`, `marginStart="14dp"` on the text column, `marginTop="2dp"` on the subtitle,
title at `textSize="16sp"` and subtitle at `textSize="12sp"` - **none** of which are tokens, and all
of which are 2-4dp off the real Settings row. Put the two screens side by side and the cards are
8dp wider, the rows 4dp taller, the text 2dp further from the tile, and the subtitle sits 2dp under
the title instead of 4dp. That is precisely the "subtly-off component" failure of the product slop
test (§2.2): nothing is *wrong* enough to name, everything is wrong enough to distrust.

`activity_local_proxy.xml` additionally builds a memory chip group out of five
`MaterialButton style="?attr/materialButtonOutlinedStyle"` at `44dp` height and `13sp` text inside a
`MaterialButtonToggleGroup` - a segmented control that shares no styling with the segmented control
on desktop (`Button.Segment`) or with the one in `SettingsView.axaml`.

**The single worst thing:** the URL-schemes note card renders its title with
`android:textSize="15sp"` + `textStyle="bold"` (`activity_url_scheme_list.xml:52-56`). **15sp does
not exist** in the ramp (§3.4: "Do not add a step. 15sp does not exist."), and it is a synthetic bold
on a Space Grotesk-adjacent surface.

**Two grades up:** delete every hand-inlined row in these four files and replace with
`layout_setting_row.xml` / `layout_setting_toggle_row.xml` includes. Nothing else. The screens become
correct by construction.

---

## A7. Server editors, sub editor, routing editor, About - `activity_server_{vless,vmess,trojan,shadowsocks,socks,wireguard,hysteria2,group,proxy_chain}.xml`, `activity_sub_edit.xml`, `activity_routing_edit.xml`, `activity_about.xml`, `layout_transport.xml`, `layout_tls.xml`, `layout_address_port.xml`

**Grade: F.** Untouched 2019 upstream V2rayNG.

**What a user sees.** Bare `EditText`s with the platform underline, bare `Spinner`s with the platform
triangle, labels as unstyled `TextView`s at
`android:textAppearance="@style/TextAppearance.AppCompat.Subhead"`, `padding_spacing_dp16` spacing
tokens from the old scale, and **English strings** (`server_lab_remarks` = "remarks",
`server_lab_address` = "address", `server_lab_port` = "port", `server_lab_alterid` = "alterId").
Sentence case is not even attempted - the labels are lowercase English nouns.

`activity_about.xml` is five identical unstyled rows with 24dp icons at 16dp padding and
`TextAppearance.AppCompat.Subhead`. No tiles, no chevrons, no card, no section header, no Russian.

**Scope of the problem, measured:** **50 of 71 Android layouts contain zero `TextAppearance.App.*`
references.** Some of those are genuinely tiny (`activity_none.xml`), but the editors, the routing
list, the sub editor, About, `layout_tls.xml` (19 legacy refs), `layout_transport.xml` (18) and
`activity_sub_edit.xml` (24) are full screens the user reaches from the "+" menu and from every
server's action sheet.

**Copy, measured:** in `res/values/strings.xml`, **384 of 463 translatable strings contain no
Cyrillic at all**. The redesign added Russian in `strings_account.xml`, `strings_auth.xml`,
`strings_buy.xml`, `strings_nav.xml`, `strings_settings_hub.xml` etc., but left the upstream table
alone. `values-ru/` carries 459 strings and is missing 321 of the base set, so the two tables do not
overlap: the *new* screens are Russian only in the default set, and the *old* screens are Russian
only in `values-ru`. On a Russian phone this mostly papers over; on any other locale the app is a
bilingual patchwork with `Home · Servers · Настройки · Аккаунт` in the nav bar.

**The single worst thing:** a user who taps «Изменить» on a server leaves our 2026 product and
lands in a 2019 Chinese-origin tool, mid-session, with English field labels.

**Two grades up:** one `ServerEditorRow` component (label above, `TextInputLayout` outlined 12dp,
helper slot, Russian label), applied to `layout_address_port.xml` + `layout_transport.xml` +
`layout_tls.xml`, which are `<include>`d by all nine editors - fixing three files fixes nine screens.

---

## A8. Bottom navigation - `activity_main.xml:525-701` + `MainActivity.updateNavSelection()`

**Grade: C.** Correct in behaviour, wrong in every measurement.

Selected state reads on two axes (accent tint + weight 700 + a pill) which satisfies §7.1, and there
is no ripple glow, which satisfies the owner's 0.4.8. Good. But: the labels are `Home`/`Servers` in
the default locale; margins are `3dp` x3 per item; the pill is `34x3dp` with a `2dp` corner radius
(`bg_nav_dot.xml`); press is `nav_press.xml` at scale **0.92** with durations **100/120ms** while
`press_scale.xml` is **0.96** at `motion_press_in`/`motion_press_out` (90/160) and the law says
**0.97**. Three different press languages inside one app. The nav sits on a 160dp gradient scrim.

**Two grades up:** Russian labels in the default set, one press token, kill the scrim, put the bar on
solid `colorSurface` with a 1dp top hairline.

---

## A9. The rest, briefly

| Screen | Grade | The one thing |
|---|---|---|
| `activity_buy_tariff.xml` | B- | Correct tokens and a real skeleton state, but `app:cornerRadius="22dp"` on the retry button and a 76dp skeleton card height are off-scale; the state block (spinner / glyph / text / retry / pending) stacks five mutually-exclusive nodes at the top of the scroll with no shared container |
| `activity_devices.xml` + `item_device.xml` | B- | Clean, but every device is its own 20dp card in a stack - the §2.4.3 uniform-card tell; a divided list belongs here. Empty-state hero is a 64dp blue tile with a 32dp glyph: neither size is a token |
| `activity_payment_history.xml` + `item_payment.xml` | C+ | Empty state and error state share one `TextView` with a `drawableTop` - so the "empty" affordance is a top-drawable, not a designed state; the CTA is `visibility="gone"` and unwired ("wire-ready" per the comment). §15 says a screen ships its states |
| `sheet_server_actions.xml` | B- | Genuinely good sheet (36x4 handle, 24dp top radius, 56dp rows) - but every row's tile is `bg_icon_blue`, so the sheet is another blue wall, and the title's `tools:text` shows the flag emoji again |
| `dialog_top_up.xml` | C | A single `TextInputLayout` in a dialog for the one money-entry moment in the app. Desktop does this as an inline flyout with amount + method chips + validation (`AccountView.axaml:359-425`). §7.6: "Modal as first thought... exhaust inline alternatives first" |
| `activity_settings.xml` | D | A 16-line host that sets `android:background="@drawable/bg_settings_glass"` - a drawable named "glass" that is a flat `colorSurface` rectangle. Dead naming from an abandoned direction |
| `toast_status.xml` | C | 24/22/12dp paddings, `textSize="14sp"`, `textStyle="bold"` inline. And it is a `Toast`, which §1.4.8 restricts |

---

# PART B - DESKTOP, SCREEN BY SCREEN

## B1. Sign-in - `Views/LoginView.axaml` (954 lines)

**Grade: B.** This is the screen the Android one should have been. It is the best thing either app
ships.

**First.** A 64dp shield tile in `Brush.Tile.Blue` with a 30dp accent glyph, the `departament`
wordmark under it, a `Headline` title and a `Body` subtitle - centred, in a column capped at 440.
That is a brand moment, and it is the same shield the onboarding screen uses, so the
onboarding→login transition has continuity.

**Second.** A neutral segmented control «Вход | Регистрация» whose active thumb is `Brush.Bg` and
`SemiBold`, deliberately *not* blue - the comment even says so. Correct accent discipline.

**Then** email, password with an eye toggle, one filled `Войти`, passwordless links, an «или»
divider, one tonal Telegram button, and text links for browser handoff / code entry / Google-coming-
soon. Exactly one filled accent surface. Three fully designed alternate states (awaiting-Telegram
with a breathing plane and a rotating arc; email-pending; success badge), each with its own
reduced-motion guard.

**What is competing that should not be.** The background. `LoginView.axaml:237` puts the whole page
on `Brush.HomeGradient` - the same `#1B2D50 → #0E141F → #0A0B0D` navy radial as Android. It is
justified in the comment as continuity with onboarding, but it is still a decorative gradient on a
sign-in form, and §6.5 has no continuity exemption.

**Off-system.** `Margin="0,14,0,0"` (`:571`), `Margin="0,40,0,0"` (`:749, :867`),
`Margin="16,8,16,28"` (`:266`), `Margin="3,0"` on the code cells (`:145`), `CornerRadius="8"` on
`SegItem` and `SoonPill`. `FontSize="20"` inline on the code digits. The spinner is a hand-rolled
`Ellipse` with `StrokeDashArray="6.9,20.8"` re-declared in four places in this one file.

**Cramped.** Nothing. If anything the alternates block is long: divider + Telegram + site link +
code link + expandable code field + Google link is five stacked choices, and the comment itself
worries about a "стопка одинаковых кнопок".

**The single worst thing:** it declares its own `Geo.Login.Back` arrow (`:32`) when nine other views
declare the same path as `Geo.Sub.Back`. Ten copies of one 60-character glyph is not a token system.

**Two grades up:** flat `Brush.Bg` instead of the gradient, and hoist the spinner + the back arrow +
the segmented control into `GlobalStyles.axaml`.

---

## B2. Onboarding - `Views/OnboardingView.axaml`

**Grade: B.** Same column, same shield, same rhythm as Login, one filled CTA (QR) and one tonal
(clipboard), a divider, then Telegram tonal and site as a text link. Clean.

Two defects: the same `Brush.HomeGradient` page, and a locally-declared `Button.Tonal.Tall` override
(`:19-22`) that exists because the *global* `.Tall` was only ever defined for `Primary` - so two
screens each patch the same missing global. That is the shape of a design system with a hole in it.

---

## B3. Home, wide layout - `Views/HomeView.axaml` + `ConnectHeroView.axaml` + `ServerListView.axaml`

**Grade: B-.**

**First.** A two-pane split: a fixed 440px left column (account chip + subscription sections + server
rows), a 1px divider, and a flexible right pane with the connect shield. That is a real desktop
layout - it restructures rather than stretches, which is what §12.3 asks for.

**Second.** The shield: a 176 disc with a 190 connecting arc, a 200 sonar echo, an outline shield
that crossfades into a filled shield. The motion is the most carefully built thing in either
codebase - the comments alone document three separate centring bugs that were found and fixed.

**What is competing.** The page background is `Brush.HomeGradient` again (`HomeView.axaml:16`), and
the right pane is 100% background + one circle. On a 1600px window the shield floats in an enormous
navy void with the status text under it and nothing else. The left column is dense; the right is
empty. The two panes do not balance.

**Unreadable/cramped.** The left column is `MinWidth="380"` inside a fixed `440` column, and the
window's own `MinWidth` is **340** (`MainWindow.axaml:15`). At the minimum window the fixed 440
column cannot fit, so the layout mode swaps to compact - but §12.3's stated floor is a usable
900x600, and the *default* window is **372x630** (`:13-14`). We ship a desktop app that opens at
phone width.

**Inconsistent.** `HomeView` and `CompactHomeView` both inline the same 25-line TUN-unavailable
banner, character for character, with `Padding="14,12"` (off-scale) and `Spacing="10"` (off-scale) in
both copies.

**The single worst thing:** `ServerListView.axaml:44` ships a `DelayDisplayConverter` whose own
comment says a failed probe renders as **«—»** - an em-dash, banned outright by §1.4.11 and §9.2, in
a value the user reads on every row of the main screen.

---

## B4. Home, compact layout - `Views/CompactHomeView.axaml` + `BottomNavBar.axaml`

**Grade: C+.**

The page is a single scroll: account chip → TUN banner → `ConnectHeroView` at `MinHeight="440"` →
`ServerListView`. On the default 372x630 window, a 440px-tall hero means **the shield alone is 70% of
the viewport** and the subscription card is always below the fold. The first frame of the compact
desktop app is a circle and nothing else.

The bottom bar has **three** destinations - Главная, Настройки, Аккаунт. Android has **four** -
Главная, Серверы, Настройки, Аккаунт. `Common/L.Shell.cs` has no `Nav_Servers` key at all. §13:
"Identical across platforms: the destination set and its order." We fail on both count and order
(Android is Home/Servers/Settings/Account; desktop is Home/Settings/Account).

The bar's scrim is a hardcoded `#00000000 → #33000000` gradient declared inline in the view
(`BottomNavBar.axaml:25-28`), which is a raw hex outside the token dictionary *and* a decorative
gradient. Its press scale is `0.92`; the law says `0.97`.

**Two grades up:** add the Servers destination, drop the hero to ~320 in compact, kill the scrim.

---

## B5. Account - `Views/AccountView.axaml` (1474 lines)

**Grade: B.** The most complete screen in the product, and the one most at risk of collapsing under
its own weight.

**First.** One hero card with three zones separated by two hairlines: identity (avatar, `Headline`
name, tariff caption), money (`Display` balance + `Пополнить`), referral (quiet chip + copy button).
Exactly one `Display` on the tab, exactly one filled accent. That is textbook.

**Second.** The subscription carousel: name + health chip, expiry line that leads with state, a
traffic pill, a device meter, one full-width «Продлить», an auto-renew toggle, and a per-card flyout
that contains **four** stacked panels (menu / add-devices stepper / upgrade list / upgrade confirm).

**What is competing that should not be.** That flyout. Four panels with their own back buttons,
their own `Title` headers, their own steppers and their own CTAs, inside a popup, inside a card,
inside a horizontal carousel, inside a vertical scroll. It is a whole app in a tooltip. §7.6 orders
inline > expandable > flyout > dialog, and this is the flyout doing the job of three sub-pages.

**Inconsistent.** Press scale on the management rows is `0.99`; on `IconButton40` it is `0.92`; on
the connect disc `0.94`; the law says `0.97`. Four values, one screen family.

**The single worst thing:** the file is 1474 lines of AXAML with the states, the carousel, the
flyout panels and the styles all in one document. Nobody will safely change this. It is already past
the point where "rework every button in the Account tab" means "rewrite the file".

---

## B6. Settings - `Views/SettingsView.axaml` (1075 lines)

**Grade: B-.**

**First.** Sentence-case section headers and cards of hairline-divided rows - the same anatomy as
Android, which is the parity we want. And crucially the tiles are **right here**: 21 rows use
`Classes="Tile"` (neutral) and exactly **one** uses `Classes="Tile Blue"`. Desktop understood the
accent budget; Android did not. Same screen, opposite outcome.

**Second.** Inline expansion: `RowLocalProxy` expands a `LocalProxyPanel` in place with port /
username / password fields instead of pushing a sub-page. Correct per §7.6.

**What is competing.** Section count and order do not match Android. Desktop:
Подключение / Обход / Производительность / Интерфейс / Подписка (5). Android:
Подключение / Обход / Интерфейс / Подписка / Устройства / О программе (6). §13 fixes "the group
order inside settings" as identical. It is not.

**Off-system.** `Margin="16,0,16,8"` on every card - an 8dp bottom margin plus the section header's
own top padding, so the gap between groups is a sum of two arbitrary numbers rather than the
`space_24` the law specifies.

**The single worst thing:** the `TextBox.Incy` control theme is re-declared inside this view
(`:84-115`) as well as in `GlobalResources.axaml`. Two definitions of the primary input control.

---

## B7. Sub-pages - `DnsSubView`, `PingSettingsPage`, `AboutPage`, `BackupPage`, `GeoFilesPage`, `UrlSchemesPage`, `PerAppProxyPage`, `RoutingSubView`, `ProviderSettingsPage`

**Grade: C+.** They look right and they are built wrong.

All nine implement the seamless sub-toolbar and all nine look consistent at a glance. But each one
**re-declares from scratch**: the back-arrow `StreamGeometry` (`Geo.Sub.Back` - 10 files carry a
byte-identical copy), a local `Button.IconButton:pressed → scale(0.92)` style (contradicting the
global 0.97), and its own toolbar `Grid` with `MinHeight="56" Margin="16,8,16,0"`. There is no
`SubPage` shell component. Nine copies of one screen chrome means the tenth will drift, and the
eleventh will not match.

`DnsSubView` additionally invents `Border.DnsChip` - a fully accent-**filled** selected chip at
`Padding="16,10"` (off-scale) with `FontWeight="SemiBold"` (a weight the ramp does not have; §5.4
allows 400/500/700 only, "No 600"). `PingSettingsPage` invents `Border.MethodRow`. `AboutPage`
invents its own `DockPanel MaxWidth="620"` while the law caps content at 720.

**The single worst thing:** press feedback across desktop measures **scale(0.92) x15, 0.97 x8,
0.99 x4, 0.96 x4, 0.9 x3, 0.94 x2, 0.98 x1**. Seven different press languages. A user cannot learn
this interface's touch physics because it does not have one.

---

## B8. The upstream half - `OptionSettingWindow`, `AddServerWindow`, `ProfilesView`, `AddGroupServerWindow`, `SubEditWindow`, `RoutingRuleSettingWindow`, `RoutingRuleDetailsWindow`, `SubSettingWindow`, `FullConfigTemplateWindow`, `BackupAndRestoreView`, `AddServer2Window`, `ClashProxiesView`, `ProfilesSelectWindow`, `GlobalHotkeySettingWindow`, `ClashConnectionsView`, `MsgView`, `StatusBarView`, `SudoPasswordInputView`, `JsonEditor`, `ThemeSettingView`, `CheckUpdateView`, `MessageBoxDialog`

**Grade: F.** 22 of 49 views. Measured by string source: **22 views use `resx:ResUI` (upstream
v2rayN), 26 use `loc:T` (departament).** The app is 45% someone else's product.

`OptionSettingWindow.axaml` (1206 lines) opens a **1000x600** window - larger than our main window -
containing a `TabControl` with a 24-row `Grid` of `TextBlock` label / `TextBox Width="200"` /
`TextBlock` tip triplets at `Margin4`, plus `btnSave`/`btnCancel` at `Width="100"` centred at the
bottom. Default Fluent chrome, English resource strings, no tokens, no Space Grotesk, no dark
discipline. §12.1: "No default Fluent/Semi look may leak. Any control that has not been restyled to
the token set is a defect: it will look like a different application." It *is* a different
application.

`AddServerWindow.axaml` (1388 lines, 94 `ResUI` refs) is the same story for the server editor -
i.e. desktop and Android have the *same* hole in the *same* place.

**The single worst thing:** three of these windows carry inline hex the token audit flags -
`ConnectHeroView.axaml:526` `Fill="#000000"`, `DevicesView.axaml:451` `Background="#80000000"`,
`MainWindow.axaml:308` `Background="#B3000000"` - all three are scrims, and all three are a different
opacity.

---

## B9. Shell / window chrome - `Views/MainWindow.axaml`

**Grade: B-.** Custom `WindowDecorations="None"` chrome with themed min/max/close, a collapsible
nav rail, resize grips, and a deep-red close hover that is genuinely well-judged. But it opens at
**372x630** with a **340x560** minimum, which means the wide two-pane Home that the team built is
not the layout anyone sees on first run. We designed a desktop app and shipped a phone emulator.

---

# PART C - THE FIVE SYSTEMIC FAILURES

These are not screen problems. They are why *every* screen scores where it does.

### C1. The background is a gradient, on both platforms, on the screens that matter most
`drawable-night/bg_home_gradient.xml` `#1B2D50`, `drawable/bg_home_gradient.xml` `#FFFFFF→#DFE6F1`,
`GlobalResources.axaml:88,124` `Brush.HomeGradient`, plus `bg_connect_glow`, `bg_bottom_nav_scrim`,
`Nav.Scrim`. Used by Android Home, desktop Home, desktop Login, desktop Onboarding, desktop
AccountSync. Bans hit: §1.1 glassmorphism-adjacent, §1.4.3 decorative gradients/glows, §6.5 no
gradients, §2.4.1 gamer-VPN reflex. **This single decision is what makes the product read as
category-default.**

### C2. There is no component library, so every screen re-invents the same four things
- Android: two settings-row components exist and are unused; 23 rows hand-inlined in Settings, 7 in
  Local proxy, 9 in Provider settings, 4 in Backup, 6 in the action sheet.
- Desktop: 10 copies of the back arrow, 9 hand-rolled sub-page toolbars, 4 copies of the spinner
  ellipse, 2 definitions of `TextBox.Incy`, 2 copies of the TUN banner, 7 press-scale values.

### C3. The accent budget is inverted on Android and correct on desktop
Android `values/themes.xml` collapses `iconTintGreen/Orange/Purple/Yellow` and their tile fills into
blue, so 22/23 Settings rows, every action-sheet row, every payment row, every device row, and the
Account "Купить" row render the identical blue tile. Desktop `SettingsView.axaml` uses 21 neutral
tiles and 1 blue. Two platforms, one design system, opposite readings.

### C4. Press physics are not a system
Android: `press_scale` 0.96 @ 90/160, `nav_press` 0.92 @ 100/120, and **only 8 of 71 layouts use
either**. 22 layouts have clickable rows with zero press response, including the entire Settings tab.
Desktop: seven scale values. The law says one: 0.97 @ 90/160.

### C5. The product has no single language
Android `values/strings.xml`: 384 of 463 translatable strings are English. `values-ru/` covers a
different 459 and misses 321 of the base. Result on a non-Russian device: `Home · Servers ·
Настройки · Аккаунт` in the permanent nav, English server editors, Russian account screens. Plus
13 em/en-dashes in Android strings and 44 in `Common/L.*.cs`, «Приветствуем!» with an exclamation
mark, «У вас пока не добавлены подписки.» with a trailing period, "App memory" untranslated inside
the Home tab, «Сервера» instead of «Серверы», and an em-dash rendered as a *value* on every server
row on desktop (`DelayDisplayConverter`).

---

# PART D - GRADE SHEET

## Android

| Screen | Files | Grade | Two grades up in one move |
|---|---|---|---|
| Sign-in | `activity_login.xml`, `LoginActivity.kt` | **D-** | Delete both cards; one centred column, brand shield + wordmark + one headline + form + ONE filled «Войти», Telegram demoted to tonal under an «или» |
| Home (first tab) | `activity_main.xml`, `MainActivity.kt` | **C-** | Delete all three gradients; flat `#0A0B0D`; counters move under the shield and appear only when connected |
| Servers tab | `layout_servers_header.xml`, `item_recycler_main.xml` | **C** | One `ServerRow` with the 28dp flag tile and no emoji in text; header keeps search + one overflow |
| Settings tab | `layout_settings_content.xml` | **C+** | Convert all 23 rows to the existing `layout_setting_row` includes with neutral tiles |
| Account tab | `activity_account.xml`, `item_subscription_card.xml` | **B-** | Port the desktop subscription card: health chip + traffic meter + device meter + one «Продлить» |
| Buy | `activity_buy_tariff.xml` | **B-** | Wrap the five state nodes in one state container; tokenise the two stray radii |
| Devices | `activity_devices.xml`, `item_device.xml` | **B-** | Cards → one divided list |
| Payment history | `activity_payment_history.xml` | **C+** | Give empty and error separate designed states with the §9.5 formula and a live CTA |
| Local proxy | `activity_local_proxy.xml` | **D+** | Replace 7 hand-rolled rows with the row include (kills 113 off-scale + 37 inline sizes) |
| Provider settings | `activity_provider_settings.xml` | **D+** | Same |
| URL schemes | `activity_url_scheme_list.xml` | **D+** | Same, and delete the 15sp step |
| Backup | `activity_backup.xml` | **D+** | Same |
| Bottom nav | `activity_main.xml:525-701` | **C** | Russian labels in the default set, one press token, no scrim |
| Server editors x9, sub edit, routing edit, About | `activity_server_*.xml` etc. | **F** | One `ServerEditorRow` applied to the three shared `<include>` layouts fixes nine screens at once |
| Action sheet | `sheet_server_actions.xml` | **B-** | Neutral tiles |
| Top-up dialog | `dialog_top_up.xml` | **C** | Inline sheet with amount + method chips, matching desktop |

## Desktop

| Screen | Files | Grade | Two grades up in one move |
|---|---|---|---|
| Sign-in | `LoginView.axaml` | **B** | Flat `Brush.Bg`; hoist spinner + back arrow + segment into `GlobalStyles` |
| Onboarding | `OnboardingView.axaml` | **B** | Same background fix; move `Tonal.Tall` global |
| Home (wide) | `HomeView.axaml`, `ConnectHeroView.axaml` | **B-** | Flat background; give the right pane something below the shield (server identity + live counters) so the panes balance |
| Home (compact) | `CompactHomeView.axaml`, `BottomNavBar.axaml` | **C+** | Add the Servers destination; hero `MinHeight` 440 → 320; kill the scrim |
| Server list | `ServerListView.axaml` | **B-** | Replace the em-dash failure glyph with «нет ответа» |
| Account | `AccountView.axaml` | **B** | Split the 4-panel flyout into two real sub-pages; split the 1474-line file |
| Settings | `SettingsView.axaml` | **B-** | Align section set + order to Android; delete the duplicate `TextBox.Incy` |
| Sub-pages x9 | `DnsSubView` … `ProviderSettingsPage` | **C+** | Build one `SubPage` shell (toolbar + back + title + content slot) and delete nine copies |
| Buy | `BuyView.axaml` | **B-** | Match the Android tariff card anatomy exactly |
| Devices / Payments | `DevicesView.axaml`, `PaymentHistoryView.axaml` | **B-** | Divided lists, not card stacks; one scrim token |
| Shell / chrome | `MainWindow.axaml` | **B-** | Default window 1000x680 so the designed wide layout is the one people see |
| Upstream windows x22 | `OptionSettingWindow` … `MessageBoxDialog` | **F** | Restyle `AddServerWindow` + `OptionSettingWindow` first - they are 60% of the traffic into this half |

---

# PART E - IF I COULD ONLY DO FIVE THINGS

1. **Delete every gradient.** `bg_home_gradient` (both themes + mono), `bg_connect_glow`,
   `bg_bottom_nav_scrim`, `Brush.HomeGradient`, `Nav.Scrim`. Five files, one afternoon, and the
   product stops looking like every other VPN on the store.
2. **Rebuild the Android sign-in from scratch** as a port of `LoginView.axaml`. It is the first
   screen a paying user sees and it is our worst.
3. **Neutralise the Android icon tiles** - one line each in `values/themes.xml`, then one blue tile
   per screen by hand. Instantly halves the visual noise of Settings, the action sheet, Devices,
   Payments and Account.
4. **Build the two missing components** - Android `SettingRow` (already written, just unused) and
   desktop `SubPage` shell - and delete the ~60 hand-rolled copies. This is what stops the drift
   that produced A6 and B7.
5. **One language.** Translate the 384 English strings in `values/strings.xml`, starting with
   `bottom_nav_home` and `bottom_nav_servers`, which are visible on literally every frame of the app.

Everything else in this document is downstream of those five.
