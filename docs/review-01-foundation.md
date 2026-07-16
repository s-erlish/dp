# Review 01 — Foundation (theme, main-screen redesign, branding, fast-connect, surface-ramp)

Scope: `git diff aadd34f..HEAD -- V2rayNG/` (commits `d9e68a8`, `818f257`, `c9b9de3`, `8f89214`).
Method: static reading only (no Android SDK / no compile available).

## Summary

No compile-breakers found. All binding IDs referenced from Kotlin exist in the new
`activity_main.xml` / `item_recycler_main.xml` (verified camelCase mapping), every
`@drawable/@color/@style/@string/@id` and `?attr/...` referenced by the new/changed XML
resolves, the mono `ThemeOverlay` and the surfaceContainer ramp are consistent across
`values/` and `values-night/`, and the `LongArray` speed broadcast round-trips correctly.

The notable issues are correctness/UX, not build failures. The most important is a LiveData
**event-replay** bug in the fast-connect path that can auto-connect / auto-restart the VPN on
every activity recreate (rotation or the new theme-change `recreate()`).

## Findings

| Severity | File:line | Issue | Fix |
|---|---|---|---|
| HIGH | `viewmodel/MainViewModel.kt:51` + `ui/MainActivity.kt:160-173` | `fastConnectAction` is a plain `MutableLiveData` that retains its last value. `MainViewModel` is retained across `recreate()` (`by viewModels()`), so after any fast-connect the observer registered in `setupViewModel()` re-fires with the last non-null guid on the next recreate — device rotation, or the new color-theme `recreate()` — and calls `restartV2Ray()` (if running) or `startVpnWithPermission()`. Result: an unwanted disconnect/reconnect or auto-connect the user never requested. | Make it a one-shot event: use a `SingleLiveEvent`/`Event<T>` wrapper, or clear it after handling (`fastConnectAction.value = null` at the end of the observer AND guard `null`), or emit via a `Channel`/`SharedFlow` consumed with `collect`. The current `if (guid == null) … return` guard does not help because the stored value is the non-null guid. |
| MEDIUM | `ui/MainActivity.kt:238,244,250` and `res/layout/activity_main.xml` (`@color/color_fab_inactive` on `card_connect`, `img_connect` `@color/colorWhite`) | Mono theme is incomplete. `ThemeOverlay.Mono` only remaps `?attr/...` tokens, but the hero connect button and its state colors are hardcoded raw colors (`R.color.color_fab_active` / `color_connected` / `color_fab_inactive`), which stay blue/green in mono mode. The dedicated `mono_fab_active` / `mono_connected` colors defined in both `colors.xml` files are never referenced. | Either drive the button via themed attrs (e.g. `?attr/colorPrimary` / `?attr/colorTertiary`) so the overlay recolors them, or read the current theme in `applyRunningState` and pick `mono_*` vs `color_*` accordingly. Otherwise delete the unused `mono_fab_active` / `mono_connected` to avoid dead resources. |
| MEDIUM | `ui/MainActivity.kt:274-283` (`startConnectionTimer`) | The uptime timer uses `System.currentTimeMillis()` captured at UI time, reset to 0 in `onDestroy`/`stopConnectionTimer`. On `recreate()` (theme change / rotation) or reopening the app while the service is already running, `connectionStartTime` starts fresh from 0, so the displayed uptime resets to `00:00:00` instead of reflecting the real connection age. | Persist the real connection start (e.g. store epoch millis in the ViewModel or MMKV when the service enters RUNNING) and compute elapsed from that, rather than from an Activity-scoped field. |
| LOW | `ui/MainActivity.kt:199-206` (`handleFabAction`) | When the user taps to STOP, the method first calls `applyRunningState(isLoading = true, …)`, which sets the card to the "active" color and the status text to `toast_services_start` ("Start Services") for a moment before stopping — a misleading transient label on disconnect. | Only show the loading/"starting" state on the start branch; on the stop branch skip the `isLoading` styling (or use a neutral "stopping" label). |
| LOW | `res/drawable/ic_launcher_foreground.xml` (adaptive icon foreground) | Foreground vector content (globe ~y32–76, sparkle up to y26 in a 108 viewport) sits close to / slightly outside the adaptive-icon safe zone (~inner 66dp); on aggressive circular masks the sparkle top may be clipped. Not a build issue. | Scale the globe+sparkle group to ~70% and center within the inner 72dp safe zone (wrap in a `<group>` with scale/translate). |
| LOW | `res/values/colors.xml` (`mono_fab_active`, `mono_connected`) | Dead resources — defined in both `values/` and `values-night/` but never referenced (see MEDIUM mono issue). | Remove, or wire them up as part of the mono fix. |

## Things explicitly verified as OK (no action needed)

- **Binding IDs**: `cardConnect`, `layoutServerInfo`, `tvDownloadSpeed`, `tvUploadSpeed`,
  `tvConnectionStatus`, `tvSelectedServer`, `tvConnectionTime`, `tvTestState`, `tabGroup`,
  `viewPager`, `toolbar`, `drawerLayout`, `navView`, `progressBar` all exist in
  `activity_main.xml`. No lingering refs to removed `fab` / `layoutTest` / `ColorStateList` /
  old `ic_play/ic_stop/ic_fab_check`.
- `item_recycler_main.xml` kept every id the adapter uses (`item_bg`, `info_container`,
  `layout_indicator`, `tv_name`, `layout_subscription`, `tv_subscription`, `tv_type`,
  `tv_statistics`, `tv_test_result`, `layout_share/edit/remove/more`). `layout_indicator`
  changed `LinearLayout`→`View`; adapter only calls `setBackgroundResource`, valid on `View`.
- **Strings**: `menu_item_fast_connect`, `speed_zero`, `title_pref_color_theme`,
  `color_theme_blue`, `color_theme_mono`, `connection_test_testing_count` (`%d` matches the
  `Int` count) all present.
- **Drawables**: `bg_connect_ring`, `bg_nav_header`, `bg_server_card`, `bg_speed_chip`,
  `bg_type_chip`, `ic_power_settings`, `ic_launcher_foreground` created; `ic_cloud_download_24dp`,
  `ic_circle`, `ic_share/edit/delete/more_24dp` pre-exist.
- **Theme/attrs**: `ThemeOverlay.Mono` → `R.style.ThemeOverlay_Mono` matches; every `mono_*`
  and `md_theme_surfaceContainer*` token it/`themes.xml` references is defined in **both**
  `values/colors.xml` and `values-night/colors.xml`. `values-night/themes.xml` inherits
  `AppThemeBase`, so the surfaceContainer ramp fix applies in dark mode too. `?attr/colorOutlineVariant`,
  `colorSurfaceVariant`, `colorPrimaryContainer`, `colorTertiary` all resolve.
- **Icon**: adaptive `ic_launcher(.xml/_round.xml)` now points to `@drawable/ic_launcher_foreground`
  (valid vector); `ic_launcher_background` color exists.
- **Speed broadcast**: `longArrayOf(...)` is sent via `MessageUtil.sendMsg2UI` typed
  `Serializable` (uses the `Serializable` `putExtra` overload) and read back with
  `getSerializableExtra("content") as? LongArray` + `size >= 2` guard — round-trips correctly.
  `MSG_STATE_SPEED_UPDATE = 51` does not collide with existing codes. Division uses
  `sinceLastQueryInSeconds` (a `Double`, guaranteed `>= QUERY_INTERVAL_MS/1000`), no div-by-zero.
- **fastConnect logic**: `selectFastestServer` filters `delay in 1 until bestDelay`, correctly
  excluding `0`/`-1` (untested/failed) latencies; runs in `onTestsFinished()` after all pings
  complete; `pendingFastConnect` is always cleared. `serversCache`/`ServersCache.guid` public.
- `MmkvManager.decodeSettingsString(key, default)`, `encodeSettings(String, String?)`,
  `decodeServerAffiliationInfo(...).testDelayMillis`, `setSelectServer`, `Long.toSpeedString()`,
  `ListPreference` import in `SettingsActivity` all present.
- `BaseActivity` applies the mono overlay via `theme.applyStyle(...)` before `super.onCreate()`
  (correct order; MMKV already initialized in `App.onCreate`).
