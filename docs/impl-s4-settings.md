# S4 — Custom Incy Settings Tab (implementation spec, doc only)

Scope: replace the hamburger navigation drawer with a fully custom, Incy-style **Settings screen**
shown as the 3rd bottom-nav tab ("Настройки"). Grouped rounded cards; each row = colored
rounded-square icon + title (+subtitle) + right value/chevron **or** iOS switch. No RecyclerView —
static XML (`ScrollView`/`NestedScrollView` of `MaterialCardView`s). The old raw-prefs
`SettingsActivity` survives as "Расширенные настройки".

Reference: `docs/incy-redesign-spec.md` → "SETTINGS tab" and "Reusable components". Staging item S4.
Detail sub-screens (Соединение / Настройки провайдеров / Тема picker) and app-icon aliases are S5;
in S4 those rows are **navigation stubs** (chevron rows that will open S5 screens).

---

## 0. Current state (verified in code)

- `ui/MainActivity.kt` — `HelperBaseActivity`, implements `NavigationView.OnNavigationItemSelectedListener`.
  - `setupBottomNav()` (lines 151-173): `nav_home` → `showHomeTab(true)`, `nav_servers` →
    `showHomeTab(false)`, `nav_more` → `binding.drawerLayout.openDrawer(START)` and returns `false`.
  - `showHomeTab(home)` (175-178): toggles `binding.groupHome.isVisible` / `binding.groupServers.isVisible`.
  - `onNavigationItemSelected(item)` (872-897): the drawer dispatch — the destinations we must relocate.
  - Back handling (113-129) references `binding.drawerLayout.isDrawerOpen(START)`.
  - `requestActivityLauncher` (86-97) handles `consumeRecreateUi` / `consumeRestartService` /
    `consumeSetupGroupTab` on activity return — reuse this for rows that open activities.
- `res/layout/activity_main.xml` — root is `DrawerLayout` containing a `LinearLayout` (toolbar +
  progress + `FrameLayout` with `group_home` NestedScrollView + `group_servers` LinearLayout + the
  `bottom_nav`), plus the `NavigationView` (`nav_view`, `menu_drawer`).
- `res/menu/menu_bottom_nav.xml` — 3 items: `nav_home`, `nav_servers`, `nav_more`
  (icons `ic_nav_home/servers/more`).
- `res/menu/menu_drawer.xml` — destinations: `telegram_login`, `sub_setting`, `per_app_proxy_settings`,
  `routing_setting`, `user_asset_setting`, `settings`, `promotion`, `logcat`, `check_for_update`,
  `backup_restore`, `about`.
- `ui/SettingsActivity.kt` + `res/xml/pref_settings.xml` — the full raw `PreferenceFragmentCompat`
  (UI / VPN / Core / Mux / Fragment / Advanced categories). Keep as-is for "Расширенные".
- Drawer-opened activities all exist and are launchable: `PerAppProxyActivity`,
  `RoutingSettingActivity`, `UserAssetActivity`, `AboutActivity`, `BackupActivity`, `LogcatActivity`,
  `SubSettingActivity`, `UrlSchemeActivity`.
- PREF constants that exist (in `AppConfig.kt`): `PREF_COLOR_THEME`, `PREF_UI_MODE_NIGHT`,
  `PREF_LANGUAGE`, `PREF_PING_METHOD`, `PREF_SHOW_MEMORY`, `PREF_AUTO_FALLBACK`, `PREF_IS_BOOTED`.
- Palette colors that exist (`res/values/colors.xml`): `colorPingRed #E5484D`, `colorConfigType #1E5FC7`,
  `color_connected #12B76A`, `color_upload #1E5FC7` (blue), `color_download #7C5CFF` (purple).
- **Do NOT exist yet** (must be created): `bg_icon_*` drawables, `ic_nav_settings`, an orange/yellow
  accent color, app-icon aliases, prefs for auto-connect / kill-switch / LAN / hotspot / disconnect-on-lock
  / provider auto-update etc. (the latter are S5 detail-screen content — S4 only routes to them).

---

## 1. Architecture — recommendation

**Recommended: inline static `group_settings` `NestedScrollView` inside the existing `FrameLayout`,
toggled by visibility exactly like `group_home`/`group_servers`. No `SettingsActivity` launch for the
tab, no `FragmentManager`, no RecyclerView.**

Rationale:
- The bottom-nav already switches screens purely by `View.isVisible` (`showHomeTab`). Adding a third
  sibling view is the smallest possible change and is 1:1 with the established pattern.
- A `Fragment` (transaction into a container) would add lifecycle plumbing and a `FragmentManager`
  round-trip for zero benefit here — the screen is static XML with click handlers.
- Launching `SettingsActivity` from the tab is rejected: it would keep an activity transition on a
  primary tab (flicker, no shared toolbar/state) and cannot host the custom Incy card UI without a
  rewrite anyway.

To keep `MainActivity` (already ~900 lines) from bloating, extract the settings-tab wiring into a
small helper rather than a Fragment:

- **`ui/SettingsTabController.kt`** (new, plain class, ~120 lines): constructed with
  `(activity: MainActivity, binding: ActivityMainBinding, launcher: ActivityResultLauncher<Intent>)`.
  Exposes `bind()` which attaches `setOnClickListener`s to every settings row and reflects toggle
  state. All row → destination logic lives here (mirrors the old `onNavigationItemSelected` `when`).
- `MainActivity` only gains: a `settingsTabController` field, a `showTab(...)` change (below), and a
  `settingsTabController.bind()` call in `onCreate`.

> Alternative if the team prefers stronger isolation: a `SettingsHomeFragment` hosted in a
> `FrameLayout id=group_settings` container, added once via `supportFragmentManager` and shown/hidden.
> Functionally identical; costs one fragment + a container. Documented as fallback, **not** recommended
> for S4.

### View toggling change

Generalize `showHomeTab(Boolean)` into a 3-way switch:

```
private fun showTab(tab: Int) {   // R.id.nav_home / nav_servers / nav_settings
    binding.groupHome.isVisible     = tab == R.id.nav_home
    binding.groupServers.isVisible  = tab == R.id.nav_servers
    binding.groupSettings.isVisible = tab == R.id.nav_settings
}
```

`setupBottomNav()` becomes:

```
R.id.nav_home     -> { showTab(R.id.nav_home); true }
R.id.nav_servers  -> { showTab(R.id.nav_servers); true }
R.id.nav_settings -> { showTab(R.id.nav_settings); true }
```

Back handling (lines 119-120): keep "if not on home, go home". Remove the
`isDrawerOpen(START)` branch (drawer deleted).

### Drawer / toolbar removals

- `activity_main.xml`: delete the `NavigationView` (`nav_view`) child and change the root from
  `DrawerLayout` to a plain `LinearLayout` (or keep `DrawerLayout` with no drawer child during a
  transitional commit — see §5). The toolbar stays but the hamburger/`ActionBarDrawerToggle` (if any)
  and `menu_more` item go.
- `MainActivity`: remove `implements NavigationView.OnNavigationItemSelectedListener`, the
  `binding.navView.setNavigationItemSelectedListener(this)` call (line 111), the whole
  `onNavigationItemSelected` (872-897), and the `openDrawer` branch. Keep `requestActivityLauncher`.
- `menu_bottom_nav.xml`: rename `nav_more` → `nav_settings`, title `@string/bottom_nav_settings`
  ("Настройки"), icon `@drawable/ic_nav_settings` (new gear glyph).
- `menu_drawer.xml`: delete (or leave orphaned and unreferenced; deletion preferred once the tab ships).

---

## 2. Reusable components (all new files)

### 2a. Colored icon backgrounds — `res/drawable/bg_icon_<color>.xml`

Tinted translucent rounded squares, 12dp corners (spec §Palette), sized by the container (44dp box).
Six variants. Use an `~14%` alpha fill of the accent, no stroke. Add two accent colors first
(orange/yellow) to `res/values/colors.xml`:

```
<color name="icon_blue">#4C8DFF</color>    <!-- reuse existing blue -->
<color name="icon_green">#22C55E</color>
<color name="icon_purple">#9B7DFF</color>
<color name="icon_yellow">#F5C542</color>   <!-- new -->
<color name="icon_red">#F04452</color>
<color name="icon_orange">#FF8A3D</color>   <!-- new -->
```

Each `bg_icon_<c>.xml`:
```
<shape android:shape="rectangle">
    <corners android:radius="12dp"/>
    <solid android:color="#264C8DFF"/>   <!-- 0x26 ≈ 15% alpha of the accent -->
</shape>
```
Files: `bg_icon_blue.xml`, `bg_icon_green.xml`, `bg_icon_purple.xml`, `bg_icon_yellow.xml`,
`bg_icon_red.xml`, `bg_icon_orange.xml`. The `ImageView` inside is `app:tint`-ed to the full-opacity
accent (`@color/icon_<c>`).

### 2b. `res/layout/layout_setting_row.xml` — navigation/value row (`<include>`-able, `merge` root)

Horizontal `LinearLayout` (or `merge`), `paddingHorizontal=16dp paddingVertical=12dp`,
`background=?attr/selectableItemBackground`, `gravity=center_vertical`, `minHeight=56dp`:
- `FrameLayout id=icon_box` 44x44, `android:background="@drawable/bg_icon_blue"` (overridden per row),
  containing `ImageView id=icon` 22x22 centered, `app:tint` accent.
- Vertical `LinearLayout` `layout_weight=1` `marginStart=14dp`:
  - `TextView id=title` 16sp `?attr/colorOnSurface`.
  - `TextView id=subtitle` 13sp `?attr/colorOnSurfaceVariant`, `visibility=gone` (shown when set).
- `TextView id=value` 14sp `?attr/colorOnSurfaceVariant`, `marginEnd=6dp`, `visibility=gone`.
- `ImageView id=chevron` 18x18 `@drawable/ic_chevron_right` (add if absent) tint
  `?attr/colorOnSurfaceVariant`.

Because `<include>` cannot re-id children, either (a) give each row a distinct `<include ... android:id>`
and access sub-views via `binding.row.findViewById`, or (b) — cleaner for a static screen — inline
copies of this layout directly in `layout_settings_screen.xml` with per-row ids (e.g.
`row_theme_title`, `row_theme_value`). Recommendation: author the row markup once here as the visual
contract, then inline instances with explicit ids in the screen layout so `ActivityMainBinding`/
view-binding exposes them directly (no `findViewById`).

### 2c. `res/layout/layout_setting_toggle_row.xml` — switch row

Same as 2b but the right side is a
`com.google.android.material.materialswitch.MaterialSwitch id=switch_row` (iOS-pill style, grey off /
blue on) instead of value+chevron. Whole row click toggles the switch. Apply a `switchStyle` /
theme overlay so the thumb+track match the Incy pill (blue `?attr/colorPrimary` when checked, grey
track when off).

### 2d. Section header style + card container

- `res/values/styles.xml`: add `style name="SettingsSectionLabel"` — 12sp, `textAllCaps=true`,
  `letterSpacing=0.06`, `?attr/colorOnSurfaceVariant`, `paddingStart=20dp`, `paddingTop=18dp`,
  `paddingBottom=8dp`. Used for each UPPERCASE group label.
- Reuse existing `bg_card_incy` semantics via `MaterialCardView` per group:
  `cardCornerRadius=20dp`, `cardElevation=0dp`, `strokeWidth=1dp`,
  `strokeColor=?attr/colorOutlineVariant`, `cardBackgroundColor=?attr/colorSurface`,
  `marginHorizontal=12dp marginBottom=6dp`. Rows stack in a vertical `LinearLayout` inside;
  1dp `?attr/colorOutlineVariant` divider `View`s between rows (no divider after the last).

### 2e. `res/layout/layout_settings_screen.xml` — the tab content

`NestedScrollView id=group_settings` (`fillViewport=true`, `visibility=gone`) → vertical `LinearLayout`
holding: section label + card (with inlined rows) per group in §3 order, then the red
"Сбросить настройки" `TextView` at the bottom. This `NestedScrollView` is placed as the **3rd child of
the existing `FrameLayout`** in `activity_main.xml` (sibling of `group_home`, `group_servers`), so
`ActivityMainBinding.groupSettings` is generated automatically.

New icon drawables needed (create as 24dp vectors, single-path, `tint`-able): `ic_nav_settings`,
`ic_chevron_right`, `ic_theme`, `ic_language`, `ic_app_icon`, `ic_connection`, `ic_tunnel`,
`ic_ping`, `ic_url_scheme`, `ic_rate`, `ic_memory`, `ic_lock`. Reuse existing where present:
`ic_per_apps_24dp`, `ic_routing_24dp`, `ic_file_24dp`, `ic_settings_24dp`, `ic_subscriptions_24dp`,
`ic_logcat_24dp`, `ic_restore_24dp`, `ic_about_24dp`, `ic_promotion_24dp`.

---

## 3. Row inventory (Incy → destination/pref)

Legend: **NAV** = opens activity/detail (chevron, optional right value). **TOGGLE** = MaterialSwitch.
**PICKER** = opens a small dialog/detail then shows selected value on the right.

### ПРОКСИ ПО ПРИЛОЖЕНИЯМ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Прокси по приложениям | `ic_per_apps_24dp` / blue | NAV (value "Выкл ›") | `launcher.launch(Intent(this, PerAppProxyActivity::class.java))` |

### ОФОРМЛЕНИЕ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Тема | `ic_theme` / purple | PICKER (value = current, "Тёмная") | S5 theme picker; writes `PREF_COLOR_THEME` + `PREF_UI_MODE_NIGHT`, then `SettingsChangeManager.makeRecreateUi()` + `recreate()` (same flow as `SettingsActivity.colorTheme` listener). S4 stub: `AlertDialog` single-choice over `@array/ui_mode_night` / `@array/color_theme`. |
| Язык | `ic_language` / blue | PICKER (value = current) | `PREF_LANGUAGE` (`@array/language_select`); on change persist + recreate UI (locale applied by base). |
| Иконка приложения | `ic_app_icon` / orange | NAV (chevron) | **New (S5)**: activity-alias chooser. S4 stub: chevron row that opens a "coming soon"/placeholder or is hidden behind a flag. Note as separate — no existing destination. |

### СОЕДИНЕНИЕ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Соединение | `ic_connection` / green | NAV (value "Настроить ›") | **New detail screen (S5)**: toggles Автоподключение, Автоподключение при загрузке (`PREF_IS_BOOTED` exists), Kill Switch, Разрешить LAN, Доступ через хотспот, LAN через прокси (all new prefs except boot). S4: chevron stub routing to the S5 screen (or disabled placeholder). |

### ТУННЕЛЬ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Туннель | `ic_tunnel` / blue | NAV (value "Настроить ›") | **Detail (S5)** = curated subset of existing VPN/Core prefs (`PREF_MODE`, `PREF_USE_HEV_TUNNEL`, `PREF_VPN_MTU`, DNS…). S4: chevron stub. |

### ПРОВАЙДЕРЫ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Настройки провайдеров | `ic_subscriptions_24dp` / blue | NAV (value "Авто ›") | **New detail (S5)**: Автообновление / Интервал (dropdown) / Уведомлять / Обновлять при запуске / Пинг при запуске / Отправлять HWID / USER-AGENT. Existing `SubSettingActivity` covers per-sub config — route here for now; new global prefs land in S5. |
| Настройки пинга | `ic_ping` / green | PICKER (value = HTTP GET/…) | `PREF_PING_METHOD` (`@array/ping_method_entries`/`_values`). S4: `AlertDialog` single-choice writing the pref. |

### ПРИЛОЖЕНИЕ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| О приложении | `ic_about_24dp` / blue | NAV (value "departament ›") | `startActivity(Intent(this, AboutActivity::class.java))` |
| Схемы URL-адресов | `ic_url_scheme` / purple | NAV | `startActivity(Intent(this, UrlSchemeActivity::class.java))` (currently unreachable from UI — new entry point) |
| Резервное копирование | `ic_restore_24dp` / green | NAV | `launcher.launch(Intent(this, BackupActivity::class.java))` |
| Оценить приложение | `ic_rate` / yellow | NAV | Play intent: `Utils.openUri(this, "market://details?id=<pkg>")` with web fallback (`https://play.google.com/store/apps/details?id=`). Reuse `Utils.openUri`. |

### ПРОИЗВОДИТЕЛЬНОСТЬ
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Расширенные настройки | `ic_settings_24dp` / blue | NAV (value "Xray Core ›") | `launcher.launch(Intent(this, SettingsActivity::class.java))` — **preserves the full raw prefs** (§4) |
| Отключать при блокировке | `ic_lock` / red | TOGGLE | **New pref** e.g. `PREF_DISCONNECT_ON_LOCK` (add const + default false). Wire actual screen-off handling in S5; S4 persists the flag via `MmkvManager.encodeSettings`. |
| Мониторинг памяти | `ic_memory` / orange | TOGGLE | `PREF_SHOW_MEMORY` (default true). Toggling shows/hides the Home memory card — `MainActivity.updateMemoryCard()` already reads this pref; no extra wiring beyond persisting. |

### ОТЛАДКА
| Row | Icon / color | Type | Destination / pref |
|---|---|---|---|
| Логи туннеля | `ic_logcat_24dp` / blue | NAV (value "None · 1 час ›") | `startActivity(Intent(this, LogcatActivity::class.java))` |

### Relocated drawer destinations not in the Incy mock (keep reachable)
Place under a **МАРШРУТИЗАЦИЯ / РЕСУРСЫ** group (or fold into ПРОВАЙДЕРЫ/ПРОИЗВОДИТЕЛЬНОСТЬ) so nothing
from `menu_drawer` is lost:
| Row | Type | Destination |
|---|---|---|
| Прокси по приложениям | NAV | `PerAppProxyActivity` (already listed above) |
| Маршрутизация | NAV | `RoutingSettingActivity` |
| Файлы ресурсов | NAV | `UserAssetActivity` |
| Подписки | NAV | `SubSettingActivity` |
| Проверить обновления | NAV | `CheckUpdateActivity` |
| Вход через Telegram | NAV (only if `BackendConfig.isConfigured()`) | `LoginActivity` |
| Реклама / промо | NAV | `Utils.openUri(APP_PROMOTION_URL)` |

> `check_for_update`, `telegram_login`, `promotion` were in the drawer — do not drop them; either add
> rows here or a "More"/"Ещё" group. Login row must stay gated by `BackendConfig.isConfigured()`
> (same guard as the old `onNavigationItemSelected`).

### Bottom
"Сбросить настройки" — red `TextView` (`@color/colorPingRed`), centered, `paddingVertical=18dp`.
Shows a confirm `AlertDialog`; on OK clears settings MMKV keys (S5 defines exact reset scope). S4 may
ship it disabled/no-op-with-confirm if reset semantics aren't finalized.

---

## 4. Keeping the old SettingsActivity ("Расширенные настройки")

- `SettingsActivity` + `pref_settings.xml` are **unchanged**. The ПРОИЗВОДИТЕЛЬНОСТЬ →
  "Расширенные настройки" row launches it via the existing `requestActivityLauncher`
  (`launcher.launch(Intent(this, SettingsActivity::class.java))`), so its return path already triggers
  `consumeRecreateUi` / `consumeRestartService` / `consumeSetupGroupTab` in `MainActivity`.
- Every raw preference (Mux, Fragment, DNS, Core log level, SOCKS, etc.) therefore remains fully
  reachable and editable — the custom tab is a curated front end, not a replacement of the pref store.
  Both read/write the same MMKV keys through `MmkvPreferenceDataStore`, so values stay consistent.
- The S5 "Туннель" and "Настройки провайдеров" detail screens are curated views over the **same** keys;
  no key is orphaned.

---

## 5. Compile-safe commit plan

Each commit builds and runs on its own.

1. **Resources only (no behavior change).** Add `bg_icon_*` drawables + `icon_*` colors (incl. new
   orange/yellow), new vector icons, `SettingsSectionLabel` style, `layout_setting_row.xml`,
   `layout_setting_toggle_row.xml`, `layout_settings_screen.xml` (root `group_settings`,
   `visibility=gone`), and string keys (`bottom_nav_settings` + all row titles/section labels).
   Nothing references them yet → compiles.

2. **Add the tab shell.** Insert `<include layout="@layout/layout_settings_screen"/>` (or inline the
   NestedScrollView) as the 3rd `FrameLayout` child in `activity_main.xml`. Rename `menu_bottom_nav`
   `nav_more`→`nav_settings` (id + title + `ic_nav_settings`). Generalize `showHomeTab`→`showTab(id)`
   and update `setupBottomNav` so `nav_settings` calls `showTab(nav_settings)` (temporarily still also
   allow the drawer to exist). Build: bottom tab now reveals the (static, unwired) settings screen.

3. **Wire the rows.** Add `ui/SettingsTabController.kt`; call `bind()` in `onCreate`. Route every row
   to its activity/pick­er/toggle per §3, reusing `requestActivityLauncher` for activity rows and
   `MmkvManager` for toggles. New pref const(s) (`PREF_DISCONNECT_ON_LOCK`) added to `AppConfig`.
   Detail-screen rows (Соединение/Туннель/Провайдеры/Иконка) point at S5 stubs or show a placeholder.
   Build: fully functional settings tab.

4. **Delete the drawer.** Remove `NavigationView` from `activity_main.xml`; change root `DrawerLayout`
   → `LinearLayout`. In `MainActivity` remove the `NavigationView.OnNavigationItemSelectedListener`
   interface, `setNavigationItemSelectedListener`, `onNavigationItemSelected`, the `openDrawer` branch,
   and the `isDrawerOpen` back-press branch. Delete `menu_drawer.xml` and `nav_header.xml` if unused.
   Build: no drawer anywhere; all destinations reachable from the tab (verify each in §3 is wired).

5. **(S5, separate)** Detail sub-screens, app-icon aliases, reset semantics, screen-off enforcement.

Compile-safety notes: keep steps 2-4 ordered so `binding.groupSettings` exists before it's referenced
(step 2 adds the view, step 3 references it); do not delete `menu_drawer`/`NavigationView` until step 4
after all dispatch has moved; string keys added in step 1 before layouts reference them.

---

## Summary (8 lines)
1. Recommend an inline `group_settings` NestedScrollView as the FrameLayout's 3rd child, toggled by visibility like home/servers — no Fragment, no RecyclerView, no SettingsActivity launch for the tab.
2. Generalize `showHomeTab` → `showTab(id)`; rename bottom-nav `nav_more`→`nav_settings`; delete the DrawerLayout, NavigationView, `menu_drawer.xml`, and `onNavigationItemSelected`.
3. New reusable resources: `bg_icon_{blue,green,purple,yellow,red,orange}`, `layout_setting_row.xml`, `layout_setting_toggle_row.xml`, `SettingsSectionLabel` style, `bg_card_incy` cards, plus new icons/colors (orange, yellow).
4. Extract row wiring into `ui/SettingsTabController.kt` to keep MainActivity lean; activity rows reuse the existing `requestActivityLauncher`, toggles use `MmkvManager`.
5. Row inventory maps every Incy row to an existing destination/pref; Тема/Язык/Пинг are pickers, Память/Блокировка are toggles, About/URL/Backup/Logs/PerApp/Routing/Assets are NAV to existing activities.
6. Соединение, Туннель, Настройки провайдеров, Иконка приложения are S5 detail-screen stubs (chevron routes); most of their toggles are new prefs (only `PREF_IS_BOOTED` exists).
7. The old `SettingsActivity`+`pref_settings.xml` stay untouched and are reachable as "Расширенные настройки", sharing the same MMKV keys so nothing is lost.
8. Five compile-safe commits: resources → tab shell → wire rows → delete drawer → (S5 details), each independently buildable.
