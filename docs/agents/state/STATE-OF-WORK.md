# State of the work — Departament VPN, Android + PC

**Written:** 2026-07-26, ~19:15 UTC · **Branch (both repos):** `claude/app-audit-agents-hyyftk`
**Android** `/home/user/dp` HEAD `ce19c31` · **PC** `/home/user/v2rayN` HEAD `ccbec27`

**Method.** I distrusted every document, including the five `docs/agents/state/*.md` verification
reports, and re-read the code. A claim appears here as true only where I found the implementing
line. For every setting I looked for the write **and** the read. For every "unreachable feature"
previously reported I looked for an entry point a user can tap. I ran both build gates. I changed
no code and no git state; this file is the only thing I wrote.

**Two caveats you need before reading anything else.**

1. **Both working trees were being edited by other agents while I audited them.** Between my first
   and last pass the Android tree grew `RoutingSettingActivity.kt`, `RoutingSettingRecyclerAdapter.kt`,
   `activity_routing_setting.xml`, `activity_routing_edit.xml` and two new string files; the desktop
   tree grew twelve modified files including a keyboard-operability rewrite of `ConnectHeroView`.
   Line numbers below will drift. Symbols will not.
2. **Neither client compiled at the moments I checked**, and the container's build history shows the
   Android tree flipping between green and red 25 times in 70 minutes — see §4.1. These are
   unfinished in-flight edits, not committed breakage. But the consequence is real: **there is no
   verified, committed checkpoint of the current work**, and every number below was measured on a
   tree that was changing under me.

---

## The honest one-paragraph answer

The waves produced roughly 35,000 lines of specification, a genuinely good Android token layer, a
genuinely good Android component layer, a genuinely good desktop token layer, a genuinely good
desktop class-style vocabulary — and, until the last hour, **not one screen built on any of it**.
Of the five Android screens the specs govern (home, sign-in, account, settings, servers), **zero
have been rebuilt**; four are byte-for-byte the 2026-07-10 build. On desktop, **not one of the
fifty views was migrated onto the new vocabulary**; all 45 new class names and all 9 new
`Motion.cs` members have zero consumers. What a user would actually notice as new since 10 July is
four things: Russian text is finally set in a face that can draw Russian, they can sign out, the
import/list menu is back, and a stalled latency check no longer deletes their servers. Everything
else the documents describe is either a specification, or finished code behind a door with no
handle. The single largest instance: `SettingsActivity` — 599 lines of Kotlin, a 288-line
preference tree, 64 new strings, careful validation on 19 keys — **has zero launch sites**, and
those 19 settings therefore have no editor at all.

---

## 1. Genuinely finished, wired, and reachable by a user

Everything in this section I traced from the pixel to the effect.

### 1.1 Android

| # | What | Evidence I read |
|---|---|---|
| A1 | **Russian text is set in a Cyrillic-bearing face.** Three real static masters vendored (`golos_text_regular/medium/bold.ttf`, 64 KB each), `res/font/golos_text.xml` binds both `android:*` and `app:*` namespaces so weight matching works on API 24-25. `TextAppearance.App.{Headline,Title,Title.Medium,Body,Subtitle,Caption}` all resolve to Golos. | `res/font/`; `res/values/styles.xml:85,102,120,137,154,171`; **180** `TextAppearance.App.*` usages across `res/layout/` (Body 47, Subtitle 44, Title 29, Title.Medium 25, Caption 19, Numeric 5, Headline 3, Chip 3, Title.Destructive 2, SectionHeader 2, Display 1) — and still climbing: this count was 158 forty minutes earlier, because an in-flight pass is still adding them |
| A2 | **Sign-out works end to end.** Row → confirm dialog (body branches on whether the tunnel is up) → `AccountSession.wipe()` first and alone → stop core → clear cache/avatar, under an 8 s watchdog on a `NonCancellable` job → failure path re-enables the row and offers «Повторить» in a Snackbar. | `activity_account.xml:587`; `AccountFragment.kt:200, 668, 700, 737`; `AccountViewModel.kt:457`; `AccountSession.kt:77` |
| A3 | **The import / server-list menu is restored and reachable.** `menu_main.xml` = 12 items in two groups (was 4). Two real entry points: Home "+" and the Servers header "+". `prepareMenu()` hides the list group when there are no servers, so no item is a dead end. | `res/menu/menu_main.xml:18-82`; `MainActivity.kt:444` (`btnHomeAdd`), `:529`, `:929`; `ServersFragment.kt:116` |
| A4 | **A stalled latency check can no longer delete a server.** The `-2L` "measuring" sentinel is gone; an in-memory `measuringGuids` set carries the state instead, so «Удалить недоступные» skips rows that are merely still being probed. | `MainViewModel.kt:127,130,360,401,428,456,878,896`; `MainRecyclerAdapter.kt:221-232` |
| A5 | **Provider settings are reachable and validate on entry.** The User-Agent override is rejected if the app could not send it, rather than stored and silently replaced at fetch time. | `MainActivity.kt:3061`; `ProviderSettingsActivity.kt:244,253-257`; `HttpUtil.kt:60-62,71` |
| A6 | **Provider toggles drive real behaviour** (previously write-only): notify-on-update, update-on-launch, ping-on-launch, ping-on-update, sort order — all five have live readers. | `SettingsManager.kt:387-425`; `SubscriptionUpdater.kt:115,117,120,262,279,281` |
| A7 | **Auto-fallback requires a confirming re-probe** before switching servers, instead of switching on one failed health check. | `MainActivity.kt:623-643`, re-probe preconditions at `:187-192` |

### 1.2 PC

| # | What | Evidence I read |
|---|---|---|
| P1 | **The token layer is complete and genuinely applied.** Accent moved inside `ResourceDictionary.ThemeDictionaries` with a real light accent `#1E5FC7` (the only P1 accessibility defect in the token system, now closed). Every brush in every view is `DynamicResource` — `grep 'StaticResource Brush\.' Views/` = 0 — which is *why* live theme switching and the mono overlay work at all. | `Assets/GlobalResources.axaml:63-241` |
| P2 | **The Russian face reaches the desktop too, and by the right mechanism.** `Font.Ui` = Golos Text is the blanket family, set on the three widest visual roots plus 25 role styles; the brand face is now scoped to Display / Chip / Numeric / Wordmark only. This closes a defect where *every* Russian string on Windows, Linux and macOS rendered through an undeclared OS fallback. | `Assets/GlobalStyles.axaml:51`, 28 `Font.Ui` setters; `Assets/Fonts/GolosText-*.ttf` |
| P3 | **Onboarding no longer greets a returning user.** Decided from a *synchronous* storage snapshot taken before the first frame, tri-state (`bool?`, `== false` not `!= true`), with a 3-way precedence `syncing > empty > content` and the same snapshot handed to the view model so both sides answer one question once. | `MainWindow.axaml.cs:216-217, 880-882, 1013`; `AppManager.cs:223-234`; `SqliteHelper.cs:83`; `HomeViewModel.cs:605-606` |
| P4 | **Windows autostart shows what Windows will actually do.** Reads real `HKCU\…\Run` state, reconciles intent against reality at startup and displays the *actual* result, and clears the `StartupApproved` disable byte. | `Common/AutostartHelper.cs:33-59, 85-104, 130-151, 168-180`; `SettingsViewModel.cs:177-179` |
| P5 | **Per-server actions are reachable on desktop** — seven of them, in the row's right-click menu: make default, test latency, edit, duplicate, share QR, share link, delete. (Contrast Android, §3.3.) | `ServerListView.axaml:164-179` |
| P6 | **TUN intent vs. capability is modelled with real readers**, plus a user-visible banner when the session is downgraded. (The *toggle* built on it has a defect — §4.3.) | `ConfigItems.cs:199-203`; readers in `CoreManager`, `ConfigHandler`, `CoreConfigContextBuilder`, `SingboxInboundService`; banner `HomeView.axaml:56` |
| P7 | **Sub-page push is idempotent by type**, so a double-click on a settings row no longer stacks two pages and fires two network requests. | `MainWindow.axaml.cs:1114-1121` |
| P8 | **The trial flag is trusted, never inferred from squad or tariff name.** `grep -ric 'squad'` = 0 across the account/buy/devices view models. | `AccountViewModel.cs:689` |

That is the complete list of what shipped. Note what is *not* on it: no screen.

---

## 2. Written down but never built

These read, in the documents, as though they were implemented. They are not. Every "MISSING" below
is a file I looked for on disk.

### 2.1 Android — the home screen (`13-start-screen.md`)

`res/layout/fragment_home.xml` — **MISSING**. So is every other file in the spec's §4 tree
(`row_account.xml`, `status_strip.xml`, …). Every file the spec orders **deleted** is still present
and still live:

```
STILL PRESENT  drawable/bg_home_gradient.xml      → applied at activity_main.xml:8
STILL PRESENT  drawable/bg_connect_glow.xml       → applied at activity_main.xml:211
STILL PRESENT  drawable/bg_bottom_nav_scrim.xml   → applied at activity_main.xml:483
STILL PRESENT  anim/shield_assemble.xml
STILL PRESENT  layout/layout_home_account.xml     layout/layout_home_empty.xml
STILL PRESENT  layout/layout_subscription_meta_bar.xml   layout/toast_status.xml
```

The banned text glyphs are still the up/down indicators (`activity_main.xml:98` `android:text="↑"`,
`:140` `"↓"`); the deleted-by-spec welcome heading is still there (`:384` `tv_home_welcome`); the
layout still carries **65 raw `dp`** and **3 raw `sp`** literals. The five-layer hero is intact.

### 2.2 Android — sign-in (`14-auth.md`)

The spec's §0.2 instruction is to delete `activity_login.xml` and `LoginActivity.kt` and build six
new surfaces. All eleven named deliverables are **MISSING**: `layout_account_gate.xml`,
`activity_auth_email.xml`, `layout_auth_otp.xml`, `sheet_auth_methods.xml`,
`sheet_link_telegram.xml`, `layout_sync_overlay.xml`, `AuthEmailActivity.kt`,
`AuthMethodsSheet.kt`, `LinkTelegramSheet.kt`, `widget/OtpCodeView.kt`, `bg_otp_cell.xml`.

`LoginActivity` still ships, is still registered (`AndroidManifest.xml:124`), and is still the only
auth surface. The tells the spec names as the reason for deleting it are all intact:
`app:cornerRadius="26dp"` capsules at `:64, :105, :202, :273, :296`; fixed `52dp` button heights at
`:57, :196, :267` (clips at 200 % font scale); `android:textStyle="bold"` synthetic bold at
`:62, :200, :271`; three competing filled primary buttons. Only the Russian copy inside the
existing string keys was edited.

### 2.3 Android — the account rework (`23-account-rework.md`)

All 13 new layouts §6.1 lists are **MISSING**: `fragment_account.xml`, `layout_account_head.xml`,
`layout_account_card.xml`, `layout_account_skeleton.xml`, `sheet_payment.xml`, `sheet_top_up.xml`,
`sheet_upgrade.xml`, `sheet_add_devices.xml`, `sheet_qr.xml`, `dialog_rename_subscription.xml`, and
the five row layouts. §6.1 marks six billing sub-screens as "rewritten"; none was — every one still
carries its 2026-07-10 commit.

### 2.4 Android — servers (`16-servers.md`)

All 12 new files §15.1 lists are **MISSING**, including `item_server.xml` and
`ui/servers/ServersFragment.kt`. (A *different*, uncommitted `ui/ServersFragment.kt` now exists —
see §3.6 — but it re-hosts the **old** row and the **old** header.) §15.2's changes did not land
either: `PingMethod` still has four values, `menu_main.xml` still exists, `FlagUtil.resolveFlag()`
still returns an emoji `String`, the 16 flag PNGs were never ported.

### 2.5 Android — settings (`12-settings.md`)

The settings **tab** — the surface a user actually opens — has:

- **no search field**: `grep -ci search layout_settings_content.xml` = **0**;
- **six** named groups (`:23, :548, :760, :966, :1216, :1342`) against the spec's four plus an
  unheaded footer pair;
- **no** «Чёрная тема» row, **no** «Меньше движения» row, **no** «Данные и резервные копии» group;
- **no «Дополнительно» row** — see §3.1.

`PREF_REDUCED_MOTION` (spec §4.5 row 4.4) has **zero references in the entire tree** — no constant,
no writer, no reader.

### 2.6 Android — the component vocabulary's missing members

- **`Select`** — one of the fifteen components `22-components.md` R15 names. No style, no layout,
  no binder. Spec only.
- **The loading state (R8)** — no `setLoading()`, no `ButtonBinder`, nothing outside three doc
  comments. The string spec 20.1 requires, `<string name="state_loading">загрузка</string>`, does
  not exist in any strings file.
- **`values-sw600dp/`** — absent. Tablets get the phone gutter.
- **The 720 dp content cap** — `@dimen/content_max_width` is declared and has **zero readers**.

### 2.7 Android — the five desktop→Android ports (`gap-desktop-to-android.md` W2–W6)

I grepped for each. Four of five are **not started**:

| Port | Verified |
|---|---|
| W2 latency-probe **timeout** | `grep PREF_PING_TIMEOUT java/ res/` = 0. `SpeedtestManager.kt:305` still `timeoutMs: Int = 3000`, its one caller passes no override |
| W4 default uTLS fingerprint | no key, no UI, no read |
| W5 routing-rule import from URL | `grep import_rulesets_from_url` = 0 |
| W6 WebDAV connection test | `grep checkConnection` = 0; `BackupActivity` still saves four fields and shows a blanket success toast |

(W2's probe *address* and W3's FakeIP row were built — behind the unreachable door, §3.1.)

### 2.8 PC — the «Серверы» destination

`33-master-plan-pc.md` wants a fourth destination. The code has **three**: `BottomNavBar.axaml.cs:9`
`enum AppTab { Home, Settings, Account }`, three rail buttons, three bottom-nav items. Two server
views sit on disk with **zero construction sites** (`ServersView`, `CompactServersView`). Editing
either ships zero pixels. The owner has since decided against the destination — recorded here so
the two dead files are not mistaken for progress, and so nobody "fixes" the deliberately-unused
`Geo.Nav.Servers` icon.

---

## 3. Half-built: it compiles, and the user still sees the old screen or a dead control

This is the section that matters most, because a green build hides all of it.

### 3.1 Android — «Дополнительно» is finished and has no door (the single worst item)

`ui/SettingsActivity.kt` (599 lines), `res/xml/pref_settings.xml` (288 lines),
`res/layout/activity_settings.xml`, and 64 new strings in `strings_settings_advanced.xml`. The work
is good: 19 real preferences across five categories, input filters, range validation, normalisation
and rejection messages, modelled dependencies (route-only hidden unless sniffing is on; FakeIP
disabled *with a stated reason* unless local DNS is on), section deep-links, and a correct write
path (`MmkvPreferenceDataStore` → the same MMKV every reader uses, with defaults that match the
readers).

**It has zero launch sites.** I grepped the whole tree: the only non-self matches are one prose
comment and two `ProviderSettingsActivity` substring hits. Its own KDoc claims
«Вкладка настроек зовёт его так: `startActivity(SettingsActivity.newIntent(this))`» — that call does
not exist. There is no `row_advanced` in `layout_settings_content.xml`, no menu item, no shortcut,
and `android:exported="false"` (`AndroidManifest.xml:89`) means no external intent can reach it.
The string that would label the row, `adv_entry_sub` («Ядро, туннель, DNS, фрагментация»), **was
written and has zero readers**.

Consequence, in plain terms: **19 settings the core actively reads have no editor at all** —
DNS hosts, outbound domain-resolve method, real-ping concurrency, auto-sort after test,
auto-remove-invalid after test, auto-fallback, log level, sniffing, route-only, allow-insecure,
MTU, interface address, local/fake DNS, domestic DNS, delay-test URL, and the three fragment
parameters.

One line of Kotlin in `setupSettings()` converts all of it from dead code into a shipping screen.

### 3.2 Android — the component layer just got its first consumers, and they are uncommitted

Every one of the five state reports says the `ui/component` package has **zero call sites**. That
was true at their HEADs. **It is no longer true in the working tree**, and no document records it:

```
PerAppProxyActivity.kt   SubPage.installTransitions, ToolbarBinder.bind/attachTo,
                         RowBinder.bind, SkeletonBinder, EmptyStateBinder  (14 calls)
PerAppProxyAdapter.kt    RowBinder.bind + RowBinder.Trailing.Toggle
AppPickerActivity.kt     SubPage, ToolbarBinder, SkeletonBinder, EmptyStateBinder  (12 calls)
EditorActionsSheet.kt    RowBinder.bind ×2 with RowTone.DEFAULT / DESTRUCTIVE   (new file)
RoutingSettingActivity.kt  SubPage, ToolbarBinder, RowBinder                    (rewritten)
```

Per-app proxy **is** reachable (`MainActivity.kt:3039`, settings tab `rowPerApp`), so this is the
first screen genuinely migrated onto the component vocabulary. App picker is reachable indirectly
from `RoutingEditActivity`. This is real progress — and it is **uncommitted, mid-edit, and part of
why neither client builds right now** (§4.1). Treat it as promising, not as banked.

What is still unconsumed: the five row styles the specs designate as the product's row vocabulary
(`Widget.Departament.Row.Navigation/.Value/.Toggle/.Destructive/.Action`) have **zero layout
references**, as do `Segment`, `SegmentGroup`, `Card.Pressable`, `Card.Selectable`, `Tile.Accent`,
`Tile.Destructive`, `Toolbar.Brand`, `Skeleton.Block`, `TextField.ReadOnly` — 25 of 58
`Widget.Departament.*` styles in total. The 43 `Widget.App.*` alias styles have zero layout usages.

### 3.3 Android — a user cannot delete, edit, share or QR a single server

Confirmed in the working tree, including the new fragment:

- `MainRecyclerAdapter.kt:64` declares `var onItemLongClick`.
- `grep setOnLongClickListener MainRecyclerAdapter.kt` → **nothing**. The callback is never invoked.
- `bindServer()` ends at `:248` with the comment *"Long-press server-actions menu removed:
  long-press is a no-op (no listener set)."*
- `MainActivity.kt:894` **and** the new `ServersFragment.kt:99` both assign that callback.

So `showServerActions()`, `ServerActionsSheet.kt` and the 271-line `sheet_server_actions.xml` are
dead code, and a server imported by QR, clipboard or by hand can never be removed from the list.
There is no long-press, no swipe, no trailing control. **The shell-split work does not fix this — it
reproduces the same wiring against the same never-invoked callback.**

The comment is the dangerous part: the code now *documents the defect as a decision*, so the next
reader will believe it. The desktop, by contrast, has all seven actions (§1.2 P5).

### 3.4 Android — the account tab

Sign-out landed (§1.1 A2). The rework did not. `23-account-rework.md` §1.2's central argument is an
inversion — time becomes the one Display figure, balance demotes to a row value, identity becomes a
two-line head with no avatar frame. The shipped layout does the opposite, unchanged: balance is
still `TextAppearance.App.Display` (`activity_account.xml:142-151`); the 52 dp avatar container with
its **camera edit badge** — the exact element §1.2 removes — is still at `:53-98`; `radius_pill`
CTAs at `:127, :278, :355`.

And §5.1 requires the tab to **stay when signed out and become the sign-in gate**. It still
vanishes: `MainActivity.kt:1368` `binding.navAccount.isVisible = loggedIn`, with `:1371` bouncing
the user to Home if they were standing on it.

### 3.5 Android — dead controls and orphaned screens

| Thing | State today | Evidence |
|---|---|---|
| «Привязать Telegram» CTA in the home header | **Dead by construction.** `updateAccountGate()` sets `header.root.isVisible = loggedIn` (`:1363`), and `updateLoginCtaVisibility()` shows `header.ctaLinkTelegram` only when `!isLoggedIn()` (`:1396-1403`). The CTA's own parent is hidden in exactly the state the CTA is meant to appear. Mutually exclusive. | `MainActivity.kt:1355-1403` |
| Home memory card | **Permanently invisible.** `PREF_SHOW_MEMORY` read at `MainActivity.kt:2192`, **0 writers**. `MemoryStatsManager`, `card_memory`, `tv_memory`, `dot_memory` and the `memory_*` strings are all dead weight. | grep |
| `CheckUpdateActivity` | **0 non-self references.** Nothing in the app offers an update check. | grep |
| `LogcatActivity` | **0 non-self references** | grep |
| `SubSettingActivity` | **0 non-self references** | grep |
| `SettingsActivity` | **0 non-self references** — see §3.1 | grep |
| Sub-page toolbar titles | `activity_base.xml:19` hardcodes `app:titleTextAppearance="@style/ToolbarBrandTitle"` = Space Grotesk. That layout is the shared toolbar for **27 sub-page activities**, and the per-instance attribute beats the theme's `toolbarStyle`. So 26 reachable sub-pages still hand their Russian titles to a face with no Cyrillic glyphs. The one activity that works around it (`SettingsActivity.applySeamlessToolbar()`) is the one nobody can open. | `activity_base.xml:19`; `styles.xml:281` |

### 3.6 Android — the shell split is in flight, not landed

`ServersFragment.kt` (259 lines), `fragment_servers.xml` (44 lines), `EditorActionsSheet.kt`,
`sheet_editor_actions.xml`, `item_editor_section.xml` and two `strings_editors.xml` files are
**untracked**; `MainActivity.kt`, `BaseFragment.kt` and `activity_main.xml` are **modified**. The
Account and Servers tabs are being moved into a `FragmentContainerView #tab_host`
(`activity_main.xml:468`), with Главная and Настройки still as sibling view groups
(`group_home:42`, `group_settings:456`). This is structural work with no user-visible design
change: `ServersFragment` re-hosts the same `MainRecyclerAdapter`, the same
`layout_servers_header` (four 36 dp icon buttons, below the 48 dp touch floor) and the same
`item_recycler_main.xml` row (emoji flag glyph at a hardcoded `18sp`, `12sp` stacked on top of
`TextAppearance.App.Numeric`).

### 3.7 PC — the class-style vocabulary has no consumers at all

`GlobalStyles.axaml` grew from 1,448 to 2,645 lines. That growth added **45 class names**. I
spot-checked thirteen of them by name across every `.axaml` and every `.cs`:

```
Secondary 0 · Tertiary 0 · Field 0 · FieldLabel 0 · EmptyState 0 · Skeleton 0 · Meter 0
NavItem 0 · SegmentTrack 0 · Selectable 0 · Money 0 · Wordmark 0 · OfflineBar 0
```

Same for `Common/Motion.cs`: `Dur.Pulse`, `Dur.Spin`, `Dur.Debounce`, `Dur.RevealExit`,
`Dur.StateExit`, `Dur.Hover`, `Motion.PressScale`, `Motion.Play()`, `StaggerFor()` — **nine
members, zero call sites each**, verified individually. `Motion.Play()` was written specifically so
that "the right call is shorter than the wrong one" for reduced motion; it is called nowhere.

Meanwhile the views hand-roll the same things locally — 26 style rules in `AccountView` alone, 20
in `BuyView`, 18 in `LoginView` — and one of those local copies *shadows* a promoted global:
`SettingsView.axaml:68` defines its own `ControlTheme x:Key="TextBox.IncyField"`, and `StaticResource`
resolves nearest-first, so the 75-line promoted global at `GlobalResources.axaml:635-709` has **no
live consumer**.

### 3.8 PC — the P0 defects the audits raised are all still open

| # | Defect | Verified today |
|---|---|---|
| P0-1 | «Устройства» opens the **root** subscription's devices, not the selected card's | `DevicesView.axaml.cs:25` `new DevicesViewModel()` — **no argument**. The ctor parameter `remnawaveUuid` exists and is documented as the Android `EXTRA_REMNAWAVE_UUID` mirror; the call chain still drops it and falls back to `LoggedInProfileUuid()` |
| P0-2 | The connect control is not operable without a mouse | At HEAD, `ConnectHeroView.axaml` `#ConnectDisc` has no `Focusable`, no `IsTabStop`, no `KeyDown`, no `AutomationProperties.Name`. **An agent is fixing this right now** — the uncommitted `ConnectHeroView.axaml.cs` adds `OnDiscGotFocus`/`OnDiscLostFocus` and a focus ring — but it does not compile yet (§4.1) |
| P0-3 | «Автообновление провайдеров» configures **geo-file** updates, in the wrong unit | `SettingsViewModel.cs:461` writes `GuiItem.AutoUpdateInterval`; its only consumer, `TaskManager.cs:113`, gates `UpdateGeoFileAll()` on `hours % AutoUpdateInterval` where `hours` is **process uptime**. The "1 ч." option therefore needs 60 hours of continuous uptime. Real subscription refresh is `SubItem.AutoUpdateInterval` (`TaskManager.cs:84-85`), which this row never touches. The copy pass renamed the label; the wiring did not move |

### 3.9 PC — every user-facing message is dropped on the floor

`MainWindow.axaml.cs:1841` routes all feedback into `NoticeManager.Instance.SendMessage(content)`
→ `AppEvents.SendMsgViewRequested.Publish(...)`. The **only** subscriber in the whole solution is
`MsgViewModel`'s constructor (`ServiceLib/ViewModels/MsgViewModel.cs:33`), and `MsgViewModel` is
constructed **only** in `DesignData.cs:26`. There are **156** `SendSnackMsgRequested.Publish` /
`NoticeManager.Enqueue` call sites feeding that channel.

The comment above the handler explicitly claims this change fixed «добавляю подписку — ничего не
происходит, без объяснений» by routing feedback to the inline message panel. **That panel does not
exist in this shell.** The bug it describes is still live, and the fix made it harder to see.

### 3.10 PC — other still-open items, spot-verified rather than taken on trust

- **Settings hub structure unchanged**: 6 named groups against the spec's 4 + footer; 8 reachable
  sub-pages out of 17 spec routes; 25 `Classes="Card"` across the settings tree against the spec's
  zero.
- **«Обход локальной сети» writes the wrong thing.** `SettingsViewModel.cs:229` writes
  `inbound.AllowLANConn`, which is *"accept connections to the local proxy from the LAN"* (read by
  `V2rayInboundService`, `SingboxInboundService`, `CoreConfigClashService` to bind `0.0.0.0`). The
  row's own hint says «Прямой доступ к устройствам в локальной сети» — a *routing* bypass. Two
  different features share one switch.
- **«Язык» cannot reach `Системный`**: `:475` still `CurrentLanguage == "en" ? "ru" : "en"`.
- **No keyboard shell**: `MainWindow.axaml.cs` binds only Ctrl +/−/0, Ctrl+V, Ctrl+S, F5. No
  `Key.Escape`, no `XButton1`, no Ctrl+F, no Ctrl+`,` — a sub-page cannot be popped from the keyboard.
- **Icon-only controls are unnamed**: `AutomationProperties.Name` appears in **1** of 50 views.
- **No offline state anywhere**: `grep -i 'offline|Нет сети' Views/ L.*.cs` = 0.
- **Server rows are half-accessible**: `ServerListView.axaml:157-158` now sets
  `Focusable`/`IsTabStop`, so the row takes a tab stop and can raise its context menu via
  Menu/Shift+F10 — but `grep 'Key.Enter|Key.Space|KeyDown' ServerListView.axaml.cs` = **0**, so
  Enter/Space still cannot select a server.
- **The servers empty state is two-thirds of a state**: icon + title + line, no action button
  (`ServerListView.axaml:305-326`).
- **Press scale is not one recipe**: six distinct values across `Views/` + `Assets/`
  (`0.97` ×16, `0.92` ×13, `0.99` ×4, `0.96`, `0.94`, `0.9`), with `scale(0.92)` redeclared
  verbatim in twelve view files.
- **The Cyrillic fix has seven holes.** Despite §1.2 P2, seven sites still override the blanket with
  the Cyrillic-free brand face, and two of them carry Russian UI text: `BottomNavBar.axaml:77-78`
  styles `TextBlock.NavLabel` — the three **compact-layout** nav captions, and the app *starts
  compact* — and `MessageBoxDialog.axaml:10` sets it window-wide on the app's only confirm/alert
  dialog. (The wide layout's rail captions were fixed: `MainWindow.axaml:185` uses `Font.Ui`.)
  Also `LoginView.axaml:88`, `DnsSubView.axaml:53`, `AccountView.axaml:85, 180, 267`.

---

## 4. Regressions the waves introduced

### 4.1 There is no stable green checkpoint (both, high, in flight)

This is not "the waves broke the build". It is worse in a subtler way: **the tree oscillates between
compiling and not compiling, minute to minute, and nothing has been committed since it started
doing so.**

I ran the project's own gate, `docs/agents/verify-build.sh`, three times and caught it red every
time:

| Run | Result |
|---|---|
| Android, 18:45 | `BUILD: FAILED` — `RoutingSettingActivity.kt:54,56,95` unresolved `tvDomainStrategySummary`, `layoutDomainStrategy` (Kotlin referencing view ids the rewritten layout no longer declares) |
| Android, 18:58 | `BUILD: FAILED` — a *different* failure, in resource linking: `activity_routing_edit.xml` referenced twelve `routing_ed_*` strings that did not exist. **Those strings existed by the time I re-checked minutes later** |
| Desktop, 19:05 | `BUILD: FAILED` — `ConnectHeroView.axaml.cs(526,49)`: `GotFocusEventArgs` not found (a missing `using Avalonia.Input;` in the in-flight keyboard-operability rewrite) |

Then I read every build the container has run today, which tells the real story:

```
Android:  14 SUCCESS / 11 FAIL between 17:53 and 19:04
Desktop:   3 SUCCESS /  1 FAIL between 17:54 and 18:47
```

The two most recent green Android runs (18:49, 18:50) had **NEW WARNINGS: 0** — so the tree does
reach a passing state. But two earlier green runs (18:03, 18:21) passed the build and **failed the
warning bar** with new hits in `AvatarManager.kt` (unnecessary safe call) and `MiscDtos.kt`
(unnecessary `!!`), which have since been fixed. One run logged
`Detected multiple Kotlin daemon sessions` — several agents building the same tree at once.

What this means for the owner: **no committed state has been verified since the current editing
wave began**, and the ~76 dirty Android files / ~22 dirty PC files include structural work (the
fragment shell split, a component-layer migration, a copy pass across dozens of files, a desktop
keyboard rewrite) that no one has yet driven to a single green, committed checkpoint. Everything
measured in this report was measured on a moving tree. Before any new work starts, someone must get
`bash docs/agents/verify-build.sh both` to `BUILD: SUCCESSFUL` + `NEW WARNINGS: 0` on **both**
clients at the same moment, and commit that moment.

### 4.2 Android: six settings the app still reads lost their only editor (high)

The settings wave rewrote `res/xml/pref_settings.xml` and dropped 36 keys. The header comment
justifies each removal as either "dead key" or "already editable elsewhere". For 20 of them that is
true. For these it is not — they now have **a reader and no writer anywhere in the app**, so the
value is frozen at whatever MMKV happens to hold:

| Key | Readers I found | Effect |
|---|---|---|
| `PREF_CONFIRM_REMOVE` | `MainActivity.kt:1695`, `ServerActivity.kt:669`, `ServerProxyChainActivity.kt:141`, `SubEditActivity.kt:222`, `SubSettingActivity.kt:119` | **Read with `decodeSettingsBool(key)` — no default, i.e. `false`.** A fresh install now deletes servers and subscriptions with **no confirmation** and cannot enable it; an existing user who had it on can never turn it off |
| `PREF_SHOW_MEMORY` | `MainActivity.kt:2192` | The home memory card can never be shown; its whole subsystem survives with no switch |
| `PREF_PREFER_IPV6` | `CoreOutboundBuilder.kt:682`, `CoreConfigManager.kt:1002` | DNS resolution preference frozen |
| `PREF_GROUP_ALL_DISPLAY` | `MainViewModel.kt:558` | "All groups" can never be enabled |
| `PREF_MUX_XUDP_QUIC` / `_CONCURRENCY` | `CoreOutboundBuilder.kt:65-66` | Frozen |
| `PREF_DYNAMIC_SOCKS_PORT` | `SettingsManager.kt:491` | Frozen at `false` |
| `PREF_USE_HEV_TUNNEL`, `PREF_HEV_TUNNEL_LOGLEVEL`, `PREF_HEV_TUNNEL_RW_TIMEOUT`, `PREF_IP_API_URL` | tunnel internals | Frozen; arguably deliberate |

The tunnel internals are defensible as "the app decides". **`PREF_CONFIRM_REMOVE` is not** — it is a
destructive-action safety gate whose effective default is now *off*, on a screen where the delete
action is one tap.

### 4.3 PC: the TUN toggle can be turned on but not off in a downgraded session (high)

`ServiceLib/ViewModels/StatusBarViewModel.cs:513`, changed by `8778233`:

```diff
-        if (_config.TunModeItem.EnableTun == EnableTun)
+        if (_config.TunModeItem.EnableTunEffective == EnableTun)
             return;
```

with `EnableTunEffective => EnableTun && !TunUnavailable` (`ConfigItems.cs:203`). On non-admin
Windows or any Linux/macOS launch: the ctor sets `TunUnavailable = true` and the VM toggle to
`false` (`:155-156`); switching **on** proceeds and leaves the session downgraded; switching **off**
now compares `(true && !true) == false` → `false == false` → **early return, nothing written**. The
persisted intent is stuck at `true`, `TunRequestedButUnavailable` keeps the home banner up forever,
and the ctor re-reads the stuck `true` on every subsequent launch. Before this commit the toggle
worked.

`SettingsViewModel.SetTunMode` (`:348-349`) uses a **different**, correct guard —
`EnableTun == enable && EnableTunEffective == enable` — so the Settings row can still turn it off.
Two surfaces for one setting now disagree about whether turning it off is possible. One line.

### 4.4 PC: the mono/black theme leaks brand blue on every primary button (medium)

`BuildMonoOverlay` (`App.axaml.cs:578-661`) overrides 36 brush keys. The token wave added seven
theme-dependent keys to Dark and Light and mirrored **none** of them into the mono overlay:
`Brush.AccentHover`, `Brush.AccentPressed`, `Brush.OutlineControl`, `Brush.OnSurfaceVariantHover`,
`Brush.Amber`, `Brush.AmberText`, `Brush.Ping.Good`. I confirmed by grep that not one of those
names appears in `App.axaml.cs`.

Two are load-bearing: `GlobalStyles.axaml:653/656` bind `Button.Primary:pointerover` / `:pressed` to
`AccentHover` / `AccentPressed`, and `Button.Primary` is used **44 times**. In the black theme the
button sits grey at rest and **flashes `#3D7EF0` brand blue on hover and `#3877E0` on press**. Mono's
entire contract is "no accent hue".

### 4.5 PC: `PortInvalid` is written and never read (medium)

`SettingsViewModel.cs:76` declares it, `:410` sets it, and a whole-solution grep returns **only those
two lines plus one doc comment**. The doc comment is candid: *"the inline caption that renders it is
a markup change and is not part of this pass."* The commit that introduced it is explicitly a fix
for a **dropped error message** — and the replacement message is dropped again, one layer further
in. An out-of-range local-proxy port is still rejected in silence. (The partial mitigation does
work: `SettingsView.axaml.cs:246-252` keeps the panel open and refocuses the field — so the user
sees the value snap back and never learns why.)

### 4.6 PC: a startup window where the connect shield is a silent no-op (low)

`HomeViewModel.cs:207-210` added `if (!HasServers) return;`. When the launch snapshot is `null`
("unknown"), `HasServers` and `IsEmpty` are both deliberately false — so in that window the shield
swallows the tap entirely: no connect, no spinner, no message, and neither the server list nor the
empty state on screen. Short-lived, but it is a new dead tap where there was none.

### 4.7 Both: copy and hygiene leaks (low)

- `ServiceLib/ViewModels/StatusBarViewModel.cs:561` hard-codes Russian inside shared ServiceLib
  (`"Весь трафик · TUN"` / `"Через системный прокси"`) and binds it into `HomeView.axaml:61`, which
  localises everything else through `{loc:T …}`. The English UI shows these two strings in Russian.
- `SettingsView.axaml:792` `Text="Масштаб интерфейса"` is a literal, not a `loc:T` key. Same shape
  at `StatusBarView.axaml:114,120`.
- `ProviderSettingsActivity.kt` carries a hardcoded Russian literal with a `TODO(copy)`.
- `MainViewModel.kt:166-173` — `onCleared` cancels the measurement scope's *children* but never the
  `SupervisorJob` itself. Harmless in practice; an unclosed scope nonetheless.

---

## 5. Where the documents overstate what happened

Said plainly, with the evidence.

1. **`docs/master-requirements-audit.md` line 108** marks item **13a (RAM panel) as DONE**. The RAM
   panel cannot be displayed: `PREF_SHOW_MEMORY` has one reader and zero writers (§4.2). Line 27
   likewise marks **1e "App-memory card (toggleable)" DONE** and cites "`pref_show_memory` toggle" —
   the toggle was deleted from `pref_settings.xml` by a later wave and never re-homed.

2. **`SettingsActivity.kt`'s own KDoc** states «Вкладка настроек зовёт его так:
   `startActivity(SettingsActivity.newIntent(this))`». That call does not exist anywhere in the
   tree. Documentation describing a caller that was never written is the most expensive kind of
   wrong, because it stops the next reader from checking.

3. **`MainRecyclerAdapter.kt:61-64` and `:248`** describe the missing per-server actions as an
   intentional removal — *"The long-press server-actions menu was removed, so this callback is no
   longer invoked"*. `git log -S "setOnLongClickListener"` on that file returns nothing: the adapter
   never had one. The comment converts an unfixed bug into a decision, while `MainActivity.kt:894`
   and the new `ServersFragment.kt:99` both still wire the callback.

4. **`23-account-rework.md` §6.1** lists six billing sub-screens as "rewritten"
   (`activity_buy_tariff`, `activity_devices`, `activity_payment_history`, `sheet_payment_method`,
   `dialog_top_up`, `item_subscription_card`). Every one of them still carries its 2026-07-10
   commit. None was touched.

5. **`MainWindow.axaml.cs:1836-1839`** claims the snack-message rework fixed «добавляю подписку —
   ничего не происходит, без объяснений». The channel it routes into has no runtime subscriber
   (§3.9). 156 call sites publish into the void.

6. **`pref_settings.xml`'s header** claims «строк с двумя домами — 0» (no setting has two editing
   homes). `pref_route_only_enabled` has two: the advanced screen and the reachable Local-proxy
   screen (`LocalProxyActivity.kt:224-230`).

7. **The five `docs/agents/state/*.md` reports themselves are honest and were right when written** —
   I re-derived their central claims and they hold. But `android-foundation.md`'s and
   `regressions.md`'s headline ("the component layer has zero call sites") is **already out of date**:
   five files now consume it (§3.2). That is a good change, and no document records it, which is its
   own kind of risk.

8. **A specification is not a screen.** `docs/design2026/` is ~35,000 lines across 20 files. It is
   valuable and it is not delivery. Nothing in this report counts a spec as done.

---

## 6. What remains, ordered so each item unblocks the next

Sizes: **S** ≈ under an hour · **M** ≈ half a day · **L** ≈ a day or two · **XL** ≈ a week+.

### Gate — nothing else is safe until this is true

| # | Do | Size | Why it blocks everything |
|---|---|---|---|
| 0 | **Finish or revert the in-flight edits, then get `verify-build.sh both` green and commit.** | **M** | Two trees, twenty-plus dirty files, neither compiling. Every measurement below is taken on sand until this lands |

### Tier 1 — one-line and one-row fixes that convert finished work into shipping features

Do these first: highest value per minute in the entire backlog, and several unblock testing of
everything after.

| # | Do | Size | Why it matters |
|---|---|---|---|
| 1 | **Add the «Дополнительно» row to the settings tab** — `startActivity(SettingsActivity.newIntent(this))`. The string `adv_entry_sub` is already written. | **S** | Converts 599 lines of finished, validated Kotlin from dead code into a screen, and gives 19 core settings their only editor (§3.1) |
| 2 | **Make `MainRecyclerAdapter.bindServer` call `onItemLongClick?.invoke(guid)`**, and delete the two comments claiming the menu was removed. | **S** | Today a manually added server can never be deleted, edited, shared or QR'd. Both call sites are already wired (§3.3) |
| 3 | **Restore an editor for `PREF_CONFIRM_REMOVE`**, or delete its five readers outright. Do not leave it half-present. | **S** | A fresh install currently deletes servers with no confirmation and cannot turn it on (§4.2) |
| 4 | **PC: fix the TUN guard** at `StatusBarViewModel.cs:513` — compare intent *and* effective, as `SettingsViewModel.SetTunMode` already does. | **S** | One line; today the toggle is one-way and the home banner is permanent (§4.3) |
| 5 | **PC: mirror the seven new theme keys into `BuildMonoOverlay`.** | **S** | The black theme flashes brand blue on all 44 primary buttons (§4.4) |
| 6 | **Decide `PREF_SHOW_MEMORY`**: give it a writer or delete the card, the strings and `MemoryStatsManager`. | **S** | Removes a whole dead subsystem, or ships it. Either beats today |
| 7 | **PC: give `PortInvalid` an inline caption**, or delete the property and say so. | **S** | An invalid port is rejected in silence (§4.5) |

### Tier 2 — the surfaces that make later design work visible

| # | Do | Size | Blocked by |
|---|---|---|---|
| 8 | **PC: give user messages a real surface.** Either host the inline message panel in this shell or route `SendSnackMsgRequested` to a real UI. 156 publishers currently vanish. | **M** | — |
| 9 | **Android: fix `activity_base.xml:19`** — drop the hardcoded `ToolbarBrandTitle` so the theme's toolbar style wins. | **S** | 26 reachable sub-pages get Russian titles in a Cyrillic face today (§3.5) |
| 10 | **PC: close the seven remaining `Font.Grotesk` overrides**, starting with `BottomNavBar` (compact nav labels — and the app starts compact) and `MessageBoxDialog` (window-wide, on the app's only confirm dialog). | **S** | — |
| 11 | **PC: finish the connect control's keyboard path** (the in-flight `ConnectHeroView` work) and add `AutomationProperties.Name`. | **S** | #0 |
| 12 | **PC: fix P0-1** — pass the selected card's `remnawaveUuid` into `new DevicesViewModel(...)`. | **S** | — |
| 13 | **PC: fix P0-3** — point «Автообновление провайдеров» at `SubItem.AutoUpdateInterval`, or relabel it honestly as geo-files and fix the unit. | **M** | — |
| 14 | **PC: split «Обход локальной сети»** from `AllowLANConn`, which is a different feature. | **M** | — |

### Tier 3 — the migration that the whole foundation was poured for

This is where the real remaining cost sits. Both platforms have a complete, well-made component
vocabulary and **no screen speaks it**. Migrate screens one at a time; the first one flushes out
everything the foundation is missing.

| # | Do | Size | Blocked by |
|---|---|---|---|
| 15 | **Android: land the per-app-proxy / app-picker migration properly** (it is the first real consumer of `RowBinder`/`ToolbarBinder`/`EmptyStateBinder`, currently uncommitted) and use it as the reference migration. | **M** | #0 |
| 16 | **Android: build the missing component members the first migration will demand** — `Select`, the button loading state + `state_loading` string, and the three referenced-but-absent drawables (`ic_unfold_more`, `ic_warning`, `ic_error`). | **M** | #15 |
| 17 | **Android: migrate the settings tab onto the row vocabulary**, add the search field and collapse six groups to four + footer. | **L** | #16 |
| 18 | **PC: migrate one screen onto the 45 new classes** — `SettingsView` is the right first target, and doing it removes the `TextBox.IncyField` shadowing defect for free. | **L** | #0 |
| 19 | **PC: unify the press recipe to 0.97** and delete the twelve verbatim `scale(0.92)` redeclarations. | **M** | #18 |

### Tier 4 — the screen rebuilds, in dependency order

Each of these is a full screen rebuild against a written spec. None can be sensibly started before
Tier 3 gives it a vocabulary to build in, and none should start before Tier 1 stops the bleeding.

| # | Do | Size | Note |
|---|---|---|---|
| 20 | **Android: rebuild sign-in** (`14-auth.md`) — 11 missing files, and it gates the account tab's signed-out state | **XL** | Do before the account rework: §5.1 of the account spec depends on the tab becoming the sign-in gate |
| 21 | **Android: rebuild the account tab** (`23-account-rework.md`) — 13 missing layouts, plus the six billing sub-screens the spec wrongly records as rewritten | **XL** | #20 |
| 22 | **Android: rebuild home** (`13-start-screen.md`) — `fragment_home.xml` plus the eight deletions | **XL** | #15, #16 |
| 23 | **Android: rebuild servers** (`16-servers.md`) — 12 missing files; fold the in-flight `ServersFragment` into it rather than shipping the old row inside a new fragment | **XL** | #2 (long-press must work first, or the rebuild inherits the dead control) |
| 24 | **Android: the remaining desktop→Android ports** — probe timeout (W2), uTLS default (W4), routing-rule import from URL (W5), WebDAV connection test (W6) | **L** | #1 (three of the four want a home on the advanced screen) |
| 25 | **PC: rebuild `LoginView` and `SettingsView`** against their specs | **XL** | #18 |

### Deliberately not on this list

- **The «Серверы» desktop destination.** The owner decided against it. `ServersView.axaml` and
  `CompactServersView.axaml` are dead files; before deleting them, harvest
  `CompactServersView.axaml:85-108` — it is the only server search field ever written for desktop,
  and Главная now owns that problem.
- **`Geo.Nav.Servers`.** Declared and unused *on purpose*. Do not "fix" it.
- **`StatusBarView`.** Invisible at 0×0 and load-bearing — tray icon, clipboard, sudo password, TUN
  elevation. Do not delete.

---

## 7. What must survive any rebuild

These are verified-correct, invisible, and easy to destroy by accident. Anyone rewriting the files
they live in should carry them across verbatim.

**Android**
- Tapping a server **selects** and never connects; with a tunnel up, an explicit «Переподключиться»
  action is offered and declining leaves the tunnel alone (`MainActivity.kt:1526, 1551`).
- `stopCoreLoop()` announces the stop only **after** `stopLoop()` returns, and
  `CoreProxyOnlyService` honours `startCoreLoop`'s result (`CoreServiceManager.kt:293-314`;
  `CoreProxyOnlyService.kt:36-42`).
- The adapter mirrors the selected guid, re-reads it on rebuild, and falls back to a full refresh
  when a row cannot be located (`MainRecyclerAdapter.kt:88-94, 150, 238, 341-348`).
- `getProxyOutbound()` resolves through routing rules and balancers, refuses freedom/blackhole
  targets, and the speedtest builder promotes the **same** outbound (`V2rayConfig.kt:520-570`;
  `CoreConfigManager.kt:214-229`).
- Subscription User-Agent precedence (per-subscription → provider → operator default) and the
  header-safety backstop at the request builder (`AngConfigManager.kt:891-893`; `HttpUtil.kt:60-71`).

**PC**
- The synchronous onboarding snapshot and its tri-state (§1.2 P3) — an unloaded default here is the
  bug that greeted returning users with the sign-up screen.
- `AutostartHelper.Reconcile` and the deliberately non-short-circuited change handler (§1.2 P4).
- `grep 'StaticResource Brush\.' Views/` **must stay 0** — it is what makes live theme switching work.
- Reduced motion is read at play time, never cached in a constructor (`Common/MotionState.cs`;
  `ServerListView.axaml.cs:400-428`).
- `SettingsView.axaml.cs:107-139` `WireRow`/`WireToggleRow` — the row takes the tab stop, the switch
  leaves the tab order, Enter/Space activates, and the focus adorner survives lite mode. This is the
  correct desktop keyboard model and it is what `ServerListView` still needs.
