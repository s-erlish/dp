# 42 - The canonical copy register

**Departament VPN - one product, two clients, one set of words.**

This document is the single source of truth for every string the product shows a user. It binds the
Android client (`/home/user/dp`) and the PC client (`/home/user/v2rayN`) to the same Russian
sentence for the same concept, and it names the resource key that carries it on each side.

The owner's requirement, verbatim, is the reason this file exists:

> «главное, чтобы дизайн весь был под программу и все было четко выверено по тексту что на пк и что
> на телефонной версии, главное чтобы был единый красивый стиль и все выглядело правильно»

The operative half is **выверено по тексту**. Two clients that say «Удалить устройство» and
«Отвязать устройство» for one action are not one product, however identical their tokens are.

**This wave writes documents only.** No source file was modified, no git command was run.

---

## 0. How to use this file

### 0.1 Precedence

1. The owner's explicit request (`00-rules.md` 0.4).
2. `00-rules.md`, and section 9 «Copy law (Russian)» in particular.
3. **This file.** It extends section 9; it does not contradict it.
4. The per-screen specs: `12-settings.md` 11, `13-start-screen.md` 13, `14-auth.md` 11,
   `16-servers.md` 13, `23-account-rework.md` 8.
5. Everything currently in `res/values*/strings*.xml` and `Common/L.*.cs`.

Where this file and a per-screen spec disagree, the disagreement is **named in section 7.1** with
the rule that decides it. `00-rules.md` 0.1 is explicit: a spec that contradicts the law is a bug,
not a variant. Fifteen such bugs are corrected here.

Two rows of the law itself are defective **against other rows of the law**, and this file may not
propagate a lock break any more than it may contradict section 9. Those two are raised as errata in
**section 7.2**, with the rule inside section 9 that decides each, and the rows that carry them are
marked **9.4!** in section 3.

The inventories this file is built on: `40-copy-inventory-android.md` (1114 keys, 684 live),
`41-copy-inventory-pc.md` (405 `L` entries + 570 `ResUI` keys + 75 AXAML literals). Neither inventory
is the source of the Δ columns: those are derived from the codebases directly (0.3), because an
inventory is a snapshot and this file outlives it.

### 0.2 How to read a row

| Column | Meaning |
|---|---|
| **Концепт** | The thing the string names. One concept, one row, both platforms. |
| **Русский** | The approved Russian string. Sentence case. Copy it exactly, including the middle dot `·` (U+00B7, one ordinary space either side), the ellipsis `…` (U+2026, one character) and the «ёлочки» (U+00AB, U+00BB). No row here carries a `₽`: every money string is built by the one formatter (C6), so a price's thin and non-breaking spaces never live in a resource value. |
| **English** | The English string. On PC it is the second slot of `L.Add(key, ru, en)`. On Android it goes in `res/values-en/`, which does not exist yet (D-S9). |
| **Android** | The Android resource name. `-` means the platform does not show this concept. |
| **PC** | The `L` key. `-` means the platform does not show this concept. |
| **Экраны** | Every surface that renders it. |
| **Текст** | What the platforms render **today**, measured against this row's Русский cell. Derived, see 0.3. |
| **Ключи** | Whether the named key **exists**, per platform, and under what name. Derived, see 0.3. |

### 0.3 The two derived columns

The first edition of this file carried **one** hand-written Δ column, and its `=` code read «both
platforms already ship exactly this string. Do not touch it.» That claim was false at scale. Ten
rows carrying `=` were probed against the resource tree: `account_tab_title`,
`account_name_fallback`, `devices_this_device`, `devices_unknown`, `history_status_paid`,
`account_health_active`, `home_status_connected`, `servers_title`, `common_cancel` and
`common_retry` are **absent from every `values*/` folder**. Two of them are not renames either -
«Это устройство» and «Активна» exist under no key at all - while «Неизвестное устройство» ships as
`devices_unknown_model` and «Оплачено» as `account_status_paid`. An implementer obeying that legend
would have skipped every one of those rows and the strings would never have been written.

A hand-maintained column over ~700 rows is not maintainable and was not maintained. It is now two
columns, both **derived from the two codebases** by
[`tools/derive-copy-delta.py`](tools/derive-copy-delta.py):

```bash
python3 docs/design2026/tools/derive-copy-delta.py            # report what would change
python3 docs/design2026/tools/derive-copy-delta.py --rewrite  # rewrite both columns in place
python3 docs/design2026/tools/derive-copy-delta.py --check    # exit 1 when the file has drifted
```

**Текст** - one code per row, the *wording* axis:

| Code | Meaning |
|---|---|
| `=` | Every platform that shows this concept already renders exactly this Russian string |
| `A` | **Android's wording changes** to it: Android renders something else today, or renders nothing |
| `P` | **PC's wording changes** to it |
| `AP` | **Both** platforms' wording changes |

**Ключи** - two codes per row, one per platform, the *key* axis:

| Code | Meaning |
|---|---|
| `A✓` | The key exists today under exactly this name, and `values/` carries this Russian |
| `A✓†` | The key exists, but the Russian lives only in `values-ru/`; `values/` still ships upstream English, which D-S9 fixes |
| `A←old_name` | `old_name` already carries this exact Russian string, so this row is a **rename**, not a new string |
| `A←old_name†` | The same, and the Russian it carries is in `values-ru/` only |
| `A+` | The key does not exist and must be created |
| `A-` | Android does not show this concept |
| `A≡` | The row reuses a key declared on another row; the Экраны column names both surfaces |

The same six shapes carry a `P` prefix for the desktop.

The old one-letter codes map onto the pair without loss: `N` is `AP` + `A+ P+`, the old `A+` is `A` +
`A+ P✓`, and the old `=` is `=` + `A✓ P✓` **only when the keys actually exist**, which is the
distinction the first edition could not express and therefore got wrong. Deletions are not a Δ code:
they are named in prose under the table that used to own them.

Every `A`, `P` and `AP` row still carries its **reason** in the note under its table. A wording
change with no stated reason is not a decision, it is a preference, and `00-rules.md` 0.1 puts
preference last. The derived column says *that* a platform changes; the note says *why*.

### 0.4 Where the strings live

Several waves edit these files at once. `40-copy-inventory-android.md` F0.2 records two resource
collisions that appeared and vanished inside one 20-minute audit, because two agents were writing
`values/strings.xml` simultaneously. One file per surface is the mitigation.

| Surface | Android file | PC file |
|---|---|---|
| Shell, navigation, common verbs | `res/values/strings_common.xml` **new** | `Common/L.Common.cs`, `Common/L.Shell.cs` |
| Главная, connect, status | `res/values/strings_home.xml` **new** | `Common/L.Home.cs` |
| Серверы, providers | `res/values/strings_servers.xml` **new** | `Common/L.Servers.cs` |
| Аккаунт, billing, devices, history | `res/values/strings_account.xml`, `strings_devices.xml`, `strings_pay.xml`, `strings_history.xml` | `Common/L.Account.cs`, `Common/L.Buy.cs` |
| Вход | `res/values/strings_auth.xml` | `Common/L.Account.cs` (`Login_*`) |
| Настройки | `res/values/strings_settings.xml` **new** | `Common/L.Settings.cs` |
| Notifications, tile, shortcuts | `res/values/strings_service.xml` **new** | `Common/L.Shell.cs` (`Tray_*`) |

**Android's default locale becomes Russian** (D-S9, `01-inventory-android.md` 5.4). Today `values/`
is half Russian and half English, so a device set to English shows English chrome around Russian
product copy. After the copy pass, `values/` is Russian and `values-en/` carries the English column
of this register.

**Seven locale folders carry strings, not five.** `ls -d values-*/` returns `values-ar`,
`values-bn`, `values-bqi-rIR`, `values-fa`, `values-vi`, `values-zh-rCN` and `values-zh-rTW`
(`values-night` and `values-sw360dp-v13` are not locales and carry no `strings*.xml`). All **seven**
are deleted with the upstream keys they translate. The first edition named five and silently left
`values-fa/` and `values-bqi-rIR/` standing, which would have given a Persian device Persian upstream
copy for the keys those two files cover and Russian for everything else - the exact half-translated
shell D-S9 exists to end.

**`values-ru/` is deleted too, and last.** It holds 768 entries: 393 already byte-identical to their
`values/` twin, 375 the Russian of a key whose `values/` twin is still English, and none that
`values/` does not declare. The moment `values/` is Russian, every one of those 768 is a duplicate,
and a duplicate is where the next copy edit silently diverges - the edit lands in one file and the
Russian device keeps reading the other. Order matters: `values/` becomes Russian **first**, then
`values-ru/` is removed in the same commit, so no intermediate build ships English chrome. W-25 and
W-27 carry the two halves.

---

## 1. The law this register enforces, and the nine rules it adds

`00-rules.md` 9.1-9.7 stands in full: direct calm voice, sentence case, active verbs, no exclamation
marks, no ALL-CAPS, no em-dash and no en-dash, `…` as one character, «ёлочки», no final period on
labels, thin space for thousands and a non-breaking space before `₽`.

These nine are additions. They are enforceable by grep, and section 8 shows how.

### C1 - One noun per concept, extended

`00-rules.md` 9.3 locks eleven concepts. Seven more are locked here, because the inventories found
each of them shipped in two or more forms.

| Концепт | Употреблять | Никогда | Почему |
|---|---|---|---|
| A connection of any kind | **подключение** | соединение, коннект, конект | 9.3 already locks it for the tunnel. The product must not use a second noun for a TCP connection, a Mux channel or a latency probe: a user does not know they are different things. Affects 6 shipped strings. |
| Removing a device from the account | **отвязать** | удалить, отключить | «Удалить» is the destructive verb for a server, a provider, a rule and a file. Reusing it for a device makes the device look destroyable. 9.4's own text says «Отвяжите одно из устройств». PC is right, Android changes. |
| The relation between a device and the account | **привязано** | подключено, подключённое | The counterpart of «отвязать». «Подключено» is the tunnel's word and the product's single most prominent status; a Devices screen that counts «Подключено 3 из 5» under a row button reading «Отвязать устройство» names one relation two ways on one screen. Affects `devices_count_line`, `devices_count_line_unlimited`, `devices_subtitle`. |
| The picture on the account head | **фото** | аватар, изображение, картинка | Three nouns shipped inside one six-row sheet: «Сменить фото», «Аватар обновлён», «…Выберите другое изображение». The confirmation must name what the three actions produce. |
| Deleting an object | **удалить** | стереть, очистить (except «Очистить журнал» and «Очистить поиск», which clear a buffer, not an object) | - |
| Buying | **Купить** | Купить тариф, Выбрать тариф, Оформить, Приобрести | 9.3 locks «Купить». Three variants shipped on the account card alone. |
| The expiry of a subscription | **Активна до %1$s** | Действует до, До, до 12.06.2026 | Four wordings shipped on PC alone (`Account_ValidUntil`, `Account_ActiveUntil`, `Account_ExpiresUntil`, `Sub_Until`). «Активна до» is the one that agrees with the health chips «Активна» / «Истекает» / «Истекла». |
| The disconnected state | **Отключено** | Не подключено, Соединение разорвано | It pairs with «Подключено», «Подключение…», «Отключение…». One paradigm, four states. |
| Dismissing, quitting, signing out | **Закрыть** (a sheet, a dialog, a window) / **Завершить работу** (quit the process, tray only) / **Выйти** (sign out of the account) | «Выход» for any of the three; «Закрыть» for quitting | Three different consequences. Splitting «Закрыть» from «Выйти» is not enough on its own: a tray item «Закрыть» collides with the desktop setting «Сворачивать в трей при закрытии», so on one product «закрытие» would mean minimise in one place and quit in another. «Завершить работу» is the only one of the three that ends the process, and it appears in exactly one menu. |

The eleven locks from 9.3 are unchanged and are not restated here.

### C2 - One key per concept, and shared keys are declared once

`40-copy-inventory-android.md` F7 found 95 groups of Android keys carrying identical text, 57 of
them with two or more live keys; `41-copy-inventory-pc.md` 5.8 found 14 such groups on PC plus four
groups where one concept had several *different* wordings.

A concept gets exactly one key. Where two screens genuinely show the same word, they reference the
same key; the register's **Экраны** column names every one of them. The consequence for
implementers: `Повторить` is `common_retry` / `Common_Retry` on every screen in the product, and
`account_retry`, `adv_action_retry`, `buy_retry`, `editor_retry`, `Account_SyncRetry` are deleted.

### C3 - Every error is a triple, and the affordance is part of the string's contract

9.4's formula is **what happened + why + what to do**. This register adds: an error string is not
approved unless the surface that renders it also renders a recovery control, and the register names
that control in the **Экраны** column.

Today Android delivers 52 errors through `toastError()` and **none** of them carries an action
(`40-copy-inventory-android.md` F6.1); PC routes ~40 through a notice channel with no subscriber, so
they are not rendered at all (`41-copy-inventory-pc.md` 2.3). A perfect sentence nobody can act on
fails 9.4 exactly as badly as «Ошибка».

### C4 - Never show a machine's words to a person

No HTTP status, no exception text, no response body, no request id, no stack, no «пришлите
скриншот». The real cause goes to `settings/about/log` (`11-app-structure.md` 8.3), which exists so
that this rule can be kept without losing the diagnosis.

Nine shipped strings break this and are deleted in section 7.

### C5 - The unit lives in the label, the figure stays bare

`0 KB/s` is not a string, in either language. The numeric strip labels the column
(«Приём, Мбит/с») and renders `0` in the figure face. This kills six literals on PC and one Android
resource, and it removes the only place in the product where a unit could disagree with itself.

Speeds are **Мбит/с** and **Кбит/с**: the counters are converted from bytes to bits at the point of
display. Volumes are **КБ / МБ / ГБ / ТБ** with a comma decimal (`12,4 ГБ`). Latency is **мс**
(`48 мс`). This settles `41-copy-inventory-pc.md` 10 P1 #6, which asked the two platforms to decide.

### C6 - One money formatter, one currency

`₽`, always, whatever the backend's currency code says (`00-rules.md` 0.4.4,
`23-account-rework.md` 4.1). Thin space for thousands, non-breaking space before the symbol, kopecks
only when non-zero and with a comma: `1 290 ₽`, `1 290,50 ₽`, `0 ₽`. Six formatters exist today and
three of them can print `$`. All six die.

The two spaces in that example are **not** the space on your keyboard, and copying them out of a
rendered document loses them. The thousands separator is **U+2009 THIN SPACE** and the space before
the symbol is **U+00A0 NO-BREAK SPACE**, so that a price never wraps between the figure and the `₽`.
No money string in this register is a resource value: the formatter builds all of them, which is why
there is exactly one formatter per platform (`Money.format` / `Money.Format`).

### C7 - A placeholder is not a label

A field's name is a persistent `Subtitle` above it. A watermark may only carry an example value.
`41-copy-inventory-pc.md` 7.1 found five fields whose only name was their watermark, three of them
on the sign-in screen, where the register form is three unlabelled boxes once the user starts
typing. Every field in this register has a label row, and the watermark column is separate.

### C8 - The brand is written one way

The wordmark asset and the app name are lowercase **`departament`**. The lowercase form survives
wherever the brand stands as a **name-plate** rather than a word in a sentence: the gate title «Вход
в departament», the notification title, the window title, the tray tooltip, the quick tile. **Inside
a running Russian sentence the brand is capitalised**: «серверам Departament», «Запустите Departament
от имени администратора», «Эта ссылка не от Departament.» The site is `departament.site`, lowercase
in every position, because it is a domain and not a word. The internal codename
`INCY` never appears in shipped copy - it is currently the watermark of the User-Agent field
(`ProviderSettingsPage.axaml:127`).

The first edition broke this rule three times in its own tables («не от departament», «Открывать
departament при входе», «Запустите departament от имени администратора»); all three are corrected
above, and 8.1 gains the grep that would have caught them.

### C9 - One voice, and one way to close an error

`00-rules.md` 9.1 fixes the register (direct, calm, no marketing, no apologies). This adds the
grammatical person, because the first edition drifted across three of them inside one screen.

- **First person plural only where the app genuinely acts on the user's behalf** and the user is not
  the actor: a charge («%1$s спишем %2$s»), an email («Отправим ссылку для входа на этот адрес»), a
  background job the user started («Проверяем оплату…», «Добавляем аккаунт»). It is a promise or a
  progress report, never a decoration.
- **Impersonal everywhere else.** A label, a row subtitle, a state, a consequence: «Продление
  вручную», «Доплата зависит от оставшегося срока», «Улучшать нечего».
- **«Вы» is never the subject of a sentence.** «Вы на максимальном тарифе» becomes «Улучшать
  нечего»; «у вас старший тариф» becomes «это старший тариф». The second person survives only as a
  possessive where the object genuinely belongs to the user («ваша подписка»), and as the imperative,
  which is the product's default mood.
- **One promise shape for one promise.** «Отправим ссылку…» before it is sent, «Мы отправили ссылку
  на %1$s» after. Not «Пришлём», not «Вышлем».
- **One closing for an error: «Повторите попытку.»** The first edition shipped four - «…и
  повторите.», «Повторите.», «Повторите попытку.», «Повторите попытку позже.» - of which «Повторите.»
  standing alone is clipped machine-speak. Where the sentence already names what to check, the
  closing joins it with «и»: «Проверьте сеть и повторите попытку.» Where a button next to an **error**
  says «Повторить», the sentence does **not** also say «позже»: the button retries the operation that
  just failed, it is available now, and C3 makes it part of the string's contract. The one place
  «позже» survives is an **empty state**, where nothing failed and the list genuinely refills on a
  schedule: «Список обновляется автоматически, загляните позже.» (R-7). An empty state is not an
  error, and 3.8 is the only table allowed to say it.

---

## 2. Register decisions

Twenty decisions this file takes, each because two shipped strings could not both be right. They are
referenced from the tables as **R-n**.

| # | Decision | Beats | Rule |
|---|---|---|---|
| R-1 | Expiry reads **«Активна до %1$s»** everywhere | «Действует до», «До {0}», «до {0:dd.MM.yyyy}» | C1, 9.3 |
| R-2 | **«подключение»** is the only noun for a connection | 6 strings using «соединение» | C1, 9.3 |
| R-3 | A device is **отвязано**, never удалено | Android's 5 `devices_delete_*` strings | C1, 9.4 |
| R-4 | The settings group for provider feeds is **«Провайдеры»**, and its auto-update row is **«Автообновление провайдеров»** | `12-settings.md` 4.4 / 11.1 «Подписки» / «Автообновление подписок» | 9.3 locks провайдер for a feed. The spec's own justification («the one place the two senses touch») is precisely the exception the lock forbids. `00-rules.md` 0.1: the spec is the bug |
| R-5 | **The object of «купить» is the тариф, never the подписка.** «Купить» bare on a button where the object is obvious; **«Купить тариф»** wherever the object must be named (row, screen title, gate, empty-state action); **«Выберите тариф»** as the section header inside the buy screen; **«Покупка тарифа»** in the history. Nothing else | «Купить подписку», «Выбрать тариф», «Оформить» | 9.3 + C1. See the note under this table |
| R-6 | The trial chip is **«Пробный»** | PC's «Пробный период», Android's ALL-CAPS «ПРОБНЫЙ» | 9.2, 0.4.3 |
| R-7 | No plans available is an empty **state**, not a sentence: «Тарифов пока нет» + «Список обновляется автоматически, загляните позже.» + «Повторить» | Android's one-string `buy_empty`, PC's `Buy_NoPlans` | 9.5 |
| R-8 | The shield status stays **two words** («Не удалось подключиться») and the recovery sentence («Сервер не отвечает. Выберите другой сервер или повторите позже.») lives in the status strip below it | collapsing them into one | 9.4 needs the sentence; `13-start-screen.md` 6 needs the status line to fit |
| R-9 | Search placeholders take **no ellipsis** and name their scope: «Поиск серверов», «Поиск по настройкам», «Поиск по приложениям» | «Поиск серверов…», «Поиск…» | 9.2: a placeholder is a label |
| R-10 | Speed is **Мбит/с**; the unit is in the column label and the figure is bare | `0 KB/s`, `HumanFy`'s `MB/s` | C5 |
| R-11 | **₽** always, one formatter per platform | 6 formatters, 3 of which print `$` | 0.4.4 |
| R-12 | The tunnel mode is named **VPN** in the segment; the elevated-rights sentence says «Режим VPN недоступен без прав администратора» | PC's raw `TUN` literal, Android's «VPN-туннель», PC's «весь трафик» | `12-settings.md` 4.2, 9.3 |
| R-13 | **«Ждём подтверждения в Telegram»** | «Ожидаем подтверждения в Telegram…» | `14-auth.md` 11.1, PG-A5 |
| R-14 | One `common_*` namespace for the 12 shared verbs | 4 keys for «Повторить», 4 for «Отмена», 3 for «Устройства» | C2 |
| R-15 | No dialog button says **«OK»**; the confirm button is the verb of the action | 19 Android dialogs using `android.R.string.ok` | 9.2 |
| R-16 | No code, no exception text, no response body, no «пришлите скриншот» | 9 shipped strings | C4 |
| R-17 | The account surface is **«Аккаунт»** and the name fallback is «Аккаунт» | «Профиль», «Личный кабинет» | 9.3 |
| R-18 | The device row shows **platform and last-active**, never an identifier | `devices_hwid` «ID: %1$s», `Devices_Id` | 9.3 bans HWID as a user-facing word; the id is useless to a user |
| R-19 | Nav labels are Russian in the default locale: «Главная», «Серверы», «Аккаунт», «Настройки» | `bottom_nav_home` = `Home`, `bottom_nav_servers` = `Servers`, `bottom_nav_more` = `More` | 1.4.10 |
| R-20 | The disconnected state is **«Отключено»** | «Не подключено» | C1 |
| R-21 | **One condition, one sentence, one key.** A failure or a note that can occur on two surfaces is written once and referenced twice; the second surface does not get a paraphrase | `err_provider_refresh` vs `strip_provider_failed`, `strip_devices` vs `devices_limit`, `strip_silent` vs `err_server_silent`, `pay_estimate_note` vs `devices_add_note` | C2, 9.4 |
| R-22 | The `depv://` scheme labels use **the product's own verbs**: «Подключить», «Отключить», «Открыть приложение», «Закрыть приложение», «Переключить подключение» | «Запустить туннель», «Отключиться» | C1, C2 |
| R-23 | **No string tells a desktop user about a phone.** «на этом устройстве», never «на этом телефоне»; «Выбрать файл» on PC where Android says «Выбрать из галереи»; no «коснитесь», no «удерживайте» in a string the desktop renders | `auth_link_hint` and `auth_sent_magic_body` («на этом телефоне») and `account_avatar_gallery` («Выбрать из галереи»), all three handed to PC verbatim | 0.1.1, C3 |
| R-24 | The tray's quit item is **«Завершить работу»** | «Выход», «Закрыть» | C1 |
| R-25 | **A permission is explained before it is requested.** Every system consent the product triggers - notifications on Android 13+, the VPN consent dialog - has a rationale string naming what the user gets, and a refusal string naming what to do | nothing: neither platform has any rationale copy today | 9.4, C3 |
| R-26 | **A bulk deletion states its count and confirms**, or it offers an undo. «Удалить недоступные» and «Удалить дубликаты» are not exempt because they sound small | both shipping with neither | `00-rules.md` 7.5, `16-servers.md` 8.3 |

**The note R-5 needs.** `00-rules.md` 9.3 defines **тариф** as «the paid plan» and **подписка** as
«the user's active service». You buy a plan; what you then have is a service. «Купить подписку»
therefore names the wrong object, and it collides inside a single empty state: 3.8's «Устройства, no
subscription» shipped the line «Купите тариф, чтобы подключать устройства.» under a button reading
«Купить подписку». One screen, two objects, one verb. 9.5's own table already says «Купите тариф»
and labels the action «Купить», so this decision agrees with the law rather than overruling it; what
it overrules is `23-account-rework.md` 8.2, `13-start-screen.md` 13.1 and the first edition of this
register, all of which wrote «Купить подписку». Section 7 records the correction. The keys change
name with the string: `account_row_buy` / `home_gate_buy` / `Common_BuySubscription` become
`account_row_buy_plan` / `home_gate_buy_plan` / `Common_BuyPlan`, because a key named for the wrong
noun is the next reader's excuse to write it back.

---

## 3. The register

Eleven sub-sections, one per surface, plus 3.1's shared namespace which the other ten reference.
Each row is one **concept**: one Russian string, one English string, one key per platform, and the
list of every surface that renders it. Two rows never carry the same string (C2), and the two
declared exceptions are in 3.1.2.

**How completeness was established.** Not by walking the two inventories - an inventory lists what
exists, and the strings that hurt a user most are the ones that do not. Both apps were walked screen
by screen against this table, and that pass is what found the share-sheet chooser titles, the
notification channel description, the notification texts for disconnecting, reconnecting and failing,
the four results of «Проверить обновления», the permission rationales, and the fact that Способы
входа could add a sign-in method and never remove one or change a password. None of those appears in
either inventory, because none of them exists in either codebase. 8.3 keeps that method as the
acceptance test rather than the inventory diff.

A `-` in the Android or PC column means that platform does not show the concept, and the Экраны
column always says which surface the other platform shows it on. A `-` is a decision, never a gap.

### 3.1 Shell and navigation

The Android shell is four destinations; the desktop shell is three - the owner ruled on 2026-07-26
that desktop does not gain a Серверы tab (`11-app-structure.md` 2.0), so the server list lives
inside Главная there. Every `Servers_*` key below therefore exists on both platforms; only the
*destination* differs.

**3.1.1 The shared namespace.** A string that appears on more than one surface is declared **once**,
in the `common_*` / `Common_*` namespace (`strings_common.xml`, `L.Common.cs`), and every screen that
shows it references that key. The Экраны column names every one of those surfaces; if a surface is
not listed there, it is not allowed to declare its own copy of the word. This is C2 made operational,
and it is why so many per-screen keys below read `common_*` rather than `servers_*` or `account_*`.

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| App name / wordmark | departament | departament | `app_name` | `Common_AppName` | launcher, toolbar wordmark, tray tooltip, notification title, quick tile label, window title | `P` | `A✓ P+` |
| Window title (PC) | departament VPN | departament VPN | - | `Shell_WindowTitle` | window chrome | `P` | `A- P+` |
| Tab: home | Главная | Home | `nav_home` | `Nav_Home` | bottom nav (Android), rail (PC) | `=` | `A←bottom_nav_home† P✓` |
| Servers | Серверы | Servers | `common_servers` | `Common_Servers` | bottom nav (Android), Серверы header, Главная ledger row, launcher shortcut, PC list band header | `=` | `A←title_servers P←Servers_Title` |
| Account | Аккаунт | Account | `common_account` | `Common_Account` | bottom nav, rail, Аккаунт header, Главная account chip | `=` | `A←auth_account P←Nav_Account` |
| Settings | Настройки | Settings | `common_settings` | `Common_Settings` | bottom nav, rail, Настройки hub header | `=` | `A←title_settings† P←Nav_Settings` |
| Collapse the rail | Свернуть панель | Collapse panel | - | `Nav_CollapsePanel` | PC rail | `=` | `A- P✓` |
| Expand the rail | Развернуть панель | Expand panel | - | `Nav_ExpandPanel` | PC rail | `=` | `A- P✓` |
| Back | Назад | Back | `common_back` | `Common_Back` | every sub-page toolbar, every sheet; also the Android back button's `contentDescription` | `A` | `A+ P✓` |
| Close | Закрыть | Close | `common_close` | `Common_Close` | sheets, dialogs, the QR view. Never the tray's quit item | `P` (R-24) | `A←editor_close P+` |
| Cancel | Отмена | Cancel | `common_cancel` | `Common_Cancel` | every confirm dialog and sheet | `=` | `A←adv_cancel P✓` |
| Save | Сохранить | Save | `common_save` | `Common_Save` | rule editor, MTU field, rename sheet, WebDAV | `P` | `A←adv_save P+` |
| Delete | Удалить | Delete | `common_delete` | `Common_Delete` | destructive confirms | `=` | `A←editor_delete P✓` |
| Unlink | Отвязать | Unlink | `common_unlink` | `Common_Unlink` | the confirm button for unlinking a device (3.4.6) and for unlinking a sign-in method (3.4.8) | `A` | `A+ P←Devices_UnlinkShort` |
| Retry | Повторить | Try again | `common_retry` | `Common_Retry` | every error state and every error strip in the product | `=` | `A←buy_retry P✓` |
| Undo | Отменить | Undo | `common_undo` | `Common_Undo` | the snackbar after a delete or an unlink | `P` | `A←menu_actions_undo P+` |
| Add | Добавить | Add | `common_add` | `Common_Add` | server list, add-sheet title, assets, routing sets | `=` | `A←menu_add_title P✓` |
| Edit | Изменить | Edit | `common_edit` | `Common_Edit` | per-item sheets, server actions | `=` | `A←subs_action_edit P✓` |
| Copy | Копировать | Copy | `common_copy` | `Common_Copy` | referral code, URL schemes, about details | `=` | `A←lp_copy P✓` |
| Copy the link | Скопировать ссылку | Copy link | `common_copy_link` | `Common_CopyLink` | server actions sheet, Устройства row | `P` | `A←scheme_copy_cd P+` |
| Open | Открыть | Open | `common_open` | `Common_Open` | about links, payment history from a strip, notification action | `=` | `A←url_scheme_label_open P✓` |
| Refresh | Обновить | Refresh | `common_refresh` | `Common_Refresh` | provider group header, provider kebab, app list, payment poll | `=` | `A←update_now† P✓` |
| More | Ещё | More | `common_more` | `Common_More` | every overflow affordance's `contentDescription` | `=` | `A←menu_actions_more_cd P←Account_More` |
| Copied | Скопировано | Copied | `common_copied` | `Common_Copied` | the one transient after **any** copy: a link, a referral code, a link code, device details | `=` | `A←lp_copied P✓` |
| Select all | Выбрать все | Select all | `common_select_all` | `Common_SelectAll` | app picker, PC multi-select | `P` | `A←menu_item_select_all† P+` |
| Clear the selection | Снять выделение | Clear selection | `common_clear_selection` | `Common_ClearSelection` | app picker, PC multi-select | `AP` | `A+ P+` |
| Search (settings) | Поиск по настройкам | Search settings | `set_search_hint` | `Settings_SearchHint` | Настройки hub | `AP` | `A+ P+` |
| Search (servers) | Поиск серверов | Search servers | `servers_search_hint` | `Servers_SearchHint` | Серверы; PC Главная list band | `AP` | `A+ P+` |
| Search (apps) | Поиск по приложениям | Search apps | `set_perapp_search` | `PerApp_Search` | Прокси по приложениям | `P` (R-9) | `A←perapp_search_hint P+` |
| Clear search | Очистить поиск | Clear search | `common_search_clear_cd` | `Common_SearchClearCd` | every search field's trailing glyph | `AP` | `A+ P+` |
| On | Включено | On | `common_on` | `Common_On` | A2 value rows | `P` | `A←routing_rule_on P✓` |
| Off | Выключено | Off | `common_off` | `Common_Off` | A2 value rows, the auto-update interval when it is off | `P` | `A←routing_rule_off P✓` |
| Default value | По умолчанию | Default | `common_default` | `Common_Default` | DNS chip, core picker, provider sort | `=` | `A←ps_sort_default P✓` |
| Custom value | Свой | Custom | `common_custom` | `Common_Custom` | DNS chip | `A` | `A+ P✓` |
| Not set | Не задан | Not set | `common_not_set` | `Common_NotSet` | the **value slot** of the SOCKS5 username and password rows while they are empty | `A` (C7) | `A+ P←Settings_NotSet` |
| Advanced | Дополнительно | Advanced | `common_advanced` | `Common_Advanced` | Настройки hub row, Дополнительно title, DNS section header | `=` | `A←subs_ed_section_advanced P←Dns_Advanced` |
| System (masculine) | Системный | System | `common_system` | `Common_System` | Язык value, DNS provider value | `P` | `A←settings_language_system P+` |
| From a QR code | Из QR-кода | From a QR code | `common_from_qr` | `Common_FromQr` | routing import sheet, assets add sheet | `AP` | `A+ P+` |
| Name | Название | Name | `common_name` | `Common_Name` | rename sheet field, rule editor field | `P` | `A←subs_ed_name P+` |
| Password | Пароль | Password | `common_password` | `Common_Password` | sign-in field, sign-in method label, SOCKS5 field | `=` | `A←lp_socks_password P←Login_Password` |
| Domains | Домены | Domains | `common_domains` | `Common_Domains` | routing section header, rule editor field | `P` | `A←routing_ed_domain P+` |
| Errors | Ошибки | Errors | `common_errors` | `Common_Errors` | log level, log filter | `AP` | `A+ P+` |
| Support | Поддержка | Support | `common_support` | `Common_Support` | О приложении section header, PC provider header | `=` | `A←sub_support† P←Sub_Support` |
| Devices | Устройства | Devices | `common_devices` | `Common_Devices` | Аккаунт row, Устройства title, strip action, Данные section header | `=` | `A←devices_title P←Account_Devices` |
| Payment | Оплата | Payment | `common_payment` | `Common_Payment` | Аккаунт group header, pay sheet title | `AP` | `A+ P+` |
| Payment history | История платежей | Payment history | `common_payment_history` | `Common_PaymentHistory` | Аккаунт row, sub-page title | `=` | `A←history_title P✓` |
| Subscription | Подписка | Subscription | `common_subscription` | `Common_Subscription` | Главная ledger row, Аккаунт group header | `=` | `A←settings_section_subscription P←Settings_SecSubscription` |
| Sign in | Войти | Sign in | `common_signin` | `Common_SignIn` | Главная gate, sign-in form CTA | `A` | `A+ P←Login_SubmitSignIn` |
| Sign in with Telegram | Войти через Telegram | Sign in with Telegram | `common_signin_telegram` | `Common_SignInTelegram` | Главная gate, Аккаунт gate, auth surface A | `=` | `A←auth_btn_telegram P✓` |
| Sign in with email | Войти по почте | Sign in with email | `common_signin_email` | `Common_SignInEmail` | Главная gate, Аккаунт gate, auth surface A | `AP` | `A+ P+` |
| Add a provider | Добавить провайдера | Add a provider | `common_add_provider` | `Common_AddProvider` | Главная gate, add sheet, Серверы empty state | `A` | `A+ P←Common_AddSubscription` |
| Buy a plan | Купить тариф | Buy a plan | `common_buy_plan` | `Common_BuyPlan` | Главная gate, Аккаунт row, Купить title, Устройства empty state | `AP` (R-5) | `A+ P+` |
| Link Telegram | Привязать Telegram | Link Telegram | `common_link_telegram` | `Common_LinkTelegram` | Аккаунт row, Способы входа, auth surface E, empty-state action | `P` | `A←home_link_telegram P+` |
| Create an account | Создать аккаунт | Create an account | `common_create_account` | `Common_CreateAccount` | register CTA, the mode switch on the sign-in form | `A` | `A+ P←Login_CreateAccount` |
| Another way to sign in | Другой способ входа | Another way to sign in | `common_other_signin` | `Common_OtherSignIn` | gate action, method sheet title | `A` | `A+ P←Login_ChooseAnother` |
| Refresh providers | Обновить провайдеров | Refresh providers | `common_refresh_providers` | `Common_RefreshProviders` | Серверы header overflow, PC shortcut | `AP` (R-4) | `A+ P+` |
| Sort: provider order | Как у провайдера | Provider order | `common_sort_provider` | `Common_SortProvider` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Sort: latency | По задержке | By latency | `common_sort_ping` | `Common_SortPing` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Sort: name | По названию | By name | `common_sort_name` | `Common_SortName` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Upgrade the plan | Улучшить тариф | Upgrade plan | `common_upgrade_plan` | `Common_UpgradePlan` | Аккаунт card overflow, upgrade sheet title | `=` | `A←account_upgrade P←Account_UpgradeTariff` |
| Top up the balance | Пополнение баланса | Balance top-up | `common_topup_title` | `Common_TopUpTitle` | top-up sheet title, pay-sheet subject, history row kind | `A` | `A+ P←Account_TopUpTitle` |

**Notes.**

- **The nav labels.** `values/strings.xml` ships `Home`, `Servers`, `More` in the default locale
  (R-19), which is why the Ключи column marks them `←bottom_nav_*†`: the Russian exists, but only in
  `values-ru/`. `bottom_nav_more` is dead (`menu_bottom_nav.xml` is referenced by nothing, the bar is
  drawn by hand in `activity_main.xml`) and is deleted with the menu file. The survivors are
  **renamed** out of the `bottom_nav_*` prefix, because the desktop draws the same labels in a side
  rail and a key named for a bottom bar is a key that will be duplicated the moment a tablet layout
  appears.
- **«Вкл» / «Выкл» become «Включено» / «Выключено».** They are abbreviations, and this file deletes
  «дн.» with the words «an abbreviation 9.2 wants a word»; it cannot then ship two of its own.
  3.6.1 separately shipped the unabbreviated «Выключено» for the auto-update interval, so the
  product had both forms already. One key now serves both surfaces.
- **«Не задан» is a value, not a watermark.** C7 is three rows above it: a watermark may only carry
  an example value. The SOCKS5 username and password rows carry their names as persistent labels
  (3.6.10) and show «Не задан» in the row's value slot while they are empty.
- **«Повторить».** Android has four keys for it and PC has two, whose English halves disagree
  («Retry» vs «Try again»). One key, English **Try again**.
- **«Назад».** `res/layout/view_toolbar.xml:70` hardcodes `android:contentDescription="Назад"`, and
  that toolbar is shared, so the literal is spoken on every sub-page in the app.
- **The search placeholders.** R-9 removes the ellipsis on both platforms and gives PC's generic
  `Common_SearchPlaceholder` («Поиск…») a scope. That key is deleted.
- `Dns_Advanced` is not a separate key: the DNS section header is `common_advanced`, the same string
  the hub row and the Дополнительно screen title carry. `Dns_AdvancedHint`, which currently carries
  the FakeIP explanation and names sing-box inside it, is retired in favour of `Dns_FakeIpHint` on
  the row that actually owns the behaviour (3.6.4).

**3.1.2 The two exemptions.** Everything else in this register that reads the same in two places is
one key. These two are not, and the reason is recorded here so that a later reader does not "fix"
them:

| Strings | Why they stay apart |
|---|---|
| `common_account` «Аккаунт» (the surface) and `account_name_fallback` «Аккаунт» (the head's name slot when no name is known) | One word, two concepts. Merging them means a future edit to the tab label silently renames every user whose display name is unknown. C2 forbids one key with two concepts more strongly than it forbids two keys with one word. |
| `home_status_no_subscription` «Подписки нет» and `account_empty_title` «Подписки пока нет» | Recorded in full in 3.8. The first names a condition in a two-word status slot; the second opens an empty state, and «пока» is what makes it an invitation rather than a verdict. |

Nothing else in the product is allowed to be two strings, or two keys, for one concept.

### 3.2 Connect and status - Главная

Canonical spec: `13-start-screen.md` 13. The Android keys below are that document's, with `home_`
kept everywhere except the strings 3.1 already owns for the whole product.

**The connect object and its status line**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Disconnected | Отключено | Disconnected | `home_status_disconnected` | `Home_StatusDisconnected` | Главная shield, tray tooltip, quick tile | `=` (R-20) | `A←toast_status_disconnected† P←Status_Disconnected` |
| Connecting | Подключение… | Connecting… | `home_status_connecting` | `Home_StatusConnecting` | shield, notification, tile | `=` | `A←toast_status_connecting† P←Status_Connecting` |
| Connected | Подключено | Connected | `home_status_connected` | `Home_StatusConnected` | shield, notification, tile, server row state | `A` | `A+ P←Status_Connected` |
| Disconnecting | Отключение… | Disconnecting… | `home_status_disconnecting` | `Home_StatusDisconnecting` | shield, notification | `AP` | `A+ P+` |
| Connect failed | Не удалось подключиться | Could not connect | `home_status_error` | `Home_StatusError` | shield | `=` (R-8) | `A←toast_status_failed† P←Common_CouldntConnect` |
| No server chosen | Сервер не выбран | No server selected | `home_status_no_server` | `Home_StatusNoServer` | shield | `AP` | `A+ P+` |
| No servers at all | Нет серверов | No servers | `common_no_servers` | `Common_NoServers` | shield; the Серверы and Главная empty-state titles | `=` | `A←servers_empty_title P←Home_NoSubs` |
| No subscription | Подписки нет | No subscription | `home_status_no_subscription` | `Home_StatusNoSubscription` | shield; the Главная ledger's subscription value | `AP` | `A+ P+` |
| Subscription expired | Подписка истекла | Subscription expired | `common_sub_expired_title` | `Common_SubExpiredTitle` | shield; Аккаунт card title | `AP` | `A+ P+` |
| Detail: retry hint | Нажмите, чтобы повторить | Tap to try again | `home_detail_retry` | `Home_DetailRetry` | under the shield, error state | `A` | `A+ P←Home_RetryHint` |
| Detail: pick a server | Выберите сервер, чтобы подключиться | Choose a server to connect | `home_detail_pick_server` | `Home_DetailPickServer` | under the shield, no-server state, both shells | `AP` | `A+ P+` |
| Connect | Подключить | Connect | `common_connect` | `Common_Connect` | shield `contentDescription`, tray item, launcher shortcut, `depv://connect` | `A` (R-22) | `A+ P←Tray_Connect` |
| Disconnect | Отключить | Disconnect | `common_disconnect` | `Common_Disconnect` | shield, tray item, notification action, `depv://disconnect` | `=` (R-22) | `A←menu_actions_busy_action P←Tray_Disconnect` |
| Cancel connecting | Отменить подключение | Cancel connecting | `home_cd_cancel` | `Home_CdCancel` | shield while connecting | `AP` | `A+ P+` |

**Notes.** «Сервер не выбран»: Android ships «Выберите сервер» (`home_select_server`) and PC ships
the same as `Home_ChooseServer`; both are imperatives standing in a status slot, which reads as an
instruction where a state belongs. The instruction moves to the detail line, where it belongs.

**The detail line does not name a destination.** The first edition wrote «Выберите сервер в разделе
«Серверы»» and gave it to both platforms. That string sends a desktop user to a tab that 3.1, section
7 and the owner's decision of 2026-07-26 all record as **not existing on the desktop**; its English
half («Choose a server on the Серверы tab») puts a Cyrillic word inside the value that goes into
`values-en/`, where the tab is called Servers; and at 34 characters it overruns the one-line status
detail slot that `13-start-screen.md` 6 constrains. «Выберите сервер, чтобы подключиться» says the
same thing, is true on both shells, and fits.

`Home_NotConnected` and `Status_Disconnected` collapse into one key (R-20). `Status_ConnectedTo`
(«Подключено · {0}») is deleted: the connected server's name is on the row below the shield, and
`13-start-screen.md` 6 does not put it in the status line.

**The numeric strip** (C5: the unit is in the label, the figure is bare)

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Download column | Приём, Мбит/с | Down, Mbit/s | `home_strip_down_label` | `Home_StripDownLabel` | Главная strip | `AP` | `A+ P+` |
| Upload column | Отдача, Мбит/с | Up, Mbit/s | `home_strip_up_label` | `Home_StripUpLabel` | Главная strip | `AP` | `A+ P+` |
| Latency column | Задержка, мс | Latency, ms | `home_strip_ping_label` | `Home_StripPingLabel` | Главная strip | `AP` | `A+ P+` |
| Session uptime | Время сессии | Session time | `home_strip_uptime_label` | `Home_StripUptimeLabel` | Главная strip, connected state | `AP` | `A+ P+` |

`speed_zero` («0 KB/s») is deleted on Android; the six `"0 KB/s"` literals in `HomeViewModel.cs`,
`ConnectHeroView.axaml.cs` and `ConnectHeroView.axaml` are deleted on PC. The idle figure is `0`.
`Utils.HumanFy` stops being used for display: it is EN-invariant and prints a dot decimal.

**The ledger rows and the subscription line**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Servers row | Серверы | Servers | `common_servers` | `Common_Servers` | Главная ledger | `=` | `A←title_servers P←Servers_Title` |
| Servers and providers | %1$s · %2$s | %1$s · %2$s | `common_count_pair` | `Common_CountPair` | Главная ledger value, Серверы header meta; both arguments are plurals (section 4) | `AP` | `A+ P+` |
| Subscription row | Подписка | Subscription | `common_subscription` | `Common_Subscription` | Главная ledger, Аккаунт group header | `=` | `A←settings_section_subscription P←Settings_SecSubscription` |
| Active until | Активна до %1$s | Active until %1$s | `common_sub_active_until` | `Common_SubActiveUntil` | Главная ledger, Аккаунт card | `AP` (R-1) | `A+ P+` |
| Trial until | Пробный период до %1$s | Trial until %1$s | `home_sub_trial` | `Home_SubTrial` | Главная ledger | `AP` | `A+ P+` |
| Expired on | Истекла %1$s | Expired %1$s | `home_sub_expired` | `Home_SubExpired` | Главная ledger | `AP` | `A+ P+` |
| No subscription, ledger value | Подписки нет | No subscription | `home_status_no_subscription` | `Home_StatusNoSubscription` | Главная ledger; shared with the shield | `AP` | `A+ P+` |
| Could not refresh | Не удалось обновить | Could not refresh | `home_sub_stale` | `Home_SubStale` | Главная ledger | `AP` | `A+ P+` |
| Expiring chip | Истекает | Expiring | `common_chip_expiring` | `Common_ChipExpiring` | Главная ledger, Аккаунт card | `A` | `A+ P←Account_HealthExpiring` |
| Expired chip | Истекла | Expired | `common_chip_expired` | `Common_ChipExpired` | Главная ledger, Аккаунт card, provider header | `=` | `A←sub_expired† P←Sub_Expired` |
| Account row title | Аккаунт | Account | `common_account` | `Common_Account` | Главная account chip | `=` | `A←auth_account P←Nav_Account` |
| Account row subtitle | Вход, подписка, устройства | Sign-in, subscription, devices | `home_account_subtitle` | `Home_AccountSubtitle` | Главная account chip, signed out | `AP` | `A+ P+` |
| Manage account | Управление аккаунтом | Manage account | `home_account_manage` | `Home_ManageAccount` | Главная account chip, signed in | `=` | `A←auth_open_account P✓` |

**«Не оформлена» is deleted.** It was the ledger's value for a missing subscription, and «оформ» is
the exact root 9.3 bans for buying. The ledger shows the same two words the shield shows, from the
same key. «Истекла» was declared three times on PC (`Account_HealthExpired`, `Account_ExpiredOn`,
`Sub_Expired`); two are deleted and the third becomes `Common_ChipExpired`.

**The gate block** - what Главная offers when there is nothing to connect to

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Sign-in caption | Войдите, чтобы появились серверы Departament. | Sign in and your Departament servers appear. | `home_gate_signin_caption` | `Home_GateSigninCaption` | Главная gate, signed out | `AP` | `A+ P+` |
| Sign in | Войти | Sign in | `common_signin` | `Common_SignIn` | Главная gate | `A` | `A+ P←Login_SubmitSignIn` |
| Add a provider | Добавить провайдера | Add a provider | `common_add_provider` | `Common_AddProvider` | Главная gate, Серверы empty state | `A` | `A+ P←Common_AddSubscription` |
| Buy caption | Осталось выбрать тариф. | One step left: choose a plan. | `home_gate_buy_caption` | `Home_GateBuyCaption` | Главная gate, signed in, no subscription | `AP` | `A+ P+` |
| Buy a plan | Купить тариф | Buy a plan | `common_buy_plan` | `Common_BuyPlan` | Главная gate, Аккаунт row, Купить title | `AP` (R-5) | `A+ P+` |
| Sync caption | Подписка активна, серверы ещё не загружены. | Your subscription is active, the servers are not loaded yet. | `home_gate_sync_caption` | `Home_GateSyncCaption` | Главная gate | `AP` | `A+ P+` |
| Load servers | Загрузить серверы | Load servers | `home_gate_sync` | `Home_GateSync` | Главная gate | `AP` | `A+ P+` |

«Войдите, чтобы получить серверы Departament.» was the first edition's line. A user does not
*receive* servers; that is an English «get» carried across whole. The servers **appear**, which is
also what 9.5's own no-servers line says («…чтобы появились серверы»).

**The status strip conditions** (`11-app-structure.md` 8.2; one strip at a time, in this priority)

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Offline | Нет сети. Показаны последние данные. | No network. Showing the last known data. | `strip_offline` | `Strip_Offline` | Главная, Серверы, Аккаунт; action «Повторить» | `AP` | `A+ P+` |
| Stale data note | Данные могли устареть | This data may be out of date | `strip_stale` | `Strip_Stale` | under a stale value on any of the three, and on a provider group header | `AP` | `A+ P+` |
| Subscription expired | Подписка истекла. Продлите её, чтобы подключаться. | Your subscription has expired. Renew it to connect. | `strip_expired` | `Strip_Expired` | Главная, Серверы, Аккаунт; action «Продлить» | `AP` (9.4) | `A+ P+` |
| Expiring soon | Подписка заканчивается %1$s. | Your subscription ends on %1$s. | `strip_expiring` | `Strip_Expiring` | Главная; action «Продлить» | `AP` | `A+ P+` |
| Device limit | Достигнут лимит устройств. Отвяжите одно из них. | You have reached the device limit. Unlink one of them. | `strip_devices` | `Strip_Devices` | Главная, Аккаунт, **and every other device-limit failure**; action «Устройства» | `AP` (R-21) | `A+ P+` |
| Provider refresh failed | Не удалось обновить провайдера. Проверьте его ссылку и повторите попытку. | Could not refresh the provider. Check its link and try again. | `strip_provider_failed` | `Strip_ProviderFailed` | Серверы group header **and every other provider-refresh failure**; action «Повторить» | `AP` (R-21) | `A+ P+` |
| Server went silent | Сервер не отвечает. Выберите другой сервер. | The server is not responding. Choose another one. | `strip_silent` | `Strip_Silent` | Главная, **and every other silent-server failure**; action «Сменить сервер» | `AP` (R-21) | `A+ P+` |
| TUN needs rights | Режим VPN недоступен без прав администратора | VPN mode needs administrator rights | - | `Home_TunUnavailable` | PC Главная; action «Перезапустить с правами» | `P` (R-12) | `A- P✓` |
| Restart elevated | Перезапустить с правами | Restart as administrator | - | `Home_RestartElevated` | PC Главная strip, and the recovery control for a failed `depv://` registration | `=` | `A- P✓` |
| Strip actions | Продлить · Повторить · Устройства · Сменить сервер · Как исправить | Renew · Try again · Devices · Change server · How to fix | `home_action_renew`, `common_retry`, `common_devices`, `home_action_change_server`, `home_action_howto` | `Home_ActionRenew`, `Common_Retry`, `Common_Devices`, `Home_ActionChangeServer`, `Home_ActionHowto` | as above | `AP` | `A+ P+` |

**Three conditions, three sentences, three keys - not six** (R-21). The first edition wrote each of
these conditions twice: once as a strip and once as an error, with a different sentence each time.
«Достигнут лимит устройств.» in the strip and «Достигнут лимит устройств. Отвяжите одно из
устройств.» in 3.7, with 9.4 itself carrying a third form that names the destination inside the
sentence. «Сервер не отвечает. Выберите другой сервер.» in the strip and «…Выберите другой сервер
или повторите позже.» in 3.7, both rendered on Главная. «Не удалось обновить провайдера.» in the
strip and «Не удалось обновить подписку. Проверьте ссылку провайдера…» in 3.7, which additionally
broke the terminology lock. Each condition now has one sentence, one key, and one recovery control,
and 3.7 references the key rather than restating the sentence. The sentence never names the control
that sits next to it: C3 makes the control part of the string's contract, so «Отвяжите одно из них»
plus a button reading «Устройства» is complete, while «…в разделе «Устройства»» plus the same button
says it twice.

**Deleted from Главная:** `home_welcome_title`, `home_empty_title` («Подписок пока нет»),
`home_empty_subtitle` («Добавьте подписку, чтобы появились серверы.» - breaks 9.3 twice),
`home_empty_add_qr`, `home_empty_add_clipboard`, `home_or_sign_in`, `home_not_connected`,
`home_select_server`, `home_sub_none` («Не оформлена»), `home_row_servers`,
`home_row_servers_value`, `home_row_subscription`, `home_sub_active`, `home_chip_expiring`,
`home_chip_expired`, `home_account_title`, `home_gate_signin`, `home_gate_add_provider`,
`home_gate_buy`, `home_cd_connect`, `home_cd_disconnect`, `home_status_no_servers`,
`home_status_expired`, `home_action_devices`, `speed_zero`, `connection_connected` («Соединено,
нажмите для проверки»), `connection_not_connected`, `memory_app_usage`,
`memory_normal/elevated/high`, `memory_value` (`%1$d MB · %2$s`), `sub_days_left` (`%1$s · %2$dd`).
On PC: `Home_Welcome`, `Home_NoSubs`, `Home_NoSubsHint`, `Home_ChooseServer`, `Home_NotConnected`,
`Home_MyServers`, `Home_RowServers`, `Home_ServersProvidersMeta`, `Home_RowSubscription`,
`Home_SubActive`, `Home_ChipExpiring`, `Home_ChipExpired`, `Home_AccountTitle`, `Home_GateSignin`,
`Home_GateBuy`, `Home_CdConnect`, `Home_CdDisconnect`, `Home_StatusNoServers`,
`Home_StatusExpired`, `Home_ActionDevices`, `Status_Disconnected`, `Status_ConnectedTo`,
`Onboarding_Title`, `Onboarding_Subtitle`, `Onboarding_OrSignIn`, `Onboarding_OrSignInShort` (the
onboarding view is deleted, `14-auth.md` PG-A3). The keys in those two lists are not lost copy: each
of them is a per-screen duplicate of a string 3.1 now declares once.

### 3.3 Servers and providers

Canonical spec: `16-servers.md` 13. On PC these strings render inside Главная (`11-app-structure.md`
2.0), not on a tab of their own.

**Screen, header, meta**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Screen title | Серверы | Servers | `common_servers` | `Common_Servers` | Серверы header; PC list band header | `=` | `A←title_servers P←Servers_Title` |
| Count line | %1$s · %2$s | %1$s · %2$s | `common_count_pair` | `Common_CountPair` | header meta; two plurals (section 4) | `AP` | `A+ P+` |
| Sort | Сортировка | Sort | `servers_sort_title` | `Servers_SortTitle` | header overflow | `AP` | `A+ P+` |
| Sort: provider order | Как у провайдера | Provider order | `common_sort_provider` | `Common_SortProvider` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Sort: latency | По задержке | By latency | `common_sort_ping` | `Common_SortPing` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Sort: name | По названию | By name | `common_sort_name` | `Common_SortName` | sort sheet, Настройки → Провайдеры | `AP` | `A+ P+` |
| Sort: manual | Вручную | Manual | `servers_sort_manual` | `Servers_SortManual` | sort sheet | `AP` | `A+ P+` |
| More (a11y) | Ещё | More | `common_more` | `Common_More` | header overflow | `=` | `A←menu_actions_more_cd P←Account_More` |
| Provider actions (a11y) | Действия провайдера | Provider actions | `servers_group_actions_cd` | `Servers_GroupActionsCd` | group header kebab | `AP` | `A+ P+` |

**Group headers and their state line**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Manually added group | Добавленные вручную | Added manually | `servers_group_local` | `Servers_GroupLocal` | group header | `AP` | `A+ P+` |
| Not refreshed | Не обновился | Not refreshed | `servers_group_stale` | `Servers_GroupStale` | group header | `AP` | `A+ P+` |
| Data may be stale | Данные могли устареть | This data may be out of date | `strip_stale` | `Strip_Stale` | group header, offline; shared with the Главная strip | `AP` | `A+ P+` |
| Refreshing | Обновляется… | Refreshing… | `servers_group_updating` | `Servers_GroupUpdating` | group header | `AP` | `A+ P+` |
| Search result count | Найдено: %1$d из %2$d | Found %1$d of %2$d | `servers_group_found` | `Servers_GroupFound` | header meta while filtering | `AP` | `A+ P+` |
| Auto-update value | Автообновление · %1$s | Auto-update · %1$s | `servers_provider_auto` | `Servers_ProviderAuto` | provider header | `AP` (R-4) | `A+ P+` |

«Добавленные вручную»: Android ships «Локальные», which is developer vocabulary.

**The server row**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Latency value | %1$d мс | %1$d ms | `servers_ping_unit` | `Servers_PingUnit` | server row trailing | `AP` | `A+ P+` |
| No answer | нет ответа | no answer | `servers_ping_none` | `Servers_PingNone` | server row trailing | `AP` | `A+ P+` |
| Connected state | Подключено | Connected | `home_status_connected` | `Home_StatusConnected` | server row, shared key | `A` | `A+ P←Status_Connected` |
| Row (a11y) | %1$s, %2$s | %1$s, %2$s | `servers_row_cd` | `Servers_RowCd` | TalkBack / Narrator: name, then state | `AP` | `A+ P+` |
| No TLS layer | Без шифрования | No encryption | `servers_security_none` | `Servers_SecurityNone` | server row meta, where `ProfileDisplay.cs` prints `NONE` | `AP` | `A+ P+` |
| Hand-written config | Свой конфиг | Custom config | `servers_security_custom` | `Servers_SecurityCustom` | server row meta, where `ProfileDisplay.cs` prints `CUSTOM` | `AP` | `A+ P+` |
| Long-press hint | Удерживайте сервер, чтобы открыть действия | Press and hold a server to open its actions | `servers_longpress_hint` | - | first run on Серверы (Android only; the desktop opens the same menu with a right-click and needs no hint) | `A` | `A+ P-` |

The latency unit: Android formats it in Kotlin (`dto/entities/ServerAffiliationInfo.kt:76` holds the
literal «мс»), PC formats it in the view. Both move to the resource. `NONE` and `CUSTOM` are the two
tokens `ProfileDisplay.cs` renders as ALL-CAPS English words on a Russian server row; they are not
protocol identifiers and section 9's list does not cover them, so they get Russian here.

**Per-item actions** (Android bottom sheet, PC flyout - `16-servers.md` 8.2, same nine rows in the
same order)

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Connect | Подключиться | Connect | `servers_action_connect` | `Servers_ActionConnect` | actions sheet | `P` | `A←url_scheme_label_connect P+` |
| Make default | Сделать основным | Make default | `servers_action_set_default` | `Servers_MakeDefault` | actions sheet | `=` | `A←server_action_set_default P✓` |
| Test latency | Проверить задержку | Test latency | `servers_action_ping` | `Common_TestLatency` | actions sheet, header overflow | `=` | `A←menu_actions_ping_cd P✓` |
| Edit | Изменить | Edit | `common_edit` | `Common_Edit` | actions sheet | `=` | `A←subs_action_edit P✓` |
| Duplicate | Дублировать | Duplicate | `servers_action_duplicate` | `Servers_Duplicate` | actions sheet | `=` | `A←server_action_duplicate P✓` |
| Duplicate suffix | (копия) | (copy) | `servers_action_duplicate_suffix` | `Servers_DuplicateSuffix` | appended to the new name | `P` | `A←server_action_duplicate_suffix P+` |
| Move | Переместить | Move | `servers_action_move` | `Servers_ActionMove` | actions sheet → sub-sheet | `AP` | `A+ P+` |
| Move to top | В начало | To the top | `servers_action_move_top` | `Servers_ActionMoveTop` | move sub-sheet | `AP` | `A+ P+` |
| Move up | Выше | Up | `servers_action_move_up` | `Servers_ActionMoveUp` | move sub-sheet | `AP` | `A+ P+` |
| Move down | Ниже | Down | `servers_action_move_down` | `Servers_ActionMoveDown` | move sub-sheet | `AP` | `A+ P+` |
| Move to bottom | В конец | To the bottom | `servers_action_move_bottom` | `Servers_ActionMoveBottom` | move sub-sheet | `AP` | `A+ P+` |
| Move to another group | В другую группу… | To another group… | `servers_action_move_group` | `Servers_ActionMoveGroup` | move sub-sheet | `AP` | `A+ P+` |
| Position | Позиция %1$d из %2$d | Position %1$d of %2$d | `servers_action_position` | `Servers_ActionPosition` | move sub-sheet header | `AP` | `A+ P+` |
| Copy the link | Скопировать ссылку | Copy link | `common_copy_link` | `Common_CopyLink` | actions sheet | `P` | `A←scheme_copy_cd P+` |
| Share a QR code | Поделиться QR-кодом | Share QR code | `servers_action_share_qr` | `Servers_ActionShareQr` | actions sheet | `AP` | `A+ P+` |
| Share sheet title | Поделиться сервером | Share this server | `servers_share_chooser` | - | the title `Intent.createChooser` draws above the Android share sheet | `A` | `A+ P-` |
| Delete the server | Удалить сервер | Delete server | `servers_action_delete` | `Servers_ActionDelete` | actions sheet, destructive | `P` | `A←srv_delete P+` |

The three share and delete rows: Android ships «Поделиться (QR)» / «Поделиться (буфер)» /
«Удалить», PC ships «Поделиться · QR-код» / «Поделиться · ссылка» / «Удалить». Two separators and
two objects for one pair of actions; the register names the object («ссылка», «QR-код», «сервер»)
and drops the parenthetical. The chooser title is a string a user reads on every Android share, and
neither platform's inventory had one - `Intent.createChooser(intent, title)` was being called with a
`null` or an English title.

**Provider actions** (the group header kebab)

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Refresh | Обновить | Refresh | `common_refresh` | `Common_Refresh` | provider kebab, provider group header | `=` | `A←update_now† P✓` |
| Rename | Переименовать | Rename | `servers_provider_rename` | `Servers_ProviderRename` | provider kebab | `AP` | `A+ P+` |
| Pin | Закрепить | Pin | `servers_provider_pin` | `Servers_ProviderPin` | provider kebab | `A` | `A+ P←Sub_Pin` |
| Unpin | Открепить | Unpin | `servers_provider_unpin` | `Servers_ProviderUnpin` | provider kebab | `AP` | `A+ P+` |
| Open the link | Открыть ссылку | Open link | `servers_provider_open_link` | `Servers_ProviderOpenLink` | provider kebab | `AP` | `A+ P+` |
| Provider settings | Настройки провайдеров | Provider settings | `set_providers_title` | `Provider_Title` | provider kebab, Настройки hub row, sub-page title | `=` | `A←ps_title P✓` |
| Delete the provider | Удалить провайдера | Delete provider | `servers_provider_delete` | `Servers_ProviderDelete` | provider kebab, destructive | `A` | `A+ P←Sub_Delete` |
| Support | Поддержка | Support | `common_support` | `Common_Support` | PC provider header, О приложении section header | `=` | `A←sub_support† P←Sub_Support` |
| Open support | Открыть поддержку | Open support | - | `Sub_OpenSupport` | PC provider header | `=` | `A- P✓` |

The provider rows: Android says «подписка» for all of them (`sub_delete`, `sub_pin`, `sub_unpin`,
`title_sub_update`), which is the single most widespread terminology-lock break in the product - 48
live Android strings contain the root «подпис» and about half of them mean provider
(`40-copy-inventory-android.md` F5.1). PC already says «провайдер» in its values but not in its key
names: `Sub_Delete`, `Sub_Pin`, `Common_UpdateSubscription` and `Sub_AutoUpdate` are renamed with the
string, because a key named for the wrong noun is the next reader's excuse to write it back.

**Adding servers**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Add sheet title | Добавить | Add | `common_add` | `Common_Add` | add sheet | `=` | `A←menu_add_title P✓` |
| Add a provider | Добавить провайдера | Add a provider | `common_add_provider` | `Common_AddProvider` | add sheet, empty state, Главная gate | `A` | `A+ P←Common_AddSubscription` |
| Scan a QR code | Сканировать QR-код | Scan a QR code | `servers_add_qr` | `Servers_AddQr` | add sheet | `P` | `A←menu_add_scan_qr P+` |
| Paste from the clipboard | Вставить из буфера | Paste from clipboard | `servers_add_clipboard` | `Servers_AddClipboard` | add sheet | `AP` | `A✓ P+` |
| Enter a link | Ввести ссылку | Enter a link | `servers_add_link` | `Servers_AddLink` | add sheet | `P` | `A←menu_actions_add_link P+` |
| Import from a file | Импортировать из файла | Import from a file | `servers_add_file` | `Servers_AddFile` | add sheet | `P` | `A←menu_actions_add_file P+` |
| Create manually | Создать вручную | Create manually | `servers_add_manual` | `Servers_AddManual` | add sheet | `P` | `A←menu_actions_add_create P+` |
| Link field label | Ссылка провайдера или сервера | Provider or server link | `servers_add_link_label` | `Servers_AddLinkLabel` | manual-entry sheet | `AP` | `A+ P+` |
| Link field watermark | Ссылка из бота или адрес сервера | Link from the bot, or a server address | `servers_add_link_hint` | `Servers_AddLinkHint` | manual-entry sheet | `AP` | `A+ P+` |

The two field strings: `MainActivity.kt:2497-2500` hardcodes both, and the second leaks a fake domain
(`https://departament.example/sub`) into user-visible copy. The watermark lost its opening verb
(«Вставьте ссылку из бота или адрес сервера», 40 characters) because it sits inside a single-line
field on a 320 dp screen, where the label above it already says what the field is.

**Bulk actions in the header overflow**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Update providers | Обновить провайдеров | Refresh providers | `common_refresh_providers` | `Common_RefreshProviders` | header overflow, PC shortcut | `AP` (R-4) | `A+ P+` |
| Test latency, scoped | Проверить задержку: %1$s | Test latency: %1$s | `servers_menu_ping_scoped` | `Servers_MenuPingScoped` | header overflow, names the scope | `AP` | `A+ P+` |
| Stop testing | Остановить проверку | Stop testing | `servers_menu_ping_stop` | `Servers_MenuPingStop` | header overflow while testing | `AP` | `A+ P+` |
| Collapse all | Свернуть все группы | Collapse all groups | `servers_menu_collapse` | `Servers_MenuCollapse` | header overflow | `AP` | `A+ P+` |
| Expand all | Развернуть все группы | Expand all groups | `servers_menu_expand` | `Servers_MenuExpand` | header overflow | `AP` | `A+ P+` |
| Export to the clipboard | Экспортировать в буфер | Export to clipboard | `servers_menu_export` | `Servers_MenuExport` | header overflow | `P` | `A←menu_actions_export P+` |
| Delete duplicates | Удалить дубликаты | Delete duplicates | `servers_menu_del_duplicate` | `Servers_MenuDelDuplicate` | header overflow, destructive | `P` (R-26) | `A←menu_actions_del_duplicate P+` |
| Delete unreachable | Удалить недоступные серверы | Delete unreachable servers | `servers_menu_del_invalid` | `Servers_MenuDelInvalid` | header overflow, destructive | `AP` (R-26) | `A+ P+` |
| Delete all servers | Удалить все серверы | Delete all servers | `servers_menu_del_all` | `Servers_MenuDelAll` | header overflow, destructive | `P` (R-26) | `A←menu_actions_del_all P+` |
| Selected count (PC) | Выбрано: %1$d | %1$d selected | - | `Servers_SelectedCount` | PC multi-select (PG-1) | `P` | `A- P+` |
| Clear selection | Снять выделение | Clear selection | `common_clear_selection` | `Common_ClearSelection` | PC multi-select, app picker | `AP` | `A+ P+` |

**All three bulk deletions confirm** (R-26). The first edition gave a confirm dialog only to «Удалить
все серверы» and let the other two delete an unbounded number of servers with no confirm, no count
and no undo; their result string existed in 5.2 and in 4.2 and appeared in no approved row at all.
The three confirms are in 3.10, each with a count in its body, and the result below is theirs.
«Удалить недоступные» also gained its object: at n=0 «Удалить недоступные» names nothing, and the
menu row above it says «Удалить дубликаты», which does.

**Transient confirmations**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Server deleted | Сервер удалён | Server deleted | `servers_deleted` | `Servers_Deleted` | snackbar with «Отменить» | `AP` | `A+ P+` |
| Servers deleted, count | Удалено: %1$s | Deleted: %1$s | `servers_deleted_count` | `Servers_DeletedCount` | snackbar with «Отменить» after any of the three bulk deletions; %1$s is a plural | `AP` | `A+ P+` |
| Provider deleted | Провайдер удалён | Provider deleted | `servers_provider_deleted` | `Servers_ProviderDeleted` | snackbar with «Отменить» | `AP` | `A+ P+` |
| Copied | Скопировано | Copied | `common_copied` | `Common_Copied` | snackbar after «Скопировать ссылку» | `=` | `A←lp_copied P✓` |
| Provider refreshed | Провайдер «%1$s» обновлён | Provider «%1$s» is up to date | `servers_refreshed` | `Servers_Refreshed` | snackbar after a provider refresh | `AP` | `A+ P+` |
| Servers added | Добавлено: %1$s | Added: %1$s | `servers_added` | `Servers_Added` | snackbar after an import; %1$s is a plural | `AP` | `A+ P+` |
| Provider already added | Провайдер уже добавлен | This provider is already added | `servers_provider_exists` | `Servers_ProviderExists` | snackbar after a duplicate import | `AP` | `A+ P+` |
| Not our link | Эта ссылка не от Departament. Используйте ссылку из бота. | This link is not from Departament. Use the link from the bot. | `servers_foreign_link` | `Servers_ForeignLink` | snackbar after an import | `AP` (C8) | `A+ P+` |

«Обновлено: %1$s» was the first edition's refresh confirmation, and its argument is a provider name:
«Обновлено: Departament» is a neuter participle agreeing with nothing, four rows under the correct
«Провайдер удалён». The subject is named, the participle agrees with it, and the quotes are the
product's «ёлочки». The last three rows are hardcoded Kotlin literals today, the last one in **three**
files (`MainActivity.kt:2596`, `ScScannerActivity.kt:35`, `SubEditActivity.kt:194`), which means a
wording fix has to be made three times or the app contradicts itself depending on how you imported -
and all three write the brand lowercase inside a Russian sentence, which C8 forbids.

### 3.4 Account and billing

Canonical spec: `23-account-rework.md` 8. Every string in this sub-section is identical on both
platforms by contract (§13). The PC keys below are that document's; where PC ships a different key
name today, the old name is given in the note.

**3.4.1 Tab, head, gate**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Tab title | Аккаунт | Account | `common_account` | `Common_Account` | Аккаунт header, bottom nav, rail | `=` | `A←auth_account P←Nav_Account` |
| Name fallback | Аккаунт | Account | `account_name_fallback` | `Account_NameFallback` | head, when no name is known (the one declared exemption, 3.1.2) | `=` | `A←auth_account P←Nav_Account` |
| Telegram not linked | Telegram не привязан | Telegram is not linked | `account_no_telegram` | `Account_NoTelegram` | head handle slot, sign-in methods, empty-state title | `P` | `A✓ P+` |
| Change the photo | Сменить фото | Change photo | `account_change_photo` | `Account_ChangePhoto` | head photo sheet | `P` (C1) | `A←account_change_avatar P+` |
| Pick a photo (Android) | Выбрать из галереи | Choose from gallery | `account_photo_pick` | - | photo sheet, Android | `=` (R-23) | `A←account_avatar_gallery P-` |
| Pick a photo (PC) | Выбрать файл | Choose a file | - | `Account_PhotoPick` | photo sheet, desktop | `P` (R-23) | `A- P+` |
| Remove the photo | Убрать фото | Remove photo | `account_photo_remove` | `Account_PhotoRemove` | photo sheet | `P` (C1) | `A←account_avatar_remove P+` |
| Photo updated | Фото обновлено | Photo updated | `account_photo_updated` | `Account_PhotoUpdated` | transient | `AP` (C1) | `A+ P+` |
| Photo failed | Не удалось загрузить фото. Выберите другое. | Could not upload the photo. Choose another one. | `account_photo_error` | `Account_PhotoError` | transient with «Повторить» | `AP` (C1) | `A+ P+` |
| Gate title | Вход в departament | Sign in to departament | `auth_gate_title` | `Login_Title` | Аккаунт signed out; the same key as the auth gate | `A` | `A+ P✓` |
| Gate body | Здесь ваша подписка, устройства и платежи. | Your subscription, devices and payments live here. | `auth_gate_body` | `Login_Subtitle` | Аккаунт signed out, auth gate | `AP` | `A+ P✓` |
| Gate: Telegram | Войти через Telegram | Sign in with Telegram | `common_signin_telegram` | `Common_SignInTelegram` | gate, auth surface A | `=` | `A←auth_btn_telegram P✓` |
| Gate: email | Войти по почте | Sign in with email | `common_signin_email` | `Common_SignInEmail` | gate, auth surface A | `AP` | `A+ P+` |

**Notes.** **One noun for the picture** (C1). The first edition ran three through one six-row sheet:
«Сменить **фото**», «Убрать **фото**», «**Аватар** обновлён», «Не удалось загрузить **фото**.
Выберите другое **изображение**.» The confirmation named the thing differently from the three
actions that produce it, and the error named it twice with two words in one sentence - while the
English column said «Photo updated» throughout, which is how you can tell the Russian drifted rather
than the concept. «фото» is now in the C1 table and «аватар» is in the 8.1 grep, keys included.

**The picker is split by platform** (R-23). A desktop has no gallery. The two rows are one concept
with two true sentences, exactly as `servers_longpress_hint` is Android-only; the first edition
marked a single «Выбрать из галереи» row `P+`, which means the desktop adopts it verbatim.

Neither platform completes 9.4's formula on the photo failure today (Android stops at «Не удалось
загрузить фото»). The gate body: PC's `Account_SignInHint` («Через Telegram быстро и без пароля. Или
войдите по почте на сайте.») describes methods the buttons already name, and it says «на сайте»,
which is no longer true - registration happens in the app. The first edition's replacement,
«Здесь **будут** подписка, устройства и платежи.», put a plural verb after a singular head noun and
a future tense where a description belongs; the gate describes what the screen is, not what it will
become.

**3.4.2 Subscription card**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Switcher sheet title | Выберите подписку | Choose a subscription | `account_switcher_sheet_title` | `Account_SwitcherSheetTitle` | switcher sheet | `AP` | `A+ P+` |
| Unnamed subscription | Подписка %1$d | Subscription %1$d | `account_sub_default_name` | `Account_SubscriptionN` | switcher, card title | `AP` | `A+ P✓` |
| Perpetual title | Бессрочная подписка | No expiry | `account_card_perpetual_title` | `Account_PerpetualTitle` | card, perpetual | `AP` | `A+ P+` |
| Perpetual detail | Срок не ограничен | There is no end date | `account_card_perpetual_detail` | `Account_PerpetualDetail` | card, perpetual | `AP` | `A+ P+` |
| Active until | Активна до %1$s | Active until %1$s | `common_sub_active_until` | `Common_SubActiveUntil` | card, Главная ledger | `AP` (R-1) | `A+ P+` |
| Remaining | Осталось %1$s | %1$s left | `account_card_active_detail` | `Account_ActiveDetail` | card, 8-30 days; %1$s is a plural | `AP` | `A+ P+` |
| Expiring today | Истекает сегодня | Expires today | `account_card_today_title` | `Account_TodayTitle` | card, last day | `AP` | `A+ P+` |
| Last day detail | Сегодня последний день | Today is the last day | `account_card_today_detail` | `Account_TodayDetail` | card, last day | `AP` | `A+ P+` |
| Expired title | Подписка истекла | Subscription expired | `common_sub_expired_title` | `Common_SubExpiredTitle` | card, Главная shield | `AP` | `A+ P+` |
| Expired detail | Срок закончился %1$s | It ended on %1$s | `account_card_expired_detail` | `Account_ExpiredDetail` | card, expired | `AP` | `A+ P+` |
| Unknown expiry | Срок неизвестен | End date unknown | `account_card_unknown_title` | `Account_UnknownTitle` | card, no date from the backend | `AP` | `A+ P+` |
| Unknown detail | Обновите данные или проверьте позже | Refresh, or check again later | `account_card_unknown_detail` | `Account_UnknownDetail` | card, with «Повторить» | `AP` | `A+ P+` |
| Health: active | Активна | Active | `account_health_active` | `Account_HealthActive` | card chip | `A` | `A+ P✓` |
| Health: expiring | Истекает | Expiring | `common_chip_expiring` | `Common_ChipExpiring` | card chip, Главная ledger | `A` | `A+ P←Account_HealthExpiring` |
| Health: expired | Истекла | Expired | `common_chip_expired` | `Common_ChipExpired` | card chip, Главная ledger, provider header | `=` | `A←sub_expired† P←Sub_Expired` |
| Trial chip | Пробный | Trial | `account_card_trial_badge` | `Account_TrialBadge` | card chip | `P` (R-6) | `A←account_trial_badge P+` |
| Plan caption | Тариф · %1$s | Plan · %1$s | `account_tariff_caption` | `Account_TariffCaption` | card, under the name | `AP` | `A+ P✓` |
| Traffic label | Трафик | Traffic | `account_card_traffic_label` | `Account_TrafficLabel` | card meter | `AP` | `A+ P+` |
| Traffic value | %1$s из %2$s | %1$s of %2$s | `account_card_traffic_value` | `Account_TrafficValue` | card meter | `AP` | `A+ P+` |
| Traffic exhausted | Трафик исчерпан | No traffic left | `account_card_traffic_over` | `Account_TrafficOver` | card meter at 100 % | `AP` | `A+ P+` |
| Renew | Продлить | Renew | `account_card_renew` | `Account_Renew` | card CTA, strip action | `A` | `A+ P✓` |
| Renew with a price | Продлить · %1$s | Renew · %1$s | `account_card_renew_price` | `Account_RenewPrice` | card CTA when a price is known | `AP` | `A+ P+` |
| Buy | Купить | Buy | `account_card_buy` | `Account_Buy` | card CTA, empty-state action | `=` (R-5) | `A←buy_pay P←Buy_Pay` |
| Upgrade the plan | Улучшить тариф | Upgrade plan | `common_upgrade_plan` | `Common_UpgradePlan` | card overflow, upgrade sheet title | `=` | `A←account_upgrade P←Account_UpgradeTariff` |
| Rename the subscription | Переименовать подписку | Rename subscription | `account_card_rename` | `Account_Rename` | card overflow | `AP` | `A+ P+` |
| Previous / next | Предыдущая · Следующая | Previous · Next | `account_switch_prev`, `account_switch_next` | `Account_PrevSub`, `Account_NextSub` | switcher arrows (a11y) | `AP` | `A+ P✓` |

**Notes.** **The switcher label is deleted.** It read «Подписка» and sat directly above a card inside
a group header also reading «Подписка» - the same defect section 7 item 3 corrects for «Провайдеры»,
on the product's most-visited screen. The group header names the group; the switcher shows the
subscription's own name.

«Активна до»: PC ships four competing expiry formats and Android ships «Действует до %1$s»
(`account_expires`); R-1 settles it. «Осталось %1$s»: PC ships `Account_ExpiresInDays` = «Осталось
{0} дн.» - an abbreviation where 9.2 wants a word, and no plural (section 4). The traffic value: PC
ships `Account_TrafficUnlimited` («{0} · безлимит») and Android ships `sub_traffic_used`
(«%1$s / %2$s»); the unit appears once, at the end, and an unlimited plan renders **no meter at all**
rather than a «безлимит» line (`23-account-rework.md` 4.5). «Обновите **страницу**» became «Обновите
**данные**»: there is no page in a native client, and the control next to the line is «Повторить».

Deleted with the card: `account_switcher_title`, `Account_SwitcherTitle`, `Account_ValidUntil`,
`Account_ExpiresUntil`, `Account_ExpiredOn`, `Account_ExpiresInDays`, `Account_Perpetual`,
`Sub_Until`, `account_trial_badge` («ПРОБНЫЙ», the product's only ALL-CAPS Cyrillic string),
`account_card_buy_tariff`, `account_card_pick_tariff`, `sub_infinity`, `account_unlimited`,
`buy_unlimited`.

**3.4.3 Auto-renew**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Row label | Автопродление | Auto-renew | `account_auto_renew` | `Account_AutoRenew` | card row | `=` | `A✓ P✓` |
| Next charge | Спишем %1$s %2$s | We will charge %1$s on %2$s | `account_auto_renew_next` | `Account_AutoRenewNext` | row subtitle, on | `AP` | `A+ P✓` |
| On | Продлим автоматически | We will renew it for you | `account_auto_renew_on` | `Account_AutoRenewOn` | row subtitle, on, no price known | `AP` | `A+ P✓` |
| Off | Продление вручную | You renew it yourself | `account_auto_renew_off` | `Account_AutoRenewOff` | row subtitle, off | `AP` | `A+ P✓` |
| Risk line | Без автопродления доступ прервётся %1$s | Without auto-renew your access stops on %1$s | `account_auto_renew_risk` | `Account_AutoRenewRisk` | row subtitle, off and expiring | `AP` | `A+ P+` |
| Saving | Сохраняем… | Saving… | `account_auto_renew_saving` | `Account_AutoRenewSaving` | row trailing, in flight | `AP` | `A+ P+` |
| Failed | Не удалось изменить автопродление. Повторите попытку. | Could not change auto-renew. Try again. | `account_auto_renew_failed` | `Account_AutoRenewFailed` | inline under the row, with «Повторить» | `AP` (C9) | `A+ P+` |

PC's current set states the switch's own position back to the user («Автопродление включено» /
«Автопродление выключено»), which is what the switch already shows. The new set states the
**consequence**, which is the only thing a subtitle is for (`12-settings.md` 11.6.1).
`Account_AutoRenew` exists on PC and is never rendered.

**The charge line opens with the verb, not the date.** The first edition wrote «%1$s спишем %2$s»
(«14 августа спишем 1 290 ₽») and annotated it in 5.2 with «order differs from English, where the
amount comes second» - a note describing a difference that does not exist, since the amount is second
in both, and one that would send an implementer hunting a reordering bug. A row subtitle that opens
with a bare date reads as a fragment; «Спишем 1 290 ₽ 14 августа» is the sentence a person says.
The argument order changes with it, and 5.2 records the new order.

**3.4.4 Groups and rows**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Group: subscription | Подписка | Subscription | `common_subscription` | `Common_Subscription` | Аккаунт group header, Главная ledger | `=` | `A←settings_section_subscription P←Settings_SecSubscription` |
| Group: billing | Оплата | Billing | `common_payment` | `Common_Payment` | Аккаунт group header, pay sheet title | `AP` | `A+ P+` |
| Group: sign-in | Вход | Sign-in | `account_group_signin` | `Account_GroupSignIn` | Аккаунт group header | `A` | `A✓ P←Login_SignIn` |
| Devices row | Устройства | Devices | `common_devices` | `Common_Devices` | Аккаунт row, Устройства title, strip action | `=` | `A←devices_title P←Account_Devices` |
| Device pair | %1$d / %2$d | %1$d / %2$d | `account_devices_pair` | `Account_DevicesPair` | devices row value | `AP` | `A+ P+` |
| Unlimited devices | Без ограничений | Unlimited | `account_devices_unlimited` | `Account_DevicesUnlimited` | devices row subtitle | `AP` | `A+ P✓` |
| Devices on this subscription | Устройств на этой подписке | Devices on this subscription | `account_devices_count_hint` | `Account_DevicesCountHint` | devices row subtitle, secondary subscription | `AP` | `A+ P+` |
| Balance row | Баланс | Balance | `account_row_balance` | `Account_Balance` | Аккаунт row | `=` | `A←account_balance P✓` |
| Top up | Пополнить | Top up | `account_top_up` | `Account_TopUp` | balance row action | `=` | `A✓ P✓` |
| Buy a plan | Купить тариф | Buy a plan | `common_buy_plan` | `Common_BuyPlan` | Аккаунт row, Купить title, Главная gate | `AP` (R-5) | `A+ P+` |
| Payment history | История платежей | Payment history | `common_payment_history` | `Common_PaymentHistory` | Аккаунт row, sub-page title | `=` | `A←history_title P✓` |
| Referral code | Реферальный код | Referral code | `account_row_referral` | `Account_ReferralCode` | Аккаунт row | `AP` | `A+ P✓` |
| Copy the code | Скопировать код | Copy code | `account_copy_referral` | `Account_CopyReferralCode` | referral row action | `=` | `A✓ P✓` |
| Copied | Скопировано | Copied | `common_copied` | `Common_Copied` | transient after the copy | `=` | `A←lp_copied P✓` |
| Sign-in methods | Способы входа | Sign-in methods | `account_row_login_methods` | `Account_LinkingTitle` | Аккаунт row, sub-page title | `A` | `A+ P✓` |
| Link Telegram | Привязать Telegram | Link Telegram | `common_link_telegram` | `Common_LinkTelegram` | Аккаунт row, Способы входа, empty-state action | `P` | `A←home_link_telegram P+` |
| Link Telegram subtitle | Управление подпиской из бота | Manage your subscription from the bot | `account_row_link_telegram_sub` | `Account_RowLinkTelegramSub` | that row | `AP` | `A+ P+` |
| Sign out | Выйти | Sign out | `account_row_logout` | `Account_SignOut` | Аккаунт row, destructive | `=` | `A✓ P✓` |

The referral row: Android ships «Реф-код %1$s» and PC ships «Реф-код {0}» - an abbreviation inside a
label, and the code belongs in the row's value slot, not inside its title. The device pair and
«Без ограничений»: PC ships four device-count formats (`Account_DevicesCount`, `Account_DevicesShort`,
`Account_DevicesUsage`, `Account_DevicesTotal`) and Android ships two; one pair format and one
unlimited word survive. `∞` is not used: it is not guaranteed in the vendored font
(`23-account-rework.md` 4.5), which also deletes `sub_infinity`, `account_unlimited` and
`buy_unlimited`. «Слотов на подписке» became «Устройств на этой подписке»: «слот» is jargon under 9.1
and the row above it is called «Устройства». PC has **no** «Привязать Telegram» string at all, only
`Account_LinkAction` = «Привязать», although 9.3 locks the phrase and 0.4.9 makes the CTA an explicit
owner request. Four copy confirmations collapse into `common_copied`: «Ссылка скопирована», «Код
скопирован» and «Скопировано» were three sentences for one event, and the button the user just
pressed already named the object.

**3.4.5 Payment and top-up sheet**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Sheet title | Оплата | Payment | `common_payment` | `Common_Payment` | pay sheet, Аккаунт group header | `AP` | `A+ P+` |
| Total | Итого | Total | `pay_total` | `Buy_Total` | pay sheet, Купить | `=` | `A←buy_total P✓` |
| Approximate | Примерно %1$s | About %1$s | `pay_estimate` | `Pay_Estimate` | pay sheet, add-devices sheet | `AP` | `A+ P+` |
| Estimate note | Точную сумму покажем при оплате | We will show the exact amount at checkout | `pay_estimate_note` | `Pay_EstimateNote` | pay sheet **and** add-devices sheet | `AP` (R-21) | `A+ P+` |
| Pay from the balance | Оплатить с баланса | Pay from balance | `pay_from_balance` | `Pay_FromBalance` | pay sheet | `AP` | `A+ P+` |
| Balance available | На балансе %1$s | %1$s on your balance | `pay_balance_have` | `Pay_BalanceHave` | pay sheet | `AP` | `A+ P+` |
| Balance short | Не хватает %1$s | %1$s short | `pay_balance_short` | `Pay_BalanceShort` | pay sheet | `AP` | `A+ P+` |
| Pay by card | Оплатить картой | Pay by card | `pay_by_card` | `Pay_ByCard` | pay sheet | `A` | `A+ P←Account_RenewWithCard` |
| Pay via SBP | Оплатить через СБП | Pay via SBP | `pay_by_sbp` | `Pay_BySbp` | pay sheet | `AP` | `A+ P+` |
| Pay via other | Оплатить через %1$s | Pay via %1$s | `pay_by_other` | `Pay_ByOther` | pay sheet | `AP` | `A+ P+` |
| Payment method | Способ оплаты | Payment method | `pay_method_title` | `Buy_PaymentMethod` | pay sheet header | `=` | `A✓ P✓` |
| Subject: renewal | Продление %1$s, %2$s | Renewing %1$s, %2$s | `pay_subject_renew` | `Pay_SubjectRenew` | pay sheet subject line | `AP` | `A+ P+` |
| Subject: upgrade | Улучшение до %1$s, +%2$s | Upgrade to %1$s, +%2$s | `pay_subject_upgrade` | `Pay_SubjectUpgrade` | pay sheet subject line | `AP` | `A+ P+` |
| Subject: devices | %1$s к подписке «%2$s» | %1$s for subscription «%2$s» | `pay_subject_devices` | `Pay_SubjectDevices` | pay sheet subject line | `AP` | `A+ P+` |
| Top-up title | Пополнение баланса | Balance top-up | `common_topup_title` | `Common_TopUpTitle` | top-up sheet, pay-sheet subject, history row kind | `A` | `A+ P←Account_TopUpTitle` |
| Amount label | Сумма | Amount | `topup_amount_label` | `TopUp_AmountLabel` | top-up field label (C7) | `AP` | `A+ P+` |
| Amount empty | Введите сумму | Enter an amount | `topup_error_empty` | `TopUp_ErrorEmpty` | field error | `P` | `A←account_top_up_hint P+` |
| Amount zero | Сумма должна быть больше 0 | The amount must be greater than 0 | `topup_error_zero` | `TopUp_ErrorZero` | field error | `AP` | `A+ P+` |
| Amount not a number | Введите сумму цифрами | Enter the amount in digits | `topup_error_format` | `TopUp_ErrorFormat` | field error | `AP` | `A+ P+` |
| Continue | Продолжить | Continue | `pay_continue` | `Account_Continue` | top-up sheet | `A` | `A+ P✓` |
| Upgrade sheet title | Улучшить тариф | Upgrade plan | `common_upgrade_plan` | `Common_UpgradePlan` | upgrade sheet, card overflow | `=` | `A←account_upgrade P←Account_UpgradeTariff` |
| Upgrade note | Доплата зависит от оставшегося срока | The extra you pay depends on the time you have left | `upgrade_sheet_note` | `Upgrade_SheetNote` | upgrade sheet | `AP` | `A+ P+` |
| Upgrade quote | %1$s · +%2$s | %1$s · +%2$s | `upgrade_quote` | `Account_UpgradeQuote` | upgrade option row; %2$s is a plural | `AP` | `A+ P✓` |
| No upgrades left | Улучшать нечего | Nothing to upgrade to | `upgrade_none` | `Account_NoUpgrades` | upgrade sheet, empty | `AP` (C9) | `A+ P✓` |
| Rename title | Название подписки | Subscription name | `rename_title` | `Rename_Title` | rename sheet | `AP` | `A+ P+` |
| Rename label | Название | Name | `common_name` | `Common_Name` | rename field, rule editor field | `P` | `A←subs_ed_name P+` |
| Rename empty | Введите название | Enter a name | `rename_error_empty` | `Rename_ErrorEmpty` | field error | `AP` | `A+ P+` |
| Rename failed | Не удалось переименовать. Повторите попытку. | Could not rename it. Try again. | `rename_failed` | `Rename_Failed` | inline, with «Повторить» | `AP` (C9) | `A+ P+` |
| Checkout in the browser | Завершите оплату в браузере | Finish the payment in your browser | `account_checkout_browser` | `Common_CompletePaymentInBrowser` | transient after opening the payment page | `=` | `A←buy_checkout_return P✓` |
| Checking the payment | Проверяем оплату… | Checking the payment… | `account_pay_checking` | `Account_PayChecking` | Аккаунт strip while polling | `AP` | `A+ P+` |
| Payment done | Оплата прошла | Payment complete | `account_pay_done` | `Account_PayDone` | transient | `AP` | `A+ P+` |
| Payment unconfirmed | Оплата не подтверждена. Проверьте историю платежей. | The payment is not confirmed. Check your payment history. | `account_pay_unconfirmed` | `Account_PayUnconfirmed` | strip with «История» | `AP` | `A+ P+` |
| Open the history | История | History | `account_pay_open_history` | `Account_PayOpenHistory` | that strip's action | `AP` | `A+ P+` |

**One estimate note, not two** (R-21). The first edition shipped «Точную сумму **покажем** при
оплате» on the pay sheet and «Точную сумму **посчитаем** при оплате» on the add-devices sheet - one
concept, one verb apart, and the English differed too («show» vs «calculate»). It is one key on both
surfaces. PC's `Account_DeviceEstimate` («≈ {0}») uses a glyph where a word belongs and
`Account_EstimateNote` («Примерная сумма, точную посчитаем при оплате») restates itself; both go.
Android's top-up strings («Сумма пополнения» / «Введите корректную сумму») go too - «корректную»
tells the user nothing about what is wrong.

«Доплата **рассчитывается за** оставшийся срок» was a calque of «is prorated over»; nothing in
Russian is *calculated over* a period. «Вы на максимальном тарифе» put «Вы» in the subject slot,
which C9 forbids, and the empty-state line under it said the same thing again with «у вас старший
тариф». «Не удалось подтвердить оплату» ran to three lines beside its «История» button at 320 dp;
the state, not the attempt, is what the user needs.

**3.4.6 Devices**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Screen title | Устройства | Devices | `common_devices` | `Common_Devices` | sub-page, Аккаунт row | `=` | `A←devices_title P←Account_Devices` |
| Subtitle | Устройства, привязанные к вашей подписке | The devices linked to your subscription | `devices_subtitle` | `Devices_Subtitle` | sub-page intro | `AP` (C1) | `A✓ P✓` |
| Connect a device | Подключить устройство | Connect a device | `devices_connect_header` | `Devices_ConnectHeader` | section header | `AP` | `A+ P+` |
| Show a QR code | Показать QR-код | Show QR code | `devices_row_qr` | `Devices_RowQr` | row | `P` | `A←subs_action_qrcode P+` |
| Copy the link | Скопировать ссылку | Copy link | `common_copy_link` | `Common_CopyLink` | row | `P` | `A←scheme_copy_cd P+` |
| Copied | Скопировано | Copied | `common_copied` | `Common_Copied` | transient | `=` | `A←lp_copied P✓` |
| QR title | QR-код подписки | Subscription QR code | `devices_qr_title` | `Devices_QrTitle` | QR sheet | `AP` | `A+ P+` |
| QR body | Отсканируйте его в приложении на другом устройстве | Scan it in the app on the other device | `devices_qr_body` | `Devices_QrBody` | QR sheet | `AP` | `A+ P+` |
| Share sheet title | Поделиться QR-кодом подписки | Share the subscription QR code | `devices_qr_chooser` | - | the title `Intent.createChooser` draws above the Android share sheet | `A` | `A+ P-` |
| Add devices | Добавить устройства | Add devices | `devices_add_row` | `Devices_AddRow` | row and sheet title | `P` | `A←account_add_devices P+` |
| Price per device | %1$s за устройство | %1$s per device | `devices_add_per_device` | `Devices_AddPerDevice` | add-devices sheet | `AP` | `A+ P+` |
| Estimate note | Точную сумму покажем при оплате | We will show the exact amount at checkout | `pay_estimate_note` | `Pay_EstimateNote` | add-devices sheet, shared with the pay sheet | `AP` (R-21) | `A+ P+` |
| Go to payment | Перейти к оплате | Go to payment | `devices_add_pay` | `Devices_AddPay` | add-devices sheet | `AP` | `A+ P+` |
| Remove one | Убрать устройство | Remove a device | `devices_add_minus` | `Buy_RemoveDevice` | stepper (a11y) | `=` | `A←buy_extra_devices_minus P✓` |
| Add one | Добавить устройство | Add a device | `devices_add_plus` | `Buy_AddDevice` | stepper (a11y) | `=` | `A←buy_extra_devices_plus P✓` |
| Count line | Привязано %1$d из %2$d | %1$d of %2$d linked | `devices_count_line` | `Devices_CountLine` | list header | `AP` (C1) | `A+ P+` |
| Count, unlimited | Привязано %1$d, без ограничений | %1$d linked, unlimited | `devices_count_line_unlimited` | `Devices_CountLineUnlimited` | list header | `AP` (C1) | `A+ P+` |
| This device | Это устройство | This device | `devices_this_device` | `Devices_ThisDevice` | row chip | `A` | `A+ P✓` |
| Unknown device | Неизвестное устройство | Unknown device | `devices_unknown` | `Devices_Unknown` | row title fallback | `=` | `A←devices_unknown_model P✓` |
| Platform and last seen | %1$s · был активен %2$s | %1$s · last active %2$s | `devices_platform_active` | `Devices_PlatformActive` | row subtitle (R-18) | `AP` | `A+ P✓` |
| Unlink | Отвязать устройство | Unlink device | `devices_unlink` | `Devices_Unlink` | row action | `A` (R-3) | `A+ P✓` |
| Unlink, short | Отвязать | Unlink | `common_unlink` | `Common_Unlink` | confirm button | `A` (R-3) | `A+ P←Devices_UnlinkShort` |
| Unlink confirm title | Отвязать устройство? | Unlink this device? | `devices_unlink_title` | `Devices_UnlinkConfirm` | confirm dialog | `A` (R-3) | `A+ P✓` |
| Unlink confirm body | Устройство «%1$s» будет отвязано от подписки. | The device «%1$s» will be unlinked from your subscription. | `devices_unlink_body` | `Devices_UnlinkBody` | confirm dialog | `AP` (R-3) | `A+ P✓` |
| Unlinked | Устройство отвязано | Device unlinked | `devices_unlinked` | `Devices_Unlinked` | transient with «Отменить» | `A` (R-3) | `A+ P✓` |
| Unlink failed | Не удалось отвязать устройство. Повторите попытку. | Could not unlink the device. Try again. | `devices_unlink_failed` | `Devices_UnlinkFailed` | inline, with «Повторить» | `AP` (C9) | `A+ P✓` |

**The screen counts with the account's verb, not the tunnel's** (C1). «Подключено 3 из 5» renders
the product's single most prominent status word - the one the shield, the notification, the tile and
the tray all use for «the VPN is up» - as a count of devices, directly above a row button reading
«Отвязать устройство». The register spends a whole rule (C1, R-3, W-7) moving Android from «удалить»
to «отвязать» and then left the surrounding sentences saying «подключено». A device is
**привязано**; the tunnel is **подключено**; the two never trade words. «Устройства, подключённые к
вашей подписке» goes the same way. `devices_link_copied` collapses into `common_copied`.

**The device subtitle uses a relative time, and says so.** «%1$s · **активно** %2$s» renders «Windows
· активно 2 часа назад», which parses as «actively two hours ago». The subject is the device, the
tense is past, and the adjective agrees with it.

R-3 covers the six unlink strings. Android's set says «удалить», and its confirm body says
«отключено», which collides with the connection vocabulary. Deleted: `devices_hwid` / `Devices_Id`
(«ID: %1$s», R-18), `devices_delete_cd` (a screen-reader label that repeated the dialog title instead
of naming the button), `devices_limit` (folded into `strip_devices`, R-21), and the whole
`devices_diag_*` family - `devices_diag_http` («HTTP: %1$d»), `devices_diag_title` («Ответ сервера
(диагностика)»), `devices_diag_failed` and `devices_diag_empty`, both of which print the raw server
response and ask the user for a screenshot (C4).

**3.4.7 Payment history**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Screen title | История платежей | Payment history | `common_payment_history` | `Common_PaymentHistory` | sub-page, Аккаунт row | `=` | `A←history_title P✓` |
| Paid | Оплачено | Paid | `history_status_paid` | `History_StatusPaid` | row status chip | `=` | `A←account_status_paid P✓` |
| Processing | В обработке | Processing | `history_status_pending` | `History_StatusProcessing` | row status chip | `=` | `A←account_status_pending P✓` |
| Failed | Ошибка | Failed | `history_status_failed` | `History_StatusFailed` | row status chip | `=` | `A←account_status_failed P✓` |
| Canceled | Отменён | Canceled | `history_status_canceled` | `History_StatusCanceled` | row status chip | `=` | `A←account_status_canceled P✓` |
| Unknown | Не определён | Unknown | `history_status_unknown` | `History_StatusUnknown` | row status chip | `AP` | `A+ P+` |
| Kind: renewal | Продление | Renewal | `history_kind_renew` | `History_KindRenew` | row title | `AP` | `A+ P+` |
| Kind: purchase | Покупка тарифа | Plan purchase | `history_kind_purchase` | `History_KindPurchase` | row title | `AP` (R-5) | `A+ P+` |
| Kind: top-up | Пополнение баланса | Balance top-up | `common_topup_title` | `Common_TopUpTitle` | row title, top-up sheet | `A` | `A+ P←Account_TopUpTitle` |
| Kind: devices | Дополнительные устройства | Additional devices | `history_kind_devices` | `History_KindDevices` | row title | `=` | `A←buy_extra_devices_title P←Buy_AdditionalDevices` |
| Kind: upgrade | Улучшение тарифа | Plan upgrade | `history_kind_upgrade` | `History_KindUpgrade` | row title | `AP` | `A+ P+` |
| Kind: other | Платёж | Payment | `history_kind_other` | `History_KindOther` | row title | `AP` | `A+ P+` |

«Покупка **подписки**» sat in the same list as «Улучшение **тарифа**» and «Дополнительные
устройства» - one list, two objects for the thing being bought. R-5 settles it: you buy a тариф, and
the history says so.

**3.4.8 Sign-in methods**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Screen title | Способы входа | Sign-in methods | `account_row_login_methods` | `Account_LinkingTitle` | sub-page, Аккаунт row | `A` | `A+ P✓` |
| Telegram | Telegram | Telegram | `linking_telegram` | `Linking_Telegram` | row title (brand, not translated) | `P` | `A←sub_telegram P+` |
| Email | Почта | Email | `linking_email` | `Linking_Email` | row title | `AP` | `A+ P+` |
| Google | Google | Google | `linking_google` | `Linking_Google` | row title (brand) | `AP` | `A+ P+` |
| Linked | Привязан | Linked | `linking_linked` | `Account_Linked` | row value, masculine | `A` | `A+ P✓` |
| Not linked, m. | Не привязан | Not linked | `linking_not_linked_m` | `Linking_NotLinkedM` | Telegram, Google rows | `AP` | `A+ P+` |
| Not linked, f. | Не привязана | Not linked | `linking_not_linked_f` | `Linking_NotLinkedF` | Почта row | `AP` | `A+ P+` |
| Link | Привязать | Link | `linking_action` | `Account_LinkAction` | row action | `A` | `A+ P✓` |
| Unlink a method | Отвязать | Unlink | `common_unlink` | `Common_Unlink` | row action, linked state, destructive | `A` | `A+ P←Devices_UnlinkShort` |
| Unlink confirm title | Отвязать %1$s? | Unlink %1$s? | `linking_unlink_title` | `Linking_UnlinkTitle` | confirm dialog | `AP` | `A+ P+` |
| Unlink confirm body | Этот способ входа перестанет работать. Остальные останутся. | This sign-in method stops working. The others stay. | `linking_unlink_body` | `Linking_UnlinkBody` | confirm dialog | `AP` | `A+ P+` |
| Unlinked | Способ входа отвязан | Sign-in method unlinked | `linking_unlinked` | `Linking_Unlinked` | transient | `AP` | `A+ P+` |
| Last method | Это единственный способ входа. Сначала привяжите другой. | This is your only sign-in method. Link another one first. | `linking_last_method` | `Linking_LastMethod` | inline under the row, with «Привязать» | `AP` | `A+ P+` |
| Change the password | Сменить пароль | Change password | `linking_change_password` | `Linking_ChangePassword` | Почта row action, linked state | `AP` | `A+ P+` |
| Current password | Текущий пароль | Current password | `linking_password_current` | `Linking_PasswordCurrent` | password sheet field | `AP` | `A+ P+` |
| New password | Новый пароль | New password | `linking_password_new` | `Linking_PasswordNew` | password sheet field | `AP` | `A+ P+` |
| Password changed | Пароль изменён | Password changed | `linking_password_done` | `Linking_PasswordDone` | transient | `AP` | `A+ P+` |
| Password wrong | Текущий пароль неверный. Проверьте его и повторите попытку. | That current password is wrong. Check it and try again. | `linking_password_wrong` | `Linking_PasswordWrong` | field error | `AP` | `A+ P+` |
| Linking via the site | Привязка через сайт | Linking happens on the website | `linking_via_site` | `Linking_ViaSite` | Google row subtitle | `AP` | `A+ P+` |
| Telegram sheet title | Привязка Telegram | Link Telegram | `linking_tg_sheet_title` | `Linking_TgSheetTitle` | sheet | `AP` | `A+ P+` |
| Telegram sheet body | Откройте бота и подтвердите привязку. | Open the bot and confirm the link. | `linking_tg_sheet_body` | `Linking_TgSheetBody` | sheet | `AP` | `A+ P+` |
| Open the bot | Открыть бота | Open the bot | `linking_tg_open_bot` | `Account_OpenBot` | sheet | `A` | `A+ P✓` |
| Link code | Код: %1$s | Code: %1$s | `linking_tg_code` | `Account_TgLinkCode` | sheet | `AP` | `A+ P✓` |
| Waiting | Ждём подтверждения… | Waiting for confirmation… | `linking_tg_waiting` | `Account_TgLinkWaiting` | sheet (R-13) | `AP` (R-13) | `A+ P✓` |
| Telegram linked | Telegram привязан | Telegram linked | `linking_tg_done` | `Linking_TgDone` | transient, shared with auth surface E | `AP` | `A+ P+` |
| Email sheet title | Привязка почты | Link an email | `linking_email_sheet_title` | `Account_EmailLinkTitle` | sheet | `AP` | `A+ P✓` |
| Email sheet body | Отправим ссылку для подтверждения на этот адрес. | We will email a confirmation link to this address. | `linking_email_hint` | `Account_EmailLinkHint` | sheet | `AP` (C9) | `A+ P✓` |
| Send | Отправить | Send | `linking_email_send` | `Account_Send` | sheet | `=` | `A←tv_send_button P✓` |
| Email sent | Письмо отправлено. Проверьте почту. | Email sent. Check your inbox. | `linking_email_sent` | `Account_EmailSent` | transient | `AP` | `A+ P✓` |

**A screen that only ever adds is not a management screen.** The first edition gave Telegram, Email
and Google one action each - «Привязать» - and nothing that removes a method or changes a password,
so a user who linked the wrong Telegram account, or who wants a new password, had no route at all.
The unlink action, its confirm, the last-method guard and the password change are the missing half.
The last-method guard is not a courtesy: unlinking the only method locks the account out, and C3
requires the recovery control («Привязать») beside the sentence that says so.

«**Пришлём** ссылку для подтверждения» was the third shape this register used for one promise, after
«**Отправим** ссылку для входа» and «Мы **отправили** ссылку». C9 fixes one: «Отправим…» before it
is sent, «Мы отправили…» after. «Привязан»: the string exists on PC and is never rendered - the rows
signal "linked" with a check glyph alone, which `00-rules.md` 14.7 forbids (colour or icon is never
the only signal). PC's «Письмо отправлено на {0}» repeats an address the user just typed; the
shorter form leaves room for the instruction.

### 3.5 Sign-in

Canonical spec: `14-auth.md` 11. The desktop's sign-in surface is being rebuilt against the same
state machine (PG-A12), so most PC rows change: the strings exist but under keys that described a
different layout.

**Surface A - the gate**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Gate title | Вход в departament | Sign in to departament | `auth_gate_title` | `Login_Title` | gate, Аккаунт signed out | `A` | `A+ P✓` |
| Gate body | Здесь ваша подписка, устройства и платежи. | Your subscription, devices and payments live here. | `auth_gate_body` | `Login_Subtitle` | gate, Аккаунт signed out | `AP` | `A+ P✓` |
| Sign in with Telegram | Войти через Telegram | Sign in with Telegram | `common_signin_telegram` | `Common_SignInTelegram` | gate primary (Android), demoted (PC, DV-1) | `=` | `A←auth_btn_telegram P✓` |
| Sign in with email | Войти по почте | Sign in with email | `common_signin_email` | `Common_SignInEmail` | gate | `AP` | `A+ P+` |
| Waiting title | Ждём подтверждения в Telegram | Waiting for Telegram | `auth_awaiting_title` | `Login_WaitingConfirm` | gate, awaiting | `AP` (R-13) | `A+ P✓` |
| Waiting body | Подтвердите вход в Telegram | Confirm the sign-in in Telegram | `auth_awaiting_body` | `Login_TelegramConfirmHint` | gate, awaiting | `AP` | `A+ P✓` |
| Open Telegram | Открыть Telegram | Open Telegram | `auth_open_telegram` | `Login_OpenTelegram` | gate, awaiting | `A` | `A+ P✓` |
| Start over | Начать заново | Start over | `auth_restart` | `Login_StartOver` | gate, awaiting; also the recovery for an expired link | `=` | `A✓ P✓` |
| Another way to sign in | Другой способ входа | Another way to sign in | `common_other_signin` | `Common_OtherSignIn` | gate action, method sheet title | `A` | `A+ P←Login_ChooseAnother` |

The waiting pair: PG-A5 and PG-A6 in `14-auth.md` 18.3 - «Ожидаем» becomes «Ждём», and
`Login_TelegramConfirmHint` carries a dash construction («…и вернитесь сюда. Остальное сделаем
сами.») that 9.2 bans and that promises work the app does not narrate. The first edition replaced it
with «Подтвердите вход в **открывшемся приложении**», which nobody says out loud and which withholds
the name of the app while the title one line above it says «Telegram». The body names it.

**Surface B - the email form**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Sign-in title | Вход по почте | Sign in with email | `auth_email_title` | `Login_TabSignIn` | form, sign-in mode | `AP` | `A+ P✓` |
| Register title | Регистрация | Register | `auth_register_title` | `Login_TabRegister` | form, register mode | `A` | `A+ P✓` |
| Method: password | Пароль | Password | `common_password` | `Common_Password` | method slot, field label | `=` | `A←lp_socks_password P←Login_Password` |
| Method: link | Вход по ссылке | Sign in with a link | `auth_method_link` | `Login_MagicLink` | method slot | `AP` | `A+ P✓` |
| Email label | Электронная почта | Email | `auth_email_label` | `Login_Email` | field label (C7) | `=` | `A←auth_email_hint P✓` |
| Email placeholder | name@example.com | name@example.com | `auth_email_placeholder` | `Login_EmailPlaceholder` | field watermark | `AP` | `A+ P+` |
| Password rule | Не менее 8 символов | At least 8 characters | `auth_password_hint_register` | `Login_PasswordHint` | helper under the field, register | `AP` | `A+ P✓` |
| Repeat password | Повторите пароль | Repeat password | `auth_password_repeat_label` | `Login_ConfirmPassword` | field label | `A` | `A+ P✓` |
| Show password | Показать пароль | Show password | `auth_show_password` | `Login_ShowPassword` | field trailing (a11y) | `=` | `A←lp_show_password P✓` |
| Hide password | Скрыть пароль | Hide password | `auth_hide_password` | `Login_HidePassword` | field trailing (a11y) | `=` | `A←lp_hide_password P✓` |
| Link method hint | Отправим ссылку для входа на этот адрес. Откройте её на этом устройстве. | We will email you a sign-in link. Open it on this device. | `auth_link_hint` | `Login_MagicHint` | helper, link method | `AP` (R-23) | `A+ P+` |
| Submit: sign in | Войти | Sign in | `common_signin` | `Common_SignIn` | form CTA, Главная gate | `A` | `A+ P←Login_SubmitSignIn` |
| Submit: send link | Отправить ссылку | Send the link | `auth_btn_send_link` | `Login_SendLink` | form CTA, link method | `AP` | `A+ P+` |
| Submit: register | Создать аккаунт | Create an account | `common_create_account` | `Common_CreateAccount` | form CTA, register mode; also the mode switch | `A` | `A+ P←Login_CreateAccount` |
| Sign in with a code | Войти по коду | Sign in with a code | `auth_btn_by_code` | `Login_ByCode` | form, demoted action | `A` | `A+ P✓` |
| Forgot password | Забыли пароль? | Forgot your password? | `auth_forgot` | `Login_ForgotPassword` | form, demoted action | `A` | `A+ P✓` |
| I already have one | У меня уже есть аккаунт | I already have an account | `auth_have_account` | `Login_HaveAccount` | mode switch, register | `AP` | `A+ P+` |
| Or | или | or | `auth_or` | `Login_Or` | divider | `A` | `A+ P✓` |

«**Ссылка на почту**» was the first edition's name for this sign-in method, and it reads «a link *to*
the mail». The method is signing in **by** a link; «Вход по ссылке» matches «Вход по почте» and
«Войти по коду», which are the two names beside it. «Откройте её на этом **телефоне**» went to the
desktop verbatim under a `P+` code (R-23).

**2FA**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Code label | Код из аутентификатора | Authenticator code | `auth_2fa_label` | `Login_EnterCode` | 2FA step (C7) | `AP` | `A+ P✓` |
| Code (a11y) | Код из аутентификатора, 6 цифр | Authenticator code, 6 digits | `auth_2fa_a11y` | `Login_EnterCodeA11y` | OTP component | `AP` | `A+ P+` |
| Confirm | Подтвердить | Confirm | `auth_btn_2fa` | `Login_Confirm` | 2FA CTA | `=` | `A✓ P✓` |
| Wrong length | Код состоит из 6 цифр | The code is 6 digits | `auth_2fa_invalid` | `Login_CodeIs6` | field error | `=` | `A←auth_code_invalid P✓` |
| Wrong code | Неверный код. Проверьте аутентификатор. | Wrong code. Check your authenticator. | `auth_2fa_wrong` | `Login_CodeWrong` | field error | `AP` | `A+ P+` |

«Код из **приложения-аутентификатора**» is 32 characters of which 26 are one near-unbreakable token,
sitting as the label above an OTP box on a 320 dp screen at font scale 200 %. The label names the
source; the word «приложение» adds nothing a user of an authenticator does not know.

**The «отправлено» block**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Link sent title | Ссылка отправлена | Link sent | `auth_sent_magic_title` | `Login_MagicSentTitle` | sent block | `A` | `A+ P✓` |
| Link sent body | Мы отправили ссылку на %1$s. Откройте её на этом устройстве. | We have sent a link to %1$s. Open it on this device. | `auth_sent_magic_body` | `Login_MagicSentHint` | sent block | `AP` (R-23) | `A+ P✓` |
| Verify title | Подтвердите почту | Confirm your email | `auth_sent_verify_title` | `Login_VerifyTitle` | sent block | `A` | `A+ P✓` |
| Verify body | Мы отправили ссылку на %1$s. Откройте её, чтобы завершить регистрацию. | We have sent a link to %1$s. Open it to finish registering. | `auth_sent_verify_body` | `Login_VerifyHint` | sent block | `AP` | `A+ P✓` |
| Reset title | Письмо отправлено | Email sent | `auth_sent_reset_title` | `Login_ResetSentTitle` | sent block | `A` | `A+ P✓` |
| Reset body | Если аккаунт с %1$s существует, мы отправили ссылку для сброса пароля. Задайте новый пароль и вернитесь ко входу. | If an account for %1$s exists, we have sent a password-reset link. Set a new password, then come back to sign in. | `auth_sent_reset_body` | `Login_ResetSentHint` | sent block | `AP` | `A+ P✓` |
| Send again | Отправить снова | Send again | `auth_resend` | `Login_Resend` | sent block | `A` | `A+ P✓` |
| Back to sign-in | Вернуться ко входу | Back to sign in | `auth_back_to_signin` | `Login_BackToSignIn` | sent block | `A` | `A+ P✓` |

PG-A7: all three PC bodies carry a dash construction that 9.2 bans.

**Surface C - the method sheet, and the website hand-off**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Sheet title | Другой способ входа | Another way to sign in | `common_other_signin` | `Common_OtherSignIn` | method sheet, gate action | `A` | `A+ P←Login_ChooseAnother` |
| Via the website | Через сайт | On the website | `auth_method_site` | `Login_ViaSite` | method sheet | `AP` | `A+ P+` |
| Via the website, sub | Откроем departament.site в браузере | We will open departament.site in your browser | `auth_method_site_sub` | `Login_ViaSiteSub` | method sheet | `AP` | `A+ P+` |
| I have a code | У меня есть код | I have a code | `auth_method_code` | `Login_ByCode` | method sheet | `AP` | `A+ P✓` |
| I have a code, sub | Вставьте код, который показал сайт | Paste the code the website showed you | `auth_method_code_sub` | `Login_CodePaste` | method sheet, field helper | `AP` | `A+ P✓` |
| Via Google | Через Google | With Google | `auth_method_google` | `Login_ContinueGoogle` | method sheet, only when configured | `AP` | `A+ P✓` |
| Via Google, sub | Войдите аккаунтом Google | Sign in with your Google account | `auth_method_google_sub` | `Login_GoogleSub` | method sheet | `AP` | `A+ P+` |
| Hand-off field label | Код из браузера | Code from the browser | `auth_handoff_label` | `Login_HandoffLabel` | hand-off field (C7) | `AP` | `A+ P+` |
| Finishing via the site | Завершаем вход через сайт… | Finishing sign-in on the website… | `auth_site_handoff` | `Login_SiteHandoff` | hand-off progress | `A` | `A+ P✓` |

**Surface D - the hand-off into the app, and surface E - linking**

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Sync title | Добавляем аккаунт | Adding your account | `auth_sync_title` | `Account_SyncTitle` | post-sign-in hand-off | `A` | `A+ P✓` |
| Stage: account | Проверяем аккаунт | Checking your account | `auth_sync_stage_account` | `Account_SyncStageAccount` | hand-off | `A` | `A+ P✓` |
| Stage: subscription | Загружаем подписку | Loading your subscription | `auth_sync_stage_subscription` | `Account_SyncSubtitle` | hand-off | `AP` | `A+ P✓` |
| Stage: servers | Загружаем серверы | Loading servers | `auth_sync_stage_servers` | `Account_SyncStageServers` | hand-off | `AP` | `A+ P✓` |
| Sync failed title | Не удалось синхронизировать | Sync did not finish | `auth_sync_error_title` | `Account_SyncErrorTitle` | hand-off error | `A` | `A+ P✓` |
| Sync failed hint | Проверьте подключение и повторите попытку. | Check your connection and try again. | `auth_sync_error_hint` | `Account_SyncErrorHint` | hand-off error | `AP` (C9) | `A+ P✓` |
| Retry | Повторить | Try again | `common_retry` | `Common_Retry` | hand-off error | `=` | `A←buy_retry P✓` |
| Sign in again | Войти заново | Sign in again | `auth_sync_relogin` | `Account_SyncReLogin` | hand-off error | `A` | `A+ P✓` |
| Link Telegram title | Привязать Telegram | Link Telegram | `common_link_telegram` | `Common_LinkTelegram` | surface E, shared with the account row | `P` | `A←home_link_telegram P+` |
| Telegram linked | Telegram привязан | Telegram linked | `linking_tg_done` | `Linking_TgDone` | transient, shared with 3.4.8 | `AP` | `A+ P+` |

The four `Добавляем / Проверяем / Загружаем` progress lines are the one place C9 licenses the first
person plural without reservation: the app is doing the work, the user is waiting, and a passive
(«Аккаунт добавляется») would be worse.

**Deleted from the auth surface** (`14-auth.md` 11.7): `auth_tg_headline`, `auth_tg_desc`,
`auth_site_headline`, `auth_site_desc`, `auth_btn_site` («Войти через сайт» - the button posted email
and password, so the label lied), `auth_register_site`, `auth_awaiting`, `auth_fields_required` (the
CTA is disabled until the form is valid, so it can never fire), `auth_err_dialog_title`, `auth_title`,
`auth_sign_in_telegram` and `auth_sign_in_site` (duplicates of `auth_btn_*`), plus `auth_btn_signin`,
`auth_btn_telegram`, `auth_btn_email`, `auth_btn_register`, `auth_create_account`, `auth_other_method`,
`auth_methods_title`, `auth_method_password`, `auth_password_label`, `auth_link_tg_title` and
`auth_link_tg_done`, each of which is a per-screen duplicate of a key 3.1 or 3.4 now declares once.
On PC: `Login_SignIn`/`Login_TabSignIn` collapse to one key, `Login_SignUp` («Регистрация на сайте»)
and `Login_ComingSoon` («Скоро») are deleted - PG-A4 says a Google button that cannot be used is not
shown at all.

### 3.6 Settings

Canonical spec: `12-settings.md` 11, with three corrections this register makes (R-2, R-4, and the
`Provider_Title` collision). In this sub-section the **screen is the sub-heading**, so the tables
carry five columns instead of seven. Every row is `N` unless marked otherwise: the settings tree is
being rebuilt from a hub of 23 rows, and most keys are new on both platforms even where a similar
string exists today.

Locale-neutral tokens are never keyed and never translated: `VPN`, `TUN`, `DNS`, `IPv6`, `FakeIP`,
`Mux`, `SOCKS5`, `HTTP`, `TCP`, `MTU`, `User-Agent`, `depv://`, `geoip.dat`, `geosite.dat`,
`Cloudflare`, `Google`, `AdGuard`, `sing-box`, `Xray`, and the language endonyms `Русский` /
`English`.

**3.6.1 Hub - «Настройки»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Настройки | Settings | `common_settings` | `Common_Settings` | `=` | `A←title_settings† P←Nav_Settings` |
| Поиск по настройкам | Search settings | `set_search_hint` | `Settings_SearchHint` | `AP` | `A+ P+` |
| Подключение | Connection | `set_sec_connection` | `Settings_SecConnection` | `=` | `A←settings_section_connection P✓` |
| Режим подключения | Connection mode | `set_mode` | `Settings_Mode` | `AP` | `A+ P✓` |
| VPN | VPN | `set_mode_vpn` | `Settings_ModeVpn` | `P` (R-12) | `A←settings_mode_vpn P+` |
| Прокси | Proxy | `set_mode_proxy` | `Settings_ModeProxy` | `=` | `A←settings_mode_proxy_opt P✓` |
| VPN и прокси | VPN and proxy | `set_mode_both` | `Settings_ModeBoth` | `AP` | `A+ P+` |
| Прокси доступен другим устройствам в сети | The proxy is reachable by other devices on the network | `set_mode_both_hint` | `Settings_ModeBothHint` | `AP` | `A+ P+` |
| Прокси по приложениям | Per-app proxy | `set_perapp_title` | `PerApp_Title` | `=` | `A←pa_title P←Settings_PerApp` |
| Кроме %1$s | All except %1$s | `set_perapp_except` | `Settings_PerAppExcept` | `AP` | `A+ P✓` |
| Только %1$s | Only %1$s | `set_perapp_only` | `Settings_PerAppOnly` | `AP` | `A+ P✓` |
| Не выбрано | Nothing selected | `set_perapp_none` | `Settings_PerAppNone` | `AP` | `A+ P+` |
| Маршрутизация | Routing | `set_routing_title` | `Settings_Routing` | `=` | `A←routing_title P✓` |
| Обход локальной сети | Bypass the local network | `set_bypass_lan` | `Settings_BypassLan` | `=` | `A←settings_bypass_lan P✓` |
| Прямой доступ к устройствам сети | Direct access to devices on your network | `set_bypass_lan_hint` | `Settings_BypassLanHint` | `AP` (9.2) | `A+ P✓` |
| Включить IPv6 в туннеле | Enable IPv6 in the tunnel | `set_ipv6_hint` | `Settings_Ipv6Hint` | `AP` | `A+ P✓` |
| Дополнительно | Advanced | `common_advanced` | `Common_Advanced` | `=` | `A←subs_ed_section_advanced P←Dns_Advanced` |
| Обход блокировок | Getting past blocks | `set_sec_bypass` | `Settings_SecBypass` | `=` | `A←settings_section_bypass P✓` |
| Мультиплексирование (Mux) | Multiplexing (Mux) | `set_mux` | `Settings_Mux` | `=` | `A←settings_mux P✓` |
| Объединяет запросы в один канал | Combines requests into one channel | `set_mux_hint` | `Settings_MuxHint` | `A` | `A+ P✓` |
| Число подключений Mux | Mux connection count | `set_mux_count` | `Settings_MuxCount` | `A` (R-2) | `A+ P✓` |
| Фрагментация пакетов | Packet fragmentation | `set_fragment` | `Settings_Fragment` | `=` | `A←settings_fragment P✓` |
| Разбивает TLS-рукопожатие против DPI | Splits the TLS handshake to defeat DPI | `set_fragment_hint` | `Settings_FragmentHint` | `=` | `A←settings_fragment_sub P✓` |
| Параметры фрагментации | Fragmentation options | `set_fragment_title` | `Fragment_Title` | `P` | `A←adv_title_fragment P+` |
| Провайдеры | Providers | `set_sec_providers` | `Settings_SecProviders` | `AP` (R-4) | `A+ P+` |
| Автообновление провайдеров | Auto-update providers | `set_sub_auto_update` | `Settings_SubAutoUpdate` | `A` (R-4) | `A+ P✓` |
| Выключено | Off | `common_off` | `Common_Off` | `P` | `A←routing_rule_off P✓` |
| Каждый час | Every hour | `set_sub_auto_1h` | `Settings_SubAuto1h` | `AP` | `A+ P+` |
| Каждые 6 часов | Every 6 hours | `set_sub_auto_6h` | `Settings_SubAuto6h` | `AP` | `A+ P+` |
| Каждые 12 часов | Every 12 hours | `set_sub_auto_12h` | `Settings_SubAuto12h` | `AP` | `A+ P+` |
| Раз в сутки | Once a day | `set_sub_auto_24h` | `Settings_SubAuto24h` | `AP` | `A+ P+` |
| Нет провайдеров | No providers | `set_sub_auto_empty` | `Settings_SubAutoEmpty` | `AP` (R-4) | `A+ P+` |
| Добавьте провайдера, чтобы включить | Add a provider to turn this on | `set_sub_auto_empty_hint` | `Settings_SubAutoEmptyHint` | `AP` | `A+ P+` |
| Обновлять при запуске | Update at launch | `set_sub_update_launch` | `Settings_SubUpdateLaunch` | `AP` | `A+ P+` |
| Проверка задержки | Latency testing | `set_latency_title` | `Ping_Title` | `P` | `A←adv_title_latency P+` |
| Настройки провайдеров | Provider settings | `set_providers_title` | `Provider_Title` | `=` | `A←ps_title P✓` |
| Файлы ресурсов | Resource files | `set_assets_title` | `Settings_GeoFiles` | `=` | `A←asset_title P✓` |
| Приложение | App | `set_sec_app` | `Settings_SecApp` | `P` | `A←about_section_app P+` |
| Оформление | Appearance | `set_appearance` | `Settings_Appearance` | `=` | `A←settings_appearance P✓` |
| Тёмная | Dark | `set_theme_dark` | `Settings_ThemeDark` | `=` | `A←settings_appearance_dark P✓` |
| Светлая | Light | `set_theme_light` | `Settings_ThemeLight` | `=` | `A←settings_appearance_light P✓` |
| Системная | System | `set_theme_system` | `Settings_ThemeSystem` | `AP` | `A+ P+` |
| Чёрная тема | Black theme | `set_black_theme` | `Settings_BlackTheme` | `AP` | `A+ P+` |
| Чистый чёрный фон без цветного акцента | A pure black background with no colour accent | `set_black_theme_hint` | `Settings_BlackThemeHint` | `AP` | `A+ P+` |
| Язык | Language | `set_language` | `Settings_Language` | `=` | `A←title_language† P✓` |
| Системный | System | `common_system` | `Common_System` | `P` | `A←settings_language_system P+` |
| Меньше движения | Less motion | `set_reduced_motion` | `Settings_ReducedMotion` | `AP` | `A+ P+` |
| Отключает анимации | Turns animations off | `set_reduced_motion_hint` | `Settings_ReducedMotionHint` | `AP` | `A+ P+` |
| Запуск при старте | Launch at startup | `set_boot` | `Settings_Autostart` | `AP` | `A+ P✓` |
| Открывать Departament при входе в систему | Open Departament when you sign in | `set_boot_hint` | `Settings_AutostartHint` | `A` (C8) | `A+ P✓` |
| Окно и горячие клавиши | Window and shortcuts | - | `Window_Title` | `P` | `A- P+` |
| Данные и резервные копии | Data and backups | `set_data_title` | `Settings_Data` | `P` | `A←backup_title P+` |
| О приложении | About | `set_about_title` | `Settings_About` | `=` | `A←about_title P✓` |
| Версия %1$s | Version %1$s | `set_about_version` | `About_VersionValue` | `AP` | `A+ P✓` |

**Notes.** «Режим подключения»: Android's row is «Режим VPN» and PC's is «Режим»; neither says what
is being chosen. **«Вместе» named no mode** - it is the value of a row called «Режим подключения»
whose other two values are «VPN» and «Прокси», so the third has to name the third mode, not the fact
that there are two of them. The Mux pair: R-2, plus PC's shorter hint, which does not restate the
title. «Провайдеры» as the group header: R-4 replaces `12-settings.md`'s «Подписки», and the row
inside the group keeps the name both platforms already ship, «Настройки провайдеров» (Android
`ps_title`, PC `Provider_Title`) - so a group and a row inside it no longer carry the same word,
which is `40-copy-inventory-android.md` F7.1's most common duplicate shape. «Запуск при старте»:
Android's current helper («Подключаться после перезагрузки устройства») promises a connection the
boot receiver does not make. «Меньше движения»: Android has no key at all and PC calls it
«Облегчённый режим», which names a mode rather than an effect. **The per-app value slot takes a
plural** (section 4, `plural_apps`): «Кроме %1$d» printed a bare integer with no noun to agree with,
so at n=1 the row read «Кроме 1». It reads «Кроме 12 приложений» now. **Eleven hub rows now carry
the sub-page's own key** rather than a second key with the same word - a hub row that opens a screen
and that screen's title are one string, and C2 gives one string one key.

**Every hub row's helper is at most six words** (9.2). «Прямой доступ к устройствам в локальной
сети» was seven and «Адрес действует, пока устройство в этой сети» (3.6.10) was seven; both are
rewritten below the cap.

**3.6.2 `settings/perapp` - «Прокси по приложениям»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Прокси по приложениям | Per-app proxy | `set_perapp_title` | `PerApp_Title` | `=` | `A←pa_title P←Settings_PerApp` |
| Режим | Mode | `set_perapp_sec_mode` | `PerApp_SecMode` | `=` | `A←perapp_mode_row P←Settings_Mode` |
| Раздельное туннелирование | Split tunneling | `set_perapp_split` | `PerApp_SplitTunnel` | `A` | `A+ P✓` |
| Выберите, какие приложения идут через VPN | Choose which apps go through the VPN | `set_perapp_split_hint` | `PerApp_SplitTunnelHint` | `AP` | `A+ P✓` |
| Правило | Rule | `set_perapp_rule` | `PerApp_Rule` | `P` | `A←routing_ed_title_edit P+` |
| Кроме выбранных | All except the selected | `set_perapp_rule_except` | `PerApp_RuleExcept` | `P` | `A←perapp_mode_except P+` |
| Только выбранные | Only the selected | `set_perapp_rule_only` | `PerApp_RuleOnly` | `P` | `A←perapp_mode_selected P+` |
| Выбранные идут напрямую, мимо VPN | Selected apps go direct, around the VPN | `set_perapp_hint_except` | `PerApp_BypassHint` | `AP` | `A+ P✓` |
| Через VPN идут только выбранные | Only the selected apps go through the VPN | `set_perapp_hint_only` | `PerApp_OnlyHint` | `AP` | `A+ P✓` |
| Приложения | Apps | `set_perapp_sec_apps` | `PerApp_Apps` | `=` | `A←routing_ed_process P✓` |
| Поиск по приложениям | Search apps | `set_perapp_search` | `PerApp_Search` | `P` (R-9) | `A←perapp_search_hint P+` |
| Добавить программу | Add a program | - | `PerApp_AddExe` | `P` | `A- P✓` |
| Выбрать все | Select all | `common_select_all` | `Common_SelectAll` | `P` | `A←menu_item_select_all† P+` |
| Снять выделение | Clear selection | `common_clear_selection` | `Common_ClearSelection` | `AP` | `A+ P+` |
| Инвертировать | Invert | `set_perapp_invert` | `PerApp_Invert` | `P` | `A←perapp_action_invert P+` |
| Импорт списка | Import the list | `set_perapp_import` | `PerApp_Import` | `AP` | `A+ P+` |
| Экспорт списка | Export the list | `set_perapp_export` | `PerApp_Export` | `AP` | `A+ P+` |
| Изменения применятся при следующем подключении | Changes apply on your next connection | `set_perapp_apply_hint` | `PerApp_TunHint` | `AP` | `A+ P✓` |
| Включите раздельное туннелирование | Turn on split tunneling | `set_perapp_disabled_hint` | `PerApp_DisabledHint` | `AP` | `A+ P+` |
| Неизвестное приложение | Unknown app | `app_picker_unknown_app` | `PerApp_UnknownApp` | `AP` | `A✓ P+` |

`A` on the last row: Android ships «Неизвестное приложение (неопознанный UID)» - a UID is a machine
fact (C4). `AP` on the apply hint: PC's version names the mechanism («Работает в режиме TUN
(sing-box)»), which a consumer cannot act on. `P` on «Добавить программу»: `PerApp_AddExe` reads
«Добавить .exe», a file extension where a noun belongs.

**3.6.3 `settings/routing` - «Маршрутизация»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Маршрутизация | Routing | `set_routing_title` | `Settings_Routing` | `=` | `A←routing_title P✓` |
| Наборы правил решают, какой трафик идёт через VPN, а какой напрямую. | Rule sets decide which traffic goes through the VPN and which goes direct. | `set_routing_intro` | `Routing_Intro` | `AP` | `A+ P✓` |
| Наборы правил | Rule sets | `set_routing_sets` | `Routing_RuleSets` | `A` | `A+ P✓` |
| %1$d правил | %1$d rules | `set_routing_rules_n` | `Routing_RulesCount` | `AP` | `A+ P✓` |
| Активен | Active | `set_routing_active` | `Routing_Active` | `A` | `A+ P✓` |
| Добавить набор | Add a set | `set_routing_add` | `Routing_Add` | `AP` | `A+ P+` |
| Импортировать набор | Import a set | `set_routing_import` | `Routing_Import` | `AP` | `A+ P+` |
| Из буфера обмена | From the clipboard | `set_routing_import_clip` | `Routing_ImportClipboard` | `AP` | `A+ P+` |
| Из QR-кода | From a QR code | `common_from_qr` | `Common_FromQr` | `AP` | `A+ P+` |
| Стандартные наборы | The built-in sets | `set_routing_import_preset` | `Routing_ImportPreset` | `AP` | `A+ P+` |
| Домены | Domains | `common_domains` | `Common_Domains` | `P` | `A←routing_ed_domain P+` |
| Стратегия доменов | Domain strategy | `set_routing_strategy` | `Routing_DomainStrategy` | `=` | `A←routing_domain_strategy P✓` |
| Как есть | As is | `set_routing_ds_asis` | `Routing_DsAsIs` | `A` | `A+ P✓` |
| IP при несовпадении | IP if no match | `set_routing_ds_ipif` | `Routing_DsIpIfNonMatch` | `A` | `A+ P✓` |
| IP по запросу | IP on demand | `set_routing_ds_ipdemand` | `Routing_DsIpOnDemand` | `A` | `A+ P✓` |
| Разрешение доменов | Domain resolution | `set_routing_resolve` | `Routing_DomainResolution` | `A` | `A+ P✓` |
| Как ядро сопоставляет домены с правилами | How the core matches domains against rules | `set_routing_resolve_hint` | `Routing_DomainHint` | `A` | `A+ P✓` |
| Обслуживание | Maintenance | `set_routing_sec_maint` | `Routing_Maintenance` | `A` | `A+ P✓` |
| Восстановить стандартные наборы | Restore the built-in sets | `set_routing_restore` | `Routing_RestoreDefaults` | `AP` | `A+ P+` |
| Стандартные наборы восстановлены | The built-in sets are back | `set_routing_restored` | `Routing_Restored` | `AP` | `A+ P+` |
| Сбросить правила | Reset the rules | `set_routing_reset` | `Routing_Reset` | `AP` | `A+ P✓` |
| Удалит все наборы, включая созданные вами | This deletes every set, including the ones you made | `set_routing_reset_hint` | `Routing_ResetHint` | `AP` (9.1) | `A+ P+` |
| Название | Name | `common_name` | `Common_Name` | `P` | `A←subs_ed_name P+` |
| Действие | Action | `set_rule_action` | `Rule_Action` | `AP` | `A+ P+` |
| Через VPN | Through the VPN | `set_rule_action_proxy` | `Rule_ActionProxy` | `AP` | `A+ P+` |
| Напрямую | Direct | `set_rule_action_direct` | `Rule_ActionDirect` | `AP` | `A+ P+` |
| Заблокировать | Block | `set_rule_action_block` | `Rule_ActionBlock` | `AP` | `A+ P+` |
| Домены | Domains | `common_domains` | `Common_Domains` | `P` | `A←routing_ed_domain P+` |
| Один домен в строке. Поддерживается префикс geosite: | One domain per line. The geosite: prefix is supported | `set_rule_domains_hint` | `Rule_DomainsHint` | `AP` (9.1) | `A+ P+` |
| IP-адреса | IP addresses | `set_rule_ips` | `Rule_Ips` | `P` | `A←routing_ed_ip P+` |
| Один адрес или диапазон в строке. Поддерживается префикс geoip: | One address or range per line. The geoip: prefix is supported | `set_rule_ips_hint` | `Rule_IpsHint` | `AP` (9.1) | `A+ P+` |
| Порты | Ports | `set_rule_ports` | `Rule_Ports` | `P` | `A←routing_ed_port P+` |
| Например: 80, 443, 1000-2000 | For example: 80, 443, 1000-2000 | `set_rule_ports_hint` | `Rule_PortsHint` | `AP` | `A+ P+` |
| Протоколы | Protocols | `set_rule_protocols` | `Rule_Protocols` | `P` | `A←routing_ed_protocol P+` |
| Программы | Programs | - | `Rule_Processes` | `P` (C2) | `A- P+` |
| По одному названию в строке | One name per line | - | `Rule_ProcessesHint` | `P` | `A- P+` |

«%1$d правил» is an ungrammatical count at n=1 and n=2 on both platforms; section 4 makes it a
plural set. Android's `routing_settings_process` («Процесс (название пакета; поддерживается только
при использовании Xray TUN…)») is deleted - a 20-word parenthesis in a field helper, and the only
remaining em-dash carrier in the non-Russian locale files.

Three sentences here were ungrammatical or dangling in the first edition. «Удалит все наборы,
включая **свои**» has no antecedent for «свои»; the dialog body four rows away says it correctly,
«включая созданные вами», so the row says it that way too. «**Поддерживаются** geosite:» is a plural
verb agreeing with one token, followed by a colon that makes the reader wait for a list that never
arrives; the subject is the **префикс**, singular, and the colon belongs to the prefix. The desktop's
rule field was labelled «Приложения» inside a screen whose per-app section is also «Приложения»; a
rule matches **программы** by name, which is also the noun `PerApp_AddExe` uses one screen away.

**3.6.4 `settings/dns` - «DNS»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| DNS | DNS | `set_dns` | `Settings_Dns` | `P` | `A←settings_dns P+` |
| DNS-сервер, через который приложение разрешает домены. По умолчанию используется встроенный резолвер. | The DNS server the app resolves domains through. The built-in resolver is used by default. | `set_dns_intro` | `Dns_Intro` | `AP` | `A+ P✓` |
| Провайдер | Provider | `set_dns_provider` | `Dns_Provider` | `A` | `A+ P✓` |
| По умолчанию | Default | `common_default` | `Common_Default` | `=` | `A←ps_sort_default P✓` |
| Свой | Custom | `common_custom` | `Common_Custom` | `A` | `A+ P✓` |
| Адрес DNS-сервера | DNS server address | `set_dns_custom_label` | `Dns_CustomAddress` | `P` | `A←settings_dns_hint P✓` |
| DoH-адрес (https://…/dns-query), DoT или обычный IP | A DoH address (https://…/dns-query), DoT, or a plain IP | `set_dns_custom_hint` | `Dns_CustomHint` | `AP` | `A+ P✓` |
| Дополнительно | Advanced | `common_advanced` | `Common_Advanced` | `=` | `A←subs_ed_section_advanced P←Dns_Advanced` |
| Ускоряет подключение, отвечая на запросы локально | Speeds up connecting by answering queries locally | `set_dns_fakeip_hint` | `Dns_FakeIpHint` | `AP` (R-2) | `A+ P+` |
| Локальный резолвер | Local resolver | `set_dns_local` | `Dns_LocalResolver` | `P` | `A←adv_local_dns_title P+` |
| Разрешать домены внутри приложения | Resolve domains inside the app | `set_dns_local_hint` | `Dns_LocalResolverHint` | `P` | `A←adv_local_dns_summary P+` |
| DNS для прямых подключений | DNS for direct connections | `set_dns_direct` | `Dns_Direct` | `AP` (R-2) | `A+ P+` |
| Системный | System | `common_system` | `Common_System` | `P` | `A←settings_language_system P+` |
| Свои записи | Custom entries | `set_dns_sec_hosts` | `Dns_SecHosts` | `AP` | `A+ P+` |
| Записи hosts | hosts entries | `set_dns_hosts` | `Dns_Hosts` | `P` | `A←adv_dns_hosts_title P+` |
| Одна запись в строке: домен и адрес | One entry per line: domain and address | `set_dns_hosts_hint` | `Dns_HostsHint` | `P` | `A←adv_dns_hosts_helper P+` |

**3.6.5 `settings/fragment` - «Параметры фрагментации»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Параметры фрагментации | Fragmentation options | `set_fragment_title` | `Fragment_Title` | `P` | `A←adv_title_fragment P+` |
| Длина | Length | `set_fragment_length` | `Fragment_Length` | `P` | `A←adv_fragment_length_title P+` |
| Диапазон в байтах, например 50-100 | A range in bytes, for example 50-100 | `set_fragment_length_hint` | `Fragment_LengthHint` | `P` | `A←adv_fragment_length_helper P+` |
| Интервал | Interval | `set_fragment_interval` | `Fragment_Interval` | `P` | `A←adv_fragment_interval_title P+` |
| Пауза между частями, мс | The pause between parts, in ms | `set_fragment_interval_hint` | `Fragment_IntervalHint` | `P` | `A←adv_fragment_interval_helper P+` |
| Пакеты | Packets | `set_fragment_packets` | `Fragment_Packets` | `P` | `A←adv_fragment_packets_title P+` |
| Значения по умолчанию подходят большинству сетей. Меняйте их, только если подключение не устанавливается. | The defaults suit most networks. Change them only if the connection does not establish. | `set_fragment_note` | `Fragment_Note` | `AP` (R-2) | `A+ P+` |

**3.6.6 `settings/latency` - «Проверка задержки»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Проверка задержки | Latency testing | `set_latency_title` | `Ping_Title` | `P` | `A←adv_title_latency P+` |
| Как измерять задержку серверов. | How server latency is measured. | `set_latency_intro` | `Ping_Intro` | `AP` | `A+ P✓` |
| Метод | Method | `set_latency_sec_method` | `Ping_SecMethod` | `AP` | `A+ P+` |
| Реальная задержка | Real latency | `set_latency_real` | `Ping_RealTitle` | `A` | `A+ P✓` |
| Через ядро, как при подключении | Through the core, the way a real connection goes | `set_latency_real_hint` | `Ping_RealHint` | `A` | `A+ P✓` |
| TCP-подключение | TCP connection | `set_latency_tcp` | `Ping_TcpTitle` | `AP` (R-2) | `A+ P+` |
| Быстрее, но менее точно | Faster, less accurate | `set_latency_tcp_hint` | `Ping_TcpHint` | `AP` | `A+ P✓` |
| Проверка | The test | `set_latency_sec_test` | `Ping_SecTest` | `AP` | `A+ P+` |
| Адрес проверки | Test address | `set_latency_url` | `Ping_TestAddress` | `P` | `A←adv_delay_url_title P✓` |
| Тайм-аут | Timeout | `set_latency_timeout` | `Ping_Timeout` | `AP` | `A+ P✓` |
| Одновременных проверок | Parallel tests | `set_latency_concurrency` | `Ping_Concurrency` | `P` | `A←adv_ping_concurrency_title P+` |
| Автоматически | Automatically | `set_latency_sec_auto` | `Ping_SecAuto` | `AP` | `A+ P+` |
| Проверять при запуске | Test at launch | `set_latency_on_launch` | `Ping_OnLaunch` | `AP` | `A+ P+` |
| Проверять после обновления провайдера | Test after a provider update | `set_latency_on_update` | `Ping_OnUpdate` | `AP` (R-4) | `A+ P+` |
| Сортировать по задержке после проверки | Sort by latency after testing | `set_latency_sort` | `Ping_SortAfter` | `P` | `A←adv_auto_sort_title P+` |
| Удалять нерабочие после проверки | Delete the dead ones after testing | `set_latency_remove` | `Ping_RemoveAfter` | `P` | `A←adv_auto_remove_title P+` |
| Серверы без ответа будут удалены | Servers that do not answer will be deleted | `set_latency_remove_hint` | `Ping_RemoveAfterHint` | `P` | `A←adv_auto_remove_summary P+` |

`A` on the two «Реальная задержка» rows: Android puts the explanation in a parenthesis inside the
title («Реальная задержка (через туннель)»); PC's split into title and helper is the archetype.
The two removed methods (`HTTP GET /generate_204`, `ICMP ping`) take their strings with them,
including three English-only ones.

**3.6.7 `settings/providers` - «Настройки провайдеров»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Настройки провайдеров | Provider settings | `set_providers_title` | `Provider_Title` | `=` | `A←ps_title P✓` |
| Обновление | Updates | `set_providers_sec_update` | `Provider_SecUpdates` | `=` | `A←ps_section_update P✓` |
| Уведомлять об обновлении | Notify me after an update | `set_providers_notify` | `Provider_Notify` | `AP` | `A+ P+` |
| Сеть | Network | `set_providers_sec_net` | `Provider_SecNetwork` | `=` | `A←ps_section_network P✓` |
| Отправлять идентификатор устройства | Send the device identifier | `set_providers_hwid` | `Provider_Hwid` | `P` | `A←ps_send_hwid P✓` |
| Нужен, чтобы считать устройства в тарифе | This is how devices are counted against your plan | `set_providers_hwid_hint` | `Provider_HwidHint` | `AP` | `A+ P+` |
| User-Agent | User-Agent | `set_providers_ua` | `Provider_UserAgent` | `P` | `A←ps_user_agent P+` |
| Отправляется при обновлении провайдера | Sent when a provider is refreshed | `set_providers_ua_hint` | `Provider_UserAgentHint` | `AP` (R-4) | `A+ P✓` |
| Значение по умолчанию | The default value | `set_providers_ua_default` | `Provider_UserAgentDefault` | `AP` | `A+ P+` |
| В заголовке нельзя передать кириллицу. Оставьте латиницу, цифры и знаки. | The header cannot carry Cyrillic. Use Latin letters, digits and punctuation. | `set_providers_ua_error` | `Provider_UserAgentError` | `AP` | `A+ P+` |
| Список серверов | The server list | `set_providers_sec_list` | `Provider_SecList` | `P` | `A←ps_section_server_list P+` |
| Порядок серверов | Server order | `set_providers_sort` | `Provider_Sort` | `AP` | `A+ P+` |
| Как у провайдера | Provider order | `common_sort_provider` | `Common_SortProvider` | `AP` | `A+ P+` |
| По задержке | By latency | `common_sort_ping` | `Common_SortPing` | `AP` | `A+ P+` |
| По названию | By name | `common_sort_name` | `Common_SortName` | `AP` (C2) | `A+ P+` |

`P` on «Отправлять идентификатор устройства»: PC's `Provider_Hwid` reads «Идентификатор устройства
(HWID)», and 9.3 bans HWID as a user-facing word. `A+` on the User-Agent validation error: the
sentence is hardcoded twice in Kotlin (`SubEditActivity.kt:36-37`, `ProviderSettingsActivity.kt:55-56`).
The desktop keeps one row Android does not have - a read-only device identifier with a copy action -
which is logged parity gap PG-S4.

**3.6.8 `settings/assets` - «Файлы ресурсов»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Файлы ресурсов | Resource files | `set_assets_title` | `Settings_GeoFiles` | `=` | `A←asset_title P✓` |
| Базы geoip и geosite нужны для маршрутизации по странам и доменам. | The geoip and geosite databases are what routing by country and domain needs. | `set_assets_intro` | `Geo_Intro` | `AP` | `A+ P✓` |
| Базы | Databases | `set_assets_sec_bases` | `Geo_SecBases` | `AP` | `A+ P+` |
| Обновить сейчас | Update now | `set_assets_update` | `Geo_UpdateNow` | `A` | `A+ P✓` |
| Обновление… | Updating… | `set_assets_updating` | `Geo_Updating` | `A` | `A+ P✓` |
| Не загружен | Not downloaded | `set_assets_not_loaded` | `Geo_NotDownloaded` | `A` | `A+ P✓` |
| %1$s МБ · обновлена %2$s | %1$s MB · updated %2$s | `set_assets_meta` | `Geo_SizeUpdated` | `AP` (9.1) | `A+ P✓` |
| Источник обновлений | Update source | `set_assets_source` | `Geo_Source` | `AP` | `A+ P+` |
| Свои файлы | Your own files | `set_assets_sec_custom` | `Geo_SecCustom` | `AP` | `A+ P+` |
| Добавить файл | Add a file | `set_assets_add` | `Geo_Add` | `AP` | `A+ P+` |
| Из файла | From a file | `set_assets_add_file` | `Geo_AddFile` | `AP` | `A+ P+` |
| По ссылке | From a link | `set_assets_add_url` | `Geo_AddUrl` | `AP` | `A+ P+` |
| Из QR-кода | From a QR code | `common_from_qr` | `Common_FromQr` | `AP` | `A+ P+` |
| Ссылка | Link | `set_assets_url_label` | `Geo_UrlLabel` | `P` | `A←asset_url_label P+` |
| Базы обновлены | The databases are up to date | `set_assets_done` | `Geo_Done` | `A` | `A+ P✓` |

«Источник обновлений»: Android's `asset_geo_files_sources` reads «Источник геофайлов
(необязательно)» and PC's `TbSettingsGeoFilesSource` uses «источник» for a provider, which 9.3 bans;
here it genuinely is an update source, and the label says so without the parenthesis. «Базы
обновлены»: PC's value ends in a full stop and a trailing space, the space existing only so that a
.NET exception message can be glued to it (`GeoFilesPage.axaml.cs:89`). Both go.

The row meta agrees with its section. «%1$s МБ · **обновлён** %2$s» is masculine, sitting under a
section headed «Базы» whose done-state is «Базы обновлены» - feminine plural. The subject of that
participle is the **база**, so it is «обновлена».

**3.6.9 `settings/advanced` - «Дополнительно»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Дополнительно | Advanced | `common_advanced` | `Common_Advanced` | `=` | `A←subs_ed_section_advanced P←Dns_Advanced` |
| Ядро и журнал | Core and log | `set_adv_sec_core` | `Adv_SecCore` | `AP` (C2) | `A+ P+` |
| Активное ядро | Active core | `set_adv_core` | `Adv_Core` | `AP` (C2) | `A+ P+` |
| Уровень журнала | Log level | `set_adv_loglevel` | `Adv_LogLevel` | `P` | `A←adv_loglevel_title P+` |
| Никакой | None | `set_adv_loglevel_none` | `Adv_LogNone` | `AP` | `A+ P+` |
| Ошибки | Errors | `common_errors` | `Common_Errors` | `AP` | `A+ P+` |
| Предупреждения | Warnings | `set_adv_loglevel_warn` | `Adv_LogWarn` | `AP` | `A+ P+` |
| Информация | Info | `set_adv_loglevel_info` | `Adv_LogInfo` | `AP` | `A+ P+` |
| Отладка | Debug | `set_adv_loglevel_debug` | `Adv_LogDebug` | `AP` | `A+ P+` |
| Отладочный журнал заметно нагружает устройство | Debug logging puts a noticeable load on the device | `set_adv_loglevel_debug_note` | `Adv_LogDebugNote` | `P` | `A←adv_loglevel_debug_hint P+` |
| Определение домена в трафике | Domain sniffing | `set_adv_sniffing` | `Adv_Sniffing` | `P` | `A←adv_sniffing_title P+` |
| Помогает правилам маршрутизации | It helps the routing rules match | `set_adv_sniffing_hint` | `Adv_SniffingHint` | `P` | `A←adv_sniffing_summary P+` |
| Только для маршрутизации | For routing only | `set_adv_route_only` | `Adv_RouteOnly` | `P` | `A←adv_route_only_title P+` |
| Не подменять адрес назначения | Do not rewrite the destination address | `set_adv_route_only_hint` | `Adv_RouteOnlyHint` | `P` | `A←adv_route_only_summary P+` |
| Разрешать небезопасные подключения | Allow insecure connections | `set_adv_insecure` | `Adv_AllowInsecure` | `AP` (R-2) | `A+ P+` |
| Отключает проверку сертификата сервера | It turns off the server certificate check | `set_adv_insecure_hint` | `Adv_AllowInsecureHint` | `P` | `A←adv_allow_insecure_summary P+` |
| Переключать сервер при сбое | Switch servers on failure | `set_adv_fallback` | `Adv_AutoFallback` | `P` | `A←adv_auto_fallback_title P+` |
| Если сервер не отвечает после подключения | When a server stops answering after you connect | `set_adv_fallback_hint` | `Adv_AutoFallbackHint` | `P` | `A←adv_auto_fallback_summary P+` |
| Туннель | Tunnel | `set_adv_sec_tunnel` | `Adv_SecTunnel` | `P` | `A←adv_group_tunnel P+` |
| От 576 до 9000. По умолчанию 1500 | From 576 to 9000. The default is 1500 | `set_adv_mtu_hint` | `Adv_MtuHint` | `P` | `A←adv_mtu_helper P+` |
| Адрес интерфейса | Interface address | `set_adv_iface` | `Adv_InterfaceAddress` | `P` | `A←adv_iface_title P+` |
| Локальный прокси | Local proxy | `set_lp_title` | `Lp_Title` | `=` | `A←lp_section_local P←Settings_LocalProxy` |
| Постоянный VPN | Always-on VPN | `set_adv_always_on` | - | `A` | `A+ P-` |
| Настраивается в системных настройках Android | You set this up in the Android system settings | `set_adv_always_on_hint` | - | `A` | `A+ P-` |
| Конфигурация | Configuration | - | `Adv_SecConfig` | `P` | `A- P+` |
| Шаблон конфигурации | Config template | - | `Adv_Template` | `P` | `A- P+` |
| Свой JSON поверх сгенерированного | Your own JSON on top of the generated one | - | `Adv_TemplateHint` | `P` | `A- P+` |
| Сбросить к стандартному | Reset to the standard one | - | `Adv_TemplateReset` | `P` | `A- P+` |
| Переподключаемся, чтобы применить настройку | Reconnecting to apply the change | `set_reconnecting` | `Settings_Reconnecting` | `P` | `A←adv_notice_reconnecting P+` |

«Постоянный VPN»: today the row lives in the hub's Подключение group and fires a `Toast` before
launching the system intent; the toast is deleted (1.4.8) and the helper carries the same fact
permanently.

**A section header and a row inside it never carry the same word** - section 7 item 3 states it for
«Провайдеры», and the first edition then shipped «Ядро» as both the header of this section and the
row inside it. The header names what the section covers («Ядро и журнал»: the core picker plus the
three log rows), and the row names the thing it sets («Активное ядро»).

**3.6.10 `settings/advanced/localproxy` - «Локальный прокси»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Локальный прокси | Local proxy | `set_lp_title` | `Lp_Title` | `=` | `A←lp_section_local P←Settings_LocalProxy` |
| SOCKS5 и HTTP на 127.0.0.1 | SOCKS5 and HTTP on 127.0.0.1 | `set_lp_enabled_hint` | `Lp_EnabledHint` | `AP` | `A+ P+` |
| Порт | Port | `set_lp_port` | `Settings_Port` | `=` | `A←lp_socks_port P✓` |
| UDP через прокси | UDP through the proxy | `set_lp_udp` | `Lp_Udp` | `AP` | `A+ P+` |
| HTTP-прокси на соседнем порту | An HTTP proxy on the next port | `set_lp_http` | `Lp_Http` | `AP` | `A+ P+` |
| Доступ из локальной сети | Reachable from the local network | `set_lp_lan` | `Lp_Lan` | `AP` | `A+ P+` |
| Другие устройства смогут пользоваться прокси | Other devices will be able to use the proxy | `set_lp_lan_hint` | `Lp_LanHint` | `AP` | `A+ P+` |
| SOCKS5-авторизация | SOCKS5 authentication | `set_lp_sec_auth` | `Settings_Socks5Auth` | `=` | `A←lp_socks_auth P✓` |
| Логин | Username | `set_lp_user` | `Settings_Username` | `P` | `A←webdav_user_label P✓` |
| Пароль | Password | `common_password` | `Common_Password` | `=` | `A←lp_socks_password P←Login_Password` |
| Создать новые логин и пароль | Generate a new username and password | `set_lp_regen` | `Lp_Regenerate` | `AP` | `A+ P+` |
| Адрес подключения | Connection address | `set_lp_sec_endpoint` | `Lp_SecEndpoint` | `AP` | `A+ P+` |
| Действует, пока устройство в этой сети | Works while this device stays on the network | `set_lp_endpoint_hint` | `Lp_EndpointHint` | `AP` (9.2) | `A+ P+` |
| Устройство не подключено к локальной сети | This device is not on a local network | `set_lp_no_lan` | `Lp_NoLan` | `AP` | `A+ P+` |

`AP` on «Логин»: PC says «Имя пользователя» and Android says «Логин»; the field is 4 characters wide
in the SOCKS5 dialogue of every other client, and «Логин» fits a 320 dp row at font scale 200 %.
The memory-limit block (`lp_memory_*`) is deleted with the screen: a memory ceiling is not a setting
a consumer sets.

**3.6.11 `settings/data` - «Данные и резервные копии»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Данные и резервные копии | Data and backups | `set_data_title` | `Settings_Data` | `P` | `A←backup_title P+` |
| Все настройки, провайдеры и серверы сохраняются в один файл. | Every setting, provider and server is saved into a single file. | `set_data_intro` | `Backup_Intro` | `AP` (9.3) | `A+ P✓` |
| Резервная копия | Backup | `set_data_sec_backup` | `Backup_SecBackup` | `AP` | `A+ P+` |
| Создать копию | Create a backup | `set_data_create` | `Backup_Create` | `AP` | `A+ P+` |
| Восстановить из копии | Restore from a backup | `set_data_restore` | `Backup_Restore` | `AP` | `A+ P✓` |
| Приложение перезапустится | The app will restart | `set_data_restore_hint` | `Backup_RestoreHint` | `AP` | `A+ P+` |
| Поделиться копией | Share the backup | `set_data_share` | - | `=` | `A←backup_action_share P-` |
| Облачная копия | Cloud backup | `set_webdav_title` | `Webdav_Title` | `P` | `A←backup_grp_cloud P+` |
| Устройства | Devices | `common_devices` | - | `=` | `A←devices_title P-` |
| Перенести подписку на ТВ | Send the subscription to a TV | `set_data_tv` | - | `A` | `A+ P-` |
| Сброс | Reset | `set_data_sec_reset` | `Backup_SecReset` | `AP` | `A+ P+` |
| Сбросить настройки | Reset the settings | `set_data_reset` | `Backup_Reset` | `AP` | `A+ P+` |
| Серверы и провайдеры останутся | Your servers and providers stay | `set_data_reset_hint` | `Backup_ResetHint` | `AP` (9.3) | `A+ P+` |
| Сохранение… | Saving… | `set_data_saving` | `Backup_Saving` | `A` | `A+ P✓` |
| Копия сохранена: %1$s | Backup saved: %1$s | `set_data_saved` | `Backup_Saved` | `AP` | `A+ P✓` |
| Восстановление… Приложение перезапустится. | Restoring… The app will restart. | `set_data_restoring` | `Backup_Restoring` | `A` | `A+ P✓` |
| Адрес сервера | Server address | `set_webdav_url` | `Webdav_Url` | `P` | `A←webdav_url_label P+` |
| Папка | Folder | `set_webdav_folder` | `Webdav_Folder` | `AP` | `A+ P+` |
| Создаётся автоматически, если её нет | Created automatically if it does not exist | `set_webdav_folder_hint` | `Webdav_FolderHint` | `AP` | `A+ P+` |
| Проверить подключение | Test the connection | `set_webdav_test` | `Webdav_Test` | `P` | `A←connection_test_pending† P+` |
| Выгрузить копию | Upload the backup | `set_webdav_upload` | `Webdav_Upload` | `AP` | `A+ P+` |
| Загрузить копию | Download the backup | `set_webdav_download` | `Webdav_Download` | `AP` | `A+ P+` |

The three backup verbs: Android ships «Резервирование конфигурации» / «Восстановление
конфигурации» (9.3 - a config is a **сервер**, and both are noun phrases where a row wants a verb),
PC ships «Экспорт» / «Импорт» with «Сохранить…» / «Восстановить…» buttons under them, so one page
has four words for two actions. The WebDAV block exists only on Android today and is English
(`title_webdav_url`, `title_webdav_pass`), so it is rewritten rather than translated; the row that
opens it and the screen it opens are one key.

**This screen broke the провайдер lock twice, and its own English column proved it.** «Все настройки,
**подписки** и серверы сохраняются в один файл» sat beside «Every setting, **provider** and
server…», and «Серверы и **подписки** останутся» beside «Your servers and **providers** stay» - the
author knew which noun was right and wrote it only in the language nobody here ships. Four rows down,
3.10's reset dialog gets it right («Серверы, провайдеры и аккаунт не пострадают»), so one screen
carried both nouns for one object. 8.1 now greps for «подписк» inside any `set_data_*` or backup
string.

**3.6.12 `settings/window` - «Окно и горячие клавиши» (PC only)**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| Окно и горячие клавиши | Window and shortcuts | - | `Window_Title` | `P` | `A- P+` |
| Окно | Window | - | `Window_SecWindow` | `P` | `A- P+` |
| Сворачивать в трей при закрытии окна | Minimise to the tray when the window is closed | - | `Window_HideToTray` | `P` (R-24) | `A- P+` |
| Запускать свёрнутым | Start minimised | - | `Window_StartMinimized` | `P` | `A- P+` |
| Показывать в Dock | Show in the Dock | - | `Window_ShowInDock` | `P` | `A- P+` |
| Горячие клавиши | Shortcuts | - | `Window_SecHotkeys` | `P` | `A- P+` |
| Показать окно | Show the window | - | `Tray_Show` | `P` | `A- P✓` |
| Подключить или отключить | Connect or disconnect | - | `Window_HkToggle` | `P` | `A- P+` |
| Сменить сервер | Change the server | - | `Window_HkSwitch` | `P` | `A- P+` |
| Обновить провайдеров | Refresh providers | - | `Common_RefreshProviders` | `P` (R-4) | `A- P+` |
| Не назначено | Not assigned | - | `Window_HkUnset` | `P` | `A- P+` |
| Нажмите сочетание… | Press a shortcut… | - | `Window_HkCapture` | `P` | `A- P+` |
| Уже назначено: %1$s | Already assigned to: %1$s | - | `Window_HkConflict` | `P` | `A- P+` |
| Сбросить сочетания | Reset the shortcuts | - | `Window_HkReset` | `P` | `A- P+` |
| Масштаб интерфейса | Interface scale | - | `Settings_UiScale` | `P` | `A- P+` |
| Ctrl + и Ctrl - меняют масштаб, Ctrl 0 сбрасывает | Ctrl + and Ctrl - change the scale, Ctrl 0 resets it | - | `Settings_UiScaleHint` | `P` | `A- P+` |
| %1$d% | %1$d% | - | `Settings_UiScaleValue` | `P` | `A- P+` |

The scale row and its tooltip are hardcoded Russian in `SettingsView.axaml:776,792` - the only rows
in Настройки that are not `{loc:T}`, so switching the app to English leaves them in Russian. The
tooltip also carries two em-dashes and a U+2212 minus sign. **The interface-scale row is desktop
only**: Android has no such setting, and the first edition's 5.2 nevertheless listed an Android key
`set_ui_scale_value` for it, on the one row it calls «the classic aapt2 crash». That Android half is
deleted; the `%` escaping rule it was invoked to illustrate is stated in 5.1 for the whole file.

«Сворачивать в трей **при закрытии**» now says «при закрытии **окна**». R-24 moves the tray's quit
item to «Завершить работу», which removes the collision, and naming the object removes the last of
the ambiguity: one setting is about closing a window, one menu item is about ending the process.

**3.6.13 `settings/about` - «О приложении»**

| Русский | English | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|
| О приложении | About | `set_about_title` | `Settings_About` | `=` | `A←about_title P✓` |
| Версия | Version | `set_about_version_label` | `About_Version` | `=` | `A←about_version P✓` |
| Поддержка | Support | `common_support` | `Common_Support` | `=` | `A←sub_support† P←Sub_Support` |
| Сайт departament.site | The departament.site website | `set_about_site` | `About_Site` | `AP` | `A+ P+` |
| Telegram-бот | The Telegram bot | `set_about_bot` | `About_TelegramBot` | `A` | `A+ P✓` |
| Проверить обновления | Check for updates | `set_about_check_update` | `About_CheckUpdate` | `P` | `A←about_check_update P+` |
| Проверяем обновления… | Checking for updates… | `set_about_update_checking` | `About_UpdateChecking` | `P` | `A←upd_checking P+` |
| Доступна версия %1$s | Version %1$s is available | `set_about_update_found` | `About_UpdateFound` | `P` | `A←upd_found_title P+` |
| У вас последняя версия | You are on the latest version | `set_about_update_none` | `About_UpdateNone` | `AP` | `A+ P+` |
| Не удалось проверить обновления. Проверьте подключение и повторите попытку. | Could not check for updates. Check your connection and try again. | `set_about_update_failed` | `About_UpdateFailed` | `AP` | `A+ P+` |
| Для разработчика | For developers | `set_about_sec_dev` | `About_SecDev` | `AP` | `A+ P+` |
| Схемы URL-адресов | URL schemes | `set_about_schemes` | `Settings_UrlSchemes` | `=` | `A←about_url_schemes P✓` |
| Быстрые команды depv:// | Quick depv:// commands | `set_schemes_sub` | `Settings_UrlSchemesHint` | `=` | `A←settings_url_scheme_sub P✓` |
| Журнал | Log | `set_about_log` | `About_Log` | `P` | `A←about_log P+` |
| Правовая информация | Legal | `set_about_sec_legal` | `About_SecLegal` | `AP` (9.1) | `A+ P+` |
| Политика конфиденциальности | Privacy policy | `set_about_privacy` | `About_Privacy` | `P` | `A←about_privacy P+` |
| Лицензии открытого кода | Open-source licences | `set_about_licenses` | `About_Licenses` | `AP` | `A+ P+` |
| Скопировать сведения об устройстве | Copy the device details | `set_about_copy_info` | `About_CopyDetails` | `AP` | `A+ P✓` |
| ОС: %1$s\nВерсия Android: %2$s\nАрхитектура: %3$s | OS: %1$s\nAndroid version: %2$s\nArchitecture: %3$s | `set_about_system_info` | - | `A` | `A+ P-` |
| ОС: %1$s\nАрхитектура: %2$s\nСреда выполнения: %3$s | OS: %1$s\nArchitecture: %2$s\nRuntime: %3$s | - | `About_SystemInfo` | `P` | `A- P✓` |
| Нажмите на схему, чтобы скопировать. | Tap a scheme to copy it. | `set_schemes_hint` | `UrlSchemes_Hint` | `AP` | `A+ P✓` |
| Регистрация схемы depv:// | depv:// scheme registration | - | `UrlSchemes_Registration` | `=` | `A- P✓` |
| Зарегистрировать | Register | - | `UrlSchemes_Register` | `=` | `A- P✓` |
| Убрать | Remove | - | `UrlSchemes_Remove` | `=` | `A- P✓` |
| Подключить | Connect | `common_connect` | `Common_Connect` | `A` (R-22) | `A+ P←Tray_Connect` |
| Открыть приложение | Open the app | `scheme_label_open` | `UrlSchemes_OpenApp` | `A` | `A+ P✓` |
| Отключить | Disconnect | `common_disconnect` | `Common_Disconnect` | `=` (R-22) | `A←menu_actions_busy_action P←Tray_Disconnect` |
| Закрыть приложение | Close the app | `scheme_label_close` | `UrlSchemes_Close` | `A` | `A+ P✓` |
| Переключить подключение | Toggle the connection | `scheme_label_toggle` | `UrlSchemes_Toggle` | `A` | `A+ P✓` |
| Импорт (автоопределение) | Import (auto-detect) | `scheme_label_import` | `UrlSchemes_Import` | `=` | `A←url_scheme_label_import P✓` |
| Добавить по ссылке | Add from a link | `scheme_label_add_url` | `UrlSchemes_AddByUrl` | `P` | `A←asset_action_add_url P✓` |
| Все | All | `set_log_all` | `Log_All` | `P` | `A←filter_config_all† P+` |
| Ошибки | Errors | `common_errors` | `Common_Errors` | `AP` | `A+ P+` |
| Скопировать всё | Copy everything | `set_log_copy` | `Log_CopyAll` | `P` | `A←log_action_copy P+` |
| Очистить | Clear | `set_log_clear` | `Log_Clear` | `P` | `A←logcat_clear† P+` |

`UrlSchemes_Close`: the string exists and is correct, and the page labels **both**
`depv://disconnect` and `depv://close` with `UrlSchemes_Stop` («Отключиться»)
(`UrlSchemesPage.axaml.cs:33-34`). One of the two rows is therefore a lie about what the scheme does.
The licences dialog: `AboutActivity.kt:31,33` opens it with an English hardcoded title («Open source
licenses») and an «OK» button, on a screen whose own row is Russian (R-15).

**The scheme page had a private vocabulary, and this register was creating it** (R-22). `depv://`
does exactly what the shield, the tray and the notification do, so it is labelled with exactly their
verbs. The first edition gave `depv://connect` two names in one document - «Подключить» in 3.2, where
the Экраны column names the scheme explicitly, and «Запустить туннель» here, marked as a string
Android must **gain**; wrote «Отключиться» where every other surface says «Отключить»; and kept
«Переключить подключение» beside state words «Подключено» / «Отключено». No rule exempts this page,
and there is no behavioural difference to record: `depv://connect` starts the same service the shield
starts.

**«Правовое» is not a section header in Russian** - it is an adjective with no noun. «Правовая
информация» is the phrase, and it is what the two rows under it are.

**«.NET» is not a word for a user's screen** (C4), and Android has no .NET runtime at all. The first
edition marked one three-slot row `A+`, which orders Android to **gain** a string naming a runtime it
does not have. The row is split: Android reports what an Android device has, the desktop reports its
own runtime under a Russian name. This is the one row in the register whose argument **count** is the
same but whose argument **meaning** differs by platform, and 5.2 records it as such.

### 3.7 Errors

9.4's formula is **what happened + why + what to do**, and this register adds C3: the surface must
carry the recovery control, and the control is named in the last column. C9 adds the closing: one
error sentence ends one way, «Повторите попытку.», joined with «и» when the sentence already names
what to check. Where 9.4 fixes the exact sentence, the row is marked **9.4**; where 9.4's own
sentence is defective, the row is marked **9.4!** and section 7.2 says why.

Three conditions are **not** repeated here. The device limit, the silent server and the failed
provider refresh each have one sentence and one key, declared in 3.2's strip table, and every surface
that hits the condition renders that key (R-21).

| Ситуация | Русский | English | Android | PC | Восстановление | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| No network **9.4** | Нет подключения к интернету. Проверьте сеть и повторите попытку. | No internet connection. Check your network and try again. | `err_network` | `Common_NetworkError` | «Повторить» in the error state | `AP` (9.4) | `A+ P✓` |
| Server unreachable **9.4!** | Сервер не отвечает. Выберите другой сервер. | The server is not responding. Choose another one. | `strip_silent` | `Strip_Silent` | «Сменить сервер» in the Главная strip | `AP` (R-8) | `A+ P+` |
| Provider refresh failed **9.4!** | Не удалось обновить провайдера. Проверьте его ссылку и повторите попытку. | Could not refresh the provider. Check its link and try again. | `strip_provider_failed` | `Strip_ProviderFailed` | «Повторить» on the group header | `AP` (R-21) | `A+ P+` |
| Device limit **9.4!** | Достигнут лимит устройств. Отвяжите одно из них. | You have reached the device limit. Unlink one of them. | `strip_devices` | `Strip_Devices` | «Устройства» | `AP` (R-21) | `A+ P+` |
| Subscription expired **9.4** | Подписка истекла. Продлите её, чтобы подключаться. | Your subscription has expired. Renew it to connect. | `strip_expired` | `Strip_Expired` | «Продлить» | `AP` (9.4) | `A+ P+` |
| Wrong credentials **9.4** | Неверная почта или пароль. | Incorrect email or password. | `auth_err_credentials` | `Login_ErrBadCreds` | the form stays filled, focus moves to the password | `=` | `A✓ P✓` |
| Payment declined **9.4** | Платёж не прошёл. Попробуйте другой способ оплаты. | The payment did not go through. Try another payment method. | `pay_failed` | `Buy_PaymentError` | «Выбрать другой способ», which reopens the pay sheet | `P` (9.4) | `A←account_payment_error_body P✓` |
| Unknown failure **9.4** | Что-то пошло не так. Повторите попытку. | Something went wrong. Try again. | `err_generic` | `Common_SomethingWrong` | «Повторить». Last resort only; the cause goes to the log | `=` (9.4) | `A←auth_err_generic P✓` |
| Service unavailable | Сервис временно недоступен. Повторите попытку. | The service is temporarily unavailable. Try again. | `err_service` | `Common_ServiceUnavailable` | «Повторить» | `AP` (C9) | `A+ P✓` |
| Timed out | Сервер не ответил вовремя. Повторите попытку. | The server did not answer in time. Try again. | `err_timeout` | `Common_Timeout` | «Повторить» | `P` (C9) | `A←account_error_timeout P✓` |
| Rate limited | Слишком много запросов. Подождите минуту и повторите попытку. | Too many requests. Wait a minute and try again. | `err_rate_limited` | `Common_TooManyRequests` | «Повторить», disabled while the countdown runs | `AP` (C3) | `A+ P✓` |
| Rate-limit countdown | Повторить через %1$s | Try again in %1$s | `err_rate_limited_in` | `Common_TooManyRequestsIn` | the label of that disabled button while it counts down | `AP` | `A+ P+` |
| Session expired | Сессия истекла. Войдите снова, чтобы продолжить. | Your session has expired. Sign in again to continue. | `err_unauthorized` | `Common_SignInRequired` | the session is cleared and the gate is rendered; no button needed | `P` | `A←account_error_unauthorized P✓` |
| Account failed to load | Не удалось загрузить аккаунт | Could not load your account | `account_error_title` | `Account_ErrorTitle` | error-state title above one of the sentences above, plus «Повторить» | `AP` | `A+ P+` |
| Devices failed to load | Не удалось загрузить устройства. Проверьте подключение и повторите попытку. | Could not load your devices. Check your connection and try again. | `devices_error` | `Devices_ErrLoad` | «Повторить» | `AP` (C9) | `A+ P✓` |
| History failed to load | Не удалось загрузить историю. Проверьте подключение и повторите попытку. | Could not load your payment history. Check your connection and try again. | `history_error` | `History_ErrLoad` | «Повторить» | `AP` (C9) | `A+ P✓` |
| Plans failed to load | Не удалось загрузить тарифы. Проверьте подключение и повторите попытку. | Could not load the plans. Check your connection and try again. | `buy_error` | `Buy_ErrLoadPlans` | «Повторить» | `AP` (C9) | `A✓ P✓` |
| No payment methods | Способы оплаты недоступны. Повторите попытку. | No payment methods are available. Try again. | `pay_no_methods` | `Buy_NoPaymentMethods` | «Повторить» | `AP` (C9) | `A+ P✓` |
| Payment page will not open | Не удалось открыть страницу оплаты. Скопируйте ссылку и откройте её в браузере. | Could not open the payment page. Copy the link and open it in your browser. | `account_checkout_no_browser` | `Common_CouldntOpenPayment` | «Скопировать ссылку» | `AP` (C3) | `A✓ P✓` |
| Sign-in unavailable | Вход сейчас недоступен. Повторите попытку. | Sign-in is not available right now. Try again. | `auth_err_unavailable` | `Login_ErrUnavailable` | «Повторить» | `AP` (C9) | `A✓ P✓` |
| Sign-in link expired | Ссылка устарела. Начните вход заново. | The link has expired. Start the sign-in again. | `auth_err_gone` | `Login_ErrLinkExpired` | «Начать заново» | `P` | `A✓ P✓` |
| Email already registered | Аккаунт с этой почтой уже существует. Войдите или восстановите пароль. | An account with this email already exists. Sign in, or reset your password. | `auth_err_email_taken` | `Login_ErrEmailTaken` | «Войти» | `AP` | `A+ P✓` |
| Passwords differ | Пароли не совпадают | The passwords do not match | `auth_err_password_mismatch` | `Login_PasswordMismatch` | field-level; the CTA stays disabled | `A` | `A+ P✓` |
| Invalid email | Введите адрес почты, например name@example.com | Enter an email address, for example name@example.com | `auth_email_invalid` | `Login_EmailInvalid` | field-level | `AP` | `A✓ P✓` |
| Could not read the link | Не удалось прочитать ссылку. Проверьте её и повторите попытку. | Could not read that link. Check it and try again. | `err_link_unreadable` | `Servers_ErrLink` | «Повторить» | `AP` (C9) | `A+ P+` |
| Protocol not supported | Такой протокол не поддерживается. Используйте ссылку из бота. | That protocol is not supported. Use the link from the bot. | `err_protocol` | `Servers_ErrProtocol` | «Открыть бота» | `AP` | `A+ P+` |
| Insecure provider URL | Адрес провайдера должен начинаться с https:// | A provider address has to start with https:// | `err_insecure_url` | `Provider_ErrInsecure` | field-level | `AP` | `A+ P+` |
| Server file is corrupt | Сервер повреждён. Удалите его и добавьте заново. | This server is corrupted. Delete it and add it again. | `err_server_corrupt` | `Servers_ErrCorrupt` | «Удалить» | `AP` | `A+ P+` |
| File not found | Файл не найден. Выберите другой файл. | That file was not found. Choose another one. | `err_file_missing` | `Backup_ErrFileMissing` | «Выбрать файл» | `AP` | `A+ P+` |
| Backup could not be written | Не удалось сохранить копию. Проверьте свободное место и повторите попытку. | Could not save the backup. Check the free space and try again. | `set_data_err_save` | `Backup_SaveFailed` | «Повторить» | `AP` (C9) | `A+ P✓` |
| Backup could not be read | Не удалось прочитать копию. Файл повреждён или создан другой версией. | Could not read that backup. The file is damaged, or it was made by another version. | `set_data_err_read` | `Backup_ImportError` | «Выбрать другой файл» | `AP` | `A+ P✓` |
| Geo bases failed | Не удалось обновить базы. Проверьте подключение и повторите попытку. | Could not update the databases. Check your connection and try again. | `set_assets_err` | `Geo_Failed` | «Повторить» | `AP` (C9) | `A+ P✓` |
| WebDAV failed | Не удалось подключиться. Проверьте адрес, логин и пароль. | Could not connect. Check the address, username and password. | `set_webdav_err` | `Webdav_Failed` | «Повторить» | `AP` | `A+ P+` |
| Scheme registration failed | Не удалось зарегистрировать схему. Нужны права администратора. | Could not register the scheme. It needs administrator rights. | - | `UrlSchemes_RegisterFailed` | «Перезапустить с правами» | `P` | `A- P✓` |
| Permission refused | Нет разрешения. Выдайте его в настройках Android и повторите попытку. | Permission denied. Grant it in Android settings and try again. | `err_permission` | - | «Открыть настройки» | `A` (C9) | `A+ P-` |
| Notification permission refused | Уведомления отключены. Разрешите их, чтобы видеть статус подключения. | Notifications are off. Allow them to see your connection status. | `err_permission_notifications` | - | «Открыть настройки» | `A` | `A+ P-` |
| VPN permission refused | Без разрешения на VPN туннель не запустится. Разрешите его и повторите попытку. | Without VPN permission the tunnel cannot start. Allow it and try again. | `err_permission_vpn` | - | «Повторить», which re-raises the system dialog | `A` (R-25) | `A+ P-` |
| Setting failed to save | Не удалось сохранить настройку. Повторите попытку. | Could not save that setting. Try again. | `set_err_save` | `Settings_ErrSave` | «Повторить» | `AP` (C9) | `A+ P+` |
| Port out of range | Введите порт от 1 до 65535 | Enter a port between 1 and 65535 | `set_err_port` | `Settings_ErrPort` | field-level | `AP` | `A+ P+` |
| MTU out of range | Введите значение от 576 до 9000 | Enter a value between 576 and 9000 | `set_err_mtu` | `Settings_ErrMtu` | field-level | `P` | `A←adv_mtu_error P+` |
| DNS address invalid | Проверьте адрес DNS-сервера | Check the DNS server address | `set_err_dns` | `Dns_ErrAddress` | field-level | `AP` | `A+ P+` |

**The PC block, in one sentence:** PC's error family stops after "what happened" in 11 of its 36
error strings (`41-copy-inventory-pc.md` 5.7), and in every one of those 11 cases Android already
ships the complete sentence. PC adopts Android's.

**Where both platforms are incomplete,** the register writes the sentence 9.4 asks for: the generic
failure gains its recovery, and the invalid-email hint drops «корректный», which tells a user nothing
about what is wrong with what they typed.

**Four errors used to hand the user a control that could not fix them** - the exact failure mode C3
exists to stop.

| Error | What it offered | What it offers now |
|---|---|---|
| `account_checkout_no_browser` | «Проверьте браузер по умолчанию.» with a «Повторить» that does the identical thing, and no affordance anywhere in the product for setting a default browser | «Скопируйте ссылку и откройте её в браузере.» with «Скопировать ссылку», a control the app actually has |
| `UrlSchemes_RegisterFailed` | «Запустите departament от имени администратора и повторите.» with «Повторить» - an instruction to do by hand what 3.2 already declares a button for, and a lowercase brand inside a Russian sentence (C8) | «Нужны права администратора.» with «Перезапустить с правами», the control the Главная strip already uses |
| `err_rate_limited` | «Подождите минуту и повторите.» with a button disabled for 60 s and no indication of when it wakes | the same sentence, and the button counts down: «Повторить через 43 с» |
| `err_service`, `err_timeout`, and six more | «…Повторите попытку **позже**.» beside a button labelled «Повторить», available now | «позже» is dropped. If the button is there, the answer is not "later" |

**Deleted outright** (C4 and 9.4):

| String | Where | Why |
|---|---|---|
| `err_provider_refresh` / `Servers_ErrorSub`, `err_server_silent` / `Common_ServerSilent`, `devices_limit` / `Devices_LimitReached` | both | A second sentence for a condition that already has one (R-21). The strip's key is the only one |
| `toast_failure` «Ошибка», `account_status_failed` «Ошибка» | Android | One word. No what, no why, no what-to-do |
| `toast_none_data` «Ничего нет» | Android | Same |
| `toast_decoding_failed` «Невозможно декодировать» | Android | Machine vocabulary |
| `toast_incorrect_protocol`, `toast_invalid_url`, `toast_action_not_allowed` | Android | Replaced by the rows above |
| `connection_test_error` «Не удалось проверить подключение: %s» | Android | `%s` is the technical cause (C4) |
| `devices_diag_http` «HTTP: %1$d», `devices_diag_title`, `devices_diag_failed`, `devices_diag_empty` | Android | Print the raw server response and ask the user for a screenshot |
| `account_payment_error_body` / `_nodetail` («HTTP %1$s\n%2$s») | Android | An HTTP code in a payment dialog. Both also take arguments their text has no slots for, so the values are silently dropped |
| `migration_fail` «Перенос данных не выполнен!», `pull_down_to_refresh` «Потяните вниз для обновления!», `del_invalid_config_comfirm` «Выполните проверку перед удалением! Подтверждаете удаление?» | Android | Exclamation marks (9.1). All three are unreachable today and must not come back |
| `toast_services_failure` «Сбой при запуске служб», `toast_services_start`, `toast_services_stop`, `tasker_start_service` | Android | «Служба» is an implementation word. The user's word is «подключение» |
| `+ ex.Message` at `GeoFilesPage.axaml.cs:89`, `BackupPage.axaml.cs:51,84`, `UrlSchemesPage.axaml.cs:110,144` | PC | Glues an untranslated .NET exception onto a Russian sentence. The five trailing spaces that exist only to accommodate it go with it |
| `Home_RetryHint` as an error's only affordance | PC | It names the gesture, not the fix. The strip carries the sentence and the action |

### 3.8 Empty states

9.5's formula is **title (what is not here) + one line (why, or what it gives you) + one action**.
Never «Нет данных» on its own, and never an action that does not exist.

| Экран | Заголовок | Строка | Действие | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Серверы, none | Нет серверов | Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | Добавить провайдера | `common_no_servers`, `servers_empty_body`, `common_add_provider` | `Common_NoServers`, `Servers_EmptyHint`, `Common_AddProvider` | `A` | `A←servers_empty_title A+ P←Home_NoSubs P✓ P←Common_AddSubscription` |
| Главная, none | Нет серверов | Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы. | Добавить провайдера | shared keys | shared keys | `A` | `A←servers_empty_title A+ P←Home_NoSubs P←Home_NoSubsHint P←Common_AddSubscription` |
| Search found nothing | Ничего не найдено | Попробуйте другой запрос. | Сбросить поиск | `common_search_empty_title`, `common_search_empty_body`, `common_search_empty_cta` | `Common_SearchEmptyTitle`, `Common_SearchEmptyBody`, `Common_SearchEmptyCta` | `P` | `A←editor_search_empty_title A←editor_search_empty_line A←editor_search_reset P+` |
| Settings search, none | Ничего не найдено | Попробуйте другой запрос. | Сбросить поиск | shared keys | shared keys | `P` | `A←editor_search_empty_title A←editor_search_empty_line A←editor_search_reset P+` |
| App list search, none | Ничего не найдено | Попробуйте другой запрос. | Сбросить поиск | shared keys | shared keys | `P` | `A←editor_search_empty_title A←editor_search_empty_line A←editor_search_reset P+` |
| Аккаунт, no subscription | Подписки пока нет | Купите тариф, чтобы подключаться к серверам Departament. | Купить | `account_empty_title`, `account_empty_body`, `account_card_buy` | `Account_FirstSub`, `Account_NoSubHint`, `Account_Buy` | `=` (9.5) | `A✓ A←account_no_subscription A←buy_pay P✓ P←Buy_Pay` |
| Устройства, none | Устройств пока нет | Устройства появятся после первого подключения. | none | `devices_empty_title`, `devices_empty_body` | `Devices_Empty`, `Devices_EmptyHint` | `AP` | `A←devices_empty A←devices_empty_hint A+ P✓` |
| Устройства, no subscription | Подписка не активна | Купите тариф, чтобы подключать устройства. | Купить тариф | `devices_nosub_title`, `devices_nosub_body`, `common_buy_plan` | `Devices_NoSub`, `Devices_NoSubHint`, `Common_BuyPlan` | `AP` (R-5) | `A←auth_subscription_expired A+ P✓ P+` |
| История платежей, none | Платежей пока нет | Здесь появится история покупок и продлений. | none | `history_empty_title`, `history_empty_body` | `History_Empty`, `History_EmptyHint` | `AP` | `A←history_empty A+ P✓` |
| Купить, no plans | Тарифов пока нет | Список обновляется автоматически, загляните позже. | Повторить | `buy_empty_title`, `buy_empty_body`, `common_retry` | `Buy_EmptyTitle`, `Buy_EmptyBody`, `Common_Retry` | `AP` (R-7) | `A+ A←buy_retry P+ P✓` |
| Telegram not linked | Telegram не привязан | Привяжите Telegram, чтобы управлять подпиской из бота. | Привязать Telegram | `account_no_telegram`, `account_no_telegram_body`, `common_link_telegram` | `Account_NoTelegram`, `Account_NoTelegramHint`, `Common_LinkTelegram` | `AP` | `A✓ A+ A←home_link_telegram P+` |
| Маршрутизация, no sets | Наборов правил пока нет | Добавьте набор или восстановите стандартные. | Добавить набор | `set_routing_empty_title`, `set_routing_empty_body`, `set_routing_add` | `Routing_EmptyTitle`, `Routing_EmptyBody`, `Routing_Add` | `AP` | `A+ P+` |
| Файлы ресурсов, no custom | Своих файлов нет | Добавьте файл, если провайдер прислал свою базу. | Добавить файл | `set_assets_empty_title`, `set_assets_empty_body`, `set_assets_add` | `Geo_EmptyTitle`, `Geo_EmptyBody`, `Geo_Add` | `AP` | `A+ P+` |
| Журнал, empty | Журнал пуст | Здесь появятся события приложения. | none | `set_log_empty_title`, `set_log_empty_body` | `Log_EmptyTitle`, `Log_EmptyBody` | `AP` | `A+ P+` |
| Upgrade sheet, none left | Улучшать нечего | Это старший тариф. | Закрыть | `upgrade_none`, `upgrade_none_body`, `common_close` | `Account_NoUpgrades`, `Account_NoUpgradesBody`, `Common_Close` | `AP` (C9) | `A+ A←editor_close P✓ P+` |

**Notes.** The two «Нет серверов» states: Android's `home_empty_title` is «Подписок пока нет» and its
body says «Добавьте подписку» - two lock breaks in one state, and the title names the wrong object
(the user is missing servers, not subscriptions). Both states and the Главная shield now render the
same key. The search-empty state: PC ships a search box (`CompactServersView.axaml:108`) and renders
**no** filtered-empty state at all; the three strings are shared by all three search fields, so the
`servers_search_empty_*` prefix moves to `common_search_empty_*`. The payment history:
`History_EmptyHint` is defined at `L.Buy.cs:66` and never drawn, and the view puts a «Купить
подписку» button on a state 9.5 says takes no action.

**The buy vocabulary agrees with itself** (R-5). The Устройства no-subscription state used to read
«Купите **тариф**, чтобы подключать устройства.» under a button saying «Купить **подписку**» - the
body and the button under it naming different objects, on one card. The object is the тариф
everywhere; the bare «Купить» survives only where the card above it has already named what is being
bought, which is 9.5's own shape for the Аккаунт state.

**One deliberate near-pair, recorded so nobody collapses it.** The Главная shield status for a
missing subscription is **«Подписки нет»** (`home_status_no_subscription`); the empty-state title on
Аккаунт is **«Подписки пока нет»**. The first names a condition in a two-word status slot; the second
opens an empty state, and «пока» is what makes it an invitation rather than a verdict. They are
different roles on different surfaces. With the exemption in 3.1.2, these are the only two.

### 3.9 Offline and stale data

9.6: offline is a designed state, not an error toast. The screen keeps its last known data, marks it
stale, disables the actions that need the network, and shows one quiet persistent bar.

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| The bar | Нет сети. Показаны последние данные. | No network. Showing the last known data. | `strip_offline` | `Strip_Offline` | Главная, Серверы, Аккаунт; action «Повторить» | `AP` | `A+ P+` |
| The stale mark | Данные могли устареть | This data may be out of date | `strip_stale` | `Strip_Stale` | under any value older than its refresh window, and on a provider group header | `AP` | `A+ P+` |
| Retry | Повторить | Try again | `common_retry` | `Common_Retry` | the bar's action | `=` | `A←buy_retry P✓` |

**This is the largest shared gap in the product: neither of the two strings exists on either platform
today** (`41-copy-inventory-pc.md` 5.10, and Android has only `account_error_network`). Both clients
currently treat losing the network as a failed request, so a user in a lift sees an error where a
state belongs. Two keys, not five: the first edition declared `account_offline` and
`servers_group_offline` with text identical to the two above, which is three keys for one bar and two
for one mark.

### 3.10 Dialogs and confirmations

`00-rules.md` 7.5-7.6: a dialog only for a decision that cannot be inline, a destructive confirm
with a red text button on the right and a neutral cancel on the left, and the button says what it
does. R-15: no dialog anywhere says «OK».

| Действие | Заголовок | Тело | Левая | Правая | Android | PC | Текст | Ключи |
|---|---|---|---|---|---|---|---|---|
| Sign out | Выйти из аккаунта? | Подписка останется активной. Чтобы вернуться, войдите снова. | Отмена | Выйти | `account_logout_title`, `account_logout_body`, `common_cancel`, `account_row_logout` | `Account_LogoutTitle`, `Account_LogoutBody`, `Common_Cancel`, `Account_SignOut` | `AP` | `A✓ A←adv_cancel P+ P✓` |
| Unlink a device | Отвязать устройство? | Устройство «%1$s» будет отвязано от подписки. | Отмена | Отвязать | `devices_unlink_title`, `devices_unlink_body`, `common_cancel`, `common_unlink` | `Devices_UnlinkConfirm`, `Devices_UnlinkBody`, `Common_Cancel`, `Common_Unlink` | `AP` (R-3) | `A+ A←adv_cancel P✓ P←Devices_UnlinkShort` |
| Unlink a sign-in method | Отвязать %1$s? | Этот способ входа перестанет работать. Остальные останутся. | Отмена | Отвязать | `linking_unlink_title`, `linking_unlink_body`, `common_cancel`, `common_unlink` | `Linking_UnlinkTitle`, `Linking_UnlinkBody`, `Common_Cancel`, `Common_Unlink` | `AP` | `A+ A←adv_cancel P+ P✓ P←Devices_UnlinkShort` |
| Delete every server | Удалить все серверы? | Серверы провайдеров вернутся после обновления, добавленные вручную придётся добавить заново. | Отмена | Удалить | `servers_del_all_title`, `servers_del_all_body`, `common_cancel`, `common_delete` | `Servers_DelAllTitle`, `Servers_DelAllBody`, `Common_Cancel`, `Common_Delete` | `AP` | `A+ A←adv_cancel A←editor_delete P+ P✓` |
| Delete duplicates | Удалить дубликаты? | Будет удалено %1$s. Останется по одному серверу из каждой пары. | Отмена | Удалить | `servers_del_dup_title`, `servers_del_dup_body`, `common_cancel`, `common_delete` | `Servers_DelDupTitle`, `Servers_DelDupBody`, `Common_Cancel`, `Common_Delete` | `AP` (R-26) | `A+ A←adv_cancel A←editor_delete P+ P✓` |
| Delete unreachable | Удалить недоступные серверы? | Будет удалено %1$s из тех, что не ответили при проверке. | Отмена | Удалить | `servers_del_invalid_title`, `servers_del_invalid_body`, `common_cancel`, `common_delete` | `Servers_DelInvalidTitle`, `Servers_DelInvalidBody`, `Common_Cancel`, `Common_Delete` | `AP` (R-26) | `A+ A←adv_cancel A←editor_delete P+ P✓` |
| Delete a server | - | - | - | - | undo, not a confirm | undo, not a confirm | - ||
| Delete a provider | - | - | - | - | undo, not a confirm | undo, not a confirm | - ||
| Reset the routing rules | Сбросить правила маршрутизации? | Все наборы, включая созданные вами, будут удалены. Действие нельзя отменить. | Отмена | Сбросить | `set_routing_reset_title`, `set_routing_reset_body`, `common_cancel`, `set_routing_reset_confirm` | `Routing_ResetTitle`, `Routing_ResetBody`, `Common_Cancel`, `Routing_ResetConfirm` | `AP` | `A+ A←adv_cancel A←menu_actions_reset_search P+ P✓ P←Routing_Reset` |
| Reset the settings | Сбросить настройки? | Все настройки вернутся к значениям по умолчанию. Серверы, провайдеры и аккаунт не пострадают. | Отмена | Сбросить | `set_data_reset_title`, `set_data_reset_body`, `common_cancel`, `set_data_reset_confirm` | `Backup_ResetTitle`, `Backup_ResetBody`, `Common_Cancel`, `Backup_ResetConfirm` | `AP` | `A+ A←adv_cancel A←menu_actions_reset_search P+ P✓ P←Routing_Reset` |
| Restore a backup | Восстановить из копии? | Текущие настройки, провайдеры и серверы будут заменены. Приложение перезапустится. | Отмена | Восстановить | `set_data_restore_title`, `set_data_restore_body`, `common_cancel`, `set_data_restore_confirm` | `Backup_RestoreTitle`, `Backup_RestoreBody`, `Common_Cancel`, `Backup_RestoreConfirm` | `AP` | `A+ A←adv_cancel A←backup_restore_confirm_action P+ P✓` |
| Reconnect after choosing a server | Переподключиться к «%1$s»? | - | Отмена | Переподключиться | `server_selected_reconnect_prompt`, `common_cancel`, `servers_reconnect` | `Servers_ReconnectPrompt`, `Common_Cancel`, `Servers_Reconnect` | `AP` | `A✓ A←adv_cancel A←server_selected_reconnect_action† P+ P✓` |
| Reconnect, name unknown | Сервер выбран. Переподключиться к нему? | - | Отмена | Переподключиться | `server_selected_reconnect_prompt_generic`, `common_cancel`, `servers_reconnect` | `Servers_ReconnectGeneric`, `Common_Cancel`, `Servers_Reconnect` | `P` | `A✓† A←adv_cancel A←server_selected_reconnect_action† P+ P✓` |
| Linux: sudo password | Пароль администратора | Пароль проверяется в терминале и не сохраняется. Его нужно вводить после каждого запуска. | Отмена | Подтвердить | - | `Sudo_Title`, `Sudo_Body`, `Common_Cancel`, `Common_Confirm` | `P` | `A- P+ P✓ P←Login_Confirm` |
| Wrong sudo password | - | Неверный пароль. Проверьте раскладку и повторите попытку. | - | - | - | `Sudo_Wrong` | `P` (C9) | `A- P+` |

**A `-` in this table is a declared absence, not a gap.** Three shapes use it, and each is named
here so that nobody fills them in: a row whose four dialog cells are all `-` has **no dialog at all**
and is confirmed by an undo snackbar instead (the two single-item deletes); a row with a title and no
body is a **one-line dialog**, where a body would only restate the title («Переподключиться к
«%1$s»?»); and a row with a body and no buttons is **inline text**, not a dialog (`Sudo_Wrong` sits
under the password field).

**Notes.** The two delete rows: `16-servers.md` 8.3 replaces the confirm with an undo snackbar for a
single server and for a provider. PC's `Sub_DeleteConfirm` («Удалить провайдера и его серверы?») is
deleted with the flow, and Android's `sub_delete_confirm` («Удалить подписку и её серверы?») dies
twice over: for the flow and for the lock. The sudo pair: PC currently renders three upstream `ResUI`
strings there, one of which is 240 characters long, carries an em-dash and explains what happens if
the check fails before the user has failed anything. The sign-out dialog: PC has no confirm at all -
`Account_SignOut` acts immediately.

**Two bulk deletions gained a confirm** (R-26). «Удалить дубликаты» and «Удалить недоступные серверы»
sat in the header overflow one row above «Удалить все серверы», deleted an unbounded number of
servers on a single tap, and had no confirm, no count and no undo; their result string
(`servers_deleted_count`) existed in 5.2 and in 4.2 and in no approved row at all. Both bodies name
the count as a plural, which is why 4.2's `plural_servers` lists them as callers, and all three land
in the same undo snackbar as the single-server delete.

**The 19 «OK» dialogs.** `MainActivity.kt:1270,1697,2487,3296,3326`, `AccountFragment.kt:587,609`,
`BuyTariffActivity.kt:545`, `DeviceManagementActivity.kt:171`, `LoginActivity.kt:339`,
`ProviderSettingsActivity.kt:232`, `ServerActivity.kt:671`, `ServerGroupActivity.kt:118`,
`ServerProxyChainActivity.kt:143`, `ServerCustomConfigActivity.kt:119`, `SubEditActivity.kt:224`,
`SubSettingActivity.kt:122`, `UserAssetActivity.kt:222`, `UserAssetUrlActivity.kt:123` all pass
`android.R.string.ok`, which renders «ОК» on a Russian device. Every one of them is either deleted
with its screen or given the verb of its action. `AccountFragment.kt`'s sign-out dialog already does
this correctly and is the model.

### 3.11 Notifications, permissions, the tray, the quick tile and shortcuts

| Концепт | Русский | English | Android | PC | Экраны | Текст | Ключи |
|---|---|---|---|---|---|---|---|
| Notification title | departament | departament | `app_name` | - | ongoing notification | `=` | `A✓ P-` |
| Notification text, connected | Подключено · %1$s | Connected · %1$s | `notif_connected` | - | ongoing notification; %1$s is the server name | `A` | `A+ P-` |
| Notification text, connecting | Подключение… | Connecting… | `home_status_connecting` | - | ongoing notification, shared key | `=` | `A←toast_status_connecting† P-` |
| Notification text, disconnecting | Отключение… | Disconnecting… | `home_status_disconnecting` | - | ongoing notification, shared key | `A` | `A+ P-` |
| Notification text, reconnecting | Переподключаемся… | Reconnecting… | `notif_reconnecting` | - | ongoing notification, after a dropped tunnel | `A` | `A+ P-` |
| Notification text, failed | Не удалось подключиться. Откройте приложение. | Could not connect. Open the app. | `notif_error` | - | ongoing notification, error state; action «Открыть» | `A` | `A+ P-` |
| Notification action: stop | Отключить | Disconnect | `common_disconnect` | - | notification action, shared key | `=` | `A←menu_actions_busy_action P-` |
| Notification action: open | Открыть | Open | `common_open` | - | notification action, shared key | `=` | `A←url_scheme_label_open P-` |
| Notification channel name | Статус подключения | Connection status | `notif_channel_name` | - | Android system settings, channel list | `A` | `A+ P-` |
| Notification channel description | Постоянное уведомление, пока туннель работает. Без него Android остановит подключение. | The ongoing notification shown while the tunnel is up. Without it Android stops the connection. | `notif_channel_desc` | - | Android system settings, under the channel name | `A` | `A+ P-` |
| Notification rationale | Разрешите уведомления, чтобы видеть статус подключения на экране блокировки | Allow notifications to see your connection status on the lock screen | `perm_notif_rationale` | - | the sheet shown **before** the Android 13+ system request; actions «Разрешить» / «Не сейчас» | `A` (R-25) | `A+ P-` |
| VPN consent lead-in | Android спросит разрешение на VPN. Без него туннель не запустится. | Android will ask for VPN permission. Without it the tunnel cannot start. | `perm_vpn_rationale` | - | the sheet shown **before** the system VPN dialog on first connect; actions «Продолжить» / «Отмена» | `A` (R-25) | `A+ P-` |
| Allow | Разрешить | Allow | `perm_allow` | - | rationale sheet | `A` | `A+ P-` |
| Not now | Не сейчас | Not now | `perm_later` | - | rationale sheet | `A` | `A+ P-` |
| Continue | Продолжить | Continue | `pay_continue` | `Account_Continue` | rationale sheet, top-up sheet | `A` | `A+ P✓` |
| Latency test notification | Проверяем задержку серверов… | Testing server latency… | `notif_ping_title` | - | foreground service while testing | `A` | `A+ P-` |
| Latency test progress | Осталось: %1$s | %1$s left | `notif_ping_progress` | - | that notification | `A` | `A+ P-` |
| Quick tile label | departament | departament | `app_name` | - | Android quick settings | `=` | `A✓ P-` |
| Quick tile, no server | Нет серверов. Добавьте провайдера в приложении. | No servers. Add a provider in the app. | `app_tile_first_use` | - | tile tap with an empty list | `A` | `A✓ P-` |
| Tray: connect | Подключить | Connect | `common_connect` | `Common_Connect` | tray menu, shield, launcher shortcut | `A` | `A+ P←Tray_Connect` |
| Tray: disconnect | Отключить | Disconnect | `common_disconnect` | `Common_Disconnect` | tray menu, shield, notification action | `=` | `A←menu_actions_busy_action P←Tray_Disconnect` |
| Tray: show the window | Показать окно | Show the window | - | `Tray_Show` | tray menu, hotkey label | `P` | `A- P✓` |
| Tray: restart | Перезапустить | Restart | - | `Tray_Restart` | tray menu | `=` | `A- P✓` |
| Tray: quit | Завершить работу | Quit | - | `Tray_Exit` | tray menu | `P` (R-24) | `A- P✓` |
| Tray tooltip | departament · %1$s | departament · %1$s | - | `Tray_Tooltip` | hover over the tray icon; %1$s is the status | `P` | `A- P+` |
| Launcher shortcut: servers | Серверы | Servers | `common_servers` | - | long-press the launcher icon | `=` | `A←title_servers P-` |

**Notes.** The two notification actions read «Остановить» and «Ещё…» today - one is the service's
vocabulary rather than the tunnel's, and the other names no destination. The tile's first-use string:
«Сначала добавьте сервер в приложении.» states a precondition without saying what the user is
missing; the new form is 9.4-shaped and uses the locked noun. «Показать» alone is ambiguous next to
«Показать пароль». All five tray items are **hardcoded literals** in `App.axaml:39-45` today, so they
do not follow the language switch; they move into `L`.

**The notification had one string and needs seven.** A user sees this notification for the whole life
of a session, and 3.2 defines five connection states; the first edition gave it text for two of them
and no channel description at all, although Android renders that description in the system channel
list directly under the name. The channel's own name was «Подключение», which is also the name of the
Настройки group two screens away; «Статус подключения» is what the channel actually is.

**A permission is explained before it is asked** (R-25). Android 13+ requires a `POST_NOTIFICATIONS`
request, and a VPN client raises the system consent dialog on first connect. The first edition
carried only the two **refusal** strings, so the product's copy began at the moment the user had
already said no. Both rationales name what the user gets, not what the app needs; both are sheets
with two actions, so the refusal path is a decision rather than a dead end; and the VPN one says out
loud that the dialog about to appear is Android's, which is the single fact that stops a first-time
user from dismissing it as a scam.

---

## 4. Plurals

Russian needs three forms. Android has **zero** `<plurals>` in the entire resource tree today, so
the Серверы tab literally renders «1 серверов · 1 провайдеров»; PC has two plural sets and a helper,
and needs three more.

### 4.1 The selector

Identical on both platforms. Android's `PluralRules` implements it natively; PC needs it written
once in `Common/Plural.cs`:

```
form(n):  a = n % 100,  b = n % 10
  11 <= a <= 14   -> MANY    (5 серверов, 12 дней)
  b == 1          -> ONE     (1 сервер, 21 день)
  2 <= b <= 4     -> FEW     (2 сервера, 33 дня)
  otherwise       -> MANY
```

Android additionally requires an `other` item; Russian reaches it only for fractional counts
(«1,5 дня»), so `other` always carries the **FEW** form. Filling it with the MANY form is the
mistake that produces «1,5 дней».

Leaving `other` out is **not** an aapt2 error - the build succeeds and the string falls back
silently at runtime. It is Android Lint's `MissingQuantity`, which is a *warning* until it is
promoted, and 8.3 promotes it. The first edition called it a build failure, which is the kind of
claim that stops anyone from wiring the check that would actually catch it.

### 4.2 The sets

**Seven sets**, and every one of them has a caller named in section 3. A set with no caller is not
created, and a caller with no set is a bug - `set_perapp_except` was one, printing a bare integer
with no noun beside it («Кроме 1»), which is why `plural_apps` is here.

| Набор | one | few | many / other | Android | PC | Кто вызывает |
|---|---|---|---|---|---|---|
| Servers | %d сервер | %d сервера | %d серверов / %d сервера | `plural_servers` | `Common_ServersPlural` | Главная meta row, Серверы header count, «Добавлено: %1$s», «Удалено: %1$s», the two bulk-delete confirm bodies (3.10) |
| Providers | %d провайдер | %d провайдера | %d провайдеров / %d провайдера | `plural_providers` | `Common_ProvidersPlural` | Главная meta row, Серверы header count |
| Days, with the figure | %d день | %d дня | %d дней / %d дня | `plural_days` | `Common_DaysPlural` | «Осталось %1$s» on the card and the ledger, «Улучшение до %1$s, +%2$s» |
| Days, the word alone | день | дня | дней / дня | `plural_days_word` | `Common_DaysWordPlural` | the card's WORD slot, where the figure is a separate view in the figure face (`23-account-rework.md` 4.3) |
| Devices | %d устройство | %d устройства | %d устройств / %d устройства | `plural_devices` | `Common_DevicesPlural` | the add-devices subject line, the stepper's accessible name |
| Rules | %d правило | %d правила | %d правил / %d правила | `plural_rules` | `Common_RulesPlural` | the routing set row's subtitle |
| Apps | %d приложение | %d приложения | %d приложений / %d приложения | `plural_apps` | `Common_AppsPlural` | the per-app row's value slot: «Кроме %1$s», «Только %1$s» (3.6.1) |

**Why two day sets.** The account card sets the number in Space Grotesk at 34 sp and the word beside
it in Golos Text at 16 sp, baseline-aligned, because Space Grotesk maps no Cyrillic
(`00-rules.md` 5.1). A single set with `%d` inside it cannot serve that layout; a single set without
`%d` cannot serve the detail line. Two sets, one rule, no invented third face.

### 4.3 The mechanism, per platform

The PC column is copied from the code, not paraphrased. `AddPlural` is **private** and takes **two
arrays**, so it is called from inside a `Register*()` partial without an `L.` qualifier
(`Common/L.cs:97`, `Common/L.Common.cs:71`):

```csharp
// Common/L.cs
private void AddPlural(string key, string[] ru, string[] en) => _plurals[key] = (ru, en);
public static string Plural(string key, int n) => Instance.PluralImpl(key, n);          // "5 серверов"
public static string PluralWord(string key, int n) => Instance.PluralWordImpl(key, n);  // "серверов"

// Common/L.Common.cs, inside RegisterCommon()
AddPlural("Common_ServersPlural",
    new[] { "сервер", "сервера", "серверов" },
    new[] { "server", "servers" });
```

| | Android | PC |
|---|---|---|
| Declaration | `<plurals name="plural_servers">` with `one` / `few` / `many` / `other` in `res/values/strings_common.xml` | `AddPlural("Common_ServersPlural", new[] { "сервер", "сервера", "серверов" }, new[] { "server", "servers" })`, inside the area's `Register*()` partial |
| Call, with the figure | `resources.getQuantityString(R.plurals.plural_servers, n, n)` | `L.Plural("Common_ServersPlural", n)` → «5 серверов» |
| Call, the word alone | `resources.getQuantityString(R.plurals.plural_days_word, n)` - one argument, because the item contains no `%d` | `L.PluralWord("Common_DaysWordPlural", n)` → «дней» |
| The classic bug | passing `n` once to an item that does contain `%d`: `getQuantityString(id, n)` compiles and then throws `UnknownFormatConversionException` at the first `%d` | passing the count into `string.Format` before selecting the form, which selects on the formatted string |
| English | one / other only. `res/values-en/` declares `one` and `other`; the `few` and `many` items are Russian-only and must not be copied into the English file | the EN array takes two forms, singular and plural |

**`L.PluralWord` does not exist yet, and one of the seven sets cannot ship without it.** `PluralImpl`
ends `return $"{n} {word}"` - the count is welded on. `Common_DaysWordPlural` exists precisely to
return the word **alone**, so the account card can draw the figure in Space Grotesk at 34 sp and the
word in Golos Text at 16 sp on one baseline (see «Why two day sets» below); with only `L.Plural` that
layout is unreachable, and the first edition's own call shape
(`L.AddPlural("Common_ServersPlural", "сервер", …)` - a static call with six scalar arguments)
does not compile against the real signature at all. The accessor is the same selector without the
interpolation. Check `Common_DevicesPlural` and `Common_RulesPlural` the same way before shipping:
wherever the caller supplies its own figure, it wants `PluralWord`, not `Plural`.

### 4.4 Strings this replaces

| Today | Where | Renders at n=1 |
|---|---|---|
| `servers_count` = `%d серверов` | Android Главная, Серверы | «1 серверов» |
| `providers_count` = `%d провайдеров` | Android Главная, Серверы | «1 провайдеров» |
| `account_option_duration`, `buy_option_duration` = `%1$d дн.` | Android buy and account | «1 дн.» - an abbreviation 9.2 bans, and no grammar at all |
| `account_promo_free_days` = `Бесплатно %1$d дн.` | Android | same |
| `Account_ExpiresInDays` = `Осталось {0} дн.` | PC account card | same |
| `Common_DaysShort` = `{0} дн.` | PC buy | same |
| `menu_actions_invalid_deleted` = `Недоступные серверы удалены: %1$d` | Android bulk delete | a count welded to a passive sentence |
| `tv_receive_success` = `Подписка импортирована (%1$d).` | Android TV pairing | a bare number in brackets with no unit |
| `Routing_RulesCount` / `set_routing_rules_n` = `%1$d правил` | both, routing | «1 правил» |
| `Account_DevicesTotal` = `{0} устройств` | PC account | «1 устройств» |
| `set_perapp_except` / `Settings_PerAppExcept` = `Кроме %1$d` | both, Настройки hub | «Кроме 1» - a bare integer with no noun to agree with at all |
| `set_perapp_only` / `Settings_PerAppOnly` = `Только %1$d` | both, Настройки hub | «Только 1» |

---

## 5. Format specifiers, escaping and typography

**A specifier mismatch between a translation and its source crashes at runtime.** On Android a
`%1$s` in `values/` and a `%1$d` in `values-en/` is an `IllegalFormatConversionException` the moment
the string is formatted; on PC a `{1}` with one argument supplied is a `FormatException`. 5.2 is the
index, and it is **generated** by
[`tools/check-specifiers.py`](tools/check-specifiers.py) from section 3 - see 5.4.

### 5.1 The rules

1. **Android always uses positional specifiers** (`%1$s`), never bare `%s`, even with one argument.
   A bare `%s` cannot be reordered by a translator and silently breaks when a second argument is
   added later. This is what aapt2 rejects outright, with «multiple substitutions specified in
   non-positional format», once a second bare specifier appears in the same string.
2. **PC uses composite formatting** (`{0}`), and the index order must match the Russian sentence, not
   the English one.
3. **Section 3's Русский cell always shows the Android positional form**, including on rows the
   desktop alone renders. 5.2 gives the desktop's `{0}` equivalent, with the same index order. One
   notation in the tables, two notations in the two codebases.
4. **A literal percent sign is `%%` in an Android resource** and `%` on PC. Getting it wrong is not a
   build failure: `aapt2` accepts a lone `%` and `String.format` then throws
   `UnknownFormatConversionException` at runtime, on the one screen where the string is used. The
   typography is Мильчин's: the sign is set **closed up** to the figure, `100%`, not `100 %`.
5. **No format specifier carries a date pattern.** `Sub_Until` = `до {0:dd.MM.yyyy}` is deleted with
   R-1: the date is formatted by the caller with the rules in `23-account-rework.md` 4.2, so the
   string takes a ready `string`.
6. **A string never takes an argument it does not print.** `account_payment_error_body` accepts two
   and prints none, so the values are silently dropped - which is how a payment failure came to show
   the user nothing about itself.
7. **The count of a plural is passed twice on Android** (once to select the form, once to fill `%d`),
   and once on PC. A set whose items carry no `%d` (`plural_days_word`) is passed the count **once**
   on Android and read through `L.PluralWord` on PC (4.3).
8. **`\n` is the two-character escape, never a real newline.** A literal line break inside a
   `<string>` element collapses to a single space when aapt2 compiles it, so
   `set_about_system_info` written across three physical lines renders as one. The first edition's
   5.2 said these were «real newlines in both files»; on Android they are `\n`, and on PC they may be
   either but are written `\n` so the two files read the same.

### 5.1.1 Escaping, and why `values-en/` will not compile without it

**Every `'` in an Android string resource is written `\'`, and every `"` is written `\"`** - or the
whole value is wrapped in `"…"`, which escapes both. An unescaped apostrophe is a hard aapt2 error
(«Apostrophe not preceded by \\»); an unescaped double quote is rejected the same way.

This is not theoretical debt. The first edition's English column contained **56 apostrophes**
(«Couldn't connect», «You're offline», «isn't», «won't», «You've», «We'll», «doesn't», «Android's
settings» …) and **four** double quotes («Device "%1$s" will be unlinked…»), and W-25 creates
`values-en/` from exactly that column. The whole existing resource tree contains **two** escaped
apostrophes, so nothing in the codebase would have caught it either.

Two things changed, and both matter:

- **The English column no longer uses contractions at all.** «Could not connect», «You are offline»,
  «is not», «will not», «We will». It is a better register for a product UI than a contracted one, it
  matches the Russian column's directness, and it removes 56 opportunities to ship an unescaped
  apostrophe. The four double quotes became «ёлочки», which need no escaping in either platform.
- **8.1 greps for the ones that survive.** A proper name will eventually need an apostrophe, and when
  it does the grep says so before the build does.

The Russian column has never needed this: «ёлочки» are U+00AB / U+00BB and the apostrophe does not
occur in Russian orthography.

### 5.1.2 The characters this file uses, by codepoint

Copying a rendered document loses these. They are named so they can be typed.

| Char | Codepoint | Where |
|---|---|---|
| `·` | **U+00B7** MIDDLE DOT, with one **ordinary** space either side | the separator in ~20 strings: «Подключено · %1$s», «Тариф · %1$s», «Автообновление · %1$s» |
| `…` | **U+2026** HORIZONTAL ELLIPSIS, one character | «Подключение…», «Сохраняем…», never three dots |
| `«` `»` | **U+00AB** / **U+00BB** | every quotation in the product, Russian and English alike |
| ` ` | **U+2009** THIN SPACE | the thousands separator, inside the money formatter only |
| ` ` | **U+00A0** NO-BREAK SPACE | between a figure and `₽`, inside the money formatter only |
| `₽` | **U+20BD** | produced by the money formatter, never typed into a resource value (C6) |

The thin and no-break spaces appear in **no row of this register**: C6 puts every money string inside
one formatter per platform, so there is nothing to copy out of the Русский column. The first edition
told the reader to copy «the non-breaking space before `₽`» out of that column, four rules after
declaring that no money string is a resource value.

### 5.2 Every parameterised string

Generated from section 3 by `tools/check-specifiers.py`; the row set and the argument count are
derived, the type and the note are written by hand and preserved by key. `s` = string already
formatted by the caller, `d` = integer, `money` = the output of the one money formatter, `date` = the
output of the date formatter, `plural` = the output of a plural set.

| Android | PC | Аргументы по порядку | Типы | Примечание |
|---|---|---|---|---|
| `account_auto_renew_next` `Спишем %1$s %2$s` | `Account_AutoRenewNext` `Спишем {0} {1}` | amount, date | money, date | the verb opens the sentence; the same order in both languages |
| `account_auto_renew_risk` `Без автопродления доступ прервётся %1$s` | `Account_AutoRenewRisk` | date | date | - |
| `account_card_active_detail` `Осталось %1$s` | `Account_ActiveDetail` | days | plural | `plural_days` |
| `account_card_expired_detail` `Срок закончился %1$s` | `Account_ExpiredDetail` | date | date | - |
| `account_card_renew_price` `Продлить · %1$s` | `Account_RenewPrice` | price | money | - |
| `account_card_traffic_value` `%1$s из %2$s` | `Account_TrafficValue` | used, total | s, s | the unit is printed once, inside `total` |
| `account_devices_pair` `%1$d / %2$d` | `Account_DevicesPair` | used, limit | d, d | figure face, `tnum` |
| `account_sub_default_name` `Подписка %1$d` | `Account_SubscriptionN` | index | d | 1-based |
| `account_tariff_caption` `Тариф · %1$s` | `Account_TariffCaption` | plan name | s | server-supplied, may be any length; the row ellipsises |
| `auth_sent_magic_body` `Мы отправили ссылку на %1$s. Откройте её на этом устройстве.` | `Login_MagicSentHint` | email | s | R-23: «устройстве», not «телефоне» |
| `auth_sent_reset_body` | `Login_ResetSentHint` | email | s | - |
| `auth_sent_verify_body` | `Login_VerifyHint` | email | s | - |
| `common_count_pair` `%1$s · %2$s` | `Common_CountPair` | servers, providers | plural, plural | both arguments are pre-formatted plurals; one key for the Главная ledger and the Серверы header |
| `common_sub_active_until` `Активна до %1$s` | `Common_SubActiveUntil` | date | date | «14 августа», year omitted when it is this year |
| `devices_add_per_device` `%1$s за устройство` | `Devices_AddPerDevice` | price | money | - |
| `devices_count_line` `Привязано %1$d из %2$d` | `Devices_CountLine` | used, limit | d, d | C1: привязано, not подключено |
| `devices_count_line_unlimited` `Привязано %1$d, без ограничений` | `Devices_CountLineUnlimited` | used | d | - |
| `devices_platform_active` `%1$s · был активен %2$s` | `Devices_PlatformActive` | platform, when | s, s | `when` is relative («2 часа назад») |
| `devices_unlink_body` `Устройство «%1$s» будет отвязано от подписки.` | `Devices_UnlinkBody` | device name | s | «ёлочки» are part of the string, not the value |
| `err_rate_limited_in` `Повторить через %1$s` | `Common_TooManyRequestsIn` | remaining | s | the label of the disabled retry button; caller formats «43 с» / «1 мин» |
| `home_sub_expired` `Истекла %1$s` | `Home_SubExpired` | date | date | - |
| `home_sub_trial` `Пробный период до %1$s` | `Home_SubTrial` | date | date | - |
| `linking_tg_code` `Код: %1$s` | `Account_TgLinkCode` | code | s | figure face, selectable |
| `linking_unlink_title` `Отвязать %1$s?` | `Linking_UnlinkTitle` | method name | s | «Telegram», «Почту», «Google»: the caller supplies the accusative |
| `notif_connected` `Подключено · %1$s` | - | server name | s | Android only |
| `notif_ping_progress` `Осталось: %1$s` | - | count | plural | `plural_servers` |
| `pay_balance_have` `На балансе %1$s` | `Pay_BalanceHave` | amount | money | - |
| `pay_balance_short` `Не хватает %1$s` | `Pay_BalanceShort` | amount | money | - |
| `pay_by_other` `Оплатить через %1$s` | `Pay_ByOther` | method name | s | server-supplied |
| `pay_estimate` `Примерно %1$s` | `Pay_Estimate` | amount | money | - |
| `pay_subject_devices` `%1$s к подписке «%2$s»` | `Pay_SubjectDevices` | devices, subscription | plural, s | `plural_devices`; the quotes belong to the string |
| `pay_subject_renew` `Продление %1$s, %2$s` | `Pay_SubjectRenew` | plan, period | s, s | - |
| `pay_subject_upgrade` `Улучшение до %1$s, +%2$s` | `Pay_SubjectUpgrade` | plan, days | s, plural | `plural_days` |
| `server_selected_reconnect_prompt` `Переподключиться к «%1$s»?` | `Servers_ReconnectPrompt` | server name | s | the dialog title; the generic twin takes none |
| `servers_action_position` `Позиция %1$d из %2$d` | `Servers_ActionPosition` | index, total | d, d | 1-based |
| `servers_added` `Добавлено: %1$s` | `Servers_Added` | count | plural | `plural_servers` |
| `servers_del_dup_body` `Будет удалено %1$s. Останется по одному серверу из каждой пары.` | `Servers_DelDupBody` | count | plural | `plural_servers`; R-26 |
| `servers_del_invalid_body` `Будет удалено %1$s из тех, что не ответили при проверке.` | `Servers_DelInvalidBody` | count | plural | `plural_servers`; R-26 |
| `servers_deleted_count` `Удалено: %1$s` | `Servers_DeletedCount` | count | plural | `plural_servers`; the undo snackbar after any of the three bulk deletions (3.3) |
| `servers_group_found` `Найдено: %1$d из %2$d` | `Servers_GroupFound` | matched, total | d, d | - |
| `servers_menu_ping_scoped` `Проверить задержку: %1$s` | `Servers_MenuPingScoped` | scope | s | «все серверы», a provider name, or «найденные» |
| `servers_ping_unit` `%1$d мс` | `Servers_PingUnit` | latency | d | never negative, never > 9999; clamp before formatting |
| `servers_provider_auto` `Автообновление · %1$s` | `Servers_ProviderAuto` | interval | s | «каждый час», from the interval option set |
| `servers_refreshed` `Провайдер «%1$s» обновлён` | `Servers_Refreshed` | provider name | s | the participle agrees with «провайдер», not with the argument |
| `servers_row_cd` `%1$s, %2$s` | `Servers_RowCd` | name, state | s, s | screen-reader only; the comma is the pause |
| `set_about_system_info` `ОС: %1$s\nВерсия Android: %2$s\nАрхитектура: %3$s` | - | os, android version, arch | s, s, s | **Android only.** Three slots, no runtime: Android has no .NET |
| - | `About_SystemInfo` `ОС: {0}\nАрхитектура: {1}\nСреда выполнения: {2}` | os, arch, runtime | s, s, s | **PC only.** The one row in this register whose arguments differ in meaning by platform (3.6.13) |
| `set_about_update_found` `Доступна версия %1$s` | `About_UpdateFound` | version | s | - |
| `set_about_version` `Версия %1$s` | `About_VersionValue` | version | s | - |
| `set_assets_meta` `%1$s МБ · обновлена %2$s` | `Geo_SizeUpdated` | size, date | s, date | size carries a comma decimal, so it is a string, never a float |
| `set_data_saved` `Копия сохранена: %1$s` | `Backup_Saved` | file name | s | the **name**, never the full path |
| `set_perapp_except` `Кроме %1$s` | `Settings_PerAppExcept` | count | plural | `plural_apps` |
| `set_perapp_only` `Только %1$s` | `Settings_PerAppOnly` | count | plural | `plural_apps` |
| `set_routing_rules_n` `%1$d правил` | `Routing_RulesCount` | count | plural | `plural_rules`; the `%1$d правил` form is deleted |
| - | `Settings_UiScaleValue` `{0}%` | scale | d | **PC only** (3.6.12). Android has no interface-scale setting. If a Russian value ever needs a literal `%`, the Android resource writes it `%%` (5.1.4) |
| `strip_expiring` `Подписка заканчивается %1$s.` | `Strip_Expiring` | date | date | the only strip string with an argument |
| `upgrade_quote` `%1$s · +%2$s` | `Account_UpgradeQuote` | price, days | money, plural | `plural_days` |
| - | `Servers_SelectedCount` `Выбрано: {0}` | count | d | PC multi-select only (3.3) |
| - | `Tray_Tooltip` `departament · {0}` | status | s | PC only |
| - | `Window_HkConflict` `Уже назначено: {0}` | action name | s | PC only |

### 5.3 Strings whose specifiers are wrong today

| String | Defect |
|---|---|
| `account_payment_error_body`, `account_payment_error_body_nodetail` | Both are called with two arguments and neither text contains a slot. The code and the detail are silently discarded, so a payment failure explains nothing. Deleted (C4) |
| `memory_value` `%1$d MB · %2$s` | Latin unit inside a formatted string (C5). Deleted with the memory readout |
| `sub_days_left` `%1$s · %2$dd` | A Latin `d` standing in for «дн.», with no plural. Deleted |
| `Sub_Until` `до {0:dd.MM.yyyy}` | A date pattern inside a translated string: a translator editing the sentence can break the pattern. Deleted with R-1 |
| `devices_diag_http` `HTTP: %1$d` | A status code shown to a user (C4). Deleted |
| `connection_test_error` `Не удалось проверить подключение: %s` | Bare `%s` (5.1.1), and the value is the technical cause. Deleted |
| `set_perapp_except` `Кроме %1$d`, `set_perapp_only` `Только %1$d` | An integer printed with no noun to agree with. At n=1 the row reads «Кроме 1». They take `plural_apps` now (4.2) |
| `Account_ReferralCode` `Реф-код {0}` | The value belongs in the row's value slot, not inside the label (3.4.4) |
| `Geo_Failed`, `Backup_ExportError`, `Backup_ImportError`, `UrlSchemes_RegisterFailed`, `UrlSchemes_RemoveFailed` | No specifier, but each ends in a **deliberate trailing space** so that `+ ex.Message` can be concatenated. The space is part of the defect and goes with it |

### 5.4 How 5.2 stays true

The first edition's 5.2 opened «this table names every parameterised string in the product» and did
not, in both directions at once: `Servers_SelectedCount` and `server_selected_reconnect_prompt` were
in section 3 and missing from it; `servers_deleted_count` and `Settings_UiScaleValue` were in it and
in no approved section-3 row; and it listed an **Android** key `set_ui_scale_value` for a setting
3.6.1 marks Android `-`, on the very row it called «the classic aapt2 crash».

Those are not four mistakes. They are one: an index of a table, maintained by hand, in a document
where another wave edits the table. So the index is derived:

```bash
python3 docs/design2026/tools/check-specifiers.py --emit    # the row set, from section 3
python3 docs/design2026/tools/check-specifiers.py --check   # exit 1 on any asymmetry
```

`--check` fails when a section-3 string carries a specifier and 5.2 does not list it, when 5.2 lists
a key section 3 does not declare, and when any Android string uses a bare `%s`. It is in 8.1.

---

## 6. The work order

Ranked by **how visible the divergence is**, not by how hard it is to fix. Rank 1 is on the screen
every user sees at every launch; rank 4 is real but reachable only by someone looking for it. Each
item names the files, so another wave can execute it one file at a time.

### 6.1 Rank 1 - on the first screen, every session

| # | Divergence | Files | Register rows |
|---|---|---|---|
| **W-1** | **The navigation bar is in English on a Russian device.** `values/strings.xml` ships `bottom_nav_home` = `Home`, `bottom_nav_servers` = `Servers`, `bottom_nav_more` = `More`. `values-ru/` covers them, so the defect shows on any device whose language is not Russian - including a Russian user who picks «English» in the app's own picker and gets English chrome around Russian product copy | Android `res/values/strings.xml`, `res/menu/menu_bottom_nav.xml` (dead, delete) | 3.1 |
| **W-2** | **The most-looked-at number in the app is in English.** Six `"0 KB/s"` literals on PC (`HomeViewModel.cs:103,105,421,422`, `ConnectHeroView.axaml.cs:399,400,416,417`, `ConnectHeroView.axaml:653,678`) and `speed_zero` on Android, plus `Utils.HumanFy`, which is EN-invariant with a dot decimal. C5 moves the unit into the column label on both | PC `Common/Utils.cs:162`, `ViewModels/HomeViewModel.cs`, `Views/ConnectHeroView.axaml(.cs)`; Android `extension/_Ext.kt:82,89`, `res/values/strings.xml` | 3.2, C5 |
| **W-3** | **The two clients name the disconnected state differently** - «Отключено» on one surface, «Не подключено» on another, and PC declares both | Android `res/values-ru/strings.xml`; PC `Common/L.Home.cs`, `L.Shell.cs` | 3.2, R-20 |
| **W-4** | **Главная's empty state breaks the terminology lock twice**: «Подписок пока нет» / «Добавьте подписку, чтобы появились серверы.» where the user is missing **servers** and adds a **провайдер**. PC's equivalent is already 9.5-verbatim | Android `res/values*/strings.xml` (`home_empty_title`, `home_empty_subtitle`) | 3.8 |
| **W-5** | **Nothing the PC user does is confirmed.** `SendMsgViewRequested` has no live subscriber, so «Скопировано», «Устройство отвязано», «Подписка продлена», «Тариф улучшен», «Устройства добавлены» and every failure in those flows are invisible - about 361 strings, correctly written, that reach nobody | PC `Views/MainWindow.axaml.cs:330,1836-1843`, `Views/MainWindow.axaml:624` | C3 |
| **W-6** | **All 52 Android errors arrive as a `Toast` with no action**, which 1.4.8 forbids outright. Three recovery affordances exist in the whole app, and one of them is on a screen with no entry point | Android `util/*`, every `ui/*Activity.kt` calling `toastError` | C3, 3.7 |

### 6.2 Rank 2 - inside the first minute of use

| # | Divergence | Files | Register rows |
|---|---|---|---|
| **W-7** | **A device is «удалено» on Android and «отвязано» on PC.** Six strings, one action, and Android's verb collides with the destructive verb for a server | Android `res/values/strings_devices.xml`, `ui/DeviceManagementActivity.kt` | 3.4.6, R-3 |
| **W-8** | **«Подписка» means two things on Android.** 48 live strings carry the root and about half of them mean **провайдер**, including the whole provider kebab, the settings auto-update row, and the delete confirm. PC is already correct throughout | Android `res/values*/strings.xml`, `strings_provider.xml`, `strings_settings_hub.xml` | 3.3, 3.6.1, C1 |
| **W-9** | **Four wordings for one expiry date.** PC alone declares `Account_ValidUntil`, `Account_ActiveUntil`, `Account_ExpiresUntil` and `Sub_Until`; Android says «Действует до» | PC `Common/L.Account.cs`, `L.Servers.cs`; Android `res/values/strings_account.xml` | 3.4.2, R-1 |
| **W-10** | **The trial chip shouts.** `account_trial_badge` = «ПРОБНЫЙ» is the only ALL-CAPS Cyrillic string in the product (0.4.3 bans it); PC says «Пробный период» | Android `res/values/strings_account.xml:44`; PC `Common/L.Account.cs` | 3.4.2, R-6 |
| **W-11** | **The PC sign-in form is three unlabelled boxes.** Email, password and repeat-password carry their names only as watermarks, so the moment the user types, the register form has no labels at all - and the two masked fields become indistinguishable | PC `Views/LoginView.axaml:351,370,417`; the fix pattern is already in `Views/SettingsView.axaml:481-509` | C7, 3.5 |
| **W-12** | **PC has no «Привязать Telegram» string at all**, only «Привязать», although 9.3 locks the phrase and 0.4.9 makes the CTA an explicit owner request. The «Telegram не привязан» empty state is missing too | PC `Common/L.Account.cs`; `Views/AccountView.axaml` | 3.4.4, 3.8 |
| **W-13** | **PC's linking rows signal "linked" with a check glyph and no word.** `Account_Linked` («Привязан») exists and is never rendered; 14.7 forbids colour or icon as the only signal | PC `Views/AccountView.axaml`, `Common/L.Account.cs` | 3.4.8 |

### 6.3 Rank 3 - the surfaces a user reaches when something has gone wrong

| # | Divergence | Files | Register rows |
|---|---|---|---|
| **W-14** | **Eleven PC error strings stop after "what happened."** In every one of those cases Android already ships the complete 9.4 sentence, so this is a copy-paste with a keyed home | PC `Common/L.Common.cs`, `L.Buy.cs`, `L.Account.cs` | 3.7 |
| **W-15** | **Machine words on a user's screen.** `devices_diag_http` («HTTP: %1$d»), two diagnostic dialogs that print the raw server response and ask for a screenshot, `account_payment_error_body` («HTTP %1$s»), `connection_test_error` with the technical cause, and five PC sites that concatenate a .NET exception onto a Russian sentence | Android `res/values/strings_devices.xml`, `strings_account.xml`, `ui/AccountFragment.kt:572`, `ui/BuyTariffActivity.kt:530`; PC `Views/GeoFilesPage.axaml.cs:89`, `BackupPage.axaml.cs:51,84`, `UrlSchemesPage.axaml.cs:110,144` | C4, 3.7 |
| **W-16** | **Offline is not a designed state on either platform.** Zero of the three strings 9.6 requires exist anywhere | both | 3.9 |
| **W-17** | **The search-empty state does not exist on PC**, although the search box does. Android has the pieces but not the state | PC `Views/CompactServersView.axaml:108`; Android `strings_menu_actions.xml` | 3.8 |
| **W-18** | **19 Android dialogs confirm with «ОК»** - a word 9.2 bans by name - and one of them is titled «Open source licenses» in English on a Russian screen | Android, the 19 call sites listed in 3.10; `ui/AboutActivity.kt:31,33` | 3.10, R-15 |
| **W-19** | **Counts are ungrammatical at every value except "many."** Zero `<plurals>` on Android; «1 серверов · 1 провайдеров» ships today | Android `res/values/strings_common.xml` (new); PC `Common/Plural.cs`, `L.Common.cs` | section 4 |
| **W-20** | **Three currency formatters with three behaviours**, two of which print the raw currency code, one of which uses `Locale.US` so kopecks arrive with a dot. One account can read `1290 ₽` on the card and `1290 USD` in the history | Android `ui/AccountFragment.kt:862-868`, `ui/BuyTariffActivity.kt:639-646`, `ui/adapter/PaymentsAdapter.kt:91-99`; PC three `CurrencySymbol` properties | C6, R-11 |

### 6.4 Rank 4 - correctness debt that is not yet on screen

| # | Divergence | Files | Register rows |
|---|---|---|---|
| **W-21** | **22 user-facing strings are hardcoded in Kotlin**, four of them duplicated verbatim across two files and one across three, plus two literals in layouts (`view_toolbar.xml:70` «Назад», spoken on every sub-page; `layout_home_account.xml:71` `✕`, which TalkBack reads as "multiplication sign") | Android `ui/MainActivity.kt`, `ScScannerActivity.kt`, `SubEditActivity.kt`, `ProviderSettingsActivity.kt`, `BuyTariffActivity.kt`, `AccountFragment.kt`, `dto/entities/ServerAffiliationInfo.kt` | 3.3, 3.6.7 |
| **W-22** | **PC hardcodes the whole tray menu** (`App.axaml:39-45`) and the interface-scale row and its tooltip (`SettingsView.axaml:776,792`), so none of them follows the language switch. The tooltip also carries two em-dashes and a U+2212 | PC `App.axaml`, `Views/SettingsView.axaml` | 3.11, 3.6.12 |
| **W-23** | **The settings tree's copy** - 230 rows across the hub and 13 sub-pages, most of them new keys - lands with the settings rebuild. Four rows in `12-settings.md` 11 are corrected here first (section 7) | Android `res/values/strings_settings.xml` (new); PC `Common/L.Settings.cs` | 3.6 |
| **W-24** | **Dead and duplicate copy.** 339 dead Android keys (260 of them genuine rot), 83 more locked behind screens with no entry point, 14 dead `L` keys, ~346 `ResUI` keys wired into windows the shell never opens, and 95 duplicate groups on Android plus 18 on PC | both | C2 |
| **W-25** | **The locale layout.** `values/` becomes Russian and `values-en/` is created from this register's English column. **Seven** stale locale folders are deleted with the upstream keys they translate, named: `values-ar`, `values-bn`, `values-bqi-rIR`, `values-fa`, `values-vi`, `values-zh-rCN`, `values-zh-rTW`. The first edition named five and left `values-fa/` and `values-bqi-rIR/` standing, which gives a Persian device Persian upstream copy for the keys those two files cover and Russian for everything else | Android `res/values*/` | 0.4 |
| **W-26** | **`values-ru/` is a divergence trap of 768 entries** - 393 already byte-identical to their `values/` twin, 375 the Russian of a key whose `values/` twin is English, 0 that `values/` does not declare. Every one of them is a place where the next copy edit lands in one file while the Russian device reads the other | Android `res/values-ru/*.xml` | 0.4 |
| **W-27** | **`values-ru/` is deleted**, in the same commit that makes `values/` Russian and never before it, so no intermediate build ships English chrome. W-26 diagnoses it; this is the item that closes it, and the first edition had no such item at all | Android `res/values-ru/` | 0.4 |
| **W-28** | **`values-en/` cannot be created from an unescaped English column.** 56 apostrophes and 4 double quotes in the first edition's English cells are each a hard aapt2 error; the whole existing tree contains two escaped apostrophes, so nothing in the repo would have caught it. The column is contraction-free now, and 8.1 greps for the survivors | Android `res/values-en/` (new), and this file's English column | 5.1.1 |
| **W-29** | **Nothing explains a permission before it is requested.** Android 13+ raises a `POST_NOTIFICATIONS` request and the VPN consent dialog appears on first connect; the product has copy for neither, only for the refusal | Android `ui/MainActivity.kt`, `res/values/strings_service.xml` (new) | 3.11, R-25 |
| **W-30** | **Способы входа only ever adds.** Telegram, Email and Google each offer «Привязать» and nothing unlinks a method or changes a password, so a user who linked the wrong Telegram account has no route at all | both, the linking screen | 3.4.8 |
| **W-31** | **Two bulk deletions have neither a confirm nor a count.** «Удалить дубликаты» and «Удалить недоступные» delete an unbounded number of servers on one tap; only «Удалить все серверы» confirms | both, the Серверы header overflow | 3.3, 3.10, R-26 |

---

## 7. Where this register overrules a spec, and where the law itself is defective

### 7.1 Specs corrected

`00-rules.md` 0.1: a spec in `docs/design2026/` that contradicts the law is a bug. Fifteen such rows
were found while building this register. Each is corrected above; each is listed here so the spec's
author can see the change rather than discover it.

| # | Spec | Its row | Corrected to | Rule |
|---|---|---|---|---|
| 1 | `12-settings.md` 11.1 | `set_mux_count` «Число **соединений** Mux» | «Число **подключений** Mux» | 9.3 / R-2. PC already ships the correct form |
| 2 | `12-settings.md` 4.4, 11.1 | Group «Подписки», row «Автообновление **подписок**» | Group «Провайдеры», row «Автообновление **провайдеров**» | 9.3 locks провайдер for a feed. The spec's own justification («the one place the two senses touch») is the exception the lock forbids / R-4 |
| 3 | `12-settings.md` 11.1 | `set_providers` «Провайдеры», inside a group also called «Провайдеры» | «Настройки провайдеров», the string both platforms already ship | C2: a group header and a row inside it never carry the same word |
| 4 | `12-settings.md` 11.2 | `set_dns_fakeip_hint` «Ускоряет **соединение**…» | «Ускоряет **подключение**…» | R-2 |
| 5 | `12-settings.md` 11.2 | `set_adv_insecure` «Разрешать небезопасные **соединения**» | «…небезопасные **подключения**» | R-2 |
| 6 | `12-settings.md` 11.2 | `set_fragment_note` «…если **соединение** не устанавливается» | «…если **подключение** не устанавливается» | R-2 |
| 7 | `12-settings.md` 11.2 | `set_latency_on_update` «после обновления **подписки**», `set_providers_ua_hint` «при обновлении **подписки**» | «…**провайдера**» in both | 9.3 / R-4 |
| 8 | `13-start-screen.md` 13.1 | `home_sub_active` «**Действует** до %1$s» | «**Активна** до %1$s» | R-1: it must agree with the health chips «Активна» / «Истекает» / «Истекла», which the same screen renders |
| 9 | `23-account-rework.md` 8.2 | `account_card_buy_tariff` «Купить тариф», `account_card_pick_tariff` «Выбрать тариф» | one string, «Купить» | 9.3 locks «Купить» / R-5 |
| 10 | `41-copy-inventory-pc.md` 6.1 #11 | "pick `buy_empty`'s form for both" - a title and a line welded into one string | the 9.5 trio: «Тарифов пока нет» / «Список обновляется автоматически, загляните позже.» / «Повторить» | 9.5 / R-7 |
| 11 | `23-account-rework.md` 8.2, `13-start-screen.md` 13.1, and **this register's own first edition** | «Купить **подписку**» as the row, the gate and the screen title | «Купить **тариф**», and «Покупка тарифа» in the history | 9.3 defines тариф as the paid plan and подписка as the service you then have. You buy a plan / R-5 |
| 12 | `12-settings.md` 11.2, and this register's first edition | «Ядро» as the section header **and** as the row inside it | «Ядро и журнал» / «Активное ядро» | C2, and section 7.1 item 3 states the same rule for «Провайдеры» |
| 13 | `12-settings.md` 11.1 | `set_mode_both` «Вместе» | «VPN и прокси» | 9.2: a value in a row called «Режим подключения» must name the mode, not the fact that there are two |
| 14 | `12-settings.md` 11.5 | `set_data_intro` «Все настройки, **подписки** и серверы…», `set_data_reset_hint` «Серверы и **подписки** останутся» | «провайдеры» in both | 9.3 / R-4. Both English halves already said «provider» |
| 15 | `16-servers.md` 8.4 | «Удалить недоступные» and «Удалить дубликаты» with no confirm, no count and no undo | a confirm with the count as a plural, and the shared undo snackbar | `00-rules.md` 7.5 / R-26 |

**One conflict this register does not resolve, because the owner already did.** `16-servers.md` 18
lists "Desktop has a Серверы entry in the rail" as an acceptance box, while `11-app-structure.md`
2.0 records the owner's decision of 2026-07-26 that the desktop must **not** gain a Серверы tab.
The owner outranks both documents (0.1.1). Every `Servers_*` key in 3.3 therefore exists on PC and
renders inside Главная. `Nav_Servers` is not added to `L.Shell.cs`, and no string anywhere sends the
desktop user to a Серверы tab - which is why 3.2's detail line lost its destination.

### 7.2 Errata against `00-rules.md` section 9

This register extends the law; it does not overrule it (0.1.2 beats 0.1.3). But it may not silently
**propagate** a defect either, and two rows of 9.4 are defective against 9.3 and C3 - two rules in the
same document. Where one part of the law breaks another, the explicit lock wins over the incidental
example: 9.3 opens «Changing one of these is a product decision, not a copy edit», while 9.4's table
is a set of worked examples of a formula. Both rows below are raised as errata for whoever owns
`00-rules.md`; until that edit lands, this register's sentence is the one that ships, and section 3
marks those rows **9.4!**.

| Law row | The defect | Ships as | Decided by |
|---|---|---|---|
| 9.4 «Subscription update failed» = «Не удалось обновить **подписку**. Проверьте ссылку **провайдера** и повторите.» | Two nouns for one object inside one sentence, under a key literally named `err_provider_refresh`. 9.3 locks **провайдер** for a subscription URL that yields servers and warns that **подписка** is ambiguous there; this sentence uses both, one clause apart | «Не удалось обновить провайдера. Проверьте его ссылку и повторите попытку.» | 9.3's lock, C1, R-21 |
| 9.4 «Device limit reached» = «Достигнут лимит устройств. Отвяжите одно из устройств **в разделе «Устройства»**.» | Names the noun twice, and names the destination in prose while C3 requires the surface to carry a control that goes there. The sentence and the button then say the same thing twice | «Достигнут лимит устройств. Отвяжите одно из них.» plus the «Устройства» action | C3, 9.2, R-21 |

Two further observations, raised but **not** overruled, because in each case the law is right and the
register was wrong: 9.5's «Купите тариф» / «Купить» is the correct buy vocabulary and R-5 now agrees
with it, and 9.2's six-word cap on row subtitles is real - two 7-word subtitles inherited from
`12-settings.md` are cut in 3.6.1 and 3.6.10.

---

## 8. Enforcement

Three layers, in this order: **greps** catch what a reviewer would catch, **scripts** keep this file
true to the two codebases, and **the build** fails on the class of defect that only appears at
runtime. The first edition had one layer and one false claim about a second.

### 8.1 Mechanical checks

Everything here must return nothing, or exit 0. Run it before claiming a copy change is done.

```bash
# ---- the two scripts that keep this file honest ----
python3 docs/design2026/tools/derive-copy-delta.py --check   # Текст / Ключи vs both codebases
python3 docs/design2026/tools/check-specifiers.py --check    # 5.2 vs section 3, both directions

# ---- Android, from /home/user/dp/V2rayNG/app/src/main/res ----

# 9.2: dashes in shipped copy (the literal form; the PCRE \x{2014} form fails in this environment)
grep -rn -e '—' -e '–' values*/strings*.xml
# 9.2: three dots where the single ellipsis character belongs
grep -rn '\.\.\.' values*/strings*.xml
# 9.1: exclamation marks
grep -rnE '>[^<]*!<' values*/strings*.xml
# 0.4.3: ALL-CAPS Cyrillic
grep -rnE '>[А-ЯЁ]{4,}<' values*/strings*.xml
# C1: the banned nouns
grep -rn 'соединени\|коннект\|Профиль\|Личный кабинет\|абонемент\|нода\|узел\|девайс\|аватар\|слот' values*/strings*.xml
# C1: «удалить» applied to a device
grep -rn 'алить устройство\|алено устройство' values*/strings*.xml
# C1: the tunnel's word inside a Devices string
grep -rn 'подключ' values*/strings*.xml | grep -E 'name="devices_'
# 9.3: «подписк» inside a provider string
grep -rn 'подписк' values*/strings*.xml | grep -iE 'ps_|sub_|provider|обнов'
# 9.3 / R-4: «подписк» inside a backup or data string
grep -rn 'подписк' values*/strings*.xml | grep -E 'name="(set_data_|set_webdav_|backup)'
# 9.3 / R-5: the wrong object for «купить»
grep -rn 'упить подписку\|упите подписку\|окупка подписки' values*/strings*.xml
# C8: the brand lowercase inside a sentence (every hit is eyeballed for sentence position)
grep -rn 'departament' values*/strings*.xml | grep -v 'departament\.site'
# R-23: a desktop-bound string that describes a phone
grep -rn 'телефон\|галере\|коснитесь\|удерживайте' values*/strings*.xml
# C5: Latin units
grep -rnE '>[^<]*\b(KB|MB|GB|TB|KB/s|MB/s)\b' values*/strings*.xml
# C4: codes and machine words
grep -rn 'HTTP\|скриншот\|UID\|Ответ сервера\|\.NET' values*/strings*.xml
# R-15: the OK button
grep -rn 'android.R.string.ok' ../java/
# user-facing literals in Kotlin (Cyrillic in a string literal outside a comment)
grep -rnE '"[^"]*[А-Яа-яЁё][^"]*"' ../java/com/v2ray/ang/ui/ | grep -v '^\s*//'
# literals in layouts
grep -rnE 'android:(text|contentDescription|hint)="[^@]' layout/
# 5.1.1: an unescaped apostrophe or double quote anywhere, and especially in values-en/
grep -rnE "[^\\\\]'" values*/strings*.xml
grep -rnE '[^\\]"' values-en/strings*.xml
# 5.1: a bare %s where a positional specifier belongs
grep -rnE '%[sd]' values*/strings*.xml | grep -v '%[0-9]\+\$'
# D-S9: no Cyrillic may reach the English locale
grep -rnP '[\x{0400}-\x{04FF}]' values-en/strings*.xml
# section 4: the plural sets exist
grep -c '<plurals' values/strings_common.xml     # expect 7
# 0.4: the stale locales and the redundant Russian folder are gone
for d in values-ar values-bn values-bqi-rIR values-fa values-vi values-zh-rCN values-zh-rTW values-ru; do
  test ! -d "$d" || echo "still present: $d"
done

# ---- PC, from /home/user/v2rayN/v2rayN/v2rayN.Desktop ----

grep -rn -e '—' -e '–' Common/L.*.cs
grep -rn '\.\.\.' Common/L.*.cs
grep -rn 'соединени\|коннект\|Профиль\|Личный кабинет\|аватар\|слот' Common/L.*.cs
grep -rn 'телефон\|галере\|коснитесь\|удерживайте' Common/L.*.cs
grep -rn 'упить подписку\|упите подписку' Common/L.*.cs
grep -rn 'departament' Common/L.*.cs | grep -v 'departament\.site'
grep -rnE '"[^"]* "' Common/L.*.cs                 # the five trailing spaces that exist for + ex.Message
grep -rn '+ ex.Message' Views/
grep -rnE '(Text|Content|Header|Watermark|ToolTip\.Tip)="[^{][^"]*[А-Яа-яЁё]' --include=*.axaml .
grep -rn 'KB/s\|MB/s\|HumanFy' Views/ ViewModels/
grep -rn 'x:Static resx:ResUI' Views/              # upstream copy reaching a live view
grep -rn 'PluralWord' Common/L.cs                  # 4.3: the accessor plural_days_word needs
```

### 8.2 Build gates

**aapt2 does not compare format arguments between locales.** It compiles each `strings.xml`
independently; a `%1$s` in `values/` against a `%1$d` in `values-en/` builds cleanly and throws
`IllegalFormatConversionException` on the screen that formats it. The first edition's acceptance box
said «the build fails otherwise, which is the point», and nothing was wired to make that true - so
the one guard the register relied on to catch the exact class of defect section 5 exists to prevent
was attached to nothing.

The check is **Android Lint**, and every one of these is a *warning* by default. Promote them and
turn on `abortOnError`, in `V2rayNG/app/build.gradle.kts`:

```kotlin
android {
    lint {
        error += setOf(
            "StringFormatMatches",   // arg types disagree between a string and its translation
            "StringFormatCount",     // arg counts disagree
            "StringFormatInvalid",   // the format itself is malformed
            "MissingQuantity",       // a <plurals> without the required `other` item
            "ImpliedQuantity",       // a quantity string used without passing the count
            "MissingTranslation",    // a key in values/ with no values-en/ twin
            "ExtraTranslation",      // a key in values-en/ that values/ does not declare
        )
        abortOnError = true
    }
}
```

`aapt2` still owns the two failures it does own, and they are worth naming correctly: an **unescaped
apostrophe** («Apostrophe not preceded by \\») and **two bare `%s` in one string** («multiple
substitutions specified in non-positional format»). A *single* bare `%s` compiles and fails at
runtime, which is why 5.1.1 and the grep exist.

5.2 is a **design aid**, not the enforcement. `check-specifiers.py --check` keeps it true to section
3; Lint keeps the resources true to each other.

### 8.3 Acceptance

A box that cannot be ticked honestly means the copy pass is not done.

**The register itself**

- [ ] `derive-copy-delta.py --check` exits 0: every Текст and Ключи cell agrees with both codebases.
- [ ] `check-specifiers.py --check` exits 0: 5.2 and section 3 name the same parameterised strings.
- [ ] No row in section 3 has a `-` in its Русский cell, and no concept in section 3 lacks an
      approved string on both platforms or a stated reason for existing on one.
- [ ] Every `A`, `P` or `AP` in the Текст column has a reason in the note under its table.
- [ ] The register was updated **in the same commit** as any string that changed. A string edited in
      the code and not here is a defect, in the code and in this file.

**The strings**

- [ ] Every string a user can see on either client appears in section 3 - established by **walking
      both apps screen by screen**, not by walking the inventories. The inventories list what exists;
      a screen shows what is missing. Five strings were found this way and only this way: the share
      chooser title, the notification channel description, the three non-connected notification
      texts, the update-check results, and every action on Способы входа except «Привязать».
- [ ] For every row whose Ключи cell reads `A+` or `P+`, the key was actually created.
- [ ] For every row whose Ключи cell reads `A←old`, `old` was **renamed**, not copied: `old` no
      longer exists.
- [ ] No concept has two keys on one platform, and no key has two concepts. The two declared
      exemptions are in 3.1.2 and nothing has been added to them.
- [ ] Every error string on screen has a recovery control within reach of the same thumb, and that
      control can actually perform the recovery the sentence names (C3).
- [ ] Every empty state has a title, a line and either an action or a stated reason for having none.
- [ ] Offline shows the bar, not an error.
- [ ] Every count-bearing string reads correctly at 1, 2, 5, 11, 21 and 101.
- [ ] `grep -c '<plurals' values/strings_common.xml` returns 7.
- [ ] The word «соединение» does not appear in either client, and «подключено» does not appear in a
      Devices string.
- [ ] The word «OK» does not appear as a button on either client.
- [ ] No HTTP status, exception text, response body or runtime name reaches a screen.
- [ ] `₽` is the only currency symbol either client can print.

**The fit**

- [ ] Every Русский cell was **measured** in the control its Экраны column names, at 13 sp on a
      320 dp screen and at font scale 200 %, and neither truncates nor overflows (1.1, 5.3). Four
      strings failed this in the first edition and were rewritten: `auth_2fa_label`,
      `servers_add_link_hint`, `home_detail_pick_server`, `account_pay_unconfirmed`.
- [ ] No row subtitle exceeds six words (9.2).
- [ ] `values-en/` compiles, and contains no Cyrillic.

---

## 9. What this register does not cover

- **`ResUI`** - the 570 upstream keys in `ServiceLib/Resx/`. About 346 of them are wired into
  windows the Departament shell never opens, and eight are still reachable
  (`41-copy-inventory-pc.md` 4.2: the delete-server confirm, the two dialog buttons, the sudo trio,
  a group-name suffix and a filter head). Those eight are covered in 3.10 and must move into `L`;
  the rest are dead copy whose fate is a separate decision (`41-copy-inventory-pc.md` 10 P3 #25).
  Until that decision lands, one accidental navigation re-introduces upstream's voice wholesale -
  «Операция не удалась, проверьте и повторите попытку», «Недопустимая конфигурация», «Группа {0}
  имеет циклическую зависимость на дочерний узел {1}».
- **The server editor** - Android's `strings_editors.xml` was being written while the inventory was
  being taken (it grew by 1 255 bytes and 16 keys inside 20 minutes). Its screens are not built yet.
  When they are, their copy is added here first.
- **Protocol and transport tokens** - `VLESS`, `VMess`, `Trojan`, `Shadowsocks`, `Hysteria2`,
  `WireGuard`, `Reality`, `TCP`, `WS`, `gRPC`, `QUIC`, `SNI`, `ALPN`. They are identifiers, they are
  not translated, and 1.4.10 allows them. Two are **not** identifiers and are corrected in 3.3:
  `NONE` and `CUSTOM`, which `ProfileDisplay.cs` renders as ALL-CAPS English words on every server
  row without a TLS layer.
- **Backend-supplied text** - plan names, payment-method names, provider names, server remarks,
  Telegram display names. The product renders them as plain text and never as markup
  (`00-rules.md` 1.4.4 permits emoji only inside such content). Their wording is the operator's, not
  this file's.
