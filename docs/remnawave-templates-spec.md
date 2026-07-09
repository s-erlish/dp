# Спецификация: сквозная поддержка host-bound XRAY_JSON шаблонов Remnawave

Статус: спецификация (только дизайн, код не меняется).
Приложение: форк v2rayNG «departament», пакет `com.v2ray.ang`, Xray-core, Kotlin.
Связанный документ: `docs/hidden-templates-design.md` (общий дизайн скрытых шаблонов).
Дата: 2026-07-09.

---

## 0. TL;DR (самый крупный разрыв)

Машинерия «шаблон → скрытый CUSTOM-профиль → применение routing/DNS на подключении»
**уже собрана целиком и работает**, ЕСЛИ тело подписки реально приходит как XRAY_JSON.

Но **клиент никогда не запрашивает JSON-формат**. Remnawave выбирает формат ответа
**по заголовку `User-Agent`**. Для вручную добавленной подписки форк шлёт
`User-Agent: departament/<version>` (`HttpUtil.kt:155-158, 225-229`), который панель НЕ
сопоставляет с xray-json → Remnawave отдаёт **обычный base64-список `vless://`**. В итоге
`parseCustomConfigServer` вообще не видит JSON-тела, шаблон не сохраняется, routing/DNS
**не применяются** — пользователь получает просто список узлов без правил. Это и есть
жалоба заказчика «не работает end-to-end».

Вторичные разрывы: (2) хрупкая эвристика детекции JSON (требует одновременно три
подстроки `inbounds`+`outbounds`+`routing`); (3) нет очистки остаточного вендорного
объекта `remnawave` (страховка); клиентская `injectHosts` для пути Remnawave **не нужна**
(инъекция серверная — подтверждено документацией, §5).

Топ-3 шага реализации — см. §6.0.

---

## 1. Что происходит сейчас: точный сквозной путь

```
Подписка (URL оператора)
  → AngConfigManager.updateConfigViaSub                    (AngConfigManager.kt:561)
      → HttpUtil.getUrlContentWithUserAgentEx              (HttpUtil.kt:217)
            UA = subscription.userAgent ?: "departament/<ver>"   (HttpUtil.kt:225-229)
            возвращает body + заголовки (hidden/profile-title/…)  (HttpUtil.kt:257-265)
      → TemplateManager.applyLockState(sub, result.hidden, body) (AngConfigManager.kt:627)
            ставит sub.locked по заголовку profile-hidden/hidden
            или in-body-директиве «#profile-hidden:»        (TemplateManager.kt:68-104)
      → parseConfigViaSub(body, guid, append=false)         (AngConfigManager.kt:631, 673)
            1) parseBatchConfig(Utils.decode(body))  // base64 → vless-ссылки
            2) parseBatchConfig(body)                // построчные vless-ссылки
            3) parseCustomConfigServer(body)         // ← ветка XRAY_JSON
```

`parseCustomConfigServer` (`AngConfigManager.kt:394-469`):
- `locked = MmkvManager.decodeSubscription(subid)?.locked == true`  (стр. 400)
- **детекция**: `body.contains("inbounds") && contains("outbounds") && contains("routing")` (401-404)
- пытается разобрать тело как **JSON-массив** конфигов (406-431); одиночный объект
  Remnawave кидает исключение на `Array<Any>` → переход в catch;
- **совместимый путь одиночного объекта** (436-450): `CustomFmt.parse(body)` →
  `ProfileItem(CUSTOM)`, `config.locked = locked`, сырьё сохраняется через
  `MmkvManager.encodeServerRaw(key, TemplateManager.wrapRawForStorage(body, locked))` (446).

На подключении (`CoreConfigManager.getV2rayConfig` → `configContext.isCustom` →
`buildV2rayCustomConfig`, `CoreConfigManager.kt:38-39, 81-132`):
- `raw = TemplateManager.decodeRuntimeRaw(guid)` (86) — прозрачно расшифровывает
  locked-шаблон (`TemplateManager.kt:149`, `TemplateCrypto`);
- переписывает `routing.rules[].process` пакеты→UID при process-routing (96-111);
- добавляет `tun`-inbound с MTU пользователя, если его нет в шаблоне (113-129);
- **в остальном шаблон уходит в ядро как есть** → routing/DNS/fragment/balancer/outbound
  применяются как задумал оператор.

UI-гейтинг locked-профилей уже расставлен: `shareConfig`/`share2Clipboard`/`share2QRCode`
(`AngConfigManager.kt:153-173, 58-120`), `shareFullContent2Clipboard` (129-145),
редактор `ServerCustomConfigActivity` блокируется (`ServerCustomConfigActivity.kt:46-50`).

---

## 2. Что код УЖЕ делает правильно (не трогать)

1. **Приём тела и заголовков подписки** — `HttpUtil.getUrlContentWithUserAgentEx`
   возвращает `UrlContentResult` с `hidden`, `profile-title`, `subscription-userinfo`,
   `announce`, `support-url`, `profile-web-page-url` (`HttpUtil.kt:202-211, 257-265`).
2. **Резолвинг locked-состояния** — заголовок `profile-hidden`/`hidden` ИЛИ in-body
   `#profile-hidden:` (`TemplateManager.applyLockState`, `TemplateManager.kt:68-104`);
   сохранение до парсинга (`AngConfigManager.kt:627-629`).
3. **Ингест одиночного XRAY_JSON-объекта** — через compat-ветку
   `parseCustomConfigServer` (`AngConfigManager.kt:436-450`). То есть **если** тело
   пришло как JSON и содержит нужные подстроки, оно СОХРАНЯЕТСЯ как CUSTOM-профиль.
4. **Хранение сырья со скрытием** — `wrapRawForStorage` шифрует (AES-GCM в Android
   Keystore) при `locked`, иначе plaintext без изменений
   (`TemplateManager.kt:116-138`, `TemplateCrypto.kt`).
5. **Применение шаблона на подключении** — `buildV2rayCustomConfig` прогоняет весь
   routing/DNS/outbounds/balancers как есть, добавляя лишь tun/UID
   (`CoreConfigManager.kt:81-132`).
6. **Извлечение remarks/server/port** — `CustomFmt.parse` берёт первый proxy-outbound
   через `getProxyOutbound()` (`CustomFmt.kt:15-26`, `V2rayConfig.kt:507-516`); для
   balancer-шаблона Remnawave (`proxy`, `proxy-2`, …) находит инъецированный `proxy`.
7. **UI-гейтинг** locked-профилей и редактора (см. §1).

Вывод: **отдельного нового парсера XRAY_JSON не требуется** — при корректной доставке
JSON текущий путь доводит шаблон до ядра. Проблема на входе (формат) и в детекции.

---

## 3. Что код НЕ делает (разрывы)

### Разрыв A — НЕ согласует формат XRAY_JSON (главный, блокирующий)
Remnawave выбирает формат по `User-Agent` (§5). Форк шлёт `departament/<ver>` (для
ручных подписок) или `BackendConfig.subscriptionUserAgent` (для auth-синхронных,
`SubscriptionSyncManager.kt:40`). Ни то, ни другое по умолчанию не заставляет панель
отдать xray-json → приходит base64-список `vless://` → ветка XRAY_JSON никогда не
срабатывает → **routing/DNS шаблона не применяются вовсе**. Заголовка `Accept:
application/json` тоже нет. Это причина «не работает end-to-end».

### Разрыв B — хрупкая эвристика детекции JSON
`parseCustomConfigServer` требует одновременно три литеральные подстроки
`inbounds`+`outbounds`+`routing` во всём теле (`AngConfigManager.kt:401-404`).
XRAY_JSON-шаблон, где нет `inbounds` (клиент сам добавляет tun) или нет секции
`routing` (только `dns`+`outbounds`), **не распознаётся** → импорт молча возвращает 0 →
`updateConfigViaSub` даёт `failureCount` (`AngConfigManager.kt:655-657`). Подстрочное
совпадение также ложно-срабатывает, если слово встречается в remark.

### Разрыв C — не вычищается остаточный вендорный объект `remnawave`
Если шаблон всё-таки содержит корневой ключ `remnawave` (импорт из файла-шаблона,
панель-вариант без серверной зачистки), Xray-core падает на неизвестном корневом поле.
Клиент не удаляет его перед сохранением/запуском (страховочная мера; для честного
Remnawave-пути объект уже вырезан сервером, §5).

### Разрыв D — клиентская injectHosts (условный, для Remnawave НЕ нужен)
Инъекция host-данных у Remnawave — **серверная** (§5): клиент получает финальный JSON.
Значит клиентская подстановка по `tagPrefix` для пути Remnawave **не требуется**. Она
нужна лишь для альтернативной модели «один шаблон + переключаемый список узлов на
устройстве» (стиль Happ), которую Remnawave не использует. См. две ветки в §6.4.

### Разрыв E — нет защиты транспорта для locked-подписок (мелкий, §4 общего дизайна)
Для locked-подписок не форсируется HTTPS и по-прежнему возможен `allowInsecureUrl`.

---

## 4. Ответ на прямой вопрос: «ингестит ли текущий код тело XRAY_JSON?»

**Частично — да, но только при удачном стечении обстоятельств.** Тело доходит до
`parseCustomConfigServer`, и одиночный XRAY_JSON-объект сохраняется через compat-ветку
(`AngConfigManager.kt:436-450`) **при двух условиях одновременно**:
1. панель реально прислала JSON (не base64-список) — сейчас НЕ гарантировано (разрыв A);
2. тело содержит подстроки `inbounds` И `outbounds` И `routing` — не всегда (разрыв B).

Если оба выполнены — routing/DNS применяются корректно. На практике из-за разрыва A
первое условие почти всегда ложно, поэтому «сквозняк» не работает.

---

## 5. Как Remnawave отдаёт конфиг (подтверждено документацией)

Источники: `docs.rw/learn/xray-json-advanced/`, `docs.rw/docs/learn-en/templates/`,
`github.com/remnawave/templates`.

- **Выбор формата — по `User-Agent`.** Панель авто-детектит клиента и отдаёт одно из
  семейств: Mihomo/Clash, Base64 (легаси-список), **Xray-json**, Sing-box. Неизвестный/
  v2rayNG-подобный UA → Base64. Чтобы получить xray-json, UA клиента должен попадать в
  правило панели (маппинг клиента → шаблон; с v2.2.0 — через *External Squads / Routing
  Rules*, несколько шаблонов на ядро). На стороне панели это управляемо оператором и
  переопределяется `SUBSCRIPTION_USER_AGENT`.
- **Инъекция host-данных — серверная.** Директива `remnawave.injectHosts` с
  `selector` (`uuids`/`remarkRegex`/`tagRegex`/`sameTagAsRecipient`) и `tagPrefix`
  (`proxy`, `proxy-2`, …; либо `useHostRemarkAsTag`/`useHostTagAsTag`) обрабатывается
  панелью при генерации подписки: реальные address/port/keys/transport подставляются в
  `outbounds`, **весь объект `remnawave` удаляется**, клиенту уходит **финальный,
  полностью заполненный JSON**. Клиент host-инъекцию **не делает**.
- **balancer `selector` — префиксное совпадение**: `["proxy"]` матчит все
  инъецированные outbounds, `["proxy-"]` — все кроме первого (failover). Это чисто
  серверная семантика; клиент просто исполняет уже готовый `routing`.
- Формат xray-json обычно отдаётся с `Content-Type: application/json`, тело — **один
  JSON-объект** (не массив), как правило с секциями `dns`/`routing`/`outbounds` и часто
  `inbounds` (socks/http), пригодный к прямому запуску ядром.

**Архитектурный вывод для форка:** для пути Remnawave делать нужно только (1) заставить
панель отдать JSON (согласование UA) и (2) устойчиво распознать и сохранить готовый JSON.
Клиентская injectHosts не нужна.

---

## 6. Точный дизайн решения

### 6.0. Топ-3 шага (минимальный сквозняк)
1. **Согласование формата (разрыв A).** Слать оператор-согласованный `User-Agent`
   (и `Accept: application/json`) для locked/departament-подписок, чтобы Remnawave отдал
   xray-json. Значение UA — конфигурируемое (`BackendConfig.subscriptionUserAgent`).
2. **Устойчивая детекция JSON (разрыв B).** Распознавать custom-тело по «это валидный
   Xray-JSON с `outbounds`», а не по трём подстрокам.
3. **Санитайз шаблона (разрыв C) + сохранение как locked CUSTOM** — вырезать остаточный
   `remnawave`, затем существующий путь `encodeServerRaw`+`wrapRawForStorage`.

Дальше — детали.

### 6.1. Согласование формата (разрыв A) — `HttpUtil` + `AngConfigManager`
- Ввести UA по умолчанию для запроса подписки, который у оператора Remnawave замаплен на
  шаблон xray-json. Источник значения — `BackendConfig.subscriptionUserAgent`
  (`BackendConfig.kt:23-24`), сегодня оно применяется только для auth-синхронных подписок
  (`SubscriptionSyncManager.kt:40`). Распространить его на **все** locked/departament-
  подписки: если `subscription.userAgent` пуст и подписка locked (или хост содержит метку
  `departament`), подставлять этот UA в `getUrlContentWithUserAgentEx`.
- Дополнительно добавить заголовок `Accept: application/json` в
  `getUrlContentWithUserAgentEx` (`HttpUtil.kt:230-234`) — безвредно для панелей,
  игнорирующих Accept, и помогает тем, кто его учитывает.
- **Замечание о курице/яйце:** `sub.locked` выясняется только ПОСЛЕ первого ответа
  (`applyLockState`). Поэтому для авто-выбора UA использовать метку хоста
  (`SubscriptionGuard.REQUIRED_LABEL == "departament"`) как ранний признак, ещё до
  первого запроса. Для auth-синхронных подписок UA уже известен из бэкенда.
- Точки: `HttpUtil.kt:217-241` (сборка запроса), `AngConfigManager.kt:586` (выбор UA),
  `BackendConfig.kt:23`.

### 6.2. Устойчивая детекция XRAY_JSON (разрыв B) — `AngConfigManager.parseCustomConfigServer`
Заменить эвристику трёх подстрок (`AngConfigManager.kt:401-404`) на:
1. `val trimmed = body.trim()`; кандидат, если `trimmed.startsWith("{")` (одиночный
   объект — типичный Remnawave) или `startsWith("[")` (массив конфигов);
2. попытаться десериализовать в `V2rayConfig` (`JsonUtil.fromJson`), считать это шаблоном,
   если получилось и `outbounds` непусты (условие «есть что запускать»);
3. `routing`/`dns`/`inbounds` — опциональны (tun добавит `buildV2rayCustomConfig`).
Оставить существующую развилку массив/одиночный объект (406-450) и, для массива, per-
элемент `V2rayConfig`-валидацию. WireGuard-ветку (452-465) не трогать.

### 6.3. Санитайз шаблона (разрыв C) — новый валидатор, вызывается из детекции
Перед `encodeServerRaw`:
- распарсить в JSON-объект, **удалить корневой ключ `remnawave`** (и прочие неизвестные
  вендорные корневые объекты) — страховка на случай незачищенного сервером шаблона;
- (опц., из §4 общего дизайна) отклонять/вырезать `inbounds`, слушающие не на
  loopback/tun; ограничить размер и число правил; требовать `outbounds`.
Разместить как `fmt/TemplateValidator` или расширение `CustomFmt`; вызывать из
`parseCustomConfigServer` до сохранения. Сохранять уже **очищенный** JSON (то самое, что
уйдёт в ядро). Для honest-Remnawave-пути `remnawave` уже отсутствует — no-op.

### 6.4. injectHosts — две ветки (разрыв D)

**Ветка A (Remnawave, серверная инъекция) — реализовать сейчас, основная.**
Ничего клиентского для host-инъекции не нужно: тело уже финальное. Достаточно §6.1–6.3 +
существующего `buildV2rayCustomConfig`. `tagPrefix`/`selector` исполняются ядром как часть
готового `routing`/`balancers`. Это MVP и путь по умолчанию.

**Ветка B (клиентская инъекция) — опционально, позже, НЕ для Remnawave.**
Только если оператор доставляет **шаблон + отдельный список узлов** (модель Happ «один
шаблон на все серверы»). Дизайн (из §2.3 общего документа):
1. хранить шаблон (с плейсхолдер-outbound `proxy`/`__PROXY__`) и список узлов отдельно;
2. на подключении: `CoreOutboundBuilder.convert(selectedNode)` → OutboundBean; клонировать
   шаблон; заменить outbound с `tag=="proxy"` (или `proxy`, `proxy-2`, … для balancer)
   построенным(и), сохранив `sockopt`/fragment шаблона;
3. слияние выполнять в `buildV2rayCustomConfig` (`CoreConfigManager.kt:81`), чтобы смена
   узла не требовала повторной загрузки.
Конвенция тегов намеренно совпадает с Remnawave — шаблоны оператора переносимы.

### 6.5. Хранение и применение — без изменений
Существующие `wrapRawForStorage`/`decodeRuntimeRaw` (`TemplateManager.kt:116-149`) и
`buildV2rayCustomConfig` (`CoreConfigManager.kt:81-132`) уже дают: шифрование locked-
сырья в Keystore, прозрачную расшифровку на подключении, применение всего routing/DNS.
Менять не требуется.

---

## 7. Точки интеграции (файлы + file:line)

| Задача | Файл : строки | Действие |
|---|---|---|
| UA-согласование формата | `util/HttpUtil.kt:217-241` (и `146-169`) | добавить `Accept: application/json`; UA по умолчанию для locked/departament |
| Источник UA | `auth/BackendConfig.kt:23-24` | `subscriptionUserAgent` как значение по умолчанию |
| Применение UA к ручным подпискам | `handler/AngConfigManager.kt:586` | подставлять UA когда `sub.userAgent` пуст и sub locked/departament |
| Устойчивая детекция JSON | `handler/AngConfigManager.kt:401-404` | заменить три-подстрочную проверку на JSON-валидацию `V2rayConfig`+`outbounds` |
| Развилка массив/объект | `handler/AngConfigManager.kt:406-450` | оставить; per-элемент валидировать |
| Санитайз `remnawave` | новый `fmt/TemplateValidator.kt` или `fmt/CustomFmt.kt:15` | вырезать корневой `remnawave`/чужие вендор-объекты до сохранения |
| Стамп locked + хранение | `handler/AngConfigManager.kt:400,419,441,422,446` | без изменений (уже корректно) |
| Резолвинг locked | `handler/AngConfigManager.kt:627-629`; `template/TemplateManager.kt:68-104` | без изменений |
| Приём заголовков | `util/HttpUtil.kt:202-211,257-265` | без изменений |
| Применение на подключении | `core/CoreConfigManager.kt:38-39,61-63,81-132` | без изменений (ветка A) |
| Хранение сырья | `template/TemplateManager.kt:116-149`; `template/TemplateCrypto.kt` | без изменений |
| UI-гейтинг | `handler/AngConfigManager.kt:129-173`; `ui/ServerCustomConfigActivity.kt:46-50` | без изменений |
| (Ветка B) клиентская инъекция | `core/CoreConfigManager.kt:81`; `core/CoreOutboundBuilder.kt` | опционально, позже |
| (Разрыв E) HTTPS для locked | `handler/AngConfigManager.kt:580-584` | не honor `allowInsecureUrl` для locked |

---

## 8. Гарантии совместимости

1. **Обычные подписки не затронуты.** UA-согласование включается только для
   locked/departament-подписок; для остальных UA и путь остаются прежними. Устойчивая
   детекция — надмножество текущей (всё, что ловилось тремя подстроками, продолжит
   ловиться как валидный `V2rayConfig` с `outbounds`); vless/vmess-списки идут раньше по
   `parseConfigViaSub` (`AngConfigManager.kt:673-682`) и не задеваются.
2. **Хранение без locked — байт-в-байт.** `wrapRawForStorage(raw, locked=false)` вернёт
   raw без изменений (`TemplateManager.kt:117`); non-locked профили не шифруются.
3. **Гейтинг сохранён.** `locked` по-прежнему стампится из подписки
   (`AngConfigManager.kt:400,419,441,508`), и share/QR/full-content/редактор остаются
   заблокированы для locked. Санитайз `remnawave` не влияет на видимость.
4. **Санитайз идемпотентен** для уже-очищенных Remnawave-конфигов (no-op).

---

## 9. Критерии приёмки

1. Locked/departament-подписка на Remnawave с настроенным xray-json шаблоном при
   обновлении отдаёт **JSON** (проверяется: тело начинается с `{`, `Content-Type`
   application/json), а не base64-список.
2. Тело распознаётся как custom → создаётся ≥1 `ProfileItem(CUSTOM)` с `locked=true`;
   `updateConfigViaSub` возвращает `configCount>0`, `successCount=1`.
3. Сырьё в MMKV хранится с префиксом `dpt-enc:`/`dpt-obf:` (зашифровано/обфусцировано),
   не читается дампом хранилища как plaintext.
4. На подключении конфиг ядра содержит `routing`/`dns`/`balancers`/`outbounds` из шаблона
   **без изменений** (кроме добавленного tun и переписанных process→UID); правила
   реально действуют (проверка маршрутизации/DNS по логам ядра).
5. Шаблон с остаточным ключом `remnawave` запускается (ключ вырезан), ядро не падает.
6. Шаблон без секции `inbounds` или без `routing` всё равно импортируется (устойчивая
   детекция), tun добавляется автоматически.
7. Для locked-профиля недоступны share/QR/копирование полного конфига/редактор (уже
   покрыто гейтингом).
8. Обычная (не-locked) подписка с vless-списком импортируется как раньше, UA не изменён.

---

## 10. Неопределённости реального мира (важно)

- **Механизм выбора формата.** Документация подтверждает выбор по `User-Agent`, но НЕ
  публикует таблицу «UA → формат». Точное значение UA, которое отдаст xray-json,
  **зависит от конфигурации панели оператора** (External Squads/Routing Rules, или
  `SUBSCRIPTION_USER_AGENT`). Поэтому UA должен быть **конфигурируемым**
  (`BackendConfig.subscriptionUserAgent`), а не захардкоженным, и его нужно согласовать с
  оператором. Возможен также вариант панели с URL-суффиксом/квери для JSON — в docs не
  подтверждён; если оператор так настроил, достаточно правильного URL и §6.1 не нужен.
- **Серверная vs клиентская инъекция.** Подтверждено: у Remnawave инъекция host-данных
  **серверная**, клиент получает финальный JSON — **клиентская injectHosts не нужна**
  (ветка A). Ветку B (клиентскую инъекцию по `tagPrefix`) реализовывать только если
  конкретный оператор доставляет шаблон и узлы раздельно (модель Happ), что для Remnawave
  не характерно. Обе ветки описаны в §6.4.
- **Наличие `inbounds`/`routing` в теле.** Зависит от шаблона оператора; поэтому детекция
  (§6.2) не должна их требовать.
- **Честность скрытия.** Это обфускация + UX-гейтинг, НЕ DRM (см. §3–4 общего дизайна и
  `TemplateCrypto`): plaintext существует в памяти на момент подключения.

---

## 11. Источники

- Remnawave — Xray JSON Advanced (injectHosts, серверная инъекция): https://docs.rw/learn/xray-json-advanced/
- Remnawave — Templates: https://docs.rw/docs/learn-en/templates/
- Remnawave — Config Profiles: https://docs.rw/learn-en/config-profiles/
- Remnawave templates repo: https://github.com/remnawave/templates
- Общий дизайн скрытых шаблонов (этот форк): `docs/hidden-templates-design.md`
