# Review 07 — Server Flag Tiles, Memory Card, Notification (commits de34b9a, 902b804, abd5ab4)

Static review (no Android SDK available). Focus: compile-time correctness, whole-file
consistency of `MainActivity.kt`, resource resolution, and lifecycle/logic.

## Verdict

- **No BLOCKER, no HIGH issues found.** All three commits should compile and behave as intended.
- **Duplicate-method check (the key concern):** `MainActivity.kt` has **exactly one** of each —
  `onCreate` (L100), `onResume` (L429), `onPause` (L436), `onDestroy` (L899), and one
  `updateMemoryCard` (L356). The only repeated `fun run()` is the three distinct anonymous
  `Runnable`s (`healthCheckRunnable`, `memoryRunnable`, `timerRunnable`) — not a duplicate. The
  earlier duplicate `onDestroy()` is correctly merged; cleanup now removes `timerRunnable`,
  `healthCheckRunnable`, `memoryRunnable` plus `tabMediator?.detach()` in one method.

## Severity table

| # | Sev | Area | Finding |
|---|-----|------|---------|
| 1 | INFO | MainActivity dup-scan | Exactly one onDestroy/onResume/onPause/onCreate/updateMemoryCard. Merge is correct. No leftover duplicate. |
| 2 | PASS | Compile — FlagUtil | `codePointAt`/`Character.charCount`/`StringBuilder.appendCodePoint` used correctly; regional-indicator math (`0x1F1E6 + (c-'A')`, range `BASE..BASE+25`) correct. `stripLeadingFlag` substrings in UTF-16 units on a verified `startsWith` prefix — safe. |
| 3 | PASS | Compile — MemoryStatsManager | `Debug.MemoryInfo().totalPss` is a valid public field; `getMemoryInfo` populates it; `/1024` KB→MB correct. |
| 4 | PASS | Compile — MainRecyclerAdapter | `binding.tvFlag` backs `@+id/tv_flag` (item_recycler_main.xml L39); `FlagUtil` imported. |
| 5 | PASS | Compile — MainActivity memory card | `binding.cardMemory`/`tvMemory`/`dotMemory` all map to ids in activity_main.xml (L260/303/283). `MemoryStatsManager` imported (L37), `isVisible` imported (L16). Colors `color_connected`/`colorConfigType`/`colorPingRed` all exist. `getColor(int)` OK on minSdk 24 (API 23+). `ColorStateList.valueOf` + `backgroundTintList` valid. |
| 6 | PASS | Compile — NotificationManager | `FlagUtil` imported; `currentConfig` handled via `?.let { cfg -> }` (no smart-cast hazard on the nullable param); `ProfileItem.remarks` is non-null `String` so `stripLeadingFlag(cfg.remarks)` type-checks. `setWhen`/`setUsesChronometer`/`setShowWhen` are valid `NotificationCompat.Builder` methods. `NotificationManager.IMPORTANCE_LOW` resolves to `android.app.NotificationManager` (already imported; unchanged from prior `IMPORTANCE_HIGH`). `setShowBadge(false)` valid. |
| 7 | PASS | Resources | Drawables `bg_flag_tile.xml`, `bg_status_dot.xml` present; `ic_pin_24dp`/`ic_support_24dp`/`ic_globe_24dp` present. Strings `memory_*`, `sub_support`/`sub_website`, `title_/summary_pref_show_memory` present. `pref_settings.xml` key `pref_show_memory` == `AppConfig.PREF_SHOW_MEMORY` (`"pref_show_memory"`). Both layouts well-formed (xmllint). NestedScrollView>LinearLayout>[card_hero, card_memory] nesting balanced. |
| 8 | PASS | Lifecycle — memory poll | Posted in `onResume` (remove+post), removed in `onPause` and `onDestroy`. 2s cadence via `postDelayed(this, 2000L)`. `timerHandler` (L54) initialized before `memoryRunnable` (L69). No leak. |
| 9 | LOW | Logic — FlagUtil false positives | `parseCountryCode` matches any word-boundaried 2-letter token in `ISO2_CODES`, so a remark like "No limit", "IT support", "in-1" resolves to a flag (NO/IT/IN). Cosmetic only; country-name pass runs first. Consider requiring an explicit `[XX]`/leading-token form. |
| 10 | LOW | Logic — FlagUtil UK glyph | `ISO2_CODES` includes `"UK"`; `codeToFlag("UK")` yields 🇺🇰 which has no emoji flag (GB is the ISO code) and renders as boxed letters. Drop `"UK"` or map it to `GB`. |
| 11 | INFO | Notification — title when null | `title` is null when `currentConfig == null`; `setContentTitle(null)` matches prior behavior (`currentConfig?.remarks`). No regression. |
| 12 | INFO | Notification — chronometer | `setWhen(now)` + `setUsesChronometer(true)` gives system-rendered uptime from notification build time (= connect time). `updateNotification` is a separate method, so per-second traffic updates do not rebuild/reset `when`. Correct. |
| 13 | INFO | Notification — channel id change | New channel id `DEPARTAMENT_VPN_CH_ID` orphans the old `RAY_NG_M_CH_ID` on upgraded installs (leftover, unused channel in system settings). Harmless and intended (needed so the new `IMPORTANCE_LOW` takes effect rather than being pinned to the old channel's user-set importance). Acceptable. |

## Notes

- No NPE / resource-not-found / crash paths identified in the reviewed diffs.
- `getColor`/`ColorStateList`/`setBackgroundTintList` and `setUsesChronometer` are all within minSdk 24.
- The merged `onDestroy` correctly consolidates all three `removeCallbacks` + `tabMediator?.detach()`.
