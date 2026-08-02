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
