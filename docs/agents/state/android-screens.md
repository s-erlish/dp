# Android screens — verified state

**Scope:** the five screens named in the work order — launch/home, sign-in, account (plus the
billing sub-screens), settings, servers — checked against `docs/design2026/13-start-screen.md`,
`14-auth.md`, `15-account-tab.md`, `23-account-rework.md`, `12-settings.md`, `16-servers.md`
and `docs/agents/audit2026/android-*.md`.

**Method:** no claim in any document was accepted. For every specified file I checked whether it
exists on disk. For every specified UI element I checked whether the layout contains it and whether
Kotlin binds it. For every setting I looked for both the write and the read. For every previously
reported unreachable feature I looked for a real entry point a user can tap.

**Repo:** `/home/user/dp`, branch `claude/app-audit-agents-hyyftk`, HEAD `c9d7bf0`.
**Companion report:** `docs/agents/state/android-foundation.md` (tokens + component layer). This
file does not re-derive its findings; it records what they mean for each screen.

**The tree is being edited while this was written.** At 18:02 UTC `ui/MainActivity.kt` grew by 142
lines under me, mid-read. `git status` at the time of the final pass showed uncommitted work in
`ui/MainActivity.kt`, `ui/BaseFragment.kt` and `res/layout/activity_main.xml` — another agent is
converting the bottom-nav tabs to fragments (`group_account` → `FragmentContainerView #tab_host`,
with a comment saying Главная, Серверы and Настройки "move here in their own stages"). **Every line
number below was re-read against the working tree after that edit landed**, and the mechanical
counts were re-run on it. That in-flight change touches the shell only; it does not alter any
finding here. Expect line numbers to drift; the symbols will not.

---

## The headline

**Not one of the five screens has been rebuilt to its 2026 specification. Four of the five have not
been touched at all since 2026-07-10, before the design waves began.** The waves produced a token
layer, a component layer, a settings screen nobody can open, and one genuinely new feature
(sign-out). The screens themselves are the July build.

| Screen | Governing spec | Verdict |
|---|---|---|
| Launch / home | `13-start-screen.md` | **Untouched.** Spec orders a rebuild into `fragment_home.xml`; that file does not exist. `activity_main.xml` last changed 2026-07-10. |
| Sign-in | `14-auth.md` | **Untouched** except a copy pass. Spec orders `activity_login.xml` + `LoginActivity.kt` **deleted**; both ship, both still registered, both still the only auth surface. |
| Account tab | `23-account-rework.md`, `15-account-tab.md` | **Partially built.** Layout is the July design, which the spec explicitly inverts. One real new feature landed: sign-out, fully wired. |
| Settings | `12-settings.md` | **Half-built, and the built half is unreachable.** The settings *tab* is the July hand-rolled list, unchanged. The new «Дополнительно» screen (`SettingsActivity`) is complete and has **zero launchers**. |
| Servers | `16-servers.md` | **Untouched**, and it carries a live functional regression: every per-server action is unreachable. |

Three numbers make the "untouched" claim non-negotiable. `audit2026/android-home.md` §4 counted the
home layout set on 2026-07-26 and published the counts. Re-run today, byte for byte:

| Check, home layout set | Audit's number | Today |
|---|---|---|
| Raw `dp` literals | 109 | **109** |
| Raw `sp` literals | 8 | **8** |
| `textStyle="bold"` | 3 | **3** |
| `↑` `↓` `✕` text glyphs | 3 | **3** |

`audit2026/android-settings.md` §2.1 did the same for the settings/peripheral set (22 files).
Re-run today: raw `dp` **738** (audit: 738), inline `textSize`/`fontFamily` **93** (audit: 93),
`15sp` **18** (audit: 18), `focus_ring` references **0** (audit: 0). Zero movement.

---

## 1. Launch / home tab — UNTOUCHED

`res/layout/activity_main.xml` (707 lines), `ui/MainActivity.kt` (3 473 lines).
Last **commit** touching the layout: `5e8cd54`, 2026-07-10; the only uncommitted change to it is the
account tab-host swap at `:497-508`, which does not touch the home tab. The only committed
2026-07-26 change to `MainActivity.kt` was −13/+5 lines deleting `markAllServersTesting()` — a
ping-state fix, not a design change.

### What the spec ordered (13-start-screen.md §2.1, §3, §4)

Rebuild `group_home` as `res/layout/fragment_home.xml`; delete the page gradient, the connect glow,
the memory card, the welcome heading, `layout_home_account.xml`, `layout_home_empty.xml`,
`layout_subscription_meta_bar.xml`, `toast_status.xml`, the nav scrim and `shield_assemble.xml`;
replace the five-layer 230dp hero with one disc, one ring, one glyph; add `#status_strip`,
`#status_line` with `#tv_status_detail`, `#numeric_strip` and the ledger rows.

### What is on disk

Nothing from that list. Every named file still exists:

```
res/drawable/bg_home_gradient.xml       bg_home_gradient_mono.xml
res/drawable/bg_connect_glow.xml        bg_connect_glow_mono.xml
res/drawable/bg_bottom_nav_scrim.xml    bg_nav_header.xml   nav_header_bg.png
res/anim/shield_assemble.xml
res/layout/layout_home_account.xml      layout_home_empty.xml
res/layout/layout_subscription_meta_bar.xml   toast_status.xml
```

`res/layout/fragment_home.xml` does not exist. Neither do `row_account.xml`, `status_strip.xml`
or any other file in the spec's §4 tree.

The tells the work order asked me to look for are all present, in the shipped file:

| Tell | Evidence |
|---|---|
| Decorative gradient as the screen ground | `activity_main.xml:8` `android:background="@drawable/bg_home_gradient"` |
| Decorative glow | `:208-212` `view_connect_glow` → `@drawable/bg_connect_glow`; the 850 ms infinite-reverse breathe still runs from `MainActivity.kt` |
| Second banned gradient | `:519` `bottom_nav_scrim` → `@drawable/bg_bottom_nav_scrim` |
| Text-glyph arrows | `:98` `android:text="↑"`, `:140` `android:text="↓"` |
| Hardcoded `sp` outside the ramp | `:113` `13sp`, `:128` `14sp`, `:155` `13sp` — each one applied **on top of** `TextAppearance.App.Numeric`, defeating the ramp on the same view |
| Hardcoded `dp` outside the scale | `:73-77` a 42dp invisible spacer, `:163-164` 42dp, `:202-203` 230dp hero, `:247` 212dp, `:255-256` 176dp, `:262` 88dp corner, plus 13× `3dp` and 4× `34dp` in the nav |
| Stacked cards | `card_memory` `:313`, the meta-bar card, the onboarding card, then N server cards |
| Debug UI gated by a preference with no writer | `card_memory` `:313`, gated on `PREF_SHOW_MEMORY`, read at `MainActivity.kt:2141`, **written nowhere in the tree** — the card can never appear |
| Deleted-by-spec welcome heading | `:384` `tv_home_welcome` |

The five-layer hero is intact exactly as the audit described it: glow `:208`, ring `:215`,
sonar pulse `:224`, 212dp `CircularProgressIndicator` `:238`, 176dp `MaterialCardView` at 88dp
radius `:254` holding two stacked 80dp shields from `:269`. The connect card's
`contentDescription` is still `@string/tasker_start_service` (`:259`).

### What did reach this screen

Only through the theme, not through the layout: Russian text set in `TextAppearance.App.*` now
resolves to Golos Text rather than the Cyrillic-free Space Grotesk (`res/values/styles.xml:85`,
`:102`, `:120`, `:137`, `:154`, `:171`). That is real and it is visible here. It is also the *only*
2026 design work that reaches this screen.

**Verdict: untouched.** Spec-only.

---

## 2. Sign-in — UNTOUCHED (copy pass only)

`res/layout/activity_login.xml` (314 lines), `ui/LoginActivity.kt` (415 lines).
Both last changed in `500bc0b`, 2026-07-10.

`14-auth.md` §0.2 is unambiguous: delete the layout, delete the activity, delete the manifest entry,
rewrite `strings_auth.xml` to a new key set, and build six new surfaces.

| Spec deliverable | On disk |
|---|---|
| `res/layout/layout_account_gate.xml` | **missing** |
| `res/layout/activity_auth_email.xml` | **missing** |
| `res/layout/layout_auth_otp.xml` | **missing** |
| `res/layout/sheet_auth_methods.xml` | **missing** |
| `res/layout/sheet_link_telegram.xml` | **missing** |
| `res/layout/layout_sync_overlay.xml` | **missing** |
| `ui/AuthEmailActivity.kt` | **missing** |
| `ui/AuthMethodsSheet.kt` | **missing** |
| `ui/LinkTelegramSheet.kt` | **missing** |
| `ui/widget/OtpCodeView.kt` | **missing** |
| `res/drawable/bg_otp_cell.xml` | **missing** |
| `res/interpolator/ease_out_expo.xml` | present (landed with the token layer, not with auth) |
| `viewmodel/AuthViewModel.kt` | present, but it is the **pre-existing** 90-line file from 2026-07-10, not the `AuthUiState` rewrite §15 orders |

`LoginActivity` is still registered (`AndroidManifest.xml:124`) and still launched from
`MainActivity.openLoginScreen()` (`:1360`) and `openTelegramLink()` (`:1371`).
`res/values/strings_auth.xml` still carries the **old** key set
(`auth_tg_headline`, `auth_site_headline`, `auth_btn_site`, …); the §11 rewrite did not happen. What
did happen is a Russian copy edit inside the existing keys.

The shipped screen is still the two-stacked-card layout the spec names as the thing being deleted,
with the exact tells:

- Two `MaterialCardView`s stacked (`:21`, `:112`), each with its own headline, body and buttons.
- **Three competing filled primary buttons**: `btn_telegram` `:54`, `btn_site` `:193`,
  `btn_confirm_2fa` `:264` — plus two outlined ones (`btn_restart` `:96`, `btn_register_site` `:287`).
  `03-direction.md` 7.3, quoted in the spec's C2 resolution, allows one.
- Fixed `52dp` button heights (`:57`, `:196`, `:267`) — clips at 200 % font scale.
- `app:cornerRadius="26dp"` capsules (`:64`, `:105`, `:202`, `:273`, `:296`) — the shape the owner
  rejected by name and the spec resolves to `radius_button` 16 in C1.
- `android:textStyle="bold"` synthetic bold on all three filled buttons (`:62`, `:200`, `:271`).

**Verdict: untouched.** The design specification for this screen is spec-only; the shipped screen is
the July build with edited Russian strings.

---

## 3. Account tab — PARTIALLY BUILT

`res/layout/activity_account.xml` (637 lines), `ui/AccountFragment.kt` (875 lines),
`viewmodel/AccountViewModel.kt` (519 lines).

### What actually landed, and it is real

**Sign-out works, end to end.** This is the one screen-level feature the last wave shipped, and I
traced it in full:

- Layout: `activity_account.xml:569-634` — its own «Аккаунт и вход» group, `row_logout` with a
  neutral tile, a destructive title role, and an inline `pb_logout` progress indicator.
- Wiring: `AccountFragment.kt:200` `binding.rowLogout.setOnClickListener { confirmSignOut() }`.
- Confirm: `:668` `confirmSignOut()` — destructive-verb primary, cancel holds focus, and the body
  text branches on whether the tunnel is actually up (`AccountViewModel.isTunnelRunning()`).
- Execute: `:700` `beginSignOut()` → `AccountViewModel.logout()` at `AccountViewModel.kt:457`, which
  runs `AccountSession.wipe()` first and alone, then stops the core, then clears the cache and the
  avatar, under an 8 s watchdog on a `NonCancellable` job.
- Failure path: `:737` `onSignOutFailed()` — a Snackbar with a retry action, anchored above the
  bottom bar. Not a Toast, not a dead spinner.
- Completion path: the session flipping to `LoggedOut` drives `onSessionCleared()` at `:282`.

Also genuinely wired on this screen: the four hero states (`renderHeroState()` `:475` toggles
skeleton / empty / carousel / error against `activity_account.xml:209/236/286/313`), the devices row
value (`:409`), the history row value (`:525`), referral copy (`:647`), the top-up dialog (`:604`)
and the payment-method sheet (`:626`).

### What the spec ordered and did not get

`23-account-rework.md` §6.1 lists 13 new layouts. **None exists**: `fragment_account.xml`,
`layout_account_head.xml`, `layout_account_card.xml`, `layout_account_skeleton.xml`,
`sheet_payment.xml`, `sheet_top_up.xml`, `sheet_upgrade.xml`, `sheet_add_devices.xml`,
`sheet_qr.xml`, `dialog_rename_subscription.xml`, and the five library row layouts
(`row_navigation/value/toggle/destructive/ledger.xml`). Of the ten `ic_acc_*` drawables the spec
adds, only `ic_acc_logout.xml` was created — the one the sign-out row needed.

The spec's central argument is an inversion (§1.2): *time* becomes the one Display figure, *balance*
demotes to a row value, and identity becomes a two-line head with a 40dp tile and no avatar frame.
The shipped layout does the opposite, unchanged:

- `activity_account.xml:142-151` — balance is `TextAppearance.App.Display`. It is still the hero.
- `:53-98` — a 52dp avatar container holding a 48dp circle **and a camera edit badge**, the exact
  element §1.2 removes ("no 52dp avatar frame, no camera badge floating on it").
- `:76` `android:textSize="20sp"` + `:77` `textStyle="bold"` on the monogram — off-ramp, synthetic.
- `:127`, `:278`, `:355` — `radius_pill` CTAs.
- Chevrons at `18dp` (`:423`, `:486`, `:548`), dividers inset at a hardcoded `72dp` (`:434`, `:497`).

The file's own comment at `:559-568` concedes the point: it explains that the tile stays on the
logout row "because every other row on this surface has one", and that "the reworked tab drops tiles
from all four rows at once, not from this one alone." The rework it defers to has not happened.

`23-account-rework.md` §5.1 also requires the tab to **stay when signed out and become the sign-in
gate**. It still disappears: `MainActivity.updateAccountGate()` (`:1304`) sets
`binding.navAccount.isVisible = loggedIn` at `:1317`, and `:1319` bounces the user to Home if they
were standing on it.

### The billing sub-screens — untouched

Every one of them last changed 2026-07-10:

| File | Last commit |
|---|---|
| `activity_buy_tariff.xml` + `BuyTariffActivity.kt` | `f0993bd` 2026-07-10 |
| `activity_devices.xml` + `DeviceManagementActivity.kt` | `382ae0b` / `f4f0f13` 2026-07-10 |
| `activity_payment_history.xml` | `53ba629` 2026-07-10 |
| `sheet_payment_method.xml`, `item_payment_method.xml` | `30791db` 2026-07-10 |
| `dialog_top_up.xml` | `53ba629` 2026-07-10 |
| `item_subscription_card.xml` | `b803363` 2026-07-10 |

`23-account-rework.md` §6.1 marks all six as "rewritten". None was.

**Verdict: partially built.** One new feature, correctly and carefully wired; the redesign itself is
spec-only.

---

## 4. Settings — HALF-BUILT, AND THE BUILT HALF IS UNREACHABLE

This screen splits in two and the two halves have opposite problems.

### 4a. The settings **tab** (what a user actually sees) — untouched

`res/layout/layout_settings_content.xml` (1 536 lines), included at `activity_main.xml:494`, wired
by `MainActivity.setupSettings()` at `MainActivity.kt:3011`. Layout last changed `2b09fd6`,
2026-07-10.

It is functionally sound — I checked every row: 23 rows, each with a click handler
(`MainActivity.kt:3015-3053`), and every value and switch is read back in `bindSettingsState()`
(`:3058` onward). No write-without-read defects in the tab itself.

It is not the spec'd screen. `12-settings.md` §4.1 orders: a header, a **search field**, then four
named groups (`Подключение`, `Обход блокировок`, `Подписки`, `Приложение`), then an unheaded footer
pair (`Данные и резервные копии`, `О приложении`).

What ships: **no search field at all** (zero occurrences of "search" in the file), and **six** named
groups — `settings_section_connection` `:23`, `_bypass` `:548`, `_interface` `:760`,
`_subscription` `:966`, `_devices` `:1216`, `_about` `:1342`. Spec rows that do not exist anywhere:
`Чёрная тема` (the mono overlay has no row), `Меньше движения`, and the `Данные и резервные копии`
footer group (backup is a row inside `О приложении`).

`PREF_REDUCED_MOTION`, which §4.5 row 4.4 introduces, has **0 references in the entire tree** — no
constant, no writer, no reader. Not started.

### 4b. The «Дополнительно» screen — built, complete, and unreachable

`ui/SettingsActivity.kt` (599 lines, rewritten in `5736224` + `13831ba`, 2026-07-26) and
`res/xml/pref_settings.xml` (288 lines, rewritten in `202a2b5`). It is a careful piece of work: five
categories keyed to `12-settings.md` §§5.4/5.5/5.6/5.9, a deep-link `EXTRA_SECTION` mode that opens
one group at a time, a `newIntent()` factory, and 64 new Russian strings in
`res/values/strings_settings_advanced.xml`.

**Nothing launches it.**

```
grep -rn "SettingsActivity.newIntent|SettingsActivity::class" java/
→ ui/SettingsActivity.kt:81   (inside its own newIntent factory)
→ 0 call sites anywhere else
```

Its own KDoc at `SettingsActivity.kt:77` says «Вкладка настроек зовёт его так:
`startActivity(SettingsActivity.newIntent(this))`». The settings tab does not. There is no
`row_advanced` in `layout_settings_content.xml`, no menu item, no shortcut
(`res/xml/shortcuts.xml` has no Settings entry). It is declared `android:exported="false"`
(`AndroidManifest.xml:88-90`), so no external intent can reach it either.

This is the same defect `audit2026/android-settings.md` §4.1 reported ("There are two settings
screens, and one of them is unreachable") — except the wave rebuilt the unreachable one instead of
connecting it. It is now a *better* unreachable screen.

The consequence for real settings: every key below has a working reader in the core but its **only**
UI writer is behind this door.

| Key | Reader | Only writer |
|---|---|---|
| `pref_dns_hosts` | `core/CoreConfigManager.kt:937` | `pref_settings.xml:167` |
| `pref_outbound_domain_resolve_method` | `core/CoreOutboundBuilder.kt:678`, `CoreConfigManager.kt:995` | `pref_settings.xml:180` |
| `pref_real_ping_concurrency` | `handler/SettingsManager.kt:597` | `pref_settings.xml:263` |
| `pref_auto_sort_after_test` | `viewmodel/MainViewModel.kt:790` | `pref_settings.xml:272` |
| `pref_auto_remove_invalid_after_test` | `viewmodel/MainViewModel.kt:786` | `pref_settings.xml:280` |
| `pref_auto_fallback` | `ui/MainActivity.kt:626`, `:2073` | `pref_settings.xml:87` |

…plus log level, sniffing, route-only, allow-insecure, MTU, interface address, local/fake DNS,
domestic DNS, delay-test URL and the three fragment parameters. 55 of the 64 advanced strings are
referenced, and every one of those references is inside `pref_settings.xml` or `SettingsActivity.kt`.

One reachable improvement did land in this wave: `ProviderSettingsActivity` (opened from the tab at
`MainActivity.kt:3038`) now validates the User-Agent override on entry instead of storing a value the
fetch would silently replace. Note it carries a hardcoded Russian literal in Kotlin with a
`TODO(copy)` (`ProviderSettingsActivity.kt`, `USER_AGENT_ERROR`).

**Verdict: half-built.** The reachable half is untouched; the rebuilt half cannot be opened.

---

## 5. Servers — UNTOUCHED, with a live functional regression

`res/layout/item_recycler_main.xml` (130 lines, last changed `2fef5f0` 2026-07-10),
`ui/MainRecyclerAdapter.kt` (386 lines), `res/layout/layout_servers_header.xml` (108 lines,
2026-07-10).

`16-servers.md` §15.1 lists 12 new files. **None exists**: `fragment_servers.xml`, `item_server.xml`,
`item_provider_header.xml`, `field_search.xml`, `header_tab.xml`, `empty_state.xml`,
`skeleton_list.xml`, `sheet_list.xml`, `ui/servers/ServersFragment.kt`, `ServersUiState.kt`,
`ServerListAdapter.kt`, `bg_row_selectable.xml`. §15.2's changes did not land either:
`enums/PingMethod.kt` still has four values (spec: two), `res/menu/menu_main.xml` still exists (spec:
deleted), `FlagUtil.resolveFlag()` still returns an emoji `String` (spec: a drawable id), and
`ServerAffiliationInfo.getTestDelayString()` still exists (spec: deleted). The 16 flag PNGs were
never ported — `res/drawable-nodpi/` holds one JPEG.

The row is still the July build, with the tells:

- `item_recycler_main.xml:40-50` — the flag is an **emoji text glyph** in a 28dp tile at a hardcoded
  `android:textSize="18sp"`.
- `:81-84` — chip padding as raw `8dp` / `2dp` literals.
- `:121` — `android:textSize="12sp"` on top of `TextAppearance.App.Numeric`, defeating the ramp on
  the same view; plus `textFontWeight="700"` inline.
- `layout_servers_header.xml:30-73` — four `36dp` icon buttons, below the 48dp touch-target floor.
- `:89-104` — a `44dp` search field with hardcoded `12dp` / `14dp` padding and `14sp` text.

### The regression: every per-server action is unreachable

This is `bugs-android-confirmed.md` D04, still open, and I confirmed both halves in source:

- `MainRecyclerAdapter.kt:64` declares `var onItemLongClick`, with a KDoc that says out loud:
  *"The long-press server-actions menu was removed, so this callback is no longer invoked by the
  adapter."*
- `bindServer()` ends at `:245-248` with a click listener and the comment
  *"Long-press server-actions menu removed: long-press is a no-op (no listener set)."*
  There is no `setOnLongClickListener` anywhere in the adapter — a grep for `LongClick` across the
  whole module returns exactly three hits: that one declaration and the two assignments below.
- `MainActivity.kt:816-817` still assigns `serversAdapter.onItemLongClick` and
  `homeAdapter.onItemLongClick` to `showServerActions(guid)`.

So `showServerActions()` (`MainActivity.kt:825`), `ServerActionsSheet.kt` and
`sheet_server_actions.xml` (271 lines) are all dead. **A server imported by QR, clipboard or by hand
can never be deleted, edited, shared or shown as a QR code from the servers list.** There is no
long-press, no swipe, no trailing control.

What *did* land here, and it is a genuine correctness fix: the `-2L` "measuring" sentinel is gone.
`MainViewModel` now keeps an in-memory `measuringGuids` set (`MainViewModel.kt:127`, `:130`) and the
adapter reads it (`MainRecyclerAdapter.kt:221-232`), so a row that is merely still being measured can
no longer be deleted by «Удалить недоступные». Note `item_recycler_main.xml:99-100` still carries the
stale comment describing the removed sentinel.

**Verdict: untouched**, plus one real data-loss fix in the view-model behind it.

---

## 6. Cross-cutting findings that hit every screen

### 6.1 The component layer has zero consumers

Confirmed independently of `android-foundation.md`: 9 binder files under
`java/com/v2ray/ang/ui/component/` (~1 900 lines) and 11 `res/layout/view_*.xml` layouts, and a grep
for the package and every exported symbol across `java/` returns **0 results outside the package
itself**. The only `<include>` of a `view_*.xml` is `view_empty_state.xml` pulling in its own action
button.

Its footprint in `res/values/styles.xml` tells the same story: of 58 `Widget.Departament.*` styles,
**25 are referenced nowhere** — no layout, no `res/xml`, no theme attribute. That list includes all
five row styles the specs designate as the product's row vocabulary:

```
Widget.Departament.Row.Navigation   Row.Value   Row.Toggle   Row.Destructive   Row.Action
Widget.Departament.Segment          SegmentGroup
Widget.Departament.Card             Card.Pressable   Card.Selectable
Widget.Departament.Tile.Accent      Tile.Destructive
Widget.Departament.Button.Destructive(.Tall)   Button.Icon.Accent / .Danger / .Filled
Widget.Departament.Skeleton.Block   TextField.ReadOnly   Toolbar.Brand   Divider.Full
```

The screens were never migrated onto the vocabulary that was built for them.

### 6.2 The Cyrillic font fix reaches the body text but not the sub-page titles

The migration is real: `TextAppearance.App.Headline/Title/Title.Medium/Body/Subtitle/Caption` all
resolve to Golos Text (`styles.xml:85/102/120/137/154/171`). Space Grotesk is retained deliberately
for `Display`, `Chip`, `Numeric` and the wordmark, which are Latin/numeric roles.

But `res/layout/activity_base.xml:19` still hardcodes
`app:titleTextAppearance="@style/ToolbarBrandTitle"`, and `ToolbarBrandTitle` is Space Grotesk
(`styles.xml:281`). That layout is the shared toolbar for **27 sub-page activities**
(files calling `setContentViewWithToolbar`). The theme *does* set
`toolbarStyle` → `Widget.Departament.Toolbar` (`themes.xml:302`), but the per-instance attribute in
the layout wins.

Exactly one activity works around it: `SettingsActivity.applySeamlessToolbar()` at
`SettingsActivity.kt:108`. That is the unreachable screen. The fix was written into the one surface
nobody can open; the other 26 reachable sub-pages still hand their Russian titles to a face with no
Cyrillic glyphs. `Widget.Departament.Toolbar.Brand` — the style that exists precisely so the
wordmark and a Russian title can differ — is one of the 25 styles referenced nowhere.

### 6.3 Previously reported unreachable features — is there an entry point now?

| Reported | Status today | Evidence |
|---|---|---|
| «Привязать Telegram» CTA banner on Home | **Still dead.** `updateAccountGate()` (`MainActivity.kt:1304`) sets `header.root.isVisible = loggedIn` (`:1312`) and `header.groupLogin.isVisible = false` unconditionally (`:1315`); `updateLoginCtaVisibility()` (`:1345`) only shows the CTA when `!isLoggedIn()` (`:1350-1352`). Mutually exclusive — the CTA's parent is hidden in exactly the state the CTA is shown. | `MainActivity.kt:1304-1352` |
| «Привязать Telegram» in the onboarding card | **Reachable**, but only in one narrow state: signed in, zero servers, Telegram not linked | `MainActivity.kt:948` (listener), `:1401` (visibility) |
| Per-server actions (delete / edit / share / QR) | **Still unreachable.** See §5 | `MainRecyclerAdapter.kt:64`, `:248`; `MainActivity.kt:816-817` |
| `CheckUpdateActivity` | **Still unreachable** — 0 launch references | manifest only |
| `SubSettingActivity` | **Still unreachable** — 0 launch references | manifest only |
| `LogcatActivity` | **Still unreachable** — 0 launch references | manifest only |
| `SettingsActivity` | **Newly unreachable** — rebuilt this wave, 0 launch references | `AndroidManifest.xml:88-90` |
| Memory card on Home | **Still unreachable** — `PREF_SHOW_MEMORY` is read (`MainActivity.kt:2141`) and written nowhere | — |

### 6.4 Minor: a string with no reader

`res/values/strings_settings_hub.xml` `settings_dns_custom` has no reference in `java/` or `res/`.
(`dns_preset_names` / `dns_preset_values` *are* used, via `R.array` at `MainActivity.kt:3225`
and `:3237`.)

---

## 7. What to tell the owner, plainly

The design waves produced a great deal of writing and a foundation. What a user holding the phone
would notice, compared with the July build, is:

1. Russian text is now set in a face that can draw Russian, on every screen. Real, visible, valuable.
2. They can sign out. Real, and carefully built.
3. A stalled latency check no longer deletes servers. Real, invisible until it saves them.
4. The subscription User-Agent field rejects input the app could not send.

That is the list. The home screen, the sign-in screen, the servers list and the settings tab look
and behave exactly as they did on 2026-07-10. The account tab gained one row. The one screen that
*was* rebuilt to a 2026 specification — «Дополнительно» — cannot be opened, which also means a dozen
core-behaviour settings have no way in.

The single highest-value change available right now is not a redesign: it is restoring the
long-press (or better, a trailing control) on the server row, because today a manually added server
cannot be removed, and adding one row to the settings tab that calls
`SettingsActivity.newIntent(this)`, because that one line converts 599 lines of finished work from
dead code into a shipping screen.
