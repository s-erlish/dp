# Owner feedback from the first real test, 2026-07-27

He installed the CI builds and went through both clients. Every item below is his, quoted or closely
paraphrased, with what I verified. **Nothing here is optional and nothing is a suggestion.**

The standing complaint that frames all of it: *«я же просил все доработать по дизайну, а не урезать
фишки которые мы делали, все эти анимации трудом и потом делались»*. Refinement, never removal.

---

## A. Regressions — things that worked and stopped working

| # | What | Where |
|---|---|---|
| A1 | **Signing out does not clear the servers.** They stay on screen and stay selectable with no session. | Android, account sign-out path |
| A2 | **Subscriptions cannot be deleted.** «удалять почему-то я тоже не могу подписки на телефоне» | Android |
| A3 | **The ping fix never reached the desktop.** Android resolves a template's real server through its routing rules; the desktop still reads the first proxy outbound, so a template whose leading entry is a decoy is measured against a host that is not the server. Port `V2rayConfig.getProxyOutbound()`'s resolution and the speedtest builder's use of it. | PC |
| A4 | **Главная lost its content and its connect animation** (already being fixed, listed for completeness). | Android |

## B. Naming and copy

| # | What | Correct |
|---|---|---|
| B1 | «Добавить провайдера» | **«Добавить подписку»**. The owner calls this thing a подписка. This overrules the terminology lock in `00-rules.md` 9.3 and every register row derived from it — his product, his word. Apply everywhere on both platforms, including «Настройки провайдеров» and «Автообновление провайдеров». |
| B2 | «серверы Departament», «Вход в departament» | **departament is always lowercase**, everywhere, both platforms, in every string. |
| B3 | Mode row values: «VPN-туннель», «Прокси», «VPN + прокси» | **«TUN»**, **«Proxy»**, **«TUN + Proxy»** — in that order, both platforms. |

## C. Structure and behaviour

| # | What |
|---|---|
| C1 | **The add menu is overloaded.** Tapping «Добавить подписку» offers scan QR, clipboard, enter link, create manually, import from file, send to TV. It must offer **QR and clipboard, and nothing else.** The rest move somewhere they belong or go. |
| C2 | **The mode control on the desktop must match the phone.** The desktop shows a two-way TUN/Прокси segment; the phone offers three options. Same three, same names, both platforms. |
| C3 | **Login buttons sit at the very bottom** of a mostly empty screen, with the headline stranded at the top. Bring them up into the composition. |
| C4 | **The desktop sign-in is not redesigned at all** — «что это ваще за меню входа такое кривое и непеределанное». The Android sign-in is right: «дизайн в целом хорош для входа, почему такого же 1 в 1 нет на пк?». Build the desktop one to match the Android one, one to one. |

## D. Craft — the bar the buttons have to clear

| # | What |
|---|---|
| D1 | **Buttons are flat fills and look unfinished.** «кнопки должны все быть проработанные такие, а не просто сплошной цвет, может градиент какой-то, может даже анимированный». The primary button needs real treatment — depth, a considered gradient, and motion on press. This is an explicit owner instruction and it overrides `00-rules.md`'s blanket ban on gradients **for buttons specifically**; the ban still holds for page backgrounds and decorative glows. |
| D2 | **The desktop font reads too heavy.** «шрифт какой-то толстый». Check the weights actually resolving on Windows — the fallback may be landing on a bolder face than intended. |
| D3 | **Desktop dialog buttons are wrong.** In «Удалить провайдера и его серверы?» the confirm button reads washed out, like a disabled control, next to a solid cancel. Destructive confirm must read as the primary action and must not look disabled. |
| D4 | «много багов с кнопками» — audit every button on the desktop for state, contrast, alignment and hit area, not just the ones named here. |

## E. The subscription pill — the thing Главная must get back

«все должно быть вернуто с пилюлей и инфой о подписке под кнопкой, чтобы при подтягивании подписки с
акка писался ник подписки, в общем все как было раньше, не знаю почему ты это все убрал»

Under the connect object, on Главная, exactly as before:

- the **pill** with the subscription's traffic figure;
- the subscription **info block** — provider name with its emoji, the auto-update timestamp, the
  operator's notice text, the support and Telegram actions, and the refresh, pin and delete controls;
- when a subscription is pulled from the account, **its own name is what shows** — the nickname the
  account returns, not a generic label.

It was removed because a spec redesigned the screen. It comes back as it was.

## F. The desktop is not finished, and the phone is the reference

«на пк также кнопки все баганные, при наведении моргают и так далее, ничего не доделано, почти все
кнопки такие куда не наведешься, вкладка аккаунт вообще хуёво выглядит не стилизованно, на андроиде в
100 раз лучше все выглядит»

| # | What |
|---|---|
| F1 | **Buttons flicker on hover**, across nearly every button in the app. A pointer-over that changes layout, re-templates the control, or animates a property that triggers a re-measure will do this. Find the shared cause rather than patching one button. |
| F2 | **The Аккаунт tab is unstyled** — it is a bare card floating in an empty pane. Build it to the same standard as the Android account tab. |
| F3 | **Android is the reference for the desktop, not the other way round.** Where the two disagree on look, the phone wins, and the desktop is brought to it — natively, but one to one in structure, hierarchy and copy. |

---

## The rule this feedback exists to enforce

Two waves shipped screens that answered a specification instead of doing the job the screen already
did, and the owner caught both by running the build. The specs are guidance for **how things look**.
What a screen contains, and what the product's words are, is his.

When a spec and this file disagree, **this file wins**.
