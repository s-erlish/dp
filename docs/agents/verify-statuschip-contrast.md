# Verification: "Status chips fail WCAG AA in the Light theme"

**Verdict: CONFIRMED — and under-reported.** The mechanism is correct, three of the four
numbers are correct, and the defect is actually *broader* than claimed: all four chips fail
in Light (not two), and the `failed` chip additionally fails in **every** theme the app ships
(Dark, Light, mono-light, mono-dark).

Severity: agree **high** for the Light-theme pending/canceled pair (1.70–1.95:1 is
effectively unreadable ink). The `failed`-in-every-theme finding is a separate, smaller
regression that the report missed.

---

## 1. The mechanism, verified

### Declarations are outside the theme dictionaries — TRUE

`ResourceDictionary.ThemeDictionaries` opens at
`/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml:59` and closes at
`:135`. It contains exactly two dictionaries, `Dark` (`:62`) and `Light` (`:100`).

The chip fills are declared at file scope, *after* the close tag:

```
/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml:255
    <SolidColorBrush x:Key="Brush.StatusChip.Green"  Color="#22C55E" Opacity="0.18" />
:256  <SolidColorBrush x:Key="Brush.StatusChip.Orange" Color="#FB923C" Opacity="0.18" />
:257  <SolidColorBrush x:Key="Brush.StatusChip.Red"    Color="#F04452" Opacity="0.18" />
:258  <SolidColorBrush x:Key="Brush.StatusChip.Yellow" Color="#EAB308" Opacity="0.18" />
```

and the orange/yellow ink at `:230-231`:

```
:230  <SolidColorBrush x:Key="Brush.Icon.Orange" Color="#FB923C" />
:231  <SolidColorBrush x:Key="Brush.Icon.Yellow" Color="#EAB308" />
```

No `Brush.StatusChip.*` or `Brush.Icon.*` key exists inside either theme dictionary
(grep over the whole Desktop project returns only these declarations plus the mono overlay
in `App.axaml.cs`). So they are theme-invariant literals.

Small wording correction: for **Green/Red** the claim "they keep dark-theme values" is
literally true — `#22C55E` and `#F04452` are verbatim the *Dark* dictionary's
`Brush.Green`/`Brush.Red` (`GlobalResources.axaml:79-80`), while Light redefines them to
`#0B7D4A`/`#C42B32` (`:115-116`). For **Orange/Yellow** there is no theme-dict counterpart at
all, so they are simply theme-less. The user-visible effect is the same either way.

### The chip composites the literal over `Brush.Surface` — TRUE

```
/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml:1106
    <Style Selector="Border.StatusChip">
:1107     <Setter Property="Background" Value="{DynamicResource Brush.SurfaceVariant}" />
:1114 <Style Selector="Border.StatusChip.paid">
:1115     <Setter Property="Background" Value="{DynamicResource Brush.StatusChip.Green}" />
:1117 <Style Selector="Border.StatusChip.paid TextBlock">
:1118     <Setter Property="Foreground" Value="{DynamicResource Brush.Green}" />      <-- theme-dependent
:1121     ...Background = Brush.StatusChip.Orange
:1124     <Setter Property="Foreground" Value="{DynamicResource Brush.Icon.Orange}" /> <-- literal
:1127     ...Background = Brush.StatusChip.Red
:1130     <Setter Property="Foreground" Value="{DynamicResource Brush.Red}" />        <-- theme-dependent
:1133     ...Background = Brush.StatusChip.Yellow
:1136     <Setter Property="Foreground" Value="{DynamicResource Brush.Icon.Yellow}" /> <-- literal
```

Both call sites put the chip inside `Border Classes="Card"`, whose background is
`Brush.Surface` (`GlobalStyles.axaml:330-331`):

- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/PaymentHistoryView.axaml:122-132`
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/AccountView.axaml:552-561`

Light `Brush.Surface` = `#FFFFFF` (`GlobalResources.axaml:102`). A `SolidColorBrush` with
`Opacity="0.18"` composites at 18% over whatever is behind it, so the reporter's model is
right.

### 11px means the 4.5:1 threshold applies — TRUE

Chip text is `Classes="Chip"` at both call sites
(`PaymentHistoryView.axaml:131`, `AccountView.axaml:560`), and:

```
/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml:310
    <Style Selector="TextBlock.Chip">
:312     <Setter Property="FontWeight" Value="Medium" />
:313     <Setter Property="FontSize" Value="11" />
:314     <Setter Property="Foreground" Value="{DynamicResource Brush.OnSurface}" />
```

11px Medium is far below the WCAG large-text exemption (24px regular / 18.66px bold), so
1.4.3 AA requires 4.5:1. No exemption.

### Adversarial checks that could have refuted the claim, and didn't

1. **Does `TextBlock.Chip`'s own `Foreground` (`:314`, `Brush.OnSurface`) win over the
   StatusChip setters?** No. Both styles live in the same single `<Styles>` root
   (`GlobalStyles.axaml:1`), and Avalonia applies styles in document order with the last
   matching setter winning. `:1118/:1124/:1130/:1136` come after `:314`, so the *status
   colour* is the rendered ink. (Had it been the other way round, ink would be
   `Brush.OnSurface` and there would be no contrast problem at all.)
2. **Is Light actually reachable?** Yes — it is a first-class user toggle:
   `SettingsView.axaml:724-727` (`SegThemeLight`) → `App.axaml.cs:517-523`
   (`nameof(ETheme.Light) => ThemeVariant.Light`), enum at
   `/home/user/v2rayN/v2rayN/ServiceLib/Enums/ETheme.cs:3`.
3. **Does the mono/black overlay rescue Light?** Only partly, and only when the black
   toggle is on. `App.axaml.cs:646-649` re-keys `Brush.StatusChip.*` to grey and
   `:641-642` re-keys `Brush.Icon.Orange/Yellow` to `onSurfaceVariant` — this is precisely
   the fix the base themes lack, and it proves the codebase already knows these keys need
   per-theme values. Plain Light (mono off) gets nothing.

---

## 2. Measured contrast (WCAG 2.x relative luminance, sRGB compositing)

Fill = `0.18 * chipColour + 0.82 * surface`. Ink as resolved per theme.

### Light base — `Brush.Surface` `#FFFFFF`

| chip | fill (composited) | ink | ratio | AA (4.5:1) |
|---|---|---|---|---|
| pending | `#FEEBDC` | `#FB923C` | **1.95:1** | FAIL |
| canceled | `#FBF1D3` | `#EAB308` | **1.70:1** | FAIL |
| paid | `#D7F5E2` | `#0B7D4A` | **4.46:1** | FAIL |
| failed | `#FCDDE0` | `#C42B32` | **4.43:1** | FAIL |

On `Brush.Bg` `#F4F7FC` (`:101`) — e.g. a chip on a non-card surface — it is worse:
pending 1.84:1, canceled 1.60:1, paid 4.18:1, failed 4.17:1.

### Dark base — `Brush.Surface` `#141619`

| chip | fill | ink | ratio | AA |
|---|---|---|---|---|
| paid | `#173625` | `#22C55E` | 5.79:1 | PASS |
| pending | `#3E2C1F` | `#FB923C` | 5.85:1 | PASS |
| canceled | `#3B3216` | `#EAB308` | 6.62:1 | PASS |
| failed | `#3C1E23` | `#F04452` | **4.03:1** | **FAIL** |

### Mono overlay (black toggle), both bases

| chip | mono-light | mono-dark |
|---|---|---|
| paid | 14.57:1 PASS | 13.55:1 PASS |
| pending | 5.34:1 PASS | 6.27:1 PASS |
| canceled | 5.34:1 PASS | 6.27:1 PASS |
| failed | **4.22:1 FAIL** | **3.97:1 FAIL** |

---

## 3. Corrections to the report

1. **Understated scope in Light.** The headline says "1.70:1–1.96:1", which reads as if only
   pending/canceled are broken. In fact **all four** Light chips fail: `paid` at 4.46:1 and
   `failed` at 4.43:1 are *below* 4.5:1, not above it. The report's own DETAIL quotes those
   numbers without flagging them as failures.
2. **One number is slightly off.** `failed` in Light is **4.43:1**, not 4.48:1. (4.48:1
   happens to be the *Dark-on-Bg* value for the same chip — likely a transposition.)
   pending is 1.95:1 (report said 1.96); canceled 1.70:1 and paid 4.46:1 are exact.
3. **Missed defect — `failed` fails in every theme.** `Border.StatusChip.failed TextBlock`
   sets `Foreground` to `Brush.Red` (`GlobalStyles.axaml:1130`). That directly violates the
   rule this repo wrote for itself four lines above the token:

   > `GlobalResources.axaml:81-84` — *"Красный ТЕКСТ на тёмных поверхностях … Ярче заливки
   > Brush.Red ради контраста ≥4.5:1 … **Brush.Red остаётся для заливок/рамок/глифов,
   > RedText — только для текста.**"*

   `Brush.RedText` exists in both theme dictionaries (`:84` dark `#FF6069`, `:120` light
   `#C42B32`) exactly for this case, and the chip does not use it. Swapping `Brush.Red` →
   `Brush.RedText` at `:1130` lifts dark from 4.03:1 to **5.08:1** — but does nothing for
   Light, where `RedText` is the same `#C42B32`, so Light still needs the fill fixed.
4. **The hue-mismatch second-order note is correct**, and correctly scoped: only
   `paid`/`failed` mismatch (ink theme-dependent `#0B7D4A`/`#C42B32` vs fill keyed to the
   dark `#22C55E`/`#F04452`). `pending`/`canceled` are hue-consistent because *both* ends
   are the same theme-less literal — which is precisely why they collapse to 1.70–1.95:1.

---

## 4. Design-law angle (`/home/user/dp/CLAUDE.md`)

The project's design law requires "Body text contrast ≥4.5:1" and "every state designed".
A status chip is a state indicator; four of them are illegible or borderline in a shipping
theme. It also breaks the file's own stated contract — the header comment at
`GlobalResources.axaml:54-58` promises *"Каждый Brush.* резолвится по активному
ThemeVariant … Оба набора несут ОДИНАКОВЫЙ список ключей"*, which is untrue for the six keys
at `:230-231` and `:255-258`.

## 5. Minimal fix shape

Move the six keys into both theme dictionaries (they already have per-theme values in the
mono overlay, so the pattern exists), giving Light darker inks and lighter-but-still-tinted
fills — e.g. Light ink `#B45309` (amber-700) / `#854D0E` (yellow-800) against the same 18%
tint clears 4.5:1 — and point `StatusChip.failed`'s ink at `Brush.RedText`
(`GlobalStyles.axaml:1130`) rather than `Brush.Red`. Fill tokens should also be re-keyed per
theme so `paid`/`failed` fills follow the Light green/red instead of the dark ones.

## 6. Files read

- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalResources.axaml` (full)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/GlobalStyles.axaml` (lines 300-336, 1090-1160, header)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/PaymentHistoryView.axaml` (80-149)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/AccountView.axaml` (111-122, 520-580)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/App.axaml.cs` (488-707)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/App.axaml` (1-60)
- `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml` (690-730)
- `/home/user/v2rayN/v2rayN/ServiceLib/Enums/ETheme.cs`
