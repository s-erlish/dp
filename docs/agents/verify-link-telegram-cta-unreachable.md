# Verification: "«Привязать Telegram» CTA banner can never be shown"

**Verdict: CONFIRMED (real), but the reporter's line numbers are wrong and the severity is
overstated. Severity should be low — dead code, not a functional regression.**

All line numbers below were read from the working tree on 2026-07-26.

---

## 1. The claim's cited line numbers do not exist as described

The report cites `MainActivity.kt:1056-1061` for `updateAccountGate`, `:1094-1096` for
`updateLoginCtaVisibility`, and `:987-991` for the click handlers. None of those match:

| Claimed | Actual |
|---|---|
| `updateAccountGate` at 1056-1061 | `/home/user/dp/V2rayNG/app/src/main/java/com/v2ray/ang/ui/MainActivity.kt:1082-1098` |
| `header.root.isVisible = loggedIn` | `MainActivity.kt:1090` |
| `header.groupLogin.isVisible = false` | `MainActivity.kt:1093` |
| `updateLoginCtaVisibility` at 1094-1096 | `MainActivity.kt:1123-1131` (predicate at `:1128-1129`, assignment at `:1130`) |
| click handlers at 987-991 | `MainActivity.kt:1021-1025` |
| `ctaDismissed` at 122 | `MainActivity.kt:122` — this one is correct |

Lines 1056-1061 are actually inside `applyAccountState`, and `:987-991` is inside an unrelated
`AlertDialog` builder. The substance of the claim still holds; the anchors were stale.

## 2. The mechanism is real — and it is doubly unreachable

### 2a. The CTA is a child of `group_login`, which is set GONE unconditionally

`/home/user/dp/V2rayNG/app/src/main/res/layout/layout_home_account.xml`:

- `:22-27` — `LinearLayout android:id="@+id/group_login"`, `android:visibility="gone"` by default.
- `:30-46` — `LinearLayout android:id="@+id/cta_link_telegram"`, also `visibility="gone"`, opened
  **inside** `group_login`.
- `:62-73` — `TextView android:id="@+id/btn_cta_dismiss"` (the ✕), inside the CTA.
- `:74` closes `cta_link_telegram`; `:75` closes `group_login`. The parent/child nesting the report
  asserts is correct.

`MainActivity.kt:1093` is the **only** write to `groupLogin` visibility anywhere in the module —
verified by grepping the whole of `app/src/main` for `groupLogin` / `group_login`; the only hits are
`MainActivity.kt:1093` and the layout's own id declaration + doc comment. It is a literal `false`,
with no branch:

```kotlin
// MainActivity.kt:1091-1093
// The signed-out login group (and its "link Telegram" CTA) is no longer an account entry
// point — the header only exists once signed in, where the account chip is shown.
header.groupLogin.isVisible = false
```

Because the XML default is also `gone` (`layout_home_account.xml:27`), there is no window in which
`group_login` is ever laid out — not even before `updateAccountGate()` first runs. Setting
`ctaLinkTelegram.isVisible = true` (`MainActivity.kt:1130`) therefore cannot put a single pixel on
screen: View visibility does not propagate past a GONE ancestor.

### 2b. Independently, the predicate and the root visibility are mutually exclusive

```kotlin
// MainActivity.kt:1090
header.root.isVisible = loggedIn          // loggedIn = AccountSession.isLoggedIn(), :1089
```
```kotlin
// MainActivity.kt:1128-1130
val show = !AccountSession.isLoggedIn() && !ctaDismissed &&
    SubscriptionOrigin.hasDepartamentSubscription()
header.ctaLinkTelegram.isVisible = show
```

`show` requires `!isLoggedIn()`; the include root is visible only when `isLoggedIn()`. The two
conditions can never both hold. `header.root` is written in exactly two places — `:1085`
(hard `false` when no backend) and `:1090` — confirmed by grep for `.root.isVisible` in the file
(other hits are `binding.groupSettings.root` at `:473` and `binding.layoutEmpty.root` at `:849`,
different views). The include in `activity_main.xml:175-182` adds no visibility override.

Call ordering is irrelevant: `updateLoginCtaVisibility()` and `updateAccountGate()` touch disjoint
views, and both orderings occur (`:803`/`:806` and `:2048`/`:2049`) with the same result.

### 2c. Consequently dead

- `cta_link_telegram` (`layout_home_account.xml:30-74`) — never rendered.
- `btn_cta_dismiss` (`:62-73`) — never tappable.
- `ctaDismissed` (`MainActivity.kt:122`) — written only at `:1023`, read only at `:1128`; the write
  site is unreachable and the read site's result is discarded by 2a.
- Both handlers (`MainActivity.kt:1021` `ctaLinkTelegram.setOnClickListener`, `:1022-1025`
  `btnCtaDismiss.setOnClickListener`) — registered but unreachable.
- `updateLoginCtaVisibility()` (`:1123-1131`) — a no-op function; its 3 call sites (`:803`, `:1057`,
  `:2049`) do nothing observable.
- Strings `auth_link_telegram_cta` and `auth_link_telegram_cta_dismiss`
  (`res/values/strings_nav.xml:14-15`) — referenced only from the dead layout.

## 3. Where the reporter is wrong: this is intentional, and the feature is NOT lost

The report's framing ("high severity") implies users lost the ability to link Telegram. They did not.

- `MainActivity.kt:1091-1092` explicitly documents the retirement: *"The signed-out login group (and
  its 'link Telegram' CTA) is no longer an account entry point."*
- `layout_home_account.xml:20-21` says the same: *"The primary login buttons now live in the
  onboarding card (layout_home_empty); this container only carries the CTA."*
- `MainActivity.kt:1069-1071` states the policy the removal enforces: *"A pasted/imported
  subscription — even a genuine 'departament' one — must NOT unlock the account, since there is no
  account to load without a login."* That is exactly the population the dead banner targeted
  (`hasDepartamentSubscription()` while signed out, `:1128-1129`).
- A **live** «Привязать Telegram» entry point exists in the onboarding card:
  `res/layout/layout_home_empty.xml:77` (`btn_home_link_tg`), handler at `MainActivity.kt:712`
  (`openTelegramLink()`), gated at `MainActivity.kt:1179`
  (`empty.btnHomeLinkTg.isVisible = buyState && !telegramLinked`, where `buyState = configured &&
  loggedIn`, `:1169`). Its container is shown when the server list is empty
  (`MainActivity.kt:720-722`). That path is reachable.

So the correct characterization is **leftover dead UI from a deliberate removal**, not a broken
feature. Cleanup, not a bug fix.

## 4. Corrected/extended finding: the CTA's own logic is self-contradictory

The reporter missed the part that actually matters if anyone ever tries to "fix" this by making the
banner visible again. The banner is wired to the **link** flow, which requires an existing session:

```kotlin
// MainActivity.kt:1020-1021
// The "link Telegram" CTA banner attaches Telegram to the signed-in account.
header.ctaLinkTelegram.setOnClickListener { openTelegramLink() }
```
```kotlin
// MainActivity.kt:1143-1153
/** Opens the Telegram screen in LINK mode: the current (already signed-in) account gets its
 *  Telegram attached ... The token request carries the current JWT ... */
private fun openTelegramLink() {
    val i = Intent(this, LoginActivity::class.java)
    i.putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_TELEGRAM)
    i.putExtra(LoginActivity.EXTRA_LINK, true)
    ...
}
```

But its visibility predicate demands the opposite (`MainActivity.kt:1128`: `!AccountSession.isLoggedIn()`).
`LoginActivity` treats `EXTRA_LINK` as an authenticated flow — it deliberately bypasses the
"already logged in, finish immediately" shortcut (`LoginActivity.kt:69`:
`if (viewModel.isLoggedIn() && !linkMode)`) and auto-starts the link request at `:159`. Naively
un-gating the banner would hand a JWT-requiring link flow to signed-out users.

**Recommended action:** delete rather than repair — `group_login` + `cta_link_telegram` +
`btn_cta_dismiss` (`layout_home_account.xml:20-75`), `ctaDismissed` (`MainActivity.kt:122`), the two
handlers (`:1020-1025`), `updateLoginCtaVisibility()` (`:1119-1131`) and its 3 call sites (`:803`,
`:1057`, `:2049`), plus the two orphan strings (`strings_nav.xml:14-15`). The `layout_home_empty`
button already covers the use case, and the account-gate policy at `:1069-1071` says the signed-out
variant should not come back.

Secondary note: with `group_login` gone, `layout_home_account.xml` is a `FrameLayout` wrapping a
single remaining child (`chip_account`, `:78-154`), so the FrameLayout can be flattened away.
