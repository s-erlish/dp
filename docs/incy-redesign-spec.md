# Incy 1:1 Redesign Spec (departament VPN)

Derived from 7 reference screenshots of the **Incy** app (v3.3.0). The fork is used only for its
core/settings; the UI must be rebuilt to match Incy. Dark-only, blue accent, iOS-style grouped cards.

## Palette (dark, default)
- background `#0A0B0D` (near-black) · surface/cards `#141619` · surfaceVariant/chips `#1E2126`
- text `#F2F4F8` · secondary text `#8A909C` · outline `#2A2E36` / `#20242B`
- primary blue `#4C8DFF` · purple (download) `#9B7DFF` · green (ok) `#22C55E` · red (bad ping) `#F04452`
- Rounded corners: cards 20–24dp, chips 8dp, colored setting icons 12dp (44dp box), pills full.
- Toggles: iOS pill switches (grey off / blue on). Section labels: UPPERCASE grey, small.

## Bottom nav (floating, compact)
3 tabs: **Главная · Сервера · Настройки**. Active = blue rounded-pill indicator behind icon+label,
blue tint; inactive grey. Small icons (22dp) + 11sp bold labels. No hamburger anywhere.

## HOME tab
1. Top bar: brand wordmark (blue "departament") + version; right = globe/language button.
2. Inline stats row (no cards, centered): `↑ 26 B/s` (blue) · `🕐 3:11:08` uptime · `↓ 40 B/s` (purple).
3. Big connect control: a large (~300dp) **dark circle** with a soft glowing ring (concentric),
   centered **shield outline icon** (blue). States: idle = dim grey-blue shield; connecting =
   pulsing; connected = bright blue shield + brighter ring. NOT a solid filled bright button.
4. Current server line: `🇪🇺 Hybrid (Автовыбор)` (flag emoji + name, blue-ish), centered.
5. Memory card (only if "Мониторинг памяти" on): thin-outlined rounded card, green dot +
   `25 MB · Норма`.
6. `Проверить` pill button (outlined, blue icon+text) — runs latency test.
7. `ТЕКУЩИЙ ПРОВАЙДЕР` label → provider card (collapsible chevron + 🍀 name + age + circular
   refresh/speedtest/… buttons + traffic bar `∞ ── 1,72 TB / ∞` + announce + `Поддержка` +
   date · count). This is essentially the current meta bar, restyled + collapsible.
8. **Server list right here** (below provider), same rows as Servers tab.

## SERVERS tab
- Title `Сервера` + right circular buttons: collapse-all (▲), refresh (↻), speedtest (gauge),
  add (+, blue). Subtitle `15 серверов · 1 провайдер`.
- Search pill `Поиск серверов…`.
- Protocol filter chips: `Все` (blue active) · `VLESS` · `Shadowsocks` — filter by protocol,
  **NOT subscription tabs**. (Remove the Default/import-sub TabLayout entirely.)
- Provider section card (collapsible) then server rows. When no servers: empty state offering
  "Добавить из буфера" / QR.

## Server row (flat, no card border, dividers between)
`[flag rounded-square 44dp] Name(bold)  \n [protocol chips] · TCP · REALITY      [•ping dot] 454ms`
- Chips: `Auto`/`VLESS` blue chip, `JSON` gold chip, transport in grey text.
- Ping dot: green ok / red bad; `n/a` red when untested/failed.
- Selected row = blue rounded outline + slightly lighter bg.
- **No inline share/edit/delete icons** — those move to long-press / a bottom sheet.

## SETTINGS tab (custom, replaces the drawer + PreferenceFragment)
Grouped rounded cards under UPPERCASE section labels. Each row = colored rounded-square icon +
title (+ optional subtitle) + right value(grey)/chevron OR iOS toggle OR dropdown.
Sections & rows (Incy parity, map to existing prefs/activities):
- **ПРОКСИ ПО ПРИЛОЖЕНИЯМ**: Прокси по приложениям (Выкл ›) → PerAppProxyActivity
- **ОФОРМЛЕНИЕ**: Тема (Тёмная ›) → theme picker; Язык (Как в системе ›); Иконка приложения ›
- **СОЕДИНЕНИЕ**: Соединение (Настроить ›) → detail screen w/ toggles: Автоподключение, Автоподключение при загрузке, Kill Switch, Разрешить LAN, Доступ через хотспот, LAN через прокси
- **ТУННЕЛЬ**: Туннель (Настроить ›)
- **ПРОВАЙДЕРЫ**: Настройки провайдеров (Авто ›) → toggles Автообновление, Интервал обновления (dropdown), Уведомлять; Обновлять при запуске; Пинг при запуске/обновлении; Отправлять HWID; USER-AGENT field. · Настройки пинга (HTTP GET ›) → the 4 ping methods (Module 2)
- **ПРИЛОЖЕНИЕ**: О приложении (departament ›); Схемы URL-адресов; Резервное копирование; Оценить приложение
- **ПРОИЗВОДИТЕЛЬНОСТЬ**: Расширенные настройки (Xray Core ›); Отключать при блокировке (toggle); Мониторинг памяти (toggle → shows memory card on Home)
- **ОТЛАДКА**: Логи туннеля (None · 1 час ›)
- Bottom: `Сбросить настройки` (red text).
- Detail sub-screens: back arrow + title + `Готово`, grouped toggle cards.

## Reusable components to build
- `bg_card_incy` (surface, radius 22, subtle stroke) · `bg_chip_*` · colored-icon backgrounds
  (`bg_icon_blue/green/purple/yellow/red/orange` = tinted translucent rounded square) ·
  `SettingRow` include layout (icon+title+subtitle+value+chevron) · `SettingToggleRow` · iOS switch style.

## Staging
- S1 ✅ dark palette + memory metric + compact RU bottom nav.
- S2: connect shield+glow button + home stats row.
- S3: servers on home + Servers tab (search + protocol chips, drop sub tabs, flat rows, empty state).
- S4: custom Settings tab (grouped cards) replacing drawer; move theme/lang/icon/connection/tunnel/providers/ping/about/backup/logs there; keep "Настройки" bottom tab opening it.
- S5: detail sub-screens (Соединение, Настройки провайдеров, Тема picker) + app-icon alias.
