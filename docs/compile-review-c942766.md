# Compile-risk review — merge c942766 (branch `claude/vpn-client-happ-design-mq51pv`)

Fork: departament (v2rayNG/Xray), package `com.v2ray.ang`.
Merge absorbs four feature branches: visual/theme, server long-press actions, hidden JSON
templates, custom Settings tab (drawer removed). Review is by static reading only (no Android SDK).

**Bottom line: 0 build-breakers (blockers). 1 major functional (non-compile) defect. The
auto-merge is semantically clean at the seams — every cross-branch symbol and resource resolves.**

---

## Blockers (will fail the build)

None found.

Every high-risk seam was checked and resolves:

- `MainActivity` no longer implements `NavigationView.OnNavigationItemSelectedListener`; no residual
  `onNavigationItemSelected` / `binding.navView` / `binding.drawerLayout` / `GravityCompat` /
  `menu_drawer` / `nav_header` references anywhere in `app/src/main/java` or `res`
  (grep clean). Deleted `menu_drawer.xml` / `nav_header.xml` are unreferenced.
- `setupSettings()` / `bindSettingsState()` — all 33 `binding.groupSettings.<id>` accessors map to
  real ids in `res/layout/layout_settings_content.xml` (included in `activity_main.xml` as
  `@+id/group_settings`, so `binding.groupSettings` is `LayoutSettingsContentBinding` and `.root`
  is valid).
- `showTab()` / `setupBottomNav()` use `nav_home` / `nav_servers` / `nav_settings`, all present in
  `res/menu/menu_bottom_nav.xml`; nav icons `ic_nav_home/servers/settings` and the
  `@color/bottom_nav_item_color` ColorStateList (in `res/color/`) all exist.
- Connect-button methods `applyThemeDecorations`, `animateConnectPress`, `startGlowPulse`,
  `stopGlowPulse`, `metaTitle`, plus `showServerActions`, `setupAccountHeader`,
  `TemplateManager.isLocked` all resolve. All connect-hero view ids exist in `activity_main.xml`.
- All MainActivity imports present; `TemplateManager` imported (line 59).
- Cross-namespace `R.attr` usage is correct: `androidx.appcompat.R.attr.colorPrimary` (line 544),
  `com.google.android.material.R.attr.colorOnSurfaceVariant` (545/566/831),
  `com.google.android.material.R.attr.colorOnSurface` (832). Theme attrs
  `connectActiveColor` / `connectedColor` declared in `values/attrs.xml` and set in both the Blue
  and Mono themes in `values/themes.xml`.
- Templates: `TemplateManager` / `TemplateCrypto` compile; three `isLocked` overloads
  (`ProfileItem` / `SubscriptionItem` / `String`) have distinct JVM signatures — no clash.
  `ProfileItem.locked` (line 75) and `SubscriptionItem.locked` (line 35) / `profileTitle` (line 29)
  exist. Integration in `CoreConfigManager` (`decodeRuntimeRaw`, l.86), `SettingsManager`
  (fully-qualified `TemplateManager.decodeRuntimeRaw`, l.196), `AngConfigManager`
  (`applyLockState` l.627, `wrapRawForStorage` l.422/446 with `locked` bound at l.400),
  `ServerCustomConfigActivity` (l.46), `SubEditActivity` (`subItem.locked` l.56/127) all resolve.
- `HttpUtil.UrlContentResult` (l.202) has `profileTitle` + `hidden` fields; its **only**
  construction (l.257) uses named arguments, and both read sites (`AngConfigManager` l.627/647)
  use named field access — no positional-constructor mismatch.
- Server actions: `ServerActionsSheet` binding ids (`tvSheetTitle`, `rowShareQr`,
  `rowShareClipboard`, `rowEdit`, `rowDuplicate`, `rowSetDefault`, `rowDelete`) match
  `sheet_server_actions.xml`; all its `@drawable`/`@string` refs exist.
  `MainRecyclerAdapter.onItemLongClick` (l.54) wired on `binding.infoContainer` (exists) and the
  `setOnLongClickListener` lambda correctly returns `true` (l.222).
- Resources: every `@string` used by the new settings tab / bottom nav / server actions / templates
  exists; `SettingsSectionLabel` and `BottomNavIndicator` styles exist; all `@color`/`@drawable`
  referenced by changed layouts and by `themes.xml` resolve. No duplicate top-level resource names
  within `values/` (the only cross-file repeats are `<item>` entries inside distinct `<style>`
  blocks — legitimate). `values-ru/strings.xml` is a locale override, not a duplicate.
- `Array<String>.indexOf(...)` calls (l.1470/1504/1602) pass non-null `String` (`.orEmpty()`) — no
  nullability signature issue.

---

## Major (functional regression — does NOT break the build)

### M1. `ServerActionsSheet` still uses a stub lock check — hidden/locked templates remain shareable via long-press
`app/src/main/java/com/v2ray/ang/ui/ServerActionsSheet.kt:74-76`

```kotlin
private fun isLocked(profile: ProfileItem): Boolean {
    return false          // <-- placeholder from the server-actions branch
}
```

The templates branch shipped `TemplateManager.isLocked(profile)`, and `MainActivity` already gates
its own share/edit paths through it (l.649/712). But the long-press bottom sheet keeps a local stub
that always returns `false`, so for a locked (hidden) profile the sheet still shows Share-QR /
Share-clipboard / Edit / Duplicate rows (l.47-52) — defeating the template-hiding guarantee for the
primary entry point. Compiles fine (self-contained, no unresolved symbol); purely a merge-seam
logic gap.

**Fix:** replace the stub body with the real check (the TODO at l.64-72 spells it out):

```kotlin
import com.v2ray.ang.template.TemplateManager
...
private fun isLocked(profile: ProfileItem): Boolean = TemplateManager.isLocked(profile)
```

(Delete stays allowed regardless — `rowSetDefault`/`rowDelete` are intentionally not gated.)

---

## Minor / informational

- `SettingsActivity` (`ui/SettingsActivity.kt`) and its `res/layout/activity_settings.xml` survive
  the drawer removal as an orphaned standalone screen. It is not reached from `MainActivity`
  (only referenced in two comments). It compiles and references no deleted resource, so it is not a
  build risk — consider removing it as dead code in a follow-up.

---

## Summary (RU)

Вероятных build-breaker'ов: **0**. Автослияние c942766 семантически чистое на всех швах —
каждый межветочный символ и ресурс разрешается (проверены MainActivity/drawer-остатки, вкладка
Настроек, bottom-nav, шаблоны, серверные действия, ресурсы, кросс-namespace R.attr, сигнатуры).

Найдена **1 major-проблема (НЕ ломает сборку, логическая регрессия):**

- **M1 — `ui/ServerActionsSheet.kt:74-76`:** метод `isLocked(profile)` — заглушка `return false`,
  оставшаяся от ветки серверных действий. Скрытые (locked) профили в bottom-sheet по долгому
  нажатию по-прежнему показывают «Поделиться QR / Копировать / Изменить / Дублировать», обходя
  защиту шаблонов. Компилируется, но нарушает функцию скрытия.
  **Исправление:** заменить тело на `return TemplateManager.isLocked(profile)` и добавить
  `import com.v2ray.ang.template.TemplateManager` (удаление/по-умолчанию оставить незаблокированными).

Прочее (не влияет на сборку): `SettingsActivity` + `activity_settings.xml` остались «сиротами»
после удаления drawer — мёртвый код, удалить отдельным PR.
