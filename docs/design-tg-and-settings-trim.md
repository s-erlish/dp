# Design — Telegram Login on Home + Minimal Settings (design only)

Scope: **design/spec only, no code changes.** App: *departament VPN* (v2rayNG/Xray fork,
`com.v2ray.ang`, Kotlin). Two deliverables:

- **A.** Move the "Sign in with Telegram" entry out of the (now-deleted) drawer and onto the **Home**
  screen, Incy-style.
- **B.** Cut the settings surface down to a short, curated Incy top-level list; everything else folds
  into one "Расширенные настройки (Xray Core)" row (the untouched legacy `SettingsActivity`) or is
  removed.

Grounded in the actual code: `res/layout/activity_main.xml` (Home = `group_home` NestedScrollView),
`res/xml/pref_settings.xml`, `res/menu/menu_drawer.xml`, and the existing auth scaffold
(`auth/BackendConfig.kt` `isConfigured()`, `auth/AuthManager.kt` `isLoggedIn()` →
`AuthTokenStore.isLoggedIn()`, `AuthTokenStore.getUser(): UserProfileDto?`,
`ui/LoginActivity.kt`). References: `docs/incy-redesign-spec.md`, `docs/impl-s4-settings.md`,
`docs/telegram-auth-design.md`, `docs/module4-auth-impl.md`.

---

# A) Telegram login on Home

## A.0 Decision (decisive)

**Placement:** a single, self-contained **account element** inserted as the **first child of the
`group_home` inner `LinearLayout`**, i.e. **above the inline stats row** (before the
`<!-- Inline stats -->` block at `activity_main.xml:57`), directly under the toolbar. It is a slim,
one-line element that reads as the "who am I / sign in" header of the Home scroll — tasteful, not a
second hero. It has two mutually-exclusive visual states in the same slot:

- **Signed out** → a tonal **"Войти через Telegram"** pill button (Telegram glyph + label).
- **Signed in** → an **account chip** (avatar/monogram + name + subscription-status subtitle).

**Rejected alternatives (and why):**
- *Between connect area and provider card* — competes with the connect shield and pushes the provider
  card down; makes Home feel like it has two CTAs. The connect shield must remain the single focal CTA.
- *Toolbar avatar/menu action only* — fine for the signed-in chip, but a signed-out **"Войти"** needs
  to be prominent, and a toolbar icon is too easy to miss for the primary onboarding action. Putting
  both states in one Home-header slot keeps it prominent when signed out and quiet when signed in.

**Anti-clutter guarantees:**
- The element is **entirely `GONE` unless `BackendConfig.isConfigured()`** is true. The current default
  build has a **blank** `BACKEND_BASE_URL`, so **Home looks exactly as it does today** until a backend
  is wired — zero visual change for the no-backend build.
- It is one compact ~52dp row, never a card stack; the signed-in chip replaces (not adds to) the
  signed-out button.

## A.1 Layout — exact insertion into `activity_main.xml`

Insert a new `<include>` as the **first child** inside the `group_home` inner `LinearLayout`
(currently `activity_main.xml` line ~56, immediately before the `<!-- Inline stats … -->`
`LinearLayout` at line 58):

```xml
<!-- Home account header: sign-in button (signed out) / account chip (signed in) -->
<include
    android:id="@+id/layout_home_account"
    layout="@layout/layout_home_account"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

New file `res/layout/layout_home_account.xml` — a `FrameLayout` root (so view-binding exposes
`binding.layoutHomeAccount`), `visibility="gone"`, `marginHorizontal=12dp`, `marginTop=8dp`,
containing **two overlapping children**, only one shown at a time:

**(1) Signed-out button — `btn_telegram_login`** (Incy tonal pill):
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_telegram_login"
    style="@style/Widget.Material3.Button.TonalButton.Icon"
    android:layout_width="match_parent"
    android:layout_height="52dp"
    android:text="@string/auth_sign_in_telegram"          <!-- "Войти через Telegram" -->
    android:textAllCaps="false"
    android:textStyle="bold"
    app:icon="@drawable/ic_telegram_24dp"
    app:iconGravity="textStart"
    app:iconTint="@null"
    app:cornerRadius="26dp"                                <!-- full pill -->
    app:backgroundTint="?attr/colorPrimaryContainer"       <!-- tonal blue, matches Incy -->
    android:textColor="?attr/colorOnPrimaryContainer"
    android:visibility="gone" />
```
Tonal (not a solid bright fill) matches the Incy rule that Home CTAs are restrained — the connect
shield stays the only bright control. Reuse the existing Material3 button family already used in
`layout_servers_empty.xml` (`Widget.Material3.Button.OutlinedButton.Icon`); tonal is the sibling style.

**(2) Signed-in chip — `chip_account`** (`LinearLayout`, horizontal, `bg_card_incy`-style rounded
surface, `?attr/selectableItemBackground`):
- `ImageView id=img_avatar` 36dp circle. **No image loader (Glide/Coil/Picasso) exists in the
  project** — for S4 render a **monogram**: circle `bg_icon_blue` + first letter of
  `displayName/username` in a centered `TextView id=tv_avatar_initial`. Loading `avatarUrl` is deferred
  (would require adding Coil/Glide — call out, do not add silently).
- Vertical text block (weight 1): `tv_account_name` (16sp bold, `?attr/colorOnSurface`) +
  `tv_account_sub` (12sp, `?attr/colorOnSurfaceVariant`) = subscription status/expiry.
- `ImageView` chevron `ic_chevron_right`, tint `?attr/colorOnSurfaceVariant`.

## A.2 Visibility logic (in `MainActivity`, e.g. an `updateAccountHeader()` called from
`onResume` and on login/logout return)

```kotlin
val configured = BackendConfig.isConfigured()
val loggedIn   = authManager.isLoggedIn()          // == AuthTokenStore.isLoggedIn()

binding.layoutHomeAccount.isVisible = configured                 // whole element hidden if no backend
binding.btnTelegramLogin.isVisible  = configured && !loggedIn
binding.chipAccount.isVisible       = configured &&  loggedIn

if (configured && loggedIn) {
    val u = AuthTokenStore.getUser()               // UserProfileDto?
    val name = u?.displayName ?: u?.username ?: getString(R.string.auth_account)
    binding.tvAccountName.text = name
    binding.tvAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "?"
    binding.tvAccountSub.text = subscriptionStatusLine()   // see A.3
}
```
`authManager` is a plain `AuthManager()` (no-arg ctor already exists, used by `AuthViewModel`); or call
the `AuthTokenStore` / `BackendConfig` objects directly to avoid holding an instance in `MainActivity`.
Call `updateAccountHeader()` in `onResume()` so returning from `LoginActivity` refreshes the state (the
existing `requestActivityLauncher`/`onResume` path already re-runs Home updates).

## A.3 Click → `LoginActivity` + subscription

- **Signed-out button click** and **signed-in chip click** both:
  `requestActivityLauncher.launch(Intent(this, LoginActivity::class.java))`
  (reuse the existing launcher so `consumeRecreateUi`/`consumeRestartService`/`consumeSetupGroupTab`
  return handling still fires). Signed-in tap opens `LoginActivity` acting as an account/status screen
  (logout lives there per `module4-auth-impl.md`; a dedicated `AccountActivity` is a later option).
- **Subscription for the signed-in subtitle:** login already imports the user's subscription via
  `SubscriptionSyncManager.importOrUpdate(...)` → it becomes a normal `SubscriptionItem`, and the
  **existing Home provider meta-bar (`layout_home_meta_bar`) already renders traffic/expiry/name**. So
  `subscriptionStatusLine()` should read the managed `SubscriptionItem` (via
  `AuthTokenStore.managedSubGuid()` → `MmkvManager.decodeSubscription(guid)`) and show e.g.
  `"Подписка активна · до 12.08"` or `"Подписка не активна"`. No new fetch/parse code — this reuses the
  subscription that login imported. If `SubscriptionInfoDto.expiresAt/status` were persisted at login,
  format from those instead.

## A.4 Drawer cleanup tie-in

The current `telegram_login` item in `menu_drawer.xml` (and its `onNavigationItemSelected` branch) is
**removed** — the drawer is deleted in S4 (`impl-s4-settings.md` §5 step 4). The Home account header is
its replacement. The `BackendConfig.isConfigured()` gate that the old drawer branch used is preserved
here as the whole-element visibility gate.

---

# B) Minimal settings

## B.1 Principle

The legacy `SettingsActivity` + `pref_settings.xml` are **left byte-for-byte unchanged** (per
`impl-s4-settings.md` §4) and become a single top-level row **"Расширенные настройки (Xray Core)"**.
So **FOLD = "not surfaced as a curated Incy row; still fully editable inside Расширенные"** — nothing is
lost. **KEEP = surfaced as a curated top-level row/toggle.** **REMOVE = genuinely unneeded for this
product; drop from UI (and delete the dead pref where noted).**

## B.2 Final top-level inventory for the S4 settings tab (decisive)

Grouped rounded cards under UPPERCASE labels, in this order:

| Section | Rows (top-level) | Type → destination/pref |
|---|---|---|
| **ОФОРМЛЕНИЕ** | **Тема** | PICKER → `pref_ui_mode_night` + `pref_color_theme` (theme picker, S5; S4 = single-choice dialog) |
| | **Язык** | PICKER → `pref_language` |
| | **Иконка приложения** | NAV (S5 app-icon alias; S4 = stub). *New — no existing pref.* |
| **СОЕДИНЕНИЕ** | **Соединение** | NAV → S5 detail (Автоподключение, Автозагрузка = `pref_is_booted`, Kill Switch, LAN, хотспот…). S4 = chevron stub. |
| **ПРОВАЙДЕРЫ** | **Провайдеры** | NAV → `SubSettingActivity` (existing) |
| | **Настройки пинга** | PICKER → `pref_ping_method` |
| **ПРОКСИ ПО ПРИЛОЖЕНИЯМ** | **Прокси по приложениям** | NAV → `PerAppProxyActivity` (existing) |
| **ПРИЛОЖЕНИЕ** | **О приложении** | NAV → `AboutActivity` (existing) |
| | **Резервное копирование** | NAV → `BackupActivity` (existing) |
| **ПРОИЗВОДИТЕЛЬНОСТЬ** | **Мониторинг памяти** | TOGGLE → `pref_show_memory` (shows/hides Home memory card) |
| **ОТЛАДКА** | **Логи** | NAV → `LogcatActivity` (existing) |
| **ДОПОЛНИТЕЛЬНО** | **Расширенные настройки (Xray Core)** | NAV → `SettingsActivity` (the untouched legacy prefs) |
| | *(folded, optional rows)* Маршрутизация → `RoutingSettingActivity`; Файлы ресурсов → `UserAssetActivity`; Проверить обновления → `CheckUpdateActivity`; Схемы URL-адресов → `UrlSchemeActivity` | NAV (existing activities) — keep reachable, but demoted under this group so top-level stays short |
| **(bottom)** | **Сбросить настройки** | red `TextView` + confirm dialog (reset scope defined in S5) |

That is **11 curated top-level rows** (Тема, Язык, Иконка, Соединение, Провайдеры, Настройки пинга,
Прокси по приложениям, О приложении, Резервное копирование, Мониторинг памяти, Логи) **+ the
Расширенные group + Сбросить** — matching the owner's requested list. The four "folded, optional" NAV
rows (Маршрутизация / Файлы ресурсов / Проверить обновления / Схемы URL) sit **inside the ДОПОЛНИТЕЛЬНО
group next to Расширенные** so no drawer destination is orphaned while the visible list stays small.

## B.3 Full mapping — every current setting

### `res/menu/menu_drawer.xml`
| Item | Class → | Verdict |
|---|---|---|
| `telegram_login` | `LoginActivity` | **REMOVE from settings** — relocated to **Home** (Part A). |
| `sub_setting` | `SubSettingActivity` | **KEEP** → row **Провайдеры**. |
| `per_app_proxy_settings` | `PerAppProxyActivity` | **KEEP** → row **Прокси по приложениям**. |
| `routing_setting` | `RoutingSettingActivity` | **FOLD** → ДОПОЛНИТЕЛЬНО group (Маршрутизация). |
| `user_asset_setting` | `UserAssetActivity` | **FOLD** → ДОПОЛНИТЕЛЬНО group (Файлы ресурсов). |
| `settings` | `SettingsActivity` | **KEEP** → row **Расширенные настройки (Xray Core)**. |
| `promotion` | `Utils.openUri(APP_PROMOTION_URL)` | **REMOVE** — ad/promo link, unneeded for this product. |
| `logcat` | `LogcatActivity` | **KEEP** → row **Логи**. |
| `check_for_update` | `CheckUpdateActivity` | **FOLD** → ДОПОЛНИТЕЛЬНО group (Проверить обновления). |
| `backup_restore` | `BackupActivity` | **KEEP** → row **Резервное копирование**. |
| `about` | `AboutActivity` | **KEEP** → row **О приложении**. |
| `placeholder` | (drawer version text) | **REMOVE** — dead with the drawer gone. |

*(New entry point not in the current drawer: `UrlSchemeActivity` → **FOLD** into ДОПОЛНИТЕЛЬНО as
"Схемы URL-адресов", optional.)*

### `res/xml/pref_settings.xml` — category by category

**UI category (`title_ui_settings`)**
| Pref | Verdict |
|---|---|
| `pref_speed_enabled` | **FOLD** (stays in Расширенные) |
| `pref_confirm_remove` | **FOLD** |
| `pref_start_scan_immediate` | **FOLD** |
| `pref_double_column_display` | **REMOVE** — obsolete: Home/Servers now use flat single-column rows (S3). Recommend deleting the pref entry. |
| `pref_group_all_display` | **REMOVE** — obsolete: subscription tabs dropped in S3 (flat list). Recommend deleting. |
| `pref_language` | **KEEP** → **Язык** |
| `pref_ui_mode_night` | **KEEP** → **Тема** |
| `pref_color_theme` | **KEEP** → **Тема** (same picker) |
| `pref_ping_method` | **KEEP** → **Настройки пинга** |
| `pref_auto_fallback` | **FOLD** → surfaced later in Соединение S5; stays in Расширенные for S4 |
| `pref_show_memory` | **KEEP** → **Мониторинг памяти** (toggle) |

**VPN category (`title_vpn_settings`)** — **all FOLD** (Расширенные; several resurface curated in the
S5 Соединение/Туннель detail screens over the same keys):
`pref_ipv6_enabled`, `pref_prefer_ipv6`, `pref_local_dns_enabled`, `pref_fake_dns_enabled`,
`pref_append_http_proxy`, `pref_vpn_dns`, `pref_vpn_bypass_lan`,
`pref_vpn_interface_address_config_index`, `pref_vpn_mtu`, `pref_use_hev_tunnel_v2`,
`pref_hev_tunnel_loglevel`, `pref_hev_tunnel_rw_timeout_v2`.

**Core category (`title_core_settings`)** — **all FOLD** (Расширенные):
`pref_sniffing_enabled`, `pref_route_only_enabled`, `pref_allow_insecure`, `pref_enable_local_proxy`,
`pref_proxy_sharing_enabled`, `pref_socks_port`, `pref_dynamic_socks_port`, `pref_socks_username`,
`pref_socks_password`, `pref_socks_enable_udp`, `pref_remote_dns`, `pref_domestic_dns`,
`pref_dns_hosts`, `pref_core_loglevel`, `pref_outbound_domain_resolve_method`.

**Mux category (`title_mux_settings`)** — **all FOLD**:
`pref_mux_enabled`, `pref_mux_concurrency`, `pref_mux_xudp_concurrency`, `pref_mux_xudp_quic`.

**Fragment category (`title_fragment_settings`)** — **all FOLD**:
`pref_fragment_enabled`, `pref_fragment_length`, `pref_fragment_interval`, `pref_fragment_packets`.

**Advanced category (`title_advanced`)** — **all FOLD** (Расширенные; some resurface in S5 details):
| Pref | Note |
|---|---|
| `pref_is_booted` | FOLD → resurfaces as "Автоподключение при загрузке" in Соединение S5 |
| `pref_auto_remove_invalid_after_test` | FOLD |
| `pref_auto_sort_after_test` | FOLD |
| `pref_delay_test_url` | FOLD (ping/latency; could resurface in a Настройки пинга detail) |
| `pref_real_ping_concurrency` | FOLD |
| `pref_ip_api_url` | FOLD |
| `pref_mode` | FOLD → resurfaces in Туннель S5 |

**Tally:** of ~50 raw prefs, **4 are surfaced top-level** (`pref_language`, `pref_ui_mode_night` +
`pref_color_theme` = one Тема row, `pref_ping_method`, `pref_show_memory`), **2 are REMOVED**
(`pref_double_column_display`, `pref_group_all_display`), and **the rest FOLD** into the single
Расширенные row — all still editable, nothing orphaned.

## B.4 Notes / call-outs

- Top-level rows that are pickers/toggles (Тема, Язык, Настройки пинга, Мониторинг памяти) write the
  **same MMKV keys** as the legacy screen, so values stay consistent whether edited in the curated tab
  or in Расширенные (`impl-s4-settings.md` §4).
- REMOVE for the two obsolete display prefs means deleting their `<CheckBoxPreference>` from
  `pref_settings.xml` (the only place they surface). REMOVE for `promotion`/`placeholder` is just not
  recreating them as rows (drawer deleted). None of these have runtime consumers worth keeping.
- No new image-loading dependency is introduced for the Home avatar; monogram fallback keeps S4
  dependency-free (avatar-image loading is a deferred, explicit decision).

---

## Summary (8 lines)
1. TG login moves to a Home **account header** = new `<include layout_home_account>` inserted as the **first child of `group_home`'s LinearLayout, above the stats row** (before `activity_main.xml:58`).
2. One slot, two states: signed-out = tonal **"Войти через Telegram"** pill (`Widget.Material3.Button.TonalButton`, `colorPrimaryContainer`, `ic_telegram_24dp`); signed-in = account chip (monogram avatar + name + subscription line).
3. Visibility: whole element `GONE` unless `BackendConfig.isConfigured()`; button when `!isLoggedIn()`, chip when `isLoggedIn()` (`AuthTokenStore`); both click → `requestActivityLauncher.launch(LoginActivity)`; refresh in `onResume`.
4. Signed-in subtitle reuses the subscription login already imported (managed `SubscriptionItem`) — no new fetch/parse; the current build (blank base URL) shows Home unchanged.
5. Minimal settings top-level (11 rows): ОФОРМЛЕНИЕ(Тема/Язык/Иконка) · Соединение · Провайдеры · Настройки пинга · Прокси по приложениям · О приложении · Резервное копирование · Мониторинг памяти(toggle) · Логи, then ДОПОЛНИТЕЛЬНО(Расширенные Xray Core + folded Маршрутизация/Файлы/Обновления/URL) and red Сбросить.
6. KEEP maps to existing activities/prefs; only 4 raw prefs surface top-level (language, ui_mode_night+color_theme→Тема, ping_method, show_memory); Соединение/Туннель/Иконка are S5 stubs.
7. FOLD = everything else in `pref_settings.xml` (VPN/Core/Mux/Fragment/Advanced, ~44 prefs) stays fully editable inside the untouched legacy `SettingsActivity` behind the one "Расширенные" row.
8. REMOVE = `pref_double_column_display`, `pref_group_all_display` (obsolete flat list), drawer `promotion` + `placeholder`, and the drawer `telegram_login` item (relocated to Home).
</content>
</invoke>
