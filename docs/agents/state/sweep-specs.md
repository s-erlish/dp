# Sweep: requirements in `docs/design2026/` that no wave was ever given

**Written** 2026-07-26 · **Scope** all 20 specs in `/home/user/dp/docs/design2026/` (40,331 lines)
checked against `/home/user/dp` (Android) and `/home/user/v2rayN` (desktop), branch
`claude/app-audit-agents-hyyftk`. **Read-only**: no source file changed, no git command run.

**Method.** I did not re-read the specs for design content. I read them for *obligations*, then
looked for the implementing line. An item is listed OPEN only where I checked the code and it is
genuinely not there, with a file:line. Where a document claims completion I distrusted it and
grepped. Where two documents disagree I say so instead of picking one. Where a document records a
decision **not** to do something, it is in §F and it is not an open item.

Items already covered by the fixed/committed list or the in-flight screen waves are excluded unless
I found them incomplete, in which case they are marked **[incomplete, not missing]**. Two caveats
carried from `STATE-OF-WORK.md`: both trees were being edited while I read them, so line numbers
drift (symbols do not), and there is no verified green checkpoint.

---

## The one-paragraph answer

The specs' *screen* obligations are being worked. Their **between-screen** obligations are not, and
they cluster in five places nobody was briefed on: **offline as a designed state** (zero on Android,
one screen on desktop with the wrong string), **the persistent status strip** (built on Android,
never wired to anything, absent on desktop), **adaptive layout** (`values-sw600dp` does not exist;
the 720 cap and the navigation-rail style have zero readers), **the list-performance floor**
(`ListAdapter`/`DiffUtil`/stable IDs = zero occurrences in the whole Android tree against 19
`notifyDataSetChanged()` sites), and **the deep-link surface** (`depv://import` still rewrites the
user's server list with no confirmation, and the entire `depv://` destinations family including
`depv://link/{token}` errors out). Separately, one three-line file — `res/layout/activity_base.xml`
— silently defeats the theme-level Cyrillic fix for twelve screens and is the highest
value-per-byte item in this report. Three built components (`view_status_strip`, `view_chip`,
`view_meter`), one built menu (`menu_bottom_nav`) and ten declared styles have zero consumers.

---

## A. Cross-cutting rules in `00-rules.md` that no wave owns

### A1 · Offline is a designed state — never built on Android; one screen and the wrong copy on desktop

`00-rules.md` §9.6 and §15 (Offline is one of the eleven states "every screen ships"), expanded in
`10-design-system.md` §8 "Offline, as a designed state — every screen that reads the network
implements it". §17.2: any missing §15 state is **at least P1**.

- **Android: nothing.** `grep -rn 'Нет сети\|offline\|Offline\|устарет\|последние данные'` over
  `res/values/*.xml` and `java/` returns **0**. No bar, no string, no stale caption, no gating of
  network-dependent actions.
- **Desktop: one surface.** `Views/AccountView.axaml:177` `Classes="OfflineBar"` bound to
  `AccountViewModel.cs:2270` `IsOffline`. That is the only real one —
  `Views/ConnectHeroView.axaml:134` reuses the same class for the TUN-downgrade banner, which is a
  different condition. HomeView, ServerListView, SettingsView, BuyView, DevicesView,
  PaymentHistoryView and CheckUpdateView all read the network and have none.
- **The copy is the §9.4 error string, not the §9.6 bar string.** The bar renders
  `Common_NetworkError` = «Нет подключения к интернету. Проверьте сеть и повторите.»
  (`Common/L.Common.cs:55`). §9.6 and `10-design-system.md` §8 specify **«Нет сети. Показаны
  последние данные.»**
- **The stale marker was never written.** `grep -rn 'могли устареть'` over both trees = **0**. §8's
  second bullet requires the caption «Данные могли устареть» under the affected block.

State: **never implemented** (Android) / **incomplete** (desktop). Value **high**, size **M**.

### A2 · The persistent status strip — built on Android, wired to nothing; absent on desktop

`11-app-structure.md` §8.2 (six conditions, priority-ordered), `24-tab-conformance.md` D-48,
`32-master-plan-android.md` §8.10.

- `res/layout/view_status_strip.xml` exists (98 lines, correct anatomy, `accessibilityLiveRegion`
  at `:78`). **Consumers: zero.** No `<include layout="@layout/view_status_strip">` in any layout,
  no `R.layout.view_status_strip` in any Kotlin file, and no `StatusStripBinder.kt` — `ui/component/`
  holds exactly nine files (ChipBinder, ComponentSupport, EmptyStateBinder, RowBinder,
  SelectionBinder, SingleClick, SkeletonBinder, SubPage, ToolbarBinder).
- None of §8.2's six conditions (offline · подписка истекла · истекает <3 дней · лимит устройств ·
  провайдер не обновился · ядро/TUN) is implemented on either platform.
- **This is not covered by the desktop toast refusal** (§F1). The owner refused *bottom transient
  notifications*; the strip is a top-of-content condition bar and a separate mechanism.

State: **implemented but nothing reads it** (Android) / **never implemented** (desktop).
Value **high**, size **M**.

### A3 · Adaptive layout: `sw600dp`, the 24 gutter, the 720 cap, the rail

`00-rules.md` §3.1 ("gutter steps 16 → 24 at `sw600dp` and at window width >= 1000px"), §4.1
(content capped at 720 and centred), §7.7 and §11.4 (bottom nav becomes a `NavigationRailView` at
`sw600dp`), §12.3 (desktop content capped at 720, "a stretched phone layout across 1920px is the
desktop version of the scaled-up-phone-UI failure").

- `res/values-sw600dp/` **does not exist**. `ls -d res/values*` yields values, -ar, -bn, -bqi-rIR,
  -fa, -night, -ru, **-sw360dp-v13**, -vi, -zh-rCN, -zh-rTW.
- `@dimen/content_max_width` 720dp (`res/values/dimens.xml:50`) — **zero readers** anywhere.
- `Widget.Departament.NavigationRail` (`res/values/styles.xml:947`), bound at
  `res/values/themes.xml:308` — **zero `NavigationRailView` instances**; the only two matches in the
  tree are the style and the binding.
- Desktop has one gutter and no breakpoint: `Assets/GlobalResources.axaml:253` `Gutter` = `16,0`,
  `:282` `Size.Gutter` = 16, and no `>= 1000` window-width branch exists in `MainWindow.axaml.cs`.
- The 720 cap is applied on four desktop views (AccountView, BuyView, DevicesView,
  PaymentHistoryView) and on **none** of HomeView, SettingsView, ServerListView, LoginView,
  ConnectHeroView, CheckUpdateView, DnsSubView.

State: **never implemented**. Value **medium-high**, size **M**.

### A4 · The list vocabulary and the performance floor

`00-rules.md` §11.2 ("List → `RecyclerView` + `ListAdapter` + `DiffUtil`") and §11.5
("adapters use stable IDs and `DiffUtil`; **no `notifyDataSetChanged()` on a visible list**"), which
is scored dimension 2 of §17.1 under a "no dimension below 3" ship bar.

Measured in `V2rayNG/app/src/main/java/`:

- `grep -rn 'ListAdapter\|DiffUtil'` → **0 files**.
- `grep -rn 'setHasStableIds\|getItemId'` → **0**.
- `grep -rn 'notifyDataSetChanged'` → **19 call sites**: `MainRecyclerAdapter.kt` ×5,
  `PerAppProxyAdapter.kt` ×2, `AccountFragment.kt` ×2, and one each in `adapter/PaymentsAdapter.kt`,
  `adapter/DeviceAdapter.kt`, `UserAssetActivity.kt`, `SubscriptionPagerAdapter.kt`,
  `SubSettingActivity.kt`, `ServerProxyChainMemberAdapter.kt`, `RoutingSettingActivity.kt` and others.
- `MainRecyclerAdapter.kt:34` is a bare `RecyclerView.Adapter<…>`, not a `ListAdapter`.

The desktop half of the same rule is satisfied — `Views/ServerListView.axaml:119-123` puts the inner
list on a `VirtualizingStackPanel`.

State: **never implemented** (Android). Value **high**, size **L**.

### A5 · `Select` — one of R15's fifteen components, and the 90 controls waiting on it

`22-components.md` R15 names Select in the vocabulary; §5 specifies it and §11.2 forbids `Spinner`
by name. `24-tab-conformance.md` Part 1 budgets 85 replacements.

`STATE-OF-WORK.md` §2.6 already records that the component is spec-only. What is *not* recorded is
that its consumers are all on screens no wave lists:

- Android **13 `<Spinner>`**: `activity_server_shadowsocks.xml`, `activity_server_vless.xml`,
  `activity_server_vmess.xml`, `dialog_config_filter.xml` (A-41), `layout_tls.xml` ×4,
  `layout_tls_hysteria2.xml` ×2, `layout_transport.xml` ×3 — i.e. the server-editor family (A-13,
  Wave 5) and the TLS/transport partials, plus 1 `AutoCompleteTextView`.
- Desktop **76 `<ComboBox>`** across `Views/`.

State: **never implemented**, and the screens that would consume it are unassigned.
Value **medium**, size **L**.

### A6 · Segmented control built, zero consumers; the one screen that needs it is unowned

`22-components.md` §6, `24-tab-conformance.md` Part 1 ("12 instances → 1 component").

- `Widget.Departament.SegmentGroup` (`res/values/styles.xml:523`) and `Widget.Departament.Segment`
  (`:545`) — **0 references** in `res/layout`, `res/menu`, `res/xml`, `java/`.
- The app's only segmented control, `res/layout/activity_local_proxy.xml:91-165` (A-28,
  «Локальный прокси»), is still five `?attr/materialButtonOutlinedStyle` `MaterialButton`s at a
  **fixed** `android:layout_height="44dp"` with inline `android:textSize="13sp"` and 14dp margins —
  three separate §3.3/§5.2/§1.4.5 defects in one control.

State: **implemented but nothing reads it**. Value **medium**, size **S** (one screen).

### A7 · Form law §7.4 — blur validation, autofill, password toggles

Measured across `java/` and `res/layout/`:

- **Validate on blur, not per keystroke**: `setOnFocusChangeListener` appears in exactly **one** file
  (`ui/ProviderSettingsActivity.kt`); `addTextChangedListener` / `doAfterTextChanged` /
  `doOnTextChanged` appear **19** times.
- **Autofill hints on both platforms**: `android:autofillHints` = **4** occurrences in all layouts.
- **Password fields have a show/hide toggle**: `endIconMode="password_toggle"` = **2**
  (`activity_login.xml:174`, `dialog_webdav.xml:87`). Other password/secret fields do not.
- Part 1's "58 bare `EditText` → one text field": **46 bare `<EditText>`** remain against 30
  `TextInputEditText`.

State: **partially implemented, no owner**. Value **medium**, size **M**.

---

## B. `24-tab-conformance.md` surfaces that no wave lists

### B1 · A-38 sub-page host — never landed, and it defeats the theme-level Cyrillic fix

**This is the highest value-per-byte item in the report: three lines in one file.**

`res/layout/activity_base.xml`:

| Line | Ships | Spec |
|---|---|---|
| `:6` | `android:fitsSystemWindows="true"` | A-38: replaced by the one inset strategy of `11-app-structure.md` §3.1.5 |
| `:11` | `android:layout_height="?attr/actionBarSize"` | A-38 / §3.3: `@dimen/toolbar_height` 56 as a **minimum**. A fixed height on a text-bearing bar clips at font scale 200% — P1 by §14.5 |
| `:19` | `app:titleTextAppearance="@style/ToolbarBrandTitle"` | A-38 / §4.8: H3, `TextAppearance.App.Title` 16/700 |
| `:28` | `app:indicatorColor="@color/color_fab_active"` | §11.1: colours are consumed as `?attr/…`, not `@color/…` |

`ToolbarBrandTitle` (`res/values/styles.xml:280-289`) sets `@font/space_grotesk`, whose binary maps
735 codepoints and **zero** in U+0400-U+04FF. §5.1: *"A Russian string found in the brand face is a
P1 defect, not a polish item."*

`res/values/themes.xml:302` already binds `toolbarStyle` to `Widget.Departament.Toolbar`, which is
the fix — **and the inline layout attribute at `:19` overrides it.** The style's own comment
(`styles.xml:276-279`) says exactly this and no one acted on it.

Twelve activities still inflate this host with Russian titles via `setContentViewWithToolbar`:
`BuyTariffActivity.kt:92`, `ServerActivity.kt:165`, `ProviderSettingsActivity.kt:71`,
`SettingsActivity.kt:87`, `LocalProxyActivity.kt:54`, `DeviceManagementActivity.kt:49`,
`TaskerActivity.kt:27`, `LoginActivity.kt:65`, `ScannerActivity.kt:28`,
`PaymentHistoryActivity.kt:45`, `tv/TvSendActivity.kt:54`, `tv/TvReceiveActivity.kt:40`.

(Sixteen other screens *were* migrated to `res/layout/view_toolbar.xml` — the seamless bar is real
and correct. These twelve were simply never swept.)

State: **never implemented**. Value **high**, size **S**.

### B2 · A-43 / §7.2 — the deep-link confirm sheet, called "mandatory" and "a new, required surface"

`ui/UrlSchemeActivity.kt:82` `handleDepvScheme()` dispatches and **executes immediately**, with no
confirmation of any kind, then toasts and finishes:

- `:100` `"import"` → `importDecodedConfig(decoded)` — batch-imports a base64 payload
- `:110` `"add"` → `parseUri(raw, null)` — imports a provider or config by URL
- `:118` `"routing"` → `importRoutingRules(json, apply = op == "onadd")`
- `:34-38` the `ACTION_SEND` `text/plain` path does the same

`11-app-structure.md` §7.2 marks all four **"confirm sheet, mandatory"** and states the reason:
*"A link in a chat message must not be able to rewrite what the user connects through."*
`32-master-plan-android.md` §22.6 repeats it ("REBUILD the surface, keep the routing") and adds that
a deep link arriving while the app is closed must open `MainActivity` on Главная with the sheet
already presented, never a bare translucent activity that finishes — which is exactly what
`:63-64` does today (`startActivity(MainActivity); finish()`).

State: **never implemented**. Value **high** (security-adjacent), size **M**.

### B3 · §7.2 — the whole `depv://` *destinations* family, and §7.1's route vocabulary

`handleDepvScheme`'s `when (uri.host)` handles connect/open, disconnect/close, toggle, import, add,
routing. Everything else falls through to `:132` `else -> toastError(R.string.editor_failed)`.

So every destination link in §7.2's second table fails: `depv://home`, `depv://servers`,
`depv://account`, `depv://account/buy`, `depv://account/devices`, `depv://account/history`,
`depv://subscription/{uuid}`, `depv://settings`, `depv://settings/{group}` — and
**`depv://link/{token}`**, which the spec singles out as closing *"a real product gap: the
Telegram-link flow returns the user to the app today with no route to hand the token to."*

Upstream of that, §7.1's route-identity table (30 stable route strings, *"identical on both
platforms… this is what makes deep links, session restore and the URL schemes page possible"*)
exists on **neither** platform: no route constant table, no parser, no restore.

State: **never implemented**. Value **high**, size **L**.

### B4 · §7.3 / A-44 / §22.1-22.4 — the four surfaces outside the app window

All four are Wave 7 or unassigned, and all four are untouched.

- **Launcher shortcuts.** `res/xml/shortcuts.xml` carries 4 shortcuts (`shortcuts_switch`,
  `shortcuts_scan`, `shortcuts_start`, `shortcuts_stop`) labelled `@string/app_widget_name`,
  `@string/menu_item_import_config_qrcode`, `@string/toast_services_start`,
  `@string/toast_services_stop` — none of the Russian labels §22.4 names.
  **The two specs disagree and need an owner call:** `11-app-structure.md` §7.3 drops start/stop and
  adds «Серверы» → `depv://servers` (3 shortcuts); `32-master-plan-android.md` §22.4 says KEEP all
  four with Russian labels. Note the §7.3 version also depends on B3.
- **QS tile.** `service/QSTileService.kt:29,32` sets `qsTile?.label` only. `setSubtitle` appears
  **nowhere** in the tree, so §22.2's three-row label/subtitle/icon matrix — including the
  `Unavailable` → «Нет подписки» disabled state — does not exist.
- **Home-screen widget.** `res/layout/widget_switch.xml` is byte-for-byte the pre-redesign file:
  `:19-20` a 45dp icon, `:30` `@style/TextAppearance.AppCompat.Small`, `:31`
  `android:textColor="@android:color/white"` — a raw colour literal that §1.5's hex-only grep does
  not catch. No server name, no state, no unified server icon (§10.5).
- **Notification.** `handler/NotificationManager.kt:160,165` builds two actions, stop and restart.
  §22.1 and §7.3 both require **«Сменить сервер»** → `depv://servers`; it does not exist, and per B3
  could not resolve if it did.

State: **never implemented**. Value **medium**, size **M**.

### B5 · A-42 Tasker

`ui/TaskerActivity.kt:27` still `setContentViewWithToolbar(binding.root, …, title = "")`. A-42 and
§22.5: give it «Действие Tasker», put its `Spinner` on the Select, tokenise.

State: **never implemented**. Value **low**, size **S**.

### B6 · The off-scale and inline-face debt lives mostly on unowned screens

`00-rules.md` §1.5 baseline was "325 off-scale `dp` across 25 files" and *"every screen touched
during the rebuild leaves its own files at zero"*. Current: **247** hits.

| File | Off-scale dp | Owner |
|---|---|---|
| `activity_local_proxy.xml` | 75 | A-28 — **nobody** |
| `activity_provider_settings.xml` | 62 | A-24 — **nobody** |
| `layout_settings_content.xml` | 42 | settings tab, in flight |
| `activity_main.xml` | 21 | home, in flight |
| `activity_account.xml` | 17 | account, in flight |
| `activity_login.xml` | 5 | sign-in, in flight |
| `activity_tv_receive.xml` / `activity_tv_send.xml` | 4 / 3 | A-31 / A-32 — **nobody** |
| `activity_buy_tariff.xml` | 4 | A-08, Wave 3 |
| `widget_switch.xml` | 2 | A-44 — **nobody** |
| `item_qrcode.xml` | 2 | A-40 — **nobody** |
| remainder (7 files) | 10 | mixed |

Roughly **150 of the 247** sit on screens no wave lists. The same split holds for the 71
`android:fontFamily` / `android:textSize` hits (§5.2 "roles, not sizes"): the non-in-flight files are
`activity_local_proxy.xml`, `activity_provider_settings.xml`, `activity_tv_receive.xml`,
`activity_tv_send.xml`, `layout_transport.xml`, `item_buy_tariff.xml`.

Clean and to be kept clean: raw hex in layouts **0**, `textAllCaps` **0**.

State: **never assigned**. Value **medium**, size **M**.

---

## C. Built, correct, and assigned to nothing

Each of these has a real implementation and zero call sites. They are not missing work; they are
work that will silently rot unless someone is told to place it.

| Artefact | Consumers | Note |
|---|---|---|
| `res/layout/view_status_strip.xml` | **0** | See A2. 98 lines, no binder |
| `res/layout/view_chip.xml` | **0** | `ChipBinder` has exactly one real consumer, `LogcatRecyclerAdapter.kt:49` |
| `res/layout/view_meter.xml` | **0** | Its header says it exists to end the meta-bar defect (an 11sp label printed over a moving accent fill at 2.9:1). `layout_subscription_meta_bar.xml` still ships |
| `res/menu/menu_bottom_nav.xml` | **0** | `activity_main.xml:474-481` still hand-rolls a `LinearLayout` bar |
| `Widget.Departament.SegmentGroup` / `.Segment` | 0 / 0 | See A6 |
| `Widget.Departament.NavigationRail` | 0 | See A3 |
| `Widget.Departament.Row.Toggle` / `.Row.Destructive` | 0 / 0 | Archetypes 4 and 5 of §8 |
| `Widget.Departament.Tile.Accent` / `.Tile.Destructive` | 0 / 0 | Two of D-5's three tiles |
| `Widget.Departament.Card.Pressable` / `.Card.Selectable` | 0 / 0 | The pressable/selectable object treatment of R5 |
| `Widget.Departament.Skeleton.Block` | 0 | `.Skeleton.Bar` has 1 |
| `@dimen/content_max_width` | 0 | See A3 |

(`Widget.Departament.Snackbar`, `.Sheet`, `.Toolbar.Brand`, `.Divider` and the dialog styles show
zero *layout* references but are correctly bound through `res/values/themes.xml:302-333` — those are
fine.)

Value **medium**, size **S each**.

---

## D. Two gaps logged inside the code itself, addressed to an owner who does not exist

These are not my inferences. They are written in the source, by the wave that hit them.

### D1 · ICON GAP — `ic_arrow_back`, `ic_warning`, `ic_error`

- `res/layout/view_toolbar.xml:59-61`: *"`res/drawable` has no back glyph. `22-components.md` 12.1
  asks for a 24dp `ic_arrow_back`; until the icon owner lands it, the 24dp chevron is mirrored."*
  Ships as `app:icon="@drawable/ic_chevron_right"` + `android:scaleX="-1"` (`:71-72`) on **every**
  sub-page in the app. `ls res/drawable | grep -i arrow` → only `ic_arrow_drop_down.xml`.
- `res/layout/view_status_strip.xml:33-35`: *"`res/drawable` has no `ic_warning` and no `ic_error`
  yet; filed with the icon owner. Until they land, the warning and error severities carry the info
  glyph in the correct tone."* §6.3: colour is never the only signal.

### D2 · STRING GAP — «Назад» is a hardcoded literal

`res/layout/view_toolbar.xml:70` `android:contentDescription="Назад"`, with the comment at `:64-65`:
*"there is no «Назад» in `res/values*/strings*.xml` and this wave may not add one; the literal below
wants to become `@string/cd_back`."*

It is the **only** hardcoded `contentDescription` in `res/layout/` (all others are `@string/…` or
explicitly `tools:ignore`). Consequence: the back affordance on every migrated sub-page carries an
unlocalised accessible name, against §10.7 / §14.3.

State: **never assigned**. Value **medium**, size **S**.

---

## E. A cross-cutting default that two files disagree about

**The theme default.** `00-rules.md` §6.9 and §0.4.1 make **dark** the product default.

- `handler/SettingsManager.kt:629` reads `PREF_UI_MODE_NIGHT` with default **`"0"` →
  `MODE_NIGHT_FOLLOW_SYSTEM`**.
- `ui/MainActivity.kt:3422` reads the same key with default **`"2"`**, so
  `currentAppearanceIndex()` returns 1 and the «Оформление» picker pre-selects «Тёмная».

On a fresh install on a light-mode phone the app renders **light** while the picker says **dark** is
chosen. Neither value is the specified default, and the two disagree with each other.

Related and unchecked by anyone: §13's parity contract requires *"the default value of every
setting"* to be identical across platforms. No document in the corpus contains that comparison table,
and I found no evidence any wave built one.

State: **contradicted / never verified**. Value **medium**, size **S**.

---

## F. Refusals and deliberate non-work — do not resurrect these

Every one of these is a recorded decision. They are listed so a later sweep does not "fix" them.

1. **Desktop bottom notifications / the transient toast channel.** `Views/MainWindow.axaml:590-597`
   records it verbatim: «Владелец не хочет НИКАКИХ нижних уведомлений (ни на подключении, ни на
   добавлении подписки, ни на прочих событиях)». `snackHost` keeps its markup but ships
   `IsVisible="False"` + `IsHitTestVisible="False"`, and `DelegateSnackMsg` is a deliberate no-op;
   connection errors surface through the connect shield's Error state instead. This **overrules**
   `11-app-structure.md` §8.1's desktop column and the transient half of D-48.
   **It does not cover §8.2's persistent status strip** — different mechanism, still open (A2).
2. **The desktop «Серверы» destination.** Owner decision. Anything in `11-app-structure.md` §3.2
   item 3, `24-tab-conformance.md` D-09/D-10 or `33-master-plan-pc.md` that adds a fourth rail item
   is overruled. `ServersView.axaml` and `CompactServersView.axaml` are dead files; `Geo.Nav.Servers`
   is unused **on purpose**.
3. **`res/anim/nav_press.xml` is kept as a separate file on purpose.** Its header explains that the
   four bottom-nav items are discrete objects with `@null` backgrounds, so R5's "rows do not scale"
   does not apply, and §0.4.8 forbids the ripple — leaving scale as the only acknowledgement the
   control is allowed inside §7.3's 100ms. Delete it only together with its four references when the
   M3 nav bar lands. (Both it and `press_scale.xml` are already on the single D-11 recipe: 0.97,
   90ms `ease_out_quart` in, 160ms `ease_out_quint` out. That work is done.)
4. **`motion_spin` is linear on purpose.** The one exemption to §8.3's linear ban, documented in
   `res/values/motion.xml` and `00-rules.md` §3.7. Not a defect.
5. **`accent_hover` / `accent_pressed` have no Android readers on purpose.**
   `res/values/colors.xml:270-271` and `res/values-night/colors.xml:161-162` state they are parity
   tokens with the desktop client because Android has no hover state (§7.1).
6. **`icon_purple` / `icon_orange` / `icon_yellow` / `icon_green` and `Brush.Tile.Purple` stay.**
   D-5 keeps the retired tile colours alive until the last referencing screen migrates; `icon_purple`
   and `Brush.Tile.Purple` must remain aliases of blue for as long as anything references them.
   Deleting them early breaks live screens.
7. **`StatusBarView` mounted at 0×0 on desktop is load-bearing** (tray icon, clipboard, sudo
   password, TUN elevation), notwithstanding `11-app-structure.md` §3.2 item 6.
8. **Sign-out is deliberately not undoable.** `ui/AccountFragment.kt:658` records the reasoning
   against §7.5's undo-over-confirmation default; it uses a dialog plus a «Повторить» retry instead.

---

## G. Watch list — owned by an in-flight wave, but the spec obligation is broader than the screen

Not open items. Flagged so the in-flight waves do not close having done the screen and missed the rule.

- **`Brush.HomeGradient` still paints three desktop surfaces** — `Views/LoginView.axaml:237`,
  `Views/OnboardingView.axaml:43`, `Views/AccountSyncView.axaml:47` — against §1.4.3 / §6.5's
  no-gradients ban. It was correctly removed from `MainWindow.axaml:423` and `HomeView.axaml:23`.
  LoginView is D-14 (sign-in, in flight), OnboardingView is D-15 (delete list), AccountSyncView is
  D-16. The **token itself** (`GlobalResources.axaml:147, 230`) should go with the last reference.
- **The Android servers empty state is two-thirds of a state** — `layout_servers_empty.xml:41,49,59`
  gives title + two actions and **no explanatory line**, against §9.5's title + one line + one action
  formula (which specifies «Добавьте провайдера или отсканируйте QR-код…» and a single «Добавить
  провайдера»). Servers wave.
- **Desktop compact mode is still live** — `MainWindow.axaml.cs:33` `CompactBreakpointWidth = 760`,
  `:34` `LayoutHysteresis`, `:257` `ApplyLayoutMode`, `:184` `btnRailToggle`, `:209/216`
  `ToggleLayoutSize`; `MainWindow.axaml:15-16` `MinWidth="380" MinHeight="620"` against §12.3's
  900×600 floor. D-03/D-05/D-11 (Wave 2, shell) marks the whole branch DELETE. The shell wave owns
  it, but note the interaction with §F2: the compact `BottomNavBar` is also where two of the seven
  remaining Cyrillic-face holes live.
- **`toast_status.xml` and the 200-odd `toast*` call sites** against 5 `Snackbar.make` sites
  (A-39 / §8.1 / §1.4.8). Home + shell waves own the deletion; the 200 call sites are a sweep nobody
  has been given.

---

## Suggested order

Ordered by value ÷ size, and by what unblocks what.

1. **B1** — three lines in `activity_base.xml`; fixes the P1 brand-face defect on twelve screens at once.
2. **D2** — add `@string/cd_back`, one string, one attribute.
3. **E** — align the two `PREF_UI_MODE_NIGHT` defaults on `"2"`.
4. **B2** — the deep-link confirm sheet. Security-adjacent, and it is the only item here a user can be harmed by.
5. **A2** — give `view_status_strip.xml` a binder and place it; six conditions then land free.
6. **A1** — offline: one string pair, one bar, one stale caption, per platform.
7. **D1** — three drawables (`ic_arrow_back`, `ic_warning`, `ic_error`); unblocks the correct back glyph everywhere and the strip's severity channel.
8. **A6** — one screen (`activity_local_proxy.xml`) onto the segment styles; retires 75 off-scale values with it.
9. **A3** — `values-sw600dp/` + the 720 cap + the rail.
10. **A4** — the `ListAdapter`/`DiffUtil`/stable-ID migration; large, but it is a scored ship-bar dimension.
11. **B3**, **A5** — the route vocabulary and Select. Both large, both gate several later screens.
12. **B4**, **B5**, **B6** — Wave 7 tail.
