# Audit 2026 - Android, the servers list

**Law:** `docs/design2026/00-rules.md` (section 18 carries D-1 … D-12; rule bodies are current).
**Component vocabulary:** `docs/design2026/22-components.md` R15 - the 15 components in that file are
the entire vocabulary. Nothing below invents a component.
**Screen specs:** `16-servers.md` **does not exist** (checked 2026-07-26), so the binding screen specs
are `24-tab-conformance.md` §3.2 / A-15 / A-17 and `32-master-plan-android.md` §6.3, §12.
**Scope:** the Серверы tab and everything reachable from a server row.
**Constraint:** this document is the only file written. No source file was edited.

---

## 0. Files audited, and the verdict on each

| File | Lines | Verdict | Why |
|---|---|---|---|
| `res/layout/item_recycler_main.xml` | 130 | **RESTYLE, heavy** | Emoji flag, per-row card, 3 inline `textSize`, 6 raw-literal gaps, dead `layout_indicator` |
| `res/layout/layout_servers_header.xml` | 108 | **REBUILD** | Four 36dp targets, three English `contentDescription`s, fixed 44dp search, radius 14 |
| `res/layout/layout_servers_empty.xml` | 67 | **REBUILD** | No body line, two actions, 8 off-scale gaps, accent glyph, wrong silhouette |
| `res/layout/item_section_header.xml` | 49 | **RESTYLE** | 38dp effective height, no kebab, not sticky, collapse state never announced |
| `res/layout/item_recycler_footer.xml` | 28 | **DELETE** | An invisible spacer with an empty `TextView` on `TextAppearance.AppCompat.Tooltip` |
| `res/layout/sheet_server_actions.xml` | 271 | **RESTYLE** | 6 coloured tiles on one surface, Body-weight row labels, red tile + red text |
| `res/layout/dialog_config_filter.xml` | 46 | **DELETE** | Unthemed `Spinner` + bare `EditText`; server filtering is the header search now |
| `res/layout/activity_server_group.xml` | 106 | **DELETE (absorbed)** | A-13 folds ten server forms into one; this is one of the ten |
| `ui/MainRecyclerAdapter.kt` | 373 | **RESTYLE + fix** | No stable IDs, no `DiffUtil`, 5 × `notifyDataSetChanged()`, JSON parse per bind |
| `ui/ServerActionsSheet.kt` | 72 | **RESTYLE + REWIRE** | Correct logic, no caller (P0), `BottomSheetDialog` not a Fragment |
| `ui/ServerGroupActivity.kt` | 170 | **DELETE (absorbed)** | Same as `activity_server_group.xml`; `AlertDialog` + `android.R.string.ok` |
| `util/FlagUtil.kt` | 261 | **RETARGET (read-only here)** | Parsing logic is good and evidence-backed; the **return type** is the defect |

Collaterally in scope because this screen is their only consumer:
`res/drawable/bg_server_row.xml` (2 raw hex, 1.5dp selected stroke), `bg_search_pill.xml` (radius 14),
`custom_divider.xml` (44dp symmetric inset, `@color/divider_color_light`),
`res/menu/menu_main.xml`, `MainActivity.kt` §`setupServerLists` / `setupServersHeader` /
`showImportMenu` / `prepareMenu` / `updateServersChrome` / `markAllServersTesting`,
`MmkvManager.encodeServerConfig`, `dto/entities/ServerAffiliationInfo.kt`.

---

## 1. Mechanical greps, scoped to these files, with real numbers

Run from `/home/user/dp/V2rayNG/app/src/main/res`. `$L` = the eight layouts above.
`$D` = `bg_server_row bg_flag_tile bg_type_chip bg_search_pill custom_divider bg_sheet_top
bg_sheet_handle bg_icon_blue bg_icon_red`.
`$S` = `values/strings.xml values/strings_server_actions.xml values/strings_menu_actions.xml`.

| # | Check (`00-rules.md` 1.5 / 9.7) | Command | Hits | Verdict |
|---|---|---|---|---|
| G1 | Raw colour literals in layouts + `menu/` | `grep -rnE '(android:(textColor\|background\|tint\|backgroundTint\|strokeColor)\|app:tint\|app:strokeColor)="#' $L menu/menu_main.xml` | **0** | clean, keep clean |
| G1b | Raw hex in this screen's drawables | `grep -rnoE 'color="#[0-9A-Fa-f]{6,8}"' $D` | **2** | `bg_server_row.xml:8,18` `#1F4C8DFF` - **1.4.6 hit** |
| G2 | `textAllCaps="true"` | `grep -rn 'textAllCaps="true"' $L` | **0** | clean |
| G3 | Face or size chosen in a layout (D-2) | `grep -rn 'android:fontFamily\|android:textSize' $L` | **3** | `item_recycler_main.xml:49` 18sp, `:121` 12sp, `layout_servers_header.xml:104` 14sp. `fontFamily` 0 |
| G4 | Off-scale `dp` (1.5 allowlist) | `grep -rnoE '"(-?[0-9]+)dp"' $L \| grep -vE '…'` | **4** | `layout_servers_header.xml:100,101` 14dp; `layout_servers_empty.xml:40` 14dp, `:58` 10dp |
| G4b | Raw `dp` literals, total | `grep -rhoE '"(-?[0-9]+)dp"' $L \| wc -l` | **57** | 13 of them are `0dp` layout weights |
| G4c | Raw `dp` literals excluding `0dp` | as above `\| grep -v '"0dp"'` | **44** | every one is a token that exists |
| G4d | **Raw literal used as a gap** (1.4.5, the stricter ban) | `grep -rnE '(padding\|margin\|drawablePadding\|spacing)[A-Za-z]*="[0-9]+dp"' $L` | **24** | 6 in `item_recycler_main`, 9 in `layout_servers_header`, 9 in `layout_servers_empty` |
| G4e | Non-token `dp` in this screen's drawables | `grep -rnoE '"[0-9.]+dp"' $D` | **12** | incl. `bg_server_row.xml:21` **1.5dp**, `bg_search_pill.xml:5` **14dp**, `custom_divider.xml:5,6` **44dp** |
| G5 | Em/en dash, file-scoped | `grep -rn -e '—' -e '–' $S` | **3** | all in `values/strings.xml` (`auto_fallback_switching:333`, `sub_auto_update_label:346`, `routing_settings_process:429`) - **none is a servers key** |
| G5b | Em/en dash, **servers keys only** | same, filtered to the 12 keys this screen renders | **0** | clean |
| G6 | Three dots instead of `…` | `grep -rn '\.\.\.' $S` | **0** | clean; `search_hint` already uses the single `…` |
| G7 | Emoji in shipped `<string>`s | python scan of `$S` | **0** | clean **in XML** - see G7b |
| G7b | **Emoji rendered as UI chrome at runtime** | `FlagUtil.kt` | **3 sites** | `:19` `GLOBE = "🌐"`, `:80-90` `codeToFlag()` synthesises a regional-indicator pair, `:27-32` returns either into `binding.tvFlag.text` (`MainRecyclerAdapter.kt:201`). **1.4.4 hit** |
| G8 | Nested `MaterialCardView` | `grep -rhc 'MaterialCardView' $L` | **2 lines = 1 card** | 0 nested, clean |
| G9 | `notifyDataSetChanged()` on a visible list (11.5) | `grep -n 'notifyDataSetChanged' MainRecyclerAdapter.kt` | **5** | `:86, :152, :192, :305, :334` |
| G10 | `sw600dp` variants of these layouts | `ls -d res/*/` | **0** | no `layout-sw600dp`, no `values-sw600dp` anywhere in the module |

**Baseline movement.** `00-rules.md` 1.5 records 325 off-scale `dp` across 25 layouts. These eight
layouts contribute **4** of them; the other 20 raw gaps are on-scale numbers written as literals,
which 1.4.5 also calls a defect ("a raw literal used as a gap is, whatever its number"). The bar for
this wave is 0 raw `dp` in all eight files, not 0 off-scale ones.

---

## 2. Ban hits (`00-rules.md` §1). Every one is at least P1 by §17.2

| # | Ban | Evidence | Severity |
|---|---|---|---|
| B1 | **1.4.4 no emoji as UI chrome** | `FlagUtil.kt:19,27-32,80-90` → `MainRecyclerAdapter.kt:201` `binding.tvFlag.text = FlagUtil.resolveFlag(profile)`. The server icon on every row *is* an emoji. `item_recycler_main.xml:50` `tools:text="🇳🇱"` documents the intent | P1 |
| B2 | **1.4.6 no raw colour literals** | `bg_server_row.xml:8,18` `#1F4C8DFF`. `@color/accent_fill_12` already holds exactly this value (`values-night/colors.xml:170`) | P1 |
| B3 | **1.4.5 no off-scale spacing** | 24 raw-literal gaps (G4d), 4 of them off-scale (10dp, 14dp ×3), plus 20dp/28dp/64dp gaps in `layout_servers_empty.xml:13,25,26,27,48` | P1 |
| B4 | **1.1 identical card grids** / **2.4.3 the uniform-card tell** | `item_recycler_main.xml:8-11,18` - every row is a free-standing rounded rectangle with 4dp margins and its own `bg_server_row` outline. A 150-row list of identical rounded rectangles is precisely the tell; `24-tab-conformance.md:91` puts Серверы on **R1 Ledger** ("rows at 0 with a 68-inset hairline") | P1 |
| B5 | **1.3 display fonts / inline type** and **5.2 roles, not sizes** | 3 inline `textSize` (G3). `item_recycler_main.xml:118-121` applies `TextAppearance.App.Numeric` and then overrides it with `textFontWeight="700"` + `textSize="12sp"`, so the ramp is declared and immediately discarded | P1 |
| B6 | **3.6 the coloured tile system is exactly three, at most three visible** | `sheet_server_actions.xml` draws **five** `bg_icon_blue` tiles (`:60,98,136,174,212`) plus one `bg_icon_red` (`:250`) = **6 coloured tiles on one surface**. D-5 and 3.6 say neutral is the default and three is the ceiling | P1 |
| B7 | **22-components §8.6 Row.Destructive: tile stays neutral** | `sheet_server_actions.xml:250` red tile **and** `:268` red label - the same signal twice | P2 |
| B8 | **1.4.10 no Latin UI text** | `layout_servers_header.xml:47` `contentDescription="@string/title_sub_update"` = "Update subscription"; `:59` `="@string/connection_test_pending"` = "Check Connectivity"; `:71` `="@string/menu_item_add_config"` = "Add config". All three are read aloud by TalkBack | P1 |
| B9 | **1.4.13 no screen without its states** | Empty-search, error, offline, loading, partial and every product gate state are absent - see §5 | P1 |
| B10 | **1.3 reinventing standard affordances** | `MainActivity.kt:751-782` `paintMenuItem()` hand-tints a platform `PopupMenu` and rewrites item titles as `SpannableString`s with a `ForegroundColorSpan`. A themed overflow is the standard affordance; repainting one per item is not | P3 |
| B11 | **6.2 a colour never means two things** | `MainRecyclerAdapter.kt:218-219` paints a *successful* latency in `?attr/pingGood` `#22C55E`, which 6.2 reserves for «подключено» / «оплачено». `32-master-plan-android.md:2395` rules this out explicitly: "Ping is neutral text, not a green or red dot" | P1 |
| B12 | **1.1 text that overflows** | `item_recycler_main.xml:63-64` `maxLines="1"` + `ellipsize="end"` on `tv_name`, inside a row whose fixed 44dp/36dp siblings do not scale. At font scale 200% a 40-character remark shows roughly its first eight characters | P1 |

---

## 3. The five focus questions, answered with evidence

### 3.1 The unified server icon (owner request 0.4.7)

**Today.** `item_recycler_main.xml:40-50` is a **`TextView`**, 28×28dp, `background="@drawable/bg_flag_tile"`
(`?attr/colorSurfaceVariant`, `radius_tile` 12), `textSize="18sp"`, holding an emoji string from
`FlagUtil.resolveFlag()`. There is **no 40dp tile slot**: the 28dp square *is* the whole icon, so the
server icon on this screen is 12dp narrower than every icon tile in the app (`tile_size` 40 is used
by the action sheet two taps away, `sheet_server_actions.xml:58`). The globe fallback is the emoji
`🌐` (`FlagUtil.kt:19`), not `ic_globe_24dp.xml`, which already exists in `res/drawable/`.

**Three surfaces disagree about what a server looks like.** The row strips the leading flag from the
name (`MainRecyclerAdapter.kt:202` `FlagUtil.stripLeadingFlag`); the sheet header does **not**
(`ServerActionsSheet.kt:43-44` uses `profile.remarks` raw), so the same server reads
«Нидерланды, Амстердам» in the list and «🇳🇱 Нидерланды, Амстердам» in the sheet. The sheet has no
tile at all. `10.5` and `24-tab-conformance.md` A-15 both require one treatment.

**Decision.** One layout fragment, `res/layout/view_server_icon.xml`, `<include>`d by the row, the
sheet header, the Home connect hero's server row and the notification's server line:

```
FrameLayout            @dimen/tile_size 40 × 40
                       background @drawable/bg_tile_neutral   (radius @dimen/radius_tile 12,
                                                               fill @color/icon_tile_neutral)
├── ShapeableImageView  id=iv_flag   @dimen/flag_size 28 × 28, layout_gravity center
│                       shapeAppearanceOverlay cornerSize @dimen/radius_flag  (NEW, 8dp)
│                       src @drawable/flag_<iso2>, scaleType centerCrop
└── ImageView           id=iv_globe  @dimen/tile_glyph 22 × 22, layout_gravity center
                        src @drawable/ic_globe_24dp, tint @color/icon_glyph_neutral
```

Exactly one child is `VISIBLE`. Every dimension is an existing token except `radius_flag`, which does
not exist and **must be added to `res/values/dimens.xml` first** (§3 of the law: "you add it to the
token file first, with a comment saying what it is for"). Its comment is the reason from
`32-master-plan-android.md` §6.3: a 12dp radius on a 28dp square over-rounds; 8dp keeps it in the
same family as the 40dp tile at 12.

`FlagUtil.resolveFlag()` changes its **return type** to `@DrawableRes Int`, defaulting to
`R.drawable.flag_xx`. `parseCountryCode`, `bracketedCode`, `leadingCode`, `looksLikeProse` and
`containsWord` are **not touched** - they are evidence-backed ("a missing flag is cheaper than a wrong
one", `FlagUtil.kt:96`) and correct. `extractFlagEmoji` stays, but it is an *input parser* (reading an
emoji out of a remark the user typed is allowed by 1.4.4's user-content clause); its result is mapped
back to an ISO-2 code and then to a drawable, never returned as a rendered string.
`stripLeadingFlag` stays and is applied at **every** call site, including the sheet header.

**Raster inventory.** `/home/user/v2rayN/v2rayN/v2rayN.Desktop/Assets/Flags/` holds **16** files:
`de eu fi fr gb jp lv nl pl ru se sg tr ua us xx`. `FlagUtil` resolves **31** ISO-2 codes
(`FlagUtil.kt:253-257`). Porting the desktop set as-is leaves 17 of the 31 falling to the globe -
`DK NO LT EE HK KR CA CH ES IT AT CZ IR IN AU BR AE`. Decision: port the 15 country files plus `xx`
into `res/drawable-nodpi/flag_<iso2>.png`, and add the 17 missing ones in the same commit so the
resolver and the asset set agree. `eu.png` has no `FlagUtil` code and is not ported.

### 3.2 Selection versus connection

**They are already separated in behaviour and conflated in the UI.**

`MainActivity.kt:1524-1547` is correct and must be preserved: "Tapping a server row SELECTS it - it
never connects and never reconnects." When the tunnel is up and a different server is selected,
`promptApplySelectedServer()` (`:1553-1569`) offers an explicit «Применить» snackbar rather than
tearing down the connection. That is the right model.

**The defect is that the list draws one marker and it carries both meanings.** The only state a row
can express is `binding.infoContainer.isSelected` (`MainRecyclerAdapter.kt:226`), fed from the
mirrored `selectedGuid`. While the tunnel runs on server A and the user picks server B:

| | server A (connected) | server B (selected) |
|---|---|---|
| Today | drawn as an ordinary row | drawn as the one highlighted row |
| The user reads | "not in use" | "connected" |
| The truth | carrying all traffic | queued for the next connect |

**Decision - two distinct, non-competing signals, per 6.3 (never colour alone):**

- **Selected** (= will be used on the next connect) is the §18 Selection indicator: `@color/accent_fill_12`
  fill + title weight 700 + a 20dp `ic_action_done` in `?attr/colorPrimary` in an **always-reserved**
  trailing slot. Three axes, zero reflow.
- **Connected** (= the tunnel is up on this server, right now) is a **`Chip.Status.Ok`** reading
  «Подключено», placed in the row's subtitle line beside the protocol chip. It is a word plus a
  colour, never a bare dot. It appears on **at most one row in the app** and it is driven by
  `MainViewModel.isRunning` + the running guid, not by `selectServer`.
- When the two coincide (the normal case) the row shows both, and that is correct: it is selected and
  it is connected.
- **Not permitted:** a second highlight colour, a left stripe (1.1), a green row fill, or reusing the
  check glyph for connection. Green stays a status hue and never becomes a selection colour (1.4.1).

`ServerActionsSheet`'s «Сделать основным» (`server_action_set_default`) is a third name for the same
concept and breaks the 9.3 terminology lock's spirit. Decision: the row title becomes «Выбрать этот
сервер» when the row is not selected, and the item is **hidden** when it already is. `«основной»`
disappears from the product.

### 3.3 Latency: can a stale or failed measurement be told from a real one?

**No, and the screen can show a plausible number for a host that was never reached. Four separate
paths.**

`ServerAffiliationInfo` (`dto/entities/ServerAffiliationInfo.kt`) has exactly **one** field,
`testDelayMillis: Long`. There is no timestamp, no network identity, no config fingerprint.

| Path | Evidence | What the user sees |
|---|---|---|
| **Failure renders as a number** | `SpeedtestManager.kt:76,79,101,108,122,129,192` all return `-1L`; `CoreTestService.kt:121` writes it verbatim; `ServerAffiliationInfo.kt:8` returns `testDelayMillis.toString() + "ms"` | **`-1ms`**, in red. A negative millisecond value with a Latin unit |
| **A measurement never expires** | `clearAllTestDelayResults` has exactly three callers, all inside `MainViewModel.testAll*` (`:274, :297, :340, :367`) - i.e. only at the start of the next test | A value measured last week on another Wi-Fi renders identically to one measured a second ago |
| **Editing a server keeps the old host's number** | `MmkvManager.encodeServerConfig()` rewrites `profileFullStorage` and **never touches `serverAffStorage`**, which is keyed by the same guid | Change a server's address and port; the row still shows the previous host's `48ms`. This is literally a plausible number for a host that was never reached |
| **Undo restores a number without restoring its provenance** | `MainActivity.kt:2626` `snapshot.delays[guid]?.let { encodeServerTestDelayMillis(guid, it) }` | A server deleted and restored ten minutes later shows its pre-deletion figure as if fresh |

A fifth, softer path: `markAllServersTesting()` (`MainActivity.kt:870-873`) writes the `-2L` "testing"
sentinel to **every** server and persists it in MMKV. If the process dies mid-run the sentinel
survives, and on the next launch every row draws a spinner with no test running. Nothing clears it.

**Decision - `testedAt` plus a four-state renderer.**

Add `var testedAt: Long = 0L` and `var testedOnNetwork: Long = 0L` to `ServerAffiliationInfo`, written
by the same call that writes `testDelayMillis`. `testedOnNetwork` is a monotonic generation counter
bumped by a `ConnectivityManager.NetworkCallback` on every `onAvailable` / `onLost`.

| `testDelayMillis` | Ping slot renders | Colour | Numeric role |
|---|---|---|---|
| `0L` - never measured | **blank** | - | n/a. Not `-`, not `n/a`, not `0` |
| `-2L` and `now - testedAt < 60 s` | 20dp `spinner_arc` (§17.1), not the platform `ProgressBar` | `colorOnSurfaceVariant` | n/a |
| `-2L` and `now - testedAt >= 60 s` | treated as `0L`, and the sentinel is rewritten to `0L` on the next `refreshServerLists` | - | n/a |
| `< 0L` (`-1`) | **«нет ответа»** | `?attr/pingBad` | no - it is a word |
| `> 0L`, fresh | **«48 мс»** (`N` + U+00A0 + `мс`) | `?attr/colorOnSurfaceVariant` - **neutral, never green** | yes, `tnum lnum zero` |
| `> 0L`, stale (`testedOnNetwork` differs, or `now - testedAt > 30 min`) | **blank** | - | n/a |

**Why stale renders blank rather than "marked stale".** 9.6 says stale data is kept and marked - and
that is right for a balance or an expiry date, which are still approximately true. A latency figure is
not approximately true on a different network; it is wrong. The 9.6 grammar is applied one altitude
up instead: when any row's measurement went stale, the **meta line** carries one quiet bar,
`colorSurfaceContainerHigh`, radius 12, 12 padding, Body text (§15's offline bar spec):
«Задержка измерена давно. Проверьте заново.» with a Tertiary «Проверить». One message, not 150.

**Invalidation writers** (all set `testDelayMillis = 0`, `testedAt = 0`):
`MmkvManager.encodeServerConfig()` when address, port, protocol or transport changed;
`SubscriptionUpdater` when a refresh replaces a guid's profile; the 60 s `-2L` sweep.
`undoBulkDelete` restores `testedAt` and `testedOnNetwork` alongside `delays`, so a restored server is
as stale as it actually was.

**Consequences elsewhere, all improvements:** `MainViewModel.selectFastestServer()` (`:562-568`) already
takes `delay in 1 until bestDelay`, so it ignores `-1` and `-2` correctly and needs no change beyond
also ignoring stale entries. `sortByTestResultsForSub()` (`:565-580`) maps `delay <= 0L` to `999999`,
which sinks failures and untested servers to the bottom - correct, keep. `MmkvManager.removeInvalidServer()`
(`:296-320`) selects on `testDelayMillis < 0L`, which after this change would also catch `-2L`; it must
select on `testDelayMillis == -1L`, or «Удалить недоступные» will delete servers that were merely
mid-test.

**Copy.** «214ms» violates 9.2 twice (no space, Latin unit). `32-master-plan-android.md` §4.3 fixes the
format at `N мс`; the whole ping column goes through the mandated single formatter `util/NumberFormat.kt`,
which does not exist yet and is another wave's deliverable - a hard dependency, logged in §8.

### 3.4 Virtualisation, stable IDs, DiffUtil

**Virtualisation: correct on the Серверы tab, broken on Home, same adapter.**

`activity_main.xml:474-478` - `rv_servers` is `match_parent` inside a `layout_weight="1"` `FrameLayout`,
outside the `NestedScrollView` that wraps Home (`:41-452`). It virtualises properly, and
`setHasFixedSize(true)` (`MainActivity.kt:661`) is legitimate.
`rv_home_servers` is **inside** that `NestedScrollView` with `isNestedScrollingEnabled = false` and
`setHasFixedSize(false)` (`MainActivity.kt:666-668`), so every row is measured and laid out. With 150
servers that is 150 inflations on the Home tab. `00-rules.md` 4.6 makes it a P1 performance defect.
It is another wave's layout, but it is **this adapter's** problem and is logged in §8.

**Stable IDs: absent.** `setHasStableIds` is never called and `getItemId` is never overridden.

**DiffUtil: absent.** `notifyDataSetChanged()` at five sites: `:86` (`setSections` fallback), `:152`
(`toggleCollapseAll`), `:192` (header tap), `:305` (`removeServerSub`), `:334` (`syncSelection`
fallback).

**Main-thread JSON parse per bind.** `MainRecyclerAdapter.kt:211` calls
`MmkvManager.decodeServerAffiliationInfo(guid)`, which is `JsonUtil.fromJsonSafe(...)`
(`MmkvManager.kt:250`) - a full JSON parse, on the main thread, on **every row bind**, i.e. once per
row per fling frame. `11.5`'s first bullet: "No synchronous I/O, JSON parsing or crypto on the main
thread in any UI path." The `customProtoCache` beside it (`:275-298`) shows the author already knew
this and cached the *other* parse; the affiliation parse was missed.

**The load-bearing fix is preserved, not removed.** `MainRecyclerAdapter.kt:135-142` (the `selectedGuid`
mirror) and `:313-339` (`syncSelection`'s "if either row cannot be located, repaint everything")
exist because MMKV cannot notify and selection is written from three places that own no list. Both
**stay**. The change is only in how "repaint everything" is expressed.

**Decision.**

1. `Row` gains a stable key: `Row.Header` → `"h:" + subId`, `Row.Server` → `"s:" + guid`.
   `setHasStableIds(true)`; `getItemId()` returns `key.hashCode().toLong()`.
2. `Row.Server` carries the **rendered** state, not a reference to it: `guid, title, protocol,
   transport, delay, testedAt, isSelected, isConnected`. `isSelected` is filled from `selectedGuid`
   inside `rebuildRows()`. This is what makes a selection change a *content* change the differ can see.
3. The adapter becomes `ListAdapter<Row, BaseViewHolder>`. `areItemsTheSame` compares the key;
   `areContentsTheSame` compares the whole `Row.Server` data class.
4. Every `notifyDataSetChanged()` becomes `submitList(rebuildRows())`. `syncSelection`'s fallback is
   unchanged in **semantics** - it still says "recompute the whole list" - but the differ turns that
   into the same two-row update when both rows are present, and into the correct larger update when
   one is not. The "two rows painted as selected at once" bug the comment at `:313-322` describes
   becomes impossible by construction rather than by a guard.
5. The affiliation read moves out of `bindServer`: `rebuildRows()` reads it once per server per
   rebuild, on `Dispatchers.Default`, into the `Row.Server`.
6. `item_recycler_footer.xml` and `VIEW_TYPE_FOOTER` are deleted; `getItemCount()` stops being
   `rows.size + 1`. The bottom inset becomes
   `rvServers.updatePadding(bottom = navBarInset + bottomNavHeight + space_16)` with `clipToPadding=false`
   (already set at `activity_main.xml:476`).
7. Section headers become sticky (`4.6`: "servers grouped by subscription: yes") via an
   `ItemDecoration`, not a second view hierarchy.
8. `ItemTouchHelperAdapter` / `ItemTouchHelperViewHolder` (`:363-372`) are inert - no `ItemTouchHelper`
   is attached - and `BaseViewHolder.onItemSelected/onItemClear` (`:344-352`) paint
   `Color.LTGRAY`, a hard-coded colour that would be visible if it ever ran. Delete all four.

### 3.5 Where the bulk actions belong, and how multi-select works

**Today.** Four 36dp icon buttons in the header (`layout_servers_header.xml:29-76`): collapse-all,
refresh-all, ping-all, add. The "+" opens a `PopupMenu` on `menu_main.xml` whose second group
(`group_server_list`) carries sort / export / delete-duplicates / delete-invalid / delete-all
(`MainActivity.kt:728-737`, `prepareMenu` `:750-767`). The "+" glyph is still `ic_add_24dp` while its
`contentDescription` was reassigned in code to «Ещё» (`MainActivity.kt:717`) - the glyph and the name
disagree, which is the one thing an icon-only control may not do.
`locateSelectedServer()` (`MainActivity.kt:2759`) has **no caller** - grep returns the definition only.

The restored machinery underneath is good and must be kept: `ServersSnapshot` / `snapshotServers()` /
`undoBulkDelete()` (`:2585-2647`), `bulkDeleteAllowed()`'s refusal while the tunnel is up
(`:2653-2664`), and `sortByTestResults()`'s "nothing measured yet, here is the check" snackbar
(`:2668-2694`). All three are exactly the §7.5 / §9.4 grammar.

**Decision - two surfaces, and only two.**

**(a) The toolbar carries one trailing `Button.Icon`: «Ещё», `ic_more_vert_24dp`, 48dp**
(`00-rules.md` 4.8: "keeps at most one trailing action; more go in an overflow";
`32-master-plan-android.md` §12.3 names the glyph). Its overflow, in three divider-separated groups:

| # | Item | Enabled / visible when | Behaviour |
|---|---|---|---|
| 1 | `Добавить сервер` | always | opens the add-source sheet: «Сканировать QR-код», «Вставить из буфера», «Ввести ссылку», «Создать вручную» |
| 2 | `Обновить провайдеров` | ≥1 provider | per-provider progress renders on each group header, not as a screen spinner |
| 3 | `Проверить задержку` | ≥1 server | every row's ping slot goes to the 20dp arc |
| 4 | `Свернуть все группы` / `Развернуть все группы` | ≥2 groups | label states the next action |
| 5 | `Выбрать несколько` | ≥1 server | enters multi-select |
| - | *divider* | | |
| 6 | `Экспортировать в буфер` | ≥1 unlocked server | already correctly disabled when all are locked (`MainActivity.kt:758`) - keep |
| - | *divider* | | |
| 7 | `Удалить недоступные` | ≥1 server at `testDelayMillis == -1L` | act + undo strip |
| 8 | `Удалить все серверы` | ≥1 server | the one confirm dialog on this screen; primary «Удалить все», not «OK» |

Eight rows, not the "5 maximum" of `32-master-plan-android.md` §12.3. That sentence predates both the
restored bulk actions and the add-source sheet absorbing six import items; the only alternative is a
second toolbar control, which 4.8 forbids outright. Recorded as a deliberate deviation per §17.3.

**«Сортировать по задержке» is removed from the overflow.** It is the Tertiary cycle control on the
meta line (`24-tab-conformance.md` §3.2.3, `32-master-plan-android.md` §12.3): label = the current
value, trailing 20dp `ic_unfold_more`, cycling «По порядку» → «По задержке» → «По имени» in place.
A sort whose current value is invisible is not a sort control.

**«Удалить дубликаты» is removed as a blind action.** It becomes overflow → `Выбрать дубликаты`, which
enters multi-select with the duplicates pre-checked. A destructive bulk action the user can see before
confirming is the §7.5 answer; the existing undo strip still backs it.

**(b) Multi-select.** Entered **only** from the overflow. Long-press stays the per-item sheet (§4 P0) -
two meanings on one gesture is the conflation this audit exists to remove.

- The 56 seamless toolbar becomes a selection toolbar in place, no new bar:
  `[16][Button.Icon close, cd «Выйти из выбора»][16][Title «Выбрано 3 из 15»][flex][Button.Icon «Ещё»][16]`.
  The title uses `plural_selected_of` (`32-master-plan-android.md` §4.4).
- Rows reuse the **already-reserved** trailing check slot from §18. No checkbox column appears, so
  nothing reflows on entering or leaving the mode.
- **The two meanings never share a visual.** On entering multi-select, the connection/selection marker
  moves off the check slot: the currently selected server carries a `Chip.Neutral` «Текущий» in its
  subtitle line for the duration of the mode, and the check slot means "in this batch" only.
- Selection-mode overflow: `Выбрать все`, `Снять выделение`, divider, `Экспортировать выбранные`,
  divider, `Удалить выбранные`.
- System Back exits the mode before it leaves the tab (`11.3`, `7.7`). The close button does the same.
  Scroll position and the search query survive both (`7.7`: "Back restores scroll position, filter
  state and input").
- `bulkDeleteAllowed()` gates «Удалить выбранные» exactly as it gates the overflow deletes today.

**(c) «Найти выбранный» does not become a menu item.** `locateSelectedServer()` is rewired to run
**automatically** when the tab is shown: if the selected row is off-screen it is scrolled into view,
and if it sits inside a collapsed group that group expands first. A named command for "scroll to a
row" is an invented affordance for a standard task (§1.3); a list that opens where you left it is the
standard. This deletes a menu item and revives dead code in one move.

**(d) The three remaining header icon buttons are deleted.** Collapse-all, refresh-all and ping-all
move into the overflow above, where they are named instead of guessed at, and their 36dp targets stop
being an accessibility defect.

---

## 4. The P0 regression: the action sheet has no caller

`MainActivity.kt:674-675` assigns `serversAdapter.onItemLongClick` and `homeAdapter.onItemLongClick`.
`MainRecyclerAdapter.kt:232-235` binds **only** `setOnClickListener`, with the comment
"Long-press server-actions menu removed: long-press is a no-op (no listener set)", and `:52-56`
documents the property as "no longer invoked by the adapter".

Consequence, verified by grep: `showServerActions()` (`:683`), and through it `showQRCode()` (`:1461`),
`share2Clipboard()` (`:1468`), `editServer()` (`:1482`) and `removeServer()` (`:1502`) have **no
reachable caller from the servers list**. A user cannot rename, edit, duplicate, share, QR or delete a
single server. `ServerActivity`, `ServerCustomConfigActivity`, `ServerGroupActivity` and
`ServerProxyChainActivity` are unreachable from this screen.

This is a functional regression on today's build, not a design gap, and it is the first item in the
work order. `ServerActionsSheet.kt` itself is correct: the locked-profile guard (`:46-53`) and the
dismiss-then-act ordering (`:55-60`) are both right and stay.

---

## 5. State matrix

`00-rules.md` §15 plus the product gate states. **Implemented = drawn today with evidence.**

| State | Implemented | Evidence / gap | Decision |
|---|---|---|---|
| **Default** | yes | `item_recycler_main.xml` | Restyled to the R1 ledger row (§6) |
| **First run** | partial | `layout_servers_empty.xml` shows a card with two buttons and **no body line** | §15 silhouette: 64 neutral tile, Headline title, Subtitle line, **one** action |
| **Loading** | **no** | No skeleton anywhere; `showLoading()`/`hideLoading()` is a screen-blocking overlay | 4 `Skeleton` rows (§16), after 300ms, `motion_pulse` 1000 |
| **Empty (no servers)** | partial | `MainActivity.kt:942` `showEmpty = serverCount == 0 && !filtersActive`. Title «Нет серверов» is right; the line is missing; two actions | «Нет серверов» / «Добавьте провайдера или отсканируйте QR-код, чтобы появились серверы.» / «Добавить провайдера» |
| **Empty (search found nothing)** | **NO - P1** | Same line: `!filtersActive` **suppresses** the empty state, so a search with no results renders a **blank white area with no text at all** | «Ничего не найдено» / «Попробуйте другой запрос.» / «Сбросить поиск» |
| **Error** | **no** | A provider refresh that fails surfaces as a toast at best | §15 error silhouette: alert glyph, the mapped cause, Tertiary «Повторить» |
| **Offline** | **no** | Nothing marks the list stale | Quiet bar: «Нет сети. Показаны последние данные.» + «Повторить» |
| **Partial** | **no** | One provider failing to refresh is invisible; the rest of the list renders as if healthy | Failure marked **on that provider's group header**, inline; the other groups render normally |
| **Long content** | **no** | `maxLines="1"` + `ellipsize="end"` (`item_recycler_main.xml:63-64`) beside a 48dp `minWidth` ping and fixed 36/44dp siblings | Title `maxLines="2"`; the transport line goes `gone` below 6 rendered characters rather than showing an ellipsis |
| **Short content** | yes | One server renders fine; a single provider correctly suppresses headers (`MainRecyclerAdapter.kt:97` `distinctProviderCount() > 1`) | Keep |
| **Success** | partial | `menu_actions_sorted`, `menu_actions_restored`, `menu_actions_all_deleted` are good snackbars | Keep; add «Сервер добавлен» on the scan return |
| **Disabled / gated** | **no** | The screen knows nothing about the account | see the seven rows below |

### Product gate states

| Gate | Drawn today | Decision - where it lands on this screen |
|---|---|---|
| `нет подписки` | no | The Departament provider group header carries a `Chip.Status.Warn` «Нет подписки» and a Tertiary «Купить». Manual servers below are untouched and still connectable |
| `подписка истекает` | no | Same header, `Chip.Status.Warn` «Истекает», Tertiary «Продлить». The rows stay live |
| `подписка истекла` | no | Same header, `Chip.Status.Error` «Истекла». That group's rows go to 0.38 and are not tappable; the check slot is empty; «Купить» is the group's Tertiary. Manual servers stay live |
| `триал` | no | `Chip.Accent` «Пробный» on the group header - the tariff badge treatment, and the **only** accent chip on the screen. `AccountViewModel.kt:220` already resolves `isTrial`; do not re-derive it from the squad or the tariff name |
| `Telegram не привязан` | no | **Not on this screen.** It has no bearing on choosing a server; §15's list is "whichever apply". It belongs to Аккаунт |
| `нет серверов` | partial | The empty state above |
| `подключение` | no | The connecting server's row carries `Chip.Status.Warn` «Подключение», the 20dp arc replaces its check, and the row is not tappable |
| `подключено` | **no** | `Chip.Status.Ok` «Подключено» on the running server's row - §3.2 |
| `отключение` | no | `Chip.Neutral` «Отключение» on the same row, targets disabled |
| `ошибка туннеля` | no | `Chip.Status.Error` «Ошибка» on the row that failed, plus the §15 error bar above the list with the mapped cause and «Повторить» |
| `лимит устройств` | no | A quiet bar above the list: «Достигнут лимит устройств. Отвяжите одно из устройств в разделе «Устройства».» (9.4 verbatim) + Tertiary «Устройства». Rows stay visible; connecting is what fails, not choosing |

**Eleven of the eighteen rows above are absent.** By §17.2 each is at least P1.

---

## 6. The redesigned screen, component by component (no new components)

Every element below is one of the 15 in `22-components.md` R15. Every value is an existing token
except the two flagged NEW, which are added to `dimens.xml` before use.

```
Toolbar §12.1A                       @dimen/toolbar_height 56, ?android:attr/colorBackground
  [16] «Серверы» TextAppearance.App.Title   [flex]   Button.Icon «Ещё» ic_more_vert_24dp 48  [16]
[ @dimen/space_8 8 ]
Text field §4                        id=et_search, marginH @dimen/screen_gutter
    minHeight @dimen/btn_height 48   (minHeight, never a fixed height - R2)
    radius @dimen/radius_button 16   (D-7)   border 1dp ?attr/colorOutlineControl (D-9)
    leading @dimen/glyph_20 20 ic_search      trailing 20 ic_close when non-empty
    hint «Поиск по названию или стране»
[ @dimen/space_12 12 ]
Meta line                            marginH 16, minHeight @dimen/view_height_dp48 48
    TextView weight 1  App.Subtitle + Numeric   «15 серверов · 2 провайдера»
    Button Tertiary    id=btn_sort   label = current sort, trailing 20 ic_unfold_more
[ @dimen/space_8 8 ]
RecyclerView                         clipToPadding=false
    paddingBottom = navBarInset + bottomNav + @dimen/space_16
    StickyHeaderDecoration + a 1dp ?attr/colorOutlineVariant hairline at
      marginStart @dimen/divider_inset_start 68, between rows of one group only
```

**Group header** (`item_section_header.xml`), sticky:

```
[16][20 ic_chevron_right, rotates 0→90 over motion_state 220][8]
    [provider name  App.Title 16/700, weight 1][count App.Caption][ status chip ][40 Button.Icon kebab][16]
minHeight @dimen/view_height_dp48 48
```

48, not the 40 of `24-tab-conformance.md` §3.2.4: the header is a touch target (it collapses the
group) and 7.2 sets a 48dp floor that a screen spec cannot lower. The kebab opens a sheet with
«Обновить», «Проверить задержку», «Переименовать», «Открыть ссылку», «Настройки провайдера»,
hairline, «Удалить провайдера» in `@color/color_destructive_text`. `contentDescription` on the root
states the collapsed state so TalkBack announces it (14.9) - today the chevron is
`importantForAccessibility="no"` (`item_section_header.xml:26`) and the state is announced nowhere.

**Server row** - `Row` §8.1 geometry, ledger rhythm, no per-row card:

```
[16][ view_server_icon 40 ][12][ text column weight 1 ][12][ ping ][8][ check 20 ][16]
                                title     App.Title 16/700, maxLines 2
                                subtitle  Chip.Neutral «VLESS»  +8+  App.Subtitle «Reality · TCP»
                                          + Chip.Status.Ok «Подключено» when the tunnel is on this row
minHeight @dimen/row_min_height 56       paddingV @dimen/space_12 12
background @drawable/bg_row              (ripple over a selector; state_pressed →
                                          ?attr/colorSurfaceContainerHigh - R5, rows do not scale)
focus      @drawable/bg_row state_focused → 2dp ?attr/colorPrimary inset ring (R7)
```

**Three changes from today that need stating plainly.**

1. **The row stops scaling on press.** `item_recycler_main.xml:28` attaches `@anim/press_scale`. That
   is correct **today**, because the row is a free-standing object with 4dp margins and its own
   outline - `res/anim/press_scale.xml`'s own comment lists "server row" among the objects that
   scale. Once the row becomes a ledger slice with hairlines above and below it, R5 applies and
   scaling tears those hairlines. The `stateListAnimator` comes off and the background step replaces
   it. `press_scale.xml`'s comment should be amended by whichever wave owns it.
2. **The selected state loses its border axis.** §18 specifies fill + border + weight + check. A
   ledger row has no border to recolour, and adding one on selection is the geometry shift §18 itself
   bans ("Border width never changes. Radius never changes."). Three axes remain - `@color/accent_fill_12`
   fill, title weight 700, and the 20dp `ic_action_done` in the always-reserved slot - which clears
   7.1's "two axes minimum" with one to spare. This resolves the three-way conflict between §18
   (12% fill + border), `32-master-plan-android.md` §12.4 (`colorSurfaceContainerHighest`, "no fill
   tint") and `24-tab-conformance.md` §3.2.6 (12% fill, check **replacing** the ping value) in favour
   of §18, because §18 is the component definition and the other two are screen prose. The check does
   **not** replace the ping value: they are different facts, and hiding «нет ответа» on the one row
   the user just chose is the worst possible place to hide it.
3. **`layout_indicator` is deleted.** `item_recycler_main.xml:34-38` is a 0×0 `gone` `View` that
   exists only so `MainRecyclerAdapter.kt:227-230` can call `setBackgroundColor` on it. Both go.

**The selection hook changes name, and this will silently paint nothing if it is missed.**
`res/drawable/bg_row.xml` landed today and keys its selected fill off **`state_activated`**
(`bg_row.xml`, the `<item android:state_activated="true">` branch), which is what
`22-components.md` §18's platform-mapping row specifies ("Set by `view.isActivated = true` on the
adapter's bind"). `MainRecyclerAdapter.kt:226` sets **`isSelected`**, which `bg_server_row.xml:16`
keys off today. The moment `bg_row` replaces `bg_server_row` in W6, a selected row draws no fill at
all unless `:226` becomes `binding.root.isActivated = selected`. Set **both** during the transition,
and set `AccessibilityNodeInfo.isSelected` explicitly so the announcement survives the switch
(`isActivated` alone does not announce).

**The two chips on this row use two different faces, and picking the wrong one is invisible in
review.** `TextAppearance.App.Chip` is Space Grotesk (`values/styles.xml`, `@font/space_grotesk`),
which maps **zero** Cyrillic codepoints (D-1). The protocol chip «VLESS» is a Latin technical token
and is correct on `App.Chip`. The status chip «Подключено» is Russian and must use
**`TextAppearance.App.Chip.Ui`** (`@font/golos_text_medium`), which landed alongside the ramp for
exactly this case. Same rule for «Истекает», «Истекла», «Пробный», «Текущий», «Подключение»,
«Отключение», «Ошибка». The action sheet's delete row uses `TextAppearance.App.Title.Destructive`,
which also now exists, rather than an inline `textColor`.

**Empty state** - `res/layout/layout_state_empty.xml`, §15 silhouette, one file parameterised by the
binding, replacing `layout_servers_empty.xml`:

```
[ 64 tile, radius_card 20, ?attr/colorSurfaceContainerHighest, 32 glyph ?attr/colorOnSurfaceVariant ]
  space_16   Title  App.Headline 24/700, centred
  space_8    Line   App.Subtitle 13/400, centred, maxWidth 320
  space_24   one action
```

The tile is **neutral**. `layout_servers_empty.xml:35` tints its glyph `?attr/colorPrimary` today,
which spends the accent budget on decoration in the one state that has no primary action to point at.
The `64dp` tile and `32dp` glyph have no tokens (`empty_icon` is 64 and fits the tile;
**`empty_glyph` 32 is NEW** and is added to `dimens.xml` before use).

**Action sheet** (`sheet_server_actions.xml`), §13.1:

- Header gains `view_server_icon` + the **stripped** remark, so the sheet and the row agree.
- Six hand-rolled `LinearLayout` rows become `<include layout="@layout/row_action.xml"/>` /
  `row_destructive.xml`. Labels move from `TextAppearance.App.Body` 14/400 to
  `TextAppearance.App.Title` 16/700 (§8.1).
- All tiles **neutral** (`@color/icon_tile_neutral` / `@color/icon_glyph_neutral`). The delete row
  keeps its red **title** (`@color/color_destructive_text`) and loses its red tile (§8.6). Six
  coloured tiles become zero.
- Row order per A-15: «Выбрать этот сервер», «Проверить задержку», «Изменить», «Дублировать»,
  «Поделиться QR-кодом», «Скопировать ссылку», hairline, «Удалить сервер».
  «Проверить задержку» is missing entirely today.
- `BottomSheetDialog` → `BottomSheetDialogFragment`, so the sheet survives rotation (11.3). Focus
  moves into the sheet on open and returns to the row on close (7.6) - neither happens today.
- The existing locked-profile guard (`ServerActionsSheet.kt:46-53`) is preserved verbatim.

---

## 7. Work order

| # | Sev | Title | Files | Change | Spec | Risk |
|---|---|---|---|---|---|---|
| W1 | **P0** | Rebind long-press so the action sheet has a caller | `MainRecyclerAdapter.kt:232-235,52-56` | `binding.infoContainer.setOnLongClickListener { onItemLongClick?.invoke(guid); true }`; delete the two stale comments | `24-tab-conformance.md` §3.2.7; `32-master-plan-android.md` §12.2 | Very low. `MainActivity.kt:674-675,683-702` is already written and correct |
| W2 | **P1** | Latency cannot lie: `testedAt`, four states, «нет ответа» | `ServerAffiliationInfo.kt`, `MmkvManager.kt:259-271,296-320`, `CoreTestService.kt:121`, `MainViewModel.kt:274,297,340,367`, `MainActivity.kt:870-873,2626`, `MainRecyclerAdapter.kt:208-219` | §3.3 table; `removeInvalidServer` selects `== -1L` not `< 0L` | `00-rules.md` 9.2, 9.6, 6.2; `32-master-plan-android.md` §4.3, §12.4 | Medium: touches the MMKV record shape. Default both new fields to `0L` so old records read as never-measured |
| W3 | **P1** | Unified server icon; `FlagUtil` returns a drawable | new `view_server_icon.xml`, `bg_tile_neutral.xml`, `res/drawable-nodpi/flag_*.png` ×32, `dimens.xml` (+`radius_flag` 8), `FlagUtil.kt`, `item_recycler_main.xml:40-50`, `sheet_server_actions.xml:29-40`, `ServerActionsSheet.kt:43` | §3.1 | `00-rules.md` 0.4.7, 10.4, 10.5, 1.4.4; `32-master-plan-android.md` §6.3 | Medium. `FlagUtil.kt` is being edited by another wave - coordinate; the parsing half is untouched |
| W4 | **P1** | The four missing list states | `MainActivity.kt:934-945`, new `layout_state_empty.xml`, delete `layout_servers_empty.xml` | Empty-search, error, offline, partial. Drop the `&& !filtersActive` suppression | `00-rules.md` §15, 9.5, 9.6; `22-components.md` §15 | Low |
| W5 | **P1** | Stable IDs + `DiffUtil`; kill 5 × `notifyDataSetChanged` | `MainRecyclerAdapter.kt` throughout | §3.4 items 1-6. **Keep** the `selectedGuid` mirror and the full-refresh fallback semantics | `00-rules.md` 11.5; `24-tab-conformance.md` §3.2.10 | Medium-high: the selection-repaint path is load-bearing. Test: select while a group is collapsed; select during a subscription import; select from fast-connect |
| W6 | **P1** | Row becomes a ledger row; selection on three axes | `item_recycler_main.xml`, `MainRecyclerAdapter.kt:226`, `custom_divider.xml` → 68 inset + `?attr/colorOutlineVariant`, delete `bg_server_row.xml` | §6. Adopt the **already-landed** `@drawable/bg_row`; `isSelected` → `isActivated`; drop `stateListAnimator`, `layout_indicator` and the per-row card | `22-components.md` §8.1, §18, R5; `00-rules.md` 1.1, 2.4.3 | Medium. `bg_row.xml` references `@color/ripple_neutral`, which is **undefined** - see the dependency table. Verify the hairline never draws above the first row of a group or below the last |
| W7 | **P1** | Header rebuild: 48dp targets, Russian names, one trailing control | `layout_servers_header.xml`, `MainActivity.kt:705-720`, `menu_main.xml`, new `menu_servers.xml`, `strings.xml` | §3.5(a). Four 36dp buttons → one 48dp «Ещё»; search 48 minHeight, radius 16, `ic_search`; add the meta line and the sort cycle | `00-rules.md` 4.8, 7.2, 14.2, 14.3, 1.4.10; `22-components.md` §12.1A | Low-medium |
| W8 | **P1** | Ping stops parsing JSON on the main thread per bind | `MainRecyclerAdapter.kt:211`, `rebuildRows()` | Read the affiliation once per rebuild on `Dispatchers.Default` into `Row.Server` | `00-rules.md` 11.5 | Low; folds into W5 |
| W9 | **P1** | Group header: 48dp, sticky, kebab, announced collapse state | `item_section_header.xml`, `MainRecyclerAdapter.kt:182-194`, new `StickyHeaderDecoration` | §6. Rotate the chevron over `motion_state` 220 instead of snapping | `00-rules.md` 4.6, 7.2, 14.9; `24-tab-conformance.md` §3.2.4 | Low-medium |
| W10 | **P1** | Product gate states on the provider group header | `item_section_header.xml`, `MainActivity.kt`, `AccountViewModel` wiring | §5's seven gate rows | `00-rules.md` §15; `22-components.md` §10 | Medium: the Серверы tab has no account dependency today. **Do not** re-derive `isTrial` from squad or tariff name - `AccountViewModel.kt:220` already resolves it |
| W11 | **P1** | Russian plurals; `%d серверов` is wrong for 1 and 2 | `strings.xml:6,7`, new `plural_servers` / `plural_providers` / `plural_selected_of` / `plural_removed_servers`, `MainActivity.kt:937-939` | The app has **zero** `<plurals>` resources today (`grep -rn '<plurals' values/` → 0). Stop concatenating `" · "` in Kotlin; use `servers_count` `%1$s · %2$s` | `32-master-plan-android.md` §4.4 | Low |
| W12 | **P1** | Copy pass | `strings.xml:5,8`, `strings_server_actions.xml` | `title_servers` «Сервера» → **«Серверы»**; `search_hint` «Поиск серверов…» → **«Поиск по названию или стране»**; `server_action_set_default` «Сделать основным» → **«Выбрать этот сервер»**; `server_action_share_qr` «Поделиться (QR)» → **«Поделиться QR-кодом»**; `server_action_share_clipboard` «Поделиться (буфер)» → **«Скопировать ссылку»**; `server_action_delete` «Удалить» → **«Удалить сервер»** | `00-rules.md` 9.2, 9.3; `22-components.md` §15; `24-tab-conformance.md` A-15 | Low |
| W13 | **P1** | Multi-select | `MainRecyclerAdapter.kt`, `MainActivity.kt`, `menu_servers.xml`, `strings_menu_actions.xml` | §3.5(b). Reuse the reserved check slot; «Текущий» chip while the mode is on; Back exits the mode first | `00-rules.md` 7.7, 11.3; `22-components.md` §18 | Medium-high: a mode is state, and `syncSelection` must not fire inside it |
| W14 | **P2** | Action sheet restyle | `sheet_server_actions.xml`, `ServerActionsSheet.kt`, new `row_action.xml` / `row_destructive.xml` | §6. Six coloured tiles → zero, via the already-landed `@drawable/bg_tile_neutral`; Body labels → Title; delete row → `TextAppearance.App.Title.Destructive`; add «Проверить задержку»; `BottomSheetDialogFragment`; focus in and back | `22-components.md` §8.4, §8.6, §13.1; `00-rules.md` 3.6, 7.6 | Low-medium |
| W15 | **P2** | Delete the footer view type | `item_recycler_footer.xml`, `MainRecyclerAdapter.kt:155,158,170,360` | Replace with `clipToPadding` bottom inset | `00-rules.md` 11.3 | Low |
| W16 | **P2** | `locateSelectedServer` becomes automatic | `MainActivity.kt:2759-2775`, `showTab()` | §3.5(c). Scroll the selected row into view on tab entry; expand its group if collapsed | `00-rules.md` 1.3, 7.7 | Low |
| W17 | **P2** | Delete the dead protocol filter | `MainViewModel.kt:610-635` | `applyProtocolFilter`, `availableProtocols` and `protocolFilter` have **zero callers outside the ViewModel**. The header search replaces them | `00-rules.md` 17.3 | Low |
| W18 | **P2** | `sw600dp`: gutter 24, content max 720 centred | new `values-sw600dp/dimens.xml`, `layout-sw600dp/` as needed | There are no `sw600dp` resources in the module at all (G10) | `00-rules.md` 4.1, 11.4, 14 | Low-medium |
| W19 | **P2** | Retire `dialog_config_filter.xml` and `activity_server_group.xml` | both layouts, `ServerGroupActivity.kt` | A-13 / `24-tab-conformance.md`:1005. `ServerGroupActivity.kt:117-125` also uses `android.R.string.ok` as a destructive confirm, which 7.5 and §13.2 forbid | `24-tab-conformance.md` A-13; `00-rules.md` 7.5 | Low, once A-13 lands. **Sequence after A-13** |
| W20 | **P3** | Overflow stops being hand-painted | `MainActivity.kt:751-782` | Theme the `PopupMenu` through `ThemeOverlay.Departament` instead of tinting each icon and rewriting titles as `SpannableString`s | `00-rules.md` 1.3, 11.1 | Low |

**Cross-wave dependencies (not this screen's files, blocking or blocked):**

| Dep | Owner | Why it lands here |
|---|---|---|
| `@color/ping_bad` dark is `#F04452` (`values-night/colors.xml:35`) | token wave | 3.5 and §8.6 both specify `#FF6069` for error **text**. `#F04452` measures 4.88:1 and fails AA as the «нет ответа» label. W2 renders wrong until this is fixed |
| `util/NumberFormat.kt` does not exist | §4.3 wave | W2's `«48 мс»` needs the single mandated formatter, not another ad-hoc `String.format` |
| `ic_search.xml`, `ic_close.xml`, `ic_sort.xml`, `ic_unfold_more.xml`, `ic_chevron_down.xml` do not exist | icon wave | W7 uses `ic_outline_filter_alt_24` as the search glyph today (`layout_servers_header.xml:105`), which `32-master-plan-android.md` §6.2 already calls "the wrong glyph" |
| `spinner_arc.xml` / `ic_spinner_arc.xml` / `spinner_rotate.xml` do not exist | §17.1 wave | W2's testing state uses the platform `ProgressBar` at 16dp until they do |
| **`@color/ripple_neutral` is referenced and never defined** | component wave | `drawable/bg_row.xml` and `values/styles.xml:488,791` reference it; `grep -rn 'name="ripple_neutral"' values/ values-night/` returns **0**. W6 adopts `bg_row.xml`, so this must be defined first |
| `row_action.xml` / `row_destructive.xml` do not exist | §8.7 wave | W14 depends on them. `bg_row.xml` and `bg_tile_neutral.xml` **have** landed and are used as-is |
| `rv_home_servers` is not virtualised (`activity_main.xml:41-452`, `MainActivity.kt:666-668`) | Home wave | Same adapter. With 150 servers Home inflates all of them. `00-rules.md` 4.6 |
| ~~Golos Text ramp~~ **SATISFIED, verified during this audit** | type wave | `TextAppearance.App.{Title,Title.Medium,Body,Subtitle,Caption}` now carry `@font/golos_text_*` and a declared `lineHeight`; `App.Chip` correctly keeps Space Grotesk and `App.Chip.Ui` exists for Russian chip labels. Nothing on this screen needs to wait on it |

---

## 8. Five-dimension verdict (`audit.native.md`, via `00-rules.md` §17.1)

| # | Dimension | Score | Justification |
|---|---|---|---|
| 1 | **Accessibility** | **1 / 4** | Four 36dp touch targets with zero separation (`layout_servers_header.xml:29-76`) against a 48dp/8dp floor. Three English `contentDescription`s read aloud by TalkBack (`:35,47,59`), one of which (`:35`, «Сервера» on a collapse button) names the screen instead of the action. Collapse state never announced (`item_section_header.xml:26`). Latency carried by colour alone with `pingGood`/`pingBad` and no word (`MainRecyclerAdapter.kt:218`). Empty-search renders a blank region with no text (`MainActivity.kt:942`). `maxLines="1"` on the primary label at font scale 200%. One thing is right: `infoContainer.isSelected` does reach `AccessibilityNodeInfo` |
| 2 | **Performance** | **1 / 4** | `rv_servers` is genuinely virtualised (`activity_main.xml:474`) and `customProtoCache` (`MainRecyclerAdapter.kt:275-298`) is a real optimisation. Against that: a full JSON parse on the main thread on **every row bind** (`:211` → `MmkvManager.kt:250`), no stable IDs, no `DiffUtil`, and five `notifyDataSetChanged()` calls on a visible list - the exact prohibition in 11.5. The same adapter drives a non-virtualised list on Home |
| 3 | **Appearance and theming** | **2 / 4** | Genuinely good: 0 raw colour literals in all eight layouts, 0 `textAllCaps`, 0 `fontFamily`, theme attrs used consistently, ramp styles applied nearly everywhere, and `press_scale.xml` already carries D-11's 0.97. The ramp now declares Golos Text and a line height per role, so the three inline `textSize` overrides are no longer merely untidy - they discard a face and a leading the style had just set. Against that: 2 raw hex in `bg_server_row.xml`, 44 raw `dp` literals (24 of them gaps), a 14dp search radius against D-7's 16, a 1.5dp selected stroke that exists in no scale, six coloured tiles on the action sheet against D-5's ceiling of three, and an emoji serving as the product's server icon |
| 4 | **Platform conformance** | **2 / 4** | `RecyclerView`, `MaterialCardView`, `BottomSheetDialog` and `MaterialButton` are all the right choices, and the selection/connection **behaviour** (`MainActivity.kt:1524-1547`) is more careful than most VPN clients. Against that: the per-row rounded card is the §2.4.3 uniform-card tell, group headers are not sticky, the overflow is a hand-tinted `PopupMenu` with `SpannableString` titles, the sheet is a `Dialog` rather than a `DialogFragment` and dies on rotation, and the long-press contract is declared and never bound |
| 5 | **Adaptivity** | **1 / 4** | No `layout-sw600dp`, no `values-sw600dp`, no 720dp cap, no gutter step - the module has neither qualifier directory (G10). A fixed `layout_height="44dp"` search field (`layout_servers_header.xml:91`) against R2's "heights are minimums, never fixed". `maxLines="1"` primary labels beside fixed-size siblings. No landscape consideration. The one thing that does adapt is that a single provider correctly collapses the header layer (`MainRecyclerAdapter.kt:97`) |

**Total: 7 / 20. Ship bar is ≥ 18 / 20 with no dimension below 3. This screen does not ship.**

Three of the five dimensions sit at 1. The order that lifts them fastest is W1 (restores function),
then W5 + W8 (performance 1 → 3), then W7 + W4 + W9 (accessibility 1 → 3), then W18 (adaptivity
1 → 3), then W3 + W6 + W14 (appearance and conformance 2 → 3+).

---

## 9. Departament slop test (`00-rules.md` §2.4), answered

1. **Category reflex** - the screen is neutral grey with blue on one row; the accent is rationed.
   **Pass**, but only because there is so little of it. The failed-ping green/red pair is the one place
   the palette drifts toward the category reflex, and B11 removes it.
2. **Second-order reflex** - not terminal-green, not Linear-grey-with-violet. **Pass.**
3. **The uniform-card tell** - 150 identical rounded rectangles with an icon, a title and a subtitle.
   **Fail.** This is the single loudest tell on the screen and W6 is the answer.
4. **The decoration tell** - the emoji flag communicates a country (real), the protocol chip
   communicates a protocol (real), the ping communicates latency (real, when it is not lying). The
   0×0 `layout_indicator` communicates nothing and is a fossil. **Near-pass**, one deletion.
5. **The copy tell** - «Сервера» is colloquial where the product says «Серверы»; «%d серверов» is
   ungrammatical for 1 and 2; «Поделиться (QR)» is a label with a parenthetical, not a verb phrase;
   three `contentDescription`s are English. **Fail.**
6. **The state tell** - open it with a search that matches nothing and the screen is blank with no
   text. **Fail**, hardest of the seven.
7. **The trust test** - a user who lives in Raycast and Linear would trust the row grammar and the
   snackbar-with-undo, and would pause at: a `-1ms` latency, a green number that means nothing, a
   flag emoji rendering three different ways across three Android versions, and a long-press that
   does nothing. **Fail.**
