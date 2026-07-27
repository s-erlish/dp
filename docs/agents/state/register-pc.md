# Register — PC (desktop), everything still open

**Written:** 2026-07-26 · **Branch:** `claude/app-audit-agents-hyyftk`
**Code read:** `/home/user/v2rayN/v2rayN/**` (`ServiceLib`, `v2rayN.Desktop`), `/home/user/dp/V2rayNG/**`
for the Android reference. **No source file was edited, no build was run, no git command was issued.**

## What this document is

Every PC defect that is **still in the code**, verified by reading it. An item is here only if I
opened the file and found it. Everything is cited `file:line`.

Three things this document deliberately does:

- **It kills what was fixed.** Roughly a third of what the session's audits reported has since
  landed. Those rows are in §7 as one line each, so nobody refiles them and nobody "re-fixes" them.
- **It keeps refuted claims out of the work list.** §8 lists them by name.
- **It says when it does not know.** §9 holds the two items whose mechanism I could not establish
  from source alone, with the exact question that settles each.

**The owner's file `OWNER-FEEDBACK-2026-07-27.md` outranks every spec, and it outranks this file
too.** Where I found a spec-derived row that contradicts him, the row is his way round.

Ordering: §1–§4 are the four he raised directly, mechanism established. §5 is the rest of his list.
§6 is everything else open, by area.

---

## 0. Severity key

| | Meaning |
|---|---|
| **blocks-release** | The product does not do its job, or does damage |
| **high** | A user hits it in normal use and is misled, blocked, or loses work |
| **medium** | Visible wrongness, no data loss, a workaround exists |
| **low** | Craft, hygiene, or latent |

---

# Part I — the four the owner raised

## 1. «на пк версии не подключается к впн, не знаю в чем причина»

Section H of the feedback traces the tunnel gate and asks whoever picks this up to confirm or refute
it. **I traced it, and the traced mechanism is only half the answer. The half that is certain is not
the gate — it is that the app cannot tell him anything.**

### PC-01 — the "cannot tunnel" notice exists as strings and is rendered by nothing · **blocks-release**

This is the item that answers «не знаю в чем причина» literally: whatever the cause, **the app never
says a word.**

Verified, end to end:

- The strings exist: `v2rayN.Desktop/Common/L.Home.cs:55` `Home_TunUnavailable` = «Режим «весь
  трафик» недоступен без прав администратора», `:56` `Home_RestartElevated` = «Перезапустить с
  правами».
- The view-model state exists: `ServiceLib/ViewModels/StatusBarViewModel.cs:127`
  `TunRequestedButUnavailable`, recomputed at `:563`; the escape hatch exists at `:48`
  `RequestTunElevationCmd`, built at `:203`.
- **Nothing consumes any of it.** `grep -rn "Home_TunUnavailable\|Home_RestartElevated\|TunRequestedButUnavailable\|RequestTunElevation"`
  over `v2rayN.Desktop/**` returns the declarations, one comment, and no view.
  `grep -n "Tun" Views/ConnectHeroView.axaml` returns **zero matches** across all 839 lines.
- `Views/StatusBarView.axaml:16-25` documents the removal and the promise that was never kept:
  the banner was deleted from `StatusBarView` (invisible host), and «две ВИДИМЫЕ копии того же
  баннера были вставлены в HomeView и CompactHomeView. Теперь баннер ОДИН и живёт в
  ConnectHeroView». `HomeView.axaml` (76 lines) and `CompactHomeView.axaml` (72 lines) no longer
  carry it; `ConnectHeroView.axaml` never received it. Three copies were removed, zero were added.

So a session that requests the tunnel and cannot build one runs, reports itself connected, carries
no VPN traffic, and displays nothing. **A VPN that cannot tunnel must say so, on the screen with the
connect button** — the feedback's own closing requirement, and the code has the string ready.

**Fix:** render the banner once, inside `ConnectHeroView`, bound to
`StatusBarViewModel.TunRequestedButUnavailable`, with `RequestTunElevationCmd` as its action. Do not
re-add copies to `HomeView`/`CompactHomeView` — one banner, in the shared hero, is the correct call
that was already made.

### PC-02 — the gate itself: confirmed as written, but **it cannot fire on the shipping Windows build** · **medium**

The chain in the feedback is exactly right:

`StatusBarViewModel.cs:566-583` `AllowEnableTun()` → `Utils.IsAdministrator()` on Windows
(`ServiceLib/Common/Utils.cs:1262-1269`) → `:155` `TunUnavailable = !AllowEnableTun()` →
`ConfigItems.cs:203` `EnableTunEffective => EnableTun && !TunUnavailable` →
`Handler/Builder/CoreConfigContextBuilder.cs:45` `IsTunEnabled = …EnableTunEffective` and `:204`
(the Custom pre-socks branch). With `EnableTunEffective` false, `BuildPreSocksIfNeeded`
(`:186-215`) does **not** synthesise the sing-box pre-service that owns the tun adapter, and
`CoreConfigHandler.MergeAppInbounds` (`:165-188`) forces `IsTunEnabled = false` for the Xray
custom config by design. Net: a core with a local SOCKS inbound and no tunnel. The app starts,
reports running, carries nothing. **Mechanism: CONFIRMED.**

**But the trigger does not exist on Windows as shipped.** `v2rayN.Desktop/app.manifest:10` declares
`requestedExecutionLevel level="requireAdministrator"`, and `v2rayN.Desktop.csproj:17` references it
via `<ApplicationManifest>`. The manifest is embedded into the apphost, and the CI publish
(`.github/workflows/departament-branch-build.yml:30`,
`dotnet publish … -c Release -r win-x64 -p:SelfContained=true`) produces an apphost, so the process
is elevated or it does not run at all. On an elevated run `IsAdministrator()` is true and the gate
never closes.

Where the gate **does** close, today, unconditionally: **Linux and macOS.** `AllowEnableTun()`
returns `AppManager.Instance.LinuxSudoPwd.IsNotEmpty()` on both (`:572-579`), and `LinuxSudoPwd` is
necessarily empty when `StatusBarViewModel`'s constructor runs. So every Linux/macOS launch starts
with `TunUnavailable = true` and builds a tunnel-less config — with, per PC-01, no notice.

**Conclusion for whoever picks this up:** do not spend the day on elevation. Spend it on PC-01
(the app must say what it is doing) and on PC-03 (measure what it actually does). If the owner's
Windows box reproduces with a real UAC prompt accepted, the tunnel gate is not the cause and the
next suspects are, in order: the sing-box pre-service failing to start or to create the wintun
device, and the port contract between the pre-service and the Xray SOCKS inbound.

### PC-03 — `StatePort2` applies the TUN offset outside its own memo, so two readers can disagree by one · **high**

`ServiceLib/Manager/AppManager.cs:25-32`:

```csharp
_statePort2 ??= Utils.GetFreePort(GetLocalPort(EInboundProtocol.api2));
return _statePort2.Value + (_config.TunModeItem.EnableTunEffective ? 1 : 0);
```

The port is memoised once; **the `+1` is recomputed on every read.** `EnableTunEffective` is
session-scoped and changes when the mode row is used (`SettingsViewModel.SetModeAsync:432`) or when
elevation is granted (`StatusBarViewModel.cs:547`). A config generated before the change and a
reader after it address different ports. Symptom class: the app is up, the statistics/api channel
talks to a port nothing is listening on, the speed widget reads zero, and nothing errors. Memoise
the whole expression or drop the offset into `GetLocalPort`.

### PC-04 — the Xray `api` inbound is grafted onto every config for a feature that is hard-disabled · **medium**

`ServiceLib/Handler/CoreConfigHandler.cs:240` calls `GraftXrayApi(root)` unconditionally.
Its only consumer is the Tier-2 hot swap, and `ServiceLib/Manager/CoreManager.cs:55` reads
`private static readonly bool EnableHotSwapTier = false;`. The grafted `dokodemo-door` binds
`AppManager.ApiPort` (`AppManager.cs:41-47`), which comes from `Utils.GetFreePort` — a check, not a
reservation. If anything takes that port between the check and Xray's bind, **Xray fails to start
and the whole connect fails**, for a listener nothing uses. Gate `GraftXrayApi` and `GenApi` on
`EnableHotSwapTier`.

---

## 2. «на пк также кнопки все баганные, при наведении моргают» — one cause

### PC-05 — hover/press effects are applied to the element that owns the hit test · **high**

The repo already found this once and wrote the diagnosis down, for exactly one control.
`Views/MainWindow.axaml.cs`'s sibling markup, `Views/MainWindow.axaml:125-133`, on
`Button.RailToggle`:

> «Bug3 (мерцание на hover) устранено полностью: hover больше НЕ красит никакой фон — единственная
> реакция на ховер = ГЛИФ ТЕМНЕЕТ. Смена цвета глифа **не трогает границы элемента**, поэтому
> :pointerover у края НЕ «дребезжит» (**нет входа/выхода хит-теста → нет мигания**). Press-scale
> живёт на ВНУТРЕННЕЙ подложке (`Border.WinBtnBg`, фикс. 30×30), а не на самой кнопке — хит-область
> кнопки неизменна.»

and at `:157-159`: «Транзишен ТОЛЬКО на RenderTransform — **нет BrushTransition фона, который мог бы
осциллировать/мигать**».

Two named mechanisms, one fix, applied to **one** button. Every other button in the app still has
both shapes:

**(a) `RenderTransform` scale on the Button itself.** Census over `Views/*.axaml` + `Assets/*.axaml`:
`scale(0.97)` ×20 · `scale(0.92)` ×11 · `scale(0.94)` ×1 · `scale(0.96)` ×1 · `scale(0.9)` ×1.
Global sites: `Assets/GlobalStyles.axaml:615` (`Button.Primary:pressed`), `:697`
(`Button.PrimaryCompact:pressed`), `:746` (`Button.Tonal`/`Secondary:pressed`), `:949`
(`Border.ServerRow.pressed`), `:1113`, `:1194` (`Button.IconButton40:pressed`), `:1287`, `:1361`,
`:1407`, `:1811`, `:2019`, `:2090`; plus `scale(0.92)` redeclared verbatim in twelve view files.
Every one of them scales the control that is hit-testing, so a pointer near the edge falls outside,
the pseudo-class drops, the scale reverts, the pointer is inside again — the oscillation the
RailToggle comment names.

**(b) a `BrushTransition` on a background whose rest value the app never declares.**
`Assets/GlobalStyles.axaml:641-647` attaches
`<BrushTransition Property="Background" Duration="0:0:0.15"/>` to
`Button.Primary /template/ ContentPresenter#PART_ContentPresenter`, and `:759-765` does the same for
`Button.Tonal`/`Secondary`. The only values ever written to that presenter are the **hover** (`:652`)
and **pressed** (`:655`) ones — there is no rest-state setter, so the transition animates from a
value owned by the Semi theme, not by us. On pointer-exit the property falls back to that foreign
value **through the transition**.

`Button.PrimaryCompact` is the proof this is a bug and not a style: `:688-690` declares
`PART_ContentPresenter.Background = Brush.Accent` at rest. `Button.Primary` — **44 uses** — does not.

**Fix (one change, not fifty):** move press-scale onto an inner, fixed-size presentation layer on
every archetype, exactly as `Border.WinBtnBg` does; declare the rest-state presenter background
alongside every hover/pressed setter that touches it; and settle on one press scale (D-11 says
0.97).

### PC-06 — the destructive confirm reads disabled next to a solid cancel · **high**

Owner D3, «Удалить провайдера и его серверы?». The dialog is
`Views/MessageBoxDialog.axaml:55-75`: cancel is `Classes="Tonal"`, confirm is `Classes="Primary"`.
This is the same defect as PC-05(b) seen from the front: `Button.Primary` declares its accent fill
on the **Button** (`GlobalStyles.axaml:595`) and never on the presenter that paints it, so at rest
it inherits the theme's neutral, and only becomes accent-coloured under the pointer. The confirm
therefore reads as the quieter of the two until you hover it.

Separately and on top: a destructive confirm must not be the generic `Primary` accent at all.
`Border.Chip.Status`/`Button.Destructive` exist; the account tab already uses
`Classes="Destructive"` for its sign-out confirm (`Views/AccountView.axaml:1270`). Use it here, and
give the dialog the destructive verb instead of «Подтвердить» (`ResUI.TbConfirm`) — «Удалить».

### PC-07 — the connect disc is not operable without a mouse · **high**

`Views/ConnectHeroView.axaml` — `grep` for `Focusable`, `IsTabStop`, `KeyDown`,
`AutomationProperties` over all 839 lines returns **nothing**. `#ConnectDisc` is a `Border` with raw
pointer handlers (`ConnectHeroView.axaml.cs:251-256`). The single action the product exists to
perform has no keyboard path and no accessible name. (The right-button/middle-button bug on the same
control **is** fixed — `:694` now filters `IsLeftButtonPressed`.) Its press scale is also `0.94`,
one of five values in the app.

---

## 3. «шрифт какой-то толстый» + «у серверов везде кривой текст» — one cause

The owner's own guess in F4 is correct: *"a face resolving to something other than the intended
one"*. I read the font binaries. It is worse and more specific than that.

### PC-08 — the brand face is a variable font pinned to **Light 300**; every Bold role is fake-bold · **high**

`Assets/GlobalStyles.axaml:52` and `Assets/GlobalResources.axaml:301`/`:315` all resolve
`Font.Brand` / `Font.Grotesk` / `Font.Numeric` to a single file,
`Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk`. Parsed from the binary:

| | value |
|---|---|
| `fvar` | 1 axis, `wght`, min 300 · **default 300** · max 700, 4 named instances |
| `OS/2 usWeightClass` | **300** |
| name ID 1 | «Space Grotesk Light» |
| name ID 16 | «Space Grotesk» |

Avalonia loads an embedded font through `SKTypeface.FromStream`, which instantiates the **default**
instance. The named `fvar` instances are not exposed as separate faces and Avalonia has no
`fontVariationSettings` equivalent on a `FontFamily` URI. So the family «Space Grotesk» contains
exactly one face, at weight **300**, and:

- `TextBlock.Display` (34/**Bold**, `GlobalStyles.axaml:337-344`) and `TextBlock.Wordmark`
  (20/**Bold**, `:429-436`) request 700 against a 300 master → Skia applies **synthetic
  emboldening**. Synthetic bold on a Light master is the smeared, over-thick look the owner is
  describing.
- `TextBlock.Chip` (11/Medium, `:395-402`) and `TextBlock.Numeric` (Medium, `:407-412`) request 500;
  weight simulation exists only for bold, so they render at **Light 300** — noticeably thinner than
  everything around them.

One face, two wrong directions, on the same screen. That is «кривой шрифт местами».

**Android does not have this problem and shows the fix.** `V2rayNG/app/src/main/res/values/styles.xml:36`
states it outright: the Android ramp goes «through `@font/space_grotesk`, whose entries pin `wght` via
`fontVariationSettings`» — a family XML with a real 700 instance
(`res/font/space_grotesk.xml` + `spacegrotesk.ttf`).

**Fix:** ship **static** Space Grotesk instances (Regular 400 / Medium 500 / Bold 700) the way Golos
Text is already shipped, and reference the folder, not the single variable file. This is a
supply-the-file change, not a markup change.

### PC-09 — Space Grotesk has **no Cyrillic at all**; every role that carries Russian in it falls back per glyph · **high**

Verified against the `cmap`: `Я`, `б`, `и`, `ы` → glyph id **0** in `SpaceGrotesk.ttf`; all present in
all three Golos files. So any Russian string in `Font.Brand` renders in an undeclared OS fallback —
a different face, a different weight and, decisively for the clipping, **different metrics on the
same line**.

The blanket setter was already moved off the brand face (`GlobalStyles.axaml:304-316`, `Font.Ui`),
and `AccountView`'s three `FontFamily="{DynamicResource Font.Grotesk}"` sites are gone (verified —
`grep` over `Views/*.axaml` returns only two prose comments). What remains is the roles that keep
the brand face **by design** and can still receive Russian: `TextBlock.Chip` (`:395`) and
`TextBlock.Numeric` (`:407`) — a chip label or a unit suffix in Russian mixes faces mid-line.

### PC-10 — four type roles declare a `LineHeight` **below** the face's natural line height, so the ascenders clip · **high**

This is the metric the owner told us to fix rather than nudge. Natural line height, computed from the
shipped binaries (`hhea` ascender + descender + lineGap, unitsPerEm 1000):

- **Golos Text** — 980 + 220 + 0 = **1.200 em**
- **Space Grotesk** — 984 + 292 + 0 = **1.276 em** (`OS/2` win metrics are worse still: 1.442 em)

Against `Assets/GlobalStyles.axaml`:

| Role | Line | Face | Size | Needs | Declared | Verdict |
|---|---|---|---|---|---|---|
| `Display` | `:337-344` | Brand | 34 | **43.4** | 40 | **clips 3.4px** |
| `Wordmark` | `:429-436` | Brand | 20 | **25.5** | 24 | **clips 1.5px** |
| `Chip` | `:395-402` | Brand | 11 | **14.04** | 14 | **clips** |
| `Headline` | `:346-353` | Ui | 24 | **28.8** | 28 | **clips 0.8px** |
| `Title` | `:355-361` | Ui | 16 | 19.2 | 20 | fits — **by 0.8px** |
| `Body` / `Subtitle` / `Caption` | `:369`/`:377`/`:385` | Ui | 14/13/12 | 16.8/15.6/14.4 | 20/18/16 | fit |

Avalonia distributes the difference between the declared line height and the font's natural height as
**half-leading**. When the difference is negative, half of it comes off the **top** — which is
precisely «the tops of the capitals are cut off».

**And that is why the server names clip.** The server name is `Classes="Title"`
(`Views/ServerListView.axaml:260-263`), which fits by **0.8 px at 100% scale and by nothing at all
the moment one glyph falls back** — a Windows system face runs ~1.33 em, i.e. 21.3 px at 16 px
against a declared 20. Same list, same row: «Germany», «Latvia» and «LTE Белый интернет 1» clip
together, Latin and Cyrillic alike, exactly as the owner reported. It is metrics, not a glyph gap —
his read was right.

**Fix:** set every `LineHeight` from the face's real metric (a clean rule: ceil to the 4-px grid at
or above 1.30 em — Display 46, Headline 32, Title 22, Wordmark 28, Chip 16), and land PC-08/PC-09
so there is only one metric per line to satisfy.

### PC-11 — `Font.Ui` is declared as a **folder**, which sweeps a 10.5 MB Chinese font into the UI family collection · **low**

`Assets/GlobalStyles.axaml:51` — `avares://departament/Assets/Fonts#Golos Text`. The folder form
loads every font asset under `Assets/Fonts`, which is `GolosText-{Regular,Medium,Bold}.ttf`,
`SpaceGrotesk.ttf` **and `NotoSansSC-Regular.ttf` (10 560 616 bytes)**. All are embedded by
`v2rayN.Desktop.csproj:43` (`AvaloniaResource Include="Assets\**"`) and parsed at collection build.
Point the family at the three Golos files, or move Noto out of the folder.

---

## 4. «там есть фикс по шаблонам… на пк это ваще не пофикшено»

### PC-12 — the desktop still takes the **first** proxy outbound; Android walks the routing · **blocks-release**

Owner A3 and I, raised twice. Confirmed open, verbatim:

`ServiceLib/Handler/Fmt/XrayJsonTemplateFmt.cs:122-129`

```csharp
public static Outbounds4Ray? GetProxyOutbound(V2rayConfig? config)
{
    if (config?.outbounds is not { Count: > 0 }) { return null; }
    return config.outbounds.FirstOrDefault(o => o.protocol.IsNotEmpty() && _proxyProtocols.Contains(o.protocol));
}
```

Its own doc comment at `:118-120` claims it *"mirrors Android's `getProxyOutbound()`"*. That is no
longer true. Android is
`V2rayNG/app/src/main/java/com/v2ray/ang/dto/V2rayConfig.kt:520-580`:

```kotlin
fun getProxyOutbound(): OutboundBean? = resolveRoutedOutbound() ?: firstProxyOutbound()
```

`resolveRoutedOutbound()` walks `routing.rules` in core order, skips rules narrowed to a special case
(`matchesGenericTraffic()`), skips a rule that sends everything to freedom/blackhole (`:553`, "a
kill-switch or a bypass, not this profile's server"), and resolves balancer members by tag prefix
with a `fallbackTag` (`:559-567`). Tests: `app/src/test/java/com/v2ray/ang/dto/ProxyOutboundResolutionTest.kt`
(present).

**Five desktop call sites read the wrong outbound today:**

| Site | What it gets wrong |
|---|---|
| `ServiceLib/Services/SpeedtestService.cs:110` | the ping/tcping target host — **a host that is not the server** |
| `ServiceLib/Handler/CoreConfigHandler.cs:440` | `InjectCustomSpeedtestNodes` grafts the decoy into the batch speedtest config — **it measures the decoy** |
| `ServiceLib/ViewModels/ProfilesViewModel.cs:459` | the row's protocol/transport chip — the decoy's protocol |
| `ServiceLib/Manager/CoreManager.cs:421` | `_runningProxyTag` capture |
| `ServiceLib/Manager/CoreManager.cs:549` | the switch context's proxy tag → statistics key |

**Fix:** port `resolveRoutedOutbound()` into `XrayJsonTemplateFmt` and port the test file. The
desktop's `V2rayConfig` DTO already carries `routing.rules` and `routing.balancers`, so this is a
resolution change, not a model change.

---

# Part II — the rest of the owner's list

## 5.1 Copy — B1, B2, B3

### PC-13 — «провайдер» everywhere; the owner's word is «подписка» · **high**

He is explicit that this overrules the terminology lock in `00-rules.md` 9.3 and every register row
derived from it. Every site is still «провайдер»:

| Key | File:line | Current |
|---|---|---|
| `Common_AddSubscription` | `Common/L.Common.cs:28` | «Добавить провайдера» |
| `Common_UpdateSubscription` | `Common/L.Common.cs:31` | «Обновить провайдера» |
| plural set | `Common/L.Common.cs:77` | `провайдер / провайдера / провайдеров` |
| `Home_NoSubsHint` | `Common/L.Home.cs:34` | «Добавьте провайдера или отсканируйте QR-код…» |
| `Onboarding_Title` | `Common/L.Home.cs:40` | «Добавьте провайдера» |
| `Servers_EmptyHint` | `Common/L.Servers.cs:24` | «Добавьте провайдера…» |
| `Sub_Delete` | `Common/L.Servers.cs:53` | «Удалить провайдера» |
| `Sub_DeleteConfirm` | `Common/L.Servers.cs:54` | «Удалить провайдера и его серверы?» |
| `Settings_SubAutoEmptyHint` | `Common/L.Settings.cs:80` | «Добавьте провайдера, чтобы включить» |
| `Settings_Providers` | `Common/L.Settings.cs:82` | «Провайдеры» |
| `Provider_Title` | `Common/L.Settings.cs:175` | «Провайдеры» |
| `Backup_CreateHint` | `Common/L.Settings.cs:201` | «…настройки, провайдеров и серверы…» |
| `Dns_Provider` | `Common/L.Settings.cs:151` | «Провайдер» — DNS provider, **leave this one** |

The comment at `L.Common.cs:26-27` that justifies the split («`Подписка` is reserved for the paid
Departament service») is what he overruled. Delete the comment with the strings. `Dns_Provider` is a
different noun and stays.

### PC-14 — «серверам Departament» still capitalised · **medium**

`Common/L.Account.cs:33` — «Купите тариф, чтобы подключаться к серверам **D**epartament.» (and the
English at `:34`). Every other string on the desktop is already lowercase (`Account_SignInTitle:37`,
`Login_Title:132`, `About_TitleVersion:212`). One string left. B2 is otherwise done.

### PC-15 — the mode segment's three labels are wrong, and the two platforms disagree · **medium**

B3 wants **«TUN» / «Proxy» / «TUN + Proxy»**, in that order, on both platforms.

- Desktop: `Views/SettingsView.axaml:347-363` — «VPN» (a bare literal, `:353`),
  `Settings_ModeProxy` = «Прокси» (`L.Settings.cs:36`), `Settings_ModeBoth` = «Вместе» (`:37`).
- Android: `res/values/strings_home_shell.xml:7-9` — «VPN-туннель» / «Прокси» / «VPN + прокси».

Three labels, three platforms' worth of disagreement, none of them his. Note the desktop's third
value is «Вместе», which does not say what it does; the helper under it
(`Settings_ModeBothHint`, `L.Settings.cs:39`) has to explain the label, which is the tell.

**Note on C2, which said the desktop segment has two options:** it has **three** —
`SegModeVpn` / `SegModeProxy` / `SegModeBoth` in a `UniformGrid Columns="3"`
(`SettingsView.axaml:348-363`), backed by `ModeVpn/ModeProxy/ModeBoth`
(`ViewModels/SettingsViewModel.cs:40-42`). The count is not the defect; the **names** are. Treated
as PC-15 rather than filed as a separate structural item.

## 5.2 Structure — C1, C3, C4, F2

### PC-16 — the desktop add menu is already two items; the phone's is the one that is overloaded · **low**

C1 asks for QR and clipboard only. Desktop `Views/HomeView.axaml:57-60` already offers exactly
`Common_AddFromClipboard` and `Common_AddViaQr`, and `Views/OnboardingView.axaml:86-137` offers the
same two. **Nothing to do on PC** — logged so the desktop is not "fixed" into having more.

### PC-17 — the sign-in screen · **high** · *see §9.1, scope not established*

C4 («что это ваще за меню входа такое кривое и непеределанное») and C3 (buttons at the bottom of an
empty screen). What I can state from the code:

- `Views/LoginView.axaml` (840 lines) is **not** an un-designed screen. It has a sub-page toolbar
  (`:163`), a method block (`:197`), a sign-in/register segment (`:239-245`), email + password with a
  reveal toggle (`:257-302`), inline error slots, magic-link and forgot-password (`:411-420`), a
  Telegram path (`:456`), a browser path (`:480`), a paste-code path (`:503-513`), a 6-cell 2FA block
  (`:553`) and an awaiting state with its own animation (`:635-680`).
- What is structurally wrong against `14-auth.md` and against the Android screen, verified:
  - the segment is **mode** («Вход»/«Регистрация», `:239-245`), not **method**;
  - `EmailBox:257`, `PasswordBox:274`, `ConfirmPasswordBox:320` have **no label element** — the
    `Watermark` is doing the label's job, so the field loses its name as soon as you type in it;
  - every error slot ships `IsVisible="False"` (`:264-268`, `:330-334`, `:579-583`, `:619-624`) with
    no reserved space, so the form **jumps** when an error appears;
  - the toolbar title is `Headline` 24 where sub-pages take `Title` 16/700 (same defect as
    `DevicesView.axaml:125`, `PaymentHistoryView.axaml:55`, `BuyView.axaml:254`).
- The alternative methods sit at the end of a long scroll (`AltMethodsBlock`, `:431`, after
  `Margin="0,24,0,0"`), which is the shape C3 describes.

The instruction is «сделать 1 в 1 как на андроиде». Rebuilding it against the Android sign-in is the
work order; the four findings above are the minimum it must fix.

### PC-18 — the Аккаунт tab: the signed-**out** state is the bare one · **high**

F2 says the tab «вообще хуёво выглядит не стилизованно… на андроиде в 100 раз лучше». Read against
the code, that cannot be about the signed-in tab: `Views/AccountView.axaml` is 1331 lines with an
offline bar (`:171-192`), a tightened profile header (`:196`), a subscription card with a traffic
meter, a sub selector, buy/devices/history rows and an inline sign-out confirm (`:1258-1273`), capped
at `MaxWidth="720"` and centred (`:168`).

The state that **is** «a bare card floating in an empty pane» is the signed-out gate,
`Views/AccountView.axaml:1276-1327`: a `MaxWidth="320"` `StackPanel`, centred, holding a `Headline`,
a `Subtitle`, one `Primary Tall` Telegram button and one left-aligned `Tertiary` — inside a pane over
1160 px wide. Nothing else. It is the one account state that was never designed.

Second, and true of the signed-in tab as well: a 720-capped single column centred in a 1160+ pane is
the Android layout transplanted, not a desktop composition. F3 asks for the phone one-to-one **in
structure, hierarchy and copy — natively expressed**. A single narrow column in a wide window is the
"stretched phone" the desktop plan explicitly rejects.

## 5.3 The phone's small courtesies — G1 and G2

### PC-19 — G1: tapping a server on the desktop **switches the live tunnel with no offer** · **high**

Android, `V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt:925-948`: selecting a server
never touches a running tunnel; it shows a Snackbar naming the server
(`server_selected_reconnect_prompt`) with a «Переподключиться» action, and declining leaves the
connection exactly as it was.

Desktop, `Views/ServerListView.axaml.cs:332-353`:

```csharp
if (IsCoreRunning())
{
    await vm.SelectServer(item.IndexId);
    return;
}
```

A row click with the tunnel up **switches immediately**. There is no prompt, no naming, and — worse —
no way to select without switching: the context menu's «Сделать основным» (`OnRowMakeDefault`,
`:786-792`) calls the same `SelectRow`, and «Подключиться» (`OnRowConnect`, `:776-782`) calls
`vm.SelectServer` too. Connected, the two menu items and the row click all do the same thing, and it
is the destructive one. The markup comment at `ServerListView.axaml:174-181` asserts «ВЫБОР ≠
ПОДКЛЮЧЕНИЕ… Клик по строке ВЫБИРАЕТ и никогда не поднимает туннель» — true only while
disconnected.

### PC-20 — G2: the courtesy audit the owner asked for, against the desktop · **medium**

He asked for the list *before* the fixing, so the list is the record. Seven rules, checked:

| Rule | Desktop today |
|---|---|
| a cancelled action is never reported as a failure | **holds** on the desktop — no equivalent of the Android VPN-permission-cancel path |
| an action that cannot work does not present an enabled control | **fails** — see PC-19 (menu items that all do the same thing), and PC-25 (a settings row wired to nothing) |
| an in-flight action shows it, and cannot be fired twice | **partly** — sub-page push is idempotent by type (`MainWindow.axaml.cs:1118-1121`); the connect control still has no in-flight lock beyond its own 12 s deadline |
| a destructive action names what it will destroy | **holds for text** (`Sub_DeleteConfirm`), **fails for form** — see PC-06 |
| an empty state says what to do next | **fails** — `ServerListView.axaml:305-326` is icon + title + line, **no action button**. Title + line + action is the formula |
| a failure offers the retry, in the same place it reports it | **partly** — the account tab does (`AccountView.axaml:184-190`); nothing else does |
| state changed elsewhere repaints here | **fails** — no offline state anywhere outside the account tab: `grep -i 'offline\|Нет сети' Views/*.axaml Common/L.*.cs` finds only `AccountView`'s bar |

---

# Part III — everything else still open

## 6.1 Core, lifecycle, correctness

| # | What the user sees | Where | Sev |
|---|---|---|---|
| **PC-21** | Traffic stats read a flat 0 KB/s for a working tunnel whenever the provider's template tags its outbound anything but `proxy*`. `ParseOutput` accumulates only keys starting with `Global.ProxyTag` (`"proxy"`), while `MergeAppInbounds` deliberately keeps the template's outbounds as-authored and never retags them | `ServiceLib/Services/Statistics/StatisticsXrayService.cs:96-100`; `ServiceLib/Handler/CoreConfigHandler.cs:130-132,162-163` | high |
| **PC-22** | Account API calls are routed through the app's **own** tunnel, and DNS for the backend is pinned for the process lifetime. `new HttpClientHandler()` with no `PooledConnectionLifetime` and `UseProxy` defaulting to the system proxy — which this app points at `127.0.0.1:<socks>` in `ForcedChange`/`Pac` mode. When the tunnel is up but broken, sign-in and subscription sync fail | `v2rayN.Desktop/Account/DepartamentApiClient.cs:24-33` | high |
| **PC-23** | A crash or power-loss mid-write silently signs the user out. `File.WriteAllBytes` on the auth blob, no temp-file + move; `Load` treats any parse failure as "start fresh". `ConfigHandler.SaveConfig:204-215` already has the correct pattern next door | `v2rayN.Desktop/Account/AuthTokenStore.cs:194` | high |
| **PC-24** | Per-node «Скорость» on a Departament server now reports an honest failure instead of a bogus measurement — but it still cannot measure. The guard landed (`CoreConfigV2rayService.cs:249-261`), so this is now a **capability gap**, not a bug: throughput on the product's own servers has no working path. Also `Global.ProtocolTypes[_node.ConfigType]` is still an unguarded indexer whose `KeyNotFoundException` is swallowed | `ServiceLib/Services/CoreConfig/V2ray/V2rayOutboundService.cs:314` | medium |
| **PC-25** | «Автообновление подписок» **now writes the right field** (`SubItem.AutoUpdateInterval`, minutes — `SettingsViewModel.cs:549-551`, read at `TaskManager.cs:84-85`). What remains open from `pc-settings` §4.2 is only the label: `Settings_Providers`/`Provider_Title` — folded into PC-13 | — | — |
| **PC-26** | «Язык» — verify the third value. `SettingsViewModel.cs:603` no longer toggles `"en" ? "ru" : "en"`; whether `Системный` is reachable was not re-traced end to end | `v2rayN.Desktop/ViewModels/SettingsViewModel.cs:603` | low |

## 6.2 Settings, and the things that are modelled but not shown

| # | What the user sees | Where | Sev |
|---|---|---|---|
| **PC-27** | An out-of-range local-proxy port is still rejected **in silence**. `PortInvalid` is declared (`:101`) and set (`:481`) and read by **nothing** — `grep` over every `.cs` and `.axaml` returns those two lines plus the doc comment. The state moved from "not modelled" to "modelled and unrendered", which is not a fix | `v2rayN.Desktop/ViewModels/SettingsViewModel.cs:101,481` | medium |
| **PC-28** | The settings search field silently uses a different, older field style than every other input, and 75 lines of the promoted global theme have no consumer at all. `SettingsView.axaml:84` declares a **local** `ControlTheme x:Key="TextBox.IncyField"` that shadows the promoted global one at `GlobalResources.axaml:635`; `{StaticResource}` resolves nearest-first. The two bodies differ (radius, and the local one drops the `:disabled` opacity rule) | `Views/SettingsView.axaml:84` | medium |
| **PC-29** | «Масштаб интерфейса» stays Russian when the app is in English — a hardcoded literal, not a `loc:T` key. Same shape in `StatusBarView.axaml:114,120` | `Views/SettingsView.axaml:792` | low |
| **PC-30** | Escape does not pop a sub-page, the mouse back button does nothing, Ctrl+F does not reach search, Ctrl+, does not open settings. The shell handler binds only Ctrl +/−/0, Ctrl+V, Ctrl+S, F5 | `Views/MainWindow.axaml.cs:1965-2025` | medium |
| **PC-31** | Six settings groups against the spec's four, 22 named rows, 8 reachable sub-pages out of 17 spec routes, and **25 `Classes="Card"`** across a settings tree the spec says has none | `Views/SettingsView.axaml:223,522,641,686,875,972`; `SettingsView.axaml.cs:43-50` | medium |

## 6.3 Theme, tokens, and design-system debt

| # | What the user sees | Where | Sev |
|---|---|---|---|
| **PC-32** | In the black/mono theme, **every primary button flashes brand blue the moment a pointer touches it**. The token wave added seven theme-dependent keys to Dark and Light and mirrored none into the mono overlay: `Brush.AccentHover`, `Brush.AccentPressed`, `Brush.OutlineControl`, `Brush.OnSurfaceVariantHover`, `Brush.Amber`, `Brush.AmberText`, `Brush.Ping.Good`. `Button.Primary:pointerover` (`GlobalStyles.axaml:652`) resolves `Brush.AccentHover` → falls through to the base variant → `#3D7EF0`. `Button.Primary` is used 44 times. Mono's whole contract is "no accent hue" | `v2rayN.Desktop/App.axaml.cs` `BuildMonoOverlay` | high |
| **PC-33** | The provider meta-bar paints **dark-theme hex literals**, so in the light theme its accent, muted text and destructive red are wrong. `static readonly IBrush _accent = Color.Parse("#4C8DFF")`, `_muted "#9BA1AD"`, `_red "#F04452"`, with a comment that still says «тема одна, тёмная» | `Views/SubscriptionMetaView.axaml.cs:29-32` | high |
| **PC-34** | Five different press scales in one product: `0.97` ×20, `0.92` ×11, `0.94` ×1, `0.96` ×1, `0.9` ×1. `scale(0.92)` is redeclared verbatim in twelve view files. D-11 says one number | `Views/*.axaml`, `Assets/GlobalStyles.axaml` | medium |
| **PC-35** | 45 class names were added to `GlobalStyles.axaml` (+1197 lines) and **45 of 45 are referenced by zero views and zero code-behind**; `Common/Motion.cs`'s new members (`Dur.Pulse/Spin/Debounce/RevealExit/StateExit/Hover`, `PressScale`, `Play()`, `StaggerFor()`) have **zero call sites**. Views still speak the old vocabulary plus 26 view-local rules in `AccountView` alone. Not a user-visible bug; it is the reason the next design change costs twice | `Assets/GlobalStyles.axaml`, `Common/Motion.cs` | medium |
| **PC-36** | Retired tokens still draw live UI: `Radius.Search` is the corner radius of **the canonical server row** (`ServerListView.axaml:156`) and of both promoted text-box themes. `Brush.OutlineStrong` (required by `33-master-plan-pc` 2.12.2 for the connect ring) does not exist | `Assets/GlobalResources.axaml`, `Views/ServerListView.axaml:156` | low |
| **PC-37** | Icon-only controls are unnamed for a screen reader: `AutomationProperties.Name` appears in **1** of 50 views | `Views/**` | medium |
| **PC-38** | `PaymentHistoryView` and `DevicesView` are unvirtualised — `VirtualizingStackPanel` appears exactly once in the tree, in `ServerListView.axaml:121` | `Views/PaymentHistoryView.axaml`, `Views/DevicesView.axaml` | low |
| **PC-39** | 91 off-scale `Margin`/`Padding`/`Spacing` values across `Views/` (allowed 0/4/8/12/16/24/32/68). Worst: `SubscriptionMetaView` 10, `AccountView` 9, `CompactServersView` 8, `RoutingSubView` 7 | `Views/**` | low |

## 6.4 Dead weight

| # | What | Where | Sev |
|---|---|---|---|
| **PC-40** | **14 of 50 views are unreachable** — zero constructors, zero XAML refs, no command raising their dialog: `ServersView`, `CompactServersView`, `ProfilesView`, `ProviderSettingsPage`, `ThemeSettingView`, `BackupAndRestoreView`, `CheckUpdateView`, `MsgView`, `ClashProxiesView`, `ClashConnectionsView`, `OptionSettingWindow` (1206 lines), `GlobalHotkeySettingWindow`, `FullConfigTemplateWindow`, `SubSettingWindow`. With them go core selection, log level, global hotkeys, the config template, check-for-updates and the log viewer. **`CompactServersView.axaml:88-113` holds the only server search field in the product, and it is unreachable** | `Views/**` | medium |
| **PC-41** | Editing `ServersView.axaml` or `CompactServersView.axaml` ships **zero pixels**: `BottomNavBar.axaml.cs:9-14` declares three tabs, `MainWindow.axaml.cs:175` wires three rail buttons. Recorded so the next wave does not restyle a dead file | — | — |

## 6.5 Release and packaging — nothing here is visible from a green build

All from `release-desktop.md`, spot-verified. These do not block *this* build (win-x64 zip), they
block a release.

| # | What | Where | Sev |
|---|---|---|---|
| **PC-42** | **`package-debian.sh` can `git checkout -f` an *upstream* tag over this branch and silently ship upstream v2rayN.** `choose_channel` only prompts on a TTY, so in CI it falls through to `latest` from `2dust/v2rayN`. Locally it destroys uncommitted work with no confirmation | `package-debian.sh:157-215` + five sibling scripts | blocks-release |
| **PC-43** | The `.deb`/`.rpm` install a package that cannot launch: the scripts look for a binary named `v2rayN`, the fork's `AssemblyName` is `departament`, so **nothing is ever made executable** and the launcher exits with «no executable found» | `package-debian.sh:515-541,600-677`; `package-rhel.sh:500-571`; `ServiceLib/Common/Utils.cs:1271-1296` | blocks-release |
| **PC-44** | The macOS `.app` declares a `CFBundleExecutable` that is not in the bundle → macOS refuses to launch it. No codesign, no notarisation | `package-osx.sh:15-17,37-47` | blocks-release |
| **PC-45** | `test.yml` installs .NET **8** and runs `dotnet test` against a `net10.0` solution → `NETSDK1045`. It is the only workflow gating pull requests | `.github/workflows/test.yml:24` | blocks-release |
| **PC-46** | Autostart is a no-op on the shipping build and the toggle **confidently reports success**. `AutostartHelper` writes `HKCU\…\Run`; Windows does not launch elevation-requiring executables from the Run key at logon, and `IsEnabled()` reads the value back and returns true. Two competing autostart mechanisms exist with different names | `Common/AutostartHelper.cs:46-48,85-104`; `ServiceLib/Handler/AutoStartupHandler.cs:83-125`; `ViewModels/SettingsViewModel.cs:279-287` | high |
| **PC-47** | In a Release build every framework error message the app shows the user, and everything in the log, collapses to a resource key — «Не удалось зарегистрировать: UnauthorizedAccess_IODenied_Path». `UseSystemResourceKeys=true` is honoured at runtime whether or not the app is trimmed | `v2rayN/Directory.Build.props:28` | high |
| **PC-48** | "Check for updates" resolves its asset from **`2dust/v2rayN`** and hands the download to `AmazTool`, which unzips it over the install directory. Latent only because `CheckUpdateView` is unreachable (PC-40) | `ServiceLib/Global.cs:674`; `Services/UpdateService.cs:315-336` | high |
| **PC-49** | `AmazTool` kills and relaunches a process named `v2rayN`; the fork's process is `departament`. Reachable via `BackupAndRestoreViewModel.cs:141-147`'s `rebootas` | `AmazTool/Utils.cs:27,36`; `AmazTool/UpgradeApp.cs:24` | medium |
| **PC-50** | The app reports itself as **v2rayN 7.23.4** in the About row | `v2rayN/Directory.Build.props:4` | medium |
| **PC-51** | Four upstream `push: master` release workflows and `winget-publish.yml` (`2dust.v2rayN`) fire on merge and publish `v2rayN-*` artifacts — one of them builds the **WPF** project, which is not this app | `.github/workflows/*` | high |
| **PC-52** | Every publish ships eight `ResUI` satellite folders (fa, fr, hu, id, ru, zh-Hans, zh-Hant) into a directory called "departament" | `Directory.Build.props` (`SatelliteResourceLanguages` unset) | low |
| **PC-53** | `PublishReadyToRun=false` on a single-file self-contained Avalonia app is seconds of cold start, for no correctness benefit | `Directory.Build.props:30` | low |
| **PC-54** | `CETCompat=false` opts a VPN client that runs elevated out of the hardware shadow-stack mitigation, inherited from upstream with no decision behind it | `v2rayN.Desktop.csproj:15` | low |
| **PC-55** | **Do not enable `PublishTrimmed`.** Eight reflection surfaces; two of them — `System.Text.Json` config/DTO loading and the `sqlite-net` server store — fail *silently* against the user's saved data | — | — |

---

# Part IV — housekeeping, so nothing gets refiled

## 7. Closed during this session — verified fixed in code, do not refile

| Claim | Where it now stands |
|---|---|
| A transient API failure at launch deletes every subscription and its servers (C1 / F1) | **Fixed.** `Account/SubscriptionSyncManager.cs:57-100` — `primaryOk`/`allOk` gate a `canPrune` flag, plus a second guard for the 200-with-null case (`:112-122`) |
| `RemoveTunDevice` awaits an unbounded `pnputil` inside `_coreOpGate` | **Fixed.** `Common/WindowsUtils.cs:77` now passes `_tunRemoveTimeout` |
| `ProfileExManager` mutates a non-thread-safe `Queue<string>` from parallel speedtests | **Fixed.** Rewritten; the file's own header documents what the old `Queue` + `ConcurrentBag` lost |
| The user's TUN preference is permanently destroyed after one unelevated run | **Fixed.** `ConfigItems.cs:199` `TunUnavailable` is `[JsonIgnore]`; `StatusBarViewModel.cs:150-156` keeps the intent; the reboot-as-admin path no longer writes `false` (`:524-537`) |
| `_hasNextReloadJob` read-then-clear race strands the tunnel on the wrong server | **Fixed.** Replaced by an `Interlocked` `_pendingJob` ordered by strength (`MainWindowViewModel.cs:838-911`) |
| Real-ping reports healthy latency for a proxy returning error pages | **Fixed.** `ConnectionHandler.cs:88-100` checks `IsSuccessStatusCode` and uses `ResponseHeadersRead` |
| Speedtest builds a bogus outbound for Custom nodes and reports success | **Fixed.** `CoreConfigV2rayService.cs:249-261` rejects `EConfigType.Custom` with a real message (the capability gap remains — PC-24) |
| Right-click / middle-click on the connect disc toggles the VPN | **Fixed.** `ConnectHeroView.axaml.cs:694` filters `IsLeftButtonPressed` |
| Double-activating the login CTA strands a `LoginView` forever | **Fixed.** `LoginView.axaml.cs:180` resets `_detached` |
| A cancelled tab crossfade strands the two-steps-back tab opaque | **Fixed.** `MainWindow.axaml.cs:450-458` normalises every uninvolved surface on each swap |
| `Execute().Subscribe()` with no `onError` (4 sites) | **Fixed.** No matches remain |
| Compact/wide layout hardcoded to compact at first frame | **Fixed.** `MainWindow.axaml.cs:256` reads `Width` |
| «Устройства» opens the **root** subscription's devices | **Fixed.** `DevicesView.axaml.cs:31` passes `AccountViewModel.Shared?.DevicesScopeUuid` |
| «Обход локальной сети» writes the opposite direction | **Fixed.** `SettingsViewModel.SetModeAsync:435` |
| «Автообновление подписки» configures geo-file updates in the wrong unit | **Fixed.** `SettingsViewModel.cs:528-551` writes `SubItem.AutoUpdateInterval` in minutes |
| Russian text set in the Cyrillic-free brand face in `AccountView` | **Fixed.** No `Font.Grotesk` remains in any view's markup |
| `ServerListView` rows are mouse-only | **Fixed.** `Focusable`/`IsTabStop` at `:157-158`, `OnRowKeyDown` at `.cs:378-410` (Space/Enter/Delete/Ctrl+P). *Note: the Enter behaviour is itself PC-19.* |
| Inverted light-accent fallback comment; `ProviderSettingsPage` raw em-dash in `Text=` | **Fixed.** Both gone |
| Content never capped at 720 | **Fixed.** 21 `MaxWidth="720"` sites |
| Em/en dashes in shipped copy; «Сервера» → «Серверы»; servers empty-state copy | **Fixed** (the empty state still lacks its **action** — PC-20) |

## 8. Refuted by verification — keep out of the work list

| Claim | Why it is out |
|---|---|
| «The desktop mode control has two options where the phone has three» (owner C2) | The desktop has three (`SettingsView.axaml:348-363`). The **labels** are wrong — carried as PC-15 |
| «The Аккаунт tab is a bare card floating in an empty pane» (owner F2), as a statement about the tab | The signed-in tab is 1331 lines of built screen. The **signed-out gate** is the bare one — carried as PC-18 |
| «Double-tapping the onboarding CTA» as the trigger for the stranded `LoginView` | `verify-loginview-detached-handoff.md` refuted the pointer trigger; the underlying flag is fixed anyway |
| «`_hasNextReloadJob`'s `:873-875` window is the lossy one» | `verify-hasnextreloadjob-race.md`: that window is provably self-healing. The real one was elsewhere, and is fixed |
| «Speedtest never works for the product's servers, high severity» | `verify-desktop-speedtest-custom-outbound.md` downgraded it: the path was not reachable from the shipped UI. Now guarded — PC-24 |
| «Five interactive archetypes below the 48px touch minimum» | `verify-iconbutton-drift.md`: headline framing wrong. The real residue is the icon-button consolidation not landing — folded into PC-34/PC-35 |
| «`ProfileExManager`'s queue race is high severity» | `verify-profileex-queue-race.md` corrected the mechanism and the severity; fixed since |
| Adding a «Серверы» destination to the desktop | Owner decision, `1b9d3cb` — the desktop does not get one. `HomeView`'s right column is the list |

## 9. Not established — the two questions that settle them

### 9.1 Which sign-in screen the owner was looking at

C4's «меню входа» could be `LoginView` (the full form, reached from Аккаунт or onboarding) or the
onboarding gate's sign-in row (`OnboardingView.axaml:161-190`). Both are candidates for C3's "buttons
at the very bottom": `OnboardingView` centres its stack (`:47-52`), `LoginView` puts the alternative
methods after a long scroll (`:431`). **The question:** which screen is in his screenshot. The four
structural findings in PC-17 hold for `LoginView` either way.

### 9.2 Whether the owner's Windows run was actually elevated

PC-02's gate cannot close on an elevated Windows process, and the manifest requests elevation. If he
accepted a UAC prompt and it still did not tunnel, the cause is downstream of the gate — the sing-box
pre-service (`CoreConfigContextBuilder.cs:197-215`) failing to start or to create the wintun device,
or the port contract between it and the Xray SOCKS inbound (`CoreConfigHandler.MergeAppInbounds:165`).
**The question:** did the app show a UAC prompt at launch, and does `bin/sing_box/sing-box.exe` exist
in his install. PC-01 must land regardless — it is what would have answered this without asking him.

---

## 10. Suggested order

1. **PC-01** — the app must say when it cannot tunnel. Everything else about §1 is guesswork until it does.
2. **PC-12** — the template outbound. He raised it twice; it makes ping and the protocol chip lie.
3. **PC-05 / PC-06** — the one flicker cause and the confirm button, together; they are the same missing rest-state declaration.
4. **PC-08 → PC-11** — ship static Space Grotesk masters, then set the line heights from the real metrics. One change, then one pass.
5. **PC-13 / PC-14 / PC-15** — the copy. Cheap, and it is his product's vocabulary.
6. **PC-19** — the reconnect offer, ported one to one from `MainActivity:933-948`.
7. **PC-32 / PC-33** — the two theme leaks.
8. **PC-17 / PC-18** — the sign-in and the account gate, rebuilt against the phone.
9. **PC-42 → PC-45** — before anyone tags a release.
