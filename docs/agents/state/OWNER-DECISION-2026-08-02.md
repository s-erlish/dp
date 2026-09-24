# Owner decision, 2026-08-02 — the design is refined, never rebuilt

Recorded the same way `OWNER-FEEDBACK-2026-07-27.md` was, and with the same authority: **when this
file and a specification disagree, this file wins.** It restates a rule the owner has now given
twice, because it was misread twice.

---

## 1. The rule

> «главная суть, чтобы дизайн то остался тот же, что и был раньше, а просто переработан... просто
> нужно ДОРАБОТАТЬ дизайн, а не полностью его переделывать... принимай именно доработки и доп
> наработки, а не фулл переделку»

The design that is on screen today **stays**. It gets refined, deepened and finished. It does not get
replaced.

This is the second time he has said it. The first was 2026-07-27: *«я же просил все доработать по
дизайну, а не урезать фишки которые мы делали, все эти анимации трудом и потом делались»*. Both times
the trigger was the same: a wave read a document in `docs/design2026/` as an instruction to rebuild a
screen from scratch, shipped a screen that answered the specification instead of doing the job the
screen already did, and he caught it by comparing two installs.

## 2. What that changes about `docs/design2026/`

The specifications keep their authority over **how things look** — tokens, spacing, type, colour,
states, motion tempo, the copy register. They lose their authority over **whether a screen is
rebuilt**.

Concretely, these are now void as work orders, and any file that reads as one is to be treated as a
description of the target quality, not as a demolition order:

| Spec | Was read as | Now means |
|---|---|---|
| `13-start-screen.md` | build `fragment_home.xml`, delete eight files | refine Главная as it stands — already corrected by `34baed0`/`76e1c39` |
| `14-auth.md` §0.2 | delete `activity_login.xml` + `LoginActivity.kt`, build eleven new surfaces | refine the sign-in that exists |
| `23-account-rework.md` §6.1 | build thirteen new layouts, invert the hierarchy | refine the account tab that exists |
| `16-servers.md` §15.1 | build twelve new files | refine the servers surface that exists |

The consequence for `STATE-OF-WORK.md` §6: **Tier 4 items 20–23 are cancelled as rebuilds.** The
defects they were meant to carry — the ones with a real user cost — survive as individual items and
are to be fixed in place. Tier 1, Tier 2 and Tier 3 are unaffected: converting finished-but-unreachable
work into a shipping feature, and giving an existing screen the vocabulary it already almost speaks,
are refinement by definition.

## 3. The test to apply before touching a screen

Ask: *does a user who knows this screen still recognise it after my change?*

- **Yes, and it is better** — that is the work.
- **No** — stop, whatever the specification says.

And its corollary, from 2026-07-27, unchanged: **refinement, never removal.** An element that was on
screen and is now gone is a regression, not a simplification — including animations. Where a previous
wave removed something, recovering it from git history is the correct fix, not a workaround.

## 4. Also reported today, and it outranks everything

> «приложение на андроиде не запускается»

The Android client does not start from the latest CI build. That is a P0 blocker above every item in
`MASTER-REGISTER.md`, including M-01. Under investigation as of this file's date; the diagnosis and
the fix belong in the commit that closes it, not here.

Note for whoever picks it up: an APK built in this environment **cannot be run** — the native
`libv2ray.aar` is absent and a type-check stub stands in — so the diagnosis has to be a static trace,
and the confirmation has to come from a CI build the owner can install. Note also that a build signed
with a per-runner debug key cannot install over a previous one (`UNASSIGNED-WORK.md` R-02), so
"does not start" must be separated from "did not install" before anything is concluded.

## 5. Editing a подписка is not a feature — decided, not lost

Three settings rows came out at his instruction: «Дополнительно», «Список подписок» and «Другие
способы добавления». Removing the second one takes `SubSettingActivity`/`SubEditActivity`'s only
door with it, and with that door goes **renaming a подписка, the per-sub User-Agent and auto-update
overrides, the per-sub remote-DNS override, and add-by-typed-URL**. That was put to him explicitly
rather than accepted quietly.

His answer: **«не надо оставлять, в целом не предусмотрено редактирование подписки»**.

So this is a product decision, not a regression, and it is recorded here precisely so nobody
"restores" it later under the refine-never-remove rule. A подписка is something the client
**receives** — from the account, a QR code or the clipboard — and then refreshes, pins, gets support
for, or deletes. It is not something the user authors or edits. The card on Главная already carries
that whole set.

Consequences that follow from it, and are therefore also decided:

- `SubEditActivity` and `SubSettingActivity` are dead code. Do not build a new entry point for them.
  Deleting them outright is a legitimate follow-up; leaving them unreferenced is also fine.
- Anything that needs a per-subscription override — User-Agent, auto-update interval, remote DNS —
  must come from the operator's data or a global default, never from a per-sub editor.
- The one thing to watch, because it is now unfixable from the UI: a подписка whose provider returns
  no `profile-title` is named by whatever the import chose. `AngConfigManager.importUrlAsSubscription`
  names it literally `"import sub"`, and `HomeFragment.metaTitle` filters only `"Default"`. With no
  rename, a bad default name is permanent — so the naming has to be right at import time. Fix it
  there.
