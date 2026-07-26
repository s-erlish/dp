# Decision: Russian is the master locale. The English option goes.

`MASTER-REGISTER.md` M-42 asks for this to be written down before any code is touched, because the two
answers lead to opposite work. Here it is.

## What is true today

- `res/values/arrays.xml:140-151` offers **Система / Русский / English**.
- There is no `values-en/`, so picking English falls through to `values/`.
- `values/` holds **1255** keys, **847 of them Russian**, across 20 files — every departament screen was
  written straight into the default bucket.
- `values-ru/` holds 880. The ~372 keys with no `values-ru` entry are invisible on a Russian device and
  fatal on every other.
- Seven vendored upstream locales (`ar`, `bn`, `bqi-rIR`, `fa`, `vi`, `zh-rCN`, `zh-rTW`) carry ~352
  keys each — v2rayNG's strings, none of departament's.

So the language picker's only non-Russian option produces a half-Russian app, and six more locales ship
in that state without being offered at all.

## The decision

**`values/` is the Russian master. The English entry is removed from the picker, and the vendored
upstream locale folders go with it.**

Why this and not the other way round:

- departament is a Russian product end to end — the site, the bot, the payments, every string the owner
  has written or corrected in this session, including the ones in his feedback («Добавить подписку»,
  «departament» lowercase, «TUN / Proxy / TUN + Proxy»). There is no English audience to serve.
- The alternative is to translate 847 strings into English and move 847 into `values-ru/` — the largest
  single item in the register — to make an option nobody asked for work.
- Removing a language option that does not work is not removing a feature. It is deleting a door that
  opens onto a broken room. Nothing the owner built is touched.

This is my call, not his, so it is stated here rather than buried in a diff. If he wants English later
it is purely additive: `values/` is already a clean master (verified: **zero** keys where `values/` is
Russian and `values-ru/` shadows with leftover English), so an `values-en/` can be added at any time
without untangling anything first.

## What the work is, concretely

1. `res/values/arrays.xml:140-151` — drop the English entry from the language array and its value array,
   leaving Система / Русский. Check every reader of the stored value for an index assumption.
2. Delete `res/values-ar/`, `values-bn/`, `values-bqi-rIR/`, `values-fa/`, `values-vi/`, `values-zh-rCN/`,
   `values-zh-rTW/` — upstream strings for screens this product no longer has.
3. `values-ru/` is now redundant with `values/`. Fold it down rather than leaving two masters to drift:
   the ~372 keys that exist only in `values/` are already the live copy, and `values-ru/` duplicates the
   other 880 exactly (verified for `strings_home.xml`, key-for-key and body-for-body). Keep `values/`.
4. **M-44** while in here: `res/layout/view_toolbar.xml:70` has the tree's one hardcoded
   `contentDescription="Назад"`. Make it a resource.

Desktop half, **M-43**, same decision applies:

5. `Views/SettingsView.axaml:792` («Масштаб интерфейса») and `Views/StatusBarView.axaml:114,120` are
   hardcoded literals rather than `loc:T` keys.
6. `Directory.Build.props` — `SatelliteResourceLanguages` is unset, so every publish ships eight `ResUI`
   satellite folders (fa, fr, hu, id, ru, zh-Hans, zh-Hant) into a directory named "departament". Pin it
   to `ru` and they stop shipping.
7. `ServiceLib/ViewModels/StatusBarViewModel.cs:561` builds the routing-mode name from two Russian
   literals inline. That string is being replaced anyway by the three-way TUN / Proxy / TUN + Proxy
   control (register M-35); when it is, it goes through the localisation path like every other string.

## The one thing to be careful about

Step 1 changes a persisted preference's value set. A device that already stored "English" must land on
Система, not on an index that no longer exists. Whoever does this reads the preference's reader before
touching the array, and handles the stale value explicitly.
