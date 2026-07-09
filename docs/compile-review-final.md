# Compile-risk review — HEAD `f179ddf` (branch `claude/vpn-client-happ-design-mq51pv`)

Статический разбор рисков компиляции/линковки ресурсов после впитывания
salvage-изменений (HOME + SETTINGS redesign, TV rework). Android SDK недоступен —
проверка выполнена чтением кода и ресурсов. Файл — только отчёт, код не менялся.

Итог: **1 корневая причина → 19 ошибок компиляции (BLOCKER)**. Остальные проверенные
зоны риска чисты.

---

## BLOCKER

### B1. `menu_main.xml` редизайн выкинул 19 id, которые всё ещё используются в `MainActivity.onOptionsItemSelected` → 19 «unresolved reference»

**Severity: BLOCKER (сборка Kotlin падает).**

Файл `app/src/main/res/menu/menu_main.xml` был переписан и теперь содержит только
4 пункта: `import_qrcode`, `import_clipboard`, `tv_send`, `import_manually_vless`.
Раньше (коммит `56fb307`) в нём были объявлены ещё 19 id. Эти id больше **не объявлены
ни в одном ресурсе** проекта (проверено по всему `res/`), поэтому соответствующие
`R.id.*` в `MainActivity.kt` — неразрешённые ссылки. Kotlin-компиляция падает на каждой.

`R.id.*` резолвится по объединённому классу `R`, так что «пропавший пункт меню» сам по
себе не ломает сборку — ломает именно то, что id удалён отовсюду.

Точные места в `app/src/main/java/com/v2ray/ang/ui/MainActivity.kt`:

| Строка | Ссылка |
|-------|--------|
| 1094 | `R.id.import_local` |
| 1099 | `R.id.import_manually_policy_group` |
| 1104 | `R.id.import_manually_proxy_chain` |
| 1109 | `R.id.import_manually_vmess` |
| 1119 | `R.id.import_manually_ss` |
| 1124 | `R.id.import_manually_socks` |
| 1129 | `R.id.import_manually_http` |
| 1134 | `R.id.import_manually_trojan` |
| 1139 | `R.id.import_manually_wireguard` |
| 1144 | `R.id.import_manually_hysteria2` |
| 1149 | `R.id.export_all` |
| 1154 | `R.id.ping_all` |
| 1160 | `R.id.real_ping_all` |
| 1166 | `R.id.service_restart` |
| 1171 | `R.id.del_all_config` |
| 1176 | `R.id.del_duplicate_config` |
| 1181 | `R.id.del_invalid_config` |
| 1186 | `R.id.sort_by_test_results` |
| 1196 | `R.id.locate_selected_config` |

Примечание: `R.id.search_view` (стр. 1058) и `R.id.sub_update` (стр. 1191) НЕ ломаются —
они объявлены в других меню (`menu_bypass_list`/`menu_app_picker`/`menu_logcat` и
`action_sub_setting` соответственно), поэтому резолвятся. `R.id.tv_send` и
`import_manually_vless` присутствуют в новом `menu_main`. `R.id.nav_home/nav_servers/
nav_settings` — в `menu_bottom_nav`.

**Точное исправление (любой из вариантов):**

1. (рекомендуется) Вернуть 19 удалённых `<item android:id="@+id/…">` обратно в
   `res/menu/menu_main.xml` (можно вложенным `<menu>`/скрытыми `showAsAction="never"`),
   чтобы `R.id.*` снова существовали и всплывающее меню «+» (`showImportMenu`, которое
   инфлейтит `menu_main`) снова давало доступ к этим действиям. Список id взять из
   `git show 56fb307:V2rayNG/app/src/main/res/menu/menu_main.xml`.
2. Либо удалить 19 соответствующих ветвей `when` из `onOptionsItemSelected` (стр.
   1094–1199). Это уберёт ошибки компиляции, но безвозвратно отрежет функции (экспорт,
   ping all, restart, удаление дублей/невалидных, ручной импорт vmess/ss/socks/…, и т.д.).

Побочный (не блокирующий) эффект того же редизайна: `onCreateOptionsMenu` вызывает
`menu.findItem(R.id.search_view)`, но в новом `menu_main` пункта `search_view` нет →
`findItem` вернёт `null`. Это безопасно обработано (`if (searchItem != null)`); поиск на
вкладке «Серверы» идёт через `layoutServersHeader.etSearch`. Компиляцию не ломает.

---

## Проверено и ЧИСТО (ложных тревог не найдено)

### Settings redesign (наивысший риск по заданию) — консистентно
Все `binding.<field>` / поля item-биндингов сверены с id в макетах — совпадают:

- `SubSettingActivity` + `SubSettingRecyclerAdapter` ↔ `activity_sub_setting.xml`
  (`recycler_view`) + `item_recycler_sub_setting.xml` (`tv_name`,`tv_url`,`chk_enable`,
  `tv_last_updated`,`layout_edit`,`layout_remove`,`layout_url`,`layout_share`,
  `layout_last_updated`) — OK.
- `RoutingSettingActivity` + `RoutingSettingRecyclerAdapter` ↔ `activity_routing_setting.xml`
  (`layout_domain_strategy`,`tv_domain_strategy_summary`,`recycler_view`) +
  `item_recycler_routing_setting.xml` (`remarks`,`domainIp`,`outboundTag`,`chk_enable`,
  `img_locked`,`layout_edit`) — OK.
- `UserAssetActivity` + `UserAssetAdapter` ↔ `activity_user_asset.xml`
  (`layout_geo_files_sources`,`tv_geo_files_sources_summary`,`recycler_view`) +
  `item_recycler_user_asset.xml` (`asset_name`,`asset_properties`,`layout_edit`,
  `layout_remove`) — OK.
- `PerAppProxyActivity` + `PerAppProxyAdapter` ↔ `activity_bypass_list.xml`
  (`switch_per_app_proxy`,`switch_bypass_apps`,`layout_switch_bypass_apps_tips`,
  `recycler_view`) + `item_recycler_bypass_list.xml` (`icon`,`name`,`package_name`,
  `check_box`) — OK.
- `BackupActivity` ↔ `activity_backup.xml` (`layout_backup`,`layout_share`,`layout_restore`,
  `layout_webdav_config_setting`) + `dialog_webdav.xml` (`et_webdav_url`,`et_webdav_user`,
  `et_webdav_pass`,`et_webdav_remote_path` через `DialogWebdavBinding`) — OK.
- `ServerCustomConfigActivity` ↔ `activity_server_custom_config.xml` (`editor`,`et_remarks`) — OK.
- Правки `activity_server_*.xml` (vmess/vless/…/wireguard) — по +1 строке, это добавление
  `android:background="?android:attr/colorBackground"`, id не менялись — OK.

### MainActivity (тройной merge) — все биндинги резолвятся
Проверены все `binding.*` против `activity_main.xml` и его `<include>`-ов
(`layout_home_empty`, `layout_subscription_meta_bar` [id `layout_home_meta_bar`],
`layout_servers_header`, `layout_home_account`, `layout_servers_empty` [id `layout_empty`],
`layout_settings_content` [id `group_settings`]). Совпадает всё, включая
`layoutHomeEmpty.homeEmptyRoot/btnHomeAddQr/btnHomeAddClipboard`, всю мета-панель
(`btnPing/btnRefresh/btnPin/btnSupport/btnTelegram/tvSubTitle/tvAnnounce/tvTraffic/
tvExpiry/layoutTraffic/progressAction/progressTraffic/layoutMetaBody/btnCollapse`),
`appbarLayout`, `homeRoot`, `tvConnectionStatus`, `imgConnect`, `viewConnectGlow/Ring`,
`cardConnect`.
- Остаточных ссылок на удалённые вью НЕТ: `layoutServerInfo`, `tvTestState`,
  `tvSelectedServer`, `btnWebsite`, `handleLayoutTestClick`, `setTestState` — 0 совпадений.
- Символы connect-watchdog определены (`connectWatchdogRunnable`,
  `scheduleConnectWatchdog`, `cancelConnectWatchdog`).
- Импорты edge-to-edge на месте: `WindowCompat`, `ViewCompat`, `WindowInsetsCompat`,
  `updatePadding` (стр. 23–27).
- `onOptionsItemSelected` обрабатывает `R.id.tv_send` (стр. 1089). `when` — выражение с
  `else`, исчерпывающее.
- Все внешние символы разрешаются: `BaseActivity.THEME_MONO/THEME_BLUE`,
  `MainViewModel.{autoFallbackUsed,consumeFastConnectEvent,fastConnectAction,
  testCurrentServerRealPing,fastConnect,delayResultAction,getProviderGroups,
  availableProtocols,keywordFilter,protocolFilter}`, `MainRecyclerAdapter.{positionOfGuid,
  setSections,toggleCollapseAll,removeServerSub,setSelectServer,onItemLongClick}`,
  `SettingsChangeManager.{consumeRecreateUi,consumeRestartService,consumeSetupGroupTab,
  makeRestartService}`, `MemoryStatsManager.{currentUsedMb,levelFor,Level}`,
  `TemplateManager.isLocked`, `MmkvManager.{decodeSettingsLong,decodeStartOnBoot,
  encodeStartOnBoot}`.

### TvSendActivity — консистентно
`activity_tv_send.xml`: `btn_scan`,`btn_send`,`tv_status`,`radio_subs`,`layout_pick`,
`tv_instructions` ↔ используемые `btnScan/btnSend/tvStatus/radioSubs/layoutPick`.
`setContentViewWithToolbar` вызывается корректно. Все строки `tv_send_*` есть
(`res/values/strings_tv.xml`), drawables `ic_tv_24dp/ic_scan_24dp/ic_share_24dp`
существуют, `TvPairingProtocol.{parsePairUri,buildRequestJson,PairInfo,PAIR_PATH,
BEARER_PREFIX}` определены.

### THEME attrs — все объявлены и заданы
Все кастомные `?attr/` из изменённых макетов/кода
(`chipTypeText`,`chipJsonText`,`chipJsonBg`,`pingGood`,`pingBad`,`indicatorColor`,
`iconTint{Blue,Green,Orange,Purple,Red,Yellow}`,`iconTileBg{…}`,`connectActiveColor`,
`connectedColor`) объявлены в `res/values/attrs.xml` и заданы значениями и в `AppThemeBase`
(`values/themes.xml`), и в `ThemeOverlay.Mono`. `values-night/themes.xml` переопределяет
только `windowLight*StatusBar`, кастомные attr наследуются из `AppThemeBase`. `accentChip`
из списка задания нигде не используется — не проблема. `MainRecyclerAdapter`
`MaterialColors.getColor(?attr pingBad/pingGood/indicatorColor)` резолвится.

### Ресурсы — коллизий и битых ссылок нет
- Дубликатов имён ресурсов в `res/values/*.xml` и в `res/values-ru/*.xml` НЕ найдено
  (string/color/style/attr/dimen/array) — merge HOME+SETTINGS чист, `mergeResources` не
  упадёт.
- Все `@string/@drawable/@color/@style/@dimen/@array/?attr` из изменённых макетов
  существуют (проверено; «промахи» скрипта — библиотечные `Widget.Material3.*` и
  color-selector `res/color/bottom_nav_item_color.xml`).
- Удалённые `menu_drawer.xml`/`nav_header.xml` нигде не упоминаются.
- `MainRecyclerAdapter` ↔ `item_recycler_main.xml` + `item_section_header.xml` +
  `item_recycler_footer.xml` — id совпадают. Стиль `SettingsSectionLabel` объявлен.

### Cross-namespace R.attr — корректно
`colorPrimary` берётся из `androidx.appcompat.R.attr`, `colorOnSurface*` — из
`com.google.android.material.R.attr` (напр. `MainActivity` стр. 603–604, 624, 877–878).
Проблем не найдено.

---

## Итоговое резюме (RU)

Вероятных build-breaker'ов: **1 корневая причина = 19 ошибок компиляции Kotlin**
(все в `MainActivity.kt`).

- **B1 — 19× «unresolved reference» в `MainActivity.onOptionsItemSelected`**
  (`R.id.import_local`, `import_manually_{policy_group,proxy_chain,vmess,ss,socks,http,
  trojan,wireguard,hysteria2}`, `export_all`, `ping_all`, `real_ping_all`,
  `service_restart`, `del_all_config`, `del_duplicate_config`, `del_invalid_config`,
  `sort_by_test_results`, `locate_selected_config`; строки 1094–1196), потому что
  редизайн `res/menu/menu_main.xml` удалил эти id, а больше они нигде не объявлены.
  **Однострочная суть фикса:** вернуть удалённые `<item android:id="@+id/…">` в
  `res/menu/menu_main.xml` (из `56fb307`), либо убрать соответствующие ветви `when`.
