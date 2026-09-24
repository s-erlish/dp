repo: s-erlish/dp
branch: master
path: V2rayNG/app/src/main/res

## Last sync

date: 2026-08-05T09:24:11Z

### Updated in this project

- Воссозданы текущие экраны: главная, начальный, два подэкрана настроек
- Единая система нажатия (лестница 0.98 / 0.96 / 0.94 / 0.90) и каталог из семи мини-анимаций с правилами применения
- Начальный экран: четыре варианта, без нижних вкладок, с автоопределением ссылки в буфере
- Кнопка подключения: свет-комета по кольцу, сонар при подтверждении, редкая пульсация кольца
- Выбор из списка — окошко у значения вместо модального диалога (Режим, DNS, Пинг, стратегия, интервал, сортировка)
- Переработано восемь подэкранов настроек
- Три варианта кнопки подключения, список серверов на главной и три варианта вкладки «Аккаунт»
- Вкладка «Настройки» целиком: шесть разделов, состав строк выверен по скриншотам релиза 2.2.1

## Screen map

| Экран в проекте | Файлы репозитория |
| --- | --- |
| 1a Главная · подключено | layout/activity_main.xml, layout/layout_home_account.xml, layout/layout_subscription_meta_bar.xml, layout/item_recycler_main.xml, drawable/bg_connect_ring_mono.xml, drawable/bg_card_incy.xml, drawable/bg_server_row.xml, drawable/bg_bottom_nav_scrim.xml, drawable/bg_nav_dot.xml |
| 1b Начальный экран | layout/layout_home_empty.xml, layout/activity_main.xml (tv_home_welcome), values/strings.xml, values-ru/strings.xml |
| 1c Резервирование конфигурации | layout/activity_backup.xml, values/styles.xml (SettingsSectionLabel), values-ru/strings.xml |
| 1d Проверить обновление | layout/activity_check_update.xml, values-ru/strings.xml |
| 2a Система нажатия | anim/press_scale.xml, anim/nav_press.xml, anim/connect_confirm.xml, values/motion.xml, interpolator/ease_out_quart.xml, interpolator/ease_out_quint.xml |
| 2b–2d Начальный экран (редизайн) | layout/layout_home_empty.xml, layout/layout_home_account.xml, values/strings.xml, values-ru/strings.xml |
| 2e–2f Подэкраны настроек (редизайн) | layout/activity_backup.xml, layout/activity_check_update.xml, layout/layout_setting_row.xml, layout/layout_setting_toggle_row.xml, values/dimens.xml, values/styles.xml |
| 3a–3b Кнопка подключения и каталог анимаций | anim/connect_confirm.xml, anim/press_scale.xml, anim/nav_press.xml, values/motion.xml, drawable-night/bg_connect_ring.xml, drawable-night/bg_connect_glow.xml, drawable/ic_shield_outline.xml, drawable/ic_shield_filled.xml |
| 3c Начальный экран (финал) | layout/layout_home_empty.xml, values/strings.xml, values-ru/strings.xml |
| 3d Прокси по приложениям | layout/activity_bypass_list.xml, layout/item_recycler_bypass_list.xml, layout/activity_app_picker.xml, values/strings_perapp.xml, drawable/ic_hub_local_proxy.xml, drawable/ic_lp_hide.xml |
| 3e DNS | layout/layout_settings_content.xml (row_dns), values/strings.xml (settings_dns, settings_dns_hint), values/strings_settings_hub.xml (dns_preset_names / dns_preset_values) |
| 3f Пинг | layout/layout_settings_content.xml (row_ping_method), values/strings.xml (settings_ping_method*), values/arrays.xml (ping_method_entries) |
| 4d Соединение · окошко у значения | values/arrays.xml (mode_value, routing_domain_strategy, ping_method_entries), values-ru/strings.xml (settings_mode_vpn, settings_mode_proxy), values/strings_settings_hub.xml (dns_preset_*) |
| 4e Настройки провайдеров | layout/activity_provider_settings.xml, values/strings_provider.xml |
| 5a–5c Кнопка подключения | anim/connect_confirm.xml, values/motion.xml, drawable-night/bg_connect_ring.xml, drawable/ic_shield_outline.xml, drawable/ic_shield_filled.xml |
| 5d Сервера | layout/layout_servers_header.xml, layout/item_recycler_main.xml, layout/item_section_header.xml, layout/layout_servers_empty.xml, drawable/bg_server_row.xml, drawable/bg_type_chip.xml, drawable/bg_search_pill.xml |
| 5e–5g Аккаунт | layout/activity_account.xml, values/strings_account.xml |
| Прототип: главная, аккаунт, настройки (переключение темы) | layout/activity_main.xml, layout/layout_settings_content.xml, layout/activity_account.xml, layout/item_recycler_main.xml, values/themes.xml (ThemeOverlay.Mono), values-night/colors.xml, drawable/ic_acc_*.xml |
| Прототип: подэкраны и потоки | values/strings_devices.xml, values/strings_account.xml, values/strings_perapp.xml, layout/activity_bypass_list.xml, layout/item_recycler_bypass_list.xml |
| Прототип: подэкраны настроек | layout/activity_local_proxy.xml, values/strings_local_proxy.xml, layout/activity_routing_setting.xml, layout/item_recycler_routing_setting.xml, layout/activity_user_asset.xml, layout/item_recycler_user_asset.xml, values/strings_provider.xml, layout/activity_tv_send.xml, values/strings_tv.xml, layout/activity_logcat.xml, layout/activity_about.xml, layout/activity_check_update.xml, layout/activity_backup.xml |
| 6a Настройки целиком | layout/layout_settings_content.xml, values/strings.xml (settings_section_*), values-ru/strings.xml, values/strings_perapp.xml, values/strings_provider.xml |
| 4f Туннель | layout/layout_settings_content.xml (row_mux, row_fragment), values-ru/strings.xml (settings_mux*, settings_fragment*), values/arrays.xml (routing_domain_strategy) |

## Notes

- Токены взяты из values-night/colors.xml (моно-тема: mono_* под ThemeOverlay.Mono), values/dimens.xml, values/styles.xml, values/motion.xml
- Скриншоты пользователя (моно-тема) показывают порядок блоков на главной, отличающийся от master: «+» на отдельной строке, статус-пилюля «Подключено» под щитом, строка скорости под ней. Воссоздание следует скриншотам
- Иконки перенесены из drawable/ic_*.xml как inline SVG (те же pathData)
- Фон главной: drawable-night/bg_home_gradient_mono.xml (радиальный 560dp, центр 0.5/0.30, #161618 → #0D0D0F → #000000)
- Плитки bg_icon_blue/green/orange/purple ссылаются на ?attr/iconTileBg*, а ThemeOverlay.Mono уводит их все в mono_surfaceContainerHighest (#232326) — поэтому в моно-теме плитки нейтральные
