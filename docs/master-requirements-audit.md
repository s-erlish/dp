# Master Requirements Audit — departament VPN

Audit date: 2026-07-09 · Scope: `/home/user/dp` (v2rayNG/Xray fork, `com.v2ray.ang`, app under `V2rayNG/`).
Method: read-only. Cross-checked current source (`V2rayNG/app/src/main`), all design/review docs in
`docs/`, and git history (`d9e68a8`…`d541aa4`). No code was modified.

## Legend
- **DONE** — implemented and (per prior static reviews) correct.
- **PARTIAL** — some sub-parts shipped; concrete gaps remain.
- **TODO** — not started in code (may have a design doc only).
- Priority: **P0** critical/blocker · **P1** core UX to reach Incy parity · **P2** important feature · **P3** nice-to-have/future.

The North-Star for the UI work is `docs/incy-redesign-spec.md`, staged S1–S5:
**S1 (palette/nav/memory) ✅ · S2 (shield+stats) ✅ · S3 (Servers tab) TODO · S4 (Settings tab) TODO · S5 (detail screens) TODO.**

---

## Requirement-by-requirement status

### 1. Home / Incy 1:1 UI
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 1a | Dark theme + blue accent | **DONE** | `res/values/colors.xml`, `themes.xml`, `ThemeOverlay.Mono`; commits `d9e68a8`, `4454ffb`. Reviewed in review-01. |
| 1b | Shield connect button + glowing ring | **DONE (S2)** | `activity_main.xml` `card_connect` + `@drawable/bg_connect_ring` + `img_connect`=`ic_shield_outline`; commit `d541aa4`. |
| 1c | Inline stats row ↑/uptime/↓ | **DONE (S2)** | `activity_main.xml` `tv_upload_speed`/`tv_connection_time`/`tv_download_speed`; `MSG_STATE_SPEED_UPDATE` path (review-01). |
| 1d | Current-server line w/ flag | **PARTIAL** | `layout_server_info` + `tv_selected_server` exist but prefixed with a static `ic_cloud_download_24dp` icon, **not** a country flag. `util/FlagUtil.kt` exists (used on rows + notification) but is not applied to the home server line. |
| 1e | App-memory card (toggleable) | **DONE** | `card_memory` in `activity_main.xml`; `pref_show_memory` toggle; `util/MemoryStatsManager.kt` (Java-heap); commit `902b804`, review-07. |
| 1f | "Проверить" button | **TODO** | Only an inline `tv_test_state` label exists. The dedicated outlined "Проверить" pill (spec §6) is not built. |
| 1g | Provider card collapsible | **TODO** | The provider meta bar (`layout_subscription_meta_bar.xml`) lives on the Servers tab and is **not** collapsible. Spec wants it restyled + collapsible on Home. |
| 1h | Server list ON HOME | **TODO** | Home (`group_home` NestedScrollView) has no server list; servers remain in the Servers-tab ViewPager. |

### 2. Servers tab (S3)
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 2a | Remove Default/import-sub tabs | **TODO** | `activity_main.xml` `group_servers` still hosts `TabLayout tab_group` + `ViewPager2 view_pager` (`GroupPagerAdapter`/`GroupServerFragment`). |
| 2b | Title + circular buttons (collapse-all/refresh/speedtest/add) | **TODO** | No such header. |
| 2c | "N серверов · M провайдер" subtitle | **TODO** | Not present. |
| 2d | Search | **TODO** | No search field on the Servers tab. |
| 2e | Protocol filter chips (Все/VLESS/Shadowsocks) | **TODO** | Not present. |
| 2f | Collapsible provider section | **TODO** | Not present. |
| 2g | Flat rows w/ flag+name+chips+ping | **PARTIAL** | Flag tiles + name + chips + ping already render in `item_recycler_main.xml` / `MainRecyclerAdapter` (commit `de34b9a`), but rows still sit inside per-tab lists, not the flat flag-left design. |
| 2h | Remove inline share/edit/delete (move to long-press/sheet) | **TODO** | `item_recycler_main.xml` still has `layout_share/edit/remove/more`; `MainRecyclerAdapter` wires them inline. |
| 2i | Empty state (add from clipboard / QR) | **TODO** | No empty-state view. |

### 3. Settings as a bottom-nav TAB (S4)
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 3a | Settings is a bottom-nav tab | **TODO** | Bottom nav is Home/Servers/**More**; `nav_more` calls `binding.drawerLayout.openDrawer(...)` (`MainActivity.kt:165-166`). Settings is still the legacy `SettingsActivity` (PreferenceFragment over `res/xml/pref_settings.xml`) opened from the drawer. |
| 3b | Grouped rounded cards, colored icons, iOS toggles, value+chevron rows | **TODO** | None of the custom Incy components (`bg_card_incy`, `bg_icon_*`, `SettingRow`, iOS switch) exist. |
| 3c | Section set (Оформление/Соединение/Туннель/Провайдеры/Приложение/Производительность/Отладка/Сбросить) | **PARTIAL (data only)** | The underlying prefs exist scattered in `pref_settings.xml` (theme, language, ping method, auto-fallback, memory, mux/fragment, DNS, socks, etc.) but not organized into the Incy sections/screens. See `docs/incy-settings-design.md` for the target mapping. |
| 3d | Remove hamburger drawer | **TODO** | `activity_main.xml` still declares `NavigationView nav_view` + `menu_drawer`; `MainActivity` implements `NavigationView.OnNavigationItemSelectedListener` and opens the drawer. |

### 4. Detail screens (S5)
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 4a | Connection detail (Автоподключение, Автоподключение при загрузке, Kill Switch, Разрешить LAN, Доступ через хотспот, LAN через прокси) | **TODO / PARTIAL data** | No detail screen. Some primitives exist as prefs/receivers (`receiver/BootReceiver.kt`, `pref_vpn_bypass_lan`, per-app proxy) but there is no Автоподключение / Kill Switch / hotspot toggle surface. |
| 4b | Provider-settings detail (Автообновление/Интервал/Уведомлять/Обновлять при запуске/Пинг при запуске/Пинг при обновлении/Отправлять HWID/USER-AGENT) | **PARTIAL** | `SubEditActivity`/`SubSettingActivity` + `SubscriptionItem` already carry autoUpdate/interval/userAgent; the rest (Уведомлять, ping-on-start/update, HWID) and the Incy grouped-toggle layout are TODO. |

### 5. Theme / language / app-icon pickers
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 5a | Theme picker (blue/mono + light/dark) via Оформление→Тема | **PARTIAL** | `pref_color_theme` (blue/mono) + `pref_ui_mode_night` ListPreferences exist and work, but reachable only through legacy settings — not the Оформление→Тема detail. |
| 5b | Language picker | **PARTIAL** | `pref_language` ListPreference exists (legacy settings), not the Incy row. |
| 5c | App-icon alias chooser | **TODO** | Zero `activity-alias` in `AndroidManifest.xml`; no icon-chooser UI. |

### 6. Server country flags + collapsible groups
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 6a | Country flags on the left of rows | **DONE** | `util/FlagUtil.kt` + `bg_flag_tile.xml` + `tv_flag` in `item_recycler_main.xml`; commit `de34b9a`; review-07. |
| 6b | Flags derived from Remnawave hosts (emoji/geoip) | **PARTIAL** | Flags are parsed from remark text / country names, not from host geoip. review-07 flags false positives ("IT support"→IT) and the bad `UK`→🇺🇰 glyph. |
| 6c | Collapsible groups | **TODO** | Grouping is by subscription tab, not collapsible provider sections (tied to S3). |

### 7. Rich pinned notification
| Status | Evidence / gap |
|---|---|
| **DONE** | `handler/NotificationManager.kt` — flag + server name (`FlagUtil.stripLeadingFlag`), chronometer uptime (`setUsesChronometer`), live ↓/↑ via `updateNotification`, and a stop action (`addAction` → `notification_action_stop_v2ray`, effectively the on/off toggle since the notification only exists while running). Commit `abd5ab4`; review-07. Minor: verify the toggle re-labels appropriately; design in `docs/notification-design.md`. |

### 8. Ping methods / auto-fallback / fast connect
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 8a | 4 selectable ping methods | **DONE** | `enums/PingMethod.kt`, `handler/SpeedtestManager.kt`, `pref_ping_method`; commit `eddfac4`; review-03. **Gap (P2):** review-03 HIGH — "HTTP" method probes one fixed URL, cannot differentiate servers; review-04 MEDIUM — `https://host:port` probe misclassifies non-TLS nodes. |
| 8b | Protocol auto-fallback | **DONE — blocker fix landed, needs re-review** | Feature: `52a67a1`. review-04 flagged a **reconnect-loop BLOCKER**; fix committed in **`a68139a`** (`autoFallbackUsed` moved to ViewModel so it survives recreate + the fallback's own restart, reset only on user connect at `MainActivity.kt:247/284`, and the failed server is excluded via `fastConnectExcludeGuid`). Loop logic now looks correct on read. **Not yet re-reviewed**; review-04 MEDIUMs remain (single transient failure still switches; `MSG_MEASURE_DELAY` result channel shared with the manual test tap). |
| 8c | Fast connect | **DONE** | `c9b9de3`; review-01 HIGH replay bug addressed in `5d60ecc`. Note: `fastConnectAction` is still a plain `MutableLiveData<String?>` (`MainViewModel.kt:58`) — confirm the observer nulls it after handling. |

### 9. Hidden JSON templates / locked profiles (Remnawave/3x-ui/Happ)
| Status | Evidence / gap |
|---|---|
| **TODO** | Design only: `docs/hidden-templates-design.md`. No code (no non-readable/locked-profile handling in `dto/entities/ProfileItem.kt` or `fmt/`). |

### 10. Telegram auth + subscription pull (+ future in-app payments)
| Status | Evidence / gap |
|---|---|
| **PARTIAL** | Scaffold DONE: `auth/` package (`AuthManager`, `AuthTokenStore`, `DepartamentApiClient(Impl)`, `SubscriptionSyncManager`), `ui/LoginActivity.kt`, `viewmodel/AuthViewModel.kt`; commit `2e5b59b`; `docs/module4-auth-impl.md`. Ships with blank `BACKEND_BASE_URL` (inert until configured). review-05 HIGH (deep-link dropped by StateFlow conflation) fixed in `f997eae`. **Gaps:** tokens stored in plain MMKV (review-05 MEDIUM), `refreshIfNeeded()`/401 handling inert, double initial sub-fetch. **In-app payments: TODO (future).** |

### 11. Circumvention settings UX (mux, fragment/packet-noise, uTLS fingerprint)
| Status | Evidence / gap |
|---|---|
| **TODO (self-serve UX)** | Base per-config prefs exist in advanced settings (`pref_mux_*`, `pref_fragment_packets`, uTLS fingerprint per node) — inherited from v2rayNG. The self-serve preset / global uTLS-fallback panel in `docs/circumvention-settings-design.md` is not built. |

### 12. Smart TV / Android TV + QR Wi-Fi subscription transfer
| Status | Evidence / gap |
|---|---|
| **PARTIAL** | Manifest-level TV support present (inherited): `AndroidManifest.xml` declares `android.software.leanback`, `LEANBACK_LAUNCHER`, and `android:banner=@mipmap/ic_banner`. **Gaps (TODO):** D-pad-optimized/any-resolution UI, and the QR Wi-Fi subscription-transfer flow in `docs/smart-tv-transfer-design.md`. |

### 13. RAM panel / low-mem discipline / future Windows-Linux portability
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 13a | RAM panel (Java-heap metric) | **DONE** | `util/MemoryStatsManager.kt` reports `Runtime.totalMemory() − freeMemory()` in MB; `card_memory`; commit `902b804`; `docs/memory-panel-design.md`. |
| 13b | Low-memory / perf discipline | **ONGOING** | 2s poll cadence, callbacks removed on lifecycle (review-07). Keep watching OkHttp client churn (review-03 MEDIUM: per-server client build). |
| 13c | Windows/Linux core-logic portability | **TODO (future)** | Not started. |

### 14. Branding + responsive resolutions
| # | Sub-requirement | Status | Evidence / gap |
|---|---|---|---|
| 14a | departament name/logo/icon | **DONE** | Launcher icon (`818f257`), wordmark (`7f9e244`), TV banner. |
| 14b | Support all phone resolutions (responsive/dimens) | **PARTIAL** | Layouts use wrap/match + a single `res/values/dimens.xml` and one `values-sw360dp-v13` bucket. No tablet/landscape/large-screen dimens buckets; some hero sizes are hardcoded dp (e.g. 230dp/176dp ring). Adequate for typical phones; verify small (≤5") and large screens. |

---

## Prioritized gap list

### P0 — verify before shipping
1. **Auto-fallback reconnect-loop fix re-review (8b).** `a68139a` addresses the review-04 BLOCKER; get a fresh review/QA to confirm no loop and that the shared `MSG_MEASURE_DELAY` channel (review-04 MEDIUM) can't cross-consume the manual test tap. Files: `ui/MainActivity.kt`, `viewmodel/MainViewModel.kt`.
2. **Fast-connect replay guard (8c).** Confirm the `fastConnectAction` observer nulls the value after handling so a recreate can't auto-connect. `viewmodel/MainViewModel.kt:58,582`, `ui/MainActivity.kt:186`.

### P1 — Incy parity, the bulk of remaining UI work (S3→S5)
3. **S3 Servers tab (2a–2i):** drop `TabLayout`/`ViewPager2`; build title + circular action buttons, "N серверов · M провайдер", search, protocol chips, collapsible provider section, flat flag-left rows, move share/edit/delete to long-press/bottom sheet, empty state. Files: `activity_main.xml`, `item_recycler_main.xml`, `MainRecyclerAdapter`, retire `GroupServerFragment`/`GroupPagerAdapter`.
4. **S4 Settings tab + drawer removal (3a–3d):** replace `nav_more`→drawer with a `nav_settings` tab that opens a custom grouped-card screen; remove `NavigationView`/`menu_drawer`/hamburger. Build reusable components (`bg_card_incy`, `bg_icon_*`, `SettingRow`, iOS switch). Files: `menu_bottom_nav.xml`, `activity_main.xml`, `MainActivity.kt`, new settings fragment/layouts; target map in `docs/incy-settings-design.md`.
5. **Home completion (1d, 1f, 1g, 1h):** flag on the current-server line, "Проверить" pill, collapsible provider card on Home, and the server list on Home.
6. **S5 detail screens (4a, 4b) + pickers (5a, 5b, 5c):** Соединение detail (auto-connect / boot / kill-switch / LAN / hotspot), Provider-settings detail, Тема/Язык pickers reachable from Оформление, and the app-icon `activity-alias` chooser.

### P2 — feature depth
7. **Telegram auth hardening (10):** encrypt token storage (Android Keystore), wire `refreshIfNeeded()`/401 handling, de-dupe initial sub fetch — before enabling a real backend.
8. **Ping accuracy (8a):** make HTTP/direct methods per-node or relabel as "internet reachability"; share one `OkHttpClient`.
9. **Flag accuracy (6b):** require explicit `[XX]`/leading-token country parsing; drop/​map `UK`→`GB`.
10. **Circumvention self-serve UX (11):** presets + global uTLS fallback per `docs/circumvention-settings-design.md`.
11. **Collapsible provider groups (6c):** folds into S3.

### P3 — future / platform
12. **Hidden/locked JSON templates (9)** — `docs/hidden-templates-design.md`.
13. **Smart-TV D-pad UI + QR Wi-Fi transfer (12)** — `docs/smart-tv-transfer-design.md`.
14. **In-app payments (10, future).**
15. **Windows/Linux core portability (13c).**
16. **Responsive dimens buckets for tablets/landscape (14b).**

---

## Recommended execution order
1. **Close P0** (re-review/QA the auto-fallback fix + confirm fast-connect null-clear). Cheap, unblocks confidence in the resilience story.
2. **S3 — Servers tab** (gap #3). Highest-visibility parity gap; also delivers collapsible groups (6c) and the flat-row/long-press model reused on Home.
3. **S4 — Settings tab + remove drawer** (gap #4). Establishes the reusable grouped-card/icon/toggle component kit that S5 and Home reuse; retires the hamburger.
4. **Home completion** (gap #5) using the row + provider-card components from S3, and the memory/stats already in place.
5. **S5 — detail screens + pickers + app-icon alias** (gap #6). Depends on the S4 component kit.
6. **P2 depth** in parallel where owned by different people: auth hardening (#7), ping/flag accuracy (#8, #9), circumvention UX (#10).
7. **P3 platform/future** (#12–#16) after Incy phone parity is shipped.

## Doc → requirement index (for maintainers)
`incy-redesign-spec.md`→1,2,3,4 (staging) · `incy-settings-design.md`→3 · `subscription-meta-bar-design.md`→1g · `server-flags-design.md`→6 · `notification-design.md`→7 · `ping-methods-design.md`→8a · `hidden-templates-design.md`→9 · `telegram-auth-design.md`+`module4-auth-impl.md`→10 · `circumvention-settings-design.md`→11 · `smart-tv-transfer-design.md`→12 · `memory-panel-design.md`→13a · `design-system-2026.md`→theme · `happ-parity-details.md`/`incy-analysis.md`/`incy-repo-findings.md`/`ux-recommendations.md`/`strategy-russia-2026.md`/`new-modules-proposals*.md`→reference/strategy · `review-01..07`→landed-work QA.
