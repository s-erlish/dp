# Recon — Desktop Account tab & billing surfaces (for the Android port)

Source of truth: `/home/user/v2rayN`, branch `claude/app-audit-agents-hyyftk`.
Everything below was read from the files cited; nothing is inferred or invented.
Android counterpart paths are under `/home/user/dp/V2rayNG/app/src/main/`.

---

## 0. Inventory — what exists on desktop

| Surface | View | Code-behind | ViewModel |
|---|---|---|---|
| Account tab | `v2rayN.Desktop/Views/AccountView.axaml` (1475 L) | `AccountView.axaml.cs` (524 L) | `ViewModels/AccountViewModel.cs` (2920 L) |
| Home account chip | `Views/HomeAccountChip.axaml` (131 L) | `HomeAccountChip.axaml.cs` (233 L) | — (reads `AccountSession` directly) |
| Post-login sync overlay | `Views/AccountSyncView.axaml` (176 L) | `AccountSyncView.axaml.cs` (324 L) | shares `AccountViewModel.Shared` |
| Login sub-page | `Views/LoginView.axaml` (954 L) | `LoginView.axaml.cs` (1377 L) | shares the ONE `AccountViewModel` |
| Buy sub-page | `Views/BuyView.axaml` (709 L) | `BuyView.axaml.cs` (173 L) | `ViewModels/BuyViewModel.cs` (840 L) |
| Devices sub-page | `Views/DevicesView.axaml` (491 L) | `DevicesView.axaml.cs` (73 L) | `ViewModels/DevicesViewModel.cs` (572 L) |
| Payment history sub-page | `Views/PaymentHistoryView.axaml` (351 L) | `PaymentHistoryView.axaml.cs` (40 L) | `ViewModels/PaymentHistoryViewModel.cs` (293 L) |
| Subscription meta bar (Home server-group header) | `Views/SubscriptionMetaView.axaml` (335 L) | `SubscriptionMetaView.axaml.cs` (687 L) | binds `HomeServerGroup` |
| API/session layer | `v2rayN.Desktop/Account/**` (17 files, 3206 L) | | |

**`HomeHeroPresenter.cs` (202 L) contains NO account/billing logic.** It only wires
`ConnectHeroView` ⇄ `HomeViewModel` (connect state, speeds, uptime, active server identity) —
`HomeHeroPresenter.cs:40-124`. Nothing to port for the Account tab. The account element on Home
is the separate `HomeAccountChip`.

**`SubscriptionMetaView` is NOT an account surface.** It is the header of a *server group* in
`ServerListView`, driven by the engine's `SubItem` (subscription-userinfo header), not by the
Departament API — `SubscriptionMetaView.axaml.cs:299-383`. It matters here only because the
account carousel **reuses its traffic-pill renderer and byte formatter verbatim** (see §5.5).

---

## 1. Shell wiring (MainWindow) — how the Account tab is hosted

`Views/MainWindow.axaml.cs`:

- `AccountView` is a **long-lived singleton field**, not recreated per navigation — line 21:
  `private readonly AccountView _accountView = new AccountView();`
- **ONE `AccountViewModel` for the whole app**, shared between the Account tab and the Login
  sub-page, so login state propagates without plumbing — lines 71-75.
- Nav rail item `navAccount` → `ShowTab(AppTab.Account)` (line 177); the compact layout's
  `HomeAccountChip` raises `AccountRequested` → same tab (line 179).
- Account tab is nav index **2** (Home 0 ▸ Settings 1 ▸ Account 2) — line 390; slide direction is
  the index delta (line 372).
- The view raises four navigation events, the host translates them — lines 249-252:
  `BuyRequested → OpenBuy()`, `DevicesRequested → OpenDevices()`,
  `HistoryRequested → OpenHistory()`, `LoginRequested → OpenLogin()`.
- Sub-page stack (`Buy / Login / Devices / History` + all Settings sub-pages) lives in one
  `subPageHost`; push = translateX 16→0 + fade 0→1, 300 ms `Ease.OutQuint`; pop = translateX 0→16 +
  fade 1→0, 200 ms `Ease.Standard` — `MainWindow.axaml.cs:1120-1186`. **No separate OS windows.**
- Three-way shell overlay gate — `MainWindow.axaml.cs:849-898`: `accountSyncView` (when
  `IsImportingAccount ∥ IsStartupLoading`) / `onboardingView` (logged-out or empty) / `bodyRoot`.
- Deep-link callback `HandleAuthCallback(code)` (`departamentvpn://auth?code=…`) brings the window
  forward, ensures LoginView is up, then `_accountVm.CompleteAppHandoff(code)` — lines 1081-1094.
- `OpenLogin()` cancels the Telegram poll on back **only when not logged in** — lines 1216-1232.
- Account tab is **excluded** from the shell's per-tab entrance fade because it plays its own
  internal stagger — lines 608-621.

---

## 2. Zone 1 — the two-zone HERO

`AccountView.axaml:253-479`. One `Border.Card` (`Padding=16`), two zones separated by **one 1 px
hairline** (`Brush.OutlineVariant`), plus an optional third hairline + referral row.

### 2.1 Zone A — identity (lines 257-293)

```
[ Avatar 48×48, Border.Avatar (circle, Brush.Tile.Blue 20% ) ]  ← 12 →  [ Name (Headline) ]
[   monogram, Grotesk 20 Bold, Brush.Accent                 ]          [ Tariff caption (Caption) ]
```

- Monogram = first letter of the display name, uppercased, `@` stripped —
  `AccountViewModel.cs:2604-2608` (`Monogram`).
- Display-name precedence, shared with the Home chip so the two identities can never drift:
  `@telegramUsername` → `telegramName` → `email` — `Account/AccountSession.cs:68-83`
  (`DisplayNameFor`), consumed at `AccountViewModel.cs:2037`.
- Name uses `Classes="Headline"` (24 sp Bold, tracking −0.24) — deliberately promoted from Title,
  see comment `AccountView.axaml:279`.
- **Tariff caption**, sentence-case, on the identity line — NOT a chip fighting the name
  (`AccountView.axaml:285-291`). Resolution order — `AccountViewModel.cs:2109-2124`:
  1. `Account_TariffCaption` = `"Тариф · {0}"` when a tariff badge resolved;
  2. else `Account_TrialPeriod` = `"Пробный период"` when `sub.IsTrial`;
  3. else hidden.
- Badge resolution chain — `AccountViewModel.cs:2084`:
  `TariffNameFor(sub.TariffId)` → `TariffNameForPriceOptionId(sub.TariffPriceOptionId)` →
  `sub.TariffBadgeName()`. The last one deliberately **excludes** the raw remnawave
  `productName`, which goes stale after an upgrade — `Dto/SubscriptionDtos.cs:58-78`.
  Generic service names (`"departament vpn"`, `"departament"`) yield `null` → badge hidden.
- **The tariff word appears exactly once on the whole tab** (here). The carousel cards deliberately
  omit it — `AccountView.axaml:542-544`.

### 2.2 Zone B — money + top-up (lines 302-427)

```
Баланс                        (Caption 12, muted)
1 490  ₽              [ + Пополнить ]     ← baselines bottom-aligned
^Display 34 Bold  ^Headline 24 muted, margin 6,0,0,4
```

**Money typesetting rules (this is the crux of the design):**

- The amount and the currency symbol are **two separate strings**, so the ₽ can be stepped down:
  `BalanceAmountText` (Display 34 Bold, `-0.7` tracking) + `BalanceCurrencyText`
  (Headline 24, `Brush.OnSurfaceVariant`) — `AccountView.axaml:316-333`,
  produced at `AccountViewModel.cs:2041-2042`.
- `FormatMoneyAmount(double)` — whole amounts drop decimals, fractional use `0.00` invariant —
  `AccountViewModel.cs:2539-2541`.
- `CurrencySymbol(string)` — **RUB-only product**: `RUB / blank / USD / unknown → ₽`; only
  `EUR → €`, `KZT → ₸`, `UAH → ₴` keep their own sign — `AccountViewModel.cs:2545-2551`.
  Identical mapping is duplicated in `PaymentHistoryViewModel.cs:249-255` so history and balance
  can never show different signs for the same backend money. (`BuyViewModel.cs:713-721` differs:
  it also maps `USD → $` and falls back to the raw code.)
- `FontFamily={Font.Numeric}` + `FontFeatures="tnum,lnum,zero"` on **every** number (tabular,
  lining, slashed zero) so digits never jump during a live update — token rationale at
  `Assets/GlobalResources.axaml:168-180`.
- Display is used **exactly once on the tab** — on the balance (comment `AccountView.axaml:309`).
- `HasBalance` gates the whole money zone; it is `true` for any logged-in profile
  (`AccountViewModel.cs:2043`) — a 0 balance still renders "0 ₽", it is not hidden.
- **Balance-change crossfade**: when `BalanceAmountText` *actually* changes (`.Skip(1)` on the
  observable), the amount settles from above — opacity 0.25→1 + translateY −6→0, `Motion.Dur.State`
  (220 ms), `Ease.Standard` — `AccountView.axaml.cs:118`, `AccountView.axaml.cs:214-248`.
  Suppressed when the tab is not `IsHitTestVisible` (i.e. not the active tab) and under lite.

**Top-up flyout** (`AccountView.axaml:358-425`), `Placement=Bottom`, `IncyFlyoutTheme`, width 264,
spacing 12:

1. Title `Account_TopUpTitle` = "Пополнение баланса".
2. Caption `Account_TopUpHint` = "Введите сумму в рублях — откроется страница оплаты."
3. `TextBox` watermark `Account_AmountRub` = "Сумма, ₽", numeric font + tnum;
   **Enter key-binding → `TopUpCmd`** (line 379-381).
4. Inline error (`Brush.RedText`), visible on `HasTopUpError` — the flyout **stays open** on an
   invalid amount so the failure is never silent (`AccountViewModel.cs:1343-1352`).
5. Payment-method chooser: `Button.MethodChip` chips in a `WrapPanel`, shown **only when ≥2
   methods** (`ShowTopUpMethods`); with exactly one method a caption
   `Account_TopUpVia` = "Оплата · {0}" names it instead — `AccountViewModel.cs:740-766`.
6. `Account_Continue` = "Продолжить", `IsEnabled={CanTopUp}`.

Chip visual spec — `AccountView.axaml:169-208`: rest = transparent + 1.5 px `Brush.OutlineVariant`
+ muted text, height 40, `MaxWidth 220`, `Radius.Chip 12`; selected = `Brush.SelectedFill`
(accent @ 12 %) + accent border + accent text; press `scale(0.97)` from centre, 120 ms
`Ease.OutQuart`; hover on unselected = `Brush.Hover`.

**Top-up mechanics** — `AccountViewModel.cs:1341-1399`:
- Amount parsed with invariant **and** current culture ("1490" and "1 490,00") —
  `TryParseAmount`, lines 779-789.
- `CanTopUp` requires a positive amount **AND** a usable (numeric-coded) method —
  `ValidateTopUpAmount` lines 795-807. Gating on the method is what stopped the "nothing happens"
  bug (comment lines 791-794).
- A top-up is always a **Platega checkout**, never a balance payment (that would be circular) —
  `_repo.Buy(new PaymentRequestDto { Amount, Currency="RUB", PaymentMethod })` line 1366.
- On success: open URL in the system browser, snack `Common_CompletePaymentInBrowser`, clear the
  amount, raise `TopUpCheckoutOpened` (view hides the flyout — `AccountView.axaml.cs:121`), then
  **`SchedulePostTopUpBalanceRefresh()`**: 12 polls × 5 s of `RefreshProfile()`, bailing the moment
  `Profile.Balance` differs from the starting value — lines 1407-1433.
- `TopUpButton` hover/press are pinned on the ContentPresenter (`#3D7EF0` / `#3877E0`) because the
  Semi theme would otherwise substitute a *lighter* blue — `AccountView.axaml:62-69`.

### 2.3 Zone C — referral row (lines 429-477)

Third quiet hero row under a second hairline, so identity + money + friend-code read as ONE object.
Visible only when `HasReferral`.

```
Код друга   [ REF-97F7CBFB ]                                [copy icon 40]
Caption     chip: SurfaceVariant, Radius.Chip, pad 12,8,    IconButton40
            Caption+Numeric, MaxWidth 180, ellipsis
```

- Copy: `AccountView.axaml.cs:129-138` → clipboard + snack `Common_Copied` = "Скопировано".
- Only the copy button has a hover response; the code chip itself does not — style at
  `AccountView.axaml:55-60`, `76-81`.
- `Account_ReferralBenefit` = "Код друга"; `ReferralCode` is the raw code (e.g. `REF-97F7CBFB`)
  from `profile.referralCode` — `AccountViewModel.cs:2044-2046`.

---

## 3. Zone 2 — the subscription state machine + carousel

`AccountView.axaml:484-1051`. Four **mutually exclusive** panels, each wrapped in
`Panel.subreveal` with a 220 ms `Ease.OutQuart` fade-in on `IsVisible=True`
(`AccountView.axaml:151-165`; the selector `:is(Window):not(.lite)` means lite = instant).
All four sit at `Margin="0,24,0,0"` — `Space.24` separates "you" (hero) from "what you have".

### 3.1 State machine — `AccountViewModel.Recompute()` lines 2159-2200

```
if (!IsLoggedIn)                       → ShowLoginCta            (all four off, early return)
else if (Subscriptions.Count > 0)      → ShowActiveSub
else if (coldLoading && Profile==null) → ShowSkeleton            coldLoading = _pendingFirstLoad || IsLoading
else if (Error != null && !_hasSubData)→ ShowError
else                                   → ShowEmpty
```

Two hard-won invariants documented in the source:
- `_pendingFirstLoad` gates the skeleton so a genuinely-empty account resolves to *empty*, not an
  eternal pulse (`AccountViewModel.cs:39-41`).
- The **error vs empty** split keys off `_hasSubData` (a *successfully loaded* list), **not** on
  `Profile == null`. A cached profile restored at cold start used to mask this and showed
  "у вас нет подписки" to an offline returning user — comment lines 2184-2190 (Russian).

### 3.2 Skeleton (lines 488-520)

`Border.Card SkeletonPulse`, `MinHeight 208`, spacing 12:
row of `SkelBar w=180` + chip placeholder 72×24 (`Radius.Chip`), then `SkelBar w=220`, then
**two** 160×14 `Radius.Traffic` bars mirroring the traffic + device pills so skeleton→loaded has no
CLS jump. Pulse = opacity 0.45↔1.0, 900 ms, `SineEaseInOut`, alternate, infinite —
`Assets/GlobalStyles.axaml:1285-1300`; the selector is `:not(.lite)`-gated so reduced motion is
static (Avalonia cannot cancel a running animation from a competing style).

### 3.3 Carousel mechanics — `AccountView.axaml.cs:250-523`

Geometry constants (lines 34-37):
```
CardPeek      = 32   // px of the neighbour card left visible = "there is more" affordance
CardGap       = 12   // horizontal StackPanel Spacing
MinCardWidth  = 240
DragThreshold = 6    // px before a tap becomes a drag
```
- `ComputeCardWidth()` (275-303): 1 subscription → card = full viewport (no dead margin);
  2+ → `max(240, viewport − 32)`.
- Host: horizontal `ScrollViewer x:Name=SubCarousel`, scrollbar hidden, `Focusable=True`,
  `ItemsControl` with a horizontal `StackPanel Spacing=12` — `AccountView.axaml:527-537`.
- Drag: pointer handlers registered with `RoutingStrategies.Tunnel, handledEventsToo: true` so the
  gesture is seen **over** the card's own buttons (line 74-76). Past the 6 px threshold the pointer
  is captured and the button tap is cancelled (line 404-412).
- Snap: `SnapToNearest()` rounds `Offset.X / (cardWidth + 12)`; the tween is a **manual
  `DispatcherTimer` at 16 ms** because `ScrollViewer.Offset` does not accept a `Transition` —
  `Motion.Dur.Reveal` 300 ms with `Ease.OutQuint`, no overshoot (lines 474-508).
- Pager: dots are built imperatively (`Ellipse.Dot`, `.active`) only when `count > 1`
  (lines 305-321); `Ellipse.Dot` = 6 px `Brush.OutlineVariant`, `.active` = 8 px `Brush.Accent`,
  gap `Dot.Gap` 8 — `Assets/GlobalStyles.axaml:733-742`. Arrows `CarouselPrev/Next` are
  `IconButton40 Row` and page ±1.
- Keyboard: ←/→ page (lines 363-379). Dots are clickable.
- The snap tween is cancelled when the tab loses `IsHitTestVisible` so no timer runs off-screen —
  `AccountView.axaml.cs:170-176`.

### 3.4 One carousel card — `AccountView.axaml:540-954`, built by `BuildCard()` `AccountViewModel.cs:2212-2400`

Card = `Border.Card`, `Width={Binding CardWidth}`, `MinHeight 208`. Four stacked blocks:

**(a) Header** (545-804) — `Grid ColumnDefinitions="*,Auto,Auto"`:
- Title (`Classes=Title`, ellipsis): user rename → else `Account_YourSubscription` = "Ваша
  подписка" for index 0 → else `Account_SubscriptionN` = "Подписка {N}" —
  `AccountViewModel.cs:2215-2217`.
- **Health chip** `Border.StatusChip` with `.paid` / `.pending` / `.failed` classes bound to
  `IsHealthActive / IsHealthExpiring / IsHealthExpired`. Chip spec: `Radius.Chip 12`, padding 8,2,
  text = full colour, background = same colour @ 18 % —
  `Assets/GlobalStyles.axaml:1106-1137`. Labels:
  `Account_HealthActive` "Активна" / `Account_HealthExpiring` "Истекает" / `Account_HealthExpired`
  "Истекла".
- Kebab `Account_More` = "Ещё" (`IconButton40`, 20 px glyph) visible on `ShowMore`; opens the
  **four-panel overflow flyout** (see §3.5).

**(b) Meters block** (806-880), `StackPanel.Meters`, `Spacing=12`, `Margin 0,16,0,0`;
`Classes.dim` → `Opacity 0.5` when expired, so the eye goes to «Продлить»
(`AccountView.axaml:135-139`):

1. **Expiry line** — `Subtitle` + `Numeric`, colour carries urgency *in addition to* the copy
   (colour-blind safe): `.urgent` → `Brush.Icon.Orange`, `.expired` → `Brush.RedText`
   (`AccountView.axaml:117-123`). Copy from `ResolveHealth()` `AccountViewModel.cs:2486-2510`:
   - perpetual → `Account_Perpetual` "Бессрочно", health = Active;
   - `days < 0` → `Account_ExpiredOnDate` "Истекла {dd.MM.yyyy}", health = Expired, urgent;
   - `days ≤ 7` → `Account_ExpiresInDays` "Осталось {n} дн.", health = Expiring, urgent
     (`n = max(1, ceil(days))`);
   - else → `Account_ActiveUntil` "Активна до {dd.MM.yyyy}", health = Active;
   - unparseable → shown verbatim as Active (never invent urgency).
   - `IsEffectivelyPerpetual` (lines 2471-2483): blank ⇒ perpetual; year ≥ 2099 **or** > 3650 days
     out ⇒ perpetual (the backend sends `2099-06-04` as a "forever" sentinel — do NOT print it).
2. **Traffic pill** — `Border.TrafficPill` track (160×16, `Radius.Traffic 8`,
   `Brush.SurfaceVariant`, `ClipToBounds`) with a `.Fill.UsageFill` inner border whose `Width` =
   `160 × clamp(used/limit, 0, 1)`; label to the right, `Caption Numeric`.
   Unlimited → `Account_TrafficUnlimited` = "{used} · безлимит", fill width 0.
   Used bytes = `raw.TrafficUsed ?? raw.UserTraffic.UsedTrafficBytes ?? 0`
   (`AccountViewModel.cs:2232`). A non-null limit of **0** is guarded so it does not produce
   NaN width (lines 2243-2247).
3. **Devices row** — the WHOLE row is a tap target (`Button.MeterRow`, `MinHeight 48`, transparent,
   press `scale(0.99)`, chevron on the right) leading to the Devices screen — not an orphaned blue
   link (`AccountView.axaml:210-234`, `849-879`). Content:
   - unlimited (`raw.IsUnlimitedDevices() || TotalDevices <= 0`) → `Account_DevicesUnlimited`
     "Безлимит устройств", **no bar**;
   - index 0 (the only card whose live connected count is known) → `Account_DevicesShort`
     "{used} / {total} устройств" + a usage bar of the same 160 px geometry;
   - secondaries → `Account_DevicesTotal` "{total} устройств", no bar.
   (`AccountViewModel.cs:2250-2270`.)
   Both pills carry `Classes="UsageFill"` → a shared 300 ms `Ease.OutQuint` width grow-in on first
   paint (`AccountView.axaml:126-133`).

**(c) Renew CTA** (882-932) — ONE full-width accent button, the card's single primary:
- Label `Account_Renew` = "Продлить"; in-slot spinner (`Ellipse.Spinner.spinning`, 18 px,
  `StrokeDashArray="6.3,18.8"`, explicit `RotateTransform Center 9,9`) while `IsRenewing`.
- `Classes.Primary={RenewTopPrimary}` / `Classes.Tonal={!RenewTopPrimary}`.
  `RenewPrimary = health != Active` (`AccountViewModel.cs:2349`) — the CTA is only *filled* when
  the plan is expiring/expired. `RenewTopPrimary = RenewPrimary && !RenewExpanded`
  (`AccountViewModel.cs:2721-2724`) — once the inline choice opens, the inner "Оплатить картой"
  owns the single accent, so two filled primaries are never stacked.
- Tap behaviour (`PrimaryRenewCmd`, `AccountViewModel.cs:2801-2811`):
  `CanRenew` (i.e. the sub carries a `tariffId`) → toggle the **inline** pay-method choice
  *inside the card*; otherwise → `RequestBuy()` → host opens Buy.
  The inline expansion deliberately replaces a flyout, which used to flip over the card + meters
  (comment lines 2704-2709).
- Inline choice (917-931): `Tonal` = `BalanceMethodLabel` = `Account_RenewFromBalance`
  "С баланса · {balance}" (built from the live profile balance, line 2279-2281) and
  `Primary` = `Account_RenewWithCard` "Оплатить картой".

**(d) Auto-renew row** (934-952) — visible on `ShowAutoRenew` (root scope, or a secondary with a
non-blank id — line 2287):
- `ToggleSwitch Theme="{ToggleSwitch.iOS}"`, two-way bound to `AutoRenewOn`.
  Switch spec — `Assets/GlobalResources.axaml:329-394`: 52×32 track, radius 16, off =
  `Brush.SurfaceHighest`, on = `Brush.Accent`, 26 px white knob with a 0.5 px `#22000000` hairline,
  knob travel `translateX(20px)` 220 ms `Ease.OutQuint`, track colour crossfade 220 ms
  `Ease.Standard`, press = knob **fill** squashed to `scale(0.9)` 90 ms `Ease.OutQuart`
  (the squash is on the ellipse, not on the translating panel, so the two transforms don't fight),
  disabled = 0.38.
- Caption (`Subtitle Numeric`, wraps) — `AccountViewModel.cs:2286-2312`:
  - ON + root + primary payload has a next charge → `Account_AutoRenewNext`
    "Продлится {dd.MM} — спишем {amount}";
  - ON + a known non-perpetual expiry → `Account_AutoRenewOnDate` "Продлится {dd.MM}";
  - ON otherwise → `Account_AutoRenewOn` "Автопродление включено";
  - OFF + expiring → `Account_AutoRenewNudge` "Включите автопродление, чтобы не прерывать";
  - OFF otherwise → `Account_AutoRenewOff` "Автопродление выключено".
  The "Продлится …" voice is kept even without an explicit next-charge so ON never flips between
  two different sentences (comment lines 2299-2302).
- **Arming guard**: the initial value is applied via `SetAutoRenewSilently()` *before* `Arm()`,
  so building a card never fires a spurious PATCH — `AccountViewModel.cs:2397-2398`, `2659-2661`,
  `2867-2877`.
- Failure reverts the toggle so the UI never lies — `AccountViewModel.cs:1806-1810`.

### 3.5 Overflow «Ещё» — four mutually exclusive flyout panels

`AccountView.axaml:576-802`, `Flyout Placement=BottomEdgeAlignedRight`, `IncyFlyoutTheme`,
`Panel Width=272`. `AccountSubCard.PanelMode { Menu, Devices, Upgrade, UpgradeConfirm }` drives
four bools — `AccountViewModel.cs:2849-2865`.

1. **Menu** (581-612): two `LinkAction` rows with 20 px glyphs —
   `Account_AddDevices` "Докупить устройства" (visible on `CanBuyDevices`) and
   `Account_UpgradeTariff` "Улучшить тариф" (visible on `HasUpgradeTargets`).
2. **Devices** (615-697): back chevron + title, then a **stepper** (`IconButton40` −, `Headline
   Numeric` count, `IconButton40` +) clamped to `[1 .. MaxExtraDevices]`
   (`AccountViewModel.cs:1579-1584`), then the estimate `Account_DeviceEstimate` = "≈ {money}",
   then the honest disclaimer `Account_EstimateNote` = "Примерная сумма — точную посчитаем при
   оплате", then `Tonal` "С баланса" / `Primary` "Картой", both disabled while `IsDeviceBusy`.
   **Client-side price formula** (there is no device quote endpoint) —
   `AccountViewModel.cs:2418-2431`:
   `pricePerExtraDevice × extras × (100 − tierDiscount)/100 × remainingDays/30`, with
   `tierDiscount = 0` because the volume tiers are not exposed on the tariff DTO ⇒ the estimate is
   an **upper bound**, hence the «≈».
   `CanBuyDevices = !IsTrial && maxExtra > 0 && pricePerExtra > 0 && remainingDays > 0 &&
   subscriptionId != ""` — lines 2322-2326.
3. **Upgrade** (700-753): list of `UpgradeTargetOption` as full-width `Tonal` buttons
   (name left, price right, muted). Eligibility — lines 2368-2388: catalog tariffs with
   `t.Price > currentPrice`, excluding the current tariff, only when `!IsTrial`,
   `remnawaveUuid != ""` **and `currentPrice > 0`** — otherwise an unknown current price would make
   every paid tariff (including cheaper ones) falsely qualify.
4. **UpgradeConfirm** (756-799): title `Account_UpgradeTo` "Улучшить до {name}", detail
   `Account_UpgradeQuote` "{amount} · +{days} дн." from the **live** `GET
   /client/subscriptions/upgrade-quote`, then `Tonal` "С баланса" / `Primary` "Картой".

### 3.6 Empty state (991-1020)

`Border.Card MinHeight 120`, centred, spacing 16:
`Border.EmptyIcon` (64×64, `Radius.Card 20`, `Brush.Tile.Blue`, 32 px accent glyph —
`Assets/GlobalStyles.axaml:1260-1274`) + `Account_FirstSub` "Оформи первую подписку" (Title,
centred) + `Account_NoSubHint` "Выбери тариф — оплата в рублях, подключение сразу." (Subtitle,
`MaxWidth 420`, wraps) + `Primary` `Account_PickPlan` "Выбрать тариф" → `BuyRequested`
(`AccountView.axaml.cs:65`). It **acts**, it does not merely describe.

### 3.7 Error state (1022-1051)

`Border.Card MinHeight 120`: `Border.Tile` (neutral) + 22 px alert glyph, `Common_CouldntLoad`
"Не удалось загрузить" (Title) + `ErrorText` (Subtitle, wraps), then `Primary` `Common_Retry`
"Повторить" → `RetryCmd`.
`ErrorText` mapping — `AccountViewModel.cs:2610-2618`:
`ServiceUnavailable` → "Сервис временно недоступен"; `NetworkError` → "Ошибка сети. Проверьте
подключение"; `Unauthorized` → "Требуется вход в аккаунт"; `RateLimited` → "Слишком много
запросов. Попробуйте позже"; `TimeoutError` → "Превышено время ожидания"; else → "Что-то пошло не
так".

---

## 4. Zone 3 — «Способы входа» (account linking) — `AccountView.axaml:1053-1270`

Section header `Account_LinkingTitle` "Способы входа" (`SectionHeader` = 16 Bold sentence-case,
NOT a tracked all-caps eyebrow), then ONE `Border.Card Padding=0 ClipToBounds` containing four
56 dp rows (`MinHeight={Size.Row}`, `Padding 16,0`) separated by **inset hairlines at
`Margin="72,0,0,0"`** (40 tile + 16 gutter + 16 gap).

| Row | Tile | Linked state | Unlinked state |
|---|---|---|---|
| **Telegram** (1063-1122) | `Tile Blue` + accent paper-plane | `@handle` (Caption, `MaxWidth 140`) + a **neutral** check glyph | `Account_LinkAction` "Привязать" → `LinkTelegramCmd`; while pending: `Border.CodePill` `Account_TgLinkCode` "Код: {code}" + `Account_OpenBot` "Открыть бота" |
| **Google** (1127-1170) | neutral `Tile` + monochrome G | email + check | disabled `Account_SoonAction` "Скоро" (desktop OAuth not in-app yet) |
| **Email и пароль** (1175-1238) | neutral `Tile` + envelope | email + check | `Account_AddAction` "Добавить" → flyout: title `Account_EmailLinkTitle` "Привязать почту", hint `Account_EmailLinkHint`, `TextBox`, `Primary` `Account_Send` "Отправить" |
| **Веб-кабинет** (1243-1267) | neutral `Tile` + globe | — | `Account_OpenAction` "Открыть" → `OpenWebCabinetCmd` (SSO handoff) |

Design rule worth preserving verbatim (comment `AccountView.axaml:1092-1094`): the "linked"
affirmation is a **quiet neutral check**, *not* a green chip — green stays the role of live
subscription status and must not proliferate across the linking rows (one-accent rule). The
identifier next to it already says "linked".

**Linking state derivation** — `AccountViewModel.cs:2048-2064`:
```
ShowLinking      = true (whenever a profile exists)
TelegramLinked   = profile.TelegramLinked;  TelegramLinkedId = "@" + username (trimmed of '@')
TelegramCanLink  = !TelegramLinked && !TelegramLinkPending
GoogleLinked     = profile.GoogleLinked;    GoogleLinkedId   = profile.Email
EmailLinked      = profile.HasPassword && profile.Email != ""; EmailLinkedId = profile.Email
```

**Telegram link flow** — `AccountViewModel.cs:1836-1904`:
`POST /client/link-telegram-request` → `{code, expiresAt, botUsername}` → show the code pill, open
`https://t.me/<bot>` (bot handle = response → publicConfig → `BackendConfig.BotUsername`), then
**poll `GET /me` 40 × 3 s** until `telegramLinked` flips → snack `Account_LinkDone` "Готово".

**Email link** — `AccountViewModel.cs:1907-1926`: client-side `@` check
(`Login_EmailInvalid` on failure) → `POST /client/link-email-request` → snack
`Account_EmailSent` "Письмо отправлено на {email}". Anti-enumeration: the backend replies the same
either way.

**Web cabinet SSO** — `AccountViewModel.cs:1929-1955`: `POST /client/auth/app-handoff` →
`{code}` → open `{siteUrl || publicAppUrl}/tg-login?code={code}` so the browser lands already
signed in.

---

## 5. Zone 4 — «Управление» + sign-out — `AccountView.axaml:1272-1397`

Section header `Common_Manage` "Управление". One `Border.Card Padding=0 ClipToBounds` (hover clips
to radius 20) with two 56 dp `Border.Row`s:

1. **`BuyRow`** — `Tile Blue` + accent glyph, title `Common_BuySubscription` "Купить подписку"
   **in `Brush.Accent`** — the single accent row of the card — plus chevron.
2. **`HistoryRow`** — neutral tile, title `Common_PaymentHistory` "История платежей",
   **trailing value = the latest payment date** (`Subtitle`, numeric font + tnum, `MaxWidth 120`,
   ellipsis) shown on `HasHistoryRowValue`, plus chevron.
   Value = `Payments.MaxBy(CreatedAt).CreatedAt` formatted `dd.MM.yyyy` —
   `AccountViewModel.cs:2146-2150`.

**Sign-out** is deliberately lifted OUT of the nav card into its own quiet row, 24 dp below
(`AccountView.axaml:1370-1397`): neutral tile + `Account_SignOut` "Выйти" in **red text only**
(`Brush.RedText`, style at line 113-115) — no red fill, no red tile. Tap → `LogoutCmd`
(`AccountView.axaml.cs:66`).

Row interaction spec: hover = `Brush.Hover` (a quiet *darken*, never the Semi light-blue —
`AccountView.axaml:73-75`); press = `scale(0.99)` from centre, 120 ms, class added in code-behind
(`AccountView.axaml.cs:142-155`); title is Grotesk **Medium** (not Bold), one line + ellipsis
(`AccountView.axaml:84-89`). Under `.lite` the transition is removed entirely (lines 105-109).

**Logout behaviour** — `AccountViewModel.cs:1289-1322` + `Account/AccountSession.cs:109-135`:
cancel every poll (telegram, register, top-up, renew, card-action, link) → `AccountSession.Wipe()`
which **stops the VPN core and force-disables the system proxy first** (so the user keeps internet)
→ removes the account-imported subscriptions + their servers (tracked by
`AuthTokenStore.ManagedGuids`; the user's own manual subs survive) → clears the token store →
`AccountCache.InvalidateAll()` → `RequestHomeServerRefresh()` so Home returns to onboarding instead
of showing a stale group.

---

## 6. Logged-out gate — `AccountView.axaml:1402-1471`

Centred `Border.Card MaxWidth 380`, spacing 20:
`Border.Avatar` 56 px with a 28 px accent Telegram glyph, then
`Account_SignInTitle` "Войди в departament" (Title, centred) +
`Account_SignInHint` "Через Telegram — быстро, без пароля. Или войди по почте на сайте."
(Subtitle, centred, wraps), then
`Primary Tall` (52 dp) `Common_SignInTelegram` "Войти через Telegram" → `LoginTelegramCmd`, and
`Tonal` `Common_SignInWebsite` "Войти через сайт" → raises `LoginRequested` (host opens LoginView).

The two surfaces (`ScrollViewer IsVisible={IsLoggedIn}` and this `Grid IsVisible={ShowLoginCta}`)
are the only two children of the root `Panel` — mutually exclusive.

---

## 7. Motion inventory (Account tab only)

| Moment | Spec | Where |
|---|---|---|
| Tab entrance, group 2 (subs + manage) | opacity 0→1 + translateY 8→0, `Dur.Reveal` 300 ms, **delay `Dur.Stagger` 40 ms**, `Ease.OutQuint`; ONE stagger step | `AccountView.axaml.cs:181-208` |
| Balance change | opacity 0.25→1 + translateY −6→0, `Dur.State` 220 ms, `Ease.Standard`; only on a REAL change, only while the tab is active | `AccountView.axaml.cs:214-248` |
| Sub-state crossfade (skeleton↔active↔empty↔error) | fade 220 ms `Ease.OutQuart` on `IsVisible=True` | `AccountView.axaml:151-165` |
| Usage-bar fill | `Width` transition 300 ms `Ease.OutQuint` (both pills) | `AccountView.axaml:126-133` |
| Carousel snap | manual 16 ms timer tween, 300 ms `Ease.OutQuint`, no bounce | `AccountView.axaml.cs:474-508` |
| Row press | `scale(0.99)` from 50 %,50 %, 120 ms | `AccountView.axaml:93-103` |
| Chip / button press | `scale(0.97)`, 120 ms `Ease.OutQuart` | `AccountView.axaml:199-201`, `GlobalStyles.axaml:386-407` |
| Skeleton pulse | opacity 0.45↔1.0, 900 ms `SineEaseInOut`, alternate ∞ | `GlobalStyles.axaml:1285-1300` |
| Renew spinner | `Ellipse.Spinner.spinning`, dash arc, explicit rotate centre | `AccountView.axaml:900-914` |
| Home chip arrival | fade + 8 px rise, `Dur.Reveal`, **after a 120 ms delay** so it lands *after* Home paints; one-shot per resolve | `HomeAccountChip.axaml.cs:136-166` |

Motion catalogue (C# mirror of the XAML tokens): `Common/Motion.cs:20-56`.
`MotionState.IsLite` (reduced motion / lite mode) is the single kill-switch; XAML uses the
`:is(Window):not(.lite)` selector idiom because Avalonia cannot cancel an already-running animation
from a competing style.

---

## 8. Design tokens the port must map (desktop → Android)

From `Assets/GlobalResources.axaml` / `GlobalStyles.axaml`:

- **Colour (dark)**: `Bg #0A0B0D`, `Surface #141619`, `SurfaceHigh #1A1D21`,
  `SurfaceVariant #1E2126`, `SurfaceHighest #20242B`, `OnSurface #F2F4F8`,
  `OnSurfaceVariant #9BA1AD`, `Outline #2A2E36`, `OutlineVariant #20242B`,
  `AccentContainer #17325C`, `OnAccentContainer #CFE0FF`, `Green #22C55E`, `Red #F04452`,
  **`RedText #FF6069`** (text-only, ≥4.5:1), `Tile.Neutral #20242B`,
  `Hover = #000000 @32 %` — lines 63-87.
- **Accent**: ONE blue `#4C8DFF`, `OnAccent #00183A`, hover `#3D7EF0`, pressed `#3877E0`
  (lines 39-51, `GlobalStyles.axaml:433-438`).
- **Tiles**: `Tile.Blue/Green/Orange/Purple/Red/Yellow` = base colour @ 20 % (lines 45-46, 226-231).
- **Status chips**: same colour @ 18 % background, full-colour text (lines 255-258).
- **Selected fill**: accent @ 12 % (`Brush.SelectedFill`, line 242).
- **Spacing scale**: 4 / 8 / 12 / 16 / 24 / 32; gutter 16; card padding 16 (lines 138-147).
- **Radii**: chip 12, tile 12, card 20, sheet 24,24,0,0, pill 100, search/field 14, traffic 8
  (lines 150-154, 284-285).
- **Sizes**: tile 40, glyph 22, row 56, dot 6 / active 8 / gap 8, traffic pill **160×16**,
  icon-button 40, sub-toolbar 56, tall CTA 52 (lines 157-163, 288, 297-299).
- **Type scale** (`GlobalStyles.axaml:272-327`):
  `Display 34 Bold −0.7` · `Headline 24 Bold −0.24` · `Title 16 Bold` · `TitleMedium 16 Medium` ·
  `Body 14` · `Subtitle 13 muted` · `Caption 12 muted` · `Chip 11 Medium` ·
  `SectionHeader 16 Bold` (sentence-case) · `Numeric` = Grotesk + `tnum,lnum,zero`.
- **Buttons**: `Primary` 48 h / Grotesk 15 Bold / accent, `Primary.Tall` 52 h,
  `Tonal` 48 h / SurfaceHighest / Medium, `Destructive` 48 h red, `LinkAction` 40 h accent text,
  `IconButton40` 40×40 circular transparent; all press `scale(0.97)`, disabled 0.38
  (`GlobalStyles.axaml:386-500`, `948-1000`).
- **Flyout**: `SurfaceHigh` + 1 px `OutlineVariant`, radius 20, padding 16,
  `BoxShadow 0 12 32 0 #66000000`, inner `ScrollViewer MaxHeight 480`
  (`GlobalStyles.axaml:29-56`).

---

## 9. The client-API layer (`v2rayN.Desktop/Account/**`)

### 9.1 Transport — `DepartamentApiClient.cs`

- Single static `HttpClient`, **25 s timeout** (line 30).
- `AuthMessageHandler` (lines 36-61) injects on EVERY request:
  `Accept: application/json`, `User-Agent: DepartamentVPN/1.0`,
  `Authorization: Bearer <jwt>`, **`X-HWID`**, **`x-device-os: windows`**, **`x-ver-os`**,
  **`x-device-model: <machine name>`** — the OS/model headers are what make the panel show a
  friendly device name and keep **one** device entry per machine.
- Error mapping (lines 418-430): `401 → Unauthorized(detail)`, **`403 → Server(403)` — explicitly
  NOT Unauthorized**, so a permission failure never wipes a live session; `404 → NotFound`,
  `410 → Gone`, `429 → RateLimited`, `502/503 → ServiceUnavailable`, else `Server(code)`.
- `SanitizeBody` (lines 436-452): drops any line containing `token` / `authorization` /
  `http(s)://`, caps at 300 chars → screenshot-safe payment diagnostics.
- Base URL `https://web.departament.site/api`; bot `departamentvpnbot` — `BackendConfig.cs:14-17`.

### 9.2 Endpoint map — `BackendConfig.cs:26-98`

```
Public     /public/config · /public/tariffs · /public/server-status
Auth       /client/auth/telegram-login-token · …/telegram-login-check · …/login · …/2fa-login
           …/google · …/me
           …/register · …/verify-email
           …/magic-link/request · …/magic-link/consume
           …/password-reset/request · …/password-reset/consume
           …/app-handoff · …/app-handoff/consume
Linking    /client/link-telegram-request · /client/link-email-request
           /client/set-password · /client/link-google
Subs       /client/subscription · /client/subscription/all · /client/subscription/qr
           /client/subscriptions/upgrade-quote · /client/subscriptions/upgrade
           PATCH /client/subscription/{scope}/{id}/name
           POST  /client/subscription/{scope}/{id}/add-devices
Devices    /client/devices · /client/devices/delete
Payments   /client/payments/platega · /client/payments/balance · /client/payments
           /client/payments/tariff/platega            ← scoped card renewal
Promo      /client/promo-code/check · …/activate · /client/trial · /client/referral-stats
Auto-renew PATCH /client/secondary-subscriptions/{id}/auto-renew
           PATCH /client/auto-renew                    ← ROOT (see §11.1)
```

### 9.3 Session / persistence

- `AccountSession` (145 L) — single source of truth, seeded from `AuthTokenStore`, raises
  `StateChanged`; `Wipe()` stops the core + clears sysproxy before deleting managed subs.
- `AccountRepository` (160 L) — wraps every call into `ApiResult<T>`; **only `RefreshProfile()`
  (i.e. `GET /me`) is allowed to wipe the session on 401** (lines 26-30, 59-80).
- `AccountCache` (89 L) — process-lifetime map, 1 h TTL, typed helpers for `devices:{uuid}` and
  `payments`; every read first checks `IsLoggedIn()` and drops the whole cache when logged out.
- `SubscriptionSyncManager` (203 L) — imports the account's subs into the engine and tracks the
  created guids so logout can remove exactly those.

### 9.4 Loading orchestration (`AccountViewModel`)

- `LoadAll()` = `RefreshProfile → LoadSubscriptions → LoadPublicConfig → LoadTariffs →
  LoadPayments` (lines 558-565).
- `FetchAndApplySubscriptions()` (597-635) fetches `/subscription/all` **and** `/subscription`,
  then `MergeSubscriptions()` (641-666): active/root first (synthesized/enriched from the primary
  payload by `BuildRootSub()` 669-694), then secondaries, deduped by non-blank id.
  Immediately kicks `LoadDevices(uuid)` for the active sub.
- `LoadDevices()` (822-849) is cache-first and **swallows failures** — the connected count is
  secondary and must never surface an error.
- Cold-start gate `IsStartupLoading` is raised **synchronously in the ctor**, before `IsLoggedIn`
  is first assigned (lines 322-333), so the shell can never paint the logged-out gate for a
  returning user. `StartupLoad()` runs on the thread pool (line 426) and is bounded by a **30 s
  watchdog** (lines 437-496): on timeout, if servers already exist on disk the gate simply drops;
  otherwise the actionable retry surface appears.
- Fresh-login gate `IsImportingAccount` is raised in the SAME UI tick as `IsLoggedIn`, before the
  first await (lines 1100-1113), so no empty onboarding frame flashes.
- `RunSyncPhases()` (1139-1172) advances the live stage caption:
  `Account_SyncStageAccount` "Проверяем аккаунт" → `Account_SyncSubtitle` "Загружаем подписки…" →
  `Account_SyncStageServers` "Обновляем серверы". On ANY exception it raises `SyncFailed` and
  **leaves the gate up** so the overlay swaps to a retry surface instead of a false hand-off.
- Live language switch re-derives every display string (lines 406-413).

### 9.5 Payment-confirmation polling (the "no in-app return" problem)

The backend confirms PAID **only by webhook**, so a returning browser proves nothing. Three
independent bounded polls exist:

| Poll | Cadence | Match criterion | Where |
|---|---|---|---|
| Scoped card renewal | 5 × 8 s of `GET /client/payments` | status ∈ paid-set **AND** (`orderId` match ∨ `paymentId` match) | `AccountViewModel.cs:1515-1563` |
| Card device-top-up / upgrade | 5 × 8 s, same criterion | same | `AccountViewModel.cs:1703-1768` |
| Buy screen | 5 × 8 s + profile refresh | same | `BuyViewModel.cs:515-573` |
| Post-top-up balance | 12 × 5 s of `GET /me`, bail on balance change | — | `AccountViewModel.cs:1407-1433` |

Paid statuses (case-insensitive): `paid, success, succeeded, completed, confirmed, done` —
`AccountViewModel.cs:73-76`, `BuyViewModel.cs:25-28`.
A superseded poll drops the *other* card's spinner but keeps the same card spinning
(`AccountViewModel.cs:1519-1525`, `1723-1730`).

---

## 10. The other billing surfaces

### 10.1 Payment history — `PaymentHistoryView.axaml` + `PaymentHistoryViewModel.cs`

- Seamless sub-toolbar: `Border.SubToolbar` + `Button.BackNav` chevron + `Headline`
  `Common_PaymentHistory`, aligned to the same `MaxWidth 560` / gutter 16 column as the content
  (lines 41-59).
- Four exclusive states — `Recompute()` `PaymentHistoryViewModel.cs:155-165`:
  `ShowList = hasRows`; `ShowError = !hasRows && Error != null`;
  `ShowLoading = !hasRows && Error == null && (IsLoading || !_loaded)`;
  `ShowEmpty = !hasRows && Error == null && !coldLoading`.
- **Cache-first ctor** (lines 79-96): a fresh (<1 h) cached list renders instantly and skips the
  network — a cached-but-empty list is a genuine empty state, not a reason to spin.
- Row (`item_payment` port, lines 76-135): `Tile Blue` 40 + 22 px accent history glyph ·
  description (Body, ellipsis) + date (Caption, numeric, `dd.MM.yyyy`) · right column = amount
  (Body **Bold** numeric tnum) over a `StatusChip`.
- Row title fallback chain: `Description → Kind → OrderId` (line 192).
- Status mapping (lines 216-232) — anything unmapped keeps its **raw** text on the neutral chip:
  - `paid/success/succeeded/completed/confirmed` → "Оплачено" (green)
  - `pending/processing/new/created/waiting/in_progress` → "В обработке" (orange)
  - `failed/error/declined/rejected` → "Ошибка" (red)
  - `canceled/cancelled/expired` → "Отменён" (yellow)
- Sorted newest-first by ordinal ISO-8601 string compare (line 181-185).
- Skeleton = **3** card silhouettes with the exact loaded geometry (lines 143-274).
- Empty (276-310): 64 px blue hero icon + `History_Empty` "Платежей пока нет" + `Primary`
  "Купить подписку" → `BuyRequested`.
- Error (312-348): same silhouette but a **neutral** tile + reason + `Tonal` "Повторить".
  **No buy CTA here** — an error is not "no payments yet".
- Error copy: network/timeout/unavailable → "Ошибка сети. Проверьте подключение";
  else `History_ErrLoad` "Не удалось загрузить историю платежей" (lines 168-174).

### 10.2 Devices — `DevicesView.axaml` + `DevicesViewModel.cs`

- **Five** exclusive slots: list / loading / empty / no-sub / error — `Recompute()` lines 408-441.
  `ShowSubtitle = list || loading` (the "Устройства, подключённые к вашей подписке" line is list
  chrome and would contradict "нет устройств").
- Toolbar shows the count next to the title when a real list is on screen (`HasCount`).
- **UUID resolution chain** (lines 163-232), documented as the thing that makes the list populate:
  caller-supplied → logged-in profile `remnawaveUuid` (no network) → **`GET /client/auth/me`**
  (the only authoritative source) → first `/subscription/all` item with a non-blank uuid →
  "Активная подписка не найдена". A transport failure while resolving identity is an **error**
  (retryable), not "no subscription"; `Unauthorized` falls through to no-sub.
- Re-resolves on `AccountSession.StateChanged` so opening Devices right after login populates
  (lines 304-333); loads are coalesced via `_loadInFlight` (lines 146-161).
- Row (`DeviceRow`, lines 483-572): name = `deviceModel → platform → "Неизвестное устройство"`;
  meta = `"{platform} · Активно: {dd.MM.yyyy}"` with graceful degradation; id line = `"ID: {hwid}"`.
  Platform → one of five tile glyphs; **`win` is tested AFTER apple** so `darwin` doesn't fall
  through to Windows (lines 515-536). The current machine's row is tinted and gets an
  «Это устройство» marker (`AuthTokenStore.DeviceId()` comparison, lines 283-293, 510-511).
  One inset divider between rows (`ShowDivider = i > 0`).
- Delete: in-view confirmation overlay (scrim + card, not an OS dialog), body
  `Devices_UnlinkBody` "Устройство «{0}» будет отключено от подписки."; ignored while in flight;
  on success the row is removed locally **and the cache is rewritten** so the Account tab's
  device counter stays true without a refetch (lines 348-385).

### 10.3 Buy — `BuyView.axaml` + `BuyViewModel.cs`

- States: skeleton / error / empty / content, plus a post-checkout pending hint and a terminal
  success state — `RenderState()` lines 263-282.
- Catalog is flattened across groups; **the group emoji is never rendered** (lines 246-257).
- Tariff card summary `Buy_DevicesTraffic` = "Устройства: {0} · Трафик: {1}" (∞ for unlimited).
- Options sorted by `SortOrder`; a tariff with no options synthesizes one from its own
  duration/price; a tariff with exactly one option preselects it (lines 785-793, 309-317).
- **Single source of truth for money**: `CurrentTotal = option.Price + extras ×
  pricePerExtraDevice` is both displayed as «Итого» and sent as the charged `Amount`, so the two
  can never drift (lines 374-390, 449-457).
- Method sheet: `С баланса — {balance}` row first (green), then the Platega methods; the balance
  row is only offered when a profile is loaded (lines 412-428).
- Payment error surfaces **inline** with the raw HTTP code + sanitized detail
  ("HTTP 402\n<detail>"), not a modal (lines 663-689).
- On confirmed purchase, `SetSuccess()` reruns the login-path steps itself — auto-import →
  `RefreshServers()` → the shared `AccountViewModel.RetryCmd` — because Buy owns a separate VM
  (lines 575-649).

### 10.4 Home account chip — `HomeAccountChip.axaml(.cs)`

Reusable 64 dp row shared by wide + compact Home. `Border.AccountChip` = `Surface` + 1 px
`OutlineVariant` + `Radius.Card`, hover = `SurfaceHigh` (a *darken*, not a white glow), press
`scale(0.99)` from centre. Content: 40 px `Avatar` with monogram · name (`Title`, ellipsis) +
`Home_ManageAccount` (Caption) · 18 px chevron.
Three states — `ApplyAccountState()` lines 66-110: logged-in + resolved → the filled row (plays a
one-shot arrival); logged-in but unresolved → **skeleton** (avatar circle + 120 px bar), *not
hit-testable and not focusable*; logged-out → hidden entirely (Home's onboarding owns the
signed-out CTA — no duplicate login affordance).
Keyboard-activatable (Enter/Space → `AccountRequested`, lines 196-205).

### 10.5 Post-login sync overlay — `AccountSyncView.axaml`

Full-screen on `Brush.HomeGradient`, centred 400-wide column, gutter 16. Loading column: 64 px ring
(static `OutlineVariant` track + spinning accent arc `StrokeDashArray="16.75,50.25"`) with the
brand shield glyph centred, `Account_SyncTitle` "Добавляем аккаунт" (Headline) and the **live stage
line** (Subtitle, single line, ellipsis, never reflows the column).
Error column (`SyncFailed`) crossfades **in place**: same ring silhouette *without* the arc + red
alert glyph, `Account_SyncErrorTitle` "Не удалось синхронизировать" +
`Account_SyncErrorHint` "Проверьте соединение и попробуйте снова." + `Primary` "Повторить" +
`Tonal` "Войти заново". The overlay stays up — a failed import yields an action, never an eternal
spinner.

---

## 11. Android delta — what must be added, and what needs NEW backend calls

The Android app **already has** the whole API/session layer
(`app/src/main/java/com/v2ray/ang/auth/**`, 1911 L, 12 files + 5 DTO files) and the screens
`AccountFragment` (691 L), `LoginActivity`, `BuyTariffActivity`, `DeviceManagementActivity`,
`PaymentHistoryActivity`, `PaymentMethodSheet`, `SubscriptionPagerAdapter`, plus layouts
`activity_account.xml` (560 L), `item_subscription_card.xml`, `item_payment.xml`, `item_device.xml`,
`sheet_payment_method.xml`. The desktop is explicitly documented as a **port of** the Android code
(e.g. `DepartamentApiClient.cs:9`, `AccountViewModel.cs:9`, `PaymentHistoryViewModel.cs:37-42`),
so the delta is the *newer* desktop work that never went back to Android.

### 11.1 Bugs in the existing Android API layer that the desktop already fixed

These are corrections, not features — fix them before porting UI on top.

1. **Root auto-renew hits a 404 route.**
   Android: `BackendConfig.kt:85` → `const val primaryAutoRenew = "/client/subscription/auto-renew"`.
   Desktop: `BackendConfig.cs:92-97` documents this as **bug #29** — the real route is
   **`/client/auto-renew`**.
2. **Auto-renew body uses the wrong key.**
   Android: `dto/SubscriptionDtos.kt:187-189` → `AutoRenewRequestDto(val autoRenew: Boolean)`.
   Desktop forces the wire key to **`enabled`** (`Dto/SubscriptionDtos.cs:270-286`) because *both*
   auto-renew routes read `req.body.enabled`; the `autoRenew` key is silently ignored ⇒ the toggle
   currently does nothing on Android.
3. **`isUnlimitedTraffic()` mis-detects a concrete 0 limit.**
   Android: `dto/SubscriptionDtos.kt:158` → `trafficLimitBytes == null`.
   Desktop: `Dto/SubscriptionDtos.cs:202-208` → `is null or <= 0`, with a comment that a real 0
   produced the "used / 0 Б" + empty-bar bug.
4. **Device headers are incomplete.** Android sends only `X-HWID`
   (`DepartamentApiClientImpl.kt:81`); desktop also sends `x-device-os`, `x-ver-os`,
   `x-device-model` (`DepartamentApiClient.cs:19-22, 53-57`), which is what gives the Devices list
   a friendly per-machine name.
5. **Logout does not stop the VPN.** Android `AccountSession.wipe()`
   (`auth/AccountSession.kt:55-59`) removes managed subs and clears the token but never stops the
   core / restores the proxy; desktop does both first (`Account/AccountSession.cs:109-135`).
   On Android this means logout can leave the tunnel running against subscriptions it just deleted.
6. **Offline returning user is told "you have no subscription".** Android
   `AccountFragment.kt:389-400` decides the hero state with
   `profile == null && error != null -> Hero.ERROR`. A cold start restores a **cached profile**
   from the token store before any network call, so `profile != null` and the branch falls through
   to `Hero.EMPTY`. Desktop fixed exactly this by keying on a *successfully loaded list* instead:
   `Error != null && !_hasSubData → error` (`AccountViewModel.cs:2183-2192`, with the Russian
   comment explaining the regression). Android already tracks `hasSubData`
   (`viewmodel/AccountViewModel.kt:46, 154, 413`) but it is **private and never exposed** — expose
   it and swap the condition.

### 11.2 Endpoints Android's client does NOT implement yet (**new backend calls required**)

Compare `auth/DepartamentApiClient.kt:27-67` (Android, 30 methods) against
`Account/IDepartamentApiClient.cs:11-78` (desktop, 41 methods). Missing on Android:

| # | Method | Endpoint | Needed for (Android surface) |
|---|---|---|---|
| 1 | `register(email, password, ref)` | `POST /client/auth/register` | in-app sign-up tab |
| 2 | `verifyEmail(token)` | `POST /client/auth/verify-email` | email-verification flow |
| 3 | `requestMagicLink(email)` | `POST /client/auth/magic-link/request` | «Войти по ссылке» |
| 4 | `consumeMagicLink(token, ref)` | `POST /client/auth/magic-link/consume` | magic-link deep link |
| 5 | `requestPasswordReset(email)` | `POST /client/auth/password-reset/request` | «Забыли пароль?» |
| 6 | `consumePasswordReset(token, pwd)` | `POST /client/auth/password-reset/consume` | reset deep link |
| 7 | `createAppHandoff()` | `POST /client/auth/app-handoff` | **«Веб-кабинет · Открыть»** linking row |
| 8 | `consumeAppHandoff(code)` | `POST /client/auth/app-handoff/consume` | browser→app SSO return |
| 9 | `requestLinkTelegram()` | `POST /client/link-telegram-request` | **Telegram row of «Способы входа»** |
| 10 | `requestLinkEmail(email)` | `POST /client/link-email-request` | **Email row of «Способы входа»** |
| 11 | `setPassword(newPassword)` | `POST /client/set-password` | set first password on a passwordless account |
| 12 | `linkGoogle(idToken)` | `POST /client/link-google` | **Google row** (Android *can* ship this — it has Play Services, unlike desktop, where the row is "Скоро") |
| 13 | `purchaseDevices(scope, id, extras, method, paymentMethod: Int?)` | `POST /client/subscription/{scope}/{id}/add-devices` | **card overflow → «Докупить устройства»**. Android's existing `addDevices` uses `paymentMethod: String?` and returns `PaymentInitDto`; the desktop variant sends an **Int** code (2..13, matching `addDevicesSchema`) and parses the **dual-shape** `AddDevicesResultDto` (`{ok,newDeviceLimit,newBalance}` for balance vs `{paymentUrl,…}` for platega) — `Dto/SubscriptionDtos.cs:288-334` |
| 14 | `payTariffPlatega(req)` | `POST /client/payments/tariff/platega` | **scoped card renewal of a chosen sub** («Продлить → Оплатить картой») |

### 11.3 DTO fields Android is missing (same endpoints, unread fields)

- `UserProfileDto`: **`googleLinked`, `appleLinked`, `hasPassword`** — desktop
  `Dto/AuthDtos.cs:324-329`; Android `dto/AuthDtos.kt:94-125` has none of them.
  **These three drive the entire linking section** and are already returned by
  `/client/auth/me` (`toClientShape`), so **no backend change is needed** — only DTO fields.
- `PaymentRequestDto`: **`extraDevices`, `scope`, `subscriptionId`** — desktop
  `Dto/PaymentDtos.cs:20-35`; Android `dto/PaymentDtos.kt:15-24` lacks them. Required for the
  scoped renewal (`scope` + `subscriptionId` are mandatory together on
  `/payments/tariff/platega`).
- `PaymentResultDto`: `message`, `paymentId`, `newBalance` (desktop `Dto/PaymentDtos.cs:49-58`).
- `PrimarySubscriptionDto`: Android **already has** `autoRenewNextChargeAmount / At / Currency`
  (`dto/SubscriptionDtos.kt:94-96`) — it just isn't rendered.
- `RawSubDto`: Android already has `trafficUsed` / `userTraffic` — also not rendered.
- New DTOs to add: `RegisterRequest/ResponseDto`, `TokenRequestDto`, `EmailRequestDto`,
  `MagicLinkConsumeRequestDto`, `PasswordResetConsumeRequestDto`, `CodeRequestDto`,
  `SetPasswordRequestDto`, `LinkGoogleRequestDto`, `MessageResponseDto`,
  `LinkTelegramRequestDto`, `AppHandoffDto`, `AddDevicesPurchaseRequestDto`,
  `AddDevicesResultDto`, `RegisterResult` sealed type — all defined in
  `Account/Dto/AuthDtos.cs` and `Account/Dto/SubscriptionDtos.cs`.

### 11.4 UI features that need **no** new backend call (pure UI/VM work)

Everything below is already reachable with endpoints Android calls today:

- **Two-zone hero** with the hairline seam, `Headline` username, tariff caption, and the split
  money typesetting (Display amount + stepped-down muted ₽). Android currently renders one
  `tv_balance` string (`activity_account.xml:143`) and one `tv_username` (line 101) with no caption
  line and no hairline.
- **Top-up method chips + inline validation.** Android already opens a top-up dialog +
  `PaymentMethodSheet` (`AccountFragment.kt:518-560`) but has no inline error / no chip row /
  no "one method → caption" rule and no `CanTopUp` gate.
- **Sub card**: health chip, expiry urgency colours, traffic pill, device meter row with chevron,
  renew CTA + inline pay-method expansion, auto-renew toggle + next-charge caption.
  `item_subscription_card.xml` currently has only `tv_sub_name`, `tv_tariff_badge`,
  `tv_sub_expiry`, `tv_sub_devices` (4 views).
- **Sign-out row.** `AccountViewModel.logout()` exists on Android
  (`viewmodel/AccountViewModel.kt:400-418`) but **no view calls it** — there is no sign-out
  affordance anywhere in `activity_account.xml` or `AccountFragment.kt`.
- **History row trailing value** (latest payment date) — Android has `tv_row_value_history`
  (line 539) and `renderHistoryValue` (`AccountFragment.kt:439`); verify it matches
  `AccountViewModel.cs:2146-2150`.
- **Error-vs-empty discrimination via `hasSubData`** — see §11.1 item 6; the fix is UI/VM-only.
- **Perpetual-expiry sentinel** (year ≥ 2099 / >10 y → "Бессрочно") — pure formatting.
- **Post-top-up balance re-poll** — uses `GET /me`, already available.
- **Payment-confirmation polling** for renewals/upgrades — uses `GET /client/payments`, already
  available.
- **Devices: `GET /client/auth/me` as the authoritative uuid step** — already available; verify
  `DeviceManagementActivity.kt` implements the full four-step chain from §10.2.
- **Payment-history: cache-first ctor + 4 states + status chip palette** — verify
  `PaymentHistoryActivity.kt` (164 L) against `PaymentHistoryViewModel.cs`.

### 11.5 Things the desktop does that Android should NOT copy verbatim

- `Google` linking row reads **«Скоро» / disabled** on desktop only because desktop has no OAuth
  client (`AccountView.axaml:1163-1167`). Android has Play Services and *should* wire
  `linkGoogle(idToken)` for real.
- Desktop opens every checkout in an **external browser** and then polls, because it has no
  in-process web view. Android may keep its existing checkout presentation, but the poll semantics
  (5 × 8 s, orderId/paymentId match against the paid-status set) must be preserved either way.
- The **carousel drag/snap** is hand-rolled on desktop because `ScrollViewer.Offset` isn't
  animatable; Android already uses `ViewPager2` + `MarginPageTransformer`
  (`AccountFragment.kt:136-163`) — keep the ViewPager, port only the geometry
  (peek 32 dp, gap 12 dp, min card 240 dp) and the dot spec (6/8 dp, gap 8).
- `x-device-os: "windows"` is hard-coded on desktop (`DepartamentApiClient.cs:55`); Android must
  send `"android"`.

### 11.6 Copy to port

The complete Russian/English string table for these surfaces is `Common/L.Account.cs:16-225`
(Account_*, Login_*, Onboarding_*) and `Common/L.Buy.cs:17-66` (Buy_*, Devices_*, History_*),
plus the shared `Common/L.Common.cs:14-64` keys listed inline throughout this document. Voice:
Russian, sentence-case, active verbs, no ALL-CAPS eyebrows, «≈» wherever a number is an estimate.

---

## 12. Non-obvious invariants worth carrying over verbatim

1. **The tariff word appears exactly once** (hero identity line); cards never repeat it.
2. **Exactly one `Display` type on the tab** — the balance amount.
3. **One accent** — «Пополнить» in the hero, «Купить подписку» in Manage, and the ONE primary CTA
   per card. `RenewTopPrimary` exists purely to prevent two stacked filled primaries.
4. **Green is reserved for live subscription status**; linking rows use a neutral check.
5. **Red is text-only for destructive** (`Brush.RedText`), never a fill or a red tile.
6. **A 403 must never be treated as "session dead"** — only a 401 on `GET /me`.
7. **`_pendingFirstLoad` + `_hasSubData`** are what separate skeleton / empty / error honestly.
8. **`SetAutoRenewSilently` before `Arm()`** — building a card must not fire a PATCH.
9. **Estimates are labelled «≈»** and carry the "точную посчитаем при оплате" note, because the
   client cannot see the volume-discount tiers.
10. **Upgrade eligibility requires a known current price > 0**, otherwise every tariff falsely
    qualifies as an upgrade.
11. **A far-future expiry (≥2099 / >10 y) renders «Бессрочно»**, never the literal fake date; a
    blank expiry is also perpetual, but an *unparseable* one is shown verbatim as active.
12. **Money = amount string + currency string, never one pre-joined string** — the split is what
    makes the stepped-down ₽ possible.
