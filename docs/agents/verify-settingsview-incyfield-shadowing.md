# Verification: "SettingsView shadows the global TextBox.IncyField"

**Verdict: CONFIRMED (real defect).** Mechanism as described is correct. Two refinements to the
reporter's impact analysis, one *additional* observable symptom they missed, and a severity
downgrade from `high` to `medium`.

Target: `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Views/SettingsView.axaml:68`

---

## 1. The two definitions exist, and they differ

Local copy, inside `<UserControl.Resources>` (opened `SettingsView.axaml:20`, closed `:136`):

- `SettingsView.axaml:68` — `<ControlTheme x:Key="TextBox.IncyField" TargetType="TextBox">`
- `SettingsView.axaml:72` — `<Setter Property="CornerRadius" Value="{DynamicResource Radius.Tile}" />`

Global copy:

- `Assets/GlobalResources.axaml:494` — `<ControlTheme x:Key="TextBox.IncyField" TargetType="TextBox">`
- `Assets/GlobalResources.axaml:498` — `<Setter Property="CornerRadius" Value="{DynamicResource Radius.Search}" />`
- `Assets/GlobalResources.axaml:565-567` — `<Style Selector="^:disabled">` → `Opacity 0.38`

Token values:

- `Assets/GlobalResources.axaml:151` — `<CornerRadius x:Key="Radius.Tile">12</CornerRadius>`
- `Assets/GlobalResources.axaml:284` — `<CornerRadius x:Key="Radius.Search">14</CornerRadius>`

Normalized diff of the two `ControlTheme` bodies (`SettingsView.axaml:68-135` vs
`GlobalResources.axaml:494-568`) yields exactly three divergences:

1. `CornerRadius`: `Radius.Tile` (12) vs `Radius.Search` (14).
2. Local template omits the `<ContentPresenter Grid.Column="1" Content="{TemplateBinding InnerRightContent}" />`
   present at `GlobalResources.axaml:465-468`.
3. Local has no `^:disabled` style (`GlobalResources.axaml:565-567`).

Everything else — brushes, `MinHeight 44`, `Padding 12,0`, `FontSize 15`, the `:pointerover`
and `:focus` border styles — is byte-identical.

## 2. The documentation claim being contradicted

`Assets/GlobalResources.axaml:487-490`:

```
<!--  SurfaceHigh, высота 44, паддинг 12,0. Радиус СВЕДЁН 12→14 (Radius.Search) -->
<!--  к единой шкале полей ввода (SearchPill / PriceOption / TextBox.Incy = 14) -->
<!--  — конец дрейфа radius 12 vs 14 (REVIEW_VISUAL L9). Добавлен :disabled     -->
<!--  (opacity 0.38) как у TextBox.Incy — раньше у поля его не было.            -->
```

## 3. The local dictionary wins — mechanism confirmed

`App.axaml:15` merges the global dictionary at **Application** scope:
`<ResourceInclude Source="Assets/GlobalResources.axaml" />`.

The three consumers are all descendants of the `SettingsView` root `UserControl`, whose
`Resources` therefore sit between them and `Application.Resources` in the lookup chain:

- `SettingsView.axaml:483-487` — `ProxyPortBox`, `Theme="{StaticResource TextBox.IncyField}"`
- `SettingsView.axaml:496-500` — `ProxyUserBox`, same
- `SettingsView.axaml:504-509` — `ProxyPassBox`, same

`StaticResource` resolves innermost-scope-first, so the local dictionary shadows the global one.
**This is corroborated inside the repo itself** — `LoginView.axaml:47-48` documents the same
promotion done correctly:

```
<!--  Тема поля Incy теперь ГЛОБАЛЬНАЯ (GlobalResources.axaml → TextBox.Incy). Локальный дубль
      удалён (P0): поля ссылаются на Theme="{StaticResource TextBox.Incy}" — рендерятся 1:1.  -->
```

LoginView had to *delete* its local duplicate for the global to take effect. If Application-scope
resources won, that deletion would have been unnecessary. SettingsView never got the same treatment.

Git confirms the omission: commit `96d0d67` ("Fix wave: functional bugs, idle perf, motion gaps,
visual drift, onboarding") introduced the global `TextBox.IncyField` and *did* touch
`SettingsView.axaml`, but its `SettingsView.axaml` diff contains **zero** lines mentioning
`IncyField`, `Radius.Tile`, or `Radius.Search` — the local copy (added earlier in `4ca8632`)
was left behind untouched.

## 4. Refutation attempt — closed

In Avalonia, `Style` setters outrank `ControlTheme` setters, so a global
`Style Selector="TextBox"` setting `CornerRadius` would make both themes irrelevant and refute the
claim. There are exactly two such styles and neither touches `CornerRadius`:

- `Assets/GlobalStyles.axaml:210-212` — sets only `ScrollViewer.AllowAutoHide`.
- `Assets/GlobalStyles.axaml:1060-1067` — sets only `FocusAdorner`.

The `ControlTheme` `CornerRadius` setter is authoritative. The claim survives.

---

## 5. Corrections to the reporter

### 5a. Sharper framing: the global theme is 100% dead code

Repo-wide grep for `IncyField` returns consumers in **one file only** — `SettingsView.axaml:487`,
`:499`, `:508`. Since all three are shadowed, `GlobalResources.axaml:494-568` (75 lines) has
**zero effective consumers anywhere in the product**. The documented fix did not "partly" fail;
it never applied to a single rendered control.

### 5b. Two of the three divergences are latent, not observable

- **Missing `:disabled`** — the reporter says the fields "have no disabled appearance." True of the
  theme, but never reachable: `Views/SettingsView.axaml.cs:62-64` only attaches `LostFocus`
  handlers to `ProxyPortBox`/`ProxyUserBox`/`ProxyPassBox`, and no `IsEnabled` binding exists for
  them in the `.axaml`. The fields are never disabled, so the missing style has no current visual
  effect.
- **Missing `InnerRightContent` presenter** — none of the three fields set `InnerRightContent`, so
  this too is dormant.

Both are real latent traps for whoever next adds a disabled state or a trailing slot, but neither
is a shipped visual defect today.

### 5c. Additional observable symptom the reporter missed: the focus ring is off-curve

`Assets/GlobalStyles.axaml:1059-1067` gives **every** `TextBox` a `FocusAdorner` with
`CornerRadius="16"`, derived explicitly from the assumed field radius:

```
<!--  Поля ввода Incy (TextBox.Incy / IncyField, Radius.Search 14 → кольцо 16).  -->
```

The ring is `Margin="-2"` outside a radius-14 field → 16 is the concentric value. At the local
copy's radius **12**, the concentric ring would be **14**. So on these three fields the focus ring
does not track the border curve — a mismatch visible *within a single control*, on focus, without
needing a second screen for comparison. This is the strongest observable consequence, and the
reporter did not mention it.

### 5d. "Every other Incy field renders at 14" — true, but cross-screen

The comparison set is `TextBox.Incy` (`GlobalResources.axaml:411`, `Radius.Search`), used at
`LoginView.axaml:350, 369, 416, 602, 688`. `SettingsView.axaml` contains only 3 `<TextBox>`
elements total — all three shadowed. So the 12-vs-14 drift is never visible side-by-side on one
screen; it is a design-system consistency failure across screens.

### 5e. Severity

`high` is overstated. Net observable impact: a 2px corner-radius deviation on three fields inside a
collapsed panel, plus a non-concentric focus ring on those fields. Real, and a genuine violation of
the "one radius scale" rule in `/home/user/dp/CLAUDE.md`, but not functional. The stronger cost is
maintenance: 75 lines of unreachable code carrying a comment that asserts a fix which is not in the
shipped build — exactly the kind of false-confidence artifact that causes the next reviewer to skip
the check. **Assess as `medium`.**

---

## 6. Fix

Delete `SettingsView.axaml:65-135` (the comment block at `:65-67` plus the whole local
`ControlTheme`). The three `Theme="{StaticResource TextBox.IncyField}"` references at `:487`,
`:499`, `:508` then resolve to `GlobalResources.axaml:494`, picking up radius 14, the `:disabled`
style, and `InnerRightContent` support — mirroring exactly what `LoginView.axaml:47-48` already
records as done for `TextBox.Incy`. No other file references the local key, so the deletion is
self-contained.
