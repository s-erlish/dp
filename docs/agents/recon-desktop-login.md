# Recon — Desktop sign-in & onboarding, complete spec for the Android port

**Source of truth:** `/home/user/v2rayN`, branch `claude/app-audit-agents-hyyftk`
(HEAD `c99664c` "Never greet a returning user with the 'add a subscription' screen").

Everything below was read from the files cited. Every measurement, string, colour, duration and
easing curve is quoted with `file:line`. Nothing is inferred or invented; where a code comment
contradicts the code, both are reported and the code wins.

Primary files:

| Purpose | Path |
|---|---|
| Login screen markup | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml` (954 lines) |
| Login screen logic | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/LoginView.axaml.cs` (1377 lines) |
| First-run screen markup | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/OnboardingView.axaml` (238 lines) |
| First-run screen logic | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/OnboardingView.axaml.cs` (213 lines) |
| Post-login sync overlay | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/AccountSyncView.axaml` (176 lines) |
| Motion tokens (C#) | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/Motion.cs` |
| Reduced-motion broadcast | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/MotionState.cs` |
| Design tokens | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml` (569 lines) |
| Component styles | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml` (1448 lines) |
| Strings | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Common/L.Account.cs`, `L.Common.cs`, `L.Home.cs`, core `L.cs` |
| View-model | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/ViewModels/AccountViewModel.cs` (2920 lines) |
| Auth orchestration | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Account/AuthManager.cs` |
| API client | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Account/DepartamentApiClient.cs`, `BackendConfig.cs`, `Dto/AuthDtos.cs`, `ApiError.cs` |
| Session persistence | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Account/AccountSession.cs` |
| Shell / navigation | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/MainWindow.axaml.cs`, `Views/MainWindow.axaml` |
| URL-scheme plumbing | `/home/user/v2rayN/v2rayN/v2rayN.Desktop/App.axaml.cs`, `Program.cs` |

---

## 1. Where these two screens sit in the app

### 1.1 Three-way shell gate

`MainWindow.ApplyShellVisibility()` (`Views/MainWindow.axaml.cs:839-871`) picks exactly one of three
full-window surfaces:

```
Control target = (_isSyncing || _isStartupLoading)
    ? accountSyncView
    : (_isEmpty && !_isLoggedIn) ? onboardingView : bodyRoot;
```
(`MainWindow.axaml.cs:867-869`)

* `_isSyncing` ← `AccountViewModel.IsImportingAccount` (post-login import) — `MainWindow.axaml.cs:1032-1038`
* `_isStartupLoading` ← `AccountViewModel.IsStartupLoading` (cold start with a persisted session) — `:1045-1051`
* `_isEmpty` ← `HomeViewModel.IsEmpty` — `:1055-1061`
* `_isLoggedIn` ← `AccountViewModel.IsLoggedIn` — `:1065-1071`

Priority is **syncing > empty > content**. The `&& !_isLoggedIn` clause is the HEAD commit's fix:
a signed-in user with zero subscriptions goes to the shell, **never** back to the "add a
subscription" onboarding (`MainWindow.axaml.cs:857-860`).

`HomeViewModel.IsEmpty` is deliberately tri-state-safe (`ViewModels/HomeViewModel.cs:540-552`):
until the engine has actually loaded the server list (`Profiles.HasLoadedServers`), it falls back to
a synchronous storage snapshot taken at launch; an unknown snapshot leaves **both**
`HasServers` and `IsEmpty` false, so the gate shows the shell rather than greeting a subscriber with
onboarding.

Transition between the three surfaces: opacity-only crossfade, **200 ms `Ease.Standard`**
(`MainWindow.axaml.cs:876-941`), instant on first show and under `.lite`.

### 1.2 Sub-page stack

`LoginView` is not a window — it is pushed onto a sub-page stack over a `ContentControl`
`subPageHost` with an opaque `Brush.Bg` background (`MainWindow.axaml:611-616`), z-above onboarding
and above the sync overlay.

* Push: `translateX 16 → 0` + `opacity 0 → 1`, **300 ms `Ease.OutQuint`** (`MainWindow.axaml.cs:1126-1147`)
* Pop: `translateX 0 → 16` + `opacity 1 → 0`, **200 ms `Ease.Standard`** (`:1149-1171`)
* Under `.lite` both are instant (`:1128-1133`, `:1151-1155`).

### 1.3 Entry points into `LoginView`

| Entry | Code | Behaviour |
|---|---|---|
| Onboarding "Войти через Telegram" | `OnboardingView.axaml.cs:108-111` → `MainWindow.OpenLoginTelegram()` (`MainWindow.axaml.cs:1237-1241`) | `OpenLogin()` then immediately `LoginTelegramCmd` — the login page opens **already in the awaiting state**, no method picker |
| Onboarding "Войти через сайт" | `OnboardingView.axaml.cs:114-117` → `MainWindow.OpenLoginSite()` (`MainWindow.axaml.cs:1246-1250`) | `OpenLogin()` then immediately `LoginBrowserCmd` — opens the browser handoff |
| Account tab logged-out CTA | `Views/AccountView.axaml.cs:62` (`LoginSiteButton.Click → LoginRequested`) wired at `MainWindow.axaml.cs:252` | plain `OpenLogin()` (method block) |
| Browser→app scheme callback | `App.axaml.cs:192-210` → `MainWindow.HandleAuthCallback` (`MainWindow.axaml.cs:1081-1094`) | brings window forward, opens `LoginView` if it isn't the top sub-page, then redeems the code |

**Discrepancy to carry over knowingly:** the code comment at `OnboardingView.axaml.cs:113-114` says
"Войти через сайт → открываем LoginView прямо на форме входа по email/паролю", but
`MainWindow.OpenLoginSite` (`MainWindow.axaml.cs:1246-1250`) actually fires `LoginBrowserCmd`
(browser handoff). The code is authoritative; the comment is stale.

`OpenLogin()` (`MainWindow.axaml.cs:1216-1232`) creates `new LoginView { DataContext = _accountVm }`
— the **shared** `AccountViewModel`, so the Account tab sees login state too — and subscribes
`BackRequested` to a handler that calls `_accountVm.CancelLogin()` **only when not logged in**
(so a successful login does not cancel anything), then `PopSubPage()`.

`OnboardingView.DataContext = _homeViewModel` (`MainWindow.axaml.cs:1007`).

---

## 2. Design tokens (must be mirrored on Android)

### 2.1 Colour — dark theme (`GlobalResources.axaml:62-97`)

| Token | Value | Usage on these screens |
|---|---|---|
| `Brush.Bg` | `#0A0B0D` | sub-toolbar floor; active segment tab fill |
| `Brush.Surface` | `#141619` | — |
| `Brush.SurfaceHigh` | `#1A1D21` | — |
| `Brush.SurfaceVariant` | `#1E2126` | text-field fill, segment track, 2FA cells, "Скоро" pill |
| `Brush.SurfaceHighest` | `#20242B` | tonal button fill |
| `Brush.OnSurface` | `#F2F4F8` | titles, field text, tonal button label, 2FA digits |
| `Brush.OnSurfaceVariant` | `#9BA1AD` | subtitles, hints, watermarks, inactive segment, eye glyph, disabled Google row, "Другой способ входа" |
| `Brush.Outline` | `#2A2E36` | field border on hover; filled 2FA cell border |
| `Brush.OutlineVariant` | `#20242B` | field border at rest, hairline dividers, 2FA cell border at rest, awaiting-ring track |
| `Brush.Accent` | `#4C8DFF` (`:39`, theme-independent) | primary CTA fill, focus rings, shield glyph, spinner arc, globe glyph, active 2FA cell border, caret |
| `Brush.OnAccent` | `#00183A` (`:40`) | text/glyph on the primary CTA, inline spinner stroke |
| `Brush.Tile.Blue` | `#4C8DFF` @ 20 % opacity (`:45`) | 64 dp shield tile background, success badge background |
| `Brush.Red` | `#F04452` | error-flash field border |
| `Brush.RedText` | `#FF6069` (`:84`) | **all error text** (6.7:1 on `#0A0B0D`) |
| `Brush.Hover` | `#000000` @ 32 % (`:86`) | hover darkening on tonal / link / icon buttons |
| `Brush.HomeGradient` | radial, centre & origin `50%,30%`, radius `75%,75%`: `#1B2D50` @0 → `#0E141F` @0.55 → `#0A0B0D` @1 (`:88-96`) | **background of BOTH login and onboarding** |
| Primary hover / pressed | `#3D7EF0` / `#3877E0` (`GlobalStyles.axaml:434,437`) | filled CTA states |
| `SelectionBrush` in fields | `#334C8DFF` (`GlobalResources.axaml:417`) | text selection |

Light theme parallel set at `GlobalResources.axaml:100-133` (`Brush.Bg #F4F7FC`, `Surface #FFFFFF`,
`SurfaceVariant #E9EEF7`, `SurfaceHighest #E3EAF4`, `OnSurface #111826`, `OnSurfaceVariant #54607A`,
`Outline #C3CCDC`, `OutlineVariant #DCE3EF`, `Red`/`RedText` both `#C42B32`, `Hover #000000` @ 6 %,
gradient `#FFFFFF → #EEF3FB → #DFE6F1`).

### 2.2 Type scale (`GlobalStyles.axaml:272-315`)

Font family: **Space Grotesk**, `avares://departament/Assets/Fonts/SpaceGrotesk.ttf#Space Grotesk`
(`GlobalResources.axaml:166`), applied to `TopLevel`, `TextBlock` and `TemplatedControl`
(`GlobalStyles.axaml:257-265`).

| Class | Size | Weight | Letter-spacing | Default colour |
|---|---|---|---|---|
| `Display` | 34 | Bold | −0.7 | `OnSurface` |
| `Headline` | 24 | Bold | −0.24 | `OnSurface` |
| `Title` | 16 | Bold | — | `OnSurface` |
| `TitleMedium` | 16 | Medium | — | `OnSurface` |
| `Body` | 14 | (regular) | — | `OnSurface` |
| `Subtitle` | 13 | (regular) | — | `OnSurfaceVariant` |
| `Caption` | 12 | (regular) | — | `OnSurfaceVariant` |
| `Chip` | 11 | Medium | — | `OnSurface` |

Numeric font token `Font.Numeric` (`GlobalResources.axaml:180`) = the same Space Grotesk face, always
paired with `FontFeatures="tnum,lnum,zero"` — used for the 2FA digits.

### 2.3 Spacing, radii, sizes

`GlobalResources.axaml:138-163, 284-309`; `GlobalStyles.axaml:16`.

* Spacing scale: `4 / 8 / 12 / 16 / 24 / 32`. Screen gutter = `16`.
* Radii: `Radius.Chip 12`, `Radius.Tile 12`, `Radius.Search 14` (text fields), `Radius.Button 16`,
  `Radius.Card 20`, `Radius.Sheet 24,24,0,0`, `Radius.Pill 100`.
* Sizes: `Size.IconButton 40`, `Size.Glyph 22`, `Size.Row 56`, `Size.SubToolbar 56`,
  `Size.CtaTall 52`, `Size.SegmentChip 44`, `Size.EmptyIcon 64`, `Size.EmptyGlyph 32`.

### 2.4 Component styles used by these screens

**`Border.SubToolbar`** (`GlobalStyles.axaml:897-902`) — height 56, background `Brush.Bg`,
`BorderThickness 0`, padding `16,0`. Deliberately seamless with the window (no divider, no shadow).

**`Button.BackNav`** (`:907-944`) — 40×40, `Radius.Pill`, transparent, `Cursor=Hand`, press
`scale(0.92)` over **120 ms** (no easing specified → Avalonia default linear), hover/pressed
background `Brush.Hover`; its `PathIcon` is 22×22 `Brush.OnSurface`.

**`Button.Primary`** (`:386-438`) — background `Brush.Accent`, foreground `Brush.OnAccent`,
`CornerRadius 16`, height **48**, padding `24,0`, Grotesk **15 Bold**, press `scale(0.97)` over
**120 ms `Ease.OutQuart`**, `:disabled` opacity **0.38**, hover `#3D7EF0` / pressed `#3877E0` with a
**150 ms `Ease.Standard`** brush transition (attached only when the window is not `.lite`).
**`Button.Primary.Tall`** (`:443-445`) overrides height to **52**.

**`Button.Tonal`** (`:449-493`) — background `Brush.SurfaceHighest`, foreground `Brush.OnSurface`,
radius 16, height **48**, padding `24,0`, Grotesk **15 Medium**, press `scale(0.97)` @120 ms
`Ease.OutQuart`, disabled 0.38, hover/pressed tint `Brush.Hover` @150 ms `Ease.Standard`.
`.Tall` (52) is **not** global for tonal — both `LoginView.axaml:63-65` and
`OnboardingView.axaml:20-22` declare a local `Button.Tonal.Tall { Height: 52 }` so the two brand
screens match.

**`Button.LinkAction`** (`:948-974`) — borderless, height **40**, padding `12,0`, `Radius.Chip 12`,
transparent background, foreground `Brush.Accent`, Grotesk **14 Medium**, `Cursor=Hand`, press
`scale(0.97)` @120 ms `Ease.OutQuart`. `LoginView.axaml:57-59` adds a hover backdrop
(`Brush.Hover` on the content presenter).

**`TextBox.Incy`** ControlTheme (`GlobalResources.axaml:407-482`) — background `Brush.SurfaceVariant`,
1 px `Brush.OutlineVariant` border, `CornerRadius 14`, `MinHeight 52`, padding `16,0`, font size 15,
foreground `OnSurface`, caret `Accent`, selection `#334C8DFF`, `Cursor=IBeam`, watermark
`OnSurfaceVariant`. Border transitions **150 ms** (`:429-433`); `:pointerover` → `Brush.Outline`,
`:focus` → `Brush.Accent`, `:disabled` → opacity 0.38. It exposes `InnerRightContent` (grid column 2)
— that is the slot the password eye lives in.

**Focus rings** (`GlobalStyles.axaml:1039-1098`) — keyboard-focus only, drawn as a `FocusAdorner`
*outside* layout (`Margin -2`, `BorderThickness 2`, `Brush.Accent`), radius = control radius + 2:
tonal/outlined/destructive → 18, LinkAction → 14, TextBox → 16, AccountChip → 22.
`Button.Primary` instead gets an **inner** ring, radius 16, `Brush.OnAccent` @ 40 % opacity, no
negative margin (blue-on-blue would be invisible). Focus rings deliberately survive `.lite`.

**`Ellipse.Spinner`** (`GlobalStyles.axaml:1318-1345`) — hit-test invisible; the `.spinning` class
runs `RotateTransform.Angle 0 → 360`, **1.1 s, LinearEasing, infinite**, and the keyframe is
attached only by the selector `:is(Window):not(.lite)` so it is never even created under
reduced motion.

---

## 3. Motion vocabulary

`Common/Motion.cs` is the single C# source of truth, mirroring the XAML `Ease.*` (`GlobalResources.axaml:191-198`).

**Durations** (`Motion.cs:22-53`):

| Token | ms | Paired curve |
|---|---|---|
| `Instant` | 0 | lite fallback |
| `PressIn` | 90 | `OutQuart` |
| `PressOut` | 160 | `OutQuint` |
| `State` | 220 | `Standard` |
| `Reveal` | 300 | `OutQuint` |
| `Exit` | 150 | `Standard` |
| `Shell` | 200 | `Standard` |
| `Slow` | 450 | `OutExpo` (reserved for auth→home handoff) |
| `Emphasis` | 600 | `OutQuint` |
| `Stagger` | 40 | — |

**Curves** (`Motion.cs:60-73`) — cubic-bezier control points, **not** the framework's built-ins:

| Token | Bezier | Android equivalent |
|---|---|---|
| `Ease.OutQuart` | `(0.25, 1, 0.5, 1)` | `PathInterpolator(0.25f,1f,0.5f,1f)` |
| `Ease.OutQuint` | `(0.22, 1, 0.36, 1)` | `PathInterpolator(0.22f,1f,0.36f,1f)` |
| `Ease.Standard` | `(0.2, 0, 0, 1)` | `PathInterpolator(0.2f,0f,0f,1f)` |
| `Ease.OutExpo` | `(0.16, 1, 0.3, 1)` | `PathInterpolator(0.16f,1f,0.3f,1f)` |

Discipline stated in `Motion.cs:16-17`: **ease-out only, exit faster than entry, no bounce/elastic.**

**Reduced motion** (`LoginView.axaml.cs:1352-1355`, `OnboardingView.axaml.cs:209-212`) is true when
any of: `Design.IsDesignMode`, env var `PREVIEW_VIEW` is set, or `MotionState.IsLite`.
`MotionState` (`Common/MotionState.cs`) is a live broadcast — the settings toggle takes effect with
no restart, and `LoginView` re-subscribes to re-evaluate the plane "breathing" loop
(`LoginView.axaml.cs:285-287`).

Two independent levers kill motion (documented `GlobalStyles.axaml:1360-1379`):
1. `:is(Window).lite <control>` empties `Transitions` (`:1383-1446`).
2. Infinite keyframe animations are gated at the **selector** level (`:is(Window):not(.lite)`), since
   Avalonia cannot cancel an already-running keyframe animation with a competing style.

---

## 4. OnboardingView — first-run screen

### 4.1 Container

```
Border Background="{DynamicResource Brush.HomeGradient}"        OnboardingView.axaml:43
└ ScrollViewer x:Name="Scroll", HScroll Disabled, VScroll Auto  :46-49
  └ Panel Margin="16,0" MinHeight="{Binding #Scroll.Bounds.Height}"  :50
    └ StackPanel x:Name="Column"
        Margin="0,24"  MaxWidth="440"
        HorizontalAlignment="Stretch"  VerticalAlignment="Center"   :51-56
```

`MinHeight` bound to the viewport height is what makes the column **centre** when it fits and
**scroll** when it doesn't (`:44-45`).

### 4.2 Children, in order (this order *is* the animation beat map)

| # | Element | Geometry / metrics | Copy |
|---|---|---|---|
| 0 | `ShieldTile` `Border` (`:61-74`) | 64×64, centred, `Brush.Tile.Blue`, `CornerRadius = Radius.Card (20)`, `RenderTransformOrigin 50%,50%`; child `PathIcon` 30×30 `Geo.Shield`, `Brush.Accent` | — |
| 1 | Wordmark `TextBlock` (`:77-81`) | `Margin 0,8,0,0`, centred, class `Title` (16 Bold) | `departament` (literal, never localised) |
| 2 | Hero title (`:85-91`) | `Margin 0,24,0,0`, stretch, class **`Display` (34 Bold, LS −0.7)**, `TextAlignment Center`, wrap | `{loc:T Onboarding_Title}` |
| 3 | Subtitle (`:95-102`) | `Margin 0,8,0,0`, class `Body` (14), `Brush.OnSurfaceVariant`, centred, wrap | `{loc:T Onboarding_Subtitle}` |
| 4 | `AddQrButton` (`:106-130`) | `Margin 0,24,0,0`, stretch, **`Primary Tall` = 52 h, radius 16, accent fill**; inner horizontal `StackPanel` `Spacing 8`, `PathIcon 20×20` (QR glyph, viewBox 1024) `Brush.OnAccent` + label `Brush.OnAccent` | `{loc:T Common_AddViaQr}` |
| 5 | `AddClipboardButton` (`:133-157`) | `Margin 0,8,0,0`, stretch, `Tonal Tall` = 52 h; `PathIcon 20×20` (copy glyph, 24 viewBox) `Brush.OnSurface` + label | `{loc:T Common_AddFromClipboard}` |
| 6 | Divider `Grid` (`:161-177`) | `Margin 0,24,0,0`, `ColumnDefinitions *,Auto,*`; two 1 px `Brush.OutlineVariant` rules, centre `TextBlock` class `Caption` with `Margin 12,0` | `{loc:T Onboarding_OrSignInShort}` |
| 7 | `LoginTelegramButton` (`:181-205`) | `Margin 0,16,0,0`, stretch, `Tonal Tall` 52 h; `PathIcon 20×20` Telegram paper-plane (viewBox 1024) `Brush.OnSurface` + label | `{loc:T Common_SignInTelegram}` |
| 8 | `LoginSiteButton` (`:209-233`) | `Margin 0,8,0,0`, **`HorizontalAlignment=Center`**, class `LinkAction` (40 h, accent text); `PathIcon` **16×16** globe `Brush.Accent` + `Brush.Accent` label, `Spacing 8` | `{loc:T Common_SignInWebsite}` |

Hierarchy rule stated at `:32-35`: **exactly one filled accent** (the QR button); the website path is
demoted to a text link so there is no second accent.

### 4.3 Icon path data (copy verbatim into Android vector drawables)

* Shield (`GlobalResources.axaml:316`, `Geo.Shield`, viewBox 24):
  `M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z`
* QR (`OnboardingView.axaml:122`, viewBox 1024) — full path in file, prefix `F1 M181.333333 384a32 32 0 0 1-64 0v-111.146667…`
* Copy/clipboard (`OnboardingView.axaml:149`, viewBox 24):
  `F1 M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z`
* Telegram (`OnboardingView.axaml:197` and `LoginView.axaml:36`, viewBox 1024) — identical outline
  paper-plane path in both files
* Globe (`OnboardingView.axaml:225` and `LoginView.axaml:45`, viewBox 24):
  `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z`

### 4.4 Actions

| Control | Handler | Target |
|---|---|---|
| `AddQrButton` | `OnAddQr` (`OnboardingView.axaml.cs:89-95`) | `HomeViewModel.AddViaQr()` → `MainWindowViewModel.AddServerViaScanAsync()` (`HomeViewModel.cs:473-479`) |
| `AddClipboardButton` | `OnAddClipboard` (`:98-104`) | `HomeViewModel.AddViaClipboard()` → `AddServerViaClipboardAsync(null)` (`HomeViewModel.cs:465-471`) |
| `LoginTelegramButton` | `OnLoginTelegram` (`:108-111`) | `MainWindow.OpenLoginTelegram()` |
| `LoginSiteButton` | `OnLoginSite` (`:114-117`) | `MainWindow.OpenLoginSite()` |

### 4.5 Entrance choreography

Constructor pre-hides all column children (`opacity = 0`) **only if motion is enabled**
(`OnboardingView.axaml.cs:54-61`), then `Loaded` fires once and plays the stagger
(`:67-84`). If reduced motion flipped on between ctor and first frame, `RestoreChildren()`
(`:199-206`) simply makes everything visible.

`BeatDelayMs` (`OnboardingView.axaml.cs:144-150`) — **four authored beats, not a uniform drip**:

| Child index | Delay | Beat |
|---|---|---|
| 0 | 0 ms | shield mark |
| 1, 2, 3 | 60 ms | identity (wordmark + title + subtitle) |
| 4, 5 | 140 ms | "provision access" (QR + clipboard) |
| 6, 7, 8 | 200 ms | "sign in" (divider + Telegram + website) |

Each child: `PlayReveal` (`:157-196`) — `opacity 0→1` plus `RenderTransform` from→to, **300 ms
(`Motion.Dur.Reveal`) `Ease.OutQuint`**, `FillMode.None`, with the beat delay.
Child 0 uses `scale(0.9) → scale(1)`; every other child uses `translateY(8px) → translateY(0)`
(`:133-134`). Total ≈ **500 ms** (200 delay + 300 duration), then **complete stasis** — no ambient
loops, and the shield explicitly does not breathe (`:20-23`, `:34-35`).

A safety `DispatcherTimer.RunOnce` at `delay + 300 + 250 ms` cancels the animation and force-restores
`Opacity = 1; RenderTransform = null` (`:174-181`), and the `finally` block does the same
unconditionally (`:189-195`). **Hit-testing is never gated by the animation — buttons are clickable
the whole time** (`:26`).

---

## 5. LoginView — structure

### 5.1 Root

```
Grid Background="{DynamicResource Brush.HomeGradient}" RowDefinitions="Auto,*"   LoginView.axaml:237
├ Row 0: Border Classes="SubToolbar"                                            :240-259
└ Row 1: ScrollViewer (HScroll Disabled, VScroll Auto)                          :262-265
    └ Panel Margin="16,8,16,28"                                                 :266
      └ Panel MaxWidth="440" HorizontalAlignment="Stretch"   ← z-stack          :270
        ├ StackPanel MethodBlock                                                :273-740
        ├ StackPanel AwaitingBlock       (IsVisible=False)                      :747-857
        ├ StackPanel EmailPendingBlock   (IsVisible=False)                      :865-927
        └ Border      SuccessBadge       (IsVisible=False, Opacity=0)           :932-948
```

The outer `Panel` is a **z-stack on purpose** so the three state blocks overlap in one cell and the
crossfade between them never shows an empty frame (`:267-269`).

Design intent recorded at `:229-236`: the radial `Brush.HomeGradient` background is shared with
Onboarding so the push transition onboarding → login has no "gradient → flat" seam, with the shield
tile acting as the shared continuity element. **No glow / halo behind the shield** — it is a brand
mark, not a status indicator (that language belongs to the connect hero).

### 5.2 Sub-toolbar (`LoginView.axaml:240-259`)

`Border Classes="SubToolbar"` (56 h, `Brush.Bg`, padding `16,0`) containing
`Grid ColumnDefinitions="Auto,*"`:

* `BackButton` — `Classes="IconButton BackNav"`, `VerticalAlignment=Center`,
  `ToolTip.Tip={loc:T Common_Back}`, child `PathIcon Data={StaticResource Geo.Login.Back}`
  (arrow-back, `LoginView.axaml:32`).
* `ToolbarTitle` — `Margin 12,0,0,0`, `Classes="Headline"` (24 Bold),
  `Text={loc:T Login_SignIn}`, `TextTrimming=CharacterEllipsis`.
  Re-derived imperatively by `ApplyMode()` (`LoginView.axaml.cs:614`): `Login_TabRegister` in register
  mode, `Login_SignIn` in sign-in mode.

`BackButton.Click` (`LoginView.axaml.cs:124-128`) sets `_handoffFired = true` **before** raising
`BackRequested` — so if the user taps back during the ~0.4 s success beat, the beat's
`finally → TryHandoff` cannot pop a second time.

### 5.3 MethodBlock — children in order (index = entrance beat key)

`StackPanel x:Name="MethodBlock"`, `VerticalAlignment=Top`, `HorizontalAlignment=Stretch`,
`RenderTransformOrigin="50%,50%"` (`:273-277`).

| # | Element | Metrics | Copy / binding |
|---|---|---|---|
| 0 | `ShieldTile` Border (`:282-296`) | 64×64, `Margin 0,16,0,0`, centred, `Brush.Tile.Blue`, radius 20, origin 50%,50%; `PathIcon` 30×30 `Geo.Login.Shield` `Brush.Accent` | — |
| 1 | Wordmark (`:300-304`) | `Margin 0,8,0,0`, centred, `Title` | `departament` |
| 2 | `TitleText` (`:308-315`) | `Margin 0,16,0,0`, stretch, **`Headline` (24 Bold)**, centred, wrap | `Login_Title` / `Login_TitleRegister` (imperative) |
| 3 | `SubtitleText` (`:316-324`) | `Margin 0,8,0,0`, `Body` 14, `OnSurfaceVariant`, centred, wrap | `Login_Subtitle` / `Login_SubtitleRegister` |
| 4 | Segment `Border Classes="SegTrack"` (`:328-341`) | see §5.4 | `Login_TabSignIn` / `Login_TabRegister` |
| 5 | `EmailBox` (`:346-351`) | `Margin 0,24,0,0`, `Theme=TextBox.Incy` (52 h, radius 14) | `Watermark = Login_Email`; `Text ↔ LoginEmail` (TwoWay) |
| 6 | `EmailError` (`:353-360`) | `Margin 0,8,0,0`, `Caption` 12, `Brush.RedText`, wrap, hidden | `Login_EmailInvalid` |
| 7 | `PasswordBox` (`:363-396`) | `Margin 0,12,0,0`, `Classes="MaskField"`, `PasswordChar="•"` (U+2022), `TextBox.Incy`; `InnerRightContent` = `TogglePasswordButton` 40×40, `Padding 0`, `Classes="IconButton"`, holding two 20×20 `PathIcon`s (`EyeOnIcon` visible, `EyeOffIcon` hidden), both `OnSurfaceVariant` | `Watermark = Login_Password` / `Login_PasswordRegister`; `Text ↔ LoginPassword`; tooltip `Login_ShowPassword`/`Login_HidePassword` |
| 8 | `RegisterPasswordHint` (`:399-406`) | `Margin 0,8,0,0`, `Caption`, `OnSurfaceVariant`, hidden | `Login_PasswordHint` |
| 9 | `ConfirmPasswordBox` (`:409-417`) | `Margin 0,12,0,0`, `MaskField`, `PasswordChar="•"`, hidden | `Watermark = Login_ConfirmPassword`; `Text ↔ RegisterConfirmPassword` |
| 10 | `ConfirmPasswordError` (`:419-426`) | `Margin 0,8,0,0`, `Caption`, `RedText`, hidden | `Login_PasswordMismatch` |
| 11 | `SiteButtonHost` Panel (`:433-460`) | `Margin 0,16,0,0`; `SiteButton` `Classes="Primary Tall"` stretch, centred content; `SiteSpinner` Ellipse 22×22 centred, `Classes="Spinner"`, hidden, origin 50%,50%, `Stroke=Brush.OnAccent`, `StrokeDashArray="6.9,20.8"`, `StrokeLineCap=Round`, `StrokeThickness=2.5` | label `Login_SubmitSignIn`; `Command=LoginSiteCmd` |
| 12 | `RegisterButtonHost` Panel (`:464-491`) | `Margin 0,16,0,0`, hidden; `RegisterSubmitButton` `Primary Tall`; `RegisterSpinner` identical to `SiteSpinner` | label `Login_CreateAccount`; `Command=RegisterCmd` |
| 13 | `PasswordlessLinks` StackPanel (`:494-514`) | `Margin 0,12,0,0`, `HorizontalAlignment=Center`, `Orientation=Horizontal`, `Spacing=8` | `MagicLinkButton` (`LinkAction`, `MagicLinkCmd`, `Login_MagicLink`) · middle dot `·` in `OnSurfaceVariant` · `ForgotPasswordButton` (`LinkAction`, `PasswordResetCmd`, `Login_ForgotPassword`) |
| 14 | `AltMethodsBlock` StackPanel (`:521-644`) | `Margin 0,24,0,0` — see §5.5 | — |
| 15 | `TwoFaBlock` StackPanel (`:647-728`) | `Margin 0,16,0,0`, hidden — see §5.6 | — |
| 16 | `ErrorLine` TextBlock (`:731-739`) | `Margin 0,16,0,0`, stretch, `Body` 14, `Brush.RedText`, centred, wrap, hidden | text set imperatively |

**Hierarchy decision recorded in the file (`:428-432`, `:516-520`):** the email/password form is the
*primary* path. Its `Войти` button is the screen's **only filled accent**. Telegram, the browser
handoff and the code fallback are **demoted below** the form under an "или" divider. This was an
explicit change from an earlier layout where the Telegram CTA sat above the form and squeezed it.

### 5.4 Segment control "Вход | Регистрация"

Track (`LoginView.axaml:72-77`, `Border.SegTrack`): height **44**, padding **4**, background
`Brush.SurfaceVariant`, `CornerRadius = Radius.Chip (12)`.

Items (`:78-96`, `Button.SegItem`): height **36**, transparent background, foreground
`Brush.OnSurfaceVariant`, `BorderThickness 0`, **`CornerRadius 8`** — chosen to be concentric with
the track (12 − 4 padding = 8, explicitly *not* an off-scale 10, `:83`), stretch, centred content,
Grotesk **14**, with `BrushTransition` on `Background` and `Foreground`, **150 ms** each.

Active state `.segActive` (`:100-104`): background `Brush.Bg`, foreground `Brush.OnSurface`,
`FontWeight SemiBold`. Hover (`:97-99`) = `Brush.Hover`; hovering the *active* tab keeps `Brush.Bg`
(`:105-107`).

Design note (`:67-71`): the active indicator is **neutral, not blue** — the single accent stays with
the CTA. Colour/background change is smooth (150 ms), **no sliding motion**; under `.lite` it is
simply instant.

Wiring: `SignInTab.Click → SetMode(false)`, `RegisterTab.Click → SetMode(true)`
(`LoginView.axaml.cs:136-137`).

### 5.5 `AltMethodsBlock` — demoted alternate methods (sign-in mode only)

1. **Divider** (`:523-539`) — `Grid ColumnDefinitions="*,Auto,*"`, two 1 px `Brush.OutlineVariant`
   rules, centre `TextBlock Classes="Caption"` `Margin 12,0`, text `{loc:T Login_Or}` ("или").
2. **`TelegramButton`** (`:545-564`) — `Margin 0,16,0,0`, stretch, `Classes="Tonal Tall"` (52 h);
   inner horizontal stack `Spacing 8`, `PathIcon 20×20` `Geo.Login.Telegram` `Brush.OnSurface`,
   label `{loc:T Common_SignInTelegram}`. Handler `OnTelegramClick`
   (`LoginView.axaml.cs:1023-1027`): clears the error, executes `LoginTelegramCmd`.
   **Enabled at all times except while polling** (`LoginView.axaml.cs:445`).
3. **`SiteBrowserButton`** (`:569-587`) — `Margin 0,14,0,0`, `HorizontalAlignment=Center`,
   `Classes="LinkAction"`, `Command=LoginBrowserCmd`; `PathIcon 18×18` globe `Brush.Accent` +
   label `{loc:T Common_SignInWebsite}`.
4. **`CodeEntryToggle`** (`:592-597`) — `Margin 0,12,0,0`, centred `LinkAction`,
   `Content={loc:T Login_ByCode}` ("Войти по коду"). Toggles `CodeEntryHost` and focuses the field
   (`LoginView.axaml.cs:1076-1084`).
5. **`CodeEntryHost`** (`:598-613`) — `Margin 0,8,0,0`, hidden by default:
   `HandoffCodeBox` (`TextBox.Incy`, `Watermark={loc:T Login_CodePaste}`, `Text ↔ HandoffCodeInput`)
   and `CodeSubmitButton` `Margin 0,8,0,0`, stretch, `Classes="Tonal Tall"`,
   `Command=LoginByCodeCmd`, `Content={loc:T Login_SubmitSignIn}`.
6. **`GoogleButton`** (`:617-643`) — `Margin 0,12,0,0`, centred `LinkAction`, foreground
   `Brush.OnSurfaceVariant`, **`IsEnabled=False`**, `ToolTip.Tip={loc:T Login_ComingSoon}`; content =
   `PathIcon 18×18` single-colour Google "G" `OnSurfaceVariant` + label `{loc:T Login_ContinueGoogle}`
   + a `Border Classes="SoonPill"` (`:110-115`: background `Brush.SurfaceVariant`, `CornerRadius 8`,
   `Padding 8,4`, centred) wrapping a `Caption` `{loc:T Login_ComingSoon}`.

   Design note (`:541-544`, `:615-616`): exactly **one** tonal alternate (Telegram); everything else is
   a quiet text link so there is no "stack of identical buttons" under the form, and Google is
   **honestly disabled** rather than faked.

### 5.6 `TwoFaBlock` — TOTP step

`StackPanel x:Name="TwoFaBlock"`, `Margin 0,16,0,0`, hidden (`:647-650`).

1. 1 px hairline `Border Background=Brush.OutlineVariant` (`:651`).
2. Prompt `TextBlock` (`:652-659`) — `Margin 0,16,0,0`, `Body` 14, `OnSurfaceVariant`, centred, wrap,
   `{loc:T Login_EnterCode}`.
3. Cell row (`:665-689`) — `Panel Margin 0,12,0,0` containing:
   * `UniformGrid x:Name="CodeCells" Columns="6"` with six `Border Classes="CodeCell"`, each holding
     one `TextBlock Classes="CodeDigit"`.
   * **`Border.CodeCell`** (`:143-155`): height **52**, `Margin 3,0`, background
     `Brush.SurfaceVariant`, border 1 px `Brush.OutlineVariant`, `CornerRadius = Radius.Chip (12)`,
     `BrushTransition` on `BorderBrush` **150 ms**.
     `.filled` → border `Brush.Outline` (`:156-158`); `.active` → border `Brush.Accent` (`:159-161`).
   * **`TextBlock.CodeDigit`** (`:162-169`): centred both axes, `Font.Numeric`,
     `FontFeatures="tnum,lnum,zero"`, **size 20**, `Brush.OnSurface`.
   * `TextBox x:Name="CodeBox"` overlaid on top (`:674-688`): `Classes="CodeInput"`,
     `Background=Transparent`, `BorderThickness=0`, `CaretBrush=Transparent`,
     `SelectionBrush=Transparent`, `Foreground=Transparent`, `Font.Numeric` + features,
     `FontSize 20`, **`MaxLength=6`**, `TextAlignment=Center`, `Theme=TextBox.Incy`,
     `Text ↔ TwoFaCode`.
     The `.CodeInput` style (`:135-138`) also forces the template border transparent, so the real
     field is invisible but still owns input, paste, IME, caret and focus — the cells are pure
     visual reflection (`:661-664`).
4. `CodeError` (`:691-699`) — `Margin 0,8,0,0`, `Caption`, `RedText`, centred, wrap, hidden,
   text `{loc:T Login_CodeIs6}`.
5. Confirm row (`:700-727`) — `Panel Margin 0,12,0,0`: `ConfirmButton` `Classes="Primary Tall"`
   stretch with label `{loc:T Login_Confirm}` and `Command=Submit2FaCmd`, plus `ConfirmSpinner`
   (22×22, identical spec to `SiteSpinner`).

**Cell rendering algorithm** — `RenderCodeCells()` (`LoginView.axaml.cs:774-793`):
```
for i in 0..5:
    cell[i].digit = i < code.Length ? code[i] : ""
    active  = CodeBox.IsFocused && code.Length < 6 && i == code.Length
    filled  = i < code.Length && !active
```
Re-run on `GotFocus`/`LostFocus` (`:154-155`) so the accent "next cell" marker only shows while
focused.

### 5.7 `AwaitingBlock` — Telegram confirmation

`StackPanel`, `Margin 0,40,0,0`, `VerticalAlignment=Top`, stretch, hidden, origin 50%,50%
(`LoginView.axaml:747-753`).

**Ring assembly** — `Panel 64×64`, centred (`:755-803`), five overlaid layers:

| Layer | Spec |
|---|---|
| Track `Ellipse` | 64×64, `Stroke=Brush.OutlineVariant`, `StrokeThickness=3` (`:759-763`) |
| `AwaitingRingFull` `Ellipse` | 64×64, **`Opacity=0`**, `Stroke=Brush.Accent`, thickness 3 — revealed only in the success beat (`:765-771`) |
| `AwaitingSpinner` `Ellipse` | 64×64, `Classes="Spinner"`, origin 50%,50%, `Stroke=Brush.Accent`, **`StrokeDashArray="16.75,50.25"`**, `StrokeLineCap=Round`, thickness 3 (`:772-781`) — dash:gap ≈ 25 % : 75 %, i.e. a **90° arc** |
| `AwaitingPlane` `PathIcon` | 26×26, `Classes="PlaneBreathe"`, centred, `Geo.Login.Telegram`, `Brush.Accent`, origin 50%,50% (`:782-791`) |
| `AwaitingCheck` `PathIcon` | 30×30, centred, `Geo.Check`, `Brush.Accent`, **`Opacity=0`**, origin 50%,50% (`:793-802`) |

`Geo.Check` (`GlobalResources.axaml:323`): `M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z`

**Copy + actions below the ring:**

| Element | Metrics | Copy |
|---|---|---|
| Title (`:806-812`) | `Margin 0,20,0,0`, **`Headline` 24** (deliberately as strong as the idle hero, `:804-805`), centred, wrap | `Login_WaitingConfirm` |
| Hint (`:813-820`) | `Margin 0,8,0,0`, `Body`, `OnSurfaceVariant`, centred, wrap | `Login_TelegramConfirmHint` |
| `OpenTelegramButton` (`:821-840`) | `Margin 0,24,0,0`, stretch, `Primary Tall` 52 h; `PathIcon 20×20` Telegram `Brush.OnAccent` + label | `Login_OpenTelegram` |
| `RestartButton` (`:842-847`) | `Margin 0,8,0,0`, centred `LinkAction` | `Login_StartOver` |
| `ChooseAnotherButton` (`:850-856`) | `Margin 0,4,0,0`, centred `LinkAction`, foreground `OnSurfaceVariant` | `Login_ChooseAnother` |

`ChooseAnotherButton` exists specifically to close the "waiting is a navigational dead end" hole
(`:848-849`): it calls `CancelLogin()` and crossfades **back to the MethodBlock inside the page**,
without leaving the screen (`LoginView.axaml.cs:1041-1045`).

**"Breathing" plane** (`LoginView.axaml:203-226`) — selector
`:is(Window):not(.lite) PathIcon.PlaneBreathe.breathing`:
`Duration 1.6 s`, `Easing = Ease.Standard`, `IterationCount Infinite`,
keyframes 0 % → `Opacity 1.0, Scale 1.0`; 50 % → `Opacity 0.55, Scale 0.94`; 100 % → back to 1.0.
It is a **second, slower rhythm over the 1.1 s arc rotation**. The `.breathing` class is attached by
code-behind **only while the awaiting block is visible and motion is on**
(`UpdateBreathe`, `LoginView.axaml.cs:532-546`), so the loop never ticks off-screen; the selector is
the second safety net.

**Local spinner style** (`LoginView.axaml:177-194`) duplicates the global one (rotation 0→360,
1.1 s, `LinearEasing`, infinite, gated by `:is(Window):not(.lite)`) but with
`RenderTransformOrigin="50%,50%"` set per-instance on each `Ellipse` (the global
`Ellipse.Spinner` style deliberately uses an absolute `0.5,0.5` no-op origin — see the warning at
`GlobalStyles.axaml:1320-1326`).

### 5.8 `EmailPendingBlock` — "message sent" pre-state

`StackPanel`, `Margin 0,40,0,0`, `VerticalAlignment=Top`, stretch, hidden (`LoginView.axaml:865-871`).

Ring: `Panel 64×64` with a `Brush.OutlineVariant` track (thickness 3), `PendingSpinner`
(same 64/`16.75,50.25`/Round/3 accent arc) and a centred `PathIcon 26×26` `Geo.Login.Mail`
`Brush.Accent` (`:872-898`). **No** full-ring/check layers here.

* `PendingTitle` — `Margin 0,20,0,0`, `Headline`, centred, wrap (`:899-905`), text set imperatively.
* `PendingHint` — `Margin 0,8,0,0`, `Body`, `OnSurfaceVariant`, centred, wrap (`:906-913`).
* `ResendButton` — `Margin 0,24,0,0`, centred `LinkAction`, `{loc:T Login_Resend}` (`:914-919`).
* `BackToSignInButton` — `Margin 0,4,0,0`, centred `LinkAction`, foreground `OnSurfaceVariant`,
  `{loc:T Login_BackToSignIn}` (`:920-926`).

`ConfigureEmailPending(kind, email)` (`LoginView.axaml.cs:650-675`) drives it:

| `PendingKind` | Title key | Hint key (formatted with the email) | Ring spins? | Resend/Back shown? |
|---|---|---|---|---|
| `Verify` | `Login_VerifyTitle` | `Login_VerifyHint` | **yes** (login is being polled) | yes |
| `Magic` | `Login_MagicSentTitle` | `Login_MagicSentHint` | no (calm static "sent") | yes |
| `Reset` | `Login_ResetSentTitle` | `Login_ResetSentHint` | no | yes |
| `Handoff` | `Login_SiteHandoff` | *(none — hint hidden)* | **yes** (code is being redeemed) | **no** (transient, self-resolving) |

`PendingSpinner.Opacity` is set to 1 only when spinning, else 0 (`:673-674`) — under reduced motion
the arc is not shown at all, leaving track + envelope.

### 5.9 `SuccessBadge`

`Border`, 64×64, centred both axes, background `Brush.Tile.Blue`, `CornerRadius = Radius.Card (20)`,
hidden, `Opacity 0`, origin 50%,50%; child `PathIcon 30×30` `Geo.Check` `Brush.Accent`
(`LoginView.axaml:932-948`).

Shown only on the success paths that never displayed the awaiting ring (site login / 2FA /
registration / verify-email / handoff) — see §8.

---

## 6. State machine

### 6.1 `LoginState` (`Account/AuthManager.cs:6-48`)

```
Idle
AwaitingTelegram(DeepLink)
Polling(DeepLink)
SiteLoading
SiteHandoffLoading
RegisterLoading
AwaitingEmailVerification(Email)
MagicLinkSent(Email)
PasswordResetSent(Email)
Success(UserProfileDto Profile)
Error(ApiError ErrorValue)
```

### 6.2 View blocks

`private enum ViewBlock { Method, Awaiting, EmailPending }` (`LoginView.axaml.cs:64-69`)
`private enum PendingKind { Verify, Magic, Reset, Handoff }` (`:71-80`)

### 6.3 `ApplyLoginState` — the complete switch (`LoginView.axaml.cs:330-427`)

| Incoming state | Block | Spinner | Error line | Extra |
|---|---|---|---|---|
| `AwaitingTelegram`, `Polling` | `Awaiting` | ring arc spins, plane breathes | cleared | `TelegramButton.IsEnabled = false` |
| `SiteLoading` | `Method` | inline arc on `SiteButton` (or on `ConfirmButton` if 2FA is up) | cleared | — |
| `SiteHandoffLoading` | `EmailPending` (kind `Handoff`) | pending ring spins | cleared | site/register busy cleared; resend + back hidden |
| `RegisterLoading` **while already on** `EmailPending` | stays on `EmailPending` | pending ring spins | cleared | "resend" case — does not jump back to the form (`:360-366`) |
| `RegisterLoading` from the form | `Method` | inline arc on `RegisterSubmitButton` | cleared | — |
| `AwaitingEmailVerification(email)` | `EmailPending` (kind `Verify`) | ring spins | cleared | — |
| `MagicLinkSent(email)` | `EmailPending` (kind `Magic`) | static | cleared | — |
| `PasswordResetSent(email)` | `EmailPending` (kind `Reset`) | static | cleared | — |
| `Success` | **stays on whatever block is visible** | all inline spinners off | cleared | `PlaySuccessBeat()` |
| `Error(e)` | `Method` | all off | set to `MessageKeyFor(e)` | if `ApiError.Unauthorized` → `FlashCredentialFields()` |
| `Idle` (default) | `Method` | all off | **untouched** — Idle arrives right after an error is shown (`:421`) | — |

`SetAwaiting(bool)` is a thin wrapper over `ShowBlock` (`:430`).

### 6.4 `ShowBlock` — transition mechanics (`LoginView.axaml.cs:437-529`)

```
_viewBlock = target;  _awaiting = target == Awaiting;
TelegramButton.IsEnabled = !_awaiting;
SetSpinning(AwaitingSpinner, _awaiting);
UpdateBreathe();
if (target != EmailPending) SetSpinning(PendingSpinner, false);

if (!_firstRenderDone || IsReducedMotion())  → SnapBlocks(target)      // instant
else if (unchanged)                          → return
else                                         → CrossfadeBlocks(in, out)
```

`CrossfadeBlocks` (`:492-529`): cancels any in-flight transition via `_blockCts`, sets the incoming
block to `Opacity 0` + `scale(0.98)` and makes **both** visible, then runs two animations
concurrently:

* incoming: `opacity 0→1`, `scale(0.98)→scale(1)`
* outgoing: `opacity 1→0`, `scale(1)→scale(0.98)`

both **`Motion.Dur.State` = 220 ms, `Ease.Standard`, `FillMode.Forward`** (`BuildScaleFade`,
`:1283-1294`). Afterwards the outgoing block is hidden and both are reset to `Opacity 1`,
`RenderTransform = null`. If a newer transition cancelled this one, the handler bails without
touching final state (`:518-521`).

**Pre-first-frame snapping is essential** (`_firstRenderDone`, `:94`, `:187-206`): Telegram login
sets the awaiting state *synchronously before the first layout* (`AccountViewModel.StartTelegramLogin`,
`:862-878`), so the very first painted frame must already be the awaiting block — never the method
block, never a self-animating entrance.

### 6.5 Mode switch (sign-in ⇄ register)

`SetMode(register)` (`:593-602`) — no-op if unchanged; otherwise clears the login error and calls
`ApplyMode()`.

`ApplyMode()` (`:609-622`) sets:
* `.segActive` on the correct tab,
* `ToolbarTitle` = `Login_TabRegister` / `Login_SignIn`,
* `TitleText` = `Login_TitleRegister` / `Login_Title`,
* `SubtitleText` = `Login_SubtitleRegister` / `Login_Subtitle`,
* `PasswordBox.Watermark` = `Login_PasswordRegister` / `Login_Password`,
then `UpdateFormVisibility()`, `UpdateSiteGate()`, `UpdateRegisterGate()`.

`UpdateFormVisibility()` (`:631-647`) — the single authority for mutually exclusive regions:

```
signInForm = !_registerMode && !_twoFaVisible

RegisterPasswordHint.IsVisible = _registerMode
ConfirmPasswordBox  .IsVisible = _registerMode
RegisterButtonHost  .IsVisible = _registerMode && !_twoFaVisible

SiteButtonHost      .IsVisible = signInForm
PasswordlessLinks   .IsVisible = signInForm
AltMethodsBlock     .IsVisible = signInForm

TwoFaBlock          .IsVisible = !_registerMode && _twoFaVisible
```

While 2FA is up, the sign-in submit and all alternates are hidden so the code step's «Подтвердить»
is the only filled accent on screen (`:626-629`).

### 6.6 2FA appearance

`Apply2Fa(tempToken)` (`:678-700`), driven by `AccountViewModel.TwoFaTempToken`:
when it becomes non-null → `UpdateFormVisibility()`, clear error, `RenderCodeCells()`, `Reveal2Fa()`,
and **focus `CodeBox`** (skipped in design mode). When it becomes null → `UpdateFormVisibility()`.

`Reveal2Fa()` (`:703-712`): `opacity 0→1` + `translateY(8px) → 0`, **300 ms `Ease.OutQuint`**;
instant before first render or under reduced motion.

---

## 7. Auth methods — end-to-end flows

Backend base URL: `https://web.departament.site/api` (`Account/BackendConfig.cs:14`).
Telegram bot username: `departamentvpnbot` (`:17`). User-Agent: `DepartamentVPN/1.0` (`:20`).
Every request carries `Accept: application/json`, `User-Agent`, `Authorization: Bearer <jwt>` when a
token exists, plus `X-HWID` (stable per-install device id), `x-device-os`, `x-ver-os`,
`x-device-model` (`Account/DepartamentApiClient.cs:42-60`). HTTP timeout **25 s** (`:30`).
JWT is 7-day and **non-refreshable** (`Account/AuthManager.cs:51-53`).

### 7.1 Telegram deep-link login

`AuthManager.BeginTelegramLogin` (`AuthManager.cs:71-140`):

1. `BackendConfig.IsConfigured()` guard → `Error(NotConfiguredError)`.
2. `POST /client/auth/telegram-login-token` → `{ token }`. Empty token → `Error(Parse)`.
3. Deep link built as **`https://t.me/{BotUsername}?start=auth_{token}`** (`:95`).
4. Emits `AwaitingTelegram(deepLink)` then immediately `Polling(deepLink)`.
5. Poll loop: `GET /client/auth/telegram-login-check?token=…` **every 2 s**, deadline **3 minutes**
   (`:99-100`). Mapping (`DepartamentApiClient.cs:78-107`): `404` → `NotYet` (keep polling),
   `410` → `Expired`, `2xx` with `confirmed && token && client` → `Confirmed`.
6. `Confirmed` → `AccountSession.OnAuthenticated(token, client)` then `Success(client)`.
   `Expired` → `Error(GoneError)`. Deadline exceeded → `Error(TimeoutError)`.
7. Cancellation token is honoured at the `Task.Delay` (`:106-111`).

UI side:
* `AccountViewModel.StartTelegramLogin` (`AccountViewModel.cs:855-881`) cancels any prior CTS, nulls
  `TwoFaTempToken`, nulls `TelegramDeepLink`, and sets `CurrentLoginState = Polling("")`
  **synchronously** as a placeholder — same awaiting UI, but with an empty link so no browser tab
  opens before the real link exists (`:872-876`).
* `ApplyLoginState` in the VM (`:1081-1095`) is what actually **opens the link**:
  on `AwaitingTelegram` it stores `TelegramDeepLink` and calls `ProcUtils.ProcessStart(deepLink)`.
* `OpenTelegramButton` reopens the same live link (`LoginView.axaml.cs:1013-1020`).
* `RestartButton` → `LoginTelegramCmd` again (fresh token, fresh link) (`:1030-1034`).
* `ChooseAnotherButton` → `CancelLogin()` → `Idle` → crossfade back to `MethodBlock` (`:1041-1045`).

`CancelLogin()` (`AccountViewModel.cs:891-906`) cancels both the Telegram CTS and the register/verify
CTS, and resets to `Idle` **only if not logged in**.

### 7.2 Email + password sign-in

`SiteButton` → `LoginSiteCmd` → `AccountViewModel.LoginSite()` (`AccountViewModel.cs:908-933`):
cancels Telegram polling, nulls `TwoFaTempToken`, sets `SiteLoading`, calls
`AuthManager.LoginSite(email, password)` → `POST /client/auth/login`.

Result mapping (`DepartamentApiClient.cs:161-170`, `Dto/AuthDtos.cs:269-277`):
* body `{token, client}` → `LoginResult.Success` → `AccountSession.OnAuthenticated` → `OnAuthenticated(client)`
* body `{requires2FA:true, tempToken}` → `LoginResult.Requires2Fa` → VM sets `TwoFaTempToken` and
  returns to `Idle` (`:920-927`) — that is what reveals `TwoFaBlock`
* `ApiError` → `LoginState.Error(e)`

Keyboard parity: Enter in `EmailBox` moves focus to the password field; Enter in `PasswordBox`
submits (sign-in mode) or moves to confirm (register mode) — `LoginView.axaml.cs:1107-1131`.
`SubmitSite()` (`:1152-1164`) re-validates before executing, so keyboard and button take one path.

`SiteButton.Click` also calls `TrimEmail()` (`:158`, `:815-826`), which writes the trimmed value back
into `LoginEmail` (the VM sends the field verbatim).

### 7.3 Registration

`RegisterSubmitButton` → `RegisterCmd` → `AccountViewModel.Register()` (`AccountViewModel.cs:1009-1022`):
cancels Telegram + previous register CTS, sets `RegisterLoading`, calls
`AuthManager.BeginRegister(email.Trim(), password, emit, ct)`.

`AuthManager.BeginRegister` (`AuthManager.cs:201-231`) → `POST /client/auth/register`:
* verification **off** → `{token, client}` → persist + `Success` (identical to email login)
* verification **on** → `{message, requiresVerification:true}` (no token) →
  `AwaitingEmailVerification(email)` then `PollUntilVerified(...)`

`PollUntilVerified` (`:239-275`): re-attempts `POST /client/auth/login` with the just-registered
credentials **every 4 s**, deadline **10 minutes**, cancellable. Failures are swallowed (still
unverified / transient). On success → persist + `Success`. On timeout it stops **quietly** and
leaves the pending screen up — no invented error (`:236-237`). `Requires2Fa` here is ignored
deliberately (a fresh account cannot have TOTP) (`:272-273`).

The verify screen's `ResendButton` re-runs `RegisterCmd` (`LoginView.axaml.cs:1058-1061`), which
re-sends the email and restarts the poll while **staying on** the pending block (`:360-366`).

### 7.4 Two-factor (TOTP)

`ConfirmButton` → `Submit2FaCmd` → `AccountViewModel.Submit2Fa()` (`AccountViewModel.cs:1061-1079`):
requires a non-empty `TwoFaTempToken`, sets `SiteLoading`, calls
`POST /client/auth/2fa-login` with `{tempToken, code}` → `AuthResult {token, client}` → persist →
null the temp token → `OnAuthenticated`. `ApiError` → `Error`.

Enter in `CodeBox` submits (`LoginView.axaml.cs:1142-1149` → `Submit2Fa()` `:1185-1192`).

### 7.5 Passwordless: magic link

`MagicLinkButton` → `MagicLinkCmd` → `RequestMagicLink()` (`AccountViewModel.cs:1025-1041`):
guards on a non-empty email containing `@` (else `Error(Unauthorized)`), cancels the register CTS,
sets `SiteLoading` **only if not already on** `MagicLinkSent` (so "send again" does not bounce back
to the form, `:1034-1039`), then `AuthManager.BeginMagicLink` → `POST /client/auth/magic-link/request`
→ emits `MagicLinkSent(email)`.

**No polling by design** (`AuthManager.cs:277-284`): the link is consumed in the system browser and
there is no in-app return callback, so a spinner could never resolve. The backend reply is
anti-enumeration (identical whether or not the address exists) — hence the conditional copy
("Если аккаунт с {0} существует…").

### 7.6 Passwordless: password reset

`ForgotPasswordButton` → `PasswordResetCmd` → `RequestPasswordReset()` (`AccountViewModel.cs:1044-1059`)
→ `AuthManager.BeginPasswordReset` (`AuthManager.cs:309-326`) → `POST /client/auth/password-reset/request`
→ `PasswordResetSent(email)`. Same no-poll rationale, same resend-stays-put guard.

### 7.7 Seamless web sign-in (browser → app SSO handoff)

This is the flow the task calls out; it has four moving parts.

**(a) Launch the browser.** `SiteBrowserButton` → `LoginBrowserCmd` → `OpenSiteLoginBrowser()`
(`AccountViewModel.cs:948-966`):

```
SiteLoginUrl = "https://departament.site/app-login"     AccountViewModel.cs:936
AppScheme    = "departamentvpn"                          AccountViewModel.cs:939
url = $"{SiteLoginUrl}?return={AppScheme}://auth"        AccountViewModel.cs:957
ProcUtils.ProcessStart(url)
```
Any in-flight `Error` state is reset to `Idle` first. A launch failure publishes a
`Common_SomethingWrong` snack.

The scheme name is chosen to satisfy the **site's existing safe-return allowlist**
`^departament[a-z0-9]*$`, so no site/backend change is required (`AccountViewModel.cs:941-947`,
`App.axaml.cs:150-152`).

**(b) The site mints a one-time code.** A logged-in web session at `/app-login` redirects the browser
to `departamentvpn://auth?code=…`.

**(c) The OS routes it back.**
* Windows: the scheme is registered per-user, **no admin**, at
  `HKCU\Software\Classes\departamentvpn` with `URL Protocol`, `DefaultIcon` and
  `shell\open\command = "<exe>" "%1"` (`App.axaml.cs:161-190`). Non-Windows skips registration.
* The app is single-instance, so a scheme launch while running spawns a throwaway process. That
  process forwards the URL to the live instance over a **per-exe named pipe**
  (`departamentvpn-<md5(exePath)>` on Windows, `departamentvpn-v2rayN` elsewhere) and exits
  (`Program.cs:33`, `:112-133`, `AppHandoffChannel`).
* A URL arriving before the handler is wired (cold start) is buffered and drained on
  `SetHandler` (`Program.cs` `AppHandoffChannel.SetHandler`).
* `ParseHandoffCode` (`App.axaml.cs:212-238`) takes the substring after the first `?` or `#`, splits
  on `&`/`#`, and matches the pair whose key is **exactly** `code` (case-insensitive) — deliberately
  not `IndexOf("code=")`, which would also match `barcode=`.

**(d) Redeem.** `MainWindow.HandleAuthCallback(code)` (`MainWindow.axaml.cs:1081-1094`):
`ShowHideWindow(true)` + `Activate()`; **ignore** if already logged in; open `LoginView` if it isn't
the top sub-page; then `_accountVm.CompleteAppHandoff(code)`.

`CompleteAppHandoff` (`AccountViewModel.cs:975-999`): trims, no-ops on empty, cancels Telegram +
register polls, sets `SiteHandoffLoading`, calls
`POST /client/auth/app-handoff/consume` with `{code}` → `AuthResult` → persist → clear
`HandoffCodeInput` → `OnAuthenticated(profile)`. Failure → `Error(e)` (an expired code surfaces as
`GoneError` → "Ссылка устарела, начните заново").

**Manual fallback.** `CodeEntryToggle` reveals `HandoffCodeBox`; the button or Enter runs
`LoginByCodeCmd` = `CompleteAppHandoff(HandoffCodeInput)` (`AccountViewModel.cs:343`,
`LoginView.axaml.cs:1087-1094`) — the identical redemption path, for platforms where the scheme
callback does not fire.

**UI during redemption:** `SiteHandoffLoading` shows the `EmailPending` block with kind `Handoff` —
spinning ring, envelope glyph, title «Завершаем вход через сайт…», **no hint, no resend, no back**
(transient and self-resolving) — then `Success` plays the badge beat over it exactly like an email
login (`LoginView.axaml.cs:347-356`, `:664-669`).

**Also available but not surfaced in Login UI:** `POST /client/auth/app-handoff` mints a code from an
authenticated app session so the user can land already-signed-in on the site
(`DepartamentApiClient.cs:155-156`) — used elsewhere (web cabinet), not on this screen.

### 7.8 Google

Not implemented in the UI. `GoogleButton` is permanently `IsEnabled=False` with a "Скоро" pill and
tooltip (`LoginView.axaml:617-643`). The API method exists (`POST /client/auth/google`,
`DepartamentApiClient.cs:118-119`) but no command binds it.

---

## 8. Success beat and the handoff gate

The screen **never pops immediately on success** — it plays a confirmation beat first, then releases
`BackRequested` (`LoginView.axaml.cs:19-23`, `:274-281`, `:900-1008`).

Flags: `_loggedIn`, `_beatStarted`, `_beatDone`, `_handoffFired`, `_detached` (`:106-110`).

```
OnLoggedIn()              // AccountViewModel.IsLoggedIn → true (distinct, once)
  _loggedIn = true
  if (!_beatStarted) PlaySuccessBeat()      // defensive
  TryHandoff()

TryHandoff()
  if (_handoffFired || _detached || !_loggedIn || !_beatDone) return
  _handoffFired = true
  BackRequested?.Invoke(...)
```
(`:907-927`)

`PlaySuccessBeat()` (`:930-958`) runs once, in a `try/catch/finally`; the `finally` always sets
`_beatDone = true` and calls `TryHandoff()`, so a user is never stranded on the login page after a
real success.

**Path A — success on the awaiting ring** (`PlayAwaitingSuccess`, `:961-989`), used when `_awaiting`:

| t (ms) | Action | Duration | Easing |
|---|---|---|---|
| 0 | stop arc rotation, remove `.breathing` | — | — |
| 0 | `AwaitingSpinner` opacity 1→0 **and** `AwaitingRingFull` opacity 0→1 (dashed arc completes into a full ring) | 220 (`Dur.State`) | `OutQuint` |
| 160 | `AwaitingPlane` opacity 1→0 | 160 (`Dur.PressOut`) | `OutQuint` |
| 160 | `AwaitingCheck` opacity 0→1 + `scale(0.9)→scale(1)` | 160 | `OutQuint` |
| ~320 | hold | 120 | — |

Total ≈ **440 ms**. Under reduced motion: snap (`spinner 0`, `full ring 1`, `plane 0`, `check 1`) plus
a **120 ms** delay so the confirmation frame still exists (`:966-975`).

**Path B — success without the awaiting block** (`PlayBadgeSuccess`, `:993-1008`), used for site
login / 2FA / registration / verify-email / handoff:

| Action | Duration | Easing |
|---|---|---|
| currently visible block fades `opacity → 0` (fire-and-forget) | 160 (`Dur.PressOut`) | `Standard` |
| `SuccessBadge` fades in `opacity 0→1` + `scale(0.9)→scale(1)` | 220 (`Dur.State`) | `OutQuint` |
| hold | 120 | — |

Total ≈ **340 ms**. Reduced motion: snap + 120 ms.

**What catches the frame after the pop:** `AccountViewModel.OnAuthenticated` (`:1098-1119`) sets
`IsLoggedIn = true` **and** `IsImportingAccount = true` in the *same* UI tick, before any await
(`:1103-1105`), so the sync overlay is already up when `LoginView` closes — no empty onboarding
flash. Then `RunSyncPhases(includeSubFetch: true)`; on failure `SyncFailed` is raised and the overlay
stays up on a retry surface instead of resolving into a false success.

**The sync overlay** (`Views/AccountSyncView.axaml`) is the destination: full-screen
`Brush.HomeGradient`, gutter 16, column `MaxWidth 400` centred; the **same 64 ring** (track
`OutlineVariant` 3 + accent arc `16.75,50.25` `.spinning`) but with the **shield** glyph 30×30
centred instead of a plane (`:64-92`); `Headline` `Account_SyncTitle` at `Margin 0,24,0,0`; a live
one-line stage line (`Subtitle`, `TextTrimming=CharacterEllipsis`) at `Margin 0,8,0,0`. Error state
crossfades in place to the same ring **without** the arc plus a red outline warning glyph,
`Account_SyncErrorTitle` / `Account_SyncErrorHint`, and `Повторить` (`Primary`) /
`Войти заново` (`Tonal`) at `Margin 0,24,0,0` / `0,12,0,0` (`:113-172`).

---

## 9. Validation gates

### 9.1 Email

`private static readonly Regex _emailRegex = new(@"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$", RegexOptions.Compiled);`
(`LoginView.axaml.cs:28`) — the deliberate analogue of Android's `Patterns.EMAIL_ADDRESS`.

`IsEmail(v) = v.Length > 0 && _emailRegex.IsMatch(v)` (`:810`).

### 9.2 `UpdateSiteGate()` (`:718-729`)

```
email = LoginEmail?.Trim() ?? ""
password = LoginPassword ?? ""
EmailError.IsVisible       = email.Length > 0 && !IsEmail(email)   // only after the user typed
SiteButton.IsEnabled       = !_siteBusy && IsEmail(email) && password.Length > 0
MagicLinkButton.IsEnabled  = IsEmail(email)      // no password needed
ForgotPasswordButton.IsEnabled = IsEmail(email)
```

### 9.3 `UpdateRegisterGate()` (`:731-747`)

```
EmailError.IsVisible           = email.Length > 0 && !IsEmail(email)
ConfirmPasswordError.IsVisible = _registerMode && confirm.Length > 0 && confirm != password
RegisterSubmitButton.IsEnabled = !_registerBusy && IsEmail(email)
                                 && password.Length >= 8
                                 && confirm.Length > 0 && confirm == password
```

**Password minimum: 8 characters.** Same threshold enforced again in `SubmitRegister()` (`:1177`).

### 9.4 `Update2FaGate()` (`:753-767`)

Non-digit characters are **stripped at the source**: if `code` contains anything non-digit, the VM
property is rewritten with digits only and the method returns (the re-notification re-enters)
(`:757-762`). Then:

```
CodeError.IsVisible    = code.Length > 0 && !IsSixDigits(code)
ConfirmButton.IsEnabled = !_siteBusy && IsSixDigits(code)
RenderCodeCells()
```
`IsSixDigits(v) = v.Length == 6 && v.All(char.IsDigit)` (`:812`).

### 9.5 Busy gating

`SetSiteBusy(busy)` (`:552-577`):
* `onSite = busy && !_twoFaVisible`, `on2Fa = busy && _twoFaVisible` — the spinner lands on whichever
  button initiated the request.
* **Under reduced motion the spinner is skipped entirely** and the label stays visible (`:560-573`):
  a frozen dashed ring with a hidden label would read as broken; the disabled/dimmed button already
  conveys "busy".
* Re-runs both gates.

`SetRegisterBusy(busy)` (`:580-590`) — same pattern for `RegisterSpinner`/`RegisterButtonLabel`.

---

## 10. Error paths

### 10.1 `ApiError` hierarchy (`Account/ApiError.cs`)

`NotConfiguredError`, `NetworkError`, `TimeoutError`, `Unauthorized(detail?)`, `NotFoundError`,
`GoneError`, `RateLimited`, `ServiceUnavailable`, `Server(code, detail?)`, `Parse`.

HTTP mapping (`DepartamentApiClient.cs:414-426`):
`401 → Unauthorized`, **`403 → Server(403)` (explicitly NOT Unauthorized**, so callers never wipe a
live session), `404 → NotFoundError`, `410 → GoneError`, `429 → RateLimited`,
`502/503 → ServiceUnavailable`, anything else → `Server(code)`.
Transport: `TaskCanceled`/`OperationCanceled → TimeoutError`, `HttpRequestException → NetworkError`
(`:396-412`).
Error bodies are sanitised before being attached: any line containing `token`, `authorization`,
`http://` or `https://` is dropped, then capped at 300 chars (`:428-447`).

### 10.2 `MessageKeyFor` — error → string key (`LoginView.axaml.cs:887-898`)

| `ApiError` | Key | RU |
|---|---|---|
| `Unauthorized` (401/403 on login ⇒ nearly always bad credentials) | `Login_ErrBadCreds` | Неверный email или пароль |
| `Server { Code: 409 }` (registration hit an existing account) | `Login_ErrEmailTaken` | Аккаунт с этой почтой уже существует |
| `GoneError` | `Login_ErrLinkExpired` | Ссылка устарела, начните заново |
| `ServiceUnavailable` | `Common_ServiceUnavailable` | Сервис временно недоступен |
| `NetworkError` or `TimeoutError` | `Common_NetworkError` | Ошибка сети. Проверьте подключение |
| `NotConfiguredError` | `Login_ErrUnavailable` | Вход недоступен |
| anything else | `Login_ErrRetry` | Что-то пошло не так, попробуйте снова |

### 10.3 Error line behaviour (`SetLoginError` / `UpdateErrorLine`, `:830-867`)

* The login-flow **key** (not the resolved text) is stored in `_loginErrorKey` so the line
  re-translates live on a language switch (`:83-84`, `:309-320`).
* Priority: login-flow key **over** the VM's generic `ErrorText` (`:840`).
* Reveal only on the transition hidden → shown (`_errorShown`, `:99`, `:844-852`) so changing the
  text or the language does not replay the animation.
* Reveal animation: `opacity 0→1` + `translateY(-4px) → 0`, **220 ms (`Dur.State`) `Ease.Standard`**
  (`:866`). Instant before first render / under reduced motion.

### 10.4 Credential flash (`FlashCredentialFields`, `:873-884`)

On `ApiError.Unauthorized` only: add class `.fieldError` to **both** `EmailBox` and `PasswordBox`,
remove it after **220 ms** (`Motion.Dur.State`).

The `.fieldError` style (`LoginView.axaml:122-124`) recolours the inner `PART_BorderElement` to
`Brush.Red`. It is declared at `UserControl` level precisely so it out-ranks the ControlTheme's
`:focus`/`:pointerover` rules and the flash is visible even on the focused field (`:117-121`).
Return to `OutlineVariant`/`Accent` rides the template's own 150 ms `BrushTransition`.
**Colour only — no shake, no bounce** (`:121`, `:871`).

---

## 11. Entrance choreography of the login screen

Constructor pre-hides every `MethodBlock` child (`opacity = 0`) when motion is enabled
(`LoginView.axaml.cs:162-169`); `_entryPending = true`.

`OnFirstLoaded` (`:187-206`): sets `_firstRenderDone = true`; if reduced motion **or** the screen
opened straight into the awaiting state **or** `MethodBlock` is not visible, it just calls
`RestoreMethodChildren()` (`:1271-1278`) — no stagger; otherwise `PlayEntryStagger()`.

`BeatDelayMs` (`LoginView.axaml.cs:1219-1225`):

| Child index | Delay | Beat |
|---|---|---|
| 0 | 0 ms | shield mark |
| 1, 2, 3 | 60 ms | identity (wordmark + title + subtitle) |
| 4 | 120 ms | «Вход / Регистрация» segment |
| 5 … 16 | 180 ms | the entire form + demoted alternates as **one group** |

Per-child reveal = `PlayReveal` (`:1229-1268`), identical to the onboarding one: **300 ms
`Ease.OutQuint`**, `FillMode.None`, child 0 `scale(0.9)→scale(1)`, all others
`translateY(8px)→translateY(0)`, safety timer at `delay + 300 + 250 ms`, `finally` restores
`Opacity = 1; RenderTransform = null`. Total ≈ **480 ms**.

`FillMode.None` + explicit base restoration is required so the animation does not shadow the buttons'
`:pressed` scale afterwards (`:1227-1228`).

---

## 12. Keyboard, focus, and other interaction details

| Behaviour | Code |
|---|---|
| Enter in email → focus password | `LoginView.axaml.cs:1107-1114` |
| Enter in password → `SubmitSite()` (sign-in) / focus confirm (register) | `:1116-1131` |
| Enter in confirm-password → `SubmitRegister()` | `:1133-1140` |
| Enter in 2FA code → `Submit2Fa()` | `:1142-1149` |
| Enter in handoff-code field → `LoginByCodeCmd` | `:1087-1094` |
| 2FA auto-focus on appearance | `:690-693` |
| Handoff-code field auto-focus on expand | `:1080-1083` |
| Password reveal toggle | `:1096-1103` — flips `PasswordBox.RevealPassword`, swaps `EyeOnIcon`/`EyeOffIcon`, updates the tooltip between `Login_ShowPassword` / `Login_HidePassword` |
| Password mask glyph | `PasswordChar="•"` (U+2022, the light bullet — chosen over the heavy `●` U+25CF) plus `LetterSpacing 2` applied **only to `PART_TextPresenter`** via `TextBox.MaskField` so the dots breathe without shifting the email field or watermarks (`LoginView.axaml:126-131`) |
| Live language re-application | `ApplyLanguage()` (`:309-320`) re-runs `UpdateErrorLine`, the eye tooltip, `ApplyMode()`, and (if visible) `ConfigureEmailPending` |
| Live reduced-motion re-application | `MotionState.Changed → UpdateBreathe` (`:285-287`) |

Subscription lifecycle: `Rebind()` on `DataContextChanged` and `AttachedToVisualTree`,
`Unbind()` + `_detached = true` on `DetachedFromVisualTree` (`:173-179`).
`CurrentLoginState` is delivered **inline when already on the UI thread**, and only posted otherwise
(`:227-239`) — this is what makes the pre-first-frame awaiting snap possible.

---

## 13. Complete copy table

All from `Common/L.Account.cs`, `L.Common.cs`, `L.Home.cs`. Format: `Add(key, ru, en)`.
`L.T(key)` = plain, `L.F(key, args)` = `string.Format` with the current UI culture
(`Common/L.cs:135-140`), `{loc:T Key}` in XAML binds a live per-key observable so switching language
re-pushes every open binding (`L.cs:248-332`). Language default `ru` (`L.cs:49`).

### 13.1 Login screen (`L.Account.cs:124-205`)

| Key | RU | EN | Line |
|---|---|---|---|
| `Login_SignIn` | Вход | Sign in | 125 |
| `Login_Title` | Вход в departament | Sign in to departament | 126 |
| `Login_Subtitle` | Войдите по email и паролю — или через Telegram в один тап. | Sign in with your email and password — or with Telegram in one tap. | 127-129 |
| `Login_Or` | или | or | 130 |
| `Login_Email` | Электронная почта | Email | 131 |
| `Login_EmailInvalid` | Введите корректный email, например name@example.com | Enter a valid email, for example name@example.com | 132-134 |
| `Login_Password` | Пароль | Password | 135 |
| `Login_ShowPassword` | Показать пароль | Show password | 136 |
| `Login_HidePassword` | Скрыть пароль | Hide password | 137 |
| `Login_EnterCode` | Введите 6-значный код из приложения | Enter the 6-digit code from your app | 138-140 |
| `Login_CodeIs6` | Код состоит из 6 цифр | The code is 6 digits | 141 |
| `Login_Confirm` | Подтвердить | Confirm | 142 |
| `Login_SignUp` *(registered, unused)* | Регистрация на сайте | Sign up on the website | 143 |
| `Login_WaitingConfirm` | Ожидаем подтверждения в Telegram | Waiting for Telegram confirmation | 144-146 |
| `Login_TelegramConfirmHint` | Подтвердите вход в открывшемся приложении и вернитесь сюда — остальное сделаем сами. | Confirm the sign-in in the app that opened, then come back here — we'll take care of the rest. | 147-149 |
| `Login_OpenTelegram` | Открыть Telegram | Open Telegram | 150 |
| `Login_StartOver` | Начать заново | Start over | 151 |
| `Login_ChooseAnother` | Другой способ входа | Use another method | 152 |
| `Login_TabSignIn` | Вход | Sign in | 155 |
| `Login_TabRegister` | Регистрация | Register | 156 |
| `Login_TitleRegister` | Создайте аккаунт | Create your account | 157 |
| `Login_SubtitleRegister` | Зарегистрируйтесь по email — или войдите через Telegram в один тап. | Register with your email — or sign in with Telegram in one tap. | 158-160 |
| `Login_PasswordRegister` | Пароль (не менее 8 символов) | Password (at least 8 characters) | 161 |
| `Login_PasswordHint` | Минимум 8 символов | At least 8 characters | 162 |
| `Login_ConfirmPassword` | Повторите пароль | Repeat password | 163 |
| `Login_PasswordMismatch` | Пароли не совпадают | The passwords don't match | 164 |
| `Login_CreateAccount` | Создать аккаунт | Create account | 165 |
| `Login_MagicLink` | Войти по ссылке | Sign in with a link | 166 |
| `Login_ForgotPassword` | Забыли пароль? | Forgot password? | 167 |
| `Login_ContinueGoogle` | Продолжить с Google | Continue with Google | 168 |
| `Login_ComingSoon` | Скоро | Soon | 169 |
| `Login_SubmitSignIn` | Войти | Sign in | 173 |
| `Login_ByCode` | Войти по коду | Sign in with a code | 175 |
| `Login_CodePaste` | Вставьте код из браузера | Paste the code from your browser | 176 |
| `Login_SiteHandoff` | Завершаем вход через сайт… | Finishing sign-in via the website… | 178 |
| `Login_VerifyTitle` | Подтвердите почту | Confirm your email | 181 |
| `Login_VerifyHint` | Мы отправили ссылку на **{0}**. Откройте её, чтобы подтвердить вход — остальное сделаем сами. | We've sent a link to {0}. Open it to confirm your sign-in — we'll take care of the rest. | 182-184 |
| `Login_MagicSentTitle` | Ссылка отправлена | Link sent | 185 |
| `Login_MagicSentHint` | Если аккаунт с **{0}** существует, мы отправили ссылку для входа. Откройте её в браузере. | If an account for {0} exists, we've sent a sign-in link. Open it in your browser. | 186-188 |
| `Login_ResetSentTitle` | Письмо отправлено | Email sent | 189 |
| `Login_ResetSentHint` | Если аккаунт с **{0}** существует, мы отправили ссылку для сброса пароля. Задайте новый пароль и вернитесь ко входу. | If an account for {0} exists, we've sent a password-reset link. Set a new password, then return to sign in. | 190-192 |
| `Login_Resend` | Отправить снова | Send again | 193 |
| `Login_BackToSignIn` | Вернуться ко входу | Back to sign in | 194 |
| `Login_ErrBadCreds` | Неверный email или пароль | Incorrect email or password | 197 |
| `Login_ErrLinkExpired` | Ссылка устарела, начните заново | The link has expired, start over | 198 |
| `Login_ErrUnavailable` | Вход недоступен | Sign-in is unavailable | 199 |
| `Login_ErrEmailTaken` | Аккаунт с этой почтой уже существует | An account with this email already exists | 200-202 |
| `Login_ErrRetry` | Что-то пошло не так, попробуйте снова | Something went wrong, try again | 203-205 |

### 13.2 Shared (`L.Common.cs`)

| Key | RU | EN | Line |
|---|---|---|---|
| `Common_Back` | Назад | Back | 14 |
| `Common_AddFromClipboard` | Добавить из буфера обмена | Add from clipboard | 27 |
| `Common_AddViaQr` | Добавить по QR-коду | Add via QR code | 28 |
| `Common_SignInTelegram` | Войти через Telegram | Sign in with Telegram | 33 |
| `Common_SignInWebsite` | Войти через сайт | Sign in via website | 34 |
| `Common_ServiceUnavailable` | Сервис временно недоступен | Service is temporarily unavailable | 51 |
| `Common_NetworkError` | Ошибка сети. Проверьте подключение | Network error. Check your connection | 52 |
| `Common_SignInRequired` | Требуется вход в аккаунт | Sign-in required | 53 |

### 13.3 Onboarding (`L.Home.cs:39-41`, `L.Account.cs:209`)

| Key | RU | EN |
|---|---|---|
| `Onboarding_Title` | Добавьте подписку | Add a subscription |
| `Onboarding_Subtitle` | Отсканируйте QR-код или вставьте ссылку из буфера — доступ появится сразу. | Scan a QR code or paste a link from the clipboard — access appears right away. |
| `Onboarding_OrSignInShort` | или войдите в аккаунт | or sign in to your account |
| `Onboarding_OrSignIn` *(registered, unused)* | Или войдите в свой аккаунт | Or sign in to your account |

### 13.4 Post-login sync overlay (`L.Account.cs:212-224`)

`Account_SyncTitle` "Добавляем аккаунт" · `Account_SyncStageAccount` "Проверяем аккаунт" ·
`Account_SyncSubtitle` "Загружаем подписки…" · `Account_SyncStageServers` "Обновляем серверы" ·
`Account_SyncErrorTitle` "Не удалось синхронизировать" ·
`Account_SyncErrorHint` "Проверьте соединение и попробуйте снова." ·
`Account_SyncRetry` "Повторить" · `Account_SyncReLogin` "Войти заново".

**Voice rules visible throughout:** Russian sentence-case, no trailing period on button labels and
status lines, active verbs, em-dash clauses for the second half of a sentence, `…` for in-progress
states. The brand word `departament` is a literal and is never translated
(`LoginView.axaml:298-299`, `OnboardingView.axaml:76`).

---

## 14. Backend surface used by these screens

`Account/BackendConfig.cs:33-51`:

| Endpoint | Method | Body → Response |
|---|---|---|
| `/client/auth/telegram-login-token` | POST `{}` | `{token}` |
| `/client/auth/telegram-login-check?token=` | GET | 404 NotYet / 410 Expired / 200 `{confirmed, token, client, justCreated}` |
| `/client/auth/login` | POST `{email,password}` | `{token,client}` \| `{requires2FA,tempToken}` |
| `/client/auth/2fa-login` | POST `{tempToken,code}` | `{token,client}` |
| `/client/auth/google` | POST `{idToken,referralCode?}` | `{token,client}` *(no UI)* |
| `/client/auth/me` | GET | `UserProfileDto` |
| `/client/auth/register` | POST `{email,password,referralCode?}` | `{token,client}` \| `{message,requiresVerification:true}` |
| `/client/auth/verify-email` | POST `{token}` | auth body |
| `/client/auth/magic-link/request` | POST `{email}` | `{message,expiresInMinutes?}` |
| `/client/auth/magic-link/consume` | POST `{token,referralCode?}` | auth body |
| `/client/auth/password-reset/request` | POST `{email}` | `{message}` |
| `/client/auth/password-reset/consume` | POST `{token,newPassword}` | `{message}` |
| `/client/auth/app-handoff` | POST `{}` | `{code,expiresAt}` |
| `/client/auth/app-handoff/consume` | POST `{code}` | `{token,client}` |

`UserProfileDto` (`Dto/AuthDtos.cs:299-403`) fields relevant to login:
`id, email, balance, currency|preferredCurrency, telegramLinked, googleLinked, appleLinked,
hasPassword, telegramId, telegramUsername, telegramName (+6 alias spellings), referralCode,
remnawaveUuid, trialUsed, autoRenewEnabled, totpEnabled, avatarUrl (+7 alias spellings)`.

Session persistence: `AccountSession.OnAuthenticated(jwt, profile)` →
`AuthTokenStore.SaveSession(jwt, user: profile)` + state `LoggedIn`
(`Account/AccountSession.cs:88-92`).
Display-name precedence used by the chip and the Account screen: `@telegramUsername` →
`telegramName` → `email` (`:68-83`).
`Wipe()` stops the core and clears the system proxy **before** deleting managed subscriptions, then
clears the token (`:109-135`) — it is called only on an explicit logout or a confirmed 401 from the
identity endpoint, never on a 403 (`:104-108`).

---

## 15. Android port — gap analysis and checklist

### 15.1 What Android has today

| Android file | Lines | State |
|---|---|---|
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/LoginActivity.kt` | 415 | Telegram + email/password + 2FA only |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/activity_login.xml` | 314 | two stacked sections ("Вход через Telegram" / "Вход через сайт"), single 2FA text field |
| `/home/user/dp/V2rayNG/app/src/main/res/values/strings_auth.xml` | 44 | 28 strings; no register/magic/reset/handoff copy |
| `/home/user/dp/V2rayNG/app/src/main/res/layout/layout_home_empty.xml` | 139 | the current "empty" surface |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/auth/AuthManager.kt` | 119 | `LoginState` = `Idle / AwaitingTelegram / Polling / SiteLoading / Success / Error` **only** |
| `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/auth/BackendConfig.kt` | 87 | auth endpoints stop at `telegramLoginToken / telegramLoginCheck / login / twoFaLogin / googleLogin / me` |

### 15.2 What must be added on Android

**Data layer**
1. `BackendConfig.Endpoints`: add `register`, `verifyEmail`, `magicLinkRequest`, `magicLinkConsume`,
   `passwordResetRequest`, `passwordResetConsume`, `appHandoff`, `appHandoffConsume`
   (paths in §14).
2. `DepartamentApiClient` + impl: add `register`, `verifyEmail`, `requestMagicLink`,
   `consumeMagicLink`, `requestPasswordReset`, `consumePasswordReset`, `createAppHandoff`,
   `consumeAppHandoff`; add DTOs `RegisterRequest/ResponseDto`, `TokenRequestDto`,
   `EmailRequestDto`, `MagicLinkConsumeRequestDto`, `PasswordResetConsumeRequestDto`,
   `CodeRequestDto`, `MessageResponseDto`, `AppHandoffDto`, and result types
   `RegisterResult.{Success,RequiresVerification}`.
3. `AuthManager.LoginState`: add `SiteHandoffLoading`, `RegisterLoading`,
   `AwaitingEmailVerification(email)`, `MagicLinkSent(email)`, `PasswordResetSent(email)`.
4. `AuthManager`: add `beginRegister` (with the 4 s / 10 min verified-poll), `beginMagicLink`,
   `beginPasswordReset`, `consumeAppHandoff`.
5. Error mapping must keep **403 ≠ Unauthorized** and add the `Server(409) → email taken` case.

**Platform**
6. Register the `departamentvpn://auth` deep link. **It does not exist today**: the only VIEW
   schemes in `/home/user/dp/V2rayNG/app/src/main/AndroidManifest.xml` are `v2rayng`
   (`:176`, hosts `install-config`/`install-sub`) and `depv` (`:188`), both on the exported
   `UrlSchemeActivity` (`:161-190`); `LoginActivity` is declared `android:exported="false"`
   (`:122-124`) and therefore cannot receive a browser intent as-is.
   Two workable shapes: (a) add a third `<intent-filter>` with
   `<data android:scheme="departamentvpn" android:host="auth"/>` to the already-exported
   `UrlSchemeActivity` and forward the parsed code inward, or (b) export the login activity with its
   own filter. Either way, parse the `code` query param with the **exact-key** rule of
   `App.axaml.cs:212-238` (never a bare `IndexOf("code=")`). Android is much simpler than desktop
   here — no named pipe, no single-instance forwarding; `singleTask` + `onNewIntent` covers it.
7. Keep the manual "Войти по коду" fallback anyway (§7.7 d) — it is the safety net.

**UI**
8. Rebuild the login screen to the three-block z-stack of §5 with the segment control, the demoted
   alternates, the 6-cell 2FA row and the pending screen.
9. Rebuild the first-run screen to §4 (single accent = QR, website demoted to a text link).

### 15.3 Token mapping (desktop → Android)

The Android token names already exist and line up 1:1 — this is a rename table, not a redesign:

| Desktop | Android |
|---|---|
| `Space.4/8/12/16/24/32` | `@dimen/space_4 … space_32` (`res/values/dimens.xml:14-19`) |
| `Radius.Chip 12` / `Radius.Tile 12` | `@dimen/radius_chip` / `@dimen/radius_tile` (`dimens.xml:22-24`) |
| `Radius.Card 20` | `@dimen/radius_card` |
| `Radius.Pill 100` | `@dimen/radius_pill` (`:26`) |
| `Radius.Sheet 24` | `@dimen/radius_sheet` (`:28`) |
| `Radius.Search 14`, `Radius.Button 16` | **missing — add** `radius_field 14dp`, `radius_button 16dp` |
| `Size.Row 56` | `@dimen/row_min_height` (`:33`) |
| `Size.Tile 40` / `Size.Glyph 22` | `@dimen/tile_size` / `@dimen/tile_glyph` (`:31-32`) |
| `Size.CtaTall 52`, `Size.SubToolbar 56`, `Size.SegmentChip 44`, `Size.EmptyIcon 64` | **missing — add** |
| `Brush.Accent #4C8DFF` | `@color/icon_blue` is already `#4C8DFF` (`colors.xml:19`) — but `md_theme_primary` is `#1E5FC7` (`:53`), so the accent must be reconciled to the single Incy blue |
| `Brush.Tile.Blue` (20 %) | `@color/icon_tile_blue #334C8DFF` (`colors.xml:38`) — note desktop is 20 % (`#33` ≈ 20 %) ✔ |
| `Brush.RedText` | **missing — add** `#FF6069` (night) / `#C42B32` (day, = `ping_bad` at `colors.xml:36`) |
| Type classes | `TextAppearance.App.{Display,Headline,Title,Body,Subtitle,Caption,Chip}` per `/home/user/dp/CLAUDE.md` |

### 15.4 Implementation checklist (ordered)

1. **Background** — one radial gradient drawable shared by both screens
   (`#1B2D50 → #0E141F @55 % → #0A0B0D`, centre 50 %/30 %, radius 75 %).
2. **Column** — `max_width 440dp`, gutter 16dp, `ScrollView` whose child has
   `android:fillViewport="true"` so the onboarding column centres when it fits and scrolls when it
   doesn't (desktop's `MinHeight = viewport` trick).
3. **Toolbar** — 56dp, background = window background, back button 40dp circle with a 22dp glyph,
   title `Headline` 24sp bold, ellipsised. No divider, no elevation.
4. **Shield tile** — 64dp square, 20dp radius, `icon_tile_blue` fill, 30dp accent glyph. Same
   drawable on both screens; it is the continuity element across the push transition.
5. **Segment control** — 44dp track / 4dp padding / `radius_chip 12`; items 36dp / radius 8dp;
   colour-only 150 ms crossfade; **neutral** active pill (surface colour, not accent).
6. **Fields** — 52dp min height, `radius_field 14`, `SurfaceVariant` fill, 1dp `OutlineVariant`
   stroke → `Outline` on hover/press → `Accent` on focus, 150 ms colour transition, 15sp text,
   `OnSurfaceVariant` hint. Password mask `•` with 2sp letter spacing on the text only.
7. **CTAs** — filled 52dp `radius_button 16` accent with `OnAccent` label, Grotesk 15sp Bold; tonal
   52dp `SurfaceHighest`/`OnSurface` 15sp Medium; link action 40dp, accent, 14sp Medium. Press
   scale 0.97 @120 ms `PathInterpolator(0.25,1,0.5,1)`.
8. **2FA row** — six 52dp cells, 3dp horizontal margin, `radius_chip 12`, `SurfaceVariant` fill,
   1dp border (`OutlineVariant` rest / `Outline` filled / `Accent` next-to-type), a monospaced-digit
   20sp glyph, and one **invisible** real `EditText` (maxLength 6, digits-only filter, centred,
   transparent text/cursor/handles) overlaid to own IME + paste + autofill (`autofillHints="smsOTPCode"`
   is the Android upgrade over the desktop original).
9. **Three blocks in a `FrameLayout`** (Method / Awaiting / EmailPending) so the 220 ms
   crossfade + 0.98 scale has no empty frame, plus a 64dp success badge on top.
10. **Spinners** — 90° arc, 1.1 s linear rotation. 64dp ring stroke 3dp for the awaiting/pending
    states; 22dp stroke 2.5dp inline on buttons. Under reduced motion the inline spinner is **not
    shown at all** and the label stays.
11. **Motion** — the four interpolators of §3 as `res/interpolator/*.xml`; the two stagger beat maps
    (login `0/60/120/180`, onboarding `0/60/140/200`), 300 ms reveals, 220 ms state changes, 160 ms
    press-outs, 120 ms holds.
12. **Reduced motion** — respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and/or the app's own
    lite toggle; every animation must have an instant fallback that still leaves the confirmation
    frame visible for ~120 ms.
13. **Success gate** — do **not** finish the activity on success; play the beat first, and raise the
    "importing account" flag in the same frame as "logged in" so the sync screen is already up when
    login closes.
14. **Shell gate** — port `(_isEmpty && !_isLoggedIn)`: never show the "add a subscription" screen to
    a signed-in user, and treat "not yet loaded" as *unknown*, not as *empty*.

### 15.5 Design decisions that must survive the port

These are explicit, documented choices in the desktop source — porting them is the point:

* **One filled accent per screen.** Login: `Войти` (or `Создать аккаунт`, or `Подтвердить` during
  2FA). Onboarding: `Добавить по QR-коду`. Everything else is tonal or a text link
  (`LoginView.axaml:428-432, 541-544, 615-616`; `OnboardingView.axaml:32-35`).
* **Email/password form is primary**; Telegram, the browser handoff and the code fallback sit below
  an "или" divider (`LoginView.axaml:516-520`).
* **Honest labels.** `Войти` = submit the form; `Войти через сайт` = browser handoff; `Войти по коду`
  = paste fallback; Google is disabled with a visible "Скоро" pill rather than faked
  (`L.Account.cs:171-176`, `LoginView.axaml:615-616`).
* **No stack of identical buttons** under the form — that is called out by name as an AI-slop tell
  (`LoginView.axaml:541-544`).
* **Neutral segment indicator**, not blue (`LoginView.axaml:67-71`).
* **No glow/halo behind the shield** — it is a brand mark, not a status indicator
  (`LoginView.axaml:229-236`).
* **Errors are colour-only** — no shake, no bounce (`LoginView.axaml.cs:871`).
* **Waiting is never a dead end** — `Другой способ входа` returns to the method block in-page
  (`LoginView.axaml:848-849`).
* **Success always gets a confirmation frame** before navigation, even under reduced motion
  (`LoginView.axaml.cs:929-1008`).
* **Never greet a returning user with onboarding** — the HEAD commit's entire subject
  (`MainWindow.axaml.cs:857-869`, `HomeViewModel.cs:540-552`).
