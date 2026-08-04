# Где что лежит в коде прототипа

Файл прототипа — `Прототип departament.dc.html`, 1133 строки, один файл. Всё внутри: разметка сверху, логика внизу в `class Component`.
Стилей во внешних файлах нет — размеры и цвета стоят прямо в атрибуте `style` каждого элемента. Ищите по тексту.

## Как искать

| Что нужно | Что грепать |
| --- | --- |
| Отклик на нажатие | `.row:active`, `.btn:active`, `.ico:active`, `.opt:active` |
| Цвета темы | `const THEMES` |
| Акценты | `const ACC` |
| Иконки (pathData) | `const ICONS` |
| Состав настроек | `const HUB` |
| Списки выбора | `const MODES`, `DNS`, `PINGS`, `STRATS`, `IVS`, `SORTS`, `THEMES_OPTS`, `LANGS`, `AUTOUP` |
| Подэкраны | `const SUBS` внутри `buildSub` |
| Тарифы | `buildBuy` |
| Потоки прогрузки | `runFlow`, `buildFlow` |
| Появление главной | `en:` внутри `renderVals` |
| Список серверов | `servers:` внутри `renderVals` |
| Переключатель | метод `sw(on)` |
| Окошко выбора | метод `pop(key)` |
| Начальный экран | `sc-if value="{{ onboard }}"` |

---

## Отклик на нажатие — блок `<style>` в начале файла

```css
.row{display:flex;align-items:center;min-height:56px;padding:10px 16px;box-sizing:border-box;cursor:pointer;
     will-change:transform;backface-visibility:hidden;
     transition:transform 200ms cubic-bezier(.34,1.2,.64,1),background-color 200ms cubic-bezier(.25,1,.5,1)}
.row:active{transform:scale(.975);background:var(--pressBg);
     transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}

.card:active{transform:scale(.975);background:var(--pressBg);transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}

.btn{cursor:pointer;will-change:transform;backface-visibility:hidden;
     transition:transform 200ms cubic-bezier(.34,1.25,.64,1)}
.btn:active{transform:scale(.965);transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}

.ico{cursor:pointer;border-radius:50%;will-change:transform;backface-visibility:hidden;
     transition:transform 200ms cubic-bezier(.34,1.25,.64,1)}
.ico:active{transform:scale(.88);transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}

.opt:active{transform:scale(.965);background:var(--pressBg)!important;transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}
.pill:active{transform:scale(.965);background:var(--pressBg);transition-duration:70ms;transition-timing-function:cubic-bezier(.4,0,.6,1)}
```

`will-change:transform` + `backface-visibility:hidden` — против дёрганья текста. В Android это `setLayerType(LAYER_TYPE_HARDWARE)` на время анимации.

## Ключевые кадры

```css
@keyframes rise{from{opacity:0}to{opacity:1}}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes pop{from{opacity:0}to{opacity:1}}
@keyframes sonar{from{transform:scale(1);opacity:1}to{transform:scale(1.6);opacity:0}}
@keyframes glowSoft{0%,100%{opacity:1}50%{opacity:.68}}
@keyframes slideL{from{opacity:0;transform:translateX(-44px)}to{opacity:1;transform:none}}
@keyframes dropIn{from{opacity:0;transform:translateY(-22px)}to{opacity:1;transform:none}}
@keyframes bloomIn{from{opacity:0;transform:scale(.7)}to{opacity:1;transform:none}}
@keyframes liftIn{from{opacity:0;transform:translateY(26px)}to{opacity:1;transform:none}}
```

---

## Корень экрана

```html
<div style="width:360px;height:800px;border-radius:26px;overflow:hidden;
            box-shadow:0 18px 44px rgba(0,0,0,.5);position:relative;
            background:var(--bg);color:var(--fg);
            font-family:Roboto,system-ui,sans-serif;display:flex;flex-direction:column">
```

360×800 = 360dp × 800dp. Скругление 26px и тень — рамка телефона в макете, в приложении их нет.

---

## Кнопка подключения

Найти по `width:214px;height:214px`.

```
Внешний габарит      214dp
  кольцо 1            inset 0,  border 1.5dp, var(--ring1)
  кольцо 2            inset 10dp, border 1.5dp, {{ c.ringMid }}
  кольцо 3 (активное) inset 22dp, border 2dp,  {{ c.ringB }}, animation {{ c.glow }}
  свечение            radial-gradient, opacity {{ c.bloomOp }}, переход 400ms
  диск                150dp, background var(--surf)
  щит                 68dp, две SVG наложены, opacity {{ c.outlineOp }} / {{ c.filledOp }}
```

Дуга при подключении — SVG 214×214, `animation:spin 1100ms linear infinite`:

```html
<circle cx="107" cy="107" r="85" fill="none" stroke="{{ c.acc }}" stroke-width="2"
        stroke-linecap="round" stroke-dasharray="104 430" />
```

Длина окружности при r=85 → 534. `104 430` = дуга 104 + пропуск 430, то есть примерно 19% круга. **Одна дуга, не несколько.**

Сонар при подтверждении:

```html
<div style="position:absolute;inset:22px;border-radius:50%;border:2px solid {{ c.acc }};
            animation:sonar 600ms cubic-bezier(.22,1,.36,1) both"></div>
```

Состояния собираются в `renderVals` → объект `c`:

```js
c: {
  acc, sweep: s.conn === 'connecting', pulse: s.pulse,
  glow: s.conn === 'on' ? 'glowSoft 5500ms ease-in-out infinite' : 'none',
  bloomOp: s.conn === 'off' ? '0.35' : '1',
  outlineOp: s.conn === 'on' ? '0' : '1',
  filledOp: s.conn === 'on' ? '1' : '0',
  pill: { off: 'Не подключено', connecting: 'Подключаемся…', on: 'Подключено' }[s.conn],
  ...
}
```

Цикл подключения — `toggleConn`:

```js
this.setState({ conn: 'connecting' });
this.later(() => this.setState({ conn: 'on', pulse: true }), 2200);
this.later(() => this.setState({ pulse: false }), 2900);
```

---

## Строка сервера

Найти по `servers:` в `renderVals` и по `sc-for list="{{ servers }}"` в разметке.

```html
<div onClick="{{ sv.pick }}"
     style="margin:2px 16px 0;display:flex;align-items:center;min-height:56px;
            padding:8px 12px;box-sizing:border-box;border-radius:20px;cursor:pointer;
            background:{{ sv.bg }};border:1.5px solid {{ sv.bd }};box-shadow:{{ sv.divider }};
            animation:{{ sv.anim }};
            transition:transform 160ms cubic-bezier(.25,1,.5,1),
                       background-color 220ms cubic-bezier(.25,1,.5,1),
                       border-color 220ms cubic-bezier(.25,1,.5,1)"
     style-active="transform:scale(.98);transition-duration:90ms">
```

Геометрия одна на все строки. Меняются только три значения:

```js
bg: s.srvSel === i ? (m ? 'rgba(255,255,255,.08)' : 'rgba(76,141,255,.12)') : 'transparent',
bd: s.srvSel === i ? acc : 'transparent',
divider: s.srvSel === i || i === 0 ? 'none' : 'inset 0 1px 0 var(--line)',
```

Разделитель — внутренняя тень, **не** `border-top`. Граница смещает разметку и разрывает рамку выбранной строки.

Внутри строки:

```
флаг      28dp, border-radius 12dp, background var(--surf3), font-size 14
название  font:700 16px 'Space Grotesk'
чипы      padding:3px 9px, border-radius 8px, font:500 11px
транспорт font:400 12px, color var(--fg2), обрезается многоточием
пинг      font:700 12px, font-feature-settings:'tnum', flex:none
```

---

## Строка настроек

Найти по `sc-for list="{{ sec.rows }}"`.

```html
<div onClick="{{ r.click }}" class="row"
     style="border-top:1px solid {{ r.sep }};border-radius:{{ r.rad }};
            position:relative;z-index:{{ r.rowZ }}">
  <div style="width:40px;height:40px;border-radius:12px;background:var(--tile);
              display:flex;align-items:center;justify-content:center;flex:none">
    <svg viewBox="{{ r.vb }}" width="22" height="22" fill="var(--tileFg)"><path d="{{ r.icon }}" /></svg>
  </div>
  <div style="flex:1 1 auto;min-width:76px;overflow:hidden;margin-left:16px;margin-right:{{ r.gap }}">
    <div style="font:400 14px/1.35 Roboto,sans-serif">{{ r.name }}</div>
    <div style="margin-top:3px;font:400 12.5px/1.35 Roboto,sans-serif;color:var(--fg2)">{{ r.sub }}</div>
  </div>
  ...
</div>
```

Скругление крайних строк и слой над соседями:

```js
rad: sec[1].length === 1 ? '19px'
   : (i === 0 ? '19px 19px 0 0'
   : (i === sec[1].length - 1 ? '0 0 19px 19px' : '0')),
rowZ: pop && on(pop) ? '30' : 'auto',
gap: (isToggle || pop || r[6]) ? '12px' : '0px',
```

Карточка секции **без** `overflow:hidden` — иначе окошко выбора срезается:

```html
<div style="margin:0 16px;background:var(--surf);border:1px solid var(--line);border-radius:20px">
```

---

## Окошко выбора

Найти по `clip-path:{{ r.popClip }}`.

```html
<div style="position:absolute;top:48px;right:8px;width:{{ r.popW }};padding:6px;
            border-radius:16px;background:var(--popBg);border:1px solid var(--outline);
            box-shadow:0 20px 44px rgba(0,0,0,.8);
            clip-path:{{ r.popClip }};opacity:{{ r.popOp }};pointer-events:{{ r.popPe }};
            transition:clip-path 260ms cubic-bezier(.25,1,.5,1),
                       opacity 180ms cubic-bezier(.25,1,.5,1);z-index:40">
```

Раскрытие — срез, **не** масштаб (от масштаба текст внутри дёргается):

```js
popClip: on(pop) ? 'inset(0 0 0 0 round 16px)' : 'inset(0 0 100% 0 round 16px)',
popOp:   on(pop) ? '1' : '0',
popPe:   on(pop) ? 'auto' : 'none',
rot:     on(pop) ? '180deg' : '0deg',
valueFg: on(pop) ? fg2 : fg,
```

Ширины заданы в `HUB`, шестой элемент строки:

```js
['Режим', '', 'shield', null, 'mode', '176px'],
['DNS',   '', 'help',   null, 'dns',  '210px'],
['Пинг',  '', 'ping',   null, 'ping', '208px'],
['Оформление', '', 'gear', null, 'look', '186px'],
['Язык', '', 'globe', null, 'lang', '166px'],
['Автообновление подписки', '', 'refresh', null, 'autoup', '176px'],
```

Пункт списка:

```html
<div onClick="{{ o.pick }}" class="opt"
     style="display:flex;align-items:center;min-height:38px;padding:4px 10px;
            box-sizing:border-box;border-radius:11px;background:{{ o.bg }}">
```

---

## Переключатель — метод `sw(on)`

```js
sw(on) {
  const acc = ACC[this.state.theme];
  const m = this.state.theme === 'mono';
  return {
    track: on ? acc : (m ? '#232326' : '#1E2126'),
    bd:    on ? acc : (m ? '#38383C' : '#3A4150'),
    thumb: on ? (m ? '#111214' : '#FFFFFF') : (m ? '#38383C' : '#7C8494'),
    left:  on ? '22px' : '6px',
    size:  on ? '24px' : '16px',
    half:  on ? '12px' : '8px'
  };
}
```

Габарит 52×32dp, radius 16dp, обводка 2dp. Бегунок растёт с 16 до 24dp. Все свойства по 220ms ease-out-quart.

---

## Нижняя навигация

Найти по `navLeft`.

```html
<div style="position:absolute;left:0;right:0;bottom:0;display:flex;align-items:flex-end;padding-bottom:6px">
  <span style="position:absolute;bottom:2px;left:{{ navLeft }};width:28px;height:3px;
               border-radius:2px;background:{{ navFg }};
               transition:left 280ms cubic-bezier(.25,1,.5,1),background-color 220ms"></span>
  <sc-for list="{{ tabs }}" as="t">
    <div onClick="{{ t.pick }}" class="btn"
         style="flex:1;display:flex;flex-direction:column;align-items:center;padding:6px 0 9px">
      <svg viewBox="{{ t.vb }}" width="23" height="23" fill="{{ t.fg }}" style="display:block"></svg>
      <span style="margin-top:-1px;font:{{ t.weight }} 11px/1 Roboto,sans-serif;color:{{ t.fg }}"></span>
    </div>
  </sc-for>
</div>
```

```js
navLeft: ['46px', '166px', '286px'][s.tab],
```

Полоска одна на всю панель и переезжает. Не три отдельные.

---

## Кольцо трафика в аккаунте

```html
<svg viewBox="0 0 172 172" width="172" height="172" style="position:absolute;inset:0;transform:rotate(-90deg)">
  <circle cx="86" cy="86" r="80" fill="none" stroke="var(--surf3)" stroke-width="6" />
  <circle cx="86" cy="86" r="80" fill="none" stroke="{{ c.acc }}" stroke-width="6" stroke-linecap="round"
          stroke-dasharray="{{ tr.dash }}" style="transition:stroke-dasharray 500ms cubic-bezier(.25,1,.5,1)" />
</svg>
```

```js
tr: {
  dash: s.unlim ? '503 0' : '191 312',   // длина окружности при r=80 ≈ 503
  used: s.unlim ? '2,0 ТБ' : '1,9 ТБ',
  of:   s.unlim ? 'без ограничений' : 'из 5 ТБ',
}
```

Безлимит — кольцо замкнуто целиком. Лимит — доля от 503.

---

## Кнопки в аккаунте

```html
<div class="btn" style="flex:1;height:48px;border-radius:24px;border:1.5px solid {{ c.acc }};
                        box-sizing:border-box;display:flex;align-items:center;justify-content:center;gap:8px">
  <svg width="18" height="18" fill="{{ c.acc }}">…</svg>
  <span style="font:700 15px Roboto,sans-serif;color:{{ c.acc }}">Пополнить</span>
</div>
<div class="btn" style="flex:1;height:48px;border-radius:24px;border:1.5px solid {{ c.acc }};
                        box-sizing:border-box;display:flex;align-items:center;justify-content:center">
  <span style="font:700 15px Roboto,sans-serif;color:var(--accFg)">Продлить</span>
</div>
```

Обе одной ширины, обе контурные, **без заливки**.

---

## Пилюля трафика на главной

```html
<div style="margin-top:10px;display:flex;align-items:baseline">
  <span style="flex:1;font:500 12px 'Space Grotesk';font-feature-settings:'tnum' on">{{ tb.label }}</span>
  <span style="font:400 12px Roboto;color:var(--fg2)">{{ tb.right }}</span>
</div>
<div style="margin-top:6px;height:3px;border-radius:2px;background:var(--surf3);overflow:hidden">
  <span style="display:block;height:3px;border-radius:2px;width:{{ tb.pct }};background:{{ tb.fill }};
               transition:width 500ms cubic-bezier(.25,1,.5,1)"></span>
</div>
```

```js
tb: s.unlimTb
  ? { pct: '0%',  fill: 'transparent', label: '2,0 ТБ / ∞',   right: '∞' }
  : { pct: '40%', fill: acc,           label: '4 ГБ / 10 ГБ', right: '12 дн.' },
```

Полоса красится только при лимите.

---

## Темы — `const THEMES`

```js
const THEMES = {
  dark: {
    '--page': '#0A0B0D',
    '--bg': 'radial-gradient(620px circle at 50% 28%,#151C2B 0%,#0E1119 52%,#0A0B0D 100%)',
    '--surf': '#141619', '--surf2': '#1A1D21', '--surf3': '#20242B',
    '--line': '#20242B', '--outline': '#2A2E36',
    '--fg': '#F2F4F8', '--fg2': '#9BA1AD',
    '--tile': '#20242B', '--tileFg': '#9BA1AD',
    '--accBg': '#17325C', '--accFg': '#CFE0FF',
    '--ring1': 'rgba(76,141,255,.18)', '--bloom': 'rgba(76,141,255,.28)',
    '--shieldOff': '#3A4A66',
    '--jsonBg': '#3A2E00', '--jsonFg': '#EAB308',
    '--pressBg': '#0D1017', '--popBg': '#20242B'
  },
  mono: { … }
};
const ACC = { dark: '#4C8DFF', mono: '#FFFFFF' };
```

Смена темы — подмена этих переменных на корне экрана, метод `apply()`. Разметка не меняется. Это и есть `ThemeOverlay.Mono`.

---

## Появление главной — объект `en`

```js
en: {
  head:    s.enter ? 'dropIn 460ms cubic-bezier(.22,1,.36,1) 60ms both'   : 'none',
  plus:    s.enter ? 'dropIn 460ms cubic-bezier(.22,1,.36,1) 130ms both'  : 'none',
  ring:    s.enter ? 'bloomIn 720ms cubic-bezier(.22,1.05,.36,1) 180ms both' : 'none',
  stats:   s.enter ? 'liftIn 520ms cubic-bezier(.22,1,.36,1) 420ms both'  : 'none',
  srvName: s.enter ? 'liftIn 520ms cubic-bezier(.22,1,.36,1) 500ms both'  : 'none',
  card:    s.enter ? 'liftIn 560ms cubic-bezier(.22,1,.36,1) 580ms both'  : 'none'
},
// строки серверов
anim: s.enter ? 'slideL 560ms cubic-bezier(.22,1,.36,1) ' + (700 + i * 85) + 'ms both' : 'none'
```

---

## Потоки прогрузки — `runFlow`

```js
runFlow(kind) {
  this.setState({ flow: kind, step: 0, flowOut: false, enter: false, tab: 0, onboard: false });
  this.later(() => this.setState({ step: 1 }), 1200);
  this.later(() => this.setState({ step: 2 }), 3000);
  this.later(() => this.setState({ step: 3 }), 4600);
  this.later(() => this.setState({ flowOut: true }), 5900);
  this.later(() => this.setState({ flow: null, step: 0, flowOut: false, enter: true }), 6450);
  this.later(() => this.setState({ enter: false }), 8600);
}
```

Снятие оверлея и запуск сборки главной — **один** `setState` на 6450 мс. Если разнести, главная мелькнёт целиком.

Варианты ухода:

```js
shellTf: s.flowOut
  ? (s.trans === 'lift'  ? 'translateY(-14%) scale(.97)'
   : s.trans === 'slide' ? 'translateX(-22%) scale(.97)'
   : 'scale(1.06)')
  : 'none',
```

---

## Начальный экран — смена состояния буфера

```js
ob: {
  foundH:  s.clip === 'found' ? '190px' : '0px',
  foundOp: s.clip === 'found' ? '1' : '0',
  emptyH:  s.clip === 'found' ? '0px' : '80px',
  emptyOp: s.clip === 'found' ? '0' : '1',
}
```

```html
<div style="overflow:hidden;max-height:{{ ob.foundH }};opacity:{{ ob.foundOp }};
            transition:max-height 340ms cubic-bezier(.25,1,.5,1),
                       opacity 240ms cubic-bezier(.25,1,.5,1)">
```

Оба блока в разметке, меняются высотой. Не подменяются.

---

## Тарифы — `buildBuy`

```js
{ key: 'base', name: 'Base', badge: 'Текущий',
  note: '5 устройств · трафик без ограничений', from: '150 ₽',
  terms: [['1 месяц','150 ₽',''], ['3 месяца','400 ₽','Выгода 50 ₽'],
          ['6 месяцев','750 ₽','Выгода 150 ₽'], ['12 месяцев','1 400 ₽','Выгода 400 ₽']] }
```

Раскрытие:

```js
h: open ? '340px' : '0px',
op: open ? '1' : '0',
bd: open ? acc : 'var(--line)',
titleFg: open ? acc : fg,
cta: 'Оплатить ' + p.terms[cur][1],
saveFg: fg2,   // выгода приглушённо-белым, не зелёным
```

---

## Подэкраны — `const SUBS` внутри `buildSub`

Формат строки: `[название, подпись, иконка, ключ переключателя, значение справа, ссылка, шеврон]`

```js
lproxy: ['Локальный прокси', '', [
  { label: 'Память', rows: [
    ['Лимит оперативной памяти', 'Меньше — экономнее, но возможны замедления', 'router', null, '128 МБ'],
    ['Снять ограничение', 'Работать без ограничения памяти ядра', 'router', 'lpMemFree']
  ] },
  …
]],
```

Все названия и подписи взяты из `values/strings_local_proxy.xml`, `strings_provider.xml`, `strings_tv.xml`, `strings_devices.xml`. Сверяйте по ним, а не по прототипу.
