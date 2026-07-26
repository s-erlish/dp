# Adversarial verification — "SubscriptionMetaView paints dark-theme hex literals, bypassing Light and Mono"

**Verdict: CONFIRMED (real defect).** Two of the seven cited line numbers are wrong (design-time only),
and the reporter *understated* the blast radius. Everything else — including both contrast numbers —
reproduces exactly from the files.

Target: `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SubscriptionMetaView.axaml.cs:30`

---

## 1. The literals exist and are static

`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SubscriptionMetaView.axaml.cs:29-32`

```csharp
//  Кэш-кисти повторяют токены Incy (тема одна, тёмная) — как в ConnectHeroView/MainWindow.
private static readonly IBrush _accent = new SolidColorBrush(Color.Parse("#4C8DFF"));       // Brush.Accent
private static readonly IBrush _muted  = new SolidColorBrush(Color.Parse("#9BA1AD"));        // Brush.OnSurfaceVariant
private static readonly IBrush _red    = new SolidColorBrush(Color.Parse("#F04452"));        // Brush.Red (destructive)
```

The comment "тема одна, тёмная" (there is only one theme, dark) is stale — see §2.

## 2. Three themes ship and all three are user-reachable at runtime

- Dark / Light theme dictionaries: `Assets/GlobalResources.axaml:59-135`
  (`Dark` at :62-97, `Light` at :100-133).
- Mono/AMOLED overlay merged over either base: `App.axaml.cs:580-663`
  (`BuildMonoOverlay`), attached/detached at `App.axaml.cs:556-573`.
- Surfaced in the UI: `Views/SettingsView.axaml:720-727` (`SegThemeDark` / `SegThemeLight`
  segment) and `:757-762` (`SwitchBlackTheme`, bound `TwoWay` to `BlackTheme`).

Relevant token values, all read from source:

| key | Dark | Light | Mono-dark | Mono-light |
|---|---|---|---|---|
| `Brush.Surface` | `#141619` (GlobalResources:64) | `#FFFFFF` (:102) | `#121214` (App:609) | `#FFFFFF` (App:609) |
| `Brush.OnSurfaceVariant` | `#9BA1AD` (:69) | **`#54607A`** (:107) | `#B0B0B4` (App:592/614) | `#5A5A5E` (App:592/614) |
| `Brush.Accent` | `#4C8DFF` (:41, variant-independent) | `#4C8DFF` | **`#FFFFFF`** (App:586/619) | **`#111214`** (App:586/619) |
| `Brush.Red` | `#F04452` (:80) | **`#C42B32`** (:116) | `#E5484D` (App:595/630) | `#C42B32` |
| `Brush.RedText` | `#FF6069` (:84) | `#C42B32` (:120) | `#FF6069` (App:599/631) | `#C42B32` |

So the Light value of `OnSurfaceVariant` is `#54607A`, **not** the `#9BA1AD` the file hardcodes.

## 3. The literals are assigned unresolved — corrected line list

The reporter listed seven sites. **Five are runtime; two are not.**

Runtime (real):
- `:375` — `PinIcon.Foreground = sub.Pinned ? _accent : _muted;`
- `:390` — `ExpiryText.Foreground = _muted;` (unlimited, "∞")
- `:398` — `ExpiryText.Foreground = _red;` (overdue)
- `:404` — `ExpiryText.Foreground = _muted;` ("до dd.MM.yyyy")
- `:602` — `PinIcon.Foreground = sub.Pinned ? _accent : _muted;` (optimistic pin toggle)

**Not runtime — reporter is wrong on these two:**
- `:679` — `ExpiryText.Foreground = _muted;`
- `:682` — `PinIcon.Foreground = _muted;`

Both live inside `ApplyDesignSample()` (`:664-686`), which is only ever called from the
`if (Design.IsDesignMode)` branch at `:78-82` and is documented "Never runs at runtime" (`:663`).
They affect the Avalonia previewer only. Dropping them does not weaken the finding — the five
runtime sites stand on their own.

These are **local values**, which in Avalonia outrank both style setters and the `DynamicResource`
bindings the AXAML already declares correctly:
- `SubscriptionMetaView.axaml:200` — `PinIcon Foreground="{DynamicResource Brush.OnSurfaceVariant}"`
- `Assets/GlobalStyles.axaml:306-309` — `TextBlock.Caption` = `FontSize 12` +
  `Foreground="{DynamicResource Brush.OnSurfaceVariant}"`, and `ExpiryText` carries
  `Classes="Caption"` (`SubscriptionMetaView.axaml:267`).

So the code-behind actively *destroys* markup that was already theme-correct.

## 4. The correct pattern is present in the same file, and in two sibling views

`:414-416` — `BuildTrafficBrush()` does it right:

```csharp
var accent = (ResolveBrush("Brush.Accent", _accent) as ISolidColorBrush)?.Color ?? Color.Parse("#4C8DFF");
```

Here `_accent` is a *fallback argument*, which is legitimate. `ResolveBrush` (`:440-450`) resolves
against `ActualThemeVariant`. Its own doc-comment at `:439` says it "подхватывает светлую И
mono-оверлей" — the author knew.

Both sibling views declare the identical literals but never assign them raw:
- `Views/ConnectHeroView.axaml.cs:47-50` declared → used only at `:68`, `:72`, `:75`, `:79`
  as `ResolveBrush("Brush.Accent", AccentFallback)` etc.
- `Views/ServerListView.axaml.cs:901-903` declared → used only at `:910`, `:915-916` as
  `Resolve("Brush.OnSurfaceVariant", _mutedFallback)` etc.

`SubscriptionMetaView` is the single outlier.

## 5. The view is mounted on a primary screen

`Views/ServerListView.axaml:92` — `<local:SubscriptionMetaView Margin="16,16,16,8" />`, used as the
per-subscription group header on Home (`Views/HomeView.axaml:22`). Not dead code.

## 6. Computed consequences — reporter's numbers verified

Card background is `Brush.Surface`: `MetaCard` is `Classes="Card"`
(`SubscriptionMetaView.axaml:93-96`) → `Assets/GlobalStyles.axaml:330-331`
`Background="{DynamicResource Brush.Surface}"` → `#FFFFFF` in Light.

WCAG 2.x relative-luminance, recomputed independently:

| state | painted | on | ratio | correct token | ratio it would give |
|---|---|---|---|---|---|
| Light, expiry date (12px text) | `#9BA1AD` | `#FFFFFF` | **2.59:1** ✗ (needs 4.5:1) | `#54607A` | 6.30:1 ✓ |
| Light, "Просрочено" (12px text) | `#F04452` | `#FFFFFF` | **3.71:1** ✗ | `Brush.RedText` `#C42B32` | 5.62:1 ✓ |
| Light, pin icon unpinned (non-text) | `#9BA1AD` | `#FFFFFF` | **2.59:1** ✗ (needs 3:1) | `#54607A` | 6.30:1 ✓ |
| Mono-dark, pin icon pinned | `#4C8DFF` **blue** | `#121214` | — | `Brush.Accent` `#FFFFFF` | — |
| Mono-light, pin icon pinned | `#4C8DFF` **blue** | `#FFFFFF` | — | `Brush.Accent` `#111214` | — |
| Dark, "Просрочено" | `#F04452` | `#141619` | 4.88:1 ✓ | `Brush.RedText` `#FF6069` | higher |

Both figures the reporter quoted (2.59:1 and 3.71:1) reproduce to two decimals. The 5.62:1 for
`#C42B32` also matches the value the theme file asserts about itself at
`GlobalResources.axaml:117-119` ("5.6:1 на Surface #FFFFFF").

The Mono claim is confirmed by the overlay's own comment: `App.axaml.cs:590`
`var connected = light ? "#111214" : "#FFFFFF"; // mono connected (не синий)`, and
`App.axaml.cs:618-619` "Акцент → серый (схлопывание #4C8DFF)". A pinned subscription in
Mono/AMOLED keeps a `#4C8DFF` blue pin — the one colour the theme exists to remove.

## 7. Two things the reporter missed

**(a) The unpinned pin state is broken too, not just the pinned one.** `:375` overwrites the
correct `DynamicResource Brush.OnSurfaceVariant` from `SubscriptionMetaView.axaml:200` on *both*
branches. In Light that is a 2.59:1 glyph on white (fails the 3:1 non-text minimum); in Mono-dark
it paints `#9BA1AD` where the theme specifies `#B0B0B4`.

**(b) Wrong token even in Dark — `_red` should be `Brush.RedText`, not `Brush.Red`.**
`GlobalResources.axaml:81-84` introduces `Brush.RedText` precisely because `Brush.Red` is a
fill/border/glyph tone unsuitable for text: "Brush.Red остаётся для заливок/рамок/глифов, RedText
— только для текста." "Просрочено" (`:397-398`) is text. So the overdue state uses the wrong
semantic token in *every* theme, independent of the resolution bug.

## 8. Corrected description of the fix

Resolving the brushes is necessary but **not sufficient** — a bare `ResolveBrush` call still writes
a one-shot local value that goes stale when the user flips the theme while the meta-bar is bound.
`OnThemeVariantChanged` (`:127-133`) currently rebuilds **only** `TrafficFill.Background`; it does
not touch `PinIcon` or `ExpiryText`. Compare `ConnectHeroView.axaml.cs:616-617`, whose
`OnThemeVariantChanged` re-runs `SetConnectState(...)` and thereby re-resolves every brush.

A complete fix is either:
1. resolve via `ResolveBrush("Brush.OnSurfaceVariant" | "Brush.Accent" | "Brush.RedText", fallback)`
   at `:375`, `:390`, `:398`, `:404`, `:602`, **and** re-apply pin tint + `ApplyExpiry(_boundSub.Expire)`
   from `OnThemeVariantChanged`; or
2. stop setting `Foreground` from code entirely — drive `PinIcon` and `ExpiryText` with style classes
   (e.g. `pinned` / `overdue`) whose setters use `DynamicResource`, which stays live across theme
   flips for free and preserves the existing `BrushTransition` on `PinIcon` (`:200-208`).

Also update the stale comment at `:29`.

**Severity: agreed, high** for Light (three contrast failures on a primary screen) and Mono
(brand-defeating blue). Not a regression in Dark, except for the `Brush.Red`-vs-`Brush.RedText`
token choice.
